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

#include "precompiled.hpp"

#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahTimingTracker.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/thread.hpp"
#include "services/management.hpp"

struct PhaseMap {
  WeakProcessorPhases::Phase            _weak_processor_phase;
  ShenandoahPhaseTimings::GCParPhases   _shenandoah_phase;
};

static const struct PhaseMap phase_mapping[] = {
#if INCLUDE_JVMTI
  {WeakProcessorPhases::jvmti,                 ShenandoahPhaseTimings::JVMTIWeakRoots},
#endif
#if INCLUDE_JFR
  {WeakProcessorPhases::jfr,                   ShenandoahPhaseTimings::JFRWeakRoots},
#endif
  {WeakProcessorPhases::jni,                   ShenandoahPhaseTimings::JNIWeakRoots},
  {WeakProcessorPhases::stringtable,           ShenandoahPhaseTimings::StringTableRoots},
  {WeakProcessorPhases::resolved_method_table, ShenandoahPhaseTimings::ResolvedMethodTableRoots},
  {WeakProcessorPhases::vm,                    ShenandoahPhaseTimings::VMWeakRoots}
};

STATIC_ASSERT(sizeof(phase_mapping) / sizeof(PhaseMap) == WeakProcessorPhases::phase_count);

ShenandoahRootProcessor::ShenandoahRootProcessor(ShenandoahHeap* heap, uint n_workers,
                                                 ShenandoahPhaseTimings::Phase phase) :
  _process_strong_tasks(new SubTasksDone(SHENANDOAH_RP_PS_NumElements)),
  _srs(n_workers),
  _phase(phase),
  _coderoots_all_iterator(ShenandoahCodeRoots::iterator()),
  _weak_processor_timings(n_workers),
  _weak_processor_task(&_weak_processor_timings, n_workers),
  _processed_weak_roots(false) {
  heap->phase_timings()->record_workers_start(_phase);

  if (ShenandoahStringDedup::is_enabled()) {
    StringDedup::gc_prologue(false);
  }
}

ShenandoahRootProcessor::~ShenandoahRootProcessor() {
  delete _process_strong_tasks;
  if (ShenandoahStringDedup::is_enabled()) {
    StringDedup::gc_epilogue();
  }

  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();

  if (_processed_weak_roots) {
    assert(_weak_processor_timings.max_threads() == n_workers(), "Must match");
    for (uint index = 0; index < WeakProcessorPhases::phase_count; index ++) {
      weak_processor_timing_to_shenandoah_timing(phase_mapping[index]._weak_processor_phase,
                                                 phase_mapping[index]._shenandoah_phase,
                                                 worker_times);
    }
  }

  ShenandoahHeap::heap()->phase_timings()->record_workers_end(_phase);
}

void ShenandoahRootProcessor::weak_processor_timing_to_shenandoah_timing(const WeakProcessorPhases::Phase wpp,
                                                                         const ShenandoahPhaseTimings::GCParPhases spp,
                                                                         ShenandoahWorkerTimings* worker_times) const {
  if (WeakProcessorPhases::is_serial(wpp)) {
    worker_times->record_time_secs(spp, 0, _weak_processor_timings.phase_time_sec(wpp));
  } else {
    for (uint index = 0; index < _weak_processor_timings.max_threads(); index ++) {
      worker_times->record_time_secs(spp, index, _weak_processor_timings.worker_time_sec(index, wpp));
    }
  }
}

void ShenandoahRootProcessor::process_all_roots_slow(OopClosure* oops) {
  CLDToOopClosure clds(oops, ClassLoaderData::_claim_strong);
  CodeBlobToOopClosure blobs(oops, !CodeBlobToOopClosure::FixRelocations);

  CodeCache::blobs_do(&blobs);
  ClassLoaderDataGraph::cld_do(&clds);
  Universe::oops_do(oops);
  Management::oops_do(oops);
  JvmtiExport::oops_do(oops);
  JNIHandles::oops_do(oops);
  ObjectSynchronizer::oops_do(oops);
  SystemDictionary::oops_do(oops);

  // Do thread roots the last. This allows verification code to find
  // any broken objects from those special roots first, not the accidental
  // dangling reference from the thread root.
  Threads::possibly_parallel_oops_do(false, oops, &blobs);
}

void ShenandoahRootProcessor::process_strong_roots(OopClosure* oops,
                                                   CLDClosure* clds,
                                                   CodeBlobClosure* blobs,
                                                   ThreadClosure* thread_cl,
                                                   uint worker_id) {

  process_java_roots(oops, clds, NULL, blobs, thread_cl, worker_id);
  process_vm_roots(oops, worker_id);

  _process_strong_tasks->all_tasks_completed(n_workers());
}

void ShenandoahRootProcessor::process_all_roots(OopClosure* oops,
                                                CLDClosure* clds,
                                                CodeBlobClosure* blobs,
                                                ThreadClosure* thread_cl,
                                                uint worker_id) {

  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
  process_java_roots(oops, clds, clds, blobs, thread_cl, worker_id);
  process_vm_roots(oops, worker_id);

  if (blobs != NULL) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
    _coderoots_all_iterator.possibly_parallel_blobs_do(blobs);
  }

  _process_strong_tasks->all_tasks_completed(n_workers());

}

class ShenandoahParallelOopsDoThreadClosure : public ThreadClosure {
private:
  OopClosure* _f;
  CodeBlobClosure* _cf;
  ThreadClosure* _thread_cl;
public:
  ShenandoahParallelOopsDoThreadClosure(OopClosure* f, CodeBlobClosure* cf, ThreadClosure* thread_cl) :
    _f(f), _cf(cf), _thread_cl(thread_cl) {}

  void do_thread(Thread* t) {
    if (_thread_cl != NULL) {
      _thread_cl->do_thread(t);
    }
    t->oops_do(_f, _cf);
  }
};

void ShenandoahRootProcessor::process_java_roots(OopClosure* strong_roots,
                                                 CLDClosure* strong_clds,
                                                 CLDClosure* weak_clds,
                                                 CodeBlobClosure* strong_code,
                                                 ThreadClosure* thread_cl,
                                                 uint worker_id)
{
  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
  // Iterating over the CLDG and the Threads are done early to allow us to
  // first process the strong CLDs and nmethods and then, after a barrier,
  // let the thread process the weak CLDs and nmethods.
  {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::CLDGRoots, worker_id);
    _cld_iterator.root_cld_do(strong_clds, weak_clds);
  }

  {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::ThreadRoots, worker_id);
    bool is_par = n_workers() > 1;
    ResourceMark rm;
    ShenandoahParallelOopsDoThreadClosure cl(strong_roots, strong_code, thread_cl);
    Threads::possibly_parallel_threads_do(is_par, &cl);
  }
}

void ShenandoahRootProcessor::process_vm_roots(OopClosure* strong_roots,
                                               uint worker_id) {
  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
  if (_process_strong_tasks->try_claim_task(SHENANDOAH_RP_PS_Universe_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::UniverseRoots, worker_id);
    Universe::oops_do(strong_roots);
  }

  if (_process_strong_tasks->try_claim_task(SHENANDOAH_RP_PS_JNIHandles_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::JNIRoots, worker_id);
    JNIHandles::oops_do(strong_roots);
  }
  if (_process_strong_tasks->try_claim_task(SHENANDOAH_RP_PS_Management_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::ManagementRoots, worker_id);
    Management::oops_do(strong_roots);
  }
  if (_process_strong_tasks->try_claim_task(SHENANDOAH_RP_PS_jvmti_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::JVMTIRoots, worker_id);
    JvmtiExport::oops_do(strong_roots);
  }
  if (_process_strong_tasks->try_claim_task(SHENANDOAH_RP_PS_SystemDictionary_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::SystemDictionaryRoots, worker_id);
    SystemDictionary::oops_do(strong_roots);
  }

  {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::ObjectSynchronizerRoots, worker_id);
    if (_process_strong_tasks->try_claim_task(SHENANDOAH_RP_PS_ObjectSynchronizer_oops_do)) {
      ObjectSynchronizer::oops_do(strong_roots);
    }
  }
}

uint ShenandoahRootProcessor::n_workers() const {
  return _srs.n_threads();
}

ShenandoahRootEvacuator::ShenandoahRootEvacuator(ShenandoahHeap* heap, uint n_workers, ShenandoahPhaseTimings::Phase phase) :
  _evacuation_tasks(new SubTasksDone(SHENANDOAH_EVAC_NumElements)),
  _srs(n_workers),
  _phase(phase),
  _coderoots_cset_iterator(ShenandoahCodeRoots::cset_iterator()) {
  heap->phase_timings()->record_workers_start(_phase);
  if (ShenandoahStringDedup::is_enabled()) {
    StringDedup::gc_prologue(false);
  }
}

ShenandoahRootEvacuator::~ShenandoahRootEvacuator() {
  delete _evacuation_tasks;
  if (ShenandoahStringDedup::is_enabled()) {
    StringDedup::gc_epilogue();
  }
  ShenandoahHeap::heap()->phase_timings()->record_workers_end(_phase);
}

void ShenandoahRootEvacuator::process_evacuate_roots(OopClosure* oops,
                                                     CodeBlobClosure* blobs,
                                                     uint worker_id) {

  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
  {
    bool is_par = n_workers() > 1;
    ResourceMark rm;
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::ThreadRoots, worker_id);

    Threads::possibly_parallel_oops_do(is_par, oops, NULL);
  }

  if (blobs != NULL) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
    _coderoots_cset_iterator.possibly_parallel_blobs_do(blobs);
  }

  if (ShenandoahStringDedup::is_enabled()) {
    ShenandoahForwardedIsAliveClosure is_alive;
    ShenandoahStringDedup::parallel_oops_do(&is_alive, oops, worker_id);
  }

  if (_evacuation_tasks->try_claim_task(SHENANDOAH_EVAC_Universe_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::UniverseRoots, worker_id);
    Universe::oops_do(oops);
  }

  if (_evacuation_tasks->try_claim_task(SHENANDOAH_EVAC_Management_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::ManagementRoots, worker_id);
    Management::oops_do(oops);
  }

  if (_evacuation_tasks->try_claim_task(SHENANDOAH_EVAC_jvmti_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::JVMTIRoots, worker_id);
    JvmtiExport::oops_do(oops);
    ShenandoahForwardedIsAliveClosure is_alive;
    JvmtiExport::weak_oops_do(&is_alive, oops);
  }

  if (_evacuation_tasks->try_claim_task(SHENANDOAH_EVAC_SystemDictionary_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::SystemDictionaryRoots, worker_id);
    SystemDictionary::oops_do(oops);
  }

  if (_evacuation_tasks->try_claim_task(SHENANDOAH_EVAC_ObjectSynchronizer_oops_do)) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::ObjectSynchronizerRoots, worker_id);
    ObjectSynchronizer::oops_do(oops);
  }

}

uint ShenandoahRootEvacuator::n_workers() const {
  return _srs.n_threads();
}

// Implemenation of ParallelCLDRootIterator
ParallelCLDRootIterator::ParallelCLDRootIterator() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must at safepoint");
  ClassLoaderDataGraph::clear_claimed_marks();
}

void ParallelCLDRootIterator::root_cld_do(CLDClosure* strong, CLDClosure* weak) {
    ClassLoaderDataGraph::roots_cld_do(strong, weak);
}
