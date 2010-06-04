/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.inputmethods.internal.thaiim;

import java.awt.im.InputMethodRequests;

public class ThaiRules {

    public static final char BASE = 0x0e00;

    public static final byte NON  =  0;
    public static final byte CONS =  1;
    public static final byte LV   =  2;
    public static final byte FV1  =  3;
    public static final byte FV2  =  4;
    public static final byte FV3  =  5;
    public static final byte FV4  =  6;
    /* Note that FV4 is added. It is not in WTT.
     * We need it for SARA AM since it has a
     * weired characteristic to share the same
     * cell with whatever consonant preceeds it.
     */
    public static final byte BV1  =  7;
    public static final byte BV2  =  8;
    public static final byte BD   =  9;
    public static final byte TONE = 10;
    public static final byte AD1  = 11;
    public static final byte AD2  = 12;
    public static final byte AD3  = 13;
    public static final byte AV1  = 14;
    public static final byte AV2  = 15;
    public static final byte AV3  = 16;

    /**
     * Constants for validity checking and auto correction
     */
    public static final byte STRICT    = 0;
    public static final byte LOOSE     = 1;
    public static final byte NOREPLACE = 2;

    public static final byte[] CHARTYPE = {
    /* 0e00 UNUSED                      */      NON,
    /* THAI CHARACTER KO KAI            */      CONS,
    /* THAI CHARACTER KHO KHAI          */      CONS,
    /* THAI CHARACTER KHO KHUAT         */      CONS,
    /* THAI CHARACTER KHO KHWAI         */      CONS,
    /* THAI CHARACTER KHO KHON          */      CONS,
    /* THAI CHARACTER KHO RAKHANG       */      CONS,
    /* THAI CHARACTER NGO NGU           */      CONS,
    /* THAI CHARACTER CHO CHAN          */      CONS,
    /* THAI CHARACTER CHO CHING         */      CONS,
    /* THAI CHARACTER CHO CHANG         */      CONS,
    /* THAI CHARACTER SO SO             */      CONS,
    /* THAI CHARACTER CHO CHOE          */      CONS,
    /* THAI CHARACTER YO YING           */      CONS,
    /* THAI CHARACTER DO CHADA          */      CONS,
    /* THAI CHARACTER TO PATAK          */      CONS,
    /* THAI CHARACTER THO THAN          */      CONS,
    /* THAI CHARACTER THO NANGMONTHO    */      CONS,
    /* THAI CHARACTER THO PHUTHAO       */      CONS,
    /* THAI CHARACTER NO NEN            */      CONS,
    /* THAI CHARACTER DO DEK            */      CONS,
    /* THAI CHARACTER TO TAO            */      CONS,
    /* THAI CHARACTER THO THUNG         */      CONS,
    /* THAI CHARACTER THO THAHAN        */      CONS,
    /* THAI CHARACTER THO THONG         */      CONS,
    /* THAI CHARACTER NO NU             */      CONS,
    /* THAI CHARACTER BO BAIMAI         */      CONS,
    /* THAI CHARACTER PO PLA            */      CONS,
    /* THAI CHARACTER PHO PHUNG         */      CONS,
    /* THAI CHARACTER FO FA             */      CONS,
    /* THAI CHARACTER PHO PHAN          */      CONS,
    /* THAI CHARACTER FO FAN            */      CONS,
    /* THAI CHARACTER PHO SAMPHAO       */      CONS,
    /* THAI CHARACTER MO MA             */      CONS,
    /* THAI CHARACTER YO YAK            */      CONS,
    /* THAI CHARACTER RO RUA            */      CONS,
    /* THAI CHARACTER RU                */      FV3,
    /* THAI CHARACTER LO LING           */      CONS,
    /* THAI CHARACTER LU                */      FV3,
    /* THAI CHARACTER WO WAEN           */      CONS,
    /* THAI CHARACTER SO SALA           */      CONS,
    /* THAI CHARACTER SO RUSI           */      CONS,
    /* THAI CHARACTER SO SUA            */      CONS,
    /* THAI CHARACTER HO HIP            */      CONS,
    /* THAI CHARACTER LO CHULA          */      CONS,
    /* THAI CHARACTER O ANG             */      CONS,
    /* THAI CHARACTER HO NOKHUK         */      CONS,
    /* THAI CHARACTER PAIYANNOI         */      NON,
    /* THAI CHARACTER SARA A            */      FV1,
    /* THAI CHARACTER MAI HAN-AKAT      */      AV2,
    /* THAI CHARACTER SARA AA           */      FV1,
    /* THAI CHARACTER SARA AM           */      FV4,
    /* THAI CHARACTER SARA I            */      AV1,
    /* THAI CHARACTER SARA II           */      AV3,
    /* THAI CHARACTER SARA UE           */      AV2,
    /* THAI CHARACTER SARA UEE          */      AV3,
    /* THAI CHARACTER SARA U            */      BV1,
    /* THAI CHARACTER SARA UU           */      BV2,
    /* THAI CHARACTER PHINTHU           */      BD,
    /* 0e3b UNUSED                      */      NON,
    /* 0e3c UNUSED                      */      NON,
    /* 0e3d UNUSED                      */      NON,
    /* 0e3e UNUSED                      */      NON,
    /* THAI CURRENCY SYMBOL BAHT        */      NON,
    /* THAI CHARACTER SARA E            */      LV,
    /* THAI CHARACTER SARA AE           */      LV,
    /* THAI CHARACTER SARA O            */      LV,
    /* THAI CHARACTER SARA AI MAIMUAN   */      LV,
    /* THAI CHARACTER SARA AI MAIMALAI  */      LV,
    /* THAI CHARACTER LAKKHANGYAO       */      FV2,
    /* THAI CHARACTER MAIYAMOK          */      NON,
    /* THAI CHARACTER MAITAIKHU         */      AD2,
    /* THAI CHARACTER MAI EK            */      TONE,
    /* THAI CHARACTER MAI THO           */      TONE,
    /* THAI CHARACTER MAI TRI           */      TONE,
    /* THAI CHARACTER MAI CHATTAWA      */      TONE,
    /* THAI CHARACTER THANTHAKHAT       */      AD1,
    /* THAI CHARACTER NIKHAHIT          */      AD3,
    /* THAI CHARACTER YAMAKKAN          */      AD3,
    /* THAI CHARACTER FONGMAN           */      NON,
    /* THAI DIGIT ZERO                  */      NON,
    /* THAI DIGIT ONE                   */      NON,
    /* THAI DIGIT TWO                   */      NON,
    /* THAI DIGIT THREE                 */      NON,
    /* THAI DIGIT FOUR                  */      NON,
    /* THAI DIGIT FIVE                  */      NON,
    /* THAI DIGIT SIX                   */      NON,
    /* THAI DIGIT SEVEN                 */      NON,
    /* THAI DIGIT EIGHT                 */      NON,
    /* THAI DIGIT NINE                  */      NON,
    /* THAI CHARACTER ANGKHANKHU        */      NON,
    /* THAI CHARACTER KHOMUT            */      NON
    };

    private InputMethodRequests requests;

    ThaiRules(InputMethodRequests requests) {
        this.requests = requests;
    }

    public static byte getCharType(char c) {
        byte cType;
        int ci = ((int) c) - (int) BASE;
        if (ci < 0 || ci >= CHARTYPE.length)
            cType = NON;
        else
            cType = CHARTYPE[ci];
        return cType;
    }

    private static boolean isValid(char c1, char c2, int[] validityArray) {
        return ((validityArray[getCharType(c1)]
                & (1 << getCharType(c2))) != 0);
    }

    /**
     * VALIDITY is a bit matrix defining whether one
     * character is allowed to be typed in after the
     * previous one (array index). Determining the
     * validity is done by bit-anding the 2nd char
     * type's mask (obtained by 1 << chartype) with
     * the array element indexed by the first char
     * type. If the result is non-zero, the 2nd
     * character is allowed to follow the first.
     */

    /* Please note that the bits in the comment below
     * are displayed least significant bit first.
     * The actual value reflexs this representation
     * when the bits are swapped.
     */

    private static final int[] INPUTVALIDITY = {
    /* NON  1110 010  0 0000 0000 0 */          0x00027,
    /* CONS 1111 111  1 1111 1111 1 */          0x1ffff,
    /* LV   0100 000  0 0000 0000 0 */          0x00002,
    /* FV1  1110 010  0 0000 0000 0 */          0x00027,
    /* FV2  1110 010  0 0000 0000 0 */          0x00027,
    /* FV3  1110 110  0 0000 0000 0 */          0x00037,
    /* FV4  1110 010  0 0000 0000 0 */          0x00027,
    /* BV1  1110 010  0 0011 0000 0 */          0x00c27,
    /* BV2  1110 010  0 0010 0000 0 */          0x00427,
    /* BD   1110 010  0 0000 0000 0 */          0x00027,
    /* TONE 1111 011  0 0000 0000 0 */          0x0006f,
    /* AD1  1110 010  0 0000 0000 0 */          0x00027,
    /* AD2  1110 010  0 0000 0000 0 */          0x00027,
    /* AD3  1110 010  0 0000 0000 0 */          0x00027,
    /* AV1  1110 010  0 0011 0000 0 */          0x00c27,
    /* AV2  1110 010  0 0010 0000 0 */          0x00427,
    /* AV3  1110 010  0 0010 0100 0 */          0x02427
    };

    private static final int[] COMPOSABLE = {
    /* NON  0000 000  0 0000 0000 0 */          0x00000,
    /* CONS 0000 001  1 1111 1111 1 */          0x1ffc0,
    /* LV   0000 000  0 0000 0000 0 */          0x00000,
    /* FV1  0000 000  0 0000 0000 0 */          0x00000,
    /* FV2  0000 000  0 0000 0000 0 */          0x00000,
    /* FV3  0000 000  0 0000 0000 0 */          0x00000,
    /* FV4  0000 000  0 0000 0000 0 */          0x00000,
    /* BV1  0000 000  0 0011 0000 0 */          0x00c00,
    /* BV2  0000 000  0 0010 0000 0 */          0x00400,
    /* BD   0000 000  0 0000 0000 0 */          0x00000,
    /* TONE 0000 001  0 0000 0000 0 */          0x00040,
    /* AD1  0000 000  0 0000 0000 0 */          0x00000,
    /* AD2  0000 000  0 0000 0000 0 */          0x00000,
    /* AD3  0000 000  0 0000 0000 0 */          0x00000,
    /* AV1  0000 000  0 0011 0000 0 */          0x00c00,
    /* AV2  0000 000  0 0010 0000 0 */          0x00400,
    /* AV3  0000 000  0 0010 0100 0 */          0x02400
    };

    private static final int[] REPLACABLE = {
    /* NON  0000 000  0 0000 0000 0 */          0x00000,
    /* CONS 0000 000  0 0000 0000 0 */          0x00000,
    /* LV   0000 000  0 0000 0000 0 */          0x00000,
    /* FV1  0000 000  0 0000 0000 0 */          0x00000,
    /* FV2  0000 000  0 0000 0000 0 */          0x00000,
    /* FV3  0000 000  0 0000 0000 0 */          0x00000,
    /* FV4  0000 001  1 1001 1111 1 */          0x1f9c0,
    /* BV1  0000 001  1 1100 1111 1 */          0x1f3c0,
    /* BV2  0000 001  1 1101 1111 1 */          0x1fbc0,
    /* BD   0000 001  1 1111 1111 1 */          0x1ffc0,
    /* TONE 0000 000  0 0111 1100 0 */          0x03e00,
    /* AD1  0000 001  0 1111 1101 1 */          0x1bf40,
    /* AD2  0000 001  1 1111 1111 1 */          0x1ffc0,
    /* AD3  0000 001  1 1111 1111 0 */          0x0ffc0,
    /* AV1  0000 001  1 1100 1111 1 */          0x1f3c0,
    /* AV2  0000 001  1 1101 1111 1 */          0x1fbc0,
    /* AV3  0000 001  1 1101 1011 1 */          0x1dbc0
    };

    private static final int[] SWAPPABLE = {
    /* NON  0000 000  0 0000 0000 0 */          0x00000,
    /* CONS 0000 000  0 0000 0000 0 */          0x00000,
    /* LV   0000 000  0 0000 0000 0 */          0x00000,
    /* FV1  0000 000  0 0000 0000 0 */          0x00000,
    /* FV2  0000 000  0 0000 0000 0 */          0x00000,
    /* FV3  0000 000  0 0000 0000 0 */          0x00000,
    /* FV4  0000 000  0 0010 0000 0 */          0x00400,
    /* BV1  0000 000  0 0000 0000 0 */          0x00000,
    /* BV2  0000 000  0 0000 0000 0 */          0x00000,
    /* BD   0000 000  0 0000 0000 0 */          0x00000,
    /* TONE 0000 000  1 1000 0011 1 */          0x1c180,
    /* AD1  0000 000  1 0000 0010 0 */          0x04080,
    /* AD2  0000 000  0 0000 0000 0 */          0x00000,
    /* AD3  0000 000  0 0000 0000 1 */          0x10000,
    /* AV1  0000 000  0 0000 0000 0 */          0x00000,
    /* AV2  0000 000  0 0000 0000 0 */          0x00000,
    /* AV3  0000 000  0 0000 0000 0 */          0x00000
    };

    public static boolean isInputValid(char c1, char c2) {
        return isValid(c1, c2, INPUTVALIDITY);
    }

    public static boolean isComposable(char c1, char c2) {
        return isValid(c1, c2, COMPOSABLE);
    }

    public static boolean isSwappable(char c1, char c2) {
        return isValid(c1, c2, SWAPPABLE);
    }

    public static boolean isReplacable(char c1, char c2) {
        return isValid(c1, c2, REPLACABLE);
    }

    public static boolean isForward(char c) {
        return (getCharType(c) < FV4);
    }

    public static boolean isDead(char c) {
        return (getCharType(c) > FV3);
    }

    public boolean isInputValid(char current) {
        int offset = requests.getInsertPositionOffset();
        if (offset == 0) {
            byte charType = getCharType(current);
            return ((charType < FV1) || (charType == FV3));
        }
        else {
            char prev = requests.getCommittedText(offset-1, offset, null).first();

            if(isForward(current)) {
                if (isInputValid(prev, current)) {
                    if (getCharType(prev) == TONE &&
                        getCharType(current) == FV1) {
                        if (offset == 1) {
                            return true;
                        } else {
                            char pprev =
                                requests.getCommittedText(offset-2, offset-1, null).first();
                            return isInputValid(pprev, current);
                        }
                    } else {
                        return true;
                    }
                } else if (prev == '\u0e32' &&       // SARA AA
                           current  == '\u0e30') {   // SARA A
                    return true;
                } else if (prev == '\u0e4d' &&       // NIKAHIT
                           current  == '\u0e32') {   // SARA AA
                                                     // Special compose to SARA AM
                    return true;
                } else {
                    return false;
                }
            } else {
                if(isInputValid(prev, current)) {
                    if (getCharType(prev) == TONE &&
                        getCharType(current) == FV4) {
                        return (offset != 1);
                    } else {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        }
    }
}
