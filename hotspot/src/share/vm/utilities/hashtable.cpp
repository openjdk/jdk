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

#include "precompiled.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/hashtable.hpp"
#include "utilities/hashtable.inline.hpp"


#ifndef USDT2
HS_DTRACE_PROBE_DECL4(hs_private, hashtable__new_entry,
  void*, unsigned int, void*, void*);
#endif /* !USDT2 */

// This is a generic hashtable, designed to be used for the symbol
// and string tables.
//
// It is implemented as an open hash table with a fixed number of buckets.
//
// %note:
//  - HashtableEntrys are allocated in blocks to reduce the space overhead.

BasicHashtableEntry* BasicHashtable::new_entry(unsigned int hashValue) {
  BasicHashtableEntry* entry;

  if (_free_list) {
    entry = _free_list;
    _free_list = _free_list->next();
  } else {
    if (_first_free_entry + _entry_size >= _end_block) {
      int block_size = MIN2(512, MAX2((int)_table_size / 2, (int)_number_of_entries));
      int len = _entry_size * block_size;
      len = 1 << log2_intptr(len); // round down to power of 2
      assert(len >= _entry_size, "");
      _first_free_entry = NEW_C_HEAP_ARRAY(char, len);
      _end_block = _first_free_entry + len;
    }
    entry = (BasicHashtableEntry*)_first_free_entry;
    _first_free_entry += _entry_size;
  }

  assert(_entry_size % HeapWordSize == 0, "");
  entry->set_hash(hashValue);
  return entry;
}


template <class T> HashtableEntry<T>* Hashtable<T>::new_entry(unsigned int hashValue, T obj) {
  HashtableEntry<T>* entry;

  entry = (HashtableEntry<T>*)BasicHashtable::new_entry(hashValue);
  entry->set_literal(obj);
#ifndef USDT2
  HS_DTRACE_PROBE4(hs_private, hashtable__new_entry,
    this, hashValue, obj, entry);
#else /* USDT2 */
  HS_PRIVATE_HASHTABLE_NEW_ENTRY(
    this, hashValue, (uintptr_t) obj, entry);
#endif /* USDT2 */
  return entry;
}


// Check to see if the hashtable is unbalanced.  The caller set a flag to
// rehash at the next safepoint.  If this bucket is 60 times greater than the
// expected average bucket length, it's an unbalanced hashtable.
// This is somewhat an arbitrary heuristic but if one bucket gets to
// rehash_count which is currently 100, there's probably something wrong.

bool BasicHashtable::check_rehash_table(int count) {
  assert(table_size() != 0, "underflow");
  if (count > (((double)number_of_entries()/(double)table_size())*rehash_multiple)) {
    // Set a flag for the next safepoint, which should be at some guaranteed
    // safepoint interval.
    return true;
  }
  return false;
}

// Create a new table and using alternate hash code, populate the new table
// with the existing elements.   This can be used to change the hash code
// and could in the future change the size of the table.

template <class T> void Hashtable<T>::move_to(Hashtable<T>* new_table) {
  int saved_entry_count = number_of_entries();

  // Iterate through the table and create a new entry for the new table
  for (int i = 0; i < new_table->table_size(); ++i) {
    for (HashtableEntry<T>* p = bucket(i); p != NULL; ) {
      HashtableEntry<T>* next = p->next();
      T string = p->literal();
      // Use alternate hashing algorithm on the symbol in the first table
      unsigned int hashValue = new_hash(string);
      // Get a new index relative to the new table (can also change size)
      int index = new_table->hash_to_index(hashValue);
      p->set_hash(hashValue);
      unlink_entry(p);
      new_table->add_entry(index, p);
      p = next;
    }
  }
  // give the new table the free list as well
  new_table->copy_freelist(this);
  assert(new_table->number_of_entries() == saved_entry_count, "lost entry on dictionary copy?");

  // Destroy memory used by the buckets in the hashtable.  The memory
  // for the elements has been used in a new table and is not
  // destroyed.  The memory reuse will benefit resizing the SystemDictionary
  // to avoid a memory allocation spike at safepoint.
  free_buckets();
}

// Reverse the order of elements in the hash buckets.

void BasicHashtable::reverse() {

  for (int i = 0; i < _table_size; ++i) {
    BasicHashtableEntry* new_list = NULL;
    BasicHashtableEntry* p = bucket(i);
    while (p != NULL) {
      BasicHashtableEntry* next = p->next();
      p->set_next(new_list);
      new_list = p;
      p = next;
    }
    *bucket_addr(i) = new_list;
  }
}


// Copy the table to the shared space.

void BasicHashtable::copy_table(char** top, char* end) {

  // Dump the hash table entries.

  intptr_t *plen = (intptr_t*)(*top);
  *top += sizeof(*plen);

  int i;
  for (i = 0; i < _table_size; ++i) {
    for (BasicHashtableEntry** p = _buckets[i].entry_addr();
                              *p != NULL;
                               p = (*p)->next_addr()) {
      if (*top + entry_size() > end) {
        report_out_of_shared_space(SharedMiscData);
      }
      *p = (BasicHashtableEntry*)memcpy(*top, *p, entry_size());
      *top += entry_size();
    }
  }
  *plen = (char*)(*top) - (char*)plen - sizeof(*plen);

  // Set the shared bit.

  for (i = 0; i < _table_size; ++i) {
    for (BasicHashtableEntry* p = bucket(i); p != NULL; p = p->next()) {
      p->set_shared();
    }
  }
}



// Reverse the order of elements in the hash buckets.

template <class T> void Hashtable<T>::reverse(void* boundary) {

  for (int i = 0; i < table_size(); ++i) {
    HashtableEntry<T>* high_list = NULL;
    HashtableEntry<T>* low_list = NULL;
    HashtableEntry<T>* last_low_entry = NULL;
    HashtableEntry<T>* p = bucket(i);
    while (p != NULL) {
      HashtableEntry<T>* next = p->next();
      if ((void*)p->literal() >= boundary) {
        p->set_next(high_list);
        high_list = p;
      } else {
        p->set_next(low_list);
        low_list = p;
        if (last_low_entry == NULL) {
          last_low_entry = p;
        }
      }
      p = next;
    }
    if (low_list != NULL) {
      *bucket_addr(i) = low_list;
      last_low_entry->set_next(high_list);
    } else {
      *bucket_addr(i) = high_list;
    }
  }
}


// Dump the hash table buckets.

void BasicHashtable::copy_buckets(char** top, char* end) {
  intptr_t len = _table_size * sizeof(HashtableBucket);
  *(intptr_t*)(*top) = len;
  *top += sizeof(intptr_t);

  *(intptr_t*)(*top) = _number_of_entries;
  *top += sizeof(intptr_t);

  if (*top + len > end) {
    report_out_of_shared_space(SharedMiscData);
  }
  _buckets = (HashtableBucket*)memcpy(*top, _buckets, len);
  *top += len;
}


#ifndef PRODUCT

template <class T> void Hashtable<T>::print() {
  ResourceMark rm;

  for (int i = 0; i < table_size(); i++) {
    HashtableEntry<T>* entry = bucket(i);
    while(entry != NULL) {
      tty->print("%d : ", i);
      entry->literal()->print();
      tty->cr();
      entry = entry->next();
    }
  }
}


void BasicHashtable::verify() {
  int count = 0;
  for (int i = 0; i < table_size(); i++) {
    for (BasicHashtableEntry* p = bucket(i); p != NULL; p = p->next()) {
      ++count;
    }
  }
  assert(count == number_of_entries(), "number of hashtable entries incorrect");
}


#endif // PRODUCT


#ifdef ASSERT

void BasicHashtable::verify_lookup_length(double load) {
  if ((double)_lookup_length / (double)_lookup_count > load * 2.0) {
    warning("Performance bug: SystemDictionary lookup_count=%d "
            "lookup_length=%d average=%lf load=%f",
            _lookup_count, _lookup_length,
            (double) _lookup_length / _lookup_count, load);
  }
}

#endif

// Explicitly instantiate these types
template class Hashtable<constantPoolOop>;
template class Hashtable<Symbol*>;
template class Hashtable<klassOop>;
template class Hashtable<oop>;

