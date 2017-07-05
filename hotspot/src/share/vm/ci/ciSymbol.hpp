/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CI_CISYMBOL_HPP
#define SHARE_VM_CI_CISYMBOL_HPP

#include "ci/ciObject.hpp"
#include "ci/ciObjectFactory.hpp"
#include "classfile/vmSymbols.hpp"
#include "oops/symbolOop.hpp"

// ciSymbol
//
// This class represents a symbolOop in the HotSpot virtual
// machine.
class ciSymbol : public ciObject {
  CI_PACKAGE_ACCESS
  // These friends all make direct use of get_symbolOop:
  friend class ciEnv;
  friend class ciInstanceKlass;
  friend class ciSignature;
  friend class ciMethod;
  friend class ciObjArrayKlass;

private:
  const vmSymbols::SID _sid;
  DEBUG_ONLY( bool sid_ok() { return vmSymbols::find_sid(get_symbolOop()) == _sid; } )

  ciSymbol(symbolOop s);  // normal case, for symbols not mentioned in vmSymbols
  ciSymbol(symbolHandle s, vmSymbols::SID sid);   // for use with vmSymbolHandles

  symbolOop get_symbolOop() const { return (symbolOop)get_oop(); }

  const char* type_string() { return "ciSymbol"; }

  void print_impl(outputStream* st);

  // This is public in symbolOop but private here, because the base can move:
  jbyte*      base();

  // Make a ciSymbol from a C string (implementation).
  static ciSymbol* make_impl(const char* s);

public:
  // The enumeration ID from vmSymbols, or vmSymbols::NO_SID if none.
  vmSymbols::SID sid() const { return _sid; }

  // The text of the symbol as a null-terminated utf8 string.
  const char* as_utf8();
  int         utf8_length();

  // Return the i-th utf8 byte, where i < utf8_length
  int         byte_at(int i);

  // Tests if the symbol starts with the given prefix.
  bool starts_with(const char* prefix, int len) const;

  // Determines where the symbol contains the given substring.
  int index_of_at(int i, const char* str, int len) const;

  // What kind of ciObject is this?
  bool is_symbol() { return true; }

  void print_symbol_on(outputStream* st);
  void print_symbol() {
    print_symbol_on(tty);
  }

  // Make a ciSymbol from a C string.
  // Consider adding to vmSymbols.hpp instead of using this constructor.
  // (Your code will be less subject to typographical bugs.)
  static ciSymbol* make(const char* s);

#define CI_SYMBOL_DECLARE(name, ignore_def) \
  static ciSymbol* name() { return ciObjectFactory::vm_symbol_at(vmSymbols::VM_SYMBOL_ENUM_NAME(name)); }
  VM_SYMBOLS_DO(CI_SYMBOL_DECLARE, CI_SYMBOL_DECLARE)
#undef CI_SYMBOL_DECLARE
};

#endif // SHARE_VM_CI_CISYMBOL_HPP
