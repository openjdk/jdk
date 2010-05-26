/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 */

package sun.text.resources;

import java.util.ListResourceBundle;

public class CollationData_th extends ListResourceBundle {

    protected final Object[][] getContents() {
        return new Object[][] {
            { "Rule",
                "! "                            // First turn on the SE Asian Vowel/Consonant
                                                // swapping rule
                + "& Z "                        // Put in all of the consonants, after Z
                + "< \u0E01 "                   //  KO KAI
                + "< \u0E02 "                   //  KHO KHAI
                + "< \u0E03 "                   //  KHO KHUAT
                + "< \u0E04 "                   //  KHO KHWAI
                + "< \u0E05 "                   //  KHO KHON
                + "< \u0E06 "                   //  KHO RAKHANG
                + "< \u0E07 "                   //  NGO NGU
                + "< \u0E08 "                   //  CHO CHAN
                + "< \u0E09 "                   //  CHO CHING
                + "< \u0E0A "                   //  CHO CHANG
                + "< \u0E0B "                   //  SO SO
                + "< \u0E0C "                   //  CHO CHOE
                + "< \u0E0D "                   //  YO YING
                + "< \u0E0E "                   //  DO CHADA
                + "< \u0E0F "                   //  TO PATAK
                + "< \u0E10 "                   //  THO THAN
                + "< \u0E11 "                   //  THO NANGMONTHO
                + "< \u0E12 "                   //  THO PHUTHAO
                + "< \u0E13 "                   //  NO NEN
                + "< \u0E14 "                   //  DO DEK
                + "< \u0E15 "                   //  TO TAO
                + "< \u0E16 "                   //  THO THUNG
                + "< \u0E17 "                   //  THO THAHAN
                + "< \u0E18 "                   //  THO THONG
                + "< \u0E19 "                   //  NO NU
                + "< \u0E1A "                   //  BO BAIMAI
                + "< \u0E1B "                   //  PO PLA
                + "< \u0E1C "                   //  PHO PHUNG
                + "< \u0E1D "                   //  FO FA
                + "< \u0E1E "                   //  PHO PHAN
                + "< \u0E1F "                   //  FO FAN
                + "< \u0E20 "                   //  PHO SAMPHAO
                + "< \u0E21 "                   //  MO MA
                + "< \u0E22 "                   //  YO YAK
                + "< \u0E23 "                   //  RO RUA
                + "< \u0E24 "                   //  RU
                + "< \u0E25 "                   //  LO LING
                + "< \u0E26 "                   //  LU
                + "< \u0E27 "                   //  WO WAEN
                + "< \u0E28 "                   //  SO SALA
                + "< \u0E29 "                   //  SO RUSI
                + "< \u0E2A "                   //  SO SUA
                + "< \u0E2B "                   //  HO HIP
                + "< \u0E2C "                   //  LO CHULA
                + "< \u0E2D "                   //  O ANG
                + "< \u0E2E "                   //  HO NOKHUK

                //
                // Normal vowels
                //
                + "< \u0E30 "                   //  SARA A
                + "< \u0E31 "                   //  MAI HAN-AKAT
                + "< \u0E32 "                   //  SARA AA

                // Normalizer will decompose this character to \u0e4d\u0e32.  This is
                // a Bad Thing, because we want the separate characters to sort
                // differently than this individual one.  Since there's no public way to
                // set the decomposition to be used when creating a collator, there's
                // no way around this right now.
                // It's best to go ahead and leave the character in, because it occurs
                // this way a lot more often than it occurs as separate characters.
                + "< \u0E33 "                   //  SARA AM

                + "< \u0E34 "                   //  SARA I

                + "< \u0E35 "                   //  SARA II
                + "< \u0E36 "                   //  SARA UE
                + "< \u0E37 "                   //  SARA UEE
                + "< \u0E38 "                   //  SARA U
                + "< \u0E39 "                   //  SARA UU

                //
                // Preceding vowels
                //
                + "< \u0E40 "                   //  SARA E
                + "< \u0E41 "                   //  SARA AE
                + "< \u0E42 "                   //  SARA O
                + "< \u0E43 "                   //  SARA AI MAIMUAN
                + "< \u0E44 "                   //  SARA AI MAIMALAI

                //
                // Digits
                //
                + "< \u0E50 "                   //  DIGIT ZERO
                + "< \u0E51 "                   //  DIGIT ONE
                + "< \u0E52 "                   //  DIGIT TWO
                + "< \u0E53 "                   //  DIGIT THREE
                + "< \u0E54 "                   //  DIGIT FOUR
                + "< \u0E55 "                   //  DIGIT FIVE
                + "< \u0E56 "                   //  DIGIT SIX
                + "< \u0E57 "                   //  DIGIT SEVEN
                + "< \u0E58 "                   //  DIGIT EIGHT
                + "< \u0E59 "                   //  DIGIT NINE

                // Sorta tonal marks, but maybe not really
                + "< \u0E4D "                   //  NIKHAHIT

                //
                // Thai symbols are supposed to sort "after white space".
                // I'm treating this as making them sort just after the normal Latin-1
                // symbols, which are in turn after the white space.
                //
                + "&'\u007d'"  //  right-brace
                + "< \u0E2F "                   //  PAIYANNOI      (ellipsis, abbreviation)
                + "< \u0E46 "                   //  MAIYAMOK
                + "< \u0E4F "                   //  FONGMAN
                + "< \u0E5A "                   //  ANGKHANKHU
                + "< \u0E5B "                   //  KHOMUT
                + "< \u0E3F "                   //  CURRENCY SYMBOL BAHT

                // These symbols are supposed to be "after all characters"
                + "< \u0E4E "                   //  YAMAKKAN

                // This rare symbol also comes after all characters.  But when it is
                // used in combination with RU and LU, the combination is treated as
                // a separate letter, ala "CH" sorting after "C" in traditional Spanish.
                + "< \u0E45 "                   //  LAKKHANGYAO
                + "& \u0E24 < \u0E24\u0E45 "
                + "& \u0E26 < \u0E26\u0E45 "

                // Tonal marks are primary ignorables but are treated as secondary
                // differences
                + "& \u0301 "   // acute accent
                + "; \u0E47 "                   //  MAITAIKHU
                + "; \u0E48 "                   //  MAI EK
                + "; \u0E49 "                   //  MAI THO
                + "; \u0E4A "                   //  MAI TRI
                + "; \u0E4B "                   //  MAI CHATTAWA
                + "; \u0E4C "                   //  THANTHAKHAT


                // These are supposed to be ignored, so I'm treating them as controls
                + "& \u0001 "
                + "= \u0E3A "                   //  PHINTHU
                + "= '.' "                      //  period
                }
        };
    }
}
