/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.internal.org.jline.terminal.Terminal;

/**
 * An immutable character sequence with ANSI style attributes.
 *
 * <p>
 * The AttributedString class represents a character sequence where each character
 * has associated style attributes (colors, bold, underline, etc.). It extends
 * AttributedCharSequence and provides an immutable implementation, making it safe
 * to use in concurrent contexts.
 * </p>
 *
 * <p>
 * Key features of this class include:
 * </p>
 * <ul>
 *   <li>Immutability - Once created, instances cannot be modified</li>
 *   <li>Memory efficiency - Substrings are created without any memory copy</li>
 *   <li>Rich styling - Support for foreground/background colors and text attributes</li>
 *   <li>Concatenation - Multiple AttributedStrings can be joined together</li>
 *   <li>Pattern matching - Regular expressions can be applied to find and extract parts</li>
 * </ul>
 *
 * <p>
 * This class is commonly used for displaying styled text in terminal applications,
 * such as prompts, menus, and highlighted output. For building AttributedStrings
 * dynamically, use {@link AttributedStringBuilder}.
 * </p>
 *
 * @see AttributedCharSequence
 * @see AttributedStringBuilder
 * @see AttributedStyle
 */
public class AttributedString extends AttributedCharSequence {

    final char[] buffer;
    final long[] style;
    final int start;
    final int end;
    /**
     * An empty AttributedString with no characters.
     */
    public static final AttributedString EMPTY = new AttributedString("");

    /**
     * An AttributedString containing only a newline character.
     */
    public static final AttributedString NEWLINE = new AttributedString("\n");

    /**
     * Creates a new AttributedString from the specified character sequence.
     *
     * <p>
     * This constructor creates a new AttributedString containing the characters
     * from the specified character sequence with default style (no attributes).
     * </p>
     *
     * @param str the character sequence to copy
     */
    public AttributedString(CharSequence str) {
        this(str, 0, str.length(), null);
    }

    /**
     * Creates a new AttributedString from a subsequence of the specified character sequence.
     *
     * <p>
     * This constructor creates a new AttributedString containing the characters
     * from the specified range of the character sequence with default style (no attributes).
     * </p>
     *
     * @param str the character sequence to copy
     * @param start the index of the first character to copy (inclusive)
     * @param end the index after the last character to copy (exclusive)
     * @throws InvalidParameterException if end is less than start
     */
    public AttributedString(CharSequence str, int start, int end) {
        this(str, start, end, null);
    }

    /**
     * Creates a new AttributedString from the specified character sequence with the specified style.
     *
     * <p>
     * This constructor creates a new AttributedString containing the characters
     * from the specified character sequence with the specified style applied to all characters.
     * </p>
     *
     * @param str the character sequence to copy
     * @param s the style to apply to all characters, or null for default style
     */
    public AttributedString(CharSequence str, AttributedStyle s) {
        this(str, 0, str.length(), s);
    }

    /**
     * Creates a new AttributedString from a subsequence of the specified character sequence
     * with the specified style.
     *
     * <p>
     * This constructor creates a new AttributedString containing the characters
     * from the specified range of the character sequence with the specified style
     * applied to all characters.
     * </p>
     *
     * <p>
     * If the character sequence is an AttributedString or AttributedStringBuilder,
     * this constructor preserves the existing style information and applies the
     * specified style on top of it (if not null).
     * </p>
     *
     * @param str the character sequence to copy
     * @param start the index of the first character to copy (inclusive)
     * @param end the index after the last character to copy (exclusive)
     * @param s the style to apply to all characters, or null to preserve existing styles
     * @throws InvalidParameterException if end is less than start
     */
    public AttributedString(CharSequence str, int start, int end, AttributedStyle s) {
        if (end < start) {
            throw new InvalidParameterException();
        }
        if (str instanceof AttributedString) {
            AttributedString as = (AttributedString) str;
            this.buffer = as.buffer;
            if (s != null) {
                this.style = as.style.clone();
                for (int i = 0; i < style.length; i++) {
                    this.style[i] = (this.style[i] & ~s.getMask()) | s.getStyle();
                }
            } else {
                this.style = as.style;
            }
            this.start = as.start + start;
            this.end = as.start + end;
        } else if (str instanceof AttributedStringBuilder) {
            AttributedStringBuilder asb = (AttributedStringBuilder) str;
            AttributedString as = asb.subSequence(start, end);
            this.buffer = as.buffer;
            this.style = as.style;
            if (s != null) {
                for (int i = 0; i < style.length; i++) {
                    this.style[i] = (this.style[i] & ~s.getMask()) | s.getStyle();
                }
            }
            this.start = as.start;
            this.end = as.end;
        } else {
            int l = end - start;
            buffer = new char[l];
            for (int i = 0; i < l; i++) {
                buffer[i] = str.charAt(start + i);
            }
            style = new long[l];
            if (s != null) {
                Arrays.fill(style, s.getStyle());
            }
            this.start = 0;
            this.end = l;
        }
    }

    /**
     * Creates a new AttributedString with the specified buffer, style, and range.
     *
     * <p>
     * This constructor is package-private and used internally for creating
     * AttributedString instances without copying the buffer and style arrays.
     * </p>
     *
     * @param buffer the character buffer
     * @param style the style buffer
     * @param start the start index in the buffers
     * @param end the end index in the buffers
     */
    AttributedString(char[] buffer, long[] style, int start, int end) {
        this.buffer = buffer;
        this.style = style;
        this.start = start;
        this.end = end;
    }

    /**
     * Creates an AttributedString from an ANSI-encoded string.
     *
     * <p>
     * This method parses the ANSI escape sequences in the input string and
     * creates an AttributedString with the corresponding styles. This is useful
     * for converting ANSI-colored output from external commands into styled
     * text that can be displayed in the terminal.
     * </p>
     *
     * @param ansi the ANSI-encoded string to parse
     * @return an AttributedString with styles based on the ANSI escape sequences
     * @see #fromAnsi(String, Terminal)
     */
    public static AttributedString fromAnsi(String ansi) {
        return fromAnsi(ansi, 0);
    }

    /**
     * Creates an AttributedString from an ANSI-encoded string with a specified tab size.
     *
     * <p>
     * This method parses the ANSI escape sequences in the input string and
     * creates an AttributedString with the corresponding styles. It also handles
     * tab characters according to the specified tab size.
     * </p>
     *
     * @param ansi the ANSI-encoded string to parse
     * @param tabs the tab size in columns
     * @return an AttributedString with styles based on the ANSI escape sequences
     */
    public static AttributedString fromAnsi(String ansi, int tabs) {
        return fromAnsi(ansi, Arrays.asList(tabs));
    }

    /**
     * Creates an AttributedString from an ANSI-encoded string with custom tab stops.
     *
     * <p>
     * This method parses the ANSI escape sequences in the input string and
     * creates an AttributedString with the corresponding styles. It also handles
     * tab characters according to the specified tab stops.
     * </p>
     *
     * @param ansi the ANSI-encoded string to parse
     * @param tabs the list of tab stop positions, or null for default tab stops
     * @return an AttributedString with styles based on the ANSI escape sequences
     */
    public static AttributedString fromAnsi(String ansi, List<Integer> tabs) {
        return fromAnsi(ansi, tabs, null, null);
    }

    /**
     * Creates an AttributedString from an ANSI-encoded string, using terminal capabilities.
     *
     * <p>
     * This method parses the ANSI escape sequences in the input string and
     * creates an AttributedString with the corresponding styles. It uses the
     * specified terminal's capabilities to handle alternate character sets.
     * </p>
     *
     * @param ansi the ANSI-encoded string to parse
     * @param terminal the terminal to use for capabilities, or null
     * @return an AttributedString with styles based on the ANSI escape sequences
     */
    public static AttributedString fromAnsi(String ansi, Terminal terminal) {
        String alternateIn, alternateOut;
        if (!DISABLE_ALTERNATE_CHARSET) {
            alternateIn = Curses.tputs(terminal.getStringCapability(InfoCmp.Capability.enter_alt_charset_mode));
            alternateOut = Curses.tputs(terminal.getStringCapability(InfoCmp.Capability.exit_alt_charset_mode));
        } else {
            alternateIn = null;
            alternateOut = null;
        }
        return fromAnsi(ansi, Arrays.asList(0), alternateIn, alternateOut);
    }

    /**
     * Creates an AttributedString from an ANSI-encoded string with custom tab stops
     * and alternate character set sequences.
     *
     * <p>
     * This method parses the ANSI escape sequences in the input string and
     * creates an AttributedString with the corresponding styles. It also handles
     * tab characters according to the specified tab stops and alternate character
     * set sequences.
     * </p>
     *
     * @param ansi the ANSI-encoded string to parse
     * @param tabs the list of tab stop positions, or null for default tab stops
     * @param altIn the sequence to enable the alternate character set, or null
     * @param altOut the sequence to disable the alternate character set, or null
     * @return an AttributedString with styles based on the ANSI escape sequences
     */
    public static AttributedString fromAnsi(String ansi, List<Integer> tabs, String altIn, String altOut) {
        if (ansi == null) {
            return null;
        }
        return new AttributedStringBuilder(ansi.length())
                .tabs(tabs)
                .altCharset(altIn, altOut)
                .ansiAppend(ansi)
                .toAttributedString();
    }

    /**
     * Strips ANSI escape sequences from a string.
     *
     * <p>
     * This method removes all ANSI escape sequences from the input string,
     * returning a plain text string with no styling information. This is useful
     * for extracting the text content from ANSI-colored output.
     * </p>
     *
     * @param ansi the ANSI-encoded string to strip
     * @return a plain text string with ANSI escape sequences removed, or null if the input is null
     */
    public static String stripAnsi(String ansi) {
        if (ansi == null) {
            return null;
        }
        return new AttributedStringBuilder(ansi.length()).ansiAppend(ansi).toString();
    }

    /**
     * Returns the character buffer for this attributed string.
     *
     * <p>
     * This method is used internally by the AttributedCharSequence implementation
     * to access the underlying character buffer.
     * </p>
     *
     * @return the character buffer
     */
    @Override
    protected char[] buffer() {
        return buffer;
    }

    /**
     * Returns the offset in the buffer where this attributed string starts.
     *
     * <p>
     * This method is used internally by the AttributedCharSequence implementation
     * to determine the starting position in the buffer.
     * </p>
     *
     * @return the offset in the buffer
     */
    @Override
    protected int offset() {
        return start;
    }

    /**
     * Returns the length of this attributed string.
     *
     * <p>
     * This method returns the number of characters in this attributed string.
     * </p>
     *
     * @return the number of characters in this attributed string
     */
    @Override
    public int length() {
        return end - start;
    }

    /**
     * Returns the style at the specified index in this attributed string.
     *
     * <p>
     * This method returns the AttributedStyle object associated with the
     * character at the specified index in this attributed string.
     * </p>
     *
     * @param index the index of the character whose style to return
     * @return the style at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    public AttributedStyle styleAt(int index) {
        return new AttributedStyle(style[start + index], style[start + index]);
    }

    /**
     * Returns the style code at the specified index in this attributed string.
     *
     * <p>
     * This method returns the raw style code (as a long value) associated with the
     * character at the specified index in this attributed string.
     * </p>
     *
     * @param index the index of the character whose style code to return
     * @return the style code at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    long styleCodeAt(int index) {
        return style[start + index];
    }

    /**
     * Returns a new AttributedString that is a subsequence of this attributed string.
     *
     * <p>
     * This method returns a new AttributedString that contains the characters and
     * styles from this attributed string starting at the specified start index
     * (inclusive) and ending at the specified end index (exclusive).
     * </p>
     *
     * <p>
     * The subsequence preserves the style information from the original string.
     * </p>
     *
     * @param start the start index, inclusive
     * @param end the end index, exclusive
     * @return the specified subsequence with its attributes
     * @throws IndexOutOfBoundsException if start or end are negative,
     *         if end is greater than length(), or if start is greater than end
     */
    @Override
    public AttributedString subSequence(int start, int end) {
        return new AttributedString(this, start, end);
    }

    /**
     * Returns a new AttributedString with the specified style applied to all matches of the pattern.
     *
     * <p>
     * This method finds all matches of the specified regular expression pattern in this
     * attributed string and applies the specified style to the matching regions. It returns
     * a new AttributedString with the modified styles.
     * </p>
     *
     * <p>
     * If no matches are found, this method returns the original attributed string.
     * </p>
     *
     * @param pattern the regular expression pattern to match
     * @param style the style to apply to matching regions
     * @return a new AttributedString with the specified style applied to matching regions
     */
    public AttributedString styleMatches(Pattern pattern, AttributedStyle style) {
        Matcher matcher = pattern.matcher(this);
        boolean result = matcher.find();
        if (result) {
            long[] newstyle = this.style.clone();
            do {
                for (int i = matcher.start(); i < matcher.end(); i++) {
                    newstyle[this.start + i] = (newstyle[this.start + i] & ~style.getMask()) | style.getStyle();
                }
                result = matcher.find();
            } while (result);
            return new AttributedString(buffer, newstyle, start, end);
        }
        return this;
    }

    /**
     * Compares this AttributedString with another object for equality.
     *
     * <p>
     * Two AttributedString objects are considered equal if they have the same
     * length, the same characters, and the same styles at each position.
     * </p>
     *
     * @param o the object to compare with
     * @return {@code true} if the objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttributedString that = (AttributedString) o;
        return end - start == that.end - that.start
                && arrEq(buffer, that.buffer, start, that.start, end - start)
                && arrEq(style, that.style, start, that.start, end - start);
    }

    /**
     * Compares two character arrays for equality within a specified range.
     *
     * <p>
     * This private helper method compares two character arrays starting at the
     * specified offsets for the specified length.
     * </p>
     *
     * @param a1 the first array
     * @param a2 the second array
     * @param s1 the starting offset in the first array
     * @param s2 the starting offset in the second array
     * @param l the length to compare
     * @return {@code true} if the arrays are equal in the specified range, {@code false} otherwise
     */
    private boolean arrEq(char[] a1, char[] a2, int s1, int s2, int l) {
        for (int i = 0; i < l; i++) {
            if (a1[s1 + i] != a2[s2 + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two long arrays for equality within a specified range.
     *
     * <p>
     * This private helper method compares two long arrays starting at the
     * specified offsets for the specified length.
     * </p>
     *
     * @param a1 the first array
     * @param a2 the second array
     * @param s1 the starting offset in the first array
     * @param s2 the starting offset in the second array
     * @param l the length to compare
     * @return {@code true} if the arrays are equal in the specified range, {@code false} otherwise
     */
    private boolean arrEq(long[] a1, long[] a2, int s1, int s2, int l) {
        for (int i = 0; i < l; i++) {
            if (a1[s1 + i] != a2[s2 + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a hash code for this AttributedString.
     *
     * <p>
     * The hash code is computed based on the characters, styles, and range
     * of this attributed string.
     * </p>
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        int result = Arrays.hashCode(buffer);
        result = 31 * result + Arrays.hashCode(style);
        result = 31 * result + start;
        result = 31 * result + end;
        return result;
    }

    /**
     * Joins multiple AttributedString objects with a delimiter.
     *
     * <p>
     * This method concatenates the specified AttributedString elements, inserting
     * the specified delimiter between each element. The resulting AttributedString
     * preserves the styles of all elements and the delimiter.
     * </p>
     *
     * @param delimiter the delimiter to insert between elements
     * @param elements the elements to join
     * @return a new AttributedString containing the joined elements
     * @throws NullPointerException if delimiter or elements is null
     */
    public static AttributedString join(AttributedString delimiter, AttributedString... elements) {
        Objects.requireNonNull(delimiter);
        Objects.requireNonNull(elements);
        return join(delimiter, Arrays.asList(elements));
    }

    /**
     * Joins an Iterable of AttributedString objects with a delimiter.
     *
     * <p>
     * This method concatenates the AttributedString elements in the specified Iterable,
     * inserting the specified delimiter between each element. The resulting AttributedString
     * preserves the styles of all elements and the delimiter.
     * </p>
     *
     * <p>
     * If the delimiter is null, the elements are concatenated without any separator.
     * </p>
     *
     * @param delimiter the delimiter to insert between elements, or null for no delimiter
     * @param elements the elements to join
     * @return a new AttributedString containing the joined elements
     * @throws NullPointerException if elements is null
     */
    public static AttributedString join(AttributedString delimiter, Iterable<AttributedString> elements) {
        Objects.requireNonNull(elements);
        AttributedStringBuilder sb = new AttributedStringBuilder();
        int i = 0;
        for (AttributedString str : elements) {
            if (i++ > 0 && delimiter != null) {
                sb.append(delimiter);
            }
            sb.append(str);
        }
        return sb.toAttributedString();
    }
}
