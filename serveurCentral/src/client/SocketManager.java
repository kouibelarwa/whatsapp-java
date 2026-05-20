package client;

import java.io.*;
import java.net.Socket;

public class SocketManager {

    private static SocketManager legacyInstance;

    private Socket socket;
    private DataOutputStream binOut;
    private DataInputStream binIn;
    private int userId;
    private String userPhone;
    private boolean authenticated = false;

    public SocketManager() {}

    public static synchronized SocketManager getInstance() {
        if (legacyInstance == null) legacyInstance = new SocketManager();
        return legacyInstance;
    }

    public void setUserPhone(String phone) {
        this.userPhone = phone;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getUserId() {
        return userId;
    }

    public void enableBinaryMode() throws IOException {
        this.binOut = new DataOutputStream(socket.getOutputStream());
        this.binIn = new DataInputStream(socket.getInputStream());
        this.authenticated = true;
    }

    public synchronized void sendBinary(String type, String receiver, String filename, byte[] data) {
        try {
            if (binOut == null) return;
            binOut.writeUTF(type);
            binOut.writeUTF(receiver);
            binOut.writeUTF(userPhone != null ? userPhone : "");
            binOut.writeUTF(filename != null ? filename : "");
            binOut.writeInt(data.length);
            binOut.write(data);
            binOut.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startListening(MessageListener listener) {
        new Thread(() -> {
            try {
                while (true) {
                    String type     = binIn.readUTF();
                    String sender   = binIn.readUTF();
                    String filename = binIn.readUTF();
                    int size        = binIn.readInt();
                    byte[] data     = new byte[size];
                    binIn.readFully(data);
                    listener.onMessage(type, sender, filename, data);
                }
            } catch (Exception e) {
                listener.onDisconnect();//si erreur de connexion
            }
        }).start();
    }

    public interface MessageListener {
        void onMessage(String type, String sender, String filename, byte[] data);
        void onDisconnect();
    }

    public void initAuth(Socket s, int id, String p) {
        this.socket = s;
        this.userId = id;
        this.userPhone = p;
    }
//fermer la connexion actuelle
    public static void reset() {
        try { if(legacyInstance != null && legacyInstance.socket != null) legacyInstance.socket.close(); }
        catch(Exception e){}
        legacyInstance = null;
    }
}