package auth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * AuthService : gère la logique d'authentification côté client.
 * - Envoie le numéro de téléphone au serveur
 * - Reçoit la confirmation que le code SMS a été envoyé
 * - Envoie le code de vérification saisi par l'utilisateur
 * - Reçoit AUTH_OK ou AUTH_FAIL
 *
 * PRINCIPE : l'authentification se fait UNE SEULE FOIS.
 * Après AUTH_OK, SessionManager sauvegarde le username localement.
 * Au prochain lancement, si session existe → on skip l'auth.
 */
public class AuthService {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    /**
     * Étape 1 : envoyer le numéro de téléphone au serveur.
     * Le serveur génère un code SMS et l'envoie via SmsCodeGenerator.
     *
     * @param phone numéro de téléphone (ex: "+212612345678")
     * @return true si le serveur a bien reçu et envoyé le SMS
     */
    public boolean sendPhoneNumber(String phone) {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Protocole : AUTH_REQUEST:phone
            out.println("AUTH_REQUEST:" + phone);

            String response = in.readLine();
            // Serveur répond SMS_SENT si le code a été généré et stocké
            return "SMS_SENT".equals(response);

        } catch (Exception e) {
            System.out.println("Erreur connexion serveur auth: " + e.getMessage());
            return false;
        }
    }

    /**
     * Étape 2 : envoyer le code de vérification saisi par l'utilisateur.
     * Le serveur compare avec le code stocké en base.
     *
     * @param phone   numéro de téléphone
     * @param code    code à 6 chiffres saisi par l'utilisateur
     * @param username nom d'utilisateur choisi
     * @return true si AUTH_OK reçu du serveur
     */
    public boolean verifyCode(String phone, String code, String username) {
        try {
            // Protocole : VERIFY_CODE:phone:code:username
            out.println("VERIFY_CODE:" + phone + ":" + code + ":" + username);

            String response = in.readLine();
            if ("AUTH_OK".equals(response)) {
                // Sauvegarder la session localement → plus d'auth au prochain lancement
                SessionManager.saveSession(username, phone);
                return true;
            } else {
                System.out.println("Authentification échouée: " + response);
                return false;
            }
        } catch (Exception e) {
            System.out.println("Erreur vérification code: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retourne la connexion socket après auth réussie.
     * NetworkClient récupère ce socket pour la session de chat.
     */
    public Socket getSocket() {
        return socket;
    }

    public PrintWriter getOut() {
        return out;
    }

    public BufferedReader getIn() {
        return in;
    }
}