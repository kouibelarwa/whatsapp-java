package auth;

import client.NetworkClient;

public class AuthService {

    private final NetworkClient network;

    public interface AuthCallback {
        void onSuccess(int userId, String phone, String username, boolean isNewUser);
        void onError(String reason);
    }

    public AuthService(NetworkClient network) {
        this.network = network;
    }

    public void requestCode(String phone, Runnable ok, Runnable err) {
        new Thread(() -> {
            String res = network.send("AUTH_REQUEST:" + phone);
            if ("SMS_SENT".equals(res)) ok.run();
            else err.run();
        }).start();
    }

    public void verifyCode(String phone, String code, String username, AuthCallback cb) {
        new Thread(() -> {

            String res = network.send("VERIFY_CODE:" + phone + ":" + code + ":" + username);

            if (res == null) {
                cb.onError("NO_RESPONSE");
                return;
            }

            if (res.startsWith("AUTH_OK:")) {

                String[] p = res.split(":");
                int userId = Integer.parseInt(p[1]);
                String retPhone = p[2];
                String retUsername = p.length > 3 ? p[3] : username;

                SessionManager.saveSession(userId, retPhone, retUsername);
                cb.onSuccess(userId, retPhone, retUsername, true);

            } else if ("AUTH_FAIL:WRONG_CODE".equals(res)) {
                cb.onError("WRONG_CODE");
            } else {
                cb.onError(res);
            }
        }).start();
    }

    public void reconnect(String phone, AuthCallback cb) {
        new Thread(() -> {

            String res = network.send("SESSION:" + phone);

            if (res == null) {
                cb.onError("NO_RESPONSE");
                return;
            }

            if (res.startsWith("SESSION_OK:")) {

                String[] p = res.split(":");
                int userId = Integer.parseInt(p[1]);
                String username = p.length > 2 ? p[2] : "";

                SessionManager.saveSession(userId, phone, username);
                cb.onSuccess(userId, phone, username, false);

            } else {
                SessionManager.clearSession();
                cb.onError("SESSION_INVALID");
            }
        }).start();
    }
}