/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "oops/symbolOop.hpp"
#include "runtime/handles.hpp"

// This is a generic hashtable, designed to be used for the symbol
// and string tables.
//
// It is implemented as an open hash table with a fixed number of buckets.
//
// %note:
//  - TableEntrys are allocated in blocks to reduce the space overhead.



class BasicHashtableEntry : public CHeapObj {
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
  BasicHashtableEntry* _next;

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

  static BasicHashtableEntry* make_ptr(BasicHashtableEntry* p) {
    return (BasicHashtableEntry*)((intptr_t)p & -2);
  }

  BasicHashtableEntry* next() const {
    return make_ptr(_next);
  }

  void set_next(BasicHashtableEntry* next) {
    _next = next;
  }

  BasicHashtableEntry** next_addr() {
    return &_next;
  }

  bool is_shared() const {
    return ((intptr_t)_next & 1) != 0;
  }

  void set_shared() {
    _next = (BasicHashtableEntry*)((intptr_t)_next | 1);
  }
};



class HashtableEntry : public BasicHashtableEntry {
  friend class VMStructs;
private:
  oop               _literal;          // ref to item in table.

public:
  // Literal
  oop literal() const                   { return _literal; }
  oop* literal_addr()                   { return &_literal; }
  void set_literal(oop s)               { _literal = s; }

  HashtableEntry* next() const {
    return (HashtableEntry*)BasicHashtableEntry::next();
  }
  HashtableEntry** next_addr() {
    return (HashtableEntry**)BasicHashtableEntry::next_addr();
  }
};



class HashtableBucket : public CHeapObj {
  friend class VMStructs;
private:
  // Instance variable
  BasicHashtableEntry*       _entry;

public:
  // Accessing
  void clear()                        { _entry = NULL; }

  // The following methods use order access methods to avoid race
  // conditions in multiprocessor systems.
  BasicHashtableEntry* get_entry() const;
  void set_entry(BasicHashtableEntry* l);

  // The following method is not MT-safe and must be done under lock.
  BasicHashtableEntry** entry_addr()  { return &_entry; }
};


class BasicHashtable : public CHeapObj {
  friend class VMStructs;

public:
  BasicHashtable(int table_size, int entry_size);
  BasicHashtable(int table_size, int entry_size,
                 HashtableBucket* buckets, int number_of_entries);

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
  HashtableBucket*  _buckets;
  BasicHashtableEntry* _free_list;
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

  void initialize(int table_size, int entry_size, int number_of_entries);

  // Accessor
  int entry_size() const { return _entry_size; }
  int table_size() { return _table_size; }

  // The following method is MT-safe and may be used with caution.
  BasicHashtableEntry* bucket(int i);

  // The following method is not MT-safe and must be done under lock.
  BasicHashtableEntry** bucket_addr(int i) { return _buckets[i].entry_addr(); }

  // Table entry management
  BasicHashtableEntry* new_entry(unsigned int hashValue);

public:
  void set_entry(int index, BasicHashtableEntry* entry);

  void add_entry(int index, BasicHashtableEntry* entry);

  void free_entry(BasicHashtableEntry* entry);

  int number_of_entries() { return _number_of_entries; }

  void verify() PRODUCT_RETURN;
};


class Hashtable : public BasicHashtable {
  friend class VMStructs;

public:
  Hashtable(int table_size, int entry_size)
    : BasicHashtable(table_size, entry_size) { }

  Hashtable(int table_size, int entry_size,
                   HashtableBucket* buckets, int number_of_entries)
    : BasicHashtable(table_size, entry_size, buckets, number_of_entries) { }

  // Invoke "f->do_oop" on the locations of all oops in the table.
  void oops_do(OopClosure* f);

  // Debugging
  void print()               PRODUCT_RETURN;

  // GC support
  //   Delete pointers to otherwise-unreachable objects.
  void unlink(BoolObjectClosure* cl);

  // Reverse the order of elements in each of the buckets. Hashtable
  // entries which refer to objects at a lower address than 'boundary'
  // are separated from those which refer to objects at higher
  // addresses, and appear first in the list.
  void reverse(void* boundary = NULL);

protected:

  static unsigned int hash_symbol(const char* s, int len);

  unsigned int compute_hash(symbolHandle name) {
    return (unsigned int) name->identity_hash();
  }

  int index_for(symbolHandle name) {
    return hash_to_index(compute_hash(name));
  }

  // Table entry management
  HashtableEntry* new_entry(unsigned int hashValue, oop obj);

  // The following method is MT-safe and may be used with caution.
  HashtableEntry* bucket(int i) {
    return (HashtableEntry*)BasicHashtable::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  HashtableEntry** bucket_addr(int i) {
    return (HashtableEntry**)BasicHashtable::bucket_addr(i);
  }
};


//  Verions of hashtable where two handles are used to compute the index.

class TwoOopHashtable : public Hashtable {
  friend class VMStructs;
protected:
  TwoOopHashtable(int table_size, int entry_size)
    : Hashtable(table_size, entry_size) {}

  TwoOopHashtable(int table_size, int entry_size, HashtableBucket* t,
                  int number_of_entries)
    : Hashtable(table_size, entry_size, t, number_of_entries) {}

public:
  unsigned int compute_hash(symbolHandle name, Handle loader) {
    // Be careful with identity_hash(), it can safepoint and if this
    // were one expression, the compiler could choose to unhandle each
    // oop before calling identity_hash() for either of them.  If the first
    // causes a GC, the next would fail.
    unsigned int name_hash = name->identity_hash();
    unsigned int loader_hash = loader.is_null() ? 0 : loader->identity_hash();
    return name_hash ^ loader_hash;
  }

  int index_for(symbolHandle name, Handle loader) {
    return hash_to_index(compute_hash(name, loader));
  }
};

#endif // SHARE_VM_UTILITIES_HASHTABLE_HPP
