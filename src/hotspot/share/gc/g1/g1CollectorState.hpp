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
    // Indicates that we are about to start or in the prepare mixed gc in the Young-Only
    // phase before the Mixed phase. This GC is required to keep pause time requirements.
    YoungPrepareMixed,
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
  inline void set_in_normal_young_gc();
  inline void set_in_space_reclamation_phase();
  inline void set_in_full_gc();

  inline void set_in_concurrent_start_gc();
  inline void set_in_prepare_mixed_gc();

  inline void set_initiate_conc_mark_if_possible(bool v);

  // Phase getters
  inline bool is_in_young_only_phase() const;
  inline bool is_in_mixed_phase() const;

  // Specific pauses
  inline bool is_in_concurrent_start_gc() const;
  inline bool is_in_prepare_mixed_gc() const;
  inline bool is_in_full_gc() const;

  inline bool initiate_conc_mark_if_possible() const;

  bool is_in_concurrent_cycle() const;
  bool is_in_marking() const;
  bool is_in_mark_or_rebuild() const;
  bool is_in_reset_for_next_cycle() const;

  enum class Pause : uint {
    Normal,
    ConcurrentStartFull,
    ConcurrentStartUndo,
    PrepareMixed,
    Cleanup,
    Remark,
    Mixed,
    Full
  };

  // Calculate GC Pause Type from internal state.
  Pause gc_pause_type(bool concurrent_operation_is_full_mark) const;

  static const char* to_string(Pause type);

  // Pause kind queries
  inline static void assert_is_young_pause(Pause type);

  inline static bool is_young_only_pause(Pause type);
  inline static bool is_concurrent_start_pause(Pause type);
  inline static bool is_prepare_mixed_pause(Pause type);
  inline static bool is_mixed_pause(Pause type);

  inline static bool is_concurrent_cycle_pause(Pause type);
};

ENUMERATOR_RANGE(G1CollectorState::Pause, G1CollectorState::Pause::Normal, G1CollectorState::Pause::Full)

#endif // SHARE_GC_G1_G1COLLECTORSTATE_HPP
