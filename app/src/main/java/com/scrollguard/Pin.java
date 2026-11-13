package com.scrollguard;

import android.content.Context;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 6 位数字密码。只存加盐哈希，原文不落盘。
 */
public final class Pin {

    private static final int ROUNDS = 120000;
    private static final int BITS = 256;

    private Pin() {
    }

    public static boolean wellFormed(String pin) {
        if (pin == null || pin.length() != 6) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            char ch = pin.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    public static void save(Context c, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltB64 = Base64.getEncoder().encodeToString(salt);
        Prefs.setPin(c, hash(pin, salt), saltB64);
    }

    public static boolean check(Context c, String pin) {
        if (!Prefs.hasPin(c) || !wellFormed(pin)) {
            return false;
        }
        String storedHash = Prefs.pinHash(c);
        String storedSalt = Prefs.pinSalt(c);
        if (storedHash.isEmpty() || storedSalt.isEmpty()) {
            return false;
        }
        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(storedSalt);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String h = hash(pin, salt);
        if (h.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(h.getBytes(), storedHash.getBytes());
    }

    private static String hash(String pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ROUNDS, BITS);
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(f.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            return "";
        } finally {
            spec.clearPassword();
        }
    }
}
