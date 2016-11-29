/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 SAP SE. All rights reserved.
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
#include "interp_masm_s390.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klassVtable.hpp"
#include "runtime/sharedRuntime.hpp"
#include "vmreg_s390.inline.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// Machine-dependent part of VtableStubs: create vtableStub of correct
// size and initialize its code.

#define __ masm->

#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread, oop receiver, int index);
#endif

// Used by compiler only; may use only caller saved, non-argument registers.
VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {

  const int   code_length = VtableStub::pd_code_size_limit(true);
  VtableStub *s = new(code_length) VtableStub(true, vtable_index);
  if (s == NULL) { // Indicates OOM In the code cache.
    return NULL;
  }

  ResourceMark    rm;
  CodeBuffer      cb(s->entry_point(), code_length);
  MacroAssembler *masm = new MacroAssembler(&cb);
  address start_pc;
  int     padding_bytes = 0;

#if (!defined(PRODUCT) && defined(COMPILER2))
  if (CountCompiledCalls) {
    // Count unused bytes
    //                  worst case             actual size
    padding_bytes += __ load_const_size() - __ load_const_optimized_rtn_len(Z_R1_scratch, (long)SharedRuntime::nof_megamorphic_calls_addr(), true);

    // Use generic emitter for direct memory increment.
    // Abuse Z_method as scratch register for generic emitter.
    // It is loaded further down anyway before it is first used.
    __ add2mem_32(Address(Z_R1_scratch), 1, Z_method);
  }
#endif

  assert(VtableStub::receiver_location() == Z_R2->as_VMReg(), "receiver expected in Z_ARG1");

  // Get receiver klass.
  // Must do an explicit check if implicit checks are disabled.
  address npe_addr = __ pc(); // npe == NULL ptr exception
  __ null_check(Z_ARG1, Z_R1_scratch, oopDesc::klass_offset_in_bytes());
  const Register rcvr_klass = Z_R1_scratch;
  __ load_klass(rcvr_klass, Z_ARG1);

  // Set method (in case of interpreted method), and destination address.
  int entry_offset = in_bytes(InstanceKlass::vtable_start_offset()) +
                     vtable_index * vtableEntry::size_in_bytes();

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    // Check offset vs vtable length.
    const Register vtable_idx = Z_R0_scratch;

    // Count unused bytes.
    //                  worst case             actual size
    padding_bytes += __ load_const_size() - __ load_const_optimized_rtn_len(vtable_idx, vtable_index*vtableEntry::size_in_bytes(), true);

    assert(Immediate::is_uimm12(in_bytes(InstanceKlass::vtable_length_offset())), "disp to large");
    __ z_cl(vtable_idx, in_bytes(InstanceKlass::vtable_length_offset()), rcvr_klass);
    __ z_brl(L);
    __ z_lghi(Z_ARG3, vtable_index);  // Debug code, don't optimize.
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, bad_compiled_vtable_index), Z_ARG1, Z_ARG3, false);
    // Count unused bytes (assume worst case here).
    padding_bytes += 12;
    __ bind(L);
  }
#endif

  int v_off = entry_offset + vtableEntry::method_offset_in_bytes();

  // Duplicate safety code from enc_class Java_Dynamic_Call_dynTOC.
  if (Displacement::is_validDisp(v_off)) {
    __ z_lg(Z_method/*method oop*/, v_off, rcvr_klass/*class oop*/);
    // Account for the load_const in the else path.
    padding_bytes += __ load_const_size();
  } else {
    // Worse case, offset does not fit in displacement field.
    __ load_const(Z_method, v_off); // Z_method temporarily holds the offset value.
    __ z_lg(Z_method/*method oop*/, 0, Z_method/*method offset*/, rcvr_klass/*class oop*/);
  }

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    __ z_ltgr(Z_method, Z_method);
    __ z_brne(L);
    __ stop("Vtable entry is ZERO",102);
    __ bind(L);
  }
#endif

  address ame_addr = __ pc(); // ame = abstract method error

  // Must do an explicit check if implicit checks are disabled.
  __ null_check(Z_method, Z_R1_scratch, in_bytes(Method::from_compiled_offset()));
  __ z_lg(Z_R1_scratch, in_bytes(Method::from_compiled_offset()), Z_method);
  __ z_br(Z_R1_scratch);

  masm->flush();

  s->set_exception_points(npe_addr, ame_addr);

  return s;
}

VtableStub* VtableStubs::create_itable_stub(int vtable_index) {
  const int   code_length = VtableStub::pd_code_size_limit(false);
  VtableStub *s = new(code_length) VtableStub(false, vtable_index);
  if (s == NULL) { // Indicates OOM in the code cache.
    return NULL;
  }

  ResourceMark    rm;
  CodeBuffer      cb(s->entry_point(), code_length);
  MacroAssembler *masm = new MacroAssembler(&cb);
  address start_pc;
  int     padding_bytes = 0;

#if (!defined(PRODUCT) && defined(COMPILER2))
  if (CountCompiledCalls) {
    // Count unused bytes
    //                  worst case             actual size
    padding_bytes += __ load_const_size() - __ load_const_optimized_rtn_len(Z_R1_scratch, (long)SharedRuntime::nof_megamorphic_calls_addr(), true);

    // Use generic emitter for direct memory increment.
    // Use Z_tmp_1 as scratch register for generic emitter.
    __ add2mem_32((Z_R1_scratch), 1, Z_tmp_1);
  }
#endif

  assert(VtableStub::receiver_location() == Z_R2->as_VMReg(), "receiver expected in Z_ARG1");

  // Entry arguments:
  //  Z_method: Interface
  //  Z_ARG1:   Receiver
  const Register rcvr_klass = Z_tmp_1;    // Used to compute itable_entry_addr.
                                          // Use extra reg to avoid re-load.
  const Register vtable_len = Z_tmp_2;    // Used to compute itable_entry_addr.
  const Register itable_entry_addr = Z_R1_scratch;
  const Register itable_interface  = Z_R0_scratch;

  // Get receiver klass.
  // Must do an explicit check if implicit checks are disabled.
  address npe_addr = __ pc(); // npe == NULL ptr exception
  __ null_check(Z_ARG1, Z_R1_scratch, oopDesc::klass_offset_in_bytes());
  __ load_klass(rcvr_klass, Z_ARG1);

  // Load start of itable entries into itable_entry.
  __ z_llgf(vtable_len, Address(rcvr_klass, InstanceKlass::vtable_length_offset()));
  __ z_sllg(vtable_len, vtable_len, exact_log2(vtableEntry::size_in_bytes()));

  // Loop over all itable entries until desired interfaceOop(Rinterface) found.
  const int vtable_base_offset = in_bytes(InstanceKlass::vtable_start_offset());
  // Count unused bytes.
  start_pc = __ pc();
  __ add2reg_with_index(itable_entry_addr, vtable_base_offset + itableOffsetEntry::interface_offset_in_bytes(), rcvr_klass, vtable_len);
  padding_bytes += 20 - (__ pc() - start_pc);

  const int itable_offset_search_inc = itableOffsetEntry::size() * wordSize;
  Label search;
  __ bind(search);

  // Handle IncompatibleClassChangeError in itable stubs.
  // If the entry is NULL then we've reached the end of the table
  // without finding the expected interface, so throw an exception.
  NearLabel   throw_icce;
  __ load_and_test_long(itable_interface, Address(itable_entry_addr));
  __ z_bre(throw_icce); // Throw the exception out-of-line.
  // Count unused bytes.
  start_pc = __ pc();
  __ add2reg(itable_entry_addr, itable_offset_search_inc);
  padding_bytes += 20 - (__ pc() - start_pc);
  __ z_cgr(itable_interface, Z_method);
  __ z_brne(search);

  // Entry found. Itable_entry_addr points to the subsequent entry (itable_offset_search_inc too far).
  // Get offset of vtable for interface.

  const Register vtable_offset = Z_R1_scratch;
  const Register itable_method = rcvr_klass;   // Calculated before.

  const int vtable_offset_offset = (itableOffsetEntry::offset_offset_in_bytes() -
                                    itableOffsetEntry::interface_offset_in_bytes()) -
                                   itable_offset_search_inc;
  __ z_llgf(vtable_offset, vtable_offset_offset, itable_entry_addr);

  // Compute itableMethodEntry and get method and entry point for compiler.
  const int method_offset = (itableMethodEntry::size() * wordSize * vtable_index) +
                            itableMethodEntry::method_offset_in_bytes();

  __ z_lg(Z_method, method_offset, vtable_offset, itable_method);

#ifndef PRODUCT
  if (DebugVtables) {
    Label ok1;
    __ z_ltgr(Z_method, Z_method);
    __ z_brne(ok1);
    __ stop("method is null",103);
    __ bind(ok1);
  }
#endif

  address ame_addr = __ pc();
  // Must do an explicit check if implicit checks are disabled.
  if (!ImplicitNullChecks) {
    __ compare64_and_branch(Z_method, (intptr_t) 0, Assembler::bcondEqual, throw_icce);
  }
  __ z_lg(Z_R1_scratch, in_bytes(Method::from_compiled_offset()), Z_method);
  __ z_br(Z_R1_scratch);

  // Handle IncompatibleClassChangeError in itable stubs.
  __ bind(throw_icce);
  // Count unused bytes
  //                  worst case          actual size
  // We force resolving of the call site by jumping to
  // the "handle wrong method" stub, and so let the
  // interpreter runtime do all the dirty work.
  padding_bytes += __ load_const_size() - __ load_const_optimized_rtn_len(Z_R1_scratch, (long)SharedRuntime::get_handle_wrong_method_stub(), true);
  __ z_br(Z_R1_scratch);

  masm->flush();

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}

// In order to tune these parameters, run the JVM with VM options
// +PrintMiscellaneous and +WizardMode to see information about
// actual itable stubs. Run it with -Xmx31G -XX:+UseCompressedOops.
int VtableStub::pd_code_size_limit(bool is_vtable_stub) {
  int size = DebugVtables ? 216 : 0;
  if (CountCompiledCalls) {
    size += 6 * 4;
  }
  if (is_vtable_stub) {
    size += 52;
  } else {
    size += 104;
  }
  if (Universe::narrow_klass_base() != NULL) {
    size += 16; // A guess.
  }
  return size;
}

int VtableStub::pd_code_alignment() {
  const unsigned int icache_line_size = 32;
  return icache_line_size;
}
