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
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "oops/oopsHierarchy.hpp"

size_t     ZAddressHeapBaseShift;
size_t     ZAddressHeapBase;

size_t     ZAddressOffsetBits;
uintptr_t  ZAddressOffsetMask;
size_t     ZAddressOffsetMax;

uintptr_t  ZPointerRemapped;
uintptr_t  ZPointerRemappedMinorMask;
uintptr_t  ZPointerRemappedMajorMask;
uintptr_t  ZPointerMarkedMinor;
uintptr_t  ZPointerMarkedMajor;
uintptr_t  ZPointerFinalizable;
uintptr_t  ZPointerRemembered;

uintptr_t  ZPointerLoadGoodMask;
uintptr_t  ZPointerLoadBadMask;

uintptr_t  ZPointerMarkGoodMask;
uintptr_t  ZPointerMarkBadMask;

uintptr_t  ZPointerStoreGoodMask;
uintptr_t  ZPointerStoreBadMask;

uintptr_t  ZPointerVectorLoadBadMask[8];
uintptr_t  ZPointerVectorStoreBadMask[8];
uintptr_t  ZPointerVectorUncolorMask[8];
uintptr_t  ZPointerVectorStoreGoodMask[8];

static uint32_t* ZPointerCalculateStoreGoodMaskLowOrderBitsAddr() {
  const uintptr_t addr = reinterpret_cast<uintptr_t>(&ZPointerStoreGoodMask);
  return reinterpret_cast<uint32_t*>(addr + ZPointerStoreGoodMaskLowOrderBitsOffset);
}

uint32_t*  ZPointerStoreGoodMaskLowOrderBitsAddr = ZPointerCalculateStoreGoodMaskLowOrderBitsAddr();

static void set_vector_mask(uintptr_t vector_mask[], uintptr_t mask) {
  for (int i = 0; i < 8; ++i) {
    vector_mask[i] = mask;
  }
}

void ZGlobalsPointers::set_good_masks() {
  ZPointerRemapped = ZPointerRemappedMajorMask & ZPointerRemappedMinorMask;

  ZPointerLoadGoodMask  = ZPointer::remap_bits(ZPointerRemapped);
  ZPointerMarkGoodMask  = ZPointerLoadGoodMask | ZPointerMarkedMinor | ZPointerMarkedMajor;
  ZPointerStoreGoodMask = ZPointerMarkGoodMask | ZPointerRemembered;

  ZPointerLoadBadMask  = ZPointerLoadGoodMask  ^ ZPointerLoadMetadataMask;
  ZPointerMarkBadMask  = ZPointerMarkGoodMask  ^ ZPointerMarkMetadataMask;
  ZPointerStoreBadMask = ZPointerStoreGoodMask ^ ZPointerStoreMetadataMask;

  set_vector_mask(ZPointerVectorLoadBadMask, ZPointerLoadBadMask);
  set_vector_mask(ZPointerVectorStoreBadMask, ZPointerStoreBadMask);
  set_vector_mask(ZPointerVectorStoreGoodMask, ZPointerStoreGoodMask);

  pd_set_good_masks();
}

void ZGlobalsPointers::initialize() {
  ZAddressOffsetBits = ZPlatformAddressOffsetBits();
  ZAddressOffsetMask = (((uintptr_t)1 << ZAddressOffsetBits) - 1) << ZAddressOffsetShift;
  ZAddressOffsetMax = (uintptr_t)1 << ZAddressOffsetBits;

  ZAddressHeapBaseShift = ZPlatformAddressHeapBaseShift();
  ZAddressHeapBase = (uintptr_t)1 << ZAddressHeapBaseShift;

  ZPointerRemappedMinorMask = ZPointerRemapped10 | ZPointerRemapped00;
  ZPointerRemappedMajorMask = ZPointerRemapped01 | ZPointerRemapped00;
  ZPointerMarkedMinor = ZPointerMarkedMinor0;
  ZPointerMarkedMajor = ZPointerMarkedMajor0;
  ZPointerFinalizable = ZPointerFinalizable0;
  ZPointerRemembered = ZPointerRemembered0;

  set_good_masks();
  set_vector_mask(ZPointerVectorUncolorMask, ~ZPointerAllMetadataMask);
}

void ZGlobalsPointers::flip_minor_mark_start() {
  ZPointerMarkedMinor ^= (ZPointerMarkedMinor0 | ZPointerMarkedMinor1);
  ZPointerRemembered ^= (ZPointerRemembered0 | ZPointerRemembered1);
  set_good_masks();
}

void ZGlobalsPointers::flip_minor_relocate_start() {
  ZPointerRemappedMinorMask ^= ZPointerRemappedMask;
  set_good_masks();
}

void ZGlobalsPointers::flip_major_mark_start() {
  ZPointerMarkedMajor ^= (ZPointerMarkedMajor0 | ZPointerMarkedMajor1);
  ZPointerFinalizable ^= (ZPointerFinalizable0 | ZPointerFinalizable1);
  set_good_masks();
}

void ZGlobalsPointers::flip_major_relocate_start() {
  ZPointerRemappedMajorMask ^= ZPointerRemappedMask;
  set_good_masks();
}

void z_catch_colored_oops(oopDesc* obj) {
  if (UseZGC) {
    (void)to_zaddress(obj);
  }
}
