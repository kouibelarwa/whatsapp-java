package client;

import java.io.*;
import java.net.Socket;

public class NetworkClient {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    public boolean connectWithSession(String phone) {    // ← paramètre devient phone

        try {
            Socket socket = new Socket(HOST, PORT);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("SESSION:" + phone);              // ✅ ligne 1

            String response = in.readLine();

            if (response != null && response.startsWith("SESSION_OK:")) {  // ✅ ligne 2
                int userId = Integer.parseInt(response.split(":")[1]);

                SocketManager sm = SocketManager.getInstance();
                sm.initAuth(socket, userId, phone);       // ✅ ligne 3

                System.out.println("✅ Auth OK");
                return true;
            }

            socket.close();
            return false;

        } catch (Exception e) {
            System.err.println("Network error: " + e.getMessage());
            return false;
        }
    }
}