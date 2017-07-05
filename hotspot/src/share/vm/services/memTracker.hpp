/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_MEM_TRACKER_HPP
#define SHARE_VM_SERVICES_MEM_TRACKER_HPP

#include "utilities/macros.hpp"

#if !INCLUDE_NMT

#include "utilities/ostream.hpp"

class BaselineOutputer : public StackObj {

};

class BaselineTTYOutputer : public BaselineOutputer {
  public:
    BaselineTTYOutputer(outputStream* st) { }
};

class MemTracker : AllStatic {
  public:
   enum ShutdownReason {
      NMT_shutdown_none,     // no shutdown requested
      NMT_shutdown_user,     // user requested shutdown
      NMT_normal,            // normal shutdown, process exit
      NMT_out_of_memory,     // shutdown due to out of memory
      NMT_initialization,    // shutdown due to initialization failure
      NMT_use_malloc_only,   // can not combine NMT with UseMallocOnly flag
      NMT_error_reporting,   // shutdown by vmError::report_and_die()
      NMT_out_of_generation, // running out of generation queue
      NMT_sequence_overflow  // overflow the sequence number
   };

  class Tracker {
   public:
    void discard() { }

    void record(address addr, size_t size = 0, MEMFLAGS flags = mtNone, address pc = NULL) { }
    void record(address old_addr, address new_addr, size_t size,
      MEMFLAGS flags, address pc = NULL) { }
  };

  private:
   static Tracker  _tkr;


  public:
   static inline void init_tracking_options(const char* option_line) { }
   static inline bool is_on()   { return false; }
   static const char* reason()  { return "Native memory tracking is not implemented"; }
   static inline bool can_walk_stack() { return false; }

   static inline void bootstrap_single_thread() { }
   static inline void bootstrap_multi_thread() { }
   static inline void start() { }

   static inline void record_malloc(address addr, size_t size, MEMFLAGS flags,
        address pc = 0, Thread* thread = NULL) { }
   static inline void record_free(address addr, MEMFLAGS flags, Thread* thread = NULL) { }
   static inline void record_arena_size(address addr, size_t size) { }
   static inline void record_virtual_memory_reserve(address addr, size_t size,
        MEMFLAGS flags, address pc = 0, Thread* thread = NULL) { }
   static inline void record_virtual_memory_reserve_and_commit(address addr, size_t size,
        MEMFLAGS flags, address pc = 0, Thread* thread = NULL) { }
   static inline void record_virtual_memory_commit(address addr, size_t size,
        address pc = 0, Thread* thread = NULL) { }
   static inline void record_virtual_memory_type(address base, MEMFLAGS flags,
        Thread* thread = NULL) { }
   static inline Tracker get_realloc_tracker() { return _tkr; }
   static inline Tracker get_virtual_memory_uncommit_tracker() { return _tkr; }
   static inline Tracker get_virtual_memory_release_tracker()  { return _tkr; }
   static inline bool baseline() { return false; }
   static inline bool has_baseline() { return false; }

   static inline void set_autoShutdown(bool value) { }
   static void shutdown(ShutdownReason reason) { }
   static inline bool shutdown_in_progress() { return false; }
   static bool print_memory_usage(BaselineOutputer& out, size_t unit,
            bool summary_only = true) { return false; }
   static bool compare_memory_usage(BaselineOutputer& out, size_t unit,
            bool summary_only = true) { return false; }

   static bool wbtest_wait_for_data_merge() { return false; }

   static inline void sync() { }
   static inline void thread_exiting(JavaThread* thread) { }
};


#else // !INCLUDE_NMT

#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "runtime/mutex.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "services/memPtr.hpp"
#include "services/memRecorder.hpp"
#include "services/memSnapshot.hpp"
#include "services/memTrackWorker.hpp"

extern bool NMT_track_callsite;

#ifndef MAX_UNSIGNED_LONG
#define MAX_UNSIGNED_LONG    (unsigned long)(-1)
#endif

#ifdef ASSERT
  #define DEBUG_CALLER_PC  (NMT_track_callsite ? os::get_caller_pc(2) : 0)
#else
  #define DEBUG_CALLER_PC  0
#endif

// The thread closure walks threads to collect per-thread
// memory recorders at NMT sync point
class SyncThreadRecorderClosure : public ThreadClosure {
 private:
  int _thread_count;

 public:
  SyncThreadRecorderClosure() {
    _thread_count =0;
  }

  void do_thread(Thread* thread);
  int  get_thread_count() const {
    return _thread_count;
  }
};

class BaselineOutputer;
class MemSnapshot;
class MemTrackWorker;
class Thread;
/*
 * MemTracker is the 'gate' class to native memory tracking runtime.
 */
class MemTracker : AllStatic {
  friend class GenerationData;
  friend class MemTrackWorker;
  friend class MemSnapshot;
  friend class SyncThreadRecorderClosure;

  // NMT state
  enum NMTStates {
    NMT_uninited,                        // not yet initialized
    NMT_bootstrapping_single_thread,     // bootstrapping, VM is in single thread mode
    NMT_bootstrapping_multi_thread,      // bootstrapping, VM is about to enter multi-thread mode
    NMT_started,                         // NMT fully started
    NMT_shutdown_pending,                // shutdown pending
    NMT_final_shutdown,                  // in final phase of shutdown
    NMT_shutdown                         // shutdown
  };

 public:
  class Tracker : public StackObj {
    friend class MemTracker;
   public:
    enum MemoryOperation {
      NoOp,                   // no op
      Malloc,                 // malloc
      Realloc,                // realloc
      Free,                   // free
      Reserve,                // virtual memory reserve
      Commit,                 // virtual memory commit
      ReserveAndCommit,       // virtual memory reserve and commit
      StackAlloc = ReserveAndCommit, // allocate thread stack
      Type,                   // assign virtual memory type
      Uncommit,               // virtual memory uncommit
      Release,                // virtual memory release
      ArenaSize,              // set arena size
      StackRelease            // release thread stack
    };


   protected:
    Tracker(MemoryOperation op, Thread* thr = NULL);

   public:
    void discard();

    void record(address addr, size_t size = 0, MEMFLAGS flags = mtNone, address pc = NULL);
    void record(address old_addr, address new_addr, size_t size,
      MEMFLAGS flags, address pc = NULL);

   private:
    bool            _need_thread_critical_lock;
    JavaThread*     _java_thread;
    MemoryOperation _op;          // memory operation
    jint            _seq;         // reserved sequence number
  };


 public:
  // native memory tracking level
  enum NMTLevel {
    NMT_off,              // native memory tracking is off
    NMT_summary,          // don't track callsite
    NMT_detail            // track callsite also
  };

   enum ShutdownReason {
     NMT_shutdown_none,     // no shutdown requested
     NMT_shutdown_user,     // user requested shutdown
     NMT_normal,            // normal shutdown, process exit
     NMT_out_of_memory,     // shutdown due to out of memory
     NMT_initialization,    // shutdown due to initialization failure
     NMT_use_malloc_only,   // can not combine NMT with UseMallocOnly flag
     NMT_error_reporting,   // shutdown by vmError::report_and_die()
     NMT_out_of_generation, // running out of generation queue
     NMT_sequence_overflow  // overflow the sequence number
   };

 public:
  // initialize NMT tracking level from command line options, called
   // from VM command line parsing code
  static void init_tracking_options(const char* option_line);

  // if NMT is enabled to record memory activities
  static inline bool is_on() {
    return (_tracking_level >= NMT_summary &&
      _state >= NMT_bootstrapping_single_thread);
  }

  static inline enum NMTLevel tracking_level() {
    return _tracking_level;
  }

  // user readable reason for shutting down NMT
  static const char* reason() {
    switch(_reason) {
      case NMT_shutdown_none:
        return "Native memory tracking is not enabled";
      case NMT_shutdown_user:
        return "Native memory tracking has been shutdown by user";
      case NMT_normal:
        return "Native memory tracking has been shutdown due to process exiting";
      case NMT_out_of_memory:
        return "Native memory tracking has been shutdown due to out of native memory";
      case NMT_initialization:
        return "Native memory tracking failed to initialize";
      case NMT_error_reporting:
        return "Native memory tracking has been shutdown due to error reporting";
      case NMT_out_of_generation:
        return "Native memory tracking has been shutdown due to running out of generation buffer";
      case NMT_sequence_overflow:
        return "Native memory tracking has been shutdown due to overflow the sequence number";
      case NMT_use_malloc_only:
        return "Native memory tracking is not supported when UseMallocOnly is on";
      default:
        ShouldNotReachHere();
        return NULL;
    }
  }

  // test if we can walk native stack
  static bool can_walk_stack() {
  // native stack is not walkable during bootstrapping on sparc
#if defined(SPARC)
    return (_state == NMT_started);
#else
    return (_state >= NMT_bootstrapping_single_thread && _state  <= NMT_started);
#endif
  }

  // if native memory tracking tracks callsite
  static inline bool track_callsite() { return _tracking_level == NMT_detail; }

  // NMT automatically shuts itself down under extreme situation by default.
  // When the value is set to false,  NMT will try its best to stay alive,
  // even it has to slow down VM.
  static inline void set_autoShutdown(bool value) {
    AutoShutdownNMT = value;
    if (AutoShutdownNMT && _slowdown_calling_thread) {
      _slowdown_calling_thread = false;
    }
  }

  // shutdown native memory tracking capability. Native memory tracking
  // can be shutdown by VM when it encounters low memory scenarios.
  // Memory tracker should gracefully shutdown itself, and preserve the
  // latest memory statistics for post morten diagnosis.
  static void shutdown(ShutdownReason reason);

  // if there is shutdown requested
  static inline bool shutdown_in_progress() {
    return (_state >= NMT_shutdown_pending);
  }

  // bootstrap native memory tracking, so it can start to collect raw data
  // before worker thread can start

  // the first phase of bootstrapping, when VM still in single-threaded mode
  static void bootstrap_single_thread();
  // the second phase of bootstrapping, VM is about or already in multi-threaded mode
  static void bootstrap_multi_thread();


  // start() has to be called when VM still in single thread mode, but after
  // command line option parsing is done.
  static void start();

  // record a 'malloc' call
  static inline void record_malloc(address addr, size_t size, MEMFLAGS flags,
                            address pc = 0, Thread* thread = NULL) {
    Tracker tkr(Tracker::Malloc, thread);
    tkr.record(addr, size, flags, pc);
  }
  // record a 'free' call
  static inline void record_free(address addr, MEMFLAGS flags, Thread* thread = NULL) {
    Tracker tkr(Tracker::Free, thread);
    tkr.record(addr, 0, flags, DEBUG_CALLER_PC);
  }

  static inline void record_arena_size(address addr, size_t size) {
    Tracker tkr(Tracker::ArenaSize);
    tkr.record(addr, size);
  }

  // record a virtual memory 'reserve' call
  static inline void record_virtual_memory_reserve(address addr, size_t size,
                     MEMFLAGS flags, address pc = 0, Thread* thread = NULL) {
    assert(size > 0, "Sanity check");
    Tracker tkr(Tracker::Reserve, thread);
    tkr.record(addr, size, flags, pc);
  }

  static inline void record_thread_stack(address addr, size_t size, Thread* thr,
                           address pc = 0) {
    Tracker tkr(Tracker::StackAlloc, thr);
    tkr.record(addr, size, mtThreadStack, pc);
  }

  static inline void release_thread_stack(address addr, size_t size, Thread* thr) {
    Tracker tkr(Tracker::StackRelease, thr);
    tkr.record(addr, size, mtThreadStack, DEBUG_CALLER_PC);
  }

  // record a virtual memory 'commit' call
  static inline void record_virtual_memory_commit(address addr, size_t size,
                            address pc, Thread* thread = NULL) {
    Tracker tkr(Tracker::Commit, thread);
    tkr.record(addr, size, mtNone, pc);
  }

  static inline void record_virtual_memory_reserve_and_commit(address addr, size_t size,
    MEMFLAGS flags, address pc, Thread* thread = NULL) {
    Tracker tkr(Tracker::ReserveAndCommit, thread);
    tkr.record(addr, size, flags, pc);
  }


  // record memory type on virtual memory base address
  static inline void record_virtual_memory_type(address base, MEMFLAGS flags,
                            Thread* thread = NULL) {
    Tracker tkr(Tracker::Type);
    tkr.record(base, 0, flags);
  }

  // Get memory trackers for memory operations that can result race conditions.
  // The memory tracker has to be obtained before realloc, virtual memory uncommit
  // and virtual memory release, and call tracker.record() method if operation
  // succeeded, or tracker.discard() to abort the tracking.
  static inline Tracker get_realloc_tracker() {
    return Tracker(Tracker::Realloc);
  }

  static inline Tracker get_virtual_memory_uncommit_tracker() {
    return Tracker(Tracker::Uncommit);
  }

  static inline Tracker get_virtual_memory_release_tracker() {
    return Tracker(Tracker::Release);
  }


  // create memory baseline of current memory snapshot
  static bool baseline();
  // is there a memory baseline
  static bool has_baseline() {
    return _baseline.baselined();
  }

  // print memory usage from current snapshot
  static bool print_memory_usage(BaselineOutputer& out, size_t unit,
           bool summary_only = true);
  // compare memory usage between current snapshot and baseline
  static bool compare_memory_usage(BaselineOutputer& out, size_t unit,
           bool summary_only = true);

  // the version for whitebox testing support, it ensures that all memory
  // activities before this method call, are reflected in the snapshot
  // database.
  static bool wbtest_wait_for_data_merge();

  // sync is called within global safepoint to synchronize nmt data
  static void sync();

  // called when a thread is about to exit
  static void thread_exiting(JavaThread* thread);

  // retrieve global snapshot
  static MemSnapshot* get_snapshot() {
    if (shutdown_in_progress()) {
      return NULL;
    }
    return _snapshot;
  }

  // print tracker stats
  NOT_PRODUCT(static void print_tracker_stats(outputStream* st);)
  NOT_PRODUCT(static void walk_stack(int toSkip, char* buf, int len);)

 private:
  // start native memory tracking worker thread
  static bool start_worker(MemSnapshot* snapshot);

  // called by worker thread to complete shutdown process
  static void final_shutdown();

 protected:
  // retrieve per-thread recorder of the specified thread.
  // if the recorder is full, it will be enqueued to overflow
  // queue, a new recorder is acquired from recorder pool or a
  // new instance is created.
  // when thread == NULL, it means global recorder
  static MemRecorder* get_thread_recorder(JavaThread* thread);

  // per-thread recorder pool
  static void release_thread_recorder(MemRecorder* rec);
  static void delete_all_pooled_recorders();

  // pending recorder queue. Recorders are queued to pending queue
  // when they are overflowed or collected at nmt sync point.
  static void enqueue_pending_recorder(MemRecorder* rec);
  static MemRecorder* get_pending_recorders();
  static void delete_all_pending_recorders();

  // write a memory tracking record in recorder
  static void write_tracking_record(address addr, MEMFLAGS type,
    size_t size, jint seq, address pc, JavaThread* thread);

  static bool is_single_threaded_bootstrap() {
    return _state == NMT_bootstrapping_single_thread;
  }

  static void check_NMT_load(Thread* thr) {
    assert(thr != NULL, "Sanity check");
    if (_slowdown_calling_thread && thr != _worker_thread) {
      os::yield_all();
    }
  }

  static void inc_pending_op_count() {
    Atomic::inc(&_pending_op_count);
  }

  static void dec_pending_op_count() {
    Atomic::dec(&_pending_op_count);
    assert(_pending_op_count >= 0, "Sanity check");
  }


 private:
  // retrieve a pooled memory record or create new one if there is not
  // one available
  static MemRecorder* get_new_or_pooled_instance();
  static void create_memory_record(address addr, MEMFLAGS type,
                   size_t size, address pc, Thread* thread);
  static void create_record_in_recorder(address addr, MEMFLAGS type,
                   size_t size, address pc, JavaThread* thread);

  static void set_current_processing_generation(unsigned long generation) {
    _worker_thread_idle = false;
    _processing_generation = generation;
  }

  static void report_worker_idle() {
    _worker_thread_idle = true;
  }

 private:
  // global memory snapshot
  static MemSnapshot*     _snapshot;

  // a memory baseline of snapshot
  static MemBaseline      _baseline;

  // query lock
  static Mutex*           _query_lock;

  // a thread can start to allocate memory before it is attached
  // to VM 'Thread', those memory activities are recorded here.
  // ThreadCritical is required to guard this global recorder.
  static MemRecorder* volatile _global_recorder;

  // main thread id
  debug_only(static intx   _main_thread_tid;)

  // pending recorders to be merged
  static MemRecorder* volatile     _merge_pending_queue;

  NOT_PRODUCT(static volatile jint   _pending_recorder_count;)

  // pooled memory recorders
  static MemRecorder* volatile     _pooled_recorders;

  // memory recorder pool management, uses following
  // counter to determine if a released memory recorder
  // should be pooled

  // latest thread count
  static int               _thread_count;
  // pooled recorder count
  static volatile jint     _pooled_recorder_count;


  // worker thread to merge pending recorders into snapshot
  static MemTrackWorker*  _worker_thread;

  // how many safepoints we skipped without entering sync point
  static int              _sync_point_skip_count;

  // if the tracker is properly intialized
  static bool             _is_tracker_ready;
  // tracking level (off, summary and detail)
  static enum NMTLevel    _tracking_level;

  // current nmt state
  static volatile enum NMTStates   _state;
  // the reason for shutting down nmt
  static enum ShutdownReason       _reason;
  // the generation that NMT is processing
  static volatile unsigned long    _processing_generation;
  // although NMT is still procesing current generation, but
  // there is not more recorder to process, set idle state
  static volatile bool             _worker_thread_idle;

  // if NMT should slow down calling thread to allow
  // worker thread to catch up
  static volatile bool             _slowdown_calling_thread;

  // pending memory op count.
  // Certain memory ops need to pre-reserve sequence number
  // before memory operation can happen to avoid race condition.
  // See MemTracker::Tracker for detail
  static volatile jint             _pending_op_count;
};

#endif // !INCLUDE_NMT

#endif // SHARE_VM_SERVICES_MEM_TRACKER_HPP
