/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/classLoaderData.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.hpp"
#include "oops/jmethodIDTable.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/macros.hpp"

// There are two hashtables to implement jmethodID.  One CHT to lookup Method to get a jmethodID
// and the other CHT is to get the Method from the jmethodID.
// The CHT is for performance because it is has lock free lookup.

static uint64_t _jmethodID_counter = 0;

static intx method_hash(Method* m) { return m->name()->identity_hash(); }

class JmethodEntry {
 public:
  uint64_t _id;
  Method*  _method;

  JmethodEntry(uint64_t id, Method* method) : _id(id), _method(method) {}
};

class ConfigBase : public AllStatic {
 public:
  typedef JmethodEntry Value;
  static void* allocate_node(void* context, size_t size, Value const& value) {
    return AllocateHeap(size, mtClass);
  }
  static void free_node(void* context, void* memory, Value const& value) {
    FreeHeap(memory);
  }
};

class JmethodIDTableConfig : public ConfigBase {
 public:
  static uintx get_hash(Value const& value, bool* is_dead) {
    *is_dead = false;
    return value._id;
  }
};

class MethodTableConfig : public ConfigBase {
 public:
  static uintx get_hash(Value const& value, bool* is_dead) {
    *is_dead = false;
    return method_hash(value._method);
  }
};

using MethodIdTable = ConcurrentHashTable<JmethodIDTableConfig, mtClass>;
static MethodIdTable* _jmethod_id_table = nullptr;

using MethodTable = ConcurrentHashTable<MethodTableConfig, mtClass>;
static MethodTable* _method_table = nullptr;

void JmethodIDTable::initialize() {
  const size_t start_size = 10;
  // 2^24 is max size
  const size_t end_size = 24;
  // If a chain gets to 32 something might be wrong
  const size_t grow_hint = 32;

  _jmethod_id_table  = new MethodIdTable(start_size, end_size, grow_hint);

  // Initialize Method lookup table.
  _method_table = new MethodTable(start_size, end_size, grow_hint);
}

class JmethodIDLookup : StackObj {
 private:
  uint64_t _mid;

 public:
  JmethodIDLookup(const uint64_t mid) : _mid(mid) {}
  uintx get_hash() const {
    return _mid;
  }
  bool equals(JmethodEntry* value, bool* is_dead) {
    *is_dead = false;
    return _mid == value->_id;
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
  assert (!needs_rehashing, "should never need rehashing");
  return result;
}

Method* JmethodIDTable::resolve_jmethod_id(jmethodID mid) {
  JmethodEntry* result = get_jmethod_entry(mid);
  return result == nullptr ? nullptr : result->_method;
}

class MethodLookup : StackObj {
 private:
  Method* _method;

 public:
  MethodLookup(Method* method) : _method(method) {}
  uintx get_hash() const {
    return method_hash(_method);
  }
  bool equals(JmethodEntry* value, bool* is_dead) {
    *is_dead = false;
    return _method == value->_method;
  }
};

static JmethodEntry* get_method_entry(Method* method) {
  Thread* current = Thread::current();
  MethodLookup lookup(method);
  JmethodEntry* result = nullptr;
  auto get = [&] (JmethodEntry* value) {
    // function called if value is found so is never null
    result = value;
  };
  bool needs_rehashing = false;
  _method_table->get(current, lookup, get, &needs_rehashing);
  return result;
}

jmethodID JmethodIDTable::find_jmethod_id_or_null(Method* m) {
  JmethodEntry* entry = get_method_entry(m);
  return (entry == nullptr) ? nullptr : (jmethodID)entry->_id;
}

static void new_jmethod_id(Method* m, uint64_t mid) {
  bool grow_hint, clean_hint, created;

  Thread* current = Thread::current();
  JmethodEntry new_entry(mid, m);
  JmethodIDLookup lookup(mid);
  created = _jmethod_id_table->insert(current, lookup, new_entry, &grow_hint, &clean_hint);
  assert(created, "must be");

  MethodLookup lookup2(m);
  created = _method_table->insert(current, lookup2, new_entry, &grow_hint, &clean_hint);
  assert(created, "must be");
  // Resize both tables if the method_table needs to grow.  The method_table has a worse
  // distribution
  if (grow_hint) {
    _method_table->grow(current);
    _jmethod_id_table->grow(current);
  }
}

// Add a method id to the jmethod_ids
jmethodID JmethodIDTable::get_or_make_jmethod_id(ClassLoaderData* cld, Method* m) {
  // Have to add jmethod_ids() to class loader data thread-safely.
  // Also have to add the method to the list safely, which the lock
  // protects as well.
  MutexLocker ml(JmethodIdCreation_lock, Mutex::_no_safepoint_check_flag);
  JmethodEntry* entry = get_method_entry(m);
  if (entry == nullptr) {
    new_jmethod_id(m, ++_jmethodID_counter);

    // Add to growable array in CLD
    cld->add_jmethod_id((jmethodID)_jmethodID_counter);
    return (jmethodID)_jmethodID_counter;
  } else {
    assert(entry->_id != 0, "should be valid");
    return (jmethodID)entry->_id;
  }
}

void JmethodIDTable::remove(jmethodID mid) {
  assert_locked_or_safepoint(JmethodIdCreation_lock);
  JmethodIDLookup lookup((uint64_t)mid);
  Thread* current = Thread::current();
  JmethodEntry* result;
  auto get = [&] (JmethodEntry* value) {
    // function called if value is found so is never null
    result = value;
  };
  bool removed = _jmethod_id_table->remove(current, lookup, get);
  assert(removed, "should be");

  MethodLookup lookup2(result->_method);
  removed = _method_table->remove(current, lookup2);
  assert(removed, "should be");
}

void JmethodIDTable::change_method_associated_with_jmethod_id(jmethodID jmid, Method* new_method) {
  assert_locked_or_safepoint(JmethodIdCreation_lock);
  // Remove old method associated with jmid
  remove(jmid);

  // Add new_method associated with jmid
  new_jmethod_id(new_method, (uint64_t)jmid);
}
