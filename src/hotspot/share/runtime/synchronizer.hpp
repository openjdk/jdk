/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_SYNCHRONIZER_HPP
#define SHARE_RUNTIME_SYNCHRONIZER_HPP

#include "memory/padded.hpp"
#include "oops/markWord.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/handles.hpp"
#include "runtime/perfData.hpp"

class ObjectMonitor;
class ThreadsList;

#ifndef OM_CACHE_LINE_SIZE
// Use DEFAULT_CACHE_LINE_SIZE if not already specified for
// the current build platform.
#define OM_CACHE_LINE_SIZE DEFAULT_CACHE_LINE_SIZE
#endif

typedef PaddedEnd<ObjectMonitor, OM_CACHE_LINE_SIZE> PaddedObjectMonitor;

struct DeflateMonitorCounters {
  volatile int n_in_use;              // currently associated with objects
  volatile int n_in_circulation;      // extant
  volatile int n_scavenged;           // reclaimed (global and per-thread)
  volatile int per_thread_scavenged;  // per-thread scavenge total
           double per_thread_times;   // per-thread scavenge times
};

class ObjectSynchronizer : AllStatic {
  friend class VMStructs;
 public:
  typedef enum {
    owner_self,
    owner_none,
    owner_other
  } LockOwnership;

  typedef enum {
    inflate_cause_vm_internal = 0,
    inflate_cause_monitor_enter = 1,
    inflate_cause_wait = 2,
    inflate_cause_notify = 3,
    inflate_cause_hash_code = 4,
    inflate_cause_jni_enter = 5,
    inflate_cause_jni_exit = 6,
    inflate_cause_nof = 7 // Number of causes
  } InflateCause;

  // exit must be implemented non-blocking, since the compiler cannot easily handle
  // deoptimization at monitor exit. Hence, it does not take a Handle argument.

  // This is the "slow path" version of monitor enter and exit.
  static void enter(Handle obj, BasicLock* lock, TRAPS);
  static void exit(oop obj, BasicLock* lock, Thread* THREAD);

  // Used only to handle jni locks or other unmatched monitor enter/exit
  // Internally they will use heavy weight monitor.
  static void jni_enter(Handle obj, TRAPS);
  static void jni_exit(oop obj, Thread* THREAD);

  // Handle all interpreter, compiler and jni cases
  static int  wait(Handle obj, jlong millis, TRAPS);
  static void notify(Handle obj, TRAPS);
  static void notifyall(Handle obj, TRAPS);

  static bool quick_notify(oopDesc* obj, Thread* self, bool All);
  static bool quick_enter(oop obj, Thread* self, BasicLock* Lock);

  // Special internal-use-only method for use by JVM infrastructure
  // that needs to wait() on a java-level object but that can't risk
  // throwing unexpected InterruptedExecutionExceptions.
  static void wait_uninterruptibly(Handle obj, jlong Millis, Thread* THREAD);

  // used by classloading to free classloader object lock,
  // wait on an internal lock, and reclaim original lock
  // with original recursion count
  static intx complete_exit(Handle obj, TRAPS);
  static void reenter (Handle obj, intx recursions, TRAPS);

  // thread-specific and global ObjectMonitor free list accessors
  static ObjectMonitor* om_alloc(Thread* self);
  static void om_release(Thread* self, ObjectMonitor* m,
                         bool FromPerThreadAlloc);
  static void om_flush(Thread* self);

  // Inflate light weight monitor to heavy weight monitor
  static ObjectMonitor* inflate(Thread* self, oop obj, const InflateCause cause);
  // This version is only for internal use
  static void inflate_helper(oop obj);
  static const char* inflate_cause_name(const InflateCause cause);

  // Returns the identity hash value for an oop
  // NOTE: It may cause monitor inflation
  static intptr_t identity_hash_value_for(Handle obj);
  static intptr_t FastHashCode(Thread* self, oop obj);

  // java.lang.Thread support
  static bool current_thread_holds_lock(JavaThread* thread, Handle h_obj);
  static LockOwnership query_lock_ownership(JavaThread* self, Handle h_obj);

  static JavaThread* get_lock_owner(ThreadsList * t_list, Handle h_obj);

  // JNI detach support
  static void release_monitors_owned_by_thread(TRAPS);
  static void monitors_iterate(MonitorClosure* m);

  // GC: we current use aggressive monitor deflation policy
  // Basically we deflate all monitors that are not busy.
  // An adaptive profile-based deflation policy could be used if needed
  static void deflate_idle_monitors(DeflateMonitorCounters* counters);
  static void deflate_idle_monitors_using_JT();
  static void deflate_global_idle_monitors_using_JT();
  static void deflate_per_thread_idle_monitors_using_JT(JavaThread* target);
  static void deflate_common_idle_monitors_using_JT(bool is_global, JavaThread* target);
  static void deflate_thread_local_monitors(Thread* thread, DeflateMonitorCounters* counters);
  static void prepare_deflate_idle_monitors(DeflateMonitorCounters* counters);
  static void finish_deflate_idle_monitors(DeflateMonitorCounters* counters);

  // For a given monitor list: global or per-thread, deflate idle monitors
  static int deflate_monitor_list(ObjectMonitor** list_p,
                                  int* count_p,
                                  ObjectMonitor** free_head_p,
                                  ObjectMonitor** free_tail_p);
  // For a given in-use monitor list: global or per-thread, deflate idle
  // monitors using a JavaThread.
  static int deflate_monitor_list_using_JT(ObjectMonitor** list_p,
                                           int* count_p,
                                           ObjectMonitor** free_head_p,
                                           ObjectMonitor** free_tail_p,
                                           ObjectMonitor** saved_mid_in_use_p);
  static bool deflate_monitor(ObjectMonitor* mid, oop obj,
                              ObjectMonitor** free_head_p,
                              ObjectMonitor** free_tail_p);
  static bool deflate_monitor_using_JT(ObjectMonitor* mid,
                                       ObjectMonitor** free_head_p,
                                       ObjectMonitor** free_tail_p);
  static bool is_async_deflation_needed();
  static bool is_safepoint_deflation_needed();
  static bool is_async_deflation_requested() { return _is_async_deflation_requested; }
  static bool is_special_deflation_requested() { return _is_special_deflation_requested; }
  static void set_is_async_deflation_requested(bool new_value) { _is_async_deflation_requested = new_value; }
  static void set_is_special_deflation_requested(bool new_value) { _is_special_deflation_requested = new_value; }
  static jlong time_since_last_async_deflation_ms();
  static void oops_do(OopClosure* f);
  // Process oops in thread local used monitors
  static void thread_local_used_oops_do(Thread* thread, OopClosure* f);

  // debugging
  static void audit_and_print_stats(bool on_exit);
  static void chk_free_entry(JavaThread* jt, ObjectMonitor* n,
                             outputStream * out, int *error_cnt_p);
  static void chk_global_free_list_and_count(outputStream * out,
                                             int *error_cnt_p);
  static void chk_global_wait_list_and_count(outputStream * out,
                                             int *error_cnt_p);
  static void chk_global_in_use_list_and_count(outputStream * out,
                                               int *error_cnt_p);
  static void chk_in_use_entry(JavaThread* jt, ObjectMonitor* n,
                               outputStream * out, int *error_cnt_p);
  static void chk_per_thread_in_use_list_and_count(JavaThread *jt,
                                                   outputStream * out,
                                                   int *error_cnt_p);
  static void chk_per_thread_free_list_and_count(JavaThread *jt,
                                                 outputStream * out,
                                                 int *error_cnt_p);
  static void log_in_use_monitor_details(outputStream * out);
  static int  log_monitor_list_counts(outputStream * out);
  static int  verify_objmon_isinpool(ObjectMonitor *addr) PRODUCT_RETURN0;

  static void do_safepoint_work(DeflateMonitorCounters* counters);

 private:
  friend class SynchronizerTest;

  enum { _BLOCKSIZE = 128 };
  // global list of blocks of monitors
  static PaddedObjectMonitor* g_block_list;
  static volatile bool _is_async_deflation_requested;
  static volatile bool _is_special_deflation_requested;
  static jlong         _last_async_deflation_time_ns;

  // Function to prepend new blocks to the appropriate lists:
  static void prepend_block_to_lists(PaddedObjectMonitor* new_blk);

  // Process oops in all global used monitors (i.e. moribund thread's monitors)
  static void global_used_oops_do(OopClosure* f);
  // Process oops in monitors on the given list
  static void list_oops_do(ObjectMonitor* list, OopClosure* f);

  // Support for SynchronizerTest access to GVars fields:
  static u_char* get_gvars_addr();
  static u_char* get_gvars_hc_sequence_addr();
  static size_t get_gvars_size();
  static u_char* get_gvars_stw_random_addr();
};

// ObjectLocker enforces balanced locking and can never throw an
// IllegalMonitorStateException. However, a pending exception may
// have to pass through, and we must also be able to deal with
// asynchronous exceptions. The caller is responsible for checking
// the thread's pending exception if needed.
class ObjectLocker : public StackObj {
 private:
  Thread*   _thread;
  Handle    _obj;
  BasicLock _lock;
  bool      _dolock;   // default true
 public:
  ObjectLocker(Handle obj, Thread* thread, bool do_lock = true);
  ~ObjectLocker();

  // Monitor behavior
  void wait(TRAPS)  { ObjectSynchronizer::wait(_obj, 0, CHECK); } // wait forever
  void notify_all(TRAPS)  { ObjectSynchronizer::notifyall(_obj, CHECK); }
  void wait_uninterruptibly(TRAPS) { ObjectSynchronizer::wait_uninterruptibly(_obj, 0, CHECK); }
  // complete_exit gives up lock completely, returning recursion count
  // reenter reclaims lock with original recursion count
  intx complete_exit(TRAPS)  { return ObjectSynchronizer::complete_exit(_obj, THREAD); }
  void reenter(intx recursions, TRAPS)  { ObjectSynchronizer::reenter(_obj, recursions, CHECK); }
};

#endif // SHARE_RUNTIME_SYNCHRONIZER_HPP
