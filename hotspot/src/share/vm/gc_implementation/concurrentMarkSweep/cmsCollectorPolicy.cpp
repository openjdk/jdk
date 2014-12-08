/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc_implementation/shared/adaptiveSizePolicy.hpp"
#include "gc_implementation/concurrentMarkSweep/cmsCollectorPolicy.hpp"
#include "gc_implementation/parNew/parNewGeneration.hpp"
#include "gc_implementation/shared/gcPolicyCounters.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "memory/cardTableRS.hpp"
#include "memory/collectorPolicy.hpp"
#include "memory/gcLocker.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/generationSpec.hpp"
#include "memory/space.hpp"
#include "memory/universe.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"

//
// ConcurrentMarkSweepPolicy methods
//

void ConcurrentMarkSweepPolicy::initialize_alignments() {
  _space_alignment = _gen_alignment = (uintx)Generation::GenGrain;
  _heap_alignment = compute_heap_alignment();
}

void ConcurrentMarkSweepPolicy::initialize_generations() {
  _generations = NEW_C_HEAP_ARRAY(GenerationSpecPtr, number_of_generations(), mtGC);
  _generations[0] = new GenerationSpec(Generation::ParNew, _initial_young_size, _max_young_size);
  _generations[1] = new GenerationSpec(Generation::ConcurrentMarkSweep, _initial_old_size, _max_old_size);
}

void ConcurrentMarkSweepPolicy::initialize_size_policy(size_t init_eden_size,
                                               size_t init_promo_size,
                                               size_t init_survivor_size) {
  double max_gc_pause_sec = ((double) MaxGCPauseMillis)/1000.0;
  _size_policy = new AdaptiveSizePolicy(init_eden_size,
                                        init_promo_size,
                                        init_survivor_size,
                                        max_gc_pause_sec,
                                        GCTimeRatio);
}

void ConcurrentMarkSweepPolicy::initialize_gc_policy_counters() {
  // initialize the policy counters - 2 collectors, 3 generations
  _gc_policy_counters = new GCPolicyCounters("ParNew:CMS", 2, 3);
}
