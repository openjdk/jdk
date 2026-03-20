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

#include "utilities/debug.hpp"
#include "utilities/enumIterator.hpp"
#include "utilities/globalDefinitions.hpp"

// State of the G1 collection.
//
// The rough phasing is Young-Only, Mixed / Space Reclamation and
// Full GC "phase".
//
// We split the Young-only phase into three parts to cover interesting
// sub-phases and avoid separate tracking.
class G1CollectorState {
  enum class Phase {
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
    // Doing extra old generation evacuation.
    Mixed,
    // The Full GC phase (that coincides with the Full GC pause).
    FullGC
  } _phase;

  // _initiate_conc_mark_if_possible indicates that there has been a request to start
  // a concurrent cycle but we have not been able to fulfill it because another one
  // has been in progress when the request came in.
  //
  // This flag remembers that there is an unfullfilled request.
  volatile bool _initiate_conc_mark_if_possible;

public:
  G1CollectorState() :
    _phase(Phase::YoungNormal),
    _initiate_conc_mark_if_possible(false) { }

  // Phase setters
  void set_in_normal_young_gc() { _phase = Phase::YoungNormal; }
  void set_in_space_reclamation_phase() { _phase = Phase::Mixed; }
  void set_in_full_gc() { _phase = Phase::FullGC; }

  // Pause setters
  void set_in_young_gc_before_mixed() { _phase = Phase::YoungLastYoung; }
  void set_in_concurrent_start_gc() { _phase = Phase::YoungConcurrentStart; _initiate_conc_mark_if_possible = false; }

  void set_initiate_conc_mark_if_possible(bool v) { _initiate_conc_mark_if_possible = v; }

  // Phase getters
  bool is_in_young_only_phase() const { return _phase == Phase::YoungNormal || _phase == Phase::YoungConcurrentStart || _phase == Phase::YoungLastYoung; }
  bool is_in_mixed_phase() const { return _phase == Phase::Mixed; }

  // Specific pauses
  bool is_in_young_gc_before_mixed() const { return _phase == Phase::YoungLastYoung; }
  bool is_in_full_gc() const { return _phase == Phase::FullGC; }
  bool is_in_concurrent_start_gc() const { return _phase == Phase::YoungConcurrentStart; }

  bool initiate_conc_mark_if_possible() const { return _initiate_conc_mark_if_possible; }

  bool is_in_concurrent_cycle() const;
  bool is_in_marking() const;
  bool is_in_mark_or_rebuild() const;
  bool is_in_reset_for_next_cycle() const;

  enum class Pause : uint {
    Normal,
    LastYoung,
    ConcurrentStartFull,
    ConcurrentStartUndo,
    Cleanup,
    Remark,
    Mixed,
    Full
  };

  // Calculate GC Pause Type from internal state.
  Pause gc_pause_type(bool concurrent_operation_is_full_mark) const;

  static const char* to_string(Pause type) {
    static const char* pause_strings[] = { "Normal",
                                           "Prepare Mixed",
                                           "Concurrent Start", // Do not distinguish between the different
                                           "Concurrent Start", // Concurrent Start pauses.
                                           "Cleanup",
                                           "Remark",
                                           "Mixed",
                                           "Full" };
    return pause_strings[static_cast<uint>(type)];
  }

  static void assert_is_young_pause(Pause type) {
    assert(type != Pause::Full, "must be");
    assert(type != Pause::Remark, "must be");
    assert(type != Pause::Cleanup, "must be");
  }

  static bool is_young_only_pause(Pause type) {
    assert_is_young_pause(type);
    return type == Pause::ConcurrentStartUndo ||
           type == Pause::ConcurrentStartFull ||
           type == Pause::LastYoung ||
           type == Pause::Normal;
  }

  static bool is_mixed_pause(Pause type) {
    assert_is_young_pause(type);
    return type == Pause::Mixed;
  }

  static bool is_last_young_pause(Pause type) {
    assert_is_young_pause(type);
    return type == Pause::LastYoung;
  }

  static bool is_concurrent_start_pause(Pause type) {
    assert_is_young_pause(type);
    return type == Pause::ConcurrentStartFull || type == Pause::ConcurrentStartUndo;
  }

  static bool is_concurrent_cycle_pause(Pause type) {
    return type == Pause::Cleanup || type == Pause::Remark;
  }
};

ENUMERATOR_RANGE(G1CollectorState::Pause, G1CollectorState::Pause::Normal, G1CollectorState::Pause::Full)

#endif // SHARE_GC_G1_G1COLLECTORSTATE_HPP
