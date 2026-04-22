package authUi;

import auth.AuthService;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * CodeView — Saisie du code OTP reçu par SMS
 *
 * Si l'utilisateur est NOUVEAU → affiche aussi un champ username
 * Si l'utilisateur EXISTE déjà → pas de champ username (récupéré du serveur)
 *
 * Après AUTH_OK → ChatView
 */
public class CodeView {

    private final AuthService auth;
    private final String      phone;
    private JFrame frame;

    public CodeView(AuthService auth, String phone) {
        this.auth  = auth;
        this.phone = phone;
    }

    public void show() {
        frame = new JFrame("WhatsApp — Vérification");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 600);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBackground(new Color(18, 18, 18));
        main.setBorder(new EmptyBorder(40, 50, 40, 50));

        // Titre
        JLabel title = new JLabel("Vérification");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(new Color(37, 211, 102));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Info numéro
        JLabel phoneInfo = new JLabel("Code envoyé au : " + phone);
        phoneInfo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        phoneInfo.setForeground(new Color(150, 150, 150));
        phoneInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Champ code OTP
        JLabel codeLabel = makeLabel("Code SMS reçu :");
        JTextField codeField = makeField("000000");

        // Champ username (visible seulement si nouveau)
        JLabel userLabel   = makeLabel("Votre nom d'utilisateur :");
        JTextField userField = makeField("ex: Jean");

        // Erreur
        JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        errorLabel.setForeground(new Color(255, 80, 80));
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Bouton valider
        JButton btnVerify = createButton("Valider");

        // Bouton changer de numéro
        JButton btnBack = createSecondaryButton("← Changer de numéro");
        btnBack.addActionListener(e -> {
            frame.dispose();
            new PhoneView(auth).show();
        });

        // Action valider
        ActionListener action = e -> {
            String code     = codeField.getText().trim();
            String username = userField.getText().trim();

            if (code.isEmpty() || !code.matches("\\d{6}")) {
                errorLabel.setText("Le code doit contenir 6 chiffres.");
                return;
            }
            if (userField.isVisible() && username.isEmpty()) {
                errorLabel.setText("Veuillez entrer un nom d'utilisateur.");
                return;
            }

            btnVerify.setEnabled(false);
            btnVerify.setText("Vérification...");
            errorLabel.setText(" ");

            auth.verifyCode(phone, code, username,
                    new AuthService.AuthCallback() {

                        @Override
                        public void onSuccess(int userId, String retPhone,
                                              String retUsername, boolean isNewUser) {
                            SwingUtilities.invokeLater(() -> {
                                frame.dispose();

                                if (isNewUser && (retUsername == null || retUsername.isBlank())) {
                                    // Très rare : username non reçu → afficher champ
                                    userLabel.setVisible(true);
                                    userField.setVisible(true);
                                    frame.revalidate();
                                } else {
                                    // ✅ Aller au ChatView
                                    new ChatView(userId, retPhone, retUsername, null).show();
                                }
                            });
                        }

                        @Override
                        public void onError(String reason) {
                            SwingUtilities.invokeLater(() -> {
                                btnVerify.setEnabled(true);
                                btnVerify.setText("Valider");
                                if ("WRONG_CODE".equals(reason)) {
                                    errorLabel.setText("Code incorrect. Réessayez.");
                                } else {
                                    errorLabel.setText("Erreur : " + reason);
                                }
                            });
                        }
                    });
        };

        btnVerify.addActionListener(action);
        codeField.addActionListener(action);

        // Assemblage
        main.add(title);
        main.add(Box.createVerticalStrut(8));
        main.add(phoneInfo);
        main.add(Box.createVerticalStrut(30));
        main.add(codeLabel);
        main.add(Box.createVerticalStrut(8));
        main.add(codeField);
        main.add(Box.createVerticalStrut(20));
        main.add(userLabel);
        main.add(Box.createVerticalStrut(8));
        main.add(userField);
        main.add(Box.createVerticalStrut(10));
        main.add(errorLabel);
        main.add(Box.createVerticalStrut(20));
        main.add(btnVerify);
        main.add(Box.createVerticalStrut(10));
        main.add(btnBack);

        frame.add(main);
        frame.setVisible(true);
    }

    // ── Helpers UI ────────────────────────────────────────────────────
    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(new Color(200, 200, 200));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JTextField makeField(String placeholder) {
        JTextField f = new JTextField();
        f.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        f.setForeground(Color.WHITE);
        f.setBackground(new Color(35, 35, 35));
        f.setCaretColor(new Color(37, 211, 102));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(37, 211, 102), 2),
                new EmptyBorder(10, 15, 10, 15)
        ));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        f.setHorizontalAlignment(JTextField.CENTER);
        return f;
    }

    private JButton createButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btn.setBackground(new Color(37, 211, 102));
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        return btn;
    }

    private JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setBackground(new Color(35, 35, 35));
        btn.setForeground(new Color(150, 150, 150));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        return btn;
    }
}