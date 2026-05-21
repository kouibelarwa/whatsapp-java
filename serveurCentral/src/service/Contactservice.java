package service;

import dao.ContactDao;
import dao.UserDao;
import dao.MessageDao;
import model.User;
import server.ChatServer;
import server.ClientHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Contactservice {
    private final ContactDao contactDao = new ContactDao();
    private final UserDao userDao = new UserDao();
    private final MessageDao messageDao = new MessageDao();

    public void handle(int userId, String userPhone, String payload, ClientHandler handler) {
        if (payload.startsWith("ADD:")) {
            String[] parts = payload.substring(4).split(":", 2);
            String targetPhone = normalizePhone(parts[0]);
            String nickname = parts.length > 1 ? parts[1].trim() : null;
            String currentUserPhone = normalizePhone(userPhone);

            if (targetPhone.equals(currentUserPhone)) {
                sendResponse(handler, "ADD_FAIL:SELF");
                return;
            }
            if (targetPhone.isEmpty()) {
                sendResponse(handler, "ADD_FAIL:NOT_FOUND");
                return;
            }

            User target = userDao.searchByPhone(targetPhone);

            if (target != null) {
                boolean added = contactDao.addContact(userId, target.getId(), nickname);
                if (!added) {
                    sendResponse(handler, "ADD_FAIL:DB");
                    return;
                }
                sendResponse(handler, "ADD_OK:" + targetPhone);
                handleGet(userId, handler);
            } else {
                sendResponse(handler, "ADD_FAIL:NOT_FOUND");
            }

        } else if (payload.equals("GET_CONTACTS")) {
            handleGet(userId, handler);

        } else if (payload.startsWith("REMOVE:")) {
            String targetPhone = normalizePhone(payload.substring(7));
            User target = userDao.searchByPhone(targetPhone);
            if (target != null) {
                contactDao.removeContact(userId, target.getId());
                messageDao.deleteConversation(userId, target.getId());
            }
            handleGet(userId, handler);
        } else if (payload.startsWith("EDIT_NICKNAME:")) {
            String[] parts = payload.substring(14).split(":", 2);
            if (parts.length >= 2) {
                String targetPhone = normalizePhone(parts[0]);
                String nickname = parts[1].trim();
                User target = userDao.searchByPhone(targetPhone);
                if (target != null) {
                    contactDao.addContact(userId, target.getId(), nickname);
                }
            }
            handleGet(userId, handler);
        }
    }


    public void handleGet(int userId, ClientHandler handler) {
        List<String[]> list = contactDao.getContactsWithNickname(userId);
        List<String[]> interactedList = messageDao.getInteractedUsers(userId);
        
        java.util.Set<String> addedPhones = new java.util.HashSet<>();
        StringBuilder sb = new StringBuilder("CONTACTS_LIST:");
        
        for (String[] c : list) {
            int contactId = Integer.parseInt(c[0]);
            String phone    = normalizePhone(c[1]);
            String username = c[2];
            java.util.List<ClientHandler> handlers = ChatServer.clients.get(contactId);
            String status   = (handlers != null && !handlers.isEmpty()) ? "ONLINE" : "OFFLINE";
            String nickname = c[4];
            String displayName = (nickname != null && !nickname.isEmpty()) ? nickname : username;
            
            sb.append(contactId).append(":")
              .append(phone).append(":")
              .append(displayName).append(":")
              .append(status).append("|");
            addedPhones.add(phone);
        }
        
        for (String[] c : interactedList) {
            String phone = normalizePhone(c[1]);
            if (!addedPhones.contains(phone)) {
                int contactId = Integer.parseInt(c[0]);
                String username = c[2];
                java.util.List<ClientHandler> handlers = ChatServer.clients.get(contactId);
                String status   = (handlers != null && !handlers.isEmpty()) ? "ONLINE" : "OFFLINE";
                // Interacted users don't have nicknames or pinning unless they are also in the contacts table
                String displayName = username; 
                
                sb.append(contactId).append(":")
                  .append(phone).append(":")
                  .append(username).append(":")
                  .append(status).append("|");
                addedPhones.add(phone);
            }
        }
        
        List<String[]> groups = userDao.getUserGroups(userId);
        for (String[] g : groups) {
            String groupId = g[0];
            String groupName = g[1];
            boolean isAnyOnline = false;
            
            List<String[]> membersList = userDao.getGroupMembersWithStatus(Integer.parseInt(groupId));
            StringBuilder membersStatus = new StringBuilder();
            for (int i = 0; i < membersList.size(); i++) {
                String mName = membersList.get(i)[0];
                int mId = Integer.parseInt(membersList.get(i)[1]);
                java.util.List<ClientHandler> handlers = ChatServer.clients.get(mId);
                if (handlers != null && !handlers.isEmpty()) {
                    isAnyOnline = true;
                    break;
                }
            }
            
            sb.append(groupId).append(":")
              .append("GROUP_" + groupId).append(":")
              .append(groupName).append(":")
              .append(isAnyOnline ? "ONLINE" : "OFFLINE").append("|");
        }
        
        sendResponse(handler, sb.toString());
    }

    private void sendResponse(ClientHandler handler, String msg) {
        try {
            handler.send("CONTACT_SIGNAL", "SERVER", "", msg.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String normalizePhone(String input) {
        if (input == null) return "";
        String normalized = input.replaceAll("[\\s\\-()]", "");
        return normalized.trim();
    }

    public void broadcastProfileUpdate(int userId) {
        // Find everyone who has this user in their contacts
        java.util.List<Integer> watchers = contactDao.getUsersWhoAdded(userId);
        for (int watcherId : watchers) {
            java.util.List<ClientHandler> handlers = ChatServer.clients.get(watcherId);
            if (handlers != null) {
                for (ClientHandler h : handlers) {
                    handleGet(watcherId, h);
                }
            }
        }
    }
}