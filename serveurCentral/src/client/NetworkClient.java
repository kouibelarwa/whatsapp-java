package client;

import java.io.*;
import java.net.Socket;

public class NetworkClient {

    private final String host;
    private final int    port;

    private Socket         socket;
    private PrintWriter    out;
    private BufferedReader in;

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Ouvre la connexion (appelé une seule fois au démarrage) */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        out    = new PrintWriter(socket.getOutputStream(), true);
        in     = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
    }

    /** Envoie une ligne et retourne la réponse */
    public String send(String message) {
        try {
            out.println(message);
            return in.readLine();
        } catch (Exception e) {
            System.err.println("Network error: " + e.getMessage());
            return null;
        }
    }

    public void close() {
        try { if (socket != null) socket.close(); }
        catch (IOException ignored) {}
    }
}