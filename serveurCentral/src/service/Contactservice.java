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

            // ✅ Empêcher de s'ajouter soi-même
            if (targetPhone.equals(userPhone)) {
                sendResponse(handler, "ADD_FAIL:SELF");
                return;
            }

            User target = userDao.getByPhone(targetPhone);

            System.out.println("[Contactservice] ADD demandé : " + targetPhone
                    + " → trouvé : " + (target != null ? target.getId() : "NULL")); // DEBUG

            if (target != null) {
                boolean added = contactDao.addContact(userId, target.getId(), nickname);
                System.out.println("[Contactservice] addContact résultat : " + added); // DEBUG
                handleGet(userId, handler);
            } else {
                sendResponse(handler, "ADD_FAIL:NOT_FOUND");
            }

        } else if (payload.equals("GET_CONTACTS")) {
            handleGet(userId, handler);

        } else if (payload.startsWith("REMOVE:")) {
            String targetPhone = payload.substring(7).trim();
            User target = userDao.getByPhone(targetPhone);
            System.out.println("[Contactservice] REMOVE demandé : " + targetPhone
                    + " → trouvé : " + (target != null ? target.getId() : "NULL")); // ✅ debug
            if (target != null) {
                boolean removed = contactDao.removeContact(userId, target.getId());
                System.out.println("[Contactservice] removeContact résultat : " + removed);
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
            // ✅ Format binaire pour que le client reçoive correctement
            handler.send("CONTACT_SIGNAL", "SERVER", "", msg.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}