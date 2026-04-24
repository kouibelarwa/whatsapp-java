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
            String targetPhone = parts[0].trim();
            String nickname = parts.length > 1 ? parts[1].trim() : null;

            User target = userDao.getByPhone(targetPhone);
            if (target != null) {
                contactDao.addContact(userId, target.getId(), nickname);
                handleGet(userId, handler);
            } else {
                sendResponse(handler, "ADD_FAIL:NOT_FOUND");
            }
        } else if (payload.equals("GET_CONTACTS")) {  // ← AJOUTER ÇA !
            handleGet(userId, handler);
        } else if (payload.startsWith("REMOVE:")) {  // ← AJOUTER ÇA
            String targetPhone = payload.substring(7).trim();
            User target = userDao.getByPhone(targetPhone);
            if (target != null) {
                contactDao.removeContact(userId, target.getId());
            }
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
            // ✅ Format binaire pour que le client reçoive correctement
            handler.send("CONTACT_SIGNAL", "SERVER", "", msg.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}