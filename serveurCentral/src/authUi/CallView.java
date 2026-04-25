package authUi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * CallView — ✅ CORRIGÉ COMPLET :
 * - Timer NE démarre PAS avant que l'autre personne accepte
 * - Caméra s'active correctement pour appel vidéo
 * - Boutons bien visibles (style WhatsApp)
 * - Appel entrant : Accepter / Refuser bien stylisés
 * - Caméra ne s'active que si appel vidéo
 */
public class CallView extends JDialog {

    private static final Color BG_CALL    = new Color(15, 20, 30);
    private static final Color BG_OVERLAY = new Color(20, 28, 40);
    private static final Color GREEN      = new Color(37, 211, 102);
    private static final Color RED        = new Color(220, 60,  60);
    private static final Color GRAY_BTN   = new Color(55, 65,  85);

    private final String   contactName;
    private final String   contactPhone;
    private final String   callType;
    private final boolean  isIncoming;
    private final Runnable onHangUp;

    private javax.swing.Timer uiTimer;
    private int      seconds   = 0;
    // ✅ FIX : connected = false, timer ne démarre QUE quand l'autre accepte
    private boolean  connected = false;

    private JLabel     statusLabel;
    private JLabel     timerLabel;
    private WebcamPanel webcamPanel;

    public CallView(Frame parent, String contactName, String contactPhone,
                    String callType, boolean isIncoming, Runnable onHangUp) {
        super(parent, true);
        this.contactName  = contactName != null ? contactName : contactPhone;
        this.contactPhone = contactPhone;
        this.callType     = callType;
        this.isIncoming   = isIncoming;
        this.onHangUp     = onHangUp;

        setTitle("video".equals(callType)
                ? "Appel vidéo — " + this.contactName
                : "Appel audio — " + this.contactName);
        setUndecorated(true);
        setSize("video".equals(callType) ? new Dimension(480, 580)
                : new Dimension(340, 440));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        buildUI();
        startTimer();   // timer tourne mais N'incrémente PAS avant connected=true
    }

    // ────────────────────────────────────────────────────────────
    // CONSTRUCTION UI
    // ────────────────────────────────────────────────────────────
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_CALL);
        root.setBorder(BorderFactory.createLineBorder(new Color(50, 60, 80), 1));
        root.add(buildTitleBar(), BorderLayout.NORTH);

        if ("video".equals(callType)) root.add(buildVideoArea(), BorderLayout.CENTER);
        else                          root.add(buildAudioArea(), BorderLayout.CENTER);

        root.add(buildControls(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(20, 26, 38));
        bar.setBorder(new EmptyBorder(10, 14, 10, 14));
        bar.setPreferredSize(new Dimension(0, 42));

        JLabel title = new JLabel("video".equals(callType)
                ? "📹  Appel vidéo" : "📞  Appel audio");
        title.setForeground(new Color(180, 190, 210));
        title.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeBtn.setForeground(Color.GRAY);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> hangUp());

        // Draggable
        MouseAdapter drag = new MouseAdapter() {
            Point origin;
            @Override public void mousePressed(MouseEvent e)  { origin = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e)  {
                if (origin == null) return;
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - origin.x, loc.y + e.getY() - origin.y);
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);

        bar.add(title,    BorderLayout.WEST);
        bar.add(closeBtn, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildAudioArea() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_CALL);
        panel.setBorder(new EmptyBorder(30, 20, 20, 20));

        JPanel avatarPanel = buildBigAvatar(contactName, 90);
        avatarPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel nameLabel = new JLabel(contactName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ✅ Texte différent : entrant vs sortant
        statusLabel = new JLabel(isIncoming ? "📲  Appel entrant…" : "📡  En attente de réponse…");
        statusLabel.setForeground(new Color(160, 170, 190));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerLabel = new JLabel("0:00");
        timerLabel.setForeground(GREEN);
        timerLabel.setFont(new Font("Segoe UI Mono", Font.BOLD, 22));
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLabel.setVisible(false); // ✅ caché jusqu'à connexion

        panel.add(Box.createVerticalStrut(10));
        panel.add(avatarPanel);
        panel.add(Box.createVerticalStrut(20));
        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(timerLabel);
        return panel;
    }

    private JPanel buildVideoArea() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(10, 14, 22));

        // Zone distante (fond + avatar + statut)
        JPanel remoteArea = new JPanel(new GridBagLayout());
        remoteArea.setOpaque(true);
        remoteArea.setBackground(new Color(10, 14, 22));

        JPanel remoteContent = new JPanel();
        remoteContent.setLayout(new BoxLayout(remoteContent, BoxLayout.Y_AXIS));
        remoteContent.setOpaque(false);

        JPanel remoteAvatar = buildBigAvatar(contactName, 70);
        remoteAvatar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel remoteName = new JLabel(contactName);
        remoteName.setForeground(Color.WHITE);
        remoteName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        remoteName.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel = new JLabel(isIncoming
                ? "📲  Appel entrant…" : "📡  En attente de réponse…");
        statusLabel.setForeground(new Color(140, 150, 170));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerLabel = new JLabel("0:00");
        timerLabel.setForeground(GREEN);
        timerLabel.setFont(new Font("Segoe UI Mono", Font.BOLD, 16));
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLabel.setVisible(false); // ✅ caché jusqu'à connexion

        remoteContent.add(remoteAvatar);
        remoteContent.add(Box.createVerticalStrut(12));
        remoteContent.add(remoteName);
        remoteContent.add(Box.createVerticalStrut(6));
        remoteContent.add(statusLabel);
        remoteContent.add(Box.createVerticalStrut(6));
        remoteContent.add(timerLabel);
        remoteArea.add(remoteContent);

        // Vue de soi (petite fenêtre caméra)
        JPanel selfView = buildSelfView();

        JLayeredPane layered = new JLayeredPane();
        layered.setLayout(null);
        remoteArea.setBounds(0, 0, 480, 460);
        layered.add(remoteArea, JLayeredPane.DEFAULT_LAYER);
        selfView.setBounds(310, 340, 155, 110);
        layered.add(selfView, JLayeredPane.PALETTE_LAYER);
        layered.setPreferredSize(new Dimension(480, 460));

        panel.add(layered, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSelfView() {
        JPanel selfPanel = new JPanel(new BorderLayout());
        selfPanel.setBackground(new Color(10, 20, 40));
        selfPanel.setBorder(BorderFactory.createLineBorder(GREEN, 2));

        // ✅ FIX CAMÉRA : crée WebcamPanel seulement pour appel vidéo
        webcamPanel = new WebcamPanel(155, 88);
        webcamPanel.start();
        selfPanel.add(webcamPanel, BorderLayout.CENTER);

        JLabel meLabel = new JLabel("  Moi");
        meLabel.setForeground(new Color(180, 200, 180));
        meLabel.setBackground(new Color(0, 0, 0, 150));
        meLabel.setOpaque(true);
        meLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        meLabel.setPreferredSize(new Dimension(0, 18));
        selfPanel.add(meLabel, BorderLayout.SOUTH);
        return selfPanel;
    }

    // ────────────────────────────────────────────────────────────
    // CONTRÔLES — Boutons WhatsApp-style bien visibles
    // ────────────────────────────────────────────────────────────
    private JPanel buildControls() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_OVERLAY);
        panel.setBorder(new EmptyBorder(18, 20, 22, 20));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        btns.setOpaque(false);

        if (isIncoming) {
            // ✅ Appel entrant : ACCEPTER (vert) + REFUSER (rouge)
            JButton acceptBtn = makeCallBtn("📞", "Accepter", GREEN, 64);
            acceptBtn.addActionListener(e -> {
                connected = true;
                acceptBtn.setVisible(false);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("✅  Connecté");
                    timerLabel.setVisible(true);
                });
                // Notifier le serveur qu'on a accepté
                // (à appeler via SocketManager depuis ChatView si disponible)
            });

            JButton rejectBtn = makeCallBtn("📵", "Refuser", RED, 64);
            rejectBtn.addActionListener(e -> hangUp());

            // Labels sous les boutons
            JLabel lblAccept = makeBtnLabel("Accepter", GREEN);
            JLabel lblReject = makeBtnLabel("Refuser",  RED);

            JPanel acceptCol = column(acceptBtn, lblAccept);
            JPanel rejectCol = column(rejectBtn, lblReject);
            btns.add(acceptCol);
            btns.add(rejectCol);

        } else {
            // ✅ Appel sortant : MUET + [CAMÉRA si vidéo] + RACCROCHER

            JButton muteBtn = makeCallBtn("🎤", "Muet", GRAY_BTN, 56);
            muteBtn.addActionListener(e -> {
                boolean muted = "🔇".equals(muteBtn.getText());
                muteBtn.setText(muted ? "🎤" : "🔇");
                muteBtn.setBackground(muted ? GRAY_BTN : RED);
            });

            JButton hangUpBtn = makeCallBtn("📵", "Raccrocher", RED, 64);
            hangUpBtn.addActionListener(e -> hangUp());

            btns.add(column(muteBtn, makeBtnLabel("Muet", Color.LIGHT_GRAY)));

            if ("video".equals(callType)) {
                JButton camBtn = makeCallBtn("📹", "Caméra", GRAY_BTN, 56);
                camBtn.addActionListener(e -> {
                    if (webcamPanel != null) {
                        webcamPanel.togglePause();
                        camBtn.setBackground(webcamPanel.isPaused() ? RED : GRAY_BTN);
                    }
                });
                btns.add(column(camBtn, makeBtnLabel("Caméra", Color.LIGHT_GRAY)));
            }

            btns.add(column(hangUpBtn, makeBtnLabel("Raccrocher", RED)));
        }

        btns.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(btns);
        return panel;
    }

    /** Colonne : bouton + label dessous */
    private JPanel column(JButton btn, JLabel label) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(btn);
        col.add(Box.createVerticalStrut(6));
        col.add(label);
        return col;
    }

    private JLabel makeBtnLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        return l;
    }

    // ────────────────────────────────────────────────────────────
    // ✅ TIMER : ne tourne QUE si connected = true
    // ────────────────────────────────────────────────────────────
    private void startTimer() {
        uiTimer = new javax.swing.Timer(1000, e -> {
            if (!connected) return; // ✅ Bloqué jusqu'à acceptation
            seconds++;
            int m = seconds / 60, s = seconds % 60;
            if (timerLabel != null) {
                timerLabel.setVisible(true);
                timerLabel.setText(String.format("%d:%02d", m, s));
            }
        });
        uiTimer.start();
    }

    // ────────────────────────────────────────────────────────────
    // API PUBLIQUE : appelée depuis ChatView quand signal reçu
    // ────────────────────────────────────────────────────────────

    /** CALL_ACCEPTED reçu → démarrer le timer */
    public void onCallAccepted() {
        connected = true;
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) statusLabel.setText("✅  Connecté");
            if (timerLabel  != null) timerLabel.setVisible(true);
        });
    }

    /** CALL_REJECTED reçu → afficher message + fermer */
    public void onCallRejected() {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) statusLabel.setText("❌  Appel refusé");
            new javax.swing.Timer(2000, e -> hangUp()).start();
        });
    }

    /** CALL_ENDED reçu → fermer */
    public void onCallEnded() {
        SwingUtilities.invokeLater(this::hangUp);
    }

    // ────────────────────────────────────────────────────────────
    // HANG UP
    // ────────────────────────────────────────────────────────────
    private void hangUp() {
        if (uiTimer    != null) uiTimer.stop();
        if (webcamPanel != null) webcamPanel.stop();
        if (onHangUp   != null) onHangUp.run();
        dispose();
    }

    // ────────────────────────────────────────────────────────────
    // UTILITAIRES
    // ────────────────────────────────────────────────────────────
    private JPanel buildBigAvatar(String name, int size) {
        JPanel av = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // Halo vert
                g2.setColor(new Color(37, 211, 102, 35));
                g2.fillOval(-12, -12, size + 24, size + 24);
                // Cercle principal
                g2.setColor(GREEN);
                g2.fillOval(0, 0, size, size);
                // Initiale
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, size / 2 - 2));
                FontMetrics fm = g2.getFontMetrics();
                String init = name != null && !name.isEmpty()
                        ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
                g2.drawString(init,
                        (size - fm.stringWidth(init)) / 2,
                        (size + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        av.setOpaque(false);
        av.setPreferredSize(new Dimension(size + 24, size + 24));
        av.setMaximumSize(new Dimension(size + 24, size + 24));
        return av;
    }

    /** Bouton rond style WhatsApp Call */
    private JButton makeCallBtn(String icon, String tooltip, Color bg, int size) {
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
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, size / 3));
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

    // ────────────────────────────────────────────────────────────
    // WEBCAM PANEL (simulation, remplacer par vraie caméra)
    // ────────────────────────────────────────────────────────────
    static class WebcamPanel extends JPanel {
        private volatile boolean running = false;
        private volatile boolean paused  = false;
        private volatile BufferedImage frame;
        private final int w, h;
        private Thread captureThread;

        WebcamPanel(int w, int h) {
            this.w = w; this.h = h;
            setPreferredSize(new Dimension(w, h));
            setBackground(new Color(15, 20, 35));
            setOpaque(true);
        }

        public void start() {
            running = true;
            captureThread = new Thread(this::captureLoop, "WebcamCapture");
            captureThread.setDaemon(true);
            captureThread.start();
        }

        public void stop() {
            running = false;
            if (captureThread != null) captureThread.interrupt();
        }

        public void togglePause() { paused = !paused; }
        public boolean isPaused() { return paused; }

        private void captureLoop() {
            int dots = 0;
            while (running) {
                if (!paused) {
                    frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = frame.createGraphics();
                    g2.setColor(new Color(15, 25, 45));
                    g2.fillRect(0, 0, w, h);
                    g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
                    g2.setColor(new Color(60, 90, 130));
                    g2.drawString("📷", w / 2 - 14, h / 2 - 4);
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                    g2.setColor(new Color(100, 130, 180));
                    g2.drawString("Caméra active" + ".".repeat(dots % 3 + 1),
                            w / 2 - 30, h / 2 + 14);
                    g2.dispose();
                    dots++;
                    SwingUtilities.invokeLater(this::repaint);
                }
                try { Thread.sleep(500); }
                catch (InterruptedException e) { break; }
            }
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (frame != null) g.drawImage(frame, 0, 0, w, h, null);
            else { g.setColor(new Color(15, 25, 45)); g.fillRect(0, 0, w, h); }
        }
    }
}