package authUi;

import auth.AuthService;
import auth.SessionManager;
import client.NetworkClient;
import client.SocketManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.charset.StandardCharsets;

public class ChatView {

    private final int           userId;
    private final String        phone;
    private final String        username;
    private final NetworkClient network;

    private JFrame frame;
    private JPanel convList;
    private JPanel mainPanel;  // panneau droit (conversation active)

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
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        // ── SIDEBAR ──────────────────────────────────────────────
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(22, 22, 22));
        sidebar.setPreferredSize(new Dimension(300, 0));

        // Header
        JPanel sideHeader = new JPanel(new BorderLayout());
        sideHeader.setBackground(new Color(30, 30, 30));
        sideHeader.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel userInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        userInfo.setOpaque(false);

        JLabel avatar = new JLabel(getInitial(username));
        avatar.setFont(new Font("Segoe UI", Font.BOLD, 18));
        avatar.setForeground(Color.WHITE);
        avatar.setBackground(new Color(37, 211, 102));
        avatar.setOpaque(true);
        avatar.setPreferredSize(new Dimension(42, 42));
        avatar.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel namePanel = new JPanel();
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
        namePanel.setOpaque(false);

        JLabel nameLabel  = new JLabel(username);
        nameLabel.setForeground(Color.WHITE);
        JLabel phoneLabel = new JLabel(phone);
        phoneLabel.setForeground(Color.GRAY);

        namePanel.add(nameLabel);
        namePanel.add(phoneLabel);
        userInfo.add(avatar);
        userInfo.add(namePanel);

        // Bouton "+"
        JButton btnAdd = new JButton("+");
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 18));
        btnAdd.setForeground(Color.WHITE);
        btnAdd.setBackground(new Color(30, 30, 30));
        btnAdd.setBorderPainted(false);
        btnAdd.setFocusPainted(false);
        btnAdd.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAdd.addActionListener(e -> addContact());

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topButtons.setOpaque(false);
        topButtons.add(btnAdd);

        sideHeader.add(userInfo,   BorderLayout.WEST);
        sideHeader.add(topButtons, BorderLayout.EAST);

        // Liste contacts
        convList = new JPanel();
        convList.setLayout(new BoxLayout(convList, BoxLayout.Y_AXIS));
        convList.setBackground(new Color(22, 22, 22));

        JScrollPane scrollConv = new JScrollPane(convList);
        scrollConv.setBorder(null);

        // Déconnexion
        JButton btnLogout = new JButton("Se déconnecter");
        btnLogout.setForeground(Color.RED);
        btnLogout.setBorderPainted(false);
        btnLogout.setContentAreaFilled(false);
        btnLogout.addActionListener(e -> logout());

        sidebar.add(sideHeader, BorderLayout.NORTH);
        sidebar.add(scrollConv, BorderLayout.CENTER);
        sidebar.add(btnLogout,  BorderLayout.SOUTH);

        // ── MAIN AREA ─────────────────────────────────────────────
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(12, 12, 12));

        JLabel welcome = new JLabel(
                "Bienvenue " + username + "  |  " + phone,
                SwingConstants.CENTER);
        welcome.setForeground(Color.WHITE);
        welcome.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        mainPanel.add(welcome, BorderLayout.CENTER);

        frame.add(sidebar,   BorderLayout.WEST);
        frame.add(mainPanel, BorderLayout.CENTER);
        frame.setVisible(true);

        // ── CHARGER CONTACTS + DÉMARRER L'ÉCOUTE ─────────────────
        ContactView contactView = new ContactView(network, convList);
        contactView.loadContacts();

        // Démarrer l'écoute des messages entrants
        startBinaryListener(contactView);
    }

    // ─── Écoute binaire ──────────────────────────────────────────
    private void startBinaryListener(ContactView contactView) {
        SocketManager.getInstance().startListening(
                new SocketManager.MessageListener() {

                    @Override
                    public void onMessage(String type, String sender,
                                          String filename, byte[] data) {
                        switch (type) {

                            case "CONTACT_SIGNAL": {
                                String payload = new String(data, StandardCharsets.UTF_8);
                                SwingUtilities.invokeLater(() ->
                                        contactView.updateContacts(payload));
                                break;
                            }

                            case "text": {
                                String msg = new String(data, StandardCharsets.UTF_8);
                                SwingUtilities.invokeLater(() ->
                                        showIncomingMessage(sender, msg));
                                break;
                            }

                            case "CALL_SIGNAL": {
                                String payload = new String(data, StandardCharsets.UTF_8);
                                SwingUtilities.invokeLater(() ->
                                        handleCallSignal(payload, sender));
                                break;
                            }

                            default:
                                System.out.println("[ChatView] Type reçu : " + type
                                        + " de " + sender);
                        }
                    }

                    @Override
                    public void onDisconnect() {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(frame,
                                    "Connexion perdue.",
                                    "Déconnecté", JOptionPane.WARNING_MESSAGE);
                            logout();
                        });
                    }
                });
    }

    private void showIncomingMessage(String sender, String msg) {
        // À remplacer par l'affichage dans la conversation active
        JOptionPane.showMessageDialog(frame,
                sender + " : " + msg, "Nouveau message",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleCallSignal(String payload, String sender) {
        if (payload.startsWith("CALL_REQUEST:")) {
            int choice = JOptionPane.showConfirmDialog(frame,
                    sender + " vous appelle. Accepter ?",
                    "Appel entrant", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                SocketManager.getInstance().sendBinary(
                        "CALL_SIGNAL", sender, "",
                        ("CALL_ACCEPT:" + sender)
                                .getBytes(StandardCharsets.UTF_8));
            } else {
                SocketManager.getInstance().sendBinary(
                        "CALL_SIGNAL", sender, "",
                        ("CALL_REJECT:" + sender)
                                .getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // ─── Ajout contact ───────────────────────────────────────────
    // Dans ChatView.java, remplacez la méthode addContact par celle-ci :
    private void addContact() {
        String phoneInput = JOptionPane.showInputDialog(frame, "Entrer le numéro du contact :");
        if (phoneInput == null || phoneInput.trim().isEmpty()) return;

        String nicknameInput = JOptionPane.showInputDialog(frame,
                "Entrer un surnom pour ce contact (optionnel, laisser vide pour utiliser son nom) :");

        String payload;
        if (nicknameInput != null && !nicknameInput.trim().isEmpty()) {
            payload = "ADD:" + phoneInput.trim() + ":" + nicknameInput.trim();
        } else {
            payload = "ADD:" + phoneInput.trim();
        }

        SocketManager.getInstance().sendBinary(
                "CONTACT_SIGNAL",
                "SERVER",
                "",
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }
    // ─── Déconnexion ─────────────────────────────────────────────
    private void logout() {
        SessionManager.clearSession();
        SocketManager.reset();
        frame.dispose();
        NetworkClient freshNetwork = new NetworkClient("localhost", 5000);
        new PhoneView(new AuthService(freshNetwork), freshNetwork).show();
    }

    private String getInitial(String name) {
        return name != null && !name.isEmpty()
                ? String.valueOf(name.charAt(0)).toUpperCase()
                : "?";
    }
}