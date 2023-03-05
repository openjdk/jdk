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
  assert(src._obj != nullptr, "must be set");

  // obj was read with AS_NO_KEEPALIVE, or equivalent, like during
  // a heap walk.  The object needs to be kept alive when it is published.
  Universe::heap()->keep_alive(src._obj);

  _wh = WeakHandle(JvmtiExport::weak_tag_storage(), src._obj);
  _obj = nullptr;
}

JvmtiTagMapKey::~JvmtiTagMapKey() {
  // If obj is set null it out, this is called for stack object on lookup,
  // and it should not have a WeakHandle created for it yet.
  if (_obj != nullptr) {
    _obj = nullptr;
    assert(_wh.is_null(), "WeakHandle should be null");
  } else {
    _wh.release(JvmtiExport::weak_tag_storage());
  }
}

oop JvmtiTagMapKey::object() const {
  assert(_obj == nullptr, "Must have a handle and not object");
  return _wh.resolve();
}

oop JvmtiTagMapKey::object_no_keepalive() const {
  assert(_obj == nullptr, "Must have a handle and not object");
  return _wh.peek();
}

JvmtiTagMapTable::JvmtiTagMapTable() : _table(Constants::_table_size) {}

void JvmtiTagMapTable::clear() {
  _table.unlink_all();
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
  bool is_added = false;
  _table.put_if_absent(new_entry, tag, &is_added);
  assert(is_added, "should be added");
}

void JvmtiTagMapTable::update(oop obj, jlong tag) {
  JvmtiTagMapKey new_entry(obj);
  bool is_updated = _table.put(new_entry, tag) == false;
  assert(is_updated, "should be updated and not added");
}

void JvmtiTagMapTable::remove(oop obj) {
  JvmtiTagMapKey jtme(obj);
  bool is_removed = _table.remove(jtme);
  assert(is_removed, "remove not succesfull.");
}

void JvmtiTagMapTable::entry_iterate(JvmtiTagMapKeyClosure* closure) {
  _table.iterate(closure);
}

void JvmtiTagMapTable::resize_if_needed() {
  _table.maybe_grow();
}

void JvmtiTagMapTable::remove_dead_entries(GrowableArray<jlong>* objects) {
  struct IsDead {
    GrowableArray<jlong>* _objects;
    IsDead(GrowableArray<jlong>* objects) : _objects(objects) {}
    bool do_entry(const JvmtiTagMapKey& entry, jlong tag) {
      if (entry.object_no_keepalive() == nullptr) {
        if (_objects != nullptr) {
          _objects->append(tag);
        }
        return true;
      }
      return false;;
    }
  } is_dead(objects);
  _table.unlink(&is_dead);
}
