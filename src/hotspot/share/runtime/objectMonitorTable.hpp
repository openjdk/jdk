/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/objectMonitor.hpp"
#include "utilities/concurrentHashTable.inline.hpp"

#ifndef SHARE_RUNTIME_OBJECTMONITORTABLE_HPP
#define SHARE_RUNTIME_OBJECTMONITORTABLE_HPP

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
    AtomicAccess::inc(&_items_count, memory_order_relaxed);
  }

  static void dec_items_count() {
    AtomicAccess::dec(&_items_count, memory_order_relaxed);
  }

  static double get_load_factor() {
    size_t count = AtomicAccess::load(&_items_count);
    return (double)count / (double)_table_size;
  }

  static size_t table_size(Thread* current = Thread::current()) {
    return ((size_t)1) << _table->get_size_log2(current);
  }

  static size_t max_log_size();

  // ~= log(AvgMonitorsPerThreadEstimate default)
  static size_t min_log_size() { return 10; }

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
  static void create();
  static void verify_monitor_get_result(oop obj, ObjectMonitor* monitor);
  static ObjectMonitor* monitor_get(Thread* current, oop obj);
  static void try_notify_grow();
  static bool should_shrink() { return false; } // Not implemented

  static constexpr double GROW_LOAD_FACTOR = 0.75;

  static bool should_grow();
  static bool should_resize();

  template<typename Task, typename... Args>
  static bool run_task(JavaThread* current, Task& task, const char* task_name, Args&... args);
  static bool grow(JavaThread* current);
  static bool clean(JavaThread* current);
  static bool resize(JavaThread* current);
  static ObjectMonitor* monitor_put_get(Thread* current, ObjectMonitor* monitor, oop obj);
  static bool remove_monitor_entry(Thread* current, ObjectMonitor* monitor);
  static bool contains_monitor(Thread* current, ObjectMonitor* monitor);
  static void print_on(outputStream* st);
};

#endif // SHARE_RUNTIME_OBJECTMONITORTABLE_HPP
