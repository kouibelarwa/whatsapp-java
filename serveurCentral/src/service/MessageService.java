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
    }
}