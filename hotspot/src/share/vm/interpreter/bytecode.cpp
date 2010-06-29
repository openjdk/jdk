/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_bytecode.cpp.incl"

// Implementation of Bytecode

bool Bytecode::check_must_rewrite(Bytecodes::Code code) const {
  assert(Bytecodes::can_rewrite(code), "post-check only");

  // Some codes are conditionally rewriting.  Look closely at them.
  switch (code) {
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


#ifdef ASSERT

void Bytecode::assert_same_format_as(Bytecodes::Code testbc, bool is_wide) const {
  Bytecodes::Code thisbc = Bytecodes::cast(byte_at(0));
  if (thisbc == Bytecodes::_breakpoint)  return;  // let the assertion fail silently
  if (is_wide) {
    assert(thisbc == Bytecodes::_wide, "expected a wide instruction");
    thisbc = Bytecodes::cast(byte_at(1));
    if (thisbc == Bytecodes::_breakpoint)  return;
  }
  int thisflags = Bytecodes::flags(testbc, is_wide) & Bytecodes::_all_fmt_bits;
  int testflags = Bytecodes::flags(thisbc, is_wide) & Bytecodes::_all_fmt_bits;
  if (thisflags != testflags)
    tty->print_cr("assert_same_format_as(%d) failed on bc=%d%s; %d != %d",
                  (int)testbc, (int)thisbc, (is_wide?"/wide":""), testflags, thisflags);
  assert(thisflags == testflags, "expected format");
}

void Bytecode::assert_index_size(int size, Bytecodes::Code bc, bool is_wide) {
  int have_fmt = (Bytecodes::flags(bc, is_wide)
                  & (Bytecodes::_fmt_has_u2 | Bytecodes::_fmt_has_u4 |
                     Bytecodes::_fmt_not_simple |
                     // Not an offset field:
                     Bytecodes::_fmt_has_o));
  int need_fmt = -1;
  switch (size) {
  case 1: need_fmt = 0;                      break;
  case 2: need_fmt = Bytecodes::_fmt_has_u2; break;
  case 4: need_fmt = Bytecodes::_fmt_has_u4; break;
  }
  if (is_wide)  need_fmt |= Bytecodes::_fmt_not_simple;
  if (have_fmt != need_fmt) {
    tty->print_cr("assert_index_size %d: bc=%d%s %d != %d", size, bc, (is_wide?"/wide":""), have_fmt, need_fmt);
    assert(have_fmt == need_fmt, "assert_index_size");
  }
}

void Bytecode::assert_offset_size(int size, Bytecodes::Code bc, bool is_wide) {
  int have_fmt = Bytecodes::flags(bc, is_wide) & Bytecodes::_all_fmt_bits;
  int need_fmt = -1;
  switch (size) {
  case 2: need_fmt = Bytecodes::_fmt_bo2; break;
  case 4: need_fmt = Bytecodes::_fmt_bo4; break;
  }
  if (is_wide)  need_fmt |= Bytecodes::_fmt_not_simple;
  if (have_fmt != need_fmt) {
    tty->print_cr("assert_offset_size %d: bc=%d%s %d != %d", size, bc, (is_wide?"/wide":""), have_fmt, need_fmt);
    assert(have_fmt == need_fmt, "assert_offset_size");
  }
}

void Bytecode::assert_constant_size(int size, int where, Bytecodes::Code bc, bool is_wide) {
  int have_fmt = Bytecodes::flags(bc, is_wide) & (Bytecodes::_all_fmt_bits
                                                  // Ignore any 'i' field (for iinc):
                                                  & ~Bytecodes::_fmt_has_i);
  int need_fmt = -1;
  switch (size) {
  case 1: need_fmt = Bytecodes::_fmt_bc;                          break;
  case 2: need_fmt = Bytecodes::_fmt_bc | Bytecodes::_fmt_has_u2; break;
  }
  if (is_wide)  need_fmt |= Bytecodes::_fmt_not_simple;
  int length = is_wide ? Bytecodes::wide_length_for(bc) : Bytecodes::length_for(bc);
  if (have_fmt != need_fmt || where + size != length) {
    tty->print_cr("assert_constant_size %d @%d: bc=%d%s %d != %d", size, where, bc, (is_wide?"/wide":""), have_fmt, need_fmt);
  }
  assert(have_fmt == need_fmt, "assert_constant_size");
  assert(where + size == length, "assert_constant_size oob");
}

void Bytecode::assert_native_index(Bytecodes::Code bc, bool is_wide) {
  assert((Bytecodes::flags(bc, is_wide) & Bytecodes::_fmt_has_nbo) != 0, "native index");
}

#endif //ASSERT

// Implementation of Bytecode_tableupswitch

int Bytecode_tableswitch::dest_offset_at(int i) const {
  return get_Java_u4_at(aligned_offset(1 + (3 + i)*jintSize));
}


// Implementation of Bytecode_invoke

void Bytecode_invoke::verify() const {
  Bytecodes::Code bc = adjusted_invoke_code();
  assert(is_valid(), "check invoke");
  assert(method()->constants()->cache() != NULL, "do not call this from verifier or rewriter");
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
  Bytecodes::Code stdc = Bytecodes::java_code(code());
  Bytecode* invoke = Bytecode_at(bcp());
  if (invoke->has_index_u4(stdc))
    return invoke->get_index_u4(stdc);
  else
    return invoke->get_index_u2_cpcache(stdc);
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
  Bytecode* invoke = Bytecode_at(bcp());
  return invoke->get_index_u2_cpcache(Bytecodes::_getfield);
}


// Implementation of Bytecodes loac constant

int Bytecode_loadconstant::index() const {
  Bytecodes::Code stdc = Bytecodes::java_code(code());
  if (stdc != Bytecodes::_wide) {
    if (Bytecodes::java_code(stdc) == Bytecodes::_ldc)
      return get_index_u1(stdc);
    else
      return get_index_u2(stdc, false);
  }
  stdc = Bytecodes::code_at(addr_at(1));
  return get_index_u2(stdc, true);
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
