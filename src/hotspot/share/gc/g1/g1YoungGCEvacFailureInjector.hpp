/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1YOUNGGCEVACUATIONFAILUREINJECTOR_HPP
#define SHARE_GC_G1_G1YOUNGGCEVACUATIONFAILUREINJECTOR_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

// Support for injecting evacuation failures based on the G1EvacuationFailureALot*
// flags. Analogous to PromotionFailureALot for the other collectors.
//
// Every G1EvacuationFailureALotInterval collections without evacuation failure
// inbetween we "arm" the injector to induce evacuation failures after
// G1EvacuationFailureALotCount successful evacuations.
//
// Available only in non-product builds.
class G1YoungGCEvacFailureInjector {
#ifndef PRODUCT
  // Should we inject evacuation failures in the current GC.
  bool _inject_evacuation_failure_for_current_gc;

  // Records the number of the last collection when evacuation failure happened.
  // Used to determine whether evacuation failure injection should be in effect
  // for the current GC.
  size_t _last_collection_with_evacuation_failure;
#endif

  bool arm_if_needed_for_gc_type(bool for_young_gc,
                                 bool during_concurrent_start,
                                 bool mark_or_rebuild_in_progress) PRODUCT_RETURN_( return false; );
public:

  // Arm the evacuation failure injector if needed for the current
  // GC (based upon the type of GC and which command line flags are set);
  void arm_if_needed() PRODUCT_RETURN;

  // Return true if it's time to cause an evacuation failure; the caller
  // provides the (preferably thread-local) counter to minimize performance impact.
  bool evacuation_should_fail(size_t& counter) PRODUCT_RETURN_( return false; );

  // Reset the evacuation failure injection counters. Should be called at
  // the end of an evacuation pause in which an evacuation failure occurred.
  void reset() PRODUCT_RETURN;
};

#endif /* SHARE_GC_G1_G1YOUNGGCEVACUATIONFAILUREINJECTOR_HPP */

