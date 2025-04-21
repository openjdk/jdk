/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
#include "jfrfiles/jfrEventClasses.hpp"
#include "logging/log.hpp"
#include "memory/allStatic.hpp"
#include "memory/resourceArea.hpp"
#include "nmt/memTag.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/basicLock.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/lightweightSynchronizer.hpp"
#include "runtime/lockStack.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/synchronizer.inline.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/globalDefinitions.hpp"

// ConcurrentHashTable storing links from objects to ObjectMonitors
class ObjectMonitorTable : AllStatic {
  struct Config {
    using Value = ObjectMonitor*;
    static uintx get_hash(Value const& value, bool* is_dead) {
      return (uintx)value->hash();
    }
    static void* allocate_node(void* context, size_t size, Value const& value) {
      ObjectMonitorTable::inc_items_count();
      return AllocateHeap(size, mtObjectMonitor);
    };
    static void free_node(void* context, void* memory, Value const& value) {
      ObjectMonitorTable::dec_items_count();
      FreeHeap(memory);
    }
  };
  using ConcurrentTable = ConcurrentHashTable<Config, mtObjectMonitor>;

  static ConcurrentTable* _table;
  static volatile size_t _items_count;
  static size_t _table_size;
  static volatile bool _resize;

  class Lookup : public StackObj {
    oop _obj;

   public:
    explicit Lookup(oop obj) : _obj(obj) {}

    uintx get_hash() const {
      uintx hash = _obj->mark().hash();
      assert(hash != 0, "should have a hash");
      return hash;
    }

    bool equals(ObjectMonitor** value) {
      assert(*value != nullptr, "must be");
      return (*value)->object_refers_to(_obj);
    }

    bool is_dead(ObjectMonitor** value) {
      assert(*value != nullptr, "must be");
      return false;
    }
  };

  class LookupMonitor : public StackObj {
    ObjectMonitor* _monitor;

   public:
    explicit LookupMonitor(ObjectMonitor* monitor) : _monitor(monitor) {}

    uintx get_hash() const {
      return _monitor->hash();
    }

    bool equals(ObjectMonitor** value) {
      return (*value) == _monitor;
    }

    bool is_dead(ObjectMonitor** value) {
      assert(*value != nullptr, "must be");
      return (*value)->object_is_dead();
    }
  };

  static void inc_items_count() {
    Atomic::inc(&_items_count, memory_order_relaxed);
  }

  static void dec_items_count() {
    Atomic::dec(&_items_count, memory_order_relaxed);
  }

  static double get_load_factor() {
    size_t count = Atomic::load(&_items_count);
    return (double)count / (double)_table_size;
  }

  static size_t table_size(Thread* current = Thread::current()) {
    return ((size_t)1) << _table->get_size_log2(current);
  }

  static size_t max_log_size() {
    // TODO[OMTable]: Evaluate the max size.
    // TODO[OMTable]: Need to fix init order to use Universe::heap()->max_capacity();
    //                Using MaxHeapSize directly this early may be wrong, and there
    //                are definitely rounding errors (alignment).
    const size_t max_capacity = MaxHeapSize;
    const size_t min_object_size = CollectedHeap::min_dummy_object_size() * HeapWordSize;
    const size_t max_objects = max_capacity / MAX2(MinObjAlignmentInBytes, checked_cast<int>(min_object_size));
    const size_t log_max_objects = log2i_graceful(max_objects);

    return MAX2(MIN2<size_t>(SIZE_BIG_LOG2, log_max_objects), min_log_size());
  }

  static size_t min_log_size() {
    // ~= log(AvgMonitorsPerThreadEstimate default)
    return 10;
  }

  template<typename V>
  static size_t clamp_log_size(V log_size) {
    return MAX2(MIN2(log_size, checked_cast<V>(max_log_size())), checked_cast<V>(min_log_size()));
  }

  static size_t initial_log_size() {
    const size_t estimate = log2i(MAX2(os::processor_count(), 1)) + log2i(MAX2(AvgMonitorsPerThreadEstimate, size_t(1)));
    return clamp_log_size(estimate);
  }

  static size_t grow_hint () {
    return ConcurrentTable::DEFAULT_GROW_HINT;
  }

 public:
  static void create() {
    _table = new ConcurrentTable(initial_log_size(), max_log_size(), grow_hint());
    _items_count = 0;
    _table_size = table_size();
    _resize = false;
  }

  static void verify_monitor_get_result(oop obj, ObjectMonitor* monitor) {
#ifdef ASSERT
    if (SafepointSynchronize::is_at_safepoint()) {
      bool has_monitor = obj->mark().has_monitor();
      assert(has_monitor == (monitor != nullptr),
          "Inconsistency between markWord and ObjectMonitorTable has_monitor: %s monitor: " PTR_FORMAT,
          BOOL_TO_STR(has_monitor), p2i(monitor));
    }
#endif
  }

  static ObjectMonitor* monitor_get(Thread* current, oop obj) {
    ObjectMonitor* result = nullptr;
    Lookup lookup_f(obj);
    auto found_f = [&](ObjectMonitor** found) {
      assert((*found)->object_peek() == obj, "must be");
      result = *found;
    };
    _table->get(current, lookup_f, found_f);
    verify_monitor_get_result(obj, result);
    return result;
  }

  static void try_notify_grow() {
    if (!_table->is_max_size_reached() && !Atomic::load(&_resize)) {
      Atomic::store(&_resize, true);
      if (Service_lock->try_lock()) {
        Service_lock->notify();
        Service_lock->unlock();
      }
    }
  }

  static bool should_shrink() {
    // Not implemented;
    return false;
  }

  static constexpr double GROW_LOAD_FACTOR = 0.75;

  static bool should_grow() {
    return get_load_factor() > GROW_LOAD_FACTOR && !_table->is_max_size_reached();
  }

  static bool should_resize() {
    return should_grow() || should_shrink() || Atomic::load(&_resize);
  }

  template<typename Task, typename... Args>
  static bool run_task(JavaThread* current, Task& task, const char* task_name, Args&... args) {
    if (task.prepare(current)) {
      log_trace(monitortable)("Started to %s", task_name);
      TraceTime timer(task_name, TRACETIME_LOG(Debug, monitortable, perf));
      while (task.do_task(current, args...)) {
        task.pause(current);
        {
          ThreadBlockInVM tbivm(current);
        }
        task.cont(current);
      }
      task.done(current);
      return true;
    }
    return false;
  }

  static bool grow(JavaThread* current) {
    ConcurrentTable::GrowTask grow_task(_table);
    if (run_task(current, grow_task, "Grow")) {
      _table_size = table_size(current);
      log_info(monitortable)("Grown to size: %zu", _table_size);
      return true;
    }
    return false;
  }

  static bool clean(JavaThread* current) {
    ConcurrentTable::BulkDeleteTask clean_task(_table);
    auto is_dead = [&](ObjectMonitor** monitor) {
      return (*monitor)->object_is_dead();
    };
    auto do_nothing = [&](ObjectMonitor** monitor) {};
    NativeHeapTrimmer::SuspendMark sm("ObjectMonitorTable");
    return run_task(current, clean_task, "Clean", is_dead, do_nothing);
  }

  static bool resize(JavaThread* current) {
    LogTarget(Info, monitortable) lt;
    bool success = false;

    if (should_grow()) {
      lt.print("Start growing with load factor %f", get_load_factor());
      success = grow(current);
    } else {
      if (!_table->is_max_size_reached() && Atomic::load(&_resize)) {
        lt.print("WARNING: Getting resize hints with load factor %f", get_load_factor());
      }
      lt.print("Start cleaning with load factor %f", get_load_factor());
      success = clean(current);
    }

    Atomic::store(&_resize, false);

    return success;
  }

  static ObjectMonitor* monitor_put_get(Thread* current, ObjectMonitor* monitor, oop obj) {
    // Enter the monitor into the concurrent hashtable.
    ObjectMonitor* result = monitor;
    Lookup lookup_f(obj);
    auto found_f = [&](ObjectMonitor** found) {
      assert((*found)->object_peek() == obj, "must be");
      result = *found;
    };
    bool grow;
    _table->insert_get(current, lookup_f, monitor, found_f, &grow);
    verify_monitor_get_result(obj, result);
    if (grow) {
      try_notify_grow();
    }
    return result;
  }

  static bool remove_monitor_entry(Thread* current, ObjectMonitor* monitor) {
    LookupMonitor lookup_f(monitor);
    return _table->remove(current, lookup_f);
  }

  static bool contains_monitor(Thread* current, ObjectMonitor* monitor) {
    LookupMonitor lookup_f(monitor);
    bool result = false;
    auto found_f = [&](ObjectMonitor** found) {
      result = true;
    };
    _table->get(current, lookup_f, found_f);
    return result;
  }

  static void print_on(outputStream* st) {
    auto printer = [&] (ObjectMonitor** entry) {
       ObjectMonitor* om = *entry;
       oop obj = om->object_peek();
       st->print("monitor=" PTR_FORMAT ", ", p2i(om));
       st->print("object=" PTR_FORMAT, p2i(obj));
       assert(obj->mark().hash() == om->hash(), "hash must match");
       st->cr();
       return true;
    };
    if (SafepointSynchronize::is_at_safepoint()) {
      _table->do_safepoint_scan(printer);
    } else {
      _table->do_scan(Thread::current(), printer);
    }
  }
};

ObjectMonitorTable::ConcurrentTable* ObjectMonitorTable::_table = nullptr;
volatile size_t ObjectMonitorTable::_items_count = 0;
size_t ObjectMonitorTable::_table_size = 0;
volatile bool ObjectMonitorTable::_resize = false;

ObjectMonitor* LightweightSynchronizer::get_or_insert_monitor_from_table(oop object, JavaThread* current, bool* inserted) {
  assert(LockingMode == LM_LIGHTWEIGHT, "must be");

  ObjectMonitor* monitor = get_monitor_from_table(current, object);
  if (monitor != nullptr) {
    *inserted = false;
    return monitor;
  }

  ObjectMonitor* alloced_monitor = new ObjectMonitor(object);
  alloced_monitor->set_anonymous_owner();

  // Try insert monitor
  monitor = add_monitor(current, alloced_monitor, object);

  *inserted = alloced_monitor == monitor;
  if (!*inserted) {
    delete alloced_monitor;
  }

  return monitor;
}

static void log_inflate(Thread* current, oop object, ObjectSynchronizer::InflateCause cause) {
  if (log_is_enabled(Trace, monitorinflation)) {
    ResourceMark rm(current);
    log_trace(monitorinflation)("inflate: object=" INTPTR_FORMAT ", mark="
                                INTPTR_FORMAT ", type='%s' cause=%s", p2i(object),
                                object->mark().value(), object->klass()->external_name(),
                                ObjectSynchronizer::inflate_cause_name(cause));
  }
}

static void post_monitor_inflate_event(EventJavaMonitorInflate* event,
                                       const oop obj,
                                       ObjectSynchronizer::InflateCause cause) {
  assert(event != nullptr, "invariant");
  const Klass* monitor_klass = obj->klass();
  if (ObjectMonitor::is_jfr_excluded(monitor_klass)) {
    return;
  }
  event->set_monitorClass(monitor_klass);
  event->set_address((uintptr_t)(void*)obj);
  event->set_cause((u1)cause);
  event->commit();
}

ObjectMonitor* LightweightSynchronizer::get_or_insert_monitor(oop object, JavaThread* current, ObjectSynchronizer::InflateCause cause) {
  assert(UseObjectMonitorTable, "must be");

  EventJavaMonitorInflate event;

  bool inserted;
  ObjectMonitor* monitor = get_or_insert_monitor_from_table(object, current, &inserted);

  if (inserted) {
    log_inflate(current, object, cause);
    if (event.should_commit()) {
      post_monitor_inflate_event(&event, object, cause);
    }

    // The monitor has an anonymous owner so it is safe from async deflation.
    ObjectSynchronizer::_in_use_list.add(monitor);
  }

  return monitor;
}

// Add the hashcode to the monitor to match the object and put it in the hashtable.
ObjectMonitor* LightweightSynchronizer::add_monitor(JavaThread* current, ObjectMonitor* monitor, oop obj) {
  assert(UseObjectMonitorTable, "must be");
  assert(obj == monitor->object(), "must be");

  intptr_t hash = obj->mark().hash();
  assert(hash != 0, "must be set when claiming the object monitor");
  monitor->set_hash(hash);

  return ObjectMonitorTable::monitor_put_get(current, monitor, obj);
}

bool LightweightSynchronizer::remove_monitor(Thread* current, ObjectMonitor* monitor, oop obj) {
  assert(UseObjectMonitorTable, "must be");
  assert(monitor->object_peek() == obj, "must be, cleared objects are removed by is_dead");

  return ObjectMonitorTable::remove_monitor_entry(current, monitor);
}

void LightweightSynchronizer::deflate_mark_word(oop obj) {
  assert(UseObjectMonitorTable, "must be");

  markWord mark = obj->mark_acquire();
  assert(!mark.has_no_hash(), "obj with inflated monitor must have had a hash");

  while (mark.has_monitor()) {
    const markWord new_mark = mark.clear_lock_bits().set_unlocked();
    mark = obj->cas_set_mark(new_mark, mark);
  }
}

void LightweightSynchronizer::initialize() {
  if (!UseObjectMonitorTable) {
    return;
  }
  ObjectMonitorTable::create();
}

bool LightweightSynchronizer::needs_resize() {
  if (!UseObjectMonitorTable) {
    return false;
  }
  return ObjectMonitorTable::should_resize();
}

bool LightweightSynchronizer::resize_table(JavaThread* current) {
  if (!UseObjectMonitorTable) {
    return true;
  }
  return ObjectMonitorTable::resize(current);
}

class LightweightSynchronizer::LockStackInflateContendedLocks : private OopClosure {
 private:
  oop _contended_oops[LockStack::CAPACITY];
  int _length;

  void do_oop(oop* o) final {
    oop obj = *o;
    if (obj->mark_acquire().has_monitor()) {
      if (_length > 0 && _contended_oops[_length - 1] == obj) {
        // Recursive
        return;
      }
      _contended_oops[_length++] = obj;
    }
  }

  void do_oop(narrowOop* o) final {
    ShouldNotReachHere();
  }

 public:
  LockStackInflateContendedLocks() :
    _contended_oops(),
    _length(0) {};

  void inflate(JavaThread* current) {
    assert(current == JavaThread::current(), "must be");
    current->lock_stack().oops_do(this);
    for (int i = 0; i < _length; i++) {
      LightweightSynchronizer::
        inflate_fast_locked_object(_contended_oops[i], ObjectSynchronizer::inflate_cause_vm_internal, current, current);
    }
  }
};

void LightweightSynchronizer::ensure_lock_stack_space(JavaThread* current) {
  assert(current == JavaThread::current(), "must be");
  LockStack& lock_stack = current->lock_stack();

  // Make room on lock_stack
  if (lock_stack.is_full()) {
    // Inflate contended objects
    LockStackInflateContendedLocks().inflate(current);
    if (lock_stack.is_full()) {
      // Inflate the oldest object
      inflate_fast_locked_object(lock_stack.bottom(), ObjectSynchronizer::inflate_cause_vm_internal, current, current);
    }
  }
}

class LightweightSynchronizer::CacheSetter : StackObj {
  JavaThread* const _thread;
  BasicLock* const _lock;
  ObjectMonitor* _monitor;

  NONCOPYABLE(CacheSetter);

 public:
  CacheSetter(JavaThread* thread, BasicLock* lock) :
    _thread(thread),
    _lock(lock),
    _monitor(nullptr) {}

  ~CacheSetter() {
    // Only use the cache if using the table.
    if (UseObjectMonitorTable) {
      if (_monitor != nullptr) {
        // If the monitor is already in the BasicLock cache then it is most
        // likely in the thread cache, do not set it again to avoid reordering.
        if (_monitor != _lock->object_monitor_cache()) {
          _thread->om_set_monitor_cache(_monitor);
          _lock->set_object_monitor_cache(_monitor);
        }
      } else {
        _lock->clear_object_monitor_cache();
      }
    }
  }

  void set_monitor(ObjectMonitor* monitor) {
    assert(_monitor == nullptr, "only set once");
    _monitor = monitor;
  }

};

// Reads first from the BasicLock cache then from the OMCache in the current thread.
// C2 fast-path may have put the monitor in the cache in the BasicLock.
inline static ObjectMonitor* read_caches(JavaThread* current, BasicLock* lock, oop object) {
  ObjectMonitor* monitor = lock->object_monitor_cache();
  if (monitor == nullptr) {
    monitor = current->om_get_from_monitor_cache(object);
  }
  return monitor;
}

class LightweightSynchronizer::VerifyThreadState {
  bool _no_safepoint;

 public:
  VerifyThreadState(JavaThread* locking_thread, JavaThread* current) : _no_safepoint(locking_thread != current) {
    assert(current == Thread::current(), "must be");
    assert(locking_thread == current || locking_thread->is_obj_deopt_suspend(), "locking_thread may not run concurrently");
    if (_no_safepoint) {
      DEBUG_ONLY(JavaThread::current()->inc_no_safepoint_count();)
    }
  }
  ~VerifyThreadState() {
    if (_no_safepoint){
      DEBUG_ONLY(JavaThread::current()->dec_no_safepoint_count();)
    }
  }
};

inline bool LightweightSynchronizer::fast_lock_try_enter(oop obj, LockStack& lock_stack, JavaThread* current) {
  markWord mark = obj->mark();
  while (mark.is_unlocked()) {
    ensure_lock_stack_space(current);
    assert(!lock_stack.is_full(), "must have made room on the lock stack");
    assert(!lock_stack.contains(obj), "thread must not already hold the lock");
    // Try to swing into 'fast-locked' state.
    markWord locked_mark = mark.set_fast_locked();
    markWord old_mark = mark;
    mark = obj->cas_set_mark(locked_mark, old_mark);
    if (old_mark == mark) {
      // Successfully fast-locked, push object to lock-stack and return.
      lock_stack.push(obj);
      return true;
    }
  }
  return false;
}

bool LightweightSynchronizer::fast_lock_spin_enter(oop obj, LockStack& lock_stack, JavaThread* current, bool observed_deflation) {
  assert(UseObjectMonitorTable, "must be");
  // Will spin with exponential backoff with an accumulative O(2^spin_limit) spins.
  const int log_spin_limit = os::is_MP() ? LightweightFastLockingSpins : 1;
  const int log_min_safepoint_check_interval = 10;

  markWord mark = obj->mark();
  const auto should_spin = [&]() {
    if (!mark.has_monitor()) {
      // Spin while not inflated.
      return true;
    } else if (observed_deflation) {
      // Spin while monitor is being deflated.
      ObjectMonitor* monitor = ObjectSynchronizer::read_monitor(current, obj, mark);
      return monitor == nullptr || monitor->is_being_async_deflated();
    }
    // Else stop spinning.
    return false;
  };
  // Always attempt to lock once even when safepoint synchronizing.
  bool should_process = false;
  for (int i = 0; should_spin() && !should_process && i < log_spin_limit; i++) {
    // Spin with exponential backoff.
    const int total_spin_count = 1 << i;
    const int inner_spin_count = MIN2(1 << log_min_safepoint_check_interval, total_spin_count);
    const int outer_spin_count = total_spin_count / inner_spin_count;
    for (int outer = 0; outer < outer_spin_count; outer++) {
      should_process = SafepointMechanism::should_process(current);
      if (should_process) {
        // Stop spinning for safepoint.
        break;
      }
      for (int inner = 1; inner < inner_spin_count; inner++) {
        SpinPause();
      }
    }

    if (fast_lock_try_enter(obj, lock_stack, current)) return true;
  }
  return false;
}

void LightweightSynchronizer::enter_for(Handle obj, BasicLock* lock, JavaThread* locking_thread) {
  assert(LockingMode == LM_LIGHTWEIGHT, "must be");
  assert(!UseObjectMonitorTable || lock->object_monitor_cache() == nullptr, "must be cleared");
  JavaThread* current = JavaThread::current();
  VerifyThreadState vts(locking_thread, current);

  if (obj->klass()->is_value_based()) {
    ObjectSynchronizer::handle_sync_on_value_based_class(obj, locking_thread);
  }

  LockStack& lock_stack = locking_thread->lock_stack();

  ObjectMonitor* monitor = nullptr;
  if (lock_stack.contains(obj())) {
    monitor = inflate_fast_locked_object(obj(), ObjectSynchronizer::inflate_cause_monitor_enter, locking_thread, current);
    bool entered = monitor->enter_for(locking_thread);
    assert(entered, "recursive ObjectMonitor::enter_for must succeed");
  } else {
    do {
      // It is assumed that enter_for must enter on an object without contention.
      monitor = inflate_and_enter(obj(), lock, ObjectSynchronizer::inflate_cause_monitor_enter, locking_thread, current);
      // But there may still be a race with deflation.
    } while (monitor == nullptr);
  }

  assert(monitor != nullptr, "LightweightSynchronizer::enter_for must succeed");
  assert(!UseObjectMonitorTable || lock->object_monitor_cache() == nullptr, "unused. already cleared");
}

void LightweightSynchronizer::enter(Handle obj, BasicLock* lock, JavaThread* current) {
  assert(LockingMode == LM_LIGHTWEIGHT, "must be");
  assert(current == JavaThread::current(), "must be");

  if (obj->klass()->is_value_based()) {
    ObjectSynchronizer::handle_sync_on_value_based_class(obj, current);
  }

  CacheSetter cache_setter(current, lock);

  // Used when deflation is observed. Progress here requires progress
  // from the deflator. After observing that the deflator is not
  // making progress (after two yields), switch to sleeping.
  SpinYield spin_yield(0, 2);
  bool observed_deflation = false;

  LockStack& lock_stack = current->lock_stack();

  if (!lock_stack.is_full() && lock_stack.try_recursive_enter(obj())) {
    // Recursively fast locked
    return;
  }

  if (lock_stack.contains(obj())) {
    ObjectMonitor* monitor = inflate_fast_locked_object(obj(), ObjectSynchronizer::inflate_cause_monitor_enter, current, current);
    bool entered = monitor->enter(current);
    assert(entered, "recursive ObjectMonitor::enter must succeed");
    cache_setter.set_monitor(monitor);
    return;
  }

  while (true) {
    // Fast-locking does not use the 'lock' argument.
    // Fast-lock spinning to avoid inflating for short critical sections.
    // The goal is to only inflate when the extra cost of using ObjectMonitors
    // is worth it.
    // If deflation has been observed we also spin while deflation is ongoing.
    if (fast_lock_try_enter(obj(), lock_stack, current)) {
      return;
    } else if (UseObjectMonitorTable && fast_lock_spin_enter(obj(), lock_stack, current, observed_deflation)) {
      return;
    }

    if (observed_deflation) {
      spin_yield.wait();
    }

    ObjectMonitor* monitor = inflate_and_enter(obj(), lock, ObjectSynchronizer::inflate_cause_monitor_enter, current, current);
    if (monitor != nullptr) {
      cache_setter.set_monitor(monitor);
      return;
    }

    // If inflate_and_enter returns nullptr it is because a deflated monitor
    // was encountered. Fallback to fast locking. The deflater is responsible
    // for clearing out the monitor and transitioning the markWord back to
    // fast locking.
    observed_deflation = true;
  }
}

void LightweightSynchronizer::exit(oop object, BasicLock* lock, JavaThread* current) {
  assert(LockingMode == LM_LIGHTWEIGHT, "must be");
  assert(current == Thread::current(), "must be");

  markWord mark = object->mark();
  assert(!mark.is_unlocked(), "must be");

  LockStack& lock_stack = current->lock_stack();
  if (mark.is_fast_locked()) {
    if (lock_stack.try_recursive_exit(object)) {
      // This is a recursive exit which succeeded
      return;
    }
    if (lock_stack.is_recursive(object)) {
      // Must inflate recursive locks if try_recursive_exit fails
      // This happens for un-structured unlocks, could potentially
      // fix try_recursive_exit to handle these.
      inflate_fast_locked_object(object, ObjectSynchronizer::inflate_cause_vm_internal, current, current);
    }
  }

  while (mark.is_fast_locked()) {
    markWord unlocked_mark = mark.set_unlocked();
    markWord old_mark = mark;
    mark = object->cas_set_mark(unlocked_mark, old_mark);
    if (old_mark == mark) {
      // CAS successful, remove from lock_stack
      size_t recursion = lock_stack.remove(object) - 1;
      assert(recursion == 0, "Should not have unlocked here");
      return;
    }
  }

  assert(mark.has_monitor(), "must be");
  // The monitor exists
  ObjectMonitor* monitor;
  if (UseObjectMonitorTable) {
    monitor = read_caches(current, lock, object);
    if (monitor == nullptr) {
      monitor = get_monitor_from_table(current, object);
    }
  } else {
    monitor = ObjectSynchronizer::read_monitor(mark);
  }
  if (monitor->has_anonymous_owner()) {
    assert(current->lock_stack().contains(object), "current must have object on its lock stack");
    monitor->set_owner_from_anonymous(current);
    monitor->set_recursions(current->lock_stack().remove(object) - 1);
  }

  monitor->exit(current);
}

// LightweightSynchronizer::inflate_locked_or_imse is used to to get an inflated
// ObjectMonitor* with LM_LIGHTWEIGHT. It is used from contexts which require
// an inflated ObjectMonitor* for a monitor, and expects to throw a
// java.lang.IllegalMonitorStateException if it is not held by the current
// thread. Such as notify/wait and jni_exit. LM_LIGHTWEIGHT keeps it invariant
// that it only inflates if it is already locked by the current thread or the
// current thread is in the process of entering. To maintain this invariant we
// need to throw a java.lang.IllegalMonitorStateException before inflating if
// the current thread is not the owner.
// LightweightSynchronizer::inflate_locked_or_imse facilitates this.
ObjectMonitor* LightweightSynchronizer::inflate_locked_or_imse(oop obj, ObjectSynchronizer::InflateCause cause, TRAPS) {
  assert(LockingMode == LM_LIGHTWEIGHT, "must be");
  JavaThread* current = THREAD;

  for (;;) {
    markWord mark = obj->mark_acquire();
    if (mark.is_unlocked()) {
      // No lock, IMSE.
      THROW_MSG_(vmSymbols::java_lang_IllegalMonitorStateException(),
                 "current thread is not owner", nullptr);
    }

    if (mark.is_fast_locked()) {
      if (!current->lock_stack().contains(obj)) {
        // Fast locked by other thread, IMSE.
        THROW_MSG_(vmSymbols::java_lang_IllegalMonitorStateException(),
                   "current thread is not owner", nullptr);
      } else {
        // Current thread owns the lock, must inflate
        return inflate_fast_locked_object(obj, cause, current, current);
      }
    }

    assert(mark.has_monitor(), "must be");
    ObjectMonitor* monitor = ObjectSynchronizer::read_monitor(current, obj, mark);
    if (monitor != nullptr) {
      if (monitor->has_anonymous_owner()) {
        LockStack& lock_stack = current->lock_stack();
        if (lock_stack.contains(obj)) {
          // Current thread owns the lock but someone else inflated it.
          // Fix owner and pop lock stack.
          monitor->set_owner_from_anonymous(current);
          monitor->set_recursions(lock_stack.remove(obj) - 1);
        } else {
          // Fast locked (and inflated) by other thread, or deflation in progress, IMSE.
          THROW_MSG_(vmSymbols::java_lang_IllegalMonitorStateException(),
                     "current thread is not owner", nullptr);
        }
      }
      return monitor;
    }
  }
}

ObjectMonitor* LightweightSynchronizer::inflate_into_object_header(oop object, ObjectSynchronizer::InflateCause cause, JavaThread* locking_thread, Thread* current) {

  // The JavaThread* locking_thread parameter is only used by LM_LIGHTWEIGHT and requires
  // that the locking_thread == Thread::current() or is suspended throughout the call by
  // some other mechanism.
  // Even with LM_LIGHTWEIGHT the thread might be nullptr when called from a non
  // JavaThread. (As may still be the case from FastHashCode). However it is only
  // important for the correctness of the LM_LIGHTWEIGHT algorithm that the thread
  // is set when called from ObjectSynchronizer::enter from the owning thread,
  // ObjectSynchronizer::enter_for from any thread, or ObjectSynchronizer::exit.
  EventJavaMonitorInflate event;

  for (;;) {
    const markWord mark = object->mark_acquire();

    // The mark can be in one of the following states:
    // *  inflated     - Just return if using stack-locking.
    //                   If using fast-locking and the ObjectMonitor owner
    //                   is anonymous and the locking_thread owns the
    //                   object lock, then we make the locking_thread
    //                   the ObjectMonitor owner and remove the lock from
    //                   the locking_thread's lock stack.
    // *  fast-locked  - Coerce it to inflated from fast-locked.
    // *  unlocked     - Aggressively inflate the object.

    // CASE: inflated
    if (mark.has_monitor()) {
      ObjectMonitor* inf = mark.monitor();
      markWord dmw = inf->header();
      assert(dmw.is_neutral(), "invariant: header=" INTPTR_FORMAT, dmw.value());
      if (inf->has_anonymous_owner() &&
          locking_thread != nullptr && locking_thread->lock_stack().contains(object)) {
        inf->set_owner_from_anonymous(locking_thread);
        size_t removed = locking_thread->lock_stack().remove(object);
        inf->set_recursions(removed - 1);
      }
      return inf;
    }

    // CASE: fast-locked
    // Could be fast-locked either by the locking_thread or by some other thread.
    //
    // Note that we allocate the ObjectMonitor speculatively, _before_
    // attempting to set the object's mark to the new ObjectMonitor. If
    // the locking_thread owns the monitor, then we set the ObjectMonitor's
    // owner to the locking_thread. Otherwise, we set the ObjectMonitor's owner
    // to anonymous. If we lose the race to set the object's mark to the
    // new ObjectMonitor, then we just delete it and loop around again.
    //
    if (mark.is_fast_locked()) {
      ObjectMonitor* monitor = new ObjectMonitor(object);
      monitor->set_header(mark.set_unlocked());
      bool own = locking_thread != nullptr && locking_thread->lock_stack().contains(object);
      if (own) {
        // Owned by locking_thread.
        monitor->set_owner(locking_thread);
      } else {
        // Owned by somebody else.
        monitor->set_anonymous_owner();
      }
      markWord monitor_mark = markWord::encode(monitor);
      markWord old_mark = object->cas_set_mark(monitor_mark, mark);
      if (old_mark == mark) {
        // Success! Return inflated monitor.
        if (own) {
          size_t removed = locking_thread->lock_stack().remove(object);
          monitor->set_recursions(removed - 1);
        }
        // Once the ObjectMonitor is configured and object is associated
        // with the ObjectMonitor, it is safe to allow async deflation:
        ObjectSynchronizer::_in_use_list.add(monitor);

        log_inflate(current, object, cause);
        if (event.should_commit()) {
          post_monitor_inflate_event(&event, object, cause);
        }
        return monitor;
      } else {
        delete monitor;
        continue;  // Interference -- just retry
      }
    }

    // CASE: unlocked
    // TODO-FIXME: for entry we currently inflate and then try to CAS _owner.
    // If we know we're inflating for entry it's better to inflate by swinging a
    // pre-locked ObjectMonitor pointer into the object header.   A successful
    // CAS inflates the object *and* confers ownership to the inflating thread.
    // In the current implementation we use a 2-step mechanism where we CAS()
    // to inflate and then CAS() again to try to swing _owner from null to current.
    // An inflateTry() method that we could call from enter() would be useful.

    assert(mark.is_unlocked(), "invariant: header=" INTPTR_FORMAT, mark.value());
    ObjectMonitor* m = new ObjectMonitor(object);
    // prepare m for installation - set monitor to initial state
    m->set_header(mark);

    if (object->cas_set_mark(markWord::encode(m), mark) != mark) {
      delete m;
      m = nullptr;
      continue;
      // interference - the markword changed - just retry.
      // The state-transitions are one-way, so there's no chance of
      // live-lock -- "Inflated" is an absorbing state.
    }

    // Once the ObjectMonitor is configured and object is associated
    // with the ObjectMonitor, it is safe to allow async deflation:
    ObjectSynchronizer::_in_use_list.add(m);

    log_inflate(current, object, cause);
    if (event.should_commit()) {
      post_monitor_inflate_event(&event, object, cause);
    }
    return m;
  }
}

ObjectMonitor* LightweightSynchronizer::inflate_fast_locked_object(oop object, ObjectSynchronizer::InflateCause cause, JavaThread* locking_thread, JavaThread* current) {
  assert(LockingMode == LM_LIGHTWEIGHT, "only used for lightweight");
  VerifyThreadState vts(locking_thread, current);
  assert(locking_thread->lock_stack().contains(object), "locking_thread must have object on its lock stack");

  ObjectMonitor* monitor;

  if (!UseObjectMonitorTable) {
    return inflate_into_object_header(object, cause, locking_thread, current);
  }

  // Inflating requires a hash code
  ObjectSynchronizer::FastHashCode(current, object);

  markWord mark = object->mark_acquire();
  assert(!mark.is_unlocked(), "Cannot be unlocked");

  for (;;) {
    // Fetch the monitor from the table
    monitor = get_or_insert_monitor(object, current, cause);

    // ObjectMonitors are always inserted as anonymously owned, this thread is
    // the current holder of the monitor. So unless the entry is stale and
    // contains a deflating monitor it must be anonymously owned.
    if (monitor->has_anonymous_owner()) {
      // The monitor must be anonymously owned if it was added
      assert(monitor == get_monitor_from_table(current, object), "The monitor must be found");
      // New fresh monitor
      break;
    }

    // If the monitor was not anonymously owned then we got a deflating monitor
    // from the table. We need to let the deflator make progress and remove this
    // entry before we are allowed to add a new one.
    os::naked_yield();
    assert(monitor->is_being_async_deflated(), "Should be the reason");
  }

  // Set the mark word; loop to handle concurrent updates to other parts of the mark word
  while (mark.is_fast_locked()) {
    mark = object->cas_set_mark(mark.set_has_monitor(), mark);
  }

  // Indicate that the monitor now has a known owner
  monitor->set_owner_from_anonymous(locking_thread);

  // Remove the entry from the thread's lock stack
  monitor->set_recursions(locking_thread->lock_stack().remove(object) - 1);

  if (locking_thread == current) {
    // Only change the thread local state of the current thread.
    locking_thread->om_set_monitor_cache(monitor);
  }

  return monitor;
}

ObjectMonitor* LightweightSynchronizer::inflate_and_enter(oop object, BasicLock* lock, ObjectSynchronizer::InflateCause cause, JavaThread* locking_thread, JavaThread* current) {
  assert(LockingMode == LM_LIGHTWEIGHT, "only used for lightweight");
  VerifyThreadState vts(locking_thread, current);

  // Note: In some paths (deoptimization) the 'current' thread inflates and
  // enters the lock on behalf of the 'locking_thread' thread.

  ObjectMonitor* monitor = nullptr;

  if (!UseObjectMonitorTable) {
    // Do the old inflate and enter.
    monitor = inflate_into_object_header(object, cause, locking_thread, current);

    bool entered;
    if (locking_thread == current) {
      entered = monitor->enter(locking_thread);
    } else {
      entered = monitor->enter_for(locking_thread);
    }

    // enter returns false for deflation found.
    return entered ? monitor : nullptr;
  }

  NoSafepointVerifier nsv;

  // Try to get the monitor from the thread-local cache.
  // There's no need to use the cache if we are locking
  // on behalf of another thread.
  if (current == locking_thread) {
    monitor = read_caches(current, lock, object);
  }

  // Get or create the monitor
  if (monitor == nullptr) {
    // Lightweight monitors require that hash codes are installed first
    ObjectSynchronizer::FastHashCode(locking_thread, object);
    monitor = get_or_insert_monitor(object, current, cause);
  }

  if (monitor->try_enter(locking_thread)) {
    return monitor;
  }

  // Holds is_being_async_deflated() stable throughout this function.
  ObjectMonitorContentionMark contention_mark(monitor);

  /// First handle the case where the monitor from the table is deflated
  if (monitor->is_being_async_deflated()) {
    // The MonitorDeflation thread is deflating the monitor. The locking thread
    // must spin until further progress has been made.

    // Clear the BasicLock cache as it may contain this monitor.
    lock->clear_object_monitor_cache();

    const markWord mark = object->mark_acquire();

    if (mark.has_monitor()) {
      // Waiting on the deflation thread to remove the deflated monitor from the table.
      os::naked_yield();

    } else if (mark.is_fast_locked()) {
      // Some other thread managed to fast-lock the lock, or this is a
      // recursive lock from the same thread; yield for the deflation
      // thread to remove the deflated monitor from the table.
      os::naked_yield();

    } else {
      assert(mark.is_unlocked(), "Implied");
      // Retry immediately
    }

    // Retry
    return nullptr;
  }

  for (;;) {
    const markWord mark = object->mark_acquire();
    // The mark can be in one of the following states:
    // *  inflated     - If the ObjectMonitor owner is anonymous
    //                   and the locking_thread owns the object
    //                   lock, then we make the locking_thread
    //                   the ObjectMonitor owner and remove the
    //                   lock from the locking_thread's lock stack.
    // *  fast-locked  - Coerce it to inflated from fast-locked.
    // *  neutral      - Inflate the object. Successful CAS is locked

    // CASE: inflated
    if (mark.has_monitor()) {
      LockStack& lock_stack = locking_thread->lock_stack();
      if (monitor->has_anonymous_owner() && lock_stack.contains(object)) {
        // The lock is fast-locked by the locking thread,
        // convert it to a held monitor with a known owner.
        monitor->set_owner_from_anonymous(locking_thread);
        monitor->set_recursions(lock_stack.remove(object) - 1);
      }

      break; // Success
    }

    // CASE: fast-locked
    // Could be fast-locked either by locking_thread or by some other thread.
    //
    if (mark.is_fast_locked()) {
      markWord old_mark = object->cas_set_mark(mark.set_has_monitor(), mark);
      if (old_mark != mark) {
        // CAS failed
        continue;
      }

      // Success! Return inflated monitor.
      LockStack& lock_stack = locking_thread->lock_stack();
      if (lock_stack.contains(object)) {
        // The lock is fast-locked by the locking thread,
        // convert it to a held monitor with a known owner.
        monitor->set_owner_from_anonymous(locking_thread);
        monitor->set_recursions(lock_stack.remove(object) - 1);
      }

      break; // Success
    }

    // CASE: neutral (unlocked)

    // Catch if the object's header is not neutral (not locked and
    // not marked is what we care about here).
    assert(mark.is_neutral(), "invariant: header=" INTPTR_FORMAT, mark.value());
    markWord old_mark = object->cas_set_mark(mark.set_has_monitor(), mark);
    if (old_mark != mark) {
      // CAS failed
      continue;
    }

    // Transitioned from unlocked to monitor means locking_thread owns the lock.
    monitor->set_owner_from_anonymous(locking_thread);

    return monitor;
  }

  if (current == locking_thread) {
    // One round of spinning
    if (monitor->spin_enter(locking_thread)) {
      return monitor;
    }

    // Monitor is contended, take the time before entering to fix the lock stack.
    LockStackInflateContendedLocks().inflate(current);
  }

  // enter can block for safepoints; clear the unhandled object oop
  PauseNoSafepointVerifier pnsv(&nsv);
  object = nullptr;

  if (current == locking_thread) {
    monitor->enter_with_contention_mark(locking_thread, contention_mark);
  } else {
    monitor->enter_for_with_contention_mark(locking_thread, contention_mark);
  }

  return monitor;
}

void LightweightSynchronizer::deflate_monitor(Thread* current, oop obj, ObjectMonitor* monitor) {
  if (obj != nullptr) {
    deflate_mark_word(obj);
  }
  bool removed = remove_monitor(current, monitor, obj);
  if (obj != nullptr) {
    assert(removed, "Should have removed the entry if obj was alive");
  }
}

ObjectMonitor* LightweightSynchronizer::get_monitor_from_table(Thread* current, oop obj) {
  assert(UseObjectMonitorTable, "must be");
  return ObjectMonitorTable::monitor_get(current, obj);
}

bool LightweightSynchronizer::contains_monitor(Thread* current, ObjectMonitor* monitor) {
  assert(UseObjectMonitorTable, "must be");
  return ObjectMonitorTable::contains_monitor(current, monitor);
}

bool LightweightSynchronizer::quick_enter(oop obj, BasicLock* lock, JavaThread* current) {
  assert(current->thread_state() == _thread_in_Java, "must be");
  assert(obj != nullptr, "must be");
  NoSafepointVerifier nsv;

  LockStack& lock_stack = current->lock_stack();
  if (lock_stack.is_full()) {
    // Always go into runtime if the lock stack is full.
    return false;
  }

  const markWord mark = obj->mark();

#ifndef _LP64
  // Only for 32bit which has limited support for fast locking outside the runtime.
  if (lock_stack.try_recursive_enter(obj)) {
    // Recursive lock successful.
    return true;
  }

  if (mark.is_unlocked()) {
    markWord locked_mark = mark.set_fast_locked();
    if (obj->cas_set_mark(locked_mark, mark) == mark) {
      // Successfully fast-locked, push object to lock-stack and return.
      lock_stack.push(obj);
      return true;
    }
  }
#endif

  if (mark.has_monitor()) {
    ObjectMonitor* monitor;
    if (UseObjectMonitorTable) {
      monitor = read_caches(current, lock, obj);
    } else {
      monitor = ObjectSynchronizer::read_monitor(mark);
    }

    if (monitor == nullptr) {
      // Take the slow-path on a cache miss.
      return false;
    }

    if (UseObjectMonitorTable) {
      // Set the monitor regardless of success.
      // Either we successfully lock on the monitor, or we retry with the
      // monitor in the slow path. If the monitor gets deflated, it will be
      // cleared, either by the CacheSetter if we fast lock in enter or in
      // inflate_and_enter when we see that the monitor is deflated.
      lock->set_object_monitor_cache(monitor);
    }

    if (monitor->spin_enter(current)) {
      return true;
    }
  }

  // Slow-path.
  return false;
}
