package authUi;

import auth.AuthService;
import client.NetworkClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class PhoneView {

    private final AuthService   auth;
    private final NetworkClient network;

    private JFrame     frame;
    private JTextField phoneField;
    private JButton    btnSend;
    private JLabel     statusLabel;

    public PhoneView(AuthService auth, NetworkClient network) {
        this.auth    = auth;
        this.network = network;
    }

    /** Compatibilité ancien constructeur. */
    public PhoneView(AuthService auth) {
        this(auth, auth.getNetwork());
    }

    public void show() {
        frame = new JFrame("WhatsApp — Connexion");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 280);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBackground(new Color(18, 18, 18));
        main.setBorder(new EmptyBorder(40, 50, 40, 50));

        JLabel title = new JLabel("WhatsApp");
        title.setForeground(new Color(37, 211, 102));
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Entrez votre numéro de téléphone");
        sub.setForeground(new Color(150, 150, 150));
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        phoneField = new JTextField();
        phoneField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        phoneField.setBackground(new Color(30, 30, 30));
        phoneField.setForeground(Color.WHITE);
        phoneField.setCaretColor(Color.WHITE);
        phoneField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(37, 211, 102)),
                new EmptyBorder(5, 10, 5, 10)));
        phoneField.setFont(new Font("Segoe UI", Font.PLAIN, 15));

        btnSend = new JButton("Envoyer le code");
        btnSend.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnSend.setBackground(new Color(37, 211, 102));
        btnSend.setForeground(Color.BLACK);
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnSend.setBorderPainted(false);
        btnSend.setFocusPainted(false);
        btnSend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSend.addActionListener(e -> sendCode());
        phoneField.addActionListener(e -> sendCode());

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        main.add(title);
        main.add(Box.createVerticalStrut(6));
        main.add(sub);
        main.add(Box.createVerticalStrut(25));
        main.add(phoneField);
        main.add(Box.createVerticalStrut(15));
        main.add(btnSend);
        main.add(Box.createVerticalStrut(10));
        main.add(statusLabel);

        frame.setContentPane(main);
        frame.setVisible(true);
        phoneField.requestFocusInWindow();
    }

    private void sendCode() {
        String phone = phoneField.getText().trim();
        if (phone.isEmpty()) {
            statusLabel.setText("Veuillez entrer un numéro.");
            return;
        }
        btnSend.setEnabled(false);
        btnSend.setText("Envoi...");
        statusLabel.setText(" ");

        auth.requestCode(phone,
                () -> SwingUtilities.invokeLater(() -> {
                    frame.dispose();
                    new CodeView(phone, auth, network).show();
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Erreur : impossible d'envoyer le code.");
                    btnSend.setEnabled(true);
                    btnSend.setText("Envoyer le code");
                }));
    }
}