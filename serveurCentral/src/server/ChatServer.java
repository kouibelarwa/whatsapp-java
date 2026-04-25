package server;

import service.CallService;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    /**
     * Map userId (INT) → ClientHandler.
     * Clé = ID entier unique, pas username (qui peut se répéter).
     */
    public static final ConcurrentHashMap<Integer, ClientHandler> clients =
            new ConcurrentHashMap<>();

    /** CallService partagé par tous les threads. */
    public static final CallService callService = new CallService();

    public static void main(String[] args) throws Exception {
        SmsApiServer.start();
        ServerSocket serverSocket = new ServerSocket(5000);
        System.out.println("[ChatServer] Démarré sur le port 5000");

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("[ChatServer] Connexion : "
                    + socket.getInetAddress());
            new ClientHandler(socket).start();
        }
    }
}