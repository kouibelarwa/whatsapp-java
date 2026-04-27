package authUi;

public class Launcher {
    public static void main(String[] args) {
        // En appelant MainApp depuis une classe qui n'étend PAS Application,
        // on contourne l'erreur "JavaFX runtime components are missing"
        // lorsque l'on utilise les JARs JavaFX directement.
        MainApp.main(args);
    }
}
