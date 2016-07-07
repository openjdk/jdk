/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_OBJECTMONITOR_HPP
#define SHARE_VM_RUNTIME_OBJECTMONITOR_HPP

#include "memory/padded.hpp"
#include "runtime/os.hpp"
#include "runtime/park.hpp"
#include "runtime/perfData.hpp"

// ObjectWaiter serves as a "proxy" or surrogate thread.
// TODO-FIXME: Eliminate ObjectWaiter and use the thread-specific
// ParkEvent instead.  Beware, however, that the JVMTI code
// knows about ObjectWaiters, so we'll have to reconcile that code.
// See next_waiter(), first_waiter(), etc.

class ObjectWaiter : public StackObj {
 public:
  enum TStates { TS_UNDEF, TS_READY, TS_RUN, TS_WAIT, TS_ENTER, TS_CXQ };
  enum Sorted  { PREPEND, APPEND, SORTED };
  ObjectWaiter * volatile _next;
  ObjectWaiter * volatile _prev;
  Thread*       _thread;
  jlong         _notifier_tid;
  ParkEvent *   _event;
  volatile int  _notified;
  volatile TStates TState;
  Sorted        _Sorted;           // List placement disposition
  bool          _active;           // Contention monitoring is enabled
 public:
  ObjectWaiter(Thread* thread);

  void wait_reenter_begin(ObjectMonitor *mon);
  void wait_reenter_end(ObjectMonitor *mon);
};

// forward declaration to avoid include tracing.hpp
class EventJavaMonitorWait;

// The ObjectMonitor class implements the heavyweight version of a
// JavaMonitor. The lightweight BasicLock/stack lock version has been
// inflated into an ObjectMonitor. This inflation is typically due to
// contention or use of Object.wait().
//
// WARNING: This is a very sensitive and fragile class. DO NOT make any
// changes unless you are fully aware of the underlying semantics.
//
// Class JvmtiRawMonitor currently inherits from ObjectMonitor so
// changes in this class must be careful to not break JvmtiRawMonitor.
// These two subsystems should be separated.
//
// ObjectMonitor Layout Overview/Highlights/Restrictions:
//
// - The _header field must be at offset 0 because the displaced header
//   from markOop is stored there. We do not want markOop.hpp to include
//   ObjectMonitor.hpp to avoid exposing ObjectMonitor everywhere. This
//   means that ObjectMonitor cannot inherit from any other class nor can
//   it use any virtual member functions. This restriction is critical to
//   the proper functioning of the VM.
// - The _header and _owner fields should be separated by enough space
//   to avoid false sharing due to parallel access by different threads.
//   This is an advisory recommendation.
// - The general layout of the fields in ObjectMonitor is:
//     _header
//     <lightly_used_fields>
//     <optional padding>
//     _owner
//     <remaining_fields>
// - The VM assumes write ordering and machine word alignment with
//   respect to the _owner field and the <remaining_fields> that can
//   be read in parallel by other threads.
// - Generally fields that are accessed closely together in time should
//   be placed proximally in space to promote data cache locality. That
//   is, temporal locality should condition spatial locality.
// - We have to balance avoiding false sharing with excessive invalidation
//   from coherence traffic. As such, we try to cluster fields that tend
//   to be _written_ at approximately the same time onto the same data
//   cache line.
// - We also have to balance the natural tension between minimizing
//   single threaded capacity misses with excessive multi-threaded
//   coherency misses. There is no single optimal layout for both
//   single-threaded and multi-threaded environments.
//
// - See ObjectMonitor::sanity_checks() for how critical restrictions are
//   enforced and advisory recommendations are reported.
// - Adjacent ObjectMonitors should be separated by enough space to avoid
//   false sharing. This is handled by the ObjectMonitor allocation code
//   in synchronizer.cpp. Also see ObjectSynchronizer::sanity_checks().
//
// Futures notes:
//   - Separating _owner from the <remaining_fields> by enough space to
//     avoid false sharing might be profitable. Given
//     http://blogs.oracle.com/dave/entry/cas_and_cache_trivia_invalidate
//     we know that the CAS in monitorenter will invalidate the line
//     underlying _owner. We want to avoid an L1 data cache miss on that
//     same line for monitorexit. Putting these <remaining_fields>:
//     _recursions, _EntryList, _cxq, and _succ, all of which may be
//     fetched in the inflated unlock path, on a different cache line
//     would make them immune to CAS-based invalidation from the _owner
//     field.
//
//   - The _recursions field should be of type int, or int32_t but not
//     intptr_t. There's no reason to use a 64-bit type for this field
//     in a 64-bit JVM.

class ObjectMonitor {
 public:
  enum {
    OM_OK,                    // no error
    OM_SYSTEM_ERROR,          // operating system error
    OM_ILLEGAL_MONITOR_STATE, // IllegalMonitorStateException
    OM_INTERRUPTED,           // Thread.interrupt()
    OM_TIMED_OUT              // Object.wait() timed out
  };

 private:
  friend class ObjectSynchronizer;
  friend class ObjectWaiter;
  friend class VMStructs;

  volatile markOop   _header;       // displaced object header word - mark
  void*     volatile _object;       // backward object pointer - strong root
 public:
  ObjectMonitor *    FreeNext;      // Free list linkage
 private:
  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE,
                        sizeof(volatile markOop) + sizeof(void * volatile) +
                        sizeof(ObjectMonitor *));
 protected:                         // protected for JvmtiRawMonitor
  void *  volatile _owner;          // pointer to owning thread OR BasicLock
  volatile jlong _previous_owner_tid;  // thread id of the previous owner of the monitor
  volatile intptr_t  _recursions;   // recursion count, 0 for first entry
  ObjectWaiter * volatile _EntryList; // Threads blocked on entry or reentry.
                                      // The list is actually composed of WaitNodes,
                                      // acting as proxies for Threads.
 private:
  ObjectWaiter * volatile _cxq;     // LL of recently-arrived threads blocked on entry.
  Thread * volatile _succ;          // Heir presumptive thread - used for futile wakeup throttling
  Thread * volatile _Responsible;

  volatile int _Spinner;            // for exit->spinner handoff optimization
  volatile int _SpinDuration;

  volatile jint  _count;            // reference count to prevent reclamation/deflation
                                    // at stop-the-world time.  See deflate_idle_monitors().
                                    // _count is approximately |_WaitSet| + |_EntryList|
 protected:
  ObjectWaiter * volatile _WaitSet; // LL of threads wait()ing on the monitor
  volatile jint  _waiters;          // number of waiting threads
 private:
  volatile int _WaitSetLock;        // protects Wait Queue - simple spinlock

 public:
  static void Initialize();

  // Only perform a PerfData operation if the PerfData object has been
  // allocated and if the PerfDataManager has not freed the PerfData
  // objects which can happen at normal VM shutdown.
  //
  #define OM_PERFDATA_OP(f, op_str)              \
    do {                                         \
      if (ObjectMonitor::_sync_ ## f != NULL &&  \
          PerfDataManager::has_PerfData()) {     \
        ObjectMonitor::_sync_ ## f->op_str;      \
      }                                          \
    } while (0)

  static PerfCounter * _sync_ContendedLockAttempts;
  static PerfCounter * _sync_FutileWakeups;
  static PerfCounter * _sync_Parks;
  static PerfCounter * _sync_EmptyNotifications;
  static PerfCounter * _sync_Notifications;
  static PerfCounter * _sync_SlowEnter;
  static PerfCounter * _sync_SlowExit;
  static PerfCounter * _sync_SlowNotify;
  static PerfCounter * _sync_SlowNotifyAll;
  static PerfCounter * _sync_FailedSpins;
  static PerfCounter * _sync_SuccessfulSpins;
  static PerfCounter * _sync_PrivateA;
  static PerfCounter * _sync_PrivateB;
  static PerfCounter * _sync_MonInCirculation;
  static PerfCounter * _sync_MonScavenged;
  static PerfCounter * _sync_Inflations;
  static PerfCounter * _sync_Deflations;
  static PerfLongVariable * _sync_MonExtant;

  static int Knob_ExitRelease;
  static int Knob_Verbose;
  static int Knob_VerifyInUse;
  static int Knob_VerifyMatch;
  static int Knob_SpinLimit;

  void* operator new (size_t size) throw() {
    return AllocateHeap(size, mtInternal);
  }
  void* operator new[] (size_t size) throw() {
    return operator new (size);
  }
  void operator delete(void* p) {
    FreeHeap(p);
  }
  void operator delete[] (void *p) {
    operator delete(p);
  }

  // TODO-FIXME: the "offset" routines should return a type of off_t instead of int ...
  // ByteSize would also be an appropriate type.
  static int header_offset_in_bytes()      { return offset_of(ObjectMonitor, _header); }
  static int object_offset_in_bytes()      { return offset_of(ObjectMonitor, _object); }
  static int owner_offset_in_bytes()       { return offset_of(ObjectMonitor, _owner); }
  static int count_offset_in_bytes()       { return offset_of(ObjectMonitor, _count); }
  static int recursions_offset_in_bytes()  { return offset_of(ObjectMonitor, _recursions); }
  static int cxq_offset_in_bytes()         { return offset_of(ObjectMonitor, _cxq); }
  static int succ_offset_in_bytes()        { return offset_of(ObjectMonitor, _succ); }
  static int EntryList_offset_in_bytes()   { return offset_of(ObjectMonitor, _EntryList); }

  // ObjectMonitor references can be ORed with markOopDesc::monitor_value
  // as part of the ObjectMonitor tagging mechanism. When we combine an
  // ObjectMonitor reference with an offset, we need to remove the tag
  // value in order to generate the proper address.
  //
  // We can either adjust the ObjectMonitor reference and then add the
  // offset or we can adjust the offset that is added to the ObjectMonitor
  // reference. The latter avoids an AGI (Address Generation Interlock)
  // stall so the helper macro adjusts the offset value that is returned
  // to the ObjectMonitor reference manipulation code:
  //
  #define OM_OFFSET_NO_MONITOR_VALUE_TAG(f) \
    ((ObjectMonitor::f ## _offset_in_bytes()) - markOopDesc::monitor_value)

  markOop   header() const;
  void      set_header(markOop hdr);

  intptr_t is_busy() const {
    // TODO-FIXME: merge _count and _waiters.
    // TODO-FIXME: assert _owner == null implies _recursions = 0
    // TODO-FIXME: assert _WaitSet != null implies _count > 0
    return _count|_waiters|intptr_t(_owner)|intptr_t(_cxq)|intptr_t(_EntryList);
  }

  intptr_t  is_entered(Thread* current) const;

  void*     owner() const;
  void      set_owner(void* owner);

  jint      waiters() const;

  jint      count() const;
  void      set_count(jint count);
  jint      contentions() const;
  intptr_t  recursions() const                                         { return _recursions; }

  // JVM/TI GetObjectMonitorUsage() needs this:
  ObjectWaiter* first_waiter()                                         { return _WaitSet; }
  ObjectWaiter* next_waiter(ObjectWaiter* o)                           { return o->_next; }
  Thread* thread_of_waiter(ObjectWaiter* o)                            { return o->_thread; }

 protected:
  // We don't typically expect or want the ctors or dtors to run.
  // normal ObjectMonitors are type-stable and immortal.
  ObjectMonitor() { ::memset((void *)this, 0, sizeof(*this)); }

  ~ObjectMonitor() {
    // TODO: Add asserts ...
    // _cxq == 0 _succ == NULL _owner == NULL _waiters == 0
    // _count == 0 _EntryList  == NULL etc
  }

 private:
  void Recycle() {
    // TODO: add stronger asserts ...
    // _cxq == 0 _succ == NULL _owner == NULL _waiters == 0
    // _count == 0 EntryList  == NULL
    // _recursions == 0 _WaitSet == NULL
    assert(((is_busy()|_recursions) == 0), "freeing inuse monitor");
    _succ          = NULL;
    _EntryList     = NULL;
    _cxq           = NULL;
    _WaitSet       = NULL;
    _recursions    = 0;
  }

 public:

  void*     object() const;
  void*     object_addr();
  void      set_object(void* obj);

  bool      check(TRAPS);       // true if the thread owns the monitor.
  void      check_slow(TRAPS);
  void      clear();
  static void sanity_checks();  // public for -XX:+ExecuteInternalVMTests
                                // in PRODUCT for -XX:SyncKnobs=Verbose=1
#ifndef PRODUCT
  void      verify();
  void      print();
#endif

  void      enter(TRAPS);
  void      exit(bool not_suspended, TRAPS);
  void      wait(jlong millis, bool interruptable, TRAPS);
  void      notify(TRAPS);
  void      notifyAll(TRAPS);

// Use the following at your own risk
  intptr_t  complete_exit(TRAPS);
  void      reenter(intptr_t recursions, TRAPS);

 private:
  void      AddWaiter(ObjectWaiter * waiter);
  static    void DeferredInitialize();
  void      INotify(Thread * Self);
  ObjectWaiter * DequeueWaiter();
  void      DequeueSpecificWaiter(ObjectWaiter * waiter);
  void      EnterI(TRAPS);
  void      ReenterI(Thread * Self, ObjectWaiter * SelfNode);
  void      UnlinkAfterAcquire(Thread * Self, ObjectWaiter * SelfNode);
  int       TryLock(Thread * Self);
  int       NotRunnable(Thread * Self, Thread * Owner);
  int       TrySpin(Thread * Self);
  void      ExitEpilog(Thread * Self, ObjectWaiter * Wakee);
  bool      ExitSuspendEquivalent(JavaThread * Self);
  void      post_monitor_wait_event(EventJavaMonitorWait * event,
                                    jlong notifier_tid,
                                    jlong timeout,
                                    bool timedout);

};

#undef TEVENT
#define TEVENT(nom) { if (SyncVerbose) FEVENT(nom); }

#define FEVENT(nom)                             \
  {                                             \
    static volatile int ctr = 0;                \
    int v = ++ctr;                              \
    if ((v & (v - 1)) == 0) {                   \
      tty->print_cr("INFO: " #nom " : %d", v);  \
      tty->flush();                             \
    }                                           \
  }

#undef  TEVENT
#define TEVENT(nom) {;}


#endif // SHARE_VM_RUNTIME_OBJECTMONITOR_HPP
