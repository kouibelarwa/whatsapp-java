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
    public void process(Message m, String receiverPhone, byte[] data) {
        // Sauvegarde en DB
        int msgId = messageDao.save(m, data);
        boolean persisted = msgId != -1;
        if (!persisted) {
            System.err.println("[MessageService] Erreur : Impossible de sauvegarder le message en base de données (vérifiez la taille du fichier et le type de colonne data, e.g. LONGBLOB).");
            System.err.println("[MessageService] Tentative de livraison temps réel...");
        }


        // Livraison si connecté
        ClientHandler receiver = ChatServer.clients.get(m.getReceiverId());
        if (receiver == null && receiverPhone != null && !receiverPhone.isBlank()) {
            receiver = findOnlineByPhone(receiverPhone);
        }
        if (receiver != null) {
            try {
                byte[] toSend = m.isText()
                        ? m.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        : data;
                receiver.send(m.getType(),
                        m.getSenderPhone(),
                        m.getFilename() != null ? m.getFilename() : "",
                        toSend);
                if (persisted) {
                    messageDao.updateEtat(msgId, "DELIVERED");
                }
                System.out.println("[MessageService] Message livré à id="
                        + m.getReceiverId());
            } catch (IOException e) {
                System.err.println("[MessageService] Erreur livraison : "
                        + e.getMessage());
            }
        } else {
            if (persisted) {
                System.out.println("[MessageService] Destinataire hors ligne, "
                        + "message sauvegardé (id=" + msgId + ")");
            } else {
                System.err.println("[MessageService] Destinataire hors ligne et sauvegarde échouée, message perdu.");
            }
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

    private ClientHandler findOnlineByPhone(String phone) {
        String target = normalizePhone(phone);
        for (ClientHandler handler : ChatServer.clients.values()) {
            String connectedPhone = normalizePhone(handler.getUserPhone());
            if (!connectedPhone.isEmpty() && connectedPhone.equals(target)) {
                return handler;
            }
        }
        return null;
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