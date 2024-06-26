/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZADDRESS_HPP
#define SHARE_GC_Z_ZADDRESS_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include CPU_HEADER(gc/z/zAddress)

// One bit that denotes where the heap start. All uncolored
// oops have this bit set, plus an offset within the heap.
extern uintptr_t  ZAddressHeapBase;
extern uintptr_t  ZAddressHeapBaseShift;

// Describes the maximal offset inside the heap.
extern size_t    ZAddressOffsetBits;
const  size_t    ZAddressOffsetShift = 0;
extern uintptr_t ZAddressOffsetMask;
extern size_t    ZAddressOffsetMax;

// Layout of metadata bits in colored pointer / zpointer.
//
// A zpointer is a combination of the address bits (heap base bit + offset)
// and two low-order metadata bytes, with the following layout:
//
// RRRRMMmmFFrr0000
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
// |     mm      | Marked young bits | MarkedYoung[0, 1]        |
// +-------------+-------------------+--------------------------+
// |     MM      | Marked old bits   | MarkedOld[0, 1]          |
// +-------------+-------------------+--------------------------+
// |    RRRR     | Remapped bits     | Remapped[00, 01, 10, 11] |
// +-------------+-------------------+--------------------------+
//
// The low order zero address bits sometimes overlap with the high order zero metadata
// bits, depending on the remapped bit being set.
//
//             vvv- overlapping address and metadata zeros
//    aaa...aaa0001MMmmFFrr0000 = Remapped00 zpointer
//
//             vv-- overlapping address and metadata zeros
//   aaa...aaa00010MMmmFFrr0000 = Remapped01 zpointer
//
//             v--- overlapping address and metadata zero
//  aaa...aaa000100MMmmFFrr0000 = Remapped10 zpointer
//
//             ---- no overlapping address and metadata zeros
// aaa...aaa0001000MMmmFFrr0000 = Remapped11 zpointer
//
// The overlapping is performed because the x86 JIT-compiled load barriers expect the
// address bits to start right after the load-good bit. It allows combining the good
// bit check and unmasking into a single speculative shift instruction. On AArch64 we
// don't do this, and hence there are no overlapping address and  metadata zeros there.
//
// The remapped bits are notably not grouped into two sets of bits, one for the young
// collection and one for the old collection, like the other bits. The reason is that
// the load barrier is only compatible with bit patterns where there is a single zero in
// its bits of operation (the load metadata bit mask). Instead, the single bit that we
// set encodes the combined state of a conceptual RemappedYoung[0, 1] and
// RemappedOld[0, 1] pair. The encoding scheme is that the shift of the load good bit,
// minus the shift of the load metadata bit start encodes the numbers 0, 1, 2 and 3.
// These numbers in binary correspond to 00, 01, 10 and 11. The low order bit in said
// numbers correspond to the simulated RemappedYoung[0, 1] value, and the high order bit
// corresponds to the simulated RemappedOld[0, 1] value. On AArch64, the remap bits
// of zpointers are the complement of this bit. So there are 3 good bits and one bad bit
// instead. This lends itself better to AArch64 instructions.
//
// We decide the bit to be taken by having the RemappedYoungMask and RemappedOldMask
// variables, which alternate between what two bits they accept for their corresponding
// old and young phase. The Remapped bit is chosen by taking the intersection of those
// two variables.
//
// RemappedOldMask alternates between these two bit patterns:
//
//  RemappedOld0 => 0011
//  RemappedOld1 => 1100
//
// RemappedYoungMask alternates between these two bit patterns:
//
//  RemappedYoung0 => 0101
//  RemappedYoung1 => 1010
//
// The corresponding intersections look like this:
//
//  RemappedOld0 & RemappedYoung0 = 0001 = Remapped00
//  RemappedOld0 & RemappedYoung1 = 0010 = Remapped01
//  RemappedOld1 & RemappedYoung0 = 0100 = Remapped10
//  RemappedOld1 & RemappedYoung1 = 1000 = Remapped11

constexpr uintptr_t z_pointer_mask(size_t shift, size_t bits) {
  return (((uintptr_t)1 << bits) - 1) << shift;
}

constexpr uintptr_t z_pointer_bit(size_t shift, size_t offset) {
  return (uintptr_t)1 << (shift + offset);
}

// Reserved bits
const size_t      ZPointerReservedShift   = 0;
const size_t      ZPointerReservedBits    = 4;
const uintptr_t   ZPointerReservedMask    = z_pointer_mask(ZPointerReservedShift, ZPointerReservedBits);

const uintptr_t   ZPointerReserved0       = z_pointer_bit(ZPointerReservedShift, 0);
const uintptr_t   ZPointerReserved1       = z_pointer_bit(ZPointerReservedShift, 1);
const uintptr_t   ZPointerReserved2       = z_pointer_bit(ZPointerReservedShift, 2);
const uintptr_t   ZPointerReserved3       = z_pointer_bit(ZPointerReservedShift, 3);

// Remembered set bits
const size_t      ZPointerRememberedShift = ZPointerReservedShift + ZPointerReservedBits;
const size_t      ZPointerRememberedBits  = 2;
const uintptr_t   ZPointerRememberedMask  = z_pointer_mask(ZPointerRememberedShift, ZPointerRememberedBits);

const uintptr_t   ZPointerRemembered0     = z_pointer_bit(ZPointerRememberedShift, 0);
const uintptr_t   ZPointerRemembered1     = z_pointer_bit(ZPointerRememberedShift, 1);

// Marked bits
const size_t      ZPointerMarkedShift     = ZPointerRememberedShift + ZPointerRememberedBits;
const size_t      ZPointerMarkedBits      = 6;
const uintptr_t   ZPointerMarkedMask      = z_pointer_mask(ZPointerMarkedShift, ZPointerMarkedBits);

const uintptr_t   ZPointerFinalizable0    = z_pointer_bit(ZPointerMarkedShift, 0);
const uintptr_t   ZPointerFinalizable1    = z_pointer_bit(ZPointerMarkedShift, 1);
const uintptr_t   ZPointerMarkedYoung0    = z_pointer_bit(ZPointerMarkedShift, 2);
const uintptr_t   ZPointerMarkedYoung1    = z_pointer_bit(ZPointerMarkedShift, 3);
const uintptr_t   ZPointerMarkedOld0      = z_pointer_bit(ZPointerMarkedShift, 4);
const uintptr_t   ZPointerMarkedOld1      = z_pointer_bit(ZPointerMarkedShift, 5);

// Remapped bits
const size_t      ZPointerRemappedShift   = ZPointerMarkedShift + ZPointerMarkedBits;
const size_t      ZPointerRemappedBits    = 4;
const uintptr_t   ZPointerRemappedMask    = z_pointer_mask(ZPointerRemappedShift, ZPointerRemappedBits);

const uintptr_t   ZPointerRemapped00      = z_pointer_bit(ZPointerRemappedShift, 0);
const uintptr_t   ZPointerRemapped01      = z_pointer_bit(ZPointerRemappedShift, 1);
const uintptr_t   ZPointerRemapped10      = z_pointer_bit(ZPointerRemappedShift, 2);
const uintptr_t   ZPointerRemapped11      = z_pointer_bit(ZPointerRemappedShift, 3);

// The shift table is tightly coupled with the zpointer layout given above
constexpr int     ZPointerLoadShiftTable[] = {
  ZPointerRemappedShift + ZPointerRemappedShift, // [0] Null
  ZPointerRemappedShift + 1,                     // [1] Remapped00
  ZPointerRemappedShift + 2,                     // [2] Remapped01
  0,
  ZPointerRemappedShift + 3,                     // [4] Remapped10
  0,
  0,
  0,
  ZPointerRemappedShift + 4                      // [8] Remapped11
};

// Barrier metadata masks
const uintptr_t   ZPointerLoadMetadataMask  = ZPointerRemappedMask;
const uintptr_t   ZPointerMarkMetadataMask  = ZPointerLoadMetadataMask | ZPointerMarkedMask;
const uintptr_t   ZPointerStoreMetadataMask = ZPointerMarkMetadataMask | ZPointerRememberedMask;
const uintptr_t   ZPointerAllMetadataMask   = ZPointerStoreMetadataMask;

// The current expected bit
extern uintptr_t  ZPointerRemapped;
extern uintptr_t  ZPointerMarkedOld;
extern uintptr_t  ZPointerMarkedYoung;
extern uintptr_t  ZPointerFinalizable;
extern uintptr_t  ZPointerRemembered;

// The current expected remap bit for the young (or old) collection is either of two bits.
// The other collection alternates the bits, so we need to use a mask.
extern uintptr_t  ZPointerRemappedYoungMask;
extern uintptr_t  ZPointerRemappedOldMask;

// Good/bad masks
extern uintptr_t  ZPointerLoadGoodMask;
extern uintptr_t  ZPointerLoadBadMask;

extern uintptr_t  ZPointerMarkGoodMask;
extern uintptr_t  ZPointerMarkBadMask;

extern uintptr_t  ZPointerStoreGoodMask;
extern uintptr_t  ZPointerStoreBadMask;

extern uintptr_t  ZPointerVectorLoadBadMask[8];
extern uintptr_t  ZPointerVectorStoreBadMask[8];
extern uintptr_t  ZPointerVectorStoreGoodMask[8];

// The bad mask is 64 bit. Its low order 32 bits contain all possible value combinations
// that this mask will have. Therefore, the memory where the 32 low order bits are stored
// can be used as a 32 bit GC epoch counter, that has a different bit pattern every time
// the bad mask is flipped. This provides a pointer to such 32 bits.
extern uint32_t*  ZPointerStoreGoodMaskLowOrderBitsAddr;
const int         ZPointerStoreGoodMaskLowOrderBitsOffset = LITTLE_ENDIAN_ONLY(0) BIG_ENDIAN_ONLY(4);

// Offsets
// - Virtual address range offsets
// - Physical memory offsets
enum class zoffset         : uintptr_t {};
// Offsets including end of offset range
enum class zoffset_end     : uintptr_t {};

// Colored oop
enum class zpointer        : uintptr_t { null = 0 };

// Uncolored oop - safe to dereference
enum class zaddress        : uintptr_t { null = 0 };

// Uncolored oop - not safe to dereference, could point uncommitted memory
enum class zaddress_unsafe : uintptr_t { null = 0 };

class ZOffset : public AllStatic {
public:
  static zaddress address(zoffset offset);
  static zaddress_unsafe address_unsafe(zoffset offset);
};

class ZPointer : public AllStatic {
public:
  static zaddress uncolor(zpointer ptr);
  static zaddress uncolor_store_good(zpointer ptr);
  static zaddress_unsafe uncolor_unsafe(zpointer ptr);

  static bool is_load_bad(zpointer ptr);
  static bool is_load_good(zpointer ptr);
  static bool is_load_good_or_null(zpointer ptr);

  static bool is_old_load_good(zpointer ptr);
  static bool is_young_load_good(zpointer ptr);

  static bool is_mark_bad(zpointer ptr);
  static bool is_mark_good(zpointer ptr);
  static bool is_mark_good_or_null(zpointer ptr);

  static bool is_store_bad(zpointer ptr);
  static bool is_store_good(zpointer ptr);
  static bool is_store_good_or_null(zpointer ptr);

  static bool is_marked_finalizable(zpointer ptr);
  static bool is_marked_old(zpointer ptr);
  static bool is_marked_young(zpointer ptr);
  static bool is_marked_any_old(zpointer ptr);
  static bool is_remapped(zpointer ptr);
  static bool is_remembered_exact(zpointer ptr);

  static constexpr int load_shift_lookup_index(uintptr_t value);
  static constexpr int load_shift_lookup(uintptr_t value);
  static uintptr_t remap_bits(uintptr_t colored);
};

class ZAddress : public AllStatic {
public:
  static zpointer color(zaddress addr, uintptr_t color);
  static zpointer color(zaddress_unsafe addr, uintptr_t color);

  static zoffset offset(zaddress addr);
  static zoffset offset(zaddress_unsafe addr);

  static zpointer load_good(zaddress addr, zpointer prev);
  static zpointer finalizable_good(zaddress addr, zpointer prev);
  static zpointer mark_good(zaddress addr, zpointer prev);
  static zpointer mark_old_good(zaddress addr, zpointer prev);
  static zpointer mark_young_good(zaddress addr, zpointer prev);
  static zpointer store_good(zaddress addr);
  static zpointer store_good_or_null(zaddress addr);
};

class ZGlobalsPointers : public AllStatic {
  friend class ZAddressTest;

private:
  static void set_good_masks();
  static void pd_set_good_masks();

public:
  static void initialize();

  static void flip_young_mark_start();
  static void flip_young_relocate_start();
  static void flip_old_mark_start();
  static void flip_old_relocate_start();
};

#endif // SHARE_GC_Z_ZADDRESS_HPP
