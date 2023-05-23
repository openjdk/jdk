// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
*   Copyright (C) 2011, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*   created on: 2011feb25
*   created by: Markus W. Scherer
*/

package jdk.internal.icu.impl;

/**
 * Implements the immutable Unicode properties Pattern_Syntax and Pattern_White_Space.
 * Hardcodes these properties, does not load data, does not depend on other ICU classes.
 * <p>
 * Note: Both properties include ASCII as well as non-ASCII, non-Latin-1 code points,
 * and both properties only include BMP code points (no supplementary ones).
 * Pattern_Syntax includes some unassigned code points.
 * <p>
 * [:Pattern_White_Space:] =
 *   [\u0009-\u000D\ \u0020\u0085\u200E\u200F\u2028\u2029]
 * <p>
 * [:Pattern_Syntax:] =
 *   [!-/\:-@\[-\^`\{-~\u00A1-\u00A7\u00A9\u00AB\u00AC\u00AE
 *    \u00B0\u00B1\u00B6\u00BB\u00BF\u00D7\u00F7
 *    \u2010-\u2027\u2030-\u203E\u2041-\u2053\u2055-\u205E
 *    \u2190-\u245F\u2500-\u2775\u2794-\u2BFF\u2E00-\u2E7F
 *    \u3001-\u3003\u3008-\u3020\u3030\uFD3E\uFD3F\uFE45\uFE46]
 * @author mscherer
 */
public final class PatternProps {
    /**
     * @return true if c is a Pattern_Syntax code point.
     */
    public static boolean isSyntax(int c) {
        if(c<0) {
            return false;
        } else if(c<=0xff) {
            return latin1[c]==3;
        } else if(c<0x2010) {
            return false;
        } else if(c<=0x3030) {
            int bits=syntax2000[index2000[(c-0x2000)>>5]];
            return ((bits>>(c&0x1f))&1)!=0;
        } else if(0xfd3e<=c && c<=0xfe46) {
            return c<=0xfd3f || 0xfe45<=c;
        } else {
            return false;
        }
    }

    /**
     * @return true if c is a Pattern_Syntax or Pattern_White_Space code point.
     */
    public static boolean isSyntaxOrWhiteSpace(int c) {
        if(c<0) {
            return false;
        } else if(c<=0xff) {
            return latin1[c]!=0;
        } else if(c<0x200e) {
            return false;
        } else if(c<=0x3030) {
            int bits=syntaxOrWhiteSpace2000[index2000[(c-0x2000)>>5]];
            return ((bits>>(c&0x1f))&1)!=0;
        } else if(0xfd3e<=c && c<=0xfe46) {
            return c<=0xfd3f || 0xfe45<=c;
        } else {
            return false;
        }
    }

    /**
     * @return true if c is a Pattern_White_Space character.
     */
    public static boolean isWhiteSpace(int c) {
        if(c<0) {
            return false;
        } else if(c<=0xff) {
            return latin1[c]==5;
        } else if(0x200e<=c && c<=0x2029) {
            return c<=0x200f || 0x2028<=c;
        } else {
            return false;
        }
    }

    /**
     * Skips over Pattern_White_Space starting at index i of the CharSequence.
     * @return The smallest index at or after i with a non-white space character.
     */
    public static int skipWhiteSpace(CharSequence s, int i) {
        while(i<s.length() && isWhiteSpace(s.charAt(i))) {
            ++i;
        }
        return i;
    }

    /**
     * @return s except with leading and trailing Pattern_White_Space removed.
     */
    public static String trimWhiteSpace(String s) {
        if(s.length()==0 || (!isWhiteSpace(s.charAt(0)) && !isWhiteSpace(s.charAt(s.length()-1)))) {
            return s;
        }
        int start=0;
        int limit=s.length();
        while(start<limit && isWhiteSpace(s.charAt(start))) {
            ++start;
        }
        if(start<limit) {
            // There is non-white space at start; we will not move limit below that,
            // so we need not test start<limit in the loop.
            while(isWhiteSpace(s.charAt(limit-1))) {
                --limit;
            }
        }
        return s.substring(start, limit);
    }

    /**
     * @return s except with leading and trailing SpaceChar characters removed.
     */
    public static String trimSpaceChar(String s) {
        if (s.length() == 0 ||
            (!Character.isSpaceChar(s.charAt(0)) && !Character.isSpaceChar(s.charAt(s.length() - 1)))) {
            return s;
        }
        int start = 0;
        int limit = s.length();
        while (start < limit && Character.isSpaceChar(s.charAt(start))) {
            ++start;
        }
        if (start < limit) {
            // There is non-SpaceChar at start; we will not move limit below that,
            // so we need not test start<limit in the loop.
            while (isWhiteSpace(s.charAt(limit - 1))) {
                --limit;
            }
        }
        return s.substring(start, limit);
    }

    /**
     * Tests whether the CharSequence contains a "pattern identifier", that is,
     * whether it contains only non-Pattern_White_Space, non-Pattern_Syntax characters.
     * @return true if there are no Pattern_White_Space or Pattern_Syntax characters in s.
     */
    public static boolean isIdentifier(CharSequence s) {
        int limit=s.length();
        if(limit==0) {
            return false;
        }
        int start=0;
        do {
            if(isSyntaxOrWhiteSpace(s.charAt(start++))) {
                return false;
            }
        } while(start<limit);
        return true;
    }

    /**
     * Tests whether the CharSequence contains a "pattern identifier", that is,
     * whether it contains only non-Pattern_White_Space, non-Pattern_Syntax characters.
     * @return true if there are no Pattern_White_Space or Pattern_Syntax characters
     *         in s between start and (exclusive) limit.
     */
    public static boolean isIdentifier(CharSequence s, int start, int limit) {
        if(start>=limit) {
            return false;
        }
        do {
            if(isSyntaxOrWhiteSpace(s.charAt(start++))) {
                return false;
            }
        } while(start<limit);
        return true;
    }

    /**
     * Skips over a "pattern identifier" starting at index i of the CharSequence.
     * @return The smallest index at or after i with
     *         a Pattern_White_Space or Pattern_Syntax character.
     */
    public static int skipIdentifier(CharSequence s, int i) {
        while(i<s.length() && !isSyntaxOrWhiteSpace(s.charAt(i))) {
            ++i;
        }
        return i;
    }

    /*
     * One byte per Latin-1 character.
     * Bit 0 is set if either Pattern property is true,
     * bit 1 if Pattern_Syntax is true,
     * bit 2 if Pattern_White_Space is true.
     * That is, Pattern_Syntax is encoded as 3 and Pattern_White_Space as 5.
     */
    private static final byte latin1[]=new byte[] {  // 256
        // WS: 9..D
        0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 5, 5, 5, 5, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        // WS: 20  Syntax: 21..2F
        5, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
        // Syntax: 3A..40
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3,
        3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        // Syntax: 5B..5E
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 0,
        // Syntax: 60
        3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        // Syntax: 7B..7E
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 0,
        // WS: 85
        0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        // Syntax: A1..A7, A9, AB, AC, AE
        0, 3, 3, 3, 3, 3, 3, 3, 0, 3, 0, 3, 3, 0, 3, 0,
        // Syntax: B0, B1, B6, BB, BF
        3, 3, 0, 0, 0, 0, 3, 0, 0, 0, 0, 3, 0, 0, 0, 3,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        // Syntax: D7
        0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        // Syntax: F7
        0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0
    };

    /*
     * One byte per 32 characters from U+2000..U+303F indexing into
     * a small table of 32-bit data words.
     * The first two data words are all-zeros and all-ones.
     */
    private static final byte index2000[]=new byte[] {  // 130
        2, 3, 4, 0, 0, 0, 0, 0,  // 20xx
        0, 0, 0, 0, 5, 1, 1, 1,  // 21xx
        1, 1, 1, 1, 1, 1, 1, 1,  // 22xx
        1, 1, 1, 1, 1, 1, 1, 1,  // 23xx
        1, 1, 1, 0, 0, 0, 0, 0,  // 24xx
        1, 1, 1, 1, 1, 1, 1, 1,  // 25xx
        1, 1, 1, 1, 1, 1, 1, 1,  // 26xx
        1, 1, 1, 6, 7, 1, 1, 1,  // 27xx
        1, 1, 1, 1, 1, 1, 1, 1,  // 28xx
        1, 1, 1, 1, 1, 1, 1, 1,  // 29xx
        1, 1, 1, 1, 1, 1, 1, 1,  // 2Axx
        1, 1, 1, 1, 1, 1, 1, 1,  // 2Bxx
        0, 0, 0, 0, 0, 0, 0, 0,  // 2Cxx
        0, 0, 0, 0, 0, 0, 0, 0,  // 2Dxx
        1, 1, 1, 1, 0, 0, 0, 0,  // 2Exx
        0, 0, 0, 0, 0, 0, 0, 0,  // 2Fxx
        8, 9  // 3000..303F
    };

    /*
     * One 32-bit integer per 32 characters. Ranges of all-false and all-true
     * are mapped to the first two values, other ranges map to appropriate bit patterns.
     */
    private static final int syntax2000[]=new int[] {
        0,
        -1,
        0xffff0000,  // 2: 2010..201F
        0x7fff00ff,  // 3: 2020..2027, 2030..203E
        0x7feffffe,  // 4: 2041..2053, 2055..205E
        0xffff0000,  // 5: 2190..219F
        0x003fffff,  // 6: 2760..2775
        0xfff00000,  // 7: 2794..279F
        0xffffff0e,  // 8: 3001..3003, 3008..301F
        0x00010001   // 9: 3020, 3030
    };

    /*
     * Same as syntax2000, but with additional bits set for the
     * Pattern_White_Space characters 200E 200F 2028 2029.
     */
    private static final int syntaxOrWhiteSpace2000[]=new int[] {
        0,
        -1,
        0xffffc000,  // 2: 200E..201F
        0x7fff03ff,  // 3: 2020..2029, 2030..203E
        0x7feffffe,  // 4: 2041..2053, 2055..205E
        0xffff0000,  // 5: 2190..219F
        0x003fffff,  // 6: 2760..2775
        0xfff00000,  // 7: 2794..279F
        0xffffff0e,  // 8: 3001..3003, 3008..301F
        0x00010001   // 9: 3020, 3030
    };
}
