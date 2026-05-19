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
        root.setStyle("-fx-background-color: #FFFFFF;");

        Label title = new Label("WhatsApp");
        title.setStyle("-fx-text-fill: #00A884; -fx-font-size: 26px; -fx-font-weight: bold;");

        //  Server IP field
        Label ipLabel = new Label("@IP");
        ipLabel.setStyle("-fx-text-fill: #667781; -fx-font-size: 11px; -fx-font-weight: bold;");

        TextField ipField = new TextField(MainApp.resolveServerHost());
        ipField.setStyle("-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-border-color: #E9EDEF; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        ipField.setPrefHeight(36);
        ipField.setPromptText("ex: 192.168.1.10");


        Label sub = new Label("Entrez votre numéro de téléphone");
        sub.setStyle("-fx-text-fill: #667781; -fx-font-size: 13px;");

        TextField phoneField = new TextField();
        phoneField.setStyle("-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-border-color: #E9EDEF; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        phoneField.setPrefHeight(40);

        Label statusLabel = new Label(" ");
        statusLabel.setTextFill(Color.RED);

        Button btnSend = new Button("Envoyer le code");
        btnSend.setStyle("-fx-background-color: #00A884; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-cursor: hand;");
        btnSend.setPrefHeight(42);
        btnSend.setMaxWidth(Double.MAX_VALUE);

        btnSend.setOnAction(e -> sendCode(ipField.getText().trim(), phoneField.getText(), btnSend, statusLabel, stage));
        phoneField.setOnAction(e -> sendCode(ipField.getText().trim(), phoneField.getText(), btnSend, statusLabel, stage));

        root.getChildren().addAll(title, ipLabel, ipField, sub, phoneField, btnSend, statusLabel);

        Scene scene = new Scene(root, 400, 360);
        stage.setTitle("WhatsApp — Connexion");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void sendCode(String serverIp, String phoneText, Button btnSend, Label statusLabel, Stage stage) {

        String ip = serverIp.isBlank() ? MainApp.resolveServerHost() : serverIp;
        int port = MainApp.resolveServerPort();
        NetworkClient dynamicNetwork = new NetworkClient(ip, port);
        AuthService dynamicAuth = new AuthService(dynamicNetwork);

        String phone = normalizePhone(phoneText);
        if (phone.isEmpty()) {
            statusLabel.setText("Veuillez entrer un numéro.");
            return;
        }
        btnSend.setDisable(true);
        btnSend.setText("Envoi...");
        statusLabel.setText(" ");

        dynamicAuth.requestCode(phone,
                () -> Platform.runLater(() -> {
                    stage.close();
                    new CodeView(phone, dynamicAuth, dynamicNetwork).start(new Stage());
                }),
                () -> Platform.runLater(() -> {
                    statusLabel.setText("Erreur : impossible de joindre le serveur (" + ip + ")");
                    btnSend.setDisable(false);
                    btnSend.setText("Envoyer le code");
                }));
    }

    private String normalizePhone(String input) {
        if (input == null) return "";
        String normalized = input.replaceAll("[\\s\\-()]", "");
        return normalized.trim();
    }
}