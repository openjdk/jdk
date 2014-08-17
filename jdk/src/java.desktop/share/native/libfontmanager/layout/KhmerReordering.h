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
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 * This file is a modification of the ICU file IndicReordering.h
 * by Jens Herden and Javier Sola for Khmer language
 *
 */

#ifndef __KHMERREORDERING_H
#define __KHMERREORDERING_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

class LEGlyphStorage;

// Vocabulary
//     Base ->         A consonant or an independent vowel in its full (not subscript) form. It is the
//                     center of the syllable, it can be souranded by coeng (subscript) consonants, vowels,
//                     split vowels, signs... but there is only one base in a syllable, it has to be coded as
//                     the first character of the syllable.
//     split vowel --> vowel that has two parts placed separately (e.g. Before and after the consonant).
//                     Khmer language has five of them. Khmer split vowels either have one part before the
//                     base and one after the base or they have a part before the base and a part above the base.
//                     The first part of all Khmer split vowels is the same character, identical to
//                     the glyph of Khmer dependent vowel SRA EI
//     coeng -->  modifier used in Khmer to construct coeng (subscript) consonants
//                Differently than indian languages, the coeng modifies the consonant that follows it,
//                not the one preceding it  Each consonant has two forms, the base form and the subscript form
//                the base form is the normal one (using the consonants code-point), the subscript form is
//                displayed when the combination coeng + consonant is encountered.
//     Consonant of type 1 -> A consonant which has subscript for that only occupies space under a base consonant
//     Consonant of type 2.-> Its subscript form occupies space under and before the base (only one, RO)
//     Consonant of Type 3 -> Its subscript form occupies space under and after the base (KHO, CHHO, THHO, BA, YO, SA)
//     Consonant shifter -> Khmer has to series of consonants. The same dependent vowel has different sounds
//                          if it is attached to a consonant of the first series or a consonant of the second series
//                          Most consonants have an equivalent in the other series, but some of theme exist only in
//                          one series (for example SA). If we want to use the consonant SA with a vowel sound that
//                          can only be done with a vowel sound that corresponds to a vowel accompanying a consonant
//                          of the other series, then we need to use a consonant shifter: TRIISAP or MUSIKATOAN
//                          x17C9 y x17CA. TRIISAP changes a first series consonant to second series sound and
//                          MUSIKATOAN a second series consonant to have a first series vowel sound.
//                          Consonant shifter are both normally supercript marks, but, when they are followed by a
//                          superscript, they change shape and take the form of subscript dependent vowel SRA U.
//                          If they are in the same syllable as a coeng consonant, Unicode 3.0 says that they
//                          should be typed before the coeng. Unicode 4.0 breaks the standard and says that it should
//                          be placed after the coeng consonant.
//     Dependent vowel ->   In khmer dependent vowels can be placed above, below, before or after the base
//                          Each vowel has its own position. Only one vowel per syllable is allowed.
//     Signs            ->  Khmer has above signs and post signs. Only one above sign and/or one post sign are
//                          Allowed in a syllable.
//
//

struct KhmerClassTable    // This list must include all types of components that can be used inside a syllable
{
    enum CharClassValues  // order is important here! This order must be the same that is found in each horizontal
                          // line in the statetable for Khmer (file KhmerReordering.cpp).
    {
        CC_RESERVED             =  0,
        CC_CONSONANT            =  1, // consonant of type 1 or independent vowel
        CC_CONSONANT2           =  2, // Consonant of type 2
        CC_CONSONANT3           =  3, // Consonant of type 3
        CC_ZERO_WIDTH_NJ_MARK   =  4, // Zero Width non joiner character (0x200C)
        CC_CONSONANT_SHIFTER    =  5,
        CC_ROBAT                =  6, // Khmer special diacritic accent -treated differently in state table
        CC_COENG                =  7, // Subscript consonant combining character
        CC_DEPENDENT_VOWEL      =  8,
        CC_SIGN_ABOVE           =  9,
        CC_SIGN_AFTER           = 10,
        CC_ZERO_WIDTH_J_MARK    = 11, // Zero width joiner character
        CC_COUNT                = 12  // This is the number of character classes
    };

    enum CharClassFlags
    {
        CF_CLASS_MASK    = 0x0000FFFF,

        CF_CONSONANT     = 0x01000000,  // flag to speed up comparing
        CF_SPLIT_VOWEL   = 0x02000000,  // flag for a split vowel -> the first part is added in front of the syllable
        CF_DOTTED_CIRCLE = 0x04000000,  // add a dotted circle if a character with this flag is the first in a syllable
        CF_COENG         = 0x08000000,  // flag to speed up comparing
        CF_SHIFTER       = 0x10000000,  // flag to speed up comparing
        CF_ABOVE_VOWEL   = 0x20000000,  // flag to speed up comparing

        // position flags
        CF_POS_BEFORE    = 0x00080000,
        CF_POS_BELOW     = 0x00040000,
        CF_POS_ABOVE     = 0x00020000,
        CF_POS_AFTER     = 0x00010000,
        CF_POS_MASK      = 0x000f0000
    };

    typedef le_uint32 CharClass;

    typedef le_int32 ScriptFlags;

    LEUnicode firstChar;   // for Khmer this will become x1780
    LEUnicode lastChar;    //  and this x17DF
    const CharClass *classTable;

    CharClass getCharClass(LEUnicode ch) const;

    static const KhmerClassTable *getKhmerClassTable();
};


class KhmerReordering /* not : public UObject because all methods are static */ {
public:
    static le_int32 reorder(const LEUnicode *theChars, le_int32 charCount, le_int32 scriptCode,
        LEUnicode *outChars, LEGlyphStorage &glyphStorage);

    static const FeatureMap *getFeatureMap(le_int32 &count);

private:
    // do not instantiate
    KhmerReordering();

    static le_int32 findSyllable(const KhmerClassTable *classTable, const LEUnicode *chars, le_int32 prev, le_int32 charCount);

};


U_NAMESPACE_END
#endif
