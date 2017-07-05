/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.awt.font;

/**
 * The <code>NumericShaper</code> class is used to convert Latin-1 (European)
 * digits to other Unicode decimal digits.  Users of this class will
 * primarily be people who wish to present data using
 * national digit shapes, but find it more convenient to represent the
 * data internally using Latin-1 (European) digits.  This does not
 * interpret the deprecated numeric shape selector character (U+206E).
 * <p>
 * Instances of <code>NumericShaper</code> are typically applied
 * as attributes to text with the
 * {@link TextAttribute#NUMERIC_SHAPING NUMERIC_SHAPING} attribute
 * of the <code>TextAttribute</code> class.
 * For example, this code snippet causes a <code>TextLayout</code> to
 * shape European digits to Arabic in an Arabic context:<br>
 * <blockquote><pre>
 * Map map = new HashMap();
 * map.put(TextAttribute.NUMERIC_SHAPING,
 *     NumericShaper.getContextualShaper(NumericShaper.ARABIC));
 * FontRenderContext frc = ...;
 * TextLayout layout = new TextLayout(text, map, frc);
 * layout.draw(g2d, x, y);
 * </pre></blockquote>
 * <br>
 * It is also possible to perform numeric shaping explicitly using instances
 * of <code>NumericShaper</code>, as this code snippet demonstrates:<br>
 * <blockquote><pre>
 *   char[] text = ...;
 *   // shape all EUROPEAN digits (except zero) to ARABIC digits
 *   NumericShaper shaper = NumericShaper.getShaper(NumericShaper.ARABIC);
 *   shaper.shape(text, start, count);
 *
 *   // shape European digits to ARABIC digits if preceding text is Arabic, or
 *   // shape European digits to TAMIL digits if preceding text is Tamil, or
 *   // leave European digits alone if there is no preceding text, or
 *   // preceding text is neither Arabic nor Tamil
 *   NumericShaper shaper =
 *      NumericShaper.getContextualShaper(NumericShaper.ARABIC |
 *                                              NumericShaper.TAMIL,
 *                                              NumericShaper.EUROPEAN);
 *   shaper.shape(text. start, count);
 * </pre></blockquote>
 *
 * @since 1.4
 */

public final class NumericShaper implements java.io.Serializable {
    /** index of context for contextual shaping - values range from 0 to 18 */
    private int key;

    /** flag indicating whether to shape contextually (high bit) and which
     *  digit ranges to shape (bits 0-18)
     */
    private int mask;

    /** Identifies the Latin-1 (European) and extended range, and
     *  Latin-1 (European) decimal base.
     */
    public static final int EUROPEAN = 1<<0;

    /** Identifies the ARABIC range and decimal base. */
    public static final int ARABIC = 1<<1;

    /** Identifies the ARABIC range and ARABIC_EXTENDED decimal base. */
    public static final int EASTERN_ARABIC = 1<<2;

    /** Identifies the DEVANAGARI range and decimal base. */
    public static final int DEVANAGARI = 1<<3;

    /** Identifies the BENGALI range and decimal base. */
    public static final int BENGALI = 1<<4;

    /** Identifies the GURMUKHI range and decimal base. */
    public static final int GURMUKHI = 1<<5;

    /** Identifies the GUJARATI range and decimal base. */
    public static final int GUJARATI = 1<<6;

    /** Identifies the ORIYA range and decimal base. */
    public static final int ORIYA = 1<<7;

    /** Identifies the TAMIL range and decimal base. Tamil does not have a
     *  decimal digit 0 so Latin-1 (European) 0 is used.
     */
    public static final int TAMIL = 1<<8;

    /** Identifies the TELUGU range and decimal base. */
    public static final int TELUGU = 1<<9;

    /** Identifies the KANNADA range and decimal base. */
    public static final int KANNADA = 1<<10;

    /** Identifies the MALAYALAM range and decimal base. */
    public static final int MALAYALAM = 1<<11;

    /** Identifies the THAI range and decimal base. */
    public static final int THAI = 1<<12;

    /** Identifies the LAO range and decimal base. */
    public static final int LAO = 1<<13;

    /** Identifies the TIBETAN range and decimal base. */
    public static final int TIBETAN = 1<<14;

    /** Identifies the MYANMAR range and decimal base. */
    public static final int MYANMAR = 1<<15;

    /** Identifies the ETHIOPIC range and decimal base. */
    public static final int ETHIOPIC = 1<<16;

    /** Identifies the KHMER range and decimal base. */
    public static final int KHMER = 1<<17;

    /** Identifies the MONGOLIAN range and decimal base. */
    public static final int MONGOLIAN = 1<<18;

    /** Identifies all ranges, for full contextual shaping. */
    public static final int ALL_RANGES = 0x0007ffff;

    private static final int EUROPEAN_KEY = 0;
    private static final int ARABIC_KEY = 1;
    private static final int EASTERN_ARABIC_KEY = 2;
    private static final int DEVANAGARI_KEY = 3;
    private static final int BENGALI_KEY = 4;
    private static final int GURMUKHI_KEY = 5;
    private static final int GUJARATI_KEY = 6;
    private static final int ORIYA_KEY = 7;
    private static final int TAMIL_KEY = 8;
    private static final int TELUGU_KEY = 9;
    private static final int KANNADA_KEY = 10;
    private static final int MALAYALAM_KEY = 11;
    private static final int THAI_KEY = 12;
    private static final int LAO_KEY = 13;
    private static final int TIBETAN_KEY = 14;
    private static final int MYANMAR_KEY = 15;
    private static final int ETHIOPIC_KEY = 16;
    private static final int KHMER_KEY = 17;
    private static final int MONGOLIAN_KEY = 18;

    private static final int NUM_KEYS = 19;

    private static final String[] keyNames = {
        "EUROPEAN",
        "ARABIC",
        "EASTERN_ARABIC",
        "DEVANAGARI",
        "BENGALI",
        "GURMUKHI",
        "GUJARATI",
        "ORIYA",
        "TAMIL",
        "TELUGU",
        "KANNADA",
        "MALAYALAM",
        "THAI",
        "LAO",
        "TIBETAN",
        "MYANMAR",
        "ETHIOPIC",
        "KHMER",
        "MONGOLIAN"
    };

    private static final int CONTEXTUAL_MASK = 1<<31;

    private static final char[] bases = {
        '\u0030' - '\u0030', // EUROPEAN
        '\u0660' - '\u0030', // ARABIC
        '\u06f0' - '\u0030', // EASTERN_ARABIC
        '\u0966' - '\u0030', // DEVANAGARI
        '\u09e6' - '\u0030', // BENGALI
        '\u0a66' - '\u0030', // GURMUKHI
        '\u0ae6' - '\u0030', // GUJARATI
        '\u0b66' - '\u0030', // ORIYA
        '\u0be7' - '\u0030', // TAMIL - note missing zero
        '\u0c66' - '\u0030', // TELUGU
        '\u0ce6' - '\u0030', // KANNADA
        '\u0d66' - '\u0030', // MALAYALAM
        '\u0e50' - '\u0030', // THAI
        '\u0ed0' - '\u0030', // LAO
        '\u0f20' - '\u0030', // TIBETAN
        '\u1040' - '\u0030', // MYANMAR
        '\u1369' - '\u0030', // ETHIOPIC
        '\u17e0' - '\u0030', // KHMER
        '\u1810' - '\u0030', // MONGOLIAN
    };

    // some ranges adjoin or overlap, rethink if we want to do a binary search on this

    private static final char[] contexts = {
        '\u0000', '\u0300', // 'EUROPEAN' (really latin-1 and extended)
        '\u0600', '\u0700', // ARABIC
        '\u0600', '\u0700', // EASTERN_ARABIC -- note overlap with arabic
        '\u0900', '\u0980', // DEVANAGARI
        '\u0980', '\u0a00', // BENGALI
        '\u0a00', '\u0a80', // GURMUKHI
        '\u0a80', '\u0b00', // GUJARATI
        '\u0b00', '\u0b80', // ORIYA
        '\u0b80', '\u0c00', // TAMIL - note missing zero
        '\u0c00', '\u0c80', // TELUGU
        '\u0c80', '\u0d00', // KANNADA
        '\u0d00', '\u0d80', // MALAYALAM
        '\u0e00', '\u0e80', // THAI
        '\u0e80', '\u0f00', // LAO
        '\u0f00', '\u1000', // TIBETAN
        '\u1000', '\u1080', // MYANMAR
        '\u1200', '\u1380', // ETHIOPIC
        '\u1780', '\u1800', // KHMER
        '\u1800', '\u1900', // MONGOLIAN
        '\uffff',
    };

    // assume most characters are near each other so probing the cache is infrequent,
    // and a linear probe is ok.

    private static int ctCache = 0;
    private static int ctCacheLimit = contexts.length - 2;

    // warning, synchronize access to this as it modifies state
    private static int getContextKey(char c) {
        if (c < contexts[ctCache]) {
            while (ctCache > 0 && c < contexts[ctCache]) --ctCache;
        } else if (c >= contexts[ctCache + 1]) {
            while (ctCache < ctCacheLimit && c >= contexts[ctCache + 1]) ++ctCache;
        }

        // if we're not in a known range, then return EUROPEAN as the range key
        return (ctCache & 0x1) == 0 ? (ctCache / 2) : EUROPEAN_KEY;
    }

    /*
     * A range table of strong directional characters (types L, R, AL).
     * Even (left) indexes are starts of ranges of non-strong-directional (or undefined)
     * characters, odd (right) indexes are starts of ranges of strong directional
     * characters.
     */
    private static char[] strongTable = {
        '\u0000', '\u0041',
        '\u005b', '\u0061',
        '\u007b', '\u00aa',
        '\u00ab', '\u00b5',
        '\u00b6', '\u00ba',
        '\u00bb', '\u00c0',
        '\u00d7', '\u00d8',
        '\u00f7', '\u00f8',
        '\u0220', '\u0222',
        '\u0234', '\u0250',
        '\u02ae', '\u02b0',
        '\u02b9', '\u02bb',
        '\u02c2', '\u02d0',
        '\u02d2', '\u02e0',
        '\u02e5', '\u02ee',
        '\u02ef', '\u037a',
        '\u037b', '\u0386',
        '\u0387', '\u0388',
        '\u038b', '\u038c',
        '\u038d', '\u038e',
        '\u03a2', '\u03a3',
        '\u03cf', '\u03d0',
        '\u03d8', '\u03da',
        '\u03f4', '\u0400',
        '\u0483', '\u048c',
        '\u04c5', '\u04c7',
        '\u04c9', '\u04cb',
        '\u04cd', '\u04d0',
        '\u04f6', '\u04f8',
        '\u04fa', '\u0531',
        '\u0557', '\u0559',
        '\u0560', '\u0561',
        '\u0588', '\u0589',
        '\u058a', '\u05be',
        '\u05bf', '\u05c0',
        '\u05c1', '\u05c3',
        '\u05c4', '\u05d0',
        '\u05eb', '\u05f0',
        '\u05f5', '\u061b',
        '\u061c', '\u061f',
        '\u0620', '\u0621',
        '\u063b', '\u0640',
        '\u064b', '\u066d',
        '\u066e', '\u0671',
        '\u06d6', '\u06e5',
        '\u06e7', '\u06fa',
        '\u06ff', '\u0700',
        '\u070e', '\u0710',
        '\u0711', '\u0712',
        '\u072d', '\u0780',
        '\u07a6', '\u0903',
        '\u0904', '\u0905',
        '\u093a', '\u093d',
        '\u0941', '\u0949',
        '\u094d', '\u0950',
        '\u0951', '\u0958',
        '\u0962', '\u0964',
        '\u0971', '\u0982',
        '\u0984', '\u0985',
        '\u098d', '\u098f',
        '\u0991', '\u0993',
        '\u09a9', '\u09aa',
        '\u09b1', '\u09b2',
        '\u09b3', '\u09b6',
        '\u09ba', '\u09be',
        '\u09c1', '\u09c7',
        '\u09c9', '\u09cb',
        '\u09cd', '\u09d7',
        '\u09d8', '\u09dc',
        '\u09de', '\u09df',
        '\u09e2', '\u09e6',
        '\u09f2', '\u09f4',
        '\u09fb', '\u0a05',
        '\u0a0b', '\u0a0f',
        '\u0a11', '\u0a13',
        '\u0a29', '\u0a2a',
        '\u0a31', '\u0a32',
        '\u0a34', '\u0a35',
        '\u0a37', '\u0a38',
        '\u0a3a', '\u0a3e',
        '\u0a41', '\u0a59',
        '\u0a5d', '\u0a5e',
        '\u0a5f', '\u0a66',
        '\u0a70', '\u0a72',
        '\u0a75', '\u0a83',
        '\u0a84', '\u0a85',
        '\u0a8c', '\u0a8d',
        '\u0a8e', '\u0a8f',
        '\u0a92', '\u0a93',
        '\u0aa9', '\u0aaa',
        '\u0ab1', '\u0ab2',
        '\u0ab4', '\u0ab5',
        '\u0aba', '\u0abd',
        '\u0ac1', '\u0ac9',
        '\u0aca', '\u0acb',
        '\u0acd', '\u0ad0',
        '\u0ad1', '\u0ae0',
        '\u0ae1', '\u0ae6',
        '\u0af0', '\u0b02',
        '\u0b04', '\u0b05',
        '\u0b0d', '\u0b0f',
        '\u0b11', '\u0b13',
        '\u0b29', '\u0b2a',
        '\u0b31', '\u0b32',
        '\u0b34', '\u0b36',
        '\u0b3a', '\u0b3d',
        '\u0b3f', '\u0b40',
        '\u0b41', '\u0b47',
        '\u0b49', '\u0b4b',
        '\u0b4d', '\u0b57',
        '\u0b58', '\u0b5c',
        '\u0b5e', '\u0b5f',
        '\u0b62', '\u0b66',
        '\u0b71', '\u0b83',
        '\u0b84', '\u0b85',
        '\u0b8b', '\u0b8e',
        '\u0b91', '\u0b92',
        '\u0b96', '\u0b99',
        '\u0b9b', '\u0b9c',
        '\u0b9d', '\u0b9e',
        '\u0ba0', '\u0ba3',
        '\u0ba5', '\u0ba8',
        '\u0bab', '\u0bae',
        '\u0bb6', '\u0bb7',
        '\u0bba', '\u0bbe',
        '\u0bc0', '\u0bc1',
        '\u0bc3', '\u0bc6',
        '\u0bc9', '\u0bca',
        '\u0bcd', '\u0bd7',
        '\u0bd8', '\u0be7',
        '\u0bf3', '\u0c01',
        '\u0c04', '\u0c05',
        '\u0c0d', '\u0c0e',
        '\u0c11', '\u0c12',
        '\u0c29', '\u0c2a',
        '\u0c34', '\u0c35',
        '\u0c3a', '\u0c41',
        '\u0c45', '\u0c60',
        '\u0c62', '\u0c66',
        '\u0c70', '\u0c82',
        '\u0c84', '\u0c85',
        '\u0c8d', '\u0c8e',
        '\u0c91', '\u0c92',
        '\u0ca9', '\u0caa',
        '\u0cb4', '\u0cb5',
        '\u0cba', '\u0cbe',
        '\u0cbf', '\u0cc0',
        '\u0cc5', '\u0cc7',
        '\u0cc9', '\u0cca',
        '\u0ccc', '\u0cd5',
        '\u0cd7', '\u0cde',
        '\u0cdf', '\u0ce0',
        '\u0ce2', '\u0ce6',
        '\u0cf0', '\u0d02',
        '\u0d04', '\u0d05',
        '\u0d0d', '\u0d0e',
        '\u0d11', '\u0d12',
        '\u0d29', '\u0d2a',
        '\u0d3a', '\u0d3e',
        '\u0d41', '\u0d46',
        '\u0d49', '\u0d4a',
        '\u0d4d', '\u0d57',
        '\u0d58', '\u0d60',
        '\u0d62', '\u0d66',
        '\u0d70', '\u0d82',
        '\u0d84', '\u0d85',
        '\u0d97', '\u0d9a',
        '\u0db2', '\u0db3',
        '\u0dbc', '\u0dbd',
        '\u0dbe', '\u0dc0',
        '\u0dc7', '\u0dcf',
        '\u0dd2', '\u0dd8',
        '\u0de0', '\u0df2',
        '\u0df5', '\u0e01',
        '\u0e31', '\u0e32',
        '\u0e34', '\u0e40',
        '\u0e47', '\u0e4f',
        '\u0e5c', '\u0e81',
        '\u0e83', '\u0e84',
        '\u0e85', '\u0e87',
        '\u0e89', '\u0e8a',
        '\u0e8b', '\u0e8d',
        '\u0e8e', '\u0e94',
        '\u0e98', '\u0e99',
        '\u0ea0', '\u0ea1',
        '\u0ea4', '\u0ea5',
        '\u0ea6', '\u0ea7',
        '\u0ea8', '\u0eaa',
        '\u0eac', '\u0ead',
        '\u0eb1', '\u0eb2',
        '\u0eb4', '\u0ebd',
        '\u0ebe', '\u0ec0',
        '\u0ec5', '\u0ec6',
        '\u0ec7', '\u0ed0',
        '\u0eda', '\u0edc',
        '\u0ede', '\u0f00',
        '\u0f18', '\u0f1a',
        '\u0f35', '\u0f36',
        '\u0f37', '\u0f38',
        '\u0f39', '\u0f3e',
        '\u0f48', '\u0f49',
        '\u0f6b', '\u0f7f',
        '\u0f80', '\u0f85',
        '\u0f86', '\u0f88',
        '\u0f8c', '\u0fbe',
        '\u0fc6', '\u0fc7',
        '\u0fcd', '\u0fcf',
        '\u0fd0', '\u1000',
        '\u1022', '\u1023',
        '\u1028', '\u1029',
        '\u102b', '\u102c',
        '\u102d', '\u1031',
        '\u1032', '\u1038',
        '\u1039', '\u1040',
        '\u1058', '\u10a0',
        '\u10c6', '\u10d0',
        '\u10f7', '\u10fb',
        '\u10fc', '\u1100',
        '\u115a', '\u115f',
        '\u11a3', '\u11a8',
        '\u11fa', '\u1200',
        '\u1207', '\u1208',
        '\u1247', '\u1248',
        '\u1249', '\u124a',
        '\u124e', '\u1250',
        '\u1257', '\u1258',
        '\u1259', '\u125a',
        '\u125e', '\u1260',
        '\u1287', '\u1288',
        '\u1289', '\u128a',
        '\u128e', '\u1290',
        '\u12af', '\u12b0',
        '\u12b1', '\u12b2',
        '\u12b6', '\u12b8',
        '\u12bf', '\u12c0',
        '\u12c1', '\u12c2',
        '\u12c6', '\u12c8',
        '\u12cf', '\u12d0',
        '\u12d7', '\u12d8',
        '\u12ef', '\u12f0',
        '\u130f', '\u1310',
        '\u1311', '\u1312',
        '\u1316', '\u1318',
        '\u131f', '\u1320',
        '\u1347', '\u1348',
        '\u135b', '\u1361',
        '\u137d', '\u13a0',
        '\u13f5', '\u1401',
        '\u1677', '\u1681',
        '\u169b', '\u16a0',
        '\u16f1', '\u1780',
        '\u17b7', '\u17be',
        '\u17c6', '\u17c7',
        '\u17c9', '\u17d4',
        '\u17db', '\u17dc',
        '\u17dd', '\u17e0',
        '\u17ea', '\u1810',
        '\u181a', '\u1820',
        '\u1878', '\u1880',
        '\u18a9', '\u1e00',
        '\u1e9c', '\u1ea0',
        '\u1efa', '\u1f00',
        '\u1f16', '\u1f18',
        '\u1f1e', '\u1f20',
        '\u1f46', '\u1f48',
        '\u1f4e', '\u1f50',
        '\u1f58', '\u1f59',
        '\u1f5a', '\u1f5b',
        '\u1f5c', '\u1f5d',
        '\u1f5e', '\u1f5f',
        '\u1f7e', '\u1f80',
        '\u1fb5', '\u1fb6',
        '\u1fbd', '\u1fbe',
        '\u1fbf', '\u1fc2',
        '\u1fc5', '\u1fc6',
        '\u1fcd', '\u1fd0',
        '\u1fd4', '\u1fd6',
        '\u1fdc', '\u1fe0',
        '\u1fed', '\u1ff2',
        '\u1ff5', '\u1ff6',
        '\u1ffd', '\u200e',
        '\u2010', '\u207f',
        '\u2080', '\u2102',
        '\u2103', '\u2107',
        '\u2108', '\u210a',
        '\u2114', '\u2115',
        '\u2116', '\u2119',
        '\u211e', '\u2124',
        '\u2125', '\u2126',
        '\u2127', '\u2128',
        '\u2129', '\u212a',
        '\u212e', '\u212f',
        '\u2132', '\u2133',
        '\u213a', '\u2160',
        '\u2184', '\u2336',
        '\u237b', '\u2395',
        '\u2396', '\u249c',
        '\u24ea', '\u3005',
        '\u3008', '\u3021',
        '\u302a', '\u3031',
        '\u3036', '\u3038',
        '\u303b', '\u3041',
        '\u3095', '\u309d',
        '\u309f', '\u30a1',
        '\u30fb', '\u30fc',
        '\u30ff', '\u3105',
        '\u312d', '\u3131',
        '\u318f', '\u3190',
        '\u31b8', '\u3200',
        '\u321d', '\u3220',
        '\u3244', '\u3260',
        '\u327c', '\u327f',
        '\u32b1', '\u32c0',
        '\u32cc', '\u32d0',
        '\u32ff', '\u3300',
        '\u3377', '\u337b',
        '\u33de', '\u33e0',
        '\u33ff', '\u3400',
        '\u4db6', '\u4e00',
        '\u9fa6', '\ua000',
        '\ua48d', '\uac00',
        '\ud7a4', '\uf900',
        '\ufa2e', '\ufb00',
        '\ufb07', '\ufb13',
        '\ufb18', '\ufb1d',
        '\ufb1e', '\ufb1f',
        '\ufb29', '\ufb2a',
        '\ufb37', '\ufb38',
        '\ufb3d', '\ufb3e',
        '\ufb3f', '\ufb40',
        '\ufb42', '\ufb43',
        '\ufb45', '\ufb46',
        '\ufbb2', '\ufbd3',
        '\ufd3e', '\ufd50',
        '\ufd90', '\ufd92',
        '\ufdc8', '\ufdf0',
        '\ufdfc', '\ufe70',
        '\ufe73', '\ufe74',
        '\ufe75', '\ufe76',
        '\ufefd', '\uff21',
        '\uff3b', '\uff41',
        '\uff5b', '\uff66',
        '\uffbf', '\uffc2',
        '\uffc8', '\uffca',
        '\uffd0', '\uffd2',
        '\uffd8', '\uffda',
        '\uffdd', '\uffff' // last entry is sentinel, actually never checked
    };


    // use a binary search with a cache

    private static int stCache = 0;

    // warning, synchronize access to this as it modifies state
    private static boolean isStrongDirectional(char c) {
        if (c < strongTable[stCache]) {
            stCache = search(c, strongTable, 0, stCache);
        } else if (c >= strongTable[stCache + 1]) {
            stCache = search(c, strongTable, stCache + 1, strongTable.length - stCache - 1);
        }
        return (stCache & 0x1) == 1;
    }

    static private int getKeyFromMask(int mask) {
        int key = 0;
        while (key < NUM_KEYS && ((mask & (1<<key)) == 0)) {
            ++key;
        }
        if (key == NUM_KEYS || ((mask & ~(1<<key)) != 0)) {
            throw new IllegalArgumentException("invalid shaper: " + Integer.toHexString(mask));
        }
        return key;
    }

    /**
     * Returns a shaper for the provided unicode range.  All
     * Latin-1 (EUROPEAN) digits are converted
     * to the corresponding decimal unicode digits.
     * @param singleRange the specified Unicode range
     * @return a non-contextual numeric shaper
     * @throws IllegalArgumentException if the range is not a single range
     */
    static public NumericShaper getShaper(int singleRange) {
        int key = getKeyFromMask(singleRange);
        return new NumericShaper(key, singleRange);
    }

    /**
     * Returns a contextual shaper for the provided unicode range(s).
     * Latin-1 (EUROPEAN) digits are converted to the decimal digits
     * corresponding to the range of the preceding text, if the
     * range is one of the provided ranges.  Multiple ranges are
     * represented by or-ing the values together, such as,
     * <code>NumericShaper.ARABIC | NumericShaper.THAI</code>.  The
     * shaper assumes EUROPEAN as the starting context, that is, if
     * EUROPEAN digits are encountered before any strong directional
     * text in the string, the context is presumed to be EUROPEAN, and
     * so the digits will not shape.
     * @param ranges the specified Unicode ranges
     * @return a shaper for the specified ranges
     */
    static public NumericShaper getContextualShaper(int ranges) {
        ranges |= CONTEXTUAL_MASK;
        return new NumericShaper(EUROPEAN_KEY, ranges);
    }

    /**
     * Returns a contextual shaper for the provided unicode range(s).
     * Latin-1 (EUROPEAN) digits will be converted to the decimal digits
     * corresponding to the range of the preceding text, if the
     * range is one of the provided ranges.  Multiple ranges are
     * represented by or-ing the values together, for example,
     * <code>NumericShaper.ARABIC | NumericShaper.THAI</code>.  The
     * shaper uses defaultContext as the starting context.
     * @param ranges the specified Unicode ranges
     * @param defaultContext the starting context, such as
     * <code>NumericShaper.EUROPEAN</code>
     * @return a shaper for the specified Unicode ranges.
     * @throws IllegalArgumentException if the specified
     * <code>defaultContext</code> is not a single valid range.
     */
    static public NumericShaper getContextualShaper(int ranges, int defaultContext) {
        int key = getKeyFromMask(defaultContext);
        ranges |= CONTEXTUAL_MASK;
        return new NumericShaper(key, ranges);
    }

    /**
     * Private constructor.
     */
    private NumericShaper(int key, int mask) {
        this.key = key;
        this.mask = mask;
    }

    /**
     * Converts the digits in the text that occur between start and
     * start + count.
     * @param text an array of characters to convert
     * @param start the index into <code>text</code> to start
     *        converting
     * @param count the number of characters in <code>text</code>
     *        to convert
     * @throws IndexOutOfBoundsException if start or start + count is
     *        out of bounds
     * @throws NullPointerException if text is null
     */
    public void shape(char[] text, int start, int count) {
        if (text == null) {
            throw new NullPointerException("text is null");
        }
        if ((start < 0)
            || (start > text.length)
            || ((start + count) < 0)
            || ((start + count) > text.length)) {
            throw new IndexOutOfBoundsException(
                "bad start or count for text of length " + text.length);
        }

        if (isContextual()) {
            shapeContextually(text, start, count, key);
        } else {
            shapeNonContextually(text, start, count);
        }
    }

    /**
     * Converts the digits in the text that occur between start and
     * start + count, using the provided context.
     * Context is ignored if the shaper is not a contextual shaper.
     * @param text an array of characters
     * @param start the index into <code>text</code> to start
     *        converting
     * @param count the number of characters in <code>text</code>
     *        to convert
     * @param context the context to which to convert the
     *        characters, such as <code>NumericShaper.EUROPEAN</code>
     * @throws IndexOutOfBoundsException if start or start + count is
     *        out of bounds
     * @throws NullPointerException if text is null
     * @throws IllegalArgumentException if this is a contextual shaper
     * and the specified <code>context</code> is not a single valid
     * range.
     */
    public void shape(char[] text, int start, int count, int context) {
        if (text == null) {
            throw new NullPointerException("text is null");
        }
        if ((start < 0)
            || (start > text.length)
            || ((start + count) < 0)
            || ((start + count) > text.length)) {
            throw new IndexOutOfBoundsException(
                "bad start or count for text of length " + text.length);
        }

        if (isContextual()) {
            int ctxKey = getKeyFromMask(context);
            shapeContextually(text, start, count, ctxKey);
        } else {
            shapeNonContextually(text, start, count);
        }
    }

    /**
     * Returns a <code>boolean</code> indicating whether or not
     * this shaper shapes contextually.
     * @return <code>true</code> if this shaper is contextual;
     *         <code>false</code> otherwise.
     */
    public boolean isContextual() {
        return (mask & CONTEXTUAL_MASK) != 0;
    }

    /**
     * Returns an <code>int</code> that ORs together the values for
     * all the ranges that will be shaped.
     * <p>
     * For example, to check if a shaper shapes to Arabic, you would use the
     * following:
     * <blockquote>
     *   <code>if ((shaper.getRanges() & shaper.ARABIC) != 0) { ... </code>
     * </blockquote>
     * @return the values for all the ranges to be shaped.
     */
    public int getRanges() {
        return mask & ~CONTEXTUAL_MASK;
    }

    /**
     * Perform non-contextual shaping.
     */
    private void shapeNonContextually(char[] text, int start, int count) {
        int base = bases[key];
        char minDigit = key == TAMIL_KEY ? '\u0031' : '\u0030'; // Tamil doesn't use decimal zero
        for (int i = start, e = start + count; i < e; ++i) {
            char c = text[i];
            if (c >= minDigit && c <= '\u0039') {
                text[i] = (char)(c + base);
            }
        }
    }

    /**
     * Perform contextual shaping.
     * Synchronized to protect caches used in getContextKey and isStrongDirectional.
     */
    private synchronized void shapeContextually(char[] text, int start, int count, int ctxKey) {

        // if we don't support this context, then don't shape
        if ((mask & (1<<ctxKey)) == 0) {
            ctxKey = EUROPEAN_KEY;
        }
        int lastkey = ctxKey;

        int base = bases[ctxKey];
        char minDigit = ctxKey == TAMIL_KEY ? '\u0031' : '\u0030'; // Tamil doesn't use decimal zero

        for (int i = start, e = start + count; i < e; ++i) {
            char c = text[i];
            if (c >= minDigit && c <= '\u0039') {
                text[i] = (char)(c + base);
            }

            if (isStrongDirectional(c)) {
                int newkey = getContextKey(c);
                if (newkey != lastkey) {
                    lastkey = newkey;

                    ctxKey = newkey;
                    if (((mask & EASTERN_ARABIC) != 0) && (ctxKey == ARABIC_KEY || ctxKey == EASTERN_ARABIC_KEY)) {
                        ctxKey = EASTERN_ARABIC_KEY;
                    } else if ((mask & (1<<ctxKey)) == 0) {
                        ctxKey = EUROPEAN_KEY;
                    }

                    base = bases[ctxKey];

                    minDigit = ctxKey == TAMIL_KEY ? '\u0031' : '\u0030'; // Tamil doesn't use decimal zero
                }
            }
        }
    }

    /**
     * Returns a hash code for this shaper.
     * @return this shaper's hash code.
     * @see java.lang.Object#hashCode
     */
    public int hashCode() {
        return mask;
    }

    /**
     * Returns true if the specified object is an instance of
     * <code>NumericShaper</code> and shapes identically to this one.
     * @param o the specified object to compare to this
     *          <code>NumericShaper</code>
     * @return <code>true</code> if <code>o</code> is an instance
     *         of <code>NumericShaper</code> and shapes in the same way;
     *         <code>false</code> otherwise.
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object o) {
        if (o != null) {
            try {
                NumericShaper rhs = (NumericShaper)o;
                return rhs.mask == mask && rhs.key == key;
            }
            catch (ClassCastException e) {
            }
        }
        return false;
    }

    /**
     * Returns a <code>String</code> that describes this shaper. This method
     * is used for debugging purposes only.
     * @return a <code>String</code> describing this shaper.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());

        buf.append("[contextual:" + isContextual());

        if (isContextual()) {
            buf.append(", context:" + keyNames[key]);
        }

        buf.append(", range(s): ");
        boolean first = true;
        for (int i = 0; i < NUM_KEYS; ++i) {
            if ((mask & (1 << i)) != 0) {
                if (first) {
                    first = false;
                } else {
                    buf.append(", ");
                }
                buf.append(keyNames[i]);
            }
        }
        buf.append(']');

        return buf.toString();
    }

    /**
     * Returns the index of the high bit in value (assuming le, actually
     * power of 2 >= value). value must be positive.
     */
    private static int getHighBit(int value) {
        if (value <= 0) {
            return -32;
        }

        int bit = 0;

        if (value >= 1 << 16) {
            value >>= 16;
            bit += 16;
        }

        if (value >= 1 << 8) {
            value >>= 8;
            bit += 8;
        }

        if (value >= 1 << 4) {
            value >>= 4;
            bit += 4;
        }

        if (value >= 1 << 2) {
            value >>= 2;
            bit += 2;
        }

        if (value >= 1 << 1) {
            value >>= 1;
            bit += 1;
        }

        return bit;
    }

    /**
     * fast binary search over subrange of array.
     */
    private static int search(char value, char[] array, int start, int length)
    {
        int power = 1 << getHighBit(length);
        int extra = length - power;
        int probe = power;
        int index = start;

        if (value >= array[index + extra]) {
            index += extra;
        }

        while (probe > 1) {
            probe >>= 1;

            if (value >= array[index + probe]) {
                index += probe;
            }
        }

        return index;
    }
}
