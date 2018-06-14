/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPTABLE_HPP
#define SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPTABLE_HPP

#include "gc/shared/stringdedup/stringDedupStat.hpp"
#include "runtime/mutexLocker.hpp"

class StringDedupEntryCache;
class StringDedupUnlinkOrOopsDoClosure;

//
// Table entry in the deduplication hashtable. Points weakly to the
// character array. Can be chained in a linked list in case of hash
// collisions or when placed in a freelist in the entry cache.
//
class StringDedupEntry : public CHeapObj<mtGC> {
private:
  StringDedupEntry* _next;
  unsigned int      _hash;
  bool              _latin1;
  typeArrayOop      _obj;

public:
  StringDedupEntry() :
    _next(NULL),
    _hash(0),
    _latin1(false),
    _obj(NULL) {
  }

  StringDedupEntry* next() {
    return _next;
  }

  StringDedupEntry** next_addr() {
    return &_next;
  }

  void set_next(StringDedupEntry* next) {
    _next = next;
  }

  unsigned int hash() {
    return _hash;
  }

  void set_hash(unsigned int hash) {
    _hash = hash;
  }

  bool latin1() {
    return _latin1;
  }

  void set_latin1(bool latin1) {
    _latin1 = latin1;
  }

  typeArrayOop obj() {
    return _obj;
  }

  typeArrayOop* obj_addr() {
    return &_obj;
  }

  void set_obj(typeArrayOop obj) {
    _obj = obj;
  }
};

//
// The deduplication hashtable keeps track of all unique character arrays used
// by String objects. Each table entry weakly points to an character array, allowing
// otherwise unreachable character arrays to be declared dead and pruned from the
// table.
//
// The table is dynamically resized to accommodate the current number of table entries.
// The table has hash buckets with chains for hash collision. If the average chain
// length goes above or below given thresholds the table grows or shrinks accordingly.
//
// The table is also dynamically rehashed (using a new hash seed) if it becomes severely
// unbalanced, i.e., a hash chain is significantly longer than average.
//
// All access to the table is protected by the StringDedupTable_lock, except under
// safepoints in which case GC workers are allowed to access a table partitions they
// have claimed without first acquiring the lock. Note however, that this applies only
// the table partition (i.e. a range of elements in _buckets), not other parts of the
// table such as the _entries field, statistics counters, etc.
//
class StringDedupTable : public CHeapObj<mtGC> {
private:
  // The currently active hashtable instance. Only modified when
  // the table is resizes or rehashed.
  static StringDedupTable*        _table;

  // Cache for reuse and fast alloc/free of table entries.
  static StringDedupEntryCache*   _entry_cache;

  StringDedupEntry**              _buckets;
  size_t                          _size;
  uintx                           _entries;
  uintx                           _shrink_threshold;
  uintx                           _grow_threshold;
  bool                            _rehash_needed;

  // The hash seed also dictates which hash function to use. A
  // zero hash seed means we will use the Java compatible hash
  // function (which doesn't use a seed), and a non-zero hash
  // seed means we use the murmur3 hash function.
  jint                            _hash_seed;

  // Constants governing table resize/rehash/cache.
  static const size_t             _min_size;
  static const size_t             _max_size;
  static const double             _grow_load_factor;
  static const double             _shrink_load_factor;
  static const uintx              _rehash_multiple;
  static const uintx              _rehash_threshold;
  static const double             _max_cache_factor;

  // Table statistics, only used for logging.
  static uintx                    _entries_added;
  static uintx                    _entries_removed;
  static uintx                    _resize_count;
  static uintx                    _rehash_count;

  static volatile size_t          _claimed_index;

  static StringDedupTable*        _resized_table;
  static StringDedupTable*        _rehashed_table;

  StringDedupTable(size_t size, jint hash_seed = 0);
  ~StringDedupTable();

  // Returns the hash bucket at the given index.
  StringDedupEntry** bucket(size_t index) {
    return _buckets + index;
  }

  // Returns the hash bucket index for the given hash code.
  size_t hash_to_index(unsigned int hash) {
    return (size_t)hash & (_size - 1);
  }

  // Adds a new table entry to the given hash bucket.
  void add(typeArrayOop value, bool latin1, unsigned int hash, StringDedupEntry** list);

  // Removes the given table entry from the table.
  void remove(StringDedupEntry** pentry, uint worker_id);

  // Transfers a table entry from the current table to the destination table.
  void transfer(StringDedupEntry** pentry, StringDedupTable* dest);

  // Returns an existing character array in the given hash bucket, or NULL
  // if no matching character array exists.
  typeArrayOop lookup(typeArrayOop value, bool latin1, unsigned int hash,
                      StringDedupEntry** list, uintx &count);

  // Returns an existing character array in the table, or inserts a new
  // table entry if no matching character array exists.
  typeArrayOop lookup_or_add_inner(typeArrayOop value, bool latin1, unsigned int hash);

  // Thread safe lookup or add of table entry
  static typeArrayOop lookup_or_add(typeArrayOop value, bool latin1, unsigned int hash) {
    // Protect the table from concurrent access. Also note that this lock
    // acts as a fence for _table, which could have been replaced by a new
    // instance if the table was resized or rehashed.
    MutexLockerEx ml(StringDedupTable_lock, Mutex::_no_safepoint_check_flag);
    return _table->lookup_or_add_inner(value, latin1, hash);
  }

  // Returns true if the hashtable is currently using a Java compatible
  // hash function.
  static bool use_java_hash() {
    return _table->_hash_seed == 0;
  }

  static bool equals(typeArrayOop value1, typeArrayOop value2);

  // Computes the hash code for the given character array, using the
  // currently active hash function and hash seed.
  static unsigned int hash_code(typeArrayOop value, bool latin1);

  static uintx unlink_or_oops_do(StringDedupUnlinkOrOopsDoClosure* cl,
                                 size_t partition_begin,
                                 size_t partition_end,
                                 uint worker_id);

  static size_t claim_table_partition(size_t partition_size);

  static bool is_resizing();
  static bool is_rehashing();

  // If a table resize is needed, returns a newly allocated empty
  // hashtable of the proper size.
  static StringDedupTable* prepare_resize();

  // Installs a newly resized table as the currently active table
  // and deletes the previously active table.
  static void finish_resize(StringDedupTable* resized_table);

  // If a table rehash is needed, returns a newly allocated empty
  // hashtable and updates the hash seed.
  static StringDedupTable* prepare_rehash();

  // Transfers rehashed entries from the currently active table into
  // the new table. Installs the new table as the currently active table
  // and deletes the previously active table.
  static void finish_rehash(StringDedupTable* rehashed_table);

public:
  static void create();

  // Deduplicates the given String object, or adds its backing
  // character array to the deduplication hashtable.
  static void deduplicate(oop java_string, StringDedupStat* stat);

  static void unlink_or_oops_do(StringDedupUnlinkOrOopsDoClosure* cl, uint worker_id);

  static void print_statistics();
  static void verify();

  // If the table entry cache has grown too large, delete overflowed entries.
  static void clean_entry_cache();

  // GC support
  static void gc_prologue(bool resize_and_rehash_table);
  static void gc_epilogue();
};

#endif // SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPTABLE_HPP
