/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciSymbol.hpp"
#include "ci/ciUtilities.hpp"
#include "memory/oopFactory.hpp"

// ------------------------------------------------------------------
// ciSymbol::ciSymbol
//
// Preallocated symbol variant.  Used with symbols from vmSymbols.
ciSymbol::ciSymbol(Symbol* s, vmSymbols::SID sid)
  : _symbol(s), _sid(sid)
{
  assert(_symbol != NULL, "adding null symbol");
  _symbol->increment_refcount();  // increment ref count
  assert(sid_ok(), "must be in vmSymbols");
}

// Normal case for non-famous symbols.
ciSymbol::ciSymbol(Symbol* s)
  : _symbol(s), _sid(vmSymbols::NO_SID)
{
  assert(_symbol != NULL, "adding null symbol");
  _symbol->increment_refcount();  // increment ref count
  assert(sid_ok(), "must not be in vmSymbols");
}

// ciSymbol
//
// This class represents a Symbol* in the HotSpot virtual
// machine.

// ------------------------------------------------------------------
// ciSymbol::as_utf8
//
// The text of the symbol as a null-terminated C string.
const char* ciSymbol::as_utf8() {
  VM_QUICK_ENTRY_MARK;
  Symbol* s = get_symbol();
  return s->as_utf8();
}

// The text of the symbol as a null-terminated C string.
const char* ciSymbol::as_quoted_ascii() {
  GUARDED_VM_QUICK_ENTRY(return get_symbol()->as_quoted_ascii();)
}

// ------------------------------------------------------------------
// ciSymbol::base
const jbyte* ciSymbol::base() {
  GUARDED_VM_ENTRY(return get_symbol()->base();)
}

// ------------------------------------------------------------------
// ciSymbol::byte_at
int ciSymbol::byte_at(int i) {
  GUARDED_VM_ENTRY(return get_symbol()->byte_at(i);)
}

// ------------------------------------------------------------------
// ciSymbol::starts_with
//
// Tests if the symbol starts with the given prefix.
bool ciSymbol::starts_with(const char* prefix, int len) const {
  GUARDED_VM_ENTRY(return get_symbol()->starts_with(prefix, len);)
}

bool ciSymbol::is_signature_polymorphic_name()  const {
  GUARDED_VM_ENTRY(return MethodHandles::is_signature_polymorphic_name(get_symbol());)
}

// ------------------------------------------------------------------
// ciSymbol::index_of
//
// Determines where the symbol contains the given substring.
int ciSymbol::index_of_at(int i, const char* str, int len) const {
  GUARDED_VM_ENTRY(return get_symbol()->index_of_at(i, str, len);)
}

// ------------------------------------------------------------------
// ciSymbol::utf8_length
int ciSymbol::utf8_length() {
  GUARDED_VM_ENTRY(return get_symbol()->utf8_length();)
}

// ------------------------------------------------------------------
// ciSymbol::print_impl
//
// Implementation of the print method
void ciSymbol::print_impl(outputStream* st) {
  st->print(" value=");
  print_symbol_on(st);
}

// ------------------------------------------------------------------
// ciSymbol::print_symbol_on
//
// Print the value of this symbol on an outputStream
void ciSymbol::print_symbol_on(outputStream *st) {
  GUARDED_VM_ENTRY(get_symbol()->print_symbol_on(st);)
}

// ------------------------------------------------------------------
// ciSymbol::make_impl
//
// Make a ciSymbol from a C string (implementation).
ciSymbol* ciSymbol::make_impl(const char* s) {
  EXCEPTION_CONTEXT;
  TempNewSymbol sym = SymbolTable::new_symbol(s, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    CURRENT_THREAD_ENV->record_out_of_memory_failure();
    return ciEnv::_unloaded_cisymbol;
  }
  return CURRENT_THREAD_ENV->get_symbol(sym);
}

// ------------------------------------------------------------------
// ciSymbol::make
//
// Make a ciSymbol from a C string.
ciSymbol* ciSymbol::make(const char* s) {
  GUARDED_VM_ENTRY(return make_impl(s);)
}
