/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/klassInfoLUT.hpp"
#include "oops/klassInfoLUTEntry.inline.hpp"
#include "utilities/debug.hpp"

// See klass.hpp
union LayoutHelperHelper {
  unsigned raw;
  struct {
    // lsb
    unsigned lh_esz : 8; // element size
    unsigned lh_ebt : 8; // element BasicType (currently unused)
    unsigned lh_hsz : 8; // header size (offset to first element)
    unsigned lh_tag : 8; // 0x80 or 0xc0
    // msb
  } bytes;
};

// small helper
static void read_and_check_omb_values(const OopMapBlock* omb, unsigned& omb_offset, unsigned& omb_count) {
  const int offset_bytes = omb->offset();
  assert(offset_bytes > 0 && is_aligned(offset_bytes, BytesPerHeapOop),
         "weird or misaligned oop map block offset (%d)", offset_bytes);
  omb_offset = (unsigned)offset_bytes / BytesPerHeapOop;

  const unsigned count = omb->count();
  assert(count > 0, "omb count zero?");
  omb_count = count;
}

uint32_t KlassLUTEntry::build_from_ik(const InstanceKlass* ik, const char*& not_encodable_reason) {

  assert(ik->is_instance_klass(), "sanity");

  const int kind = ik->kind();
  const int lh = ik->layout_helper();
  const int loader_index = KlassInfoLUT::try_register_perma_cld(ik->class_loader_data());

  U value(0);

  // Set common bits, these are always present
  assert(kind < 0b111, "sanity");
  value.common.kind = kind;
  value.common.loader = loader_index;

  // We may not be able to encode the IK-specific info; if we can't, those bits are left zero
  // and we return an error string for logging
  #define NOPE(s) { not_encodable_reason = s; return value.raw; }

  if (Klass::layout_helper_needs_slow_path(lh)) {
    if (ik->is_abstract() || ik->is_interface()) {
      NOPE("Size not trivially computable (klass abstract or interface)");
      // Please note that we could represent abstract or interface classes, but atm there is not
      // much of a point.
    } else {
      NOPE("Size not trivially computable");
    }
  }

  const size_t wordsize = Klass::layout_helper_to_size_helper(lh);
  if (wordsize >= ik_wordsize_limit) {
    NOPE("Size too large");
  }

  // Has more than one nonstatic OopMapBlock?
  const int oop_map_count = ik->nonstatic_oop_map_count();

  unsigned omb_offset_1 = 0, omb_count_1 = 0, omb_offset_2 = 0, omb_count_2 = 0;

  switch(oop_map_count) {
  case 0: // nothing to do
    break;
  case 1:
    read_and_check_omb_values(ik->start_of_nonstatic_oop_maps(), omb_offset_1, omb_count_1);
    break;
  case 2:
    read_and_check_omb_values(ik->start_of_nonstatic_oop_maps(), omb_offset_1, omb_count_1);
    read_and_check_omb_values(ik->start_of_nonstatic_oop_maps() + 1, omb_offset_2, omb_count_2);
    break;
  default:
    NOPE("More than 2 oop map blocks");
  }

  if (omb_offset_1 >= ik_omb_offset_1_limit) {
    NOPE("omb offset 1 overflow");
  }

  if (omb_count_1 >= ik_omb_count_1_limit) {
    NOPE("omb count 1 overflow");
  }

  if (omb_offset_2 >= ik_omb_offset_2_limit) {
    NOPE("omb offset 2 overflow");
  }

  if (omb_count_2 >= ik_omb_count_2_limit) {
    NOPE("omb count 2 overflow");
  }

#undef NOPE
  // Okay, we are good.

  value.ike.wordsize = wordsize;
  value.ike.omb_count_1 = omb_count_1;
  value.ike.omb_offset_1 = omb_offset_1;
  value.ike.omb_count_2 = omb_count_2;
  value.ike.omb_offset_2 = omb_offset_2;

  return value.raw;

}

uint32_t KlassLUTEntry::build_from_ak(const ArrayKlass* ak) {

  assert(ak->is_array_klass(), "sanity");

  const int kind = ak->kind();
  const int lh = ak->layout_helper();
  const int loader_index = KlassInfoLUT::try_register_perma_cld(ak->class_loader_data());

  assert(Klass::layout_helper_is_objArray(lh) || Klass::layout_helper_is_typeArray(lh), "unexpected");

  LayoutHelperHelper lhu = { (unsigned) lh };
  U value(0);
  value.common.kind = kind;
  value.common.loader = loader_index;
  value.ake.lh_ebt = lhu.bytes.lh_ebt;
  value.ake.lh_esz = lhu.bytes.lh_esz;
  value.ake.lh_hsz = lhu.bytes.lh_hsz;
  return value.raw;

}

uint32_t KlassLUTEntry::build_from(const Klass* k) {

  uint32_t value = invalid_entry;
  if (k->is_array_klass()) {
    value = build_from_ak(ArrayKlass::cast(k));
  } else {
    assert(k->is_instance_klass(), "sanity");
    const char* not_encodable_reason = nullptr;
    value = build_from_ik(InstanceKlass::cast(k), not_encodable_reason);
    if (not_encodable_reason != nullptr) {
      log_debug(klut)("klass klute register: %s cannot be encoded because: %s.", k->name()->as_C_string(), not_encodable_reason);
    }
  }
  return value;

}

#ifdef ASSERT

void KlassLUTEntry::verify_against(const Klass* k) const {

  // General static asserts that need access to private members, but I don't want
  // to place them in a header
  STATIC_ASSERT(bits_common + bits_specific == bits_total);
  STATIC_ASSERT(32 == bits_total);
  STATIC_ASSERT(bits_ak_lh <= bits_specific);

  STATIC_ASSERT(sizeof(KE) == sizeof(uint32_t));
  STATIC_ASSERT(sizeof(AKE) == sizeof(uint32_t));
  STATIC_ASSERT(sizeof(IKE) == sizeof(uint32_t));
  STATIC_ASSERT(sizeof(U) == sizeof(uint32_t));

  // Kind must fit into 3 bits
  STATIC_ASSERT(Klass::KLASS_KIND_COUNT < nth_bit(bits_kind));

#define XX(name, ignored) STATIC_ASSERT((int)name ## Kind == (int)Klass::name ## Kind);
  ALL_KLASS_KINDS_DO(XX)
#undef XX

  assert(!is_invalid(), "Entry should be valid (%x)", _v.raw);

  // kind
  const unsigned real_kind = (unsigned)k->kind();
  const unsigned our_kind = kind();
  const int real_lh = k->layout_helper();

  assert(our_kind == real_kind, "kind mismatch (%d vs %d) (%x)", real_kind, our_kind, _v.raw);

  const ClassLoaderData* const real_cld = k->class_loader_data();
  const unsigned loader = loader_index();
  assert(loader < 4, "invalid loader index");
  if (loader > 0) {
    assert(KlassInfoLUT::get_perma_cld(loader) == real_cld, "Different CLD?");
  } else {
    assert(!real_cld->is_permanent_class_loader_data(), "perma cld?");
  }

  if (k->is_array_klass()) {

    // compare our (truncated) lh with the real one
    const LayoutHelperHelper lhu = { (unsigned) real_lh };
    assert(lhu.bytes.lh_ebt == ak_layouthelper_ebt() &&
           lhu.bytes.lh_esz == ak_layouthelper_esz() &&
           lhu.bytes.lh_hsz == ak_layouthelper_hsz() &&
           ( (lhu.bytes.lh_tag == 0xC0 && real_kind == Klass::TypeArrayKlassKind) ||
             (lhu.bytes.lh_tag == 0x80 && real_kind == Klass::ObjArrayKlassKind) ),
             "layouthelper mismatch (0x%x vs 0x%x)", real_lh, _v.raw);

  } else {

    assert(k->is_instance_klass(), "unexpected");
    const InstanceKlass* const ik = InstanceKlass::cast(k);

    const int real_oop_map_count = ik->nonstatic_oop_map_count();
    const unsigned omb_offset_1 = (real_oop_map_count >= 1) ? (unsigned)ik->start_of_nonstatic_oop_maps()[0].offset() : max_uintx;
    const unsigned omb_count_1 =  (real_oop_map_count >= 1) ? ik->start_of_nonstatic_oop_maps()[0].count() : max_uintx;
    const unsigned omb_offset_2 = (real_oop_map_count >= 2) ? (unsigned)ik->start_of_nonstatic_oop_maps()[1].offset() : max_uintx;
    const unsigned omb_count_2 =  (real_oop_map_count >= 2) ? ik->start_of_nonstatic_oop_maps()[1].count() : max_uintx;

    const int real_wordsize = Klass::layout_helper_needs_slow_path(real_lh) ?
        -1 : Klass::layout_helper_to_size_helper(real_lh);

    if (ik_carries_infos()) {

      // check wordsize
      assert(real_wordsize == ik_wordsize(), "wordsize mismatch? (%d vs %d) (%x)", real_wordsize, ik_wordsize(), _v.raw);

      // check omb info
      switch (real_oop_map_count) {
      case 0: {
        assert(ik_omb_offset_1() == 0 && ik_omb_count_1() == 0 &&
               ik_omb_offset_2() == 0 && ik_omb_count_2() == 0, "omb should not be present (0x%x)", _v.raw);
      }
      break;
      case 1: {
        assert(ik_omb_offset_1() * BytesPerHeapOop == omb_offset_1, "first omb offset mismatch (0x%x)", _v.raw);
        assert(ik_omb_count_1() == omb_count_1, "first omb count mismatch (0x%x)", _v.raw);
        assert(ik_omb_offset_2() == 0 && ik_omb_count_2() == 0, "second omb should not be present (0x%x)", _v.raw);
      }
      break;
      case 2: {
        assert(ik_omb_offset_1() * BytesPerHeapOop == omb_offset_1, "first omb offset mismatch (0x%x)", _v.raw);
        assert(ik_omb_count_1() == omb_count_1, "first omb count mismatch (0x%x)", _v.raw);
        assert(ik_omb_offset_2() * BytesPerHeapOop == omb_offset_2, "second omb offset mismatch (0x%x)", _v.raw);
        assert(ik_omb_count_2() == omb_count_2, "second omb count mismatch (0x%x)", _v.raw);
      }
      break;
      default: fatal("More than one oop maps, IKE should not be encodable");
      }
    } else {
      // Check if this Klass should, in fact, have been encodable
      assert( Klass::layout_helper_needs_slow_path(real_lh)       ||
              (real_wordsize >= (int)ik_wordsize_limit)           ||
              (real_oop_map_count > 2)                            ||
              ((size_t) omb_offset_1 >= ik_omb_offset_1_limit)    ||
              ((size_t) omb_count_1 >= ik_omb_count_1_limit)      ||
              ((size_t) omb_offset_2 >= ik_omb_offset_2_limit)    ||
              ((size_t) omb_count_2 >= ik_omb_count_2_limit),
              "Klass should have been encodable" );
    }
  }

} // KlassLUTEntry::verify_against

#endif // ASSERT

KlassLUTEntry::KlassLUTEntry(const Klass* k) : _v(build_from(k)) {
}

// Helper function, prints current limits
void KlassLUTEntry::print_limits(outputStream* st) {
  st->print_cr("IKE Limits: instance byte size %zu, omb1 count: %zu, omb1 byte offset: %zu, omb2 oop count: %zu, omb2 byte offset: %zu",
               ik_wordsize_limit * BytesPerWord,
               ik_omb_count_1_limit, ik_omb_offset_1_limit * BytesPerHeapOop,
               ik_omb_count_2_limit, ik_omb_offset_2_limit * BytesPerHeapOop);
}
