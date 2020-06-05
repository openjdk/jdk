/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1CodeBlobClosure.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1GCParPhaseTimesTracker.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RootClosures.hpp"
#include "gc/g1/g1RootProcessor.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/universe.hpp"
#include "runtime/mutex.hpp"
#include "services/management.hpp"
#include "utilities/macros.hpp"

G1RootProcessor::G1RootProcessor(G1CollectedHeap* g1h, uint n_workers) :
    _g1h(g1h),
    _process_strong_tasks(G1RP_PS_NumElements),
    _srs(n_workers) {}

void G1RootProcessor::evacuate_roots(G1ParScanThreadState* pss, uint worker_id) {
  G1GCPhaseTimes* phase_times = _g1h->phase_times();

  G1EvacPhaseTimesTracker timer(phase_times, pss, G1GCPhaseTimes::ExtRootScan, worker_id);

  G1EvacuationRootClosures* closures = pss->closures();
  process_java_roots(closures, phase_times, worker_id);

  process_vm_roots(closures, phase_times, worker_id);

  {
    // Now the CM ref_processor roots.
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::CMRefRoots, worker_id);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_refProcessor_oops_do)) {
      // We need to treat the discovered reference lists of the
      // concurrent mark ref processor as roots and keep entries
      // (which are added by the marking threads) on them live
      // until they can be processed at the end of marking.
      _g1h->ref_processor_cm()->weak_oops_do(closures->strong_oops());
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
                                        CodeBlobClosure* blobs) {
  AllRootsClosures closures(oops, clds);

  process_java_roots(&closures, NULL, 0);
  process_vm_roots(&closures, NULL, 0);

  process_code_cache_roots(blobs, NULL, 0);

  _process_strong_tasks.all_tasks_completed(n_workers());
}

void G1RootProcessor::process_java_roots(G1RootClosures* closures,
                                         G1GCPhaseTimes* phase_times,
                                         uint worker_id) {
  // We need to make make sure that the "strong" nmethods are processed first
  // using the strong closure. Only after that we process the weakly reachable
  // nmethods.
  // We need to strictly separate the strong and weak nmethod processing because
  // any processing claims that nmethod, i.e. will not be iterated again.
  // Which means if an nmethod is processed first and claimed, the strong processing
  // will not happen, and the oops reachable by that nmethod will not be marked
  // properly.
  //
  // That is why we process strong nmethods first, synchronize all threads via a
  // barrier, and only then allow weak processing. To minimize the wait time at
  // that barrier we do the strong nmethod processing first, and immediately after-
  // wards indicate that that thread is done. Hopefully other root processing after
  // nmethod processing is enough so there is no need to wait.
  //
  // This is only required in the concurrent start pause with class unloading enabled.
  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::ThreadRoots, worker_id);
    bool is_par = n_workers() > 1;
    Threads::possibly_parallel_oops_do(is_par,
                                       closures->strong_oops(),
                                       closures->strong_codeblobs());
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::CLDGRoots, worker_id);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_ClassLoaderDataGraph_oops_do)) {
      ClassLoaderDataGraph::roots_cld_do(closures->strong_clds(), closures->weak_clds());
    }
  }
}

void G1RootProcessor::process_vm_roots(G1RootClosures* closures,
                                       G1GCPhaseTimes* phase_times,
                                       uint worker_id) {
  OopClosure* strong_roots = closures->strong_oops();

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::UniverseRoots, worker_id);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_Universe_oops_do)) {
      Universe::oops_do(strong_roots);
    }
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::JNIRoots, worker_id);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_JNIHandles_oops_do)) {
      JNIHandles::oops_do(strong_roots);
    }
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::ObjectSynchronizerRoots, worker_id);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_ObjectSynchronizer_oops_do)) {
      ObjectSynchronizer::oops_do(strong_roots);
    }
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::ManagementRoots, worker_id);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_Management_oops_do)) {
      Management::oops_do(strong_roots);
    }
  }

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::JVMTIRoots, worker_id);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_jvmti_oops_do)) {
      JvmtiExport::oops_do(strong_roots);
    }
  }

#if INCLUDE_AOT
  if (UseAOT) {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::AOTCodeRoots, worker_id);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_aot_oops_do)) {
        AOTLoader::oops_do(strong_roots);
    }
  }
#endif

  {
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::VMGlobalRoots, worker_id);
    if (_process_strong_tasks.try_claim_task(G1RP_PS_VMGlobal_oops_do)) {
      OopStorageSet::vm_global()->oops_do(strong_roots);
    }
  }
}

void G1RootProcessor::process_code_cache_roots(CodeBlobClosure* code_closure,
                                               G1GCPhaseTimes* phase_times,
                                               uint worker_id) {
  if (_process_strong_tasks.try_claim_task(G1RP_PS_CodeCache_oops_do)) {
    CodeCache::blobs_do(code_closure);
  }
}

uint G1RootProcessor::n_workers() const {
  return _srs.n_threads();
}
