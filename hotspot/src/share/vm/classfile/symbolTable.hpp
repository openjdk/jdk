/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// The symbol table holds all symbolOops and corresponding interned strings.
// symbolOops and literal strings should be canonicalized.
//
// The interned strings are created lazily.
//
// It is implemented as an open hash table with a fixed number of buckets.
//
// %note:
//  - symbolTableEntrys are allocated in blocks to reduce the space overhead.

class BoolObjectClosure;


class SymbolTable : public Hashtable {
  friend class VMStructs;

private:
  // The symbol table
  static SymbolTable* _the_table;

  // Adding elements
  symbolOop basic_add(int index, u1* name, int len,
                      unsigned int hashValue, TRAPS);
  bool basic_add(constantPoolHandle cp, int names_count,
                 const char** names, int* lengths, int* cp_indices,
                 unsigned int* hashValues, TRAPS);

  // Table size
  enum {
    symbol_table_size = 20011
  };

  symbolOop lookup(int index, const char* name, int len, unsigned int hash);

  SymbolTable()
    : Hashtable(symbol_table_size, sizeof (HashtableEntry)) {}

  SymbolTable(HashtableBucket* t, int number_of_entries)
    : Hashtable(symbol_table_size, sizeof (HashtableEntry), t,
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

  static symbolOop lookup(const char* name, int len, TRAPS);
  // lookup only, won't add. Also calculate hash.
  static symbolOop lookup_only(const char* name, int len, unsigned int& hash);
  // Only copy to C string to be added if lookup failed.
  static symbolOop lookup(symbolHandle sym, int begin, int end, TRAPS);

  // jchar (utf16) version of lookups
  static symbolOop lookup_unicode(const jchar* name, int len, TRAPS);
  static symbolOop lookup_only_unicode(const jchar* name, int len, unsigned int& hash);

  static void add(constantPoolHandle cp, int names_count,
                  const char** names, int* lengths, int* cp_indices,
                  unsigned int* hashValues, TRAPS);

  // GC support
  //   Delete pointers to otherwise-unreachable objects.
  static void unlink(BoolObjectClosure* cl) {
    the_table()->Hashtable::unlink(cl);
  }

  // Invoke "f->do_oop" on the locations of all oops in the table.
  static void oops_do(OopClosure* f) {
    the_table()->Hashtable::oops_do(f);
  }

  // Symbol lookup
  static symbolOop lookup(int index, const char* name, int len, TRAPS);

  // Needed for preloading classes in signatures when compiling.
  // Returns the symbol is already present in symbol table, otherwise
  // NULL.  NO ALLOCATION IS GUARANTEED!
  static symbolOop probe(const char* name, int len) {
    unsigned int ignore_hash;
    return lookup_only(name, len, ignore_hash);
  }
  static symbolOop probe_unicode(const jchar* name, int len) {
    unsigned int ignore_hash;
    return lookup_only_unicode(name, len, ignore_hash);
  }

  // Histogram
  static void print_histogram()     PRODUCT_RETURN;

  // Debugging
  static void verify();

  // Sharing
  static void copy_buckets(char** top, char*end) {
    the_table()->Hashtable::copy_buckets(top, end);
  }
  static void copy_table(char** top, char*end) {
    the_table()->Hashtable::copy_table(top, end);
  }
  static void reverse(void* boundary = NULL) {
    ((Hashtable*)the_table())->reverse(boundary);
  }
};


class StringTable : public Hashtable {
  friend class VMStructs;

private:
  // The string table
  static StringTable* _the_table;

  static oop intern(Handle string_or_null, jchar* chars, int length, TRAPS);
  oop basic_add(int index, Handle string_or_null, jchar* name, int len,
                unsigned int hashValue, TRAPS);

  // Table size
  enum {
    string_table_size = 1009
  };

  oop lookup(int index, jchar* chars, int length, unsigned int hashValue);

  StringTable() : Hashtable(string_table_size, sizeof (HashtableEntry)) {}

  StringTable(HashtableBucket* t, int number_of_entries)
    : Hashtable(string_table_size, sizeof (HashtableEntry), t,
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
    assert(length == string_table_size * sizeof(HashtableBucket),
           "bad shared string size.");
    _the_table = new StringTable(t, number_of_entries);
  }


  static int hash_string(jchar* s, int len);


  // GC support
  //   Delete pointers to otherwise-unreachable objects.
  static void unlink(BoolObjectClosure* cl) {
    the_table()->Hashtable::unlink(cl);
  }

  // Invoke "f->do_oop" on the locations of all oops in the table.
  static void oops_do(OopClosure* f) {
    the_table()->Hashtable::oops_do(f);
  }

  // Probing
  static oop lookup(symbolOop symbol);

  // Interning
  static oop intern(symbolOop symbol, TRAPS);
  static oop intern(oop string, TRAPS);
  static oop intern(const char *utf8_string, TRAPS);

  // Debugging
  static void verify();

  // Sharing
  static void copy_buckets(char** top, char*end) {
    the_table()->Hashtable::copy_buckets(top, end);
  }
  static void copy_table(char** top, char*end) {
    the_table()->Hashtable::copy_table(top, end);
  }
  static void reverse() {
    ((BasicHashtable*)the_table())->reverse();
  }
};
