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

#ifdef ASSERT
#define KLUT_ENABLE_EXPENSIVE_STATS
//#define KLUT_ENABLE_EXPENSIVE_LOG
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
// Since there is no secure way to initialize the KLUT entry for these classes, we (for the
// moment) allow a form of "self-healing": if the KLUT entry for a class is requested but
// not yet added, we add it to the KLUT table on the fly. See KlassInfoLUT::late_register_klass().

class KlassInfoLUT : public AllStatic {

  static ClassLoaderData* _common_loaders[4]; // See "loader" bits in Klute
  static unsigned _max_entries;
  static klute_raw_t* _table;
  static bool _initialized;
  static inline unsigned max_entries() { return _max_entries; }
  static inline klute_raw_t at(unsigned index);
  static inline void put(unsigned index, klute_raw_t klute);
  static void allocate_lookup_table();

  // Klass registration statistics. These are not expensive and therefore
  // we carry them always.
#define REGISTER_STATS_DO(f)    \
  f(registered_IK)              \
  f(registered_IRK)             \
  f(registered_IMK)             \
  f(registered_ICLK)            \
  f(registered_ISCK)            \
  f(registered_TAK)             \
  f(registered_OAK)             \
  f(registered_IK_for_abstract_or_interface)
#define XX(xx) static void inc_##xx();
  REGISTER_STATS_DO(XX)
#undef XX

  // Statistics about the hit rate of the lookup table. These are
  // expensive and only enabled when needed.
#ifdef KLUT_ENABLE_EXPENSIVE_STATS
#define HIT_STATS_DO(f)    \
  f(hits_IK)           \
  f(hits_IRK)          \
  f(hits_IMK)          \
  f(hits_ICLK)         \
  f(hits_ISCK)         \
  f(hits_TAK)          \
  f(hits_OAK)          \
  f(hits_bootloader)   \
  f(hits_sysloader)    \
  f(hits_platformloader)   \
  f(noinfo_IMK)        \
  f(noinfo_ICLK)       \
  f(noinfo_IK_other)
#define XX(xx) static void inc_##xx();
  HIT_STATS_DO(XX)
#undef XX
  static void update_hit_stats(klute_raw_t klute);
#endif // KLUT_ENABLE_EXPENSIVE_STATS

#ifdef KLUT_ENABLE_EXPENSIVE_LOG
  static void log_hit(klute_raw_t klute);
#endif

#if INCLUDE_CDS
  static klute_raw_t late_register_klass(narrowKlass nk);
#endif

  static bool use_lookup_table() { return _table != nullptr; }

public:

  static void initialize();

  static klute_raw_t register_klass(const Klass* k);
  static inline klute_raw_t lookup(narrowKlass k);

  static void scan_klass_range_update_lut(address from, address to);

  // ClassLoaderData handling
  static void register_cld_if_needed(ClassLoaderData* cld);
  static int index_for_cld(const ClassLoaderData* cld);
  static inline ClassLoaderData* lookup_cld(int index);
#if INCLUDE_CDS
  static void shared_klass_cld_changed(Klass* k);
#endif

  static void print_statistics(outputStream* out);
};

#endif // SHARE_OOPS_KLASSINFOLUT_HPP
