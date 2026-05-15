package auth;

public class SessionManager {

    private static int    userId   = -1;
    private static String phone;
    private static String username;

    public static void saveSession(int id, String p, String u) {
        userId   = id;
        phone    = p;
        username = u;
    }

    public static void clearSession() {
        userId   = -1;
        phone    = null;
        username = null;
    }

    public static boolean hasSession()        { return userId != -1; }
    public static int     getSavedUserId()    { return userId;       }
    public static String  getSavedPhone()     { return phone;        }
    public static String  getSavedUsername()  { return username;     }
}