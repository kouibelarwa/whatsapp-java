package authUi;

import auth.AuthService;
import client.NetworkClient;
import client.SocketManager;

import java.util.Scanner;

public class PhoneScreen {

    private final AuthService authService = new AuthService();
    private final Scanner scanner = new Scanner(System.in);

    public void launch() {

        System.out.println("📱 Enter phone:");
        String phone = scanner.nextLine();

        boolean ok = authService.sendPhoneNumber(phone);

        if (ok) {
            ProfileScreen ps = new ProfileScreen(authService, phone);
            ps.show();
        }
    }
}