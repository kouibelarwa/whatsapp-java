package server;

import auth.SmsCodeGenerator;
import dao.UserDao;
import model.Message;
import model.User;
import service.CallService;
import service.Contactservice;
import service.MessageService;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler extends Thread {

    private static final int MAX_SIZE = 100 * 1024 * 1024; // 100 Mo

    private final Socket socket;

    private DataInputStream  binIn;
    private DataOutputStream binOut;

    private int    userId   = -1;
    private String userPhone;
    private String username;

    private final UserDao        userDao        = new UserDao();
    private final MessageService msgService     = new MessageService();
    private final Contactservice contactService = new Contactservice();
    private final CallService    callService    = ChatServer.callService;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            binIn  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            binOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            if (!handleAuth()) {
                socket.close();
                return;
            }

            msgService.deliverOfflineMessages(userId, userPhone, this);
            chatLoop();

        } catch (Exception e) {
            System.out.println("[Server] " + tag() + " déconnecté : " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    // ── AUTH ──────────────────────────────────────────────────────────────────

    private boolean handleAuth() throws IOException {
        String line = binIn.readUTF();
        if (line == null) return false;

        if (line.startsWith("AUTH_REQUEST:"))
            return handleAuthRequest(line.substring("AUTH_REQUEST:".length()).trim());

        if (line.startsWith("SESSION:"))
            return handleSessionReconnect(line.substring("SESSION:".length()).trim());

        sendText("ERROR:UNKNOWN_COMMAND");
        return false;
    }

    private boolean handleAuthRequest(String phone) throws IOException {
        String code = SmsCodeGenerator.generateCode();
        userDao.saveVerificationCode(phone, code);
        SmsApiServer.storeCode(phone, code);

        System.out.println("[Server] Envoi SMS_SENT...");
        sendText("SMS_SENT");

        System.out.println("[Server] En attente du message VERIFY_CODE...");
        String verifyLine = binIn.readUTF();
        System.out.println("[Server] Reçu : " + verifyLine);

        if (verifyLine == null || !verifyLine.startsWith("VERIFY_CODE:")) {
            System.out.println("[Server] Format invalide reçu : " + verifyLine);
            sendText("AUTH_FAIL:BAD_PROTOCOL");
            return false;
        }
        String[] parts = verifyLine.split(":", 4);
        if (parts.length < 4) {
            sendText("AUTH_FAIL:BAD_FORMAT");
            return false;
        }

        String reqPhone    = parts[1];
        String reqCode     = parts[2];
        String reqUsername = parts[3].trim();

        if (!userDao.verifyCode(reqPhone, reqCode)) {
            sendText("AUTH_FAIL:WRONG_CODE");
            return false;
        }
        SmsApiServer.removeCode(reqPhone);

        userDao.markVerifiedAndSetUsername(reqPhone, reqUsername);
        int id = userDao.getIdByPhone(reqPhone);
        if (id == -1) {
            sendText("AUTH_FAIL:DB_ERROR");
            return false;
        }

        if (ChatServer.clients.containsKey(id)) {
            sendText("AUTH_FAIL:ALREADY_CONNECTED");
            return false;
        }

        this.userId    = id;
        this.userPhone = reqPhone;
        this.username  = reqUsername;
        ChatServer.clients.put(userId, this);
        userDao.updateStatusById(userId, "ONLINE");
        broadcastStatus("ONLINE");

        sendText("AUTH_OK:" + userId + ":" + reqPhone + ":" + reqUsername);
        System.out.println("[Server] " + username + " (id=" + userId + ") authentifié.");
        return true;
    }

    private boolean handleSessionReconnect(String savedPhone) throws IOException {
        if (savedPhone == null || savedPhone.isEmpty()) {
            sendText("ERROR:INVALID_PHONE");
            return false;
        }

        User user = userDao.getByPhone(savedPhone);
        if (user == null || !user.isVerified()) {
            sendText("ERROR:USER_NOT_FOUND");
            return false;
        }

        if (ChatServer.clients.containsKey(user.getId())) {
            sendText("ERROR:ALREADY_CONNECTED");
            return false;
        }

        this.userId    = user.getId();
        this.userPhone = user.getPhone();
        this.username  = user.getUsername();
        ChatServer.clients.put(userId, this);
        userDao.updateStatusById(userId, "ONLINE");
        broadcastStatus("ONLINE");

        sendText("SESSION_OK:" + userId + ":" + username);
        System.out.println("[Server] " + username + " (id=" + userId + ") reconnecté via session.");
        return true;
    }

    // ── CHAT LOOP ─────────────────────────────────────────────────────────────

    private void chatLoop() throws IOException {
        try {
            while (true) {
                String type          = binIn.readUTF();
                String receiverPhone = binIn.readUTF();
                String senderPhone   = binIn.readUTF();
                String filename      = binIn.readUTF();
                int    size          = binIn.readInt();

                if (size < 0 || size > MAX_SIZE) {
                    System.err.println("[Security] Taille invalide de " + username + " : " + size);
                    break;
                }

                byte[] data = new byte[size];
                binIn.readFully(data);
                dispatch(type, receiverPhone, filename, data);
            }
        } catch (EOFException e) {
            System.out.println("[Server] Flux terminé pour " + username);
        }
    }

    // ── DISPATCH ──────────────────────────────────────────────────────────────

    private void dispatch(String type, String receiverPhone,
                          String filename, byte[] data) {
        switch (type) {

            case "text":
            case "audio":
            case "video":
            case "image":
            case "file": {
                int receiverId = userDao.getIdByPhone(receiverPhone);
                if (receiverId == -1) {
                    System.err.println("[Server] Phone inconnu : " + receiverPhone);
                    return;
                }
                int receiverId = receiverUser.getId();
                Message m;
                if ("text".equals(type)) {
                    String content = new String(data, StandardCharsets.UTF_8);
                    m = Message.text(userId, userPhone, receiverId, content);
                } else {
                    m = Message.binary(userId, userPhone, receiverId, type, filename);
                }
                break;
            }

            case "GROUP_MSG:text":
            case "GROUP_MSG:audio":
            case "GROUP_MSG:video":
            case "GROUP_MSG:image":
            case "GROUP_MSG:file": {
                // receiverPhone contains the groupId
                int groupId = Integer.parseInt(receiverPhone);
                String actualType = type.split(":")[1];

                Message m;
                if ("text".equals(actualType)) {
                    String content = new String(data, StandardCharsets.UTF_8);
                    // senderPhone is passed so clients know who sent it
                    m = Message.text(userId, userPhone, groupId, content);
                } else {
                    m = Message.binary(userId, userPhone, groupId, actualType, filename);
                }
                msgService.processGroupMessage(m, groupId, data);
                break;
            }

            case "CALL_SIGNAL": {
                String payload = new String(data, StandardCharsets.UTF_8);
                // Format : SIGNAL:otherPhone  OU  SIGNAL:otherPhone:callId
                String[] parts = payload.split(":", 3);
                if (parts.length < 2) return;

                String signal     = parts[0];
                String otherPhone = parts[1];
                int    callId     = parts.length >= 3 ? parseCallId(parts[2]) : -1;

                switch (signal) {
                    case "CALL_REQUEST":
                        callService.handleRequest(userId, userPhone, otherPhone, callType);
                        break;
                    case "CALL_ACCEPT":
                        callService.handleAccept(userId, userPhone, otherPhone, callId);
                        break;
                    case "CALL_REJECT":
                        callService.handleReject(userId, userPhone, otherPhone, callId);
                        break;
                    case "CALL_END":
                        callService.handleEnd(userId, userPhone, otherPhone, callId);
                        break;
                    default:
                        System.err.println("[Call] Signal inconnu : " + signal);
                }
                break;
            }

            case "CALL_AUDIO":
            case "CALL_VIDEO": {
                if (receiverPhone.startsWith("GROUP_")) {
                    int groupId = Integer.parseInt(receiverPhone.replace("GROUP_", ""));
                    java.util.List<Integer> members = userDao.getGroupMembers(groupId);
                    for (int memberId : members) {
                        if (memberId == userId) continue;
                        ClientHandler receiver = ChatServer.clients.get(memberId);
                        if (receiver != null) {
                            try {
                                receiver.send(type, receiverPhone, filename, data);
                            } catch (IOException e) {}
                        }
                    }
                } else {
                    User receiverUser = userDao.searchByPhone(receiverPhone);
                    if (receiverUser == null) {
                        return;
                    }
                    int receiverId = receiverUser.getId();
                    ClientHandler receiver = ChatServer.clients.get(receiverId);
                    if (receiver == null) {
                        return;
                    }
                    try {
                        receiver.send(type, userPhone, filename, data);
                    } catch (IOException e) {
                        System.err.println("[Call] Erreur relay " + type + " : " + e.getMessage());
                    }
                }
                break;
            }

            case "CONTACT_SIGNAL": {
                String payload = new String(data, StandardCharsets.UTF_8);
                contactService.handle(userId, userPhone, payload, this);
                break;
            }

            default:
                System.err.println("[Server] Type inconnu de " + username + " : " + type);
        }
    }

    // ✅ ICI — en dehors du switch, à l'intérieur de la classe
    private int parseCallId(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return -1; }
    }

    // ── SEND ──────────────────────────────────────────────────────────────────

    private synchronized void sendText(String msg) throws IOException {
        binOut.writeUTF(msg);
        binOut.flush();
    }

    public synchronized void send(String type, String senderPhone,
                                  String filename, byte[] data) throws IOException {
        if (binOut == null) {
            System.err.println("[Server] binOut null pour " + tag());
            return;
        }
        binOut.writeUTF(type);
        binOut.writeUTF(senderPhone);
        binOut.writeUTF("");
        binOut.writeUTF(filename);
        binOut.writeInt(data != null ? data.length : 0);
        if (data != null && data.length > 0) binOut.write(data);
        binOut.flush();
    }

    // ── DISCONNECT ────────────────────────────────────────────────────────────

    private void disconnect() {
        if (userId != -1) {
            ChatServer.clients.remove(userId);
            userDao.updateStatusById(userId, "OFFLINE");
            System.out.println("[Server] " + username + " (id=" + userId + ") déconnecté.");
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    private void broadcastStatus(String status) {
        String payload = "STATUS:" + userPhone + ":" + status + "|";
        for (ClientHandler client : ChatServer.clients.values()) {
            if (client.userId != this.userId) {
                try {
                    client.send("CONTACT_SIGNAL", "server", "", payload.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {}
            }
        }
    }

    private String tag() {
        return username != null
                ? username + "(id=" + userId + ")"
                : socket.getInetAddress().toString();
    }

    public String getUserPhone() {
        return userPhone;
    }
}