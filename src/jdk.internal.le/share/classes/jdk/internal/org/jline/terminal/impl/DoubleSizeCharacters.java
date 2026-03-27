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

import jdk.internal.org.jline.terminal.Terminal;

/**
 * Utility class for handling double-size characters in terminals.
 *
 * <p>
 * Double-size characters are a feature supported by some terminals that allows
 * displaying text at double width and/or double height. This is useful for
 * creating banners, headers, or emphasizing important text.
 * </p>
 *
 * <p>
 * The implementation uses VT100-compatible escape sequences:
 * - ESC # 3: Double-height, double-width line (top half)
 * - ESC # 4: Double-height, double-width line (bottom half)
 * - ESC # 5: Single-width, single-height line (normal)
 * - ESC # 6: Double-width, single-height line
 * </p>
 */
public class DoubleSizeCharacters {

    /**
     * Creates a new DoubleSizeCharacters instance.
     */
    public DoubleSizeCharacters() {
        // Default constructor
    }

    // VT100 double-size character control sequences
    private static final String ESC = "\u001b";
    private static final String DOUBLE_HEIGHT_TOP = ESC + "#3";
    private static final String DOUBLE_HEIGHT_BOTTOM = ESC + "#4";
    private static final String NORMAL_SIZE = ESC + "#5";
    private static final String DOUBLE_WIDTH = ESC + "#6";

    /**
     * Enumeration of double-size character modes.
     */
    public enum Mode {
        /** Normal single-width, single-height characters */
        NORMAL,
        /** Double-width, single-height characters */
        DOUBLE_WIDTH,
        /** Double-width, double-height characters (top half) */
        DOUBLE_HEIGHT_TOP,
        /** Double-width, double-height characters (bottom half) */
        DOUBLE_HEIGHT_BOTTOM
    }

    /**
     * Checks if the terminal supports double-size characters.
     *
     * @param terminal the terminal to check
     * @return true if the terminal supports double-size characters, false otherwise
     */
    public static boolean isDoubleSizeSupported(Terminal terminal) {
        String terminalType = terminal.getType().toLowerCase(java.util.Locale.ROOT);

        // Most VT100-compatible terminals support double-size characters
        return terminalType.contains("xterm")
                || terminalType.contains("vt")
                || terminalType.contains("ansi")
                || terminalType.contains("screen")
                || terminalType.contains("tmux");
    }

    /**
     * Sets the double-size character mode for the current line.
     *
     * @param terminal the terminal to write to
     * @param mode the double-size mode to set
     * @throws IOException if an I/O error occurs
     */
    public static void setMode(Terminal terminal, Mode mode) throws IOException {
        String sequence;
        switch (mode) {
            case NORMAL:
                sequence = NORMAL_SIZE;
                break;
            case DOUBLE_WIDTH:
                sequence = DOUBLE_WIDTH;
                break;
            case DOUBLE_HEIGHT_TOP:
                sequence = DOUBLE_HEIGHT_TOP;
                break;
            case DOUBLE_HEIGHT_BOTTOM:
                sequence = DOUBLE_HEIGHT_BOTTOM;
                break;
            default:
                sequence = NORMAL_SIZE;
                break;
        }

        terminal.writer().print(sequence);
        terminal.writer().flush();
    }

    /**
     * Prints text in normal size.
     *
     * @param terminal the terminal to write to
     * @param text the text to print
     * @throws IOException if an I/O error occurs
     */
    public static void printNormal(Terminal terminal, String text) throws IOException {
        setMode(terminal, Mode.NORMAL);
        terminal.writer().println(text);
        terminal.writer().flush();
    }

    /**
     * Prints text in double width.
     *
     * @param terminal the terminal to write to
     * @param text the text to print
     * @throws IOException if an I/O error occurs
     */
    public static void printDoubleWidth(Terminal terminal, String text) throws IOException {
        setMode(terminal, Mode.DOUBLE_WIDTH);
        terminal.writer().println(text);
        terminal.writer().flush();
    }

    /**
     * Prints text in double height (both top and bottom halves).
     * This method automatically handles printing both the top and bottom halves
     * of double-height text.
     *
     * @param terminal the terminal to write to
     * @param text the text to print
     * @throws IOException if an I/O error occurs
     */
    public static void printDoubleHeight(Terminal terminal, String text) throws IOException {
        // Print top half
        setMode(terminal, Mode.DOUBLE_HEIGHT_TOP);
        terminal.writer().println(text);
        terminal.writer().flush();

        // Print bottom half
        setMode(terminal, Mode.DOUBLE_HEIGHT_BOTTOM);
        terminal.writer().println(text);
        terminal.writer().flush();

        // Reset to normal
        setMode(terminal, Mode.NORMAL);
    }

    /**
     * Creates a banner with the specified text using double-height characters.
     * This is useful for creating prominent headers or titles.
     *
     * @param terminal the terminal to write to
     * @param text the text for the banner
     * @param borderChar the character to use for the border (e.g., '*', '=', '-')
     * @throws IOException if an I/O error occurs
     */
    public static void printBanner(Terminal terminal, String text, char borderChar) throws IOException {
        if (!isDoubleSizeSupported(terminal)) {
            // Fall back to normal text with border
            String border = new String(new char[text.length() + 4]).replace('\0', borderChar);
            terminal.writer().println(border);
            terminal.writer().println(borderChar + " " + text + " " + borderChar);
            terminal.writer().println(border);
            terminal.writer().flush();
            return;
        }

        // Create border line
        String border = new String(new char[text.length() + 4]).replace('\0', borderChar);

        // Print top border in double width
        printDoubleWidth(terminal, border);

        // Print text in double height
        printDoubleHeight(terminal, borderChar + " " + text + " " + borderChar);

        // Print bottom border in double width
        printDoubleWidth(terminal, border);

        // Reset to normal
        setMode(terminal, Mode.NORMAL);
    }

    /**
     * Resets the terminal to normal character size.
     * This is useful to ensure the terminal is in a known state.
     *
     * @param terminal the terminal to reset
     * @throws IOException if an I/O error occurs
     */
    public static void reset(Terminal terminal) throws IOException {
        setMode(terminal, Mode.NORMAL);
    }
}
