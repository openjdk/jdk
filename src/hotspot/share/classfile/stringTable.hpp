/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allStatic.hpp"
#include "memory/padded.hpp"
#include "oops/oop.hpp"
#include "oops/oopHandle.hpp"
#include "oops/weakHandle.hpp"
#include "utilities/tableStatistics.hpp"

class CompactHashtableWriter;
class JavaThread;
class SerializeClosure;

class StringTableConfig;

class StringTable : AllStatic {
  friend class StringTableConfig;
  class VerifyCompStrings;
  static volatile bool _has_work;

  // Set if one bucket is out of balance due to hash algorithm deficiency
  static volatile bool _needs_rehashing;

  static OopStorage* _oop_storage;

  static void grow(JavaThread* jt);
  static void clean_dead_entries(JavaThread* jt);

  static double get_load_factor();
  static double get_dead_factor(size_t num_dead);

public:
  typedef struct StringWrapperInternal StringWrapper;

  // Unnamed int needed to fit CompactHashtable's equals type signature
  static bool wrapped_string_equals(oop java_string, const StringWrapper& wrapped_str, int = 0);

private:
  static const char* get_symbol_utf8(const StringWrapper& symbol_str);
  static unsigned int hash_wrapped_string(const StringWrapper& wrapped_str);
  static const jchar* to_unicode(const StringWrapper& wrapped_str, int &len, TRAPS);
  static Handle handle_from_wrapped_string(const StringWrapper& wrapped_str, TRAPS);

  // GC support

  // Callback for GC to notify of changes that might require cleaning or resize.
  static void gc_notification(size_t num_dead);
  static void trigger_concurrent_work();

  static void item_added();
  static void item_removed();
  static size_t items_count_acquire();

  static oop intern(const StringWrapper& name, TRAPS);
  static oop do_intern(const StringWrapper& name, uintx hash, TRAPS);
  static oop do_lookup(const StringWrapper& name, uintx hash);

  static void print_table_statistics(outputStream* st);

 public:
  static size_t table_size();
  static TableStatistics get_table_statistics();

  static void create_table();

  static void do_concurrent_work(JavaThread* jt);
  static bool has_work();

  // Probing
  static oop lookup(Symbol* symbol);
  static oop lookup(const jchar* chars, int length);

  // Interning
  static oop intern(Symbol* symbol, TRAPS);
  static oop intern(oop string, TRAPS);
  static oop intern(const char* utf8_string, TRAPS);

  // Rehash the string table if it gets out of balance
private:
  static bool should_grow();
  static bool maybe_rehash_table();
public:
  static void rehash_table();
  static bool needs_rehashing() { return _needs_rehashing; }
  static inline void update_needs_rehash(bool rehash);

  // AOT support
  static inline oop read_string_from_compact_hashtable(address base_address, u4 index) NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
private:
  static oop lookup_shared(const StringWrapper& name, unsigned int hash) NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
public:
  static oop lookup_shared(const jchar* name, int len) NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  static size_t shared_entry_count() NOT_CDS_JAVA_HEAP_RETURN_(0);
  static void init_shared_table() NOT_CDS_JAVA_HEAP_RETURN;
  static void write_shared_table() NOT_CDS_JAVA_HEAP_RETURN;
  static void serialize_shared_table_header(SerializeClosure* soc) NOT_CDS_JAVA_HEAP_RETURN;
  static void move_shared_strings_into_runtime_table();

  // Jcmd
  static void dump(outputStream* st, bool verbose=false);
  // Debugging
  static size_t verify_and_compare_entries();
  static void verify();
};

#endif // SHARE_CLASSFILE_STRINGTABLE_HPP
