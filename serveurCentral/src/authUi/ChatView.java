package authUi;

import auth.AuthService;
import auth.SessionManager;
import client.NetworkClient;
import client.SocketManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class ChatView {

    private final int userId;
    private final String phone;
    private String username;
    private final NetworkClient network;

    private Stage stage;
    private VBox convList;
    private BorderPane mainPanel;
    private ContactView contactView;

    private ConversationView activeConversation;
    private String activeContactPhone;
    private CallView activeCallView;
    private final SocketManager socketManager; // Private socket for this user

    private final Map<String, ConversationView> conversationCache = new HashMap<>();
    public static final Map<String, byte[]> avatarCache = new HashMap<>();
    private StackPane myAvatarPane;
    private Label myNameLbl;

    public ChatView(int userId, String phone, String username, NetworkClient network, SocketManager socketManager) {
        this.userId = userId;
        this.phone = phone;
        this.username = username != null ? username : "Utilisateur";
        this.network = network;
        this.socketManager = socketManager;
    }

    public void start(Stage stage) {
        this.stage = stage;
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #FFFFFF;");

        root.setLeft(buildSidebar());

        mainPanel = new BorderPane();
        mainPanel.setStyle("-fx-background-color: #FFFFFF;");
        showWelcomeScreen();
        root.setCenter(mainPanel);

        Scene scene = new Scene(root, 960, 620);
        stage.setTitle("WhatsApp — " + username);
        stage.setScene(scene);
        stage.setMinWidth(700);
        stage.setMinHeight(450);
        stage.show();

        // Pass the private socketManager to ContactView
        contactView = new ContactView(network, convList, socketManager);
        contactView.setConversationOpenCallback(this::openConversation);

        startBinaryListener(contactView);
        socketManager.sendBinary("GET_AVATAR", phone, "", "req".getBytes(StandardCharsets.UTF_8));
        contactView.loadContacts();
        // Retry once after listener warmup to guarantee list visibility right after
        // login.
        new Thread(() -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
            }
            contactView.loadContacts();
        }).start();
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(300);
        sidebar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E9EDEF; -fx-border-width: 0 1 0 0;");

        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 15, 12, 15));
        header.setStyle("-fx-background-color: #F0F2F5;");
        header.setSpacing(10);

        myAvatarPane = buildAvatar(username, 44);

        VBox nameBox = new VBox();
        myNameLbl = new Label(username);
        myNameLbl.setStyle("-fx-text-fill: #111B21; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label phoneLbl = new Label(phone);
        phoneLbl.setStyle("-fx-text-fill: #667781; -fx-font-size: 11px;");
        nameBox.getChildren().addAll(myNameLbl, phoneLbl);

        HBox profileBox = new HBox(10, myAvatarPane, nameBox);
        profileBox.setAlignment(Pos.CENTER_LEFT);
        profileBox.setStyle("-fx-cursor: hand;");
        profileBox.setOnMouseClicked(e -> showProfileDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAdd = new Button("+ Contact");
        btnAdd.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #00A884; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;");
        btnAdd.setOnAction(e -> addContact());

        Button btnGroup = new Button("+ Groupe");
        btnGroup.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #00A884; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;");
        btnGroup.setOnAction(e -> createGroup());

        header.getChildren().addAll(profileBox, spacer, btnAdd, btnGroup);

        // Search Bar
        TextField searchField = new TextField();
        searchField.setPromptText("Rechercher...");
        searchField.setStyle("-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-prompt-text-fill: #667781; -fx-background-radius: 8;");
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (contactView != null) {
                contactView.filterContacts(newV);
            }
        });
        VBox.setMargin(searchField, new Insets(8, 12, 8, 12));

        // Conversation List
        convList = new VBox();
        convList.setStyle("-fx-background-color: #FFFFFF;");
        ScrollPane scrollPane = new ScrollPane(convList);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #FFFFFF; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Logout button
        Button btnLogout = new Button("⏻  Se déconnecter");
        btnLogout.setStyle("-fx-background-color: transparent; -fx-text-fill: #EA0038; -fx-font-size: 12px; -fx-cursor: hand;");
        btnLogout.setMaxWidth(Double.MAX_VALUE);
        btnLogout.setOnAction(e -> logout());

        sidebar.getChildren().addAll(header, searchField, scrollPane, btnLogout);
        return sidebar;
    }

    private void showWelcomeScreen() {
        VBox welcome = new VBox(12);
        welcome.setAlignment(Pos.CENTER);

        Label icon = new Label("💬");
        icon.setFont(Font.font("Segoe UI Emoji", 64));

        Label title = new Label("WhatsApp");
        title.setStyle("-fx-text-fill: #41525d; -fx-font-size: 26px; -fx-font-weight: bold;");

        Label sub = new Label("Cliquez sur un contact pour commencer");
        sub.setStyle("-fx-text-fill: #667781; -fx-font-size: 13px;");

        Label userLbl = new Label("Connecté : " + username + "  |  " + phone);
        userLbl.setStyle("-fx-text-fill: #8696a0; -fx-font-size: 11px;");

        welcome.getChildren().addAll(icon, title, sub, userLbl);
        mainPanel.setCenter(welcome);
    }

    private void openConversation(int contactId, String contactPhone, String contactName, String contactStatus) {
        String normalizedContactPhone = normalizeForCache(contactPhone);
        activeContactPhone = normalizedContactPhone;
        activeConversation = conversationCache.computeIfAbsent(normalizedContactPhone,
                k -> openConversationInternal(contactId, contactPhone, contactName, contactStatus));
        mainPanel.setCenter(activeConversation.getView());
    }

    private ConversationView openConversationInternal(int contactId, String contactPhone, String contactName,
            String contactStatus) {
        ConversationView c = new ConversationView(userId, phone, contactId, contactPhone, contactName, contactStatus,
                socketManager);
        c.setAllContacts(contactView.allContacts);
        c.setOnBack(() -> {
            activeContactPhone = null;
            activeConversation = null;
            showWelcomeScreen();
        });
        c.setOnForwardRefresh(targetPhone -> {
            String targetKey = normalizeForCache(targetPhone);
            ConversationView targetConv = conversationCache.get(targetKey);
            if (targetConv != null)
                Platform.runLater(targetConv::refreshHistory);
        });
        c.setOnAudioCall(() -> startOutgoingCall(contactPhone, contactName, "audio"));
        c.setOnVideoCall(() -> startOutgoingCall(contactPhone, contactName, "video"));
        return c;
    }

    private void startOutgoingCall(String targetPhone, String targetName, String type) {
        if (activeCallView != null)
            return;

        socketManager.sendBinary("CALL_SIGNAL", targetPhone, "",
                ("CALL_REQUEST:" + type.toUpperCase() + ":" + targetPhone).getBytes(StandardCharsets.UTF_8));

        activeCallView = new CallView(targetName, targetPhone, type, false, null, () -> {
            socketManager.sendBinary("CALL_SIGNAL", targetPhone, "",
                    ("CALL_END:" + targetPhone).getBytes(StandardCharsets.UTF_8));
            activeCallView = null;
        }, socketManager);
        activeCallView.start(new Stage());
    }

    private void startBinaryListener(ContactView contactView) {
        socketManager.startListening(new SocketManager.MessageListener() {
            @Override
            public void onMessage(String type, String sender, String filename, byte[] data) {
                switch (type) {
                    case "AVATAR_REPLY":
                        byte[] avatarBytes = data;
                        avatarCache.put(sender, avatarBytes);
                        Platform.runLater(() -> {
                            if (sender.equals(phone)) {
                                updateMyAvatar(avatarBytes);
                            }
                            // Refresh current conversation header if it's the sender
                            if (activeConversation != null && normalizeForCache(sender).equals(normalizeForCache(activeConversation.getContactPhone()))) {
                                activeConversation.refreshAvatar(avatarBytes);
                            }
                            // Refresh contact rows in sidebar
                            contactView.refreshAvatar(sender, avatarBytes);
                        });
                        break;
                    case "text":
                    case "audio":
                    case "video":
                    case "image":
                    case "file":
                    case "REPLY:text":
                    case "GROUP_MSG:text":
                    case "GROUP_MSG:audio":
                    case "GROUP_MSG:video":
                    case "GROUP_MSG:image":
                    case "GROUP_MSG:file":
                    case "GROUP_REPLY:text":
                    case "GROUP_REPLY:audio":
                    case "GROUP_REPLY:video":
                    case "GROUP_REPLY:image":
                    case "GROUP_REPLY:file":
                        Platform.runLater(() -> {
                            boolean isGroupMsg = sender != null && sender.startsWith("GROUP_");
                            
                            String normalizedType = type;
                            if (type.startsWith("GROUP_MSG:")) {
                                normalizedType = type.replace("GROUP_MSG:", "");
                            } else if (type.startsWith("GROUP_REPLY:")) {
                                normalizedType = type.replace("GROUP_REPLY:", "REPLY:");
                            }
                            
                            String msgType = normalizedType;
                            if (msgType.startsWith("REPLY:")) {
                                msgType = msgType.replace("REPLY:", "");
                            }

                            String groupIdStr = isGroupMsg ? sender.split(":")[0] : null;
                            String targetKey = isGroupMsg ? groupIdStr : normalizeForCache(sender);
                            String senderPhoneForUi = isGroupMsg
                                    ? (sender.contains(":") ? sender.split(":")[1] : sender)
                                    : sender;

                            ConversationView cachedConv = conversationCache.get(targetKey);
                            if (cachedConv == null && targetKey != null && !targetKey.isBlank()) {
                                if (isGroupMsg) {
                                    int gId = Integer.parseInt(groupIdStr.replace("GROUP_", ""));
                                    contactView.addDynamicContact(gId, targetKey, "Groupe " + gId, "ONLINE");
                                    cachedConv = openConversationInternal(gId, targetKey, "Groupe " + gId, "ONLINE");
                                    conversationCache.put(targetKey, cachedConv);
                                } else {
                                    String rawSender = senderPhoneForUi;
                                    String contactName = rawSender;
                                    String contactPhoneToUse = rawSender;
                                    
                                    if (rawSender.contains("~")) {
                                        String[] parts = rawSender.split("~", 2);
                                        contactPhoneToUse = parts[0];
                                        contactName = parts[0]; // Par défaut, on affiche le numéro brut
                                    }
                                    
                                    boolean contactExists = false;
                                    
                                    // Chercher si le contact existe déjà dans allContacts
                                    for (Map.Entry<String, String> entry : contactView.allContacts.entrySet()) {
                                        if (normalizeForCache(entry.getKey()).equals(targetKey)) {
                                            contactName = entry.getValue();
                                            contactPhoneToUse = entry.getKey();
                                            contactExists = true;
                                            break;
                                        }
                                    }

                                    if (!contactExists) {
                                        contactView.addDynamicContact(-1, contactPhoneToUse, contactName, "ONLINE");
                                    }
                                    
                                    cachedConv = openConversationInternal(-1, contactPhoneToUse, contactName, "ONLINE");
                                    conversationCache.put(targetKey, cachedConv);
                                }
                            }
                            if (cachedConv != null) {
                                cachedConv.receiveMessage(normalizedType, filename, data, senderPhoneForUi);
                            }
                            if (targetKey == null || !targetKey.equals(activeContactPhone)) {
                                String msgText = "text".equals(msgType) ? new String(data, StandardCharsets.UTF_8)
                                        : "📎 [Média]";
                                showNotification(isGroupMsg ? "Groupe (" + senderPhoneForUi + ")" : sender, msgText);
                            }
                        });
                        break;

                    case "GROUP_SIGNAL":
                        String groupPayload = new String(data, StandardCharsets.UTF_8);
                        handleGroupSignal(groupPayload);
                        break;

                    case "CONTACT_SIGNAL":
                        String contactPayload = new String(data, StandardCharsets.UTF_8);
                        if (contactPayload.startsWith("ADD_FAIL:NOT_FOUND")) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR, "User does not exist in the database.");
                                alert.showAndWait();
                            });
                        } else if (contactPayload.startsWith("ADD_FAIL:SELF")) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR,
                                        "Vous ne pouvez pas vous ajouter vous-même !");
                                alert.showAndWait();
                            });
                        } else {
                            // Regular contact list update is handled via loadContacts usually,
                            // but let's make sure handleGroupSignal is similar
                            Platform.runLater(() -> handleContactSignal(contactPayload));
                        }
                        break;

                    case "CALL_SIGNAL":
                        String callPayload = new String(data, StandardCharsets.UTF_8);
                        Platform.runLater(() -> handleCallSignal(callPayload, sender));
                        break;

                    case "CALL_AUDIO":
                        if (activeCallView != null) {
                            activeCallView.receiveAudio(data);
                        }
                        break;

                    case "CALL_VIDEO":
                        if (activeCallView != null) {
                            String realSender = (filename != null && !filename.isEmpty()) ? filename : sender;
                            activeCallView.receiveVideo(data, realSender);
                        }
                        break;
                }
            }

            @Override
            public void onDisconnect() {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Connexion perdue au serveur.");
                    alert.showAndWait();
                    logout();
                });
            }
        });
    }

    private void handleCallSignal(String payload, String sender) {
        String[] parts = payload.split(":");
        String signal = parts[0];

        if (signal.equals("CALL_INCOMING") || signal.equals("CALL_REQUEST")) {
            String callType = parts.length >= 2 ? parts[1].toLowerCase() : "audio";
            String callerPhone = parts.length >= 3 ? parts[2] : (sender != null ? sender : "Inconnu");
            
            // Priorité : Surnom local > Nom envoyé par serveur (pour groupes) > Numéro brut
            String callerDisplayName = contactView.allContacts.getOrDefault(callerPhone, callerPhone);
            if (callerDisplayName.equals(callerPhone) && parts.length >= 4) {
                callerDisplayName = parts[3]; // Nom synchronisé envoyé par le serveur
            }

            activeCallView = new CallView(callerDisplayName, callerPhone, callType, true, () -> {
                socketManager.sendBinary("CALL_SIGNAL", callerPhone, "",
                        ("CALL_REJECT:" + callerPhone).getBytes(StandardCharsets.UTF_8));
                activeCallView = null;
            }, () -> {
                socketManager.sendBinary("CALL_SIGNAL", callerPhone, "",
                        ("CALL_END:" + callerPhone).getBytes(StandardCharsets.UTF_8));
                activeCallView = null;
            }, socketManager);
            activeCallView.start(new Stage());
            return;
        }

        if (signal.equals("CALL_ACCEPTED")) {
            showToast("Appel accepté !");
            if (activeCallView != null)
                activeCallView.startCallSession();
            return;
        }
        if (signal.equals("CALL_JOINED")) {
            String joinedPhone = parts.length >= 2 ? parts[1] : null;
            if (joinedPhone != null && activeCallView != null) {
                activeCallView.handleJoined(joinedPhone);
            }
            return;
        }
        if (signal.equals("CALL_ACTIVE_LIST")) {
            String list = parts.length >= 2 ? parts[1] : "";
            if (activeCallView != null) {
                activeCallView.handleActiveList(list);
            }
            return;
        }
        if (signal.equals("CALL_TERMINATED")) {
            if (activeCallView != null) {
                activeCallView.handleTerminated();
            }
            return;
        }
        if (signal.equals("CALL_REJECTED")) {
            showToast("Appel refusé.");
            String caller = parts.length >= 2 ? parts[1] : sender;
            if (caller != null && caller.startsWith("GROUP_"))
                return;
            if (activeCallView != null) {
                activeCallView.endCall();
                activeCallView = null;
            }
            return;
        }
        if (signal.equals("CALL_ENDED")) {
            showToast("Appel terminé.");
            if (activeCallView != null) {
                activeCallView.endCall();
                activeCallView = null;
            }
            return;
        }
        if (signal.equals("CALL_LEFT")) {
            String leftPhone = parts.length >= 2 ? parts[1] : null;
            if (leftPhone != null && activeCallView != null) {
                showToast("Participant a quitté l'appel.");
                activeCallView.removeParticipant(leftPhone);
            }
            return;
        }
        if (signal.equals("CALL_MISSED")) {
            showToast("Appel manqué.");
        }
    }

    private void showNotification(String sender, String msg) {
        showToast("Message de " + sender + ": " + (msg.length() > 30 ? msg.substring(0, 30) + "..." : msg));
    }

    private void showToast(String message) {
        // Simple implementation for JavaFX
        System.out.println("[TOAST]: " + message);
    }

    private void addContact() {
        TextInputDialog phoneDialog = new TextInputDialog();
        phoneDialog.setTitle("Ajouter un contact");
        phoneDialog.setHeaderText("Entrer le numéro du contact :");
        Optional<String> phoneResult = phoneDialog.showAndWait();

        if (phoneResult.isPresent() && !phoneResult.get().trim().isEmpty()) {
            String phoneInput = normalizePhone(phoneResult.get());

            TextInputDialog nicknameDialog = new TextInputDialog();
            nicknameDialog.setTitle("Surnom");
            nicknameDialog.setHeaderText("Entrer un surnom (optionnel) :");
            Optional<String> nicknameResult = nicknameDialog.showAndWait();

            String nicknameInput = nicknameResult.orElse("");

            String payload = (!nicknameInput.trim().isEmpty()) ? "ADD:" + phoneInput + ":" + nicknameInput.trim()
                    : "ADD:" + phoneInput;
            socketManager.sendBinary("CONTACT_SIGNAL", "", "", payload.getBytes(StandardCharsets.UTF_8));

            new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                socketManager.sendBinary("CONTACT_SIGNAL", "", "",
                        "GET_CONTACTS".getBytes(StandardCharsets.UTF_8));
            }).start();
        }
    }

    private void createGroup() {
        TextInputDialog membersDialog = new TextInputDialog();
        membersDialog.setTitle("Membres du groupe");
        membersDialog.setHeaderText("Entrer les noms des contacts à ajouter (séparés par des virgules) :");
        Optional<String> membersResult = membersDialog.showAndWait();

        if (membersResult.isPresent() && !membersResult.get().trim().isEmpty()) {
            String membersStr = membersResult.get().trim();
            String[] membersArr = membersStr.split(",");
            for (String m : membersArr) {
                if (m.trim().isEmpty())
                    continue;
                if (!contactView.hasContactByName(m.trim())) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Erreur : Contact introuvable (" + m.trim() + ") !");
                    alert.showAndWait();
                    return; // Stop creation
                }
            }

            TextInputDialog nameDialog = new TextInputDialog();
            nameDialog.setTitle("Nouveau Groupe");
            nameDialog.setHeaderText("Entrer le nom du groupe :");
            Optional<String> nameResult = nameDialog.showAndWait();

            if (nameResult.isPresent() && !nameResult.get().trim().isEmpty()) {
                String groupName = nameResult.get().trim();
                String payload = "CREATE_GROUP:" + groupName + ":" + membersStr;
                socketManager.sendBinary("GROUP_SIGNAL", "", "",
                        payload.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void handleContactSignal(String payload) {
        contactView.updateContacts(payload);

        if (payload.startsWith("STATUS:")) {
            String[] parts = payload.split(":");
            if (parts.length >= 3) {
                String cphone = parts[1];
                String status = parts[2];
                String key = normalizeForCache(cphone);
                ConversationView cv = conversationCache.get(key);
                if (cv != null) {
                    cv.updateStatus(status);
                }
            }
        }

        // Propagate updated contact names to all active conversations
        for (ConversationView cv : conversationCache.values()) {
            cv.setAllContacts(contactView.allContacts);
        }
    }

    private void handleGroupSignal(String payload) {
        if (payload.startsWith("GROUP_INFO_REPLY:")) {
            if (activeConversation != null) {
                activeConversation.showGroupInfoDialog(payload);
            }
        } else if (payload.startsWith("GROUP_ADDED:") || payload.startsWith("GROUP_UPDATED:")
                || payload.startsWith("GROUP_CREATED:")) {
            if (payload.startsWith("GROUP_ADDED:") || payload.startsWith("GROUP_CREATED:")) {
                String[] parts = payload.split(":", 3);
                if (parts.length >= 3) {
                    String groupId = parts[1];
                    String groupName = parts[2];
                    contactView.addDynamicContact(Integer.parseInt(groupId), "GROUP_" + groupId, groupName, "ONLINE");
                }
            }
            contactView.loadContacts();
        } else if (payload.startsWith("GROUP_ERROR:")) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR, payload.substring(12));
                alert.showAndWait();
            });
        }
    }

    private String normalizeForCache(String phone) {
        if (phone == null)
            return "";
        if (phone.startsWith("GROUP_"))
            return phone;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() >= 9)
            return digits.substring(digits.length() - 9);
        return digits;
    }

    private String normalizePhone(String input) {
        if (input == null)
            return "";
        String normalized = input.replaceAll("[\\s\\-()]", "");
        return normalized.trim();
    }

    private void logout() {
        SessionManager.clearSession();
        SocketManager.reset();
        stage.close();
        NetworkClient freshNetwork = MainApp.createNetworkClient();
        new PhoneView(new AuthService(freshNetwork), freshNetwork).start(new Stage());
    }

    public static StackPane buildAvatar(String name, int size) {
        StackPane pane = new StackPane();
        Circle circle = new Circle(size / 2.0, Color.web("#00A884"));
        Label initials = new Label(
                name != null && !name.isEmpty() ? String.valueOf(name.charAt(0)).toUpperCase() : "?");
        initials.setFont(Font.font("Segoe UI", FontWeight.BOLD, size / 2.5));
        initials.setTextFill(Color.WHITE);
        pane.getChildren().addAll(circle, initials);
        return pane;
    }

    public static StackPane buildImageAvatar(byte[] imgBytes, int size) {
        try {
            Image img = new Image(new ByteArrayInputStream(imgBytes));
            ImageView iv = new ImageView(img);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(false);
            Circle clip = new Circle(size / 2.0, size / 2.0, size / 2.0);
            iv.setClip(clip);
            StackPane pane = new StackPane(iv);
            return pane;
        } catch (Exception e) {
            return buildAvatar("?", size);
        }
    }

    private void updateMyAvatar(byte[] bytes) {
        if (bytes != null && bytes.length > 0) {
            myAvatarPane.getChildren().clear();
            myAvatarPane.getChildren().add(buildImageAvatar(bytes, 44));
        }
    }

    private void showProfileDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Mon Profil");
        dialog.setHeaderText("Modifier votre profil");

        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #FFFFFF;");

        StackPane currentAvatar = avatarCache.containsKey(phone) ? buildImageAvatar(avatarCache.get(phone), 100)
                : buildAvatar(username, 100);

        Button btnChangePhoto = new Button("Changer la photo");
        btnChangePhoto.setStyle("-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-cursor: hand;");
        final byte[][] newAvatarBytes = { null };
        btnChangePhoto.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                try {
                    newAvatarBytes[0] = Files.readAllBytes(file.toPath());
                    layout.getChildren().set(0, buildImageAvatar(newAvatarBytes[0], 100));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        TextField nameField = new TextField(username);
        nameField.setStyle("-fx-background-color: #F0F2F5; -fx-text-fill: #111B21; -fx-border-color: #E9EDEF; -fx-border-radius: 5;");

        layout.getChildren().addAll(currentAvatar, btnChangePhoto, new Label("Nom d'utilisateur :"), nameField);

        dialog.getDialogPane().setContent(layout);
        ButtonType btnSave = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: #FFFFFF;");

        dialog.setResultConverter(b -> {
            if (b == btnSave) {
                String newName = nameField.getText().trim();
                byte[] dataToSend = newAvatarBytes[0] != null ? newAvatarBytes[0] : new byte[0];
                socketManager.sendBinary("UPDATE_PROFILE", "", newName, dataToSend);
                if (!newName.isEmpty()) {
                    username = newName;
                    myNameLbl.setText(newName);
                }
                if (newAvatarBytes[0] != null) {
                    avatarCache.put(phone, newAvatarBytes[0]);
                    updateMyAvatar(newAvatarBytes[0]);
                }
            }
            return null;
        });
        dialog.showAndWait();
    }
}