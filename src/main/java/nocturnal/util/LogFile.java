package nocturnal.util;

import nocturnal.Nocturnal;
import nocturnal.config.Config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Append-only text file under .minecraft/nocturnal/. write() is safe from any thread;
 * lines are buffered and only hit the disk when flush() is called (once per tick).
 */
public final class LogFile {
    public final Path path;
    private final String header;
    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private BufferedWriter writer;

    public LogFile(String relativePath, String header) {
        this.path = Config.DIR.resolve(relativePath);
        this.header = header;
    }

    public void open() {
        try {
            Files.createDirectories(path.getParent());
            boolean fresh = !Files.exists(path) || Files.size(path) == 0;
            writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (fresh && header != null) pending.add(header);
        } catch (IOException e) {
            Nocturnal.LOG.error("Could not open {}", path, e);
        }
    }

    public void write(String line) {
        if (writer != null) pending.add(line);
    }

    public void flush() {
        if (writer == null || pending.isEmpty()) return;
        try {
            for (String line; (line = pending.poll()) != null; ) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            Nocturnal.LOG.error("Could not write {}", path, e);
        }
    }

    public void close() {
        flush();
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {
        }
        writer = null;
    }
}
