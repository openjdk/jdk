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

#ifndef SHARE_VM_CLASSFILE_SYMBOLTABLE_HPP
#define SHARE_VM_CLASSFILE_SYMBOLTABLE_HPP

#include "memory/allocation.inline.hpp"
#include "oops/symbol.hpp"
#include "utilities/hashtable.hpp"

// The symbol table holds all Symbol*s and corresponding interned strings.
// Symbol*s and literal strings should be canonicalized.
//
// The interned strings are created lazily.
//
// It is implemented as an open hash table with a fixed number of buckets.
//
// %note:
//  - symbolTableEntrys are allocated in blocks to reduce the space overhead.

class BoolObjectClosure;
class outputStream;

// Class to hold a newly created or referenced Symbol* temporarily in scope.
// new_symbol() and lookup() will create a Symbol* if not already in the
// symbol table and add to the symbol's reference count.
// probe() and lookup_only() will increment the refcount if symbol is found.
class TempNewSymbol : public StackObj {
  Symbol* _temp;

 public:
  TempNewSymbol() : _temp(NULL) {}
  // Creating or looking up a symbol increments the symbol's reference count
  TempNewSymbol(Symbol *s) : _temp(s) {}

  // Operator= increments reference count.
  void operator=(const TempNewSymbol &s) {
    //clear();  //FIXME
    _temp = s._temp;
    if (_temp !=NULL) _temp->increment_refcount();
  }

  // Decrement reference counter so it can go away if it's unique
  void clear() { if (_temp != NULL)  _temp->decrement_refcount();  _temp = NULL; }

  ~TempNewSymbol() { clear(); }

  // Operators so they can be used like Symbols
  Symbol* operator -> () const                   { return _temp; }
  bool    operator == (Symbol* o) const          { return _temp == o; }
  // Sneaky conversion function
  operator Symbol*()                             { return _temp; }
};

template <class T, class N> class CompactHashtable;

class SymbolTable : public RehashableHashtable<Symbol*, mtSymbol> {
  friend class VMStructs;
  friend class ClassFileParser;

private:
  // The symbol table
  static SymbolTable* _the_table;

  // Set if one bucket is out of balance due to hash algorithm deficiency
  static bool _needs_rehashing;
  static bool _lookup_shared_first;

  // For statistics
  static int _symbols_removed;
  static int _symbols_counted;

  // shared symbol table.
  static CompactHashtable<Symbol*, char> _shared_table;

  Symbol* allocate_symbol(const u1* name, int len, bool c_heap, TRAPS); // Assumes no characters larger than 0x7F

  // Adding elements
  Symbol* basic_add(int index, u1* name, int len, unsigned int hashValue,
                    bool c_heap, TRAPS);
  bool basic_add(ClassLoaderData* loader_data,
                 const constantPoolHandle& cp, int names_count,
                 const char** names, int* lengths, int* cp_indices,
                 unsigned int* hashValues, TRAPS);

  static void new_symbols(ClassLoaderData* loader_data,
                          const constantPoolHandle& cp, int names_count,
                          const char** name, int* lengths,
                          int* cp_indices, unsigned int* hashValues,
                          TRAPS) {
    add(loader_data, cp, names_count, name, lengths, cp_indices, hashValues, THREAD);
  }

  static Symbol* lookup_shared(const char* name, int len, unsigned int hash);
  Symbol* lookup_dynamic(int index, const char* name, int len, unsigned int hash);
  Symbol* lookup(int index, const char* name, int len, unsigned int hash);

  SymbolTable()
    : RehashableHashtable<Symbol*, mtSymbol>(SymbolTableSize, sizeof (HashtableEntry<Symbol*, mtSymbol>)) {}

  SymbolTable(HashtableBucket<mtSymbol>* t, int number_of_entries)
    : RehashableHashtable<Symbol*, mtSymbol>(SymbolTableSize, sizeof (HashtableEntry<Symbol*, mtSymbol>), t,
                number_of_entries) {}

  // Arena for permanent symbols (null class loader) that are never unloaded
  static Arena*  _arena;
  static Arena* arena() { return _arena; }  // called for statistics

  static void initialize_symbols(int arena_alloc_size = 0);

  static volatile int _parallel_claimed_idx;

  // Release any dead symbols
  static void buckets_unlink(int start_idx, int end_idx, int* processed, int* removed);
public:
  enum {
    symbol_alloc_batch_size = 8,
    // Pick initial size based on java -version size measurements
    symbol_alloc_arena_size = 360*K
  };

  // The symbol table
  static SymbolTable* the_table() { return _the_table; }

  // Size of one bucket in the string table.  Used when checking for rollover.
  static uint bucket_size() { return sizeof(HashtableBucket<mtSymbol>); }

  static void create_table() {
    assert(_the_table == NULL, "One symbol table allowed.");
    _the_table = new SymbolTable();
    initialize_symbols(symbol_alloc_arena_size);
  }

  static unsigned int hash_symbol(const char* s, int len);

  static Symbol* lookup(const char* name, int len, TRAPS);
  // lookup only, won't add. Also calculate hash.
  static Symbol* lookup_only(const char* name, int len, unsigned int& hash);
  // Only copy to C string to be added if lookup failed.
  static Symbol* lookup(const Symbol* sym, int begin, int end, TRAPS);

  static void release(Symbol* sym);

  // Look up the address of the literal in the SymbolTable for this Symbol*
  static Symbol** lookup_symbol_addr(Symbol* sym);

  // jchar (UTF16) version of lookups
  static Symbol* lookup_unicode(const jchar* name, int len, TRAPS);
  static Symbol* lookup_only_unicode(const jchar* name, int len, unsigned int& hash);

  static void add(ClassLoaderData* loader_data,
                  const constantPoolHandle& cp, int names_count,
                  const char** names, int* lengths, int* cp_indices,
                  unsigned int* hashValues, TRAPS);

  // Release any dead symbols
  static void unlink() {
    int processed = 0;
    int removed = 0;
    unlink(&processed, &removed);
  }
  static void unlink(int* processed, int* removed);
  // Release any dead symbols, possibly parallel version
  static void possibly_parallel_unlink(int* processed, int* removed);

  // iterate over symbols
  static void symbols_do(SymbolClosure *cl);

  // Symbol creation
  static Symbol* new_symbol(const char* utf8_buffer, int length, TRAPS) {
    assert(utf8_buffer != NULL, "just checking");
    return lookup(utf8_buffer, length, THREAD);
  }
  static Symbol*       new_symbol(const char* name, TRAPS) {
    return new_symbol(name, (int)strlen(name), THREAD);
  }
  static Symbol*       new_symbol(const Symbol* sym, int begin, int end, TRAPS) {
    assert(begin <= end && end <= sym->utf8_length(), "just checking");
    return lookup(sym, begin, end, THREAD);
  }

  // Create a symbol in the arena for symbols that are not deleted
  static Symbol* new_permanent_symbol(const char* name, TRAPS);

  // Symbol lookup
  static Symbol* lookup(int index, const char* name, int len, TRAPS);

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

  // Histogram
  static void print_histogram()     PRODUCT_RETURN;
  static void print()     PRODUCT_RETURN;

  // Debugging
  static void verify();
  static void dump(outputStream* st, bool verbose=false);
  static void read(const char* filename, TRAPS);

  // Sharing
  static bool copy_compact_table(char** top, char* end);
  static const char* init_shared_table(const char* buffer);

  // Rehash the symbol table if it gets out of balance
  static void rehash_table();
  static bool needs_rehashing()         { return _needs_rehashing; }
  // Parallel chunked scanning
  static void clear_parallel_claimed_index() { _parallel_claimed_idx = 0; }
  static int parallel_claimed_index()        { return _parallel_claimed_idx; }
};

#endif // SHARE_VM_CLASSFILE_SYMBOLTABLE_HPP
