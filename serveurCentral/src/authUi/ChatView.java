package authUi;

import auth.SessionManager;
import client.NetworkClient;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * ChatView — Écran d'accueil principal (après authentification)
 *
 * Accessible de DEUX façons :
 *   1. Après AUTH_OK (nouveau ou reconnexion via OTP)
 *   2. Directement depuis MainApp si SESSION_OK (reconnexion automatique)
 *
 * L'utilisateur peut ici :
 *   - Voir ses conversations
 *   - Changer son numéro de téléphone → retour PhoneView
 *   - Se déconnecter → session effacée → retour PhoneView
 */
public class ChatView {

    private final int           userId;
    private final String        phone;
    private final String        username;
    private final NetworkClient network;

    private JFrame frame;

    public ChatView(int userId, String phone, String username, NetworkClient network) {
        this.userId   = userId;
        this.phone    = phone;
        this.username = username != null ? username : "Utilisateur";
        this.network  = network;
    }

    public void show() {
        frame = new JFrame("WhatsApp");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        // ── Sidebar gauche ─────────────────────────────────────────────
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(22, 22, 22));
        sidebar.setPreferredSize(new Dimension(300, 0));

        // Header sidebar
        JPanel sideHeader = new JPanel(new BorderLayout());
        sideHeader.setBackground(new Color(30, 30, 30));
        sideHeader.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Avatar + infos utilisateur
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

        JLabel nameLabel = new JLabel(username);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameLabel.setForeground(Color.WHITE);

        JLabel phoneLabel = new JLabel(phone);
        phoneLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        phoneLabel.setForeground(new Color(150, 150, 150));

        namePanel.add(nameLabel);
        namePanel.add(phoneLabel);

        userInfo.add(avatar);
        userInfo.add(namePanel);

        // Bouton changer numéro
        JButton btnChangePhone = new JButton("✏");
        btnChangePhone.setToolTipText("Changer de numéro");
        btnChangePhone.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        btnChangePhone.setForeground(new Color(150, 150, 150));
        btnChangePhone.setBackground(new Color(30, 30, 30));
        btnChangePhone.setBorderPainted(false);
        btnChangePhone.setFocusPainted(false);
        btnChangePhone.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnChangePhone.addActionListener(e -> changePhone());

        sideHeader.add(userInfo,      BorderLayout.WEST);
        sideHeader.add(btnChangePhone, BorderLayout.EAST);

        // Liste des conversations (placeholder)
        JPanel convList = new JPanel();
        convList.setLayout(new BoxLayout(convList, BoxLayout.Y_AXIS));
        convList.setBackground(new Color(22, 22, 22));

        convList.add(makeConvItem("Alice",   "Bonjour !",        "10:30"));
        convList.add(makeConvItem("Bob",     "Tu es disponible ?","09:15"));
        convList.add(makeConvItem("Groupe",  "Photo envoyée",    "Hier"));

        JScrollPane scrollConv = new JScrollPane(convList);
        scrollConv.setBorder(null);
        scrollConv.setBackground(new Color(22, 22, 22));

        // Bouton déconnexion
        JButton btnLogout = new JButton("Se déconnecter");
        btnLogout.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLogout.setForeground(new Color(255, 80, 80));
        btnLogout.setBackground(new Color(22, 22, 22));
        btnLogout.setBorderPainted(false);
        btnLogout.setFocusPainted(false);
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.setBorder(new EmptyBorder(10, 15, 10, 15));
        btnLogout.addActionListener(e -> logout());

        sidebar.add(sideHeader,  BorderLayout.NORTH);
        sidebar.add(scrollConv,  BorderLayout.CENTER);
        sidebar.add(btnLogout,   BorderLayout.SOUTH);

        // ── Zone principale ────────────────────────────────────────────
        JPanel mainArea = new JPanel(new BorderLayout());
        mainArea.setBackground(new Color(12, 12, 12));

        JLabel welcome = new JLabel(
                "<html><center>💬<br><br>Bonjour <b>" + username + "</b> !<br>" +
                        "<span style='color:#888;font-size:12px'>Sélectionnez une conversation</span></center></html>",
                SwingConstants.CENTER
        );
        welcome.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        welcome.setForeground(new Color(200, 200, 200));
        mainArea.add(welcome, BorderLayout.CENTER);

        // ── Assemblage ─────────────────────────────────────────────────
        frame.add(sidebar,  BorderLayout.WEST);
        frame.add(mainArea, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    // ── Changer de numéro → retour PhoneView ─────────────────────────
    private void changePhone() {
        int confirm = JOptionPane.showConfirmDialog(
                frame,
                "Voulez-vous changer votre numéro de téléphone ?\n" +
                        "Vous devrez vous re-authentifier.",
                "Changer de numéro",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            SessionManager.clearSession();
            frame.dispose();
            // Retour à PhoneView avec un nouvel AuthService
            client.NetworkClient net = new client.NetworkClient("localhost", 5000);
            auth.AuthService authSvc = new auth.AuthService(net);
            new PhoneView(authSvc).show();
        }
    }

    // ── Se déconnecter ────────────────────────────────────────────────
    private void logout() {
        SessionManager.clearSession();
        frame.dispose();
        client.NetworkClient net = new client.NetworkClient("localhost", 5000);
        auth.AuthService authSvc = new auth.AuthService(net);
        new PhoneView(authSvc).show();
    }

    // ── Helpers UI ────────────────────────────────────────────────────
    private String getInitial(String name) {
        return name != null && !name.isEmpty()
                ? String.valueOf(name.charAt(0)).toUpperCase()
                : "?";
    }

    private JPanel makeConvItem(String name, String lastMsg, String time) {
        JPanel item = new JPanel(new BorderLayout());
        item.setBackground(new Color(22, 22, 22));
        item.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(35, 35, 35)),
                new EmptyBorder(12, 15, 12, 15)
        ));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel nameL = new JLabel(name);
        nameL.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nameL.setForeground(Color.WHITE);

        JLabel msgL = new JLabel(lastMsg);
        msgL.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        msgL.setForeground(new Color(130, 130, 130));

        JLabel timeL = new JLabel(time);
        timeL.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        timeL.setForeground(new Color(37, 211, 102));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.add(nameL);
        left.add(msgL);

        item.add(left,  BorderLayout.WEST);
        item.add(timeL, BorderLayout.EAST);

        item.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { item.setBackground(new Color(35,35,35)); }
            public void mouseExited(MouseEvent e)  { item.setBackground(new Color(22,22,22)); }
        });

        return item;
    }
}