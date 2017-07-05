/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/arrayOop.hpp"
#include "oops/markOop.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"

// Note: Rtemp usage is this file should not impact C2 and should be
// correct as long as it is not implicitly used in lower layers (the
// arm [macro]assembler) and used with care in the other C1 specific
// files.

void C1_MacroAssembler::inline_cache_check(Register receiver, Register iCache) {
  Label verified;
  load_klass(Rtemp, receiver);
  cmp(Rtemp, iCache);
  b(verified, eq); // jump over alignment no-ops
#ifdef AARCH64
  jump(SharedRuntime::get_ic_miss_stub(), relocInfo::runtime_call_type, Rtemp);
#else
  jump(SharedRuntime::get_ic_miss_stub(), relocInfo::runtime_call_type);
#endif
  align(CodeEntryAlignment);
  bind(verified);
}

void C1_MacroAssembler::build_frame(int frame_size_in_bytes, int bang_size_in_bytes) {
  assert(bang_size_in_bytes >= frame_size_in_bytes, "stack bang size incorrect");
  assert((frame_size_in_bytes % StackAlignmentInBytes) == 0, "frame size should be aligned");

#ifdef AARCH64
  // Extra nop for MT-safe patching in NativeJump::patch_verified_entry
  nop();
#endif // AARCH64

  arm_stack_overflow_check(bang_size_in_bytes, Rtemp);

  // FP can no longer be used to memorize SP. It may be modified
  // if this method contains a methodHandle call site
  raw_push(FP, LR);
  sub_slow(SP, SP, frame_size_in_bytes);
}

void C1_MacroAssembler::remove_frame(int frame_size_in_bytes) {
  add_slow(SP, SP, frame_size_in_bytes);
  raw_pop(FP, LR);
}

void C1_MacroAssembler::verified_entry() {
  if (C1Breakpoint) {
    breakpoint();
  }
}

// Puts address of allocated object into register `obj` and end of allocated object into register `obj_end`.
void C1_MacroAssembler::try_allocate(Register obj, Register obj_end, Register tmp1, Register tmp2,
                                     RegisterOrConstant size_expression, Label& slow_case) {
  if (UseTLAB) {
    tlab_allocate(obj, obj_end, tmp1, size_expression, slow_case);
  } else {
    eden_allocate(obj, obj_end, tmp1, tmp2, size_expression, slow_case);
    incr_allocated_bytes(size_expression, tmp1);
  }
}


void C1_MacroAssembler::initialize_header(Register obj, Register klass, Register len, Register tmp) {
  assert_different_registers(obj, klass, len, tmp);

  if(UseBiasedLocking && !len->is_valid()) {
    ldr(tmp, Address(klass, Klass::prototype_header_offset()));
  } else {
    mov(tmp, (intptr_t)markOopDesc::prototype());
  }

#ifdef AARCH64
  if (UseCompressedClassPointers) {
    str(tmp, Address(obj, oopDesc::mark_offset_in_bytes()));
    encode_klass_not_null(tmp, klass);          // Take care not to kill klass
    str_w(tmp, Address(obj, oopDesc::klass_offset_in_bytes()));
  } else {
    assert(oopDesc::mark_offset_in_bytes() + wordSize == oopDesc::klass_offset_in_bytes(), "adjust this code");
    stp(tmp, klass, Address(obj, oopDesc::mark_offset_in_bytes()));
  }
#else
  str(tmp, Address(obj, oopDesc::mark_offset_in_bytes()));
  str(klass, Address(obj, oopDesc::klass_offset_in_bytes()));
#endif // AARCH64

  if (len->is_valid()) {
    str_32(len, Address(obj, arrayOopDesc::length_offset_in_bytes()));
  }
#ifdef AARCH64
  else if (UseCompressedClassPointers) {
    store_klass_gap(obj);
  }
#endif // AARCH64
}


// Cleans object body [base..obj_end]. Clobbers `base` and `tmp` registers.
void C1_MacroAssembler::initialize_body(Register base, Register obj_end, Register tmp) {
  zero_memory(base, obj_end, tmp);
}


void C1_MacroAssembler::initialize_object(Register obj, Register obj_end, Register klass,
                                          Register len, Register tmp1, Register tmp2,
                                          RegisterOrConstant header_size, int obj_size_in_bytes,
                                          bool is_tlab_allocated)
{
  assert_different_registers(obj, obj_end, klass, len, tmp1, tmp2);
  initialize_header(obj, klass, len, tmp1);

  const Register ptr = tmp2;

  if (!(UseTLAB && ZeroTLAB && is_tlab_allocated)) {
#ifdef AARCH64
    if (obj_size_in_bytes < 0) {
      add_rc(ptr, obj, header_size);
      initialize_body(ptr, obj_end, tmp1);

    } else {
      int base = instanceOopDesc::header_size() * HeapWordSize;
      assert(obj_size_in_bytes >= base, "should be");

      const int zero_bytes = obj_size_in_bytes - base;
      assert((zero_bytes % wordSize) == 0, "should be");

      if ((zero_bytes % (2*wordSize)) != 0) {
        str(ZR, Address(obj, base));
        base += wordSize;
      }

      const int stp_count = zero_bytes / (2*wordSize);

      if (zero_bytes > 8 * wordSize) {
        Label loop;
        add(ptr, obj, base);
        mov(tmp1, stp_count);
        bind(loop);
        subs(tmp1, tmp1, 1);
        stp(ZR, ZR, Address(ptr, 2*wordSize, post_indexed));
        b(loop, gt);
      } else {
        for (int i = 0; i < stp_count; i++) {
          stp(ZR, ZR, Address(obj, base + i * 2 * wordSize));
        }
      }
    }
#else
    if (obj_size_in_bytes >= 0 && obj_size_in_bytes <= 8 * BytesPerWord) {
      mov(tmp1, 0);
      const int base = instanceOopDesc::header_size() * HeapWordSize;
      for (int i = base; i < obj_size_in_bytes; i += wordSize) {
        str(tmp1, Address(obj, i));
      }
    } else {
      assert(header_size.is_constant() || header_size.as_register() == ptr, "code assumption");
      add(ptr, obj, header_size);
      initialize_body(ptr, obj_end, tmp1);
    }
#endif // AARCH64
  }

  // StoreStore barrier required after complete initialization
  // (headers + content zeroing), before the object may escape.
  membar(MacroAssembler::StoreStore, tmp1);
}

void C1_MacroAssembler::allocate_object(Register obj, Register tmp1, Register tmp2, Register tmp3,
                                        int header_size, int object_size,
                                        Register klass, Label& slow_case) {
  assert_different_registers(obj, tmp1, tmp2, tmp3, klass, Rtemp);
  assert(header_size >= 0 && object_size >= header_size, "illegal sizes");
  const int object_size_in_bytes = object_size * BytesPerWord;

  const Register obj_end = tmp1;
  const Register len = noreg;

  if (Assembler::is_arith_imm_in_range(object_size_in_bytes)) {
    try_allocate(obj, obj_end, tmp2, tmp3, object_size_in_bytes, slow_case);
  } else {
    // Rtemp should be free at c1 LIR level
    mov_slow(Rtemp, object_size_in_bytes);
    try_allocate(obj, obj_end, tmp2, tmp3, Rtemp, slow_case);
  }
  initialize_object(obj, obj_end, klass, len, tmp2, tmp3, instanceOopDesc::header_size() * HeapWordSize, object_size_in_bytes, /* is_tlab_allocated */ UseTLAB);
}

void C1_MacroAssembler::allocate_array(Register obj, Register len,
                                       Register tmp1, Register tmp2, Register tmp3,
                                       int header_size, int element_size,
                                       Register klass, Label& slow_case) {
  assert_different_registers(obj, len, tmp1, tmp2, tmp3, klass, Rtemp);
  const int header_size_in_bytes = header_size * BytesPerWord;
  const int scale_shift = exact_log2(element_size);
  const Register obj_size = Rtemp; // Rtemp should be free at c1 LIR level

#ifdef AARCH64
  mov_slow(Rtemp, max_array_allocation_length);
  cmp_32(len, Rtemp);
#else
  cmp_32(len, max_array_allocation_length);
#endif // AARCH64
  b(slow_case, hs);

  bool align_header = ((header_size_in_bytes | element_size) & MinObjAlignmentInBytesMask) != 0;
  assert(align_header || ((header_size_in_bytes & MinObjAlignmentInBytesMask) == 0), "must be");
  assert(align_header || ((element_size & MinObjAlignmentInBytesMask) == 0), "must be");

  mov(obj_size, header_size_in_bytes + (align_header ? (MinObjAlignmentInBytes - 1) : 0));
  add_ptr_scaled_int32(obj_size, obj_size, len, scale_shift);

  if (align_header) {
    align_reg(obj_size, obj_size, MinObjAlignmentInBytes);
  }

  try_allocate(obj, tmp1, tmp2, tmp3, obj_size, slow_case);
  initialize_object(obj, tmp1, klass, len, tmp2, tmp3, header_size_in_bytes, -1, /* is_tlab_allocated */ UseTLAB);
}

int C1_MacroAssembler::lock_object(Register hdr, Register obj,
                                   Register disp_hdr, Register tmp1,
                                   Label& slow_case) {
  Label done, fast_lock, fast_lock_done;
  int null_check_offset = 0;

  const Register tmp2 = Rtemp; // Rtemp should be free at c1 LIR level
  assert_different_registers(hdr, obj, disp_hdr, tmp1, tmp2);

  assert(BasicObjectLock::lock_offset_in_bytes() == 0, "ajust this code");
  const int obj_offset = BasicObjectLock::obj_offset_in_bytes();
  const int mark_offset = BasicLock::displaced_header_offset_in_bytes();

  if (UseBiasedLocking) {
    // load object
    str(obj, Address(disp_hdr, obj_offset));
    null_check_offset = biased_locking_enter(obj, hdr/*scratched*/, tmp1, false, tmp2, done, slow_case);
  }

  assert(oopDesc::mark_offset_in_bytes() == 0, "Required by atomic instructions");

#ifdef AARCH64

  str(obj, Address(disp_hdr, obj_offset));

  if (!UseBiasedLocking) {
    null_check_offset = offset();
  }
  ldr(hdr, obj);

  // Test if object is already locked
  assert(markOopDesc::unlocked_value == 1, "adjust this code");
  tbnz(hdr, exact_log2(markOopDesc::unlocked_value), fast_lock);

  // Check for recursive locking
  // See comments in InterpreterMacroAssembler::lock_object for
  // explanations on the fast recursive locking check.
  intptr_t mask = ((intptr_t)3) - ((intptr_t)os::vm_page_size());
  Assembler::LogicalImmediate imm(mask, false);
  mov(tmp2, SP);
  sub(tmp2, hdr, tmp2);
  ands(tmp2, tmp2, imm);
  b(slow_case, ne);

  // Recursive locking: store 0 into a lock record
  str(ZR, Address(disp_hdr, mark_offset));
  b(fast_lock_done);

#else // AARCH64

  if (!UseBiasedLocking) {
    null_check_offset = offset();
  }

  // On MP platforms the next load could return a 'stale' value if the memory location has been modified by another thread.
  // That would be acceptable as ether CAS or slow case path is taken in that case.

  // Must be the first instruction here, because implicit null check relies on it
  ldr(hdr, Address(obj, oopDesc::mark_offset_in_bytes()));

  str(obj, Address(disp_hdr, obj_offset));
  tst(hdr, markOopDesc::unlocked_value);
  b(fast_lock, ne);

  // Check for recursive locking
  // See comments in InterpreterMacroAssembler::lock_object for
  // explanations on the fast recursive locking check.
  // -1- test low 2 bits
  movs(tmp2, AsmOperand(hdr, lsl, 30));
  // -2- test (hdr - SP) if the low two bits are 0
  sub(tmp2, hdr, SP, eq);
  movs(tmp2, AsmOperand(tmp2, lsr, exact_log2(os::vm_page_size())), eq);
  // If 'eq' then OK for recursive fast locking: store 0 into a lock record.
  str(tmp2, Address(disp_hdr, mark_offset), eq);
  b(fast_lock_done, eq);
  // else need slow case
  b(slow_case);

#endif // AARCH64

  bind(fast_lock);
  // Save previous object header in BasicLock structure and update the header
  str(hdr, Address(disp_hdr, mark_offset));

  cas_for_lock_acquire(hdr, disp_hdr, obj, tmp2, slow_case);

  bind(fast_lock_done);

#ifndef PRODUCT
  if (PrintBiasedLockingStatistics) {
    cond_atomic_inc32(al, BiasedLocking::fast_path_entry_count_addr());
  }
#endif // !PRODUCT

  bind(done);

  return null_check_offset;
}

void C1_MacroAssembler::unlock_object(Register hdr, Register obj,
                                      Register disp_hdr, Register tmp,
                                      Label& slow_case) {
  // Note: this method is not using its 'tmp' argument

  assert_different_registers(hdr, obj, disp_hdr, Rtemp);
  Register tmp2 = Rtemp;

  assert(BasicObjectLock::lock_offset_in_bytes() == 0, "ajust this code");
  const int obj_offset = BasicObjectLock::obj_offset_in_bytes();
  const int mark_offset = BasicLock::displaced_header_offset_in_bytes();

  Label done;
  if (UseBiasedLocking) {
    // load object
    ldr(obj, Address(disp_hdr, obj_offset));
    biased_locking_exit(obj, hdr, done);
  }

  assert(oopDesc::mark_offset_in_bytes() == 0, "Required by atomic instructions");
  Label retry;

  // Load displaced header and object from the lock
  ldr(hdr, Address(disp_hdr, mark_offset));
  // If hdr is NULL, we've got recursive locking and there's nothing more to do
  cbz(hdr, done);

  if(!UseBiasedLocking) {
    // load object
    ldr(obj, Address(disp_hdr, obj_offset));
  }

  // Restore the object header
  cas_for_lock_release(disp_hdr, hdr, obj, tmp2, slow_case);

  bind(done);
}


#ifndef PRODUCT

void C1_MacroAssembler::verify_stack_oop(int stack_offset) {
  if (!VerifyOops) return;
  verify_oop_addr(Address(SP, stack_offset));
}

void C1_MacroAssembler::verify_not_null_oop(Register r) {
  Label not_null;
  cbnz(r, not_null);
  stop("non-null oop required");
  bind(not_null);
  if (!VerifyOops) return;
  verify_oop(r);
}

#endif // !PRODUCT
