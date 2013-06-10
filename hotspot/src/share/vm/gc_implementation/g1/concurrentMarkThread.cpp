/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/concurrentMarkThread.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1Log.hpp"
#include "gc_implementation/g1/g1MMUTracker.hpp"
#include "gc_implementation/g1/vm_operations_g1.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/vmThread.hpp"

// ======= Concurrent Mark Thread ========

// The CM thread is created when the G1 garbage collector is used

SurrogateLockerThread*
     ConcurrentMarkThread::_slt = NULL;

ConcurrentMarkThread::ConcurrentMarkThread(ConcurrentMark* cm) :
  ConcurrentGCThread(),
  _cm(cm),
  _started(false),
  _in_progress(false),
  _vtime_accum(0.0),
  _vtime_mark_accum(0.0) {
  create_and_start();
}

class CMCheckpointRootsFinalClosure: public VoidClosure {

  ConcurrentMark* _cm;
public:

  CMCheckpointRootsFinalClosure(ConcurrentMark* cm) :
    _cm(cm) {}

  void do_void(){
    _cm->checkpointRootsFinal(false); // !clear_all_soft_refs
  }
};

class CMCleanUp: public VoidClosure {
  ConcurrentMark* _cm;
public:

  CMCleanUp(ConcurrentMark* cm) :
    _cm(cm) {}

  void do_void(){
    _cm->cleanup();
  }
};



void ConcurrentMarkThread::run() {
  initialize_in_thread();
  _vtime_start = os::elapsedVTime();
  wait_for_universe_init();

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1CollectorPolicy* g1_policy = g1h->g1_policy();
  G1MMUTracker *mmu_tracker = g1_policy->mmu_tracker();
  Thread *current_thread = Thread::current();

  while (!_should_terminate) {
    // wait until started is set.
    sleepBeforeNextCycle();
    {
      ResourceMark rm;
      HandleMark   hm;
      double cycle_start = os::elapsedVTime();

      // We have to ensure that we finish scanning the root regions
      // before the next GC takes place. To ensure this we have to
      // make sure that we do not join the STS until the root regions
      // have been scanned. If we did then it's possible that a
      // subsequent GC could block us from joining the STS and proceed
      // without the root regions have been scanned which would be a
      // correctness issue.

      double scan_start = os::elapsedTime();
      if (!cm()->has_aborted()) {
        if (G1Log::fine()) {
          gclog_or_tty->date_stamp(PrintGCDateStamps);
          gclog_or_tty->stamp(PrintGCTimeStamps);
          gclog_or_tty->print_cr("[GC concurrent-root-region-scan-start]");
        }

        _cm->scanRootRegions();

        double scan_end = os::elapsedTime();
        if (G1Log::fine()) {
          gclog_or_tty->date_stamp(PrintGCDateStamps);
          gclog_or_tty->stamp(PrintGCTimeStamps);
          gclog_or_tty->print_cr("[GC concurrent-root-region-scan-end, %1.7lf secs]",
                                 scan_end - scan_start);
        }
      }

      double mark_start_sec = os::elapsedTime();
      if (G1Log::fine()) {
        gclog_or_tty->date_stamp(PrintGCDateStamps);
        gclog_or_tty->stamp(PrintGCTimeStamps);
        gclog_or_tty->print_cr("[GC concurrent-mark-start]");
      }

      int iter = 0;
      do {
        iter++;
        if (!cm()->has_aborted()) {
          _cm->markFromRoots();
        }

        double mark_end_time = os::elapsedVTime();
        double mark_end_sec = os::elapsedTime();
        _vtime_mark_accum += (mark_end_time - cycle_start);
        if (!cm()->has_aborted()) {
          if (g1_policy->adaptive_young_list_length()) {
            double now = os::elapsedTime();
            double remark_prediction_ms = g1_policy->predict_remark_time_ms();
            jlong sleep_time_ms = mmu_tracker->when_ms(now, remark_prediction_ms);
            os::sleep(current_thread, sleep_time_ms, false);
          }

          if (G1Log::fine()) {
            gclog_or_tty->date_stamp(PrintGCDateStamps);
            gclog_or_tty->stamp(PrintGCTimeStamps);
            gclog_or_tty->print_cr("[GC concurrent-mark-end, %1.7lf secs]",
                                      mark_end_sec - mark_start_sec);
          }

          CMCheckpointRootsFinalClosure final_cl(_cm);
          VM_CGC_Operation op(&final_cl, "GC remark", true /* needs_pll */);
          VMThread::execute(&op);
        }
        if (cm()->restart_for_overflow()) {
          if (G1TraceMarkStackOverflow) {
            gclog_or_tty->print_cr("Restarting conc marking because of MS overflow "
                                   "in remark (restart #%d).", iter);
          }
          if (G1Log::fine()) {
            gclog_or_tty->date_stamp(PrintGCDateStamps);
            gclog_or_tty->stamp(PrintGCTimeStamps);
            gclog_or_tty->print_cr("[GC concurrent-mark-restart-for-overflow]");
          }
        }
      } while (cm()->restart_for_overflow());

      double end_time = os::elapsedVTime();
      // Update the total virtual time before doing this, since it will try
      // to measure it to get the vtime for this marking.  We purposely
      // neglect the presumably-short "completeCleanup" phase here.
      _vtime_accum = (end_time - _vtime_start);

      if (!cm()->has_aborted()) {
        if (g1_policy->adaptive_young_list_length()) {
          double now = os::elapsedTime();
          double cleanup_prediction_ms = g1_policy->predict_cleanup_time_ms();
          jlong sleep_time_ms = mmu_tracker->when_ms(now, cleanup_prediction_ms);
          os::sleep(current_thread, sleep_time_ms, false);
        }

        CMCleanUp cl_cl(_cm);
        VM_CGC_Operation op(&cl_cl, "GC cleanup", false /* needs_pll */);
        VMThread::execute(&op);
      } else {
        // We don't want to update the marking status if a GC pause
        // is already underway.
        _sts.join();
        g1h->set_marking_complete();
        _sts.leave();
      }

      // Check if cleanup set the free_regions_coming flag. If it
      // hasn't, we can just skip the next step.
      if (g1h->free_regions_coming()) {
        // The following will finish freeing up any regions that we
        // found to be empty during cleanup. We'll do this part
        // without joining the suspendible set. If an evacuation pause
        // takes place, then we would carry on freeing regions in
        // case they are needed by the pause. If a Full GC takes
        // place, it would wait for us to process the regions
        // reclaimed by cleanup.

        double cleanup_start_sec = os::elapsedTime();
        if (G1Log::fine()) {
          gclog_or_tty->date_stamp(PrintGCDateStamps);
          gclog_or_tty->stamp(PrintGCTimeStamps);
          gclog_or_tty->print_cr("[GC concurrent-cleanup-start]");
        }

        // Now do the concurrent cleanup operation.
        _cm->completeCleanup();

        // Notify anyone who's waiting that there are no more free
        // regions coming. We have to do this before we join the STS
        // (in fact, we should not attempt to join the STS in the
        // interval between finishing the cleanup pause and clearing
        // the free_regions_coming flag) otherwise we might deadlock:
        // a GC worker could be blocked waiting for the notification
        // whereas this thread will be blocked for the pause to finish
        // while it's trying to join the STS, which is conditional on
        // the GC workers finishing.
        g1h->reset_free_regions_coming();

        double cleanup_end_sec = os::elapsedTime();
        if (G1Log::fine()) {
          gclog_or_tty->date_stamp(PrintGCDateStamps);
          gclog_or_tty->stamp(PrintGCTimeStamps);
          gclog_or_tty->print_cr("[GC concurrent-cleanup-end, %1.7lf secs]",
                                 cleanup_end_sec - cleanup_start_sec);
        }
      }
      guarantee(cm()->cleanup_list_is_empty(),
                "at this point there should be no regions on the cleanup list");

      // There is a tricky race before recording that the concurrent
      // cleanup has completed and a potential Full GC starting around
      // the same time. We want to make sure that the Full GC calls
      // abort() on concurrent mark after
      // record_concurrent_mark_cleanup_completed(), since abort() is
      // the method that will reset the concurrent mark state. If we
      // end up calling record_concurrent_mark_cleanup_completed()
      // after abort() then we might incorrectly undo some of the work
      // abort() did. Checking the has_aborted() flag after joining
      // the STS allows the correct ordering of the two methods. There
      // are two scenarios:
      //
      // a) If we reach here before the Full GC, the fact that we have
      // joined the STS means that the Full GC cannot start until we
      // leave the STS, so record_concurrent_mark_cleanup_completed()
      // will complete before abort() is called.
      //
      // b) If we reach here during the Full GC, we'll be held up from
      // joining the STS until the Full GC is done, which means that
      // abort() will have completed and has_aborted() will return
      // true to prevent us from calling
      // record_concurrent_mark_cleanup_completed() (and, in fact, it's
      // not needed any more as the concurrent mark state has been
      // already reset).
      _sts.join();
      if (!cm()->has_aborted()) {
        g1_policy->record_concurrent_mark_cleanup_completed();
      }
      _sts.leave();

      if (cm()->has_aborted()) {
        if (G1Log::fine()) {
          gclog_or_tty->date_stamp(PrintGCDateStamps);
          gclog_or_tty->stamp(PrintGCTimeStamps);
          gclog_or_tty->print_cr("[GC concurrent-mark-abort]");
        }
      }

      // We now want to allow clearing of the marking bitmap to be
      // suspended by a collection pause.
      _sts.join();
      _cm->clearNextBitmap();
      _sts.leave();
    }

    // Update the number of full collections that have been
    // completed. This will also notify the FullGCCount_lock in case a
    // Java thread is waiting for a full GC to happen (e.g., it
    // called System.gc() with +ExplicitGCInvokesConcurrent).
    _sts.join();
    g1h->increment_old_marking_cycles_completed(true /* concurrent */);
    g1h->register_concurrent_cycle_end();
    _sts.leave();
  }
  assert(_should_terminate, "just checking");

  terminate();
}


void ConcurrentMarkThread::yield() {
  _sts.yield("Concurrent Mark");
}

void ConcurrentMarkThread::stop() {
  // it is ok to take late safepoints here, if needed
  MutexLockerEx mu(Terminator_lock);
  _should_terminate = true;
  while (!_has_terminated) {
    Terminator_lock->wait();
  }
}

void ConcurrentMarkThread::print() const {
  print_on(tty);
}

void ConcurrentMarkThread::print_on(outputStream* st) const {
  st->print("\"G1 Main Concurrent Mark GC Thread\" ");
  Thread::print_on(st);
  st->cr();
}

void ConcurrentMarkThread::sleepBeforeNextCycle() {
  // We join here because we don't want to do the "shouldConcurrentMark()"
  // below while the world is otherwise stopped.
  assert(!in_progress(), "should have been cleared");

  MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
  while (!started()) {
    CGC_lock->wait(Mutex::_no_safepoint_check_flag);
  }
  set_in_progress();
  clear_started();
}

// Note: As is the case with CMS - this method, although exported
// by the ConcurrentMarkThread, which is a non-JavaThread, can only
// be called by a JavaThread. Currently this is done at vm creation
// time (post-vm-init) by the main/Primordial (Java)Thread.
// XXX Consider changing this in the future to allow the CM thread
// itself to create this thread?
void ConcurrentMarkThread::makeSurrogateLockerThread(TRAPS) {
  assert(UseG1GC, "SLT thread needed only for concurrent GC");
  assert(THREAD->is_Java_thread(), "must be a Java thread");
  assert(_slt == NULL, "SLT already created");
  _slt = SurrogateLockerThread::make(THREAD);
}
