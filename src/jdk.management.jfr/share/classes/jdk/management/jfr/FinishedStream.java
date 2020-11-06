package jdk.management.jfr;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

final class FinishedStream extends Stream {
    private final BufferedInputStream inputStream;
    private final byte[] buffer;

    FinishedStream(InputStream is, int blockSize) {
        super();
        this.inputStream = new BufferedInputStream(is, 50000);
        this.buffer = new byte[blockSize];
    }

    public byte[] read() throws IOException {
        // OK to reuse buffer since this
        // is only used for serialization
        touch();
        int read = inputStream.read(buffer);
        if (read == -1) {
            // null indicate no more data
            return null;
        }
        if (read != buffer.length) {
            byte[] smallerBuffer = new byte[read];
            System.arraycopy(buffer, 0, smallerBuffer, 0, read);
            return smallerBuffer;
        }

        return buffer;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

}
