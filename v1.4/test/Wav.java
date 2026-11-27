import java.io.*;
import java.nio.*;
import java.nio.file.*;

/** Minimal 16-bit PCM mono WAV reader for tests. */
public final class Wav {
    public static float[] read(File f) throws IOException {
        byte[] b = Files.readAllBytes(f.toPath());
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        int p = 12;
        while (p + 8 <= b.length) {
            String id = new String(b, p, 4, "US-ASCII");
            int len = bb.getInt(p + 4);
            if (id.equals("data")) {
                if (len < 0 || p + 8 + len > b.length) len = b.length - p - 8;
                float[] out = new float[len / 2];
                for (int i = 0; i < out.length; i++) out[i] = bb.getShort(p + 8 + 2 * i) / 32768f;
                return out;
            }
            p += 8 + len + (len & 1);
        }
        throw new IOException("no data chunk in " + f);
    }
}
