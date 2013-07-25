/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/sharedHeap.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/java.hpp"
#include "services/management.hpp"
#include "utilities/copy.hpp"
#include "utilities/workgroup.hpp"

SharedHeap* SharedHeap::_sh;

// The set of potentially parallel tasks in strong root scanning.
enum SH_process_strong_roots_tasks {
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
  _rem_set(NULL),
  _strong_roots_parity(0),
  _process_strong_tasks(new SubTasksDone(SH_PS_NumElements)),
  _workers(NULL)
{
  if (_process_strong_tasks == NULL || !_process_strong_tasks->valid()) {
    vm_exit_during_initialization("Failed necessary allocation.");
  }
  _sh = this;  // ch is static, should be set only once.
  if ((UseParNewGC ||
      (UseConcMarkSweepGC && CMSParallelRemarkEnabled) ||
       UseG1GC) &&
      ParallelGCThreads > 0) {
    _workers = new FlexibleWorkGang("Parallel GC Threads", ParallelGCThreads,
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

void SharedHeap::change_strong_roots_parity() {
  // Also set the new collection parity.
  assert(_strong_roots_parity >= 0 && _strong_roots_parity <= 2,
         "Not in range.");
  _strong_roots_parity++;
  if (_strong_roots_parity == 3) _strong_roots_parity = 1;
  assert(_strong_roots_parity >= 1 && _strong_roots_parity <= 2,
         "Not in range.");
}

SharedHeap::StrongRootsScope::StrongRootsScope(SharedHeap* outer, bool activate)
  : MarkScope(activate)
{
  if (_active) {
    outer->change_strong_roots_parity();
    // Zero the claimed high water mark in the StringTable
    StringTable::clear_parallel_claimed_index();
  }
}

SharedHeap::StrongRootsScope::~StrongRootsScope() {
  // nothing particular
}

void SharedHeap::process_strong_roots(bool activate_scope,
                                      bool is_scavenging,
                                      ScanningOption so,
                                      OopClosure* roots,
                                      CodeBlobClosure* code_roots,
                                      KlassClosure* klass_closure) {
  StrongRootsScope srs(this, activate_scope);

  // General strong roots.
  assert(_strong_roots_parity != 0, "must have called prologue code");
  // _n_termination for _process_strong_tasks should be set up stream
  // in a method not running in a GC worker.  Otherwise the GC worker
  // could be trying to change the termination condition while the task
  // is executing in another GC worker.
  if (!_process_strong_tasks->is_task_claimed(SH_PS_Universe_oops_do)) {
    Universe::oops_do(roots);
  }
  // Global (strong) JNI handles
  if (!_process_strong_tasks->is_task_claimed(SH_PS_JNIHandles_oops_do))
    JNIHandles::oops_do(roots);

  // All threads execute this; the individual threads are task groups.
  CLDToOopClosure roots_from_clds(roots);
  CLDToOopClosure* roots_from_clds_p = (is_scavenging ? NULL : &roots_from_clds);
  if (CollectedHeap::use_parallel_gc_threads()) {
    Threads::possibly_parallel_oops_do(roots, roots_from_clds_p, code_roots);
  } else {
    Threads::oops_do(roots, roots_from_clds_p, code_roots);
  }

  if (!_process_strong_tasks-> is_task_claimed(SH_PS_ObjectSynchronizer_oops_do))
    ObjectSynchronizer::oops_do(roots);
  if (!_process_strong_tasks->is_task_claimed(SH_PS_FlatProfiler_oops_do))
    FlatProfiler::oops_do(roots);
  if (!_process_strong_tasks->is_task_claimed(SH_PS_Management_oops_do))
    Management::oops_do(roots);
  if (!_process_strong_tasks->is_task_claimed(SH_PS_jvmti_oops_do))
    JvmtiExport::oops_do(roots);

  if (!_process_strong_tasks->is_task_claimed(SH_PS_SystemDictionary_oops_do)) {
    if (so & SO_AllClasses) {
      SystemDictionary::oops_do(roots);
    } else if (so & SO_SystemClasses) {
      SystemDictionary::always_strong_oops_do(roots);
    } else {
      fatal("We should always have selected either SO_AllClasses or SO_SystemClasses");
    }
  }

  if (!_process_strong_tasks->is_task_claimed(SH_PS_ClassLoaderDataGraph_oops_do)) {
    if (so & SO_AllClasses) {
      ClassLoaderDataGraph::oops_do(roots, klass_closure, !is_scavenging);
    } else if (so & SO_SystemClasses) {
      ClassLoaderDataGraph::always_strong_oops_do(roots, klass_closure, !is_scavenging);
    }
  }

  // All threads execute the following. A specific chunk of buckets
  // from the StringTable are the individual tasks.
  if (so & SO_Strings) {
    if (CollectedHeap::use_parallel_gc_threads()) {
      StringTable::possibly_parallel_oops_do(roots);
    } else {
      StringTable::oops_do(roots);
    }
  }

  if (!_process_strong_tasks->is_task_claimed(SH_PS_CodeCache_oops_do)) {
    if (so & SO_CodeCache) {
      assert(code_roots != NULL, "must supply closure for code cache");

      if (is_scavenging) {
        // We only visit parts of the CodeCache when scavenging.
        CodeCache::scavenge_root_nmethods_do(code_roots);
      } else {
        // CMSCollector uses this to do intermediate-strength collections.
        // We scan the entire code cache, since CodeCache::do_unloading is not called.
        CodeCache::blobs_do(code_roots);
      }
    }
    // Verify that the code cache contents are not subject to
    // movement by a scavenging collection.
    DEBUG_ONLY(CodeBlobToOopClosure assert_code_is_non_scavengable(&assert_is_non_scavengable_closure, /*do_marking=*/ false));
    DEBUG_ONLY(CodeCache::asserted_non_scavengable_nmethods_do(&assert_code_is_non_scavengable));
  }

  _process_strong_tasks->all_tasks_completed();
}

class AlwaysTrueClosure: public BoolObjectClosure {
public:
  bool do_object_b(oop p) { return true; }
};
static AlwaysTrueClosure always_true;

void SharedHeap::process_weak_roots(OopClosure* root_closure,
                                    CodeBlobClosure* code_roots) {
  // Global (weak) JNI handles
  JNIHandles::weak_oops_do(&always_true, root_closure);

  CodeCache::blobs_do(code_roots);
  StringTable::oops_do(root_closure);
}

void SharedHeap::set_barrier_set(BarrierSet* bs) {
  _barrier_set = bs;
  // Cached barrier set for fast access in oops
  oopDesc::set_bs(bs);
}

void SharedHeap::post_initialize() {
  ref_processing_init();
}

void SharedHeap::ref_processing_init() {}

// Some utilities.
void SharedHeap::print_size_transition(outputStream* out,
                                       size_t bytes_before,
                                       size_t bytes_after,
                                       size_t capacity) {
  out->print(" %d%s->%d%s(%d%s)",
             byte_size_in_proper_unit(bytes_before),
             proper_unit_for_byte_size(bytes_before),
             byte_size_in_proper_unit(bytes_after),
             proper_unit_for_byte_size(bytes_after),
             byte_size_in_proper_unit(capacity),
             proper_unit_for_byte_size(capacity));
}
