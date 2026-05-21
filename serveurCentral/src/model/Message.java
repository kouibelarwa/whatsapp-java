package model;


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
    private Integer replyToId; // ID du message auquel on répond
    private boolean isPinned;


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


    public Message(int id, int senderId, String senderPhone,
                   int receiverId, String type,
                   String filename, String content, String etat, java.sql.Timestamp sentAt) {
        this(id, senderId, senderPhone, receiverId, type, filename, content, etat, sentAt, null, false);
    }

    public Message(int id, int senderId, String senderPhone,
                   int receiverId, String type,
                   String filename, String content, String etat, java.sql.Timestamp sentAt, Integer replyToId) {
        this(id, senderId, senderPhone, receiverId, type, filename, content, etat, sentAt, replyToId, false);
    }

    public Message(int id, int senderId, String senderPhone,
                   int receiverId, String type,
                   String filename, String content, String etat, java.sql.Timestamp sentAt, Integer replyToId, boolean isPinned) {
        this.id          = id;
        this.senderId    = senderId;
        this.senderPhone = senderPhone;
        this.receiverId  = receiverId;
        this.type        = type;
        this.filename    = filename;
        this.content     = content;
        this.etat        = etat;
        this.sentAt      = sentAt;
        this.replyToId   = replyToId;
        this.isPinned    = isPinned;
    }

    private Message() {}

    // ── Helpers vérifie si c’est un message texte/audio/video
    public boolean isText()   { return "text".equals(type); }
    public boolean isBinary() {
        return "audio".equals(type) || "video".equals(type) || "file".equals(type) || "image".equals(type);
    }

    // ── Getters / setters
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
    public Integer getReplyToId() { return replyToId; }
    public void setReplyToId(Integer replyToId) { this.replyToId = replyToId; }
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
}