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
import java.io.Reader;
//import java.util.logging.Level;
//import java.util.logging.Logger;

import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_CLOSE_MODE;

/**
 * A reader that provides non-blocking read operations.
 *
 * <p>
 * The NonBlockingReader class extends the standard Reader class to provide
 * non-blocking read operations. Unlike standard readers, which block until
 * data is available or the end of the stream is reached, non-blocking readers
 * can be configured to return immediately or after a specified timeout if no
 * data is available.
 * </p>
 *
 * <p>
 * This class is particularly useful for terminal applications that need to
 * perform other tasks while waiting for user input, or that need to implement
 * features like input timeouts or polling.
 * </p>
 *
 * <p>
 * The class defines two special return values:
 * </p>
 * <ul>
 *   <li>{@link #EOF} (-1) - Indicates that the end of the stream has been reached</li>
 *   <li>{@link #READ_EXPIRED} (-2) - Indicates that the read operation timed out</li>
 * </ul>
 *
 * <p>
 * Implementations of this class typically use a separate thread to handle
 * blocking I/O operations, allowing the main thread to continue execution.
 * The {@link #shutdown()} method can be used to terminate this background
 * thread when it is no longer needed.
 * </p>
 */
public abstract class NonBlockingReader extends Reader {

    /**
     * Default constructor.
     * Initializes close mode based on the current value of the system property.
     */
    public NonBlockingReader() {
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

//    private static final Logger LOG = Logger.getLogger(NonBlockingReader.class.getName());

    /**
     * Flag indicating whether this reader has been closed.
     * Marked as volatile to ensure visibility across threads.
     */
    protected volatile boolean closed = false;

    /**
     * Flag to track if a warning has been logged for this reader.
     * Used to avoid log spam in warn mode.
     */
    private boolean warningLogged = false;

    /**
     * Close mode for this reader.
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
     * Checks if this reader has been closed.
     * <p>
     * In JLine 4.x, strict mode is enabled by default: when a closed reader is accessed,
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
     * @throws ClosedException if this reader has been closed and strict mode is enabled (default)
     */
    protected void checkClosed() throws IOException {
        if (closed) {
            switch (closeMode) {
                case STRICT:
                    throw new ClosedException();
                case WARN:
                    // Log warning only once per reader instance to avoid log spam
                    if (!warningLogged) {
//                        LOG.log(
//                                Level.WARNING,
//                                "Accessing a closed reader. "
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
     * Shuts down the thread that is handling blocking I/O.
     *
     * <p>
     * This method terminates the background thread that is used to handle
     * blocking I/O operations. This allows the application to clean up resources
     * and prevent thread leaks when the reader is no longer needed.
     * </p>
     *
     * <p>
     * Note that if the thread is currently blocked waiting for I/O, it will not
     * actually shut down until the I/O is received or the thread is interrupted.
     * In some implementations, this method may interrupt the thread to force it
     * to shut down immediately.
     * </p>
     *
     * <p>
     * After calling this method, the reader should not be used anymore, as
     * subsequent read operations may fail or block indefinitely.
     * </p>
     */
    public void shutdown() {}

    @Override
    public int read() throws IOException {
        return read(0L, false);
    }

    /**
     * Peeks to see if there is a byte waiting in the input stream without
     * actually consuming the byte.
     *
     * @param timeout The amount of time to wait, 0 == forever
     * @return -1 on eof, -2 if the timeout expired with no available input
     * or the character that was read (without consuming it).
     * @throws IOException if anything wrong happens
     */
    public int peek(long timeout) throws IOException {
        return read(timeout, true);
    }

    /**
     * Attempts to read a character from the input stream for a specific
     * period of time.
     *
     * @param timeout The amount of time to wait for the character
     * @return The character read, -1 if EOF is reached, or -2 if the
     * read timed out.
     * @throws IOException if anything wrong happens
     */
    public int read(long timeout) throws IOException {
        return read(timeout, false);
    }

    /**
     * This version of read() is very specific to jline's purposes, it
     * will always always return a single byte at a time, rather than filling
     * the entire buffer.
     * @param b the buffer
     * @param off the offset in the buffer
     * @param len the maximum number of chars to read
     * @throws IOException if anything wrong happens
     */
    @Override
    public int read(char[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int c = this.read(0L);

        if (c == EOF) {
            return EOF;
        }
        b[off] = (char) c;
        return 1;
    }

    public int readBuffered(char[] b) throws IOException {
        return readBuffered(b, 0L);
    }

    public int readBuffered(char[] b, long timeout) throws IOException {
        return readBuffered(b, 0, b.length, timeout);
    }

    public abstract int readBuffered(char[] b, int off, int len, long timeout) throws IOException;

    public int available() {
        return 0;
    }

    /**
     * Attempts to read a character from the input stream for a specific
     * period of time.
     * @param timeout The amount of time to wait for the character
     * @param isPeek <code>true</code>if the character read must not be consumed
     * @return The character read, -1 if EOF is reached, or -2 if the
     *   read timed out.
     * @throws IOException if anything wrong happens
     */
    protected abstract int read(long timeout, boolean isPeek) throws IOException;

    /**
     * Closes this reader and marks it as closed.
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
