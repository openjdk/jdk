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
 */

#ifndef __GLYPHPOSITIONINGLOOKUPPROCESSOR_H
#define __GLYPHPOSITIONINGLOOKUPPROCESSOR_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "Lookups.h"
#include "ICUFeatures.h"
#include "GlyphDefinitionTables.h"
#include "GlyphPositioningTables.h"
#include "GlyphIterator.h"
#include "LookupProcessor.h"

U_NAMESPACE_BEGIN

class GlyphPositioningLookupProcessor : public LookupProcessor
{
public:
    GlyphPositioningLookupProcessor(const LEReferenceTo<GlyphPositioningTableHeader> &glyphPositioningTableHeader,
        LETag scriptTag,
        LETag languageTag,
        const FeatureMap *featureMap,
        le_int32 featureMapCount,
        le_bool featureOrder,
        LEErrorCode& success);

    virtual ~GlyphPositioningLookupProcessor();

    virtual le_uint32 applySubtable(const LEReferenceTo<LookupSubtable> &lookupSubtable, le_uint16 lookupType, GlyphIterator *glyphIterator,
        const LEFontInstance *fontInstance, LEErrorCode& success) const;

protected:
    GlyphPositioningLookupProcessor();

private:

    GlyphPositioningLookupProcessor(const GlyphPositioningLookupProcessor &other); // forbid copying of this class
    GlyphPositioningLookupProcessor &operator=(const GlyphPositioningLookupProcessor &other); // forbid copying of this class
};

U_NAMESPACE_END
#endif
