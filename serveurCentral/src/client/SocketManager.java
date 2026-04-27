package client;

import java.io.*;
import java.net.Socket;

public class SocketManager {
    private static SocketManager instance;
    private Socket socket;
    private DataOutputStream binOut;
    private DataInputStream binIn;

    private int userId;
    private String userPhone;
    private boolean authenticated = false;

    private SocketManager() {}

    public static synchronized SocketManager getInstance() {
        if (instance == null) instance = new SocketManager();
        return instance;
    }

    public void setUserPhone(String phone) {
        this.userPhone = phone;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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
                    String ignored  = binIn.readUTF();
                    String filename = binIn.readUTF();
                    int size        = binIn.readInt();
                    byte[] data     = new byte[size];
                    binIn.readFully(data);
                    listener.onMessage(type, sender, filename, data);
                }
            } catch (Exception e) {
                listener.onDisconnect();
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

    public static void reset() {
        try { if(instance != null && instance.socket != null) instance.socket.close(); }
        catch(Exception e){}
        instance = null;
    }
}