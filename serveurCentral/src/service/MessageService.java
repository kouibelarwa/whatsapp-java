package service;

import dao.MessageDao;
import model.Message;
import server.ChatServer;
import server.ClientHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MessageService {

    private final MessageDao dao = new MessageDao();

    /**
     * Traite un message entrant.
     *
     * ORDRE :
     *   1. Sauvegarder en base NOT_DELIVERED (toujours)
     *   2. Chercher le destinataire par receiverId dans clients
     *   3. Si online → send() → updateEtat(DELIVERED)
     *   4. Si offline → reste NOT_DELIVERED, livré à la reconnexion
     */
    public void process(Message m, byte[] data) {

        // ÉTAPE 1 : sauvegarder en base
        int messageId = dao.save(m, m.isBinary() ? data : null);
        if (messageId == -1) {
            System.err.println("[MessageService] Échec sauvegarde id="
                    + m.getSenderId() + "→" + m.getReceiverId());
            return;
        }

        // ÉTAPE 2 : chercher le destinataire par son ID (toujours unique)
        ClientHandler receiverHandler =
                ChatServer.clients.get(m.getReceiverId());

        if (receiverHandler != null) {
            try {
                // Envoyer : le senderPhone sert d'identifiant affiché
                receiverHandler.send(
                        m.getType(),
                        m.getSenderPhone(),
                        m.getFilename(),
                        data
                );
                // ÉTAPE 3 : confirmer la livraison
                dao.updateEtat(messageId, "DELIVERED");
                System.out.println("[MessageService] Livré id=" + messageId);

            } catch (Exception e) {
                // send() échoué → reste NOT_DELIVERED
                System.err.println("[MessageService] Échec livraison id="
                        + messageId + " : " + e.getMessage());
            }
        } else {
            // Destinataire offline → NOT_DELIVERED déjà en base
            System.out.println("[MessageService] Destinataire offline, "
                    + "message id=" + messageId + " en attente.");
        }
    }

    /**
     * Livre les messages offline à la reconnexion.
     * Cherche par receiverId (INT) → pas de conflit entre deux Sara.
     */
    public void deliverOfflineMessages(int userId, String userPhone,
                                       ClientHandler handler) {
        List<Message> list = dao.getUndelivered(userId);
        for (Message m : list) {
            try {
                byte[] data;
                if (m.isText()) {
                    data = m.getContent().getBytes(StandardCharsets.UTF_8);
                } else {
                    data = dao.getDataById(m.getId());
                }
                handler.send(m.getType(), m.getSenderPhone(),
                        m.getFilename(), data);
                dao.updateEtat(m.getId(), "DELIVERED");
            } catch (Exception e) {
                System.err.println("[MessageService] Échec offline id="
                        + m.getId() + ": " + e.getMessage());
            }
        }
    }
}