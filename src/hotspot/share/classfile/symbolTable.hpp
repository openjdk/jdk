/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_SYMBOLTABLE_HPP
#define SHARE_CLASSFILE_SYMBOLTABLE_HPP

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "oops/symbol.hpp"
#include "oops/symbolHandle.hpp"
#include "utilities/tableStatistics.hpp"

class JavaThread;
template <typename T> class GrowableArray;

// TempNewSymbol in symbolHandle.hpp is used with SymbolTable operations,
// so include it here.

class CompactHashtableWriter;
class SerializeClosure;

class SymbolTableConfig;
class SymbolTableCreateEntry;

class constantPoolHandle;
class SymbolClosure;

class SymbolTable : public AllStatic {
  friend class VMStructs;
  friend class Symbol;
  friend class ClassFileParser;
  friend class SymbolTableConfig;
  friend class SymbolTableCreateEntry;

 private:
  static volatile bool _has_work;

  // Set if one bucket is out of balance due to hash algorithm deficiency
  static volatile bool _needs_rehashing;

  static void grow(JavaThread* jt);
  static void clean_dead_entries(JavaThread* jt);

  static double get_load_factor();

  static void check_concurrent_work();

  static void item_added();
  static void item_removed();

  // For cleaning
  static void reset_has_items_to_clean();
  static void mark_has_items_to_clean();
  static bool has_items_to_clean();

  static Symbol* do_lookup(const char* name, int len, uintx hash);
  static Symbol* do_add_if_needed(const char* name, int len, uintx hash, bool is_permanent);

  // lookup only, won't add. Also calculate hash. Used by the ClassfileParser.
  static Symbol* lookup_only(const char* name, int len, unsigned int& hash);
  static Symbol* lookup_only_unicode(const jchar* name, int len, unsigned int& hash);

  // Adding elements
  static void new_symbols(ClassLoaderData* loader_data,
                          const constantPoolHandle& cp, int names_count,
                          const char** name, int* lengths,
                          int* cp_indices, unsigned int* hashValues);

  static Symbol* lookup_shared(const char* name, int len, unsigned int hash) NOT_CDS_RETURN_(nullptr);
  static Symbol* lookup_dynamic(const char* name, int len, unsigned int hash);
  static Symbol* lookup_common(const char* name, int len, unsigned int hash);

  // Arena for permanent symbols (null class loader) that are never unloaded
  static Arena*  _arena;
  static Arena* arena() { return _arena; }  // called for statistics

  static void print_table_statistics(outputStream* st);

  static void try_rehash_table();

public:
  // The symbol table
  static size_t table_size();
  static TableStatistics get_table_statistics();

  enum {
    symbol_alloc_batch_size = 8,
    // Pick initial size based on java -version size measurements
    symbol_alloc_arena_size = 360*K // TODO (revisit)
  };

  static void create_table();

  static void do_concurrent_work(JavaThread* jt);
  static bool has_work();
  static void trigger_cleanup();

  // Probing
  // Needed for preloading classes in signatures when compiling.
  // Returns the symbol is already present in symbol table, otherwise
  // null.  NO ALLOCATION IS GUARANTEED!
  static Symbol* probe(const char* name, int len) {
    unsigned int ignore_hash;
    return lookup_only(name, len, ignore_hash);
  }
  static Symbol* probe_unicode(const jchar* name, int len) {
    unsigned int ignore_hash;
    return lookup_only_unicode(name, len, ignore_hash);
  }

  // Symbol lookup and create if not found.
  // jchar (UTF16) version of lookup
  static Symbol* new_symbol(const jchar* name, int len);
  // char (UTF8) versions
  static Symbol* new_symbol(const Symbol* sym, int begin, int end);
  static Symbol* new_symbol(const char* utf8_buffer, int length);
  static Symbol* new_symbol(const char* name) {
    return new_symbol(name, (int)strlen(name));
  }

  // Create a symbol in the arena for symbols that are not deleted
  static Symbol* new_permanent_symbol(const char* name);

  // Rehash the string table if it gets out of balance
private:
  static bool should_grow();
  static bool maybe_rehash_table();

public:
  static void rehash_table();
  static bool needs_rehashing() { return _needs_rehashing; }
  static inline void update_needs_rehash(bool rehash);

  // Heap dumper and CDS
  static void symbols_do(SymbolClosure *cl);

  // Sharing
  static void shared_symbols_do(SymbolClosure *cl);  // no safepoint iteration.
private:
  static void copy_shared_symbol_table(GrowableArray<Symbol*>* symbols,
                                       CompactHashtableWriter* ch_table);
public:
  static size_t estimate_size_for_archive() NOT_CDS_RETURN_(0);
  static void write_to_archive(GrowableArray<Symbol*>* symbols) NOT_CDS_RETURN;
  static void serialize_shared_table_header(SerializeClosure* soc,
                                            bool is_static_archive = true) NOT_CDS_RETURN;

  // Jcmd
  static void dump(outputStream* st, bool verbose=false);
  // Debugging
  static void verify();

  // Histogram
  static void print_histogram() PRODUCT_RETURN;
};

#endif // SHARE_CLASSFILE_SYMBOLTABLE_HPP
