package authUi;

import auth.AuthService;
import auth.SessionManager;
import client.NetworkClient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class MainApp extends Application {

    private NetworkClient network;
    private AuthService auth;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        network = new NetworkClient("localhost", 5000);
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
}