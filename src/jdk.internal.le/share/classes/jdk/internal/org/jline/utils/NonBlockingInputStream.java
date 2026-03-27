/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.io.IOException;
import java.io.InputStream;
//import java.util.logging.Level;
//import java.util.logging.Logger;

import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_CLOSE_MODE;

/**
 * An input stream that supports non-blocking read operations with timeouts.
 *
 * <p>
 * The NonBlockingInputStream class extends InputStream to provide non-blocking read
 * operations. Unlike standard input streams, which block indefinitely until data is
 * available or the end of the stream is reached, non-blocking input streams can be
 * configured to return immediately or after a specified timeout if no data is available.
 * </p>
 *
 * <p>
 * This class defines two special return values:
 * </p>
 * <ul>
 *   <li>{@link #EOF} (-1) - Indicates that the end of the stream has been reached</li>
 *   <li>{@link #READ_EXPIRED} (-2) - Indicates that the read operation timed out</li>
 * </ul>
 *
 * <p>
 * This abstract class provides the framework for non-blocking input operations, with
 * concrete implementations handling the details of how the non-blocking behavior is
 * achieved (e.g., through NIO, separate threads, or native methods).
 * </p>
 *
 * <p>
 * Non-blocking input streams are particularly useful for terminal applications that
 * need to perform other tasks while waiting for user input, or that need to implement
 * features like input timeouts or polling.
 * </p>
 */
public abstract class NonBlockingInputStream extends InputStream {

    /**
     * Default constructor.
     * Initializes close mode based on the current value of the system property.
     */
    public NonBlockingInputStream() {
        this.closeMode = parseCloseMode();
    }

    /**
     * Parses the close mode from system properties.
     */
    private static CloseMode parseCloseMode() {
        String mode = System.getProperty(PROP_CLOSE_MODE);
        if (mode != null) {
            if ("strict".equalsIgnoreCase(mode)) {
                return CloseMode.STRICT;
            } else if ("warn".equalsIgnoreCase(mode)) {
                return CloseMode.WARN;
            } else if ("lenient".equalsIgnoreCase(mode)) {
                return CloseMode.LENIENT;
            }
        }

        // Default: strict for v4
        return CloseMode.STRICT;
    }

    public static final int EOF = -1;
    public static final int READ_EXPIRED = -2;

//    private static final Logger LOG = Logger.getLogger(NonBlockingInputStream.class.getName());

    /**
     * Flag indicating whether this input stream has been closed.
     * Marked as volatile to ensure visibility across threads.
     */
    protected volatile boolean closed = false;

    /**
     * Flag to track if a warning has been logged for this input stream.
     * Used to avoid log spam in warn mode.
     */
    private boolean warningLogged = false;

    /**
     * Close mode for this input stream.
     * Determined at construction time from the system property.
     */
    private final CloseMode closeMode;

    /**
     * Enum representing the close mode behavior.
     */
    private enum CloseMode {
        /** Throw ClosedException when accessing closed streams */
        STRICT,
        /** Log warning when accessing closed streams */
        WARN,
        /** Silently allow accessing closed streams */
        LENIENT
    }

    /**
     * Checks if this input stream has been closed.
     * <p>
     * In JLine 4.x, strict mode is enabled by default: when a closed input stream is accessed,
     * it throws a {@code ClosedException}. This ensures proper resource management and
     * prevents use-after-close bugs.
     * </p>
     * <p>
     * The behavior can be controlled via the system property
     * {@link org.jline.terminal.TerminalBuilder#PROP_CLOSE_MODE PROP_CLOSE_MODE}:
     * </p>
     * <ul>
     *   <li>{@code "strict"} - Throw {@code ClosedException} (default in JLine 4.x)</li>
     *   <li>{@code "warn"} - Log a warning but continue (default in JLine 3.x)</li>
     *   <li>{@code "lenient"} - Silently allow access (no warning, no exception)</li>
     * </ul>
     *
     * @throws ClosedException if this input stream has been closed and strict mode is enabled (default)
     */
    protected void checkClosed() throws IOException {
        if (closed) {
            switch (closeMode) {
                case STRICT:
                    throw new ClosedException();
                case WARN:
                    // Log warning only once per input stream instance to avoid log spam
                    if (!warningLogged) {
//                        LOG.log(
//                                Level.WARNING,
//                                "Accessing a closed input stream. "
//                                        + "This may indicate a resource management issue. "
//                                        + "Set -D" + PROP_CLOSE_MODE + "=strict to make this an error.",
//                                new Throwable("Stack trace"));
                        warningLogged = true;
                    }
                    break;
                case LENIENT:
                    // Silently allow access
                    break;
            }
        }
    }

    /**
     * Reads the next byte of data from the input stream. The value byte is
     * returned as an <code>int</code> in the range <code>0</code> to
     * <code>255</code>. If no byte is available because the end of the stream
     * has been reached, the value <code>-1</code> is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     *
     * @return     the next byte of data, or <code>-1</code> if the end of the
     *             stream is reached.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public int read() throws IOException {
        return read(0L, false);
    }

    /**
     * Peeks to see if there is a byte waiting in the input stream without
     * actually consuming the byte.
     *
     * @param      timeout The amount of time to wait, 0 == forever
     * @return     -1 on eof, -2 if the timeout expired with no available input
     *             or the character that was read (without consuming it).
     * @exception  IOException  if an I/O error occurs.
     */
    public int peek(long timeout) throws IOException {
        return read(timeout, true);
    }

    /**
     * Attempts to read a character from the input stream for a specific
     * period of time.
     *
     * @param      timeout      The amount of time to wait for the character
     * @return     The character read, -1 if EOF is reached,
     *             or -2 if the read timed out.
     * @exception  IOException  if an I/O error occurs.
     */
    public int read(long timeout) throws IOException {
        return read(timeout, false);
    }

    public int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        int c = read();
        if (c == EOF) {
            return EOF;
        }
        b[off] = (byte) c;
        return 1;
    }

    public int readBuffered(byte[] b) throws IOException {
        return readBuffered(b, 0L);
    }

    public int readBuffered(byte[] b, long timeout) throws IOException {
        return readBuffered(b, 0, b.length, timeout);
    }

    public int readBuffered(byte[] b, int off, int len, long timeout) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || off + len < b.length) {
            throw new IllegalArgumentException();
        } else if (len == 0) {
            return 0;
        } else {
            Timeout t = new Timeout(timeout);
            int nb = 0;
            while (!t.elapsed()) {
                int r = read(nb > 0 ? 1 : t.timeout());
                if (r < 0) {
                    return nb > 0 ? nb : r;
                }
                b[off + nb++] = (byte) r;
                if (nb >= len || t.isInfinite()) {
                    break;
                }
            }
            return nb;
        }
    }

    /**
     * Shuts down the thread that is handling blocking I/O if any. Note that if the
     * thread is currently blocked waiting for I/O it may not actually
     * shut down until the I/O is received.
     */
    public void shutdown() {}

    public abstract int read(long timeout, boolean isPeek) throws IOException;

    /**
     * Closes this input stream and marks it as closed.
     * <p>
     * Subsequent read operations behavior depends on the
     * {@link org.jline.terminal.TerminalBuilder#PROP_CLOSE_MODE PROP_CLOSE_MODE} setting:
     * </p>
     * <ul>
     *   <li>{@code "strict"} - Throw {@link ClosedException} (default in JLine 4.x)</li>
     *   <li>{@code "warn"} - Log a warning but continue (default in JLine 3.x)</li>
     *   <li>{@code "lenient"} - Silently allow access</li>
     * </ul>
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        closed = true;
    }
}
