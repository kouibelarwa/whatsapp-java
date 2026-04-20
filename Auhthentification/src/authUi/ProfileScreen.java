package auth.ui;

import auth.AuthService;
import auth.SmsCodeGenerator;
import auth.SessionManager;
import client.SocketManager;

import java.util.Scanner;

/**
 * ProfileScreen (Écran ②) : Saisie du code SMS + choix du username.
 *
 * FLUX :
 * PhoneScreen (Écran ①)
 *       ↓  SMS_SENT
 * ProfileScreen (Écran ②)
 *   - L'utilisateur entre le code reçu par SMS
 *   - L'utilisateur choisit son username
 *   - authService.verifyCode() → serveur vérifie vs base de données
 *   - Si AUTH_OK → SessionManager.saveSession() → Chat principal
 *   - Si AUTH_FAIL → message d'erreur + retenter
 */
public class ProfileScreen {

    private final AuthService authService;
    private final String phone;
    private final Scanner scanner = new Scanner(System.in);

    // Nombre de tentatives maximum
    private static final int MAX_ATTEMPTS = 3;

    public ProfileScreen(AuthService authService, String phone) {
        this.authService = authService;
        this.phone = phone;
    }

    /**
     * Affiche l'écran de vérification du code SMS (Écran ②).
     */
    public void show() {
        System.out.println("\n╔══════════════════════════════╗");
        System.out.println("║   Vérification du numéro    ║");
        System.out.println("╚══════════════════════════════╝");
        System.out.println("\n📩 Un code a été envoyé à: " + phone);

        int attempts = 0;
        boolean verified = false;

        while (attempts < MAX_ATTEMPTS && !verified) {
            // ─── Saisie du code SMS ───
            System.out.print("\nEntrez le code à 6 chiffres: ");
            String code = scanner.nextLine().trim();

            // Validation format avant d'envoyer au serveur
            if (!SmsCodeGenerator.isValidFormat(code)) {
                System.out.println("❌ Format invalide. Le code doit avoir 6 chiffres.");
                attempts++;
                continue;
            }

            // ─── Saisie du username ───
            System.out.print("Choisissez votre nom d'utilisateur: ");
            String username = scanner.nextLine().trim();

            if (username.isEmpty() || username.contains(":")) {
                System.out.println("❌ Username invalide (ne doit pas contenir ':')");
                continue;
            }

            System.out.println("\n⏳ Vérification en cours...");

            // ─── Envoi au serveur pour vérification ───
            // Serveur compare le code avec celui stocké en BD pour ce numéro
            boolean success = authService.verifyCode(phone, code, username);

            if (success) {
                verified = true;
                // SessionManager.saveSession() déjà appelé dans authService.verifyCode()
                System.out.println("\n✅ Authentification réussie!");
                System.out.println("🎉 Bienvenue " + username + "!");

                // Initialiser SocketManager avec la connexion auth
                SocketManager.getInstance().init(
                        authService.getSocket(),
                        authService.getOut(),
                        authService.getIn(),
                        username
                );

                // → Lancer l'application principale
                launchMainApp(username);

            } else {
                attempts++;
                int remaining = MAX_ATTEMPTS - attempts;
                if (remaining > 0) {
                    System.out.println("❌ Code incorrect. " + remaining + " tentative(s) restante(s).");
                } else {
                    System.out.println("❌ Trop de tentatives échouées.");
                    System.out.println("🔄 Retour à l'écran précédent...");
                    // Retour à PhoneScreen pour demander un nouveau code
                    PhoneScreen phoneScreen = new PhoneScreen();
                    phoneScreen.launch();
                }
            }
        }
    }

    /**
     * Lance l'application principale après auth réussie.
     * Dans un vrai projet : ouvre la fenêtre de chat principale.
     */
    private void launchMainApp(String username) {
        System.out.println("\n════════════════════════════════");
        System.out.println("  Application WhatsApp Java");
        System.out.println("  Connecté en tant que: " + username);
        System.out.println("════════════════════════════════");
        System.out.println("\n📌 NOTE: La prochaine fois que vous");
        System.out.println("   lancez l'app, vous serez connecté");
        System.out.println("   DIRECTEMENT sans re-authentification.");

        // Console de chat simple
        System.out.println("\nFormat: destinataire:message | 'quit' pour quitter");
        Scanner chatScanner = new Scanner(System.in);
        String input;

        while (!(input = chatScanner.nextLine()).equalsIgnoreCase("quit")) {
            String[] parts = input.split(":", 2);
            if (parts.length == 2) {
                SocketManager.getInstance().send(parts[0] + ":TEXT:" + parts[1]);
                System.out.println("➤ [" + parts[0] + "]: " + parts[1]);
            } else {
                System.out.println("Format invalide. Utilisez: destinataire:message");
            }
        }

        SocketManager.getInstance().reset();
        System.out.println("👋 Déconnecté.");
    }
}