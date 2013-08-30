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


#include "precompiled.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/classLoaderData.hpp"
#include "oops/symbol.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/os.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"

Symbol::Symbol(const u1* name, int length, int refcount) {
  _refcount = refcount;
  _length = length;
  _identity_hash = os::random();
  for (int i = 0; i < _length; i++) {
    byte_at_put(i, name[i]);
  }
}

void* Symbol::operator new(size_t sz, int len, TRAPS) throw() {
  int alloc_size = size(len)*HeapWordSize;
  address res = (address) AllocateHeap(alloc_size, mtSymbol);
  return res;
}

void* Symbol::operator new(size_t sz, int len, Arena* arena, TRAPS) throw() {
  int alloc_size = size(len)*HeapWordSize;
  address res = (address)arena->Amalloc(alloc_size);
  return res;
}

void* Symbol::operator new(size_t sz, int len, ClassLoaderData* loader_data, TRAPS) throw() {
  address res;
  int alloc_size = size(len)*HeapWordSize;
  res = (address) Metaspace::allocate(loader_data, size(len), true,
                                      MetaspaceObj::SymbolType, CHECK_NULL);
  return res;
}

void Symbol::operator delete(void *p) {
  assert(((Symbol*)p)->refcount() == 0, "should not call this");
  FreeHeap(p);
}

// ------------------------------------------------------------------
// Symbol::equals
//
// Compares the symbol with a string of the given length.
bool Symbol::equals(const char* str, int len) const {
  int l = utf8_length();
  if (l != len) return false;
  while (l-- > 0) {
    if (str[l] != (char) byte_at(l))
      return false;
  }
  assert(l == -1, "we should be at the beginning");
  return true;
}


// ------------------------------------------------------------------
// Symbol::starts_with
//
// Tests if the symbol starts with the specified prefix of the given
// length.
bool Symbol::starts_with(const char* prefix, int len) const {
  if (len > utf8_length()) return false;
  while (len-- > 0) {
    if (prefix[len] != (char) byte_at(len))
      return false;
  }
  assert(len == -1, "we should be at the beginning");
  return true;
}


// ------------------------------------------------------------------
// Symbol::index_of
//
// Finds if the given string is a substring of this symbol's utf8 bytes.
// Return -1 on failure.  Otherwise return the first index where str occurs.
int Symbol::index_of_at(int i, const char* str, int len) const {
  assert(i >= 0 && i <= utf8_length(), "oob");
  if (len <= 0)  return 0;
  char first_char = str[0];
  address bytes = (address) ((Symbol*)this)->base();
  address limit = bytes + utf8_length() - len;  // inclusive limit
  address scan = bytes + i;
  if (scan > limit)
    return -1;
  for (; scan <= limit; scan++) {
    scan = (address) memchr(scan, first_char, (limit + 1 - scan));
    if (scan == NULL)
      return -1;  // not found
    assert(scan >= bytes+i && scan <= limit, "scan oob");
    if (memcmp(scan, str, len) == 0)
      return (int)(scan - bytes);
  }
  return -1;
}


char* Symbol::as_C_string(char* buf, int size) const {
  if (size > 0) {
    int len = MIN2(size - 1, utf8_length());
    for (int i = 0; i < len; i++) {
      buf[i] = byte_at(i);
    }
    buf[len] = '\0';
  }
  return buf;
}

char* Symbol::as_C_string() const {
  int len = utf8_length();
  char* str = NEW_RESOURCE_ARRAY(char, len + 1);
  return as_C_string(str, len + 1);
}

char* Symbol::as_C_string_flexible_buffer(Thread* t,
                                                 char* buf, int size) const {
  char* str;
  int len = utf8_length();
  int buf_len = len + 1;
  if (size < buf_len) {
    str = NEW_RESOURCE_ARRAY(char, buf_len);
  } else {
    str = buf;
  }
  return as_C_string(str, buf_len);
}

void Symbol::print_symbol_on(outputStream* st) const {
  ResourceMark rm;
  st = st ? st : tty;
  st->print("%s", as_quoted_ascii());
}

char* Symbol::as_quoted_ascii() const {
  const char *ptr = (const char *)&_body[0];
  int quoted_length = UTF8::quoted_ascii_length(ptr, utf8_length());
  char* result = NEW_RESOURCE_ARRAY(char, quoted_length + 1);
  UTF8::as_quoted_ascii(ptr, utf8_length(), result, quoted_length + 1);
  return result;
}

jchar* Symbol::as_unicode(int& length) const {
  Symbol* this_ptr = (Symbol*)this;
  length = UTF8::unicode_length((char*)this_ptr->bytes(), utf8_length());
  jchar* result = NEW_RESOURCE_ARRAY(jchar, length);
  if (length > 0) {
    UTF8::convert_to_unicode((char*)this_ptr->bytes(), result, length);
  }
  return result;
}

const char* Symbol::as_klass_external_name(char* buf, int size) const {
  if (size > 0) {
    char* str    = as_C_string(buf, size);
    int   length = (int)strlen(str);
    // Turn all '/'s into '.'s (also for array klasses)
    for (int index = 0; index < length; index++) {
      if (str[index] == '/') {
        str[index] = '.';
      }
    }
    return str;
  } else {
    return buf;
  }
}

const char* Symbol::as_klass_external_name() const {
  char* str    = as_C_string();
  int   length = (int)strlen(str);
  // Turn all '/'s into '.'s (also for array klasses)
  for (int index = 0; index < length; index++) {
    if (str[index] == '/') {
      str[index] = '.';
    }
  }
  return str;
}

// Alternate hashing for unbalanced symbol tables.
unsigned int Symbol::new_hash(jint seed) {
  ResourceMark rm;
  // Use alternate hashing algorithm on this symbol.
  return AltHashing::murmur3_32(seed, (const jbyte*)as_C_string(), utf8_length());
}

void Symbol::increment_refcount() {
  // Only increment the refcount if positive.  If negative either
  // overflow has occurred or it is a permanent symbol in a read only
  // shared archive.
  if (_refcount >= 0) {
    Atomic::inc(&_refcount);
    NOT_PRODUCT(Atomic::inc(&_total_count);)
  }
}

void Symbol::decrement_refcount() {
  if (_refcount >= 0) {
    Atomic::dec(&_refcount);
#ifdef ASSERT
    if (_refcount < 0) {
      print();
      assert(false, "reference count underflow for symbol");
    }
#endif
  }
}

void Symbol::print_on(outputStream* st) const {
  if (this == NULL) {
    st->print_cr("NULL");
  } else {
    st->print("Symbol: '");
    print_symbol_on(st);
    st->print("'");
    st->print(" count %d", refcount());
  }
}

// The print_value functions are present in all builds, to support the
// disassembler and error reporting.
void Symbol::print_value_on(outputStream* st) const {
  if (this == NULL) {
    st->print("NULL");
  } else {
    st->print("'");
    for (int i = 0; i < utf8_length(); i++) {
      st->print("%c", byte_at(i));
    }
    st->print("'");
  }
}

// SymbolTable prints this in its statistics
NOT_PRODUCT(int Symbol::_total_count = 0;)
