/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_c1_MacroAssembler_x86.cpp.incl"

int C1_MacroAssembler::lock_object(Register hdr, Register obj, Register disp_hdr, Register scratch, Label& slow_case) {
  const int aligned_mask = BytesPerWord -1;
  const int hdr_offset = oopDesc::mark_offset_in_bytes();
  assert(hdr == rax, "hdr must be rax, for the cmpxchg instruction");
  assert(hdr != obj && hdr != disp_hdr && obj != disp_hdr, "registers must be different");
  Label done;
  int null_check_offset = -1;

  verify_oop(obj);

  // save object being locked into the BasicObjectLock
  movptr(Address(disp_hdr, BasicObjectLock::obj_offset_in_bytes()), obj);

  if (UseBiasedLocking) {
    assert(scratch != noreg, "should have scratch register at this point");
    null_check_offset = biased_locking_enter(disp_hdr, obj, hdr, scratch, false, done, &slow_case);
  } else {
    null_check_offset = offset();
  }

  // Load object header
  movptr(hdr, Address(obj, hdr_offset));
  // and mark it as unlocked
  orptr(hdr, markOopDesc::unlocked_value);
  // save unlocked object header into the displaced header location on the stack
  movptr(Address(disp_hdr, 0), hdr);
  // test if object header is still the same (i.e. unlocked), and if so, store the
  // displaced header address in the object header - if it is not the same, get the
  // object header instead
  if (os::is_MP()) MacroAssembler::lock(); // must be immediately before cmpxchg!
  cmpxchgptr(disp_hdr, Address(obj, hdr_offset));
  // if the object header was the same, we're done
  if (PrintBiasedLockingStatistics) {
    cond_inc32(Assembler::equal,
               ExternalAddress((address)BiasedLocking::fast_path_entry_count_addr()));
  }
  jcc(Assembler::equal, done);
  // if the object header was not the same, it is now in the hdr register
  // => test if it is a stack pointer into the same stack (recursive locking), i.e.:
  //
  // 1) (hdr & aligned_mask) == 0
  // 2) rsp <= hdr
  // 3) hdr <= rsp + page_size
  //
  // these 3 tests can be done by evaluating the following expression:
  //
  // (hdr - rsp) & (aligned_mask - page_size)
  //
  // assuming both the stack pointer and page_size have their least
  // significant 2 bits cleared and page_size is a power of 2
  subptr(hdr, rsp);
  andptr(hdr, aligned_mask - os::vm_page_size());
  // for recursive locking, the result is zero => save it in the displaced header
  // location (NULL in the displaced hdr location indicates recursive locking)
  movptr(Address(disp_hdr, 0), hdr);
  // otherwise we don't care about the result and handle locking via runtime call
  jcc(Assembler::notZero, slow_case);
  // done
  bind(done);
  return null_check_offset;
}


void C1_MacroAssembler::unlock_object(Register hdr, Register obj, Register disp_hdr, Label& slow_case) {
  const int aligned_mask = BytesPerWord -1;
  const int hdr_offset = oopDesc::mark_offset_in_bytes();
  assert(disp_hdr == rax, "disp_hdr must be rax, for the cmpxchg instruction");
  assert(hdr != obj && hdr != disp_hdr && obj != disp_hdr, "registers must be different");
  Label done;

  if (UseBiasedLocking) {
    // load object
    movptr(obj, Address(disp_hdr, BasicObjectLock::obj_offset_in_bytes()));
    biased_locking_exit(obj, hdr, done);
  }

  // load displaced header
  movptr(hdr, Address(disp_hdr, 0));
  // if the loaded hdr is NULL we had recursive locking
  testptr(hdr, hdr);
  // if we had recursive locking, we are done
  jcc(Assembler::zero, done);
  if (!UseBiasedLocking) {
    // load object
    movptr(obj, Address(disp_hdr, BasicObjectLock::obj_offset_in_bytes()));
  }
  verify_oop(obj);
  // test if object header is pointing to the displaced header, and if so, restore
  // the displaced header in the object - if the object header is not pointing to
  // the displaced header, get the object header instead
  if (os::is_MP()) MacroAssembler::lock(); // must be immediately before cmpxchg!
  cmpxchgptr(hdr, Address(obj, hdr_offset));
  // if the object header was not pointing to the displaced header,
  // we do unlocking via runtime call
  jcc(Assembler::notEqual, slow_case);
  // done
  bind(done);
}


// Defines obj, preserves var_size_in_bytes
void C1_MacroAssembler::try_allocate(Register obj, Register var_size_in_bytes, int con_size_in_bytes, Register t1, Register t2, Label& slow_case) {
  if (UseTLAB) {
    tlab_allocate(obj, var_size_in_bytes, con_size_in_bytes, t1, t2, slow_case);
  } else {
    eden_allocate(obj, var_size_in_bytes, con_size_in_bytes, t1, slow_case);
  }
}


void C1_MacroAssembler::initialize_header(Register obj, Register klass, Register len, Register t1, Register t2) {
  assert_different_registers(obj, klass, len);
  if (UseBiasedLocking && !len->is_valid()) {
    assert_different_registers(obj, klass, len, t1, t2);
    movptr(t1, Address(klass, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
    movptr(Address(obj, oopDesc::mark_offset_in_bytes()), t1);
  } else {
    // This assumes that all prototype bits fit in an int32_t
    movptr(Address(obj, oopDesc::mark_offset_in_bytes ()), (int32_t)(intptr_t)markOopDesc::prototype());
  }

  movptr(Address(obj, oopDesc::klass_offset_in_bytes()), klass);
  if (len->is_valid()) {
    movl(Address(obj, arrayOopDesc::length_offset_in_bytes()), len);
  }
}


// preserves obj, destroys len_in_bytes
void C1_MacroAssembler::initialize_body(Register obj, Register len_in_bytes, int hdr_size_in_bytes, Register t1) {
  Label done;
  assert(obj != len_in_bytes && obj != t1 && t1 != len_in_bytes, "registers must be different");
  assert((hdr_size_in_bytes & (BytesPerWord - 1)) == 0, "header size is not a multiple of BytesPerWord");
  Register index = len_in_bytes;
  // index is positive and ptr sized
  subptr(index, hdr_size_in_bytes);
  jcc(Assembler::zero, done);
  // initialize topmost word, divide index by 2, check if odd and test if zero
  // note: for the remaining code to work, index must be a multiple of BytesPerWord
#ifdef ASSERT
  { Label L;
    testptr(index, BytesPerWord - 1);
    jcc(Assembler::zero, L);
    stop("index is not a multiple of BytesPerWord");
    bind(L);
  }
#endif
  xorptr(t1, t1);    // use _zero reg to clear memory (shorter code)
  if (UseIncDec) {
    shrptr(index, 3);  // divide by 8/16 and set carry flag if bit 2 was set
  } else {
    shrptr(index, 2);  // use 2 instructions to avoid partial flag stall
    shrptr(index, 1);
  }
#ifndef _LP64
  // index could have been not a multiple of 8 (i.e., bit 2 was set)
  { Label even;
    // note: if index was a multiple of 8, than it cannot
    //       be 0 now otherwise it must have been 0 before
    //       => if it is even, we don't need to check for 0 again
    jcc(Assembler::carryClear, even);
    // clear topmost word (no jump needed if conditional assignment would work here)
    movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - 0*BytesPerWord), t1);
    // index could be 0 now, need to check again
    jcc(Assembler::zero, done);
    bind(even);
  }
#endif // !_LP64
  // initialize remaining object fields: rdx is a multiple of 2 now
  { Label loop;
    bind(loop);
    movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - 1*BytesPerWord), t1);
    NOT_LP64(movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - 2*BytesPerWord), t1);)
    decrement(index);
    jcc(Assembler::notZero, loop);
  }

  // done
  bind(done);
}


void C1_MacroAssembler::allocate_object(Register obj, Register t1, Register t2, int header_size, int object_size, Register klass, Label& slow_case) {
  assert(obj == rax, "obj must be in rax, for cmpxchg");
  assert(obj != t1 && obj != t2 && t1 != t2, "registers must be different"); // XXX really?
  assert(header_size >= 0 && object_size >= header_size, "illegal sizes");

  try_allocate(obj, noreg, object_size * BytesPerWord, t1, t2, slow_case);

  initialize_object(obj, klass, noreg, object_size * HeapWordSize, t1, t2);
}

void C1_MacroAssembler::initialize_object(Register obj, Register klass, Register var_size_in_bytes, int con_size_in_bytes, Register t1, Register t2) {
  assert((con_size_in_bytes & MinObjAlignmentInBytesMask) == 0,
         "con_size_in_bytes is not multiple of alignment");
  const int hdr_size_in_bytes = instanceOopDesc::base_offset_in_bytes();

  initialize_header(obj, klass, noreg, t1, t2);

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
      NOT_LP64(movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - (2*BytesPerWord)),
             t1_zero);)
      decrement(index);
      jcc(Assembler::notZero, loop);
    }
  }

  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == rax, "must be");
    call(RuntimeAddress(Runtime1::entry_for(Runtime1::dtrace_object_alloc_id)));
  }

  verify_oop(obj);
}

void C1_MacroAssembler::allocate_array(Register obj, Register len, Register t1, Register t2, int header_size, Address::ScaleFactor f, Register klass, Label& slow_case) {
  assert(obj == rax, "obj must be in rax, for cmpxchg");
  assert_different_registers(obj, len, t1, t2, klass);

  // determine alignment mask
  assert(!(BytesPerWord & 1), "must be a multiple of 2 for masking code to work");

  // check for negative or excessive length
  cmpptr(len, (int32_t)max_array_allocation_length);
  jcc(Assembler::above, slow_case);

  const Register arr_size = t2; // okay to be the same
  // align object end
  movptr(arr_size, (int32_t)header_size * BytesPerWord + MinObjAlignmentInBytesMask);
  lea(arr_size, Address(arr_size, len, f));
  andptr(arr_size, ~MinObjAlignmentInBytesMask);

  try_allocate(obj, arr_size, 0, t1, t2, slow_case);

  initialize_header(obj, klass, len, t1, t2);

  // clear rest of allocated space
  const Register len_zero = len;
  initialize_body(obj, arr_size, header_size * BytesPerWord, len_zero);

  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == rax, "must be");
    call(RuntimeAddress(Runtime1::entry_for(Runtime1::dtrace_object_alloc_id)));
  }

  verify_oop(obj);
}



void C1_MacroAssembler::inline_cache_check(Register receiver, Register iCache) {
  verify_oop(receiver);
  // explicit NULL check not needed since load from [klass_offset] causes a trap
  // check against inline cache
  assert(!MacroAssembler::needs_explicit_null_check(oopDesc::klass_offset_in_bytes()), "must add explicit null check");
  int start_offset = offset();
  cmpptr(iCache, Address(receiver, oopDesc::klass_offset_in_bytes()));
  // if icache check fails, then jump to runtime routine
  // Note: RECEIVER must still contain the receiver!
  jump_cc(Assembler::notEqual,
          RuntimeAddress(SharedRuntime::get_ic_miss_stub()));
  const int ic_cmp_size = LP64_ONLY(10) NOT_LP64(9);
  assert(offset() - start_offset == ic_cmp_size, "check alignment in emit_method_entry");
}


void C1_MacroAssembler::method_exit(bool restore_frame) {
  if (restore_frame) {
    leave();
  }
  ret(0);
}


void C1_MacroAssembler::build_frame(int frame_size_in_bytes) {
  // Make sure there is enough stack space for this method's activation.
  // Note that we do this before doing an enter(). This matches the
  // ordering of C2's stack overflow check / rsp decrement and allows
  // the SharedRuntime stack overflow handling to be consistent
  // between the two compilers.
  generate_stack_overflow_check(frame_size_in_bytes);

  enter();
#ifdef TIERED
  // c2 leaves fpu stack dirty. Clean it on entry
  if (UseSSE < 2 ) {
    empty_FPU_stack();
  }
#endif // TIERED
  decrement(rsp, frame_size_in_bytes); // does not emit code for frame_size == 0
}


void C1_MacroAssembler::unverified_entry(Register receiver, Register ic_klass) {
  if (C1Breakpoint) int3();
  inline_cache_check(receiver, ic_klass);
}


void C1_MacroAssembler::verified_entry() {
  if (C1Breakpoint)int3();
  // build frame
  verify_FPU(0, "method_entry");
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
