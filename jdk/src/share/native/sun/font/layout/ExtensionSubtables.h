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
 * (C) Copyright IBM Corp. 2002-2003 - All Rights Reserved
 *
 */

#ifndef __EXTENSIONSUBTABLES_H
#define __EXTENSIONSUBTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"
#include "GlyphSubstitutionTables.h"
#include "LookupProcessor.h"
#include "GlyphIterator.h"

U_NAMESPACE_BEGIN

struct ExtensionSubtable //: GlyphSubstitutionSubtable
{
    le_uint16 substFormat;
    le_uint16 extensionLookupType;
    le_uint32 extensionOffset;

    le_uint32 process(const LEReferenceTo<ExtensionSubtable> &base, const LookupProcessor *lookupProcessor, le_uint16 lookupType,
                      GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode& success) const;
};

U_NAMESPACE_END
#endif
