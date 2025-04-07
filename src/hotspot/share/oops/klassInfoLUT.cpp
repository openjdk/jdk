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
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

ClassLoaderData* KlassInfoLUT::_common_loaders[4] = { nullptr };
uint32_t* KlassInfoLUT::_entries = nullptr;
unsigned KlassInfoLUT::_num_entries = -1;

void KlassInfoLUT::initialize() {
  assert(CompressedKlassPointers::fully_initialized(), "Too early");

  if (UseCompactObjectHeaders) {
    // Init Lookup Table
    // We allocate a lookup table only if we can use the narrowKlass for a lookup reasonably well.
    // We can do this only if the nKlass is small enough - we allow it for COH (22 bit nKlass with
    // 10 bit shift means we have a small and condensed table). We don't bother for -COH,
    assert(CompressedKlassPointers::narrow_klass_pointer_bits() <= 22, "Use only for COH");
    assert(CompressedKlassPointers::shift() == 10, "must be (for density)");

    const narrowKlass highest_nk = CompressedKlassPointers::highest_valid_narrow_klass_id();
    size_t table_size_in_bytes = sizeof(uint32_t) * highest_nk;
    bool uses_large_pages = false;
    if (UseLargePages) {
      const size_t large_page_size = os::large_page_size();
      if (large_page_size < 16 * M) { // not for freakishly large pages
        table_size_in_bytes = align_up(table_size_in_bytes, large_page_size);
        _entries = (uint32_t*) os::reserve_memory_special(table_size_in_bytes, large_page_size, large_page_size, nullptr, false);
        if (_entries != nullptr) {
          uses_large_pages = true;
          _num_entries = table_size_in_bytes / sizeof(uint32_t);
        }
      }
    }
    if (_entries == nullptr) {
      table_size_in_bytes = align_up(table_size_in_bytes, os::vm_page_size());
      _entries = (uint32_t*)os::reserve_memory(table_size_in_bytes, false, mtKLUT);
      os::commit_memory_or_exit((char*)_entries, table_size_in_bytes, false, "KLUT");
      _num_entries = table_size_in_bytes / sizeof(uint32_t);
    }

    log_info(klut)("Lookup table initialized (%u entries, using %s pages): " RANGEFMT,
                    _num_entries, (uses_large_pages ? "large" : "normal"), RANGEFMTARGS(_entries, table_size_in_bytes));

    // We need to zap the whole LUT if CDS is enabled or dumping, since we may need to late-register classes.
    memset(_entries, 0xff, _num_entries * sizeof(uint32_t));
    assert(_entries[0] == KlassLUTEntry::invalid_entry, "Sanity"); // must be 0xffffffff

  }
}

int KlassInfoLUT::try_register_perma_cld(ClassLoaderData* cld) {
  int index = 0;
  if (cld->is_permanent_class_loader_data()) {
    if (cld->is_the_null_class_loader_data()) {
      index = 1;
    } else if (cld->is_system_class_loader_data()) {
      index = 2;
    } else if (cld->is_platform_class_loader_data()) {
      index = 3;
    }
  }
  if (index > 0) {
    ClassLoaderData* old_cld = Atomic::load(_common_loaders + index);
    if (old_cld == nullptr) {
      old_cld = Atomic::cmpxchg(&_common_loaders[index], (ClassLoaderData*)nullptr, cld);
      if (old_cld == nullptr || old_cld == cld) {
        return index;
      }
    } else if (old_cld == cld) {
      return index;
    }
  }
  return 0;
}

static void log_klass_registration(const Klass* k, narrowKlass nk, KlassLUTEntry klute, const char* message) {
  char tmp[1024];
  log_debug(klut)("Klass " PTR_FORMAT ", nk %u, klute: " INT32_FORMAT_X_0 ": %s %s%s",
                  p2i(k), nk, klute.value(),
                  message,
                  (k->is_shared() ? "(shared) " : ""),
                  k->name()->as_C_string(tmp, sizeof(tmp)));
}

KlassLUTEntry KlassInfoLUT::register_klass(const Klass* k) {



  const narrowKlass nk = UseCompressedClassPointers ? CompressedKlassPointers::encode(const_cast<Klass*>(k)) : 0;

  KlassLUTEntry klute = k->klute();
  if (klute.is_valid()) {
    // The Klass already carries the pre-computed klute. That can happen if it was loaded from a shared
    // archive, in which case it contains the klute computed at (dynamic) load time when dumping.
    if (use_lookup_table()) {
      assert(nk < num_entries(), "narrowKlass %u is OOB for LUT", nk);
      if (klute.value() == _entries[nk]) {
        log_klass_registration(k, nk, klute, "already registered");
      } else {
        // Copy the klute value from the Klass to the table slot. This saves some cycles but, more importantly,
        // makes the coding more robust when using it on Klasses that are not fully initialized yet (during CDS
        // initialization we encounter Klasses with no associated CLD, for instance).
        _entries[nk] = klute.value();
        log_klass_registration(k, nk, klute, "updated table value for");
      }
    }
  } else {
    // Calculate klute from Klass properties and update the table value.
    klute = KlassLUTEntry::build_from_klass(k);
    if (use_lookup_table()) {
      _entries[nk] = klute.value();
    }
    log_klass_registration(k, nk, klute, "registered");
  }

#ifdef ASSERT
  klute.verify_against_klass(k);
  if (use_lookup_table()) {
    KlassLUTEntry e2(at(nk));
    assert(e2 == klute, "sanity");
  }
#endif // ASSERT

  // update register stats
  switch (k->kind()) {
  case Klass::InstanceKlassKind:             inc_registered_IK(); break;
  case Klass::InstanceRefKlassKind:          inc_registered_IRK(); break;
  case Klass::InstanceMirrorKlassKind:       inc_registered_IMK(); break;
  case Klass::InstanceClassLoaderKlassKind:  inc_registered_ICLK(); break;
  case Klass::InstanceStackChunkKlassKind:   inc_registered_ISCK(); break;
  case Klass::TypeArrayKlassKind:            inc_registered_TAK(); break;
  case Klass::ObjArrayKlassKind:             inc_registered_OAK(); break;
  default: ShouldNotReachHere();
  };
  if (k->is_abstract() || k->is_interface()) {
    inc_registered_IK_for_abstract_or_interface();
  }

  return klute;
}

#if INCLUDE_CDS
// We only tolerate this for CDS. I hope to find a better solution that allows me to
// safely register all Klass structures *before* anyone calls any methods on them. CDS
// makes that very difficult.
// The problem with late_register_class is that it imposes an overhead on every lookup, since
// a lookup table entry may potentially be still invalid.
KlassLUTEntry KlassInfoLUT::late_register_klass(narrowKlass nk) {
  const Klass* k = CompressedKlassPointers::decode(nk);
  assert(k->is_shared(), "Only for CDS classes");
  // In the CDS case, we expect the original class to have been registered during dumptime; so
  // it should carry a valid KLUTE entry. We just copy the klute into the lookup table. Note that
  // we cannot calculate the KLUTE from the Klass here even if we wanted, since the Klass may not
  // yet been fully initialized (CDS calls Klass functions on Klass structures that are not yet
  // fully initialized, e.g. have no associated CLD).
  const KlassLUTEntry klute = k->klute();
  assert(klute.is_valid(), "Must be a valid klute");
  _entries[nk] = klute.value();
  log_klass_registration(k, nk, klute.value(), "late-registered");
  return klute;
}
#endif // INCLUDE_CDS

// Counters and incrementors
#define XX(xx)                      \
volatile uint64_t counter_##xx = 0; \
void KlassInfoLUT::inc_##xx() {     \
  Atomic::inc(&counter_##xx);       \
}
REGISTER_STATS_DO(XX)
#ifdef KLUT_ENABLE_EXPENSIVE_STATS
HIT_STATS_DO(XX)
#endif // KLUT_ENABLE_EXPENSIVE_STATS
#undef XX

void KlassInfoLUT::print_statistics(outputStream* st) {

  st->print_cr("KLUT statistics:");

  if (use_lookup_table()) {
    st->print_cr("Lookup Table Size: %u slots (%zu bytes)", _num_entries, _num_entries * sizeof(uint32_t));
  }

  const uint64_t registered_all = counter_registered_IK + counter_registered_IRK + counter_registered_IMK +
      counter_registered_ICLK + counter_registered_ISCK + counter_registered_TAK + counter_registered_OAK;

#define PERCENTAGE_OF(x, x100) ( ((double)x * 100.0f) / x100 )
#define PRINT_WITH_PERCENTAGE(title, x, x100) \
  st->print("   " title ": "); \
  st->fill_to(24);             \
  st->print_cr(" " UINT64_FORMAT " (%.2f%%)", x, PERCENTAGE_OF(x, x100));

  st->print_cr("   Registered classes, total: " UINT64_FORMAT, registered_all);
#define XX(name, shortname) PRINT_WITH_PERCENTAGE("Registered, " #shortname, counter_registered_##shortname, registered_all);
  ALL_KLASS_KINDS_DO(XX)
#undef XX

  const uint64_t registered_AK = counter_registered_OAK - counter_registered_TAK;
  const uint64_t registered_IK = registered_all - registered_AK;
  PRINT_WITH_PERCENTAGE("Registered classes, IK (all)", registered_IK, registered_all);
  PRINT_WITH_PERCENTAGE("Registered classes, AK (all)", registered_AK, registered_all);

  PRINT_WITH_PERCENTAGE("Registered classes, IK, for abstract/interface", counter_registered_IK_for_abstract_or_interface, registered_all);

#ifdef KLUT_ENABLE_EXPENSIVE_STATS
  const uint64_t hits = counter_hits_IK + counter_hits_IRK + counter_hits_IMK +
                        counter_hits_ICLK + counter_hits_ISCK + counter_hits_TAK + counter_hits_OAK;

  st->print_cr("   Hits, total: " UINT64_FORMAT, hits);
#define XX(name, shortname) PRINT_WITH_PERCENTAGE("Hits, " #shortname, counter_hits_##shortname, hits);
  ALL_KLASS_KINDS_DO(XX)
#undef XX

  const uint64_t hits_ak = counter_hits_OAK + counter_hits_TAK;
  const uint64_t hits_ik = hits - hits_ak;
  const uint64_t no_info_hits = counter_noinfo_ICLK + counter_noinfo_IMK + counter_noinfo_IK_other;

  PRINT_WITH_PERCENTAGE("Hits, IK (all)", hits_ik, hits);
  PRINT_WITH_PERCENTAGE("Hits, AK (all)", hits_ak, hits);

  PRINT_WITH_PERCENTAGE("Hits, all for bootloader", counter_hits_bootloader, hits);
  PRINT_WITH_PERCENTAGE("Hits, all for systemloader", counter_hits_sysloader, hits);
  PRINT_WITH_PERCENTAGE("Hits, all for platformloader", counter_hits_platformloader, hits);

  st->print_cr("   IK details missing for " UINT64_FORMAT " hits (%.2f%%) due to: "
               "IMK " UINT64_FORMAT " (%.2f%%) "
               "ICLK " UINT64_FORMAT " (%.2f%%) "
               "other " UINT64_FORMAT " (%.2f%%)",
               no_info_hits, PERCENTAGE_OF(no_info_hits, hits),
               counter_noinfo_IMK, PERCENTAGE_OF(counter_noinfo_IMK, hits),
               counter_noinfo_ICLK, PERCENTAGE_OF(counter_noinfo_ICLK, hits),
               counter_noinfo_IK_other, PERCENTAGE_OF(counter_noinfo_IK_other, hits)
  );
#endif // KLUT_ENABLE_EXPENSIVE_STATS

  if (use_lookup_table()) {
    // Hit density per cacheline distribution (How well are narrow Klass IDs clustered to give us good local density)
    constexpr int chacheline_size = 64;
    constexpr int slots_per_cacheline = chacheline_size / sizeof(KlassLUTEntry);
    const int num_cachelines = num_entries() / slots_per_cacheline;
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
    for (int i = 0; i <= slots_per_cacheline; i++) {
      st->print_cr("%d valid entries per cacheline: %d", i, valid_hits_per_cacheline_distribution[i]);
    }
  }
  // Just for info, print limits
  KlassLUTEntry::print_limits(st);
}

#ifdef KLUT_ENABLE_EXPENSIVE_STATS
void KlassInfoLUT::update_hit_stats(KlassLUTEntry klute) {
  switch (klute.kind()) {
#define XX(name, shortname) case Klass::name ## Kind: inc_hits_ ## shortname(); break;
  ALL_KLASS_KINDS_DO(XX)
#undef XX
  default: ShouldNotReachHere();
  };
  if (klute.is_instance() && !klute.ik_carries_infos()) {
    switch (klute.kind()) {
      case Klass::InstanceClassLoaderKlassKind: inc_noinfo_ICLK(); break;
      case Klass::InstanceMirrorKlassKind: inc_noinfo_IMK(); break;
      default: inc_noinfo_IK_other(); break;
    }
  }
  switch (klute.loader_index()) {
  case 1: inc_hits_bootloader(); break;
  case 2: inc_hits_sysloader(); break;
  case 3: inc_hits_platformloader(); break;
  };
}
#endif // KLUT_ENABLE_EXPENSIVE_STATS

#ifdef KLUT_ENABLE_EXPENSIVE_LOG
void KlassInfoLUT::log_hit(KlassLUTEntry klute) {
  //log_debug(klut)("retrieval: klute: name: %s kind: %d", k->name()->as_C_string(), k->kind());
}
#endif

