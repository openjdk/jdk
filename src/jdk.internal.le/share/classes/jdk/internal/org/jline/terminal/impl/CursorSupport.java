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
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.internal.org.jline.terminal.Cursor;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.utils.Curses;
import jdk.internal.org.jline.utils.InfoCmp;

/**
 * Utility class for cursor position detection in terminals.
 *
 * <p>
 * The CursorSupport class provides functionality for determining the current
 * cursor position in a terminal. It uses terminal capabilities to request the
 * cursor position from the terminal and parse the response.
 * </p>
 *
 * <p>
 * This class is used internally by terminal implementations to implement the
 * {@link Terminal#getCursorPosition(IntConsumer)} method. It relies on specific
 * terminal capabilities (user6 and user7) that define the sequence to request
 * the cursor position and the format of the response.
 * </p>
 *
 * <p>
 * The cursor position detection works by:
 * </p>
 * <ol>
 *   <li>Sending a special escape sequence to the terminal (defined by user7 capability)</li>
 *   <li>Reading the terminal's response</li>
 *   <li>Parsing the response using a pattern derived from the user6 capability</li>
 *   <li>Extracting the row and column coordinates from the parsed response</li>
 * </ol>
 *
 * <p>
 * Note that cursor position reporting is not supported by all terminals, and
 * this method may return null if the terminal does not support this feature
 * or if an error occurs during the detection process.
 * </p>
 *
 * @see Terminal#getCursorPosition(IntConsumer)
 * @see Cursor
 */
public class CursorSupport {

    /**
     * Private constructor to prevent instantiation.
     */
    private CursorSupport() {
        // Utility class
    }

    /**
     * Gets the current cursor position from the terminal.
     *
     * <p>
     * This method sends a request to the terminal for its current cursor position
     * and parses the response to extract the coordinates. It uses the terminal's
     * user6 and user7 capabilities to determine the request sequence and response
     * format.
     * </p>
     *
     * <p>
     * The method reads from the terminal's input stream until it finds a response
     * that matches the expected pattern. Any characters read that are not part of
     * the cursor position response can be optionally collected through the
     * discarded consumer.
     * </p>
     *
     * @param terminal the terminal to get the cursor position from
     * @param discarded an optional consumer for characters read that are not part
     *                 of the cursor position response, or null if these characters
     *                 should be ignored
     * @return the cursor position, or null if the position could not be determined
     *         (e.g., if the terminal does not support cursor position reporting)
     */
    public static Cursor getCursorPosition(Terminal terminal, IntConsumer discarded) {
        try {
            String u6 = terminal.getStringCapability(InfoCmp.Capability.user6);
            String u7 = terminal.getStringCapability(InfoCmp.Capability.user7);
            if (u6 == null || u7 == null) {
                return null;
            }
            // Prepare parser
            boolean inc1 = false;
            StringBuilder patb = new StringBuilder();
            int index = 0;
            while (index < u6.length()) {
                char ch;
                switch (ch = u6.charAt(index++)) {
                    case '\\':
                        switch (u6.charAt(index++)) {
                            case 'e':
                            case 'E':
                                patb.append("\\x1b");
                                break;
                            default:
                                throw new IllegalArgumentException();
                        }
                        break;
                    case '%':
                        ch = u6.charAt(index++);
                        switch (ch) {
                            case '%':
                                patb.append('%');
                                break;
                            case 'i':
                                inc1 = true;
                                break;
                            case 'd':
                                patb.append("([0-9]+)");
                                break;
                            default:
                                throw new IllegalArgumentException();
                        }
                        break;
                    default:
                        switch (ch) {
                            case '[':
                                patb.append('\\');
                                break;
                        }
                        patb.append(ch);
                        break;
                }
            }
            Pattern pattern = Pattern.compile(patb.toString());
            // Output cursor position request
            Curses.tputs(terminal.writer(), u7);
            terminal.flush();
            StringBuilder sb = new StringBuilder();
            int start = 0;
            while (true) {
                int c = terminal.reader().read();
                if (c < 0) {
                    return null;
                }
                sb.append((char) c);
                Matcher matcher = pattern.matcher(sb.substring(start));
                if (matcher.matches()) {
                    int y = Integer.parseInt(matcher.group(1));
                    int x = Integer.parseInt(matcher.group(2));
                    if (inc1) {
                        x--;
                        y--;
                    }
                    if (discarded != null) {
                        for (int i = 0; i < start; i++) {
                            discarded.accept(sb.charAt(i));
                        }
                    }
                    return new Cursor(x, y);
                } else if (!matcher.hitEnd()) {
                    start++;
                }
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
    }
}
