/*
 * Copyright (c) 2002-2017, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.org.jline.utils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.nio.CharBuffer;

public class NonBlockingPumpReader extends NonBlockingReader {

    private static final int DEFAULT_BUFFER_SIZE = 4096;

    // Read and write buffer are backed by the same array
    private final CharBuffer readBuffer;
    private final CharBuffer writeBuffer;

    private final Writer writer;

    private boolean closed;

    public NonBlockingPumpReader() {
        this(DEFAULT_BUFFER_SIZE);
    }

    public NonBlockingPumpReader(int bufferSize) {
        char[] buf = new char[bufferSize];
        this.readBuffer = CharBuffer.wrap(buf);
        this.writeBuffer = CharBuffer.wrap(buf);
        this.writer = new NbpWriter();
        // There are no bytes available to read after initialization
        readBuffer.limit(0);
    }

    public Writer getWriter() {
        return this.writer;
    }

    private int wait(CharBuffer buffer, long timeout) throws InterruptedIOException {
        boolean isInfinite = (timeout <= 0L);
        long end = 0;
        if (!isInfinite) {
            end = System.currentTimeMillis() + timeout;
        }
        while (!closed && !buffer.hasRemaining() && (isInfinite || timeout > 0L)) {
            // Wake up waiting readers/writers
            notifyAll();
            try {
                wait(timeout);
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
            if (!isInfinite) {
                timeout = end - System.currentTimeMillis();
            }
        }
        return closed
                ? EOF
                : buffer.hasRemaining()
                    ? 0
                    : READ_EXPIRED;
    }

    private static boolean rewind(CharBuffer buffer, CharBuffer other) {
        // Extend limit of other buffer if there is additional input/output available
        if (buffer.position() > other.position()) {
            other.limit(buffer.position());
        }
        // If we have reached the end of the buffer, rewind and set the new limit
        if (buffer.position() == buffer.capacity()) {
            buffer.rewind();
            buffer.limit(other.position());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized boolean ready() {
        return readBuffer.hasRemaining();
    }

    public synchronized int available() {
        int count = readBuffer.remaining();
        if (writeBuffer.position() < readBuffer.position()) {
            count += writeBuffer.position();
        }
        return count;
    }

    @Override
    protected synchronized int read(long timeout, boolean isPeek) throws IOException {
        // Blocks until more input is available or the reader is closed.
        int res = wait(readBuffer, timeout);
        if (res >= 0) {
            res = isPeek ? readBuffer.get(readBuffer.position()) : readBuffer.get();
        }
        rewind(readBuffer, writeBuffer);
        return res;
    }

    synchronized void write(char[] cbuf, int off, int len) throws IOException {
        while (len > 0) {
            // Blocks until there is new space available for buffering or the
            // reader is closed.
            if (wait(writeBuffer, 0L) == EOF) {
                throw new ClosedException();
            }
            // Copy as much characters as we can
            int count = Math.min(len, writeBuffer.remaining());
            writeBuffer.put(cbuf, off, count);
            off += count;
            len -= count;
            // Update buffer states and rewind if necessary
            rewind(writeBuffer, readBuffer);
        }
    }

    synchronized void flush() {
        // Avoid waking up readers when there is nothing to read
        if (readBuffer.hasRemaining()) {
            // Notify readers
            notifyAll();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        this.closed = true;
        notifyAll();
    }

    private class NbpWriter extends Writer {

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            NonBlockingPumpReader.this.write(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            NonBlockingPumpReader.this.flush();
        }

        @Override
        public void close() throws IOException {
            NonBlockingPumpReader.this.close();
        }

    }

}
