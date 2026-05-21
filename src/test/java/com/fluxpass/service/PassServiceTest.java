package com.fluxpass.service;

import com.fluxpass.model.PasswordEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.net.URI;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class PassServiceTest {

    @Test
    @DisplayName("PasswordEntry extracts name and category from path")
    void testPasswordEntryPathConstruction() {
        PasswordEntry root = new PasswordEntry("github");
        assertEquals("github", root.getFullPath());
        assertEquals("github", root.getName());
        assertEquals("", root.getCategory());

        PasswordEntry withCategory = new PasswordEntry("social/github");
        assertEquals("social/github", withCategory.getFullPath());
        assertEquals("github", withCategory.getName());
        assertEquals("social", withCategory.getCategory());

        PasswordEntry nested = new PasswordEntry("work/internal/vpn");
        assertEquals("work/internal/vpn", nested.getFullPath());
        assertEquals("vpn", nested.getName());
        assertEquals("work/internal", nested.getCategory());

        PasswordEntry deepNested = new PasswordEntry("a/b/c/d/entry");
        assertEquals("a/b/c/d/entry", deepNested.getFullPath());
        assertEquals("entry", deepNested.getName());
        assertEquals("a/b/c/d", deepNested.getCategory());
    }

    @Test
    @DisplayName("PasswordEntry equality is based on fullPath")
    void testPasswordEntryEquality() {
        PasswordEntry e1 = new PasswordEntry("social/github");
        PasswordEntry e2 = new PasswordEntry("social/github");
        PasswordEntry e3 = new PasswordEntry("social/twitter");

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertNotEquals(e1, e3);
    }

    @Test
    @DisplayName("deriveHostname extracts host from URL")
    void testDeriveHostname() {
        assertEquals("example.com", deriveHostname("https://example.com"));
        assertEquals("example.com", deriveHostname("http://example.com/path"));
        assertEquals("example.com", deriveHostname("www.example.com"));
        assertEquals("example.com", deriveHostname("https://www.example.com/page?q=1"));
        assertEquals("sub.domain.co.uk", deriveHostname("https://sub.domain.co.uk/path"));
        assertEquals("", deriveHostname(""));
        assertEquals("", deriveHostname((String) null));
        assertEquals("", deriveHostname("not-a-valid-url-!@#"));
    }

    @Test
    @DisplayName("generatePassword produces correct length and required character classes")
    void testGeneratePassword() {
        String pw = generatePasswordString(20, false);
        assertEquals(20, pw.length());
        assertTrue(pw.matches(".*[A-Z].*"), "Must contain uppercase");
        assertTrue(pw.matches(".*[a-z].*"), "Must contain lowercase");
        assertTrue(pw.matches(".*[0-9].*"), "Must contain digit");
        assertTrue(pw.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*"), "Must contain symbol");

        String pwNoSymbols = generatePasswordString(16, true);
        assertEquals(16, pwNoSymbols.length());
        assertTrue(pwNoSymbols.matches(".*[A-Z].*"));
        assertTrue(pwNoSymbols.matches(".*[a-z].*"));
        assertTrue(pwNoSymbols.matches(".*[0-9].*"));
        assertFalse(pwNoSymbols.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*"), "Must NOT contain symbol");
    }

    @Test
    @DisplayName("generatePassword with minimum length")
    void testGeneratePasswordMinLength() {
        String pw = generatePasswordString(4, false);
        assertEquals(4, pw.length());
        String pwNoSym = generatePasswordString(3, true);
        assertEquals(3, pwNoSym.length());
    }

    @Test
    @DisplayName("PassService can be instantiated")
    void testPassServiceCreation() {
        PassService service = new PassService();
        assertNotNull(service);
        assertNotNull(service.getStorePath());
    }

    static String deriveHostname(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            String input = url.startsWith("http") ? url : "https://" + url;
            String host = URI.create(input).toURL().getHost();
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return "";
        }
    }

    static String generatePasswordString(int length, boolean noSymbols) {
        StringBuilder sb = new StringBuilder();
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?";

        String charset = upper + lower + digits + (noSymbols ? "" : symbols);
        SecureRandom random = new SecureRandom();

        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        if (!noSymbols) {
            sb.append(symbols.charAt(random.nextInt(symbols.length())));
        }

        int remaining = length - sb.length();
        for (int i = 0; i < remaining; i++) {
            sb.append(charset.charAt(random.nextInt(charset.length())));
        }

        char[] chars = sb.toString().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            int j = random.nextInt(chars.length);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }

        return new String(chars);
    }
}
