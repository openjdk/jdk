/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiTagMapTable.hpp"


JvmtiTagMapKey::JvmtiTagMapKey(oop obj) : _obj(obj) {}

JvmtiTagMapKey::JvmtiTagMapKey(const JvmtiTagMapKey& src) {
  // move object into WeakHandle when copying into the table
  if (src._obj != nullptr) {

    // obj was read with AS_NO_KEEPALIVE, or equivalent, like during
    // a heap walk.  The object needs to be kept alive when it is published.
    Universe::heap()->keep_alive(src._obj);

    _wh = WeakHandle(JvmtiExport::weak_tag_storage(), src._obj);
  } else {
    // resizing needs to create a copy.
    _wh = src._wh;
  }
  // obj is always null after a copy.
  _obj = nullptr;
}

void JvmtiTagMapKey::release_weak_handle() {
  _wh.release(JvmtiExport::weak_tag_storage());
}

oop JvmtiTagMapKey::object() const {
  assert(_obj == nullptr, "Must have a handle and not object");
  return _wh.resolve();
}

oop JvmtiTagMapKey::object_no_keepalive() const {
  assert(_obj == nullptr, "Must have a handle and not object");
  return _wh.peek();
}

static const int INITIAL_TABLE_SIZE = 1007;
static const int MAX_TABLE_SIZE     = 0x3fffffff;

JvmtiTagMapTable::JvmtiTagMapTable() : _table(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE) {}

void JvmtiTagMapTable::clear() {
  struct RemoveAll {
    bool do_entry(JvmtiTagMapKey& entry, const jlong& tag) {
      entry.release_weak_handle();
      return true;
    }
  } remove_all;
  // The unlink method of ResourceHashTable gets a pointer to a type whose 'do_entry(K,V)' method is callled
  // while iterating over all the elements of the table. If the do_entry() method returns true the element
  // will be removed.
  // In this case, we always return true from do_entry to clear all the elements.
  _table.unlink(&remove_all);

  assert(_table.number_of_entries() == 0, "should have removed all entries");
}

JvmtiTagMapTable::~JvmtiTagMapTable() {
  clear();
}

jlong JvmtiTagMapTable::find(oop obj) {
  if (is_empty()) {
    return 0;
  }

  if (obj->fast_no_hash_check()) {
    // Objects in the table all have a hashcode.
    return 0;
  }

  JvmtiTagMapKey jtme(obj);
  jlong* found = _table.get(jtme);
  return found == nullptr ? 0 : *found;
}

void JvmtiTagMapTable::add(oop obj, jlong tag) {
  JvmtiTagMapKey new_entry(obj);
  bool is_added;
  if (obj->fast_no_hash_check()) {
    // Can't be in the table so add it fast.
    is_added = _table.put_when_absent(new_entry, tag);
  } else {
    jlong* value = _table.put_if_absent(new_entry, tag, &is_added);
    *value = tag; // assign the new tag
  }
  if (is_added) {
    if (_table.maybe_grow(5, true /* use_large_table_sizes */)) {
      int max_bucket_size = DEBUG_ONLY(_table.verify()) NOT_DEBUG(0);
      log_info(jvmti, table) ("JvmtiTagMap table resized to %d for %d entries max bucket %d",
                              _table.table_size(), _table.number_of_entries(), max_bucket_size);
    }
  }
}

void JvmtiTagMapTable::remove(oop obj) {
  JvmtiTagMapKey jtme(obj);
  auto clean = [] (JvmtiTagMapKey& entry, jlong tag) {
    entry.release_weak_handle();
  };
  _table.remove(jtme, clean);
}

void JvmtiTagMapTable::entry_iterate(JvmtiTagMapKeyClosure* closure) {
  _table.iterate(closure);
}

void JvmtiTagMapTable::remove_dead_entries(GrowableArray<jlong>* objects) {
  struct IsDead {
    GrowableArray<jlong>* _objects;
    IsDead(GrowableArray<jlong>* objects) : _objects(objects) {}
    bool do_entry(JvmtiTagMapKey& entry, jlong tag) {
      if (entry.object_no_keepalive() == nullptr) {
        if (_objects != nullptr) {
          _objects->append(tag);
        }
        entry.release_weak_handle();
        return true;
      }
      return false;;
    }
  } is_dead(objects);
  _table.unlink(&is_dead);
}
