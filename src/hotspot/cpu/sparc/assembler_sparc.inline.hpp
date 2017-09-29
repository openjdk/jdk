/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_ASSEMBLER_SPARC_INLINE_HPP
#define CPU_SPARC_VM_ASSEMBLER_SPARC_INLINE_HPP

#include "asm/assembler.hpp"


inline void Assembler::avoid_pipeline_stall() {
#ifdef VALIDATE_PIPELINE
  if (_hazard_state == PcHazard) {
    assert(is_cbcond_before() || is_rdpc_before(), "PC-hazard not preceeded by CBCOND or RDPC.");
    assert_no_delay("Must not have PC-hazard state in delay-slot.");
    nop();
    _hazard_state = NoHazard;
  }
#endif

  bool post_cond = is_cbcond_before();
  bool post_rdpc = is_rdpc_before();

  if (post_cond || post_rdpc) {
    nop();
#ifdef VALIDATE_PIPELINE
    if (_hazard_state != PcHazard) {
      assert(post_cond, "CBCOND before when no hazard @0x%p\n", pc());
      assert(post_rdpc, "RDPC before when no hazard @0x%p\n", pc());
    }
#endif
  }
}

inline void Assembler::check_delay() {
#ifdef VALIDATE_PIPELINE
  guarantee(_delay_state != AtDelay, "Use delayed() when filling delay-slot");
  _delay_state = NoDelay;
#endif
}

inline void Assembler::emit_int32(int32_t x) {
  check_delay();
#ifdef VALIDATE_PIPELINE
  _hazard_state = NoHazard;
#endif
  AbstractAssembler::emit_int32(x);
}

inline void Assembler::emit_data(int32_t x) {
  emit_int32(x);
}

inline void Assembler::emit_data(int32_t x, relocInfo::relocType rtype) {
  relocate(rtype);
  emit_int32(x);
}

inline void Assembler::emit_data(int32_t x, RelocationHolder const &rspec) {
  relocate(rspec);
  emit_int32(x);
}


inline void Assembler::add(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(add_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::add(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(add_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::addcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(add_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::addcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(add_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::addc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(addc_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::addc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(addc_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::addccc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(addc_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::addccc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(addc_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::aes_eround01(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes4_op3) | fs1(s1, FloatRegisterImpl::D) | fs3(s3, FloatRegisterImpl::D) | op5(aes_eround01_op5) | fs2(s2, FloatRegisterImpl::D));
}
inline void Assembler::aes_eround23(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes4_op3) | fs1(s1, FloatRegisterImpl::D) | fs3(s3, FloatRegisterImpl::D) | op5(aes_eround23_op5) | fs2(s2, FloatRegisterImpl::D));
}
inline void Assembler::aes_dround01(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes4_op3) | fs1(s1, FloatRegisterImpl::D) | fs3(s3, FloatRegisterImpl::D) | op5(aes_dround01_op5) | fs2(s2, FloatRegisterImpl::D));
}
inline void Assembler::aes_dround23(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes4_op3) | fs1(s1, FloatRegisterImpl::D) | fs3(s3, FloatRegisterImpl::D) | op5(aes_dround23_op5) | fs2(s2, FloatRegisterImpl::D));
}
inline void Assembler::aes_eround01_l(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes4_op3) | fs1(s1, FloatRegisterImpl::D) | fs3(s3, FloatRegisterImpl::D) | op5(aes_eround01_l_op5) | fs2(s2, FloatRegisterImpl::D));
}
inline void Assembler::aes_eround23_l(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes4_op3) | fs1(s1, FloatRegisterImpl::D) | fs3(s3, FloatRegisterImpl::D) | op5(aes_eround23_l_op5) | fs2(s2, FloatRegisterImpl::D));
}
inline void Assembler::aes_dround01_l(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes4_op3) | fs1(s1, FloatRegisterImpl::D) | fs3(s3, FloatRegisterImpl::D) | op5(aes_dround01_l_op5) | fs2(s2, FloatRegisterImpl::D));
}
inline void Assembler::aes_dround23_l(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes4_op3) | fs1(s1, FloatRegisterImpl::D) | fs3(s3, FloatRegisterImpl::D) | op5(aes_dround23_l_op5) | fs2(s2, FloatRegisterImpl::D));
}
inline void Assembler::aes_kexpand1(FloatRegister s1, FloatRegister s2, int imm5a, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes4_op3) | fs1(s1, FloatRegisterImpl::D) | u_field(imm5a, 13, 9) | op5(aes_kexpand1_op5) | fs2(s2, FloatRegisterImpl::D));
}

// 3-operand AES instructions

inline void Assembler::aes_kexpand0(FloatRegister s1, FloatRegister s2, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes3_op3) | fs1(s1, FloatRegisterImpl::D) | opf(aes_kexpand0_opf) | fs2(s2, FloatRegisterImpl::D));
}
inline void Assembler::aes_kexpand2(FloatRegister s1, FloatRegister s2, FloatRegister d) {
  aes_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(aes3_op3) | fs1(s1, FloatRegisterImpl::D) | opf(aes_kexpand2_opf) | fs2(s2, FloatRegisterImpl::D));
}

inline void Assembler::bpr(RCondition c, bool a, Predict p, Register s1, address d, relocInfo::relocType rt) {
  avoid_pipeline_stall();
  cti();
  emit_data(op(branch_op) | annul(a) | cond(c) | op2(bpr_op2) | wdisp16(intptr_t(d), intptr_t(pc())) | predict(p) | rs1(s1), rt);
  induce_delay_slot();
}
inline void Assembler::bpr(RCondition c, bool a, Predict p, Register s1, Label &L) {
  // Note[+]: All assembly emit routines using the 'target()' branch back-patch
  //     resolver must call 'avoid_pipeline_stall()' prior to calling 'target()'
  //     (we must do so even though the call will be made, as here, in the above
  //     implementation of 'bpr()', invoked below). The reason is the assumption
  //     made in 'target()', where using the current PC as the address for back-
  //     patching prevents any additional code to be emitted _after_ the address
  //     has been set (implicitly) in order to refer to the correct instruction.
  avoid_pipeline_stall();
  bpr(c, a, p, s1, target(L));
}

inline void Assembler::fb(Condition c, bool a, address d, relocInfo::relocType rt) {
  v9_dep();
  avoid_pipeline_stall();
  cti();
  emit_data(op(branch_op) | annul(a) | cond(c) | op2(fb_op2) | wdisp(intptr_t(d), intptr_t(pc()), 22), rt);
  induce_delay_slot();
}
inline void Assembler::fb(Condition c, bool a, Label &L) {
  avoid_pipeline_stall();
  fb(c, a, target(L));
}

inline void Assembler::fbp(Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt) {
  avoid_pipeline_stall();
  cti();
  emit_data(op(branch_op) | annul(a) | cond(c) | op2(fbp_op2) | branchcc(cc) | predict(p) | wdisp(intptr_t(d), intptr_t(pc()), 19), rt);
  induce_delay_slot();
}
inline void Assembler::fbp(Condition c, bool a, CC cc, Predict p, Label &L) {
  avoid_pipeline_stall();
  fbp(c, a, cc, p, target(L));
}

inline void Assembler::br(Condition c, bool a, address d, relocInfo::relocType rt) {
  v9_dep();
  avoid_pipeline_stall();
  cti();
  emit_data(op(branch_op) | annul(a) | cond(c) | op2(br_op2) | wdisp(intptr_t(d), intptr_t(pc()), 22), rt);
  induce_delay_slot();
}
inline void Assembler::br(Condition c, bool a, Label &L) {
  avoid_pipeline_stall();
  br(c, a, target(L));
}

inline void Assembler::bp(Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt) {
  avoid_pipeline_stall();
  cti();
  emit_data(op(branch_op) | annul(a) | cond(c) | op2(bp_op2) | branchcc(cc) | predict(p) | wdisp(intptr_t(d), intptr_t(pc()), 19), rt);
  induce_delay_slot();
}
inline void Assembler::bp(Condition c, bool a, CC cc, Predict p, Label &L) {
  avoid_pipeline_stall();
  bp(c, a, cc, p, target(L));
}

// compare and branch
inline void Assembler::cbcond(Condition c, CC cc, Register s1, Register s2, Label &L) {
  avoid_pipeline_stall();
  cti();
  emit_data(op(branch_op) | cond_cbcond(c) | op2(bpr_op2) | branchcc(cc) | wdisp10(intptr_t(target(L)), intptr_t(pc())) | rs1(s1) | rs2(s2));
  induce_pc_hazard();
}
inline void Assembler::cbcond(Condition c, CC cc, Register s1, int simm5, Label &L) {
  avoid_pipeline_stall();
  cti();
  emit_data(op(branch_op) | cond_cbcond(c) | op2(bpr_op2) | branchcc(cc) | wdisp10(intptr_t(target(L)), intptr_t(pc())) | rs1(s1) | immed(true) | simm(simm5, 5));
  induce_pc_hazard();
}

inline void Assembler::call(address d, relocInfo::relocType rt) {
  avoid_pipeline_stall();
  cti();
  emit_data(op(call_op) | wdisp(intptr_t(d), intptr_t(pc()), 30), rt);
  induce_delay_slot();
  assert(rt != relocInfo::virtual_call_type, "must use virtual_call_Relocation::spec");
}
inline void Assembler::call(Label &L, relocInfo::relocType rt) {
  avoid_pipeline_stall();
  call(target(L), rt);
}

inline void Assembler::call(address d, RelocationHolder const &rspec) {
  avoid_pipeline_stall();
  cti();
  emit_data(op(call_op) | wdisp(intptr_t(d), intptr_t(pc()), 30), rspec);
  induce_delay_slot();
  assert(rspec.type() != relocInfo::virtual_call_type, "must use virtual_call_Relocation::spec");
}

inline void Assembler::casa(Register s1, Register s2, Register d, int ia) {
  emit_int32(op(ldst_op) | rd(d) | op3(casa_op3) | rs1(s1) | (ia == -1 ? immed(true) : imm_asi(ia)) | rs2(s2));
}
inline void Assembler::casxa(Register s1, Register s2, Register d, int ia) {
  emit_int32(op(ldst_op) | rd(d) | op3(casxa_op3) | rs1(s1) | (ia == -1 ? immed(true) : imm_asi(ia)) | rs2(s2));
}

inline void Assembler::udiv(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(udiv_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::udiv(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(udiv_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::sdiv(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sdiv_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::sdiv(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sdiv_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::udivcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(udiv_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::udivcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(udiv_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::sdivcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sdiv_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::sdivcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sdiv_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::done() {
  cti();
  emit_int32(op(arith_op) | fcn(0) | op3(done_op3));
}
inline void Assembler::retry() {
  cti();
  emit_int32(op(arith_op) | fcn(1) | op3(retry_op3));
}

inline void Assembler::fadd(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | fs1(s1, w) | opf(0x40 + w) | fs2(s2, w));
}
inline void Assembler::fsub(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | fs1(s1, w) | opf(0x44 + w) | fs2(s2, w));
}

inline void Assembler::fcmp(FloatRegisterImpl::Width w, CC cc, FloatRegister s1, FloatRegister s2) {
  emit_int32(op(arith_op) | cmpcc(cc) | op3(fpop2_op3) | fs1(s1, w) | opf(0x50 + w) | fs2(s2, w));
}
inline void Assembler::fcmpe(FloatRegisterImpl::Width w, CC cc, FloatRegister s1, FloatRegister s2) {
  emit_int32(op(arith_op) | cmpcc(cc) | op3(fpop2_op3) | fs1(s1, w) | opf(0x54 + w) | fs2(s2, w));
}

inline void Assembler::ftox(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(fpop1_op3) | opf(0x80 + w) | fs2(s, w));
}
inline void Assembler::ftoi(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::S) | op3(fpop1_op3) | opf(0xd0 + w) | fs2(s, w));
}

inline void Assembler::ftof(FloatRegisterImpl::Width sw, FloatRegisterImpl::Width dw, FloatRegister s, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, dw) | op3(fpop1_op3) | opf(0xc0 + sw + dw*4) | fs2(s, sw));
}

inline void Assembler::fxtof(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x80 + w*4) | fs2(s, FloatRegisterImpl::D));
}
inline void Assembler::fitof(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0xc0 + w*4) | fs2(s, FloatRegisterImpl::S));
}

inline void Assembler::fmov(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x00 + w) | fs2(s, w));
}
inline void Assembler::fneg(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x04 + w) | fs2(s, w));
}
inline void Assembler::fabs(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x08 + w) | fs2(s, w));
}
inline void Assembler::fmul(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | fs1(s1, w) | opf(0x48 + w) | fs2(s2, w));
}
inline void Assembler::fmul(FloatRegisterImpl::Width sw, FloatRegisterImpl::Width dw, FloatRegister s1, FloatRegister s2, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, dw) | op3(fpop1_op3) | fs1(s1, sw) | opf(0x60 + sw + dw*4) | fs2(s2, sw));
}
inline void Assembler::fdiv(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | fs1(s1, w) | opf(0x4c + w) | fs2(s2, w));
}

inline void Assembler::fxor(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d) {
  vis1_only();
  emit_int32(op(arith_op) | fd(d, w) | op3(flog3_op3) | fs1(s1, w) | opf(0x6E - w) | fs2(s2, w));
}

inline void Assembler::fsqrt(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x28 + w) | fs2(s, w));
}

inline void Assembler::fmadd(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  fmaf_only();
  emit_int32(op(arith_op) | fd(d, w) | op3(stpartialf_op3) | fs1(s1, w) | fs3(s3, w) | op5(w) | fs2(s2, w));
}
inline void Assembler::fmsub(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  fmaf_only();
  emit_int32(op(arith_op) | fd(d, w) | op3(stpartialf_op3) | fs1(s1, w) | fs3(s3, w) | op5(0x4 + w) | fs2(s2, w));
}

inline void Assembler::fnmadd(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  fmaf_only();
  emit_int32(op(arith_op) | fd(d, w) | op3(stpartialf_op3) | fs1(s1, w) | fs3(s3, w) | op5(0xc + w) | fs2(s2, w));
}
inline void Assembler::fnmsub(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d) {
  fmaf_only();
  emit_int32(op(arith_op) | fd(d, w) | op3(stpartialf_op3) | fs1(s1, w) | fs3(s3, w) | op5(0x8 + w) | fs2(s2, w));
}

inline void Assembler::flush(Register s1, Register s2) {
  emit_int32(op(arith_op) | op3(flush_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::flush(Register s1, int simm13a) {
  emit_data(op(arith_op) | op3(flush_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::flushw() {
  emit_int32(op(arith_op) | op3(flushw_op3));
}

inline void Assembler::illtrap(int const22a) {
  emit_int32(op(branch_op) | u_field(const22a, 21, 0));
}

inline void Assembler::impdep1(int id1, int const19a) {
  emit_int32(op(arith_op) | fcn(id1) | op3(impdep1_op3) | u_field(const19a, 18, 0));
}
inline void Assembler::impdep2(int id1, int const19a) {
  emit_int32(op(arith_op) | fcn(id1) | op3(impdep2_op3) | u_field(const19a, 18, 0));
}

inline void Assembler::jmpl(Register s1, Register s2, Register d) {
  avoid_pipeline_stall();
  cti();
  emit_int32(op(arith_op) | rd(d) | op3(jmpl_op3) | rs1(s1) | rs2(s2));
  induce_delay_slot();
}
inline void Assembler::jmpl(Register s1, int simm13a, Register d, RelocationHolder const &rspec) {
  avoid_pipeline_stall();
  cti();
  emit_data(op(arith_op) | rd(d) | op3(jmpl_op3) | rs1(s1) | immed(true) | simm(simm13a, 13), rspec);
  induce_delay_slot();
}

inline void Assembler::ldf(FloatRegisterImpl::Width w, Register s1, Register s2, FloatRegister d) {
  emit_int32(op(ldst_op) | fd(d, w) | alt_op3(ldf_op3, w) | rs1(s1) | rs2(s2));
}
inline void Assembler::ldf(FloatRegisterImpl::Width w, Register s1, int simm13a, FloatRegister d, RelocationHolder const &rspec) {
  emit_data(op(ldst_op) | fd(d, w) | alt_op3(ldf_op3, w) | rs1(s1) | immed(true) | simm(simm13a, 13), rspec);
}

inline void Assembler::ldd(Register s1, Register s2, FloatRegister d) {
  assert(d->is_even(), "not even");
  ldf(FloatRegisterImpl::D, s1, s2, d);
}
inline void Assembler::ldd(Register s1, int simm13a, FloatRegister d) {
  assert(d->is_even(), "not even");
  ldf(FloatRegisterImpl::D, s1, simm13a, d);
}

inline void Assembler::ldxfsr(Register s1, Register s2) {
  emit_int32(op(ldst_op) | rd(G1) | op3(ldfsr_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::ldxfsr(Register s1, int simm13a) {
  emit_data(op(ldst_op) | rd(G1) | op3(ldfsr_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::ldfa(FloatRegisterImpl::Width w, Register s1, Register s2, int ia, FloatRegister d) {
  emit_int32(op(ldst_op) | fd(d, w) | alt_op3(ldf_op3 | alt_bit_op3, w) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::ldfa(FloatRegisterImpl::Width w, Register s1, int simm13a, FloatRegister d) {
  emit_int32(op(ldst_op) | fd(d, w) | alt_op3(ldf_op3 | alt_bit_op3, w) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::ldsb(Register s1, Register s2, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldsb_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::ldsb(Register s1, int simm13a, Register d) {
  emit_data(op(ldst_op) | rd(d) | op3(ldsb_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::ldsh(Register s1, Register s2, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldsh_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::ldsh(Register s1, int simm13a, Register d) {
  emit_data(op(ldst_op) | rd(d) | op3(ldsh_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::ldsw(Register s1, Register s2, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldsw_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::ldsw(Register s1, int simm13a, Register d) {
  emit_data(op(ldst_op) | rd(d) | op3(ldsw_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::ldub(Register s1, Register s2, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldub_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::ldub(Register s1, int simm13a, Register d) {
  emit_data(op(ldst_op) | rd(d) | op3(ldub_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::lduh(Register s1, Register s2, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(lduh_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::lduh(Register s1, int simm13a, Register d) {
  emit_data(op(ldst_op) | rd(d) | op3(lduh_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::lduw(Register s1, Register s2, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(lduw_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::lduw(Register s1, int simm13a, Register d) {
  emit_data(op(ldst_op) | rd(d) | op3(lduw_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::ldx(Register s1, Register s2, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldx_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::ldx(Register s1, int simm13a, Register d) {
  emit_data(op(ldst_op) | rd(d) | op3(ldx_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::ldsba(Register s1, Register s2, int ia, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldsb_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::ldsba(Register s1, int simm13a, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldsb_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::ldsha(Register s1, Register s2, int ia, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldsh_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::ldsha(Register s1, int simm13a, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldsh_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::ldswa(Register s1, Register s2, int ia, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldsw_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::ldswa(Register s1, int simm13a, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldsw_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::lduba(Register s1, Register s2, int ia, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldub_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::lduba(Register s1, int simm13a, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldub_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::lduha(Register s1, Register s2, int ia, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(lduh_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::lduha(Register s1, int simm13a, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(lduh_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::lduwa(Register s1, Register s2, int ia, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(lduw_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::lduwa(Register s1, int simm13a, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(lduw_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::ldxa(Register s1, Register s2, int ia, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldx_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::ldxa(Register s1, int simm13a, Register d) {
  emit_int32(op(ldst_op) | rd(d) | op3(ldx_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::and3(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(and_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::and3(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(and_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::andcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(and_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::andcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(and_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::andn(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(andn_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::andn(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(andn_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::andncc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(andn_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::andncc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(andn_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::or3(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(or_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::or3(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(or_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::orcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(or_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::orcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(or_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::orn(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(orn_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::orn(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(orn_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::orncc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(orn_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::orncc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(orn_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::xor3(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(xor_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::xor3(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(xor_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::xorcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(xor_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::xorcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(xor_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::xnor(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(xnor_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::xnor(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(xnor_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::xnorcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(xnor_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::xnorcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(xnor_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::membar(Membar_mask_bits const7a) {
  emit_int32(op(arith_op) | op3(membar_op3) | rs1(O7) | immed(true) | u_field(int(const7a), 6, 0));
}

inline void Assembler::fmov(FloatRegisterImpl::Width w, Condition c, bool floatCC, CC cca, FloatRegister s2, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop2_op3) | cond_mov(c) | opf_cc(cca, floatCC) | opf_low6(w) | fs2(s2, w));
}

inline void Assembler::fmov(FloatRegisterImpl::Width w, RCondition c, Register s1, FloatRegister s2, FloatRegister d) {
  emit_int32(op(arith_op) | fd(d, w) | op3(fpop2_op3) | rs1(s1) | rcond(c) | opf_low5(4 + w) | fs2(s2, w));
}

inline void Assembler::movcc(Condition c, bool floatCC, CC cca, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(movcc_op3) | mov_cc(cca, floatCC) | cond_mov(c) | rs2(s2));
}
inline void Assembler::movcc(Condition c, bool floatCC, CC cca, int simm11a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(movcc_op3) | mov_cc(cca, floatCC) | cond_mov(c) | immed(true) | simm(simm11a, 11));
}

inline void Assembler::movr(RCondition c, Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(movr_op3) | rs1(s1) | rcond(c) | rs2(s2));
}
inline void Assembler::movr(RCondition c, Register s1, int simm10a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(movr_op3) | rs1(s1) | rcond(c) | immed(true) | simm(simm10a, 10));
}

inline void Assembler::mulx(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(mulx_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::mulx(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(mulx_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::sdivx(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sdivx_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::sdivx(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sdivx_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::udivx(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(udivx_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::udivx(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(udivx_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::umul(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(umul_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::umul(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(umul_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::smul(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(smul_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::smul(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(smul_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::umulcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(umul_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::umulcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(umul_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::smulcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(smul_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::smulcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(smul_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::nop() {
  emit_int32(op(branch_op) | op2(sethi_op2));
}

inline void Assembler::sw_count() {
  emit_int32(op(branch_op) | op2(sethi_op2) | 0x3f0);
}

inline void Assembler::popc(Register s, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(popc_op3) | rs2(s));
}
inline void Assembler::popc(int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(popc_op3) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::prefetch(Register s1, Register s2, PrefetchFcn f) {
  emit_int32(op(ldst_op) | fcn(f) | op3(prefetch_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::prefetch(Register s1, int simm13a, PrefetchFcn f) {
  emit_data(op(ldst_op) | fcn(f) | op3(prefetch_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::prefetcha(Register s1, Register s2, int ia, PrefetchFcn f) {
  emit_int32(op(ldst_op) | fcn(f) | op3(prefetch_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::prefetcha(Register s1, int simm13a, PrefetchFcn f) {
  emit_int32(op(ldst_op) | fcn(f) | op3(prefetch_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::rdy(Register d) {
  v9_dep();
  emit_int32(op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(0, 18, 14));
}
inline void Assembler::rdccr(Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(2, 18, 14));
}
inline void Assembler::rdasi(Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(3, 18, 14));
}
inline void Assembler::rdtick(Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(4, 18, 14));
}
inline void Assembler::rdpc(Register d) {
  avoid_pipeline_stall();
  cti();
  emit_int32(op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(5, 18, 14));
  induce_pc_hazard();
}
inline void Assembler::rdfprs(Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(6, 18, 14));
}

inline void Assembler::rett(Register s1, Register s2) {
  cti();
  emit_int32(op(arith_op) | op3(rett_op3) | rs1(s1) | rs2(s2));
  induce_delay_slot();
}
inline void Assembler::rett(Register s1, int simm13a, relocInfo::relocType rt) {
  cti();
  emit_data(op(arith_op) | op3(rett_op3) | rs1(s1) | immed(true) | simm(simm13a, 13), rt);
  induce_delay_slot();
}

inline void Assembler::save(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(save_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::save(Register s1, int simm13a, Register d) {
  // make sure frame is at least large enough for the register save area
  assert(-simm13a >= 16 * wordSize, "frame too small");
  emit_int32(op(arith_op) | rd(d) | op3(save_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::restore(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(restore_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::restore(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(restore_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

// pp 216

inline void Assembler::saved() {
  emit_int32(op(arith_op) | fcn(0) | op3(saved_op3));
}
inline void Assembler::restored() {
  emit_int32(op(arith_op) | fcn(1) | op3(saved_op3));
}

inline void Assembler::sethi(int imm22a, Register d, RelocationHolder const &rspec) {
  emit_data(op(branch_op) | rd(d) | op2(sethi_op2) | hi22(imm22a), rspec);
}

inline void Assembler::sll(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sll_op3) | rs1(s1) | sx(0) | rs2(s2));
}
inline void Assembler::sll(Register s1, int imm5a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sll_op3) | rs1(s1) | sx(0) | immed(true) | u_field(imm5a, 4, 0));
}
inline void Assembler::srl(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(srl_op3) | rs1(s1) | sx(0) | rs2(s2));
}
inline void Assembler::srl(Register s1, int imm5a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(srl_op3) | rs1(s1) | sx(0) | immed(true) | u_field(imm5a, 4, 0));
}
inline void Assembler::sra(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sra_op3) | rs1(s1) | sx(0) | rs2(s2));
}
inline void Assembler::sra(Register s1, int imm5a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sra_op3) | rs1(s1) | sx(0) | immed(true) | u_field(imm5a, 4, 0));
}

inline void Assembler::sllx(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sll_op3) | rs1(s1) | sx(1) | rs2(s2));
}
inline void Assembler::sllx(Register s1, int imm6a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sll_op3) | rs1(s1) | sx(1) | immed(true) | u_field(imm6a, 5, 0));
}
inline void Assembler::srlx(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(srl_op3) | rs1(s1) | sx(1) | rs2(s2));
}
inline void Assembler::srlx(Register s1, int imm6a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(srl_op3) | rs1(s1) | sx(1) | immed(true) | u_field(imm6a, 5, 0));
}
inline void Assembler::srax(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sra_op3) | rs1(s1) | sx(1) | rs2(s2));
}
inline void Assembler::srax(Register s1, int imm6a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sra_op3) | rs1(s1) | sx(1) | immed(true) | u_field(imm6a, 5, 0));
}

inline void Assembler::sir(int simm13a) {
  emit_int32(op(arith_op) | fcn(15) | op3(sir_op3) | immed(true) | simm(simm13a, 13));
}

// pp 221

inline void Assembler::stbar() {
  emit_int32(op(arith_op) | op3(membar_op3) | u_field(15, 18, 14));
}

// pp 222

inline void Assembler::stf(FloatRegisterImpl::Width w, FloatRegister d, Register s1, Register s2) {
  emit_int32(op(ldst_op) | fd(d, w) | alt_op3(stf_op3, w) | rs1(s1) | rs2(s2));
}
inline void Assembler::stf(FloatRegisterImpl::Width w, FloatRegister d, Register s1, int simm13a) {
  emit_data(op(ldst_op) | fd(d, w) | alt_op3(stf_op3, w) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::std(FloatRegister d, Register s1, Register s2) {
  assert(d->is_even(), "not even");
  stf(FloatRegisterImpl::D, d, s1, s2);
}
inline void Assembler::std(FloatRegister d, Register s1, int simm13a) {
  assert(d->is_even(), "not even");
  stf(FloatRegisterImpl::D, d, s1, simm13a);
}

inline void Assembler::stxfsr(Register s1, Register s2) {
  emit_int32(op(ldst_op) | rd(G1) | op3(stfsr_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::stxfsr(Register s1, int simm13a) {
  emit_data(op(ldst_op) | rd(G1) | op3(stfsr_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::stfa(FloatRegisterImpl::Width w, FloatRegister d, Register s1, Register s2, int ia) {
  emit_int32(op(ldst_op) | fd(d, w) | alt_op3(stf_op3 | alt_bit_op3, w) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::stfa(FloatRegisterImpl::Width w, FloatRegister d, Register s1, int simm13a) {
  emit_int32(op(ldst_op) | fd(d, w) | alt_op3(stf_op3 | alt_bit_op3, w) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

// p 226

inline void Assembler::stb(Register d, Register s1, Register s2) {
  emit_int32(op(ldst_op) | rd(d) | op3(stb_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::stb(Register d, Register s1, int simm13a) {
  emit_data(op(ldst_op) | rd(d) | op3(stb_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::sth(Register d, Register s1, Register s2) {
  emit_int32(op(ldst_op) | rd(d) | op3(sth_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::sth(Register d, Register s1, int simm13a) {
  emit_data(op(ldst_op) | rd(d) | op3(sth_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::stw(Register d, Register s1, Register s2) {
  emit_int32(op(ldst_op) | rd(d) | op3(stw_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::stw(Register d, Register s1, int simm13a) {
  emit_data(op(ldst_op) | rd(d) | op3(stw_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}


inline void Assembler::stx(Register d, Register s1, Register s2) {
  emit_int32(op(ldst_op) | rd(d) | op3(stx_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::stx(Register d, Register s1, int simm13a) {
  emit_data(op(ldst_op) | rd(d) | op3(stx_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::stba(Register d, Register s1, Register s2, int ia) {
  emit_int32(op(ldst_op) | rd(d) | op3(stb_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::stba(Register d, Register s1, int simm13a) {
  emit_int32(op(ldst_op) | rd(d) | op3(stb_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::stha(Register d, Register s1, Register s2, int ia) {
  emit_int32(op(ldst_op) | rd(d) | op3(sth_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::stha(Register d, Register s1, int simm13a) {
  emit_int32(op(ldst_op) | rd(d) | op3(sth_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::stwa(Register d, Register s1, Register s2, int ia) {
  emit_int32(op(ldst_op) | rd(d) | op3(stw_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::stwa(Register d, Register s1, int simm13a) {
  emit_int32(op(ldst_op) | rd(d) | op3(stw_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::stxa(Register d, Register s1, Register s2, int ia) {
  emit_int32(op(ldst_op) | rd(d) | op3(stx_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::stxa(Register d, Register s1, int simm13a) {
  emit_int32(op(ldst_op) | rd(d) | op3(stx_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::stda(Register d, Register s1, Register s2, int ia) {
  emit_int32(op(ldst_op) | rd(d) | op3(std_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::stda(Register d, Register s1, int simm13a) {
  emit_int32(op(ldst_op) | rd(d) | op3(std_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

// pp 230

inline void Assembler::sub(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sub_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::sub(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sub_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::subcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sub_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::subcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(sub_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::subc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(subc_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::subc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(subc_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::subccc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(subc_op3 | cc_bit_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::subccc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(subc_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

// pp 231

inline void Assembler::swap(Register s1, Register s2, Register d) {
  v9_dep();
  emit_int32(op(ldst_op) | rd(d) | op3(swap_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::swap(Register s1, int simm13a, Register d) {
  v9_dep();
  emit_data(op(ldst_op) | rd(d) | op3(swap_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

inline void Assembler::swapa(Register s1, Register s2, int ia, Register d) {
  v9_dep();
  emit_int32(op(ldst_op) | rd(d) | op3(swap_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}
inline void Assembler::swapa(Register s1, int simm13a, Register d) {
  v9_dep();
  emit_int32(op(ldst_op) | rd(d) | op3(swap_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

// pp 234, note op in book is wrong, see pp 268

inline void Assembler::taddcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(taddcc_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::taddcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(taddcc_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

// pp 235

inline void Assembler::tsubcc(Register s1, Register s2, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(tsubcc_op3) | rs1(s1) | rs2(s2));
}
inline void Assembler::tsubcc(Register s1, int simm13a, Register d) {
  emit_int32(op(arith_op) | rd(d) | op3(tsubcc_op3) | rs1(s1) | immed(true) | simm(simm13a, 13));
}

// pp 237

inline void Assembler::trap(Condition c, CC cc, Register s1, Register s2) {
  emit_int32(op(arith_op) | cond(c) | op3(trap_op3) | rs1(s1) | trapcc(cc) | rs2(s2));
}
inline void Assembler::trap(Condition c, CC cc, Register s1, int trapa) {
  emit_int32(op(arith_op) | cond(c) | op3(trap_op3) | rs1(s1) | trapcc(cc) | immed(true) | u_field(trapa, 6, 0));
}
// simple uncond. trap
inline void Assembler::trap(int trapa) {
  trap(always, icc, G0, trapa);
}

inline void Assembler::wry(Register d) {
  v9_dep();
  emit_int32(op(arith_op) | rs1(d) | op3(wrreg_op3) | u_field(0, 29, 25));
}
inline void Assembler::wrccr(Register s) {
  emit_int32(op(arith_op) | rs1(s) | op3(wrreg_op3) | u_field(2, 29, 25));
}
inline void Assembler::wrccr(Register s, int simm13a) {
  emit_int32(op(arith_op) | rs1(s) | op3(wrreg_op3) | u_field(2, 29, 25) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::wrasi(Register d) {
  emit_int32(op(arith_op) | rs1(d) | op3(wrreg_op3) | u_field(3, 29, 25));
}
// wrasi(d, imm) stores (d xor imm) to asi
inline void Assembler::wrasi(Register d, int simm13a) {
  emit_int32(op(arith_op) | rs1(d) | op3(wrreg_op3) | u_field(3, 29, 25) | immed(true) | simm(simm13a, 13));
}
inline void Assembler::wrfprs(Register d) {
  emit_int32(op(arith_op) | rs1(d) | op3(wrreg_op3) | u_field(6, 29, 25));
}

inline void Assembler::alignaddr(Register s1, Register s2, Register d) {
  vis1_only();
  emit_int32(op(arith_op) | rd(d) | op3(alignaddr_op3) | rs1(s1) | opf(alignaddr_opf) | rs2(s2));
}

inline void Assembler::faligndata(FloatRegister s1, FloatRegister s2, FloatRegister d) {
  vis1_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(faligndata_op3) | fs1(s1, FloatRegisterImpl::D) | opf(faligndata_opf) | fs2(s2, FloatRegisterImpl::D));
}

inline void Assembler::fzero(FloatRegisterImpl::Width w, FloatRegister d) {
  vis1_only();
  emit_int32(op(arith_op) | fd(d, w) | op3(fzero_op3) | opf(0x62 - w));
}

inline void Assembler::fsrc2(FloatRegisterImpl::Width w, FloatRegister s2, FloatRegister d) {
  vis1_only();
  emit_int32(op(arith_op) | fd(d, w) | op3(fsrc_op3) | opf(0x7A - w) | fs2(s2, w));
}

inline void Assembler::fnot1(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister d) {
  vis1_only();
  emit_int32(op(arith_op) | fd(d, w) | op3(fnot_op3) | fs1(s1, w) | opf(0x6C - w));
}

inline void Assembler::fpmerge(FloatRegister s1, FloatRegister s2, FloatRegister d) {
  vis1_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(0x36) | fs1(s1, FloatRegisterImpl::S) | opf(0x4b) | fs2(s2, FloatRegisterImpl::S));
}

inline void Assembler::stpartialf(Register s1, Register s2, FloatRegister d, int ia) {
  vis1_only();
  emit_int32(op(ldst_op) | fd(d, FloatRegisterImpl::D) | op3(stpartialf_op3) | rs1(s1) | imm_asi(ia) | rs2(s2));
}

// VIS2 instructions

inline void Assembler::edge8n(Register s1, Register s2, Register d) {
  vis2_only();
  emit_int32(op(arith_op) | rd(d) | op3(edge_op3) | rs1(s1) | opf(edge8n_opf) | rs2(s2));
}

inline void Assembler::bmask(Register s1, Register s2, Register d) {
  vis2_only();
  emit_int32(op(arith_op) | rd(d) | op3(bmask_op3) | rs1(s1) | opf(bmask_opf) | rs2(s2));
}
inline void Assembler::bshuffle(FloatRegister s1, FloatRegister s2, FloatRegister d) {
  vis2_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(bshuffle_op3) | fs1(s1, FloatRegisterImpl::D) | opf(bshuffle_opf) | fs2(s2, FloatRegisterImpl::D));
}

// VIS3 instructions

inline void Assembler::addxc(Register s1, Register s2, Register d) {
  vis3_only();
  emit_int32(op(arith_op) | rd(d) | op3(addx_op3) | rs1(s1) | opf(addxc_opf) | rs2(s2));
}
inline void Assembler::addxccc(Register s1, Register s2, Register d) {
  vis3_only();
  emit_int32(op(arith_op) | rd(d) | op3(addx_op3) | rs1(s1) | opf(addxccc_opf) | rs2(s2));
}

inline void Assembler::movstosw(FloatRegister s, Register d) {
  vis3_only();
  emit_int32(op(arith_op) | rd(d) | op3(mftoi_op3) | opf(mstosw_opf) | fs2(s, FloatRegisterImpl::S));
}
inline void Assembler::movstouw(FloatRegister s, Register d) {
  vis3_only();
  emit_int32(op(arith_op) | rd(d) | op3(mftoi_op3) | opf(mstouw_opf) | fs2(s, FloatRegisterImpl::S));
}
inline void Assembler::movdtox(FloatRegister s, Register d) {
  vis3_only();
  emit_int32(op(arith_op) | rd(d) | op3(mftoi_op3) | opf(mdtox_opf) | fs2(s, FloatRegisterImpl::D));
}

inline void Assembler::movwtos(Register s, FloatRegister d) {
  vis3_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::S) | op3(mftoi_op3) | opf(mwtos_opf) | rs2(s));
}
inline void Assembler::movxtod(Register s, FloatRegister d) {
  vis3_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(mftoi_op3) | opf(mxtod_opf) | rs2(s));
}

inline void Assembler::xmulx(Register s1, Register s2, Register d) {
  vis3_only();
  emit_int32(op(arith_op) | rd(d) | op3(xmulx_op3) | rs1(s1) | opf(xmulx_opf) | rs2(s2));
}
inline void Assembler::xmulxhi(Register s1, Register s2, Register d) {
  vis3_only();
  emit_int32(op(arith_op) | rd(d) | op3(xmulx_op3) | rs1(s1) | opf(xmulxhi_opf) | rs2(s2));
}
inline void Assembler::umulxhi(Register s1, Register s2, Register d) {
  vis3_only();
  emit_int32(op(arith_op) | rd(d) | op3(umulx_op3) | rs1(s1) | opf(umulxhi_opf) | rs2(s2));
}

// Crypto SHA instructions

inline void Assembler::sha1() {
  sha1_only();
  emit_int32(op(arith_op) | op3(sha_op3) | opf(sha1_opf));
}
inline void Assembler::sha256() {
  sha256_only();
  emit_int32(op(arith_op) | op3(sha_op3) | opf(sha256_opf));
}
inline void Assembler::sha512() {
  sha512_only();
  emit_int32(op(arith_op) | op3(sha_op3) | opf(sha512_opf));
}

// CRC32C instruction

inline void Assembler::crc32c(FloatRegister s1, FloatRegister s2, FloatRegister d) {
  crc32c_only();
  emit_int32(op(arith_op) | fd(d, FloatRegisterImpl::D) | op3(crc32c_op3) | fs1(s1, FloatRegisterImpl::D) | opf(crc32c_opf) | fs2(s2, FloatRegisterImpl::D));
}

// MPMUL instruction

inline void Assembler::mpmul(int uimm5) {
  mpmul_only();
  emit_int32(op(arith_op) | rd(0) | op3(mpmul_op3) | rs1(0) | opf(mpmul_opf) | uimm(uimm5, 5));
}

#endif // CPU_SPARC_VM_ASSEMBLER_SPARC_INLINE_HPP
