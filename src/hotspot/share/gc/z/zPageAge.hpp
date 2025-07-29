/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 */

#ifndef SHARE_GC_Z_ZPAGEAGE_HPP
#define SHARE_GC_Z_ZPAGEAGE_HPP

#include "utilities/enumIterator.hpp"
#include "utilities/globalDefinitions.hpp"

enum class ZPageAge : uint8_t {
  eden,
  survivor1,
  survivor2,
  survivor3,
  survivor4,
  survivor5,
  survivor6,
  survivor7,
  survivor8,
  survivor9,
  survivor10,
  survivor11,
  survivor12,
  survivor13,
  survivor14,
  old
};

constexpr uint ZPageAgeCount = static_cast<uint>(ZPageAge::old) + 1;
constexpr ZPageAge ZPageAgeLastPlusOne = static_cast<ZPageAge>(ZPageAgeCount);

constexpr uint ZNumRelocationAges = ZPageAgeCount - 1;

ENUMERATOR_RANGE(ZPageAge,
                 ZPageAge::eden,
                 ZPageAge::old);

using ZPageAgeRange = EnumRange<ZPageAge>;

constexpr ZPageAgeRange ZPageAgeRangeEden = ZPageAgeRange::create<ZPageAge::eden, ZPageAge::survivor1>();
constexpr ZPageAgeRange ZPageAgeRangeYoung = ZPageAgeRange::create<ZPageAge::eden, ZPageAge::old>();
constexpr ZPageAgeRange ZPageAgeRangeSurvivor = ZPageAgeRange::create<ZPageAge::survivor1, ZPageAge::old>();
constexpr ZPageAgeRange ZPageAgeRangeRelocation = ZPageAgeRange::create<ZPageAge::survivor1, ZPageAgeLastPlusOne>();
constexpr ZPageAgeRange ZPageAgeRangeOld = ZPageAgeRange::create<ZPageAge::old, ZPageAgeLastPlusOne>();

#endif // SHARE_GC_Z_ZPAGEAGE_HPP
