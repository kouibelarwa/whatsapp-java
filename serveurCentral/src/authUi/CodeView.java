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
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

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
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(35, 50, 35, 50));
        root.setStyle("-fx-background-color: #121212;");

        Label title = new Label("Vérification");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label sub = new Label("Code envoyé au : " + phone);
        sub.setStyle("-fx-text-fill: #969696; -fx-font-size: 12px;");

        TextField codeField = new TextField();
        codeField.setPromptText("Code SMS");
        codeField.setStyle(
                "-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #25D366; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        codeField.setPrefHeight(40);
        codeField.setAlignment(Pos.CENTER);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Votre nom (pseudo)");
        usernameField.setStyle(
                "-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #3c3c3c; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        usernameField.setPrefHeight(40);

        Label statusLabel = new Label(" ");
        statusLabel.setTextFill(Color.RED);

        Button btnVerify = new Button("Vérifier");
        btnVerify.setStyle(
                "-fx-background-color: #25D366; -fx-text-fill: black; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 5px;");
        btnVerify.setPrefHeight(42);
        btnVerify.setMaxWidth(Double.MAX_VALUE);

        btnVerify.setOnAction(
                e -> verifyCode(codeField.getText(), usernameField.getText(), btnVerify, statusLabel, stage));
        usernameField.setOnAction(
                e -> verifyCode(codeField.getText(), usernameField.getText(), btnVerify, statusLabel, stage));
        codeField.setOnAction(e -> usernameField.requestFocus());

        root.getChildren().addAll(title, sub, codeField, usernameField, btnVerify, statusLabel);

        Scene scene = new Scene(root, 400, 340);
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
                SocketManager.getInstance().setUserPhone(phoneResult);
                SocketManager.getInstance().setUserId(userId);

                Platform.runLater(() -> {
                    stage.close();
                    new ChatView(userId, phoneResult, usernameResult, network).start(new Stage());
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