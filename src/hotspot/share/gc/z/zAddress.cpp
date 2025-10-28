/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/barrierSet.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zVerify.hpp"
#include "logging/log.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/java.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/powerOfTwo.hpp"

uintptr_t  ZAddressHeapBase;
size_t     ZAddressHeapBaseShift;

size_t     ZAddressPlatformMaxAddressSpace;

size_t     ZAddressOffsetBits;
uintptr_t  ZAddressOffsetMask;
size_t     ZAddressOffsetMax;

size_t     ZBackingOffsetMax;

uint32_t   ZBackingIndexMax;

uintptr_t  ZPointerRemapped;
uintptr_t  ZPointerRemappedYoungMask;
uintptr_t  ZPointerRemappedOldMask;
uintptr_t  ZPointerMarkedYoung;
uintptr_t  ZPointerMarkedOld;
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
  ZPointerRemapped = ZPointerRemappedOldMask & ZPointerRemappedYoungMask;

  ZPointerLoadGoodMask  = ZPointer::remap_bits(ZPointerRemapped);
  ZPointerMarkGoodMask  = ZPointerLoadGoodMask | ZPointerMarkedYoung | ZPointerMarkedOld;
  ZPointerStoreGoodMask = ZPointerMarkGoodMask | ZPointerRemembered;

  ZPointerLoadBadMask  = ZPointerLoadGoodMask  ^ ZPointerLoadMetadataMask;
  ZPointerMarkBadMask  = ZPointerMarkGoodMask  ^ ZPointerMarkMetadataMask;
  ZPointerStoreBadMask = ZPointerStoreGoodMask ^ ZPointerStoreMetadataMask;

  set_vector_mask(ZPointerVectorLoadBadMask, ZPointerLoadBadMask);
  set_vector_mask(ZPointerVectorStoreBadMask, ZPointerStoreBadMask);
  set_vector_mask(ZPointerVectorStoreGoodMask, ZPointerStoreGoodMask);

  pd_set_good_masks();
}

static void initialize_check_oop_function() {
#ifdef CHECK_UNHANDLED_OOPS
  if (ZVerifyOops) {
    // Enable extra verification of usages of oops in oopsHierarchy.hpp
    check_oop_function = &check_is_valid_zaddress;
  }
#endif
}

void ZGlobalsPointers::set_heap_base(size_t heap_base_shift) {
  assert(heap_base_shift <= ZAddressHeapBaseMaxShift, "Heap base shift to large");
  assert(heap_base_shift <= ZAddressPlatformHeapBaseMaxShift, "Heap base shift to large");
  assert(heap_base_shift >= ZAddressHeapBaseMinShift, "Heap base shift to small");
  assert(heap_base_shift >= ZAddressMaxHeapRequiredHeapBaseShift, "Heap base shift to small");

  // Setup the heap base
  ZAddressHeapBaseShift = heap_base_shift;
  ZAddressHeapBase = (uintptr_t)1 << ZAddressHeapBaseShift;

  log_trace(gc, init)("Set Heap Base: " PTR_FORMAT, ZAddressHeapBase);

  // Setup the offset
  ZAddressOffsetBits = ZAddressHeapBaseShift;
  ZAddressOffsetMask = (((uintptr_t)1 << ZAddressOffsetBits) - 1) << ZAddressOffsetShift;
  ZAddressOffsetMax = (uintptr_t)1 << ZAddressOffsetBits;
}

size_t ZGlobalsPointers::ZAddressPlatformHeapBaseMaxShift;
size_t ZGlobalsPointers::ZAddressMaxHeapRequiredHeapBaseShift;
size_t ZGlobalsPointers::ZAddressMaxHeapRecommendedHeapBaseShift;
size_t ZGlobalsPointers::ZAddressInitialHeapBaseShift;

void ZGlobalsPointers::initialize() {
  ZAddressPlatformHeapBaseMaxShift = ZPlatformHeapBaseMaxShift();
  ZAddressPlatformMaxAddressSpace = size_t(1) << ZAddressPlatformHeapBaseMaxShift;
  const size_t max_supported_heap = MIN2(ZAddressMaxCapacityLimit, ZAddressPlatformMaxAddressSpace);

  guarantee(ZAddressPlatformHeapBaseMaxShift <= ZAddressHeapBaseMaxShift, "Platform max shift must not be more than allowed max shift");
  guarantee(ZAddressPlatformHeapBaseMaxShift >= ZAddressHeapBaseMinShift, "Platform max shift must not be less than allowed min shift");

  // Check max supported heap size
  if (MaxHeapSize > max_supported_heap) {
    vm_exit_during_initialization(
        err_msg("Java heap too large (max supported heap size is %zuG)",
                max_supported_heap / G));
  }

  // Set inital heap base
  ZAddressMaxHeapRequiredHeapBaseShift = (size_t)log2i_ceil(MaxHeapSize);
  const size_t desired_heap_base_shift = ZAddressMaxHeapRequiredHeapBaseShift + (size_t)log2i_exact(ZVirtualToPhysicalRatio);
  ZAddressMaxHeapRecommendedHeapBaseShift = MIN2(desired_heap_base_shift, ZAddressPlatformHeapBaseMaxShift);
  ZAddressInitialHeapBaseShift = ZForceHighestHeapBase
      ? ZAddressPlatformHeapBaseMaxShift
      : MAX2(MIN2(ZAddressHeapBaseRecommendInitalMinShift, ZAddressPlatformHeapBaseMaxShift), ZAddressMaxHeapRecommendedHeapBaseShift);
  set_heap_base(ZAddressInitialHeapBaseShift);

  ZPointerRemappedYoungMask = ZPointerRemapped10 | ZPointerRemapped00;
  ZPointerRemappedOldMask = ZPointerRemapped01 | ZPointerRemapped00;
  ZPointerMarkedYoung = ZPointerMarkedYoung0;
  ZPointerMarkedOld = ZPointerMarkedOld0;
  ZPointerFinalizable = ZPointerFinalizable0;
  ZPointerRemembered = ZPointerRemembered0;

  set_good_masks();

  initialize_check_oop_function();
}

bool ZGlobalsPointers::set_next_heap_base() {
  const size_t min_heap_base_shift = MAX2(ZAddressMaxHeapRequiredHeapBaseShift, ZAddressHeapBaseMinShift);

  const size_t next_heap_base_shift = ZAddressHeapBaseShift == min_heap_base_shift
      ? ZAddressPlatformHeapBaseMaxShift
      : ZAddressHeapBaseShift - 1;

  // Set next heap base
  set_heap_base(next_heap_base_shift);

  // Signal if we have reached the inital heap base
  return next_heap_base_shift != ZAddressInitialHeapBaseShift;
}

void ZGlobalsPointers::flip_young_mark_start() {
  ZPointerMarkedYoung ^= (ZPointerMarkedYoung0 | ZPointerMarkedYoung1);
  ZPointerRemembered ^= (ZPointerRemembered0 | ZPointerRemembered1);
  set_good_masks();
}

void ZGlobalsPointers::flip_young_relocate_start() {
  ZPointerRemappedYoungMask ^= ZPointerRemappedMask;
  set_good_masks();
}

void ZGlobalsPointers::flip_old_mark_start() {
  ZPointerMarkedOld ^= (ZPointerMarkedOld0 | ZPointerMarkedOld1);
  ZPointerFinalizable ^= (ZPointerFinalizable0 | ZPointerFinalizable1);
  set_good_masks();
}

void ZGlobalsPointers::flip_old_relocate_start() {
  ZPointerRemappedOldMask ^= ZPointerRemappedMask;
  set_good_masks();
}
