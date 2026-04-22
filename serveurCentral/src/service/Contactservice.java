package service;

import dao.ContactDao;
import dao.UserDao;
import model.User;
import server.ChatServer;
import server.ClientHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ContactService — gestion des contacts côté serveur.
 *
 * PROTOCOLE (signaux binaire type "CONTACT_SIGNAL") :
 *
 *   Client → Serveur : CONTACT_SIGNAL
 *             data   = "ADD:phone_du_contact"
 *                    | "REMOVE:phone_du_contact"
 *                    | "GET_CONTACTS"
 *                    | "SEARCH:phone"
 *
 *   Serveur → Client : CONTACT_SIGNAL
 *             data   = "ADD_OK:phone:username"
 *                    | "ADD_FAIL:REASON"
 *                    | "REMOVE_OK"
 *                    | "CONTACTS_LIST:phone1:username1:status1|phone2:username2:status2|..."
 *                    | "SEARCH_RESULT:phone:username"
 *                    | "SEARCH_NOT_FOUND"
 */
public class Contactservice {

    private final ContactDao contactDao = new ContactDao();
    private final UserDao    userDao    = new UserDao();

    /**
     * Point d'entrée unique.
     * Reçoit la commande depuis ClientHandler et dispatch.
     */
    public void handle(int userId, String userPhone,
                       String payload, ClientHandler handler) {

        if (payload.startsWith("ADD:")) {
            String targetPhone = payload.substring(4).trim();
            handleAdd(userId, targetPhone, handler);

        } else if (payload.startsWith("REMOVE:")) {
            String targetPhone = payload.substring(7).trim();
            handleRemove(userId, targetPhone, handler);

        } else if ("GET_CONTACTS".equals(payload)) {
            handleGetContacts(userId, handler);

        } else if (payload.startsWith("SEARCH:")) {
            String searchPhone = payload.substring(7).trim();
            handleSearch(searchPhone, handler);

        } else {
            sendContactSignal(handler, "ADD_FAIL:UNKNOWN_COMMAND");
        }
    }

    // ── Ajouter un contact ───────────────────────────────────────

    private void handleAdd(int ownerId, String targetPhone,
                           ClientHandler handler) {
        // Chercher l'utilisateur cible par son phone
        User target = userDao.getByPhone(targetPhone);

        if (target == null) {
            sendContactSignal(handler, "ADD_FAIL:USER_NOT_FOUND");
            return;
        }
        if (target.getId() == ownerId) {
            sendContactSignal(handler, "ADD_FAIL:CANNOT_ADD_YOURSELF");
            return;
        }
        if (contactDao.contactExists(ownerId, target.getId())) {
            sendContactSignal(handler, "ADD_FAIL:ALREADY_EXISTS");
            return;
        }

        contactDao.addContact(ownerId, target.getId(), null);
        sendContactSignal(handler,
                "ADD_OK:" + target.getPhone() + ":" + target.getUsername());

        System.out.println("[Contact] " + ownerId
                + " a ajouté " + targetPhone);
    }

    // ── Supprimer un contact ─────────────────────────────────────

    private void handleRemove(int ownerId, String targetPhone,
                              ClientHandler handler) {
        User target = userDao.getByPhone(targetPhone);
        if (target == null) {
            sendContactSignal(handler, "ADD_FAIL:USER_NOT_FOUND");
            return;
        }
        contactDao.removeContact(ownerId, target.getId());
        sendContactSignal(handler, "REMOVE_OK");
    }

    // ── Récupérer la liste des contacts ──────────────────────────

    private void handleGetContacts(int ownerId, ClientHandler handler) {
        List<User> contacts = contactDao.getContacts(ownerId);

        if (contacts.isEmpty()) {
            sendContactSignal(handler, "CONTACTS_LIST:");
            return;
        }

        // Format : phone1:username1:status1|phone2:username2:status2|...
        StringBuilder sb = new StringBuilder("CONTACTS_LIST:");
        for (int i = 0; i < contacts.size(); i++) {
            User u = contacts.get(i);
            // Vérifier le statut réel dans la map des connectés
            boolean online = ChatServer.clients.containsKey(u.getId());
            String status  = online ? "ONLINE" : "OFFLINE";
            sb.append(u.getPhone())
                    .append(":").append(u.getUsername())
                    .append(":").append(status);
            if (i < contacts.size() - 1) sb.append("|");
        }
        sendContactSignal(handler, sb.toString());
    }

    // ── Rechercher un utilisateur par phone ──────────────────────

    private void handleSearch(String phone, ClientHandler handler) {
        User found = userDao.searchByPhone(phone);
        if (found == null || !found.isVerified()) {
            sendContactSignal(handler, "SEARCH_NOT_FOUND");
        } else {
            sendContactSignal(handler,
                    "SEARCH_RESULT:" + found.getPhone()
                            + ":" + found.getUsername());
        }
    }

    // ── Envoi du signal vers le client ───────────────────────────

    private void sendContactSignal(ClientHandler handler, String payload) {
        try {
            handler.send("CONTACT_SIGNAL", "",
                    "", payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[Contact] Envoi échoué : " + e.getMessage());
        }
    }
}