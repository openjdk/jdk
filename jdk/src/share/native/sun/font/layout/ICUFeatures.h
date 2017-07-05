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
 */

#ifndef __ICUFEATURES_H
#define __ICUFEATURES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

struct FeatureRecord
{
    ATag        featureTag;
    Offset      featureTableOffset;
};

struct FeatureTable
{
    Offset      featureParamsOffset;
    le_uint16   lookupCount;
    le_uint16   lookupListIndexArray[ANY_NUMBER];
};
LE_VAR_ARRAY(FeatureTable, lookupListIndexArray)

struct FeatureListTable
{
    le_uint16           featureCount;
    FeatureRecord       featureRecordArray[ANY_NUMBER];

  LEReferenceTo<FeatureTable>  getFeatureTable(const LETableReference &base, le_uint16 featureIndex, LETag *featureTag, LEErrorCode &success) const;

#if 0
  const LEReferenceTo<FeatureTable>  getFeatureTable(const LETableReference &base, LETag featureTag, LEErrorCode &success) const;
#endif
};

LE_VAR_ARRAY(FeatureListTable, featureRecordArray)

U_NAMESPACE_END
#endif
