/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
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
// |     mm      | Marked minor bits | MarkedMinor[0, 1]        |
// +-------------+-------------------+--------------------------+
// |     MM      | Marked major bits | MarkedMajor[0, 1]        |
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
// The overlapping is performed because the JIT-compiled load barriers expect the
// address bits to start right after the load-good bit. It allows combining the good
// bit check and unmasking into a single speculative shift instruction.
//
// The remapped bits are notably not grouped into two sets of bits, one for the minor
// collection and one for the major collection, like the other bits. The reason is that
// the load barrier is only compatible with bit patterns where there is a single zero in
// its bits of operation (the load metadata bit mask). Instead, the single bit that we
// set encodes the combined state of a conceptual RemappedMinor[0, 1] and
// RemappedMajor[0, 1] pair. The encoding scheme is that the shift of the load good bit,
// minus the shift of the load metadata bit start encodes the numbers 0, 1, 2 and 3.
// These numbers in binary correspond to 00, 01, 10 and 11. The low order bit in said
// numbers correspond to the simulated RemappedMinor[0, 1] value, and the high order bit
// corresponds to the simulated RemappedMajor[0, 1] value.
//
// We decide the bit to be taken by having the RemappedMinorMask and RemappedMajorMask
// variables, which alternate between what two bits they accept for their corresponding
// major and minor phase. The Remapped bit is chosen by taking the intersection of those
// two variables.
//
// RemappedMajorMask alternates between these two bit patterns:
//
//  RemappedMajor0 => 0011
//  RemappedMajor1 => 1100
//
// RemappedMinorMask alternates between these two bit patterns:
//
//  RemappedMinor0 => 0101
//  RemappedMinor1 => 1010
//
// The corresponding intersections look like this:
//
//  RemappedMajor0 & RemappedMinor0 = 0001 = Remapped00
//  RemappedMajor0 & RemappedMinor1 = 0010 = Remapped01
//  RemappedMajor1 & RemappedMinor0 = 0100 = Remapped10
//  RemappedMajor1 & RemappedMinor1 = 1000 = Remapped11

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

// Offsets
// - Virtual address range offsets
// - Physical memory offsets
enum class zoffset         : uintptr_t {};

// Colored oop
enum class zpointer        : uintptr_t { null = 0 };

// Uncolored oop - safe to dereference
enum class zaddress        : uintptr_t { null = 0 };

// Uncolored oop - not safe to dereference
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
  static zpointer set_remset_bits(zpointer ptr);

  static bool is_load_bad(zpointer ptr);
  static bool is_load_good(zpointer ptr);
  static bool is_load_good_or_null(zpointer ptr);

  static bool is_major_load_good(zpointer ptr);
  static bool is_minor_load_good(zpointer ptr);

  static bool is_mark_bad(zpointer ptr);
  static bool is_mark_good(zpointer ptr);
  static bool is_mark_good_or_null(zpointer ptr);

  static bool is_store_bad(zpointer ptr);
  static bool is_store_good(zpointer ptr);
  static bool is_store_good_or_null(zpointer ptr);

  static bool is_marked_finalizable(zpointer ptr);
  static bool is_marked_major(zpointer ptr);
  static bool is_marked_minor(zpointer ptr);
  static bool is_marked_any_major(zpointer ptr);
  static bool is_remapped(zpointer ptr);
  static bool is_remembered_exact(zpointer ptr);

  static constexpr int load_shift_lookup_index(uintptr_t value);
  static constexpr int load_shift_lookup(uintptr_t value);
};

class ZAddress : public AllStatic {
public:
  static zpointer color(zaddress addr, uintptr_t color);

  static zoffset offset(zaddress addr);
  static zoffset offset(zaddress_unsafe addr);

  static zpointer load_good(zaddress addr, zpointer prev);
  static zpointer finalizable_good(zaddress addr, zpointer prev);
  static zpointer mark_good(zaddress addr, zpointer prev);
  static zpointer mark_major_good(zaddress addr, zpointer prev);
  static zpointer mark_minor_good(zaddress addr, zpointer prev);
  static zpointer store_good(zaddress addr);
  static zpointer store_good_or_null(zaddress addr);
};

class ZGlobalsPointers : public AllStatic {
  friend class ZAddressTest;

private:
  static void set_good_masks();

public:
  static void initialize();

  static void flip_minor_mark_start();
  static void flip_minor_relocate_start();
  static void flip_major_mark_start();
  static void flip_major_relocate_start();
};

#endif // SHARE_GC_Z_ZADDRESS_HPP
