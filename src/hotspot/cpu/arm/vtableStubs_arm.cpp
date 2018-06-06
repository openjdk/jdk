/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "assembler_arm.inline.hpp"
#include "code/vtableStubs.hpp"
#include "interp_masm_arm.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compiledICHolder.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klassVtable.hpp"
#include "runtime/sharedRuntime.hpp"
#include "vmreg_arm.inline.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// machine-dependent part of VtableStubs: create VtableStub of correct size and
// initialize its code

#define __ masm->

#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread, oop receiver, int index);
#endif

VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {
  const int code_length = VtableStub::pd_code_size_limit(true);
  VtableStub* s = new(code_length) VtableStub(true, vtable_index);
  // Can be NULL if there is no free space in the code cache.
  if (s == NULL) {
    return NULL;
  }

  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

  assert(VtableStub::receiver_location() == R0->as_VMReg(), "receiver expected in R0");

  const Register tmp = Rtemp; // Rtemp OK, should be free at call sites

  address npe_addr = __ pc();
  __ load_klass(tmp, R0);

  {
  int entry_offset = in_bytes(Klass::vtable_start_offset()) + vtable_index * vtableEntry::size_in_bytes();
  int method_offset = vtableEntry::method_offset_in_bytes() + entry_offset;

  assert ((method_offset & (wordSize - 1)) == 0, "offset should be aligned");
  int offset_mask = AARCH64_ONLY(0xfff << LogBytesPerWord) NOT_AARCH64(0xfff);
  if (method_offset & ~offset_mask) {
    __ add(tmp, tmp, method_offset & ~offset_mask);
  }
  __ ldr(Rmethod, Address(tmp, method_offset & offset_mask));
  }

  address ame_addr = __ pc();
#ifdef AARCH64
  __ ldr(tmp, Address(Rmethod, Method::from_compiled_offset()));
  __ br(tmp);
#else
  __ ldr(PC, Address(Rmethod, Method::from_compiled_offset()));
#endif // AARCH64

  masm->flush();

  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    tty->print_cr("vtable #%d at " PTR_FORMAT "[%d] left over: %d",
                  vtable_index, p2i(s->entry_point()),
                  (int)(s->code_end() - s->entry_point()),
                  (int)(s->code_end() - __ pc()));
  }
  guarantee(__ pc() <= s->code_end(), "overflowed buffer");
  // FIXME ARM: need correct 'slop' - below is x86 code
  // shut the door on sizing bugs
  //int slop = 8;  // 32-bit offset is this much larger than a 13-bit one
  //assert(vtable_index > 10 || __ pc() + slop <= s->code_end(), "room for 32-bit offset");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}

VtableStub* VtableStubs::create_itable_stub(int itable_index) {
  const int code_length = VtableStub::pd_code_size_limit(false);
  VtableStub* s = new(code_length) VtableStub(false, itable_index);
  // Can be NULL if there is no free space in the code cache.
  if (s == NULL) {
    return NULL;
  }

  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

  assert(VtableStub::receiver_location() == R0->as_VMReg(), "receiver expected in R0");

  // R0-R3 / R0-R7 registers hold the arguments and cannot be spoiled
  const Register Rclass  = AARCH64_ONLY(R9)  NOT_AARCH64(R4);
  const Register Rintf   = AARCH64_ONLY(R10) NOT_AARCH64(R5);
  const Register Rscan   = AARCH64_ONLY(R11) NOT_AARCH64(R6);

  assert_different_registers(Ricklass, Rclass, Rintf, Rscan, Rtemp);

  // Calculate the start of itable (itable goes after vtable)
  const int scale = exact_log2(vtableEntry::size_in_bytes());
  address npe_addr = __ pc();
  __ load_klass(Rclass, R0);

  Label L_no_such_interface;

  // Receiver subtype check against REFC.
  __ ldr(Rintf, Address(Ricklass, CompiledICHolder::holder_klass_offset()));
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             Rclass, Rintf, noreg,
                             // outputs: temp reg1, temp reg2
                             noreg, Rscan, Rtemp,
                             L_no_such_interface);

  // Get Method* and entry point for compiler
  __ ldr(Rintf, Address(Ricklass, CompiledICHolder::holder_metadata_offset()));
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             Rclass, Rintf, itable_index,
                             // outputs: temp reg1, temp reg2, temp reg3
                             Rmethod, Rscan, Rtemp,
                             L_no_such_interface);

  address ame_addr = __ pc();

#ifdef AARCH64
  __ ldr(Rtemp, Address(Rmethod, Method::from_compiled_offset()));
  __ br(Rtemp);
#else
  __ ldr(PC, Address(Rmethod, Method::from_compiled_offset()));
#endif // AARCH64

  __ bind(L_no_such_interface);

  // Handle IncompatibleClassChangeError in itable stubs.
  // More detailed error message.
  // We force resolving of the call site by jumping to the "handle
  // wrong method" stub, and so let the interpreter runtime do all the
  // dirty work.
  assert(SharedRuntime::get_handle_wrong_method_stub() != NULL, "check initialization order");
  __ jump(SharedRuntime::get_handle_wrong_method_stub(), relocInfo::runtime_call_type, Rtemp);

  masm->flush();

  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    tty->print_cr("itable #%d at " PTR_FORMAT "[%d] left over: %d",
                  itable_index, p2i(s->entry_point()),
                  (int)(s->code_end() - s->entry_point()),
                  (int)(s->code_end() - __ pc()));
  }
  guarantee(__ pc() <= s->code_end(), "overflowed buffer");
  // FIXME ARM: need correct 'slop' - below is x86 code
  // shut the door on sizing bugs
  //int slop = 8;  // 32-bit offset is this much larger than a 13-bit one
  //assert(itable_index > 10 || __ pc() + slop <= s->code_end(), "room for 32-bit offset");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}

int VtableStub::pd_code_size_limit(bool is_vtable_stub) {
  int instr_count;

  if (is_vtable_stub) {
    // vtable stub size
    instr_count = NOT_AARCH64(4) AARCH64_ONLY(5);
  } else {
    // itable stub size
    instr_count = NOT_AARCH64(31) AARCH64_ONLY(31);
  }

#ifdef AARCH64
  if (UseCompressedClassPointers) {
    instr_count += MacroAssembler::instr_count_for_decode_klass_not_null();
  }
#endif // AARCH64

  return instr_count * Assembler::InstructionSize;
}

int VtableStub::pd_code_alignment() {
  return 8;
}
