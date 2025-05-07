/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_OOPS_KLASSINFOLUTENTRY_HPP
#define SHARE_OOPS_KLASSINFOLUTENTRY_HPP

// Included by oop.hpp and klass.hpp, keep includes short
#include "memory/allStatic.hpp"
#include "oops/klassKind.hpp"
#include "oops/objLayout.hpp"
#include "utilities/globalDefinitions.hpp"

class ArrayKlass;
class InstanceKlass;
class Klass;
class oopDesc;
class objArrayOopDesc;
class OopMapBlock;
class outputStream;
class typeArrayOopDesc;

// A Klass Info Lookup Table Entry (klute) is a 32-bit value carrying, in a very condensed form, some of the most
// important information about a Klass.
//
// It carries the following information:
// - The Klass kind
// - The ClassLoaderData association (if the Klass belongs to one of the three permanent CLDs - boot, system, app)
//
// - For InstanceKlass klasses, it may carry more information iff the object satisfies the following conditions:
//   - its size, in words, is less than 64 heap words (512 bytes)
//   - it has less than three oop map entries, and these oop map entries are within certain limits for position and count
// - in that case, the klute carries the object size information and information for both entries.
//
// - For ArrayKlass klasses, it carries parts of the layout helper needed to calculate the object size.
//
// ----------------- Common bits ---------------------------------
//
// These bits are always populated.
//
// Bit    31          27          23          19          15          11          7           3        0
//        K  K  K  L  L  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -
//        \     /  \ /
//         -----    -
//         kind     loader
//
// K (3 bits): The Klass kind. Note that 0b111 = 7 is not a valid KlassKind, and therefore used (see below) to designate an invalid klute).
// L (2 bits): Whether the Klass is associated with one of the three permanent CLDs:
//             '0' unknown CLD, '1' boot loader CLD, '2' system loader CLD, '3' platform loader CLD
//
//
// ----------------- InstanceKlass encoding ----------------------
//
// Bit    31          27          23          19          15          11          7           3        0
//        K  K  K  L  L  S  S  S  S  S  S  O2 O2 O2 O2 O2 C2 C2 C2 C2 C2 C2 O1 O1 O1 O1 C1 C1 C1 C1 C1 C1
//                       \             /   \                              / \                          /
//                        -------------     ------------------------------   --------------------------
//                         obj size           offset, count for oop map 2     offset, count for oop map 1
//
// C1 (6 bits): Count of first Oop Map Entry
// O1 (4 bits): Offset, in number-of-(oop|narrowOop), of second Oop Map Entry
// C2 (6 bits): Count of first Oop Map Entry
// O2 (5 bits): Offset, in number-of-(oop|narrowOop), of second Oop Map Entry
// S  (5 bits): Object instance size in heap words
//
// If the InstanceKlass cannot be represented by this scheme (instance size too large, too many or too large oop map entries), then
// the IK-specific bits are all zero'd out (this is rare):
//
// Bit    31          27          23          19          15          11          7           3        0
//        K  K  K  L  L  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
//
//
// ----------------- ArrayKlass encoding -------------------------
//
// Bit   31          27          23          19          15          11          7           3        0
//       K  K  K  L  L  0  0  0  0  0  0  0  0  0  0  0  E  E  E  E  E  E  E  E  H  H  H  H  H  H  H  H
//                      \                             /  \                    /  \                    /
//                       -----------------------------    --------------------    --------------------
//                             unused                        log2 elem size          header size
//
//
// H (5 bits): header size, in bytes (same as layouthelper header size)
// E (2 bits): log2 elem size, in bytes
//
//
// ----------------- Invalid Klute encoding -------------------------
//
// A klute that has all bits set (1) is invalid:
//
// Bit   31          27          23          19          15          11          7           3        0
//       1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
//
// It is the KLUT table entry initialization value (KLUT is zapped with '-1' on startup).
//
// The "invalid"  designates an invalid entry that should never be encountered at runtime. When doing a lookup
// with a narrowKlass value into the KLUT table, one should always find a valid klute, since a narrowKlass value
// can only result from a Klass that was loaded, and as part of Klass creation, the klute table entry is created.
//
// Implementation note: the value -1 (all bits 1) relies on the fact that a KlassKind of 7 (0b111) is invalid. We
// don't use zero as "invalid entry" since zero would encode a valid Klass.

typedef uint32_t klute_raw_t;

class KlassLUTEntry {

  static constexpr int bits_total      = 32;

  // All valid entries:  KKKB ---- ---- ---- ---- ---- ---- ----
  static constexpr int bits_kind       = 3;
  static constexpr int bits_cld_index  = 2;
  static constexpr int bits_common     = bits_kind + bits_cld_index;
  static constexpr int bits_specific   = bits_total - bits_common;

  // Bits valid for all entries, regardless of Klass kind
  struct KE {
    // lsb
    unsigned kind_specific_bits : bits_specific;
    unsigned cld_index          : bits_cld_index;
    unsigned kind               : bits_kind;
    // msb
  };

  // Bits only valid for InstanceKlass
  static constexpr int bits_ik_omb_count_1  = 6;
  static constexpr int bits_ik_omb_offset_1 = 4;
  static constexpr int bits_ik_omb_count_2  = 6;
  static constexpr int bits_ik_omb_offset_2 = 5;
  static constexpr int bits_ik_omb_bits   = bits_ik_omb_count_1 + bits_ik_omb_offset_1 + bits_ik_omb_count_2 + bits_ik_omb_offset_2;
  static constexpr int bits_ik_wordsize   = bits_specific - bits_ik_omb_bits;
  struct IKE {
    // lsb
    unsigned omb_count_1  : bits_ik_omb_count_1;
    unsigned omb_offset_1 : bits_ik_omb_offset_1;
    unsigned omb_count_2  : bits_ik_omb_count_2;
    unsigned omb_offset_2 : bits_ik_omb_offset_2;
    unsigned wordsize     : bits_ik_wordsize;
    unsigned other        : bits_common;
    // msb
  };

  // Bits only valid for ArrayKlass
  static constexpr int bits_ak_l2esz      = 8;
  static constexpr int bits_ak_hsz        = 8;
  struct AKE {
    // lsb
    unsigned hsz    : bits_ak_hsz;    // header size (offset to first element) in bytes
    unsigned l2esz  : bits_ak_l2esz;  // log2 elem size
    unsigned unused : bits_specific - bits_ak_l2esz - bits_ak_hsz;
    unsigned other  : bits_common;
    // msb
  };

  union U {
    klute_raw_t raw;
    KE common;
    IKE ike;
    AKE ake;
    U(klute_raw_t v) : raw(v) {}
  };

  const U _v;

  // The limits to what we can numerically represent in an (InstanceKlass) Entry
  static constexpr size_t ik_wordsize_limit = nth_bit(bits_ik_wordsize);
  static constexpr size_t ik_omb_offset_1_limit = nth_bit(bits_ik_omb_offset_1);
  static constexpr size_t ik_omb_count_1_limit = nth_bit(bits_ik_omb_count_1);
  static constexpr size_t ik_omb_offset_2_limit = nth_bit(bits_ik_omb_offset_2);
  static constexpr size_t ik_omb_count_2_limit = nth_bit(bits_ik_omb_count_2);

  static klute_raw_t build_from_common(const Klass* k);
  static klute_raw_t build_from_ik(const InstanceKlass* k, const char*& not_encodable_reason);
  static klute_raw_t build_from_ak(const ArrayKlass* k);

public:

  // Invalid entries are entries that have not been set yet.
  // Note: cannot use "0" as invalid_entry, since 0 is valid (interface or abstract InstanceKlass, size = 0 and has no oop map)
  // We use kind=7=0b111 (invalid), and set the rest of the bits also to 1
  static constexpr klute_raw_t invalid_entry = 0xFFFFFFFF;

  inline KlassLUTEntry(klute_raw_t v) : _v(v) {}
  inline KlassLUTEntry(const KlassLUTEntry& other) : _v(other._v) {}

  // Note: all entries should be valid. An invalid entry indicates
  // an error somewhere.
  bool is_valid() const   { return _v.raw != invalid_entry; }

  // Given a Klass, construct a klute from it.
  static klute_raw_t build_from_klass(const Klass* k);

  bool is_valid_for_klass(const Klass* k) const;

  DEBUG_ONLY(void verify_against_klass(const Klass* k) const;)

  klute_raw_t value() const     { return _v.raw; }

  inline unsigned kind() const  { return _v.common.kind; }

  // returns loader index (0 for unknown)
  inline int cld_index() const  { return _v.common.cld_index; }

  bool is_array() const         { return _v.common.kind >= TypeArrayKlassKind; }
  bool is_instance() const      { return !is_array(); }

  bool is_obj_array() const     { return _v.common.kind == ObjArrayKlassKind; }
  bool is_type_array() const    { return _v.common.kind == TypeArrayKlassKind; }

  // Following methods only if IK:

  // Returns true if entry carries IK-specific info (oop map block info + size).
  // If false, caller needs to look up these infor Klass*.
  inline bool ik_carries_infos() const;

  // Following methods only if IK *and* ik_carries_infos() == true:

  // Returns size, in words, of oops of this class
  inline int ik_wordsize() const;

  // Returns count of first OopMapBlock, 0 if there is no oop map block.
  inline unsigned ik_omb_count_1() const;

  // Returns offset of first OopMapBlock in number-of-oops (so, scaled by BytesPerHeapOop).
  inline unsigned ik_omb_offset_1() const;

  // Returns count of second OopMapBlock, 0 if there is no second oop map block.
  inline unsigned ik_omb_count_2() const;

  // Returns offset of second OopMapBlock in number-of-oops (so, scaled by BytesPerHeapOop).
  inline unsigned ik_omb_offset_2() const;

  // Following methods only if AK:

  // returns log2 element size in bytes
  inline unsigned ak_log2_elem_size() const { return _v.ake.l2esz; }

  // returns offset of first array element, in bytes
  inline unsigned ak_first_element_offset_in_bytes() const { return _v.ake.hsz; }

  // for an oak, calculates word size given header size, element size, and array length
  template <HeaderMode mode, class OopType>
  inline size_t oak_calculate_wordsize_given_oop_fast(objArrayOopDesc* obj) const;

  // for a tak, calculates word size given header size, element size, and array length
  template <HeaderMode mode>
  inline size_t tak_calculate_wordsize_given_oop_fast(typeArrayOopDesc* obj) const;

  // Helper function, prints current limits
  static void print_limits(outputStream* st);

  void print(outputStream* st) const;

}; // KlassInfoLUEntry

#define KLUTE_FORMAT INT32_FORMAT_X_0

#endif // SHARE_OOPS_KLASSINFOLUTENTRY_HPP
