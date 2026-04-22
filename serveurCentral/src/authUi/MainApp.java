package authUi;

import auth.AuthService;
import auth.SessionManager;
import client.NetworkClient;

import javax.swing.*;
import java.io.IOException;

public class MainApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            NetworkClient network = new NetworkClient("localhost", 5000);

            try {
                network.connect();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Impossible de se connecter au serveur.");
                return;
            }

            AuthService auth = new AuthService(network);

            if (SessionManager.hasSession()) {
                String savedPhone = SessionManager.getSavedPhone();

                showLoading("Reconnexion en cours...");

                auth.reconnect(savedPhone, new AuthService.AuthCallback() {

                    @Override
                    public void onSuccess(int userId, String phone,
                                          String username, boolean isNewUser) {
                        SwingUtilities.invokeLater(() -> {
                            hideLoading();
                            new ChatView(userId, phone, username, network).show();
                        });
                    }

                    @Override
                    public void onError(String reason) {
                        SwingUtilities.invokeLater(() -> {
                            hideLoading();
                            SessionManager.clearSession();
                            new PhoneView(auth).show();
                        });
                    }
                });

            } else {
                new PhoneView(auth).show();
            }
        });
    }

    // ── Loading simple ────────────────────────────────────────────────
    private static JWindow loadingWindow;

    private static void showLoading(String msg) {
        loadingWindow = new JWindow();
        JLabel label  = new JLabel(msg, SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(16f));
        label.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));
        loadingWindow.add(label);
        loadingWindow.pack();
        loadingWindow.setLocationRelativeTo(null);
        loadingWindow.setVisible(true);
    }

    private static void hideLoading() {
        if (loadingWindow != null) {
            loadingWindow.dispose();
            loadingWindow = null;
        }
    }
}