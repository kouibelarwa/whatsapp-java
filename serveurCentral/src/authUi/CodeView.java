package authUi;

import auth.AuthService;
import client.NetworkClient;
import client.SocketManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CodeView {

    private final String        phone;
    private final AuthService   auth;
    private final NetworkClient network;

    private JFrame     frame;
    private JTextField codeField;
    private JTextField usernameField;
    private JButton    btnVerify;
    private JLabel     statusLabel;

    public CodeView(String phone, AuthService auth, NetworkClient network) {
        this.phone   = phone;
        this.auth    = auth;
        this.network = network;
    }

    public void show() {
        frame = new JFrame("Vérification du code");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 340);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBackground(new Color(18, 18, 18));
        main.setBorder(new EmptyBorder(35, 50, 35, 50));

        JLabel title = new JLabel("Vérification");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Code envoyé au : " + phone);
        sub.setForeground(new Color(150, 150, 150));
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblCode = new JLabel("Code SMS");
        lblCode.setForeground(new Color(200, 200, 200));
        lblCode.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        codeField = new JTextField();
        codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        codeField.setBackground(new Color(30, 30, 30));
        codeField.setForeground(Color.WHITE);
        codeField.setCaretColor(Color.WHITE);
        codeField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(37, 211, 102)),
                new EmptyBorder(5, 10, 5, 10)));
        codeField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        codeField.setHorizontalAlignment(JTextField.CENTER);

        JLabel lblUser = new JLabel("Votre nom (pseudo)");
        lblUser.setForeground(new Color(200, 200, 200));
        lblUser.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        usernameField = new JTextField();
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        usernameField.setBackground(new Color(30, 30, 30));
        usernameField.setForeground(Color.WHITE);
        usernameField.setCaretColor(Color.WHITE);
        usernameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60)),
                new EmptyBorder(5, 10, 5, 10)));
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 15));

        btnVerify = new JButton("Vérifier");
        btnVerify.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnVerify.setBackground(new Color(37, 211, 102));
        btnVerify.setForeground(Color.BLACK);
        btnVerify.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnVerify.setBorderPainted(false);
        btnVerify.setFocusPainted(false);
        btnVerify.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnVerify.addActionListener(e -> verifyCode());
        usernameField.addActionListener(e -> verifyCode());
        codeField.addActionListener(e -> usernameField.requestFocusInWindow());

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        main.add(title);
        main.add(Box.createVerticalStrut(5));
        main.add(sub);
        main.add(Box.createVerticalStrut(20));
        main.add(lblCode);
        main.add(Box.createVerticalStrut(5));
        main.add(codeField);
        main.add(Box.createVerticalStrut(15));
        main.add(lblUser);
        main.add(Box.createVerticalStrut(5));
        main.add(usernameField);
        main.add(Box.createVerticalStrut(15));
        main.add(btnVerify);
        main.add(Box.createVerticalStrut(10));
        main.add(statusLabel);

        frame.setContentPane(main);
        frame.setVisible(true);
        codeField.requestFocusInWindow();
    }

    private void verifyCode() {
        String code     = codeField.getText().trim();
        String username = usernameField.getText().trim();

        if (code.isEmpty()) {
            statusLabel.setText("Veuillez entrer le code SMS.");
            return;
        }
        if (username.isEmpty()) {
            statusLabel.setText("Veuillez entrer votre nom.");
            return;
        }

        btnVerify.setEnabled(false);
        btnVerify.setText("Vérification...");
        statusLabel.setText(" ");

        auth.verifyCode(phone, code, username, new AuthService.AuthCallback() {

            @Override
            public void onSuccess(int userId, String phone,
                                  String username, boolean isNewUser) {
                // Mettre à jour le phone dans SocketManager
                SocketManager.getInstance().setUserPhone(phone);
                SocketManager.getInstance().setUserId(userId);

                SwingUtilities.invokeLater(() -> {
                    frame.dispose();
                    new ChatView(userId, phone, username, network).show();
                });
            }

            @Override
            public void onError(String reason) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Code invalide : " + reason);
                    btnVerify.setEnabled(true);
                    btnVerify.setText("Vérifier");
                    codeField.selectAll();
                    codeField.requestFocusInWindow();
                });
            }
        });
    }
}