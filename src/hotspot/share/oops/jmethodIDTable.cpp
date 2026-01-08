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

#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "oops/jmethodIDTable.hpp"
#include "oops/method.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/macros.hpp"

// Save (jmethod, Method*) in a hashtable to lookup Method.
// The CHT is for performance because it has lock free lookup.
// The value of the next jmethodID.  This only increments (always unique IDs).
static uint64_t _jmethodID_counter = 0;
// Tracks the number of jmethodID entries in the _jmethod_id_table.
// Incremented on insert, decremented on remove. Used to track if we need to resize the table.
static uint64_t _jmethodID_entry_count = 0;

uint64_t JmethodIDTable::get_entry_count() { return _jmethodID_entry_count; }

class JmethodEntry {
 public:
  uint64_t _id;
  Method*  _method;

  JmethodEntry(uint64_t id, Method* method) : _id(id), _method(method) {}
};

class JmethodIDTableConfig : public AllStatic {
 public:
  typedef JmethodEntry Value;
  static void* allocate_node(void* context, size_t size, Value const& value) {
    return AllocateHeap(size, mtJNI);
  }
  static void free_node(void* context, void* memory, Value const& value) {
    FreeHeap(memory);
  }
  static uintx get_hash(Value const& value, bool* is_dead) {
    *is_dead = false;
    return value._id;
  }
  static bool is_dead(Value const& value) {
    return false;
  }
};

using MethodIdTable = ConcurrentHashTable<JmethodIDTableConfig, mtJNI>;
static MethodIdTable* _jmethod_id_table = nullptr;

void JmethodIDTable::initialize() {
  const size_t start_size = 10;
  // 2^24 is max size
  const size_t end_size = 24;
  // If a chain gets to 32 something might be wrong.
  const size_t grow_hint = 32;

  _jmethod_id_table  = new MethodIdTable(start_size, end_size, grow_hint);
}

class JmethodIDLookup : StackObj {
 private:
  uint64_t _mid;

 public:
  JmethodIDLookup(const uint64_t mid) : _mid(mid) {}
  uintx get_hash() const {
    return _mid;
  }
  bool equals(JmethodEntry* value) {
    return _mid == value->_id;
  }

  bool is_dead(JmethodEntry* value) {
    return false;
  }
};

static JmethodEntry* get_jmethod_entry(jmethodID mid) {
  assert(mid != nullptr, "JNI method id should not be null");

  Thread* current = Thread::current();
  JmethodIDLookup lookup((uint64_t)mid);
  JmethodEntry* result = nullptr;
  auto get = [&] (JmethodEntry* value) {
    // function called if value is found so is never null
    result = value;
  };
  bool needs_rehashing = false;
  _jmethod_id_table->get(current, lookup, get, &needs_rehashing);
  assert(!needs_rehashing, "should never need rehashing");
  return result;
}

Method* JmethodIDTable::resolve_jmethod_id(jmethodID mid) {
  JmethodEntry* result = get_jmethod_entry(mid);
  return result == nullptr ? nullptr : result->_method;
}

const unsigned _resize_load_trigger = 5;       // load factor that will trigger the resize

static unsigned table_size(Thread* current) {
  return 1 << _jmethod_id_table->get_size_log2(current);
}

static bool needs_resize(Thread* current) {
  return ((_jmethodID_entry_count > (_resize_load_trigger * table_size(current))) &&
         !_jmethod_id_table->is_max_size_reached());
}

// Add a method id to the jmethod_ids.
jmethodID JmethodIDTable::make_jmethod_id(Method* m) {
  bool grow_hint, clean_hint;

  assert_locked_or_safepoint(JmethodIdCreation_lock);
  // Update jmethodID global counter.
  _jmethodID_counter++;

  JmethodEntry new_entry(_jmethodID_counter, m);
  Thread* current = Thread::current();
  JmethodIDLookup lookup(_jmethodID_counter);
  bool created = _jmethod_id_table->insert(current, lookup, new_entry, &grow_hint, &clean_hint);
  assert(created, "must be");
  log_debug(jmethod)("Inserted jmethod id " UINT64_FORMAT_X, _jmethodID_counter);
  // Increment number of entries in the table.
  _jmethodID_entry_count++;

  // Resize table if it needs to grow.  The _jmethod_id_table has a good distribution.
  if (needs_resize(current)) {
    _jmethod_id_table->grow(current);
    log_info(jmethod)("Growing table to %d for " UINT64_FORMAT " entries", table_size(current), _jmethodID_counter);
  }
  return (jmethodID)_jmethodID_counter;
}

void JmethodIDTable::remove(jmethodID jmid) {
  assert_locked_or_safepoint(JmethodIdCreation_lock);
  JmethodIDLookup lookup((uint64_t)jmid);
  Thread* current = Thread::current();
  JmethodEntry* result;
  auto get = [&] (JmethodEntry* value) {
    // The function that is called if value is found, so is never null.
    result = value;
  };
  bool removed = _jmethod_id_table->remove(current, lookup, get);
  assert(removed, "must be");
  log_debug(jmethod)("Removed jmethod id " UINT64_FORMAT_X, (uint64_t)jmid);
  // Decrement number of entries in the table.
  _jmethodID_entry_count--;
}

void JmethodIDTable::change_method_associated_with_jmethod_id(jmethodID jmid, Method* new_method) {
  assert_locked_or_safepoint(JmethodIdCreation_lock);
  JmethodEntry* result = get_jmethod_entry(jmid);
  // Change table entry to point to the new method.
  log_debug(jmethod)("Changed jmethod id " UINT64_FORMAT_X " from " PTR_FORMAT " to " PTR_FORMAT, (uint64_t)jmid,
                     p2i(result->_method), p2i(new_method));
  result->_method = new_method;
}

void JmethodIDTable::clear_jmethod_id(jmethodID jmid, Method* obsolete_method) {
  assert_locked_or_safepoint(JmethodIdCreation_lock);
  JmethodEntry* result = get_jmethod_entry(jmid);
  // We need to make sure that jmethodID actually resolves to this method
  // - multiple redefined versions may share jmethodID slots and if a method
  //   has already been rewired to a newer version we could be clearing
  //   the reference to a still existing method instance.
  if (result->_method == obsolete_method) {
    result->_method = nullptr;
  }
}
