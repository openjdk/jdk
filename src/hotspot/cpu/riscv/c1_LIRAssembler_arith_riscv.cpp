/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.hpp"
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"

#ifndef PRODUCT
#define COMMENT(x)   do { __ block_comment(x); } while (0)
#else
#define COMMENT(x)
#endif

#define __ _masm->

void LIR_Assembler::arithmetic_idiv(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr illegal,
                                    LIR_Opr result, CodeEmitInfo* info) {
  // opcode check
  assert((code == lir_idiv) || (code == lir_irem), "opcode must be idiv or irem");
  bool is_irem = (code == lir_irem);
  // opreand check
  assert(left->is_single_cpu(), "left must be a register");
  assert(right->is_single_cpu() || right->is_constant(), "right must be a register or constant");
  assert(result->is_single_cpu(), "result must be a register");
  Register lreg = left->as_register();
  Register dreg = result->as_register();

  // power-of-2 constant check and codegen
  if (right->is_constant()) {
    int c = right->as_constant_ptr()->as_jint();
    assert(c > 0 && is_power_of_2(c), "divisor must be power-of-2 constant");
    if (is_irem) {
      if (c == 1) {
        // move 0 to dreg if divisor is 1
        __ mv(dreg, zr);
      } else {
        unsigned int shift = exact_log2(c);
        __ sraiw(t0, lreg, 0x1f);
        __ srliw(t0, t0, BitsPerInt - shift);
        __ addw(t1, lreg, t0);
        if (Assembler::is_simm12(c - 1)) {
          __ andi(t1, t1, c - 1);
        } else {
          __ zero_extend(t1, t1, shift);
        }
        __ subw(dreg, t1, t0);
      }
    } else {
      if (c == 1) {
        // move lreg to dreg if divisor is 1
        __ mv(dreg, lreg);
      } else {
        unsigned int shift = exact_log2(c);
        __ sraiw(t0, lreg, 0x1f);
        if (Assembler::is_simm12(c - 1)) {
          __ andi(t0, t0, c - 1);
        } else {
          __ zero_extend(t0, t0, shift);
        }
        __ addw(dreg, t0, lreg);
        __ sraiw(dreg, dreg, shift);
      }
    }
  } else {
    Register rreg = right->as_register();
    __ corrected_idivl(dreg, lreg, rreg, is_irem);
  }
}

void LIR_Assembler::arith_op_single_cpu_right_constant(LIR_Code code, LIR_Opr left, LIR_Opr right,
                                                       Register lreg, Register dreg) {
  // cpu register - constant
  jlong c;

  switch (right->type()) {
    case T_LONG:
      c = right->as_constant_ptr()->as_jlong(); break;
    case T_INT:     // fall through
    case T_ADDRESS:
      c = right->as_constant_ptr()->as_jint(); break;
    default:
      ShouldNotReachHere();
      c = 0;   // unreachable
  }

  assert(code == lir_add || code == lir_sub, "mismatched arithmetic op");
  if (c == 0 && dreg == lreg) {
    COMMENT("effective nop elided");
    return;
  }
  switch (left->type()) {
    case T_INT:
      switch (code) {
        case lir_add: __ addw(dreg, lreg, c); break;
        case lir_sub: __ subw(dreg, lreg, c); break;
        default:      ShouldNotReachHere();
      }
    break;
    case T_OBJECT:  // fall through
    case T_ADDRESS:
      switch (code) {
        case lir_add: __ add(dreg, lreg, c); break;
        case lir_sub: __ sub(dreg, lreg, c); break;
        default:      ShouldNotReachHere();
      }
    break;
    default:
      ShouldNotReachHere();
  }
}

void LIR_Assembler::arith_op_single_cpu(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest) {
  Register lreg = left->as_register();
  Register dreg = as_reg(dest);

  if (right->is_single_cpu()) {
    // cpu register - cpu register
    assert(left->type() == T_INT && right->type() == T_INT && dest->type() == T_INT, "should be");
    Register rreg = right->as_register();
    switch (code) {
      case lir_add: __ addw(dest->as_register(), lreg, rreg); break;
      case lir_sub: __ subw(dest->as_register(), lreg, rreg); break;
      case lir_mul: __ mulw(dest->as_register(), lreg, rreg); break;
      default:      ShouldNotReachHere();
    }
  } else if (right->is_double_cpu()) {
    Register rreg = right->as_register_lo();
    // sigle_cpu + double_cpu; can happen with obj_long
    assert(code == lir_add || code == lir_sub, "mismatched arithmetic op");
    switch (code) {
      case lir_add: __ add(dreg, lreg, rreg); break;
      case lir_sub: __ sub(dreg, lreg, rreg); break;
      default:      ShouldNotReachHere();
    }
  } else if (right->is_constant()) {
    arith_op_single_cpu_right_constant(code, left, right, lreg, dreg);
  } else {
    ShouldNotReachHere();
  }
}

void LIR_Assembler::arith_op_double_cpu(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest) {
  Register lreg_lo = left->as_register_lo();

  if (right->is_double_cpu()) {
    // cpu register - cpu register
    Register rreg_lo = right->as_register_lo();
    switch (code) {
      case lir_add: __ add(dest->as_register_lo(), lreg_lo, rreg_lo); break;
      case lir_sub: __ sub(dest->as_register_lo(), lreg_lo, rreg_lo); break;
      case lir_mul: __ mul(dest->as_register_lo(), lreg_lo, rreg_lo); break;
      case lir_div: __ corrected_idivq(dest->as_register_lo(), lreg_lo, rreg_lo, false); break;
      case lir_rem: __ corrected_idivq(dest->as_register_lo(), lreg_lo, rreg_lo, true); break;
      default:
        ShouldNotReachHere();
    }
  } else if (right->is_constant()) {
    jlong c = right->as_constant_ptr()->as_jlong();
    Register dreg = as_reg(dest);
    switch (code) {
      case lir_add: // fall through
      case lir_sub:
        if (c == 0 && dreg == lreg_lo) {
          COMMENT("effective nop elided");
          return;
        }
        code == lir_add ? __ add(dreg, lreg_lo, c) : __ sub(dreg, lreg_lo, c);
        break;
      case lir_div:
        assert(c > 0 && is_power_of_2(c), "divisor must be power-of-2 constant");
        if (c == 1) {
          // move lreg_lo to dreg if divisor is 1
          __ mv(dreg, lreg_lo);
        } else {
          unsigned int shift = exact_log2_long(c);
          // use t0 as intermediate result register
          __ srai(t0, lreg_lo, 0x3f);
          if (Assembler::is_simm12(c - 1)) {
            __ andi(t0, t0, c - 1);
          } else {
            __ zero_extend(t0, t0, shift);
          }
          __ add(dreg, t0, lreg_lo);
          __ srai(dreg, dreg, shift);
        }
        break;
      case lir_rem:
        assert(c > 0 && is_power_of_2(c), "divisor must be power-of-2 constant");
        if (c == 1) {
          // move 0 to dreg if divisor is 1
          __ mv(dreg, zr);
        } else {
          unsigned int shift = exact_log2_long(c);
          __ srai(t0, lreg_lo, 0x3f);
          __ srli(t0, t0, BitsPerLong - shift);
          __ add(t1, lreg_lo, t0);
          if (Assembler::is_simm12(c - 1)) {
            __ andi(t1, t1, c - 1);
          } else {
            __ zero_extend(t1, t1, shift);
          }
          __ sub(dreg, t1, t0);
        }
        break;
      default:
        ShouldNotReachHere();
    }
  } else {
    ShouldNotReachHere();
  }
}

void LIR_Assembler::arith_op_single_fpu(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest) {
  assert(right->is_single_fpu(), "right hand side of float arithmetics needs to be float register");
  switch (code) {
    case lir_add: __ fadd_s(dest->as_float_reg(), left->as_float_reg(), right->as_float_reg()); break;
    case lir_sub: __ fsub_s(dest->as_float_reg(), left->as_float_reg(), right->as_float_reg()); break;
    case lir_mul: __ fmul_s(dest->as_float_reg(), left->as_float_reg(), right->as_float_reg()); break;
    case lir_div: __ fdiv_s(dest->as_float_reg(), left->as_float_reg(), right->as_float_reg()); break;
    default:
      ShouldNotReachHere();
  }
}

void LIR_Assembler::arith_op_double_fpu(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest) {
  if (right->is_double_fpu()) {
    // fpu register - fpu register
    switch (code) {
      case lir_add: __ fadd_d(dest->as_double_reg(), left->as_double_reg(), right->as_double_reg()); break;
      case lir_sub: __ fsub_d(dest->as_double_reg(), left->as_double_reg(), right->as_double_reg()); break;
      case lir_mul: __ fmul_d(dest->as_double_reg(), left->as_double_reg(), right->as_double_reg()); break;
      case lir_div: __ fdiv_d(dest->as_double_reg(), left->as_double_reg(), right->as_double_reg()); break;
      default:
        ShouldNotReachHere();
    }
  } else {
    ShouldNotReachHere();
  }
}

void LIR_Assembler::arith_op(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest,
                             CodeEmitInfo* info, bool pop_fpu_stack) {
  assert(info == NULL, "should never be used, idiv/irem and ldiv/lrem not handled by this method");

  if (left->is_single_cpu()) {
    arith_op_single_cpu(code, left, right, dest);
  } else if (left->is_double_cpu()) {
    arith_op_double_cpu(code, left, right, dest);
  } else if (left->is_single_fpu()) {
    arith_op_single_fpu(code, left, right, dest);
  } else if (left->is_double_fpu()) {
    arith_op_double_fpu(code, left, right, dest);
  } else {
    ShouldNotReachHere();
  }
}

#undef __
