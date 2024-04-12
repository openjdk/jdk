/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/metaspaceShared.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/symbol.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/signature.hpp"
#include "utilities/stringUtils.hpp"
#include "utilities/utf8.hpp"

Symbol* Symbol::_vm_symbols[vmSymbols::number_of_symbols()];

uint32_t Symbol::pack_hash_and_refcount(short hash, int refcount) {
  STATIC_ASSERT(PERM_REFCOUNT == ((1 << 16) - 1));
  assert(refcount >= 0, "negative refcount");
  assert(refcount <= PERM_REFCOUNT, "invalid refcount");
  uint32_t hi = hash;
  uint32_t lo = refcount;
  return (hi << 16) | lo;
}

Symbol::Symbol(const u1* name, int length, int refcount) {
  _hash_and_refcount =  pack_hash_and_refcount((short)os::random(), refcount);
  _length = (u2)length;
  // _body[0..1] are allocated in the header just by coincidence in the current
  // implementation of Symbol. They are read by identity_hash(), so make sure they
  // are initialized.
  // No other code should assume that _body[0..1] are always allocated. E.g., do
  // not unconditionally read base()[0] as that will be invalid for an empty Symbol.
  _body[0] = _body[1] = 0;
  memcpy(_body, name, length);
}

// This copies the symbol when it is added to the ConcurrentHashTable.
Symbol::Symbol(const Symbol& s1) {
  _hash_and_refcount = s1._hash_and_refcount;
  _length = s1._length;
  memcpy(_body, s1._body, _length);
}

#if INCLUDE_CDS
void Symbol::update_identity_hash() {
  // This is called at a safepoint during dumping of a static CDS archive. The caller should have
  // called os::init_random() with a deterministic seed and then iterate all archived Symbols in
  // a deterministic order.
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");
  _hash_and_refcount =  pack_hash_and_refcount((short)os::random(), PERM_REFCOUNT);
}

void Symbol::set_permanent() {
  // This is called at a safepoint during dumping of a dynamic CDS archive.
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");
  _hash_and_refcount =  pack_hash_and_refcount(extract_hash(_hash_and_refcount), PERM_REFCOUNT);
}
#endif

// ------------------------------------------------------------------
// Symbol::index_of
//
// Test if we have the give substring at or after the i-th char of this
// symbol's utf8 bytes.
// Return -1 on failure.  Otherwise return the first index where substr occurs.
int Symbol::index_of_at(int i, const char* substr, int substr_len) const {
  assert(i >= 0 && i <= utf8_length(), "oob");
  if (substr_len <= 0)  return 0;
  char first_char = substr[0];
  address bytes = (address) ((Symbol*)this)->base();
  address limit = bytes + utf8_length() - substr_len;  // inclusive limit
  address scan = bytes + i;
  if (scan > limit)
    return -1;
  for (; scan <= limit; scan++) {
    scan = (address) memchr(scan, first_char, (limit + 1 - scan));
    if (scan == nullptr)
      return -1;  // not found
    assert(scan >= bytes+i && scan <= limit, "scan oob");
    if (substr_len <= 2
        ? (char) scan[substr_len-1] == substr[substr_len-1]
        : memcmp(scan+1, substr+1, substr_len-1) == 0) {
      return (int)(scan - bytes);
    }
  }
  return -1;
}

bool Symbol::is_star_match(const char* pattern) const {
  if (strchr(pattern, '*') == nullptr) {
    return equals(pattern);
  } else {
    ResourceMark rm;
    char* buf = as_C_string();
    return StringUtils::is_star_match(pattern, buf);
  }
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
  if (s == nullptr) {
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
      if (str[index] == JVM_SIGNATURE_SLASH) {
        str[index] = JVM_SIGNATURE_DOT;
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
    if (str[index] == JVM_SIGNATURE_SLASH) {
      str[index] = JVM_SIGNATURE_DOT;
    }
  }
  return str;
}

static void print_class(outputStream *os, const SignatureStream& ss) {
  int sb = ss.raw_symbol_begin(), se = ss.raw_symbol_end();
  for (int i = sb; i < se; ++i) {
    char ch = ss.raw_char_at(i);
    if (ch == JVM_SIGNATURE_SLASH) {
      os->put(JVM_SIGNATURE_DOT);
    } else {
      os->put(ch);
    }
  }
}

static void print_array(outputStream *os, SignatureStream& ss) {
  int dimensions = ss.skip_array_prefix();
  assert(dimensions > 0, "");
  if (ss.is_reference()) {
    print_class(os, ss);
  } else {
    os->print("%s", type2name(ss.type()));
  }
  for (int i = 0; i < dimensions; ++i) {
    os->print("[]");
  }
}

void Symbol::print_as_signature_external_return_type(outputStream *os) {
  for (SignatureStream ss(this); !ss.is_done(); ss.next()) {
    if (ss.at_return_type()) {
      if (ss.is_array()) {
        print_array(os, ss);
      } else if (ss.is_reference()) {
        print_class(os, ss);
      } else {
        os->print("%s", type2name(ss.type()));
      }
    }
  }
}

void Symbol::print_as_signature_external_parameters(outputStream *os) {
  bool first = true;
  for (SignatureStream ss(this); !ss.is_done(); ss.next()) {
    if (ss.at_return_type()) break;
    if (!first) { os->print(", "); }
    if (ss.is_array()) {
      print_array(os, ss);
    } else if (ss.is_reference()) {
      print_class(os, ss);
    } else {
      os->print("%s", type2name(ss.type()));
    }
    first = false;
  }
}

void Symbol::print_as_field_external_type(outputStream *os) {
  SignatureStream ss(this, false);
  assert(!ss.is_done(), "must have at least one element in field ref");
  assert(!ss.at_return_type(), "field ref cannot be a return type");
  assert(!Signature::is_method(this), "field ref cannot be a method");

  if (ss.is_array()) {
    print_array(os, ss);
  } else if (ss.is_reference()) {
    print_class(os, ss);
  } else {
    os->print("%s", type2name(ss.type()));
  }
#ifdef ASSERT
  ss.next();
  assert(ss.is_done(), "must have at most one element in field ref");
#endif
}

// Increment refcount while checking for zero.  If the Symbol's refcount becomes zero
// a thread could be concurrently removing the Symbol.  This is used during SymbolTable
// lookup to avoid reviving a dead Symbol.
bool Symbol::try_increment_refcount() {
  uint32_t found = _hash_and_refcount;
  while (true) {
    uint32_t old_value = found;
    int refc = extract_refcount(old_value);
    if (refc == PERM_REFCOUNT) {
      return true;  // sticky max or created permanent
    } else if (refc == 0) {
      return false; // dead, can't revive.
    } else {
      found = Atomic::cmpxchg(&_hash_and_refcount, old_value, old_value + 1);
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
    print();
    fatal("refcount has gone to zero");
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
  uint32_t found = _hash_and_refcount;
  while (true) {
    uint32_t old_value = found;
    int refc = extract_refcount(old_value);
    if (refc == PERM_REFCOUNT) {
      return;  // refcount is permanent, permanent is sticky
    } else if (refc == 0) {
      print();
      fatal("refcount underflow");
      return;
    } else {
      found = Atomic::cmpxchg(&_hash_and_refcount, old_value, old_value - 1);
      if (found == old_value) {
        return;  // successfully updated.
      }
      // refcount changed, try again.
    }
  }
}

void Symbol::make_permanent() {
  uint32_t found = _hash_and_refcount;
  while (true) {
    uint32_t old_value = found;
    int refc = extract_refcount(old_value);
    if (refc == PERM_REFCOUNT) {
      return;  // refcount is permanent, permanent is sticky
    } else if (refc == 0) {
      print();
      fatal("refcount underflow");
      return;
    } else {
      short hash = extract_hash(old_value);
      found = Atomic::cmpxchg(&_hash_and_refcount, old_value, pack_hash_and_refcount(hash, PERM_REFCOUNT));
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

void Symbol::print() const { print_on(tty); }

// The print_value functions are present in all builds, to support the
// disassembler and error reporting.
void Symbol::print_value_on(outputStream* st) const {
  st->print_raw("'", 1);
  st->print_raw((const char*)base(), utf8_length());
  st->print_raw("'", 1);
}

void Symbol::print_value() const { print_value_on(tty); }

bool Symbol::is_valid(Symbol* s) {
  if (!is_aligned(s, sizeof(MetaWord))) return false;
  if ((size_t)s < os::min_page_size()) return false;

  if (!os::is_readable_range(s, s + 1)) return false;

  // Symbols are not allocated in Java heap.
  if (Universe::heap()->is_in(s)) return false;

  int len = s->utf8_length();
  if (len < 0) return false;

  jbyte* bytes = (jbyte*) s->bytes();
  return os::is_readable_range(bytes, bytes + len);
}

// SymbolTable prints this in its statistics
NOT_PRODUCT(size_t Symbol::_total_count = 0;)

#ifndef PRODUCT
bool Symbol::is_valid_id(vmSymbolID vm_symbol_id) {
  return vmSymbols::is_valid_id(vm_symbol_id);
}
#endif
