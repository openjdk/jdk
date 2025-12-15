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

#include "logging/log.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitorTable.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/globalDefinitions.hpp"

// -----------------------------------------------------------------------------
// ConcurrentHashTable storing links from objects to ObjectMonitors

using ConcurrentTable = ConcurrentHashTable<ObjectMonitorTableConfig, mtObjectMonitor>;

static ConcurrentTable* _table = nullptr;
static volatile size_t _items_count = 0;
static size_t _table_size = 0;
static volatile bool _resize = false;

class ObjectMonitorTableConfig : public AllStatic {
 public:
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

void ObjectMonitorTable::inc_items_count() {
  AtomicAccess::inc(&_items_count, memory_order_relaxed);
}

void ObjectMonitorTable::dec_items_count() {
  AtomicAccess::dec(&_items_count, memory_order_relaxed);
}

double ObjectMonitorTable::get_load_factor() {
  size_t count = AtomicAccess::load(&_items_count);
  return (double)count / (double)_table_size;
}

size_t ObjectMonitorTable::table_size(Thread* current) {
  return ((size_t)1) << _table->get_size_log2(current);
}

size_t ObjectMonitorTable::max_log_size() {
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

// ~= log(AvgMonitorsPerThreadEstimate default)
size_t ObjectMonitorTable::min_log_size() {
  return 10;
}

template<typename V>
size_t ObjectMonitorTable::clamp_log_size(V log_size) {
  return MAX2(MIN2(log_size, checked_cast<V>(max_log_size())), checked_cast<V>(min_log_size()));
}

size_t ObjectMonitorTable::initial_log_size() {
  const size_t estimate = log2i(MAX2(os::processor_count(), 1)) + log2i(MAX2(AvgMonitorsPerThreadEstimate, size_t(1)));
  return clamp_log_size(estimate);
}

size_t ObjectMonitorTable::grow_hint() {
  return ConcurrentTable::DEFAULT_GROW_HINT;
}

void ObjectMonitorTable::create() {
  _table = new ConcurrentTable(initial_log_size(), max_log_size(), grow_hint());
  _items_count = 0;
  _table_size = table_size(Thread::current());
  _resize = false;
}

void ObjectMonitorTable::verify_monitor_get_result(oop obj, ObjectMonitor* monitor) {
#ifdef ASSERT
  if (SafepointSynchronize::is_at_safepoint()) {
    bool has_monitor = obj->mark().has_monitor();
    assert(has_monitor == (monitor != nullptr),
           "Inconsistency between markWord and ObjectMonitorTable has_monitor: %s monitor: " PTR_FORMAT,
           BOOL_TO_STR(has_monitor), p2i(monitor));
  }
#endif
}

ObjectMonitor* ObjectMonitorTable::monitor_get(Thread* current, oop obj) {
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

void ObjectMonitorTable::try_notify_grow() {
  if (!_table->is_max_size_reached() && !AtomicAccess::load(&_resize)) {
    AtomicAccess::store(&_resize, true);
    if (Service_lock->try_lock()) {
      Service_lock->notify();
      Service_lock->unlock();
    }
  }
}

bool ObjectMonitorTable::should_grow() {
  return get_load_factor() > GROW_LOAD_FACTOR && !_table->is_max_size_reached();
}

bool ObjectMonitorTable::should_resize() {
  return should_grow() || should_shrink() || AtomicAccess::load(&_resize);
}

template <typename Task, typename... Args>
bool ObjectMonitorTable::run_task(JavaThread* current, Task& task, const char* task_name, Args&... args) {
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

bool ObjectMonitorTable::grow(JavaThread* current) {
  ConcurrentTable::GrowTask grow_task(_table);
  if (run_task(current, grow_task, "Grow")) {
    _table_size = table_size(current);
    log_info(monitortable)("Grown to size: %zu", _table_size);
    return true;
  }
  return false;
}

bool ObjectMonitorTable::clean(JavaThread* current) {
  ConcurrentTable::BulkDeleteTask clean_task(_table);
  auto is_dead = [&](ObjectMonitor** monitor) {
    return (*monitor)->object_is_dead();
  };
  auto do_nothing = [&](ObjectMonitor** monitor) {};
  NativeHeapTrimmer::SuspendMark sm("ObjectMonitorTable");
  return run_task(current, clean_task, "Clean", is_dead, do_nothing);
}

bool ObjectMonitorTable::resize(JavaThread* current) {
  LogTarget(Info, monitortable) lt;
  bool success = false;

  if (should_grow()) {
    lt.print("Start growing with load factor %f", get_load_factor());
    success = grow(current);
  } else {
    if (!_table->is_max_size_reached() && AtomicAccess::load(&_resize)) {
      lt.print("WARNING: Getting resize hints with load factor %f", get_load_factor());
    }
    lt.print("Start cleaning with load factor %f", get_load_factor());
    success = clean(current);
  }

  AtomicAccess::store(&_resize, false);

  return success;
}

ObjectMonitor* ObjectMonitorTable::monitor_put_get(Thread* current, ObjectMonitor* monitor, oop obj) {
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

bool ObjectMonitorTable::remove_monitor_entry(Thread* current, ObjectMonitor* monitor) {
  LookupMonitor lookup_f(monitor);
  return _table->remove(current, lookup_f);
}

bool ObjectMonitorTable::contains_monitor(Thread* current, ObjectMonitor* monitor) {
  LookupMonitor lookup_f(monitor);
  bool result = false;
  auto found_f = [&](ObjectMonitor** found) {
    result = true;
  };
  _table->get(current, lookup_f, found_f);
  return result;
}

void ObjectMonitorTable::print_on(outputStream* st) {
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
