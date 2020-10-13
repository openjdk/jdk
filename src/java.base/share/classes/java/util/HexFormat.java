/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.util;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

/**
 * Converts between bytes and chars and hex-encoded strings which may include additional
 * formatting markup such as prefixes, suffixes, and delimiters.
 * <p>
 * There are two {@code HexFormat}ters with preset parameters {@link #of()} and
 * {@link #ofDelimiter(String) of(delimiter)}. For other parameter combinations
 * the {@code withXXX} methods return copies of {@code HexFormat}ters modified
 * {@link #withPrefix(String)}, {@link #withSuffix(String)}, {@link #withDelimiter(String)}
 * or choice of {@link #withUpperCase()} or {@link #withLowerCase()} parameters using
 * a fluent builder style.
 * <p>
 * For primitive to hexadecimal string conversions the {@code toHexDigits}
 * methods include {@link #toHexDigits(byte)}, {@link #toHexDigits(int)}, and
 * {@link #toHexDigits(long)}, etc.
 * For conversions producing uppercase hexadecimal strings use {@link #withUpperCase()}.
 *
 * <p>
 * For hexadecimal string to primitive conversions the {@code fromHexDigits}
 * methods include {@link #fromHexDigits(CharSequence) fromHexDigits(string)},
 * {@link #fromHexDigitsToLong(CharSequence) fromHexDigitsToLong(string)}, and
 * {@link #fromHexDigit(int) fromHexDigit(int)} converts a single character or codepoint.
 *
 * <p>
 * For byte array to formatted hexadecimal string conversions
 * the {@code formatHex} methods include {@link #formatHex(byte[]) formatHex(byte[])}
 * and {@link #formatHex(Appendable, byte[]) formatHex(Appendable, byte[])}.
 * The formatted output can be appended to {@link StringBuilder}, {@link System#out},
 * {@link java.io.Writer}, and {@link java.io.PrintStream}, all of which are {@link Appendable}s.
 * Each byte value is formatted as the {@code prefix}, two hexadecimal characters from the
 * uppercase or lowercase digits, and the {@code suffix}.
 * A {@code delimiter} appears after each formatted value, except the last.
 * For conversions producing uppercase hexadecimal strings use {@link #withUpperCase()}.
 *
 * <p>
 * For formatted hexadecimal string to byte array conversions the
 * {@code parseHex} methods include {@link #parseHex(CharSequence) parseHex(string)} and
 * {@link #parseHex(char[], int, int) parseHex(char[], offset, length)}.
 * Each byte value is parsed as the {@code prefix}, two hexadecimal characters from the
 * uppercase or lowercase digits, and the {@code suffix}.
 * The {@code delimiter} is required after each formatted value, except the last.
 *
 * @apiNote
 * For example, an individual byte is converted to a string of hexadecimal digits using
 * {@link HexFormat#toHexDigits(int) toHexDigits(int)} and converted from a string to a
 * primitive value using {@link HexFormat#fromHexDigits(CharSequence) fromHexDigits(string)}.
 * <pre>{@code
 *     HexFormat hex = HexFormat.of();
 *     byte b = 127;
 *     String byteStr = hex.toHexDigits(b);
 *
 *     int byteVal = hex.fromHexDigits(byteStr);
 *     assert(byteStr.equals("7f"));
 *     assert(b == byteVal);
 *
 *     // The hexadecimal digits are: "7f"
 * }</pre>
 * <p>
 * For a comma ({@code ", "}) separated format with a prefix ({@code "#"})
 * using lowercase hex digits the {@code HexFormat} is:
 * <pre>{@code
 *     HexFormat commaFormat = HexFormat.ofDelimiter(", ").withPrefix("#");
 *     byte[] bytes = {0, 1, 2, 3, 124, 125, 126, 127};
 *     String str = commaFormat.formatHex(bytes);
 *
 *     byte[] parsed = commaFormat.parseHex(str);
 *     assert(Arrays.equals(bytes, parsed));
 *
 *     // The formatted string is: "#00, #01, #02, #03, #7c, #7d, #7e, #7f"
 * }</pre>
 * <p>
 * For a fingerprint of byte values that uses the delimiter colon ({@code ":"})
 * and uppercase letters the {@code HexFormat} is:
 * <pre>{@code
 *     HexFormat formatFingerprint = HexFormat.ofDelimiter(":").withUpperCase();
 *     byte[] bytes = {0, 1, 2, 3, 124, 125, 126, 127};
 *     String str = formatFingerprint.formatHex(bytes);
 *     byte[] parsed = formatFingerprint.parseHex(str);
 *     assert(Arrays.equals(bytes, parsed));
 *
 *     // The formatted string is: "00:01:02:03:7C:7D:7E:7F"
 * }</pre>
 *
 * <p>
 * This is a <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class; use of identity-sensitive operations (including reference equality
 * ({@code ==}), identity hash code, or synchronization) on instances of
 * {@code HexFormat} may have unpredictable results and should be avoided.
 * The {@code equals} method should be used for comparisons.
 *
 * @implSpec
 * This class is immutable and thread-safe.
 * <p>
 * Unless otherwise noted, passing a null argument to any method will cause a
 * {@link java.lang.NullPointerException NullPointerException} to be thrown.
 *
 * @since 16
 */


public final class HexFormat {

    // Access to create strings from a byte array.
    private static final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();

    private static final byte[] UPPERCASE_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
    };
    private static final byte[] LOWERCASE_DIGITS = {
            '0' , '1' , '2' , '3' , '4' , '5' , '6' , '7',
            '8' , '9' , 'a' , 'b' , 'c' , 'd' , 'e' , 'f',
    };

    /**
     * Format each byte of an array as a pair of hex digits.
     * The hex characters are from lowercase alpha digits.
     */
    private static final HexFormat HEX_FORMAT =
            new HexFormat("", "", "", LOWERCASE_DIGITS);

    private static final byte[] emptyBytes = new byte[0];
    private final String delimiter;
    private final String prefix;
    private final String suffix;
    private final byte[] digits;

    /**
     * Returns a HexFormat with a delimiter, prefix, suffix, and array of digits.
     *
     * @param delimiter a delimiter, non-null
     * @param prefix a prefix, non-null
     * @param suffix a suffix, non-null
     * @param digits byte array of digits indexed by low nibble, non-null
     * @throws NullPointerException if any argument is null
     */
    private HexFormat(String delimiter, String prefix, String suffix, byte[] digits) {
        this.delimiter = Objects.requireNonNull(delimiter, "delimiter");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.suffix = Objects.requireNonNull(suffix, "suffix");
        this.digits = Objects.requireNonNull(digits, "digits");
    }

    /**
     * Returns a hexadecimal formatter with no delimiter and lowercase characters.
     * The hex characters are lowercase and the delimiter, prefix, and suffix are empty.
     * The methods {@link #withDelimiter(String) withDelimiter},
     * {@link #withUpperCase() withUpperCase}, {@link #withLowerCase() withLowerCase},
     * {@link #withPrefix(String) withPrefix}, and {@link #withSuffix(String) withSuffix}
     * return copies of formatters with new parameters.
     *
     * @return a hex formatter
     */
    public static HexFormat of() {
        return HEX_FORMAT;
    }

    /**
     * Returns a hexadecimal formatter with a {@code delimiter} and lowercase letters.
     * The prefix and suffix are empty.
     * The methods {@link #withDelimiter(String) withDelimiter},
     * {@link #withUpperCase() withUpperCase}, {@link #withLowerCase() withLowerCase},
     * {@link #withPrefix(String) withPrefix}, and {@link #withSuffix(String) withSuffix}
     * return copies of formatters with new parameters.
     *
     * @param delimiter a {@code delimiter}, non-null, may be empty
     * @return a {@link HexFormat} with the {@code delimiter} and lowercase letters
     */
    public static HexFormat ofDelimiter(String delimiter) {
        return new HexFormat(delimiter, "", "", LOWERCASE_DIGITS);
    }

    /**
     * Returns a copy of this {@code HexFormat} with the {@code delimiter}.
     * @param delimiter the {@code delimiter}, non-null, may be empty
     * @return a copy of this {@code HexFormat} with the {@code delimiter}
     */
    public HexFormat withDelimiter(String delimiter) {
        return new HexFormat(delimiter, this.prefix, this.suffix, this.digits);
    }

    /**
     * Returns a copy of this {@code HexFormat} with the {@code prefix}.
     *
     * @param prefix a prefix, non-null, may be empty
     * @return a copy of this {@code HexFormat} with the {@code prefix}
     */
    public HexFormat withPrefix(String prefix) {
        return new HexFormat(this.delimiter, prefix, this.suffix, this.digits);
    }

    /**
     * Returns a copy of this {@code HexFormat} with the {@code suffix}.
     *
     * @param suffix a {@code suffix}, non-null, may be empty
     * @return a copy of this {@code HexFormat} with the {@code suffix}
     */
    public HexFormat withSuffix(String suffix) {
        return new HexFormat(this.delimiter, this.prefix, suffix, this.digits);
    }

    /**
     * Returns a copy of this {@code HexFormat} to use uppercase hex characters.
     * The uppercase hexadecimal characters are {@code "0-9", "A-F"}.
     *
     * @return a copy of this {@code HexFormat} with uppercase hexadecimal characters
     */
    public HexFormat withUpperCase() {
        return new HexFormat(this.delimiter, this.prefix, this.suffix, UPPERCASE_DIGITS);
    }

    /**
     * Returns a copy of this {@code HexFormat} to use lowercase hex characters.
     * The lowercase hexadecimal characters are {@code "0-9", "a-f"}.
     *
     * @return a copy of this {@code HexFormat} with lowercase hex characters
     */
    public HexFormat withLowerCase() {
        return new HexFormat(this.delimiter, this.prefix, this.suffix, LOWERCASE_DIGITS);
    }

    /**
     * Returns the {@code delimiter} between hexadecimal values in a formatted byte array.
     *
     * @return return the {@code delimiter}, non-null, may be empty {@code ""}
     */
    public String delimiter() {
        return delimiter;
    }

    /**
     * Returns the {@code prefix} used for each hexadecimal value in a formatted byte array.
     *
     * @return returns the {@code prefix}
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Returns the {@code suffix} used for each hexadecimal value in a formatted byte array.
     *
     * @return returns the {@code suffix}
     */
    public String suffix() {
        return suffix;
    }

    /**
     * Returns {@code true} if the hexadecimal digits will be uppercase,
     *          otherwise {@code false}.
     * @return returns {@code true} if the hexadecimal digits will be uppercase,
     *          otherwise {@code false}
     */
    public boolean isUpperCase() {
        return Arrays.equals(digits, UPPERCASE_DIGITS);
    }

    /**
     * Returns a hexadecimal string formatted from a byte array.
     * Each byte value is formatted as the {@code prefix}, two hexadecimal characters
     * {@linkplain #isUpperCase selected from} uppercase or lowercase digits, and the {@code suffix}.
     * A {@code delimiter} appears after each formatted value, except the last.
     *
     * The behavior is equivalent to
     * {@link #formatHex(byte[], int, int) format(bytes, 0, bytes.length))}.
     *
     * @param bytes a non-null array of bytes
     * @return a string hexadecimal formatting of the byte array
     */
    public String formatHex(byte[] bytes) {
        return formatHex(bytes, 0, bytes.length);
    }

    /**
     * Returns a hexadecimal string formatted from a byte array range.
     * Each byte value is formatted as the {@code prefix}, two hexadecimal characters
     * {@linkplain #isUpperCase selected from} uppercase or lowercase digits, and the {@code suffix}.
     * A {@code delimiter} appears after each formatted value, except the last.
     *
     * @param bytes a non-null array of bytes
     * @param index the starting index
     * @param length the number of bytes to format
     * @return a string hex formatting each byte of the array range
     * @throws IndexOutOfBoundsException if the array range is out of bounds
     */
    public String formatHex(byte[] bytes, int index, int length) {
        Objects.requireNonNull(bytes,"bytes");
        Objects.checkFromIndexSize(index, length, bytes.length);
        if (length == 0) {
            return "";
        }
        // Format efficiently if possible
        String s = formatOptDelimiter(bytes, index, length);
        if (s == null) {
            StringBuilder sb = new StringBuilder(bytes.length *
                    (delimiter.length() + prefix.length() + suffix.length()) - delimiter.length());
            formatHex(sb, bytes, index, length);
            s = sb.toString();
        }
        return s;
    }

    /**
     * Appends a hexadecimal string formatted from a byte array to the {@link Appendable}.
     * Each byte value is formatted as the {@code prefix}, two hexadecimal characters
     * {@linkplain #isUpperCase selected from} uppercase or lowercase digits, and the {@code suffix}.
     * A {@code delimiter} appears after each formatted value, except the last.
     * The behavior is equivalent to
     * {@link #formatHex(byte[]) out.append(format(bytes))}.
     *
     * @param <A> The type of Appendable
     * @param out an Appendable, non-null
     * @param bytes a byte array
     * @return the {@code Appendable}
     * @throws UncheckedIOException if an I/O exception occurs appending to the output
     */
    public <A extends Appendable> A formatHex(A out, byte[] bytes) {
        return formatHex(out, bytes, 0, bytes.length);
    }

    /**
     * Appends a hexadecimal string formatted from a byte array range to the {@link Appendable}.
     * Each byte value is formatted as the {@code prefix}, two hexadecimal characters
     * {@linkplain #isUpperCase selected from} uppercase or lowercase digits, and the {@code suffix}.
     * A {@code delimiter} appears after each formatted value, except the last.
     * The behavior is equivalent to
     * {@link #formatHex(byte[], int, int)  out.append(format(bytes, index, length))}.
     *
     * @param <A> The type of Appendable
     * @param out an Appendable, non-null
     * @param bytes a byte array, non-null
     * @param index the starting index
     * @param length the number of bytes to format
     * @return the {@code Appendable}
     * @throws IndexOutOfBoundsException if the array range is out of bounds
     * @throws UncheckedIOException if an I/O exception occurs appending to the output
     */
    public <A extends Appendable> A formatHex(A out, byte[] bytes, int index, int length) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(bytes, "bytes");
        Objects.checkFromIndexSize(index, length, bytes.length);

        if (length > 0) {
            try {
                String between = suffix + delimiter + prefix;
                out.append(prefix);
                toHexDigits(out, bytes[0]);
                if (between.isEmpty()) {
                    for (int i = 1; i < bytes.length; i++) {
                        toHexDigits(out, bytes[i]);
                    }
                } else {
                    for (int i = 1; i < bytes.length; i++) {
                        out.append(between);
                        toHexDigits(out, bytes[i]);
                    }
                }
                out.append(suffix);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe.getMessage(), ioe);
            }
        }
        return out;
    }

    /**
     * Returns a string formatting of the range of bytes optimized
     * for a single allocation.
     * Prefix and suffix must be empty and the delimiter
     * must be empty or a single byte character, otherwise null is returned.
     *
     * @param bytes the bytes, non-null
     * @param index the starting index
     * @param length the length
     * @return a String formatting or null for non-single byte formatting
    */
    private String formatOptDelimiter(byte[] bytes, int index, int length) {
        byte[] rep;
        if (!prefix.isEmpty() || !suffix.isEmpty()) {
            return null;
        }
        if (delimiter.isEmpty()) {
            // Allocate the byte array and fill in the hex pairs for each byte
            rep = new byte[length * 2];
            for (int i = 0; i < length; i++) {
                rep[i * 2] = (byte)toHighHexDigit(bytes[index + i]);
                rep[i * 2 + 1] = (byte)toLowHexDigit(bytes[index + i]);
            }
        } else if (delimiter.length() == 1 && delimiter.charAt(0) < 256) {
            // Allocate the byte array and fill in the characters for the first byte
            // Then insert the delimiter and hex characters for each of the remaining bytes
            char sep = delimiter.charAt(0);
            rep = new byte[bytes.length * 3 - 1];
            rep[0] = (byte) toHighHexDigit(bytes[0]);
            rep[1] = (byte) toLowHexDigit(bytes[0]);
            for (int i = 1; i < bytes.length; i++) {
                rep[i * 3 - 1] = (byte) sep;
                rep[i * 3    ] = (byte) toHighHexDigit(bytes[i]);
                rep[i * 3 + 1] = (byte) toLowHexDigit(bytes[i]);
            }
        } else {
            // Delimiter formatting not to a single byte
            return null;
        }
        try {
            // Return a new string using the bytes without making a copy
            return jla.newStringNoRepl(rep, StandardCharsets.ISO_8859_1);
        } catch (CharacterCodingException cce) {
            throw new AssertionError(cce);
        }
    }

    /**
     * Returns a byte array containing hexadecimal values parsed from the string.
     *
     * Each byte value is parsed as the {@code prefix}, two hexadecimal characters from the
     * uppercase or lowercase digits, and the {@code suffix}.
     * A {@code delimiter} appears after each formatted value, except the last.
     * The {@code delimiter}s, {@code prefix}es, and {@code suffix}es strings must be present;
     * they may be empty strings.
     * A valid string consists only of the above format.
     *
     * @param string a string containing the byte values with {@code prefix}, hexadecimal digits, {@code suffix},
     *            and delimiters
     * @return a byte array
     * @throws IllegalArgumentException if the {@code prefix} or {@code suffix} is not present for each byte value,
     *          the byte values are not hexadecimal characters, or if the {@code delimiter} is not present
     *          after all but the last byte value.
     */
    public byte[] parseHex(CharSequence string) {
        return parseHex(string, 0, string.length());
    }

    /**
     * Returns a byte array containing hexadecimal values parsed from a range of the string.
     *
     * Each byte value is parsed as the {@code prefix}, two hexadecimal characters from the
     * uppercase or lowercase digits, and the {@code suffix}.
     * A {@code delimiter} appears after each formatted value, except the last.
     * The {@code delimiter}s, {@code prefix}es, and {@code suffix}es strings must be present;
     * they may be empty strings.
     * A valid string consists only of the above format.
     *
     * @param string a string range containing hex digits,
     *           {@code delimiters}, {@code prefix}, and {@code suffix}.
     * @param index of the start of the character range
     * @param length of the character range
     * @return a byte array
     * @throws IllegalArgumentException if the string length is not valid or
     *          the string contains non-hex characters,
     *          or the {@code delimiter}, {@code prefix}, or {@code suffix} are not found
     * @throws IndexOutOfBoundsException if the string range is out of bounds
     */
    public byte[] parseHex(CharSequence string, int index, int length) {
        Objects.requireNonNull(string, "string");
        Objects.checkFromIndexSize(index, length, string.length());

        if (index != 0 || length != string.length()) {
            string = string.subSequence(index, length);
        }

        if (string.length() == 0)
            return emptyBytes;
        if (delimiter.isEmpty() && prefix.isEmpty() && suffix.isEmpty())
            return parseNoDelimiter(string);

        int valueChars = prefix.length() + 2 + suffix.length();
        int stride = valueChars + delimiter.length();
        if (string.length() < valueChars || (string.length() - valueChars) % stride != 0)
            throw new IllegalArgumentException("extra or missing delimiters " +
                    "or values consisting of prefix, two hex digits, and suffix");

        checkLiteral(string, 0, prefix);
        checkLiteral(string, string.length() - suffix.length(), suffix);
        String between = suffix + delimiter + prefix;
        final int len = (string.length() - valueChars) / stride + 1;
        byte[] bytes = new byte[len];
        int i, offset;
        for (i = 0, offset = prefix.length(); i < len - 1; i++, offset += 2 + between.length()) {
            int v = fromHexDigits(string, offset);
            if (v < 0)
                throw new IllegalArgumentException("input contains non-hex characters");
            bytes[i] = (byte) v;
            checkLiteral(string, offset + 2, between);
        }
        int v = fromHexDigits(string, offset);
        if (v < 0)
            throw new IllegalArgumentException("input contains non-hex characters");
        bytes[i] = (byte) v;

        return bytes;
    }

    /**
     * Returns a byte array containing hexadecimal values parsed from
     * a range of the character array.
     *
     * Each byte value is parsed as the {@code prefix}, two hexadecimal characters from the
     * uppercase or lowercase digits, and the {@code suffix}.
     * A {@code delimiter} appears after each formatted value, except the last.
     * The {@code delimiter}s, {@code prefix}es, and {@code suffix}es strings must be present;
     * they may be empty strings.
     * A valid string consists only of the above format.
     *
     * @param chars a char array range containing an even number of hex digits,
     *          {@code delimiters}, {@code prefix}, and {@code suffix}.
     * @param index the starting index
     * @param length the length to parse
     * @return a byte array
     * @throws IllegalArgumentException if the string length is not valid or
     *          the character array contains non-hex characters,
     *          or the {@code delimiter}, {@code prefix}, or {@code suffix} are not found
     * @throws IndexOutOfBoundsException if the char array range is out of bounds
     */
    public byte[] parseHex(char[] chars, int index, int length) {
        Objects.requireNonNull(chars, "chars");
        Objects.checkFromIndexSize(index, length, chars.length);
        CharBuffer cb = CharBuffer.wrap(chars, index, index + length);
        return parseHex(cb);
    }

    /**
     * Compare the literal and throw an exception if it does not match.
     *
     * @param string a CharSequence
     * @param index the index of the literal in the CharSequence
     * @param literal the expected literal
     * @throws IllegalArgumentException if the literal is not present
     */
    private static void checkLiteral(CharSequence string, int index, String literal) {
        if (literal.isEmpty() ||
                (literal.length() == 1 && literal.charAt(0) == string.charAt(index))) {
            return;
        }
        for (int i = 0; i < literal.length(); i++) {
            if (string.charAt(index + i) != literal.charAt(i)) {
                throw new IllegalArgumentException(escapeNL("found: \"" +
                        string.subSequence(index, index + literal.length()) +
                        "\", expected: \"" + literal + "\""));
            }
        }
    }

    /**
     * Expands new line characters to escaped newlines for display.
     *
     * @param string a string
     * @return a string with newline characters escaped
     */
    private static String escapeNL(String string) {
        return string.replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Returns the hex character for the low 4 bits of the value considering it to be a byte.
     * If the parameter {@link #isUpperCase()} is {@code true} the
     * character returned for values {@code 10-15} is uppercase {@code "A-F"},
     * otherwise the character returned is lowercase {@code "a-f"}.
     * The values in the range {@code 0-9} are returned as {@code "0-9"}.
     *
     * @param value a value, only the low 4 bits {@code 0-3} of the value are used
     * @return the hex character for the low 4 bits {@code 0-3} of the value
     */
    public char toLowHexDigit(int value) {
        return (char)digits[value & 0xf];
    }

    /**
     * Returns the hex character for the high 4 bits of the value considering it to be a byte.
     * If the parameter {@link #isUpperCase()} is {@code true} the
     * character returned for values {@code 10-15} is uppercase {@code "A-F"},
     * otherwise the character returned is lowercase {@code "a-f"}.
     * The values in the range {@code 0-9} are returned as {@code "0-9"}.
     *
     * @param value a value, only bits {@code 4-7} of the value are used
     * @return the hex character for the bits {@code 4-7} of the value are used
     */
    public char toHighHexDigit(int value) {
        return (char)digits[(value >> 4) & 0xf];
    }

    /**
     * Returns the two hex characters for the {@code byte} value.
     * Each nibble (4 bits) from most significant to least significant of the value
     * is formatted as if by {@link #toLowHexDigit(int) toLowHexDigit(nibble)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param value a byte value
     * @return the two hex characters for the byte value
     */
    public String toHexDigits(byte value) {
        byte[] rep = new byte[2];
        rep[0] = (byte)toHighHexDigit(value);
        rep[1] = (byte)toLowHexDigit(value);
        try {
            return jla.newStringNoRepl(rep, StandardCharsets.ISO_8859_1);
        } catch (CharacterCodingException cce) {
            throw new AssertionError(cce);
        }
    }

    /**
     * Appends two hex characters for the byte value to the {@link Appendable}.
     * Each nibble (4 bits) from most significant to least significant of the value
     * is formatted as if by {@link #toLowHexDigit(int) toLowHexDigit(nibble)}.
     * The behavior is equivalent to
     * {@link #toHexDigits(byte) out.append(toHexDigits((byte)value))}.
     *
     * @param out an Appendable, non-null
     * @param value a byte value
     * @return the {@code Appendable}
     * @throws UncheckedIOException if an I/O exception occurs appending to the output
     */
    public Appendable toHexDigits(Appendable out, byte value) {
        Objects.requireNonNull(out, "out");
        try {
            out.append(toHighHexDigit(value));
            out.append(toLowHexDigit(value));
            return out;
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe.getMessage(), ioe);
        }
    }

    /**
     * Returns the four hex characters for the {@code char} value.
     * Each nibble (4 bits) from most significant to least significant of the value
     * is formatted as if by {@link #toLowHexDigit(int) toLowHexDigit(nibble)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param value a {@code char} value
     * @return the four hex characters for the {@code char} value
     */
    public String toHexDigits(char value) {
        return toHexDigits((short)value);
    }

    /**
     * Returns the four hex characters for the {@code short} value.
     * Each nibble (4 bits) from most significant to least significant of the value
     * is formatted as if by {@link #toLowHexDigit(int) toLowHexDigit(nibble)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param value a {@code short} value
     * @return the four hex characters for the {@code short} value
     */
    public String toHexDigits(short value) {
        byte[] rep = new byte[4];
        rep[0] = (byte)toHighHexDigit((byte)(value >> 8));
        rep[1] = (byte)toLowHexDigit((byte)(value >> 8));
        rep[2] = (byte)toHighHexDigit((byte)value);
        rep[3] = (byte)toLowHexDigit((byte)value);

        try {
            return jla.newStringNoRepl(rep, StandardCharsets.ISO_8859_1);
        } catch (CharacterCodingException cce) {
            throw new AssertionError(cce);
        }
    }

    /**
     * Returns the eight hex characters for the {@code int} value.
     * Each nibble (4 bits) from most significant to least significant of the value
     * is formatted as if by {@link #toLowHexDigit(int) toLowHexDigit(nibble)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param value an {@code int} value
     * @return the eight hex characters for the {@code int} value
     */
    public String toHexDigits(int value) {
        byte[] rep = new byte[8];
        rep[0] = (byte)toHighHexDigit((byte)(value >> 24));
        rep[1] = (byte)toLowHexDigit((byte)(value >> 24));
        rep[2] = (byte)toHighHexDigit((byte)(value >> 16));
        rep[3] = (byte)toLowHexDigit((byte)(value >> 16));
        rep[4] = (byte)toHighHexDigit((byte)(value >> 8));
        rep[5] = (byte)toLowHexDigit((byte)(value >> 8));
        rep[6] = (byte)toHighHexDigit((byte)value);
        rep[7] = (byte)toLowHexDigit((byte)value);

        try {
            return jla.newStringNoRepl(rep, StandardCharsets.ISO_8859_1);
        } catch (CharacterCodingException cce) {
            throw new AssertionError(cce);
        }
    }

    /**
     * Returns the sixteen hex characters for the {@code long} value.
     * Each nibble (4 bits) from most significant to least significant of the value
     * is formatted as if by {@link #toLowHexDigit(int) toLowHexDigit(nibble)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param value a {@code long} value
     * @return the sixteen hex characters for the {@code long} value
     */
    public String toHexDigits(long value) {
        byte[] rep = new byte[16];
        rep[0] = (byte)toHighHexDigit((byte)(value >>> 56));
        rep[1] = (byte)toLowHexDigit((byte)(value >>> 56));
        rep[2] = (byte)toHighHexDigit((byte)(value >>> 48));
        rep[3] = (byte)toLowHexDigit((byte)(value >>> 48));
        rep[4] = (byte)toHighHexDigit((byte)(value >>> 40));
        rep[5] = (byte)toLowHexDigit((byte)(value >>> 40));
        rep[6] = (byte)toHighHexDigit((byte)(value >>> 32));
        rep[7] = (byte)toLowHexDigit((byte)(value >>> 32));
        rep[8] = (byte)toHighHexDigit((byte)(value >>> 24));
        rep[9] = (byte)toLowHexDigit((byte)(value >>> 24));
        rep[10] = (byte)toHighHexDigit((byte)(value >>> 16));
        rep[11] = (byte)toLowHexDigit((byte)(value >>> 16));
        rep[12] = (byte)toHighHexDigit((byte)(value >>> 8));
        rep[13] = (byte)toLowHexDigit((byte)(value >>> 8));
        rep[14] = (byte)toHighHexDigit((byte)value);
        rep[15] = (byte)toLowHexDigit((byte)value);

        try {
            return jla.newStringNoRepl(rep, StandardCharsets.ISO_8859_1);
        } catch (CharacterCodingException cce) {
            throw new AssertionError(cce);
        }
    }

    /**
     * Returns up to sixteen hex characters for the {@code long} value.
     * Each nibble (4 bits) from most significant to least significant of the value
     * is formatted as if by {@link #toLowHexDigit(int) toLowHexDigit(nibble)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param value an {@code long} value
     * @param digits the number of hexadecimal digits to return, 0 to 16
     * @return the hex characters for the {@code long} value
     * @throws  IllegalArgumentException if {@code digits} is negative or greater than 16
     */
    public String toHexDigits(long value, int digits) {
        if (digits < 0 || digits > 16)
            throw new IllegalArgumentException("number of digits: " + digits);
        if (digits == 0)
            return "";
        byte[] rep = new byte[digits];
        for (int i = rep.length - 1; i >= 0; i--) {
            rep[i] = (byte)toLowHexDigit((byte)(value));
            value = value >>> 4;
        }
        try {
            return jla.newStringNoRepl(rep, StandardCharsets.ISO_8859_1);
        } catch (CharacterCodingException cce) {
            throw new AssertionError(cce);
        }
    }

    /**
     * Returns a byte array containing the parsed hex digits.
     * A valid string consists only of an even number of hex digits.
     *
     * @param string a string containing an even number of only hex digits
     * @return a byte array
     * @throws IllegalArgumentException if the string length is not valid or
     *          the string contains non-hex characters
     */
    private byte[] parseNoDelimiter(CharSequence string) {
        if ((string.length() & 1) != 0)
            throw new IllegalArgumentException("string length not even: " +
                    string.length());

        byte[] bytes = new byte[string.length() / 2];
        int illegal = 0;        // Accumulate logical-or of all bytes
        for (int i = 0; i < bytes.length; i++) {
            int v = fromHexDigits(string, i * 2);
            bytes[i] = (byte) v;
            illegal |= v;
        }
        // check if any character was an illegal character
        if (illegal < 0)
            throw new IllegalArgumentException("input contains non-hex characters");

        return bytes;
    }

    /**
     * Check the number of requested digits against a limit.
     *
     * @param digits the number of digits requested
     * @param limit the maximum allowed
     */
    private static void checkDigitCount(int digits, int limit) {
        if (digits > limit)
            throw new IllegalArgumentException("digits greater than " + limit + ": " + digits);
    }

    /**
     * Returns {@code true} if the character is a valid hex character or codepoint.
     * A character is a valid hexadecimal character if
     * {@link Character#digit(int, int) Character.digit(int, 16)} returns
     * a positive value.
     *
     * @param ch a codepoint
     * @return {@code true} if the character is valid a hexadecimal character,
     *          otherwise {@code false}
     */
    public boolean isHexDigit(int ch) {
        return Character.digit(ch, 16) >= 0;
    }

    /**
     * Returns the value for the hexadecimal character or codepoint.
     * The characters {@code "0-9", "A-F", "a-f"} are parsed
     * using {@link Character#digit(int, int) Character.digit(int, 16)}.
     *
     * @param ch a character or codepoint
     * @return the value {@code 0..15}
     * @throws  NumberFormatException if the codepoint is not a hexadecimal character
     */
    public int fromHexDigit(int ch) {
        int value = Character.digit(ch, 16);
        if (value < 0)
            throw new NumberFormatException("not a hexadecimal digit: \"" + (char)ch + "\"");
        return value;
    }

    /**
     * Returns a value parsed from two hex characters in a string.
     * The characters in the range from {@code index} to {@code index + 1} ,
     * inclusive, must be valid hex digits according to {@link #fromHexDigit(int)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param string a CharSequence containing the characters
     * @param index the index of the first character of the range
     * @return the value parsed from the string range
     * @throws  NumberFormatException if any of the characters in the range
     *          is not a hexadecimal character
     * @throws  IndexOutOfBoundsException if the sub-range is out of bounds
     *          for the {@code CharSequence}
     */
    private int fromHexDigits(CharSequence string, int index) {
        Objects.requireNonNull(string, "string");
        int high = fromHexDigit(string.charAt(index));
        int low = fromHexDigit(string.charAt(index + 1));
        return (high << 4) | low;
    }

    /**
     * Returns the {@code int} value parsed from a string of up to eight hexadecimal characters.
     * The hexadecimal characters are parsed from most significant to least significant
     * using {@link #fromHexDigit(int)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param string a CharSequence containing up to eight hex characters
     * @return the value parsed from the string
     * @throws  IllegalArgumentException if the string length is greater than eight (8) or
     *      if any of the characters is not a hexadecimal character
     */
    public int fromHexDigits(CharSequence string) {
        Objects.requireNonNull(string, "string");
        int len = string.length();
        checkDigitCount(len, 8);
        int value = 0;
        for (int i = 0; i < len; i++) {
            value = (value << 4) + fromHexDigit(string.charAt(i));
        }
        return value;
    }

    /**
     * Returns the {@code int} value parsed from a string range of up to eight hexadecimal
     * characters.
     * The characters in the range from {@code index} to {@code index + length - 1}, inclusive,
     * are parsed from most significant to least significant using {@link #fromHexDigit(int)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param string a CharSequence containing the characters
     * @param index the index of the first character of the range
     * @param length the number of hexadecimal digits to parse
     * @return the value parsed from the string range
     * @throws  IndexOutOfBoundsException if the sub-range is out of bounds
     *          for the {@code CharSequence}
     * @throws  IllegalArgumentException if length is greater than eight (8) or if
     *          any of the characters is not a hexadecimal character
     */
    public int fromHexDigits(CharSequence string, int index, int length) {
        Objects.requireNonNull(string, "string");
        checkDigitCount(length, 8);
        Objects.checkFromIndexSize(index, length, string.length());
        int value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 4) + fromHexDigit(string.charAt(index + i));
        }
        return value;
    }

    /**
     * Returns the long value parsed from a string of up to sixteen hexadecimal characters.
     * The hexadecimal characters are parsed from most significant to least significant
     * using {@link #fromHexDigit(int)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param string a CharSequence containing up to sixteen hex characters
     * @return the value parsed from the string
     * @throws  IllegalArgumentException if the string length is greater than sixteen (16) or
     *         if any of the characters is not a hexadecimal character
     */
    public long fromHexDigitsToLong(CharSequence string) {
        Objects.requireNonNull(string, "string");
        int len = string.length();
        checkDigitCount(len, 16);
        long value = 0L;
        for (int i = 0; i < len; i++) {
            value = (value << 4) + fromHexDigit(string.charAt(i));
        }
        return value;
    }

    /**
     * Returns the long value parsed parsed from a string range of up to sixteen hexadecimal
     * characters.
     * The characters in the range from {@code index} to {@code index + length - 1}, inclusive,
     * are parsed from most significant to least significant using {@link #fromHexDigit(int)}.
     * The {@code delimiter}, {@code prefix} and {@code suffix} are not used.
     *
     * @param string a CharSequence containing the characters
     * @param index the index of the first character of the range
     * @param length the number of hexadecimal digits to parse
     * @return the value parsed from the string range
     * @throws  IndexOutOfBoundsException if the sub-range is out of bounds
     *          for the {@code CharSequence}
     * @throws  IllegalArgumentException if {@code length} is greater than sixteen (16) or
     *          if any of the characters is not a hexadecimal character
     */
    public long fromHexDigitsToLong(CharSequence string, int index, int length) {
        Objects.requireNonNull(string, "string");
        checkDigitCount(length, 16);
        Objects.checkFromIndexSize(index, length, string.length());
        long value = 0L;
        for (int i = 0; i < length; i++) {
            value = (value << 4) + fromHexDigit(string.charAt(index + i));
        }
        return value;
    }

    /**
     * Returns {@code true} if the other object is a {@code HexFormat}
     * with the same parameters.
     *
     * @param o an object, may be null
     * @return {@code true} if the other object is a {@code HexFormat} and the parameters
     *         {@code uppercase}, {@code delimiter}, {@code prefix}, and {@code suffix} are equal;
     *         otherwise {@code false}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HexFormat otherHex = (HexFormat) o;
        return delimiter.equals(otherHex.delimiter) &&
                prefix.equals(otherHex.prefix) &&
                suffix.equals(otherHex.suffix) &&
                Arrays.equals(digits, otherHex.digits);
    }

    /**
     * Returns a hashcode for this {@code HexFormat} that is consistent with
     * {@link #equals(Object) equals}.
     *
     * @return a hashcode for this {@code HexFormat}
     */
    @Override
    public int hashCode() {
        int result = Objects.hash(delimiter, prefix, suffix);
        result = 31 * result + Boolean.hashCode(Arrays.equals(digits, UPPERCASE_DIGITS));
        return result;
    }

    /**
     * Returns a description of the formatter parameters for {@code uppercase},
     * {@code delimiter}, {@code prefix}, and {@code suffix}.
     *
     * @return return a description of this {@code HexFormat}
     */
    @Override
    public String toString() {
        return escapeNL("uppercase: " + Arrays.equals(digits, UPPERCASE_DIGITS) +
                ", delimiter: \"" + delimiter +
                "\", prefix: \"" + prefix +
                "\", suffix: \"" + suffix + "\"");
    }
}
