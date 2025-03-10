/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1YOUNGGCALLOCATIONFAILUREINJECTOR_HPP
#define SHARE_GC_G1_G1YOUNGGCALLOCATIONFAILUREINJECTOR_HPP

#include "gc/shared/gc_globals.hpp"
#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

#if ALLOCATION_FAILURE_INJECTOR
#define ALLOCATION_FAILURE_INJECTOR_RETURN
#define ALLOCATION_FAILURE_INJECTOR_RETURN_(code)
#define ALLOCATION_FAILURE_INJECTOR_ONLY(code) code
#else
#define ALLOCATION_FAILURE_INJECTOR_RETURN { return; }
#define ALLOCATION_FAILURE_INJECTOR_RETURN_(code) { code }
#define ALLOCATION_FAILURE_INJECTOR_ONLY(code)
#endif // ALLOCATION_FAILURE_INJECTOR

// Support for injecting allocation failures based on the G1GCAllocationFailureALot*
// flags. Analogous to PromotionFailureALot for the other collectors.
//
// Every G1GCAllocationFailureALotInterval collections without evacuation failure
// in between we "arm" the injector to induce allocation failures after
// G1GCAllocationFailureALotCount successful evacuations.
//
// Available only when ALLOCATION_FAILURE_INJECTOR is defined.
class G1YoungGCAllocationFailureInjector {
#if ALLOCATION_FAILURE_INJECTOR
  // Should we inject evacuation failures in the current GC.
  bool _inject_allocation_failure_for_current_gc;

  // Records the number of the last collection when allocation failure happened.
  // Used to determine whether allocation failure injection should be in effect
  // for the current GC.
  size_t _last_collection_with_allocation_failure;

  // Records the regions that will fail evacuation.
  CHeapBitMap _allocation_failure_regions;
#endif

  bool arm_if_needed_for_gc_type(bool for_young_only_phase,
                                 bool during_concurrent_start,
                                 bool mark_or_rebuild_in_progress) ALLOCATION_FAILURE_INJECTOR_RETURN_( return false; );

  // Selects the regions that will fail allocation by G1GCAllocationFailureALotCSetPercent.
  void select_allocation_failure_regions() ALLOCATION_FAILURE_INJECTOR_RETURN;
public:

  G1YoungGCAllocationFailureInjector() ALLOCATION_FAILURE_INJECTOR_RETURN;

  // Arm the allocation failure injector if needed for the current
  // GC (based upon the type of GC and which command line flags are set);
  void arm_if_needed() ALLOCATION_FAILURE_INJECTOR_RETURN;

  // Return true if it's time to cause an allocation failure; the caller
  // provides the (preferably thread-local) counter to minimize performance impact.
  bool allocation_should_fail(size_t& counter, uint region_idx) ALLOCATION_FAILURE_INJECTOR_RETURN_( return false; );

  // Reset the allocation failure injection counters. Should be called at
  // the end of an evacuation pause in which an allocation failure occurred.
  void reset() ALLOCATION_FAILURE_INJECTOR_RETURN;
};

#endif /* SHARE_GC_G1_G1YOUNGGCALLOCATIONFAILUREINJECTOR_HPP */
