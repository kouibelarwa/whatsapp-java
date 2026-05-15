package client;

import java.nio.charset.StandardCharsets;

public class RequestSender {

    private final SocketManager sm = SocketManager.getInstance();

    public void sendText(String receiver, String message) {
        sm.sendBinary("text", receiver, "",
                message.getBytes(StandardCharsets.UTF_8));
    }

    public void sendFile(String receiver, String filename, byte[] data) {
        sm.sendBinary("file", receiver, filename, data);
    }

    public void sendAudio(String receiver, String filename, byte[] data) {
        sm.sendBinary("audio", receiver, filename, data);
    }

    public void sendVideo(String receiver, String filename, byte[] data) {
        sm.sendBinary("video", receiver, filename, data);
    }

    public void sendCallRequest(String receiver) {
        sm.sendBinary("CALL_SIGNAL", receiver, "",
                ("CALL_REQUEST:" + receiver)
                        .getBytes(StandardCharsets.UTF_8));
    }

    public void acceptCall(String receiver) {
        sm.sendBinary("CALL_SIGNAL", receiver, "",
                ("CALL_ACCEPT:" + receiver)
                        .getBytes(StandardCharsets.UTF_8));
    }

    public void endCall(String receiver) {
        sm.sendBinary("CALL_SIGNAL", receiver, "",
                ("CALL_END:" + receiver)
                        .getBytes(StandardCharsets.UTF_8));
    }
}