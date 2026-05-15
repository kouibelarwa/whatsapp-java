package authUi;

import client.SocketManager;
import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.control.TextInputDialog;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallView {

    private final String contactName;
    private final String contactPhone;
    private final String callType;
    private final boolean isIncoming;
    private final Runnable onDecline;
    private final Runnable onHangup;
    private final SocketManager socketManager;

    private Stage stage;
    private TilePane remoteVideoPane;
    private java.util.Map<String, ImageView> remoteVideoMap = new java.util.HashMap<>();
    private Set<String> targetPhones = new HashSet<>();
    private ImageView localVideoView;
    private Label statusLbl;
    private HBox btnBox;

    // A/V Components
    private Webcam webcam;
    private Thread videoThread;
    private Thread audioThread;
    private Thread timerThread;

    private TargetDataLine audioInput;
    private SourceDataLine audioOutput;

    private boolean isCallActive = false;
    private boolean isHardwareActive = false;
    
    private Button btnMute;
    private Button btnCamera;
    private boolean isMuted = false;
    private boolean isCameraOff = false;
    private int callDurationSeconds = 0;
    private final ExecutorService mediaExecutor = Executors.newCachedThreadPool();

    public CallView(String contactName, String contactPhone, String callType, boolean isIncoming, Runnable onDecline, Runnable onHangup, SocketManager socketManager) {
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.callType = callType;
        this.isIncoming = isIncoming;
        this.onDecline = onDecline;
        this.onHangup = onHangup;
        this.socketManager = socketManager;
        this.targetPhones.add(contactPhone);
    }

    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Appel - " + contactName);
        stage.setOnCloseRequest(e -> endCall());

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E9EDEF; -fx-border-width: 1px; -fx-background-radius: 10px; -fx-border-radius: 10px;");

        Label typeLbl = new Label("video".equals(callType) ? "📹 Appel Vidéo" : "📞 Appel Audio");
        typeLbl.setStyle("-fx-text-fill: #667781; -fx-font-size: 14px;");

        Label nameLbl = new Label(contactName);
        nameLbl.setStyle("-fx-text-fill: #111B21; -fx-font-size: 24px; -fx-font-weight: bold;");

        statusLbl = new Label(isIncoming ? "Appel entrant..." : "Appel en cours...");
        statusLbl.setStyle("-fx-text-fill: #00A884; -fx-font-size: 14px;");

        // Remote Video (Main / Multiple)
        remoteVideoPane = new TilePane();
        remoteVideoPane.setAlignment(Pos.CENTER);
        remoteVideoPane.setHgap(10);
        remoteVideoPane.setVgap(10);
        remoteVideoPane.setPrefColumns(1);
        remoteVideoPane.setPrefSize(400, 300);
        remoteVideoPane.setStyle("-fx-background-color: #F0F2F5;");

        // Local Video (Small Preview)
        localVideoView = new ImageView();
        localVideoView.setFitWidth(100);
        localVideoView.setFitHeight(75);
        localVideoView.setPreserveRatio(true);
        localVideoView.setStyle("-fx-border-color: #00A884; -fx-border-width: 1px;");

        StackPane videoContainer = new StackPane();
        videoContainer.setAlignment(Pos.BOTTOM_RIGHT);
        videoContainer.getChildren().addAll(remoteVideoPane, localVideoView);
        StackPane.setMargin(localVideoView, new Insets(10));

        btnBox = new HBox(20);
        btnBox.setAlignment(Pos.CENTER);

        if (isIncoming) {
            Button btnAccept = new Button("Accepter");
            btnAccept.setStyle("-fx-background-color: #00A884; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
            btnAccept.setPrefSize(100, 40);
            btnAccept.setOnAction(e -> acceptCall());

            Button btnReject = new Button("Refuser");
            btnReject.setStyle("-fx-background-color: #EA0038; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
            btnReject.setPrefSize(100, 40);
            btnReject.setOnAction(e -> endCall());

            btnBox.getChildren().addAll(btnAccept, btnReject);
        } else {
            btnBox.getChildren().add(createEndButton());
            // Si c'est nous qui appelons, on allume la caméra locale immédiatement
            activateHardware(false);
        }

        if ("video".equals(callType)) {
            root.getChildren().addAll(typeLbl, nameLbl, statusLbl, videoContainer, btnBox);
        } else {
            root.getChildren().addAll(typeLbl, nameLbl, statusLbl, btnBox);
        }

        Scene scene = new Scene(root, 440, "video".equals(callType) ? 550 : 300);
        stage.setScene(scene);
        stage.show();
    }

    private Button createEndButton() {
        Button btnEnd = new Button("Raccrocher");
        btnEnd.setStyle("-fx-background-color: #EA0038; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
        btnEnd.setPrefSize(120, 40);
        btnEnd.setOnAction(e -> endCall());
        return btnEnd;
    }

    private void acceptCall() {
        socketManager.sendBinary("CALL_SIGNAL", contactPhone, "", ("CALL_ACCEPT:" + contactPhone).getBytes(StandardCharsets.UTF_8));
        startCallSession();
    }

    public void startCallSession() {
        if (isCallActive) return;

        Platform.runLater(() -> {
            statusLbl.setText("En ligne (00:00)");
            btnBox.getChildren().clear();
            
            btnMute = new Button("🔇 Couper Micro");
            btnMute.setStyle("-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
            btnMute.setPrefSize(130, 40);
            btnMute.setOnAction(e -> toggleMute());
            
            btnCamera = new Button("🚫 Couper Caméra");
            btnCamera.setStyle("-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
            btnCamera.setPrefSize(140, 40);
            btnCamera.setOnAction(e -> toggleCamera());
            
            Button btnAddUser = new Button("➕ Ajouter");
            btnAddUser.setStyle("-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
            btnAddUser.setPrefSize(100, 40);
            btnAddUser.setOnAction(e -> handleAddParticipant());
            
            Button btnEnd = createEndButton();

            if ("video".equals(callType)) {
                btnBox.getChildren().addAll(btnMute, btnCamera, btnAddUser, btnEnd);
            } else {
                btnBox.getChildren().addAll(btnMute, btnAddUser, btnEnd);
            }
            
            startTimer();
        });
        isCallActive = true;
        if (!isHardwareActive) {
            activateHardware(true);
        }
    }

    private void handleAddParticipant() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ajouter un participant");
        dialog.setHeaderText("Entrez le numéro du nouveau participant :");
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String newPhone = result.get().trim().replaceAll("[\\s\\-()]", "");
            targetPhones.add(newPhone);
            socketManager.sendBinary("CALL_SIGNAL", newPhone, "",
                    ("CALL_REQUEST:" + callType.toUpperCase() + ":" + newPhone).getBytes(StandardCharsets.UTF_8));
        }
    }

    public void removeParticipant(String phone) {
        synchronized (targetPhones) {
            Platform.runLater(() -> {
                ImageView iv = remoteVideoMap.remove(phone);
                if (iv != null && remoteVideoPane != null) {
                    remoteVideoPane.getChildren().remove(iv);
                    updateLayout();
                }
            });
        }
    }

    public void handleActiveList(String list) {
        // list = "phone1,phone2,..."
        String[] phones = list.split(",");
        for (String p : phones) {
            if (p.isEmpty() || p.equals(socketManager.getUserPhone())) continue;
            // No need to add to targetPhones since we send to the group ID
            // But we can pre-init the UI if we want
        }
    }

    public void handleJoined(String phone) {
        if (phone.equals(socketManager.getUserPhone())) return;
        Platform.runLater(() -> statusLbl.setText(phone + " a rejoint l'appel"));
    }

    public void handleTerminated() {
        Platform.runLater(() -> {
            statusLbl.setText("Appel terminé (seul)");
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (Exception e) {}
                Platform.runLater(this::endCall);
            }).start();
        });
    }

    private void updateLayout() {
        int count = remoteVideoMap.size();
        double width = count <= 1 ? 400 : (count <= 4 ? 190 : 120);
        double height = count <= 1 ? 300 : (count <= 4 ? 140 : 90);
        
        for (ImageView rv : remoteVideoMap.values()) {
            rv.setFitWidth(width);
            rv.setFitHeight(height);
        }
        
        if (count > 4) {
            remoteVideoPane.setPrefColumns(3);
        } else if (count > 1) {
            remoteVideoPane.setPrefColumns(2);
        } else {
            remoteVideoPane.setPrefColumns(1);
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;
        btnMute.setText(isMuted ? "🎤 Activer Micro" : "🔇 Couper Micro");
        btnMute.setStyle(isMuted ? "-fx-background-color: #E9EDEF; -fx-text-fill: #111B21; -fx-font-weight: bold; -fx-background-radius: 20px;" : "-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-font-weight: bold; -fx-background-radius: 20px;");
    }

    private void toggleCamera() {
        isCameraOff = !isCameraOff;
        btnCamera.setText(isCameraOff ? "📹 Activer Caméra" : "🚫 Couper Caméra");
        btnCamera.setStyle(isCameraOff ? "-fx-background-color: #E9EDEF; -fx-text-fill: #111B21; -fx-font-weight: bold; -fx-background-radius: 20px;" : "-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-font-weight: bold; -fx-background-radius: 20px;");
        if (isCameraOff) {
            Platform.runLater(() -> localVideoView.setImage(null));
        }
    }

    private void startTimer() {
        callDurationSeconds = 0;
        timerThread = new Thread(() -> {
            while (isCallActive) {
                try {
                    Thread.sleep(1000);
                    callDurationSeconds++;
                    int mins = callDurationSeconds / 60;
                    int secs = callDurationSeconds % 60;
                    String timeStr = String.format("%02d:%02d", mins, secs);
                    Platform.runLater(() -> statusLbl.setText("En ligne (" + timeStr + ")"));
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        timerThread.setDaemon(true);
        timerThread.start();
    }

    private void activateHardware(boolean startSending) {
        isHardwareActive = true;

        // Audio
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
            DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);

            if (AudioSystem.isLineSupported(targetInfo)) {
                audioInput = (TargetDataLine) AudioSystem.getLine(targetInfo);
                audioInput.open(format);
                audioInput.start();

                audioThread = new Thread(() -> {
                    byte[] buffer = new byte[640]; // Smaller buffer for lower latency (20ms at 16kHz)
                    while (isHardwareActive) {
                        int bytesRead = audioInput.read(buffer, 0, buffer.length);
                        if (bytesRead > 0 && isCallActive && !isMuted) {
                            final byte[] packet = new byte[bytesRead];
                            System.arraycopy(buffer, 0, packet, 0, bytesRead);
                            // Direct send for audio to minimize lag
                            socketManager.sendBinary("CALL_AUDIO", contactPhone, "", packet);
                        }
                    }
                });
                audioThread.start();
            }

            if (AudioSystem.isLineSupported(sourceInfo)) {
                audioOutput = (SourceDataLine) AudioSystem.getLine(sourceInfo);
                audioOutput.open(format);
                audioOutput.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Vidéo
        if ("video".equals(callType)) {
            new Thread(() -> {
                webcam = Webcam.getDefault();
                if (webcam != null) {
                    webcam.open();
                    while (isHardwareActive) {
                        if (!isCameraOff) {
                            BufferedImage image = webcam.getImage();
                            if (image != null) {
                                WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                                Platform.runLater(() -> localVideoView.setImage(fxImage));

                                if (isCallActive) {
                                    try {
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        // Use slightly lower quality for video to save bandwidth/latency
                                        ImageIO.write(image, "jpg", baos);
                                        final byte[] frameData = baos.toByteArray();
                                        socketManager.sendBinary("CALL_VIDEO", contactPhone, "", frameData);
                                    } catch (Exception e) { e.printStackTrace(); }
                                }
                            }
                        }
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {} // ~10 FPS
                    }
                }
            }).start();
        }
    }

    public void receiveAudio(byte[] data) {
        if (audioOutput != null && isCallActive) {
            audioOutput.write(data, 0, data.length);
        }
    }

    public void receiveVideo(byte[] data, String sender) {
        if (isCallActive) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                if (image != null) {
                    WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                    Platform.runLater(() -> {
                        ImageView rv = remoteVideoMap.get(sender);
                        if (rv == null) {
                            rv = new ImageView();
                            rv.setPreserveRatio(true);
                            rv.setStyle("-fx-border-color: #00A884; -fx-border-width: 1px;");
                            remoteVideoMap.put(sender, rv);
                            remoteVideoPane.getChildren().add(rv);
                            updateLayout(); // Resize all views
                        }
                        
                        rv.setImage(fxImage);
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void endCall() {
        boolean wasActive = isCallActive;
        isCallActive = false;
        isHardwareActive = false;

        if (timerThread != null) {
            timerThread.interrupt();
        }
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        if (audioInput != null) {
            audioInput.stop();
            audioInput.close();
        }
        if (audioOutput != null) {
            audioOutput.stop();
            audioOutput.close();
        }

        if (wasActive) {
            if (onHangup != null) onHangup.run();
        } else if (isIncoming) {
            if (onDecline != null) onDecline.run();
        } else if (onHangup != null) {
            onHangup.run();
        }
        Platform.runLater(() -> stage.close());
    }
}