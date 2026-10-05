package com.luaspets.security;

import jakarta.servlet.http.HttpSession;

public final class MfaSession {
    public static final String PASSWORD_CONFIRMED = "LUAS_MFA_PASSWORD_CONFIRMED";
    public static final String STATE = "LUAS_MFA_STATE";
    public static final String PROVISIONAL = "LUAS_MFA_PROVISIONAL";
    public static final String EXPIRES = "LUAS_MFA_EXPIRES";
    private static final int ATTEMPT_LIMIT = 5;
    private static final String ATTEMPTS = "LUAS_MFA_ATTEMPTS";
    public enum State { PENDING, ENROLLMENT, COMPLETE }
    private MfaSession() {
    }
    public static boolean attempt(HttpSession session) {
        synchronized (session) {
            int attempts = session.getAttribute(ATTEMPTS) instanceof Integer n ? n : 0;
            session.setAttribute(ATTEMPTS, attempts + 1);
            return attempts < ATTEMPT_LIMIT;
        }
    }
    public static void resetAttempts(HttpSession session) {
        session.removeAttribute(ATTEMPTS);
    }
    public static void clearSetup(HttpSession session) {
        session.removeAttribute(PROVISIONAL);
        session.removeAttribute(EXPIRES);
    }
}
