package authUi;

import auth.AuthService;
import auth.SessionManager;
import client.SocketManager;

import java.util.Scanner;

public class ProfileScreen {

    private final AuthService authService;
    private final String phone;
    private final Scanner scanner = new Scanner(System.in);

    public ProfileScreen(AuthService authService, String phone) {
        this.authService = authService;
        this.phone = phone;
    }

    public void show() {

        System.out.print("Code: ");
        String code = scanner.nextLine();

        System.out.print("Username: ");
        String username = scanner.nextLine();

        boolean ok = authService.verifyCode(phone, code, username);

        if (ok) {

            SessionManager.saveSession(username, phone);

            try {
                SocketManager sm = SocketManager.getInstance();
                sm.enableBinaryMode();
                sm.startListening(new SocketManager.MessageListener() {

                    public void onMessage(String type, String sender,
                                          String filename, byte[] data) {  // ✅ 4 paramètres
                        System.out.println(sender + ": " + new String(data));
                    }

                    public void onDisconnect() {
                        System.out.println("Disconnected");
                        SocketManager.reset();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }

            launchChat(username);
        }
    }

    private void launchChat(String username) {

        System.out.println("💬 Chat started");

        while (true) {

            String input = scanner.nextLine();

            if (input.trim().equals("quit")) break;
            String[] p = input.split(":", 2);

            if (p.length == 2) {
                SocketManager.getInstance().sendMessage(p[0], p[1]);
            }
        }
    }
}