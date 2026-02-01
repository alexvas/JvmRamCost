package jvmram.db.boot;

import java.util.Locale;

public enum Os {
    WINDOWS, LINUX;

    private static final Os CURRENT = detect();

    private static Os detect() {
        String name = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT);

        if (name.contains("win")) {
            return WINDOWS;
        }
        if (name.contains("nux") || name.contains("nix")) {
            return LINUX;
        }
        throw new IllegalStateException("Os %s is not supported".formatted(name));
    }

    public static Os current() {
        return CURRENT;
    }
}