/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */



package com.sun.xml.internal.fastinfoset;

public class DecoderStateTables {
    private static int RANGE_INDEX_END      = 0;
    private static int RANGE_INDEX_VALUE    = 1;

    public final static int STATE_ILLEGAL                   = 255;
    public final static int STATE_UNSUPPORTED               = 254;

    // EII child states
    public final static int EII_NO_AIIS_INDEX_SMALL         = 0;
    public final static int EII_AIIS_INDEX_SMALL            = 1;
    public final static int EII_INDEX_MEDIUM                = 2;
    public final static int EII_INDEX_LARGE                 = 3;
    public final static int EII_NAMESPACES                  = 4;
    public final static int EII_LITERAL                     = 5;
    public final static int CII_UTF8_SMALL_LENGTH           = 6;
    public final static int CII_UTF8_MEDIUM_LENGTH          = 7;
    public final static int CII_UTF8_LARGE_LENGTH           = 8;
    public final static int CII_UTF16_SMALL_LENGTH          = 9;
    public final static int CII_UTF16_MEDIUM_LENGTH         = 10;
    public final static int CII_UTF16_LARGE_LENGTH          = 11;
    public final static int CII_RA                          = 12;
    public final static int CII_EA                          = 13;
    public final static int CII_INDEX_SMALL                 = 14;
    public final static int CII_INDEX_MEDIUM                = 15;
    public final static int CII_INDEX_LARGE                 = 16;
    public final static int CII_INDEX_LARGE_LARGE           = 17;
    public final static int COMMENT_II                      = 18;
    public final static int PROCESSING_INSTRUCTION_II       = 19;
    public final static int DOCUMENT_TYPE_DECLARATION_II    = 20;
    public final static int UNEXPANDED_ENTITY_REFERENCE_II  = 21;
    public final static int TERMINATOR_SINGLE               = 22;
    public final static int TERMINATOR_DOUBLE               = 23;

    private static final int[] DII = new int[256];

    private static final int[][] DII_RANGES = {
        // EII

        // %00000000 to %00011111  EII no attributes small index
        { 0x1F, EII_NO_AIIS_INDEX_SMALL },

        // %00100000 to %00100111  EII medium index
        { 0x27, EII_INDEX_MEDIUM },

        // %00101000 to %00101111  EII large index
        // %00110000  EII very large index
        // %00101000 to %00110000
        { 0x30, EII_INDEX_LARGE },

        // %00110001 to %00110111  ILLEGAL
        { 0x37, STATE_ILLEGAL },

        // %00111000  EII namespaces
        { 0x38, EII_NAMESPACES },

        // %00111001 to %00111011  ILLEGAL
        { 0x3B, STATE_ILLEGAL },

        // %00111100  EII literal (no prefix, no namespace)
        { 0x3C, EII_LITERAL },

        // %00111101  EII literal (no prefix, namespace)
        { 0x3D, EII_LITERAL },

        // %00111110  ILLEGAL
        { 0x3E, STATE_ILLEGAL },

        // %00111111  EII literal (prefix, namespace)
        { 0x3F, EII_LITERAL },

        // %01000000 to %01011111  EII attributes small index
        { 0x5F, EII_AIIS_INDEX_SMALL },

        // %01100000 to %01100111  EII medium index
        { 0x67, EII_INDEX_MEDIUM },

        // %01101000 to %01101111  EII large index
        // %01110000  EII very large index
        // %01101000 to %01110000
        { 0x70, EII_INDEX_LARGE },

        // %01110001 to %01110111  ILLEGAL
        { 0x77, STATE_ILLEGAL },

        // %01111000  EII attributes namespaces
        { 0x78, EII_NAMESPACES },

        // %01111001 to %01111011  ILLEGAL
        { 0x7B, STATE_ILLEGAL },

        // %01111100  EII attributes literal (no prefix, no namespace)
        { 0x7C, EII_LITERAL },

        // %01111101  EII attributes literal (no prefix, namespace)
        { 0x7D, EII_LITERAL },

        // %01111110  ILLEGAL
        { 0x7E, STATE_ILLEGAL },

        // %01111111  EII attributes literal (prefix, namespace)
        { 0x7F, EII_LITERAL },

        // %10000000 to %11000011
        { 0xC3, STATE_ILLEGAL },

        // %11000100 to %11000111
        { 0xC7, DOCUMENT_TYPE_DECLARATION_II },

        // %11001000 to %1110000
        { 0xE0, STATE_ILLEGAL },

        // %11100001 processing instruction
        { 0xE1, PROCESSING_INSTRUCTION_II },

        // %11100010 comment
        { 0xE2, COMMENT_II},

        // %111000011 to %11101111
        { 0xEF, STATE_ILLEGAL },

        // Terminators

        // %11110000  single terminator
        { 0xF0, TERMINATOR_SINGLE },

        // %11110000 to %11111110 ILLEGAL
        { 0xFE, STATE_ILLEGAL },

        // %11111111  double terminator
        { 0xFF, TERMINATOR_DOUBLE }
    };

    private static final int[] EII = new int[256];

    private static final int[][] EII_RANGES = {
        // EII

        // %00000000 to %00011111  EII no attributes small index
        { 0x1F, EII_NO_AIIS_INDEX_SMALL },

        // %00100000 to %00100111  EII medium index
        { 0x27, EII_INDEX_MEDIUM },

        // %00101000 to %00101111  EII large index
        // %00110000  EII very large index
        // %00101000 to %00110000
        { 0x30, EII_INDEX_LARGE },

        // %00110001 to %00110111  ILLEGAL
        { 0x37, STATE_ILLEGAL },

        // %00111000  EII namespaces
        { 0x38, EII_NAMESPACES },

        // %00111001 to %00111011  ILLEGAL
        { 0x3B, STATE_ILLEGAL },

        // %00111100  EII literal (no prefix, no namespace)
        { 0x3C, EII_LITERAL },

        // %00111101  EII literal (no prefix, namespace)
        { 0x3D, EII_LITERAL },

        // %00111110  ILLEGAL
        { 0x3E, STATE_ILLEGAL },

        // %00111111  EII literal (prefix, namespace)
        { 0x3F, EII_LITERAL },

        // %01000000 to %01011111  EII attributes small index
        { 0x5F, EII_AIIS_INDEX_SMALL },

        // %01100000 to %01100111  EII medium index
        { 0x67, EII_INDEX_MEDIUM },

        // %01101000 to %01101111  EII large index
        // %01110000  EII very large index
        // %01101000 to %01110000
        { 0x70, EII_INDEX_LARGE },

        // %01110001 to %01110111  ILLEGAL
        { 0x77, STATE_ILLEGAL },

        // %01111000  EII attributes namespaces
        { 0x78, EII_NAMESPACES },

        // %01111001 to %01111011  ILLEGAL
        { 0x7B, STATE_ILLEGAL },

        // %01111100  EII attributes literal (no prefix, no namespace)
        { 0x7C, EII_LITERAL },

        // %01111101  EII attributes literal (no prefix, namespace)
        { 0x7D, EII_LITERAL },

        // %01111110  ILLEGAL
        { 0x7E, STATE_ILLEGAL },

        // %01111111  EII attributes literal (prefix, namespace)
        { 0x7F, EII_LITERAL },

        // CII

        // UTF-8 string

        // %10000000 to %10000001  CII UTF-8 no add to table small length
        { 0x81, CII_UTF8_SMALL_LENGTH },

        // %10000010  CII UTF-8 no add to table medium length
        { 0x82, CII_UTF8_MEDIUM_LENGTH },

        // %10000011  CII UTF-8 no add to table large length
        { 0x83, CII_UTF8_LARGE_LENGTH },

        // UTF-16 string

        // %10000100 to %10000101  CII UTF-16 no add to table small length
        { 0x85, CII_UTF16_SMALL_LENGTH },

        // %10000110  CII UTF-16 no add to table medium length
        { 0x86, CII_UTF16_MEDIUM_LENGTH },

        // %10000111  CII UTF-16 no add to table large length
        { 0x87, CII_UTF16_LARGE_LENGTH },

        // Resitricted alphabet

        // %10001000 to %10001011  CII RA no add to table
        { 0x8B, CII_RA },

        // Encoding algorithm

        // %10001100 to %10001111  CII EA no add to table
        { 0x8F, CII_EA },

        // UTF-8 string, add to table

        // %10010000 to %10010001  CII add to table small length
        { 0x91, CII_UTF8_SMALL_LENGTH },

        // %10010010  CII add to table medium length
        { 0x92, CII_UTF8_MEDIUM_LENGTH },

        // %10010011  CII add to table large length
        { 0x93, CII_UTF8_LARGE_LENGTH },

        // UTF-16 string, add to table

        // %10010100 to %10010101  CII UTF-16 add to table small length
        { 0x95, CII_UTF16_SMALL_LENGTH },

        // %10010110  CII UTF-16 add to table medium length
        { 0x96, CII_UTF16_MEDIUM_LENGTH },

        // %10010111  CII UTF-16 add to table large length
        { 0x97, CII_UTF16_LARGE_LENGTH },

        // Restricted alphabet, add to table

        // %10011000 to %10011011  CII RA add to table
        { 0x9B, CII_RA },

        // Encoding algorithm, add to table

        // %10011100 to %10011111  CII EA add to table
        { 0x9F, CII_EA },

        // Index

        // %10100000 to %10101111  CII small index
        { 0xAF, CII_INDEX_SMALL },

        // %10110000 to %10110011  CII medium index
        { 0xB3, CII_INDEX_MEDIUM },

        // %10110100 to %10110111  CII large index
        { 0xB7, CII_INDEX_LARGE },

        // %10111000  CII very large index
        { 0xB8, CII_INDEX_LARGE_LARGE },

        // %10111001 to %11000111  ILLEGAL
        { 0xC7, STATE_ILLEGAL },

        // %11001000 to %11001011
        { 0xCB, UNEXPANDED_ENTITY_REFERENCE_II },

        // %11001100 to %11100000  ILLEGAL
        { 0xE0, STATE_ILLEGAL },

        // %11100001 processing instruction
        { 0xE1, PROCESSING_INSTRUCTION_II },

        // %11100010 comment
        { 0xE2, COMMENT_II},

        // %111000011 to %11101111
        { 0xEF, STATE_ILLEGAL },

        // Terminators

        // %11110000  single terminator
        { 0xF0, TERMINATOR_SINGLE },

        // %11110000 to %11111110 ILLEGAL
        { 0xFE, STATE_ILLEGAL },

        // %11111111  double terminator
        { 0xFF, TERMINATOR_DOUBLE }
    };


    // AII states
    public final static int AII_INDEX_SMALL                 = 0;
    public final static int AII_INDEX_MEDIUM                = 1;
    public final static int AII_INDEX_LARGE                 = 2;
    public final static int AII_LITERAL                     = 3;
    public final static int AII_TERMINATOR_SINGLE           = 4;
    public final static int AII_TERMINATOR_DOUBLE           = 5;

    private static final int[] AII = new int[256];

    private static final int[][] AII_RANGES = {
        // %00000000 to %00111111  AII small index
        { 0x3F, AII_INDEX_SMALL },

        // %01000000 to %01011111  AII medium index
        { 0x5F, AII_INDEX_MEDIUM },

        // %01100000 to %01101111  AII large index
        { 0x6F, AII_INDEX_LARGE },

        // %01110000 to %01110111  ILLEGAL
        { 0x77, STATE_ILLEGAL },

        // %01111000  AII literal (no prefix, no namespace)
        // %01111001  AII literal (no prefix, namespace)
        { 0x79, AII_LITERAL },

        // %01111010  ILLEGAL
        { 0x7A, STATE_ILLEGAL },

        // %01111011  AII literal (prefix, namespace)
        { 0x7B, AII_LITERAL },

        // %10000000 to %11101111  ILLEGAL
        { 0xEF, STATE_ILLEGAL },

        // Terminators

        // %11110000  single terminator
        { 0xF0, AII_TERMINATOR_SINGLE },

        // %11110000 to %11111110 ILLEGAL
        { 0xFE, STATE_ILLEGAL },

        // %11111111  double terminator
        { 0xFF, AII_TERMINATOR_DOUBLE }
    };


    // AII value states
    public final static int NISTRING_UTF8_SMALL_LENGTH     = 0;
    public final static int NISTRING_UTF8_MEDIUM_LENGTH    = 1;
    public final static int NISTRING_UTF8_LARGE_LENGTH     = 2;
    public final static int NISTRING_UTF16_SMALL_LENGTH    = 3;
    public final static int NISTRING_UTF16_MEDIUM_LENGTH   = 4;
    public final static int NISTRING_UTF16_LARGE_LENGTH    = 5;
    public final static int NISTRING_RA                    = 6;
    public final static int NISTRING_EA                    = 7;
    public final static int NISTRING_INDEX_SMALL           = 8;
    public final static int NISTRING_INDEX_MEDIUM          = 9;
    public final static int NISTRING_INDEX_LARGE           = 10;
    public final static int NISTRING_EMPTY                 = 11;

    private static final int[] NISTRING = new int[256];

    private static final int[][] NISTRING_RANGES = {
        // UTF-8 string

        // %00000000 to %00000111  UTF-8 no add to table small length
        { 0x07, NISTRING_UTF8_SMALL_LENGTH },

        // %00001000  UTF-8 no add to table medium length
        { 0x08, NISTRING_UTF8_MEDIUM_LENGTH },

        // %00001001 to %00001011 ILLEGAL
        { 0x0B, STATE_ILLEGAL },

        // %00001100  UTF-8 no add to table large length
        { 0x0C, NISTRING_UTF8_LARGE_LENGTH },

        // %00001101 to %00001111 ILLEGAL
        { 0x0F, STATE_ILLEGAL },

        // UTF-16 string

        // %00010000 to %00010111  UTF-16 no add to table small length
        { 0x17, NISTRING_UTF16_SMALL_LENGTH },

        // %00001000  UTF-16 no add to table medium length
        { 0x18, NISTRING_UTF16_MEDIUM_LENGTH },

        // %00011001 to %00011011 ILLEGAL
        { 0x1B, STATE_ILLEGAL },

        // %00011100  UTF-16 no add to table large length
        { 0x1C, NISTRING_UTF16_LARGE_LENGTH },

        // %00011101 to %00011111 ILLEGAL
        { 0x1F, STATE_ILLEGAL },

        // Restricted alphabet

        // %00100000 to %00101111  RA no add to table small length
        { 0x2F, NISTRING_RA },

        // Encoding algorithm

        // %00110000 to %00111111  EA no add to table
        { 0x3F, NISTRING_EA },

        // UTF-8 string, add to table

        // %01000000 to %01000111  UTF-8 add to table small length
        { 0x47, NISTRING_UTF8_SMALL_LENGTH },

        // %01001000  UTF-8 add to table medium length
        { 0x48, NISTRING_UTF8_MEDIUM_LENGTH },

        // %01001001 to %01001011 ILLEGAL
        { 0x4B, STATE_ILLEGAL },

        // %01001100  UTF-8 add to table large length
        { 0x4C, NISTRING_UTF8_LARGE_LENGTH },

        // %01001101 to %01001111 ILLEGAL
        { 0x4F, STATE_ILLEGAL },

        // UTF-16 string, add to table

        // %01010000 to %01010111  UTF-16 add to table small length
        { 0x57, NISTRING_UTF16_SMALL_LENGTH },

        // %01001000  UTF-16 add to table medium length
        { 0x58, NISTRING_UTF16_MEDIUM_LENGTH },

        // %01011001 to %01011011 ILLEGAL
        { 0x5B, STATE_ILLEGAL },

        // %01011100  UTF-16 add to table large length
        { 0x5C, NISTRING_UTF16_LARGE_LENGTH },

        // %01011101 to %01011111 ILLEGAL
        { 0x5F, STATE_ILLEGAL },

        // Restricted alphabet, add to table

        // %01100000 to %01101111  RA no add to table small length
        { 0x6F, NISTRING_RA },

        // Encoding algorithm, add to table

        // %01110000 to %01111111  EA add to table
        { 0x7F, NISTRING_EA },

        // Index

        // %10000000 to %10111111 index small
        { 0xBF, NISTRING_INDEX_SMALL },

        // %11000000 to %11011111 index medium
        { 0xDF, NISTRING_INDEX_MEDIUM },

        // %11100000 to %11101111 index large
        { 0xEF, NISTRING_INDEX_LARGE },

        // %11110000 to %11111110 ILLEGAL
        { 0xFE, STATE_ILLEGAL },

        // %11111111 Empty value
        { 0xFF, NISTRING_EMPTY },
    };


    /* package */ final static int ISTRING_SMALL_LENGTH        = 0;
    /* package */ final static int ISTRING_MEDIUM_LENGTH       = 1;
    /* package */ final static int ISTRING_LARGE_LENGTH        = 2;
    /* package */ final static int ISTRING_INDEX_SMALL         = 3;
    /* package */ final static int ISTRING_INDEX_MEDIUM        = 4;
    /* package */ final static int ISTRING_INDEX_LARGE         = 5;

    private static final int[] ISTRING = new int[256];

    private static final int[][] ISTRING_RANGES = {
        // %00000000 to %00111111 small length
        { 0x3F, ISTRING_SMALL_LENGTH },

        // %01000000 medium length
        { 0x40, ISTRING_MEDIUM_LENGTH },

        // %01000001 to %01011111 ILLEGAL
        { 0x5F, STATE_ILLEGAL },

        // %01100000 large length
        { 0x60, ISTRING_LARGE_LENGTH },

        // %01100001 to %01111111 ILLEGAL
        { 0x7F, STATE_ILLEGAL },

        // %10000000 to %10111111 index small
        { 0xBF, ISTRING_INDEX_SMALL },

        // %11000000 to %11011111 index medium
        { 0xDF, ISTRING_INDEX_MEDIUM },

        // %11100000 to %11101111 index large
        { 0xEF, ISTRING_INDEX_LARGE },

        // %11110000 to %11111111 ILLEGAL
        { 0xFF, STATE_ILLEGAL },
    };


    /* package */ final static int ISTRING_PREFIX_NAMESPACE_LENGTH_3   = 6;
    /* package */ final static int ISTRING_PREFIX_NAMESPACE_LENGTH_5   = 7;
    /* package */ final static int ISTRING_PREFIX_NAMESPACE_LENGTH_29  = 8;
    /* package */ final static int ISTRING_PREFIX_NAMESPACE_LENGTH_36  = 9;
    /* package */ final static int ISTRING_PREFIX_NAMESPACE_INDEX_ZERO = 10;

    private static final int[] ISTRING_PREFIX_NAMESPACE = new int[256];

    private static final int[][] ISTRING_PREFIX_NAMESPACE_RANGES = {
        // %00000000 to %00000001 small length
        { 0x01, ISTRING_SMALL_LENGTH },

        // %00000010 small length
        { 0x02, ISTRING_PREFIX_NAMESPACE_LENGTH_3 },

        // %00000011 small length
        { 0x03, ISTRING_SMALL_LENGTH },

        // %00000100 small length
        { 0x04, ISTRING_PREFIX_NAMESPACE_LENGTH_5 },

        // %00011011 small length
        { 0x1B, ISTRING_SMALL_LENGTH },

        // %00011100 small length
        { 0x1C, ISTRING_PREFIX_NAMESPACE_LENGTH_29 },

        // %00100010 small length
        { 0x22, ISTRING_SMALL_LENGTH },

        // %00100011 small length
        { 0x23, ISTRING_PREFIX_NAMESPACE_LENGTH_36 },

        // %00000101 to %00111111 small length
        { 0x3F, ISTRING_SMALL_LENGTH },




        // %01000000 medium length
        { 0x40, ISTRING_MEDIUM_LENGTH },

        // %01000001 to %01011111 ILLEGAL
        { 0x5F, STATE_ILLEGAL },

        // %01100000 large length
        { 0x60, ISTRING_LARGE_LENGTH },

        // %01100001 to %01111111 ILLEGAL
        { 0x7F, STATE_ILLEGAL },

        // %10000000 index small, 0
        { 0x80, ISTRING_PREFIX_NAMESPACE_INDEX_ZERO },

        // %10000000 to %10111111 index small
        { 0xBF, ISTRING_INDEX_SMALL },

        // %11000000 to %11011111 index medium
        { 0xDF, ISTRING_INDEX_MEDIUM },

        // %11100000 to %11101111 index large
        { 0xEF, ISTRING_INDEX_LARGE },

        // %11110000 to %11111111 ILLEGAL
        { 0xFF, STATE_ILLEGAL },
    };

    // UTF-8 states
    /* package */ final static int UTF8_NCNAME_NCNAME         = 0;
    /* package */ final static int UTF8_NCNAME_NCNAME_CHAR    = 1;
    /* package */ final static int UTF8_TWO_BYTES             = 2;
    /* package */ final static int UTF8_THREE_BYTES           = 3;
    /* package */ final static int UTF8_FOUR_BYTES            = 4;

    private static final int[] UTF8_NCNAME = new int[256];

    private static final int[][] UTF8_NCNAME_RANGES = {

        // Basic Latin

        // %00000000 to %00101100
        { 0x2C, STATE_ILLEGAL },

        // '-' '.'
        // %%00101101 to %00101110 [#x002D-#x002E]
        { 0x2E, UTF8_NCNAME_NCNAME_CHAR },

        // %00101111
        { 0x2F, STATE_ILLEGAL },

        // [0-9]
        // %0011000 to %00111001  [#x0030-#x0039]
        { 0x39, UTF8_NCNAME_NCNAME_CHAR },

        // %01000000
        { 0x40, STATE_ILLEGAL },

        // [A-Z]
        // %01000001 to %01011010 [#x0041-#x005A]
        { 0x5A, UTF8_NCNAME_NCNAME },

        // %01011110
        { 0x5E, STATE_ILLEGAL },

        // '_'
        // %01011111 [#x005F]
        { 0x5F, UTF8_NCNAME_NCNAME },

        // %01100000
        { 0x60, STATE_ILLEGAL },

        // [a-z]
        // %01100001 to %01111010 [#x0061-#x007A]
        { 0x7A, UTF8_NCNAME_NCNAME },

        // %01111011 to %01111111
        { 0x7F, STATE_ILLEGAL },


        // Two bytes

        // %10000000 to %11000001
        { 0xC1, STATE_ILLEGAL },

        // %11000010 to %11011111
        { 0xDF, UTF8_TWO_BYTES },


        // Three bytes

        // %11100000 to %11101111
        { 0xEF, UTF8_THREE_BYTES },


        // Four bytes

        // %11110000 to %11110111
        { 0xF7, UTF8_FOUR_BYTES },


        // %11111000 to %11111111
        { 0xFF, STATE_ILLEGAL }
    };

    /* package */ final static int UTF8_ONE_BYTE = 1;

    private static final int[] UTF8 = new int[256];

    private static final int[][] UTF8_RANGES = {

        // Basic Latin

        // %00000000 to %00001000
        { 0x08, STATE_ILLEGAL },

        // CHARACTER TABULATION, LINE FEED
        // %%00001001 to %00001010 [#x0009-#x000A]
        { 0x0A, UTF8_ONE_BYTE },

        // %00001011 to %00001100
        { 0x0C, STATE_ILLEGAL },

        // CARRIAGE RETURN
        // %00001101 [#x000D]
        { 0x0D, UTF8_ONE_BYTE },

        // %00001110 to %00011111
        { 0x1F, STATE_ILLEGAL },

        // %0010000 to %01111111
        { 0x7F, UTF8_ONE_BYTE },


        // Two bytes

        // %10000000 to %11000001
        { 0xC1, STATE_ILLEGAL },

        // %11000010 to %11011111
        { 0xDF, UTF8_TWO_BYTES },


        // Three bytes

        // %11100000 to %11101111
        { 0xEF, UTF8_THREE_BYTES },


        // Four bytes

        // %11110000 to %11110111
        { 0xF7, UTF8_FOUR_BYTES },


        // %11111000 to %11111111
        { 0xFF, STATE_ILLEGAL }
    };

    private static void constructTable(int[] table, int[][] ranges) {
        int start = 0x00;
        for (int range = 0; range < ranges.length; range++) {
            int end = ranges[range][RANGE_INDEX_END];
            int value = ranges[range][RANGE_INDEX_VALUE];
            for (int i = start; i<= end; i++) {
                table[i] = value;
            }
            start = end + 1;
        }
    }

    public static final int DII(final int index) {
        return DII[index];
    }

    public static final int EII(final int index) {
        return EII[index];
    }

    public static final int AII(final int index) {
        return AII[index];
    }

    public static final int NISTRING(final int index) {
        return NISTRING[index];
    }

    public static final int ISTRING(final int index) {
        return ISTRING[index];
    }

    public static final int ISTRING_PREFIX_NAMESPACE(final int index) {
        return ISTRING_PREFIX_NAMESPACE[index];
    }

    public static final int UTF8(final int index) {
        return UTF8[index];
    }

    public static final int UTF8_NCNAME(final int index) {
        return UTF8_NCNAME[index];
    }

    static {
        // DII
        constructTable(DII, DII_RANGES);

        // EII
        constructTable(EII, EII_RANGES);

        // AII
        constructTable(AII, AII_RANGES);

        // AII Value
        constructTable(NISTRING, NISTRING_RANGES);

        // Identifying string
        constructTable(ISTRING, ISTRING_RANGES);

        // Identifying string
        constructTable(ISTRING_PREFIX_NAMESPACE, ISTRING_PREFIX_NAMESPACE_RANGES);

        // UTF-8 NCNAME states
        constructTable(UTF8_NCNAME, UTF8_NCNAME_RANGES);

        // UTF-8 states
        constructTable(UTF8, UTF8_RANGES);
    }

    private DecoderStateTables() {
    }
}
