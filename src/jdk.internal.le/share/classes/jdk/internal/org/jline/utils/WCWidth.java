/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.text.BreakIterator;

import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.impl.AbstractTerminal;

/**
 * Utility class for determining the display width of Unicode characters.
 *
 * <p>
 * The WCWidth class provides methods for calculating the display width of Unicode
 * characters in terminal environments. This is important for proper text alignment
 * and cursor positioning, especially when dealing with wide characters (such as
 * East Asian characters) and zero-width characters (such as combining marks).
 * </p>
 *
 * <p>
 * This implementation is based on Markus Kuhn's wcwidth implementation, which follows
 * the Unicode Standard guidelines for character width. It categorizes characters as:
 * </p>
 * <ul>
 *   <li>Zero width (0) - Control characters, combining marks, format characters</li>
 *   <li>Single width (1) - Most Latin, Greek, Cyrillic, and other scripts</li>
 *   <li>Double width (2) - East Asian scripts (Chinese, Japanese, Korean)</li>
 *   <li>Ambiguous width (-1) - Characters with context-dependent width</li>
 * </ul>
 *
 * <p>
 * Tables are generated from Unicode 16.0 data files:
 * </p>
 * <ul>
 *   <li>{@code combining} table from
 *       <a href="https://unicode.org/Public/16.0.0/ucd/UnicodeData.txt">UnicodeData.txt</a>
 *       (categories Mn, Me, Cf, plus Hangul Jamo U+1160-11FF and U+200B)</li>
 *   <li>East Asian Width from
 *       <a href="https://unicode.org/Public/16.0.0/ucd/EastAsianWidth.txt">EastAsianWidth.txt</a></li>
 *   <li>Emoji presentation from
 *       <a href="https://unicode.org/Public/16.0.0/ucd/emoji/emoji-data.txt">emoji-data.txt</a></li>
 * </ul>
 *
 * <p>
 * This class is used throughout JLine for calculating string display widths,
 * which is essential for proper terminal display formatting, cursor positioning,
 * and text alignment.
 * </p>
 */
public final class WCWidth {

    /**
     * Whether the JDK runtime supports grapheme cluster segmentation.
     * JDK 21+ provides improved {@code BreakIterator} support (full UAX #29)
     * and {@code Character.isEmoji()} for reliable emoji detection.
     * When {@code true} and no terminal is provided, grapheme cluster-aware
     * width calculation is used as a better default for application-level
     * width queries.
     */
    static final boolean HAS_JDK_GRAPHEME_SUPPORT = Runtime.version().feature() >= 21;

    /**
     * Prevents instantiation of this utility class.
     */
    private WCWidth() {}

    /* The following two functions define the column width of an ISO 10646
     * character as follows:
     *
     *    - The null character (U+0000) has a column width of 0.
     *
     *    - Other C0/C1 control characters and DEL will lead to a return
     *      value of -1.
     *
     *    - Non-spacing and enclosing combining characters (general
     *      category code Mn or Me in the Unicode database) have a
     *      column width of 0.
     *
     *    - SOFT HYPHEN (U+00AD) has a column width of 1.
     *
     *    - Other format characters (general category code Cf in the Unicode
     *      database) and ZERO WIDTH SPACE (U+200B) have a column width of 0.
     *
     *    - Hangul Jamo medial vowels and final consonants (U+1160-U+11FF)
     *      have a column width of 0.
     *
     *    - Spacing characters in the East Asian Wide (W) or East Asian
     *      Full-width (F) category as defined in Unicode Technical
     *      Report #11 have a column width of 2. This includes BMP
     *      characters with Emoji_Presentation=Yes, which are EAW=W
     *      in Unicode 16.0.
     *
     *    - All remaining characters (including all printable
     *      ISO 8859-1 and WGL4 characters, Unicode control characters,
     *      etc.) have a column width of 1.
     *
     * This implementation assumes that wchar_t characters are encoded
     * in ISO 10646.
     */
    public static int wcwidth(int ucs) {

        /* test for 8-bit control characters */
        if (ucs == 0) return 0;
        if (ucs < 32 || (ucs >= 0x7f && ucs < 0xa0)) return -1;

        /* binary search in table of non-spacing characters */
        if (bisearch(ucs, combining, combining.length - 1)) return 0;

        /* East Asian Wide (W) and Fullwidth (F) characters — Unicode 16.0
         * Also covers BMP Emoji_Presentation=Yes characters (U+231A, U+2615, etc.)
         * which are EAW=W in Unicode 16.0.
         * See https://unicode.org/Public/16.0.0/ucd/EastAsianWidth.txt */
        if (bisearch(ucs, wide, wide.length - 1)) return 2;

        /* if we arrive here, ucs is not a combining, control, emoji, or wide character */
        return 1;
    }

    /* Sorted list of non-overlapping intervals of non-spacing characters.
     * Generated from Unicode 16.0 UnicodeData.txt:
     *   categories Mn (Nonspacing Mark) + Me (Enclosing Mark) + Cf (Format)
     *   minus U+00AD (Soft Hyphen)
     *   plus U+1160-11FF (Hangul Jamo medial vowels and final consonants)
     *   plus U+200B (Zero Width Space)
     *   plus U+1F3FB-1F3FF (Emoji skin tone modifiers)
     * 2367 codepoints in 369 intervals */
    // @spotless:off
    static final Interval[] combining = {
        new Interval(0x0300, 0x036F), new Interval(0x0483, 0x0489), new Interval(0x0591, 0x05BD),
        new Interval(0x05BF, 0x05BF), new Interval(0x05C1, 0x05C2), new Interval(0x05C4, 0x05C5),
        new Interval(0x05C7, 0x05C7), new Interval(0x0600, 0x0605), new Interval(0x0610, 0x061A),
        new Interval(0x061C, 0x061C), new Interval(0x064B, 0x065F), new Interval(0x0670, 0x0670),
        new Interval(0x06D6, 0x06DD), new Interval(0x06DF, 0x06E4), new Interval(0x06E7, 0x06E8),
        new Interval(0x06EA, 0x06ED), new Interval(0x070F, 0x070F), new Interval(0x0711, 0x0711),
        new Interval(0x0730, 0x074A), new Interval(0x07A6, 0x07B0), new Interval(0x07EB, 0x07F3),
        new Interval(0x07FD, 0x07FD), new Interval(0x0816, 0x0819), new Interval(0x081B, 0x0823),
        new Interval(0x0825, 0x0827), new Interval(0x0829, 0x082D), new Interval(0x0859, 0x085B),
        new Interval(0x0890, 0x0891), new Interval(0x0897, 0x089F), new Interval(0x08CA, 0x0902),
        new Interval(0x093A, 0x093A), new Interval(0x093C, 0x093C), new Interval(0x0941, 0x0948),
        new Interval(0x094D, 0x094D), new Interval(0x0951, 0x0957), new Interval(0x0962, 0x0963),
        new Interval(0x0981, 0x0981), new Interval(0x09BC, 0x09BC), new Interval(0x09C1, 0x09C4),
        new Interval(0x09CD, 0x09CD), new Interval(0x09E2, 0x09E3), new Interval(0x09FE, 0x09FE),
        new Interval(0x0A01, 0x0A02), new Interval(0x0A3C, 0x0A3C), new Interval(0x0A41, 0x0A42),
        new Interval(0x0A47, 0x0A48), new Interval(0x0A4B, 0x0A4D), new Interval(0x0A51, 0x0A51),
        new Interval(0x0A70, 0x0A71), new Interval(0x0A75, 0x0A75), new Interval(0x0A81, 0x0A82),
        new Interval(0x0ABC, 0x0ABC), new Interval(0x0AC1, 0x0AC5), new Interval(0x0AC7, 0x0AC8),
        new Interval(0x0ACD, 0x0ACD), new Interval(0x0AE2, 0x0AE3), new Interval(0x0AFA, 0x0AFF),
        new Interval(0x0B01, 0x0B01), new Interval(0x0B3C, 0x0B3C), new Interval(0x0B3F, 0x0B3F),
        new Interval(0x0B41, 0x0B44), new Interval(0x0B4D, 0x0B4D), new Interval(0x0B55, 0x0B56),
        new Interval(0x0B62, 0x0B63), new Interval(0x0B82, 0x0B82), new Interval(0x0BC0, 0x0BC0),
        new Interval(0x0BCD, 0x0BCD), new Interval(0x0C00, 0x0C00), new Interval(0x0C04, 0x0C04),
        new Interval(0x0C3C, 0x0C3C), new Interval(0x0C3E, 0x0C40), new Interval(0x0C46, 0x0C48),
        new Interval(0x0C4A, 0x0C4D), new Interval(0x0C55, 0x0C56), new Interval(0x0C62, 0x0C63),
        new Interval(0x0C81, 0x0C81), new Interval(0x0CBC, 0x0CBC), new Interval(0x0CBF, 0x0CBF),
        new Interval(0x0CC6, 0x0CC6), new Interval(0x0CCC, 0x0CCD), new Interval(0x0CE2, 0x0CE3),
        new Interval(0x0D00, 0x0D01), new Interval(0x0D3B, 0x0D3C), new Interval(0x0D41, 0x0D44),
        new Interval(0x0D4D, 0x0D4D), new Interval(0x0D62, 0x0D63), new Interval(0x0D81, 0x0D81),
        new Interval(0x0DCA, 0x0DCA), new Interval(0x0DD2, 0x0DD4), new Interval(0x0DD6, 0x0DD6),
        new Interval(0x0E31, 0x0E31), new Interval(0x0E34, 0x0E3A), new Interval(0x0E47, 0x0E4E),
        new Interval(0x0EB1, 0x0EB1), new Interval(0x0EB4, 0x0EBC), new Interval(0x0EC8, 0x0ECE),
        new Interval(0x0F18, 0x0F19), new Interval(0x0F35, 0x0F35), new Interval(0x0F37, 0x0F37),
        new Interval(0x0F39, 0x0F39), new Interval(0x0F71, 0x0F7E), new Interval(0x0F80, 0x0F84),
        new Interval(0x0F86, 0x0F87), new Interval(0x0F8D, 0x0F97), new Interval(0x0F99, 0x0FBC),
        new Interval(0x0FC6, 0x0FC6), new Interval(0x102D, 0x1030), new Interval(0x1032, 0x1037),
        new Interval(0x1039, 0x103A), new Interval(0x103D, 0x103E), new Interval(0x1058, 0x1059),
        new Interval(0x105E, 0x1060), new Interval(0x1071, 0x1074), new Interval(0x1082, 0x1082),
        new Interval(0x1085, 0x1086), new Interval(0x108D, 0x108D), new Interval(0x109D, 0x109D),
        new Interval(0x1160, 0x11FF), new Interval(0x135D, 0x135F), new Interval(0x1712, 0x1714),
        new Interval(0x1732, 0x1733), new Interval(0x1752, 0x1753), new Interval(0x1772, 0x1773),
        new Interval(0x17B4, 0x17B5), new Interval(0x17B7, 0x17BD), new Interval(0x17C6, 0x17C6),
        new Interval(0x17C9, 0x17D3), new Interval(0x17DD, 0x17DD), new Interval(0x180B, 0x180F),
        new Interval(0x1885, 0x1886), new Interval(0x18A9, 0x18A9), new Interval(0x1920, 0x1922),
        new Interval(0x1927, 0x1928), new Interval(0x1932, 0x1932), new Interval(0x1939, 0x193B),
        new Interval(0x1A17, 0x1A18), new Interval(0x1A1B, 0x1A1B), new Interval(0x1A56, 0x1A56),
        new Interval(0x1A58, 0x1A5E), new Interval(0x1A60, 0x1A60), new Interval(0x1A62, 0x1A62),
        new Interval(0x1A65, 0x1A6C), new Interval(0x1A73, 0x1A7C), new Interval(0x1A7F, 0x1A7F),
        new Interval(0x1AB0, 0x1ACE), new Interval(0x1B00, 0x1B03), new Interval(0x1B34, 0x1B34),
        new Interval(0x1B36, 0x1B3A), new Interval(0x1B3C, 0x1B3C), new Interval(0x1B42, 0x1B42),
        new Interval(0x1B6B, 0x1B73), new Interval(0x1B80, 0x1B81), new Interval(0x1BA2, 0x1BA5),
        new Interval(0x1BA8, 0x1BA9), new Interval(0x1BAB, 0x1BAD), new Interval(0x1BE6, 0x1BE6),
        new Interval(0x1BE8, 0x1BE9), new Interval(0x1BED, 0x1BED), new Interval(0x1BEF, 0x1BF1),
        new Interval(0x1C2C, 0x1C33), new Interval(0x1C36, 0x1C37), new Interval(0x1CD0, 0x1CD2),
        new Interval(0x1CD4, 0x1CE0), new Interval(0x1CE2, 0x1CE8), new Interval(0x1CED, 0x1CED),
        new Interval(0x1CF4, 0x1CF4), new Interval(0x1CF8, 0x1CF9), new Interval(0x1DC0, 0x1DFF),
        new Interval(0x200B, 0x200F), new Interval(0x202A, 0x202E), new Interval(0x2060, 0x2064),
        new Interval(0x2066, 0x206F), new Interval(0x20D0, 0x20F0), new Interval(0x2CEF, 0x2CF1),
        new Interval(0x2D7F, 0x2D7F), new Interval(0x2DE0, 0x2DFF), new Interval(0x302A, 0x302D),
        new Interval(0x3099, 0x309A), new Interval(0xA66F, 0xA672), new Interval(0xA674, 0xA67D),
        new Interval(0xA69E, 0xA69F), new Interval(0xA6F0, 0xA6F1), new Interval(0xA802, 0xA802),
        new Interval(0xA806, 0xA806), new Interval(0xA80B, 0xA80B), new Interval(0xA825, 0xA826),
        new Interval(0xA82C, 0xA82C), new Interval(0xA8C4, 0xA8C5), new Interval(0xA8E0, 0xA8F1),
        new Interval(0xA8FF, 0xA8FF), new Interval(0xA926, 0xA92D), new Interval(0xA947, 0xA951),
        new Interval(0xA980, 0xA982), new Interval(0xA9B3, 0xA9B3), new Interval(0xA9B6, 0xA9B9),
        new Interval(0xA9BC, 0xA9BD), new Interval(0xA9E5, 0xA9E5), new Interval(0xAA29, 0xAA2E),
        new Interval(0xAA31, 0xAA32), new Interval(0xAA35, 0xAA36), new Interval(0xAA43, 0xAA43),
        new Interval(0xAA4C, 0xAA4C), new Interval(0xAA7C, 0xAA7C), new Interval(0xAAB0, 0xAAB0),
        new Interval(0xAAB2, 0xAAB4), new Interval(0xAAB7, 0xAAB8), new Interval(0xAABE, 0xAABF),
        new Interval(0xAAC1, 0xAAC1), new Interval(0xAAEC, 0xAAED), new Interval(0xAAF6, 0xAAF6),
        new Interval(0xABE5, 0xABE5), new Interval(0xABE8, 0xABE8), new Interval(0xABED, 0xABED),
        new Interval(0xFB1E, 0xFB1E), new Interval(0xFE00, 0xFE0F), new Interval(0xFE20, 0xFE2F),
        new Interval(0xFEFF, 0xFEFF), new Interval(0xFFF9, 0xFFFB), new Interval(0x101FD, 0x101FD),
        new Interval(0x102E0, 0x102E0), new Interval(0x10376, 0x1037A), new Interval(0x10A01, 0x10A03),
        new Interval(0x10A05, 0x10A06), new Interval(0x10A0C, 0x10A0F), new Interval(0x10A38, 0x10A3A),
        new Interval(0x10A3F, 0x10A3F), new Interval(0x10AE5, 0x10AE6), new Interval(0x10D24, 0x10D27),
        new Interval(0x10D69, 0x10D6D), new Interval(0x10EAB, 0x10EAC), new Interval(0x10EFC, 0x10EFF),
        new Interval(0x10F46, 0x10F50), new Interval(0x10F82, 0x10F85), new Interval(0x11001, 0x11001),
        new Interval(0x11038, 0x11046), new Interval(0x11070, 0x11070), new Interval(0x11073, 0x11074),
        new Interval(0x1107F, 0x11081), new Interval(0x110B3, 0x110B6), new Interval(0x110B9, 0x110BA),
        new Interval(0x110BD, 0x110BD), new Interval(0x110C2, 0x110C2), new Interval(0x110CD, 0x110CD),
        new Interval(0x11100, 0x11102), new Interval(0x11127, 0x1112B), new Interval(0x1112D, 0x11134),
        new Interval(0x11173, 0x11173), new Interval(0x11180, 0x11181), new Interval(0x111B6, 0x111BE),
        new Interval(0x111C9, 0x111CC), new Interval(0x111CF, 0x111CF), new Interval(0x1122F, 0x11231),
        new Interval(0x11234, 0x11234), new Interval(0x11236, 0x11237), new Interval(0x1123E, 0x1123E),
        new Interval(0x11241, 0x11241), new Interval(0x112DF, 0x112DF), new Interval(0x112E3, 0x112EA),
        new Interval(0x11300, 0x11301), new Interval(0x1133B, 0x1133C), new Interval(0x11340, 0x11340),
        new Interval(0x11366, 0x1136C), new Interval(0x11370, 0x11374), new Interval(0x113BB, 0x113C0),
        new Interval(0x113CE, 0x113CE), new Interval(0x113D0, 0x113D0), new Interval(0x113D2, 0x113D2),
        new Interval(0x113E1, 0x113E2), new Interval(0x11438, 0x1143F), new Interval(0x11442, 0x11444),
        new Interval(0x11446, 0x11446), new Interval(0x1145E, 0x1145E), new Interval(0x114B3, 0x114B8),
        new Interval(0x114BA, 0x114BA), new Interval(0x114BF, 0x114C0), new Interval(0x114C2, 0x114C3),
        new Interval(0x115B2, 0x115B5), new Interval(0x115BC, 0x115BD), new Interval(0x115BF, 0x115C0),
        new Interval(0x115DC, 0x115DD), new Interval(0x11633, 0x1163A), new Interval(0x1163D, 0x1163D),
        new Interval(0x1163F, 0x11640), new Interval(0x116AB, 0x116AB), new Interval(0x116AD, 0x116AD),
        new Interval(0x116B0, 0x116B5), new Interval(0x116B7, 0x116B7), new Interval(0x1171D, 0x1171D),
        new Interval(0x1171F, 0x1171F), new Interval(0x11722, 0x11725), new Interval(0x11727, 0x1172B),
        new Interval(0x1182F, 0x11837), new Interval(0x11839, 0x1183A), new Interval(0x1193B, 0x1193C),
        new Interval(0x1193E, 0x1193E), new Interval(0x11943, 0x11943), new Interval(0x119D4, 0x119D7),
        new Interval(0x119DA, 0x119DB), new Interval(0x119E0, 0x119E0), new Interval(0x11A01, 0x11A0A),
        new Interval(0x11A33, 0x11A38), new Interval(0x11A3B, 0x11A3E), new Interval(0x11A47, 0x11A47),
        new Interval(0x11A51, 0x11A56), new Interval(0x11A59, 0x11A5B), new Interval(0x11A8A, 0x11A96),
        new Interval(0x11A98, 0x11A99), new Interval(0x11C30, 0x11C36), new Interval(0x11C38, 0x11C3D),
        new Interval(0x11C3F, 0x11C3F), new Interval(0x11C92, 0x11CA7), new Interval(0x11CAA, 0x11CB0),
        new Interval(0x11CB2, 0x11CB3), new Interval(0x11CB5, 0x11CB6), new Interval(0x11D31, 0x11D36),
        new Interval(0x11D3A, 0x11D3A), new Interval(0x11D3C, 0x11D3D), new Interval(0x11D3F, 0x11D45),
        new Interval(0x11D47, 0x11D47), new Interval(0x11D90, 0x11D91), new Interval(0x11D95, 0x11D95),
        new Interval(0x11D97, 0x11D97), new Interval(0x11EF3, 0x11EF4), new Interval(0x11F00, 0x11F01),
        new Interval(0x11F36, 0x11F3A), new Interval(0x11F40, 0x11F40), new Interval(0x11F42, 0x11F42),
        new Interval(0x11F5A, 0x11F5A), new Interval(0x13430, 0x13440), new Interval(0x13447, 0x13455),
        new Interval(0x1611E, 0x16129), new Interval(0x1612D, 0x1612F), new Interval(0x16AF0, 0x16AF4),
        new Interval(0x16B30, 0x16B36), new Interval(0x16F4F, 0x16F4F), new Interval(0x16F8F, 0x16F92),
        new Interval(0x16FE4, 0x16FE4), new Interval(0x1BC9D, 0x1BC9E), new Interval(0x1BCA0, 0x1BCA3),
        new Interval(0x1CF00, 0x1CF2D), new Interval(0x1CF30, 0x1CF46), new Interval(0x1D167, 0x1D169),
        new Interval(0x1D173, 0x1D182), new Interval(0x1D185, 0x1D18B), new Interval(0x1D1AA, 0x1D1AD),
        new Interval(0x1D242, 0x1D244), new Interval(0x1DA00, 0x1DA36), new Interval(0x1DA3B, 0x1DA6C),
        new Interval(0x1DA75, 0x1DA75), new Interval(0x1DA84, 0x1DA84), new Interval(0x1DA9B, 0x1DA9F),
        new Interval(0x1DAA1, 0x1DAAF), new Interval(0x1E000, 0x1E006), new Interval(0x1E008, 0x1E018),
        new Interval(0x1E01B, 0x1E021), new Interval(0x1E023, 0x1E024), new Interval(0x1E026, 0x1E02A),
        new Interval(0x1E08F, 0x1E08F), new Interval(0x1E130, 0x1E136), new Interval(0x1E2AE, 0x1E2AE),
        new Interval(0x1E2EC, 0x1E2EF), new Interval(0x1E4EC, 0x1E4EF), new Interval(0x1E5EE, 0x1E5EF),
        new Interval(0x1E8D0, 0x1E8D6), new Interval(0x1E944, 0x1E94A), new Interval(0x1F3FB, 0x1F3FF),
        new Interval(0xE0001, 0xE0001), new Interval(0xE0020, 0xE007F), new Interval(0xE0100, 0xE01EF)
    };
    // @spotless:on

    /* Sorted list of non-overlapping intervals of East Asian Wide (W) and
     * Fullwidth (F) characters. Generated from Unicode 16.0 EastAsianWidth.txt.
     * Used for binary search to determine width-2 characters. */
    // @spotless:off
    static final Interval[] wide = {
        new Interval(0x1100, 0x115F),   /* Hangul Jamo */
        new Interval(0x231A, 0x231B),   /* Watch, Hourglass */
        new Interval(0x2329, 0x232A),   /* Angle brackets */
        new Interval(0x23E9, 0x23EC),   /* Playback symbols */
        new Interval(0x23F0, 0x23F0),   /* Alarm clock */
        new Interval(0x23F3, 0x23F3),   /* Hourglass flowing */
        new Interval(0x25FD, 0x25FE),   /* Medium small squares */
        new Interval(0x2614, 0x2615),   /* Umbrella, Hot beverage */
        new Interval(0x2630, 0x2637),   /* Trigrams */
        new Interval(0x2648, 0x2653),   /* Zodiac signs */
        new Interval(0x267F, 0x267F),   /* Wheelchair */
        new Interval(0x268A, 0x268F),   /* Yijing mono/digrams */
        new Interval(0x2693, 0x2693),   /* Anchor */
        new Interval(0x26A1, 0x26A1),   /* High voltage */
        new Interval(0x26AA, 0x26AB),   /* Circles */
        new Interval(0x26BD, 0x26BE),   /* Soccer, Baseball */
        new Interval(0x26C4, 0x26C5),   /* Snowman, Sun behind cloud */
        new Interval(0x26CE, 0x26CE),   /* Ophiuchus */
        new Interval(0x26D4, 0x26D4),   /* No entry */
        new Interval(0x26EA, 0x26EA),   /* Church */
        new Interval(0x26F2, 0x26F3),   /* Fountain, Golf */
        new Interval(0x26F5, 0x26F5),   /* Sailboat */
        new Interval(0x26FA, 0x26FA),   /* Tent */
        new Interval(0x26FD, 0x26FD),   /* Fuel pump */
        new Interval(0x2705, 0x2705),   /* Check mark */
        new Interval(0x270A, 0x270B),   /* Raised fist, Raised hand */
        new Interval(0x2728, 0x2728),   /* Sparkles */
        new Interval(0x274C, 0x274C),   /* Cross mark */
        new Interval(0x274E, 0x274E),   /* Cross mark (square) */
        new Interval(0x2753, 0x2755),   /* Question/Exclamation marks */
        new Interval(0x2757, 0x2757),   /* Exclamation mark */
        new Interval(0x2795, 0x2797),   /* Plus, Minus, Division */
        new Interval(0x27B0, 0x27B0),   /* Curly loop */
        new Interval(0x27BF, 0x27BF),   /* Double curly loop */
        new Interval(0x2B1B, 0x2B1C),   /* Black/White large squares */
        new Interval(0x2B50, 0x2B50),   /* Star */
        new Interval(0x2B55, 0x2B55),   /* Heavy circle */
        new Interval(0x2E80, 0x303E),   /* CJK Radicals .. CJK Symbols (excl. U+303F) */
        new Interval(0x3041, 0x33BF),   /* Hiragana .. CJK Compatibility */
        new Interval(0x33C0, 0x33FF),   /* CJK Compatibility (cont) */
        new Interval(0x3400, 0x4DFF),   /* CJK Unified Ideographs Extension A + Yijing Hexagrams */
        new Interval(0x4E00, 0xA4CF),   /* CJK Unified Ideographs .. Yi */
        new Interval(0xA960, 0xA97C),   /* Hangul Jamo Extended-A */
        new Interval(0xAC00, 0xD7A3),   /* Hangul Syllables */
        new Interval(0xF900, 0xFAFF),   /* CJK Compatibility Ideographs */
        new Interval(0xFE10, 0xFE19),   /* Vertical forms */
        new Interval(0xFE30, 0xFE6F),   /* CJK Compatibility Forms */
        new Interval(0xFF00, 0xFF60),   /* Fullwidth Forms */
        new Interval(0xFFE0, 0xFFE6),   /* Fullwidth Signs */
        new Interval(0x16FE0, 0x16FF1), /* Ideographic Symbols and Punctuation */
        new Interval(0x17000, 0x187F7), /* Tangut */
        new Interval(0x18800, 0x18CD5), /* Tangut Components */
        new Interval(0x18CFF, 0x18D08), /* Tangut Supplement */
        new Interval(0x1AFF0, 0x1AFF3), /* Kana Extended-B */
        new Interval(0x1AFF5, 0x1AFFB), /* Kana Extended-B (cont) */
        new Interval(0x1AFFD, 0x1AFFE), /* Kana Extended-B (cont) */
        new Interval(0x1B000, 0x1B122), /* Kana Supplement */
        new Interval(0x1B132, 0x1B132), /* Small Kana */
        new Interval(0x1B150, 0x1B152), /* Small Kana Extension */
        new Interval(0x1B155, 0x1B155), /* Small Kana (cont) */
        new Interval(0x1B164, 0x1B167), /* Small Kana Extension (cont) */
        new Interval(0x1B170, 0x1B2FB), /* Nushu */
        new Interval(0x1D300, 0x1D356), /* Tai Xuan Jing Symbols */
        new Interval(0x1D360, 0x1D376), /* Counting Rod Numerals */
        new Interval(0x1F004, 0x1F004), /* Mahjong Red Dragon */
        new Interval(0x1F0CF, 0x1F0CF), /* Playing Card Black Joker */
        new Interval(0x1F100, 0x1F10A), /* Enclosed Alphanumeric Supplement */
        new Interval(0x1F110, 0x1F12D), /* Enclosed Alphanumeric Supplement (cont) */
        new Interval(0x1F130, 0x1F169), /* Enclosed Alphanumeric Supplement (cont) */
        new Interval(0x1F170, 0x1F1AC), /* Enclosed Alphanumeric Supplement (cont) */
        new Interval(0x1F1E6, 0x1F202), /* Regional Indicators .. Enclosed Ideographic */
        new Interval(0x1F210, 0x1F23B), /* Enclosed Ideographic Supplement */
        new Interval(0x1F240, 0x1F248), /* Enclosed Ideographic Supplement (cont) */
        new Interval(0x1F250, 0x1F251), /* Enclosed Ideographic Supplement (cont) */
        new Interval(0x1F260, 0x1F265), /* Enclosed Ideographic Supplement (cont) */
        new Interval(0x1F300, 0x1F320), /* Miscellaneous Symbols and Pictographs */
        new Interval(0x1F32D, 0x1F335), /* Food and Drink */
        new Interval(0x1F337, 0x1F37C), /* Plants and Nature */
        new Interval(0x1F37E, 0x1F393), /* Drinks and Celebrations */
        new Interval(0x1F3A0, 0x1F3CA), /* Activities */
        new Interval(0x1F3CF, 0x1F3D3), /* Sports */
        new Interval(0x1F3E0, 0x1F3F0), /* Buildings */
        new Interval(0x1F3F4, 0x1F3F4), /* Black Flag */
        new Interval(0x1F3F8, 0x1F43E), /* Sports and Animals */
        new Interval(0x1F440, 0x1F440), /* Eyes */
        new Interval(0x1F442, 0x1F4FC), /* People and Objects */
        new Interval(0x1F4FF, 0x1F53D), /* Objects */
        new Interval(0x1F54B, 0x1F54E), /* Religious */
        new Interval(0x1F550, 0x1F567), /* Clock faces */
        new Interval(0x1F57A, 0x1F57A), /* Dancing */
        new Interval(0x1F595, 0x1F596), /* Gestures */
        new Interval(0x1F5A4, 0x1F5A4), /* Black Heart */
        new Interval(0x1F5FB, 0x1F64F), /* Places and People */
        new Interval(0x1F680, 0x1F6C5), /* Transport */
        new Interval(0x1F6CC, 0x1F6CC), /* Sleeping */
        new Interval(0x1F6D0, 0x1F6D2), /* Shopping */
        new Interval(0x1F6D5, 0x1F6D7), /* Places */
        new Interval(0x1F6DC, 0x1F6DF), /* Transport (cont) */
        new Interval(0x1F6EB, 0x1F6EC), /* Airplane */
        new Interval(0x1F6F4, 0x1F6FC), /* Transport */
        new Interval(0x1F7E0, 0x1F7EB), /* Colored circles/squares */
        new Interval(0x1F7F0, 0x1F7F0), /* Heavy equals sign */
        new Interval(0x1F90C, 0x1F93A), /* Gestures and Activities */
        new Interval(0x1F93C, 0x1F945), /* Sports */
        new Interval(0x1F947, 0x1F9FF), /* Awards, Objects, People */
        new Interval(0x1FA00, 0x1FA53), /* Chess symbols */
        new Interval(0x1FA60, 0x1FA6D), /* Xiangqi */
        new Interval(0x1FA70, 0x1FA7C), /* Symbols and Pictographs Extended-A */
        new Interval(0x1FA80, 0x1FA89), /* Symbols and Pictographs Extended-A (cont) */
        new Interval(0x1FA8F, 0x1FAC6), /* Symbols and Pictographs Extended-A (cont) */
        new Interval(0x1FACE, 0x1FADC), /* Symbols and Pictographs Extended-A (cont) */
        new Interval(0x1FADF, 0x1FAE9), /* Symbols and Pictographs Extended-A (cont) */
        new Interval(0x1FAF0, 0x1FAF8), /* Hand gestures */
        new Interval(0x20000, 0x2FFFD), /* CJK Unified Ideographs Extension B..F */
        new Interval(0x30000, 0x3FFFD), /* CJK Unified Ideographs Extension G..J */
    };
    // @spotless:on

    private static class Interval {
        public final int first;
        public final int last;

        public Interval(int first, int last) {
            this.first = first;
            this.last = last;
        }
    }

    /**
     * Returns the number of {@code char}s consumed by the grapheme cluster
     * starting at {@code index} in the given {@code CharSequence}.
     *
     * <p>A grapheme cluster is a user-perceived character that may be composed
     * of multiple Unicode code points. This method recognizes:</p>
     * <ul>
     *   <li>ZWJ sequences (e.g., family emoji 👨‍👩‍👧‍👦)</li>
     *   <li>Regional indicator pairs (flags, e.g., 🇫🇷)</li>
     *   <li>Emoji modifier sequences (skin tones, e.g., 👋🏽)</li>
     *   <li>Variation selector sequences (U+FE0E, U+FE0F)</li>
     *   <li>Combining mark sequences</li>
     * </ul>
     *
     * @param cs    the character sequence
     * @param index the starting char index
     * @return the number of chars consumed by the grapheme cluster
     */
    public static int charCountForGraphemeCluster(CharSequence cs, int index) {
        if (HAS_JDK_GRAPHEME_SUPPORT) {
            return charCountForGraphemeClusterBreakIterator(cs, index);
        }
        return charCountForGraphemeClusterLegacy(cs, index);
    }

    /**
     * Creates a {@link BreakIterator} configured for grapheme cluster segmentation
     * of the given character sequence. Returns {@code null} when JDK grapheme
     * support is unavailable (&lt; 21).
     *
     * <p>The returned iterator is bound to the {@code String} produced by
     * {@code cs.toString()} at creation time. It must only be reused with the
     * <em>same</em> character sequence; passing it to
     * {@link #charCountForGraphemeClusterBreakIterator(CharSequence, int, BreakIterator)}
     * with a different sequence will produce incorrect results. Create a new
     * iterator for each distinct sequence.</p>
     */
    static BreakIterator createGraphemeBreakIterator(CharSequence cs) {
        if (!HAS_JDK_GRAPHEME_SUPPORT) return null;
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(cs.toString());
        return bi;
    }

    /**
     * Uses JDK 21+ {@link BreakIterator} with full UAX #29 Extended Grapheme
     * Cluster segmentation. Automatically stays current with new Unicode
     * versions as the JDK is updated.
     */
    static int charCountForGraphemeClusterBreakIterator(CharSequence cs, int index) {
        int len = cs.length();
        if (index >= len) return 0;
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(cs.toString());
        return charCountForGraphemeClusterBreakIterator(cs, index, bi);
    }

    /**
     * Uses a pre-configured {@link BreakIterator} to find the grapheme cluster
     * boundary at the given index, avoiding per-call allocation.
     */
    static int charCountForGraphemeClusterBreakIterator(CharSequence cs, int index, BreakIterator bi) {
        int len = cs.length();
        if (index >= len) return 0;
        int next = bi.following(index);
        if (next == BreakIterator.DONE) {
            return len - index;
        }
        return next - index;
    }

    /**
     * Fallback grapheme cluster segmentation for JDK &lt; 21.
     * Handles ZWJ sequences, regional indicator pairs, emoji modifiers,
     * variation selectors, and combining marks using heuristics.
     */
    static int charCountForGraphemeClusterLegacy(CharSequence cs, int index) {
        int len = cs.length();
        if (index >= len) return 0;

        int cp = Character.codePointAt(cs, index);
        int pos = index + Character.charCount(cp);

        // Regional indicator pairs form a single grapheme cluster (flag)
        if (isRegionalIndicator(cp) && pos < len) {
            int next = Character.codePointAt(cs, pos);
            if (isRegionalIndicator(next)) {
                pos += Character.charCount(next);
            }
            return pos - index;
        }

        // Consume grapheme cluster extensions
        while (pos < len) {
            int ncp = Character.codePointAt(cs, pos);
            if (ncp == 0x200D) { // ZWJ — joins with the next character
                int zwjSize = Character.charCount(ncp);
                if (pos + zwjSize < len) {
                    pos += zwjSize;
                    pos += Character.charCount(Character.codePointAt(cs, pos));
                } else {
                    break;
                }
            } else if (wcwidth(ncp) == 0 && ncp >= 0x20) {
                // Zero-width extending characters: combining marks,
                // variation selectors (FE0E/FE0F), skin tone modifiers,
                // tag characters, etc.
                pos += Character.charCount(ncp);
            } else {
                break;
            }
        }

        return pos - index;
    }

    /**
     * Returns the display width of the grapheme cluster starting at {@code index}.
     *
     * <p>Variation selectors override the base code point's width:
     * VS16 ({@code U+FE0F}) upgrades the cluster to emoji presentation (width 2),
     * while VS15 ({@code U+FE0E}) downgrades it to text presentation (width 1).
     * When neither is present, the width of the base code point (via
     * {@link #wcwidth(int)}) is used.</p>
     *
     * @param cs    the character sequence
     * @param index the starting char index
     * @return the display width of the grapheme cluster, same range as {@link #wcwidth(int)}
     */
    public static int wcwidthForGraphemeCluster(CharSequence cs, int index) {
        return wcwidthForGraphemeCluster(cs, index, charCountForGraphemeCluster(cs, index));
    }

    /**
     * Returns the display width of the grapheme cluster of the given
     * {@code clusterCharCount} chars starting at {@code index}.
     *
     * <p>This overload avoids recomputing the cluster boundaries when the
     * caller already obtained them from
     * {@link #charCountForGraphemeCluster(CharSequence, int)}.</p>
     *
     * @param cs               the character sequence
     * @param index            the starting char index
     * @param clusterCharCount number of {@code char}s in the cluster
     * @return the display width of the grapheme cluster, same range as {@link #wcwidth(int)}
     */
    static int wcwidthForGraphemeCluster(CharSequence cs, int index, int clusterCharCount) {
        int cp = Character.codePointAt(cs, index);
        int w = wcwidth(cp);

        // Scan the cluster for variation selectors
        int end = index + clusterCharCount;
        int pos = index + Character.charCount(cp);
        while (pos < end) {
            int ncp = Character.codePointAt(cs, pos);
            if (ncp == 0xFE0F) {
                return 2; // VS16 — emoji presentation (width 2)
            } else if (ncp == 0xFE0E) {
                return 1; // VS15 — text presentation (width 1)
            }
            pos += Character.charCount(ncp);
        }

        return w;
    }

    /**
     * Compute the display width in terminal columns of the character or grapheme cluster
     * that begins at the given index in the character sequence.
     *
     * <p>If the terminal has grapheme-cluster mode enabled, or if {@code terminal} is
     * {@code null} and the runtime provides JDK-level grapheme-cluster support (JDK 21+),
     * the measurement is grapheme-cluster-aware so emoji variation selectors and ZWJ
     * sequences are handled as a single display unit. Otherwise the width of the
     * single code point at {@code index} is returned.</p>
     *
     * @param cs       the character sequence containing the cluster
     * @param index    the starting char index of the character or cluster
     * @param terminal the terminal to query for grapheme-cluster mode, or {@code null}
     * @return         the display width in terminal columns for the character or cluster
     */
    public static int wcwidthForDisplay(CharSequence cs, int index, Terminal terminal) {
        if ((terminal != null && terminal.getGraphemeClusterMode()) || (terminal == null && HAS_JDK_GRAPHEME_SUPPORT)) {
            int charCount = charCountForGraphemeCluster(cs, index);
            return wcwidthForDisplayWithGroupings(cs, index, terminal, charCount);
        }
        return wcwidth(Character.codePointAt(cs, index));
    }

    /**
     * Compute the display width in terminal columns of the character or grapheme cluster
     * starting at the given char index, using a provided cluster char count to avoid
     * recomputing cluster boundaries.
     *
     * @param cs       the character sequence containing the cluster
     * @param index    the starting char index of the character or cluster
     * @param terminal the terminal to query for grapheme-cluster grouping behavior; may be {@code null}
     * @param charCount the number of Java `char`s occupied by the character or grapheme cluster
     *                  starting at {@code index}
     * @return the display width in terminal columns for the character or grapheme cluster
     */
    static int wcwidthForDisplay(CharSequence cs, int index, Terminal terminal, int charCount) {
        if ((terminal != null && terminal.getGraphemeClusterMode()) || (terminal == null && HAS_JDK_GRAPHEME_SUPPORT)) {
            return wcwidthForDisplayWithGroupings(cs, index, terminal, charCount);
        }
        return wcwidth(Character.codePointAt(cs, index));
    }

    /**
     * Compute the number of Java chars that form the display unit (code point or grapheme cluster) starting at {@code index} in {@code cs}.
     *
     * <p>If the terminal indicates grapheme cluster mode, or when {@code terminal} is {@code null} and JDK grapheme-cluster support is available (JDK 21+), this uses grapheme-cluster segmentation so ZWJ sequences, flag pairs, skin-tone modifiers, and similar multi-code-point units are treated as a single unit and may span multiple {@code char}s. Otherwise it returns the {@link Character#charCount(int) char count} for the code point at {@code index}.</p>
     *
     * @param cs the character sequence
     * @param index the starting char index
     * @param terminal the terminal to consult for grapheme cluster mode, or {@code null}
     * @return the number of {@code char} units to advance past the display unit beginning at {@code index}
     */
    public static int charCountForDisplay(CharSequence cs, int index, Terminal terminal) {
        return charCountForDisplay(cs, index, terminal, null);
    }

    /**
     * Compute the number of Java chars that form the display unit starting at {@code index},
     * reusing a pre-configured {@link BreakIterator} to avoid per-call allocation.
     *
     * @param cs the character sequence
     * @param index the starting char index
     * @param terminal the terminal to consult for grapheme cluster mode, or {@code null}
     * @param bi a pre-configured BreakIterator from {@link #createGraphemeBreakIterator}, or {@code null}
     * @return the number of {@code char} units to advance past the display unit beginning at {@code index}
     */
    static int charCountForDisplay(CharSequence cs, int index, Terminal terminal, BreakIterator bi) {
        if ((terminal != null && terminal.getGraphemeClusterMode()) || (terminal == null && HAS_JDK_GRAPHEME_SUPPORT)) {
            int charCount;
            if (bi != null) {
                charCount = charCountForGraphemeClusterBreakIterator(cs, index, bi);
            } else {
                charCount = charCountForGraphemeCluster(cs, index);
            }
            return charCountForDisplayWithGroupings(cs, index, terminal, charCount);
        }
        return Character.charCount(Character.codePointAt(cs, index));
    }

    /**
     * Compute the display width in columns for the grapheme cluster starting at the given index,
     * honoring the terminal's per-category emoji grouping support.
     *
     * If the terminal is null or supports grouping for this cluster, the cluster is measured as a
     * single grapheme and its cluster width is returned. If the cluster is an emoji grouping that
     * the terminal does not support, the returned width is the sum of the individual code-point
     * widths in the cluster. If the cluster contains a single non-grouped code point, the width of
     * that base code point is returned.
     *
     * @param cs the character sequence containing the cluster
     * @param index the index of the first char of the cluster in {@code cs}
     * @param terminal the terminal whose emoji grouping support determines grouping behavior; may be {@code null}
     * @param charCount the number of Java {@code char} units that make up the grapheme cluster at {@code index}
     * @return the display width in columns for the cluster (or base code point / summed per-code-point width)
     */
    private static int wcwidthForDisplayWithGroupings(CharSequence cs, int index, Terminal terminal, int charCount) {
        if (terminal == null || isGroupedByTerminal(cs, index, terminal, charCount)) {
            return wcwidthForGraphemeCluster(cs, index, charCount);
        }
        if (isMultiCodepointEmoji(cs, index, charCount)) {
            // Multi-codepoint emoji cluster not grouped by terminal:
            // sum individual codepoint widths
            return wcwidthUngrouped(cs, index, charCount);
        }
        return wcwidth(Character.codePointAt(cs, index));
    }

    /**
     * Determine the number of Java char units that should be consumed for display starting at the given index, considering the terminal's emoji grouping support.
     *
     * @param cs the character sequence containing the cluster
     * @param index the char index within cs where the cluster begins
     * @param terminal the terminal whose emoji grouping preferences are consulted; may be null
     * @param charCount the full char count of the grapheme cluster beginning at index
     * @return the number of chars to consume for display: the full cluster charCount when the terminal groups the cluster or when the cluster is an emoji sequence, otherwise the char count of the single code point at index
     */
    private static int charCountForDisplayWithGroupings(CharSequence cs, int index, Terminal terminal, int charCount) {
        if (terminal == null || isGroupedByTerminal(cs, index, terminal, charCount)) {
            return charCount;
        }
        if (isMultiCodepointEmoji(cs, index, charCount)) {
            // Consume the entire cluster even when not grouped, so that
            // combining characters (skin tone modifiers, etc.) are not
            // processed separately with incorrect zero-width values
            return charCount;
        }
        return Character.charCount(Character.codePointAt(cs, index));
    }

    /**
     * Compute the display width in terminal character cells for a multi-codepoint cluster
     * when the terminal does not treat the cluster as a single grapheme.
     *
     * <p>The width is the sum of the display widths of the cluster's constituent code points.
     * Skin-tone modifiers and regional indicator symbols are treated as width 2 when not
     * grouped with a base character.</p>
     *
     * @param cs the character sequence containing the cluster
     * @param index the index (in Java chars) of the cluster's first char within {@code cs}
     * @param charCount the number of Java chars that make up the cluster
     * @return the total display width of the cluster in character cells
     */
    private static int wcwidthUngrouped(CharSequence cs, int index, int charCount) {
        int end = index + charCount;
        int totalWidth = 0;
        int pos = index;
        while (pos < end) {
            int cp = Character.codePointAt(cs, pos);
            if (isSkinToneModifier(cp) || isRegionalIndicator(cp)) {
                // These are in the combining table (wcwidth=0) but render
                // as width-2 emoji when not combined with a base character
                totalWidth += 2;
            } else {
                int w = wcwidth(cp);
                if (w > 0) {
                    totalWidth += w;
                }
            }
            pos += Character.charCount(cp);
        }
        return totalWidth;
    }

    /**
     * Tests whether the terminal groups the given cluster as a single unit.
     *
     * <p>Delegates to {@link AbstractTerminal#isClusterGrouped} when the
     * terminal is an AbstractTerminal instance; otherwise falls back to
     * {@link Terminal#getGraphemeClusterMode()}.</p>
     */
    private static boolean isGroupedByTerminal(CharSequence cs, int index, Terminal terminal, int charCount) {
        if (terminal instanceof AbstractTerminal) {
            return ((AbstractTerminal) terminal).isClusterGrouped(cs, index, charCount);
        }
        return terminal.getGraphemeClusterMode();
    }

    /**
     * Tests whether the grapheme cluster starting at {@code index} is a
     * multi-codepoint emoji sequence (as opposed to a single codepoint or a
     * non-emoji combining sequence).
     */
    private static boolean isMultiCodepointEmoji(CharSequence cs, int index, int charCount) {
        if (charCount <= Character.charCount(Character.codePointAt(cs, index))) {
            return false; // Single codepoint
        }
        int cp = Character.codePointAt(cs, index);
        return isRegionalIndicator(cp) || cp >= 0x2000;
    }

    /**
     * Determines whether a Unicode code point is a Regional Indicator Symbol (U+1F1E6..U+1F1FF).
     *
     * @param cp the Unicode code point to test
     * @return `true` if the code point is within U+1F1E6..U+1F1FF, `false` otherwise
     */
    public static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    /**
     * Determines whether the Unicode code point is an Emoji Modifier (skin tone) in the range U+1F3FB..U+1F3FF.
     *
     * @param cp the Unicode code point to test
     * @return true if the code point is an Emoji Modifier (skin tone) between U+1F3FB and U+1F3FF, false otherwise
     */
    private static boolean isSkinToneModifier(int cp) {
        return cp >= 0x1F3FB && cp <= 0x1F3FF;
    }

    /**
     * Determine whether a code point falls within any interval in a sorted Interval table.
     *
     * @param ucs   the Unicode code point to test
     * @param table a sorted array of Interval ranges
     * @param max   index of the last interval in {@code table} to consider (inclusive)
     * @return      {@code true} if an interval between {@code table[0]} and {@code table[max]} contains {@code ucs}, {@code false} otherwise
     */
    private static boolean bisearch(int ucs, Interval[] table, int max) {
        int min = 0;
        int mid;

        if (ucs < table[0].first || ucs > table[max].last) return false;
        while (max >= min) {
            mid = (min + max) / 2;
            if (ucs > table[mid].last) min = mid + 1;
            else if (ucs < table[mid].first) max = mid - 1;
            else return true;
        }

        return false;
    }
}
