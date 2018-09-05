/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "interp_masm_sparc.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compiledICHolder.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klassVtable.hpp"
#include "runtime/sharedRuntime.hpp"
#include "vmreg_sparc.inline.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// machine-dependent part of VtableStubs: create vtableStub of correct size and
// initialize its code

#define __ masm->

#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread, oopDesc* receiver, int index);
#endif


// Used by compiler only; may use only caller saved, non-argument registers
VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {
  // Read "A word on VtableStub sizing" in share/code/vtableStubs.hpp for details on stub sizing.
  const int stub_code_length = code_size_limit(true);
  VtableStub* s = new(stub_code_length) VtableStub(true, vtable_index);
  // Can be NULL if there is no free space in the code cache.
  if (s == NULL) {
    return NULL;
  }

  // Count unused bytes in instruction sequences of variable size.
  // We add them to the computed buffer size in order to avoid
  // overflow in subsequently generated stubs.
  address   start_pc;
  int       slop_bytes = 0;
  int       slop_delta = 0;
  const int index_dependent_slop     = ((vtable_index < 512) ? 2 : 0)*BytesPerInstWord; // code size change with transition from 13-bit to 32-bit constant (@index == 512?).

  ResourceMark    rm;
  CodeBuffer      cb(s->entry_point(), stub_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#if (!defined(PRODUCT) && defined(COMPILER2))
  if (CountCompiledCalls) {
    __ inc_counter(SharedRuntime::nof_megamorphic_calls_addr(), G5, G3_scratch);
  }
#endif // PRODUCT

  assert(VtableStub::receiver_location() == O0->as_VMReg(), "receiver expected in O0");

  // get receiver klass
  address npe_addr = __ pc();
  __ load_klass(O0, G3_scratch);

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    // check offset vs vtable length
    __ ld(G3_scratch, in_bytes(Klass::vtable_length_offset()), G5);
    __ cmp_and_br_short(G5, vtable_index*vtableEntry::size(), Assembler::greaterUnsigned, Assembler::pt, L);

    // set generates 8 instructions (worst case), 1 instruction (best case)
    start_pc = __ pc();
    __ set(vtable_index, O2);
    slop_delta  = __ worst_case_insts_for_set()*BytesPerInstWord - (__ pc() - start_pc);
    slop_bytes += slop_delta;
    assert(slop_delta >= 0, "negative slop(%d) encountered, adjust code size estimate!", slop_delta);

    // there is no variance in call_VM() emitted code.
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, bad_compiled_vtable_index), O0, O2);
    __ bind(L);
  }
#endif

  // set Method* (in case of interpreted method), and destination address
  start_pc = __ pc();
  __ lookup_virtual_method(G3_scratch, vtable_index, G5_method);
  // lookup_virtual_method generates 3 instructions (worst case), 1 instruction (best case)
  slop_delta  = 3*BytesPerInstWord - (int)(__ pc() - start_pc);
  slop_bytes += slop_delta;
  assert(slop_delta >= 0, "negative slop(%d) encountered, adjust code size estimate!", slop_delta);

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    __ br_notnull_short(G5_method, Assembler::pt, L);
    __ stop("Vtable entry is ZERO");
    __ bind(L);
  }
#endif

  address ame_addr = __ pc();  // if the vtable entry is null, the method is abstract
                               // NOTE: for vtable dispatches, the vtable entry will never be null.

  __ ld_ptr(G5_method, in_bytes(Method::from_compiled_offset()), G3_scratch);

  // jump to target (either compiled code or c2iadapter)
  __ JMP(G3_scratch, 0);
  // load Method* (in case we call c2iadapter)
  __ delayed()->nop();

  masm->flush();
  slop_bytes += index_dependent_slop; // add'l slop for size variance due to large itable offsets
  bookkeeping(masm, tty, s, npe_addr, ame_addr, true, vtable_index, slop_bytes, index_dependent_slop);

  return s;
}


VtableStub* VtableStubs::create_itable_stub(int itable_index) {
  // Read "A word on VtableStub sizing" in share/code/vtableStubs.hpp for details on stub sizing.
  const int stub_code_length = code_size_limit(false);
  VtableStub* s = new(stub_code_length) VtableStub(false, itable_index);
  // Can be NULL if there is no free space in the code cache.
  if (s == NULL) {
    return NULL;
  }
  // Count unused bytes in instruction sequences of variable size.
  // We add them to the computed buffer size in order to avoid
  // overflow in subsequently generated stubs.
  address   start_pc;
  int       slop_bytes = 0;
  int       slop_delta = 0;
  const int index_dependent_slop     = ((itable_index < 512) ? 2 : 0)*BytesPerInstWord; // code size change with transition from 13-bit to 32-bit constant (@index == 512?).

  ResourceMark    rm;
  CodeBuffer      cb(s->entry_point(), stub_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#if (!defined(PRODUCT) && defined(COMPILER2))
  if (CountCompiledCalls) {
//  Use G3_scratch, G4_scratch as work regs for inc_counter.
//  These are defined before use further down.
    __ inc_counter(SharedRuntime::nof_megamorphic_calls_addr(), G3_scratch, G4_scratch);
  }
#endif // PRODUCT

  Register G3_Klass = G3_scratch;
  Register G5_icholder = G5;  // Passed in as an argument
  Register G4_interface = G4_scratch;
  Label search;

  // Entry arguments:
  //  G5_interface: Interface
  //  O0:           Receiver
  assert(VtableStub::receiver_location() == O0->as_VMReg(), "receiver expected in O0");

  // get receiver klass (also an implicit null-check)
  address npe_addr = __ pc();
  __ load_klass(O0, G3_Klass);

  // Push a new window to get some temp registers.  This chops the head of all
  // my 64-bit %o registers in the LION build, but this is OK because no longs
  // are passed in the %o registers.  Instead, longs are passed in G1 and G4
  // and so those registers are not available here.
  __ save(SP,-frame::register_save_words*wordSize,SP);

  Label    L_no_such_interface;
  Register L5_method = L5;

  start_pc = __ pc();

  // Receiver subtype check against REFC.
  __ ld_ptr(G5_icholder, CompiledICHolder::holder_klass_offset(), G4_interface);
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             G3_Klass, G4_interface, itable_index,
                             // outputs: scan temp. reg1, scan temp. reg2
                             L5_method, L2, L3,
                             L_no_such_interface,
                             /*return_method=*/ false);

  const ptrdiff_t typecheckSize = __ pc() - start_pc;
  start_pc = __ pc();

  // Get Method* and entrypoint for compiler
  __ ld_ptr(G5_icholder, CompiledICHolder::holder_metadata_offset(), G4_interface);
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             G3_Klass, G4_interface, itable_index,
                             // outputs: method, scan temp. reg
                             L5_method, L2, L3,
                             L_no_such_interface);

  const ptrdiff_t lookupSize = __ pc() - start_pc;

  // Reduce "estimate" such that "padding" does not drop below 8.
  // Do not target a left-over number of zero, because a very
  // large vtable or itable offset (> 4K) will require an extra
  // sethi/or pair of instructions.
  // Found typecheck(60) + lookup(72) to exceed previous extimate (32*4).
  const ptrdiff_t estimate = 36*BytesPerInstWord;
  const ptrdiff_t codesize = typecheckSize + lookupSize + index_dependent_slop;
  slop_delta  = (int)(estimate - codesize);
  slop_bytes += slop_delta;
  assert(slop_delta >= 0, "itable #%d: Code size estimate (%d) for lookup_interface_method too small, required: %d", itable_index, (int)estimate, (int)codesize);

#ifndef PRODUCT
  if (DebugVtables) {
    Label L01;
    __ br_notnull_short(L5_method, Assembler::pt, L01);
    __ stop("Method* is null");
    __ bind(L01);
  }
#endif

  // If the following load is through a NULL pointer, we'll take an OS
  // exception that should translate into an AbstractMethodError.  We need the
  // window count to be correct at that time.
  __ restore(L5_method, 0, G5_method);
  // Restore registers *before* the AME point.

  address ame_addr = __ pc();   // if the vtable entry is null, the method is abstract
  __ ld_ptr(G5_method, in_bytes(Method::from_compiled_offset()), G3_scratch);

  // G5_method:  Method*
  // O0:         Receiver
  // G3_scratch: entry point
  __ JMP(G3_scratch, 0);
  __ delayed()->nop();

  __ bind(L_no_such_interface);
  // Handle IncompatibleClassChangeError in itable stubs.
  // More detailed error message.
  // We force resolving of the call site by jumping to the "handle
  // wrong method" stub, and so let the interpreter runtime do all the
  // dirty work.
  AddressLiteral icce(SharedRuntime::get_handle_wrong_method_stub());
  __ jump_to(icce, G3_scratch);
  __ delayed()->restore();

  masm->flush();
  slop_bytes += index_dependent_slop; // add'l slop for size variance due to large itable offsets
  bookkeeping(masm, tty, s, npe_addr, ame_addr, false, itable_index, slop_bytes, index_dependent_slop);

  return s;
}

int VtableStub::pd_code_alignment() {
  // UltraSPARC cache line size is 8 instructions:
  const unsigned int icache_line_size = 32;
  return icache_line_size;
}
