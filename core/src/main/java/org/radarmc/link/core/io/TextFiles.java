package org.radarmc.link.core.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class TextFiles {
    private static final int BUFFER_SIZE = 8192;
    private static final String TEMP_SUFFIX = ".tmp";

    private TextFiles() {}

    public static String readUtf8(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    public static String readUtf8(InputStream in, int maxBytes) throws IOException {
        return new String(readAll(in, maxBytes), StandardCharsets.UTF_8);
    }

    public static void writeUtf8(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeUtf8Atomically(Path file, String text) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        writeUtf8(temp, text);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > maxBytes) {
                throw new IOException("input is larger than " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
