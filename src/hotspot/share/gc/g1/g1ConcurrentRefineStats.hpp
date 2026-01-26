/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTREFINESTATS_HPP
#define SHARE_GC_G1_G1CONCURRENTREFINESTATS_HPP

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

// Collection of statistics for concurrent refinement processing.
// Used for collecting per-thread statistics and for summaries over a
// collection of threads.
class G1ConcurrentRefineStats : public CHeapObj<mtGC> {
  Atomic<jlong> _sweep_duration;              // Time spent sweeping the table finding non-clean cards
                                              // and refining them.
  Atomic<jlong> _yield_during_sweep_duration; // Time spent yielding during the sweep (not doing the sweep).

  Atomic<size_t> _cards_scanned;              // Total number of cards scanned.
  Atomic<size_t> _cards_clean;                // Number of cards found clean.
  Atomic<size_t> _cards_not_parsable;         // Number of cards we could not parse and left unrefined.
  Atomic<size_t> _cards_already_refer_to_cset;// Number of cards marked found to be already young.
  Atomic<size_t> _cards_refer_to_cset;        // Number of dirty cards that were recently found to contain a to-cset reference.
  Atomic<size_t> _cards_no_cross_region;      // Number of dirty cards that were dirtied, but then cleaned again by the mutator.

  Atomic<jlong> _refine_duration;             // Time spent during actual refinement.

public:
  G1ConcurrentRefineStats();

  // Time spent performing sweeping the refinement table (includes actual refinement,
  // but not yield time).
  inline jlong sweep_duration() const;
  inline jlong yield_during_sweep_duration() const;
  inline jlong refine_duration() const;

  // Number of refined cards.
  inline size_t refined_cards() const;

  inline size_t cards_scanned() const;
  inline size_t cards_clean() const;
  inline size_t cards_not_clean() const;
  inline size_t cards_not_parsable() const;
  inline size_t cards_already_refer_to_cset() const;
  inline size_t cards_refer_to_cset() const;
  inline size_t cards_no_cross_region() const;
  // Number of cards that were marked dirty and in need of refinement. This includes cards recently
  // found to refer to the collection set as they originally were dirty.
  inline size_t cards_pending() const;

  inline size_t cards_to_cset() const;

  inline void inc_sweep_time(jlong t);
  inline void inc_yield_during_sweep_duration(jlong t);
  inline void inc_refine_duration(jlong t);

  inline void inc_cards_scanned(size_t increment);
  inline void inc_cards_clean(size_t increment);
  inline void inc_cards_not_parsable();
  inline void inc_cards_already_refer_to_cset();
  inline void inc_cards_refer_to_cset();
  inline void inc_cards_no_cross_region();

  void add_atomic(G1ConcurrentRefineStats* other);

  void reset();
};

#endif // SHARE_GC_G1_G1CONCURRENTREFINESTATS_HPP
