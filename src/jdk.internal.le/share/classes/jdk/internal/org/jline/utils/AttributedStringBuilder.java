/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A mutable builder for creating styled text strings with ANSI attributes.
 *
 * <p>
 * The AttributedStringBuilder class provides a mutable implementation of AttributedCharSequence
 * for constructing styled text strings. It allows for dynamic building of attributed strings
 * by appending characters, strings, or other attributed strings with various styles.
 * </p>
 *
 * <p>
 * This class is similar to StringBuilder but with added support for ANSI style attributes.
 * It provides methods for appending text with different styles, manipulating the content,
 * and converting the result to an immutable AttributedString when building is complete.
 * </p>
 *
 * <p>
 * Key features include:
 * </p>
 * <ul>
 *   <li>Append operations with different styles</li>
 *   <li>Tab expansion with configurable tab stops</li>
 *   <li>Style manipulation (foreground/background colors, bold, underline, etc.)</li>
 *   <li>Regular expression based styling</li>
 *   <li>Alternative character set support</li>
 * </ul>
 *
 * <p>
 * This class is commonly used for building complex styled output for terminal applications,
 * such as syntax highlighting, interactive prompts, and formatted displays.
 * </p>
 *
 * @see AttributedCharSequence
 * @see AttributedString
 * @see AttributedStyle
 */
public class AttributedStringBuilder extends AttributedCharSequence implements Appendable {

    private char[] buffer;
    private long[] style;
    private int length;
    private TabStops tabs = new TabStops(0);
    private char[] altIn;
    private char[] altOut;
    private boolean inAltCharset;
    private int lastLineLength = 0;
    private AttributedStyle current = AttributedStyle.DEFAULT;

    /**
     * Creates an AttributedString by appending multiple character sequences.
     *
     * <p>
     * This static utility method creates a new AttributedString by appending
     * all the specified character sequences. It is a convenient way to concatenate
     * multiple strings or AttributedStrings into a single AttributedString.
     * </p>
     *
     * @param strings the character sequences to append
     * @return a new AttributedString containing all the appended sequences
     */
    public static AttributedString append(CharSequence... strings) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        for (CharSequence s : strings) {
            sb.append(s);
        }
        return sb.toAttributedString();
    }

    /**
     * Creates a new AttributedStringBuilder with the default initial capacity.
     *
     * <p>
     * This constructor creates a new AttributedStringBuilder with an initial
     * capacity of 64 characters. The builder will automatically grow as needed
     * when more characters are appended.
     * </p>
     */
    public AttributedStringBuilder() {
        this(64);
    }

    /**
     * Creates a new AttributedStringBuilder with the specified initial capacity.
     *
     * <p>
     * This constructor creates a new AttributedStringBuilder with the specified
     * initial capacity. The builder will automatically grow as needed when more
     * characters are appended.
     * </p>
     *
     * @param capacity the initial capacity of the builder
     */
    public AttributedStringBuilder(int capacity) {
        buffer = new char[capacity];
        style = new long[capacity];
        length = 0;
    }

    /**
     * Returns the length of this attributed string builder.
     *
     * <p>
     * This method returns the number of characters in this attributed string builder.
     * </p>
     *
     * @return the number of characters in this attributed string builder
     */
    @Override
    public int length() {
        return length;
    }

    /**
     * Returns the character at the specified index in this attributed string builder.
     *
     * <p>
     * This method returns the character at the specified index in this
     * attributed string builder.
     * </p>
     *
     * @param index the index of the character to return
     * @return the character at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    public char charAt(int index) {
        return buffer[index];
    }

    /**
     * Returns the style at the specified index in this attributed string builder.
     *
     * <p>
     * This method returns the AttributedStyle object associated with the
     * character at the specified index in this attributed string builder.
     * </p>
     *
     * @param index the index of the character whose style to return
     * @return the style at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    public AttributedStyle styleAt(int index) {
        return new AttributedStyle(style[index], style[index]);
    }

    /**
     * Returns the style code at the specified index in this attributed string builder.
     *
     * <p>
     * This method returns the raw style code (as a long value) associated with the
     * character at the specified index in this attributed string builder.
     * </p>
     *
     * @param index the index of the character whose style code to return
     * @return the style code at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    long styleCodeAt(int index) {
        return style[index];
    }

    /**
     * Returns the character buffer for this attributed string builder.
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
     * Returns the offset in the buffer where this attributed string builder starts.
     *
     * <p>
     * This method is used internally by the AttributedCharSequence implementation
     * to determine the starting position in the buffer. For AttributedStringBuilder,
     * this is always 0 since the builder uses the entire buffer.
     * </p>
     *
     * @return the offset in the buffer (always 0 for AttributedStringBuilder)
     */
    @Override
    protected int offset() {
        return 0;
    }

    /**
     * Returns a new AttributedString that is a subsequence of this attributed string builder.
     *
     * <p>
     * This method returns a new AttributedString that contains the characters and
     * styles from this attributed string builder starting at the specified start index
     * (inclusive) and ending at the specified end index (exclusive).
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
        return new AttributedString(
                Arrays.copyOfRange(buffer, start, end), Arrays.copyOfRange(style, start, end), 0, end - start);
    }

    /**
     * Appends the specified character sequence to this builder.
     *
     * <p>
     * This method appends the specified character sequence to this builder,
     * applying the current style to all characters. If the character sequence
     * is null, the string "null" is appended, as required by the Appendable interface.
     * </p>
     *
     * @param csq the character sequence to append, or null
     * @return this builder
     */
    @Override
    public AttributedStringBuilder append(CharSequence csq) {
        if (csq == null) {
            csq = "null"; // Required by Appendable.append
        }
        return append(new AttributedString(csq, current));
    }

    /**
     * Appends a subsequence of the specified character sequence to this builder.
     *
     * <p>
     * This method appends a subsequence of the specified character sequence to this builder,
     * applying the current style to all characters. If the character sequence
     * is null, the string "null" is appended, as required by the Appendable interface.
     * </p>
     *
     * @param csq the character sequence to append, or null
     * @param start the index of the first character to append
     * @param end the index after the last character to append
     * @return this builder
     * @throws IndexOutOfBoundsException if start or end are negative, or if
     *         end is greater than csq.length(), or if start is greater than end
     */
    @Override
    public AttributedStringBuilder append(CharSequence csq, int start, int end) {
        if (csq == null) {
            csq = "null"; // Required by Appendable.append
        }
        return append(csq.subSequence(start, end));
    }

    /**
     * Appends the specified character to this builder.
     *
     * <p>
     * This method appends the specified character to this builder,
     * applying the current style to the character.
     * </p>
     *
     * @param c the character to append
     * @return this builder
     */
    @Override
    public AttributedStringBuilder append(char c) {
        return append(Character.toString(c));
    }

    /**
     * Appends the specified character to this builder multiple times.
     *
     * <p>
     * This method appends the specified character to this builder the specified
     * number of times, applying the current style to all characters.
     * </p>
     *
     * @param c the character to append
     * @param repeat the number of times to append the character
     * @return this builder
     */
    public AttributedStringBuilder append(char c, int repeat) {
        AttributedString s = new AttributedString(Character.toString(c), current);
        while (repeat-- > 0) {
            append(s);
        }
        return this;
    }

    /**
     * Appends the specified character sequence to this builder with the specified style.
     *
     * <p>
     * This method appends the specified character sequence to this builder,
     * applying the specified style to all characters. This allows appending text
     * with a style different from the current style without changing the current style.
     * </p>
     *
     * @param csq the character sequence to append
     * @param style the style to apply to the appended characters
     * @return this builder
     */
    public AttributedStringBuilder append(CharSequence csq, AttributedStyle style) {
        return append(new AttributedString(csq, style));
    }

    /**
     * Sets the current style for this builder.
     *
     * <p>
     * This method sets the current style for this builder, which will be applied
     * to all characters appended after this call (until the style is changed again).
     * </p>
     *
     * @param style the new current style
     * @return this builder
     */
    public AttributedStringBuilder style(AttributedStyle style) {
        current = style;
        return this;
    }

    /**
     * Updates the current style for this builder using a function.
     *
     * <p>
     * This method updates the current style for this builder by applying the
     * specified function to the current style. This allows for fluent style
     * modifications without creating intermediate style objects.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * builder.style(s -> s.bold().foreground(AttributedStyle.RED));
     * </pre>
     *
     * @param style the function to apply to the current style
     * @return this builder
     */
    public AttributedStringBuilder style(Function<AttributedStyle, AttributedStyle> style) {
        current = style.apply(current);
        return this;
    }

    /**
     * Appends the specified character sequence with a temporarily modified style.
     *
     * <p>
     * This method temporarily modifies the current style using the specified function,
     * appends the specified character sequence with that style, and then restores
     * the original style. This allows for appending styled text without permanently
     * changing the current style.
     * </p>
     *
     * @param style the function to apply to the current style
     * @param cs the character sequence to append
     * @return this builder
     */
    public AttributedStringBuilder styled(Function<AttributedStyle, AttributedStyle> style, CharSequence cs) {
        return styled(style, sb -> sb.append(cs));
    }

    /**
     * Appends the specified character sequence with the specified style.
     *
     * <p>
     * This method temporarily sets the current style to the specified style,
     * appends the specified character sequence with that style, and then restores
     * the original style. This allows for appending styled text without permanently
     * changing the current style.
     * </p>
     *
     * @param style the style to use
     * @param cs the character sequence to append
     * @return this builder
     */
    public AttributedStringBuilder styled(AttributedStyle style, CharSequence cs) {
        return styled(s -> style, sb -> sb.append(cs));
    }

    /**
     * Performs operations with a temporarily modified style.
     *
     * <p>
     * This method temporarily modifies the current style using the specified function,
     * performs the operations specified by the consumer with that style, and then
     * restores the original style. This allows for performing complex styling
     * operations without permanently changing the current style.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * builder.styled(s -> s.bold().foreground(AttributedStyle.RED),
     *               sb -> sb.append("Error: ").append(errorMessage));
     * </pre>
     *
     * @param style the function to apply to the current style
     * @param consumer the consumer that performs operations on this builder
     * @return this builder
     */
    public AttributedStringBuilder styled(
            Function<AttributedStyle, AttributedStyle> style, Consumer<AttributedStringBuilder> consumer) {
        AttributedStyle prev = current;
        current = style.apply(prev);
        consumer.accept(this);
        current = prev;
        return this;
    }

    /**
     * Returns the current style for this builder.
     *
     * <p>
     * This method returns the current style for this builder, which is applied
     * to all characters appended after the style was set (until the style is changed).
     * </p>
     *
     * @return the current style
     */
    public AttributedStyle style() {
        return current;
    }

    /**
     * Appends the specified AttributedString to this builder.
     *
     * <p>
     * This method appends the specified AttributedString to this builder,
     * preserving its character attributes. The current style is applied to
     * any attributes that are not explicitly set in the AttributedString.
     * </p>
     *
     * @param str the AttributedString to append
     * @return this builder
     */
    public AttributedStringBuilder append(AttributedString str) {
        return append((AttributedCharSequence) str, 0, str.length());
    }

    /**
     * Appends a subsequence of the specified AttributedString to this builder.
     *
     * <p>
     * This method appends a subsequence of the specified AttributedString to this builder,
     * preserving its character attributes. The current style is applied to
     * any attributes that are not explicitly set in the AttributedString.
     * </p>
     *
     * @param str the AttributedString to append
     * @param start the index of the first character to append
     * @param end the index after the last character to append
     * @return this builder
     * @throws IndexOutOfBoundsException if start or end are negative, or if
     *         end is greater than str.length(), or if start is greater than end
     */
    public AttributedStringBuilder append(AttributedString str, int start, int end) {
        return append((AttributedCharSequence) str, start, end);
    }

    /**
     * Appends the specified AttributedCharSequence to this builder.
     *
     * <p>
     * This method appends the specified AttributedCharSequence to this builder,
     * preserving its character attributes. The current style is applied to
     * any attributes that are not explicitly set in the AttributedCharSequence.
     * </p>
     *
     * @param str the AttributedCharSequence to append
     * @return this builder
     */
    public AttributedStringBuilder append(AttributedCharSequence str) {
        return append(str, 0, str.length());
    }

    /**
     * Appends a subsequence of the specified AttributedCharSequence to this builder.
     *
     * <p>
     * This method appends a subsequence of the specified AttributedCharSequence to this builder,
     * preserving its character attributes. The current style is applied to
     * any attributes that are not explicitly set in the AttributedCharSequence.
     * </p>
     *
     * <p>
     * If the sequence contains tab characters and tab stops are defined, the tabs
     * will be expanded according to the current tab stop settings.
     * </p>
     *
     * @param str the AttributedCharSequence to append
     * @param start the index of the first character to append
     * @param end the index after the last character to append
     * @return this builder
     * @throws IndexOutOfBoundsException if start or end are negative, or if
     *         end is greater than str.length(), or if start is greater than end
     */
    public AttributedStringBuilder append(AttributedCharSequence str, int start, int end) {
        ensureCapacity(length + end - start);
        for (int i = start; i < end; i++) {
            char c = str.charAt(i);
            long s = str.styleCodeAt(i) & ~current.getMask() | current.getStyle();
            if (tabs.defined() && c == '\t') {
                insertTab(new AttributedStyle(s, 0));
            } else {
                ensureCapacity(length + 1);
                buffer[length] = c;
                style[length] = s;
                if (c == '\n') {
                    lastLineLength = 0;
                } else {
                    lastLineLength++;
                }
                length++;
            }
        }
        return this;
    }

    protected void ensureCapacity(int nl) {
        if (nl > buffer.length) {
            int s = Math.max(buffer.length, 1);
            while (s <= nl) {
                s *= 2;
            }
            buffer = Arrays.copyOf(buffer, s);
            style = Arrays.copyOf(style, s);
        }
    }

    /**
     * Appends the specified ANSI-encoded string to this builder.
     *
     * <p>
     * This method parses the ANSI escape sequences in the input string and
     * appends the text with the corresponding styles to this builder.
     * This is useful for converting ANSI-colored output from external commands
     * into styled text.
     * </p>
     *
     * <p>
     * This method is equivalent to {@link #ansiAppend(String)} but returns void.
     * </p>
     *
     * @param ansi the ANSI-encoded string to parse and append
     * @see #ansiAppend(String)
     */
    public void appendAnsi(String ansi) {
        ansiAppend(ansi);
    }

    /**
     * Appends the specified ANSI-encoded string to this builder.
     *
     * <p>
     * This method parses the ANSI escape sequences in the input string and
     * appends the text with the corresponding styles to this builder.
     * This is useful for converting ANSI-colored output from external commands
     * into styled text.
     * </p>
     *
     * <p>
     * The method recognizes standard ANSI SGR (Select Graphic Rendition) sequences
     * for text attributes (bold, underline, etc.) and colors (foreground and background).
     * </p>
     *
     * @param ansi the ANSI-encoded string to parse and append
     * @return this builder
     */
    public AttributedStringBuilder ansiAppend(String ansi) {
        int ansiStart = 0;
        int ansiState = 0;
        ensureCapacity(length + ansi.length());
        for (int i = 0; i < ansi.length(); i++) {
            char c = ansi.charAt(i);
            if (ansiState == 0 && c == 27) {
                ansiState++;
            } else if (ansiState == 1 && c == '[') {
                ansiState++;
                ansiStart = i + 1;
            } else if (ansiState == 2) {
                if (c == 'm') {
                    String[] params = ansi.substring(ansiStart, i).split(";");
                    int j = 0;
                    while (j < params.length) {
                        int ansiParam = params[j].isEmpty() ? 0 : Integer.parseInt(params[j]);
                        switch (ansiParam) {
                            case 0:
                                current = AttributedStyle.DEFAULT;
                                break;
                            case 1:
                                current = current.bold();
                                break;
                            case 2:
                                current = current.faint();
                                break;
                            case 3:
                                current = current.italic();
                                break;
                            case 4:
                                current = current.underline();
                                break;
                            case 5:
                                current = current.blink();
                                break;
                            case 7:
                                current = current.inverse();
                                break;
                            case 8:
                                current = current.conceal();
                                break;
                            case 9:
                                current = current.crossedOut();
                                break;
                            case 22:
                                current = current.boldOff().faintOff();
                                break;
                            case 23:
                                current = current.italicOff();
                                break;
                            case 24:
                                current = current.underlineOff();
                                break;
                            case 25:
                                current = current.blinkOff();
                                break;
                            case 27:
                                current = current.inverseOff();
                                break;
                            case 28:
                                current = current.concealOff();
                                break;
                            case 29:
                                current = current.crossedOutOff();
                                break;
                            case 30:
                            case 31:
                            case 32:
                            case 33:
                            case 34:
                            case 35:
                            case 36:
                            case 37:
                                current = current.foreground(ansiParam - 30);
                                break;
                            case 39:
                                current = current.foregroundOff();
                                break;
                            case 40:
                            case 41:
                            case 42:
                            case 43:
                            case 44:
                            case 45:
                            case 46:
                            case 47:
                                current = current.background(ansiParam - 40);
                                break;
                            case 49:
                                current = current.backgroundOff();
                                break;
                            case 38:
                            case 48:
                                if (j + 1 < params.length) {
                                    int ansiParam2 = Integer.parseInt(params[++j]);
                                    if (ansiParam2 == 2) {
                                        if (j + 3 < params.length) {
                                            int r = Integer.parseInt(params[++j]);
                                            int g = Integer.parseInt(params[++j]);
                                            int b = Integer.parseInt(params[++j]);
                                            if (ansiParam == 38) {
                                                current = current.foreground(r, g, b);
                                            } else {
                                                current = current.background(r, g, b);
                                            }
                                        }
                                    } else if (ansiParam2 == 5) {
                                        if (j + 1 < params.length) {
                                            int col = Integer.parseInt(params[++j]);
                                            if (ansiParam == 38) {
                                                current = current.foreground(col);
                                            } else {
                                                current = current.background(col);
                                            }
                                        }
                                    }
                                }
                                break;
                            case 90:
                            case 91:
                            case 92:
                            case 93:
                            case 94:
                            case 95:
                            case 96:
                            case 97:
                                current = current.foreground(ansiParam - 90 + 8);
                                break;
                            case 100:
                            case 101:
                            case 102:
                            case 103:
                            case 104:
                            case 105:
                            case 106:
                            case 107:
                                current = current.background(ansiParam - 100 + 8);
                                break;
                        }
                        j++;
                    }
                    ansiState = 0;
                } else if (!(c >= '0' && c <= '9' || c == ';')) {
                    // This is not a SGR code, so ignore
                    ansiState = 0;
                }
            } else {
                if (ansiState >= 1) {
                    ensureCapacity(length + 1);
                    buffer[length++] = 27;
                    if (ansiState >= 2) {
                        ensureCapacity(length + 1);
                        buffer[length++] = '[';
                    }
                    ansiState = 0;
                }
                if (c == '\t' && tabs.defined()) {
                    insertTab(current);
                } else {
                    ensureCapacity(length + 1);
                    if (inAltCharset) {
                        switch (c) {
                            case 'j':
                                c = '\u2518';
                                break;
                            case 'k':
                                c = '\u2510';
                                break;
                            case 'l':
                                c = '\u250C';
                                break;
                            case 'm':
                                c = '\u2514';
                                break;
                            case 'n':
                                c = '\u253C';
                                break;
                            case 'q':
                                c = '\u2500';
                                break;
                            case 't':
                                c = '\u251C';
                                break;
                            case 'u':
                                c = '\u2524';
                                break;
                            case 'v':
                                c = '\u2534';
                                break;
                            case 'w':
                                c = '\u252C';
                                break;
                            case 'x':
                                c = '\u2502';
                                break;
                        }
                    }
                    buffer[length] = c;
                    style[length] = this.current.getStyle();
                    if (c == '\n') {
                        lastLineLength = 0;
                    } else {
                        lastLineLength++;
                    }
                    length++;
                    if (altIn != null && altOut != null) {
                        char[] alt = inAltCharset ? altOut : altIn;
                        if (equals(buffer, length - alt.length, alt, 0, alt.length)) {
                            inAltCharset = !inAltCharset;
                            length -= alt.length;
                        }
                    }
                }
            }
        }
        return this;
    }

    private static boolean equals(char[] a, int aFromIndex, char[] b, int bFromIndex, int length) {
        if (aFromIndex < 0 || bFromIndex < 0 || aFromIndex + length > a.length || bFromIndex + length > b.length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return false;
            }
        }
        return true;
    }

    protected void insertTab(AttributedStyle s) {
        int nb = tabs.spaces(lastLineLength);
        ensureCapacity(length + nb);
        for (int i = 0; i < nb; i++) {
            buffer[length] = ' ';
            style[length] = s.getStyle();
            length++;
        }
        lastLineLength += nb;
    }

    /**
     * Sets the length of this attributed string builder.
     *
     * <p>
     * This method sets the length of this attributed string builder. If the
     * specified length is less than the current length, the builder is truncated;
     * if it is greater than the current length, the builder is extended with
     * undefined characters and styles.
     * </p>
     *
     * <p>
     * Note that extending the builder with this method may result in undefined
     * behavior if the extended region is accessed without first setting its
     * characters and styles.
     * </p>
     *
     * @param l the new length
     */
    public void setLength(int l) {
        length = l;
    }

    /**
     * Set the number of spaces a tab is expanded to. Tab size cannot be changed
     * after text has been added to prevent inconsistent indentation.
     *
     * If tab size is set to 0, tabs are not expanded (the default).
     * @param tabsize Spaces per tab or 0 for no tab expansion. Must be non-negative
     * @return this
     */
    public AttributedStringBuilder tabs(int tabsize) {
        if (tabsize < 0) {
            throw new IllegalArgumentException("Tab size must be non negative");
        }
        return tabs(Arrays.asList(tabsize));
    }

    /**
     * Sets the tab stops for this attributed string builder.
     *
     * <p>
     * This method sets the tab stops for this attributed string builder,
     * which are used for tab expansion when appending tab characters.
     * Tab stops cannot be changed after text has been added to prevent
     * inconsistent indentation.
     * </p>
     *
     * @param tabs the list of tab stop positions
     * @return this attributed string builder
     * @throws IllegalStateException if text has already been appended
     */
    public AttributedStringBuilder tabs(List<Integer> tabs) {
        if (length > 0) {
            throw new IllegalStateException("Cannot change tab size after appending text");
        }
        this.tabs = new TabStops(tabs);
        return this;
    }

    /**
     * Sets the alternate character set sequences for this builder.
     *
     * <p>
     * This method sets the alternate character set sequences for this builder,
     * which are used for handling special characters like box drawing characters.
     * The alternate character set cannot be changed after text has been added
     * to prevent inconsistent character rendering.
     * </p>
     *
     * @param altIn the sequence to enable the alternate character set, or null to disable
     * @param altOut the sequence to disable the alternate character set, or null to disable
     * @return this builder
     * @throws IllegalStateException if text has already been appended
     */
    public AttributedStringBuilder altCharset(String altIn, String altOut) {
        if (length > 0) {
            throw new IllegalStateException("Cannot change alternative charset after appending text");
        }
        this.altIn = altIn != null ? altIn.toCharArray() : null;
        this.altOut = altOut != null ? altOut.toCharArray() : null;
        return this;
    }

    /**
     * Applies the specified style to all matches of the pattern in this builder.
     *
     * <p>
     * This method finds all matches of the specified regular expression pattern in this
     * builder and applies the specified style to the matching regions. The style is
     * applied to all characters in each match.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * builder.append("Error: File not found")
     *        .styleMatches(Pattern.compile("Error:"), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
     * </pre>
     *
     * @param pattern the regular expression pattern to match
     * @param s the style to apply to matching regions
     * @return this builder
     */
    public AttributedStringBuilder styleMatches(Pattern pattern, AttributedStyle s) {
        Matcher matcher = pattern.matcher(this);
        while (matcher.find()) {
            for (int i = matcher.start(); i < matcher.end(); i++) {
                style[i] = (style[i] & ~s.getMask()) | s.getStyle();
            }
        }
        return this;
    }

    /**
     * Applies different styles to different capture groups in pattern matches.
     *
     * <p>
     * This method finds all matches of the specified regular expression pattern in this
     * builder and applies different styles to different capture groups in each match.
     * The first style in the list is applied to the first capture group, the second style
     * to the second capture group, and so on.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * builder.append("Error: File not found")
     *        .styleMatches(Pattern.compile("(Error): (File not found)"),
     *                     Arrays.asList(
     *                         AttributedStyle.DEFAULT.foreground(AttributedStyle.RED),
     *                         AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)));
     * </pre>
     *
     * @param pattern the regular expression pattern to match
     * @param styles the list of styles to apply to capture groups
     * @return this builder
     * @throws IndexOutOfBoundsException if the pattern has fewer capture groups than styles
     */
    public AttributedStringBuilder styleMatches(Pattern pattern, List<AttributedStyle> styles) {
        Matcher matcher = pattern.matcher(this);
        while (matcher.find()) {
            for (int group = 0; group < matcher.groupCount(); group++) {
                AttributedStyle s = styles.get(group);
                for (int i = matcher.start(group + 1); i < matcher.end(group + 1); i++) {
                    style[i] = (style[i] & ~s.getMask()) | s.getStyle();
                }
            }
        }
        return this;
    }

    private static class TabStops {
        private List<Integer> tabs = new ArrayList<>();
        private int lastStop = 0;
        private int lastSize = 0;

        public TabStops(int tabs) {
            this.lastSize = tabs;
        }

        public TabStops(List<Integer> tabs) {
            this.tabs = tabs;
            int p = 0;
            for (int s : tabs) {
                if (s <= p) {
                    continue;
                }
                lastStop = s;
                lastSize = s - p;
                p = s;
            }
        }

        boolean defined() {
            return lastSize > 0;
        }

        int spaces(int lastLineLength) {
            int out = 0;
            if (lastLineLength >= lastStop) {
                out = lastSize - (lastLineLength - lastStop) % lastSize;
            } else {
                for (int s : tabs) {
                    if (s > lastLineLength) {
                        out = s - lastLineLength;
                        break;
                    }
                }
            }
            return out;
        }
    }
}
