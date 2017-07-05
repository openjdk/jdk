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
#include "classfile/altHashing.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/g1StringDedupTable.hpp"
#include "gc/shared/gcLocker.hpp"
#include "logging/log.hpp"
#include "memory/padded.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/mutexLocker.hpp"

//
// Freelist in the deduplication table entry cache. Links table
// entries together using their _next fields.
//
class G1StringDedupEntryFreeList : public CHeapObj<mtGC> {
private:
  G1StringDedupEntry* _list;
  size_t              _length;

public:
  G1StringDedupEntryFreeList() :
    _list(NULL),
    _length(0) {
  }

  void add(G1StringDedupEntry* entry) {
    entry->set_next(_list);
    _list = entry;
    _length++;
  }

  G1StringDedupEntry* remove() {
    G1StringDedupEntry* entry = _list;
    if (entry != NULL) {
      _list = entry->next();
      _length--;
    }
    return entry;
  }

  size_t length() {
    return _length;
  }
};

//
// Cache of deduplication table entries. This cache provides fast allocation and
// reuse of table entries to lower the pressure on the underlying allocator.
// But more importantly, it provides fast/deferred freeing of table entries. This
// is important because freeing of table entries is done during stop-the-world
// phases and it is not uncommon for large number of entries to be freed at once.
// Tables entries that are freed during these phases are placed onto a freelist in
// the cache. The deduplication thread, which executes in a concurrent phase, will
// later reuse or free the underlying memory for these entries.
//
// The cache allows for single-threaded allocations and multi-threaded frees.
// Allocations are synchronized by StringDedupTable_lock as part of a table
// modification.
//
class G1StringDedupEntryCache : public CHeapObj<mtGC> {
private:
  // One freelist per GC worker to allow lock less freeing of
  // entries while doing a parallel scan of the table. Using
  // PaddedEnd to avoid false sharing.
  PaddedEnd<G1StringDedupEntryFreeList>* _lists;
  size_t                                 _nlists;

public:
  G1StringDedupEntryCache();
  ~G1StringDedupEntryCache();

  // Get a table entry from the cache freelist, or allocate a new
  // entry if the cache is empty.
  G1StringDedupEntry* alloc();

  // Insert a table entry into the cache freelist.
  void free(G1StringDedupEntry* entry, uint worker_id);

  // Returns current number of entries in the cache.
  size_t size();

  // If the cache has grown above the given max size, trim it down
  // and deallocate the memory occupied by trimmed of entries.
  void trim(size_t max_size);
};

G1StringDedupEntryCache::G1StringDedupEntryCache() {
  _nlists = ParallelGCThreads;
  _lists = PaddedArray<G1StringDedupEntryFreeList, mtGC>::create_unfreeable((uint)_nlists);
}

G1StringDedupEntryCache::~G1StringDedupEntryCache() {
  ShouldNotReachHere();
}

G1StringDedupEntry* G1StringDedupEntryCache::alloc() {
  for (size_t i = 0; i < _nlists; i++) {
    G1StringDedupEntry* entry = _lists[i].remove();
    if (entry != NULL) {
      return entry;
    }
  }
  return new G1StringDedupEntry();
}

void G1StringDedupEntryCache::free(G1StringDedupEntry* entry, uint worker_id) {
  assert(entry->obj() != NULL, "Double free");
  assert(worker_id < _nlists, "Invalid worker id");
  entry->set_obj(NULL);
  entry->set_hash(0);
  _lists[worker_id].add(entry);
}

size_t G1StringDedupEntryCache::size() {
  size_t size = 0;
  for (size_t i = 0; i < _nlists; i++) {
    size += _lists[i].length();
  }
  return size;
}

void G1StringDedupEntryCache::trim(size_t max_size) {
  size_t cache_size = 0;
  for (size_t i = 0; i < _nlists; i++) {
    G1StringDedupEntryFreeList* list = &_lists[i];
    cache_size += list->length();
    while (cache_size > max_size) {
      G1StringDedupEntry* entry = list->remove();
      assert(entry != NULL, "Should not be null");
      cache_size--;
      delete entry;
    }
  }
}

G1StringDedupTable*      G1StringDedupTable::_table = NULL;
G1StringDedupEntryCache* G1StringDedupTable::_entry_cache = NULL;

const size_t             G1StringDedupTable::_min_size = (1 << 10);   // 1024
const size_t             G1StringDedupTable::_max_size = (1 << 24);   // 16777216
const double             G1StringDedupTable::_grow_load_factor = 2.0; // Grow table at 200% load
const double             G1StringDedupTable::_shrink_load_factor = _grow_load_factor / 3.0; // Shrink table at 67% load
const double             G1StringDedupTable::_max_cache_factor = 0.1; // Cache a maximum of 10% of the table size
const uintx              G1StringDedupTable::_rehash_multiple = 60;   // Hash bucket has 60 times more collisions than expected
const uintx              G1StringDedupTable::_rehash_threshold = (uintx)(_rehash_multiple * _grow_load_factor);

uintx                    G1StringDedupTable::_entries_added = 0;
uintx                    G1StringDedupTable::_entries_removed = 0;
uintx                    G1StringDedupTable::_resize_count = 0;
uintx                    G1StringDedupTable::_rehash_count = 0;

G1StringDedupTable::G1StringDedupTable(size_t size, jint hash_seed) :
  _size(size),
  _entries(0),
  _grow_threshold((uintx)(size * _grow_load_factor)),
  _shrink_threshold((uintx)(size * _shrink_load_factor)),
  _rehash_needed(false),
  _hash_seed(hash_seed) {
  assert(is_power_of_2(size), "Table size must be a power of 2");
  _buckets = NEW_C_HEAP_ARRAY(G1StringDedupEntry*, _size, mtGC);
  memset(_buckets, 0, _size * sizeof(G1StringDedupEntry*));
}

G1StringDedupTable::~G1StringDedupTable() {
  FREE_C_HEAP_ARRAY(G1StringDedupEntry*, _buckets);
}

void G1StringDedupTable::create() {
  assert(_table == NULL, "One string deduplication table allowed");
  _entry_cache = new G1StringDedupEntryCache();
  _table = new G1StringDedupTable(_min_size);
}

void G1StringDedupTable::add(typeArrayOop value, bool latin1, unsigned int hash, G1StringDedupEntry** list) {
  G1StringDedupEntry* entry = _entry_cache->alloc();
  entry->set_obj(value);
  entry->set_hash(hash);
  entry->set_latin1(latin1);
  entry->set_next(*list);
  *list = entry;
  _entries++;
}

void G1StringDedupTable::remove(G1StringDedupEntry** pentry, uint worker_id) {
  G1StringDedupEntry* entry = *pentry;
  *pentry = entry->next();
  _entry_cache->free(entry, worker_id);
}

void G1StringDedupTable::transfer(G1StringDedupEntry** pentry, G1StringDedupTable* dest) {
  G1StringDedupEntry* entry = *pentry;
  *pentry = entry->next();
  unsigned int hash = entry->hash();
  size_t index = dest->hash_to_index(hash);
  G1StringDedupEntry** list = dest->bucket(index);
  entry->set_next(*list);
  *list = entry;
}

bool G1StringDedupTable::equals(typeArrayOop value1, typeArrayOop value2) {
  return (value1 == value2 ||
          (value1->length() == value2->length() &&
           (!memcmp(value1->base(T_BYTE),
                    value2->base(T_BYTE),
                    value1->length() * sizeof(jbyte)))));
}

typeArrayOop G1StringDedupTable::lookup(typeArrayOop value, bool latin1, unsigned int hash,
                                        G1StringDedupEntry** list, uintx &count) {
  for (G1StringDedupEntry* entry = *list; entry != NULL; entry = entry->next()) {
    if (entry->hash() == hash && entry->latin1() == latin1) {
      typeArrayOop existing_value = entry->obj();
      if (equals(value, existing_value)) {
        // Match found
        return existing_value;
      }
    }
    count++;
  }

  // Not found
  return NULL;
}

typeArrayOop G1StringDedupTable::lookup_or_add_inner(typeArrayOop value, bool latin1, unsigned int hash) {
  size_t index = hash_to_index(hash);
  G1StringDedupEntry** list = bucket(index);
  uintx count = 0;

  // Lookup in list
  typeArrayOop existing_value = lookup(value, latin1, hash, list, count);

  // Check if rehash is needed
  if (count > _rehash_threshold) {
    _rehash_needed = true;
  }

  if (existing_value == NULL) {
    // Not found, add new entry
    add(value, latin1, hash, list);

    // Update statistics
    _entries_added++;
  }

  return existing_value;
}

unsigned int G1StringDedupTable::hash_code(typeArrayOop value, bool latin1) {
  unsigned int hash;
  int length = value->length();
  if (latin1) {
    const jbyte* data = (jbyte*)value->base(T_BYTE);
    if (use_java_hash()) {
      hash = java_lang_String::hash_code(data, length);
    } else {
      hash = AltHashing::murmur3_32(_table->_hash_seed, data, length);
    }
  } else {
    length /= sizeof(jchar) / sizeof(jbyte); // Convert number of bytes to number of chars
    const jchar* data = (jchar*)value->base(T_CHAR);
    if (use_java_hash()) {
      hash = java_lang_String::hash_code(data, length);
    } else {
      hash = AltHashing::murmur3_32(_table->_hash_seed, data, length);
    }
  }

  return hash;
}

void G1StringDedupTable::deduplicate(oop java_string, G1StringDedupStat& stat) {
  assert(java_lang_String::is_instance(java_string), "Must be a string");
  No_Safepoint_Verifier nsv;

  stat.inc_inspected();

  typeArrayOop value = java_lang_String::value(java_string);
  if (value == NULL) {
    // String has no value
    stat.inc_skipped();
    return;
  }

  bool latin1 = java_lang_String::is_latin1(java_string);
  unsigned int hash = 0;

  if (use_java_hash()) {
    // Get hash code from cache
    hash = java_lang_String::hash(java_string);
  }

  if (hash == 0) {
    // Compute hash
    hash = hash_code(value, latin1);
    stat.inc_hashed();

    if (use_java_hash() && hash != 0) {
      // Store hash code in cache
      java_lang_String::set_hash(java_string, hash);
    }
  }

  typeArrayOop existing_value = lookup_or_add(value, latin1, hash);
  if (existing_value == value) {
    // Same value, already known
    stat.inc_known();
    return;
  }

  // Get size of value array
  uintx size_in_bytes = value->size() * HeapWordSize;
  stat.inc_new(size_in_bytes);

  if (existing_value != NULL) {
    // Enqueue the reference to make sure it is kept alive. Concurrent mark might
    // otherwise declare it dead if there are no other strong references to this object.
    G1SATBCardTableModRefBS::enqueue(existing_value);

    // Existing value found, deduplicate string
    java_lang_String::set_value(java_string, existing_value);

    if (G1CollectedHeap::heap()->is_in_young(value)) {
      stat.inc_deduped_young(size_in_bytes);
    } else {
      stat.inc_deduped_old(size_in_bytes);
    }
  }
}

G1StringDedupTable* G1StringDedupTable::prepare_resize() {
  size_t size = _table->_size;

  // Check if the hashtable needs to be resized
  if (_table->_entries > _table->_grow_threshold) {
    // Grow table, double the size
    size *= 2;
    if (size > _max_size) {
      // Too big, don't resize
      return NULL;
    }
  } else if (_table->_entries < _table->_shrink_threshold) {
    // Shrink table, half the size
    size /= 2;
    if (size < _min_size) {
      // Too small, don't resize
      return NULL;
    }
  } else if (StringDeduplicationResizeALot) {
    // Force grow
    size *= 2;
    if (size > _max_size) {
      // Too big, force shrink instead
      size /= 4;
    }
  } else {
    // Resize not needed
    return NULL;
  }

  // Update statistics
  _resize_count++;

  // Allocate the new table. The new table will be populated by workers
  // calling unlink_or_oops_do() and finally installed by finish_resize().
  return new G1StringDedupTable(size, _table->_hash_seed);
}

void G1StringDedupTable::finish_resize(G1StringDedupTable* resized_table) {
  assert(resized_table != NULL, "Invalid table");

  resized_table->_entries = _table->_entries;

  // Free old table
  delete _table;

  // Install new table
  _table = resized_table;
}

void G1StringDedupTable::unlink_or_oops_do(G1StringDedupUnlinkOrOopsDoClosure* cl, uint worker_id) {
  // The table is divided into partitions to allow lock-less parallel processing by
  // multiple worker threads. A worker thread first claims a partition, which ensures
  // exclusive access to that part of the table, then continues to process it. To allow
  // shrinking of the table in parallel we also need to make sure that the same worker
  // thread processes all partitions where entries will hash to the same destination
  // partition. Since the table size is always a power of two and we always shrink by
  // dividing the table in half, we know that for a given partition there is only one
  // other partition whoes entries will hash to the same destination partition. That
  // other partition is always the sibling partition in the second half of the table.
  // For example, if the table is divided into 8 partitions, the sibling of partition 0
  // is partition 4, the sibling of partition 1 is partition 5, etc.
  size_t table_half = _table->_size / 2;

  // Let each partition be one page worth of buckets
  size_t partition_size = MIN2(table_half, os::vm_page_size() / sizeof(G1StringDedupEntry*));
  assert(table_half % partition_size == 0, "Invalid partition size");

  // Number of entries removed during the scan
  uintx removed = 0;

  for (;;) {
    // Grab next partition to scan
    size_t partition_begin = cl->claim_table_partition(partition_size);
    size_t partition_end = partition_begin + partition_size;
    if (partition_begin >= table_half) {
      // End of table
      break;
    }

    // Scan the partition followed by the sibling partition in the second half of the table
    removed += unlink_or_oops_do(cl, partition_begin, partition_end, worker_id);
    removed += unlink_or_oops_do(cl, table_half + partition_begin, table_half + partition_end, worker_id);
  }

  // Delayed update avoid contention on the table lock
  if (removed > 0) {
    MutexLockerEx ml(StringDedupTable_lock, Mutex::_no_safepoint_check_flag);
    _table->_entries -= removed;
    _entries_removed += removed;
  }
}

uintx G1StringDedupTable::unlink_or_oops_do(G1StringDedupUnlinkOrOopsDoClosure* cl,
                                            size_t partition_begin,
                                            size_t partition_end,
                                            uint worker_id) {
  uintx removed = 0;
  for (size_t bucket = partition_begin; bucket < partition_end; bucket++) {
    G1StringDedupEntry** entry = _table->bucket(bucket);
    while (*entry != NULL) {
      oop* p = (oop*)(*entry)->obj_addr();
      if (cl->is_alive(*p)) {
        cl->keep_alive(p);
        if (cl->is_resizing()) {
          // We are resizing the table, transfer entry to the new table
          _table->transfer(entry, cl->resized_table());
        } else {
          if (cl->is_rehashing()) {
            // We are rehashing the table, rehash the entry but keep it
            // in the table. We can't transfer entries into the new table
            // at this point since we don't have exclusive access to all
            // destination partitions. finish_rehash() will do a single
            // threaded transfer of all entries.
            typeArrayOop value = (typeArrayOop)*p;
            bool latin1 = (*entry)->latin1();
            unsigned int hash = hash_code(value, latin1);
            (*entry)->set_hash(hash);
          }

          // Move to next entry
          entry = (*entry)->next_addr();
        }
      } else {
        // Not alive, remove entry from table
        _table->remove(entry, worker_id);
        removed++;
      }
    }
  }

  return removed;
}

G1StringDedupTable* G1StringDedupTable::prepare_rehash() {
  if (!_table->_rehash_needed && !StringDeduplicationRehashALot) {
    // Rehash not needed
    return NULL;
  }

  // Update statistics
  _rehash_count++;

  // Compute new hash seed
  _table->_hash_seed = AltHashing::compute_seed();

  // Allocate the new table, same size and hash seed
  return new G1StringDedupTable(_table->_size, _table->_hash_seed);
}

void G1StringDedupTable::finish_rehash(G1StringDedupTable* rehashed_table) {
  assert(rehashed_table != NULL, "Invalid table");

  // Move all newly rehashed entries into the correct buckets in the new table
  for (size_t bucket = 0; bucket < _table->_size; bucket++) {
    G1StringDedupEntry** entry = _table->bucket(bucket);
    while (*entry != NULL) {
      _table->transfer(entry, rehashed_table);
    }
  }

  rehashed_table->_entries = _table->_entries;

  // Free old table
  delete _table;

  // Install new table
  _table = rehashed_table;
}

void G1StringDedupTable::verify() {
  for (size_t bucket = 0; bucket < _table->_size; bucket++) {
    // Verify entries
    G1StringDedupEntry** entry = _table->bucket(bucket);
    while (*entry != NULL) {
      typeArrayOop value = (*entry)->obj();
      guarantee(value != NULL, "Object must not be NULL");
      guarantee(G1CollectedHeap::heap()->is_in_reserved(value), "Object must be on the heap");
      guarantee(!value->is_forwarded(), "Object must not be forwarded");
      guarantee(value->is_typeArray(), "Object must be a typeArrayOop");
      bool latin1 = (*entry)->latin1();
      unsigned int hash = hash_code(value, latin1);
      guarantee((*entry)->hash() == hash, "Table entry has inorrect hash");
      guarantee(_table->hash_to_index(hash) == bucket, "Table entry has incorrect index");
      entry = (*entry)->next_addr();
    }

    // Verify that we do not have entries with identical oops or identical arrays.
    // We only need to compare entries in the same bucket. If the same oop or an
    // identical array has been inserted more than once into different/incorrect
    // buckets the verification step above will catch that.
    G1StringDedupEntry** entry1 = _table->bucket(bucket);
    while (*entry1 != NULL) {
      typeArrayOop value1 = (*entry1)->obj();
      bool latin1_1 = (*entry1)->latin1();
      G1StringDedupEntry** entry2 = (*entry1)->next_addr();
      while (*entry2 != NULL) {
        typeArrayOop value2 = (*entry2)->obj();
        bool latin1_2 = (*entry2)->latin1();
        guarantee(latin1_1 != latin1_2 || !equals(value1, value2), "Table entries must not have identical arrays");
        entry2 = (*entry2)->next_addr();
      }
      entry1 = (*entry1)->next_addr();
    }
  }
}

void G1StringDedupTable::trim_entry_cache() {
  MutexLockerEx ml(StringDedupTable_lock, Mutex::_no_safepoint_check_flag);
  size_t max_cache_size = (size_t)(_table->_size * _max_cache_factor);
  _entry_cache->trim(max_cache_size);
}

void G1StringDedupTable::print_statistics() {
  LogHandle(gc, stringdedup) log;
  log.debug("   [Table]");
  log.debug("      [Memory Usage: " G1_STRDEDUP_BYTES_FORMAT_NS "]",
            G1_STRDEDUP_BYTES_PARAM(_table->_size * sizeof(G1StringDedupEntry*) + (_table->_entries + _entry_cache->size()) * sizeof(G1StringDedupEntry)));
  log.debug("      [Size: " SIZE_FORMAT ", Min: " SIZE_FORMAT ", Max: " SIZE_FORMAT "]", _table->_size, _min_size, _max_size);
  log.debug("      [Entries: " UINTX_FORMAT ", Load: " G1_STRDEDUP_PERCENT_FORMAT_NS ", Cached: " UINTX_FORMAT ", Added: " UINTX_FORMAT ", Removed: " UINTX_FORMAT "]",
            _table->_entries, (double)_table->_entries / (double)_table->_size * 100.0, _entry_cache->size(), _entries_added, _entries_removed);
  log.debug("      [Resize Count: " UINTX_FORMAT ", Shrink Threshold: " UINTX_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT_NS "), Grow Threshold: " UINTX_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT_NS ")]",
            _resize_count, _table->_shrink_threshold, _shrink_load_factor * 100.0, _table->_grow_threshold, _grow_load_factor * 100.0);
  log.debug("      [Rehash Count: " UINTX_FORMAT ", Rehash Threshold: " UINTX_FORMAT ", Hash Seed: 0x%x]", _rehash_count, _rehash_threshold, _table->_hash_seed);
  log.debug("      [Age Threshold: " UINTX_FORMAT "]", StringDeduplicationAgeThreshold);
}
