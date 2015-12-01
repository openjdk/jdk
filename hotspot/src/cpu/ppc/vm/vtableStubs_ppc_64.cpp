/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2015 SAP AG. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "code/vtableStubs.hpp"
#include "interp_masm_ppc_64.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klassVtable.hpp"
#include "runtime/sharedRuntime.hpp"
#include "vmreg_ppc.inline.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

#define __ masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) // nothing
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif
#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread, oopDesc* receiver, int index);
#endif

// Used by compiler only; may use only caller saved, non-argument
// registers.
VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {
  // PPC port: use fixed size.
  const int code_length = VtableStub::pd_code_size_limit(true);
  VtableStub* s = new (code_length) VtableStub(true, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);
  address start_pc;

#ifndef PRODUCT
  if (CountCompiledCalls) {
    __ load_const(R11_scratch1, SharedRuntime::nof_megamorphic_calls_addr());
    __ lwz(R12_scratch2, 0, R11_scratch1);
    __ addi(R12_scratch2, R12_scratch2, 1);
    __ stw(R12_scratch2, 0, R11_scratch1);
  }
#endif

  assert(VtableStub::receiver_location() == R3_ARG1->as_VMReg(), "receiver expected in R3_ARG1");

  // Get receiver klass.
  const Register rcvr_klass = R11_scratch1;

  // We might implicit NULL fault here.
  address npe_addr = __ pc(); // npe = null pointer exception
  __ null_check(R3, oopDesc::klass_offset_in_bytes(), /*implicit only*/NULL);
  __ load_klass(rcvr_klass, R3);

 // Set method (in case of interpreted method), and destination address.
  int entry_offset = in_bytes(Klass::vtable_start_offset()) + vtable_index*vtableEntry::size_in_bytes();

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    // Check offset vs vtable length.
    const Register vtable_len = R12_scratch2;
    __ lwz(vtable_len, in_bytes(Klass::vtable_length_offset()), rcvr_klass);
    __ cmpwi(CCR0, vtable_len, vtable_index*vtableEntry::size());
    __ bge(CCR0, L);
    __ li(R12_scratch2, vtable_index);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, bad_compiled_vtable_index), R3_ARG1, R12_scratch2, false);
    __ bind(L);
  }
#endif

  int v_off = entry_offset + vtableEntry::method_offset_in_bytes();

  __ ld(R19_method, v_off, rcvr_klass);

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    __ cmpdi(CCR0, R19_method, 0);
    __ bne(CCR0, L);
    __ stop("Vtable entry is ZERO", 102);
    __ bind(L);
  }
#endif

  // If the vtable entry is null, the method is abstract.
  address ame_addr = __ pc(); // ame = abstract method error
  __ null_check(R19_method, in_bytes(Method::from_compiled_offset()), /*implicit only*/NULL);
  __ ld(R12_scratch2, in_bytes(Method::from_compiled_offset()), R19_method);
  __ mtctr(R12_scratch2);
  __ bctr();
  masm->flush();

  guarantee(__ pc() <= s->code_end(), "overflowed buffer");

  s->set_exception_points(npe_addr, ame_addr);

  return s;
}

VtableStub* VtableStubs::create_itable_stub(int vtable_index) {
  // PPC port: use fixed size.
  const int code_length = VtableStub::pd_code_size_limit(false);
  VtableStub* s = new (code_length) VtableStub(false, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);
  address start_pc;

#ifndef PRODUCT
  if (CountCompiledCalls) {
    __ load_const(R11_scratch1, SharedRuntime::nof_megamorphic_calls_addr());
    __ lwz(R12_scratch2, 0, R11_scratch1);
    __ addi(R12_scratch2, R12_scratch2, 1);
    __ stw(R12_scratch2, 0, R11_scratch1);
  }
#endif

  assert(VtableStub::receiver_location() == R3_ARG1->as_VMReg(), "receiver expected in R3_ARG1");

  // Entry arguments:
  //  R19_method: Interface
  //  R3_ARG1:    Receiver
  //

  const Register rcvr_klass = R11_scratch1;
  const Register vtable_len = R12_scratch2;
  const Register itable_entry_addr = R21_tmp1;
  const Register itable_interface = R22_tmp2;

  // Get receiver klass.

  // We might implicit NULL fault here.
  address npe_addr = __ pc(); // npe = null pointer exception
  __ null_check(R3_ARG1, oopDesc::klass_offset_in_bytes(), /*implicit only*/NULL);
  __ load_klass(rcvr_klass, R3_ARG1);

  BLOCK_COMMENT("Load start of itable entries into itable_entry.");
  __ lwz(vtable_len, in_bytes(Klass::vtable_length_offset()), rcvr_klass);
  __ slwi(vtable_len, vtable_len, exact_log2(vtableEntry::size_in_bytes()));
  __ add(itable_entry_addr, vtable_len, rcvr_klass);

  // Loop over all itable entries until desired interfaceOop(Rinterface) found.
  BLOCK_COMMENT("Increment itable_entry_addr in loop.");
  const int vtable_base_offset = in_bytes(Klass::vtable_start_offset());
  __ addi(itable_entry_addr, itable_entry_addr, vtable_base_offset + itableOffsetEntry::interface_offset_in_bytes());

  const int itable_offset_search_inc = itableOffsetEntry::size() * wordSize;
  Label search;
  __ bind(search);
  __ ld(itable_interface, 0, itable_entry_addr);

  // Handle IncompatibleClassChangeError in itable stubs.
  // If the entry is NULL then we've reached the end of the table
  // without finding the expected interface, so throw an exception.
  BLOCK_COMMENT("Handle IncompatibleClassChangeError in itable stubs.");
  Label throw_icce;
  __ cmpdi(CCR1, itable_interface, 0);
  __ cmpd(CCR0, itable_interface, R19_method);
  __ addi(itable_entry_addr, itable_entry_addr, itable_offset_search_inc);
  __ beq(CCR1, throw_icce);
  __ bne(CCR0, search);

  // Entry found and itable_entry_addr points to it, get offset of vtable for interface.

  const Register vtable_offset = R12_scratch2;
  const Register itable_method = R11_scratch1;

  const int vtable_offset_offset = (itableOffsetEntry::offset_offset_in_bytes() -
                                    itableOffsetEntry::interface_offset_in_bytes()) -
                                   itable_offset_search_inc;
  __ lwz(vtable_offset, vtable_offset_offset, itable_entry_addr);

  // Compute itableMethodEntry and get method and entry point for compiler.
  const int method_offset = (itableMethodEntry::size() * wordSize * vtable_index) +
    itableMethodEntry::method_offset_in_bytes();

  __ add(itable_method, rcvr_klass, vtable_offset);
  __ ld(R19_method, method_offset, itable_method);

#ifndef PRODUCT
  if (DebugVtables) {
    Label ok;
    __ cmpd(CCR0, R19_method, 0);
    __ bne(CCR0, ok);
    __ stop("method is null", 103);
    __ bind(ok);
  }
#endif

  // If the vtable entry is null, the method is abstract.
  address ame_addr = __ pc(); // ame = abstract method error

  // Must do an explicit check if implicit checks are disabled.
  __ null_check(R19_method, in_bytes(Method::from_compiled_offset()), &throw_icce);
  __ ld(R12_scratch2, in_bytes(Method::from_compiled_offset()), R19_method);
  __ mtctr(R12_scratch2);
  __ bctr();

  // Handle IncompatibleClassChangeError in itable stubs.
  // More detailed error message.
  // We force resolving of the call site by jumping to the "handle
  // wrong method" stub, and so let the interpreter runtime do all the
  // dirty work.
  __ bind(throw_icce);
  __ load_const(R11_scratch1, SharedRuntime::get_handle_wrong_method_stub());
  __ mtctr(R11_scratch1);
  __ bctr();

  masm->flush();

  guarantee(__ pc() <= s->code_end(), "overflowed buffer");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}

int VtableStub::pd_code_size_limit(bool is_vtable_stub) {
  if (TraceJumps || DebugVtables || CountCompiledCalls || VerifyOops) {
    return 1000;
  } else {
    int decode_klass_size = MacroAssembler::instr_size_for_decode_klass_not_null();
    if (is_vtable_stub) {
      return 20 + decode_klass_size +  8 + 8;   // Plain + cOops + Traps + safety
    } else {
      return 96 + decode_klass_size + 12 + 8;   // Plain + cOops + Traps + safety
    }
  }
}

int VtableStub::pd_code_alignment() {
  const unsigned int icache_line_size = 32;
  return icache_line_size;
}
