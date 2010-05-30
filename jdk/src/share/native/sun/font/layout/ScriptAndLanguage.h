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
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#ifndef __SCRIPTANDLANGUAGE_H
#define __SCRIPTANDLANGUAGE_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

typedef TagAndOffsetRecord LangSysRecord;

struct LangSysTable
{
    Offset    lookupOrderOffset;
    le_uint16 reqFeatureIndex;
    le_uint16 featureCount;
    le_uint16 featureIndexArray[ANY_NUMBER];
};

struct ScriptTable
{
    Offset              defaultLangSysTableOffset;
    le_uint16           langSysCount;
    LangSysRecord       langSysRecordArray[ANY_NUMBER];

    const LangSysTable  *findLanguage(LETag languageTag, le_bool exactMatch = FALSE) const;
};

typedef TagAndOffsetRecord ScriptRecord;

struct ScriptListTable
{
    le_uint16           scriptCount;
    ScriptRecord        scriptRecordArray[ANY_NUMBER];

    const ScriptTable   *findScript(LETag scriptTag) const;
    const LangSysTable  *findLanguage(LETag scriptTag, LETag languageTag, le_bool exactMatch = FALSE) const;
};

U_NAMESPACE_END
#endif

