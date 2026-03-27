/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import java.io.IOException;
import java.io.Writer;

/**
 * Base class for writing to Windows console.
 *
 * <p>
 * The AbstractWindowsConsoleWriter class provides a foundation for writing text
 * to the Windows console. It extends the standard Writer class and handles the
 * common aspects of writing to the console, while leaving the actual console
 * interaction to be implemented by concrete subclasses.
 * </p>
 *
 * <p>
 * This class is necessary because standard Java output streams don't work well
 * with the Windows console, particularly for non-ASCII characters and color output.
 * Instead of using standard output streams, Windows terminal implementations use
 * this writer to directly interact with the Windows console API.
 * </p>
 *
 * <p>
 * Concrete subclasses must implement the {@link #writeConsole(char[], int)} method
 * to perform the actual writing to the console using platform-specific mechanisms
 * (e.g., JNI, JNA, or FFM).
 * </p>
 *
 * @see java.io.Writer
 */
public abstract class AbstractWindowsConsoleWriter extends Writer {

    /**
     * Default constructor.
     */
    public AbstractWindowsConsoleWriter() {
        // Default constructor
    }

    /**
     * Writes text to the Windows console.
     *
     * <p>
     * This method must be implemented by concrete subclasses to perform the actual
     * writing to the Windows console using platform-specific mechanisms. The
     * implementation should handle proper encoding and display of characters,
     * including non-ASCII characters and ANSI escape sequences if supported.
     * </p>
     *
     * @param text the character array containing the text to write
     * @param len the number of characters to write
     * @throws IOException if an I/O error occurs
     */
    protected abstract void writeConsole(char[] text, int len) throws IOException;

    /**
     * Writes a portion of a character array to the Windows console.
     *
     * <p>
     * This method handles the common logic for writing to the console, including
     * creating a new character array if the offset is not zero and synchronizing
     * access to the console. The actual writing is delegated to the
     * {@link #writeConsole(char[], int)} method implemented by subclasses.
     * </p>
     *
     * @param cbuf the character array containing the text to write
     * @param off the offset from which to start reading characters
     * @param len the number of characters to write
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        char[] text = cbuf;
        if (off != 0) {
            text = new char[len];
            System.arraycopy(cbuf, off, text, 0, len);
        }

        synchronized (this.lock) {
            writeConsole(text, len);
        }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
}
