package service;

import dao.UserDao;
import server.ChatServer;
import server.ClientHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * CallService — Gère les signaux d'appel audio/vidéo côté serveur.
 * Route les signaux entre appelant et appelé.
 */
public class CallService {

    private final UserDao userDao = new UserDao();

    // ─────────────────────────────────────────────────────────────
    // DEMANDE D'APPEL
    // ─────────────────────────────────────────────────────────────

    /**
     * Appelant envoie CALL_REQUEST → on notifie l'appelé.
     */
    public void handleRequest(int callerId, String callerPhone, String calleePhone) {
        System.out.println("[CallService] Appel de " + callerPhone
                + " → " + calleePhone);

        int calleeId = userDao.getIdByPhone(calleePhone);
        if (calleeId == -1) {
            System.err.println("[CallService] Appelé inconnu : " + calleePhone);
            // Notifier l'appelant que le numéro est inconnu
            notifyCaller(callerId, "CALL_REJECTED:" + calleePhone);
            return;
        }

        ClientHandler calleeHandler = ChatServer.clients.get(calleeId);
        if (calleeHandler == null) {
            // Appelé hors ligne
            System.out.println("[CallService] Appelé hors ligne : " + calleePhone);
            notifyCaller(callerId, "CALL_MISSED:" + calleePhone);
            return;
        }

        // Envoyer signal d'appel entrant à l'appelé
        try {
            String signal = "CALL_INCOMING:" + callerPhone;
            calleeHandler.send(
                    "CALL_SIGNAL",
                    callerPhone,
                    "",
                    signal.getBytes(StandardCharsets.UTF_8));
            System.out.println("[CallService] Signal CALL_INCOMING envoyé à "
                    + calleePhone);
        } catch (IOException e) {
            System.err.println("[CallService] Erreur envoi CALL_INCOMING : "
                    + e.getMessage());
            notifyCaller(callerId, "CALL_MISSED:" + calleePhone);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // APPEL ACCEPTÉ
    // ─────────────────────────────────────────────────────────────

    /**
     * Appelé accepte → notifier l'appelant.
     */
    public void handleAccept(int calleeId, String calleePhone, String callerPhone) {
        System.out.println("[CallService] " + calleePhone
                + " accepte l'appel de " + callerPhone);

        int callerId = userDao.getIdByPhone(callerPhone);
        if (callerId == -1) return;

        ClientHandler callerHandler = ChatServer.clients.get(callerId);
        if (callerHandler == null) return;

        try {
            String signal = "CALL_ACCEPTED:" + calleePhone;
            callerHandler.send(
                    "CALL_SIGNAL",
                    calleePhone,
                    "",
                    signal.getBytes(StandardCharsets.UTF_8));
            System.out.println("[CallService] Appel accepté — connexion établie entre "
                    + callerPhone + " et " + calleePhone);
        } catch (IOException e) {
            System.err.println("[CallService] Erreur CALL_ACCEPT : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // APPEL REFUSÉ
    // ─────────────────────────────────────────────────────────────

    /**
     * Appelé refuse → notifier l'appelant.
     */
    public void handleReject(int calleeId, String calleePhone, String callerPhone) {
        System.out.println("[CallService] " + calleePhone
                + " refuse l'appel de " + callerPhone);

        int callerId = userDao.getIdByPhone(callerPhone);
        if (callerId == -1) return;

        notifyCaller(callerId, "CALL_REJECTED:" + calleePhone);
    }

    // ─────────────────────────────────────────────────────────────
    // FIN D'APPEL
    // ─────────────────────────────────────────────────────────────

    /**
     * Un des deux raccroche → notifier l'autre.
     */
    public void handleEnd(int senderId, String senderPhone, String otherPhone) {
        System.out.println("[CallService] " + senderPhone
                + " raccroche (autre : " + otherPhone + ")");

        int otherId = userDao.getIdByPhone(otherPhone);
        if (otherId == -1) return;

        ClientHandler otherHandler = ChatServer.clients.get(otherId);
        if (otherHandler == null) return;

        try {
            String signal = "CALL_ENDED:" + senderPhone;
            otherHandler.send(
                    "CALL_SIGNAL",
                    senderPhone,
                    "",
                    signal.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[CallService] Erreur CALL_END : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITAIRE PRIVÉ
    // ─────────────────────────────────────────────────────────────

    private void notifyCaller(int callerId, String signal) {
        ClientHandler callerHandler = ChatServer.clients.get(callerId);
        if (callerHandler == null) return;
        try {
            callerHandler.send(
                    "CALL_SIGNAL", "", "",
                    signal.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[CallService] Erreur notify caller : " + e.getMessage());
        }
    }
}