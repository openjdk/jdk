/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_STRINGTABLE_HPP
#define SHARE_VM_CLASSFILE_STRINGTABLE_HPP

#include "memory/allocation.inline.hpp"
#include "utilities/hashtable.hpp"

template <class T, class N> class CompactHashtable;
class CompactHashtableWriter;
class FileMapInfo;

class StringTable : public RehashableHashtable<oop, mtSymbol> {
  friend class VMStructs;
  friend class Symbol;

private:
  // The string table
  static StringTable* _the_table;

  // Shared string table
  static CompactHashtable<oop, char> _shared_table;
  static bool _ignore_shared_strings;

  // Set if one bucket is out of balance due to hash algorithm deficiency
  static bool _needs_rehashing;

  // Claimed high water mark for parallel chunked scanning
  static volatile int _parallel_claimed_idx;

  static oop intern(Handle string_or_null, jchar* chars, int length, TRAPS);
  oop basic_add(int index, Handle string_or_null, jchar* name, int len,
                unsigned int hashValue, TRAPS);

  oop lookup_in_main_table(int index, jchar* chars, int length, unsigned int hashValue);
  static oop lookup_shared(jchar* name, int len);

  // Apply the give oop closure to the entries to the buckets
  // in the range [start_idx, end_idx).
  static void buckets_oops_do(OopClosure* f, int start_idx, int end_idx);
  // Unlink or apply the give oop closure to the entries to the buckets
  // in the range [start_idx, end_idx).
  static void buckets_unlink_or_oops_do(BoolObjectClosure* is_alive, OopClosure* f, int start_idx, int end_idx, int* processed, int* removed);

  StringTable() : RehashableHashtable<oop, mtSymbol>((int)StringTableSize,
                              sizeof (HashtableEntry<oop, mtSymbol>)) {}

  StringTable(HashtableBucket<mtSymbol>* t, int number_of_entries)
    : RehashableHashtable<oop, mtSymbol>((int)StringTableSize, sizeof (HashtableEntry<oop, mtSymbol>), t,
                     number_of_entries) {}
public:
  // The string table
  static StringTable* the_table() { return _the_table; }

  // Size of one bucket in the string table.  Used when checking for rollover.
  static uint bucket_size() { return sizeof(HashtableBucket<mtSymbol>); }

  static void create_table() {
    assert(_the_table == NULL, "One string table allowed.");
    _the_table = new StringTable();
  }

  // GC support
  //   Delete pointers to otherwise-unreachable objects.
  static void unlink_or_oops_do(BoolObjectClosure* cl, OopClosure* f) {
    int processed = 0;
    int removed = 0;
    unlink_or_oops_do(cl, f, &processed, &removed);
  }
  static void unlink(BoolObjectClosure* cl) {
    int processed = 0;
    int removed = 0;
    unlink_or_oops_do(cl, NULL, &processed, &removed);
  }
  static void unlink_or_oops_do(BoolObjectClosure* cl, OopClosure* f, int* processed, int* removed);
  static void unlink(BoolObjectClosure* cl, int* processed, int* removed) {
    unlink_or_oops_do(cl, NULL, processed, removed);
  }
  // Serially invoke "f->do_oop" on the locations of all oops in the table.
  static void oops_do(OopClosure* f);

  // Possibly parallel versions of the above
  static void possibly_parallel_unlink_or_oops_do(BoolObjectClosure* cl, OopClosure* f, int* processed, int* removed);
  static void possibly_parallel_unlink(BoolObjectClosure* cl, int* processed, int* removed) {
    possibly_parallel_unlink_or_oops_do(cl, NULL, processed, removed);
  }
  static void possibly_parallel_oops_do(OopClosure* f);

  // Hashing algorithm, used as the hash value used by the
  //     StringTable for bucket selection and comparison (stored in the
  //     HashtableEntry structures).  This is used in the String.intern() method.
  template<typename T> static unsigned int hash_string(const T* s, int len);

  // Internal test.
  static void test_alt_hash() PRODUCT_RETURN;

  // Probing
  static oop lookup(Symbol* symbol);
  static oop lookup(jchar* chars, int length);

  // Interning
  static oop intern(Symbol* symbol, TRAPS);
  static oop intern(oop string, TRAPS);
  static oop intern(const char *utf8_string, TRAPS);

  // Debugging
  static void verify();
  static void dump(outputStream* st, bool verbose=false);

  enum VerifyMesgModes {
    _verify_quietly    = 0,
    _verify_with_mesgs = 1
  };

  enum VerifyRetTypes {
    _verify_pass          = 0,
    _verify_fail_continue = 1,
    _verify_fail_done     = 2
  };

  static VerifyRetTypes compare_entries(int bkt1, int e_cnt1,
                                        HashtableEntry<oop, mtSymbol>* e_ptr1,
                                        int bkt2, int e_cnt2,
                                        HashtableEntry<oop, mtSymbol>* e_ptr2);
  static VerifyRetTypes verify_entry(int bkt, int e_cnt,
                                     HashtableEntry<oop, mtSymbol>* e_ptr,
                                     VerifyMesgModes mesg_mode);
  static int verify_and_compare_entries();

  // Sharing
  static void ignore_shared_strings(bool v) { _ignore_shared_strings = v; }
  static bool shared_string_ignored()       { return _ignore_shared_strings; }
  static void shared_oops_do(OopClosure* f);
  static bool copy_shared_string(GrowableArray<MemRegion> *string_space,
                                 CompactHashtableWriter* ch_table);
  static bool copy_compact_table(char** top, char* end, GrowableArray<MemRegion> *string_space,
                                 size_t* space_size);
  static const char* init_shared_table(FileMapInfo *mapinfo, char* buffer);
  static void reverse() {
    the_table()->Hashtable<oop, mtSymbol>::reverse();
  }

  // Rehash the symbol table if it gets out of balance
  static void rehash_table();
  static bool needs_rehashing() { return _needs_rehashing; }

  // Parallel chunked scanning
  static void clear_parallel_claimed_index() { _parallel_claimed_idx = 0; }
  static int parallel_claimed_index() { return _parallel_claimed_idx; }
};
#endif // SHARE_VM_CLASSFILE_STRINGTABLE_HPP
