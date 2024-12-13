/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_OBJECTMONITOR_HPP
#define SHARE_RUNTIME_OBJECTMONITOR_HPP

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "oops/markWord.hpp"
#include "oops/oopHandle.hpp"
#include "oops/weakHandle.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/perfDataTypes.hpp"
#include "utilities/checkedCast.hpp"

class ObjectMonitor;
class ObjectMonitorContentionMark;
class ParkEvent;
class BasicLock;
class ContinuationWrapper;


class ObjectWaiter : public CHeapObj<mtThread> {
 public:
  enum TStates : uint8_t { TS_UNDEF, TS_READY, TS_RUN, TS_WAIT, TS_ENTER, TS_CXQ };
  ObjectWaiter* volatile _next;
  ObjectWaiter* volatile _prev;
  JavaThread*     _thread;
  OopHandle      _vthread;
  ObjectMonitor* _monitor;
  uint64_t  _notifier_tid;
  int         _recursions;
  volatile TStates TState;
  volatile bool _notified;
  bool           _is_wait;
  bool        _at_reenter;
  bool       _interrupted;
  bool            _active;    // Contention monitoring is enabled
 public:
  ObjectWaiter(JavaThread* current);
  ObjectWaiter(oop vthread, ObjectMonitor* mon);
  ~ObjectWaiter();
  JavaThread* thread()      const { return _thread; }
  bool is_vthread()         const { return _thread == nullptr; }
  uint8_t state()           const { return TState; }
  ObjectMonitor* monitor()  const { return _monitor; }
  bool is_wait()            const { return _is_wait; }
  bool notified()           const { return _notified; }
  bool at_reenter()         const { return _at_reenter; }
  bool at_monitorenter()    const { return !_is_wait || _at_reenter || _notified; }
  oop vthread() const;
  void wait_reenter_begin(ObjectMonitor *mon);
  void wait_reenter_end(ObjectMonitor *mon);
};

// The ObjectMonitor class implements the heavyweight version of a
// JavaMonitor. The lightweight BasicLock/stack lock version has been
// inflated into an ObjectMonitor. This inflation is typically due to
// contention or use of Object.wait().
//
// WARNING: This is a very sensitive and fragile class. DO NOT make any
// changes unless you are fully aware of the underlying semantics.
//
// ObjectMonitor Layout Overview/Highlights/Restrictions:
//
// - The _metadata field must be at offset 0 because the displaced header
//   from markWord is stored there. We do not want markWord.hpp to include
//   ObjectMonitor.hpp to avoid exposing ObjectMonitor everywhere. This
//   means that ObjectMonitor cannot inherit from any other class nor can
//   it use any virtual member functions. This restriction is critical to
//   the proper functioning of the VM.
// - The _metadata and _owner fields should be separated by enough space
//   to avoid false sharing due to parallel access by different threads.
//   This is an advisory recommendation.
// - The general layout of the fields in ObjectMonitor is:
//     _metadata
//     <lightly_used_fields>
//     <optional padding>
//     _owner
//     <optional padding>
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
// - See TEST_VM(ObjectMonitor, sanity) gtest for how critical restrictions are
//   enforced.
// - Adjacent ObjectMonitors should be separated by enough space to avoid
//   false sharing. This is handled by the ObjectMonitor allocation code
//   in synchronizer.cpp. Also see TEST_VM(SynchronizerTest, sanity) gtest.
//
// Futures notes:
// - Separating _owner from the <remaining_fields> by enough space to
//   avoid false sharing might be profitable. Given that the CAS in
//   monitorenter will invalidate the line underlying _owner. We want
//   to avoid an L1 data cache miss on that same line for monitorexit.
//   Putting these <remaining_fields>:
//   _recursions, _EntryList, _cxq, and _succ, all of which may be
//   fetched in the inflated unlock path, on a different cache line
//   would make them immune to CAS-based invalidation from the _owner
//   field.
//
// - The _recursions field should be of type int, or int32_t but not
//   intptr_t. There's no reason to use a 64-bit type for this field
//   in a 64-bit JVM.

#define OM_CACHE_LINE_SIZE DEFAULT_CACHE_LINE_SIZE

class ObjectMonitor : public CHeapObj<mtObjectMonitor> {
  friend class ObjectSynchronizer;
  friend class ObjectWaiter;
  friend class VMStructs;
  JVMCI_ONLY(friend class JVMCIVMStructs;)

  static OopStorage* _oop_storage;

  // List of j.l.VirtualThread waiting to be unblocked by unblocker thread.
  static OopHandle _vthread_cxq_head;
  // ParkEvent of unblocker thread.
  static ParkEvent* _vthread_unparker_ParkEvent;

  // The sync code expects the metadata field to be at offset zero (0).
  // Enforced by the assert() in metadata_addr().
  // * LM_LIGHTWEIGHT with UseObjectMonitorTable:
  // Contains the _object's hashCode.
  // * LM_LEGACY, LM_MONITOR, LM_LIGHTWEIGHT without UseObjectMonitorTable:
  // Contains the displaced object header word - mark
  volatile uintptr_t _metadata;     // metadata
  WeakHandle _object;               // backward object pointer
  // Separate _metadata and _owner on different cache lines since both can
  // have busy multi-threaded access. _metadata and _object are set at initial
  // inflation. The _object does not change, so it is a good choice to share
  // its cache line with _metadata.
  DEFINE_PAD_MINUS_SIZE(0, OM_CACHE_LINE_SIZE, sizeof(_metadata) +
                        sizeof(WeakHandle));

  static const int64_t NO_OWNER = 0;
  static const int64_t ANONYMOUS_OWNER = 1;
  static const int64_t DEFLATER_MARKER = 2;

  int64_t volatile _owner;  // Either owner_id of owner, NO_OWNER, ANONYMOUS_OWNER or DEFLATER_MARKER.
  volatile uint64_t _previous_owner_tid;  // thread id of the previous owner of the monitor
  // Separate _owner and _next_om on different cache lines since
  // both can have busy multi-threaded access. _previous_owner_tid is only
  // changed by ObjectMonitor::exit() so it is a good choice to share the
  // cache line with _owner.
  DEFINE_PAD_MINUS_SIZE(1, OM_CACHE_LINE_SIZE, sizeof(void* volatile) +
                        sizeof(volatile uint64_t));
  ObjectMonitor* _next_om;          // Next ObjectMonitor* linkage
  volatile intx _recursions;        // recursion count, 0 for first entry
  ObjectWaiter* volatile _EntryList;  // Threads blocked on entry or reentry.
                                      // The list is actually composed of WaitNodes,
                                      // acting as proxies for Threads.

  ObjectWaiter* volatile _cxq;      // LL of recently-arrived threads blocked on entry.
  int64_t volatile _succ;           // Heir presumptive thread - used for futile wakeup throttling

  volatile int _SpinDuration;

  int _contentions;                 // Number of active contentions in enter(). It is used by is_busy()
                                    // along with other fields to determine if an ObjectMonitor can be
                                    // deflated. It is also used by the async deflation protocol. See
                                    // ObjectMonitor::deflate_monitor().

  ObjectWaiter* volatile _WaitSet;  // LL of threads wait()ing on the monitor
  volatile int  _waiters;           // number of waiting threads
  volatile int _WaitSetLock;        // protects Wait Queue - simple spinlock

  // Used in LM_LEGACY mode to store BasicLock* in case of inflation by contending thread.
  BasicLock* volatile _stack_locker;

 public:

  static void Initialize();
  static void Initialize2();

  static OopHandle& vthread_cxq_head() { return _vthread_cxq_head; }
  static ParkEvent* vthread_unparker_ParkEvent() { return _vthread_unparker_ParkEvent; }

  // Only perform a PerfData operation if the PerfData object has been
  // allocated and if the PerfDataManager has not freed the PerfData
  // objects which can happen at normal VM shutdown.
  //
  #define OM_PERFDATA_OP(f, op_str)                 \
    do {                                            \
      if (ObjectMonitor::_sync_ ## f != nullptr &&  \
          PerfDataManager::has_PerfData()) {        \
        ObjectMonitor::_sync_ ## f->op_str;         \
      }                                             \
    } while (0)

  static PerfCounter * _sync_ContendedLockAttempts;
  static PerfCounter * _sync_FutileWakeups;
  static PerfCounter * _sync_Parks;
  static PerfCounter * _sync_Notifications;
  static PerfCounter * _sync_Inflations;
  static PerfCounter * _sync_Deflations;
  static PerfLongVariable * _sync_MonExtant;

  static int Knob_SpinLimit;

  static ByteSize metadata_offset()    { return byte_offset_of(ObjectMonitor, _metadata); }
  static ByteSize owner_offset()       { return byte_offset_of(ObjectMonitor, _owner); }
  static ByteSize recursions_offset()  { return byte_offset_of(ObjectMonitor, _recursions); }
  static ByteSize cxq_offset()         { return byte_offset_of(ObjectMonitor, _cxq); }
  static ByteSize succ_offset()        { return byte_offset_of(ObjectMonitor, _succ); }
  static ByteSize EntryList_offset()   { return byte_offset_of(ObjectMonitor, _EntryList); }

  // ObjectMonitor references can be ORed with markWord::monitor_value
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
    ((in_bytes(ObjectMonitor::f ## _offset())) - checked_cast<int>(markWord::monitor_value))

  uintptr_t           metadata() const;
  void                set_metadata(uintptr_t value);
  volatile uintptr_t* metadata_addr();

  markWord            header() const;
  void                set_header(markWord hdr);

  intptr_t            hash() const;
  void                set_hash(intptr_t hash);

  bool is_busy() const {
    // TODO-FIXME: assert _owner == NO_OWNER implies _recursions = 0
    intptr_t ret_code = intptr_t(_waiters) | intptr_t(_cxq) | intptr_t(_EntryList);
    int cnts = contentions(); // read once
    if (cnts > 0) {
      ret_code |= intptr_t(cnts);
    }
    if (!owner_is_DEFLATER_MARKER()) {
      ret_code |= intptr_t(owner_raw());
    }
    return ret_code != 0;
  }
  const char* is_busy_to_string(stringStream* ss);

  bool is_entered(JavaThread* current) const;

  // Returns true if this OM has an owner, false otherwise.
  bool      has_owner() const;
  int64_t   owner() const;  // Returns NO_OWNER if DEFLATER_MARKER is observed.
  int64_t   owner_raw() const;

  // These methods return the value we set in _owner when acquiring
  // the monitor with the given thread/vthread, AKA owner_id.
  static int64_t owner_id_from(JavaThread* thread);
  static int64_t owner_id_from(oop vthread);

  // Returns true if owner field == DEFLATER_MARKER and false otherwise.
  bool      owner_is_DEFLATER_MARKER() const;
  // Returns true if 'this' is being async deflated and false otherwise.
  bool      is_being_async_deflated();
  // Clear _owner field; current value must match thread's owner_id.
  void      release_clear_owner(JavaThread* thread);
  // Simply set _owner field to new_value; current value must match old_value.
  void      set_owner_from_raw(int64_t old_value, int64_t new_value);
  // Same as above but uses owner_id of current as new value.
  void      set_owner_from(int64_t old_value, JavaThread* current);
  // Try to set _owner field to new_value if the current value matches
  // old_value, using Atomic::cmpxchg(). Otherwise, does not change the
  // _owner field. Returns the prior value of the _owner field.
  int64_t   try_set_owner_from_raw(int64_t old_value, int64_t new_value);
  // Same as above but uses owner_id of current as new_value.
  int64_t   try_set_owner_from(int64_t old_value, JavaThread* current);

  // Methods to check and set _succ. The successor is the thread selected
  // from _cxq/_EntryList by the current owner when releasing the monitor,
  // to run again and re-try acquiring the monitor. It is used to avoid
  // unnecessary wake-ups if there is already a successor set.
  bool      has_successor() const;
  bool      has_successor(JavaThread* thread) const;
  void      set_successor(JavaThread* thread);
  void      set_successor(oop vthread);
  void      clear_successor();
  int64_t   successor() const;

  // Returns true if _owner field == owner_id of thread, false otherwise.
  bool has_owner(JavaThread* thread) const { return owner() == owner_id_from(thread); }
  // Set _owner field to owner_id of thread; current value must be NO_OWNER.
  void set_owner(JavaThread* thread) { set_owner_from(NO_OWNER, thread); }
  // Try to set _owner field from NO_OWNER to owner_id of thread.
  bool try_set_owner(JavaThread* thread) {
    return try_set_owner_from(NO_OWNER, thread) == NO_OWNER;
  }

  bool has_anonymous_owner() const { return owner_raw() == ANONYMOUS_OWNER; }
  void set_anonymous_owner() {
    set_owner_from_raw(NO_OWNER, ANONYMOUS_OWNER);
  }
  void set_owner_from_anonymous(JavaThread* owner) {
    set_owner_from(ANONYMOUS_OWNER, owner);
  }

  // Get and set _stack_locker.
  BasicLock* stack_locker() const;
  void set_stack_locker(BasicLock* locker);

  // Simply get _next_om field.
  ObjectMonitor* next_om() const;
  // Simply set _next_om field to new_value.
  void set_next_om(ObjectMonitor* new_value);

  int       contentions() const;
  void      add_to_contentions(int value);
  intx      recursions() const                                         { return _recursions; }
  void      set_recursions(size_t recursions);

  // JVM/TI GetObjectMonitorUsage() needs this:
  int waiters() const;
  ObjectWaiter* first_waiter()                                         { return _WaitSet; }
  ObjectWaiter* next_waiter(ObjectWaiter* o)                           { return o->_next; }
  JavaThread* thread_of_waiter(ObjectWaiter* o)                        { return o->_thread; }

  ObjectMonitor(oop object);
  ~ObjectMonitor();

  oop       object() const;
  oop       object_peek() const;
  bool      object_is_dead() const;
  bool      object_refers_to(oop obj) const;

  // Returns true if the specified thread owns the ObjectMonitor. Otherwise
  // returns false and throws IllegalMonitorStateException (IMSE).
  bool      check_owner(TRAPS);

 private:
  class ExitOnSuspend {
   protected:
    ObjectMonitor* _om;
    bool _om_exited;
   public:
    ExitOnSuspend(ObjectMonitor* om) : _om(om), _om_exited(false) {}
    void operator()(JavaThread* current);
    bool exited() { return _om_exited; }
  };
  class ClearSuccOnSuspend {
   protected:
    ObjectMonitor* _om;
   public:
    ClearSuccOnSuspend(ObjectMonitor* om) : _om(om)  {}
    void operator()(JavaThread* current);
  };

  bool      enter_is_async_deflating();
 public:
  void      enter_for_with_contention_mark(JavaThread* locking_thread, ObjectMonitorContentionMark& contention_mark);
  bool      enter_for(JavaThread* locking_thread);
  bool      enter(JavaThread* current);
  bool      try_enter(JavaThread* current, bool check_for_recursion = true);
  bool      spin_enter(JavaThread* current);
  void      enter_with_contention_mark(JavaThread* current, ObjectMonitorContentionMark& contention_mark);
  void      exit(JavaThread* current, bool not_suspended = true);
  bool      resume_operation(JavaThread* current, ObjectWaiter* node, ContinuationWrapper& cont);
  void      wait(jlong millis, bool interruptible, TRAPS);
  void      notify(TRAPS);
  void      notifyAll(TRAPS);

  void      print() const;
#ifdef ASSERT
  void      print_debug_style_on(outputStream* st) const;
#endif
  void      print_on(outputStream* st) const;

  // Use the following at your own risk
  intx      complete_exit(JavaThread* current);

 private:
  void      AddWaiter(ObjectWaiter* waiter);
  void      INotify(JavaThread* current);
  ObjectWaiter* DequeueWaiter();
  void      DequeueSpecificWaiter(ObjectWaiter* waiter);
  void      EnterI(JavaThread* current);
  void      ReenterI(JavaThread* current, ObjectWaiter* current_node);
  void      UnlinkAfterAcquire(JavaThread* current, ObjectWaiter* current_node);

  bool      VThreadMonitorEnter(JavaThread* current, ObjectWaiter* node = nullptr);
  void      VThreadWait(JavaThread* current, jlong millis);
  bool      VThreadWaitReenter(JavaThread* current, ObjectWaiter* node, ContinuationWrapper& cont);
  void      VThreadEpilog(JavaThread* current, ObjectWaiter* node);

  enum class TryLockResult { Interference = -1, HasOwner = 0, Success = 1 };

  bool           TryLockWithContentionMark(JavaThread* locking_thread, ObjectMonitorContentionMark& contention_mark);
  TryLockResult  TryLock(JavaThread* current);

  bool      TrySpin(JavaThread* current);
  bool      short_fixed_spin(JavaThread* current, int spin_count, bool adapt);
  void      ExitEpilog(JavaThread* current, ObjectWaiter* Wakee);

  // Deflation support
  bool      deflate_monitor(Thread* current);
 private:
  void      install_displaced_markword_in_object(const oop obj);
};

// RAII object to ensure that ObjectMonitor::is_being_async_deflated() is
// stable within the context of this mark.
class ObjectMonitorContentionMark : StackObj {
  DEBUG_ONLY(friend class ObjectMonitor;)

  ObjectMonitor* _monitor;
  bool _extended;

  NONCOPYABLE(ObjectMonitorContentionMark);

 public:
  explicit ObjectMonitorContentionMark(ObjectMonitor* monitor);
  ~ObjectMonitorContentionMark();

  // Extends the contention scope beyond this objects lifetime.
  // Requires manual decrement of the contentions counter.
  void extend();
};

#endif // SHARE_RUNTIME_OBJECTMONITOR_HPP
