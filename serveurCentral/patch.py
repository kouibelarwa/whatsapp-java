import re

with open(r'c:\Users\HP\Desktop\whatsaap-java\serveurCentral\src\authUi\ConversationView.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Header (remove statusLabel from info)
content = content.replace('info.getChildren().addAll(nameLbl, statusLabel);', 'info.getChildren().addAll(nameLbl);')

# 2. Input bar layout
content = content.replace('inputBar.getChildren().addAll(btnEmoji, btnAudio, textField, btnFile, btnSend);', 'inputBar.getChildren().addAll(btnEmoji, btnFile, textField, btnAudio, btnSend);')

# 3. Helpers
helpers = """
    private String formatTime(java.sql.Timestamp ts) {
        if (ts == null) return new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
        return new java.text.SimpleDateFormat("HH:mm").format(ts);
    }

    private Label createTimeLabel(String timeStr, String etat, boolean mine) {
        String text = timeStr;
        if (mine && etat != null) {
            text += "READ".equals(etat) ? " ✓✓" : ("DELIVERED".equals(etat) ? " ✓✓" : " ✓");
        }
        Label timeLabel = new Label(text);
        String color = (mine && "READ".equals(etat)) ? "#53bdeb" : "#969696";
        timeLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 10px;");
        timeLabel.setAlignment(Pos.CENTER_RIGHT);
        return timeLabel;
    }

    private void addAudioBubble
"""
content = content.replace('    private void addAudioBubble', helpers.strip() + 'Bubble')

# 4. update bubble signatures
content = content.replace('private void addAudioBubble(File audioFile, boolean mine)', 'private void addAudioBubble(File audioFile, boolean mine, String timeStr, String etat)')
content = content.replace('private void addFileBubble(File localFile, String filename, String type, boolean mine)', 'private void addFileBubble(File localFile, String filename, String type, boolean mine, String timeStr, String etat)')
content = content.replace('private void addMessageBubble(String text, boolean mine)', 'private void addMessageBubble(String text, boolean mine, String timeStr, String etat)')

# 5. update timeLabels inside bubbles
time_replace = """        Label timeLabel = new Label("Maintenant");
        timeLabel.setStyle("-fx-text-fill: #969696; -fx-font-size: 10px;");
        timeLabel.setAlignment(Pos.CENTER_RIGHT);"""
content = content.replace(time_replace, '        Label timeLabel = createTimeLabel(timeStr, etat, mine);')

# 6. loadHistory
loadH_old = """        for (Message m : history) {
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
        }"""
loadH_new = """        for (Message m : history) {
            boolean mine = m.getSenderId() == myUserId;
            String timeStr = formatTime(m.getSentAt());
            String etat = m.getEtat();
            if (m.isText()) {
                addMessageBubble(m.getContent(), mine, timeStr, etat);
            } else if ("audio".equals(m.getType())) {
                byte[] data = messageDao.getDataById(m.getId());
                try {
                    File tempFile = File.createTempFile("history_audio_", ".wav");
                    Files.write(tempFile.toPath(), data);
                    addAudioBubble(tempFile, mine, timeStr, etat);
                } catch (Exception e) {
                    addMessageBubble("🎵 Message audio", mine, timeStr, etat);
                }
            } else {
                String filename = m.getFilename() != null ? m.getFilename() : "fichier";
                try {
                    byte[] binaryData = messageDao.getDataById(m.getId());
                    File tempFile = File.createTempFile("history_file_", "_" + filename.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    Files.write(tempFile.toPath(), binaryData);
                    addFileBubble(tempFile, filename, m.getType(), mine, timeStr, etat);
                } catch (Exception e) {
                    addMessageBubble("📎 " + filename, mine, timeStr, etat);
                }
            }
        }"""
content = content.replace(loadH_old, loadH_new)

# 7. sendCurrentPayload
content = content.replace('            addMessageBubble(text, true);', '            addMessageBubble(text, true, formatTime(null), "NOT_DELIVERED");')
content = content.replace('                addFileBubble(fileToSend, filename, typeToSend, true);', '                addFileBubble(fileToSend, filename, typeToSend, true, formatTime(null), "NOT_DELIVERED");')

# 8. stopRecordingAndSend
content = content.replace('                    addAudioBubble(tempAudioFile, true);', '                    addAudioBubble(tempAudioFile, true, formatTime(null), "NOT_DELIVERED");')

# 9. receiveMessage
content = content.replace('            if ("text".equals(type)) {', '            String timeStr = formatTime(null);\n            if ("text".equals(type)) {')
content = content.replace('                addMessageBubble(new String(data, StandardCharsets.UTF_8), false);', '                addMessageBubble(new String(data, StandardCharsets.UTF_8), false, timeStr, "READ");')
content = content.replace('                    addAudioBubble(tempFile, false);', '                    addAudioBubble(tempFile, false, timeStr, "READ");')
content = content.replace('                    addFileBubble(tempFile, safeName, type, false);', '                    addFileBubble(tempFile, safeName, type, false, timeStr, "READ");')
content = content.replace('                    addMessageBubble(label + " (" + (data.length/1024) + " KB)", false);', '                    addMessageBubble(label + " (" + (data.length/1024) + " KB)", false, timeStr, "READ");')
content = content.replace('                addMessageBubble("📎 Message reçu (" + type + ")", false);', '                addMessageBubble("📎 Message reçu (" + type + ")", false, timeStr, "READ");')


with open(r'c:\Users\HP\Desktop\whatsaap-java\serveurCentral\src\authUi\ConversationView.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("Patched!")
