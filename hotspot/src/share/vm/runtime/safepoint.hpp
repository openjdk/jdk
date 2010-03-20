/*
 * Copyright 1997-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

//
// Safepoint synchronization
////
// The VMThread or CMS_thread uses the SafepointSynchronize::begin/end
// methods to enter/exit a safepoint region. The begin method will roll
// all JavaThreads forward to a safepoint.
//
// JavaThreads must use the ThreadSafepointState abstraction (defined in
// thread.hpp) to indicate that that they are at a safepoint.
//
// The Mutex/Condition variable and ObjectLocker classes calls the enter/
// exit safepoint methods, when a thread is blocked/restarted. Hence, all mutex exter/
// exit points *must* be at a safepoint.


class ThreadSafepointState;
class SnippetCache;
class nmethod;

//
// Implements roll-forward to safepoint (safepoint synchronization)
//
class SafepointSynchronize : AllStatic {
 public:
  enum SynchronizeState {
      _not_synchronized = 0,                   // Threads not synchronized at a safepoint
                                               // Keep this value 0. See the coment in do_call_back()
      _synchronizing    = 1,                   // Synchronizing in progress
      _synchronized     = 2                    // All Java threads are stopped at a safepoint. Only VM thread is running
  };

  enum SafepointingThread {
      _null_thread  = 0,
      _vm_thread    = 1,
      _other_thread = 2
  };

  enum SafepointTimeoutReason {
    _spinning_timeout = 0,
    _blocking_timeout = 1
  };

  typedef struct {
    float  _time_stamp;                        // record when the current safepoint occurs in seconds
    int    _vmop_type;                         // type of VM operation triggers the safepoint
    int    _nof_total_threads;                 // total number of Java threads
    int    _nof_initial_running_threads;       // total number of initially seen running threads
    int    _nof_threads_wait_to_block;         // total number of threads waiting for to block
    bool   _page_armed;                        // true if polling page is armed, false otherwise
    int    _nof_threads_hit_page_trap;         // total number of threads hitting the page trap
    jlong  _time_to_spin;                      // total time in millis spent in spinning
    jlong  _time_to_wait_to_block;             // total time in millis spent in waiting for to block
    jlong  _time_to_do_cleanups;               // total time in millis spent in performing cleanups
    jlong  _time_to_sync;                      // total time in millis spent in getting to _synchronized
    jlong  _time_to_exec_vmop;                 // total time in millis spent in vm operation itself
  } SafepointStats;

 private:
  static volatile SynchronizeState _state;     // Threads might read this flag directly, without acquireing the Threads_lock
  static volatile int _waiting_to_block;       // number of threads we are waiting for to block

  // This counter is used for fast versions of jni_Get<Primitive>Field.
  // An even value means there is no ongoing safepoint operations.
  // The counter is incremented ONLY at the beginning and end of each
  // safepoint. The fact that Threads_lock is held throughout each pair of
  // increments (at the beginning and end of each safepoint) guarantees
  // race freedom.
public:
  static volatile int _safepoint_counter;
private:
  static long       _end_of_last_safepoint;     // Time of last safepoint in milliseconds

  // statistics
  static jlong            _safepoint_begin_time;     // time when safepoint begins
  static SafepointStats*  _safepoint_stats;          // array of SafepointStats struct
  static int              _cur_stat_index;           // current index to the above array
  static julong           _safepoint_reasons[];      // safepoint count for each VM op
  static julong           _coalesced_vmop_count;     // coalesced vmop count
  static jlong            _max_sync_time;            // maximum sync time in nanos
  static jlong            _max_vmop_time;            // maximum vm operation time in nanos
  static float            _ts_of_current_safepoint;  // time stamp of current safepoint in seconds

  static void begin_statistics(int nof_threads, int nof_running);
  static void update_statistics_on_spin_end();
  static void update_statistics_on_sync_end(jlong end_time);
  static void update_statistics_on_cleanup_end(jlong end_time);
  static void end_statistics(jlong end_time);
  static void print_statistics();
  inline static void inc_page_trap_count() {
    Atomic::inc(&_safepoint_stats[_cur_stat_index]._nof_threads_hit_page_trap);
  }

  // For debug long safepoint
  static void print_safepoint_timeout(SafepointTimeoutReason timeout_reason);

public:

  // Main entry points

  // Roll all threads forward to safepoint. Must be called by the
  // VMThread or CMS_thread.
  static void begin();
  static void end();                    // Start all suspended threads again...

  static bool safepoint_safe(JavaThread *thread, JavaThreadState state);

  // Query
  inline static bool is_at_safepoint()   { return _state == _synchronized;  }
  inline static bool is_synchronizing()  { return _state == _synchronizing;  }

  inline static bool do_call_back() {
    return (_state != _not_synchronized);
  }

  // Called when a thread volantary blocks
  static void   block(JavaThread *thread);
  static void   signal_thread_at_safepoint()              { _waiting_to_block--; }

  // Exception handling for page polling
  static void handle_polling_page_exception(JavaThread *thread);

  // VM Thread interface for determining safepoint rate
  static long last_non_safepoint_interval() {
    return os::javaTimeMillis() - _end_of_last_safepoint;
  }
  static bool is_cleanup_needed();
  static void do_cleanup_tasks();

  // debugging
  static void print_state()                                PRODUCT_RETURN;
  static void safepoint_msg(const char* format, ...)       PRODUCT_RETURN;

  static void deferred_initialize_stat();
  static void print_stat_on_exit();
  inline static void inc_vmop_coalesced_count() { _coalesced_vmop_count++; }

  static void set_is_at_safepoint()                        { _state = _synchronized; }
  static void set_is_not_at_safepoint()                    { _state = _not_synchronized; }

  // assembly support
  static address address_of_state()                        { return (address)&_state; }

  static address safepoint_counter_addr()                  { return (address)&_safepoint_counter; }
};

// State class for a thread suspended at a safepoint
class ThreadSafepointState: public CHeapObj {
 public:
  // These states are maintained by VM thread while threads are being brought
  // to a safepoint.  After SafepointSynchronize::end(), they are reset to
  // _running.
  enum suspend_type {
    _running                =  0, // Thread state not yet determined (i.e., not at a safepoint yet)
    _at_safepoint           =  1, // Thread at a safepoint (f.ex., when blocked on a lock)
    _call_back              =  2  // Keep executing and wait for callback (if thread is in interpreted or vm)
  };
 private:
  volatile bool _at_poll_safepoint;  // At polling page safepoint (NOT a poll return safepoint)
  // Thread has called back the safepoint code (for debugging)
  bool                           _has_called_back;

  JavaThread *                   _thread;
  volatile suspend_type          _type;


 public:
  ThreadSafepointState(JavaThread *thread);

  // examine/roll-forward/restart
  void examine_state_of_thread();
  void roll_forward(suspend_type type);
  void restart();

  // Query
  JavaThread*  thread() const         { return _thread; }
  suspend_type type() const           { return _type; }
  bool         is_running() const     { return (_type==_running); }

  // Support for safepoint timeout (debugging)
  bool has_called_back() const                   { return _has_called_back; }
  void set_has_called_back(bool val)             { _has_called_back = val; }
  bool              is_at_poll_safepoint() { return _at_poll_safepoint; }
  void              set_at_poll_safepoint(bool val) { _at_poll_safepoint = val; }

  void handle_polling_page_exception();

  // debugging
  void print_on(outputStream* st) const;
  void print() const                        { print_on(tty); }

  // Initialize
  static void create(JavaThread *thread);
  static void destroy(JavaThread *thread);

  void safepoint_msg(const char* format, ...) {
    if (ShowSafepointMsgs) {
      va_list ap;
      va_start(ap, format);
      tty->vprint_cr(format, ap);
      va_end(ap);
    }
  }
};

//
// CounterDecay
//
// Interates through invocation counters and decrements them. This
// is done at each safepoint.
//
class CounterDecay : public AllStatic {
  static jlong _last_timestamp;
 public:
  static  void decay();
  static  bool is_decay_needed() { return (os::javaTimeMillis() - _last_timestamp) > CounterDecayMinIntervalLength; }
};
