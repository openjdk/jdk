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

#ifndef SHARE_OOPS_KLASSINFOLUT_HPP
#define SHARE_OOPS_KLASSINFOLUT_HPP

#include "memory/allStatic.hpp"
#include "oops/compressedKlass.hpp"
#include "oops/klassInfoLUTEntry.hpp"
#include "utilities/globalDefinitions.hpp"

class Klass;
class ClassLoaderData;

// Unexpensive stats
#define KLUT_ENABLE_REGISTRATION_STATS

#ifdef ASSERT
// expensive stats
#define KLUT_ENABLE_HIT_STATS
// very expensive tests
#define KLUT_SUPER_PARANOID
#endif

// The Klass Info Lookup Table (KLUT) is a table of 32-bit values. Each value represents
// a Klass. It contains some important information about its class in a very condensed
// form. For details about the KLUT entry encoding, please see comments in
// klassInfoLUTEntry.hpp.
//
// The purpose of this table is to make it (mostly) unnecessary to dereference Klass to
// get Klass meta information; instead, the KLUT entry is read, which means instead of
// reading from several memory locations spread out over different cache lines, we read
// just one datum from a very condensed data store. The result is fewer memory traffic
// and better spatial locality.
//
// The KLUT is only allocated when compact object headers are used. With Compact Object Headers,
// we do have very "tight" (condensed) narrowKlass value space that is perfect for use as
// indexes into the KLUT lookup table.
// Without Compact Headers, we still calculate KLUT entries, but store then in and retrieve
// them from the Klass directly. This gives a (more modest) performance benefit for non-COH
// scenarios as well as serves to unify oop iteration code.
//
// KLUT entry life cycle:
//
// When a Klass is dynamically loaded, the KLUT entry is calculated, entered into the table
// (table[narrowKlass] = klute) and also stored in the Klass itself. See KlassInfoLUT::register_klass().
//
// The KLUT entry is never removed from the table. When a class is unloaded, the entry gets
// stale; that is fine, since the narrowKlass value that could be used to look up the KLUT
// entry is also stale - referring to an unloaded Klass via its narrowKlass value is an error.
// A new future Klass that would be created at the same position in the Class Space will
// get the same narrowKlass and overwrite, as part of its creation, the old stale table slot
// with a newly generated KLUT entry.
//
// It gets a bit more complicated with CDS. CDS maps Klass instances into the process memory
// without going through the process of initialization. It also refers to that Klass via
// a narrowKlass value that is the result of a precalculation during dump time (e.g. by
// accessing obj->klass()->kind() on object that are re-animated from the CDS archive).
// These Klasses we register by scanning the CDS archive after it has been mapped into
// the Klass encoding range.

class KlassInfoLUT : public AllStatic {

  static ClassLoaderData* _common_loaders[4]; // See "loader" bits in Klute
  static unsigned _max_entries;
  static klute_raw_t* _table;
  static bool _initialized;
  static inline unsigned max_entries()  { return _max_entries; }
  static inline klute_raw_t at(unsigned index);
  static inline void put(unsigned index, klute_raw_t klute);
  static void allocate_lookup_table();
  static bool uses_lookup_table()       { return _table != nullptr; }

#if defined(KLUT_ENABLE_REGISTRATION_STATS) || defined(KLUT_ENABLE_HIT_STATS)
  class Counter {
    volatile uint64_t _v;
  public:
    Counter() : _v(0) {}
    void inc();
    uint64_t get() const { return _v; }
  };
  struct Counters {
    // How many InstanceKlass (excluding sub types)
    Counter counter_IK;
    // How many InstanceRefKlass
    Counter counter_IRK;
    // How many InstanceMirrorKlas
    Counter counter_IMK;
    // How many InstanceClassLoaderKlass
    Counter counter_ICLK;
    // How many InstanceStackChunkKlass
    Counter counter_ISCK;
    // How many TypeArrayKlass
    Counter counter_TAK;
    // How many ObjectArrayKlass
    Counter counter_OAK;

    // Of InstanceKlass registrations:
    // How many were not fully representable
    Counter counter_IK_no_info;
    // Of InstanceKlass registrations:
    // How many were from abstract/interface klasses (hence not fully representable)
    Counter counter_IK_no_info_abstract_or_interface;
    // Of InstanceKlass registrations:
    // How many had more than two oopmap entries (hence not fully representable)
    Counter counter_IK_no_info_too_many_oopmapentries;
    // Of InstanceKlass registrations:
    // How many were larger than 64 heap words  (hence not fully representable)
    Counter counter_IK_no_info_too_large;

    // Of InstanceKlass: How many had zero oopmap entries
    Counter counter_IK_zero_oopmapentries;
    // Of InstanceKlass: How many had one oopmap entries
    Counter counter_IK_one_oopmapentries;
    // Of InstanceKlass: How many had two oopmap entries
    Counter counter_IK_two_oopmapentries;

    // Of Klass: How many were tied to the permanent boot class loader CLD
    Counter counter_from_boot_cld;
    // Of Klass: How many were tied to the permanent sys class loader CLD
    Counter counter_from_system_cld;
    // Of Klass: How many were tied to the permanent platform class loader CLD
    Counter counter_from_platform_cld;
    // Of all Klass registrations:
    // How many were tied to an unknown CLD
    Counter counter_from_unknown_cld;
    // Of all Klass registrations:
    // How many were tied to a CLD that was nullptr at
    // registration time (AOT unlinked class)
    Counter counter_from_null_cld;
  };
  static void update_counters(Counters& counters, const Klass* k, klute_raw_t klute);
  static void print_counters(outputStream* st, const Counters& counters, const char* prefix);
#ifdef KLUT_ENABLE_REGISTRATION_STATS
  static Counters _registration_counters;
  static void update_registration_counters(const Klass* k, klute_raw_t klute);
#endif

#ifdef KLUT_ENABLE_HIT_STATS
  static Counters _hit_counters;
  static void update_hit_counters(const Klass* k, klute_raw_t klute);
#endif
#endif // (KLUT_ENABLE_REGISTRATION_STATS) || defined(KLUT_ENABLE_HIT_STATS)

public:

  static void initialize();

  static klute_raw_t register_klass(const Klass* k);
  static inline klute_raw_t lookup(narrowKlass k);

  // ClassLoaderData handling
  static constexpr unsigned cld_index_unknown = 0;
  static unsigned index_for_cld(const ClassLoaderData* cld);
  static inline ClassLoaderData* lookup_cld(unsigned index);
  static void register_cld_if_needed(ClassLoaderData* cld);

#if INCLUDE_CDS
  static void scan_klass_range_update_lut(address from, address to);
  static void shared_klass_cld_changed(Klass* k);
#endif

  static void print_statistics(outputStream* out);
};

#endif // SHARE_OOPS_KLASSINFOLUT_HPP
