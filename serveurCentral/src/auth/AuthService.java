package auth;

import client.NetworkClient;
import client.SocketManager;

public class AuthService {

    private final NetworkClient network;

    public interface AuthCallback {
        void onSuccess(int userId, String phone, String username, boolean isNewUser);
        void onError(String reason);
    }

    public AuthService(NetworkClient network) {
        this.network = network;
    }

    public NetworkClient getNetwork() { return network; }

    // ─── STEP 1 : demander le code SMS ───────────────────────────
    public void requestCode(String phone, Runnable ok, Runnable err) {
        new Thread(() -> {
            try {
                network.connect();
                String res = network.send("AUTH_REQUEST:" + phone);
                if ("SMS_SENT".equals(res)) ok.run();
                else                        err.run();
            } catch (Exception e) {
                System.err.println("requestCode error: " + e.getMessage());
                err.run();
            }
        }).start();
    }

    // ─── STEP 2 : vérifier le code ───────────────────────────────
    // Dans AuthService.java
    public void verifyCode(String phone, String code,
                           String username, AuthCallback cb) {
        new Thread(() -> {
            try {
                String res = network.send(
                        "VERIFY_CODE:" + phone + ":" + code + ":" + username);
                if (res == null) { cb.onError("NO_RESPONSE"); return; }

                if (res.startsWith("AUTH_OK:")) {
                    String[] p    = res.split(":", 4);
                    int    uid    = Integer.parseInt(p[1]);
                    String rPhone = p[2];
                    String rUser  = p.length > 3 ? p[3] : username;

                    SessionManager.saveSession(uid, rPhone, rUser);

                    // ✅ FIX PRINCIPAL : injecter le socket AVANT enableBinaryMode
                    SocketManager.getInstance().initAuth(
                            network.getSocket(), uid, rPhone);
                    SocketManager.getInstance().enableBinaryMode();

                    cb.onSuccess(uid, rPhone, rUser, true);
                } else {
                    cb.onError(res);
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    // ─── RECONNECT ────────────────────────────────────────────────
    public void reconnect(String phone, AuthCallback cb) {
        new Thread(() -> {
            try {
                network.connect();
                String res = network.send("SESSION:" + phone);
                if (res == null) { cb.onError("NO_RESPONSE"); return; }

                if (res.startsWith("SESSION_OK:")) {
                    // SESSION_OK:userId:username
                    String[] p   = res.split(":", 3);
                    int    uid   = Integer.parseInt(p[1]);
                    String uname = p.length > 2 ? p[2] : "";

                    SessionManager.saveSession(uid, phone, uname);
                    SocketManager.getInstance().initAuth(
                            network.getSocket(), uid, phone);
                    SocketManager.getInstance().enableBinaryMode();
                    cb.onSuccess(uid, phone, uname, false);
                } else {
                    SessionManager.clearSession();
                    cb.onError("SESSION_INVALID");
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }
}