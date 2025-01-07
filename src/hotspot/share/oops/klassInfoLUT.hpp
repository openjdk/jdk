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

class KlassInfoLUT : public AllStatic {

  static ClassLoaderData* _common_loaders[4]; // See "loader" bits in Klute
  static uint32_t* _entries;

  static inline unsigned num_entries();

  static inline uint32_t at(unsigned index);

  // register stats are not expensive
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

  // hit stats are expensive
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
  static void update_hit_stats(KlassLUTEntry klute);
#endif // KLUT_ENABLE_EXPENSIVE_STATS

#ifdef KLUT_ENABLE_EXPENSIVE_LOG
  static void log_hit(KlassLUTEntry klute);
#endif

public:

  static void initialize();

  static void register_klass(const Klass* k);

  static inline KlassLUTEntry get_entry(narrowKlass k);

  static int try_register_perma_cld(ClassLoaderData* cld);
  static inline ClassLoaderData* get_perma_cld(int index);

  static void print_statistics(outputStream* out);

};

#endif // SHARE_OOPS_KLASSINFOLUT_HPP
