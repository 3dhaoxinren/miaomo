package dev.inknfclab;

/** Identity checks for the isolated Shizuku/SUI NFC service process. */
final class ServiceIdentity {
    static boolean supported(int uid) {
        return uid == 0 || uid == 2000;
    }

    static String kind(int uid) {
        if (uid == 2000) return "shell";
        if (uid == 0) return "root";
        return "UID " + uid;
    }

    private ServiceIdentity() {}
}
