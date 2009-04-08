/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_concurrentMarkThread.cpp.incl"

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
  _vtime_mark_accum(0.0),
  _vtime_count_accum(0.0)
{
  create_and_start();
}

class CMCheckpointRootsInitialClosure: public VoidClosure {

  ConcurrentMark* _cm;
public:

  CMCheckpointRootsInitialClosure(ConcurrentMark* cm) :
    _cm(cm) {}

  void do_void(){
    _cm->checkpointRootsInitial();
  }
};

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

  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  G1CollectorPolicy* g1_policy = g1->g1_policy();
  G1MMUTracker *mmu_tracker = g1_policy->mmu_tracker();
  Thread *current_thread = Thread::current();

  while (!_should_terminate) {
    // wait until started is set.
    sleepBeforeNextCycle();
    {
      ResourceMark rm;
      HandleMark   hm;
      double cycle_start = os::elapsedVTime();
      double mark_start_sec = os::elapsedTime();
      char verbose_str[128];

      if (PrintGC) {
        gclog_or_tty->date_stamp(PrintGCDateStamps);
        gclog_or_tty->stamp(PrintGCTimeStamps);
        gclog_or_tty->print_cr("[GC concurrent-mark-start]");
      }

      if (!g1_policy->in_young_gc_mode()) {
        // this ensures the flag is not set if we bail out of the marking
        // cycle; normally the flag is cleared immediately after cleanup
        g1->set_marking_complete();

        if (g1_policy->adaptive_young_list_length()) {
          double now = os::elapsedTime();
          double init_prediction_ms = g1_policy->predict_init_time_ms();
          jlong sleep_time_ms = mmu_tracker->when_ms(now, init_prediction_ms);
          os::sleep(current_thread, sleep_time_ms, false);
        }

        // We don't have to skip here if we've been asked to restart, because
        // in the worst case we just enqueue a new VM operation to start a
        // marking.  Note that the init operation resets has_aborted()
        CMCheckpointRootsInitialClosure init_cl(_cm);
        strcpy(verbose_str, "GC initial-mark");
        VM_CGC_Operation op(&init_cl, verbose_str);
        VMThread::execute(&op);
      }

      int iter = 0;
      do {
        iter++;
        if (!cm()->has_aborted()) {
          _cm->markFromRoots();
        } else {
          if (TraceConcurrentMark)
            gclog_or_tty->print_cr("CM-skip-mark-from-roots");
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

          if (PrintGC) {
            gclog_or_tty->date_stamp(PrintGCDateStamps);
            gclog_or_tty->stamp(PrintGCTimeStamps);
            gclog_or_tty->print_cr("[GC concurrent-mark-end, %1.7lf sec]",
                                      mark_end_sec - mark_start_sec);
          }

          CMCheckpointRootsFinalClosure final_cl(_cm);
          sprintf(verbose_str, "GC remark");
          VM_CGC_Operation op(&final_cl, verbose_str);
          VMThread::execute(&op);
        } else {
          if (TraceConcurrentMark)
            gclog_or_tty->print_cr("CM-skip-remark");
        }
        if (cm()->restart_for_overflow() &&
            G1TraceMarkStackOverflow) {
          gclog_or_tty->print_cr("Restarting conc marking because of MS overflow "
                                 "in remark (restart #%d).", iter);
        }

        if (cm()->restart_for_overflow()) {
          if (PrintGC) {
            gclog_or_tty->date_stamp(PrintGCDateStamps);
            gclog_or_tty->stamp(PrintGCTimeStamps);
            gclog_or_tty->print_cr("[GC concurrent-mark-restart-for-overflow]");
          }
        }
      } while (cm()->restart_for_overflow());
      double counting_start_time = os::elapsedVTime();

      // YSR: These look dubious (i.e. redundant) !!! FIX ME
      slt()->manipulatePLL(SurrogateLockerThread::acquirePLL);
      slt()->manipulatePLL(SurrogateLockerThread::releaseAndNotifyPLL);

      if (!cm()->has_aborted()) {
        double count_start_sec = os::elapsedTime();
        if (PrintGC) {
          gclog_or_tty->date_stamp(PrintGCDateStamps);
          gclog_or_tty->stamp(PrintGCTimeStamps);
          gclog_or_tty->print_cr("[GC concurrent-count-start]");
        }

        _sts.join();
        _cm->calcDesiredRegions();
        _sts.leave();

        if (!cm()->has_aborted()) {
          double count_end_sec = os::elapsedTime();
          if (PrintGC) {
            gclog_or_tty->date_stamp(PrintGCDateStamps);
            gclog_or_tty->stamp(PrintGCTimeStamps);
            gclog_or_tty->print_cr("[GC concurrent-count-end, %1.7lf]",
                                   count_end_sec - count_start_sec);
          }
        }
      } else {
        if (TraceConcurrentMark) gclog_or_tty->print_cr("CM-skip-end-game");
      }
      double end_time = os::elapsedVTime();
      _vtime_count_accum += (end_time - counting_start_time);
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
        sprintf(verbose_str, "GC cleanup");
        VM_CGC_Operation op(&cl_cl, verbose_str);
        VMThread::execute(&op);
      } else {
        if (TraceConcurrentMark) gclog_or_tty->print_cr("CM-skip-cleanup");
        G1CollectedHeap::heap()->set_marking_complete();
      }

      if (!cm()->has_aborted()) {
        double cleanup_start_sec = os::elapsedTime();
        if (PrintGC) {
          gclog_or_tty->date_stamp(PrintGCDateStamps);
          gclog_or_tty->stamp(PrintGCTimeStamps);
          gclog_or_tty->print_cr("[GC concurrent-cleanup-start]");
        }

        // Now do the remainder of the cleanup operation.
        _sts.join();
        _cm->completeCleanup();
        if (!cm()->has_aborted()) {
          g1_policy->record_concurrent_mark_cleanup_completed();

          double cleanup_end_sec = os::elapsedTime();
          if (PrintGC) {
            gclog_or_tty->date_stamp(PrintGCDateStamps);
            gclog_or_tty->stamp(PrintGCTimeStamps);
            gclog_or_tty->print_cr("[GC concurrent-cleanup-end, %1.7lf]",
                                   cleanup_end_sec - cleanup_start_sec);
          }
        }
        _sts.leave();
      }
      // We're done: no more unclean regions coming.
      G1CollectedHeap::heap()->set_unclean_regions_coming(false);

      if (cm()->has_aborted()) {
        if (PrintGC) {
          gclog_or_tty->date_stamp(PrintGCDateStamps);
          gclog_or_tty->stamp(PrintGCTimeStamps);
          gclog_or_tty->print_cr("[GC concurrent-mark-abort]");
        }
      }

      _sts.join();
      _cm->disable_co_trackers();
      _sts.leave();

      // we now want to allow clearing of the marking bitmap to be
      // suspended by a collection pause.
      _sts.join();
      _cm->clearNextBitmap();
      _sts.leave();
    }
  }
  assert(_should_terminate, "just checking");

  terminate();
}


void ConcurrentMarkThread::yield() {
  if (TraceConcurrentMark) gclog_or_tty->print_cr("CM-yield");
  _sts.yield("Concurrent Mark");
  if (TraceConcurrentMark) gclog_or_tty->print_cr("CM-yield-end");
}

void ConcurrentMarkThread::stop() {
  // it is ok to take late safepoints here, if needed
  MutexLockerEx mu(Terminator_lock);
  _should_terminate = true;
  while (!_has_terminated) {
    Terminator_lock->wait();
  }
  if (TraceConcurrentMark) gclog_or_tty->print_cr("CM-stop");
}

void ConcurrentMarkThread::print() {
  gclog_or_tty->print("\"Concurrent Mark GC Thread\" ");
  Thread::print();
  gclog_or_tty->cr();
}

void ConcurrentMarkThread::sleepBeforeNextCycle() {
  clear_in_progress();
  // We join here because we don't want to do the "shouldConcurrentMark()"
  // below while the world is otherwise stopped.
  MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
  while (!started()) {
    if (TraceConcurrentMark) gclog_or_tty->print_cr("CM-sleeping");
    CGC_lock->wait(Mutex::_no_safepoint_check_flag);
  }
  set_in_progress();
  clear_started();
  if (TraceConcurrentMark) gclog_or_tty->print_cr("CM-starting");
}

// Note: this method, although exported by the ConcurrentMarkSweepThread,
// which is a non-JavaThread, can only be called by a JavaThread.
// Currently this is done at vm creation time (post-vm-init) by the
// main/Primordial (Java)Thread.
// XXX Consider changing this in the future to allow the CMS thread
// itself to create this thread?
void ConcurrentMarkThread::makeSurrogateLockerThread(TRAPS) {
  assert(_slt == NULL, "SLT already created");
  _slt = SurrogateLockerThread::make(THREAD);
}
