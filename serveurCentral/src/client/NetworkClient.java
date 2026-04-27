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

    // Connexion automatique sécurisée
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
            // 1. On s'assure d'être connecté avant d'envoyer
            ensureConnected();

            // 2. Envoi
            out.writeUTF(message);
            out.flush();

            // 3. Réception
            return in.readUTF();
        } catch (Exception e) {
            System.err.println("[NetworkClient] ERREUR : " + e.getClass().getSimpleName()
                    + " → " + e.getMessage());
            // Si une erreur survient, on réinitialise pour la prochaine fois
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            socket = null;
            return null;
        }
    }

    public void connect() throws IOException {
        ensureConnected();
    }
    // ✅ NOUVEAU : expose le socket actif pour SocketManager
    public Socket getSocket() {
        return socket;
    }
}