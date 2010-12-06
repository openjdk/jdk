/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/vm_operations_g1.hpp"
#include "gc_implementation/shared/isGCActiveMark.hpp"
#include "gc_implementation/g1/vm_operations_g1.hpp"
#include "runtime/interfaceSupport.hpp"

VM_G1CollectForAllocation::VM_G1CollectForAllocation(
                                                  unsigned int gc_count_before,
                                                  size_t word_size)
  : VM_G1OperationWithAllocRequest(gc_count_before, word_size) {
  guarantee(word_size > 0, "an allocation should always be requested");
}

void VM_G1CollectForAllocation::doit() {
  JvmtiGCForAllocationMarker jgcm;
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  _result = g1h->satisfy_failed_allocation(_word_size, &_pause_succeeded);
  assert(_result == NULL || _pause_succeeded,
         "if we get back a result, the pause should have succeeded");
}

void VM_G1CollectFull::doit() {
  JvmtiGCFullMarker jgcm;
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  GCCauseSetter x(g1h, _gc_cause);
  g1h->do_full_collection(false /* clear_all_soft_refs */);
}

VM_G1IncCollectionPause::VM_G1IncCollectionPause(
                                      unsigned int   gc_count_before,
                                      size_t         word_size,
                                      bool           should_initiate_conc_mark,
                                      double         target_pause_time_ms,
                                      GCCause::Cause gc_cause)
  : VM_G1OperationWithAllocRequest(gc_count_before, word_size),
    _should_initiate_conc_mark(should_initiate_conc_mark),
    _target_pause_time_ms(target_pause_time_ms),
    _full_collections_completed_before(0) {
  guarantee(target_pause_time_ms > 0.0,
            err_msg("target_pause_time_ms = %1.6lf should be positive",
                    target_pause_time_ms));
  guarantee(word_size == 0 || gc_cause == GCCause::_g1_inc_collection_pause,
            "we can only request an allocation if the GC cause is for "
            "an incremental GC pause");
  _gc_cause = gc_cause;
}

void VM_G1IncCollectionPause::doit() {
  JvmtiGCForAllocationMarker jgcm;
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  assert(!_should_initiate_conc_mark ||
  ((_gc_cause == GCCause::_gc_locker && GCLockerInvokesConcurrent) ||
   (_gc_cause == GCCause::_java_lang_system_gc && ExplicitGCInvokesConcurrent)),
         "only a GC locker or a System.gc() induced GC should start a cycle");

  if (_word_size > 0) {
    // An allocation has been requested. So, try to do that first.
    _result = g1h->attempt_allocation_at_safepoint(_word_size,
                                     false /* expect_null_cur_alloc_region */);
    if (_result != NULL) {
      // If we can successfully allocate before we actually do the
      // pause then we will consider this pause successful.
      _pause_succeeded = true;
      return;
    }
  }

  GCCauseSetter x(g1h, _gc_cause);
  if (_should_initiate_conc_mark) {
    // It's safer to read full_collections_completed() here, given
    // that noone else will be updating it concurrently. Since we'll
    // only need it if we're initiating a marking cycle, no point in
    // setting it earlier.
    _full_collections_completed_before = g1h->full_collections_completed();

    // At this point we are supposed to start a concurrent cycle. We
    // will do so if one is not already in progress.
    bool res = g1h->g1_policy()->force_initial_mark_if_outside_cycle();
  }

  _pause_succeeded =
    g1h->do_collection_pause_at_safepoint(_target_pause_time_ms);
  if (_pause_succeeded && _word_size > 0) {
    // An allocation had been requested.
    _result = g1h->attempt_allocation_at_safepoint(_word_size,
                                      true /* expect_null_cur_alloc_region */);
  } else {
    assert(_result == NULL, "invariant");
  }
}

void VM_G1IncCollectionPause::doit_epilogue() {
  VM_GC_Operation::doit_epilogue();

  // If the pause was initiated by a System.gc() and
  // +ExplicitGCInvokesConcurrent, we have to wait here for the cycle
  // that just started (or maybe one that was already in progress) to
  // finish.
  if (_gc_cause == GCCause::_java_lang_system_gc &&
      _should_initiate_conc_mark) {
    assert(ExplicitGCInvokesConcurrent,
           "the only way to be here is if ExplicitGCInvokesConcurrent is set");

    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    // In the doit() method we saved g1h->full_collections_completed()
    // in the _full_collections_completed_before field. We have to
    // wait until we observe that g1h->full_collections_completed()
    // has increased by at least one. This can happen if a) we started
    // a cycle and it completes, b) a cycle already in progress
    // completes, or c) a Full GC happens.

    // If the condition has already been reached, there's no point in
    // actually taking the lock and doing the wait.
    if (g1h->full_collections_completed() <=
                                          _full_collections_completed_before) {
      // The following is largely copied from CMS

      Thread* thr = Thread::current();
      assert(thr->is_Java_thread(), "invariant");
      JavaThread* jt = (JavaThread*)thr;
      ThreadToNativeFromVM native(jt);

      MutexLockerEx x(FullGCCount_lock, Mutex::_no_safepoint_check_flag);
      while (g1h->full_collections_completed() <=
                                          _full_collections_completed_before) {
        FullGCCount_lock->wait(Mutex::_no_safepoint_check_flag);
      }
    }
  }
}

void VM_CGC_Operation::doit() {
  gclog_or_tty->date_stamp(PrintGC && PrintGCDateStamps);
  TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
  TraceTime t(_printGCMessage, PrintGC, true, gclog_or_tty);
  SharedHeap* sh = SharedHeap::heap();
  // This could go away if CollectedHeap gave access to _gc_is_active...
  if (sh != NULL) {
    IsGCActiveMark x;
    _cl->do_void();
  } else {
    _cl->do_void();
  }
}

bool VM_CGC_Operation::doit_prologue() {
  Heap_lock->lock();
  SharedHeap::heap()->_thread_holds_heap_lock_for_gc = true;
  return true;
}

void VM_CGC_Operation::doit_epilogue() {
  SharedHeap::heap()->_thread_holds_heap_lock_for_gc = false;
  Heap_lock->unlock();
}
