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

#ifndef SHARE_RUNTIME_OBJECTMONITOR_HPP
#define SHARE_RUNTIME_OBJECTMONITOR_HPP

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "oops/markWord.hpp"
#include "runtime/os.hpp"
#include "runtime/park.hpp"
#include "runtime/perfData.hpp"

class ObjectMonitor;

// ObjectWaiter serves as a "proxy" or surrogate thread.
// TODO-FIXME: Eliminate ObjectWaiter and use the thread-specific
// ParkEvent instead.  Beware, however, that the JVMTI code
// knows about ObjectWaiters, so we'll have to reconcile that code.
// See next_waiter(), first_waiter(), etc.

// 继承了StackObj类型
class ObjectWaiter : public StackObj {
 public:
    // 线程状态
    enum TStates {
        TS_UNDEF, // 未定义
        TS_READY, // 就绪
        TS_RUN,  // 运行
        TS_WAIT, // 等待
        TS_ENTER, // 持锁？
        TS_CXQ // todo
    };

  // 等待 nomitor的下一个线程、前一个线程
  ObjectWaiter* volatile _next;
  ObjectWaiter* volatile _prev;

  // 等待的线程对象
  Thread*       _thread;

  // C++ long数据类型通常为32位。 jlong​​是64位
  jlong         _notifier_tid;

  ParkEvent *   _event;
  volatile int  _notified;

  // 当前线程的状态
  volatile TStates TState;

  // Contention monitoring is enabled
  // 启用 "竞争 监控"
  bool          _active;

 public:
  ObjectWaiter(Thread* thread);

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
// - The _header field must be at offset 0 because the displaced header
//   from markWord is stored there. We do not want markWord.hpp to include
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
// - See TEST_VM(ObjectMonitor, sanity) gtest for how critical restrictions are
//   enforced.
// - Adjacent ObjectMonitors should be separated by enough space to avoid
//   false sharing. This is handled by the ObjectMonitor allocation code
//   in synchronizer.cpp. Also see TEST_VM(SynchronizerTest, sanity) gtest.
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

#ifndef OM_CACHE_LINE_SIZE
// Use DEFAULT_CACHE_LINE_SIZE if not already specified for
// the current build platform.
#define OM_CACHE_LINE_SIZE DEFAULT_CACHE_LINE_SIZE
#endif

class ObjectMonitor {
  // 关键字 friend class: 类的 友元函数/友元类 是定义在类外部，
  // 但有权访问类的所有私有（private）成员和保护（protected）成员
  friend class ObjectSynchronizer;
  friend class ObjectWaiter;
  friend class VMStructs;
  JVMCI_ONLY(friend class JVMCIVMStructs;)

  // 同步代码期望头字段在偏移量为0的位置；
  // The sync code expects the header field to be at offset zero (0).
  // Enforced by the assert() in header_addr().
  volatile markWord _header;        // displaced object header word - mark
  void* volatile _object;           // backward object pointer - strong root

  // 定义名称为 AllocationState/分配状态 的枚举：空闲、新、旧
  typedef enum {
    Free = 0,  // Free must be 0 for monitor to be free after memset(..,0,..).
    New,
    Old
  } AllocationState;

  AllocationState _allocation_state;

  // Separate _header and _owner on different cache lines since both can
  // have busy multi-threaded access. _header, _object and _allocation_state
  // are set at initial inflation(通货膨胀). _object and _allocation_state don't
  // change until deflation so _object and _allocation_state are good
  // choices to share the cache line with _header.
  // 将不同缓存行上的 _header 和 _owner分开，因为不同缓存上的_header、_owner可以有多线程的方法
  DEFINE_PAD_MINUS_SIZE(0, OM_CACHE_LINE_SIZE, sizeof(volatile markWord) +sizeof(void* volatile) + sizeof(AllocationState));

  // Used by async deflation as a marker in the _owner field:
  #define DEFLATER_MARKER reinterpret_cast<void*>(-1)
  void* volatile _owner;            // pointer to owning thread OR BasicLock
  volatile jlong _previous_owner_tid;  // thread id of the previous owner of the monitor
  // Separate _owner and _next_om on different cache lines since
  // both can have busy multi-threaded access. _previous_owner_tid is only
  // changed by ObjectMonitor::exit() so it is a good choice to share the
  // cache line with _owner.
  DEFINE_PAD_MINUS_SIZE(1, OM_CACHE_LINE_SIZE, sizeof(void* volatile) +sizeof(volatile jlong));

  ObjectMonitor* _next_om;          // Next ObjectMonitor* linkage
  volatile intx _recursions;        // recursion count, 0 for first entry
  ObjectWaiter* volatile _EntryList;  // Threads blocked on entry or reentry.
                                      // The list is actually composed of WaitNodes,
                                      // acting as proxies for Threads.

  ObjectWaiter* volatile _cxq;      // LL of recently-arrived threads blocked on entry.
  Thread* volatile _succ;           // Heir presumptive thread - used for futile wakeup throttling
  Thread* volatile _Responsible;

  volatile int _Spinner;            // for exit->spinner handoff optimization
  volatile int _SpinDuration;

  jint  _contentions;               // Number of active contentions in enter(). It is used by is_busy()
                                    // along with other fields to determine if an ObjectMonitor can be
                                    // deflated. It is also used by the async deflation protocol. See
                                    // ObjectSynchronizer::deflate_monitor_using_JT().
 protected:
  // LL of threads wait()ing on the monitor
  // 等待监视器的线程队列
  ObjectWaiter* volatile _WaitSet;
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
  static PerfCounter * _sync_Notifications;
  static PerfCounter * _sync_Inflations;
  static PerfCounter * _sync_Deflations;
  static PerfLongVariable * _sync_MonExtant;

  static int Knob_SpinLimit;

  void* operator new (size_t size) throw();
  void* operator new[] (size_t size) throw();
  void operator delete(void* p);
  void operator delete[] (void* p);

  // TODO-FIXME: the "offset" routines should return a type of off_t instead of int ...
  // ByteSize would also be an appropriate type.
  static int header_offset_in_bytes()      { return offset_of(ObjectMonitor, _header); }
  static int object_offset_in_bytes()      { return offset_of(ObjectMonitor, _object); }
  static int owner_offset_in_bytes()       { return offset_of(ObjectMonitor, _owner); }
  static int recursions_offset_in_bytes()  { return offset_of(ObjectMonitor, _recursions); }
  static int cxq_offset_in_bytes()         { return offset_of(ObjectMonitor, _cxq); }
  static int succ_offset_in_bytes()        { return offset_of(ObjectMonitor, _succ); }
  static int EntryList_offset_in_bytes()   { return offset_of(ObjectMonitor, _EntryList); }

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
    ((ObjectMonitor::f ## _offset_in_bytes()) - markWord::monitor_value)

  markWord           header() const;
  volatile markWord* header_addr();
  void               set_header(markWord hdr);

  intptr_t is_busy() const {
    // TODO-FIXME: assert _owner == null implies _recursions = 0
    intptr_t ret_code = _waiters | intptr_t(_cxq) | intptr_t(_EntryList);
    if (contentions() > 0) {
      ret_code |= contentions();
    }
    if (_owner != DEFLATER_MARKER) {
      ret_code |= intptr_t(_owner);
    }
    return ret_code;
  }
  const char* is_busy_to_string(stringStream* ss);

  intptr_t  is_entered(Thread* current) const;

  void*     owner() const;  // Returns NULL if DEFLATER_MARKER is observed.
  // Returns true if owner field == DEFLATER_MARKER and false otherwise.
  bool      owner_is_DEFLATER_MARKER();
  // Returns true if 'this' is being async deflated and false otherwise.
  bool      is_being_async_deflated();
  // Clear _owner field; current value must match old_value.
  void      release_clear_owner(void* old_value);
  // Simply set _owner field to new_value; current value must match old_value.
  void      set_owner_from(void* old_value, void* new_value);
  // Simply set _owner field to new_value; current value must match old_value1 or old_value2.
  void      set_owner_from(void* old_value1, void* old_value2, void* new_value);
  // Simply set _owner field to self; current value must match basic_lock_p.
  void      set_owner_from_BasicLock(void* basic_lock_p, Thread* self);
  // Try to set _owner field to new_value if the current value matches
  // old_value, using Atomic::cmpxchg(). Otherwise, does not change the
  // _owner field. Returns the prior value of the _owner field.
  void*     try_set_owner_from(void* old_value, void* new_value);

  // Simply get _next_om field.
  ObjectMonitor* next_om() const;
  // Get _next_om field with acquire semantics.
  ObjectMonitor* next_om_acquire() const;
  // Simply set _next_om field to new_value.
  void set_next_om(ObjectMonitor* new_value);
  // Set _next_om field to new_value with release semantics.
  void release_set_next_om(ObjectMonitor* new_value);
  // Try to set _next_om field to new_value if the current value matches
  // old_value, using Atomic::cmpxchg(). Otherwise, does not change the
  // _next_om field. Returns the prior value of the _next_om field.
  ObjectMonitor* try_set_next_om(ObjectMonitor* old_value, ObjectMonitor* new_value);

  jint      waiters() const;

  jint      contentions() const;
  void      add_to_contentions(jint value);
  intx      recursions() const                                         { return _recursions; }

  // JVM/TI GetObjectMonitorUsage() needs this:
  ObjectWaiter* first_waiter()                                         { return _WaitSet; }
  ObjectWaiter* next_waiter(ObjectWaiter* o)                           { return o->_next; }
  Thread* thread_of_waiter(ObjectWaiter* o)                            { return o->_thread; }

 protected:
  // We don't typically expect or want the ctors or dtors to run.
  // normal ObjectMonitors are type-stable and immortal.
  ObjectMonitor() { ::memset((void*)this, 0, sizeof(*this)); }

  ~ObjectMonitor() {
    // TODO: Add asserts ...
    // _cxq == 0 _succ == NULL _owner == NULL _waiters == 0
    // _contentions == 0 _EntryList  == NULL etc
  }

 private:
  void Recycle() {
    // TODO: add stronger asserts ...
    // _cxq == 0 _succ == NULL _owner == NULL _waiters == 0
    // _contentions == 0 EntryList  == NULL
    // _recursions == 0 _WaitSet == NULL
#ifdef ASSERT
    stringStream ss;
    assert((is_busy() | _recursions) == 0, "freeing in-use monitor: %s, "
           "recursions=" INTX_FORMAT, is_busy_to_string(&ss), _recursions);
#endif
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
  void      release_set_allocation_state(AllocationState s);
  void      set_allocation_state(AllocationState s);
  AllocationState allocation_state() const;
  AllocationState allocation_state_acquire() const;
  bool      is_free() const;
  bool      is_old() const;
  bool      is_new() const;

  // Returns true if the specified thread owns the ObjectMonitor. Otherwise
  // returns false and throws IllegalMonitorStateException (IMSE).
  bool      check_owner(Thread* THREAD);
  void      clear();
  void      clear_common();

  bool      enter(TRAPS);
  void      exit(bool not_suspended, TRAPS);
  void      wait(jlong millis, bool interruptable, TRAPS);
  void      notify(TRAPS);
  void      notifyAll(TRAPS);

  void      print() const;
#ifdef ASSERT
  void      print_debug_style_on(outputStream* st) const;
#endif
  void      print_on(outputStream* st) const;

// Use the following at your own risk
  intx      complete_exit(TRAPS);
  bool      reenter(intx recursions, TRAPS);

 private:
  void      AddWaiter(ObjectWaiter* waiter);
  void      INotify(Thread* self);
  ObjectWaiter* DequeueWaiter();
  void      DequeueSpecificWaiter(ObjectWaiter* waiter);
  void      EnterI(TRAPS);
  void      ReenterI(Thread* self, ObjectWaiter* self_node);
  void      UnlinkAfterAcquire(Thread* self, ObjectWaiter* self_node);
  int       TryLock(Thread* self);
  int       NotRunnable(Thread* self, Thread* Owner);
  int       TrySpin(Thread* self);
  void      ExitEpilog(Thread* self, ObjectWaiter* Wakee);
  bool      ExitSuspendEquivalent(JavaThread* self);
  void      install_displaced_markword_in_object(const oop obj);
};

#endif // SHARE_RUNTIME_OBJECTMONITOR_HPP
