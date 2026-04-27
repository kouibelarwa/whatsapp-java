package client;

import java.io.*;
import java.net.Socket;

public class NetworkClient {
    private final String host;
    private final int port;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private void ensureConnected() throws IOException {
        if (socket == null || socket.isClosed() || out == null || in == null) {
            System.out.println("[NetworkClient] Tentative connexion → " + host + ":" + port);
            socket = new Socket(host, port);
            System.out.println("[NetworkClient] Connecté ✅");
            out = new DataOutputStream(socket.getOutputStream());
            in  = new DataInputStream(socket.getInputStream());
        }
    }

    public String send(String message) {
        try {
            ensureConnected();
            out.writeUTF(message);
            out.flush();
            return in.readUTF();
        } catch (Exception e) {
            System.err.println("[NetworkClient] ERREUR : " + e.getClass().getSimpleName()
                    + " → " + e.getMessage());
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            socket = null;
            return null;
        }
    }

    public void connect() throws IOException {
        ensureConnected();
    }

    public Socket getSocket() {
        return socket;
    }
}