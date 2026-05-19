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
import javafx.stage.Stage;
import javafx.scene.control.TextInputDialog;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private TilePane participantsGrid;
    private java.util.Map<String, ImageView> remoteVideoMap = new java.util.HashMap<>();
    private Set<String> targetPhones = new HashSet<>();
    private ImageView localVideoView;
    private Label statusLbl;
    private HBox btnBox;

    // Contact mapping to resolve participant names
    private java.util.Map<String, String> allContacts = new java.util.HashMap<>();

    // Audio mixing and speech detection
    private final java.util.Map<String, java.util.concurrent.ConcurrentLinkedQueue<byte[]>> participantAudioQueues = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> lastSpeechTime = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, StackPane> participantCards = new java.util.HashMap<>();
    
    private Thread audioPlaybackThread;
    private javafx.animation.Timeline speakingIndicatorTimeline;

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

    public void setAllContacts(java.util.Map<String, String> allContacts) {
        if (allContacts != null) {
            this.allContacts = allContacts;
        }
    }

    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Appel - " + contactName);
        stage.setOnCloseRequest(e -> endCall());

        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #111B21; -fx-border-color: #202C33; -fx-border-width: 1px; -fx-background-radius: 12px; -fx-border-radius: 12px;");

        Label typeLbl = new Label("video".equals(callType) ? "📹 Appel Vidéo de Groupe" : "📞 Appel Audio de Groupe");
        typeLbl.setStyle("-fx-text-fill: #8696a0; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label nameLbl = new Label(contactPhone.startsWith("GROUP_") ? contactName : "Appel en cours...");
        nameLbl.setStyle("-fx-text-fill: #E9EDEF; -fx-font-size: 20px; -fx-font-weight: bold;");

        statusLbl = new Label(isIncoming ? "Appel entrant..." : "Connexion...");
        statusLbl.setStyle("-fx-text-fill: #00A884; -fx-font-size: 13px; -fx-font-weight: bold;");

        // Participants Grid (Unified for Audio & Video)
        participantsGrid = new TilePane();
        participantsGrid.setAlignment(Pos.CENTER);
        participantsGrid.setHgap(10);
        participantsGrid.setVgap(10);
        participantsGrid.setPrefSize(400, 360);
        participantsGrid.setStyle("-fx-background-color: #111B21;");

        // Initialize local video view
        localVideoView = new ImageView();
        localVideoView.setPreserveRatio(true);

        btnBox = new HBox(15);
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
            activateHardware(false);
        }

        root.getChildren().addAll(typeLbl, nameLbl, statusLbl, participantsGrid, btnBox);

        updateLayout(); // Renders the initial participant card grid

        Scene scene = new Scene(root, 440, 560);
        stage.setScene(scene);
        stage.show();
    }

    private Button createEndButton() {
        Button btnEnd = new Button("Raccrocher");
        btnEnd.setStyle("-fx-background-color: #EA0038; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
        btnEnd.setPrefSize(110, 40);
        btnEnd.setOnAction(e -> endCall());
        return btnEnd;
    }

    private void acceptCall() {
        socketManager.sendBinary("CALL_SIGNAL", contactPhone, "", ("CALL_ACCEPT:" + contactPhone).getBytes(StandardCharsets.UTF_8));
        
        // Notify all peers in the mesh that we joined
        if (!contactPhone.startsWith("GROUP_")) {
            String joinedSignal = "CALL_JOINED:" + socketManager.getUserPhone();
            synchronized (targetPhones) {
                for (String tp : targetPhones) {
                    socketManager.sendBinary("CALL_SIGNAL", tp, "", (joinedSignal + ":" + tp).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        
        startCallSession();
    }

    public void startCallSession() {
        if (isCallActive) return;

        Platform.runLater(() -> {
            statusLbl.setText("En ligne (00:00)");
            btnBox.getChildren().clear();
            
            btnMute = new Button("🔇 Micro");
            btnMute.setStyle("-fx-background-color: #202C33; -fx-text-fill: #E9EDEF; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
            btnMute.setPrefSize(100, 40);
            btnMute.setOnAction(e -> toggleMute());
            
            btnCamera = new Button("🚫 Caméra");
            btnCamera.setStyle("-fx-background-color: #202C33; -fx-text-fill: #E9EDEF; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
            btnCamera.setPrefSize(100, 40);
            btnCamera.setOnAction(e -> toggleCamera());
            
            Button btnAddUser = new Button("➕ Inviter");
            btnAddUser.setStyle("-fx-background-color: #202C33; -fx-text-fill: #E9EDEF; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
            btnAddUser.setPrefSize(100, 40);
            btnAddUser.setOnAction(e -> handleAddParticipant());
            
            Button btnEnd = createEndButton();

            if ("video".equals(callType)) {
                btnBox.getChildren().addAll(btnMute, btnCamera, btnAddUser, btnEnd);
            } else {
                btnBox.getChildren().addAll(btnMute, btnAddUser, btnEnd);
            }
            
            startTimer();
            startSpeakingIndicatorTimer();
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
            
            if (contactPhone.startsWith("GROUP_")) {
                // Group calls handle routing through the server
                String syncSignal = "CALL_ADD_PARTICIPANT:" + newPhone + ":" + contactPhone;
                socketManager.sendBinary("CALL_SIGNAL", contactPhone, "", syncSignal.getBytes(StandardCharsets.UTF_8));
                
                // Request the new one to join
                targetPhones.add(newPhone);
                String activeList = socketManager.getUserPhone() + "," + String.join(",", targetPhones);
                String requestPayload = "CALL_INVITE:" + callType.toUpperCase() + ":" + contactPhone + ":" + activeList + ":" + newPhone;
                socketManager.sendBinary("CALL_SIGNAL", newPhone, "", requestPayload.getBytes(StandardCharsets.UTF_8));
                Platform.runLater(this::updateLayout);
            } else {
                // P2P Mesh logic
                String activeList = socketManager.getUserPhone() + "," + String.join(",", targetPhones);
                // Set the inviter's own phone as the caller for the new user
                String requestPayload = "CALL_INVITE:" + callType.toUpperCase() + ":" + socketManager.getUserPhone() + ":" + activeList + ":" + newPhone;
                socketManager.sendBinary("CALL_SIGNAL", newPhone, "", requestPayload.getBytes(StandardCharsets.UTF_8));
                
                // Show a toast or update status, but DO NOT add to targetPhones yet. 
                // We will add them when they explicitly broadcast CALL_JOINED.
                Platform.runLater(() -> statusLbl.setText("Invitation envoyée à " + newPhone));
            }
        }
    }

    public void removeParticipant(String phone) {
        synchronized (targetPhones) {
            targetPhones.remove(phone);
            Platform.runLater(() -> {
                remoteVideoMap.remove(phone);
                updateLayout();
            });
            if (targetPhones.isEmpty()) {
                Platform.runLater(this::endCall);
            }
        }
    }

    public void handleActiveList(String list) {
        // list = "phone1,phone2,..."
        String[] phones = list.split(",");
        synchronized (targetPhones) {
            for (String p : phones) {
                if (p.isEmpty() || p.equals(socketManager.getUserPhone())) continue;
                if (!p.startsWith("GROUP_") && !p.equals(contactPhone)) {
                    targetPhones.add(p);
                }
            }
        }
        Platform.runLater(this::updateLayout);
    }

    public void handleJoined(String phone) {
        if (phone.equals(socketManager.getUserPhone())) return;
        synchronized (targetPhones) {
            if (!phone.startsWith("GROUP_") && !phone.equals(contactPhone)) {
                targetPhones.add(phone);
            }
        }
        Platform.runLater(() -> {
            statusLbl.setText(phone + " a rejoint");
            updateLayout();
        });
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
        int count = 1; // Local user
        synchronized (targetPhones) {
            for (String phone : targetPhones) {
                if (!phone.startsWith("GROUP_")) {
                    count++;
                }
            }
        }
        
        double cardWidth, cardHeight;
        int cols;

        if (count <= 1) {
            cardWidth = 360;
            cardHeight = 320;
            cols = 1;
        } else if (count == 2) {
            cardWidth = 185;
            cardHeight = 320;
            cols = 2;
        } else if (count <= 4) {
            cardWidth = 185;
            cardHeight = 160;
            cols = 2;
        } else {
            cardWidth = 120;
            cardHeight = 100;
            cols = 3;
        }

        participantsGrid.setPrefColumns(cols);
        rebuildGrid(cardWidth, cardHeight);
    }

    private void rebuildGrid(double cardWidth, double cardHeight) {
        participantsGrid.getChildren().clear();
        participantCards.clear();

        // 1. Local User card
        String localPhone = socketManager.getUserPhone();
        boolean hasLocalVideo = "video".equals(callType) && !isCameraOff && localVideoView.getImage() != null;
        StackPane localCard = createParticipantCard(localPhone, "Moi (Vous)", cardWidth, cardHeight, hasLocalVideo, localVideoView);
        participantsGrid.getChildren().add(localCard);
        participantCards.put(localPhone, localCard);

        // 2. Remote User cards
        synchronized (targetPhones) {
            for (String phone : targetPhones) {
                if (phone.startsWith("GROUP_")) {
                    continue; // Ne pas afficher la carte du groupe lui-même
                }
                String resolvedName = allContacts.getOrDefault(phone, phone);
                ImageView rv = remoteVideoMap.get(phone);
                boolean hasRemoteVideo = "video".equals(callType) && rv != null && rv.getImage() != null;
                
                StackPane remoteCard = createParticipantCard(phone, resolvedName, cardWidth, cardHeight, hasRemoteVideo, rv);
                participantsGrid.getChildren().add(remoteCard);
                participantCards.put(phone, remoteCard);
            }
        }
    }

    private StackPane createParticipantCard(String phone, String name, double width, double height, boolean hasVideo, ImageView videoView) {
        StackPane card = new StackPane();
        card.setPrefSize(width, height);
        card.setMinSize(width, height);
        card.setMaxSize(width, height);
        card.setStyle("-fx-background-color: #1F2C34; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-border-color: #2D3D48; -fx-border-width: 1px;");

        if (hasVideo && videoView != null) {
            videoView.setFitWidth(width);
            videoView.setFitHeight(height);
            videoView.setPreserveRatio(true); // Preserve aspect ratio to avoid distortion

            // Clip video view to have rounded corners matching the card
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(width, height);
            clip.setArcWidth(24);
            clip.setArcHeight(24);
            videoView.setClip(clip);

            card.getChildren().add(videoView);

            Label nameOverlayLbl = new Label(name);
            nameOverlayLbl.setStyle("-fx-text-fill: #E9EDEF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-background-color: rgba(17, 27, 33, 0.6); -fx-background-radius: 4px; -fx-padding: 3 8 3 8;");
            StackPane.setAlignment(nameOverlayLbl, Pos.BOTTOM_CENTER);
            StackPane.setMargin(nameOverlayLbl, new Insets(8));
            card.getChildren().add(nameOverlayLbl);
        } else {
            StackPane avatar = ChatView.buildAvatar(name, height > 120 ? 64 : 44);
            Label nameCardLbl = new Label(name);
            nameCardLbl.setStyle("-fx-text-fill: #E9EDEF; -fx-font-weight: bold; -fx-font-size: " + (height > 120 ? "14px" : "11px") + ";");
            VBox centerBox = new VBox(height > 120 ? 10 : 5, avatar, nameCardLbl);
            centerBox.setAlignment(Pos.CENTER);
            card.getChildren().add(centerBox);
        }

        return card;
    }

    private void toggleMute() {
        isMuted = !isMuted;
        btnMute.setText(isMuted ? "🎤 Activer Micro" : "🔇 Micro");
        btnMute.setStyle(isMuted ? "-fx-background-color: #00A884; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px;" : "-fx-background-color: #202C33; -fx-text-fill: #E9EDEF; -fx-font-weight: bold; -fx-background-radius: 20px;");
    }

    private void toggleCamera() {
        isCameraOff = !isCameraOff;
        btnCamera.setText(isCameraOff ? "📹 Activer Caméra" : "🚫 Caméra");
        btnCamera.setStyle(isCameraOff ? "-fx-background-color: #00A884; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px;" : "-fx-background-color: #202C33; -fx-text-fill: #E9EDEF; -fx-font-weight: bold; -fx-background-radius: 20px;");
        if (isCameraOff) {
            Platform.runLater(() -> {
                localVideoView.setImage(null);
                updateLayout(); // Rebuild grid to show avatar
            });
        } else {
            Platform.runLater(this::updateLayout); // Rebuild grid to show video
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

    private void startSpeakingIndicatorTimer() {
        speakingIndicatorTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(200), ev -> {
                long now = System.currentTimeMillis();
                for (java.util.Map.Entry<String, StackPane> entry : participantCards.entrySet()) {
                    String phone = entry.getKey();
                    StackPane card = entry.getValue();
                    
                    Long lastSpoke = lastSpeechTime.get(phone);
                    boolean isSpeaking = lastSpoke != null && (now - lastSpoke) < 800; // speaking in last 800ms
                    
                    if (isSpeaking) {
                        // Apply glowing Emerald WhatsApp green border
                        card.setStyle("-fx-background-color: #1F2C34; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-border-color: #00A884; -fx-border-width: 3px;");
                    } else {
                        // Regular subtle dark card border
                        card.setStyle("-fx-background-color: #1F2C34; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-border-color: #2D3D48; -fx-border-width: 1px;");
                    }
                }
            })
        );
        speakingIndicatorTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        speakingIndicatorTimeline.play();
    }

    private void activateHardware(boolean startSending) {
        isHardwareActive = true;

        // Audio Setup
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
            DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);

            if (AudioSystem.isLineSupported(targetInfo)) {
                audioInput = (TargetDataLine) AudioSystem.getLine(targetInfo);
                audioInput.open(format);
                audioInput.start();

                audioThread = new Thread(() -> {
                    byte[] buffer = new byte[640]; // 20ms packet at 16kHz
                    while (isHardwareActive) {
                        int bytesRead = audioInput.read(buffer, 0, buffer.length);
                        if (bytesRead > 0 && isCallActive && !isMuted) {
                            final byte[] packet = new byte[bytesRead];
                            System.arraycopy(buffer, 0, packet, 0, bytesRead);
                            
                            // Detect local speaking activity
                            double sum = 0;
                            for (int i = 0; i < packet.length; i += 2) {
                                int sample = (packet[i] << 8) | (packet[i+1] & 0xFF);
                                sum += sample * sample;
                            }
                            double rms = Math.sqrt(sum / (packet.length / 2.0));
                            if (rms > 500) {
                                lastSpeechTime.put(socketManager.getUserPhone(), System.currentTimeMillis());
                            }
                            
                            // Direct send for audio to minimize lag
                            if (contactPhone.startsWith("GROUP_")) {
                                socketManager.sendBinary("CALL_AUDIO", contactPhone, "", packet);
                            } else {
                                synchronized (targetPhones) {
                                    for (String tp : targetPhones) {
                                        socketManager.sendBinary("CALL_AUDIO", tp, "", packet);
                                    }
                                }
                            }
                        }
                    }
                });
                audioThread.setDaemon(true);
                audioThread.start();
            }

            if (AudioSystem.isLineSupported(sourceInfo)) {
                audioOutput = (SourceDataLine) AudioSystem.getLine(sourceInfo);
                audioOutput.open(format);
                audioOutput.start();
                
                // Start background mixing playback thread
                audioPlaybackThread = new Thread(() -> {
                    byte[] mixBuffer = new byte[640];
                    while (isHardwareActive) {
                        boolean hasData = false;
                        int[] sumSamples = new int[320];
                        
                        for (java.util.Map.Entry<String, java.util.concurrent.ConcurrentLinkedQueue<byte[]>> entry : participantAudioQueues.entrySet()) {
                            java.util.concurrent.ConcurrentLinkedQueue<byte[]> queue = entry.getValue();
                            byte[] packet = queue.poll();
                            if (packet != null) {
                                hasData = true;
                                for (int i = 0; i < 320 && (i * 2 + 1) < packet.length; i++) {
                                    int sample = (packet[i * 2] << 8) | (packet[i * 2 + 1] & 0xFF);
                                    
                                    // 1. Noise gate
                                    int absVal = Math.abs(sample);
                                    if (absVal < 150) {
                                        sample = 0;
                                    } else {
                                        // 2. AGC (boost softer speech)
                                        if (absVal < 4000) {
                                            sample = (int) (sample * 1.5);
                                        }
                                    }
                                    sumSamples[i] += sample;
                                }
                            }
                        }
                        
                        if (hasData) {
                            for (int i = 0; i < 320; i++) {
                                int sample = sumSamples[i];
                                // 3. Saturated Clipping to prevent overflow distortion
                                if (sample > 32767) sample = 32767;
                                else if (sample < -32768) sample = -32768;
                                
                                mixBuffer[i * 2] = (byte) ((sample >> 8) & 0xFF);
                                mixBuffer[i * 2 + 1] = (byte) (sample & 0xFF);
                            }
                            
                            if (audioOutput != null) {
                                audioOutput.write(mixBuffer, 0, mixBuffer.length);
                            }
                        } else {
                            try { Thread.sleep(10); } catch (InterruptedException e) { break; }
                        }
                    }
                });
                audioPlaybackThread.setDaemon(true);
                audioPlaybackThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Vidéo
        if ("video".equals(callType)) {
            new Thread(() -> {
                try {
                    webcam = Webcam.getDefault();
                    if (webcam != null) {
                        webcam.open();
                        while (isHardwareActive) {
                            if (!isCameraOff) {
                                BufferedImage image = webcam.getImage();
                                if (image != null) {
                                    WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                                    Platform.runLater(() -> {
                                        boolean isFirstFrame = (localVideoView.getImage() == null);
                                        localVideoView.setImage(fxImage);
                                        if (isFirstFrame) {
                                            updateLayout(); // Mettre à jour la grille dès que la première image arrive
                                        }
                                    });

                                    if (isCallActive) {
                                        try {
                                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                            ImageIO.write(image, "jpg", baos);
                                            final byte[] frameData = baos.toByteArray();
                                            
                                            if (contactPhone.startsWith("GROUP_")) {
                                                socketManager.sendBinary("CALL_VIDEO", contactPhone, "", frameData);
                                            } else {
                                                synchronized (targetPhones) {
                                                    for (String tp : targetPhones) {
                                                        socketManager.sendBinary("CALL_VIDEO", tp, "", frameData);
                                                    }
                                                }
                                            }
                                        } catch (Exception e) { e.printStackTrace(); }
                                    }
                                }
                            }
                            try { Thread.sleep(100); } catch (InterruptedException ignored) {} // ~10 FPS
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Erreur avec la webcam (peut-être déjà utilisée par une autre application): " + ex.getMessage());
                    ex.printStackTrace();
                }
            }).start();
        }
    }

    public void receiveAudio(String speakerPhone, byte[] data) {
        if (!isCallActive || data == null) return;

        // 1. Enqueue incoming packet for software mixing
        java.util.concurrent.ConcurrentLinkedQueue<byte[]> queue = participantAudioQueues.computeIfAbsent(speakerPhone, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
        
        // Prune the queue if it grows too large to maintain absolute real-time sync
        while (queue.size() > 5) {
            queue.poll();
        }
        queue.add(data);

        // 2. Compute RMS amplitude for speaker visual highlight
        double sum = 0;
        for (int i = 0; i < data.length; i += 2) {
            int sample = (data[i] << 8) | (data[i+1] & 0xFF);
            sum += sample * sample;
        }
        double rms = Math.sqrt(sum / (data.length / 2.0));
        if (rms > 500) {
            lastSpeechTime.put(speakerPhone, System.currentTimeMillis());
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
                        boolean isNew = (rv == null);
                        if (isNew) {
                            rv = new ImageView();
                            rv.setPreserveRatio(true);
                            remoteVideoMap.put(sender, rv);
                        }
                        
                        rv.setImage(fxImage);
                        
                        if (isNew) {
                            updateLayout(); // Rebuild grid to show new video feed instead of avatar
                        }
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void endCall() {
        boolean wasActive = isCallActive;
        isCallActive = false;
        isHardwareActive = false;

        if (speakingIndicatorTimeline != null) {
            speakingIndicatorTimeline.stop();
        }
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
            // Notify ALL participants that we are leaving
            for (String tp : targetPhones) {
                socketManager.sendBinary("CALL_SIGNAL", tp, "", ("CALL_END:" + tp).getBytes(StandardCharsets.UTF_8));
            }
            if (onHangup != null) onHangup.run();
        } else if (isIncoming) {
            if (onDecline != null) onDecline.run();
        } else if (onHangup != null) {
            // For outgoing call not yet accepted, still notify target
            for (String tp : targetPhones) {
                socketManager.sendBinary("CALL_SIGNAL", tp, "", ("CALL_END:" + tp).getBytes(StandardCharsets.UTF_8));
            }
            onHangup.run();
        }
        Platform.runLater(() -> stage.close());
    }
}