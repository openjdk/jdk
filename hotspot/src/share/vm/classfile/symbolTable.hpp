/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

class SymbolTable : public Hashtable<Symbol*, mtSymbol> {
  friend class VMStructs;
  friend class ClassFileParser;

private:
  // The symbol table
  static SymbolTable* _the_table;

  // Set if one bucket is out of balance due to hash algorithm deficiency
  static bool _needs_rehashing;

  // For statistics
  static int _symbols_removed;
  static int _symbols_counted;

  Symbol* allocate_symbol(const u1* name, int len, bool c_heap, TRAPS); // Assumes no characters larger than 0x7F

  // Adding elements
  Symbol* basic_add(int index, u1* name, int len, unsigned int hashValue,
                    bool c_heap, TRAPS);
  bool basic_add(ClassLoaderData* loader_data,
                 constantPoolHandle cp, int names_count,
                 const char** names, int* lengths, int* cp_indices,
                 unsigned int* hashValues, TRAPS);

  static void new_symbols(ClassLoaderData* loader_data,
                          constantPoolHandle cp, int names_count,
                          const char** name, int* lengths,
                          int* cp_indices, unsigned int* hashValues,
                          TRAPS) {
    add(loader_data, cp, names_count, name, lengths, cp_indices, hashValues, THREAD);
  }

  Symbol* lookup(int index, const char* name, int len, unsigned int hash);

  SymbolTable()
    : Hashtable<Symbol*, mtSymbol>(SymbolTableSize, sizeof (HashtableEntry<Symbol*, mtSymbol>)) {}

  SymbolTable(HashtableBucket<mtSymbol>* t, int number_of_entries)
    : Hashtable<Symbol*, mtSymbol>(SymbolTableSize, sizeof (HashtableEntry<Symbol*, mtSymbol>), t,
                number_of_entries) {}

  // Arena for permanent symbols (null class loader) that are never unloaded
  static Arena*  _arena;
  static Arena* arena() { return _arena; }  // called for statistics

  static void initialize_symbols(int arena_alloc_size = 0);

  static volatile int _parallel_claimed_idx;

  // Release any dead symbols
  static void buckets_unlink(int start_idx, int end_idx, int* processed, int* removed, size_t* memory_total);
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

  static void create_table(HashtableBucket<mtSymbol>* t, int length,
                           int number_of_entries) {
    assert(_the_table == NULL, "One symbol table allowed.");

    // If CDS archive used a different symbol table size, use that size instead
    // which is better than giving an error.
    SymbolTableSize = length/bucket_size();

    _the_table = new SymbolTable(t, number_of_entries);
    // if CDS give symbol table a default arena size since most symbols
    // are already allocated in the shared misc section.
    initialize_symbols();
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

  // jchar (utf16) version of lookups
  static Symbol* lookup_unicode(const jchar* name, int len, TRAPS);
  static Symbol* lookup_only_unicode(const jchar* name, int len, unsigned int& hash);

  static void add(ClassLoaderData* loader_data,
                  constantPoolHandle cp, int names_count,
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
  static void dump(outputStream* st);

  // Sharing
  static void copy_buckets(char** top, char*end) {
    the_table()->Hashtable<Symbol*, mtSymbol>::copy_buckets(top, end);
  }
  static void copy_table(char** top, char*end) {
    the_table()->Hashtable<Symbol*, mtSymbol>::copy_table(top, end);
  }
  static void reverse(void* boundary = NULL) {
    the_table()->Hashtable<Symbol*, mtSymbol>::reverse(boundary);
  }

  // Rehash the symbol table if it gets out of balance
  static void rehash_table();
  static bool needs_rehashing()         { return _needs_rehashing; }
  // Parallel chunked scanning
  static void clear_parallel_claimed_index() { _parallel_claimed_idx = 0; }
  static int parallel_claimed_index()        { return _parallel_claimed_idx; }
};

class StringTable : public Hashtable<oop, mtSymbol> {
  friend class VMStructs;

private:
  // The string table
  static StringTable* _the_table;

  // Set if one bucket is out of balance due to hash algorithm deficiency
  static bool _needs_rehashing;

  // Claimed high water mark for parallel chunked scanning
  static volatile int _parallel_claimed_idx;

  static oop intern(Handle string_or_null, jchar* chars, int length, TRAPS);
  oop basic_add(int index, Handle string_or_null, jchar* name, int len,
                unsigned int hashValue, TRAPS);

  oop lookup(int index, jchar* chars, int length, unsigned int hashValue);

  // Apply the give oop closure to the entries to the buckets
  // in the range [start_idx, end_idx).
  static void buckets_oops_do(OopClosure* f, int start_idx, int end_idx);
  // Unlink or apply the give oop closure to the entries to the buckets
  // in the range [start_idx, end_idx).
  static void buckets_unlink_or_oops_do(BoolObjectClosure* is_alive, OopClosure* f, int start_idx, int end_idx, int* processed, int* removed);

  StringTable() : Hashtable<oop, mtSymbol>((int)StringTableSize,
                              sizeof (HashtableEntry<oop, mtSymbol>)) {}

  StringTable(HashtableBucket<mtSymbol>* t, int number_of_entries)
    : Hashtable<oop, mtSymbol>((int)StringTableSize, sizeof (HashtableEntry<oop, mtSymbol>), t,
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
  static unsigned int hash_string(const jchar* s, int len);

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
  static void dump(outputStream* st);

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
  static void copy_buckets(char** top, char*end) {
    the_table()->Hashtable<oop, mtSymbol>::copy_buckets(top, end);
  }
  static void copy_table(char** top, char*end) {
    the_table()->Hashtable<oop, mtSymbol>::copy_table(top, end);
  }
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
#endif // SHARE_VM_CLASSFILE_SYMBOLTABLE_HPP
