package authUi;

import client.NetworkClient;
import client.SocketManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ContactView {

    private final NetworkClient network;
    private final VBox convList;
    private final SocketManager socketManager;
    private final Map<String, HBox> contactRows = new HashMap<>();
    // Non-static: each user instance has its own isolated contact map
    public final Map<String, String> allContacts = new HashMap<>();

    public interface ConversationOpenCallback {
        void open(int id, String phone, String name, String status);
    }

    private ConversationOpenCallback openCallback;

    public ContactView(NetworkClient network, VBox convList, SocketManager socketManager) {
        this.network = network;
        this.convList = convList;
        this.socketManager = socketManager;
    }

    public void setConversationOpenCallback(ConversationOpenCallback openCallback) {
        this.openCallback = openCallback;
    }

    public void loadContacts() {
        socketManager.sendBinary("CONTACT_SIGNAL", "", "", "GET_CONTACTS".getBytes(StandardCharsets.UTF_8));
    }

    public void updateContacts(String payload) {
        // ─── STATUS update (user came online/offline) ──────────────────────────
        // Format: "STATUS:phone:ONLINE|"
        // DO NOT clear the contact list for status updates!
        if (payload.contains("STATUS:") && !payload.contains("CONTACTS_LIST:")) {
            Platform.runLater(() -> {
                String[] entries = payload.split("\\|");
                for (String entry : entries) {
                    if (!entry.startsWith("STATUS:")) continue;
                    String[] parts = entry.split(":");
                    if (parts.length >= 3) {
                        String phone = parts[1];
                        String status = parts[2];
                        HBox row = contactRows.get(phone);
                        if (row != null) {
                            Label statusLbl = (Label) row.getProperties().get("statusLabel");
                            if (statusLbl != null) updateStatusLabel(statusLbl, status);
                        }
                    }
                }
            });
            return; // ← never clear the list for a status signal
        }

        // ─── Full contacts list ─────────────────────────────────────────────────
        if (!payload.contains("CONTACTS_LIST:")) return;

        Platform.runLater(() -> {
            convList.getChildren().clear();
            contactRows.clear();
            allContacts.clear();

            String data = payload.substring("CONTACTS_LIST:".length());

            if (data.trim().isEmpty()) {
                Label noContacts = new Label("Aucun contact.");
                noContacts.setStyle("-fx-text-fill: #667781; -fx-padding: 15px;");
                convList.getChildren().add(noContacts);
                return;
            }

            String[] rows = data.split("\\|");
            for (String rowStr : rows) {
                if (rowStr.trim().isEmpty()) continue;
                String[] parts = rowStr.split(":");
                if (parts.length >= 4) {
                    int id = Integer.parseInt(parts[0]);
                    String phone = parts[1];
                    String name = parts[2];
                    String status = parts[3];
                    allContacts.put(phone, name);
                    addContactUI(id, phone, name, status);
                }
            }
        });
    }

    public void addDynamicContact(int id, String phone, String name, String status) {
        Platform.runLater(() -> {
            allContacts.put(phone, name);
            addContactUI(id, phone, name, status);
        });
    }

    public void refreshAvatar(String phone, byte[] avatarBytes) {
        Platform.runLater(() -> {
            HBox row = contactRows.get(phone);
            if (row != null) {
                StackPane avatarPane = ChatView.buildImageAvatar(avatarBytes, 48);
                row.getChildren().set(0, avatarPane);
            }
        });
    }

    public boolean hasContactByName(String name) {
        String cleanName = name.trim().toLowerCase(Locale.ROOT);
        for (HBox row : contactRows.values()) {
            String cName = String.valueOf(row.getProperties().getOrDefault("contactName", "")).toLowerCase(Locale.ROOT);
            if (cName.equals(cleanName)) return true;
        }
        return false;
    }

    public void addContactUI(int id, String phone, String name, String status) {
        if (contactRows.containsKey(phone)) {
            HBox existing = contactRows.get(phone);
            updateExistingRow(existing, name, status);
            return;
        }

        HBox row = new HBox(12);
        row.setPadding(new Insets(10, 15, 10, 15));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E9EDEF; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #F5F6F6; -fx-border-color: #E9EDEF; -fx-border-width: 0 0 1 0;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E9EDEF; -fx-border-width: 0 0 1 0;"));
        row.setOnMouseClicked(e -> {
            if (openCallback != null) openCallback.open(id, phone, name, status);
        });

        StackPane avatar;
        if (ChatView.avatarCache.containsKey(phone)) {
            avatar = ChatView.buildImageAvatar(ChatView.avatarCache.get(phone), 48);
        } else {
            avatar = ChatView.buildAvatar(name, 48);
            // Request avatar from server
            socketManager.sendBinary("GET_AVATAR", phone, "", "req".getBytes(StandardCharsets.UTF_8));
        }

        VBox info = new VBox(2);
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-text-fill: #111B21; -fx-font-weight: bold; -fx-font-size: 15px;");
        
        info.getChildren().addAll(nameLbl);

        Label statusLbl = new Label();
        updateStatusLabel(statusLbl, status);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnDelete = new Button("🗑");
        btnDelete.setStyle("-fx-background-color: transparent; -fx-text-fill: #54656F; -fx-font-size: 14px; -fx-cursor: hand;");
        btnDelete.setOnMouseEntered(e -> btnDelete.setStyle("-fx-background-color: transparent; -fx-text-fill: #EA0038; -fx-font-size: 14px; -fx-cursor: hand;"));
        btnDelete.setOnMouseExited(e -> btnDelete.setStyle("-fx-background-color: transparent; -fx-text-fill: #54656F; -fx-font-size: 14px; -fx-cursor: hand;"));
        btnDelete.setOnAction(e -> {
            // Correct protocol: CONTACT_SIGNAL with REMOVE:phone payload
            socketManager.sendBinary("CONTACT_SIGNAL", "", "",
                    ("REMOVE:" + phone).getBytes(StandardCharsets.UTF_8));
            contactRows.remove(phone);
            convList.getChildren().remove(row);
        });

        row.getChildren().addAll(avatar, info, statusLbl, spacer, btnDelete);

        row.getProperties().put("statusLabel", statusLbl);
        row.getProperties().put("searchText", (phone + " " + name).toLowerCase(Locale.ROOT));
        row.getProperties().put("contactName", name);
        
        contactRows.put(phone, row);
        convList.getChildren().add(row);
    }

    private void updateExistingRow(HBox row, String name, String status) {
        VBox info = (VBox) row.getChildren().get(1);
        Label nameLbl = (Label) info.getChildren().get(0);
        nameLbl.setText(name);

        Label statusLbl = (Label) row.getProperties().get("statusLabel");
        updateStatusLabel(statusLbl, status);
        
        row.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E9EDEF; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
    }

    private void updateStatusLabel(Label label, String status) {
        if (status.equals("ONLINE") || status.equals("OFFLINE")) {
            label.setText(status.equals("ONLINE") ? "En ligne" : "Hors ligne");
            label.setStyle("-fx-text-fill: " + (status.equals("ONLINE") ? "#00A884" : "#667781") + "; -fx-font-size: 12px;");
        } else {
            label.setText(status);
            label.setStyle("-fx-text-fill: #8696a0; -fx-font-size: 11px;");
        }
    }

    public void filterContacts(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        for (HBox row : contactRows.values()) {
            String text = (String) row.getProperties().get("searchText");
            row.setVisible(text.contains(q));
            row.setManaged(text.contains(q));
        }
    }
}