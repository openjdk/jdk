/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_HASHTABLE_HPP
#define SHARE_UTILITIES_HASHTABLE_HPP

#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/tableStatistics.hpp"

// This is a generic hashtable, designed to be used for the symbol
// and string tables.
//
// It is implemented as an open hash table with a fixed number of buckets.
//
// %note:
//  - TableEntrys are allocated in blocks to reduce the space overhead.



template <MEMFLAGS F> class BasicHashtableEntry : public CHeapObj<F> {
  friend class VMStructs;
private:
  unsigned int         _hash;           // 32-bit hash for item

  // Link to next element in the linked list for this bucket.
  BasicHashtableEntry<F>* _next;

  // Windows IA64 compiler requires subclasses to be able to access these
protected:
  // Entry objects should not be created, they should be taken from the
  // free list with BasicHashtable.new_entry().
  BasicHashtableEntry() { ShouldNotReachHere(); }
  // Entry objects should not be destroyed.  They should be placed on
  // the free list instead with BasicHashtable.free_entry().
  ~BasicHashtableEntry() { ShouldNotReachHere(); }

public:

  unsigned int hash() const             { return _hash; }
  void set_hash(unsigned int hash)      { _hash = hash; }
  unsigned int* hash_addr()             { return &_hash; }

  BasicHashtableEntry<F>* next() const {
    return _next;
  }

  void set_next(BasicHashtableEntry<F>* next) {
    _next = next;
  }

  BasicHashtableEntry<F>** next_addr() {
    return &_next;
  }
};



template <class T, MEMFLAGS F> class HashtableEntry : public BasicHashtableEntry<F> {
  friend class VMStructs;
private:
  T               _literal;          // ref to item in table.

public:
  // Literal
  T literal() const                   { return _literal; }
  T* literal_addr()                   { return &_literal; }
  void set_literal(T s)               { _literal = s; }

  HashtableEntry* next() const {
    return (HashtableEntry*)BasicHashtableEntry<F>::next();
  }
  HashtableEntry** next_addr() {
    return (HashtableEntry**)BasicHashtableEntry<F>::next_addr();
  }
};



template <MEMFLAGS F> class HashtableBucket : public CHeapObj<F> {
  friend class VMStructs;
private:
  // Instance variable
  BasicHashtableEntry<F>*       _entry;

public:
  // Accessing
  void clear()                        { _entry = NULL; }

  // The following methods use order access methods to avoid race
  // conditions in multiprocessor systems.
  BasicHashtableEntry<F>* get_entry() const;
  void set_entry(BasicHashtableEntry<F>* l);

  // The following method is not MT-safe and must be done under lock.
  BasicHashtableEntry<F>** entry_addr()  { return &_entry; }

};


template <MEMFLAGS F> class BasicHashtable : public CHeapObj<F> {
  friend class VMStructs;

public:
  BasicHashtable(int table_size, int entry_size);
  BasicHashtable(int table_size, int entry_size,
                 HashtableBucket<F>* buckets, int number_of_entries);
  ~BasicHashtable();

  // Bucket handling
  int hash_to_index(unsigned int full_hash) const {
    int h = full_hash % _table_size;
    assert(h >= 0 && h < _table_size, "Illegal hash value");
    return h;
  }

private:
  // Instance variables
  int                              _table_size;
  HashtableBucket<F>*              _buckets;
  BasicHashtableEntry<F>* volatile _free_list;
  char*                            _first_free_entry;
  char*                            _end_block;
  int                              _entry_size;
  volatile int                     _number_of_entries;
  GrowableArrayCHeap<char*, F>     _entry_blocks;

protected:

  TableRateStatistics _stats_rate;

  void initialize(int table_size, int entry_size, int number_of_entries);

  // Accessor
  int entry_size() const { return _entry_size; }

  // The following method is MT-safe and may be used with caution.
  BasicHashtableEntry<F>* bucket(int i) const;

  // The following method is not MT-safe and must be done under lock.
  BasicHashtableEntry<F>** bucket_addr(int i) { return _buckets[i].entry_addr(); }

  // Attempt to get an entry from the free list
  BasicHashtableEntry<F>* new_entry_free_list();

  // Table entry management
  BasicHashtableEntry<F>* new_entry(unsigned int hashValue);

  // Used when moving the entry to another table
  // Clean up links, but do not add to free_list
  void unlink_entry(BasicHashtableEntry<F>* entry) {
    entry->set_next(NULL);
    --_number_of_entries;
  }

  // Move over freelist and free block for allocation
  void copy_freelist(BasicHashtable* src) {
    _free_list = src->_free_list;
    src->_free_list = NULL;
    _first_free_entry = src->_first_free_entry;
    src->_first_free_entry = NULL;
    _end_block = src->_end_block;
    src->_end_block = NULL;
  }

  // Free the buckets in this hashtable
  void free_buckets();
public:
  int table_size() const { return _table_size; }
  void set_entry(int index, BasicHashtableEntry<F>* entry);

  void add_entry(int index, BasicHashtableEntry<F>* entry);

  void free_entry(BasicHashtableEntry<F>* entry);

  int number_of_entries() const { return _number_of_entries; }

  int calculate_resize(bool use_large_table_sizes) const;
  bool resize(int new_size);

  // Grow the number of buckets if the average entries per bucket is over the load_factor
  bool maybe_grow(int max_size, int load_factor = 8);

  template <class T> void verify_table(const char* table_name) PRODUCT_RETURN;
};


template <class T, MEMFLAGS F> class Hashtable : public BasicHashtable<F> {
  friend class VMStructs;

public:
  Hashtable(int table_size, int entry_size)
    : BasicHashtable<F>(table_size, entry_size) { }

  Hashtable(int table_size, int entry_size,
                   HashtableBucket<F>* buckets, int number_of_entries)
    : BasicHashtable<F>(table_size, entry_size, buckets, number_of_entries) { }

  // Debugging
  void print()               PRODUCT_RETURN;

  unsigned int compute_hash(const Symbol* name) const {
    return (unsigned int) name->identity_hash();
  }

  int index_for(const Symbol* name) const {
    return this->hash_to_index(compute_hash(name));
  }

  TableStatistics statistics_calculate(T (*literal_load_barrier)(HashtableEntry<T, F>*) = NULL);
  void print_table_statistics(outputStream* st, const char *table_name, T (*literal_load_barrier)(HashtableEntry<T, F>*) = NULL);

 protected:

  // Table entry management
  HashtableEntry<T, F>* new_entry(unsigned int hashValue, T obj);
  // Don't create and use freelist of HashtableEntry.
  HashtableEntry<T, F>* allocate_new_entry(unsigned int hashValue, T obj);

  // The following method is MT-safe and may be used with caution.
  HashtableEntry<T, F>* bucket(int i) const {
    return (HashtableEntry<T, F>*)BasicHashtable<F>::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  HashtableEntry<T, F>** bucket_addr(int i) {
    return (HashtableEntry<T, F>**)BasicHashtable<F>::bucket_addr(i);
  }
};

// A subclass of BasicHashtable that allows you to do a simple K -> V mapping
// without using tons of boilerplate code.
template<
    typename K, typename V, MEMFLAGS F,
    unsigned (*HASH)  (K const&)           = primitive_hash<K>,
    bool     (*EQUALS)(K const&, K const&) = primitive_equals<K>
    >
class KVHashtable : public BasicHashtable<F> {
  class KVHashtableEntry : public BasicHashtableEntry<F> {
  public:
    K _key;
    V _value;
    KVHashtableEntry* next() {
      return (KVHashtableEntry*)BasicHashtableEntry<F>::next();
    }
  };

protected:
  KVHashtableEntry* bucket(int i) const {
    return (KVHashtableEntry*)BasicHashtable<F>::bucket(i);
  }

  KVHashtableEntry* new_entry(unsigned int hashValue, K key, V value) {
    KVHashtableEntry* entry = (KVHashtableEntry*)BasicHashtable<F>::new_entry(hashValue);
    entry->_key   = key;
    entry->_value = value;
    return entry;
  }

public:
  KVHashtable(int table_size) : BasicHashtable<F>(table_size, sizeof(KVHashtableEntry)) {}

  V* add(K key, V value) {
    unsigned int hash = HASH(key);
    KVHashtableEntry* entry = new_entry(hash, key, value);
    BasicHashtable<F>::add_entry(BasicHashtable<F>::hash_to_index(hash), entry);
    return &(entry->_value);
  }

  V* lookup(K key) const {
    unsigned int hash = HASH(key);
    int index = BasicHashtable<F>::hash_to_index(hash);
    for (KVHashtableEntry* e = bucket(index); e != NULL; e = e->next()) {
      if (e->hash() == hash && EQUALS(e->_key, key)) {
        return &(e->_value);
      }
    }
    return NULL;
  }

  // Look up the key.
  // If an entry for the key exists, leave map unchanged and return a pointer to its value.
  // If no entry for the key exists, create a new entry from key and value and return a
  //  pointer to the value.
  // *p_created is true if entry was created, false if entry pre-existed.
  V* add_if_absent(K key, V value, bool* p_created) {
    unsigned int hash = HASH(key);
    int index = BasicHashtable<F>::hash_to_index(hash);
    for (KVHashtableEntry* e = bucket(index); e != NULL; e = e->next()) {
      if (e->hash() == hash && EQUALS(e->_key, key)) {
        *p_created = false;
        return &(e->_value);
      }
    }

    KVHashtableEntry* entry = new_entry(hash, key, value);
    BasicHashtable<F>::add_entry(BasicHashtable<F>::hash_to_index(hash), entry);
    *p_created = true;
    return &(entry->_value);
  }

  int table_size() const {
    return BasicHashtable<F>::table_size();
  }

  // ITER contains bool do_entry(K, V const&), which will be
  // called for each entry in the table.  If do_entry() returns false,
  // the iteration is cancelled.
  template<class ITER>
  void iterate(ITER* iter) const {
    for (int index = 0; index < table_size(); index++) {
      for (KVHashtableEntry* e = bucket(index); e != NULL; e = e->next()) {
        bool cont = iter->do_entry(e->_key, &e->_value);
        if (!cont) { return; }
      }
    }
  }
};


#endif // SHARE_UTILITIES_HASHTABLE_HPP
