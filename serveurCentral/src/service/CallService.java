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


public class CallService {

    private final UserDao userDao = new UserDao();
    private final MessageDao messageDao = new MessageDao();
    private final CallDao callDao = new CallDao();
    
    // Maps callerPhone (or calleePhone) -> callId
    private final Map<String, Integer> activeCalls = new ConcurrentHashMap<>();




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

        java.util.List<ClientHandler> calleeHandlers = ChatServer.clients.get(calleeId);
        if (calleeHandlers == null || calleeHandlers.isEmpty()) {
            // Appelé hors ligne
            System.out.println("[CallService] Appelé hors ligne : " + normalizedCalleePhone);
            
            int callId = callDao.createCall(callerId, calleeId);
            if (callId != -1) callDao.markMissed(callId);
            
            persistCallNotice(callerId, callerPhone, calleeId, callType, "Appel manqué");
            notifyCaller(callerId, "CALL_MISSED:" + normalizedCalleePhone);
            return;
        }


        int callId = callDao.createCall(callerId, calleeId);
        if (callId != -1) {
            activeCalls.put(callerPhone, callId);
            activeCalls.put(calleePhone, callId);
        }


        for (ClientHandler calleeHandler : calleeHandlers) {
            try {
                String signal = "CALL_INCOMING:" + callType + ":" + callerPhone;
                calleeHandler.send(
                        "CALL_SIGNAL",
                        callerPhone,
                        "",
                        signal.getBytes(StandardCharsets.UTF_8));
                System.out.println("[CallService] Signal CALL_INCOMING envoyé à une session de "
                        + normalizedCalleePhone);
            } catch (IOException e) {
                System.err.println("[CallService] Erreur envoi CALL_INCOMING à une session : "
                        + e.getMessage());
            }
        }
    }




    public void handleAccept(int calleeId, String calleePhone, String callerPhone) {
        System.out.println("[CallService] " + calleePhone
                + " accepte l'appel de " + callerPhone);

        int callerId = userDao.getIdByPhone(callerPhone);
        if (callerId == -1) return;

        java.util.List<ClientHandler> callerHandlers = ChatServer.clients.get(callerId);
        if (callerHandlers == null) return;

        for (ClientHandler callerHandler : callerHandlers) {
            try {
                String signal = "CALL_ACCEPTED:" + calleePhone;
                callerHandler.send(
                        "CALL_SIGNAL",
                        calleePhone,
                        "",
                        signal.getBytes(StandardCharsets.UTF_8));
                System.out.println("[CallService] Appel accepté — connexion établie pour une session de "
                        + callerPhone);
            } catch (IOException e) {
                System.err.println("[CallService] Erreur CALL_ACCEPT pour une session : " + e.getMessage());
            }
        }
        
        Integer callId = activeCalls.get(callerPhone);
        if (callId != null) {
            callDao.updateStatus(callId, "ACCEPTED");
        }
    }




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




    public void handleEnd(int senderId, String senderPhone, String otherPhone) {
        System.out.println("[CallService] " + senderPhone
                + " raccroche (autre : " + otherPhone + ")");

        int otherId = userDao.getIdByPhone(otherPhone);
        if (otherId == -1) return;

        java.util.List<ClientHandler> otherHandlers = ChatServer.clients.get(otherId);
        if (otherHandlers == null) return;

        for (ClientHandler otherHandler : otherHandlers) {
            try {
                String signal = "CALL_ENDED:" + senderPhone;
                otherHandler.send(
                        "CALL_SIGNAL",
                        senderPhone,
                        "",
                        signal.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("[CallService] Erreur CALL_END pour une session : " + e.getMessage());
            }
        }
        
        Integer callId = activeCalls.get(senderPhone);
        if (callId != null) {
            callDao.updateStatus(callId, "ENDED");
            activeCalls.remove(senderPhone);
            activeCalls.remove(otherPhone);
            
            persistCallNotice(senderId, senderPhone, otherId, "audio/video", "Appel terminé");
        }
    }



    private void notifyCaller(int callerId, String signal) {
        java.util.List<ClientHandler> callerHandlers = ChatServer.clients.get(callerId);
        if (callerHandlers == null) return;
        for (ClientHandler callerHandler : callerHandlers) {
            try {
                callerHandler.send(
                        "CALL_SIGNAL", "", "",
                        signal.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("[CallService] Erreur notify caller session : " + e.getMessage());
            }
        }
    }

    private void persistCallNotice(int callerId, String callerPhone, int calleeId, String callType, String action) {
        String content = "[Appel] " + action + " (" + callType + ")";
        Message notice = Message.text(callerId, callerPhone, calleeId, content);
        int saved = messageDao.save(notice, null);
        if (saved != -1) {
            // Notifier le destinataire s'il est en ligne
            java.util.List<ClientHandler> calleeHandlers = ChatServer.clients.get(calleeId);
            if (calleeHandlers != null) {
                for (ClientHandler calleeHandler : calleeHandlers) {
                    try {
                        calleeHandler.send("text", callerPhone, "", content.getBytes(StandardCharsets.UTF_8));
                        messageDao.updateEtat(saved, "DELIVERED");
                    } catch (Exception e) {}
                }
            }
        } else {
            System.err.println("[CallService] Impossible de sauvegarder la notification : " + action);
        }
    }
    public void handleGenericSignal(int senderId, String senderPhone, String receiverPhone, String signal, String[] parts) {
        User receiver = userDao.searchByPhone(receiverPhone);
        if (receiver == null) return;

        java.util.List<ClientHandler> handlers = ChatServer.clients.get(receiver.getId());
        if (handlers == null) return;

        String fullPayload = String.join(":", parts);
        for (ClientHandler handler : handlers) {
            try {
                handler.send("CALL_SIGNAL", senderPhone, "", fullPayload.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {}
        }
    }
}