package service;

import dao.MessageDao;
import dao.UserDao;
import model.Message;
import model.User;
import server.ChatServer;
import server.ClientHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import dao.CallDao;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CallService — Gère les signaux d'appel audio/vidéo côté serveur.
 * Route les signaux entre appelant et appelé.
 */
public class CallService {

    private final UserDao userDao = new UserDao();
    private final MessageDao messageDao = new MessageDao();
    private final CallDao callDao = new CallDao();
    
    // Maps callerPhone (or calleePhone) -> callId
    private final Map<String, Integer> activeCalls = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────
    // DEMANDE D'APPEL
    // ─────────────────────────────────────────────────────────────

    /**
     * Appelant envoie CALL_REQUEST → on notifie l'appelé.
     */
    public void handleRequest(int callerId, String callerPhone, String calleePhone, String callType) {
        System.out.println("[CallService] Appel " + callType + " de " + callerPhone
                + " → " + calleePhone);

        User callee = userDao.searchByPhone(calleePhone);
        if (callee == null) {
            System.err.println("[CallService] Appelé inconnu : " + calleePhone);
            // Notifier l'appelant que le numéro est inconnu
            notifyCaller(callerId, "CALL_REJECTED:" + calleePhone);
            return;
        }
        int calleeId = callee.getId();
        String normalizedCalleePhone = callee.getPhone();

        ClientHandler calleeHandler = ChatServer.clients.get(calleeId);
        if (calleeHandler == null) {
            // Appelé hors ligne
            System.out.println("[CallService] Appelé hors ligne : " + normalizedCalleePhone);
            
            int callId = callDao.createCall(callerId, calleeId);
            if (callId != -1) callDao.markMissed(callId);
            
            persistCallNotice(callerId, callerPhone, calleeId, callType, "Appel manqué");
            notifyCaller(callerId, "CALL_MISSED:" + normalizedCalleePhone);
            return;
        }

        // Save call in DB
        int callId = callDao.createCall(callerId, calleeId);
        if (callId != -1) {
            activeCalls.put(callerPhone, callId);
            activeCalls.put(calleePhone, callId);
        }

        // Envoyer signal d'appel entrant à l'appelé (inclure le type d'appel)
        try {
            String signal = "CALL_INCOMING:" + callType + ":" + callerPhone;
            calleeHandler.send(
                    "CALL_SIGNAL",
                    callerPhone,
                    "",
                    signal.getBytes(StandardCharsets.UTF_8));
            System.out.println("[CallService] Signal CALL_INCOMING envoyé à "
                    + normalizedCalleePhone);
        } catch (IOException e) {
            System.err.println("[CallService] Erreur envoi CALL_INCOMING : "
                    + e.getMessage());
            
            if (callId != -1) {
                callDao.markMissed(callId);
                activeCalls.remove(callerPhone);
                activeCalls.remove(calleePhone);
            }
            persistCallNotice(callerId, callerPhone, calleeId, callType, "Appel manqué");
            notifyCaller(callerId, "CALL_MISSED:" + normalizedCalleePhone);
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
            
            Integer callId = activeCalls.get(callerPhone);
            if (callId != null) {
                callDao.updateStatus(callId, "ACCEPTED");
            }
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

        Integer callId = activeCalls.get(callerPhone);
        if (callId != null) {
            callDao.updateStatus(callId, "REJECTED");
            activeCalls.remove(callerPhone);
            activeCalls.remove(calleePhone);
        }
        
        persistCallNotice(callerId, callerPhone, calleeId, "audio/video", "Appel refusé");
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
                    
            Integer callId = activeCalls.get(senderPhone);
            if (callId != null) {
                callDao.updateStatus(callId, "ENDED");
                activeCalls.remove(senderPhone);
                activeCalls.remove(otherPhone);
                
                persistCallNotice(senderId, senderPhone, otherId, "audio/video", "Appel terminé");
            }
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

    private void persistCallNotice(int callerId, String callerPhone, int calleeId, String callType, String action) {
        String content = "📞 " + action + " (" + callType + ")";
        Message notice = Message.text(callerId, callerPhone, calleeId, content);
        int saved = messageDao.save(notice, null);
        if (saved != -1) {
            // Also notify the receiver instantly if online
            ClientHandler calleeHandler = ChatServer.clients.get(calleeId);
            if (calleeHandler != null) {
                try {
                    calleeHandler.send("text", callerPhone, "", content.getBytes(StandardCharsets.UTF_8));
                    messageDao.updateEtat(saved, "DELIVERED");
                } catch (Exception e) {}
            }
        } else {
            System.err.println("[CallService] Impossible de sauvegarder la notification : " + action);
        }
    }
}