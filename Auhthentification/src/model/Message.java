package model;

public class Message {
    private int id;
    private String sender;
    private String receiver;
    private String type; //audio text file video call
    private  String content;
    private String etat; //delivred not delivred
    public Message(String sender, String receiver, String type, String content, String etat) {

        this.sender=sender;
        this.receiver=receiver;
        this.type=type;
        this.content=content;
        this.etat=etat;
    }
    public Message(int id, String sender, String receiver, String type, String content, String etat) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
        this.content = content;
        this.etat = etat;
    }


    public int getId() {
        return id;
    }

    public String getSender(){return sender;}

    public String getReceiver() {
        return receiver;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getEtat() {
        return etat;
    }
    public  void setEtat(String etat){
        this.etat=etat;
    }
}