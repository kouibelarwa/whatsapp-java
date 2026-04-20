package client;

import auth.AuthService;
import auth.SessionManager;
import model.Message;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * NetworkClient : gère la connexion TCP avec le serveur APRÈS authentification.
 *
 * Flux complet :
 * 1. Vérifie si session existe (SessionManager)
 * 2. Si oui → connexion directe avec username (protocole normal de Personne 1)
 * 3. Si non → AuthService gère le flow SMS, puis récupère le socket
 *
 * Une fois connecté, NetworkClient envoie/reçoit les messages de chat.
 */
public class NetworkClient {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    /**
     * Connexion directe pour utilisateur déjà authentifié (session existante).
     * Envoie directement le username → protocole attendu par ClientHandler (Personne 1).
     *
     * @param username nom d'utilisateur sauvegardé
     * @return true si connexion réussie
     */
    public boolean connectWithSession(String username) {
        try {
            this.socket = new Socket(SERVER_HOST, SERVER_PORT);
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = username;

            // Protocole Personne 1 : première ligne = username
            out.println(username);

            // Vérifier si username accepté (pas de doublon)
            // Note: ClientHandler envoie ERROR:USERNAME_ALREADY_USED si doublon
            // On lance l'écoute en arrière-plan
            System.out.println("✅ Connecté au serveur en tant que: " + username);
            return true;

        } catch (Exception e) {
            System.out.println("Erreur connexion serveur: " + e.getMessage());
            return false;
        }
    }

    /**
     * Récupère le socket depuis AuthService après authentification réussie.
     * AuthService a déjà établi la connexion → on réutilise le même socket.
     *
     * @param authService service d'auth après AUTH_OK
     * @param username    username choisi lors de l'auth
     */
    public void connectFromAuth(AuthService authService, String username) {
        this.socket = authService.getSocket();
        this.out = authService.getOut();
        this.in = authService.getIn();
        this.username = username;
        System.out.println("✅ Session auth transférée vers NetworkClient pour: " + username);
    }

    /**
     * Envoie un message texte à un destinataire.
     * Format protocole Personne 1 : receiver:type:content
     *
     * @param receiver  destinataire
     * @param content   contenu du message
     */
    public void sendTextMessage(String receiver, String content) {
        if (out != null) {
            out.println(receiver + ":TEXT:" + content);
        }
    }

    /**
     * Envoie un message de type FILE (nom du fichier).
     */
    public void sendFileMessage(String receiver, String fileName) {
        if (out != null) {
            out.println(receiver + ":FILE:" + fileName);
        }
    }

    /**
     * Lance l'écoute des messages entrants dans un thread séparé.
     * Chaque message reçu est affiché ou transmis à l'UI.
     *
     * @param listener callback pour traiter les messages reçus
     */
    public void startListening(MessageListener listener) {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    listener.onMessageReceived(line);
                }
            } catch (Exception e) {
                System.out.println("Connexion fermée: " + e.getMessage());
                listener.onDisconnected();
            }
        }).start();
    }

    /**
     * Ferme la connexion proprement.
     */
    public void disconnect() {
        try {
            if (socket != null) socket.close();
            System.out.println("Déconnecté du serveur.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return username;
    }

    /**
     * Interface callback pour les messages entrants.
     */
    public interface MessageListener {
        void onMessageReceived(String rawMessage);
        void onDisconnected();
    }
}