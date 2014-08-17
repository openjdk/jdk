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
 * This file is a modification of the ICU file KhmerReordering.cpp
 * by Jens Herden and Javier Sola who have given all their possible rights to IBM and the Governement of Bhutan
 * A first module for Dzongkha was developed by Karunakar under Panlocalisation funding.
 * Assistance for this module has been received from Namgay Thinley, Christopher Fynn and Javier Sola
 *
 */

//#include <stdio.h>
#include "LETypes.h"
#include "OpenTypeTables.h"
#include "TibetanReordering.h"
#include "LEGlyphStorage.h"


U_NAMESPACE_BEGIN

// Characters that get referred to by name...
enum
{
    C_DOTTED_CIRCLE = 0x25CC,
    C_PRE_NUMBER_MARK = 0x0F3F
 };


enum
{
    // simple classes, they are used in the statetable (in this file) to control the length of a syllable
    // they are also used to know where a character should be placed (location in reference to the base character)
    // and also to know if a character, when independtly displayed, should be displayed with a dotted-circle to
    // indicate error in syllable construction
    _xx = TibetanClassTable::CC_RESERVED,
    _ba = TibetanClassTable::CC_BASE,
    _sj = TibetanClassTable::CC_SUBJOINED | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_BELOW,
    _tp = TibetanClassTable::CC_TSA_PHRU  | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_ABOVE,
    _ac = TibetanClassTable::CC_A_CHUNG |  TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_BELOW,
    _cs = TibetanClassTable::CC_COMP_SANSKRIT | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_BELOW,
    _ha = TibetanClassTable::CC_HALANTA | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_BELOW,
    _bv = TibetanClassTable::CC_BELOW_VOWEL | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_BELOW,
    _av = TibetanClassTable::CC_ABOVE_VOWEL | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_ABOVE,
    _an = TibetanClassTable::CC_ANUSVARA | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_ABOVE,
    _cb = TibetanClassTable::CC_CANDRABINDU | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_ABOVE,
    _vs = TibetanClassTable::CC_VISARGA | TibetanClassTable::CF_DOTTED_CIRCLE| TibetanClassTable::CF_POS_AFTER,
    _as = TibetanClassTable::CC_ABOVE_S_MARK | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_ABOVE,
    _bs = TibetanClassTable::CC_BELOW_S_MARK | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_BELOW,
    _di = TibetanClassTable::CC_DIGIT | TibetanClassTable::CF_DIGIT,
    _pd = TibetanClassTable::CC_PRE_DIGIT_MARK | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_PREDIGIT | TibetanClassTable::CF_POS_BEFORE ,
    _bd = TibetanClassTable::CC_POST_BELOW_DIGIT_M | TibetanClassTable::CF_DOTTED_CIRCLE | TibetanClassTable::CF_POS_AFTER
};


// Character class tables
//_xx Non Combining characters
//_ba Base Consonants
//_sj Subjoined consonants
//_tp Tsa - phru
//_ac A-chung, Vowel Lengthening mark
//_cs Precomposed Sanskrit vowel + subjoined consonants
//_ha Halanta/Virama
//_bv Below vowel
//_av above vowel
//_an Anusvara
//_cb Candrabindu
//_vs Visaraga/Post mark
//_as Upper Stress marks
//_bs Lower Stress marks
//_di Digit
//_pd Number pre combining, Needs reordering
//_bd Other number combining marks

static const TibetanClassTable::CharClass tibetanCharClasses[] =
{
   // 0    1    2    3    4    5    6    7    8    9   a     b   c    d     e   f
    _xx, _ba, _xx, _xx, _ba, _ba, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0F00 - 0F0F 0
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _bd, _bd, _xx, _xx, _xx, _xx, _xx, _xx, // 0F10 - 0F1F 1
    _di, _di, _di, _di, _di, _di, _di, _di, _di, _di, _xx, _xx, _xx, _xx, _xx, _xx, // 0F20 - 0F2F 2
    _xx, _xx, _xx, _xx, _xx, _bs, _xx, _bs, _xx, _tp, _xx, _xx, _xx, _xx, _bd, _pd, // 0F30 - 0F3F 3
    _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _xx, _ba, _ba, _ba, _ba, _ba, _ba, _ba, // 0F40 - 0F4F 4
    _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, // 0F50 - 0F5F 5
    _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _ba, _xx, _xx, _xx, _xx, _xx, // 0F60 - 0F6F 6
    _xx, _ac, _av, _cs, _bv, _bv, _cs, _cs, _cs, _cs, _av, _av, _av, _av, _an, _vs, // 0F70 - 0F7F 7
    _av, _cs, _cb, _cb, _ha, _xx, _as, _as, _ba, _ba, _ba, _ba, _xx, _xx, _xx, _xx, // 0F80 - 0F8F 8
    _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _xx, _sj, _sj, _sj, _sj, _sj, _sj, _sj, // 0F90 - 0F9F 9
    _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, // 0FA0 - 0FAF a
    _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _sj, _xx, _sj, _sj, // 0FB0 - 0FBF b
    _xx, _xx, _xx, _xx, _xx, _xx, _bs, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0FC0 - 0FCF c
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx,// 0FD0 - 0FDF  d
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0FE0 - 0FEF e
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0FF0 - 0FFF f
};


//
// Tibetan Class Tables
//

//
// The range of characters defined in the above table is defined here. For Tibetan 0F00 to 0FFF
// Even if the Tibetan range is bigger, most of the characters are not combinable, and therefore treated
// as _xx
static const TibetanClassTable tibetanClassTable = {0x0F00, 0x0FFF, tibetanCharClasses};


// Below we define how a character in the input string is either in the tibetanCharClasses table
// (in which case we get its type back), or an unknown object in which case we get _xx (CC_RESERVED) back
TibetanClassTable::CharClass TibetanClassTable::getCharClass(LEUnicode ch) const
{
    if (ch < firstChar || ch > lastChar) {
        return CC_RESERVED;
    }

    return classTable[ch - firstChar];
}

const TibetanClassTable *TibetanClassTable::getTibetanClassTable()
{
    return &tibetanClassTable;
}



class TibetanReorderingOutput : public UMemory {
private:
    le_int32 fSyllableCount;
    le_int32 fOutIndex;
    LEUnicode *fOutChars;

    LEGlyphStorage &fGlyphStorage;


public:
    TibetanReorderingOutput(LEUnicode *outChars, LEGlyphStorage &glyphStorage)
        : fSyllableCount(0), fOutIndex(0), fOutChars(outChars), fGlyphStorage(glyphStorage)
    {
        // nothing else to do...
    }

    ~TibetanReorderingOutput()
    {
        // nothing to do here...
    }

    void reset()
    {
        fSyllableCount += 1;
    }

    void writeChar(LEUnicode ch, le_uint32 charIndex, FeatureMask featureMask)
    {
        LEErrorCode success = LE_NO_ERROR;

        fOutChars[fOutIndex] = ch;

        fGlyphStorage.setCharIndex(fOutIndex, charIndex, success);
        fGlyphStorage.setAuxData(fOutIndex, featureMask, success);

        fOutIndex += 1;
    }

    le_int32 getOutputIndex()
    {
        return fOutIndex;
    }
};


//TODO remove unused flags
#define ccmpFeatureTag LE_CCMP_FEATURE_TAG
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

// Shaping features
#define prefFeatureMask 0x80000000UL
#define blwfFeatureMask 0x40000000UL
#define abvfFeatureMask 0x20000000UL
#define pstfFeatureMask 0x10000000UL
#define presFeatureMask 0x08000000UL
#define blwsFeatureMask 0x04000000UL
#define abvsFeatureMask 0x02000000UL
#define pstsFeatureMask 0x01000000UL
#define cligFeatureMask 0x00800000UL
#define ccmpFeatureMask 0x00040000UL

// Positioning features
#define distFeatureMask 0x00400000UL
#define blwmFeatureMask 0x00200000UL
#define abvmFeatureMask 0x00100000UL
#define mkmkFeatureMask 0x00080000UL

#define tagPref    (ccmpFeatureMask | prefFeatureMask | presFeatureMask | cligFeatureMask | distFeatureMask)
#define tagAbvf    (ccmpFeatureMask | abvfFeatureMask | abvsFeatureMask | cligFeatureMask | distFeatureMask | abvmFeatureMask | mkmkFeatureMask)
#define tagPstf    (ccmpFeatureMask | blwfFeatureMask | blwsFeatureMask | prefFeatureMask | presFeatureMask | pstfFeatureMask | pstsFeatureMask | cligFeatureMask | distFeatureMask | blwmFeatureMask)
#define tagBlwf    (ccmpFeatureMask | blwfFeatureMask | blwsFeatureMask | cligFeatureMask | distFeatureMask | blwmFeatureMask | mkmkFeatureMask)
#define tagDefault (ccmpFeatureMask | prefFeatureMask | blwfFeatureMask | presFeatureMask | blwsFeatureMask | cligFeatureMask | distFeatureMask | abvmFeatureMask | blwmFeatureMask | mkmkFeatureMask)



// These are in the order in which the features need to be applied
// for correct processing
static const FeatureMap featureMap[] =
{
    // Shaping features
    {ccmpFeatureTag, ccmpFeatureMask},
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
// formed Tibetan Syllable.
//
// Each horizontal line is ordered exactly the same way as the values in TibetanClassTable
// CharClassValues in TibetanReordering.h This coincidence of values allows the
// follow up of the table.
//
// Each line corresponds to a state, which does not necessarily need to be a type
// of component... for example, state 2 is a base, with is always a first character
// in the syllable, but the state could be produced a consonant of any type when
// it is the first character that is analysed (in ground state).
//
static const le_int8 tibetanStateTable[][TibetanClassTable::CC_COUNT] =
{


    //Dzongkha state table
    //xx  ba  sj  tp  ac  cs  ha  bv  av  an  cb  vs  as  bs  di  pd  bd
    { 1,  2,  4,  3,  8,  7,  9, 10, 14, 13, 17, 18, 19, 19, 20, 21, 21,}, //  0 - ground state
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,}, //  1 - exit state (or sign to the right of the syllable)
    {-1, -1,  4,  3,  8,  7,  9, 10, 14, 13, 17, 18, 19, 19, -1, -1, -1,}, //  2 - Base consonant
    {-1, -1,  5, -1,  8,  7, -1, 10, 14, 13, 17, 18, 19, 19, -1, -1, -1,}, //  3 - Tsa phru after base
    {-1, -1,  4,  6,  8,  7,  9, 10, 14, 13, 17, 18, 19, 19, -1, -1, -1,}, //  4 - Subjoined consonant after base
    {-1, -1,  5, -1,  8,  7, -1, 10, 14, 13, 17, 18, 19, 19, -1, -1, -1,}, //  5 - Subjoined consonant after tsa phru
    {-1, -1, -1, -1,  8,  7, -1, 10, 14, 13, 17, 18, 19, 19, -1, -1, -1,}, //  6 - Tsa phru after subjoined consonant
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 19, 19, -1, -1, -1,}, //  7 - Pre Composed Sanskrit
    {-1, -1, -1, -1, -1, -1, -1, 10, 14, 13, 17, 18, 19, 19, -1, -1, -1,}, //  8 - A-chung
    {-1, -1, -1, -1, -1, -1, -1, -1, 14, 13, 17, -1, 19, 19, -1, -1, -1,}, //  9 - Halanta
    {-1, -1, -1, -1, -1, -1, -1, 11, 14, 13, 17, 18, 19, 19, -1, -1, -1,}, // 10 - below vowel 1
    {-1, -1, -1, -1, -1, -1, -1, 12, 14, 13, 17, 18, 19, 19, -1, -1, -1,}, // 11 - below vowel 2
    {-1, -1, -1, -1, -1, -1, -1, -1, 14, 13, 17, 18, 19, 19, -1, -1, -1,}, // 12 - below vowel 3
    {-1, -1, -1, -1, -1, -1, -1, -1, 14, 17, 17, 18, 19, 19, -1, -1, -1,}, // 13 - Anusvara before vowel
    {-1, -1, -1, -1, -1, -1, -1, -1, 15, 17, 17, 18, 19, 19, -1, -1, -1,}, // 14 - above vowel 1
    {-1, -1, -1, -1, -1, -1, -1, -1, 16, 17, 17, 18, 19, 19, -1, -1, -1,}, // 15 - above vowel 2
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, 17, 17, 18, 19, 19, -1, -1, -1,}, // 16 - above vowel 3
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 18, 19, 19, -1, -1, -1,}, // 17 - Anusvara or Candrabindu after vowel
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 19, 19, -1, -1, -1,}, // 18 - Visarga
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,}, // 19 - strss mark
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 21, 21,}, // 20 - digit
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,}, // 21 - digit mark


};


const FeatureMap *TibetanReordering::getFeatureMap(le_int32 &count)
{
    count = featureMapCount;

    return featureMap;
}


// Given an input string of characters and a location in which to start looking
// calculate, using the state table, which one is the last character of the syllable
// that starts in the starting position.
le_int32 TibetanReordering::findSyllable(const TibetanClassTable *classTable, const LEUnicode *chars, le_int32 prev, le_int32 charCount)
{
    le_int32 cursor = prev;
    le_int8 state = 0;

    while (cursor < charCount) {
        TibetanClassTable::CharClass charClass = (classTable->getCharClass(chars[cursor]) & TibetanClassTable::CF_CLASS_MASK);

        state = tibetanStateTable[state][charClass];

        if (state < 0) {
            break;
        }

        cursor += 1;
    }

    return cursor;
}


// This is the real reordering function as applied to the Tibetan language

le_int32 TibetanReordering::reorder(const LEUnicode *chars, le_int32 charCount, le_int32,
                                  LEUnicode *outChars, LEGlyphStorage &glyphStorage)
{
    const TibetanClassTable *classTable = TibetanClassTable::getTibetanClassTable();

    TibetanReorderingOutput output(outChars, glyphStorage);
    TibetanClassTable::CharClass charClass;
    le_int32 i, prev = 0;

    // This loop only exits when we reach the end of a run, which may contain
    // several syllables.
    while (prev < charCount) {
        le_int32 syllable = findSyllable(classTable, chars, prev, charCount);

        output.reset();

        // shall we add a dotted circle?
        // If in the position in which the base should be (first char in the string) there is
        // a character that has the Dotted circle flag (a character that cannot be a base)
        // then write a dotted circle
        if (classTable->getCharClass(chars[prev]) & TibetanClassTable::CF_DOTTED_CIRCLE) {
            output.writeChar(C_DOTTED_CIRCLE, prev, tagDefault);
        }

        // copy the rest to output, inverting the pre-number mark if present after a digit.
        for (i = prev; i < syllable; i += 1) {
            charClass = classTable->getCharClass(chars[i]);

           if ((TibetanClassTable::CF_DIGIT & charClass)
              && ( classTable->getCharClass(chars[i+1]) & TibetanClassTable::CF_PREDIGIT))
           {
                         output.writeChar(C_PRE_NUMBER_MARK, i, tagPref);
                         output.writeChar(chars[i], i+1 , tagPref);
                        i += 1;
          } else {
            switch (charClass & TibetanClassTable::CF_POS_MASK) {

                // If the present character is a number, and the next character is a pre-number combining mark
            // then the two characters are reordered

                case TibetanClassTable::CF_POS_ABOVE :
                    output.writeChar(chars[i], i, tagAbvf);
                    break;

                case TibetanClassTable::CF_POS_AFTER :
                    output.writeChar(chars[i], i, tagPstf);
                    break;

                case TibetanClassTable::CF_POS_BELOW :
                    output.writeChar(chars[i], i, tagBlwf);
                    break;

                default:
                    // default - any other characters
                   output.writeChar(chars[i], i, tagDefault);
                    break;
            } // switch
          } // if
        } // for

        prev = syllable; // move the pointer to the start of next syllable
    }

    return output.getOutputIndex();
}


U_NAMESPACE_END
