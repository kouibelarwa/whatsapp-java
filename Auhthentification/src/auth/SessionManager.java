package auth;

import java.io.*;
import java.nio.file.*;

/**
 * SessionManager : gère la session locale de l'utilisateur.
 *
 * PRINCIPE CLÉ : l'authentification SMS ne se fait QU'UNE SEULE FOIS.
 * Après AUTH_OK, on sauvegarde le username + phone dans un fichier local.
 * Au prochain lancement de l'app → si ce fichier existe, on skip l'auth
 * et on va directement à l'écran principal de chat.
 *
 * Fichier de session : ~/.whatsapp_session (dans le home de l'utilisateur)
 */
public class SessionManager {

    // Chemin du fichier de session local
    private static final String SESSION_FILE = System.getProperty("user.home") + "/.whatsapp_session";

    /**
     * Sauvegarde la session après authentification réussie.
     *
     * @param phone    numéro de téléphone associé
     */
    public static void saveSession(int userId, String phone) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(SESSION_FILE))) {
            writer.println("userId=" + userId);
            writer.println("phone=" + phone);
            System.out.println("✅ Session sauvegardée pour: " + userId);
        } catch (IOException e) {
            System.out.println("Erreur sauvegarde session: " + e.getMessage());
        }
    }

    /**
     * Vérifie si une session existe déjà (utilisateur déjà authentifié).
     *
     * @return true si le fichier de session existe et est valide
     */
    public static boolean hasSession() {
        File file = new File(SESSION_FILE);
        return file.exists() && file.length() > 0;
    }

    /**
     * Charge le username depuis la session sauvegardée.
     *
     * @return username ou null si pas de session
     */
    public static int getSavedUserId() {
        String val = readValue("userId");
        return val != null ? Integer.parseInt(val) : -1;
    }


    /**
     * Charge le numéro de téléphone depuis la session sauvegardée.
     *
     * @return phone ou null si pas de session
     */
    public static String getSavedPhone() {
        return readValue("phone");
    }

    /**
     * Supprime la session (déconnexion / réinitialisation).
     * L'utilisateur devra se ré-authentifier au prochain lancement.
     */
    public static void clearSession() {
        File file = new File(SESSION_FILE);
        if (file.exists()) {
            file.delete();
            System.out.println("🗑️ Session supprimée.");
        }
    }

    /**
     * Lit une valeur clé=valeur depuis le fichier de session.
     */
    private static String readValue(String key) {
        try (BufferedReader reader = new BufferedReader(new FileReader(SESSION_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key + "=")) {
                    return line.substring(key.length() + 1).trim();
                }
            }
        } catch (IOException e) {
            // Pas de session
        }
        return null;
    }
}