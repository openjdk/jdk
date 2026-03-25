/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2021, Red Hat Inc. All rights reserved.
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

#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/arrayOop.hpp"
#include "oops/markWord.hpp"
#include "runtime/arguments.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"

void C1_MacroAssembler::float_cmp(bool is_float, int unordered_result,
                                  FloatRegister f0, FloatRegister f1,
                                  Register result)
{
  Label done;
  if (is_float) {
    fcmps(f0, f1);
  } else {
    fcmpd(f0, f1);
  }
  if (unordered_result < 0) {
    // we want -1 for unordered or less than, 0 for equal and 1 for
    // greater than.
    cset(result, NE);  // Not equal or unordered
    cneg(result, result, LT);  // Less than or unordered
  } else {
    // we want -1 for less than, 0 for equal and 1 for unordered or
    // greater than.
    cset(result, NE);  // Not equal or unordered
    cneg(result, result, LO);  // Less than
  }
}

int C1_MacroAssembler::lock_object(Register hdr, Register obj, Register basic_lock, Register temp, Label& slow_case) {
  assert_different_registers(hdr, obj, basic_lock, temp, rscratch2);
  int null_check_offset = -1;

  verify_oop(obj);

  // save object being locked into the BasicObjectLock
  str(obj, Address(basic_lock, BasicObjectLock::obj_offset()));

  null_check_offset = offset();

  fast_lock(basic_lock, obj, hdr, temp, rscratch2, slow_case);

  return null_check_offset;
}


void C1_MacroAssembler::unlock_object(Register hdr, Register obj, Register basic_lock, Register temp, Label& slow_case) {
  assert_different_registers(hdr, obj, basic_lock, temp, rscratch2);

  // load object
  ldr(obj, Address(basic_lock, BasicObjectLock::obj_offset()));
  verify_oop(obj);

  fast_unlock(obj, hdr, temp, rscratch2, slow_case);
}


// Defines obj, preserves var_size_in_bytes
void C1_MacroAssembler::try_allocate(Register obj, Register var_size_in_bytes, int con_size_in_bytes, Register t1, Register t2, Label& slow_case) {
  if (UseTLAB) {
    tlab_allocate(obj, var_size_in_bytes, con_size_in_bytes, t1, t2, slow_case);
  } else {
    b(slow_case);
  }
}

void C1_MacroAssembler::initialize_header(Register obj, Register klass, Register len, Register t1, Register t2) {
  assert_different_registers(obj, klass, len);

  if (UseCompactObjectHeaders || Arguments::is_valhalla_enabled()) {
    // COH: Markword contains class pointer which is only known at runtime.
    // Valhalla: Could have value class which has a different prototype header to a normal object.
    // In both cases, we need to fetch dynamically.
    ldr(t1, Address(klass, Klass::prototype_header_offset()));
    str(t1, Address(obj, oopDesc::mark_offset_in_bytes()));
  } else {
    // Otherwise: Can use the statically computed prototype header which is the same for every object.
    mov(t1, checked_cast<int32_t>(markWord::prototype().value()));
    str(t1, Address(obj, oopDesc::mark_offset_in_bytes()));
  }

  if (!UseCompactObjectHeaders) {
    // COH: Markword already contains class pointer. Nothing else to do.
    // Otherwise: Fetch klass pointer following the markword
    if (UseCompressedClassPointers) { // Take care not to kill klass
      encode_klass_not_null(t1, klass);
      strw(t1, Address(obj, oopDesc::klass_offset_in_bytes()));
    } else {
      str(klass, Address(obj, oopDesc::klass_offset_in_bytes()));
    }
  }

  if (len->is_valid()) {
    strw(len, Address(obj, arrayOopDesc::length_offset_in_bytes()));
    int base_offset = arrayOopDesc::length_offset_in_bytes() + BytesPerInt;
    if (!is_aligned(base_offset, BytesPerWord)) {
      assert(is_aligned(base_offset, BytesPerInt), "must be 4-byte aligned");
      // Clear gap/first 4 bytes following the length field.
      strw(zr, Address(obj, base_offset));
    }
  } else if (UseCompressedClassPointers && !UseCompactObjectHeaders) {
    store_klass_gap(obj, zr);
  }
}

// preserves obj, destroys len_in_bytes
//
// Scratch registers: t1 = r10, t2 = r11
//
void C1_MacroAssembler::initialize_body(Register obj, Register len_in_bytes, int hdr_size_in_bytes, Register t1, Register t2) {
  assert(hdr_size_in_bytes >= 0, "header size must be positive or 0");
  assert(t1 == r10 && t2 == r11, "must be");

  Label done;

  // len_in_bytes is positive and ptr sized
  subs(len_in_bytes, len_in_bytes, hdr_size_in_bytes);
  br(Assembler::EQ, done);

  // zero_words() takes ptr in r10 and count in words in r11
  mov(rscratch1, len_in_bytes);
  lea(t1, Address(obj, hdr_size_in_bytes));
  lsr(t2, rscratch1, LogBytesPerWord);
  address tpc = zero_words(t1, t2);

  bind(done);
  if (tpc == nullptr) {
    Compilation::current()->bailout("no space for trampoline stub");
  }
}


void C1_MacroAssembler::allocate_object(Register obj, Register t1, Register t2, int header_size, int object_size, Register klass, Label& slow_case) {
  assert_different_registers(obj, t1, t2); // XXX really?
  assert(header_size >= 0 && object_size >= header_size, "illegal sizes");

  try_allocate(obj, noreg, object_size * BytesPerWord, t1, t2, slow_case);

  initialize_object(obj, klass, noreg, object_size * HeapWordSize, t1, t2, UseTLAB);
}

// Scratch registers: t1 = r10, t2 = r11
void C1_MacroAssembler::initialize_object(Register obj, Register klass, Register var_size_in_bytes, int con_size_in_bytes, Register t1, Register t2, bool is_tlab_allocated) {
  assert((con_size_in_bytes & MinObjAlignmentInBytesMask) == 0,
         "con_size_in_bytes is not multiple of alignment");
  const int hdr_size_in_bytes = instanceOopDesc::header_size() * HeapWordSize;

  initialize_header(obj, klass, noreg, t1, t2);

  if (!(UseTLAB && ZeroTLAB && is_tlab_allocated)) {
     // clear rest of allocated space
     const Register index = t2;
     if (var_size_in_bytes != noreg) {
       mov(index, var_size_in_bytes);
       initialize_body(obj, index, hdr_size_in_bytes, t1, t2);
       if (Compilation::current()->bailed_out()) {
         return;
       }
     } else if (con_size_in_bytes > hdr_size_in_bytes) {
       con_size_in_bytes -= hdr_size_in_bytes;
       lea(t1, Address(obj, hdr_size_in_bytes));
       address tpc = zero_words(t1, con_size_in_bytes / BytesPerWord);
       if (tpc == nullptr) {
         Compilation::current()->bailout("no space for trampoline stub");
         return;
       }
     }
  }

  membar(StoreStore);

  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == r0, "must be");
    far_call(RuntimeAddress(Runtime1::entry_for(StubId::c1_dtrace_object_alloc_id)));
  }

  verify_oop(obj);
}
void C1_MacroAssembler::allocate_array(Register obj, Register len, Register t1, Register t2, int base_offset_in_bytes, int f, Register klass, Label& slow_case, bool zero_array) {
  assert_different_registers(obj, len, t1, t2, klass);

  // determine alignment mask
  assert(!(BytesPerWord & 1), "must be a multiple of 2 for masking code to work");

  // check for negative or excessive length
  mov(rscratch1, (int32_t)max_array_allocation_length);
  cmp(len, rscratch1);
  br(Assembler::HS, slow_case);

  const Register arr_size = t2; // okay to be the same
  // align object end
  mov(arr_size, (int32_t)base_offset_in_bytes + MinObjAlignmentInBytesMask);
  add(arr_size, arr_size, len, ext::uxtw, f);
  andr(arr_size, arr_size, ~MinObjAlignmentInBytesMask);

  try_allocate(obj, arr_size, 0, t1, t2, slow_case);

  initialize_header(obj, klass, len, t1, t2);

  // Align-up to word boundary, because we clear the 4 bytes potentially
  // following the length field in initialize_header().
  int base_offset = align_up(base_offset_in_bytes, BytesPerWord);
  // clear rest of allocated space
  if (zero_array) {
    initialize_body(obj, arr_size, base_offset, t1, t2);
  }
  if (Compilation::current()->bailed_out()) {
    return;
  }

  membar(StoreStore);

  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == r0, "must be");
    far_call(RuntimeAddress(Runtime1::entry_for(StubId::c1_dtrace_object_alloc_id)));
  }

  verify_oop(obj);
}

void C1_MacroAssembler::build_frame_helper(int frame_size_in_bytes, int sp_offset_for_orig_pc, int sp_inc, bool reset_orig_pc, bool needs_stack_repair) {
  MacroAssembler::build_frame(frame_size_in_bytes);

  if (needs_stack_repair) {
    save_stack_increment(sp_inc, frame_size_in_bytes);
  }
  if (reset_orig_pc) {
    // Zero orig_pc to detect deoptimization during buffering in the entry points
    str(zr, Address(sp, sp_offset_for_orig_pc));
  }
}

void C1_MacroAssembler::build_frame(int frame_size_in_bytes, int bang_size_in_bytes, int sp_offset_for_orig_pc, bool needs_stack_repair, bool has_scalarized_args, Label* verified_inline_entry_label) {
  // Make sure there is enough stack space for this method's activation.
  // Note that we do this before creating a frame.
  assert(bang_size_in_bytes >= frame_size_in_bytes, "stack bang size incorrect");
  generate_stack_overflow_check(bang_size_in_bytes);

  build_frame_helper(frame_size_in_bytes, sp_offset_for_orig_pc, 0, has_scalarized_args, needs_stack_repair);

  // Insert nmethod entry barrier into frame.
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->nmethod_entry_barrier(this, nullptr /* slow_path */, nullptr /* continuation */, nullptr /* guard */);

  if (verified_inline_entry_label != nullptr) {
    // Jump here from the scalarized entry points that already created the frame.
    bind(*verified_inline_entry_label);
  }
}

void C1_MacroAssembler::verified_entry(bool breakAtEntry) {
  // If we have to make this method not-entrant we'll overwrite its
  // first instruction with a jump.  For this action to be legal we
  // must ensure that this first instruction is a B, BL, NOP, BKPT,
  // SVC, HVC, or SMC.  Make it a NOP.
  nop();
  if (C1Breakpoint) brk(1);
}

int C1_MacroAssembler::scalarized_entry(const CompiledEntrySignature* ces, int frame_size_in_bytes, int bang_size_in_bytes, int sp_offset_for_orig_pc, Label& verified_inline_entry_label, bool is_inline_ro_entry) {
  assert(InlineTypePassFieldsAsArgs, "sanity");
  // Make sure there is enough stack space for this method's activation.
  assert(bang_size_in_bytes >= frame_size_in_bytes, "stack bang size incorrect");
  generate_stack_overflow_check(bang_size_in_bytes);

  GrowableArray<SigEntry>* sig    = ces->sig();
  GrowableArray<SigEntry>* sig_cc = is_inline_ro_entry ? ces->sig_cc_ro() : ces->sig_cc();
  VMRegPair* regs      = ces->regs();
  VMRegPair* regs_cc   = is_inline_ro_entry ? ces->regs_cc_ro() : ces->regs_cc();
  int args_on_stack    = ces->args_on_stack();
  int args_on_stack_cc = is_inline_ro_entry ? ces->args_on_stack_cc_ro() : ces->args_on_stack_cc();

  assert(sig->length() <= sig_cc->length(), "Zero-sized inline class not allowed!");
  BasicType* sig_bt = NEW_RESOURCE_ARRAY(BasicType, sig_cc->length());
  int args_passed = sig->length();
  int args_passed_cc = SigEntry::fill_sig_bt(sig_cc, sig_bt);

  // Create a temp frame so we can call into the runtime. It must be properly set up to accommodate GC.
  build_frame_helper(frame_size_in_bytes, sp_offset_for_orig_pc, 0, true, ces->c1_needs_stack_repair());

  // The runtime call might safepoint, make sure nmethod entry barrier is executed
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  // C1 code is not hot enough to micro optimize the nmethod entry barrier with an out-of-line stub
  bs->nmethod_entry_barrier(this, nullptr /* slow_path */, nullptr /* continuation */, nullptr /* guard */);

  // FIXME -- call runtime only if we cannot in-line allocate all the incoming inline type args.
  mov(r19, (intptr_t) ces->method());
  if (is_inline_ro_entry) {
    far_call(RuntimeAddress(Runtime1::entry_for(StubId::c1_buffer_inline_args_no_receiver_id)));
  } else {
    far_call(RuntimeAddress(Runtime1::entry_for(StubId::c1_buffer_inline_args_id)));
  }
  int rt_call_offset = offset();

  // The runtime call returns the new array in r20 instead of the usual r0
  // because r0 is also j_rarg7 which may be holding a live argument here.
  Register val_array = r20;

  // Remove the temp frame
  MacroAssembler::remove_frame(frame_size_in_bytes);

  // Check if we need to extend the stack for packing
  int sp_inc = 0;
  if (args_on_stack > args_on_stack_cc) {
    sp_inc = extend_stack_for_inline_args(args_on_stack);
  }

  shuffle_inline_args(true, is_inline_ro_entry, sig_cc,
                      args_passed_cc, args_on_stack_cc, regs_cc, // from
                      args_passed, args_on_stack, regs,          // to
                      sp_inc, val_array);

  // Create the real frame. Below jump will then skip over the stack banging and frame
  // setup code in the verified_inline_entry (which has a different real_frame_size).
  build_frame_helper(frame_size_in_bytes, sp_offset_for_orig_pc, sp_inc, false, ces->c1_needs_stack_repair());

  b(verified_inline_entry_label);
  return rt_call_offset;
}


void C1_MacroAssembler::load_parameter(int offset_in_words, Register reg) {
  // rfp, + 0: link
  //     + 1: return address
  //     + 2: argument with offset 0
  //     + 3: argument with offset 1
  //     + 4: ...

  ldr(reg, Address(rfp, (offset_in_words + 2) * BytesPerWord));
}

#ifndef PRODUCT

void C1_MacroAssembler::verify_stack_oop(int stack_offset) {
  if (!VerifyOops) return;
  verify_oop_addr(Address(sp, stack_offset));
}

void C1_MacroAssembler::verify_not_null_oop(Register r) {
  if (!VerifyOops) return;
  Label not_null;
  cbnz(r, not_null);
  stop("non-null oop required");
  bind(not_null);
  verify_oop(r);
}

void C1_MacroAssembler::invalidate_registers(bool inv_r0, bool inv_r19, bool inv_r2, bool inv_r3, bool inv_r4, bool inv_r5) {
#ifdef ASSERT
  static int nn;
  if (inv_r0) mov(r0, 0xDEAD);
  if (inv_r19) mov(r19, 0xDEAD);
  if (inv_r2) mov(r2, nn++);
  if (inv_r3) mov(r3, 0xDEAD);
  if (inv_r4) mov(r4, 0xDEAD);
  if (inv_r5) mov(r5, 0xDEAD);
#endif
}
#endif // ifndef PRODUCT
