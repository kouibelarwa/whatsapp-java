package authUi;

import client.SocketManager;
import dao.MessageDao;
import dao.UserDao;
import model.Message;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.sound.sampled.*;

public class ConversationView {

    private final int myUserId;
    private final String myPhone;
    private final String contactPhone;
    private final String contactName;
    private String contactStatus;
    private final int contactId;

    private BorderPane view;
    private VBox messagesPanel;
    private ScrollPane scrollPane;
    private TextField textField;
    private Label statusLabel;
    private Button btnSend;
    private Button btnAudio;

    private Runnable onBack;
    private Runnable onAudioCall;
    private Runnable onVideoCall;

    private final MessageDao messageDao = new MessageDao();
    private final UserDao userDao = new UserDao();

    // Audio recording fields
    private TargetDataLine audioLine;
    private File tempAudioFile;
    private Thread recordingThread;
    private boolean isRecording = false;

    public ConversationView(int myUserId, String myPhone, String contactPhone, String contactName, String contactStatus) {
        this.myUserId = myUserId;
        this.myPhone = myPhone;
        this.contactPhone = contactPhone;
        this.contactName = contactName != null ? contactName : contactPhone;
        this.contactStatus = contactStatus != null ? contactStatus : "OFFLINE";
        this.contactId = userDao.getIdByPhone(contactPhone);

        buildUI();
        if (contactId != -1) messageDao.markAllAsRead(contactId, myUserId);
        loadHistory();
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    public void setOnAudioCall(Runnable onAudioCall) {
        this.onAudioCall = onAudioCall;
    }

    public void setOnVideoCall(Runnable onVideoCall) {
        this.onVideoCall = onVideoCall;
    }

    public BorderPane getView() {
        return view;
    }

    private void buildUI() {
        view = new BorderPane();
        view.setStyle("-fx-background-color: #0b140e;");

        // Header
        HBox header = new HBox(15);
        header.setPadding(new Insets(10, 15, 10, 15));
        header.setStyle("-fx-background-color: #151e18; -fx-border-color: #1e2d23; -fx-border-width: 0 0 1 0;");
        header.setAlignment(Pos.CENTER_LEFT);

        Button btnBack = new Button("⬅");
        btnBack.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 18px;");
        btnBack.setOnAction(e -> { if (onBack != null) onBack.run(); });

        StackPane avatar = ChatView.buildAvatar(contactName, 42);

        VBox info = new VBox();
        Label nameLbl = new Label(contactName);
        nameLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px;");
        statusLabel = new Label("● " + ("ONLINE".equals(contactStatus) ? "En ligne" : "Hors ligne"));
        statusLabel.setStyle("-fx-text-fill: " + ("ONLINE".equals(contactStatus) ? "#25D366" : "#787878") + "; -fx-font-size: 11px;");
        info.getChildren().addAll(nameLbl, statusLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAudioCall = new Button("📞");
        btnAudioCall.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 18px; -fx-cursor: hand;");
        btnAudioCall.setOnAction(e -> { if (onAudioCall != null) onAudioCall.run(); });

        Button btnVideoCall = new Button("📹");
        btnVideoCall.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 18px; -fx-cursor: hand;");
        btnVideoCall.setOnAction(e -> { if (onVideoCall != null) onVideoCall.run(); });

        header.getChildren().addAll(btnBack, avatar, info, spacer, btnAudioCall, btnVideoCall);
        view.setTop(header);

        // Messages
        messagesPanel = new VBox(10);
        messagesPanel.setPadding(new Insets(15));
        messagesPanel.setStyle("-fx-background-color: #0e1610;");

        scrollPane = new ScrollPane(messagesPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #0e1610; -fx-border-color: transparent;");
        view.setCenter(scrollPane);

        // Input Bar
        HBox inputBar = new HBox(10);
        inputBar.setPadding(new Insets(10));
        inputBar.setStyle("-fx-background-color: #151e18;");
        inputBar.setAlignment(Pos.CENTER);

        Button btnEmoji = new Button("😊");
        btnEmoji.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 20px; -fx-cursor: hand;");
        btnEmoji.setOnAction(e -> showEmojiPicker(btnEmoji));

        Button btnFile = new Button("📎");
        btnFile.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 20px; -fx-cursor: hand;");
        btnFile.setOnAction(e -> sendFile());

        btnAudio = new Button("🎤");
        btnAudio.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 20px; -fx-cursor: hand;");
        btnAudio.setOnAction(e -> {
            if (!isRecording) {
                startRecording();
            } else {
                stopRecordingAndSend();
            }
        });

        textField = new TextField();
        textField.setPromptText("Écrire un message...");
        textField.setStyle("-fx-background-color: #2a372e; -fx-text-fill: white; -fx-background-radius: 20px;");
        textField.setPrefHeight(40);
        HBox.setHgrow(textField, Priority.ALWAYS);
        textField.setOnAction(e -> sendText());

        btnSend = new Button("➤");
        btnSend.setStyle("-fx-background-color: #25D366; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px;");
        btnSend.setPrefHeight(40);
        btnSend.setPrefWidth(40);
        btnSend.setOnAction(e -> sendText());

        inputBar.getChildren().addAll(btnEmoji, btnFile, btnAudio, textField, btnSend);
        view.setBottom(inputBar);
    }

    private void loadHistory() {
        if (contactId == -1) return;
        List<Message> history = messageDao.getConversation(myUserId, contactId);
        for (Message m : history) {
            boolean mine = m.getSenderId() == myUserId;
            if (m.isText()) {
                addMessageBubble(m.getContent(), mine);
            } else if ("audio".equals(m.getType())) {
                byte[] data = messageDao.getDataById(m.getId());
                try {
                    File tempFile = File.createTempFile("history_audio_", ".wav");
                    Files.write(tempFile.toPath(), data);
                    addAudioBubble(tempFile, mine);
                } catch (Exception e) {
                    addMessageBubble("🎵 Message audio", mine);
                }
            } else {
                String filename = m.getFilename() != null ? m.getFilename() : "fichier";
                try {
                    byte[] binaryData = messageDao.getDataById(m.getId());
                    File tempFile = File.createTempFile("history_file_", "_" + filename.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    Files.write(tempFile.toPath(), binaryData);
                    addFileBubble(tempFile, filename, m.getType(), mine);
                } catch (Exception e) {
                    addMessageBubble("📎 " + filename, mine);
                }
            }
        }
        scrollToBottom();
    }

    private void sendText() {
        String text = textField.getText().trim();
        if (text.isEmpty()) return;
        textField.setText("");
        addMessageBubble(text, true);
        scrollToBottom();

        new Thread(() -> SocketManager.getInstance().sendBinary(
                "text", contactPhone, "", text.getBytes(StandardCharsets.UTF_8))
        ).start();
    }

    private void showEmojiPicker(Button btn) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        
        FlowPane pane = new FlowPane();
        pane.setPrefWidth(200);
        pane.setVgap(5);
        pane.setHgap(5);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-background-color: #2a372e; -fx-border-color: #1e2d23; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        
        String[] emojis = {"😊", "😂", "❤️", "😍", "🙏", "👍", "😭", "😘", "🥰", "😎", "🤔", "🙌", "🔥", "💯", "🎉", "✨"};
        for (String em : emojis) {
            Button eb = new Button(em);
            eb.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 22px; -fx-cursor: hand; -fx-font-family: 'Segoe UI Emoji';");
            eb.setOnAction(e -> {
                textField.appendText(em);
                popup.hide();
            });
            pane.getChildren().add(eb);
        }
        
        popup.getContent().add(pane);
        
        // Afficher au-dessus du bouton
        javafx.geometry.Point2D point = btn.localToScreen(0, 0);
        if (point != null) {
            popup.show(btn, point.getX(), point.getY() - 150);
        }
    }

    private void sendFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner un fichier");
        File file = fileChooser.showOpenDialog(view.getScene().getWindow());
        
        if (file != null) {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                String filename = file.getName();
                String type = isVideoFile(filename) ? "video" : "file";
                addFileBubble(file, filename, type, true);
                scrollToBottom();
                
                new Thread(() -> SocketManager.getInstance().sendBinary(
                        type, contactPhone, filename, data)
                ).start();
                
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Erreur lors de la lecture du fichier : " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    private void startRecording() {
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Microphone non supporté !");
                return;
            }

            audioLine = (TargetDataLine) AudioSystem.getLine(info);
            audioLine.open(format);
            audioLine.start();
            isRecording = true;
            btnAudio.setStyle("-fx-background-color: #dc3c3c; -fx-text-fill: white; -fx-font-size: 20px; -fx-background-radius: 50%;");

            tempAudioFile = File.createTempFile("voice_note", ".wav");
            
            recordingThread = new Thread(() -> {
                try {
                    AudioInputStream ais = new AudioInputStream(audioLine);
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempAudioFile);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            recordingThread.start();
            
            textField.setPromptText("🎙️ Enregistrement en cours... (Cliquez pour envoyer)");
            textField.setDisable(true);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void stopRecordingAndSend() {
        if (!isRecording) return;
        isRecording = false;
        
        btnAudio.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 20px; -fx-cursor: hand;");
        textField.setPromptText("Écrire un message...");
        textField.setDisable(false);
        
        if (audioLine != null) {
            audioLine.stop();
            audioLine.close();
        }
        
        try {
            if (tempAudioFile != null && tempAudioFile.exists()) {
                // Attendre un tout petit peu pour s'assurer que le fichier est bien écrit
                Thread.sleep(200);
                byte[] data = Files.readAllBytes(tempAudioFile.toPath());
                String filename = "vocal_" + System.currentTimeMillis() + ".wav";
                
                Platform.runLater(() -> {
                    addAudioBubble(tempAudioFile, true);
                    scrollToBottom();
                });
                
                new Thread(() -> SocketManager.getInstance().sendBinary(
                        "audio", contactPhone, filename, data)
                ).start();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void receiveMessage(String type, String filename, byte[] data) {
        Platform.runLater(() -> {
            if ("text".equals(type)) {
                addMessageBubble(new String(data, StandardCharsets.UTF_8), false);
            } else if ("audio".equals(type)) {
                try {
                    File tempFile = File.createTempFile("received_audio_", ".wav");
                    Files.write(tempFile.toPath(), data);
                    addAudioBubble(tempFile, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if ("video".equals(type) || "file".equals(type) || "image".equals(type)) {
                try {
                    String safeName = (filename == null || filename.isBlank()) ? ("incoming_" + System.currentTimeMillis()) : filename;
                    File tempFile = File.createTempFile("received_", "_" + safeName.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    Files.write(tempFile.toPath(), data);
                    addFileBubble(tempFile, safeName, type, false);
                } catch (Exception e) {
                    String label = "video".equals(type) ? "🎬 Vidéo reçue" : "📎 Fichier reçu";
                    addMessageBubble(label + " (" + (data.length/1024) + " KB)", false);
                }
            } else {

                addMessageBubble("📎 Message reçu (" + type + ")", false);
            }
            scrollToBottom();
            if (contactId != -1) messageDao.markAllAsRead(contactId, myUserId);
        });
    }

    private boolean isVideoFile(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        return lower.endsWith(".mp4")
                || lower.endsWith(".m4v")
                || lower.endsWith(".mov")
                || lower.endsWith(".avi")
                || lower.endsWith(".mkv")
                || lower.endsWith(".wmv")
                || lower.endsWith(".webm");
    }

    private void addAudioBubble(File audioFile, boolean mine) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(10, 15, 10, 15));
        bubble.setStyle("-fx-background-color: " + (mine ? "#005c4b" : "#202c22") + "; -fx-background-radius: 15px;");
        bubble.setMaxWidth(400);

        HBox audioPlayer = new HBox(10);
        audioPlayer.setAlignment(Pos.CENTER_LEFT);
        
        Button btnPlay = new Button("▶");
        btnPlay.setStyle("-fx-background-color: #25D366; -fx-text-fill: white; -fx-background-radius: 50%;");
        
        Label lblDuration = new Label("Audio (" + (audioFile.length() / 1024) + " KB)");
        lblDuration.setStyle("-fx-text-fill: white;");

        btnPlay.setOnAction(e -> {
            try {
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(audioFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                clip.start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        audioPlayer.getChildren().addAll(btnPlay, lblDuration);

        Label timeLabel = new Label("Maintenant");
        timeLabel.setStyle("-fx-text-fill: #969696; -fx-font-size: 10px;");
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        bubble.getChildren().addAll(audioPlayer, timeLabel);
        wrapper.getChildren().add(bubble);
        messagesPanel.getChildren().add(wrapper);
    }

    private void addFileBubble(File localFile, String filename, String type, boolean mine) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(8);
        bubble.setPadding(new Insets(10, 15, 10, 15));
        bubble.setStyle("-fx-background-color: " + (mine ? "#005c4b" : "#202c22") + "; -fx-background-radius: 15px;");
        bubble.setMaxWidth(460);

        Label fileLabel = new Label((isVideoFile(filename) ? "🎬 " : "📎 ") + filename);
        fileLabel.setWrapText(true);
        fileLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");

        Label sizeLabel = new Label((localFile.length() / 1024) + " KB");
        sizeLabel.setStyle("-fx-text-fill: #c9c9c9; -fx-font-size: 11px;");

        HBox actions = new HBox(8);
        Button openBtn = new Button(isVideoFile(filename) ? "Lire" : "Ouvrir");
        openBtn.setStyle("-fx-background-color: #25D366; -fx-text-fill: #0f0f0f; -fx-font-weight: bold;");
        openBtn.setOnAction(e -> {
            try {
                if ("video".equals(type) || isVideoFile(filename)) {
                    playVideoInApp(localFile, filename);
                } else if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(localFile);
                }
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Impossible d'ouvrir le fichier.");
                alert.showAndWait();
            }
        });

        Button downloadBtn = new Button("Télécharger");
        downloadBtn.setStyle("-fx-background-color: #303030; -fx-text-fill: white;");
        downloadBtn.setOnAction(e -> downloadFile(localFile, filename));
        actions.getChildren().addAll(openBtn, downloadBtn);

        bubble.getChildren().addAll(fileLabel, sizeLabel, actions);
        wrapper.getChildren().add(bubble);
        messagesPanel.getChildren().add(wrapper);
    }

    private void downloadFile(File source, String filename) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Télécharger");
        chooser.setInitialFileName(filename);
        File target = chooser.showSaveDialog(view.getScene().getWindow());
        if (target == null) return;
        try {
            Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Erreur lors du téléchargement.");
            alert.showAndWait();
        }
    }

    private void playVideoInApp(File file, String title) {
        Stage stage = new Stage();
        Media media = new Media(file.toURI().toString());
        MediaPlayer player = new MediaPlayer(media);
        MediaView mediaView = new MediaView(player);
        mediaView.setFitWidth(640);
        mediaView.setFitHeight(400);
        mediaView.setPreserveRatio(true);

        Button playPause = new Button("⏯");
        playPause.setOnAction(e -> {
            MediaPlayer.Status status = player.getStatus();
            if (status == MediaPlayer.Status.PLAYING) player.pause();
            else player.play();
        });

        VBox root = new VBox(10, mediaView, playPause);
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.CENTER);
        stage.setTitle("Lecture vidéo - " + title);
        stage.setScene(new javafx.scene.Scene(root, 700, 500));
        stage.show();
        player.play();
        stage.setOnCloseRequest(e -> player.dispose());
    }

    private void addMessageBubble(String text, boolean mine) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(10, 15, 10, 15));
        bubble.setStyle("-fx-background-color: " + (mine ? "#005c4b" : "#202c22") + "; -fx-background-radius: 15px;");
        bubble.setMaxWidth(400);

        TextFlow textFlow = new TextFlow();
        Text msgText = new Text(text);
        msgText.setFill(Color.WHITE);
        msgText.setFont(Font.font("Segoe UI Emoji", 14));
        textFlow.getChildren().add(msgText);

        Label timeLabel = new Label("Maintenant");
        timeLabel.setStyle("-fx-text-fill: #969696; -fx-font-size: 10px;");
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        bubble.getChildren().addAll(textFlow, timeLabel);
        wrapper.getChildren().add(bubble);

        messagesPanel.getChildren().add(wrapper);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }
}