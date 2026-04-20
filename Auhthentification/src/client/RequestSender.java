package client;

/**
 * RequestSender : formatte et envoie les requêtes au serveur via SocketManager.
 *
 * Centralise tous les types de messages envoyés pour éviter les erreurs
 * de protocole (format : receiver:type:content comme attendu par ClientHandler).
 */
public class RequestSender {

    private final SocketManager socketManager;

    public RequestSender() {
        this.socketManager = SocketManager.getInstance();
    }

    /**
     * Envoie un message texte.
     * Format : receiver:TEXT:content
     */
    public void sendText(String receiver, String content) {
        socketManager.send(receiver + ":TEXT:" + content);
    }

    /**
     * Envoie un fichier (nom/chemin du fichier).
     * Format : receiver:FILE:fileName
     */
    public void sendFile(String receiver, String fileName) {
        socketManager.send(receiver + ":FILE:" + fileName);
    }

    /**
     * Envoie un message audio.
     * Format : receiver:AUDIO:audioData
     */
    public void sendAudio(String receiver, String audioData) {
        socketManager.send(receiver + ":AUDIO:" + audioData);
    }

    /**
     * Initie un appel.
     * Format : receiver:CALL:INIT
     */
    public void sendCallRequest(String receiver) {
        socketManager.send(receiver + ":CALL:INIT");
    }

    /**
     * Envoie une vidéo.
     * Format : receiver:VIDEO:videoData
     */
    public void sendVideo(String receiver, String videoData) {
        socketManager.send(receiver + ":VIDEO:" + videoData);
    }
}