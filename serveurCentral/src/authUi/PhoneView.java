package authUi;

import auth.AuthService;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * PhoneView — Écran de saisie du numéro de téléphone
 *
 * L'utilisateur entre son numéro → AUTH_REQUEST envoyé au serveur
 * → SMS_SENT → passer à CodeView
 */
public class PhoneView {

    private final AuthService auth;
    private JFrame frame;

    public PhoneView(AuthService auth) {
        this.auth = auth;
    }

    public void show() {
        frame = new JFrame("WhatsApp — Authentification");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 520);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBackground(new Color(18, 18, 18));
        main.setBorder(new EmptyBorder(50, 50, 50, 50));

        // Logo
        JLabel logo = new JLabel("💬", SwingConstants.CENTER);
        logo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 64));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Titre
        JLabel title = new JLabel("WhatsApp");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(new Color(37, 211, 102));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Sous-titre
        JLabel subtitle = new JLabel("Entrez votre numéro de téléphone");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(new Color(150, 150, 150));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Champ numéro
        JTextField phoneField = new JTextField();
        phoneField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        phoneField.setForeground(Color.WHITE);
        phoneField.setBackground(new Color(35, 35, 35));
        phoneField.setCaretColor(new Color(37, 211, 102));
        phoneField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(37, 211, 102), 2),
                new EmptyBorder(10, 15, 10, 15)
        ));
        phoneField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        phoneField.setHorizontalAlignment(JTextField.CENTER);

        // Info
        JLabel info = new JLabel("ex: 0699000001");
        info.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        info.setForeground(new Color(100, 100, 100));
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Message d'erreur
        JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        errorLabel.setForeground(new Color(255, 80, 80));
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Bouton
        JButton btnSend = createButton("Envoyer le code SMS");

        // Action bouton
        ActionListener action = e -> {
            String phone = phoneField.getText().trim();
            if (phone.isEmpty()) {
                errorLabel.setText("Veuillez entrer votre numéro.");
                return;
            }
            btnSend.setEnabled(false);
            btnSend.setText("Envoi en cours...");
            errorLabel.setText(" ");

            auth.requestCode(phone,
                    // Succès → SMS_SENT
                    () -> SwingUtilities.invokeLater(() -> {
                        frame.dispose();
                        new CodeView(auth, phone).show();
                    }),
                    // Erreur
                    () -> SwingUtilities.invokeLater(() -> {
                        errorLabel.setText("Erreur serveur. Réessayez.");
                        btnSend.setEnabled(true);
                        btnSend.setText("Envoyer le code SMS");
                    })
            );
        };

        btnSend.addActionListener(action);
        phoneField.addActionListener(action); // Appuyer Entrée = cliquer

        // Assemblage
        main.add(logo);
        main.add(Box.createVerticalStrut(10));
        main.add(title);
        main.add(Box.createVerticalStrut(6));
        main.add(subtitle);
        main.add(Box.createVerticalStrut(35));
        main.add(phoneField);
        main.add(Box.createVerticalStrut(6));
        main.add(info);
        main.add(Box.createVerticalStrut(8));
        main.add(errorLabel);
        main.add(Box.createVerticalStrut(20));
        main.add(btnSend);

        frame.add(main);
        frame.setVisible(true);
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
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(18, 180, 80));
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(new Color(37, 211, 102));
            }
        });
        return btn;
    }
}