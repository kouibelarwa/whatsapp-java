package model;

public class User {
    private int id;
    private String phone;
    private  String username;
    private boolean verified;
    private String status; // ONLINE / OFFLINE
    public User(int id,String phone, String username, boolean verified, String status) {
        this.id=id;
        this.phone = phone;
        this.username = username;
        this.verified = verified;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getUsername() {
        return username;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}