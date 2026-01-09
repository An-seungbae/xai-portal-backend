package kr.co.xai.portal.backend.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("사용법: PasswordHashGenerator <plainPassword>");
            System.out.println("예: PasswordHashGenerator admin1234");
            return;
        }

        String plain = args[0];
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        String hash = enc.encode(plain);

        System.out.println("PLAIN: " + plain);
        System.out.println("BCRYPT: " + hash);
    }
}
