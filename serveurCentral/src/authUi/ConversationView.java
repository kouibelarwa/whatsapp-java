package authUi;

import client.SocketManager;
import dao.MessageDao;
import dao.UserDao;
import model.Message;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ConversationView extends JPanel {

    private final int    myUserId;
    private final String myPhone;
    private final String contactPhone;
    private final String contactName;
    private       String contactStatus;
    private final int    contactId;

    private JPanel      messagesPanel;
    private JScrollPane scrollPane;
    private JTextField  textField;
    private JLabel      statusLabel;
    private JButton     btnSend;
    private JButton     btnAudio;
    private JLabel      recLabel;

    private TargetDataLine        micLine;
    private ByteArrayOutputStream audioBuffer;
    private volatile boolean      recording = false;
    private Thread                recorderThread;

    private final MessageDao messageDao = new MessageDao();
    private final UserDao    userDao    = new UserDao();

    private static final Color BG_DARK        = new Color(11,  20,  14);
    private static final Color BG_MESSAGES    = new Color(14,  22,  16);
    private static final Color BG_INPUT       = new Color(21,  30,  24);
    private static final Color BG_HEADER      = new Color(21,  30,  24);
    private static final Color BG_INPUT_FIELD = new Color(42,  55,  46);
    private static final Color COLOR_SENT     = new Color(0,   92,  75);
    private static final Color COLOR_RECV     = new Color(32,  44,  34);
    private static final Color COLOR_GREEN    = new Color(37, 211, 102);
    private static final Color COLOR_RED      = new Color(220,  60,  60);
    private static final Color COLOR_TEXT     = new Color(230, 230, 230);
    private static final Color COLOR_TIME     = new Color(150, 150, 150);

    public ConversationView(int myUserId, String myPhone,
                            String contactPhone, String contactName,
                            String contactStatus) {
        this.myUserId      = myUserId;
        this.myPhone       = myPhone;
        this.contactPhone  = contactPhone;
        this.contactName   = contactName != null ? contactName : contactPhone;
        this.contactStatus = contactStatus != null ? contactStatus : "OFFLINE";
        this.contactId     = userDao.getIdByPhone(contactPhone);

        setLayout(new BorderLayout());
        setBackground(BG_DARK);
        buildHeader();
        buildMessages();
        buildInputBar();
        loadHistory();

        if (contactId != -1) messageDao.markAllAsRead(contactId, myUserId);
    }
    private CallView activeCallView = null;
    // ── HEADER ──────────────────────────────────────────────────
    private void buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_HEADER);
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        header.setPreferredSize(new Dimension(0, 64));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        JPanel avatar = buildAvatar(contactName, 42);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel nameLabel = new JLabel(contactName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

        boolean online = "ONLINE".equals(contactStatus);
        statusLabel = new JLabel("● " + (online ? "En ligne" : "Hors ligne"));
        statusLabel.setForeground(online ? COLOR_GREEN : new Color(120, 120, 120));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        info.add(nameLabel);
        info.add(statusLabel);
        left.add(avatar);
        left.add(info);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JButton btnAudioCall = makeHeaderBtn("Tel", "Appel audio");
        btnAudioCall.addActionListener(e -> startCall("audio"));

        JButton btnVideoCall = makeHeaderBtn("Vid", "Appel video");
        btnVideoCall.addActionListener(e -> startCall("video"));

        right.add(btnAudioCall);
        right.add(btnVideoCall);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        JPanel sep = new JPanel();
        sep.setBackground(new Color(30, 45, 35));
        sep.setPreferredSize(new Dimension(0, 1));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(header, BorderLayout.CENTER);
        wrap.add(sep,    BorderLayout.SOUTH);
        add(wrap, BorderLayout.NORTH);
    }

    // ── ZONE DE MESSAGES ─────────────────────────────────────────
    private void buildMessages() {
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(BG_MESSAGES);
        messagesPanel.setBorder(new EmptyBorder(12, 8, 12, 8));

        scrollPane = new JScrollPane(messagesPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(BG_MESSAGES);
        scrollPane.getViewport().setBackground(BG_MESSAGES);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
    }

    // ── BARRE D'ENTRÉE ───────────────────────────────────────────
    private void buildInputBar() {
        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setBackground(BG_INPUT);
        inputBar.setBorder(new EmptyBorder(10, 10, 10, 10));

        // ── Bouton fichier seulement (emoji supprimé) ──
        JButton btnFile = makeRoundBtn("[F]", "Fichier", 40, BG_INPUT_FIELD);
        btnFile.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnFile.addActionListener(e -> sendFile());

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftBtns.setOpaque(false);
        leftBtns.add(btnFile);

        // ── Champ de texte ──
        textField = new JTextField();
        textField.setBackground(BG_INPUT_FIELD);
        textField.setForeground(COLOR_TEXT);
        textField.setCaretColor(Color.WHITE);
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 80, 65), 1, true),
                new EmptyBorder(10, 14, 10, 14)));
        textField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textField.setToolTipText("Ecrire un message...");
        textField.addActionListener(e -> sendText());

        recLabel = new JLabel("  [REC] Enregistrement... cliquez pour envoyer");
        recLabel.setForeground(COLOR_RED);
        recLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        recLabel.setVisible(false);
        recLabel.setBorder(new EmptyBorder(0, 8, 0, 8));

        JPanel centerPanel = new JPanel(new BorderLayout(4, 0));
        centerPanel.setOpaque(false);
        centerPanel.add(textField, BorderLayout.CENTER);
        centerPanel.add(recLabel,  BorderLayout.EAST);

        // ── Boutons droite ──
        btnAudio = makeRoundBtn("[M]", "Cliquer pour enregistrer", 44, COLOR_GREEN);
        btnAudio.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnAudio.addActionListener(e -> {
            if (!recording) {
                startRecording();
                btnAudio.setToolTipText("Cliquez pour arreter et envoyer");
            } else {
                stopAndSendRecording();
                btnAudio.setToolTipText("Cliquer pour enregistrer");
            }
        });

        btnSend = makeRoundBtn(">", "Envoyer", 44, COLOR_GREEN);
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 18));
        btnSend.setForeground(Color.WHITE);
        btnSend.addActionListener(e -> sendText());
        btnSend.setVisible(false);

        textField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void update() {
                boolean hasText = !textField.getText().isEmpty();
                btnSend.setVisible(hasText);
                btnAudio.setVisible(!hasText);
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightBtns.setOpaque(false);
        rightBtns.add(btnAudio);
        rightBtns.add(btnSend);

        inputBar.add(leftBtns,    BorderLayout.WEST);
        inputBar.add(centerPanel, BorderLayout.CENTER);
        inputBar.add(rightBtns,   BorderLayout.EAST);

        JPanel sep = new JPanel();
        sep.setBackground(new Color(30, 45, 35));
        sep.setPreferredSize(new Dimension(0, 1));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(sep,      BorderLayout.NORTH);
        wrap.add(inputBar, BorderLayout.CENTER);
        add(wrap, BorderLayout.SOUTH);
    }

    // ── HISTORIQUE ───────────────────────────────────────────────
    private void loadHistory() {
        if (contactId == -1) return;
        List<Message> history = messageDao.getConversation(myUserId, contactId);
        for (Message m : history) {
            boolean mine = m.getSenderId() == myUserId;
            if (m.isText()) {
                addMessageBubble(m.getContent(), mine, m.getEtat());
            } else {
                // ✅ Charger les données binaires depuis la DB
                byte[] data = messageDao.getDataById(m.getId());
                addFileBubble(m.getType(), m.getFilename(), mine, m.getEtat(), data);
            }
        }
        scrollToBottom();
    }

    // ── ENVOI TEXTE ──────────────────────────────────────────────
    private void sendText() {
        String text = textField.getText().trim();
        if (text.isEmpty()) return;
        textField.setText("");
        addMessageBubble(text, true, "SENT");
        scrollToBottom();
        new Thread(() ->
                SocketManager.getInstance().sendBinary(
                        "text", contactPhone, "", text.getBytes(StandardCharsets.UTF_8))
        ).start();
    }

    // ── RÉCEPTION MESSAGE ────────────────────────────────────────
    public void receiveMessage(String type, String filename, byte[] data) {
        SwingUtilities.invokeLater(() -> {
            if ("text".equals(type))
                addMessageBubble(
                        new String(data, StandardCharsets.UTF_8), false, "READ");
            else if ("audio".equals(type))
                addFileBubble(type, filename, false, "READ", data);
            else if ("image".equals(type))
                addFileBubble(type, filename, false, "READ", data);
            else
                addFileBubble(type, filename, false, "READ", data);
            scrollToBottom();
            if (contactId != -1)
                messageDao.markAllAsRead(contactId, myUserId);
        });
    }

    // ── MISE À JOUR STATUT ───────────────────────────────────────
    public void updateContactStatus(String newStatus) {
        this.contactStatus = newStatus;
        if (statusLabel != null) SwingUtilities.invokeLater(() -> {
            boolean online = "ONLINE".equals(newStatus);
            statusLabel.setText("● " + (online ? "En ligne" : "Hors ligne"));
            statusLabel.setForeground(online ? COLOR_GREEN : new Color(120, 120, 120));
        });
    }

    // ── CLEAR MESSAGES ───────────────────────────────────────────
    public void clearMessages() {
        SwingUtilities.invokeLater(() -> {
            messagesPanel.removeAll();
            messagesPanel.revalidate();
            messagesPanel.repaint();
        });
    }

    // ── BULLE TEXTE ──────────────────────────────────────────────
    private void addMessageBubble(String text, boolean mine, String etat) {
        JPanel wrapper = new JPanel(
                new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 3));
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        final Color bg = mine ? COLOR_SENT : COLOR_RECV;
        JPanel bubble = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(8, 14, 6, 14));

        int maxW = 360;
        JTextArea ta = new JTextArea(text);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        ta.setForeground(Color.WHITE);
        ta.setOpaque(false);
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setFocusable(false);
        ta.setBorder(null);
        ta.setMaximumSize(new Dimension(maxW, Integer.MAX_VALUE));
        bubble.add(ta);

        JPanel timeRow = new JPanel(
                new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 3, 0));
        timeRow.setOpaque(false);
        java.time.LocalTime now = java.time.LocalTime.now();
        JLabel timeLabel = new JLabel(
                String.format("%02d:%02d", now.getHour(), now.getMinute()));
        timeLabel.setForeground(COLOR_TIME);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeRow.add(timeLabel);

        if (mine) {
            String tick; Color col;
            switch (etat != null ? etat : "SENT") {
                case "READ":      tick = "vv"; col = new Color(83, 182, 255); break;
                case "DELIVERED": tick = "vv"; col = Color.LIGHT_GRAY;        break;
                default:          tick = "v";  col = Color.GRAY;              break;
            }
            JLabel tickLabel = new JLabel(tick);
            tickLabel.setForeground(col);
            tickLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeRow.add(tickLabel);
        }
        bubble.add(timeRow);
        wrapper.add(bubble);
        messagesPanel.add(wrapper);
        messagesPanel.add(Box.createVerticalStrut(2));
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    // ── BULLE FICHIER ✅ CORRIGÉE ────────────────────────────────
    private void addFileBubble(String type, String filename,
                               boolean mine, String etat, byte[] fileData) {
        if ("audio".equals(type)) {
            addAudioBubble(filename, mine, etat, fileData);
            return;
        }

        // Icône texte selon type
        String icon;
        switch (type != null ? type : "") {
            case "video":
                icon = "[Video]";
                break;
            case "image":
                icon = "[Image]";
                break;
            default:
                icon = "[Fichier]";
                break;
        }

        JPanel wrapper = new JPanel(
                new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 3));
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        final Color bg = mine ? COLOR_SENT : COLOR_RECV;
        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(10, 14, 8, 14));

        // Nom du fichier
        JLabel nameLabel = new JLabel(icon + " " + (filename != null ? filename : type));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        bubble.add(nameLabel);

        // ✅ Bouton telecharger si données disponibles
        if (fileData != null && fileData.length > 0) {
            JButton btnDl = new JButton("Telecharger");
            btnDl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            btnDl.setForeground(Color.WHITE);
            btnDl.setBackground(new Color(0, 120, 100));
            btnDl.setBorderPainted(false);
            btnDl.setFocusPainted(false);
            btnDl.setOpaque(true);
            btnDl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final byte[] dataToSave = fileData;
            final String fname = filename;
            btnDl.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File(fname != null ? fname : "fichier"));
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    try {
                        java.nio.file.Files.write(
                                chooser.getSelectedFile().toPath(), dataToSave);
                        JOptionPane.showMessageDialog(this, "Fichier sauvegarde !");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this,
                                "Erreur : " + ex.getMessage());
                    }
                }
            });
            bubble.add(Box.createVerticalStrut(6));
            bubble.add(btnDl);
        }

        // Heure
        JPanel timeRow = new JPanel(
                new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 3, 0));
        timeRow.setOpaque(false);
        java.time.LocalTime now = java.time.LocalTime.now();
        JLabel tl = new JLabel(String.format("%02d:%02d", now.getHour(), now.getMinute()));
        tl.setForeground(COLOR_TIME);
        tl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeRow.add(tl);
        bubble.add(timeRow);

        wrapper.add(bubble);
        messagesPanel.add(wrapper);
        messagesPanel.add(Box.createVerticalStrut(2));
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    // ── BULLE AUDIO ──────────────────────────────────────────────
    private void addAudioBubble(String filename, boolean mine,
                                String etat, byte[] audioData) {
        JPanel wrapper = new JPanel(
                new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 3));
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        final Color bg = mine ? COLOR_SENT : COLOR_RECV;
        JPanel bubble = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(10, 14, 8, 14));

        JPanel audioRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        audioRow.setOpaque(false);

        JButton playBtn = new JButton("Play");
        playBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        playBtn.setForeground(Color.WHITE);
        playBtn.setBackground(COLOR_GREEN);
        playBtn.setPreferredSize(new Dimension(55, 36));
        playBtn.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        playBtn.setFocusPainted(false);
        playBtn.setOpaque(true);
        playBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        WavePanel wavePanel = new WavePanel();

        int secs = audioData != null
                ? Math.max(1, (audioData.length - 44) / (44100 * 2)) : 5;
        JLabel durLabel = new JLabel("0:" + String.format("%02d", secs));
        durLabel.setForeground(COLOR_TIME);
        durLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        playBtn.addActionListener(e -> {
            if (audioData != null && audioData.length > 44) {
                playAudioBytes(audioData, playBtn, wavePanel);
            } else {
                playBtn.setEnabled(false);
                new Thread(() -> {
                    for (int i = 0; i < 12; i++) {
                        final int idx = i;
                        SwingUtilities.invokeLater(() -> wavePanel.setActive(idx));
                        try { Thread.sleep(200); }
                        catch (InterruptedException ignored) {}
                    }
                    SwingUtilities.invokeLater(() -> {
                        playBtn.setText("Play");
                        playBtn.setEnabled(true);
                        wavePanel.setActive(-1);
                    });
                }).start();
            }
        });

        audioRow.add(playBtn);
        audioRow.add(wavePanel);
        audioRow.add(durLabel);
        bubble.add(audioRow);

        JPanel timeRow = new JPanel(
                new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 3, 0));
        timeRow.setOpaque(false);
        java.time.LocalTime now = java.time.LocalTime.now();
        JLabel tl = new JLabel(String.format("%02d:%02d", now.getHour(), now.getMinute()));
        tl.setForeground(COLOR_TIME);
        tl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeRow.add(tl);
        bubble.add(timeRow);

        wrapper.add(bubble);
        messagesPanel.add(wrapper);
        messagesPanel.add(Box.createVerticalStrut(2));
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    private void playAudioBytes(byte[] wavData, JButton playBtn, WavePanel wavePanel) {
        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> {
                    playBtn.setText("Stop");
                    playBtn.setEnabled(false);
                });
                AudioInputStream ais = AudioSystem.getAudioInputStream(
                        new BufferedInputStream(new ByteArrayInputStream(wavData)));
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                long stepMs = Math.max(clip.getMicrosecondLength() / 1000 / 12, 80);
                clip.start();
                for (int i = 0; i < 12; i++) {
                    final int idx = i;
                    SwingUtilities.invokeLater(() -> wavePanel.setActive(idx));
                    Thread.sleep(stepMs);
                }
                clip.drain();
                clip.close();
                SwingUtilities.invokeLater(() -> {
                    playBtn.setText("Play");
                    playBtn.setEnabled(true);
                    wavePanel.setActive(-1);
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    playBtn.setText("Play");
                    playBtn.setEnabled(true);
                });
            }
        }, "AudioPlayer").start();
    }

    // ── ENREGISTREMENT AUDIO ─────────────────────────────────────
    private void startRecording() {
        AudioFormat fmt = new AudioFormat(44100, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);

        if (!AudioSystem.isLineSupported(info)) {
            JOptionPane.showMessageDialog(this,
                    "Microphone non disponible !",
                    "Microphone", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            micLine = (TargetDataLine) AudioSystem.getLine(info);
            micLine.open(fmt);
            micLine.start();
        } catch (LineUnavailableException ex) {
            JOptionPane.showMessageDialog(this,
                    "Impossible d'acceder au microphone : " + ex.getMessage(),
                    "Erreur micro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        audioBuffer = new ByteArrayOutputStream();
        recording   = true;

        SwingUtilities.invokeLater(() -> {
            btnAudio.setBackground(COLOR_RED);
            btnAudio.setText("[STOP]");
            recLabel.setVisible(true);
        });

        recorderThread = new Thread(() -> {
            byte[] buf = new byte[4096];
            while (recording) {
                int n = micLine.read(buf, 0, buf.length);
                if (n > 0) audioBuffer.write(buf, 0, n);
            }
        }, "AudioRecorder");
        recorderThread.setDaemon(true);
        recorderThread.start();
    }

    private void stopAndSendRecording() {
        if (!recording) return;
        recording = false;

        if (micLine != null) {
            micLine.stop();
            micLine.drain();
            micLine.close();
        }
        if (recorderThread != null) {
            try { recorderThread.join(1000); }
            catch (InterruptedException ignored) {}
        }

        SwingUtilities.invokeLater(() -> {
            btnAudio.setBackground(COLOR_GREEN);
            btnAudio.setText("[M]");
            recLabel.setVisible(false);
        });

        byte[] rawPCM = audioBuffer != null ? audioBuffer.toByteArray() : new byte[0];

        // ✅ Minimum 0.1 seconde
        if (rawPCM.length < 8820) {
            JOptionPane.showMessageDialog(this,
                    "Enregistrement trop court !\nCliquez pour demarrer, recliquez pour arreter.",
                    "Trop court", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        byte[] wavData = pcmToWav(rawPCM, 44100, 1, 16);
        String fname   = "audio_" + System.currentTimeMillis() + ".wav";

        addAudioBubble(fname, true, "SENT", wavData);
        scrollToBottom();
        new Thread(() ->
                SocketManager.getInstance().sendBinary("audio", contactPhone, fname, wavData)
        ).start();
    }

    // ── ENVOI FICHIER ✅ CORRIGÉ ─────────────────────────────────
    private void sendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choisir un fichier a envoyer");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file.length() > 50L * 1024 * 1024) {
            JOptionPane.showMessageDialog(this,
                    "Fichier trop grand (limite 50 Mo) !");
            return;
        }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            String name = file.getName();
            String type = detectType(name);
            // ✅ FIX : toujours passer data, jamais null
            addFileBubble(type, name, true, "SENT", data);
            scrollToBottom();
            new Thread(() ->
                    SocketManager.getInstance().sendBinary(type, contactPhone, name, data)
            ).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur : " + e.getMessage());
        }
    }

    private String detectType(String name) {
        String low = name.toLowerCase();
        if (low.matches(".*\\.(mp3|wav|ogg|aac|m4a)$"))       return "audio";
        if (low.matches(".*\\.(mp4|avi|mkv|mov|wmv)$"))       return "video";
        if (low.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$")) return "image";
        return "file";
    }

    // ── APPEL ────────────────────────────────────────────────────
    private void startCall(String callType) {

        // Si appel déjà en cours, ignorer
        if (activeCallView != null && activeCallView.isVisible()) {
            JOptionPane.showMessageDialog(this,
                    "Un appel est déjà en cours !",
                    "Appel en cours", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Nettoyer l'ancien proprement
        if (activeCallView != null) {
            activeCallView.forceStop();
            activeCallView = null;
        }

        // Envoyer signal au serveur
        SocketManager.getInstance().sendBinary(
                "CALL_SIGNAL", contactPhone, "",
                ("CALL_REQUEST:" + contactPhone)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);

        activeCallView = new CallView(
                parent, contactName, contactPhone, callType, false,
                () -> {
                    SocketManager.getInstance().sendBinary(
                            "CALL_SIGNAL", contactPhone, "",
                            ("CALL_END:" + contactPhone)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    activeCallView = null;
                });

        SwingUtilities.invokeLater(() -> activeCallView.setVisible(true));
    }

    // ── UTILITAIRES ──────────────────────────────────────────────
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            messagesPanel.revalidate();
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private JPanel buildAvatar(String name, int size) {
        JPanel av = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLOR_GREEN);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
                FontMetrics fm = g2.getFontMetrics();
                String init = name != null && !name.isEmpty()
                        ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
                g2.drawString(init,
                        (getWidth()  - fm.stringWidth(init)) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        av.setOpaque(false);
        av.setPreferredSize(new Dimension(size, size));
        return av;
    }

    private JButton makeRoundBtn(String icon, String tooltip, int size, Color bg) {
        JButton btn = new JButton(icon) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(size, size));
        return btn;
    }

    private JButton makeHeaderBtn(String icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(new Color(180, 190, 180));
        btn.setBackground(BG_HEADER);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(50, 36));
        return btn;
    }

    // ── PCM → WAV ────────────────────────────────────────────────
    private byte[] pcmToWav(byte[] pcm, int sampleRate, int channels, int bitDepth) {
        int byteRate   = sampleRate * channels * bitDepth / 8;
        int blockAlign = channels * bitDepth / 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        try {
            out.write("RIFF".getBytes());  writeInt(out,   36 + pcm.length);
            out.write("WAVE".getBytes());
            out.write("fmt ".getBytes());  writeInt(out,   16);
            writeShort(out, (short) 1);    writeShort(out, (short) channels);
            writeInt(out, sampleRate);     writeInt(out,   byteRate);
            writeShort(out, (short) blockAlign);
            writeShort(out, (short) bitDepth);
            out.write("data".getBytes());  writeInt(out, pcm.length);
            out.write(pcm);
        } catch (IOException ignored) {}
        return out.toByteArray();
    }

    private void writeInt(ByteArrayOutputStream o, int v) throws IOException {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF); o.write((v >> 24) & 0xFF);
    }

    private void writeShort(ByteArrayOutputStream o, short v) throws IOException {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF);
    }
}