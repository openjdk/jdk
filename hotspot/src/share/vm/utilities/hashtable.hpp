/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_HASHTABLE_HPP
#define SHARE_VM_UTILITIES_HASHTABLE_HPP

#include "classfile/classLoaderData.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.hpp"

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

  // Link to next element in the linked list for this bucket.  EXCEPT
  // bit 0 set indicates that this entry is shared and must not be
  // unlinked from the table. Bit 0 is set during the dumping of the
  // archive. Since shared entries are immutable, _next fields in the
  // shared entries will not change.  New entries will always be
  // unshared and since pointers are align, bit 0 will always remain 0
  // with no extra effort.
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

  static BasicHashtableEntry<F>* make_ptr(BasicHashtableEntry<F>* p) {
    return (BasicHashtableEntry*)((intptr_t)p & -2);
  }

  BasicHashtableEntry<F>* next() const {
    return make_ptr(_next);
  }

  void set_next(BasicHashtableEntry<F>* next) {
    _next = next;
  }

  BasicHashtableEntry<F>** next_addr() {
    return &_next;
  }

  bool is_shared() const {
    return ((intptr_t)_next & 1) != 0;
  }

  void set_shared() {
    _next = (BasicHashtableEntry<F>*)((intptr_t)_next | 1);
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

  // Sharing support.
  void copy_buckets(char** top, char* end);
  void copy_table(char** top, char* end);

  // Bucket handling
  int hash_to_index(unsigned int full_hash) {
    int h = full_hash % _table_size;
    assert(h >= 0 && h < _table_size, "Illegal hash value");
    return h;
  }

  // Reverse the order of elements in each of the buckets.
  void reverse();

private:
  // Instance variables
  int               _table_size;
  HashtableBucket<F>*     _buckets;
  BasicHashtableEntry<F>* _free_list;
  char*             _first_free_entry;
  char*             _end_block;
  int               _entry_size;
  int               _number_of_entries;

protected:

#ifdef ASSERT
  int               _lookup_count;
  int               _lookup_length;
  void verify_lookup_length(double load);
#endif

  enum {
    rehash_count = 100,
    rehash_multiple = 60
  };

  void initialize(int table_size, int entry_size, int number_of_entries);

  // Accessor
  int entry_size() const { return _entry_size; }

  // The following method is MT-safe and may be used with caution.
  BasicHashtableEntry<F>* bucket(int i);

  // The following method is not MT-safe and must be done under lock.
  BasicHashtableEntry<F>** bucket_addr(int i) { return _buckets[i].entry_addr(); }

  // Table entry management
  BasicHashtableEntry<F>* new_entry(unsigned int hashValue);

  // Check that the table is unbalanced
  bool check_rehash_table(int count);

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
  int table_size() { return _table_size; }
  void set_entry(int index, BasicHashtableEntry<F>* entry);

  void add_entry(int index, BasicHashtableEntry<F>* entry);

  void free_entry(BasicHashtableEntry<F>* entry);

  int number_of_entries() { return _number_of_entries; }

  void verify() PRODUCT_RETURN;
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

  // Reverse the order of elements in each of the buckets. Hashtable
  // entries which refer to objects at a lower address than 'boundary'
  // are separated from those which refer to objects at higher
  // addresses, and appear first in the list.
  void reverse(void* boundary = NULL);

protected:

  unsigned int compute_hash(Symbol* name) {
    return (unsigned int) name->identity_hash();
  }

  int index_for(Symbol* name) {
    return this->hash_to_index(compute_hash(name));
  }

  // Table entry management
  HashtableEntry<T, F>* new_entry(unsigned int hashValue, T obj);

  // The following method is MT-safe and may be used with caution.
  HashtableEntry<T, F>* bucket(int i) {
    return (HashtableEntry<T, F>*)BasicHashtable<F>::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  HashtableEntry<T, F>** bucket_addr(int i) {
    return (HashtableEntry<T, F>**)BasicHashtable<F>::bucket_addr(i);
  }

  // Function to move these elements into the new table.
  void move_to(Hashtable<T, F>* new_table);
  static bool use_alternate_hashcode()  { return _seed != 0; }
  static jint seed()                    { return _seed; }

  static int literal_size(Symbol *symbol);
  static int literal_size(oop oop);

  // The following two are currently not used, but are needed anyway because some
  // C++ compilers (MacOS and Solaris) force the instantiation of
  // Hashtable<ConstantPool*, mtClass>::dump_table() even though we never call this function
  // in the VM code.
  static int literal_size(ConstantPool *cp) {Unimplemented(); return 0;}
  static int literal_size(Klass *k)         {Unimplemented(); return 0;}

public:
  void dump_table(outputStream* st, const char *table_name);

 private:
  static jint _seed;
};


//  Verions of hashtable where two handles are used to compute the index.

template <class T, MEMFLAGS F> class TwoOopHashtable : public Hashtable<T, F> {
  friend class VMStructs;
protected:
  TwoOopHashtable(int table_size, int entry_size)
    : Hashtable<T, F>(table_size, entry_size) {}

  TwoOopHashtable(int table_size, int entry_size, HashtableBucket<F>* t,
                  int number_of_entries)
    : Hashtable<T, F>(table_size, entry_size, t, number_of_entries) {}

public:
  unsigned int compute_hash(Symbol* name, ClassLoaderData* loader_data) {
    unsigned int name_hash = name->identity_hash();
    // loader is null with CDS
    assert(loader_data != NULL || UseSharedSpaces || DumpSharedSpaces,
           "only allowed with shared spaces");
    unsigned int loader_hash = loader_data == NULL ? 0 : loader_data->identity_hash();
    return name_hash ^ loader_hash;
  }

  int index_for(Symbol* name, ClassLoaderData* loader_data) {
    return this->hash_to_index(compute_hash(name, loader_data));
  }
};

#endif // SHARE_VM_UTILITIES_HASHTABLE_HPP
