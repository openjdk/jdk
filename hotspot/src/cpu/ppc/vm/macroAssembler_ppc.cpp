/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2014 SAP AG. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "compiler/disassembler.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "memory/resourceArea.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1SATBCardTableModRefBS.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#endif // INCLUDE_ALL_GCS

#ifdef PRODUCT
#define BLOCK_COMMENT(str) // nothing
#else
#define BLOCK_COMMENT(str) block_comment(str)
#endif

#ifdef ASSERT
// On RISC, there's no benefit to verifying instruction boundaries.
bool AbstractAssembler::pd_check_instruction_mark() { return false; }
#endif

void MacroAssembler::ld_largeoffset_unchecked(Register d, int si31, Register a, int emit_filler_nop) {
  assert(Assembler::is_simm(si31, 31) && si31 >= 0, "si31 out of range");
  if (Assembler::is_simm(si31, 16)) {
    ld(d, si31, a);
    if (emit_filler_nop) nop();
  } else {
    const int hi = MacroAssembler::largeoffset_si16_si16_hi(si31);
    const int lo = MacroAssembler::largeoffset_si16_si16_lo(si31);
    addis(d, a, hi);
    ld(d, lo, d);
  }
}

void MacroAssembler::ld_largeoffset(Register d, int si31, Register a, int emit_filler_nop) {
  assert_different_registers(d, a);
  ld_largeoffset_unchecked(d, si31, a, emit_filler_nop);
}

void MacroAssembler::load_sized_value(Register dst, RegisterOrConstant offs, Register base,
                                      size_t size_in_bytes, bool is_signed) {
  switch (size_in_bytes) {
  case  8:              ld(dst, offs, base);                         break;
  case  4:  is_signed ? lwa(dst, offs, base) : lwz(dst, offs, base); break;
  case  2:  is_signed ? lha(dst, offs, base) : lhz(dst, offs, base); break;
  case  1:  lbz(dst, offs, base); if (is_signed) extsb(dst, dst);    break; // lba doesn't exist :(
  default:  ShouldNotReachHere();
  }
}

void MacroAssembler::store_sized_value(Register dst, RegisterOrConstant offs, Register base,
                                       size_t size_in_bytes) {
  switch (size_in_bytes) {
  case  8:  std(dst, offs, base); break;
  case  4:  stw(dst, offs, base); break;
  case  2:  sth(dst, offs, base); break;
  case  1:  stb(dst, offs, base); break;
  default:  ShouldNotReachHere();
  }
}

void MacroAssembler::align(int modulus, int max, int rem) {
  int padding = (rem + modulus - (offset() % modulus)) % modulus;
  if (padding > max) return;
  for (int c = (padding >> 2); c > 0; --c) { nop(); }
}

// Issue instructions that calculate given TOC from global TOC.
void MacroAssembler::calculate_address_from_global_toc(Register dst, address addr, bool hi16, bool lo16,
                                                       bool add_relocation, bool emit_dummy_addr) {
  int offset = -1;
  if (emit_dummy_addr) {
    offset = -128; // dummy address
  } else if (addr != (address)(intptr_t)-1) {
    offset = MacroAssembler::offset_to_global_toc(addr);
  }

  if (hi16) {
    addis(dst, R29, MacroAssembler::largeoffset_si16_si16_hi(offset));
  }
  if (lo16) {
    if (add_relocation) {
      // Relocate at the addi to avoid confusion with a load from the method's TOC.
      relocate(internal_word_Relocation::spec(addr));
    }
    addi(dst, dst, MacroAssembler::largeoffset_si16_si16_lo(offset));
  }
}

int MacroAssembler::patch_calculate_address_from_global_toc_at(address a, address bound, address addr) {
  const int offset = MacroAssembler::offset_to_global_toc(addr);

  const address inst2_addr = a;
  const int inst2 = *(int *)inst2_addr;

  // The relocation points to the second instruction, the addi,
  // and the addi reads and writes the same register dst.
  const int dst = inv_rt_field(inst2);
  assert(is_addi(inst2) && inv_ra_field(inst2) == dst, "must be addi reading and writing dst");

  // Now, find the preceding addis which writes to dst.
  int inst1 = 0;
  address inst1_addr = inst2_addr - BytesPerInstWord;
  while (inst1_addr >= bound) {
    inst1 = *(int *) inst1_addr;
    if (is_addis(inst1) && inv_rt_field(inst1) == dst) {
      // Stop, found the addis which writes dst.
      break;
    }
    inst1_addr -= BytesPerInstWord;
  }

  assert(is_addis(inst1) && inv_ra_field(inst1) == 29 /* R29 */, "source must be global TOC");
  set_imm((int *)inst1_addr, MacroAssembler::largeoffset_si16_si16_hi(offset));
  set_imm((int *)inst2_addr, MacroAssembler::largeoffset_si16_si16_lo(offset));
  return (int)((intptr_t)addr - (intptr_t)inst1_addr);
}

address MacroAssembler::get_address_of_calculate_address_from_global_toc_at(address a, address bound) {
  const address inst2_addr = a;
  const int inst2 = *(int *)inst2_addr;

  // The relocation points to the second instruction, the addi,
  // and the addi reads and writes the same register dst.
  const int dst = inv_rt_field(inst2);
  assert(is_addi(inst2) && inv_ra_field(inst2) == dst, "must be addi reading and writing dst");

  // Now, find the preceding addis which writes to dst.
  int inst1 = 0;
  address inst1_addr = inst2_addr - BytesPerInstWord;
  while (inst1_addr >= bound) {
    inst1 = *(int *) inst1_addr;
    if (is_addis(inst1) && inv_rt_field(inst1) == dst) {
      // stop, found the addis which writes dst
      break;
    }
    inst1_addr -= BytesPerInstWord;
  }

  assert(is_addis(inst1) && inv_ra_field(inst1) == 29 /* R29 */, "source must be global TOC");

  int offset = (get_imm(inst1_addr, 0) << 16) + get_imm(inst2_addr, 0);
  // -1 is a special case
  if (offset == -1) {
    return (address)(intptr_t)-1;
  } else {
    return global_toc() + offset;
  }
}

#ifdef _LP64
// Patch compressed oops or klass constants.
// Assembler sequence is
// 1) compressed oops:
//    lis  rx = const.hi
//    ori rx = rx | const.lo
// 2) compressed klass:
//    lis  rx = const.hi
//    clrldi rx = rx & 0xFFFFffff // clearMS32b, optional
//    ori rx = rx | const.lo
// Clrldi will be passed by.
int MacroAssembler::patch_set_narrow_oop(address a, address bound, narrowOop data) {
  assert(UseCompressedOops, "Should only patch compressed oops");

  const address inst2_addr = a;
  const int inst2 = *(int *)inst2_addr;

  // The relocation points to the second instruction, the ori,
  // and the ori reads and writes the same register dst.
  const int dst = inv_rta_field(inst2);
  assert(is_ori(inst2) && inv_rs_field(inst2) == dst, "must be ori reading and writing dst");
  // Now, find the preceding addis which writes to dst.
  int inst1 = 0;
  address inst1_addr = inst2_addr - BytesPerInstWord;
  bool inst1_found = false;
  while (inst1_addr >= bound) {
    inst1 = *(int *)inst1_addr;
    if (is_lis(inst1) && inv_rs_field(inst1) == dst) { inst1_found = true; break; }
    inst1_addr -= BytesPerInstWord;
  }
  assert(inst1_found, "inst is not lis");

  int xc = (data >> 16) & 0xffff;
  int xd = (data >>  0) & 0xffff;

  set_imm((int *)inst1_addr, (short)(xc)); // see enc_load_con_narrow_hi/_lo
  set_imm((int *)inst2_addr,        (xd)); // unsigned int
  return (int)((intptr_t)inst2_addr - (intptr_t)inst1_addr);
}

// Get compressed oop or klass constant.
narrowOop MacroAssembler::get_narrow_oop(address a, address bound) {
  assert(UseCompressedOops, "Should only patch compressed oops");

  const address inst2_addr = a;
  const int inst2 = *(int *)inst2_addr;

  // The relocation points to the second instruction, the ori,
  // and the ori reads and writes the same register dst.
  const int dst = inv_rta_field(inst2);
  assert(is_ori(inst2) && inv_rs_field(inst2) == dst, "must be ori reading and writing dst");
  // Now, find the preceding lis which writes to dst.
  int inst1 = 0;
  address inst1_addr = inst2_addr - BytesPerInstWord;
  bool inst1_found = false;

  while (inst1_addr >= bound) {
    inst1 = *(int *) inst1_addr;
    if (is_lis(inst1) && inv_rs_field(inst1) == dst) { inst1_found = true; break;}
    inst1_addr -= BytesPerInstWord;
  }
  assert(inst1_found, "inst is not lis");

  uint xl = ((unsigned int) (get_imm(inst2_addr, 0) & 0xffff));
  uint xh = (((get_imm(inst1_addr, 0)) & 0xffff) << 16);

  return (int) (xl | xh);
}
#endif // _LP64

void MacroAssembler::load_const_from_method_toc(Register dst, AddressLiteral& a, Register toc) {
  int toc_offset = 0;
  // Use RelocationHolder::none for the constant pool entry, otherwise
  // we will end up with a failing NativeCall::verify(x) where x is
  // the address of the constant pool entry.
  // FIXME: We should insert relocation information for oops at the constant
  // pool entries instead of inserting it at the loads; patching of a constant
  // pool entry should be less expensive.
  address oop_address = address_constant((address)a.value(), RelocationHolder::none);
  // Relocate at the pc of the load.
  relocate(a.rspec());
  toc_offset = (int)(oop_address - code()->consts()->start());
  ld_largeoffset_unchecked(dst, toc_offset, toc, true);
}

bool MacroAssembler::is_load_const_from_method_toc_at(address a) {
  const address inst1_addr = a;
  const int inst1 = *(int *)inst1_addr;

   // The relocation points to the ld or the addis.
   return (is_ld(inst1)) ||
          (is_addis(inst1) && inv_ra_field(inst1) != 0);
}

int MacroAssembler::get_offset_of_load_const_from_method_toc_at(address a) {
  assert(is_load_const_from_method_toc_at(a), "must be load_const_from_method_toc");

  const address inst1_addr = a;
  const int inst1 = *(int *)inst1_addr;

  if (is_ld(inst1)) {
    return inv_d1_field(inst1);
  } else if (is_addis(inst1)) {
    const int dst = inv_rt_field(inst1);

    // Now, find the succeeding ld which reads and writes to dst.
    address inst2_addr = inst1_addr + BytesPerInstWord;
    int inst2 = 0;
    while (true) {
      inst2 = *(int *) inst2_addr;
      if (is_ld(inst2) && inv_ra_field(inst2) == dst && inv_rt_field(inst2) == dst) {
        // Stop, found the ld which reads and writes dst.
        break;
      }
      inst2_addr += BytesPerInstWord;
    }
    return (inv_d1_field(inst1) << 16) + inv_d1_field(inst2);
  }
  ShouldNotReachHere();
  return 0;
}

// Get the constant from a `load_const' sequence.
long MacroAssembler::get_const(address a) {
  assert(is_load_const_at(a), "not a load of a constant");
  const int *p = (const int*) a;
  unsigned long x = (((unsigned long) (get_imm(a,0) & 0xffff)) << 48);
  if (is_ori(*(p+1))) {
    x |= (((unsigned long) (get_imm(a,1) & 0xffff)) << 32);
    x |= (((unsigned long) (get_imm(a,3) & 0xffff)) << 16);
    x |= (((unsigned long) (get_imm(a,4) & 0xffff)));
  } else if (is_lis(*(p+1))) {
    x |= (((unsigned long) (get_imm(a,2) & 0xffff)) << 32);
    x |= (((unsigned long) (get_imm(a,1) & 0xffff)) << 16);
    x |= (((unsigned long) (get_imm(a,3) & 0xffff)));
  } else {
    ShouldNotReachHere();
    return (long) 0;
  }
  return (long) x;
}

// Patch the 64 bit constant of a `load_const' sequence. This is a low
// level procedure. It neither flushes the instruction cache nor is it
// mt safe.
void MacroAssembler::patch_const(address a, long x) {
  assert(is_load_const_at(a), "not a load of a constant");
  int *p = (int*) a;
  if (is_ori(*(p+1))) {
    set_imm(0 + p, (x >> 48) & 0xffff);
    set_imm(1 + p, (x >> 32) & 0xffff);
    set_imm(3 + p, (x >> 16) & 0xffff);
    set_imm(4 + p, x & 0xffff);
  } else if (is_lis(*(p+1))) {
    set_imm(0 + p, (x >> 48) & 0xffff);
    set_imm(2 + p, (x >> 32) & 0xffff);
    set_imm(1 + p, (x >> 16) & 0xffff);
    set_imm(3 + p, x & 0xffff);
  } else {
    ShouldNotReachHere();
  }
}

AddressLiteral MacroAssembler::allocate_metadata_address(Metadata* obj) {
  assert(oop_recorder() != NULL, "this assembler needs a Recorder");
  int index = oop_recorder()->allocate_metadata_index(obj);
  RelocationHolder rspec = metadata_Relocation::spec(index);
  return AddressLiteral((address)obj, rspec);
}

AddressLiteral MacroAssembler::constant_metadata_address(Metadata* obj) {
  assert(oop_recorder() != NULL, "this assembler needs a Recorder");
  int index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = metadata_Relocation::spec(index);
  return AddressLiteral((address)obj, rspec);
}

AddressLiteral MacroAssembler::allocate_oop_address(jobject obj) {
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->allocate_oop_index(obj);
  return AddressLiteral(address(obj), oop_Relocation::spec(oop_index));
}

AddressLiteral MacroAssembler::constant_oop_address(jobject obj) {
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  return AddressLiteral(address(obj), oop_Relocation::spec(oop_index));
}

RegisterOrConstant MacroAssembler::delayed_value_impl(intptr_t* delayed_value_addr,
                                                      Register tmp, int offset) {
  intptr_t value = *delayed_value_addr;
  if (value != 0) {
    return RegisterOrConstant(value + offset);
  }

  // Load indirectly to solve generation ordering problem.
  // static address, no relocation
  int simm16_offset = load_const_optimized(tmp, delayed_value_addr, noreg, true);
  ld(tmp, simm16_offset, tmp); // must be aligned ((xa & 3) == 0)

  if (offset != 0) {
    addi(tmp, tmp, offset);
  }

  return RegisterOrConstant(tmp);
}

#ifndef PRODUCT
void MacroAssembler::pd_print_patched_instruction(address branch) {
  Unimplemented(); // TODO: PPC port
}
#endif // ndef PRODUCT

// Conditional far branch for destinations encodable in 24+2 bits.
void MacroAssembler::bc_far(int boint, int biint, Label& dest, int optimize) {

  // If requested by flag optimize, relocate the bc_far as a
  // runtime_call and prepare for optimizing it when the code gets
  // relocated.
  if (optimize == bc_far_optimize_on_relocate) {
    relocate(relocInfo::runtime_call_type);
  }

  // variant 2:
  //
  //    b!cxx SKIP
  //    bxx   DEST
  //  SKIP:
  //

  const int opposite_boint = add_bhint_to_boint(opposite_bhint(inv_boint_bhint(boint)),
                                                opposite_bcond(inv_boint_bcond(boint)));

  // We emit two branches.
  // First, a conditional branch which jumps around the far branch.
  const address not_taken_pc = pc() + 2 * BytesPerInstWord;
  const address bc_pc        = pc();
  bc(opposite_boint, biint, not_taken_pc);

  const int bc_instr = *(int*)bc_pc;
  assert(not_taken_pc == (address)inv_bd_field(bc_instr, (intptr_t)bc_pc), "postcondition");
  assert(opposite_boint == inv_bo_field(bc_instr), "postcondition");
  assert(boint == add_bhint_to_boint(opposite_bhint(inv_boint_bhint(inv_bo_field(bc_instr))),
                                     opposite_bcond(inv_boint_bcond(inv_bo_field(bc_instr)))),
         "postcondition");
  assert(biint == inv_bi_field(bc_instr), "postcondition");

  // Second, an unconditional far branch which jumps to dest.
  // Note: target(dest) remembers the current pc (see CodeSection::target)
  //       and returns the current pc if the label is not bound yet; when
  //       the label gets bound, the unconditional far branch will be patched.
  const address target_pc = target(dest);
  const address b_pc  = pc();
  b(target_pc);

  assert(not_taken_pc == pc(),                     "postcondition");
  assert(dest.is_bound() || target_pc == b_pc, "postcondition");
}

bool MacroAssembler::is_bc_far_at(address instruction_addr) {
  return is_bc_far_variant1_at(instruction_addr) ||
         is_bc_far_variant2_at(instruction_addr) ||
         is_bc_far_variant3_at(instruction_addr);
}

address MacroAssembler::get_dest_of_bc_far_at(address instruction_addr) {
  if (is_bc_far_variant1_at(instruction_addr)) {
    const address instruction_1_addr = instruction_addr;
    const int instruction_1 = *(int*)instruction_1_addr;
    return (address)inv_bd_field(instruction_1, (intptr_t)instruction_1_addr);
  } else if (is_bc_far_variant2_at(instruction_addr)) {
    const address instruction_2_addr = instruction_addr + 4;
    return bxx_destination(instruction_2_addr);
  } else if (is_bc_far_variant3_at(instruction_addr)) {
    return instruction_addr + 8;
  }
  // variant 4 ???
  ShouldNotReachHere();
  return NULL;
}
void MacroAssembler::set_dest_of_bc_far_at(address instruction_addr, address dest) {

  if (is_bc_far_variant3_at(instruction_addr)) {
    // variant 3, far cond branch to the next instruction, already patched to nops:
    //
    //    nop
    //    endgroup
    //  SKIP/DEST:
    //
    return;
  }

  // first, extract boint and biint from the current branch
  int boint = 0;
  int biint = 0;

  ResourceMark rm;
  const int code_size = 2 * BytesPerInstWord;
  CodeBuffer buf(instruction_addr, code_size);
  MacroAssembler masm(&buf);
  if (is_bc_far_variant2_at(instruction_addr) && dest == instruction_addr + 8) {
    // Far branch to next instruction: Optimize it by patching nops (produce variant 3).
    masm.nop();
    masm.endgroup();
  } else {
    if (is_bc_far_variant1_at(instruction_addr)) {
      // variant 1, the 1st instruction contains the destination address:
      //
      //    bcxx  DEST
      //    endgroup
      //
      const int instruction_1 = *(int*)(instruction_addr);
      boint = inv_bo_field(instruction_1);
      biint = inv_bi_field(instruction_1);
    } else if (is_bc_far_variant2_at(instruction_addr)) {
      // variant 2, the 2nd instruction contains the destination address:
      //
      //    b!cxx SKIP
      //    bxx   DEST
      //  SKIP:
      //
      const int instruction_1 = *(int*)(instruction_addr);
      boint = add_bhint_to_boint(opposite_bhint(inv_boint_bhint(inv_bo_field(instruction_1))),
          opposite_bcond(inv_boint_bcond(inv_bo_field(instruction_1))));
      biint = inv_bi_field(instruction_1);
    } else {
      // variant 4???
      ShouldNotReachHere();
    }

    // second, set the new branch destination and optimize the code
    if (dest != instruction_addr + 4 && // the bc_far is still unbound!
        masm.is_within_range_of_bcxx(dest, instruction_addr)) {
      // variant 1:
      //
      //    bcxx  DEST
      //    endgroup
      //
      masm.bc(boint, biint, dest);
      masm.endgroup();
    } else {
      // variant 2:
      //
      //    b!cxx SKIP
      //    bxx   DEST
      //  SKIP:
      //
      const int opposite_boint = add_bhint_to_boint(opposite_bhint(inv_boint_bhint(boint)),
                                                    opposite_bcond(inv_boint_bcond(boint)));
      const address not_taken_pc = masm.pc() + 2 * BytesPerInstWord;
      masm.bc(opposite_boint, biint, not_taken_pc);
      masm.b(dest);
    }
  }
  ICache::ppc64_flush_icache_bytes(instruction_addr, code_size);
}

// Emit a NOT mt-safe patchable 64 bit absolute call/jump.
void MacroAssembler::bxx64_patchable(address dest, relocInfo::relocType rt, bool link) {
  // get current pc
  uint64_t start_pc = (uint64_t) pc();

  const address pc_of_bl = (address) (start_pc + (6*BytesPerInstWord)); // bl is last
  const address pc_of_b  = (address) (start_pc + (0*BytesPerInstWord)); // b is first

  // relocate here
  if (rt != relocInfo::none) {
    relocate(rt);
  }

  if ( ReoptimizeCallSequences &&
       (( link && is_within_range_of_b(dest, pc_of_bl)) ||
        (!link && is_within_range_of_b(dest, pc_of_b)))) {
    // variant 2:
    // Emit an optimized, pc-relative call/jump.

    if (link) {
      // some padding
      nop();
      nop();
      nop();
      nop();
      nop();
      nop();

      // do the call
      assert(pc() == pc_of_bl, "just checking");
      bl(dest, relocInfo::none);
    } else {
      // do the jump
      assert(pc() == pc_of_b, "just checking");
      b(dest, relocInfo::none);

      // some padding
      nop();
      nop();
      nop();
      nop();
      nop();
      nop();
    }

    // Assert that we can identify the emitted call/jump.
    assert(is_bxx64_patchable_variant2_at((address)start_pc, link),
           "can't identify emitted call");
  } else {
    // variant 1:
#if defined(ABI_ELFv2)
    nop();
    calculate_address_from_global_toc(R12, dest, true, true, false);
    mtctr(R12);
    nop();
    nop();
#else
    mr(R0, R11);  // spill R11 -> R0.

    // Load the destination address into CTR,
    // calculate destination relative to global toc.
    calculate_address_from_global_toc(R11, dest, true, true, false);

    mtctr(R11);
    mr(R11, R0);  // spill R11 <- R0.
    nop();
#endif

    // do the call/jump
    if (link) {
      bctrl();
    } else{
      bctr();
    }
    // Assert that we can identify the emitted call/jump.
    assert(is_bxx64_patchable_variant1b_at((address)start_pc, link),
           "can't identify emitted call");
  }

  // Assert that we can identify the emitted call/jump.
  assert(is_bxx64_patchable_at((address)start_pc, link),
         "can't identify emitted call");
  assert(get_dest_of_bxx64_patchable_at((address)start_pc, link) == dest,
         "wrong encoding of dest address");
}

// Identify a bxx64_patchable instruction.
bool MacroAssembler::is_bxx64_patchable_at(address instruction_addr, bool link) {
  return is_bxx64_patchable_variant1b_at(instruction_addr, link)
    //|| is_bxx64_patchable_variant1_at(instruction_addr, link)
      || is_bxx64_patchable_variant2_at(instruction_addr, link);
}

// Does the call64_patchable instruction use a pc-relative encoding of
// the call destination?
bool MacroAssembler::is_bxx64_patchable_pcrelative_at(address instruction_addr, bool link) {
  // variant 2 is pc-relative
  return is_bxx64_patchable_variant2_at(instruction_addr, link);
}

// Identify variant 1.
bool MacroAssembler::is_bxx64_patchable_variant1_at(address instruction_addr, bool link) {
  unsigned int* instr = (unsigned int*) instruction_addr;
  return (link ? is_bctrl(instr[6]) : is_bctr(instr[6])) // bctr[l]
      && is_mtctr(instr[5]) // mtctr
    && is_load_const_at(instruction_addr);
}

// Identify variant 1b: load destination relative to global toc.
bool MacroAssembler::is_bxx64_patchable_variant1b_at(address instruction_addr, bool link) {
  unsigned int* instr = (unsigned int*) instruction_addr;
  return (link ? is_bctrl(instr[6]) : is_bctr(instr[6])) // bctr[l]
    && is_mtctr(instr[3]) // mtctr
    && is_calculate_address_from_global_toc_at(instruction_addr + 2*BytesPerInstWord, instruction_addr);
}

// Identify variant 2.
bool MacroAssembler::is_bxx64_patchable_variant2_at(address instruction_addr, bool link) {
  unsigned int* instr = (unsigned int*) instruction_addr;
  if (link) {
    return is_bl (instr[6])  // bl dest is last
      && is_nop(instr[0])  // nop
      && is_nop(instr[1])  // nop
      && is_nop(instr[2])  // nop
      && is_nop(instr[3])  // nop
      && is_nop(instr[4])  // nop
      && is_nop(instr[5]); // nop
  } else {
    return is_b  (instr[0])  // b  dest is first
      && is_nop(instr[1])  // nop
      && is_nop(instr[2])  // nop
      && is_nop(instr[3])  // nop
      && is_nop(instr[4])  // nop
      && is_nop(instr[5])  // nop
      && is_nop(instr[6]); // nop
  }
}

// Set dest address of a bxx64_patchable instruction.
void MacroAssembler::set_dest_of_bxx64_patchable_at(address instruction_addr, address dest, bool link) {
  ResourceMark rm;
  int code_size = MacroAssembler::bxx64_patchable_size;
  CodeBuffer buf(instruction_addr, code_size);
  MacroAssembler masm(&buf);
  masm.bxx64_patchable(dest, relocInfo::none, link);
  ICache::ppc64_flush_icache_bytes(instruction_addr, code_size);
}

// Get dest address of a bxx64_patchable instruction.
address MacroAssembler::get_dest_of_bxx64_patchable_at(address instruction_addr, bool link) {
  if (is_bxx64_patchable_variant1_at(instruction_addr, link)) {
    return (address) (unsigned long) get_const(instruction_addr);
  } else if (is_bxx64_patchable_variant2_at(instruction_addr, link)) {
    unsigned int* instr = (unsigned int*) instruction_addr;
    if (link) {
      const int instr_idx = 6; // bl is last
      int branchoffset = branch_destination(instr[instr_idx], 0);
      return instruction_addr + branchoffset + instr_idx*BytesPerInstWord;
    } else {
      const int instr_idx = 0; // b is first
      int branchoffset = branch_destination(instr[instr_idx], 0);
      return instruction_addr + branchoffset + instr_idx*BytesPerInstWord;
    }
  // Load dest relative to global toc.
  } else if (is_bxx64_patchable_variant1b_at(instruction_addr, link)) {
    return get_address_of_calculate_address_from_global_toc_at(instruction_addr + 2*BytesPerInstWord,
                                                               instruction_addr);
  } else {
    ShouldNotReachHere();
    return NULL;
  }
}

// Uses ordering which corresponds to ABI:
//    _savegpr0_14:  std  r14,-144(r1)
//    _savegpr0_15:  std  r15,-136(r1)
//    _savegpr0_16:  std  r16,-128(r1)
void MacroAssembler::save_nonvolatile_gprs(Register dst, int offset) {
  std(R14, offset, dst);   offset += 8;
  std(R15, offset, dst);   offset += 8;
  std(R16, offset, dst);   offset += 8;
  std(R17, offset, dst);   offset += 8;
  std(R18, offset, dst);   offset += 8;
  std(R19, offset, dst);   offset += 8;
  std(R20, offset, dst);   offset += 8;
  std(R21, offset, dst);   offset += 8;
  std(R22, offset, dst);   offset += 8;
  std(R23, offset, dst);   offset += 8;
  std(R24, offset, dst);   offset += 8;
  std(R25, offset, dst);   offset += 8;
  std(R26, offset, dst);   offset += 8;
  std(R27, offset, dst);   offset += 8;
  std(R28, offset, dst);   offset += 8;
  std(R29, offset, dst);   offset += 8;
  std(R30, offset, dst);   offset += 8;
  std(R31, offset, dst);   offset += 8;

  stfd(F14, offset, dst);   offset += 8;
  stfd(F15, offset, dst);   offset += 8;
  stfd(F16, offset, dst);   offset += 8;
  stfd(F17, offset, dst);   offset += 8;
  stfd(F18, offset, dst);   offset += 8;
  stfd(F19, offset, dst);   offset += 8;
  stfd(F20, offset, dst);   offset += 8;
  stfd(F21, offset, dst);   offset += 8;
  stfd(F22, offset, dst);   offset += 8;
  stfd(F23, offset, dst);   offset += 8;
  stfd(F24, offset, dst);   offset += 8;
  stfd(F25, offset, dst);   offset += 8;
  stfd(F26, offset, dst);   offset += 8;
  stfd(F27, offset, dst);   offset += 8;
  stfd(F28, offset, dst);   offset += 8;
  stfd(F29, offset, dst);   offset += 8;
  stfd(F30, offset, dst);   offset += 8;
  stfd(F31, offset, dst);
}

// Uses ordering which corresponds to ABI:
//    _restgpr0_14:  ld   r14,-144(r1)
//    _restgpr0_15:  ld   r15,-136(r1)
//    _restgpr0_16:  ld   r16,-128(r1)
void MacroAssembler::restore_nonvolatile_gprs(Register src, int offset) {
  ld(R14, offset, src);   offset += 8;
  ld(R15, offset, src);   offset += 8;
  ld(R16, offset, src);   offset += 8;
  ld(R17, offset, src);   offset += 8;
  ld(R18, offset, src);   offset += 8;
  ld(R19, offset, src);   offset += 8;
  ld(R20, offset, src);   offset += 8;
  ld(R21, offset, src);   offset += 8;
  ld(R22, offset, src);   offset += 8;
  ld(R23, offset, src);   offset += 8;
  ld(R24, offset, src);   offset += 8;
  ld(R25, offset, src);   offset += 8;
  ld(R26, offset, src);   offset += 8;
  ld(R27, offset, src);   offset += 8;
  ld(R28, offset, src);   offset += 8;
  ld(R29, offset, src);   offset += 8;
  ld(R30, offset, src);   offset += 8;
  ld(R31, offset, src);   offset += 8;

  // FP registers
  lfd(F14, offset, src);   offset += 8;
  lfd(F15, offset, src);   offset += 8;
  lfd(F16, offset, src);   offset += 8;
  lfd(F17, offset, src);   offset += 8;
  lfd(F18, offset, src);   offset += 8;
  lfd(F19, offset, src);   offset += 8;
  lfd(F20, offset, src);   offset += 8;
  lfd(F21, offset, src);   offset += 8;
  lfd(F22, offset, src);   offset += 8;
  lfd(F23, offset, src);   offset += 8;
  lfd(F24, offset, src);   offset += 8;
  lfd(F25, offset, src);   offset += 8;
  lfd(F26, offset, src);   offset += 8;
  lfd(F27, offset, src);   offset += 8;
  lfd(F28, offset, src);   offset += 8;
  lfd(F29, offset, src);   offset += 8;
  lfd(F30, offset, src);   offset += 8;
  lfd(F31, offset, src);
}

// For verify_oops.
void MacroAssembler::save_volatile_gprs(Register dst, int offset) {
  std(R3,  offset, dst);   offset += 8;
  std(R4,  offset, dst);   offset += 8;
  std(R5,  offset, dst);   offset += 8;
  std(R6,  offset, dst);   offset += 8;
  std(R7,  offset, dst);   offset += 8;
  std(R8,  offset, dst);   offset += 8;
  std(R9,  offset, dst);   offset += 8;
  std(R10, offset, dst);   offset += 8;
  std(R11, offset, dst);   offset += 8;
  std(R12, offset, dst);
}

// For verify_oops.
void MacroAssembler::restore_volatile_gprs(Register src, int offset) {
  ld(R3,  offset, src);   offset += 8;
  ld(R4,  offset, src);   offset += 8;
  ld(R5,  offset, src);   offset += 8;
  ld(R6,  offset, src);   offset += 8;
  ld(R7,  offset, src);   offset += 8;
  ld(R8,  offset, src);   offset += 8;
  ld(R9,  offset, src);   offset += 8;
  ld(R10, offset, src);   offset += 8;
  ld(R11, offset, src);   offset += 8;
  ld(R12, offset, src);
}

void MacroAssembler::save_LR_CR(Register tmp) {
  mfcr(tmp);
  std(tmp, _abi(cr), R1_SP);
  mflr(tmp);
  std(tmp, _abi(lr), R1_SP);
  // Tmp must contain lr on exit! (see return_addr and prolog in ppc64.ad)
}

void MacroAssembler::restore_LR_CR(Register tmp) {
  assert(tmp != R1_SP, "must be distinct");
  ld(tmp, _abi(lr), R1_SP);
  mtlr(tmp);
  ld(tmp, _abi(cr), R1_SP);
  mtcr(tmp);
}

address MacroAssembler::get_PC_trash_LR(Register result) {
  Label L;
  bl(L);
  bind(L);
  address lr_pc = pc();
  mflr(result);
  return lr_pc;
}

void MacroAssembler::resize_frame(Register offset, Register tmp) {
#ifdef ASSERT
  assert_different_registers(offset, tmp, R1_SP);
  andi_(tmp, offset, frame::alignment_in_bytes-1);
  asm_assert_eq("resize_frame: unaligned", 0x204);
#endif

  // tmp <- *(SP)
  ld(tmp, _abi(callers_sp), R1_SP);
  // addr <- SP + offset;
  // *(addr) <- tmp;
  // SP <- addr
  stdux(tmp, R1_SP, offset);
}

void MacroAssembler::resize_frame(int offset, Register tmp) {
  assert(is_simm(offset, 16), "too big an offset");
  assert_different_registers(tmp, R1_SP);
  assert((offset & (frame::alignment_in_bytes-1))==0, "resize_frame: unaligned");
  // tmp <- *(SP)
  ld(tmp, _abi(callers_sp), R1_SP);
  // addr <- SP + offset;
  // *(addr) <- tmp;
  // SP <- addr
  stdu(tmp, offset, R1_SP);
}

void MacroAssembler::resize_frame_absolute(Register addr, Register tmp1, Register tmp2) {
  // (addr == tmp1) || (addr == tmp2) is allowed here!
  assert(tmp1 != tmp2, "must be distinct");

  // compute offset w.r.t. current stack pointer
  // tmp_1 <- addr - SP (!)
  subf(tmp1, R1_SP, addr);

  // atomically update SP keeping back link.
  resize_frame(tmp1/* offset */, tmp2/* tmp */);
}

void MacroAssembler::push_frame(Register bytes, Register tmp) {
#ifdef ASSERT
  assert(bytes != R0, "r0 not allowed here");
  andi_(R0, bytes, frame::alignment_in_bytes-1);
  asm_assert_eq("push_frame(Reg, Reg): unaligned", 0x203);
#endif
  neg(tmp, bytes);
  stdux(R1_SP, R1_SP, tmp);
}

// Push a frame of size `bytes'.
void MacroAssembler::push_frame(unsigned int bytes, Register tmp) {
  long offset = align_addr(bytes, frame::alignment_in_bytes);
  if (is_simm(-offset, 16)) {
    stdu(R1_SP, -offset, R1_SP);
  } else {
    load_const(tmp, -offset);
    stdux(R1_SP, R1_SP, tmp);
  }
}

// Push a frame of size `bytes' plus abi_reg_args on top.
void MacroAssembler::push_frame_reg_args(unsigned int bytes, Register tmp) {
  push_frame(bytes + frame::abi_reg_args_size, tmp);
}

// Setup up a new C frame with a spill area for non-volatile GPRs and
// additional space for local variables.
void MacroAssembler::push_frame_reg_args_nonvolatiles(unsigned int bytes,
                                                      Register tmp) {
  push_frame(bytes + frame::abi_reg_args_size + frame::spill_nonvolatiles_size, tmp);
}

// Pop current C frame.
void MacroAssembler::pop_frame() {
  ld(R1_SP, _abi(callers_sp), R1_SP);
}

#if defined(ABI_ELFv2)
address MacroAssembler::branch_to(Register r_function_entry, bool and_link) {
  // TODO(asmundak): make sure the caller uses R12 as function descriptor
  // most of the times.
  if (R12 != r_function_entry) {
    mr(R12, r_function_entry);
  }
  mtctr(R12);
  // Do a call or a branch.
  if (and_link) {
    bctrl();
  } else {
    bctr();
  }
  _last_calls_return_pc = pc();

  return _last_calls_return_pc;
}

// Call a C function via a function descriptor and use full C
// calling conventions. Updates and returns _last_calls_return_pc.
address MacroAssembler::call_c(Register r_function_entry) {
  return branch_to(r_function_entry, /*and_link=*/true);
}

// For tail calls: only branch, don't link, so callee returns to caller of this function.
address MacroAssembler::call_c_and_return_to_caller(Register r_function_entry) {
  return branch_to(r_function_entry, /*and_link=*/false);
}

address MacroAssembler::call_c(address function_entry, relocInfo::relocType rt) {
  load_const(R12, function_entry, R0);
  return branch_to(R12,  /*and_link=*/true);
}

#else
// Generic version of a call to C function via a function descriptor
// with variable support for C calling conventions (TOC, ENV, etc.).
// Updates and returns _last_calls_return_pc.
address MacroAssembler::branch_to(Register function_descriptor, bool and_link, bool save_toc_before_call,
                                  bool restore_toc_after_call, bool load_toc_of_callee, bool load_env_of_callee) {
  // we emit standard ptrgl glue code here
  assert((function_descriptor != R0), "function_descriptor cannot be R0");

  // retrieve necessary entries from the function descriptor
  ld(R0, in_bytes(FunctionDescriptor::entry_offset()), function_descriptor);
  mtctr(R0);

  if (load_toc_of_callee) {
    ld(R2_TOC, in_bytes(FunctionDescriptor::toc_offset()), function_descriptor);
  }
  if (load_env_of_callee) {
    ld(R11, in_bytes(FunctionDescriptor::env_offset()), function_descriptor);
  } else if (load_toc_of_callee) {
    li(R11, 0);
  }

  // do a call or a branch
  if (and_link) {
    bctrl();
  } else {
    bctr();
  }
  _last_calls_return_pc = pc();

  return _last_calls_return_pc;
}

// Call a C function via a function descriptor and use full C calling
// conventions.
// We don't use the TOC in generated code, so there is no need to save
// and restore its value.
address MacroAssembler::call_c(Register fd) {
  return branch_to(fd, /*and_link=*/true,
                       /*save toc=*/false,
                       /*restore toc=*/false,
                       /*load toc=*/true,
                       /*load env=*/true);
}

address MacroAssembler::call_c_and_return_to_caller(Register fd) {
  return branch_to(fd, /*and_link=*/false,
                       /*save toc=*/false,
                       /*restore toc=*/false,
                       /*load toc=*/true,
                       /*load env=*/true);
}

address MacroAssembler::call_c(const FunctionDescriptor* fd, relocInfo::relocType rt) {
  if (rt != relocInfo::none) {
    // this call needs to be relocatable
    if (!ReoptimizeCallSequences
        || (rt != relocInfo::runtime_call_type && rt != relocInfo::none)
        || fd == NULL   // support code-size estimation
        || !fd->is_friend_function()
        || fd->entry() == NULL) {
      // it's not a friend function as defined by class FunctionDescriptor,
      // so do a full call-c here.
      load_const(R11, (address)fd, R0);

      bool has_env = (fd != NULL && fd->env() != NULL);
      return branch_to(R11, /*and_link=*/true,
                            /*save toc=*/false,
                            /*restore toc=*/false,
                            /*load toc=*/true,
                            /*load env=*/has_env);
    } else {
      // It's a friend function. Load the entry point and don't care about
      // toc and env. Use an optimizable call instruction, but ensure the
      // same code-size as in the case of a non-friend function.
      nop();
      nop();
      nop();
      bl64_patchable(fd->entry(), rt);
      _last_calls_return_pc = pc();
      return _last_calls_return_pc;
    }
  } else {
    // This call does not need to be relocatable, do more aggressive
    // optimizations.
    if (!ReoptimizeCallSequences
      || !fd->is_friend_function()) {
      // It's not a friend function as defined by class FunctionDescriptor,
      // so do a full call-c here.
      load_const(R11, (address)fd, R0);
      return branch_to(R11, /*and_link=*/true,
                            /*save toc=*/false,
                            /*restore toc=*/false,
                            /*load toc=*/true,
                            /*load env=*/true);
    } else {
      // it's a friend function, load the entry point and don't care about
      // toc and env.
      address dest = fd->entry();
      if (is_within_range_of_b(dest, pc())) {
        bl(dest);
      } else {
        bl64_patchable(dest, rt);
      }
      _last_calls_return_pc = pc();
      return _last_calls_return_pc;
    }
  }
}

// Call a C function.  All constants needed reside in TOC.
//
// Read the address to call from the TOC.
// Read env from TOC, if fd specifies an env.
// Read new TOC from TOC.
address MacroAssembler::call_c_using_toc(const FunctionDescriptor* fd,
                                         relocInfo::relocType rt, Register toc) {
  if (!ReoptimizeCallSequences
    || (rt != relocInfo::runtime_call_type && rt != relocInfo::none)
    || !fd->is_friend_function()) {
    // It's not a friend function as defined by class FunctionDescriptor,
    // so do a full call-c here.
    assert(fd->entry() != NULL, "function must be linked");

    AddressLiteral fd_entry(fd->entry());
    load_const_from_method_toc(R11, fd_entry, toc);
    mtctr(R11);
    if (fd->env() == NULL) {
      li(R11, 0);
      nop();
    } else {
      AddressLiteral fd_env(fd->env());
      load_const_from_method_toc(R11, fd_env, toc);
    }
    AddressLiteral fd_toc(fd->toc());
    load_toc_from_toc(R2_TOC, fd_toc, toc);
    // R2_TOC is killed.
    bctrl();
    _last_calls_return_pc = pc();
  } else {
    // It's a friend function, load the entry point and don't care about
    // toc and env. Use an optimizable call instruction, but ensure the
    // same code-size as in the case of a non-friend function.
    nop();
    bl64_patchable(fd->entry(), rt);
    _last_calls_return_pc = pc();
  }
  return _last_calls_return_pc;
}
#endif // ABI_ELFv2

void MacroAssembler::call_VM_base(Register oop_result,
                                  Register last_java_sp,
                                  address  entry_point,
                                  bool     check_exceptions) {
  BLOCK_COMMENT("call_VM {");
  // Determine last_java_sp register.
  if (!last_java_sp->is_valid()) {
    last_java_sp = R1_SP;
  }
  set_top_ijava_frame_at_SP_as_last_Java_frame(last_java_sp, R11_scratch1);

  // ARG1 must hold thread address.
  mr(R3_ARG1, R16_thread);
#if defined(ABI_ELFv2)
  address return_pc = call_c(entry_point, relocInfo::none);
#else
  address return_pc = call_c((FunctionDescriptor*)entry_point, relocInfo::none);
#endif

  reset_last_Java_frame();

  // Check for pending exceptions.
  if (check_exceptions) {
    // We don't check for exceptions here.
    ShouldNotReachHere();
  }

  // Get oop result if there is one and reset the value in the thread.
  if (oop_result->is_valid()) {
    get_vm_result(oop_result);
  }

  _last_calls_return_pc = return_pc;
  BLOCK_COMMENT("} call_VM");
}

void MacroAssembler::call_VM_leaf_base(address entry_point) {
  BLOCK_COMMENT("call_VM_leaf {");
#if defined(ABI_ELFv2)
  call_c(entry_point, relocInfo::none);
#else
  call_c(CAST_FROM_FN_PTR(FunctionDescriptor*, entry_point), relocInfo::none);
#endif
  BLOCK_COMMENT("} call_VM_leaf");
}

void MacroAssembler::call_VM(Register oop_result, address entry_point, bool check_exceptions) {
  call_VM_base(oop_result, noreg, entry_point, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1,
                             bool check_exceptions) {
  // R3_ARG1 is reserved for the thread.
  mr_if_needed(R4_ARG2, arg_1);
  call_VM(oop_result, entry_point, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2,
                             bool check_exceptions) {
  // R3_ARG1 is reserved for the thread
  mr_if_needed(R4_ARG2, arg_1);
  assert(arg_2 != R4_ARG2, "smashed argument");
  mr_if_needed(R5_ARG3, arg_2);
  call_VM(oop_result, entry_point, check_exceptions);
}

void MacroAssembler::call_VM_leaf(address entry_point) {
  call_VM_leaf_base(entry_point);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1) {
  mr_if_needed(R3_ARG1, arg_1);
  call_VM_leaf(entry_point);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1, Register arg_2) {
  mr_if_needed(R3_ARG1, arg_1);
  assert(arg_2 != R3_ARG1, "smashed argument");
  mr_if_needed(R4_ARG2, arg_2);
  call_VM_leaf(entry_point);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1, Register arg_2, Register arg_3) {
  mr_if_needed(R3_ARG1, arg_1);
  assert(arg_2 != R3_ARG1, "smashed argument");
  mr_if_needed(R4_ARG2, arg_2);
  assert(arg_3 != R3_ARG1 && arg_3 != R4_ARG2, "smashed argument");
  mr_if_needed(R5_ARG3, arg_3);
  call_VM_leaf(entry_point);
}

// Check whether instruction is a read access to the polling page
// which was emitted by load_from_polling_page(..).
bool MacroAssembler::is_load_from_polling_page(int instruction, void* ucontext,
                                               address* polling_address_ptr) {
  if (!is_ld(instruction))
    return false; // It's not a ld. Fail.

  int rt = inv_rt_field(instruction);
  int ra = inv_ra_field(instruction);
  int ds = inv_ds_field(instruction);
  if (!(ds == 0 && ra != 0 && rt == 0)) {
    return false; // It's not a ld(r0, X, ra). Fail.
  }

  if (!ucontext) {
    // Set polling address.
    if (polling_address_ptr != NULL) {
      *polling_address_ptr = NULL;
    }
    return true; // No ucontext given. Can't check value of ra. Assume true.
  }

#ifdef LINUX
  // Ucontext given. Check that register ra contains the address of
  // the safepoing polling page.
  ucontext_t* uc = (ucontext_t*) ucontext;
  // Set polling address.
  address addr = (address)uc->uc_mcontext.regs->gpr[ra] + (ssize_t)ds;
  if (polling_address_ptr != NULL) {
    *polling_address_ptr = addr;
  }
  return os::is_poll_address(addr);
#else
  // Not on Linux, ucontext must be NULL.
  ShouldNotReachHere();
  return false;
#endif
}

bool MacroAssembler::is_memory_serialization(int instruction, JavaThread* thread, void* ucontext) {
#ifdef LINUX
  ucontext_t* uc = (ucontext_t*) ucontext;

  if (is_stwx(instruction) || is_stwux(instruction)) {
    int ra = inv_ra_field(instruction);
    int rb = inv_rb_field(instruction);

    // look up content of ra and rb in ucontext
    address ra_val=(address)uc->uc_mcontext.regs->gpr[ra];
    long rb_val=(long)uc->uc_mcontext.regs->gpr[rb];
    return os::is_memory_serialize_page(thread, ra_val+rb_val);
  } else if (is_stw(instruction) || is_stwu(instruction)) {
    int ra = inv_ra_field(instruction);
    int d1 = inv_d1_field(instruction);

    // look up content of ra in ucontext
    address ra_val=(address)uc->uc_mcontext.regs->gpr[ra];
    return os::is_memory_serialize_page(thread, ra_val+d1);
  } else {
    return false;
  }
#else
  // workaround not needed on !LINUX :-)
  ShouldNotCallThis();
  return false;
#endif
}

void MacroAssembler::bang_stack_with_offset(int offset) {
  // When increasing the stack, the old stack pointer will be written
  // to the new top of stack according to the PPC64 abi.
  // Therefore, stack banging is not necessary when increasing
  // the stack by <= os::vm_page_size() bytes.
  // When increasing the stack by a larger amount, this method is
  // called repeatedly to bang the intermediate pages.

  // Stack grows down, caller passes positive offset.
  assert(offset > 0, "must bang with positive offset");

  long stdoffset = -offset;

  if (is_simm(stdoffset, 16)) {
    // Signed 16 bit offset, a simple std is ok.
    if (UseLoadInstructionsForStackBangingPPC64) {
      ld(R0, (int)(signed short)stdoffset, R1_SP);
    } else {
      std(R0,(int)(signed short)stdoffset, R1_SP);
    }
  } else if (is_simm(stdoffset, 31)) {
    const int hi = MacroAssembler::largeoffset_si16_si16_hi(stdoffset);
    const int lo = MacroAssembler::largeoffset_si16_si16_lo(stdoffset);

    Register tmp = R11;
    addis(tmp, R1_SP, hi);
    if (UseLoadInstructionsForStackBangingPPC64) {
      ld(R0,  lo, tmp);
    } else {
      std(R0, lo, tmp);
    }
  } else {
    ShouldNotReachHere();
  }
}

// If instruction is a stack bang of the form
//    std    R0,    x(Ry),       (see bang_stack_with_offset())
//    stdu   R1_SP, x(R1_SP),    (see push_frame(), resize_frame())
// or stdux  R1_SP, Rx, R1_SP    (see push_frame(), resize_frame())
// return the banged address. Otherwise, return 0.
address MacroAssembler::get_stack_bang_address(int instruction, void *ucontext) {
#ifdef LINUX
  ucontext_t* uc = (ucontext_t*) ucontext;
  int rs = inv_rs_field(instruction);
  int ra = inv_ra_field(instruction);
  if (   (is_ld(instruction)   && rs == 0 &&  UseLoadInstructionsForStackBangingPPC64)
      || (is_std(instruction)  && rs == 0 && !UseLoadInstructionsForStackBangingPPC64)
      || (is_stdu(instruction) && rs == 1)) {
    int ds = inv_ds_field(instruction);
    // return banged address
    return ds+(address)uc->uc_mcontext.regs->gpr[ra];
  } else if (is_stdux(instruction) && rs == 1) {
    int rb = inv_rb_field(instruction);
    address sp = (address)uc->uc_mcontext.regs->gpr[1];
    long rb_val = (long)uc->uc_mcontext.regs->gpr[rb];
    return ra != 1 || rb_val >= 0 ? NULL         // not a stack bang
                                  : sp + rb_val; // banged address
  }
  return NULL; // not a stack bang
#else
  // workaround not needed on !LINUX :-)
  ShouldNotCallThis();
  return NULL;
#endif
}

// CmpxchgX sets condition register to cmpX(current, compare).
void MacroAssembler::cmpxchgw(ConditionRegister flag, Register dest_current_value,
                              Register compare_value, Register exchange_value,
                              Register addr_base, int semantics, bool cmpxchgx_hint,
                              Register int_flag_success, bool contention_hint) {
  Label retry;
  Label failed;
  Label done;

  // Save one branch if result is returned via register and
  // result register is different from the other ones.
  bool use_result_reg    = (int_flag_success != noreg);
  bool preset_result_reg = (int_flag_success != dest_current_value && int_flag_success != compare_value &&
                            int_flag_success != exchange_value && int_flag_success != addr_base);

  // release/fence semantics
  if (semantics & MemBarRel) {
    release();
  }

  if (use_result_reg && preset_result_reg) {
    li(int_flag_success, 0); // preset (assume cas failed)
  }

  // Add simple guard in order to reduce risk of starving under high contention (recommended by IBM).
  if (contention_hint) { // Don't try to reserve if cmp fails.
    lwz(dest_current_value, 0, addr_base);
    cmpw(flag, dest_current_value, compare_value);
    bne(flag, failed);
  }

  // atomic emulation loop
  bind(retry);

  lwarx(dest_current_value, addr_base, cmpxchgx_hint);
  cmpw(flag, dest_current_value, compare_value);
  if (UseStaticBranchPredictionInCompareAndSwapPPC64) {
    bne_predict_not_taken(flag, failed);
  } else {
    bne(                  flag, failed);
  }
  // branch to done  => (flag == ne), (dest_current_value != compare_value)
  // fall through    => (flag == eq), (dest_current_value == compare_value)

  stwcx_(exchange_value, addr_base);
  if (UseStaticBranchPredictionInCompareAndSwapPPC64) {
    bne_predict_not_taken(CCR0, retry); // StXcx_ sets CCR0.
  } else {
    bne(                  CCR0, retry); // StXcx_ sets CCR0.
  }
  // fall through    => (flag == eq), (dest_current_value == compare_value), (swapped)

  // Result in register (must do this at the end because int_flag_success can be the
  // same register as one above).
  if (use_result_reg) {
    li(int_flag_success, 1);
  }

  if (semantics & MemBarFenceAfter) {
    fence();
  } else if (semantics & MemBarAcq) {
    isync();
  }

  if (use_result_reg && !preset_result_reg) {
    b(done);
  }

  bind(failed);
  if (use_result_reg && !preset_result_reg) {
    li(int_flag_success, 0);
  }

  bind(done);
  // (flag == ne) => (dest_current_value != compare_value), (!swapped)
  // (flag == eq) => (dest_current_value == compare_value), ( swapped)
}

// Preforms atomic compare exchange:
//   if (compare_value == *addr_base)
//     *addr_base = exchange_value
//     int_flag_success = 1;
//   else
//     int_flag_success = 0;
//
// ConditionRegister flag       = cmp(compare_value, *addr_base)
// Register dest_current_value  = *addr_base
// Register compare_value       Used to compare with value in memory
// Register exchange_value      Written to memory if compare_value == *addr_base
// Register addr_base           The memory location to compareXChange
// Register int_flag_success    Set to 1 if exchange_value was written to *addr_base
//
// To avoid the costly compare exchange the value is tested beforehand.
// Several special cases exist to avoid that unnecessary information is generated.
//
void MacroAssembler::cmpxchgd(ConditionRegister flag,
                              Register dest_current_value, Register compare_value, Register exchange_value,
                              Register addr_base, int semantics, bool cmpxchgx_hint,
                              Register int_flag_success, Label* failed_ext, bool contention_hint) {
  Label retry;
  Label failed_int;
  Label& failed = (failed_ext != NULL) ? *failed_ext : failed_int;
  Label done;

  // Save one branch if result is returned via register and result register is different from the other ones.
  bool use_result_reg    = (int_flag_success!=noreg);
  bool preset_result_reg = (int_flag_success!=dest_current_value && int_flag_success!=compare_value &&
                            int_flag_success!=exchange_value && int_flag_success!=addr_base);
  assert(int_flag_success == noreg || failed_ext == NULL, "cannot have both");

  // release/fence semantics
  if (semantics & MemBarRel) {
    release();
  }

  if (use_result_reg && preset_result_reg) {
    li(int_flag_success, 0); // preset (assume cas failed)
  }

  // Add simple guard in order to reduce risk of starving under high contention (recommended by IBM).
  if (contention_hint) { // Don't try to reserve if cmp fails.
    ld(dest_current_value, 0, addr_base);
    cmpd(flag, dest_current_value, compare_value);
    bne(flag, failed);
  }

  // atomic emulation loop
  bind(retry);

  ldarx(dest_current_value, addr_base, cmpxchgx_hint);
  cmpd(flag, dest_current_value, compare_value);
  if (UseStaticBranchPredictionInCompareAndSwapPPC64) {
    bne_predict_not_taken(flag, failed);
  } else {
    bne(                  flag, failed);
  }

  stdcx_(exchange_value, addr_base);
  if (UseStaticBranchPredictionInCompareAndSwapPPC64) {
    bne_predict_not_taken(CCR0, retry); // stXcx_ sets CCR0
  } else {
    bne(                  CCR0, retry); // stXcx_ sets CCR0
  }

  // result in register (must do this at the end because int_flag_success can be the same register as one above)
  if (use_result_reg) {
    li(int_flag_success, 1);
  }

  // POWER6 doesn't need isync in CAS.
  // Always emit isync to be on the safe side.
  if (semantics & MemBarFenceAfter) {
    fence();
  } else if (semantics & MemBarAcq) {
    isync();
  }

  if (use_result_reg && !preset_result_reg) {
    b(done);
  }

  bind(failed_int);
  if (use_result_reg && !preset_result_reg) {
    li(int_flag_success, 0);
  }

  bind(done);
  // (flag == ne) => (dest_current_value != compare_value), (!swapped)
  // (flag == eq) => (dest_current_value == compare_value), ( swapped)
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
                                             Register sethi_temp,
                                             Label& L_no_such_interface) {
  assert_different_registers(recv_klass, intf_klass, method_result, scan_temp);
  assert(itable_index.is_constant() || itable_index.as_register() == method_result,
         "caller must use same register for non-constant itable index as for method");

  // Compute start of first itableOffsetEntry (which is at the end of the vtable).
  int vtable_base = InstanceKlass::vtable_start_offset() * wordSize;
  int itentry_off = itableMethodEntry::method_offset_in_bytes();
  int logMEsize   = exact_log2(itableMethodEntry::size() * wordSize);
  int scan_step   = itableOffsetEntry::size() * wordSize;
  int log_vte_size= exact_log2(vtableEntry::size() * wordSize);

  lwz(scan_temp, InstanceKlass::vtable_length_offset() * wordSize, recv_klass);
  // %%% We should store the aligned, prescaled offset in the klassoop.
  // Then the next several instructions would fold away.

  sldi(scan_temp, scan_temp, log_vte_size);
  addi(scan_temp, scan_temp, vtable_base);
  add(scan_temp, recv_klass, scan_temp);

  // Adjust recv_klass by scaled itable_index, so we can free itable_index.
  if (itable_index.is_register()) {
    Register itable_offset = itable_index.as_register();
    sldi(itable_offset, itable_offset, logMEsize);
    if (itentry_off) addi(itable_offset, itable_offset, itentry_off);
    add(recv_klass, itable_offset, recv_klass);
  } else {
    long itable_offset = (long)itable_index.as_constant();
    load_const_optimized(sethi_temp, (itable_offset<<logMEsize)+itentry_off); // static address, no relocation
    add(recv_klass, sethi_temp, recv_klass);
  }

  // for (scan = klass->itable(); scan->interface() != NULL; scan += scan_step) {
  //   if (scan->interface() == intf) {
  //     result = (klass + scan->offset() + itable_index);
  //   }
  // }
  Label search, found_method;

  for (int peel = 1; peel >= 0; peel--) {
    // %%%% Could load both offset and interface in one ldx, if they were
    // in the opposite order. This would save a load.
    ld(method_result, itableOffsetEntry::interface_offset_in_bytes(), scan_temp);

    // Check that this entry is non-null. A null entry means that
    // the receiver class doesn't implement the interface, and wasn't the
    // same as when the caller was compiled.
    cmpd(CCR0, method_result, intf_klass);

    if (peel) {
      beq(CCR0, found_method);
    } else {
      bne(CCR0, search);
      // (invert the test to fall through to found_method...)
    }

    if (!peel) break;

    bind(search);

    cmpdi(CCR0, method_result, 0);
    beq(CCR0, L_no_such_interface);
    addi(scan_temp, scan_temp, scan_step);
  }

  bind(found_method);

  // Got a hit.
  int ito_offset = itableOffsetEntry::offset_offset_in_bytes();
  lwz(scan_temp, ito_offset, scan_temp);
  ldx(method_result, scan_temp, recv_klass);
}

// virtual method calling
void MacroAssembler::lookup_virtual_method(Register recv_klass,
                                           RegisterOrConstant vtable_index,
                                           Register method_result) {

  assert_different_registers(recv_klass, method_result, vtable_index.register_or_noreg());

  const int base = InstanceKlass::vtable_start_offset() * wordSize;
  assert(vtableEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");

  if (vtable_index.is_register()) {
    sldi(vtable_index.as_register(), vtable_index.as_register(), LogBytesPerWord);
    add(recv_klass, vtable_index.as_register(), recv_klass);
  } else {
    addi(recv_klass, recv_klass, vtable_index.as_constant() << LogBytesPerWord);
  }
  ld(R19_method, base + vtableEntry::method_offset_in_bytes(), recv_klass);
}

/////////////////////////////////////////// subtype checking ////////////////////////////////////////////

void MacroAssembler::check_klass_subtype_fast_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp1_reg,
                                                   Register temp2_reg,
                                                   Label& L_success,
                                                   Label& L_failure) {

  const Register check_cache_offset = temp1_reg;
  const Register cached_super       = temp2_reg;

  assert_different_registers(sub_klass, super_klass, check_cache_offset, cached_super);

  int sco_offset = in_bytes(Klass::super_check_offset_offset());
  int sc_offset  = in_bytes(Klass::secondary_super_cache_offset());

  // If the pointers are equal, we are done (e.g., String[] elements).
  // This self-check enables sharing of secondary supertype arrays among
  // non-primary types such as array-of-interface. Otherwise, each such
  // type would need its own customized SSA.
  // We move this check to the front of the fast path because many
  // type checks are in fact trivially successful in this manner,
  // so we get a nicely predicted branch right at the start of the check.
  cmpd(CCR0, sub_klass, super_klass);
  beq(CCR0, L_success);

  // Check the supertype display:
  lwz(check_cache_offset, sco_offset, super_klass);
  // The loaded value is the offset from KlassOopDesc.

  ldx(cached_super, check_cache_offset, sub_klass);
  cmpd(CCR0, cached_super, super_klass);
  beq(CCR0, L_success);

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

  cmpwi(CCR0, check_cache_offset, sc_offset);
  bne(CCR0, L_failure);
  // bind(slow_path); // fallthru
}

void MacroAssembler::check_klass_subtype_slow_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp1_reg,
                                                   Register temp2_reg,
                                                   Label* L_success,
                                                   Register result_reg) {
  const Register array_ptr = temp1_reg; // current value from cache array
  const Register temp      = temp2_reg;

  assert_different_registers(sub_klass, super_klass, array_ptr, temp);

  int source_offset = in_bytes(Klass::secondary_supers_offset());
  int target_offset = in_bytes(Klass::secondary_super_cache_offset());

  int length_offset = Array<Klass*>::length_offset_in_bytes();
  int base_offset   = Array<Klass*>::base_offset_in_bytes();

  Label hit, loop, failure, fallthru;

  ld(array_ptr, source_offset, sub_klass);

  //assert(4 == arrayOopDesc::length_length_in_bytes(), "precondition violated.");
  lwz(temp, length_offset, array_ptr);
  cmpwi(CCR0, temp, 0);
  beq(CCR0, result_reg!=noreg ? failure : fallthru); // length 0

  mtctr(temp); // load ctr

  bind(loop);
  // Oops in table are NO MORE compressed.
  ld(temp, base_offset, array_ptr);
  cmpd(CCR0, temp, super_klass);
  beq(CCR0, hit);
  addi(array_ptr, array_ptr, BytesPerWord);
  bdnz(loop);

  bind(failure);
  if (result_reg!=noreg) li(result_reg, 1); // load non-zero result (indicates a miss)
  b(fallthru);

  bind(hit);
  std(super_klass, target_offset, sub_klass); // save result to cache
  if (result_reg != noreg) li(result_reg, 0); // load zero result (indicates a hit)
  if (L_success != NULL) b(*L_success);

  bind(fallthru);
}

// Try fast path, then go to slow one if not successful
void MacroAssembler::check_klass_subtype(Register sub_klass,
                         Register super_klass,
                         Register temp1_reg,
                         Register temp2_reg,
                         Label& L_success) {
  Label L_failure;
  check_klass_subtype_fast_path(sub_klass, super_klass, temp1_reg, temp2_reg, L_success, L_failure);
  check_klass_subtype_slow_path(sub_klass, super_klass, temp1_reg, temp2_reg, &L_success);
  bind(L_failure); // Fallthru if not successful.
}

void MacroAssembler::check_method_handle_type(Register mtype_reg, Register mh_reg,
                                              Register temp_reg,
                                              Label& wrong_method_type) {
  assert_different_registers(mtype_reg, mh_reg, temp_reg);
  // Compare method type against that of the receiver.
  load_heap_oop_not_null(temp_reg, delayed_value(java_lang_invoke_MethodHandle::type_offset_in_bytes, temp_reg), mh_reg);
  cmpd(CCR0, temp_reg, mtype_reg);
  bne(CCR0, wrong_method_type);
}

RegisterOrConstant MacroAssembler::argument_offset(RegisterOrConstant arg_slot,
                                                   Register temp_reg,
                                                   int extra_slot_offset) {
  // cf. TemplateTable::prepare_invoke(), if (load_receiver).
  int stackElementSize = Interpreter::stackElementSize;
  int offset = extra_slot_offset * stackElementSize;
  if (arg_slot.is_constant()) {
    offset += arg_slot.as_constant() * stackElementSize;
    return offset;
  } else {
    assert(temp_reg != noreg, "must specify");
    sldi(temp_reg, arg_slot.as_register(), exact_log2(stackElementSize));
    if (offset != 0)
      addi(temp_reg, temp_reg, offset);
    return temp_reg;
  }
}

void MacroAssembler::biased_locking_enter(ConditionRegister cr_reg, Register obj_reg,
                                          Register mark_reg, Register temp_reg,
                                          Register temp2_reg, Label& done, Label* slow_case) {
  assert(UseBiasedLocking, "why call this otherwise?");

#ifdef ASSERT
  assert_different_registers(obj_reg, mark_reg, temp_reg, temp2_reg);
#endif

  Label cas_label;

  // Branch to done if fast path fails and no slow_case provided.
  Label *slow_case_int = (slow_case != NULL) ? slow_case : &done;

  // Biased locking
  // See whether the lock is currently biased toward our thread and
  // whether the epoch is still valid
  // Note that the runtime guarantees sufficient alignment of JavaThread
  // pointers to allow age to be placed into low bits
  assert(markOopDesc::age_shift == markOopDesc::lock_bits + markOopDesc::biased_lock_bits,
         "biased locking makes assumptions about bit layout");

  if (PrintBiasedLockingStatistics) {
    load_const(temp_reg, (address) BiasedLocking::total_entry_count_addr(), temp2_reg);
    lwz(temp2_reg, 0, temp_reg);
    addi(temp2_reg, temp2_reg, 1);
    stw(temp2_reg, 0, temp_reg);
  }

  andi(temp_reg, mark_reg, markOopDesc::biased_lock_mask_in_place);
  cmpwi(cr_reg, temp_reg, markOopDesc::biased_lock_pattern);
  bne(cr_reg, cas_label);

  load_klass(temp_reg, obj_reg);

  load_const_optimized(temp2_reg, ~((int) markOopDesc::age_mask_in_place));
  ld(temp_reg, in_bytes(Klass::prototype_header_offset()), temp_reg);
  orr(temp_reg, R16_thread, temp_reg);
  xorr(temp_reg, mark_reg, temp_reg);
  andr(temp_reg, temp_reg, temp2_reg);
  cmpdi(cr_reg, temp_reg, 0);
  if (PrintBiasedLockingStatistics) {
    Label l;
    bne(cr_reg, l);
    load_const(mark_reg, (address) BiasedLocking::biased_lock_entry_count_addr());
    lwz(temp2_reg, 0, mark_reg);
    addi(temp2_reg, temp2_reg, 1);
    stw(temp2_reg, 0, mark_reg);
    // restore mark_reg
    ld(mark_reg, oopDesc::mark_offset_in_bytes(), obj_reg);
    bind(l);
  }
  beq(cr_reg, done);

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
  andi(temp2_reg, temp_reg, markOopDesc::biased_lock_mask_in_place);
  cmpwi(cr_reg, temp2_reg, 0);
  bne(cr_reg, try_revoke_bias);

  // Biasing is still enabled for this data type. See whether the
  // epoch of the current bias is still valid, meaning that the epoch
  // bits of the mark word are equal to the epoch bits of the
  // prototype header. (Note that the prototype header's epoch bits
  // only change at a safepoint.) If not, attempt to rebias the object
  // toward the current thread. Note that we must be absolutely sure
  // that the current epoch is invalid in order to do this because
  // otherwise the manipulations it performs on the mark word are
  // illegal.

  int shift_amount = 64 - markOopDesc::epoch_shift;
  // rotate epoch bits to right (little) end and set other bits to 0
  // [ big part | epoch | little part ] -> [ 0..0 | epoch ]
  rldicl_(temp2_reg, temp_reg, shift_amount, 64 - markOopDesc::epoch_bits);
  // branch if epoch bits are != 0, i.e. they differ, because the epoch has been incremented
  bne(CCR0, try_rebias);

  // The epoch of the current bias is still valid but we know nothing
  // about the owner; it might be set or it might be clear. Try to
  // acquire the bias of the object using an atomic operation. If this
  // fails we will go in to the runtime to revoke the object's bias.
  // Note that we first construct the presumed unbiased header so we
  // don't accidentally blow away another thread's valid bias.
  andi(mark_reg, mark_reg, (markOopDesc::biased_lock_mask_in_place |
                                markOopDesc::age_mask_in_place |
                                markOopDesc::epoch_mask_in_place));
  orr(temp_reg, R16_thread, mark_reg);

  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  // CmpxchgX sets cr_reg to cmpX(temp2_reg, mark_reg).
  fence(); // TODO: replace by MacroAssembler::MemBarRel | MacroAssembler::MemBarAcq ?
  cmpxchgd(/*flag=*/cr_reg, /*current_value=*/temp2_reg,
           /*compare_value=*/mark_reg, /*exchange_value=*/temp_reg,
           /*where=*/obj_reg,
           MacroAssembler::MemBarAcq,
           MacroAssembler::cmpxchgx_hint_acquire_lock(),
           noreg, slow_case_int); // bail out if failed

  // If the biasing toward our thread failed, this means that
  // another thread succeeded in biasing it toward itself and we
  // need to revoke that bias. The revocation will occur in the
  // interpreter runtime in the slow case.
  if (PrintBiasedLockingStatistics) {
    load_const(temp_reg, (address) BiasedLocking::anonymously_biased_lock_entry_count_addr(), temp2_reg);
    lwz(temp2_reg, 0, temp_reg);
    addi(temp2_reg, temp2_reg, 1);
    stw(temp2_reg, 0, temp_reg);
  }
  b(done);

  bind(try_rebias);
  // At this point we know the epoch has expired, meaning that the
  // current "bias owner", if any, is actually invalid. Under these
  // circumstances _only_, we are allowed to use the current header's
  // value as the comparison value when doing the cas to acquire the
  // bias in the current epoch. In other words, we allow transfer of
  // the bias from one thread to another directly in this situation.
  andi(temp_reg, mark_reg, markOopDesc::age_mask_in_place);
  orr(temp_reg, R16_thread, temp_reg);
  load_klass(temp2_reg, obj_reg);
  ld(temp2_reg, in_bytes(Klass::prototype_header_offset()), temp2_reg);
  orr(temp_reg, temp_reg, temp2_reg);

  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  // CmpxchgX sets cr_reg to cmpX(temp2_reg, mark_reg).
  fence(); // TODO: replace by MacroAssembler::MemBarRel | MacroAssembler::MemBarAcq ?
  cmpxchgd(/*flag=*/cr_reg, /*current_value=*/temp2_reg,
                 /*compare_value=*/mark_reg, /*exchange_value=*/temp_reg,
                 /*where=*/obj_reg,
                 MacroAssembler::MemBarAcq,
                 MacroAssembler::cmpxchgx_hint_acquire_lock(),
                 noreg, slow_case_int); // bail out if failed

  // If the biasing toward our thread failed, this means that
  // another thread succeeded in biasing it toward itself and we
  // need to revoke that bias. The revocation will occur in the
  // interpreter runtime in the slow case.
  if (PrintBiasedLockingStatistics) {
    load_const(temp_reg, (address) BiasedLocking::rebiased_lock_entry_count_addr(), temp2_reg);
    lwz(temp2_reg, 0, temp_reg);
    addi(temp2_reg, temp2_reg, 1);
    stw(temp2_reg, 0, temp_reg);
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
  load_klass(temp_reg, obj_reg);
  ld(temp_reg, in_bytes(Klass::prototype_header_offset()), temp_reg);
  andi(temp2_reg, mark_reg, markOopDesc::age_mask_in_place);
  orr(temp_reg, temp_reg, temp2_reg);

  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  // CmpxchgX sets cr_reg to cmpX(temp2_reg, mark_reg).
  fence(); // TODO: replace by MacroAssembler::MemBarRel | MacroAssembler::MemBarAcq ?
  cmpxchgd(/*flag=*/cr_reg, /*current_value=*/temp2_reg,
                 /*compare_value=*/mark_reg, /*exchange_value=*/temp_reg,
                 /*where=*/obj_reg,
                 MacroAssembler::MemBarAcq,
                 MacroAssembler::cmpxchgx_hint_acquire_lock());

  // reload markOop in mark_reg before continuing with lightweight locking
  ld(mark_reg, oopDesc::mark_offset_in_bytes(), obj_reg);

  // Fall through to the normal CAS-based lock, because no matter what
  // the result of the above CAS, some thread must have succeeded in
  // removing the bias bit from the object's header.
  if (PrintBiasedLockingStatistics) {
    Label l;
    bne(cr_reg, l);
    load_const(temp_reg, (address) BiasedLocking::revoked_lock_entry_count_addr(), temp2_reg);
    lwz(temp2_reg, 0, temp_reg);
    addi(temp2_reg, temp2_reg, 1);
    stw(temp2_reg, 0, temp_reg);
    bind(l);
  }

  bind(cas_label);
}

void MacroAssembler::biased_locking_exit (ConditionRegister cr_reg, Register mark_addr, Register temp_reg, Label& done) {
  // Check for biased locking unlock case, which is a no-op
  // Note: we do not have to check the thread ID for two reasons.
  // First, the interpreter checks for IllegalMonitorStateException at
  // a higher level. Second, if the bias was revoked while we held the
  // lock, the object could not be rebiased toward another thread, so
  // the bias bit would be clear.

  ld(temp_reg, 0, mark_addr);
  andi(temp_reg, temp_reg, markOopDesc::biased_lock_mask_in_place);

  cmpwi(cr_reg, temp_reg, markOopDesc::biased_lock_pattern);
  beq(cr_reg, done);
}

// "The box" is the space on the stack where we copy the object mark.
void MacroAssembler::compiler_fast_lock_object(ConditionRegister flag, Register oop, Register box,
                                               Register temp, Register displaced_header, Register current_header) {
  assert_different_registers(oop, box, temp, displaced_header, current_header);
  assert(flag != CCR0, "bad condition register");
  Label cont;
  Label object_has_monitor;
  Label cas_failed;

  // Load markOop from object into displaced_header.
  ld(displaced_header, oopDesc::mark_offset_in_bytes(), oop);


  // Always do locking in runtime.
  if (EmitSync & 0x01) {
    cmpdi(flag, oop, 0); // Oop can't be 0 here => always false.
    return;
  }

  if (UseBiasedLocking) {
    biased_locking_enter(flag, oop, displaced_header, temp, current_header, cont);
  }

  // Handle existing monitor.
  if ((EmitSync & 0x02) == 0) {
    // The object has an existing monitor iff (mark & monitor_value) != 0.
    andi_(temp, displaced_header, markOopDesc::monitor_value);
    bne(CCR0, object_has_monitor);
  }

  // Set displaced_header to be (markOop of object | UNLOCK_VALUE).
  ori(displaced_header, displaced_header, markOopDesc::unlocked_value);

  // Load Compare Value application register.

  // Initialize the box. (Must happen before we update the object mark!)
  std(displaced_header, BasicLock::displaced_header_offset_in_bytes(), box);

  // Must fence, otherwise, preceding store(s) may float below cmpxchg.
  // Compare object markOop with mark and if equal exchange scratch1 with object markOop.
  // CmpxchgX sets cr_reg to cmpX(current, displaced).
  membar(Assembler::StoreStore);
  cmpxchgd(/*flag=*/flag,
           /*current_value=*/current_header,
           /*compare_value=*/displaced_header,
           /*exchange_value=*/box,
           /*where=*/oop,
           MacroAssembler::MemBarAcq,
           MacroAssembler::cmpxchgx_hint_acquire_lock(),
           noreg,
           &cas_failed);
  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  // If the compare-and-exchange succeeded, then we found an unlocked
  // object and we have now locked it.
  b(cont);

  bind(cas_failed);
  // We did not see an unlocked object so try the fast recursive case.

  // Check if the owner is self by comparing the value in the markOop of object
  // (current_header) with the stack pointer.
  sub(current_header, current_header, R1_SP);
  load_const_optimized(temp, (address) (~(os::vm_page_size()-1) |
                                        markOopDesc::lock_mask_in_place));

  and_(R0/*==0?*/, current_header, temp);
  // If condition is true we are cont and hence we can store 0 as the
  // displaced header in the box, which indicates that it is a recursive lock.
  mcrf(flag,CCR0);
  std(R0/*==0, perhaps*/, BasicLock::displaced_header_offset_in_bytes(), box);

  // Handle existing monitor.
  if ((EmitSync & 0x02) == 0) {
    b(cont);

    bind(object_has_monitor);
    // The object's monitor m is unlocked iff m->owner == NULL,
    // otherwise m->owner may contain a thread or a stack address.
    //
    // Try to CAS m->owner from NULL to current thread.
    addi(temp, displaced_header, ObjectMonitor::owner_offset_in_bytes()-markOopDesc::monitor_value);
    li(displaced_header, 0);
    // CmpxchgX sets flag to cmpX(current, displaced).
    cmpxchgd(/*flag=*/flag,
             /*current_value=*/current_header,
             /*compare_value=*/displaced_header,
             /*exchange_value=*/R16_thread,
             /*where=*/temp,
             MacroAssembler::MemBarRel | MacroAssembler::MemBarAcq,
             MacroAssembler::cmpxchgx_hint_acquire_lock());

    // Store a non-null value into the box.
    std(box, BasicLock::displaced_header_offset_in_bytes(), box);

#   ifdef ASSERT
    bne(flag, cont);
    // We have acquired the monitor, check some invariants.
    addi(/*monitor=*/temp, temp, -ObjectMonitor::owner_offset_in_bytes());
    // Invariant 1: _recursions should be 0.
    //assert(ObjectMonitor::recursions_size_in_bytes() == 8, "unexpected size");
    asm_assert_mem8_is_zero(ObjectMonitor::recursions_offset_in_bytes(), temp,
                            "monitor->_recursions should be 0", -1);
    // Invariant 2: OwnerIsThread shouldn't be 0.
    //assert(ObjectMonitor::OwnerIsThread_size_in_bytes() == 4, "unexpected size");
    //asm_assert_mem4_isnot_zero(ObjectMonitor::OwnerIsThread_offset_in_bytes(), temp,
    //                           "monitor->OwnerIsThread shouldn't be 0", -1);
#   endif
  }

  bind(cont);
  // flag == EQ indicates success
  // flag == NE indicates failure
}

void MacroAssembler::compiler_fast_unlock_object(ConditionRegister flag, Register oop, Register box,
                                                 Register temp, Register displaced_header, Register current_header) {
  assert_different_registers(oop, box, temp, displaced_header, current_header);
  assert(flag != CCR0, "bad condition register");
  Label cont;
  Label object_has_monitor;

  // Always do locking in runtime.
  if (EmitSync & 0x01) {
    cmpdi(flag, oop, 0); // Oop can't be 0 here => always false.
    return;
  }

  if (UseBiasedLocking) {
    biased_locking_exit(flag, oop, current_header, cont);
  }

  // Find the lock address and load the displaced header from the stack.
  ld(displaced_header, BasicLock::displaced_header_offset_in_bytes(), box);

  // If the displaced header is 0, we have a recursive unlock.
  cmpdi(flag, displaced_header, 0);
  beq(flag, cont);

  // Handle existing monitor.
  if ((EmitSync & 0x02) == 0) {
    // The object has an existing monitor iff (mark & monitor_value) != 0.
    ld(current_header, oopDesc::mark_offset_in_bytes(), oop);
    andi(temp, current_header, markOopDesc::monitor_value);
    cmpdi(flag, temp, 0);
    bne(flag, object_has_monitor);
  }


  // Check if it is still a light weight lock, this is is true if we see
  // the stack address of the basicLock in the markOop of the object.
  // Cmpxchg sets flag to cmpd(current_header, box).
  cmpxchgd(/*flag=*/flag,
           /*current_value=*/current_header,
           /*compare_value=*/box,
           /*exchange_value=*/displaced_header,
           /*where=*/oop,
           MacroAssembler::MemBarRel,
           MacroAssembler::cmpxchgx_hint_release_lock(),
           noreg,
           &cont);

  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  // Handle existing monitor.
  if ((EmitSync & 0x02) == 0) {
    b(cont);

    bind(object_has_monitor);
    addi(current_header, current_header, -markOopDesc::monitor_value); // monitor
    ld(temp,             ObjectMonitor::owner_offset_in_bytes(), current_header);
    ld(displaced_header, ObjectMonitor::recursions_offset_in_bytes(), current_header);
    xorr(temp, R16_thread, temp);      // Will be 0 if we are the owner.
    orr(temp, temp, displaced_header); // Will be 0 if there are 0 recursions.
    cmpdi(flag, temp, 0);
    bne(flag, cont);

    ld(temp,             ObjectMonitor::EntryList_offset_in_bytes(), current_header);
    ld(displaced_header, ObjectMonitor::cxq_offset_in_bytes(), current_header);
    orr(temp, temp, displaced_header); // Will be 0 if both are 0.
    cmpdi(flag, temp, 0);
    bne(flag, cont);
    release();
    std(temp, ObjectMonitor::owner_offset_in_bytes(), current_header);
  }

  bind(cont);
  // flag == EQ indicates success
  // flag == NE indicates failure
}

// Write serialization page so VM thread can do a pseudo remote membar.
// We use the current thread pointer to calculate a thread specific
// offset to write to within the page. This minimizes bus traffic
// due to cache line collision.
void MacroAssembler::serialize_memory(Register thread, Register tmp1, Register tmp2) {
  srdi(tmp2, thread, os::get_serialize_page_shift_count());

  int mask = os::vm_page_size() - sizeof(int);
  if (Assembler::is_simm(mask, 16)) {
    andi(tmp2, tmp2, mask);
  } else {
    lis(tmp1, (int)((signed short) (mask >> 16)));
    ori(tmp1, tmp1, mask & 0x0000ffff);
    andr(tmp2, tmp2, tmp1);
  }

  load_const(tmp1, (long) os::get_memory_serialize_page());
  release();
  stwx(R0, tmp1, tmp2);
}


// GC barrier helper macros

// Write the card table byte if needed.
void MacroAssembler::card_write_barrier_post(Register Rstore_addr, Register Rnew_val, Register Rtmp) {
  CardTableModRefBS* bs = (CardTableModRefBS*) Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::CardTableModRef ||
         bs->kind() == BarrierSet::CardTableExtension, "wrong barrier");
#ifdef ASSERT
  cmpdi(CCR0, Rnew_val, 0);
  asm_assert_ne("null oop not allowed", 0x321);
#endif
  card_table_write(bs->byte_map_base, Rtmp, Rstore_addr);
}

// Write the card table byte.
void MacroAssembler::card_table_write(jbyte* byte_map_base, Register Rtmp, Register Robj) {
  assert_different_registers(Robj, Rtmp, R0);
  load_const_optimized(Rtmp, (address)byte_map_base, R0);
  srdi(Robj, Robj, CardTableModRefBS::card_shift);
  li(R0, 0); // dirty
  if (UseConcMarkSweepGC) membar(Assembler::StoreStore);
  stbx(R0, Rtmp, Robj);
}

#if INCLUDE_ALL_GCS
// General G1 pre-barrier generator.
// Goal: record the previous value if it is not null.
void MacroAssembler::g1_write_barrier_pre(Register Robj, RegisterOrConstant offset, Register Rpre_val,
                                          Register Rtmp1, Register Rtmp2, bool needs_frame) {
  Label runtime, filtered;

  // Is marking active?
  if (in_bytes(PtrQueue::byte_width_of_active()) == 4) {
    lwz(Rtmp1, in_bytes(JavaThread::satb_mark_queue_offset() + PtrQueue::byte_offset_of_active()), R16_thread);
  } else {
    guarantee(in_bytes(PtrQueue::byte_width_of_active()) == 1, "Assumption");
    lbz(Rtmp1, in_bytes(JavaThread::satb_mark_queue_offset() + PtrQueue::byte_offset_of_active()), R16_thread);
  }
  cmpdi(CCR0, Rtmp1, 0);
  beq(CCR0, filtered);

  // Do we need to load the previous value?
  if (Robj != noreg) {
    // Load the previous value...
    if (UseCompressedOops) {
      lwz(Rpre_val, offset, Robj);
    } else {
      ld(Rpre_val, offset, Robj);
    }
    // Previous value has been loaded into Rpre_val.
  }
  assert(Rpre_val != noreg, "must have a real register");

  // Is the previous value null?
  cmpdi(CCR0, Rpre_val, 0);
  beq(CCR0, filtered);

  if (Robj != noreg && UseCompressedOops) {
    decode_heap_oop_not_null(Rpre_val);
  }

  // OK, it's not filtered, so we'll need to call enqueue. In the normal
  // case, pre_val will be a scratch G-reg, but there are some cases in
  // which it's an O-reg. In the first case, do a normal call. In the
  // latter, do a save here and call the frameless version.

  // Can we store original value in the thread's buffer?
  // Is index == 0?
  // (The index field is typed as size_t.)
  const Register Rbuffer = Rtmp1, Rindex = Rtmp2;

  ld(Rindex, in_bytes(JavaThread::satb_mark_queue_offset() + PtrQueue::byte_offset_of_index()), R16_thread);
  cmpdi(CCR0, Rindex, 0);
  beq(CCR0, runtime); // If index == 0, goto runtime.
  ld(Rbuffer, in_bytes(JavaThread::satb_mark_queue_offset() + PtrQueue::byte_offset_of_buf()), R16_thread);

  addi(Rindex, Rindex, -wordSize); // Decrement index.
  std(Rindex, in_bytes(JavaThread::satb_mark_queue_offset() + PtrQueue::byte_offset_of_index()), R16_thread);

  // Record the previous value.
  stdx(Rpre_val, Rbuffer, Rindex);
  b(filtered);

  bind(runtime);

  // VM call need frame to access(write) O register.
  if (needs_frame) {
    save_LR_CR(Rtmp1);
    push_frame_reg_args(0, Rtmp2);
  }

  if (Rpre_val->is_volatile() && Robj == noreg) mr(R31, Rpre_val); // Save pre_val across C call if it was preloaded.
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), Rpre_val, R16_thread);
  if (Rpre_val->is_volatile() && Robj == noreg) mr(Rpre_val, R31); // restore

  if (needs_frame) {
    pop_frame();
    restore_LR_CR(Rtmp1);
  }

  bind(filtered);
}

// General G1 post-barrier generator
// Store cross-region card.
void MacroAssembler::g1_write_barrier_post(Register Rstore_addr, Register Rnew_val, Register Rtmp1, Register Rtmp2, Register Rtmp3, Label *filtered_ext) {
  Label runtime, filtered_int;
  Label& filtered = (filtered_ext != NULL) ? *filtered_ext : filtered_int;
  assert_different_registers(Rstore_addr, Rnew_val, Rtmp1, Rtmp2);

  G1SATBCardTableModRefBS* bs = (G1SATBCardTableModRefBS*) Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::G1SATBCT ||
         bs->kind() == BarrierSet::G1SATBCTLogging, "wrong barrier");

  // Does store cross heap regions?
  if (G1RSBarrierRegionFilter) {
    xorr(Rtmp1, Rstore_addr, Rnew_val);
    srdi_(Rtmp1, Rtmp1, HeapRegion::LogOfHRGrainBytes);
    beq(CCR0, filtered);
  }

  // Crosses regions, storing NULL?
#ifdef ASSERT
  cmpdi(CCR0, Rnew_val, 0);
  asm_assert_ne("null oop not allowed (G1)", 0x322); // Checked by caller on PPC64, so following branch is obsolete:
  //beq(CCR0, filtered);
#endif

  // Storing region crossing non-NULL, is card already dirty?
  assert(sizeof(*bs->byte_map_base) == sizeof(jbyte), "adjust this code");
  const Register Rcard_addr = Rtmp1;
  Register Rbase = Rtmp2;
  load_const_optimized(Rbase, (address)bs->byte_map_base, /*temp*/ Rtmp3);

  srdi(Rcard_addr, Rstore_addr, CardTableModRefBS::card_shift);

  // Get the address of the card.
  lbzx(/*card value*/ Rtmp3, Rbase, Rcard_addr);
  cmpwi(CCR0, Rtmp3, (int)G1SATBCardTableModRefBS::g1_young_card_val());
  beq(CCR0, filtered);

  membar(Assembler::StoreLoad);
  lbzx(/*card value*/ Rtmp3, Rbase, Rcard_addr);  // Reload after membar.
  cmpwi(CCR0, Rtmp3 /* card value */, CardTableModRefBS::dirty_card_val());
  beq(CCR0, filtered);

  // Storing a region crossing, non-NULL oop, card is clean.
  // Dirty card and log.
  li(Rtmp3, CardTableModRefBS::dirty_card_val());
  //release(); // G1: oops are allowed to get visible after dirty marking.
  stbx(Rtmp3, Rbase, Rcard_addr);

  add(Rcard_addr, Rbase, Rcard_addr); // This is the address which needs to get enqueued.
  Rbase = noreg; // end of lifetime

  const Register Rqueue_index = Rtmp2,
                 Rqueue_buf   = Rtmp3;
  ld(Rqueue_index, in_bytes(JavaThread::dirty_card_queue_offset() + PtrQueue::byte_offset_of_index()), R16_thread);
  cmpdi(CCR0, Rqueue_index, 0);
  beq(CCR0, runtime); // index == 0 then jump to runtime
  ld(Rqueue_buf, in_bytes(JavaThread::dirty_card_queue_offset() + PtrQueue::byte_offset_of_buf()), R16_thread);

  addi(Rqueue_index, Rqueue_index, -wordSize); // decrement index
  std(Rqueue_index, in_bytes(JavaThread::dirty_card_queue_offset() + PtrQueue::byte_offset_of_index()), R16_thread);

  stdx(Rcard_addr, Rqueue_buf, Rqueue_index); // store card
  b(filtered);

  bind(runtime);

  // Save the live input values.
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), Rcard_addr, R16_thread);

  bind(filtered_int);
}
#endif // INCLUDE_ALL_GCS

// Values for last_Java_pc, and last_Java_sp must comply to the rules
// in frame_ppc64.hpp.
void MacroAssembler::set_last_Java_frame(Register last_Java_sp, Register last_Java_pc) {
  // Always set last_Java_pc and flags first because once last_Java_sp
  // is visible has_last_Java_frame is true and users will look at the
  // rest of the fields. (Note: flags should always be zero before we
  // get here so doesn't need to be set.)

  // Verify that last_Java_pc was zeroed on return to Java
  asm_assert_mem8_is_zero(in_bytes(JavaThread::last_Java_pc_offset()), R16_thread,
                          "last_Java_pc not zeroed before leaving Java", 0x200);

  // When returning from calling out from Java mode the frame anchor's
  // last_Java_pc will always be set to NULL. It is set here so that
  // if we are doing a call to native (not VM) that we capture the
  // known pc and don't have to rely on the native call having a
  // standard frame linkage where we can find the pc.
  if (last_Java_pc != noreg)
    std(last_Java_pc, in_bytes(JavaThread::last_Java_pc_offset()), R16_thread);

  // Set last_Java_sp last.
  std(last_Java_sp, in_bytes(JavaThread::last_Java_sp_offset()), R16_thread);
}

void MacroAssembler::reset_last_Java_frame(void) {
  asm_assert_mem8_isnot_zero(in_bytes(JavaThread::last_Java_sp_offset()),
                             R16_thread, "SP was not set, still zero", 0x202);

  BLOCK_COMMENT("reset_last_Java_frame {");
  li(R0, 0);

  // _last_Java_sp = 0
  std(R0, in_bytes(JavaThread::last_Java_sp_offset()), R16_thread);

  // _last_Java_pc = 0
  std(R0, in_bytes(JavaThread::last_Java_pc_offset()), R16_thread);
  BLOCK_COMMENT("} reset_last_Java_frame");
}

void MacroAssembler::set_top_ijava_frame_at_SP_as_last_Java_frame(Register sp, Register tmp1) {
  assert_different_registers(sp, tmp1);

  // sp points to a TOP_IJAVA_FRAME, retrieve frame's PC via
  // TOP_IJAVA_FRAME_ABI.
  // FIXME: assert that we really have a TOP_IJAVA_FRAME here!
#ifdef CC_INTERP
  ld(tmp1/*pc*/, _top_ijava_frame_abi(frame_manager_lr), sp);
#else
  address entry = pc();
  load_const_optimized(tmp1, entry);
#endif

  set_last_Java_frame(/*sp=*/sp, /*pc=*/tmp1);
}

void MacroAssembler::get_vm_result(Register oop_result) {
  // Read:
  //   R16_thread
  //   R16_thread->in_bytes(JavaThread::vm_result_offset())
  //
  // Updated:
  //   oop_result
  //   R16_thread->in_bytes(JavaThread::vm_result_offset())

  ld(oop_result, in_bytes(JavaThread::vm_result_offset()), R16_thread);
  li(R0, 0);
  std(R0, in_bytes(JavaThread::vm_result_offset()), R16_thread);

  verify_oop(oop_result);
}

void MacroAssembler::get_vm_result_2(Register metadata_result) {
  // Read:
  //   R16_thread
  //   R16_thread->in_bytes(JavaThread::vm_result_2_offset())
  //
  // Updated:
  //   metadata_result
  //   R16_thread->in_bytes(JavaThread::vm_result_2_offset())

  ld(metadata_result, in_bytes(JavaThread::vm_result_2_offset()), R16_thread);
  li(R0, 0);
  std(R0, in_bytes(JavaThread::vm_result_2_offset()), R16_thread);
}


void MacroAssembler::encode_klass_not_null(Register dst, Register src) {
  Register current = (src != noreg) ? src : dst; // Klass is in dst if no src provided.
  if (Universe::narrow_klass_base() != 0) {
    // Use dst as temp if it is free.
    load_const(R0, Universe::narrow_klass_base(), (dst != current && dst != R0) ? dst : noreg);
    sub(dst, current, R0);
    current = dst;
  }
  if (Universe::narrow_klass_shift() != 0) {
    srdi(dst, current, Universe::narrow_klass_shift());
    current = dst;
  }
  mr_if_needed(dst, current); // Move may be required.
}

void MacroAssembler::store_klass(Register dst_oop, Register klass, Register ck) {
  if (UseCompressedClassPointers) {
    encode_klass_not_null(ck, klass);
    stw(ck, oopDesc::klass_offset_in_bytes(), dst_oop);
  } else {
    std(klass, oopDesc::klass_offset_in_bytes(), dst_oop);
  }
}

void MacroAssembler::store_klass_gap(Register dst_oop, Register val) {
  if (UseCompressedClassPointers) {
    if (val == noreg) {
      val = R0;
      li(val, 0);
    }
    stw(val, oopDesc::klass_gap_offset_in_bytes(), dst_oop); // klass gap if compressed
  }
}

int MacroAssembler::instr_size_for_decode_klass_not_null() {
  if (!UseCompressedClassPointers) return 0;
  int num_instrs = 1;  // shift or move
  if (Universe::narrow_klass_base() != 0) num_instrs = 7;  // shift + load const + add
  return num_instrs * BytesPerInstWord;
}

void MacroAssembler::decode_klass_not_null(Register dst, Register src) {
  if (src == noreg) src = dst;
  Register shifted_src = src;
  if (Universe::narrow_klass_shift() != 0 ||
      Universe::narrow_klass_base() == 0 && src != dst) {  // Move required.
    shifted_src = dst;
    sldi(shifted_src, src, Universe::narrow_klass_shift());
  }
  if (Universe::narrow_klass_base() != 0) {
    load_const(R0, Universe::narrow_klass_base());
    add(dst, shifted_src, R0);
  }
}

void MacroAssembler::load_klass(Register dst, Register src) {
  if (UseCompressedClassPointers) {
    lwz(dst, oopDesc::klass_offset_in_bytes(), src);
    // Attention: no null check here!
    decode_klass_not_null(dst, dst);
  } else {
    ld(dst, oopDesc::klass_offset_in_bytes(), src);
  }
}

void MacroAssembler::load_klass_with_trap_null_check(Register dst, Register src) {
  if (!os::zero_page_read_protected()) {
    if (TrapBasedNullChecks) {
      trap_null_check(src);
    }
  }
  load_klass(dst, src);
}

void MacroAssembler::reinit_heapbase(Register d, Register tmp) {
  if (Universe::heap() != NULL) {
    if (Universe::narrow_oop_base() == NULL) {
      Assembler::xorr(R30, R30, R30);
    } else {
      load_const(R30, Universe::narrow_ptrs_base(), tmp);
    }
  } else {
    load_const(R30, Universe::narrow_ptrs_base_addr(), tmp);
    ld(R30, 0, R30);
  }
}

// Clear Array
// Kills both input registers. tmp == R0 is allowed.
void MacroAssembler::clear_memory_doubleword(Register base_ptr, Register cnt_dwords, Register tmp) {
  // Procedure for large arrays (uses data cache block zero instruction).
    Label startloop, fast, fastloop, small_rest, restloop, done;
    const int cl_size         = VM_Version::get_cache_line_size(),
              cl_dwords       = cl_size>>3,
              cl_dw_addr_bits = exact_log2(cl_dwords),
              dcbz_min        = 1;                     // Min count of dcbz executions, needs to be >0.

//2:
    cmpdi(CCR1, cnt_dwords, ((dcbz_min+1)<<cl_dw_addr_bits)-1); // Big enough? (ensure >=dcbz_min lines included).
    blt(CCR1, small_rest);                                      // Too small.
    rldicl_(tmp, base_ptr, 64-3, 64-cl_dw_addr_bits);           // Extract dword offset within first cache line.
    beq(CCR0, fast);                                            // Already 128byte aligned.

    subfic(tmp, tmp, cl_dwords);
    mtctr(tmp);                        // Set ctr to hit 128byte boundary (0<ctr<cl_dwords).
    subf(cnt_dwords, tmp, cnt_dwords); // rest.
    li(tmp, 0);
//10:
  bind(startloop);                     // Clear at the beginning to reach 128byte boundary.
    std(tmp, 0, base_ptr);             // Clear 8byte aligned block.
    addi(base_ptr, base_ptr, 8);
    bdnz(startloop);
//13:
  bind(fast);                                  // Clear 128byte blocks.
    srdi(tmp, cnt_dwords, cl_dw_addr_bits);    // Loop count for 128byte loop (>0).
    andi(cnt_dwords, cnt_dwords, cl_dwords-1); // Rest in dwords.
    mtctr(tmp);                                // Load counter.
//16:
  bind(fastloop);
    dcbz(base_ptr);                    // Clear 128byte aligned block.
    addi(base_ptr, base_ptr, cl_size);
    bdnz(fastloop);
    if (InsertEndGroupPPC64) { endgroup(); } else { nop(); }
//20:
  bind(small_rest);
    cmpdi(CCR0, cnt_dwords, 0);        // size 0?
    beq(CCR0, done);                   // rest == 0
    li(tmp, 0);
    mtctr(cnt_dwords);                 // Load counter.
//24:
  bind(restloop);                      // Clear rest.
    std(tmp, 0, base_ptr);             // Clear 8byte aligned block.
    addi(base_ptr, base_ptr, 8);
    bdnz(restloop);
//27:
  bind(done);
}

/////////////////////////////////////////// String intrinsics ////////////////////////////////////////////

// Search for a single jchar in an jchar[].
//
// Assumes that result differs from all other registers.
//
// Haystack, needle are the addresses of jchar-arrays.
// NeedleChar is needle[0] if it is known at compile time.
// Haycnt is the length of the haystack. We assume haycnt >=1.
//
// Preserves haystack, haycnt, kills all other registers.
//
// If needle == R0, we search for the constant needleChar.
void MacroAssembler::string_indexof_1(Register result, Register haystack, Register haycnt,
                                      Register needle, jchar needleChar,
                                      Register tmp1, Register tmp2) {

  assert_different_registers(result, haystack, haycnt, needle, tmp1, tmp2);

  Label L_InnerLoop, L_FinalCheck, L_Found1, L_Found2, L_Found3, L_NotFound, L_End;
  Register needle0 = needle, // Contains needle[0].
           addr = tmp1,
           ch1 = tmp2,
           ch2 = R0;

//2 (variable) or 3 (const):
   if (needle != R0) lhz(needle0, 0, needle); // Preload needle character, needle has len==1.
   dcbtct(haystack, 0x00);                        // Indicate R/O access to haystack.

   srwi_(tmp2, haycnt, 1);   // Shift right by exact_log2(UNROLL_FACTOR).
   mr(addr, haystack);
   beq(CCR0, L_FinalCheck);
   mtctr(tmp2);              // Move to count register.
//8:
  bind(L_InnerLoop);             // Main work horse (2x unrolled search loop).
   lhz(ch1, 0, addr);        // Load characters from haystack.
   lhz(ch2, 2, addr);
   (needle != R0) ? cmpw(CCR0, ch1, needle0) : cmplwi(CCR0, ch1, needleChar);
   (needle != R0) ? cmpw(CCR1, ch2, needle0) : cmplwi(CCR1, ch2, needleChar);
   beq(CCR0, L_Found1);   // Did we find the needle?
   beq(CCR1, L_Found2);
   addi(addr, addr, 4);
   bdnz(L_InnerLoop);
//16:
  bind(L_FinalCheck);
   andi_(R0, haycnt, 1);
   beq(CCR0, L_NotFound);
   lhz(ch1, 0, addr);        // One position left at which we have to compare.
   (needle != R0) ? cmpw(CCR1, ch1, needle0) : cmplwi(CCR1, ch1, needleChar);
   beq(CCR1, L_Found3);
//21:
  bind(L_NotFound);
   li(result, -1);           // Not found.
   b(L_End);

  bind(L_Found2);
   addi(addr, addr, 2);
//24:
  bind(L_Found1);
  bind(L_Found3);                  // Return index ...
   subf(addr, haystack, addr); // relative to haystack,
   srdi(result, addr, 1);      // in characters.
  bind(L_End);
}


// Implementation of IndexOf for jchar arrays.
//
// The length of haystack and needle are not constant, i.e. passed in a register.
//
// Preserves registers haystack, needle.
// Kills registers haycnt, needlecnt.
// Assumes that result differs from all other registers.
// Haystack, needle are the addresses of jchar-arrays.
// Haycnt, needlecnt are the lengths of them, respectively.
//
// Needlecntval must be zero or 15-bit unsigned immediate and > 1.
void MacroAssembler::string_indexof(Register result, Register haystack, Register haycnt,
                                    Register needle, ciTypeArray* needle_values, Register needlecnt, int needlecntval,
                                    Register tmp1, Register tmp2, Register tmp3, Register tmp4) {

  // Ensure 0<needlecnt<=haycnt in ideal graph as prerequisite!
  Label L_TooShort, L_Found, L_NotFound, L_End;
  Register last_addr = haycnt, // Kill haycnt at the beginning.
           addr      = tmp1,
           n_start   = tmp2,
           ch1       = tmp3,
           ch2       = R0;

  // **************************************************************************************************
  // Prepare for main loop: optimized for needle count >=2, bail out otherwise.
  // **************************************************************************************************

//1 (variable) or 3 (const):
   dcbtct(needle, 0x00);    // Indicate R/O access to str1.
   dcbtct(haystack, 0x00);  // Indicate R/O access to str2.

  // Compute last haystack addr to use if no match gets found.
  if (needlecntval == 0) { // variable needlecnt
//3:
   subf(ch1, needlecnt, haycnt);      // Last character index to compare is haycnt-needlecnt.
   addi(addr, haystack, -2);          // Accesses use pre-increment.
   cmpwi(CCR6, needlecnt, 2);
   blt(CCR6, L_TooShort);          // Variable needlecnt: handle short needle separately.
   slwi(ch1, ch1, 1);                 // Scale to number of bytes.
   lwz(n_start, 0, needle);           // Load first 2 characters of needle.
   add(last_addr, haystack, ch1);     // Point to last address to compare (haystack+2*(haycnt-needlecnt)).
   addi(needlecnt, needlecnt, -2);    // Rest of needle.
  } else { // constant needlecnt
  guarantee(needlecntval != 1, "IndexOf with single-character needle must be handled separately");
  assert((needlecntval & 0x7fff) == needlecntval, "wrong immediate");
//5:
   addi(ch1, haycnt, -needlecntval);  // Last character index to compare is haycnt-needlecnt.
   lwz(n_start, 0, needle);           // Load first 2 characters of needle.
   addi(addr, haystack, -2);          // Accesses use pre-increment.
   slwi(ch1, ch1, 1);                 // Scale to number of bytes.
   add(last_addr, haystack, ch1);     // Point to last address to compare (haystack+2*(haycnt-needlecnt)).
   li(needlecnt, needlecntval-2);     // Rest of needle.
  }

  // Main Loop (now we have at least 3 characters).
//11:
  Label L_OuterLoop, L_InnerLoop, L_FinalCheck, L_Comp1, L_Comp2, L_Comp3;
  bind(L_OuterLoop); // Search for 1st 2 characters.
  Register addr_diff = tmp4;
   subf(addr_diff, addr, last_addr); // Difference between already checked address and last address to check.
   addi(addr, addr, 2);              // This is the new address we want to use for comparing.
   srdi_(ch2, addr_diff, 2);
   beq(CCR0, L_FinalCheck);       // 2 characters left?
   mtctr(ch2);                       // addr_diff/4
//16:
  bind(L_InnerLoop);                // Main work horse (2x unrolled search loop)
   lwz(ch1, 0, addr);           // Load 2 characters of haystack (ignore alignment).
   lwz(ch2, 2, addr);
   cmpw(CCR0, ch1, n_start); // Compare 2 characters (1 would be sufficient but try to reduce branches to CompLoop).
   cmpw(CCR1, ch2, n_start);
   beq(CCR0, L_Comp1);       // Did we find the needle start?
   beq(CCR1, L_Comp2);
   addi(addr, addr, 4);
   bdnz(L_InnerLoop);
//24:
  bind(L_FinalCheck);
   rldicl_(addr_diff, addr_diff, 64-1, 63); // Remaining characters not covered by InnerLoop: (addr_diff>>1)&1.
   beq(CCR0, L_NotFound);
   lwz(ch1, 0, addr);                       // One position left at which we have to compare.
   cmpw(CCR1, ch1, n_start);
   beq(CCR1, L_Comp3);
//29:
  bind(L_NotFound);
   li(result, -1); // not found
   b(L_End);


   // **************************************************************************************************
   // Special Case: unfortunately, the variable needle case can be called with needlecnt<2
   // **************************************************************************************************
//31:
 if ((needlecntval>>1) !=1 ) { // Const needlecnt is 2 or 3? Reduce code size.
  int nopcnt = 5;
  if (needlecntval !=0 ) ++nopcnt; // Balance alignment (other case: see below).
  if (needlecntval == 0) {         // We have to handle these cases separately.
  Label L_OneCharLoop;
  bind(L_TooShort);
   mtctr(haycnt);
   lhz(n_start, 0, needle);    // First character of needle
  bind(L_OneCharLoop);
   lhzu(ch1, 2, addr);
   cmpw(CCR1, ch1, n_start);
   beq(CCR1, L_Found);      // Did we find the one character needle?
   bdnz(L_OneCharLoop);
   li(result, -1);             // Not found.
   b(L_End);
  } // 8 instructions, so no impact on alignment.
  for (int x = 0; x < nopcnt; ++x) nop();
 }

  // **************************************************************************************************
  // Regular Case Part II: compare rest of needle (first 2 characters have been compared already)
  // **************************************************************************************************

  // Compare the rest
//36 if needlecntval==0, else 37:
  bind(L_Comp2);
   addi(addr, addr, 2); // First comparison has failed, 2nd one hit.
  bind(L_Comp1);            // Addr points to possible needle start.
  bind(L_Comp3);            // Could have created a copy and use a different return address but saving code size here.
  if (needlecntval != 2) {  // Const needlecnt==2?
   if (needlecntval != 3) {
    if (needlecntval == 0) beq(CCR6, L_Found); // Variable needlecnt==2?
    Register ind_reg = tmp4;
    li(ind_reg, 2*2);   // First 2 characters are already compared, use index 2.
    mtctr(needlecnt);   // Decremented by 2, still > 0.
//40:
   Label L_CompLoop;
   bind(L_CompLoop);
    lhzx(ch2, needle, ind_reg);
    lhzx(ch1, addr, ind_reg);
    cmpw(CCR1, ch1, ch2);
    bne(CCR1, L_OuterLoop);
    addi(ind_reg, ind_reg, 2);
    bdnz(L_CompLoop);
   } else { // No loop required if there's only one needle character left.
    lhz(ch2, 2*2, needle);
    lhz(ch1, 2*2, addr);
    cmpw(CCR1, ch1, ch2);
    bne(CCR1, L_OuterLoop);
   }
  }
  // Return index ...
//46:
  bind(L_Found);
   subf(addr, haystack, addr); // relative to haystack, ...
   srdi(result, addr, 1);      // in characters.
//48:
  bind(L_End);
}

// Implementation of Compare for jchar arrays.
//
// Kills the registers str1, str2, cnt1, cnt2.
// Kills cr0, ctr.
// Assumes that result differes from the input registers.
void MacroAssembler::string_compare(Register str1_reg, Register str2_reg, Register cnt1_reg, Register cnt2_reg,
                                    Register result_reg, Register tmp_reg) {
   assert_different_registers(result_reg, str1_reg, str2_reg, cnt1_reg, cnt2_reg, tmp_reg);

   Label Ldone, Lslow_case, Lslow_loop, Lfast_loop;
   Register cnt_diff = R0,
            limit_reg = cnt1_reg,
            chr1_reg = result_reg,
            chr2_reg = cnt2_reg,
            addr_diff = str2_reg;

   // Offset 0 should be 32 byte aligned.
//-4:
    dcbtct(str1_reg, 0x00);  // Indicate R/O access to str1.
    dcbtct(str2_reg, 0x00);  // Indicate R/O access to str2.
//-2:
   // Compute min(cnt1, cnt2) and check if 0 (bail out if we don't need to compare characters).
    subf(result_reg, cnt2_reg, cnt1_reg);  // difference between cnt1/2
    subf_(addr_diff, str1_reg, str2_reg);  // alias?
    beq(CCR0, Ldone);                   // return cnt difference if both ones are identical
    srawi(limit_reg, result_reg, 31);      // generate signmask (cnt1/2 must be non-negative so cnt_diff can't overflow)
    mr(cnt_diff, result_reg);
    andr(limit_reg, result_reg, limit_reg); // difference or zero (negative): cnt1<cnt2 ? cnt1-cnt2 : 0
    add_(limit_reg, cnt2_reg, limit_reg);  // min(cnt1, cnt2)==0?
    beq(CCR0, Ldone);                   // return cnt difference if one has 0 length

    lhz(chr1_reg, 0, str1_reg);            // optional: early out if first characters mismatch
    lhzx(chr2_reg, str1_reg, addr_diff);   // optional: early out if first characters mismatch
    addi(tmp_reg, limit_reg, -1);          // min(cnt1, cnt2)-1
    subf_(result_reg, chr2_reg, chr1_reg); // optional: early out if first characters mismatch
    bne(CCR0, Ldone);                   // optional: early out if first characters mismatch

   // Set loop counter by scaling down tmp_reg
    srawi_(chr2_reg, tmp_reg, exact_log2(4)); // (min(cnt1, cnt2)-1)/4
    ble(CCR0, Lslow_case);                 // need >4 characters for fast loop
    andi(limit_reg, tmp_reg, 4-1);            // remaining characters

   // Adapt str1_reg str2_reg for the first loop iteration
    mtctr(chr2_reg);                 // (min(cnt1, cnt2)-1)/4
    addi(limit_reg, limit_reg, 4+1); // compare last 5-8 characters in slow_case if mismatch found in fast_loop
//16:
   // Compare the rest of the characters
   bind(Lfast_loop);
    ld(chr1_reg, 0, str1_reg);
    ldx(chr2_reg, str1_reg, addr_diff);
    cmpd(CCR0, chr2_reg, chr1_reg);
    bne(CCR0, Lslow_case); // return chr1_reg
    addi(str1_reg, str1_reg, 4*2);
    bdnz(Lfast_loop);
    addi(limit_reg, limit_reg, -4); // no mismatch found in fast_loop, only 1-4 characters missing
//23:
   bind(Lslow_case);
    mtctr(limit_reg);
//24:
   bind(Lslow_loop);
    lhz(chr1_reg, 0, str1_reg);
    lhzx(chr2_reg, str1_reg, addr_diff);
    subf_(result_reg, chr2_reg, chr1_reg);
    bne(CCR0, Ldone); // return chr1_reg
    addi(str1_reg, str1_reg, 1*2);
    bdnz(Lslow_loop);
//30:
   // If strings are equal up to min length, return the length difference.
    mr(result_reg, cnt_diff);
    nop(); // alignment
//32:
   // Otherwise, return the difference between the first mismatched chars.
   bind(Ldone);
}


// Compare char[] arrays.
//
// str1_reg   USE only
// str2_reg   USE only
// cnt_reg    USE_DEF, due to tmp reg shortage
// result_reg DEF only, might compromise USE only registers
void MacroAssembler::char_arrays_equals(Register str1_reg, Register str2_reg, Register cnt_reg, Register result_reg,
                                        Register tmp1_reg, Register tmp2_reg, Register tmp3_reg, Register tmp4_reg,
                                        Register tmp5_reg) {

  // Str1 may be the same register as str2 which can occur e.g. after scalar replacement.
  assert_different_registers(result_reg, str1_reg, cnt_reg, tmp1_reg, tmp2_reg, tmp3_reg, tmp4_reg, tmp5_reg);
  assert_different_registers(result_reg, str2_reg, cnt_reg, tmp1_reg, tmp2_reg, tmp3_reg, tmp4_reg, tmp5_reg);

  // Offset 0 should be 32 byte aligned.
  Label Linit_cbc, Lcbc, Lloop, Ldone_true, Ldone_false;
  Register index_reg = tmp5_reg;
  Register cbc_iter  = tmp4_reg;

//-1:
  dcbtct(str1_reg, 0x00);  // Indicate R/O access to str1.
  dcbtct(str2_reg, 0x00);  // Indicate R/O access to str2.
//1:
  andi(cbc_iter, cnt_reg, 4-1);            // Remaining iterations after 4 java characters per iteration loop.
  li(index_reg, 0); // init
  li(result_reg, 0); // assume false
  srwi_(tmp2_reg, cnt_reg, exact_log2(4)); // Div: 4 java characters per iteration (main loop).

  cmpwi(CCR1, cbc_iter, 0);             // CCR1 = (cbc_iter==0)
  beq(CCR0, Linit_cbc);                 // too short
    mtctr(tmp2_reg);
//8:
    bind(Lloop);
      ldx(tmp1_reg, str1_reg, index_reg);
      ldx(tmp2_reg, str2_reg, index_reg);
      cmpd(CCR0, tmp1_reg, tmp2_reg);
      bne(CCR0, Ldone_false);  // Unequal char pair found -> done.
      addi(index_reg, index_reg, 4*sizeof(jchar));
      bdnz(Lloop);
//14:
  bind(Linit_cbc);
  beq(CCR1, Ldone_true);
    mtctr(cbc_iter);
//16:
    bind(Lcbc);
      lhzx(tmp1_reg, str1_reg, index_reg);
      lhzx(tmp2_reg, str2_reg, index_reg);
      cmpw(CCR0, tmp1_reg, tmp2_reg);
      bne(CCR0, Ldone_false);  // Unequal char pair found -> done.
      addi(index_reg, index_reg, 1*sizeof(jchar));
      bdnz(Lcbc);
    nop();
  bind(Ldone_true);
  li(result_reg, 1);
//24:
  bind(Ldone_false);
}


void MacroAssembler::char_arrays_equalsImm(Register str1_reg, Register str2_reg, int cntval, Register result_reg,
                                           Register tmp1_reg, Register tmp2_reg) {
  // Str1 may be the same register as str2 which can occur e.g. after scalar replacement.
  assert_different_registers(result_reg, str1_reg, tmp1_reg, tmp2_reg);
  assert_different_registers(result_reg, str2_reg, tmp1_reg, tmp2_reg);
  assert(sizeof(jchar) == 2, "must be");
  assert(cntval >= 0 && ((cntval & 0x7fff) == cntval), "wrong immediate");

  Label Ldone_false;

  if (cntval < 16) { // short case
    if (cntval != 0) li(result_reg, 0); // assume false

    const int num_bytes = cntval*sizeof(jchar);
    int index = 0;
    for (int next_index; (next_index = index + 8) <= num_bytes; index = next_index) {
      ld(tmp1_reg, index, str1_reg);
      ld(tmp2_reg, index, str2_reg);
      cmpd(CCR0, tmp1_reg, tmp2_reg);
      bne(CCR0, Ldone_false);
    }
    if (cntval & 2) {
      lwz(tmp1_reg, index, str1_reg);
      lwz(tmp2_reg, index, str2_reg);
      cmpw(CCR0, tmp1_reg, tmp2_reg);
      bne(CCR0, Ldone_false);
      index += 4;
    }
    if (cntval & 1) {
      lhz(tmp1_reg, index, str1_reg);
      lhz(tmp2_reg, index, str2_reg);
      cmpw(CCR0, tmp1_reg, tmp2_reg);
      bne(CCR0, Ldone_false);
    }
    // fallthrough: true
  } else {
    Label Lloop;
    Register index_reg = tmp1_reg;
    const int loopcnt = cntval/4;
    assert(loopcnt > 0, "must be");
    // Offset 0 should be 32 byte aligned.
    //2:
    dcbtct(str1_reg, 0x00);  // Indicate R/O access to str1.
    dcbtct(str2_reg, 0x00);  // Indicate R/O access to str2.
    li(tmp2_reg, loopcnt);
    li(index_reg, 0); // init
    li(result_reg, 0); // assume false
    mtctr(tmp2_reg);
    //8:
    bind(Lloop);
    ldx(R0, str1_reg, index_reg);
    ldx(tmp2_reg, str2_reg, index_reg);
    cmpd(CCR0, R0, tmp2_reg);
    bne(CCR0, Ldone_false);  // Unequal char pair found -> done.
    addi(index_reg, index_reg, 4*sizeof(jchar));
    bdnz(Lloop);
    //14:
    if (cntval & 2) {
      lwzx(R0, str1_reg, index_reg);
      lwzx(tmp2_reg, str2_reg, index_reg);
      cmpw(CCR0, R0, tmp2_reg);
      bne(CCR0, Ldone_false);
      if (cntval & 1) addi(index_reg, index_reg, 2*sizeof(jchar));
    }
    if (cntval & 1) {
      lhzx(R0, str1_reg, index_reg);
      lhzx(tmp2_reg, str2_reg, index_reg);
      cmpw(CCR0, R0, tmp2_reg);
      bne(CCR0, Ldone_false);
    }
    // fallthru: true
  }
  li(result_reg, 1);
  bind(Ldone_false);
}


void MacroAssembler::asm_assert(bool check_equal, const char *msg, int id) {
#ifdef ASSERT
  Label ok;
  if (check_equal) {
    beq(CCR0, ok);
  } else {
    bne(CCR0, ok);
  }
  stop(msg, id);
  bind(ok);
#endif
}

void MacroAssembler::asm_assert_mems_zero(bool check_equal, int size, int mem_offset,
                                          Register mem_base, const char* msg, int id) {
#ifdef ASSERT
  switch (size) {
    case 4:
      lwz(R0, mem_offset, mem_base);
      cmpwi(CCR0, R0, 0);
      break;
    case 8:
      ld(R0, mem_offset, mem_base);
      cmpdi(CCR0, R0, 0);
      break;
    default:
      ShouldNotReachHere();
  }
  asm_assert(check_equal, msg, id);
#endif // ASSERT
}

void MacroAssembler::verify_thread() {
  if (VerifyThread) {
    unimplemented("'VerifyThread' currently not implemented on PPC");
  }
}

// READ: oop. KILL: R0. Volatile floats perhaps.
void MacroAssembler::verify_oop(Register oop, const char* msg) {
  if (!VerifyOops) {
    return;
  }
  // Will be preserved.
  Register tmp = R11;
  assert(oop != tmp, "precondition");
  unsigned int nbytes_save = 10*8; // 10 volatile gprs
  address/* FunctionDescriptor** */fd = StubRoutines::verify_oop_subroutine_entry_address();
  // save tmp
  mr(R0, tmp);
  // kill tmp
  save_LR_CR(tmp);
  push_frame_reg_args(nbytes_save, tmp);
  // restore tmp
  mr(tmp, R0);
  save_volatile_gprs(R1_SP, 112); // except R0
  // load FunctionDescriptor** / entry_address *
  load_const(tmp, fd);
  // load FunctionDescriptor* / entry_address
  ld(tmp, 0, tmp);
  mr(R4_ARG2, oop);
  load_const(R3_ARG1, (address)msg);
  // call destination for its side effect
  call_c(tmp);
  restore_volatile_gprs(R1_SP, 112); // except R0
  pop_frame();
  // save tmp
  mr(R0, tmp);
  // kill tmp
  restore_LR_CR(tmp);
  // restore tmp
  mr(tmp, R0);
}

const char* stop_types[] = {
  "stop",
  "untested",
  "unimplemented",
  "shouldnotreachhere"
};

static void stop_on_request(int tp, const char* msg) {
  tty->print("PPC assembly code requires stop: (%s) %s\n", (void *)stop_types[tp%/*stop_end*/4], msg);
  guarantee(false, err_msg("PPC assembly code requires stop: %s", msg));
}

// Call a C-function that prints output.
void MacroAssembler::stop(int type, const char* msg, int id) {
#ifndef PRODUCT
  block_comment(err_msg("stop: %s %s {", stop_types[type%stop_end], msg));
#else
  block_comment("stop {");
#endif

  // setup arguments
  load_const_optimized(R3_ARG1, type);
  load_const_optimized(R4_ARG2, (void *)msg, /*tmp=*/R0);
  call_VM_leaf(CAST_FROM_FN_PTR(address, stop_on_request), R3_ARG1, R4_ARG2);
  illtrap();
  emit_int32(id);
  block_comment("} stop;");
}

#ifndef PRODUCT
// Write pattern 0x0101010101010101 in memory region [low-before, high+after].
// Val, addr are temp registers.
// If low == addr, addr is killed.
// High is preserved.
void MacroAssembler::zap_from_to(Register low, int before, Register high, int after, Register val, Register addr) {
  if (!ZapMemory) return;

  assert_different_registers(low, val);

  BLOCK_COMMENT("zap memory region {");
  load_const_optimized(val, 0x0101010101010101);
  int size = before + after;
  if (low == high && size < 5 && size > 0) {
    int offset = -before*BytesPerWord;
    for (int i = 0; i < size; ++i) {
      std(val, offset, low);
      offset += (1*BytesPerWord);
    }
  } else {
    addi(addr, low, -before*BytesPerWord);
    assert_different_registers(high, val);
    if (after) addi(high, high, after * BytesPerWord);
    Label loop;
    bind(loop);
    std(val, 0, addr);
    addi(addr, addr, 8);
    cmpd(CCR6, addr, high);
    ble(CCR6, loop);
    if (after) addi(high, high, -after * BytesPerWord);  // Correct back to old value.
  }
  BLOCK_COMMENT("} zap memory region");
}

#endif // !PRODUCT

SkipIfEqualZero::SkipIfEqualZero(MacroAssembler* masm, Register temp, const bool* flag_addr) : _masm(masm), _label() {
  int simm16_offset = masm->load_const_optimized(temp, (address)flag_addr, R0, true);
  assert(sizeof(bool) == 1, "PowerPC ABI");
  masm->lbz(temp, simm16_offset, temp);
  masm->cmpwi(CCR0, temp, 0);
  masm->beq(CCR0, _label);
}

SkipIfEqualZero::~SkipIfEqualZero() {
  _masm->bind(_label);
}
