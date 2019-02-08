/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_STRINGTABLE_HPP
#define SHARE_CLASSFILE_STRINGTABLE_HPP

#include "gc/shared/oopStorage.hpp"
#include "gc/shared/oopStorageParState.hpp"
#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "oops/oop.hpp"
#include "oops/weakHandle.hpp"
#include "utilities/concurrentHashTable.hpp"

class CompactHashtableWriter;
class SerializeClosure;

class StringTable;
class StringTableConfig;
typedef ConcurrentHashTable<WeakHandle<vm_string_table_data>,
                            StringTableConfig, mtSymbol> StringTableHash;

class StringTableCreateEntry;

class StringTable : public CHeapObj<mtSymbol>{
  friend class VMStructs;
  friend class Symbol;
  friend class StringTableConfig;
  friend class StringTableCreateEntry;

private:
  void grow(JavaThread* jt);
  void clean_dead_entries(JavaThread* jt);

  // The string table
  static StringTable* _the_table;
  static volatile bool _alt_hash;

private:

  StringTableHash* _local_table;
  size_t _current_size;
  volatile bool _has_work;
  // Set if one bucket is out of balance due to hash algorithm deficiency
  volatile bool _needs_rehashing;

  OopStorage* _weak_handles;

  volatile size_t _items_count;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile size_t));
  volatile size_t _uncleaned_items_count;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile size_t));

  double get_load_factor() const;
  double get_dead_factor() const;

  void check_concurrent_work();
  void trigger_concurrent_work();

  static size_t item_added();
  static void item_removed();
  size_t add_items_to_clean(size_t ndead);

  StringTable();

  static oop intern(Handle string_or_null_h, const jchar* name, int len, TRAPS);
  oop do_intern(Handle string_or_null, const jchar* name, int len, uintx hash, TRAPS);
  oop do_lookup(const jchar* name, int len, uintx hash);

  void concurrent_work(JavaThread* jt);
  void print_table_statistics(outputStream* st, const char* table_name);

  void try_rehash_table();
  bool do_rehash();
  inline void update_needs_rehash(bool rehash);

 public:
  // The string table
  static StringTable* the_table() { return _the_table; }
  size_t table_size();

  static OopStorage* weak_storage() { return the_table()->_weak_handles; }

  static void create_table() {
    assert(_the_table == NULL, "One string table allowed.");
    _the_table = new StringTable();
  }

  static void do_concurrent_work(JavaThread* jt);
  static bool has_work() { return the_table()->_has_work; }

  // GC support

  // Must be called before a parallel walk where strings might die.
  static void reset_dead_counter() {
    the_table()->_uncleaned_items_count = 0;
  }
  // After the parallel walk this method must be called to trigger
  // cleaning. Note it might trigger a resize instead.
  static void finish_dead_counter() {
    the_table()->check_concurrent_work();
  }

  // If GC uses ParState directly it should add the number of cleared
  // strings to this method.
  static void inc_dead_counter(size_t ndead) {
    the_table()->add_items_to_clean(ndead);
  }

  // Serially invoke "f->do_oop" on the locations of all oops in the table.
  static void oops_do(OopClosure* f);

  // Possibly parallel versions of the above
  static void possibly_parallel_oops_do(
     OopStorage::ParState<false /* concurrent */, false /* const*/>* par_state_string,
     OopClosure* f);

  // Probing
  static oop lookup(Symbol* symbol);
  static oop lookup(const jchar* chars, int length);

  // Interning
  static oop intern(Symbol* symbol, TRAPS);
  static oop intern(oop string, TRAPS);
  static oop intern(const char *utf8_string, TRAPS);

  // Rehash the string table if it gets out of balance
  static void rehash_table();
  static bool needs_rehashing()
    { return StringTable::the_table()->_needs_rehashing; }

  // Sharing
 private:
  oop lookup_shared(const jchar* name, int len, unsigned int hash) NOT_CDS_JAVA_HEAP_RETURN_(NULL);
  static void copy_shared_string_table(CompactHashtableWriter* ch_table) NOT_CDS_JAVA_HEAP_RETURN;
 public:
  static oop create_archived_string(oop s, Thread* THREAD) NOT_CDS_JAVA_HEAP_RETURN_(NULL);
  static void shared_oops_do(OopClosure* f) NOT_CDS_JAVA_HEAP_RETURN;
  static void write_to_archive() NOT_CDS_JAVA_HEAP_RETURN;
  static void serialize_shared_table_header(SerializeClosure* soc) NOT_CDS_JAVA_HEAP_RETURN;

  // Jcmd
  static void dump(outputStream* st, bool verbose=false);
  // Debugging
  static size_t verify_and_compare_entries();
  static void verify();
};

#endif // SHARE_CLASSFILE_STRINGTABLE_HPP
