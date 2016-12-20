/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "assembler_arm.inline.hpp"
#include "code/vtableStubs.hpp"
#include "interp_masm_arm.hpp"
#include "memory/resourceArea.hpp"
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
  const Register Rlength = AARCH64_ONLY(R10)  NOT_AARCH64(R5);
  const Register Rscan   = AARCH64_ONLY(R11) NOT_AARCH64(R6);
  const Register tmp     = Rtemp;

  assert_different_registers(Ricklass, Rclass, Rlength, Rscan, tmp);

  // Calculate the start of itable (itable goes after vtable)
  const int scale = exact_log2(vtableEntry::size_in_bytes());
  address npe_addr = __ pc();
  __ load_klass(Rclass, R0);
  __ ldr_s32(Rlength, Address(Rclass, Klass::vtable_length_offset()));

  __ add(Rscan, Rclass, in_bytes(Klass::vtable_start_offset()));
  __ add(Rscan, Rscan, AsmOperand(Rlength, lsl, scale));

  // Search through the itable for an interface equal to incoming Ricklass
  // itable looks like [intface][offset][intface][offset][intface][offset]
  const int entry_size = itableOffsetEntry::size() * HeapWordSize;
  assert(itableOffsetEntry::interface_offset_in_bytes() == 0, "not added for convenience");

  Label loop;
  __ bind(loop);
  __ ldr(tmp, Address(Rscan, entry_size, post_indexed));
#ifdef AARCH64
  Label found;
  __ cmp(tmp, Ricklass);
  __ b(found, eq);
  __ cbnz(tmp, loop);
#else
  __ cmp(tmp, Ricklass);  // set ZF and CF if interface is found
  __ cmn(tmp, 0, ne);     // check if tmp == 0 and clear CF if it is
  __ b(loop, ne);
#endif // AARCH64

  assert(StubRoutines::throw_IncompatibleClassChangeError_entry() != NULL, "Check initialization order");
#ifdef AARCH64
  __ jump(StubRoutines::throw_IncompatibleClassChangeError_entry(), relocInfo::runtime_call_type, tmp);
  __ bind(found);
#else
  // CF == 0 means we reached the end of itable without finding icklass
  __ jump(StubRoutines::throw_IncompatibleClassChangeError_entry(), relocInfo::runtime_call_type, noreg, cc);
#endif // !AARCH64

  // Interface found at previous position of Rscan, now load the method oop
  __ ldr_s32(tmp, Address(Rscan, itableOffsetEntry::offset_offset_in_bytes() - entry_size));
  {
    const int method_offset = itableMethodEntry::size() * HeapWordSize * itable_index +
      itableMethodEntry::method_offset_in_bytes();
    __ add_slow(Rmethod, Rclass, method_offset);
  }
  __ ldr(Rmethod, Address(Rmethod, tmp));

  address ame_addr = __ pc();

#ifdef AARCH64
  __ ldr(tmp, Address(Rmethod, Method::from_compiled_offset()));
  __ br(tmp);
#else
  __ ldr(PC, Address(Rmethod, Method::from_compiled_offset()));
#endif // AARCH64

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
    instr_count = NOT_AARCH64(20) AARCH64_ONLY(20);
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
