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

#include "precompiled.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/stringdedup/stringDedupTable.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "memory/padded.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/arrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepointVerifiers.hpp"

//
// List of deduplication table entries. Links table
// entries together using their _next fields.
//
class StringDedupEntryList : public CHeapObj<mtGC> {
private:
  StringDedupEntry*   _list;
  size_t              _length;

public:
  StringDedupEntryList() :
    _list(NULL),
    _length(0) {
  }

  void add(StringDedupEntry* entry) {
    entry->set_next(_list);
    _list = entry;
    _length++;
  }

  StringDedupEntry* remove() {
    StringDedupEntry* entry = _list;
    if (entry != NULL) {
      _list = entry->next();
      _length--;
    }
    return entry;
  }

  StringDedupEntry* remove_all() {
    StringDedupEntry* list = _list;
    _list = NULL;
    return list;
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
class StringDedupEntryCache : public CHeapObj<mtGC> {
private:
  // One cache/overflow list per GC worker to allow lock less freeing of
  // entries while doing a parallel scan of the table. Using PaddedEnd to
  // avoid false sharing.
  size_t                             _nlists;
  size_t                             _max_list_length;
  PaddedEnd<StringDedupEntryList>*   _cached;
  PaddedEnd<StringDedupEntryList>*   _overflowed;

public:
  StringDedupEntryCache(size_t max_size);
  ~StringDedupEntryCache();

  // Set max number of table entries to cache.
  void set_max_size(size_t max_size);

  // Get a table entry from the cache, or allocate a new entry if the cache is empty.
  StringDedupEntry* alloc();

  // Insert a table entry into the cache.
  void free(StringDedupEntry* entry, uint worker_id);

  // Returns current number of entries in the cache.
  size_t size();

  // Deletes overflowed entries.
  void delete_overflowed();
};

StringDedupEntryCache::StringDedupEntryCache(size_t max_size) :
  _nlists(ParallelGCThreads),
  _max_list_length(0),
  _cached(PaddedArray<StringDedupEntryList, mtGC>::create_unfreeable((uint)_nlists)),
  _overflowed(PaddedArray<StringDedupEntryList, mtGC>::create_unfreeable((uint)_nlists)) {
  set_max_size(max_size);
}

StringDedupEntryCache::~StringDedupEntryCache() {
  ShouldNotReachHere();
}

void StringDedupEntryCache::set_max_size(size_t size) {
  _max_list_length = size / _nlists;
}

StringDedupEntry* StringDedupEntryCache::alloc() {
  for (size_t i = 0; i < _nlists; i++) {
    StringDedupEntry* entry = _cached[i].remove();
    if (entry != NULL) {
      return entry;
    }
  }
  return new StringDedupEntry();
}

void StringDedupEntryCache::free(StringDedupEntry* entry, uint worker_id) {
  assert(entry->obj() != NULL, "Double free");
  assert(worker_id < _nlists, "Invalid worker id");

  entry->set_obj(NULL);
  entry->set_hash(0);

  if (_cached[worker_id].length() < _max_list_length) {
    // Cache is not full
    _cached[worker_id].add(entry);
  } else {
    // Cache is full, add to overflow list for later deletion
    _overflowed[worker_id].add(entry);
  }
}

size_t StringDedupEntryCache::size() {
  size_t size = 0;
  for (size_t i = 0; i < _nlists; i++) {
    size += _cached[i].length();
  }
  return size;
}

void StringDedupEntryCache::delete_overflowed() {
  double start = os::elapsedTime();
  uintx count = 0;

  for (size_t i = 0; i < _nlists; i++) {
    StringDedupEntry* entry;

    {
      // The overflow list can be modified during safepoints, therefore
      // we temporarily join the suspendible thread set while removing
      // all entries from the list.
      SuspendibleThreadSetJoiner sts_join;
      entry = _overflowed[i].remove_all();
    }

    // Delete all entries
    while (entry != NULL) {
      StringDedupEntry* next = entry->next();
      delete entry;
      entry = next;
      count++;
    }
  }

  double end = os::elapsedTime();
  log_trace(gc, stringdedup)("Deleted " UINTX_FORMAT " entries, " STRDEDUP_TIME_FORMAT_MS,
                             count, STRDEDUP_TIME_PARAM_MS(end - start));
}

StringDedupTable*        StringDedupTable::_table = NULL;
StringDedupEntryCache*   StringDedupTable::_entry_cache = NULL;

const size_t             StringDedupTable::_min_size = (1 << 10);   // 1024
const size_t             StringDedupTable::_max_size = (1 << 24);   // 16777216
const double             StringDedupTable::_grow_load_factor = 2.0; // Grow table at 200% load
const double             StringDedupTable::_shrink_load_factor = _grow_load_factor / 3.0; // Shrink table at 67% load
const double             StringDedupTable::_max_cache_factor = 0.1; // Cache a maximum of 10% of the table size
const uintx              StringDedupTable::_rehash_multiple = 60;   // Hash bucket has 60 times more collisions than expected
const uintx              StringDedupTable::_rehash_threshold = (uintx)(_rehash_multiple * _grow_load_factor);

uintx                    StringDedupTable::_entries_added = 0;
uintx                    StringDedupTable::_entries_removed = 0;
uintx                    StringDedupTable::_resize_count = 0;
uintx                    StringDedupTable::_rehash_count = 0;

StringDedupTable*        StringDedupTable::_resized_table = NULL;
StringDedupTable*        StringDedupTable::_rehashed_table = NULL;
volatile size_t          StringDedupTable::_claimed_index = 0;

StringDedupTable::StringDedupTable(size_t size, jint hash_seed) :
  _size(size),
  _entries(0),
  _shrink_threshold((uintx)(size * _shrink_load_factor)),
  _grow_threshold((uintx)(size * _grow_load_factor)),
  _rehash_needed(false),
  _hash_seed(hash_seed) {
  assert(is_power_of_2(size), "Table size must be a power of 2");
  _buckets = NEW_C_HEAP_ARRAY(StringDedupEntry*, _size, mtGC);
  memset(_buckets, 0, _size * sizeof(StringDedupEntry*));
}

StringDedupTable::~StringDedupTable() {
  FREE_C_HEAP_ARRAY(G1StringDedupEntry*, _buckets);
}

void StringDedupTable::create() {
  assert(_table == NULL, "One string deduplication table allowed");
  _entry_cache = new StringDedupEntryCache(_min_size * _max_cache_factor);
  _table = new StringDedupTable(_min_size);
}

void StringDedupTable::add(typeArrayOop value, bool latin1, unsigned int hash, StringDedupEntry** list) {
  StringDedupEntry* entry = _entry_cache->alloc();
  entry->set_obj(value);
  entry->set_hash(hash);
  entry->set_latin1(latin1);
  entry->set_next(*list);
  *list = entry;
  _entries++;
}

void StringDedupTable::remove(StringDedupEntry** pentry, uint worker_id) {
  StringDedupEntry* entry = *pentry;
  *pentry = entry->next();
  _entry_cache->free(entry, worker_id);
}

void StringDedupTable::transfer(StringDedupEntry** pentry, StringDedupTable* dest) {
  StringDedupEntry* entry = *pentry;
  *pentry = entry->next();
  unsigned int hash = entry->hash();
  size_t index = dest->hash_to_index(hash);
  StringDedupEntry** list = dest->bucket(index);
  entry->set_next(*list);
  *list = entry;
}

bool StringDedupTable::equals(typeArrayOop value1, typeArrayOop value2) {
  return (oopDesc::equals(value1, value2) ||
          (value1->length() == value2->length() &&
          (!memcmp(value1->base(T_BYTE),
                    value2->base(T_BYTE),
                    value1->length() * sizeof(jbyte)))));
}

typeArrayOop StringDedupTable::lookup(typeArrayOop value, bool latin1, unsigned int hash,
                                      StringDedupEntry** list, uintx &count) {
  for (StringDedupEntry* entry = *list; entry != NULL; entry = entry->next()) {
    if (entry->hash() == hash && entry->latin1() == latin1) {
      oop* obj_addr = (oop*)entry->obj_addr();
      oop obj = NativeAccess<ON_PHANTOM_OOP_REF | AS_NO_KEEPALIVE>::oop_load(obj_addr);
      if (equals(value, static_cast<typeArrayOop>(obj))) {
        obj = NativeAccess<ON_PHANTOM_OOP_REF>::oop_load(obj_addr);
        return static_cast<typeArrayOop>(obj);
      }
    }
    count++;
  }

  // Not found
  return NULL;
}

typeArrayOop StringDedupTable::lookup_or_add_inner(typeArrayOop value, bool latin1, unsigned int hash) {
  size_t index = hash_to_index(hash);
  StringDedupEntry** list = bucket(index);
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

unsigned int StringDedupTable::hash_code(typeArrayOop value, bool latin1) {
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

void StringDedupTable::deduplicate(oop java_string, StringDedupStat* stat) {
  assert(java_lang_String::is_instance(java_string), "Must be a string");
  NoSafepointVerifier nsv;

  stat->inc_inspected();

  typeArrayOop value = java_lang_String::value(java_string);
  if (value == NULL) {
    // String has no value
    stat->inc_skipped();
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
    stat->inc_hashed();

    if (use_java_hash() && hash != 0) {
      // Store hash code in cache
      java_lang_String::set_hash(java_string, hash);
    }
  }

  typeArrayOop existing_value = lookup_or_add(value, latin1, hash);
  if (oopDesc::equals_raw(existing_value, value)) {
    // Same value, already known
    stat->inc_known();
    return;
  }

  // Get size of value array
  uintx size_in_bytes = value->size() * HeapWordSize;
  stat->inc_new(size_in_bytes);

  if (existing_value != NULL) {
    // Existing value found, deduplicate string
    java_lang_String::set_value(java_string, existing_value);
    stat->deduped(value, size_in_bytes);
  }
}

bool StringDedupTable::is_resizing() {
  return _resized_table != NULL;
}

bool StringDedupTable::is_rehashing() {
  return _rehashed_table != NULL;
}

StringDedupTable* StringDedupTable::prepare_resize() {
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

  // Update max cache size
  _entry_cache->set_max_size(size * _max_cache_factor);

  // Allocate the new table. The new table will be populated by workers
  // calling unlink_or_oops_do() and finally installed by finish_resize().
  return new StringDedupTable(size, _table->_hash_seed);
}

void StringDedupTable::finish_resize(StringDedupTable* resized_table) {
  assert(resized_table != NULL, "Invalid table");

  resized_table->_entries = _table->_entries;

  // Free old table
  delete _table;

  // Install new table
  _table = resized_table;
}

void StringDedupTable::unlink_or_oops_do(StringDedupUnlinkOrOopsDoClosure* cl, uint worker_id) {
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
  size_t partition_size = MIN2(table_half, os::vm_page_size() / sizeof(StringDedupEntry*));
  assert(table_half % partition_size == 0, "Invalid partition size");

  // Number of entries removed during the scan
  uintx removed = 0;

  for (;;) {
    // Grab next partition to scan
    size_t partition_begin = claim_table_partition(partition_size);
    size_t partition_end = partition_begin + partition_size;
    if (partition_begin >= table_half) {
      // End of table
      break;
    }

    // Scan the partition followed by the sibling partition in the second half of the table
    removed += unlink_or_oops_do(cl, partition_begin, partition_end, worker_id);
    removed += unlink_or_oops_do(cl, table_half + partition_begin, table_half + partition_end, worker_id);
  }

  // Delayed update to avoid contention on the table lock
  if (removed > 0) {
    MutexLockerEx ml(StringDedupTable_lock, Mutex::_no_safepoint_check_flag);
    _table->_entries -= removed;
    _entries_removed += removed;
  }
}

uintx StringDedupTable::unlink_or_oops_do(StringDedupUnlinkOrOopsDoClosure* cl,
                                          size_t partition_begin,
                                          size_t partition_end,
                                          uint worker_id) {
  uintx removed = 0;
  for (size_t bucket = partition_begin; bucket < partition_end; bucket++) {
    StringDedupEntry** entry = _table->bucket(bucket);
    while (*entry != NULL) {
      oop* p = (oop*)(*entry)->obj_addr();
      if (cl->is_alive(*p)) {
        cl->keep_alive(p);
        if (is_resizing()) {
          // We are resizing the table, transfer entry to the new table
          _table->transfer(entry, _resized_table);
        } else {
          if (is_rehashing()) {
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

void StringDedupTable::gc_prologue(bool resize_and_rehash_table) {
  assert(!is_resizing() && !is_rehashing(), "Already in progress?");

  _claimed_index = 0;
  if (resize_and_rehash_table) {
    // If both resize and rehash is needed, only do resize. Rehash of
    // the table will eventually happen if the situation persists.
    _resized_table = StringDedupTable::prepare_resize();
    if (!is_resizing()) {
      _rehashed_table = StringDedupTable::prepare_rehash();
    }
  }
}

void StringDedupTable::gc_epilogue() {
  assert(!is_resizing() || !is_rehashing(), "Can not both resize and rehash");
  assert(_claimed_index >= _table->_size / 2 || _claimed_index == 0, "All or nothing");

  if (is_resizing()) {
    StringDedupTable::finish_resize(_resized_table);
    _resized_table = NULL;
  } else if (is_rehashing()) {
    StringDedupTable::finish_rehash(_rehashed_table);
    _rehashed_table = NULL;
  }
}

StringDedupTable* StringDedupTable::prepare_rehash() {
  if (!_table->_rehash_needed && !StringDeduplicationRehashALot) {
    // Rehash not needed
    return NULL;
  }

  // Update statistics
  _rehash_count++;

  // Compute new hash seed
  _table->_hash_seed = AltHashing::compute_seed();

  // Allocate the new table, same size and hash seed
  return new StringDedupTable(_table->_size, _table->_hash_seed);
}

void StringDedupTable::finish_rehash(StringDedupTable* rehashed_table) {
  assert(rehashed_table != NULL, "Invalid table");

  // Move all newly rehashed entries into the correct buckets in the new table
  for (size_t bucket = 0; bucket < _table->_size; bucket++) {
    StringDedupEntry** entry = _table->bucket(bucket);
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

size_t StringDedupTable::claim_table_partition(size_t partition_size) {
  return Atomic::add(partition_size, &_claimed_index) - partition_size;
}

void StringDedupTable::verify() {
  for (size_t bucket = 0; bucket < _table->_size; bucket++) {
    // Verify entries
    StringDedupEntry** entry = _table->bucket(bucket);
    while (*entry != NULL) {
      typeArrayOop value = (*entry)->obj();
      guarantee(value != NULL, "Object must not be NULL");
      guarantee(Universe::heap()->is_in_reserved(value), "Object must be on the heap");
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
    StringDedupEntry** entry1 = _table->bucket(bucket);
    while (*entry1 != NULL) {
      typeArrayOop value1 = (*entry1)->obj();
      bool latin1_1 = (*entry1)->latin1();
      StringDedupEntry** entry2 = (*entry1)->next_addr();
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

void StringDedupTable::clean_entry_cache() {
  _entry_cache->delete_overflowed();
}

void StringDedupTable::print_statistics() {
  Log(gc, stringdedup) log;
  log.debug("  Table");
  log.debug("    Memory Usage: " STRDEDUP_BYTES_FORMAT_NS,
            STRDEDUP_BYTES_PARAM(_table->_size * sizeof(StringDedupEntry*) + (_table->_entries + _entry_cache->size()) * sizeof(StringDedupEntry)));
  log.debug("    Size: " SIZE_FORMAT ", Min: " SIZE_FORMAT ", Max: " SIZE_FORMAT, _table->_size, _min_size, _max_size);
  log.debug("    Entries: " UINTX_FORMAT ", Load: " STRDEDUP_PERCENT_FORMAT_NS ", Cached: " UINTX_FORMAT ", Added: " UINTX_FORMAT ", Removed: " UINTX_FORMAT,
            _table->_entries, percent_of((size_t)_table->_entries, _table->_size), _entry_cache->size(), _entries_added, _entries_removed);
  log.debug("    Resize Count: " UINTX_FORMAT ", Shrink Threshold: " UINTX_FORMAT "(" STRDEDUP_PERCENT_FORMAT_NS "), Grow Threshold: " UINTX_FORMAT "(" STRDEDUP_PERCENT_FORMAT_NS ")",
            _resize_count, _table->_shrink_threshold, _shrink_load_factor * 100.0, _table->_grow_threshold, _grow_load_factor * 100.0);
  log.debug("    Rehash Count: " UINTX_FORMAT ", Rehash Threshold: " UINTX_FORMAT ", Hash Seed: 0x%x", _rehash_count, _rehash_threshold, _table->_hash_seed);
  log.debug("    Age Threshold: " UINTX_FORMAT, StringDeduplicationAgeThreshold);
}
