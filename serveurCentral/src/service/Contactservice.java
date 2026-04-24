package service;

import dao.ContactDao;
import dao.UserDao;
import model.User;
import server.ClientHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Contactservice {
    private final ContactDao contactDao = new ContactDao();
    private final UserDao userDao = new UserDao();

    public void handle(int userId, String userPhone, String payload, ClientHandler handler) {
        if (payload.startsWith("ADD:")) {
            String targetPhone = payload.substring(4).trim();
            User target = userDao.getByPhone(targetPhone);

            if (target != null) {
                // ✅ Insertion dans la table 'contacts' avec les IDs (int)
                contactDao.addContact(userId, target.getId(), null);
                // On renvoie la liste mise à jour immédiatement
                handleGet(userId, handler);
            } else {
                sendResponse(handler, "ADD_FAIL:NOT_FOUND");
            }
        }
    }

    public void handleGet(int userId, ClientHandler handler) {
        List<User> list = contactDao.getContacts(userId);
        StringBuilder sb = new StringBuilder("CONTACTS_LIST:");
        for (User u : list) {
            // Format: phone:username:status|...
            sb.append(u.getPhone()).append(":").append(u.getUsername()).append(":ONLINE|");
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