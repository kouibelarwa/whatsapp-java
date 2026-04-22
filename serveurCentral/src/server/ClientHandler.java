package server;

import dao.UserDao;
import model.Message;
import model.User;
import service.CallService;
import service.Contactservice;
import service.MessageService;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * ClientHandler — un Thread par client connecté.
 *
 * ══════════════════════════════════════════════════════════════
 * PHASE 1 — AUTH texte
 * ══════════════════════════════════════════════════════════════
 *   Client → "AUTH_REQUEST:phone"
 *   Serveur → "SMS_SENT"
 *   Client → "VERIFY_CODE:phone:code:username"
 *   Serveur → "AUTH_OK:userId:phone" | "AUTH_FAIL:raison"
 *
 *   Client → "SESSION:phone"          ← phone sauvegardé localement
 *   Serveur → "SESSION_OK:userId" | "ERROR:raison"
 *
 * ══════════════════════════════════════════════════════════════
 * PHASE 2 — CHAT binaire
 * ══════════════════════════════════════════════════════════════
 *   Trame client → serveur :
 *     type(UTF) | receiverPhone(UTF) | senderPhone(UTF) |
 *     filename(UTF) | size(Int) | data[size]
 *
 *   Types :
 *     "text"           → data = bytes UTF-8
 *     "audio/video/file" → data = bytes binaires
 *     "CALL_SIGNAL"    → data = "CALL_REQUEST:phone" | "CALL_ACCEPT:phone" | ...
 *     "CONTACT_SIGNAL" → data = "ADD:phone" | "REMOVE:phone" |
 *                               "GET_CONTACTS" | "SEARCH:phone"
 *
 *   Trame serveur → client :
 *     type(UTF) | senderPhone(UTF) | filename(UTF) | size(Int) | data[size]
 */
public class ClientHandler extends Thread {

    private static final int MAX_SIZE = 100 * 1024 * 1024; // 100 Mo

    private final Socket          socket;
    private       BufferedReader  textIn;
    private       PrintWriter     textOut;
    private       DataInputStream  binIn;
    private       DataOutputStream binOut;

    // Identifiants du client connecté
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

            // Livrer les messages offline avec l'ID réel
            msgService.deliverOfflineMessages(userId, userPhone, this);

            chatLoop();

        } catch (Exception e) {
            System.out.println("[Server] " + tag() + " déconnecté : "
                    + e.getMessage());
        } finally {
            disconnect();
        }
    }

    // ── PHASE 1 ─────────────────────────────────────────────────

    private boolean handleAuth() throws IOException {
        String line = textIn.readLine();
        if (line == null) return false;

        if (line.startsWith("AUTH_REQUEST:")) {
            return handleAuthRequest(
                    line.substring("AUTH_REQUEST:".length()).trim());
        }
        if (line.startsWith("SESSION:")) {
            return handleSessionReconnect(
                    line.substring("SESSION:".length()).trim());
        }
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
            textOut.println("AUTH_FAIL:BAD_PROTOCOL"); return false;
        }
        String[] parts = verifyLine.split(":", 4);
        if (parts.length < 4) {
            textOut.println("AUTH_FAIL:BAD_FORMAT"); return false;
        }

        String reqPhone    = parts[1];
        String reqCode     = parts[2];
        String reqUsername = parts[3].trim();

        if (!userDao.verifyCode(reqPhone, reqCode)) {
            textOut.println("AUTH_FAIL:WRONG_CODE"); return false;
        }
        SmsApiServer.removeCode(reqPhone);

        // Enregistrer et récupérer l'ID
        userDao.markVerifiedAndSetUsername(reqPhone, reqUsername);
        int id = userDao.getIdByPhone(reqPhone);
        if (id == -1) {
            textOut.println("AUTH_FAIL:DB_ERROR"); return false;
        }

        // Vérifier si déjà connecté (par ID)
        if (ChatServer.clients.containsKey(id)) {
            textOut.println("AUTH_FAIL:ALREADY_CONNECTED"); return false;
        }

        this.userId   = id;
        this.userPhone = reqPhone;
        this.username  = reqUsername;
        ChatServer.clients.put(userId, this);
        userDao.updateStatusById(userId, "ONLINE");

        // Envoyer l'ID et le phone au client pour qu'il les utilise
        textOut.println("AUTH_OK:" + userId + ":" + reqPhone);
        System.out.println("[Server] " + username
                + " (id=" + userId + ") authentifié.");
        return true;
    }

    private boolean handleSessionReconnect(String savedPhone) {
        if (savedPhone == null || savedPhone.isEmpty()) {
            textOut.println("ERROR:INVALID_PHONE"); return false;
        }

        User user = userDao.getByPhone(savedPhone);
        if (user == null || !user.isVerified()) {
            textOut.println("ERROR:USER_NOT_FOUND"); return false;
        }

        if (ChatServer.clients.containsKey(user.getId())) {
            textOut.println("ERROR:ALREADY_CONNECTED"); return false;
        }

        this.userId    = user.getId();
        this.userPhone = user.getPhone();
        this.username  = user.getUsername();
        ChatServer.clients.put(userId, this);
        userDao.updateStatusById(userId, "ONLINE");

        textOut.println("SESSION_OK:" + userId);
        System.out.println("[Server] " + username
                + " (id=" + userId + ") reconnecté via session.");
        return true;
    }

    // ── PHASE 2 ─────────────────────────────────────────────────

    private void chatLoop() throws IOException {
        while (true) {
            String type          = binIn.readUTF();
            String receiverPhone = binIn.readUTF();
            String senderPhone   = binIn.readUTF(); // ignoré, on force userPhone
            String filename      = binIn.readUTF();
            int    size          = binIn.readInt();

            if (size < 0 || size > MAX_SIZE) {
                System.err.println("[Security] Taille invalide de "
                        + username + " : " + size);
                break;
            }

            byte[] data = new byte[size];
            binIn.readFully(data);

            dispatch(type, receiverPhone, filename, data);
        }
    }

    private void dispatch(String type, String receiverPhone,
                          String filename, byte[] data) {
        switch (type) {

            case "text":
            case "audio":
            case "video":
            case "file": {
                // Résoudre receiverId depuis receiverPhone
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
                String payload   = new String(data, StandardCharsets.UTF_8);
                String[] parts   = payload.split(":", 2);
                if (parts.length < 2) return;
                String signal    = parts[0];
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

    // ── Envoi vers ce client ─────────────────────────────────────

    public synchronized void send(String type, String senderPhone,
                                  String filename, byte[] data)
            throws IOException {
        DataOutputStream out = binOut != null
                ? binOut
                : new DataOutputStream(socket.getOutputStream());
        out.writeUTF(type);
        out.writeUTF(senderPhone != null ? senderPhone : "");
        out.writeUTF(filename    != null ? filename    : "");
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    // ── Utilitaires ──────────────────────────────────────────────



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
        return username != null ? username + "(id=" + userId + ")"
                : socket.getInetAddress().toString();
    }
}