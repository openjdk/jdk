/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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


inline void Assembler::check_delay() {
# ifdef CHECK_DELAY
  guarantee( delay_state != at_delay_slot, "must say delayed() when filling delay slot");
  delay_state = no_delay;
# endif
}

inline void Assembler::emit_int32(int x) {
  check_delay();
  AbstractAssembler::emit_int32(x);
}

inline void Assembler::emit_data(int x, relocInfo::relocType rtype) {
  relocate(rtype);
  emit_int32(x);
}

inline void Assembler::emit_data(int x, RelocationHolder const& rspec) {
  relocate(rspec);
  emit_int32(x);
}


inline void Assembler::add(Register s1, Register s2, Register d ) { emit_int32( op(arith_op) | rd(d) | op3(add_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::add(Register s1, int simm13a, Register d ) { emit_int32( op(arith_op) | rd(d) | op3(add_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

inline void Assembler::bpr( RCondition c, bool a, Predict p, Register s1, address d, relocInfo::relocType rt ) { v9_only(); insert_nop_after_cbcond(); cti();  emit_data( op(branch_op) | annul(a) | cond(c) | op2(bpr_op2) | wdisp16(intptr_t(d), intptr_t(pc())) | predict(p) | rs1(s1), rt);  has_delay_slot(); }
inline void Assembler::bpr( RCondition c, bool a, Predict p, Register s1, Label& L) { insert_nop_after_cbcond(); bpr( c, a, p, s1, target(L)); }

inline void Assembler::fb( Condition c, bool a, address d, relocInfo::relocType rt ) { v9_dep();  insert_nop_after_cbcond(); cti();  emit_data( op(branch_op) | annul(a) | cond(c) | op2(fb_op2) | wdisp(intptr_t(d), intptr_t(pc()), 22), rt);  has_delay_slot(); }
inline void Assembler::fb( Condition c, bool a, Label& L ) { insert_nop_after_cbcond(); fb(c, a, target(L)); }

inline void Assembler::fbp( Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt ) { v9_only(); insert_nop_after_cbcond(); cti();  emit_data( op(branch_op) | annul(a) | cond(c) | op2(fbp_op2) | branchcc(cc) | predict(p) | wdisp(intptr_t(d), intptr_t(pc()), 19), rt);  has_delay_slot(); }
inline void Assembler::fbp( Condition c, bool a, CC cc, Predict p, Label& L ) { insert_nop_after_cbcond(); fbp(c, a, cc, p, target(L)); }

inline void Assembler::br( Condition c, bool a, address d, relocInfo::relocType rt ) { v9_dep(); insert_nop_after_cbcond(); cti();   emit_data( op(branch_op) | annul(a) | cond(c) | op2(br_op2) | wdisp(intptr_t(d), intptr_t(pc()), 22), rt);  has_delay_slot(); }
inline void Assembler::br( Condition c, bool a, Label& L ) { insert_nop_after_cbcond(); br(c, a, target(L)); }

inline void Assembler::bp( Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt ) { v9_only();  insert_nop_after_cbcond(); cti();  emit_data( op(branch_op) | annul(a) | cond(c) | op2(bp_op2) | branchcc(cc) | predict(p) | wdisp(intptr_t(d), intptr_t(pc()), 19), rt);  has_delay_slot(); }
inline void Assembler::bp( Condition c, bool a, CC cc, Predict p, Label& L ) { insert_nop_after_cbcond(); bp(c, a, cc, p, target(L)); }

// compare and branch
inline void Assembler::cbcond(Condition c, CC cc, Register s1, Register s2, Label& L) { cti();  no_cbcond_before();  emit_data(op(branch_op) | cond_cbcond(c) | op2(bpr_op2) | branchcc(cc) | wdisp10(intptr_t(target(L)), intptr_t(pc())) | rs1(s1) | rs2(s2)); }
inline void Assembler::cbcond(Condition c, CC cc, Register s1, int simm5, Label& L)   { cti();  no_cbcond_before();  emit_data(op(branch_op) | cond_cbcond(c) | op2(bpr_op2) | branchcc(cc) | wdisp10(intptr_t(target(L)), intptr_t(pc())) | rs1(s1) | immed(true) | simm(simm5, 5)); }

inline void Assembler::call( address d,  relocInfo::relocType rt ) { insert_nop_after_cbcond(); cti();  emit_data( op(call_op) | wdisp(intptr_t(d), intptr_t(pc()), 30), rt);  has_delay_slot(); assert(rt != relocInfo::virtual_call_type, "must use virtual_call_Relocation::spec"); }
inline void Assembler::call( Label& L,   relocInfo::relocType rt ) { insert_nop_after_cbcond(); call( target(L), rt); }

inline void Assembler::call( address d,  RelocationHolder const& rspec ) { insert_nop_after_cbcond(); cti();  emit_data( op(call_op) | wdisp(intptr_t(d), intptr_t(pc()), 30), rspec);  has_delay_slot(); assert(rspec.type() != relocInfo::virtual_call_type, "must use virtual_call_Relocation::spec"); }

inline void Assembler::flush( Register s1, Register s2) { emit_int32( op(arith_op) | op3(flush_op3) | rs1(s1) | rs2(s2)); }
inline void Assembler::flush( Register s1, int simm13a) { emit_data( op(arith_op) | op3(flush_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }

inline void Assembler::jmpl( Register s1, Register s2, Register d ) { insert_nop_after_cbcond(); cti();  emit_int32( op(arith_op) | rd(d) | op3(jmpl_op3) | rs1(s1) | rs2(s2));  has_delay_slot(); }
inline void Assembler::jmpl( Register s1, int simm13a, Register d, RelocationHolder const& rspec ) { insert_nop_after_cbcond(); cti();  emit_data( op(arith_op) | rd(d) | op3(jmpl_op3) | rs1(s1) | immed(true) | simm(simm13a, 13), rspec);  has_delay_slot(); }

inline void Assembler::ldf(FloatRegisterImpl::Width w, Register s1, Register s2, FloatRegister d) { emit_int32( op(ldst_op) | fd(d, w) | alt_op3(ldf_op3, w) | rs1(s1) | rs2(s2) ); }
inline void Assembler::ldf(FloatRegisterImpl::Width w, Register s1, int simm13a, FloatRegister d, RelocationHolder const& rspec) { emit_data( op(ldst_op) | fd(d, w) | alt_op3(ldf_op3, w) | rs1(s1) | immed(true) | simm(simm13a, 13), rspec); }

inline void Assembler::ldxfsr( Register s1, Register s2) { v9_only();  emit_int32( op(ldst_op) | rd(G1)    | op3(ldfsr_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::ldxfsr( Register s1, int simm13a) { v9_only();  emit_data( op(ldst_op) | rd(G1)    | op3(ldfsr_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }

inline void Assembler::ldsb(  Register s1, Register s2, Register d) { emit_int32( op(ldst_op) | rd(d) | op3(ldsb_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::ldsb(  Register s1, int simm13a, Register d) { emit_data( op(ldst_op) | rd(d) | op3(ldsb_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }

inline void Assembler::ldsh(  Register s1, Register s2, Register d) { emit_int32( op(ldst_op) | rd(d) | op3(ldsh_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::ldsh(  Register s1, int simm13a, Register d) { emit_data( op(ldst_op) | rd(d) | op3(ldsh_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }
inline void Assembler::ldsw(  Register s1, Register s2, Register d) { emit_int32( op(ldst_op) | rd(d) | op3(ldsw_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::ldsw(  Register s1, int simm13a, Register d) { emit_data( op(ldst_op) | rd(d) | op3(ldsw_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }
inline void Assembler::ldub(  Register s1, Register s2, Register d) { emit_int32( op(ldst_op) | rd(d) | op3(ldub_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::ldub(  Register s1, int simm13a, Register d) { emit_data( op(ldst_op) | rd(d) | op3(ldub_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }
inline void Assembler::lduh(  Register s1, Register s2, Register d) { emit_int32( op(ldst_op) | rd(d) | op3(lduh_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::lduh(  Register s1, int simm13a, Register d) { emit_data( op(ldst_op) | rd(d) | op3(lduh_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }
inline void Assembler::lduw(  Register s1, Register s2, Register d) { emit_int32( op(ldst_op) | rd(d) | op3(lduw_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::lduw(  Register s1, int simm13a, Register d) { emit_data( op(ldst_op) | rd(d) | op3(lduw_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }

inline void Assembler::ldx(   Register s1, Register s2, Register d) { v9_only();  emit_int32( op(ldst_op) | rd(d) | op3(ldx_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::ldx(   Register s1, int simm13a, Register d) { v9_only();  emit_data( op(ldst_op) | rd(d) | op3(ldx_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }
inline void Assembler::ldd(   Register s1, Register s2, Register d) { v9_dep(); assert(d->is_even(), "not even"); emit_int32( op(ldst_op) | rd(d) | op3(ldd_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::ldd(   Register s1, int simm13a, Register d) { v9_dep(); assert(d->is_even(), "not even"); emit_data( op(ldst_op) | rd(d) | op3(ldd_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }

inline void Assembler::rett( Register s1, Register s2                         ) { cti();  emit_int32( op(arith_op) | op3(rett_op3) | rs1(s1) | rs2(s2));  has_delay_slot(); }
inline void Assembler::rett( Register s1, int simm13a, relocInfo::relocType rt) { cti();  emit_data( op(arith_op) | op3(rett_op3) | rs1(s1) | immed(true) | simm(simm13a, 13), rt);  has_delay_slot(); }

inline void Assembler::sethi( int imm22a, Register d, RelocationHolder const& rspec ) { emit_data( op(branch_op) | rd(d) | op2(sethi_op2) | hi22(imm22a), rspec); }

  // pp 222

inline void Assembler::stf(    FloatRegisterImpl::Width w, FloatRegister d, Register s1, Register s2) { emit_int32( op(ldst_op) | fd(d, w) | alt_op3(stf_op3, w) | rs1(s1) | rs2(s2) ); }
inline void Assembler::stf(    FloatRegisterImpl::Width w, FloatRegister d, Register s1, int simm13a) { emit_data( op(ldst_op) | fd(d, w) | alt_op3(stf_op3, w) | rs1(s1) | immed(true) | simm(simm13a, 13)); }

inline void Assembler::stxfsr( Register s1, Register s2) { v9_only();  emit_int32( op(ldst_op) | rd(G1)    | op3(stfsr_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::stxfsr( Register s1, int simm13a) { v9_only();  emit_data( op(ldst_op) | rd(G1)    | op3(stfsr_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }

  // p 226

inline void Assembler::stb(  Register d, Register s1, Register s2) { emit_int32( op(ldst_op) | rd(d) | op3(stb_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::stb(  Register d, Register s1, int simm13a) { emit_data( op(ldst_op) | rd(d) | op3(stb_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }
inline void Assembler::sth(  Register d, Register s1, Register s2) { emit_int32( op(ldst_op) | rd(d) | op3(sth_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::sth(  Register d, Register s1, int simm13a) { emit_data( op(ldst_op) | rd(d) | op3(sth_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }
inline void Assembler::stw(  Register d, Register s1, Register s2) { emit_int32( op(ldst_op) | rd(d) | op3(stw_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::stw(  Register d, Register s1, int simm13a) { emit_data( op(ldst_op) | rd(d) | op3(stw_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }


inline void Assembler::stx(  Register d, Register s1, Register s2) { v9_only();  emit_int32( op(ldst_op) | rd(d) | op3(stx_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::stx(  Register d, Register s1, int simm13a) { v9_only();  emit_data( op(ldst_op) | rd(d) | op3(stx_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }
inline void Assembler::std(  Register d, Register s1, Register s2) { v9_dep(); assert(d->is_even(), "not even"); emit_int32( op(ldst_op) | rd(d) | op3(std_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::std(  Register d, Register s1, int simm13a) { v9_dep(); assert(d->is_even(), "not even"); emit_data( op(ldst_op) | rd(d) | op3(std_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }

// pp 231

inline void Assembler::swap(    Register s1, Register s2, Register d) { v9_dep();  emit_int32( op(ldst_op) | rd(d) | op3(swap_op3) | rs1(s1) | rs2(s2) ); }
inline void Assembler::swap(    Register s1, int simm13a, Register d) { v9_dep();  emit_data( op(ldst_op) | rd(d) | op3(swap_op3) | rs1(s1) | immed(true) | simm(simm13a, 13)); }

#endif // CPU_SPARC_VM_ASSEMBLER_SPARC_INLINE_HPP
