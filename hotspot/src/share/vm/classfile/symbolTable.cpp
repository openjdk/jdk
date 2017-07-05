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

# include "incls/_precompiled.incl"
# include "incls/_symbolTable.cpp.incl"

// --------------------------------------------------------------------------

SymbolTable* SymbolTable::_the_table = NULL;

// Lookup a symbol in a bucket.

symbolOop SymbolTable::lookup(int index, const char* name,
                              int len, unsigned int hash) {
  for (HashtableEntry* e = bucket(index); e != NULL; e = e->next()) {
    if (e->hash() == hash) {
      symbolOop sym = symbolOop(e->literal());
      if (sym->equals(name, len)) {
        return sym;
      }
    }
  }
  return NULL;
}


// We take care not to be blocking while holding the
// SymbolTable_lock. Otherwise, the system might deadlock, since the
// symboltable is used during compilation (VM_thread) The lock free
// synchronization is simplified by the fact that we do not delete
// entries in the symbol table during normal execution (only during
// safepoints).

symbolOop SymbolTable::lookup(const char* name, int len, TRAPS) {
  unsigned int hashValue = hash_symbol(name, len);
  int index = the_table()->hash_to_index(hashValue);

  symbolOop s = the_table()->lookup(index, name, len, hashValue);

  // Found
  if (s != NULL) return s;

  // Otherwise, add to symbol to table
  return the_table()->basic_add(index, (u1*)name, len, hashValue, CHECK_NULL);
}

symbolOop SymbolTable::lookup(symbolHandle sym, int begin, int end, TRAPS) {
  char* buffer;
  int index, len;
  unsigned int hashValue;
  char* name;
  {
    debug_only(No_Safepoint_Verifier nsv;)

    name = (char*)sym->base() + begin;
    len = end - begin;
    hashValue = hash_symbol(name, len);
    index = the_table()->hash_to_index(hashValue);
    symbolOop s = the_table()->lookup(index, name, len, hashValue);

    // Found
    if (s != NULL) return s;
  }

  // Otherwise, add to symbol to table. Copy to a C string first.
  char stack_buf[128];
  ResourceMark rm(THREAD);
  if (len <= 128) {
    buffer = stack_buf;
  } else {
    buffer = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, len);
  }
  for (int i=0; i<len; i++) {
    buffer[i] = name[i];
  }
  // Make sure there is no safepoint in the code above since name can't move.
  // We can't include the code in No_Safepoint_Verifier because of the
  // ResourceMark.

  return the_table()->basic_add(index, (u1*)buffer, len, hashValue, CHECK_NULL);
}

symbolOop SymbolTable::lookup_only(const char* name, int len,
                                   unsigned int& hash) {
  hash = hash_symbol(name, len);
  int index = the_table()->hash_to_index(hash);

  return the_table()->lookup(index, name, len, hash);
}

// Suggestion: Push unicode-based lookup all the way into the hashing
// and probing logic, so there is no need for convert_to_utf8 until
// an actual new symbolOop is created.
symbolOop SymbolTable::lookup_unicode(const jchar* name, int utf16_length, TRAPS) {
  int utf8_length = UNICODE::utf8_length((jchar*) name, utf16_length);
  char stack_buf[128];
  if (utf8_length < (int) sizeof(stack_buf)) {
    char* chars = stack_buf;
    UNICODE::convert_to_utf8(name, utf16_length, chars);
    return lookup(chars, utf8_length, THREAD);
  } else {
    ResourceMark rm(THREAD);
    char* chars = NEW_RESOURCE_ARRAY(char, utf8_length + 1);;
    UNICODE::convert_to_utf8(name, utf16_length, chars);
    return lookup(chars, utf8_length, THREAD);
  }
}

symbolOop SymbolTable::lookup_only_unicode(const jchar* name, int utf16_length,
                                           unsigned int& hash) {
  int utf8_length = UNICODE::utf8_length((jchar*) name, utf16_length);
  char stack_buf[128];
  if (utf8_length < (int) sizeof(stack_buf)) {
    char* chars = stack_buf;
    UNICODE::convert_to_utf8(name, utf16_length, chars);
    return lookup_only(chars, utf8_length, hash);
  } else {
    ResourceMark rm;
    char* chars = NEW_RESOURCE_ARRAY(char, utf8_length + 1);;
    UNICODE::convert_to_utf8(name, utf16_length, chars);
    return lookup_only(chars, utf8_length, hash);
  }
}

void SymbolTable::add(constantPoolHandle cp, int names_count,
                      const char** names, int* lengths, int* cp_indices,
                      unsigned int* hashValues, TRAPS) {
  SymbolTable* table = the_table();
  bool added = table->basic_add(cp, names_count, names, lengths,
                                cp_indices, hashValues, CHECK);
  if (!added) {
    // do it the hard way
    for (int i=0; i<names_count; i++) {
      int index = table->hash_to_index(hashValues[i]);
      symbolOop sym = table->basic_add(index, (u1*)names[i], lengths[i],
                                       hashValues[i], CHECK);
      cp->symbol_at_put(cp_indices[i], sym);
    }
  }
}

symbolOop SymbolTable::basic_add(int index, u1 *name, int len,
                                 unsigned int hashValue, TRAPS) {
  assert(!Universe::heap()->is_in_reserved(name) || GC_locker::is_active(),
         "proposed name of symbol must be stable");

  // We assume that lookup() has been called already, that it failed,
  // and symbol was not found.  We create the symbol here.
  symbolKlass* sk  = (symbolKlass*) Universe::symbolKlassObj()->klass_part();
  symbolOop s_oop = sk->allocate_symbol(name, len, CHECK_NULL);
  symbolHandle sym (THREAD, s_oop);

  // Allocation must be done before grapping the SymbolTable_lock lock
  MutexLocker ml(SymbolTable_lock, THREAD);

  assert(sym->equals((char*)name, len), "symbol must be properly initialized");

  // Since look-up was done lock-free, we need to check if another
  // thread beat us in the race to insert the symbol.

  symbolOop test = lookup(index, (char*)name, len, hashValue);
  if (test != NULL) {
    // A race occurred and another thread introduced the symbol, this one
    // will be dropped and collected.
    return test;
  }

  HashtableEntry* entry = new_entry(hashValue, sym());
  add_entry(index, entry);
  return sym();
}

bool SymbolTable::basic_add(constantPoolHandle cp, int names_count,
                            const char** names, int* lengths,
                            int* cp_indices, unsigned int* hashValues,
                            TRAPS) {
  symbolKlass* sk  = (symbolKlass*) Universe::symbolKlassObj()->klass_part();
  symbolOop sym_oops[symbol_alloc_batch_size];
  bool allocated = sk->allocate_symbols(names_count, names, lengths,
                                        sym_oops, CHECK_false);
  if (!allocated) {
    return false;
  }
  symbolHandle syms[symbol_alloc_batch_size];
  int i;
  for (i=0; i<names_count; i++) {
    syms[i] = symbolHandle(THREAD, sym_oops[i]);
  }

  // Allocation must be done before grabbing the SymbolTable_lock lock
  MutexLocker ml(SymbolTable_lock, THREAD);

  for (i=0; i<names_count; i++) {
    assert(syms[i]->equals(names[i], lengths[i]), "symbol must be properly initialized");
    // Since look-up was done lock-free, we need to check if another
    // thread beat us in the race to insert the symbol.
    int index = hash_to_index(hashValues[i]);
    symbolOop test = lookup(index, names[i], lengths[i], hashValues[i]);
    if (test != NULL) {
      // A race occurred and another thread introduced the symbol, this one
      // will be dropped and collected. Use test instead.
      cp->symbol_at_put(cp_indices[i], test);
    } else {
      symbolOop sym = syms[i]();
      HashtableEntry* entry = new_entry(hashValues[i], sym);
      add_entry(index, entry);
      cp->symbol_at_put(cp_indices[i], sym);
    }
  }

  return true;
}


void SymbolTable::verify() {
  for (int i = 0; i < the_table()->table_size(); ++i) {
    HashtableEntry* p = the_table()->bucket(i);
    for ( ; p != NULL; p = p->next()) {
      symbolOop s = symbolOop(p->literal());
      guarantee(s != NULL, "symbol is NULL");
      s->verify();
      guarantee(s->is_perm(), "symbol not in permspace");
      unsigned int h = hash_symbol((char*)s->bytes(), s->utf8_length());
      guarantee(p->hash() == h, "broken hash in symbol table entry");
      guarantee(the_table()->hash_to_index(h) == i,
                "wrong index in symbol table");
    }
  }
}


//---------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT

void SymbolTable::print_histogram() {
  MutexLocker ml(SymbolTable_lock);
  const int results_length = 100;
  int results[results_length];
  int i,j;

  // initialize results to zero
  for (j = 0; j < results_length; j++) {
    results[j] = 0;
  }

  int total = 0;
  int max_symbols = 0;
  int out_of_range = 0;
  for (i = 0; i < the_table()->table_size(); i++) {
    HashtableEntry* p = the_table()->bucket(i);
    for ( ; p != NULL; p = p->next()) {
      int counter = symbolOop(p->literal())->utf8_length();
      total += counter;
      if (counter < results_length) {
        results[counter]++;
      } else {
        out_of_range++;
      }
      max_symbols = MAX2(max_symbols, counter);
    }
  }
  tty->print_cr("Symbol Table:");
  tty->print_cr("%8s %5d", "Total  ", total);
  tty->print_cr("%8s %5d", "Maximum", max_symbols);
  tty->print_cr("%8s %3.2f", "Average",
          ((float) total / (float) the_table()->table_size()));
  tty->print_cr("%s", "Histogram:");
  tty->print_cr(" %s %29s", "Length", "Number chains that length");
  for (i = 0; i < results_length; i++) {
    if (results[i] > 0) {
      tty->print_cr("%6d %10d", i, results[i]);
    }
  }
  int line_length = 70;
  tty->print_cr("%s %30s", " Length", "Number chains that length");
  for (i = 0; i < results_length; i++) {
    if (results[i] > 0) {
      tty->print("%4d", i);
      for (j = 0; (j < results[i]) && (j < line_length);  j++) {
        tty->print("%1s", "*");
      }
      if (j == line_length) {
        tty->print("%1s", "+");
      }
      tty->cr();
    }
  }
  tty->print_cr(" %s %d: %d\n", "Number chains longer than",
                    results_length, out_of_range);
}

#endif // PRODUCT

// --------------------------------------------------------------------------

#ifdef ASSERT
class StableMemoryChecker : public StackObj {
  enum { _bufsize = wordSize*4 };

  address _region;
  jint    _size;
  u1      _save_buf[_bufsize];

  int sample(u1* save_buf) {
    if (_size <= _bufsize) {
      memcpy(save_buf, _region, _size);
      return _size;
    } else {
      // copy head and tail
      memcpy(&save_buf[0],          _region,                      _bufsize/2);
      memcpy(&save_buf[_bufsize/2], _region + _size - _bufsize/2, _bufsize/2);
      return (_bufsize/2)*2;
    }
  }

 public:
  StableMemoryChecker(const void* region, jint size) {
    _region = (address) region;
    _size   = size;
    sample(_save_buf);
  }

  bool verify() {
    u1 check_buf[sizeof(_save_buf)];
    int check_size = sample(check_buf);
    return (0 == memcmp(_save_buf, check_buf, check_size));
  }

  void set_region(const void* region) { _region = (address) region; }
};
#endif


// --------------------------------------------------------------------------


// Compute the hash value for a java.lang.String object which would
// contain the characters passed in. This hash value is used for at
// least two purposes.
//
// (a) As the hash value used by the StringTable for bucket selection
//     and comparison (stored in the HashtableEntry structures).  This
//     is used in the String.intern() method.
//
// (b) As the hash value used by the String object itself, in
//     String.hashCode().  This value is normally calculate in Java code
//     in the String.hashCode method(), but is precomputed for String
//     objects in the shared archive file.
//
//     For this reason, THIS ALGORITHM MUST MATCH String.hashCode().

int StringTable::hash_string(jchar* s, int len) {
  unsigned h = 0;
  while (len-- > 0) {
    h = 31*h + (unsigned) *s;
    s++;
  }
  return h;
}


StringTable* StringTable::_the_table = NULL;

oop StringTable::lookup(int index, jchar* name,
                        int len, unsigned int hash) {
  for (HashtableEntry* l = bucket(index); l != NULL; l = l->next()) {
    if (l->hash() == hash) {
      if (java_lang_String::equals(l->literal(), name, len)) {
        return l->literal();
      }
    }
  }
  return NULL;
}


oop StringTable::basic_add(int index, Handle string_or_null, jchar* name,
                           int len, unsigned int hashValue, TRAPS) {
  debug_only(StableMemoryChecker smc(name, len * sizeof(name[0])));
  assert(!Universe::heap()->is_in_reserved(name) || GC_locker::is_active(),
         "proposed name of symbol must be stable");

  Handle string;
  // try to reuse the string if possible
  if (!string_or_null.is_null() && string_or_null()->is_perm()) {
    string = string_or_null;
  } else {
    string = java_lang_String::create_tenured_from_unicode(name, len, CHECK_NULL);
  }

  // Allocation must be done before grapping the SymbolTable_lock lock
  MutexLocker ml(StringTable_lock, THREAD);

  assert(java_lang_String::equals(string(), name, len),
         "string must be properly initialized");

  // Since look-up was done lock-free, we need to check if another
  // thread beat us in the race to insert the symbol.

  oop test = lookup(index, name, len, hashValue); // calls lookup(u1*, int)
  if (test != NULL) {
    // Entry already added
    return test;
  }

  HashtableEntry* entry = new_entry(hashValue, string());
  add_entry(index, entry);
  return string();
}


oop StringTable::lookup(symbolOop symbol) {
  ResourceMark rm;
  int length;
  jchar* chars = symbol->as_unicode(length);
  unsigned int hashValue = hash_string(chars, length);
  int index = the_table()->hash_to_index(hashValue);
  return the_table()->lookup(index, chars, length, hashValue);
}


oop StringTable::intern(Handle string_or_null, jchar* name,
                        int len, TRAPS) {
  unsigned int hashValue = hash_string(name, len);
  int index = the_table()->hash_to_index(hashValue);
  oop string = the_table()->lookup(index, name, len, hashValue);

  // Found
  if (string != NULL) return string;

  // Otherwise, add to symbol to table
  return the_table()->basic_add(index, string_or_null, name, len,
                                hashValue, CHECK_NULL);
}

oop StringTable::intern(symbolOop symbol, TRAPS) {
  if (symbol == NULL) return NULL;
  ResourceMark rm(THREAD);
  int length;
  jchar* chars = symbol->as_unicode(length);
  Handle string;
  oop result = intern(string, chars, length, CHECK_NULL);
  return result;
}


oop StringTable::intern(oop string, TRAPS)
{
  if (string == NULL) return NULL;
  ResourceMark rm(THREAD);
  int length;
  Handle h_string (THREAD, string);
  jchar* chars = java_lang_String::as_unicode_string(string, length);
  oop result = intern(h_string, chars, length, CHECK_NULL);
  return result;
}


oop StringTable::intern(const char* utf8_string, TRAPS) {
  if (utf8_string == NULL) return NULL;
  ResourceMark rm(THREAD);
  int length = UTF8::unicode_length(utf8_string);
  jchar* chars = NEW_RESOURCE_ARRAY(jchar, length);
  UTF8::convert_to_unicode(utf8_string, chars, length);
  Handle string;
  oop result = intern(string, chars, length, CHECK_NULL);
  return result;
}

void StringTable::verify() {
  for (int i = 0; i < the_table()->table_size(); ++i) {
    HashtableEntry* p = the_table()->bucket(i);
    for ( ; p != NULL; p = p->next()) {
      oop s = p->literal();
      guarantee(s != NULL, "interned string is NULL");
      guarantee(s->is_perm(), "interned string not in permspace");

      int length;
      jchar* chars = java_lang_String::as_unicode_string(s, length);
      unsigned int h = hash_string(chars, length);
      guarantee(p->hash() == h, "broken hash in string table entry");
      guarantee(the_table()->hash_to_index(h) == i,
                "wrong index in string table");
    }
  }
}
