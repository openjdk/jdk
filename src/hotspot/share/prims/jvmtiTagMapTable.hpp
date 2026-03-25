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

#ifndef SHARE_VM_PRIMS_TAGMAPTABLE_HPP
#define SHARE_VM_PRIMS_TAGMAPTABLE_HPP

#include "gc/shared/collectedHeap.hpp"
#include "memory/allocation.hpp"
#include "oops/layoutKind.hpp"
#include "oops/weakHandle.hpp"
#include "utilities/resizableHashTable.hpp"

class JvmtiEnv;

// Describes an object which can be tagged during heap walk operation.
// - generic heap object: _obj: oop, offset == 0, _inline_klass == nullptr;
// - value heap object: _obj: oop, offset == 0, _inline_klass == _obj.klass();
// - flat value object: _obj: holder object, offset == offset in the holder, _inline_klass == klass of the flattened object;
class JvmtiHeapwalkObject {
  oop _obj;                   // for flattened value object this is holder object
  int _offset;                // == 0 for heap objects
  InlineKlass* _inline_klass; // for value object, nullptr otherwise
  LayoutKind _layout_kind;    // layout kind in holder object, used only for flat->heap conversion

  static InlineKlass* inline_klass_or_null(oop obj) {
    Klass* k = obj->klass();
    return k->is_inline_klass() ? InlineKlass::cast(k) : nullptr;
  }
public:
  JvmtiHeapwalkObject(): _obj(nullptr), _offset(0), _inline_klass(nullptr), _layout_kind(LayoutKind::UNKNOWN) {}
  JvmtiHeapwalkObject(oop obj): _obj(obj), _offset(0), _inline_klass(inline_klass_or_null(obj)), _layout_kind(LayoutKind::REFERENCE) {}
  JvmtiHeapwalkObject(oop obj, int offset, InlineKlass* ik, LayoutKind lk): _obj(obj), _offset(offset), _inline_klass(ik), _layout_kind(lk) {}

  inline bool is_empty() const { return _obj == nullptr; }
  inline bool is_value() const { return _inline_klass != nullptr; }
  inline bool is_flat() const { return _offset != 0; }

  inline oop obj() const { return _obj; }
  inline int offset() const { return _offset; }
  inline InlineKlass* inline_klass() const { return _inline_klass; }
  inline LayoutKind layout_kind() const { return _layout_kind; }

  inline Klass* klass() const { return is_value() ? _inline_klass : obj()->klass(); }

  static bool equals(const JvmtiHeapwalkObject& obj1, const JvmtiHeapwalkObject& obj2);

  bool operator==(const JvmtiHeapwalkObject& other) const {
    // need to compare inline_klass too to handle the case when flat object has flat field at offset 0
    return _obj == other._obj && _offset == other._offset && _inline_klass == other._inline_klass;
  }
  bool operator!=(const JvmtiHeapwalkObject& other) const {
    return !(*this == other);
  }
};


// The oop is needed for lookup rather than creating a WeakHandle during
// lookup because the HeapWalker may walk soon to be dead objects and
// creating a WeakHandle for an otherwise dead object makes G1 unhappy.
//
// This class is the Key type for inserting in ResizeableHashTable
// Its get_hash() and equals() methods are also used for getting the hash
// value of a Key and comparing two Keys, respectively.
//
// Value objects: keep just one tag for all equal value objects including heap allocated value objects.
// We have to keep a strong reference to each unique value object with a non-zero tag.
// During heap walking flattened value object tags stored in separate JvmtiFlatTagMapTable,
// converted to standard strong entries in JvmtiTagMapTable outside of sefepoint.
// All equal value objects should have the same tag.
// Keep value objects alive (1 copy for each "value") until their tags are removed.

class JvmtiTagMapKey : public CHeapObj<mtServiceability> {
  union {
    WeakHandle _wh;
    OopHandle _h; // for value objects (_is_weak == false)
  };
  bool _is_weak;
  // temporarily hold obj while searching
  const JvmtiHeapwalkObject* _obj;

 public:
  JvmtiTagMapKey(const JvmtiHeapwalkObject* obj);
  // Copy ctor is called when we put entry to the hash table.
  JvmtiTagMapKey(const JvmtiTagMapKey& src);

  JvmtiTagMapKey& operator=(const JvmtiTagMapKey&) = delete;

  JvmtiHeapwalkObject heapwalk_object() const;

  oop object() const;
  oop object_no_keepalive() const;
  void release_handle();

  static unsigned get_hash(const JvmtiTagMapKey& entry);
  static bool equals(const JvmtiTagMapKey& lhs, const JvmtiTagMapKey& rhs);
};

typedef
ResizeableHashTable <JvmtiTagMapKey, jlong,
                              AnyObj::C_HEAP, mtServiceability,
                              JvmtiTagMapKey::get_hash,
                              JvmtiTagMapKey::equals> ResizableHT;

// A supporting class for iterating over all entries in Hashmap
class JvmtiTagMapKeyClosure {
public:
  virtual bool do_entry(JvmtiTagMapKey& key, jlong& value) = 0;
};

class JvmtiTagMapTable : public CHeapObj<mtServiceability> {
 private:
  ResizableHT _table;

  jlong* lookup(const JvmtiHeapwalkObject& obj) const;

 public:
  JvmtiTagMapTable();
  ~JvmtiTagMapTable();

  int number_of_entries() const { return _table.number_of_entries(); }

  jlong find(const JvmtiHeapwalkObject& obj) const;
  // obj cannot be flat
  void add(const JvmtiHeapwalkObject& obj, jlong tag);
  // update the tag if the entry exists, returns false otherwise
  bool update(const JvmtiHeapwalkObject& obj, jlong tag);

  bool remove(const JvmtiHeapwalkObject& obj);

  // iterate over all entries in the hashmap
  void entry_iterate(JvmtiTagMapKeyClosure* closure);

  bool is_empty() const { return _table.number_of_entries() == 0; }

  // Cleanup cleared entries and store dead object tags in objects array
  void remove_dead_entries(GrowableArray<jlong>* objects);
  void clear();
};


// This class is the Key type for hash table to keep flattened value objects during heap walk operations.
// The objects needs to be moved to JvmtiTagMapTable outside of safepoint.
class JvmtiFlatTagMapKey: public CHeapObj<mtServiceability> {
private:
  // holder object
  OopHandle _h;
  // temporarily holds holder object while searching
  oop _holder;
  int _offset;
  InlineKlass* _inline_klass;
  LayoutKind _layout_kind;
public:
  JvmtiFlatTagMapKey(const JvmtiHeapwalkObject& obj);
  // Copy ctor is called when we put entry to the hash table.
  JvmtiFlatTagMapKey(const JvmtiFlatTagMapKey& src);

  JvmtiFlatTagMapKey& operator=(const JvmtiFlatTagMapKey&) = delete;

  JvmtiHeapwalkObject heapwalk_object() const;

  oop holder() const;
  oop holder_no_keepalive() const;
  int offset() const { return _offset; }
  InlineKlass* inline_klass() const { return _inline_klass; }
  LayoutKind layout_kind() const { return _layout_kind; }

  void release_handle();

  static unsigned get_hash(const JvmtiFlatTagMapKey& entry);
  static bool equals(const JvmtiFlatTagMapKey& lhs, const JvmtiFlatTagMapKey& rhs);
};

typedef
ResizeableHashTable <JvmtiFlatTagMapKey, jlong,
                     AnyObj::C_HEAP, mtServiceability,
                     JvmtiFlatTagMapKey::get_hash,
                     JvmtiFlatTagMapKey::equals> FlatObjectHashtable;

// A supporting class for iterating over all entries in JvmtiFlatTagMapTable.
class JvmtiFlatTagMapKeyClosure {
public:
  virtual bool do_entry(JvmtiFlatTagMapKey& key, jlong& value) = 0;
};

class JvmtiFlatTagMapTable: public CHeapObj<mtServiceability> {
private:
  FlatObjectHashtable _table;

public:
  JvmtiFlatTagMapTable();
  ~JvmtiFlatTagMapTable();

  int number_of_entries() const { return _table.number_of_entries(); }

  jlong find(const JvmtiHeapwalkObject& obj) const;
  // obj must be flat
  void add(const JvmtiHeapwalkObject& obj, jlong tag);

  // returns tag for the entry, 0 is not found
  jlong remove(const JvmtiHeapwalkObject& obj);

  // iterate over entries in the hashmap
  void entry_iterate(JvmtiFlatTagMapKeyClosure* closure);

  bool is_empty() const { return _table.number_of_entries() == 0; }

  void clear();
};

#endif // SHARE_VM_PRIMS_TAGMAPTABLE_HPP
