package auth;

import java.util.Random;

public class SmsCodeGenerator {

    private static final int    CODE_LENGTH = 6;
    private static final Random random      = new Random();

    public static String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++)
            code.append(random.nextInt(10));
        return code.toString();
    }

    public static boolean isValidFormat(String code) {
        if (code == null) return false;
        return code.matches("\\d{6}");
    }
}