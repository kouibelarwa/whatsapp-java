package client;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * SocketManager : Singleton qui maintient le socket actif partagé
 * entre tous les composants de l'application client.
 *
 * Permet à PhoneScreen, ProfileScreen et l'écran de chat d'accéder
 * au même socket sans le recréer.
 */
public class SocketManager {

    private static SocketManager instance;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUsername;
    private boolean authenticated = false;

    // Constructeur privé → Singleton
    private SocketManager() {}

    /**
     * Retourne l'instance unique de SocketManager.
     */
    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }

    /**
     * Initialise le SocketManager avec une connexion existante.
     * Appelé après AuthService.verifyCode() ou connectWithSession().
     */
    public void init(Socket socket, PrintWriter out, BufferedReader in, String username) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.currentUsername = username;
        this.authenticated = true;
    }

    /**
     * Envoie un message brut au serveur.
     */
    public void send(String message) {
        if (out != null) {
            out.println(message);
        } else {
            System.out.println("❌ Impossible d'envoyer: socket non initialisé.");
        }
    }

    /**
     * Vérifie si le socket est connecté et actif.
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public Socket getSocket() { return socket; }
    public PrintWriter getOut() { return out; }
    public BufferedReader getIn() { return in; }
    public String getCurrentUsername() { return currentUsername; }
    public boolean isAuthenticated() { return authenticated; }

    /**
     * Réinitialise le SocketManager (déconnexion).
     */
    public void reset() {
        try {
            if (socket != null) socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        socket = null;
        out = null;
        in = null;
        currentUsername = null;
        authenticated = false;
    }
}