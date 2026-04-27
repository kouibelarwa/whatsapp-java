package service;

import dao.ContactDao;
import dao.UserDao;
import model.User;
import server.ChatServer;
import server.ClientHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Contactservice {
    private final ContactDao contactDao = new ContactDao();
    private final UserDao userDao = new UserDao();

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
            }
            handleGet(userId, handler);
        }
    }


    public void handleGet(int userId, ClientHandler handler) {
        List<String[]> list = contactDao.getContactsWithNickname(userId);
        StringBuilder sb = new StringBuilder("CONTACTS_LIST:");
        for (String[] c : list) {
            int contactId = Integer.parseInt(c[0]);
            String phone    = c[1];
            String username = c[2];
            String status   = ChatServer.clients.containsKey(contactId) ? "ONLINE" : "OFFLINE";
            String nickname = c[4];
            String displayName = (nickname != null && !nickname.isEmpty()) ? nickname : username;
            sb.append(phone).append(":").append(displayName).append(":").append(status).append("|");
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
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2);
        }
        return normalized.trim();
    }
}