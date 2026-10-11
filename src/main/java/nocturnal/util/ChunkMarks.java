package nocturnal.util;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import nocturnal.Nocturnal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One byte per chunk, kept in a file so it lasts between sessions. 0 means nothing is known about the chunk. */
public final class ChunkMarks {
    private static final int FORMAT = 1;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Nocturnal chunk marks");
        t.setDaemon(true);
        return t;
    });

    public final Path file;
    private final Long2ByteOpenHashMap marks = new Long2ByteOpenHashMap();
    private boolean dirty;

    public ChunkMarks(Path file) {
        this.file = file;
        load();
    }

    public byte get(int x, int z) {
        return marks.get(ChunkPos.asLong(x, z));
    }

    public boolean has(long chunk) {
        return marks.containsKey(chunk);
    }

    public void put(long chunk, byte mark) {
        marks.put(chunk, mark);
        dirty = true;
    }

    /** Writes a copy on a background thread, if anything changed since the last save. */
    public void save() {
        if (!dirty) return;
        dirty = false;
        Long2ByteOpenHashMap copy = marks.clone();
        WRITER.execute(() -> write(copy));
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != FORMAT) throw new IOException("Unknown format");
            for (int i = in.readInt(); i > 0; i--) marks.put(in.readLong(), in.readByte());
        } catch (IOException e) {
            Nocturnal.LOG.error("Could not read {}", file, e);
        }
    }

    /** Writes to a temporary file first, so quitting mid-write can't corrupt the saved one. */
    private void write(Long2ByteOpenHashMap snapshot) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(FORMAT);
                out.writeInt(snapshot.size());
                for (Long2ByteMap.Entry entry : snapshot.long2ByteEntrySet()) {
                    out.writeLong(entry.getLongKey());
                    out.writeByte(entry.getByteValue());
                }
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Nocturnal.LOG.error("Could not write {}", file, e);
        }
    }
}
