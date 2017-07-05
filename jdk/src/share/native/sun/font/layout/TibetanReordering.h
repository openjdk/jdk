/*
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
 *
 */

/*
 *
 * (C) Copyright IBM Corp. 1998-2010 - All Rights Reserved
 *
 * Developed at DIT - Government of Bhutan
 *
 * Contact person: Pema Geyleg - <pema_geyleg@druknet.bt>
 *
 * This file is a modification of the ICU file KhmerReordering.h
 * by Jens Herden and Javier Sola who have given all their possible rights to IBM and the Governement of Bhutan
 * A first module for Dzongkha was developed by Karunakar under Panlocalisation funding.
 * Assistance for this module has been received from Namgay Thinley, Christopher Fynn and Javier Sola
 *
 */

#ifndef __TIBETANREORDERING_H
#define __TIBETANREORDERING_H

/**
 * \file
 * \internal
 */

// #include "LETypes.h"
// #include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

class LEGlyphStorage;

// Vocabulary
//     Base ->         A consonant in its full (not subscript) form. It is the
//                     center of the syllable, it can be souranded by subjoined consonants, vowels,
//                     signs... but there is only one base in a stack, it has to be coded as
//                     the first character of the syllable.Included here are also groups of base + subjoined
//                                                                               which are represented by one single code point in unicode (e.g. 0F43) Also other characters that might take
//                     subjoined consonants or other combining characters.
//     Subjoined ->    Subjoined consonants and groups of subjoined consonants which have a single code-point
//                     to repersent the group (even if each subjoined consonant is represented independently
//                     by anothe code-point
//     Tsa Phru -->    Tsa Phru character, Bhutanese people will always place it right after the base, but sometimes, due to
//                                                                              "normalization"
//                                                                               is placed after all the subjoined consonants, and it is also permitted there.
//     A Chung  Vowel lengthening mark --> . 0F71 It is placed after the base and any subjoined consonants but before any vowels
//     Precomposed Sanskrit vowels --> The are combinations of subjoined consonants + vowels that have been assigned
//                     a given code-point (in spite of each single part of them having also a code-point
//                     They are avoided, and users are encouraged to use the combination of code-points that
//                     represents the same sound instead of using this combined characters. This is included here
//                     for compatibility with possible texts that use them (they are not in the Dzongkha keyboard).
//     Halanta ->      The Halanta or Virama character 0F84 indicates that a consonant should not use its inheernt vowel,
//                     in spite of not having other vowels present. It is usually placed immediatly after a base consonant,
//                     but in some special cases it can also be placed after a subjoined consonant, so this is also
//                     permitted in this algorithm. (Halanta is always displayed in Tibetan not used as a connecting char)
//
//     Subjoined vowels -> Dependent vowels (matras) placed below the base and below all subjoined consonants. There
//                     might be as much as three subjoined vowels in a given stack (only one in general text, but up
//                     to three for abreviations, they have to be permitted).
//     Superscript vowels -> There are three superscript vowels, and they can be repeated or combined (up to three
//                     times. They can combine with subjoined vowels, and are always coded after these.
//     Anusvara -->    Nasalisation sign. Traditioinally placed in absence of vowels, but also after vowels. In some
//                     special cases it can be placed before a vowel, so this is also permitted
//     Candrabindu ->  Forms of the Anusvara with different glyphs (and different in identity) which can be placed
//                     without vowel or after the vowel, but never before. Cannot combine with Anusvara.
//     Stress marks -> Marks placed above or below a syllable, affecting the whole syllable. They are combining
//                     marks, so they have to be attached to a specific stack. The are using to emphasise a syllable.
//
//     Digits ->       Digits are not considered as non-combining characters because there are a few characters which
//                     combine with them, so they have to be considered independently.
//     Digit combining marks -> dependent marks that combine with digits.
//
//     TODO
//     There are a number of characters in the CJK block that are used in Tibetan script, two of these are symbols
//     are used as bases for combining glyphs, and have not been encoded in Tibetan. As these characters are outside
//     of the tibetan block, they have not been treated in this program.


struct TibetanClassTable    // This list must include all types of components that can be used inside a syllable
{
    enum CharClassValues  // order is important here! This order must be the same that is found in each horizontal
                          // line in the statetable for Tibetan (file TibetanReordering.cpp). It assigns one number
                          // to each type of character that has to be considered when analysing the order in which
                          // characters can be placed
    {
        CC_RESERVED             =  0, //Non Combining Characters
        CC_BASE                 =  1, // Base Consonants, Base Consonants with Subjoined attached in code point, Sanskrit base marks
        CC_SUBJOINED            =  2, // Subjoined Consonats, combination of more than Subjoined Consonants in the code point
        CC_TSA_PHRU             =  3, // Tsa-Phru character 0F39
        CC_A_CHUNG              =  4, // Vowel Lenthening a-chung mark 0F71
        CC_COMP_SANSKRIT        =  5, // Precomposed Sanskrit vowels including Subjoined characters and vowels
        CC_HALANTA              =  6, // Halanta Character 0F84
        CC_BELOW_VOWEL          =  7, // Subjoined vowels
        CC_ABOVE_VOWEL          =  8, // Superscript vowels
        CC_ANUSVARA             =  9, // Tibetan sign Rjes Su Nga Ro 0F7E
        CC_CANDRABINDU          = 10, // Tibetan sign Sna Ldan and Nyi Zla Naa Da 0F82, 0F83
        CC_VISARGA              = 11, // Tibetan sign Rnam Bcad (0F7F)
        CC_ABOVE_S_MARK         = 12, // Stress Marks placed above the text
        CC_BELOW_S_MARK         = 13, // Stress Marks placed below the text
        CC_DIGIT                = 14, // Dzongkha Digits
        CC_PRE_DIGIT_MARK       = 15, // Mark placed before the digit
        CC_POST_BELOW_DIGIT_M   = 16, // Mark placed below or after the digit
        CC_COUNT                = 17  // This is the number of character classes
    };

    enum CharClassFlags
    {
        CF_CLASS_MASK    = 0x0000FFFF,

        CF_DOTTED_CIRCLE = 0x04000000,  // add a dotted circle if a character with this flag is the first in a syllable
        CF_DIGIT         = 0x01000000,  // flag to speed up comparaisson
        CF_PREDIGIT      = 0x02000000,  // flag to detect pre-digit marks for reordering

        // position flags
        CF_POS_BEFORE    = 0x00080000,
        CF_POS_BELOW     = 0x00040000,
        CF_POS_ABOVE     = 0x00020000,
        CF_POS_AFTER     = 0x00010000,
        CF_POS_MASK      = 0x000f0000
    };

    typedef le_uint32 CharClass;

    typedef le_int32 ScriptFlags;

    LEUnicode firstChar;   // for Tibetan this will become xOF00
    LEUnicode lastChar;    //  and this x0FFF
    const CharClass *classTable;

    CharClass getCharClass(LEUnicode ch) const;

    static const TibetanClassTable *getTibetanClassTable();
};


class TibetanReordering /* not : public UObject because all methods are static */ {
public:
    static le_int32 reorder(const LEUnicode *theChars, le_int32 charCount, le_int32 scriptCode,
        LEUnicode *outChars, LEGlyphStorage &glyphStorage);

    static const FeatureMap *getFeatureMap(le_int32 &count);

private:
    // do not instantiate
    TibetanReordering();

    static le_int32 findSyllable(const TibetanClassTable *classTable, const LEUnicode *chars, le_int32 prev, le_int32 charCount);

};


U_NAMESPACE_END
#endif
