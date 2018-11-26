/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_SAFEPOINT_HPP
#define SHARE_VM_RUNTIME_SAFEPOINT_HPP

#include "memory/allocation.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

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
class JavaThread;

//
// Implements roll-forward to safepoint (safepoint synchronization)
//
class SafepointSynchronize : AllStatic {
 public:
  enum SynchronizeState {
      _not_synchronized = 0,                   // Threads not synchronized at a safepoint
                                               // Keep this value 0. See the comment in do_call_back()
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

  // The enums are listed in the order of the tasks when done serially.
  enum SafepointCleanupTasks {
    SAFEPOINT_CLEANUP_DEFLATE_MONITORS,
    SAFEPOINT_CLEANUP_UPDATE_INLINE_CACHES,
    SAFEPOINT_CLEANUP_COMPILATION_POLICY,
    SAFEPOINT_CLEANUP_SYMBOL_TABLE_REHASH,
    SAFEPOINT_CLEANUP_STRING_TABLE_REHASH,
    SAFEPOINT_CLEANUP_CLD_PURGE,
    SAFEPOINT_CLEANUP_SYSTEM_DICTIONARY_RESIZE,
    // Leave this one last.
    SAFEPOINT_CLEANUP_NUM_TASKS
  };

 private:
  static volatile SynchronizeState _state;     // Threads might read this flag directly, without acquiring the Threads_lock
  static volatile int _waiting_to_block;       // number of threads we are waiting for to block
  static int _current_jni_active_count;        // Counts the number of active critical natives during the safepoint
  static int _defer_thr_suspend_loop_count;    // Iterations before blocking VM threads

  // This counter is used for fast versions of jni_Get<Primitive>Field.
  // An even value means there is no ongoing safepoint operations.
  // The counter is incremented ONLY at the beginning and end of each
  // safepoint. The fact that Threads_lock is held throughout each pair of
  // increments (at the beginning and end of each safepoint) guarantees
  // race freedom.
  static volatile uint64_t _safepoint_counter;

private:
  static long              _end_of_last_safepoint;     // Time of last safepoint in milliseconds
  static julong            _coalesced_vmop_count;     // coalesced vmop count

  // Statistics
  static void begin_statistics(int nof_threads, int nof_running);
  static void update_statistics_on_spin_end();
  static void update_statistics_on_sync_end(jlong end_time);
  static void update_statistics_on_cleanup_end(jlong end_time);
  static void end_statistics(jlong end_time);
  static void print_statistics();

  // For debug long safepoint
  static void print_safepoint_timeout(SafepointTimeoutReason timeout_reason);

public:

  // Main entry points

  // Roll all threads forward to safepoint. Must be called by the
  // VMThread or CMS_thread.
  static void begin();
  static void end();                    // Start all suspended threads again...

  static bool safepoint_safe(JavaThread *thread, JavaThreadState state);

  static void check_for_lazy_critical_native(JavaThread *thread, JavaThreadState state);

  // Query
  inline static bool is_at_safepoint()       { return _state == _synchronized; }
  inline static bool is_synchronizing()      { return _state == _synchronizing; }
  inline static uint64_t safepoint_counter() { return _safepoint_counter; }

  inline static void increment_jni_active_count() {
    assert_locked_or_safepoint(Safepoint_lock);
    _current_jni_active_count++;
  }

private:
  inline static bool do_call_back() {
    return (_state != _not_synchronized);
  }

  // Called when a thread voluntarily blocks
  static void   block(JavaThread *thread);

  friend class SafepointMechanism;

public:
  static void   signal_thread_at_safepoint()              { _waiting_to_block--; }

  // Exception handling for page polling
  static void handle_polling_page_exception(JavaThread *thread);

  // VM Thread interface for determining safepoint rate
  static long last_non_safepoint_interval() {
    return os::javaTimeMillis() - _end_of_last_safepoint;
  }
  static long end_of_last_safepoint() {
    return _end_of_last_safepoint;
  }
  static bool is_cleanup_needed();
  static void do_cleanup_tasks();

  static void print_stat_on_exit();
  inline static void inc_vmop_coalesced_count() { _coalesced_vmop_count++; }

  static void set_is_at_safepoint()                        { _state = _synchronized; }
  static void set_is_not_at_safepoint()                    { _state = _not_synchronized; }

  // Assembly support
  static address address_of_state()                        { return (address)&_state; }

  // Only used for making sure that no safepoint has happened in
  // JNI_FastGetField. Therefore only the low 32-bits are needed
  // even if this is a 64-bit counter.
  static address safepoint_counter_addr() {
#ifdef VM_LITTLE_ENDIAN
    return (address)&_safepoint_counter;
#else /* BIG */
    // Return pointer to the 32 LSB:
    return (address) (((uint32_t*)(&_safepoint_counter)) + 1);
#endif
  }
};

// Some helper assert macros for safepoint checks.

#define assert_at_safepoint()                                           \
  assert(SafepointSynchronize::is_at_safepoint(), "should be at a safepoint")

#define assert_at_safepoint_msg(...)                                    \
  assert(SafepointSynchronize::is_at_safepoint(), __VA_ARGS__)

#define assert_not_at_safepoint()                                       \
  assert(!SafepointSynchronize::is_at_safepoint(), "should not be at a safepoint")

#define assert_not_at_safepoint_msg(...)                                \
  assert(!SafepointSynchronize::is_at_safepoint(), __VA_ARGS__)

// State class for a thread suspended at a safepoint
class ThreadSafepointState: public CHeapObj<mtInternal> {
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
  JavaThreadState                _orig_thread_state;


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
  JavaThreadState orig_thread_state() const { return _orig_thread_state; }

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
};



#endif // SHARE_VM_RUNTIME_SAFEPOINT_HPP
