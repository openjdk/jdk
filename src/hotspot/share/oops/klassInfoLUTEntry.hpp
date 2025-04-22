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
#include "oops/objLayout.hpp"
#include "utilities/globalDefinitions.hpp"

class ArrayKlass;
class InstanceKlass;
class Klass;
class oopDesc;
class OopMapBlock;
class outputStream;

//                                     msb                                       lsb
//
// invalid_entry (debug zap):             1111 1111 1111 1111 1111 1111 1111 1111  (relies on ClassKind == 0b111 == 7 being invalid)
//
// All valid entries:                     KKKL L... .... .... .... .... .... ....
//
// InstanceKlass:                         KKKL LSSS SSSO OOOO CCCC CCOO OOCC CCCC
//                                                     2 2222 2222 2211 1111 1111
// InstanceKlass, has_no_addinfo:         KKKL L000 0000 0000 0000 0000 0000 0000  (all IK specific bits 0) (note: means that "0" is a valid IK entry with no add. info)
// InstanceKlass, has no oopmap entries:  KKKL LSSS SSS. .... .... .... 0000 0000  (omb count bits are 0)   (only valid if !has_no_addinfo)
//
// ArrayKlass:                            KKKL L--- ---- ---- ---- ---- -eeh hhhh
//
//
// IK specific bits:
// C1 : Count of first OMB                        (6 bits)
// O1 : Offset, in number-of-oops, of first OMB   (4 bits)
// C2 : Count of second OMB                       (6 bits)
// O2 : Offset, in number-of-oops, of second OMB  (5 bits)
// S  : Object instance size in words             (6 bits)
//
// AK specific bits:
// h : header size                                (5 bits)
// e : log2 element size                          (2 bits)
//
// Common bits:
// L : Loader                                     (2 bits)
//    (0 unknown, 1 boot loader,
//     2 system loader, 3 platform loader)
// K : KlassKind                                  (3 bits)
//
// - : unused

class KlassLUTEntry {

#define ALL_KLASS_KINDS_DO(what) \
    what(InstanceKlass, IK) \
    what(InstanceRefKlass, IRK) \
    what(InstanceMirrorKlass, IMK) \
    what(InstanceClassLoaderKlass, ICLK) \
    what(InstanceStackChunkKlass, ISCK) \
    what(TypeArrayKlass, TAK) \
    what(ObjArrayKlass, OAK) \

  // Todo: move KlassKind out of Klass
  // Don't want to include it here for all the baggage it brings
  enum LocalKlassKind {
#define XX(name, ignored) name ## Kind,
    ALL_KLASS_KINDS_DO(XX)
#undef XX
    UnknownKlassKind,
    LastKlassKind = ObjArrayKlassKind,
    FirstArrayKlassKind = TypeArrayKlassKind
  };

  static constexpr int bits_total      = 32;

  // All valid entries:  KKKB ---- ---- ---- ---- ---- ---- ----
  static constexpr int bits_kind       = 3;
  static constexpr int bits_loader     = 2;
  static constexpr int bits_common     = bits_kind + bits_loader;
  static constexpr int bits_specific   = bits_total - bits_common;

  // Bits valid for all entries, regardless of Klass kind
  struct KE {
    // lsb
    unsigned kind_specific_bits : bits_specific;
    unsigned loader             : bits_loader;
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
  static constexpr int bits_ak_l2esz      = 2;
  static constexpr int bits_ak_hsz        = 5;
  struct AKE {
    // lsb
    unsigned hsz    : bits_ak_hsz;    // header size (offset to first element) in bytes
    unsigned l2esz  : bits_ak_l2esz;  // log2 elem size
    unsigned unused : bits_specific - bits_ak_l2esz - bits_ak_hsz;
    unsigned other  : bits_common;
    // msb
  };

  union U {
    uint32_t raw;
    KE common;
    IKE ike;
    AKE ake;
    U(uint32_t v) : raw(v) {}
  };

  U _v;

  // The limits to what we can numerically represent in an (InstanceKlass) Entry
  static constexpr size_t ik_wordsize_limit = nth_bit(bits_ik_wordsize);
  static constexpr size_t ik_omb_offset_1_limit = nth_bit(bits_ik_omb_offset_1);
  static constexpr size_t ik_omb_count_1_limit = nth_bit(bits_ik_omb_count_1);
  static constexpr size_t ik_omb_offset_2_limit = nth_bit(bits_ik_omb_offset_2);
  static constexpr size_t ik_omb_count_2_limit = nth_bit(bits_ik_omb_count_2);

  static uint32_t build_from_ik(const InstanceKlass* k, const char*& not_encodable_reason);
  static uint32_t build_from_ak(const ArrayKlass* k);

public:

  // Invalid entries are entries that have not been set yet.
  // Note: cannot use "0" as invalid_entry, since 0 is valid (interface or abstract InstanceKlass, size = 0 and has no oop map)
  // We use kind=7=0b111 (invalid), and set the rest of the bits also to 1
  static constexpr uint32_t invalid_entry = 0xFFFFFFFF;

  inline KlassLUTEntry() : _v(invalid_entry) {}
  inline KlassLUTEntry(uint32_t v) : _v(v) {}
  inline KlassLUTEntry(const KlassLUTEntry& other) : _v(other._v) {}

  inline KlassLUTEntry& operator=(uint32_t v)                   { _v = v; return *this; }
  inline KlassLUTEntry& operator=(const KlassLUTEntry& other)   { _v = other._v; return *this; }

  inline bool operator==(const KlassLUTEntry& other) const          { return _v.raw == other._v.raw; }
  inline bool operator!=(const KlassLUTEntry& other) const          { return _v.raw != other._v.raw; }

  // Note: all entries should be valid. An invalid entry indicates
  // an error somewhere.
  bool is_valid() const   { return _v.raw != invalid_entry; }

  static KlassLUTEntry build_from_klass(const Klass* k);

#ifdef ASSERT
  void verify_against_klass(const Klass* k) const;
#endif

  uint32_t value() const { return _v.raw; }

  inline unsigned kind() const { return _v.common.kind; }

  // returns loader index (0 for unknown)
  inline int loader_index() const { return _v.common.loader; }

  bool is_array() const     { return _v.common.kind >= FirstArrayKlassKind; }
  bool is_instance() const  { return !is_array(); }

  bool is_obj_array() const  { return _v.common.kind == ObjArrayKlassKind; }
  bool is_type_array() const { return _v.common.kind == TypeArrayKlassKind; }

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

  // returns log2 element size
  inline unsigned ak_log2_elem_size() const { return _v.ake.l2esz; }

  // returns distance to first element
  inline unsigned ak_header_size() const { return _v.ake.hsz; }

  // for an oak, calculates word size given header size, element size, and array length
  template <HeaderMode mode, class OopType>
  inline size_t oak_calculate_wordsize_given_oop_fast(oopDesc* obj) const;

  // for a tak, calculates word size given header size, element size, and array length
  template <HeaderMode mode>
  inline size_t tak_calculate_wordsize_given_oop_fast(oopDesc* obj) const;

  // Helper function, prints current limits
  static void print_limits(outputStream* st);

  void print(outputStream* st) const;

}; // KlassInfoLUEntry

#endif // SHARE_OOPS_KLASSINFOLUTENTRY_HPP
