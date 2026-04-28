package model;

/**
 * Représente un message.
 *
 * RÈGLE DE STOCKAGE :
 *   type "text"              → content rempli, data null
 *   type "audio/video/file"  → data LONGBLOB rempli, content null
 *
 * IDENTIFIANTS :
 *   senderId / receiverId → IDs de la table users (INT)
 *   senderPhone           → phone de l'expéditeur (pour affichage et socket)
 */
public class Message {

    private int    id;
    private int    senderId;
    private int    receiverId;
    private String senderPhone;    // pour affichage côté destinataire
    private String type;
    private String filename;
    private String content;
    private String etat;
    private java.sql.Timestamp sentAt;

    // ── Factory texte ────────────────────────────────────────────
    public static Message text(int senderId, String senderPhone,
                               int receiverId, String content) {
        Message m    = new Message();
        m.senderId   = senderId;
        m.senderPhone = senderPhone;
        m.receiverId = receiverId;
        m.type       = "text";
        m.content    = content;
        m.etat       = "NOT_DELIVERED";
        m.sentAt     = new java.sql.Timestamp(System.currentTimeMillis());
        return m;
    }

    // ── Factory binaire (audio / video / file) ───────────────────
    public static Message binary(int senderId, String senderPhone,
                                 int receiverId,
                                 String type, String filename) {
        Message m    = new Message();
        m.senderId   = senderId;
        m.senderPhone = senderPhone;
        m.receiverId = receiverId;
        m.type       = type;
        m.filename   = filename;
        m.etat       = "NOT_DELIVERED";
        m.sentAt     = new java.sql.Timestamp(System.currentTimeMillis());
        return m;
    }

    // ── Constructeur depuis la base ──────────────────────────────
    public Message(int id, int senderId, String senderPhone,
                   int receiverId, String type,
                   String filename, String content, String etat, java.sql.Timestamp sentAt) {
        this.id          = id;
        this.senderId    = senderId;
        this.senderPhone = senderPhone;
        this.receiverId  = receiverId;
        this.type        = type;
        this.filename    = filename;
        this.content     = content;
        this.etat        = etat;
        this.sentAt      = sentAt;
    }

    private Message() {}

    // ── Helpers ──────────────────────────────────────────────────
    public boolean isText()   { return "text".equals(type); }
    public boolean isBinary() {
        return "audio".equals(type) || "video".equals(type) || "file".equals(type) || "image".equals(type);
    }

    // ── Getters / setters ─────────────────────────────────────────
    public int    getId()           { return id; }
    public int    getSenderId()     { return senderId; }
    public int    getReceiverId()   { return receiverId; }
    public String getSenderPhone()  { return senderPhone; }
    public String getType()         { return type; }
    public String getFilename()     { return filename; }
    public String getContent()      { return content; }
    public String getEtat()         { return etat; }
    public void   setEtat(String e) { this.etat = e; }
    public java.sql.Timestamp getSentAt() { return sentAt; }
}