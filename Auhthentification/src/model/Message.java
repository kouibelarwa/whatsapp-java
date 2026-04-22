package model;

public class Message {
    private int id;
    private String sender;
    private String receiver;
    private String type;     // text, audio, video, file, call
    private String filename; // nom du fichier (vide si type=text)
    private String etat;     // NOT_DELIVERED / DELIVERED

    // Constructeur sans ID (nouveau message)
    public Message(String sender, String receiver, String type, String filename, String etat) {
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
        this.filename = filename;
        this.etat = etat;
    }

    // Constructeur avec ID (message récupéré de la DB)
    public Message(int id, String sender, String receiver,
                   String type, String filename, String etat) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
        this.filename = filename;
        this.etat = etat;
    }

    public int getId()         { return id; }
    public String getSender()  { return sender; }
    public String getReceiver(){ return receiver; }
    public String getType()    { return type; }
    public String getFilename(){ return filename; }
    public String getEtat()    { return etat; }

    public void setEtat(String etat) { this.etat = etat; }
}