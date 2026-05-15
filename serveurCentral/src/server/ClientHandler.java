package server;

import auth.SmsCodeGenerator;
import dao.UserDao;
import dao.ContactDao;
import dao.MessageDao;
import model.Message;
import model.User;
import service.CallService;
import service.Contactservice;
import service.MessageService;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler extends Thread {

    private static final int MAX_SIZE = 100 * 1024 * 1024; // 100 Mo
    private static final ExecutorService mediaExecutor = Executors.newCachedThreadPool();

    private final Socket socket;

    private DataInputStream  binIn;
    private DataOutputStream binOut;

    private int    userId   = -1;
    private String userPhone;
    private String username;

    private final UserDao        userDao        = new UserDao();
    private final MessageDao     messageDao     = new MessageDao();
    private final MessageService msgService     = new MessageService();
    private final Contactservice contactService = new Contactservice();
    private final CallService    callService    = ChatServer.callService;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            binIn  = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()));
            binOut = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));

            if (!handleAuth()) {
                socket.close();
                return;
            }

            // Note : On ne livre pas les messages ici car le client les charge
            // directement depuis la base de données via loadHistory()
            // msgService.deliverOfflineMessages(userId, userPhone, this);


            // Boucle principale
            chatLoop();

        } catch (Exception e) {
            System.out.println("[Server] " + tag() + " déconnecté : " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private boolean handleAuth() throws IOException {
        String line = binIn.readUTF();
        if (line == null) return false;

        if (line.startsWith("AUTH_REQUEST:"))
            return handleAuthRequest(
                    line.substring("AUTH_REQUEST:".length()).trim());

        if (line.startsWith("SESSION:"))
            return handleSessionReconnect(
                    line.substring("SESSION:".length()).trim());

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

        String expectedCode = SmsApiServer.getCode(reqPhone);
        boolean codeMatches = (expectedCode != null && expectedCode.equals(reqCode));

        if (!codeMatches && !userDao.verifyCode(reqPhone, reqCode)) {
            sendText("AUTH_FAIL:WRONG_CODE");
            return false;
        }
        SmsApiServer.removeCode(reqPhone);

        userDao.markVerifiedAndSetUsername(reqPhone, reqUsername);
        int id = userDao.getIdByPhone(reqPhone);
        if (id == -1) {
            id = Math.abs(reqPhone.hashCode());
            System.err.println("[Server] BDD injoignable, utilisation d'un ID temporaire: " + id);
        }

        // Multi-session support: We allow multiple connections for the same user.

        this.userId    = id;
        this.userPhone = reqPhone;
        this.username  = reqUsername;
        
        ChatServer.clients.computeIfAbsent(userId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(this);
        userDao.updateStatusById(userId, "ONLINE");
        userDao.mergeDuplicateAccounts(userId, userPhone);
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

        // Multi-session support: We allow multiple connections for the same user.

        this.userId    = user.getId();
        this.userPhone = user.getPhone();
        this.username  = user.getUsername();
        ChatServer.clients.computeIfAbsent(userId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(this);
        userDao.updateStatusById(userId, "ONLINE");
        userDao.mergeDuplicateAccounts(userId, userPhone);
        broadcastStatus("ONLINE");

        sendText("SESSION_OK:" + userId + ":" + username);
        System.out.println("[Server] " + username
                + " (id=" + userId + ") reconnecté via session.");
        return true;
    }

    private void chatLoop() throws IOException {
        try {
            while (true) {
                String type          = binIn.readUTF();
                String receiverPhone = binIn.readUTF();
                String senderPhone   = binIn.readUTF();
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
            case "image":
            case "file":
            case "REPLY:text":
            case "REPLY:audio":
            case "REPLY:video":
            case "REPLY:image":
            case "REPLY:file": {
                User receiverUser = userDao.searchByPhone(receiverPhone);
                if (receiverUser == null) {
                    System.err.println("[Server] Phone inconnu : " + receiverPhone);
                    return;
                }
                int receiverId = receiverUser.getId();
                Message m;
                
                String baseType = type.startsWith("REPLY:") ? type.substring(6) : type;
                Integer replyToId = type.startsWith("REPLY:") ? Integer.parseInt(filename) : null;
                String actualFilename = type.startsWith("REPLY:") ? "" : filename; // filename was used for replyToId

                if ("text".equals(baseType)) {
                    String content = new String(data, StandardCharsets.UTF_8);
                    m = Message.text(userId, userPhone, receiverId, content);
                } else {
                    m = Message.binary(userId, userPhone, receiverId, baseType, actualFilename);
                }
                
                if (replyToId != null) m.setReplyToId(replyToId);

                msgService.process(m, receiverPhone, data);
                break;
            }

            case "GROUP_SIGNAL": {
                String payload = new String(data, StandardCharsets.UTF_8);
                if (payload.startsWith("CREATE_GROUP:")) {
                    // CREATE_GROUP:groupName:phone1,phone2...
                    String[] parts = payload.split(":", 3);
                    if (parts.length >= 2) {
                        String groupName = parts[1];
                        String[] members = parts.length > 2 ? parts[2].split(",") : new String[0];
                        java.util.List<String> memberPhones = new java.util.ArrayList<>(java.util.Arrays.asList(members));
                        msgService.createGroup(groupName, userId, userPhone, memberPhones, this);
                    }
                } else if (payload.startsWith("GET_GROUP_INFO:")) {
                    int groupId = Integer.parseInt(payload.split(":")[1]);
                    msgService.sendGroupInfo(groupId, this);
                } else if (payload.startsWith("ADD_GROUP_MEMBER:")) {
                    String[] parts = payload.split(":");
                    msgService.addGroupMember(Integer.parseInt(parts[1]), parts[2], userId, this);
                } else if (payload.startsWith("REMOVE_GROUP_MEMBER:")) {
                    String[] parts = payload.split(":");
                    msgService.removeGroupMember(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), userId, this);
                } else if (payload.startsWith("PROMOTE_ADMIN:")) {
                    String[] parts = payload.split(":");
                    msgService.promoteAdmin(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), userId, this);
                } else if (payload.startsWith("LEAVE_GROUP:")) {
                    msgService.leaveGroup(Integer.parseInt(payload.split(":")[1]), userId, this);
                }
                break;
            }

            case "GROUP_MSG:text":
            case "GROUP_MSG:audio":
            case "GROUP_MSG:video":
            case "GROUP_MSG:image":
            case "GROUP_MSG:file":
            case "GROUP_REPLY:text":
            case "GROUP_REPLY:audio":
            case "GROUP_REPLY:video":
            case "GROUP_REPLY:image":
            case "GROUP_REPLY:file": {
                // receiverPhone contains the groupId
                int groupId = Integer.parseInt(receiverPhone);
                boolean isReply = type.contains("_REPLY:");
                String actualType = isReply ? type.split(":")[1] : type.split(":")[1]; 
                // Fix: actualType should be the second part
                actualType = type.split(":")[1];
                
                Integer replyToId = isReply ? Integer.parseInt(filename) : null;
                String actualFilename = isReply ? "" : filename;

                Message m;
                if ("text".equals(actualType)) {
                    String content = new String(data, StandardCharsets.UTF_8);
                    m = Message.text(userId, userPhone, groupId, content);
                } else {
                    m = Message.binary(userId, userPhone, groupId, actualType, actualFilename);
                }
                
                if (replyToId != null) m.setReplyToId(replyToId);
                
                msgService.processGroupMessage(m, groupId, data);
                break;
            }

            case "CONTACT_SIGNAL": {
                String payload = new String(data, StandardCharsets.UTF_8);
                contactService.handle(userId, userPhone, payload, this);
                break;
            }



            case "UPDATE_PROFILE": {
                String newName = filename;
                if (newName != null && !newName.trim().isEmpty()) {
                    userDao.updateUsername(userId, newName);
                    this.username = newName;
                }
                if (data != null && data.length > 0) {
                    userDao.updateAvatar(userId, data);
                }
                // Notify contacts that profile was updated
                contactService.handleGet(userId, this);
                contactService.broadcastProfileUpdate(userId);
                break;
            }

            case "GET_AVATAR": {
                byte[] avatar = userDao.getAvatar(receiverPhone);
                if (avatar != null && avatar.length > 0) {
                    try {
                        this.send("AVATAR_REPLY", receiverPhone, "", avatar);
                    } catch (IOException e) {}
                }
                break;
            }

            case "CALL_SIGNAL": {
                String payload = new String(data, StandardCharsets.UTF_8);
                String[] parts = payload.split(":");
                if (parts.length < 2) return;
                String signal = parts[0];

                String otherPhone;
                String callType = "audio";

                if (signal.equals("CALL_REQUEST") && parts.length >= 3) {
                    callType = parts[1].toLowerCase();
                    otherPhone = parts[2];
                } else {
                    otherPhone = parts[parts.length - 1];
                }

                if (otherPhone.startsWith("GROUP_")) {
                    String groupKey = otherPhone; // "GROUP_id"
                    java.util.Set<String> activeParticipants = ChatServer.activeGroupCalls.computeIfAbsent(groupKey, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
                    
                    if (signal.equals("CALL_REQUEST")) {
                        activeParticipants.add(userPhone);
                        int groupId = Integer.parseInt(groupKey.replace("GROUP_", ""));
                        java.util.List<Integer> members = userDao.getGroupMembers(groupId);
                        String groupName = userDao.getGroupName(groupId);
                        
                        for (int memberId : members) {
                            if (memberId == userId) continue;
                            java.util.List<ClientHandler> receivers = ChatServer.clients.get(memberId);
                            if (receivers != null) {
                                for (ClientHandler receiver : receivers) {
                                    if (activeParticipants.contains(receiver.getUserPhone())) continue;
                                    final String fCallType = callType;
                                    final String fOtherPhone = otherPhone;
                                    final String fGroupName = groupName;
                                    mediaExecutor.submit(() -> {
                                        try {
                                            receiver.send("CALL_SIGNAL", fOtherPhone, "", ("CALL_INCOMING:" + fCallType + ":" + fOtherPhone + ":" + fGroupName).getBytes(StandardCharsets.UTF_8));
                                        } catch (Exception e) {}
                                    });
                                }
                            }
                        }
                    } else if (signal.equals("CALL_ACCEPT")) {
                        activeParticipants.add(userPhone);
                        String joinedSignal = "CALL_JOINED:" + userPhone;
                        String activeListSignal = "CALL_ACTIVE_LIST:" + String.join(",", activeParticipants);
                        
                        for (String activePhone : activeParticipants) {
                            User activeUser = userDao.searchByPhone(activePhone);
                            if (activeUser == null) continue;
                            java.util.List<ClientHandler> handlers = ChatServer.clients.get(activeUser.getId());
                            if (handlers == null) continue;
                            for (ClientHandler handler : handlers) {
                                try {
                                    if (activePhone.equals(userPhone)) {
                                        handler.send("CALL_SIGNAL", groupKey, "", activeListSignal.getBytes(StandardCharsets.UTF_8));
                                    } else {
                                        handler.send("CALL_SIGNAL", groupKey, "", joinedSignal.getBytes(StandardCharsets.UTF_8));
                                    }
                                } catch (Exception e) {}
                            }
                        }
                    } else if (signal.equals("CALL_END") || signal.equals("CALL_REJECT")) {
                        activeParticipants.remove(userPhone);
                        String leftSignal = "CALL_LEFT:" + userPhone;
                        for (String activePhone : activeParticipants) {
                            User activeUser = userDao.searchByPhone(activePhone);
                            if (activeUser == null) continue;
                            java.util.List<ClientHandler> handlers = ChatServer.clients.get(activeUser.getId());
                            if (handlers == null) continue;
                            for (ClientHandler handler : handlers) {
                                try {
                                    handler.send("CALL_SIGNAL", groupKey, "", leftSignal.getBytes(StandardCharsets.UTF_8));
                                } catch (Exception e) {}
                            }
                        }
                        if (activeParticipants.size() == 1) {
                            String lastPhone = activeParticipants.iterator().next();
                            activeParticipants.clear();
                            ChatServer.activeGroupCalls.remove(groupKey);
                            User lastUser = userDao.searchByPhone(lastPhone);
                            if (lastUser != null) {
                                java.util.List<ClientHandler> handlers = ChatServer.clients.get(lastUser.getId());
                                if (handlers != null) {
                                    for (ClientHandler handler : handlers) {
                                        try {
                                            handler.send("CALL_SIGNAL", groupKey, "", "CALL_TERMINATED".getBytes(StandardCharsets.UTF_8));
                                        } catch (Exception e) {}
                                    }
                                }
                            }
                        } else if (activeParticipants.isEmpty()) {
                            ChatServer.activeGroupCalls.remove(groupKey);
                        }
                    }
                    break;
                }

                switch (signal) {
                    case "CALL_REQUEST":
                        callService.handleRequest(userId, userPhone, otherPhone, callType);
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
                        // Relay unknown signals (e.g., ADD_PARTICIPANT, etc.) to the other phone
                        callService.handleGenericSignal(userId, userPhone, otherPhone, signal, parts);
                        break;
                }
                break;
            }

            case "CALL_AUDIO":
            case "CALL_VIDEO": {
                if (receiverPhone.startsWith("GROUP_")) {
                    String groupKey = receiverPhone;
                    java.util.Set<String> activeParticipants = ChatServer.activeGroupCalls.get(groupKey);
                    if (activeParticipants == null || !activeParticipants.contains(userPhone)) break;

                    for (String activePhone : activeParticipants) {
                        if (activePhone.equals(userPhone)) continue;
                        User receiverUser = userDao.searchByPhone(activePhone);
                        if (receiverUser == null) continue;
                        java.util.List<ClientHandler> receivers = ChatServer.clients.get(receiverUser.getId());
                        if (receivers != null) {
                            for (ClientHandler receiver : receivers) {
                                if (type.equals("CALL_AUDIO")) {
                                    try { receiver.send(type, receiverPhone, userPhone, data); } catch (IOException e) {}
                                } else {
                                    final String fType = type;
                                    final String fReceiverPhone = receiverPhone;
                                    final String fUserPhone = userPhone;
                                    final byte[] fData = data;
                                    mediaExecutor.submit(() -> {
                                        try { receiver.send(fType, fReceiverPhone, fUserPhone, fData); } catch (IOException e) {}
                                    });
                                }
                            }
                        }
                    }
                } else {
                    User receiverUser = userDao.searchByPhone(receiverPhone);
                    if (receiverUser == null) {
                        return;
                    }
                    int receiverId = receiverUser.getId();
                    java.util.List<ClientHandler> receivers = ChatServer.clients.get(receiverId);
                    if (receivers == null) {
                        return;
                    }
                    for (ClientHandler receiver : receivers) {
                        final String fType = type;
                        final String fUserPhone = userPhone;
                        final String fFilename = filename;
                        final byte[] fData = data;
                        mediaExecutor.submit(() -> {
                            try {
                                receiver.send(fType, fUserPhone, fFilename, fData);
                            } catch (IOException e) {
                                System.err.println("[Call] Erreur relay " + fType + " : " + e.getMessage());
                            }
                        });
                    }
                }
                break;
            }



            default:
                System.err.println("[Server] Type inconnu de " + username + " : " + type);
        }
    }

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
        binOut.writeUTF(senderPhone != null ? senderPhone : "");
        binOut.writeUTF(filename != null ? filename : "");
        binOut.writeInt(data != null ? data.length : 0);
        if (data != null && data.length > 0) binOut.write(data);
        binOut.flush();
    }

    private void disconnect() {
        if (userId != -1) {
            for (String groupKey : ChatServer.activeGroupCalls.keySet()) {
                java.util.Set<String> active = ChatServer.activeGroupCalls.get(groupKey);
                if (active != null && active.contains(userPhone)) {
                    active.remove(userPhone);
                    // Broadcast exit to others in group call
                    for (String otherPhone : active) {
                        User other = userDao.searchByPhone(otherPhone);
                        if (other != null) {
                            java.util.List<ClientHandler> handlers = ChatServer.clients.get(other.getId());
                            if (handlers != null) {
                                for (ClientHandler h : handlers) {
                                    try { h.send("CALL_SIGNAL", groupKey, "", ("CALL_LEFT:" + userPhone).getBytes(StandardCharsets.UTF_8)); } catch (Exception e) {}
                                }
                            }
                        }
                    }
                }
            }
            java.util.List<ClientHandler> userHandlers = ChatServer.clients.get(userId);
            if (userHandlers != null) {
                userHandlers.remove(this);
                if (userHandlers.isEmpty()) {
                    ChatServer.clients.remove(userId);
                    userDao.updateStatusById(userId, "OFFLINE");
                    broadcastStatus("OFFLINE");
                    System.out.println("[Server] " + username
                        + " (id=" + userId + ") déconnecté (dernière session).");
                } else {
                    System.out.println("[Server] " + username
                        + " (id=" + userId + ") une session fermée (" + userHandlers.size() + " restantes).");
                }
            }
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    private void broadcastStatus(String status) {
        String payload = "STATUS:" + userPhone + ":" + status + "|";
        for (java.util.List<ClientHandler> handlers : ChatServer.clients.values()) {
            for (ClientHandler client : handlers) {
                if (client.userId != this.userId) {
                    try {
                        client.send("CONTACT_SIGNAL", "server", "", payload.getBytes(StandardCharsets.UTF_8));
                    } catch (Exception e) {}
                }
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