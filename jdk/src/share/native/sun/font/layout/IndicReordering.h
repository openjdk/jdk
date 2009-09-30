/*
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
 */

/*
 *
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#ifndef __INDICREORDERING_H
#define __INDICREORDERING_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

// Characters that get refered to by name...
#define C_SIGN_ZWNJ           0x200C
#define C_SIGN_ZWJ            0x200D

// Character class values
#define CC_RESERVED               0U
#define CC_VOWEL_MODIFIER         1U
#define CC_STRESS_MARK            2U
#define CC_INDEPENDENT_VOWEL      3U
#define CC_INDEPENDENT_VOWEL_2    4U
#define CC_INDEPENDENT_VOWEL_3    5U
#define CC_CONSONANT              6U
#define CC_CONSONANT_WITH_NUKTA   7U
#define CC_NUKTA                  8U
#define CC_DEPENDENT_VOWEL        9U
#define CC_SPLIT_VOWEL_PIECE_1   10U
#define CC_SPLIT_VOWEL_PIECE_2   11U
#define CC_SPLIT_VOWEL_PIECE_3   12U
#define CC_VIRAMA                13U
#define CC_ZERO_WIDTH_MARK       14U
#define CC_COUNT                 15U

// Character class flags
#define CF_CLASS_MASK    0x0000FFFFU

#define CF_CONSONANT     0x80000000U

#define CF_REPH          0x40000000U
#define CF_VATTU         0x20000000U
#define CF_BELOW_BASE    0x10000000U
#define CF_POST_BASE     0x08000000U
#define CF_LENGTH_MARK   0x04000000U

#define CF_POS_BEFORE    0x00300000U
#define CF_POS_BELOW     0x00200000U
#define CF_POS_ABOVE     0x00100000U
#define CF_POS_AFTER     0x00000000U
#define CF_POS_MASK      0x00300000U

#define CF_INDEX_MASK    0x000F0000U
#define CF_INDEX_SHIFT   16

// Script flag bits
#define SF_MATRAS_AFTER_BASE     0x80000000U
#define SF_REPH_AFTER_BELOW      0x40000000U
#define SF_EYELASH_RA            0x20000000U
#define SF_MPRE_FIXUP            0x10000000U

#define SF_POST_BASE_LIMIT_MASK  0x0000FFFFU
#define SF_NO_POST_BASE_LIMIT    0x00007FFFU

typedef LEUnicode SplitMatra[3];

class MPreFixups;
class LEGlyphStorage;

struct IndicClassTable
{
    typedef le_uint32 CharClass;
    typedef le_uint32 ScriptFlags;

    LEUnicode firstChar;
    LEUnicode lastChar;
    le_int32 worstCaseExpansion;
    ScriptFlags scriptFlags;
    const CharClass *classTable;
    const SplitMatra *splitMatraTable;

    inline le_int32 getWorstCaseExpansion() const;

    CharClass getCharClass(LEUnicode ch) const;

    inline const SplitMatra *getSplitMatra(CharClass charClass) const;

    inline le_bool isVowelModifier(LEUnicode ch) const;
    inline le_bool isStressMark(LEUnicode ch) const;
    inline le_bool isConsonant(LEUnicode ch) const;
    inline le_bool isReph(LEUnicode ch) const;
    inline le_bool isVirama(LEUnicode ch) const;
    inline le_bool isNukta(LEUnicode ch) const;
    inline le_bool isVattu(LEUnicode ch) const;
    inline le_bool isMatra(LEUnicode ch) const;
    inline le_bool isSplitMatra(LEUnicode ch) const;
    inline le_bool isLengthMark(LEUnicode ch) const;
    inline le_bool hasPostOrBelowBaseForm(LEUnicode ch) const;
    inline le_bool hasPostBaseForm(LEUnicode ch) const;
    inline le_bool hasBelowBaseForm(LEUnicode ch) const;

    inline static le_bool isVowelModifier(CharClass charClass);
    inline static le_bool isStressMark(CharClass charClass);
    inline static le_bool isConsonant(CharClass charClass);
    inline static le_bool isReph(CharClass charClass);
    inline static le_bool isVirama(CharClass charClass);
    inline static le_bool isNukta(CharClass charClass);
    inline static le_bool isVattu(CharClass charClass);
    inline static le_bool isMatra(CharClass charClass);
    inline static le_bool isSplitMatra(CharClass charClass);
    inline static le_bool isLengthMark(CharClass charClass);
    inline static le_bool hasPostOrBelowBaseForm(CharClass charClass);
    inline static le_bool hasPostBaseForm(CharClass charClass);
    inline static le_bool hasBelowBaseForm(CharClass charClass);

    static const IndicClassTable *getScriptClassTable(le_int32 scriptCode);
};

class IndicReordering /* not : public UObject because all methods are static */ {
public:
    static le_int32 getWorstCaseExpansion(le_int32 scriptCode);

    static le_int32 reorder(const LEUnicode *theChars, le_int32 charCount, le_int32 scriptCode,
        LEUnicode *outChars, LEGlyphStorage &glyphStorage,
        MPreFixups **outMPreFixups);

    static void adjustMPres(MPreFixups *mpreFixups, LEGlyphStorage &glyphStorage);

    static const FeatureMap *getFeatureMap(le_int32 &count);

private:
    // do not instantiate
    IndicReordering();

    static le_int32 findSyllable(const IndicClassTable *classTable, const LEUnicode *chars, le_int32 prev, le_int32 charCount);

};

inline le_int32 IndicClassTable::getWorstCaseExpansion() const
{
    return worstCaseExpansion;
}

inline const SplitMatra *IndicClassTable::getSplitMatra(CharClass charClass) const
{
    le_int32 index = (charClass & CF_INDEX_MASK) >> CF_INDEX_SHIFT;

    return &splitMatraTable[index - 1];
}

inline le_bool IndicClassTable::isVowelModifier(CharClass charClass)
{
    return (charClass & CF_CLASS_MASK) == CC_VOWEL_MODIFIER;
}

inline le_bool IndicClassTable::isStressMark(CharClass charClass)
{
    return (charClass & CF_CLASS_MASK) == CC_STRESS_MARK;
}

inline le_bool IndicClassTable::isConsonant(CharClass charClass)
{
    return (charClass & CF_CONSONANT) != 0;
}

inline le_bool IndicClassTable::isReph(CharClass charClass)
{
    return (charClass & CF_REPH) != 0;
}

inline le_bool IndicClassTable::isNukta(CharClass charClass)
{
    return (charClass & CF_CLASS_MASK) == CC_NUKTA;
}

inline le_bool IndicClassTable::isVirama(CharClass charClass)
{
    return (charClass & CF_CLASS_MASK) == CC_VIRAMA;
}

inline le_bool IndicClassTable::isVattu(CharClass charClass)
{
    return (charClass & CF_VATTU) != 0;
}

inline le_bool IndicClassTable::isMatra(CharClass charClass)
{
    charClass &= CF_CLASS_MASK;

    return charClass >= CC_DEPENDENT_VOWEL && charClass <= CC_SPLIT_VOWEL_PIECE_3;
}

inline le_bool IndicClassTable::isSplitMatra(CharClass charClass)
{
    return (charClass & CF_INDEX_MASK) != 0;
}

inline le_bool IndicClassTable::isLengthMark(CharClass charClass)
{
    return (charClass & CF_LENGTH_MARK) != 0;
}

inline le_bool IndicClassTable::hasPostOrBelowBaseForm(CharClass charClass)
{
    return (charClass & (CF_POST_BASE | CF_BELOW_BASE)) != 0;
}

inline le_bool IndicClassTable::hasPostBaseForm(CharClass charClass)
{
    return (charClass & CF_POST_BASE) != 0;
}

inline le_bool IndicClassTable::hasBelowBaseForm(CharClass charClass)
{
    return (charClass & CF_BELOW_BASE) != 0;
}

inline le_bool IndicClassTable::isVowelModifier(LEUnicode ch) const
{
    return isVowelModifier(getCharClass(ch));
}

inline le_bool IndicClassTable::isStressMark(LEUnicode ch) const
{
    return isStressMark(getCharClass(ch));
}

inline le_bool IndicClassTable::isConsonant(LEUnicode ch) const
{
    return isConsonant(getCharClass(ch));
}

inline le_bool IndicClassTable::isReph(LEUnicode ch) const
{
    return isReph(getCharClass(ch));
}

inline le_bool IndicClassTable::isVirama(LEUnicode ch) const
{
    return isVirama(getCharClass(ch));
}

inline le_bool IndicClassTable::isNukta(LEUnicode ch) const
{
    return isNukta(getCharClass(ch));
}

inline le_bool IndicClassTable::isVattu(LEUnicode ch) const
{
    return isVattu(getCharClass(ch));
}

inline le_bool IndicClassTable::isMatra(LEUnicode ch) const
{
    return isMatra(getCharClass(ch));
}

inline le_bool IndicClassTable::isSplitMatra(LEUnicode ch) const
{
    return isSplitMatra(getCharClass(ch));
}

inline le_bool IndicClassTable::isLengthMark(LEUnicode ch) const
{
    return isLengthMark(getCharClass(ch));
}

inline le_bool IndicClassTable::hasPostOrBelowBaseForm(LEUnicode ch) const
{
    return hasPostOrBelowBaseForm(getCharClass(ch));
}

inline le_bool IndicClassTable::hasPostBaseForm(LEUnicode ch) const
{
    return hasPostBaseForm(getCharClass(ch));
}

inline le_bool IndicClassTable::hasBelowBaseForm(LEUnicode ch) const
{
    return hasBelowBaseForm(getCharClass(ch));
}

U_NAMESPACE_END
#endif
