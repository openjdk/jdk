/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1COLLECTORSTATE_HPP
#define SHARE_GC_G1_G1COLLECTORSTATE_HPP

#include "gc/g1/g1GCPauseType.hpp"
#include "utilities/globalDefinitions.hpp"

// State of the G1 collection.
//
// The rough phasing is Young-Only, Mixed / Space Reclamation and
// Full GC "phase".
//
// We split the Young-only phase into three parts to cover interesting
// sub-phases and avoid separate tracking.
class G1CollectorState {
  enum Phase {
    // Indicates that the next GC in the Young-Only phase will (likely) be a "Normal"
    // young GC.
    YoungNormal,
    // We are in a concurrent start GC during the Young-Only phase. This is only set
    // during that GC because we only decide whether we do this type of GC at the start
    // of the pause.
    YoungConcurrentStart,
    // Indicates that we are about to start or in the last young gc in the Young-Only
    // phase before the Mixed phase. This GC is required to keep pause time requirements.
    YoungLastYoung,
    // Doing extra old generation cleanups.
    Mixed,
    // The Full GC phase (that coincides with the Full GC pause).
    FullGC
  } _phase;

  // If _initiate_conc_mark_if_possible is set at the beginning of a
  // pause, it is a suggestion that the pause should start a marking
  // cycle by doing the concurrent start work. However, it is possible
  // This decision is mostly based on heap occupancy.
  //
  // If the concurrent marking thread is still finishing up the
  // previous marking cycle (e.g., clearing the marking bitmap).
  // If that is the case we cannot start a new cycle and
  // we'll have to wait for the concurrent marking thread to finish
  // what it is doing. In this case we will postpone the marking cycle
  // initiation decision for the next pause. When we eventually decide
  // to start a cycle, we will set _in_concurrent_start_gc which
  // will stay true until the end of the concurrent start pause doing the
  // concurrent start work.
  volatile bool _initiate_conc_mark_if_possible;

public:
  G1CollectorState() :
    _phase(YoungNormal),
    _initiate_conc_mark_if_possible(false) { }

  // Phase setters
  void set_in_normal_young_gc() { _phase = YoungNormal; }
  void set_in_space_reclamation_phase() { _phase = Mixed; }
  void set_in_full_gc() { _phase = FullGC; }

  // Pause setters
  void set_in_young_gc_before_mixed() { _phase = YoungLastYoung; }
  void set_in_concurrent_start_gc() { _phase = YoungConcurrentStart; _initiate_conc_mark_if_possible = false; }

  void set_initiate_conc_mark_if_possible(bool v) { _initiate_conc_mark_if_possible = v; }

  // Phase getters
  bool is_in_young_only_phase() const { return _phase == YoungNormal || _phase == YoungConcurrentStart || _phase == YoungLastYoung; }
  bool is_in_mixed_phase() const { return _phase == Mixed; }

  // Specific pauses
  bool is_in_young_gc_before_mixed() const { return _phase == YoungLastYoung; }
  bool is_in_full_gc() const { return _phase == FullGC; }
  bool is_in_concurrent_start_gc() const { return _phase == YoungConcurrentStart; }

  bool initiate_conc_mark_if_possible() const { return _initiate_conc_mark_if_possible; }

  bool is_in_concurrent_cycle() const;
  bool is_in_marking() const;
  bool is_in_mark_or_rebuild() const;
  bool is_in_reset_for_next_cycle() const;

  // Calculate GC Pause Type from internal state.
  G1GCPauseType young_gc_pause_type(bool concurrent_operation_is_full_mark) const;
};

#endif // SHARE_GC_G1_G1COLLECTORSTATE_HPP
