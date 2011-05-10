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
 * HangulLayoutEngine.cpp: OpenType processing for Han fonts.
 *
 * (C) Copyright IBM Corp. 1998-2010 - All Rights Reserved.
 */

#include "LETypes.h"
#include "LEScripts.h"
#include "LELanguages.h"

#include "LayoutEngine.h"
#include "OpenTypeLayoutEngine.h"
#include "HangulLayoutEngine.h"
#include "ScriptAndLanguageTags.h"
#include "LEGlyphStorage.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(HangulOpenTypeLayoutEngine)


#define FEATURE_MAP(name) {name ## FeatureTag, name ## FeatureMask}

#define LJMO_FIRST 0x1100
#define LJMO_LAST  0x1159
#define LJMO_FILL  0x115F
#define LJMO_COUNT 19

#define VJMO_FIRST 0x1161
#define VJMO_LAST  0x11A2
#define VJMO_FILL  0x1160
#define VJMO_COUNT 21

#define TJMO_FIRST 0x11A7
#define TJMO_LAST  0x11F9
#define TJMO_COUNT 28

#define HSYL_FIRST 0xAC00
#define HSYL_COUNT 11172
#define HSYL_LVCNT (VJMO_COUNT * TJMO_COUNT)

// Character classes
enum
{
    CC_L = 0,
    CC_V,
    CC_T,
    CC_LV,
    CC_LVT,
    CC_X,
    CC_COUNT
};

// Action flags
#define AF_L 1
#define AF_V 2
#define AF_T 4

// Actions
#define a_N   0
#define a_L   (AF_L)
#define a_V   (AF_V)
#define a_T   (AF_T)
#define a_VT  (AF_V | AF_T)
#define a_LV  (AF_L | AF_V)
#define a_LVT (AF_L | AF_V | AF_T)

typedef struct
{
    le_int32 newState;
    le_int32 actionFlags;
} StateTransition;

static const StateTransition stateTable[][CC_COUNT] =
{
//       L          V          T          LV         LVT           X
    { {1, a_L},  {2, a_LV}, {3, a_LVT}, {2, a_LV}, {3, a_LVT},  {4, a_T}}, // 0 - start
    { {1, a_L},  {2, a_V},  {3, a_VT},  {2, a_LV}, {3, a_LVT}, {-1, a_V}}, // 1 - L+
    {{-1, a_N},  {2, a_V},  {3, a_T},  {-1, a_N}, {-1, a_N},   {-1, a_N}}, // 2 - L+V+
    {{-1, a_N}, {-1, a_N},  {3, a_T},  {-1, a_N}, {-1, a_N},   {-1, a_N}}, // 3 - L+V+T*
    {{-1, a_N}, {-1, a_N}, {-1, a_N},  {-1, a_N}, {-1, a_N},    {4, a_T}}  // 4 - X+
};


#define ccmpFeatureTag LE_CCMP_FEATURE_TAG
#define ljmoFeatureTag LE_LJMO_FEATURE_TAG
#define vjmoFeatureTag LE_VJMO_FEATURE_TAG
#define tjmoFeatureTag LE_TJMO_FEATURE_TAG

#define ccmpFeatureMask 0x80000000UL
#define ljmoFeatureMask 0x40000000UL
#define vjmoFeatureMask 0x20000000UL
#define tjmoFeatureMask 0x10000000UL

static const FeatureMap featureMap[] =
{
    {ccmpFeatureTag, ccmpFeatureMask},
    {ljmoFeatureTag, ljmoFeatureMask},
    {vjmoFeatureTag, vjmoFeatureMask},
    {tjmoFeatureTag, tjmoFeatureMask}
};

static const le_int32 featureMapCount = LE_ARRAY_SIZE(featureMap);

#define nullFeatures 0
#define ljmoFeatures (ccmpFeatureMask | ljmoFeatureMask)
#define vjmoFeatures (ccmpFeatureMask | vjmoFeatureMask | ljmoFeatureMask | tjmoFeatureMask)
#define tjmoFeatures (ccmpFeatureMask | tjmoFeatureMask | ljmoFeatureMask | vjmoFeatureMask)

static le_int32 compose(LEUnicode lead, LEUnicode vowel, LEUnicode trail, LEUnicode &syllable)
{
    le_int32 lIndex = lead  - LJMO_FIRST;
    le_int32 vIndex = vowel - VJMO_FIRST;
    le_int32 tIndex = trail - TJMO_FIRST;
    le_int32 result = 3;

    if ((lIndex < 0 || lIndex >= LJMO_COUNT ) || (vIndex < 0 || vIndex >= VJMO_COUNT)) {
        return 0;
    }

    if (tIndex <= 0 || tIndex >= TJMO_COUNT) {
        tIndex = 0;
        result = 2;
    }

    syllable = (LEUnicode) ((lIndex * VJMO_COUNT + vIndex) * TJMO_COUNT + tIndex + HSYL_FIRST);

    return result;
}

static le_int32 decompose(LEUnicode syllable, LEUnicode &lead, LEUnicode &vowel, LEUnicode &trail)
{
    le_int32 sIndex = syllable - HSYL_FIRST;

    if (sIndex < 0 || sIndex >= HSYL_COUNT) {
        return 0;
    }

    lead  = (LEUnicode)(LJMO_FIRST + (sIndex / HSYL_LVCNT));
    vowel = VJMO_FIRST + (sIndex % HSYL_LVCNT) / TJMO_COUNT;
    trail = TJMO_FIRST + (sIndex % TJMO_COUNT);

    if (trail == TJMO_FIRST) {
        return 2;
    }

    return 3;
}

static le_int32 getCharClass(LEUnicode ch, LEUnicode &lead, LEUnicode &vowel, LEUnicode &trail)
{
    lead  = LJMO_FILL;
    vowel = VJMO_FILL;
    trail = TJMO_FIRST;

    if (ch >= LJMO_FIRST && ch <= LJMO_LAST) {
        lead  = ch;
        return CC_L;
    }

    if (ch >= VJMO_FIRST && ch <= VJMO_LAST) {
        vowel = ch;
        return CC_V;
    }

    if (ch > TJMO_FIRST && ch <= TJMO_LAST) {
        trail = ch;
        return CC_T;
    }

    le_int32 c = decompose(ch, lead, vowel, trail);

    if (c == 2) {
        return CC_LV;
    }

    if (c == 3) {
        return CC_LVT;
    }

    trail = ch;
    return CC_X;
}

HangulOpenTypeLayoutEngine::HangulOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 /*languageCode*/,
                                       le_int32 typoFlags, const GlyphSubstitutionTableHeader *gsubTable, LEErrorCode &success)
    : OpenTypeLayoutEngine(fontInstance, scriptCode, korLanguageCode, typoFlags, gsubTable, success)
{
    fFeatureMap = featureMap;
    fFeatureMapCount = featureMapCount;
    fFeatureOrder = TRUE;
}

HangulOpenTypeLayoutEngine::HangulOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 /*languageCode*/,
                                                           le_int32 typoFlags, LEErrorCode &success)
    : OpenTypeLayoutEngine(fontInstance, scriptCode, korLanguageCode, typoFlags, success)
{
    fFeatureMap = featureMap;
    fFeatureMapCount = featureMapCount;
    fFeatureOrder = TRUE;
}

HangulOpenTypeLayoutEngine::~HangulOpenTypeLayoutEngine()
{
    // nothing to do
}

le_int32 HangulOpenTypeLayoutEngine::characterProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
        LEUnicode *&outChars, LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    if (chars == NULL || offset < 0 || count < 0 || max < 0 || offset >= max || offset + count > max) {
        success = LE_ILLEGAL_ARGUMENT_ERROR;
        return 0;
    }

    le_int32 worstCase = count * 3;

    outChars = LE_NEW_ARRAY(LEUnicode, worstCase);

    if (outChars == NULL) {
        success = LE_MEMORY_ALLOCATION_ERROR;
        return 0;
    }

    glyphStorage.allocateGlyphArray(worstCase, rightToLeft, success);
    glyphStorage.allocateAuxData(success);

    if (LE_FAILURE(success)) {
        LE_DELETE_ARRAY(outChars);
        return 0;
    }

    le_int32 outCharCount = 0;
    le_int32 limit = offset + count;
    le_int32 i = offset;

    while (i < limit) {
        le_int32 state    = 0;
        le_int32 inStart  = i;
        le_int32 outStart = outCharCount;

        while( i < limit) {
            LEUnicode lead  = 0;
            LEUnicode vowel = 0;
            LEUnicode trail = 0;
            le_int32 chClass = getCharClass(chars[i], lead, vowel, trail);
            const StateTransition transition = stateTable[state][chClass];

            if (chClass == CC_X) {
                /* Any character of type X will be stored as a trail jamo */
                if ((transition.actionFlags & AF_T) != 0) {
                    outChars[outCharCount] = trail;
                    glyphStorage.setCharIndex(outCharCount, i-offset, success);
                    glyphStorage.setAuxData(outCharCount++, nullFeatures, success);
                }
            } else {
                /* Any Hangul will be fully decomposed. Output the decomposed characters. */
                if ((transition.actionFlags & AF_L) != 0) {
                    outChars[outCharCount] = lead;
                    glyphStorage.setCharIndex(outCharCount, i-offset, success);
                    glyphStorage.setAuxData(outCharCount++, ljmoFeatures, success);
                }

                if ((transition.actionFlags & AF_V) != 0) {
                    outChars[outCharCount] = vowel;
                    glyphStorage.setCharIndex(outCharCount, i-offset, success);
                    glyphStorage.setAuxData(outCharCount++, vjmoFeatures, success);
                }

                if ((transition.actionFlags & AF_T) != 0) {
                    outChars[outCharCount] = trail;
                    glyphStorage.setCharIndex(outCharCount, i-offset, success);
                    glyphStorage.setAuxData(outCharCount++, tjmoFeatures, success);
                }
            }

            state = transition.newState;

            /* Negative next state means stop. */
            if (state < 0) {
                break;
            }

            i += 1;
        }

        le_int32 inLength  = i - inStart;
        le_int32 outLength = outCharCount - outStart;

        /*
         * See if the syllable can be composed into a single character. There are 5
         * possible cases:
         *
         *   Input     Decomposed to    Compose to
         *   LV        L, V             LV
         *   LVT       L, V, T          LVT
         *   L, V      L, V             LV, DEL
         *   LV, T     L, V, T          LVT, DEL
         *   L, V, T   L, V, T          LVT, DEL, DEL
         */
        if ((inLength >= 1 && inLength <= 3) && (outLength == 2 || outLength == 3)) {
            LEUnicode syllable = 0x0000;
            LEUnicode lead  = outChars[outStart];
            LEUnicode vowel = outChars[outStart + 1];
            LEUnicode trail = outLength == 3? outChars[outStart + 2] : TJMO_FIRST;

            /*
             * If the composition consumes the whole decomposed syllable,
             * we can use it.
             */
            if (compose(lead, vowel, trail, syllable) == outLength) {
                outCharCount = outStart;
                outChars[outCharCount] = syllable;
                glyphStorage.setCharIndex(outCharCount, inStart-offset, success);
                glyphStorage.setAuxData(outCharCount++, nullFeatures, success);

                /*
                 * Replace the rest of the input characters with DEL.
                 */
                for(le_int32 d = inStart + 1; d < i; d += 1) {
                    outChars[outCharCount] = 0xFFFF;
                    glyphStorage.setCharIndex(outCharCount, d - offset, success);
                    glyphStorage.setAuxData(outCharCount++, nullFeatures, success);
                }
            }
        }
    }

    glyphStorage.adoptGlyphCount(outCharCount);
    return outCharCount;
}

U_NAMESPACE_END
