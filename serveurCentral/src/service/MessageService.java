package service;

import dao.MessageDao;
import dao.UserDao;
import model.Message;
import server.ChatServer;
import server.ClientHandler;

import java.io.IOException;
import java.util.List;

/**
 * MessageService — Traitement et livraison des messages.
 */
public class MessageService {

    private final MessageDao messageDao = new MessageDao();
    private final UserDao    userDao    = new UserDao();

    /**
     * Traite un message entrant :
     * 1. Sauvegarde en DB
     * 2. Livraison si destinataire connecté → DELIVERED
     * 3. Sinon reste NOT_DELIVERED → livré plus tard
     */
    public void process(Message m, byte[] data) {
        // Sauvegarde en DB
        int msgId = messageDao.save(m, data);
        if (msgId == -1) {
            System.err.println("[MessageService] Erreur sauvegarde message !");
            return;
        }

        // Livraison si connecté
        ClientHandler receiver = ChatServer.clients.get(m.getReceiverId());
        if (receiver != null) {
            try {
                byte[] toSend = m.isText()
                        ? m.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        : data;
                receiver.send(m.getType(),
                        m.getSenderPhone(),
                        m.getFilename() != null ? m.getFilename() : "",
                        toSend);
                messageDao.updateEtat(msgId, "DELIVERED");
                System.out.println("[MessageService] Message livré à id="
                        + m.getReceiverId());
            } catch (IOException e) {
                System.err.println("[MessageService] Erreur livraison : "
                        + e.getMessage());
            }
        } else {
            System.out.println("[MessageService] Destinataire hors ligne, "
                    + "message sauvegardé (id=" + msgId + ")");
        }
    }

    /**
     * Livre tous les messages non délivrés lors de la reconnexion.
     */
    public void deliverOfflineMessages(int userId, String userPhone,
                                       ClientHandler handler) {
        List<Message> pending = messageDao.getUndelivered(userId);
        if (pending.isEmpty()) return;

        System.out.println("[MessageService] Livraison de "
                + pending.size() + " message(s) hors-ligne à " + userPhone);

        for (Message m : pending) {
            try {
                byte[] data;
                if (m.isText()) {
                    data = m.getContent().getBytes(
                            java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    data = messageDao.getDataById(m.getId());
                }
                handler.send(m.getType(), m.getSenderPhone(),
                        m.getFilename() != null ? m.getFilename() : "", data);
                messageDao.updateEtat(m.getId(), "DELIVERED");
            } catch (IOException e) {
                System.err.println("[MessageService] Erreur livraison offline : "
                        + e.getMessage());
            }
        }

        int groupId = userDao.createGroup(groupName, creatorId);
        if (groupId != -1) {
            userDao.addMember(groupId, creatorId, true);
            
            for (int memberId : memberIds) {
                userDao.addMember(groupId, memberId, false);
            }

            try {
                String payload = "GROUP_CREATED:" + groupId + ":" + groupName;
                client.send("GROUP_SIGNAL", "", "", payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {}
            
            List<Integer> members = userDao.getGroupMembers(groupId);
            for (int memberId : members) {
                ClientHandler receiverClient = ChatServer.clients.get(memberId);
                if (receiverClient != null) {
                    try {
                        String payload = "GROUP_ADDED:" + groupId + ":" + groupName;
                        receiverClient.send("GROUP_SIGNAL", "", "", payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (Exception e) {}
                }
            }
        } else {
            try {
                client.send("GROUP_SIGNAL", "", "", "GROUP_ERROR:Creation_failed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {}
        }
    }

    public void processGroupMessage(Message m, int groupId, byte[] data) {
        int msgId = messageDao.saveGroupMessage(m, groupId, data);
        if (msgId == -1) {
            System.err.println("[MessageService] Erreur sauvegarde message groupe " + groupId + ". Tentative de livraison temps réel...");
        }

        List<Integer> members = userDao.getGroupMembers(groupId);
        for (int memberId : members) {
            if (memberId == m.getSenderId()) continue;

            ClientHandler receiverClient = ChatServer.clients.get(memberId);
            if (receiverClient != null) {
                try {
                    String senderInfo = m.getSenderPhone(); 
                    receiverClient.send("GROUP_MSG:" + m.getType(), "GROUP_" + groupId + ":" + senderInfo, m.getFilename(), data);
                } catch (Exception e) {
                    System.err.println("[MessageService] Erreur envoi au membre " + memberId);
                }
            }
        }
    }

    public void sendGroupInfo(int groupId, ClientHandler client) {
        List<String[]> members = userDao.getGroupMembersWithStatus(groupId);
        StringBuilder sb = new StringBuilder("GROUP_INFO_REPLY:" + groupId + "|");
        for (String[] m : members) {
            // m: username, id, phone, is_admin
            String mUsername = m[0];
            int mId = Integer.parseInt(m[1]);
            String mPhone = m[2];
            boolean isAdmin = Boolean.parseBoolean(m[3]);
            boolean isOnline = ChatServer.clients.containsKey(mId);
            sb.append(mId).append(":").append(mPhone).append(":").append(mUsername).append(":")
              .append(isAdmin ? "true" : "false").append(":").append(isOnline ? "ONLINE" : "OFFLINE").append(";");
        }
        try {
            client.send("GROUP_SIGNAL", "", "", sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {}
    }

    private void broadcastGroupUpdate(int groupId) {
        List<Integer> members = userDao.getGroupMembers(groupId);
        for (int memberId : members) {
            ClientHandler receiverClient = ChatServer.clients.get(memberId);
            if (receiverClient != null) {
                try {
                    receiverClient.send("GROUP_SIGNAL", "", "", ("GROUP_UPDATED:" + groupId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception e) {}
            }
        }
    }

    public void addGroupMember(int groupId, String contactName, int requesterId, ClientHandler client) {
        // Verification admin
        if (!isAdmin(groupId, requesterId)) {
            sendGroupError(client, "Seul un admin peut ajouter des membres.");
            return;
        }
        
        List<String[]> contacts = contactDao.getContactsWithNickname(requesterId);
        String cleanName = contactName.trim();
        int targetId = -1;
        for (String[] c : contacts) {
            if (cleanName.equalsIgnoreCase(c[4]) || cleanName.equalsIgnoreCase(c[2])) {
                targetId = Integer.parseInt(c[0]);
                break;
            }
        }
        
        if (targetId == -1) {
            model.User u = userDao.getByUsername(cleanName);
            if (u == null) u = userDao.searchByPhone(cleanName);
            if (u != null) {
                targetId = u.getId();
            }
        }
        
        if (targetId == -1) {
            sendGroupError(client, "Contact introuvable (" + cleanName + ")");
            return;
        }
        
        userDao.addMember(groupId, targetId, false);
        broadcastGroupUpdate(groupId);
    }

    public void removeGroupMember(int groupId, int targetId, int requesterId, ClientHandler client) {
        if (!isAdmin(groupId, requesterId)) {
            sendGroupError(client, "Seul un admin peut retirer des membres.");
            return;
        }
        userDao.removeMember(groupId, targetId);
        broadcastGroupUpdate(groupId);
        // Also inform the removed user if they are online
        ClientHandler removedClient = ChatServer.clients.get(targetId);
        if (removedClient != null) {
            try {
                removedClient.send("GROUP_SIGNAL", "", "", ("GROUP_UPDATED:" + groupId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {}
        }
    }

    public void promoteAdmin(int groupId, int targetId, int requesterId, ClientHandler client) {
        if (!isAdmin(groupId, requesterId)) {
            sendGroupError(client, "Seul un admin peut promouvoir un membre.");
            return;
        }
        userDao.promoteAdmin(groupId, targetId);
        broadcastGroupUpdate(groupId);
    }

    public void leaveGroup(int groupId, int userId, ClientHandler client) {
        userDao.removeMember(groupId, userId);
        try {
            client.send("GROUP_SIGNAL", "", "", ("GROUP_UPDATED:" + groupId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {}
        broadcastGroupUpdate(groupId);
    }

    private boolean isAdmin(int groupId, int userId) {
        List<String[]> members = userDao.getGroupMembersWithStatus(groupId);
        for (String[] m : members) {
            if (Integer.parseInt(m[1]) == userId) {
                return Boolean.parseBoolean(m[3]);
            }
        }
        return false;
    }

    private void sendGroupError(ClientHandler client, String msg) {
        try {
            client.send("GROUP_SIGNAL", "", "", ("GROUP_ERROR:" + msg).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {}
    }
}