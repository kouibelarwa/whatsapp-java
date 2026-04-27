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
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class CallView {

    private final String contactName;
    private final String contactPhone;
    private final String callType;
    private final boolean isIncoming;
    private final Runnable onDecline;
    private final Runnable onHangup;

    private Stage stage;
    private ImageView remoteVideoView;
    private ImageView localVideoView;
    private Label statusLbl;
    private HBox btnBox;

    // A/V Components
    private Webcam webcam;
    private Thread videoThread;
    private Thread audioThread;
    
    private TargetDataLine audioInput;
    private SourceDataLine audioOutput;

    private boolean isCallActive = false;
    private boolean isHardwareActive = false;

    public CallView(String contactName, String contactPhone, String callType, boolean isIncoming, Runnable onDecline, Runnable onHangup) {
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.callType = callType;
        this.isIncoming = isIncoming;
        this.onDecline = onDecline;
        this.onHangup = onHangup;
    }

    public void start(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1c2620; -fx-border-color: #25D366; -fx-border-width: 2px; -fx-background-radius: 10px; -fx-border-radius: 10px;");

        Label typeLbl = new Label("video".equals(callType) ? "📹 Appel Vidéo" : "📞 Appel Audio");
        typeLbl.setStyle("-fx-text-fill: gray; -fx-font-size: 14px;");

        Label nameLbl = new Label(contactName);
        nameLbl.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");

        statusLbl = new Label(isIncoming ? "Appel entrant..." : "Appel en cours...");
        statusLbl.setStyle("-fx-text-fill: #25D366; -fx-font-size: 14px;");

        // Remote Video (Main)
        remoteVideoView = new ImageView();
        remoteVideoView.setFitWidth(400);
        remoteVideoView.setFitHeight(300);
        remoteVideoView.setPreserveRatio(true);
        remoteVideoView.setStyle("-fx-background-color: black;");

        // Local Video (Small Preview)
        localVideoView = new ImageView();
        localVideoView.setFitWidth(100);
        localVideoView.setFitHeight(75);
        localVideoView.setPreserveRatio(true);
        localVideoView.setStyle("-fx-border-color: #25D366; -fx-border-width: 1px;");

        StackPane videoContainer = new StackPane();
        videoContainer.setAlignment(Pos.BOTTOM_RIGHT);
        videoContainer.getChildren().addAll(remoteVideoView, localVideoView);
        StackPane.setMargin(localVideoView, new Insets(10));

        btnBox = new HBox(20);
        btnBox.setAlignment(Pos.CENTER);

        if (isIncoming) {
            Button btnAccept = new Button("Accepter");
            btnAccept.setStyle("-fx-background-color: #25D366; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px;");
            btnAccept.setPrefSize(100, 40);
            btnAccept.setOnAction(e -> acceptCall());

            Button btnReject = new Button("Refuser");
            btnReject.setStyle("-fx-background-color: #dc3c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px;");
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
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.show();
    }

    private Button createEndButton() {
        Button btnEnd = new Button("Raccrocher");
        btnEnd.setStyle("-fx-background-color: #dc3c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px;");
        btnEnd.setPrefSize(120, 40);
        btnEnd.setOnAction(e -> endCall());
        return btnEnd;
    }

    private void acceptCall() {
        SocketManager.getInstance().sendBinary("CALL_SIGNAL", contactPhone, "", ("CALL_ACCEPT:" + contactPhone).getBytes(StandardCharsets.UTF_8));
        startCallSession();
    }

    public void startCallSession() {
        Platform.runLater(() -> {
            statusLbl.setText("En ligne (00:00)");
            btnBox.getChildren().clear();
            btnBox.getChildren().add(createEndButton());
        });
        isCallActive = true;
        if (!isHardwareActive) {
            activateHardware(true);
        }
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
                    byte[] buffer = new byte[1024];
                    while (isHardwareActive) {
                        int bytesRead = audioInput.read(buffer, 0, buffer.length);
                        if (bytesRead > 0 && isCallActive) {
                            byte[] packet = new byte[bytesRead];
                            System.arraycopy(buffer, 0, packet, 0, bytesRead);
                            SocketManager.getInstance().sendBinary("CALL_AUDIO", contactPhone, "", packet);
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
                        BufferedImage image = webcam.getImage();
                        if (image != null) {
                            WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                            Platform.runLater(() -> localVideoView.setImage(fxImage));

                            if (isCallActive) {
                                try {
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    ImageIO.write(image, "jpg", baos);
                                    byte[] frameData = baos.toByteArray();
                                    SocketManager.getInstance().sendBinary("CALL_VIDEO", contactPhone, "", frameData);
                                } catch (Exception e) { e.printStackTrace(); }
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

    public void receiveVideo(byte[] data) {
        if (isCallActive) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                if (image != null) {
                    WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                    Platform.runLater(() -> remoteVideoView.setImage(fxImage));
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void endCall() {
        boolean wasActive = isCallActive;
        isCallActive = false;
        isHardwareActive = false;
        
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