package com.coyotai.education.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.Console;
import java.util.Arrays;
import java.util.Scanner;

/** Uses the same BCrypt algorithm and default strength as the application's login encoder. */
public final class PasswordUtils {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** Creates a new salted hash suitable for the application's users.password column. */
    public String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        return encoder.encode(password);
    }

    /** Checks a candidate password against a database hash. BCrypt hashes cannot be decrypted. */
    public boolean matchesPassword(String password, String storedHash) {
        return password != null && storedHash != null && !storedHash.isBlank()
                && encoder.matches(password, storedHash.trim());
    }

    public static void main(String[] args) {
        System.out.println("Database passwords are BCrypt hashes and cannot be decrypted.");
        System.out.println("Enter a password you think you used to check it against the stored hash.");
        String storedHash;
        String password;
        Console console = System.console();
        if (console != null) {
            storedHash = console.readLine("Paste users.password hash: ");
            char[] input = console.readPassword("Password to check: ");
            if (storedHash == null || input == null) return;
            password = new String(input);
            Arrays.fill(input, '\0');
        } else {
            // Supports running main from an IDE whose run console has no java.io.Console.
            Scanner scanner = new Scanner(System.in);
            System.out.print("Paste users.password hash: ");
            if (!scanner.hasNextLine()) return;
            storedHash = scanner.nextLine();
            System.out.print("Password to check (visible in the IDE console): ");
            if (!scanner.hasNextLine()) return;
            password = scanner.nextLine();
        }
        boolean matches = new PasswordUtils().matchesPassword(password, storedHash);
        System.out.println(matches ? "MATCH: the password is accepted by this hash."
                : "NO MATCH: the password does not match the stored hash.");
    }
}