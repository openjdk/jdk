/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/threadCritical.hpp"
#include "services/memPtr.hpp"
#include "services/memReporter.hpp"
#include "services/memTracker.hpp"
#include "utilities/decoder.hpp"
#include "utilities/globalDefinitions.hpp"

bool NMT_track_callsite = false;

// walk all 'known' threads at NMT sync point, and collect their recorders
void SyncThreadRecorderClosure::do_thread(Thread* thread) {
  assert(SafepointSynchronize::is_at_safepoint(), "Safepoint required");
  if (thread->is_Java_thread()) {
    JavaThread* javaThread = (JavaThread*)thread;
    MemRecorder* recorder = javaThread->get_recorder();
    if (recorder != NULL) {
      MemTracker::enqueue_pending_recorder(recorder);
      javaThread->set_recorder(NULL);
    }
  }
  _thread_count ++;
}


MemRecorder*                    MemTracker::_global_recorder = NULL;
MemSnapshot*                    MemTracker::_snapshot = NULL;
MemBaseline                     MemTracker::_baseline;
Mutex*                          MemTracker::_query_lock = NULL;
volatile MemRecorder*           MemTracker::_merge_pending_queue = NULL;
volatile MemRecorder*           MemTracker::_pooled_recorders = NULL;
MemTrackWorker*                 MemTracker::_worker_thread = NULL;
int                             MemTracker::_sync_point_skip_count = 0;
MemTracker::NMTLevel            MemTracker::_tracking_level = MemTracker::NMT_off;
volatile MemTracker::NMTStates  MemTracker::_state = NMT_uninited;
MemTracker::ShutdownReason      MemTracker::_reason = NMT_shutdown_none;
int                             MemTracker::_thread_count = 255;
volatile jint                   MemTracker::_pooled_recorder_count = 0;
debug_only(intx                 MemTracker::_main_thread_tid = 0;)
NOT_PRODUCT(volatile jint       MemTracker::_pending_recorder_count = 0;)

void MemTracker::init_tracking_options(const char* option_line) {
  _tracking_level = NMT_off;
  if (strncmp(option_line, "=summary", 8) == 0) {
    _tracking_level = NMT_summary;
  } else if (strncmp(option_line, "=detail", 8) == 0) {
    _tracking_level = NMT_detail;
  }
}

// first phase of bootstrapping, when VM is still in single-threaded mode.
void MemTracker::bootstrap_single_thread() {
  if (_tracking_level > NMT_off) {
    assert(_state == NMT_uninited, "wrong state");

    // NMT is not supported with UseMallocOnly is on. NMT can NOT
    // handle the amount of malloc data without significantly impacting
    // runtime performance when this flag is on.
    if (UseMallocOnly) {
      shutdown(NMT_use_malloc_only);
      return;
    }

    _query_lock = new (std::nothrow) Mutex(Monitor::max_nonleaf, "NMT_queryLock");
    if (_query_lock == NULL) {
      shutdown(NMT_out_of_memory);
      return;
    }

    debug_only(_main_thread_tid = os::current_thread_id();)
    _state = NMT_bootstrapping_single_thread;
    NMT_track_callsite = (_tracking_level == NMT_detail && can_walk_stack());
  }
}

// second phase of bootstrapping, when VM is about to or already entered multi-theaded mode.
void MemTracker::bootstrap_multi_thread() {
  if (_tracking_level > NMT_off && _state == NMT_bootstrapping_single_thread) {
  // create nmt lock for multi-thread execution
    assert(_main_thread_tid == os::current_thread_id(), "wrong thread");
    _state = NMT_bootstrapping_multi_thread;
    NMT_track_callsite = (_tracking_level == NMT_detail && can_walk_stack());
  }
}

// fully start nmt
void MemTracker::start() {
  // Native memory tracking is off from command line option
  if (_tracking_level == NMT_off || shutdown_in_progress()) return;

  assert(_main_thread_tid == os::current_thread_id(), "wrong thread");
  assert(_state == NMT_bootstrapping_multi_thread, "wrong state");

  _snapshot = new (std::nothrow)MemSnapshot();
  if (_snapshot != NULL && !_snapshot->out_of_memory()) {
    if (start_worker()) {
      _state = NMT_started;
      NMT_track_callsite = (_tracking_level == NMT_detail && can_walk_stack());
      return;
    }
  }

  // fail to start native memory tracking, shut it down
  shutdown(NMT_initialization);
}

/**
 * Shutting down native memory tracking.
 * We can not shutdown native memory tracking immediately, so we just
 * setup shutdown pending flag, every native memory tracking component
 * should orderly shut itself down.
 *
 * The shutdown sequences:
 *  1. MemTracker::shutdown() sets MemTracker to shutdown pending state
 *  2. Worker thread calls MemTracker::final_shutdown(), which transites
 *     MemTracker to final shutdown state.
 *  3. At sync point, MemTracker does final cleanup, before sets memory
 *     tracking level to off to complete shutdown.
 */
void MemTracker::shutdown(ShutdownReason reason) {
  if (_tracking_level == NMT_off) return;

  if (_state <= NMT_bootstrapping_single_thread) {
    // we still in single thread mode, there is not contention
    _state = NMT_shutdown_pending;
    _reason = reason;
  } else {
    // we want to know who initialized shutdown
    if ((jint)NMT_started == Atomic::cmpxchg((jint)NMT_shutdown_pending,
                                       (jint*)&_state, (jint)NMT_started)) {
        _reason = reason;
    }
  }
}

// final phase of shutdown
void MemTracker::final_shutdown() {
  // delete all pending recorders and pooled recorders
  delete_all_pending_recorders();
  delete_all_pooled_recorders();

  {
    // shared baseline and snapshot are the only objects needed to
    // create query results
    MutexLockerEx locker(_query_lock, true);
    // cleanup baseline data and snapshot
    _baseline.clear();
    delete _snapshot;
    _snapshot = NULL;
  }

  // shutdown shared decoder instance, since it is only
  // used by native memory tracking so far.
  Decoder::shutdown();

  MemTrackWorker* worker = NULL;
  {
    ThreadCritical tc;
    // can not delete worker inside the thread critical
    if (_worker_thread != NULL && Thread::current() == _worker_thread) {
      worker = _worker_thread;
      _worker_thread = NULL;
    }
  }
  if (worker != NULL) {
    delete worker;
  }
  _state = NMT_final_shutdown;
}

// delete all pooled recorders
void MemTracker::delete_all_pooled_recorders() {
  // free all pooled recorders
  volatile MemRecorder* cur_head = _pooled_recorders;
  if (cur_head != NULL) {
    MemRecorder* null_ptr = NULL;
    while (cur_head != NULL && (void*)cur_head != Atomic::cmpxchg_ptr((void*)null_ptr,
      (void*)&_pooled_recorders, (void*)cur_head)) {
      cur_head = _pooled_recorders;
    }
    if (cur_head != NULL) {
      delete cur_head;
      _pooled_recorder_count = 0;
    }
  }
}

// delete all recorders in pending queue
void MemTracker::delete_all_pending_recorders() {
  // free all pending recorders
  MemRecorder* pending_head = get_pending_recorders();
  if (pending_head != NULL) {
    delete pending_head;
  }
}

/*
 * retrieve per-thread recorder of specified thread.
 * if thread == NULL, it means global recorder
 */
MemRecorder* MemTracker::get_thread_recorder(JavaThread* thread) {
  if (shutdown_in_progress()) return NULL;

  MemRecorder* rc;
  if (thread == NULL) {
    rc = _global_recorder;
  } else {
    rc = thread->get_recorder();
  }

  if (rc != NULL && rc->is_full()) {
    enqueue_pending_recorder(rc);
    rc = NULL;
  }

  if (rc == NULL) {
    rc = get_new_or_pooled_instance();
    if (thread == NULL) {
      _global_recorder = rc;
    } else {
      thread->set_recorder(rc);
    }
  }
  return rc;
}

/*
 * get a per-thread recorder from pool, or create a new one if
 * there is not one available.
 */
MemRecorder* MemTracker::get_new_or_pooled_instance() {
   MemRecorder* cur_head = const_cast<MemRecorder*> (_pooled_recorders);
   if (cur_head == NULL) {
     MemRecorder* rec = new (std::nothrow)MemRecorder();
     if (rec == NULL || rec->out_of_memory()) {
       shutdown(NMT_out_of_memory);
       if (rec != NULL) {
         delete rec;
         rec = NULL;
       }
     }
     return rec;
   } else {
     MemRecorder* next_head = cur_head->next();
     if ((void*)cur_head != Atomic::cmpxchg_ptr((void*)next_head, (void*)&_pooled_recorders,
       (void*)cur_head)) {
       return get_new_or_pooled_instance();
     }
     cur_head->set_next(NULL);
     Atomic::dec(&_pooled_recorder_count);
     debug_only(cur_head->set_generation();)
     return cur_head;
  }
}

/*
 * retrieve all recorders in pending queue, and empty the queue
 */
MemRecorder* MemTracker::get_pending_recorders() {
  MemRecorder* cur_head = const_cast<MemRecorder*>(_merge_pending_queue);
  MemRecorder* null_ptr = NULL;
  while ((void*)cur_head != Atomic::cmpxchg_ptr((void*)null_ptr, (void*)&_merge_pending_queue,
    (void*)cur_head)) {
    cur_head = const_cast<MemRecorder*>(_merge_pending_queue);
  }
  NOT_PRODUCT(Atomic::store(0, &_pending_recorder_count));
  return cur_head;
}

/*
 * release a recorder to recorder pool.
 */
void MemTracker::release_thread_recorder(MemRecorder* rec) {
  assert(rec != NULL, "null recorder");
  // we don't want to pool too many recorders
  rec->set_next(NULL);
  if (shutdown_in_progress() || _pooled_recorder_count > _thread_count * 2) {
    delete rec;
    return;
  }

  rec->clear();
  MemRecorder* cur_head = const_cast<MemRecorder*>(_pooled_recorders);
  rec->set_next(cur_head);
  while ((void*)cur_head != Atomic::cmpxchg_ptr((void*)rec, (void*)&_pooled_recorders,
    (void*)cur_head)) {
    cur_head = const_cast<MemRecorder*>(_pooled_recorders);
    rec->set_next(cur_head);
  }
  Atomic::inc(&_pooled_recorder_count);
}

/*
 * This is the most important method in whole nmt implementation.
 *
 * Create a memory record.
 * 1. When nmt is in single-threaded bootstrapping mode, no lock is needed as VM
 *    still in single thread mode.
 * 2. For all threads other than JavaThread, ThreadCritical is needed
 *    to write to recorders to global recorder.
 * 3. For JavaThreads that are not longer visible by safepoint, also
 *    need to take ThreadCritical and records are written to global
 *    recorders, since these threads are NOT walked by Threads.do_thread().
 * 4. JavaThreads that are running in native state, have to transition
 *    to VM state before writing to per-thread recorders.
 * 5. JavaThreads that are running in VM state do not need any lock and
 *    records are written to per-thread recorders.
 * 6. For a thread has yet to attach VM 'Thread', they need to take
 *    ThreadCritical to write to global recorder.
 *
 *    Important note:
 *    NO LOCK should be taken inside ThreadCritical lock !!!
 */
void MemTracker::create_memory_record(address addr, MEMFLAGS flags,
    size_t size, address pc, Thread* thread) {
  if (!shutdown_in_progress()) {
    // single thread, we just write records direct to global recorder,'
    // with any lock
    if (_state == NMT_bootstrapping_single_thread) {
      assert(_main_thread_tid == os::current_thread_id(), "wrong thread");
      thread = NULL;
    } else {
      if (thread == NULL) {
          // don't use Thread::current(), since it is possible that
          // the calling thread has yet to attach to VM 'Thread',
          // which will result assertion failure
          thread = ThreadLocalStorage::thread();
      }
    }

    if (thread != NULL) {
      if (thread->is_Java_thread() && ((JavaThread*)thread)->is_safepoint_visible()) {
        JavaThread*      java_thread = static_cast<JavaThread*>(thread);
        JavaThreadState  state = java_thread->thread_state();
        if (SafepointSynchronize::safepoint_safe(java_thread, state)) {
          // JavaThreads that are safepoint safe, can run through safepoint,
          // so ThreadCritical is needed to ensure no threads at safepoint create
          // new records while the records are being gathered and the sequence number is changing
          ThreadCritical tc;
          create_record_in_recorder(addr, flags, size, pc, java_thread);
        } else {
          create_record_in_recorder(addr, flags, size, pc, java_thread);
        }
      } else {
        // other threads, such as worker and watcher threads, etc. need to
        // take ThreadCritical to write to global recorder
        ThreadCritical tc;
        create_record_in_recorder(addr, flags, size, pc, NULL);
      }
    } else {
      if (_state == NMT_bootstrapping_single_thread) {
        // single thread, no lock needed
        create_record_in_recorder(addr, flags, size, pc, NULL);
      } else {
        // for thread has yet to attach VM 'Thread', we can not use VM mutex.
        // use native thread critical instead
        ThreadCritical tc;
        create_record_in_recorder(addr, flags, size, pc, NULL);
      }
    }
  }
}

// write a record to proper recorder. No lock can be taken from this method
// down.
void MemTracker::create_record_in_recorder(address addr, MEMFLAGS flags,
    size_t size, address pc, JavaThread* thread) {

    MemRecorder* rc = get_thread_recorder(thread);
    if (rc != NULL) {
      rc->record(addr, flags, size, pc);
    }
}

/**
 * enqueue a recorder to pending queue
 */
void MemTracker::enqueue_pending_recorder(MemRecorder* rec) {
  assert(rec != NULL, "null recorder");

  // we are shutting down, so just delete it
  if (shutdown_in_progress()) {
    rec->set_next(NULL);
    delete rec;
    return;
  }

  MemRecorder* cur_head = const_cast<MemRecorder*>(_merge_pending_queue);
  rec->set_next(cur_head);
  while ((void*)cur_head != Atomic::cmpxchg_ptr((void*)rec, (void*)&_merge_pending_queue,
    (void*)cur_head)) {
    cur_head = const_cast<MemRecorder*>(_merge_pending_queue);
    rec->set_next(cur_head);
  }
  NOT_PRODUCT(Atomic::inc(&_pending_recorder_count);)
}

/*
 * The method is called at global safepoint
 * during it synchronization process.
 *   1. enqueue all JavaThreads' per-thread recorders
 *   2. enqueue global recorder
 *   3. retrieve all pending recorders
 *   4. reset global sequence number generator
 *   5. call worker's sync
 */
#define MAX_SAFEPOINTS_TO_SKIP     128
#define SAFE_SEQUENCE_THRESHOLD    30
#define HIGH_GENERATION_THRESHOLD  60

void MemTracker::sync() {
  assert(_tracking_level > NMT_off, "NMT is not enabled");
  assert(SafepointSynchronize::is_at_safepoint(), "Safepoint required");

  // Some GC tests hit large number of safepoints in short period of time
  // without meaningful activities. We should prevent going to
  // sync point in these cases, which can potentially exhaust generation buffer.
  // Here is the factots to determine if we should go into sync point:
  // 1. not to overflow sequence number
  // 2. if we are in danger to overflow generation buffer
  // 3. how many safepoints we already skipped sync point
  if (_state == NMT_started) {
    // worker thread is not ready, no one can manage generation
    // buffer, so skip this safepoint
    if (_worker_thread == NULL) return;

    if (_sync_point_skip_count < MAX_SAFEPOINTS_TO_SKIP) {
      int per_seq_in_use = SequenceGenerator::peek() * 100 / max_jint;
      int per_gen_in_use = _worker_thread->generations_in_use() * 100 / MAX_GENERATIONS;
      if (per_seq_in_use < SAFE_SEQUENCE_THRESHOLD && per_gen_in_use >= HIGH_GENERATION_THRESHOLD) {
        _sync_point_skip_count ++;
        return;
      }
    }
    _sync_point_skip_count = 0;
    {
      // This method is running at safepoint, with ThreadCritical lock,
      // it should guarantee that NMT is fully sync-ed.
      ThreadCritical tc;

      // walk all JavaThreads to collect recorders
      SyncThreadRecorderClosure stc;
      Threads::threads_do(&stc);

      _thread_count = stc.get_thread_count();
      MemRecorder* pending_recorders = get_pending_recorders();

      if (_global_recorder != NULL) {
        _global_recorder->set_next(pending_recorders);
        pending_recorders = _global_recorder;
        _global_recorder = NULL;
      }
      SequenceGenerator::reset();
      // check _worker_thread with lock to avoid racing condition
      if (_worker_thread != NULL) {
        _worker_thread->at_sync_point(pending_recorders);
      }
    }
  }

  // now, it is the time to shut whole things off
  if (_state == NMT_final_shutdown) {
    // walk all JavaThreads to delete all recorders
    SyncThreadRecorderClosure stc;
    Threads::threads_do(&stc);
    // delete global recorder
    {
      ThreadCritical tc;
      if (_global_recorder != NULL) {
        delete _global_recorder;
        _global_recorder = NULL;
      }
    }
    MemRecorder* pending_recorders = get_pending_recorders();
    if (pending_recorders != NULL) {
      delete pending_recorders;
    }
    // try at a later sync point to ensure MemRecorder instance drops to zero to
    // completely shutdown NMT
    if (MemRecorder::_instance_count == 0) {
      _state = NMT_shutdown;
      _tracking_level = NMT_off;
    }
  }
}

/*
 * Start worker thread.
 */
bool MemTracker::start_worker() {
  assert(_worker_thread == NULL, "Just Check");
  _worker_thread = new (std::nothrow) MemTrackWorker();
  if (_worker_thread == NULL || _worker_thread->has_error()) {
    shutdown(NMT_initialization);
    return false;
  }
  _worker_thread->start();
  return true;
}

/*
 * We need to collect a JavaThread's per-thread recorder
 * before it exits.
 */
void MemTracker::thread_exiting(JavaThread* thread) {
  if (is_on()) {
    MemRecorder* rec = thread->get_recorder();
    if (rec != NULL) {
      enqueue_pending_recorder(rec);
      thread->set_recorder(NULL);
    }
  }
}

// baseline current memory snapshot
bool MemTracker::baseline() {
  MutexLockerEx lock(_query_lock, true);
  MemSnapshot* snapshot = get_snapshot();
  if (snapshot != NULL) {
    return _baseline.baseline(*snapshot, false);
  }
  return false;
}

// print memory usage from current snapshot
bool MemTracker::print_memory_usage(BaselineOutputer& out, size_t unit, bool summary_only) {
  MemBaseline  baseline;
  MutexLockerEx lock(_query_lock, true);
  MemSnapshot* snapshot = get_snapshot();
  if (snapshot != NULL && baseline.baseline(*snapshot, summary_only)) {
    BaselineReporter reporter(out, unit);
    reporter.report_baseline(baseline, summary_only);
    return true;
  }
  return false;
}

// compare memory usage between current snapshot and baseline
bool MemTracker::compare_memory_usage(BaselineOutputer& out, size_t unit, bool summary_only) {
  MutexLockerEx lock(_query_lock, true);
  if (_baseline.baselined()) {
    MemBaseline baseline;
    MemSnapshot* snapshot = get_snapshot();
    if (snapshot != NULL && baseline.baseline(*snapshot, summary_only)) {
      BaselineReporter reporter(out, unit);
      reporter.diff_baselines(baseline, _baseline, summary_only);
      return true;
    }
  }
  return false;
}

#ifndef PRODUCT
void MemTracker::walk_stack(int toSkip, char* buf, int len) {
  int cur_len = 0;
  char tmp[1024];
  address pc;

  while (cur_len < len) {
    pc = os::get_caller_pc(toSkip + 1);
    if (pc != NULL && os::dll_address_to_function_name(pc, tmp, sizeof(tmp), NULL)) {
      jio_snprintf(&buf[cur_len], (len - cur_len), "%s\n", tmp);
      cur_len = (int)strlen(buf);
    } else {
      buf[cur_len] = '\0';
      break;
    }
    toSkip ++;
  }
}

void MemTracker::print_tracker_stats(outputStream* st) {
  st->print_cr("\nMemory Tracker Stats:");
  st->print_cr("\tMax sequence number = %d", SequenceGenerator::max_seq_num());
  st->print_cr("\tthead count = %d", _thread_count);
  st->print_cr("\tArena instance = %d", Arena::_instance_count);
  st->print_cr("\tpooled recorder count = %d", _pooled_recorder_count);
  st->print_cr("\tqueued recorder count = %d", _pending_recorder_count);
  st->print_cr("\tmemory recorder instance count = %d", MemRecorder::_instance_count);
  if (_worker_thread != NULL) {
    st->print_cr("\tWorker thread:");
    st->print_cr("\t\tSync point count = %d", _worker_thread->_sync_point_count);
    st->print_cr("\t\tpending recorder count = %d", _worker_thread->count_pending_recorders());
    st->print_cr("\t\tmerge count = %d", _worker_thread->_merge_count);
  } else {
    st->print_cr("\tWorker thread is not started");
  }
  st->print_cr(" ");

  if (_snapshot != NULL) {
    _snapshot->print_snapshot_stats(st);
  } else {
    st->print_cr("No snapshot");
  }
}
#endif

