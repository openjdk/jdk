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
 * (C) Copyright IBM Corp. 2004-2013 - All Rights Reserved
 *
 */

#ifndef __KERNTABLE_H
#define __KERNTABLE_H

#ifndef __LETYPES_H
#include "LETypes.h"
#endif

#include "LETypes.h"
#include "LETableReference.h"
//#include "LEFontInstance.h"
//#include "LEGlyphStorage.h"

#include <stdio.h>

U_NAMESPACE_BEGIN
struct PairInfo;
class  LEFontInstance;
class  LEGlyphStorage;

/**
 * Windows type 0 kerning table support only for now.
 */
class U_LAYOUT_API KernTable
{
 private:
  le_uint16 coverage;
  le_uint16 nPairs;
  PairInfo  *pairsSwapped;
  const LETableReference &fTable;
  le_uint16 searchRange;
  le_uint16 entrySelector;
  le_uint16 rangeShift;

 public:
  KernTable(const LETableReference &table, LEErrorCode &success);

  /*
   * Process the glyph positions.
   */
  void process(LEGlyphStorage& storage, LEErrorCode &success);
};

U_NAMESPACE_END

#endif
