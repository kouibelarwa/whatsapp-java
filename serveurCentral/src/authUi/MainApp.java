package authUi;

import auth.AuthService;
import auth.SessionManager;
import client.NetworkClient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class MainApp {

    public static void main(String[] args) {

        // ✅ Charger OpenCV UNE SEULE FOIS ici, avant tout
        try {
            System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
            System.out.println("[MainApp] OpenCV chargé : "
                    + org.opencv.core.Core.VERSION);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[MainApp] OpenCV non trouvé : " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {

            NetworkClient network = new NetworkClient("localhost", 5000);
            AuthService   auth    = new AuthService(network);

            if (SessionManager.hasSession()) {
                String savedPhone = SessionManager.getSavedPhone();
                showLoading("Reconnexion...");

                auth.reconnect(savedPhone, new AuthService.AuthCallback() {
                    @Override
                    public void onSuccess(int userId, String phone,
                                          String username, boolean isNewUser) {
                        SwingUtilities.invokeLater(() -> {
                            hideLoading();
                            new ChatView(userId, phone, username, network).show();
                        });
                    }

                    @Override
                    public void onError(String reason) {
                        SwingUtilities.invokeLater(() -> {
                            hideLoading();
                            SessionManager.clearSession();
                            new PhoneView(auth, network).show();
                        });
                    }
                });
            } else {
                new PhoneView(auth, network).show();
            }
        });
    }

    public static class ChatApplication extends Application {
        private NetworkClient network;
        private AuthService auth;

    @Override
    public void start(Stage primaryStage) {
        network = createNetworkClient();
        auth = new AuthService(network);

        if (SessionManager.hasSession()) {
            String savedPhone = SessionManager.getSavedPhone();
            
            auth.reconnect(savedPhone, new AuthService.AuthCallback() {
                @Override
                public void onSuccess(int userId, String phone, String username, boolean isNewUser) {
                    Platform.runLater(() -> {
                        new ChatView(userId, phone, username, network).start(new Stage());
                    });
                }

                @Override
                public void onError(String reason) {
                    Platform.runLater(() -> {
                        SessionManager.clearSession();
                        new PhoneView(auth, network).start(new Stage());
                    });
                }
            });
        } else {
            new PhoneView(auth, network).start(new Stage());
        }
    }
    } // Close ChatApplication

    public static NetworkClient createNetworkClient() {
        return new NetworkClient(resolveServerHost(), resolveServerPort());
    }

    public static String resolveServerHost() {
        String fromProperty = System.getProperty("chat.server.host");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        String fromEnv = System.getenv("CHAT_SERVER_HOST");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return "localhost";
    }

    public static int resolveServerPort() {
        String fromProperty = System.getProperty("chat.server.port");
        String fromEnv = System.getenv("CHAT_SERVER_PORT");
        String raw = (fromProperty != null && !fromProperty.isBlank()) ? fromProperty : fromEnv;
        if (raw != null && !raw.isBlank()) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 5000;
    }
}