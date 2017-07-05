/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_sharedHeap.cpp.incl"

SharedHeap* SharedHeap::_sh;

// The set of potentially parallel tasks in strong root scanning.
enum SH_process_strong_roots_tasks {
  SH_PS_Universe_oops_do,
  SH_PS_JNIHandles_oops_do,
  SH_PS_ObjectSynchronizer_oops_do,
  SH_PS_FlatProfiler_oops_do,
  SH_PS_Management_oops_do,
  SH_PS_SystemDictionary_oops_do,
  SH_PS_jvmti_oops_do,
  SH_PS_vmSymbols_oops_do,
  SH_PS_SymbolTable_oops_do,
  SH_PS_StringTable_oops_do,
  SH_PS_CodeCache_oops_do,
  // Leave this one last.
  SH_PS_NumElements
};

SharedHeap::SharedHeap(CollectorPolicy* policy_) :
  CollectedHeap(),
  _collector_policy(policy_),
  _perm_gen(NULL), _rem_set(NULL),
  _strong_roots_parity(0),
  _process_strong_tasks(new SubTasksDone(SH_PS_NumElements)),
  _workers(NULL), _n_par_threads(0)
{
  if (_process_strong_tasks == NULL || !_process_strong_tasks->valid()) {
    vm_exit_during_initialization("Failed necessary allocation.");
  }
  _sh = this;  // ch is static, should be set only once.
  if ((UseParNewGC ||
      (UseConcMarkSweepGC && CMSParallelRemarkEnabled) ||
       UseG1GC) &&
      ParallelGCThreads > 0) {
    _workers = new WorkGang("Parallel GC Threads", ParallelGCThreads,
                            /* are_GC_task_threads */true,
                            /* are_ConcurrentGC_threads */false);
    if (_workers == NULL) {
      vm_exit_during_initialization("Failed necessary allocation.");
    }
  }
}

bool SharedHeap::heap_lock_held_for_gc() {
  Thread* t = Thread::current();
  return    Heap_lock->owned_by_self()
         || (   (t->is_GC_task_thread() ||  t->is_VM_thread())
             && _thread_holds_heap_lock_for_gc);
}

void SharedHeap::set_par_threads(int t) {
  _n_par_threads = t;
  _process_strong_tasks->set_par_threads(t);
}

class AssertIsPermClosure: public OopClosure {
public:
  virtual void do_oop(oop* p) {
    assert((*p) == NULL || (*p)->is_perm(), "Referent should be perm.");
  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};
static AssertIsPermClosure assert_is_perm_closure;

void SharedHeap::change_strong_roots_parity() {
  // Also set the new collection parity.
  assert(_strong_roots_parity >= 0 && _strong_roots_parity <= 2,
         "Not in range.");
  _strong_roots_parity++;
  if (_strong_roots_parity == 3) _strong_roots_parity = 1;
  assert(_strong_roots_parity >= 1 && _strong_roots_parity <= 2,
         "Not in range.");
}

void SharedHeap::process_strong_roots(bool collecting_perm_gen,
                                      ScanningOption so,
                                      OopClosure* roots,
                                      OopsInGenClosure* perm_blk) {
  // General strong roots.
  if (n_par_threads() == 0) change_strong_roots_parity();
  if (!_process_strong_tasks->is_task_claimed(SH_PS_Universe_oops_do)) {
    Universe::oops_do(roots);
    ReferenceProcessor::oops_do(roots);
    // Consider perm-gen discovered lists to be strong.
    perm_gen()->ref_processor()->weak_oops_do(roots);
  }
  // Global (strong) JNI handles
  if (!_process_strong_tasks->is_task_claimed(SH_PS_JNIHandles_oops_do))
    JNIHandles::oops_do(roots);
  // All threads execute this; the individual threads are task groups.
  if (ParallelGCThreads > 0) {
    Threads::possibly_parallel_oops_do(roots);
  } else {
    Threads::oops_do(roots);
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
    } else
      if (so & SO_SystemClasses) {
        SystemDictionary::always_strong_oops_do(roots);
      }
  }

  if (!_process_strong_tasks->is_task_claimed(SH_PS_SymbolTable_oops_do)) {
    if (so & SO_Symbols) {
      SymbolTable::oops_do(roots);
    }
    // Verify if the symbol table contents are in the perm gen
    NOT_PRODUCT(SymbolTable::oops_do(&assert_is_perm_closure));
  }

  if (!_process_strong_tasks->is_task_claimed(SH_PS_StringTable_oops_do)) {
     if (so & SO_Strings) {
       StringTable::oops_do(roots);
     }
    // Verify if the string table contents are in the perm gen
    NOT_PRODUCT(StringTable::oops_do(&assert_is_perm_closure));
  }

  if (!_process_strong_tasks->is_task_claimed(SH_PS_CodeCache_oops_do)) {
     if (so & SO_CodeCache) {
       CodeCache::oops_do(roots);
     }
    // Verify if the code cache contents are in the perm gen
    NOT_PRODUCT(CodeCache::oops_do(&assert_is_perm_closure));
  }

  // Roots that should point only into permanent generation.
  {
    OopClosure* blk = NULL;
    if (collecting_perm_gen) {
      blk = roots;
    } else {
      debug_only(blk = &assert_is_perm_closure);
    }
    if (blk != NULL) {
      if (!_process_strong_tasks->is_task_claimed(SH_PS_vmSymbols_oops_do))
        vmSymbols::oops_do(blk);
    }
  }

  if (!collecting_perm_gen) {
    // All threads perform this; coordination is handled internally.

    rem_set()->younger_refs_iterate(perm_gen(), perm_blk);
  }
  _process_strong_tasks->all_tasks_completed();
}

class AlwaysTrueClosure: public BoolObjectClosure {
public:
  void do_object(oop p) { ShouldNotReachHere(); }
  bool do_object_b(oop p) { return true; }
};
static AlwaysTrueClosure always_true;

class SkipAdjustingSharedStrings: public OopClosure {
  OopClosure* _clo;
public:
  SkipAdjustingSharedStrings(OopClosure* clo) : _clo(clo) {}

  virtual void do_oop(oop* p) {
    oop o = (*p);
    if (!o->is_shared_readwrite()) {
      _clo->do_oop(p);
    }
  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};

// Unmarked shared Strings in the StringTable (which got there due to
// being in the constant pools of as-yet unloaded shared classes) were
// not marked and therefore did not have their mark words preserved.
// These entries are also deliberately not purged from the string
// table during unloading of unmarked strings. If an identity hash
// code was computed for any of these objects, it will not have been
// cleared to zero during the forwarding process or by the
// RecursiveAdjustSharedObjectClosure, and will be confused by the
// adjusting process as a forwarding pointer. We need to skip
// forwarding StringTable entries which contain unmarked shared
// Strings. Actually, since shared strings won't be moving, we can
// just skip adjusting any shared entries in the string table.

void SharedHeap::process_weak_roots(OopClosure* root_closure,
                                    OopClosure* non_root_closure) {
  // Global (weak) JNI handles
  JNIHandles::weak_oops_do(&always_true, root_closure);

  CodeCache::oops_do(non_root_closure);
  SymbolTable::oops_do(root_closure);
  if (UseSharedSpaces && !DumpSharedSpaces) {
    SkipAdjustingSharedStrings skip_closure(root_closure);
    StringTable::oops_do(&skip_closure);
  } else {
    StringTable::oops_do(root_closure);
  }
}

void SharedHeap::set_barrier_set(BarrierSet* bs) {
  _barrier_set = bs;
  // Cached barrier set for fast access in oops
  oopDesc::set_bs(bs);
}

void SharedHeap::post_initialize() {
  ref_processing_init();
}

void SharedHeap::ref_processing_init() {
  perm_gen()->ref_processor_init();
}

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
