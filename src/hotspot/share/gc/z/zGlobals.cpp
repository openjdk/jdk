/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zGlobals.hpp"

size_t     ZPageSizeMediumShift;
size_t     ZPageSizeMedium;

size_t     ZObjectSizeLimitMedium;

const int& ZObjectAlignmentSmallShift  = LogMinObjAlignmentInBytes;
int        ZObjectAlignmentMediumShift;

const int& ZObjectAlignmentSmall       = MinObjAlignmentInBytes;
int        ZObjectAlignmentMedium;

uintptr_t  ZAddressLoadGoodMask;
uintptr_t  ZAddressLoadBadMask;
size_t     ZAddressLoadShift;

uintptr_t  ZAddressMarkGoodMask;
uintptr_t  ZAddressMarkBadMask;

uintptr_t  ZAddressStoreGoodMask;
uintptr_t  ZAddressStoreBadMask;

uintptr_t  ZAddressVectorLoadBadMask[8];
uintptr_t  ZAddressVectorStoreBadMask[8];
uintptr_t  ZAddressVectorUncolorMask[8];
uintptr_t  ZAddressVectorStoreGoodMask[8];

static uint32_t* ZAddressCalculateStoreGoodMaskLowOrderBitsAddr() {
  const uintptr_t addr = reinterpret_cast<uintptr_t>(&ZAddressStoreGoodMask);
  return reinterpret_cast<uint32_t*>(addr + ZAddressStoreGoodMaskLowOrderBitsOffset);
}

uint32_t*  ZAddressStoreGoodMaskLowOrderBitsAddr = ZAddressCalculateStoreGoodMaskLowOrderBitsAddr();

size_t     ZAddressHeapBaseShift;
size_t     ZAddressHeapBase;

uintptr_t  ZAddressRemapped;
uintptr_t  ZAddressRemappedMinorMask;
uintptr_t  ZAddressRemappedMajorMask;
uintptr_t  ZAddressMarkedMinor;
uintptr_t  ZAddressMarkedMajor;
uintptr_t  ZAddressFinalizable;
uintptr_t  ZAddressRemembered;
