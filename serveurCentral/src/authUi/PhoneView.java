package authUi;

import auth.AuthService;
import client.NetworkClient;
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

public class PhoneView {
    private final AuthService auth;
    private final NetworkClient network;

    public PhoneView(AuthService auth, NetworkClient network) {
        this.auth = auth;
        this.network = network;
    }
    
    public PhoneView(AuthService auth) {
        this(auth, auth.getNetwork());
    }

    public void start(Stage stage) {
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40, 50, 40, 50));
        root.setStyle("-fx-background-color: #121212;");

        Label title = new Label("WhatsApp");
        title.setStyle("-fx-text-fill: #25D366; -fx-font-size: 26px; -fx-font-weight: bold;");

        Label sub = new Label("Entrez votre numéro de téléphone");
        sub.setStyle("-fx-text-fill: #969696; -fx-font-size: 13px;");

        TextField phoneField = new TextField();
        phoneField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #25D366; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        phoneField.setPrefHeight(40);

        Label statusLabel = new Label(" ");
        statusLabel.setTextFill(Color.RED);

        Button btnSend = new Button("Envoyer le code");
        btnSend.setStyle("-fx-background-color: #25D366; -fx-text-fill: black; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 5px;");
        btnSend.setPrefHeight(42);
        btnSend.setMaxWidth(Double.MAX_VALUE);

        btnSend.setOnAction(e -> sendCode(phoneField.getText(), btnSend, statusLabel, stage));
        phoneField.setOnAction(e -> sendCode(phoneField.getText(), btnSend, statusLabel, stage));

        root.getChildren().addAll(title, sub, phoneField, btnSend, statusLabel);

        Scene scene = new Scene(root, 400, 280);
        stage.setTitle("WhatsApp — Connexion");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void sendCode(String phoneText, Button btnSend, Label statusLabel, Stage stage) {
        String phone = normalizePhone(phoneText);
        if (phone.isEmpty()) {
            statusLabel.setText("Veuillez entrer un numéro.");
            return;
        }
        btnSend.setDisable(true);
        btnSend.setText("Envoi...");
        statusLabel.setText(" ");

        auth.requestCode(phone,
                () -> Platform.runLater(() -> {
                    stage.close();
                    new CodeView(phone, auth, network).start(new Stage());
                }),
                () -> Platform.runLater(() -> {
                    statusLabel.setText("Erreur : impossible d'envoyer le code.");
                    btnSend.setDisable(false);
                    btnSend.setText("Envoyer le code");
                }));
    }

    private String normalizePhone(String input) {
        if (input == null) return "";
        String normalized = input.replaceAll("[\\s\\-()]", "");
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2);
        }
        return normalized.trim();
    }
}