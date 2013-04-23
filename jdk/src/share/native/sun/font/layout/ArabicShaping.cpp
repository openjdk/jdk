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
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "ArabicShaping.h"
#include "LEGlyphStorage.h"
#include "ClassDefinitionTables.h"

U_NAMESPACE_BEGIN

// This table maps Unicode joining types to
// ShapeTypes.
const ArabicShaping::ShapeType ArabicShaping::shapeTypes[] =
{
    ArabicShaping::ST_NOSHAPE_NONE, // [U]
    ArabicShaping::ST_NOSHAPE_DUAL, // [C]
    ArabicShaping::ST_DUAL,         // [D]
    ArabicShaping::ST_LEFT,         // [L]
    ArabicShaping::ST_RIGHT,        // [R]
    ArabicShaping::ST_TRANSPARENT   // [T]
};

/*
    shaping array holds types for Arabic chars between 0610 and 0700
    other values are either unshaped, or transparent if a mark or format
    code, except for format codes 200c (zero-width non-joiner) and 200d
    (dual-width joiner) which are both unshaped and non_joining or
    dual-joining, respectively.
*/
ArabicShaping::ShapeType ArabicShaping::getShapeType(LEUnicode c)
{
  LEErrorCode success = LE_NO_ERROR;
  const LEReferenceTo<ClassDefinitionTable> joiningTypes((const ClassDefinitionTable *) ArabicShaping::shapingTypeTable,
                                                         ArabicShaping::shapingTypeTableLen);
  le_int32 joiningType = joiningTypes->getGlyphClass(joiningTypes, c, success);

  if (joiningType >= 0 && joiningType < ArabicShaping::JT_COUNT && LE_SUCCESS(success)) {
    return ArabicShaping::shapeTypes[joiningType];
  }

  return ArabicShaping::ST_NOSHAPE_NONE;
}

#define isolFeatureTag LE_ISOL_FEATURE_TAG
#define initFeatureTag LE_INIT_FEATURE_TAG
#define mediFeatureTag LE_MEDI_FEATURE_TAG
#define finaFeatureTag LE_FINA_FEATURE_TAG
#define ligaFeatureTag LE_LIGA_FEATURE_TAG
#define msetFeatureTag LE_MSET_FEATURE_TAG
#define markFeatureTag LE_MARK_FEATURE_TAG
#define ccmpFeatureTag LE_CCMP_FEATURE_TAG
#define rligFeatureTag LE_RLIG_FEATURE_TAG
#define caltFeatureTag LE_CALT_FEATURE_TAG
#define dligFeatureTag LE_DLIG_FEATURE_TAG
#define cswhFeatureTag LE_CSWH_FEATURE_TAG
#define cursFeatureTag LE_CURS_FEATURE_TAG
#define kernFeatureTag LE_KERN_FEATURE_TAG
#define mkmkFeatureTag LE_MKMK_FEATURE_TAG

// NOTE:
// The isol, fina, init and medi features must be
// defined in the above order, and have masks that
// are all in the same nibble.
#define isolFeatureMask 0x80000000UL
#define finaFeatureMask 0x40000000UL
#define initFeatureMask 0x20000000UL
#define mediFeatureMask 0x10000000UL
#define ccmpFeatureMask 0x08000000UL
#define rligFeatureMask 0x04000000UL
#define caltFeatureMask 0x02000000UL
#define ligaFeatureMask 0x01000000UL
#define dligFeatureMask 0x00800000UL
#define cswhFeatureMask 0x00400000UL
#define msetFeatureMask 0x00200000UL
#define cursFeatureMask 0x00100000UL
#define kernFeatureMask 0x00080000UL
#define markFeatureMask 0x00040000UL
#define mkmkFeatureMask 0x00020000UL

#define NO_FEATURES   0
#define ISOL_FEATURES (isolFeatureMask | ligaFeatureMask | msetFeatureMask | markFeatureMask | ccmpFeatureMask | rligFeatureMask | caltFeatureMask | dligFeatureMask | cswhFeatureMask | cursFeatureMask | kernFeatureMask | mkmkFeatureMask)

#define SHAPE_MASK 0xF0000000UL

static const FeatureMap featureMap[] = {
    {ccmpFeatureTag, ccmpFeatureMask},
    {isolFeatureTag, isolFeatureMask},
    {finaFeatureTag, finaFeatureMask},
    {mediFeatureTag, mediFeatureMask},
    {initFeatureTag, initFeatureMask},
    {rligFeatureTag, rligFeatureMask},
    {caltFeatureTag, caltFeatureMask},
    {ligaFeatureTag, ligaFeatureMask},
    {dligFeatureTag, dligFeatureMask},
    {cswhFeatureTag, cswhFeatureMask},
    {msetFeatureTag, msetFeatureMask},
    {cursFeatureTag, cursFeatureMask},
    {kernFeatureTag, kernFeatureMask},
    {markFeatureTag, markFeatureMask},
    {mkmkFeatureTag, mkmkFeatureMask}
};

const FeatureMap *ArabicShaping::getFeatureMap(le_int32 &count)
{
    count = LE_ARRAY_SIZE(featureMap);

    return featureMap;
}

void ArabicShaping::adjustTags(le_int32 outIndex, le_int32 shapeOffset, LEGlyphStorage &glyphStorage)
{
    LEErrorCode success = LE_NO_ERROR;
    FeatureMask featureMask = (FeatureMask) glyphStorage.getAuxData(outIndex, success);
    FeatureMask shape = featureMask & SHAPE_MASK;

    shape >>= shapeOffset;

    glyphStorage.setAuxData(outIndex, ((featureMask & ~SHAPE_MASK) | shape), success);
}

void ArabicShaping::shape(const LEUnicode *chars, le_int32 offset, le_int32 charCount, le_int32 charMax,
                          le_bool rightToLeft, LEGlyphStorage &glyphStorage)
{
    // iterate in logical order, store tags in visible order
    //
    // the effective right char is the most recently encountered
    // non-transparent char
    //
    // four boolean states:
    //   the effective right char shapes
    //   the effective right char causes left shaping
    //   the current char shapes
    //   the current char causes right shaping
    //
    // if both cause shaping, then
    //   shaper.shape(errout, 2) (isolate to initial, or final to medial)
    //   shaper.shape(out, 1) (isolate to final)

    ShapeType rightType = ST_NOSHAPE_NONE, leftType = ST_NOSHAPE_NONE;
    LEErrorCode success = LE_NO_ERROR;
    le_int32 i;

    for (i = offset - 1; i >= 0; i -= 1) {
        rightType = getShapeType(chars[i]);

        if (rightType != ST_TRANSPARENT) {
            break;
        }
    }

    for (i = offset + charCount; i < charMax; i += 1) {
        leftType = getShapeType(chars[i]);

        if (leftType != ST_TRANSPARENT) {
            break;
        }
    }

    // erout is effective right logical index
    le_int32 erout = -1;
    le_bool rightShapes = FALSE;
    le_bool rightCauses = (rightType & MASK_SHAPE_LEFT) != 0;
    le_int32 in, e, out = 0, dir = 1;

    if (rightToLeft) {
        out = charCount - 1;
        erout = charCount;
        dir = -1;
    }

    for (in = offset, e = offset + charCount; in < e; in += 1, out += dir) {
        LEUnicode c = chars[in];
        ShapeType t = getShapeType(c);

        if (t == ST_NOSHAPE_NONE) {
            glyphStorage.setAuxData(out, NO_FEATURES, success);
        } else {
        glyphStorage.setAuxData(out, ISOL_FEATURES, success);
        }

        if ((t & MASK_TRANSPARENT) != 0) {
            continue;
        }

        le_bool curShapes = (t & MASK_NOSHAPE) == 0;
        le_bool curCauses = (t & MASK_SHAPE_RIGHT) != 0;

        if (rightCauses && curCauses) {
            if (rightShapes) {
                adjustTags(erout, 2, glyphStorage);
            }

            if (curShapes) {
                adjustTags(out, 1, glyphStorage);
            }
        }

        rightShapes = curShapes;
        rightCauses = (t & MASK_SHAPE_LEFT) != 0;
        erout = out;
    }

    if (rightShapes && rightCauses && (leftType & MASK_SHAPE_RIGHT) != 0) {
        adjustTags(erout, 2, glyphStorage);
    }
}

U_NAMESPACE_END
