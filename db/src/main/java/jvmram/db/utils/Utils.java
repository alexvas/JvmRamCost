package jvmram.db.utils;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utils {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Utils() {}


    public static Object invokeExact(MethodHandle handle, Object... args) {
        try {
            return handle.invokeExact(args);
        } catch (Throwable t) {
            LOG.error("Failed to invoke native method", t);
            throw new IllegalStateException("Failed to invoke native method", t);
        }
    }

    public static @Nullable String readContent(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read content from %s".formatted(path), e);
        }
    }

    public static @Nullable String readResource(String resourcePath) {
        try (var is = Utils.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + resourcePath, e);
        }
    }
}
