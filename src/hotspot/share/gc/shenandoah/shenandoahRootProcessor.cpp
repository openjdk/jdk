/*
 * Copyright (c) 2015, 2018, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahRootProcessor.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahTimingTracker.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/thread.hpp"
#include "services/management.hpp"

ShenandoahRootProcessor::ShenandoahRootProcessor(ShenandoahHeap* heap, uint n_workers,
                                                 ShenandoahPhaseTimings::Phase phase) :
  _process_strong_tasks(new SubTasksDone(SHENANDOAH_RP_PS_NumElements)),
  _srs(n_workers),
  _par_state_string(StringTable::weak_storage()),
  _phase(phase),
  _coderoots_all_iterator(ShenandoahCodeRoots::iterator())
{
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

  ShenandoahHeap::heap()->phase_timings()->record_workers_end(_phase);
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
  WeakProcessor::oops_do(oops);
  ObjectSynchronizer::oops_do(oops);
  SystemDictionary::oops_do(oops);
  StringTable::oops_do(oops);

  if (ShenandoahStringDedup::is_enabled()) {
    ShenandoahStringDedup::oops_do_slow(oops);
  }

  // Do thread roots the last. This allows verification code to find
  // any broken objects from those special roots first, not the accidental
  // dangling reference from the thread root.
  Threads::possibly_parallel_oops_do(false, oops, &blobs);
}

void ShenandoahRootProcessor::process_strong_roots(OopClosure* oops,
                                                   OopClosure* weak_oops,
                                                   CLDClosure* clds,
                                                   CLDClosure* weak_clds,
                                                   CodeBlobClosure* blobs,
                                                   ThreadClosure* thread_cl,
                                                   uint worker_id) {

  process_java_roots(oops, clds, weak_clds, blobs, thread_cl, worker_id);
  process_vm_roots(oops, NULL, weak_oops, worker_id);

  _process_strong_tasks->all_tasks_completed(n_workers());
}

void ShenandoahRootProcessor::process_all_roots(OopClosure* oops,
                                                OopClosure* weak_oops,
                                                CLDClosure* clds,
                                                CodeBlobClosure* blobs,
                                                ThreadClosure* thread_cl,
                                                uint worker_id) {

  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
  process_java_roots(oops, clds, clds, blobs, thread_cl, worker_id);
  process_vm_roots(oops, oops, weak_oops, worker_id);

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
                                               OopClosure* weak_roots,
                                               OopClosure* jni_weak_roots,
                                               uint worker_id)
{
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
  if (jni_weak_roots != NULL) {
    if (_process_strong_tasks->try_claim_task(SHENANDOAH_RP_PS_JNIHandles_weak_oops_do)) {
      ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::JNIWeakRoots, worker_id);
      WeakProcessor::oops_do(jni_weak_roots);
    }
  }

  if (ShenandoahStringDedup::is_enabled() && weak_roots != NULL) {
    ShenandoahStringDedup::parallel_oops_do(weak_roots, worker_id);
  }

  {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::ObjectSynchronizerRoots, worker_id);
    if (_process_strong_tasks->try_claim_task(SHENANDOAH_RP_PS_ObjectSynchronizer_oops_do)) {
      ObjectSynchronizer::oops_do(strong_roots);
    }
  }

  // All threads execute the following. A specific chunk of buckets
  // from the StringTable are the individual tasks.
  if (weak_roots != NULL) {
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::StringTableRoots, worker_id);
    StringTable::possibly_parallel_oops_do(&_par_state_string, weak_roots);
  }
}

uint ShenandoahRootProcessor::n_workers() const {
  return _srs.n_threads();
}

ShenandoahRootEvacuator::ShenandoahRootEvacuator(ShenandoahHeap* heap, uint n_workers, ShenandoahPhaseTimings::Phase phase) :
  _evacuation_tasks(new SubTasksDone(SHENANDOAH_EVAC_NumElements)),
  _srs(n_workers),
  _phase(phase),
  _coderoots_cset_iterator(ShenandoahCodeRoots::cset_iterator())
{
  heap->phase_timings()->record_workers_start(_phase);
}

ShenandoahRootEvacuator::~ShenandoahRootEvacuator() {
  delete _evacuation_tasks;
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

  if (_evacuation_tasks->try_claim_task(SHENANDOAH_EVAC_jvmti_oops_do)) {
    ShenandoahForwardedIsAliveClosure is_alive;
    ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::JVMTIRoots, worker_id);
    JvmtiExport::weak_oops_do(&is_alive, oops);
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
