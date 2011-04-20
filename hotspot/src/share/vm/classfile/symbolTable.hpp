/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
    _temp = s._temp;
    if (_temp !=NULL) _temp->increment_refcount();
  }

  // Decrement reference counter so it can go away if it's unique
  ~TempNewSymbol() { if (_temp != NULL) _temp->decrement_refcount(); }

  // Operators so they can be used like Symbols
  Symbol* operator -> () const                   { return _temp; }
  bool    operator == (Symbol* o) const          { return _temp == o; }
  // Sneaky conversion function
  operator Symbol*()                             { return _temp; }
};

class SymbolTable : public Hashtable<Symbol*> {
  friend class VMStructs;
  friend class ClassFileParser;

private:
  // The symbol table
  static SymbolTable* _the_table;

  // For statistics
  static int symbols_removed;
  static int symbols_counted;

  Symbol* allocate_symbol(const u1* name, int len, TRAPS);   // Assumes no characters larger than 0x7F
  bool allocate_symbols(int names_count, const u1** names, int* lengths, Symbol** syms, TRAPS);

  // Adding elements
  Symbol* basic_add(int index, u1* name, int len,
                      unsigned int hashValue, TRAPS);
  bool basic_add(constantPoolHandle cp, int names_count,
                 const char** names, int* lengths, int* cp_indices,
                 unsigned int* hashValues, TRAPS);

  static void new_symbols(constantPoolHandle cp, int names_count,
                          const char** name, int* lengths,
                          int* cp_indices, unsigned int* hashValues,
                          TRAPS) {
    add(cp, names_count, name, lengths, cp_indices, hashValues, THREAD);
  }


  // Table size
  enum {
    symbol_table_size = 20011
  };

  Symbol* lookup(int index, const char* name, int len, unsigned int hash);

  SymbolTable()
    : Hashtable<Symbol*>(symbol_table_size, sizeof (HashtableEntry<Symbol*>)) {}

  SymbolTable(HashtableBucket* t, int number_of_entries)
    : Hashtable<Symbol*>(symbol_table_size, sizeof (HashtableEntry<Symbol*>), t,
                number_of_entries) {}


public:
  enum {
    symbol_alloc_batch_size = 8
  };

  // The symbol table
  static SymbolTable* the_table() { return _the_table; }

  static void create_table() {
    assert(_the_table == NULL, "One symbol table allowed.");
    _the_table = new SymbolTable();
  }

  static void create_table(HashtableBucket* t, int length,
                           int number_of_entries) {
    assert(_the_table == NULL, "One symbol table allowed.");
    assert(length == symbol_table_size * sizeof(HashtableBucket),
           "bad shared symbol size.");
    _the_table = new SymbolTable(t, number_of_entries);
  }

  static Symbol* lookup(const char* name, int len, TRAPS);
  // lookup only, won't add. Also calculate hash.
  static Symbol* lookup_only(const char* name, int len, unsigned int& hash);
  // Only copy to C string to be added if lookup failed.
  static Symbol* lookup(const Symbol* sym, int begin, int end, TRAPS);

  static void release(Symbol* sym);

  // jchar (utf16) version of lookups
  static Symbol* lookup_unicode(const jchar* name, int len, TRAPS);
  static Symbol* lookup_only_unicode(const jchar* name, int len, unsigned int& hash);

  static void add(constantPoolHandle cp, int names_count,
                  const char** names, int* lengths, int* cp_indices,
                  unsigned int* hashValues, TRAPS);

  // Release any dead symbols
  static void unlink();

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

  // Sharing
  static void copy_buckets(char** top, char*end) {
    the_table()->Hashtable<Symbol*>::copy_buckets(top, end);
  }
  static void copy_table(char** top, char*end) {
    the_table()->Hashtable<Symbol*>::copy_table(top, end);
  }
  static void reverse(void* boundary = NULL) {
    the_table()->Hashtable<Symbol*>::reverse(boundary);
  }
};

class StringTable : public Hashtable<oop> {
  friend class VMStructs;

private:
  // The string table
  static StringTable* _the_table;

  static oop intern(Handle string_or_null, jchar* chars, int length, TRAPS);
  oop basic_add(int index, Handle string_or_null, jchar* name, int len,
                unsigned int hashValue, TRAPS);

  oop lookup(int index, jchar* chars, int length, unsigned int hashValue);

  StringTable() : Hashtable<oop>((int)StringTableSize,
                                 sizeof (HashtableEntry<oop>)) {}

  StringTable(HashtableBucket* t, int number_of_entries)
    : Hashtable<oop>((int)StringTableSize, sizeof (HashtableEntry<oop>), t,
                     number_of_entries) {}

public:
  // The string table
  static StringTable* the_table() { return _the_table; }

  static void create_table() {
    assert(_the_table == NULL, "One string table allowed.");
    _the_table = new StringTable();
  }

  static void create_table(HashtableBucket* t, int length,
                           int number_of_entries) {
    assert(_the_table == NULL, "One string table allowed.");
    assert((size_t)length == StringTableSize * sizeof(HashtableBucket),
           "bad shared string size.");
    _the_table = new StringTable(t, number_of_entries);
  }

  // GC support
  //   Delete pointers to otherwise-unreachable objects.
  static void unlink(BoolObjectClosure* cl);

  // Invoke "f->do_oop" on the locations of all oops in the table.
  static void oops_do(OopClosure* f);

  // Probing
  static oop lookup(Symbol* symbol);

  // Interning
  static oop intern(Symbol* symbol, TRAPS);
  static oop intern(oop string, TRAPS);
  static oop intern(const char *utf8_string, TRAPS);

  // Debugging
  static void verify();

  // Sharing
  static void copy_buckets(char** top, char*end) {
    the_table()->Hashtable<oop>::copy_buckets(top, end);
  }
  static void copy_table(char** top, char*end) {
    the_table()->Hashtable<oop>::copy_table(top, end);
  }
  static void reverse() {
    the_table()->Hashtable<oop>::reverse();
  }
};

#endif // SHARE_VM_CLASSFILE_SYMBOLTABLE_HPP
