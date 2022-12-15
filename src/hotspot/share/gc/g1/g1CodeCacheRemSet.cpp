/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "code/nmethod.hpp"
#include "gc/g1/g1CodeRootSetTable.hpp"
#include "gc/g1/g1CodeCacheRemSet.hpp"
#include "gc/g1/heapRegion.hpp"
#include "memory/heap.hpp"
#include "memory/iterator.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "services/memTracker.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/stack.inline.hpp"

G1CodeRootSetTable* volatile G1CodeRootSetTable::_purge_list = NULL;

size_t G1CodeRootSetTable::mem_size() {
  return sizeof(*this) +
    _table.table_size() * sizeof(Table::Node*) +
    _table.number_of_entries() * sizeof(Table::Node);
}

bool G1CodeRootSetTable::add(nmethod* nm) {
  if (!contains(nm)) {
    _table.put(nm, nm);
    return true;
  }
  return false;
}

bool G1CodeRootSetTable::contains(nmethod* nm) {
  return _table.contains(nm);
}

bool G1CodeRootSetTable::remove(nmethod* nm) {
  return _table.remove(nm);
}

void G1CodeRootSetTable::copy_to(G1CodeRootSetTable* new_table) {
  _table.iterate_all([&new_table](nmethod* nm, nmethod* _) {
    new_table->add(nm);
  });
}

void G1CodeRootSetTable::nmethods_do(CodeBlobClosure* blk) {
  _table.iterate_all([&](nmethod* nm, nmethod* _) {
    blk->do_code_blob(nm);
  });
}

void G1CodeRootSetTable::remove_if(CleanCallback& should_remove) {
  _table.unlink(&should_remove);
}

G1CodeRootSet::~G1CodeRootSet() {
  delete _table;
}

G1CodeRootSetTable* G1CodeRootSet::load_acquire_table() {
  return Atomic::load_acquire(&_table);
}

void G1CodeRootSet::allocate_small_table() {
  G1CodeRootSetTable* temp = new G1CodeRootSetTable(SmallSize);

  Atomic::release_store(&_table, temp);
}

void G1CodeRootSetTable::purge_list_append(G1CodeRootSetTable* table) {
  for (;;) {
    table->_purge_next = _purge_list;
    G1CodeRootSetTable* old = Atomic::cmpxchg(&_purge_list, table->_purge_next, table);
    if (old == table->_purge_next) {
      break;
    }
  }
}

void G1CodeRootSetTable::purge() {
  G1CodeRootSetTable* table = _purge_list;
  _purge_list = NULL;
  while (table != NULL) {
    G1CodeRootSetTable* to_purge = table;
    table = table->_purge_next;
    delete to_purge;
  }
}

void G1CodeRootSet::move_to_large() {
  G1CodeRootSetTable* temp = new G1CodeRootSetTable(LargeSize);

  _table->copy_to(temp);

  G1CodeRootSetTable::purge_list_append(_table);

  Atomic::release_store(&_table, temp);
}

void G1CodeRootSet::purge() {
  G1CodeRootSetTable::purge();
}

size_t G1CodeRootSet::static_mem_size() {
  return G1CodeRootSetTable::static_mem_size();
}

void G1CodeRootSet::add(nmethod* method) {
  bool added = false;
  if (is_empty()) {
    allocate_small_table();
  }
  added = _table->add(method);
  if (added) {
    if (length() == Threshold) {
      move_to_large();
    }
  }
}

bool G1CodeRootSet::remove(nmethod* method) {
  bool removed = false;
  if (_table != NULL) {
    removed = _table->remove(method);
  }
  if (removed) {
    if (length() == 0) {
      clear();
    }
  }
  return removed;
}

bool G1CodeRootSet::contains(nmethod* method) {
  G1CodeRootSetTable* table = load_acquire_table(); // contains() may be called outside of lock, so ensure mem sync.
  if (table != NULL) {
    return table->contains(method);
  }
  return false;
}

void G1CodeRootSet::clear() {
  delete _table;
  _table = NULL;
}

size_t G1CodeRootSet::mem_size() {
  return sizeof(*this) + (_table != NULL ? _table->mem_size() : 0);
}

void G1CodeRootSet::nmethods_do(CodeBlobClosure* blk) const {
  if (_table != NULL) {
    _table->nmethods_do(blk);
  }
}

class CleanCallback : public StackObj {
  NONCOPYABLE(CleanCallback); // can not copy, _blobs will point to old copy
  class PointsIntoHRDetectionClosure : public OopClosure {
    HeapRegion* _hr;
   public:
    bool _points_into;
    PointsIntoHRDetectionClosure(HeapRegion* hr) : _hr(hr), _points_into(false) {}

    void do_oop(narrowOop* o) {
      do_oop_work(o);
    }

    void do_oop(oop* o) {
      do_oop_work(o);
    }

    template <typename T>
    void do_oop_work(T* p) {
      if (_hr->is_in(RawAccess<>::oop_load(p))) {
        _points_into = true;
      }
    }
  };

  PointsIntoHRDetectionClosure _detector;
  CodeBlobToOopClosure _blobs;

 public:
  CleanCallback(HeapRegion* hr) : _detector(hr), _blobs(&_detector, !CodeBlobToOopClosure::FixRelocations) {}

  bool do_entry(nmethod* nm, nmethod* _) {
    _detector._points_into = false;
    _blobs.do_code_blob(nm);
    return !_detector._points_into;
  }
};

void G1CodeRootSet::clean(HeapRegion* owner) {
  CleanCallback should_clean(owner);
  if (_table != NULL) {
    _table->remove_if(should_clean);
  }
  if (length() == 0) {
    clear();
  }
}
