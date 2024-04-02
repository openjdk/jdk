/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CodeRootSet.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"

class G1CodeRootSetHashTableConfig : public StackObj {
public:
  using Value = nmethod*;

  static uintx get_hash(Value const& value, bool* is_dead);

  static void* allocate_node(void* context, size_t size, Value const& value) {
    return AllocateHeap(size, mtGC);
  }

  static void free_node(void* context, void* memory, Value const& value) {
    FreeHeap(memory);
  }
};

// Storage container for the code root set.
class G1CodeRootSetHashTable : public CHeapObj<mtGC> {
  using HashTable = ConcurrentHashTable<G1CodeRootSetHashTableConfig, mtGC>;
  using HashTableScanTask = HashTable::ScanTask;

  // Default (log2) number of buckets; small since typically we do not expect many
  // entries.
  static const size_t Log2DefaultNumBuckets = 2;
  static const uint BucketClaimSize = 16;

  HashTable _table;
  HashTableScanTask _table_scanner;

  size_t volatile _num_entries;

  bool is_empty() const { return number_of_entries() == 0; }

  class HashTableLookUp : public StackObj {
    nmethod* _nmethod;

  public:
    explicit HashTableLookUp(nmethod* nmethod) : _nmethod(nmethod) { }
    uintx get_hash() const;
    bool equals(nmethod** value);
    bool is_dead(nmethod** value) const { return false; }
  };

  class HashTableIgnore : public StackObj {
  public:
    HashTableIgnore() { }
    void operator()(nmethod** value) { /* do nothing */ }
  };

public:
  G1CodeRootSetHashTable() :
    _table(Mutex::service-1,
           nullptr,
           Log2DefaultNumBuckets,
           false /* enable_statistics */),
    _table_scanner(&_table, BucketClaimSize), _num_entries(0) {
    clear();
  }

  // Robert Jenkins 1996 & Thomas Wang 1997
  // http://web.archive.org/web/20071223173210/http://www.concentric.net/~Ttwang/tech/inthash.htm
  static uint32_t hash(uint32_t key) {
    key = ~key + (key << 15);
    key = key ^ (key >> 12);
    key = key + (key << 2);
    key = key ^ (key >> 4);
    key = key * 2057;
    key = key ^ (key >> 16);
    return key;
  }

  static uintx get_hash(nmethod* nmethod) {
    uintptr_t value = (uintptr_t)nmethod;
    // The CHT only uses the bits smaller than HashTable::DEFAULT_MAX_SIZE_LOG2, so
    // try to increase the randomness by incorporating the upper bits of the
    // address too.
    STATIC_ASSERT(HashTable::DEFAULT_MAX_SIZE_LOG2 <= sizeof(uint32_t) * BitsPerByte);
#ifdef _LP64
    return hash((uint32_t)value ^ (uint32_t(value >> 32)));
#else
    return hash((uint32_t)value);
#endif
  }

  void insert(nmethod* method) {
    HashTableLookUp lookup(method);
    bool grow_hint = false;
    bool inserted = _table.insert(Thread::current(), lookup, method, &grow_hint);
    if (inserted) {
      Atomic::inc(&_num_entries);
    }
    if (grow_hint) {
      _table.grow(Thread::current());
    }
  }

  bool remove(nmethod* method) {
    HashTableLookUp lookup(method);
    bool removed = _table.remove(Thread::current(), lookup);
    if (removed) {
      Atomic::dec(&_num_entries);
    }
    return removed;
  }

  bool contains(nmethod* method) {
    HashTableLookUp lookup(method);
    HashTableIgnore ignore;
    return _table.get(Thread::current(), lookup, ignore);
  }

  void clear() {
    // Remove all entries.
    auto always_true = [] (nmethod** value) {
                         return true;
                       };
    clean(always_true);
  }

  void iterate_at_safepoint(CodeBlobClosure* blk) {
    assert_at_safepoint();
    // A lot of code root sets are typically empty.
    if (is_empty()) {
      return;
    }

    auto do_value =
      [&] (nmethod** value) {
        blk->do_code_blob(*value);
        return true;
      };
    _table_scanner.do_safepoint_scan(do_value);
  }

  // Removes entries as indicated by the given EVAL closure.
  template <class EVAL>
  void clean(EVAL& eval) {
    // A lot of code root sets are typically empty.
    if (is_empty()) {
      return;
    }

    size_t num_deleted = 0;
    auto do_delete =
      [&] (nmethod** value) {
        num_deleted++;
      };
    bool succeeded = _table.try_bulk_delete(Thread::current(), eval, do_delete);
    guarantee(succeeded, "unable to clean table");

    if (num_deleted != 0) {
      size_t current_size = Atomic::sub(&_num_entries, num_deleted);
      shrink_to_match(current_size);
    }
  }

  // Removes dead/unlinked entries.
  void bulk_remove() {
    auto delete_check = [&] (nmethod** value) {
      return (*value)->is_unlinked();
    };

    clean(delete_check);
  }

  // Calculate the log2 of the table size we want to shrink to.
  size_t log2_target_shrink_size(size_t current_size) const {
    // A table with the new size should be at most filled by this factor. Otherwise
    // we would grow again quickly.
    const float WantedLoadFactor = 0.5;
    size_t min_expected_size = checked_cast<size_t>(ceil(current_size / WantedLoadFactor));

    size_t result = Log2DefaultNumBuckets;
    if (min_expected_size != 0) {
      size_t log2_bound = checked_cast<size_t>(log2i_exact(round_up_power_of_2(min_expected_size)));
      result = clamp(log2_bound, Log2DefaultNumBuckets, HashTable::DEFAULT_MAX_SIZE_LOG2);
    }
    return result;
  }

  // Shrink to keep table size appropriate to the given number of entries.
  void shrink_to_match(size_t current_size) {
    size_t prev_log2size = _table.get_size_log2(Thread::current());
    size_t new_log2_table_size = log2_target_shrink_size(current_size);
    if (new_log2_table_size < prev_log2size) {
      _table.shrink(Thread::current(), new_log2_table_size);
    }
  }

  void reset_table_scanner() {
    _table_scanner.set(&_table, BucketClaimSize);
  }

  size_t mem_size() { return sizeof(*this) + _table.get_mem_size(Thread::current()); }

  size_t number_of_entries() const { return Atomic::load(&_num_entries); }
};

uintx G1CodeRootSetHashTable::HashTableLookUp::get_hash() const {
  return G1CodeRootSetHashTable::get_hash(_nmethod);
}

bool G1CodeRootSetHashTable::HashTableLookUp::equals(nmethod** value) {
  return *value == _nmethod;
}

uintx G1CodeRootSetHashTableConfig::get_hash(Value const& value, bool* is_dead) {
  *is_dead = false;
  return G1CodeRootSetHashTable::get_hash(value);
}

size_t G1CodeRootSet::length() const { return _table->number_of_entries(); }

void G1CodeRootSet::add(nmethod* method) {
  if (!contains(method)) {
    assert(!_is_iterating, "must be");
    _table->insert(method);
  }
}

G1CodeRootSet::G1CodeRootSet() :
  _table(new G1CodeRootSetHashTable())
  DEBUG_ONLY(COMMA _is_iterating(false)) { }

G1CodeRootSet::~G1CodeRootSet() {
  delete _table;
}

bool G1CodeRootSet::remove(nmethod* method) {
  assert(!_is_iterating, "should not mutate while iterating the table");
  return _table->remove(method);
}

void G1CodeRootSet::bulk_remove() {
  assert(!_is_iterating, "should not mutate while iterating the table");
  _table->bulk_remove();
}

bool G1CodeRootSet::contains(nmethod* method) {
  return _table->contains(method);
}

void G1CodeRootSet::clear() {
  assert(!_is_iterating, "should not mutate while iterating the table");
  _table->clear();
}

size_t G1CodeRootSet::mem_size() {
  return sizeof(*this) + _table->mem_size();
}

void G1CodeRootSet::reset_table_scanner() {
  _table->reset_table_scanner();
}

void G1CodeRootSet::nmethods_do(CodeBlobClosure* blk) const {
  DEBUG_ONLY(_is_iterating = true;)
  _table->iterate_at_safepoint(blk);
  DEBUG_ONLY(_is_iterating = false;)
}

class CleanCallback : public StackObj {
  NONCOPYABLE(CleanCallback); // can not copy, _blobs will point to old copy

  class PointsIntoHRDetectionClosure : public OopClosure {
    HeapRegion* _hr;

    template <typename T>
    void do_oop_work(T* p) {
      if (_hr->is_in(RawAccess<>::oop_load(p))) {
        _points_into = true;
      }
    }

   public:
    bool _points_into;
    PointsIntoHRDetectionClosure(HeapRegion* hr) : _hr(hr), _points_into(false) {}

    void do_oop(narrowOop* o) { do_oop_work(o); }

    void do_oop(oop* o) { do_oop_work(o); }
  };

  PointsIntoHRDetectionClosure _detector;
  CodeBlobToOopClosure _blobs;

 public:
  CleanCallback(HeapRegion* hr) : _detector(hr), _blobs(&_detector, !CodeBlobToOopClosure::FixRelocations) {}

  bool operator()(nmethod** value) {
    _detector._points_into = false;
    _blobs.do_code_blob(*value);
    return !_detector._points_into;
  }
};

void G1CodeRootSet::clean(HeapRegion* owner) {
  assert(!_is_iterating, "should not mutate while iterating the table");

  CleanCallback eval(owner);
  _table->clean(eval);
}
