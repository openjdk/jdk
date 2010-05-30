/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

/*
 * (C) Copyright IBM Corp. 2000 - All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by IBM. These materials are provided
 * under terms of a License Agreement between IBM and Sun.
 * This technology is protected by multiple US and International
 * patents. This notice and attribution to IBM may not be removed.
 *
 */

package com.sun.inputmethods.internal.indicim;

class DevanagariTables {

    static final char[] keyboardMap = {
    /* 00 */ '\u0000',
    /* 01 */ '\u0001',
    /* 02 */ '\u0002',
    /* 03 */ '\u0003',
    /* 04 */ '\u0004',
    /* 05 */ '\u0005',
    /* 06 */ '\u0006',
    /* 07 */ '\u0007',
    /* 08 */ '\u0008',
    /* 09 */ '\u0009',
    /* 0A */ '\012',
    /* 0B */ '\u000B',
    /* 0C */ '\u000C',
    /* 0D */ '\015',
    /* 0E */ '\u000E',
    /* 0F */ '\u000F',
    /* 10 */ '\u0010',
    /* 11 */ '\u0011',
    /* 12 */ '\u0012',
    /* 13 */ '\u0013',
    /* 14 */ '\u0014',
    /* 15 */ '\u0015',
    /* 16 */ '\u0016',
    /* 17 */ '\u0017',
    /* 18 */ '\u0018',
    /* 19 */ '\u0019',
    /* 1A */ '\u001A',
    /* 1B */ '\u001B',
    /* 1C */ '\u001C',
    /* 1D */ '\u001D',
    /* 1E */ '\u001E',
    /* 1F */ '\u001F',
    /* 20 */ '\u0020',
    /* 21 */ '\u090D',   // '!'
    /* 22 */ '\u0920',   // '"'
    /* 23 */ '\uFF00',   // '#'
    /* 24 */ '\uFF01',   // '$'
    /* 25 */ '\uFF02',   // '%'
    /* 26 */ '\uFF04',   // '&'
    /* 27 */ '\u091F',   // '''
    /* 28 */ '\u0028',   // '('
    /* 29 */ '\u0029',   // ')'
    /* 2A */ '\uFF05',   // '*'
    /* 2B */ '\u090B',   // '+'
    /* 2C */ '\u002C',   // ','
    /* 2D */ '\u002D',   // '-'
    /* 2E */ '\u002E',   // '.'
    /* 2F */ '\u092F',   // '/'
    /* 30 */ '\u0966',   // '0'
    /* 31 */ '\u0967',   // '1'
    /* 32 */ '\u0968',   // '2'
    /* 33 */ '\u0969',   // '3'
    /* 34 */ '\u096A',   // '4'
    /* 35 */ '\u096B',   // '5'
    /* 36 */ '\u096C',   // '6'
    /* 37 */ '\u096D',   // '7'
    /* 38 */ '\u096E',   // '8'
    /* 39 */ '\u096F',   // '9'
    /* 3A */ '\u091B',   // ':'
    /* 3B */ '\u091A',   // ';'
    /* 3C */ '\u0937',   // '<'
    /* 3D */ '\u0943',   // '='
    /* 3E */ '\u0964',   // '>'
    /* 3F */ '\u095F',   // '?'
    /* 40 */ '\u0945',   // '@'
    /* 41 */ '\u0913',   // 'A'
    /* 42 */ '\u0934',   // 'B'
    /* 43 */ '\u0923',   // 'C'
    /* 44 */ '\u0905',   // 'D'
    /* 45 */ '\u0906',   // 'E'
    /* 46 */ '\u0907',   // 'F'
    /* 47 */ '\u0909',   // 'G'
    /* 48 */ '\u092B',   // 'H'
    /* 49 */ '\u0918',   // 'I'
    /* 4A */ '\u0931',   // 'J'
    /* 4B */ '\u0916',   // 'K'
    /* 4C */ '\u0925',   // 'L'
    /* 4D */ '\u0936',   // 'M'
    /* 4E */ '\u0933',   // 'N'
    /* 4F */ '\u0927',   // 'O'
    /* 50 */ '\u091D',   // 'P'
    /* 51 */ '\u0914',   // 'Q'
    /* 52 */ '\u0908',   // 'R'
    /* 53 */ '\u090F',   // 'S'
    /* 54 */ '\u090A',   // 'T'
    /* 55 */ '\u0919',   // 'U'
    /* 56 */ '\u0929',   // 'V'
    /* 57 */ '\u0910',   // 'W'
    /* 58 */ '\u0901',   // 'X'
    /* 59 */ '\u092D',   // 'Y'
    /* 5A */ '\u090E',   // 'Z'
    /* 5B */ '\u0921',   // '['
    /* 5C */ '\u0949',   // '\'
    /* 5D */ '\u093C',   // ']'
    /* 5E */ '\uFF03',   // '^'
    /* 5F */ '\u0903',   // '_'
    /* 60 */ '\u094A',   // '`'
    /* 61 */ '\u094B',   // 'a'
    /* 62 */ '\u0935',   // 'b'
    /* 63 */ '\u092E',   // 'c'
    /* 64 */ '\u094D',   // 'd'
    /* 65 */ '\u093E',   // 'e'
    /* 66 */ '\u093F',   // 'f'
    /* 67 */ '\u0941',   // 'g'
    /* 68 */ '\u092A',   // 'h'
    /* 69 */ '\u0917',   // 'i'
    /* 6A */ '\u0930',   // 'j'
    /* 6B */ '\u0915',   // 'k'
    /* 6C */ '\u0924',   // 'l'
    /* 6D */ '\u0938',   // 'm'
    /* 6E */ '\u0932',   // 'n'
    /* 6F */ '\u0926',   // 'o'
    /* 70 */ '\u091C',   // 'p'
    /* 71 */ '\u094C',   // 'q'
    /* 72 */ '\u0940',   // 'r'
    /* 73 */ '\u0947',   // 's'
    /* 74 */ '\u0942',   // 't'
    /* 75 */ '\u0939',   // 'u'
    /* 76 */ '\u0928',   // 'v'
    /* 77 */ '\u0948',   // 'w'
    /* 78 */ '\u0902',   // 'x'
    /* 79 */ '\u092C',   // 'y'
    /* 7A */ '\u0946',   // 'z'
    /* 7B */ '\u0922',   // '{'
    /* 7C */ '\u0911',   // '|'
    /* 7D */ '\u091E',   // '}'
    /* 7E */ '\u0912',   // '~'
    /* 7F */ '\u007F'    //
};

    // the character substitutions for the meta characters.
    static final char[] RA_SUB = {'\u094D', '\u0930'};
    static final char[] RA_SUP = {'\u0930', '\u094D'};
    static final char[] CONJ_JA_NYA = {'\u091C', '\u094D', '\u091E'};
    static final char[] CONJ_TA_RA = {'\u0924', '\u094D', '\u0930'};
    static final char[] CONJ_KA_SSA = {'\u0915', '\u094D', '\u0937'};
    static final char[] CONJ_SHA_RA = {'\u0936', '\u094D', '\u0930'};

    static final char[][] substitutionTable = {
        RA_SUB, RA_SUP, CONJ_JA_NYA, CONJ_TA_RA, CONJ_KA_SSA, CONJ_SHA_RA
    };

    // The following characters followed by Nukta should be replaced
    // by the corresponding character as defined in ISCII91
    static final char SIGN_CANDRABINDU      = '\u0901';
    static final char LETTER_I              = '\u0907';
    static final char LETTER_II             = '\u0908';
    static final char LETTER_VOCALIC_R      = '\u090B';
    static final char LETTER_KA             = '\u0915';
    static final char LETTER_KHA            = '\u0916';
    static final char LETTER_GA             = '\u0917';
    static final char LETTER_JA             = '\u091C';
    static final char LETTER_DDA            = '\u0921';
    static final char LETTER_DDHA           = '\u0922';
    static final char LETTER_PHA            = '\u092B';
    static final char VOWEL_SIGN_I          = '\u093F';
    static final char VOWEL_SIGN_II         = '\u0940';
    static final char VOWEL_SIGN_VOCALIC_R  = '\u0943';
    static final char DANDA                 = '\u0964';

   // The follwing characters replace the above characters followed by Nukta. These
   // are defined in one to one correspondence order.
    static final char SIGN_OM               = '\u0950';
    static final char LETTER_VOCALIC_L      = '\u090C';
    static final char LETTER_VOCALIC_LL     = '\u0961';
    static final char LETTER_VOCALIC_RR     = '\u0960';
    static final char LETTER_QA             = '\u0958';
    static final char LETTER_KHHA           = '\u0959';
    static final char LETTER_GHHA           = '\u095A';
    static final char LETTER_ZA             = '\u095B';
    static final char LETTER_DDDHA          = '\u095C';
    static final char LETTER_RHA            = '\u095D';
    static final char LETTER_FA             = '\u095E';
    static final char VOWEL_SIGN_VOCALIC_L  = '\u0962';
    static final char VOWEL_SIGN_VOCALIC_LL = '\u0963';
    static final char VOWEL_SIGN_VOCALIC_RR = '\u0944';
    static final char SIGN_AVAGRAHA         = '\u093D';

    static final char[] joinWithNukta = {
        SIGN_CANDRABINDU,
        LETTER_I,
        LETTER_II,
        LETTER_VOCALIC_R ,
        LETTER_KA,
        LETTER_KHA,
        LETTER_GA,
        LETTER_JA,
        LETTER_DDA,
        LETTER_DDHA,
        LETTER_PHA,
        VOWEL_SIGN_I,
        VOWEL_SIGN_II,
        VOWEL_SIGN_VOCALIC_R,
        DANDA
    };

    static final char[] nuktaForm = {
        SIGN_OM,
        LETTER_VOCALIC_L,
        LETTER_VOCALIC_LL,
        LETTER_VOCALIC_RR,
        LETTER_QA,
        LETTER_KHHA,
        LETTER_GHHA,
        LETTER_ZA,
        LETTER_DDDHA,
        LETTER_RHA,
        LETTER_FA,
        VOWEL_SIGN_VOCALIC_L,
        VOWEL_SIGN_VOCALIC_LL,
        VOWEL_SIGN_VOCALIC_RR,
        SIGN_AVAGRAHA
    };
}
