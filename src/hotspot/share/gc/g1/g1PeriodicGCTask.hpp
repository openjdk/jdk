/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1PERIODICGCTASK_HPP
#define SHARE_GC_G1_G1PERIODICGCTASK_HPP

#include "gc/g1/g1ServiceThread.hpp"

class G1CollectedHeap;
class G1GCCounters;

// Periodic GC Behavior Table (JDK-8213198):
//
// Interval I            | Concurrent | Threshold T | Action
// ----------------------|------------|-------------|--------------------------------------------------
// N* (not set)          | ?          | ?           | Conc Mark @ ergonomic interval (NEW)
// Y (== 0)              | ?          | ?           | Disabled
// Y (!= 0)              | Y          | Y           | Conc GC @ I, skip if load > T
// Y (!= 0)              | Y          | N           | Conc GC @ I (always)
// Y (!= 0)              | N          | Y           | Full GC @ I if load < T, Conc GC if load >= T (NEW)
// Y (!= 0)              | N          | N           | Full GC @ I (always)
//
// N* = G1PeriodicGCInterval not set on command line (use FLAG_IS_DEFAULT)
// Y  = G1PeriodicGCInterval set on command line
// ?  = don't care
// I  = interval; T = threshold
// GC evaluation is cancelled/reset on any GC.
//
// Runtime flag changes (G1PeriodicGCInterval is MANAGEABLE):
// - If interval > 0 at runtime: use that interval (overrides ergonomic)
// - If interval == 0 at runtime AND was set on cmdline: stays disabled
// - If interval == 0 at runtime AND was NOT set on cmdline: uses ergonomic
// This allows Java code to temporarily enable/disable periodic GC.

// Task handling periodic GCs.
class G1PeriodicGCTask : public G1ServiceTask {
  // Check if conditions are met to start a periodic GC.
  bool should_start_periodic_gc(G1CollectedHeap* g1h, G1GCCounters* counters);

  // Perform the periodic GC check and trigger if appropriate.
  void check_for_periodic_gc();

  // Helper predicates for readability.
  static bool is_periodic_interval_set_on_cmdline();
  static bool is_periodic_gc_explicitly_enabled();
  static bool is_periodic_gc_explicitly_disabled();
  static bool is_load_threshold_enabled();

  // Determine the effective periodic interval (explicit or ergonomic).
  static uintx get_effective_periodic_interval();

public:
  G1PeriodicGCTask(const char* name);
  virtual void execute();

  // Determine whether periodic GC should use concurrent mark or full GC.
  // Implements the behavior table based on flag configuration.
  static bool should_use_concurrent_periodic_gc();
};

#endif // SHARE_GC_G1_G1PERIODICGCTASK_HPP
