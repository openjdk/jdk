/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_SYMBOLHANDLE_HPP
#define SHARE_OOPS_SYMBOLHANDLE_HPP

#include "memory/allocation.hpp"
#include "oops/symbol.hpp"
#include "runtime/atomic.hpp"

// TempNewSymbol acts as a handle class in a handle/body idiom and is
// responsible for proper resource management of the body (which is a Symbol*).
// The body is resource managed by a reference counting scheme.
// TempNewSymbol can therefore be used to properly hold a newly created or referenced
// Symbol* temporarily in scope.
//
// Routines in SymbolTable will initialize the reference count of a Symbol* before
// it becomes "managed" by TempNewSymbol instances. As a handle class, TempNewSymbol
// needs to maintain proper reference counting in context of copy semantics.
//
// In SymbolTable, new_symbol() will create a Symbol* if not already in the
// symbol table and add to the symbol's reference count.
// probe() and lookup_only() will increment the refcount if symbol is found.
template <bool TEMP>
class SymbolHandleBase : public StackObj {
  static Symbol* volatile _cleanup_delay_queue[];
  static volatile uint _cleanup_delay_index;

  Symbol* _temp;

public:
  static constexpr uint CLEANUP_DELAY_MAX_ENTRIES = 128;

  SymbolHandleBase() : _temp(nullptr) { }

  // Conversion from a Symbol* to a SymbolHandleBase.
  // Does not increment the current reference count if temporary.
  SymbolHandleBase(Symbol *s) : _temp(s) {
    if (!TEMP) {
      Symbol::maybe_increment_refcount(_temp);
      return;
    }

    // Delay cleanup for temp symbols. Refcount is incremented while in
    // queue. But don't requeue existing entries, or entries that are held
    // elsewhere - it's a waste of effort.
    if (s != nullptr && s->refcount() == 1) {
      add_to_cleanup_delay_queue(s);
    }
  }

  // Copy constructor increments reference count.
  SymbolHandleBase(const SymbolHandleBase& rhs) : _temp(rhs._temp) {
    Symbol::maybe_increment_refcount(_temp);
  }

  // Assignment operator uses a c++ trick called copy and swap idiom.
  // rhs is passed by value so within the scope of this method it is a copy.
  // At method exit it contains the former value of _temp, triggering the correct refcount
  // decrement upon destruction.
  void operator=(SymbolHandleBase rhs) {
    Symbol* tmp = rhs._temp;
    rhs._temp = _temp;
    _temp = tmp;
  }

  // Decrement reference counter so it can go away if it's unused
  ~SymbolHandleBase() {
    Symbol::maybe_decrement_refcount(_temp);
  }

  // Keep this symbol alive for some time to allow for reuse.
  // Temp symbols for the same string can often be created in quick succession,
  // and this queue allows them to be reused instead of churning.
  void add_to_cleanup_delay_queue(Symbol* sym) {
    sym->increment_refcount();
    STATIC_ASSERT(is_power_of_2(CLEANUP_DELAY_MAX_ENTRIES)); // allow modulo shortcut
    uint i = Atomic::add(&_cleanup_delay_index, 1u) & (CLEANUP_DELAY_MAX_ENTRIES - 1);
    Symbol* old = Atomic::xchg(&_cleanup_delay_queue[i], sym);
    if (old != nullptr) {
      old->decrement_refcount();
    }
  }

  // Symbol* conversion operators
  Symbol* operator -> () const                   { return _temp; }
  bool    operator == (Symbol* o) const          { return _temp == o; }
  operator Symbol*() const                       { return _temp; }

  static unsigned int compute_hash(const SymbolHandleBase& name) {
    return (unsigned int) name->identity_hash();
  }

  static void drain_cleanup_delay_queue() {
    for (uint i = 0; i < CLEANUP_DELAY_MAX_ENTRIES; i++) {
      Symbol* sym = Atomic::xchg(&_cleanup_delay_queue[i], (Symbol*) nullptr);
      if (sym != nullptr) {
        sym->decrement_refcount();
      }
    }
  }
};

template<bool TEMP>
Symbol* volatile SymbolHandleBase<TEMP>::_cleanup_delay_queue[CLEANUP_DELAY_MAX_ENTRIES] = {};
template<bool TEMP>
volatile uint SymbolHandleBase<TEMP>::_cleanup_delay_index = 0;

// TempNewSymbol is a temporary holder for a newly created symbol
using TempNewSymbol = SymbolHandleBase<true>;

// SymbolHandle is a non-temp symbol used to hold a symbol in a semi permanent place,
// like in a hashtable. The only difference is that the constructor increments the refcount.
using SymbolHandle = SymbolHandleBase<false>;

#endif // SHARE_OOPS_SYMBOLHANDLE_HPP
