package model;

public class Call {
    private int    id;
    private String caller;
    private String callee;
    private String status; // RINGING | ACCEPTED | REJECTED | ENDED | MISSED
    private long   startTime;
    private long   endTime;
    private int    duration; // secondes

    public Call(String caller, String callee) {
        this.caller = caller;
        this.callee = callee;
        this.status = "RINGING";
    }

    public Call(int id, String caller, String callee, String status,
                long startTime, long endTime, int duration) {
        this.id        = id;
        this.caller    = caller;
        this.callee    = callee;
        this.status    = status;
        this.startTime = startTime;
        this.endTime   = endTime;
        this.duration  = duration;
    }

    public int    getId()        { return id; }
    public String getCaller()    { return caller; }
    public String getCallee()    { return callee; }
    public String getStatus()    { return status; }
    public long   getStartTime() { return startTime; }
    public long   getEndTime()   { return endTime; }
    public int    getDuration()  { return duration; }

    public void setStatus(String status)    { this.status    = status; }
    public void setStartTime(long t)        { this.startTime = t; }
    public void setEndTime(long t)          { this.endTime   = t; }
    public void setDuration(int d)          { this.duration  = d; }
}