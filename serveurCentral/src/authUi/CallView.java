package authUi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

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

    private Runnable acceptCallback;

    private javax.swing.Timer uiTimer;
    private int     seconds   = 0;
    private boolean connected = false;

    private JLabel      statusLabel;
    private JLabel      timerLabel;
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

        // ✅ Plein écran
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screen = ge.getMaximumWindowBounds();
        setSize(screen.width, screen.height);
        setLocation(screen.x, screen.y);

        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        buildUI();
        startTimer();
    }

    public void setAcceptCallback(Runnable callback) {
        this.acceptCallback = callback;
    }

    // ────────────────────────────────────────────────────────────
    // UI
    // ────────────────────────────────────────────────────────────
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_CALL);
        root.add(buildTopBar(),   BorderLayout.NORTH);

        if ("video".equals(callType)) root.add(buildVideoArea(), BorderLayout.CENTER);
        else                          root.add(buildAudioArea(), BorderLayout.CENTER);

        root.add(buildControls(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    // ── BARRE HAUT : type + nom + statut + timer + X ──
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(10, 14, 24));
        bar.setBorder(new EmptyBorder(14, 20, 14, 20));

        // Gauche : type d'appel
        JLabel typeLabel = new JLabel("video".equals(callType) ? "📹 Appel vidéo" : "📞 Appel audio");
        typeLabel.setForeground(new Color(160, 170, 190));
        typeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Centre : nom + statut + timer
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(contactName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel = new JLabel(isIncoming ? "📲  Appel entrant…" : "📡  En attente de réponse…");
        statusLabel.setForeground(new Color(160, 170, 190));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerLabel = new JLabel("0:00");
        timerLabel.setForeground(GREEN);
        timerLabel.setFont(new Font("Segoe UI Mono", Font.BOLD, 18));
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLabel.setVisible(false);

        centerPanel.add(nameLabel);
        centerPanel.add(Box.createVerticalStrut(3));
        centerPanel.add(statusLabel);
        centerPanel.add(Box.createVerticalStrut(2));
        centerPanel.add(timerLabel);

        // Droite : bouton fermer
        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        closeBtn.setForeground(new Color(180, 180, 180));
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> hangUp());

        bar.add(typeLabel,   BorderLayout.WEST);
        bar.add(centerPanel, BorderLayout.CENTER);
        bar.add(closeBtn,    BorderLayout.EAST);
        return bar;
    }

    // ── ZONE AUDIO ──
    private JPanel buildAudioArea() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG_CALL);
        JPanel avatar = buildBigAvatar(contactName, 140);
        panel.add(avatar);
        return panel;
    }

    // ── ZONE VIDÉO plein écran ──
    private JPanel buildVideoArea() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(8, 10, 18));

        // Fond : avatar contact au centre
        JPanel remoteArea = new JPanel(new GridBagLayout());
        remoteArea.setOpaque(true);
        remoteArea.setBackground(new Color(8, 10, 18));
        remoteArea.add(buildBigAvatar(contactName, 110));

        // Vue de soi
        webcamPanel = new WebcamPanel(400, 280);
        webcamPanel.start();

        JPanel selfPanel = new JPanel(new BorderLayout());
        selfPanel.setBackground(new Color(10, 20, 40));
        selfPanel.setBorder(BorderFactory.createLineBorder(GREEN, 2));
        selfPanel.add(webcamPanel, BorderLayout.CENTER);

        JLabel meLabel = new JLabel("  Moi");
        meLabel.setForeground(new Color(180, 200, 180));
        meLabel.setBackground(new Color(0, 0, 0, 160));
        meLabel.setOpaque(true);
        meLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        meLabel.setPreferredSize(new Dimension(0, 20));
        selfPanel.add(meLabel, BorderLayout.SOUTH);

        // Layered pane — s'adapte au redimensionnement
        JLayeredPane layered = new JLayeredPane();
        layered.setLayout(null);

        layered.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int lw = layered.getWidth();
                int lh = layered.getHeight();
                remoteArea.setBounds(0, 0, lw, lh);
                selfPanel.setBounds(lw - 430, lh - 310, 400, 290);
            }
        });

        layered.add(remoteArea, JLayeredPane.DEFAULT_LAYER);
        layered.add(selfPanel,  JLayeredPane.PALETTE_LAYER);

        panel.add(layered, BorderLayout.CENTER);
        return panel;
    }

    // ── CONTRÔLES BAS ──
    private JPanel buildControls() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(10, 14, 24));
        panel.setBorder(new EmptyBorder(20, 20, 30, 20));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 28, 0));
        btns.setOpaque(false);

        if (isIncoming) {
            JButton acceptBtn = makeCallBtn("📞", "Accepter", GREEN, 72);
            JButton rejectBtn = makeCallBtn("📵", "Refuser",  RED,   72);

            acceptBtn.addActionListener(e -> {
                connected = true;
                acceptBtn.setVisible(false);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("✅  Connecté");
                    timerLabel.setVisible(true);
                });
                if (acceptCallback != null) acceptCallback.run();
            });

            rejectBtn.addActionListener(e -> hangUp());

            btns.add(column(acceptBtn, makeBtnLabel("Accepter", GREEN)));
            btns.add(column(rejectBtn, makeBtnLabel("Refuser",  RED)));

        } else {
            JButton muteBtn   = makeCallBtn("🎤", "Muet",       GRAY_BTN, 62);
            JButton hangUpBtn = makeCallBtn("📵", "Raccrocher", RED,      72);

            muteBtn.addActionListener(e -> {
                boolean muted = "🔇".equals(muteBtn.getText());
                muteBtn.setText(muted ? "🎤" : "🔇");
                muteBtn.setBackground(muted ? GRAY_BTN : RED);
            });
            hangUpBtn.addActionListener(e -> hangUp());

            btns.add(column(muteBtn, makeBtnLabel("Muet", Color.LIGHT_GRAY)));

            if ("video".equals(callType)) {
                JButton camBtn = makeCallBtn("📹", "Caméra", GRAY_BTN, 62);
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
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return l;
    }

    // ── TIMER ──
    private void startTimer() {
        uiTimer = new javax.swing.Timer(1000, e -> {
            if (!connected) return;
            seconds++;
            int m = seconds / 60, s = seconds % 60;
            if (timerLabel != null) {
                timerLabel.setVisible(true);
                timerLabel.setText(String.format("%d:%02d", m, s));
            }
        });
        uiTimer.start();
    }

    // ── API PUBLIQUE ──
    public void onCallAccepted() {
        connected = true;
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) statusLabel.setText("✅  Connecté");
            if (timerLabel  != null) timerLabel.setVisible(true);
        });
    }

    public void onCallRejected() {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) statusLabel.setText("❌  Appel refusé");
            new javax.swing.Timer(2000, e -> hangUp()).start();
        });
    }

    public void onCallEnded() {
        SwingUtilities.invokeLater(this::hangUp);
    }

    // ── FORCE STOP (appelé avant nouveau appel) ──
    public void forceStop() {
        if (uiTimer     != null) uiTimer.stop();
        if (webcamPanel != null) webcamPanel.stop();
        dispose();
    }

    // ── HANG UP ──
    private void hangUp() {
        if (uiTimer     != null) uiTimer.stop();
        if (webcamPanel != null) webcamPanel.stop();
        if (onHangUp    != null) onHangUp.run();
        dispose();
    }

    // ── AVATAR ──
    private JPanel buildBigAvatar(String name, int size) {
        JPanel av = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(37, 211, 102, 35));
                g2.fillOval(-12, -12, size + 24, size + 24);
                g2.setColor(GREEN);
                g2.fillOval(0, 0, size, size);
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

    // ── BOUTON ROND ──
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
    // WEBCAM PANEL — Caméra ouverte une seule fois (singleton)
    // ────────────────────────────────────────────────────────────
    static class WebcamPanel extends JPanel {

        // ✅ Caméra STATIQUE — ouverte une fois pour toute la session
        private static org.opencv.videoio.VideoCapture sharedCamera = null;
        private static Thread sharedThread = null;
        private static volatile BufferedImage sharedFrame = null;
        private static volatile boolean cameraRunning = false;

        /** Ouvrir la caméra partagée — appeler au démarrage de l'app */
        public static void initSharedCamera() {
            if (sharedCamera != null && sharedCamera.isOpened()) return;
            cameraRunning = true;
            sharedThread = new Thread(() -> {
                try {
                    // ✅ Essayer MSMF d'abord (plus rapide), puis DSHOW
                    sharedCamera = new org.opencv.videoio.VideoCapture(0);
                    if (!sharedCamera.isOpened()) {
                        sharedCamera.release();
                        sharedCamera = new org.opencv.videoio.VideoCapture(
                                0, org.opencv.videoio.Videoio.CAP_DSHOW);
                    }
                    if (!sharedCamera.isOpened()) {
                        System.err.println("[Camera] Impossible d'ouvrir la caméra");
                        return;
                    }
                    sharedCamera.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH,  640);
                    sharedCamera.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT, 480);
                    sharedCamera.set(org.opencv.videoio.Videoio.CAP_PROP_FPS, 30);
                    System.out.println("[Camera] Caméra partagée ouverte : "
                            + sharedCamera.getBackendName());

                    // ✅ Lire jusqu'à frame valide AVANT de continuer
                    org.opencv.core.Mat warmup = new org.opencv.core.Mat();
                    int tries = 0;
                    while (tries++ < 100) {
                        if (sharedCamera.read(warmup) && !warmup.empty()) {
                            org.opencv.core.Scalar mean = org.opencv.core.Core.mean(warmup);
                            if (mean.val[0] > 1 || mean.val[1] > 1 || mean.val[2] > 1) {
                                sharedFrame = convertMat(warmup);
                                System.out.println("[Camera] Première frame valide ! tries=" + tries);
                                break;
                            }
                        }
                        Thread.sleep(50);
                    }
                    warmup.release();
                    System.out.println("[Camera] Caméra partagée prête !");

                    org.opencv.core.Mat mat = new org.opencv.core.Mat();
                    while (cameraRunning) {
                        if (sharedCamera.read(mat) && !mat.empty()) {
                            sharedFrame = convertMat(mat);
                        }
                        Thread.sleep(33);
                    }
                    mat.release();
                    sharedCamera.release();
                    sharedCamera = null;
                    System.out.println("[Camera] Caméra partagée libérée.");
                } catch (Exception e) {
                    System.err.println("[Camera] Erreur : " + e.getMessage());
                }
            }, "SharedCamera");
            sharedThread.setDaemon(true);
            sharedThread.start();
        }

        /** Arrêter la caméra partagée — appeler à la fermeture de l'app */
        public static void stopSharedCamera() {
            cameraRunning = false;
        }

        private static BufferedImage convertMat(org.opencv.core.Mat mat) {
            org.opencv.core.Mat rgb = new org.opencv.core.Mat();
            if (mat.channels() == 3)
                org.opencv.imgproc.Imgproc.cvtColor(mat, rgb, org.opencv.imgproc.Imgproc.COLOR_BGR2RGB);
            else
                mat.copyTo(rgb);
            BufferedImage img = new BufferedImage(rgb.width(), rgb.height(), BufferedImage.TYPE_3BYTE_BGR);
            byte[] data = new byte[(int)(rgb.total() * rgb.elemSize())];
            rgb.get(0, 0, data);
            img.getRaster().setDataElements(0, 0, rgb.width(), rgb.height(), data);
            rgb.release();
            return img;
        }

        // ── Instance ──
        private volatile boolean running = false;
        private volatile boolean paused  = false;
        private final int w, h;
        private javax.swing.Timer displayTimer;

        WebcamPanel(int w, int h) {
            this.w = w; this.h = h;
            setPreferredSize(new Dimension(w, h));
            setBackground(new Color(15, 20, 35));
            setOpaque(true);
        }

        public void start() {
            running = true;
            // ✅ Timer qui affiche la frame partagée toutes les 33ms
            displayTimer = new javax.swing.Timer(33, e -> {
                if (running && !paused && sharedFrame != null) {
                    repaint();
                }
            });
            displayTimer.start();
        }

        public void stop() {
            running = false;
            if (displayTimer != null) displayTimer.stop();
        }

        public void togglePause() { paused = !paused; }
        public boolean isPaused() { return paused; }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage f = sharedFrame;
            System.out.println("[Paint] sharedFrame=" + (f != null ? f.getWidth()+"x"+f.getHeight() : "NULL")
                    + " size=" + getWidth() + "x" + getHeight()
                    + " visible=" + isVisible());
            if (f != null) {
                g.drawImage(f, 0, 0, w, h, null);
            } else {
                g.setColor(new Color(20, 30, 50));
                g.fillRect(0, 0, w, h);
                g.setColor(new Color(100, 130, 180));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g.drawString("Activation camera...", 10, h / 2);
            }
        }
    }
}