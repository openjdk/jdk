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

#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "cds/cdsConfig.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/klass.hpp"
#include "oops/klassInfoLUT.inline.hpp"
#include "oops/klassInfoLUTEntry.inline.hpp"
#include "oops/klassKind.hpp"
#include "runtime/atomic.hpp"

#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

ClassLoaderData* KlassInfoLUT::_common_loaders[4] = { nullptr };
bool KlassInfoLUT::_initialized = false;
klute_raw_t* KlassInfoLUT::_table = nullptr;
unsigned KlassInfoLUT::_max_entries = -1;

#ifdef KLUT_ENABLE_REGISTRATION_STATS
KlassInfoLUT::Counters KlassInfoLUT::_registration_counters;
#endif

#ifdef KLUT_ENABLE_HIT_STATS
KlassInfoLUT::Counters KlassInfoLUT::_hit_counters;
#endif

void KlassInfoLUT::initialize() {
  assert(!_initialized, "Only once");
  if (UseCompactObjectHeaders) {

    // Init Lookup Table
    // We allocate a lookup table only if we can use the narrowKlass for a lookup reasonably well.
    // We can do this only if the nKlass is small enough - we allow it for COH (22 bit nKlass with
    // 10 bit shift means we have a small and condensed table). We don't bother for -COH,
    assert(CompressedKlassPointers::fully_initialized(), "Too early");
    assert(CompressedKlassPointers::narrow_klass_pointer_bits() <= 22, "Use only for COH");
    assert(CompressedKlassPointers::shift() == 10, "must be (for density)");

    const narrowKlass highest_nk = CompressedKlassPointers::highest_valid_narrow_klass_id();
    size_t table_size_in_bytes = sizeof(klute_raw_t) * highest_nk;
    bool uses_large_pages = false;
    if (UseLargePages) {
      const size_t large_page_size = os::large_page_size();
      if (large_page_size < 16 * M) { // not for freakishly large pages
        table_size_in_bytes = align_up(table_size_in_bytes, large_page_size);
        _table = (klute_raw_t*) os::reserve_memory_special(table_size_in_bytes, large_page_size, large_page_size, nullptr, false);
        if (_table != nullptr) {
          uses_large_pages = true;
          _max_entries = (unsigned)(table_size_in_bytes / sizeof(klute_raw_t));
        }
      }
    }
    if (_table == nullptr) {
      table_size_in_bytes = align_up(table_size_in_bytes, os::vm_page_size());
      _table = (klute_raw_t*)os::reserve_memory(table_size_in_bytes, false, mtKLUT);
      os::commit_memory_or_exit((char*)_table, table_size_in_bytes, false, "KLUT");
      _max_entries = (unsigned)(table_size_in_bytes / sizeof(klute_raw_t));
    }

    log_info(klut)("Lookup table initialized (%u entries, using %s pages): " RANGEFMT,
                    _max_entries, (uses_large_pages ? "large" : "normal"), RANGEFMTARGS(_table, table_size_in_bytes));

    // We need to zap the whole LUT if CDS is enabled or dumping, since we may need to late-register classes.
    memset(_table, 0xff, _max_entries * sizeof(klute_raw_t));
    assert(_table[0] == KlassLUTEntry::invalid_entry, "Sanity"); // must be 0xffffffff

  }
  _initialized = true;
}

static const char* common_loader_names[4] = { "other", "boot", "app", "platform" };

void KlassInfoLUT::register_cld_if_needed(ClassLoaderData* cld) {

#ifdef INCLUDE_CDS
  // Unfortunately, due to AOT delayed class linking (see JDK-8342429), we can
  // encounter Klass that are unlinked and their CLD field is still nullptr.
  // Until JDK-8342429 we must accept that.
  if (cld == nullptr) {
    return;
  }
#else
  assert(cld != nullptr, "CLD null");
#endif

  // We remember CLDs for the three permanent class loaders in a lookup array.
  unsigned index = 0;
  if (cld->is_permanent_class_loader_data()) {
    if (cld->is_the_null_class_loader_data()) {
      index = 1;
    } else if (cld->is_system_class_loader_data()) {
      index = 2;
    } else if (cld->is_platform_class_loader_data()) {
      index = 3;
    }
  }

  if (index == 0) {
    return;
  }

  ClassLoaderData* old_cld = Atomic::load(_common_loaders + index);
  if (old_cld == nullptr) {
    old_cld = Atomic::cmpxchg(&_common_loaders[index], (ClassLoaderData*)nullptr, cld);
    if (old_cld == nullptr) {
      log_debug(klut)("Registered CLD " PTR_FORMAT " (%s loader) CLD at index %d",
                       p2i(cld), common_loader_names[index], index);
    }
  }

  // There should only be 3 permanent CLDs
  assert(old_cld == cld || old_cld == nullptr, "Different CLD??");
}

unsigned KlassInfoLUT::index_for_cld(const ClassLoaderData* cld) {
  assert(cld != nullptr, "CLD null?");
  for (int i = 1; i <= 3; i++) {
    if (cld == _common_loaders[i]) {
      return i;
    }
  }
  return cld_index_unknown;
}

static void log_klass_registration(const Klass* k, narrowKlass nk, bool added_to_table,
                                   klute_raw_t klute, const char* message) {
  char tmp[1024];
  const KlassLUTEntry klutehelper(klute);
  log_debug(klut)("Klass " PTR_FORMAT ", cld: %s, nk %u(%c), klute: " KLUTE_FORMAT ": %s %s%s",
                  p2i(k), common_loader_names[klutehelper.cld_index()], nk,
                  (added_to_table ? '+' : '-'),
                  klute,
                  message,
                  (k->is_shared() ? "(shared) " : ""),
                  k->name()->as_C_string(tmp, sizeof(tmp)));
}

klute_raw_t KlassInfoLUT::register_klass(const Klass* k) {

  // First register the CLD in case we did not already do that
  ClassLoaderData* const cld = k->class_loader_data();
  register_cld_if_needed(cld);

  // We calculate the klute that will be stored into the Klass.
  //
  // We also add the klute to the lookup table iff we use a lookup table (we do if COH is enabled)
  // and if the Klass is in the narrowKlass encoding range. Interfaces and abstract classes are
  // not put there anymore since we don't need narrowKlass lookup for them.
  const bool add_to_table =  uses_lookup_table() ? CompressedKlassPointers::is_encodable(k) : false;
  const narrowKlass nk = add_to_table ? CompressedKlassPointers::encode(const_cast<Klass*>(k)) : 0;

  // Calculate klute from Klass properties and update the table value.
  const klute_raw_t klute = KlassLUTEntry::build_from_klass(k);
  const klute_raw_t oldklute = k->klute();
  if (add_to_table) {
    put(nk, klute);
  }
  log_klass_registration(k, nk, add_to_table, klute, "registered");

#ifdef KLUT_ENABLE_REGISTRATION_STATS
  if (oldklute != klute) {
    update_registration_counters(k, klute);
  }
#endif // KLUT_ENABLE_REGISTRATION_STATS

#ifdef ASSERT
  // Until See JDK-8342429 is solved
  KlassLUTEntry(klute).verify_against_klass(k);
  if (add_to_table) {
    KlassLUTEntry e2(at(nk));
    assert(e2.value() == klute, "sanity");
  }
#endif // ASSERT

  return klute;
}

#if INCLUDE_CDS

void KlassInfoLUT::scan_klass_range_update_lut(address from, address to) {
  assert(_initialized, "not initialized");
  if (uses_lookup_table()) {
    log_info(klut)("Scanning CDS klass range: " RANGE2FMT, RANGE2FMTARGS(from, to));
    const size_t stepsize = CompressedKlassPointers::klass_alignment_in_bytes();
    assert(stepsize >= K, "only for COH and large alignments");
    assert(is_aligned(from, stepsize), "from address unaligned");
    assert(is_aligned(to, stepsize), "to address unaligned");
    assert(from < to, "invalid range");
    unsigned found = 0;
    for (address here = from; here < to; here += stepsize) {
      if (!os::is_readable_range(here, here + sizeof(Klass))) {
        continue;
      }
      const Klass* const candidate = (Klass*)here;
      if (!candidate->check_stamp()) {
        continue;
      }
      const klute_raw_t klute = candidate->klute();
      if (klute == KlassLUTEntry::invalid_entry) {
        continue;
      }
      // Above checks may of course, very rarely, result in false positives (locations wrongly
      // identified as Klass locations). That is absolutely fine: we then copy a "klute" from
      // that "Klass" to the table that really isn't either klute nor Klass.
      // Since that slot is not used anyway by a real Klass, nothing bad will happen.
      // OTOH, *missing* to add a klute for a Klass that exists would be really bad.
      const narrowKlass nk = CompressedKlassPointers::encode(const_cast<Klass*>(candidate));
      put(nk, klute);
      log_info(klut)("Suspected Klass found at " PTR_FORMAT "; adding nk %u, klute: " KLUTE_FORMAT,
                     p2i(candidate), nk, klute);
      found ++;
    }
    log_info(klut)("Found and registered %u possible Klass locations in CDS klass range " RANGE2FMT,
                   found, RANGE2FMTARGS(from, to));
  }
}

void KlassInfoLUT::shared_klass_cld_changed(Klass* k) {
  // Called when the CLD field inside a Klass is changed by CDS.
  // Recalculates the klute for this Klass (even though strictly speaking we
  // only need to update the CLD index in the klute).
  //
  // This is necessary to prevent the klute and the Klass being out of sync.
  //
  // Two cases:
  // - when the CLD is set to nullptr in the process of archive dumping (remove_unshareable_info),
  //   we set the klute.cld_index to 0 aka "unknown CLD". Any oop iteration over an object with a
  //   klute.cld_index of 0 will then retrieve the CLD from the Klass directly.
  // - when the CLD is restored after the archive has been loaded, klute.cld_index is set to
  //   the value corresponding to that CLD.
  assert(k->is_shared(), "Only for CDS classes");
  const klute_raw_t oldklute = k->klute();
  k->register_with_klut(); // re-register
  const klute_raw_t newklute = k->klute();
  if (uses_lookup_table() && CompressedKlassPointers::is_encodable(k)) {
    const narrowKlass nk = CompressedKlassPointers::encode(k);
    put(nk, newklute);
  }

  char tmp[1024];
  log_debug(klut)("Updated klute for Klass " PTR_FORMAT " (%s) after CLD change:"
                  "old: " KLUTE_FORMAT ", new: " KLUTE_FORMAT,
                  p2i(k), k->name()->as_C_string(tmp, sizeof(tmp)), oldklute, newklute);
}
#endif // INCLUDE_CDS


// Statistics

#if defined(KLUT_ENABLE_REGISTRATION_STATS) || defined(KLUT_ENABLE_HIT_STATS)

 void KlassInfoLUT::Counter::inc() {
  Atomic::inc(&_v);
}

void KlassInfoLUT::update_counters(Counters& counters, const Klass* k, klute_raw_t klute) {
  const KlassLUTEntry kle(klute);

  switch (k->kind()) {
#define WHAT(name, shorthand) \
  case name ## Kind : counters.counter_ ## shorthand.inc(); break;
  KLASSKIND_ALL_KINDS_DO(WHAT)
#undef WHAT
  default: ShouldNotReachHere();
  };

  if (kle.is_instance()) {
    const InstanceKlass* const ik = InstanceKlass::cast(k);

    if (!kle.ik_carries_infos()) {
      counters.counter_IK_no_info.inc();
      const InstanceKlass* const ik = InstanceKlass::cast(k);
    }

    if (ik->is_abstract() || ik->is_interface()) {
      counters.counter_IK_no_info_abstract_or_interface.inc();
    }

    const int lh = ik->layout_helper();
    if (!Klass::layout_helper_needs_slow_path(lh)) {
      const size_t wordsize = Klass::layout_helper_to_size_helper(ik->layout_helper());
      if (wordsize >= KlassLUTEntry::ik_wordsize_limit) {
        counters.counter_IK_no_info_too_large.inc();
      }
    }

    switch (ik->nonstatic_oop_map_count()) {
    case 0: counters.counter_IK_zero_oopmapentries.inc(); break;
    case 1: counters.counter_IK_one_oopmapentries.inc(); break;
    case 2: counters.counter_IK_two_oopmapentries.inc(); break;
    default: counters.counter_IK_no_info_too_many_oopmapentries.inc(); break;
    }
  }

  switch (kle.cld_index()) {
  case cld_index_unknown: {
    if (k->class_loader_data() != nullptr) {
      counters.counter_from_unknown_cld.inc();
    } else {
      counters.counter_from_null_cld.inc();
    }
    break;
  }
  case 1: counters.counter_from_boot_cld.inc(); break;
  case 2: counters.counter_from_system_cld.inc(); break;
  case 3: counters.counter_from_platform_cld.inc(); break;
  default: ShouldNotReachHere();
  }
}

static void print_part_counter(outputStream* st, const char* prefix1, const char* prefix2, uint64_t v, uint64_t total) {
  st->print("%s %s: ", prefix1, prefix2);
  st->fill_to(32);
  st->print_cr(UINT64_FORMAT " (%.2f%%)", v, ((double)v * 100.0f) / total);
}

void KlassInfoLUT::print_counters(outputStream* st, const Counters& counters, const char* prefix) {

  // All Klasses
  const uint64_t all =
#define XX(name, shortname) counters.counter_ ## shortname.get() +
  KLASSKIND_ALL_KINDS_DO(XX)
#undef XX
  0;

  print_part_counter(st, prefix, "(all)", all, all);

  const uint64_t registered_AK = counters.counter_TAK.get() + counters.counter_OAK.get();
  const uint64_t registered_IK = all - registered_AK;
  print_part_counter(st, prefix, "IK (all)", registered_IK, all);
  print_part_counter(st, prefix, "AK (all)", registered_AK, all);

#define XX(name, shortname) \
    print_part_counter(st, prefix, ""# shortname, counters.counter_ ## shortname.get(), all);
  KLASSKIND_ALL_KINDS_DO(XX)
#undef XX

  print_part_counter(st, prefix, "IK (no info)", counters.counter_IK_no_info.get(), all);
  print_part_counter(st, prefix, "IK (no info, abstract or interface)", counters.counter_IK_no_info_abstract_or_interface.get(), all);
  print_part_counter(st, prefix, "IK (no info, too many oopmap entries)", counters.counter_IK_no_info_too_many_oopmapentries.get(), all);
  print_part_counter(st, prefix, "IK (no info, obj size too large)", counters.counter_IK_no_info_too_large.get(), all);

  print_part_counter(st, prefix, "IK (0 oopmap entries)", counters.counter_IK_zero_oopmapentries.get(), all);
  print_part_counter(st, prefix, "IK (1 oopmap entry)", counters.counter_IK_one_oopmapentries.get(), all);
  print_part_counter(st, prefix, "IK (2 oopmap entries)", counters.counter_IK_two_oopmapentries.get(), all);

  print_part_counter(st, prefix, "boot cld", counters.counter_from_boot_cld.get(), all);
  print_part_counter(st, prefix, "system cld", counters.counter_from_system_cld.get(), all);
  print_part_counter(st, prefix, "platform cld", counters.counter_from_platform_cld.get(), all);
  print_part_counter(st, prefix, "unknown cld", counters.counter_from_unknown_cld.get(), all);
  print_part_counter(st, prefix, "null cld", counters.counter_from_null_cld.get(), all);

}

void KlassInfoLUT::print_statistics(outputStream* st) {
  StreamAutoIndentor indent(st);

  st->print_cr("KLUT");

  st->print_cr("Klass registrations:");
  {
    streamIndentor si(st, 4);
#ifdef KLUT_ENABLE_REGISTRATION_STATS
    print_counters(st, _registration_counters, "registrations");
#else
    st->print_cr("Not available");
#endif
  }

  st->print_cr("Hits:");
  {
    streamIndentor si(st, 4);
#ifdef KLUT_ENABLE_HIT_STATS
    if (uses_lookup_table()) {
      print_counters(st, _hit_counters, "hits");
    } else {
      st->print_cr("Not available (COH disabled)");
    }
#else
    st->print_cr("Not available");
#endif
  }

  st->print_cr("Lookup Table:");
  {
    streamIndentor si(st, 4);
    if (uses_lookup_table()) {

      st->print_cr("Size: %u slots (%zu bytes)", _max_entries, _max_entries * sizeof(klute_raw_t));

      // Hit density per cacheline distribution (How well are narrow Klass IDs clustered to give us good local density)
      constexpr int chacheline_size = 64;
      constexpr int slots_per_cacheline = chacheline_size / sizeof(KlassLUTEntry);
      const int num_cachelines = max_entries() / slots_per_cacheline;
      int valid_hits_per_cacheline_distribution[slots_per_cacheline + 1] = { 0 };
      for (int i = 0; i < num_cachelines; i++) {
        int n = 0;
        for (int j = 0; j < slots_per_cacheline; j++) {
          KlassLUTEntry e(at((i * slots_per_cacheline) + j));
          const bool fully_valid = e.is_valid() && (e.is_array() || e.ik_carries_infos());
          if (fully_valid) {
            n++;
          }
        }
        assert(n <= slots_per_cacheline, "Sanity");
        valid_hits_per_cacheline_distribution[n]++;
      }
      st->print_cr("LUT valid hit density over cacheline size:");
      {
        streamIndentor si(st, 4);
        for (int i = 0; i <= slots_per_cacheline; i++) {
          st->print_cr("%d valid entries per cacheline: %d", i, valid_hits_per_cacheline_distribution[i]);
        }
      }
    } else {
      st->print_cr("Not available (COH disabled)");
    }
  }

  st->print_cr("Limits:");
  {
    streamIndentor si(st, 4);
    st->print_cr("max instance size: %zu words", KlassLUTEntry::ik_wordsize_limit);
    st->print_cr("max oopmap block 1 count: %zu", KlassLUTEntry::ik_omb_count_1_limit);
    st->print_cr("max oopmap block 1 offset: %zu oops", KlassLUTEntry::ik_omb_offset_1_limit);
    st->print_cr("max oopmap block 2 count: %zu", KlassLUTEntry::ik_omb_count_2_limit);
    st->print_cr("max oopmap block 2 offset: %zu oops", KlassLUTEntry::ik_omb_offset_2_limit);
  }
  st->cr();
}
#endif // (KLUT_ENABLE_REGISTRATION_STATS) || defined(KLUT_ENABLE_HIT_STATS)


#ifdef KLUT_ENABLE_REGISTRATION_STATS
void KlassInfoLUT::update_registration_counters(const Klass* k, klute_raw_t klute) {
  update_counters(_registration_counters, k, klute);
}
#endif // KLUT_ENABLE_REGISTRATION_STATS

#ifdef KLUT_ENABLE_HIT_STATS
void KlassInfoLUT::update_hit_counters(const Klass* k, klute_raw_t klute) {
  update_counters(_hit_counters, k, klute);
}



#endif // KLUT_ENABLE_HIT_STATS
