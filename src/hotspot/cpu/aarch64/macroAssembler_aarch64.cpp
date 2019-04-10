/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2019, Red Hat Inc. All rights reserved.
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

#include <sys/types.h>

#include "precompiled.hpp"
#include "jvm.h"
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "interpreter/interpreter.hpp"
#include "compiler/disassembler.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_aarch64.hpp"
#include "oops/accessDecorators.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/icache.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/thread.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#endif
#ifdef COMPILER2
#include "oops/oop.hpp"
#include "opto/compile.hpp"
#include "opto/intrinsicnode.hpp"
#include "opto/node.hpp"
#endif

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#define STOP(error) stop(error)
#else
#define BLOCK_COMMENT(str) block_comment(str)
#define STOP(error) block_comment(error); stop(error)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

// Patch any kind of instruction; there may be several instructions.
// Return the total length (in bytes) of the instructions.
int MacroAssembler::pd_patch_instruction_size(address branch, address target) {
  int instructions = 1;
  assert((uint64_t)target < (1ul << 48), "48-bit overflow in address constant");
  long offset = (target - branch) >> 2;
  unsigned insn = *(unsigned*)branch;
  if ((Instruction_aarch64::extract(insn, 29, 24) & 0b111011) == 0b011000) {
    // Load register (literal)
    Instruction_aarch64::spatch(branch, 23, 5, offset);
  } else if (Instruction_aarch64::extract(insn, 30, 26) == 0b00101) {
    // Unconditional branch (immediate)
    Instruction_aarch64::spatch(branch, 25, 0, offset);
  } else if (Instruction_aarch64::extract(insn, 31, 25) == 0b0101010) {
    // Conditional branch (immediate)
    Instruction_aarch64::spatch(branch, 23, 5, offset);
  } else if (Instruction_aarch64::extract(insn, 30, 25) == 0b011010) {
    // Compare & branch (immediate)
    Instruction_aarch64::spatch(branch, 23, 5, offset);
  } else if (Instruction_aarch64::extract(insn, 30, 25) == 0b011011) {
    // Test & branch (immediate)
    Instruction_aarch64::spatch(branch, 18, 5, offset);
  } else if (Instruction_aarch64::extract(insn, 28, 24) == 0b10000) {
    // PC-rel. addressing
    offset = target-branch;
    int shift = Instruction_aarch64::extract(insn, 31, 31);
    if (shift) {
      u_int64_t dest = (u_int64_t)target;
      uint64_t pc_page = (uint64_t)branch >> 12;
      uint64_t adr_page = (uint64_t)target >> 12;
      unsigned offset_lo = dest & 0xfff;
      offset = adr_page - pc_page;

      // We handle 4 types of PC relative addressing
      //   1 - adrp    Rx, target_page
      //       ldr/str Ry, [Rx, #offset_in_page]
      //   2 - adrp    Rx, target_page
      //       add     Ry, Rx, #offset_in_page
      //   3 - adrp    Rx, target_page (page aligned reloc, offset == 0)
      //       movk    Rx, #imm16<<32
      //   4 - adrp    Rx, target_page (page aligned reloc, offset == 0)
      // In the first 3 cases we must check that Rx is the same in the adrp and the
      // subsequent ldr/str, add or movk instruction. Otherwise we could accidentally end
      // up treating a type 4 relocation as a type 1, 2 or 3 just because it happened
      // to be followed by a random unrelated ldr/str, add or movk instruction.
      //
      unsigned insn2 = ((unsigned*)branch)[1];
      if (Instruction_aarch64::extract(insn2, 29, 24) == 0b111001 &&
                Instruction_aarch64::extract(insn, 4, 0) ==
                        Instruction_aarch64::extract(insn2, 9, 5)) {
        // Load/store register (unsigned immediate)
        unsigned size = Instruction_aarch64::extract(insn2, 31, 30);
        Instruction_aarch64::patch(branch + sizeof (unsigned),
                                    21, 10, offset_lo >> size);
        guarantee(((dest >> size) << size) == dest, "misaligned target");
        instructions = 2;
      } else if (Instruction_aarch64::extract(insn2, 31, 22) == 0b1001000100 &&
                Instruction_aarch64::extract(insn, 4, 0) ==
                        Instruction_aarch64::extract(insn2, 4, 0)) {
        // add (immediate)
        Instruction_aarch64::patch(branch + sizeof (unsigned),
                                   21, 10, offset_lo);
        instructions = 2;
      } else if (Instruction_aarch64::extract(insn2, 31, 21) == 0b11110010110 &&
                   Instruction_aarch64::extract(insn, 4, 0) ==
                     Instruction_aarch64::extract(insn2, 4, 0)) {
        // movk #imm16<<32
        Instruction_aarch64::patch(branch + 4, 20, 5, (uint64_t)target >> 32);
        long dest = ((long)target & 0xffffffffL) | ((long)branch & 0xffff00000000L);
        long pc_page = (long)branch >> 12;
        long adr_page = (long)dest >> 12;
        offset = adr_page - pc_page;
        instructions = 2;
      }
    }
    int offset_lo = offset & 3;
    offset >>= 2;
    Instruction_aarch64::spatch(branch, 23, 5, offset);
    Instruction_aarch64::patch(branch, 30, 29, offset_lo);
  } else if (Instruction_aarch64::extract(insn, 31, 21) == 0b11010010100) {
    u_int64_t dest = (u_int64_t)target;
    // Move wide constant
    assert(nativeInstruction_at(branch+4)->is_movk(), "wrong insns in patch");
    assert(nativeInstruction_at(branch+8)->is_movk(), "wrong insns in patch");
    Instruction_aarch64::patch(branch, 20, 5, dest & 0xffff);
    Instruction_aarch64::patch(branch+4, 20, 5, (dest >>= 16) & 0xffff);
    Instruction_aarch64::patch(branch+8, 20, 5, (dest >>= 16) & 0xffff);
    assert(target_addr_for_insn(branch) == target, "should be");
    instructions = 3;
  } else if (Instruction_aarch64::extract(insn, 31, 22) == 0b1011100101 &&
             Instruction_aarch64::extract(insn, 4, 0) == 0b11111) {
    // nothing to do
    assert(target == 0, "did not expect to relocate target for polling page load");
  } else {
    ShouldNotReachHere();
  }
  return instructions * NativeInstruction::instruction_size;
}

int MacroAssembler::patch_oop(address insn_addr, address o) {
  int instructions;
  unsigned insn = *(unsigned*)insn_addr;
  assert(nativeInstruction_at(insn_addr+4)->is_movk(), "wrong insns in patch");

  // OOPs are either narrow (32 bits) or wide (48 bits).  We encode
  // narrow OOPs by setting the upper 16 bits in the first
  // instruction.
  if (Instruction_aarch64::extract(insn, 31, 21) == 0b11010010101) {
    // Move narrow OOP
    narrowOop n = CompressedOops::encode((oop)o);
    Instruction_aarch64::patch(insn_addr, 20, 5, n >> 16);
    Instruction_aarch64::patch(insn_addr+4, 20, 5, n & 0xffff);
    instructions = 2;
  } else {
    // Move wide OOP
    assert(nativeInstruction_at(insn_addr+8)->is_movk(), "wrong insns in patch");
    uintptr_t dest = (uintptr_t)o;
    Instruction_aarch64::patch(insn_addr, 20, 5, dest & 0xffff);
    Instruction_aarch64::patch(insn_addr+4, 20, 5, (dest >>= 16) & 0xffff);
    Instruction_aarch64::patch(insn_addr+8, 20, 5, (dest >>= 16) & 0xffff);
    instructions = 3;
  }
  return instructions * NativeInstruction::instruction_size;
}

int MacroAssembler::patch_narrow_klass(address insn_addr, narrowKlass n) {
  // Metatdata pointers are either narrow (32 bits) or wide (48 bits).
  // We encode narrow ones by setting the upper 16 bits in the first
  // instruction.
  NativeInstruction *insn = nativeInstruction_at(insn_addr);
  assert(Instruction_aarch64::extract(insn->encoding(), 31, 21) == 0b11010010101 &&
         nativeInstruction_at(insn_addr+4)->is_movk(), "wrong insns in patch");

  Instruction_aarch64::patch(insn_addr, 20, 5, n >> 16);
  Instruction_aarch64::patch(insn_addr+4, 20, 5, n & 0xffff);
  return 2 * NativeInstruction::instruction_size;
}

address MacroAssembler::target_addr_for_insn(address insn_addr, unsigned insn) {
  long offset = 0;
  if ((Instruction_aarch64::extract(insn, 29, 24) & 0b011011) == 0b00011000) {
    // Load register (literal)
    offset = Instruction_aarch64::sextract(insn, 23, 5);
    return address(((uint64_t)insn_addr + (offset << 2)));
  } else if (Instruction_aarch64::extract(insn, 30, 26) == 0b00101) {
    // Unconditional branch (immediate)
    offset = Instruction_aarch64::sextract(insn, 25, 0);
  } else if (Instruction_aarch64::extract(insn, 31, 25) == 0b0101010) {
    // Conditional branch (immediate)
    offset = Instruction_aarch64::sextract(insn, 23, 5);
  } else if (Instruction_aarch64::extract(insn, 30, 25) == 0b011010) {
    // Compare & branch (immediate)
    offset = Instruction_aarch64::sextract(insn, 23, 5);
   } else if (Instruction_aarch64::extract(insn, 30, 25) == 0b011011) {
    // Test & branch (immediate)
    offset = Instruction_aarch64::sextract(insn, 18, 5);
  } else if (Instruction_aarch64::extract(insn, 28, 24) == 0b10000) {
    // PC-rel. addressing
    offset = Instruction_aarch64::extract(insn, 30, 29);
    offset |= Instruction_aarch64::sextract(insn, 23, 5) << 2;
    int shift = Instruction_aarch64::extract(insn, 31, 31) ? 12 : 0;
    if (shift) {
      offset <<= shift;
      uint64_t target_page = ((uint64_t)insn_addr) + offset;
      target_page &= ((uint64_t)-1) << shift;
      // Return the target address for the following sequences
      //   1 - adrp    Rx, target_page
      //       ldr/str Ry, [Rx, #offset_in_page]
      //   2 - adrp    Rx, target_page
      //       add     Ry, Rx, #offset_in_page
      //   3 - adrp    Rx, target_page (page aligned reloc, offset == 0)
      //       movk    Rx, #imm12<<32
      //   4 - adrp    Rx, target_page (page aligned reloc, offset == 0)
      //
      // In the first two cases  we check that the register is the same and
      // return the target_page + the offset within the page.
      // Otherwise we assume it is a page aligned relocation and return
      // the target page only.
      //
      unsigned insn2 = ((unsigned*)insn_addr)[1];
      if (Instruction_aarch64::extract(insn2, 29, 24) == 0b111001 &&
                Instruction_aarch64::extract(insn, 4, 0) ==
                        Instruction_aarch64::extract(insn2, 9, 5)) {
        // Load/store register (unsigned immediate)
        unsigned int byte_offset = Instruction_aarch64::extract(insn2, 21, 10);
        unsigned int size = Instruction_aarch64::extract(insn2, 31, 30);
        return address(target_page + (byte_offset << size));
      } else if (Instruction_aarch64::extract(insn2, 31, 22) == 0b1001000100 &&
                Instruction_aarch64::extract(insn, 4, 0) ==
                        Instruction_aarch64::extract(insn2, 4, 0)) {
        // add (immediate)
        unsigned int byte_offset = Instruction_aarch64::extract(insn2, 21, 10);
        return address(target_page + byte_offset);
      } else {
        if (Instruction_aarch64::extract(insn2, 31, 21) == 0b11110010110  &&
               Instruction_aarch64::extract(insn, 4, 0) ==
                 Instruction_aarch64::extract(insn2, 4, 0)) {
          target_page = (target_page & 0xffffffff) |
                         ((uint64_t)Instruction_aarch64::extract(insn2, 20, 5) << 32);
        }
        return (address)target_page;
      }
    } else {
      ShouldNotReachHere();
    }
  } else if (Instruction_aarch64::extract(insn, 31, 23) == 0b110100101) {
    u_int32_t *insns = (u_int32_t *)insn_addr;
    // Move wide constant: movz, movk, movk.  See movptr().
    assert(nativeInstruction_at(insns+1)->is_movk(), "wrong insns in patch");
    assert(nativeInstruction_at(insns+2)->is_movk(), "wrong insns in patch");
    return address(u_int64_t(Instruction_aarch64::extract(insns[0], 20, 5))
                   + (u_int64_t(Instruction_aarch64::extract(insns[1], 20, 5)) << 16)
                   + (u_int64_t(Instruction_aarch64::extract(insns[2], 20, 5)) << 32));
  } else if (Instruction_aarch64::extract(insn, 31, 22) == 0b1011100101 &&
             Instruction_aarch64::extract(insn, 4, 0) == 0b11111) {
    return 0;
  } else {
    ShouldNotReachHere();
  }
  return address(((uint64_t)insn_addr + (offset << 2)));
}

void MacroAssembler::safepoint_poll(Label& slow_path) {
  if (SafepointMechanism::uses_thread_local_poll()) {
    ldr(rscratch1, Address(rthread, Thread::polling_page_offset()));
    tbnz(rscratch1, exact_log2(SafepointMechanism::poll_bit()), slow_path);
  } else {
    unsigned long offset;
    adrp(rscratch1, ExternalAddress(SafepointSynchronize::address_of_state()), offset);
    ldrw(rscratch1, Address(rscratch1, offset));
    assert(SafepointSynchronize::_not_synchronized == 0, "rewrite this code");
    cbnz(rscratch1, slow_path);
  }
}

// Just like safepoint_poll, but use an acquiring load for thread-
// local polling.
//
// We need an acquire here to ensure that any subsequent load of the
// global SafepointSynchronize::_state flag is ordered after this load
// of the local Thread::_polling page.  We don't want this poll to
// return false (i.e. not safepointing) and a later poll of the global
// SafepointSynchronize::_state spuriously to return true.
//
// This is to avoid a race when we're in a native->Java transition
// racing the code which wakes up from a safepoint.
//
void MacroAssembler::safepoint_poll_acquire(Label& slow_path) {
  if (SafepointMechanism::uses_thread_local_poll()) {
    lea(rscratch1, Address(rthread, Thread::polling_page_offset()));
    ldar(rscratch1, rscratch1);
    tbnz(rscratch1, exact_log2(SafepointMechanism::poll_bit()), slow_path);
  } else {
    safepoint_poll(slow_path);
  }
}

void MacroAssembler::reset_last_Java_frame(bool clear_fp) {
  // we must set sp to zero to clear frame
  str(zr, Address(rthread, JavaThread::last_Java_sp_offset()));

  // must clear fp, so that compiled frames are not confused; it is
  // possible that we need it only for debugging
  if (clear_fp) {
    str(zr, Address(rthread, JavaThread::last_Java_fp_offset()));
  }

  // Always clear the pc because it could have been set by make_walkable()
  str(zr, Address(rthread, JavaThread::last_Java_pc_offset()));
}

// Calls to C land
//
// When entering C land, the rfp, & resp of the last Java frame have to be recorded
// in the (thread-local) JavaThread object. When leaving C land, the last Java fp
// has to be reset to 0. This is required to allow proper stack traversal.
void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         Register last_java_pc,
                                         Register scratch) {

  if (last_java_pc->is_valid()) {
      str(last_java_pc, Address(rthread,
                                JavaThread::frame_anchor_offset()
                                + JavaFrameAnchor::last_Java_pc_offset()));
    }

  // determine last_java_sp register
  if (last_java_sp == sp) {
    mov(scratch, sp);
    last_java_sp = scratch;
  } else if (!last_java_sp->is_valid()) {
    last_java_sp = esp;
  }

  str(last_java_sp, Address(rthread, JavaThread::last_Java_sp_offset()));

  // last_java_fp is optional
  if (last_java_fp->is_valid()) {
    str(last_java_fp, Address(rthread, JavaThread::last_Java_fp_offset()));
  }
}

void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         address  last_java_pc,
                                         Register scratch) {
  assert(last_java_pc != NULL, "must provide a valid PC");

  adr(scratch, last_java_pc);
  str(scratch, Address(rthread,
                       JavaThread::frame_anchor_offset()
                       + JavaFrameAnchor::last_Java_pc_offset()));

  set_last_Java_frame(last_java_sp, last_java_fp, noreg, scratch);
}

void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         Label &L,
                                         Register scratch) {
  if (L.is_bound()) {
    set_last_Java_frame(last_java_sp, last_java_fp, target(L), scratch);
  } else {
    InstructionMark im(this);
    L.add_patch_at(code(), locator());
    set_last_Java_frame(last_java_sp, last_java_fp, pc() /* Patched later */, scratch);
  }
}

void MacroAssembler::far_call(Address entry, CodeBuffer *cbuf, Register tmp) {
  assert(ReservedCodeCacheSize < 4*G, "branch out of range");
  assert(CodeCache::find_blob(entry.target()) != NULL,
         "destination of far call not found in code cache");
  if (far_branches()) {
    unsigned long offset;
    // We can use ADRP here because we know that the total size of
    // the code cache cannot exceed 2Gb.
    adrp(tmp, entry, offset);
    add(tmp, tmp, offset);
    if (cbuf) cbuf->set_insts_mark();
    blr(tmp);
  } else {
    if (cbuf) cbuf->set_insts_mark();
    bl(entry);
  }
}

void MacroAssembler::far_jump(Address entry, CodeBuffer *cbuf, Register tmp) {
  assert(ReservedCodeCacheSize < 4*G, "branch out of range");
  assert(CodeCache::find_blob(entry.target()) != NULL,
         "destination of far call not found in code cache");
  if (far_branches()) {
    unsigned long offset;
    // We can use ADRP here because we know that the total size of
    // the code cache cannot exceed 2Gb.
    adrp(tmp, entry, offset);
    add(tmp, tmp, offset);
    if (cbuf) cbuf->set_insts_mark();
    br(tmp);
  } else {
    if (cbuf) cbuf->set_insts_mark();
    b(entry);
  }
}

void MacroAssembler::reserved_stack_check() {
    // testing if reserved zone needs to be enabled
    Label no_reserved_zone_enabling;

    ldr(rscratch1, Address(rthread, JavaThread::reserved_stack_activation_offset()));
    cmp(sp, rscratch1);
    br(Assembler::LO, no_reserved_zone_enabling);

    enter();   // LR and FP are live.
    lea(rscratch1, CAST_FROM_FN_PTR(address, SharedRuntime::enable_stack_reserved_zone));
    mov(c_rarg0, rthread);
    blr(rscratch1);
    leave();

    // We have already removed our own frame.
    // throw_delayed_StackOverflowError will think that it's been
    // called by our caller.
    lea(rscratch1, RuntimeAddress(StubRoutines::throw_delayed_StackOverflowError_entry()));
    br(rscratch1);
    should_not_reach_here();

    bind(no_reserved_zone_enabling);
}

int MacroAssembler::biased_locking_enter(Register lock_reg,
                                         Register obj_reg,
                                         Register swap_reg,
                                         Register tmp_reg,
                                         bool swap_reg_contains_mark,
                                         Label& done,
                                         Label* slow_case,
                                         BiasedLockingCounters* counters) {
  assert(UseBiasedLocking, "why call this otherwise?");
  assert_different_registers(lock_reg, obj_reg, swap_reg);

  if (PrintBiasedLockingStatistics && counters == NULL)
    counters = BiasedLocking::counters();

  assert_different_registers(lock_reg, obj_reg, swap_reg, tmp_reg, rscratch1, rscratch2, noreg);
  assert(markOopDesc::age_shift == markOopDesc::lock_bits + markOopDesc::biased_lock_bits, "biased locking makes assumptions about bit layout");
  Address mark_addr      (obj_reg, oopDesc::mark_offset_in_bytes());
  Address klass_addr     (obj_reg, oopDesc::klass_offset_in_bytes());
  Address saved_mark_addr(lock_reg, 0);

  // Biased locking
  // See whether the lock is currently biased toward our thread and
  // whether the epoch is still valid
  // Note that the runtime guarantees sufficient alignment of JavaThread
  // pointers to allow age to be placed into low bits
  // First check to see whether biasing is even enabled for this object
  Label cas_label;
  int null_check_offset = -1;
  if (!swap_reg_contains_mark) {
    null_check_offset = offset();
    ldr(swap_reg, mark_addr);
  }
  andr(tmp_reg, swap_reg, markOopDesc::biased_lock_mask_in_place);
  cmp(tmp_reg, (u1)markOopDesc::biased_lock_pattern);
  br(Assembler::NE, cas_label);
  // The bias pattern is present in the object's header. Need to check
  // whether the bias owner and the epoch are both still current.
  load_prototype_header(tmp_reg, obj_reg);
  orr(tmp_reg, tmp_reg, rthread);
  eor(tmp_reg, swap_reg, tmp_reg);
  andr(tmp_reg, tmp_reg, ~((int) markOopDesc::age_mask_in_place));
  if (counters != NULL) {
    Label around;
    cbnz(tmp_reg, around);
    atomic_incw(Address((address)counters->biased_lock_entry_count_addr()), tmp_reg, rscratch1, rscratch2);
    b(done);
    bind(around);
  } else {
    cbz(tmp_reg, done);
  }

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
  andr(rscratch1, tmp_reg, markOopDesc::biased_lock_mask_in_place);
  cbnz(rscratch1, try_revoke_bias);

  // Biasing is still enabled for this data type. See whether the
  // epoch of the current bias is still valid, meaning that the epoch
  // bits of the mark word are equal to the epoch bits of the
  // prototype header. (Note that the prototype header's epoch bits
  // only change at a safepoint.) If not, attempt to rebias the object
  // toward the current thread. Note that we must be absolutely sure
  // that the current epoch is invalid in order to do this because
  // otherwise the manipulations it performs on the mark word are
  // illegal.
  andr(rscratch1, tmp_reg, markOopDesc::epoch_mask_in_place);
  cbnz(rscratch1, try_rebias);

  // The epoch of the current bias is still valid but we know nothing
  // about the owner; it might be set or it might be clear. Try to
  // acquire the bias of the object using an atomic operation. If this
  // fails we will go in to the runtime to revoke the object's bias.
  // Note that we first construct the presumed unbiased header so we
  // don't accidentally blow away another thread's valid bias.
  {
    Label here;
    mov(rscratch1, markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place | markOopDesc::epoch_mask_in_place);
    andr(swap_reg, swap_reg, rscratch1);
    orr(tmp_reg, swap_reg, rthread);
    cmpxchg_obj_header(swap_reg, tmp_reg, obj_reg, rscratch1, here, slow_case);
    // If the biasing toward our thread failed, this means that
    // another thread succeeded in biasing it toward itself and we
    // need to revoke that bias. The revocation will occur in the
    // interpreter runtime in the slow case.
    bind(here);
    if (counters != NULL) {
      atomic_incw(Address((address)counters->anonymously_biased_lock_entry_count_addr()),
                  tmp_reg, rscratch1, rscratch2);
    }
  }
  b(done);

  bind(try_rebias);
  // At this point we know the epoch has expired, meaning that the
  // current "bias owner", if any, is actually invalid. Under these
  // circumstances _only_, we are allowed to use the current header's
  // value as the comparison value when doing the cas to acquire the
  // bias in the current epoch. In other words, we allow transfer of
  // the bias from one thread to another directly in this situation.
  //
  // FIXME: due to a lack of registers we currently blow away the age
  // bits in this situation. Should attempt to preserve them.
  {
    Label here;
    load_prototype_header(tmp_reg, obj_reg);
    orr(tmp_reg, rthread, tmp_reg);
    cmpxchg_obj_header(swap_reg, tmp_reg, obj_reg, rscratch1, here, slow_case);
    // If the biasing toward our thread failed, then another thread
    // succeeded in biasing it toward itself and we need to revoke that
    // bias. The revocation will occur in the runtime in the slow case.
    bind(here);
    if (counters != NULL) {
      atomic_incw(Address((address)counters->rebiased_lock_entry_count_addr()),
                  tmp_reg, rscratch1, rscratch2);
    }
  }
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
  //
  // FIXME: due to a lack of registers we currently blow away the age
  // bits in this situation. Should attempt to preserve them.
  {
    Label here, nope;
    load_prototype_header(tmp_reg, obj_reg);
    cmpxchg_obj_header(swap_reg, tmp_reg, obj_reg, rscratch1, here, &nope);
    bind(here);

    // Fall through to the normal CAS-based lock, because no matter what
    // the result of the above CAS, some thread must have succeeded in
    // removing the bias bit from the object's header.
    if (counters != NULL) {
      atomic_incw(Address((address)counters->revoked_lock_entry_count_addr()), tmp_reg,
                  rscratch1, rscratch2);
    }
    bind(nope);
  }

  bind(cas_label);

  return null_check_offset;
}

void MacroAssembler::biased_locking_exit(Register obj_reg, Register temp_reg, Label& done) {
  assert(UseBiasedLocking, "why call this otherwise?");

  // Check for biased locking unlock case, which is a no-op
  // Note: we do not have to check the thread ID for two reasons.
  // First, the interpreter checks for IllegalMonitorStateException at
  // a higher level. Second, if the bias was revoked while we held the
  // lock, the object could not be rebiased toward another thread, so
  // the bias bit would be clear.
  ldr(temp_reg, Address(obj_reg, oopDesc::mark_offset_in_bytes()));
  andr(temp_reg, temp_reg, markOopDesc::biased_lock_mask_in_place);
  cmp(temp_reg, (u1)markOopDesc::biased_lock_pattern);
  br(Assembler::EQ, done);
}

static void pass_arg0(MacroAssembler* masm, Register arg) {
  if (c_rarg0 != arg ) {
    masm->mov(c_rarg0, arg);
  }
}

static void pass_arg1(MacroAssembler* masm, Register arg) {
  if (c_rarg1 != arg ) {
    masm->mov(c_rarg1, arg);
  }
}

static void pass_arg2(MacroAssembler* masm, Register arg) {
  if (c_rarg2 != arg ) {
    masm->mov(c_rarg2, arg);
  }
}

static void pass_arg3(MacroAssembler* masm, Register arg) {
  if (c_rarg3 != arg ) {
    masm->mov(c_rarg3, arg);
  }
}

void MacroAssembler::call_VM_base(Register oop_result,
                                  Register java_thread,
                                  Register last_java_sp,
                                  address  entry_point,
                                  int      number_of_arguments,
                                  bool     check_exceptions) {
   // determine java_thread register
  if (!java_thread->is_valid()) {
    java_thread = rthread;
  }

  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = esp;
  }

  // debugging support
  assert(number_of_arguments >= 0   , "cannot have negative number of arguments");
  assert(java_thread == rthread, "unexpected register");
#ifdef ASSERT
  // TraceBytecodes does not use r12 but saves it over the call, so don't verify
  // if ((UseCompressedOops || UseCompressedClassPointers) && !TraceBytecodes) verify_heapbase("call_VM_base: heap base corrupted?");
#endif // ASSERT

  assert(java_thread != oop_result  , "cannot use the same register for java_thread & oop_result");
  assert(java_thread != last_java_sp, "cannot use the same register for java_thread & last_java_sp");

  // push java thread (becomes first argument of C function)

  mov(c_rarg0, java_thread);

  // set last Java frame before call
  assert(last_java_sp != rfp, "can't use rfp");

  Label l;
  set_last_Java_frame(last_java_sp, rfp, l, rscratch1);

  // do the call, remove parameters
  MacroAssembler::call_VM_leaf_base(entry_point, number_of_arguments, &l);

  // reset last Java frame
  // Only interpreter should have to clear fp
  reset_last_Java_frame(true);

   // C++ interp handles this in the interpreter
  check_and_handle_popframe(java_thread);
  check_and_handle_earlyret(java_thread);

  if (check_exceptions) {
    // check for pending exceptions (java_thread is set upon return)
    ldr(rscratch1, Address(java_thread, in_bytes(Thread::pending_exception_offset())));
    Label ok;
    cbz(rscratch1, ok);
    lea(rscratch1, RuntimeAddress(StubRoutines::forward_exception_entry()));
    br(rscratch1);
    bind(ok);
  }

  // get oop result if there is one and reset the value in the thread
  if (oop_result->is_valid()) {
    get_vm_result(oop_result, java_thread);
  }
}

void MacroAssembler::call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions) {
  call_VM_base(oop_result, noreg, noreg, entry_point, number_of_arguments, check_exceptions);
}

// Maybe emit a call via a trampoline.  If the code cache is small
// trampolines won't be emitted.

address MacroAssembler::trampoline_call(Address entry, CodeBuffer *cbuf) {
  assert(JavaThread::current()->is_Compiler_thread(), "just checking");
  assert(entry.rspec().type() == relocInfo::runtime_call_type
         || entry.rspec().type() == relocInfo::opt_virtual_call_type
         || entry.rspec().type() == relocInfo::static_call_type
         || entry.rspec().type() == relocInfo::virtual_call_type, "wrong reloc type");

  // We need a trampoline if branches are far.
  if (far_branches()) {
    bool in_scratch_emit_size = false;
#ifdef COMPILER2
    // We don't want to emit a trampoline if C2 is generating dummy
    // code during its branch shortening phase.
    CompileTask* task = ciEnv::current()->task();
    in_scratch_emit_size =
      (task != NULL && is_c2_compile(task->comp_level()) &&
       Compile::current()->in_scratch_emit_size());
#endif
    if (!in_scratch_emit_size) {
      address stub = emit_trampoline_stub(offset(), entry.target());
      if (stub == NULL) {
        return NULL; // CodeCache is full
      }
    }
  }

  if (cbuf) cbuf->set_insts_mark();
  relocate(entry.rspec());
  if (!far_branches()) {
    bl(entry.target());
  } else {
    bl(pc());
  }
  // just need to return a non-null address
  return pc();
}


// Emit a trampoline stub for a call to a target which is too far away.
//
// code sequences:
//
// call-site:
//   branch-and-link to <destination> or <trampoline stub>
//
// Related trampoline stub for this call site in the stub section:
//   load the call target from the constant pool
//   branch (LR still points to the call site above)

address MacroAssembler::emit_trampoline_stub(int insts_call_instruction_offset,
                                             address dest) {
  // Max stub size: alignment nop, TrampolineStub.
  address stub = start_a_stub(NativeInstruction::instruction_size
                   + NativeCallTrampolineStub::instruction_size);
  if (stub == NULL) {
    return NULL;  // CodeBuffer::expand failed
  }

  // Create a trampoline stub relocation which relates this trampoline stub
  // with the call instruction at insts_call_instruction_offset in the
  // instructions code-section.
  align(wordSize);
  relocate(trampoline_stub_Relocation::spec(code()->insts()->start()
                                            + insts_call_instruction_offset));
  const int stub_start_offset = offset();

  // Now, create the trampoline stub's code:
  // - load the call
  // - call
  Label target;
  ldr(rscratch1, target);
  br(rscratch1);
  bind(target);
  assert(offset() - stub_start_offset == NativeCallTrampolineStub::data_offset,
         "should be");
  emit_int64((int64_t)dest);

  const address stub_start_addr = addr_at(stub_start_offset);

  assert(is_NativeCallTrampolineStub_at(stub_start_addr), "doesn't look like a trampoline");

  end_a_stub();
  return stub_start_addr;
}

void MacroAssembler::emit_static_call_stub() {
  // CompiledDirectStaticCall::set_to_interpreted knows the
  // exact layout of this stub.

  isb();
  mov_metadata(rmethod, (Metadata*)NULL);

  // Jump to the entry point of the i2c stub.
  movptr(rscratch1, 0);
  br(rscratch1);
}

void MacroAssembler::c2bool(Register x) {
  // implements x == 0 ? 0 : 1
  // note: must only look at least-significant byte of x
  //       since C-style booleans are stored in one byte
  //       only! (was bug)
  tst(x, 0xff);
  cset(x, Assembler::NE);
}

address MacroAssembler::ic_call(address entry, jint method_index) {
  RelocationHolder rh = virtual_call_Relocation::spec(pc(), method_index);
  // address const_ptr = long_constant((jlong)Universe::non_oop_word());
  // unsigned long offset;
  // ldr_constant(rscratch2, const_ptr);
  movptr(rscratch2, (uintptr_t)Universe::non_oop_word());
  return trampoline_call(Address(entry, rh));
}

// Implementation of call_VM versions

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             bool check_exceptions) {
  call_VM_helper(oop_result, entry_point, 0, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             bool check_exceptions) {
  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 1, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             bool check_exceptions) {
  assert(arg_1 != c_rarg2, "smashed arg");
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 2, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             Register arg_3,
                             bool check_exceptions) {
  assert(arg_1 != c_rarg3, "smashed arg");
  assert(arg_2 != c_rarg3, "smashed arg");
  pass_arg3(this, arg_3);

  assert(arg_1 != c_rarg2, "smashed arg");
  pass_arg2(this, arg_2);

  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 3, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             int number_of_arguments,
                             bool check_exceptions) {
  call_VM_base(oop_result, rthread, last_java_sp, entry_point, number_of_arguments, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             bool check_exceptions) {
  pass_arg1(this, arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 1, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             bool check_exceptions) {

  assert(arg_1 != c_rarg2, "smashed arg");
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 2, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             Register arg_3,
                             bool check_exceptions) {
  assert(arg_1 != c_rarg3, "smashed arg");
  assert(arg_2 != c_rarg3, "smashed arg");
  pass_arg3(this, arg_3);
  assert(arg_1 != c_rarg2, "smashed arg");
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 3, check_exceptions);
}


void MacroAssembler::get_vm_result(Register oop_result, Register java_thread) {
  ldr(oop_result, Address(java_thread, JavaThread::vm_result_offset()));
  str(zr, Address(java_thread, JavaThread::vm_result_offset()));
  verify_oop(oop_result, "broken oop in call_VM_base");
}

void MacroAssembler::get_vm_result_2(Register metadata_result, Register java_thread) {
  ldr(metadata_result, Address(java_thread, JavaThread::vm_result_2_offset()));
  str(zr, Address(java_thread, JavaThread::vm_result_2_offset()));
}

void MacroAssembler::align(int modulus) {
  while (offset() % modulus != 0) nop();
}

// these are no-ops overridden by InterpreterMacroAssembler

void MacroAssembler::check_and_handle_earlyret(Register java_thread) { }

void MacroAssembler::check_and_handle_popframe(Register java_thread) { }


RegisterOrConstant MacroAssembler::delayed_value_impl(intptr_t* delayed_value_addr,
                                                      Register tmp,
                                                      int offset) {
  intptr_t value = *delayed_value_addr;
  if (value != 0)
    return RegisterOrConstant(value + offset);

  // load indirectly to solve generation ordering problem
  ldr(tmp, ExternalAddress((address) delayed_value_addr));

  if (offset != 0)
    add(tmp, tmp, offset);

  return RegisterOrConstant(tmp);
}


void MacroAssembler:: notify(int type) {
  if (type == bytecode_start) {
    // set_last_Java_frame(esp, rfp, (address)NULL);
    Assembler:: notify(type);
    // reset_last_Java_frame(true);
  }
  else
    Assembler:: notify(type);
}

// Look up the method for a megamorphic invokeinterface call.
// The target method is determined by <intf_klass, itable_index>.
// The receiver klass is in recv_klass.
// On success, the result will be in method_result, and execution falls through.
// On failure, execution transfers to the given label.
void MacroAssembler::lookup_interface_method(Register recv_klass,
                                             Register intf_klass,
                                             RegisterOrConstant itable_index,
                                             Register method_result,
                                             Register scan_temp,
                                             Label& L_no_such_interface,
                         bool return_method) {
  assert_different_registers(recv_klass, intf_klass, scan_temp);
  assert_different_registers(method_result, intf_klass, scan_temp);
  assert(recv_klass != method_result || !return_method,
     "recv_klass can be destroyed when method isn't needed");
  assert(itable_index.is_constant() || itable_index.as_register() == method_result,
         "caller must use same register for non-constant itable index as for method");

  // Compute start of first itableOffsetEntry (which is at the end of the vtable)
  int vtable_base = in_bytes(Klass::vtable_start_offset());
  int itentry_off = itableMethodEntry::method_offset_in_bytes();
  int scan_step   = itableOffsetEntry::size() * wordSize;
  int vte_size    = vtableEntry::size_in_bytes();
  assert(vte_size == wordSize, "else adjust times_vte_scale");

  ldrw(scan_temp, Address(recv_klass, Klass::vtable_length_offset()));

  // %%% Could store the aligned, prescaled offset in the klassoop.
  // lea(scan_temp, Address(recv_klass, scan_temp, times_vte_scale, vtable_base));
  lea(scan_temp, Address(recv_klass, scan_temp, Address::lsl(3)));
  add(scan_temp, scan_temp, vtable_base);

  if (return_method) {
    // Adjust recv_klass by scaled itable_index, so we can free itable_index.
    assert(itableMethodEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");
    // lea(recv_klass, Address(recv_klass, itable_index, Address::times_ptr, itentry_off));
    lea(recv_klass, Address(recv_klass, itable_index, Address::lsl(3)));
    if (itentry_off)
      add(recv_klass, recv_klass, itentry_off);
  }

  // for (scan = klass->itable(); scan->interface() != NULL; scan += scan_step) {
  //   if (scan->interface() == intf) {
  //     result = (klass + scan->offset() + itable_index);
  //   }
  // }
  Label search, found_method;

  for (int peel = 1; peel >= 0; peel--) {
    ldr(method_result, Address(scan_temp, itableOffsetEntry::interface_offset_in_bytes()));
    cmp(intf_klass, method_result);

    if (peel) {
      br(Assembler::EQ, found_method);
    } else {
      br(Assembler::NE, search);
      // (invert the test to fall through to found_method...)
    }

    if (!peel)  break;

    bind(search);

    // Check that the previous entry is non-null.  A null entry means that
    // the receiver class doesn't implement the interface, and wasn't the
    // same as when the caller was compiled.
    cbz(method_result, L_no_such_interface);
    add(scan_temp, scan_temp, scan_step);
  }

  bind(found_method);

  // Got a hit.
  if (return_method) {
    ldrw(scan_temp, Address(scan_temp, itableOffsetEntry::offset_offset_in_bytes()));
    ldr(method_result, Address(recv_klass, scan_temp, Address::uxtw(0)));
  }
}

// virtual method calling
void MacroAssembler::lookup_virtual_method(Register recv_klass,
                                           RegisterOrConstant vtable_index,
                                           Register method_result) {
  const int base = in_bytes(Klass::vtable_start_offset());
  assert(vtableEntry::size() * wordSize == 8,
         "adjust the scaling in the code below");
  int vtable_offset_in_bytes = base + vtableEntry::method_offset_in_bytes();

  if (vtable_index.is_register()) {
    lea(method_result, Address(recv_klass,
                               vtable_index.as_register(),
                               Address::lsl(LogBytesPerWord)));
    ldr(method_result, Address(method_result, vtable_offset_in_bytes));
  } else {
    vtable_offset_in_bytes += vtable_index.as_constant() * wordSize;
    ldr(method_result,
        form_address(rscratch1, recv_klass, vtable_offset_in_bytes, 0));
  }
}

void MacroAssembler::check_klass_subtype(Register sub_klass,
                           Register super_klass,
                           Register temp_reg,
                           Label& L_success) {
  Label L_failure;
  check_klass_subtype_fast_path(sub_klass, super_klass, temp_reg,        &L_success, &L_failure, NULL);
  check_klass_subtype_slow_path(sub_klass, super_klass, temp_reg, noreg, &L_success, NULL);
  bind(L_failure);
}


void MacroAssembler::check_klass_subtype_fast_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   Label* L_slow_path,
                                        RegisterOrConstant super_check_offset) {
  assert_different_registers(sub_klass, super_klass, temp_reg);
  bool must_load_sco = (super_check_offset.constant_or_zero() == -1);
  if (super_check_offset.is_register()) {
    assert_different_registers(sub_klass, super_klass,
                               super_check_offset.as_register());
  } else if (must_load_sco) {
    assert(temp_reg != noreg, "supply either a temp or a register offset");
  }

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == NULL)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == NULL)   { L_failure   = &L_fallthrough; label_nulls++; }
  if (L_slow_path == NULL) { L_slow_path = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one NULL in the batch");

  int sc_offset = in_bytes(Klass::secondary_super_cache_offset());
  int sco_offset = in_bytes(Klass::super_check_offset_offset());
  Address super_check_offset_addr(super_klass, sco_offset);

  // Hacked jmp, which may only be used just before L_fallthrough.
#define final_jmp(label)                                                \
  if (&(label) == &L_fallthrough) { /*do nothing*/ }                    \
  else                            b(label)                /*omit semi*/

  // If the pointers are equal, we are done (e.g., String[] elements).
  // This self-check enables sharing of secondary supertype arrays among
  // non-primary types such as array-of-interface.  Otherwise, each such
  // type would need its own customized SSA.
  // We move this check to the front of the fast path because many
  // type checks are in fact trivially successful in this manner,
  // so we get a nicely predicted branch right at the start of the check.
  cmp(sub_klass, super_klass);
  br(Assembler::EQ, *L_success);

  // Check the supertype display:
  if (must_load_sco) {
    ldrw(temp_reg, super_check_offset_addr);
    super_check_offset = RegisterOrConstant(temp_reg);
  }
  Address super_check_addr(sub_klass, super_check_offset);
  ldr(rscratch1, super_check_addr);
  cmp(super_klass, rscratch1); // load displayed supertype

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

  if (super_check_offset.is_register()) {
    br(Assembler::EQ, *L_success);
    subs(zr, super_check_offset.as_register(), sc_offset);
    if (L_failure == &L_fallthrough) {
      br(Assembler::EQ, *L_slow_path);
    } else {
      br(Assembler::NE, *L_failure);
      final_jmp(*L_slow_path);
    }
  } else if (super_check_offset.as_constant() == sc_offset) {
    // Need a slow path; fast failure is impossible.
    if (L_slow_path == &L_fallthrough) {
      br(Assembler::EQ, *L_success);
    } else {
      br(Assembler::NE, *L_slow_path);
      final_jmp(*L_success);
    }
  } else {
    // No slow path; it's a fast decision.
    if (L_failure == &L_fallthrough) {
      br(Assembler::EQ, *L_success);
    } else {
      br(Assembler::NE, *L_failure);
      final_jmp(*L_success);
    }
  }

  bind(L_fallthrough);

#undef final_jmp
}

// These two are taken from x86, but they look generally useful

// scans count pointer sized words at [addr] for occurence of value,
// generic
void MacroAssembler::repne_scan(Register addr, Register value, Register count,
                                Register scratch) {
  Label Lloop, Lexit;
  cbz(count, Lexit);
  bind(Lloop);
  ldr(scratch, post(addr, wordSize));
  cmp(value, scratch);
  br(EQ, Lexit);
  sub(count, count, 1);
  cbnz(count, Lloop);
  bind(Lexit);
}

// scans count 4 byte words at [addr] for occurence of value,
// generic
void MacroAssembler::repne_scanw(Register addr, Register value, Register count,
                                Register scratch) {
  Label Lloop, Lexit;
  cbz(count, Lexit);
  bind(Lloop);
  ldrw(scratch, post(addr, wordSize));
  cmpw(value, scratch);
  br(EQ, Lexit);
  sub(count, count, 1);
  cbnz(count, Lloop);
  bind(Lexit);
}

void MacroAssembler::check_klass_subtype_slow_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Register temp2_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   bool set_cond_codes) {
  assert_different_registers(sub_klass, super_klass, temp_reg);
  if (temp2_reg != noreg)
    assert_different_registers(sub_klass, super_klass, temp_reg, temp2_reg, rscratch1);
#define IS_A_TEMP(reg) ((reg) == temp_reg || (reg) == temp2_reg)

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

  BLOCK_COMMENT("check_klass_subtype_slow_path");

  // Do a linear scan of the secondary super-klass chain.
  // This code is rarely used, so simplicity is a virtue here.
  // The repne_scan instruction uses fixed registers, which we must spill.
  // Don't worry too much about pre-existing connections with the input regs.

  assert(sub_klass != r0, "killed reg"); // killed by mov(r0, super)
  assert(sub_klass != r2, "killed reg"); // killed by lea(r2, &pst_counter)

  RegSet pushed_registers;
  if (!IS_A_TEMP(r2))    pushed_registers += r2;
  if (!IS_A_TEMP(r5))    pushed_registers += r5;

  if (super_klass != r0 || UseCompressedOops) {
    if (!IS_A_TEMP(r0))   pushed_registers += r0;
  }

  push(pushed_registers, sp);

  // Get super_klass value into r0 (even if it was in r5 or r2).
  if (super_klass != r0) {
    mov(r0, super_klass);
  }

#ifndef PRODUCT
  mov(rscratch2, (address)&SharedRuntime::_partial_subtype_ctr);
  Address pst_counter_addr(rscratch2);
  ldr(rscratch1, pst_counter_addr);
  add(rscratch1, rscratch1, 1);
  str(rscratch1, pst_counter_addr);
#endif //PRODUCT

  // We will consult the secondary-super array.
  ldr(r5, secondary_supers_addr);
  // Load the array length.
  ldrw(r2, Address(r5, Array<Klass*>::length_offset_in_bytes()));
  // Skip to start of data.
  add(r5, r5, Array<Klass*>::base_offset_in_bytes());

  cmp(sp, zr); // Clear Z flag; SP is never zero
  // Scan R2 words at [R5] for an occurrence of R0.
  // Set NZ/Z based on last compare.
  repne_scan(r5, r0, r2, rscratch1);

  // Unspill the temp. registers:
  pop(pushed_registers, sp);

  br(Assembler::NE, *L_failure);

  // Success.  Cache the super we found and proceed in triumph.
  str(super_klass, super_cache_addr);

  if (L_success != &L_fallthrough) {
    b(*L_success);
  }

#undef IS_A_TEMP

  bind(L_fallthrough);
}


void MacroAssembler::verify_oop(Register reg, const char* s) {
  if (!VerifyOops) return;

  // Pass register number to verify_oop_subroutine
  const char* b = NULL;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("verify_oop: %s: %s", reg->name(), s);
    b = code_string(ss.as_string());
  }
  BLOCK_COMMENT("verify_oop {");

  stp(r0, rscratch1, Address(pre(sp, -2 * wordSize)));
  stp(rscratch2, lr, Address(pre(sp, -2 * wordSize)));

  mov(r0, reg);
  mov(rscratch1, (address)b);

  // call indirectly to solve generation ordering problem
  lea(rscratch2, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  ldr(rscratch2, Address(rscratch2));
  blr(rscratch2);

  ldp(rscratch2, lr, Address(post(sp, 2 * wordSize)));
  ldp(r0, rscratch1, Address(post(sp, 2 * wordSize)));

  BLOCK_COMMENT("} verify_oop");
}

void MacroAssembler::verify_oop_addr(Address addr, const char* s) {
  if (!VerifyOops) return;

  const char* b = NULL;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("verify_oop_addr: %s", s);
    b = code_string(ss.as_string());
  }
  BLOCK_COMMENT("verify_oop_addr {");

  stp(r0, rscratch1, Address(pre(sp, -2 * wordSize)));
  stp(rscratch2, lr, Address(pre(sp, -2 * wordSize)));

  // addr may contain sp so we will have to adjust it based on the
  // pushes that we just did.
  if (addr.uses(sp)) {
    lea(r0, addr);
    ldr(r0, Address(r0, 4 * wordSize));
  } else {
    ldr(r0, addr);
  }
  mov(rscratch1, (address)b);

  // call indirectly to solve generation ordering problem
  lea(rscratch2, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  ldr(rscratch2, Address(rscratch2));
  blr(rscratch2);

  ldp(rscratch2, lr, Address(post(sp, 2 * wordSize)));
  ldp(r0, rscratch1, Address(post(sp, 2 * wordSize)));

  BLOCK_COMMENT("} verify_oop_addr");
}

Address MacroAssembler::argument_address(RegisterOrConstant arg_slot,
                                         int extra_slot_offset) {
  // cf. TemplateTable::prepare_invoke(), if (load_receiver).
  int stackElementSize = Interpreter::stackElementSize;
  int offset = Interpreter::expr_offset_in_bytes(extra_slot_offset+0);
#ifdef ASSERT
  int offset1 = Interpreter::expr_offset_in_bytes(extra_slot_offset+1);
  assert(offset1 - offset == stackElementSize, "correct arithmetic");
#endif
  if (arg_slot.is_constant()) {
    return Address(esp, arg_slot.as_constant() * stackElementSize
                   + offset);
  } else {
    add(rscratch1, esp, arg_slot.as_register(),
        ext::uxtx, exact_log2(stackElementSize));
    return Address(rscratch1, offset);
  }
}

void MacroAssembler::call_VM_leaf_base(address entry_point,
                                       int number_of_arguments,
                                       Label *retaddr) {
  call_VM_leaf_base1(entry_point, number_of_arguments, 0, ret_type_integral, retaddr);
}

void MacroAssembler::call_VM_leaf_base1(address entry_point,
                                        int number_of_gp_arguments,
                                        int number_of_fp_arguments,
                                        ret_type type,
                                        Label *retaddr) {
  Label E, L;

  stp(rscratch1, rmethod, Address(pre(sp, -2 * wordSize)));

  // We add 1 to number_of_arguments because the thread in arg0 is
  // not counted
  mov(rscratch1, entry_point);
  blrt(rscratch1, number_of_gp_arguments + 1, number_of_fp_arguments, type);
  if (retaddr)
    bind(*retaddr);

  ldp(rscratch1, rmethod, Address(post(sp, 2 * wordSize)));
  maybe_isb();
}

void MacroAssembler::call_VM_leaf(address entry_point, int number_of_arguments) {
  call_VM_leaf_base(entry_point, number_of_arguments);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0) {
  pass_arg0(this, arg_0);
  call_VM_leaf_base(entry_point, 1);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0, Register arg_1) {
  pass_arg0(this, arg_0);
  pass_arg1(this, arg_1);
  call_VM_leaf_base(entry_point, 2);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0,
                                  Register arg_1, Register arg_2) {
  pass_arg0(this, arg_0);
  pass_arg1(this, arg_1);
  pass_arg2(this, arg_2);
  call_VM_leaf_base(entry_point, 3);
}

void MacroAssembler::super_call_VM_leaf(address entry_point, Register arg_0) {
  pass_arg0(this, arg_0);
  MacroAssembler::call_VM_leaf_base(entry_point, 1);
}

void MacroAssembler::super_call_VM_leaf(address entry_point, Register arg_0, Register arg_1) {

  assert(arg_0 != c_rarg1, "smashed arg");
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  MacroAssembler::call_VM_leaf_base(entry_point, 2);
}

void MacroAssembler::super_call_VM_leaf(address entry_point, Register arg_0, Register arg_1, Register arg_2) {
  assert(arg_0 != c_rarg2, "smashed arg");
  assert(arg_1 != c_rarg2, "smashed arg");
  pass_arg2(this, arg_2);
  assert(arg_0 != c_rarg1, "smashed arg");
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  MacroAssembler::call_VM_leaf_base(entry_point, 3);
}

void MacroAssembler::super_call_VM_leaf(address entry_point, Register arg_0, Register arg_1, Register arg_2, Register arg_3) {
  assert(arg_0 != c_rarg3, "smashed arg");
  assert(arg_1 != c_rarg3, "smashed arg");
  assert(arg_2 != c_rarg3, "smashed arg");
  pass_arg3(this, arg_3);
  assert(arg_0 != c_rarg2, "smashed arg");
  assert(arg_1 != c_rarg2, "smashed arg");
  pass_arg2(this, arg_2);
  assert(arg_0 != c_rarg1, "smashed arg");
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  MacroAssembler::call_VM_leaf_base(entry_point, 4);
}

void MacroAssembler::null_check(Register reg, int offset) {
  if (needs_explicit_null_check(offset)) {
    // provoke OS NULL exception if reg = NULL by
    // accessing M[reg] w/o changing any registers
    // NOTE: this is plenty to provoke a segv
    ldr(zr, Address(reg));
  } else {
    // nothing to do, (later) access of M[reg + offset]
    // will provoke OS NULL exception if reg = NULL
  }
}

// MacroAssembler protected routines needed to implement
// public methods

void MacroAssembler::mov(Register r, Address dest) {
  code_section()->relocate(pc(), dest.rspec());
  u_int64_t imm64 = (u_int64_t)dest.target();
  movptr(r, imm64);
}

// Move a constant pointer into r.  In AArch64 mode the virtual
// address space is 48 bits in size, so we only need three
// instructions to create a patchable instruction sequence that can
// reach anywhere.
void MacroAssembler::movptr(Register r, uintptr_t imm64) {
#ifndef PRODUCT
  {
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "0x%" PRIX64, imm64);
    block_comment(buffer);
  }
#endif
  assert(imm64 < (1ul << 48), "48-bit overflow in address constant");
  movz(r, imm64 & 0xffff);
  imm64 >>= 16;
  movk(r, imm64 & 0xffff, 16);
  imm64 >>= 16;
  movk(r, imm64 & 0xffff, 32);
}

// Macro to mov replicated immediate to vector register.
//  Vd will get the following values for different arrangements in T
//   imm32 == hex 000000gh  T8B:  Vd = ghghghghghghghgh
//   imm32 == hex 000000gh  T16B: Vd = ghghghghghghghghghghghghghghghgh
//   imm32 == hex 0000efgh  T4H:  Vd = efghefghefghefgh
//   imm32 == hex 0000efgh  T8H:  Vd = efghefghefghefghefghefghefghefgh
//   imm32 == hex abcdefgh  T2S:  Vd = abcdefghabcdefgh
//   imm32 == hex abcdefgh  T4S:  Vd = abcdefghabcdefghabcdefghabcdefgh
//   T1D/T2D: invalid
void MacroAssembler::mov(FloatRegister Vd, SIMD_Arrangement T, u_int32_t imm32) {
  assert(T != T1D && T != T2D, "invalid arrangement");
  if (T == T8B || T == T16B) {
    assert((imm32 & ~0xff) == 0, "extraneous bits in unsigned imm32 (T8B/T16B)");
    movi(Vd, T, imm32 & 0xff, 0);
    return;
  }
  u_int32_t nimm32 = ~imm32;
  if (T == T4H || T == T8H) {
    assert((imm32  & ~0xffff) == 0, "extraneous bits in unsigned imm32 (T4H/T8H)");
    imm32 &= 0xffff;
    nimm32 &= 0xffff;
  }
  u_int32_t x = imm32;
  int movi_cnt = 0;
  int movn_cnt = 0;
  while (x) { if (x & 0xff) movi_cnt++; x >>= 8; }
  x = nimm32;
  while (x) { if (x & 0xff) movn_cnt++; x >>= 8; }
  if (movn_cnt < movi_cnt) imm32 = nimm32;
  unsigned lsl = 0;
  while (imm32 && (imm32 & 0xff) == 0) { lsl += 8; imm32 >>= 8; }
  if (movn_cnt < movi_cnt)
    mvni(Vd, T, imm32 & 0xff, lsl);
  else
    movi(Vd, T, imm32 & 0xff, lsl);
  imm32 >>= 8; lsl += 8;
  while (imm32) {
    while ((imm32 & 0xff) == 0) { lsl += 8; imm32 >>= 8; }
    if (movn_cnt < movi_cnt)
      bici(Vd, T, imm32 & 0xff, lsl);
    else
      orri(Vd, T, imm32 & 0xff, lsl);
    lsl += 8; imm32 >>= 8;
  }
}

void MacroAssembler::mov_immediate64(Register dst, u_int64_t imm64)
{
#ifndef PRODUCT
  {
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "0x%" PRIX64, imm64);
    block_comment(buffer);
  }
#endif
  if (operand_valid_for_logical_immediate(false, imm64)) {
    orr(dst, zr, imm64);
  } else {
    // we can use a combination of MOVZ or MOVN with
    // MOVK to build up the constant
    u_int64_t imm_h[4];
    int zero_count = 0;
    int neg_count = 0;
    int i;
    for (i = 0; i < 4; i++) {
      imm_h[i] = ((imm64 >> (i * 16)) & 0xffffL);
      if (imm_h[i] == 0) {
        zero_count++;
      } else if (imm_h[i] == 0xffffL) {
        neg_count++;
      }
    }
    if (zero_count == 4) {
      // one MOVZ will do
      movz(dst, 0);
    } else if (neg_count == 4) {
      // one MOVN will do
      movn(dst, 0);
    } else if (zero_count == 3) {
      for (i = 0; i < 4; i++) {
        if (imm_h[i] != 0L) {
          movz(dst, (u_int32_t)imm_h[i], (i << 4));
          break;
        }
      }
    } else if (neg_count == 3) {
      // one MOVN will do
      for (int i = 0; i < 4; i++) {
        if (imm_h[i] != 0xffffL) {
          movn(dst, (u_int32_t)imm_h[i] ^ 0xffffL, (i << 4));
          break;
        }
      }
    } else if (zero_count == 2) {
      // one MOVZ and one MOVK will do
      for (i = 0; i < 3; i++) {
        if (imm_h[i] != 0L) {
          movz(dst, (u_int32_t)imm_h[i], (i << 4));
          i++;
          break;
        }
      }
      for (;i < 4; i++) {
        if (imm_h[i] != 0L) {
          movk(dst, (u_int32_t)imm_h[i], (i << 4));
        }
      }
    } else if (neg_count == 2) {
      // one MOVN and one MOVK will do
      for (i = 0; i < 4; i++) {
        if (imm_h[i] != 0xffffL) {
          movn(dst, (u_int32_t)imm_h[i] ^ 0xffffL, (i << 4));
          i++;
          break;
        }
      }
      for (;i < 4; i++) {
        if (imm_h[i] != 0xffffL) {
          movk(dst, (u_int32_t)imm_h[i], (i << 4));
        }
      }
    } else if (zero_count == 1) {
      // one MOVZ and two MOVKs will do
      for (i = 0; i < 4; i++) {
        if (imm_h[i] != 0L) {
          movz(dst, (u_int32_t)imm_h[i], (i << 4));
          i++;
          break;
        }
      }
      for (;i < 4; i++) {
        if (imm_h[i] != 0x0L) {
          movk(dst, (u_int32_t)imm_h[i], (i << 4));
        }
      }
    } else if (neg_count == 1) {
      // one MOVN and two MOVKs will do
      for (i = 0; i < 4; i++) {
        if (imm_h[i] != 0xffffL) {
          movn(dst, (u_int32_t)imm_h[i] ^ 0xffffL, (i << 4));
          i++;
          break;
        }
      }
      for (;i < 4; i++) {
        if (imm_h[i] != 0xffffL) {
          movk(dst, (u_int32_t)imm_h[i], (i << 4));
        }
      }
    } else {
      // use a MOVZ and 3 MOVKs (makes it easier to debug)
      movz(dst, (u_int32_t)imm_h[0], 0);
      for (i = 1; i < 4; i++) {
        movk(dst, (u_int32_t)imm_h[i], (i << 4));
      }
    }
  }
}

void MacroAssembler::mov_immediate32(Register dst, u_int32_t imm32)
{
#ifndef PRODUCT
    {
      char buffer[64];
      snprintf(buffer, sizeof(buffer), "0x%" PRIX32, imm32);
      block_comment(buffer);
    }
#endif
  if (operand_valid_for_logical_immediate(true, imm32)) {
    orrw(dst, zr, imm32);
  } else {
    // we can use MOVZ, MOVN or two calls to MOVK to build up the
    // constant
    u_int32_t imm_h[2];
    imm_h[0] = imm32 & 0xffff;
    imm_h[1] = ((imm32 >> 16) & 0xffff);
    if (imm_h[0] == 0) {
      movzw(dst, imm_h[1], 16);
    } else if (imm_h[0] == 0xffff) {
      movnw(dst, imm_h[1] ^ 0xffff, 16);
    } else if (imm_h[1] == 0) {
      movzw(dst, imm_h[0], 0);
    } else if (imm_h[1] == 0xffff) {
      movnw(dst, imm_h[0] ^ 0xffff, 0);
    } else {
      // use a MOVZ and MOVK (makes it easier to debug)
      movzw(dst, imm_h[0], 0);
      movkw(dst, imm_h[1], 16);
    }
  }
}

// Form an address from base + offset in Rd.  Rd may or may
// not actually be used: you must use the Address that is returned.
// It is up to you to ensure that the shift provided matches the size
// of your data.
Address MacroAssembler::form_address(Register Rd, Register base, long byte_offset, int shift) {
  if (Address::offset_ok_for_immed(byte_offset, shift))
    // It fits; no need for any heroics
    return Address(base, byte_offset);

  // Don't do anything clever with negative or misaligned offsets
  unsigned mask = (1 << shift) - 1;
  if (byte_offset < 0 || byte_offset & mask) {
    mov(Rd, byte_offset);
    add(Rd, base, Rd);
    return Address(Rd);
  }

  // See if we can do this with two 12-bit offsets
  {
    unsigned long word_offset = byte_offset >> shift;
    unsigned long masked_offset = word_offset & 0xfff000;
    if (Address::offset_ok_for_immed(word_offset - masked_offset)
        && Assembler::operand_valid_for_add_sub_immediate(masked_offset << shift)) {
      add(Rd, base, masked_offset << shift);
      word_offset -= masked_offset;
      return Address(Rd, word_offset << shift);
    }
  }

  // Do it the hard way
  mov(Rd, byte_offset);
  add(Rd, base, Rd);
  return Address(Rd);
}

void MacroAssembler::atomic_incw(Register counter_addr, Register tmp, Register tmp2) {
  if (UseLSE) {
    mov(tmp, 1);
    ldadd(Assembler::word, tmp, zr, counter_addr);
    return;
  }
  Label retry_load;
  if ((VM_Version::features() & VM_Version::CPU_STXR_PREFETCH))
    prfm(Address(counter_addr), PSTL1STRM);
  bind(retry_load);
  // flush and load exclusive from the memory location
  ldxrw(tmp, counter_addr);
  addw(tmp, tmp, 1);
  // if we store+flush with no intervening write tmp wil be zero
  stxrw(tmp2, tmp, counter_addr);
  cbnzw(tmp2, retry_load);
}


int MacroAssembler::corrected_idivl(Register result, Register ra, Register rb,
                                    bool want_remainder, Register scratch)
{
  // Full implementation of Java idiv and irem.  The function
  // returns the (pc) offset of the div instruction - may be needed
  // for implicit exceptions.
  //
  // constraint : ra/rb =/= scratch
  //         normal case
  //
  // input : ra: dividend
  //         rb: divisor
  //
  // result: either
  //         quotient  (= ra idiv rb)
  //         remainder (= ra irem rb)

  assert(ra != scratch && rb != scratch, "reg cannot be scratch");

  int idivl_offset = offset();
  if (! want_remainder) {
    sdivw(result, ra, rb);
  } else {
    sdivw(scratch, ra, rb);
    Assembler::msubw(result, scratch, rb, ra);
  }

  return idivl_offset;
}

int MacroAssembler::corrected_idivq(Register result, Register ra, Register rb,
                                    bool want_remainder, Register scratch)
{
  // Full implementation of Java ldiv and lrem.  The function
  // returns the (pc) offset of the div instruction - may be needed
  // for implicit exceptions.
  //
  // constraint : ra/rb =/= scratch
  //         normal case
  //
  // input : ra: dividend
  //         rb: divisor
  //
  // result: either
  //         quotient  (= ra idiv rb)
  //         remainder (= ra irem rb)

  assert(ra != scratch && rb != scratch, "reg cannot be scratch");

  int idivq_offset = offset();
  if (! want_remainder) {
    sdiv(result, ra, rb);
  } else {
    sdiv(scratch, ra, rb);
    Assembler::msub(result, scratch, rb, ra);
  }

  return idivq_offset;
}

void MacroAssembler::membar(Membar_mask_bits order_constraint) {
  address prev = pc() - NativeMembar::instruction_size;
  address last = code()->last_insn();
  if (last != NULL && nativeInstruction_at(last)->is_Membar() && prev == last) {
    NativeMembar *bar = NativeMembar_at(prev);
    // We are merging two memory barrier instructions.  On AArch64 we
    // can do this simply by ORing them together.
    bar->set_kind(bar->get_kind() | order_constraint);
    BLOCK_COMMENT("merged membar");
  } else {
    code()->set_last_insn(pc());
    dmb(Assembler::barrier(order_constraint));
  }
}

bool MacroAssembler::try_merge_ldst(Register rt, const Address &adr, size_t size_in_bytes, bool is_store) {
  if (ldst_can_merge(rt, adr, size_in_bytes, is_store)) {
    merge_ldst(rt, adr, size_in_bytes, is_store);
    code()->clear_last_insn();
    return true;
  } else {
    assert(size_in_bytes == 8 || size_in_bytes == 4, "only 8 bytes or 4 bytes load/store is supported.");
    const unsigned mask = size_in_bytes - 1;
    if (adr.getMode() == Address::base_plus_offset &&
        (adr.offset() & mask) == 0) { // only supports base_plus_offset.
      code()->set_last_insn(pc());
    }
    return false;
  }
}

void MacroAssembler::ldr(Register Rx, const Address &adr) {
  // We always try to merge two adjacent loads into one ldp.
  if (!try_merge_ldst(Rx, adr, 8, false)) {
    Assembler::ldr(Rx, adr);
  }
}

void MacroAssembler::ldrw(Register Rw, const Address &adr) {
  // We always try to merge two adjacent loads into one ldp.
  if (!try_merge_ldst(Rw, adr, 4, false)) {
    Assembler::ldrw(Rw, adr);
  }
}

void MacroAssembler::str(Register Rx, const Address &adr) {
  // We always try to merge two adjacent stores into one stp.
  if (!try_merge_ldst(Rx, adr, 8, true)) {
    Assembler::str(Rx, adr);
  }
}

void MacroAssembler::strw(Register Rw, const Address &adr) {
  // We always try to merge two adjacent stores into one stp.
  if (!try_merge_ldst(Rw, adr, 4, true)) {
    Assembler::strw(Rw, adr);
  }
}

// MacroAssembler routines found actually to be needed

void MacroAssembler::push(Register src)
{
  str(src, Address(pre(esp, -1 * wordSize)));
}

void MacroAssembler::pop(Register dst)
{
  ldr(dst, Address(post(esp, 1 * wordSize)));
}

// Note: load_unsigned_short used to be called load_unsigned_word.
int MacroAssembler::load_unsigned_short(Register dst, Address src) {
  int off = offset();
  ldrh(dst, src);
  return off;
}

int MacroAssembler::load_unsigned_byte(Register dst, Address src) {
  int off = offset();
  ldrb(dst, src);
  return off;
}

int MacroAssembler::load_signed_short(Register dst, Address src) {
  int off = offset();
  ldrsh(dst, src);
  return off;
}

int MacroAssembler::load_signed_byte(Register dst, Address src) {
  int off = offset();
  ldrsb(dst, src);
  return off;
}

int MacroAssembler::load_signed_short32(Register dst, Address src) {
  int off = offset();
  ldrshw(dst, src);
  return off;
}

int MacroAssembler::load_signed_byte32(Register dst, Address src) {
  int off = offset();
  ldrsbw(dst, src);
  return off;
}

void MacroAssembler::load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed, Register dst2) {
  switch (size_in_bytes) {
  case  8:  ldr(dst, src); break;
  case  4:  ldrw(dst, src); break;
  case  2:  is_signed ? load_signed_short(dst, src) : load_unsigned_short(dst, src); break;
  case  1:  is_signed ? load_signed_byte( dst, src) : load_unsigned_byte( dst, src); break;
  default:  ShouldNotReachHere();
  }
}

void MacroAssembler::store_sized_value(Address dst, Register src, size_t size_in_bytes, Register src2) {
  switch (size_in_bytes) {
  case  8:  str(src, dst); break;
  case  4:  strw(src, dst); break;
  case  2:  strh(src, dst); break;
  case  1:  strb(src, dst); break;
  default:  ShouldNotReachHere();
  }
}

void MacroAssembler::decrementw(Register reg, int value)
{
  if (value < 0)  { incrementw(reg, -value);      return; }
  if (value == 0) {                               return; }
  if (value < (1 << 12)) { subw(reg, reg, value); return; }
  /* else */ {
    guarantee(reg != rscratch2, "invalid dst for register decrement");
    movw(rscratch2, (unsigned)value);
    subw(reg, reg, rscratch2);
  }
}

void MacroAssembler::decrement(Register reg, int value)
{
  if (value < 0)  { increment(reg, -value);      return; }
  if (value == 0) {                              return; }
  if (value < (1 << 12)) { sub(reg, reg, value); return; }
  /* else */ {
    assert(reg != rscratch2, "invalid dst for register decrement");
    mov(rscratch2, (unsigned long)value);
    sub(reg, reg, rscratch2);
  }
}

void MacroAssembler::decrementw(Address dst, int value)
{
  assert(!dst.uses(rscratch1), "invalid dst for address decrement");
  if (dst.getMode() == Address::literal) {
    assert(abs(value) < (1 << 12), "invalid value and address mode combination");
    lea(rscratch2, dst);
    dst = Address(rscratch2);
  }
  ldrw(rscratch1, dst);
  decrementw(rscratch1, value);
  strw(rscratch1, dst);
}

void MacroAssembler::decrement(Address dst, int value)
{
  assert(!dst.uses(rscratch1), "invalid address for decrement");
  if (dst.getMode() == Address::literal) {
    assert(abs(value) < (1 << 12), "invalid value and address mode combination");
    lea(rscratch2, dst);
    dst = Address(rscratch2);
  }
  ldr(rscratch1, dst);
  decrement(rscratch1, value);
  str(rscratch1, dst);
}

void MacroAssembler::incrementw(Register reg, int value)
{
  if (value < 0)  { decrementw(reg, -value);      return; }
  if (value == 0) {                               return; }
  if (value < (1 << 12)) { addw(reg, reg, value); return; }
  /* else */ {
    assert(reg != rscratch2, "invalid dst for register increment");
    movw(rscratch2, (unsigned)value);
    addw(reg, reg, rscratch2);
  }
}

void MacroAssembler::increment(Register reg, int value)
{
  if (value < 0)  { decrement(reg, -value);      return; }
  if (value == 0) {                              return; }
  if (value < (1 << 12)) { add(reg, reg, value); return; }
  /* else */ {
    assert(reg != rscratch2, "invalid dst for register increment");
    movw(rscratch2, (unsigned)value);
    add(reg, reg, rscratch2);
  }
}

void MacroAssembler::incrementw(Address dst, int value)
{
  assert(!dst.uses(rscratch1), "invalid dst for address increment");
  if (dst.getMode() == Address::literal) {
    assert(abs(value) < (1 << 12), "invalid value and address mode combination");
    lea(rscratch2, dst);
    dst = Address(rscratch2);
  }
  ldrw(rscratch1, dst);
  incrementw(rscratch1, value);
  strw(rscratch1, dst);
}

void MacroAssembler::increment(Address dst, int value)
{
  assert(!dst.uses(rscratch1), "invalid dst for address increment");
  if (dst.getMode() == Address::literal) {
    assert(abs(value) < (1 << 12), "invalid value and address mode combination");
    lea(rscratch2, dst);
    dst = Address(rscratch2);
  }
  ldr(rscratch1, dst);
  increment(rscratch1, value);
  str(rscratch1, dst);
}


void MacroAssembler::pusha() {
  push(0x7fffffff, sp);
}

void MacroAssembler::popa() {
  pop(0x7fffffff, sp);
}

// Push lots of registers in the bit set supplied.  Don't push sp.
// Return the number of words pushed
int MacroAssembler::push(unsigned int bitset, Register stack) {
  int words_pushed = 0;

  // Scan bitset to accumulate register pairs
  unsigned char regs[32];
  int count = 0;
  for (int reg = 0; reg <= 30; reg++) {
    if (1 & bitset)
      regs[count++] = reg;
    bitset >>= 1;
  }
  regs[count++] = zr->encoding_nocheck();
  count &= ~1;  // Only push an even nuber of regs

  if (count) {
    stp(as_Register(regs[0]), as_Register(regs[1]),
       Address(pre(stack, -count * wordSize)));
    words_pushed += 2;
  }
  for (int i = 2; i < count; i += 2) {
    stp(as_Register(regs[i]), as_Register(regs[i+1]),
       Address(stack, i * wordSize));
    words_pushed += 2;
  }

  assert(words_pushed == count, "oops, pushed != count");

  return count;
}

int MacroAssembler::pop(unsigned int bitset, Register stack) {
  int words_pushed = 0;

  // Scan bitset to accumulate register pairs
  unsigned char regs[32];
  int count = 0;
  for (int reg = 0; reg <= 30; reg++) {
    if (1 & bitset)
      regs[count++] = reg;
    bitset >>= 1;
  }
  regs[count++] = zr->encoding_nocheck();
  count &= ~1;

  for (int i = 2; i < count; i += 2) {
    ldp(as_Register(regs[i]), as_Register(regs[i+1]),
       Address(stack, i * wordSize));
    words_pushed += 2;
  }
  if (count) {
    ldp(as_Register(regs[0]), as_Register(regs[1]),
       Address(post(stack, count * wordSize)));
    words_pushed += 2;
  }

  assert(words_pushed == count, "oops, pushed != count");

  return count;
}
#ifdef ASSERT
void MacroAssembler::verify_heapbase(const char* msg) {
#if 0
  assert (UseCompressedOops || UseCompressedClassPointers, "should be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  if (CheckCompressedOops) {
    Label ok;
    push(1 << rscratch1->encoding(), sp); // cmpptr trashes rscratch1
    cmpptr(rheapbase, ExternalAddress((address)Universe::narrow_ptrs_base_addr()));
    br(Assembler::EQ, ok);
    stop(msg);
    bind(ok);
    pop(1 << rscratch1->encoding(), sp);
  }
#endif
}
#endif

void MacroAssembler::resolve_jobject(Register value, Register thread, Register tmp) {
  Label done, not_weak;
  cbz(value, done);           // Use NULL as-is.

  STATIC_ASSERT(JNIHandles::weak_tag_mask == 1u);
  tbz(r0, 0, not_weak);    // Test for jweak tag.

  // Resolve jweak.
  access_load_at(T_OBJECT, IN_NATIVE | ON_PHANTOM_OOP_REF, value,
                 Address(value, -JNIHandles::weak_tag_value), tmp, thread);
  verify_oop(value);
  b(done);

  bind(not_weak);
  // Resolve (untagged) jobject.
  access_load_at(T_OBJECT, IN_NATIVE, value, Address(value, 0), tmp, thread);
  verify_oop(value);
  bind(done);
}

void MacroAssembler::stop(const char* msg) {
  address ip = pc();
  pusha();
  mov(c_rarg0, (address)msg);
  mov(c_rarg1, (address)ip);
  mov(c_rarg2, sp);
  mov(c_rarg3, CAST_FROM_FN_PTR(address, MacroAssembler::debug64));
  // call(c_rarg3);
  blrt(c_rarg3, 3, 0, 1);
  hlt(0);
}

void MacroAssembler::warn(const char* msg) {
  pusha();
  mov(c_rarg0, (address)msg);
  mov(lr, CAST_FROM_FN_PTR(address, warning));
  blrt(lr, 1, 0, MacroAssembler::ret_type_void);
  popa();
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

// If a constant does not fit in an immediate field, generate some
// number of MOV instructions and then perform the operation.
void MacroAssembler::wrap_add_sub_imm_insn(Register Rd, Register Rn, unsigned imm,
                                           add_sub_imm_insn insn1,
                                           add_sub_reg_insn insn2) {
  assert(Rd != zr, "Rd = zr and not setting flags?");
  if (operand_valid_for_add_sub_immediate((int)imm)) {
    (this->*insn1)(Rd, Rn, imm);
  } else {
    if (uabs(imm) < (1 << 24)) {
       (this->*insn1)(Rd, Rn, imm & -(1 << 12));
       (this->*insn1)(Rd, Rd, imm & ((1 << 12)-1));
    } else {
       assert_different_registers(Rd, Rn);
       mov(Rd, (uint64_t)imm);
       (this->*insn2)(Rd, Rn, Rd, LSL, 0);
    }
  }
}

// Seperate vsn which sets the flags. Optimisations are more restricted
// because we must set the flags correctly.
void MacroAssembler::wrap_adds_subs_imm_insn(Register Rd, Register Rn, unsigned imm,
                                           add_sub_imm_insn insn1,
                                           add_sub_reg_insn insn2) {
  if (operand_valid_for_add_sub_immediate((int)imm)) {
    (this->*insn1)(Rd, Rn, imm);
  } else {
    assert_different_registers(Rd, Rn);
    assert(Rd != zr, "overflow in immediate operand");
    mov(Rd, (uint64_t)imm);
    (this->*insn2)(Rd, Rn, Rd, LSL, 0);
  }
}


void MacroAssembler::add(Register Rd, Register Rn, RegisterOrConstant increment) {
  if (increment.is_register()) {
    add(Rd, Rn, increment.as_register());
  } else {
    add(Rd, Rn, increment.as_constant());
  }
}

void MacroAssembler::addw(Register Rd, Register Rn, RegisterOrConstant increment) {
  if (increment.is_register()) {
    addw(Rd, Rn, increment.as_register());
  } else {
    addw(Rd, Rn, increment.as_constant());
  }
}

void MacroAssembler::sub(Register Rd, Register Rn, RegisterOrConstant decrement) {
  if (decrement.is_register()) {
    sub(Rd, Rn, decrement.as_register());
  } else {
    sub(Rd, Rn, decrement.as_constant());
  }
}

void MacroAssembler::subw(Register Rd, Register Rn, RegisterOrConstant decrement) {
  if (decrement.is_register()) {
    subw(Rd, Rn, decrement.as_register());
  } else {
    subw(Rd, Rn, decrement.as_constant());
  }
}

void MacroAssembler::reinit_heapbase()
{
  if (UseCompressedOops) {
    if (Universe::is_fully_initialized()) {
      mov(rheapbase, Universe::narrow_ptrs_base());
    } else {
      lea(rheapbase, ExternalAddress((address)Universe::narrow_ptrs_base_addr()));
      ldr(rheapbase, Address(rheapbase));
    }
  }
}

// this simulates the behaviour of the x86 cmpxchg instruction using a
// load linked/store conditional pair. we use the acquire/release
// versions of these instructions so that we flush pending writes as
// per Java semantics.

// n.b the x86 version assumes the old value to be compared against is
// in rax and updates rax with the value located in memory if the
// cmpxchg fails. we supply a register for the old value explicitly

// the aarch64 load linked/store conditional instructions do not
// accept an offset. so, unlike x86, we must provide a plain register
// to identify the memory word to be compared/exchanged rather than a
// register+offset Address.

void MacroAssembler::cmpxchgptr(Register oldv, Register newv, Register addr, Register tmp,
                                Label &succeed, Label *fail) {
  // oldv holds comparison value
  // newv holds value to write in exchange
  // addr identifies memory word to compare against/update
  if (UseLSE) {
    mov(tmp, oldv);
    casal(Assembler::xword, oldv, newv, addr);
    cmp(tmp, oldv);
    br(Assembler::EQ, succeed);
    membar(AnyAny);
  } else {
    Label retry_load, nope;
    if ((VM_Version::features() & VM_Version::CPU_STXR_PREFETCH))
      prfm(Address(addr), PSTL1STRM);
    bind(retry_load);
    // flush and load exclusive from the memory location
    // and fail if it is not what we expect
    ldaxr(tmp, addr);
    cmp(tmp, oldv);
    br(Assembler::NE, nope);
    // if we store+flush with no intervening write tmp wil be zero
    stlxr(tmp, newv, addr);
    cbzw(tmp, succeed);
    // retry so we only ever return after a load fails to compare
    // ensures we don't return a stale value after a failed write.
    b(retry_load);
    // if the memory word differs we return it in oldv and signal a fail
    bind(nope);
    membar(AnyAny);
    mov(oldv, tmp);
  }
  if (fail)
    b(*fail);
}

void MacroAssembler::cmpxchg_obj_header(Register oldv, Register newv, Register obj, Register tmp,
                                        Label &succeed, Label *fail) {
  assert(oopDesc::mark_offset_in_bytes() == 0, "assumption");
  cmpxchgptr(oldv, newv, obj, tmp, succeed, fail);
}

void MacroAssembler::cmpxchgw(Register oldv, Register newv, Register addr, Register tmp,
                                Label &succeed, Label *fail) {
  // oldv holds comparison value
  // newv holds value to write in exchange
  // addr identifies memory word to compare against/update
  // tmp returns 0/1 for success/failure
  if (UseLSE) {
    mov(tmp, oldv);
    casal(Assembler::word, oldv, newv, addr);
    cmp(tmp, oldv);
    br(Assembler::EQ, succeed);
    membar(AnyAny);
  } else {
    Label retry_load, nope;
    if ((VM_Version::features() & VM_Version::CPU_STXR_PREFETCH))
      prfm(Address(addr), PSTL1STRM);
    bind(retry_load);
    // flush and load exclusive from the memory location
    // and fail if it is not what we expect
    ldaxrw(tmp, addr);
    cmp(tmp, oldv);
    br(Assembler::NE, nope);
    // if we store+flush with no intervening write tmp wil be zero
    stlxrw(tmp, newv, addr);
    cbzw(tmp, succeed);
    // retry so we only ever return after a load fails to compare
    // ensures we don't return a stale value after a failed write.
    b(retry_load);
    // if the memory word differs we return it in oldv and signal a fail
    bind(nope);
    membar(AnyAny);
    mov(oldv, tmp);
  }
  if (fail)
    b(*fail);
}

// A generic CAS; success or failure is in the EQ flag.  A weak CAS
// doesn't retry and may fail spuriously.  If the oldval is wanted,
// Pass a register for the result, otherwise pass noreg.

// Clobbers rscratch1
void MacroAssembler::cmpxchg(Register addr, Register expected,
                             Register new_val,
                             enum operand_size size,
                             bool acquire, bool release,
                             bool weak,
                             Register result) {
  if (result == noreg)  result = rscratch1;
  BLOCK_COMMENT("cmpxchg {");
  if (UseLSE) {
    mov(result, expected);
    lse_cas(result, new_val, addr, size, acquire, release, /*not_pair*/ true);
    compare_eq(result, expected, size);
  } else {
    Label retry_load, done;
    if ((VM_Version::features() & VM_Version::CPU_STXR_PREFETCH))
      prfm(Address(addr), PSTL1STRM);
    bind(retry_load);
    load_exclusive(result, addr, size, acquire);
    compare_eq(result, expected, size);
    br(Assembler::NE, done);
    store_exclusive(rscratch1, new_val, addr, size, release);
    if (weak) {
      cmpw(rscratch1, 0u);  // If the store fails, return NE to our caller.
    } else {
      cbnzw(rscratch1, retry_load);
    }
    bind(done);
  }
  BLOCK_COMMENT("} cmpxchg");
}

// A generic comparison. Only compares for equality, clobbers rscratch1.
void MacroAssembler::compare_eq(Register rm, Register rn, enum operand_size size) {
  if (size == xword) {
    cmp(rm, rn);
  } else if (size == word) {
    cmpw(rm, rn);
  } else if (size == halfword) {
    eorw(rscratch1, rm, rn);
    ands(zr, rscratch1, 0xffff);
  } else if (size == byte) {
    eorw(rscratch1, rm, rn);
    ands(zr, rscratch1, 0xff);
  } else {
    ShouldNotReachHere();
  }
}


static bool different(Register a, RegisterOrConstant b, Register c) {
  if (b.is_constant())
    return a != c;
  else
    return a != b.as_register() && a != c && b.as_register() != c;
}

#define ATOMIC_OP(NAME, LDXR, OP, IOP, AOP, STXR, sz)                   \
void MacroAssembler::atomic_##NAME(Register prev, RegisterOrConstant incr, Register addr) { \
  if (UseLSE) {                                                         \
    prev = prev->is_valid() ? prev : zr;                                \
    if (incr.is_register()) {                                           \
      AOP(sz, incr.as_register(), prev, addr);                          \
    } else {                                                            \
      mov(rscratch2, incr.as_constant());                               \
      AOP(sz, rscratch2, prev, addr);                                   \
    }                                                                   \
    return;                                                             \
  }                                                                     \
  Register result = rscratch2;                                          \
  if (prev->is_valid())                                                 \
    result = different(prev, incr, addr) ? prev : rscratch2;            \
                                                                        \
  Label retry_load;                                                     \
  if ((VM_Version::features() & VM_Version::CPU_STXR_PREFETCH))         \
    prfm(Address(addr), PSTL1STRM);                                     \
  bind(retry_load);                                                     \
  LDXR(result, addr);                                                   \
  OP(rscratch1, result, incr);                                          \
  STXR(rscratch2, rscratch1, addr);                                     \
  cbnzw(rscratch2, retry_load);                                         \
  if (prev->is_valid() && prev != result) {                             \
    IOP(prev, rscratch1, incr);                                         \
  }                                                                     \
}

ATOMIC_OP(add, ldxr, add, sub, ldadd, stxr, Assembler::xword)
ATOMIC_OP(addw, ldxrw, addw, subw, ldadd, stxrw, Assembler::word)
ATOMIC_OP(addal, ldaxr, add, sub, ldaddal, stlxr, Assembler::xword)
ATOMIC_OP(addalw, ldaxrw, addw, subw, ldaddal, stlxrw, Assembler::word)

#undef ATOMIC_OP

#define ATOMIC_XCHG(OP, AOP, LDXR, STXR, sz)                            \
void MacroAssembler::atomic_##OP(Register prev, Register newv, Register addr) { \
  if (UseLSE) {                                                         \
    prev = prev->is_valid() ? prev : zr;                                \
    AOP(sz, newv, prev, addr);                                          \
    return;                                                             \
  }                                                                     \
  Register result = rscratch2;                                          \
  if (prev->is_valid())                                                 \
    result = different(prev, newv, addr) ? prev : rscratch2;            \
                                                                        \
  Label retry_load;                                                     \
  if ((VM_Version::features() & VM_Version::CPU_STXR_PREFETCH))         \
    prfm(Address(addr), PSTL1STRM);                                     \
  bind(retry_load);                                                     \
  LDXR(result, addr);                                                   \
  STXR(rscratch1, newv, addr);                                          \
  cbnzw(rscratch1, retry_load);                                         \
  if (prev->is_valid() && prev != result)                               \
    mov(prev, result);                                                  \
}

ATOMIC_XCHG(xchg, swp, ldxr, stxr, Assembler::xword)
ATOMIC_XCHG(xchgw, swp, ldxrw, stxrw, Assembler::word)
ATOMIC_XCHG(xchgal, swpal, ldaxr, stlxr, Assembler::xword)
ATOMIC_XCHG(xchgalw, swpal, ldaxrw, stlxrw, Assembler::word)

#undef ATOMIC_XCHG

#ifndef PRODUCT
extern "C" void findpc(intptr_t x);
#endif

void MacroAssembler::debug64(char* msg, int64_t pc, int64_t regs[])
{
  // In order to get locks to work, we need to fake a in_VM state
  if (ShowMessageBoxOnError ) {
    JavaThread* thread = JavaThread::current();
    JavaThreadState saved_state = thread->thread_state();
    thread->set_thread_state(_thread_in_vm);
#ifndef PRODUCT
    if (CountBytecodes || TraceBytecodes || StopInterpreterAt) {
      ttyLocker ttyl;
      BytecodeCounter::print();
    }
#endif
    if (os::message_box(msg, "Execution stopped, print registers?")) {
      ttyLocker ttyl;
      tty->print_cr(" pc = 0x%016lx", pc);
#ifndef PRODUCT
      tty->cr();
      findpc(pc);
      tty->cr();
#endif
      tty->print_cr(" r0 = 0x%016lx", regs[0]);
      tty->print_cr(" r1 = 0x%016lx", regs[1]);
      tty->print_cr(" r2 = 0x%016lx", regs[2]);
      tty->print_cr(" r3 = 0x%016lx", regs[3]);
      tty->print_cr(" r4 = 0x%016lx", regs[4]);
      tty->print_cr(" r5 = 0x%016lx", regs[5]);
      tty->print_cr(" r6 = 0x%016lx", regs[6]);
      tty->print_cr(" r7 = 0x%016lx", regs[7]);
      tty->print_cr(" r8 = 0x%016lx", regs[8]);
      tty->print_cr(" r9 = 0x%016lx", regs[9]);
      tty->print_cr("r10 = 0x%016lx", regs[10]);
      tty->print_cr("r11 = 0x%016lx", regs[11]);
      tty->print_cr("r12 = 0x%016lx", regs[12]);
      tty->print_cr("r13 = 0x%016lx", regs[13]);
      tty->print_cr("r14 = 0x%016lx", regs[14]);
      tty->print_cr("r15 = 0x%016lx", regs[15]);
      tty->print_cr("r16 = 0x%016lx", regs[16]);
      tty->print_cr("r17 = 0x%016lx", regs[17]);
      tty->print_cr("r18 = 0x%016lx", regs[18]);
      tty->print_cr("r19 = 0x%016lx", regs[19]);
      tty->print_cr("r20 = 0x%016lx", regs[20]);
      tty->print_cr("r21 = 0x%016lx", regs[21]);
      tty->print_cr("r22 = 0x%016lx", regs[22]);
      tty->print_cr("r23 = 0x%016lx", regs[23]);
      tty->print_cr("r24 = 0x%016lx", regs[24]);
      tty->print_cr("r25 = 0x%016lx", regs[25]);
      tty->print_cr("r26 = 0x%016lx", regs[26]);
      tty->print_cr("r27 = 0x%016lx", regs[27]);
      tty->print_cr("r28 = 0x%016lx", regs[28]);
      tty->print_cr("r30 = 0x%016lx", regs[30]);
      tty->print_cr("r31 = 0x%016lx", regs[31]);
      BREAKPOINT;
    }
    ThreadStateTransition::transition(thread, _thread_in_vm, saved_state);
  } else {
    ttyLocker ttyl;
    ::tty->print_cr("=============== DEBUG MESSAGE: %s ================\n",
                    msg);
    assert(false, "DEBUG MESSAGE: %s", msg);
  }
}

#ifdef BUILTIN_SIM
// routine to generate an x86 prolog for a stub function which
// bootstraps into the generated ARM code which directly follows the
// stub
//
// the argument encodes the number of general and fp registers
// passed by the caller and the callng convention (currently just
// the number of general registers and assumes C argument passing)

extern "C" {
int aarch64_stub_prolog_size();
void aarch64_stub_prolog();
void aarch64_prolog();
}

void MacroAssembler::c_stub_prolog(int gp_arg_count, int fp_arg_count, int ret_type,
                                   address *prolog_ptr)
{
  int calltype = (((ret_type & 0x3) << 8) |
                  ((fp_arg_count & 0xf) << 4) |
                  (gp_arg_count & 0xf));

  // the addresses for the x86 to ARM entry code we need to use
  address start = pc();
  // printf("start = %lx\n", start);
  int byteCount =  aarch64_stub_prolog_size();
  // printf("byteCount = %x\n", byteCount);
  int instructionCount = (byteCount + 3)/ 4;
  // printf("instructionCount = %x\n", instructionCount);
  for (int i = 0; i < instructionCount; i++) {
    nop();
  }

  memcpy(start, (void*)aarch64_stub_prolog, byteCount);

  // write the address of the setup routine and the call format at the
  // end of into the copied code
  u_int64_t *patch_end = (u_int64_t *)(start + byteCount);
  if (prolog_ptr)
    patch_end[-2] = (u_int64_t)prolog_ptr;
  patch_end[-1] = calltype;
}
#endif

void MacroAssembler::push_call_clobbered_registers() {
  int step = 4 * wordSize;
  push(RegSet::range(r0, r18) - RegSet::of(rscratch1, rscratch2), sp);
  sub(sp, sp, step);
  mov(rscratch1, -step);
  // Push v0-v7, v16-v31.
  for (int i = 31; i>= 4; i -= 4) {
    if (i <= v7->encoding() || i >= v16->encoding())
      st1(as_FloatRegister(i-3), as_FloatRegister(i-2), as_FloatRegister(i-1),
          as_FloatRegister(i), T1D, Address(post(sp, rscratch1)));
  }
  st1(as_FloatRegister(0), as_FloatRegister(1), as_FloatRegister(2),
      as_FloatRegister(3), T1D, Address(sp));
}

void MacroAssembler::pop_call_clobbered_registers() {
  for (int i = 0; i < 32; i += 4) {
    if (i <= v7->encoding() || i >= v16->encoding())
      ld1(as_FloatRegister(i), as_FloatRegister(i+1), as_FloatRegister(i+2),
          as_FloatRegister(i+3), T1D, Address(post(sp, 4 * wordSize)));
  }

  pop(RegSet::range(r0, r18) - RegSet::of(rscratch1, rscratch2), sp);
}

void MacroAssembler::push_CPU_state(bool save_vectors) {
  int step = (save_vectors ? 8 : 4) * wordSize;
  push(0x3fffffff, sp);         // integer registers except lr & sp
  mov(rscratch1, -step);
  sub(sp, sp, step);
  for (int i = 28; i >= 4; i -= 4) {
    st1(as_FloatRegister(i), as_FloatRegister(i+1), as_FloatRegister(i+2),
        as_FloatRegister(i+3), save_vectors ? T2D : T1D, Address(post(sp, rscratch1)));
  }
  st1(v0, v1, v2, v3, save_vectors ? T2D : T1D, sp);
}

void MacroAssembler::pop_CPU_state(bool restore_vectors) {
  int step = (restore_vectors ? 8 : 4) * wordSize;
  for (int i = 0; i <= 28; i += 4)
    ld1(as_FloatRegister(i), as_FloatRegister(i+1), as_FloatRegister(i+2),
        as_FloatRegister(i+3), restore_vectors ? T2D : T1D, Address(post(sp, step)));
  pop(0x3fffffff, sp);         // integer registers except lr & sp
}

/**
 * Helpers for multiply_to_len().
 */
void MacroAssembler::add2_with_carry(Register final_dest_hi, Register dest_hi, Register dest_lo,
                                     Register src1, Register src2) {
  adds(dest_lo, dest_lo, src1);
  adc(dest_hi, dest_hi, zr);
  adds(dest_lo, dest_lo, src2);
  adc(final_dest_hi, dest_hi, zr);
}

// Generate an address from (r + r1 extend offset).  "size" is the
// size of the operand.  The result may be in rscratch2.
Address MacroAssembler::offsetted_address(Register r, Register r1,
                                          Address::extend ext, int offset, int size) {
  if (offset || (ext.shift() % size != 0)) {
    lea(rscratch2, Address(r, r1, ext));
    return Address(rscratch2, offset);
  } else {
    return Address(r, r1, ext);
  }
}

Address MacroAssembler::spill_address(int size, int offset, Register tmp)
{
  assert(offset >= 0, "spill to negative address?");
  // Offset reachable ?
  //   Not aligned - 9 bits signed offset
  //   Aligned - 12 bits unsigned offset shifted
  Register base = sp;
  if ((offset & (size-1)) && offset >= (1<<8)) {
    add(tmp, base, offset & ((1<<12)-1));
    base = tmp;
    offset &= -1<<12;
  }

  if (offset >= (1<<12) * size) {
    add(tmp, base, offset & (((1<<12)-1)<<12));
    base = tmp;
    offset &= ~(((1<<12)-1)<<12);
  }

  return Address(base, offset);
}

// Checks whether offset is aligned.
// Returns true if it is, else false.
bool MacroAssembler::merge_alignment_check(Register base,
                                           size_t size,
                                           long cur_offset,
                                           long prev_offset) const {
  if (AvoidUnalignedAccesses) {
    if (base == sp) {
      // Checks whether low offset if aligned to pair of registers.
      long pair_mask = size * 2 - 1;
      long offset = prev_offset > cur_offset ? cur_offset : prev_offset;
      return (offset & pair_mask) == 0;
    } else { // If base is not sp, we can't guarantee the access is aligned.
      return false;
    }
  } else {
    long mask = size - 1;
    // Load/store pair instruction only supports element size aligned offset.
    return (cur_offset & mask) == 0 && (prev_offset & mask) == 0;
  }
}

// Checks whether current and previous loads/stores can be merged.
// Returns true if it can be merged, else false.
bool MacroAssembler::ldst_can_merge(Register rt,
                                    const Address &adr,
                                    size_t cur_size_in_bytes,
                                    bool is_store) const {
  address prev = pc() - NativeInstruction::instruction_size;
  address last = code()->last_insn();

  if (last == NULL || !nativeInstruction_at(last)->is_Imm_LdSt()) {
    return false;
  }

  if (adr.getMode() != Address::base_plus_offset || prev != last) {
    return false;
  }

  NativeLdSt* prev_ldst = NativeLdSt_at(prev);
  size_t prev_size_in_bytes = prev_ldst->size_in_bytes();

  assert(prev_size_in_bytes == 4 || prev_size_in_bytes == 8, "only supports 64/32bit merging.");
  assert(cur_size_in_bytes == 4 || cur_size_in_bytes == 8, "only supports 64/32bit merging.");

  if (cur_size_in_bytes != prev_size_in_bytes || is_store != prev_ldst->is_store()) {
    return false;
  }

  long max_offset = 63 * prev_size_in_bytes;
  long min_offset = -64 * prev_size_in_bytes;

  assert(prev_ldst->is_not_pre_post_index(), "pre-index or post-index is not supported to be merged.");

  // Only same base can be merged.
  if (adr.base() != prev_ldst->base()) {
    return false;
  }

  long cur_offset = adr.offset();
  long prev_offset = prev_ldst->offset();
  size_t diff = abs(cur_offset - prev_offset);
  if (diff != prev_size_in_bytes) {
    return false;
  }

  // Following cases can not be merged:
  // ldr x2, [x2, #8]
  // ldr x3, [x2, #16]
  // or:
  // ldr x2, [x3, #8]
  // ldr x2, [x3, #16]
  // If t1 and t2 is the same in "ldp t1, t2, [xn, #imm]", we'll get SIGILL.
  if (!is_store && (adr.base() == prev_ldst->target() || rt == prev_ldst->target())) {
    return false;
  }

  long low_offset = prev_offset > cur_offset ? cur_offset : prev_offset;
  // Offset range must be in ldp/stp instruction's range.
  if (low_offset > max_offset || low_offset < min_offset) {
    return false;
  }

  if (merge_alignment_check(adr.base(), prev_size_in_bytes, cur_offset, prev_offset)) {
    return true;
  }

  return false;
}

// Merge current load/store with previous load/store into ldp/stp.
void MacroAssembler::merge_ldst(Register rt,
                                const Address &adr,
                                size_t cur_size_in_bytes,
                                bool is_store) {

  assert(ldst_can_merge(rt, adr, cur_size_in_bytes, is_store) == true, "cur and prev must be able to be merged.");

  Register rt_low, rt_high;
  address prev = pc() - NativeInstruction::instruction_size;
  NativeLdSt* prev_ldst = NativeLdSt_at(prev);

  long offset;

  if (adr.offset() < prev_ldst->offset()) {
    offset = adr.offset();
    rt_low = rt;
    rt_high = prev_ldst->target();
  } else {
    offset = prev_ldst->offset();
    rt_low = prev_ldst->target();
    rt_high = rt;
  }

  Address adr_p = Address(prev_ldst->base(), offset);
  // Overwrite previous generated binary.
  code_section()->set_end(prev);

  const int sz = prev_ldst->size_in_bytes();
  assert(sz == 8 || sz == 4, "only supports 64/32bit merging.");
  if (!is_store) {
    BLOCK_COMMENT("merged ldr pair");
    if (sz == 8) {
      ldp(rt_low, rt_high, adr_p);
    } else {
      ldpw(rt_low, rt_high, adr_p);
    }
  } else {
    BLOCK_COMMENT("merged str pair");
    if (sz == 8) {
      stp(rt_low, rt_high, adr_p);
    } else {
      stpw(rt_low, rt_high, adr_p);
    }
  }
}

/**
 * Multiply 64 bit by 64 bit first loop.
 */
void MacroAssembler::multiply_64_x_64_loop(Register x, Register xstart, Register x_xstart,
                                           Register y, Register y_idx, Register z,
                                           Register carry, Register product,
                                           Register idx, Register kdx) {
  //
  //  jlong carry, x[], y[], z[];
  //  for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx-, kdx--) {
  //    huge_128 product = y[idx] * x[xstart] + carry;
  //    z[kdx] = (jlong)product;
  //    carry  = (jlong)(product >>> 64);
  //  }
  //  z[xstart] = carry;
  //

  Label L_first_loop, L_first_loop_exit;
  Label L_one_x, L_one_y, L_multiply;

  subsw(xstart, xstart, 1);
  br(Assembler::MI, L_one_x);

  lea(rscratch1, Address(x, xstart, Address::lsl(LogBytesPerInt)));
  ldr(x_xstart, Address(rscratch1));
  ror(x_xstart, x_xstart, 32); // convert big-endian to little-endian

  bind(L_first_loop);
  subsw(idx, idx, 1);
  br(Assembler::MI, L_first_loop_exit);
  subsw(idx, idx, 1);
  br(Assembler::MI, L_one_y);
  lea(rscratch1, Address(y, idx, Address::uxtw(LogBytesPerInt)));
  ldr(y_idx, Address(rscratch1));
  ror(y_idx, y_idx, 32); // convert big-endian to little-endian
  bind(L_multiply);

  // AArch64 has a multiply-accumulate instruction that we can't use
  // here because it has no way to process carries, so we have to use
  // separate add and adc instructions.  Bah.
  umulh(rscratch1, x_xstart, y_idx); // x_xstart * y_idx -> rscratch1:product
  mul(product, x_xstart, y_idx);
  adds(product, product, carry);
  adc(carry, rscratch1, zr);   // x_xstart * y_idx + carry -> carry:product

  subw(kdx, kdx, 2);
  ror(product, product, 32); // back to big-endian
  str(product, offsetted_address(z, kdx, Address::uxtw(LogBytesPerInt), 0, BytesPerLong));

  b(L_first_loop);

  bind(L_one_y);
  ldrw(y_idx, Address(y,  0));
  b(L_multiply);

  bind(L_one_x);
  ldrw(x_xstart, Address(x,  0));
  b(L_first_loop);

  bind(L_first_loop_exit);
}

/**
 * Multiply 128 bit by 128. Unrolled inner loop.
 *
 */
void MacroAssembler::multiply_128_x_128_loop(Register y, Register z,
                                             Register carry, Register carry2,
                                             Register idx, Register jdx,
                                             Register yz_idx1, Register yz_idx2,
                                             Register tmp, Register tmp3, Register tmp4,
                                             Register tmp6, Register product_hi) {

  //   jlong carry, x[], y[], z[];
  //   int kdx = ystart+1;
  //   for (int idx=ystart-2; idx >= 0; idx -= 2) { // Third loop
  //     huge_128 tmp3 = (y[idx+1] * product_hi) + z[kdx+idx+1] + carry;
  //     jlong carry2  = (jlong)(tmp3 >>> 64);
  //     huge_128 tmp4 = (y[idx]   * product_hi) + z[kdx+idx] + carry2;
  //     carry  = (jlong)(tmp4 >>> 64);
  //     z[kdx+idx+1] = (jlong)tmp3;
  //     z[kdx+idx] = (jlong)tmp4;
  //   }
  //   idx += 2;
  //   if (idx > 0) {
  //     yz_idx1 = (y[idx] * product_hi) + z[kdx+idx] + carry;
  //     z[kdx+idx] = (jlong)yz_idx1;
  //     carry  = (jlong)(yz_idx1 >>> 64);
  //   }
  //

  Label L_third_loop, L_third_loop_exit, L_post_third_loop_done;

  lsrw(jdx, idx, 2);

  bind(L_third_loop);

  subsw(jdx, jdx, 1);
  br(Assembler::MI, L_third_loop_exit);
  subw(idx, idx, 4);

  lea(rscratch1, Address(y, idx, Address::uxtw(LogBytesPerInt)));

  ldp(yz_idx2, yz_idx1, Address(rscratch1, 0));

  lea(tmp6, Address(z, idx, Address::uxtw(LogBytesPerInt)));

  ror(yz_idx1, yz_idx1, 32); // convert big-endian to little-endian
  ror(yz_idx2, yz_idx2, 32);

  ldp(rscratch2, rscratch1, Address(tmp6, 0));

  mul(tmp3, product_hi, yz_idx1);  //  yz_idx1 * product_hi -> tmp4:tmp3
  umulh(tmp4, product_hi, yz_idx1);

  ror(rscratch1, rscratch1, 32); // convert big-endian to little-endian
  ror(rscratch2, rscratch2, 32);

  mul(tmp, product_hi, yz_idx2);   //  yz_idx2 * product_hi -> carry2:tmp
  umulh(carry2, product_hi, yz_idx2);

  // propagate sum of both multiplications into carry:tmp4:tmp3
  adds(tmp3, tmp3, carry);
  adc(tmp4, tmp4, zr);
  adds(tmp3, tmp3, rscratch1);
  adcs(tmp4, tmp4, tmp);
  adc(carry, carry2, zr);
  adds(tmp4, tmp4, rscratch2);
  adc(carry, carry, zr);

  ror(tmp3, tmp3, 32); // convert little-endian to big-endian
  ror(tmp4, tmp4, 32);
  stp(tmp4, tmp3, Address(tmp6, 0));

  b(L_third_loop);
  bind (L_third_loop_exit);

  andw (idx, idx, 0x3);
  cbz(idx, L_post_third_loop_done);

  Label L_check_1;
  subsw(idx, idx, 2);
  br(Assembler::MI, L_check_1);

  lea(rscratch1, Address(y, idx, Address::uxtw(LogBytesPerInt)));
  ldr(yz_idx1, Address(rscratch1, 0));
  ror(yz_idx1, yz_idx1, 32);
  mul(tmp3, product_hi, yz_idx1);  //  yz_idx1 * product_hi -> tmp4:tmp3
  umulh(tmp4, product_hi, yz_idx1);
  lea(rscratch1, Address(z, idx, Address::uxtw(LogBytesPerInt)));
  ldr(yz_idx2, Address(rscratch1, 0));
  ror(yz_idx2, yz_idx2, 32);

  add2_with_carry(carry, tmp4, tmp3, carry, yz_idx2);

  ror(tmp3, tmp3, 32);
  str(tmp3, Address(rscratch1, 0));

  bind (L_check_1);

  andw (idx, idx, 0x1);
  subsw(idx, idx, 1);
  br(Assembler::MI, L_post_third_loop_done);
  ldrw(tmp4, Address(y, idx, Address::uxtw(LogBytesPerInt)));
  mul(tmp3, tmp4, product_hi);  //  tmp4 * product_hi -> carry2:tmp3
  umulh(carry2, tmp4, product_hi);
  ldrw(tmp4, Address(z, idx, Address::uxtw(LogBytesPerInt)));

  add2_with_carry(carry2, tmp3, tmp4, carry);

  strw(tmp3, Address(z, idx, Address::uxtw(LogBytesPerInt)));
  extr(carry, carry2, tmp3, 32);

  bind(L_post_third_loop_done);
}

/**
 * Code for BigInteger::multiplyToLen() instrinsic.
 *
 * r0: x
 * r1: xlen
 * r2: y
 * r3: ylen
 * r4:  z
 * r5: zlen
 * r10: tmp1
 * r11: tmp2
 * r12: tmp3
 * r13: tmp4
 * r14: tmp5
 * r15: tmp6
 * r16: tmp7
 *
 */
void MacroAssembler::multiply_to_len(Register x, Register xlen, Register y, Register ylen,
                                     Register z, Register zlen,
                                     Register tmp1, Register tmp2, Register tmp3, Register tmp4,
                                     Register tmp5, Register tmp6, Register product_hi) {

  assert_different_registers(x, xlen, y, ylen, z, zlen, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6);

  const Register idx = tmp1;
  const Register kdx = tmp2;
  const Register xstart = tmp3;

  const Register y_idx = tmp4;
  const Register carry = tmp5;
  const Register product  = xlen;
  const Register x_xstart = zlen;  // reuse register

  // First Loop.
  //
  //  final static long LONG_MASK = 0xffffffffL;
  //  int xstart = xlen - 1;
  //  int ystart = ylen - 1;
  //  long carry = 0;
  //  for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx-, kdx--) {
  //    long product = (y[idx] & LONG_MASK) * (x[xstart] & LONG_MASK) + carry;
  //    z[kdx] = (int)product;
  //    carry = product >>> 32;
  //  }
  //  z[xstart] = (int)carry;
  //

  movw(idx, ylen);      // idx = ylen;
  movw(kdx, zlen);      // kdx = xlen+ylen;
  mov(carry, zr);       // carry = 0;

  Label L_done;

  movw(xstart, xlen);
  subsw(xstart, xstart, 1);
  br(Assembler::MI, L_done);

  multiply_64_x_64_loop(x, xstart, x_xstart, y, y_idx, z, carry, product, idx, kdx);

  Label L_second_loop;
  cbzw(kdx, L_second_loop);

  Label L_carry;
  subw(kdx, kdx, 1);
  cbzw(kdx, L_carry);

  strw(carry, Address(z, kdx, Address::uxtw(LogBytesPerInt)));
  lsr(carry, carry, 32);
  subw(kdx, kdx, 1);

  bind(L_carry);
  strw(carry, Address(z, kdx, Address::uxtw(LogBytesPerInt)));

  // Second and third (nested) loops.
  //
  // for (int i = xstart-1; i >= 0; i--) { // Second loop
  //   carry = 0;
  //   for (int jdx=ystart, k=ystart+1+i; jdx >= 0; jdx--, k--) { // Third loop
  //     long product = (y[jdx] & LONG_MASK) * (x[i] & LONG_MASK) +
  //                    (z[k] & LONG_MASK) + carry;
  //     z[k] = (int)product;
  //     carry = product >>> 32;
  //   }
  //   z[i] = (int)carry;
  // }
  //
  // i = xlen, j = tmp1, k = tmp2, carry = tmp5, x[i] = product_hi

  const Register jdx = tmp1;

  bind(L_second_loop);
  mov(carry, zr);                // carry = 0;
  movw(jdx, ylen);               // j = ystart+1

  subsw(xstart, xstart, 1);      // i = xstart-1;
  br(Assembler::MI, L_done);

  str(z, Address(pre(sp, -4 * wordSize)));

  Label L_last_x;
  lea(z, offsetted_address(z, xstart, Address::uxtw(LogBytesPerInt), 4, BytesPerInt)); // z = z + k - j
  subsw(xstart, xstart, 1);       // i = xstart-1;
  br(Assembler::MI, L_last_x);

  lea(rscratch1, Address(x, xstart, Address::uxtw(LogBytesPerInt)));
  ldr(product_hi, Address(rscratch1));
  ror(product_hi, product_hi, 32);  // convert big-endian to little-endian

  Label L_third_loop_prologue;
  bind(L_third_loop_prologue);

  str(ylen, Address(sp, wordSize));
  stp(x, xstart, Address(sp, 2 * wordSize));
  multiply_128_x_128_loop(y, z, carry, x, jdx, ylen, product,
                          tmp2, x_xstart, tmp3, tmp4, tmp6, product_hi);
  ldp(z, ylen, Address(post(sp, 2 * wordSize)));
  ldp(x, xlen, Address(post(sp, 2 * wordSize)));   // copy old xstart -> xlen

  addw(tmp3, xlen, 1);
  strw(carry, Address(z, tmp3, Address::uxtw(LogBytesPerInt)));
  subsw(tmp3, tmp3, 1);
  br(Assembler::MI, L_done);

  lsr(carry, carry, 32);
  strw(carry, Address(z, tmp3, Address::uxtw(LogBytesPerInt)));
  b(L_second_loop);

  // Next infrequent code is moved outside loops.
  bind(L_last_x);
  ldrw(product_hi, Address(x,  0));
  b(L_third_loop_prologue);

  bind(L_done);
}

// Code for BigInteger::mulAdd instrinsic
// out     = r0
// in      = r1
// offset  = r2  (already out.length-offset)
// len     = r3
// k       = r4
//
// pseudo code from java implementation:
// carry = 0;
// offset = out.length-offset - 1;
// for (int j=len-1; j >= 0; j--) {
//     product = (in[j] & LONG_MASK) * kLong + (out[offset] & LONG_MASK) + carry;
//     out[offset--] = (int)product;
//     carry = product >>> 32;
// }
// return (int)carry;
void MacroAssembler::mul_add(Register out, Register in, Register offset,
      Register len, Register k) {
    Label LOOP, END;
    // pre-loop
    cmp(len, zr); // cmp, not cbz/cbnz: to use condition twice => less branches
    csel(out, zr, out, Assembler::EQ);
    br(Assembler::EQ, END);
    add(in, in, len, LSL, 2); // in[j+1] address
    add(offset, out, offset, LSL, 2); // out[offset + 1] address
    mov(out, zr); // used to keep carry now
    BIND(LOOP);
    ldrw(rscratch1, Address(pre(in, -4)));
    madd(rscratch1, rscratch1, k, out);
    ldrw(rscratch2, Address(pre(offset, -4)));
    add(rscratch1, rscratch1, rscratch2);
    strw(rscratch1, Address(offset));
    lsr(out, rscratch1, 32);
    subs(len, len, 1);
    br(Assembler::NE, LOOP);
    BIND(END);
}

/**
 * Emits code to update CRC-32 with a byte value according to constants in table
 *
 * @param [in,out]crc   Register containing the crc.
 * @param [in]val       Register containing the byte to fold into the CRC.
 * @param [in]table     Register containing the table of crc constants.
 *
 * uint32_t crc;
 * val = crc_table[(val ^ crc) & 0xFF];
 * crc = val ^ (crc >> 8);
 *
 */
void MacroAssembler::update_byte_crc32(Register crc, Register val, Register table) {
  eor(val, val, crc);
  andr(val, val, 0xff);
  ldrw(val, Address(table, val, Address::lsl(2)));
  eor(crc, val, crc, Assembler::LSR, 8);
}

/**
 * Emits code to update CRC-32 with a 32-bit value according to tables 0 to 3
 *
 * @param [in,out]crc   Register containing the crc.
 * @param [in]v         Register containing the 32-bit to fold into the CRC.
 * @param [in]table0    Register containing table 0 of crc constants.
 * @param [in]table1    Register containing table 1 of crc constants.
 * @param [in]table2    Register containing table 2 of crc constants.
 * @param [in]table3    Register containing table 3 of crc constants.
 *
 * uint32_t crc;
 *   v = crc ^ v
 *   crc = table3[v&0xff]^table2[(v>>8)&0xff]^table1[(v>>16)&0xff]^table0[v>>24]
 *
 */
void MacroAssembler::update_word_crc32(Register crc, Register v, Register tmp,
        Register table0, Register table1, Register table2, Register table3,
        bool upper) {
  eor(v, crc, v, upper ? LSR:LSL, upper ? 32:0);
  uxtb(tmp, v);
  ldrw(crc, Address(table3, tmp, Address::lsl(2)));
  ubfx(tmp, v, 8, 8);
  ldrw(tmp, Address(table2, tmp, Address::lsl(2)));
  eor(crc, crc, tmp);
  ubfx(tmp, v, 16, 8);
  ldrw(tmp, Address(table1, tmp, Address::lsl(2)));
  eor(crc, crc, tmp);
  ubfx(tmp, v, 24, 8);
  ldrw(tmp, Address(table0, tmp, Address::lsl(2)));
  eor(crc, crc, tmp);
}

void MacroAssembler::kernel_crc32_using_crc32(Register crc, Register buf,
        Register len, Register tmp0, Register tmp1, Register tmp2,
        Register tmp3) {
    Label CRC_by64_loop, CRC_by4_loop, CRC_by1_loop, CRC_less64, CRC_by64_pre, CRC_by32_loop, CRC_less32, L_exit;
    assert_different_registers(crc, buf, len, tmp0, tmp1, tmp2, tmp3);

    mvnw(crc, crc);

    subs(len, len, 128);
    br(Assembler::GE, CRC_by64_pre);
  BIND(CRC_less64);
    adds(len, len, 128-32);
    br(Assembler::GE, CRC_by32_loop);
  BIND(CRC_less32);
    adds(len, len, 32-4);
    br(Assembler::GE, CRC_by4_loop);
    adds(len, len, 4);
    br(Assembler::GT, CRC_by1_loop);
    b(L_exit);

  BIND(CRC_by32_loop);
    ldp(tmp0, tmp1, Address(post(buf, 16)));
    subs(len, len, 32);
    crc32x(crc, crc, tmp0);
    ldr(tmp2, Address(post(buf, 8)));
    crc32x(crc, crc, tmp1);
    ldr(tmp3, Address(post(buf, 8)));
    crc32x(crc, crc, tmp2);
    crc32x(crc, crc, tmp3);
    br(Assembler::GE, CRC_by32_loop);
    cmn(len, 32);
    br(Assembler::NE, CRC_less32);
    b(L_exit);

  BIND(CRC_by4_loop);
    ldrw(tmp0, Address(post(buf, 4)));
    subs(len, len, 4);
    crc32w(crc, crc, tmp0);
    br(Assembler::GE, CRC_by4_loop);
    adds(len, len, 4);
    br(Assembler::LE, L_exit);
  BIND(CRC_by1_loop);
    ldrb(tmp0, Address(post(buf, 1)));
    subs(len, len, 1);
    crc32b(crc, crc, tmp0);
    br(Assembler::GT, CRC_by1_loop);
    b(L_exit);

  BIND(CRC_by64_pre);
    sub(buf, buf, 8);
    ldp(tmp0, tmp1, Address(buf, 8));
    crc32x(crc, crc, tmp0);
    ldr(tmp2, Address(buf, 24));
    crc32x(crc, crc, tmp1);
    ldr(tmp3, Address(buf, 32));
    crc32x(crc, crc, tmp2);
    ldr(tmp0, Address(buf, 40));
    crc32x(crc, crc, tmp3);
    ldr(tmp1, Address(buf, 48));
    crc32x(crc, crc, tmp0);
    ldr(tmp2, Address(buf, 56));
    crc32x(crc, crc, tmp1);
    ldr(tmp3, Address(pre(buf, 64)));

    b(CRC_by64_loop);

    align(CodeEntryAlignment);
  BIND(CRC_by64_loop);
    subs(len, len, 64);
    crc32x(crc, crc, tmp2);
    ldr(tmp0, Address(buf, 8));
    crc32x(crc, crc, tmp3);
    ldr(tmp1, Address(buf, 16));
    crc32x(crc, crc, tmp0);
    ldr(tmp2, Address(buf, 24));
    crc32x(crc, crc, tmp1);
    ldr(tmp3, Address(buf, 32));
    crc32x(crc, crc, tmp2);
    ldr(tmp0, Address(buf, 40));
    crc32x(crc, crc, tmp3);
    ldr(tmp1, Address(buf, 48));
    crc32x(crc, crc, tmp0);
    ldr(tmp2, Address(buf, 56));
    crc32x(crc, crc, tmp1);
    ldr(tmp3, Address(pre(buf, 64)));
    br(Assembler::GE, CRC_by64_loop);

    // post-loop
    crc32x(crc, crc, tmp2);
    crc32x(crc, crc, tmp3);

    sub(len, len, 64);
    add(buf, buf, 8);
    cmn(len, 128);
    br(Assembler::NE, CRC_less64);
  BIND(L_exit);
    mvnw(crc, crc);
}

/**
 * @param crc   register containing existing CRC (32-bit)
 * @param buf   register pointing to input byte buffer (byte*)
 * @param len   register containing number of bytes
 * @param table register that will contain address of CRC table
 * @param tmp   scratch register
 */
void MacroAssembler::kernel_crc32(Register crc, Register buf, Register len,
        Register table0, Register table1, Register table2, Register table3,
        Register tmp, Register tmp2, Register tmp3) {
  Label L_by16, L_by16_loop, L_by4, L_by4_loop, L_by1, L_by1_loop, L_exit;
  unsigned long offset;

  if (UseCRC32) {
      kernel_crc32_using_crc32(crc, buf, len, table0, table1, table2, table3);
      return;
  }

    mvnw(crc, crc);

    adrp(table0, ExternalAddress(StubRoutines::crc_table_addr()), offset);
    if (offset) add(table0, table0, offset);
    add(table1, table0, 1*256*sizeof(juint));
    add(table2, table0, 2*256*sizeof(juint));
    add(table3, table0, 3*256*sizeof(juint));

  if (UseNeon) {
      cmp(len, (u1)64);
      br(Assembler::LT, L_by16);
      eor(v16, T16B, v16, v16);

    Label L_fold;

      add(tmp, table0, 4*256*sizeof(juint)); // Point at the Neon constants

      ld1(v0, v1, T2D, post(buf, 32));
      ld1r(v4, T2D, post(tmp, 8));
      ld1r(v5, T2D, post(tmp, 8));
      ld1r(v6, T2D, post(tmp, 8));
      ld1r(v7, T2D, post(tmp, 8));
      mov(v16, T4S, 0, crc);

      eor(v0, T16B, v0, v16);
      sub(len, len, 64);

    BIND(L_fold);
      pmull(v22, T8H, v0, v5, T8B);
      pmull(v20, T8H, v0, v7, T8B);
      pmull(v23, T8H, v0, v4, T8B);
      pmull(v21, T8H, v0, v6, T8B);

      pmull2(v18, T8H, v0, v5, T16B);
      pmull2(v16, T8H, v0, v7, T16B);
      pmull2(v19, T8H, v0, v4, T16B);
      pmull2(v17, T8H, v0, v6, T16B);

      uzp1(v24, T8H, v20, v22);
      uzp2(v25, T8H, v20, v22);
      eor(v20, T16B, v24, v25);

      uzp1(v26, T8H, v16, v18);
      uzp2(v27, T8H, v16, v18);
      eor(v16, T16B, v26, v27);

      ushll2(v22, T4S, v20, T8H, 8);
      ushll(v20, T4S, v20, T4H, 8);

      ushll2(v18, T4S, v16, T8H, 8);
      ushll(v16, T4S, v16, T4H, 8);

      eor(v22, T16B, v23, v22);
      eor(v18, T16B, v19, v18);
      eor(v20, T16B, v21, v20);
      eor(v16, T16B, v17, v16);

      uzp1(v17, T2D, v16, v20);
      uzp2(v21, T2D, v16, v20);
      eor(v17, T16B, v17, v21);

      ushll2(v20, T2D, v17, T4S, 16);
      ushll(v16, T2D, v17, T2S, 16);

      eor(v20, T16B, v20, v22);
      eor(v16, T16B, v16, v18);

      uzp1(v17, T2D, v20, v16);
      uzp2(v21, T2D, v20, v16);
      eor(v28, T16B, v17, v21);

      pmull(v22, T8H, v1, v5, T8B);
      pmull(v20, T8H, v1, v7, T8B);
      pmull(v23, T8H, v1, v4, T8B);
      pmull(v21, T8H, v1, v6, T8B);

      pmull2(v18, T8H, v1, v5, T16B);
      pmull2(v16, T8H, v1, v7, T16B);
      pmull2(v19, T8H, v1, v4, T16B);
      pmull2(v17, T8H, v1, v6, T16B);

      ld1(v0, v1, T2D, post(buf, 32));

      uzp1(v24, T8H, v20, v22);
      uzp2(v25, T8H, v20, v22);
      eor(v20, T16B, v24, v25);

      uzp1(v26, T8H, v16, v18);
      uzp2(v27, T8H, v16, v18);
      eor(v16, T16B, v26, v27);

      ushll2(v22, T4S, v20, T8H, 8);
      ushll(v20, T4S, v20, T4H, 8);

      ushll2(v18, T4S, v16, T8H, 8);
      ushll(v16, T4S, v16, T4H, 8);

      eor(v22, T16B, v23, v22);
      eor(v18, T16B, v19, v18);
      eor(v20, T16B, v21, v20);
      eor(v16, T16B, v17, v16);

      uzp1(v17, T2D, v16, v20);
      uzp2(v21, T2D, v16, v20);
      eor(v16, T16B, v17, v21);

      ushll2(v20, T2D, v16, T4S, 16);
      ushll(v16, T2D, v16, T2S, 16);

      eor(v20, T16B, v22, v20);
      eor(v16, T16B, v16, v18);

      uzp1(v17, T2D, v20, v16);
      uzp2(v21, T2D, v20, v16);
      eor(v20, T16B, v17, v21);

      shl(v16, T2D, v28, 1);
      shl(v17, T2D, v20, 1);

      eor(v0, T16B, v0, v16);
      eor(v1, T16B, v1, v17);

      subs(len, len, 32);
      br(Assembler::GE, L_fold);

      mov(crc, 0);
      mov(tmp, v0, T1D, 0);
      update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, false);
      update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, true);
      mov(tmp, v0, T1D, 1);
      update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, false);
      update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, true);
      mov(tmp, v1, T1D, 0);
      update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, false);
      update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, true);
      mov(tmp, v1, T1D, 1);
      update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, false);
      update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, true);

      add(len, len, 32);
  }

  BIND(L_by16);
    subs(len, len, 16);
    br(Assembler::GE, L_by16_loop);
    adds(len, len, 16-4);
    br(Assembler::GE, L_by4_loop);
    adds(len, len, 4);
    br(Assembler::GT, L_by1_loop);
    b(L_exit);

  BIND(L_by4_loop);
    ldrw(tmp, Address(post(buf, 4)));
    update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3);
    subs(len, len, 4);
    br(Assembler::GE, L_by4_loop);
    adds(len, len, 4);
    br(Assembler::LE, L_exit);
  BIND(L_by1_loop);
    subs(len, len, 1);
    ldrb(tmp, Address(post(buf, 1)));
    update_byte_crc32(crc, tmp, table0);
    br(Assembler::GT, L_by1_loop);
    b(L_exit);

    align(CodeEntryAlignment);
  BIND(L_by16_loop);
    subs(len, len, 16);
    ldp(tmp, tmp3, Address(post(buf, 16)));
    update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, false);
    update_word_crc32(crc, tmp, tmp2, table0, table1, table2, table3, true);
    update_word_crc32(crc, tmp3, tmp2, table0, table1, table2, table3, false);
    update_word_crc32(crc, tmp3, tmp2, table0, table1, table2, table3, true);
    br(Assembler::GE, L_by16_loop);
    adds(len, len, 16-4);
    br(Assembler::GE, L_by4_loop);
    adds(len, len, 4);
    br(Assembler::GT, L_by1_loop);
  BIND(L_exit);
    mvnw(crc, crc);
}

void MacroAssembler::kernel_crc32c_using_crc32c(Register crc, Register buf,
        Register len, Register tmp0, Register tmp1, Register tmp2,
        Register tmp3) {
    Label CRC_by64_loop, CRC_by4_loop, CRC_by1_loop, CRC_less64, CRC_by64_pre, CRC_by32_loop, CRC_less32, L_exit;
    assert_different_registers(crc, buf, len, tmp0, tmp1, tmp2, tmp3);

    subs(len, len, 128);
    br(Assembler::GE, CRC_by64_pre);
  BIND(CRC_less64);
    adds(len, len, 128-32);
    br(Assembler::GE, CRC_by32_loop);
  BIND(CRC_less32);
    adds(len, len, 32-4);
    br(Assembler::GE, CRC_by4_loop);
    adds(len, len, 4);
    br(Assembler::GT, CRC_by1_loop);
    b(L_exit);

  BIND(CRC_by32_loop);
    ldp(tmp0, tmp1, Address(post(buf, 16)));
    subs(len, len, 32);
    crc32cx(crc, crc, tmp0);
    ldr(tmp2, Address(post(buf, 8)));
    crc32cx(crc, crc, tmp1);
    ldr(tmp3, Address(post(buf, 8)));
    crc32cx(crc, crc, tmp2);
    crc32cx(crc, crc, tmp3);
    br(Assembler::GE, CRC_by32_loop);
    cmn(len, 32);
    br(Assembler::NE, CRC_less32);
    b(L_exit);

  BIND(CRC_by4_loop);
    ldrw(tmp0, Address(post(buf, 4)));
    subs(len, len, 4);
    crc32cw(crc, crc, tmp0);
    br(Assembler::GE, CRC_by4_loop);
    adds(len, len, 4);
    br(Assembler::LE, L_exit);
  BIND(CRC_by1_loop);
    ldrb(tmp0, Address(post(buf, 1)));
    subs(len, len, 1);
    crc32cb(crc, crc, tmp0);
    br(Assembler::GT, CRC_by1_loop);
    b(L_exit);

  BIND(CRC_by64_pre);
    sub(buf, buf, 8);
    ldp(tmp0, tmp1, Address(buf, 8));
    crc32cx(crc, crc, tmp0);
    ldr(tmp2, Address(buf, 24));
    crc32cx(crc, crc, tmp1);
    ldr(tmp3, Address(buf, 32));
    crc32cx(crc, crc, tmp2);
    ldr(tmp0, Address(buf, 40));
    crc32cx(crc, crc, tmp3);
    ldr(tmp1, Address(buf, 48));
    crc32cx(crc, crc, tmp0);
    ldr(tmp2, Address(buf, 56));
    crc32cx(crc, crc, tmp1);
    ldr(tmp3, Address(pre(buf, 64)));

    b(CRC_by64_loop);

    align(CodeEntryAlignment);
  BIND(CRC_by64_loop);
    subs(len, len, 64);
    crc32cx(crc, crc, tmp2);
    ldr(tmp0, Address(buf, 8));
    crc32cx(crc, crc, tmp3);
    ldr(tmp1, Address(buf, 16));
    crc32cx(crc, crc, tmp0);
    ldr(tmp2, Address(buf, 24));
    crc32cx(crc, crc, tmp1);
    ldr(tmp3, Address(buf, 32));
    crc32cx(crc, crc, tmp2);
    ldr(tmp0, Address(buf, 40));
    crc32cx(crc, crc, tmp3);
    ldr(tmp1, Address(buf, 48));
    crc32cx(crc, crc, tmp0);
    ldr(tmp2, Address(buf, 56));
    crc32cx(crc, crc, tmp1);
    ldr(tmp3, Address(pre(buf, 64)));
    br(Assembler::GE, CRC_by64_loop);

    // post-loop
    crc32cx(crc, crc, tmp2);
    crc32cx(crc, crc, tmp3);

    sub(len, len, 64);
    add(buf, buf, 8);
    cmn(len, 128);
    br(Assembler::NE, CRC_less64);
  BIND(L_exit);
}

/**
 * @param crc   register containing existing CRC (32-bit)
 * @param buf   register pointing to input byte buffer (byte*)
 * @param len   register containing number of bytes
 * @param table register that will contain address of CRC table
 * @param tmp   scratch register
 */
void MacroAssembler::kernel_crc32c(Register crc, Register buf, Register len,
        Register table0, Register table1, Register table2, Register table3,
        Register tmp, Register tmp2, Register tmp3) {
  kernel_crc32c_using_crc32c(crc, buf, len, table0, table1, table2, table3);
}


SkipIfEqual::SkipIfEqual(
    MacroAssembler* masm, const bool* flag_addr, bool value) {
  _masm = masm;
  unsigned long offset;
  _masm->adrp(rscratch1, ExternalAddress((address)flag_addr), offset);
  _masm->ldrb(rscratch1, Address(rscratch1, offset));
  _masm->cbzw(rscratch1, _label);
}

SkipIfEqual::~SkipIfEqual() {
  _masm->bind(_label);
}

void MacroAssembler::addptr(const Address &dst, int32_t src) {
  Address adr;
  switch(dst.getMode()) {
  case Address::base_plus_offset:
    // This is the expected mode, although we allow all the other
    // forms below.
    adr = form_address(rscratch2, dst.base(), dst.offset(), LogBytesPerWord);
    break;
  default:
    lea(rscratch2, dst);
    adr = Address(rscratch2);
    break;
  }
  ldr(rscratch1, adr);
  add(rscratch1, rscratch1, src);
  str(rscratch1, adr);
}

void MacroAssembler::cmpptr(Register src1, Address src2) {
  unsigned long offset;
  adrp(rscratch1, src2, offset);
  ldr(rscratch1, Address(rscratch1, offset));
  cmp(src1, rscratch1);
}

void MacroAssembler::cmpoop(Register obj1, Register obj2) {
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->obj_equals(this, obj1, obj2);
}

void MacroAssembler::load_klass(Register dst, Register src) {
  if (UseCompressedClassPointers) {
    ldrw(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    decode_klass_not_null(dst);
  } else {
    ldr(dst, Address(src, oopDesc::klass_offset_in_bytes()));
  }
}

// ((OopHandle)result).resolve();
void MacroAssembler::resolve_oop_handle(Register result, Register tmp) {
  // OopHandle::resolve is an indirection.
  access_load_at(T_OBJECT, IN_NATIVE, result, Address(result, 0), tmp, noreg);
}

void MacroAssembler::load_mirror(Register dst, Register method, Register tmp) {
  const int mirror_offset = in_bytes(Klass::java_mirror_offset());
  ldr(dst, Address(rmethod, Method::const_offset()));
  ldr(dst, Address(dst, ConstMethod::constants_offset()));
  ldr(dst, Address(dst, ConstantPool::pool_holder_offset_in_bytes()));
  ldr(dst, Address(dst, mirror_offset));
  resolve_oop_handle(dst, tmp);
}

void MacroAssembler::cmp_klass(Register oop, Register trial_klass, Register tmp) {
  if (UseCompressedClassPointers) {
    ldrw(tmp, Address(oop, oopDesc::klass_offset_in_bytes()));
    if (Universe::narrow_klass_base() == NULL) {
      cmp(trial_klass, tmp, LSL, Universe::narrow_klass_shift());
      return;
    } else if (((uint64_t)Universe::narrow_klass_base() & 0xffffffff) == 0
               && Universe::narrow_klass_shift() == 0) {
      // Only the bottom 32 bits matter
      cmpw(trial_klass, tmp);
      return;
    }
    decode_klass_not_null(tmp);
  } else {
    ldr(tmp, Address(oop, oopDesc::klass_offset_in_bytes()));
  }
  cmp(trial_klass, tmp);
}

void MacroAssembler::load_prototype_header(Register dst, Register src) {
  load_klass(dst, src);
  ldr(dst, Address(dst, Klass::prototype_header_offset()));
}

void MacroAssembler::store_klass(Register dst, Register src) {
  // FIXME: Should this be a store release?  concurrent gcs assumes
  // klass length is valid if klass field is not null.
  if (UseCompressedClassPointers) {
    encode_klass_not_null(src);
    strw(src, Address(dst, oopDesc::klass_offset_in_bytes()));
  } else {
    str(src, Address(dst, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::store_klass_gap(Register dst, Register src) {
  if (UseCompressedClassPointers) {
    // Store to klass gap in destination
    strw(src, Address(dst, oopDesc::klass_gap_offset_in_bytes()));
  }
}

// Algorithm must match CompressedOops::encode.
void MacroAssembler::encode_heap_oop(Register d, Register s) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::encode_heap_oop: heap base corrupted?");
#endif
  verify_oop(s, "broken oop in encode_heap_oop");
  if (Universe::narrow_oop_base() == NULL) {
    if (Universe::narrow_oop_shift() != 0) {
      assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
      lsr(d, s, LogMinObjAlignmentInBytes);
    } else {
      mov(d, s);
    }
  } else {
    subs(d, s, rheapbase);
    csel(d, d, zr, Assembler::HS);
    lsr(d, d, LogMinObjAlignmentInBytes);

    /*  Old algorithm: is this any worse?
    Label nonnull;
    cbnz(r, nonnull);
    sub(r, r, rheapbase);
    bind(nonnull);
    lsr(r, r, LogMinObjAlignmentInBytes);
    */
  }
}

void MacroAssembler::encode_heap_oop_not_null(Register r) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::encode_heap_oop_not_null: heap base corrupted?");
  if (CheckCompressedOops) {
    Label ok;
    cbnz(r, ok);
    stop("null oop passed to encode_heap_oop_not_null");
    bind(ok);
  }
#endif
  verify_oop(r, "broken oop in encode_heap_oop_not_null");
  if (Universe::narrow_oop_base() != NULL) {
    sub(r, r, rheapbase);
  }
  if (Universe::narrow_oop_shift() != 0) {
    assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
    lsr(r, r, LogMinObjAlignmentInBytes);
  }
}

void MacroAssembler::encode_heap_oop_not_null(Register dst, Register src) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::encode_heap_oop_not_null2: heap base corrupted?");
  if (CheckCompressedOops) {
    Label ok;
    cbnz(src, ok);
    stop("null oop passed to encode_heap_oop_not_null2");
    bind(ok);
  }
#endif
  verify_oop(src, "broken oop in encode_heap_oop_not_null2");

  Register data = src;
  if (Universe::narrow_oop_base() != NULL) {
    sub(dst, src, rheapbase);
    data = dst;
  }
  if (Universe::narrow_oop_shift() != 0) {
    assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
    lsr(dst, data, LogMinObjAlignmentInBytes);
    data = dst;
  }
  if (data == src)
    mov(dst, src);
}

void  MacroAssembler::decode_heap_oop(Register d, Register s) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::decode_heap_oop: heap base corrupted?");
#endif
  if (Universe::narrow_oop_base() == NULL) {
    if (Universe::narrow_oop_shift() != 0 || d != s) {
      lsl(d, s, Universe::narrow_oop_shift());
    }
  } else {
    Label done;
    if (d != s)
      mov(d, s);
    cbz(s, done);
    add(d, rheapbase, s, Assembler::LSL, LogMinObjAlignmentInBytes);
    bind(done);
  }
  verify_oop(d, "broken oop in decode_heap_oop");
}

void  MacroAssembler::decode_heap_oop_not_null(Register r) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  if (Universe::narrow_oop_shift() != 0) {
    assert(LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
    if (Universe::narrow_oop_base() != NULL) {
      add(r, rheapbase, r, Assembler::LSL, LogMinObjAlignmentInBytes);
    } else {
      add(r, zr, r, Assembler::LSL, LogMinObjAlignmentInBytes);
    }
  } else {
    assert (Universe::narrow_oop_base() == NULL, "sanity");
  }
}

void  MacroAssembler::decode_heap_oop_not_null(Register dst, Register src) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  if (Universe::narrow_oop_shift() != 0) {
    assert(LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
    if (Universe::narrow_oop_base() != NULL) {
      add(dst, rheapbase, src, Assembler::LSL, LogMinObjAlignmentInBytes);
    } else {
      add(dst, zr, src, Assembler::LSL, LogMinObjAlignmentInBytes);
    }
  } else {
    assert (Universe::narrow_oop_base() == NULL, "sanity");
    if (dst != src) {
      mov(dst, src);
    }
  }
}

void MacroAssembler::encode_klass_not_null(Register dst, Register src) {
  if (Universe::narrow_klass_base() == NULL) {
    if (Universe::narrow_klass_shift() != 0) {
      assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift(), "decode alg wrong");
      lsr(dst, src, LogKlassAlignmentInBytes);
    } else {
      if (dst != src) mov(dst, src);
    }
    return;
  }

  if (use_XOR_for_compressed_class_base) {
    if (Universe::narrow_klass_shift() != 0) {
      eor(dst, src, (uint64_t)Universe::narrow_klass_base());
      lsr(dst, dst, LogKlassAlignmentInBytes);
    } else {
      eor(dst, src, (uint64_t)Universe::narrow_klass_base());
    }
    return;
  }

  if (((uint64_t)Universe::narrow_klass_base() & 0xffffffff) == 0
      && Universe::narrow_klass_shift() == 0) {
    movw(dst, src);
    return;
  }

#ifdef ASSERT
  verify_heapbase("MacroAssembler::encode_klass_not_null2: heap base corrupted?");
#endif

  Register rbase = dst;
  if (dst == src) rbase = rheapbase;
  mov(rbase, (uint64_t)Universe::narrow_klass_base());
  sub(dst, src, rbase);
  if (Universe::narrow_klass_shift() != 0) {
    assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift(), "decode alg wrong");
    lsr(dst, dst, LogKlassAlignmentInBytes);
  }
  if (dst == src) reinit_heapbase();
}

void MacroAssembler::encode_klass_not_null(Register r) {
  encode_klass_not_null(r, r);
}

void  MacroAssembler::decode_klass_not_null(Register dst, Register src) {
  Register rbase = dst;
  assert (UseCompressedClassPointers, "should only be used for compressed headers");

  if (Universe::narrow_klass_base() == NULL) {
    if (Universe::narrow_klass_shift() != 0) {
      assert(LogKlassAlignmentInBytes == Universe::narrow_klass_shift(), "decode alg wrong");
      lsl(dst, src, LogKlassAlignmentInBytes);
    } else {
      if (dst != src) mov(dst, src);
    }
    return;
  }

  if (use_XOR_for_compressed_class_base) {
    if (Universe::narrow_klass_shift() != 0) {
      lsl(dst, src, LogKlassAlignmentInBytes);
      eor(dst, dst, (uint64_t)Universe::narrow_klass_base());
    } else {
      eor(dst, src, (uint64_t)Universe::narrow_klass_base());
    }
    return;
  }

  if (((uint64_t)Universe::narrow_klass_base() & 0xffffffff) == 0
      && Universe::narrow_klass_shift() == 0) {
    if (dst != src)
      movw(dst, src);
    movk(dst, (uint64_t)Universe::narrow_klass_base() >> 32, 32);
    return;
  }

  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  if (dst == src) rbase = rheapbase;
  mov(rbase, (uint64_t)Universe::narrow_klass_base());
  if (Universe::narrow_klass_shift() != 0) {
    assert(LogKlassAlignmentInBytes == Universe::narrow_klass_shift(), "decode alg wrong");
    add(dst, rbase, src, Assembler::LSL, LogKlassAlignmentInBytes);
  } else {
    add(dst, rbase, src);
  }
  if (dst == src) reinit_heapbase();
}

void  MacroAssembler::decode_klass_not_null(Register r) {
  decode_klass_not_null(r, r);
}

void  MacroAssembler::set_narrow_oop(Register dst, jobject obj) {
#ifdef ASSERT
  {
    ThreadInVMfromUnknown tiv;
    assert (UseCompressedOops, "should only be used for compressed oops");
    assert (Universe::heap() != NULL, "java heap should be initialized");
    assert (oop_recorder() != NULL, "this assembler needs an OopRecorder");
    assert(Universe::heap()->is_in_reserved(JNIHandles::resolve(obj)), "should be real oop");
  }
#endif
  int oop_index = oop_recorder()->find_index(obj);
  InstructionMark im(this);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  code_section()->relocate(inst_mark(), rspec);
  movz(dst, 0xDEAD, 16);
  movk(dst, 0xBEEF);
}

void  MacroAssembler::set_narrow_klass(Register dst, Klass* k) {
  assert (UseCompressedClassPointers, "should only be used for compressed headers");
  assert (oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int index = oop_recorder()->find_index(k);
  assert(! Universe::heap()->is_in_reserved(k), "should not be an oop");

  InstructionMark im(this);
  RelocationHolder rspec = metadata_Relocation::spec(index);
  code_section()->relocate(inst_mark(), rspec);
  narrowKlass nk = Klass::encode_klass(k);
  movz(dst, (nk >> 16), 16);
  movk(dst, nk & 0xffff);
}

void MacroAssembler::access_load_at(BasicType type, DecoratorSet decorators,
                                    Register dst, Address src,
                                    Register tmp1, Register thread_tmp) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  decorators = AccessInternal::decorator_fixup(decorators);
  bool as_raw = (decorators & AS_RAW) != 0;
  if (as_raw) {
    bs->BarrierSetAssembler::load_at(this, decorators, type, dst, src, tmp1, thread_tmp);
  } else {
    bs->load_at(this, decorators, type, dst, src, tmp1, thread_tmp);
  }
}

void MacroAssembler::access_store_at(BasicType type, DecoratorSet decorators,
                                     Address dst, Register src,
                                     Register tmp1, Register thread_tmp) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  decorators = AccessInternal::decorator_fixup(decorators);
  bool as_raw = (decorators & AS_RAW) != 0;
  if (as_raw) {
    bs->BarrierSetAssembler::store_at(this, decorators, type, dst, src, tmp1, thread_tmp);
  } else {
    bs->store_at(this, decorators, type, dst, src, tmp1, thread_tmp);
  }
}

void MacroAssembler::resolve(DecoratorSet decorators, Register obj) {
  // Use stronger ACCESS_WRITE|ACCESS_READ by default.
  if ((decorators & (ACCESS_READ | ACCESS_WRITE)) == 0) {
    decorators |= ACCESS_READ | ACCESS_WRITE;
  }
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  return bs->resolve(this, decorators, obj);
}

void MacroAssembler::load_heap_oop(Register dst, Address src, Register tmp1,
                                   Register thread_tmp, DecoratorSet decorators) {
  access_load_at(T_OBJECT, IN_HEAP | decorators, dst, src, tmp1, thread_tmp);
}

void MacroAssembler::load_heap_oop_not_null(Register dst, Address src, Register tmp1,
                                            Register thread_tmp, DecoratorSet decorators) {
  access_load_at(T_OBJECT, IN_HEAP | IS_NOT_NULL | decorators, dst, src, tmp1, thread_tmp);
}

void MacroAssembler::store_heap_oop(Address dst, Register src, Register tmp1,
                                    Register thread_tmp, DecoratorSet decorators) {
  access_store_at(T_OBJECT, IN_HEAP | decorators, dst, src, tmp1, thread_tmp);
}

// Used for storing NULLs.
void MacroAssembler::store_heap_oop_null(Address dst) {
  access_store_at(T_OBJECT, IN_HEAP, dst, noreg, noreg, noreg);
}

Address MacroAssembler::allocate_metadata_address(Metadata* obj) {
  assert(oop_recorder() != NULL, "this assembler needs a Recorder");
  int index = oop_recorder()->allocate_metadata_index(obj);
  RelocationHolder rspec = metadata_Relocation::spec(index);
  return Address((address)obj, rspec);
}

// Move an oop into a register.  immediate is true if we want
// immediate instrcutions, i.e. we are not going to patch this
// instruction while the code is being executed by another thread.  In
// that case we can use move immediates rather than the constant pool.
void MacroAssembler::movoop(Register dst, jobject obj, bool immediate) {
  int oop_index;
  if (obj == NULL) {
    oop_index = oop_recorder()->allocate_oop_index(obj);
  } else {
#ifdef ASSERT
    {
      ThreadInVMfromUnknown tiv;
      assert(Universe::heap()->is_in_reserved(JNIHandles::resolve(obj)), "should be real oop");
    }
#endif
    oop_index = oop_recorder()->find_index(obj);
  }
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  if (! immediate) {
    address dummy = address(uintptr_t(pc()) & -wordSize); // A nearby aligned address
    ldr_constant(dst, Address(dummy, rspec));
  } else
    mov(dst, Address((address)obj, rspec));
}

// Move a metadata address into a register.
void MacroAssembler::mov_metadata(Register dst, Metadata* obj) {
  int oop_index;
  if (obj == NULL) {
    oop_index = oop_recorder()->allocate_metadata_index(obj);
  } else {
    oop_index = oop_recorder()->find_index(obj);
  }
  RelocationHolder rspec = metadata_Relocation::spec(oop_index);
  mov(dst, Address((address)obj, rspec));
}

Address MacroAssembler::constant_oop_address(jobject obj) {
#ifdef ASSERT
  {
    ThreadInVMfromUnknown tiv;
    assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
    assert(Universe::heap()->is_in_reserved(JNIHandles::resolve(obj)), "not an oop");
  }
#endif
  int oop_index = oop_recorder()->find_index(obj);
  return Address((address)obj, oop_Relocation::spec(oop_index));
}

// Defines obj, preserves var_size_in_bytes, okay for t2 == var_size_in_bytes.
void MacroAssembler::tlab_allocate(Register obj,
                                   Register var_size_in_bytes,
                                   int con_size_in_bytes,
                                   Register t1,
                                   Register t2,
                                   Label& slow_case) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->tlab_allocate(this, obj, var_size_in_bytes, con_size_in_bytes, t1, t2, slow_case);
}

// Defines obj, preserves var_size_in_bytes
void MacroAssembler::eden_allocate(Register obj,
                                   Register var_size_in_bytes,
                                   int con_size_in_bytes,
                                   Register t1,
                                   Label& slow_case) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->eden_allocate(this, obj, var_size_in_bytes, con_size_in_bytes, t1, slow_case);
}

// Zero words; len is in bytes
// Destroys all registers except addr
// len must be a nonzero multiple of wordSize
void MacroAssembler::zero_memory(Register addr, Register len, Register t1) {
  assert_different_registers(addr, len, t1, rscratch1, rscratch2);

#ifdef ASSERT
  { Label L;
    tst(len, BytesPerWord - 1);
    br(Assembler::EQ, L);
    stop("len is not a multiple of BytesPerWord");
    bind(L);
  }
#endif

#ifndef PRODUCT
  block_comment("zero memory");
#endif

  Label loop;
  Label entry;

//  Algorithm:
//
//    scratch1 = cnt & 7;
//    cnt -= scratch1;
//    p += scratch1;
//    switch (scratch1) {
//      do {
//        cnt -= 8;
//          p[-8] = 0;
//        case 7:
//          p[-7] = 0;
//        case 6:
//          p[-6] = 0;
//          // ...
//        case 1:
//          p[-1] = 0;
//        case 0:
//          p += 8;
//      } while (cnt);
//    }

  const int unroll = 8; // Number of str(zr) instructions we'll unroll

  lsr(len, len, LogBytesPerWord);
  andr(rscratch1, len, unroll - 1);  // tmp1 = cnt % unroll
  sub(len, len, rscratch1);      // cnt -= unroll
  // t1 always points to the end of the region we're about to zero
  add(t1, addr, rscratch1, Assembler::LSL, LogBytesPerWord);
  adr(rscratch2, entry);
  sub(rscratch2, rscratch2, rscratch1, Assembler::LSL, 2);
  br(rscratch2);
  bind(loop);
  sub(len, len, unroll);
  for (int i = -unroll; i < 0; i++)
    Assembler::str(zr, Address(t1, i * wordSize));
  bind(entry);
  add(t1, t1, unroll * wordSize);
  cbnz(len, loop);
}

void MacroAssembler::verify_tlab() {
#ifdef ASSERT
  if (UseTLAB && VerifyOops) {
    Label next, ok;

    stp(rscratch2, rscratch1, Address(pre(sp, -16)));

    ldr(rscratch2, Address(rthread, in_bytes(JavaThread::tlab_top_offset())));
    ldr(rscratch1, Address(rthread, in_bytes(JavaThread::tlab_start_offset())));
    cmp(rscratch2, rscratch1);
    br(Assembler::HS, next);
    STOP("assert(top >= start)");
    should_not_reach_here();

    bind(next);
    ldr(rscratch2, Address(rthread, in_bytes(JavaThread::tlab_end_offset())));
    ldr(rscratch1, Address(rthread, in_bytes(JavaThread::tlab_top_offset())));
    cmp(rscratch2, rscratch1);
    br(Assembler::HS, ok);
    STOP("assert(top <= end)");
    should_not_reach_here();

    bind(ok);
    ldp(rscratch2, rscratch1, Address(post(sp, 16)));
  }
#endif
}

// Writes to stack successive pages until offset reached to check for
// stack overflow + shadow pages.  This clobbers tmp.
void MacroAssembler::bang_stack_size(Register size, Register tmp) {
  assert_different_registers(tmp, size, rscratch1);
  mov(tmp, sp);
  // Bang stack for total size given plus shadow page size.
  // Bang one page at a time because large size can bang beyond yellow and
  // red zones.
  Label loop;
  mov(rscratch1, os::vm_page_size());
  bind(loop);
  lea(tmp, Address(tmp, -os::vm_page_size()));
  subsw(size, size, rscratch1);
  str(size, Address(tmp));
  br(Assembler::GT, loop);

  // Bang down shadow pages too.
  // At this point, (tmp-0) is the last address touched, so don't
  // touch it again.  (It was touched as (tmp-pagesize) but then tmp
  // was post-decremented.)  Skip this address by starting at i=1, and
  // touch a few more pages below.  N.B.  It is important to touch all
  // the way down to and including i=StackShadowPages.
  for (int i = 0; i < (int)(JavaThread::stack_shadow_zone_size() / os::vm_page_size()) - 1; i++) {
    // this could be any sized move but this is can be a debugging crumb
    // so the bigger the better.
    lea(tmp, Address(tmp, -os::vm_page_size()));
    str(size, Address(tmp));
  }
}


// Move the address of the polling page into dest.
void MacroAssembler::get_polling_page(Register dest, address page, relocInfo::relocType rtype) {
  if (SafepointMechanism::uses_thread_local_poll()) {
    ldr(dest, Address(rthread, Thread::polling_page_offset()));
  } else {
    unsigned long off;
    adrp(dest, Address(page, rtype), off);
    assert(off == 0, "polling page must be page aligned");
  }
}

// Move the address of the polling page into r, then read the polling
// page.
address MacroAssembler::read_polling_page(Register r, address page, relocInfo::relocType rtype) {
  get_polling_page(r, page, rtype);
  return read_polling_page(r, rtype);
}

// Read the polling page.  The address of the polling page must
// already be in r.
address MacroAssembler::read_polling_page(Register r, relocInfo::relocType rtype) {
  InstructionMark im(this);
  code_section()->relocate(inst_mark(), rtype);
  ldrw(zr, Address(r, 0));
  return inst_mark();
}

void MacroAssembler::adrp(Register reg1, const Address &dest, unsigned long &byte_offset) {
  relocInfo::relocType rtype = dest.rspec().reloc()->type();
  unsigned long low_page = (unsigned long)CodeCache::low_bound() >> 12;
  unsigned long high_page = (unsigned long)(CodeCache::high_bound()-1) >> 12;
  unsigned long dest_page = (unsigned long)dest.target() >> 12;
  long offset_low = dest_page - low_page;
  long offset_high = dest_page - high_page;

  assert(is_valid_AArch64_address(dest.target()), "bad address");
  assert(dest.getMode() == Address::literal, "ADRP must be applied to a literal address");

  InstructionMark im(this);
  code_section()->relocate(inst_mark(), dest.rspec());
  // 8143067: Ensure that the adrp can reach the dest from anywhere within
  // the code cache so that if it is relocated we know it will still reach
  if (offset_high >= -(1<<20) && offset_low < (1<<20)) {
    _adrp(reg1, dest.target());
  } else {
    unsigned long target = (unsigned long)dest.target();
    unsigned long adrp_target
      = (target & 0xffffffffUL) | ((unsigned long)pc() & 0xffff00000000UL);

    _adrp(reg1, (address)adrp_target);
    movk(reg1, target >> 32, 32);
  }
  byte_offset = (unsigned long)dest.target() & 0xfff;
}

void MacroAssembler::load_byte_map_base(Register reg) {
  CardTable::CardValue* byte_map_base =
    ((CardTableBarrierSet*)(BarrierSet::barrier_set()))->card_table()->byte_map_base();

  if (is_valid_AArch64_address((address)byte_map_base)) {
    // Strictly speaking the byte_map_base isn't an address at all,
    // and it might even be negative.
    unsigned long offset;
    adrp(reg, ExternalAddress((address)byte_map_base), offset);
    // We expect offset to be zero with most collectors.
    if (offset != 0) {
      add(reg, reg, offset);
    }
  } else {
    mov(reg, (uint64_t)byte_map_base);
  }
}

void MacroAssembler::build_frame(int framesize) {
  assert(framesize > 0, "framesize must be > 0");
  if (framesize < ((1 << 9) + 2 * wordSize)) {
    sub(sp, sp, framesize);
    stp(rfp, lr, Address(sp, framesize - 2 * wordSize));
    if (PreserveFramePointer) add(rfp, sp, framesize - 2 * wordSize);
  } else {
    stp(rfp, lr, Address(pre(sp, -2 * wordSize)));
    if (PreserveFramePointer) mov(rfp, sp);
    if (framesize < ((1 << 12) + 2 * wordSize))
      sub(sp, sp, framesize - 2 * wordSize);
    else {
      mov(rscratch1, framesize - 2 * wordSize);
      sub(sp, sp, rscratch1);
    }
  }
}

void MacroAssembler::remove_frame(int framesize) {
  assert(framesize > 0, "framesize must be > 0");
  if (framesize < ((1 << 9) + 2 * wordSize)) {
    ldp(rfp, lr, Address(sp, framesize - 2 * wordSize));
    add(sp, sp, framesize);
  } else {
    if (framesize < ((1 << 12) + 2 * wordSize))
      add(sp, sp, framesize - 2 * wordSize);
    else {
      mov(rscratch1, framesize - 2 * wordSize);
      add(sp, sp, rscratch1);
    }
    ldp(rfp, lr, Address(post(sp, 2 * wordSize)));
  }
}

#ifdef COMPILER2
typedef void (MacroAssembler::* chr_insn)(Register Rt, const Address &adr);

// Search for str1 in str2 and return index or -1
void MacroAssembler::string_indexof(Register str2, Register str1,
                                    Register cnt2, Register cnt1,
                                    Register tmp1, Register tmp2,
                                    Register tmp3, Register tmp4,
                                    Register tmp5, Register tmp6,
                                    int icnt1, Register result, int ae) {
  // NOTE: tmp5, tmp6 can be zr depending on specific method version
  Label LINEARSEARCH, LINEARSTUB, LINEAR_MEDIUM, DONE, NOMATCH, MATCH;

  Register ch1 = rscratch1;
  Register ch2 = rscratch2;
  Register cnt1tmp = tmp1;
  Register cnt2tmp = tmp2;
  Register cnt1_neg = cnt1;
  Register cnt2_neg = cnt2;
  Register result_tmp = tmp4;

  bool isL = ae == StrIntrinsicNode::LL;

  bool str1_isL = ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UL;
  bool str2_isL = ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::LU;
  int str1_chr_shift = str1_isL ? 0:1;
  int str2_chr_shift = str2_isL ? 0:1;
  int str1_chr_size = str1_isL ? 1:2;
  int str2_chr_size = str2_isL ? 1:2;
  chr_insn str1_load_1chr = str1_isL ? (chr_insn)&MacroAssembler::ldrb :
                                      (chr_insn)&MacroAssembler::ldrh;
  chr_insn str2_load_1chr = str2_isL ? (chr_insn)&MacroAssembler::ldrb :
                                      (chr_insn)&MacroAssembler::ldrh;
  chr_insn load_2chr = isL ? (chr_insn)&MacroAssembler::ldrh : (chr_insn)&MacroAssembler::ldrw;
  chr_insn load_4chr = isL ? (chr_insn)&MacroAssembler::ldrw : (chr_insn)&MacroAssembler::ldr;

  // Note, inline_string_indexOf() generates checks:
  // if (substr.count > string.count) return -1;
  // if (substr.count == 0) return 0;

  // We have two strings, a source string in str2, cnt2 and a pattern string
  // in str1, cnt1. Find the 1st occurence of pattern in source or return -1.

  // For larger pattern and source we use a simplified Boyer Moore algorithm.
  // With a small pattern and source we use linear scan.

  if (icnt1 == -1) {
    sub(result_tmp, cnt2, cnt1);
    cmp(cnt1, (u1)8);             // Use Linear Scan if cnt1 < 8 || cnt1 >= 256
    br(LT, LINEARSEARCH);
    dup(v0, T16B, cnt1); // done in separate FPU pipeline. Almost no penalty
    subs(zr, cnt1, 256);
    lsr(tmp1, cnt2, 2);
    ccmp(cnt1, tmp1, 0b0000, LT); // Source must be 4 * pattern for BM
    br(GE, LINEARSTUB);
  }

// The Boyer Moore alogorithm is based on the description here:-
//
// http://en.wikipedia.org/wiki/Boyer%E2%80%93Moore_string_search_algorithm
//
// This describes and algorithm with 2 shift rules. The 'Bad Character' rule
// and the 'Good Suffix' rule.
//
// These rules are essentially heuristics for how far we can shift the
// pattern along the search string.
//
// The implementation here uses the 'Bad Character' rule only because of the
// complexity of initialisation for the 'Good Suffix' rule.
//
// This is also known as the Boyer-Moore-Horspool algorithm:-
//
// http://en.wikipedia.org/wiki/Boyer-Moore-Horspool_algorithm
//
// This particular implementation has few java-specific optimizations.
//
// #define ASIZE 256
//
//    int bm(unsigned char *x, int m, unsigned char *y, int n) {
//       int i, j;
//       unsigned c;
//       unsigned char bc[ASIZE];
//
//       /* Preprocessing */
//       for (i = 0; i < ASIZE; ++i)
//          bc[i] = m;
//       for (i = 0; i < m - 1; ) {
//          c = x[i];
//          ++i;
//          // c < 256 for Latin1 string, so, no need for branch
//          #ifdef PATTERN_STRING_IS_LATIN1
//          bc[c] = m - i;
//          #else
//          if (c < ASIZE) bc[c] = m - i;
//          #endif
//       }
//
//       /* Searching */
//       j = 0;
//       while (j <= n - m) {
//          c = y[i+j];
//          if (x[m-1] == c)
//            for (i = m - 2; i >= 0 && x[i] == y[i + j]; --i);
//          if (i < 0) return j;
//          // c < 256 for Latin1 string, so, no need for branch
//          #ifdef SOURCE_STRING_IS_LATIN1
//          // LL case: (c< 256) always true. Remove branch
//          j += bc[y[j+m-1]];
//          #endif
//          #ifndef PATTERN_STRING_IS_UTF
//          // UU case: need if (c<ASIZE) check. Skip 1 character if not.
//          if (c < ASIZE)
//            j += bc[y[j+m-1]];
//          else
//            j += 1
//          #endif
//          #ifdef PATTERN_IS_LATIN1_AND_SOURCE_IS_UTF
//          // UL case: need if (c<ASIZE) check. Skip <pattern length> if not.
//          if (c < ASIZE)
//            j += bc[y[j+m-1]];
//          else
//            j += m
//          #endif
//       }
//    }

  if (icnt1 == -1) {
    Label BCLOOP, BCSKIP, BMLOOPSTR2, BMLOOPSTR1, BMSKIP, BMADV, BMMATCH,
        BMLOOPSTR1_LASTCMP, BMLOOPSTR1_CMP, BMLOOPSTR1_AFTER_LOAD, BM_INIT_LOOP;
    Register cnt1end = tmp2;
    Register str2end = cnt2;
    Register skipch = tmp2;

    // str1 length is >=8, so, we can read at least 1 register for cases when
    // UTF->Latin1 conversion is not needed(8 LL or 4UU) and half register for
    // UL case. We'll re-read last character in inner pre-loop code to have
    // single outer pre-loop load
    const int firstStep = isL ? 7 : 3;

    const int ASIZE = 256;
    const int STORED_BYTES = 32; // amount of bytes stored per instruction
    sub(sp, sp, ASIZE);
    mov(tmp5, ASIZE/STORED_BYTES); // loop iterations
    mov(ch1, sp);
    BIND(BM_INIT_LOOP);
      stpq(v0, v0, Address(post(ch1, STORED_BYTES)));
      subs(tmp5, tmp5, 1);
      br(GT, BM_INIT_LOOP);

      sub(cnt1tmp, cnt1, 1);
      mov(tmp5, str2);
      add(str2end, str2, result_tmp, LSL, str2_chr_shift);
      sub(ch2, cnt1, 1);
      mov(tmp3, str1);
    BIND(BCLOOP);
      (this->*str1_load_1chr)(ch1, Address(post(tmp3, str1_chr_size)));
      if (!str1_isL) {
        subs(zr, ch1, ASIZE);
        br(HS, BCSKIP);
      }
      strb(ch2, Address(sp, ch1));
    BIND(BCSKIP);
      subs(ch2, ch2, 1);
      br(GT, BCLOOP);

      add(tmp6, str1, cnt1, LSL, str1_chr_shift); // address after str1
      if (str1_isL == str2_isL) {
        // load last 8 bytes (8LL/4UU symbols)
        ldr(tmp6, Address(tmp6, -wordSize));
      } else {
        ldrw(tmp6, Address(tmp6, -wordSize/2)); // load last 4 bytes(4 symbols)
        // convert Latin1 to UTF. We'll have to wait until load completed, but
        // it's still faster than per-character loads+checks
        lsr(tmp3, tmp6, BitsPerByte * (wordSize/2 - str1_chr_size)); // str1[N-1]
        ubfx(ch1, tmp6, 8, 8); // str1[N-2]
        ubfx(ch2, tmp6, 16, 8); // str1[N-3]
        andr(tmp6, tmp6, 0xFF); // str1[N-4]
        orr(ch2, ch1, ch2, LSL, 16);
        orr(tmp6, tmp6, tmp3, LSL, 48);
        orr(tmp6, tmp6, ch2, LSL, 16);
      }
    BIND(BMLOOPSTR2);
      (this->*str2_load_1chr)(skipch, Address(str2, cnt1tmp, Address::lsl(str2_chr_shift)));
      sub(cnt1tmp, cnt1tmp, firstStep); // cnt1tmp is positive here, because cnt1 >= 8
      if (str1_isL == str2_isL) {
        // re-init tmp3. It's for free because it's executed in parallel with
        // load above. Alternative is to initialize it before loop, but it'll
        // affect performance on in-order systems with 2 or more ld/st pipelines
        lsr(tmp3, tmp6, BitsPerByte * (wordSize - str1_chr_size));
      }
      if (!isL) { // UU/UL case
        lsl(ch2, cnt1tmp, 1); // offset in bytes
      }
      cmp(tmp3, skipch);
      br(NE, BMSKIP);
      ldr(ch2, Address(str2, isL ? cnt1tmp : ch2));
      mov(ch1, tmp6);
      if (isL) {
        b(BMLOOPSTR1_AFTER_LOAD);
      } else {
        sub(cnt1tmp, cnt1tmp, 1); // no need to branch for UU/UL case. cnt1 >= 8
        b(BMLOOPSTR1_CMP);
      }
    BIND(BMLOOPSTR1);
      (this->*str1_load_1chr)(ch1, Address(str1, cnt1tmp, Address::lsl(str1_chr_shift)));
      (this->*str2_load_1chr)(ch2, Address(str2, cnt1tmp, Address::lsl(str2_chr_shift)));
    BIND(BMLOOPSTR1_AFTER_LOAD);
      subs(cnt1tmp, cnt1tmp, 1);
      br(LT, BMLOOPSTR1_LASTCMP);
    BIND(BMLOOPSTR1_CMP);
      cmp(ch1, ch2);
      br(EQ, BMLOOPSTR1);
    BIND(BMSKIP);
      if (!isL) {
        // if we've met UTF symbol while searching Latin1 pattern, then we can
        // skip cnt1 symbols
        if (str1_isL != str2_isL) {
          mov(result_tmp, cnt1);
        } else {
          mov(result_tmp, 1);
        }
        subs(zr, skipch, ASIZE);
        br(HS, BMADV);
      }
      ldrb(result_tmp, Address(sp, skipch)); // load skip distance
    BIND(BMADV);
      sub(cnt1tmp, cnt1, 1);
      add(str2, str2, result_tmp, LSL, str2_chr_shift);
      cmp(str2, str2end);
      br(LE, BMLOOPSTR2);
      add(sp, sp, ASIZE);
      b(NOMATCH);
    BIND(BMLOOPSTR1_LASTCMP);
      cmp(ch1, ch2);
      br(NE, BMSKIP);
    BIND(BMMATCH);
      sub(result, str2, tmp5);
      if (!str2_isL) lsr(result, result, 1);
      add(sp, sp, ASIZE);
      b(DONE);

    BIND(LINEARSTUB);
    cmp(cnt1, (u1)16); // small patterns still should be handled by simple algorithm
    br(LT, LINEAR_MEDIUM);
    mov(result, zr);
    RuntimeAddress stub = NULL;
    if (isL) {
      stub = RuntimeAddress(StubRoutines::aarch64::string_indexof_linear_ll());
      assert(stub.target() != NULL, "string_indexof_linear_ll stub has not been generated");
    } else if (str1_isL) {
      stub = RuntimeAddress(StubRoutines::aarch64::string_indexof_linear_ul());
       assert(stub.target() != NULL, "string_indexof_linear_ul stub has not been generated");
    } else {
      stub = RuntimeAddress(StubRoutines::aarch64::string_indexof_linear_uu());
      assert(stub.target() != NULL, "string_indexof_linear_uu stub has not been generated");
    }
    trampoline_call(stub);
    b(DONE);
  }

  BIND(LINEARSEARCH);
  {
    Label DO1, DO2, DO3;

    Register str2tmp = tmp2;
    Register first = tmp3;

    if (icnt1 == -1)
    {
        Label DOSHORT, FIRST_LOOP, STR2_NEXT, STR1_LOOP, STR1_NEXT;

        cmp(cnt1, u1(str1_isL == str2_isL ? 4 : 2));
        br(LT, DOSHORT);
      BIND(LINEAR_MEDIUM);
        (this->*str1_load_1chr)(first, Address(str1));
        lea(str1, Address(str1, cnt1, Address::lsl(str1_chr_shift)));
        sub(cnt1_neg, zr, cnt1, LSL, str1_chr_shift);
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);

      BIND(FIRST_LOOP);
        (this->*str2_load_1chr)(ch2, Address(str2, cnt2_neg));
        cmp(first, ch2);
        br(EQ, STR1_LOOP);
      BIND(STR2_NEXT);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LE, FIRST_LOOP);
        b(NOMATCH);

      BIND(STR1_LOOP);
        adds(cnt1tmp, cnt1_neg, str1_chr_size);
        add(cnt2tmp, cnt2_neg, str2_chr_size);
        br(GE, MATCH);

      BIND(STR1_NEXT);
        (this->*str1_load_1chr)(ch1, Address(str1, cnt1tmp));
        (this->*str2_load_1chr)(ch2, Address(str2, cnt2tmp));
        cmp(ch1, ch2);
        br(NE, STR2_NEXT);
        adds(cnt1tmp, cnt1tmp, str1_chr_size);
        add(cnt2tmp, cnt2tmp, str2_chr_size);
        br(LT, STR1_NEXT);
        b(MATCH);

      BIND(DOSHORT);
      if (str1_isL == str2_isL) {
        cmp(cnt1, (u1)2);
        br(LT, DO1);
        br(GT, DO3);
      }
    }

    if (icnt1 == 4) {
      Label CH1_LOOP;

        (this->*load_4chr)(ch1, str1);
        sub(result_tmp, cnt2, 4);
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);

      BIND(CH1_LOOP);
        (this->*load_4chr)(ch2, Address(str2, cnt2_neg));
        cmp(ch1, ch2);
        br(EQ, MATCH);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LE, CH1_LOOP);
        b(NOMATCH);
      }

    if ((icnt1 == -1 && str1_isL == str2_isL) || icnt1 == 2) {
      Label CH1_LOOP;

      BIND(DO2);
        (this->*load_2chr)(ch1, str1);
        if (icnt1 == 2) {
          sub(result_tmp, cnt2, 2);
        }
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);
      BIND(CH1_LOOP);
        (this->*load_2chr)(ch2, Address(str2, cnt2_neg));
        cmp(ch1, ch2);
        br(EQ, MATCH);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LE, CH1_LOOP);
        b(NOMATCH);
    }

    if ((icnt1 == -1 && str1_isL == str2_isL) || icnt1 == 3) {
      Label FIRST_LOOP, STR2_NEXT, STR1_LOOP;

      BIND(DO3);
        (this->*load_2chr)(first, str1);
        (this->*str1_load_1chr)(ch1, Address(str1, 2*str1_chr_size));
        if (icnt1 == 3) {
          sub(result_tmp, cnt2, 3);
        }
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);
      BIND(FIRST_LOOP);
        (this->*load_2chr)(ch2, Address(str2, cnt2_neg));
        cmpw(first, ch2);
        br(EQ, STR1_LOOP);
      BIND(STR2_NEXT);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LE, FIRST_LOOP);
        b(NOMATCH);

      BIND(STR1_LOOP);
        add(cnt2tmp, cnt2_neg, 2*str2_chr_size);
        (this->*str2_load_1chr)(ch2, Address(str2, cnt2tmp));
        cmp(ch1, ch2);
        br(NE, STR2_NEXT);
        b(MATCH);
    }

    if (icnt1 == -1 || icnt1 == 1) {
      Label CH1_LOOP, HAS_ZERO, DO1_SHORT, DO1_LOOP;

      BIND(DO1);
        (this->*str1_load_1chr)(ch1, str1);
        cmp(cnt2, (u1)8);
        br(LT, DO1_SHORT);

        sub(result_tmp, cnt2, 8/str2_chr_size);
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);
        mov(tmp3, str2_isL ? 0x0101010101010101 : 0x0001000100010001);
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));

        if (str2_isL) {
          orr(ch1, ch1, ch1, LSL, 8);
        }
        orr(ch1, ch1, ch1, LSL, 16);
        orr(ch1, ch1, ch1, LSL, 32);
      BIND(CH1_LOOP);
        ldr(ch2, Address(str2, cnt2_neg));
        eor(ch2, ch1, ch2);
        sub(tmp1, ch2, tmp3);
        orr(tmp2, ch2, str2_isL ? 0x7f7f7f7f7f7f7f7f : 0x7fff7fff7fff7fff);
        bics(tmp1, tmp1, tmp2);
        br(NE, HAS_ZERO);
        adds(cnt2_neg, cnt2_neg, 8);
        br(LT, CH1_LOOP);

        cmp(cnt2_neg, (u1)8);
        mov(cnt2_neg, 0);
        br(LT, CH1_LOOP);
        b(NOMATCH);

      BIND(HAS_ZERO);
        rev(tmp1, tmp1);
        clz(tmp1, tmp1);
        add(cnt2_neg, cnt2_neg, tmp1, LSR, 3);
        b(MATCH);

      BIND(DO1_SHORT);
        mov(result_tmp, cnt2);
        lea(str2, Address(str2, cnt2, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, cnt2, LSL, str2_chr_shift);
      BIND(DO1_LOOP);
        (this->*str2_load_1chr)(ch2, Address(str2, cnt2_neg));
        cmpw(ch1, ch2);
        br(EQ, MATCH);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LT, DO1_LOOP);
    }
  }
  BIND(NOMATCH);
    mov(result, -1);
    b(DONE);
  BIND(MATCH);
    add(result, result_tmp, cnt2_neg, ASR, str2_chr_shift);
  BIND(DONE);
}

typedef void (MacroAssembler::* chr_insn)(Register Rt, const Address &adr);
typedef void (MacroAssembler::* uxt_insn)(Register Rd, Register Rn);

void MacroAssembler::string_indexof_char(Register str1, Register cnt1,
                                         Register ch, Register result,
                                         Register tmp1, Register tmp2, Register tmp3)
{
  Label CH1_LOOP, HAS_ZERO, DO1_SHORT, DO1_LOOP, MATCH, NOMATCH, DONE;
  Register cnt1_neg = cnt1;
  Register ch1 = rscratch1;
  Register result_tmp = rscratch2;

  cmp(cnt1, (u1)4);
  br(LT, DO1_SHORT);

  orr(ch, ch, ch, LSL, 16);
  orr(ch, ch, ch, LSL, 32);

  sub(cnt1, cnt1, 4);
  mov(result_tmp, cnt1);
  lea(str1, Address(str1, cnt1, Address::uxtw(1)));
  sub(cnt1_neg, zr, cnt1, LSL, 1);

  mov(tmp3, 0x0001000100010001);

  BIND(CH1_LOOP);
    ldr(ch1, Address(str1, cnt1_neg));
    eor(ch1, ch, ch1);
    sub(tmp1, ch1, tmp3);
    orr(tmp2, ch1, 0x7fff7fff7fff7fff);
    bics(tmp1, tmp1, tmp2);
    br(NE, HAS_ZERO);
    adds(cnt1_neg, cnt1_neg, 8);
    br(LT, CH1_LOOP);

    cmp(cnt1_neg, (u1)8);
    mov(cnt1_neg, 0);
    br(LT, CH1_LOOP);
    b(NOMATCH);

  BIND(HAS_ZERO);
    rev(tmp1, tmp1);
    clz(tmp1, tmp1);
    add(cnt1_neg, cnt1_neg, tmp1, LSR, 3);
    b(MATCH);

  BIND(DO1_SHORT);
    mov(result_tmp, cnt1);
    lea(str1, Address(str1, cnt1, Address::uxtw(1)));
    sub(cnt1_neg, zr, cnt1, LSL, 1);
  BIND(DO1_LOOP);
    ldrh(ch1, Address(str1, cnt1_neg));
    cmpw(ch, ch1);
    br(EQ, MATCH);
    adds(cnt1_neg, cnt1_neg, 2);
    br(LT, DO1_LOOP);
  BIND(NOMATCH);
    mov(result, -1);
    b(DONE);
  BIND(MATCH);
    add(result, result_tmp, cnt1_neg, ASR, 1);
  BIND(DONE);
}

// Compare strings.
void MacroAssembler::string_compare(Register str1, Register str2,
    Register cnt1, Register cnt2, Register result, Register tmp1, Register tmp2,
    FloatRegister vtmp1, FloatRegister vtmp2, FloatRegister vtmp3, int ae) {
  Label DONE, SHORT_LOOP, SHORT_STRING, SHORT_LAST, TAIL, STUB,
      DIFFERENCE, NEXT_WORD, SHORT_LOOP_TAIL, SHORT_LAST2, SHORT_LAST_INIT,
      SHORT_LOOP_START, TAIL_CHECK;

  const u1 STUB_THRESHOLD = 64 + 8;
  bool isLL = ae == StrIntrinsicNode::LL;
  bool isLU = ae == StrIntrinsicNode::LU;
  bool isUL = ae == StrIntrinsicNode::UL;

  bool str1_isL = isLL || isLU;
  bool str2_isL = isLL || isUL;

  int str1_chr_shift = str1_isL ? 0 : 1;
  int str2_chr_shift = str2_isL ? 0 : 1;
  int str1_chr_size = str1_isL ? 1 : 2;
  int str2_chr_size = str2_isL ? 1 : 2;
  int minCharsInWord = isLL ? wordSize : wordSize/2;

  FloatRegister vtmpZ = vtmp1, vtmp = vtmp2;
  chr_insn str1_load_chr = str1_isL ? (chr_insn)&MacroAssembler::ldrb :
                                      (chr_insn)&MacroAssembler::ldrh;
  chr_insn str2_load_chr = str2_isL ? (chr_insn)&MacroAssembler::ldrb :
                                      (chr_insn)&MacroAssembler::ldrh;
  uxt_insn ext_chr = isLL ? (uxt_insn)&MacroAssembler::uxtbw :
                            (uxt_insn)&MacroAssembler::uxthw;

  BLOCK_COMMENT("string_compare {");

  // Bizzarely, the counts are passed in bytes, regardless of whether they
  // are L or U strings, however the result is always in characters.
  if (!str1_isL) asrw(cnt1, cnt1, 1);
  if (!str2_isL) asrw(cnt2, cnt2, 1);

  // Compute the minimum of the string lengths and save the difference.
  subsw(result, cnt1, cnt2);
  cselw(cnt2, cnt1, cnt2, Assembler::LE); // min

  // A very short string
  cmpw(cnt2, minCharsInWord);
  br(Assembler::LE, SHORT_STRING);

  // Compare longwords
  // load first parts of strings and finish initialization while loading
  {
    if (str1_isL == str2_isL) { // LL or UU
      ldr(tmp1, Address(str1));
      cmp(str1, str2);
      br(Assembler::EQ, DONE);
      ldr(tmp2, Address(str2));
      cmp(cnt2, STUB_THRESHOLD);
      br(GE, STUB);
      subsw(cnt2, cnt2, minCharsInWord);
      br(EQ, TAIL_CHECK);
      lea(str2, Address(str2, cnt2, Address::uxtw(str2_chr_shift)));
      lea(str1, Address(str1, cnt2, Address::uxtw(str1_chr_shift)));
      sub(cnt2, zr, cnt2, LSL, str2_chr_shift);
    } else if (isLU) {
      ldrs(vtmp, Address(str1));
      cmp(str1, str2);
      br(Assembler::EQ, DONE);
      ldr(tmp2, Address(str2));
      cmp(cnt2, STUB_THRESHOLD);
      br(GE, STUB);
      subw(cnt2, cnt2, 4);
      eor(vtmpZ, T16B, vtmpZ, vtmpZ);
      lea(str1, Address(str1, cnt2, Address::uxtw(str1_chr_shift)));
      lea(str2, Address(str2, cnt2, Address::uxtw(str2_chr_shift)));
      zip1(vtmp, T8B, vtmp, vtmpZ);
      sub(cnt1, zr, cnt2, LSL, str1_chr_shift);
      sub(cnt2, zr, cnt2, LSL, str2_chr_shift);
      add(cnt1, cnt1, 4);
      fmovd(tmp1, vtmp);
    } else { // UL case
      ldr(tmp1, Address(str1));
      cmp(str1, str2);
      br(Assembler::EQ, DONE);
      ldrs(vtmp, Address(str2));
      cmp(cnt2, STUB_THRESHOLD);
      br(GE, STUB);
      subw(cnt2, cnt2, 4);
      lea(str1, Address(str1, cnt2, Address::uxtw(str1_chr_shift)));
      eor(vtmpZ, T16B, vtmpZ, vtmpZ);
      lea(str2, Address(str2, cnt2, Address::uxtw(str2_chr_shift)));
      sub(cnt1, zr, cnt2, LSL, str1_chr_shift);
      zip1(vtmp, T8B, vtmp, vtmpZ);
      sub(cnt2, zr, cnt2, LSL, str2_chr_shift);
      add(cnt1, cnt1, 8);
      fmovd(tmp2, vtmp);
    }
    adds(cnt2, cnt2, isUL ? 4 : 8);
    br(GE, TAIL);
    eor(rscratch2, tmp1, tmp2);
    cbnz(rscratch2, DIFFERENCE);
    // main loop
    bind(NEXT_WORD);
    if (str1_isL == str2_isL) {
      ldr(tmp1, Address(str1, cnt2));
      ldr(tmp2, Address(str2, cnt2));
      adds(cnt2, cnt2, 8);
    } else if (isLU) {
      ldrs(vtmp, Address(str1, cnt1));
      ldr(tmp2, Address(str2, cnt2));
      add(cnt1, cnt1, 4);
      zip1(vtmp, T8B, vtmp, vtmpZ);
      fmovd(tmp1, vtmp);
      adds(cnt2, cnt2, 8);
    } else { // UL
      ldrs(vtmp, Address(str2, cnt2));
      ldr(tmp1, Address(str1, cnt1));
      zip1(vtmp, T8B, vtmp, vtmpZ);
      add(cnt1, cnt1, 8);
      fmovd(tmp2, vtmp);
      adds(cnt2, cnt2, 4);
    }
    br(GE, TAIL);

    eor(rscratch2, tmp1, tmp2);
    cbz(rscratch2, NEXT_WORD);
    b(DIFFERENCE);
    bind(TAIL);
    eor(rscratch2, tmp1, tmp2);
    cbnz(rscratch2, DIFFERENCE);
    // Last longword.  In the case where length == 4 we compare the
    // same longword twice, but that's still faster than another
    // conditional branch.
    if (str1_isL == str2_isL) {
      ldr(tmp1, Address(str1));
      ldr(tmp2, Address(str2));
    } else if (isLU) {
      ldrs(vtmp, Address(str1));
      ldr(tmp2, Address(str2));
      zip1(vtmp, T8B, vtmp, vtmpZ);
      fmovd(tmp1, vtmp);
    } else { // UL
      ldrs(vtmp, Address(str2));
      ldr(tmp1, Address(str1));
      zip1(vtmp, T8B, vtmp, vtmpZ);
      fmovd(tmp2, vtmp);
    }
    bind(TAIL_CHECK);
    eor(rscratch2, tmp1, tmp2);
    cbz(rscratch2, DONE);

    // Find the first different characters in the longwords and
    // compute their difference.
    bind(DIFFERENCE);
    rev(rscratch2, rscratch2);
    clz(rscratch2, rscratch2);
    andr(rscratch2, rscratch2, isLL ? -8 : -16);
    lsrv(tmp1, tmp1, rscratch2);
    (this->*ext_chr)(tmp1, tmp1);
    lsrv(tmp2, tmp2, rscratch2);
    (this->*ext_chr)(tmp2, tmp2);
    subw(result, tmp1, tmp2);
    b(DONE);
  }

  bind(STUB);
    RuntimeAddress stub = NULL;
    switch(ae) {
      case StrIntrinsicNode::LL:
        stub = RuntimeAddress(StubRoutines::aarch64::compare_long_string_LL());
        break;
      case StrIntrinsicNode::UU:
        stub = RuntimeAddress(StubRoutines::aarch64::compare_long_string_UU());
        break;
      case StrIntrinsicNode::LU:
        stub = RuntimeAddress(StubRoutines::aarch64::compare_long_string_LU());
        break;
      case StrIntrinsicNode::UL:
        stub = RuntimeAddress(StubRoutines::aarch64::compare_long_string_UL());
        break;
      default:
        ShouldNotReachHere();
     }
    assert(stub.target() != NULL, "compare_long_string stub has not been generated");
    trampoline_call(stub);
    b(DONE);

  bind(SHORT_STRING);
  // Is the minimum length zero?
  cbz(cnt2, DONE);
  // arrange code to do most branches while loading and loading next characters
  // while comparing previous
  (this->*str1_load_chr)(tmp1, Address(post(str1, str1_chr_size)));
  subs(cnt2, cnt2, 1);
  br(EQ, SHORT_LAST_INIT);
  (this->*str2_load_chr)(cnt1, Address(post(str2, str2_chr_size)));
  b(SHORT_LOOP_START);
  bind(SHORT_LOOP);
  subs(cnt2, cnt2, 1);
  br(EQ, SHORT_LAST);
  bind(SHORT_LOOP_START);
  (this->*str1_load_chr)(tmp2, Address(post(str1, str1_chr_size)));
  (this->*str2_load_chr)(rscratch1, Address(post(str2, str2_chr_size)));
  cmp(tmp1, cnt1);
  br(NE, SHORT_LOOP_TAIL);
  subs(cnt2, cnt2, 1);
  br(EQ, SHORT_LAST2);
  (this->*str1_load_chr)(tmp1, Address(post(str1, str1_chr_size)));
  (this->*str2_load_chr)(cnt1, Address(post(str2, str2_chr_size)));
  cmp(tmp2, rscratch1);
  br(EQ, SHORT_LOOP);
  sub(result, tmp2, rscratch1);
  b(DONE);
  bind(SHORT_LOOP_TAIL);
  sub(result, tmp1, cnt1);
  b(DONE);
  bind(SHORT_LAST2);
  cmp(tmp2, rscratch1);
  br(EQ, DONE);
  sub(result, tmp2, rscratch1);

  b(DONE);
  bind(SHORT_LAST_INIT);
  (this->*str2_load_chr)(cnt1, Address(post(str2, str2_chr_size)));
  bind(SHORT_LAST);
  cmp(tmp1, cnt1);
  br(EQ, DONE);
  sub(result, tmp1, cnt1);

  bind(DONE);

  BLOCK_COMMENT("} string_compare");
}
#endif // COMPILER2

// This method checks if provided byte array contains byte with highest bit set.
void MacroAssembler::has_negatives(Register ary1, Register len, Register result) {
    // Simple and most common case of aligned small array which is not at the
    // end of memory page is placed here. All other cases are in stub.
    Label LOOP, END, STUB, STUB_LONG, SET_RESULT, DONE;
    const uint64_t UPPER_BIT_MASK=0x8080808080808080;
    assert_different_registers(ary1, len, result);

    cmpw(len, 0);
    br(LE, SET_RESULT);
    cmpw(len, 4 * wordSize);
    br(GE, STUB_LONG); // size > 32 then go to stub

    int shift = 64 - exact_log2(os::vm_page_size());
    lsl(rscratch1, ary1, shift);
    mov(rscratch2, (size_t)(4 * wordSize) << shift);
    adds(rscratch2, rscratch1, rscratch2);  // At end of page?
    br(CS, STUB); // at the end of page then go to stub
    subs(len, len, wordSize);
    br(LT, END);

  BIND(LOOP);
    ldr(rscratch1, Address(post(ary1, wordSize)));
    tst(rscratch1, UPPER_BIT_MASK);
    br(NE, SET_RESULT);
    subs(len, len, wordSize);
    br(GE, LOOP);
    cmpw(len, -wordSize);
    br(EQ, SET_RESULT);

  BIND(END);
    ldr(result, Address(ary1));
    sub(len, zr, len, LSL, 3); // LSL 3 is to get bits from bytes
    lslv(result, result, len);
    tst(result, UPPER_BIT_MASK);
    b(SET_RESULT);

  BIND(STUB);
    RuntimeAddress has_neg =  RuntimeAddress(StubRoutines::aarch64::has_negatives());
    assert(has_neg.target() != NULL, "has_negatives stub has not been generated");
    trampoline_call(has_neg);
    b(DONE);

  BIND(STUB_LONG);
    RuntimeAddress has_neg_long =  RuntimeAddress(
            StubRoutines::aarch64::has_negatives_long());
    assert(has_neg_long.target() != NULL, "has_negatives stub has not been generated");
    trampoline_call(has_neg_long);
    b(DONE);

  BIND(SET_RESULT);
    cset(result, NE); // set true or false

  BIND(DONE);
}

void MacroAssembler::arrays_equals(Register a1, Register a2, Register tmp3,
                                   Register tmp4, Register tmp5, Register result,
                                   Register cnt1, int elem_size) {
  Label DONE, SAME;
  Register tmp1 = rscratch1;
  Register tmp2 = rscratch2;
  Register cnt2 = tmp2;  // cnt2 only used in array length compare
  int elem_per_word = wordSize/elem_size;
  int log_elem_size = exact_log2(elem_size);
  int length_offset = arrayOopDesc::length_offset_in_bytes();
  int base_offset
    = arrayOopDesc::base_offset_in_bytes(elem_size == 2 ? T_CHAR : T_BYTE);
  int stubBytesThreshold = 3 * 64 + (UseSIMDForArrayEquals ? 0 : 16);

  assert(elem_size == 1 || elem_size == 2, "must be char or byte");
  assert_different_registers(a1, a2, result, cnt1, rscratch1, rscratch2);

#ifndef PRODUCT
  {
    const char kind = (elem_size == 2) ? 'U' : 'L';
    char comment[64];
    snprintf(comment, sizeof comment, "array_equals%c{", kind);
    BLOCK_COMMENT(comment);
  }
#endif

  // if (a1 == a2)
  //     return true;
  cmpoop(a1, a2); // May have read barriers for a1 and a2.
  br(EQ, SAME);

  if (UseSimpleArrayEquals) {
    Label NEXT_WORD, SHORT, TAIL03, TAIL01, A_MIGHT_BE_NULL, A_IS_NOT_NULL;
    // if (a1 == null || a2 == null)
    //     return false;
    // a1 & a2 == 0 means (some-pointer is null) or
    // (very-rare-or-even-probably-impossible-pointer-values)
    // so, we can save one branch in most cases
    tst(a1, a2);
    mov(result, false);
    br(EQ, A_MIGHT_BE_NULL);
    // if (a1.length != a2.length)
    //      return false;
    bind(A_IS_NOT_NULL);
    ldrw(cnt1, Address(a1, length_offset));
    ldrw(cnt2, Address(a2, length_offset));
    eorw(tmp5, cnt1, cnt2);
    cbnzw(tmp5, DONE);
    lea(a1, Address(a1, base_offset));
    lea(a2, Address(a2, base_offset));
    // Check for short strings, i.e. smaller than wordSize.
    subs(cnt1, cnt1, elem_per_word);
    br(Assembler::LT, SHORT);
    // Main 8 byte comparison loop.
    bind(NEXT_WORD); {
      ldr(tmp1, Address(post(a1, wordSize)));
      ldr(tmp2, Address(post(a2, wordSize)));
      subs(cnt1, cnt1, elem_per_word);
      eor(tmp5, tmp1, tmp2);
      cbnz(tmp5, DONE);
    } br(GT, NEXT_WORD);
    // Last longword.  In the case where length == 4 we compare the
    // same longword twice, but that's still faster than another
    // conditional branch.
    // cnt1 could be 0, -1, -2, -3, -4 for chars; -4 only happens when
    // length == 4.
    if (log_elem_size > 0)
      lsl(cnt1, cnt1, log_elem_size);
    ldr(tmp3, Address(a1, cnt1));
    ldr(tmp4, Address(a2, cnt1));
    eor(tmp5, tmp3, tmp4);
    cbnz(tmp5, DONE);
    b(SAME);
    bind(A_MIGHT_BE_NULL);
    // in case both a1 and a2 are not-null, proceed with loads
    cbz(a1, DONE);
    cbz(a2, DONE);
    b(A_IS_NOT_NULL);
    bind(SHORT);

    tbz(cnt1, 2 - log_elem_size, TAIL03); // 0-7 bytes left.
    {
      ldrw(tmp1, Address(post(a1, 4)));
      ldrw(tmp2, Address(post(a2, 4)));
      eorw(tmp5, tmp1, tmp2);
      cbnzw(tmp5, DONE);
    }
    bind(TAIL03);
    tbz(cnt1, 1 - log_elem_size, TAIL01); // 0-3 bytes left.
    {
      ldrh(tmp3, Address(post(a1, 2)));
      ldrh(tmp4, Address(post(a2, 2)));
      eorw(tmp5, tmp3, tmp4);
      cbnzw(tmp5, DONE);
    }
    bind(TAIL01);
    if (elem_size == 1) { // Only needed when comparing byte arrays.
      tbz(cnt1, 0, SAME); // 0-1 bytes left.
      {
        ldrb(tmp1, a1);
        ldrb(tmp2, a2);
        eorw(tmp5, tmp1, tmp2);
        cbnzw(tmp5, DONE);
      }
    }
  } else {
    Label NEXT_DWORD, SHORT, TAIL, TAIL2, STUB, EARLY_OUT,
        CSET_EQ, LAST_CHECK;
    mov(result, false);
    cbz(a1, DONE);
    ldrw(cnt1, Address(a1, length_offset));
    cbz(a2, DONE);
    ldrw(cnt2, Address(a2, length_offset));
    // on most CPUs a2 is still "locked"(surprisingly) in ldrw and it's
    // faster to perform another branch before comparing a1 and a2
    cmp(cnt1, (u1)elem_per_word);
    br(LE, SHORT); // short or same
    ldr(tmp3, Address(pre(a1, base_offset)));
    subs(zr, cnt1, stubBytesThreshold);
    br(GE, STUB);
    ldr(tmp4, Address(pre(a2, base_offset)));
    sub(tmp5, zr, cnt1, LSL, 3 + log_elem_size);
    cmp(cnt2, cnt1);
    br(NE, DONE);

    // Main 16 byte comparison loop with 2 exits
    bind(NEXT_DWORD); {
      ldr(tmp1, Address(pre(a1, wordSize)));
      ldr(tmp2, Address(pre(a2, wordSize)));
      subs(cnt1, cnt1, 2 * elem_per_word);
      br(LE, TAIL);
      eor(tmp4, tmp3, tmp4);
      cbnz(tmp4, DONE);
      ldr(tmp3, Address(pre(a1, wordSize)));
      ldr(tmp4, Address(pre(a2, wordSize)));
      cmp(cnt1, (u1)elem_per_word);
      br(LE, TAIL2);
      cmp(tmp1, tmp2);
    } br(EQ, NEXT_DWORD);
    b(DONE);

    bind(TAIL);
    eor(tmp4, tmp3, tmp4);
    eor(tmp2, tmp1, tmp2);
    lslv(tmp2, tmp2, tmp5);
    orr(tmp5, tmp4, tmp2);
    cmp(tmp5, zr);
    b(CSET_EQ);

    bind(TAIL2);
    eor(tmp2, tmp1, tmp2);
    cbnz(tmp2, DONE);
    b(LAST_CHECK);

    bind(STUB);
    ldr(tmp4, Address(pre(a2, base_offset)));
    cmp(cnt2, cnt1);
    br(NE, DONE);
    if (elem_size == 2) { // convert to byte counter
      lsl(cnt1, cnt1, 1);
    }
    eor(tmp5, tmp3, tmp4);
    cbnz(tmp5, DONE);
    RuntimeAddress stub = RuntimeAddress(StubRoutines::aarch64::large_array_equals());
    assert(stub.target() != NULL, "array_equals_long stub has not been generated");
    trampoline_call(stub);
    b(DONE);

    bind(EARLY_OUT);
    // (a1 != null && a2 == null) || (a1 != null && a2 != null && a1 == a2)
    // so, if a2 == null => return false(0), else return true, so we can return a2
    mov(result, a2);
    b(DONE);
    bind(SHORT);
    cmp(cnt2, cnt1);
    br(NE, DONE);
    cbz(cnt1, SAME);
    sub(tmp5, zr, cnt1, LSL, 3 + log_elem_size);
    ldr(tmp3, Address(a1, base_offset));
    ldr(tmp4, Address(a2, base_offset));
    bind(LAST_CHECK);
    eor(tmp4, tmp3, tmp4);
    lslv(tmp5, tmp4, tmp5);
    cmp(tmp5, zr);
    bind(CSET_EQ);
    cset(result, EQ);
    b(DONE);
  }

  bind(SAME);
  mov(result, true);
  // That's it.
  bind(DONE);

  BLOCK_COMMENT("} array_equals");
}

// Compare Strings

// For Strings we're passed the address of the first characters in a1
// and a2 and the length in cnt1.
// elem_size is the element size in bytes: either 1 or 2.
// There are two implementations.  For arrays >= 8 bytes, all
// comparisons (including the final one, which may overlap) are
// performed 8 bytes at a time.  For strings < 8 bytes, we compare a
// halfword, then a short, and then a byte.

void MacroAssembler::string_equals(Register a1, Register a2,
                                   Register result, Register cnt1, int elem_size)
{
  Label SAME, DONE, SHORT, NEXT_WORD;
  Register tmp1 = rscratch1;
  Register tmp2 = rscratch2;
  Register cnt2 = tmp2;  // cnt2 only used in array length compare

  assert(elem_size == 1 || elem_size == 2, "must be 2 or 1 byte");
  assert_different_registers(a1, a2, result, cnt1, rscratch1, rscratch2);

#ifndef PRODUCT
  {
    const char kind = (elem_size == 2) ? 'U' : 'L';
    char comment[64];
    snprintf(comment, sizeof comment, "{string_equals%c", kind);
    BLOCK_COMMENT(comment);
  }
#endif

  mov(result, false);

  // Check for short strings, i.e. smaller than wordSize.
  subs(cnt1, cnt1, wordSize);
  br(Assembler::LT, SHORT);
  // Main 8 byte comparison loop.
  bind(NEXT_WORD); {
    ldr(tmp1, Address(post(a1, wordSize)));
    ldr(tmp2, Address(post(a2, wordSize)));
    subs(cnt1, cnt1, wordSize);
    eor(tmp1, tmp1, tmp2);
    cbnz(tmp1, DONE);
  } br(GT, NEXT_WORD);
  // Last longword.  In the case where length == 4 we compare the
  // same longword twice, but that's still faster than another
  // conditional branch.
  // cnt1 could be 0, -1, -2, -3, -4 for chars; -4 only happens when
  // length == 4.
  ldr(tmp1, Address(a1, cnt1));
  ldr(tmp2, Address(a2, cnt1));
  eor(tmp2, tmp1, tmp2);
  cbnz(tmp2, DONE);
  b(SAME);

  bind(SHORT);
  Label TAIL03, TAIL01;

  tbz(cnt1, 2, TAIL03); // 0-7 bytes left.
  {
    ldrw(tmp1, Address(post(a1, 4)));
    ldrw(tmp2, Address(post(a2, 4)));
    eorw(tmp1, tmp1, tmp2);
    cbnzw(tmp1, DONE);
  }
  bind(TAIL03);
  tbz(cnt1, 1, TAIL01); // 0-3 bytes left.
  {
    ldrh(tmp1, Address(post(a1, 2)));
    ldrh(tmp2, Address(post(a2, 2)));
    eorw(tmp1, tmp1, tmp2);
    cbnzw(tmp1, DONE);
  }
  bind(TAIL01);
  if (elem_size == 1) { // Only needed when comparing 1-byte elements
    tbz(cnt1, 0, SAME); // 0-1 bytes left.
    {
      ldrb(tmp1, a1);
      ldrb(tmp2, a2);
      eorw(tmp1, tmp1, tmp2);
      cbnzw(tmp1, DONE);
    }
  }
  // Arrays are equal.
  bind(SAME);
  mov(result, true);

  // That's it.
  bind(DONE);
  BLOCK_COMMENT("} string_equals");
}


// The size of the blocks erased by the zero_blocks stub.  We must
// handle anything smaller than this ourselves in zero_words().
const int MacroAssembler::zero_words_block_size = 8;

// zero_words() is used by C2 ClearArray patterns.  It is as small as
// possible, handling small word counts locally and delegating
// anything larger to the zero_blocks stub.  It is expanded many times
// in compiled code, so it is important to keep it short.

// ptr:   Address of a buffer to be zeroed.
// cnt:   Count in HeapWords.
//
// ptr, cnt, rscratch1, and rscratch2 are clobbered.
void MacroAssembler::zero_words(Register ptr, Register cnt)
{
  assert(is_power_of_2(zero_words_block_size), "adjust this");
  assert(ptr == r10 && cnt == r11, "mismatch in register usage");

  BLOCK_COMMENT("zero_words {");
  cmp(cnt, (u1)zero_words_block_size);
  Label around;
  br(LO, around);
  {
    RuntimeAddress zero_blocks =  RuntimeAddress(StubRoutines::aarch64::zero_blocks());
    assert(zero_blocks.target() != NULL, "zero_blocks stub has not been generated");
    if (StubRoutines::aarch64::complete()) {
      trampoline_call(zero_blocks);
    } else {
      bl(zero_blocks);
    }
  }
  bind(around);
  for (int i = zero_words_block_size >> 1; i > 1; i >>= 1) {
    Label l;
    tbz(cnt, exact_log2(i), l);
    for (int j = 0; j < i; j += 2) {
      stp(zr, zr, post(ptr, 16));
    }
    bind(l);
  }
  {
    Label l;
    tbz(cnt, 0, l);
    str(zr, Address(ptr));
    bind(l);
  }
  BLOCK_COMMENT("} zero_words");
}

// base:         Address of a buffer to be zeroed, 8 bytes aligned.
// cnt:          Immediate count in HeapWords.
#define SmallArraySize (18 * BytesPerLong)
void MacroAssembler::zero_words(Register base, u_int64_t cnt)
{
  BLOCK_COMMENT("zero_words {");
  int i = cnt & 1;  // store any odd word to start
  if (i) str(zr, Address(base));

  if (cnt <= SmallArraySize / BytesPerLong) {
    for (; i < (int)cnt; i += 2)
      stp(zr, zr, Address(base, i * wordSize));
  } else {
    const int unroll = 4; // Number of stp(zr, zr) instructions we'll unroll
    int remainder = cnt % (2 * unroll);
    for (; i < remainder; i += 2)
      stp(zr, zr, Address(base, i * wordSize));

    Label loop;
    Register cnt_reg = rscratch1;
    Register loop_base = rscratch2;
    cnt = cnt - remainder;
    mov(cnt_reg, cnt);
    // adjust base and prebias by -2 * wordSize so we can pre-increment
    add(loop_base, base, (remainder - 2) * wordSize);
    bind(loop);
    sub(cnt_reg, cnt_reg, 2 * unroll);
    for (i = 1; i < unroll; i++)
      stp(zr, zr, Address(loop_base, 2 * i * wordSize));
    stp(zr, zr, Address(pre(loop_base, 2 * unroll * wordSize)));
    cbnz(cnt_reg, loop);
  }
  BLOCK_COMMENT("} zero_words");
}

// Zero blocks of memory by using DC ZVA.
//
// Aligns the base address first sufficently for DC ZVA, then uses
// DC ZVA repeatedly for every full block.  cnt is the size to be
// zeroed in HeapWords.  Returns the count of words left to be zeroed
// in cnt.
//
// NOTE: This is intended to be used in the zero_blocks() stub.  If
// you want to use it elsewhere, note that cnt must be >= 2*zva_length.
void MacroAssembler::zero_dcache_blocks(Register base, Register cnt) {
  Register tmp = rscratch1;
  Register tmp2 = rscratch2;
  int zva_length = VM_Version::zva_length();
  Label initial_table_end, loop_zva;
  Label fini;

  // Base must be 16 byte aligned. If not just return and let caller handle it
  tst(base, 0x0f);
  br(Assembler::NE, fini);
  // Align base with ZVA length.
  neg(tmp, base);
  andr(tmp, tmp, zva_length - 1);

  // tmp: the number of bytes to be filled to align the base with ZVA length.
  add(base, base, tmp);
  sub(cnt, cnt, tmp, Assembler::ASR, 3);
  adr(tmp2, initial_table_end);
  sub(tmp2, tmp2, tmp, Assembler::LSR, 2);
  br(tmp2);

  for (int i = -zva_length + 16; i < 0; i += 16)
    stp(zr, zr, Address(base, i));
  bind(initial_table_end);

  sub(cnt, cnt, zva_length >> 3);
  bind(loop_zva);
  dc(Assembler::ZVA, base);
  subs(cnt, cnt, zva_length >> 3);
  add(base, base, zva_length);
  br(Assembler::GE, loop_zva);
  add(cnt, cnt, zva_length >> 3); // count not zeroed by DC ZVA
  bind(fini);
}

// base:   Address of a buffer to be filled, 8 bytes aligned.
// cnt:    Count in 8-byte unit.
// value:  Value to be filled with.
// base will point to the end of the buffer after filling.
void MacroAssembler::fill_words(Register base, Register cnt, Register value)
{
//  Algorithm:
//
//    scratch1 = cnt & 7;
//    cnt -= scratch1;
//    p += scratch1;
//    switch (scratch1) {
//      do {
//        cnt -= 8;
//          p[-8] = v;
//        case 7:
//          p[-7] = v;
//        case 6:
//          p[-6] = v;
//          // ...
//        case 1:
//          p[-1] = v;
//        case 0:
//          p += 8;
//      } while (cnt);
//    }

  assert_different_registers(base, cnt, value, rscratch1, rscratch2);

  Label fini, skip, entry, loop;
  const int unroll = 8; // Number of stp instructions we'll unroll

  cbz(cnt, fini);
  tbz(base, 3, skip);
  str(value, Address(post(base, 8)));
  sub(cnt, cnt, 1);
  bind(skip);

  andr(rscratch1, cnt, (unroll-1) * 2);
  sub(cnt, cnt, rscratch1);
  add(base, base, rscratch1, Assembler::LSL, 3);
  adr(rscratch2, entry);
  sub(rscratch2, rscratch2, rscratch1, Assembler::LSL, 1);
  br(rscratch2);

  bind(loop);
  add(base, base, unroll * 16);
  for (int i = -unroll; i < 0; i++)
    stp(value, value, Address(base, i * 16));
  bind(entry);
  subs(cnt, cnt, unroll * 2);
  br(Assembler::GE, loop);

  tbz(cnt, 0, fini);
  str(value, Address(post(base, 8)));
  bind(fini);
}

// Intrinsic for sun/nio/cs/ISO_8859_1$Encoder.implEncodeISOArray and
// java/lang/StringUTF16.compress.
void MacroAssembler::encode_iso_array(Register src, Register dst,
                      Register len, Register result,
                      FloatRegister Vtmp1, FloatRegister Vtmp2,
                      FloatRegister Vtmp3, FloatRegister Vtmp4)
{
    Label DONE, SET_RESULT, NEXT_32, NEXT_32_PRFM, LOOP_8, NEXT_8, LOOP_1, NEXT_1,
        NEXT_32_START, NEXT_32_PRFM_START;
    Register tmp1 = rscratch1, tmp2 = rscratch2;

      mov(result, len); // Save initial len

#ifndef BUILTIN_SIM
      cmp(len, (u1)8); // handle shortest strings first
      br(LT, LOOP_1);
      cmp(len, (u1)32);
      br(LT, NEXT_8);
      // The following code uses the SIMD 'uzp1' and 'uzp2' instructions
      // to convert chars to bytes
      if (SoftwarePrefetchHintDistance >= 0) {
        ld1(Vtmp1, Vtmp2, Vtmp3, Vtmp4, T8H, src);
        subs(tmp2, len, SoftwarePrefetchHintDistance/2 + 16);
        br(LE, NEXT_32_START);
        b(NEXT_32_PRFM_START);
        BIND(NEXT_32_PRFM);
          ld1(Vtmp1, Vtmp2, Vtmp3, Vtmp4, T8H, src);
        BIND(NEXT_32_PRFM_START);
          prfm(Address(src, SoftwarePrefetchHintDistance));
          orr(v4, T16B, Vtmp1, Vtmp2);
          orr(v5, T16B, Vtmp3, Vtmp4);
          uzp1(Vtmp1, T16B, Vtmp1, Vtmp2);
          uzp1(Vtmp3, T16B, Vtmp3, Vtmp4);
          uzp2(v5, T16B, v4, v5); // high bytes
          umov(tmp2, v5, D, 1);
          fmovd(tmp1, v5);
          orr(tmp1, tmp1, tmp2);
          cbnz(tmp1, LOOP_8);
          stpq(Vtmp1, Vtmp3, dst);
          sub(len, len, 32);
          add(dst, dst, 32);
          add(src, src, 64);
          subs(tmp2, len, SoftwarePrefetchHintDistance/2 + 16);
          br(GE, NEXT_32_PRFM);
          cmp(len, (u1)32);
          br(LT, LOOP_8);
        BIND(NEXT_32);
          ld1(Vtmp1, Vtmp2, Vtmp3, Vtmp4, T8H, src);
        BIND(NEXT_32_START);
      } else {
        BIND(NEXT_32);
          ld1(Vtmp1, Vtmp2, Vtmp3, Vtmp4, T8H, src);
      }
      prfm(Address(src, SoftwarePrefetchHintDistance));
      uzp1(v4, T16B, Vtmp1, Vtmp2);
      uzp1(v5, T16B, Vtmp3, Vtmp4);
      orr(Vtmp1, T16B, Vtmp1, Vtmp2);
      orr(Vtmp3, T16B, Vtmp3, Vtmp4);
      uzp2(Vtmp1, T16B, Vtmp1, Vtmp3); // high bytes
      umov(tmp2, Vtmp1, D, 1);
      fmovd(tmp1, Vtmp1);
      orr(tmp1, tmp1, tmp2);
      cbnz(tmp1, LOOP_8);
      stpq(v4, v5, dst);
      sub(len, len, 32);
      add(dst, dst, 32);
      add(src, src, 64);
      cmp(len, (u1)32);
      br(GE, NEXT_32);
      cbz(len, DONE);

    BIND(LOOP_8);
      cmp(len, (u1)8);
      br(LT, LOOP_1);
    BIND(NEXT_8);
      ld1(Vtmp1, T8H, src);
      uzp1(Vtmp2, T16B, Vtmp1, Vtmp1); // low bytes
      uzp2(Vtmp3, T16B, Vtmp1, Vtmp1); // high bytes
      fmovd(tmp1, Vtmp3);
      cbnz(tmp1, NEXT_1);
      strd(Vtmp2, dst);

      sub(len, len, 8);
      add(dst, dst, 8);
      add(src, src, 16);
      cmp(len, (u1)8);
      br(GE, NEXT_8);

    BIND(LOOP_1);
#endif
    cbz(len, DONE);
    BIND(NEXT_1);
      ldrh(tmp1, Address(post(src, 2)));
      tst(tmp1, 0xff00);
      br(NE, SET_RESULT);
      strb(tmp1, Address(post(dst, 1)));
      subs(len, len, 1);
      br(GT, NEXT_1);

    BIND(SET_RESULT);
      sub(result, result, len); // Return index where we stopped
                                // Return len == 0 if we processed all
                                // characters
    BIND(DONE);
}


// Inflate byte[] array to char[].
void MacroAssembler::byte_array_inflate(Register src, Register dst, Register len,
                                        FloatRegister vtmp1, FloatRegister vtmp2, FloatRegister vtmp3,
                                        Register tmp4) {
  Label big, done, after_init, to_stub;

  assert_different_registers(src, dst, len, tmp4, rscratch1);

  fmovd(vtmp1, zr);
  lsrw(tmp4, len, 3);
  bind(after_init);
  cbnzw(tmp4, big);
  // Short string: less than 8 bytes.
  {
    Label loop, tiny;

    cmpw(len, 4);
    br(LT, tiny);
    // Use SIMD to do 4 bytes.
    ldrs(vtmp2, post(src, 4));
    zip1(vtmp3, T8B, vtmp2, vtmp1);
    subw(len, len, 4);
    strd(vtmp3, post(dst, 8));

    cbzw(len, done);

    // Do the remaining bytes by steam.
    bind(loop);
    ldrb(tmp4, post(src, 1));
    strh(tmp4, post(dst, 2));
    subw(len, len, 1);

    bind(tiny);
    cbnz(len, loop);

    b(done);
  }

  if (SoftwarePrefetchHintDistance >= 0) {
    bind(to_stub);
      RuntimeAddress stub =  RuntimeAddress(StubRoutines::aarch64::large_byte_array_inflate());
      assert(stub.target() != NULL, "large_byte_array_inflate stub has not been generated");
      trampoline_call(stub);
      b(after_init);
  }

  // Unpack the bytes 8 at a time.
  bind(big);
  {
    Label loop, around, loop_last, loop_start;

    if (SoftwarePrefetchHintDistance >= 0) {
      const int large_loop_threshold = (64 + 16)/8;
      ldrd(vtmp2, post(src, 8));
      andw(len, len, 7);
      cmp(tmp4, (u1)large_loop_threshold);
      br(GE, to_stub);
      b(loop_start);

      bind(loop);
      ldrd(vtmp2, post(src, 8));
      bind(loop_start);
      subs(tmp4, tmp4, 1);
      br(EQ, loop_last);
      zip1(vtmp2, T16B, vtmp2, vtmp1);
      ldrd(vtmp3, post(src, 8));
      st1(vtmp2, T8H, post(dst, 16));
      subs(tmp4, tmp4, 1);
      zip1(vtmp3, T16B, vtmp3, vtmp1);
      st1(vtmp3, T8H, post(dst, 16));
      br(NE, loop);
      b(around);
      bind(loop_last);
      zip1(vtmp2, T16B, vtmp2, vtmp1);
      st1(vtmp2, T8H, post(dst, 16));
      bind(around);
      cbz(len, done);
    } else {
      andw(len, len, 7);
      bind(loop);
      ldrd(vtmp2, post(src, 8));
      sub(tmp4, tmp4, 1);
      zip1(vtmp3, T16B, vtmp2, vtmp1);
      st1(vtmp3, T8H, post(dst, 16));
      cbnz(tmp4, loop);
    }
  }

  // Do the tail of up to 8 bytes.
  add(src, src, len);
  ldrd(vtmp3, Address(src, -8));
  add(dst, dst, len, ext::uxtw, 1);
  zip1(vtmp3, T16B, vtmp3, vtmp1);
  strq(vtmp3, Address(dst, -16));

  bind(done);
}

// Compress char[] array to byte[].
void MacroAssembler::char_array_compress(Register src, Register dst, Register len,
                                         FloatRegister tmp1Reg, FloatRegister tmp2Reg,
                                         FloatRegister tmp3Reg, FloatRegister tmp4Reg,
                                         Register result) {
  encode_iso_array(src, dst, len, result,
                   tmp1Reg, tmp2Reg, tmp3Reg, tmp4Reg);
  cmp(len, zr);
  csel(result, result, zr, EQ);
}

// get_thread() can be called anywhere inside generated code so we
// need to save whatever non-callee save context might get clobbered
// by the call to JavaThread::aarch64_get_thread_helper() or, indeed,
// the call setup code.
//
// aarch64_get_thread_helper() clobbers only r0, r1, and flags.
//
void MacroAssembler::get_thread(Register dst) {
  RegSet saved_regs = RegSet::range(r0, r1) + lr - dst;
  push(saved_regs, sp);

  mov(lr, CAST_FROM_FN_PTR(address, JavaThread::aarch64_get_thread_helper));
  blrt(lr, 1, 0, 1);
  if (dst != c_rarg0) {
    mov(dst, c_rarg0);
  }

  pop(saved_regs, sp);
}
