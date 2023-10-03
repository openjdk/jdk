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

#ifndef SHARE_VM_PRIMS_TAGMAPTABLE_HPP
#define SHARE_VM_PRIMS_TAGMAPTABLE_HPP

#include "gc/shared/collectedHeap.hpp"
#include "memory/allocation.hpp"
#include "oops/weakHandle.hpp"
#include "utilities/resizeableResourceHash.hpp"

class JvmtiEnv;
class JvmtiTagMapKeyClosure;

// The oop is needed for lookup rather than creating a WeakHandle during
// lookup because the HeapWalker may walk soon to be dead objects and
// creating a WeakHandle for an otherwise dead object makes G1 unhappy.
//
// This class is the Key type for inserting in ResizeableResourceHashTable
// Its get_hash() and equals() methods are also used for getting the hash
// value of a Key and comparing two Keys, respectively.
class JvmtiTagMapKey : public CHeapObj<mtServiceability> {
  WeakHandle _wh;
  oop _obj; // temporarily hold obj while searching
 public:
  JvmtiTagMapKey(oop obj);
  JvmtiTagMapKey(const JvmtiTagMapKey& src);
  JvmtiTagMapKey& operator=(const JvmtiTagMapKey&) = delete;

  oop object() const;
  oop object_no_keepalive() const;
  void release_weak_handle();

  static unsigned get_hash(const JvmtiTagMapKey& entry) {
    assert(entry._obj != nullptr, "must lookup obj to hash");
    return (unsigned)entry._obj->identity_hash();
  }

  static bool equals(const JvmtiTagMapKey& lhs, const JvmtiTagMapKey& rhs) {
    oop lhs_obj = lhs._obj != nullptr ? lhs._obj : lhs.object_no_keepalive();
    oop rhs_obj = rhs._obj != nullptr ? rhs._obj : rhs.object_no_keepalive();
    return lhs_obj == rhs_obj;
  }
};

typedef
ResizeableResourceHashtable <JvmtiTagMapKey, jlong,
                              AnyObj::C_HEAP, mtServiceability,
                              JvmtiTagMapKey::get_hash,
                              JvmtiTagMapKey::equals> ResizableResourceHT;

class JvmtiTagMapTable : public CHeapObj<mtServiceability> {
 private:
  ResizableResourceHT _table;

 public:
  JvmtiTagMapTable();
  ~JvmtiTagMapTable();

  jlong find(oop obj);
  void add(oop obj, jlong tag);

  void remove(oop obj);

  // iterate over all entries in the hashmap
  void entry_iterate(JvmtiTagMapKeyClosure* closure);

  bool is_empty() const { return _table.number_of_entries() == 0; }

  // Cleanup cleared entries and store dead object tags in objects array
  void remove_dead_entries(GrowableArray<jlong>* objects);
  void clear();
};

// A supporting class for iterating over all entries in Hashmap
class JvmtiTagMapKeyClosure {
 public:
  virtual bool do_entry(JvmtiTagMapKey& key, jlong& value) = 0;
};

#endif // SHARE_VM_PRIMS_TAGMAPTABLE_HPP
