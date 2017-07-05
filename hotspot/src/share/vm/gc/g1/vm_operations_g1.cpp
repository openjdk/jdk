/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/concurrentMarkThread.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/g1/g1Log.hpp"
#include "gc/g1/vm_operations_g1.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "runtime/interfaceSupport.hpp"

VM_G1CollectForAllocation::VM_G1CollectForAllocation(uint gc_count_before,
                                                     size_t word_size)
  : VM_G1OperationWithAllocRequest(gc_count_before, word_size,
                                   GCCause::_allocation_failure) {
  guarantee(word_size != 0, "An allocation should always be requested with this operation.");
}

void VM_G1CollectForAllocation::doit() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  GCCauseSetter x(g1h, _gc_cause);

  _result = g1h->satisfy_failed_allocation(_word_size, allocation_context(), &_pause_succeeded);
  assert(_result == NULL || _pause_succeeded,
         "if we get back a result, the pause should have succeeded");
}

void VM_G1CollectFull::doit() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  GCCauseSetter x(g1h, _gc_cause);
  g1h->do_full_collection(false /* clear_all_soft_refs */);
}

VM_G1IncCollectionPause::VM_G1IncCollectionPause(uint           gc_count_before,
                                                 size_t         word_size,
                                                 bool           should_initiate_conc_mark,
                                                 double         target_pause_time_ms,
                                                 GCCause::Cause gc_cause)
  : VM_G1OperationWithAllocRequest(gc_count_before, word_size, gc_cause),
    _should_initiate_conc_mark(should_initiate_conc_mark),
    _target_pause_time_ms(target_pause_time_ms),
    _should_retry_gc(false),
    _old_marking_cycles_completed_before(0) {
  guarantee(target_pause_time_ms > 0.0,
            "target_pause_time_ms = %1.6lf should be positive",
            target_pause_time_ms);
  _gc_cause = gc_cause;
}

bool VM_G1IncCollectionPause::doit_prologue() {
  bool res = VM_G1OperationWithAllocRequest::doit_prologue();
  if (!res) {
    if (_should_initiate_conc_mark) {
      // The prologue can fail for a couple of reasons. The first is that another GC
      // got scheduled and prevented the scheduling of the initial mark GC. The
      // second is that the GC locker may be active and the heap can't be expanded.
      // In both cases we want to retry the GC so that the initial mark pause is
      // actually scheduled. In the second case, however, we should stall until
      // until the GC locker is no longer active and then retry the initial mark GC.
      _should_retry_gc = true;
    }
  }
  return res;
}

void VM_G1IncCollectionPause::doit() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  assert(!_should_initiate_conc_mark || g1h->should_do_concurrent_full_gc(_gc_cause),
      "only a GC locker, a System.gc(), stats update, whitebox, or a hum allocation induced GC should start a cycle");

  if (_word_size > 0) {
    // An allocation has been requested. So, try to do that first.
    _result = g1h->attempt_allocation_at_safepoint(_word_size,
                                                   allocation_context(),
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
    // It's safer to read old_marking_cycles_completed() here, given
    // that noone else will be updating it concurrently. Since we'll
    // only need it if we're initiating a marking cycle, no point in
    // setting it earlier.
    _old_marking_cycles_completed_before = g1h->old_marking_cycles_completed();

    // At this point we are supposed to start a concurrent cycle. We
    // will do so if one is not already in progress.
    bool res = g1h->g1_policy()->force_initial_mark_if_outside_cycle(_gc_cause);

    // The above routine returns true if we were able to force the
    // next GC pause to be an initial mark; it returns false if a
    // marking cycle is already in progress.
    //
    // If a marking cycle is already in progress just return and skip the
    // pause below - if the reason for requesting this initial mark pause
    // was due to a System.gc() then the requesting thread should block in
    // doit_epilogue() until the marking cycle is complete.
    //
    // If this initial mark pause was requested as part of a humongous
    // allocation then we know that the marking cycle must just have
    // been started by another thread (possibly also allocating a humongous
    // object) as there was no active marking cycle when the requesting
    // thread checked before calling collect() in
    // attempt_allocation_humongous(). Retrying the GC, in this case,
    // will cause the requesting thread to spin inside collect() until the
    // just started marking cycle is complete - which may be a while. So
    // we do NOT retry the GC.
    if (!res) {
      assert(_word_size == 0, "Concurrent Full GC/Humongous Object IM shouldn't be allocating");
      if (_gc_cause != GCCause::_g1_humongous_allocation) {
        _should_retry_gc = true;
      }
      return;
    }
  }

  _pause_succeeded =
    g1h->do_collection_pause_at_safepoint(_target_pause_time_ms);
  if (_pause_succeeded && _word_size > 0) {
    // An allocation had been requested.
    _result = g1h->attempt_allocation_at_safepoint(_word_size,
                                                   allocation_context(),
                                                   true /* expect_null_cur_alloc_region */);
  } else {
    assert(_result == NULL, "invariant");
    if (!_pause_succeeded) {
      // Another possible reason reason for the pause to not be successful
      // is that, again, the GC locker is active (and has become active
      // since the prologue was executed). In this case we should retry
      // the pause after waiting for the GC locker to become inactive.
      _should_retry_gc = true;
    }
  }
}

void VM_G1IncCollectionPause::doit_epilogue() {
  VM_G1OperationWithAllocRequest::doit_epilogue();

  // If the pause was initiated by a System.gc() and
  // +ExplicitGCInvokesConcurrent, we have to wait here for the cycle
  // that just started (or maybe one that was already in progress) to
  // finish.
  if (GCCause::is_user_requested_gc(_gc_cause) &&
      _should_initiate_conc_mark) {
    assert(ExplicitGCInvokesConcurrent,
           "the only way to be here is if ExplicitGCInvokesConcurrent is set");

    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    // In the doit() method we saved g1h->old_marking_cycles_completed()
    // in the _old_marking_cycles_completed_before field. We have to
    // wait until we observe that g1h->old_marking_cycles_completed()
    // has increased by at least one. This can happen if a) we started
    // a cycle and it completes, b) a cycle already in progress
    // completes, or c) a Full GC happens.

    // If the condition has already been reached, there's no point in
    // actually taking the lock and doing the wait.
    if (g1h->old_marking_cycles_completed() <=
                                          _old_marking_cycles_completed_before) {
      // The following is largely copied from CMS

      Thread* thr = Thread::current();
      assert(thr->is_Java_thread(), "invariant");
      JavaThread* jt = (JavaThread*)thr;
      ThreadToNativeFromVM native(jt);

      MutexLockerEx x(FullGCCount_lock, Mutex::_no_safepoint_check_flag);
      while (g1h->old_marking_cycles_completed() <=
                                          _old_marking_cycles_completed_before) {
        FullGCCount_lock->wait(Mutex::_no_safepoint_check_flag);
      }
    }
  }
}

void VM_CGC_Operation::acquire_pending_list_lock() {
  assert(_needs_pll, "don't call this otherwise");
  // The caller may block while communicating
  // with the SLT thread in order to acquire/release the PLL.
  SurrogateLockerThread* slt = ConcurrentMarkThread::slt();
  if (slt != NULL) {
    slt->manipulatePLL(SurrogateLockerThread::acquirePLL);
  } else {
    SurrogateLockerThread::report_missing_slt();
  }
}

void VM_CGC_Operation::release_and_notify_pending_list_lock() {
  assert(_needs_pll, "don't call this otherwise");
  // The caller may block while communicating
  // with the SLT thread in order to acquire/release the PLL.
  ConcurrentMarkThread::slt()->
    manipulatePLL(SurrogateLockerThread::releaseAndNotifyPLL);
}

void VM_CGC_Operation::doit() {
  TraceCPUTime tcpu(G1Log::finer(), true, gclog_or_tty);
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  GCIdMark gc_id_mark(_gc_id);
  GCTraceTime t(_printGCMessage, G1Log::fine(), true, g1h->gc_timer_cm());
  IsGCActiveMark x;
  _cl->do_void();
}

bool VM_CGC_Operation::doit_prologue() {
  // Note the relative order of the locks must match that in
  // VM_GC_Operation::doit_prologue() or deadlocks can occur
  if (_needs_pll) {
    acquire_pending_list_lock();
  }

  Heap_lock->lock();
  return true;
}

void VM_CGC_Operation::doit_epilogue() {
  // Note the relative order of the unlocks must match that in
  // VM_GC_Operation::doit_epilogue()
  Heap_lock->unlock();
  if (_needs_pll) {
    release_and_notify_pending_list_lock();
  }
}
