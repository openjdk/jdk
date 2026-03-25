/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "memory/universe.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/inlineKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiTagMapTable.hpp"


static unsigned get_value_object_hash(oop holder, int offset, Klass* klass) {
  assert(klass->is_inline_klass(), "Must be InlineKlass");
  // For inline types, use the klass as a hash code and let the equals match the obj.
  // It might have a long bucket but TBD to improve this if a customer situation arises.
  return (unsigned)((int64_t)klass >> 3);
}

static unsigned get_value_object_hash(const JvmtiHeapwalkObject & obj) {
  assert(obj.is_value(), "Must be value class");
  return get_value_object_hash(obj.obj(), obj.offset(), obj.inline_klass());
}

static bool equal_oops(oop obj1, oop obj2); // forward declaration

static bool equal_fields(char type, oop obj1, int offset1, oop obj2, int offset2) {
  switch (type) {
  case JVM_SIGNATURE_BOOLEAN:
    return obj1->bool_field(offset1) == obj2->bool_field(offset2);
  case JVM_SIGNATURE_CHAR:
    return obj1->char_field(offset1) == obj2->char_field(offset2);
  case JVM_SIGNATURE_FLOAT:
    return obj1->float_field(offset1) == obj2->float_field(offset2);
  case JVM_SIGNATURE_DOUBLE:
    return obj1->double_field(offset1) == obj2->double_field(offset2);
  case JVM_SIGNATURE_BYTE:
    return obj1->byte_field(offset1) == obj2->byte_field(offset2);
  case JVM_SIGNATURE_SHORT:
    return obj1->short_field(offset1) == obj2->short_field(offset2);
  case JVM_SIGNATURE_INT:
    return obj1->int_field(offset1) == obj2->int_field(offset2);
  case JVM_SIGNATURE_LONG:
    return obj1->long_field(offset1) == obj2->long_field(offset2);
  case JVM_SIGNATURE_CLASS:
  case JVM_SIGNATURE_ARRAY:
    return equal_oops(obj1->obj_field(offset1), obj2->obj_field(offset2));
  }
  ShouldNotReachHere();
}

static bool is_null_flat_field(oop obj, int offset, InlineKlass* klass) {
  return klass->is_payload_marked_as_null(cast_from_oop<address>(obj) + offset);
}

// For heap-allocated objects offset is 0 and 'klass' is obj1->klass() (== obj2->klass()).
// For flattened objects offset is the offset in the holder object, 'klass' is inlined object class.
// The object must be prechecked for non-null values.
static bool equal_value_objects(oop obj1, int offset1, oop obj2, int offset2, InlineKlass* klass) {
  for (JavaFieldStream fld(klass); !fld.done(); fld.next()) {
    // ignore static fields
    if (fld.access_flags().is_static()) {
      continue;
    }
    int field_offset1 = offset1 + fld.offset() - (offset1 > 0 ? klass->payload_offset() : 0);
    int field_offset2 = offset2 + fld.offset() - (offset2 > 0 ? klass->payload_offset() : 0);
    if (fld.is_flat()) { // flat value field
      InstanceKlass* holder_klass = fld.field_holder();
      InlineKlass* field_klass = holder_klass->get_inline_type_field_klass(fld.index());
      if (!fld.is_null_free_inline_type()) {
        bool field1_is_null = is_null_flat_field(obj1, field_offset1, field_klass);
        bool field2_is_null = is_null_flat_field(obj2, field_offset2, field_klass);
        if (field1_is_null != field2_is_null) {
          return false;
        }
        if (field1_is_null) { // if both fields are null, go to next field
          continue;
        }
      }

      if (!equal_value_objects(obj1, field_offset1, obj2, field_offset2, field_klass)) {
        return false;
      }
    } else {
      if (!equal_fields(fld.signature()->char_at(0), obj1, field_offset1, obj2, field_offset2)) {
        return false;
      }
    }
  }
  return true;
}

// handles null oops
static bool equal_oops(oop obj1, oop obj2) {
  if (obj1 == obj2) {
    return true;
  }

  if (obj1 != nullptr && obj2 != nullptr && obj1->klass() == obj2->klass() && obj1->is_inline_type()) {
    InlineKlass* vk = InlineKlass::cast(obj1->klass());
    return equal_value_objects(obj1, 0, obj2, 0, vk);
  }
  return false;
}


bool JvmtiHeapwalkObject::equals(const JvmtiHeapwalkObject& obj1, const JvmtiHeapwalkObject& obj2) {
  if (obj1 == obj2) { // the same oop/offset/inline_klass
    return true;
  }

  if (obj1.is_value() && obj1.inline_klass() == obj2.inline_klass()) {
    // instances of the same value class
    return equal_value_objects(obj1.obj(), obj1.offset(), obj2.obj(), obj2.offset(), obj1.inline_klass());
  }
  return false;
}


JvmtiTagMapKey::JvmtiTagMapKey(const JvmtiHeapwalkObject* obj) : _obj(obj) {
}

JvmtiTagMapKey::JvmtiTagMapKey(const JvmtiTagMapKey& src): _h() {
  if (src._obj != nullptr) {
    // move object into Handle when copying into the table
    assert(!src._obj->is_flat(), "cannot put flat object to JvmtiTagMapKey");
    _is_weak = !src._obj->is_value();

    // obj was read with AS_NO_KEEPALIVE, or equivalent, like during
    // a heap walk.  The object needs to be kept alive when it is published.
    Universe::heap()->keep_alive(src._obj->obj());

    if (_is_weak) {
      _wh = WeakHandle(JvmtiExport::weak_tag_storage(), src._obj->obj());
    } else {
      _h = OopHandle(JvmtiExport::jvmti_oop_storage(), src._obj->obj());
    }
  } else {
    // resizing needs to create a copy.
    _is_weak = src._is_weak;
    if (_is_weak) {
      _wh = src._wh;
    } else {
      _h = src._h;
    }
  }
  // obj is always null after a copy.
  _obj = nullptr;
}

void JvmtiTagMapKey::release_handle() {
  if (_is_weak) {
    _wh.release(JvmtiExport::weak_tag_storage());
  } else {
    _h.release(JvmtiExport::jvmti_oop_storage());
  }
}

JvmtiHeapwalkObject JvmtiTagMapKey::heapwalk_object() const {
  return _obj != nullptr ? JvmtiHeapwalkObject(_obj->obj(), _obj->offset(), _obj->inline_klass(), _obj->layout_kind())
                         : JvmtiHeapwalkObject(object_no_keepalive());
}

oop JvmtiTagMapKey::object() const {
  assert(_obj == nullptr, "Must have a handle and not object");
  return _is_weak ? _wh.resolve() : _h.resolve();
}

oop JvmtiTagMapKey::object_no_keepalive() const {
  assert(_obj == nullptr, "Must have a handle and not object");
  return _is_weak ? _wh.peek() : _h.peek();
}

unsigned JvmtiTagMapKey::get_hash(const JvmtiTagMapKey& entry) {
  const JvmtiHeapwalkObject* obj = entry._obj;
  assert(obj != nullptr, "must lookup obj to hash");
  if (obj->is_value()) {
    return get_value_object_hash(*obj);
  } else {
    return (unsigned)obj->obj()->identity_hash();
  }
}

bool JvmtiTagMapKey::equals(const JvmtiTagMapKey& lhs, const JvmtiTagMapKey& rhs) {
  JvmtiHeapwalkObject lhs_obj = lhs.heapwalk_object();
  JvmtiHeapwalkObject rhs_obj = rhs.heapwalk_object();
  return JvmtiHeapwalkObject::equals(lhs_obj, rhs_obj);
}

static const int INITIAL_TABLE_SIZE = 1007;
static const int MAX_TABLE_SIZE     = 0x3fffffff;

JvmtiTagMapTable::JvmtiTagMapTable() : _table(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE) {}

void JvmtiTagMapTable::clear() {
  struct RemoveAll {
    bool do_entry(JvmtiTagMapKey& entry, const jlong& tag) {
      entry.release_handle();
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

jlong* JvmtiTagMapTable::lookup(const JvmtiHeapwalkObject& obj) const {
  if (is_empty()) {
    return nullptr;
  }

  if (!obj.is_value()) {
    if (obj.obj()->fast_no_hash_check()) {
      // Objects in the table all have a hashcode, unless inlined types.
      return nullptr;
    }
  }
  JvmtiTagMapKey entry(&obj);
  jlong* found = _table.get(entry);
  return found;
}


jlong JvmtiTagMapTable::find(const JvmtiHeapwalkObject& obj) const {
  jlong* found = lookup(obj);
  return found == nullptr ? 0 : *found;
}

void JvmtiTagMapTable::add(const JvmtiHeapwalkObject& obj, jlong tag) {
  assert(!obj.is_flat(), "Cannot add flat object to JvmtiTagMapTable");
  JvmtiTagMapKey new_entry(&obj);
  bool is_added;
  if (!obj.is_value() && obj.obj()->fast_no_hash_check()) {
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

bool JvmtiTagMapTable::update(const JvmtiHeapwalkObject& obj, jlong tag) {
  jlong* found = lookup(obj);
  if (found == nullptr) {
    return false;
  }
  *found = tag;
  return true;
}

bool JvmtiTagMapTable::remove(const JvmtiHeapwalkObject& obj) {
  JvmtiTagMapKey entry(&obj);
  auto clean = [](JvmtiTagMapKey & entry, jlong tag) {
    entry.release_handle();
  };
  return _table.remove(entry, clean);
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
        entry.release_handle();
        return true;
      }
      return false;;
    }
  } is_dead(objects);
  _table.unlink(&is_dead);
}


JvmtiFlatTagMapKey::JvmtiFlatTagMapKey(const JvmtiHeapwalkObject& obj)
  : _holder(obj.obj()), _offset(obj.offset()), _inline_klass(obj.inline_klass()), _layout_kind(obj.layout_kind()) {
}

JvmtiFlatTagMapKey::JvmtiFlatTagMapKey(const JvmtiFlatTagMapKey& src) : _h() {
  // move object into Handle when copying into the table
  if (src._holder != nullptr) {
    // Holder object was read with AS_NO_KEEPALIVE. Needs to be kept alive when it is published.
    Universe::heap()->keep_alive(src._holder);
    _h = OopHandle(JvmtiExport::jvmti_oop_storage(), src._holder);
  } else {
    // resizing needs to create a copy.
    _h = src._h;
  }
  // holder object is always null after a copy.
  _holder = nullptr;
  _offset = src._offset;
  _inline_klass = src._inline_klass;
  _layout_kind = src._layout_kind;
}

JvmtiHeapwalkObject JvmtiFlatTagMapKey::heapwalk_object() const {
  return JvmtiHeapwalkObject(_holder != nullptr ? _holder : holder_no_keepalive(), _offset, _inline_klass, _layout_kind);
}

oop JvmtiFlatTagMapKey::holder() const {
  assert(_holder == nullptr, "Must have a handle and not object");
  return _h.resolve();
}

oop JvmtiFlatTagMapKey::holder_no_keepalive() const {
  assert(_holder == nullptr, "Must have a handle and not object");
  return _h.peek();

}

void JvmtiFlatTagMapKey::release_handle() {
  _h.release(JvmtiExport::jvmti_oop_storage());
}

unsigned JvmtiFlatTagMapKey::get_hash(const JvmtiFlatTagMapKey& entry) {
  return get_value_object_hash(entry._holder, entry._offset, entry._inline_klass);
}

bool JvmtiFlatTagMapKey::equals(const JvmtiFlatTagMapKey& lhs, const JvmtiFlatTagMapKey& rhs) {
  if (lhs._inline_klass == rhs._inline_klass) {
    oop lhs_obj = lhs._holder != nullptr ? lhs._holder : lhs._h.peek();
    oop rhs_obj = rhs._holder != nullptr ? rhs._holder : rhs._h.peek();
    return equal_value_objects(lhs_obj, lhs._offset, rhs_obj, rhs._offset, lhs._inline_klass);
  }
  return false;
}


JvmtiFlatTagMapTable::JvmtiFlatTagMapTable(): _table(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE) {}

JvmtiFlatTagMapTable::~JvmtiFlatTagMapTable() {
  clear();
}

jlong JvmtiFlatTagMapTable::find(const JvmtiHeapwalkObject& obj) const {
  if (is_empty()) {
    return 0;
  }

  JvmtiFlatTagMapKey entry(obj);
  jlong* found = _table.get(entry);
  return found == nullptr ? 0 : *found;
}

void JvmtiFlatTagMapTable::add(const JvmtiHeapwalkObject& obj, jlong tag) {
  assert(obj.is_value() && obj.is_flat(), "Must be flattened value object");
  JvmtiFlatTagMapKey entry(obj);
  bool is_added;
  jlong* value = _table.put_if_absent(entry, tag, &is_added);
  *value = tag; // assign the new tag
  if (is_added) {
    if (_table.maybe_grow(5, true /* use_large_table_sizes */)) {
      int max_bucket_size = DEBUG_ONLY(_table.verify()) NOT_DEBUG(0);
      log_info(jvmti, table) ("JvmtiFlatTagMapTable table resized to %d for %d entries max bucket %d",
        _table.table_size(), _table.number_of_entries(), max_bucket_size);
    }
  }
}

jlong JvmtiFlatTagMapTable::remove(const JvmtiHeapwalkObject& obj) {
  JvmtiFlatTagMapKey entry(obj);
  jlong ret = 0;
  auto clean = [&](JvmtiFlatTagMapKey& entry, jlong tag) {
    ret = tag;
    entry.release_handle();
  };
  _table.remove(entry, clean);
  return ret;
}

void JvmtiFlatTagMapTable::entry_iterate(JvmtiFlatTagMapKeyClosure* closure) {
  _table.iterate(closure);
}

void JvmtiFlatTagMapTable::clear() {
  struct RemoveAll {
    bool do_entry(JvmtiFlatTagMapKey& entry, const jlong& tag) {
      entry.release_handle();
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
