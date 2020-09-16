/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compilerDirectives.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/tribool.hpp"
#include "utilities/xmlstream.hpp"


Symbol* vmSymbols::_symbols[vmSymbols::SID_LIMIT];

Symbol* vmSymbols::_type_signatures[T_VOID+1] = { NULL /*, NULL...*/ };

inline int compare_symbol(const Symbol* a, const Symbol* b) {
  if (a == b)  return 0;
  // follow the natural address order:
  return (address)a > (address)b ? +1 : -1;
}

static vmSymbols::SID vm_symbol_index[vmSymbols::SID_LIMIT];
extern "C" {
  static int compare_vmsymbol_sid(const void* void_a, const void* void_b) {
    const Symbol* a = vmSymbols::symbol_at(*((vmSymbols::SID*) void_a));
    const Symbol* b = vmSymbols::symbol_at(*((vmSymbols::SID*) void_b));
    return compare_symbol(a, b);
  }
}

#ifdef ASSERT
#define VM_SYMBOL_ENUM_NAME_BODY(name, string) #name "\0"
static const char* vm_symbol_enum_names =
  VM_SYMBOLS_DO(VM_SYMBOL_ENUM_NAME_BODY, VM_ALIAS_IGNORE)
  "\0";
static const char* vm_symbol_enum_name(vmSymbols::SID sid) {
  const char* string = &vm_symbol_enum_names[0];
  int skip = (int)sid - (int)vmSymbols::FIRST_SID;
  for (; skip != 0; skip--) {
    size_t skiplen = strlen(string);
    if (skiplen == 0)  return "<unknown>";  // overflow
    string += skiplen+1;
  }
  return string;
}
#endif //ASSERT

// Put all the VM symbol strings in one place.
// Makes for a more compact libjvm.
#define VM_SYMBOL_BODY(name, string) string "\0"
static const char* vm_symbol_bodies = VM_SYMBOLS_DO(VM_SYMBOL_BODY, VM_ALIAS_IGNORE);

void vmSymbols::initialize(TRAPS) {
  assert((int)SID_LIMIT <= (1<<log2_SID_LIMIT), "must fit in this bitfield");
  assert((int)SID_LIMIT*5 > (1<<log2_SID_LIMIT), "make the bitfield smaller, please");
  assert(vmIntrinsics::FLAG_LIMIT <= (1 << vmIntrinsics::log2_FLAG_LIMIT), "must fit in this bitfield");

  if (!UseSharedSpaces) {
    const char* string = &vm_symbol_bodies[0];
    for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
      Symbol* sym = SymbolTable::new_permanent_symbol(string);
      _symbols[index] = sym;
      string += strlen(string); // skip string body
      string += 1;              // skip trailing null
    }

    _type_signatures[T_BYTE]    = byte_signature();
    _type_signatures[T_CHAR]    = char_signature();
    _type_signatures[T_DOUBLE]  = double_signature();
    _type_signatures[T_FLOAT]   = float_signature();
    _type_signatures[T_INT]     = int_signature();
    _type_signatures[T_LONG]    = long_signature();
    _type_signatures[T_SHORT]   = short_signature();
    _type_signatures[T_BOOLEAN] = bool_signature();
    _type_signatures[T_VOID]    = void_signature();
#ifdef ASSERT
    for (int i = (int)T_BOOLEAN; i < (int)T_VOID+1; i++) {
      Symbol* s = _type_signatures[i];
      if (s == NULL)  continue;
      SignatureStream ss(s, false);
      assert(ss.type() == i, "matching signature");
      assert(!ss.is_reference(), "no single-char signature for T_OBJECT, etc.");
    }
#endif
  }

#ifdef ASSERT
  // Check for duplicates:
  for (int i1 = (int)FIRST_SID; i1 < (int)SID_LIMIT; i1++) {
    Symbol* sym = symbol_at((SID)i1);
    for (int i2 = (int)FIRST_SID; i2 < i1; i2++) {
      if (symbol_at((SID)i2) == sym) {
        tty->print("*** Duplicate VM symbol SIDs %s(%d) and %s(%d): \"",
                   vm_symbol_enum_name((SID)i2), i2,
                   vm_symbol_enum_name((SID)i1), i1);
        sym->print_symbol_on(tty);
        tty->print_cr("\"");
      }
    }
  }
#endif //ASSERT

  // Create an index for find_id:
  {
    for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
      vm_symbol_index[index] = (SID)index;
    }
    int num_sids = SID_LIMIT-FIRST_SID;
    qsort(&vm_symbol_index[FIRST_SID], num_sids, sizeof(vm_symbol_index[0]),
          compare_vmsymbol_sid);
  }

#ifdef ASSERT
  {
    // Spot-check correspondence between strings, symbols, and enums:
    assert(_symbols[NO_SID] == NULL, "must be");
    const char* str = "java/lang/Object";
    TempNewSymbol jlo = SymbolTable::new_permanent_symbol(str);
    assert(strncmp(str, (char*)jlo->base(), jlo->utf8_length()) == 0, "");
    assert(jlo == java_lang_Object(), "");
    SID sid = VM_SYMBOL_ENUM_NAME(java_lang_Object);
    assert(find_sid(jlo) == sid, "");
    assert(symbol_at(sid) == jlo, "");

    // Make sure find_sid produces the right answer in each case.
    for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
      Symbol* sym = symbol_at((SID)index);
      sid = find_sid(sym);
      assert(sid == (SID)index, "symbol index works");
      // Note:  If there are duplicates, this assert will fail.
      // A "Duplicate VM symbol" message will have already been printed.
    }

    // The string "format" happens (at the moment) not to be a vmSymbol,
    // though it is a method name in java.lang.String.
    str = "format";
    TempNewSymbol fmt = SymbolTable::new_permanent_symbol(str);
    sid = find_sid(fmt);
    assert(sid == NO_SID, "symbol index works (negative test)");
  }
#endif
}


#ifndef PRODUCT
const char* vmSymbols::name_for(vmSymbols::SID sid) {
  if (sid == NO_SID)
    return "NO_SID";
  const char* string = &vm_symbol_bodies[0];
  for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
    if (index == (int)sid)
      return string;
    string += strlen(string); // skip string body
    string += 1;              // skip trailing null
  }
  return "BAD_SID";
}
#endif



void vmSymbols::symbols_do(SymbolClosure* f) {
  for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
    f->do_symbol(&_symbols[index]);
  }
  for (int i = 0; i < T_VOID+1; i++) {
    f->do_symbol(&_type_signatures[i]);
  }
}

void vmSymbols::metaspace_pointers_do(MetaspaceClosure *it) {
  for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
    it->push(&_symbols[index]);
  }
  for (int i = 0; i < T_VOID+1; i++) {
    it->push(&_type_signatures[i]);
  }
}

void vmSymbols::serialize(SerializeClosure* soc) {
  soc->do_region((u_char*)&_symbols[FIRST_SID],
                 (SID_LIMIT - FIRST_SID) * sizeof(_symbols[0]));
  soc->do_region((u_char*)_type_signatures, sizeof(_type_signatures));
}

static int mid_hint = (int)vmSymbols::FIRST_SID+1;

#ifndef PRODUCT
static int find_sid_calls, find_sid_probes;
// (Typical counts are calls=7000 and probes=17000.)
#endif

vmSymbols::SID vmSymbols::find_sid(const Symbol* symbol) {
  // Handle the majority of misses by a bounds check.
  // Then, use a binary search over the index.
  // Expected trip count is less than log2_SID_LIMIT, about eight.
  // This is slow but acceptable, given that calls are not
  // dynamically common.  (Method*::intrinsic_id has a cache.)
  NOT_PRODUCT(find_sid_calls++);
  int min = (int)FIRST_SID, max = (int)SID_LIMIT - 1;
  SID sid = NO_SID, sid1;
  int cmp1;
  sid1 = vm_symbol_index[min];
  cmp1 = compare_symbol(symbol, symbol_at(sid1));
  if (cmp1 <= 0) {              // before the first
    if (cmp1 == 0)  sid = sid1;
  } else {
    sid1 = vm_symbol_index[max];
    cmp1 = compare_symbol(symbol, symbol_at(sid1));
    if (cmp1 >= 0) {            // after the last
      if (cmp1 == 0)  sid = sid1;
    } else {
      // After checking the extremes, do a binary search.
      ++min; --max;             // endpoints are done
      int mid = mid_hint;       // start at previous success
      while (max >= min) {
        assert(mid >= min && mid <= max, "");
        NOT_PRODUCT(find_sid_probes++);
        sid1 = vm_symbol_index[mid];
        cmp1 = compare_symbol(symbol, symbol_at(sid1));
        if (cmp1 == 0) {
          mid_hint = mid;
          sid = sid1;
          break;
        }
        if (cmp1 < 0)
          max = mid - 1;        // symbol < symbol_at(sid)
        else
          min = mid + 1;

        // Pick a new probe point:
        mid = (max + min) / 2;
      }
    }
  }

#ifdef ASSERT
  // Perform the exhaustive self-check the first 1000 calls,
  // and every 100 calls thereafter.
  static int find_sid_check_count = -2000;
  if ((uint)++find_sid_check_count > (uint)100) {
    if (find_sid_check_count > 0)  find_sid_check_count = 0;

    // Make sure this is the right answer, using linear search.
    // (We have already proven that there are no duplicates in the list.)
    SID sid2 = NO_SID;
    for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
      Symbol* sym2 = symbol_at((SID)index);
      if (sym2 == symbol) {
        sid2 = (SID)index;
        break;
      }
    }
    // Unless it's a duplicate, assert that the sids are the same.
    if (_symbols[sid] != _symbols[sid2]) {
      assert(sid == sid2, "binary same as linear search");
    }
  }
#endif //ASSERT

  return sid;
}

vmSymbols::SID vmSymbols::find_sid(const char* symbol_name) {
  Symbol* symbol = SymbolTable::probe(symbol_name, (int) strlen(symbol_name));
  if (symbol == NULL)  return NO_SID;
  return find_sid(symbol);
}
