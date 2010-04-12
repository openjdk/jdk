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

#include "incls/_precompiled.incl"
#include "incls/_bytecode.cpp.incl"

// Implementation of Bytecode
// Should eventually get rid of these functions and use ThisRelativeObj methods instead

void Bytecode::set_code(Bytecodes::Code code) {
  Bytecodes::check(code);
  *addr_at(0) = u_char(code);
}


bool Bytecode::check_must_rewrite() const {
  assert(Bytecodes::can_rewrite(code()), "post-check only");

  // Some codes are conditionally rewriting.  Look closely at them.
  switch (code()) {
  case Bytecodes::_aload_0:
    // Even if RewriteFrequentPairs is turned on,
    // the _aload_0 code might delay its rewrite until
    // a following _getfield rewrites itself.
    return false;

  case Bytecodes::_lookupswitch:
    return false;  // the rewrite is not done by the interpreter

  case Bytecodes::_new:
    // (Could actually look at the class here, but the profit would be small.)
    return false;  // the rewrite is not always done
  }

  // No other special cases.
  return true;
}



// Implementation of Bytecode_tableupswitch

int Bytecode_tableswitch::dest_offset_at(int i) const {
  address x = aligned_addr_at(1);
  int x2 = aligned_offset(1 + (3 + i)*jintSize);
  int val = java_signed_word_at(x2);
  return java_signed_word_at(aligned_offset(1 + (3 + i)*jintSize));
}


// Implementation of Bytecode_invoke

void Bytecode_invoke::verify() const {
  Bytecodes::Code bc = adjusted_invoke_code();
  assert(is_valid(), "check invoke");
}


symbolOop Bytecode_invoke::signature() const {
  constantPoolOop constants = method()->constants();
  return constants->signature_ref_at(index());
}


symbolOop Bytecode_invoke::name() const {
  constantPoolOop constants = method()->constants();
  return constants->name_ref_at(index());
}


BasicType Bytecode_invoke::result_type(Thread *thread) const {
  symbolHandle sh(thread, signature());
  ResultTypeFinder rts(sh);
  rts.iterate();
  return rts.type();
}


methodHandle Bytecode_invoke::static_target(TRAPS) {
  methodHandle m;
  KlassHandle resolved_klass;
  constantPoolHandle constants(THREAD, _method->constants());

  if (adjusted_invoke_code() == Bytecodes::_invokedynamic) {
    LinkResolver::resolve_dynamic_method(m, resolved_klass, constants, index(), CHECK_(methodHandle()));
  } else if (adjusted_invoke_code() != Bytecodes::_invokeinterface) {
    LinkResolver::resolve_method(m, resolved_klass, constants, index(), CHECK_(methodHandle()));
  } else {
    LinkResolver::resolve_interface_method(m, resolved_klass, constants, index(), CHECK_(methodHandle()));
  }
  return m;
}


int Bytecode_invoke::index() const {
  // Note:  Rewriter::rewrite changes the Java_u2 of an invokedynamic to a native_u4,
  // at the same time it allocates per-call-site CP cache entries.
  if (has_giant_index())
    return Bytes::get_native_u4(bcp() + 1);
  else
    return Bytes::get_Java_u2(bcp() + 1);
}


// Implementation of Bytecode_static

void Bytecode_static::verify() const {
  assert(Bytecodes::java_code(code()) == Bytecodes::_putstatic
      || Bytecodes::java_code(code()) == Bytecodes::_getstatic, "check static");
}


BasicType Bytecode_static::result_type(methodOop method) const {
  int index = java_hwrd_at(1);
  constantPoolOop constants = method->constants();
  symbolOop field_type = constants->signature_ref_at(index);
  BasicType basic_type = FieldType::basic_type(field_type);
  return basic_type;
}


// Implementation of Bytecode_field

void Bytecode_field::verify() const {
  Bytecodes::Code stdc = Bytecodes::java_code(code());
  assert(stdc == Bytecodes::_putstatic || stdc == Bytecodes::_getstatic ||
         stdc == Bytecodes::_putfield  || stdc == Bytecodes::_getfield, "check field");
}


bool Bytecode_field::is_static() const {
  Bytecodes::Code stdc = Bytecodes::java_code(code());
  return stdc == Bytecodes::_putstatic || stdc == Bytecodes::_getstatic;
}


int Bytecode_field::index() const {
  return java_hwrd_at(1);
}


// Implementation of Bytecodes loac constant

int Bytecode_loadconstant::index() const {
  Bytecodes::Code stdc = Bytecodes::java_code(code());
  return stdc == Bytecodes::_ldc ? java_byte_at(1) : java_hwrd_at(1);
}

//------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT

void Bytecode_lookupswitch::verify() const {
  switch (Bytecodes::java_code(code())) {
    case Bytecodes::_lookupswitch:
      { int i = number_of_pairs() - 1;
        while (i-- > 0) {
          assert(pair_at(i)->match() < pair_at(i+1)->match(), "unsorted table entries");
        }
      }
      break;
    default:
      fatal("not a lookupswitch bytecode");
  }
}

void Bytecode_tableswitch::verify() const {
  switch (Bytecodes::java_code(code())) {
    case Bytecodes::_tableswitch:
      { int lo = low_key();
        int hi = high_key();
        assert (hi >= lo, "incorrect hi/lo values in tableswitch");
        int i  = hi - lo - 1 ;
        while (i-- > 0) {
          // no special check needed
        }
      }
      break;
    default:
      fatal("not a tableswitch bytecode");
  }
}

#endif
