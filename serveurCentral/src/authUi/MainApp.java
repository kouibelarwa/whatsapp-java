package authUi;

import auth.AuthService;
import auth.SessionManager;
import client.NetworkClient;

import javax.swing.*;

public class MainApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            NetworkClient network = new NetworkClient("100.104.161.27", 5000);
            AuthService   auth    = new AuthService(network);

            if (SessionManager.hasSession()) {
                String savedPhone = SessionManager.getSavedPhone();
                showLoading("Reconnexion...");

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
                            new PhoneView(auth, network).show();
                        });
                    }
                });
            } else {
                new PhoneView(auth, network).show();
            }
        });
    }

    private static JWindow loading;

    private static void showLoading(String msg) {
        loading = new JWindow();
        JLabel label = new JLabel(msg, SwingConstants.CENTER);
        label.setBorder(
                javax.swing.BorderFactory.createEmptyBorder(30, 50, 30, 50));
        label.setFont(label.getFont().deriveFont(16f));
        loading.add(label);
        loading.pack();
        loading.setLocationRelativeTo(null);
        loading.setVisible(true);
    }

    private static void hideLoading() {
        if (loading != null) { loading.dispose(); loading = null; }
    }
}