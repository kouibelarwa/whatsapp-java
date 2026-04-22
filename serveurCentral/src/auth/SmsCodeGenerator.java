package auth;

import server.SmsApiServer;

import java.util.Random;

/**
 * SmsCodeGenerator : génère un code de vérification à 6 chiffres.
 *
 * CÔTÉ CLIENT : génère le code et le transmets au serveur via le socket.
 * CÔTÉ SERVEUR : le serveur reçoit ce code et le stocke en base de données.
 *
 * Dans une vraie app : ce code serait généré CÔTÉ SERVEUR et envoyé via
 * une API SMS (Twilio, etc.). Ici pour le projet, le serveur génère et stocke,
 * et "simule" l'envoi SMS en affichant le code dans la console serveur.
 */
public class SmsCodeGenerator {

    private static final int CODE_LENGTH = 6;
    private static final Random random = new Random();

    /**
     * Génère un code numérique aléatoire à 6 chiffres.
     * Exemple : "483920"
     *
     * @return code sous forme de String (avec zéros initiaux si nécessaire)
     */
    public static String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10)); // chiffre entre 0 et 9
        }
        return code.toString();
    }

    /**
     * Valide le format d'un code (6 chiffres uniquement).
     *
     * @param code code saisi par l'utilisateur
     * @return true si le format est valide
     */
    public static boolean isValidFormat(String code) {
        if (code == null) return false;
        return code.matches("\\d{6}");
    }

//    public static String generateAndStore(String phone) {
//        String code = generateCode();
//        SmsApiServer.storeCode(phone, code);
//        return code;
//    }
}