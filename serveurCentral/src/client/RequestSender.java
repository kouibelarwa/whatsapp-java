package client;

import java.nio.charset.StandardCharsets;

public class RequestSender {

    private final SocketManager sm = SocketManager.getInstance();

    public void sendText(String receiver, String message) {
        sm.sendBinary(
                "TEXT",
                receiver,
                "",
                message.getBytes(StandardCharsets.UTF_8)
        );
    }

    public void sendFile(String receiver, String filename, byte[] data) {
        sm.sendBinary("FILE", receiver, filename, data);
    }

    public void sendAudio(String receiver, String filename, byte[] data) {
        sm.sendBinary("AUDIO", receiver, filename, data);
    }

    public void sendVideo(String receiver, String filename, byte[] data) {
        sm.sendBinary("VIDEO", receiver, filename, data);
    }

    public void sendCallRequest(String receiver) {
        sm.sendEvent("CALL_REQUEST:" + receiver);
    }

    public void acceptCall(String receiver) {
        sm.sendEvent("CALL_ACCEPT:" + receiver);
    }

    public void endCall(String receiver) {
        sm.sendEvent("CALL_END:" + receiver);
    }
}