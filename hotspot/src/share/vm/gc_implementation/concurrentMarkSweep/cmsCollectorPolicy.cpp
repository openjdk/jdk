/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_cmsCollectorPolicy.cpp.incl"

//
// ConcurrentMarkSweepPolicy methods
//

ConcurrentMarkSweepPolicy::ConcurrentMarkSweepPolicy() {
  initialize_all();
}

void ConcurrentMarkSweepPolicy::initialize_generations() {
  initialize_perm_generation(PermGen::ConcurrentMarkSweep);
  _generations = new GenerationSpecPtr[number_of_generations()];
  if (_generations == NULL)
    vm_exit_during_initialization("Unable to allocate gen spec");

  if (UseParNewGC && ParallelGCThreads > 0) {
    if (UseAdaptiveSizePolicy) {
      _generations[0] = new GenerationSpec(Generation::ASParNew,
                                           _initial_gen0_size, _max_gen0_size);
    } else {
      _generations[0] = new GenerationSpec(Generation::ParNew,
                                           _initial_gen0_size, _max_gen0_size);
    }
  } else {
    _generations[0] = new GenerationSpec(Generation::DefNew,
                                         _initial_gen0_size, _max_gen0_size);
  }
  if (UseAdaptiveSizePolicy) {
    _generations[1] = new GenerationSpec(Generation::ASConcurrentMarkSweep,
                            _initial_gen1_size, _max_gen1_size);
  } else {
    _generations[1] = new GenerationSpec(Generation::ConcurrentMarkSweep,
                            _initial_gen1_size, _max_gen1_size);
  }

  if (_generations[0] == NULL || _generations[1] == NULL) {
    vm_exit_during_initialization("Unable to allocate gen spec");
  }
}

void ConcurrentMarkSweepPolicy::initialize_size_policy(size_t init_eden_size,
                                               size_t init_promo_size,
                                               size_t init_survivor_size) {
  double max_gc_minor_pause_sec = ((double) MaxGCMinorPauseMillis)/1000.0;
  double max_gc_pause_sec = ((double) MaxGCPauseMillis)/1000.0;
  _size_policy = new CMSAdaptiveSizePolicy(init_eden_size,
                                           init_promo_size,
                                           init_survivor_size,
                                           max_gc_minor_pause_sec,
                                           max_gc_pause_sec,
                                           GCTimeRatio);
}

void ConcurrentMarkSweepPolicy::initialize_gc_policy_counters() {
  // initialize the policy counters - 2 collectors, 3 generations
  if (UseParNewGC && ParallelGCThreads > 0) {
    _gc_policy_counters = new GCPolicyCounters("ParNew:CMS", 2, 3);
  }
  else {
    _gc_policy_counters = new GCPolicyCounters("Copy:CMS", 2, 3);
  }
}

// Returns true if the incremental mode is enabled.
bool ConcurrentMarkSweepPolicy::has_soft_ended_eden()
{
  return CMSIncrementalMode;
}


//
// ASConcurrentMarkSweepPolicy methods
//

void ASConcurrentMarkSweepPolicy::initialize_gc_policy_counters() {

  assert(size_policy() != NULL, "A size policy is required");
  // initialize the policy counters - 2 collectors, 3 generations
  if (UseParNewGC && ParallelGCThreads > 0) {
    _gc_policy_counters = new CMSGCAdaptivePolicyCounters("ParNew:CMS", 2, 3,
      size_policy());
  }
  else {
    _gc_policy_counters = new CMSGCAdaptivePolicyCounters("Copy:CMS", 2, 3,
      size_policy());
  }
}
