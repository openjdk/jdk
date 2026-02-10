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
// Theory of operations -- Object Monitor Table:
//
// The OMT (Object Monitor Table) is a concurrent hash table specifically
// designed so that it (in the normal case) can be searched from C2 generated
// code.
//
// In its simplest form it consists of:
//  1. An array of pointers.
//  2. The size (capacity) of the array, which is always a power of two.
//
// When you want to find a monitor associated with an object, you extract the
// hash value of the object. Then calculate an index by taking the hash value
// and bit-wise AND it with the capacity mask of the OMT. Now use that index
// into the OMT's array of pointers. If the pointer is non null, check if it's
// a monitor pointer that is associated with the object. If so you're done. If
// THE pointer is non null, but associated with another object, you start
// looking at (index+1), (index+2) and so on, until you either find the
// correct monitor, or a null pointer. Finding a null pointer means that the
// monitor is simply not in the OMT.
//
// If the size of the pointer array is significantly larger than the number of
// pointers in it, the chance of finding the monitor in the hash index
// (without any further linear searching) is quite high. It is also straight
// forward to generate C2 code for this, which for the fast path doesn't
// contain any branching at all. See: C2_MacroAssembler::fast_lock().
//
// When the number of monitors (pointers in the array) reaches above the
// allowed limit (defined by the GROW_LOAD_FACTOR symbol) we need to grow the
// table.
//
// A simple description of how growing the OMT is done, is to say that we
// allocate a new table (twice as large as the old one), and then copy all the
// old monitor pointers from the old table to the new.
//
// But since the OMT is a concurrent hash table and things needs to work for
// other clients of the OMT while we grow it, it's gets a bit more
// complicated.
//
// Both the new and (potentially several) old table(s) may exist at the same
// time. The newest is allays called the "current", and the older ones are
// singly linked using a "prev" pointer.
//
// As soon as we have allocated and linked in the new "current" OMT, all new
// monitor pointers will be added to the new tale. Effectively making the
// atomic switch from "old current" to "new current" a linerization point.
//
// After that we start to go through all the indexes in the old table. If the
// index is empty (the pointer is null) we put a "tombstone" into that index,
// which will prevent any future concurrent insert ending up in that index.
//
// If the index contains a monitor pointer, we insert that monitor pointer
// into the OMT which can be considered as one generation newer. If the index
// contains a "removed" pointer, we just ignore it.
//
// We use special pointer values for "tombstone" and "removed". Any pointer
// that is not null, not a tombstone and not removed, is considered to be a
// pointer to a monitor.
//
// When all the monitor pointers from an old OMT has been transferred to the
// new OMT, the old table is unlinked.
//
// This copying from an old OMT to one generation newer OMT, will continue
// until all the monitor pointers from old OMTs has been transferred to the
// newest "current" OMT.
//
// The memory for old, unlinked OMTs will be freed after a thread-local
// handshake with all Java threads.
//
// Searching the OMT for a monitor pointer while there are several generations
// of the OMT, will start from the oldest OMT.
//
// A note about the GROW_LOAD_FACTOR: In order to guarantee that the add and
// search algorithms can't loop forever, we must make sure that there is at
// least one null pointer in the array to stop them from dead looping.
// Furthermore, when we grow the OMT, we must make sure that the new "current"
// can accommodate all the monitors from all older OMTs, while still being so
// sparsely populated that the C2 generated code will likely find what it's
// searching for at the hash index, without needing further linear searching.
// The grow load factor is set to 12.5%, which satisfies the above
// requirements. Don't change it for fun, it might backfire.

// -----------------------------------------------------------------------------
// ConcurrentHashTable storing links from objects to ObjectMonitors

ObjectMonitorTable::Table* volatile ObjectMonitorTable::_curr;

class ObjectMonitorTable::Table : public CHeapObj<mtObjectMonitor> {
  friend class ObjectMonitorTable;

  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, 0);
  const size_t _capacity_mask;       // One less than its power-of-two capacity
  Table* volatile _prev;             // Set while rehashing
  ObjectMonitor* volatile* _buckets; // The payload
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(_capacity_mask) + sizeof(_prev) + sizeof(_buckets));
  volatile size_t _items_count;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_CACHE_LINE_SIZE, sizeof(_items_count));

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

  ObjectMonitor* get(oop obj, intptr_t hash) {
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
      ObjectMonitor* monitor = AtomicAccess::load_acquire(bucket);

      if (monitor == tombstone() || monitor == nullptr) {
        // Not found
        break;
      }

      if (monitor != removed_entry() && monitor->object_peek() == obj) {
        // Found matching monitor.
        return monitor;
      }

      index = (index + 1) & _capacity_mask;
      assert(index != start_index, "invariant");
    }

    // Rehashing could have started by now, but if a monitor has been inserted in a
    // newer table, it was inserted after the get linearization point.
    return nullptr;
  }

  ObjectMonitor* prepare_insert(oop obj, ObjectMonitor* new_monitor, intptr_t hash) {
    // Acquire any tomb stones and relocations if prev transitioned to null.
    Table* prev = AtomicAccess::load_acquire(&_prev);
    if (prev != nullptr) {
      ObjectMonitor* result = prev->prepare_insert(obj, new_monitor, hash);
      if (result != nullptr) {
        return result;
      }
    }

    const size_t start_index = size_t(hash) & _capacity_mask;
    size_t index = start_index;

    for (;;) {
      ObjectMonitor* volatile* bucket = _buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load_acquire(bucket);

      if (monitor == nullptr) {
        // Found an empty slot to install the new monitor in.
        // To avoid concurrent inserts succeeding, place a tomb stone here.
        ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, tombstone(), memory_order_relaxed);
        if (result == monitor) {
          // Success! Nobody will try to insert here again, except reinsert from rehashing.
          return nullptr;
        }
        monitor = result;
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
      assert(index != start_index, "invariant");
    }
  }

  ObjectMonitor* get_set(oop obj, ObjectMonitor* new_monitor, intptr_t hash) {
    // Acquire any tombstones and relocations if prev transitioned to null.
    Table* prev = AtomicAccess::load_acquire(&_prev);
    if (prev != nullptr) {
      // Sprinkle tombstones in previous tables to force concurrent inserters
      // to the latest table. We only really want to try inserting in the
      // latest table.
      ObjectMonitor* result = prev->prepare_insert(obj, new_monitor, hash);
      if (result != nullptr) {
        return result;
      }
    }

    const size_t start_index = size_t(hash) & _capacity_mask;
    size_t index = start_index;

    for (;;) {
      ObjectMonitor* volatile* bucket = _buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load_acquire(bucket);

      if (monitor == nullptr) {
        // Empty slot to install the new monitor
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
          ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, tombstone(), memory_order_acq_rel);
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
      assert(index != start_index, "invariant");
    }
  }

  void remove(oop obj, ObjectMonitor* old_monitor, intptr_t hash) {
    const size_t start_index = size_t(hash) & _capacity_mask;
    size_t index = start_index;

    for (;;) {
      ObjectMonitor* volatile* bucket = _buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load_acquire(bucket);

      if (monitor == nullptr) {
        // The monitor does not exist in this table.
        break;
      }

      if (monitor == tombstone()) {
        // Stop searching at tombstones.
        break;
      }

      if (monitor == old_monitor) {
        // Found matching entry; remove it
        ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, removed_entry(), memory_order_relaxed);
        assert(result == monitor, "should not fail");
        break;
      }

      index = (index + 1) & _capacity_mask;
      assert(index != start_index, "invariant");
    }

    // Old versions are removed after newer versions to ensure that observing
    // the monitor removed and then doing a subsequent lookup results in there
    // still not being a monitor, instead of flickering back to being there.
    // Only the deflation thread rebuilds and unlinks tables, so we do not need
    // any concurrency safe prev read below.
    if (_prev != nullptr) {
      _prev->remove(obj, old_monitor, hash);
    }
  }

  void reinsert(oop obj, ObjectMonitor* new_monitor) {
    intptr_t hash = obj->mark().hash();

    const size_t start_index = size_t(hash) & _capacity_mask;
    size_t index = start_index;

    for (;;) {
      ObjectMonitor* volatile* bucket = _buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load_acquire(bucket);

      if (monitor == nullptr) {
        // Empty slot to install the new monitor.
        ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, new_monitor, memory_order_acq_rel);
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
        ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, new_monitor, memory_order_acq_rel);
        if (result == monitor) {
          // Success - unconditionally increment.
          inc_items_count();
          return;
        }

        // Another monitor was installed.
        monitor = result;
      }

      if (monitor == removed_entry()) {
        // A removed entry can be flipped back with reinsertion.
        ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, new_monitor, memory_order_release);
        if (result == monitor) {
          // Success - but don't increment; the initial entry did that for us.
          return;
        }

        // Another monitor was installed.
        monitor = result;
      }

      assert(monitor != nullptr, "invariant");
      assert(monitor != tombstone(), "invariant");
      assert(monitor != removed_entry(), "invariant");
      assert(monitor->object_peek() != obj, "invariant");

      index = (index + 1) & _capacity_mask;
      assert(index != start_index, "invariant");
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

    // Relocate entries from prev.
    for (size_t index = 0; index <= prev->_capacity_mask; index++) {
      if ((index & 127) == 0) {
        // Poll for safepoints to improve time to safepoint
        ThreadBlockInVM tbivm(current);
      }

      ObjectMonitor* volatile* bucket = prev->_buckets + index;
      ObjectMonitor* monitor = AtomicAccess::load_acquire(bucket);

      if (monitor == nullptr) {
        // Empty slot; put a tombstone there.
        ObjectMonitor* result = AtomicAccess::cmpxchg(bucket, monitor, tombstone(), memory_order_acq_rel);
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
        if (obj != nullptr) {
          // In the current implementation the deflation thread drives
          // the rebuilding, and it will already have removed any entry
          // it has deflated. The assert is ony here to make sure.
          assert(!monitor->is_being_async_deflated(), "Should be");
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
  const intptr_t hash = obj->mark().hash();
  Table* curr = AtomicAccess::load_acquire(&_curr);
  ObjectMonitor* monitor = curr->get(obj, hash);
  OrderAccess::acquire();
  return monitor;
}

// Returns a new table to try inserting into.
ObjectMonitorTable::Table* ObjectMonitorTable::grow_table(Table* curr) {
  Table* new_table = AtomicAccess::load(&_curr);
  if (new_table != curr) {
    // Table changed; no need to try further
    return new_table;
  }

  new_table = new Table(curr->capacity() << 1, curr);
  Table* result = AtomicAccess::cmpxchg(&_curr, curr, new_table, memory_order_acq_rel);
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
  const intptr_t hash = obj->mark().hash();
  Table* curr = AtomicAccess::load_acquire(&_curr);

  for (;;) {
    // Curr is the latest table and is reasonably loaded.
    ObjectMonitor* result = curr->get_set(obj, monitor, hash);
    if (result != nullptr) {
      return result;
    }
    // The table's limit was reached, we need to grow it.
    curr = grow_table(curr);
  }
}

void ObjectMonitorTable::remove_monitor_entry(Thread* current, ObjectMonitor* monitor) {
  oop obj = monitor->object_peek();
  if (obj == nullptr) {
    // Defer removal until subsequent rebuilding.
    return;
  }
  const intptr_t hash = obj->mark().hash();

  Table* curr = AtomicAccess::load_acquire(&_curr);
  curr->remove(obj, monitor, hash);
  assert(monitor_get(current, obj) != monitor, "should have been removed");
}

// Before handshake; rehash and unlink tables.
void ObjectMonitorTable::rebuild(GrowableArray<Table*>* delete_list) {
  Table* new_table;
  {
    Table* curr = AtomicAccess::load_acquire(&_curr);
    new_table = new Table(curr->capacity(), curr);
    Table* result = AtomicAccess::cmpxchg(&_curr, curr, new_table, memory_order_acq_rel);
    if (result != curr) {
      // Somebody else racingly started rehashing. Delete the
      // new_table and treat somebody else's table as the new one.
      delete new_table;
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
