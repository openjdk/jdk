/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/sharedHeap.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/java.hpp"
#include "services/management.hpp"
#include "utilities/copy.hpp"
#include "utilities/workgroup.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

SharedHeap* SharedHeap::_sh;

// The set of potentially parallel tasks in root scanning.
enum SH_process_roots_tasks {
  SH_PS_Universe_oops_do,
  SH_PS_JNIHandles_oops_do,
  SH_PS_ObjectSynchronizer_oops_do,
  SH_PS_FlatProfiler_oops_do,
  SH_PS_Management_oops_do,
  SH_PS_SystemDictionary_oops_do,
  SH_PS_ClassLoaderDataGraph_oops_do,
  SH_PS_jvmti_oops_do,
  SH_PS_CodeCache_oops_do,
  // Leave this one last.
  SH_PS_NumElements
};

SharedHeap::SharedHeap(CollectorPolicy* policy_) :
  CollectedHeap(),
  _collector_policy(policy_),
  _strong_roots_scope(NULL),
  _strong_roots_parity(0),
  _process_strong_tasks(new SubTasksDone(SH_PS_NumElements)),
  _workers(NULL)
{
  if (_process_strong_tasks == NULL || !_process_strong_tasks->valid()) {
    vm_exit_during_initialization("Failed necessary allocation.");
  }
  _sh = this;  // ch is static, should be set only once.
  if (UseConcMarkSweepGC || UseG1GC) {
    _workers = new FlexibleWorkGang("GC Thread", ParallelGCThreads,
                            /* are_GC_task_threads */true,
                            /* are_ConcurrentGC_threads */false);
    if (_workers == NULL) {
      vm_exit_during_initialization("Failed necessary allocation.");
    } else {
      _workers->initialize_workers();
    }
  }
}

int SharedHeap::n_termination() {
  return _process_strong_tasks->n_threads();
}

void SharedHeap::set_n_termination(int t) {
  _process_strong_tasks->set_n_threads(t);
}

bool SharedHeap::heap_lock_held_for_gc() {
  Thread* t = Thread::current();
  return    Heap_lock->owned_by_self()
         || (   (t->is_GC_task_thread() ||  t->is_VM_thread())
             && _thread_holds_heap_lock_for_gc);
}

void SharedHeap::set_par_threads(uint t) {
  assert(t == 0 || !UseSerialGC, "Cannot have parallel threads");
  _n_par_threads = t;
  _process_strong_tasks->set_n_threads(t);
}

#ifdef ASSERT
class AssertNonScavengableClosure: public OopClosure {
public:
  virtual void do_oop(oop* p) {
    assert(!Universe::heap()->is_in_partial_collection(*p),
      "Referent should not be scavengable.");  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};
static AssertNonScavengableClosure assert_is_non_scavengable_closure;
#endif

SharedHeap::StrongRootsScope* SharedHeap::active_strong_roots_scope() const {
  return _strong_roots_scope;
}
void SharedHeap::register_strong_roots_scope(SharedHeap::StrongRootsScope* scope) {
  assert(_strong_roots_scope == NULL, "Should only have one StrongRootsScope active");
  assert(scope != NULL, "Illegal argument");
  _strong_roots_scope = scope;
}
void SharedHeap::unregister_strong_roots_scope(SharedHeap::StrongRootsScope* scope) {
  assert(_strong_roots_scope == scope, "Wrong scope unregistered");
  _strong_roots_scope = NULL;
}

void SharedHeap::change_strong_roots_parity() {
  // Also set the new collection parity.
  assert(_strong_roots_parity >= 0 && _strong_roots_parity <= 2,
         "Not in range.");
  _strong_roots_parity++;
  if (_strong_roots_parity == 3) _strong_roots_parity = 1;
  assert(_strong_roots_parity >= 1 && _strong_roots_parity <= 2,
         "Not in range.");
}

SharedHeap::StrongRootsScope::StrongRootsScope(SharedHeap* heap, bool activate)
  : MarkScope(activate), _sh(heap), _n_workers_done_with_threads(0)
{
  if (_active) {
    _sh->register_strong_roots_scope(this);
    _sh->change_strong_roots_parity();
    // Zero the claimed high water mark in the StringTable
    StringTable::clear_parallel_claimed_index();
  }
}

SharedHeap::StrongRootsScope::~StrongRootsScope() {
  if (_active) {
    _sh->unregister_strong_roots_scope(this);
  }
}

Monitor* SharedHeap::StrongRootsScope::_lock = new Monitor(Mutex::leaf, "StrongRootsScope lock", false, Monitor::_safepoint_check_never);

void SharedHeap::StrongRootsScope::mark_worker_done_with_threads(uint n_workers) {
  // The Thread work barrier is only needed by G1 Class Unloading.
  // No need to use the barrier if this is single-threaded code.
  if (UseG1GC && ClassUnloadingWithConcurrentMark && n_workers > 0) {
    uint new_value = (uint)Atomic::add(1, &_n_workers_done_with_threads);
    if (new_value == n_workers) {
      // This thread is last. Notify the others.
      MonitorLockerEx ml(_lock, Mutex::_no_safepoint_check_flag);
      _lock->notify_all();
    }
  }
}

void SharedHeap::StrongRootsScope::wait_until_all_workers_done_with_threads(uint n_workers) {
  assert(UseG1GC,                          "Currently only used by G1");
  assert(ClassUnloadingWithConcurrentMark, "Currently only needed when doing G1 Class Unloading");

  // No need to use the barrier if this is single-threaded code.
  if (n_workers > 0 && (uint)_n_workers_done_with_threads != n_workers) {
    MonitorLockerEx ml(_lock, Mutex::_no_safepoint_check_flag);
    while ((uint)_n_workers_done_with_threads != n_workers) {
      _lock->wait(Mutex::_no_safepoint_check_flag, 0, false);
    }
  }
}

void SharedHeap::process_roots(bool activate_scope,
                               ScanningOption so,
                               OopClosure* strong_roots,
                               OopClosure* weak_roots,
                               CLDClosure* strong_cld_closure,
                               CLDClosure* weak_cld_closure,
                               CodeBlobClosure* code_roots) {
  StrongRootsScope srs(this, activate_scope);

  // General roots.
  assert(_strong_roots_parity != 0, "must have called prologue code");
  assert(code_roots != NULL, "code root closure should always be set");
  // _n_termination for _process_strong_tasks should be set up stream
  // in a method not running in a GC worker.  Otherwise the GC worker
  // could be trying to change the termination condition while the task
  // is executing in another GC worker.

  // Iterating over the CLDG and the Threads are done early to allow G1 to
  // first process the strong CLDs and nmethods and then, after a barrier,
  // let the thread process the weak CLDs and nmethods.

  if (!_process_strong_tasks->is_task_claimed(SH_PS_ClassLoaderDataGraph_oops_do)) {
    ClassLoaderDataGraph::roots_cld_do(strong_cld_closure, weak_cld_closure);
  }

  // Some CLDs contained in the thread frames should be considered strong.
  // Don't process them if they will be processed during the ClassLoaderDataGraph phase.
  CLDClosure* roots_from_clds_p = (strong_cld_closure != weak_cld_closure) ? strong_cld_closure : NULL;
  // Only process code roots from thread stacks if we aren't visiting the entire CodeCache anyway
  CodeBlobClosure* roots_from_code_p = (so & SO_AllCodeCache) ? NULL : code_roots;

  Threads::possibly_parallel_oops_do(strong_roots, roots_from_clds_p, roots_from_code_p);

  // This is the point where this worker thread will not find more strong CLDs/nmethods.
  // Report this so G1 can synchronize the strong and weak CLDs/nmethods processing.
  active_strong_roots_scope()->mark_worker_done_with_threads(n_par_threads());

  if (!_process_strong_tasks->is_task_claimed(SH_PS_Universe_oops_do)) {
    Universe::oops_do(strong_roots);
  }
  // Global (strong) JNI handles
  if (!_process_strong_tasks->is_task_claimed(SH_PS_JNIHandles_oops_do))
    JNIHandles::oops_do(strong_roots);

  if (!_process_strong_tasks-> is_task_claimed(SH_PS_ObjectSynchronizer_oops_do))
    ObjectSynchronizer::oops_do(strong_roots);
  if (!_process_strong_tasks->is_task_claimed(SH_PS_FlatProfiler_oops_do))
    FlatProfiler::oops_do(strong_roots);
  if (!_process_strong_tasks->is_task_claimed(SH_PS_Management_oops_do))
    Management::oops_do(strong_roots);
  if (!_process_strong_tasks->is_task_claimed(SH_PS_jvmti_oops_do))
    JvmtiExport::oops_do(strong_roots);

  if (!_process_strong_tasks->is_task_claimed(SH_PS_SystemDictionary_oops_do)) {
    SystemDictionary::roots_oops_do(strong_roots, weak_roots);
  }

  // All threads execute the following. A specific chunk of buckets
  // from the StringTable are the individual tasks.
  if (weak_roots != NULL) {
    if (CollectedHeap::use_parallel_gc_threads()) {
      StringTable::possibly_parallel_oops_do(weak_roots);
    } else {
      StringTable::oops_do(weak_roots);
    }
  }

  if (!_process_strong_tasks->is_task_claimed(SH_PS_CodeCache_oops_do)) {
    if (so & SO_ScavengeCodeCache) {
      assert(code_roots != NULL, "must supply closure for code cache");

      // We only visit parts of the CodeCache when scavenging.
      CodeCache::scavenge_root_nmethods_do(code_roots);
    }
    if (so & SO_AllCodeCache) {
      assert(code_roots != NULL, "must supply closure for code cache");

      // CMSCollector uses this to do intermediate-strength collections.
      // We scan the entire code cache, since CodeCache::do_unloading is not called.
      CodeCache::blobs_do(code_roots);
    }
    // Verify that the code cache contents are not subject to
    // movement by a scavenging collection.
    DEBUG_ONLY(CodeBlobToOopClosure assert_code_is_non_scavengable(&assert_is_non_scavengable_closure, !CodeBlobToOopClosure::FixRelocations));
    DEBUG_ONLY(CodeCache::asserted_non_scavengable_nmethods_do(&assert_code_is_non_scavengable));
  }

  _process_strong_tasks->all_tasks_completed();
}

void SharedHeap::process_all_roots(bool activate_scope,
                                   ScanningOption so,
                                   OopClosure* roots,
                                   CLDClosure* cld_closure,
                                   CodeBlobClosure* code_closure) {
  process_roots(activate_scope, so,
                roots, roots,
                cld_closure, cld_closure,
                code_closure);
}

void SharedHeap::process_strong_roots(bool activate_scope,
                                      ScanningOption so,
                                      OopClosure* roots,
                                      CLDClosure* cld_closure,
                                      CodeBlobClosure* code_closure) {
  process_roots(activate_scope, so,
                roots, NULL,
                cld_closure, NULL,
                code_closure);
}


class AlwaysTrueClosure: public BoolObjectClosure {
public:
  bool do_object_b(oop p) { return true; }
};
static AlwaysTrueClosure always_true;

void SharedHeap::process_weak_roots(OopClosure* root_closure) {
  // Global (weak) JNI handles
  JNIHandles::weak_oops_do(&always_true, root_closure);
}

void SharedHeap::set_barrier_set(BarrierSet* bs) {
  _barrier_set = bs;
  // Cached barrier set for fast access in oops
  oopDesc::set_bs(bs);
}

void SharedHeap::post_initialize() {
  CollectedHeap::post_initialize();
  ref_processing_init();
}

void SharedHeap::ref_processing_init() {}

// Some utilities.
void SharedHeap::print_size_transition(outputStream* out,
                                       size_t bytes_before,
                                       size_t bytes_after,
                                       size_t capacity) {
  out->print(" " SIZE_FORMAT "%s->" SIZE_FORMAT "%s(" SIZE_FORMAT "%s)",
             byte_size_in_proper_unit(bytes_before),
             proper_unit_for_byte_size(bytes_before),
             byte_size_in_proper_unit(bytes_after),
             proper_unit_for_byte_size(bytes_after),
             byte_size_in_proper_unit(capacity),
             proper_unit_for_byte_size(capacity));
}
