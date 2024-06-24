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

#ifndef SHARE_GC_Z_ZADDRESS_INLINE_HPP
#define SHARE_GC_Z_ZADDRESS_INLINE_HPP

#include "gc/z/zAddress.hpp"

#include "gc/shared/gc_globals.hpp"
#include "oops/oop.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"
#include CPU_HEADER_INLINE(gc/z/zAddress)

// zoffset functions

inline uintptr_t untype(zoffset offset) {
  const uintptr_t value = static_cast<uintptr_t>(offset);
  assert(value < ZAddressOffsetMax, "must have no other bits");
  return value;
}

inline uintptr_t untype(zoffset_end offset) {
  const uintptr_t value = static_cast<uintptr_t>(offset);
  assert(value <= ZAddressOffsetMax, "must have no other bits");
  return value;
}

inline zoffset to_zoffset(uintptr_t value) {
  assert(value < ZAddressOffsetMax, "must have no other bits");
  return zoffset(value);
}

inline zoffset to_zoffset(zoffset_end offset) {
  const uintptr_t value = untype(offset);
  return to_zoffset(value);
}

inline zoffset operator+(zoffset offset, size_t size) {
  return to_zoffset(untype(offset) + size);
}

inline zoffset& operator+=(zoffset& offset, size_t size) {
  offset = to_zoffset(untype(offset) + size);
  return offset;
}

inline zoffset operator-(zoffset offset, size_t size) {
  const uintptr_t value = untype(offset) - size;
  return to_zoffset(value);
}

inline size_t operator-(zoffset left, zoffset right) {
  const size_t diff = untype(left) - untype(right);
  assert(diff < ZAddressOffsetMax, "Underflow");
  return diff;
}

inline zoffset& operator-=(zoffset& offset, size_t size) {
  offset = to_zoffset(untype(offset) - size);
  return offset;
}

inline bool to_zoffset_end(zoffset_end* result, zoffset_end start, size_t size) {
  const uintptr_t value = untype(start) + size;
  if (value <= ZAddressOffsetMax) {
    *result = zoffset_end(value);
    return true;
  }
  return false;
}

inline zoffset_end to_zoffset_end(zoffset start, size_t size) {
  const uintptr_t value = untype(start) + size;
  assert(value <= ZAddressOffsetMax, "Overflow start: " PTR_FORMAT " size: " PTR_FORMAT " value: " PTR_FORMAT,
                                     untype(start), size, value);
  return zoffset_end(value);
}

inline zoffset_end to_zoffset_end(uintptr_t value) {
  assert(value <= ZAddressOffsetMax, "Overflow");
  return zoffset_end(value);
}

inline zoffset_end to_zoffset_end(zoffset offset) {
  return zoffset_end(untype(offset));
}

inline bool operator!=(zoffset first, zoffset_end second) {
  return untype(first) != untype(second);
}

inline bool operator!=(zoffset_end first, zoffset second) {
  return untype(first) != untype(second);
}

inline bool operator==(zoffset first, zoffset_end second) {
  return untype(first) == untype(second);
}

inline bool operator==(zoffset_end first, zoffset second) {
  return untype(first) == untype(second);
}

inline bool operator<(zoffset_end first, zoffset second) {
  return untype(first) < untype(second);
}

inline bool operator<(zoffset first, zoffset_end second) {
  return untype(first) < untype(second);
}

inline bool operator<=(zoffset_end first, zoffset second) {
  return untype(first) <= untype(second);
}

inline bool operator>(zoffset first, zoffset_end second) {
  return untype(first) > untype(second);
}

inline bool operator>=(zoffset first, zoffset_end second) {
  return untype(first) >= untype(second);
}

inline size_t operator-(zoffset_end first, zoffset second) {
  return untype(first) - untype(second);
}

inline zoffset_end operator-(zoffset_end first, size_t second) {
  return to_zoffset_end(untype(first) - second);
}

inline size_t operator-(zoffset_end first, zoffset_end second) {
  return untype(first) - untype(second);
}

inline zoffset_end& operator-=(zoffset_end& offset, size_t size) {
  offset = to_zoffset_end(untype(offset) - size);
  return offset;
}

inline zoffset_end& operator+=(zoffset_end& offset, size_t size) {
  offset = to_zoffset_end(untype(offset) + size);
  return offset;
}

// zpointer functions

#define report_is_valid_failure(str) assert(!assert_on_failure, "%s: " PTR_FORMAT, str, value);

inline bool is_valid(zpointer ptr, bool assert_on_failure = false) {
  if (assert_on_failure && !ZVerifyOops) {
    return true;
  }

  const uintptr_t value = static_cast<uintptr_t>(ptr);

  if (value == 0) {
    // Accept raw null
    return false;
  }

  if ((value & ~ZPointerStoreMetadataMask) != 0) {
#ifndef AARCH64
    const int index = ZPointer::load_shift_lookup_index(value);
    if (index != 0 && !is_power_of_2(index)) {
      report_is_valid_failure("Invalid remap bits");
      return false;
    }
#endif

    const int shift = ZPointer::load_shift_lookup(value);
    if (!is_power_of_2(value & (ZAddressHeapBase << shift))) {
      report_is_valid_failure("Missing heap base");
      return false;
    }

    if (((value >> shift) & 7) != 0) {
      report_is_valid_failure("Alignment bits should not be set");
      return false;
    }
  }

  const uintptr_t load_metadata = ZPointer::remap_bits(value);
  if (!is_power_of_2(load_metadata)) {
    report_is_valid_failure("Must have exactly one load metadata bit");
    return false;
  }

  const uintptr_t store_metadata = (value & (ZPointerStoreMetadataMask ^ ZPointerLoadMetadataMask));
  const uintptr_t marked_young_metadata = store_metadata & (ZPointerMarkedYoung0 | ZPointerMarkedYoung1);
  const uintptr_t marked_old_metadata = store_metadata & (ZPointerMarkedOld0 | ZPointerMarkedOld1 |
                                                          ZPointerFinalizable0 | ZPointerFinalizable1);
  const uintptr_t remembered_metadata = store_metadata & (ZPointerRemembered0 | ZPointerRemembered1);
  if (!is_power_of_2(marked_young_metadata)) {
    report_is_valid_failure("Must have exactly one marked young metadata bit");
    return false;
  }

  if (!is_power_of_2(marked_old_metadata)) {
    report_is_valid_failure("Must have exactly one marked old metadata bit");
    return false;
  }

  if (remembered_metadata == 0) {
    report_is_valid_failure("Must have at least one remembered metadata bit set");
    return false;
  }

  if ((marked_young_metadata | marked_old_metadata | remembered_metadata) != store_metadata) {
    report_is_valid_failure("Must have exactly three sets of store metadata bits");
    return false;
  }

  if ((value & ZPointerReservedMask) != 0) {
    report_is_valid_failure("Dirty reserved bits");
    return false;
  }

  return true;
}

inline void assert_is_valid(zpointer ptr) {
  DEBUG_ONLY(is_valid(ptr, true /* assert_on_failure */);)
}

inline uintptr_t untype(zpointer ptr) {
  return static_cast<uintptr_t>(ptr);
}

inline zpointer to_zpointer(uintptr_t value) {
  assert_is_valid(zpointer(value));
  return zpointer(value);
}

inline zpointer to_zpointer(oopDesc* o) {
  return ::to_zpointer(uintptr_t(o));
}

// Is it exactly null?
inline bool is_null(zpointer ptr) {
  return ptr == zpointer::null;
}

inline bool is_null_any(zpointer ptr) {
  const uintptr_t raw_addr = untype(ptr);
  return (raw_addr & ~ZPointerAllMetadataMask) == 0;
}

// Is it null - colored or not?
inline bool is_null_assert_load_good(zpointer ptr) {
  const bool result = is_null_any(ptr);
  assert(!result || ZPointer::is_load_good(ptr), "Got bad colored null");
  return result;
}

// zaddress functions

inline bool is_null(zaddress addr) {
  return addr == zaddress::null;
}

inline bool is_valid(zaddress addr, bool assert_on_failure = false) {
  if (assert_on_failure && !ZVerifyOops) {
    return true;
  }

  if (is_null(addr)) {
    // Null is valid
    return true;
  }

  const uintptr_t value = static_cast<uintptr_t>(addr);

  if (value & 0x7) {
    // No low order bits
    report_is_valid_failure("Has low-order bits set");
    return false;
  }

  if ((value & ZAddressHeapBase) == 0) {
    // Must have a heap base bit
    report_is_valid_failure("Missing heap base");
    return false;
  }

  if (value >= (ZAddressHeapBase + ZAddressOffsetMax)) {
    // Must not point outside of the heap's virtual address range
    report_is_valid_failure("Address outside of the heap");
    return false;
  }

  return true;
}

inline void assert_is_valid(zaddress addr) {
  DEBUG_ONLY(is_valid(addr, true /* assert_on_failure */);)
}

inline uintptr_t untype(zaddress addr) {
  return static_cast<uintptr_t>(addr);
}

#ifdef ASSERT
inline void dereferenceable_test(zaddress addr) {
  if (ZVerifyOops && !is_null(addr)) {
    // Intentionally crash if the address is pointing into unmapped memory
    (void)Atomic::load((int*)(uintptr_t)addr);
  }
}
#endif

inline zaddress to_zaddress(uintptr_t value) {
  const zaddress addr = zaddress(value);
  assert_is_valid(addr);
  DEBUG_ONLY(dereferenceable_test(addr));
  return addr;
}

inline zaddress to_zaddress(oopDesc* o) {
  return to_zaddress(uintptr_t(o));
}

inline oop to_oop(zaddress addr) {
  const oop obj = cast_to_oop(addr);
  assert(!ZVerifyOops || oopDesc::is_oop_or_null(obj), "Broken oop: " PTR_FORMAT " [" PTR_FORMAT " " PTR_FORMAT " " PTR_FORMAT " " PTR_FORMAT "]",
         p2i(obj),
         *(uintptr_t*)(untype(addr) + 0x00),
         *(uintptr_t*)(untype(addr) + 0x08),
         *(uintptr_t*)(untype(addr) + 0x10),
         *(uintptr_t*)(untype(addr) + 0x18));
  return obj;
}

inline zaddress operator+(zaddress addr, size_t size) {
  return to_zaddress(untype(addr) + size);
}

inline size_t operator-(zaddress left, zaddress right) {
  assert(left >= right, "Unexpected order - left: " PTR_FORMAT " right: " PTR_FORMAT, untype(left), untype(right));
  return untype(left) - untype(right);
}

// zaddress_unsafe functions

inline bool is_null(zaddress_unsafe addr) {
  return addr == zaddress_unsafe::null;
}

inline bool is_valid(zaddress_unsafe addr, bool assert_on_failure = false) {
  return is_valid(zaddress(addr), assert_on_failure);
}

inline void assert_is_valid(zaddress_unsafe addr) {
  DEBUG_ONLY(is_valid(addr, true /* assert_on_failure */);)
}


inline uintptr_t untype(zaddress_unsafe addr) {
  return static_cast<uintptr_t>(addr);
}

// The zaddress_unsafe type denotes that this
// memory isn't guaranteed to be dereferenceable.
// The containing page could have been reclaimed
// and/or uncommitted.
//
// The zaddress type denotes that this memory can
// be dereferenced (runtime verified).
//
// This function can be used when the caller guarantees
// that addr points to dereferenceable memory. Examples
// of cases after which this function can be used:
//
// 1) A load good check on the colored pointer that addr was created from
// 2) A load barrier has self-healed the pointer in addr
// 3) A check that the addr doesn't belong to a relocation set. Since addr
//    could denote two different objects in the two generations, a check
//    against the colored pointer, that addr was created from, is needed to
//    figure out what relocation set to look in.
// 4) From the relocation code
inline zaddress safe(zaddress_unsafe addr) {
  return to_zaddress(untype(addr));
}

inline zaddress_unsafe to_zaddress_unsafe(uintptr_t value) {
  const zaddress_unsafe addr = zaddress_unsafe(value);
  assert_is_valid(addr);
  return addr;
}

inline zaddress_unsafe unsafe(zaddress addr) {
  return to_zaddress_unsafe(untype(addr));
}

inline zaddress_unsafe to_zaddress_unsafe(oop o) {
  return to_zaddress_unsafe(cast_from_oop<uintptr_t>(o));
}

inline zaddress_unsafe operator+(zaddress_unsafe offset, size_t size) {
  return to_zaddress_unsafe(untype(offset) + size);
}

inline size_t operator-(zaddress_unsafe left, zaddress_unsafe right) {
  return untype(left) - untype(right);
}

// ZOffset functions

inline zaddress ZOffset::address(zoffset offset) {
  return to_zaddress(untype(offset) | ZAddressHeapBase);
}

inline zaddress_unsafe ZOffset::address_unsafe(zoffset offset) {
  return to_zaddress_unsafe(untype(offset) | ZAddressHeapBase);
}

// ZPointer functions

inline zaddress ZPointer::uncolor(zpointer ptr) {
  assert(ZPointer::is_load_good(ptr) || is_null_any(ptr),
      "Should be load good when handed out: " PTR_FORMAT, untype(ptr));
  const uintptr_t raw_addr = untype(ptr);
  return to_zaddress(raw_addr >> ZPointer::load_shift_lookup(raw_addr));
}

inline zaddress ZPointer::uncolor_store_good(zpointer ptr) {
  assert(ZPointer::is_store_good(ptr), "Should be store good: " PTR_FORMAT, untype(ptr));
  return uncolor(ptr);
}

inline zaddress_unsafe ZPointer::uncolor_unsafe(zpointer ptr) {
  assert(ZPointer::is_store_bad(ptr), "Unexpected ptr");
  const uintptr_t raw_addr = untype(ptr);
  return to_zaddress_unsafe(raw_addr >> ZPointer::load_shift_lookup(raw_addr));
}

inline bool ZPointer::is_load_bad(zpointer ptr) {
  return untype(ptr) & ZPointerLoadBadMask;
}

inline bool ZPointer::is_load_good(zpointer ptr) {
  return !is_load_bad(ptr) && !is_null(ptr);
}

inline bool ZPointer::is_load_good_or_null(zpointer ptr) {
  // Checking if an address is "not bad" is an optimized version of
  // checking if it's "good or null", which eliminates an explicit
  // null check. However, the implicit null check only checks that
  // the mask bits are zero, not that the entire address is zero.
  // This means that an address without mask bits would pass through
  // the barrier as if it was null. This should be harmless as such
  // addresses should ever be passed through the barrier.
  const bool result = !is_load_bad(ptr);
  assert((is_load_good(ptr) || is_null(ptr)) == result, "Bad address");
  return result;
}

inline bool ZPointer::is_young_load_good(zpointer ptr) {
  assert(!is_null(ptr), "not supported");
  return (remap_bits(untype(ptr)) & ZPointerRemappedYoungMask) != 0;
}

inline bool ZPointer::is_old_load_good(zpointer ptr) {
  assert(!is_null(ptr), "not supported");
  return (remap_bits(untype(ptr)) & ZPointerRemappedOldMask) != 0;
}

inline bool ZPointer::is_mark_bad(zpointer ptr) {
  return untype(ptr) & ZPointerMarkBadMask;
}

inline bool ZPointer::is_mark_good(zpointer ptr) {
  return !is_mark_bad(ptr) && !is_null(ptr);
}

inline bool ZPointer::is_mark_good_or_null(zpointer ptr) {
  // Checking if an address is "not bad" is an optimized version of
  // checking if it's "good or null", which eliminates an explicit
  // null check. However, the implicit null check only checks that
  // the mask bits are zero, not that the entire address is zero.
  // This means that an address without mask bits would pass through
  // the barrier as if it was null. This should be harmless as such
  // addresses should ever be passed through the barrier.
  const bool result = !is_mark_bad(ptr);
  assert((is_mark_good(ptr) || is_null(ptr)) == result, "Bad address");
  return result;
}

inline bool ZPointer::is_store_bad(zpointer ptr) {
  return untype(ptr) & ZPointerStoreBadMask;
}

inline bool ZPointer::is_store_good(zpointer ptr) {
  return !is_store_bad(ptr) && !is_null(ptr);
}

inline bool ZPointer::is_store_good_or_null(zpointer ptr) {
  // Checking if an address is "not bad" is an optimized version of
  // checking if it's "good or null", which eliminates an explicit
  // null check. However, the implicit null check only checks that
  // the mask bits are zero, not that the entire address is zero.
  // This means that an address without mask bits would pass through
  // the barrier as if it was null. This should be harmless as such
  // addresses should ever be passed through the barrier.
  const bool result = !is_store_bad(ptr);
  assert((is_store_good(ptr) || is_null(ptr)) == result, "Bad address");
  return result;
}

inline bool ZPointer::is_marked_finalizable(zpointer ptr) {
  assert(!is_null(ptr), "must not be null");
  return untype(ptr) & ZPointerFinalizable;
}

inline bool ZPointer::is_marked_old(zpointer ptr) {
  return untype(ptr) & (ZPointerMarkedOld);
}

inline bool ZPointer::is_marked_young(zpointer ptr) {
  return untype(ptr) & (ZPointerMarkedYoung);
}

inline bool ZPointer::is_marked_any_old(zpointer ptr) {
  return untype(ptr) & (ZPointerMarkedOld |
                        ZPointerFinalizable);
}

inline bool ZPointer::is_remapped(zpointer ptr) {
  assert(!is_null(ptr), "must not be null");
  return remap_bits(untype(ptr)) & ZPointerRemapped;
}

inline bool ZPointer::is_remembered_exact(zpointer ptr) {
  assert(!is_null(ptr), "must not be null");
  return (untype(ptr) & ZPointerRemembered) == ZPointerRemembered;
}

inline constexpr int ZPointer::load_shift_lookup_index(uintptr_t value) {
  return (value >> ZPointerRemappedShift) & ((1 << ZPointerRemappedBits) - 1);
}

// ZAddress functions

inline zpointer ZAddress::color(zaddress addr, uintptr_t color) {
  return to_zpointer((untype(addr) << ZPointer::load_shift_lookup(color)) | color);
}

inline zpointer ZAddress::color(zaddress_unsafe addr, uintptr_t color) {
  return to_zpointer((untype(addr) << ZPointer::load_shift_lookup(color)) | color);
}

inline zoffset ZAddress::offset(zaddress addr) {
  return to_zoffset(untype(addr) & ZAddressOffsetMask);
}

inline zoffset ZAddress::offset(zaddress_unsafe addr) {
  return to_zoffset(untype(addr) & ZAddressOffsetMask);
}

inline zpointer color_null() {
  return ZAddress::color(zaddress::null, ZPointerStoreGoodMask | ZPointerRememberedMask);
}

inline zpointer ZAddress::load_good(zaddress addr, zpointer prev) {
  if (is_null_any(prev)) {
    return color_null();
  }

  const uintptr_t non_load_bits_mask = ZPointerLoadMetadataMask ^ ZPointerAllMetadataMask;
  const uintptr_t non_load_prev_bits = untype(prev) & non_load_bits_mask;
  return color(addr, ZPointerLoadGoodMask | non_load_prev_bits | ZPointerRememberedMask);
}

inline zpointer ZAddress::finalizable_good(zaddress addr, zpointer prev) {
  if (is_null_any(prev)) {
    return color_null();
  }

  return color(addr, ZPointerLoadGoodMask | ZPointerMarkedYoung | ZPointerFinalizable | ZPointerRememberedMask);
}

inline zpointer ZAddress::mark_good(zaddress addr, zpointer prev) {
  if (is_null_any(prev)) {
    return color_null();
  }

  return color(addr, ZPointerLoadGoodMask | ZPointerMarkedYoung | ZPointerMarkedOld | ZPointerRememberedMask);
}

inline zpointer ZAddress::mark_old_good(zaddress addr, zpointer prev) {
  if (is_null_any(prev)) {
    return color_null();
  }

  const uintptr_t prev_color = untype(prev);

  const uintptr_t young_marked_mask = ZPointerMarkedYoung0 | ZPointerMarkedYoung1;
  const uintptr_t young_marked = prev_color & young_marked_mask;

  return color(addr, ZPointerLoadGoodMask | ZPointerMarkedOld | young_marked | ZPointerRememberedMask);
}

inline zpointer ZAddress::mark_young_good(zaddress addr, zpointer prev) {
  if (is_null_any(prev)) {
    return color_null();
  }

  const uintptr_t prev_color = untype(prev);

  const uintptr_t old_marked_mask = ZPointerMarkedMask ^ (ZPointerMarkedYoung0 | ZPointerMarkedYoung1);
  const uintptr_t old_marked = prev_color & old_marked_mask;

  return color(addr, ZPointerLoadGoodMask | ZPointerMarkedYoung | old_marked | ZPointerRememberedMask);
}

inline zpointer ZAddress::store_good(zaddress addr) {
  return color(addr, ZPointerStoreGoodMask);
}

inline zpointer ZAddress::store_good_or_null(zaddress addr) {
  return is_null(addr) ? zpointer::null : store_good(addr);
}

#endif // SHARE_GC_Z_ZADDRESS_INLINE_HPP
