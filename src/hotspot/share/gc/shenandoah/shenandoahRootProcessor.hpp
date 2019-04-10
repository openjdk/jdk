/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_HPP

#include "code/codeCache.hpp"
#include "gc/shared/oopStorageParState.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "gc/shared/weakProcessorPhaseTimes.hpp"
#include "gc/shared/workgroup.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"

class ParallelCLDRootIterator {
public:
  ParallelCLDRootIterator();
  void root_cld_do(CLDClosure* strong, CLDClosure* weak);
};

enum Shenandoah_process_roots_tasks {
  SHENANDOAH_RP_PS_Universe_oops_do,
  SHENANDOAH_RP_PS_JNIHandles_oops_do,
  SHENANDOAH_RP_PS_ObjectSynchronizer_oops_do,
  SHENANDOAH_RP_PS_Management_oops_do,
  SHENANDOAH_RP_PS_SystemDictionary_oops_do,
  SHENANDOAH_RP_PS_jvmti_oops_do,
  // Leave this one last.
  SHENANDOAH_RP_PS_NumElements
};

class ShenandoahRootProcessor : public StackObj {
  SubTasksDone* _process_strong_tasks;
  StrongRootsScope _srs;
  OopStorage::ParState<false, false> _par_state_string;
  ShenandoahPhaseTimings::Phase _phase;
  ParallelCLDRootIterator _cld_iterator;
  ShenandoahAllCodeRootsIterator _coderoots_all_iterator;
  CodeBlobClosure* _threads_nmethods_cl;
  WeakProcessorPhaseTimes _weak_processor_timings;
  WeakProcessor::Task     _weak_processor_task;
  bool                    _processed_weak_roots;

  void process_java_roots(OopClosure* scan_non_heap_roots,
                          CLDClosure* scan_strong_clds,
                          CLDClosure* scan_weak_clds,
                          CodeBlobClosure* scan_strong_code,
                          ThreadClosure* thread_cl,
                          uint worker_i);

  void process_vm_roots(OopClosure* scan_non_heap_roots,
                        uint worker_i);

  void weak_processor_timing_to_shenandoah_timing(const WeakProcessorPhases::Phase wpp,
                                                  const ShenandoahPhaseTimings::GCParPhases spp,
                                                  ShenandoahWorkerTimings* worker_times) const;

public:
  ShenandoahRootProcessor(ShenandoahHeap* heap, uint n_workers,
                          ShenandoahPhaseTimings::Phase phase);
  ~ShenandoahRootProcessor();

  // Apply oops, clds and blobs to all strongly reachable roots in the system.
  // Optionally, apply class loader closure to weak clds, depending on class unloading
  // for the particular GC cycles.
  void process_strong_roots(OopClosure* oops,
                            CLDClosure* clds,
                            CodeBlobClosure* blobs,
                            ThreadClosure* thread_cl,
                            uint worker_id);

  // Apply oops, clds and blobs to strongly reachable roots in the system
  void process_all_roots(OopClosure* oops,
                         CLDClosure* clds,
                         CodeBlobClosure* blobs,
                         ThreadClosure* thread_cl,
                         uint worker_id);

  // Apply oops, clds and blobs to strongly and weakly reachable roots in the system
  template <typename IsAlive>
  void update_all_roots(OopClosure* oops,
                        CLDClosure* clds,
                        CodeBlobClosure* blobs,
                        ThreadClosure* thread_cl,
                        uint worker_id);

  // For slow debug/verification code
  void process_all_roots_slow(OopClosure* oops);

  // Number of worker threads used by the root processor.
  uint n_workers() const;
};

class ShenandoahRootEvacuator : public StackObj {
  SubTasksDone* _evacuation_tasks;
  StrongRootsScope _srs;
  ShenandoahPhaseTimings::Phase _phase;
  ShenandoahCsetCodeRootsIterator _coderoots_cset_iterator;
  OopStorage::ParState<false, false> _par_state_string;

  enum Shenandoah_evacuate_roots_tasks {
    SHENANDOAH_EVAC_Universe_oops_do,
    SHENANDOAH_EVAC_ObjectSynchronizer_oops_do,
    SHENANDOAH_EVAC_Management_oops_do,
    SHENANDOAH_EVAC_SystemDictionary_oops_do,
    SHENANDOAH_EVAC_jvmti_oops_do,
    // Leave this one last.
    SHENANDOAH_EVAC_NumElements
  };
public:
  ShenandoahRootEvacuator(ShenandoahHeap* heap, uint n_workers,
                          ShenandoahPhaseTimings::Phase phase);
  ~ShenandoahRootEvacuator();

  void process_evacuate_roots(OopClosure* oops,
                              CodeBlobClosure* blobs,
                              uint worker_id);

  // Number of worker threads used by the root processor.
  uint n_workers() const;
};
#endif // SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_HPP
