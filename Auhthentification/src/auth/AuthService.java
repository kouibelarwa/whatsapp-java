package auth;

import client.SocketManager;

import java.io.*;
import java.net.Socket;

public class AuthService {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    private Socket socket;
    private PrintWriter textOut;
    private BufferedReader textIn;

    // =========================
    // STEP 1 : SEND PHONE
    // =========================
    public boolean sendPhoneNumber(String phone) {

        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);

            textOut = new PrintWriter(socket.getOutputStream(), true);
            textIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // AUTH REQUEST (TEXT PROTOCOL)
            textOut.println("AUTH_REQUEST:" + phone);

            String response = textIn.readLine();

            if ("SMS_SENT".equals(response)) {
                System.out.println("📩 SMS sent");
                return true;
            }

            socket.close();
            return false;

        } catch (Exception e) {
            System.err.println("Auth error: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // STEP 2 : VERIFY CODE
    // =========================
    public boolean verifyCode(String phone, String code, String username) {

        try {
            textOut.println("VERIFY_CODE:" + phone + ":" + code + ":" + username);

            String response = textIn.readLine();

            if ("AUTH_OK".equals(response)) {

                // 🔥 SAVE SESSION
                SessionManager.saveSession(username, phone);

                // 🔥 SWITCH SOCKET TO BINARY MODE
                SocketManager sm = SocketManager.getInstance();

                sm.initAuth(socket, username);   // garde socket + username
                sm.enableBinaryMode();           // switch chat mode

                System.out.println("✅ Auth success");
                return true;
            }

            System.err.println("Auth failed: " + response);
            return false;

        } catch (Exception e) {
            System.err.println("Verify error: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // GETTERS (OPTIONAL)
    // =========================
    public Socket getSocket() {
        return socket;
    }

    public PrintWriter getOut() {
        return textOut;
    }

    public BufferedReader getIn() {
        return textIn;
    }
}