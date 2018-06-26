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
#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "ci/ciEnv.hpp"
#include "code/nativeInst.hpp"
#include "compiler/disassembler.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "oops/accessDecorators.hpp"
#include "oops/klass.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/macros.hpp"

// Implementation of AddressLiteral

void AddressLiteral::set_rspec(relocInfo::relocType rtype) {
  switch (rtype) {
  case relocInfo::oop_type:
    // Oops are a special case. Normally they would be their own section
    // but in cases like icBuffer they are literals in the code stream that
    // we don't have a section for. We use none so that we get a literal address
    // which is always patchable.
    break;
  case relocInfo::external_word_type:
    _rspec = external_word_Relocation::spec(_target);
    break;
  case relocInfo::internal_word_type:
    _rspec = internal_word_Relocation::spec(_target);
    break;
  case relocInfo::opt_virtual_call_type:
    _rspec = opt_virtual_call_Relocation::spec();
    break;
  case relocInfo::static_call_type:
    _rspec = static_call_Relocation::spec();
    break;
  case relocInfo::runtime_call_type:
    _rspec = runtime_call_Relocation::spec();
    break;
  case relocInfo::poll_type:
  case relocInfo::poll_return_type:
    _rspec = Relocation::spec_simple(rtype);
    break;
  case relocInfo::none:
    break;
  default:
    ShouldNotReachHere();
    break;
  }
}

// Initially added to the Assembler interface as a pure virtual:
//   RegisterConstant delayed_value(..)
// for:
//   6812678 macro assembler needs delayed binding of a few constants (for 6655638)
// this was subsequently modified to its present name and return type
RegisterOrConstant MacroAssembler::delayed_value_impl(intptr_t* delayed_value_addr,
                                                      Register tmp,
                                                      int offset) {
  ShouldNotReachHere();
  return RegisterOrConstant(-1);
}


#ifdef AARCH64
// Note: ARM32 version is OS dependent
void MacroAssembler::breakpoint(AsmCondition cond) {
  if (cond == al) {
    brk();
  } else {
    Label L;
    b(L, inverse(cond));
    brk();
    bind(L);
  }
}
#endif // AARCH64


// virtual method calling
void MacroAssembler::lookup_virtual_method(Register recv_klass,
                                           Register vtable_index,
                                           Register method_result) {
  const int base_offset = in_bytes(Klass::vtable_start_offset()) + vtableEntry::method_offset_in_bytes();
  assert(vtableEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");
  add(recv_klass, recv_klass, AsmOperand(vtable_index, lsl, LogBytesPerWord));
  ldr(method_result, Address(recv_klass, base_offset));
}


// Simplified, combined version, good for typical uses.
// Falls through on failure.
void MacroAssembler::check_klass_subtype(Register sub_klass,
                                         Register super_klass,
                                         Register temp_reg,
                                         Register temp_reg2,
                                         Register temp_reg3,
                                         Label& L_success) {
  Label L_failure;
  check_klass_subtype_fast_path(sub_klass, super_klass, temp_reg, temp_reg2, &L_success, &L_failure, NULL);
  check_klass_subtype_slow_path(sub_klass, super_klass, temp_reg, temp_reg2, temp_reg3, &L_success, NULL);
  bind(L_failure);
};

void MacroAssembler::check_klass_subtype_fast_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Register temp_reg2,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   Label* L_slow_path) {

  assert_different_registers(sub_klass, super_klass, temp_reg, temp_reg2, noreg);
  const Register super_check_offset = temp_reg2;

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == NULL)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == NULL)   { L_failure   = &L_fallthrough; label_nulls++; }
  if (L_slow_path == NULL) { L_slow_path = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one NULL in the batch");

  int sc_offset = in_bytes(Klass::secondary_super_cache_offset());
  int sco_offset = in_bytes(Klass::super_check_offset_offset());
  Address super_check_offset_addr(super_klass, sco_offset);

  // If the pointers are equal, we are done (e.g., String[] elements).
  // This self-check enables sharing of secondary supertype arrays among
  // non-primary types such as array-of-interface.  Otherwise, each such
  // type would need its own customized SSA.
  // We move this check to the front of the fast path because many
  // type checks are in fact trivially successful in this manner,
  // so we get a nicely predicted branch right at the start of the check.
  cmp(sub_klass, super_klass);
  b(*L_success, eq);

  // Check the supertype display:
  ldr_u32(super_check_offset, super_check_offset_addr);

  Address super_check_addr(sub_klass, super_check_offset);
  ldr(temp_reg, super_check_addr);
  cmp(super_klass, temp_reg); // load displayed supertype

  // This check has worked decisively for primary supers.
  // Secondary supers are sought in the super_cache ('super_cache_addr').
  // (Secondary supers are interfaces and very deeply nested subtypes.)
  // This works in the same check above because of a tricky aliasing
  // between the super_cache and the primary super display elements.
  // (The 'super_check_addr' can address either, as the case requires.)
  // Note that the cache is updated below if it does not help us find
  // what we need immediately.
  // So if it was a primary super, we can just fail immediately.
  // Otherwise, it's the slow path for us (no success at this point).

  b(*L_success, eq);
  cmp_32(super_check_offset, sc_offset);
  if (L_failure == &L_fallthrough) {
    b(*L_slow_path, eq);
  } else {
    b(*L_failure, ne);
    if (L_slow_path != &L_fallthrough) {
      b(*L_slow_path);
    }
  }

  bind(L_fallthrough);
}


void MacroAssembler::check_klass_subtype_slow_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Register temp2_reg,
                                                   Register temp3_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   bool set_cond_codes) {
#ifdef AARCH64
  NOT_IMPLEMENTED();
#else
  // Note: if used by code that expects a register to be 0 on success,
  // this register must be temp_reg and set_cond_codes must be true

  Register saved_reg = noreg;

  // get additional tmp registers
  if (temp3_reg == noreg) {
    saved_reg = temp3_reg = LR;
    push(saved_reg);
  }

  assert(temp2_reg != noreg, "need all the temporary registers");
  assert_different_registers(sub_klass, super_klass, temp_reg, temp2_reg, temp3_reg);

  Register cmp_temp = temp_reg;
  Register scan_temp = temp3_reg;
  Register count_temp = temp2_reg;

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == NULL)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == NULL)   { L_failure   = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one NULL in the batch");

  // a couple of useful fields in sub_klass:
  int ss_offset = in_bytes(Klass::secondary_supers_offset());
  int sc_offset = in_bytes(Klass::secondary_super_cache_offset());
  Address secondary_supers_addr(sub_klass, ss_offset);
  Address super_cache_addr(     sub_klass, sc_offset);

#ifndef PRODUCT
  inc_counter((address)&SharedRuntime::_partial_subtype_ctr, scan_temp, count_temp);
#endif

  // We will consult the secondary-super array.
  ldr(scan_temp, Address(sub_klass, ss_offset));

  assert(! UseCompressedOops, "search_key must be the compressed super_klass");
  // else search_key is the
  Register search_key = super_klass;

  // Load the array length.
  ldr(count_temp, Address(scan_temp, Array<Klass*>::length_offset_in_bytes()));
  add(scan_temp, scan_temp, Array<Klass*>::base_offset_in_bytes());

  add(count_temp, count_temp, 1);

  Label L_loop, L_setnz_and_fail, L_fail;

  // Top of search loop
  bind(L_loop);
  // Notes:
  //  scan_temp starts at the array elements
  //  count_temp is 1+size
  subs(count_temp, count_temp, 1);
  if ((L_failure != &L_fallthrough) && (! set_cond_codes) && (saved_reg == noreg)) {
    // direct jump to L_failure if failed and no cleanup needed
    b(*L_failure, eq); // not found and
  } else {
    b(L_fail, eq); // not found in the array
  }

  // Load next super to check
  // In the array of super classes elements are pointer sized.
  int element_size = wordSize;
  ldr(cmp_temp, Address(scan_temp, element_size, post_indexed));

  // Look for Rsuper_klass on Rsub_klass's secondary super-class-overflow list
  subs(cmp_temp, cmp_temp, search_key);

  // A miss means we are NOT a subtype and need to keep looping
  b(L_loop, ne);

  // Falling out the bottom means we found a hit; we ARE a subtype

  // Note: temp_reg/cmp_temp is already 0 and flag Z is set

  // Success.  Cache the super we found and proceed in triumph.
  str(super_klass, Address(sub_klass, sc_offset));

  if (saved_reg != noreg) {
    // Return success
    pop(saved_reg);
  }

  b(*L_success);

  bind(L_fail);
  // Note1: check "b(*L_failure, eq)" above if adding extra instructions here
  if (set_cond_codes) {
    movs(temp_reg, sub_klass); // clears Z and sets temp_reg to non-0 if needed
  }
  if (saved_reg != noreg) {
    pop(saved_reg);
  }
  if (L_failure != &L_fallthrough) {
    b(*L_failure);
  }

  bind(L_fallthrough);
#endif
}

// Returns address of receiver parameter, using tmp as base register. tmp and params_count can be the same.
Address MacroAssembler::receiver_argument_address(Register params_base, Register params_count, Register tmp) {
  assert_different_registers(params_base, params_count);
  add(tmp, params_base, AsmOperand(params_count, lsl, Interpreter::logStackElementSize));
  return Address(tmp, -Interpreter::stackElementSize);
}


void MacroAssembler::align(int modulus) {
  while (offset() % modulus != 0) {
    nop();
  }
}

int MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                        Register last_java_fp,
                                        bool save_last_java_pc,
                                        Register tmp) {
  int pc_offset;
  if (last_java_fp != noreg) {
    // optional
    str(last_java_fp, Address(Rthread, JavaThread::last_Java_fp_offset()));
    _fp_saved = true;
  } else {
    _fp_saved = false;
  }
  if (AARCH64_ONLY(true) NOT_AARCH64(save_last_java_pc)) { // optional on 32-bit ARM
#ifdef AARCH64
    pc_offset = mov_pc_to(tmp);
    str(tmp, Address(Rthread, JavaThread::last_Java_pc_offset()));
#else
    str(PC, Address(Rthread, JavaThread::last_Java_pc_offset()));
    pc_offset = offset() + VM_Version::stored_pc_adjustment();
#endif
    _pc_saved = true;
  } else {
    _pc_saved = false;
    pc_offset = -1;
  }
  // According to comment in javaFrameAnchorm SP must be saved last, so that other
  // entries are valid when SP is set.

  // However, this is probably not a strong constrainst since for instance PC is
  // sometimes read from the stack at SP... but is pushed later (by the call). Hence,
  // we now write the fields in the expected order but we have not added a StoreStore
  // barrier.

  // XXX: if the ordering is really important, PC should always be saved (without forgetting
  // to update oop_map offsets) and a StoreStore barrier might be needed.

  if (last_java_sp == noreg) {
    last_java_sp = SP; // always saved
  }
#ifdef AARCH64
  if (last_java_sp == SP) {
    mov(tmp, SP);
    str(tmp, Address(Rthread, JavaThread::last_Java_sp_offset()));
  } else {
    str(last_java_sp, Address(Rthread, JavaThread::last_Java_sp_offset()));
  }
#else
  str(last_java_sp, Address(Rthread, JavaThread::last_Java_sp_offset()));
#endif

  return pc_offset; // for oopmaps
}

void MacroAssembler::reset_last_Java_frame(Register tmp) {
  const Register Rzero = zero_register(tmp);
  str(Rzero, Address(Rthread, JavaThread::last_Java_sp_offset()));
  if (_fp_saved) {
    str(Rzero, Address(Rthread, JavaThread::last_Java_fp_offset()));
  }
  if (_pc_saved) {
    str(Rzero, Address(Rthread, JavaThread::last_Java_pc_offset()));
  }
}


// Implementation of call_VM versions

void MacroAssembler::call_VM_leaf_helper(address entry_point, int number_of_arguments) {
  assert(number_of_arguments >= 0, "cannot have negative number of arguments");
  assert(number_of_arguments <= 4, "cannot have more than 4 arguments");

#ifndef AARCH64
  // Safer to save R9 here since callers may have been written
  // assuming R9 survives. This is suboptimal but is not worth
  // optimizing for the few platforms where R9 is scratched.
  push(RegisterSet(R4) | R9ifScratched);
  mov(R4, SP);
  bic(SP, SP, StackAlignmentInBytes - 1);
#endif // AARCH64
  call(entry_point, relocInfo::runtime_call_type);
#ifndef AARCH64
  mov(SP, R4);
  pop(RegisterSet(R4) | R9ifScratched);
#endif // AARCH64
}


void MacroAssembler::call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions) {
  assert(number_of_arguments >= 0, "cannot have negative number of arguments");
  assert(number_of_arguments <= 3, "cannot have more than 3 arguments");

  const Register tmp = Rtemp;
  assert_different_registers(oop_result, tmp);

  set_last_Java_frame(SP, FP, true, tmp);

#ifdef ASSERT
  AARCH64_ONLY(if (UseCompressedOops || UseCompressedClassPointers) { verify_heapbase("call_VM_helper: heap base corrupted?"); });
#endif // ASSERT

#ifndef AARCH64
#if R9_IS_SCRATCHED
  // Safer to save R9 here since callers may have been written
  // assuming R9 survives. This is suboptimal but is not worth
  // optimizing for the few platforms where R9 is scratched.

  // Note: cannot save R9 above the saved SP (some calls expect for
  // instance the Java stack top at the saved SP)
  // => once saved (with set_last_Java_frame), decrease SP before rounding to
  // ensure the slot at SP will be free for R9).
  sub(SP, SP, 4);
  bic(SP, SP, StackAlignmentInBytes - 1);
  str(R9, Address(SP, 0));
#else
  bic(SP, SP, StackAlignmentInBytes - 1);
#endif // R9_IS_SCRATCHED
#endif

  mov(R0, Rthread);
  call(entry_point, relocInfo::runtime_call_type);

#ifndef AARCH64
#if R9_IS_SCRATCHED
  ldr(R9, Address(SP, 0));
#endif
  ldr(SP, Address(Rthread, JavaThread::last_Java_sp_offset()));
#endif

  reset_last_Java_frame(tmp);

  // C++ interp handles this in the interpreter
  check_and_handle_popframe();
  check_and_handle_earlyret();

  if (check_exceptions) {
    // check for pending exceptions
    ldr(tmp, Address(Rthread, Thread::pending_exception_offset()));
#ifdef AARCH64
    Label L;
    cbz(tmp, L);
    mov_pc_to(Rexception_pc);
    b(StubRoutines::forward_exception_entry());
    bind(L);
#else
    cmp(tmp, 0);
    mov(Rexception_pc, PC, ne);
    b(StubRoutines::forward_exception_entry(), ne);
#endif // AARCH64
  }

  // get oop result if there is one and reset the value in the thread
  if (oop_result->is_valid()) {
    get_vm_result(oop_result, tmp);
  }
}

void MacroAssembler::call_VM(Register oop_result, address entry_point, bool check_exceptions) {
  call_VM_helper(oop_result, entry_point, 0, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, bool check_exceptions) {
  assert (arg_1 == R1, "fixed register for arg_1");
  call_VM_helper(oop_result, entry_point, 1, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, bool check_exceptions) {
  assert (arg_1 == R1, "fixed register for arg_1");
  assert (arg_2 == R2, "fixed register for arg_2");
  call_VM_helper(oop_result, entry_point, 2, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions) {
  assert (arg_1 == R1, "fixed register for arg_1");
  assert (arg_2 == R2, "fixed register for arg_2");
  assert (arg_3 == R3, "fixed register for arg_3");
  call_VM_helper(oop_result, entry_point, 3, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, int number_of_arguments, bool check_exceptions) {
  // Not used on ARM
  Unimplemented();
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, bool check_exceptions) {
  // Not used on ARM
  Unimplemented();
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, bool check_exceptions) {
// Not used on ARM
  Unimplemented();
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions) {
  // Not used on ARM
  Unimplemented();
}

// Raw call, without saving/restoring registers, exception handling, etc.
// Mainly used from various stubs.
void MacroAssembler::call_VM(address entry_point, bool save_R9_if_scratched) {
  const Register tmp = Rtemp; // Rtemp free since scratched by call
  set_last_Java_frame(SP, FP, true, tmp);
#if R9_IS_SCRATCHED
  if (save_R9_if_scratched) {
    // Note: Saving also R10 for alignment.
    push(RegisterSet(R9, R10));
  }
#endif
  mov(R0, Rthread);
  call(entry_point, relocInfo::runtime_call_type);
#if R9_IS_SCRATCHED
  if (save_R9_if_scratched) {
    pop(RegisterSet(R9, R10));
  }
#endif
  reset_last_Java_frame(tmp);
}

void MacroAssembler::call_VM_leaf(address entry_point) {
  call_VM_leaf_helper(entry_point, 0);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1) {
  assert (arg_1 == R0, "fixed register for arg_1");
  call_VM_leaf_helper(entry_point, 1);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1, Register arg_2) {
  assert (arg_1 == R0, "fixed register for arg_1");
  assert (arg_2 == R1, "fixed register for arg_2");
  call_VM_leaf_helper(entry_point, 2);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1, Register arg_2, Register arg_3) {
  assert (arg_1 == R0, "fixed register for arg_1");
  assert (arg_2 == R1, "fixed register for arg_2");
  assert (arg_3 == R2, "fixed register for arg_3");
  call_VM_leaf_helper(entry_point, 3);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1, Register arg_2, Register arg_3, Register arg_4) {
  assert (arg_1 == R0, "fixed register for arg_1");
  assert (arg_2 == R1, "fixed register for arg_2");
  assert (arg_3 == R2, "fixed register for arg_3");
  assert (arg_4 == R3, "fixed register for arg_4");
  call_VM_leaf_helper(entry_point, 4);
}

void MacroAssembler::get_vm_result(Register oop_result, Register tmp) {
  assert_different_registers(oop_result, tmp);
  ldr(oop_result, Address(Rthread, JavaThread::vm_result_offset()));
  str(zero_register(tmp), Address(Rthread, JavaThread::vm_result_offset()));
  verify_oop(oop_result);
}

void MacroAssembler::get_vm_result_2(Register metadata_result, Register tmp) {
  assert_different_registers(metadata_result, tmp);
  ldr(metadata_result, Address(Rthread, JavaThread::vm_result_2_offset()));
  str(zero_register(tmp), Address(Rthread, JavaThread::vm_result_2_offset()));
}

void MacroAssembler::add_rc(Register dst, Register arg1, RegisterOrConstant arg2) {
  if (arg2.is_register()) {
    add(dst, arg1, arg2.as_register());
  } else {
    add(dst, arg1, arg2.as_constant());
  }
}

void MacroAssembler::add_slow(Register rd, Register rn, int c) {
#ifdef AARCH64
  if (c == 0) {
    if (rd != rn) {
      mov(rd, rn);
    }
    return;
  }
  if (c < 0) {
    sub_slow(rd, rn, -c);
    return;
  }
  if (c > right_n_bits(24)) {
    guarantee(rd != rn, "no large add_slow with only one register");
    mov_slow(rd, c);
    add(rd, rn, rd);
  } else {
    int lo = c & right_n_bits(12);
    int hi = (c >> 12) & right_n_bits(12);
    if (lo != 0) {
      add(rd, rn, lo, lsl0);
    }
    if (hi != 0) {
      add(rd, (lo == 0) ? rn : rd, hi, lsl12);
    }
  }
#else
  // This function is used in compiler for handling large frame offsets
  if ((c < 0) && (((-c) & ~0x3fc) == 0)) {
    return sub(rd, rn, (-c));
  }
  int low = c & 0x3fc;
  if (low != 0) {
    add(rd, rn, low);
    rn = rd;
  }
  if (c & ~0x3fc) {
    assert(AsmOperand::is_rotated_imm(c & ~0x3fc), "unsupported add_slow offset %d", c);
    add(rd, rn, c & ~0x3fc);
  } else if (rd != rn) {
    assert(c == 0, "");
    mov(rd, rn); // need to generate at least one move!
  }
#endif // AARCH64
}

void MacroAssembler::sub_slow(Register rd, Register rn, int c) {
#ifdef AARCH64
  if (c <= 0) {
    add_slow(rd, rn, -c);
    return;
  }
  if (c > right_n_bits(24)) {
    guarantee(rd != rn, "no large sub_slow with only one register");
    mov_slow(rd, c);
    sub(rd, rn, rd);
  } else {
    int lo = c & right_n_bits(12);
    int hi = (c >> 12) & right_n_bits(12);
    if (lo != 0) {
      sub(rd, rn, lo, lsl0);
    }
    if (hi != 0) {
      sub(rd, (lo == 0) ? rn : rd, hi, lsl12);
    }
  }
#else
  // This function is used in compiler for handling large frame offsets
  if ((c < 0) && (((-c) & ~0x3fc) == 0)) {
    return add(rd, rn, (-c));
  }
  int low = c & 0x3fc;
  if (low != 0) {
    sub(rd, rn, low);
    rn = rd;
  }
  if (c & ~0x3fc) {
    assert(AsmOperand::is_rotated_imm(c & ~0x3fc), "unsupported sub_slow offset %d", c);
    sub(rd, rn, c & ~0x3fc);
  } else if (rd != rn) {
    assert(c == 0, "");
    mov(rd, rn); // need to generate at least one move!
  }
#endif // AARCH64
}

void MacroAssembler::mov_slow(Register rd, address addr) {
  // do *not* call the non relocated mov_related_address
  mov_slow(rd, (intptr_t)addr);
}

void MacroAssembler::mov_slow(Register rd, const char *str) {
  mov_slow(rd, (intptr_t)str);
}

#ifdef AARCH64

// Common code for mov_slow and instr_count_for_mov_slow.
// Returns number of instructions of mov_slow pattern,
// generating it if non-null MacroAssembler is given.
int MacroAssembler::mov_slow_helper(Register rd, intptr_t c, MacroAssembler* masm) {
  // This code pattern is matched in NativeIntruction::is_mov_slow.
  // Update it at modifications.

  const intx mask = right_n_bits(16);
  // 1 movz instruction
  for (int base_shift = 0; base_shift < 64; base_shift += 16) {
    if ((c & ~(mask << base_shift)) == 0) {
      if (masm != NULL) {
        masm->movz(rd, ((uintx)c) >> base_shift, base_shift);
      }
      return 1;
    }
  }
  // 1 movn instruction
  for (int base_shift = 0; base_shift < 64; base_shift += 16) {
    if (((~c) & ~(mask << base_shift)) == 0) {
      if (masm != NULL) {
        masm->movn(rd, ((uintx)(~c)) >> base_shift, base_shift);
      }
      return 1;
    }
  }
  // 1 orr instruction
  {
    LogicalImmediate imm(c, false);
    if (imm.is_encoded()) {
      if (masm != NULL) {
        masm->orr(rd, ZR, imm);
      }
      return 1;
    }
  }
  // 1 movz/movn + up to 3 movk instructions
  int zeroes = 0;
  int ones = 0;
  for (int base_shift = 0; base_shift < 64; base_shift += 16) {
    int part = (c >> base_shift) & mask;
    if (part == 0) {
      ++zeroes;
    } else if (part == mask) {
      ++ones;
    }
  }
  int def_bits = 0;
  if (ones > zeroes) {
    def_bits = mask;
  }
  int inst_count = 0;
  for (int base_shift = 0; base_shift < 64; base_shift += 16) {
    int part = (c >> base_shift) & mask;
    if (part != def_bits) {
      if (masm != NULL) {
        if (inst_count > 0) {
          masm->movk(rd, part, base_shift);
        } else {
          if (def_bits == 0) {
            masm->movz(rd, part, base_shift);
          } else {
            masm->movn(rd, ~part & mask, base_shift);
          }
        }
      }
      inst_count++;
    }
  }
  assert((1 <= inst_count) && (inst_count <= 4), "incorrect number of instructions");
  return inst_count;
}

void MacroAssembler::mov_slow(Register rd, intptr_t c) {
#ifdef ASSERT
  int off = offset();
#endif
  (void) mov_slow_helper(rd, c, this);
  assert(offset() - off == instr_count_for_mov_slow(c) * InstructionSize, "size mismatch");
}

// Counts instructions generated by mov_slow(rd, c).
int MacroAssembler::instr_count_for_mov_slow(intptr_t c) {
  return mov_slow_helper(noreg, c, NULL);
}

int MacroAssembler::instr_count_for_mov_slow(address c) {
  return mov_slow_helper(noreg, (intptr_t)c, NULL);
}

#else

void MacroAssembler::mov_slow(Register rd, intptr_t c, AsmCondition cond) {
  if (AsmOperand::is_rotated_imm(c)) {
    mov(rd, c, cond);
  } else if (AsmOperand::is_rotated_imm(~c)) {
    mvn(rd, ~c, cond);
  } else if (VM_Version::supports_movw()) {
    movw(rd, c & 0xffff, cond);
    if ((unsigned int)c >> 16) {
      movt(rd, (unsigned int)c >> 16, cond);
    }
  } else {
    // Find first non-zero bit
    int shift = 0;
    while ((c & (3 << shift)) == 0) {
      shift += 2;
    }
    // Put the least significant part of the constant
    int mask = 0xff << shift;
    mov(rd, c & mask, cond);
    // Add up to 3 other parts of the constant;
    // each of them can be represented as rotated_imm
    if (c & (mask << 8)) {
      orr(rd, rd, c & (mask << 8), cond);
    }
    if (c & (mask << 16)) {
      orr(rd, rd, c & (mask << 16), cond);
    }
    if (c & (mask << 24)) {
      orr(rd, rd, c & (mask << 24), cond);
    }
  }
}

#endif // AARCH64

void MacroAssembler::mov_oop(Register rd, jobject o, int oop_index,
#ifdef AARCH64
                             bool patchable
#else
                             AsmCondition cond
#endif
                             ) {

  if (o == NULL) {
#ifdef AARCH64
    if (patchable) {
      nop();
    }
    mov(rd, ZR);
#else
    mov(rd, 0, cond);
#endif
    return;
  }

  if (oop_index == 0) {
    oop_index = oop_recorder()->allocate_oop_index(o);
  }
  relocate(oop_Relocation::spec(oop_index));

#ifdef AARCH64
  if (patchable) {
    nop();
  }
  ldr(rd, pc());
#else
  if (VM_Version::supports_movw()) {
    movw(rd, 0, cond);
    movt(rd, 0, cond);
  } else {
    ldr(rd, Address(PC), cond);
    // Extra nop to handle case of large offset of oop placeholder (see NativeMovConstReg::set_data).
    nop();
  }
#endif
}

void MacroAssembler::mov_metadata(Register rd, Metadata* o, int metadata_index AARCH64_ONLY_ARG(bool patchable)) {
  if (o == NULL) {
#ifdef AARCH64
    if (patchable) {
      nop();
    }
#endif
    mov(rd, 0);
    return;
  }

  if (metadata_index == 0) {
    metadata_index = oop_recorder()->allocate_metadata_index(o);
  }
  relocate(metadata_Relocation::spec(metadata_index));

#ifdef AARCH64
  if (patchable) {
    nop();
  }
#ifdef COMPILER2
  if (!patchable && VM_Version::prefer_moves_over_load_literal()) {
    mov_slow(rd, (address)o);
    return;
  }
#endif
  ldr(rd, pc());
#else
  if (VM_Version::supports_movw()) {
    movw(rd, ((int)o) & 0xffff);
    movt(rd, (unsigned int)o >> 16);
  } else {
    ldr(rd, Address(PC));
    // Extra nop to handle case of large offset of metadata placeholder (see NativeMovConstReg::set_data).
    nop();
  }
#endif // AARCH64
}

void MacroAssembler::mov_float(FloatRegister fd, jfloat c NOT_AARCH64_ARG(AsmCondition cond)) {
  Label skip_constant;
  union {
    jfloat f;
    jint i;
  } accessor;
  accessor.f = c;

#ifdef AARCH64
  // TODO-AARCH64 - try to optimize loading of float constants with fmov and/or mov_slow
  Label L;
  ldr_s(fd, target(L));
  b(skip_constant);
  bind(L);
  emit_int32(accessor.i);
  bind(skip_constant);
#else
  flds(fd, Address(PC), cond);
  b(skip_constant);
  emit_int32(accessor.i);
  bind(skip_constant);
#endif // AARCH64
}

void MacroAssembler::mov_double(FloatRegister fd, jdouble c NOT_AARCH64_ARG(AsmCondition cond)) {
  Label skip_constant;
  union {
    jdouble d;
    jint i[2];
  } accessor;
  accessor.d = c;

#ifdef AARCH64
  // TODO-AARCH64 - try to optimize loading of double constants with fmov
  Label L;
  ldr_d(fd, target(L));
  b(skip_constant);
  align(wordSize);
  bind(L);
  emit_int32(accessor.i[0]);
  emit_int32(accessor.i[1]);
  bind(skip_constant);
#else
  fldd(fd, Address(PC), cond);
  b(skip_constant);
  emit_int32(accessor.i[0]);
  emit_int32(accessor.i[1]);
  bind(skip_constant);
#endif // AARCH64
}

void MacroAssembler::ldr_global_s32(Register reg, address address_of_global) {
  intptr_t addr = (intptr_t) address_of_global;
#ifdef AARCH64
  assert((addr & 0x3) == 0, "address should be aligned");

  // FIXME: TODO
  if (false && page_reachable_from_cache(address_of_global)) {
    assert(false,"TODO: relocate");
    //relocate();
    adrp(reg, address_of_global);
    ldrsw(reg, Address(reg, addr & 0xfff));
  } else {
    mov_slow(reg, addr & ~0x3fff);
    ldrsw(reg, Address(reg, addr & 0x3fff));
  }
#else
  mov_slow(reg, addr & ~0xfff);
  ldr(reg, Address(reg, addr & 0xfff));
#endif
}

void MacroAssembler::ldr_global_ptr(Register reg, address address_of_global) {
#ifdef AARCH64
  intptr_t addr = (intptr_t) address_of_global;
  assert ((addr & 0x7) == 0, "address should be aligned");
  mov_slow(reg, addr & ~0x7fff);
  ldr(reg, Address(reg, addr & 0x7fff));
#else
  ldr_global_s32(reg, address_of_global);
#endif
}

void MacroAssembler::ldrb_global(Register reg, address address_of_global) {
  intptr_t addr = (intptr_t) address_of_global;
  mov_slow(reg, addr & ~0xfff);
  ldrb(reg, Address(reg, addr & 0xfff));
}

void MacroAssembler::zero_extend(Register rd, Register rn, int bits) {
#ifdef AARCH64
  switch (bits) {
    case  8: uxtb(rd, rn); break;
    case 16: uxth(rd, rn); break;
    case 32: mov_w(rd, rn); break;
    default: ShouldNotReachHere();
  }
#else
  if (bits <= 8) {
    andr(rd, rn, (1 << bits) - 1);
  } else if (bits >= 24) {
    bic(rd, rn, -1 << bits);
  } else {
    mov(rd, AsmOperand(rn, lsl, 32 - bits));
    mov(rd, AsmOperand(rd, lsr, 32 - bits));
  }
#endif
}

void MacroAssembler::sign_extend(Register rd, Register rn, int bits) {
#ifdef AARCH64
  switch (bits) {
    case  8: sxtb(rd, rn); break;
    case 16: sxth(rd, rn); break;
    case 32: sxtw(rd, rn); break;
    default: ShouldNotReachHere();
  }
#else
  mov(rd, AsmOperand(rn, lsl, 32 - bits));
  mov(rd, AsmOperand(rd, asr, 32 - bits));
#endif
}

#ifndef AARCH64

void MacroAssembler::long_move(Register rd_lo, Register rd_hi,
                               Register rn_lo, Register rn_hi,
                               AsmCondition cond) {
  if (rd_lo != rn_hi) {
    if (rd_lo != rn_lo) { mov(rd_lo, rn_lo, cond); }
    if (rd_hi != rn_hi) { mov(rd_hi, rn_hi, cond); }
  } else if (rd_hi != rn_lo) {
    if (rd_hi != rn_hi) { mov(rd_hi, rn_hi, cond); }
    if (rd_lo != rn_lo) { mov(rd_lo, rn_lo, cond); }
  } else {
    eor(rd_lo, rd_hi, rd_lo, cond);
    eor(rd_hi, rd_lo, rd_hi, cond);
    eor(rd_lo, rd_hi, rd_lo, cond);
  }
}

void MacroAssembler::long_shift(Register rd_lo, Register rd_hi,
                                Register rn_lo, Register rn_hi,
                                AsmShift shift, Register count) {
  Register tmp;
  if (rd_lo != rn_lo && rd_lo != rn_hi && rd_lo != count) {
    tmp = rd_lo;
  } else {
    tmp = rd_hi;
  }
  assert_different_registers(tmp, count, rn_lo, rn_hi);

  subs(tmp, count, 32);
  if (shift == lsl) {
    assert_different_registers(rd_hi, rn_lo);
    assert_different_registers(count, rd_hi);
    mov(rd_hi, AsmOperand(rn_lo, shift, tmp), pl);
    rsb(tmp, count, 32, mi);
    if (rd_hi == rn_hi) {
      mov(rd_hi, AsmOperand(rn_hi, lsl, count), mi);
      orr(rd_hi, rd_hi, AsmOperand(rn_lo, lsr, tmp), mi);
    } else {
      mov(rd_hi, AsmOperand(rn_lo, lsr, tmp), mi);
      orr(rd_hi, rd_hi, AsmOperand(rn_hi, lsl, count), mi);
    }
    mov(rd_lo, AsmOperand(rn_lo, shift, count));
  } else {
    assert_different_registers(rd_lo, rn_hi);
    assert_different_registers(rd_lo, count);
    mov(rd_lo, AsmOperand(rn_hi, shift, tmp), pl);
    rsb(tmp, count, 32, mi);
    if (rd_lo == rn_lo) {
      mov(rd_lo, AsmOperand(rn_lo, lsr, count), mi);
      orr(rd_lo, rd_lo, AsmOperand(rn_hi, lsl, tmp), mi);
    } else {
      mov(rd_lo, AsmOperand(rn_hi, lsl, tmp), mi);
      orr(rd_lo, rd_lo, AsmOperand(rn_lo, lsr, count), mi);
    }
    mov(rd_hi, AsmOperand(rn_hi, shift, count));
  }
}

void MacroAssembler::long_shift(Register rd_lo, Register rd_hi,
                                Register rn_lo, Register rn_hi,
                                AsmShift shift, int count) {
  assert(count != 0 && (count & ~63) == 0, "must be");

  if (shift == lsl) {
    assert_different_registers(rd_hi, rn_lo);
    if (count >= 32) {
      mov(rd_hi, AsmOperand(rn_lo, lsl, count - 32));
      mov(rd_lo, 0);
    } else {
      mov(rd_hi, AsmOperand(rn_hi, lsl, count));
      orr(rd_hi, rd_hi, AsmOperand(rn_lo, lsr, 32 - count));
      mov(rd_lo, AsmOperand(rn_lo, lsl, count));
    }
  } else {
    assert_different_registers(rd_lo, rn_hi);
    if (count >= 32) {
      if (count == 32) {
        mov(rd_lo, rn_hi);
      } else {
        mov(rd_lo, AsmOperand(rn_hi, shift, count - 32));
      }
      if (shift == asr) {
        mov(rd_hi, AsmOperand(rn_hi, asr, 0));
      } else {
        mov(rd_hi, 0);
      }
    } else {
      mov(rd_lo, AsmOperand(rn_lo, lsr, count));
      orr(rd_lo, rd_lo, AsmOperand(rn_hi, lsl, 32 - count));
      mov(rd_hi, AsmOperand(rn_hi, shift, count));
    }
  }
}
#endif // !AARCH64

void MacroAssembler::_verify_oop(Register reg, const char* s, const char* file, int line) {
  // This code pattern is matched in NativeIntruction::skip_verify_oop.
  // Update it at modifications.
  if (!VerifyOops) return;

  char buffer[64];
#ifdef COMPILER1
  if (CommentedAssembly) {
    snprintf(buffer, sizeof(buffer), "verify_oop at %d", offset());
    block_comment(buffer);
  }
#endif
  const char* msg_buffer = NULL;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("%s at offset %d (%s:%d)", s, offset(), file, line);
    msg_buffer = code_string(ss.as_string());
  }

  save_all_registers();

  if (reg != R2) {
      mov(R2, reg);                              // oop to verify
  }
  mov(R1, SP);                                   // register save area

  Label done;
  InlinedString Lmsg(msg_buffer);
  ldr_literal(R0, Lmsg);                         // message

  // call indirectly to solve generation ordering problem
  ldr_global_ptr(Rtemp, StubRoutines::verify_oop_subroutine_entry_address());
  call(Rtemp);

  restore_all_registers();

  b(done);
#ifdef COMPILER2
  int off = offset();
#endif
  bind_literal(Lmsg);
#ifdef COMPILER2
  if (offset() - off == 1 * wordSize) {
    // no padding, so insert nop for worst-case sizing
    nop();
  }
#endif
  bind(done);
}

void MacroAssembler::_verify_oop_addr(Address addr, const char* s, const char* file, int line) {
  if (!VerifyOops) return;

  const char* msg_buffer = NULL;
  {
    ResourceMark rm;
    stringStream ss;
    if ((addr.base() == SP) && (addr.index()==noreg)) {
      ss.print("verify_oop_addr SP[%d]: %s", (int)addr.disp(), s);
    } else {
      ss.print("verify_oop_addr: %s", s);
    }
    ss.print(" (%s:%d)", file, line);
    msg_buffer = code_string(ss.as_string());
  }

  int push_size = save_all_registers();

  if (addr.base() == SP) {
    // computes an addr that takes into account the push
    if (addr.index() != noreg) {
      Register new_base = addr.index() == R2 ? R1 : R2; // avoid corrupting the index
      add(new_base, SP, push_size);
      addr = addr.rebase(new_base);
    } else {
      addr = addr.plus_disp(push_size);
    }
  }

  ldr(R2, addr);                                 // oop to verify
  mov(R1, SP);                                   // register save area

  Label done;
  InlinedString Lmsg(msg_buffer);
  ldr_literal(R0, Lmsg);                         // message

  // call indirectly to solve generation ordering problem
  ldr_global_ptr(Rtemp, StubRoutines::verify_oop_subroutine_entry_address());
  call(Rtemp);

  restore_all_registers();

  b(done);
  bind_literal(Lmsg);
  bind(done);
}

void MacroAssembler::null_check(Register reg, Register tmp, int offset) {
  if (needs_explicit_null_check(offset)) {
#ifdef AARCH64
    ldr(ZR, Address(reg));
#else
    assert_different_registers(reg, tmp);
    if (tmp == noreg) {
      tmp = Rtemp;
      assert((! Thread::current()->is_Compiler_thread()) ||
             (! (ciEnv::current()->task() == NULL)) ||
             (! (ciEnv::current()->comp_level() == CompLevel_full_optimization)),
             "Rtemp not available in C2"); // explicit tmp register required
      // XXX: could we mark the code buffer as not compatible with C2 ?
    }
    ldr(tmp, Address(reg));
#endif
  }
}

// Puts address of allocated object into register `obj` and end of allocated object into register `obj_end`.
void MacroAssembler::eden_allocate(Register obj, Register obj_end, Register tmp1, Register tmp2,
                                 RegisterOrConstant size_expression, Label& slow_case) {
  if (!Universe::heap()->supports_inline_contig_alloc()) {
    b(slow_case);
    return;
  }

  CollectedHeap* ch = Universe::heap();

  const Register top_addr = tmp1;
  const Register heap_end = tmp2;

  if (size_expression.is_register()) {
    assert_different_registers(obj, obj_end, top_addr, heap_end, size_expression.as_register());
  } else {
    assert_different_registers(obj, obj_end, top_addr, heap_end);
  }

  bool load_const = AARCH64_ONLY(false) NOT_AARCH64(VM_Version::supports_movw() ); // TODO-AARCH64 check performance
  if (load_const) {
    mov_address(top_addr, (address)Universe::heap()->top_addr(), symbolic_Relocation::eden_top_reference);
  } else {
    ldr(top_addr, Address(Rthread, JavaThread::heap_top_addr_offset()));
  }
  // Calculate new heap_top by adding the size of the object
  Label retry;
  bind(retry);

#ifdef AARCH64
  ldxr(obj, top_addr);
#else
  ldr(obj, Address(top_addr));
#endif // AARCH64

  ldr(heap_end, Address(top_addr, (intptr_t)ch->end_addr() - (intptr_t)ch->top_addr()));
  add_rc(obj_end, obj, size_expression);
  // Check if obj_end wrapped around, i.e., obj_end < obj. If yes, jump to the slow case.
  cmp(obj_end, obj);
  b(slow_case, lo);
  // Update heap_top if allocation succeeded
  cmp(obj_end, heap_end);
  b(slow_case, hi);

#ifdef AARCH64
  stxr(heap_end/*scratched*/, obj_end, top_addr);
  cbnz_w(heap_end, retry);
#else
  atomic_cas_bool(obj, obj_end, top_addr, 0, heap_end/*scratched*/);
  b(retry, ne);
#endif // AARCH64
}

// Puts address of allocated object into register `obj` and end of allocated object into register `obj_end`.
void MacroAssembler::tlab_allocate(Register obj, Register obj_end, Register tmp1,
                                 RegisterOrConstant size_expression, Label& slow_case) {
  const Register tlab_end = tmp1;
  assert_different_registers(obj, obj_end, tlab_end);

  ldr(obj, Address(Rthread, JavaThread::tlab_top_offset()));
  ldr(tlab_end, Address(Rthread, JavaThread::tlab_end_offset()));
  add_rc(obj_end, obj, size_expression);
  cmp(obj_end, tlab_end);
  b(slow_case, hi);
  str(obj_end, Address(Rthread, JavaThread::tlab_top_offset()));
}

// Fills memory regions [start..end] with zeroes. Clobbers `start` and `tmp` registers.
void MacroAssembler::zero_memory(Register start, Register end, Register tmp) {
  Label loop;
  const Register ptr = start;

#ifdef AARCH64
  // TODO-AARCH64 - compare performance of 2x word zeroing with simple 1x
  const Register size = tmp;
  Label remaining, done;

  sub(size, end, start);

#ifdef ASSERT
  { Label L;
    tst(size, wordSize - 1);
    b(L, eq);
    stop("size is not a multiple of wordSize");
    bind(L);
  }
#endif // ASSERT

  subs(size, size, wordSize);
  b(remaining, le);

  // Zero by 2 words per iteration.
  bind(loop);
  subs(size, size, 2*wordSize);
  stp(ZR, ZR, Address(ptr, 2*wordSize, post_indexed));
  b(loop, gt);

  bind(remaining);
  b(done, ne);
  str(ZR, Address(ptr));
  bind(done);
#else
  mov(tmp, 0);
  bind(loop);
  cmp(ptr, end);
  str(tmp, Address(ptr, wordSize, post_indexed), lo);
  b(loop, lo);
#endif // AARCH64
}

void MacroAssembler::incr_allocated_bytes(RegisterOrConstant size_in_bytes, Register tmp) {
#ifdef AARCH64
  ldr(tmp, Address(Rthread, in_bytes(JavaThread::allocated_bytes_offset())));
  add_rc(tmp, tmp, size_in_bytes);
  str(tmp, Address(Rthread, in_bytes(JavaThread::allocated_bytes_offset())));
#else
  // Bump total bytes allocated by this thread
  Label done;

  // Borrow the Rthread for alloc counter
  Register Ralloc = Rthread;
  add(Ralloc, Ralloc, in_bytes(JavaThread::allocated_bytes_offset()));
  ldr(tmp, Address(Ralloc));
  adds(tmp, tmp, size_in_bytes);
  str(tmp, Address(Ralloc), cc);
  b(done, cc);

  // Increment the high word and store single-copy atomically (that is an unlikely scenario on typical embedded systems as it means >4GB has been allocated)
  // To do so ldrd/strd instructions used which require an even-odd pair of registers. Such a request could be difficult to satisfy by
  // allocating those registers on a higher level, therefore the routine is ready to allocate a pair itself.
  Register low, high;
  // Select ether R0/R1 or R2/R3

  if (size_in_bytes.is_register() && (size_in_bytes.as_register() == R0 || size_in_bytes.as_register() == R1)) {
    low = R2;
    high  = R3;
  } else {
    low = R0;
    high  = R1;
  }
  push(RegisterSet(low, high));

  ldrd(low, Address(Ralloc));
  adds(low, low, size_in_bytes);
  adc(high, high, 0);
  strd(low, Address(Ralloc));

  pop(RegisterSet(low, high));

  bind(done);

  // Unborrow the Rthread
  sub(Rthread, Ralloc, in_bytes(JavaThread::allocated_bytes_offset()));
#endif // AARCH64
}

void MacroAssembler::arm_stack_overflow_check(int frame_size_in_bytes, Register tmp) {
  // Version of AbstractAssembler::generate_stack_overflow_check optimized for ARM
  if (UseStackBanging) {
    const int page_size = os::vm_page_size();

    sub_slow(tmp, SP, JavaThread::stack_shadow_zone_size());
    strb(R0, Address(tmp));
#ifdef AARCH64
    for (; frame_size_in_bytes >= page_size; frame_size_in_bytes -= page_size) {
      sub(tmp, tmp, page_size);
      strb(R0, Address(tmp));
    }
#else
    for (; frame_size_in_bytes >= page_size; frame_size_in_bytes -= 0xff0) {
      strb(R0, Address(tmp, -0xff0, pre_indexed));
    }
#endif // AARCH64
  }
}

void MacroAssembler::arm_stack_overflow_check(Register Rsize, Register tmp) {
  if (UseStackBanging) {
    Label loop;

    mov(tmp, SP);
    add_slow(Rsize, Rsize, JavaThread::stack_shadow_zone_size() - os::vm_page_size());
#ifdef AARCH64
    sub(tmp, tmp, Rsize);
    bind(loop);
    subs(Rsize, Rsize, os::vm_page_size());
    strb(ZR, Address(tmp, Rsize));
#else
    bind(loop);
    subs(Rsize, Rsize, 0xff0);
    strb(R0, Address(tmp, -0xff0, pre_indexed));
#endif // AARCH64
    b(loop, hi);
  }
}

void MacroAssembler::stop(const char* msg) {
  // This code pattern is matched in NativeIntruction::is_stop.
  // Update it at modifications.
#ifdef COMPILER1
  if (CommentedAssembly) {
    block_comment("stop");
  }
#endif

  InlinedAddress Ldebug(CAST_FROM_FN_PTR(address, MacroAssembler::debug));
  InlinedString Lmsg(msg);

  // save all registers for further inspection
  save_all_registers();

  ldr_literal(R0, Lmsg);                     // message
  mov(R1, SP);                               // register save area

#ifdef AARCH64
  ldr_literal(Rtemp, Ldebug);
  br(Rtemp);
#else
  ldr_literal(PC, Ldebug);                   // call MacroAssembler::debug
#endif // AARCH64

#if defined(COMPILER2) && defined(AARCH64)
  int off = offset();
#endif
  bind_literal(Lmsg);
  bind_literal(Ldebug);
#if defined(COMPILER2) && defined(AARCH64)
  if (offset() - off == 2 * wordSize) {
    // no padding, so insert nop for worst-case sizing
    nop();
  }
#endif
}

void MacroAssembler::warn(const char* msg) {
#ifdef COMPILER1
  if (CommentedAssembly) {
    block_comment("warn");
  }
#endif

  InlinedAddress Lwarn(CAST_FROM_FN_PTR(address, warning));
  InlinedString Lmsg(msg);
  Label done;

  int push_size = save_caller_save_registers();

#ifdef AARCH64
  // TODO-AARCH64 - get rid of extra debug parameters
  mov(R1, LR);
  mov(R2, FP);
  add(R3, SP, push_size);
#endif

  ldr_literal(R0, Lmsg);                    // message
  ldr_literal(LR, Lwarn);                   // call warning

  call(LR);

  restore_caller_save_registers();

  b(done);
  bind_literal(Lmsg);
  bind_literal(Lwarn);
  bind(done);
}


int MacroAssembler::save_all_registers() {
  // This code pattern is matched in NativeIntruction::is_save_all_registers.
  // Update it at modifications.
#ifdef AARCH64
  const Register tmp = Rtemp;
  raw_push(R30, ZR);
  for (int i = 28; i >= 0; i -= 2) {
      raw_push(as_Register(i), as_Register(i+1));
  }
  mov_pc_to(tmp);
  str(tmp, Address(SP, 31*wordSize));
  ldr(tmp, Address(SP, tmp->encoding()*wordSize));
  return 32*wordSize;
#else
  push(RegisterSet(R0, R12) | RegisterSet(LR) | RegisterSet(PC));
  return 15*wordSize;
#endif // AARCH64
}

void MacroAssembler::restore_all_registers() {
#ifdef AARCH64
  for (int i = 0; i <= 28; i += 2) {
    raw_pop(as_Register(i), as_Register(i+1));
  }
  raw_pop(R30, ZR);
#else
  pop(RegisterSet(R0, R12) | RegisterSet(LR));   // restore registers
  add(SP, SP, wordSize);                         // discard saved PC
#endif // AARCH64
}

int MacroAssembler::save_caller_save_registers() {
#ifdef AARCH64
  for (int i = 0; i <= 16; i += 2) {
    raw_push(as_Register(i), as_Register(i+1));
  }
  raw_push(R18, LR);
  return 20*wordSize;
#else
#if R9_IS_SCRATCHED
  // Save also R10 to preserve alignment
  push(RegisterSet(R0, R3) | RegisterSet(R12) | RegisterSet(LR) | RegisterSet(R9,R10));
  return 8*wordSize;
#else
  push(RegisterSet(R0, R3) | RegisterSet(R12) | RegisterSet(LR));
  return 6*wordSize;
#endif
#endif // AARCH64
}

void MacroAssembler::restore_caller_save_registers() {
#ifdef AARCH64
  raw_pop(R18, LR);
  for (int i = 16; i >= 0; i -= 2) {
    raw_pop(as_Register(i), as_Register(i+1));
  }
#else
#if R9_IS_SCRATCHED
  pop(RegisterSet(R0, R3) | RegisterSet(R12) | RegisterSet(LR) | RegisterSet(R9,R10));
#else
  pop(RegisterSet(R0, R3) | RegisterSet(R12) | RegisterSet(LR));
#endif
#endif // AARCH64
}

void MacroAssembler::debug(const char* msg, const intx* registers) {
  // In order to get locks to work, we need to fake a in_VM state
  JavaThread* thread = JavaThread::current();
  thread->set_thread_state(_thread_in_vm);

  if (ShowMessageBoxOnError) {
    ttyLocker ttyl;
    if (CountBytecodes || TraceBytecodes || StopInterpreterAt) {
      BytecodeCounter::print();
    }
    if (os::message_box(msg, "Execution stopped, print registers?")) {
#ifdef AARCH64
      // saved registers: R0-R30, PC
      const int nregs = 32;
#else
      // saved registers: R0-R12, LR, PC
      const int nregs = 15;
      const Register regs[nregs] = {R0, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, LR, PC};
#endif // AARCH64

      for (int i = 0; i < nregs AARCH64_ONLY(-1); i++) {
        tty->print_cr("%s = " INTPTR_FORMAT, AARCH64_ONLY(as_Register(i)) NOT_AARCH64(regs[i])->name(), registers[i]);
      }

#ifdef AARCH64
      tty->print_cr("pc = " INTPTR_FORMAT, registers[nregs-1]);
#endif // AARCH64

      // derive original SP value from the address of register save area
      tty->print_cr("%s = " INTPTR_FORMAT, SP->name(), p2i(&registers[nregs]));
    }
    BREAKPOINT;
  } else {
    ::tty->print_cr("=============== DEBUG MESSAGE: %s ================\n", msg);
  }
  assert(false, "DEBUG MESSAGE: %s", msg);
  fatal("%s", msg); // returning from MacroAssembler::debug is not supported
}

void MacroAssembler::unimplemented(const char* what) {
  const char* buf = NULL;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("unimplemented: %s", what);
    buf = code_string(ss.as_string());
  }
  stop(buf);
}


// Implementation of FixedSizeCodeBlock

FixedSizeCodeBlock::FixedSizeCodeBlock(MacroAssembler* masm, int size_in_instrs, bool enabled) :
_masm(masm), _start(masm->pc()), _size_in_instrs(size_in_instrs), _enabled(enabled) {
}

FixedSizeCodeBlock::~FixedSizeCodeBlock() {
  if (_enabled) {
    address curr_pc = _masm->pc();

    assert(_start < curr_pc, "invalid current pc");
    guarantee(curr_pc <= _start + _size_in_instrs * Assembler::InstructionSize, "code block is too long");

    int nops_count = (_start - curr_pc) / Assembler::InstructionSize + _size_in_instrs;
    for (int i = 0; i < nops_count; i++) {
      _masm->nop();
    }
  }
}

#ifdef AARCH64

// Serializes memory.
// tmp register is not used on AArch64, this parameter is provided solely for better compatibility with 32-bit ARM
void MacroAssembler::membar(Membar_mask_bits order_constraint, Register tmp) {
  if (!os::is_MP()) return;

  // TODO-AARCH64 investigate dsb vs dmb effects
  if (order_constraint == StoreStore) {
    dmb(DMB_st);
  } else if ((order_constraint & ~(LoadLoad | LoadStore)) == 0) {
    dmb(DMB_ld);
  } else {
    dmb(DMB_all);
  }
}

#else

// Serializes memory. Potentially blows flags and reg.
// tmp is a scratch for v6 co-processor write op (could be noreg for other architecure versions)
// preserve_flags takes a longer path in LoadStore case (dmb rather then control dependency) to preserve status flags. Optional.
// load_tgt is an ordered load target in a LoadStore case only, to create dependency between the load operation and conditional branch. Optional.
void MacroAssembler::membar(Membar_mask_bits order_constraint,
                            Register tmp,
                            bool preserve_flags,
                            Register load_tgt) {
  if (!os::is_MP()) return;

  if (order_constraint == StoreStore) {
    dmb(DMB_st, tmp);
  } else if ((order_constraint & StoreLoad)  ||
             (order_constraint & LoadLoad)   ||
             (order_constraint & StoreStore) ||
             (load_tgt == noreg)             ||
             preserve_flags) {
    dmb(DMB_all, tmp);
  } else {
    // LoadStore: speculative stores reordeing is prohibited

    // By providing an ordered load target register, we avoid an extra memory load reference
    Label not_taken;
    bind(not_taken);
    cmp(load_tgt, load_tgt);
    b(not_taken, ne);
  }
}

#endif // AARCH64

// If "allow_fallthrough_on_failure" is false, we always branch to "slow_case"
// on failure, so fall-through can only mean success.
// "one_shot" controls whether we loop and retry to mitigate spurious failures.
// This is only needed for C2, which for some reason does not rety,
// while C1/interpreter does.
// TODO: measure if it makes a difference

void MacroAssembler::cas_for_lock_acquire(Register oldval, Register newval,
  Register base, Register tmp, Label &slow_case,
  bool allow_fallthrough_on_failure, bool one_shot)
{

  bool fallthrough_is_success = false;

  // ARM Litmus Test example does prefetching here.
  // TODO: investigate if it helps performance

  // The last store was to the displaced header, so to prevent
  // reordering we must issue a StoreStore or Release barrier before
  // the CAS store.

#ifdef AARCH64

  Register Rscratch = tmp;
  Register Roop = base;
  Register mark = oldval;
  Register Rbox = newval;
  Label loop;

  assert(oopDesc::mark_offset_in_bytes() == 0, "must be");

  // Instead of StoreStore here, we use store-release-exclusive below

  bind(loop);

  ldaxr(tmp, base);  // acquire
  cmp(tmp, oldval);
  b(slow_case, ne);
  stlxr(tmp, newval, base); // release
  if (one_shot) {
    cmp_w(tmp, 0);
  } else {
    cbnz_w(tmp, loop);
    fallthrough_is_success = true;
  }

  // MemBarAcquireLock would normally go here, but
  // we already do ldaxr+stlxr above, which has
  // Sequential Consistency

#else
  membar(MacroAssembler::StoreStore, noreg);

  if (one_shot) {
    ldrex(tmp, Address(base, oopDesc::mark_offset_in_bytes()));
    cmp(tmp, oldval);
    strex(tmp, newval, Address(base, oopDesc::mark_offset_in_bytes()), eq);
    cmp(tmp, 0, eq);
  } else {
    atomic_cas_bool(oldval, newval, base, oopDesc::mark_offset_in_bytes(), tmp);
  }

  // MemBarAcquireLock barrier
  // According to JSR-133 Cookbook, this should be LoadLoad | LoadStore,
  // but that doesn't prevent a load or store from floating up between
  // the load and store in the CAS sequence, so play it safe and
  // do a full fence.
  membar(Membar_mask_bits(LoadLoad | LoadStore | StoreStore | StoreLoad), noreg);
#endif
  if (!fallthrough_is_success && !allow_fallthrough_on_failure) {
    b(slow_case, ne);
  }
}

void MacroAssembler::cas_for_lock_release(Register oldval, Register newval,
  Register base, Register tmp, Label &slow_case,
  bool allow_fallthrough_on_failure, bool one_shot)
{

  bool fallthrough_is_success = false;

  assert_different_registers(oldval,newval,base,tmp);

#ifdef AARCH64
  Label loop;

  assert(oopDesc::mark_offset_in_bytes() == 0, "must be");

  bind(loop);
  ldxr(tmp, base);
  cmp(tmp, oldval);
  b(slow_case, ne);
  // MemBarReleaseLock barrier
  stlxr(tmp, newval, base);
  if (one_shot) {
    cmp_w(tmp, 0);
  } else {
    cbnz_w(tmp, loop);
    fallthrough_is_success = true;
  }
#else
  // MemBarReleaseLock barrier
  // According to JSR-133 Cookbook, this should be StoreStore | LoadStore,
  // but that doesn't prevent a load or store from floating down between
  // the load and store in the CAS sequence, so play it safe and
  // do a full fence.
  membar(Membar_mask_bits(LoadLoad | LoadStore | StoreStore | StoreLoad), tmp);

  if (one_shot) {
    ldrex(tmp, Address(base, oopDesc::mark_offset_in_bytes()));
    cmp(tmp, oldval);
    strex(tmp, newval, Address(base, oopDesc::mark_offset_in_bytes()), eq);
    cmp(tmp, 0, eq);
  } else {
    atomic_cas_bool(oldval, newval, base, oopDesc::mark_offset_in_bytes(), tmp);
  }
#endif
  if (!fallthrough_is_success && !allow_fallthrough_on_failure) {
    b(slow_case, ne);
  }

  // ExitEnter
  // According to JSR-133 Cookbook, this should be StoreLoad, the same
  // barrier that follows volatile store.
  // TODO: Should be able to remove on armv8 if volatile loads
  // use the load-acquire instruction.
  membar(StoreLoad, noreg);
}

#ifndef PRODUCT

// Preserves flags and all registers.
// On SMP the updated value might not be visible to external observers without a sychronization barrier
void MacroAssembler::cond_atomic_inc32(AsmCondition cond, int* counter_addr) {
  if (counter_addr != NULL) {
    InlinedAddress counter_addr_literal((address)counter_addr);
    Label done, retry;
    if (cond != al) {
      b(done, inverse(cond));
    }

#ifdef AARCH64
    raw_push(R0, R1);
    raw_push(R2, ZR);

    ldr_literal(R0, counter_addr_literal);

    bind(retry);
    ldxr_w(R1, R0);
    add_w(R1, R1, 1);
    stxr_w(R2, R1, R0);
    cbnz_w(R2, retry);

    raw_pop(R2, ZR);
    raw_pop(R0, R1);
#else
    push(RegisterSet(R0, R3) | RegisterSet(Rtemp));
    ldr_literal(R0, counter_addr_literal);

    mrs(CPSR, Rtemp);

    bind(retry);
    ldr_s32(R1, Address(R0));
    add(R2, R1, 1);
    atomic_cas_bool(R1, R2, R0, 0, R3);
    b(retry, ne);

    msr(CPSR_fsxc, Rtemp);

    pop(RegisterSet(R0, R3) | RegisterSet(Rtemp));
#endif // AARCH64

    b(done);
    bind_literal(counter_addr_literal);

    bind(done);
  }
}

#endif // !PRODUCT


// Building block for CAS cases of biased locking: makes CAS and records statistics.
// The slow_case label is used to transfer control if CAS fails. Otherwise leaves condition codes set.
void MacroAssembler::biased_locking_enter_with_cas(Register obj_reg, Register old_mark_reg, Register new_mark_reg,
                                                 Register tmp, Label& slow_case, int* counter_addr) {

  cas_for_lock_acquire(old_mark_reg, new_mark_reg, obj_reg, tmp, slow_case);
#ifdef ASSERT
  breakpoint(ne); // Fallthrough only on success
#endif
#ifndef PRODUCT
  if (counter_addr != NULL) {
    cond_atomic_inc32(al, counter_addr);
  }
#endif // !PRODUCT
}

int MacroAssembler::biased_locking_enter(Register obj_reg, Register swap_reg, Register tmp_reg,
                                         bool swap_reg_contains_mark,
                                         Register tmp2,
                                         Label& done, Label& slow_case,
                                         BiasedLockingCounters* counters) {
  // obj_reg must be preserved (at least) if the bias locking fails
  // tmp_reg is a temporary register
  // swap_reg was used as a temporary but contained a value
  //   that was used afterwards in some call pathes. Callers
  //   have been fixed so that swap_reg no longer needs to be
  //   saved.
  // Rtemp in no longer scratched

  assert(UseBiasedLocking, "why call this otherwise?");
  assert_different_registers(obj_reg, swap_reg, tmp_reg, tmp2);
  guarantee(swap_reg!=tmp_reg, "invariant");
  assert(tmp_reg != noreg, "must supply tmp_reg");

#ifndef PRODUCT
  if (PrintBiasedLockingStatistics && (counters == NULL)) {
    counters = BiasedLocking::counters();
  }
#endif

  assert(markOopDesc::age_shift == markOopDesc::lock_bits + markOopDesc::biased_lock_bits, "biased locking makes assumptions about bit layout");
  Address mark_addr(obj_reg, oopDesc::mark_offset_in_bytes());

  // Biased locking
  // See whether the lock is currently biased toward our thread and
  // whether the epoch is still valid
  // Note that the runtime guarantees sufficient alignment of JavaThread
  // pointers to allow age to be placed into low bits
  // First check to see whether biasing is even enabled for this object
  Label cas_label;

  // The null check applies to the mark loading, if we need to load it.
  // If the mark has already been loaded in swap_reg then it has already
  // been performed and the offset is irrelevant.
  int null_check_offset = offset();
  if (!swap_reg_contains_mark) {
    ldr(swap_reg, mark_addr);
  }

  // On MP platform loads could return 'stale' values in some cases.
  // That is acceptable since either CAS or slow case path is taken in the worst case.

  andr(tmp_reg, swap_reg, (uintx)markOopDesc::biased_lock_mask_in_place);
  cmp(tmp_reg, markOopDesc::biased_lock_pattern);

  b(cas_label, ne);

  // The bias pattern is present in the object's header. Need to check
  // whether the bias owner and the epoch are both still current.
  load_klass(tmp_reg, obj_reg);
  ldr(tmp_reg, Address(tmp_reg, Klass::prototype_header_offset()));
  orr(tmp_reg, tmp_reg, Rthread);
  eor(tmp_reg, tmp_reg, swap_reg);

#ifdef AARCH64
  ands(tmp_reg, tmp_reg, ~((uintx) markOopDesc::age_mask_in_place));
#else
  bics(tmp_reg, tmp_reg, ((int) markOopDesc::age_mask_in_place));
#endif // AARCH64

#ifndef PRODUCT
  if (counters != NULL) {
    cond_atomic_inc32(eq, counters->biased_lock_entry_count_addr());
  }
#endif // !PRODUCT

  b(done, eq);

  Label try_revoke_bias;
  Label try_rebias;

  // At this point we know that the header has the bias pattern and
  // that we are not the bias owner in the current epoch. We need to
  // figure out more details about the state of the header in order to
  // know what operations can be legally performed on the object's
  // header.

  // If the low three bits in the xor result aren't clear, that means
  // the prototype header is no longer biased and we have to revoke
  // the bias on this object.
  tst(tmp_reg, (uintx)markOopDesc::biased_lock_mask_in_place);
  b(try_revoke_bias, ne);

  // Biasing is still enabled for this data type. See whether the
  // epoch of the current bias is still valid, meaning that the epoch
  // bits of the mark word are equal to the epoch bits of the
  // prototype header. (Note that the prototype header's epoch bits
  // only change at a safepoint.) If not, attempt to rebias the object
  // toward the current thread. Note that we must be absolutely sure
  // that the current epoch is invalid in order to do this because
  // otherwise the manipulations it performs on the mark word are
  // illegal.
  tst(tmp_reg, (uintx)markOopDesc::epoch_mask_in_place);
  b(try_rebias, ne);

  // tmp_reg has the age, epoch and pattern bits cleared
  // The remaining (owner) bits are (Thread ^ current_owner)

  // The epoch of the current bias is still valid but we know nothing
  // about the owner; it might be set or it might be clear. Try to
  // acquire the bias of the object using an atomic operation. If this
  // fails we will go in to the runtime to revoke the object's bias.
  // Note that we first construct the presumed unbiased header so we
  // don't accidentally blow away another thread's valid bias.

  // Note that we know the owner is not ourself. Hence, success can
  // only happen when the owner bits is 0

#ifdef AARCH64
  // Bit mask biased_lock + age + epoch is not a valid AArch64 logical immediate, as it has
  // cleared bit in the middle (cms bit). So it is loaded with separate instruction.
  mov(tmp2, (markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place | markOopDesc::epoch_mask_in_place));
  andr(swap_reg, swap_reg, tmp2);
#else
  // until the assembler can be made smarter, we need to make some assumptions about the values
  // so we can optimize this:
  assert((markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place | markOopDesc::epoch_mask_in_place) == 0x1ff, "biased bitmasks changed");

  mov(swap_reg, AsmOperand(swap_reg, lsl, 23));
  mov(swap_reg, AsmOperand(swap_reg, lsr, 23)); // markOop with thread bits cleared (for CAS)
#endif // AARCH64

  orr(tmp_reg, swap_reg, Rthread); // new mark

  biased_locking_enter_with_cas(obj_reg, swap_reg, tmp_reg, tmp2, slow_case,
        (counters != NULL) ? counters->anonymously_biased_lock_entry_count_addr() : NULL);

  // If the biasing toward our thread failed, this means that
  // another thread succeeded in biasing it toward itself and we
  // need to revoke that bias. The revocation will occur in the
  // interpreter runtime in the slow case.

  b(done);

  bind(try_rebias);

  // At this point we know the epoch has expired, meaning that the
  // current "bias owner", if any, is actually invalid. Under these
  // circumstances _only_, we are allowed to use the current header's
  // value as the comparison value when doing the cas to acquire the
  // bias in the current epoch. In other words, we allow transfer of
  // the bias from one thread to another directly in this situation.

  // tmp_reg low (not owner) bits are (age: 0 | pattern&epoch: prototype^swap_reg)

  eor(tmp_reg, tmp_reg, swap_reg); // OK except for owner bits (age preserved !)

  // owner bits 'random'. Set them to Rthread.
#ifdef AARCH64
  mov(tmp2, (markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place | markOopDesc::epoch_mask_in_place));
  andr(tmp_reg, tmp_reg, tmp2);
#else
  mov(tmp_reg, AsmOperand(tmp_reg, lsl, 23));
  mov(tmp_reg, AsmOperand(tmp_reg, lsr, 23));
#endif // AARCH64

  orr(tmp_reg, tmp_reg, Rthread); // new mark

  biased_locking_enter_with_cas(obj_reg, swap_reg, tmp_reg, tmp2, slow_case,
        (counters != NULL) ? counters->rebiased_lock_entry_count_addr() : NULL);

  // If the biasing toward our thread failed, then another thread
  // succeeded in biasing it toward itself and we need to revoke that
  // bias. The revocation will occur in the runtime in the slow case.

  b(done);

  bind(try_revoke_bias);

  // The prototype mark in the klass doesn't have the bias bit set any
  // more, indicating that objects of this data type are not supposed
  // to be biased any more. We are going to try to reset the mark of
  // this object to the prototype value and fall through to the
  // CAS-based locking scheme. Note that if our CAS fails, it means
  // that another thread raced us for the privilege of revoking the
  // bias of this particular object, so it's okay to continue in the
  // normal locking code.

  // tmp_reg low (not owner) bits are (age: 0 | pattern&epoch: prototype^swap_reg)

  eor(tmp_reg, tmp_reg, swap_reg); // OK except for owner bits (age preserved !)

  // owner bits 'random'. Clear them
#ifdef AARCH64
  mov(tmp2, (markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place | markOopDesc::epoch_mask_in_place));
  andr(tmp_reg, tmp_reg, tmp2);
#else
  mov(tmp_reg, AsmOperand(tmp_reg, lsl, 23));
  mov(tmp_reg, AsmOperand(tmp_reg, lsr, 23));
#endif // AARCH64

  biased_locking_enter_with_cas(obj_reg, swap_reg, tmp_reg, tmp2, cas_label,
        (counters != NULL) ? counters->revoked_lock_entry_count_addr() : NULL);

  // Fall through to the normal CAS-based lock, because no matter what
  // the result of the above CAS, some thread must have succeeded in
  // removing the bias bit from the object's header.

  bind(cas_label);

  return null_check_offset;
}


void MacroAssembler::biased_locking_exit(Register obj_reg, Register tmp_reg, Label& done) {
  assert(UseBiasedLocking, "why call this otherwise?");

  // Check for biased locking unlock case, which is a no-op
  // Note: we do not have to check the thread ID for two reasons.
  // First, the interpreter checks for IllegalMonitorStateException at
  // a higher level. Second, if the bias was revoked while we held the
  // lock, the object could not be rebiased toward another thread, so
  // the bias bit would be clear.
  ldr(tmp_reg, Address(obj_reg, oopDesc::mark_offset_in_bytes()));

  andr(tmp_reg, tmp_reg, (uintx)markOopDesc::biased_lock_mask_in_place);
  cmp(tmp_reg, markOopDesc::biased_lock_pattern);
  b(done, eq);
}


void MacroAssembler::resolve_jobject(Register value,
                                     Register tmp1,
                                     Register tmp2) {
  assert_different_registers(value, tmp1, tmp2);
  Label done, not_weak;
  cbz(value, done);             // Use NULL as-is.
  STATIC_ASSERT(JNIHandles::weak_tag_mask == 1u);
  tbz(value, 0, not_weak);      // Test for jweak tag.

  // Resolve jweak.
  access_load_at(T_OBJECT, IN_NATIVE | ON_PHANTOM_OOP_REF,
                 Address(value, -JNIHandles::weak_tag_value), value, tmp1, tmp2, noreg);
  b(done);
  bind(not_weak);
  // Resolve (untagged) jobject.
  access_load_at(T_OBJECT, IN_NATIVE,
                 Address(value, 0), value, tmp1, tmp2, noreg);
  verify_oop(value);
  bind(done);
}


//////////////////////////////////////////////////////////////////////////////////

#ifdef AARCH64

void MacroAssembler::load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed) {
  switch (size_in_bytes) {
    case  8: ldr(dst, src); break;
    case  4: is_signed ? ldr_s32(dst, src) : ldr_u32(dst, src); break;
    case  2: is_signed ? ldrsh(dst, src) : ldrh(dst, src); break;
    case  1: is_signed ? ldrsb(dst, src) : ldrb(dst, src); break;
    default: ShouldNotReachHere();
  }
}

void MacroAssembler::store_sized_value(Register src, Address dst, size_t size_in_bytes) {
  switch (size_in_bytes) {
    case  8: str(src, dst);    break;
    case  4: str_32(src, dst); break;
    case  2: strh(src, dst);   break;
    case  1: strb(src, dst);   break;
    default: ShouldNotReachHere();
  }
}

#else

void MacroAssembler::load_sized_value(Register dst, Address src,
                                    size_t size_in_bytes, bool is_signed, AsmCondition cond) {
  switch (size_in_bytes) {
    case  4: ldr(dst, src, cond); break;
    case  2: is_signed ? ldrsh(dst, src, cond) : ldrh(dst, src, cond); break;
    case  1: is_signed ? ldrsb(dst, src, cond) : ldrb(dst, src, cond); break;
    default: ShouldNotReachHere();
  }
}


void MacroAssembler::store_sized_value(Register src, Address dst, size_t size_in_bytes, AsmCondition cond) {
  switch (size_in_bytes) {
    case  4: str(src, dst, cond); break;
    case  2: strh(src, dst, cond);   break;
    case  1: strb(src, dst, cond);   break;
    default: ShouldNotReachHere();
  }
}
#endif // AARCH64

// Look up the method for a megamorphic invokeinterface call.
// The target method is determined by <Rinterf, Rindex>.
// The receiver klass is in Rklass.
// On success, the result will be in method_result, and execution falls through.
// On failure, execution transfers to the given label.
void MacroAssembler::lookup_interface_method(Register Rklass,
                                             Register Rintf,
                                             RegisterOrConstant itable_index,
                                             Register method_result,
                                             Register Rscan,
                                             Register Rtmp,
                                             Label& L_no_such_interface) {

  assert_different_registers(Rklass, Rintf, Rscan, Rtmp);

  const int entry_size = itableOffsetEntry::size() * HeapWordSize;
  assert(itableOffsetEntry::interface_offset_in_bytes() == 0, "not added for convenience");

  // Compute start of first itableOffsetEntry (which is at the end of the vtable)
  const int base = in_bytes(Klass::vtable_start_offset());
  const int scale = exact_log2(vtableEntry::size_in_bytes());
  ldr_s32(Rtmp, Address(Rklass, Klass::vtable_length_offset())); // Get length of vtable
  add(Rscan, Rklass, base);
  add(Rscan, Rscan, AsmOperand(Rtmp, lsl, scale));

  // Search through the itable for an interface equal to incoming Rintf
  // itable looks like [intface][offset][intface][offset][intface][offset]

  Label loop;
  bind(loop);
  ldr(Rtmp, Address(Rscan, entry_size, post_indexed));
#ifdef AARCH64
  Label found;
  cmp(Rtmp, Rintf);
  b(found, eq);
  cbnz(Rtmp, loop);
#else
  cmp(Rtmp, Rintf);  // set ZF and CF if interface is found
  cmn(Rtmp, 0, ne);  // check if tmp == 0 and clear CF if it is
  b(loop, ne);
#endif // AARCH64

#ifdef AARCH64
  b(L_no_such_interface);
  bind(found);
#else
  // CF == 0 means we reached the end of itable without finding icklass
  b(L_no_such_interface, cc);
#endif // !AARCH64

  if (method_result != noreg) {
    // Interface found at previous position of Rscan, now load the method
    ldr_s32(Rtmp, Address(Rscan, itableOffsetEntry::offset_offset_in_bytes() - entry_size));
    if (itable_index.is_register()) {
      add(Rtmp, Rtmp, Rklass); // Add offset to Klass*
      assert(itableMethodEntry::size() * HeapWordSize == wordSize, "adjust the scaling in the code below");
      assert(itableMethodEntry::method_offset_in_bytes() == 0, "adjust the offset in the code below");
      ldr(method_result, Address::indexed_ptr(Rtmp, itable_index.as_register()));
    } else {
      int method_offset = itableMethodEntry::size() * HeapWordSize * itable_index.as_constant() +
                          itableMethodEntry::method_offset_in_bytes();
      add_slow(method_result, Rklass, method_offset);
      ldr(method_result, Address(method_result, Rtmp));
    }
  }
}

#ifdef COMPILER2
// TODO: 8 bytes at a time? pre-fetch?
// Compare char[] arrays aligned to 4 bytes.
void MacroAssembler::char_arrays_equals(Register ary1, Register ary2,
                                        Register limit, Register result,
                                      Register chr1, Register chr2, Label& Ldone) {
  Label Lvector, Lloop;

  // Note: limit contains number of bytes (2*char_elements) != 0.
  tst(limit, 0x2); // trailing character ?
  b(Lvector, eq);

  // compare the trailing char
  sub(limit, limit, sizeof(jchar));
  ldrh(chr1, Address(ary1, limit));
  ldrh(chr2, Address(ary2, limit));
  cmp(chr1, chr2);
  mov(result, 0, ne);     // not equal
  b(Ldone, ne);

  // only one char ?
  tst(limit, limit);
  mov(result, 1, eq);
  b(Ldone, eq);

  // word by word compare, dont't need alignment check
  bind(Lvector);

  // Shift ary1 and ary2 to the end of the arrays, negate limit
  add(ary1, limit, ary1);
  add(ary2, limit, ary2);
  neg(limit, limit);

  bind(Lloop);
  ldr_u32(chr1, Address(ary1, limit));
  ldr_u32(chr2, Address(ary2, limit));
  cmp_32(chr1, chr2);
  mov(result, 0, ne);     // not equal
  b(Ldone, ne);
  adds(limit, limit, 2*sizeof(jchar));
  b(Lloop, ne);

  // Caller should set it:
  // mov(result_reg, 1);  //equal
}
#endif

void MacroAssembler::inc_counter(address counter_addr, Register tmpreg1, Register tmpreg2) {
  mov_slow(tmpreg1, counter_addr);
  ldr_s32(tmpreg2, tmpreg1);
  add_32(tmpreg2, tmpreg2, 1);
  str_32(tmpreg2, tmpreg1);
}

void MacroAssembler::floating_cmp(Register dst) {
#ifdef AARCH64
  NOT_TESTED();
  cset(dst, gt);            // 1 if '>', else 0
  csinv(dst, dst, ZR, ge);  // previous value if '>=', else -1
#else
  vmrs(dst, FPSCR);
  orr(dst, dst, 0x08000000);
  eor(dst, dst, AsmOperand(dst, lsl, 3));
  mov(dst, AsmOperand(dst, asr, 30));
#endif
}

void MacroAssembler::restore_default_fp_mode() {
#ifdef AARCH64
  msr(SysReg_FPCR, ZR);
#else
#ifndef __SOFTFP__
  // Round to Near mode, IEEE compatible, masked exceptions
  mov(Rtemp, 0);
  vmsr(FPSCR, Rtemp);
#endif // !__SOFTFP__
#endif // AARCH64
}

#ifndef AARCH64
// 24-bit word range == 26-bit byte range
bool check26(int offset) {
  // this could be simplified, but it mimics encoding and decoding
  // an actual branch insrtuction
  int off1 = offset << 6 >> 8;
  int encoded = off1 & ((1<<24)-1);
  int decoded = encoded << 8 >> 6;
  return offset == decoded;
}
#endif // !AARCH64

// Perform some slight adjustments so the default 32MB code cache
// is fully reachable.
static inline address first_cache_address() {
  return CodeCache::low_bound() + sizeof(HeapBlock::Header);
}
static inline address last_cache_address() {
  return CodeCache::high_bound() - Assembler::InstructionSize;
}

#ifdef AARCH64
// Can we reach target using ADRP?
bool MacroAssembler::page_reachable_from_cache(address target) {
  intptr_t cl = (intptr_t)first_cache_address() & ~0xfff;
  intptr_t ch = (intptr_t)last_cache_address() & ~0xfff;
  intptr_t addr = (intptr_t)target & ~0xfff;

  intptr_t loffset = addr - cl;
  intptr_t hoffset = addr - ch;
  return is_imm_in_range(loffset >> 12, 21, 0) && is_imm_in_range(hoffset >> 12, 21, 0);
}
#endif

// Can we reach target using unconditional branch or call from anywhere
// in the code cache (because code can be relocated)?
bool MacroAssembler::_reachable_from_cache(address target) {
#ifdef __thumb__
  if ((1 & (intptr_t)target) != 0) {
    // Return false to avoid 'b' if we need switching to THUMB mode.
    return false;
  }
#endif

  address cl = first_cache_address();
  address ch = last_cache_address();

  if (ForceUnreachable) {
    // Only addresses from CodeCache can be treated as reachable.
    if (target < CodeCache::low_bound() || CodeCache::high_bound() < target) {
      return false;
    }
  }

  intptr_t loffset = (intptr_t)target - (intptr_t)cl;
  intptr_t hoffset = (intptr_t)target - (intptr_t)ch;

#ifdef AARCH64
  return is_offset_in_range(loffset, 26) && is_offset_in_range(hoffset, 26);
#else
  return check26(loffset - 8) && check26(hoffset - 8);
#endif
}

bool MacroAssembler::reachable_from_cache(address target) {
  assert(CodeCache::contains(pc()), "not supported");
  return _reachable_from_cache(target);
}

// Can we reach the entire code cache from anywhere else in the code cache?
bool MacroAssembler::_cache_fully_reachable() {
  address cl = first_cache_address();
  address ch = last_cache_address();
  return _reachable_from_cache(cl) && _reachable_from_cache(ch);
}

bool MacroAssembler::cache_fully_reachable() {
  assert(CodeCache::contains(pc()), "not supported");
  return _cache_fully_reachable();
}

void MacroAssembler::jump(address target, relocInfo::relocType rtype, Register scratch NOT_AARCH64_ARG(AsmCondition cond)) {
  assert((rtype == relocInfo::runtime_call_type) || (rtype == relocInfo::none), "not supported");
  if (reachable_from_cache(target)) {
    relocate(rtype);
    b(target NOT_AARCH64_ARG(cond));
    return;
  }

  // Note: relocate is not needed for the code below,
  // encoding targets in absolute format.
  if (ignore_non_patchable_relocations()) {
    rtype = relocInfo::none;
  }

#ifdef AARCH64
  assert (scratch != noreg, "should be specified");
  InlinedAddress address_literal(target, rtype);
  ldr_literal(scratch, address_literal);
  br(scratch);
  int off = offset();
  bind_literal(address_literal);
#ifdef COMPILER2
  if (offset() - off == wordSize) {
    // no padding, so insert nop for worst-case sizing
    nop();
  }
#endif
#else
  if (VM_Version::supports_movw() && (scratch != noreg) && (rtype == relocInfo::none)) {
    // Note: this version cannot be (atomically) patched
    mov_slow(scratch, (intptr_t)target, cond);
    bx(scratch, cond);
  } else {
    Label skip;
    InlinedAddress address_literal(target);
    if (cond != al) {
      b(skip, inverse(cond));
    }
    relocate(rtype);
    ldr_literal(PC, address_literal);
    bind_literal(address_literal);
    bind(skip);
  }
#endif // AARCH64
}

// Similar to jump except that:
// - near calls are valid only if any destination in the cache is near
// - no movt/movw (not atomically patchable)
void MacroAssembler::patchable_jump(address target, relocInfo::relocType rtype, Register scratch NOT_AARCH64_ARG(AsmCondition cond)) {
  assert((rtype == relocInfo::runtime_call_type) || (rtype == relocInfo::none), "not supported");
  if (cache_fully_reachable()) {
    // Note: this assumes that all possible targets (the initial one
    // and the addressed patched to) are all in the code cache.
    assert(CodeCache::contains(target), "target might be too far");
    relocate(rtype);
    b(target NOT_AARCH64_ARG(cond));
    return;
  }

  // Discard the relocation information if not needed for CacheCompiledCode
  // since the next encodings are all in absolute format.
  if (ignore_non_patchable_relocations()) {
    rtype = relocInfo::none;
  }

#ifdef AARCH64
  assert (scratch != noreg, "should be specified");
  InlinedAddress address_literal(target);
  relocate(rtype);
  ldr_literal(scratch, address_literal);
  br(scratch);
  int off = offset();
  bind_literal(address_literal);
#ifdef COMPILER2
  if (offset() - off == wordSize) {
    // no padding, so insert nop for worst-case sizing
    nop();
  }
#endif
#else
  {
    Label skip;
    InlinedAddress address_literal(target);
    if (cond != al) {
      b(skip, inverse(cond));
    }
    relocate(rtype);
    ldr_literal(PC, address_literal);
    bind_literal(address_literal);
    bind(skip);
  }
#endif // AARCH64
}

void MacroAssembler::call(address target, RelocationHolder rspec NOT_AARCH64_ARG(AsmCondition cond)) {
  Register scratch = LR;
  assert(rspec.type() == relocInfo::runtime_call_type || rspec.type() == relocInfo::none, "not supported");
  if (reachable_from_cache(target)) {
    relocate(rspec);
    bl(target NOT_AARCH64_ARG(cond));
    return;
  }

  // Note: relocate is not needed for the code below,
  // encoding targets in absolute format.
  if (ignore_non_patchable_relocations()) {
    // This assumes the information was needed only for relocating the code.
    rspec = RelocationHolder::none;
  }

#ifndef AARCH64
  if (VM_Version::supports_movw() && (rspec.type() == relocInfo::none)) {
    // Note: this version cannot be (atomically) patched
    mov_slow(scratch, (intptr_t)target, cond);
    blx(scratch, cond);
    return;
  }
#endif

  {
    Label ret_addr;
#ifndef AARCH64
    if (cond != al) {
      b(ret_addr, inverse(cond));
    }
#endif


#ifdef AARCH64
    // TODO-AARCH64: make more optimal implementation
    // [ Keep in sync with MacroAssembler::call_size ]
    assert(rspec.type() == relocInfo::none, "call reloc not implemented");
    mov_slow(scratch, target);
    blr(scratch);
#else
    InlinedAddress address_literal(target);
    relocate(rspec);
    adr(LR, ret_addr);
    ldr_literal(PC, address_literal);

    bind_literal(address_literal);
    bind(ret_addr);
#endif
  }
}

#if defined(AARCH64) && defined(COMPILER2)
int MacroAssembler::call_size(address target, bool far, bool patchable) {
  // FIXME: mov_slow is variable-length
  if (!far) return 1; // bl
  if (patchable) return 2;  // ldr; blr
  return instr_count_for_mov_slow((intptr_t)target) + 1;
}
#endif

int MacroAssembler::patchable_call(address target, RelocationHolder const& rspec, bool c2) {
  assert(rspec.type() == relocInfo::static_call_type ||
         rspec.type() == relocInfo::none ||
         rspec.type() == relocInfo::opt_virtual_call_type, "not supported");

  // Always generate the relocation information, needed for patching
  relocate(rspec); // used by NativeCall::is_call_before()
  if (cache_fully_reachable()) {
    // Note: this assumes that all possible targets (the initial one
    // and the addresses patched to) are all in the code cache.
    assert(CodeCache::contains(target), "target might be too far");
    bl(target);
  } else {
#if defined(AARCH64) && defined(COMPILER2)
    if (c2) {
      // return address needs to match call_size().
      // no need to trash Rtemp
      int off = offset();
      Label skip_literal;
      InlinedAddress address_literal(target);
      ldr_literal(LR, address_literal);
      blr(LR);
      int ret_addr_offset = offset();
      assert(offset() - off == call_size(target, true, true) * InstructionSize, "need to fix call_size()");
      b(skip_literal);
      int off2 = offset();
      bind_literal(address_literal);
      if (offset() - off2 == wordSize) {
        // no padding, so insert nop for worst-case sizing
        nop();
      }
      bind(skip_literal);
      return ret_addr_offset;
    }
#endif
    Label ret_addr;
    InlinedAddress address_literal(target);
#ifdef AARCH64
    ldr_literal(Rtemp, address_literal);
    adr(LR, ret_addr);
    br(Rtemp);
#else
    adr(LR, ret_addr);
    ldr_literal(PC, address_literal);
#endif
    bind_literal(address_literal);
    bind(ret_addr);
  }
  return offset();
}

// ((OopHandle)result).resolve();
void MacroAssembler::resolve_oop_handle(Register result) {
  // OopHandle::resolve is an indirection.
  ldr(result, Address(result, 0));
}

void MacroAssembler::load_mirror(Register mirror, Register method, Register tmp) {
  const int mirror_offset = in_bytes(Klass::java_mirror_offset());
  ldr(tmp, Address(method, Method::const_offset()));
  ldr(tmp, Address(tmp,  ConstMethod::constants_offset()));
  ldr(tmp, Address(tmp, ConstantPool::pool_holder_offset_in_bytes()));
  ldr(mirror, Address(tmp, mirror_offset));
  resolve_oop_handle(mirror);
}


///////////////////////////////////////////////////////////////////////////////

// Compressed pointers

#ifdef AARCH64

void MacroAssembler::load_klass(Register dst_klass, Register src_oop) {
  if (UseCompressedClassPointers) {
    ldr_w(dst_klass, Address(src_oop, oopDesc::klass_offset_in_bytes()));
    decode_klass_not_null(dst_klass);
  } else {
    ldr(dst_klass, Address(src_oop, oopDesc::klass_offset_in_bytes()));
  }
}

#else

void MacroAssembler::load_klass(Register dst_klass, Register src_oop, AsmCondition cond) {
  ldr(dst_klass, Address(src_oop, oopDesc::klass_offset_in_bytes()), cond);
}

#endif // AARCH64

// Blows src_klass.
void MacroAssembler::store_klass(Register src_klass, Register dst_oop) {
#ifdef AARCH64
  if (UseCompressedClassPointers) {
    assert(src_klass != dst_oop, "not enough registers");
    encode_klass_not_null(src_klass);
    str_w(src_klass, Address(dst_oop, oopDesc::klass_offset_in_bytes()));
    return;
  }
#endif // AARCH64
  str(src_klass, Address(dst_oop, oopDesc::klass_offset_in_bytes()));
}

#ifdef AARCH64

void MacroAssembler::store_klass_gap(Register dst) {
  if (UseCompressedClassPointers) {
    str_w(ZR, Address(dst, oopDesc::klass_gap_offset_in_bytes()));
  }
}

#endif // AARCH64


void MacroAssembler::load_heap_oop(Register dst, Address src, Register tmp1, Register tmp2, Register tmp3, DecoratorSet decorators) {
  access_load_at(T_OBJECT, IN_HEAP | decorators, src, dst, tmp1, tmp2, tmp3);
}

// Blows src and flags.
void MacroAssembler::store_heap_oop(Address obj, Register new_val, Register tmp1, Register tmp2, Register tmp3, DecoratorSet decorators) {
  access_store_at(T_OBJECT, IN_HEAP | decorators, obj, new_val, tmp1, tmp2, tmp3, false);
}

void MacroAssembler::store_heap_oop_null(Address obj, Register new_val, Register tmp1, Register tmp2, Register tmp3, DecoratorSet decorators) {
  access_store_at(T_OBJECT, IN_HEAP, obj, new_val, tmp1, tmp2, tmp3, true);
}

void MacroAssembler::access_load_at(BasicType type, DecoratorSet decorators,
                                    Address src, Register dst, Register tmp1, Register tmp2, Register tmp3) {
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  decorators = AccessInternal::decorator_fixup(decorators);
  bool as_raw = (decorators & AS_RAW) != 0;
  if (as_raw) {
    bs->BarrierSetAssembler::load_at(this, decorators, type, dst, src, tmp1, tmp2, tmp3);
  } else {
    bs->load_at(this, decorators, type, dst, src, tmp1, tmp2, tmp3);
  }
}

void MacroAssembler::access_store_at(BasicType type, DecoratorSet decorators,
                                     Address obj, Register new_val, Register tmp1, Register tmp2, Register tmp3, bool is_null) {
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  decorators = AccessInternal::decorator_fixup(decorators);
  bool as_raw = (decorators & AS_RAW) != 0;
  if (as_raw) {
    bs->BarrierSetAssembler::store_at(this, decorators, type, obj, new_val, tmp1, tmp2, tmp3, is_null);
  } else {
    bs->store_at(this, decorators, type, obj, new_val, tmp1, tmp2, tmp3, is_null);
  }
}


#ifdef AARCH64

// Algorithm must match oop.inline.hpp encode_heap_oop.
void MacroAssembler::encode_heap_oop(Register dst, Register src) {
  // This code pattern is matched in NativeIntruction::skip_encode_heap_oop.
  // Update it at modifications.
  assert (UseCompressedOops, "must be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
#ifdef ASSERT
  verify_heapbase("MacroAssembler::encode_heap_oop: heap base corrupted?");
#endif
  verify_oop(src);
  if (Universe::narrow_oop_base() == NULL) {
    if (Universe::narrow_oop_shift() != 0) {
      assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
      _lsr(dst, src, Universe::narrow_oop_shift());
    } else if (dst != src) {
      mov(dst, src);
    }
  } else {
    tst(src, src);
    csel(dst, Rheap_base, src, eq);
    sub(dst, dst, Rheap_base);
    if (Universe::narrow_oop_shift() != 0) {
      assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
      _lsr(dst, dst, Universe::narrow_oop_shift());
    }
  }
}

// Same algorithm as oop.inline.hpp decode_heap_oop.
void MacroAssembler::decode_heap_oop(Register dst, Register src) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::decode_heap_oop: heap base corrupted?");
#endif
  assert(Universe::narrow_oop_shift() == 0 || LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
  if (Universe::narrow_oop_base() != NULL) {
    tst(src, src);
    add(dst, Rheap_base, AsmOperand(src, lsl, Universe::narrow_oop_shift()));
    csel(dst, dst, ZR, ne);
  } else {
    _lsl(dst, src, Universe::narrow_oop_shift());
  }
  verify_oop(dst);
}

#ifdef COMPILER2
// Algorithm must match oop.inline.hpp encode_heap_oop.
// Must preserve condition codes, or C2 encodeHeapOop_not_null rule
// must be changed.
void MacroAssembler::encode_heap_oop_not_null(Register dst, Register src) {
  assert (UseCompressedOops, "must be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
#ifdef ASSERT
  verify_heapbase("MacroAssembler::encode_heap_oop: heap base corrupted?");
#endif
  verify_oop(src);
  if (Universe::narrow_oop_base() == NULL) {
    if (Universe::narrow_oop_shift() != 0) {
      assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
      _lsr(dst, src, Universe::narrow_oop_shift());
    } else if (dst != src) {
          mov(dst, src);
    }
  } else {
    sub(dst, src, Rheap_base);
    if (Universe::narrow_oop_shift() != 0) {
      assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
      _lsr(dst, dst, Universe::narrow_oop_shift());
    }
  }
}

// Same algorithm as oops.inline.hpp decode_heap_oop.
// Must preserve condition codes, or C2 decodeHeapOop_not_null rule
// must be changed.
void MacroAssembler::decode_heap_oop_not_null(Register dst, Register src) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::decode_heap_oop: heap base corrupted?");
#endif
  assert(Universe::narrow_oop_shift() == 0 || LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
  if (Universe::narrow_oop_base() != NULL) {
    add(dst, Rheap_base, AsmOperand(src, lsl, Universe::narrow_oop_shift()));
  } else {
    _lsl(dst, src, Universe::narrow_oop_shift());
  }
  verify_oop(dst);
}

void MacroAssembler::set_narrow_klass(Register dst, Klass* k) {
  assert(UseCompressedClassPointers, "should only be used for compressed header");
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int klass_index = oop_recorder()->find_index(k);
  RelocationHolder rspec = metadata_Relocation::spec(klass_index);

  // Relocation with special format (see relocInfo_arm.hpp).
  relocate(rspec);
  narrowKlass encoded_k = Klass::encode_klass(k);
  movz(dst, encoded_k & 0xffff, 0);
  movk(dst, (encoded_k >> 16) & 0xffff, 16);
}

void MacroAssembler::set_narrow_oop(Register dst, jobject obj) {
  assert(UseCompressedOops, "should only be used for compressed header");
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);

  relocate(rspec);
  movz(dst, 0xffff, 0);
  movk(dst, 0xffff, 16);
}

#endif // COMPILER2
// Must preserve condition codes, or C2 encodeKlass_not_null rule
// must be changed.
void MacroAssembler::encode_klass_not_null(Register r) {
  if (Universe::narrow_klass_base() != NULL) {
    // Use Rheap_base as a scratch register in which to temporarily load the narrow_klass_base.
    assert(r != Rheap_base, "Encoding a klass in Rheap_base");
    mov_slow(Rheap_base, Universe::narrow_klass_base());
    sub(r, r, Rheap_base);
  }
  if (Universe::narrow_klass_shift() != 0) {
    assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift(), "decode alg wrong");
    _lsr(r, r, Universe::narrow_klass_shift());
  }
  if (Universe::narrow_klass_base() != NULL) {
    reinit_heapbase();
  }
}

// Must preserve condition codes, or C2 encodeKlass_not_null rule
// must be changed.
void MacroAssembler::encode_klass_not_null(Register dst, Register src) {
  if (dst == src) {
    encode_klass_not_null(src);
    return;
  }
  if (Universe::narrow_klass_base() != NULL) {
    mov_slow(dst, (int64_t)Universe::narrow_klass_base());
    sub(dst, src, dst);
    if (Universe::narrow_klass_shift() != 0) {
      assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift(), "decode alg wrong");
      _lsr(dst, dst, Universe::narrow_klass_shift());
    }
  } else {
    if (Universe::narrow_klass_shift() != 0) {
      assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift(), "decode alg wrong");
      _lsr(dst, src, Universe::narrow_klass_shift());
    } else {
      mov(dst, src);
    }
  }
}

// Function instr_count_for_decode_klass_not_null() counts the instructions
// generated by decode_klass_not_null(register r) and reinit_heapbase(),
// when (Universe::heap() != NULL).  Hence, if the instructions they
// generate change, then this method needs to be updated.
int MacroAssembler::instr_count_for_decode_klass_not_null() {
  assert(UseCompressedClassPointers, "only for compressed klass ptrs");
  assert(Universe::heap() != NULL, "java heap should be initialized");
  if (Universe::narrow_klass_base() != NULL) {
    return instr_count_for_mov_slow(Universe::narrow_klass_base()) + // mov_slow
      1 +                                                                 // add
      instr_count_for_mov_slow(Universe::narrow_ptrs_base());   // reinit_heapbase() = mov_slow
  } else {
    if (Universe::narrow_klass_shift() != 0) {
      return 1;
    }
  }
  return 0;
}

// Must preserve condition codes, or C2 decodeKlass_not_null rule
// must be changed.
void MacroAssembler::decode_klass_not_null(Register r) {
  int off = offset();
  assert(UseCompressedClassPointers, "should only be used for compressed headers");
  assert(Universe::heap() != NULL, "java heap should be initialized");
  assert(r != Rheap_base, "Decoding a klass in Rheap_base");
  // Cannot assert, instr_count_for_decode_klass_not_null() counts instructions.
  // Also do not verify_oop as this is called by verify_oop.
  if (Universe::narrow_klass_base() != NULL) {
    // Use Rheap_base as a scratch register in which to temporarily load the narrow_klass_base.
    mov_slow(Rheap_base, Universe::narrow_klass_base());
    add(r, Rheap_base, AsmOperand(r, lsl, Universe::narrow_klass_shift()));
    reinit_heapbase();
  } else {
    if (Universe::narrow_klass_shift() != 0) {
      assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift(), "decode alg wrong");
      _lsl(r, r, Universe::narrow_klass_shift());
    }
  }
  assert((offset() - off) == (instr_count_for_decode_klass_not_null() * InstructionSize), "need to fix instr_count_for_decode_klass_not_null");
}

// Must preserve condition codes, or C2 decodeKlass_not_null rule
// must be changed.
void MacroAssembler::decode_klass_not_null(Register dst, Register src) {
  if (src == dst) {
    decode_klass_not_null(src);
    return;
  }

  assert(UseCompressedClassPointers, "should only be used for compressed headers");
  assert(Universe::heap() != NULL, "java heap should be initialized");
  assert(src != Rheap_base, "Decoding a klass in Rheap_base");
  assert(dst != Rheap_base, "Decoding a klass into Rheap_base");
  // Also do not verify_oop as this is called by verify_oop.
  if (Universe::narrow_klass_base() != NULL) {
    mov_slow(dst, Universe::narrow_klass_base());
    add(dst, dst, AsmOperand(src, lsl, Universe::narrow_klass_shift()));
  } else {
    _lsl(dst, src, Universe::narrow_klass_shift());
  }
}


void MacroAssembler::reinit_heapbase() {
  if (UseCompressedOops || UseCompressedClassPointers) {
    if (Universe::heap() != NULL) {
      mov_slow(Rheap_base, Universe::narrow_ptrs_base());
    } else {
      ldr_global_ptr(Rheap_base, (address)Universe::narrow_ptrs_base_addr());
    }
  }
}

#ifdef ASSERT
void MacroAssembler::verify_heapbase(const char* msg) {
  // This code pattern is matched in NativeIntruction::skip_verify_heapbase.
  // Update it at modifications.
  assert (UseCompressedOops, "should be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  if (CheckCompressedOops) {
    Label ok;
    str(Rthread, Address(Rthread, in_bytes(JavaThread::in_top_frame_unsafe_section_offset())));
    raw_push(Rtemp, ZR);
    mrs(Rtemp, Assembler::SysReg_NZCV);
    str(Rtemp, Address(SP, 1 * wordSize));
    mov_slow(Rtemp, Universe::narrow_ptrs_base());
    cmp(Rheap_base, Rtemp);
    b(ok, eq);
    stop(msg);
    bind(ok);
    ldr(Rtemp, Address(SP, 1 * wordSize));
    msr(Assembler::SysReg_NZCV, Rtemp);
    raw_pop(Rtemp, ZR);
    str(ZR, Address(Rthread, in_bytes(JavaThread::in_top_frame_unsafe_section_offset())));
  }
}
#endif // ASSERT

#endif // AARCH64

#ifdef COMPILER2
void MacroAssembler::fast_lock(Register Roop, Register Rbox, Register Rscratch, Register Rscratch2 AARCH64_ONLY_ARG(Register Rscratch3))
{
  assert(VM_Version::supports_ldrex(), "unsupported, yet?");

  Register Rmark      = Rscratch2;

  assert(Roop != Rscratch, "");
  assert(Roop != Rmark, "");
  assert(Rbox != Rscratch, "");
  assert(Rbox != Rmark, "");

  Label fast_lock, done;

  if (UseBiasedLocking && !UseOptoBiasInlining) {
    Label failed;
#ifdef AARCH64
    biased_locking_enter(Roop, Rmark, Rscratch, false, Rscratch3, done, failed);
#else
    biased_locking_enter(Roop, Rmark, Rscratch, false, noreg, done, failed);
#endif
    bind(failed);
  }

  ldr(Rmark, Address(Roop, oopDesc::mark_offset_in_bytes()));
  tst(Rmark, markOopDesc::unlocked_value);
  b(fast_lock, ne);

  // Check for recursive lock
  // See comments in InterpreterMacroAssembler::lock_object for
  // explanations on the fast recursive locking check.
#ifdef AARCH64
  intptr_t mask = ((intptr_t)3) - ((intptr_t)os::vm_page_size());
  Assembler::LogicalImmediate imm(mask, false);
  mov(Rscratch, SP);
  sub(Rscratch, Rmark, Rscratch);
  ands(Rscratch, Rscratch, imm);
  // set to zero if recursive lock, set to non zero otherwise (see discussion in JDK-8153107)
  str(Rscratch, Address(Rbox, BasicLock::displaced_header_offset_in_bytes()));
  b(done);

#else
  // -1- test low 2 bits
  movs(Rscratch, AsmOperand(Rmark, lsl, 30));
  // -2- test (hdr - SP) if the low two bits are 0
  sub(Rscratch, Rmark, SP, eq);
  movs(Rscratch, AsmOperand(Rscratch, lsr, exact_log2(os::vm_page_size())), eq);
  // If still 'eq' then recursive locking OK
  // set to zero if recursive lock, set to non zero otherwise (see discussion in JDK-8153107)
  str(Rscratch, Address(Rbox, BasicLock::displaced_header_offset_in_bytes()));
  b(done);
#endif

  bind(fast_lock);
  str(Rmark, Address(Rbox, BasicLock::displaced_header_offset_in_bytes()));

  bool allow_fallthrough_on_failure = true;
  bool one_shot = true;
  cas_for_lock_acquire(Rmark, Rbox, Roop, Rscratch, done, allow_fallthrough_on_failure, one_shot);

  bind(done);

}

void MacroAssembler::fast_unlock(Register Roop, Register Rbox, Register Rscratch, Register Rscratch2  AARCH64_ONLY_ARG(Register Rscratch3))
{
  assert(VM_Version::supports_ldrex(), "unsupported, yet?");

  Register Rmark      = Rscratch2;

  assert(Roop != Rscratch, "");
  assert(Roop != Rmark, "");
  assert(Rbox != Rscratch, "");
  assert(Rbox != Rmark, "");

  Label done;

  if (UseBiasedLocking && !UseOptoBiasInlining) {
    biased_locking_exit(Roop, Rscratch, done);
  }

  ldr(Rmark, Address(Rbox, BasicLock::displaced_header_offset_in_bytes()));
  // If hdr is NULL, we've got recursive locking and there's nothing more to do
  cmp(Rmark, 0);
  b(done, eq);

  // Restore the object header
  bool allow_fallthrough_on_failure = true;
  bool one_shot = true;
  cas_for_lock_release(Rmark, Rbox, Roop, Rscratch, done, allow_fallthrough_on_failure, one_shot);

  bind(done);

}
#endif // COMPILER2
