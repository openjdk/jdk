/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "oops/oopsHierarchy.hpp"

size_t     ZAddressHeapBaseShift;
size_t     ZAddressHeapBase;

size_t     ZAddressOffsetBits;
uintptr_t  ZAddressOffsetMask;
size_t     ZAddressOffsetMax;

uintptr_t  ZAddressRemapped;
uintptr_t  ZAddressRemappedMinorMask;
uintptr_t  ZAddressRemappedMajorMask;
uintptr_t  ZAddressMarkedMinor;
uintptr_t  ZAddressMarkedMajor;
uintptr_t  ZAddressFinalizable;
uintptr_t  ZAddressRemembered;

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

static void set_vector_mask(uintptr_t vector_mask[], uintptr_t mask) {
  for (int i = 0; i < 8; ++i) {
    vector_mask[i] = mask;
  }
}

void ZGlobalsPointers::set_good_masks() {
  ZAddressRemapped = ZAddressRemappedMajorMask & ZAddressRemappedMinorMask;

  ZAddressLoadGoodMask  = ZAddressRemapped;
  ZAddressMarkGoodMask  = ZAddressLoadGoodMask | ZAddressMarkedMinor | ZAddressMarkedMajor;
  ZAddressStoreGoodMask = ZAddressMarkGoodMask | ZAddressRemembered;

  ZAddressLoadGoodMask  = ZAddressRemapped;
  ZAddressMarkGoodMask  = ZAddressLoadGoodMask | ZAddressMarkedMinor | ZAddressMarkedMajor;
  ZAddressStoreGoodMask = ZAddressMarkGoodMask | ZAddressRemembered;

  ZAddressLoadShift = ZPointer::load_shift_lookup(ZAddressLoadGoodMask);

  ZAddressLoadBadMask  = ZAddressLoadGoodMask  ^ ZAddressLoadMetadataMask;
  ZAddressMarkBadMask  = ZAddressMarkGoodMask  ^ ZAddressMarkMetadataMask;
  ZAddressStoreBadMask = ZAddressStoreGoodMask ^ ZAddressStoreMetadataMask;

  set_vector_mask(ZAddressVectorLoadBadMask, ZAddressLoadBadMask);
  set_vector_mask(ZAddressVectorStoreBadMask, ZAddressStoreBadMask);
  set_vector_mask(ZAddressVectorStoreGoodMask, ZAddressStoreGoodMask);
}

void ZGlobalsPointers::initialize() {
  ZAddressOffsetBits = ZPlatformAddressOffsetBits();
  ZAddressOffsetMask = (((uintptr_t)1 << ZAddressOffsetBits) - 1) << ZAddressOffsetShift;
  ZAddressOffsetMax = (uintptr_t)1 << ZAddressOffsetBits;

  ZAddressHeapBaseShift = ZPlatformAddressHeapBaseShift();
  ZAddressHeapBase = (uintptr_t)1 << ZAddressHeapBaseShift;

  ZAddressRemappedMinorMask = ZAddressRemapped10 | ZAddressRemapped00;
  ZAddressRemappedMajorMask = ZAddressRemapped01 | ZAddressRemapped00;
  ZAddressMarkedMinor = ZAddressMarkedMinor0;
  ZAddressMarkedMajor = ZAddressMarkedMajor0;
  ZAddressFinalizable = ZAddressFinalizable0;
  ZAddressRemembered = ZAddressRemembered0;

  set_good_masks();
  set_vector_mask(ZAddressVectorUncolorMask, ~ZAddressAllMetadataMask);
}

void ZGlobalsPointers::flip_minor_mark_start() {
  ZAddressMarkedMinor ^= (ZAddressMarkedMinor0 | ZAddressMarkedMinor1);
  ZAddressRemembered ^= (ZAddressRemembered0 | ZAddressRemembered1);
  set_good_masks();
}

void ZGlobalsPointers::flip_minor_relocate_start() {
  ZAddressRemappedMinorMask ^= ZAddressRemappedMask;
  set_good_masks();
}

void ZGlobalsPointers::flip_major_mark_start() {
  ZAddressMarkedMajor ^= (ZAddressMarkedMajor0 | ZAddressMarkedMajor1);
  ZAddressFinalizable ^= (ZAddressFinalizable0 | ZAddressFinalizable1);
  set_good_masks();
}

void ZGlobalsPointers::flip_major_relocate_start() {
  ZAddressRemappedMajorMask ^= ZAddressRemappedMask;
  set_good_masks();
}

void z_catch_colored_oops(oopDesc* obj) {
  if (UseZGC) {
    (void)to_zaddress(obj);
  }
}
