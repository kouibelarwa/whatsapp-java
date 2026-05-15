package authUi;

import auth.AuthService;
import client.NetworkClient;
import client.SocketManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CodeView {
    private final String phone;
    private final AuthService auth;
    private final NetworkClient network;

    public CodeView(String phone, AuthService auth, NetworkClient network) {
        this.phone = phone;
        this.auth = auth;
        this.network = network;
    }

    public void start(Stage stage) {
        VBox root = new VBox(12);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30, 50, 30, 50));
        root.setStyle("-fx-background-color: #FFFFFF;");

        Label title = new Label("Vérification");
        title.setStyle("-fx-text-fill: #111B21; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label sub = new Label("Code envoyé au : " + phone);
        sub.setStyle("-fx-text-fill: #667781; -fx-font-size: 12px;");

        // --- Code URL Box ---
        // Use the actual server host from the network connection (IP entered by user)
        String serverHost = network.getHost();
        String codeUrl = "http://" + serverHost + ":8080/code?phone=" + phone;

        Label urlLabel = new Label("⚠ Demandez le code au serveur :");
        urlLabel.setStyle("-fx-text-fill: #667781; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label urlText = new Label(codeUrl);
        urlText.setStyle("-fx-text-fill: #00A884; -fx-font-size: 10px; -fx-font-family: 'Consolas';");
        urlText.setWrapText(true);

        Button btnCopyUrl = new Button("📋 Copier l'URL");
        btnCopyUrl.setStyle("-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-font-size: 11px; -fx-background-radius: 4px; -fx-border-color: #E9EDEF; -fx-border-radius: 4px; -fx-cursor: hand;");
        btnCopyUrl.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(codeUrl);
            Clipboard.getSystemClipboard().setContent(content);
            btnCopyUrl.setText("✓ Copié !");
        });

        Button btnFetchCode = new Button("🔄 Récupérer le code automatiquement");
        btnFetchCode.setStyle("-fx-background-color: #E7FCE3; -fx-text-fill: #00A884; -fx-font-size: 11px; -fx-background-radius: 4px; -fx-cursor: hand;");
        btnFetchCode.setMaxWidth(Double.MAX_VALUE);

        TextField codeField = new TextField();
        codeField.setPromptText("Code SMS");
        codeField.setStyle(
                "-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-border-color: #E9EDEF; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        codeField.setPrefHeight(40);
        codeField.setAlignment(Pos.CENTER);

        btnFetchCode.setOnAction(e -> {
            btnFetchCode.setText("⏳ Chargement...");
            btnFetchCode.setDisable(true);
            new Thread(() -> {
                try {
                    URL url = new URL(codeUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String response = reader.readLine();
                    reader.close();
                    // Response: "Code de verification pour +212... : 123456"
                    if (response != null && response.contains(":")) {
                        String code = response.substring(response.lastIndexOf(":") + 1).trim();
                        Platform.runLater(() -> {
                            codeField.setText(code);
                            btnFetchCode.setText("✓ Code récupéré !");
                            btnFetchCode.setDisable(false);
                        });
                    } else {
                        Platform.runLater(() -> {
                            btnFetchCode.setText("❌ Pas de code trouvé");
                            btnFetchCode.setDisable(false);
                        });
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        btnFetchCode.setText("❌ Serveur inaccessible");
                        btnFetchCode.setDisable(false);
                    });
                }
            }).start();
        });

        TextField usernameField = new TextField();
        usernameField.setPromptText("Votre nom (pseudo)");
        usernameField.setStyle(
                "-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-border-color: #E9EDEF; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        usernameField.setPrefHeight(40);

        Label statusLabel = new Label(" ");
        statusLabel.setTextFill(Color.RED);

        Button btnVerify = new Button("Vérifier");
        btnVerify.setStyle(
                "-fx-background-color: #00A884; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-cursor: hand;");
        btnVerify.setPrefHeight(42);
        btnVerify.setMaxWidth(Double.MAX_VALUE);

        btnVerify.setOnAction(
                e -> verifyCode(codeField.getText(), usernameField.getText(), btnVerify, statusLabel, stage));
        usernameField.setOnAction(
                e -> verifyCode(codeField.getText(), usernameField.getText(), btnVerify, statusLabel, stage));
        codeField.setOnAction(e -> usernameField.requestFocus());

        root.getChildren().addAll(title, sub,
                urlLabel, urlText, btnCopyUrl, btnFetchCode,
                codeField, usernameField, btnVerify, statusLabel);

        Scene scene = new Scene(root, 420, 500);
        stage.setTitle("Vérification du code");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void verifyCode(String codeText, String userText, Button btnVerify, Label statusLabel, Stage stage) {
        String code = codeText.trim();
        String username = userText.trim();

        if (code.isEmpty()) {
            statusLabel.setText("Veuillez entrer le code SMS.");
            return;
        }
        if (username.isEmpty()) {
            statusLabel.setText("Veuillez entrer votre nom.");
            return;
        }

        btnVerify.setDisable(true);
        btnVerify.setText("Vérification...");
        statusLabel.setText(" ");

        auth.verifyCode(phone, code, username, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(int userId, String phoneResult, String usernameResult, boolean isNewUser) {
                // IMPORTANT: Create a dedicated SocketManager for THIS user session
                SocketManager userSocket = new SocketManager();
                try {
                    userSocket.initAuth(network.getSocket(), userId, phoneResult);
                    userSocket.enableBinaryMode();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                Platform.runLater(() -> {
                    stage.close();
                    new ChatView(userId, phoneResult, usernameResult, network, userSocket).start(new Stage());
                });
            }

            @Override
            public void onError(String reason) {
                Platform.runLater(() -> {
                    statusLabel.setText("Code invalide : " + reason);
                    btnVerify.setDisable(false);
                    btnVerify.setText("Vérifier");
                });
            }
        });
    }
}