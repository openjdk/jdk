/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import java.io.EOFException;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.IntSupplier;

import jdk.internal.org.jline.terminal.MouseEvent;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.utils.InfoCmp;
import jdk.internal.org.jline.utils.InfoCmp.Capability;
import jdk.internal.org.jline.utils.InputStreamReader;

/**
 * Utility class for mouse support in terminals.
 *
 * <p>
 * The MouseSupport class provides functionality for enabling, disabling, and
 * processing mouse events in terminals that support mouse tracking. It handles
 * the details of sending the appropriate escape sequences to the terminal to
 * enable different mouse tracking modes and parsing the responses to create
 * MouseEvent objects.
 * </p>
 *
 * <p>
 * This class is used internally by terminal implementations to implement the
 * mouse-related methods defined in the Terminal interface, such as
 * {@link Terminal#hasMouseSupport()}, {@link Terminal#trackMouse(Terminal.MouseTracking)},
 * and {@link Terminal#readMouseEvent()}.
 * </p>
 *
 * <p>
 * Mouse tracking in terminals typically works by:
 * </p>
 * <ol>
 *   <li>Sending special escape sequences to enable a specific mouse tracking mode</li>
 *   <li>Receiving escape sequences from the terminal when mouse events occur</li>
 *   <li>Parsing these sequences to extract information about the event type, button, modifiers, and coordinates</li>
 *   <li>Creating MouseEvent objects that represent these events</li>
 * </ol>
 *
 * <p>
 * Note that mouse support is not available in all terminals, and the methods in
 * this class may not work correctly if the terminal does not support mouse tracking.
 * </p>
 *
 * @see Terminal#hasMouseSupport()
 * @see Terminal#trackMouse(Terminal.MouseTracking)
 * @see Terminal#readMouseEvent()
 * @see MouseEvent
 */
public class MouseSupport {

    /**
     * Private constructor to prevent instantiation.
     */
    private MouseSupport() {
        // Utility class
    }

    /**
     * Checks if the terminal supports mouse tracking.
     *
     * <p>
     * This method determines whether the terminal supports mouse tracking by
     * checking if it has the key_mouse capability. This capability is required
     * for mouse tracking to work correctly.
     * </p>
     *
     * @param terminal the terminal to check
     * @return {@code true} if the terminal supports mouse tracking, {@code false} otherwise
     */
    public static boolean hasMouseSupport(Terminal terminal) {
        return terminal.getStringCapability(InfoCmp.Capability.key_mouse) != null;
    }

    /**
     * Enables or disables mouse tracking in the terminal.
     *
     * <p>
     * This method sends the appropriate escape sequences to the terminal to
     * enable or disable mouse tracking according to the specified tracking mode.
     * The available tracking modes are:
     * </p>
     * <ul>
     *   <li>{@link Terminal.MouseTracking#Off} - Disables mouse tracking</li>
     *   <li>{@link Terminal.MouseTracking#Normal} - Reports button press and release events</li>
     *   <li>{@link Terminal.MouseTracking#Button} - Reports button press, release, and motion events while buttons are pressed</li>
     *   <li>{@link Terminal.MouseTracking#Any} - Reports all mouse events, including movement without buttons pressed</li>
     * </ul>
     *
     * <p>
     * This implementation enables multiple mouse modes (1005, 1006, and basic) by default,
     * which provides maximum compatibility across different terminals. The terminal will
     * use the most advanced mode it supports:
     * </p>
     * <ul>
     *   <li>SGR mode (1006) - For terminals that support explicit release events</li>
     *   <li>UTF-8 mode (1005) - For terminals that need to report coordinates > 223</li>
     *   <li>Basic mode (1000) - For basic mouse event reporting</li>
     * </ul>
     *
     * <p>
     * When disabling mouse tracking, all modes are disabled to ensure a clean state.
     * </p>
     *
     * @param terminal the terminal to configure
     * @param tracking the mouse tracking mode to enable
     * @return {@code true} if mouse tracking is supported and was configured, {@code false} otherwise
     */
    public static boolean trackMouse(Terminal terminal, Terminal.MouseTracking tracking) {
        if (hasMouseSupport(terminal)) {
            switch (tracking) {
                case Off:
                    terminal.writer()
                            .write("\033[?1000l\033[?1002l\033[?1003l\033[?1005l\033[?1006l\033[?1015l\033[?1016l");
                    break;
                case Normal:
                    terminal.writer().write("\033[?1005h\033[?1006h\033[?1000h");
                    break;
                case Button:
                    terminal.writer().write("\033[?1005h\033[?1006h\033[?1002h");
                    break;
                case Any:
                    terminal.writer().write("\033[?1005h\033[?1006h\033[?1003h");
                    break;
            }
            terminal.flush();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Reads a mouse event from the terminal.
     *
     * <p>
     * This method reads a mouse event from the terminal's input stream and
     * converts it into a MouseEvent object. It uses the previous mouse event
     * to determine the type of the new event (e.g., to distinguish between
     * press, drag, and release events).
     * </p>
     *
     * @param terminal the terminal to read from
     * @param last the previous mouse event, used to determine the type of the new event
     * @return the mouse event that was read
     */
    public static MouseEvent readMouse(Terminal terminal, MouseEvent last) {
        return readMouse(() -> readExt(terminal), last, null);
    }

    /**
     * Reads a mouse event from the terminal with a prefix that has already been consumed.
     *
     * <p>
     * This method is similar to {@link #readMouse(Terminal, MouseEvent)}, but it
     * allows specifying a prefix that has already been consumed. This is useful when
     * the mouse event prefix (e.g., "\033[<" or "\033[M") has been consumed by
     * the key binding detection, and we need to continue parsing from the current position.
     * </p>
     *
     * @param terminal the terminal to read from
     * @param last the previous mouse event, used to determine the type of the new event
     * @param prefix the prefix that has already been consumed, or null if none
     * @return the mouse event that was read
     */
    public static MouseEvent readMouse(Terminal terminal, MouseEvent last, String prefix) {
        return readMouse(() -> readExt(terminal), last, prefix);
    }

    /**
     * Reads a mouse event using the provided input supplier.
     *
     * <p>
     * This method reads a mouse event using the provided input supplier and
     * converts it into a MouseEvent object. It uses the previous mouse event
     * to determine the type of the new event (e.g., to distinguish between
     * press, drag, and release events).
     * </p>
     *
     * <p>
     * The input supplier should provide the raw bytes of the mouse event data.
     * This method expects the data to be in the format used by xterm-compatible
     * terminals for mouse reporting.
     * </p>
     *
     * @param reader the input supplier to read from
     * @param last the previous mouse event, used to determine the type of the new event
     * @return the mouse event that was read
     *
     * <p>
     * This implementation supports multiple mouse event formats:
     * </p>
     * <ul>
     *   <li>X10 format (default) - Basic mouse reporting</li>
     *   <li>UTF-8 format (1005) - Extended mouse reporting with UTF-8 encoded coordinates</li>
     *   <li>SGR format (1006) - Extended mouse reporting with explicit release events</li>
     *   <li>URXVT format (1015) - Extended mouse reporting with decimal coordinates</li>
     *   <li>SGR-Pixels format (1016) - Like SGR but reports position in pixels</li>
     * </ul>
     */
    public static MouseEvent readMouse(IntSupplier reader, MouseEvent last) {
        return readMouse(reader, last, null);
    }

    /**
     * Reads a mouse event using the provided input supplier with a prefix that has already been consumed.
     *
     * <p>
     * This method is similar to {@link #readMouse(IntSupplier, MouseEvent)}, but it
     * allows specifying a prefix that has already been consumed. This is useful when
     * the mouse event prefix (e.g., "\033[<" or "\033[M") has been consumed by
     * the key binding detection, and we need to continue parsing from the current position.
     * </p>
     *
     * @param reader the input supplier to read from
     * @param last the previous mouse event, used to determine the type of the new event
     * @param prefix the prefix that has already been consumed, or null if none
     * @return the mouse event that was read
     */
    public static MouseEvent readMouse(IntSupplier reader, MouseEvent last, String prefix) {
        // If a prefix was provided, create a reader that first returns the prefix characters
        // and then reads from the original reader
        if (prefix != null && !prefix.isEmpty()) {
            IntSupplier prefixReader;
            if (prefix.equals("\033[<")) {
                // SGR format
                prefixReader = createReaderFromString("<");
                return readMouse(chainReaders(prefixReader, reader), last, null);
            } else if (prefix.equals("\033[M")) {
                // X10 or UTF-8 format
                prefixReader = createReaderFromString("M");
                return readMouse(chainReaders(prefixReader, reader), last, null);
            }
        }

        int c = reader.getAsInt();

        // Detect the mouse event format based on the first character
        if (c == '<') {
            // SGR (1006) or SGR-Pixels (1016) format
            return readMouseSGR(reader, last);
        } else if (c >= '0' && c <= '9') {
            // URXVT (1015) format
            return readMouseURXVT(c, reader, last);
        } else if (c == 'M') {
            // This is the ESC [ M prefix, now we need to read the next byte to determine
            // if it's basic X10 or UTF-8 encoded (1005)
            int cb = reader.getAsInt();
            // Read the next two characters to determine if they're UTF-8 encoded
            int cx = reader.getAsInt();
            int cy = reader.getAsInt();

            // Check if this is likely UTF-8 encoded (1005)
            // In UTF-8 mode, coordinates > 95 will be encoded as multibyte sequences
            // which means their first byte will have the high bit set
            if ((cx & 0x80) != 0 || (cy & 0x80) != 0) {
                return readMouseUTF8(cb, cx, cy, reader, last);
            } else {
                // Basic X10 format
                return readMouseX10(cb - ' ', cx - ' ' - 1, cy - ' ' - 1, last);
            }
        } else {
            // X10 format (default) - first byte is the button code
            return readMouseX10(c - ' ', reader, last);
        }
    }

    /**
     * Reads a mouse event in X10 format.
     *
     * @param cb the button code (already read)
     * @param reader the input supplier to read from
     * @param last the previous mouse event
     * @return the mouse event that was read
     */
    private static MouseEvent readMouseX10(int cb, IntSupplier reader, MouseEvent last) {
        int cx = reader.getAsInt() - ' ' - 1;
        int cy = reader.getAsInt() - ' ' - 1;
        return parseMouseEvent(cb, cx, cy, false, last);
    }

    /**
     * Reads a mouse event in X10 format with pre-read coordinates.
     *
     * @param cb the button code (already read)
     * @param cx the x coordinate (already read and processed)
     * @param cy the y coordinate (already read and processed)
     * @param last the previous mouse event
     * @return the mouse event that was read
     */
    private static MouseEvent readMouseX10(int cb, int cx, int cy, MouseEvent last) {
        return parseMouseEvent(cb, cx, cy, false, last);
    }

    /**
     * Reads a mouse event in UTF-8 format (1005).
     * In this format, coordinates are UTF-8 encoded to support values > 223.
     *
     * @param cb the button code (already read)
     * @param cx the first byte of the x coordinate (already read)
     * @param cy the first byte of the y coordinate (already read)
     * @param reader the input supplier to read from
     * @param last the previous mouse event
     * @return the mouse event that was read
     */
    private static MouseEvent readMouseUTF8(int cb, int cx, int cy, IntSupplier reader, MouseEvent last) {
        // Decode the UTF-8 encoded coordinates
        int x = decodeUtf8Coordinate(cx, reader);
        int y = decodeUtf8Coordinate(cy, reader);

        // Adjust coordinates (they're 1-based in the protocol)
        x = x - 1;
        y = y - 1;

        return parseMouseEvent(cb - ' ', x, y, false, last);
    }

    /**
     * Decodes a UTF-8 encoded coordinate value.
     *
     * @param firstByte the first byte of the UTF-8 encoded value
     * @param reader the input supplier to read additional bytes if needed
     * @return the decoded coordinate value
     */
    private static int decodeUtf8Coordinate(int firstByte, IntSupplier reader) {
        // UTF-8 encoding rules:
        // 0xxxxxxx - Single byte (values 0-127)
        // 110xxxxx 10xxxxxx - Two bytes (values 128-2047)
        // 1110xxxx 10xxxxxx 10xxxxxx - Three bytes (values 2048-65535)

        if ((firstByte & 0x80) == 0) {
            // Single byte (0xxxxxxx)
            return firstByte - 32; // Subtract 32 as per mouse protocol
        } else if ((firstByte & 0xE0) == 0xC0) {
            // Two bytes (110xxxxx 10xxxxxx)
            int secondByte = reader.getAsInt();
            int value = ((firstByte & 0x1F) << 6) | (secondByte & 0x3F);
            return value - 32;
        } else if ((firstByte & 0xF0) == 0xE0) {
            // Three bytes (1110xxxx 10xxxxxx 10xxxxxx)
            int secondByte = reader.getAsInt();
            int thirdByte = reader.getAsInt();
            int value = ((firstByte & 0x0F) << 12) | ((secondByte & 0x3F) << 6) | (thirdByte & 0x3F);
            return value - 32;
        }

        // Fallback for invalid UTF-8 sequence
        return firstByte - 32;
    }

    /**
     * Reads a mouse event in SGR format (1006 or 1016).
     * Format: CSI < Cb ; Cx ; Cy M/m
     *
     * <p>
     * This method handles both standard SGR format (1006) and SGR-Pixels format (1016).
     * In SGR format, coordinates are reported in character cells.
     * In SGR-Pixels format, coordinates are reported in pixels.
     * </p>
     *
     * <p>
     * Currently, the MouseEvent class doesn't distinguish between cell and pixel
     * coordinates, so both formats are treated the same. In the future, this could
     * be enhanced to provide different handling for pixel coordinates.
     * </p>
     *
     * @param reader the input supplier to read from
     * @param last the previous mouse event
     * @return the mouse event that was read
     */
    private static MouseEvent readMouseSGR(IntSupplier reader, MouseEvent last) {
        StringBuilder sb = new StringBuilder();
        int[] params = new int[3];
        int paramIndex = 0;
        boolean isPixels = false;
        boolean isRelease = false;

        // Read parameters until 'M' or 'm' is encountered
        int c;
        while ((c = reader.getAsInt()) != -1) {
            if (c == 'M' || c == 'm') {
                isRelease = (c == 'm');
                break;
            } else if (c == ';') {
                if (paramIndex < params.length) {
                    try {
                        params[paramIndex++] = Integer.parseInt(sb.toString());
                    } catch (NumberFormatException e) {
                        // Invalid parameter, use default
                        params[paramIndex++] = 0;
                    }
                    sb.setLength(0);
                }
            } else if (c >= '0' && c <= '9') {
                sb.append((char) c);
            }
        }

        // Parse the last parameter if any
        if (sb.length() > 0 && paramIndex < params.length) {
            try {
                params[paramIndex] = Integer.parseInt(sb.toString());
            } catch (NumberFormatException e) {
                // Invalid parameter, use default
                params[paramIndex] = 0;
            }
        }

        int cb = params[0];
        int cx = params[1] - 1; // Convert to 0-based
        int cy = params[2] - 1; // Convert to 0-based

        // Check if this is SGR-Pixels format (1016)
        // The button code in SGR-Pixels mode is the same as in SGR mode
        // The only difference is that coordinates are reported in pixels
        // Currently, we don't distinguish between cell and pixel coordinates
        // in the MouseEvent class, so we treat them the same

        return parseMouseEvent(cb, cx, cy, isRelease, last);
    }

    /**
     * Reads a mouse event in URXVT format (1015).
     * Format: CSI Cb ; Cx ; Cy M
     *
     * @param firstDigit the first digit of the button code (already read)
     * @param reader the input supplier to read from
     * @param last the previous mouse event
     * @return the mouse event that was read
     */
    private static MouseEvent readMouseURXVT(int firstDigit, IntSupplier reader, MouseEvent last) {
        StringBuilder sb = new StringBuilder().append((char) firstDigit);
        int[] params = new int[3];
        int paramIndex = 0;

        // Read parameters until 'M' is encountered
        int c;
        while ((c = reader.getAsInt()) != -1) {
            if (c == 'M') {
                break;
            } else if (c == ';') {
                if (paramIndex < params.length) {
                    try {
                        params[paramIndex++] = Integer.parseInt(sb.toString());
                    } catch (NumberFormatException e) {
                        // Invalid parameter, use default
                        params[paramIndex++] = 0;
                    }
                    sb.setLength(0);
                }
            } else if (c >= '0' && c <= '9') {
                sb.append((char) c);
            }
        }

        // Parse the last parameter if any
        if (sb.length() > 0 && paramIndex < params.length) {
            try {
                params[paramIndex] = Integer.parseInt(sb.toString());
            } catch (NumberFormatException e) {
                // Invalid parameter, use default
                params[paramIndex] = 0;
            }
        }

        int cb = params[0];
        int cx = params[1] - 1; // Convert to 0-based
        int cy = params[2] - 1; // Convert to 0-based

        return parseMouseEvent(cb, cx, cy, false, last);
    }

    /**
     * Parses a mouse event from the given parameters.
     *
     * @param cb the button code
     * @param cx the x coordinate
     * @param cy the y coordinate
     * @param isRelease whether this is an explicit release event (SGR format)
     * @param last the previous mouse event
     * @return the parsed mouse event
     */
    private static MouseEvent parseMouseEvent(int cb, int cx, int cy, boolean isRelease, MouseEvent last) {
        MouseEvent.Type type;
        MouseEvent.Button button;
        EnumSet<MouseEvent.Modifier> modifiers = EnumSet.noneOf(MouseEvent.Modifier.class);

        // Parse modifiers
        if ((cb & 4) == 4) {
            modifiers.add(MouseEvent.Modifier.Shift);
        }
        if ((cb & 8) == 8) {
            modifiers.add(MouseEvent.Modifier.Alt);
        }
        if ((cb & 16) == 16) {
            modifiers.add(MouseEvent.Modifier.Control);
        }

        // Handle wheel events
        if ((cb & 64) == 64) {
            type = MouseEvent.Type.Wheel;
            button = (cb & 1) == 1 ? MouseEvent.Button.WheelDown : MouseEvent.Button.WheelUp;
        } else {
            // Handle button events
            if (isRelease) {
                // Explicit release event (SGR format)
                button = getButtonForCode(cb & 3);
                type = MouseEvent.Type.Released;
            } else {
                int b = (cb & 3);
                switch (b) {
                    case 0:
                        button = MouseEvent.Button.Button1;
                        if (last.getButton() == button
                                && (last.getType() == MouseEvent.Type.Pressed
                                        || last.getType() == MouseEvent.Type.Dragged)) {
                            type = MouseEvent.Type.Dragged;
                        } else {
                            type = MouseEvent.Type.Pressed;
                        }
                        break;
                    case 1:
                        button = MouseEvent.Button.Button2;
                        if (last.getButton() == button
                                && (last.getType() == MouseEvent.Type.Pressed
                                        || last.getType() == MouseEvent.Type.Dragged)) {
                            type = MouseEvent.Type.Dragged;
                        } else {
                            type = MouseEvent.Type.Pressed;
                        }
                        break;
                    case 2:
                        button = MouseEvent.Button.Button3;
                        if (last.getButton() == button
                                && (last.getType() == MouseEvent.Type.Pressed
                                        || last.getType() == MouseEvent.Type.Dragged)) {
                            type = MouseEvent.Type.Dragged;
                        } else {
                            type = MouseEvent.Type.Pressed;
                        }
                        break;
                    default:
                        if (last.getType() == MouseEvent.Type.Pressed || last.getType() == MouseEvent.Type.Dragged) {
                            button = last.getButton();
                            type = MouseEvent.Type.Released;
                        } else {
                            button = MouseEvent.Button.NoButton;
                            type = MouseEvent.Type.Moved;
                        }
                        break;
                }
            }
        }

        return new MouseEvent(type, button, modifiers, cx, cy);
    }

    /**
     * Gets the button for the given button code.
     *
     * @param code the button code
     * @return the corresponding button
     */
    private static MouseEvent.Button getButtonForCode(int code) {
        switch (code) {
            case 0:
                return MouseEvent.Button.Button1;
            case 1:
                return MouseEvent.Button.Button2;
            case 2:
                return MouseEvent.Button.Button3;
            default:
                return MouseEvent.Button.NoButton;
        }
    }

    /**
     * Returns a list of key sequences that could be used for mouse events
     * based on the current mouse mode configuration.
     *
     * <p>
     * This method returns the possible prefixes for mouse events that applications
     * should recognize. This is useful for applications that need to handle mouse
     * events but don't want to rely on the terminal's kmous capability, which
     * might not accurately reflect the actual mouse mode being used.
     * </p>
     *
     * @return array of possible mouse event prefixes
     */
    public static String[] keys() {
        // Return all possible mouse event prefixes
        return new String[] {
            "\033[<", // SGR format (1006)
            "\033[M" // Basic (1000) and UTF-8 (1005) formats
        };
    }

    /**
     * Returns a list of key sequences that could be used for mouse events,
     * including the terminal's key_mouse capability if available.
     *
     * <p>
     * This method combines the standard mouse event prefixes with the terminal's
     * key_mouse capability. This is useful for applications that need to bind
     * all possible mouse event sequences to ensure compatibility across different
     * terminals.
     * </p>
     *
     * @param terminal the terminal to get the key_mouse capability from
     * @return array of possible mouse event prefixes including the terminal's key_mouse capability
     */
    public static String[] keys(Terminal terminal) {
        String keyMouse = terminal.getStringCapability(Capability.key_mouse);
        if (keyMouse != null) {
            // Check if keyMouse is one of our standard prefixes
            if (Arrays.asList(keys()).contains(keyMouse)) {
                // If it's already in our standard prefixes, just return those
                return keys();
            }
            // Include the terminal's key_mouse capability if it's not already in our standard prefixes
            return new String[] {
                keyMouse, // Terminal's key_mouse capability
                "\033[<", // SGR format (1006 and 1016)
                "\033[M" // Basic (1000) and UTF-8 (1005) formats
            };
        } else {
            // Just return the standard prefixes if key_mouse is not available
            return keys();
        }
    }

    /**
     * Reads a single character from the terminal's input stream.
     *
     * <p>
     * This method reads a single character from the terminal's input stream,
     * handling the case where the terminal's encoding is not UTF-8. Mouse events
     * are encoded in UTF-8, so if the terminal is using a different encoding,
     * this method creates a temporary UTF-8 reader to read the character.
     * </p>
     *
     * @param terminal the terminal to read from
     * @return the character that was read
     * @throws IOError if an I/O error occurs while reading
     */
    private static int readExt(Terminal terminal) {
        try {
            // The coordinates are encoded in UTF-8, so if that's not the input encoding,
            // we need to get around
            int c;
            if (terminal.encoding() != StandardCharsets.UTF_8) {
                c = new InputStreamReader(terminal.input(), StandardCharsets.UTF_8).read();
            } else {
                c = terminal.reader().read();
            }
            if (c < 0) {
                throw new EOFException();
            }
            return c;
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    /**
     * Creates a reader from a string.
     *
     * @param s the string to read from
     * @return an IntSupplier that reads from the string
     */
    private static IntSupplier createReaderFromString(String s) {
        final int[] chars = s.chars().toArray();
        final int[] index = {0};

        return () -> {
            if (index[0] < chars.length) {
                return chars[index[0]++];
            }
            return -1;
        };
    }

    /**
     * Chains two readers together, reading from the first reader until it's exhausted,
     * then reading from the second reader.
     *
     * @param first the first reader to read from
     * @param second the second reader to read from after the first is exhausted
     * @return an IntSupplier that reads from both readers in sequence
     */
    private static IntSupplier chainReaders(IntSupplier first, IntSupplier second) {
        return new IntSupplier() {
            private boolean firstExhausted = false;

            @Override
            public int getAsInt() {
                if (!firstExhausted) {
                    int c = first.getAsInt();
                    if (c != -1) {
                        return c;
                    }
                    firstExhausted = true;
                }
                return second.getAsInt();
            }
        };
    }
}
