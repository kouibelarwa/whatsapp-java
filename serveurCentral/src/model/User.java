package model;

public class User {
    private int id;
    private String phone;
    private String username;
    private String verificationCode; // reçu de Personne 2, stocké ici
    private boolean verified;
    private String status; // ONLINE / OFFLINE

    public User(int id, String phone, String username,
                String verificationCode, boolean verified, String status) {
        this.id = id;
        this.phone = phone;
        this.username = username;
        this.verificationCode = verificationCode;
        this.verified = verified;
        this.status = status;
    }

    public int getId()                  { return id; }
    public String getPhone()            { return phone; }
    public String getUsername()         { return username; }
    public String getVerificationCode() { return verificationCode; }
    public boolean isVerified()         { return verified; }
    public String getStatus()           { return status; }

    public void setVerified(boolean verified)   { this.verified = verified; }
    public void setStatus(String status)        { this.status = status; }
    public void setUsername(String username)    { this.username = username; }
}