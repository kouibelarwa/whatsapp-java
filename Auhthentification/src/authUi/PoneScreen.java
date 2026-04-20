package auth.ui;

import auth.AuthService;
import auth.SessionManager;
import client.NetworkClient;
import client.SocketManager;

import java.util.Scanner;

/**
 * PhoneScreen (Écran ①) : Premier écran affiché si pas de session.
 *
 * LOGIQUE DE DÉMARRAGE :
 * ┌─────────────────────────────────────────────────┐
 * │  App démarre                                    │
 * │       ↓                                         │
 * │  SessionManager.hasSession() ?                  │
 * │       ├── OUI → connexion directe (skip auth)   │
 * │       └── NON → PhoneScreen (Écran ①)           │
 * └─────────────────────────────────────────────────┘
 *
 * L'utilisateur entre son numéro → serveur génère code SMS
 * → CountryPickerComponent aide à choisir l'indicatif pays
 * → Si SMS_SENT → ProfileScreen (Écran ②)
 */
public class PhoneScreen {

    private final AuthService authService = new AuthService();
    private final Scanner scanner = new Scanner(System.in);

    /**
     * Point d'entrée principal de l'application.
     * Vérifie la session avant d'afficher quoi que ce soit.
     */
    public void launch() {
        // ═══ VÉRIFICATION SESSION (une seule auth pour toute la vie) ═══
        if (SessionManager.hasSession()) {
            String savedUsername = SessionManager.getSavedUsername();
            String savedPhone = SessionManager.getSavedPhone();
            System.out.println("✅ Session trouvée pour: " + savedUsername);
            System.out.println("🚀 Connexion directe en cours...");

            // Connexion directe sans auth SMS
            NetworkClient networkClient = new NetworkClient();
            if (networkClient.connectWithSession(savedUsername)) {
                SocketManager.getInstance().init(
                        networkClient.getSocket() == null ? null : null, // géré en interne
                        null, null, savedUsername
                );
                // → Lancer l'écran principal de chat
                System.out.println("🎉 Bienvenue " + savedUsername + "! Vous êtes connecté.");
                launchChatScreen(savedUsername);
            } else {
                System.out.println("⚠️ Échec connexion. Réauthentification requise.");
                SessionManager.clearSession();
                showPhoneInput(); // Retour à l'auth
            }
            return;
        }

        // ═══ PAS DE SESSION → Afficher Écran ① ═══
        showPhoneInput();
    }

    /**
     * Affiche l'écran de saisie du numéro de téléphone (Écran ①).
     */
    private void showPhoneInput() {
        System.out.println("\n╔══════════════════════════════╗");
        System.out.println("║     WhatsApp Java - Auth     ║");
        System.out.println("╚══════════════════════════════╝");
        System.out.println("\n📱 Entrez votre numéro de téléphone");
        System.out.println("   (avec indicatif pays, ex: +212612345678)");

        // CountryPickerComponent affiche les indicatifs disponibles
        CountryPickerComponent.showCountryCodes();

        System.out.print("\nNuméro: ");
        String phone = scanner.nextLine().trim();

        if (!isValidPhone(phone)) {
            System.out.println("❌ Numéro invalide. Réessayez.");
            showPhoneInput();
            return;
        }

        System.out.println("\n⏳ Envoi du code SMS à " + phone + "...");

        // Envoyer numéro au serveur → serveur génère code et le stocke en BD
        boolean smsSent = authService.sendPhoneNumber(phone);

        if (smsSent) {
            System.out.println("✅ Code SMS envoyé!");
            // → Passer à l'Écran ②
            ProfileScreen profileScreen = new ProfileScreen(authService, phone);
            profileScreen.show();
        } else {
            System.out.println("❌ Échec envoi SMS. Vérifiez votre numéro.");
            showPhoneInput();
        }
    }

    /**
     * Valide le format basique d'un numéro international.
     */
    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("\\+?[0-9]{8,15}");
    }

    /**
     * Lance l'écran principal de chat (post-authentification).
     * Dans une vraie app avec UI : ouvre la fenêtre de chat.
     */
    private void launchChatScreen(String username) {
        System.out.println("\n════════════════════════════════");
        System.out.println("  Chat en ligne | " + username);
        System.out.println("  Format: destinataire:message");
        System.out.println("  'quit' pour se déconnecter");
        System.out.println("════════════════════════════════\n");

        // Mode console simple pour tester
        // Dans un vrai projet : lancer ProfileScreen ou MainChatScreen avec GUI
    }
}