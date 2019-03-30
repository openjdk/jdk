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


#include "precompiled.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/classLoaderData.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/symbol.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "utilities/utf8.hpp"

uint32_t Symbol::pack_length_and_refcount(int length, int refcount) {
  STATIC_ASSERT(max_symbol_length == ((1 << 16) - 1));
  STATIC_ASSERT(PERM_REFCOUNT == ((1 << 16) - 1));
  assert(length >= 0, "negative length");
  assert(length <= max_symbol_length, "too long symbol");
  assert(refcount >= 0, "negative refcount");
  assert(refcount <= PERM_REFCOUNT, "invalid refcount");
  uint32_t hi = length;
  uint32_t lo = refcount;
  return (hi << 16) | lo;
}

Symbol::Symbol(const u1* name, int length, int refcount) {
  _length_and_refcount =  pack_length_and_refcount(length, refcount);
  _identity_hash = (short)os::random();
  for (int i = 0; i < length; i++) {
    byte_at_put(i, name[i]);
  }
}

void* Symbol::operator new(size_t sz, int len, TRAPS) throw() {
  int alloc_size = size(len)*wordSize;
  address res = (address) AllocateHeap(alloc_size, mtSymbol);
  return res;
}

void* Symbol::operator new(size_t sz, int len, Arena* arena, TRAPS) throw() {
  int alloc_size = size(len)*wordSize;
  address res = (address)arena->Amalloc_4(alloc_size);
  return res;
}

void Symbol::operator delete(void *p) {
  assert(((Symbol*)p)->refcount() == 0, "should not call this");
  FreeHeap(p);
}

// ------------------------------------------------------------------
// Symbol::starts_with
//
// Tests if the symbol starts with the specified prefix of the given
// length.
bool Symbol::starts_with(const char* prefix, int len) const {
  if (len > utf8_length()) return false;
  while (len-- > 0) {
    if (prefix[len] != char_at(len))
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
      buf[i] = char_at(i);
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

void Symbol::print_utf8_on(outputStream* st) const {
  st->print("%s", as_C_string());
}

void Symbol::print_symbol_on(outputStream* st) const {
  char *s;
  st = st ? st : tty;
  {
    // ResourceMark may not affect st->print(). If st is a string
    // stream it could resize, using the same resource arena.
    ResourceMark rm;
    s = as_quoted_ascii();
    s = os::strdup(s);
  }
  if (s == NULL) {
    st->print("(null)");
  } else {
    st->print("%s", s);
    os::free(s);
  }
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

// Increment refcount while checking for zero.  If the Symbol's refcount becomes zero
// a thread could be concurrently removing the Symbol.  This is used during SymbolTable
// lookup to avoid reviving a dead Symbol.
bool Symbol::try_increment_refcount() {
  uint32_t found = _length_and_refcount;
  while (true) {
    uint32_t old_value = found;
    int refc = extract_refcount(old_value);
    if (refc == PERM_REFCOUNT) {
      return true;  // sticky max or created permanent
    } else if (refc == 0) {
      return false; // dead, can't revive.
    } else {
      found = Atomic::cmpxchg(old_value + 1, &_length_and_refcount, old_value);
      if (found == old_value) {
        return true; // successfully updated.
      }
      // refcount changed, try again.
    }
  }
}

// The increment_refcount() is called when not doing lookup. It is assumed that you
// have a symbol with a non-zero refcount and it can't become zero while referenced by
// this caller.
void Symbol::increment_refcount() {
  if (!try_increment_refcount()) {
#ifdef ASSERT
    print();
    fatal("refcount has gone to zero");
#endif
  }
#ifndef PRODUCT
  if (refcount() != PERM_REFCOUNT) { // not a permanent symbol
    NOT_PRODUCT(Atomic::inc(&_total_count);)
  }
#endif
}

// Decrement refcount potentially while racing increment, so we need
// to check the value after attempting to decrement so that if another
// thread increments to PERM_REFCOUNT the value is not decremented.
void Symbol::decrement_refcount() {
  uint32_t found = _length_and_refcount;
  while (true) {
    uint32_t old_value = found;
    int refc = extract_refcount(old_value);
    if (refc == PERM_REFCOUNT) {
      return;  // refcount is permanent, permanent is sticky
    } else if (refc == 0) {
#ifdef ASSERT
      print();
      fatal("refcount underflow");
#endif
      return;
    } else {
      found = Atomic::cmpxchg(old_value - 1, &_length_and_refcount, old_value);
      if (found == old_value) {
        return;  // successfully updated.
      }
      // refcount changed, try again.
    }
  }
}

void Symbol::make_permanent() {
  uint32_t found = _length_and_refcount;
  while (true) {
    uint32_t old_value = found;
    int refc = extract_refcount(old_value);
    if (refc == PERM_REFCOUNT) {
      return;  // refcount is permanent, permanent is sticky
    } else if (refc == 0) {
#ifdef ASSERT
      print();
      fatal("refcount underflow");
#endif
      return;
    } else {
      int len = extract_length(old_value);
      found = Atomic::cmpxchg(pack_length_and_refcount(len, PERM_REFCOUNT), &_length_and_refcount, old_value);
      if (found == old_value) {
        return;  // successfully updated.
      }
      // refcount changed, try again.
    }
  }
}

void Symbol::metaspace_pointers_do(MetaspaceClosure* it) {
  if (log_is_enabled(Trace, cds)) {
    LogStream trace_stream(Log(cds)::trace());
    trace_stream.print("Iter(Symbol): %p ", this);
    print_value_on(&trace_stream);
    trace_stream.cr();
  }
}

void Symbol::print_on(outputStream* st) const {
  st->print("Symbol: '");
  print_symbol_on(st);
  st->print("'");
  st->print(" count %d", refcount());
}

// The print_value functions are present in all builds, to support the
// disassembler and error reporting.
void Symbol::print_value_on(outputStream* st) const {
  st->print("'");
  for (int i = 0; i < utf8_length(); i++) {
    st->print("%c", char_at(i));
  }
  st->print("'");
}

bool Symbol::is_valid(Symbol* s) {
  if (!is_aligned(s, sizeof(MetaWord))) return false;
  if ((size_t)s < os::min_page_size()) return false;

  if (!os::is_readable_range(s, s + 1)) return false;

  // Symbols are not allocated in Java heap.
  if (Universe::heap()->is_in_reserved(s)) return false;

  int len = s->utf8_length();
  if (len < 0) return false;

  jbyte* bytes = (jbyte*) s->bytes();
  return os::is_readable_range(bytes, bytes + len);
}

// SymbolTable prints this in its statistics
NOT_PRODUCT(size_t Symbol::_total_count = 0;)
