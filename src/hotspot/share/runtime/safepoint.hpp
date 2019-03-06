/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_SAFEPOINT_HPP
#define SHARE_RUNTIME_SAFEPOINT_HPP

#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmOperations.hpp"
#include "utilities/ostream.hpp"
#include "utilities/waitBarrier.hpp"

//
// Safepoint synchronization
////
// The VMThread uses the SafepointSynchronize::begin/end
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

//
// Implements roll-forward to safepoint (safepoint synchronization)
//
class SafepointSynchronize : AllStatic {
 public:
  enum SynchronizeState {
      _not_synchronized = 0,                   // Threads not synchronized at a safepoint. Keep this value 0.
      _synchronizing    = 1,                   // Synchronizing in progress
      _synchronized     = 2                    // All Java threads are running in native, blocked in OS or stopped at safepoint.
                                               // VM thread and any NonJavaThread may be running.
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
  friend class SafepointMechanism;
  friend class ThreadSafepointState;
  friend class HandshakeState;

  // Threads might read this flag directly, without acquiring the Threads_lock:
  static volatile SynchronizeState _state;
  // Number of threads we are waiting for to block:
  static int              _waiting_to_block;
  // Counts the number of active critical natives during the safepoint:
  static int              _current_jni_active_count;

  // This counter is used for fast versions of jni_Get<Primitive>Field.
  // An even value means there are no ongoing safepoint operations.
  // The counter is incremented ONLY at the beginning and end of each
  // safepoint.
  static volatile uint64_t _safepoint_counter;

  // JavaThreads that need to block for the safepoint will stop on the
  // _wait_barrier, where they can quickly be started again.
  static WaitBarrier* _wait_barrier;
  static long         _end_of_last_safepoint;     // Time of last safepoint in milliseconds
  static julong       _coalesced_vmop_count;     // coalesced vmop count

  // Statistics
  static void begin_statistics(int nof_threads, int nof_running);
  static void update_statistics_on_spin_end();
  static void update_statistics_on_sync_end(jlong end_time);
  static void update_statistics_on_cleanup_end(jlong end_time);
  static void end_statistics(jlong end_time);
  static void print_statistics();

  // For debug long safepoint
  static void print_safepoint_timeout();

  // Helper methods for safepoint procedure:
  static void arm_safepoint();
  static int synchronize_threads(jlong safepoint_limit_time, int nof_threads, int* initial_running);
  static void disarm_safepoint();
  static void increment_jni_active_count();
  static void decrement_waiting_to_block();

  // Used in safepoint_safe to do a stable load of the thread state.
  static bool try_stable_load_state(JavaThreadState *state,
                                    JavaThread *thread,
                                    uint64_t safepoint_count);

  // Called when a thread voluntarily blocks
  static void block(JavaThread *thread);

  // Called from VMThread during handshakes.
  // If true the VMThread may safely process the handshake operation for the JavaThread.
  static bool handshake_safe(JavaThread *thread);

public:

  static void init(Thread* vmthread);

  // Roll all threads forward to safepoint. Must be called by the VMThread.
  static void begin();
  static void end();                    // Start all suspended threads again...

  // The value for a not set safepoint id.
  static const uint64_t InactiveSafepointCounter;

  // Query
  static bool is_at_safepoint()                   { return _state == _synchronized; }
  static bool is_synchronizing()                  { return _state == _synchronizing; }
  static uint64_t safepoint_counter()             { return _safepoint_counter; }
  static bool is_same_safepoint(uint64_t counter) { return (SafepointSynchronize::safepoint_counter() - counter) < 2; }
  // Exception handling for page polling
  static void handle_polling_page_exception(JavaThread *thread);

  static bool is_cleanup_needed();
  static void do_cleanup_tasks();

  static void set_is_at_safepoint()             { _state = _synchronized; }
  static void set_is_not_at_safepoint()         { _state = _not_synchronized; }

  // Assembly support
  static address address_of_state()             { return (address)&_state; }

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
class ThreadSafepointState: public CHeapObj<mtThread> {
 private:
  // At polling page safepoint (NOT a poll return safepoint):
  volatile bool                   _at_poll_safepoint;
  JavaThread*                     _thread;
  bool                            _safepoint_safe;
  volatile uint64_t               _safepoint_id;
  JavaThreadState                 _orig_thread_state;

  ThreadSafepointState*           _next;

  void account_safe_thread();

 public:
  ThreadSafepointState(JavaThread *thread);

  // Linked list support:
  ThreadSafepointState* get_next() const { return _next; }
  void set_next(ThreadSafepointState* value) { _next = value; }
  ThreadSafepointState** next_ptr() { return &_next; }

  // examine/restart
  void examine_state_of_thread(uint64_t safepoint_count);
  void restart();

  // Query
  JavaThread*  thread() const         { return _thread; }
  bool         is_running() const     { return !_safepoint_safe; }

  uint64_t get_safepoint_id() const;
  void     reset_safepoint_id();
  void     set_safepoint_id(uint64_t sid);

  JavaThreadState orig_thread_state() const { return _orig_thread_state; }

  // Support for safepoint timeout (debugging)
  bool is_at_poll_safepoint()           { return _at_poll_safepoint; }
  void set_at_poll_safepoint(bool val)  { _at_poll_safepoint = val; }

  void handle_polling_page_exception();

  // debugging
  void print_on(outputStream* st) const;
  void print() const                        { print_on(tty); }

  // Initialize
  static void create(JavaThread *thread);
  static void destroy(JavaThread *thread);
};

class SafepointTracing : public AllStatic {
private:
  // Absolute
  static jlong _last_safepoint_begin_time_ns;
  static jlong _last_safepoint_sync_time_ns;
  static jlong _last_safepoint_cleanup_time_ns;
  static jlong _last_safepoint_end_time_ns;
  // amount of ms since epoch
  static jlong _last_safepoint_end_time_epoch_ms;
  // Relative
  static jlong _last_app_time_ns;

  static int _nof_threads;
  static int _nof_running;
  static int _page_trap;

  static VM_Operation::VMOp_Type _current_type;
  static jlong     _max_sync_time;
  static jlong     _max_vmop_time;
  static uint64_t  _op_count[VM_Operation::VMOp_Terminating];

  static void statistics_log();

public:
  static void init();

  static void begin(VM_Operation::VMOp_Type type);
  static void synchronized(int nof_threads, int nof_running, int traps);
  static void cleanup();
  static void end();

  static void statistics_exit_log();

  static jlong time_since_last_safepoint_ms() {
    return (os::javaTimeNanos() - _last_safepoint_end_time_ns) / (NANOUNITS / MILLIUNITS);
  }

  static jlong end_of_last_safepoint_epoch_ms() {
    return _last_safepoint_end_time_epoch_ms;
  }

  static jlong start_of_safepoint() {
    return _last_safepoint_begin_time_ns;
  }
};

#endif // SHARE_RUNTIME_SAFEPOINT_HPP
