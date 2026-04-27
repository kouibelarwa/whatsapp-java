package authUi;

import auth.AuthService;
import auth.SessionManager;
import client.NetworkClient;
import client.SocketManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * ChatView — Fenêtre principale de l'application.
 * Sidebar contacts + panneau droit ConversationView.
 * Gère les appels entrants via CallView.
 */
public class ChatView {

    private final int           userId;
    private final String        phone;
    private final String        username;
    private final NetworkClient network;

    private JFrame frame;
    private JPanel convList;
    private JPanel mainPanel;

    private ConversationView     activeConversation;
    private String               activeContactPhone;

    private final Map<String, ConversationView> conversationCache = new HashMap<>();

    // Couleurs
    private static final Color BG_DARK   = new Color(12, 12, 12);
    private static final Color BG_SIDE   = new Color(22, 22, 22);
    private static final Color BG_HEADER = new Color(30, 30, 30);
    private static final Color GREEN     = new Color(37, 211, 102);

    public ChatView(int userId, String phone,
                    String username, NetworkClient network) {
        this.userId   = userId;
        this.phone    = phone;
        this.username = username != null ? username : "Utilisateur";
        this.network  = network;
    }

    public void show() {
        frame = new JFrame("WhatsApp — " + username);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(960, 620);
        frame.setMinimumSize(new Dimension(700, 450));
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        frame.add(buildSidebar(),  BorderLayout.WEST);
        frame.add(mainPanel = buildMainPanel(), BorderLayout.CENTER);
        frame.setVisible(true);

        ContactView contactView = new ContactView(network, convList);
        contactView.setConversationOpenCallback(this::openConversation);

        // ✅ LISTENER EN PREMIER
        startBinaryListener(contactView);

        // ✅ ATTENDRE 300ms PUIS charger
        new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            contactView.loadContacts();
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // SIDEBAR
    // ─────────────────────────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG_SIDE);
        sidebar.setPreferredSize(new Dimension(300, 0));

        // Header
        JPanel sideHeader = new JPanel(new BorderLayout());
        sideHeader.setBackground(BG_HEADER);
        sideHeader.setBorder(new EmptyBorder(12, 15, 12, 15));

        JPanel userInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        userInfo.setOpaque(false);

        JPanel myAvatar = buildAvatar(username, 44);

        JPanel namePanel = new JPanel();
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
        namePanel.setOpaque(false);

        JLabel nameLabel  = new JLabel(username);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JLabel phoneLabel = new JLabel(phone);
        phoneLabel.setForeground(Color.GRAY);
        phoneLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        namePanel.add(nameLabel);
        namePanel.add(phoneLabel);
        userInfo.add(myAvatar);
        userInfo.add(namePanel);

        JButton btnAdd = new JButton("+");
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 22));
        btnAdd.setForeground(GREEN);
        btnAdd.setBackground(BG_HEADER);
        btnAdd.setBorderPainted(false);
        btnAdd.setFocusPainted(false);
        btnAdd.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAdd.setToolTipText("Ajouter un contact");
        btnAdd.addActionListener(e -> addContact());

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        topButtons.setOpaque(false);
        topButtons.add(btnAdd);

        sideHeader.add(userInfo,   BorderLayout.WEST);
        sideHeader.add(topButtons, BorderLayout.EAST);

        // Barre de recherche
        JTextField searchField = new JTextField();
        searchField.setBackground(new Color(40, 40, 40));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(50, 50, 50)),
                new EmptyBorder(7, 12, 7, 12)));
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel sideTop = new JPanel(new BorderLayout());
        sideTop.add(sideHeader,  BorderLayout.NORTH);
        sideTop.add(searchField, BorderLayout.SOUTH);

        // Liste contacts
        convList = new JPanel();
        convList.setLayout(new BoxLayout(convList, BoxLayout.Y_AXIS));
        convList.setBackground(BG_SIDE);

        JScrollPane scrollConv = new JScrollPane(convList);
        scrollConv.setBorder(null);
        scrollConv.setBackground(BG_SIDE);
        scrollConv.getViewport().setBackground(BG_SIDE);
        scrollConv.getVerticalScrollBar().setUnitIncrement(12);

        // Bouton déconnexion
        JButton btnLogout = new JButton("⏻  Se déconnecter");
        btnLogout.setForeground(new Color(200, 80, 80));
        btnLogout.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLogout.setBorderPainted(false);
        btnLogout.setContentAreaFilled(false);
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.setBorder(new EmptyBorder(10, 15, 10, 15));
        btnLogout.addActionListener(e -> logout());

        sidebar.add(sideTop,    BorderLayout.NORTH);
        sidebar.add(scrollConv, BorderLayout.CENTER);
        sidebar.add(btnLogout,  BorderLayout.SOUTH);

        return sidebar;
    }

    // ─────────────────────────────────────────────────────────────
    // PANNEAU PRINCIPAL / ÉCRAN BIENVENUE
    // ─────────────────────────────────────────────────────────────

    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        showWelcomeScreen(panel);
        return panel;
    }

    private void showWelcomeScreen(JPanel panel) {
        panel.removeAll();

        JPanel welcome = new JPanel(new GridBagLayout());
        welcome.setBackground(BG_DARK);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);

        JLabel icon = new JLabel("💬");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 64));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("WhatsApp");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Cliquez sur un contact pour commencer");
        sub.setForeground(Color.GRAY);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel userLbl = new JLabel("Connecté : " + username + "  |  " + phone);
        userLbl.setForeground(new Color(90, 90, 90));
        userLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        userLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(icon);
        box.add(Box.createVerticalStrut(12));
        box.add(title);
        box.add(Box.createVerticalStrut(8));
        box.add(sub);
        box.add(Box.createVerticalStrut(12));
        box.add(userLbl);

        welcome.add(box);
        panel.add(welcome, BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();
    }

    // ─────────────────────────────────────────────────────────────
    // OUVRIR CONVERSATION
    // ─────────────────────────────────────────────────────────────

    private void openConversation(String contactPhone, String contactName, String contactStatus) {
        activeContactPhone = contactPhone;
        ConversationView conv = conversationCache.computeIfAbsent(contactPhone,
                k -> new ConversationView(userId, phone, contactPhone, contactName, contactStatus));
        activeConversation = conv;

        mainPanel.removeAll();
        mainPanel.add(conv, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // ─────────────────────────────────────────────────────────────
    // ÉCOUTE BINAIRE
    // ─────────────────────────────────────────────────────────────

    private void startBinaryListener(ContactView contactView) {
        SocketManager.getInstance().startListening(new SocketManager.MessageListener() {

            @Override
            public void onMessage(String type, String sender,
                                  String filename, byte[] data) {
                switch (type) {

                    case "CONTACT_SIGNAL": {
                        String payload = new String(data, StandardCharsets.UTF_8);
                        System.out.println("[Debug] CONTACT_SIGNAL reçu : " + payload); // ← ajouter ici
                        SwingUtilities.invokeLater(() ->
                                contactView.updateContacts(payload));
                        break;
                    }

                    case "text":
                    case "audio":
                    case "video":
                    case "image":
                    case "file": {
                        System.out.println("[Debug] recu type=" + type
                                + " sender='" + sender + "'"
                                + " activeContact='" + activeContactPhone + "'"
                                + " dataSize=" + (data != null ? data.length : 0));
                        SwingUtilities.invokeLater(() -> {
                            if (sender != null && sender.equals(activeContactPhone)
                                    && activeConversation != null) {
                                activeConversation.receiveMessage(type, filename, data);
                            } else {
                                String msgText = "text".equals(type)
                                        ? new String(data, StandardCharsets.UTF_8)
                                        : "📎 " + (filename != null ? filename : type);
                                showNotification(sender, msgText);
                            }
                        });
                        break;
                    }

                    case "CALL_SIGNAL": {
                        String payload = new String(data, StandardCharsets.UTF_8);
                        SwingUtilities.invokeLater(() ->
                                handleCallSignal(payload, sender));
                        break;
                    }

                    default:
                        System.out.println("[ChatView] Type : " + type
                                + " de " + sender);
                }
            }

            @Override
            public void onDisconnect() {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame,
                            "Connexion perdue au serveur.",
                            "Déconnecté", JOptionPane.WARNING_MESSAGE);
                    logout();
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // GESTION SIGNAUX D'APPEL
    // ─────────────────────────────────────────────────────────────

    // ── REMPLACE UNIQUEMENT la méthode handleCallSignal() dans ChatView.java ──
// Le reste de ChatView.java reste IDENTIQUE

    private CallView activeCallView = null; // ✅ AJOUTER en champ de classe

    private void handleCallSignal(String payload, String sender) {

        // ── Appel entrant ──────────────────────────────────────────
        if (payload.startsWith("CALL_INCOMING:")) {
            // Format : CALL_INCOMING:callerPhone:callId
            String[] parts = payload.split(":", 3);
            String caller  = parts.length >= 2 ? parts[1] : (sender != null ? sender : "Inconnu");
            String callIdStr = parts.length >= 3 ? parts[2] : "-1";
            int callId = -1;
            try { callId = Integer.parseInt(callIdStr); } catch (Exception ignored) {}

            final int finalCallId = callId;
            final String finalCaller = caller;

            // ✅ FIX : on n'envoie PAS CALL_ACCEPT automatiquement !
            // On ouvre CallView et on attend que l'utilisateur clique "Accepter"
            activeCallView = new CallView(
                    frame, finalCaller, finalCaller, "audio", true,
                    // Raccrocher → envoyer CALL_REJECT
                    () -> SocketManager.getInstance().sendBinary(
                            "CALL_SIGNAL", finalCaller, "",
                            ("CALL_REJECT:" + finalCaller + ":" + finalCallId)
                                    .getBytes(StandardCharsets.UTF_8))
            );

            // ✅ Quand l'utilisateur clique "Accepter" dans CallView,
            //    CallView appelle onCallAccepted() → on envoie CALL_ACCEPT au serveur
            // Pour brancher ça, on utilise le callback acceptCallback
            activeCallView.setAcceptCallback(() ->
                    SocketManager.getInstance().sendBinary(
                            "CALL_SIGNAL", finalCaller, "",
                            ("CALL_ACCEPT:" + finalCaller + ":" + finalCallId)
                                    .getBytes(StandardCharsets.UTF_8))
            );

            activeCallView.setVisible(true);
            return;
        }

        // ── Appel accepté ──────────────────────────────────────────
        if (payload.startsWith("CALL_ACCEPTED:")) {
            if (activeCallView != null) {
                activeCallView.onCallAccepted();
            }
            showToast("✅ Appel accepté !");
            return;
        }

        // ── Appel refusé ───────────────────────────────────────────
        if (payload.startsWith("CALL_REJECTED:")) {
            if (activeCallView != null) {
                activeCallView.onCallRejected();
                activeCallView = null;
            }
            showToast("❌ Appel refusé.");
            return;
        }

        // ── Appel terminé ──────────────────────────────────────────
        if (payload.startsWith("CALL_ENDED:")) {
            if (activeCallView != null) {
                activeCallView.onCallEnded();
                activeCallView = null;
            }
            showToast("📵 Appel terminé.");
            return;
        }

        // ── Appel manqué ───────────────────────────────────────────
        if (payload.startsWith("CALL_MISSED:")) {
            if (activeCallView != null) {
                activeCallView.onCallRejected();
                activeCallView = null;
            }
            showToast("📵 Appel manqué.");
        }
    }
    // ─────────────────────────────────────────────────────────────
    // NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────

    private void showNotification(String sender, String msg) {
        JDialog notif = new JDialog(frame);
        notif.setUndecorated(true);
        notif.setSize(280, 72);
        notif.setAlwaysOnTop(true);
        notif.getContentPane().setBackground(new Color(30, 30, 30));
        notif.setLayout(new BorderLayout());

        // Bande verte gauche
        JPanel accent = new JPanel();
        accent.setBackground(GREEN);
        accent.setPreferredSize(new Dimension(5, 0));
        notif.add(accent, BorderLayout.WEST);

        JLabel lbl = new JLabel(
                "<html><b style='color:#25d366'>" + (sender != null ? sender : "?")
                        + "</b><br><span style='color:#ccc'>"
                        + truncate(msg, 38) + "</span></html>");
        lbl.setBorder(new EmptyBorder(10, 12, 10, 12));
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        notif.add(lbl, BorderLayout.CENTER);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        notif.setLocation(screen.width - 300, screen.height - 110);
        notif.setVisible(true);

        new Timer(4000, e -> notif.dispose()).start();

        lbl.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                notif.dispose();
                openConversation(sender, sender, "ONLINE");
            }
        });
    }

    /** Toast discret non-bloquant (3s). */
    private void showToast(String message) {
        JDialog toast = new JDialog(frame);
        toast.setUndecorated(true);
        toast.setSize(220, 46);
        toast.setAlwaysOnTop(true);
        toast.getContentPane().setBackground(new Color(40, 40, 40));
        toast.setLayout(new GridBagLayout());

        JLabel lbl = new JLabel(message);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        toast.add(lbl);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        toast.setLocation(screen.width - 240, screen.height - 160);
        toast.setVisible(true);
        new Timer(3000, e -> toast.dispose()).start();
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s != null ? s : "";
    }

    // ─────────────────────────────────────────────────────────────
    // AJOUT CONTACT
    // ─────────────────────────────────────────────────────────────

    private void addContact() {
        String phoneInput = JOptionPane.showInputDialog(frame,
                "Entrer le numéro du contact :",
                "Ajouter un contact", JOptionPane.PLAIN_MESSAGE);
        if (phoneInput == null || phoneInput.trim().isEmpty()) return;

        String nicknameInput = JOptionPane.showInputDialog(frame,
                "Entrer un surnom (optionnel) :",
                "Surnom", JOptionPane.PLAIN_MESSAGE);

        String payload = (nicknameInput != null && !nicknameInput.trim().isEmpty())
                ? "ADD:" + phoneInput.trim() + ":" + nicknameInput.trim()
                : "ADD:" + phoneInput.trim();

        SocketManager.getInstance().sendBinary(
                "CONTACT_SIGNAL", "", "",
                payload.getBytes(StandardCharsets.UTF_8));

        // ✅ NOUVEAU : attendre la réponse puis recharger si pas reçue
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            SocketManager.getInstance().sendBinary(
                    "CONTACT_SIGNAL", "", "",
                    "GET_CONTACTS".getBytes(StandardCharsets.UTF_8));
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // DÉCONNEXION
    // ─────────────────────────────────────────────────────────────

    private void logout() {
        SessionManager.clearSession();
        SocketManager.reset();
        frame.dispose();
        NetworkClient freshNetwork = new NetworkClient("localhost", 5000);
        new PhoneView(new AuthService(freshNetwork), freshNetwork).show();
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITAIRES
    // ─────────────────────────────────────────────────────────────

    private JPanel buildAvatar(String name, int size) {
        JPanel av = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(GREEN);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, size / 3));
                FontMetrics fm = g2.getFontMetrics();
                String init = name != null && !name.isEmpty()
                        ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
                g2.drawString(init,
                        (getWidth() - fm.stringWidth(init)) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        av.setOpaque(false);
        av.setPreferredSize(new Dimension(size, size));
        return av;
    }
}