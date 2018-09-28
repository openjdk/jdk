/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "aot/aotLoader.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1CodeBlobClosure.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RootClosures.hpp"
#include "gc/g1/g1RootProcessor.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/shared/oopStorageParState.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/mutex.hpp"
#include "services/management.hpp"
#include "utilities/macros.hpp"

void G1RootProcessor::worker_has_discovered_all_strong_classes() {
  assert(ClassUnloadingWithConcurrentMark, "Currently only needed when doing G1 Class Unloading");

  uint new_value = (uint)Atomic::add(1, &_n_workers_discovered_strong_classes);
  if (new_value == n_workers()) {
    // This thread is last. Notify the others.
    MonitorLockerEx ml(&_lock, Mutex::_no_safepoint_check_flag);
    _lock.notify_all();
  }
}

void G1RootProcessor::wait_until_all_strong_classes_discovered() {
  assert(ClassUnloadingWithConcurrentMark, "Currently only needed when doing G1 Class Unloading");

  if ((uint)_n_workers_discovered_strong_classes != n_workers()) {
    MonitorLockerEx ml(&_lock, Mutex::_no_safepoint_check_flag);
    while ((uint)_n_workers_discovered_strong_classes != n_workers()) {
      _lock.wait(Mutex::_no_safepoint_check_flag, 0, false);
    }
  }
}

G1RootProcessor::G1RootProcessor(G1CollectedHeap* g1h, uint n_workers) :
    _g1h(g1h),
    _process_strong_tasks(G1RP_PS_NumElements),
    _srs(n_workers),
    _par_state_string(StringTable::weak_storage()),
    _lock(Mutex::leaf, "G1 Root Scanning barrier lock", false, Monitor::_safepoint_check_never),
    _n_workers_discovered_strong_classes(0) {}

void G1RootProcessor::evacuate_roots(G1ParScanThreadState* pss, uint worker_i) {
  G1GCPhaseTimes* phase_times = _g1h->g1_policy()->phase_times();

  G1EvacPhaseTimesTracker timer(phase_times, pss, G1GCPhaseTimes::ExtRootScan, worker_i);

  G1EvacuationRootClosures* closures = pss->closures();
  process_java_roots(closures, phase_times, worker_i);

  // This is the point where this worker thread will not find more strong CLDs/nmethods.
  // Report this so G1 can synchronize the strong and weak CLDs/nmethods processing.
  if (closures->trace_metadata()) {
    worker_has_discovered_all_strong_classes();
  }

  process_vm_roots(closures, phase_times, worker_i);
  process_string_table_roots(closures, phase_times, worker_i);

  {
    // Now the CM ref_processor roots.
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::CMRefRoots, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_refProcessor_oops_do)) {
      // We need to treat the discovered reference lists of the
      // concurrent mark ref processor as roots and keep entries
      // (which are added by the marking threads) on them live
      // until they can be processed at the end of marking.
      _g1h->ref_processor_cm()->weak_oops_do(closures->strong_oops());
    }
  }

  if (closures->trace_metadata()) {
    {
      G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::WaitForStrongCLD, worker_i);
      // Barrier to make sure all workers passed
      // the strong CLD and strong nmethods phases.
      wait_until_all_strong_classes_discovered();
    }

    // Now take the complement of the strong CLDs.
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::WeakCLDRoots, worker_i);
    assert(closures->second_pass_weak_clds() != NULL, "Should be non-null if we are tracing metadata.");
    ClassLoaderDataGraph::roots_cld_do(NULL, closures->second_pass_weak_clds());
  } else {
    phase_times->record_time_secs(G1GCPhaseTimes::WaitForStrongCLD, worker_i, 0.0);
    phase_times->record_time_secs(G1GCPhaseTimes::WeakCLDRoots, worker_i, 0.0);
    assert(closures->second_pass_weak_clds() == NULL, "Should be null if not tracing metadata.");
  }

  // During conc marking we have to filter the per-thread SATB buffers
  // to make sure we remove any oops into the CSet (which will show up
  // as implicitly live).
  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::SATBFiltering, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_filter_satb_buffers) && _g1h->collector_state()->mark_or_rebuild_in_progress()) {
      G1BarrierSet::satb_mark_queue_set().filter_thread_buffers();
    }
  }

  _process_strong_tasks.all_tasks_completed(n_workers());
}

// Adaptor to pass the closures to the strong roots in the VM.
class StrongRootsClosures : public G1RootClosures {
  OopClosure* _roots;
  CLDClosure* _clds;
  CodeBlobClosure* _blobs;
public:
  StrongRootsClosures(OopClosure* roots, CLDClosure* clds, CodeBlobClosure* blobs) :
      _roots(roots), _clds(clds), _blobs(blobs) {}

  OopClosure* weak_oops()   { return NULL; }
  OopClosure* strong_oops() { return _roots; }

  CLDClosure* weak_clds()        { return NULL; }
  CLDClosure* strong_clds()      { return _clds; }

  CodeBlobClosure* strong_codeblobs() { return _blobs; }
};

void G1RootProcessor::process_strong_roots(OopClosure* oops,
                                           CLDClosure* clds,
                                           CodeBlobClosure* blobs) {
  StrongRootsClosures closures(oops, clds, blobs);

  process_java_roots(&closures, NULL, 0);
  process_vm_roots(&closures, NULL, 0);

  _process_strong_tasks.all_tasks_completed(n_workers());
}

// Adaptor to pass the closures to all the roots in the VM.
class AllRootsClosures : public G1RootClosures {
  OopClosure* _roots;
  CLDClosure* _clds;
public:
  AllRootsClosures(OopClosure* roots, CLDClosure* clds) :
      _roots(roots), _clds(clds) {}

  OopClosure* weak_oops() { return _roots; }
  OopClosure* strong_oops() { return _roots; }

  // By returning the same CLDClosure for both weak and strong CLDs we ensure
  // that a single walk of the CLDG will invoke the closure on all CLDs i the
  // system.
  CLDClosure* weak_clds() { return _clds; }
  CLDClosure* strong_clds() { return _clds; }

  // We don't want to visit code blobs more than once, so we return NULL for the
  // strong case and walk the entire code cache as a separate step.
  CodeBlobClosure* strong_codeblobs() { return NULL; }
};

void G1RootProcessor::process_all_roots(OopClosure* oops,
                                        CLDClosure* clds,
                                        CodeBlobClosure* blobs,
                                        bool process_string_table) {
  AllRootsClosures closures(oops, clds);

  process_java_roots(&closures, NULL, 0);
  process_vm_roots(&closures, NULL, 0);

  if (process_string_table) {
    process_string_table_roots(&closures, NULL, 0);
  }
  process_code_cache_roots(blobs, NULL, 0);

  _process_strong_tasks.all_tasks_completed(n_workers());
}

void G1RootProcessor::process_all_roots(OopClosure* oops,
                                        CLDClosure* clds,
                                        CodeBlobClosure* blobs) {
  process_all_roots(oops, clds, blobs, true);
}

void G1RootProcessor::process_all_roots_no_string_table(OopClosure* oops,
                                                        CLDClosure* clds,
                                                        CodeBlobClosure* blobs) {
  assert(!ClassUnloading, "Should only be used when class unloading is disabled");
  process_all_roots(oops, clds, blobs, false);
}

void G1RootProcessor::process_java_roots(G1RootClosures* closures,
                                         G1GCPhaseTimes* phase_times,
                                         uint worker_i) {
  // Iterating over the CLDG and the Threads are done early to allow us to
  // first process the strong CLDs and nmethods and then, after a barrier,
  // let the thread process the weak CLDs and nmethods.
  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::CLDGRoots, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_ClassLoaderDataGraph_oops_do)) {
      ClassLoaderDataGraph::roots_cld_do(closures->strong_clds(), closures->weak_clds());
    }
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::ThreadRoots, worker_i);
    bool is_par = n_workers() > 1;
    Threads::possibly_parallel_oops_do(is_par,
                                       closures->strong_oops(),
                                       closures->strong_codeblobs());
  }
}

void G1RootProcessor::process_vm_roots(G1RootClosures* closures,
                                       G1GCPhaseTimes* phase_times,
                                       uint worker_i) {
  OopClosure* strong_roots = closures->strong_oops();

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::UniverseRoots, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_Universe_oops_do)) {
      Universe::oops_do(strong_roots);
    }
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::JNIRoots, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_JNIHandles_oops_do)) {
      JNIHandles::oops_do(strong_roots);
    }
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::ObjectSynchronizerRoots, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_ObjectSynchronizer_oops_do)) {
      ObjectSynchronizer::oops_do(strong_roots);
    }
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::ManagementRoots, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_Management_oops_do)) {
      Management::oops_do(strong_roots);
    }
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::JVMTIRoots, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_jvmti_oops_do)) {
      JvmtiExport::oops_do(strong_roots);
    }
  }

#if INCLUDE_AOT
  if (UseAOT) {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::AOTCodeRoots, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_aot_oops_do)) {
        AOTLoader::oops_do(strong_roots);
    }
  }
#endif

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::SystemDictionaryRoots, worker_i);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_SystemDictionary_oops_do)) {
      SystemDictionary::oops_do(strong_roots);
    }
  }
}

void G1RootProcessor::process_string_table_roots(G1RootClosures* closures,
                                                 G1GCPhaseTimes* phase_times,
                                                 uint worker_i) {
  assert(closures->weak_oops() != NULL, "Should only be called when all roots are processed");
  G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::StringTableRoots, worker_i);
  // All threads execute the following. A specific chunk of buckets
  // from the StringTable are the individual tasks.
  StringTable::possibly_parallel_oops_do(&_par_state_string, closures->weak_oops());
}

void G1RootProcessor::process_code_cache_roots(CodeBlobClosure* code_closure,
                                               G1GCPhaseTimes* phase_times,
                                               uint worker_i) {
  if (_process_strong_tasks.try_claim_task(G1RP_PS_CodeCache_oops_do)) {
    CodeCache::blobs_do(code_closure);
  }
}

uint G1RootProcessor::n_workers() const {
  return _srs.n_threads();
}
