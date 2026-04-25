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

    private final Socket          socket;
    private       BufferedReader  textIn;
    private       PrintWriter     textOut;
    private       DataInputStream  binIn;
    private       DataOutputStream binOut;

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
            textIn  = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            textOut = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true);

            if (!handleAuth()) { socket.close(); return; }

            binIn  = new DataInputStream(socket.getInputStream());
            binOut = new DataOutputStream(socket.getOutputStream());

            msgService.deliverOfflineMessages(userId, userPhone, this);

            chatLoop();

        } catch (Exception e) {
            System.out.println("[Server] " + tag() + " déconnecté : "
                    + e.getMessage());
        } finally {
            disconnect();
        }
    }

    // ── AUTH ────────────────────────────────────────────────────

    private boolean handleAuth() throws IOException {
        String line = textIn.readLine();
        if (line == null) return false;

        if (line.startsWith("AUTH_REQUEST:"))
            return handleAuthRequest(
                    line.substring("AUTH_REQUEST:".length()).trim());

        if (line.startsWith("SESSION:"))
            return handleSessionReconnect(
                    line.substring("SESSION:".length()).trim());

        textOut.println("ERROR:UNKNOWN_COMMAND");
        return false;
    }

    private boolean handleAuthRequest(String phone) throws IOException {
        String code = SmsCodeGenerator.generateCode();
        userDao.saveVerificationCode(phone, code);
        SmsApiServer.storeCode(phone, code);
        textOut.println("SMS_SENT");

        String verifyLine = textIn.readLine();
        if (verifyLine == null || !verifyLine.startsWith("VERIFY_CODE:")) {
            textOut.println("AUTH_FAIL:BAD_PROTOCOL");
            return false;
        }

        String[] parts = verifyLine.split(":", 4);
        if (parts.length < 4) {
            textOut.println("AUTH_FAIL:BAD_FORMAT");
            return false;
        }

        String reqPhone    = parts[1];
        String reqCode     = parts[2];
        String reqUsername = parts[3].trim();

        if (!userDao.verifyCode(reqPhone, reqCode)) {
            textOut.println("AUTH_FAIL:WRONG_CODE");
            return false;
        }
        SmsApiServer.removeCode(reqPhone);

        userDao.markVerifiedAndSetUsername(reqPhone, reqUsername);
        int id = userDao.getIdByPhone(reqPhone);
        if (id == -1) {
            textOut.println("AUTH_FAIL:DB_ERROR");
            return false;
        }

        if (ChatServer.clients.containsKey(id)) {
            textOut.println("AUTH_FAIL:ALREADY_CONNECTED");
            return false;
        }

        this.userId    = id;
        this.userPhone = reqPhone;
        this.username  = reqUsername;
        ChatServer.clients.put(userId, this);
        userDao.updateStatusById(userId, "ONLINE");

        // ✅ FIX : inclure username dans la réponse
        textOut.println("AUTH_OK:" + userId + ":" + reqPhone
                + ":" + reqUsername);
        System.out.println("[Server] " + username
                + " (id=" + userId + ") authentifié.");
        return true;
    }

    private boolean handleSessionReconnect(String savedPhone) {
        if (savedPhone == null || savedPhone.isEmpty()) {
            textOut.println("ERROR:INVALID_PHONE");
            return false;
        }

        User user = userDao.getByPhone(savedPhone);
        if (user == null || !user.isVerified()) {
            textOut.println("ERROR:USER_NOT_FOUND");
            return false;
        }

        if (ChatServer.clients.containsKey(user.getId())) {
            textOut.println("ERROR:ALREADY_CONNECTED");
            return false;
        }

        this.userId    = user.getId();
        this.userPhone = user.getPhone();
        this.username  = user.getUsername();
        ChatServer.clients.put(userId, this);
        userDao.updateStatusById(userId, "ONLINE");

        // ✅ FIX : inclure username dans la réponse
        textOut.println("SESSION_OK:" + userId + ":" + username);
        System.out.println("[Server] " + username
                + " (id=" + userId + ") reconnecté via session.");
        return true;
    }

    // ── CHAT LOOP ───────────────────────────────────────────────

    private void chatLoop() throws IOException {
        try {
            while (true) {
                String type          = binIn.readUTF();
                String receiverPhone = binIn.readUTF();
                String senderPhone   = binIn.readUTF();
                String filename      = binIn.readUTF();
                int    size          = binIn.readInt();

                if (size < 0 || size > MAX_SIZE) {
                    System.err.println("[Security] Taille invalide reçue de " + username + " : " + size);
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

    private void dispatch(String type, String receiverPhone,
                          String filename, byte[] data) {
        switch (type) {

            case "text":
            case "audio":
            case "video":
            case "file": {
                int receiverId = userDao.getIdByPhone(receiverPhone);
                if (receiverId == -1) {
                    System.err.println("[Server] Phone inconnu : "
                            + receiverPhone);
                    return;
                }
                Message m;
                if ("text".equals(type)) {
                    String content = new String(data, StandardCharsets.UTF_8);
                    m = Message.text(userId, userPhone, receiverId, content);
                } else {
                    m = Message.binary(userId, userPhone,
                            receiverId, type, filename);
                }
                msgService.process(m, data);
                break;
            }

            case "CALL_SIGNAL": {
                String payload    = new String(data, StandardCharsets.UTF_8);
                String[] parts    = payload.split(":", 2);
                if (parts.length < 2) return;
                String signal     = parts[0];
                String otherPhone = parts[1];

                switch (signal) {
                    case "CALL_REQUEST":
                        callService.handleRequest(userId, userPhone, otherPhone);
                        break;
                    case "CALL_ACCEPT":
                        callService.handleAccept(userId, userPhone, otherPhone);
                        break;
                    case "CALL_REJECT":
                        callService.handleReject(userId, userPhone, otherPhone);
                        break;
                    case "CALL_END":
                        callService.handleEnd(userId, userPhone, otherPhone);
                        break;
                    default:
                        System.err.println("[Call] Signal inconnu : " + signal);
                }
                break;
            }

            case "CONTACT_SIGNAL": {
                String payload = new String(data, StandardCharsets.UTF_8);
                contactService.handle(userId, userPhone, payload, this);
                break;
            }

            default:
                System.err.println("[Server] Type inconnu de "
                        + username + " : " + type);
        }
    }

    // ── SEND ────────────────────────────────────────────────────

    public synchronized void send(String type, String senderPhone,
                                  String filename, byte[] data) throws IOException {
        // On utilise binOut s'il existe, sinon on utilise directement le flux de sortie
        // Mais attention : en mode texte (AUTH), on utilise textOut, en mode CHAT on utilise binOut.
        if (binOut != null) {
            binOut.writeUTF(type);
            binOut.writeUTF(senderPhone != null ? senderPhone : "");
            binOut.writeUTF(filename != null ? filename : "");
            binOut.writeInt(data.length);
            binOut.write(data);
            binOut.flush();
        } else {
            // Si on n'est pas encore en mode binaire, on ne devrait pas envoyer de binaire
            System.err.println("[Server] Tentative d'envoi binaire avant activation du mode binaire");
        }
    }
    // ── DISCONNECT ──────────────────────────────────────────────

    private void disconnect() {
        if (userId != -1) {
            ChatServer.clients.remove(userId);
            userDao.updateStatusById(userId, "OFFLINE");
            System.out.println("[Server] " + username
                    + " (id=" + userId + ") déconnecté.");
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    private String tag() {
        return username != null
                ? username + "(id=" + userId + ")"
                : socket.getInetAddress().toString();
    }
}