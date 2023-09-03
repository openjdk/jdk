/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "c1/c1_LIR.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/arrayOop.hpp"
#include "oops/markWord.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"

void C1_MacroAssembler::float_cmp(bool is_float, int unordered_result,
                                  FloatRegister freg0, FloatRegister freg1,
                                  Register result) {
  if (is_float) {
    float_compare(result, freg0, freg1, unordered_result);
  } else {
    double_compare(result, freg0, freg1, unordered_result);
  }
}

int C1_MacroAssembler::lock_object(Register hdr, Register obj, Register disp_hdr, Label& slow_case) {
  const int aligned_mask = BytesPerWord - 1;
  const int hdr_offset = oopDesc::mark_offset_in_bytes();
  assert_different_registers(hdr, obj, disp_hdr);
  int null_check_offset = -1;

  verify_oop(obj);

  // save object being locked into the BasicObjectLock
  sd(obj, Address(disp_hdr, BasicObjectLock::obj_offset()));

  null_check_offset = offset();

  if (DiagnoseSyncOnValueBasedClasses != 0) {
    load_klass(hdr, obj);
    lwu(hdr, Address(hdr, Klass::access_flags_offset()));
    test_bit(t0, hdr, exact_log2(JVM_ACC_IS_VALUE_BASED_CLASS));
    bnez(t0, slow_case, true /* is_far */);
  }

  // Load object header
  ld(hdr, Address(obj, hdr_offset));

  if (LockingMode == LM_LIGHTWEIGHT) {
    lightweight_lock(obj, hdr, t0, t1, slow_case);
  } else if (LockingMode == LM_LEGACY) {
    Label done;
    // and mark it as unlocked
    ori(hdr, hdr, markWord::unlocked_value);
    // save unlocked object header into the displaced header location on the stack
    sd(hdr, Address(disp_hdr, 0));
    // test if object header is still the same (i.e. unlocked), and if so, store the
    // displaced header address in the object header - if it is not the same, get the
    // object header instead
    la(t1, Address(obj, hdr_offset));
    cmpxchgptr(hdr, disp_hdr, t1, t0, done, /*fallthough*/nullptr);
    // if the object header was the same, we're done
    // if the object header was not the same, it is now in the hdr register
    // => test if it is a stack pointer into the same stack (recursive locking), i.e.:
    //
    // 1) (hdr & aligned_mask) == 0
    // 2) sp <= hdr
    // 3) hdr <= sp + page_size
    //
    // these 3 tests can be done by evaluating the following expression:
    //
    // (hdr -sp) & (aligned_mask - page_size)
    //
    // assuming both the stack pointer and page_size have their least
    // significant 2 bits cleared and page_size is a power of 2
    sub(hdr, hdr, sp);
    mv(t0, aligned_mask - (int)os::vm_page_size());
    andr(hdr, hdr, t0);
    // for recursive locking, the result is zero => save it in the displaced header
    // location (null in the displaced hdr location indicates recursive locking)
    sd(hdr, Address(disp_hdr, 0));
    // otherwise we don't care about the result and handle locking via runtime call
    bnez(hdr, slow_case, /* is_far */ true);
    // done
    bind(done);
  }

  increment(Address(xthread, JavaThread::held_monitor_count_offset()));
  return null_check_offset;
}

void C1_MacroAssembler::unlock_object(Register hdr, Register obj, Register disp_hdr, Label& slow_case) {
  const int aligned_mask = BytesPerWord - 1;
  const int hdr_offset = oopDesc::mark_offset_in_bytes();
  assert(hdr != obj && hdr != disp_hdr && obj != disp_hdr, "registers must be different");
  Label done;

  if (LockingMode != LM_LIGHTWEIGHT) {
    // load displaced header
    ld(hdr, Address(disp_hdr, 0));
    // if the loaded hdr is null we had recursive locking
    // if we had recursive locking, we are done
    beqz(hdr, done);
  }

  // load object
  ld(obj, Address(disp_hdr, BasicObjectLock::obj_offset()));
  verify_oop(obj);

  if (LockingMode == LM_LIGHTWEIGHT) {
    ld(hdr, Address(obj, oopDesc::mark_offset_in_bytes()));
    test_bit(t0, hdr, exact_log2(markWord::monitor_value));
    bnez(t0, slow_case, /* is_far */ true);
    lightweight_unlock(obj, hdr, t0, t1, slow_case);
  } else if (LockingMode == LM_LEGACY) {
    // test if object header is pointing to the displaced header, and if so, restore
    // the displaced header in the object - if the object header is not pointing to
    // the displaced header, get the object header instead
    // if the object header was not pointing to the displaced header,
    // we do unlocking via runtime call
    if (hdr_offset) {
      la(t0, Address(obj, hdr_offset));
      cmpxchgptr(disp_hdr, hdr, t0, t1, done, &slow_case);
    } else {
      cmpxchgptr(disp_hdr, hdr, obj, t1, done, &slow_case);
    }
    // done
    bind(done);
  }

  decrement(Address(xthread, JavaThread::held_monitor_count_offset()));
}

// Defines obj, preserves var_size_in_bytes
void C1_MacroAssembler::try_allocate(Register obj, Register var_size_in_bytes, int con_size_in_bytes, Register tmp1, Register tmp2, Label& slow_case) {
  if (UseTLAB) {
    tlab_allocate(obj, var_size_in_bytes, con_size_in_bytes, tmp1, tmp2, slow_case, /* is_far */ true);
  } else {
    j(slow_case);
  }
}

void C1_MacroAssembler::initialize_header(Register obj, Register klass, Register len, Register tmp1, Register tmp2) {
  assert_different_registers(obj, klass, len, tmp1, tmp2);
  // This assumes that all prototype bits fitr in an int32_t
  mv(tmp1, (int32_t)(intptr_t)markWord::prototype().value());
  sd(tmp1, Address(obj, oopDesc::mark_offset_in_bytes()));

  if (UseCompressedClassPointers) { // Take care not to kill klass
    encode_klass_not_null(tmp1, klass, tmp2);
    sw(tmp1, Address(obj, oopDesc::klass_offset_in_bytes()));
  } else {
    sd(klass, Address(obj, oopDesc::klass_offset_in_bytes()));
  }

  if (len->is_valid()) {
    sw(len, Address(obj, arrayOopDesc::length_offset_in_bytes()));
  } else if (UseCompressedClassPointers) {
    store_klass_gap(obj, zr);
  }
}

// preserves obj, destroys len_in_bytes
void C1_MacroAssembler::initialize_body(Register obj, Register len_in_bytes, int hdr_size_in_bytes, Register tmp) {
  assert(hdr_size_in_bytes >= 0, "header size must be positive or 0");
  Label done;

  // len_in_bytes is positive and ptr sized
  sub(len_in_bytes, len_in_bytes, hdr_size_in_bytes);
  beqz(len_in_bytes, done);

  // Preserve obj
  if (hdr_size_in_bytes) {
    add(obj, obj, hdr_size_in_bytes);
  }
  zero_memory(obj, len_in_bytes, tmp);
  if (hdr_size_in_bytes) {
    sub(obj, obj, hdr_size_in_bytes);
  }

  bind(done);
}

void C1_MacroAssembler::allocate_object(Register obj, Register tmp1, Register tmp2, int header_size, int object_size, Register klass, Label& slow_case) {
  assert_different_registers(obj, tmp1, tmp2);
  assert(header_size >= 0 && object_size >= header_size, "illegal sizes");

  try_allocate(obj, noreg, object_size * BytesPerWord, tmp1, tmp2, slow_case);

  initialize_object(obj, klass, noreg, object_size * HeapWordSize, tmp1, tmp2, UseTLAB);
}

void C1_MacroAssembler::initialize_object(Register obj, Register klass, Register var_size_in_bytes, int con_size_in_bytes, Register tmp1, Register tmp2, bool is_tlab_allocated) {
  assert((con_size_in_bytes & MinObjAlignmentInBytesMask) == 0,
         "con_size_in_bytes is not multiple of alignment");
  const int hdr_size_in_bytes = instanceOopDesc::header_size() * HeapWordSize;

  initialize_header(obj, klass, noreg, tmp1, tmp2);

  if (!(UseTLAB && ZeroTLAB && is_tlab_allocated)) {
    // clear rest of allocated space
    const Register index = tmp2;
    // 16: multiplier for threshold
    const int threshold = 16 * BytesPerWord;    // approximate break even point for code size (see comments below)
    if (var_size_in_bytes != noreg) {
      mv(index, var_size_in_bytes);
      initialize_body(obj, index, hdr_size_in_bytes, tmp1);
    } else if (con_size_in_bytes <= threshold) {
      // use explicit null stores
      int i = hdr_size_in_bytes;
      if (i < con_size_in_bytes && (con_size_in_bytes % (2 * BytesPerWord))) { // 2: multiplier for BytesPerWord
        sd(zr, Address(obj, i));
        i += BytesPerWord;
      }
      for (; i < con_size_in_bytes; i += BytesPerWord) {
        sd(zr, Address(obj, i));
      }
    } else if (con_size_in_bytes > hdr_size_in_bytes) {
      block_comment("zero memory");
      // use loop to null out the fields
      int words = (con_size_in_bytes - hdr_size_in_bytes) / BytesPerWord;
      mv(index, words / 8); // 8: byte size

      const int unroll = 8; // Number of sd(zr) instructions we'll unroll
      int remainder = words % unroll;
      la(t0, Address(obj, hdr_size_in_bytes + remainder * BytesPerWord));

      Label entry_point, loop;
      j(entry_point);

      bind(loop);
      sub(index, index, 1);
      for (int i = -unroll; i < 0; i++) {
        if (-i == remainder) {
          bind(entry_point);
        }
        sd(zr, Address(t0, i * wordSize));
      }
      if (remainder == 0) {
        bind(entry_point);
      }
      add(t0, t0, unroll * wordSize);
      bnez(index, loop);
    }
  }

  membar(MacroAssembler::StoreStore);

  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == x10, "must be");
    far_call(RuntimeAddress(Runtime1::entry_for(Runtime1::dtrace_object_alloc_id)));
  }

  verify_oop(obj);
}

void C1_MacroAssembler::allocate_array(Register obj, Register len, Register tmp1, Register tmp2, int header_size, int f, Register klass, Label& slow_case) {
  assert_different_registers(obj, len, tmp1, tmp2, klass);

  // determine alignment mask
  assert(!(BytesPerWord & 1), "must be multiple of 2 for masking code to work");

  // check for negative or excessive length
  mv(t0, (int32_t)max_array_allocation_length);
  bgeu(len, t0, slow_case, /* is_far */ true);

  const Register arr_size = tmp2; // okay to be the same
  // align object end
  mv(arr_size, (int32_t)header_size * BytesPerWord + MinObjAlignmentInBytesMask);
  shadd(arr_size, len, arr_size, t0, f);
  andi(arr_size, arr_size, ~(uint)MinObjAlignmentInBytesMask);

  try_allocate(obj, arr_size, 0, tmp1, tmp2, slow_case);

  initialize_header(obj, klass, len, tmp1, tmp2);

  // clear rest of allocated space
  const Register len_zero = len;
  initialize_body(obj, arr_size, header_size * BytesPerWord, len_zero);

  membar(MacroAssembler::StoreStore);

  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == x10, "must be");
    far_call(RuntimeAddress(Runtime1::entry_for(Runtime1::dtrace_object_alloc_id)));
  }

  verify_oop(obj);
}

void C1_MacroAssembler::inline_cache_check(Register receiver, Register iCache, Label &L) {
  verify_oop(receiver);
  // explicit null check not needed since load from [klass_offset] causes a trap
  // check against inline cache
  assert(!MacroAssembler::needs_explicit_null_check(oopDesc::klass_offset_in_bytes()), "must add explicit null check");
  assert_different_registers(receiver, iCache, t0, t2);
  cmp_klass(receiver, iCache, t0, t2 /* call-clobbered t2 as a tmp */, L);
}

void C1_MacroAssembler::build_frame(int framesize, int bang_size_in_bytes) {
  assert(bang_size_in_bytes >= framesize, "stack bang size incorrect");
  // Make sure there is enough stack space for this method's activation.
  // Note that we do this before creating a frame.
  generate_stack_overflow_check(bang_size_in_bytes);
  MacroAssembler::build_frame(framesize);

  // Insert nmethod entry barrier into frame.
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->nmethod_entry_barrier(this, nullptr /* slow_path */, nullptr /* continuation */, nullptr /* guard */);
}

void C1_MacroAssembler::remove_frame(int framesize) {
  MacroAssembler::remove_frame(framesize);
}


void C1_MacroAssembler::verified_entry(bool breakAtEntry) {
  // If we have to make this method not-entrant we'll overwrite its
  // first instruction with a jump. For this action to be legal we
  // must ensure that this first instruction is a J, JAL or NOP.
  // Make it a NOP.
  IncompressibleRegion ir(this);  // keep the nop as 4 bytes for patching.
  assert_alignment(pc());
  nop();  // 4 bytes
}

void C1_MacroAssembler::load_parameter(int offset_in_words, Register reg) {
  //  fp + -2: link
  //     + -1: return address
  //     +  0: argument with offset 0
  //     +  1: argument with offset 1
  //     +  2: ...
  ld(reg, Address(fp, offset_in_words * BytesPerWord));
}

#ifndef PRODUCT

void C1_MacroAssembler::verify_stack_oop(int stack_offset) {
  if (!VerifyOops) {
    return;
  }
  verify_oop_addr(Address(sp, stack_offset));
}

void C1_MacroAssembler::verify_not_null_oop(Register r) {
  if (!VerifyOops) return;
  Label not_null;
  bnez(r, not_null);
  stop("non-null oop required");
  bind(not_null);
  verify_oop(r);
}

void C1_MacroAssembler::invalidate_registers(bool inv_x10, bool inv_x9, bool inv_x12, bool inv_x13, bool inv_x14, bool inv_x15) {
#ifdef ASSERT
  static int nn;
  if (inv_x10) { mv(x10, 0xDEAD); }
  if (inv_x9)  { mv(x9, 0xDEAD);  }
  if (inv_x12) { mv(x12, nn++);   }
  if (inv_x13) { mv(x13, 0xDEAD); }
  if (inv_x14) { mv(x14, 0xDEAD); }
  if (inv_x15) { mv(x15, 0xDEAD); }
#endif // ASSERT
}
#endif // ifndef PRODUCT

typedef void (C1_MacroAssembler::*c1_cond_branch_insn)(Register op1, Register op2, Label& label, bool is_far);
typedef void (C1_MacroAssembler::*c1_float_cond_branch_insn)(FloatRegister op1, FloatRegister op2,
              Label& label, bool is_far, bool is_unordered);

static c1_cond_branch_insn c1_cond_branch[] =
{
  /* SHORT branches */
  (c1_cond_branch_insn)&MacroAssembler::beq,
  (c1_cond_branch_insn)&MacroAssembler::bne,
  (c1_cond_branch_insn)&MacroAssembler::blt,
  (c1_cond_branch_insn)&MacroAssembler::ble,
  (c1_cond_branch_insn)&MacroAssembler::bge,
  (c1_cond_branch_insn)&MacroAssembler::bgt,
  (c1_cond_branch_insn)&MacroAssembler::bleu, // lir_cond_belowEqual
  (c1_cond_branch_insn)&MacroAssembler::bgeu  // lir_cond_aboveEqual
};

static c1_float_cond_branch_insn c1_float_cond_branch[] =
{
  /* FLOAT branches */
  (c1_float_cond_branch_insn)&MacroAssembler::float_beq,
  (c1_float_cond_branch_insn)&MacroAssembler::float_bne,
  (c1_float_cond_branch_insn)&MacroAssembler::float_blt,
  (c1_float_cond_branch_insn)&MacroAssembler::float_ble,
  (c1_float_cond_branch_insn)&MacroAssembler::float_bge,
  (c1_float_cond_branch_insn)&MacroAssembler::float_bgt,
  nullptr, // lir_cond_belowEqual
  nullptr, // lir_cond_aboveEqual

  /* DOUBLE branches */
  (c1_float_cond_branch_insn)&MacroAssembler::double_beq,
  (c1_float_cond_branch_insn)&MacroAssembler::double_bne,
  (c1_float_cond_branch_insn)&MacroAssembler::double_blt,
  (c1_float_cond_branch_insn)&MacroAssembler::double_ble,
  (c1_float_cond_branch_insn)&MacroAssembler::double_bge,
  (c1_float_cond_branch_insn)&MacroAssembler::double_bgt,
  nullptr, // lir_cond_belowEqual
  nullptr  // lir_cond_aboveEqual
};

void C1_MacroAssembler::c1_cmp_branch(int cmpFlag, Register op1, Register op2, Label& label,
                                      BasicType type, bool is_far) {
  if (type == T_OBJECT || type == T_ARRAY) {
    assert(cmpFlag == lir_cond_equal || cmpFlag == lir_cond_notEqual, "Should be equal or notEqual");
    if (cmpFlag == lir_cond_equal) {
      beq(op1, op2, label, is_far);
    } else {
      bne(op1, op2, label, is_far);
    }
  } else {
    assert(cmpFlag >= 0 && cmpFlag < (int)(sizeof(c1_cond_branch) / sizeof(c1_cond_branch[0])),
           "invalid c1 conditional branch index");
    (this->*c1_cond_branch[cmpFlag])(op1, op2, label, is_far);
  }
}

void C1_MacroAssembler::c1_float_cmp_branch(int cmpFlag, FloatRegister op1, FloatRegister op2, Label& label,
                                            bool is_far, bool is_unordered) {
  assert(cmpFlag >= 0 &&
         cmpFlag < (int)(sizeof(c1_float_cond_branch) / sizeof(c1_float_cond_branch[0])),
         "invalid c1 float conditional branch index");
  (this->*c1_float_cond_branch[cmpFlag])(op1, op2, label, is_far, is_unordered);
}
