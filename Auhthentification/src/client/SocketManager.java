package client;

import java.io.*;
import java.net.Socket;

public class SocketManager {

    private static SocketManager instance;

    private Socket socket;

    // AUTH
    private PrintWriter textOut;
    private BufferedReader textIn;

    // BINARY CHAT
    private DataOutputStream binOut;
    private DataInputStream binIn;

    private String username;
    private boolean authenticated = false;

    private SocketManager() {}

    public static synchronized void reset() {
        if (instance != null) {
            try { instance.socket.close(); } catch (Exception ignored) {}
            instance = null;
        }
    }

    // =========================
    // AUTH MODE (TEXT)
    // =========================
    public void initAuth(Socket socket, String username) throws IOException {
        this.socket = socket;
        this.username = username;

        this.textOut = new PrintWriter(socket.getOutputStream(), true);
        this.textIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        this.authenticated = false;
    }

    // =========================
    // SWITCH TO BINARY MODE
    // =========================
    public void enableBinaryMode() throws IOException {
        if (textOut != null) textOut.flush();
        this.binOut = new DataOutputStream(socket.getOutputStream());
        this.binIn  = new DataInputStream(socket.getInputStream());
        this.authenticated = true;
    }

    // =========================
    // SEND BINARY MESSAGE
    // =========================
    public synchronized void sendBinary(String type, String receiver, String filename, byte[] data) {

        try {
            if (binOut == null) {
                System.err.println("Binary not initialized");
                return;
            }

            binOut.writeUTF(type);
            binOut.writeUTF(receiver);
            binOut.writeUTF(username);
            binOut.writeUTF(filename == null ? "" : filename);

            binOut.writeInt(data.length);
            binOut.write(data);
            binOut.flush();

        } catch (Exception e) {
            System.err.println("sendBinary error: " + e.getMessage());
        }
    }

    // =========================
    // SEND EVENT (CALL ETC)
    // =========================
    public void sendMessage(String receiver, String text) {
        sendBinary("TEXT", receiver, "", text.getBytes());
    }
    public void sendEvent(String event) {
        textOut.println(event);
    }

    // =========================
    // LISTENER
    // =========================
    public void startListening(MessageListener listener) {

        new Thread(() -> {
            try {
                while (true) {
                    String type     = binIn.readUTF();
                    String sender   = binIn.readUTF();
                    String filename = binIn.readUTF();
                    int size        = binIn.readInt();
                    byte[] data     = new byte[size];
                    binIn.readFully(data);
                    listener.onMessage(type, sender, filename, data);  // ✅ 4 paramètres
                }
            } catch (Exception e) {
                listener.onDisconnect();
            }
        }).start();
    }

    public interface MessageListener {
        void onMessage(String type, String sender,
                       String filename, byte[] data);  // ✅ 4 paramètres
        void onDisconnect();
    }

    // GETTERS
    public String getUsername() { return username; }

    public static SocketManager getInstance() {
        return instance;
    }

    public Socket getSocket() {
        return socket;
    }

    public PrintWriter getTextOut() {
        return textOut;
    }

    public BufferedReader getTextIn() {
        return textIn;
    }

    public DataOutputStream getBinOut() {
        return binOut;
    }

    public DataInputStream getBinIn() {
        return binIn;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}