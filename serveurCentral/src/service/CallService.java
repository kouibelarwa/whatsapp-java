package service;

import dao.CallDao;
import dao.UserDao;
import server.ChatServer;
import server.ClientHandler;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * CallService — signaux d'appel par IDs.
 *
 * PROTOCOLE :
 *   Client → Serveur : CALL_SIGNAL | data = "CALL_REQUEST:targetPhone"
 *                                          | "CALL_ACCEPT:callerPhone"
 *                                          | "CALL_REJECT:callerPhone"
 *                                          | "CALL_END:otherPhone"
 *
 *   Serveur → Client : CALL_SIGNAL | data = "CALL_INCOMING:callerPhone"
 *                                          | "CALL_ACCEPTED:calleePhone"
 *                                          | "CALL_REJECTED:calleePhone"
 *                                          | "CALL_ENDED:otherPhone"
 *                                          | "CALL_MISSED:otherPhone"
 */
public class CallService {

    private static final int RING_TIMEOUT_SECONDS = 30;

    private final CallDao dao     = new CallDao();
    private final UserDao userDao = new UserDao();

    // clé = "callerId:calleeId" (IDs entiers)
    private final ConcurrentHashMap<String, Integer>          activeCalls =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeouts  =
            new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4);

    // ── CALL_REQUEST ─────────────────────────────────────────────

    public void handleRequest(int callerId, String callerPhone,
                              String targetPhone) {
        // Résoudre l'ID du destinataire depuis son phone
        int calleeId = userDao.getIdByPhone(targetPhone);
        if (calleeId == -1) {
            sendSignal(callerId, "CALL_MISSED", targetPhone);
            return;
        }

        ClientHandler calleeHandler = ChatServer.clients.get(calleeId);
        int callId = dao.createCall(callerId, calleeId);
        String key  = pairKey(callerId, calleeId);
        activeCalls.put(key, callId);

        if (calleeHandler == null) {
            // Offline → MISSED immédiat
            dao.markMissed(callId);
            activeCalls.remove(key);
            sendSignal(callerId, "CALL_MISSED", targetPhone);
            System.out.println("[Call] " + targetPhone + " offline → MISSED");
            return;
        }

        // Envoyer signal au destinataire
        sendSignalById(calleeId, "CALL_INCOMING", callerPhone);

        // Timer MISSED 30s
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (activeCalls.containsKey(key)) {
                activeCalls.remove(key);
                dao.markMissed(callId);
                sendSignal(callerId,  "CALL_MISSED", targetPhone);
                sendSignalById(calleeId, "CALL_MISSED", callerPhone);
                System.out.println("[Call] Timeout MISSED "
                        + callerPhone + "→" + targetPhone);
            }
        }, RING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        timeouts.put(key, future);
    }

    // ── CALL_ACCEPT ──────────────────────────────────────────────

    public void handleAccept(int calleeId, String calleePhone,
                             String callerPhone) {
        int callerId = userDao.getIdByPhone(callerPhone);
        String key   = pairKey(callerId, calleeId);
        cancelTimeout(key);
        Integer callId = activeCalls.get(key);
        if (callId != null) dao.updateStatus(callId, "ACCEPTED");
        sendSignal(callerId, "CALL_ACCEPTED", calleePhone);
        System.out.println("[Call] ACCEPTED " + callerPhone + "↔" + calleePhone);
    }

    // ── CALL_REJECT ──────────────────────────────────────────────

    public void handleReject(int calleeId, String calleePhone,
                             String callerPhone) {
        int callerId = userDao.getIdByPhone(callerPhone);
        String key   = pairKey(callerId, calleeId);
        cancelTimeout(key);
        Integer callId = activeCalls.remove(key);
        if (callId != null) dao.updateStatus(callId, "REJECTED");
        sendSignal(callerId, "CALL_REJECTED", calleePhone);
        System.out.println("[Call] REJECTED " + callerPhone + "→" + calleePhone);
    }

    // ── CALL_END ─────────────────────────────────────────────────

    public void handleEnd(int senderId, String senderPhone,
                          String otherPhone) {
        int otherId  = userDao.getIdByPhone(otherPhone);
        String key   = pairKey(senderId, otherId);
        String key2  = pairKey(otherId, senderId);
        cancelTimeout(key);
        cancelTimeout(key2);
        Integer callId = activeCalls.remove(key);
        if (callId == null) callId = activeCalls.remove(key2);
        if (callId != null) dao.updateStatus(callId, "ENDED");
        sendSignalById(otherId, "CALL_ENDED", senderPhone);
        System.out.println("[Call] ENDED " + senderPhone + "↔" + otherPhone);
    }

    // ── Helpers ──────────────────────────────────────────────────

    /** Envoie un signal à un client identifié par son ID. */
    private void sendSignalById(int recipientId, String signal, String info) {
        ClientHandler h = ChatServer.clients.get(recipientId);
        if (h == null) return;
        try {
            String payload = signal + ":" + info;
            h.send("CALL_SIGNAL", info, "",
                    payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[Call] Envoi échoué vers id="
                    + recipientId + " : " + e.getMessage());
        }
    }

    /** Envoie un signal à un client identifié par son ID (int). */
    private void sendSignal(int recipientId, String signal, String info) {
        sendSignalById(recipientId, signal, info);
    }

    private void cancelTimeout(String key) {
        ScheduledFuture<?> f = timeouts.remove(key);
        if (f != null) f.cancel(false);
    }

    private String pairKey(int a, int b) {
        return a + ":" + b;
    }
}