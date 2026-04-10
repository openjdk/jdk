/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import java.io.IOError;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Cursor;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.spi.Pty;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.NonBlockingReader;

/**
 * Base implementation for terminals on POSIX-compliant systems.
 *
 * <p>
 * The AbstractPosixTerminal class provides a foundation for terminal implementations
 * on POSIX-compliant systems such as Linux, macOS, and other Unix-like operating
 * systems. It builds on the AbstractTerminal class and adds POSIX-specific
 * functionality, particularly related to pseudoterminal (PTY) handling.
 * </p>
 *
 * <p>
 * This class manages the interaction with the underlying PTY, handling terminal
 * attributes, size changes, and other POSIX-specific terminal operations. It
 * provides implementations for many of the abstract methods defined in
 * AbstractTerminal, leaving only a few methods to be implemented by concrete
 * subclasses.
 * </p>
 *
 * <p>
 * Key features provided by this class include:
 * </p>
 * <ul>
 *   <li>PTY management and interaction</li>
 *   <li>Terminal attribute preservation and restoration</li>
 *   <li>Size handling and window change signals</li>
 *   <li>Cursor position detection</li>
 * </ul>
 *
 * <p>
 * This class is designed to be extended by concrete implementations that target
 * specific POSIX platforms or environments.
 * </p>
 *
 * @see org.jline.terminal.impl.AbstractTerminal
 * @see org.jline.terminal.spi.Pty
 */
public abstract class AbstractPosixTerminal extends AbstractTerminal {

    protected final Pty pty;
    protected final Attributes originalAttributes;

    public AbstractPosixTerminal(String name, String type, Pty pty) throws IOException {
        this(name, type, pty, null, SignalHandler.SIG_DFL);
    }

    public AbstractPosixTerminal(String name, String type, Pty pty, Charset encoding, SignalHandler signalHandler)
            throws IOException {
        this(name, type, pty, encoding, encoding, encoding, signalHandler);
    }

    public AbstractPosixTerminal(
            String name,
            String type,
            Pty pty,
            Charset encoding,
            Charset inputEncoding,
            Charset outputEncoding,
            SignalHandler signalHandler)
            throws IOException {
        super(name, type, encoding, inputEncoding, outputEncoding, signalHandler);
        Objects.requireNonNull(pty);
        this.pty = pty;
        this.originalAttributes = this.pty.getAttr();
    }

    public Pty getPty() {
        return pty;
    }

    public Attributes getAttributes() {
        checkClosed();
        try {
            return pty.getAttr();
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public void setAttributes(Attributes attr) {
        checkClosed();
        try {
            pty.setAttr(attr);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public Size getSize() {
        checkClosed();
        try {
            return pty.getSize();
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public void setSize(Size size) {
        checkClosed();
        try {
            pty.setSize(size);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    protected void doClose() throws IOException {
        super.doClose();
        pty.setAttr(originalAttributes);
        pty.close();
    }

    @Override
    public Cursor getCursorPosition(IntConsumer discarded) {
        return CursorSupport.getCursorPosition(this, discarded);
    }

    @Override
    public TerminalProvider getProvider() {
        return getPty().getProvider();
    }

    @Override
    public SystemStream getSystemStream() {
        return getPty().getSystemStream();
    }

    @Override
    public String toString() {
        return getKind() + "[" + "name='"
                + name + '\'' + ", pty='"
                + pty + '\'' + ", type='"
                + type + '\'' + ", size='"
                + getSize() + '\'' + ']';
    }

    @Override
    public int getDefaultForegroundColor() {
        try {
            // Send OSC 10 query
            writer().write("\033]10;?\033\\");
            writer().flush();

            // Read response
            return parseColorResponse(reader(), 10);
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public int getDefaultBackgroundColor() {
        try {
            // Send OSC 11 query
            writer().write("\033]11;?\033\\");
            writer().flush();

            // Read response
            return parseColorResponse(reader(), 11);
        } catch (IOException e) {
            return -1;
        }
    }

    int parseColorResponse(NonBlockingReader reader, int colorType) throws IOException {
        if (reader.peek(50) < 0) {
            return -1;
        }

        if (!readOscHeader(reader, colorType)) {
            return -1;
        }

        List<String> rgb = readRgbValues(reader);
        if (rgb.size() != 3) {
            return -1;
        }

        return convertRgbToInt(rgb);
    }

    /**
     * Reads and validates the OSC header: ESC ] {colorType} ; rgb:
     */
    private boolean readOscHeader(NonBlockingReader reader, int colorType) throws IOException {
        // Check for OSC sequence start
        if (reader.read(10) != '\033' || reader.read(10) != ']') {
            return false;
        }

        // Check for color type (10 or 11)
        int tens = reader.read(10);
        int ones = reader.read(10);
        if (tens != '1' || (ones != '0' && ones != '1')) {
            return false;
        }

        // Check that the type matches what we expect
        int type = (ones - '0') + 10;
        if (type != colorType) {
            return false;
        }

        // Check for separator
        if (reader.read(10) != ';') {
            return false;
        }

        // Check for rgb: format
        return reader.read(10) == 'r' && reader.read(10) == 'g' && reader.read(10) == 'b' && reader.read(10) == ':';
    }

    /**
     * Reads RGB hex values separated by '/' and terminated by BEL or ST.
     * Returns an empty list on EOF, timeout, or invalid input.
     */
    private List<String> readRgbValues(NonBlockingReader reader) throws IOException {
        StringBuilder sb = new StringBuilder(16);
        List<String> rgb = new ArrayList<>();
        while (true) {
            int c = reader.read(10);
            if (c == -1) {
                return Collections.emptyList(); // EOF — stream closed
            }
            if (c == NonBlockingReader.READ_EXPIRED) {
                return Collections.emptyList(); // timeout — terminal not responding within probe window
            }
            if (c == '\007') {
                rgb.add(sb.toString());
                return rgb;
            }
            if (c == '\033') {
                return readStTerminator(reader) ? addAndReturn(rgb, sb.toString()) : Collections.emptyList();
            }
            if (isHexChar(c)) {
                sb.append((char) c);
            } else if (c == '/') {
                rgb.add(sb.toString());
                sb.setLength(0);
            }
        }
    }

    private boolean readStTerminator(NonBlockingReader reader) throws IOException {
        return reader.read(10) == '\\';
    }

    private static List<String> addAndReturn(List<String> list, String value) {
        list.add(value);
        return list;
    }

    private static boolean isHexChar(int c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * Converts a list of three hex RGB strings to a single packed int (0xRRGGBB).
     */
    private static int convertRgbToInt(List<String> rgb) {
        double r = Integer.parseInt(rgb.get(0), 16) / ((1 << (4 * rgb.get(0).length())) - 1.0);
        double g = Integer.parseInt(rgb.get(1), 16) / ((1 << (4 * rgb.get(1).length())) - 1.0);
        double b = Integer.parseInt(rgb.get(2), 16) / ((1 << (4 * rgb.get(2).length())) - 1.0);

        return (int) ((Math.round(r * 255) << 16) + (Math.round(g * 255) << 8) + Math.round(b * 255));
    }
}
