/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_concurrentZFThread.cpp.incl"

// ======= Concurrent Zero-Fill Thread ========

// The CM thread is created when the G1 garbage collector is used

int ConcurrentZFThread::_region_allocs = 0;
int ConcurrentZFThread::_sync_zfs = 0;
int ConcurrentZFThread::_zf_waits = 0;
int ConcurrentZFThread::_regions_filled = 0;

ConcurrentZFThread::ConcurrentZFThread() :
  ConcurrentGCThread()
{
  create_and_start();
}

void ConcurrentZFThread::wait_for_ZF_completed(HeapRegion* hr) {
  assert(ZF_mon->owned_by_self(), "Precondition.");
  note_zf_wait();
  while (hr->zero_fill_state() == HeapRegion::ZeroFilling) {
    ZF_mon->wait(Mutex::_no_safepoint_check_flag);
  }
}

void ConcurrentZFThread::processHeapRegion(HeapRegion* hr) {
  assert(!Universe::heap()->is_gc_active(),
         "This should not happen during GC.");
  assert(hr != NULL, "Precondition");
  // These are unlocked reads, but if this test is successful, then no
  // other thread will attempt this zero filling.  Only a GC thread can
  // modify the ZF state of a region whose state is zero-filling, and this
  // should only happen while the ZF thread is locking out GC.
  if (hr->zero_fill_state() == HeapRegion::ZeroFilling
      && hr->zero_filler() == Thread::current()) {
    assert(hr->top() == hr->bottom(), "better be empty!");
    assert(!hr->isHumongous(), "Only free regions on unclean list.");
    Copy::fill_to_words(hr->bottom(), hr->capacity()/HeapWordSize);
    note_region_filled();
  }
}

void ConcurrentZFThread::run() {
  initialize_in_thread();
  Thread* thr_self = Thread::current();
  _vtime_start = os::elapsedVTime();
  wait_for_universe_init();

  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  _sts.join();
  while (!_should_terminate) {
    _sts.leave();

    {
      MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);

      // This local variable will hold a region being zero-filled.  This
      // region will neither be on the unclean or zero-filled lists, and
      // will not be available for allocation; thus, we might have an
      // allocation fail, causing a full GC, because of this, but this is a
      // price we will pay.  (In future, we might want to make the fact
      // that there's a region being zero-filled apparent to the G1 heap,
      // which could then wait for it in this extreme case...)
      HeapRegion* to_fill;

      while (!g1->should_zf()
             || (to_fill = g1->pop_unclean_region_list_locked()) == NULL)
        ZF_mon->wait(Mutex::_no_safepoint_check_flag);
      while (to_fill->zero_fill_state() == HeapRegion::ZeroFilling)
        ZF_mon->wait(Mutex::_no_safepoint_check_flag);

      // So now to_fill is non-NULL and is not ZeroFilling.  It might be
      // Allocated or ZeroFilled.  (The latter could happen if this thread
      // starts the zero-filling of a region, but a GC intervenes and
      // pushes new regions needing on the front of the filling on the
      // front of the list.)

      switch (to_fill->zero_fill_state()) {
      case HeapRegion::Allocated:
        to_fill = NULL;
        break;

      case HeapRegion::NotZeroFilled:
        to_fill->set_zero_fill_in_progress(thr_self);

        ZF_mon->unlock();
        _sts.join();
        processHeapRegion(to_fill);
        _sts.leave();
        ZF_mon->lock_without_safepoint_check();

        if (to_fill->zero_fill_state() == HeapRegion::ZeroFilling
            && to_fill->zero_filler() == thr_self) {
          to_fill->set_zero_fill_complete();
          (void)g1->put_free_region_on_list_locked(to_fill);
        }
        break;

      case HeapRegion::ZeroFilled:
        (void)g1->put_free_region_on_list_locked(to_fill);
        break;

      case HeapRegion::ZeroFilling:
        ShouldNotReachHere();
        break;
      }
    }
    _vtime_accum = (os::elapsedVTime() - _vtime_start);
    _sts.join();
  }
  _sts.leave();

  assert(_should_terminate, "just checking");
  terminate();
}

bool ConcurrentZFThread::offer_yield() {
  if (_sts.should_yield()) {
    _sts.yield("Concurrent ZF");
    return true;
  } else {
    return false;
  }
}

void ConcurrentZFThread::stop() {
  // it is ok to take late safepoints here, if needed
  MutexLockerEx mu(Terminator_lock);
  _should_terminate = true;
  while (!_has_terminated) {
    Terminator_lock->wait();
  }
}

void ConcurrentZFThread::print() const {
  print_on(tty);
}

void ConcurrentZFThread::print_on(outputStream* st) const {
  st->print("\"G1 Concurrent Zero-Fill Thread\" ");
  Thread::print_on(st);
  st->cr();
}


double ConcurrentZFThread::_vtime_accum;

void ConcurrentZFThread::print_summary_info() {
  gclog_or_tty->print("\nConcurrent Zero-Filling:\n");
  gclog_or_tty->print("  Filled %d regions, used %5.2fs.\n",
                      _regions_filled,
                      vtime_accum());
  gclog_or_tty->print("  Of %d region allocs, %d (%5.2f%%) required sync ZF,\n",
                      _region_allocs, _sync_zfs,
                      (_region_allocs > 0 ?
                       (float)_sync_zfs/(float)_region_allocs*100.0 :
                       0.0));
  gclog_or_tty->print("     and %d (%5.2f%%) required a ZF wait.\n",
                      _zf_waits,
                      (_region_allocs > 0 ?
                       (float)_zf_waits/(float)_region_allocs*100.0 :
                       0.0));

}
