/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/hashtable.hpp"
#include "utilities/resizeableResourceHash.hpp"

class JvmtiEnv;
class JvmtiTagMapEntryClosure;

class JvmtiTagMapEntry : public CHeapObj<mtInternal> {
  WeakHandle _wh;
  oop        _obj;  // temporarily hold obj while searching

 public:
  JvmtiTagMapEntry(oop obj);
  JvmtiTagMapEntry(const JvmtiTagMapEntry& src);
  JvmtiTagMapEntry& operator=(JvmtiTagMapEntry const&) = delete;

  ~JvmtiTagMapEntry();

  void resolve();
  oop object() const;
  oop object_no_keepalive() const;

  static unsigned get_hash(JvmtiTagMapEntry const &entry)  {
    assert(entry._obj != NULL, "must lookup obj to hash");
    return entry._obj->identity_hash();
  }

  static bool equals(JvmtiTagMapEntry const & lhs, JvmtiTagMapEntry const & rhs) {
    oop lhs_obj = lhs._obj != nullptr ? lhs._obj : lhs.object_no_keepalive();
    oop rhs_obj = rhs._obj != nullptr ? rhs._obj : rhs.object_no_keepalive();
    return lhs_obj == rhs_obj;
  }
};

typedef
ResizeableResourceHashtable <JvmtiTagMapEntry, jlong,
                             AnyObj::C_HEAP, mtInternal,
                             JvmtiTagMapEntry::get_hash,
                             JvmtiTagMapEntry::equals
                             > ResizableResourceHT ;

class JvmtiTagMapTable : public CHeapObj<mtInternal> {
  enum Constants {
    _table_size  = 1007
  };

  void resize_if_needed();
  ResizableResourceHT  _rrht_table;

 public:
  JvmtiTagMapTable();
  ~JvmtiTagMapTable();

  jlong find(oop obj);
  bool add(oop obj, jlong tag);

  bool remove(oop obj);

  // iterate over all entries in the hashmap
  void entry_iterate(JvmtiTagMapEntryClosure* closure);

  bool is_empty() const { return _rrht_table.number_of_entries() == 0; }

  // Cleanup cleared entries and store dead object tags in objects array
  void remove_dead_entries(GrowableArray<jlong>* objects);
  void clear();
};

// A supporting class for iterating over all entries in Hashmap
class JvmtiTagMapEntryClosure {
  public:
  virtual bool do_entry(JvmtiTagMapEntry & key, jlong & value) = 0;
};

#endif // SHARE_VM_PRIMS_TAGMAPTABLE_HPP
