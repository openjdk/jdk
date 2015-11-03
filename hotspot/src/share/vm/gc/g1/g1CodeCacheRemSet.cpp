/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CodeCacheRemSet.hpp"
#include "gc/g1/heapRegion.hpp"
#include "memory/heap.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/stack.inline.hpp"

class CodeRootSetTable : public Hashtable<nmethod*, mtGC> {
  friend class G1CodeRootSetTest;
  typedef HashtableEntry<nmethod*, mtGC> Entry;

  static CodeRootSetTable* volatile _purge_list;

  CodeRootSetTable* _purge_next;

  unsigned int compute_hash(nmethod* nm) {
    uintptr_t hash = (uintptr_t)nm;
    return hash ^ (hash >> 7); // code heap blocks are 128byte aligned
  }

  void remove_entry(Entry* e, Entry* previous);
  Entry* new_entry(nmethod* nm);

 public:
  CodeRootSetTable(int size) : Hashtable<nmethod*, mtGC>(size, sizeof(Entry)), _purge_next(NULL) {}
  ~CodeRootSetTable();

  // Needs to be protected locks
  bool add(nmethod* nm);
  bool remove(nmethod* nm);

  // Can be called without locking
  bool contains(nmethod* nm);

  int entry_size() const { return BasicHashtable<mtGC>::entry_size(); }

  void copy_to(CodeRootSetTable* new_table);
  void nmethods_do(CodeBlobClosure* blk);

  template<typename CB>
  int remove_if(CB& should_remove);

  static void purge_list_append(CodeRootSetTable* tbl);
  static void purge();

  static size_t static_mem_size() {
    return sizeof(_purge_list);
  }
};

CodeRootSetTable* volatile CodeRootSetTable::_purge_list = NULL;

CodeRootSetTable::Entry* CodeRootSetTable::new_entry(nmethod* nm) {
  unsigned int hash = compute_hash(nm);
  Entry* entry = (Entry*) new_entry_free_list();
  if (entry == NULL) {
    entry = (Entry*) NEW_C_HEAP_ARRAY2(char, entry_size(), mtGC, CURRENT_PC);
  }
  entry->set_next(NULL);
  entry->set_hash(hash);
  entry->set_literal(nm);
  return entry;
}

void CodeRootSetTable::remove_entry(Entry* e, Entry* previous) {
  int index = hash_to_index(e->hash());
  assert((e == bucket(index)) == (previous == NULL), "if e is the first entry then previous should be null");

  if (previous == NULL) {
    set_entry(index, e->next());
  } else {
    previous->set_next(e->next());
  }
  free_entry(e);
}

CodeRootSetTable::~CodeRootSetTable() {
  for (int index = 0; index < table_size(); ++index) {
    for (Entry* e = bucket(index); e != NULL; ) {
      Entry* to_remove = e;
      // read next before freeing.
      e = e->next();
      unlink_entry(to_remove);
      FREE_C_HEAP_ARRAY(char, to_remove);
    }
  }
  assert(number_of_entries() == 0, "should have removed all entries");
  free_buckets();
  for (BasicHashtableEntry<mtGC>* e = new_entry_free_list(); e != NULL; e = new_entry_free_list()) {
    FREE_C_HEAP_ARRAY(char, e);
  }
}

bool CodeRootSetTable::add(nmethod* nm) {
  if (!contains(nm)) {
    Entry* e = new_entry(nm);
    int index = hash_to_index(e->hash());
    add_entry(index, e);
    return true;
  }
  return false;
}

bool CodeRootSetTable::contains(nmethod* nm) {
  int index = hash_to_index(compute_hash(nm));
  for (Entry* e = bucket(index); e != NULL; e = e->next()) {
    if (e->literal() == nm) {
      return true;
    }
  }
  return false;
}

bool CodeRootSetTable::remove(nmethod* nm) {
  int index = hash_to_index(compute_hash(nm));
  Entry* previous = NULL;
  for (Entry* e = bucket(index); e != NULL; previous = e, e = e->next()) {
    if (e->literal() == nm) {
      remove_entry(e, previous);
      return true;
    }
  }
  return false;
}

void CodeRootSetTable::copy_to(CodeRootSetTable* new_table) {
  for (int index = 0; index < table_size(); ++index) {
    for (Entry* e = bucket(index); e != NULL; e = e->next()) {
      new_table->add(e->literal());
    }
  }
  new_table->copy_freelist(this);
}

void CodeRootSetTable::nmethods_do(CodeBlobClosure* blk) {
  for (int index = 0; index < table_size(); ++index) {
    for (Entry* e = bucket(index); e != NULL; e = e->next()) {
      blk->do_code_blob(e->literal());
    }
  }
}

template<typename CB>
int CodeRootSetTable::remove_if(CB& should_remove) {
  int num_removed = 0;
  for (int index = 0; index < table_size(); ++index) {
    Entry* previous = NULL;
    Entry* e = bucket(index);
    while (e != NULL) {
      Entry* next = e->next();
      if (should_remove(e->literal())) {
        remove_entry(e, previous);
        ++num_removed;
      } else {
        previous = e;
      }
      e = next;
    }
  }
  return num_removed;
}

G1CodeRootSet::~G1CodeRootSet() {
  delete _table;
}

CodeRootSetTable* G1CodeRootSet::load_acquire_table() {
  return (CodeRootSetTable*) OrderAccess::load_ptr_acquire(&_table);
}

void G1CodeRootSet::allocate_small_table() {
  CodeRootSetTable* temp = new CodeRootSetTable(SmallSize);

  OrderAccess::release_store_ptr(&_table, temp);
}

void CodeRootSetTable::purge_list_append(CodeRootSetTable* table) {
  for (;;) {
    table->_purge_next = _purge_list;
    CodeRootSetTable* old = (CodeRootSetTable*) Atomic::cmpxchg_ptr(table, &_purge_list, table->_purge_next);
    if (old == table->_purge_next) {
      break;
    }
  }
}

void CodeRootSetTable::purge() {
  CodeRootSetTable* table = _purge_list;
  _purge_list = NULL;
  while (table != NULL) {
    CodeRootSetTable* to_purge = table;
    table = table->_purge_next;
    delete to_purge;
  }
}

void G1CodeRootSet::move_to_large() {
  CodeRootSetTable* temp = new CodeRootSetTable(LargeSize);

  _table->copy_to(temp);

  CodeRootSetTable::purge_list_append(_table);

  OrderAccess::release_store_ptr(&_table, temp);
}


void G1CodeRootSet::purge() {
  CodeRootSetTable::purge();
}

size_t G1CodeRootSet::static_mem_size() {
  return CodeRootSetTable::static_mem_size();
}

void G1CodeRootSet::add(nmethod* method) {
  bool added = false;
  if (is_empty()) {
    allocate_small_table();
  }
  added = _table->add(method);
  if (_length == Threshold) {
    move_to_large();
  }
  if (added) {
    ++_length;
  }
}

bool G1CodeRootSet::remove(nmethod* method) {
  bool removed = false;
  if (_table != NULL) {
    removed = _table->remove(method);
  }
  if (removed) {
    _length--;
    if (_length == 0) {
      clear();
    }
  }
  return removed;
}

bool G1CodeRootSet::contains(nmethod* method) {
  CodeRootSetTable* table = load_acquire_table();
  if (table != NULL) {
    return table->contains(method);
  }
  return false;
}

void G1CodeRootSet::clear() {
  delete _table;
  _table = NULL;
  _length = 0;
}

size_t G1CodeRootSet::mem_size() {
  return sizeof(*this) +
      (_table != NULL ? sizeof(CodeRootSetTable) + _table->entry_size() * _length : 0);
}

void G1CodeRootSet::nmethods_do(CodeBlobClosure* blk) const {
  if (_table != NULL) {
    _table->nmethods_do(blk);
  }
}

class CleanCallback : public StackObj {
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
      if (_hr->is_in(oopDesc::load_decode_heap_oop(p))) {
        _points_into = true;
      }
    }
  };

  PointsIntoHRDetectionClosure _detector;
  CodeBlobToOopClosure _blobs;

 public:
  CleanCallback(HeapRegion* hr) : _detector(hr), _blobs(&_detector, !CodeBlobToOopClosure::FixRelocations) {}

  bool operator() (nmethod* nm) {
    _detector._points_into = false;
    _blobs.do_code_blob(nm);
    return !_detector._points_into;
  }
};

void G1CodeRootSet::clean(HeapRegion* owner) {
  CleanCallback should_clean(owner);
  if (_table != NULL) {
    int removed = _table->remove_if(should_clean);
    assert((size_t)removed <= _length, "impossible");
    _length -= removed;
  }
  if (_length == 0) {
    clear();
  }
}

#ifndef PRODUCT

class G1CodeRootSetTest {
 public:
  static void test() {
    {
      G1CodeRootSet set1;
      assert(set1.is_empty(), "Code root set must be initially empty but is not.");

      assert(G1CodeRootSet::static_mem_size() == sizeof(void*),
             "The code root set's static memory usage is incorrect, " SIZE_FORMAT " bytes", G1CodeRootSet::static_mem_size());

      set1.add((nmethod*)1);
      assert(set1.length() == 1, "Added exactly one element, but set contains "
             SIZE_FORMAT " elements", set1.length());

      const size_t num_to_add = (size_t)G1CodeRootSet::Threshold + 1;

      for (size_t i = 1; i <= num_to_add; i++) {
        set1.add((nmethod*)1);
      }
      assert(set1.length() == 1,
             "Duplicate detection should not have increased the set size but "
             "is " SIZE_FORMAT, set1.length());

      for (size_t i = 2; i <= num_to_add; i++) {
        set1.add((nmethod*)(uintptr_t)(i));
      }
      assert(set1.length() == num_to_add,
             "After adding in total " SIZE_FORMAT " distinct code roots, they "
             "need to be in the set, but there are only " SIZE_FORMAT,
             num_to_add, set1.length());

      assert(CodeRootSetTable::_purge_list != NULL, "should have grown to large hashtable");

      size_t num_popped = 0;
      for (size_t i = 1; i <= num_to_add; i++) {
        bool removed = set1.remove((nmethod*)i);
        if (removed) {
          num_popped += 1;
        } else {
          break;
        }
      }
      assert(num_popped == num_to_add,
             "Managed to pop " SIZE_FORMAT " code roots, but only " SIZE_FORMAT " "
             "were added", num_popped, num_to_add);
      assert(CodeRootSetTable::_purge_list != NULL, "should have grown to large hashtable");

      G1CodeRootSet::purge();

      assert(CodeRootSetTable::_purge_list == NULL, "should have purged old small tables");

    }

  }
};

void TestCodeCacheRemSet_test() {
  G1CodeRootSetTest::test();
}

#endif
