// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package jdk.internal.icu.text;

import jdk.internal.icu.impl.Utility;

/**
 * <p>
 * Standalone utility class providing UTF16 character conversions and indexing conversions.
 * </p>
 * <p>
 * Code that uses strings alone rarely need modification. By design, UTF-16 does not allow overlap,
 * so searching for strings is a safe operation. Similarly, concatenation is always safe.
 * Substringing is safe if the start and end are both on UTF-32 boundaries. In normal code, the
 * values for start and end are on those boundaries, since they arose from operations like
 * searching. If not, the nearest UTF-32 boundaries can be determined using <code>bounds()</code>.
 * </p>
 * <strong>Examples:</strong>
 * <p>
 * The following examples illustrate use of some of these methods.
 *
 * <pre>
 * // iteration forwards: Original
 * for (int i = 0; i &lt; s.length(); ++i) {
 *     char ch = s.charAt(i);
 *     doSomethingWith(ch);
 * }
 *
 * // iteration forwards: Changes for UTF-32
 * int ch;
 * for (int i = 0; i &lt; s.length(); i += UTF16.getCharCount(ch)) {
 *     ch = UTF16.charAt(s, i);
 *     doSomethingWith(ch);
 * }
 *
 * // iteration backwards: Original
 * for (int i = s.length() - 1; i &gt;= 0; --i) {
 *     char ch = s.charAt(i);
 *     doSomethingWith(ch);
 * }
 *
 * // iteration backwards: Changes for UTF-32
 * int ch;
 * for (int i = s.length() - 1; i &gt; 0; i -= UTF16.getCharCount(ch)) {
 *     ch = UTF16.charAt(s, i);
 *     doSomethingWith(ch);
 * }
 * </pre>
 *
 * <strong>Notes:</strong>
 * <ul>
 * <li> <strong>Naming:</strong> For clarity, High and Low surrogates are called <code>Lead</code>
 * and <code>Trail</code> in the API, which gives a better sense of their ordering in a string.
 * <code>offset16</code> and <code>offset32</code> are used to distinguish offsets to UTF-16
 * boundaries vs offsets to UTF-32 boundaries. <code>int char32</code> is used to contain UTF-32
 * characters, as opposed to <code>char16</code>, which is a UTF-16 code unit. </li>
 * <li> <strong>Roundtripping Offsets:</strong> You can always roundtrip from a UTF-32 offset to a
 * UTF-16 offset and back. Because of the difference in structure, you can roundtrip from a UTF-16
 * offset to a UTF-32 offset and back if and only if <code>bounds(string, offset16) != TRAIL</code>.
 * </li>
 * <li> <strong>Exceptions:</strong> The error checking will throw an exception if indices are out
 * of bounds. Other than than that, all methods will behave reasonably, even if unmatched surrogates
 * or out-of-bounds UTF-32 values are present. <code>UCharacter.isLegal()</code> can be used to
 * check for validity if desired. </li>
 * <li> <strong>Unmatched Surrogates:</strong> If the string contains unmatched surrogates, then
 * these are counted as one UTF-32 value. This matches their iteration behavior, which is vital. It
 * also matches common display practice as missing glyphs (see the Unicode Standard Section 5.4,
 * 5.5). </li>
 * <li> <strong>Optimization:</strong> The method implementations may need optimization if the
 * compiler doesn't fold static final methods. Since surrogate pairs will form an exceeding small
 * percentage of all the text in the world, the singleton case should always be optimized for. </li>
 * </ul>
 *
 * @author Mark Davis, with help from Markus Scherer
 * @stable ICU 2.1
 */

public final class UTF16 {
    // public variables ---------------------------------------------------

    /**
     * Value returned in {@link #bounds(String, int) bounds()}.
     * These values are chosen specifically so that it actually represents the position of the
     * character [offset16 - (value &gt;&gt; 2), offset16 + (value &amp; 3)]
     *
     * @stable ICU 2.1
     */
    public static final int SINGLE_CHAR_BOUNDARY = 1, LEAD_SURROGATE_BOUNDARY = 2,
            TRAIL_SURROGATE_BOUNDARY = 5;

    /**
     * The lowest Unicode code point value.
     *
     * @stable ICU 2.1
     */
    public static final int CODEPOINT_MIN_VALUE = 0;

    /**
     * The highest Unicode code point value (scalar value) according to the Unicode Standard.
     *
     * @stable ICU 2.1
     */
    public static final int CODEPOINT_MAX_VALUE = 0x10ffff;

    /**
     * The minimum value for Supplementary code points
     *
     * @stable ICU 2.1
     */
    public static final int SUPPLEMENTARY_MIN_VALUE = 0x10000;

    /**
     * Lead surrogate minimum value
     *
     * @stable ICU 2.1
     */
    public static final int LEAD_SURROGATE_MIN_VALUE = 0xD800;

    /**
     * Trail surrogate minimum value
     *
     * @stable ICU 2.1
     */
    public static final int TRAIL_SURROGATE_MIN_VALUE = 0xDC00;

    /**
     * Lead surrogate maximum value
     *
     * @stable ICU 2.1
     */
    public static final int LEAD_SURROGATE_MAX_VALUE = 0xDBFF;

    /**
     * Trail surrogate maximum value
     *
     * @stable ICU 2.1
     */
    public static final int TRAIL_SURROGATE_MAX_VALUE = 0xDFFF;

    /**
     * Surrogate minimum value
     *
     * @stable ICU 2.1
     */
    public static final int SURROGATE_MIN_VALUE = LEAD_SURROGATE_MIN_VALUE;

    /**
     * Maximum surrogate value
     *
     * @stable ICU 2.1
     */
    public static final int SURROGATE_MAX_VALUE = TRAIL_SURROGATE_MAX_VALUE;

    /**
     * Lead surrogate bitmask
     */
    private static final int LEAD_SURROGATE_BITMASK = 0xFFFFFC00;

    /**
     * Trail surrogate bitmask
     */
    private static final int TRAIL_SURROGATE_BITMASK = 0xFFFFFC00;

    /**
     * Surrogate bitmask
     */
    private static final int SURROGATE_BITMASK = 0xFFFFF800;

    /**
     * Lead surrogate bits
     */
    private static final int LEAD_SURROGATE_BITS = 0xD800;

    /**
     * Trail surrogate bits
     */
    private static final int TRAIL_SURROGATE_BITS = 0xDC00;

    /**
     * Surrogate bits
     */
    private static final int SURROGATE_BITS = 0xD800;

    // constructor --------------------------------------------------------

    // /CLOVER:OFF
    /**
     * Prevent instance from being created.
     */
    private UTF16() {
    }

    // /CLOVER:ON
    // public method ------------------------------------------------------

    /**
     * Extract a single UTF-32 value from a string. Used when iterating forwards or backwards (with
     * <code>UTF16.getCharCount()</code>, as well as random access. If a validity check is
     * required, use <code><a href="../lang/UCharacter.html#isLegal(char)">
     * UCharacter.isLegal()</a></code>
     * on the return value. If the char retrieved is part of a surrogate pair, its supplementary
     * character will be returned. If a complete supplementary character is not found the incomplete
     * character will be returned
     *
     * @param source Array of UTF-16 chars
     * @param offset16 UTF-16 offset to the start of the character.
     * @return UTF-32 value for the UTF-32 value that contains the char at offset16. The boundaries
     *         of that codepoint are the same as in <code>bounds32()</code>.
     * @exception IndexOutOfBoundsException Thrown if offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int charAt(String source, int offset16) {
        char single = source.charAt(offset16);
        if (single < LEAD_SURROGATE_MIN_VALUE) {
            return single;
        }
        return _charAt(source, offset16, single);
    }

    private static int _charAt(String source, int offset16, char single) {
        if (single > TRAIL_SURROGATE_MAX_VALUE) {
            return single;
        }

        // Convert the UTF-16 surrogate pair if necessary.
        // For simplicity in usage, and because the frequency of pairs is
        // low, look both directions.

        if (single <= LEAD_SURROGATE_MAX_VALUE) {
            ++offset16;
            if (source.length() != offset16) {
                char trail = source.charAt(offset16);
                if (trail >= TRAIL_SURROGATE_MIN_VALUE && trail <= TRAIL_SURROGATE_MAX_VALUE) {
                    return Character.toCodePoint(single, trail);
                }
            }
        } else {
            --offset16;
            if (offset16 >= 0) {
                // single is a trail surrogate so
                char lead = source.charAt(offset16);
                if (lead >= LEAD_SURROGATE_MIN_VALUE && lead <= LEAD_SURROGATE_MAX_VALUE) {
                    return Character.toCodePoint(lead, single);
                }
            }
        }
        return single; // return unmatched surrogate
    }

    /**
     * Extract a single UTF-32 value from a string. Used when iterating forwards or backwards (with
     * <code>UTF16.getCharCount()</code>, as well as random access. If a validity check is
     * required, use <code><a href="../lang/UCharacter.html#isLegal(char)">
     * UCharacter.isLegal()</a></code>
     * on the return value. If the char retrieved is part of a surrogate pair, its supplementary
     * character will be returned. If a complete supplementary character is not found the incomplete
     * character will be returned
     *
     * @param source Array of UTF-16 chars
     * @param offset16 UTF-16 offset to the start of the character.
     * @return UTF-32 value for the UTF-32 value that contains the char at offset16. The boundaries
     *         of that codepoint are the same as in <code>bounds32()</code>.
     * @exception IndexOutOfBoundsException Thrown if offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int charAt(CharSequence source, int offset16) {
        char single = source.charAt(offset16);
        if (single < UTF16.LEAD_SURROGATE_MIN_VALUE) {
            return single;
        }
        return _charAt(source, offset16, single);
    }

    private static int _charAt(CharSequence source, int offset16, char single) {
        if (single > UTF16.TRAIL_SURROGATE_MAX_VALUE) {
            return single;
        }

        // Convert the UTF-16 surrogate pair if necessary.
        // For simplicity in usage, and because the frequency of pairs is
        // low, look both directions.

        if (single <= UTF16.LEAD_SURROGATE_MAX_VALUE) {
            ++offset16;
            if (source.length() != offset16) {
                char trail = source.charAt(offset16);
                if (trail >= UTF16.TRAIL_SURROGATE_MIN_VALUE
                        && trail <= UTF16.TRAIL_SURROGATE_MAX_VALUE) {
                    return Character.toCodePoint(single, trail);
                }
            }
        } else {
            --offset16;
            if (offset16 >= 0) {
                // single is a trail surrogate so
                char lead = source.charAt(offset16);
                if (lead >= UTF16.LEAD_SURROGATE_MIN_VALUE
                        && lead <= UTF16.LEAD_SURROGATE_MAX_VALUE) {
                    return Character.toCodePoint(lead, single);
                }
            }
        }
        return single; // return unmatched surrogate
    }

    /**
     * Extract a single UTF-32 value from a string. Used when iterating forwards or backwards (with
     * <code>UTF16.getCharCount()</code>, as well as random access. If a validity check is
     * required, use <code><a href="../lang/UCharacter.html#isLegal(char)">UCharacter.isLegal()
     * </a></code>
     * on the return value. If the char retrieved is part of a surrogate pair, its supplementary
     * character will be returned. If a complete supplementary character is not found the incomplete
     * character will be returned
     *
     * @param source UTF-16 chars string buffer
     * @param offset16 UTF-16 offset to the start of the character.
     * @return UTF-32 value for the UTF-32 value that contains the char at offset16. The boundaries
     *         of that codepoint are the same as in <code>bounds32()</code>.
     * @exception IndexOutOfBoundsException Thrown if offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int charAt(StringBuffer source, int offset16) {
        if (offset16 < 0 || offset16 >= source.length()) {
            throw new StringIndexOutOfBoundsException(offset16);
        }

        char single = source.charAt(offset16);
        if (!isSurrogate(single)) {
            return single;
        }

        // Convert the UTF-16 surrogate pair if necessary.
        // For simplicity in usage, and because the frequency of pairs is
        // low, look both directions.

        if (single <= LEAD_SURROGATE_MAX_VALUE) {
            ++offset16;
            if (source.length() != offset16) {
                char trail = source.charAt(offset16);
                if (isTrailSurrogate(trail))
                    return Character.toCodePoint(single, trail);
            }
        } else {
            --offset16;
            if (offset16 >= 0) {
                // single is a trail surrogate so
                char lead = source.charAt(offset16);
                if (isLeadSurrogate(lead)) {
                    return Character.toCodePoint(lead, single);
                }
            }
        }
        return single; // return unmatched surrogate
    }

    /**
     * Extract a single UTF-32 value from a substring. Used when iterating forwards or backwards
     * (with <code>UTF16.getCharCount()</code>, as well as random access. If a validity check is
     * required, use <code><a href="../lang/UCharacter.html#isLegal(char)">UCharacter.isLegal()
     * </a></code>
     * on the return value. If the char retrieved is part of a surrogate pair, its supplementary
     * character will be returned. If a complete supplementary character is not found the incomplete
     * character will be returned
     *
     * @param source Array of UTF-16 chars
     * @param start Offset to substring in the source array for analyzing
     * @param limit Offset to substring in the source array for analyzing
     * @param offset16 UTF-16 offset relative to start
     * @return UTF-32 value for the UTF-32 value that contains the char at offset16. The boundaries
     *         of that codepoint are the same as in <code>bounds32()</code>.
     * @exception IndexOutOfBoundsException Thrown if offset16 is not within the range of start and limit.
     * @stable ICU 2.1
     */
    public static int charAt(char source[], int start, int limit, int offset16) {
        offset16 += start;
        if (offset16 < start || offset16 >= limit) {
            throw new ArrayIndexOutOfBoundsException(offset16);
        }

        char single = source[offset16];
        if (!isSurrogate(single)) {
            return single;
        }

        // Convert the UTF-16 surrogate pair if necessary.
        // For simplicity in usage, and because the frequency of pairs is
        // low, look both directions.
        if (single <= LEAD_SURROGATE_MAX_VALUE) {
            offset16++;
            if (offset16 >= limit) {
                return single;
            }
            char trail = source[offset16];
            if (isTrailSurrogate(trail)) {
                return Character.toCodePoint(single, trail);
            }
        } else { // isTrailSurrogate(single), so
            if (offset16 == start) {
                return single;
            }
            offset16--;
            char lead = source[offset16];
            if (isLeadSurrogate(lead))
                return Character.toCodePoint(lead, single);
        }
        return single; // return unmatched surrogate
    }

    /**
     * Extract a single UTF-32 value from a string. Used when iterating forwards or backwards (with
     * <code>UTF16.getCharCount()</code>, as well as random access. If a validity check is
     * required, use <code><a href="../lang/UCharacter.html#isLegal(char)">UCharacter.isLegal()
     * </a></code>
     * on the return value. If the char retrieved is part of a surrogate pair, its supplementary
     * character will be returned. If a complete supplementary character is not found the incomplete
     * character will be returned
     *
     * @param source UTF-16 chars string buffer
     * @param offset16 UTF-16 offset to the start of the character.
     * @return UTF-32 value for the UTF-32 value that contains the char at offset16. The boundaries
     *         of that codepoint are the same as in <code>bounds32()</code>.
     * @exception IndexOutOfBoundsException Thrown if offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int charAt(Replaceable source, int offset16) {
        if (offset16 < 0 || offset16 >= source.length()) {
            throw new StringIndexOutOfBoundsException(offset16);
        }

        char single = source.charAt(offset16);
        if (!isSurrogate(single)) {
            return single;
        }

        // Convert the UTF-16 surrogate pair if necessary.
        // For simplicity in usage, and because the frequency of pairs is
        // low, look both directions.

        if (single <= LEAD_SURROGATE_MAX_VALUE) {
            ++offset16;
            if (source.length() != offset16) {
                char trail = source.charAt(offset16);
                if (isTrailSurrogate(trail))
                    return Character.toCodePoint(single, trail);
            }
        } else {
            --offset16;
            if (offset16 >= 0) {
                // single is a trail surrogate so
                char lead = source.charAt(offset16);
                if (isLeadSurrogate(lead)) {
                    return Character.toCodePoint(lead, single);
                }
            }
        }
        return single; // return unmatched surrogate
    }

    /**
     * Determines how many chars this char32 requires. If a validity check is required, use <code>
     * <a href="../lang/UCharacter.html#isLegal(char)">isLegal()</a></code>
     * on char32 before calling.
     *
     * @param char32 The input codepoint.
     * @return 2 if is in supplementary space, otherwise 1.
     * @stable ICU 2.1
     */
    public static int getCharCount(int char32) {
        if (char32 < SUPPLEMENTARY_MIN_VALUE) {
            return 1;
        }
        return 2;
    }

    /**
     * Returns the type of the boundaries around the char at offset16. Used for random access.
     *
     * @param source Text to analyse
     * @param offset16 UTF-16 offset
     * @return
     *            <ul>
     *            <li> SINGLE_CHAR_BOUNDARY : a single char; the bounds are [offset16, offset16+1]
     *            <li> LEAD_SURROGATE_BOUNDARY : a surrogate pair starting at offset16; the bounds
     *            are [offset16, offset16 + 2]
     *            <li> TRAIL_SURROGATE_BOUNDARY : a surrogate pair starting at offset16 - 1; the
     *            bounds are [offset16 - 1, offset16 + 1]
     *            </ul>
     *            For bit-twiddlers, the return values for these are chosen so that the boundaries
     *            can be gotten by: [offset16 - (value &gt;&gt; 2), offset16 + (value &amp; 3)].
     * @exception IndexOutOfBoundsException If offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int bounds(String source, int offset16) {
        char ch = source.charAt(offset16);
        if (isSurrogate(ch)) {
            if (isLeadSurrogate(ch)) {
                if (++offset16 < source.length() && isTrailSurrogate(source.charAt(offset16))) {
                    return LEAD_SURROGATE_BOUNDARY;
                }
            } else {
                // isTrailSurrogate(ch), so
                --offset16;
                if (offset16 >= 0 && isLeadSurrogate(source.charAt(offset16))) {
                    return TRAIL_SURROGATE_BOUNDARY;
                }
            }
        }
        return SINGLE_CHAR_BOUNDARY;
    }

    /**
     * Returns the type of the boundaries around the char at offset16. Used for random access.
     *
     * @param source String buffer to analyse
     * @param offset16 UTF16 offset
     * @return
     *            <ul>
     *            <li> SINGLE_CHAR_BOUNDARY : a single char; the bounds are [offset16, offset16 + 1]
     *            <li> LEAD_SURROGATE_BOUNDARY : a surrogate pair starting at offset16; the bounds
     *            are [offset16, offset16 + 2]
     *            <li> TRAIL_SURROGATE_BOUNDARY : a surrogate pair starting at offset16 - 1; the
     *            bounds are [offset16 - 1, offset16 + 1]
     *            </ul>
     *            For bit-twiddlers, the return values for these are chosen so that the boundaries
     *            can be gotten by: [offset16 - (value &gt;&gt; 2), offset16 + (value &amp; 3)].
     * @exception IndexOutOfBoundsException If offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int bounds(StringBuffer source, int offset16) {
        char ch = source.charAt(offset16);
        if (isSurrogate(ch)) {
            if (isLeadSurrogate(ch)) {
                if (++offset16 < source.length() && isTrailSurrogate(source.charAt(offset16))) {
                    return LEAD_SURROGATE_BOUNDARY;
                }
            } else {
                // isTrailSurrogate(ch), so
                --offset16;
                if (offset16 >= 0 && isLeadSurrogate(source.charAt(offset16))) {
                    return TRAIL_SURROGATE_BOUNDARY;
                }
            }
        }
        return SINGLE_CHAR_BOUNDARY;
    }

    /**
     * Returns the type of the boundaries around the char at offset16. Used for random access. Note
     * that the boundaries are determined with respect to the subarray, hence the char array
     * {0xD800, 0xDC00} has the result SINGLE_CHAR_BOUNDARY for start = offset16 = 0 and limit = 1.
     *
     * @param source Char array to analyse
     * @param start Offset to substring in the source array for analyzing
     * @param limit Offset to substring in the source array for analyzing
     * @param offset16 UTF16 offset relative to start
     * @return
     *            <ul>
     *            <li> SINGLE_CHAR_BOUNDARY : a single char; the bounds are
     *            <li> LEAD_SURROGATE_BOUNDARY : a surrogate pair starting at offset16; the bounds
     *            are [offset16, offset16 + 2]
     *            <li> TRAIL_SURROGATE_BOUNDARY : a surrogate pair starting at offset16 - 1; the
     *            bounds are [offset16 - 1, offset16 + 1]
     *            </ul>
     *            For bit-twiddlers, the boundary values for these are chosen so that the boundaries
     *            can be gotten by: [offset16 - (boundvalue &gt;&gt; 2), offset16 + (boundvalue &amp; 3)].
     * @exception IndexOutOfBoundsException If offset16 is not within the range of start and limit.
     * @stable ICU 2.1
     */
    public static int bounds(char source[], int start, int limit, int offset16) {
        offset16 += start;
        if (offset16 < start || offset16 >= limit) {
            throw new ArrayIndexOutOfBoundsException(offset16);
        }
        char ch = source[offset16];
        if (isSurrogate(ch)) {
            if (isLeadSurrogate(ch)) {
                ++offset16;
                if (offset16 < limit && isTrailSurrogate(source[offset16])) {
                    return LEAD_SURROGATE_BOUNDARY;
                }
            } else { // isTrailSurrogate(ch), so
                --offset16;
                if (offset16 >= start && isLeadSurrogate(source[offset16])) {
                    return TRAIL_SURROGATE_BOUNDARY;
                }
            }
        }
        return SINGLE_CHAR_BOUNDARY;
    }

    /**
     * Determines whether the code point is a surrogate.
     *
     * @param codePoint The input character.
     *        (In ICU 2.1-69 the type of this parameter was <code>char</code>.)
     * @return true If the input code point is a surrogate.
     * @stable ICU 70
     */
    public static boolean isSurrogate(int codePoint) {
        return (codePoint & SURROGATE_BITMASK) == SURROGATE_BITS;
    }

    /**
     * Determines whether the code point is a trail surrogate.
     *
     * @param codePoint The input character.
     *        (In ICU 2.1-69 the type of this parameter was <code>char</code>.)
     * @return true If the input code point is a trail surrogate.
     * @stable ICU 70
     */
    public static boolean isTrailSurrogate(int codePoint) {
        return (codePoint & TRAIL_SURROGATE_BITMASK) == TRAIL_SURROGATE_BITS;
    }

    /**
     * Determines whether the code point is a lead surrogate.
     *
     * @param codePoint The input character.
     *        (In ICU 2.1-69 the type of this parameter was <code>char</code>.)
     * @return true If the input code point is a lead surrogate
     * @stable ICU 70
     */
    public static boolean isLeadSurrogate(int codePoint) {
        return (codePoint & LEAD_SURROGATE_BITMASK) == LEAD_SURROGATE_BITS;
    }

    /**
     * Returns the lead surrogate. If a validity check is required, use
     * <code><a href="../lang/UCharacter.html#isLegal(char)">isLegal()</a></code> on char32
     * before calling.
     *
     * @param char32 The input character.
     * @return lead surrogate if the getCharCount(ch) is 2; <br>
     *         and 0 otherwise (note: 0 is not a valid lead surrogate).
     * @stable ICU 2.1
     */
    public static char getLeadSurrogate(int char32) {
        if (char32 >= SUPPLEMENTARY_MIN_VALUE) {
            return (char) (LEAD_SURROGATE_OFFSET_ + (char32 >> LEAD_SURROGATE_SHIFT_));
        }
        return 0;
    }

    /**
     * Returns the trail surrogate. If a validity check is required, use
     * <code><a href="../lang/UCharacter.html#isLegal(char)">isLegal()</a></code> on char32
     * before calling.
     *
     * @param char32 The input character.
     * @return the trail surrogate if the getCharCount(ch) is 2; <br>
     *         otherwise the character itself
     * @stable ICU 2.1
     */
    public static char getTrailSurrogate(int char32) {
        if (char32 >= SUPPLEMENTARY_MIN_VALUE) {
            return (char) (TRAIL_SURROGATE_MIN_VALUE + (char32 & TRAIL_SURROGATE_MASK_));
        }
        return (char) char32;
    }

    /**
     * Convenience method corresponding to String.valueOf(char). Returns a one or two char string
     * containing the UTF-32 value in UTF16 format. If a validity check is required, use
     * {@link jdk.internal.icu.lang.UCharacter#isLegal(int)} on char32 before calling.
     *
     * @param char32 The input character.
     * @return string value of char32 in UTF16 format
     * @exception IllegalArgumentException Thrown if char32 is a invalid codepoint.
     * @stable ICU 2.1
     */
    public static String valueOf(int char32) {
        if (char32 < CODEPOINT_MIN_VALUE || char32 > CODEPOINT_MAX_VALUE) {
            throw new IllegalArgumentException("Illegal codepoint");
        }
        return toString(char32);
    }

    /**
     * Convenience method corresponding to String.valueOf(codepoint at offset16). Returns a one or
     * two char string containing the UTF-32 value in UTF16 format. If offset16 indexes a surrogate
     * character, the whole supplementary codepoint will be returned. If a validity check is
     * required, use {@link jdk.internal.icu.lang.UCharacter#isLegal(int)} on the
     * codepoint at offset16 before calling. The result returned will be a newly created String
     * obtained by calling source.substring(..) with the appropriate indexes.
     *
     * @param source The input string.
     * @param offset16 The UTF16 index to the codepoint in source
     * @return string value of char32 in UTF16 format
     * @stable ICU 2.1
     */
    public static String valueOf(String source, int offset16) {
        switch (bounds(source, offset16)) {
        case LEAD_SURROGATE_BOUNDARY:
            return source.substring(offset16, offset16 + 2);
        case TRAIL_SURROGATE_BOUNDARY:
            return source.substring(offset16 - 1, offset16 + 1);
        default:
            return source.substring(offset16, offset16 + 1);
        }
    }

    /**
     * Convenience method corresponding to StringBuffer.valueOf(codepoint at offset16). Returns a
     * one or two char string containing the UTF-32 value in UTF16 format. If offset16 indexes a
     * surrogate character, the whole supplementary codepoint will be returned. If a validity check
     * is required, use {@link jdk.internal.icu.lang.UCharacter#isLegal(int)} on
     * the codepoint at offset16 before calling. The result returned will be a newly created String
     * obtained by calling source.substring(..) with the appropriate indexes.
     *
     * @param source The input string buffer.
     * @param offset16 The UTF16 index to the codepoint in source
     * @return string value of char32 in UTF16 format
     * @stable ICU 2.1
     */
    public static String valueOf(StringBuffer source, int offset16) {
        switch (bounds(source, offset16)) {
        case LEAD_SURROGATE_BOUNDARY:
            return source.substring(offset16, offset16 + 2);
        case TRAIL_SURROGATE_BOUNDARY:
            return source.substring(offset16 - 1, offset16 + 1);
        default:
            return source.substring(offset16, offset16 + 1);
        }
    }

    /**
     * Convenience method. Returns a one or two char string containing the UTF-32 value in UTF16
     * format. If offset16 indexes a surrogate character, the whole supplementary codepoint will be
     * returned, except when either the leading or trailing surrogate character lies out of the
     * specified subarray. In the latter case, only the surrogate character within bounds will be
     * returned. If a validity check is required, use
     * {@link jdk.internal.icu.lang.UCharacter#isLegal(int)} on the codepoint at
     * offset16 before calling. The result returned will be a newly created String containing the
     * relevant characters.
     *
     * @param source The input char array.
     * @param start Start index of the subarray
     * @param limit End index of the subarray
     * @param offset16 The UTF16 index to the codepoint in source relative to start
     * @return string value of char32 in UTF16 format
     * @stable ICU 2.1
     */
    public static String valueOf(char source[], int start, int limit, int offset16) {
        switch (bounds(source, start, limit, offset16)) {
        case LEAD_SURROGATE_BOUNDARY:
            return new String(source, start + offset16, 2);
        case TRAIL_SURROGATE_BOUNDARY:
            return new String(source, start + offset16 - 1, 2);
        }
        return new String(source, start + offset16, 1);
    }

    /**
     * Returns the UTF-16 offset that corresponds to a UTF-32 offset. Used for random access. See
     * the {@link UTF16 class description} for notes on roundtripping.
     *
     * @param source The UTF-16 string
     * @param offset32 UTF-32 offset
     * @return UTF-16 offset
     * @exception IndexOutOfBoundsException If offset32 is out of bounds.
     * @stable ICU 2.1
     */
    public static int findOffsetFromCodePoint(String source, int offset32) {
        char ch;
        int size = source.length(), result = 0, count = offset32;
        if (offset32 < 0 || offset32 > size) {
            throw new StringIndexOutOfBoundsException(offset32);
        }
        while (result < size && count > 0) {
            ch = source.charAt(result);
            if (isLeadSurrogate(ch) && ((result + 1) < size)
                    && isTrailSurrogate(source.charAt(result + 1))) {
                result++;
            }

            count--;
            result++;
        }
        if (count != 0) {
            throw new StringIndexOutOfBoundsException(offset32);
        }
        return result;
    }

    /**
     * Returns the UTF-16 offset that corresponds to a UTF-32 offset. Used for random access. See
     * the {@link UTF16 class description} for notes on roundtripping.
     *
     * @param source The UTF-16 string buffer
     * @param offset32 UTF-32 offset
     * @return UTF-16 offset
     * @exception IndexOutOfBoundsException If offset32 is out of bounds.
     * @stable ICU 2.1
     */
    public static int findOffsetFromCodePoint(StringBuffer source, int offset32) {
        char ch;
        int size = source.length(), result = 0, count = offset32;
        if (offset32 < 0 || offset32 > size) {
            throw new StringIndexOutOfBoundsException(offset32);
        }
        while (result < size && count > 0) {
            ch = source.charAt(result);
            if (isLeadSurrogate(ch) && ((result + 1) < size)
                    && isTrailSurrogate(source.charAt(result + 1))) {
                result++;
            }

            count--;
            result++;
        }
        if (count != 0) {
            throw new StringIndexOutOfBoundsException(offset32);
        }
        return result;
    }

    /**
     * Returns the UTF-16 offset that corresponds to a UTF-32 offset. Used for random access. See
     * the {@link UTF16 class description} for notes on roundtripping.
     *
     * @param source The UTF-16 char array whose substring is to be analysed
     * @param start Offset of the substring to be analysed
     * @param limit Offset of the substring to be analysed
     * @param offset32 UTF-32 offset relative to start
     * @return UTF-16 offset relative to start
     * @exception IndexOutOfBoundsException If offset32 is out of bounds.
     * @stable ICU 2.1
     */
    public static int findOffsetFromCodePoint(char source[], int start, int limit, int offset32) {
        char ch;
        int result = start, count = offset32;
        if (offset32 > limit - start) {
            throw new ArrayIndexOutOfBoundsException(offset32);
        }
        while (result < limit && count > 0) {
            ch = source[result];
            if (isLeadSurrogate(ch) && ((result + 1) < limit)
                    && isTrailSurrogate(source[result + 1])) {
                result++;
            }

            count--;
            result++;
        }
        if (count != 0) {
            throw new ArrayIndexOutOfBoundsException(offset32);
        }
        return result - start;
    }

    /**
     * Returns the UTF-32 offset corresponding to the first UTF-32 boundary at or after the given
     * UTF-16 offset. Used for random access. See the {@link UTF16 class description} for
     * notes on roundtripping.<br>
     * <i>Note: If the UTF-16 offset is into the middle of a surrogate pair, then the UTF-32 offset
     * of the <strong>lead</strong> of the pair is returned. </i>
     * <p>
     * To find the UTF-32 length of a string, use:
     *
     * <pre>
     * len32 = countCodePoint(source, source.length());
     * </pre>
     *
     * @param source Text to analyse
     * @param offset16 UTF-16 offset &lt; source text length.
     * @return UTF-32 offset
     * @exception IndexOutOfBoundsException If offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int findCodePointOffset(String source, int offset16) {
        if (offset16 < 0 || offset16 > source.length()) {
            throw new StringIndexOutOfBoundsException(offset16);
        }

        int result = 0;
        char ch;
        boolean hadLeadSurrogate = false;

        for (int i = 0; i < offset16; ++i) {
            ch = source.charAt(i);
            if (hadLeadSurrogate && isTrailSurrogate(ch)) {
                hadLeadSurrogate = false; // count valid trail as zero
            } else {
                hadLeadSurrogate = isLeadSurrogate(ch);
                ++result; // count others as 1
            }
        }

        if (offset16 == source.length()) {
            return result;
        }

        // end of source being the less significant surrogate character
        // shift result back to the start of the supplementary character
        if (hadLeadSurrogate && (isTrailSurrogate(source.charAt(offset16)))) {
            result--;
        }

        return result;
    }

    /**
     * Returns the UTF-32 offset corresponding to the first UTF-32 boundary at the given UTF-16
     * offset. Used for random access. See the {@link UTF16 class description} for notes on
     * roundtripping.<br>
     * <i>Note: If the UTF-16 offset is into the middle of a surrogate pair, then the UTF-32 offset
     * of the <strong>lead</strong> of the pair is returned. </i>
     * <p>
     * To find the UTF-32 length of a string, use:
     *
     * <pre>
     * len32 = countCodePoint(source);
     * </pre>
     *
     * @param source Text to analyse
     * @param offset16 UTF-16 offset &lt; source text length.
     * @return UTF-32 offset
     * @exception IndexOutOfBoundsException If offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int findCodePointOffset(StringBuffer source, int offset16) {
        if (offset16 < 0 || offset16 > source.length()) {
            throw new StringIndexOutOfBoundsException(offset16);
        }

        int result = 0;
        char ch;
        boolean hadLeadSurrogate = false;

        for (int i = 0; i < offset16; ++i) {
            ch = source.charAt(i);
            if (hadLeadSurrogate && isTrailSurrogate(ch)) {
                hadLeadSurrogate = false; // count valid trail as zero
            } else {
                hadLeadSurrogate = isLeadSurrogate(ch);
                ++result; // count others as 1
            }
        }

        if (offset16 == source.length()) {
            return result;
        }

        // end of source being the less significant surrogate character
        // shift result back to the start of the supplementary character
        if (hadLeadSurrogate && (isTrailSurrogate(source.charAt(offset16)))) {
            result--;
        }

        return result;
    }

    /**
     * Returns the UTF-32 offset corresponding to the first UTF-32 boundary at the given UTF-16
     * offset. Used for random access. See the {@link UTF16 class description} for notes on
     * roundtripping.<br>
     * <i>Note: If the UTF-16 offset is into the middle of a surrogate pair, then the UTF-32 offset
     * of the <strong>lead</strong> of the pair is returned. </i>
     * <p>
     * To find the UTF-32 length of a substring, use:
     *
     * <pre>
     * len32 = countCodePoint(source, start, limit);
     * </pre>
     *
     * @param source Text to analyse
     * @param start Offset of the substring
     * @param limit Offset of the substring
     * @param offset16 UTF-16 relative to start
     * @return UTF-32 offset relative to start
     * @exception IndexOutOfBoundsException If offset16 is not within the range of start and limit.
     * @stable ICU 2.1
     */
    public static int findCodePointOffset(char source[], int start, int limit, int offset16) {
        offset16 += start;
        if (offset16 > limit) {
            throw new StringIndexOutOfBoundsException(offset16);
        }

        int result = 0;
        char ch;
        boolean hadLeadSurrogate = false;

        for (int i = start; i < offset16; ++i) {
            ch = source[i];
            if (hadLeadSurrogate && isTrailSurrogate(ch)) {
                hadLeadSurrogate = false; // count valid trail as zero
            } else {
                hadLeadSurrogate = isLeadSurrogate(ch);
                ++result; // count others as 1
            }
        }

        if (offset16 == limit) {
            return result;
        }

        // end of source being the less significant surrogate character
        // shift result back to the start of the supplementary character
        if (hadLeadSurrogate && (isTrailSurrogate(source[offset16]))) {
            result--;
        }

        return result;
    }

    /**
     * Append a single UTF-32 value to the end of a StringBuffer. If a validity check is required,
     * use {@link jdk.internal.icu.lang.UCharacter#isLegal(int)} on char32 before
     * calling.
     *
     * @param target The buffer to append to
     * @param char32 Value to append.
     * @return the updated StringBuffer
     * @exception IllegalArgumentException Thrown when char32 does not lie within the range of the Unicode codepoints
     * @stable ICU 2.1
     */
    public static StringBuffer append(StringBuffer target, int char32) {
        // Check for irregular values
        if (char32 < CODEPOINT_MIN_VALUE || char32 > CODEPOINT_MAX_VALUE) {
            throw new IllegalArgumentException("Illegal codepoint: " + Integer.toHexString(char32));
        }

        // Write the UTF-16 values
        if (char32 >= SUPPLEMENTARY_MIN_VALUE) {
            target.append(getLeadSurrogate(char32));
            target.append(getTrailSurrogate(char32));
        } else {
            target.append((char) char32);
        }
        return target;
    }

    /**
     * Cover JDK 1.5 APIs. Append the code point to the buffer and return the buffer as a
     * convenience.
     *
     * @param target The buffer to append to
     * @param cp The code point to append
     * @return the updated StringBuffer
     * @throws IllegalArgumentException If cp is not a valid code point
     * @stable ICU 3.0
     */
    public static StringBuffer appendCodePoint(StringBuffer target, int cp) {
        return append(target, cp);
    }

    /**
     * Adds a codepoint to offset16 position of the argument char array.
     *
     * @param target Char array to be append with the new code point
     * @param limit UTF16 offset which the codepoint will be appended.
     * @param char32 Code point to be appended
     * @return offset after char32 in the array.
     * @exception IllegalArgumentException Thrown if there is not enough space for the append, or when char32 does not
     *                lie within the range of the Unicode codepoints.
     * @stable ICU 2.1
     */
    public static int append(char[] target, int limit, int char32) {
        // Check for irregular values
        if (char32 < CODEPOINT_MIN_VALUE || char32 > CODEPOINT_MAX_VALUE) {
            throw new IllegalArgumentException("Illegal codepoint");
        }
        // Write the UTF-16 values
        if (char32 >= SUPPLEMENTARY_MIN_VALUE) {
            target[limit++] = getLeadSurrogate(char32);
            target[limit++] = getTrailSurrogate(char32);
        } else {
            target[limit++] = (char) char32;
        }
        return limit;
    }

    /**
     * Number of codepoints in a UTF16 String
     *
     * @param source UTF16 string
     * @return number of codepoint in string
     * @stable ICU 2.1
     */
    public static int countCodePoint(String source) {
        if (source == null || source.length() == 0) {
            return 0;
        }
        return findCodePointOffset(source, source.length());
    }

    /**
     * Number of codepoints in a UTF16 String buffer
     *
     * @param source UTF16 string buffer
     * @return number of codepoint in string
     * @stable ICU 2.1
     */
    public static int countCodePoint(StringBuffer source) {
        if (source == null || source.length() == 0) {
            return 0;
        }
        return findCodePointOffset(source, source.length());
    }

    /**
     * Number of codepoints in a UTF16 char array substring
     *
     * @param source UTF16 char array
     * @param start Offset of the substring
     * @param limit Offset of the substring
     * @return number of codepoint in the substring
     * @exception IndexOutOfBoundsException If start and limit are not valid.
     * @stable ICU 2.1
     */
    public static int countCodePoint(char source[], int start, int limit) {
        if (source == null || source.length == 0) {
            return 0;
        }
        return findCodePointOffset(source, start, limit, limit - start);
    }

    /**
     * Set a code point into a UTF16 position. Adjusts target according if we are replacing a
     * non-supplementary codepoint with a supplementary and vice versa.
     *
     * @param target Stringbuffer
     * @param offset16 UTF16 position to insert into
     * @param char32 Code point
     * @stable ICU 2.1
     */
    public static void setCharAt(StringBuffer target, int offset16, int char32) {
        int count = 1;
        char single = target.charAt(offset16);

        if (isSurrogate(single)) {
            // pairs of the surrogate with offset16 at the lead char found
            if (isLeadSurrogate(single) && (target.length() > offset16 + 1)
                    && isTrailSurrogate(target.charAt(offset16 + 1))) {
                count++;
            } else {
                // pairs of the surrogate with offset16 at the trail char
                // found
                if (isTrailSurrogate(single) && (offset16 > 0)
                        && isLeadSurrogate(target.charAt(offset16 - 1))) {
                    offset16--;
                    count++;
                }
            }
        }
        target.replace(offset16, offset16 + count, valueOf(char32));
    }

    /**
     * Set a code point into a UTF16 position in a char array. Adjusts target according if we are
     * replacing a non-supplementary codepoint with a supplementary and vice versa.
     *
     * @param target char array
     * @param limit numbers of valid chars in target, different from target.length. limit counts the
     *            number of chars in target that represents a string, not the size of array target.
     * @param offset16 UTF16 position to insert into
     * @param char32 code point
     * @return new number of chars in target that represents a string
     * @exception IndexOutOfBoundsException if offset16 is out of range
     * @stable ICU 2.1
     */
    public static int setCharAt(char target[], int limit, int offset16, int char32) {
        if (offset16 >= limit) {
            throw new ArrayIndexOutOfBoundsException(offset16);
        }
        int count = 1;
        char single = target[offset16];

        if (isSurrogate(single)) {
            // pairs of the surrogate with offset16 at the lead char found
            if (isLeadSurrogate(single) && (target.length > offset16 + 1)
                    && isTrailSurrogate(target[offset16 + 1])) {
                count++;
            } else {
                // pairs of the surrogate with offset16 at the trail char
                // found
                if (isTrailSurrogate(single) && (offset16 > 0)
                        && isLeadSurrogate(target[offset16 - 1])) {
                    offset16--;
                    count++;
                }
            }
        }

        String str = valueOf(char32);
        int result = limit;
        int strlength = str.length();
        target[offset16] = str.charAt(0);
        if (count == strlength) {
            if (count == 2) {
                target[offset16 + 1] = str.charAt(1);
            }
        } else {
            // this is not exact match in space, we'll have to do some
            // shifting
            System.arraycopy(target, offset16 + count, target, offset16 + strlength, limit
                    - (offset16 + count));
            if (count < strlength) {
                // char32 is a supplementary character trying to squeeze into
                // a non-supplementary space
                target[offset16 + 1] = str.charAt(1);
                result++;
                if (result < target.length) {
                    target[result] = 0;
                }
            } else {
                // char32 is a non-supplementary character trying to fill
                // into a supplementary space
                result--;
                target[result] = 0;
            }
        }
        return result;
    }

    /**
     * Shifts offset16 by the argument number of codepoints
     *
     * @param source string
     * @param offset16 UTF16 position to shift
     * @param shift32 number of codepoints to shift
     * @return new shifted offset16
     * @exception IndexOutOfBoundsException if the new offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int moveCodePointOffset(String source, int offset16, int shift32) {
        int result = offset16;
        int size = source.length();
        int count;
        char ch;
        if (offset16 < 0 || offset16 > size) {
            throw new StringIndexOutOfBoundsException(offset16);
        }
        if (shift32 > 0) {
            if (shift32 + offset16 > size) {
                throw new StringIndexOutOfBoundsException(offset16);
            }
            count = shift32;
            while (result < size && count > 0) {
                ch = source.charAt(result);
                if (isLeadSurrogate(ch) && ((result + 1) < size)
                        && isTrailSurrogate(source.charAt(result + 1))) {
                    result++;
                }
                count--;
                result++;
            }
        } else {
            if (offset16 + shift32 < 0) {
                throw new StringIndexOutOfBoundsException(offset16);
            }
            for (count = -shift32; count > 0; count--) {
                result--;
                if (result < 0) {
                    break;
                }
                ch = source.charAt(result);
                if (isTrailSurrogate(ch) && result > 0
                        && isLeadSurrogate(source.charAt(result - 1))) {
                    result--;
                }
            }
        }
        if (count != 0) {
            throw new StringIndexOutOfBoundsException(shift32);
        }
        return result;
    }

    /**
     * Shifts offset16 by the argument number of codepoints
     *
     * @param source String buffer
     * @param offset16 UTF16 position to shift
     * @param shift32 Number of codepoints to shift
     * @return new shifted offset16
     * @exception IndexOutOfBoundsException If the new offset16 is out of bounds.
     * @stable ICU 2.1
     */
    public static int moveCodePointOffset(StringBuffer source, int offset16, int shift32) {
        int result = offset16;
        int size = source.length();
        int count;
        char ch;
        if (offset16 < 0 || offset16 > size) {
            throw new StringIndexOutOfBoundsException(offset16);
        }
        if (shift32 > 0) {
            if (shift32 + offset16 > size) {
                throw new StringIndexOutOfBoundsException(offset16);
            }
            count = shift32;
            while (result < size && count > 0) {
                ch = source.charAt(result);
                if (isLeadSurrogate(ch) && ((result + 1) < size)
                        && isTrailSurrogate(source.charAt(result + 1))) {
                    result++;
                }
                count--;
                result++;
            }
        } else {
            if (offset16 + shift32 < 0) {
                throw new StringIndexOutOfBoundsException(offset16);
            }
            for (count = -shift32; count > 0; count--) {
                result--;
                if (result < 0) {
                    break;
                }
                ch = source.charAt(result);
                if (isTrailSurrogate(ch) && result > 0
                        && isLeadSurrogate(source.charAt(result - 1))) {
                    result--;
                }
            }
        }
        if (count != 0) {
            throw new StringIndexOutOfBoundsException(shift32);
        }
        return result;
    }

    /**
     * Shifts offset16 by the argument number of codepoints within a subarray.
     *
     * @param source Char array
     * @param start Position of the subarray to be performed on
     * @param limit Position of the subarray to be performed on
     * @param offset16 UTF16 position to shift relative to start
     * @param shift32 Number of codepoints to shift
     * @return new shifted offset16 relative to start
     * @exception IndexOutOfBoundsException If the new offset16 is out of bounds with respect to the subarray or the
     *                subarray bounds are out of range.
     * @stable ICU 2.1
     */
    public static int moveCodePointOffset(char source[], int start, int limit, int offset16,
            int shift32) {
        int size = source.length;
        int count;
        char ch;
        int result = offset16 + start;
        if (start < 0 || limit < start) {
            throw new StringIndexOutOfBoundsException(start);
        }
        if (limit > size) {
            throw new StringIndexOutOfBoundsException(limit);
        }
        if (offset16 < 0 || result > limit) {
            throw new StringIndexOutOfBoundsException(offset16);
        }
        if (shift32 > 0) {
            if (shift32 + result > size) {
                throw new StringIndexOutOfBoundsException(result);
            }
            count = shift32;
            while (result < limit && count > 0) {
                ch = source[result];
                if (isLeadSurrogate(ch) && (result + 1 < limit)
                        && isTrailSurrogate(source[result + 1])) {
                    result++;
                }
                count--;
                result++;
            }
        } else {
            if (result + shift32 < start) {
                throw new StringIndexOutOfBoundsException(result);
            }
            for (count = -shift32; count > 0; count--) {
                result--;
                if (result < start) {
                    break;
                }
                ch = source[result];
                if (isTrailSurrogate(ch) && result > start && isLeadSurrogate(source[result - 1])) {
                    result--;
                }
            }
        }
        if (count != 0) {
            throw new StringIndexOutOfBoundsException(shift32);
        }
        result -= start;
        return result;
    }

    /**
     * Inserts char32 codepoint into target at the argument offset16. If the offset16 is in the
     * middle of a supplementary codepoint, char32 will be inserted after the supplementary
     * codepoint. The length of target increases by one if codepoint is non-supplementary, 2
     * otherwise.
     * <p>
     * The overall effect is exactly as if the argument were converted to a string by the method
     * valueOf(char) and the characters in that string were then inserted into target at the
     * position indicated by offset16.
     * </p>
     * <p>
     * The offset argument must be greater than or equal to 0, and less than or equal to the length
     * of source.
     *
     * @param target String buffer to insert to
     * @param offset16 Offset which char32 will be inserted in
     * @param char32 Codepoint to be inserted
     * @return a reference to target
     * @exception IndexOutOfBoundsException Thrown if offset16 is invalid.
     * @stable ICU 2.1
     */
    public static StringBuffer insert(StringBuffer target, int offset16, int char32) {
        String str = valueOf(char32);
        if (offset16 != target.length() && bounds(target, offset16) == TRAIL_SURROGATE_BOUNDARY) {
            offset16++;
        }
        target.insert(offset16, str);
        return target;
    }

    /**
     * Inserts char32 codepoint into target at the argument offset16. If the offset16 is in the
     * middle of a supplementary codepoint, char32 will be inserted after the supplementary
     * codepoint. Limit increases by one if codepoint is non-supplementary, 2 otherwise.
     * <p>
     * The overall effect is exactly as if the argument were converted to a string by the method
     * valueOf(char) and the characters in that string were then inserted into target at the
     * position indicated by offset16.
     * </p>
     * <p>
     * The offset argument must be greater than or equal to 0, and less than or equal to the limit.
     *
     * @param target Char array to insert to
     * @param limit End index of the char array, limit &lt;= target.length
     * @param offset16 Offset which char32 will be inserted in
     * @param char32 Codepoint to be inserted
     * @return new limit size
     * @exception IndexOutOfBoundsException Thrown if offset16 is invalid.
     * @stable ICU 2.1
     */
    public static int insert(char target[], int limit, int offset16, int char32) {
        String str = valueOf(char32);
        if (offset16 != limit && bounds(target, 0, limit, offset16) == TRAIL_SURROGATE_BOUNDARY) {
            offset16++;
        }
        int size = str.length();
        if (limit + size > target.length) {
            throw new ArrayIndexOutOfBoundsException(offset16 + size);
        }
        System.arraycopy(target, offset16, target, offset16 + size, limit - offset16);
        target[offset16] = str.charAt(0);
        if (size == 2) {
            target[offset16 + 1] = str.charAt(1);
        }
        return limit + size;
    }

    /**
     * Removes the codepoint at the specified position in this target (shortening target by 1
     * character if the codepoint is a non-supplementary, 2 otherwise).
     *
     * @param target String buffer to remove codepoint from
     * @param offset16 Offset which the codepoint will be removed
     * @return a reference to target
     * @exception IndexOutOfBoundsException Thrown if offset16 is invalid.
     * @stable ICU 2.1
     */
    public static StringBuffer delete(StringBuffer target, int offset16) {
        int count = 1;
        switch (bounds(target, offset16)) {
        case LEAD_SURROGATE_BOUNDARY:
            count++;
            break;
        case TRAIL_SURROGATE_BOUNDARY:
            count++;
            offset16--;
            break;
        }
        target.delete(offset16, offset16 + count);
        return target;
    }

    /**
     * Removes the codepoint at the specified position in this target (shortening target by 1
     * character if the codepoint is a non-supplementary, 2 otherwise).
     *
     * @param target String buffer to remove codepoint from
     * @param limit End index of the char array, limit &lt;= target.length
     * @param offset16 Offset which the codepoint will be removed
     * @return a new limit size
     * @exception IndexOutOfBoundsException Thrown if offset16 is invalid.
     * @stable ICU 2.1
     */
    public static int delete(char target[], int limit, int offset16) {
        int count = 1;
        switch (bounds(target, 0, limit, offset16)) {
        case LEAD_SURROGATE_BOUNDARY:
            count++;
            break;
        case TRAIL_SURROGATE_BOUNDARY:
            count++;
            offset16--;
            break;
        }
        System.arraycopy(target, offset16 + count, target, offset16, limit - (offset16 + count));
        target[limit - count] = 0;
        return limit - count;
    }

    /**
     * Returns the index within the argument UTF16 format Unicode string of the first occurrence of
     * the argument codepoint. I.e., the smallest index <code>i</code> such that
     * <code>UTF16.charAt(source, i) ==
     * char32</code> is true.
     * <p>
     * If no such character occurs in this string, then -1 is returned.
     * </p>
     * <p>
     * Examples:<br>
     * UTF16.indexOf("abc", 'a') returns 0<br>
     * UTF16.indexOf("abc\ud800\udc00", 0x10000) returns 3<br>
     * UTF16.indexOf("abc\ud800\udc00", 0xd800) returns -1<br>
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string that will be searched
     * @param char32 Codepoint to search for
     * @return the index of the first occurrence of the codepoint in the argument Unicode string, or
     *         -1 if the codepoint does not occur.
     * @stable ICU 2.6
     */
    public static int indexOf(String source, int char32) {
        if (char32 < CODEPOINT_MIN_VALUE || char32 > CODEPOINT_MAX_VALUE) {
            throw new IllegalArgumentException("Argument char32 is not a valid codepoint");
        }
        // non-surrogate bmp
        if (char32 < LEAD_SURROGATE_MIN_VALUE
                || (char32 > TRAIL_SURROGATE_MAX_VALUE && char32 < SUPPLEMENTARY_MIN_VALUE)) {
            return source.indexOf((char) char32);
        }
        // surrogate
        if (char32 < SUPPLEMENTARY_MIN_VALUE) {
            int result = source.indexOf((char) char32);
            if (result >= 0) {
                if (isLeadSurrogate(char32) && (result < source.length() - 1)
                        && isTrailSurrogate(source.charAt(result + 1))) {
                    return indexOf(source, char32, result + 1);
                }
                // trail surrogate
                if (result > 0 && isLeadSurrogate(source.charAt(result - 1))) {
                    return indexOf(source, char32, result + 1);
                }
            }
            return result;
        }
        // supplementary
        String char32str = toString(char32);
        return source.indexOf(char32str);
    }

    /**
     * Returns the index within the argument UTF16 format Unicode string of the first occurrence of
     * the argument string str. This method is implemented based on codepoints, hence a "lead
     * surrogate character + trail surrogate character" is treated as one entity.e Hence if the str
     * starts with trail surrogate character at index 0, a source with a leading a surrogate
     * character before str found at in source will not have a valid match. Vice versa for lead
     * surrogates that ends str. See example below.
     * <p>
     * If no such string str occurs in this source, then -1 is returned.
     * </p>
     * <p>
     * Examples:<br>
     * UTF16.indexOf("abc", "ab") returns 0<br>
     * UTF16.indexOf("abc\ud800\udc00", "\ud800\udc00") returns 3<br>
     * UTF16.indexOf("abc\ud800\udc00", "\ud800") returns -1<br>
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string that will be searched
     * @param str UTF16 format Unicode string to search for
     * @return the index of the first occurrence of the codepoint in the argument Unicode string, or
     *         -1 if the codepoint does not occur.
     * @stable ICU 2.6
     */
    public static int indexOf(String source, String str) {
        int strLength = str.length();
        // non-surrogate ends
        if (!isTrailSurrogate(str.charAt(0)) && !isLeadSurrogate(str.charAt(strLength - 1))) {
            return source.indexOf(str);
        }

        int result = source.indexOf(str);
        int resultEnd = result + strLength;
        if (result >= 0) {
            // check last character
            if (isLeadSurrogate(str.charAt(strLength - 1)) && (result < source.length() - 1)
                    && isTrailSurrogate(source.charAt(resultEnd + 1))) {
                return indexOf(source, str, resultEnd + 1);
            }
            // check first character which is a trail surrogate
            if (isTrailSurrogate(str.charAt(0)) && result > 0
                    && isLeadSurrogate(source.charAt(result - 1))) {
                return indexOf(source, str, resultEnd + 1);
            }
        }
        return result;
    }

    /**
     * Returns the index within the argument UTF16 format Unicode string of the first occurrence of
     * the argument codepoint. I.e., the smallest index i such that: <br>
     * (UTF16.charAt(source, i) == char32 &amp;&amp; i &gt;= fromIndex) is true.
     * <p>
     * If no such character occurs in this string, then -1 is returned.
     * </p>
     * <p>
     * Examples:<br>
     * UTF16.indexOf("abc", 'a', 1) returns -1<br>
     * UTF16.indexOf("abc\ud800\udc00", 0x10000, 1) returns 3<br>
     * UTF16.indexOf("abc\ud800\udc00", 0xd800, 1) returns -1<br>
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string that will be searched
     * @param char32 Codepoint to search for
     * @param fromIndex The index to start the search from.
     * @return the index of the first occurrence of the codepoint in the argument Unicode string at
     *         or after fromIndex, or -1 if the codepoint does not occur.
     * @stable ICU 2.6
     */
    public static int indexOf(String source, int char32, int fromIndex) {
        if (char32 < CODEPOINT_MIN_VALUE || char32 > CODEPOINT_MAX_VALUE) {
            throw new IllegalArgumentException("Argument char32 is not a valid codepoint");
        }
        // non-surrogate bmp
        if (char32 < LEAD_SURROGATE_MIN_VALUE
                || (char32 > TRAIL_SURROGATE_MAX_VALUE && char32 < SUPPLEMENTARY_MIN_VALUE)) {
            return source.indexOf((char) char32, fromIndex);
        }
        // surrogate
        if (char32 < SUPPLEMENTARY_MIN_VALUE) {
            int result = source.indexOf((char) char32, fromIndex);
            if (result >= 0) {
                if (isLeadSurrogate(char32) && (result < source.length() - 1)
                        && isTrailSurrogate(source.charAt(result + 1))) {
                    return indexOf(source, char32, result + 1);
                }
                // trail surrogate
                if (result > 0 && isLeadSurrogate(source.charAt(result - 1))) {
                    return indexOf(source, char32, result + 1);
                }
            }
            return result;
        }
        // supplementary
        String char32str = toString(char32);
        return source.indexOf(char32str, fromIndex);
    }

    /**
     * Returns the index within the argument UTF16 format Unicode string of the first occurrence of
     * the argument string str. This method is implemented based on codepoints, hence a "lead
     * surrogate character + trail surrogate character" is treated as one entity.e Hence if the str
     * starts with trail surrogate character at index 0, a source with a leading a surrogate
     * character before str found at in source will not have a valid match. Vice versa for lead
     * surrogates that ends str. See example below.
     * <p>
     * If no such string str occurs in this source, then -1 is returned.
     * </p>
     * <p>
     * Examples:<br>
     * UTF16.indexOf("abc", "ab", 0) returns 0<br>
     * UTF16.indexOf("abc\ud800\udc00", "\ud800\udc00", 0) returns 3<br>
     * UTF16.indexOf("abc\ud800\udc00", "\ud800\udc00", 2) returns 3<br>
     * UTF16.indexOf("abc\ud800\udc00", "\ud800", 0) returns -1<br>
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string that will be searched
     * @param str UTF16 format Unicode string to search for
     * @param fromIndex The index to start the search from.
     * @return the index of the first occurrence of the codepoint in the argument Unicode string, or
     *         -1 if the codepoint does not occur.
     * @stable ICU 2.6
     */
    public static int indexOf(String source, String str, int fromIndex) {
        int strLength = str.length();
        // non-surrogate ends
        if (!isTrailSurrogate(str.charAt(0)) && !isLeadSurrogate(str.charAt(strLength - 1))) {
            return source.indexOf(str, fromIndex);
        }

        int result = source.indexOf(str, fromIndex);
        int resultEnd = result + strLength;
        if (result >= 0) {
            // check last character
            if (isLeadSurrogate(str.charAt(strLength - 1)) && (result < source.length() - 1)
                    && isTrailSurrogate(source.charAt(resultEnd))) {
                return indexOf(source, str, resultEnd + 1);
            }
            // check first character which is a trail surrogate
            if (isTrailSurrogate(str.charAt(0)) && result > 0
                    && isLeadSurrogate(source.charAt(result - 1))) {
                return indexOf(source, str, resultEnd + 1);
            }
        }
        return result;
    }

    /**
     * Returns the index within the argument UTF16 format Unicode string of the last occurrence of
     * the argument codepoint. I.e., the index returned is the largest value i such that:
     * UTF16.charAt(source, i) == char32 is true.
     * <p>
     * Examples:<br>
     * UTF16.lastIndexOf("abc", 'a') returns 0<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", 0x10000) returns 3<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", 0xd800) returns -1<br>
     * </p>
     * <p>
     * source is searched backwards starting at the last character.
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string that will be searched
     * @param char32 Codepoint to search for
     * @return the index of the last occurrence of the codepoint in source, or -1 if the codepoint
     *         does not occur.
     * @stable ICU 2.6
     */
    public static int lastIndexOf(String source, int char32) {
        if (char32 < CODEPOINT_MIN_VALUE || char32 > CODEPOINT_MAX_VALUE) {
            throw new IllegalArgumentException("Argument char32 is not a valid codepoint");
        }
        // non-surrogate bmp
        if (char32 < LEAD_SURROGATE_MIN_VALUE
                || (char32 > TRAIL_SURROGATE_MAX_VALUE && char32 < SUPPLEMENTARY_MIN_VALUE)) {
            return source.lastIndexOf((char) char32);
        }
        // surrogate
        if (char32 < SUPPLEMENTARY_MIN_VALUE) {
            int result = source.lastIndexOf((char) char32);
            if (result >= 0) {
                if (isLeadSurrogate(char32) && (result < source.length() - 1)
                        && isTrailSurrogate(source.charAt(result + 1))) {
                    return lastIndexOf(source, char32, result - 1);
                }
                // trail surrogate
                if (result > 0 && isLeadSurrogate(source.charAt(result - 1))) {
                    return lastIndexOf(source, char32, result - 1);
                }
            }
            return result;
        }
        // supplementary
        String char32str = toString(char32);
        return source.lastIndexOf(char32str);
    }

    /**
     * Returns the index within the argument UTF16 format Unicode string of the last occurrence of
     * the argument string str. This method is implemented based on codepoints, hence a "lead
     * surrogate character + trail surrogate character" is treated as one entity.e Hence if the str
     * starts with trail surrogate character at index 0, a source with a leading a surrogate
     * character before str found at in source will not have a valid match. Vice versa for lead
     * surrogates that ends str. See example below.
     * <p>
     * Examples:<br>
     * UTF16.lastIndexOf("abc", "a") returns 0<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", "\ud800\udc00") returns 3<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", "\ud800") returns -1<br>
     * </p>
     * <p>
     * source is searched backwards starting at the last character.
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string that will be searched
     * @param str UTF16 format Unicode string to search for
     * @return the index of the last occurrence of the codepoint in source, or -1 if the codepoint
     *         does not occur.
     * @stable ICU 2.6
     */
    public static int lastIndexOf(String source, String str) {
        int strLength = str.length();
        // non-surrogate ends
        if (!isTrailSurrogate(str.charAt(0)) && !isLeadSurrogate(str.charAt(strLength - 1))) {
            return source.lastIndexOf(str);
        }

        int result = source.lastIndexOf(str);
        if (result >= 0) {
            // check last character
            if (isLeadSurrogate(str.charAt(strLength - 1)) && (result < source.length() - 1)
                    && isTrailSurrogate(source.charAt(result + strLength + 1))) {
                return lastIndexOf(source, str, result - 1);
            }
            // check first character which is a trail surrogate
            if (isTrailSurrogate(str.charAt(0)) && result > 0
                    && isLeadSurrogate(source.charAt(result - 1))) {
                return lastIndexOf(source, str, result - 1);
            }
        }
        return result;
    }

    /**
     * <p>
     * Returns the index within the argument UTF16 format Unicode string of the last occurrence of
     * the argument codepoint, where the result is less than or equals to fromIndex.
     * </p>
     * <p>
     * This method is implemented based on codepoints, hence a single surrogate character will not
     * match a supplementary character.
     * </p>
     * <p>
     * source is searched backwards starting at the last character starting at the specified index.
     * </p>
     * <p>
     * Examples:<br>
     * UTF16.lastIndexOf("abc", 'c', 2) returns 2<br>
     * UTF16.lastIndexOf("abc", 'c', 1) returns -1<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", 0x10000, 5) returns 3<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", 0x10000, 3) returns 3<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", 0xd800) returns -1<br>
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string that will be searched
     * @param char32 Codepoint to search for
     * @param fromIndex the index to start the search from. There is no restriction on the value of
     *            fromIndex. If it is greater than or equal to the length of this string, it has the
     *            same effect as if it were equal to one less than the length of this string: this
     *            entire string may be searched. If it is negative, it has the same effect as if it
     *            were -1: -1 is returned.
     * @return the index of the last occurrence of the codepoint in source, or -1 if the codepoint
     *         does not occur.
     * @stable ICU 2.6
     */
    public static int lastIndexOf(String source, int char32, int fromIndex) {
        if (char32 < CODEPOINT_MIN_VALUE || char32 > CODEPOINT_MAX_VALUE) {
            throw new IllegalArgumentException("Argument char32 is not a valid codepoint");
        }
        // non-surrogate bmp
        if (char32 < LEAD_SURROGATE_MIN_VALUE
                || (char32 > TRAIL_SURROGATE_MAX_VALUE && char32 < SUPPLEMENTARY_MIN_VALUE)) {
            return source.lastIndexOf((char) char32, fromIndex);
        }
        // surrogate
        if (char32 < SUPPLEMENTARY_MIN_VALUE) {
            int result = source.lastIndexOf((char) char32, fromIndex);
            if (result >= 0) {
                if (isLeadSurrogate(char32) && (result < source.length() - 1)
                        && isTrailSurrogate(source.charAt(result + 1))) {
                    return lastIndexOf(source, char32, result - 1);
                }
                // trail surrogate
                if (result > 0 && isLeadSurrogate(source.charAt(result - 1))) {
                    return lastIndexOf(source, char32, result - 1);
                }
            }
            return result;
        }
        // supplementary
        String char32str = toString(char32);
        return source.lastIndexOf(char32str, fromIndex);
    }

    /**
     * <p>
     * Returns the index within the argument UTF16 format Unicode string of the last occurrence of
     * the argument string str, where the result is less than or equals to fromIndex.
     * </p>
     * <p>
     * This method is implemented based on codepoints, hence a "lead surrogate character + trail
     * surrogate character" is treated as one entity. Hence if the str starts with trail surrogate
     * character at index 0, a source with a leading a surrogate character before str found at in
     * source will not have a valid match. Vice versa for lead surrogates that ends str.
     * </p>
     * See example below.
     * <p>
     * Examples:<br>
     * UTF16.lastIndexOf("abc", "c", 2) returns 2<br>
     * UTF16.lastIndexOf("abc", "c", 1) returns -1<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", "\ud800\udc00", 5) returns 3<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", "\ud800\udc00", 3) returns 3<br>
     * UTF16.lastIndexOf("abc\ud800\udc00", "\ud800", 4) returns -1<br>
     * </p>
     * <p>
     * source is searched backwards starting at the last character.
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string that will be searched
     * @param str UTF16 format Unicode string to search for
     * @param fromIndex the index to start the search from. There is no restriction on the value of
     *            fromIndex. If it is greater than or equal to the length of this string, it has the
     *            same effect as if it were equal to one less than the length of this string: this
     *            entire string may be searched. If it is negative, it has the same effect as if it
     *            were -1: -1 is returned.
     * @return the index of the last occurrence of the codepoint in source, or -1 if the codepoint
     *         does not occur.
     * @stable ICU 2.6
     */
    public static int lastIndexOf(String source, String str, int fromIndex) {
        int strLength = str.length();
        // non-surrogate ends
        if (!isTrailSurrogate(str.charAt(0)) && !isLeadSurrogate(str.charAt(strLength - 1))) {
            return source.lastIndexOf(str, fromIndex);
        }

        int result = source.lastIndexOf(str, fromIndex);
        if (result >= 0) {
            // check last character
            if (isLeadSurrogate(str.charAt(strLength - 1)) && (result < source.length() - 1)
                    && isTrailSurrogate(source.charAt(result + strLength))) {
                return lastIndexOf(source, str, result - 1);
            }
            // check first character which is a trail surrogate
            if (isTrailSurrogate(str.charAt(0)) && result > 0
                    && isLeadSurrogate(source.charAt(result - 1))) {
                return lastIndexOf(source, str, result - 1);
            }
        }
        return result;
    }

    /**
     * Returns a new UTF16 format Unicode string resulting from replacing all occurrences of
     * oldChar32 in source with newChar32. If the character oldChar32 does not occur in the UTF16
     * format Unicode string source, then source will be returned. Otherwise, a new String object is
     * created that represents a codepoint sequence identical to the codepoint sequence represented
     * by source, except that every occurrence of oldChar32 is replaced by an occurrence of
     * newChar32.
     * <p>
     * Examples: <br>
     * UTF16.replace("mesquite in your cellar", 'e', 'o');<br>
     * returns "mosquito in your collar"<br>
     * UTF16.replace("JonL", 'q', 'x');<br>
     * returns "JonL" (no change)<br>
     * UTF16.replace("Supplementary character \ud800\udc00", 0x10000, '!'); <br>
     * returns "Supplementary character !"<br>
     * UTF16.replace("Supplementary character \ud800\udc00", 0xd800, '!'); <br>
     * returns "Supplementary character \ud800\udc00"<br>
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string which the codepoint replacements will be based on.
     * @param oldChar32 Non-zero old codepoint to be replaced.
     * @param newChar32 The new codepoint to replace oldChar32
     * @return new String derived from source by replacing every occurrence of oldChar32 with
     *         newChar32, unless when no oldChar32 is found in source then source will be returned.
     * @stable ICU 2.6
     */
    public static String replace(String source, int oldChar32, int newChar32) {
        if (oldChar32 <= 0 || oldChar32 > CODEPOINT_MAX_VALUE) {
            throw new IllegalArgumentException("Argument oldChar32 is not a valid codepoint");
        }
        if (newChar32 <= 0 || newChar32 > CODEPOINT_MAX_VALUE) {
            throw new IllegalArgumentException("Argument newChar32 is not a valid codepoint");
        }

        int index = indexOf(source, oldChar32);
        if (index == -1) {
            return source;
        }
        String newChar32Str = toString(newChar32);
        int oldChar32Size = 1;
        int newChar32Size = newChar32Str.length();
        StringBuffer result = new StringBuffer(source);
        int resultIndex = index;

        if (oldChar32 >= SUPPLEMENTARY_MIN_VALUE) {
            oldChar32Size = 2;
        }

        while (index != -1) {
            int endResultIndex = resultIndex + oldChar32Size;
            result.replace(resultIndex, endResultIndex, newChar32Str);
            int lastEndIndex = index + oldChar32Size;
            index = indexOf(source, oldChar32, lastEndIndex);
            resultIndex += newChar32Size + index - lastEndIndex;
        }
        return result.toString();
    }

    /**
     * Returns a new UTF16 format Unicode string resulting from replacing all occurrences of oldStr
     * in source with newStr. If the string oldStr does not occur in the UTF16 format Unicode string
     * source, then source will be returned. Otherwise, a new String object is created that
     * represents a codepoint sequence identical to the codepoint sequence represented by source,
     * except that every occurrence of oldStr is replaced by an occurrence of newStr.
     * <p>
     * Examples: <br>
     * UTF16.replace("mesquite in your cellar", "e", "o");<br>
     * returns "mosquito in your collar"<br>
     * UTF16.replace("mesquite in your cellar", "mesquite", "cat");<br>
     * returns "cat in your cellar"<br>
     * UTF16.replace("JonL", "q", "x");<br>
     * returns "JonL" (no change)<br>
     * UTF16.replace("Supplementary character \ud800\udc00", "\ud800\udc00", '!'); <br>
     * returns "Supplementary character !"<br>
     * UTF16.replace("Supplementary character \ud800\udc00", "\ud800", '!'); <br>
     * returns "Supplementary character \ud800\udc00"<br>
     * </p>
     * Note this method is provided as support to jdk 1.3, which does not support supplementary
     * characters to its fullest.
     *
     * @param source UTF16 format Unicode string which the replacements will be based on.
     * @param oldStr Non-zero-length string to be replaced.
     * @param newStr The new string to replace oldStr
     * @return new String derived from source by replacing every occurrence of oldStr with newStr.
     *         When no oldStr is found in source, then source will be returned.
     * @stable ICU 2.6
     */
    public static String replace(String source, String oldStr, String newStr) {
        int index = indexOf(source, oldStr);
        if (index == -1) {
            return source;
        }
        int oldStrSize = oldStr.length();
        int newStrSize = newStr.length();
        StringBuffer result = new StringBuffer(source);
        int resultIndex = index;

        while (index != -1) {
            int endResultIndex = resultIndex + oldStrSize;
            result.replace(resultIndex, endResultIndex, newStr);
            int lastEndIndex = index + oldStrSize;
            index = indexOf(source, oldStr, lastEndIndex);
            resultIndex += newStrSize + index - lastEndIndex;
        }
        return result.toString();
    }

    /**
     * Reverses a UTF16 format Unicode string and replaces source's content with it. This method
     * will reverse surrogate characters correctly, instead of blindly reversing every character.
     * <p>
     * Examples:<br>
     * UTF16.reverse(new StringBuffer( "Supplementary characters \ud800\udc00\ud801\udc01"))<br>
     * returns "\ud801\udc01\ud800\udc00 sretcarahc yratnemelppuS".
     *
     * @param source The source StringBuffer that contains UTF16 format Unicode string to be reversed
     * @return a modified source with reversed UTF16 format Unicode string.
     * @stable ICU 2.6
     */
    public static StringBuffer reverse(StringBuffer source) {
        int length = source.length();
        StringBuffer result = new StringBuffer(length);
        for (int i = length; i-- > 0;) {
            char ch = source.charAt(i);
            if (isTrailSurrogate(ch) && i > 0) {
                char ch2 = source.charAt(i - 1);
                if (isLeadSurrogate(ch2)) {
                    result.append(ch2);
                    result.append(ch);
                    --i;
                    continue;
                }
            }
            result.append(ch);
        }
        return result;
    }

    /**
     * Check if the string contains more Unicode code points than a certain number. This is more
     * efficient than counting all code points in the entire string and comparing that number with a
     * threshold. This function may not need to scan the string at all if the length is within a
     * certain range, and never needs to count more than 'number + 1' code points. Logically
     * equivalent to (countCodePoint(s) &gt; number). A Unicode code point may occupy either one or two
     * code units.
     *
     * @param source The input string.
     * @param number The number of code points in the string is compared against the 'number'
     *            parameter.
     * @return boolean value for whether the string contains more Unicode code points than 'number'.
     * @stable ICU 2.4
     */
    public static boolean hasMoreCodePointsThan(String source, int number) {
        if (number < 0) {
            return true;
        }
        if (source == null) {
            return false;
        }
        int length = source.length();

        // length >= 0 known
        // source contains at least (length + 1) / 2 code points: <= 2
        // chars per cp
        if (((length + 1) >> 1) > number) {
            return true;
        }

        // check if source does not even contain enough chars
        int maxsupplementary = length - number;
        if (maxsupplementary <= 0) {
            return false;
        }

        // there are maxsupplementary = length - number more chars than
        // asked-for code points

        // count code points until they exceed and also check that there are
        // no more than maxsupplementary supplementary code points (char pairs)
        int start = 0;
        while (true) {
            if (length == 0) {
                return false;
            }
            if (number == 0) {
                return true;
            }
            if (isLeadSurrogate(source.charAt(start++)) && start != length
                    && isTrailSurrogate(source.charAt(start))) {
                start++;
                if (--maxsupplementary <= 0) {
                    // too many pairs - too few code points
                    return false;
                }
            }
            --number;
        }
    }

    /**
     * Check if the sub-range of char array, from argument start to limit, contains more Unicode
     * code points than a certain number. This is more efficient than counting all code points in
     * the entire char array range and comparing that number with a threshold. This function may not
     * need to scan the char array at all if start and limit is within a certain range, and never
     * needs to count more than 'number + 1' code points. Logically equivalent to
     * (countCodePoint(source, start, limit) &gt; number). A Unicode code point may occupy either one
     * or two code units.
     *
     * @param source Array of UTF-16 chars
     * @param start Offset to substring in the source array for analyzing
     * @param limit Offset to substring in the source array for analyzing
     * @param number The number of code points in the string is compared against the 'number'
     *            parameter.
     * @return boolean value for whether the string contains more Unicode code points than 'number'.
     * @exception IndexOutOfBoundsException Thrown when limit &lt; start
     * @stable ICU 2.4
     */
    public static boolean hasMoreCodePointsThan(char source[], int start, int limit, int number) {
        int length = limit - start;
        if (length < 0 || start < 0 || limit < 0) {
            throw new IndexOutOfBoundsException(
                    "Start and limit indexes should be non-negative and start <= limit");
        }
        if (number < 0) {
            return true;
        }
        if (source == null) {
            return false;
        }

        // length >= 0 known
        // source contains at least (length + 1) / 2 code points: <= 2
        // chars per cp
        if (((length + 1) >> 1) > number) {
            return true;
        }

        // check if source does not even contain enough chars
        int maxsupplementary = length - number;
        if (maxsupplementary <= 0) {
            return false;
        }

        // there are maxsupplementary = length - number more chars than
        // asked-for code points

        // count code points until they exceed and also check that there are
        // no more than maxsupplementary supplementary code points (char pairs)
        while (true) {
            if (length == 0) {
                return false;
            }
            if (number == 0) {
                return true;
            }
            if (isLeadSurrogate(source[start++]) && start != limit
                    && isTrailSurrogate(source[start])) {
                start++;
                if (--maxsupplementary <= 0) {
                    // too many pairs - too few code points
                    return false;
                }
            }
            --number;
        }
    }

    /**
     * Check if the string buffer contains more Unicode code points than a certain number. This is
     * more efficient than counting all code points in the entire string buffer and comparing that
     * number with a threshold. This function may not need to scan the string buffer at all if the
     * length is within a certain range, and never needs to count more than 'number + 1' code
     * points. Logically equivalent to (countCodePoint(s) &gt; number). A Unicode code point may
     * occupy either one or two code units.
     *
     * @param source The input string buffer.
     * @param number The number of code points in the string buffer is compared against the 'number'
     *            parameter.
     * @return boolean value for whether the string buffer contains more Unicode code points than
     *         'number'.
     * @stable ICU 2.4
     */
    public static boolean hasMoreCodePointsThan(StringBuffer source, int number) {
        if (number < 0) {
            return true;
        }
        if (source == null) {
            return false;
        }
        int length = source.length();

        // length >= 0 known
        // source contains at least (length + 1) / 2 code points: <= 2
        // chars per cp
        if (((length + 1) >> 1) > number) {
            return true;
        }

        // check if source does not even contain enough chars
        int maxsupplementary = length - number;
        if (maxsupplementary <= 0) {
            return false;
        }

        // there are maxsupplementary = length - number more chars than
        // asked-for code points

        // count code points until they exceed and also check that there are
        // no more than maxsupplementary supplementary code points (char pairs)
        int start = 0;
        while (true) {
            if (length == 0) {
                return false;
            }
            if (number == 0) {
                return true;
            }
            if (isLeadSurrogate(source.charAt(start++)) && start != length
                    && isTrailSurrogate(source.charAt(start))) {
                start++;
                if (--maxsupplementary <= 0) {
                    // too many pairs - too few code points
                    return false;
                }
            }
            --number;
        }
    }

    /**
     * Cover JDK 1.5 API. Create a String from an array of codePoints.
     *
     * @param codePoints The code array
     * @param offset The start of the text in the code point array
     * @param count The number of code points
     * @return a String representing the code points between offset and count
     * @throws IllegalArgumentException If an invalid code point is encountered
     * @throws IndexOutOfBoundsException If the offset or count are out of bounds.
     * @stable ICU 3.0
     */
    public static String newString(int[] codePoints, int offset, int count) {
        if (count < 0) {
            throw new IllegalArgumentException();
        }
        char[] chars = new char[count];
        int w = 0;
        for (int r = offset, e = offset + count; r < e; ++r) {
            int cp = codePoints[r];
            if (cp < 0 || cp > 0x10ffff) {
                throw new IllegalArgumentException();
            }
            while (true) {
                try {
                    if (cp < 0x010000) {
                        chars[w] = (char) cp;
                        w++;
                    } else {
                        chars[w] = (char) (LEAD_SURROGATE_OFFSET_ + (cp >> LEAD_SURROGATE_SHIFT_));
                        chars[w + 1] = (char) (TRAIL_SURROGATE_MIN_VALUE + (cp & TRAIL_SURROGATE_MASK_));
                        w += 2;
                    }
                    break;
                } catch (IndexOutOfBoundsException ex) {
                    int newlen = (int) (Math.ceil((double) codePoints.length * (w + 2)
                            / (r - offset + 1)));
                    char[] temp = new char[newlen];
                    System.arraycopy(chars, 0, temp, 0, w);
                    chars = temp;
                }
            }
        }
        return new String(chars, 0, w);
    }

    /**
     * <p>
     * UTF16 string comparator class. Allows UTF16 string comparison to be done with the various
     * modes
     * </p>
     * <ul>
     * <li> Code point comparison or code unit comparison
     * <li> Case sensitive comparison, case insensitive comparison or case insensitive comparison
     * with special handling for character 'i'.
     * </ul>
     * <p>
     * The code unit or code point comparison differ only when comparing supplementary code points
     * (&#92;u10000..&#92;u10ffff) to BMP code points near the end of the BMP (i.e.,
     * &#92;ue000..&#92;uffff). In code unit comparison, high BMP code points sort after
     * supplementary code points because they are stored as pairs of surrogates which are at
     * &#92;ud800..&#92;udfff.
     * </p>
     *
     * @see #FOLD_CASE_DEFAULT
     * @see #FOLD_CASE_EXCLUDE_SPECIAL_I
     * @stable ICU 2.1
     */
    public static final class StringComparator implements java.util.Comparator<String> {
        // public constructor ------------------------------------------------

        /**
         * Default constructor that does code unit comparison and case sensitive comparison.
         *
         * @stable ICU 2.1
         */
        public StringComparator() {
            this(false, false, FOLD_CASE_DEFAULT);
        }

        /**
         * Constructor that does comparison based on the argument options.
         *
         * @param codepointcompare Flag to indicate true for code point comparison or false for code unit
         *            comparison.
         * @param ignorecase False for case sensitive comparison, true for case-insensitive comparison
         * @param foldcaseoption FOLD_CASE_DEFAULT or FOLD_CASE_EXCLUDE_SPECIAL_I. This option is used only
         *            when ignorecase is set to true. If ignorecase is false, this option is
         *            ignored.
         * @see #FOLD_CASE_DEFAULT
         * @see #FOLD_CASE_EXCLUDE_SPECIAL_I
         * @throws IllegalArgumentException If foldcaseoption is out of range
         * @stable ICU 2.4
         */
        public StringComparator(boolean codepointcompare, boolean ignorecase, int foldcaseoption) {
            setCodePointCompare(codepointcompare);
            m_ignoreCase_ = ignorecase;
            if (foldcaseoption < FOLD_CASE_DEFAULT || foldcaseoption > FOLD_CASE_EXCLUDE_SPECIAL_I) {
                throw new IllegalArgumentException("Invalid fold case option");
            }
            m_foldCase_ = foldcaseoption;
        }

        // public data member ------------------------------------------------

        /**
         * Option value for case folding comparison:
         *
         * <p>Comparison is case insensitive, strings are folded using default mappings defined in
         * Unicode data file CaseFolding.txt, before comparison.
         *
         * @stable ICU 2.4
         */
        public static final int FOLD_CASE_DEFAULT = 0;

        /**
         * Option value for case folding:
         * Use the modified set of mappings provided in CaseFolding.txt to handle dotted I
         * and dotless i appropriately for Turkic languages (tr, az).
         *
         * <p>Comparison is case insensitive, strings are folded using modified mappings defined in
         * Unicode data file CaseFolding.txt, before comparison.
         *
         * @stable ICU 2.4
         * @see jdk.internal.icu.lang.UCharacter#FOLD_CASE_EXCLUDE_SPECIAL_I
         */
        public static final int FOLD_CASE_EXCLUDE_SPECIAL_I = 1;

        // public methods ----------------------------------------------------

        // public setters ----------------------------------------------------

        /**
         * Sets the comparison mode to code point compare if flag is true. Otherwise comparison mode
         * is set to code unit compare
         *
         * @param flag True for code point compare, false for code unit compare
         * @stable ICU 2.4
         */
        public void setCodePointCompare(boolean flag) {
            if (flag) {
                m_codePointCompare_ = Normalizer.COMPARE_CODE_POINT_ORDER;
            } else {
                m_codePointCompare_ = 0;
            }
        }

        /**
         * Sets the Comparator to case-insensitive comparison mode if argument is true, otherwise
         * case sensitive comparison mode if set to false.
         *
         * @param ignorecase True for case-insensitive comparison, false for case sensitive comparison
         * @param foldcaseoption FOLD_CASE_DEFAULT or FOLD_CASE_EXCLUDE_SPECIAL_I. This option is used only
         *            when ignorecase is set to true. If ignorecase is false, this option is
         *            ignored.
         * @see #FOLD_CASE_DEFAULT
         * @see #FOLD_CASE_EXCLUDE_SPECIAL_I
         * @stable ICU 2.4
         */
        public void setIgnoreCase(boolean ignorecase, int foldcaseoption) {
            m_ignoreCase_ = ignorecase;
            if (foldcaseoption < FOLD_CASE_DEFAULT || foldcaseoption > FOLD_CASE_EXCLUDE_SPECIAL_I) {
                throw new IllegalArgumentException("Invalid fold case option");
            }
            m_foldCase_ = foldcaseoption;
        }

        // public getters ----------------------------------------------------

        /**
         * Checks if the comparison mode is code point compare.
         *
         * @return true for code point compare, false for code unit compare
         * @stable ICU 2.4
         */
        public boolean getCodePointCompare() {
            return m_codePointCompare_ == Normalizer.COMPARE_CODE_POINT_ORDER;
        }

        /**
         * Checks if Comparator is in the case insensitive mode.
         *
         * @return true if Comparator performs case insensitive comparison, false otherwise
         * @stable ICU 2.4
         */
        public boolean getIgnoreCase() {
            return m_ignoreCase_;
        }

        /**
         * Gets the fold case options set in Comparator to be used with case insensitive comparison.
         *
         * @return either FOLD_CASE_DEFAULT or FOLD_CASE_EXCLUDE_SPECIAL_I
         * @see #FOLD_CASE_DEFAULT
         * @see #FOLD_CASE_EXCLUDE_SPECIAL_I
         * @stable ICU 2.4
         */
        public int getIgnoreCaseOption() {
            return m_foldCase_;
        }

        // public other methods ----------------------------------------------

        /**
         * Compare two strings depending on the options selected during construction.
         *
         * @param a first source string.
         * @param b second source string.
         * @return 0 returned if a == b. If a &lt; b, a negative value is returned. Otherwise if a &gt; b,
         *         a positive value is returned.
         * @exception ClassCastException thrown when either a or b is not a String object
         * @stable ICU 4.4
         */
        @Override
        public int compare(String a, String b) {
            if (Utility.sameObjects(a, b)) {
                return 0;
            }
            if (a == null) {
                return -1;
            }
            if (b == null) {
                return 1;
            }

            if (m_ignoreCase_) {
                return compareCaseInsensitive(a, b);
            }
            return compareCaseSensitive(a, b);
        }

        // private data member ----------------------------------------------

        /**
         * Code unit comparison flag. True if code unit comparison is required. False if code point
         * comparison is required.
         */
        private int m_codePointCompare_;

        /**
         * Fold case comparison option.
         */
        private int m_foldCase_;

        /**
         * Flag indicator if ignore case is to be used during comparison
         */
        private boolean m_ignoreCase_;

        /**
         * Code point order offset for surrogate characters
         */
        private static final int CODE_POINT_COMPARE_SURROGATE_OFFSET_ = 0x2800;

        // private method ---------------------------------------------------

        /**
         * Compares case insensitive. This is a direct port of ICU4C, to make maintenance life
         * easier.
         *
         * @param s1
         *            first string to compare
         * @param s2
         *            second string to compare
         * @return -1 is s1 &lt; s2, 0 if equals,
         */
        private int compareCaseInsensitive(String s1, String s2) {
            return Normalizer.cmpEquivFold(s1, s2, m_foldCase_ | m_codePointCompare_
                    | Normalizer.COMPARE_IGNORE_CASE);
        }

        /**
         * Compares case sensitive. This is a direct port of ICU4C, to make maintenance life
         * easier.
         *
         * @param s1
         *            first string to compare
         * @param s2
         *            second string to compare
         * @return -1 is s1 &lt; s2, 0 if equals,
         */
        private int compareCaseSensitive(String s1, String s2) {
            // compare identical prefixes - they do not need to be fixed up
            // limit1 = start1 + min(length1, length2)
            int length1 = s1.length();
            int length2 = s2.length();
            int minlength = length1;
            int result = 0;
            if (length1 < length2) {
                result = -1;
            } else if (length1 > length2) {
                result = 1;
                minlength = length2;
            }

            char c1 = 0;
            char c2 = 0;
            int index = 0;
            for (; index < minlength; index++) {
                c1 = s1.charAt(index);
                c2 = s2.charAt(index);
                // check pseudo-limit
                if (c1 != c2) {
                    break;
                }
            }

            if (index == minlength) {
                return result;
            }

            boolean codepointcompare = m_codePointCompare_ == Normalizer.COMPARE_CODE_POINT_ORDER;
            // if both values are in or above the surrogate range, fix them up
            if (c1 >= LEAD_SURROGATE_MIN_VALUE && c2 >= LEAD_SURROGATE_MIN_VALUE
                    && codepointcompare) {
                // subtract 0x2800 from BMP code points to make them smaller
                // than supplementary ones
                if ((c1 <= LEAD_SURROGATE_MAX_VALUE && (index + 1) != length1 && isTrailSurrogate(s1.charAt(index + 1)))
                        || (isTrailSurrogate(c1) && index != 0 && isLeadSurrogate(s1.charAt(index - 1)))) {
                    // part of a surrogate pair, leave >=d800
                } else {
                    // BMP code point - may be surrogate code point - make
                    // < d800
                    c1 -= CODE_POINT_COMPARE_SURROGATE_OFFSET_;
                }

                if ((c2 <= LEAD_SURROGATE_MAX_VALUE && (index + 1) != length2 && isTrailSurrogate(s2.charAt(index + 1)))
                        || (isTrailSurrogate(c2) && index != 0 && isLeadSurrogate(s2.charAt(index - 1)))) {
                    // part of a surrogate pair, leave >=d800
                } else {
                    // BMP code point - may be surrogate code point - make <d800
                    c2 -= CODE_POINT_COMPARE_SURROGATE_OFFSET_;
                }
            }

            // now c1 and c2 are in UTF-32-compatible order
            return c1 - c2;
        }
    }

    /**
     * Utility for getting a code point from a CharSequence that contains exactly one code point.
     * @return the code point IF the string is non-null and consists of a single code point.
     * otherwise returns -1.
     * @param s to test
     * @stable ICU 54
     */
    public static int getSingleCodePoint(CharSequence s) {
        if (s == null || s.length() == 0) {
            return -1;
        } else if (s.length() == 1) {
            return s.charAt(0);
        } else if (s.length() > 2) {
            return -1;
        }

        // at this point, len = 2
        int cp = Character.codePointAt(s, 0);
        if (cp > 0xFFFF) { // is surrogate pair
            return cp;
        }
        return -1;
    }

    /**
     * Utility for comparing a code point to a string without having to create a new string. Returns the same results
     * as a code point comparison of UTF16.valueOf(codePoint) and s.toString(). More specifically, if
     * <pre>
     * sc = new StringComparator(true,false,0);
     * fast = UTF16.compareCodePoint(codePoint, charSequence)
     * slower = sc.compare(UTF16.valueOf(codePoint), charSequence == null ? "" : charSequence.toString())
     * </pre>
     * then
     * <pre>
     * Integer.signum(fast) == Integer.signum(slower)
     * </pre>
     * @param codePoint to test
     * @param s to test
     * @return equivalent of code point comparator comparing two strings.
     * @stable ICU 54
     */
    public static int compareCodePoint(int codePoint, CharSequence s) {
        if (s == null) {
            return 1;
        }
        final int strLen = s.length();
        if (strLen == 0) {
            return 1;
        }
        int second = Character.codePointAt(s, 0);
        int diff = codePoint - second;
        if (diff != 0) {
            return diff;
        }
        return strLen == Character.charCount(codePoint) ? 0 : -1;
    }

    // private data members -------------------------------------------------

    /**
     * Shift value for lead surrogate to form a supplementary character.
     */
    private static final int LEAD_SURROGATE_SHIFT_ = 10;

    /**
     * Mask to retrieve the significant value from a trail surrogate.
     */
    private static final int TRAIL_SURROGATE_MASK_ = 0x3FF;

    /**
     * Value that all lead surrogate starts with
     */
    private static final int LEAD_SURROGATE_OFFSET_ = LEAD_SURROGATE_MIN_VALUE
            - (SUPPLEMENTARY_MIN_VALUE >> LEAD_SURROGATE_SHIFT_);

    // private methods ------------------------------------------------------

    /**
     * <p>
     * Converts argument code point and returns a String object representing the code point's value
     * in UTF16 format.
     * </p>
     * <p>
     * This method does not check for the validity of the codepoint, the results are not guaranteed
     * if a invalid codepoint is passed as argument.
     * </p>
     * <p>
     * The result is a string whose length is 1 for non-supplementary code points, 2 otherwise.
     * </p>
     *
     * @param ch
     *            code point
     * @return string representation of the code point
     */
    private static String toString(int ch) {
        if (ch < SUPPLEMENTARY_MIN_VALUE) {
            return String.valueOf((char) ch);
        }

        StringBuilder result = new StringBuilder();
        result.append(getLeadSurrogate(ch));
        result.append(getTrailSurrogate(ch));
        return result.toString();
    }
}
// eof
