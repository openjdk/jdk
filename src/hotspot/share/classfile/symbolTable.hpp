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

#ifndef SHARE_CLASSFILE_SYMBOLTABLE_HPP
#define SHARE_CLASSFILE_SYMBOLTABLE_HPP

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "oops/symbol.hpp"
#include "utilities/concurrentHashTable.hpp"
#include "utilities/hashtable.hpp"

class JavaThread;

// TempNewSymbol acts as a handle class in a handle/body idiom and is
// responsible for proper resource management of the body (which is a Symbol*).
// The body is resource managed by a reference counting scheme.
// TempNewSymbol can therefore be used to properly hold a newly created or referenced
// Symbol* temporarily in scope.
//
// Routines in SymbolTable will initialize the reference count of a Symbol* before
// it becomes "managed" by TempNewSymbol instances. As a handle class, TempNewSymbol
// needs to maintain proper reference counting in context of copy semantics.
//
// In SymbolTable, new_symbol() and lookup() will create a Symbol* if not already in the
// symbol table and add to the symbol's reference count.
// probe() and lookup_only() will increment the refcount if symbol is found.
class TempNewSymbol : public StackObj {
  Symbol* _temp;

public:
  TempNewSymbol() : _temp(NULL) {}

  // Conversion from a Symbol* to a TempNewSymbol.
  // Does not increment the current reference count.
  TempNewSymbol(Symbol *s) : _temp(s) {}

  // Copy constructor increments reference count.
  TempNewSymbol(const TempNewSymbol& rhs) : _temp(rhs._temp) {
    if (_temp != NULL) {
      _temp->increment_refcount();
    }
  }

  // Assignment operator uses a c++ trick called copy and swap idiom.
  // rhs is passed by value so within the scope of this method it is a copy.
  // At method exit it contains the former value of _temp, triggering the correct refcount
  // decrement upon destruction.
  void operator=(TempNewSymbol rhs) {
    Symbol* tmp = rhs._temp;
    rhs._temp = _temp;
    _temp = tmp;
  }

  // Decrement reference counter so it can go away if it's unused
  ~TempNewSymbol() {
    if (_temp != NULL) {
      _temp->decrement_refcount();
    }
  }

  // Symbol* conversion operators
  Symbol* operator -> () const                   { return _temp; }
  bool    operator == (Symbol* o) const          { return _temp == o; }
  operator Symbol*()                             { return _temp; }
};

class CompactHashtableWriter;
class SerializeClosure;

class SymbolTableConfig;
typedef ConcurrentHashTable<Symbol*,
                              SymbolTableConfig, mtSymbol> SymbolTableHash;

class SymbolTableCreateEntry;

class SymbolTable : public CHeapObj<mtSymbol> {
  friend class VMStructs;
  friend class Symbol;
  friend class ClassFileParser;
  friend class SymbolTableConfig;
  friend class SymbolTableCreateEntry;

private:
  static void delete_symbol(Symbol* sym);
  void grow(JavaThread* jt);
  void clean_dead_entries(JavaThread* jt);

  // The symbol table
  static SymbolTable* _the_table;
  static volatile bool _lookup_shared_first;
  static volatile bool _alt_hash;

  // For statistics
  volatile size_t _symbols_removed;
  volatile size_t _symbols_counted;

  SymbolTableHash* _local_table;
  size_t _current_size;
  volatile bool _has_work;
  // Set if one bucket is out of balance due to hash algorithm deficiency
  volatile bool _needs_rehashing;

  volatile size_t _items_count;
  volatile bool   _has_items_to_clean;

  double get_load_factor() const;

  void check_concurrent_work();

  static void item_added();
  static void item_removed();

  // For cleaning
  void reset_has_items_to_clean();
  void mark_has_items_to_clean();
  bool has_items_to_clean() const;

  SymbolTable();

  Symbol* allocate_symbol(const char* name, int len, bool c_heap, TRAPS); // Assumes no characters larger than 0x7F
  Symbol* do_lookup(const char* name, int len, uintx hash);
  Symbol* do_add_if_needed(const char* name, int len, uintx hash, bool heap, TRAPS);

  // Adding elements
  static void new_symbols(ClassLoaderData* loader_data,
                          const constantPoolHandle& cp, int names_count,
                          const char** name, int* lengths,
                          int* cp_indices, unsigned int* hashValues,
                          TRAPS);

  static Symbol* lookup_shared(const char* name, int len, unsigned int hash);
  Symbol* lookup_dynamic(const char* name, int len, unsigned int hash);
  Symbol* lookup_common(const char* name, int len, unsigned int hash);

  // Arena for permanent symbols (null class loader) that are never unloaded
  static Arena*  _arena;
  static Arena* arena() { return _arena; }  // called for statistics

  static void initialize_symbols(int arena_alloc_size = 0);

  void concurrent_work(JavaThread* jt);
  void print_table_statistics(outputStream* st, const char* table_name);

  void try_rehash_table();
  bool do_rehash();
  inline void update_needs_rehash(bool rehash);

public:
  // The symbol table
  static SymbolTable* the_table() { return _the_table; }
  size_t table_size();

  enum {
    symbol_alloc_batch_size = 8,
    // Pick initial size based on java -version size measurements
    symbol_alloc_arena_size = 360*K // TODO (revisit)
  };

  static void create_table() {
    assert(_the_table == NULL, "One symbol table allowed.");
    _the_table = new SymbolTable();
    initialize_symbols(symbol_alloc_arena_size);
  }

  static void do_concurrent_work(JavaThread* jt);
  static bool has_work() { return the_table()->_has_work; }
  static void trigger_cleanup();

  // Probing
  static Symbol* lookup(const char* name, int len, TRAPS);
  // lookup only, won't add. Also calculate hash.
  static Symbol* lookup_only(const char* name, int len, unsigned int& hash);
  // adds new symbol if not found
  static Symbol* lookup(const Symbol* sym, int begin, int end, TRAPS);
  // jchar (UTF16) version of lookups
  static Symbol* lookup_unicode(const jchar* name, int len, TRAPS);
  static Symbol* lookup_only_unicode(const jchar* name, int len, unsigned int& hash);
  // Needed for preloading classes in signatures when compiling.
  // Returns the symbol is already present in symbol table, otherwise
  // NULL.  NO ALLOCATION IS GUARANTEED!
  static Symbol* probe(const char* name, int len) {
    unsigned int ignore_hash;
    return lookup_only(name, len, ignore_hash);
  }
  static Symbol* probe_unicode(const jchar* name, int len) {
    unsigned int ignore_hash;
    return lookup_only_unicode(name, len, ignore_hash);
  }

  // Symbol creation
  static Symbol* new_symbol(const char* utf8_buffer, int length, TRAPS) {
    assert(utf8_buffer != NULL, "just checking");
    return lookup(utf8_buffer, length, THREAD);
  }
  static Symbol* new_symbol(const char* name, TRAPS) {
    return new_symbol(name, (int)strlen(name), THREAD);
  }
  static Symbol* new_symbol(const Symbol* sym, int begin, int end, TRAPS) {
    assert(begin <= end && end <= sym->utf8_length(), "just checking");
    return lookup(sym, begin, end, THREAD);
  }
  // Create a symbol in the arena for symbols that are not deleted
  static Symbol* new_permanent_symbol(const char* name, TRAPS);

  // Rehash the string table if it gets out of balance
  static void rehash_table();
  static bool needs_rehashing()
    { return SymbolTable::the_table()->_needs_rehashing; }

  // Heap dumper and CDS
  static void symbols_do(SymbolClosure *cl);

  // Sharing
private:
  static void copy_shared_symbol_table(CompactHashtableWriter* ch_table);
public:
  static void write_to_archive() NOT_CDS_RETURN;
  static void serialize_shared_table_header(SerializeClosure* soc) NOT_CDS_RETURN;
  static void metaspace_pointers_do(MetaspaceClosure* it);

  // Jcmd
  static void dump(outputStream* st, bool verbose=false);
  // Debugging
  static void verify();
  static void read(const char* filename, TRAPS);

  // Histogram
  static void print_histogram() PRODUCT_RETURN;
};

#endif // SHARE_CLASSFILE_SYMBOLTABLE_HPP
