/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

ObjectMonitorTable::Table* volatile ObjectMonitorTable::_curr;

class ObjectMonitorTable::Table : public CHeapObj<mtObjectMonitor> {
  friend class ObjectMonitorTable;

  const size_t _capacity_mask;       // One less than its power-of-two capacity
  Table* volatile _prev;             // Set while rehashing
  ObjectMonitor* volatile* _buckets; // The payload

  char _padding[DEFAULT_CACHE_LINE_SIZE];

  volatile size_t _items_count;

  static ObjectMonitor* tombstone() {
    return (ObjectMonitor*)ObjectMonitorTable::SpecialPointerValues::tombstone;
  }

  static ObjectMonitor* removed_entry() {
    return (ObjectMonitor*)ObjectMonitorTable::SpecialPointerValues::removed;
  }

  // Make sure we leave space for previous versions to relocate too.
  bool try_inc_items_count() {
    for (;;) {
      size_t population = AtomicAccess::load(&_items_count);
      if (should_grow(population)) {
        return false;
      }
      if (AtomicAccess::cmpxchg(&_items_count, population, population + 1, memory_order_relaxed) == population) {
        return true;
      }
    }
  }

  double get_load_factor(size_t count) {
    return (double)count / (double)capacity();
  }

  void inc_items_count() {
    AtomicAccess::inc(&_items_count, memory_order_relaxed);
  }

  void dec_items_count() {
    AtomicAccess::dec(&_items_count, memory_order_relaxed);
  }

public:
  Table(size_t capacity, Table* prev)
    : _capacity_mask(capacity - 1),
      _prev(prev),
      _buckets(NEW_C_HEAP_ARRAY(ObjectMonitor*, capacity, mtObjectMonitor)),
      _items_count(0)
  {
    for (size_t i = 0; i < capacity; ++i) {
      _buckets[i] = nullptr;
    }
  }

  ~Table() {
    FREE_C_HEAP_ARRAY(ObjectMonitor*, _buckets);
  }

  Table* prev() {
    return AtomicAccess::load(&_prev);
  }

  size_t capacity() {
    return _capacity_mask + 1;
  }

  bool should_grow(size_t population) {
    return get_load_factor(population) > GROW_LOAD_FACTOR;
  }

  bool should_grow() {
    return should_grow(AtomicAccess::load(&_items_count));
  }

  ObjectMonitor* get(oop obj, int hash) {
    // Acquire tombstones and relocations in case prev transitioned to null
    Table* prev = AtomicAccess::load_acquire(&_prev);
    if (prev != nullptr) {
      ObjectMonitor* result = prev->get(obj, hash);
      if (result != nullptr) {
        return result;
      }
    }

    const size_t start_index = size_t(hash) & _capacity_mask;
    size_t index = start_index;

    for (;;) {
      ObjectMonitor* volatile* bucket = _buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load(bucket);

      if (monitor == tombstone() || monitor == nullptr) {
        // Not found
        break;
      }

      if (monitor != removed_entry() && monitor->object_peek() == obj) {
        // Found matching monitor.
        OrderAccess::acquire();
        return monitor;
      }

      index = (index + 1) & _capacity_mask;
      if (index == start_index) {
        // Not found - wrap around.
        break;
      }
    }

    // Rehashing could have stareted by now, but if a monitor has been inserted in a
    // newer table, it was inserted after the get linearization point.
    return nullptr;
  }

  ObjectMonitor* get_set(oop obj, ObjectMonitor* new_monitor, int hash) {
    // Acquire any tombstones and relocations if prev transitioned to null.
    Table* prev = AtomicAccess::load_acquire(&_prev);
    if (prev != nullptr) {
      ObjectMonitor* result = prev->get_set(obj, new_monitor, hash);
      if (result != nullptr) {
        return result;
      }
    }

    const size_t start_index = size_t(hash) & _capacity_mask;
    size_t index = start_index;

    for (;;) {
      ObjectMonitor* volatile* bucket = _buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load(bucket);

      if (monitor == nullptr) {
        // Empty slot to install the new monitor.
        if (try_inc_items_count()) {
          // Succeeding in claiming an item.
          ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, new_monitor, memory_order_release);
          if (result == monitor) {
            // Success - already incremented.
            return new_monitor;
          }

          // Something else was installed in place.
          dec_items_count();
          monitor = result;
        } else {
          // Out of allowance; leaving place for rehashing to succeed.
          // To avoid concurrent inserts succeeding, place a tombstone here.
          ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, tombstone());
          if (result == monitor) {
            // Success; nobody will try to insert here again, except reinsert from rehashing.
            return nullptr;
          }
          monitor = result;
        }
      }

      if (monitor == tombstone()) {
        // Can't insert into this table.
        return nullptr;
      }

      if (monitor != removed_entry() && monitor->object_peek() == obj) {
        // Found matching monitor.
        return monitor;
      }

      index = (index + 1) & _capacity_mask;
      if (index == start_index) {
        // No slot to install in this table.
        return nullptr;
      }
    }
  }

  void remove(oop obj, ObjectMonitor* old_monitor, int hash) {
    // Acquire any tombstones and relocations if prev transitioned to null.
    Table* prev = AtomicAccess::load_acquire(&_prev);
    if (prev != nullptr) {
      prev->remove(obj, old_monitor, hash);
    }

    const size_t start_index = size_t(hash) & _capacity_mask;
    size_t index = start_index;

    for (;;) {
      ObjectMonitor* volatile* bucket = _buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load(bucket);

      if (monitor == nullptr) {
        // Monitor does not exist in this table.
        return;
      }

      if (monitor == old_monitor) {
        // Found matching entry; remove it.
        AtomicAccess::cmpxchg(bucket, monitor, removed_entry());
        return;
      }

      index = (index + 1) & _capacity_mask;
      if (index == start_index) {
        // Not found
        return;
      }
    }
  }

  void reinsert(oop obj, ObjectMonitor* new_monitor) {
    int hash = obj->mark().hash();

    const size_t start_index = size_t(hash) & _capacity_mask;
    size_t index = start_index;

    for (;;) {
      ObjectMonitor* volatile* bucket = _buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load(bucket);

      if (monitor == nullptr) {
        // Empty slot to install the new monitor.
        ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, new_monitor, memory_order_release);
        if (result == monitor) {
          // Success - unconditionally increment.
          inc_items_count();
          return;
        }

        // Another monitor was installed.
        monitor = result;
      }

      if (monitor == tombstone()) {
        // A concurrent inserter did not get enough allowance in the table.
        // But reinsert always succeeds - we will take the spot.
        ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, new_monitor, memory_order_release);
        if (result == monitor) {
          // Success - unconditionally increment.
          inc_items_count();
          return;
        }

        // Another monitor was installed.
        monitor = result;
      }

      assert(monitor != nullptr, "invariant");
      assert(monitor != tombstone(), "invariant");
      assert(monitor == removed_entry() || monitor->object_peek() != obj, "invariant");

      index = (index + 1) & _capacity_mask;
      assert(index != start_index, "should never be full");
    }
  }

  void rebuild() {
    Table* prev = _prev;
    if (prev == nullptr) {
      // Base case for recursion - no previous version.
      return;
    }

    // Finish rebuilding up to prev as target so we can use prev as source.
    prev->rebuild();

    JavaThread* current = JavaThread::current();

    // Relocate entries from prev after
    for (size_t index = 0; index <= prev->_capacity_mask; index++) {
      if ((index & 128) == 0) {
        // Poll for safepoints to improve time to safepoint
        ThreadBlockInVM tbivm(current);
      }

      ObjectMonitor* volatile* bucket = prev->_buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load(bucket);

      if (monitor == nullptr) {
        // Empty slot; put a tombstone there.
        ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, tombstone(), memory_order_relaxed);
        if (result == nullptr) {
          // Success; move to next entry.
          continue;
        }

        // Concurrent insert; relocate.
        monitor = result;
      }

      if (monitor != tombstone() && monitor != removed_entry()) {
        // A monitor
        oop obj = monitor->object_peek();
        if (!monitor->is_being_async_deflated() && obj != nullptr) {
          // Re-insert still live monitor.
          reinsert(obj, monitor);
        }
      }
    }

    // Unlink this table, releasing the tombstones and relocations.
    AtomicAccess::release_store(&_prev, (Table*)nullptr);
  }
};

void ObjectMonitorTable::create() {
  _curr = new Table(128, nullptr);
}

ObjectMonitor* ObjectMonitorTable::monitor_get(Thread* current, oop obj) {
  const int hash = obj->mark().hash();
  Table* curr = AtomicAccess::load_acquire(&_curr);

  return curr->get(obj, hash);
}

// Returns a new table to try inserting into.
ObjectMonitorTable::Table *ObjectMonitorTable::grow_table(Table *curr) {
  Table *new_table = AtomicAccess::load(&_curr);
  if (new_table != curr) {
    // Table changed; no need to try further
    return new_table;
  }

  new_table = new Table(curr->capacity() << 1, curr);
  Table *result =
      AtomicAccess::cmpxchg(&_curr, curr, new_table, memory_order_acq_rel);
  if (result == curr) {
    // Successfully started rehashing.
    log_info(monitorinflation)("Growing object monitor table");
    ObjectSynchronizer::request_deflate_idle_monitors();
    return new_table;
  }

  // Somebody else started rehashing; restart in new table.
  delete new_table;

  return result;
}

ObjectMonitor* ObjectMonitorTable::monitor_put_get(Thread* current, ObjectMonitor* monitor, oop obj) {
  const int hash = obj->mark().hash();
  Table* curr = AtomicAccess::load_acquire(&_curr);

  for (;;) {
    // Curr is the latest table and is reasonably loaded.
    ObjectMonitor* result = curr->get_set(obj, monitor, hash);
    if (result != nullptr) {
      return result;
      // Table rehashing started; try again in the new table
    }
    curr = grow_table(curr);
  }
}

void ObjectMonitorTable::remove_monitor_entry(Thread* current, ObjectMonitor* monitor) {
  oop obj = monitor->object_peek();
  if (obj == nullptr) {
    // Defer removal until subsequent rebuilding.
    return;
  }
  const int hash = obj->mark().hash();

  Table* curr = AtomicAccess::load_acquire(&_curr);
  curr->remove(obj, monitor, hash);
}

// Before handshake; rehash and unlink tables.
void ObjectMonitorTable::rebuild(GrowableArray<Table*>* delete_list) {
  Table* new_table;
  {
    Table* curr = AtomicAccess::load_acquire(&_curr);
    new_table = new Table(curr->capacity(), curr);
    Table* result = AtomicAccess::cmpxchg(&_curr, curr, new_table, memory_order_release);
    if (result != curr) {
      // Somebody else racingly started rehashing; try again.
      new_table = result;
    }
  }

  for (Table* curr = new_table->prev(); curr != nullptr; curr = curr->prev()) {
    delete_list->append(curr);
  }

  // Rebuild with the new table as target.
  new_table->rebuild();
}

// After handshake; destroy old tables
void ObjectMonitorTable::destroy(GrowableArray<Table*>* delete_list) {
  for (ObjectMonitorTable::Table* table: *delete_list) {
    delete table;
  }
}

address ObjectMonitorTable::current_table_address() {
  return (address)(&_curr);
}

ByteSize ObjectMonitorTable::table_capacity_mask_offset() {
  return byte_offset_of(Table, _capacity_mask);
}

ByteSize ObjectMonitorTable::table_buckets_offset() {
  return byte_offset_of(Table, _buckets);
}
