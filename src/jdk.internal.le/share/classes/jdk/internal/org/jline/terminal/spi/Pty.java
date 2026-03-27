/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.spi;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;

/**
 * Represents a pseudoterminal (PTY) that provides terminal emulation.
 *
 * <p>
 * A pseudoterminal (PTY) is a pair of virtual character devices that provide a bidirectional
 * communication channel. The PTY consists of a master side and a slave side. The slave side
 * appears as a terminal device to processes, while the master side is used by terminal emulators
 * to control the slave side.
 * </p>
 *
 * <p>
 * This interface provides methods to access the input and output streams for both the master
 * and slave sides of the PTY, as well as methods to get and set terminal attributes and size.
 * </p>
 *
 * <p>
 * PTY implementations are typically platform-specific and may use different underlying
 * mechanisms depending on the operating system (e.g., /dev/pts on Unix-like systems).
 * </p>
 *
 * @see java.io.Closeable
 */
public interface Pty extends Closeable {

    /**
     * Returns the input stream for the master side of the PTY.
     *
     * <p>
     * This stream receives data that has been written to the slave's output stream.
     * Terminal emulators typically read from this stream to get the output from
     * processes running in the terminal.
     * </p>
     *
     * @return the master's input stream
     * @throws IOException if an I/O error occurs
     */
    InputStream getMasterInput() throws IOException;

    /**
     * Returns the output stream for the master side of the PTY.
     *
     * <p>
     * Data written to this stream will be available for reading from the slave's
     * input stream. Terminal emulators typically write to this stream to send input
     * to processes running in the terminal.
     * </p>
     *
     * @return the master's output stream
     * @throws IOException if an I/O error occurs
     */
    OutputStream getMasterOutput() throws IOException;

    /**
     * Returns the input stream for the slave side of the PTY.
     *
     * <p>
     * This stream receives data that has been written to the master's output stream.
     * Processes running in the terminal read from this stream to get their input.
     * </p>
     *
     * @return the slave's input stream
     * @throws IOException if an I/O error occurs
     */
    InputStream getSlaveInput() throws IOException;

    /**
     * Returns the output stream for the slave side of the PTY.
     *
     * <p>
     * Data written to this stream will be available for reading from the master's
     * input stream. Processes running in the terminal write to this stream to
     * produce their output.
     * </p>
     *
     * @return the slave's output stream
     * @throws IOException if an I/O error occurs
     */
    OutputStream getSlaveOutput() throws IOException;

    /**
     * Returns the current terminal attributes for this PTY.
     *
     * <p>
     * Terminal attributes control various aspects of terminal behavior, such as
     * echo settings, line discipline, and control characters.
     * </p>
     *
     * @return the current terminal attributes
     * @throws IOException if an I/O error occurs
     * @see org.jline.terminal.Attributes
     */
    Attributes getAttr() throws IOException;

    /**
     * Sets the terminal attributes for this PTY.
     *
     * <p>
     * This method allows changing various aspects of terminal behavior, such as
     * echo settings, line discipline, and control characters.
     * </p>
     *
     * @param attr the terminal attributes to set
     * @throws IOException if an I/O error occurs
     * @see org.jline.terminal.Attributes
     */
    void setAttr(Attributes attr) throws IOException;

    /**
     * Returns the current size (dimensions) of this PTY.
     *
     * <p>
     * The size includes the number of rows and columns in the terminal window.
     * </p>
     *
     * @return the current terminal size
     * @throws IOException if an I/O error occurs
     * @see org.jline.terminal.Size
     */
    Size getSize() throws IOException;

    /**
     * Sets the size (dimensions) of this PTY.
     *
     * <p>
     * This method changes the number of rows and columns in the terminal window.
     * When the size changes, a SIGWINCH signal is typically sent to processes
     * running in the terminal.
     * </p>
     *
     * @param size the new terminal size to set
     * @throws IOException if an I/O error occurs
     * @see org.jline.terminal.Size
     */
    void setSize(Size size) throws IOException;

    /**
     * Returns the system stream associated with this PTY, if any.
     *
     * <p>
     * The system stream indicates whether this PTY is connected to standard input,
     * standard output, or standard error.
     * </p>
     *
     * @return the associated system stream, or {@code null} if this PTY is not
     *         associated with a system stream
     * @see SystemStream
     */
    SystemStream getSystemStream();

    /**
     * Returns the terminal provider that created this PTY.
     *
     * <p>
     * The terminal provider is responsible for creating and managing terminal
     * instances on a specific platform.
     * </p>
     *
     * @return the terminal provider that created this PTY
     * @see TerminalProvider
     */
    TerminalProvider getProvider();
}
