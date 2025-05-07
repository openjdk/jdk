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

#include "cds/cdsConfig.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/klassInfoLUT.hpp"
#include "oops/klassInfoLUTEntry.inline.hpp"
#include "oops/klassKind.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

// See klass.hpp
union LayoutHelperHelper {
  unsigned raw;
  struct {
    // lsb
    unsigned lh_esz : 8; // log2 element size
    unsigned lh_ebt : 8; // element BasicType
    unsigned lh_hsz : 8; // header size (offset to first element)
    unsigned lh_tag : 8; // 0x80 (IK) or 0xc0 (AK)
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

klute_raw_t KlassLUTEntry::build_from_common(const Klass* k) {
  const int kind = k->kind();
  const ClassLoaderData* const cld = k->class_loader_data();
  unsigned cld_index = KlassInfoLUT::cld_index_unknown;

  if (cld == nullptr) {
#ifdef INCLUDE_CDS
    // Unfortunately, due to AOT delayed class linking (see JDK-8342429), we can
    // encounter Klass that are unlinked and their CLD field is still nullptr.
    // Until JDK-8342429 we accept that and treat it the same as non-permanent CLDs:
    // "unknown CLD, look CLD up in Klass directly".
    // Later, when the CLD field in Klass is set in the course of class linking, we
    // will recalculate the klute based on the new CLD.
    cld_index = KlassInfoLUT::cld_index_unknown;
#else
    fatal("CLD null for Klass " PTR_FORMAT, p2i(k));
#endif
  } else {
    cld_index = KlassInfoLUT::index_for_cld(cld);
  }
  U value(0);
  value.common.kind = kind;
  value.common.cld_index = cld_index;

  return value.raw;
}

klute_raw_t KlassLUTEntry::build_from_ik(const InstanceKlass* ik, const char*& not_encodable_reason) {

  assert(ik->is_instance_klass(), "sanity");

  U value(build_from_common(ik));

  // We may not be able to encode the IK-specific info; if we can't, those bits are left zero
  // and we return an error string for logging
  #define NOPE(s) { not_encodable_reason = s; return value.raw; }

  const int lh = ik->layout_helper();
  if (Klass::layout_helper_needs_slow_path(lh)) {
    if (ik->is_abstract() || ik->is_interface()) {
      NOPE("klass is abstract or interface");
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

klute_raw_t KlassLUTEntry::build_from_ak(const ArrayKlass* ak) {

  assert(ak->is_array_klass(), "sanity");

  U value(build_from_common(ak));


  const int lh = ak->layout_helper();
  assert(Klass::layout_helper_is_objArray(lh) || Klass::layout_helper_is_typeArray(lh), "unexpected");

  LayoutHelperHelper lhu = { (unsigned) lh };

  assert(lhu.bytes.lh_esz <= 3, "Sanity (%X)", lh);
  value.ake.l2esz = lhu.bytes.lh_esz;

  assert(lhu.bytes.lh_hsz >= 12 && lhu.bytes.lh_hsz <= 24, "Sanity");
  value.ake.hsz = lhu.bytes.lh_hsz;

  return value.raw;
}

klute_raw_t KlassLUTEntry::build_from_klass(const Klass* k) {

  klute_raw_t value = invalid_entry;
  if (k->is_array_klass()) {
    value = build_from_ak(ArrayKlass::cast(k));
  } else {
    assert(k->is_instance_klass(), "sanity");
    const char* not_encodable_reason = nullptr;
    value = build_from_ik(InstanceKlass::cast(k), not_encodable_reason);
    if (not_encodable_reason != nullptr) {
      log_debug(klut)("InstanceKlass " PTR_FORMAT ": (%s) cannot encode details: %s.", p2i(k),
                      k->name()->as_C_string(), not_encodable_reason);
    }
  }
  return value;
}

#ifdef ASSERT
void KlassLUTEntry::verify_against_klass(const Klass* k) const {

#define PREAMBLE_FORMAT               "Klass: " PTR_FORMAT "(%s), klute " KLUTE_FORMAT ": "
#define PREAMBLE_ARGS                 p2i(k), k->external_name(), _v.raw
#define ASSERT_HERE(cond, msg)        assert( (cond), PREAMBLE_FORMAT msg, PREAMBLE_ARGS);
#define ASSERT_HERE2(cond, msg, ...)  assert( (cond), PREAMBLE_FORMAT msg, PREAMBLE_ARGS, __VA_ARGS__);

  assert(k->check_stamp(), "Stamp invalid");

  // General static asserts that need access to private members, but I don't want
  // to place them in a header
  STATIC_ASSERT(bits_common + bits_specific == bits_total);
  STATIC_ASSERT(32 == bits_total);

  STATIC_ASSERT(sizeof(KE) == sizeof(uint32_t));
  STATIC_ASSERT(sizeof(AKE) == sizeof(uint32_t));
  STATIC_ASSERT(sizeof(IKE) == sizeof(uint32_t));
  STATIC_ASSERT(sizeof(U) == sizeof(uint32_t));

  // Kind must fit into 3 bits
  STATIC_ASSERT(KlassKindCount < nth_bit(bits_kind));

  ASSERT_HERE(is_valid(), "klute invalid");

  // kind
  const unsigned real_kind = (unsigned)k->kind();
  const unsigned our_kind = kind();
  const int real_lh = k->layout_helper();

  ASSERT_HERE2(our_kind == real_kind, "kind mismatch (%d vs %d)", real_kind, our_kind);

  const ClassLoaderData* const real_cld = k->class_loader_data();
  const unsigned cld_index = loader_index();
  ASSERT_HERE(cld_index < 4, "invalid loader index");

  if (real_cld == nullptr) {
    // With AOT, we can encounter Klasses that have not set their CLD yet. Until that is solved (JDK-8342429),
    // work around this problem.
    constexpr bool tolerate_aot_unlinked_classes = CDS_ONLY(true) NOT_CDS(false);
    if (tolerate_aot_unlinked_classes) {
      // We expect the klute to show "unknown CLD"
      ASSERT_HERE2(cld_index == KlassInfoLUT::cld_index_unknown,
                   "for CLD==nullptr cld_index is expected to be %u, was %u",
                   KlassInfoLUT::cld_index_unknown, cld_index);
    } else {
      ASSERT_HERE(real_cld != nullptr, "Klass CLD is null?");
    }
  } else {
    const ClassLoaderData* const cld_from_klute = KlassInfoLUT::lookup_cld(cld_index);
    if (cld_index != KlassInfoLUT::cld_index_unknown) {
      // We expect to get one of the three permanent class loaders, and for it to match the one in Klass
      ASSERT_HERE2(cld_from_klute->is_permanent_class_loader_data(), "not perma cld (loader_index: %d, CLD: " PTR_FORMAT ")",
                   cld_index, p2i(cld_from_klute));
      ASSERT_HERE2(cld_from_klute == real_cld,
                   "Different CLD (loader_index: %d, real Klass CLD: " PTR_FORMAT ", from klute CLD lookup table: " PTR_FORMAT ")?",
                   cld_index, p2i(real_cld), p2i(cld_from_klute));
    } else {
      // We expect to get a NULL from the CLD lookup table.
      ASSERT_HERE2(cld_from_klute == nullptr, "CLD not null? (" PTR_FORMAT ")", p2i(cld_from_klute));
      // cld_index == 0 means "unknown CLD" and since we expect only to ever run with three permanent CLDs,
      // that cld should not be permanent.
      ASSERT_HERE2(!real_cld->is_permanent_class_loader_data(),
                   "Unregistered permanent CLD? (" PTR_FORMAT ")", p2i(cld_from_klute));
    }
  }

  if (k->is_array_klass()) {
    // compare klute information with the information from the layouthelper
    const LayoutHelperHelper lhu = { (unsigned) real_lh };
    ASSERT_HERE2(lhu.bytes.lh_esz == ak_log2_elem_size() &&
                 lhu.bytes.lh_hsz == ak_first_element_offset_in_bytes() &&
                 ( (lhu.bytes.lh_tag == 0xC0 && real_kind == TypeArrayKlassKind) ||
                 (lhu.bytes.lh_tag == 0x80 && real_kind == ObjArrayKlassKind) ),
                 "layouthelper mismatch (lh from Klass: 0x%x", real_lh);
  } else {

    ASSERT_HERE(k->is_instance_klass(), "unexpected");
    const InstanceKlass* const ik = InstanceKlass::cast(k);

    const int real_oop_map_count = ik->nonstatic_oop_map_count();
    const unsigned omb_offset_1 = (real_oop_map_count >= 1) ? (unsigned)ik->start_of_nonstatic_oop_maps()[0].offset() : UINT_MAX;
    const unsigned omb_count_1 =  (real_oop_map_count >= 1) ? ik->start_of_nonstatic_oop_maps()[0].count() : UINT_MAX;
    const unsigned omb_offset_2 = (real_oop_map_count >= 2) ? (unsigned)ik->start_of_nonstatic_oop_maps()[1].offset() : UINT_MAX;
    const unsigned omb_count_2 =  (real_oop_map_count >= 2) ? ik->start_of_nonstatic_oop_maps()[1].count() : UINT_MAX;

    const int real_wordsize = Klass::layout_helper_needs_slow_path(real_lh) ?
        -1 : Klass::layout_helper_to_size_helper(real_lh);

    if (ik_carries_infos()) {

      // check wordsize
      ASSERT_HERE2(real_wordsize == ik_wordsize(), "wordsize mismatch? (%d vs %d)", real_wordsize, ik_wordsize());

      // check omb info
      switch (real_oop_map_count) {
      case 0: {
        ASSERT_HERE(ik_omb_offset_1() == 0 && ik_omb_count_1() == 0 &&
                    ik_omb_offset_2() == 0 && ik_omb_count_2() == 0,
                    "omb should not be present");
      }
      break;
      case 1: {
        ASSERT_HERE(ik_omb_offset_1() * BytesPerHeapOop == omb_offset_1, "first omb offset mismatch");
        ASSERT_HERE(ik_omb_count_1() == omb_count_1, "first omb count mismatch");
        ASSERT_HERE(ik_omb_offset_2() == 0 && ik_omb_count_2() == 0, "second omb should not be present");
      }
      break;
      case 2: {
        ASSERT_HERE(ik_omb_offset_1() * BytesPerHeapOop == omb_offset_1, "first omb offset mismatch");
        ASSERT_HERE(ik_omb_count_1() == omb_count_1, "first omb count mismatch");
        ASSERT_HERE(ik_omb_offset_2() * BytesPerHeapOop == omb_offset_2, "second omb offset mismatch");
        ASSERT_HERE(ik_omb_count_2() == omb_count_2, "second omb count mismatch");
      }
      break;
      default: fatal("More than one oop maps, IKE should not have been fully encodable");
      }
    } else {
      // Check if this Klass should, in fact, have been fully encodable
      ASSERT_HERE(Klass::layout_helper_needs_slow_path(real_lh)       ||
                  (real_wordsize >= (int)ik_wordsize_limit)           ||
                  (real_oop_map_count > 2)                            ||
                  ((size_t) omb_offset_1 >= ik_omb_offset_1_limit)    ||
                  ((size_t) omb_count_1 >= ik_omb_count_1_limit)      ||
                  ((size_t) omb_offset_2 >= ik_omb_offset_2_limit)    ||
                  ((size_t) omb_count_2 >= ik_omb_count_2_limit),
                  "Klass should have been encodable" );
    }
  }
#undef PREAMBLE_FORMAT
#undef PREAMBLE_ARGS
#undef ASSERT_HERE
#undef ASSERT2_HERE
} // KlassLUTEntry::verify_against

#endif // ASSERT

#if INCLUDE_CDS
klute_raw_t KlassLUTEntry::calculate_klute_with_new_cld_index(unsigned cld_index) const {
  assert(cld_index < 3, "Sanity");
  U v(_v.raw);
  v.common.cld_index = cld_index;
  return v.raw;
}
#endif


void KlassLUTEntry::print(outputStream* st) const {
  st->print("%X (Kind: %d Loader: %d)", value(), kind(), loader_index());
}

// Helper function, prints current limits
void KlassLUTEntry::print_limits(outputStream* st) {
  st->print_cr("IKE Limits: instance byte size %zu, omb1 count: %zu, omb1 byte offset: %zu, omb2 oop count: %zu, omb2 byte offset: %zu",
               ik_wordsize_limit * BytesPerWord,
               ik_omb_count_1_limit, ik_omb_offset_1_limit * BytesPerHeapOop,
               ik_omb_count_2_limit, ik_omb_offset_2_limit * BytesPerHeapOop);
}
