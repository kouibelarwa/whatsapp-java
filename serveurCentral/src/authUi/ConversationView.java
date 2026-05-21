package authUi;

import dao.MessageDao;
import dao.UserDao;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import model.Message;
import model.User;
import client.SocketManager;
import javax.sound.sampled.*;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class ConversationView {

    private Consumer<String> onForwardRefresh;
    private final int myUserId;
    private final String myPhone;
    private final String contactPhone;
    private String contactName;
    private String contactStatus;
    private final int contactId;

    private BorderPane view;
    private VBox messagesPanel;
    private ScrollPane scrollPane;
    private TextField textField;
    private Label nameLbl;
    private Label statusLabel;
    private StackPane headerAvatarPane;
    private HBox inputBar;
    private Button btnSend;
    private Button btnAudio;
    private File pendingAttachment;
    private String pendingAttachmentType;

    private Runnable onBack;
    private Runnable onAudioCall;
    private Runnable onVideoCall;

    private Integer replyToId = null;
    private String replyToText = null;
    private VBox replyPreview;
    private final SocketManager socketManager;
    
    private TargetDataLine targetLine;
    private File audioRecordFile;
    private boolean isRecording = false;

    private final MessageDao messageDao = new MessageDao();
    private final UserDao userDao = new UserDao();


    private Map<String, String> allContacts = new java.util.HashMap<>();

    public void setAllContacts(Map<String, String> allContacts) {
        this.allContacts = allContacts;
        if (contactPhone != null && !contactPhone.startsWith("GROUP_") && allContacts.containsKey(contactPhone)) {
            String newName = allContacts.get(contactPhone);
            if (!newName.equals(this.contactName)) {
                this.contactName = newName;
                refreshHeader();

                Platform.runLater(this::refreshHistory);
            }
        }
    }

    public void updateStatus(String status) {
        this.contactStatus = status;
        refreshHeader();
    }

    private void refreshHeader() {
        Platform.runLater(() -> {
            if (nameLbl != null) nameLbl.setText(contactName);
            if (statusLabel != null) {
                statusLabel.setText(contactStatus);
                statusLabel.setStyle("-fx-text-fill: " + ("ONLINE".equals(contactStatus) ? "#00A884" : "#667781") + "; -fx-font-size: 12px;");
            }
        });
    }

    public ConversationView(int myUserId, String myPhone, int contactId, String contactPhone, String contactName, String contactStatus, SocketManager socketManager) {
        this.myUserId = myUserId;
        this.myPhone = myPhone;
        this.contactId = contactId;
        this.contactPhone = contactPhone;
        this.contactName = contactName;
        this.contactStatus = contactStatus;
        this.socketManager = socketManager;
        buildUI();
        loadHistory();
    }

    public void refreshAvatar(byte[] avatarBytes) {
        Platform.runLater(() -> {
            StackPane newAvatar = ChatView.buildImageAvatar(avatarBytes, 42);
            HBox header = (HBox) view.getTop();
            HBox clickable = (HBox) header.getChildren().get(1);
            clickable.getChildren().set(0, newAvatar);
            this.headerAvatarPane = newAvatar;
        });
    }

    public void updateHeaderName(String newName) {
        Platform.runLater(() -> {
            this.contactName = newName;
            nameLbl.setText(newName);
        });
    }

    public void setOnForwardRefresh(Consumer<String> callback) { this.onForwardRefresh = callback; }



    public void setOnBack(Runnable onBack) { this.onBack = onBack; }
    public void setOnAudioCall(Runnable onAudioCall) { this.onAudioCall = onAudioCall; }
    public void setOnVideoCall(Runnable onVideoCall) { this.onVideoCall = onVideoCall; }

    public BorderPane getView() {
        return view;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    private void buildUI() {
        view = new BorderPane();
        view.setStyle("-fx-background-color: #E5DDD5;");

        // Header
        HBox header = new HBox(15);
        header.setPadding(new Insets(10, 15, 10, 15));
        header.setStyle("-fx-background-color: #F0F2F5; -fx-border-color: #E9EDEF; -fx-border-width: 0 0 1 0;");
        header.setAlignment(Pos.CENTER_LEFT);

        Button btnBack = new Button("⬅");
        btnBack.setStyle("-fx-background-color: transparent; -fx-text-fill: #54656F; -fx-font-size: 18px; -fx-cursor: hand;");
        btnBack.setOnAction(e -> { if (onBack != null) onBack.run(); });

        StackPane avatar;
        if (ChatView.avatarCache.containsKey(contactPhone)) {
            avatar = ChatView.buildImageAvatar(ChatView.avatarCache.get(contactPhone), 42);
        } else {
            avatar = ChatView.buildAvatar(contactName, 42);
            // Request if not group
            if (contactPhone != null && !contactPhone.startsWith("GROUP_")) {
                socketManager.sendBinary("GET_AVATAR", contactPhone, "", "req".getBytes(StandardCharsets.UTF_8));
            }
        }
        this.headerAvatarPane = avatar; // We need to store this to refresh later

        VBox info = new VBox();
        nameLbl = new Label(contactName);
        nameLbl.setStyle("-fx-text-fill: #111B21; -fx-font-weight: bold; -fx-font-size: 15px;");
        info.getChildren().addAll(nameLbl);

        HBox clickableHeader = new HBox(10, this.headerAvatarPane, info);
        clickableHeader.setAlignment(Pos.CENTER_LEFT);
        clickableHeader.setStyle("-fx-cursor: hand;");
        clickableHeader.setOnMouseClicked(e -> {
            if (contactPhone != null && contactPhone.startsWith("GROUP_")) {
                socketManager.sendBinary("GROUP_SIGNAL", "", "", ("GET_GROUP_INFO:" + contactId).getBytes(StandardCharsets.UTF_8));
            } else {
                handleRenameContact();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAudioCall = new Button("📞");
        btnAudioCall.setStyle("-fx-background-color: transparent; -fx-text-fill: #54656F; -fx-font-size: 18px; -fx-cursor: hand;");
        btnAudioCall.setOnAction(e -> { if (onAudioCall != null) onAudioCall.run(); });

        Button btnVideoCall = new Button("📹");
        btnVideoCall.setStyle("-fx-background-color: transparent; -fx-text-fill: #54656F; -fx-font-size: 18px; -fx-cursor: hand;");
        btnVideoCall.setOnAction(e -> { if (onVideoCall != null) onVideoCall.run(); });

        header.getChildren().addAll(btnBack, clickableHeader, spacer, btnAudioCall, btnVideoCall);
        view.setTop(header);

        // Messages
        messagesPanel = new VBox(15);
        messagesPanel.setPadding(new Insets(20));
        messagesPanel.setStyle("-fx-background-color: transparent;");

        scrollPane = new ScrollPane(messagesPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        view.setCenter(scrollPane);

        // Input
        inputBar = new HBox(10);
        inputBar.setPadding(new Insets(10));
        inputBar.setAlignment(Pos.CENTER_LEFT);
        inputBar.setStyle("-fx-background-color: #F0F2F5;");

        Button btnEmoji = new Button("😊");
        btnEmoji.setStyle("-fx-background-color: transparent; -fx-text-fill: #54656F; -fx-font-size: 20px; -fx-cursor: hand;");
        
        ContextMenu emojiPopup = new ContextMenu();
        emojiPopup.getStyleClass().add("emoji-popup");
        
        String[] emojis = {"😊", "😂", "🥰", "😍", "🤩", "😘", "😜", "😎", "🤔", "😢", "🔥", "💯", "👍", "🙏", "❤️", "✨", "🎉", "🚀"};
        FlowPane emojiGrid = new FlowPane(5, 5);
        emojiGrid.setPadding(new Insets(10));
        emojiGrid.setPrefWrapLength(200);
        emojiGrid.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E9EDEF;");
        
        for (String emoji : emojis) {
            Button eBtn = new Button(emoji);
            eBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-cursor: hand;");
            eBtn.setOnAction(ev -> {
                textField.appendText(emoji);
                textField.requestFocus();
            });
            emojiGrid.getChildren().add(eBtn);
        }
        
        CustomMenuItem emojiItem = new CustomMenuItem(emojiGrid);
        emojiItem.setHideOnClick(false);
        emojiPopup.getItems().add(emojiItem);
        
        btnEmoji.setOnAction(e -> {
            emojiPopup.show(btnEmoji, Side.TOP, 0, 0);
        });

        Button btnAttach = new Button("📎");
        btnAttach.setStyle("-fx-background-color: transparent; -fx-text-fill: #54656F; -fx-font-size: 20px; -fx-cursor: hand;");
        btnAttach.setOnAction(e -> handleAttachment());

        textField = new TextField();
        textField.setPromptText("Écrire un message...");
        HBox.setHgrow(textField, Priority.ALWAYS);
        textField.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #111B21; -fx-background-radius: 20; -fx-padding: 8 15 8 15;");
        textField.setOnAction(e -> handleSend());

        btnAudio = new Button("🎤");
        btnAudio.setStyle("-fx-background-color: transparent; -fx-text-fill: #54656F; -fx-font-size: 20px; -fx-cursor: hand;");
        btnAudio.setOnAction(e -> handleAudioRecordStub());

        btnSend = new Button("➤");
        btnSend.setStyle("-fx-background-color: #00A884; -fx-text-fill: white; -fx-background-radius: 50%; -fx-min-width: 45; -fx-min-height: 45; -fx-cursor: hand;");
        btnSend.setOnAction(e -> handleSend());

        inputBar.getChildren().addAll(btnEmoji, btnAttach, textField, btnAudio, btnSend);
        
        replyPreview = new VBox(5);
        replyPreview.setPadding(new Insets(10));
        replyPreview.setStyle("-fx-background-color: #F0F2F5; -fx-border-color: #00A884; -fx-border-width: 1 0 0 0;");
        replyPreview.setVisible(false);
        replyPreview.setManaged(false);

        VBox bottomContainer = new VBox(replyPreview, inputBar);
        view.setBottom(bottomContainer);
    }

    private void handleAttachment() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            this.pendingAttachment = file;
            this.pendingAttachmentType = isImageFile(file.getName()) ? "image" : (isVideoFile(file.getName()) ? "video" : "file");
            
            // Preview
            HBox preview = new HBox(10);
            preview.setPadding(new Insets(5));
            preview.setAlignment(Pos.CENTER_LEFT);
            preview.setStyle("-fx-background-color: #1e2d23;");
            
            Label name = new Label(file.getName());
            name.setStyle("-fx-text-fill: #111B21;");
            
            Button btnCancel = new Button("✕");
            btnCancel.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc3c3c;");
            btnCancel.setOnAction(e -> {
                inputBar.getChildren().remove(preview);
                pendingAttachment = null;
            });
            
            preview.getChildren().addAll(new Label("📎"), name, btnCancel);
            if (!inputBar.getChildren().contains(preview)) {
                inputBar.getChildren().add(3, preview);
            }
        }
    }

    private void handleSend() {
        String text = textField.getText().trim();
        if (text.isEmpty() && pendingAttachment == null) return;

        String timeStr = formatTime(null);
        
        if (pendingAttachment != null) {
            try {
                byte[] data = Files.readAllBytes(pendingAttachment.toPath());
                String filename = pendingAttachment.getName();
                String type = pendingAttachmentType;

            if ("image".equals(type)) addImageBubble(-1, pendingAttachment, true, timeStr, "NOT_DELIVERED");
            else if ("video".equals(type)) addVideoBubble(-1, pendingAttachment, filename, true, timeStr, "NOT_DELIVERED");
            else addFileBubble(-1, pendingAttachment, filename, type, true, timeStr, "NOT_DELIVERED");

            String msgType = type;
            if (replyToId != null) {
                msgType = (contactPhone != null && contactPhone.startsWith("GROUP_")) ? ("GROUP_REPLY:" + type) : ("REPLY:" + type);
                filename = String.valueOf(replyToId);
            } else {
                msgType = (contactPhone != null && contactPhone.startsWith("GROUP_")) ? ("GROUP_MSG:" + type) : type;
            }

            final String finalMsgType = msgType;
            final String finalFilename = filename;

            new Thread(() -> socketManager.sendBinary(
                    finalMsgType, contactPhone.replace("GROUP_", ""), finalFilename, data)
            ).start();

            pendingAttachment = null;
            // Remove preview
            inputBar.getChildren().removeIf(n -> n instanceof HBox && ((HBox)n).getStyle().contains("#1e2d23"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    } else {
        addMessageBubble(-1, text, true, timeStr, "NOT_DELIVERED", replyToId, replyToText, null);
            String msgType = (contactPhone != null && contactPhone.startsWith("GROUP_")) ? "GROUP_MSG:text" : "text";
            
            if (replyToId != null) {
                msgType = (contactPhone != null && contactPhone.startsWith("GROUP_")) ? "GROUP_REPLY:text" : "REPLY:text";
                socketManager.sendBinary(msgType, contactPhone.replace("GROUP_", ""), String.valueOf(replyToId), text.getBytes(StandardCharsets.UTF_8));
            } else {
                socketManager.sendBinary(msgType, contactPhone.replace("GROUP_", ""), "", text.getBytes(StandardCharsets.UTF_8));
            }
        }

        cancelReply();
        textField.clear();
        scrollToBottom();
    }

    private void loadHistory() {
        messagesPanel.getChildren().clear();
        java.util.List<Message> history = contactPhone.startsWith("GROUP_") ? 
                messageDao.getGroupHistory(contactId) : 
                messageDao.getConversation(myUserId, contactId);

        for (Message m : history) {
            boolean mine = m.getSenderId() == myUserId;
            String timeStr = formatTime(m.getSentAt());
            String etat = m.getEtat();

            if (m.isText()) {
                String replyContent = null;
                if (m.getReplyToId() != null) {
                    Message parent = messageDao.getMessageById(m.getReplyToId());
                    if (parent != null) replyContent = parent.getContent();
                }
                addMessageBubble(m.getId(), m.getContent(), mine, timeStr, etat, m.getReplyToId(), replyContent, m.getSenderPhone());
            } else if ("audio".equals(m.getType())) {
                byte[] data;
                if (contactPhone != null && contactPhone.startsWith("GROUP_")) {
                    data = messageDao.getGroupMessageData(m.getId());
                } else {
                    data = messageDao.getDataById(m.getId());
                }
                try {
                    File tempFile = File.createTempFile("history_audio_", ".wav");
                    Files.write(tempFile.toPath(), data);
                    addAudioBubble(m.getId(), tempFile, mine, timeStr, etat);
                } catch (Exception e) {
                    addMessageBubble(m.getId(), "🎵 Message audio", mine, timeStr, etat);
                }
            } else {
                String filename = m.getFilename() != null ? m.getFilename() : "fichier";
                try {
                    byte[] binaryData;
                    if (contactPhone != null && contactPhone.startsWith("GROUP_")) {
                        binaryData = messageDao.getGroupMessageData(m.getId());
                    } else {
                        binaryData = messageDao.getDataById(m.getId());
                    }
                    File tempFile = File.createTempFile("history_file_", "_" + filename.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    Files.write(tempFile.toPath(), binaryData);
                    if ("image".equals(m.getType()) || isImageFile(filename)) {
                        addImageBubble(m.getId(), tempFile, mine, timeStr, etat);
                    } else if ("video".equals(m.getType()) || isVideoFile(filename)) {
                        addVideoBubble(m.getId(), tempFile, filename, mine, timeStr, etat);
                    } else if ("audio".equals(m.getType())) {
                        addAudioBubble(m.getId(), tempFile, mine, timeStr, etat);
                    } else {
                        addFileBubble(m.getId(), tempFile, filename, m.getType(), mine, timeStr, etat);
                    }
                } catch (Exception e) {
                    addMessageBubble(m.getId(), "📎 " + filename, mine, timeStr, etat);
                }
            }
        }
        scrollToBottom();
    }

    private void handleAudioRecordStub() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Microphone non supporté.");
                return;
            }
            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(format);
            targetLine.start();

            isRecording = true;
            btnAudio.setText("⏹");
            btnAudio.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc3c3c; -fx-font-size: 20px;");

            audioRecordFile = File.createTempFile("voice_msg_", ".wav");

            Thread recordThread = new Thread(() -> {
                try (AudioInputStream ais = new AudioInputStream(targetLine)) {
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audioRecordFile);
                } catch (Exception e) {
                    if (isRecording) e.printStackTrace();
                }
            });
            recordThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }
        isRecording = false;
        btnAudio.setText("🎤");
        btnAudio.setStyle("-fx-background-color: transparent; -fx-text-fill: #8696a0; -fx-font-size: 20px;");

        try {
            if (audioRecordFile != null && audioRecordFile.exists()) {
                byte[] data = Files.readAllBytes(audioRecordFile.toPath());
                if (data.length > 500) {
                    String filename = "vocal_" + System.currentTimeMillis() + ".wav";
                    String timeStr = formatTime(null);

                    Platform.runLater(() -> {
                        addAudioBubble(-1, audioRecordFile, true, timeStr, "SENT");
                        scrollToBottom();
                    });

                    String msgType = (contactPhone != null && contactPhone.startsWith("GROUP_")) ? "GROUP_MSG:audio" : "audio";
                    new Thread(() -> socketManager.sendBinary(
                            msgType, contactPhone.replace("GROUP_", ""), filename, data)
                    ).start();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void receiveMessage(String type, String filename, byte[] data, String senderPhoneForUi) {
        Platform.runLater(() -> {
            String timeStr = formatTime(null);
            
            String senderPrefix = "";
            if (senderPhoneForUi != null && contactPhone != null && contactPhone.startsWith("GROUP_") && !senderPhoneForUi.equals(myPhone)) {
                String resolvedName = allContacts.getOrDefault(senderPhoneForUi, senderPhoneForUi);
                senderPrefix = "~ " + resolvedName + "\n";
            }

            if ("text".equals(type)) {
                addMessageBubble(-1, senderPrefix + new String(data, StandardCharsets.UTF_8), false, timeStr, "READ");
            } else if (type.startsWith("REPLY:")) {
                int parentId = -1;
                try { parentId = Integer.parseInt(filename); } catch(Exception e){}
                
                final int pId = parentId;
                final String fTime = timeStr;
                final String fPrefix = senderPrefix;
                new Thread(() -> {
                    String parentContent = "Message cité";
                    if (pId != -1) {
                        model.Message parent = new dao.MessageDao().getMessageById(pId);
                        if (parent != null) {
                            parentContent = parent.getContent();
                            if (parentContent == null || parentContent.isEmpty()) parentContent = "[Média]";
                        }
                    }
                    final String finalContent = parentContent;
                    Platform.runLater(() -> {
                        addMessageBubble(-1, fPrefix + new String(data, StandardCharsets.UTF_8), false, fTime, "READ", pId, finalContent);
                    });
                }).start();
            } else if ("audio".equals(type)) {
                try {
                    File tempFile = File.createTempFile("received_audio_", ".wav");
                    Files.write(tempFile.toPath(), data);
                    addAudioBubble(-1, tempFile, false, timeStr, "READ");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if ("video".equals(type) || "file".equals(type) || "image".equals(type)) {
                try {
                    String safeName = (filename == null || filename.isBlank()) ? ("incoming_" + System.currentTimeMillis()) : filename;
                    File tempFile = File.createTempFile("received_", "_" + safeName.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    Files.write(tempFile.toPath(), data);
                    if ("image".equals(type) || isImageFile(safeName)) {
                        addImageBubble(-1, tempFile, false, timeStr, "READ");
                    } else if ("audio".equals(type)) {
                        addAudioBubble(-1, tempFile, false, timeStr, "READ");
                    } else if ("video".equals(type) || isVideoFile(safeName)) {
                        addVideoBubble(-1, tempFile, safeName, false, timeStr, "READ");
                    } else {
                        addFileBubble(-1, tempFile, safeName, type, false, timeStr, "READ");
                    }
                } catch (Exception e) {
                    String label = "video".equals(type) ? "🎬 Vidéo reçue" : "📎 Fichier reçu";
                    addMessageBubble(-1, label + " (" + (data.length/1024) + " KB)", false, timeStr, "READ");
                }
            } else {
                addMessageBubble(-1, "📎 Message reçu (" + type + ")", false, timeStr, "READ");
            }
            scrollToBottom();
            if (contactId != -1) messageDao.markAllAsRead(contactId, myUserId);
        });
    }

    private boolean isVideoFile(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov");
    }
    
    private boolean isImageFile(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif");
    }

    private String formatTime(java.sql.Timestamp ts) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
        return ts != null ? sdf.format(ts) : sdf.format(new java.util.Date());
    }

    private Label createTimeLabel(String timeStr, String etat, boolean mine) {
        String check = "";
        if (mine) {
            if ("READ".equals(etat)) check = " ✓✓";
            else if ("DELIVERED".equals(etat)) check = " ✓✓"; // Simplified
            else if ("SENT".equals(etat)) check = " ✓";
            else check = " ✓";
        }
        Label timeLabel = new Label(timeStr + check);
        timeLabel.setStyle("-fx-text-fill: #667781; -fx-font-size: 10px;");
        return timeLabel;
    }

    private void addAudioBubble(int msgId, File audioFile, boolean mine, String timeStr, String etat) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        String bgColor = mine ? "#D9FDD3" : "#FFFFFF";
        bubble.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10px;");
        bubble.setMaxWidth(250);

        HBox audioPlayer = new HBox(10);
        audioPlayer.setAlignment(Pos.CENTER_LEFT);
        
        Button btnPlay = new Button("▶");
        btnPlay.setStyle("-fx-background-color: #25D366; -fx-text-fill: white; -fx-background-radius: 50%;");
        
        Label lblDuration = new Label("Audio (" + (audioFile.length() / 1024) + " KB)");
        lblDuration.setStyle("-fx-text-fill: #111B21;");

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

        Label timeLabel = createTimeLabel(timeStr, etat, mine);

        bubble.getChildren().addAll(audioPlayer, timeLabel);
        addOptionsToBubble(msgId, "[Audio]", mine, wrapper, bubble, audioFile, "audio");
        messagesPanel.getChildren().add(wrapper);
    }

    private void addImageBubble(int msgId, File imgFile, boolean mine, String timeStr, String etat) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(5));
        String bgColor = mine ? "#D9FDD3" : "#FFFFFF";
        bubble.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10px;");
        bubble.setMaxWidth(300);

        try {
            javafx.scene.image.Image img = new javafx.scene.image.Image(imgFile.toURI().toString());
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
            iv.setFitWidth(290);
            iv.setPreserveRatio(true);
            iv.setStyle("-fx-cursor: hand;");
            iv.setOnMouseClicked(e -> {
                try {
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(imgFile);
                } catch (Exception ex) {}
            });
            bubble.getChildren().add(iv);
        } catch (Exception e) {
            bubble.getChildren().add(new Label("🖼 Image"));
        }

        Label timeLabel = createTimeLabel(timeStr, etat, mine);
        bubble.getChildren().add(timeLabel);

        addOptionsToBubble(msgId, "[Image]", mine, wrapper, bubble, imgFile, "image");
        messagesPanel.getChildren().add(wrapper);
    }

    private void addVideoBubble(int msgId, File videoFile, String filename, boolean mine, String timeStr, String etat) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(5));
        String bgColor = mine ? "#D9FDD3" : "#FFFFFF";
        bubble.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10px;");
        bubble.setMaxWidth(300);

        StackPane playerStack = new StackPane();
        playerStack.setPrefSize(290, 180);
        playerStack.setStyle("-fx-background-color: black;");

        Label placeholder = new Label("🎬 " + filename);
        placeholder.setStyle("-fx-text-fill: #111B21;");
        playerStack.getChildren().add(placeholder);

        Button playBtn = new Button("▶");
        playBtn.setStyle("-fx-background-color: rgba(37, 211, 102, 0.8); -fx-text-fill: white; -fx-font-size: 20px; -fx-background-radius: 50%;");
        playBtn.setOnAction(e -> playVideoInApp(videoFile, filename));
        playerStack.getChildren().add(playBtn);

        bubble.getChildren().add(playerStack);

        Label timeLabel = createTimeLabel(timeStr, etat, mine);
        bubble.getChildren().add(timeLabel);

        addOptionsToBubble(msgId, "[Vidéo: " + filename + "]", mine, wrapper, bubble, videoFile, "video");
        messagesPanel.getChildren().add(wrapper);
    }

    private void playVideoInApp(File file, String name) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addFileBubble(int msgId, File localFile, String filename, String type, boolean mine, String timeStr, String etat) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(8);
        bubble.setPadding(new Insets(10, 12, 10, 12));
        String bgColor = mine ? "#D9FDD3" : "#FFFFFF";
        bubble.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10px;");
        bubble.setPrefWidth(240);

        Label fileLabel = new Label("📄 " + filename);
        fileLabel.setStyle("-fx-text-fill: #111B21; -fx-font-weight: bold;");
        fileLabel.setWrapText(true);

        Label sizeLabel = new Label((localFile.length() / 1024) + " KB • " + type.toUpperCase());
        sizeLabel.setStyle("-fx-text-fill: #667781; -fx-font-size: 11px;");

        HBox actions = new HBox(10);
        Button btnOpen = new Button("Ouvrir");
        btnOpen.setStyle("-fx-background-color: #25D366; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand;");
        btnOpen.setOnAction(e -> {
            try {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(localFile);
            } catch (Exception ex) { ex.printStackTrace(); }
        });

        Button btnDownload = new Button("Télécharger");
        btnDownload.setStyle("-fx-background-color: #343f46; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand;");
        btnDownload.setOnAction(e -> {
            javafx.stage.FileChooser saveChooser = new javafx.stage.FileChooser();
            saveChooser.setInitialFileName(filename);
            File dest = saveChooser.showSaveDialog(null);
            if (dest != null) {
                try {
                    Files.copy(localFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        actions.getChildren().addAll(btnOpen, btnDownload);

        Label timeLabel = createTimeLabel(timeStr, etat, mine);

        bubble.getChildren().addAll(fileLabel, sizeLabel, actions, timeLabel);
        addOptionsToBubble(msgId, "[Fichier: " + filename + "]", mine, wrapper, bubble, localFile, "file");
        messagesPanel.getChildren().add(wrapper);
    }

    public void addMessageBubble(int msgId, String text, boolean mine, String timeStr, String etat, Integer rId, String rText, String senderPhone) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(5);
        
        // --- NICKNAME DISPLAY ---
        if (!mine && senderPhone != null) {
            String phonePart = senderPhone;
            String dbName = senderPhone;
            if (senderPhone.contains("~")) {
                String[] parts = senderPhone.split("~", 2);
                phonePart = parts[0];
                dbName = parts[1];
            }
            String displayName = allContacts.containsKey(phonePart) ? allContacts.get(phonePart) : dbName; 
            Label senderLbl = new Label(displayName);
            senderLbl.setStyle("-fx-text-fill: #00A884; -fx-font-weight: bold; -fx-font-size: 11px;");
            bubble.getChildren().add(senderLbl);
        }
        bubble.setPadding(new Insets(8, 12, 8, 12));
        String bgColor = mine ? "#D9FDD3" : "#FFFFFF";
        bubble.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10px;");
        bubble.setMaxWidth(400);


        if (rId != null && rText != null) {
            VBox replyBox = new VBox(2);
            replyBox.setPadding(new Insets(5));
            replyBox.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-background-radius: 5; -fx-border-color: #00A884; -fx-border-width: 0 0 0 3;");
            Label rLbl = new Label(rText);
            rLbl.setStyle("-fx-text-fill: #667781; -fx-font-size: 12px;");
            rLbl.setMaxWidth(380);
            replyBox.getChildren().add(rLbl);
            bubble.getChildren().add(replyBox);
        }

        Text textNode = new Text(text);
        textNode.setFill(Color.web("#111B21"));
        textNode.setStyle("-fx-font-size: 14px;");
        TextFlow textFlow = new TextFlow(textNode);

        Label timeLabel = createTimeLabel(timeStr, etat, mine);

        bubble.getChildren().addAll(textFlow, timeLabel);
        addOptionsToBubble(msgId, text, mine, wrapper, bubble, null, "text");
        messagesPanel.getChildren().add(wrapper);
    }
    
    public void addMessageBubble(int msgId, String text, boolean mine, String timeStr, String etat) {
        addMessageBubble(msgId, text, mine, timeStr, etat, null, null, null);
    }

    public void addMessageBubble(int msgId, String text, boolean mine, String timeStr, String etat, Integer rId, String rText) {
        addMessageBubble(msgId, text, mine, timeStr, etat, rId, rText, null);
    }

    private void addOptionsToBubble(int msgId, String content, boolean mine, HBox wrapper, VBox bubble, File file, String type) {
        HBox container = new HBox(5);
        container.setAlignment(Pos.CENTER);
        
        MenuButton optionsBtn = new MenuButton("⋮");
        optionsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: gray; -fx-font-size: 16px; -fx-cursor: hand;");
        
        MenuItem forwardItem = new MenuItem("Transférer");
        forwardItem.setOnAction(e -> handleForwardAction(content, file, type));
        
        MenuItem replyItem = new MenuItem("Répondre");
        replyItem.setOnAction(e -> startReply(msgId, content));
        
        optionsBtn.getItems().addAll(replyItem, forwardItem);

        if (mine) container.getChildren().addAll(optionsBtn, bubble);
        else container.getChildren().addAll(bubble, optionsBtn);
        
        wrapper.getChildren().add(container);
    }

    private void startReply(int msgId, String text) {
        this.replyToId = msgId;
        this.replyToText = text;
        
        replyPreview.getChildren().clear();
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label lbl = new Label("Répondre à : " + text);
        lbl.setStyle("-fx-text-fill: #25D366; -fx-font-weight: bold;");
        lbl.setMaxWidth(300);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button btnClose = new Button("✕");
        btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        btnClose.setOnAction(e -> cancelReply());
        
        header.getChildren().addAll(lbl, spacer, btnClose);
        replyPreview.getChildren().add(header);
        
        replyPreview.setVisible(true);
        replyPreview.setManaged(true);
        textField.requestFocus();
    }

    private void cancelReply() {
        this.replyToId = null;
        this.replyToText = null;
        replyPreview.setVisible(false);
        replyPreview.setManaged(false);
    }
    
    private void handleRenameContact() {
        TextInputDialog dialog = new TextInputDialog(contactName);
        dialog.setTitle("Renommer le contact");
        dialog.setHeaderText("Nouveau nom pour " + contactPhone + " :");
        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty() && !newName.equals(contactName)) {
                socketManager.sendBinary("CONTACT_SIGNAL", "", "", ("EDIT_NICKNAME:" + contactPhone + ":" + newName).getBytes(StandardCharsets.UTF_8));
                // Update local header immediately
                this.contactName = newName;
                Platform.runLater(() -> {
                    if (nameLbl != null) nameLbl.setText(newName);
                });
                Alert info = new Alert(Alert.AlertType.INFORMATION, "Le nom du contact a été mis à jour.");
                info.showAndWait();
            }
        });
    }

    private void handleForwardAction(String content, File file, String type) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Transférer le message");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setPrefWidth(300);
        root.setStyle("-fx-background-color: #121212;");

        TextField searchField = new TextField();
        searchField.setPromptText("Rechercher un contact...");
        searchField.setStyle("-fx-background-color: #2a372e; -fx-text-fill: white; -fx-background-radius: 10;");

        VBox contactList = new VBox(5);
        ScrollPane sp = new ScrollPane(contactList);
        sp.setFitToWidth(true);
        sp.setPrefHeight(300);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        Runnable updateList = () -> {
            contactList.getChildren().clear();
            String q = searchField.getText().toLowerCase();
            for (Map.Entry<String, String> entry : allContacts.entrySet()) {
                String phone = entry.getKey();
                String name = entry.getValue();
                if (name.toLowerCase().contains(q) || phone.contains(q)) {
                    Button btn = new Button(name + " (" + phone + ")");
                    btn.setMaxWidth(Double.MAX_VALUE);
                    btn.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-alignment: center-left; -fx-padding: 8;");
                    btn.setOnAction(e -> {
                        dialog.setResult(phone);
                        dialog.close();
                    });
                    contactList.getChildren().add(btn);
                }
            }
        };

        searchField.textProperty().addListener((obs, old, nval) -> updateList.run());
        updateList.run();

        root.getChildren().addAll(searchField, sp);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setStyle("-fx-background-color: #121212;");

        dialog.showAndWait().ifPresent(targetPhone -> {
            if (targetPhone != null) {
                String dest = targetPhone.replace("GROUP_", "");
                if (file != null && file.exists()) {
                    try {
                        byte[] data = Files.readAllBytes(file.toPath());
                        String filename = file.getName();
                        String msgType = targetPhone.startsWith("GROUP_") ? ("GROUP_MSG:" + type) : type;
                        socketManager.sendBinary(msgType, dest, filename, data);
                    } catch (Exception ex) { ex.printStackTrace(); }
                } else {
                    String msgType = targetPhone.startsWith("GROUP_") ? "GROUP_MSG:text" : "text";
                    socketManager.sendBinary(msgType, dest, "", content.getBytes(StandardCharsets.UTF_8));
                }
                
                String name = allContacts.get(targetPhone);
                Alert info = new Alert(Alert.AlertType.INFORMATION, "Message transféré à " + (name != null ? name : targetPhone));
                info.showAndWait();

                // Refresh target conversation if it's cached or active
                if (onForwardRefresh != null) {
                    onForwardRefresh.accept(targetPhone);
                }
            }
        });
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    public int getContactId() {
        return contactId;
    }

    private Dialog<Void> currentGroupDialog;

    public void showGroupInfoDialog(String payload) {
        Platform.runLater(() -> {
            if (currentGroupDialog != null && currentGroupDialog.isShowing()) {
                currentGroupDialog.close();
            }

            // payload format: GROUP_INFO_REPLY:groupId|id:phone:username:isAdmin:isOnline;id:phone...
            String dataPart = payload.substring(payload.indexOf("|") + 1);
            String[] members = dataPart.split(";");

            boolean amIAdmin = false;
            for (String m : members) {
                if (m.isEmpty()) continue;
                String[] parts = m.split(":");
                if (Integer.parseInt(parts[0]) == myUserId) {
                    amIAdmin = Boolean.parseBoolean(parts[3]);
                    break;
                }
            }

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Infos du Groupe - " + contactName);
            dialog.setHeaderText("Membres du groupe");
            
            VBox list = new VBox(10);
            list.setPadding(new Insets(10));
            list.setStyle("-fx-background-color: #121212;");

            for (String m : members) {
                if (m.isEmpty()) continue;
                String[] parts = m.split(":");
                int mId = Integer.parseInt(parts[0]);
                String mPhone = parts[1];
                String mName = parts[2];
                boolean isAdmin = Boolean.parseBoolean(parts[3]);
                String onlineStatus = parts[4];

                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(5));
                row.setStyle("-fx-border-color: #282828; -fx-border-width: 0 0 1 0;");

                VBox details = new VBox(2);
                String displayName = (mId == myUserId) ? "Vous" : mName;
                Label nameLblMember = new Label(displayName + (isAdmin ? " (Admin)" : ""));
                nameLblMember.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                Label statLbl = new Label("ONLINE".equals(onlineStatus) ? "En ligne" : "Hors ligne");
                statLbl.setStyle("-fx-text-fill: " + ("ONLINE".equals(onlineStatus) ? "#25D366" : "gray") + "; -fx-font-size: 11px;");
                details.getChildren().addAll(nameLblMember, statLbl);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                row.getChildren().addAll(ChatView.buildAvatar(mName, 36), details, spacer);

                if (amIAdmin && mId != myUserId) {
                    Button btnRemove = new Button("Retirer");
                    btnRemove.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc3c3c;");
                    btnRemove.setOnAction(e -> {
                        socketManager.sendBinary("GROUP_SIGNAL", "", "", ("REMOVE_GROUP_MEMBER:" + contactId + ":" + mId).getBytes(StandardCharsets.UTF_8));
                        dialog.close();
                    });
                    
                    Button btnAdmin = new Button(isAdmin ? "Déchoir" : "Promouvoir");
                    btnAdmin.setStyle("-fx-background-color: transparent; -fx-text-fill: #25D366;");
                    btnAdmin.setOnAction(e -> {
                        socketManager.sendBinary("GROUP_SIGNAL", "", "", ("PROMOTE_ADMIN:" + contactId + ":" + mId).getBytes(StandardCharsets.UTF_8));
                        dialog.close();
                    });
                    row.getChildren().addAll(btnAdmin, btnRemove);
                }

                list.getChildren().add(row);
            }

            if (amIAdmin) {
                Button btnAdd = new Button("+ Ajouter un membre");
                btnAdd.setStyle("-fx-background-color: #00a884; -fx-text-fill: white; -fx-background-radius: 5;");
                btnAdd.setOnAction(e -> {
                    Dialog<String> addDialog = new Dialog<>();
                    addDialog.setTitle("Ajouter un membre");
                    addDialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
                    
                    VBox addRoot = new VBox(10);
                    addRoot.setPadding(new Insets(10));
                    addRoot.setPrefWidth(300);
                    addRoot.setStyle("-fx-background-color: #121212;");

                    TextField search = new TextField();
                    search.setPromptText("Rechercher un contact...");
                    search.setStyle("-fx-background-color: #2a372e; -fx-text-fill: white; -fx-background-radius: 10;");

                    VBox cList = new VBox(5);
                    ScrollPane scp = new ScrollPane(cList);
                    scp.setFitToWidth(true);
                    scp.setPrefHeight(300);
                    scp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

                    Runnable upList = () -> {
                        cList.getChildren().clear();
                        String q = search.getText().toLowerCase();
                        for (Map.Entry<String, String> entry : allContacts.entrySet()) {
                            if (entry.getValue().toLowerCase().contains(q) || entry.getKey().contains(q)) {
                                Button b = new Button(entry.getValue() + " (" + entry.getKey() + ")");
                                b.setMaxWidth(Double.MAX_VALUE);
                                b.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-alignment: center-left;");
                                b.setOnAction(ev -> {
                                    addDialog.setResult(entry.getKey());
                                    addDialog.close();
                                });
                                cList.getChildren().add(b);
                            }
                        }
                    };
                    search.textProperty().addListener((o, old, nv) -> upList.run());
                    upList.run();

                    addRoot.getChildren().addAll(search, scp);
                    addDialog.getDialogPane().setContent(addRoot);
                    addDialog.getDialogPane().setStyle("-fx-background-color: #121212;");

                    addDialog.showAndWait().ifPresent(phoneInput -> {
                        socketManager.sendBinary("GROUP_SIGNAL", "", "", ("ADD_GROUP_MEMBER:" + contactId + ":" + phoneInput).getBytes(StandardCharsets.UTF_8));
                        dialog.close();
                    });
                });
                list.getChildren().add(btnAdd);
            }

            Button btnLeave = new Button("Quitter le groupe");
            btnLeave.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc3c3c; -fx-border-color: #dc3c3c; -fx-border-radius: 5;");
            btnLeave.setOnAction(e -> {
                socketManager.sendBinary("GROUP_SIGNAL", "", "", ("LEAVE_GROUP:" + contactId).getBytes(StandardCharsets.UTF_8));
                dialog.close();
                if (onBack != null) onBack.run();
            });
            list.getChildren().add(btnLeave);

            ScrollPane sp = new ScrollPane(list);
            sp.setFitToWidth(true);
            sp.setPrefHeight(400);
            sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

            dialog.getDialogPane().setContent(sp);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setStyle("-fx-background-color: #121212;");
            
            currentGroupDialog = dialog;
            dialog.showAndWait();
        });
    }

    public void refreshHistory() {
        Platform.runLater(this::loadHistory);
    }
}