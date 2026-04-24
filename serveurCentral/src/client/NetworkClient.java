package client;

import java.io.*;
import java.net.Socket;

public class NetworkClient {

    private final String host;
    private final int    port;

    private Socket        socket;
    private PrintWriter   out;
    private BufferedReader in;

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            try { socket.close(); } catch (Exception ignored) {}
        }
        socket = new Socket(host, port);
        out    = new PrintWriter(socket.getOutputStream(), true);
        in     = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        SocketManager.getInstance().initAuth(socket, -1, null);
    }

    public String send(String message) {
        try {
            out.println(message);
            return in.readLine();
        } catch (Exception e) {
            System.err.println("Network error: " + e.getMessage());
            return null;
        }
    }

    public Socket getSocket() { return socket; }

    public void close() {
        try { if (socket != null) socket.close(); }
        catch (IOException ignored) {}
    }
}