package service;

import dao.CallDao;
import dao.UserDao;
import server.ChatServer;
import server.ClientHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * CallService — Gère les signaux d'appel audio/vidéo côté serveur.
 * Route les signaux entre appelant et appelé + sauvegarde en BD.
 */
public class CallService {

    private final UserDao userDao   = new UserDao();
    private final CallDao callDao   = new CallDao();   // ✅ AJOUTÉ

    // ─────────────────────────────────────────────────────────────
    // DEMANDE D'APPEL
    // ─────────────────────────────────────────────────────────────

    public void handleRequest(int callerId, String callerPhone, String calleePhone) {
        System.out.println("[CallService] Appel de " + callerPhone + " → " + calleePhone);

        int calleeId = userDao.getIdByPhone(calleePhone);
        if (calleeId == -1) {
            System.err.println("[CallService] Appelé inconnu : " + calleePhone);
            notifyCaller(callerId, "CALL_REJECTED:" + calleePhone);
            return;
        }

        // ✅ Créer l'appel en BD avec statut RINGING
        int callId = callDao.createCall(callerId, calleeId);
        System.out.println("[CallService] Appel créé en BD id=" + callId);

        ClientHandler calleeHandler = ChatServer.clients.get(calleeId);
        if (calleeHandler == null) {
            System.out.println("[CallService] Appelé hors ligne : " + calleePhone);
            // ✅ Marquer MISSED en BD
            if (callId != -1) callDao.markMissed(callId);
            notifyCaller(callerId, "CALL_MISSED:" + calleePhone);
            return;
        }

        try {
            // ✅ Envoyer l'ID de l'appel dans le signal pour que le client puisse le référencer
            String signal = "CALL_INCOMING:" + callerPhone + ":" + callId;
            calleeHandler.send("CALL_SIGNAL", callerPhone, "",
                    signal.getBytes(StandardCharsets.UTF_8));
            System.out.println("[CallService] Signal CALL_INCOMING envoyé à " + calleePhone);
        } catch (IOException e) {
            System.err.println("[CallService] Erreur envoi CALL_INCOMING : " + e.getMessage());
            if (callId != -1) callDao.markMissed(callId);
            notifyCaller(callerId, "CALL_MISSED:" + calleePhone);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // APPEL ACCEPTÉ
    // ─────────────────────────────────────────────────────────────

    public void handleAccept(int calleeId, String calleePhone, String callerPhone, int callId) {
        System.out.println("[CallService] " + calleePhone + " accepte l'appel de " + callerPhone);

        // ✅ Mettre à jour statut ACCEPTED en BD
        if (callId != -1) callDao.updateStatus(callId, "ACCEPTED");

        int callerId = userDao.getIdByPhone(callerPhone);
        if (callerId == -1) return;

        ClientHandler callerHandler = ChatServer.clients.get(callerId);
        if (callerHandler == null) return;

        try {
            String signal = "CALL_ACCEPTED:" + calleePhone;
            callerHandler.send("CALL_SIGNAL", calleePhone, "",
                    signal.getBytes(StandardCharsets.UTF_8));
            System.out.println("[CallService] Appel accepté entre " + callerPhone + " et " + calleePhone);
        } catch (IOException e) {
            System.err.println("[CallService] Erreur CALL_ACCEPT : " + e.getMessage());
        }
    }

    // Surcharge pour compatibilité si callId non disponible
    public void handleAccept(int calleeId, String calleePhone, String callerPhone) {
        handleAccept(calleeId, calleePhone, callerPhone, -1);
    }

    // ─────────────────────────────────────────────────────────────
    // APPEL REFUSÉ
    // ─────────────────────────────────────────────────────────────

    public void handleReject(int calleeId, String calleePhone, String callerPhone, int callId) {
        System.out.println("[CallService] " + calleePhone + " refuse l'appel de " + callerPhone);

        // ✅ Mettre à jour statut REJECTED en BD
        if (callId != -1) callDao.updateStatus(callId, "REJECTED");

        int callerId = userDao.getIdByPhone(callerPhone);
        if (callerId == -1) return;
        notifyCaller(callerId, "CALL_REJECTED:" + calleePhone);
    }

    public void handleReject(int calleeId, String calleePhone, String callerPhone) {
        handleReject(calleeId, calleePhone, callerPhone, -1);
    }

    // ─────────────────────────────────────────────────────────────
    // FIN D'APPEL
    // ─────────────────────────────────────────────────────────────

    public void handleEnd(int senderId, String senderPhone, String otherPhone, int callId) {
        System.out.println("[CallService] " + senderPhone + " raccroche (autre : " + otherPhone + ")");

        // ✅ Mettre à jour statut ENDED en BD
        if (callId != -1) callDao.updateStatus(callId, "ENDED");

        int otherId = userDao.getIdByPhone(otherPhone);
        if (otherId == -1) return;

        ClientHandler otherHandler = ChatServer.clients.get(otherId);
        if (otherHandler == null) return;

        try {
            String signal = "CALL_ENDED:" + senderPhone;
            otherHandler.send("CALL_SIGNAL", senderPhone, "",
                    signal.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[CallService] Erreur CALL_END : " + e.getMessage());
        }
    }

    public void handleEnd(int senderId, String senderPhone, String otherPhone) {
        handleEnd(senderId, senderPhone, otherPhone, -1);
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITAIRE PRIVÉ
    // ─────────────────────────────────────────────────────────────

    private void notifyCaller(int callerId, String signal) {
        ClientHandler callerHandler = ChatServer.clients.get(callerId);
        if (callerHandler == null) return;
        try {
            callerHandler.send("CALL_SIGNAL", "", "",
                    signal.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[CallService] Erreur notify caller : " + e.getMessage());
        }
    }
}