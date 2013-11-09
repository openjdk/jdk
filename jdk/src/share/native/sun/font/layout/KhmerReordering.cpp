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
 * (C) Copyright IBM Corp. 1998-2007 - All Rights Reserved
 *
 * This file is a modification of the ICU file IndicReordering.cpp
 * by Jens Herden and Javier Sola for Khmer language
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "KhmerReordering.h"
#include "LEGlyphStorage.h"


U_NAMESPACE_BEGIN

// Characters that get referred to by name...
enum
{
    C_SIGN_ZWNJ     = 0x200C,
    C_SIGN_ZWJ      = 0x200D,
    C_DOTTED_CIRCLE = 0x25CC,
    C_RO            = 0x179A,
    C_VOWEL_AA      = 0x17B6,
    C_SIGN_NIKAHIT  = 0x17C6,
    C_VOWEL_E       = 0x17C1,
    C_COENG         = 0x17D2
};


enum
{
    // simple classes, they are used in the statetable (in this file) to control the length of a syllable
    // they are also used to know where a character should be placed (location in reference to the base character)
    // and also to know if a character, when independtly displayed, should be displayed with a dotted-circle to
    // indicate error in syllable construction
    _xx = KhmerClassTable::CC_RESERVED,
    _sa = KhmerClassTable::CC_SIGN_ABOVE | KhmerClassTable::CF_DOTTED_CIRCLE | KhmerClassTable::CF_POS_ABOVE,
    _sp = KhmerClassTable::CC_SIGN_AFTER | KhmerClassTable::CF_DOTTED_CIRCLE| KhmerClassTable::CF_POS_AFTER,
    _c1 = KhmerClassTable::CC_CONSONANT | KhmerClassTable::CF_CONSONANT,
    _c2 = KhmerClassTable::CC_CONSONANT2 | KhmerClassTable::CF_CONSONANT,
    _c3 = KhmerClassTable::CC_CONSONANT3 | KhmerClassTable::CF_CONSONANT,
    _rb = KhmerClassTable::CC_ROBAT | KhmerClassTable::CF_POS_ABOVE | KhmerClassTable::CF_DOTTED_CIRCLE,
    _cs = KhmerClassTable::CC_CONSONANT_SHIFTER | KhmerClassTable::CF_DOTTED_CIRCLE | KhmerClassTable::CF_SHIFTER,
    _dl = KhmerClassTable::CC_DEPENDENT_VOWEL | KhmerClassTable::CF_POS_BEFORE | KhmerClassTable::CF_DOTTED_CIRCLE,
    _db = KhmerClassTable::CC_DEPENDENT_VOWEL | KhmerClassTable::CF_POS_BELOW | KhmerClassTable::CF_DOTTED_CIRCLE,
    _da = KhmerClassTable::CC_DEPENDENT_VOWEL | KhmerClassTable::CF_POS_ABOVE | KhmerClassTable::CF_DOTTED_CIRCLE | KhmerClassTable::CF_ABOVE_VOWEL,
    _dr = KhmerClassTable::CC_DEPENDENT_VOWEL | KhmerClassTable::CF_POS_AFTER | KhmerClassTable::CF_DOTTED_CIRCLE,
    _co = KhmerClassTable::CC_COENG | KhmerClassTable::CF_COENG | KhmerClassTable::CF_DOTTED_CIRCLE,

    // split vowel
    _va = _da | KhmerClassTable::CF_SPLIT_VOWEL,
    _vr = _dr | KhmerClassTable::CF_SPLIT_VOWEL
};


// Character class tables
// _xx character does not combine into syllable, such as numbers, puntuation marks, non-Khmer signs...
// _sa Sign placed above the base
// _sp Sign placed after the base
// _c1 Consonant of type 1 or independent vowel (independent vowels behave as type 1 consonants)
// _c2 Consonant of type 2 (only RO)
// _c3 Consonant of type 3
// _rb Khmer sign robat u17CC. combining mark for subscript consonants
// _cd Consonant-shifter
// _dl Dependent vowel placed before the base (left of the base)
// _db Dependent vowel placed below the base
// _da Dependent vowel placed above the base
// _dr Dependent vowel placed behind the base (right of the base)
// _co Khmer combining mark COENG u17D2, combines with the consonant or independent vowel following
//     it to create a subscript consonant or independent vowel
// _va Khmer split vowel in wich the first part is before the base and the second one above the base
// _vr Khmer split vowel in wich the first part is before the base and the second one behind (right of) the base

static const KhmerClassTable::CharClass khmerCharClasses[] =
{
    _c1, _c1, _c1, _c3, _c1, _c1, _c1, _c1, _c3, _c1, _c1, _c1, _c1, _c3, _c1, _c1, // 1780 - 178F
    _c1, _c1, _c1, _c1, _c3, _c1, _c1, _c1, _c1, _c3, _c2, _c1, _c1, _c1, _c3, _c3, // 1790 - 179F
    _c1, _c3, _c1, _c1, _c1, _c1, _c1, _c1, _c1, _c1, _c1, _c1, _c1, _c1, _c1, _c1, // 17A0 - 17AF
    _c1, _c1, _c1, _c1, _dr, _dr, _dr, _da, _da, _da, _da, _db, _db, _db, _va, _vr, // 17B0 - 17BF
    _vr, _dl, _dl, _dl, _vr, _vr, _sa, _sp, _sp, _cs, _cs, _sa, _rb, _sa, _sa, _sa, // 17C0 - 17CF
    _sa, _sa, _co, _sa, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _sa, _xx, _xx, // 17D0 - 17DF
};


//
// Khmer Class Tables
//

//
// The range of characters defined in the above table is defined here. FOr Khmer 1780 to 17DF
// Even if the Khmer range is bigger, all other characters are not combinable, and therefore treated
// as _xx
static const KhmerClassTable khmerClassTable = {0x1780, 0x17df, khmerCharClasses};


// Below we define how a character in the input string is either in the khmerCharClasses table
// (in which case we get its type back), a ZWJ or ZWNJ (two characters that may appear
// within the syllable, but are not in the table) we also get their type back, or an unknown object
// in which case we get _xx (CC_RESERVED) back
KhmerClassTable::CharClass KhmerClassTable::getCharClass(LEUnicode ch) const
{

    if (ch == C_SIGN_ZWJ) {
        return CC_ZERO_WIDTH_J_MARK;
    }

    if (ch == C_SIGN_ZWNJ) {
        return CC_ZERO_WIDTH_NJ_MARK;
    }

    if (ch < firstChar || ch > lastChar) {
        return CC_RESERVED;
    }

    return classTable[ch - firstChar];
}

const KhmerClassTable *KhmerClassTable::getKhmerClassTable()
{
    return &khmerClassTable;
}



class KhmerReorderingOutput : public UMemory {
private:
    le_int32 fSyllableCount;
    le_int32 fOutIndex;
    LEUnicode *fOutChars;

    LEGlyphStorage &fGlyphStorage;


public:
    KhmerReorderingOutput(LEUnicode *outChars, LEGlyphStorage &glyphStorage)
        : fSyllableCount(0), fOutIndex(0), fOutChars(outChars), fGlyphStorage(glyphStorage)
    {
        // nothing else to do...
    }

    ~KhmerReorderingOutput()
    {
        // nothing to do here...
    }

    void reset()
    {
        fSyllableCount += 1;
    }

    void writeChar(LEUnicode ch, le_uint32 charIndex, FeatureMask charFeatures)
    {
        LEErrorCode success = LE_NO_ERROR;

        fOutChars[fOutIndex] = ch;

        fGlyphStorage.setCharIndex(fOutIndex, charIndex, success);
        fGlyphStorage.setAuxData(fOutIndex, charFeatures | (fSyllableCount & LE_GLYPH_GROUP_MASK), success);

        fOutIndex += 1;
    }

    le_int32 getOutputIndex()
    {
        return fOutIndex;
    }
};


#define blwfFeatureTag LE_BLWF_FEATURE_TAG
#define pstfFeatureTag LE_PSTF_FEATURE_TAG
#define presFeatureTag LE_PRES_FEATURE_TAG
#define blwsFeatureTag LE_BLWS_FEATURE_TAG
#define abvsFeatureTag LE_ABVS_FEATURE_TAG
#define pstsFeatureTag LE_PSTS_FEATURE_TAG

#define blwmFeatureTag LE_BLWM_FEATURE_TAG
#define abvmFeatureTag LE_ABVM_FEATURE_TAG
#define distFeatureTag LE_DIST_FEATURE_TAG

#define prefFeatureTag LE_PREF_FEATURE_TAG
#define abvfFeatureTag LE_ABVF_FEATURE_TAG
#define cligFeatureTag LE_CLIG_FEATURE_TAG
#define mkmkFeatureTag LE_MKMK_FEATURE_TAG

#define prefFeatureMask 0x80000000UL
#define blwfFeatureMask 0x40000000UL
#define abvfFeatureMask 0x20000000UL
#define pstfFeatureMask 0x10000000UL
#define presFeatureMask 0x08000000UL
#define blwsFeatureMask 0x04000000UL
#define abvsFeatureMask 0x02000000UL
#define pstsFeatureMask 0x01000000UL
#define cligFeatureMask 0x00800000UL
#define distFeatureMask 0x00400000UL
#define blwmFeatureMask 0x00200000UL
#define abvmFeatureMask 0x00100000UL
#define mkmkFeatureMask 0x00080000UL

#define tagPref    (prefFeatureMask | presFeatureMask | cligFeatureMask | distFeatureMask)
#define tagAbvf    (abvfFeatureMask | abvsFeatureMask | cligFeatureMask | distFeatureMask | abvmFeatureMask | mkmkFeatureMask)
#define tagPstf    (blwfFeatureMask | blwsFeatureMask | prefFeatureMask | presFeatureMask | pstfFeatureMask | pstsFeatureMask | cligFeatureMask | distFeatureMask | blwmFeatureMask)
#define tagBlwf    (blwfFeatureMask | blwsFeatureMask | cligFeatureMask | distFeatureMask | blwmFeatureMask | mkmkFeatureMask)
#define tagDefault (prefFeatureMask | blwfFeatureMask | presFeatureMask | blwsFeatureMask | cligFeatureMask | distFeatureMask | abvmFeatureMask | blwmFeatureMask | mkmkFeatureMask)



// These are in the order in which the features need to be applied
// for correct processing
static const FeatureMap featureMap[] =
{
    // Shaping features
    {prefFeatureTag, prefFeatureMask},
    {blwfFeatureTag, blwfFeatureMask},
    {abvfFeatureTag, abvfFeatureMask},
    {pstfFeatureTag, pstfFeatureMask},
    {presFeatureTag, presFeatureMask},
    {blwsFeatureTag, blwsFeatureMask},
    {abvsFeatureTag, abvsFeatureMask},
    {pstsFeatureTag, pstsFeatureMask},
    {cligFeatureTag, cligFeatureMask},

    // Positioning features
    {distFeatureTag, distFeatureMask},
    {blwmFeatureTag, blwmFeatureMask},
    {abvmFeatureTag, abvmFeatureMask},
    {mkmkFeatureTag, mkmkFeatureMask},
};

static const le_int32 featureMapCount = LE_ARRAY_SIZE(featureMap);

// The stateTable is used to calculate the end (the length) of a well
// formed Khmer Syllable.
//
// Each horizontal line is ordered exactly the same way as the values in KhmerClassTable
// CharClassValues in KhmerReordering.h This coincidence of values allows the
// follow up of the table.
//
// Each line corresponds to a state, which does not necessarily need to be a type
// of component... for example, state 2 is a base, with is always a first character
// in the syllable, but the state could be produced a consonant of any type when
// it is the first character that is analysed (in ground state).
//
// Differentiating 3 types of consonants is necessary in order to
// forbid the use of certain combinations, such as having a second
// coeng after a coeng RO,
// The inexistent possibility of having a type 3 after another type 3 is permitted,
// eliminating it would very much complicate the table, and it does not create typing
// problems, as the case above.
//
// The table is quite complex, in order to limit the number of coeng consonants
// to 2 (by means of the table).
//
// There a peculiarity, as far as Unicode is concerned:
// - The consonant-shifter is considered in two possible different
//   locations, the one considered in Unicode 3.0 and the one considered in
//   Unicode 4.0. (there is a backwards compatibility problem in this standard).


// xx    independent character, such as a number, punctuation sign or non-khmer char
//
// c1    Khmer consonant of type 1 or an independent vowel
//       that is, a letter in which the subscript for is only under the
//       base, not taking any space to the right or to the left
//
// c2    Khmer consonant of type 2, the coeng form takes space under
//       and to the left of the base (only RO is of this type)
//
// c3    Khmer consonant of type 3. Its subscript form takes space under
//       and to the right of the base.
//
// cs    Khmer consonant shifter
//
// rb    Khmer robat
//
// co    coeng character (u17D2)
//
// dv    dependent vowel (including split vowels, they are treated in the same way).
//       even if dv is not defined above, the component that is really tested for is
//       KhmerClassTable::CC_DEPENDENT_VOWEL, which is common to all dependent vowels
//
// zwj   Zero Width joiner
//
// zwnj  Zero width non joiner
//
// sa    above sign
//
// sp    post sign
//
// there are lines with equal content but for an easier understanding
// (and maybe change in the future) we did not join them
//
static const le_int8 khmerStateTable[][KhmerClassTable::CC_COUNT] =
{

//   xx  c1  c2  c3 zwnj cs  rb  co  dv  sa  sp zwj
    { 1,  2,  2,  2,  1,  1,  1,  6,  1,  1,  1,  2}, //  0 - ground state
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, //  1 - exit state (or sign to the right of the syllable)
    {-1, -1, -1, -1,  3,  4,  5,  6, 16, 17,  1, -1}, //  2 - Base consonant
    {-1, -1, -1, -1, -1,  4, -1, -1, 16, -1, -1, -1}, //  3 - First ZWNJ before a register shifter
                                                      //      It can only be followed by a shifter or a vowel
    {-1, -1, -1, -1, 15, -1, -1,  6, 16, 17,  1, 14}, //  4 - First register shifter
    {-1, -1, -1, -1, -1, -1, -1, -1, 20, -1,  1, -1}, //  5 - Robat
    {-1,  7,  8,  9, -1, -1, -1, -1, -1, -1, -1, -1}, //  6 - First Coeng
    {-1, -1, -1, -1, 12, 13, -1, 10, 16, 17,  1, 14}, //  7 - First consonant of type 1 after coeng
    {-1, -1, -1, -1, 12, 13, -1, -1, 16, 17,  1, 14}, //  8 - First consonant of type 2 after coeng
    {-1, -1, -1, -1, 12, 13, -1, 10, 16, 17,  1, 14}, //  9 - First consonant or type 3 after ceong
    {-1, 11, 11, 11, -1, -1, -1, -1, -1, -1, -1, -1}, // 10 - Second Coeng (no register shifter before)
    {-1, -1, -1, -1, 15, -1, -1, -1, 16, 17,  1, 14}, // 11 - Second coeng consonant (or ind. vowel) no register shifter before
    {-1, -1, -1, -1, -1, 13, -1, -1, 16, -1, -1, -1}, // 12 - Second ZWNJ before a register shifter
    {-1, -1, -1, -1, 15, -1, -1, -1, 16, 17,  1, 14}, // 13 - Second register shifter
    {-1, -1, -1, -1, -1, -1, -1, -1, 16, -1, -1, -1}, // 14 - ZWJ before vowel
    {-1, -1, -1, -1, -1, -1, -1, -1, 16, -1, -1, -1}, // 15 - ZWNJ before vowel
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, 17,  1, 18}, // 16 - dependent vowel
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  1, 18}, // 17 - sign above
    {-1, -1, -1, -1, -1, -1, -1, 19, -1, -1, -1, -1}, // 18 - ZWJ after vowel
    {-1,  1, -1,  1, -1, -1, -1, -1, -1, -1, -1, -1}, // 19 - Third coeng
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  1, -1}, // 20 - dependent vowel after a Robat

};


const FeatureMap *KhmerReordering::getFeatureMap(le_int32 &count)
{
    count = featureMapCount;

    return featureMap;
}


// Given an input string of characters and a location in which to start looking
// calculate, using the state table, which one is the last character of the syllable
// that starts in the starting position.
le_int32 KhmerReordering::findSyllable(const KhmerClassTable *classTable, const LEUnicode *chars, le_int32 prev, le_int32 charCount)
{
    le_int32 cursor = prev;
    le_int8 state = 0;

    while (cursor < charCount) {
        KhmerClassTable::CharClass charClass = (classTable->getCharClass(chars[cursor]) & KhmerClassTable::CF_CLASS_MASK);

        state = khmerStateTable[state][charClass];

        if (state < 0) {
            break;
        }

        cursor += 1;
    }

    return cursor;
}


// This is the real reordering function as applied to the Khmer language

le_int32 KhmerReordering::reorder(const LEUnicode *chars, le_int32 charCount, le_int32 /*scriptCode*/,
                                  LEUnicode *outChars, LEGlyphStorage &glyphStorage)
{
    const KhmerClassTable *classTable = KhmerClassTable::getKhmerClassTable();

    KhmerReorderingOutput output(outChars, glyphStorage);
    KhmerClassTable::CharClass charClass;
    le_int32 i, prev = 0, coengRo;


    // This loop only exits when we reach the end of a run, which may contain
    // several syllables.
    while (prev < charCount) {
        le_int32 syllable = findSyllable(classTable, chars, prev, charCount);

        output.reset();

        // write a pre vowel or the pre part of a split vowel first
        // and look out for coeng + ro. RO is the only vowel of type 2, and
        // therefore the only one that requires saving space before the base.
        coengRo = -1;  // There is no Coeng Ro, if found this value will change
        for (i = prev; i < syllable; i += 1) {
            charClass = classTable->getCharClass(chars[i]);

            // if a split vowel, write the pre part. In Khmer the pre part
            // is the same for all split vowels, same glyph as pre vowel C_VOWEL_E
            if (charClass & KhmerClassTable::CF_SPLIT_VOWEL) {
                output.writeChar(C_VOWEL_E, i, tagPref);
                break; // there can be only one vowel
            }

            // if a vowel with pos before write it out
            if (charClass & KhmerClassTable::CF_POS_BEFORE) {
                output.writeChar(chars[i], i, tagPref);
                break; // there can be only one vowel
            }

            // look for coeng + ro and remember position
            // works because coeng + ro is always in front of a vowel (if there is a vowel)
            // and because CC_CONSONANT2 is enough to identify it, as it is the only consonant
            // with this flag
            if ( (charClass & KhmerClassTable::CF_COENG) && (i + 1 < syllable) &&
                 ( (classTable->getCharClass(chars[i + 1]) & KhmerClassTable::CF_CLASS_MASK) == KhmerClassTable::CC_CONSONANT2) )
            {
                    coengRo = i;
            }
        }

        // write coeng + ro if found
        if (coengRo > -1) {
            output.writeChar(C_COENG, coengRo, tagPref);
            output.writeChar(C_RO, coengRo + 1, tagPref);
        }

        // shall we add a dotted circle?
        // If in the position in which the base should be (first char in the string) there is
        // a character that has the Dotted circle flag (a character that cannot be a base)
        // then write a dotted circle
        if (classTable->getCharClass(chars[prev]) & KhmerClassTable::CF_DOTTED_CIRCLE) {
            output.writeChar(C_DOTTED_CIRCLE, prev, tagDefault);
        }

        // copy what is left to the output, skipping before vowels and coeng Ro if they are present
        for (i = prev; i < syllable; i += 1) {
            charClass = classTable->getCharClass(chars[i]);

            // skip a before vowel, it was already processed
            if (charClass & KhmerClassTable::CF_POS_BEFORE) {
                continue;
            }

            // skip coeng + ro, it was already processed
            if (i == coengRo) {
                i += 1;
                continue;
            }

            switch (charClass & KhmerClassTable::CF_POS_MASK) {
                case KhmerClassTable::CF_POS_ABOVE :
                    output.writeChar(chars[i], i, tagAbvf);
                    break;

                case KhmerClassTable::CF_POS_AFTER :
                    output.writeChar(chars[i], i, tagPstf);
                    break;

                case KhmerClassTable::CF_POS_BELOW :
                    output.writeChar(chars[i], i, tagBlwf);
                    break;

                default:
                    // assign the correct flags to a coeng consonant
                    // Consonants of type 3 are taged as Post forms and those type 1 as below forms
                    if ( (charClass & KhmerClassTable::CF_COENG) && i + 1 < syllable ) {
                        if ( (classTable->getCharClass(chars[i + 1]) & KhmerClassTable::CF_CLASS_MASK)
                              == KhmerClassTable::CC_CONSONANT3) {
                            output.writeChar(chars[i], i, tagPstf);
                            i += 1;
                            output.writeChar(chars[i], i, tagPstf);
                        }
                        else {
                            output.writeChar(chars[i], i, tagBlwf);
                            i += 1;
                            output.writeChar(chars[i], i, tagBlwf);
                        }
                        break;
                    }
                    // if a shifter is followed by an above vowel change the shifter to below form,
                    // an above vowel can have two possible positions i + 1 or i + 3
                    // (position i+1 corresponds to unicode 3, position i+3 to Unicode 4)
                    // and there is an extra rule for C_VOWEL_AA + C_SIGN_NIKAHIT also for two
                    // different positions, right after the shifter or after a vowel (Unicode 4)
                    if ( (charClass & KhmerClassTable::CF_SHIFTER) && (i + 1 < syllable) ) {
                        if ((classTable->getCharClass(chars[i + 1]) & KhmerClassTable::CF_ABOVE_VOWEL)
                            || (i + 2 < syllable
                                && ( (classTable->getCharClass(chars[i + 1]) & KhmerClassTable::CF_CLASS_MASK) == C_VOWEL_AA)
                                && ( (classTable->getCharClass(chars[i + 2]) & KhmerClassTable::CF_CLASS_MASK) == C_SIGN_NIKAHIT))
                            || (i + 3 < syllable && (classTable->getCharClass(chars[i + 3]) & KhmerClassTable::CF_ABOVE_VOWEL))
                            || (i + 4 < syllable
                                && ( (classTable->getCharClass(chars[i + 3]) & KhmerClassTable::CF_CLASS_MASK) == C_VOWEL_AA)
                                && ( (classTable->getCharClass(chars[i + 4]) & KhmerClassTable::CF_CLASS_MASK) == C_SIGN_NIKAHIT) ) )
                        {
                            output.writeChar(chars[i], i, tagBlwf);
                            break;
                        }

                    }
                    // default - any other characters
                    output.writeChar(chars[i], i, tagDefault);
                    break;
            } // switch
        } // for

        prev = syllable; // move the pointer to the start of next syllable
    }

    return output.getOutputIndex();
}


U_NAMESPACE_END
