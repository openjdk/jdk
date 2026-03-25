/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "code/compiledIC.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/arrayOop.hpp"
#include "oops/markWord.hpp"
#include "runtime/arguments.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/globalDefinitions.hpp"

int C1_MacroAssembler::lock_object(Register hdr, Register obj, Register basic_lock, Register tmp, Label& slow_case) {
  assert(hdr == rax, "hdr must be rax, for the cmpxchg instruction");
  assert_different_registers(hdr, obj, basic_lock, tmp);
  int null_check_offset = -1;

  verify_oop(obj);

  // save object being locked into the BasicObjectLock
  movptr(Address(basic_lock, BasicObjectLock::obj_offset()), obj);

  null_check_offset = offset();

  fast_lock(basic_lock, obj, hdr, tmp, slow_case);

  return null_check_offset;
}

void C1_MacroAssembler::unlock_object(Register hdr, Register obj, Register basic_lock, Label& slow_case) {
  assert(basic_lock == rax, "basic_lock must be rax, for the cmpxchg instruction");
  assert(hdr != obj && hdr != basic_lock && obj != basic_lock, "registers must be different");

  // load object
  movptr(obj, Address(basic_lock, BasicObjectLock::obj_offset()));
  verify_oop(obj);

  fast_unlock(obj, rax, hdr, slow_case);
}


// Defines obj, preserves var_size_in_bytes
void C1_MacroAssembler::try_allocate(Register obj, Register var_size_in_bytes, int con_size_in_bytes, Register t1, Register t2, Label& slow_case) {
  if (UseTLAB) {
    tlab_allocate(obj, var_size_in_bytes, con_size_in_bytes, t1, t2, slow_case);
  } else {
    jmp(slow_case);
  }
}


void C1_MacroAssembler::initialize_header(Register obj, Register klass, Register len, Register t1, Register t2) {
  assert_different_registers(obj, klass, len, t1, t2);
  if (UseCompactObjectHeaders || Arguments::is_valhalla_enabled()) {
    // COH: Markword contains class pointer which is only known at runtime.
    // Valhalla: Could have value class which has a different prototype header to a normal object.
    // In both cases, we need to fetch dynamically.
    movptr(t1, Address(klass, Klass::prototype_header_offset()));
    movptr(Address(obj, oopDesc::mark_offset_in_bytes()), t1);
  } else {
    // Otherwise: Can use the statically computed prototype header which is the same for every object.
    movptr(Address(obj, oopDesc::mark_offset_in_bytes()), checked_cast<int32_t>(markWord::prototype().value()));
  }
  if (!UseCompactObjectHeaders) {
    // COH: Markword already contains class pointer. Nothing else to do.
    // Otherwise: Fetch klass pointer following the markword
    if (UseCompressedClassPointers) { // Take care not to kill klass
      movptr(t1, klass);
      encode_klass_not_null(t1, rscratch1);
      movl(Address(obj, oopDesc::klass_offset_in_bytes()), t1);
    } else {
      movptr(Address(obj, oopDesc::klass_offset_in_bytes()), klass);
    }
  }

  if (len->is_valid()) {
    movl(Address(obj, arrayOopDesc::length_offset_in_bytes()), len);
    int base_offset = arrayOopDesc::length_offset_in_bytes() + BytesPerInt;
    if (!is_aligned(base_offset, BytesPerWord)) {
      assert(is_aligned(base_offset, BytesPerInt), "must be 4-byte aligned");
      // Clear gap/first 4 bytes following the length field.
      xorl(t1, t1);
      movl(Address(obj, base_offset), t1);
    }
  } else if (UseCompressedClassPointers && !UseCompactObjectHeaders) {
    xorptr(t1, t1);
    store_klass_gap(obj, t1);
  }
}


// preserves obj, destroys len_in_bytes
void C1_MacroAssembler::initialize_body(Register obj, Register len_in_bytes, int hdr_size_in_bytes, Register t1) {
  assert(hdr_size_in_bytes >= 0, "header size must be positive or 0");
  Label done;

  // len_in_bytes is positive and ptr sized
  subptr(len_in_bytes, hdr_size_in_bytes);
  zero_memory(obj, len_in_bytes, hdr_size_in_bytes, t1);
  bind(done);
}


void C1_MacroAssembler::allocate_object(Register obj, Register t1, Register t2, int header_size, int object_size, Register klass, Label& slow_case) {
  assert(obj == rax, "obj must be in rax, for cmpxchg");
  assert_different_registers(obj, t1, t2); // XXX really?
  assert(header_size >= 0 && object_size >= header_size, "illegal sizes");

  try_allocate(obj, noreg, object_size * BytesPerWord, t1, t2, slow_case);

  initialize_object(obj, klass, noreg, object_size * HeapWordSize, t1, t2, UseTLAB);
}

void C1_MacroAssembler::initialize_object(Register obj, Register klass, Register var_size_in_bytes, int con_size_in_bytes, Register t1, Register t2, bool is_tlab_allocated) {
  assert((con_size_in_bytes & MinObjAlignmentInBytesMask) == 0,
         "con_size_in_bytes is not multiple of alignment");
  const int hdr_size_in_bytes = instanceOopDesc::header_size() * HeapWordSize;
  if (UseCompactObjectHeaders) {
    assert(hdr_size_in_bytes == 8, "check object headers size");
  }
  initialize_header(obj, klass, noreg, t1, t2);

  if (!(UseTLAB && ZeroTLAB && is_tlab_allocated)) {
    // clear rest of allocated space
    const Register t1_zero = t1;
    const Register index = t2;
    const int threshold = 6 * BytesPerWord;   // approximate break even point for code size (see comments below)
    if (var_size_in_bytes != noreg) {
      mov(index, var_size_in_bytes);
      initialize_body(obj, index, hdr_size_in_bytes, t1_zero);
    } else if (con_size_in_bytes <= threshold) {
      // use explicit null stores
      // code size = 2 + 3*n bytes (n = number of fields to clear)
      xorptr(t1_zero, t1_zero); // use t1_zero reg to clear memory (shorter code)
      for (int i = hdr_size_in_bytes; i < con_size_in_bytes; i += BytesPerWord)
        movptr(Address(obj, i), t1_zero);
    } else if (con_size_in_bytes > hdr_size_in_bytes) {
      // use loop to null out the fields
      // code size = 16 bytes for even n (n = number of fields to clear)
      // initialize last object field first if odd number of fields
      xorptr(t1_zero, t1_zero); // use t1_zero reg to clear memory (shorter code)
      movptr(index, (con_size_in_bytes - hdr_size_in_bytes) >> 3);
      // initialize last object field if constant size is odd
      if (((con_size_in_bytes - hdr_size_in_bytes) & 4) != 0)
        movptr(Address(obj, con_size_in_bytes - (1*BytesPerWord)), t1_zero);
      // initialize remaining object fields: rdx is a multiple of 2
      { Label loop;
        bind(loop);
        movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - (1*BytesPerWord)),
               t1_zero);
        decrement(index);
        jcc(Assembler::notZero, loop);
      }
    }
  }

  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == rax, "must be");
    call(RuntimeAddress(Runtime1::entry_for(StubId::c1_dtrace_object_alloc_id)));
  }

  verify_oop(obj);
}

void C1_MacroAssembler::allocate_array(Register obj, Register len, Register t1, Register t2, int base_offset_in_bytes, Address::ScaleFactor f, Register klass, Label& slow_case, bool zero_array) {
  assert(obj == rax, "obj must be in rax, for cmpxchg");
  assert_different_registers(obj, len, t1, t2, klass);

  // determine alignment mask
  assert(!(BytesPerWord & 1), "must be a multiple of 2 for masking code to work");

  // check for negative or excessive length
  cmpptr(len, checked_cast<int32_t>(max_array_allocation_length));
  jcc(Assembler::above, slow_case);

  const Register arr_size = t2; // okay to be the same
  // align object end
  movptr(arr_size, base_offset_in_bytes + MinObjAlignmentInBytesMask);
  lea(arr_size, Address(arr_size, len, f));
  andptr(arr_size, ~MinObjAlignmentInBytesMask);

  try_allocate(obj, arr_size, 0, t1, t2, slow_case);

  initialize_header(obj, klass, len, t1, t2);

  // clear rest of allocated space
  if (zero_array) {
    const Register len_zero = len;
    // Align-up to word boundary, because we clear the 4 bytes potentially
    // following the length field in initialize_header().
    int base_offset = align_up(base_offset_in_bytes, BytesPerWord);
    initialize_body(obj, arr_size, base_offset, len_zero);
  }

  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == rax, "must be");
    call(RuntimeAddress(Runtime1::entry_for(StubId::c1_dtrace_object_alloc_id)));
  }

  verify_oop(obj);
}

void C1_MacroAssembler::build_frame_helper(int frame_size_in_bytes, int sp_offset_for_orig_pc, int sp_inc, bool reset_orig_pc, bool needs_stack_repair) {
  push(rbp);
#ifdef ASSERT
  if (sp_inc > 0) {
    movl(Address(rsp, 0), badRegWordVal);
    movl(Address(rsp, VMRegImpl::stack_slot_size), badRegWordVal);
  }
#endif
  if (PreserveFramePointer) {
    mov(rbp, rsp);
  }
  decrement(rsp, frame_size_in_bytes);

  if (needs_stack_repair) {
    // Save stack increment (also account for fixed framesize and rbp)
    assert((sp_inc & (StackAlignmentInBytes-1)) == 0, "stack increment not aligned");
    int real_frame_size = sp_inc + frame_size_in_bytes;
    movptr(Address(rsp, frame_size_in_bytes - wordSize), real_frame_size);
  }
  if (reset_orig_pc) {
    // Zero orig_pc to detect deoptimization during buffering in the entry points
    movptr(Address(rsp, sp_offset_for_orig_pc), 0);
  }
}

void C1_MacroAssembler::build_frame(int frame_size_in_bytes, int bang_size_in_bytes, int sp_offset_for_orig_pc, bool needs_stack_repair, bool has_scalarized_args, Label* verified_inline_entry_label) {
  // Make sure there is enough stack space for this method's activation.
  // Note that we do this before doing an enter(). This matches the
  // ordering of C2's stack overflow check / rsp decrement and allows
  // the SharedRuntime stack overflow handling to be consistent
  // between the two compilers.
  assert(bang_size_in_bytes >= frame_size_in_bytes, "stack bang size incorrect");
  generate_stack_overflow_check(bang_size_in_bytes);

  build_frame_helper(frame_size_in_bytes, sp_offset_for_orig_pc, 0, has_scalarized_args, needs_stack_repair);

  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  // C1 code is not hot enough to micro optimize the nmethod entry barrier with an out-of-line stub
  bs->nmethod_entry_barrier(this, nullptr /* slow_path */, nullptr /* continuation */);

  if (verified_inline_entry_label != nullptr) {
    // Jump here from the scalarized entry points that already created the frame.
    bind(*verified_inline_entry_label);
  }
}

void C1_MacroAssembler::verified_entry(bool breakAtEntry) {
  if (breakAtEntry) int3();
  // build frame
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
  bs->nmethod_entry_barrier(this, nullptr /* slow_path */, nullptr /* continuation */);

  // FIXME -- call runtime only if we cannot in-line allocate all the incoming inline type args.
  movptr(rbx, (intptr_t)(ces->method()));
  if (is_inline_ro_entry) {
    call(RuntimeAddress(Runtime1::entry_for(StubId::c1_buffer_inline_args_no_receiver_id)));
  } else {
    call(RuntimeAddress(Runtime1::entry_for(StubId::c1_buffer_inline_args_id)));
  }
  int rt_call_offset = offset();

  // Remove the temp frame
  addptr(rsp, frame_size_in_bytes);
  pop(rbp);

  // Check if we need to extend the stack for packing
  int sp_inc = 0;
  if (args_on_stack > args_on_stack_cc) {
    sp_inc = extend_stack_for_inline_args(args_on_stack);
  }

  shuffle_inline_args(true, is_inline_ro_entry, sig_cc,
                      args_passed_cc, args_on_stack_cc, regs_cc, // from
                      args_passed, args_on_stack, regs,          // to
                      sp_inc, rax);

  // Create the real frame. Below jump will then skip over the stack banging and frame
  // setup code in the verified_inline_entry (which has a different real_frame_size).
  build_frame_helper(frame_size_in_bytes, sp_offset_for_orig_pc, sp_inc, false, ces->c1_needs_stack_repair());

  jmp(verified_inline_entry_label);
  return rt_call_offset;
}

void C1_MacroAssembler::load_parameter(int offset_in_words, Register reg) {
  // rbp, + 0: link
  //     + 1: return address
  //     + 2: argument with offset 0
  //     + 3: argument with offset 1
  //     + 4: ...

  movptr(reg, Address(rbp, (offset_in_words + 2) * BytesPerWord));
}

#ifndef PRODUCT

void C1_MacroAssembler::verify_stack_oop(int stack_offset) {
  if (!VerifyOops) return;
  verify_oop_addr(Address(rsp, stack_offset));
}

void C1_MacroAssembler::verify_not_null_oop(Register r) {
  if (!VerifyOops) return;
  Label not_null;
  testptr(r, r);
  jcc(Assembler::notZero, not_null);
  stop("non-null oop required");
  bind(not_null);
  verify_oop(r);
}

void C1_MacroAssembler::invalidate_registers(bool inv_rax, bool inv_rbx, bool inv_rcx, bool inv_rdx, bool inv_rsi, bool inv_rdi) {
#ifdef ASSERT
  if (inv_rax) movptr(rax, 0xDEAD);
  if (inv_rbx) movptr(rbx, 0xDEAD);
  if (inv_rcx) movptr(rcx, 0xDEAD);
  if (inv_rdx) movptr(rdx, 0xDEAD);
  if (inv_rsi) movptr(rsi, 0xDEAD);
  if (inv_rdi) movptr(rdi, 0xDEAD);
#endif
}

#endif // ifndef PRODUCT
