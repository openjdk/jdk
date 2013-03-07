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
 *
 * (C) Copyright IBM Corp. 1998-2003 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "OpenTypeUtilities.h"
#include "OpenTypeTables.h"
#include "ICUFeatures.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

LEReferenceTo<FeatureTable> FeatureListTable::getFeatureTable(const LETableReference &base, le_uint16 featureIndex, LETag *featureTag, LEErrorCode &success) const
{
  if (featureIndex >= SWAPW(featureCount) || LE_FAILURE(success)) {
    return LEReferenceTo<FeatureTable>();
  }

    Offset featureTableOffset = featureRecordArray[featureIndex].featureTableOffset;

    *featureTag = SWAPT(featureRecordArray[featureIndex].featureTag);

    return LEReferenceTo<FeatureTable>(base, success, SWAPW(featureTableOffset));
}

#if 0
/*
 * Note: according to the OpenType Spec. v 1.4, the entries in the Feature
 * List Table are sorted alphabetically by feature tag; however, there seem
 * to be some fonts which have an unsorted list; that's why the binary search
 * is #if 0'd out and replaced by a linear search.
 *
 * Also note: as of ICU 2.6, this method isn't called anyhow...
 */
const FeatureTable *FeatureListTable::getFeatureTable(LETag featureTag) const
{
#if 0
    Offset featureTableOffset =
        OpenTypeUtilities::getTagOffset(featureTag, (TagAndOffsetRecord *) featureRecordArray, SWAPW(featureCount));

    if (featureTableOffset == 0) {
        return 0;
    }

    return (const FeatureTable *) ((char *) this + SWAPW(featureTableOffset));
#else
    int count = SWAPW(featureCount);

    for (int i = 0; i < count; i += 1) {
        if (SWAPT(featureRecordArray[i].featureTag) == featureTag) {
            return (const FeatureTable *) ((char *) this + SWAPW(featureRecordArray[i].featureTableOffset));
        }
    }

    return 0;
#endif
}
#endif

U_NAMESPACE_END
