/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2015, Red Hat Inc. All rights reserved.
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
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "interpreter/interpreter.hpp"

#include "compiler/disassembler.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_aarch64.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "opto/compile.hpp"
#include "opto/node.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/icache.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/sharedRuntime.hpp"

#if INCLUDE_ALL_GCS
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#include "gc/g1/heapRegion.hpp"
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

      // We handle 3 types of PC relative addressing
      //   1 - adrp    Rx, target_page
      //       ldr/str Ry, [Rx, #offset_in_page]
      //   2 - adrp    Rx, target_page
      //       add     Ry, Rx, #offset_in_page
      //   3 - adrp    Rx, target_page (page aligned reloc, offset == 0)
      // In the first 2 cases we must check that Rx is the same in the adrp and the
      // subsequent ldr/str or add instruction. Otherwise we could accidentally end
      // up treating a type 3 relocation as a type 1 or 2 just because it happened
      // to be followed by a random unrelated ldr/str or add instruction.
      //
      // In the case of a type 3 relocation, we know that these are only generated
      // for the safepoint polling page, or for the card type byte map base so we
      // assert as much and of course that the offset is 0.
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
      } else {
        assert((jbyte *)target ==
                ((CardTableModRefBS*)(Universe::heap()->barrier_set()))->byte_map_base ||
               target == StubRoutines::crc_table_addr() ||
               (address)target == os::get_polling_page(),
               "adrp must be polling page or byte map base");
        assert(offset_lo == 0, "offset must be 0 for polling page or byte map base");
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
    narrowOop n = oopDesc::encode_heap_oop((oop)o);
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
      //   2 - adrp    Rx, target_page         ]
      //       add     Ry, Rx, #offset_in_page
      //   3 - adrp    Rx, target_page (page aligned reloc, offset == 0)
      //
      // In the first two cases  we check that the register is the same and
      // return the target_page + the offset within the page.
      // Otherwise we assume it is a page aligned relocation and return
      // the target page only. The only cases this is generated is for
      // the safepoint polling page or for the card table byte map base so
      // we assert as much.
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
        assert((jbyte *)target_page ==
                ((CardTableModRefBS*)(Universe::heap()->barrier_set()))->byte_map_base ||
               (address)target_page == os::get_polling_page(),
               "adrp must be polling page or byte map base");
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

void MacroAssembler::serialize_memory(Register thread, Register tmp) {
  dsb(Assembler::SY);
}


void MacroAssembler::reset_last_Java_frame(bool clear_fp,
                                           bool clear_pc) {
  // we must set sp to zero to clear frame
  str(zr, Address(rthread, JavaThread::last_Java_sp_offset()));
  // must clear fp, so that compiled frames are not confused; it is
  // possible that we need it only for debugging
  if (clear_fp) {
    str(zr, Address(rthread, JavaThread::last_Java_fp_offset()));
  }

  if (clear_pc) {
    str(zr, Address(rthread, JavaThread::last_Java_pc_offset()));
  }
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
  if (last_java_pc != NULL) {
    adr(scratch, last_java_pc);
  } else {
    // FIXME: This is almost never correct.  We should delete all
    // cases of set_last_Java_frame with last_java_pc=NULL and use the
    // correct return address instead.
    adr(scratch, pc());
  }

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
    set_last_Java_frame(last_java_sp, last_java_fp, (address)NULL, scratch);
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
  cmp(tmp_reg, markOopDesc::biased_lock_pattern);
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
    cmpxchgptr(swap_reg, tmp_reg, obj_reg, rscratch1, here, slow_case);
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
    cmpxchgptr(swap_reg, tmp_reg, obj_reg, rscratch1, here, slow_case);
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
    cmpxchgptr(swap_reg, tmp_reg, obj_reg, rscratch1, here, &nope);
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
  cmp(temp_reg, markOopDesc::biased_lock_pattern);
  br(Assembler::EQ, done);
}


// added to make this compile

REGISTER_DEFINITION(Register, noreg);

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
  reset_last_Java_frame(true, true);

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
  assert(entry.rspec().type() == relocInfo::runtime_call_type
         || entry.rspec().type() == relocInfo::opt_virtual_call_type
         || entry.rspec().type() == relocInfo::static_call_type
         || entry.rspec().type() == relocInfo::virtual_call_type, "wrong reloc type");

  unsigned int start_offset = offset();
  if (far_branches() && !Compile::current()->in_scratch_emit_size()) {
    address stub = emit_trampoline_stub(start_offset, entry.target());
    if (stub == NULL) {
      return NULL; // CodeCache is full
    }
  }

  if (cbuf) cbuf->set_insts_mark();
  relocate(entry.rspec());
  if (Assembler::reachable_from_branch_at(pc(), entry.target())) {
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
  address stub = start_a_stub(Compile::MAX_stubs_size/2);
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
  return stub;
}

address MacroAssembler::ic_call(address entry) {
  RelocationHolder rh = virtual_call_Relocation::spec(pc());
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
    // reset_last_Java_frame(true, false);
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
                                             Label& L_no_such_interface) {
  assert_different_registers(recv_klass, intf_klass, method_result, scan_temp);
  assert(itable_index.is_constant() || itable_index.as_register() == method_result,
         "caller must use same register for non-constant itable index as for method");

  // Compute start of first itableOffsetEntry (which is at the end of the vtable)
  int vtable_base = InstanceKlass::vtable_start_offset() * wordSize;
  int itentry_off = itableMethodEntry::method_offset_in_bytes();
  int scan_step   = itableOffsetEntry::size() * wordSize;
  int vte_size    = vtableEntry::size() * wordSize;
  assert(vte_size == wordSize, "else adjust times_vte_scale");

  ldrw(scan_temp, Address(recv_klass, InstanceKlass::vtable_length_offset() * wordSize));

  // %%% Could store the aligned, prescaled offset in the klassoop.
  // lea(scan_temp, Address(recv_klass, scan_temp, times_vte_scale, vtable_base));
  lea(scan_temp, Address(recv_klass, scan_temp, Address::lsl(3)));
  add(scan_temp, scan_temp, vtable_base);
  if (HeapWordsPerLong > 1) {
    // Round up to align_object_offset boundary
    // see code for instanceKlass::start_of_itable!
    round_to(scan_temp, BytesPerLong);
  }

  // Adjust recv_klass by scaled itable_index, so we can free itable_index.
  assert(itableMethodEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");
  // lea(recv_klass, Address(recv_klass, itable_index, Address::times_ptr, itentry_off));
  lea(recv_klass, Address(recv_klass, itable_index, Address::lsl(3)));
  if (itentry_off)
    add(recv_klass, recv_klass, itentry_off);

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
  ldr(scan_temp, Address(scan_temp, itableOffsetEntry::offset_offset_in_bytes()));
  ldr(method_result, Address(recv_klass, scan_temp));
}

// virtual method calling
void MacroAssembler::lookup_virtual_method(Register recv_klass,
                                           RegisterOrConstant vtable_index,
                                           Register method_result) {
  const int base = InstanceKlass::vtable_start_offset() * wordSize;
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
    ldr(method_result, Address(recv_klass, vtable_offset_in_bytes));
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
    cmp(super_check_offset.as_register(), sc_offset);
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

  // Get super_klass value into r0 (even if it was in r5 or r2).
  RegSet pushed_registers;
  if (!IS_A_TEMP(r2))    pushed_registers += r2;
  if (!IS_A_TEMP(r5))    pushed_registers += r5;

  if (super_klass != r0 || UseCompressedOops) {
    if (!IS_A_TEMP(r0))   pushed_registers += r0;
  }

  push(pushed_registers, sp);

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
    snprintf(buffer, sizeof(buffer), "0x%"PRIX64, imm64);
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
    snprintf(buffer, sizeof(buffer), "0x%"PRIX64, imm64);
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
      snprintf(buffer, sizeof(buffer), "0x%"PRIX32, imm32);
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
  Label retry_load;
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
  ldrw(rscratch1, dst);
  decrementw(rscratch1, value);
  strw(rscratch1, dst);
}

void MacroAssembler::decrement(Address dst, int value)
{
  assert(!dst.uses(rscratch1), "invalid address for decrement");
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
  ldrw(rscratch1, dst);
  incrementw(rscratch1, value);
  strw(rscratch1, dst);
}

void MacroAssembler::increment(Address dst, int value)
{
  assert(!dst.uses(rscratch1), "invalid dst for address increment");
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
  // tmp returns 0/1 for success/failure
  Label retry_load, nope;

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
  if (fail)
    b(*fail);
}

void MacroAssembler::cmpxchgw(Register oldv, Register newv, Register addr, Register tmp,
                                Label &succeed, Label *fail) {
  // oldv holds comparison value
  // newv holds value to write in exchange
  // addr identifies memory word to compare against/update
  // tmp returns 0/1 for success/failure
  Label retry_load, nope;

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
  if (fail)
    b(*fail);
}

static bool different(Register a, RegisterOrConstant b, Register c) {
  if (b.is_constant())
    return a != c;
  else
    return a != b.as_register() && a != c && b.as_register() != c;
}

#define ATOMIC_OP(LDXR, OP, IOP, STXR)                                       \
void MacroAssembler::atomic_##OP(Register prev, RegisterOrConstant incr, Register addr) { \
  Register result = rscratch2;                                          \
  if (prev->is_valid())                                                 \
    result = different(prev, incr, addr) ? prev : rscratch2;            \
                                                                        \
  Label retry_load;                                                     \
  bind(retry_load);                                                     \
  LDXR(result, addr);                                                   \
  OP(rscratch1, result, incr);                                          \
  STXR(rscratch2, rscratch1, addr);                                     \
  cbnzw(rscratch2, retry_load);                                         \
  if (prev->is_valid() && prev != result) {                             \
    IOP(prev, rscratch1, incr);                                         \
  }                                                                     \
}

ATOMIC_OP(ldxr, add, sub, stxr)
ATOMIC_OP(ldxrw, addw, subw, stxrw)

#undef ATOMIC_OP

#define ATOMIC_XCHG(OP, LDXR, STXR)                                     \
void MacroAssembler::atomic_##OP(Register prev, Register newv, Register addr) { \
  Register result = rscratch2;                                          \
  if (prev->is_valid())                                                 \
    result = different(prev, newv, addr) ? prev : rscratch2;            \
                                                                        \
  Label retry_load;                                                     \
  bind(retry_load);                                                     \
  LDXR(result, addr);                                                   \
  STXR(rscratch1, newv, addr);                                          \
  cbnzw(rscratch1, retry_load);                                         \
  if (prev->is_valid() && prev != result)                               \
    mov(prev, result);                                                  \
}

ATOMIC_XCHG(xchg, ldxr, stxr)
ATOMIC_XCHG(xchgw, ldxrw, stxrw)

#undef ATOMIC_XCHG

void MacroAssembler::incr_allocated_bytes(Register thread,
                                          Register var_size_in_bytes,
                                          int con_size_in_bytes,
                                          Register t1) {
  if (!thread->is_valid()) {
    thread = rthread;
  }
  assert(t1->is_valid(), "need temp reg");

  ldr(t1, Address(thread, in_bytes(JavaThread::allocated_bytes_offset())));
  if (var_size_in_bytes->is_valid()) {
    add(t1, t1, var_size_in_bytes);
  } else {
    add(t1, t1, con_size_in_bytes);
  }
  str(t1, Address(thread, in_bytes(JavaThread::allocated_bytes_offset())));
}

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
    assert(false, err_msg("DEBUG MESSAGE: %s", msg));
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

void MacroAssembler::push_CPU_state() {
    push(0x3fffffff, sp);         // integer registers except lr & sp

    for (int i = 30; i >= 0; i -= 2)
      stpd(as_FloatRegister(i), as_FloatRegister(i+1),
           Address(pre(sp, -2 * wordSize)));
}

void MacroAssembler::pop_CPU_state() {
  for (int i = 0; i < 32; i += 2)
    ldpd(as_FloatRegister(i), as_FloatRegister(i+1),
         Address(post(sp, 2 * wordSize)));

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

    ornw(crc, zr, crc);

  if (UseCRC32) {
    Label CRC_by64_loop, CRC_by4_loop, CRC_by1_loop;

      subs(len, len, 64);
      br(Assembler::GE, CRC_by64_loop);
      adds(len, len, 64-4);
      br(Assembler::GE, CRC_by4_loop);
      adds(len, len, 4);
      br(Assembler::GT, CRC_by1_loop);
      b(L_exit);

    BIND(CRC_by4_loop);
      ldrw(tmp, Address(post(buf, 4)));
      subs(len, len, 4);
      crc32w(crc, crc, tmp);
      br(Assembler::GE, CRC_by4_loop);
      adds(len, len, 4);
      br(Assembler::LE, L_exit);
    BIND(CRC_by1_loop);
      ldrb(tmp, Address(post(buf, 1)));
      subs(len, len, 1);
      crc32b(crc, crc, tmp);
      br(Assembler::GT, CRC_by1_loop);
      b(L_exit);

      align(CodeEntryAlignment);
    BIND(CRC_by64_loop);
      subs(len, len, 64);
      ldp(tmp, tmp3, Address(post(buf, 16)));
      crc32x(crc, crc, tmp);
      crc32x(crc, crc, tmp3);
      ldp(tmp, tmp3, Address(post(buf, 16)));
      crc32x(crc, crc, tmp);
      crc32x(crc, crc, tmp3);
      ldp(tmp, tmp3, Address(post(buf, 16)));
      crc32x(crc, crc, tmp);
      crc32x(crc, crc, tmp3);
      ldp(tmp, tmp3, Address(post(buf, 16)));
      crc32x(crc, crc, tmp);
      crc32x(crc, crc, tmp3);
      br(Assembler::GE, CRC_by64_loop);
      adds(len, len, 64-4);
      br(Assembler::GE, CRC_by4_loop);
      adds(len, len, 4);
      br(Assembler::GT, CRC_by1_loop);
    BIND(L_exit);
      ornw(crc, zr, crc);
      return;
  }

    adrp(table0, ExternalAddress(StubRoutines::crc_table_addr()), offset);
    if (offset) add(table0, table0, offset);
    add(table1, table0, 1*256*sizeof(juint));
    add(table2, table0, 2*256*sizeof(juint));
    add(table3, table0, 3*256*sizeof(juint));

  if (UseNeon) {
      cmp(len, 64);
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

      uzp1(v24, v20, v22, T8H);
      uzp2(v25, v20, v22, T8H);
      eor(v20, T16B, v24, v25);

      uzp1(v26, v16, v18, T8H);
      uzp2(v27, v16, v18, T8H);
      eor(v16, T16B, v26, v27);

      ushll2(v22, T4S, v20, T8H, 8);
      ushll(v20, T4S, v20, T4H, 8);

      ushll2(v18, T4S, v16, T8H, 8);
      ushll(v16, T4S, v16, T4H, 8);

      eor(v22, T16B, v23, v22);
      eor(v18, T16B, v19, v18);
      eor(v20, T16B, v21, v20);
      eor(v16, T16B, v17, v16);

      uzp1(v17, v16, v20, T2D);
      uzp2(v21, v16, v20, T2D);
      eor(v17, T16B, v17, v21);

      ushll2(v20, T2D, v17, T4S, 16);
      ushll(v16, T2D, v17, T2S, 16);

      eor(v20, T16B, v20, v22);
      eor(v16, T16B, v16, v18);

      uzp1(v17, v20, v16, T2D);
      uzp2(v21, v20, v16, T2D);
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

      uzp1(v24, v20, v22, T8H);
      uzp2(v25, v20, v22, T8H);
      eor(v20, T16B, v24, v25);

      uzp1(v26, v16, v18, T8H);
      uzp2(v27, v16, v18, T8H);
      eor(v16, T16B, v26, v27);

      ushll2(v22, T4S, v20, T8H, 8);
      ushll(v20, T4S, v20, T4H, 8);

      ushll2(v18, T4S, v16, T8H, 8);
      ushll(v16, T4S, v16, T4H, 8);

      eor(v22, T16B, v23, v22);
      eor(v18, T16B, v19, v18);
      eor(v20, T16B, v21, v20);
      eor(v16, T16B, v17, v16);

      uzp1(v17, v16, v20, T2D);
      uzp2(v21, v16, v20, T2D);
      eor(v16, T16B, v17, v21);

      ushll2(v20, T2D, v16, T4S, 16);
      ushll(v16, T2D, v16, T2S, 16);

      eor(v20, T16B, v22, v20);
      eor(v16, T16B, v16, v18);

      uzp1(v17, v20, v16, T2D);
      uzp2(v21, v20, v16, T2D);
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
    ornw(crc, zr, crc);
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
  Label L_exit;
  Label CRC_by64_loop, CRC_by4_loop, CRC_by1_loop;

    subs(len, len, 64);
    br(Assembler::GE, CRC_by64_loop);
    adds(len, len, 64-4);
    br(Assembler::GE, CRC_by4_loop);
    adds(len, len, 4);
    br(Assembler::GT, CRC_by1_loop);
    b(L_exit);

  BIND(CRC_by4_loop);
    ldrw(tmp, Address(post(buf, 4)));
    subs(len, len, 4);
    crc32cw(crc, crc, tmp);
    br(Assembler::GE, CRC_by4_loop);
    adds(len, len, 4);
    br(Assembler::LE, L_exit);
  BIND(CRC_by1_loop);
    ldrb(tmp, Address(post(buf, 1)));
    subs(len, len, 1);
    crc32cb(crc, crc, tmp);
    br(Assembler::GT, CRC_by1_loop);
    b(L_exit);

    align(CodeEntryAlignment);
  BIND(CRC_by64_loop);
    subs(len, len, 64);
    ldp(tmp, tmp3, Address(post(buf, 16)));
    crc32cx(crc, crc, tmp);
    crc32cx(crc, crc, tmp3);
    ldp(tmp, tmp3, Address(post(buf, 16)));
    crc32cx(crc, crc, tmp);
    crc32cx(crc, crc, tmp3);
    ldp(tmp, tmp3, Address(post(buf, 16)));
    crc32cx(crc, crc, tmp);
    crc32cx(crc, crc, tmp3);
    ldp(tmp, tmp3, Address(post(buf, 16)));
    crc32cx(crc, crc, tmp);
    crc32cx(crc, crc, tmp3);
    br(Assembler::GE, CRC_by64_loop);
    adds(len, len, 64-4);
    br(Assembler::GE, CRC_by4_loop);
    adds(len, len, 4);
    br(Assembler::GT, CRC_by1_loop);
  BIND(L_exit);
    return;
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

void MacroAssembler::cmpptr(Register src1, Address src2) {
  unsigned long offset;
  adrp(rscratch1, src2, offset);
  ldr(rscratch1, Address(rscratch1, offset));
  cmp(src1, rscratch1);
}

void MacroAssembler::store_check(Register obj, Address dst) {
  store_check(obj);
}

void MacroAssembler::store_check(Register obj) {
  // Does a store check for the oop in register obj. The content of
  // register obj is destroyed afterwards.

  BarrierSet* bs = Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::CardTableForRS ||
         bs->kind() == BarrierSet::CardTableExtension,
         "Wrong barrier set kind");

  CardTableModRefBS* ct = barrier_set_cast<CardTableModRefBS>(bs);
  assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");

  lsr(obj, obj, CardTableModRefBS::card_shift);

  assert(CardTableModRefBS::dirty_card_val() == 0, "must be");

  {
    ExternalAddress cardtable((address) ct->byte_map_base);
    unsigned long offset;
    adrp(rscratch1, cardtable, offset);
    assert(offset == 0, "byte_map_base is misaligned");
  }

  if (UseCondCardMark) {
    Label L_already_dirty;
    ldrb(rscratch2,  Address(obj, rscratch1));
    cbz(rscratch2, L_already_dirty);
    strb(zr, Address(obj, rscratch1));
    bind(L_already_dirty);
  } else {
    strb(zr, Address(obj, rscratch1));
  }
}

void MacroAssembler::load_klass(Register dst, Register src) {
  if (UseCompressedClassPointers) {
    ldrw(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    decode_klass_not_null(dst);
  } else {
    ldr(dst, Address(src, oopDesc::klass_offset_in_bytes()));
  }
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

// Algorithm must match oop.inline.hpp encode_heap_oop.
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
  assert (UseCompressedOops, "should only be used for compressed oops");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (oop_recorder() != NULL, "this assembler needs an OopRecorder");

  int oop_index = oop_recorder()->find_index(obj);
  assert(Universe::heap()->is_in_reserved(JNIHandles::resolve(obj)), "should be real oop");

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

void MacroAssembler::load_heap_oop(Register dst, Address src)
{
  if (UseCompressedOops) {
    ldrw(dst, src);
    decode_heap_oop(dst);
  } else {
    ldr(dst, src);
  }
}

void MacroAssembler::load_heap_oop_not_null(Register dst, Address src)
{
  if (UseCompressedOops) {
    ldrw(dst, src);
    decode_heap_oop_not_null(dst);
  } else {
    ldr(dst, src);
  }
}

void MacroAssembler::store_heap_oop(Address dst, Register src) {
  if (UseCompressedOops) {
    assert(!dst.uses(src), "not enough registers");
    encode_heap_oop(src);
    strw(src, dst);
  } else
    str(src, dst);
}

// Used for storing NULLs.
void MacroAssembler::store_heap_oop_null(Address dst) {
  if (UseCompressedOops) {
    strw(zr, dst);
  } else
    str(zr, dst);
}

#if INCLUDE_ALL_GCS
void MacroAssembler::g1_write_barrier_pre(Register obj,
                                          Register pre_val,
                                          Register thread,
                                          Register tmp,
                                          bool tosca_live,
                                          bool expand_call) {
  // If expand_call is true then we expand the call_VM_leaf macro
  // directly to skip generating the check by
  // InterpreterMacroAssembler::call_VM_leaf_base that checks _last_sp.

  assert(thread == rthread, "must be");

  Label done;
  Label runtime;

  assert(pre_val != noreg, "check this code");

  if (obj != noreg)
    assert_different_registers(obj, pre_val, tmp);

  Address in_progress(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                       PtrQueue::byte_offset_of_active()));
  Address index(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                       PtrQueue::byte_offset_of_index()));
  Address buffer(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                       PtrQueue::byte_offset_of_buf()));


  // Is marking active?
  if (in_bytes(PtrQueue::byte_width_of_active()) == 4) {
    ldrw(tmp, in_progress);
  } else {
    assert(in_bytes(PtrQueue::byte_width_of_active()) == 1, "Assumption");
    ldrb(tmp, in_progress);
  }
  cbzw(tmp, done);

  // Do we need to load the previous value?
  if (obj != noreg) {
    load_heap_oop(pre_val, Address(obj, 0));
  }

  // Is the previous value null?
  cbz(pre_val, done);

  // Can we store original value in the thread's buffer?
  // Is index == 0?
  // (The index field is typed as size_t.)

  ldr(tmp, index);                      // tmp := *index_adr
  cbz(tmp, runtime);                    // tmp == 0?
                                        // If yes, goto runtime

  sub(tmp, tmp, wordSize);              // tmp := tmp - wordSize
  str(tmp, index);                      // *index_adr := tmp
  ldr(rscratch1, buffer);
  add(tmp, tmp, rscratch1);             // tmp := tmp + *buffer_adr

  // Record the previous value
  str(pre_val, Address(tmp, 0));
  b(done);

  bind(runtime);
  // save the live input values
  push(r0->bit(tosca_live) | obj->bit(obj != noreg) | pre_val->bit(true), sp);

  // Calling the runtime using the regular call_VM_leaf mechanism generates
  // code (generated by InterpreterMacroAssember::call_VM_leaf_base)
  // that checks that the *(rfp+frame::interpreter_frame_last_sp) == NULL.
  //
  // If we care generating the pre-barrier without a frame (e.g. in the
  // intrinsified Reference.get() routine) then ebp might be pointing to
  // the caller frame and so this check will most likely fail at runtime.
  //
  // Expanding the call directly bypasses the generation of the check.
  // So when we do not have have a full interpreter frame on the stack
  // expand_call should be passed true.

  if (expand_call) {
    assert(pre_val != c_rarg1, "smashed arg");
    pass_arg1(this, thread);
    pass_arg0(this, pre_val);
    MacroAssembler::call_VM_leaf_base(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), 2);
  } else {
    call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), pre_val, thread);
  }

  pop(r0->bit(tosca_live) | obj->bit(obj != noreg) | pre_val->bit(true), sp);

  bind(done);
}

void MacroAssembler::g1_write_barrier_post(Register store_addr,
                                           Register new_val,
                                           Register thread,
                                           Register tmp,
                                           Register tmp2) {
  assert(thread == rthread, "must be");

  Address queue_index(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                       PtrQueue::byte_offset_of_index()));
  Address buffer(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                       PtrQueue::byte_offset_of_buf()));

  BarrierSet* bs = Universe::heap()->barrier_set();
  CardTableModRefBS* ct = (CardTableModRefBS*)bs;
  assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");

  Label done;
  Label runtime;

  // Does store cross heap regions?

  eor(tmp, store_addr, new_val);
  lsr(tmp, tmp, HeapRegion::LogOfHRGrainBytes);
  cbz(tmp, done);

  // crosses regions, storing NULL?

  cbz(new_val, done);

  // storing region crossing non-NULL, is card already dirty?

  ExternalAddress cardtable((address) ct->byte_map_base);
  assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");
  const Register card_addr = tmp;

  lsr(card_addr, store_addr, CardTableModRefBS::card_shift);

  unsigned long offset;
  adrp(tmp2, cardtable, offset);

  // get the address of the card
  add(card_addr, card_addr, tmp2);
  ldrb(tmp2, Address(card_addr, offset));
  cmpw(tmp2, (int)G1SATBCardTableModRefBS::g1_young_card_val());
  br(Assembler::EQ, done);

  assert((int)CardTableModRefBS::dirty_card_val() == 0, "must be 0");

  membar(Assembler::StoreLoad);

  ldrb(tmp2, Address(card_addr, offset));
  cbzw(tmp2, done);

  // storing a region crossing, non-NULL oop, card is clean.
  // dirty card and log.

  strb(zr, Address(card_addr, offset));

  ldr(rscratch1, queue_index);
  cbz(rscratch1, runtime);
  sub(rscratch1, rscratch1, wordSize);
  str(rscratch1, queue_index);

  ldr(tmp2, buffer);
  str(card_addr, Address(tmp2, rscratch1));
  b(done);

  bind(runtime);
  // save the live input values
  push(store_addr->bit(true) | new_val->bit(true), sp);
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), card_addr, thread);
  pop(store_addr->bit(true) | new_val->bit(true), sp);

  bind(done);
}

#endif // INCLUDE_ALL_GCS

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
    oop_index = oop_recorder()->find_index(obj);
    assert(Universe::heap()->is_in_reserved(JNIHandles::resolve(obj)), "should be real oop");
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
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  assert(Universe::heap()->is_in_reserved(JNIHandles::resolve(obj)), "not an oop");
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
  assert_different_registers(obj, t2);
  assert_different_registers(obj, var_size_in_bytes);
  Register end = t2;

  // verify_tlab();

  ldr(obj, Address(rthread, JavaThread::tlab_top_offset()));
  if (var_size_in_bytes == noreg) {
    lea(end, Address(obj, con_size_in_bytes));
  } else {
    lea(end, Address(obj, var_size_in_bytes));
  }
  ldr(rscratch1, Address(rthread, JavaThread::tlab_end_offset()));
  cmp(end, rscratch1);
  br(Assembler::HI, slow_case);

  // update the tlab top pointer
  str(end, Address(rthread, JavaThread::tlab_top_offset()));

  // recover var_size_in_bytes if necessary
  if (var_size_in_bytes == end) {
    sub(var_size_in_bytes, var_size_in_bytes, obj);
  }
  // verify_tlab();
}

// Preserves r19, and r3.
Register MacroAssembler::tlab_refill(Label& retry,
                                     Label& try_eden,
                                     Label& slow_case) {
  Register top = r0;
  Register t1  = r2;
  Register t2  = r4;
  assert_different_registers(top, rthread, t1, t2, /* preserve: */ r19, r3);
  Label do_refill, discard_tlab;

  if (!Universe::heap()->supports_inline_contig_alloc()) {
    // No allocation in the shared eden.
    b(slow_case);
  }

  ldr(top, Address(rthread, in_bytes(JavaThread::tlab_top_offset())));
  ldr(t1,  Address(rthread, in_bytes(JavaThread::tlab_end_offset())));

  // calculate amount of free space
  sub(t1, t1, top);
  lsr(t1, t1, LogHeapWordSize);

  // Retain tlab and allocate object in shared space if
  // the amount free in the tlab is too large to discard.

  ldr(rscratch1, Address(rthread, in_bytes(JavaThread::tlab_refill_waste_limit_offset())));
  cmp(t1, rscratch1);
  br(Assembler::LE, discard_tlab);

  // Retain
  // ldr(rscratch1, Address(rthread, in_bytes(JavaThread::tlab_refill_waste_limit_offset())));
  mov(t2, (int32_t) ThreadLocalAllocBuffer::refill_waste_limit_increment());
  add(rscratch1, rscratch1, t2);
  str(rscratch1, Address(rthread, in_bytes(JavaThread::tlab_refill_waste_limit_offset())));

  if (TLABStats) {
    // increment number of slow_allocations
    addmw(Address(rthread, in_bytes(JavaThread::tlab_slow_allocations_offset())),
         1, rscratch1);
  }
  b(try_eden);

  bind(discard_tlab);
  if (TLABStats) {
    // increment number of refills
    addmw(Address(rthread, in_bytes(JavaThread::tlab_number_of_refills_offset())), 1,
         rscratch1);
    // accumulate wastage -- t1 is amount free in tlab
    addmw(Address(rthread, in_bytes(JavaThread::tlab_fast_refill_waste_offset())), t1,
         rscratch1);
  }

  // if tlab is currently allocated (top or end != null) then
  // fill [top, end + alignment_reserve) with array object
  cbz(top, do_refill);

  // set up the mark word
  mov(rscratch1, (intptr_t)markOopDesc::prototype()->copy_set_hash(0x2));
  str(rscratch1, Address(top, oopDesc::mark_offset_in_bytes()));
  // set the length to the remaining space
  sub(t1, t1, typeArrayOopDesc::header_size(T_INT));
  add(t1, t1, (int32_t)ThreadLocalAllocBuffer::alignment_reserve());
  lsl(t1, t1, log2_intptr(HeapWordSize/sizeof(jint)));
  strw(t1, Address(top, arrayOopDesc::length_offset_in_bytes()));
  // set klass to intArrayKlass
  {
    unsigned long offset;
    // dubious reloc why not an oop reloc?
    adrp(rscratch1, ExternalAddress((address)Universe::intArrayKlassObj_addr()),
         offset);
    ldr(t1, Address(rscratch1, offset));
  }
  // store klass last.  concurrent gcs assumes klass length is valid if
  // klass field is not null.
  store_klass(top, t1);

  mov(t1, top);
  ldr(rscratch1, Address(rthread, in_bytes(JavaThread::tlab_start_offset())));
  sub(t1, t1, rscratch1);
  incr_allocated_bytes(rthread, t1, 0, rscratch1);

  // refill the tlab with an eden allocation
  bind(do_refill);
  ldr(t1, Address(rthread, in_bytes(JavaThread::tlab_size_offset())));
  lsl(t1, t1, LogHeapWordSize);
  // allocate new tlab, address returned in top
  eden_allocate(top, t1, 0, t2, slow_case);

  // Check that t1 was preserved in eden_allocate.
#ifdef ASSERT
  if (UseTLAB) {
    Label ok;
    Register tsize = r4;
    assert_different_registers(tsize, rthread, t1);
    str(tsize, Address(pre(sp, -16)));
    ldr(tsize, Address(rthread, in_bytes(JavaThread::tlab_size_offset())));
    lsl(tsize, tsize, LogHeapWordSize);
    cmp(t1, tsize);
    br(Assembler::EQ, ok);
    STOP("assert(t1 != tlab size)");
    should_not_reach_here();

    bind(ok);
    ldr(tsize, Address(post(sp, 16)));
  }
#endif
  str(top, Address(rthread, in_bytes(JavaThread::tlab_start_offset())));
  str(top, Address(rthread, in_bytes(JavaThread::tlab_top_offset())));
  add(top, top, t1);
  sub(top, top, (int32_t)ThreadLocalAllocBuffer::alignment_reserve_in_bytes());
  str(top, Address(rthread, in_bytes(JavaThread::tlab_end_offset())));
  verify_tlab();
  b(retry);

  return rthread; // for use by caller
}

// Defines obj, preserves var_size_in_bytes
void MacroAssembler::eden_allocate(Register obj,
                                   Register var_size_in_bytes,
                                   int con_size_in_bytes,
                                   Register t1,
                                   Label& slow_case) {
  assert_different_registers(obj, var_size_in_bytes, t1);
  if (!Universe::heap()->supports_inline_contig_alloc()) {
    b(slow_case);
  } else {
    Register end = t1;
    Register heap_end = rscratch2;
    Label retry;
    bind(retry);
    {
      unsigned long offset;
      adrp(rscratch1, ExternalAddress((address) Universe::heap()->end_addr()), offset);
      ldr(heap_end, Address(rscratch1, offset));
    }

    ExternalAddress heap_top((address) Universe::heap()->top_addr());

    // Get the current top of the heap
    {
      unsigned long offset;
      adrp(rscratch1, heap_top, offset);
      // Use add() here after ARDP, rather than lea().
      // lea() does not generate anything if its offset is zero.
      // However, relocs expect to find either an ADD or a load/store
      // insn after an ADRP.  add() always generates an ADD insn, even
      // for add(Rn, Rn, 0).
      add(rscratch1, rscratch1, offset);
      ldaxr(obj, rscratch1);
    }

    // Adjust it my the size of our new object
    if (var_size_in_bytes == noreg) {
      lea(end, Address(obj, con_size_in_bytes));
    } else {
      lea(end, Address(obj, var_size_in_bytes));
    }

    // if end < obj then we wrapped around high memory
    cmp(end, obj);
    br(Assembler::LO, slow_case);

    cmp(end, heap_end);
    br(Assembler::HI, slow_case);

    // If heap_top hasn't been changed by some other thread, update it.
    stlxr(rscratch2, end, rscratch1);
    cbnzw(rscratch2, retry);
  }
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
  for (int i = 0; i< StackShadowPages-1; i++) {
    // this could be any sized move but this is can be a debugging crumb
    // so the bigger the better.
    lea(tmp, Address(tmp, -os::vm_page_size()));
    str(size, Address(tmp));
  }
}


address MacroAssembler::read_polling_page(Register r, address page, relocInfo::relocType rtype) {
  unsigned long off;
  adrp(r, Address(page, rtype), off);
  InstructionMark im(this);
  code_section()->relocate(inst_mark(), rtype);
  ldrw(zr, Address(r, off));
  return inst_mark();
}

address MacroAssembler::read_polling_page(Register r, relocInfo::relocType rtype) {
  InstructionMark im(this);
  code_section()->relocate(inst_mark(), rtype);
  ldrw(zr, Address(r, 0));
  return inst_mark();
}

void MacroAssembler::adrp(Register reg1, const Address &dest, unsigned long &byte_offset) {
  relocInfo::relocType rtype = dest.rspec().reloc()->type();
  if (uabs(pc() - dest.target()) >= (1LL << 32)) {
    guarantee(rtype == relocInfo::none
              || rtype == relocInfo::external_word_type
              || rtype == relocInfo::poll_type
              || rtype == relocInfo::poll_return_type,
              "can only use a fixed address with an ADRP");
    // Out of range.  This doesn't happen very often, but we have to
    // handle it
    mov(reg1, dest);
    byte_offset = 0;
  } else {
    InstructionMark im(this);
    code_section()->relocate(inst_mark(), dest.rspec());
    byte_offset = (uint64_t)dest.target() & 0xfff;
    _adrp(reg1, dest.target());
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


// Search for str1 in str2 and return index or -1
void MacroAssembler::string_indexof(Register str2, Register str1,
                                    Register cnt2, Register cnt1,
                                    Register tmp1, Register tmp2,
                                    Register tmp3, Register tmp4,
                                    int icnt1, Register result) {
  Label BM, LINEARSEARCH, DONE, NOMATCH, MATCH;

  Register ch1 = rscratch1;
  Register ch2 = rscratch2;
  Register cnt1tmp = tmp1;
  Register cnt2tmp = tmp2;
  Register cnt1_neg = cnt1;
  Register cnt2_neg = cnt2;
  Register result_tmp = tmp4;

  // Note, inline_string_indexOf() generates checks:
  // if (substr.count > string.count) return -1;
  // if (substr.count == 0) return 0;

// We have two strings, a source string in str2, cnt2 and a pattern string
// in str1, cnt1. Find the 1st occurence of pattern in source or return -1.

// For larger pattern and source we use a simplified Boyer Moore algorithm.
// With a small pattern and source we use linear scan.

  if (icnt1 == -1) {
    cmp(cnt1, 256);             // Use Linear Scan if cnt1 < 8 || cnt1 >= 256
    ccmp(cnt1, 8, 0b0000, LO);  // Can't handle skip >= 256 because we use
    br(LO, LINEARSEARCH);       // a byte array.
    cmp(cnt1, cnt2, LSR, 2);    // Source must be 4 * pattern for BM
    br(HS, LINEARSEARCH);
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
// #define ASIZE 128
//
//    int bm(unsigned char *x, int m, unsigned char *y, int n) {
//       int i, j;
//       unsigned c;
//       unsigned char bc[ASIZE];
//
//       /* Preprocessing */
//       for (i = 0; i < ASIZE; ++i)
//          bc[i] = 0;
//       for (i = 0; i < m - 1; ) {
//          c = x[i];
//          ++i;
//          if (c < ASIZE) bc[c] = i;
//       }
//
//       /* Searching */
//       j = 0;
//       while (j <= n - m) {
//          c = y[i+j];
//          if (x[m-1] == c)
//            for (i = m - 2; i >= 0 && x[i] == y[i + j]; --i);
//          if (i < 0) return j;
//          if (c < ASIZE)
//            j = j - bc[y[j+m-1]] + m;
//          else
//            j += 1; // Advance by 1 only if char >= ASIZE
//       }
//    }

  if (icnt1 == -1) {
    BIND(BM);

    Label ZLOOP, BCLOOP, BCSKIP, BMLOOPSTR2, BMLOOPSTR1, BMSKIP;
    Label BMADV, BMMATCH, BMCHECKEND;

    Register cnt1end = tmp2;
    Register str2end = cnt2;
    Register skipch = tmp2;

    // Restrict ASIZE to 128 to reduce stack space/initialisation.
    // The presence of chars >= ASIZE in the target string does not affect
    // performance, but we must be careful not to initialise them in the stack
    // array.
    // The presence of chars >= ASIZE in the source string may adversely affect
    // performance since we can only advance by one when we encounter one.

      stp(zr, zr, pre(sp, -128));
      for (int i = 1; i < 8; i++)
          stp(zr, zr, Address(sp, i*16));

      mov(cnt1tmp, 0);
      sub(cnt1end, cnt1, 1);
    BIND(BCLOOP);
      ldrh(ch1, Address(str1, cnt1tmp, Address::lsl(1)));
      cmp(ch1, 128);
      add(cnt1tmp, cnt1tmp, 1);
      br(HS, BCSKIP);
      strb(cnt1tmp, Address(sp, ch1));
    BIND(BCSKIP);
      cmp(cnt1tmp, cnt1end);
      br(LT, BCLOOP);

      mov(result_tmp, str2);

      sub(cnt2, cnt2, cnt1);
      add(str2end, str2, cnt2, LSL, 1);
    BIND(BMLOOPSTR2);
      sub(cnt1tmp, cnt1, 1);
      ldrh(ch1, Address(str1, cnt1tmp, Address::lsl(1)));
      ldrh(skipch, Address(str2, cnt1tmp, Address::lsl(1)));
      cmp(ch1, skipch);
      br(NE, BMSKIP);
      subs(cnt1tmp, cnt1tmp, 1);
      br(LT, BMMATCH);
    BIND(BMLOOPSTR1);
      ldrh(ch1, Address(str1, cnt1tmp, Address::lsl(1)));
      ldrh(ch2, Address(str2, cnt1tmp, Address::lsl(1)));
      cmp(ch1, ch2);
      br(NE, BMSKIP);
      subs(cnt1tmp, cnt1tmp, 1);
      br(GE, BMLOOPSTR1);
    BIND(BMMATCH);
      sub(result_tmp, str2, result_tmp);
      lsr(result, result_tmp, 1);
      add(sp, sp, 128);
      b(DONE);
    BIND(BMADV);
      add(str2, str2, 2);
      b(BMCHECKEND);
    BIND(BMSKIP);
      cmp(skipch, 128);
      br(HS, BMADV);
      ldrb(ch2, Address(sp, skipch));
      add(str2, str2, cnt1, LSL, 1);
      sub(str2, str2, ch2, LSL, 1);
    BIND(BMCHECKEND);
      cmp(str2, str2end);
      br(LE, BMLOOPSTR2);
      add(sp, sp, 128);
      b(NOMATCH);
  }

  BIND(LINEARSEARCH);
  {
    Label DO1, DO2, DO3;

    Register str2tmp = tmp2;
    Register first = tmp3;

    if (icnt1 == -1)
    {
        Label DOSHORT, FIRST_LOOP, STR2_NEXT, STR1_LOOP, STR1_NEXT, LAST_WORD;

        cmp(cnt1, 4);
        br(LT, DOSHORT);

        sub(cnt2, cnt2, cnt1);
        sub(cnt1, cnt1, 4);
        mov(result_tmp, cnt2);

        lea(str1, Address(str1, cnt1, Address::uxtw(1)));
        lea(str2, Address(str2, cnt2, Address::uxtw(1)));
        sub(cnt1_neg, zr, cnt1, LSL, 1);
        sub(cnt2_neg, zr, cnt2, LSL, 1);
        ldr(first, Address(str1, cnt1_neg));

      BIND(FIRST_LOOP);
        ldr(ch2, Address(str2, cnt2_neg));
        cmp(first, ch2);
        br(EQ, STR1_LOOP);
      BIND(STR2_NEXT);
        adds(cnt2_neg, cnt2_neg, 2);
        br(LE, FIRST_LOOP);
        b(NOMATCH);

      BIND(STR1_LOOP);
        adds(cnt1tmp, cnt1_neg, 8);
        add(cnt2tmp, cnt2_neg, 8);
        br(GE, LAST_WORD);

      BIND(STR1_NEXT);
        ldr(ch1, Address(str1, cnt1tmp));
        ldr(ch2, Address(str2, cnt2tmp));
        cmp(ch1, ch2);
        br(NE, STR2_NEXT);
        adds(cnt1tmp, cnt1tmp, 8);
        add(cnt2tmp, cnt2tmp, 8);
        br(LT, STR1_NEXT);

      BIND(LAST_WORD);
        ldr(ch1, Address(str1));
        sub(str2tmp, str2, cnt1_neg);         // adjust to corresponding
        ldr(ch2, Address(str2tmp, cnt2_neg)); // word in str2
        cmp(ch1, ch2);
        br(NE, STR2_NEXT);
        b(MATCH);

      BIND(DOSHORT);
        cmp(cnt1, 2);
        br(LT, DO1);
        br(GT, DO3);
    }

    if (icnt1 == 4) {
      Label CH1_LOOP;

        ldr(ch1, str1);
        sub(cnt2, cnt2, 4);
        mov(result_tmp, cnt2);
        lea(str2, Address(str2, cnt2, Address::uxtw(1)));
        sub(cnt2_neg, zr, cnt2, LSL, 1);

      BIND(CH1_LOOP);
        ldr(ch2, Address(str2, cnt2_neg));
        cmp(ch1, ch2);
        br(EQ, MATCH);
        adds(cnt2_neg, cnt2_neg, 2);
        br(LE, CH1_LOOP);
        b(NOMATCH);
    }

    if (icnt1 == -1 || icnt1 == 2) {
      Label CH1_LOOP;

      BIND(DO2);
        ldrw(ch1, str1);
        sub(cnt2, cnt2, 2);
        mov(result_tmp, cnt2);
        lea(str2, Address(str2, cnt2, Address::uxtw(1)));
        sub(cnt2_neg, zr, cnt2, LSL, 1);

      BIND(CH1_LOOP);
        ldrw(ch2, Address(str2, cnt2_neg));
        cmp(ch1, ch2);
        br(EQ, MATCH);
        adds(cnt2_neg, cnt2_neg, 2);
        br(LE, CH1_LOOP);
        b(NOMATCH);
    }

    if (icnt1 == -1 || icnt1 == 3) {
      Label FIRST_LOOP, STR2_NEXT, STR1_LOOP;

      BIND(DO3);
        ldrw(first, str1);
        ldrh(ch1, Address(str1, 4));

        sub(cnt2, cnt2, 3);
        mov(result_tmp, cnt2);
        lea(str2, Address(str2, cnt2, Address::uxtw(1)));
        sub(cnt2_neg, zr, cnt2, LSL, 1);

      BIND(FIRST_LOOP);
        ldrw(ch2, Address(str2, cnt2_neg));
        cmpw(first, ch2);
        br(EQ, STR1_LOOP);
      BIND(STR2_NEXT);
        adds(cnt2_neg, cnt2_neg, 2);
        br(LE, FIRST_LOOP);
        b(NOMATCH);

      BIND(STR1_LOOP);
        add(cnt2tmp, cnt2_neg, 4);
        ldrh(ch2, Address(str2, cnt2tmp));
        cmp(ch1, ch2);
        br(NE, STR2_NEXT);
        b(MATCH);
    }

    if (icnt1 == -1 || icnt1 == 1) {
      Label CH1_LOOP, HAS_ZERO;
      Label DO1_SHORT, DO1_LOOP;

      BIND(DO1);
        ldrh(ch1, str1);
        cmp(cnt2, 4);
        br(LT, DO1_SHORT);

        orr(ch1, ch1, ch1, LSL, 16);
        orr(ch1, ch1, ch1, LSL, 32);

        sub(cnt2, cnt2, 4);
        mov(result_tmp, cnt2);
        lea(str2, Address(str2, cnt2, Address::uxtw(1)));
        sub(cnt2_neg, zr, cnt2, LSL, 1);

        mov(tmp3, 0x0001000100010001);
      BIND(CH1_LOOP);
        ldr(ch2, Address(str2, cnt2_neg));
        eor(ch2, ch1, ch2);
        sub(tmp1, ch2, tmp3);
        orr(tmp2, ch2, 0x7fff7fff7fff7fff);
        bics(tmp1, tmp1, tmp2);
        br(NE, HAS_ZERO);
        adds(cnt2_neg, cnt2_neg, 8);
        br(LT, CH1_LOOP);

        cmp(cnt2_neg, 8);
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
        lea(str2, Address(str2, cnt2, Address::uxtw(1)));
        sub(cnt2_neg, zr, cnt2, LSL, 1);
      BIND(DO1_LOOP);
        ldrh(ch2, Address(str2, cnt2_neg));
        cmpw(ch1, ch2);
        br(EQ, MATCH);
        adds(cnt2_neg, cnt2_neg, 2);
        br(LT, DO1_LOOP);
    }
  }
  BIND(NOMATCH);
    mov(result, -1);
    b(DONE);
  BIND(MATCH);
    add(result, result_tmp, cnt2_neg, ASR, 1);
  BIND(DONE);
}

// Compare strings.
void MacroAssembler::string_compare(Register str1, Register str2,
                                    Register cnt1, Register cnt2, Register result,
                                    Register tmp1) {
  Label LENGTH_DIFF, DONE, SHORT_LOOP, SHORT_STRING,
    NEXT_WORD, DIFFERENCE;

  BLOCK_COMMENT("string_compare {");

  // Compute the minimum of the string lengths and save the difference.
  subsw(tmp1, cnt1, cnt2);
  cselw(cnt2, cnt1, cnt2, Assembler::LE); // min

  // A very short string
  cmpw(cnt2, 4);
  br(Assembler::LT, SHORT_STRING);

  // Check if the strings start at the same location.
  cmp(str1, str2);
  br(Assembler::EQ, LENGTH_DIFF);

  // Compare longwords
  {
    subw(cnt2, cnt2, 4); // The last longword is a special case

    // Move both string pointers to the last longword of their
    // strings, negate the remaining count, and convert it to bytes.
    lea(str1, Address(str1, cnt2, Address::uxtw(1)));
    lea(str2, Address(str2, cnt2, Address::uxtw(1)));
    sub(cnt2, zr, cnt2, LSL, 1);

    // Loop, loading longwords and comparing them into rscratch2.
    bind(NEXT_WORD);
    ldr(result, Address(str1, cnt2));
    ldr(cnt1, Address(str2, cnt2));
    adds(cnt2, cnt2, wordSize);
    eor(rscratch2, result, cnt1);
    cbnz(rscratch2, DIFFERENCE);
    br(Assembler::LT, NEXT_WORD);

    // Last longword.  In the case where length == 4 we compare the
    // same longword twice, but that's still faster than another
    // conditional branch.

    ldr(result, Address(str1));
    ldr(cnt1, Address(str2));
    eor(rscratch2, result, cnt1);
    cbz(rscratch2, LENGTH_DIFF);

    // Find the first different characters in the longwords and
    // compute their difference.
    bind(DIFFERENCE);
    rev(rscratch2, rscratch2);
    clz(rscratch2, rscratch2);
    andr(rscratch2, rscratch2, -16);
    lsrv(result, result, rscratch2);
    uxthw(result, result);
    lsrv(cnt1, cnt1, rscratch2);
    uxthw(cnt1, cnt1);
    subw(result, result, cnt1);
    b(DONE);
  }

  bind(SHORT_STRING);
  // Is the minimum length zero?
  cbz(cnt2, LENGTH_DIFF);

  bind(SHORT_LOOP);
  load_unsigned_short(result, Address(post(str1, 2)));
  load_unsigned_short(cnt1, Address(post(str2, 2)));
  subw(result, result, cnt1);
  cbnz(result, DONE);
  sub(cnt2, cnt2, 1);
  cbnz(cnt2, SHORT_LOOP);

  // Strings are equal up to min length.  Return the length difference.
  bind(LENGTH_DIFF);
  mov(result, tmp1);

  // That's it
  bind(DONE);

  BLOCK_COMMENT("} string_compare");
}


void MacroAssembler::string_equals(Register str1, Register str2,
                                   Register cnt, Register result,
                                   Register tmp1) {
  Label SAME_CHARS, DONE, SHORT_LOOP, SHORT_STRING,
    NEXT_WORD;

  const Register tmp2 = rscratch1;
  assert_different_registers(str1, str2, cnt, result, tmp1, tmp2, rscratch2);

  BLOCK_COMMENT("string_equals {");

  // Start by assuming that the strings are not equal.
  mov(result, zr);

  // A very short string
  cmpw(cnt, 4);
  br(Assembler::LT, SHORT_STRING);

  // Check if the strings start at the same location.
  cmp(str1, str2);
  br(Assembler::EQ, SAME_CHARS);

  // Compare longwords
  {
    subw(cnt, cnt, 4); // The last longword is a special case

    // Move both string pointers to the last longword of their
    // strings, negate the remaining count, and convert it to bytes.
    lea(str1, Address(str1, cnt, Address::uxtw(1)));
    lea(str2, Address(str2, cnt, Address::uxtw(1)));
    sub(cnt, zr, cnt, LSL, 1);

    // Loop, loading longwords and comparing them into rscratch2.
    bind(NEXT_WORD);
    ldr(tmp1, Address(str1, cnt));
    ldr(tmp2, Address(str2, cnt));
    adds(cnt, cnt, wordSize);
    eor(rscratch2, tmp1, tmp2);
    cbnz(rscratch2, DONE);
    br(Assembler::LT, NEXT_WORD);

    // Last longword.  In the case where length == 4 we compare the
    // same longword twice, but that's still faster than another
    // conditional branch.

    ldr(tmp1, Address(str1));
    ldr(tmp2, Address(str2));
    eor(rscratch2, tmp1, tmp2);
    cbz(rscratch2, SAME_CHARS);
    b(DONE);
  }

  bind(SHORT_STRING);
  // Is the length zero?
  cbz(cnt, SAME_CHARS);

  bind(SHORT_LOOP);
  load_unsigned_short(tmp1, Address(post(str1, 2)));
  load_unsigned_short(tmp2, Address(post(str2, 2)));
  subw(tmp1, tmp1, tmp2);
  cbnz(tmp1, DONE);
  sub(cnt, cnt, 1);
  cbnz(cnt, SHORT_LOOP);

  // Strings are equal.
  bind(SAME_CHARS);
  mov(result, true);

  // That's it
  bind(DONE);

  BLOCK_COMMENT("} string_equals");
}

// Compare char[] arrays aligned to 4 bytes
void MacroAssembler::char_arrays_equals(Register ary1, Register ary2,
                                        Register result, Register tmp1)
{
  Register cnt1 = rscratch1;
  Register cnt2 = rscratch2;
  Register tmp2 = rscratch2;

  Label SAME, DIFFER, NEXT, TAIL03, TAIL01;

  int length_offset  = arrayOopDesc::length_offset_in_bytes();
  int base_offset    = arrayOopDesc::base_offset_in_bytes(T_CHAR);

  BLOCK_COMMENT("char_arrays_equals  {");

    // different until proven equal
    mov(result, false);

    // same array?
    cmp(ary1, ary2);
    br(Assembler::EQ, SAME);

    // ne if either null
    cbz(ary1, DIFFER);
    cbz(ary2, DIFFER);

    // lengths ne?
    ldrw(cnt1, Address(ary1, length_offset));
    ldrw(cnt2, Address(ary2, length_offset));
    cmp(cnt1, cnt2);
    br(Assembler::NE, DIFFER);

    lea(ary1, Address(ary1, base_offset));
    lea(ary2, Address(ary2, base_offset));

    subs(cnt1, cnt1, 4);
    br(LT, TAIL03);

  BIND(NEXT);
    ldr(tmp1, Address(post(ary1, 8)));
    ldr(tmp2, Address(post(ary2, 8)));
    subs(cnt1, cnt1, 4);
    eor(tmp1, tmp1, tmp2);
    cbnz(tmp1, DIFFER);
    br(GE, NEXT);

  BIND(TAIL03);  // 0-3 chars left, cnt1 = #chars left - 4
    tst(cnt1, 0b10);
    br(EQ, TAIL01);
    ldrw(tmp1, Address(post(ary1, 4)));
    ldrw(tmp2, Address(post(ary2, 4)));
    cmp(tmp1, tmp2);
    br(NE, DIFFER);
  BIND(TAIL01);  // 0-1 chars left
    tst(cnt1, 0b01);
    br(EQ, SAME);
    ldrh(tmp1, ary1);
    ldrh(tmp2, ary2);
    cmp(tmp1, tmp2);
    br(NE, DIFFER);

  BIND(SAME);
    mov(result, true);
  BIND(DIFFER); // result already set

  BLOCK_COMMENT("} char_arrays_equals");
}

// encode char[] to byte[] in ISO_8859_1
void MacroAssembler::encode_iso_array(Register src, Register dst,
                      Register len, Register result,
                      FloatRegister Vtmp1, FloatRegister Vtmp2,
                      FloatRegister Vtmp3, FloatRegister Vtmp4)
{
    Label DONE, NEXT_32, LOOP_8, NEXT_8, LOOP_1, NEXT_1;
    Register tmp1 = rscratch1;

      mov(result, len); // Save initial len

#ifndef BUILTIN_SIM
      subs(len, len, 32);
      br(LT, LOOP_8);

// The following code uses the SIMD 'uqxtn' and 'uqxtn2' instructions
// to convert chars to bytes. These set the 'QC' bit in the FPSR if
// any char could not fit in a byte, so clear the FPSR so we can test it.
      clear_fpsr();

    BIND(NEXT_32);
      ld1(Vtmp1, Vtmp2, Vtmp3, Vtmp4, T8H, src);
      uqxtn(Vtmp1, T8B, Vtmp1, T8H);  // uqxtn  - write bottom half
      uqxtn(Vtmp1, T16B, Vtmp2, T8H); // uqxtn2 - write top half
      uqxtn(Vtmp2, T8B, Vtmp3, T8H);
      uqxtn(Vtmp2, T16B, Vtmp4, T8H); // uqxtn2
      get_fpsr(tmp1);
      cbnzw(tmp1, LOOP_8);
      st1(Vtmp1, Vtmp2, T16B, post(dst, 32));
      subs(len, len, 32);
      add(src, src, 64);
      br(GE, NEXT_32);

    BIND(LOOP_8);
      adds(len, len, 32-8);
      br(LT, LOOP_1);
      clear_fpsr(); // QC may be set from loop above, clear again
    BIND(NEXT_8);
      ld1(Vtmp1, T8H, src);
      uqxtn(Vtmp1, T8B, Vtmp1, T8H);
      get_fpsr(tmp1);
      cbnzw(tmp1, LOOP_1);
      st1(Vtmp1, T8B, post(dst, 8));
      subs(len, len, 8);
      add(src, src, 16);
      br(GE, NEXT_8);

    BIND(LOOP_1);
      adds(len, len, 8);
      br(LE, DONE);
#else
      cbz(len, DONE);
#endif
    BIND(NEXT_1);
      ldrh(tmp1, Address(post(src, 2)));
      tst(tmp1, 0xff00);
      br(NE, DONE);
      strb(tmp1, Address(post(dst, 1)));
      subs(len, len, 1);
      br(GT, NEXT_1);

    BIND(DONE);
      sub(result, result, len); // Return index where we stopped
}
