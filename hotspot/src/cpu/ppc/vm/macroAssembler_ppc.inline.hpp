/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#ifndef CPU_PPC_VM_MACROASSEMBLER_PPC_INLINE_HPP
#define CPU_PPC_VM_MACROASSEMBLER_PPC_INLINE_HPP

#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "asm/codeBuffer.hpp"
#include "code/codeCache.hpp"

inline bool MacroAssembler::is_ld_largeoffset(address a) {
  const int inst1 = *(int *)a;
  const int inst2 = *(int *)(a+4);
  return (is_ld(inst1)) ||
         (is_addis(inst1) && is_ld(inst2) && inv_ra_field(inst2) == inv_rt_field(inst1));
}

inline int MacroAssembler::get_ld_largeoffset_offset(address a) {
  assert(MacroAssembler::is_ld_largeoffset(a), "must be ld with large offset");

  const int inst1 = *(int *)a;
  if (is_ld(inst1)) {
    return inv_d1_field(inst1);
  } else {
    const int inst2 = *(int *)(a+4);
    return (inv_d1_field(inst1) << 16) + inv_d1_field(inst2);
  }
}

inline void MacroAssembler::round_to(Register r, int modulus) {
  assert(is_power_of_2_long((jlong)modulus), "must be power of 2");
  addi(r, r, modulus-1);
  clrrdi(r, r, log2_long((jlong)modulus));
}

// Move register if destination register and target register are different.
inline void MacroAssembler::mr_if_needed(Register rd, Register rs) {
  if(rs !=rd) mr(rd, rs);
}

// Address of the global TOC.
inline address MacroAssembler::global_toc() {
  return CodeCache::low_bound();
}

// Offset of given address to the global TOC.
inline int MacroAssembler::offset_to_global_toc(const address addr) {
  intptr_t offset = (intptr_t)addr - (intptr_t)MacroAssembler::global_toc();
  assert(Assembler::is_simm((long)offset, 31) && offset >= 0, "must be in range");
  return (int)offset;
}

// Address of current method's TOC.
inline address MacroAssembler::method_toc() {
  return code()->consts()->start();
}

// Offset of given address to current method's TOC.
inline int MacroAssembler::offset_to_method_toc(address addr) {
  intptr_t offset = (intptr_t)addr - (intptr_t)method_toc();
  assert(is_simm((long)offset, 31) && offset >= 0, "must be in range");
  return (int)offset;
}

inline bool MacroAssembler::is_calculate_address_from_global_toc_at(address a, address bound) {
  const address inst2_addr = a;
  const int inst2 = *(int *) a;

  // The relocation points to the second instruction, the addi.
  if (!is_addi(inst2)) return false;

  // The addi reads and writes the same register dst.
  const int dst = inv_rt_field(inst2);
  if (inv_ra_field(inst2) != dst) return false;

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

  if (!(inst1 == 0 || inv_ra_field(inst1) == 29 /* R29 */)) return false;
  return is_addis(inst1);
}

#ifdef _LP64
// Detect narrow oop constants.
inline bool MacroAssembler::is_set_narrow_oop(address a, address bound) {
  const address inst2_addr = a;
  const int inst2 = *(int *)a;

  // The relocation points to the second instruction, the addi.
  if (!is_addi(inst2)) return false;

  // The addi reads and writes the same register dst.
  const int dst = inv_rt_field(inst2);
  if (inv_ra_field(inst2) != dst) return false;

  // Now, find the preceding addis which writes to dst.
  int inst1 = 0;
  address inst1_addr = inst2_addr - BytesPerInstWord;
  while (inst1_addr >= bound) {
    inst1 = *(int *) inst1_addr;
    if (is_lis(inst1) && inv_rs_field(inst1) == dst) return true;
    inst1_addr -= BytesPerInstWord;
  }
  return false;
}
#endif


inline bool MacroAssembler::is_load_const_at(address a) {
  const int* p_inst = (int *) a;
  bool b = is_lis(*p_inst++);
  if (is_ori(*p_inst)) {
    p_inst++;
    b = b && is_rldicr(*p_inst++); // TODO: could be made more precise: `sldi'!
    b = b && is_oris(*p_inst++);
    b = b && is_ori(*p_inst);
  } else if (is_lis(*p_inst)) {
    p_inst++;
    b = b && is_ori(*p_inst++);
    b = b && is_ori(*p_inst);
    // TODO: could enhance reliability by adding is_insrdi
  } else return false;
  return b;
}

inline void MacroAssembler::set_oop_constant(jobject obj, Register d) {
  set_oop(constant_oop_address(obj), d);
}

inline void MacroAssembler::set_oop(AddressLiteral obj_addr, Register d) {
  assert(obj_addr.rspec().type() == relocInfo::oop_type, "must be an oop reloc");
  load_const(d, obj_addr);
}

inline void MacroAssembler::pd_patch_instruction(address branch, address target) {
  jint& stub_inst = *(jint*) branch;
  stub_inst = patched_branch(target - branch, stub_inst, 0);
}

// Relocation of conditional far branches.
inline bool MacroAssembler::is_bc_far_variant1_at(address instruction_addr) {
  // Variant 1, the 1st instruction contains the destination address:
  //
  //    bcxx  DEST
  //    endgroup
  //
  const int instruction_1 = *(int*)(instruction_addr);
  const int instruction_2 = *(int*)(instruction_addr + 4);
  return is_bcxx(instruction_1) &&
         (inv_bd_field(instruction_1, (intptr_t)instruction_addr) != (intptr_t)(instruction_addr + 2*4)) &&
         is_endgroup(instruction_2);
}

// Relocation of conditional far branches.
inline bool MacroAssembler::is_bc_far_variant2_at(address instruction_addr) {
  // Variant 2, the 2nd instruction contains the destination address:
  //
  //    b!cxx SKIP
  //    bxx   DEST
  //  SKIP:
  //
  const int instruction_1 = *(int*)(instruction_addr);
  const int instruction_2 = *(int*)(instruction_addr + 4);
  return is_bcxx(instruction_1) &&
         (inv_bd_field(instruction_1, (intptr_t)instruction_addr) == (intptr_t)(instruction_addr + 2*4)) &&
         is_bxx(instruction_2);
}

// Relocation for conditional branches
inline bool MacroAssembler::is_bc_far_variant3_at(address instruction_addr) {
  // Variant 3, far cond branch to the next instruction, already patched to nops:
  //
  //    nop
  //    endgroup
  //  SKIP/DEST:
  //
  const int instruction_1 = *(int*)(instruction_addr);
  const int instruction_2 = *(int*)(instruction_addr + 4);
  return is_nop(instruction_1) &&
         is_endgroup(instruction_2);
}


// Convenience bc_far versions
inline void MacroAssembler::blt_far(ConditionRegister crx, Label& L, int optimize) { MacroAssembler::bc_far(bcondCRbiIs1, bi0(crx, less), L, optimize); }
inline void MacroAssembler::bgt_far(ConditionRegister crx, Label& L, int optimize) { MacroAssembler::bc_far(bcondCRbiIs1, bi0(crx, greater), L, optimize); }
inline void MacroAssembler::beq_far(ConditionRegister crx, Label& L, int optimize) { MacroAssembler::bc_far(bcondCRbiIs1, bi0(crx, equal), L, optimize); }
inline void MacroAssembler::bso_far(ConditionRegister crx, Label& L, int optimize) { MacroAssembler::bc_far(bcondCRbiIs1, bi0(crx, summary_overflow), L, optimize); }
inline void MacroAssembler::bge_far(ConditionRegister crx, Label& L, int optimize) { MacroAssembler::bc_far(bcondCRbiIs0, bi0(crx, less), L, optimize); }
inline void MacroAssembler::ble_far(ConditionRegister crx, Label& L, int optimize) { MacroAssembler::bc_far(bcondCRbiIs0, bi0(crx, greater), L, optimize); }
inline void MacroAssembler::bne_far(ConditionRegister crx, Label& L, int optimize) { MacroAssembler::bc_far(bcondCRbiIs0, bi0(crx, equal), L, optimize); }
inline void MacroAssembler::bns_far(ConditionRegister crx, Label& L, int optimize) { MacroAssembler::bc_far(bcondCRbiIs0, bi0(crx, summary_overflow), L, optimize); }

inline address MacroAssembler::call_stub(Register function_entry) {
  mtctr(function_entry);
  bctrl();
  return pc();
}

inline void MacroAssembler::call_stub_and_return_to(Register function_entry, Register return_pc) {
  assert_different_registers(function_entry, return_pc);
  mtlr(return_pc);
  mtctr(function_entry);
  bctr();
}

// Get the pc where the last emitted call will return to.
inline address MacroAssembler::last_calls_return_pc() {
  return _last_calls_return_pc;
}

// Read from the polling page, its address is already in a register.
inline void MacroAssembler::load_from_polling_page(Register polling_page_address, int offset) {
  ld(R0, offset, polling_page_address);
}

// Trap-instruction-based checks.

inline void MacroAssembler::trap_null_check(Register a, trap_to_bits cmp) {
  assert(TrapBasedNullChecks, "sanity");
  tdi(cmp, a/*reg a*/, 0);
}
inline void MacroAssembler::trap_zombie_not_entrant() {
  tdi(traptoUnconditional, 0/*reg 0*/, 1);
}
inline void MacroAssembler::trap_should_not_reach_here() {
  tdi_unchecked(traptoUnconditional, 0/*reg 0*/, 2);
}

inline void MacroAssembler::trap_ic_miss_check(Register a, Register b) {
  td(traptoGreaterThanUnsigned | traptoLessThanUnsigned, a, b);
}

// Do an explicit null check if access to a+offset will not raise a SIGSEGV.
// Either issue a trap instruction that raises SIGTRAP, or do a compare that
// branches to exception_entry.
// No support for compressed oops (base page of heap).  Does not distinguish
// loads and stores.
inline void MacroAssembler::null_check_throw(Register a, int offset, Register temp_reg, address exception_entry) {
  if (!ImplicitNullChecks || needs_explicit_null_check(offset) NOT_LINUX(|| true) /*!os::zero_page_read_protected()*/) {
    if (TrapBasedNullChecks) {
      assert(UseSIGTRAP, "sanity");
      trap_null_check(a);
    } else {
      Label ok;
      cmpdi(CCR0, a, 0);
      bne(CCR0, ok);
      load_const_optimized(temp_reg, exception_entry);
      mtctr(temp_reg);
      bctr();
      bind(ok);
    }
  }
}

inline void MacroAssembler::ld_with_trap_null_check(Register d, int si16, Register s1) {
  if ( NOT_LINUX(true) LINUX_ONLY(false)/*!os::zero_page_read_protected()*/) {
    if (TrapBasedNullChecks) {
      trap_null_check(s1);
    }
  }
  ld(d, si16, s1);
}

// Attention: No null check for loaded uncompressed OOP. Can be used for loading klass field.
inline void MacroAssembler::load_heap_oop_with_trap_null_check(Register d, RegisterOrConstant si16,
                                                                   Register s1) {
  if ( NOT_LINUX(true)LINUX_ONLY(false) /*!os::zero_page_read_protected()*/) {
    if (TrapBasedNullChecks) {
      trap_null_check(s1);
    }
  }
  load_heap_oop_not_null(d, si16, s1);
}

inline void MacroAssembler::load_heap_oop_not_null(Register d, RegisterOrConstant offs, Register s1) {
  if (UseCompressedOops) {
    lwz(d, offs, s1);
    // Attention: no null check here!
    decode_heap_oop_not_null(d);
  } else {
    ld(d, offs, s1);
  }
}

inline void MacroAssembler::load_heap_oop(Register d, RegisterOrConstant offs, Register s1) {
  if (UseCompressedOops) {
    lwz(d, offs, s1);
    decode_heap_oop(d);
  } else {
    ld(d, offs, s1);
  }
}

inline void MacroAssembler::encode_heap_oop_not_null(Register d) {
  if (Universe::narrow_oop_base() != NULL) {
    sub(d, d, R30);
  }
  if (Universe::narrow_oop_shift() != 0) {
    srdi(d, d, LogMinObjAlignmentInBytes);
  }
}

inline void MacroAssembler::decode_heap_oop_not_null(Register d) {
  if (Universe::narrow_oop_shift() != 0) {
    assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
    sldi(d, d, LogMinObjAlignmentInBytes);
  }
  if (Universe::narrow_oop_base() != NULL) {
    add(d, d, R30);
  }
}

inline void MacroAssembler::decode_heap_oop(Register d) {
  Label isNull;
  if (Universe::narrow_oop_base() != NULL) {
    cmpwi(CCR0, d, 0);
    beq(CCR0, isNull);
  }
  if (Universe::narrow_oop_shift() != 0) {
    assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
    sldi(d, d, LogMinObjAlignmentInBytes);
  }
  if (Universe::narrow_oop_base() != NULL) {
    add(d, d, R30);
  }
  bind(isNull);
}

// SIGTRAP-based range checks for arrays.
inline void MacroAssembler::trap_range_check_l(Register a, Register b) {
  tw (traptoLessThanUnsigned,                  a/*reg a*/, b/*reg b*/);
}
inline void MacroAssembler::trap_range_check_l(Register a, int si16) {
  twi(traptoLessThanUnsigned,                  a/*reg a*/, si16);
}
inline void MacroAssembler::trap_range_check_le(Register a, int si16) {
  twi(traptoEqual | traptoLessThanUnsigned,    a/*reg a*/, si16);
}
inline void MacroAssembler::trap_range_check_g(Register a, int si16) {
  twi(traptoGreaterThanUnsigned,               a/*reg a*/, si16);
}
inline void MacroAssembler::trap_range_check_ge(Register a, Register b) {
  tw (traptoEqual | traptoGreaterThanUnsigned, a/*reg a*/, b/*reg b*/);
}
inline void MacroAssembler::trap_range_check_ge(Register a, int si16) {
  twi(traptoEqual | traptoGreaterThanUnsigned, a/*reg a*/, si16);
}

#endif // CPU_PPC_VM_MACROASSEMBLER_PPC_INLINE_HPP
