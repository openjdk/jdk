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

#ifndef SHARE_GC_Z_ZGLOBALS_HPP
#define SHARE_GC_Z_ZGLOBALS_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include CPU_HEADER(gc/z/zGlobals)

// Collector name
const char* const ZName                         = "The Z Garbage Collector";

// Granule shift/size
const size_t      ZGranuleSizeShift             = ZPlatformGranuleSizeShift;
const size_t      ZGranuleSize                  = (size_t)1 << ZGranuleSizeShift;

// Number of heap views
const size_t      ZHeapViews                    = ZPlatformHeapViews;

// Virtual memory to physical memory ratio
const size_t      ZVirtualToPhysicalRatio       = 16; // 16:1

// Page types
const uint8_t     ZPageTypeSmall                = 0;
const uint8_t     ZPageTypeMedium               = 1;
const uint8_t     ZPageTypeLarge                = 2;

// Page size shifts
const size_t      ZPageSizeSmallShift           = ZGranuleSizeShift;
extern size_t     ZPageSizeMediumShift;

// Page sizes
const size_t      ZPageSizeSmall                = (size_t)1 << ZPageSizeSmallShift;
extern size_t     ZPageSizeMedium;

// Object size limits
const size_t      ZObjectSizeLimitSmall         = ZPageSizeSmall / 8; // 12.5% max waste
extern size_t     ZObjectSizeLimitMedium;

// Object alignment shifts
extern const int& ZObjectAlignmentSmallShift;
extern int        ZObjectAlignmentMediumShift;
const int         ZObjectAlignmentLargeShift    = ZGranuleSizeShift;

// Object alignments
extern const int& ZObjectAlignmentSmall;
extern int        ZObjectAlignmentMedium;
const int         ZObjectAlignmentLarge         = 1 << ZObjectAlignmentLargeShift;

//
// Good/Bad mask states
// --------------------
//
//                 GoodMask         BadMask          WeakGoodMask     WeakBadMask
//                 --------------------------------------------------------------
//  Marked0        001              110              101              010
//  Marked1        010              101              110              001
//  Remapped       100              011              100              011
//

// Good/bad masks
extern uintptr_t  ZAddressLoadGoodMask;
extern uintptr_t  ZAddressLoadBadMask;
extern size_t     ZAddressLoadShift;

extern uintptr_t  ZAddressMarkGoodMask;
extern uintptr_t  ZAddressMarkBadMask;

extern uintptr_t  ZAddressStoreGoodMask;
extern uintptr_t  ZAddressStoreBadMask;

extern uintptr_t  ZAddressVectorLoadBadMask[8];
extern uintptr_t  ZAddressVectorStoreBadMask[8];
extern uintptr_t  ZAddressVectorUncolorMask[8];
extern uintptr_t  ZAddressVectorStoreGoodMask[8];

// The bad mask is 64 bit. Its low order 32 bits contain all possible value combinations
// that this mask will have. Therefore, the memory where the 32 low order bits are stored
// can be used as a 32 bit GC epoch counter, that has a different bit pattern every time
// the bad mask is flipped. This provides a pointer to such 32 bits.
extern uint32_t*  ZAddressStoreGoodMaskLowOrderBitsAddr;
const int         ZAddressStoreGoodMaskLowOrderBitsOffset = LITTLE_ENDIAN_ONLY(0) BIG_ENDIAN_ONLY(4);

// Where the heap starts
extern uintptr_t  ZAddressHeapBase;
extern uintptr_t  ZAddressHeapBaseShift;

// Metadata part of address

// The layout of a zpointer comprises address bits and two low order metadata bytes,
// with the following layout:
// RRRRmmMMFFrr0000
// ****               : Used by load barrier
// **********         : Used by mark barrier
// ************       : Used by store barrier
//             ****   : Reserved bits
// 
// The table below describes what each color does.
//
// +-------------+-------------------+--------------------------+
// | Bit pattern | Description       | Included colors          |
// +-------------+-------------------+--------------------------+
// |     rr      | Remembered bits   | Remembered[0, 1]         |
// +-------------+-------------------+--------------------------+
// |     FF      | Finalizable bits  | Finalizable[0, 1]        |
// +-------------+-------------------+--------------------------+
// |     MM      | Marked major bits | MarkedMajor[0, 1]        |
// +-------------+-------------------+--------------------------+
// |     mm      | Marked minor bits | MarkedMinor[0, 1]        |
// +-------------+-------------------+--------------------------+
// |    RRRR     | Remapped bits     | Remapped[00, 01, 10, 11] |
// +-------------+-------------------+--------------------------+
//
// The low order zero address bits sometimes overlap with the high order zero metadata
// bits, depending on the remapped bit being set.
//
//             vvv- overlapping address and metadata zeros
//    aaa...aaa0001mmMMFFrr0000 = Remapped00 zpointer
//             vv-- overlapping address and metadata zeros
//   aaa...aaa00010mmMMFFrr0000 = Remapped01 zpointer
//             v--- overlapping address and metadata zero
//  aaa...aaa000100mmMMFFrr0000 = Remapped10 zpointer
// aaa...aaa0001000mmMMFFrr0000 = Remapped11 zpointer
//
// The overlapping is performed because the JIT-compiled load barriers expect the
// address bits to start right after the load-good bit. It allows combining the good
// bit check and unmasking into a single speculative shift instruction.
//
// The remapped bits are notably not grouped into two sets of bits, one for the minor
// collection and one for the major collection, like the other bits. The reason is that
// the load barrier is only compatible with bit patterns where there is a single zero in
// its bits of operation (the load metadata bit mask). Instead, the single bit that we
// set encodes the combined state of a conceptual RemappedMinor[0, 1] and
// RemappedMajor[0, 1] bits. The encoding scheme is that the shift of the load good bit,
// minus the shift of the load metadata bit start encodes the numbers 0, 1, 2 and 3.
// These numbers in binary correspond to 00, 01, 10 and 11. The low order bit in said
// numbers correspond to the simulated RemappedMinor[0, 1] value, and the high order bit
// corresponds to the simulated RemappedMajor[0, 1] value.
// We decide the bit to be taken by having the RemappedMinorMask and RemappedMajorMask
// variables, which alternate between what two bits they accept for their corresponding
// major and minor phase. The Remapped bit is chosen by taking the intersection of those
// two variables.
// RemappedMajorMask alternates between these two bit patterns:
// RemappedMajor0 => 0011
// RemappedMajor1 => 1100
//
// RemappedMinorMask alternates between these two bit patterns:
// RemappedMinor0 => 0101
// RemappedMinor1 => 1010
//
// The corresponding intersections look like this:
// RemappedMajor0 & RemappedMinor0 = 0001 = Remapped00
// RemappedMajor0 & RemappedMinor1 = 0010 = Remapped01
// RemappedMajor1 & RemappedMinor0 = 0100 = Remapped10
// RemappedMajor1 & RemappedMinor1 = 1000 = Remapped11

constexpr uintptr_t z_address_mask(size_t shift, size_t bits) {
  return (((uintptr_t)1 << bits) - 1) << shift;
}

constexpr uintptr_t z_address_bit(size_t shift, size_t offset) {
  return (uintptr_t)1 << (shift + offset);
}

// Reserved bits
const size_t      ZAddressReservedShift   = 0;
const size_t      ZAddressReservedBits    = 4;
const uintptr_t   ZAddressReservedMask    = z_address_mask(ZAddressReservedShift, ZAddressReservedBits);

const uintptr_t   ZAddressReserved0       = z_address_bit(ZAddressReservedShift, 0);
const uintptr_t   ZAddressReserved1       = z_address_bit(ZAddressReservedShift, 1);
const uintptr_t   ZAddressReserved2       = z_address_bit(ZAddressReservedShift, 2);
const uintptr_t   ZAddressReserved3       = z_address_bit(ZAddressReservedShift, 3);

// Remembered set bits
const size_t      ZAddressRememberedShift = ZAddressReservedShift + ZAddressReservedBits;
const size_t      ZAddressRememberedBits  = 2;
const uintptr_t   ZAddressRememberedMask  = z_address_mask(ZAddressRememberedShift, ZAddressRememberedBits);

const uintptr_t   ZAddressRemembered0     = z_address_bit(ZAddressRememberedShift, 0);
const uintptr_t   ZAddressRemembered1     = z_address_bit(ZAddressRememberedShift, 1);

// Marked bits
const size_t      ZAddressMarkedShift     = ZAddressRememberedShift + ZAddressRememberedBits;
const size_t      ZAddressMarkedBits      = 6;
const uintptr_t   ZAddressMarkedMask      = z_address_mask(ZAddressMarkedShift, ZAddressMarkedBits);

const uintptr_t   ZAddressFinalizable0    = z_address_bit(ZAddressMarkedShift, 0);
const uintptr_t   ZAddressFinalizable1    = z_address_bit(ZAddressMarkedShift, 1);
const uintptr_t   ZAddressMarkedMinor0    = z_address_bit(ZAddressMarkedShift, 2);
const uintptr_t   ZAddressMarkedMinor1    = z_address_bit(ZAddressMarkedShift, 3);
const uintptr_t   ZAddressMarkedMajor0    = z_address_bit(ZAddressMarkedShift, 4);
const uintptr_t   ZAddressMarkedMajor1    = z_address_bit(ZAddressMarkedShift, 5);

// Remapped bits
const size_t      ZAddressRemappedShift   = ZAddressMarkedShift + ZAddressMarkedBits;
const size_t      ZAddressRemappedBits    = 4;
const uintptr_t   ZAddressRemappedMask    = z_address_mask(ZAddressRemappedShift, ZAddressRemappedBits);

const uintptr_t   ZAddressRemapped00      = z_address_bit(ZAddressRemappedShift, 0);
const uintptr_t   ZAddressRemapped01      = z_address_bit(ZAddressRemappedShift, 1);
const uintptr_t   ZAddressRemapped10      = z_address_bit(ZAddressRemappedShift, 2);
const uintptr_t   ZAddressRemapped11      = z_address_bit(ZAddressRemappedShift, 3);

// The shift table is tightly coupled with the zpointer layout given above
constexpr int     ZAddressLoadShiftTable[] = {
  ZAddressRemappedShift + ZAddressRemappedShift, // [0] NULL
  ZAddressRemappedShift + 1,                     // [1] Remapped00
  ZAddressRemappedShift + 2,                     // [2] Remapped01
  0,
  ZAddressRemappedShift + 3,                     // [4] Remapped10
  0,
  0,
  0,
  ZAddressRemappedShift + 4                      // [8] Remapped11
};

// Barrier metadata masks
const uintptr_t   ZAddressLoadMetadataMask  = ZAddressRemappedMask;
const uintptr_t   ZAddressMarkMetadataMask  = ZAddressLoadMetadataMask | ZAddressMarkedMask;
const uintptr_t   ZAddressStoreMetadataMask = ZAddressMarkMetadataMask | ZAddressRememberedMask;
const uintptr_t   ZAddressAllMetadataMask   = ZAddressStoreMetadataMask;

// The current expected bit
extern uintptr_t  ZAddressRemapped;
extern uintptr_t  ZAddressMarkedMajor;
extern uintptr_t  ZAddressMarkedMinor;
extern uintptr_t  ZAddressFinalizable;
extern uintptr_t  ZAddressRemembered;

// The current expected remap bit for the minor (or major) collection is either of two bits.
// The other collection alternates the bits, so we need to use a mask.
extern uintptr_t  ZAddressRemappedMinorMask;
extern uintptr_t  ZAddressRemappedMajorMask;

// Cache line size
const size_t      ZCacheLineSize                = ZPlatformCacheLineSize;
#define           ZCACHE_ALIGNED                ATTRIBUTE_ALIGNED(ZCacheLineSize)

// Mark stack space
extern uintptr_t  ZMarkStackSpaceStart;
const size_t      ZMarkStackSpaceExpandSize     = (size_t)1 << 25; // 32M

// Mark stack and magazine sizes
const size_t      ZMarkStackSizeShift           = 11; // 2K
const size_t      ZMarkStackSize                = (size_t)1 << ZMarkStackSizeShift;
const size_t      ZMarkStackHeaderSize          = (size_t)1 << 4; // 16B
const size_t      ZMarkStackSlots               = (ZMarkStackSize - ZMarkStackHeaderSize) / sizeof(uintptr_t);
const size_t      ZMarkStackMagazineSize        = (size_t)1 << 15; // 32K
const size_t      ZMarkStackMagazineSlots       = (ZMarkStackMagazineSize / ZMarkStackSize) - 1;

// Mark stripe size
const size_t      ZMarkStripeShift              = ZGranuleSizeShift;

// Max number of mark stripes
const size_t      ZMarkStripesMax               = 16; // Must be a power of two

// Mark cache size
const size_t      ZMarkCacheSize                = 1024; // Must be a power of two

// Partial array minimum size
const size_t      ZMarkPartialArrayMinSizeShift = 12; // 4K
const size_t      ZMarkPartialArrayMinSize      = (size_t)1 << ZMarkPartialArrayMinSizeShift;

// Max number of proactive/terminate flush attempts
const size_t      ZMarkProactiveFlushMax        = 10;

// Try complete mark timeout
const uint64_t    ZMarkCompleteTimeout          = 200; // us

#endif // SHARE_GC_Z_ZGLOBALS_HPP
