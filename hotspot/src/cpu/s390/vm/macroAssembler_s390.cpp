/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 SAP SE. All rights reserved.
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
#include "asm/codeBuffer.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "compiler/disassembler.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "gc/shared/cardTableModRefBS.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/klass.inline.hpp"
#include "opto/compile.hpp"
#include "opto/intrinsicnode.hpp"
#include "opto/matcher.hpp"
#include "prims/methodHandles.hpp"
#include "registerSaver_s390.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/icache.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/events.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#include "gc/g1/heapRegion.hpp"
#endif

#include <ucontext.h>

#define BLOCK_COMMENT(str) block_comment(str)
#define BIND(label)        bind(label); BLOCK_COMMENT(#label ":")

// Move 32-bit register if destination and source are different.
void MacroAssembler::lr_if_needed(Register rd, Register rs) {
  if (rs != rd) { z_lr(rd, rs); }
}

// Move register if destination and source are different.
void MacroAssembler::lgr_if_needed(Register rd, Register rs) {
  if (rs != rd) { z_lgr(rd, rs); }
}

// Zero-extend 32-bit register into 64-bit register if destination and source are different.
void MacroAssembler::llgfr_if_needed(Register rd, Register rs) {
  if (rs != rd) { z_llgfr(rd, rs); }
}

// Move float register if destination and source are different.
void MacroAssembler::ldr_if_needed(FloatRegister rd, FloatRegister rs) {
  if (rs != rd) { z_ldr(rd, rs); }
}

// Move integer register if destination and source are different.
// It is assumed that shorter-than-int types are already
// appropriately sign-extended.
void MacroAssembler::move_reg_if_needed(Register dst, BasicType dst_type, Register src,
                                        BasicType src_type) {
  assert((dst_type != T_FLOAT) && (dst_type != T_DOUBLE), "use move_freg for float types");
  assert((src_type != T_FLOAT) && (src_type != T_DOUBLE), "use move_freg for float types");

  if (dst_type == src_type) {
    lgr_if_needed(dst, src); // Just move all 64 bits.
    return;
  }

  switch (dst_type) {
    // Do not support these types for now.
    //  case T_BOOLEAN:
    case T_BYTE:  // signed byte
      switch (src_type) {
        case T_INT:
          z_lgbr(dst, src);
          break;
        default:
          ShouldNotReachHere();
      }
      return;

    case T_CHAR:
    case T_SHORT:
      switch (src_type) {
        case T_INT:
          if (dst_type == T_CHAR) {
            z_llghr(dst, src);
          } else {
            z_lghr(dst, src);
          }
          break;
        default:
          ShouldNotReachHere();
      }
      return;

    case T_INT:
      switch (src_type) {
        case T_BOOLEAN:
        case T_BYTE:
        case T_CHAR:
        case T_SHORT:
        case T_INT:
        case T_LONG:
        case T_OBJECT:
        case T_ARRAY:
        case T_VOID:
        case T_ADDRESS:
          lr_if_needed(dst, src);
          // llgfr_if_needed(dst, src);  // zero-extend (in case we need to find a bug).
          return;

        default:
          assert(false, "non-integer src type");
          return;
      }
    case T_LONG:
      switch (src_type) {
        case T_BOOLEAN:
        case T_BYTE:
        case T_CHAR:
        case T_SHORT:
        case T_INT:
          z_lgfr(dst, src); // sign extension
          return;

        case T_LONG:
        case T_OBJECT:
        case T_ARRAY:
        case T_VOID:
        case T_ADDRESS:
          lgr_if_needed(dst, src);
          return;

        default:
          assert(false, "non-integer src type");
          return;
      }
      return;
    case T_OBJECT:
    case T_ARRAY:
    case T_VOID:
    case T_ADDRESS:
      switch (src_type) {
        // These types don't make sense to be converted to pointers:
        //      case T_BOOLEAN:
        //      case T_BYTE:
        //      case T_CHAR:
        //      case T_SHORT:

        case T_INT:
          z_llgfr(dst, src); // zero extension
          return;

        case T_LONG:
        case T_OBJECT:
        case T_ARRAY:
        case T_VOID:
        case T_ADDRESS:
          lgr_if_needed(dst, src);
          return;

        default:
          assert(false, "non-integer src type");
          return;
      }
      return;
    default:
      assert(false, "non-integer dst type");
      return;
  }
}

// Move float register if destination and source are different.
void MacroAssembler::move_freg_if_needed(FloatRegister dst, BasicType dst_type,
                                         FloatRegister src, BasicType src_type) {
  assert((dst_type == T_FLOAT) || (dst_type == T_DOUBLE), "use move_reg for int types");
  assert((src_type == T_FLOAT) || (src_type == T_DOUBLE), "use move_reg for int types");
  if (dst_type == src_type) {
    ldr_if_needed(dst, src); // Just move all 64 bits.
  } else {
    switch (dst_type) {
      case T_FLOAT:
        assert(src_type == T_DOUBLE, "invalid float type combination");
        z_ledbr(dst, src);
        return;
      case T_DOUBLE:
        assert(src_type == T_FLOAT, "invalid float type combination");
        z_ldebr(dst, src);
        return;
      default:
        assert(false, "non-float dst type");
        return;
    }
  }
}

// Optimized emitter for reg to mem operations.
// Uses modern instructions if running on modern hardware, classic instructions
// otherwise. Prefers (usually shorter) classic instructions if applicable.
// Data register (reg) cannot be used as work register.
//
// Don't rely on register locking, instead pass a scratch register (Z_R0 by default).
// CAUTION! Passing registers >= Z_R2 may produce bad results on old CPUs!
void MacroAssembler::freg2mem_opt(FloatRegister reg,
                                  int64_t       disp,
                                  Register      index,
                                  Register      base,
                                  void (MacroAssembler::*modern) (FloatRegister, int64_t, Register, Register),
                                  void (MacroAssembler::*classic)(FloatRegister, int64_t, Register, Register),
                                  Register      scratch) {
  index = (index == noreg) ? Z_R0 : index;
  if (Displacement::is_shortDisp(disp)) {
    (this->*classic)(reg, disp, index, base);
  } else {
    if (Displacement::is_validDisp(disp)) {
      (this->*modern)(reg, disp, index, base);
    } else {
      if (scratch != Z_R0 && scratch != Z_R1) {
        (this->*modern)(reg, disp, index, base);      // Will fail with disp out of range.
      } else {
        if (scratch != Z_R0) {   // scratch == Z_R1
          if ((scratch == index) || (index == base)) {
            (this->*modern)(reg, disp, index, base);  // Will fail with disp out of range.
          } else {
            add2reg(scratch, disp, base);
            (this->*classic)(reg, 0, index, scratch);
            if (base == scratch) {
              add2reg(base, -disp);  // Restore base.
            }
          }
        } else {   // scratch == Z_R0
          z_lgr(scratch, base);
          add2reg(base, disp);
          (this->*classic)(reg, 0, index, base);
          z_lgr(base, scratch);      // Restore base.
        }
      }
    }
  }
}

void MacroAssembler::freg2mem_opt(FloatRegister reg, const Address &a, bool is_double) {
  if (is_double) {
    freg2mem_opt(reg, a.disp20(), a.indexOrR0(), a.baseOrR0(), MODERN_FFUN(z_stdy), CLASSIC_FFUN(z_std));
  } else {
    freg2mem_opt(reg, a.disp20(), a.indexOrR0(), a.baseOrR0(), MODERN_FFUN(z_stey), CLASSIC_FFUN(z_ste));
  }
}

// Optimized emitter for mem to reg operations.
// Uses modern instructions if running on modern hardware, classic instructions
// otherwise. Prefers (usually shorter) classic instructions if applicable.
// data register (reg) cannot be used as work register.
//
// Don't rely on register locking, instead pass a scratch register (Z_R0 by default).
// CAUTION! Passing registers >= Z_R2 may produce bad results on old CPUs!
void MacroAssembler::mem2freg_opt(FloatRegister reg,
                                  int64_t       disp,
                                  Register      index,
                                  Register      base,
                                  void (MacroAssembler::*modern) (FloatRegister, int64_t, Register, Register),
                                  void (MacroAssembler::*classic)(FloatRegister, int64_t, Register, Register),
                                  Register      scratch) {
  index = (index == noreg) ? Z_R0 : index;
  if (Displacement::is_shortDisp(disp)) {
    (this->*classic)(reg, disp, index, base);
  } else {
    if (Displacement::is_validDisp(disp)) {
      (this->*modern)(reg, disp, index, base);
    } else {
      if (scratch != Z_R0 && scratch != Z_R1) {
        (this->*modern)(reg, disp, index, base);      // Will fail with disp out of range.
      } else {
        if (scratch != Z_R0) {   // scratch == Z_R1
          if ((scratch == index) || (index == base)) {
            (this->*modern)(reg, disp, index, base);  // Will fail with disp out of range.
          } else {
            add2reg(scratch, disp, base);
            (this->*classic)(reg, 0, index, scratch);
            if (base == scratch) {
              add2reg(base, -disp);  // Restore base.
            }
          }
        } else {   // scratch == Z_R0
          z_lgr(scratch, base);
          add2reg(base, disp);
          (this->*classic)(reg, 0, index, base);
          z_lgr(base, scratch);      // Restore base.
        }
      }
    }
  }
}

void MacroAssembler::mem2freg_opt(FloatRegister reg, const Address &a, bool is_double) {
  if (is_double) {
    mem2freg_opt(reg, a.disp20(), a.indexOrR0(), a.baseOrR0(), MODERN_FFUN(z_ldy), CLASSIC_FFUN(z_ld));
  } else {
    mem2freg_opt(reg, a.disp20(), a.indexOrR0(), a.baseOrR0(), MODERN_FFUN(z_ley), CLASSIC_FFUN(z_le));
  }
}

// Optimized emitter for reg to mem operations.
// Uses modern instructions if running on modern hardware, classic instructions
// otherwise. Prefers (usually shorter) classic instructions if applicable.
// Data register (reg) cannot be used as work register.
//
// Don't rely on register locking, instead pass a scratch register
// (Z_R0 by default)
// CAUTION! passing registers >= Z_R2 may produce bad results on old CPUs!
void MacroAssembler::reg2mem_opt(Register reg,
                                 int64_t  disp,
                                 Register index,
                                 Register base,
                                 void (MacroAssembler::*modern) (Register, int64_t, Register, Register),
                                 void (MacroAssembler::*classic)(Register, int64_t, Register, Register),
                                 Register scratch) {
  index = (index == noreg) ? Z_R0 : index;
  if (Displacement::is_shortDisp(disp)) {
    (this->*classic)(reg, disp, index, base);
  } else {
    if (Displacement::is_validDisp(disp)) {
      (this->*modern)(reg, disp, index, base);
    } else {
      if (scratch != Z_R0 && scratch != Z_R1) {
        (this->*modern)(reg, disp, index, base);      // Will fail with disp out of range.
      } else {
        if (scratch != Z_R0) {   // scratch == Z_R1
          if ((scratch == index) || (index == base)) {
            (this->*modern)(reg, disp, index, base);  // Will fail with disp out of range.
          } else {
            add2reg(scratch, disp, base);
            (this->*classic)(reg, 0, index, scratch);
            if (base == scratch) {
              add2reg(base, -disp);  // Restore base.
            }
          }
        } else {   // scratch == Z_R0
          if ((scratch == reg) || (scratch == base) || (reg == base)) {
            (this->*modern)(reg, disp, index, base);  // Will fail with disp out of range.
          } else {
            z_lgr(scratch, base);
            add2reg(base, disp);
            (this->*classic)(reg, 0, index, base);
            z_lgr(base, scratch);    // Restore base.
          }
        }
      }
    }
  }
}

int MacroAssembler::reg2mem_opt(Register reg, const Address &a, bool is_double) {
  int store_offset = offset();
  if (is_double) {
    reg2mem_opt(reg, a.disp20(), a.indexOrR0(), a.baseOrR0(), MODERN_IFUN(z_stg), CLASSIC_IFUN(z_stg));
  } else {
    reg2mem_opt(reg, a.disp20(), a.indexOrR0(), a.baseOrR0(), MODERN_IFUN(z_sty), CLASSIC_IFUN(z_st));
  }
  return store_offset;
}

// Optimized emitter for mem to reg operations.
// Uses modern instructions if running on modern hardware, classic instructions
// otherwise. Prefers (usually shorter) classic instructions if applicable.
// Data register (reg) will be used as work register where possible.
void MacroAssembler::mem2reg_opt(Register reg,
                                 int64_t  disp,
                                 Register index,
                                 Register base,
                                 void (MacroAssembler::*modern) (Register, int64_t, Register, Register),
                                 void (MacroAssembler::*classic)(Register, int64_t, Register, Register)) {
  index = (index == noreg) ? Z_R0 : index;
  if (Displacement::is_shortDisp(disp)) {
    (this->*classic)(reg, disp, index, base);
  } else {
    if (Displacement::is_validDisp(disp)) {
      (this->*modern)(reg, disp, index, base);
    } else {
      if ((reg == index) && (reg == base)) {
        z_sllg(reg, reg, 1);
        add2reg(reg, disp);
        (this->*classic)(reg, 0, noreg, reg);
      } else if ((reg == index) && (reg != Z_R0)) {
        add2reg(reg, disp);
        (this->*classic)(reg, 0, reg, base);
      } else if (reg == base) {
        add2reg(reg, disp);
        (this->*classic)(reg, 0, index, reg);
      } else if (reg != Z_R0) {
        add2reg(reg, disp, base);
        (this->*classic)(reg, 0, index, reg);
      } else { // reg == Z_R0 && reg != base here
        add2reg(base, disp);
        (this->*classic)(reg, 0, index, base);
        add2reg(base, -disp);
      }
    }
  }
}

void MacroAssembler::mem2reg_opt(Register reg, const Address &a, bool is_double) {
  if (is_double) {
    z_lg(reg, a);
  } else {
    mem2reg_opt(reg, a.disp20(), a.indexOrR0(), a.baseOrR0(), MODERN_IFUN(z_ly), CLASSIC_IFUN(z_l));
  }
}

void MacroAssembler::mem2reg_signed_opt(Register reg, const Address &a) {
  mem2reg_opt(reg, a.disp20(), a.indexOrR0(), a.baseOrR0(), MODERN_IFUN(z_lgf), CLASSIC_IFUN(z_lgf));
}

void MacroAssembler::and_imm(Register r, long mask,
                             Register tmp /* = Z_R0 */,
                             bool wide    /* = false */) {
  assert(wide || Immediate::is_simm32(mask), "mask value too large");

  if (!wide) {
    z_nilf(r, mask);
    return;
  }

  assert(r != tmp, " need a different temporary register !");
  load_const_optimized(tmp, mask);
  z_ngr(r, tmp);
}

// Calculate the 1's complement.
// Note: The condition code is neither preserved nor correctly set by this code!!!
// Note: (wide == false) does not protect the high order half of the target register
//       from alteration. It only serves as optimization hint for 32-bit results.
void MacroAssembler::not_(Register r1, Register r2, bool wide) {

  if ((r2 == noreg) || (r2 == r1)) { // Calc 1's complement in place.
    z_xilf(r1, -1);
    if (wide) {
      z_xihf(r1, -1);
    }
  } else { // Distinct src and dst registers.
    if (VM_Version::has_DistinctOpnds()) {
      load_const_optimized(r1, -1);
      z_xgrk(r1, r2, r1);
    } else {
      if (wide) {
        z_lgr(r1, r2);
        z_xilf(r1, -1);
        z_xihf(r1, -1);
      } else {
        z_lr(r1, r2);
        z_xilf(r1, -1);
      }
    }
  }
}

unsigned long MacroAssembler::create_mask(int lBitPos, int rBitPos) {
  assert(lBitPos >=  0,      "zero is  leftmost bit position");
  assert(rBitPos <= 63,      "63   is rightmost bit position");
  assert(lBitPos <= rBitPos, "inverted selection interval");
  return (lBitPos == 0 ? (unsigned long)(-1L) : ((1UL<<(63-lBitPos+1))-1)) & (~((1UL<<(63-rBitPos))-1));
}

// Helper function for the "Rotate_then_<logicalOP>" emitters.
// Rotate src, then mask register contents such that only bits in range survive.
// For oneBits == false, all bits not in range are set to 0. Useful for deleting all bits outside range.
// For oneBits == true,  all bits not in range are set to 1. Useful for preserving all bits outside range.
// The caller must ensure that the selected range only contains bits with defined value.
void MacroAssembler::rotate_then_mask(Register dst, Register src, int lBitPos, int rBitPos,
                                      int nRotate, bool src32bit, bool dst32bit, bool oneBits) {
  assert(!(dst32bit && lBitPos < 32), "selection interval out of range for int destination");
  bool sll4rll = (nRotate >= 0) && (nRotate <= (63-rBitPos)); // Substitute SLL(G) for RLL(G).
  bool srl4rll = (nRotate <  0) && (-nRotate <= lBitPos);     // Substitute SRL(G) for RLL(G).
  //  Pre-determine which parts of dst will be zero after shift/rotate.
  bool llZero  =  sll4rll && (nRotate >= 16);
  bool lhZero  = (sll4rll && (nRotate >= 32)) || (srl4rll && (nRotate <= -48));
  bool lfZero  = llZero && lhZero;
  bool hlZero  = (sll4rll && (nRotate >= 48)) || (srl4rll && (nRotate <= -32));
  bool hhZero  =                                 (srl4rll && (nRotate <= -16));
  bool hfZero  = hlZero && hhZero;

  // rotate then mask src operand.
  // if oneBits == true,  all bits outside selected range are 1s.
  // if oneBits == false, all bits outside selected range are 0s.
  if (src32bit) {   // There might be garbage in the upper 32 bits which will get masked away.
    if (dst32bit) {
      z_rll(dst, src, nRotate);   // Copy and rotate, upper half of reg remains undisturbed.
    } else {
      if      (sll4rll) { z_sllg(dst, src,  nRotate); }
      else if (srl4rll) { z_srlg(dst, src, -nRotate); }
      else              { z_rllg(dst, src,  nRotate); }
    }
  } else {
    if      (sll4rll) { z_sllg(dst, src,  nRotate); }
    else if (srl4rll) { z_srlg(dst, src, -nRotate); }
    else              { z_rllg(dst, src,  nRotate); }
  }

  unsigned long  range_mask    = create_mask(lBitPos, rBitPos);
  unsigned int   range_mask_h  = (unsigned int)(range_mask >> 32);
  unsigned int   range_mask_l  = (unsigned int)range_mask;
  unsigned short range_mask_hh = (unsigned short)(range_mask >> 48);
  unsigned short range_mask_hl = (unsigned short)(range_mask >> 32);
  unsigned short range_mask_lh = (unsigned short)(range_mask >> 16);
  unsigned short range_mask_ll = (unsigned short)range_mask;
  // Works for z9 and newer H/W.
  if (oneBits) {
    if ((~range_mask_l) != 0)                { z_oilf(dst, ~range_mask_l); } // All bits outside range become 1s.
    if (((~range_mask_h) != 0) && !dst32bit) { z_oihf(dst, ~range_mask_h); }
  } else {
    // All bits outside range become 0s
    if (((~range_mask_l) != 0) &&              !lfZero) {
      z_nilf(dst, range_mask_l);
    }
    if (((~range_mask_h) != 0) && !dst32bit && !hfZero) {
      z_nihf(dst, range_mask_h);
    }
  }
}

// Rotate src, then insert selected range from rotated src into dst.
// Clear dst before, if requested.
void MacroAssembler::rotate_then_insert(Register dst, Register src, int lBitPos, int rBitPos,
                                        int nRotate, bool clear_dst) {
  // This version does not depend on src being zero-extended int2long.
  nRotate &= 0x003f;                                       // For risbg, pretend it's an unsigned value.
  z_risbg(dst, src, lBitPos, rBitPos, nRotate, clear_dst); // Rotate, then insert selected, clear the rest.
}

// Rotate src, then and selected range from rotated src into dst.
// Set condition code only if so requested. Otherwise it is unpredictable.
// See performance note in macroAssembler_s390.hpp for important information.
void MacroAssembler::rotate_then_and(Register dst, Register src, int lBitPos, int rBitPos,
                                     int nRotate, bool test_only) {
  guarantee(!test_only, "Emitter not fit for test_only instruction variant.");
  // This version does not depend on src being zero-extended int2long.
  nRotate &= 0x003f;                                       // For risbg, pretend it's an unsigned value.
  z_rxsbg(dst, src, lBitPos, rBitPos, nRotate, test_only); // Rotate, then xor selected.
}

// Rotate src, then or selected range from rotated src into dst.
// Set condition code only if so requested. Otherwise it is unpredictable.
// See performance note in macroAssembler_s390.hpp for important information.
void MacroAssembler::rotate_then_or(Register dst, Register src,  int  lBitPos,  int  rBitPos,
                                    int nRotate, bool test_only) {
  guarantee(!test_only, "Emitter not fit for test_only instruction variant.");
  // This version does not depend on src being zero-extended int2long.
  nRotate &= 0x003f;                                       // For risbg, pretend it's an unsigned value.
  z_rosbg(dst, src, lBitPos, rBitPos, nRotate, test_only); // Rotate, then xor selected.
}

// Rotate src, then xor selected range from rotated src into dst.
// Set condition code only if so requested. Otherwise it is unpredictable.
// See performance note in macroAssembler_s390.hpp for important information.
void MacroAssembler::rotate_then_xor(Register dst, Register src,  int  lBitPos,  int  rBitPos,
                                     int nRotate, bool test_only) {
  guarantee(!test_only, "Emitter not fit for test_only instruction variant.");
    // This version does not depend on src being zero-extended int2long.
  nRotate &= 0x003f;                                       // For risbg, pretend it's an unsigned value.
  z_rxsbg(dst, src, lBitPos, rBitPos, nRotate, test_only); // Rotate, then xor selected.
}

void MacroAssembler::add64(Register r1, RegisterOrConstant inc) {
  if (inc.is_register()) {
    z_agr(r1, inc.as_register());
  } else { // constant
    intptr_t imm = inc.as_constant();
    add2reg(r1, imm);
  }
}
// Helper function to multiply the 64bit contents of a register by a 16bit constant.
// The optimization tries to avoid the mghi instruction, since it uses the FPU for
// calculation and is thus rather slow.
//
// There is no handling for special cases, e.g. cval==0 or cval==1.
//
// Returns len of generated code block.
unsigned int MacroAssembler::mul_reg64_const16(Register rval, Register work, int cval) {
  int block_start = offset();

  bool sign_flip = cval < 0;
  cval = sign_flip ? -cval : cval;

  BLOCK_COMMENT("Reg64*Con16 {");

  int bit1 = cval & -cval;
  if (bit1 == cval) {
    z_sllg(rval, rval, exact_log2(bit1));
    if (sign_flip) { z_lcgr(rval, rval); }
  } else {
    int bit2 = (cval-bit1) & -(cval-bit1);
    if ((bit1+bit2) == cval) {
      z_sllg(work, rval, exact_log2(bit1));
      z_sllg(rval, rval, exact_log2(bit2));
      z_agr(rval, work);
      if (sign_flip) { z_lcgr(rval, rval); }
    } else {
      if (sign_flip) { z_mghi(rval, -cval); }
      else           { z_mghi(rval,  cval); }
    }
  }
  BLOCK_COMMENT("} Reg64*Con16");

  int block_end = offset();
  return block_end - block_start;
}

// Generic operation r1 := r2 + imm.
//
// Should produce the best code for each supported CPU version.
// r2 == noreg yields r1 := r1 + imm
// imm == 0 emits either no instruction or r1 := r2 !
// NOTES: 1) Don't use this function where fixed sized
//           instruction sequences are required!!!
//        2) Don't use this function if condition code
//           setting is required!
//        3) Despite being declared as int64_t, the parameter imm
//           must be a simm_32 value (= signed 32-bit integer).
void MacroAssembler::add2reg(Register r1, int64_t imm, Register r2) {
  assert(Immediate::is_simm32(imm), "probably an implicit conversion went wrong");

  if (r2 == noreg) { r2 = r1; }

  // Handle special case imm == 0.
  if (imm == 0) {
    lgr_if_needed(r1, r2);
    // Nothing else to do.
    return;
  }

  if (!PreferLAoverADD || (r2 == Z_R0)) {
    bool distinctOpnds = VM_Version::has_DistinctOpnds();

    // Can we encode imm in 16 bits signed?
    if (Immediate::is_simm16(imm)) {
      if (r1 == r2) {
        z_aghi(r1, imm);
        return;
      }
      if (distinctOpnds) {
        z_aghik(r1, r2, imm);
        return;
      }
      z_lgr(r1, r2);
      z_aghi(r1, imm);
      return;
    }
  } else {
    // Can we encode imm in 12 bits unsigned?
    if (Displacement::is_shortDisp(imm)) {
      z_la(r1, imm, r2);
      return;
    }
    // Can we encode imm in 20 bits signed?
    if (Displacement::is_validDisp(imm)) {
      // Always use LAY instruction, so we don't need the tmp register.
      z_lay(r1, imm, r2);
      return;
    }

  }

  // Can handle it (all possible values) with long immediates.
  lgr_if_needed(r1, r2);
  z_agfi(r1, imm);
}

// Generic operation r := b + x + d
//
// Addition of several operands with address generation semantics - sort of:
//  - no restriction on the registers. Any register will do for any operand.
//  - x == noreg: operand will be disregarded.
//  - b == noreg: will use (contents of) result reg as operand (r := r + d).
//  - x == Z_R0:  just disregard
//  - b == Z_R0:  use as operand. This is not address generation semantics!!!
//
// The same restrictions as on add2reg() are valid!!!
void MacroAssembler::add2reg_with_index(Register r, int64_t d, Register x, Register b) {
  assert(Immediate::is_simm32(d), "probably an implicit conversion went wrong");

  if (x == noreg) { x = Z_R0; }
  if (b == noreg) { b = r; }

  // Handle special case x == R0.
  if (x == Z_R0) {
    // Can simply add the immediate value to the base register.
    add2reg(r, d, b);
    return;
  }

  if (!PreferLAoverADD || (b == Z_R0)) {
    bool distinctOpnds = VM_Version::has_DistinctOpnds();
    // Handle special case d == 0.
    if (d == 0) {
      if (b == x)        { z_sllg(r, b, 1); return; }
      if (r == x)        { z_agr(r, b);     return; }
      if (r == b)        { z_agr(r, x);     return; }
      if (distinctOpnds) { z_agrk(r, x, b); return; }
      z_lgr(r, b);
      z_agr(r, x);
    } else {
      if (x == b)             { z_sllg(r, x, 1); }
      else if (r == x)        { z_agr(r, b); }
      else if (r == b)        { z_agr(r, x); }
      else if (distinctOpnds) { z_agrk(r, x, b); }
      else {
        z_lgr(r, b);
        z_agr(r, x);
      }
      add2reg(r, d);
    }
  } else {
    // Can we encode imm in 12 bits unsigned?
    if (Displacement::is_shortDisp(d)) {
      z_la(r, d, x, b);
      return;
    }
    // Can we encode imm in 20 bits signed?
    if (Displacement::is_validDisp(d)) {
      z_lay(r, d, x, b);
      return;
    }
    z_la(r, 0, x, b);
    add2reg(r, d);
  }
}

// Generic emitter (32bit) for direct memory increment.
// For optimal code, do not specify Z_R0 as temp register.
void MacroAssembler::add2mem_32(const Address &a, int64_t imm, Register tmp) {
  if (VM_Version::has_MemWithImmALUOps() && Immediate::is_simm8(imm)) {
    z_asi(a, imm);
  } else {
    z_lgf(tmp, a);
    add2reg(tmp, imm);
    z_st(tmp, a);
  }
}

void MacroAssembler::add2mem_64(const Address &a, int64_t imm, Register tmp) {
  if (VM_Version::has_MemWithImmALUOps() && Immediate::is_simm8(imm)) {
    z_agsi(a, imm);
  } else {
    z_lg(tmp, a);
    add2reg(tmp, imm);
    z_stg(tmp, a);
  }
}

void MacroAssembler::load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed) {
  switch (size_in_bytes) {
    case  8: z_lg(dst, src); break;
    case  4: is_signed ? z_lgf(dst, src) : z_llgf(dst, src); break;
    case  2: is_signed ? z_lgh(dst, src) : z_llgh(dst, src); break;
    case  1: is_signed ? z_lgb(dst, src) : z_llgc(dst, src); break;
    default: ShouldNotReachHere();
  }
}

void MacroAssembler::store_sized_value(Register src, Address dst, size_t size_in_bytes) {
  switch (size_in_bytes) {
    case  8: z_stg(src, dst); break;
    case  4: z_st(src, dst); break;
    case  2: z_sth(src, dst); break;
    case  1: z_stc(src, dst); break;
    default: ShouldNotReachHere();
  }
}

// Split a si20 offset (20bit, signed) into an ui12 offset (12bit, unsigned) and
// a high-order summand in register tmp.
//
// return value: <  0: No split required, si20 actually has property uimm12.
//               >= 0: Split performed. Use return value as uimm12 displacement and
//                     tmp as index register.
int MacroAssembler::split_largeoffset(int64_t si20_offset, Register tmp, bool fixed_codelen, bool accumulate) {
  assert(Immediate::is_simm20(si20_offset), "sanity");
  int lg_off = (int)si20_offset &  0x0fff; // Punch out low-order 12 bits, always positive.
  int ll_off = (int)si20_offset & ~0x0fff; // Force low-order 12 bits to zero.
  assert((Displacement::is_shortDisp(si20_offset) && (ll_off == 0)) ||
         !Displacement::is_shortDisp(si20_offset), "unexpected offset values");
  assert((lg_off+ll_off) == si20_offset, "offset splitup error");

  Register work = accumulate? Z_R0 : tmp;

  if (fixed_codelen) {          // Len of code = 10 = 4 + 6.
    z_lghi(work, ll_off>>12);   // Implicit sign extension.
    z_slag(work, work, 12);
  } else {                      // Len of code = 0..10.
    if (ll_off == 0) { return -1; }
    // ll_off has 8 significant bits (at most) plus sign.
    if ((ll_off & 0x0000f000) == 0) {    // Non-zero bits only in upper halfbyte.
      z_llilh(work, ll_off >> 16);
      if (ll_off < 0) {                  // Sign-extension required.
        z_lgfr(work, work);
      }
    } else {
      if ((ll_off & 0x000f0000) == 0) {  // Non-zero bits only in lower halfbyte.
        z_llill(work, ll_off);
      } else {                           // Non-zero bits in both halfbytes.
        z_lghi(work, ll_off>>12);        // Implicit sign extension.
        z_slag(work, work, 12);
      }
    }
  }
  if (accumulate) { z_algr(tmp, work); } // len of code += 4
  return lg_off;
}

void MacroAssembler::load_float_largeoffset(FloatRegister t, int64_t si20, Register a, Register tmp) {
  if (Displacement::is_validDisp(si20)) {
    z_ley(t, si20, a);
  } else {
    // Fixed_codelen = true is a simple way to ensure that the size of load_float_largeoffset
    // does not depend on si20 (scratch buffer emit size == code buffer emit size for constant
    // pool loads).
    bool accumulate    = true;
    bool fixed_codelen = true;
    Register work;

    if (fixed_codelen) {
      z_lgr(tmp, a);  // Lgr_if_needed not applicable due to fixed_codelen.
    } else {
      accumulate = (a == tmp);
    }
    work = tmp;

    int disp12 = split_largeoffset(si20, work, fixed_codelen, accumulate);
    if (disp12 < 0) {
      z_le(t, si20, work);
    } else {
      if (accumulate) {
        z_le(t, disp12, work);
      } else {
        z_le(t, disp12, work, a);
      }
    }
  }
}

void MacroAssembler::load_double_largeoffset(FloatRegister t, int64_t si20, Register a, Register tmp) {
  if (Displacement::is_validDisp(si20)) {
    z_ldy(t, si20, a);
  } else {
    // Fixed_codelen = true is a simple way to ensure that the size of load_double_largeoffset
    // does not depend on si20 (scratch buffer emit size == code buffer emit size for constant
    // pool loads).
    bool accumulate    = true;
    bool fixed_codelen = true;
    Register work;

    if (fixed_codelen) {
      z_lgr(tmp, a);  // Lgr_if_needed not applicable due to fixed_codelen.
    } else {
      accumulate = (a == tmp);
    }
    work = tmp;

    int disp12 = split_largeoffset(si20, work, fixed_codelen, accumulate);
    if (disp12 < 0) {
      z_ld(t, si20, work);
    } else {
      if (accumulate) {
        z_ld(t, disp12, work);
      } else {
        z_ld(t, disp12, work, a);
      }
    }
  }
}

// PCrelative TOC access.
// Returns distance (in bytes) from current position to start of consts section.
// Returns 0 (zero) if no consts section exists or if it has size zero.
long MacroAssembler::toc_distance() {
  CodeSection* cs = code()->consts();
  return (long)((cs != NULL) ? cs->start()-pc() : 0);
}

// Implementation on x86/sparc assumes that constant and instruction section are
// adjacent, but this doesn't hold. Two special situations may occur, that we must
// be able to handle:
//   1. const section may be located apart from the inst section.
//   2. const section may be empty
// In both cases, we use the const section's start address to compute the "TOC",
// this seems to occur only temporarily; in the final step we always seem to end up
// with the pc-relatice variant.
//
// PC-relative offset could be +/-2**32 -> use long for disp
// Furthermore: makes no sense to have special code for
// adjacent const and inst sections.
void MacroAssembler::load_toc(Register Rtoc) {
  // Simply use distance from start of const section (should be patched in the end).
  long disp = toc_distance();

  RelocationHolder rspec = internal_word_Relocation::spec(pc() + disp);
  relocate(rspec);
  z_larl(Rtoc, RelAddr::pcrel_off32(disp));  // Offset is in halfwords.
}

// PCrelative TOC access.
// Load from anywhere pcrelative (with relocation of load instr)
void MacroAssembler::load_long_pcrelative(Register Rdst, address dataLocation) {
  address          pc             = this->pc();
  ptrdiff_t        total_distance = dataLocation - pc;
  RelocationHolder rspec          = internal_word_Relocation::spec(dataLocation);

  assert((total_distance & 0x01L) == 0, "halfword alignment is mandatory");
  assert(total_distance != 0, "sanity");

  // Some extra safety net.
  if (!RelAddr::is_in_range_of_RelAddr32(total_distance)) {
    guarantee(RelAddr::is_in_range_of_RelAddr32(total_distance), "too far away");
  }

  (this)->relocate(rspec, relocInfo::pcrel_addr_format);
  z_lgrl(Rdst, RelAddr::pcrel_off32(total_distance));
}


// PCrelative TOC access.
// Load from anywhere pcrelative (with relocation of load instr)
// loaded addr has to be relocated when added to constant pool.
void MacroAssembler::load_addr_pcrelative(Register Rdst, address addrLocation) {
  address          pc             = this->pc();
  ptrdiff_t        total_distance = addrLocation - pc;
  RelocationHolder rspec          = internal_word_Relocation::spec(addrLocation);

  assert((total_distance & 0x01L) == 0, "halfword alignment is mandatory");

  // Some extra safety net.
  if (!RelAddr::is_in_range_of_RelAddr32(total_distance)) {
    guarantee(RelAddr::is_in_range_of_RelAddr32(total_distance), "too far away");
  }

  (this)->relocate(rspec, relocInfo::pcrel_addr_format);
  z_lgrl(Rdst, RelAddr::pcrel_off32(total_distance));
}

// Generic operation: load a value from memory and test.
// CondCode indicates the sign (<0, ==0, >0) of the loaded value.
void MacroAssembler::load_and_test_byte(Register dst, const Address &a) {
  z_lb(dst, a);
  z_ltr(dst, dst);
}

void MacroAssembler::load_and_test_short(Register dst, const Address &a) {
  int64_t disp = a.disp20();
  if (Displacement::is_shortDisp(disp)) {
    z_lh(dst, a);
  } else if (Displacement::is_longDisp(disp)) {
    z_lhy(dst, a);
  } else {
    guarantee(false, "displacement out of range");
  }
  z_ltr(dst, dst);
}

void MacroAssembler::load_and_test_int(Register dst, const Address &a) {
  z_lt(dst, a);
}

void MacroAssembler::load_and_test_int2long(Register dst, const Address &a) {
  z_ltgf(dst, a);
}

void MacroAssembler::load_and_test_long(Register dst, const Address &a) {
  z_ltg(dst, a);
}

// Test a bit in memory.
void MacroAssembler::testbit(const Address &a, unsigned int bit) {
  assert(a.index() == noreg, "no index reg allowed in testbit");
  if (bit <= 7) {
    z_tm(a.disp() + 3, a.base(), 1 << bit);
  } else if (bit <= 15) {
    z_tm(a.disp() + 2, a.base(), 1 << (bit - 8));
  } else if (bit <= 23) {
    z_tm(a.disp() + 1, a.base(), 1 << (bit - 16));
  } else if (bit <= 31) {
    z_tm(a.disp() + 0, a.base(), 1 << (bit - 24));
  } else {
    ShouldNotReachHere();
  }
}

// Test a bit in a register. Result is reflected in CC.
void MacroAssembler::testbit(Register r, unsigned int bitPos) {
  if (bitPos < 16) {
    z_tmll(r, 1U<<bitPos);
  } else if (bitPos < 32) {
    z_tmlh(r, 1U<<(bitPos-16));
  } else if (bitPos < 48) {
    z_tmhl(r, 1U<<(bitPos-32));
  } else if (bitPos < 64) {
    z_tmhh(r, 1U<<(bitPos-48));
  } else {
    ShouldNotReachHere();
  }
}

// Clear a register, i.e. load const zero into reg.
// Return len (in bytes) of generated instruction(s).
// whole_reg: Clear 64 bits if true, 32 bits otherwise.
// set_cc:    Use instruction that sets the condition code, if true.
int MacroAssembler::clear_reg(Register r, bool whole_reg, bool set_cc) {
  unsigned int start_off = offset();
  if (whole_reg) {
    set_cc ? z_xgr(r, r) : z_laz(r, 0, Z_R0);
  } else {  // Only 32bit register.
    set_cc ? z_xr(r, r) : z_lhi(r, 0);
  }
  return offset() - start_off;
}

#ifdef ASSERT
int MacroAssembler::preset_reg(Register r, unsigned long pattern, int pattern_len) {
  switch (pattern_len) {
    case 1:
      pattern = (pattern & 0x000000ff)  | ((pattern & 0x000000ff)<<8);
    case 2:
      pattern = (pattern & 0x0000ffff)  | ((pattern & 0x0000ffff)<<16);
    case 4:
      pattern = (pattern & 0xffffffffL) | ((pattern & 0xffffffffL)<<32);
    case 8:
      return load_const_optimized_rtn_len(r, pattern, true);
      break;
    default:
      guarantee(false, "preset_reg: bad len");
  }
  return 0;
}
#endif

// addr: Address descriptor of memory to clear index register will not be used !
// size: Number of bytes to clear.
//    !!! DO NOT USE THEM FOR ATOMIC MEMORY CLEARING !!!
//    !!! Use store_const() instead                  !!!
void MacroAssembler::clear_mem(const Address& addr, unsigned size) {
  guarantee(size <= 256, "MacroAssembler::clear_mem: size too large");

  if (size == 1) {
    z_mvi(addr, 0);
    return;
  }

  switch (size) {
    case 2: z_mvhhi(addr, 0);
      return;
    case 4: z_mvhi(addr, 0);
      return;
    case 8: z_mvghi(addr, 0);
      return;
    default: ; // Fallthru to xc.
  }

  z_xc(addr, size, addr);
}

void MacroAssembler::align(int modulus) {
  while (offset() % modulus != 0) z_nop();
}

// Special version for non-relocateable code if required alignment
// is larger than CodeEntryAlignment.
void MacroAssembler::align_address(int modulus) {
  while ((uintptr_t)pc() % modulus != 0) z_nop();
}

Address MacroAssembler::argument_address(RegisterOrConstant arg_slot,
                                         Register temp_reg,
                                         int64_t extra_slot_offset) {
  // On Z, we can have index and disp in an Address. So don't call argument_offset,
  // which issues an unnecessary add instruction.
  int stackElementSize = Interpreter::stackElementSize;
  int64_t offset = extra_slot_offset * stackElementSize;
  const Register argbase = Z_esp;
  if (arg_slot.is_constant()) {
    offset += arg_slot.as_constant() * stackElementSize;
    return Address(argbase, offset);
  }
  // else
  assert(temp_reg != noreg, "must specify");
  assert(temp_reg != Z_ARG1, "base and index are conflicting");
  z_sllg(temp_reg, arg_slot.as_register(), exact_log2(stackElementSize)); // tempreg = arg_slot << 3
  return Address(argbase, temp_reg, offset);
}


//===================================================================
//===   START   C O N S T A N T S   I N   C O D E   S T R E A M   ===
//===================================================================
//===            P A T CH A B L E   C O N S T A N T S             ===
//===================================================================


//---------------------------------------------------
//  Load (patchable) constant into register
//---------------------------------------------------


// Load absolute address (and try to optimize).
//   Note: This method is usable only for position-fixed code,
//         referring to a position-fixed target location.
//         If not so, relocations and patching must be used.
void MacroAssembler::load_absolute_address(Register d, address addr) {
  assert(addr != NULL, "should not happen");
  BLOCK_COMMENT("load_absolute_address:");
  if (addr == NULL) {
    z_larl(d, pc()); // Dummy emit for size calc.
    return;
  }

  if (RelAddr::is_in_range_of_RelAddr32(addr, pc())) {
    z_larl(d, addr);
    return;
  }

  load_const_optimized(d, (long)addr);
}

// Load a 64bit constant.
// Patchable code sequence, but not atomically patchable.
// Make sure to keep code size constant -> no value-dependent optimizations.
// Do not kill condition code.
void MacroAssembler::load_const(Register t, long x) {
  Assembler::z_iihf(t, (int)(x >> 32));
  Assembler::z_iilf(t, (int)(x & 0xffffffff));
}

// Load a 32bit constant into a 64bit register, sign-extend or zero-extend.
// Patchable code sequence, but not atomically patchable.
// Make sure to keep code size constant -> no value-dependent optimizations.
// Do not kill condition code.
void MacroAssembler::load_const_32to64(Register t, int64_t x, bool sign_extend) {
  if (sign_extend) { Assembler::z_lgfi(t, x); }
  else             { Assembler::z_llilf(t, x); }
}

// Load narrow oop constant, no decompression.
void MacroAssembler::load_narrow_oop(Register t, narrowOop a) {
  assert(UseCompressedOops, "must be on to call this method");
  load_const_32to64(t, a, false /*sign_extend*/);
}

// Load narrow klass constant, compression required.
void MacroAssembler::load_narrow_klass(Register t, Klass* k) {
  assert(UseCompressedClassPointers, "must be on to call this method");
  narrowKlass encoded_k = Klass::encode_klass(k);
  load_const_32to64(t, encoded_k, false /*sign_extend*/);
}

//------------------------------------------------------
//  Compare (patchable) constant with register.
//------------------------------------------------------

// Compare narrow oop in reg with narrow oop constant, no decompression.
void MacroAssembler::compare_immediate_narrow_oop(Register oop1, narrowOop oop2) {
  assert(UseCompressedOops, "must be on to call this method");

  Assembler::z_clfi(oop1, oop2);
}

// Compare narrow oop in reg with narrow oop constant, no decompression.
void MacroAssembler::compare_immediate_narrow_klass(Register klass1, Klass* klass2) {
  assert(UseCompressedClassPointers, "must be on to call this method");
  narrowKlass encoded_k = Klass::encode_klass(klass2);

  Assembler::z_clfi(klass1, encoded_k);
}

//----------------------------------------------------------
//  Check which kind of load_constant we have here.
//----------------------------------------------------------

// Detection of CPU version dependent load_const sequence.
// The detection is valid only for code sequences generated by load_const,
// not load_const_optimized.
bool MacroAssembler::is_load_const(address a) {
  unsigned long inst1, inst2;
  unsigned int  len1,  len2;

  len1 = get_instruction(a, &inst1);
  len2 = get_instruction(a + len1, &inst2);

  return is_z_iihf(inst1) && is_z_iilf(inst2);
}

// Detection of CPU version dependent load_const_32to64 sequence.
// Mostly used for narrow oops and narrow Klass pointers.
// The detection is valid only for code sequences generated by load_const_32to64.
bool MacroAssembler::is_load_const_32to64(address pos) {
  unsigned long inst1, inst2;
  unsigned int len1;

  len1 = get_instruction(pos, &inst1);
  return is_z_llilf(inst1);
}

// Detection of compare_immediate_narrow sequence.
// The detection is valid only for code sequences generated by compare_immediate_narrow_oop.
bool MacroAssembler::is_compare_immediate32(address pos) {
  return is_equal(pos, CLFI_ZOPC, RIL_MASK);
}

// Detection of compare_immediate_narrow sequence.
// The detection is valid only for code sequences generated by compare_immediate_narrow_oop.
bool MacroAssembler::is_compare_immediate_narrow_oop(address pos) {
  return is_compare_immediate32(pos);
  }

// Detection of compare_immediate_narrow sequence.
// The detection is valid only for code sequences generated by compare_immediate_narrow_klass.
bool MacroAssembler::is_compare_immediate_narrow_klass(address pos) {
  return is_compare_immediate32(pos);
}

//-----------------------------------
//  patch the load_constant
//-----------------------------------

// CPU-version dependend patching of load_const.
void MacroAssembler::patch_const(address a, long x) {
  assert(is_load_const(a), "not a load of a constant");
  set_imm32((address)a, (int) ((x >> 32) & 0xffffffff));
  set_imm32((address)(a + 6), (int)(x & 0xffffffff));
}

// Patching the value of CPU version dependent load_const_32to64 sequence.
// The passed ptr MUST be in compressed format!
int MacroAssembler::patch_load_const_32to64(address pos, int64_t np) {
  assert(is_load_const_32to64(pos), "not a load of a narrow ptr (oop or klass)");

  set_imm32(pos, np);
  return 6;
}

// Patching the value of CPU version dependent compare_immediate_narrow sequence.
// The passed ptr MUST be in compressed format!
int MacroAssembler::patch_compare_immediate_32(address pos, int64_t np) {
  assert(is_compare_immediate32(pos), "not a compressed ptr compare");

  set_imm32(pos, np);
  return 6;
}

// Patching the immediate value of CPU version dependent load_narrow_oop sequence.
// The passed ptr must NOT be in compressed format!
int MacroAssembler::patch_load_narrow_oop(address pos, oop o) {
  assert(UseCompressedOops, "Can only patch compressed oops");

  narrowOop no = oopDesc::encode_heap_oop(o);
  return patch_load_const_32to64(pos, no);
}

// Patching the immediate value of CPU version dependent load_narrow_klass sequence.
// The passed ptr must NOT be in compressed format!
int MacroAssembler::patch_load_narrow_klass(address pos, Klass* k) {
  assert(UseCompressedClassPointers, "Can only patch compressed klass pointers");

  narrowKlass nk = Klass::encode_klass(k);
  return patch_load_const_32to64(pos, nk);
}

// Patching the immediate value of CPU version dependent compare_immediate_narrow_oop sequence.
// The passed ptr must NOT be in compressed format!
int MacroAssembler::patch_compare_immediate_narrow_oop(address pos, oop o) {
  assert(UseCompressedOops, "Can only patch compressed oops");

  narrowOop no = oopDesc::encode_heap_oop(o);
  return patch_compare_immediate_32(pos, no);
}

// Patching the immediate value of CPU version dependent compare_immediate_narrow_klass sequence.
// The passed ptr must NOT be in compressed format!
int MacroAssembler::patch_compare_immediate_narrow_klass(address pos, Klass* k) {
  assert(UseCompressedClassPointers, "Can only patch compressed klass pointers");

  narrowKlass nk = Klass::encode_klass(k);
  return patch_compare_immediate_32(pos, nk);
}

//------------------------------------------------------------------------
//  Extract the constant from a load_constant instruction stream.
//------------------------------------------------------------------------

// Get constant from a load_const sequence.
long MacroAssembler::get_const(address a) {
  assert(is_load_const(a), "not a load of a constant");
  unsigned long x;
  x =  (((unsigned long) (get_imm32(a,0) & 0xffffffff)) << 32);
  x |= (((unsigned long) (get_imm32(a,1) & 0xffffffff)));
  return (long) x;
}

//--------------------------------------
//  Store a constant in memory.
//--------------------------------------

// General emitter to move a constant to memory.
// The store is atomic.
//  o Address must be given in RS format (no index register)
//  o Displacement should be 12bit unsigned for efficiency. 20bit signed also supported.
//  o Constant can be 1, 2, 4, or 8 bytes, signed or unsigned.
//  o Memory slot can be 1, 2, 4, or 8 bytes, signed or unsigned.
//  o Memory slot must be at least as wide as constant, will assert otherwise.
//  o Signed constants will sign-extend, unsigned constants will zero-extend to slot width.
int MacroAssembler::store_const(const Address &dest, long imm,
                                unsigned int lm, unsigned int lc,
                                Register scratch) {
  int64_t  disp = dest.disp();
  Register base = dest.base();
  assert(!dest.has_index(), "not supported");
  assert((lm==1)||(lm==2)||(lm==4)||(lm==8), "memory   length not supported");
  assert((lc==1)||(lc==2)||(lc==4)||(lc==8), "constant length not supported");
  assert(lm>=lc, "memory slot too small");
  assert(lc==8 || Immediate::is_simm(imm, lc*8), "const out of range");
  assert(Displacement::is_validDisp(disp), "displacement out of range");

  bool is_shortDisp = Displacement::is_shortDisp(disp);
  int store_offset = -1;

  // For target len == 1 it's easy.
  if (lm == 1) {
    store_offset = offset();
    if (is_shortDisp) {
      z_mvi(disp, base, imm);
      return store_offset;
    } else {
      z_mviy(disp, base, imm);
      return store_offset;
    }
  }

  // All the "good stuff" takes an unsigned displacement.
  if (is_shortDisp) {
    // NOTE: Cannot use clear_mem for imm==0, because it is not atomic.

    store_offset = offset();
    switch (lm) {
      case 2:  // Lc == 1 handled correctly here, even for unsigned. Instruction does no widening.
        z_mvhhi(disp, base, imm);
        return store_offset;
      case 4:
        if (Immediate::is_simm16(imm)) {
          z_mvhi(disp, base, imm);
          return store_offset;
        }
        break;
      case 8:
        if (Immediate::is_simm16(imm)) {
          z_mvghi(disp, base, imm);
          return store_offset;
        }
        break;
      default:
        ShouldNotReachHere();
        break;
    }
  }

  //  Can't optimize, so load value and store it.
  guarantee(scratch != noreg, " need a scratch register here !");
  if (imm != 0) {
    load_const_optimized(scratch, imm);  // Preserves CC anyway.
  } else {
    // Leave CC alone!!
    (void) clear_reg(scratch, true, false); // Indicate unused result.
  }

  store_offset = offset();
  if (is_shortDisp) {
    switch (lm) {
      case 2:
        z_sth(scratch, disp, Z_R0, base);
        return store_offset;
      case 4:
        z_st(scratch, disp, Z_R0, base);
        return store_offset;
      case 8:
        z_stg(scratch, disp, Z_R0, base);
        return store_offset;
      default:
        ShouldNotReachHere();
        break;
    }
  } else {
    switch (lm) {
      case 2:
        z_sthy(scratch, disp, Z_R0, base);
        return store_offset;
      case 4:
        z_sty(scratch, disp, Z_R0, base);
        return store_offset;
      case 8:
        z_stg(scratch, disp, Z_R0, base);
        return store_offset;
      default:
        ShouldNotReachHere();
        break;
    }
  }
  return -1; // should not reach here
}

//===================================================================
//===       N O T   P A T CH A B L E   C O N S T A N T S          ===
//===================================================================

// Load constant x into register t with a fast instrcution sequence
// depending on the bits in x. Preserves CC under all circumstances.
int MacroAssembler::load_const_optimized_rtn_len(Register t, long x, bool emit) {
  if (x == 0) {
    int len;
    if (emit) {
      len = clear_reg(t, true, false);
    } else {
      len = 4;
    }
    return len;
  }

  if (Immediate::is_simm16(x)) {
    if (emit) { z_lghi(t, x); }
    return 4;
  }

  // 64 bit value: | part1 | part2 | part3 | part4 |
  // At least one part is not zero!
  int part1 = ((x >> 32) & 0xffff0000) >> 16;
  int part2 = (x >> 32) & 0x0000ffff;
  int part3 = (x & 0xffff0000) >> 16;
  int part4 = (x & 0x0000ffff);

  // Lower word only (unsigned).
  if ((part1 == 0) && (part2 == 0)) {
    if (part3 == 0) {
      if (emit) z_llill(t, part4);
      return 4;
    }
    if (part4 == 0) {
      if (emit) z_llilh(t, part3);
      return 4;
    }
    if (emit) z_llilf(t, (int)(x & 0xffffffff));
    return 6;
  }

  // Upper word only.
  if ((part3 == 0) && (part4 == 0)) {
    if (part1 == 0) {
      if (emit) z_llihl(t, part2);
      return 4;
    }
    if (part2 == 0) {
      if (emit) z_llihh(t, part1);
      return 4;
    }
    if (emit) z_llihf(t, (int)(x >> 32));
    return 6;
  }

  // Lower word only (signed).
  if ((part1 == 0x0000ffff) && (part2 == 0x0000ffff) && ((part3 & 0x00008000) != 0)) {
    if (emit) z_lgfi(t, (int)(x & 0xffffffff));
    return 6;
  }

  int len = 0;

  if ((part1 == 0) || (part2 == 0)) {
    if (part1 == 0) {
      if (emit) z_llihl(t, part2);
      len += 4;
    } else {
      if (emit) z_llihh(t, part1);
      len += 4;
    }
  } else {
    if (emit) z_llihf(t, (int)(x >> 32));
    len += 6;
  }

  if ((part3 == 0) || (part4 == 0)) {
    if (part3 == 0) {
      if (emit) z_iill(t, part4);
      len += 4;
    } else {
      if (emit) z_iilh(t, part3);
      len += 4;
    }
  } else {
    if (emit) z_iilf(t, (int)(x & 0xffffffff));
    len += 6;
  }
  return len;
}

//=====================================================================
//===     H I G H E R   L E V E L   B R A N C H   E M I T T E R S   ===
//=====================================================================

// Note: In the worst case, one of the scratch registers is destroyed!!!
void MacroAssembler::compare32_and_branch(Register r1, RegisterOrConstant x2, branch_condition cond, Label& lbl) {
  // Right operand is constant.
  if (x2.is_constant()) {
    jlong value = x2.as_constant();
    compare_and_branch_optimized(r1, value, cond, lbl, /*len64=*/false, /*has_sign=*/true);
    return;
  }

  // Right operand is in register.
  compare_and_branch_optimized(r1, x2.as_register(), cond, lbl, /*len64=*/false, /*has_sign=*/true);
}

// Note: In the worst case, one of the scratch registers is destroyed!!!
void MacroAssembler::compareU32_and_branch(Register r1, RegisterOrConstant x2, branch_condition cond, Label& lbl) {
  // Right operand is constant.
  if (x2.is_constant()) {
    jlong value = x2.as_constant();
    compare_and_branch_optimized(r1, value, cond, lbl, /*len64=*/false, /*has_sign=*/false);
    return;
  }

  // Right operand is in register.
  compare_and_branch_optimized(r1, x2.as_register(), cond, lbl, /*len64=*/false, /*has_sign=*/false);
}

// Note: In the worst case, one of the scratch registers is destroyed!!!
void MacroAssembler::compare64_and_branch(Register r1, RegisterOrConstant x2, branch_condition cond, Label& lbl) {
  // Right operand is constant.
  if (x2.is_constant()) {
    jlong value = x2.as_constant();
    compare_and_branch_optimized(r1, value, cond, lbl, /*len64=*/true, /*has_sign=*/true);
    return;
  }

  // Right operand is in register.
  compare_and_branch_optimized(r1, x2.as_register(), cond, lbl, /*len64=*/true, /*has_sign=*/true);
}

void MacroAssembler::compareU64_and_branch(Register r1, RegisterOrConstant x2, branch_condition cond, Label& lbl) {
  // Right operand is constant.
  if (x2.is_constant()) {
    jlong value = x2.as_constant();
    compare_and_branch_optimized(r1, value, cond, lbl, /*len64=*/true, /*has_sign=*/false);
    return;
  }

  // Right operand is in register.
  compare_and_branch_optimized(r1, x2.as_register(), cond, lbl, /*len64=*/true, /*has_sign=*/false);
}

// Generate an optimal branch to the branch target.
// Optimal means that a relative branch (brc or brcl) is used if the
// branch distance is short enough. Loading the target address into a
// register and branching via reg is used as fallback only.
//
// Used registers:
//   Z_R1 - work reg. Holds branch target address.
//          Used in fallback case only.
//
// This version of branch_optimized is good for cases where the target address is known
// and constant, i.e. is never changed (no relocation, no patching).
void MacroAssembler::branch_optimized(Assembler::branch_condition cond, address branch_addr) {
  address branch_origin = pc();

  if (RelAddr::is_in_range_of_RelAddr16(branch_addr, branch_origin)) {
    z_brc(cond, branch_addr);
  } else if (RelAddr::is_in_range_of_RelAddr32(branch_addr, branch_origin)) {
    z_brcl(cond, branch_addr);
  } else {
    load_const_optimized(Z_R1, branch_addr);  // CC must not get killed by load_const_optimized.
    z_bcr(cond, Z_R1);
  }
}

// This version of branch_optimized is good for cases where the target address
// is potentially not yet known at the time the code is emitted.
//
// One very common case is a branch to an unbound label which is handled here.
// The caller might know (or hope) that the branch distance is short enough
// to be encoded in a 16bit relative address. In this case he will pass a
// NearLabel branch_target.
// Care must be taken with unbound labels. Each call to target(label) creates
// an entry in the patch queue for that label to patch all references of the label
// once it gets bound. Those recorded patch locations must be patchable. Otherwise,
// an assertion fires at patch time.
void MacroAssembler::branch_optimized(Assembler::branch_condition cond, Label& branch_target) {
  if (branch_target.is_bound()) {
    address branch_addr = target(branch_target);
    branch_optimized(cond, branch_addr);
  } else {
    z_brcl(cond, branch_target); // Let's hope target is in range. Otherwise, we will abort at patch time.
  }
}

// Generate an optimal compare and branch to the branch target.
// Optimal means that a relative branch (clgrj, brc or brcl) is used if the
// branch distance is short enough. Loading the target address into a
// register and branching via reg is used as fallback only.
//
// Input:
//   r1 - left compare operand
//   r2 - right compare operand
void MacroAssembler::compare_and_branch_optimized(Register r1,
                                                  Register r2,
                                                  Assembler::branch_condition cond,
                                                  address  branch_addr,
                                                  bool     len64,
                                                  bool     has_sign) {
  unsigned int casenum = (len64?2:0)+(has_sign?0:1);

  address branch_origin = pc();
  if (VM_Version::has_CompareBranch() && RelAddr::is_in_range_of_RelAddr16(branch_addr, branch_origin)) {
    switch (casenum) {
      case 0: z_crj( r1, r2, cond, branch_addr); break;
      case 1: z_clrj (r1, r2, cond, branch_addr); break;
      case 2: z_cgrj(r1, r2, cond, branch_addr); break;
      case 3: z_clgrj(r1, r2, cond, branch_addr); break;
      default: ShouldNotReachHere(); break;
    }
  } else {
    switch (casenum) {
      case 0: z_cr( r1, r2); break;
      case 1: z_clr(r1, r2); break;
      case 2: z_cgr(r1, r2); break;
      case 3: z_clgr(r1, r2); break;
      default: ShouldNotReachHere(); break;
    }
    branch_optimized(cond, branch_addr);
  }
}

// Generate an optimal compare and branch to the branch target.
// Optimal means that a relative branch (clgij, brc or brcl) is used if the
// branch distance is short enough. Loading the target address into a
// register and branching via reg is used as fallback only.
//
// Input:
//   r1 - left compare operand (in register)
//   x2 - right compare operand (immediate)
void MacroAssembler::compare_and_branch_optimized(Register r1,
                                                  jlong    x2,
                                                  Assembler::branch_condition cond,
                                                  Label&   branch_target,
                                                  bool     len64,
                                                  bool     has_sign) {
  address      branch_origin = pc();
  bool         x2_imm8       = (has_sign && Immediate::is_simm8(x2)) || (!has_sign && Immediate::is_uimm8(x2));
  bool         is_RelAddr16  = (branch_target.is_bound() &&
                                RelAddr::is_in_range_of_RelAddr16(target(branch_target), branch_origin));
  unsigned int casenum       = (len64?2:0)+(has_sign?0:1);

  if (VM_Version::has_CompareBranch() && is_RelAddr16 && x2_imm8) {
    switch (casenum) {
      case 0: z_cij( r1, x2, cond, branch_target); break;
      case 1: z_clij(r1, x2, cond, branch_target); break;
      case 2: z_cgij(r1, x2, cond, branch_target); break;
      case 3: z_clgij(r1, x2, cond, branch_target); break;
      default: ShouldNotReachHere(); break;
    }
    return;
  }

  if (x2 == 0) {
    switch (casenum) {
      case 0: z_ltr(r1, r1); break;
      case 1: z_ltr(r1, r1); break; // Caution: unsigned test only provides zero/notZero indication!
      case 2: z_ltgr(r1, r1); break;
      case 3: z_ltgr(r1, r1); break; // Caution: unsigned test only provides zero/notZero indication!
      default: ShouldNotReachHere(); break;
    }
  } else {
    if ((has_sign && Immediate::is_simm16(x2)) || (!has_sign && Immediate::is_uimm(x2, 15))) {
      switch (casenum) {
        case 0: z_chi(r1, x2); break;
        case 1: z_chi(r1, x2); break; // positive immediate < 2**15
        case 2: z_cghi(r1, x2); break;
        case 3: z_cghi(r1, x2); break; // positive immediate < 2**15
        default: break;
      }
    } else if ( (has_sign && Immediate::is_simm32(x2)) || (!has_sign && Immediate::is_uimm32(x2)) ) {
      switch (casenum) {
        case 0: z_cfi( r1, x2); break;
        case 1: z_clfi(r1, x2); break;
        case 2: z_cgfi(r1, x2); break;
        case 3: z_clgfi(r1, x2); break;
        default: ShouldNotReachHere(); break;
      }
    } else {
      // No instruction with immediate operand possible, so load into register.
      Register scratch = (r1 != Z_R0) ? Z_R0 : Z_R1;
      load_const_optimized(scratch, x2);
      switch (casenum) {
        case 0: z_cr( r1, scratch); break;
        case 1: z_clr(r1, scratch); break;
        case 2: z_cgr(r1, scratch); break;
        case 3: z_clgr(r1, scratch); break;
        default: ShouldNotReachHere(); break;
      }
    }
  }
  branch_optimized(cond, branch_target);
}

// Generate an optimal compare and branch to the branch target.
// Optimal means that a relative branch (clgrj, brc or brcl) is used if the
// branch distance is short enough. Loading the target address into a
// register and branching via reg is used as fallback only.
//
// Input:
//   r1 - left compare operand
//   r2 - right compare operand
void MacroAssembler::compare_and_branch_optimized(Register r1,
                                                  Register r2,
                                                  Assembler::branch_condition cond,
                                                  Label&   branch_target,
                                                  bool     len64,
                                                  bool     has_sign) {
  unsigned int casenum = (len64?2:0)+(has_sign?0:1);

  if (branch_target.is_bound()) {
    address branch_addr = target(branch_target);
    compare_and_branch_optimized(r1, r2, cond, branch_addr, len64, has_sign);
  } else {
    {
      switch (casenum) {
        case 0: z_cr( r1, r2); break;
        case 1: z_clr(r1, r2); break;
        case 2: z_cgr(r1, r2); break;
        case 3: z_clgr(r1, r2); break;
        default: ShouldNotReachHere(); break;
      }
      branch_optimized(cond, branch_target);
    }
  }
}

//===========================================================================
//===   END     H I G H E R   L E V E L   B R A N C H   E M I T T E R S   ===
//===========================================================================

AddressLiteral MacroAssembler::allocate_metadata_address(Metadata* obj) {
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int index = oop_recorder()->allocate_metadata_index(obj);
  RelocationHolder rspec = metadata_Relocation::spec(index);
  return AddressLiteral((address)obj, rspec);
}

AddressLiteral MacroAssembler::constant_metadata_address(Metadata* obj) {
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
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

// NOTE: destroys r
void MacroAssembler::c2bool(Register r, Register t) {
  z_lcr(t, r);   // t = -r
  z_or(r, t);    // r = -r OR r
  z_srl(r, 31);  // Yields 0 if r was 0, 1 otherwise.
}

RegisterOrConstant MacroAssembler::delayed_value_impl(intptr_t* delayed_value_addr,
                                                      Register tmp,
                                                      int offset) {
  intptr_t value = *delayed_value_addr;
  if (value != 0) {
    return RegisterOrConstant(value + offset);
  }

  BLOCK_COMMENT("delayed_value {");
  // Load indirectly to solve generation ordering problem.
  load_absolute_address(tmp, (address) delayed_value_addr); // tmp = a;
  z_lg(tmp, 0, tmp);                   // tmp = *tmp;

#ifdef ASSERT
  NearLabel L;
  compare64_and_branch(tmp, (intptr_t)0L, Assembler::bcondNotEqual, L);
  z_illtrap();
  bind(L);
#endif

  if (offset != 0) {
    z_agfi(tmp, offset);               // tmp = tmp + offset;
  }

  BLOCK_COMMENT("} delayed_value");
  return RegisterOrConstant(tmp);
}

// Patch instruction `inst' at offset `inst_pos' to refer to `dest_pos'
// and return the resulting instruction.
// Dest_pos and inst_pos are 32 bit only. These parms can only designate
// relative positions.
// Use correct argument types. Do not pre-calculate distance.
unsigned long MacroAssembler::patched_branch(address dest_pos, unsigned long inst, address inst_pos) {
  int c = 0;
  unsigned long patched_inst = 0;
  if (is_call_pcrelative_short(inst) ||
      is_branch_pcrelative_short(inst) ||
      is_branchoncount_pcrelative_short(inst) ||
      is_branchonindex32_pcrelative_short(inst)) {
    c = 1;
    int m = fmask(15, 0);    // simm16(-1, 16, 32);
    int v = simm16(RelAddr::pcrel_off16(dest_pos, inst_pos), 16, 32);
    patched_inst = (inst & ~m) | v;
  } else if (is_compareandbranch_pcrelative_short(inst)) {
    c = 2;
    long m = fmask(31, 16);  // simm16(-1, 16, 48);
    long v = simm16(RelAddr::pcrel_off16(dest_pos, inst_pos), 16, 48);
    patched_inst = (inst & ~m) | v;
  } else if (is_branchonindex64_pcrelative_short(inst)) {
    c = 3;
    long m = fmask(31, 16);  // simm16(-1, 16, 48);
    long v = simm16(RelAddr::pcrel_off16(dest_pos, inst_pos), 16, 48);
    patched_inst = (inst & ~m) | v;
  } else if (is_call_pcrelative_long(inst) || is_branch_pcrelative_long(inst)) {
    c = 4;
    long m = fmask(31, 0);  // simm32(-1, 16, 48);
    long v = simm32(RelAddr::pcrel_off32(dest_pos, inst_pos), 16, 48);
    patched_inst = (inst & ~m) | v;
  } else if (is_pcrelative_long(inst)) { // These are the non-branch pc-relative instructions.
    c = 5;
    long m = fmask(31, 0);  // simm32(-1, 16, 48);
    long v = simm32(RelAddr::pcrel_off32(dest_pos, inst_pos), 16, 48);
    patched_inst = (inst & ~m) | v;
  } else {
    print_dbg_msg(tty, inst, "not a relative branch", 0);
    dump_code_range(tty, inst_pos, 32, "not a pcrelative branch");
    ShouldNotReachHere();
  }

  long new_off = get_pcrel_offset(patched_inst);
  if (new_off != (dest_pos-inst_pos)) {
    tty->print_cr("case %d: dest_pos = %p, inst_pos = %p, disp = %ld(%12.12lx)", c, dest_pos, inst_pos, new_off, new_off);
    print_dbg_msg(tty, inst,         "<- original instruction: branch patching error", 0);
    print_dbg_msg(tty, patched_inst, "<- patched  instruction: branch patching error", 0);
#ifdef LUCY_DBG
    VM_Version::z_SIGSEGV();
#endif
    ShouldNotReachHere();
  }
  return patched_inst;
}

// Only called when binding labels (share/vm/asm/assembler.cpp)
// Pass arguments as intended. Do not pre-calculate distance.
void MacroAssembler::pd_patch_instruction(address branch, address target) {
  unsigned long stub_inst;
  int           inst_len = get_instruction(branch, &stub_inst);

  set_instruction(branch, patched_branch(target, stub_inst, branch), inst_len);
}


// Extract relative address (aka offset).
// inv_simm16 works for 4-byte instructions only.
// compare and branch instructions are 6-byte and have a 16bit offset "in the middle".
long MacroAssembler::get_pcrel_offset(unsigned long inst) {

  if (MacroAssembler::is_pcrelative_short(inst)) {
    if (((inst&0xFFFFffff00000000UL) == 0) && ((inst&0x00000000FFFF0000UL) != 0)) {
      return RelAddr::inv_pcrel_off16(inv_simm16(inst));
    } else {
      return RelAddr::inv_pcrel_off16(inv_simm16_48(inst));
    }
  }

  if (MacroAssembler::is_pcrelative_long(inst)) {
    return RelAddr::inv_pcrel_off32(inv_simm32(inst));
  }

  print_dbg_msg(tty, inst, "not a pcrelative instruction", 6);
#ifdef LUCY_DBG
  VM_Version::z_SIGSEGV();
#else
  ShouldNotReachHere();
#endif
  return -1;
}

long MacroAssembler::get_pcrel_offset(address pc) {
  unsigned long inst;
  unsigned int  len = get_instruction(pc, &inst);

#ifdef ASSERT
  long offset;
  if (MacroAssembler::is_pcrelative_short(inst) || MacroAssembler::is_pcrelative_long(inst)) {
    offset = get_pcrel_offset(inst);
  } else {
    offset = -1;
  }

  if (offset == -1) {
    dump_code_range(tty, pc, 32, "not a pcrelative instruction");
#ifdef LUCY_DBG
    VM_Version::z_SIGSEGV();
#else
    ShouldNotReachHere();
#endif
  }
  return offset;
#else
  return get_pcrel_offset(inst);
#endif // ASSERT
}

// Get target address from pc-relative instructions.
address MacroAssembler::get_target_addr_pcrel(address pc) {
  assert(is_pcrelative_long(pc), "not a pcrelative instruction");
  return pc + get_pcrel_offset(pc);
}

// Patch pc relative load address.
void MacroAssembler::patch_target_addr_pcrel(address pc, address con) {
  unsigned long inst;
  // Offset is +/- 2**32 -> use long.
  ptrdiff_t distance = con - pc;

  get_instruction(pc, &inst);

  if (is_pcrelative_short(inst)) {
    *(short *)(pc+2) = RelAddr::pcrel_off16(con, pc);  // Instructions are at least 2-byte aligned, no test required.

    // Some extra safety net.
    if (!RelAddr::is_in_range_of_RelAddr16(distance)) {
      print_dbg_msg(tty, inst, "distance out of range (16bit)", 4);
      dump_code_range(tty, pc, 32, "distance out of range (16bit)");
      guarantee(RelAddr::is_in_range_of_RelAddr16(distance), "too far away (more than +/- 2**16");
    }
    return;
  }

  if (is_pcrelative_long(inst)) {
    *(int *)(pc+2)   = RelAddr::pcrel_off32(con, pc);

    // Some Extra safety net.
    if (!RelAddr::is_in_range_of_RelAddr32(distance)) {
      print_dbg_msg(tty, inst, "distance out of range (32bit)", 6);
      dump_code_range(tty, pc, 32, "distance out of range (32bit)");
      guarantee(RelAddr::is_in_range_of_RelAddr32(distance), "too far away (more than +/- 2**32");
    }
    return;
  }

  guarantee(false, "not a pcrelative instruction to patch!");
}

// "Current PC" here means the address just behind the basr instruction.
address MacroAssembler::get_PC(Register result) {
  z_basr(result, Z_R0); // Don't branch, just save next instruction address in result.
  return pc();
}

// Get current PC + offset.
// Offset given in bytes, must be even!
// "Current PC" here means the address of the larl instruction plus the given offset.
address MacroAssembler::get_PC(Register result, int64_t offset) {
  address here = pc();
  z_larl(result, offset/2); // Save target instruction address in result.
  return here + offset;
}

// Resize_frame with SP(new) = SP(old) - [offset].
void MacroAssembler::resize_frame_sub(Register offset, Register fp, bool load_fp)
{
  assert_different_registers(offset, fp, Z_SP);
  if (load_fp) { z_lg(fp, _z_abi(callers_sp), Z_SP); }

  z_sgr(Z_SP, offset);
  z_stg(fp, _z_abi(callers_sp), Z_SP);
}

// Resize_frame with SP(new) = [addr].
void MacroAssembler::resize_frame_absolute(Register addr, Register fp, bool load_fp) {
  assert_different_registers(addr, fp, Z_SP);
  if (load_fp) { z_lg(fp, _z_abi(callers_sp), Z_SP); }

  if (addr != Z_R0) {
    // Minimize stalls by not using Z_SP immediately after update.
    z_stg(fp, _z_abi(callers_sp), addr);
    z_lgr(Z_SP, addr);
  } else {
    z_lgr(Z_SP, addr);
    z_stg(fp, _z_abi(callers_sp), Z_SP);
  }
}

// Resize_frame with SP(new) = SP(old) + offset.
void MacroAssembler::resize_frame(RegisterOrConstant offset, Register fp, bool load_fp) {
  assert_different_registers(fp, Z_SP);
  if (load_fp) z_lg(fp, _z_abi(callers_sp), Z_SP);

  if (Displacement::is_validDisp((int)_z_abi(callers_sp) + offset.constant_or_zero())) {
    // Minimize stalls by first using, then updating Z_SP.
    // Do that only if we have a small positive offset or if ExtImm are available.
    z_stg(fp, Address(Z_SP, offset, _z_abi(callers_sp)));
    add64(Z_SP, offset);
  } else {
    add64(Z_SP, offset);
    z_stg(fp, _z_abi(callers_sp), Z_SP);
  }
}

void MacroAssembler::push_frame(Register bytes, Register old_sp, bool copy_sp, bool bytes_with_inverted_sign) {
#ifdef ASSERT
  assert_different_registers(bytes, old_sp, Z_SP);
  if (!copy_sp) {
    z_cgr(old_sp, Z_SP);
    asm_assert_eq("[old_sp]!=[Z_SP]", 0x211);
  }
#endif
  if (copy_sp) { z_lgr(old_sp, Z_SP); }
  if (bytes_with_inverted_sign) {
    z_stg(old_sp, 0, bytes, Z_SP);
    add2reg_with_index(Z_SP, 0, bytes, Z_SP);
  } else {
    z_sgr(Z_SP, bytes); // Z_sgfr sufficient, but probably not faster.
    z_stg(old_sp, 0, Z_SP);
  }
}

unsigned int MacroAssembler::push_frame(unsigned int bytes, Register scratch) {
  long offset = Assembler::align(bytes, frame::alignment_in_bytes);

  if (Displacement::is_validDisp(-offset)) {
    // Minimize stalls by first using, then updating Z_SP.
    // Do that only if we have ExtImm available.
    z_stg(Z_SP, -offset, Z_SP);
    add2reg(Z_SP, -offset);
  } else {
    if (scratch != Z_R0 && scratch != Z_R1) {
      z_stg(Z_SP, -offset, Z_SP);
      add2reg(Z_SP, -offset);
    } else {   // scratch == Z_R0 || scratch == Z_R1
      z_lgr(scratch, Z_SP);
      add2reg(Z_SP, -offset);
      z_stg(scratch, 0, Z_SP);
    }
  }
  return offset;
}

// Push a frame of size `bytes' plus abi160 on top.
unsigned int MacroAssembler::push_frame_abi160(unsigned int bytes) {
  BLOCK_COMMENT("push_frame_abi160 {");
  unsigned int res = push_frame(bytes + frame::z_abi_160_size);
  BLOCK_COMMENT("} push_frame_abi160");
  return res;
}

// Pop current C frame.
void MacroAssembler::pop_frame() {
  BLOCK_COMMENT("pop_frame:");
  Assembler::z_lg(Z_SP, _z_abi(callers_sp), Z_SP);
}

void MacroAssembler::call_VM_leaf_base(address entry_point, bool allow_relocation) {
  if (allow_relocation) {
    call_c(entry_point);
  } else {
    call_c_static(entry_point);
  }
}

void MacroAssembler::call_VM_leaf_base(address entry_point) {
  bool allow_relocation = true;
  call_VM_leaf_base(entry_point, allow_relocation);
}

void MacroAssembler::call_VM_base(Register oop_result,
                                  Register last_java_sp,
                                  address  entry_point,
                                  bool     allow_relocation,
                                  bool     check_exceptions) { // Defaults to true.
  // Allow_relocation indicates, if true, that the generated code shall
  // be fit for code relocation or referenced data relocation. In other
  // words: all addresses must be considered variable. PC-relative addressing
  // is not possible then.
  // On the other hand, if (allow_relocation == false), addresses and offsets
  // may be considered stable, enabling us to take advantage of some PC-relative
  // addressing tweaks. These might improve performance and reduce code size.

  // Determine last_java_sp register.
  if (!last_java_sp->is_valid()) {
    last_java_sp = Z_SP;  // Load Z_SP as SP.
  }

  set_top_ijava_frame_at_SP_as_last_Java_frame(last_java_sp, Z_R1, allow_relocation);

  // ARG1 must hold thread address.
  z_lgr(Z_ARG1, Z_thread);

  address return_pc = NULL;
  if (allow_relocation) {
    return_pc = call_c(entry_point);
  } else {
    return_pc = call_c_static(entry_point);
  }

  reset_last_Java_frame(allow_relocation);

  // C++ interp handles this in the interpreter.
  check_and_handle_popframe(Z_thread);
  check_and_handle_earlyret(Z_thread);

  // Check for pending exceptions.
  if (check_exceptions) {
    // Check for pending exceptions (java_thread is set upon return).
    load_and_test_long(Z_R0_scratch, Address(Z_thread, Thread::pending_exception_offset()));

    // This used to conditionally jump to forward_exception however it is
    // possible if we relocate that the branch will not reach. So we must jump
    // around so we can always reach.

    Label ok;
    z_bre(ok); // Bcondequal is the same as bcondZero.
    call_stub(StubRoutines::forward_exception_entry());
    bind(ok);
  }

  // Get oop result if there is one and reset the value in the thread.
  if (oop_result->is_valid()) {
    get_vm_result(oop_result);
  }

  _last_calls_return_pc = return_pc;  // Wipe out other (error handling) calls.
}

void MacroAssembler::call_VM_base(Register oop_result,
                                  Register last_java_sp,
                                  address  entry_point,
                                  bool     check_exceptions) { // Defaults to true.
  bool allow_relocation = true;
  call_VM_base(oop_result, last_java_sp, entry_point, allow_relocation, check_exceptions);
}

// VM calls without explicit last_java_sp.

void MacroAssembler::call_VM(Register oop_result, address entry_point, bool check_exceptions) {
  // Call takes possible detour via InterpreterMacroAssembler.
  call_VM_base(oop_result, noreg, entry_point, true, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, bool check_exceptions) {
  // Z_ARG1 is reserved for the thread.
  lgr_if_needed(Z_ARG2, arg_1);
  call_VM(oop_result, entry_point, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, bool check_exceptions) {
  // Z_ARG1 is reserved for the thread.
  lgr_if_needed(Z_ARG2, arg_1);
  assert(arg_2 != Z_ARG2, "smashed argument");
  lgr_if_needed(Z_ARG3, arg_2);
  call_VM(oop_result, entry_point, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2,
                             Register arg_3, bool check_exceptions) {
  // Z_ARG1 is reserved for the thread.
  lgr_if_needed(Z_ARG2, arg_1);
  assert(arg_2 != Z_ARG2, "smashed argument");
  lgr_if_needed(Z_ARG3, arg_2);
  assert(arg_3 != Z_ARG2 && arg_3 != Z_ARG3, "smashed argument");
  lgr_if_needed(Z_ARG4, arg_3);
  call_VM(oop_result, entry_point, check_exceptions);
}

// VM static calls without explicit last_java_sp.

void MacroAssembler::call_VM_static(Register oop_result, address entry_point, bool check_exceptions) {
  // Call takes possible detour via InterpreterMacroAssembler.
  call_VM_base(oop_result, noreg, entry_point, false, check_exceptions);
}

void MacroAssembler::call_VM_static(Register oop_result, address entry_point, Register arg_1, Register arg_2,
                                    Register arg_3, bool check_exceptions) {
  // Z_ARG1 is reserved for the thread.
  lgr_if_needed(Z_ARG2, arg_1);
  assert(arg_2 != Z_ARG2, "smashed argument");
  lgr_if_needed(Z_ARG3, arg_2);
  assert(arg_3 != Z_ARG2 && arg_3 != Z_ARG3, "smashed argument");
  lgr_if_needed(Z_ARG4, arg_3);
  call_VM_static(oop_result, entry_point, check_exceptions);
}

// VM calls with explicit last_java_sp.

void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, bool check_exceptions) {
  // Call takes possible detour via InterpreterMacroAssembler.
  call_VM_base(oop_result, last_java_sp, entry_point, true, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, bool check_exceptions) {
   // Z_ARG1 is reserved for the thread.
   lgr_if_needed(Z_ARG2, arg_1);
   call_VM(oop_result, last_java_sp, entry_point, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1,
                             Register arg_2, bool check_exceptions) {
   // Z_ARG1 is reserved for the thread.
   lgr_if_needed(Z_ARG2, arg_1);
   assert(arg_2 != Z_ARG2, "smashed argument");
   lgr_if_needed(Z_ARG3, arg_2);
   call_VM(oop_result, last_java_sp, entry_point, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1,
                             Register arg_2, Register arg_3, bool check_exceptions) {
  // Z_ARG1 is reserved for the thread.
  lgr_if_needed(Z_ARG2, arg_1);
  assert(arg_2 != Z_ARG2, "smashed argument");
  lgr_if_needed(Z_ARG3, arg_2);
  assert(arg_3 != Z_ARG2 && arg_3 != Z_ARG3, "smashed argument");
  lgr_if_needed(Z_ARG4, arg_3);
  call_VM(oop_result, last_java_sp, entry_point, check_exceptions);
}

// VM leaf calls.

void MacroAssembler::call_VM_leaf(address entry_point) {
  // Call takes possible detour via InterpreterMacroAssembler.
  call_VM_leaf_base(entry_point, true);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1) {
  if (arg_1 != noreg) lgr_if_needed(Z_ARG1, arg_1);
  call_VM_leaf(entry_point);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1, Register arg_2) {
  if (arg_1 != noreg) lgr_if_needed(Z_ARG1, arg_1);
  assert(arg_2 != Z_ARG1, "smashed argument");
  if (arg_2 != noreg) lgr_if_needed(Z_ARG2, arg_2);
  call_VM_leaf(entry_point);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1, Register arg_2, Register arg_3) {
  if (arg_1 != noreg) lgr_if_needed(Z_ARG1, arg_1);
  assert(arg_2 != Z_ARG1, "smashed argument");
  if (arg_2 != noreg) lgr_if_needed(Z_ARG2, arg_2);
  assert(arg_3 != Z_ARG1 && arg_3 != Z_ARG2, "smashed argument");
  if (arg_3 != noreg) lgr_if_needed(Z_ARG3, arg_3);
  call_VM_leaf(entry_point);
}

// Static VM leaf calls.
// Really static VM leaf calls are never patched.

void MacroAssembler::call_VM_leaf_static(address entry_point) {
  // Call takes possible detour via InterpreterMacroAssembler.
  call_VM_leaf_base(entry_point, false);
}

void MacroAssembler::call_VM_leaf_static(address entry_point, Register arg_1) {
  if (arg_1 != noreg) lgr_if_needed(Z_ARG1, arg_1);
  call_VM_leaf_static(entry_point);
}

void MacroAssembler::call_VM_leaf_static(address entry_point, Register arg_1, Register arg_2) {
  if (arg_1 != noreg) lgr_if_needed(Z_ARG1, arg_1);
  assert(arg_2 != Z_ARG1, "smashed argument");
  if (arg_2 != noreg) lgr_if_needed(Z_ARG2, arg_2);
  call_VM_leaf_static(entry_point);
}

void MacroAssembler::call_VM_leaf_static(address entry_point, Register arg_1, Register arg_2, Register arg_3) {
  if (arg_1 != noreg) lgr_if_needed(Z_ARG1, arg_1);
  assert(arg_2 != Z_ARG1, "smashed argument");
  if (arg_2 != noreg) lgr_if_needed(Z_ARG2, arg_2);
  assert(arg_3 != Z_ARG1 && arg_3 != Z_ARG2, "smashed argument");
  if (arg_3 != noreg) lgr_if_needed(Z_ARG3, arg_3);
  call_VM_leaf_static(entry_point);
}

// Don't use detour via call_c(reg).
address MacroAssembler::call_c(address function_entry) {
  load_const(Z_R1, function_entry);
  return call(Z_R1);
}

// Variant for really static (non-relocatable) calls which are never patched.
address MacroAssembler::call_c_static(address function_entry) {
  load_absolute_address(Z_R1, function_entry);
#if 0 // def ASSERT
  // Verify that call site did not move.
  load_const_optimized(Z_R0, function_entry);
  z_cgr(Z_R1, Z_R0);
  z_brc(bcondEqual, 3);
  z_illtrap(0xba);
#endif
  return call(Z_R1);
}

address MacroAssembler::call_c_opt(address function_entry) {
  bool success = call_far_patchable(function_entry, -2 /* emit relocation + constant */);
  _last_calls_return_pc = success ? pc() : NULL;
  return _last_calls_return_pc;
}

// Identify a call_far_patchable instruction: LARL + LG + BASR
//
//    nop                   ; optionally, if required for alignment
//    lgrl rx,A(TOC entry)  ; PC-relative access into constant pool
//    basr Z_R14,rx         ; end of this instruction must be aligned to a word boundary
//
// Code pattern will eventually get patched into variant2 (see below for detection code).
//
bool MacroAssembler::is_call_far_patchable_variant0_at(address instruction_addr) {
  address iaddr = instruction_addr;

  // Check for the actual load instruction.
  if (!is_load_const_from_toc(iaddr)) { return false; }
  iaddr += load_const_from_toc_size();

  // Check for the call (BASR) instruction, finally.
  assert(iaddr-instruction_addr+call_byregister_size() == call_far_patchable_size(), "size mismatch");
  return is_call_byregister(iaddr);
}

// Identify a call_far_patchable instruction: BRASL
//
// Code pattern to suits atomic patching:
//    nop                       ; Optionally, if required for alignment.
//    nop    ...                ; Multiple filler nops to compensate for size difference (variant0 is longer).
//    nop                       ; For code pattern detection: Prepend each BRASL with a nop.
//    brasl  Z_R14,<reladdr>    ; End of code must be 4-byte aligned !
bool MacroAssembler::is_call_far_patchable_variant2_at(address instruction_addr) {
  const address call_addr = (address)((intptr_t)instruction_addr + call_far_patchable_size() - call_far_pcrelative_size());

  // Check for correct number of leading nops.
  address iaddr;
  for (iaddr = instruction_addr; iaddr < call_addr; iaddr += nop_size()) {
    if (!is_z_nop(iaddr)) { return false; }
  }
  assert(iaddr == call_addr, "sanity");

  // --> Check for call instruction.
  if (is_call_far_pcrelative(call_addr)) {
    assert(call_addr-instruction_addr+call_far_pcrelative_size() == call_far_patchable_size(), "size mismatch");
    return true;
  }

  return false;
}

// Emit a NOT mt-safely patchable 64 bit absolute call.
// If toc_offset == -2, then the destination of the call (= target) is emitted
//                      to the constant pool and a runtime_call relocation is added
//                      to the code buffer.
// If toc_offset != -2, target must already be in the constant pool at
//                      _ctableStart+toc_offset (a caller can retrieve toc_offset
//                      from the runtime_call relocation).
// Special handling of emitting to scratch buffer when there is no constant pool.
// Slightly changed code pattern. We emit an additional nop if we would
// not end emitting at a word aligned address. This is to ensure
// an atomically patchable displacement in brasl instructions.
//
// A call_far_patchable comes in different flavors:
//  - LARL(CP) / LG(CP) / BR (address in constant pool, access via CP register)
//  - LGRL(CP) / BR          (address in constant pool, pc-relative accesss)
//  - BRASL                  (relative address of call target coded in instruction)
// All flavors occupy the same amount of space. Length differences are compensated
// by leading nops, such that the instruction sequence always ends at the same
// byte offset. This is required to keep the return offset constant.
// Furthermore, the return address (the end of the instruction sequence) is forced
// to be on a 4-byte boundary. This is required for atomic patching, should we ever
// need to patch the call target of the BRASL flavor.
// RETURN value: false, if no constant pool entry could be allocated, true otherwise.
bool MacroAssembler::call_far_patchable(address target, int64_t tocOffset) {
  // Get current pc and ensure word alignment for end of instr sequence.
  const address start_pc = pc();
  const intptr_t       start_off = offset();
  assert(!call_far_patchable_requires_alignment_nop(start_pc), "call_far_patchable requires aligned address");
  const ptrdiff_t      dist      = (ptrdiff_t)(target - (start_pc + 2)); // Prepend each BRASL with a nop.
  const bool emit_target_to_pool = (tocOffset == -2) && !code_section()->scratch_emit();
  const bool emit_relative_call  = !emit_target_to_pool &&
                                   RelAddr::is_in_range_of_RelAddr32(dist) &&
                                   ReoptimizeCallSequences &&
                                   !code_section()->scratch_emit();

  if (emit_relative_call) {
    // Add padding to get the same size as below.
    const unsigned int padding = call_far_patchable_size() - call_far_pcrelative_size();
    unsigned int current_padding;
    for (current_padding = 0; current_padding < padding; current_padding += nop_size()) { z_nop(); }
    assert(current_padding == padding, "sanity");

    // relative call: len = 2(nop) + 6 (brasl)
    // CodeBlob resize cannot occur in this case because
    // this call is emitted into pre-existing space.
    z_nop(); // Prepend each BRASL with a nop.
    z_brasl(Z_R14, target);
  } else {
    // absolute call: Get address from TOC.
    // len = (load TOC){6|0} + (load from TOC){6} + (basr){2} = {14|8}
    if (emit_target_to_pool) {
      // When emitting the call for the first time, we do not need to use
      // the pc-relative version. It will be patched anyway, when the code
      // buffer is copied.
      // Relocation is not needed when !ReoptimizeCallSequences.
      relocInfo::relocType rt = ReoptimizeCallSequences ? relocInfo::runtime_call_w_cp_type : relocInfo::none;
      AddressLiteral dest(target, rt);
      // Store_oop_in_toc() adds dest to the constant table. As side effect, this kills
      // inst_mark(). Reset if possible.
      bool reset_mark = (inst_mark() == pc());
      tocOffset = store_oop_in_toc(dest);
      if (reset_mark) { set_inst_mark(); }
      if (tocOffset == -1) {
        return false; // Couldn't create constant pool entry.
      }
    }
    assert(offset() == start_off, "emit no code before this point!");

    address tocPos = pc() + tocOffset;
    if (emit_target_to_pool) {
      tocPos = code()->consts()->start() + tocOffset;
    }
    load_long_pcrelative(Z_R14, tocPos);
    z_basr(Z_R14, Z_R14);
  }

#ifdef ASSERT
  // Assert that we can identify the emitted call.
  assert(is_call_far_patchable_at(addr_at(start_off)), "can't identify emitted call");
  assert(offset() == start_off+call_far_patchable_size(), "wrong size");

  if (emit_target_to_pool) {
    assert(get_dest_of_call_far_patchable_at(addr_at(start_off), code()->consts()->start()) == target,
           "wrong encoding of dest address");
  }
#endif
  return true; // success
}

// Identify a call_far_patchable instruction.
// For more detailed information see header comment of call_far_patchable.
bool MacroAssembler::is_call_far_patchable_at(address instruction_addr) {
  return is_call_far_patchable_variant2_at(instruction_addr)  || // short version: BRASL
         is_call_far_patchable_variant0_at(instruction_addr);    // long version LARL + LG + BASR
}

// Does the call_far_patchable instruction use a pc-relative encoding
// of the call destination?
bool MacroAssembler::is_call_far_patchable_pcrelative_at(address instruction_addr) {
  // Variant 2 is pc-relative.
  return is_call_far_patchable_variant2_at(instruction_addr);
}

bool MacroAssembler::is_call_far_pcrelative(address instruction_addr) {
  // Prepend each BRASL with a nop.
  return is_z_nop(instruction_addr) && is_z_brasl(instruction_addr + nop_size());  // Match at position after one nop required.
}

// Set destination address of a call_far_patchable instruction.
void MacroAssembler::set_dest_of_call_far_patchable_at(address instruction_addr, address dest, int64_t tocOffset) {
  ResourceMark rm;

  // Now that CP entry is verified, patch call to a pc-relative call (if circumstances permit).
  int code_size = MacroAssembler::call_far_patchable_size();
  CodeBuffer buf(instruction_addr, code_size);
  MacroAssembler masm(&buf);
  masm.call_far_patchable(dest, tocOffset);
  ICache::invalidate_range(instruction_addr, code_size); // Empty on z.
}

// Get dest address of a call_far_patchable instruction.
address MacroAssembler::get_dest_of_call_far_patchable_at(address instruction_addr, address ctable) {
  // Dynamic TOC: absolute address in constant pool.
  // Check variant2 first, it is more frequent.

  // Relative address encoded in call instruction.
  if (is_call_far_patchable_variant2_at(instruction_addr)) {
    return MacroAssembler::get_target_addr_pcrel(instruction_addr + nop_size()); // Prepend each BRASL with a nop.

  // Absolute address in constant pool.
  } else if (is_call_far_patchable_variant0_at(instruction_addr)) {
    address iaddr = instruction_addr;

    long    tocOffset = get_load_const_from_toc_offset(iaddr);
    address tocLoc    = iaddr + tocOffset;
    return *(address *)(tocLoc);
  } else {
    fprintf(stderr, "MacroAssembler::get_dest_of_call_far_patchable_at has a problem at %p:\n", instruction_addr);
    fprintf(stderr, "not a call_far_patchable: %16.16lx %16.16lx, len = %d\n",
            *(unsigned long*)instruction_addr,
            *(unsigned long*)(instruction_addr+8),
            call_far_patchable_size());
    Disassembler::decode(instruction_addr, instruction_addr+call_far_patchable_size());
    ShouldNotReachHere();
    return NULL;
  }
}

void MacroAssembler::align_call_far_patchable(address pc) {
  if (call_far_patchable_requires_alignment_nop(pc)) { z_nop(); }
}

void MacroAssembler::check_and_handle_earlyret(Register java_thread) {
}

void MacroAssembler::check_and_handle_popframe(Register java_thread) {
}

// Read from the polling page.
// Use TM or TMY instruction, depending on read offset.
//   offset = 0: Use TM, safepoint polling.
//   offset < 0: Use TMY, profiling safepoint polling.
void MacroAssembler::load_from_polling_page(Register polling_page_address, int64_t offset) {
  if (Immediate::is_uimm12(offset)) {
    z_tm(offset, polling_page_address, mask_safepoint);
  } else {
    z_tmy(offset, polling_page_address, mask_profiling);
  }
}

// Check whether z_instruction is a read access to the polling page
// which was emitted by load_from_polling_page(..).
bool MacroAssembler::is_load_from_polling_page(address instr_loc) {
  unsigned long z_instruction;
  unsigned int  ilen = get_instruction(instr_loc, &z_instruction);

  if (ilen == 2) { return false; } // It's none of the allowed instructions.

  if (ilen == 4) {
    if (!is_z_tm(z_instruction)) { return false; } // It's len=4, but not a z_tm. fail.

    int ms = inv_mask(z_instruction,8,32);  // mask
    int ra = inv_reg(z_instruction,16,32);  // base register
    int ds = inv_uimm12(z_instruction);     // displacement

    if (!(ds == 0 && ra != 0 && ms == mask_safepoint)) {
      return false; // It's not a z_tm(0, ra, mask_safepoint). Fail.
    }

  } else { /* if (ilen == 6) */

    assert(!is_z_lg(z_instruction), "old form (LG) polling page access. Please fix and use TM(Y).");

    if (!is_z_tmy(z_instruction)) { return false; } // It's len=6, but not a z_tmy. fail.

    int ms = inv_mask(z_instruction,8,48);  // mask
    int ra = inv_reg(z_instruction,16,48);  // base register
    int ds = inv_simm20(z_instruction);     // displacement
  }

  return true;
}

// Extract poll address from instruction and ucontext.
address MacroAssembler::get_poll_address(address instr_loc, void* ucontext) {
  assert(ucontext != NULL, "must have ucontext");
  ucontext_t* uc = (ucontext_t*) ucontext;
  unsigned long z_instruction;
  unsigned int ilen = get_instruction(instr_loc, &z_instruction);

  if (ilen == 4 && is_z_tm(z_instruction)) {
    int ra = inv_reg(z_instruction, 16, 32);  // base register
    int ds = inv_uimm12(z_instruction);       // displacement
    address addr = (address)uc->uc_mcontext.gregs[ra];
    return addr + ds;
  } else if (ilen == 6 && is_z_tmy(z_instruction)) {
    int ra = inv_reg(z_instruction, 16, 48);  // base register
    int ds = inv_simm20(z_instruction);       // displacement
    address addr = (address)uc->uc_mcontext.gregs[ra];
    return addr + ds;
  }

  ShouldNotReachHere();
  return NULL;
}

// Extract poll register from instruction.
uint MacroAssembler::get_poll_register(address instr_loc) {
  unsigned long z_instruction;
  unsigned int ilen = get_instruction(instr_loc, &z_instruction);

  if (ilen == 4 && is_z_tm(z_instruction)) {
    return (uint)inv_reg(z_instruction, 16, 32);  // base register
  } else if (ilen == 6 && is_z_tmy(z_instruction)) {
    return (uint)inv_reg(z_instruction, 16, 48);  // base register
  }

  ShouldNotReachHere();
  return 0;
}

bool MacroAssembler::is_memory_serialization(int instruction, JavaThread* thread, void* ucontext) {
  ShouldNotCallThis();
  return false;
}

// Write serialization page so VM thread can do a pseudo remote membar
// We use the current thread pointer to calculate a thread specific
// offset to write to within the page. This minimizes bus traffic
// due to cache line collision.
void MacroAssembler::serialize_memory(Register thread, Register tmp1, Register tmp2) {
  assert_different_registers(tmp1, tmp2);
  z_sllg(tmp2, thread, os::get_serialize_page_shift_count());
  load_const_optimized(tmp1, (long) os::get_memory_serialize_page());

  int mask = os::get_serialize_page_mask();
  if (Immediate::is_uimm16(mask)) {
    z_nill(tmp2, mask);
    z_llghr(tmp2, tmp2);
  } else {
    z_nilf(tmp2, mask);
    z_llgfr(tmp2, tmp2);
  }

  z_release();
  z_st(Z_R0, 0, tmp2, tmp1);
}

// Don't rely on register locking, always use Z_R1 as scratch register instead.
void MacroAssembler::bang_stack_with_offset(int offset) {
  // Stack grows down, caller passes positive offset.
  assert(offset > 0, "must bang with positive offset");
  if (Displacement::is_validDisp(-offset)) {
    z_tmy(-offset, Z_SP, mask_stackbang);
  } else {
    add2reg(Z_R1, -offset, Z_SP);    // Do not destroy Z_SP!!!
    z_tm(0, Z_R1, mask_stackbang);  // Just banging.
  }
}

void MacroAssembler::reserved_stack_check(Register return_pc) {
  // Test if reserved zone needs to be enabled.
  Label no_reserved_zone_enabling;
  assert(return_pc == Z_R14, "Return pc must be in R14 before z_br() to StackOverflow stub.");
  BLOCK_COMMENT("reserved_stack_check {");

  z_clg(Z_SP, Address(Z_thread, JavaThread::reserved_stack_activation_offset()));
  z_brl(no_reserved_zone_enabling);

  // Enable reserved zone again, throw stack overflow exception.
  save_return_pc();
  push_frame_abi160(0);
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::enable_stack_reserved_zone), Z_thread);
  pop_frame();
  restore_return_pc();

  load_const_optimized(Z_R1, StubRoutines::throw_delayed_StackOverflowError_entry());
  // Don't use call() or z_basr(), they will invalidate Z_R14 which contains the return pc.
  z_br(Z_R1);

  should_not_reach_here();

  bind(no_reserved_zone_enabling);
  BLOCK_COMMENT("} reserved_stack_check");
}

// Defines obj, preserves var_size_in_bytes, okay for t2 == var_size_in_bytes.
void MacroAssembler::tlab_allocate(Register obj,
                                   Register var_size_in_bytes,
                                   int con_size_in_bytes,
                                   Register t1,
                                   Label& slow_case) {
  assert_different_registers(obj, var_size_in_bytes, t1);
  Register end = t1;
  Register thread = Z_thread;

  z_lg(obj, Address(thread, JavaThread::tlab_top_offset()));
  if (var_size_in_bytes == noreg) {
    z_lay(end, Address(obj, con_size_in_bytes));
  } else {
    z_lay(end, Address(obj, var_size_in_bytes));
  }
  z_cg(end, Address(thread, JavaThread::tlab_end_offset()));
  branch_optimized(bcondHigh, slow_case);

  // Update the tlab top pointer.
  z_stg(end, Address(thread, JavaThread::tlab_top_offset()));

  // Recover var_size_in_bytes if necessary.
  if (var_size_in_bytes == end) {
    z_sgr(var_size_in_bytes, obj);
  }
}

// Emitter for interface method lookup.
//   input: recv_klass, intf_klass, itable_index
//   output: method_result
//   kills: itable_index, temp1_reg, Z_R0, Z_R1
// TODO: Temp2_reg is unused. we may use this emitter also in the itable stubs.
// If the register is still not needed then, remove it.
void MacroAssembler::lookup_interface_method(Register           recv_klass,
                                             Register           intf_klass,
                                             RegisterOrConstant itable_index,
                                             Register           method_result,
                                             Register           temp1_reg,
                                             Register           temp2_reg,
                                             Label&             no_such_interface) {

  const Register vtable_len = temp1_reg;    // Used to compute itable_entry_addr.
  const Register itable_entry_addr = Z_R1_scratch;
  const Register itable_interface = Z_R0_scratch;

  BLOCK_COMMENT("lookup_interface_method {");

  // Load start of itable entries into itable_entry_addr.
  z_llgf(vtable_len, Address(recv_klass, InstanceKlass::vtable_length_offset()));
  z_sllg(vtable_len, vtable_len, exact_log2(vtableEntry::size_in_bytes()));

  // Loop over all itable entries until desired interfaceOop(Rinterface) found.
  const int vtable_base_offset = in_bytes(InstanceKlass::vtable_start_offset());

  add2reg_with_index(itable_entry_addr,
                     vtable_base_offset + itableOffsetEntry::interface_offset_in_bytes(),
                     recv_klass, vtable_len);

  const int itable_offset_search_inc = itableOffsetEntry::size() * wordSize;
  Label     search;

  bind(search);

  // Handle IncompatibleClassChangeError.
  // If the entry is NULL then we've reached the end of the table
  // without finding the expected interface, so throw an exception.
  load_and_test_long(itable_interface, Address(itable_entry_addr));
  z_bre(no_such_interface);

  add2reg(itable_entry_addr, itable_offset_search_inc);
  z_cgr(itable_interface, intf_klass);
  z_brne(search);

  // Entry found and itable_entry_addr points to it, get offset of vtable for interface.

  const int vtable_offset_offset = (itableOffsetEntry::offset_offset_in_bytes() -
                                    itableOffsetEntry::interface_offset_in_bytes()) -
                                   itable_offset_search_inc;

  // Compute itableMethodEntry and get method and entry point
  // we use addressing with index and displacement, since the formula
  // for computing the entry's offset has a fixed and a dynamic part,
  // the latter depending on the matched interface entry and on the case,
  // that the itable index has been passed as a register, not a constant value.
  int method_offset = itableMethodEntry::method_offset_in_bytes();
                           // Fixed part (displacement), common operand.
  Register itable_offset;  // Dynamic part (index register).

  if (itable_index.is_register()) {
     // Compute the method's offset in that register, for the formula, see the
     // else-clause below.
     itable_offset = itable_index.as_register();

     z_sllg(itable_offset, itable_offset, exact_log2(itableMethodEntry::size() * wordSize));
     z_agf(itable_offset, vtable_offset_offset, itable_entry_addr);
  } else {
    itable_offset = Z_R1_scratch;
    // Displacement increases.
    method_offset += itableMethodEntry::size() * wordSize * itable_index.as_constant();

    // Load index from itable.
    z_llgf(itable_offset, vtable_offset_offset, itable_entry_addr);
  }

  // Finally load the method's oop.
  z_lg(method_result, method_offset, itable_offset, recv_klass);
  BLOCK_COMMENT("} lookup_interface_method");
}

// Lookup for virtual method invocation.
void MacroAssembler::lookup_virtual_method(Register           recv_klass,
                                           RegisterOrConstant vtable_index,
                                           Register           method_result) {
  assert_different_registers(recv_klass, vtable_index.register_or_noreg());
  assert(vtableEntry::size() * wordSize == wordSize,
         "else adjust the scaling in the code below");

  BLOCK_COMMENT("lookup_virtual_method {");

  const int base = in_bytes(Klass::vtable_start_offset());

  if (vtable_index.is_constant()) {
    // Load with base + disp.
    Address vtable_entry_addr(recv_klass,
                              vtable_index.as_constant() * wordSize +
                              base +
                              vtableEntry::method_offset_in_bytes());

    z_lg(method_result, vtable_entry_addr);
  } else {
    // Shift index properly and load with base + index + disp.
    Register vindex = vtable_index.as_register();
    Address  vtable_entry_addr(recv_klass, vindex,
                               base + vtableEntry::method_offset_in_bytes());

    z_sllg(vindex, vindex, exact_log2(wordSize));
    z_lg(method_result, vtable_entry_addr);
  }
  BLOCK_COMMENT("} lookup_virtual_method");
}

// Factor out code to call ic_miss_handler.
// Generate code to call the inline cache miss handler.
//
// In most cases, this code will be generated out-of-line.
// The method parameters are intended to provide some variability.
//   ICM          - Label which has to be bound to the start of useful code (past any traps).
//   trapMarker   - Marking byte for the generated illtrap instructions (if any).
//                  Any value except 0x00 is supported.
//                  = 0x00 - do not generate illtrap instructions.
//                         use nops to fill ununsed space.
//   requiredSize - required size of the generated code. If the actually
//                  generated code is smaller, use padding instructions to fill up.
//                  = 0 - no size requirement, no padding.
//   scratch      - scratch register to hold branch target address.
//
//  The method returns the code offset of the bound label.
unsigned int MacroAssembler::call_ic_miss_handler(Label& ICM, int trapMarker, int requiredSize, Register scratch) {
  intptr_t startOffset = offset();

  // Prevent entry at content_begin().
  if (trapMarker != 0) {
    z_illtrap(trapMarker);
  }

  // Load address of inline cache miss code into scratch register
  // and branch to cache miss handler.
  BLOCK_COMMENT("IC miss handler {");
  BIND(ICM);
  unsigned int   labelOffset = offset();
  AddressLiteral icmiss(SharedRuntime::get_ic_miss_stub());

  load_const_optimized(scratch, icmiss);
  z_br(scratch);

  // Fill unused space.
  if (requiredSize > 0) {
    while ((offset() - startOffset) < requiredSize) {
      if (trapMarker == 0) {
        z_nop();
      } else {
        z_illtrap(trapMarker);
      }
    }
  }
  BLOCK_COMMENT("} IC miss handler");
  return labelOffset;
}

void MacroAssembler::nmethod_UEP(Label& ic_miss) {
  Register ic_reg       = as_Register(Matcher::inline_cache_reg_encode());
  int      klass_offset = oopDesc::klass_offset_in_bytes();
  if (!ImplicitNullChecks || MacroAssembler::needs_explicit_null_check(klass_offset)) {
    if (VM_Version::has_CompareBranch()) {
      z_cgij(Z_ARG1, 0, Assembler::bcondEqual, ic_miss);
    } else {
      z_ltgr(Z_ARG1, Z_ARG1);
      z_bre(ic_miss);
    }
  }
  // Compare cached class against klass from receiver.
  compare_klass_ptr(ic_reg, klass_offset, Z_ARG1, false);
  z_brne(ic_miss);
}

void MacroAssembler::check_klass_subtype_fast_path(Register   sub_klass,
                                                   Register   super_klass,
                                                   Register   temp1_reg,
                                                   Label*     L_success,
                                                   Label*     L_failure,
                                                   Label*     L_slow_path,
                                                   RegisterOrConstant super_check_offset) {

  const int sc_offset  = in_bytes(Klass::secondary_super_cache_offset());
  const int sco_offset = in_bytes(Klass::super_check_offset_offset());

  bool must_load_sco = (super_check_offset.constant_or_zero() == -1);
  bool need_slow_path = (must_load_sco ||
                         super_check_offset.constant_or_zero() == sc_offset);

  // Input registers must not overlap.
  assert_different_registers(sub_klass, super_klass, temp1_reg);
  if (super_check_offset.is_register()) {
    assert_different_registers(sub_klass, super_klass,
                               super_check_offset.as_register());
  } else if (must_load_sco) {
    assert(temp1_reg != noreg, "supply either a temp or a register offset");
  }

  const Register Rsuper_check_offset = temp1_reg;

  NearLabel L_fallthrough;
  int label_nulls = 0;
  if (L_success == NULL)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == NULL)   { L_failure   = &L_fallthrough; label_nulls++; }
  if (L_slow_path == NULL) { L_slow_path = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1 ||
         (L_slow_path == &L_fallthrough && label_nulls <= 2 && !need_slow_path),
         "at most one NULL in the batch, usually");

  BLOCK_COMMENT("check_klass_subtype_fast_path {");
  // If the pointers are equal, we are done (e.g., String[] elements).
  // This self-check enables sharing of secondary supertype arrays among
  // non-primary types such as array-of-interface. Otherwise, each such
  // type would need its own customized SSA.
  // We move this check to the front of the fast path because many
  // type checks are in fact trivially successful in this manner,
  // so we get a nicely predicted branch right at the start of the check.
  compare64_and_branch(sub_klass, super_klass, bcondEqual, *L_success);

  // Check the supertype display, which is uint.
  if (must_load_sco) {
    z_llgf(Rsuper_check_offset, sco_offset, super_klass);
    super_check_offset = RegisterOrConstant(Rsuper_check_offset);
  }
  Address super_check_addr(sub_klass, super_check_offset, 0);
  z_cg(super_klass, super_check_addr); // compare w/ displayed supertype

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

  // Hacked jmp, which may only be used just before L_fallthrough.
#define final_jmp(label)                                                \
  if (&(label) == &L_fallthrough) { /*do nothing*/ }                    \
  else                            { branch_optimized(Assembler::bcondAlways, label); } /*omit semicolon*/

  if (super_check_offset.is_register()) {
    branch_optimized(Assembler::bcondEqual, *L_success);
    z_cfi(super_check_offset.as_register(), sc_offset);
    if (L_failure == &L_fallthrough) {
      branch_optimized(Assembler::bcondEqual, *L_slow_path);
    } else {
      branch_optimized(Assembler::bcondNotEqual, *L_failure);
      final_jmp(*L_slow_path);
    }
  } else if (super_check_offset.as_constant() == sc_offset) {
    // Need a slow path; fast failure is impossible.
    if (L_slow_path == &L_fallthrough) {
      branch_optimized(Assembler::bcondEqual, *L_success);
    } else {
      branch_optimized(Assembler::bcondNotEqual, *L_slow_path);
      final_jmp(*L_success);
    }
  } else {
    // No slow path; it's a fast decision.
    if (L_failure == &L_fallthrough) {
      branch_optimized(Assembler::bcondEqual, *L_success);
    } else {
      branch_optimized(Assembler::bcondNotEqual, *L_failure);
      final_jmp(*L_success);
    }
  }

  bind(L_fallthrough);
#undef local_brc
#undef final_jmp
  BLOCK_COMMENT("} check_klass_subtype_fast_path");
  // fallthru (to slow path)
}

void MacroAssembler::check_klass_subtype_slow_path(Register Rsubklass,
                                                   Register Rsuperklass,
                                                   Register Rarray_ptr,  // tmp
                                                   Register Rlength,     // tmp
                                                   Label* L_success,
                                                   Label* L_failure) {
  // Input registers must not overlap.
  // Also check for R1 which is explicitely used here.
  assert_different_registers(Z_R1, Rsubklass, Rsuperklass, Rarray_ptr, Rlength);
  NearLabel L_fallthrough, L_loop;
  int label_nulls = 0;
  if (L_success == NULL) { L_success = &L_fallthrough; label_nulls++; }
  if (L_failure == NULL) { L_failure = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one NULL in the batch");

  const int ss_offset = in_bytes(Klass::secondary_supers_offset());
  const int sc_offset = in_bytes(Klass::secondary_super_cache_offset());

  const int length_offset = Array<Klass*>::length_offset_in_bytes();
  const int base_offset   = Array<Klass*>::base_offset_in_bytes();

  // Hacked jmp, which may only be used just before L_fallthrough.
#define final_jmp(label)                                                \
  if (&(label) == &L_fallthrough) { /*do nothing*/ }                    \
  else                            branch_optimized(Assembler::bcondAlways, label) /*omit semicolon*/

  NearLabel loop_iterate, loop_count, match;

  BLOCK_COMMENT("check_klass_subtype_slow_path {");
  z_lg(Rarray_ptr, ss_offset, Rsubklass);

  load_and_test_int(Rlength, Address(Rarray_ptr, length_offset));
  branch_optimized(Assembler::bcondZero, *L_failure);

  // Oops in table are NO MORE compressed.
  z_cg(Rsuperklass, base_offset, Rarray_ptr); // Check array element for match.
  z_bre(match);                               // Shortcut for array length = 1.

  // No match yet, so we must walk the array's elements.
  z_lngfr(Rlength, Rlength);
  z_sllg(Rlength, Rlength, LogBytesPerWord); // -#bytes of cache array
  z_llill(Z_R1, BytesPerWord);               // Set increment/end index.
  add2reg(Rlength, 2 * BytesPerWord);        // start index  = -(n-2)*BytesPerWord
  z_slgr(Rarray_ptr, Rlength);               // start addr: +=  (n-2)*BytesPerWord
  z_bru(loop_count);

  BIND(loop_iterate);
  z_cg(Rsuperklass, base_offset, Rlength, Rarray_ptr); // Check array element for match.
  z_bre(match);
  BIND(loop_count);
  z_brxlg(Rlength, Z_R1, loop_iterate);

  // Rsuperklass not found among secondary super classes -> failure.
  branch_optimized(Assembler::bcondAlways, *L_failure);

  // Got a hit. Return success (zero result). Set cache.
  // Cache load doesn't happen here. For speed it is directly emitted by the compiler.

  BIND(match);

  z_stg(Rsuperklass, sc_offset, Rsubklass); // Save result to cache.

  final_jmp(*L_success);

  // Exit to the surrounding code.
  BIND(L_fallthrough);
#undef local_brc
#undef final_jmp
  BLOCK_COMMENT("} check_klass_subtype_slow_path");
}

// Emitter for combining fast and slow path.
void MacroAssembler::check_klass_subtype(Register sub_klass,
                                         Register super_klass,
                                         Register temp1_reg,
                                         Register temp2_reg,
                                         Label&   L_success) {
  NearLabel failure;
  BLOCK_COMMENT(err_msg("check_klass_subtype(%s subclass of %s) {", sub_klass->name(), super_klass->name()));
  check_klass_subtype_fast_path(sub_klass, super_klass, temp1_reg,
                                &L_success, &failure, NULL);
  check_klass_subtype_slow_path(sub_klass, super_klass,
                                temp1_reg, temp2_reg, &L_success, NULL);
  BIND(failure);
  BLOCK_COMMENT("} check_klass_subtype");
}

// Increment a counter at counter_address when the eq condition code is
// set. Kills registers tmp1_reg and tmp2_reg and preserves the condition code.
void MacroAssembler::increment_counter_eq(address counter_address, Register tmp1_reg, Register tmp2_reg) {
  Label l;
  z_brne(l);
  load_const(tmp1_reg, counter_address);
  add2mem_32(Address(tmp1_reg), 1, tmp2_reg);
  z_cr(tmp1_reg, tmp1_reg); // Set cc to eq.
  bind(l);
}

// Semantics are dependent on the slow_case label:
//   If the slow_case label is not NULL, failure to biased-lock the object
//   transfers control to the location of the slow_case label. If the
//   object could be biased-locked, control is transferred to the done label.
//   The condition code is unpredictable.
//
//   If the slow_case label is NULL, failure to biased-lock the object results
//   in a transfer of control to the done label with a condition code of not_equal.
//   If the biased-lock could be successfully obtained, control is transfered to
//   the done label with a condition code of equal.
//   It is mandatory to react on the condition code At the done label.
//
void MacroAssembler::biased_locking_enter(Register  obj_reg,
                                          Register  mark_reg,
                                          Register  temp_reg,
                                          Register  temp2_reg,    // May be Z_RO!
                                          Label    &done,
                                          Label    *slow_case) {
  assert(UseBiasedLocking, "why call this otherwise?");
  assert_different_registers(obj_reg, mark_reg, temp_reg, temp2_reg);

  Label cas_label; // Try, if implemented, CAS locking. Fall thru to slow path otherwise.

  BLOCK_COMMENT("biased_locking_enter {");

  // Biased locking
  // See whether the lock is currently biased toward our thread and
  // whether the epoch is still valid.
  // Note that the runtime guarantees sufficient alignment of JavaThread
  // pointers to allow age to be placed into low bits.
  assert(markOopDesc::age_shift == markOopDesc::lock_bits + markOopDesc::biased_lock_bits,
         "biased locking makes assumptions about bit layout");
  z_lr(temp_reg, mark_reg);
  z_nilf(temp_reg, markOopDesc::biased_lock_mask_in_place);
  z_chi(temp_reg, markOopDesc::biased_lock_pattern);
  z_brne(cas_label);  // Try cas if object is not biased, i.e. cannot be biased locked.

  load_prototype_header(temp_reg, obj_reg);
  load_const_optimized(temp2_reg, ~((int) markOopDesc::age_mask_in_place));

  z_ogr(temp_reg, Z_thread);
  z_xgr(temp_reg, mark_reg);
  z_ngr(temp_reg, temp2_reg);
  if (PrintBiasedLockingStatistics) {
    increment_counter_eq((address) BiasedLocking::biased_lock_entry_count_addr(), mark_reg, temp2_reg);
    // Restore mark_reg.
    z_lg(mark_reg, oopDesc::mark_offset_in_bytes(), obj_reg);
  }
  branch_optimized(Assembler::bcondEqual, done);  // Biased lock obtained, return success.

  Label try_revoke_bias;
  Label try_rebias;
  Address mark_addr = Address(obj_reg, oopDesc::mark_offset_in_bytes());

  //----------------------------------------------------------------------------
  // At this point we know that the header has the bias pattern and
  // that we are not the bias owner in the current epoch. We need to
  // figure out more details about the state of the header in order to
  // know what operations can be legally performed on the object's
  // header.

  // If the low three bits in the xor result aren't clear, that means
  // the prototype header is no longer biased and we have to revoke
  // the bias on this object.
  z_tmll(temp_reg, markOopDesc::biased_lock_mask_in_place);
  z_brnaz(try_revoke_bias);

  // Biasing is still enabled for this data type. See whether the
  // epoch of the current bias is still valid, meaning that the epoch
  // bits of the mark word are equal to the epoch bits of the
  // prototype header. (Note that the prototype header's epoch bits
  // only change at a safepoint.) If not, attempt to rebias the object
  // toward the current thread. Note that we must be absolutely sure
  // that the current epoch is invalid in order to do this because
  // otherwise the manipulations it performs on the mark word are
  // illegal.
  z_tmll(temp_reg, markOopDesc::epoch_mask_in_place);
  z_brnaz(try_rebias);

  //----------------------------------------------------------------------------
  // The epoch of the current bias is still valid but we know nothing
  // about the owner; it might be set or it might be clear. Try to
  // acquire the bias of the object using an atomic operation. If this
  // fails we will go in to the runtime to revoke the object's bias.
  // Note that we first construct the presumed unbiased header so we
  // don't accidentally blow away another thread's valid bias.
  z_nilf(mark_reg, markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place |
         markOopDesc::epoch_mask_in_place);
  z_lgr(temp_reg, Z_thread);
  z_llgfr(mark_reg, mark_reg);
  z_ogr(temp_reg, mark_reg);

  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  z_csg(mark_reg, temp_reg, 0, obj_reg);

  // If the biasing toward our thread failed, this means that
  // another thread succeeded in biasing it toward itself and we
  // need to revoke that bias. The revocation will occur in the
  // interpreter runtime in the slow case.

  if (PrintBiasedLockingStatistics) {
    increment_counter_eq((address) BiasedLocking::anonymously_biased_lock_entry_count_addr(),
                         temp_reg, temp2_reg);
  }
  if (slow_case != NULL) {
    branch_optimized(Assembler::bcondNotEqual, *slow_case); // Biased lock not obtained, need to go the long way.
  }
  branch_optimized(Assembler::bcondAlways, done);           // Biased lock status given in condition code.

  //----------------------------------------------------------------------------
  bind(try_rebias);
  // At this point we know the epoch has expired, meaning that the
  // current "bias owner", if any, is actually invalid. Under these
  // circumstances _only_, we are allowed to use the current header's
  // value as the comparison value when doing the cas to acquire the
  // bias in the current epoch. In other words, we allow transfer of
  // the bias from one thread to another directly in this situation.

  z_nilf(mark_reg, markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place | markOopDesc::epoch_mask_in_place);
  load_prototype_header(temp_reg, obj_reg);
  z_llgfr(mark_reg, mark_reg);

  z_ogr(temp_reg, Z_thread);

  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  z_csg(mark_reg, temp_reg, 0, obj_reg);

  // If the biasing toward our thread failed, this means that
  // another thread succeeded in biasing it toward itself and we
  // need to revoke that bias. The revocation will occur in the
  // interpreter runtime in the slow case.

  if (PrintBiasedLockingStatistics) {
    increment_counter_eq((address) BiasedLocking::rebiased_lock_entry_count_addr(), temp_reg, temp2_reg);
  }
  if (slow_case != NULL) {
    branch_optimized(Assembler::bcondNotEqual, *slow_case);  // Biased lock not obtained, need to go the long way.
  }
  z_bru(done);           // Biased lock status given in condition code.

  //----------------------------------------------------------------------------
  bind(try_revoke_bias);
  // The prototype mark in the klass doesn't have the bias bit set any
  // more, indicating that objects of this data type are not supposed
  // to be biased any more. We are going to try to reset the mark of
  // this object to the prototype value and fall through to the
  // CAS-based locking scheme. Note that if our CAS fails, it means
  // that another thread raced us for the privilege of revoking the
  // bias of this particular object, so it's okay to continue in the
  // normal locking code.
  load_prototype_header(temp_reg, obj_reg);

  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  z_csg(mark_reg, temp_reg, 0, obj_reg);

  // Fall through to the normal CAS-based lock, because no matter what
  // the result of the above CAS, some thread must have succeeded in
  // removing the bias bit from the object's header.
  if (PrintBiasedLockingStatistics) {
    // z_cgr(mark_reg, temp2_reg);
    increment_counter_eq((address) BiasedLocking::revoked_lock_entry_count_addr(), temp_reg, temp2_reg);
  }

  bind(cas_label);
  BLOCK_COMMENT("} biased_locking_enter");
}

void MacroAssembler::biased_locking_exit(Register mark_addr, Register temp_reg, Label& done) {
  // Check for biased locking unlock case, which is a no-op
  // Note: we do not have to check the thread ID for two reasons.
  // First, the interpreter checks for IllegalMonitorStateException at
  // a higher level. Second, if the bias was revoked while we held the
  // lock, the object could not be rebiased toward another thread, so
  // the bias bit would be clear.
  BLOCK_COMMENT("biased_locking_exit {");

  z_lg(temp_reg, 0, mark_addr);
  z_nilf(temp_reg, markOopDesc::biased_lock_mask_in_place);

  z_chi(temp_reg, markOopDesc::biased_lock_pattern);
  z_bre(done);
  BLOCK_COMMENT("} biased_locking_exit");
}

void MacroAssembler::compiler_fast_lock_object(Register oop, Register box, Register temp1, Register temp2, bool try_bias) {
  Register displacedHeader = temp1;
  Register currentHeader = temp1;
  Register temp = temp2;
  NearLabel done, object_has_monitor;

  BLOCK_COMMENT("compiler_fast_lock_object {");

  // Load markOop from oop into mark.
  z_lg(displacedHeader, 0, oop);

  if (try_bias) {
    biased_locking_enter(oop, displacedHeader, temp, Z_R0, done);
  }

  // Handle existing monitor.
  if ((EmitSync & 0x01) == 0) {
    // The object has an existing monitor iff (mark & monitor_value) != 0.
    guarantee(Immediate::is_uimm16(markOopDesc::monitor_value), "must be half-word");
    z_lr(temp, displacedHeader);
    z_nill(temp, markOopDesc::monitor_value);
    z_brne(object_has_monitor);
  }

  // Set mark to markOop | markOopDesc::unlocked_value.
  z_oill(displacedHeader, markOopDesc::unlocked_value);

  // Load Compare Value application register.

  // Initialize the box (must happen before we update the object mark).
  z_stg(displacedHeader, BasicLock::displaced_header_offset_in_bytes(), box);

  // Memory Fence (in cmpxchgd)
  // Compare object markOop with mark and if equal exchange scratch1 with object markOop.

  // If the compare-and-swap succeeded, then we found an unlocked object and we
  // have now locked it.
  z_csg(displacedHeader, box, 0, oop);
  assert(currentHeader==displacedHeader, "must be same register"); // Identified two registers from z/Architecture.
  z_bre(done);

  // We did not see an unlocked object so try the fast recursive case.

  z_sgr(currentHeader, Z_SP);
  load_const_optimized(temp, (~(os::vm_page_size()-1) | markOopDesc::lock_mask_in_place));

  z_ngr(currentHeader, temp);
  //   z_brne(done);
  //   z_release();
  z_stg(currentHeader/*==0 or not 0*/, BasicLock::displaced_header_offset_in_bytes(), box);

  z_bru(done);

  if ((EmitSync & 0x01) == 0) {
    Register zero = temp;
    Register monitor_tagged = displacedHeader; // Tagged with markOopDesc::monitor_value.
    bind(object_has_monitor);
    // The object's monitor m is unlocked iff m->owner == NULL,
    // otherwise m->owner may contain a thread or a stack address.
    //
    // Try to CAS m->owner from NULL to current thread.
    z_lghi(zero, 0);
    // If m->owner is null, then csg succeeds and sets m->owner=THREAD and CR=EQ.
    z_csg(zero, Z_thread, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner), monitor_tagged);
    // Store a non-null value into the box.
    z_stg(box, BasicLock::displaced_header_offset_in_bytes(), box);
#ifdef ASSERT
      z_brne(done);
      // We've acquired the monitor, check some invariants.
      // Invariant 1: _recursions should be 0.
      asm_assert_mem8_is_zero(OM_OFFSET_NO_MONITOR_VALUE_TAG(recursions), monitor_tagged,
                              "monitor->_recursions should be 0", -1);
      z_ltgr(zero, zero); // Set CR=EQ.
#endif
  }
  bind(done);

  BLOCK_COMMENT("} compiler_fast_lock_object");
  // If locking was successful, CR should indicate 'EQ'.
  // The compiler or the native wrapper generates a branch to the runtime call
  // _complete_monitor_locking_Java.
}

void MacroAssembler::compiler_fast_unlock_object(Register oop, Register box, Register temp1, Register temp2, bool try_bias) {
  Register displacedHeader = temp1;
  Register currentHeader = temp2;
  Register temp = temp1;
  Register monitor = temp2;

  Label done, object_has_monitor;

  BLOCK_COMMENT("compiler_fast_unlock_object {");

  if (try_bias) {
    biased_locking_exit(oop, currentHeader, done);
  }

  // Find the lock address and load the displaced header from the stack.
  // if the displaced header is zero, we have a recursive unlock.
  load_and_test_long(displacedHeader, Address(box, BasicLock::displaced_header_offset_in_bytes()));
  z_bre(done);

  // Handle existing monitor.
  if ((EmitSync & 0x02) == 0) {
    // The object has an existing monitor iff (mark & monitor_value) != 0.
    z_lg(currentHeader, oopDesc::mark_offset_in_bytes(), oop);
    guarantee(Immediate::is_uimm16(markOopDesc::monitor_value), "must be half-word");
    z_nill(currentHeader, markOopDesc::monitor_value);
    z_brne(object_has_monitor);
  }

  // Check if it is still a light weight lock, this is true if we see
  // the stack address of the basicLock in the markOop of the object
  // copy box to currentHeader such that csg does not kill it.
  z_lgr(currentHeader, box);
  z_csg(currentHeader, displacedHeader, 0, oop);
  z_bru(done); // Csg sets CR as desired.

  // Handle existing monitor.
  if ((EmitSync & 0x02) == 0) {
    bind(object_has_monitor);
    z_lg(currentHeader, oopDesc::mark_offset_in_bytes(), oop);    // CurrentHeader is tagged with monitor_value set.
    load_and_test_long(temp, Address(currentHeader, OM_OFFSET_NO_MONITOR_VALUE_TAG(recursions)));
    z_brne(done);
    load_and_test_long(temp, Address(currentHeader, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)));
    z_brne(done);
    load_and_test_long(temp, Address(currentHeader, OM_OFFSET_NO_MONITOR_VALUE_TAG(EntryList)));
    z_brne(done);
    load_and_test_long(temp, Address(currentHeader, OM_OFFSET_NO_MONITOR_VALUE_TAG(cxq)));
    z_brne(done);
    z_release();
    z_stg(temp/*=0*/, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner), currentHeader);
  }

  bind(done);

  BLOCK_COMMENT("} compiler_fast_unlock_object");
  // flag == EQ indicates success
  // flag == NE indicates failure
}

// Write to card table for modification at store_addr - register is destroyed afterwards.
void MacroAssembler::card_write_barrier_post(Register store_addr, Register tmp) {
  CardTableModRefBS* bs = (CardTableModRefBS*) Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::CardTableForRS ||
         bs->kind() == BarrierSet::CardTableExtension, "wrong barrier");
  assert_different_registers(store_addr, tmp);
  z_srlg(store_addr, store_addr, CardTableModRefBS::card_shift);
  load_absolute_address(tmp, (address)bs->byte_map_base);
  z_agr(store_addr, tmp);
  z_mvi(0, store_addr, 0); // Store byte 0.
}

void MacroAssembler::resolve_jobject(Register value, Register tmp1, Register tmp2) {
  NearLabel Ldone;
  z_ltgr(tmp1, value);
  z_bre(Ldone);          // Use NULL result as-is.

  z_nill(value, ~JNIHandles::weak_tag_mask);
  z_lg(value, 0, value); // Resolve (untagged) jobject.

#if INCLUDE_ALL_GCS
  if (UseG1GC) {
    NearLabel Lnot_weak;
    z_tmll(tmp1, JNIHandles::weak_tag_mask); // Test for jweak tag.
    z_braz(Lnot_weak);
    verify_oop(value);
    g1_write_barrier_pre(noreg /* obj */,
                         noreg /* offset */,
                         value /* pre_val */,
                         noreg /* val */,
                         tmp1  /* tmp1 */,
                         tmp2  /* tmp2 */,
                         true  /* pre_val_needed */);
    bind(Lnot_weak);
  }
#endif // INCLUDE_ALL_GCS
  verify_oop(value);
  bind(Ldone);
}

#if INCLUDE_ALL_GCS

//------------------------------------------------------
// General G1 pre-barrier generator.
// Purpose: record the previous value if it is not null.
// All non-tmps are preserved.
//------------------------------------------------------
void MacroAssembler::g1_write_barrier_pre(Register           Robj,
                                          RegisterOrConstant offset,
                                          Register           Rpre_val,      // Ideally, this is a non-volatile register.
                                          Register           Rval,          // Will be preserved.
                                          Register           Rtmp1,         // If Rpre_val is volatile, either Rtmp1
                                          Register           Rtmp2,         // or Rtmp2 has to be non-volatile..
                                          bool               pre_val_needed // Save Rpre_val across runtime call, caller uses it.
                                       ) {
  Label callRuntime, filtered;
  const int active_offset = in_bytes(JavaThread::satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_active());
  const int buffer_offset = in_bytes(JavaThread::satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_buf());
  const int index_offset  = in_bytes(JavaThread::satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_index());
  assert_different_registers(Rtmp1, Rtmp2, Z_R0_scratch); // None of the Rtmp<i> must be Z_R0!!

  BLOCK_COMMENT("g1_write_barrier_pre {");

  // Is marking active?
  // Note: value is loaded for test purposes only. No further use here.
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    load_and_test_int(Rtmp1, Address(Z_thread, active_offset));
  } else {
    guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
    load_and_test_byte(Rtmp1, Address(Z_thread, active_offset));
  }
  z_bre(filtered); // Activity indicator is zero, so there is no marking going on currently.

  // Do we need to load the previous value into Rpre_val?
  if (Robj != noreg) {
    // Load the previous value...
    Register ixReg = offset.is_register() ? offset.register_or_noreg() : Z_R0;
    if (UseCompressedOops) {
      z_llgf(Rpre_val, offset.constant_or_zero(), ixReg, Robj);
    } else {
      z_lg(Rpre_val, offset.constant_or_zero(), ixReg, Robj);
    }
  }
  assert(Rpre_val != noreg, "must have a real register");

  // Is the previous value NULL?
  // Note: pre_val is loaded, decompressed and stored (directly or via runtime call).
  //       Register contents is preserved across runtime call if caller requests to do so.
  z_ltgr(Rpre_val, Rpre_val);
  z_bre(filtered); // previous value is NULL, so we don't need to record it.

  // Decode the oop now. We know it's not NULL.
  if (Robj != noreg && UseCompressedOops) {
    oop_decoder(Rpre_val, Rpre_val, /*maybeNULL=*/false);
  }

  // OK, it's not filtered, so we'll need to call enqueue.

  // We can store the original value in the thread's buffer
  // only if index > 0. Otherwise, we need runtime to handle.
  // (The index field is typed as size_t.)
  Register Rbuffer = Rtmp1, Rindex = Rtmp2;

  z_lg(Rbuffer, buffer_offset, Z_thread);

  load_and_test_long(Rindex, Address(Z_thread, index_offset));
  z_bre(callRuntime); // If index == 0, goto runtime.

  add2reg(Rindex, -wordSize); // Decrement index.
  z_stg(Rindex, index_offset, Z_thread);

  // Record the previous value.
  z_stg(Rpre_val, 0, Rbuffer, Rindex);
  z_bru(filtered);  // We are done.

  Rbuffer = noreg;  // end of life
  Rindex  = noreg;  // end of life

  bind(callRuntime);

  // Save Rpre_val (result) over runtime call.
  // Requires Rtmp1, Rtmp2, or Rpre_val to be non-volatile.
  Register Rpre_save = Rpre_val;
  if (pre_val_needed && Rpre_val->is_volatile()) {
    guarantee(!Rtmp1->is_volatile() || !Rtmp2->is_volatile(), "oops!");
    Rpre_save = !Rtmp1->is_volatile() ? Rtmp1 : Rtmp2;
  }
  lgr_if_needed(Rpre_save, Rpre_val);

  // Preserve inputs by spilling them into the top frame.
  if (Robj != noreg && Robj->is_volatile()) {
    z_stg(Robj, Robj->encoding()*BytesPerWord, Z_SP);
  }
  if (offset.is_register() && offset.as_register()->is_volatile()) {
    Register Roff = offset.as_register();
    z_stg(Roff, Roff->encoding()*BytesPerWord, Z_SP);
  }
  if (Rval != noreg && Rval->is_volatile()) {
    z_stg(Rval, Rval->encoding()*BytesPerWord, Z_SP);
  }

  // Push frame to protect top frame with return pc and spilled register values.
  save_return_pc();
  push_frame_abi160(0); // Will use Z_R0 as tmp on old CPUs.

  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), Rpre_val, Z_thread);

  pop_frame();
  restore_return_pc();

  // Restore spilled values.
  if (Robj != noreg && Robj->is_volatile()) {
    z_lg(Robj, Robj->encoding()*BytesPerWord, Z_SP);
  }
  if (offset.is_register() && offset.as_register()->is_volatile()) {
    Register Roff = offset.as_register();
    z_lg(Roff, Roff->encoding()*BytesPerWord, Z_SP);
  }
  if (Rval != noreg && Rval->is_volatile()) {
    z_lg(Rval, Rval->encoding()*BytesPerWord, Z_SP);
  }

  // Restore Rpre_val (result) after runtime call.
  lgr_if_needed(Rpre_val, Rpre_save);

  bind(filtered);
  BLOCK_COMMENT("} g1_write_barrier_pre");
}

// General G1 post-barrier generator.
// Purpose: Store cross-region card.
void MacroAssembler::g1_write_barrier_post(Register Rstore_addr,
                                           Register Rnew_val,
                                           Register Rtmp1,
                                           Register Rtmp2,
                                           Register Rtmp3) {
  Label callRuntime, filtered;

  assert_different_registers(Rstore_addr, Rnew_val, Rtmp1, Rtmp2); // Most probably, Rnew_val == Rtmp3.

  G1SATBCardTableModRefBS* bs = (G1SATBCardTableModRefBS*) Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::G1SATBCTLogging, "wrong barrier");

  BLOCK_COMMENT("g1_write_barrier_post {");

  // Does store cross heap regions?
  // It does if the two addresses specify different grain addresses.
  if (G1RSBarrierRegionFilter) {
    if (VM_Version::has_DistinctOpnds()) {
      z_xgrk(Rtmp1, Rstore_addr, Rnew_val);
    } else {
      z_lgr(Rtmp1, Rstore_addr);
      z_xgr(Rtmp1, Rnew_val);
    }
    z_srag(Rtmp1, Rtmp1, HeapRegion::LogOfHRGrainBytes);
    z_bre(filtered);
  }

  // Crosses regions, storing NULL?
#ifdef ASSERT
  z_ltgr(Rnew_val, Rnew_val);
  asm_assert_ne("null oop not allowed (G1)", 0x255); // TODO: also on z? Checked by caller on PPC64, so following branch is obsolete:
  z_bre(filtered);  // Safety net: don't break if we have a NULL oop.
#endif
  Rnew_val = noreg; // end of lifetime

  // Storing region crossing non-NULL, is card already dirty?
  assert(sizeof(*bs->byte_map_base) == sizeof(jbyte), "adjust this code");
  assert_different_registers(Rtmp1, Rtmp2, Rtmp3);
  // Make sure not to use Z_R0 for any of these registers.
  Register Rcard_addr = (Rtmp1 != Z_R0_scratch) ? Rtmp1 : Rtmp3;
  Register Rbase      = (Rtmp2 != Z_R0_scratch) ? Rtmp2 : Rtmp3;

  // calculate address of card
  load_const_optimized(Rbase, (address)bs->byte_map_base);        // Card table base.
  z_srlg(Rcard_addr, Rstore_addr, CardTableModRefBS::card_shift); // Index into card table.
  add2reg_with_index(Rcard_addr, 0, Rcard_addr, Rbase);           // Explicit calculation needed for cli.
  Rbase = noreg; // end of lifetime

  // Filter young.
  assert((unsigned int)G1SATBCardTableModRefBS::g1_young_card_val() <= 255, "otherwise check this code");
  z_cli(0, Rcard_addr, (int)G1SATBCardTableModRefBS::g1_young_card_val());
  z_bre(filtered);

  // Check the card value. If dirty, we're done.
  // This also avoids false sharing of the (already dirty) card.
  z_sync(); // Required to support concurrent cleaning.
  assert((unsigned int)CardTableModRefBS::dirty_card_val() <= 255, "otherwise check this code");
  z_cli(0, Rcard_addr, CardTableModRefBS::dirty_card_val()); // Reload after membar.
  z_bre(filtered);

  // Storing a region crossing, non-NULL oop, card is clean.
  // Dirty card and log.
  z_mvi(0, Rcard_addr, CardTableModRefBS::dirty_card_val());

  Register Rcard_addr_x = Rcard_addr;
  Register Rqueue_index = (Rtmp2 != Z_R0_scratch) ? Rtmp2 : Rtmp1;
  Register Rqueue_buf   = (Rtmp3 != Z_R0_scratch) ? Rtmp3 : Rtmp1;
  const int qidx_off    = in_bytes(JavaThread::dirty_card_queue_offset() + SATBMarkQueue::byte_offset_of_index());
  const int qbuf_off    = in_bytes(JavaThread::dirty_card_queue_offset() + SATBMarkQueue::byte_offset_of_buf());
  if ((Rcard_addr == Rqueue_buf) || (Rcard_addr == Rqueue_index)) {
    Rcard_addr_x = Z_R0_scratch;  // Register shortage. We have to use Z_R0.
  }
  lgr_if_needed(Rcard_addr_x, Rcard_addr);

  load_and_test_long(Rqueue_index, Address(Z_thread, qidx_off));
  z_bre(callRuntime); // Index == 0 then jump to runtime.

  z_lg(Rqueue_buf, qbuf_off, Z_thread);

  add2reg(Rqueue_index, -wordSize); // Decrement index.
  z_stg(Rqueue_index, qidx_off, Z_thread);

  z_stg(Rcard_addr_x, 0, Rqueue_index, Rqueue_buf); // Store card.
  z_bru(filtered);

  bind(callRuntime);

  // TODO: do we need a frame? Introduced to be on the safe side.
  bool needs_frame = true;

  // VM call need frame to access(write) O register.
  if (needs_frame) {
    save_return_pc();
    push_frame_abi160(0); // Will use Z_R0 as tmp on old CPUs.
  }

  // Save the live input values.
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), Rcard_addr_x, Z_thread);

  if (needs_frame) {
    pop_frame();
    restore_return_pc();
  }

  bind(filtered);

  BLOCK_COMMENT("} g1_write_barrier_post");
}
#endif // INCLUDE_ALL_GCS

// Last_Java_sp must comply to the rules in frame_s390.hpp.
void MacroAssembler::set_last_Java_frame(Register last_Java_sp, Register last_Java_pc, bool allow_relocation) {
  BLOCK_COMMENT("set_last_Java_frame {");

  // Always set last_Java_pc and flags first because once last_Java_sp
  // is visible has_last_Java_frame is true and users will look at the
  // rest of the fields. (Note: flags should always be zero before we
  // get here so doesn't need to be set.)

  // Verify that last_Java_pc was zeroed on return to Java.
  if (allow_relocation) {
    asm_assert_mem8_is_zero(in_bytes(JavaThread::last_Java_pc_offset()),
                            Z_thread,
                            "last_Java_pc not zeroed before leaving Java",
                            0x200);
  } else {
    asm_assert_mem8_is_zero_static(in_bytes(JavaThread::last_Java_pc_offset()),
                                   Z_thread,
                                   "last_Java_pc not zeroed before leaving Java",
                                   0x200);
  }

  // When returning from calling out from Java mode the frame anchor's
  // last_Java_pc will always be set to NULL. It is set here so that
  // if we are doing a call to native (not VM) that we capture the
  // known pc and don't have to rely on the native call having a
  // standard frame linkage where we can find the pc.
  if (last_Java_pc!=noreg) {
    z_stg(last_Java_pc, Address(Z_thread, JavaThread::last_Java_pc_offset()));
  }

  // This membar release is not required on z/Architecture, since the sequence of stores
  // in maintained. Nevertheless, we leave it in to document the required ordering.
  // The implementation of z_release() should be empty.
  // z_release();

  z_stg(last_Java_sp, Address(Z_thread, JavaThread::last_Java_sp_offset()));
  BLOCK_COMMENT("} set_last_Java_frame");
}

void MacroAssembler::reset_last_Java_frame(bool allow_relocation) {
  BLOCK_COMMENT("reset_last_Java_frame {");

  if (allow_relocation) {
    asm_assert_mem8_isnot_zero(in_bytes(JavaThread::last_Java_sp_offset()),
                               Z_thread,
                               "SP was not set, still zero",
                               0x202);
  } else {
    asm_assert_mem8_isnot_zero_static(in_bytes(JavaThread::last_Java_sp_offset()),
                                      Z_thread,
                                      "SP was not set, still zero",
                                      0x202);
  }

  // _last_Java_sp = 0
  // Clearing storage must be atomic here, so don't use clear_mem()!
  store_const(Address(Z_thread, JavaThread::last_Java_sp_offset()), 0);

  // _last_Java_pc = 0
  store_const(Address(Z_thread, JavaThread::last_Java_pc_offset()), 0);

  BLOCK_COMMENT("} reset_last_Java_frame");
  return;
}

void MacroAssembler::set_top_ijava_frame_at_SP_as_last_Java_frame(Register sp, Register tmp1, bool allow_relocation) {
  assert_different_registers(sp, tmp1);

  // We cannot trust that code generated by the C++ compiler saves R14
  // to z_abi_160.return_pc, because sometimes it spills R14 using stmg at
  // z_abi_160.gpr14 (e.g. InterpreterRuntime::_new()).
  // Therefore we load the PC into tmp1 and let set_last_Java_frame() save
  // it into the frame anchor.
  get_PC(tmp1);
  set_last_Java_frame(/*sp=*/sp, /*pc=*/tmp1, allow_relocation);
}

void MacroAssembler::set_thread_state(JavaThreadState new_state) {
  z_release();

  assert(Immediate::is_uimm16(_thread_max_state), "enum value out of range for instruction");
  assert(sizeof(JavaThreadState) == sizeof(int), "enum value must have base type int");
  store_const(Address(Z_thread, JavaThread::thread_state_offset()), new_state, Z_R0, false);
}

void MacroAssembler::get_vm_result(Register oop_result) {
  verify_thread();

  z_lg(oop_result, Address(Z_thread, JavaThread::vm_result_offset()));
  clear_mem(Address(Z_thread, JavaThread::vm_result_offset()), sizeof(void*));

  verify_oop(oop_result);
}

void MacroAssembler::get_vm_result_2(Register result) {
  verify_thread();

  z_lg(result, Address(Z_thread, JavaThread::vm_result_2_offset()));
  clear_mem(Address(Z_thread, JavaThread::vm_result_2_offset()), sizeof(void*));
}

// We require that C code which does not return a value in vm_result will
// leave it undisturbed.
void MacroAssembler::set_vm_result(Register oop_result) {
  z_stg(oop_result, Address(Z_thread, JavaThread::vm_result_offset()));
}

// Explicit null checks (used for method handle code).
void MacroAssembler::null_check(Register reg, Register tmp, int64_t offset) {
  if (!ImplicitNullChecks) {
    NearLabel ok;

    compare64_and_branch(reg, (intptr_t) 0, Assembler::bcondNotEqual, ok);

    // We just put the address into reg if it was 0 (tmp==Z_R0 is allowed so we can't use it for the address).
    address exception_entry = Interpreter::throw_NullPointerException_entry();
    load_absolute_address(reg, exception_entry);
    z_br(reg);

    bind(ok);
  } else {
    if (needs_explicit_null_check((intptr_t)offset)) {
      // Provoke OS NULL exception if reg = NULL by
      // accessing M[reg] w/o changing any registers.
      z_lg(tmp, 0, reg);
    }
    // else
      // Nothing to do, (later) access of M[reg + offset]
      // will provoke OS NULL exception if reg = NULL.
  }
}

//-------------------------------------
//  Compressed Klass Pointers
//-------------------------------------

// Klass oop manipulations if compressed.
void MacroAssembler::encode_klass_not_null(Register dst, Register src) {
  Register current = (src != noreg) ? src : dst; // Klass is in dst if no src provided. (dst == src) also possible.
  address  base    = Universe::narrow_klass_base();
  int      shift   = Universe::narrow_klass_shift();
  assert(UseCompressedClassPointers, "only for compressed klass ptrs");

  BLOCK_COMMENT("cKlass encoder {");

#ifdef ASSERT
  Label ok;
  z_tmll(current, KlassAlignmentInBytes-1); // Check alignment.
  z_brc(Assembler::bcondAllZero, ok);
  // The plain disassembler does not recognize illtrap. It instead displays
  // a 32-bit value. Issueing two illtraps assures the disassembler finds
  // the proper beginning of the next instruction.
  z_illtrap(0xee);
  z_illtrap(0xee);
  bind(ok);
#endif

  if (base != NULL) {
    unsigned int base_h = ((unsigned long)base)>>32;
    unsigned int base_l = (unsigned int)((unsigned long)base);
    if ((base_h != 0) && (base_l == 0) && VM_Version::has_HighWordInstr()) {
      lgr_if_needed(dst, current);
      z_aih(dst, -((int)base_h));     // Base has no set bits in lower half.
    } else if ((base_h == 0) && (base_l != 0)) {
      lgr_if_needed(dst, current);
      z_agfi(dst, -(int)base_l);
    } else {
      load_const(Z_R0, base);
      lgr_if_needed(dst, current);
      z_sgr(dst, Z_R0);
    }
    current = dst;
  }
  if (shift != 0) {
    assert (LogKlassAlignmentInBytes == shift, "decode alg wrong");
    z_srlg(dst, current, shift);
    current = dst;
  }
  lgr_if_needed(dst, current); // Move may be required (if neither base nor shift != 0).

  BLOCK_COMMENT("} cKlass encoder");
}

// This function calculates the size of the code generated by
//   decode_klass_not_null(register dst, Register src)
// when (Universe::heap() != NULL). Hence, if the instructions
// it generates change, then this method needs to be updated.
int MacroAssembler::instr_size_for_decode_klass_not_null() {
  address  base    = Universe::narrow_klass_base();
  int shift_size   = Universe::narrow_klass_shift() == 0 ? 0 : 6; /* sllg */
  int addbase_size = 0;
  assert(UseCompressedClassPointers, "only for compressed klass ptrs");

  if (base != NULL) {
    unsigned int base_h = ((unsigned long)base)>>32;
    unsigned int base_l = (unsigned int)((unsigned long)base);
    if ((base_h != 0) && (base_l == 0) && VM_Version::has_HighWordInstr()) {
      addbase_size += 6; /* aih */
    } else if ((base_h == 0) && (base_l != 0)) {
      addbase_size += 6; /* algfi */
    } else {
      addbase_size += load_const_size();
      addbase_size += 4; /* algr */
    }
  }
#ifdef ASSERT
  addbase_size += 10;
  addbase_size += 2; // Extra sigill.
#endif
  return addbase_size + shift_size;
}

// !!! If the instructions that get generated here change
//     then function instr_size_for_decode_klass_not_null()
//     needs to get updated.
// This variant of decode_klass_not_null() must generate predictable code!
// The code must only depend on globally known parameters.
void MacroAssembler::decode_klass_not_null(Register dst) {
  address  base    = Universe::narrow_klass_base();
  int      shift   = Universe::narrow_klass_shift();
  int      beg_off = offset();
  assert(UseCompressedClassPointers, "only for compressed klass ptrs");

  BLOCK_COMMENT("cKlass decoder (const size) {");

  if (shift != 0) { // Shift required?
    z_sllg(dst, dst, shift);
  }
  if (base != NULL) {
    unsigned int base_h = ((unsigned long)base)>>32;
    unsigned int base_l = (unsigned int)((unsigned long)base);
    if ((base_h != 0) && (base_l == 0) && VM_Version::has_HighWordInstr()) {
      z_aih(dst, base_h);     // Base has no set bits in lower half.
    } else if ((base_h == 0) && (base_l != 0)) {
      z_algfi(dst, base_l);   // Base has no set bits in upper half.
    } else {
      load_const(Z_R0, base); // Base has set bits everywhere.
      z_algr(dst, Z_R0);
    }
  }

#ifdef ASSERT
  Label ok;
  z_tmll(dst, KlassAlignmentInBytes-1); // Check alignment.
  z_brc(Assembler::bcondAllZero, ok);
  // The plain disassembler does not recognize illtrap. It instead displays
  // a 32-bit value. Issueing two illtraps assures the disassembler finds
  // the proper beginning of the next instruction.
  z_illtrap(0xd1);
  z_illtrap(0xd1);
  bind(ok);
#endif
  assert(offset() == beg_off + instr_size_for_decode_klass_not_null(), "Code gen mismatch.");

  BLOCK_COMMENT("} cKlass decoder (const size)");
}

// This variant of decode_klass_not_null() is for cases where
//  1) the size of the generated instructions may vary
//  2) the result is (potentially) stored in a register different from the source.
void MacroAssembler::decode_klass_not_null(Register dst, Register src) {
  address base  = Universe::narrow_klass_base();
  int     shift = Universe::narrow_klass_shift();
  assert(UseCompressedClassPointers, "only for compressed klass ptrs");

  BLOCK_COMMENT("cKlass decoder {");

  if (src == noreg) src = dst;

  if (shift != 0) { // Shift or at least move required?
    z_sllg(dst, src, shift);
  } else {
    lgr_if_needed(dst, src);
  }

  if (base != NULL) {
    unsigned int base_h = ((unsigned long)base)>>32;
    unsigned int base_l = (unsigned int)((unsigned long)base);
    if ((base_h != 0) && (base_l == 0) && VM_Version::has_HighWordInstr()) {
      z_aih(dst, base_h);     // Base has not set bits in lower half.
    } else if ((base_h == 0) && (base_l != 0)) {
      z_algfi(dst, base_l);   // Base has no set bits in upper half.
    } else {
      load_const_optimized(Z_R0, base); // Base has set bits everywhere.
      z_algr(dst, Z_R0);
    }
  }

#ifdef ASSERT
  Label ok;
  z_tmll(dst, KlassAlignmentInBytes-1); // Check alignment.
  z_brc(Assembler::bcondAllZero, ok);
  // The plain disassembler does not recognize illtrap. It instead displays
  // a 32-bit value. Issueing two illtraps assures the disassembler finds
  // the proper beginning of the next instruction.
  z_illtrap(0xd2);
  z_illtrap(0xd2);
  bind(ok);
#endif
  BLOCK_COMMENT("} cKlass decoder");
}

void MacroAssembler::load_klass(Register klass, Address mem) {
  if (UseCompressedClassPointers) {
    z_llgf(klass, mem);
    // Attention: no null check here!
    decode_klass_not_null(klass);
  } else {
    z_lg(klass, mem);
  }
}

void MacroAssembler::load_klass(Register klass, Register src_oop) {
  if (UseCompressedClassPointers) {
    z_llgf(klass, oopDesc::klass_offset_in_bytes(), src_oop);
    // Attention: no null check here!
    decode_klass_not_null(klass);
  } else {
    z_lg(klass, oopDesc::klass_offset_in_bytes(), src_oop);
  }
}

void MacroAssembler::load_prototype_header(Register Rheader, Register Rsrc_oop) {
  assert_different_registers(Rheader, Rsrc_oop);
  load_klass(Rheader, Rsrc_oop);
  z_lg(Rheader, Address(Rheader, Klass::prototype_header_offset()));
}

void MacroAssembler::store_klass(Register klass, Register dst_oop, Register ck) {
  if (UseCompressedClassPointers) {
    assert_different_registers(dst_oop, klass, Z_R0);
    if (ck == noreg) ck = klass;
    encode_klass_not_null(ck, klass);
    z_st(ck, Address(dst_oop, oopDesc::klass_offset_in_bytes()));
  } else {
    z_stg(klass, Address(dst_oop, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::store_klass_gap(Register s, Register d) {
  if (UseCompressedClassPointers) {
    assert(s != d, "not enough registers");
    z_st(s, Address(d, oopDesc::klass_gap_offset_in_bytes()));
  }
}

// Compare klass ptr in memory against klass ptr in register.
//
// Rop1            - klass in register, always uncompressed.
// disp            - Offset of klass in memory, compressed/uncompressed, depending on runtime flag.
// Rbase           - Base address of cKlass in memory.
// maybeNULL       - True if Rop1 possibly is a NULL.
void MacroAssembler::compare_klass_ptr(Register Rop1, int64_t disp, Register Rbase, bool maybeNULL) {

  BLOCK_COMMENT("compare klass ptr {");

  if (UseCompressedClassPointers) {
    const int shift = Universe::narrow_klass_shift();
    address   base  = Universe::narrow_klass_base();

    assert((shift == 0) || (shift == LogKlassAlignmentInBytes), "cKlass encoder detected bad shift");
    assert_different_registers(Rop1, Z_R0);
    assert_different_registers(Rop1, Rbase, Z_R1);

    // First encode register oop and then compare with cOop in memory.
    // This sequence saves an unnecessary cOop load and decode.
    if (base == NULL) {
      if (shift == 0) {
        z_cl(Rop1, disp, Rbase);     // Unscaled
      } else {
        z_srlg(Z_R0, Rop1, shift);   // ZeroBased
        z_cl(Z_R0, disp, Rbase);
      }
    } else {                         // HeapBased
#ifdef ASSERT
      bool     used_R0 = true;
      bool     used_R1 = true;
#endif
      Register current = Rop1;
      Label    done;

      if (maybeNULL) {       // NULL ptr must be preserved!
        z_ltgr(Z_R0, current);
        z_bre(done);
        current = Z_R0;
      }

      unsigned int base_h = ((unsigned long)base)>>32;
      unsigned int base_l = (unsigned int)((unsigned long)base);
      if ((base_h != 0) && (base_l == 0) && VM_Version::has_HighWordInstr()) {
        lgr_if_needed(Z_R0, current);
        z_aih(Z_R0, -((int)base_h));     // Base has no set bits in lower half.
      } else if ((base_h == 0) && (base_l != 0)) {
        lgr_if_needed(Z_R0, current);
        z_agfi(Z_R0, -(int)base_l);
      } else {
        int pow2_offset = get_oop_base_complement(Z_R1, ((uint64_t)(intptr_t)base));
        add2reg_with_index(Z_R0, pow2_offset, Z_R1, Rop1); // Subtract base by adding complement.
      }

      if (shift != 0) {
        z_srlg(Z_R0, Z_R0, shift);
      }
      bind(done);
      z_cl(Z_R0, disp, Rbase);
#ifdef ASSERT
      if (used_R0) preset_reg(Z_R0, 0xb05bUL, 2);
      if (used_R1) preset_reg(Z_R1, 0xb06bUL, 2);
#endif
    }
  } else {
    z_clg(Rop1, disp, Z_R0, Rbase);
  }
  BLOCK_COMMENT("} compare klass ptr");
}

//---------------------------
//  Compressed oops
//---------------------------

void MacroAssembler::encode_heap_oop(Register oop) {
  oop_encoder(oop, oop, true /*maybe null*/);
}

void MacroAssembler::encode_heap_oop_not_null(Register oop) {
  oop_encoder(oop, oop, false /*not null*/);
}

// Called with something derived from the oop base. e.g. oop_base>>3.
int MacroAssembler::get_oop_base_pow2_offset(uint64_t oop_base) {
  unsigned int oop_base_ll = ((unsigned int)(oop_base >>  0)) & 0xffff;
  unsigned int oop_base_lh = ((unsigned int)(oop_base >> 16)) & 0xffff;
  unsigned int oop_base_hl = ((unsigned int)(oop_base >> 32)) & 0xffff;
  unsigned int oop_base_hh = ((unsigned int)(oop_base >> 48)) & 0xffff;
  unsigned int n_notzero_parts = (oop_base_ll == 0 ? 0:1)
                               + (oop_base_lh == 0 ? 0:1)
                               + (oop_base_hl == 0 ? 0:1)
                               + (oop_base_hh == 0 ? 0:1);

  assert(oop_base != 0, "This is for HeapBased cOops only");

  if (n_notzero_parts != 1) { //  Check if oop_base is just a few pages shy of a power of 2.
    uint64_t pow2_offset = 0x10000 - oop_base_ll;
    if (pow2_offset < 0x8000) {  // This might not be necessary.
      uint64_t oop_base2 = oop_base + pow2_offset;

      oop_base_ll = ((unsigned int)(oop_base2 >>  0)) & 0xffff;
      oop_base_lh = ((unsigned int)(oop_base2 >> 16)) & 0xffff;
      oop_base_hl = ((unsigned int)(oop_base2 >> 32)) & 0xffff;
      oop_base_hh = ((unsigned int)(oop_base2 >> 48)) & 0xffff;
      n_notzero_parts = (oop_base_ll == 0 ? 0:1) +
                        (oop_base_lh == 0 ? 0:1) +
                        (oop_base_hl == 0 ? 0:1) +
                        (oop_base_hh == 0 ? 0:1);
      if (n_notzero_parts == 1) {
        assert(-(int64_t)pow2_offset != (int64_t)-1, "We use -1 to signal uninitialized base register");
        return -pow2_offset;
      }
    }
  }
  return 0;
}

// If base address is offset from a straight power of two by just a few pages,
// return this offset to the caller for a possible later composite add.
// TODO/FIX: will only work correctly for 4k pages.
int MacroAssembler::get_oop_base(Register Rbase, uint64_t oop_base) {
  int pow2_offset = get_oop_base_pow2_offset(oop_base);

  load_const_optimized(Rbase, oop_base - pow2_offset); // Best job possible.

  return pow2_offset;
}

int MacroAssembler::get_oop_base_complement(Register Rbase, uint64_t oop_base) {
  int offset = get_oop_base(Rbase, oop_base);
  z_lcgr(Rbase, Rbase);
  return -offset;
}

// Compare compressed oop in memory against oop in register.
// Rop1            - Oop in register.
// disp            - Offset of cOop in memory.
// Rbase           - Base address of cOop in memory.
// maybeNULL       - True if Rop1 possibly is a NULL.
// maybeNULLtarget - Branch target for Rop1 == NULL, if flow control shall NOT continue with compare instruction.
void MacroAssembler::compare_heap_oop(Register Rop1, Address mem, bool maybeNULL) {
  Register Rbase  = mem.baseOrR0();
  Register Rindex = mem.indexOrR0();
  int64_t  disp   = mem.disp();

  const int shift = Universe::narrow_oop_shift();
  address   base  = Universe::narrow_oop_base();

  assert(UseCompressedOops, "must be on to call this method");
  assert(Universe::heap() != NULL, "java heap must be initialized to call this method");
  assert((shift == 0) || (shift == LogMinObjAlignmentInBytes), "cOop encoder detected bad shift");
  assert_different_registers(Rop1, Z_R0);
  assert_different_registers(Rop1, Rbase, Z_R1);
  assert_different_registers(Rop1, Rindex, Z_R1);

  BLOCK_COMMENT("compare heap oop {");

  // First encode register oop and then compare with cOop in memory.
  // This sequence saves an unnecessary cOop load and decode.
  if (base == NULL) {
    if (shift == 0) {
      z_cl(Rop1, disp, Rindex, Rbase);  // Unscaled
    } else {
      z_srlg(Z_R0, Rop1, shift);        // ZeroBased
      z_cl(Z_R0, disp, Rindex, Rbase);
    }
  } else {                              // HeapBased
#ifdef ASSERT
    bool  used_R0 = true;
    bool  used_R1 = true;
#endif
    Label done;
    int   pow2_offset = get_oop_base_complement(Z_R1, ((uint64_t)(intptr_t)base));

    if (maybeNULL) {       // NULL ptr must be preserved!
      z_ltgr(Z_R0, Rop1);
      z_bre(done);
    }

    add2reg_with_index(Z_R0, pow2_offset, Z_R1, Rop1);
    z_srlg(Z_R0, Z_R0, shift);

    bind(done);
    z_cl(Z_R0, disp, Rindex, Rbase);
#ifdef ASSERT
    if (used_R0) preset_reg(Z_R0, 0xb05bUL, 2);
    if (used_R1) preset_reg(Z_R1, 0xb06bUL, 2);
#endif
  }
  BLOCK_COMMENT("} compare heap oop");
}

// Load heap oop and decompress, if necessary.
void  MacroAssembler::load_heap_oop(Register dest, const Address &a) {
  if (UseCompressedOops) {
    z_llgf(dest, a.disp(), a.indexOrR0(), a.baseOrR0());
    oop_decoder(dest, dest, true);
  } else {
    z_lg(dest, a.disp(), a.indexOrR0(), a.baseOrR0());
  }
}

// Load heap oop and decompress, if necessary.
void MacroAssembler::load_heap_oop(Register dest, int64_t disp, Register base) {
  if (UseCompressedOops) {
    z_llgf(dest, disp, base);
    oop_decoder(dest, dest, true);
  } else {
    z_lg(dest, disp, base);
  }
}

// Load heap oop and decompress, if necessary.
void MacroAssembler::load_heap_oop_not_null(Register dest, int64_t disp, Register base) {
  if (UseCompressedOops) {
    z_llgf(dest, disp, base);
    oop_decoder(dest, dest, false);
  } else {
    z_lg(dest, disp, base);
  }
}

// Compress, if necessary, and store oop to heap.
void MacroAssembler::store_heap_oop(Register Roop, RegisterOrConstant offset, Register base) {
  Register Ridx = offset.is_register() ? offset.register_or_noreg() : Z_R0;
  if (UseCompressedOops) {
    assert_different_registers(Roop, offset.register_or_noreg(), base);
    encode_heap_oop(Roop);
    z_st(Roop, offset.constant_or_zero(), Ridx, base);
  } else {
    z_stg(Roop, offset.constant_or_zero(), Ridx, base);
  }
}

// Compress, if necessary, and store oop to heap. Oop is guaranteed to be not NULL.
void MacroAssembler::store_heap_oop_not_null(Register Roop, RegisterOrConstant offset, Register base) {
  Register Ridx = offset.is_register() ? offset.register_or_noreg() : Z_R0;
  if (UseCompressedOops) {
    assert_different_registers(Roop, offset.register_or_noreg(), base);
    encode_heap_oop_not_null(Roop);
    z_st(Roop, offset.constant_or_zero(), Ridx, base);
  } else {
    z_stg(Roop, offset.constant_or_zero(), Ridx, base);
  }
}

// Store NULL oop to heap.
void MacroAssembler::store_heap_oop_null(Register zero, RegisterOrConstant offset, Register base) {
  Register Ridx = offset.is_register() ? offset.register_or_noreg() : Z_R0;
  if (UseCompressedOops) {
    z_st(zero, offset.constant_or_zero(), Ridx, base);
  } else {
    z_stg(zero, offset.constant_or_zero(), Ridx, base);
  }
}

//-------------------------------------------------
// Encode compressed oop. Generally usable encoder.
//-------------------------------------------------
// Rsrc - contains regular oop on entry. It remains unchanged.
// Rdst - contains compressed oop on exit.
// Rdst and Rsrc may indicate same register, in which case Rsrc does not remain unchanged.
//
// Rdst must not indicate scratch register Z_R1 (Z_R1_scratch) for functionality.
// Rdst should not indicate scratch register Z_R0 (Z_R0_scratch) for performance.
//
// only32bitValid is set, if later code only uses the lower 32 bits. In this
// case we must not fix the upper 32 bits.
void MacroAssembler::oop_encoder(Register Rdst, Register Rsrc, bool maybeNULL,
                                 Register Rbase, int pow2_offset, bool only32bitValid) {

  const address oop_base  = Universe::narrow_oop_base();
  const int     oop_shift = Universe::narrow_oop_shift();
  const bool    disjoint  = Universe::narrow_oop_base_disjoint();

  assert(UseCompressedOops, "must be on to call this method");
  assert(Universe::heap() != NULL, "java heap must be initialized to call this encoder");
  assert((oop_shift == 0) || (oop_shift == LogMinObjAlignmentInBytes), "cOop encoder detected bad shift");

  if (disjoint || (oop_base == NULL)) {
    BLOCK_COMMENT("cOop encoder zeroBase {");
    if (oop_shift == 0) {
      if (oop_base != NULL && !only32bitValid) {
        z_llgfr(Rdst, Rsrc); // Clear upper bits in case the register will be decoded again.
      } else {
        lgr_if_needed(Rdst, Rsrc);
      }
    } else {
      z_srlg(Rdst, Rsrc, oop_shift);
      if (oop_base != NULL && !only32bitValid) {
        z_llgfr(Rdst, Rdst); // Clear upper bits in case the register will be decoded again.
      }
    }
    BLOCK_COMMENT("} cOop encoder zeroBase");
    return;
  }

  bool used_R0 = false;
  bool used_R1 = false;

  BLOCK_COMMENT("cOop encoder general {");
  assert_different_registers(Rdst, Z_R1);
  assert_different_registers(Rsrc, Rbase);
  if (maybeNULL) {
    Label done;
    // We reorder shifting and subtracting, so that we can compare
    // and shift in parallel:
    //
    // cycle 0:  potential LoadN, base = <const>
    // cycle 1:  base = !base     dst = src >> 3,    cmp cr = (src != 0)
    // cycle 2:  if (cr) br,      dst = dst + base + offset

    // Get oop_base components.
    if (pow2_offset == -1) {
      if (Rdst == Rbase) {
        if (Rdst == Z_R1 || Rsrc == Z_R1) {
          Rbase = Z_R0;
          used_R0 = true;
        } else {
          Rdst = Z_R1;
          used_R1 = true;
        }
      }
      if (Rbase == Z_R1) {
        used_R1 = true;
      }
      pow2_offset = get_oop_base_complement(Rbase, ((uint64_t)(intptr_t)oop_base) >> oop_shift);
    }
    assert_different_registers(Rdst, Rbase);

    // Check for NULL oop (must be left alone) and shift.
    if (oop_shift != 0) {  // Shift out alignment bits
      if (((intptr_t)oop_base&0xc000000000000000L) == 0L) { // We are sure: no single address will have the leftmost bit set.
        z_srag(Rdst, Rsrc, oop_shift);  // Arithmetic shift sets the condition code.
      } else {
        z_srlg(Rdst, Rsrc, oop_shift);
        z_ltgr(Rsrc, Rsrc);  // This is the recommended way of testing for zero.
        // This probably is faster, as it does not write a register. No!
        // z_cghi(Rsrc, 0);
      }
    } else {
      z_ltgr(Rdst, Rsrc);   // Move NULL to result register.
    }
    z_bre(done);

    // Subtract oop_base components.
    if ((Rdst == Z_R0) || (Rbase == Z_R0)) {
      z_algr(Rdst, Rbase);
      if (pow2_offset != 0) { add2reg(Rdst, pow2_offset); }
    } else {
      add2reg_with_index(Rdst, pow2_offset, Rbase, Rdst);
    }
    if (!only32bitValid) {
      z_llgfr(Rdst, Rdst); // Clear upper bits in case the register will be decoded again.
    }
    bind(done);

  } else {  // not null
    // Get oop_base components.
    if (pow2_offset == -1) {
      pow2_offset = get_oop_base_complement(Rbase, (uint64_t)(intptr_t)oop_base);
    }

    // Subtract oop_base components and shift.
    if (Rdst == Z_R0 || Rsrc == Z_R0 || Rbase == Z_R0) {
      // Don't use lay instruction.
      if (Rdst == Rsrc) {
        z_algr(Rdst, Rbase);
      } else {
        lgr_if_needed(Rdst, Rbase);
        z_algr(Rdst, Rsrc);
      }
      if (pow2_offset != 0) add2reg(Rdst, pow2_offset);
    } else {
      add2reg_with_index(Rdst, pow2_offset, Rbase, Rsrc);
    }
    if (oop_shift != 0) {   // Shift out alignment bits.
      z_srlg(Rdst, Rdst, oop_shift);
    }
    if (!only32bitValid) {
      z_llgfr(Rdst, Rdst); // Clear upper bits in case the register will be decoded again.
    }
  }
#ifdef ASSERT
  if (used_R0 && Rdst != Z_R0 && Rsrc != Z_R0) { preset_reg(Z_R0, 0xb01bUL, 2); }
  if (used_R1 && Rdst != Z_R1 && Rsrc != Z_R1) { preset_reg(Z_R1, 0xb02bUL, 2); }
#endif
  BLOCK_COMMENT("} cOop encoder general");
}

//-------------------------------------------------
// decode compressed oop. Generally usable decoder.
//-------------------------------------------------
// Rsrc - contains compressed oop on entry.
// Rdst - contains regular oop on exit.
// Rdst and Rsrc may indicate same register.
// Rdst must not be the same register as Rbase, if Rbase was preloaded (before call).
// Rdst can be the same register as Rbase. Then, either Z_R0 or Z_R1 must be available as scratch.
// Rbase - register to use for the base
// pow2_offset - offset of base to nice value. If -1, base must be loaded.
// For performance, it is good to
//  - avoid Z_R0 for any of the argument registers.
//  - keep Rdst and Rsrc distinct from Rbase. Rdst == Rsrc is ok for performance.
//  - avoid Z_R1 for Rdst if Rdst == Rbase.
void MacroAssembler::oop_decoder(Register Rdst, Register Rsrc, bool maybeNULL, Register Rbase, int pow2_offset) {

  const address oop_base  = Universe::narrow_oop_base();
  const int     oop_shift = Universe::narrow_oop_shift();
  const bool    disjoint  = Universe::narrow_oop_base_disjoint();

  assert(UseCompressedOops, "must be on to call this method");
  assert(Universe::heap() != NULL, "java heap must be initialized to call this decoder");
  assert((oop_shift == 0) || (oop_shift == LogMinObjAlignmentInBytes),
         "cOop encoder detected bad shift");

  // cOops are always loaded zero-extended from memory. No explicit zero-extension necessary.

  if (oop_base != NULL) {
    unsigned int oop_base_hl = ((unsigned int)((uint64_t)(intptr_t)oop_base >> 32)) & 0xffff;
    unsigned int oop_base_hh = ((unsigned int)((uint64_t)(intptr_t)oop_base >> 48)) & 0xffff;
    unsigned int oop_base_hf = ((unsigned int)((uint64_t)(intptr_t)oop_base >> 32)) & 0xFFFFffff;
    if (disjoint && (oop_base_hl == 0 || oop_base_hh == 0)) {
      BLOCK_COMMENT("cOop decoder disjointBase {");
      // We do not need to load the base. Instead, we can install the upper bits
      // with an OR instead of an ADD.
      Label done;

      // Rsrc contains a narrow oop. Thus we are sure the leftmost <oop_shift> bits will never be set.
      if (maybeNULL) {  // NULL ptr must be preserved!
        z_slag(Rdst, Rsrc, oop_shift);  // Arithmetic shift sets the condition code.
        z_bre(done);
      } else {
        z_sllg(Rdst, Rsrc, oop_shift);  // Logical shift leaves condition code alone.
      }
      if ((oop_base_hl != 0) && (oop_base_hh != 0)) {
        z_oihf(Rdst, oop_base_hf);
      } else if (oop_base_hl != 0) {
        z_oihl(Rdst, oop_base_hl);
      } else {
        assert(oop_base_hh != 0, "not heapbased mode");
        z_oihh(Rdst, oop_base_hh);
      }
      bind(done);
      BLOCK_COMMENT("} cOop decoder disjointBase");
    } else {
      BLOCK_COMMENT("cOop decoder general {");
      // There are three decode steps:
      //   scale oop offset (shift left)
      //   get base (in reg) and pow2_offset (constant)
      //   add base, pow2_offset, and oop offset
      // The following register overlap situations may exist:
      // Rdst == Rsrc,  Rbase any other
      //   not a problem. Scaling in-place leaves Rbase undisturbed.
      //   Loading Rbase does not impact the scaled offset.
      // Rdst == Rbase, Rsrc  any other
      //   scaling would destroy a possibly preloaded Rbase. Loading Rbase
      //   would destroy the scaled offset.
      //   Remedy: use Rdst_tmp if Rbase has been preloaded.
      //           use Rbase_tmp if base has to be loaded.
      // Rsrc == Rbase, Rdst  any other
      //   Only possible without preloaded Rbase.
      //   Loading Rbase does not destroy compressed oop because it was scaled into Rdst before.
      // Rsrc == Rbase, Rdst == Rbase
      //   Only possible without preloaded Rbase.
      //   Loading Rbase would destroy compressed oop. Scaling in-place is ok.
      //   Remedy: use Rbase_tmp.
      //
      Label    done;
      Register Rdst_tmp       = Rdst;
      Register Rbase_tmp      = Rbase;
      bool     used_R0        = false;
      bool     used_R1        = false;
      bool     base_preloaded = pow2_offset >= 0;
      guarantee(!(base_preloaded && (Rsrc == Rbase)), "Register clash, check caller");
      assert(oop_shift != 0, "room for optimization");

      // Check if we need to use scratch registers.
      if (Rdst == Rbase) {
        assert(!(((Rdst == Z_R0) && (Rsrc == Z_R1)) || ((Rdst == Z_R1) && (Rsrc == Z_R0))), "need a scratch reg");
        if (Rdst != Rsrc) {
          if (base_preloaded) { Rdst_tmp  = (Rdst == Z_R1) ? Z_R0 : Z_R1; }
          else                { Rbase_tmp = (Rdst == Z_R1) ? Z_R0 : Z_R1; }
        } else {
          Rbase_tmp = (Rdst == Z_R1) ? Z_R0 : Z_R1;
        }
      }
      if (base_preloaded) lgr_if_needed(Rbase_tmp, Rbase);

      // Scale oop and check for NULL.
      // Rsrc contains a narrow oop. Thus we are sure the leftmost <oop_shift> bits will never be set.
      if (maybeNULL) {  // NULL ptr must be preserved!
        z_slag(Rdst_tmp, Rsrc, oop_shift);  // Arithmetic shift sets the condition code.
        z_bre(done);
      } else {
        z_sllg(Rdst_tmp, Rsrc, oop_shift);  // Logical shift leaves condition code alone.
      }

      // Get oop_base components.
      if (!base_preloaded) {
        pow2_offset = get_oop_base(Rbase_tmp, (uint64_t)(intptr_t)oop_base);
      }

      // Add up all components.
      if ((Rbase_tmp == Z_R0) || (Rdst_tmp == Z_R0)) {
        z_algr(Rdst_tmp, Rbase_tmp);
        if (pow2_offset != 0) { add2reg(Rdst_tmp, pow2_offset); }
      } else {
        add2reg_with_index(Rdst_tmp, pow2_offset, Rbase_tmp, Rdst_tmp);
      }

      bind(done);
      lgr_if_needed(Rdst, Rdst_tmp);
#ifdef ASSERT
      if (used_R0 && Rdst != Z_R0 && Rsrc != Z_R0) { preset_reg(Z_R0, 0xb03bUL, 2); }
      if (used_R1 && Rdst != Z_R1 && Rsrc != Z_R1) { preset_reg(Z_R1, 0xb04bUL, 2); }
#endif
      BLOCK_COMMENT("} cOop decoder general");
    }
  } else {
    BLOCK_COMMENT("cOop decoder zeroBase {");
    if (oop_shift == 0) {
      lgr_if_needed(Rdst, Rsrc);
    } else {
      z_sllg(Rdst, Rsrc, oop_shift);
    }
    BLOCK_COMMENT("} cOop decoder zeroBase");
  }
}

void MacroAssembler::load_mirror(Register mirror, Register method) {
  mem2reg_opt(mirror, Address(method, Method::const_offset()));
  mem2reg_opt(mirror, Address(mirror, ConstMethod::constants_offset()));
  mem2reg_opt(mirror, Address(mirror, ConstantPool::pool_holder_offset_in_bytes()));
  mem2reg_opt(mirror, Address(mirror, Klass::java_mirror_offset()));
}

//---------------------------------------------------------------
//---  Operations on arrays.
//---------------------------------------------------------------

// Compiler ensures base is doubleword aligned and cnt is #doublewords.
// Emitter does not KILL cnt and base arguments, since they need to be copied to
// work registers anyway.
// Actually, only r0, r1, and r5 are killed.
unsigned int MacroAssembler::Clear_Array(Register cnt_arg, Register base_pointer_arg, Register src_addr, Register src_len) {
  // Src_addr is evenReg.
  // Src_len is odd_Reg.

  int      block_start = offset();
  Register tmp_reg  = src_len; // Holds target instr addr for EX.
  Register dst_len  = Z_R1;    // Holds dst len  for MVCLE.
  Register dst_addr = Z_R0;    // Holds dst addr for MVCLE.

  Label doXC, doMVCLE, done;

  BLOCK_COMMENT("Clear_Array {");

  // Check for zero len and convert to long.
  z_ltgfr(src_len, cnt_arg);      // Remember casted value for doSTG case.
  z_bre(done);                    // Nothing to do if len == 0.

  // Prefetch data to be cleared.
  if (VM_Version::has_Prefetch()) {
    z_pfd(0x02,   0, Z_R0, base_pointer_arg);
    z_pfd(0x02, 256, Z_R0, base_pointer_arg);
  }

  z_sllg(dst_len, src_len, 3);    // #bytes to clear.
  z_cghi(src_len, 32);            // Check for len <= 256 bytes (<=32 DW).
  z_brnh(doXC);                   // If so, use executed XC to clear.

  // MVCLE: initialize long arrays (general case).
  bind(doMVCLE);
  z_lgr(dst_addr, base_pointer_arg);
  clear_reg(src_len, true, false); // Src len of MVCLE is zero.

  MacroAssembler::move_long_ext(dst_addr, src_addr, 0);
  z_bru(done);

  // XC: initialize short arrays.
  Label XC_template; // Instr template, never exec directly!
    bind(XC_template);
    z_xc(0,0,base_pointer_arg,0,base_pointer_arg);

  bind(doXC);
    add2reg(dst_len, -1);             // Get #bytes-1 for EXECUTE.
    if (VM_Version::has_ExecuteExtensions()) {
      z_exrl(dst_len, XC_template);   // Execute XC with var. len.
    } else {
      z_larl(tmp_reg, XC_template);
      z_ex(dst_len,0,Z_R0,tmp_reg);   // Execute XC with var. len.
    }
    // z_bru(done);      // fallthru

  bind(done);

  BLOCK_COMMENT("} Clear_Array");

  int block_end = offset();
  return block_end - block_start;
}

// Compiler ensures base is doubleword aligned and cnt is count of doublewords.
// Emitter does not KILL any arguments nor work registers.
// Emitter generates up to 16 XC instructions, depending on the array length.
unsigned int MacroAssembler::Clear_Array_Const(long cnt, Register base) {
  int  block_start    = offset();
  int  off;
  int  lineSize_Bytes = AllocatePrefetchStepSize;
  int  lineSize_DW    = AllocatePrefetchStepSize>>LogBytesPerWord;
  bool doPrefetch     = VM_Version::has_Prefetch();
  int  XC_maxlen      = 256;
  int  numXCInstr     = cnt > 0 ? (cnt*BytesPerWord-1)/XC_maxlen+1 : 0;

  BLOCK_COMMENT("Clear_Array_Const {");
  assert(cnt*BytesPerWord <= 4096, "ClearArrayConst can handle 4k only");

  // Do less prefetching for very short arrays.
  if (numXCInstr > 0) {
    // Prefetch only some cache lines, then begin clearing.
    if (doPrefetch) {
      if (cnt*BytesPerWord <= lineSize_Bytes/4) {  // If less than 1/4 of a cache line to clear,
        z_pfd(0x02, 0, Z_R0, base);                // prefetch just the first cache line.
      } else {
        assert(XC_maxlen == lineSize_Bytes, "ClearArrayConst needs 256B cache lines");
        for (off = 0; (off < AllocatePrefetchLines) && (off <= numXCInstr); off ++) {
          z_pfd(0x02, off*lineSize_Bytes, Z_R0, base);
        }
      }
    }

    for (off=0; off<(numXCInstr-1); off++) {
      z_xc(off*XC_maxlen, XC_maxlen-1, base, off*XC_maxlen, base);

      // Prefetch some cache lines in advance.
      if (doPrefetch && (off <= numXCInstr-AllocatePrefetchLines)) {
        z_pfd(0x02, (off+AllocatePrefetchLines)*lineSize_Bytes, Z_R0, base);
      }
    }
    if (off*XC_maxlen < cnt*BytesPerWord) {
      z_xc(off*XC_maxlen, (cnt*BytesPerWord-off*XC_maxlen)-1, base, off*XC_maxlen, base);
    }
  }
  BLOCK_COMMENT("} Clear_Array_Const");

  int block_end = offset();
  return block_end - block_start;
}

// Compiler ensures base is doubleword aligned and cnt is #doublewords.
// Emitter does not KILL cnt and base arguments, since they need to be copied to
// work registers anyway.
// Actually, only r0, r1, r4, and r5 (which are work registers) are killed.
//
// For very large arrays, exploit MVCLE H/W support.
// MVCLE instruction automatically exploits H/W-optimized page mover.
// - Bytes up to next page boundary are cleared with a series of XC to self.
// - All full pages are cleared with the page mover H/W assist.
// - Remaining bytes are again cleared by a series of XC to self.
//
unsigned int MacroAssembler::Clear_Array_Const_Big(long cnt, Register base_pointer_arg, Register src_addr, Register src_len) {
  // Src_addr is evenReg.
  // Src_len is odd_Reg.

  int      block_start = offset();
  Register dst_len  = Z_R1;      // Holds dst len  for MVCLE.
  Register dst_addr = Z_R0;      // Holds dst addr for MVCLE.

  BLOCK_COMMENT("Clear_Array_Const_Big {");

  // Get len to clear.
  load_const_optimized(dst_len, (long)cnt*8L);  // in Bytes = #DW*8

  // Prepare other args to MVCLE.
  z_lgr(dst_addr, base_pointer_arg);
  // Indicate unused result.
  (void) clear_reg(src_len, true, false);  // Src len of MVCLE is zero.

  // Clear.
  MacroAssembler::move_long_ext(dst_addr, src_addr, 0);
  BLOCK_COMMENT("} Clear_Array_Const_Big");

  int block_end = offset();
  return block_end - block_start;
}

// Allocator.
unsigned int MacroAssembler::CopyRawMemory_AlignedDisjoint(Register src_reg, Register dst_reg,
                                                           Register cnt_reg,
                                                           Register tmp1_reg, Register tmp2_reg) {
  // Tmp1 is oddReg.
  // Tmp2 is evenReg.

  int block_start = offset();
  Label doMVC, doMVCLE, done, MVC_template;

  BLOCK_COMMENT("CopyRawMemory_AlignedDisjoint {");

  // Check for zero len and convert to long.
  z_ltgfr(cnt_reg, cnt_reg);      // Remember casted value for doSTG case.
  z_bre(done);                    // Nothing to do if len == 0.

  z_sllg(Z_R1, cnt_reg, 3);       // Dst len in bytes. calc early to have the result ready.

  z_cghi(cnt_reg, 32);            // Check for len <= 256 bytes (<=32 DW).
  z_brnh(doMVC);                  // If so, use executed MVC to clear.

  bind(doMVCLE);                  // A lot of data (more than 256 bytes).
  // Prep dest reg pair.
  z_lgr(Z_R0, dst_reg);           // dst addr
  // Dst len already in Z_R1.
  // Prep src reg pair.
  z_lgr(tmp2_reg, src_reg);       // src addr
  z_lgr(tmp1_reg, Z_R1);          // Src len same as dst len.

  // Do the copy.
  move_long_ext(Z_R0, tmp2_reg, 0xb0); // Bypass cache.
  z_bru(done);                         // All done.

  bind(MVC_template);             // Just some data (not more than 256 bytes).
  z_mvc(0, 0, dst_reg, 0, src_reg);

  bind(doMVC);

  if (VM_Version::has_ExecuteExtensions()) {
    add2reg(Z_R1, -1);
  } else {
    add2reg(tmp1_reg, -1, Z_R1);
    z_larl(Z_R1, MVC_template);
  }

  if (VM_Version::has_Prefetch()) {
    z_pfd(1,  0,Z_R0,src_reg);
    z_pfd(2,  0,Z_R0,dst_reg);
    //    z_pfd(1,256,Z_R0,src_reg);    // Assume very short copy.
    //    z_pfd(2,256,Z_R0,dst_reg);
  }

  if (VM_Version::has_ExecuteExtensions()) {
    z_exrl(Z_R1, MVC_template);
  } else {
    z_ex(tmp1_reg, 0, Z_R0, Z_R1);
  }

  bind(done);

  BLOCK_COMMENT("} CopyRawMemory_AlignedDisjoint");

  int block_end = offset();
  return block_end - block_start;
}

//------------------------------------------------------
//   Special String Intrinsics. Implementation
//------------------------------------------------------

// Intrinsics for CompactStrings

// Compress char[] to byte[]. odd_reg contains cnt. Kills dst. Early clobber: result
// The result is the number of characters copied before the first incompatible character was found.
// If tmp2 is provided and the compression fails, the compression stops exactly at this point and the result is precise.
//
// Note: Does not behave exactly like package private StringUTF16 compress java implementation in case of failure:
// - Different number of characters may have been written to dead array (if tmp2 not provided).
// - Returns a number <cnt instead of 0. (Result gets compared with cnt.)
unsigned int MacroAssembler::string_compress(Register result, Register src, Register dst, Register odd_reg,
                                             Register even_reg, Register tmp, Register tmp2) {
  int block_start = offset();
  Label Lloop1, Lloop2, Lslow, Ldone;
  const Register addr2 = dst, ind1 = result, mask = tmp;
  const bool precise = (tmp2 != noreg);

  BLOCK_COMMENT("string_compress {");

  z_sll(odd_reg, 1);       // Number of bytes to read. (Must be a positive simm32.)
  clear_reg(ind1);         // Index to read.
  z_llilf(mask, 0xFF00FF00);
  z_ahi(odd_reg, -16);     // Last possible index for fast loop.
  z_brl(Lslow);

  // ind1: index, even_reg: index increment, odd_reg: index limit
  z_iihf(mask, 0xFF00FF00);
  z_lhi(even_reg, 16);

  bind(Lloop1); // 8 Characters per iteration.
  z_lg(Z_R0, Address(src, ind1));
  z_lg(Z_R1, Address(src, ind1, 8));
  if (precise) {
    if (VM_Version::has_DistinctOpnds()) {
      z_ogrk(tmp2, Z_R0, Z_R1);
    } else {
      z_lgr(tmp2, Z_R0);
      z_ogr(tmp2, Z_R1);
    }
    z_ngr(tmp2, mask);
    z_brne(Lslow);         // Failed fast case, retry slowly.
  }
  z_stcmh(Z_R0, 5, 0, addr2);
  z_stcm(Z_R0, 5, 2, addr2);
  if (!precise) { z_ogr(Z_R0, Z_R1); }
  z_stcmh(Z_R1, 5, 4, addr2);
  z_stcm(Z_R1, 5, 6, addr2);
  if (!precise) {
    z_ngr(Z_R0, mask);
    z_brne(Ldone);         // Failed (more than needed was written).
  }
  z_aghi(addr2, 8);
  z_brxle(ind1, even_reg, Lloop1);

  bind(Lslow);
  // Compute index limit and skip if negative.
  z_ahi(odd_reg, 16-2);    // Last possible index for slow loop.
  z_lhi(even_reg, 2);
  z_cr(ind1, odd_reg);
  z_brh(Ldone);

  bind(Lloop2); // 1 Character per iteration.
  z_llh(Z_R0, Address(src, ind1));
  z_tmll(Z_R0, 0xFF00);
  z_brnaz(Ldone);          // Failed slow case: Return number of written characters.
  z_stc(Z_R0, Address(addr2));
  z_aghi(addr2, 1);
  z_brxle(ind1, even_reg, Lloop2);

  bind(Ldone);             // result = ind1 = 2*cnt
  z_srl(ind1, 1);

  BLOCK_COMMENT("} string_compress");

  return offset() - block_start;
}

// Inflate byte[] to char[].
unsigned int MacroAssembler::string_inflate_trot(Register src, Register dst, Register cnt, Register tmp) {
  int block_start = offset();

  BLOCK_COMMENT("string_inflate {");

  Register stop_char = Z_R0;
  Register table     = Z_R1;
  Register src_addr  = tmp;

  assert_different_registers(Z_R0, Z_R1, tmp, src, dst, cnt);
  assert(dst->encoding()%2 == 0, "must be even reg");
  assert(cnt->encoding()%2 == 1, "must be odd reg");
  assert(cnt->encoding() - dst->encoding() == 1, "must be even/odd pair");

  StubRoutines::zarch::generate_load_trot_table_addr(this, table);  // kills Z_R0 (if ASSERT)
  clear_reg(stop_char);  // Stop character. Not used here, but initialized to have a defined value.
  lgr_if_needed(src_addr, src);
  z_llgfr(cnt, cnt);     // # src characters, must be a positive simm32.

  translate_ot(dst, src_addr, /* mask = */ 0x0001);

  BLOCK_COMMENT("} string_inflate");

  return offset() - block_start;
}

// Inflate byte[] to char[]. odd_reg contains cnt. Kills src.
unsigned int MacroAssembler::string_inflate(Register src, Register dst, Register odd_reg,
                                            Register even_reg, Register tmp) {
  int block_start = offset();

  BLOCK_COMMENT("string_inflate {");

  Label Lloop1, Lloop2, Lslow, Ldone;
  const Register addr1 = src, ind2 = tmp;

  z_sll(odd_reg, 1);       // Number of bytes to write. (Must be a positive simm32.)
  clear_reg(ind2);         // Index to write.
  z_ahi(odd_reg, -16);     // Last possible index for fast loop.
  z_brl(Lslow);

  // ind2: index, even_reg: index increment, odd_reg: index limit
  clear_reg(Z_R0);
  clear_reg(Z_R1);
  z_lhi(even_reg, 16);

  bind(Lloop1); // 8 Characters per iteration.
  z_icmh(Z_R0, 5, 0, addr1);
  z_icmh(Z_R1, 5, 4, addr1);
  z_icm(Z_R0, 5, 2, addr1);
  z_icm(Z_R1, 5, 6, addr1);
  z_aghi(addr1, 8);
  z_stg(Z_R0, Address(dst, ind2));
  z_stg(Z_R1, Address(dst, ind2, 8));
  z_brxle(ind2, even_reg, Lloop1);

  bind(Lslow);
  // Compute index limit and skip if negative.
  z_ahi(odd_reg, 16-2);    // Last possible index for slow loop.
  z_lhi(even_reg, 2);
  z_cr(ind2, odd_reg);
  z_brh(Ldone);

  bind(Lloop2); // 1 Character per iteration.
  z_llc(Z_R0, Address(addr1));
  z_sth(Z_R0, Address(dst, ind2));
  z_aghi(addr1, 1);
  z_brxle(ind2, even_reg, Lloop2);

  bind(Ldone);

  BLOCK_COMMENT("} string_inflate");

  return offset() - block_start;
}

// Kills src.
unsigned int MacroAssembler::has_negatives(Register result, Register src, Register cnt,
                                           Register odd_reg, Register even_reg, Register tmp) {
  int block_start = offset();
  Label Lloop1, Lloop2, Lslow, Lnotfound, Ldone;
  const Register addr = src, mask = tmp;

  BLOCK_COMMENT("has_negatives {");

  z_llgfr(Z_R1, cnt);      // Number of bytes to read. (Must be a positive simm32.)
  z_llilf(mask, 0x80808080);
  z_lhi(result, 1);        // Assume true.
  // Last possible addr for fast loop.
  z_lay(odd_reg, -16, Z_R1, src);
  z_chi(cnt, 16);
  z_brl(Lslow);

  // ind1: index, even_reg: index increment, odd_reg: index limit
  z_iihf(mask, 0x80808080);
  z_lghi(even_reg, 16);

  bind(Lloop1); // 16 bytes per iteration.
  z_lg(Z_R0, Address(addr));
  z_lg(Z_R1, Address(addr, 8));
  z_ogr(Z_R0, Z_R1);
  z_ngr(Z_R0, mask);
  z_brne(Ldone);           // If found return 1.
  z_brxlg(addr, even_reg, Lloop1);

  bind(Lslow);
  z_aghi(odd_reg, 16-1);   // Last possible addr for slow loop.
  z_lghi(even_reg, 1);
  z_cgr(addr, odd_reg);
  z_brh(Lnotfound);

  bind(Lloop2); // 1 byte per iteration.
  z_cli(Address(addr), 0x80);
  z_brnl(Ldone);           // If found return 1.
  z_brxlg(addr, even_reg, Lloop2);

  bind(Lnotfound);
  z_lhi(result, 0);

  bind(Ldone);

  BLOCK_COMMENT("} has_negatives");

  return offset() - block_start;
}

// kill: cnt1, cnt2, odd_reg, even_reg; early clobber: result
unsigned int MacroAssembler::string_compare(Register str1, Register str2,
                                            Register cnt1, Register cnt2,
                                            Register odd_reg, Register even_reg, Register result, int ae) {
  int block_start = offset();

  assert_different_registers(str1, cnt1, cnt2, odd_reg, even_reg, result);
  assert_different_registers(str2, cnt1, cnt2, odd_reg, even_reg, result);

  // If strings are equal up to min length, return the length difference.
  const Register diff = result, // Pre-set result with length difference.
                 min  = cnt1,   // min number of bytes
                 tmp  = cnt2;

  // Note: Making use of the fact that compareTo(a, b) == -compareTo(b, a)
  // we interchange str1 and str2 in the UL case and negate the result.
  // Like this, str1 is always latin1 encoded, except for the UU case.
  // In addition, we need 0 (or sign which is 0) extend when using 64 bit register.
  const bool used_as_LU = (ae == StrIntrinsicNode::LU || ae == StrIntrinsicNode::UL);

  BLOCK_COMMENT("string_compare {");

  if (used_as_LU) {
    z_srl(cnt2, 1);
  }

  // See if the lengths are different, and calculate min in cnt1.
  // Save diff in case we need it for a tie-breaker.

  // diff = cnt1 - cnt2
  if (VM_Version::has_DistinctOpnds()) {
    z_srk(diff, cnt1, cnt2);
  } else {
    z_lr(diff, cnt1);
    z_sr(diff, cnt2);
  }
  if (str1 != str2) {
    if (VM_Version::has_LoadStoreConditional()) {
      z_locr(min, cnt2, Assembler::bcondHigh);
    } else {
      Label Lskip;
      z_brl(Lskip);    // min ok if cnt1 < cnt2
      z_lr(min, cnt2); // min = cnt2
      bind(Lskip);
    }
  }

  if (ae == StrIntrinsicNode::UU) {
    z_sra(diff, 1);
  }
  if (str1 != str2) {
    Label Ldone;
    if (used_as_LU) {
      // Loop which searches the first difference character by character.
      Label Lloop;
      const Register ind1 = Z_R1,
                     ind2 = min;
      int stride1 = 1, stride2 = 2; // See comment above.

      // ind1: index, even_reg: index increment, odd_reg: index limit
      z_llilf(ind1, (unsigned int)(-stride1));
      z_lhi(even_reg, stride1);
      add2reg(odd_reg, -stride1, min);
      clear_reg(ind2); // kills min

      bind(Lloop);
      z_brxh(ind1, even_reg, Ldone);
      z_llc(tmp, Address(str1, ind1));
      z_llh(Z_R0, Address(str2, ind2));
      z_ahi(ind2, stride2);
      z_sr(tmp, Z_R0);
      z_bre(Lloop);

      z_lr(result, tmp);

    } else {
      // Use clcle in fast loop (only for same encoding).
      z_lgr(Z_R0, str1);
      z_lgr(even_reg, str2);
      z_llgfr(Z_R1, min);
      z_llgfr(odd_reg, min);

      if (ae == StrIntrinsicNode::LL) {
        compare_long_ext(Z_R0, even_reg, 0);
      } else {
        compare_long_uni(Z_R0, even_reg, 0);
      }
      z_bre(Ldone);
      z_lgr(Z_R1, Z_R0);
      if (ae == StrIntrinsicNode::LL) {
        z_llc(Z_R0, Address(even_reg));
        z_llc(result, Address(Z_R1));
      } else {
        z_llh(Z_R0, Address(even_reg));
        z_llh(result, Address(Z_R1));
      }
      z_sr(result, Z_R0);
    }

    // Otherwise, return the difference between the first mismatched chars.
    bind(Ldone);
  }

  if (ae == StrIntrinsicNode::UL) {
    z_lcr(result, result); // Negate result (see note above).
  }

  BLOCK_COMMENT("} string_compare");

  return offset() - block_start;
}

unsigned int MacroAssembler::array_equals(bool is_array_equ, Register ary1, Register ary2, Register limit,
                                          Register odd_reg, Register even_reg, Register result, bool is_byte) {
  int block_start = offset();

  BLOCK_COMMENT("array_equals {");

  assert_different_registers(ary1, limit, odd_reg, even_reg);
  assert_different_registers(ary2, limit, odd_reg, even_reg);

  Label Ldone, Ldone_true, Ldone_false, Lclcle, CLC_template;
  int base_offset = 0;

  if (ary1 != ary2) {
    if (is_array_equ) {
      base_offset = arrayOopDesc::base_offset_in_bytes(is_byte ? T_BYTE : T_CHAR);

      // Return true if the same array.
      compareU64_and_branch(ary1, ary2, Assembler::bcondEqual, Ldone_true);

      // Return false if one of them is NULL.
      compareU64_and_branch(ary1, (intptr_t)0, Assembler::bcondEqual, Ldone_false);
      compareU64_and_branch(ary2, (intptr_t)0, Assembler::bcondEqual, Ldone_false);

      // Load the lengths of arrays.
      z_llgf(odd_reg, Address(ary1, arrayOopDesc::length_offset_in_bytes()));

      // Return false if the two arrays are not equal length.
      z_c(odd_reg, Address(ary2, arrayOopDesc::length_offset_in_bytes()));
      z_brne(Ldone_false);

      // string len in bytes (right operand)
      if (!is_byte) {
        z_chi(odd_reg, 128);
        z_sll(odd_reg, 1); // preserves flags
        z_brh(Lclcle);
      } else {
        compareU32_and_branch(odd_reg, (intptr_t)256, Assembler::bcondHigh, Lclcle);
      }
    } else {
      z_llgfr(odd_reg, limit); // Need to zero-extend prior to using the value.
      compareU32_and_branch(limit, (intptr_t)256, Assembler::bcondHigh, Lclcle);
    }


    // Use clc instruction for up to 256 bytes.
    {
      Register str1_reg = ary1,
          str2_reg = ary2;
      if (is_array_equ) {
        str1_reg = Z_R1;
        str2_reg = even_reg;
        add2reg(str1_reg, base_offset, ary1); // string addr (left operand)
        add2reg(str2_reg, base_offset, ary2); // string addr (right operand)
      }
      z_ahi(odd_reg, -1); // Clc uses decremented limit. Also compare result to 0.
      z_brl(Ldone_true);
      // Note: We could jump to the template if equal.

      assert(VM_Version::has_ExecuteExtensions(), "unsupported hardware");
      z_exrl(odd_reg, CLC_template);
      z_bre(Ldone_true);
      // fall through

      bind(Ldone_false);
      clear_reg(result);
      z_bru(Ldone);

      bind(CLC_template);
      z_clc(0, 0, str1_reg, 0, str2_reg);
    }

    // Use clcle instruction.
    {
      bind(Lclcle);
      add2reg(even_reg, base_offset, ary2); // string addr (right operand)
      add2reg(Z_R0, base_offset, ary1);     // string addr (left operand)

      z_lgr(Z_R1, odd_reg); // string len in bytes (left operand)
      if (is_byte) {
        compare_long_ext(Z_R0, even_reg, 0);
      } else {
        compare_long_uni(Z_R0, even_reg, 0);
      }
      z_lghi(result, 0); // Preserve flags.
      z_brne(Ldone);
    }
  }
  // fall through

  bind(Ldone_true);
  z_lghi(result, 1); // All characters are equal.
  bind(Ldone);

  BLOCK_COMMENT("} array_equals");

  return offset() - block_start;
}

// kill: haycnt, needlecnt, odd_reg, even_reg; early clobber: result
unsigned int MacroAssembler::string_indexof(Register result, Register haystack, Register haycnt,
                                            Register needle, Register needlecnt, int needlecntval,
                                            Register odd_reg, Register even_reg, int ae) {
  int block_start = offset();

  // Ensure 0<needlecnt<=haycnt in ideal graph as prerequisite!
  assert(ae != StrIntrinsicNode::LU, "Invalid encoding");
  const int h_csize = (ae == StrIntrinsicNode::LL) ? 1 : 2;
  const int n_csize = (ae == StrIntrinsicNode::UU) ? 2 : 1;
  Label L_needle1, L_Found, L_NotFound;

  BLOCK_COMMENT("string_indexof {");

  if (needle == haystack) {
    z_lhi(result, 0);
  } else {

  // Load first character of needle (R0 used by search_string instructions).
  if (n_csize == 2) { z_llgh(Z_R0, Address(needle)); } else { z_llgc(Z_R0, Address(needle)); }

  // Compute last haystack addr to use if no match gets found.
  if (needlecnt != noreg) { // variable needlecnt
    z_ahi(needlecnt, -1); // Remaining characters after first one.
    z_sr(haycnt, needlecnt); // Compute index succeeding last element to compare.
    if (n_csize == 2) { z_sll(needlecnt, 1); } // In bytes.
  } else { // constant needlecnt
    assert((needlecntval & 0x7fff) == needlecntval, "must be positive simm16 immediate");
    // Compute index succeeding last element to compare.
    if (needlecntval != 1) { z_ahi(haycnt, 1 - needlecntval); }
  }

  z_llgfr(haycnt, haycnt); // Clear high half.
  z_lgr(result, haystack); // Final result will be computed from needle start pointer.
  if (h_csize == 2) { z_sll(haycnt, 1); } // Scale to number of bytes.
  z_agr(haycnt, haystack); // Point to address succeeding last element (haystack+scale*(haycnt-needlecnt+1)).

  if (h_csize != n_csize) {
    assert(ae == StrIntrinsicNode::UL, "Invalid encoding");

    if (needlecnt != noreg || needlecntval != 1) {
      if (needlecnt != noreg) {
        compare32_and_branch(needlecnt, (intptr_t)0, Assembler::bcondEqual, L_needle1);
      }

      // Main Loop: UL version (now we have at least 2 characters).
      Label L_OuterLoop, L_InnerLoop, L_Skip;
      bind(L_OuterLoop); // Search for 1st 2 characters.
      z_lgr(Z_R1, haycnt);
      MacroAssembler::search_string_uni(Z_R1, result);
      z_brc(Assembler::bcondNotFound, L_NotFound);
      z_lgr(result, Z_R1);

      z_lghi(Z_R1, n_csize);
      z_lghi(even_reg, h_csize);
      bind(L_InnerLoop);
      z_llgc(odd_reg, Address(needle, Z_R1));
      z_ch(odd_reg, Address(result, even_reg));
      z_brne(L_Skip);
      if (needlecnt != noreg) { z_cr(Z_R1, needlecnt); } else { z_chi(Z_R1, needlecntval - 1); }
      z_brnl(L_Found);
      z_aghi(Z_R1, n_csize);
      z_aghi(even_reg, h_csize);
      z_bru(L_InnerLoop);

      bind(L_Skip);
      z_aghi(result, h_csize); // This is the new address we want to use for comparing.
      z_bru(L_OuterLoop);
    }

  } else {
    const intptr_t needle_bytes = (n_csize == 2) ? ((needlecntval - 1) << 1) : (needlecntval - 1);
    Label L_clcle;

    if (needlecnt != noreg || (needlecntval != 1 && needle_bytes <= 256)) {
      if (needlecnt != noreg) {
        compare32_and_branch(needlecnt, 256, Assembler::bcondHigh, L_clcle);
        z_ahi(needlecnt, -1); // remaining bytes -1 (for CLC)
        z_brl(L_needle1);
      }

      // Main Loop: clc version (now we have at least 2 characters).
      Label L_OuterLoop, CLC_template;
      bind(L_OuterLoop); // Search for 1st 2 characters.
      z_lgr(Z_R1, haycnt);
      if (h_csize == 1) {
        MacroAssembler::search_string(Z_R1, result);
      } else {
        MacroAssembler::search_string_uni(Z_R1, result);
      }
      z_brc(Assembler::bcondNotFound, L_NotFound);
      z_lgr(result, Z_R1);

      if (needlecnt != noreg) {
        assert(VM_Version::has_ExecuteExtensions(), "unsupported hardware");
        z_exrl(needlecnt, CLC_template);
      } else {
        z_clc(h_csize, needle_bytes -1, Z_R1, n_csize, needle);
      }
      z_bre(L_Found);
      z_aghi(result, h_csize); // This is the new address we want to use for comparing.
      z_bru(L_OuterLoop);

      if (needlecnt != noreg) {
        bind(CLC_template);
        z_clc(h_csize, 0, Z_R1, n_csize, needle);
      }
    }

    if (needlecnt != noreg || needle_bytes > 256) {
      bind(L_clcle);

      // Main Loop: clcle version (now we have at least 256 bytes).
      Label L_OuterLoop, CLC_template;
      bind(L_OuterLoop); // Search for 1st 2 characters.
      z_lgr(Z_R1, haycnt);
      if (h_csize == 1) {
        MacroAssembler::search_string(Z_R1, result);
      } else {
        MacroAssembler::search_string_uni(Z_R1, result);
      }
      z_brc(Assembler::bcondNotFound, L_NotFound);

      add2reg(Z_R0, n_csize, needle);
      add2reg(even_reg, h_csize, Z_R1);
      z_lgr(result, Z_R1);
      if (needlecnt != noreg) {
        z_llgfr(Z_R1, needlecnt); // needle len in bytes (left operand)
        z_llgfr(odd_reg, needlecnt);
      } else {
        load_const_optimized(Z_R1, needle_bytes);
        if (Immediate::is_simm16(needle_bytes)) { z_lghi(odd_reg, needle_bytes); } else { z_lgr(odd_reg, Z_R1); }
      }
      if (h_csize == 1) {
        compare_long_ext(Z_R0, even_reg, 0);
      } else {
        compare_long_uni(Z_R0, even_reg, 0);
      }
      z_bre(L_Found);

      if (n_csize == 2) { z_llgh(Z_R0, Address(needle)); } else { z_llgc(Z_R0, Address(needle)); } // Reload.
      z_aghi(result, h_csize); // This is the new address we want to use for comparing.
      z_bru(L_OuterLoop);
    }
  }

  if (needlecnt != noreg || needlecntval == 1) {
    bind(L_needle1);

    // Single needle character version.
    if (h_csize == 1) {
      MacroAssembler::search_string(haycnt, result);
    } else {
      MacroAssembler::search_string_uni(haycnt, result);
    }
    z_lgr(result, haycnt);
    z_brc(Assembler::bcondFound, L_Found);
  }

  bind(L_NotFound);
  add2reg(result, -1, haystack); // Return -1.

  bind(L_Found); // Return index (or -1 in fallthrough case).
  z_sgr(result, haystack);
  if (h_csize == 2) { z_srag(result, result, exact_log2(sizeof(jchar))); }
  }
  BLOCK_COMMENT("} string_indexof");

  return offset() - block_start;
}

// early clobber: result
unsigned int MacroAssembler::string_indexof_char(Register result, Register haystack, Register haycnt,
                                                 Register needle, jchar needleChar, Register odd_reg, Register even_reg, bool is_byte) {
  int block_start = offset();

  BLOCK_COMMENT("string_indexof_char {");

  if (needle == haystack) {
    z_lhi(result, 0);
  } else {

  Label Ldone;

  z_llgfr(odd_reg, haycnt);  // Preset loop ctr/searchrange end.
  if (needle == noreg) {
    load_const_optimized(Z_R0, (unsigned long)needleChar);
  } else {
    if (is_byte) {
      z_llgcr(Z_R0, needle); // First (and only) needle char.
    } else {
      z_llghr(Z_R0, needle); // First (and only) needle char.
    }
  }

  if (!is_byte) {
    z_agr(odd_reg, odd_reg); // Calc #bytes to be processed with SRSTU.
  }

  z_lgr(even_reg, haystack); // haystack addr
  z_agr(odd_reg, haystack);  // First char after range end.
  z_lghi(result, -1);

  if (is_byte) {
    MacroAssembler::search_string(odd_reg, even_reg);
  } else {
    MacroAssembler::search_string_uni(odd_reg, even_reg);
  }
  z_brc(Assembler::bcondNotFound, Ldone);
  if (is_byte) {
    if (VM_Version::has_DistinctOpnds()) {
      z_sgrk(result, odd_reg, haystack);
    } else {
      z_sgr(odd_reg, haystack);
      z_lgr(result, odd_reg);
    }
  } else {
    z_slgr(odd_reg, haystack);
    z_srlg(result, odd_reg, exact_log2(sizeof(jchar)));
  }

  bind(Ldone);
  }
  BLOCK_COMMENT("} string_indexof_char");

  return offset() - block_start;
}


//-------------------------------------------------
//   Constants (scalar and oop) in constant pool
//-------------------------------------------------

// Add a non-relocated constant to the CP.
int MacroAssembler::store_const_in_toc(AddressLiteral& val) {
  long    value  = val.value();
  address tocPos = long_constant(value);

  if (tocPos != NULL) {
    int tocOffset = (int)(tocPos - code()->consts()->start());
    return tocOffset;
  }
  // Address_constant returned NULL, so no constant entry has been created.
  // In that case, we return a "fatal" offset, just in case that subsequently
  // generated access code is executed.
  return -1;
}

// Returns the TOC offset where the address is stored.
// Add a relocated constant to the CP.
int MacroAssembler::store_oop_in_toc(AddressLiteral& oop) {
  // Use RelocationHolder::none for the constant pool entry.
  // Otherwise we will end up with a failing NativeCall::verify(x),
  // where x is the address of the constant pool entry.
  address tocPos = address_constant((address)oop.value(), RelocationHolder::none);

  if (tocPos != NULL) {
    int              tocOffset = (int)(tocPos - code()->consts()->start());
    RelocationHolder rsp = oop.rspec();
    Relocation      *rel = rsp.reloc();

    // Store toc_offset in relocation, used by call_far_patchable.
    if ((relocInfo::relocType)rel->type() == relocInfo::runtime_call_w_cp_type) {
      ((runtime_call_w_cp_Relocation *)(rel))->set_constant_pool_offset(tocOffset);
    }
    // Relocate at the load's pc.
    relocate(rsp);

    return tocOffset;
  }
  // Address_constant returned NULL, so no constant entry has been created
  // in that case, we return a "fatal" offset, just in case that subsequently
  // generated access code is executed.
  return -1;
}

bool MacroAssembler::load_const_from_toc(Register dst, AddressLiteral& a, Register Rtoc) {
  int     tocOffset = store_const_in_toc(a);
  if (tocOffset == -1) return false;
  address tocPos    = tocOffset + code()->consts()->start();
  assert((address)code()->consts()->start() != NULL, "Please add CP address");

  load_long_pcrelative(dst, tocPos);
  return true;
}

bool MacroAssembler::load_oop_from_toc(Register dst, AddressLiteral& a, Register Rtoc) {
  int     tocOffset = store_oop_in_toc(a);
  if (tocOffset == -1) return false;
  address tocPos    = tocOffset + code()->consts()->start();
  assert((address)code()->consts()->start() != NULL, "Please add CP address");

  load_addr_pcrelative(dst, tocPos);
  return true;
}

// If the instruction sequence at the given pc is a load_const_from_toc
// sequence, return the value currently stored at the referenced position
// in the TOC.
intptr_t MacroAssembler::get_const_from_toc(address pc) {

  assert(is_load_const_from_toc(pc), "must be load_const_from_pool");

  long    offset  = get_load_const_from_toc_offset(pc);
  address dataLoc = NULL;
  if (is_load_const_from_toc_pcrelative(pc)) {
    dataLoc = pc + offset;
  } else {
    CodeBlob* cb = CodeCache::find_blob_unsafe(pc);   // Else we get assertion if nmethod is zombie.
    assert(cb && cb->is_nmethod(), "sanity");
    nmethod* nm = (nmethod*)cb;
    dataLoc = nm->ctable_begin() + offset;
  }
  return *(intptr_t *)dataLoc;
}

// If the instruction sequence at the given pc is a load_const_from_toc
// sequence, copy the passed-in new_data value into the referenced
// position in the TOC.
void MacroAssembler::set_const_in_toc(address pc, unsigned long new_data, CodeBlob *cb) {
  assert(is_load_const_from_toc(pc), "must be load_const_from_pool");

  long    offset = MacroAssembler::get_load_const_from_toc_offset(pc);
  address dataLoc = NULL;
  if (is_load_const_from_toc_pcrelative(pc)) {
    dataLoc = pc+offset;
  } else {
    nmethod* nm = CodeCache::find_nmethod(pc);
    assert((cb == NULL) || (nm == (nmethod*)cb), "instruction address should be in CodeBlob");
    dataLoc = nm->ctable_begin() + offset;
  }
  if (*(unsigned long *)dataLoc != new_data) { // Prevent cache invalidation: update only if necessary.
    *(unsigned long *)dataLoc = new_data;
  }
}

// Dynamic TOC. Getter must only be called if "a" is a load_const_from_toc
// site. Verify by calling is_load_const_from_toc() before!!
// Offset is +/- 2**32 -> use long.
long MacroAssembler::get_load_const_from_toc_offset(address a) {
  assert(is_load_const_from_toc_pcrelative(a), "expected pc relative load");
  //  expected code sequence:
  //    z_lgrl(t, simm32);    len = 6
  unsigned long inst;
  unsigned int  len = get_instruction(a, &inst);
  return get_pcrel_offset(inst);
}

//**********************************************************************************
//  inspection of generated instruction sequences for a particular pattern
//**********************************************************************************

bool MacroAssembler::is_load_const_from_toc_pcrelative(address a) {
#ifdef ASSERT
  unsigned long inst;
  unsigned int  len = get_instruction(a+2, &inst);
  if ((len == 6) && is_load_pcrelative_long(a) && is_call_pcrelative_long(inst)) {
    const int range = 128;
    Assembler::dump_code_range(tty, a, range, "instr(a) == z_lgrl && instr(a+2) == z_brasl");
    VM_Version::z_SIGSEGV();
  }
#endif
  // expected code sequence:
  //   z_lgrl(t, relAddr32);    len = 6
  //TODO: verify accessed data is in CP, if possible.
  return is_load_pcrelative_long(a);  // TODO: might be too general. Currently, only lgrl is used.
}

bool MacroAssembler::is_load_const_from_toc_call(address a) {
  return is_load_const_from_toc(a) && is_call_byregister(a + load_const_from_toc_size());
}

bool MacroAssembler::is_load_const_call(address a) {
  return is_load_const(a) && is_call_byregister(a + load_const_size());
}

//-------------------------------------------------
//   Emitters for some really CICS instructions
//-------------------------------------------------

void MacroAssembler::move_long_ext(Register dst, Register src, unsigned int pad) {
  assert(dst->encoding()%2==0, "must be an even/odd register pair");
  assert(src->encoding()%2==0, "must be an even/odd register pair");
  assert(pad<256, "must be a padding BYTE");

  Label retry;
  bind(retry);
  Assembler::z_mvcle(dst, src, pad);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::compare_long_ext(Register left, Register right, unsigned int pad) {
  assert(left->encoding() % 2 == 0, "must be an even/odd register pair");
  assert(right->encoding() % 2 == 0, "must be an even/odd register pair");
  assert(pad<256, "must be a padding BYTE");

  Label retry;
  bind(retry);
  Assembler::z_clcle(left, right, pad, Z_R0);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::compare_long_uni(Register left, Register right, unsigned int pad) {
  assert(left->encoding() % 2 == 0, "must be an even/odd register pair");
  assert(right->encoding() % 2 == 0, "must be an even/odd register pair");
  assert(pad<=0xfff, "must be a padding HALFWORD");
  assert(VM_Version::has_ETF2(), "instruction must be available");

  Label retry;
  bind(retry);
  Assembler::z_clclu(left, right, pad, Z_R0);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::search_string(Register end, Register start) {
  assert(end->encoding() != 0, "end address must not be in R0");
  assert(start->encoding() != 0, "start address must not be in R0");

  Label retry;
  bind(retry);
  Assembler::z_srst(end, start);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::search_string_uni(Register end, Register start) {
  assert(end->encoding() != 0, "end address must not be in R0");
  assert(start->encoding() != 0, "start address must not be in R0");
  assert(VM_Version::has_ETF3(), "instruction must be available");

  Label retry;
  bind(retry);
  Assembler::z_srstu(end, start);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::kmac(Register srcBuff) {
  assert(srcBuff->encoding()     != 0, "src buffer address can't be in Z_R0");
  assert(srcBuff->encoding() % 2 == 0, "src buffer/len must be an even/odd register pair");

  Label retry;
  bind(retry);
  Assembler::z_kmac(Z_R0, srcBuff);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::kimd(Register srcBuff) {
  assert(srcBuff->encoding()     != 0, "src buffer address can't be in Z_R0");
  assert(srcBuff->encoding() % 2 == 0, "src buffer/len must be an even/odd register pair");

  Label retry;
  bind(retry);
  Assembler::z_kimd(Z_R0, srcBuff);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::klmd(Register srcBuff) {
  assert(srcBuff->encoding()     != 0, "src buffer address can't be in Z_R0");
  assert(srcBuff->encoding() % 2 == 0, "src buffer/len must be an even/odd register pair");

  Label retry;
  bind(retry);
  Assembler::z_klmd(Z_R0, srcBuff);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::km(Register dstBuff, Register srcBuff) {
  // DstBuff and srcBuff are allowed to be the same register (encryption in-place).
  // DstBuff and srcBuff storage must not overlap destructively, and neither must overlap the parameter block.
  assert(srcBuff->encoding()     != 0, "src buffer address can't be in Z_R0");
  assert(dstBuff->encoding() % 2 == 0, "dst buffer addr must be an even register");
  assert(srcBuff->encoding() % 2 == 0, "src buffer addr/len must be an even/odd register pair");

  Label retry;
  bind(retry);
  Assembler::z_km(dstBuff, srcBuff);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::kmc(Register dstBuff, Register srcBuff) {
  // DstBuff and srcBuff are allowed to be the same register (encryption in-place).
  // DstBuff and srcBuff storage must not overlap destructively, and neither must overlap the parameter block.
  assert(srcBuff->encoding()     != 0, "src buffer address can't be in Z_R0");
  assert(dstBuff->encoding() % 2 == 0, "dst buffer addr must be an even register");
  assert(srcBuff->encoding() % 2 == 0, "src buffer addr/len must be an even/odd register pair");

  Label retry;
  bind(retry);
  Assembler::z_kmc(dstBuff, srcBuff);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::cksm(Register crcBuff, Register srcBuff) {
  assert(srcBuff->encoding() % 2 == 0, "src buffer addr/len must be an even/odd register pair");

  Label retry;
  bind(retry);
  Assembler::z_cksm(crcBuff, srcBuff);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::translate_oo(Register r1, Register r2, uint m3) {
  assert(r1->encoding() % 2 == 0, "dst addr/src len must be an even/odd register pair");
  assert((m3 & 0b1110) == 0, "Unused mask bits must be zero");

  Label retry;
  bind(retry);
  Assembler::z_troo(r1, r2, m3);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::translate_ot(Register r1, Register r2, uint m3) {
  assert(r1->encoding() % 2 == 0, "dst addr/src len must be an even/odd register pair");
  assert((m3 & 0b1110) == 0, "Unused mask bits must be zero");

  Label retry;
  bind(retry);
  Assembler::z_trot(r1, r2, m3);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::translate_to(Register r1, Register r2, uint m3) {
  assert(r1->encoding() % 2 == 0, "dst addr/src len must be an even/odd register pair");
  assert((m3 & 0b1110) == 0, "Unused mask bits must be zero");

  Label retry;
  bind(retry);
  Assembler::z_trto(r1, r2, m3);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::translate_tt(Register r1, Register r2, uint m3) {
  assert(r1->encoding() % 2 == 0, "dst addr/src len must be an even/odd register pair");
  assert((m3 & 0b1110) == 0, "Unused mask bits must be zero");

  Label retry;
  bind(retry);
  Assembler::z_trtt(r1, r2, m3);
  Assembler::z_brc(Assembler::bcondOverflow /* CC==3 (iterate) */, retry);
}

void MacroAssembler::generate_safepoint_check(Label& slow_path, Register scratch, bool may_relocate) {
  if (scratch == noreg) scratch = Z_R1;
  address Astate = SafepointSynchronize::address_of_state();
  BLOCK_COMMENT("safepoint check:");

  if (may_relocate) {
    ptrdiff_t total_distance = Astate - this->pc();
    if (RelAddr::is_in_range_of_RelAddr32(total_distance)) {
      RelocationHolder rspec = external_word_Relocation::spec(Astate);
      (this)->relocate(rspec, relocInfo::pcrel_addr_format);
      load_absolute_address(scratch, Astate);
    } else {
      load_const_optimized(scratch, Astate);
    }
  } else {
    load_absolute_address(scratch, Astate);
  }
  z_cli(/*SafepointSynchronize::sz_state()*/4-1, scratch, SafepointSynchronize::_not_synchronized);
  z_brne(slow_path);
}


void MacroAssembler::generate_type_profiling(const Register Rdata,
                                             const Register Rreceiver_klass,
                                             const Register Rwanted_receiver_klass,
                                             const Register Rmatching_row,
                                             bool is_virtual_call) {
  const int row_size = in_bytes(ReceiverTypeData::receiver_offset(1)) -
                       in_bytes(ReceiverTypeData::receiver_offset(0));
  const int num_rows = ReceiverTypeData::row_limit();
  NearLabel found_free_row;
  NearLabel do_increment;
  NearLabel found_no_slot;

  BLOCK_COMMENT("type profiling {");

  // search for:
  //    a) The type given in Rwanted_receiver_klass.
  //    b) The *first* empty row.

  // First search for a) only, just running over b) with no regard.
  // This is possible because
  //    wanted_receiver_class == receiver_class  &&  wanted_receiver_class == 0
  // is never true (receiver_class can't be zero).
  for (int row_num = 0; row_num < num_rows; row_num++) {
    // Row_offset should be a well-behaved positive number. The generated code relies
    // on that wrt constant code size. Add2reg can handle all row_offset values, but
    // will have to vary generated code size.
    int row_offset = in_bytes(ReceiverTypeData::receiver_offset(row_num));
    assert(Displacement::is_shortDisp(row_offset), "Limitation of generated code");

    // Is Rwanted_receiver_klass in this row?
    if (VM_Version::has_CompareBranch()) {
      z_lg(Rwanted_receiver_klass, row_offset, Z_R0, Rdata);
      // Rmatching_row = Rdata + row_offset;
      add2reg(Rmatching_row, row_offset, Rdata);
      // if (*row_recv == (intptr_t) receiver_klass) goto fill_existing_slot;
      compare64_and_branch(Rwanted_receiver_klass, Rreceiver_klass, Assembler::bcondEqual, do_increment);
    } else {
      add2reg(Rmatching_row, row_offset, Rdata);
      z_cg(Rreceiver_klass, row_offset, Z_R0, Rdata);
      z_bre(do_increment);
    }
  }

  // Now that we did not find a match, let's search for b).

  // We could save the first calculation of Rmatching_row if we woud search for a) in reverse order.
  // We would then end up here with Rmatching_row containing the value for row_num == 0.
  // We would not see much benefit, if any at all, because the CPU can schedule
  // two instructions together with a branch anyway.
  for (int row_num = 0; row_num < num_rows; row_num++) {
    int row_offset = in_bytes(ReceiverTypeData::receiver_offset(row_num));

    // Has this row a zero receiver_klass, i.e. is it empty?
    if (VM_Version::has_CompareBranch()) {
      z_lg(Rwanted_receiver_klass, row_offset, Z_R0, Rdata);
      // Rmatching_row = Rdata + row_offset
      add2reg(Rmatching_row, row_offset, Rdata);
      // if (*row_recv == (intptr_t) 0) goto found_free_row
      compare64_and_branch(Rwanted_receiver_klass, (intptr_t)0, Assembler::bcondEqual, found_free_row);
    } else {
      add2reg(Rmatching_row, row_offset, Rdata);
      load_and_test_long(Rwanted_receiver_klass, Address(Rdata, row_offset));
      z_bre(found_free_row);  // zero -> Found a free row.
    }
  }

  // No match, no empty row found.
  // Increment total counter to indicate polymorphic case.
  if (is_virtual_call) {
    add2mem_64(Address(Rdata, CounterData::count_offset()), 1, Rmatching_row);
  }
  z_bru(found_no_slot);

  // Here we found an empty row, but we have not found Rwanted_receiver_klass.
  // Rmatching_row holds the address to the first empty row.
  bind(found_free_row);
  // Store receiver_klass into empty slot.
  z_stg(Rreceiver_klass, 0, Z_R0, Rmatching_row);

  // Increment the counter of Rmatching_row.
  bind(do_increment);
  ByteSize counter_offset = ReceiverTypeData::receiver_count_offset(0) - ReceiverTypeData::receiver_offset(0);
  add2mem_64(Address(Rmatching_row, counter_offset), 1, Rdata);

  bind(found_no_slot);

  BLOCK_COMMENT("} type profiling");
}

//---------------------------------------
// Helpers for Intrinsic Emitters
//---------------------------------------

/**
 * uint32_t crc;
 * timesXtoThe32[crc & 0xFF] ^ (crc >> 8);
 */
void MacroAssembler::fold_byte_crc32(Register crc, Register val, Register table, Register tmp) {
  assert_different_registers(crc, table, tmp);
  assert_different_registers(val, table);
  if (crc == val) {      // Must rotate first to use the unmodified value.
    rotate_then_insert(tmp, val, 56-2, 63-2, 2, true);  // Insert byte 7 of val, shifted left by 2, into byte 6..7 of tmp, clear the rest.
    z_srl(crc, 8);       // Unsigned shift, clear leftmost 8 bits.
  } else {
    z_srl(crc, 8);       // Unsigned shift, clear leftmost 8 bits.
    rotate_then_insert(tmp, val, 56-2, 63-2, 2, true);  // Insert byte 7 of val, shifted left by 2, into byte 6..7 of tmp, clear the rest.
  }
  z_x(crc, Address(table, tmp, 0));
}

/**
 * uint32_t crc;
 * timesXtoThe32[crc & 0xFF] ^ (crc >> 8);
 */
void MacroAssembler::fold_8bit_crc32(Register crc, Register table, Register tmp) {
  fold_byte_crc32(crc, crc, table, tmp);
}

/**
 * Emits code to update CRC-32 with a byte value according to constants in table.
 *
 * @param [in,out]crc Register containing the crc.
 * @param [in]val     Register containing the byte to fold into the CRC.
 * @param [in]table   Register containing the table of crc constants.
 *
 * uint32_t crc;
 * val = crc_table[(val ^ crc) & 0xFF];
 * crc = val ^ (crc >> 8);
 */
void MacroAssembler::update_byte_crc32(Register crc, Register val, Register table) {
  z_xr(val, crc);
  fold_byte_crc32(crc, val, table, val);
}


/**
 * @param crc   register containing existing CRC (32-bit)
 * @param buf   register pointing to input byte buffer (byte*)
 * @param len   register containing number of bytes
 * @param table register pointing to CRC table
 */
void MacroAssembler::update_byteLoop_crc32(Register crc, Register buf, Register len, Register table,
                                           Register data, bool invertCRC) {
  assert_different_registers(crc, buf, len, table, data);

  Label L_mainLoop, L_done;
  const int mainLoop_stepping = 1;

  // Process all bytes in a single-byte loop.
  z_ltr(len, len);
  z_brnh(L_done);

  if (invertCRC) {
    not_(crc, noreg, false); // ~c
  }

  bind(L_mainLoop);
    z_llgc(data, Address(buf, (intptr_t)0));// Current byte of input buffer (zero extended). Avoids garbage in upper half of register.
    add2reg(buf, mainLoop_stepping);        // Advance buffer position.
    update_byte_crc32(crc, data, table);
    z_brct(len, L_mainLoop);                // Iterate.

  if (invertCRC) {
    not_(crc, noreg, false); // ~c
  }

  bind(L_done);
}

/**
 * Emits code to update CRC-32 with a 4-byte value according to constants in table.
 * Implementation according to jdk/src/share/native/java/util/zip/zlib-1.2.8/crc32.c.
 *
 */
void MacroAssembler::update_1word_crc32(Register crc, Register buf, Register table, int bufDisp, int bufInc,
                                        Register t0,  Register t1,  Register t2,    Register t3) {
  // This is what we implement (the DOBIG4 part):
  //
  // #define DOBIG4 c ^= *++buf4; \
  //         c = crc_table[4][c & 0xff] ^ crc_table[5][(c >> 8) & 0xff] ^ \
  //             crc_table[6][(c >> 16) & 0xff] ^ crc_table[7][c >> 24]
  // #define DOBIG32 DOBIG4; DOBIG4; DOBIG4; DOBIG4; DOBIG4; DOBIG4; DOBIG4; DOBIG4
  const int ix0 = 4*(4*CRC32_COLUMN_SIZE);
  const int ix1 = 5*(4*CRC32_COLUMN_SIZE);
  const int ix2 = 6*(4*CRC32_COLUMN_SIZE);
  const int ix3 = 7*(4*CRC32_COLUMN_SIZE);

  // XOR crc with next four bytes of buffer.
  lgr_if_needed(t0, crc);
  z_x(t0, Address(buf, bufDisp));
  if (bufInc != 0) {
    add2reg(buf, bufInc);
  }

  // Chop crc into 4 single-byte pieces, shifted left 2 bits, to form the table indices.
  rotate_then_insert(t3, t0, 56-2, 63-2, 2,    true);  // ((c >>  0) & 0xff) << 2
  rotate_then_insert(t2, t0, 56-2, 63-2, 2-8,  true);  // ((c >>  8) & 0xff) << 2
  rotate_then_insert(t1, t0, 56-2, 63-2, 2-16, true);  // ((c >> 16) & 0xff) << 2
  rotate_then_insert(t0, t0, 56-2, 63-2, 2-24, true);  // ((c >> 24) & 0xff) << 2

  // Load pre-calculated table values.
  // Use columns 4..7 for big-endian.
  z_ly(t3, Address(table, t3, (intptr_t)ix0));
  z_ly(t2, Address(table, t2, (intptr_t)ix1));
  z_ly(t1, Address(table, t1, (intptr_t)ix2));
  z_ly(t0, Address(table, t0, (intptr_t)ix3));

  // Calculate new crc from table values.
  z_xr(t2, t3);
  z_xr(t0, t1);
  z_xr(t0, t2);  // Now crc contains the final checksum value.
  lgr_if_needed(crc, t0);
}

/**
 * @param crc   register containing existing CRC (32-bit)
 * @param buf   register pointing to input byte buffer (byte*)
 * @param len   register containing number of bytes
 * @param table register pointing to CRC table
 *
 * uses Z_R10..Z_R13 as work register. Must be saved/restored by caller!
 */
void MacroAssembler::kernel_crc32_2word(Register crc, Register buf, Register len, Register table,
                                        Register t0,  Register t1,  Register t2,  Register t3) {
  assert_different_registers(crc, buf, len, table);

  Label L_mainLoop, L_tail;
  Register  data = t0;
  Register  ctr  = Z_R0;
  const int mainLoop_stepping = 8;
  const int tailLoop_stepping = 1;
  const int log_stepping      = exact_log2(mainLoop_stepping);

  // Don't test for len <= 0 here. This pathological case should not occur anyway.
  // Optimizing for it by adding a test and a branch seems to be a waste of CPU cycles.
  // The situation itself is detected and handled correctly by the conditional branches
  // following aghi(len, -stepping) and aghi(len, +stepping).

  not_(crc, noreg, false);             // 1s complement of crc

#if 0
  {
    // Pre-mainLoop alignment did not show any positive effect on performance.
    // We leave the code in for reference. Maybe the vector instructions in z13 depend on alignment.

    z_cghi(len, mainLoop_stepping);    // Alignment is useless for short data streams.
    z_brnh(L_tail);

    // Align buf to word (4-byte) boundary.
    z_lcr(ctr, buf);
    rotate_then_insert(ctr, ctr, 62, 63, 0, true); // TODO: should set cc
    z_sgfr(len, ctr);                  // Remaining len after alignment.

    update_byteLoop_crc32(crc, buf, ctr, table, data, false);
  }
#endif

  // Check for short (<mainLoop_stepping bytes) buffer.
  z_srag(ctr, len, log_stepping);
  z_brnh(L_tail);

  z_lrvr(crc, crc);             // Revert byte order because we are dealing with big-endian data.
  rotate_then_insert(len, len, 64-log_stepping, 63, 0, true); // #bytes for tailLoop

  BIND(L_mainLoop);
    update_1word_crc32(crc, buf, table, 0, 0, crc, t1, t2, t3);
    update_1word_crc32(crc, buf, table, 4, mainLoop_stepping, crc, t1, t2, t3);
    z_brct(ctr, L_mainLoop);    // Iterate.

  z_lrvr(crc, crc);        // Revert byte order back to original.

  // Process last few (<8) bytes of buffer.
  BIND(L_tail);
  update_byteLoop_crc32(crc, buf, len, table, data, false);

  not_(crc, noreg, false); // 1s complement of crc
}

/**
 * @param crc   register containing existing CRC (32-bit)
 * @param buf   register pointing to input byte buffer (byte*)
 * @param len   register containing number of bytes
 * @param table register pointing to CRC table
 *
 * uses Z_R10..Z_R13 as work register. Must be saved/restored by caller!
 */
void MacroAssembler::kernel_crc32_1word(Register crc, Register buf, Register len, Register table,
                                        Register t0,  Register t1,  Register t2,  Register t3) {
  assert_different_registers(crc, buf, len, table);

  Label L_mainLoop, L_tail;
  Register  data = t0;
  Register  ctr  = Z_R0;
  const int mainLoop_stepping = 4;
  const int log_stepping      = exact_log2(mainLoop_stepping);

  // Don't test for len <= 0 here. This pathological case should not occur anyway.
  // Optimizing for it by adding a test and a branch seems to be a waste of CPU cycles.
  // The situation itself is detected and handled correctly by the conditional branches
  // following aghi(len, -stepping) and aghi(len, +stepping).

  not_(crc, noreg, false); // 1s complement of crc

  // Check for short (<4 bytes) buffer.
  z_srag(ctr, len, log_stepping);
  z_brnh(L_tail);

  z_lrvr(crc, crc);          // Revert byte order because we are dealing with big-endian data.
  rotate_then_insert(len, len, 64-log_stepping, 63, 0, true); // #bytes for tailLoop

  BIND(L_mainLoop);
    update_1word_crc32(crc, buf, table, 0, mainLoop_stepping, crc, t1, t2, t3);
    z_brct(ctr, L_mainLoop); // Iterate.
  z_lrvr(crc, crc);          // Revert byte order back to original.

  // Process last few (<8) bytes of buffer.
  BIND(L_tail);
  update_byteLoop_crc32(crc, buf, len, table, data, false);

  not_(crc, noreg, false); // 1s complement of crc
}

/**
 * @param crc   register containing existing CRC (32-bit)
 * @param buf   register pointing to input byte buffer (byte*)
 * @param len   register containing number of bytes
 * @param table register pointing to CRC table
 */
void MacroAssembler::kernel_crc32_1byte(Register crc, Register buf, Register len, Register table,
                                        Register t0,  Register t1,  Register t2,  Register t3) {
  assert_different_registers(crc, buf, len, table);
  Register data = t0;

  update_byteLoop_crc32(crc, buf, len, table, data, true);
}

void MacroAssembler::kernel_crc32_singleByte(Register crc, Register buf, Register len, Register table, Register tmp) {
  assert_different_registers(crc, buf, len, table, tmp);

  not_(crc, noreg, false); // ~c

  z_llgc(tmp, Address(buf, (intptr_t)0));  // Current byte of input buffer (zero extended). Avoids garbage in upper half of register.
  update_byte_crc32(crc, tmp, table);

  not_(crc, noreg, false); // ~c
}

//
// Code for BigInteger::multiplyToLen() intrinsic.
//

// dest_lo += src1 + src2
// dest_hi += carry1 + carry2
// Z_R7 is destroyed !
void MacroAssembler::add2_with_carry(Register dest_hi, Register dest_lo,
                                     Register src1, Register src2) {
  clear_reg(Z_R7);
  z_algr(dest_lo, src1);
  z_alcgr(dest_hi, Z_R7);
  z_algr(dest_lo, src2);
  z_alcgr(dest_hi, Z_R7);
}

// Multiply 64 bit by 64 bit first loop.
void MacroAssembler::multiply_64_x_64_loop(Register x, Register xstart,
                                           Register x_xstart,
                                           Register y, Register y_idx,
                                           Register z,
                                           Register carry,
                                           Register product,
                                           Register idx, Register kdx) {
  // jlong carry, x[], y[], z[];
  // for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx--, kdx--) {
  //   huge_128 product = y[idx] * x[xstart] + carry;
  //   z[kdx] = (jlong)product;
  //   carry  = (jlong)(product >>> 64);
  // }
  // z[xstart] = carry;

  Label L_first_loop, L_first_loop_exit;
  Label L_one_x, L_one_y, L_multiply;

  z_aghi(xstart, -1);
  z_brl(L_one_x);   // Special case: length of x is 1.

  // Load next two integers of x.
  z_sllg(Z_R1_scratch, xstart, LogBytesPerInt);
  mem2reg_opt(x_xstart, Address(x, Z_R1_scratch, 0));


  bind(L_first_loop);

  z_aghi(idx, -1);
  z_brl(L_first_loop_exit);
  z_aghi(idx, -1);
  z_brl(L_one_y);

  // Load next two integers of y.
  z_sllg(Z_R1_scratch, idx, LogBytesPerInt);
  mem2reg_opt(y_idx, Address(y, Z_R1_scratch, 0));


  bind(L_multiply);

  Register multiplicand = product->successor();
  Register product_low = multiplicand;

  lgr_if_needed(multiplicand, x_xstart);
  z_mlgr(product, y_idx);     // multiplicand * y_idx -> product::multiplicand
  clear_reg(Z_R7);
  z_algr(product_low, carry); // Add carry to result.
  z_alcgr(product, Z_R7);     // Add carry of the last addition.
  add2reg(kdx, -2);

  // Store result.
  z_sllg(Z_R7, kdx, LogBytesPerInt);
  reg2mem_opt(product_low, Address(z, Z_R7, 0));
  lgr_if_needed(carry, product);
  z_bru(L_first_loop);


  bind(L_one_y); // Load one 32 bit portion of y as (0,value).

  clear_reg(y_idx);
  mem2reg_opt(y_idx, Address(y, (intptr_t) 0), false);
  z_bru(L_multiply);


  bind(L_one_x); // Load one 32 bit portion of x as (0,value).

  clear_reg(x_xstart);
  mem2reg_opt(x_xstart, Address(x, (intptr_t) 0), false);
  z_bru(L_first_loop);

  bind(L_first_loop_exit);
}

// Multiply 64 bit by 64 bit and add 128 bit.
void MacroAssembler::multiply_add_128_x_128(Register x_xstart, Register y,
                                            Register z,
                                            Register yz_idx, Register idx,
                                            Register carry, Register product,
                                            int offset) {
  // huge_128 product = (y[idx] * x_xstart) + z[kdx] + carry;
  // z[kdx] = (jlong)product;

  Register multiplicand = product->successor();
  Register product_low = multiplicand;

  z_sllg(Z_R7, idx, LogBytesPerInt);
  mem2reg_opt(yz_idx, Address(y, Z_R7, offset));

  lgr_if_needed(multiplicand, x_xstart);
  z_mlgr(product, yz_idx); // multiplicand * yz_idx -> product::multiplicand
  mem2reg_opt(yz_idx, Address(z, Z_R7, offset));

  add2_with_carry(product, product_low, carry, yz_idx);

  z_sllg(Z_R7, idx, LogBytesPerInt);
  reg2mem_opt(product_low, Address(z, Z_R7, offset));

}

// Multiply 128 bit by 128 bit. Unrolled inner loop.
void MacroAssembler::multiply_128_x_128_loop(Register x_xstart,
                                             Register y, Register z,
                                             Register yz_idx, Register idx,
                                             Register jdx,
                                             Register carry, Register product,
                                             Register carry2) {
  // jlong carry, x[], y[], z[];
  // int kdx = ystart+1;
  // for (int idx=ystart-2; idx >= 0; idx -= 2) { // Third loop
  //   huge_128 product = (y[idx+1] * x_xstart) + z[kdx+idx+1] + carry;
  //   z[kdx+idx+1] = (jlong)product;
  //   jlong carry2 = (jlong)(product >>> 64);
  //   product = (y[idx] * x_xstart) + z[kdx+idx] + carry2;
  //   z[kdx+idx] = (jlong)product;
  //   carry = (jlong)(product >>> 64);
  // }
  // idx += 2;
  // if (idx > 0) {
  //   product = (y[idx] * x_xstart) + z[kdx+idx] + carry;
  //   z[kdx+idx] = (jlong)product;
  //   carry = (jlong)(product >>> 64);
  // }

  Label L_third_loop, L_third_loop_exit, L_post_third_loop_done;

  // scale the index
  lgr_if_needed(jdx, idx);
  and_imm(jdx, 0xfffffffffffffffcL);
  rshift(jdx, 2);


  bind(L_third_loop);

  z_aghi(jdx, -1);
  z_brl(L_third_loop_exit);
  add2reg(idx, -4);

  multiply_add_128_x_128(x_xstart, y, z, yz_idx, idx, carry, product, 8);
  lgr_if_needed(carry2, product);

  multiply_add_128_x_128(x_xstart, y, z, yz_idx, idx, carry2, product, 0);
  lgr_if_needed(carry, product);
  z_bru(L_third_loop);


  bind(L_third_loop_exit);  // Handle any left-over operand parts.

  and_imm(idx, 0x3);
  z_brz(L_post_third_loop_done);

  Label L_check_1;

  z_aghi(idx, -2);
  z_brl(L_check_1);

  multiply_add_128_x_128(x_xstart, y, z, yz_idx, idx, carry, product, 0);
  lgr_if_needed(carry, product);


  bind(L_check_1);

  add2reg(idx, 0x2);
  and_imm(idx, 0x1);
  z_aghi(idx, -1);
  z_brl(L_post_third_loop_done);

  Register   multiplicand = product->successor();
  Register   product_low = multiplicand;

  z_sllg(Z_R7, idx, LogBytesPerInt);
  clear_reg(yz_idx);
  mem2reg_opt(yz_idx, Address(y, Z_R7, 0), false);
  lgr_if_needed(multiplicand, x_xstart);
  z_mlgr(product, yz_idx); // multiplicand * yz_idx -> product::multiplicand
  clear_reg(yz_idx);
  mem2reg_opt(yz_idx, Address(z, Z_R7, 0), false);

  add2_with_carry(product, product_low, yz_idx, carry);

  z_sllg(Z_R7, idx, LogBytesPerInt);
  reg2mem_opt(product_low, Address(z, Z_R7, 0), false);
  rshift(product_low, 32);

  lshift(product, 32);
  z_ogr(product_low, product);
  lgr_if_needed(carry, product_low);

  bind(L_post_third_loop_done);
}

void MacroAssembler::multiply_to_len(Register x, Register xlen,
                                     Register y, Register ylen,
                                     Register z,
                                     Register tmp1, Register tmp2,
                                     Register tmp3, Register tmp4,
                                     Register tmp5) {
  ShortBranchVerifier sbv(this);

  assert_different_registers(x, xlen, y, ylen, z,
                             tmp1, tmp2, tmp3, tmp4, tmp5, Z_R1_scratch, Z_R7);
  assert_different_registers(x, xlen, y, ylen, z,
                             tmp1, tmp2, tmp3, tmp4, tmp5, Z_R8);

  z_stmg(Z_R7, Z_R13, _z_abi(gpr7), Z_SP);

  // In openJdk, we store the argument as 32-bit value to slot.
  Address zlen(Z_SP, _z_abi(remaining_cargs));  // Int in long on big endian.

  const Register idx = tmp1;
  const Register kdx = tmp2;
  const Register xstart = tmp3;

  const Register y_idx = tmp4;
  const Register carry = tmp5;
  const Register product  = Z_R0_scratch;
  const Register x_xstart = Z_R8;

  // First Loop.
  //
  //   final static long LONG_MASK = 0xffffffffL;
  //   int xstart = xlen - 1;
  //   int ystart = ylen - 1;
  //   long carry = 0;
  //   for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx-, kdx--) {
  //     long product = (y[idx] & LONG_MASK) * (x[xstart] & LONG_MASK) + carry;
  //     z[kdx] = (int)product;
  //     carry = product >>> 32;
  //   }
  //   z[xstart] = (int)carry;
  //

  lgr_if_needed(idx, ylen);  // idx = ylen
  z_llgf(kdx, zlen);         // C2 does not respect int to long conversion for stub calls, thus load zero-extended.
  clear_reg(carry);          // carry = 0

  Label L_done;

  lgr_if_needed(xstart, xlen);
  z_aghi(xstart, -1);
  z_brl(L_done);

  multiply_64_x_64_loop(x, xstart, x_xstart, y, y_idx, z, carry, product, idx, kdx);

  NearLabel L_second_loop;
  compare64_and_branch(kdx, RegisterOrConstant((intptr_t) 0), bcondEqual, L_second_loop);

  NearLabel L_carry;
  z_aghi(kdx, -1);
  z_brz(L_carry);

  // Store lower 32 bits of carry.
  z_sllg(Z_R1_scratch, kdx, LogBytesPerInt);
  reg2mem_opt(carry, Address(z, Z_R1_scratch, 0), false);
  rshift(carry, 32);
  z_aghi(kdx, -1);


  bind(L_carry);

  // Store upper 32 bits of carry.
  z_sllg(Z_R1_scratch, kdx, LogBytesPerInt);
  reg2mem_opt(carry, Address(z, Z_R1_scratch, 0), false);

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
  // i = xlen, j = tmp1, k = tmp2, carry = tmp5, x[i] = rdx

  const Register jdx = tmp1;

  bind(L_second_loop);

  clear_reg(carry);           // carry = 0;
  lgr_if_needed(jdx, ylen);   // j = ystart+1

  z_aghi(xstart, -1);         // i = xstart-1;
  z_brl(L_done);

  // Use free slots in the current stackframe instead of push/pop.
  Address zsave(Z_SP, _z_abi(carg_1));
  reg2mem_opt(z, zsave);


  Label L_last_x;

  z_sllg(Z_R1_scratch, xstart, LogBytesPerInt);
  load_address(z, Address(z, Z_R1_scratch, 4)); // z = z + k - j
  z_aghi(xstart, -1);                           // i = xstart-1;
  z_brl(L_last_x);

  z_sllg(Z_R1_scratch, xstart, LogBytesPerInt);
  mem2reg_opt(x_xstart, Address(x, Z_R1_scratch, 0));


  Label L_third_loop_prologue;

  bind(L_third_loop_prologue);

  Address xsave(Z_SP, _z_abi(carg_2));
  Address xlensave(Z_SP, _z_abi(carg_3));
  Address ylensave(Z_SP, _z_abi(carg_4));

  reg2mem_opt(x, xsave);
  reg2mem_opt(xstart, xlensave);
  reg2mem_opt(ylen, ylensave);


  multiply_128_x_128_loop(x_xstart, y, z, y_idx, jdx, ylen, carry, product, x);

  mem2reg_opt(z, zsave);
  mem2reg_opt(x, xsave);
  mem2reg_opt(xlen, xlensave);   // This is the decrement of the loop counter!
  mem2reg_opt(ylen, ylensave);

  add2reg(tmp3, 1, xlen);
  z_sllg(Z_R1_scratch, tmp3, LogBytesPerInt);
  reg2mem_opt(carry, Address(z, Z_R1_scratch, 0), false);
  z_aghi(tmp3, -1);
  z_brl(L_done);

  rshift(carry, 32);
  z_sllg(Z_R1_scratch, tmp3, LogBytesPerInt);
  reg2mem_opt(carry, Address(z, Z_R1_scratch, 0), false);
  z_bru(L_second_loop);

  // Next infrequent code is moved outside loops.
  bind(L_last_x);

  clear_reg(x_xstart);
  mem2reg_opt(x_xstart, Address(x, (intptr_t) 0), false);
  z_bru(L_third_loop_prologue);

  bind(L_done);

  z_lmg(Z_R7, Z_R13, _z_abi(gpr7), Z_SP);
}

#ifndef PRODUCT
// Assert if CC indicates "not equal" (check_equal==true) or "equal" (check_equal==false).
void MacroAssembler::asm_assert(bool check_equal, const char *msg, int id) {
  Label ok;
  if (check_equal) {
    z_bre(ok);
  } else {
    z_brne(ok);
  }
  stop(msg, id);
  bind(ok);
}

// Assert if CC indicates "low".
void MacroAssembler::asm_assert_low(const char *msg, int id) {
  Label ok;
  z_brnl(ok);
  stop(msg, id);
  bind(ok);
}

// Assert if CC indicates "high".
void MacroAssembler::asm_assert_high(const char *msg, int id) {
  Label ok;
  z_brnh(ok);
  stop(msg, id);
  bind(ok);
}

// Assert if CC indicates "not equal" (check_equal==true) or "equal" (check_equal==false)
// generate non-relocatable code.
void MacroAssembler::asm_assert_static(bool check_equal, const char *msg, int id) {
  Label ok;
  if (check_equal) { z_bre(ok); }
  else             { z_brne(ok); }
  stop_static(msg, id);
  bind(ok);
}

void MacroAssembler::asm_assert_mems_zero(bool check_equal, bool allow_relocation, int size, int64_t mem_offset,
                                          Register mem_base, const char* msg, int id) {
  switch (size) {
    case 4:
      load_and_test_int(Z_R0, Address(mem_base, mem_offset));
      break;
    case 8:
      load_and_test_long(Z_R0,  Address(mem_base, mem_offset));
      break;
    default:
      ShouldNotReachHere();
  }
  if (allow_relocation) { asm_assert(check_equal, msg, id); }
  else                  { asm_assert_static(check_equal, msg, id); }
}

// Check the condition
//   expected_size == FP - SP
// after transformation:
//   expected_size - FP + SP == 0
// Destroys Register expected_size if no tmp register is passed.
void MacroAssembler::asm_assert_frame_size(Register expected_size, Register tmp, const char* msg, int id) {
  if (tmp == noreg) {
    tmp = expected_size;
  } else {
    if (tmp != expected_size) {
      z_lgr(tmp, expected_size);
    }
    z_algr(tmp, Z_SP);
    z_slg(tmp, 0, Z_R0, Z_SP);
    asm_assert_eq(msg, id);
  }
}
#endif // !PRODUCT

void MacroAssembler::verify_thread() {
  if (VerifyThread) {
    unimplemented("", 117);
  }
}

// Plausibility check for oops.
void MacroAssembler::verify_oop(Register oop, const char* msg) {
  if (!VerifyOops) return;

  BLOCK_COMMENT("verify_oop {");
  Register tmp = Z_R0;
  unsigned int nbytes_save = 6 *8;
  address entry = StubRoutines::verify_oop_subroutine_entry_address();
  save_return_pc();
  push_frame_abi160(nbytes_save);
  z_stmg(Z_R0, Z_R5, 160, Z_SP);

  z_lgr(Z_ARG2, oop);
  load_const(Z_ARG1, (address) msg);
  load_const(Z_R1, entry);
  z_lg(Z_R1, 0, Z_R1);
  call_c(Z_R1);

  z_lmg(Z_R0, Z_R5, 160, Z_SP);
  pop_frame();

  restore_return_pc();
  BLOCK_COMMENT("} verify_oop ");
}

const char* MacroAssembler::stop_types[] = {
  "stop",
  "untested",
  "unimplemented",
  "shouldnotreachhere"
};

static void stop_on_request(const char* tp, const char* msg) {
  tty->print("Z assembly code requires stop: (%s) %s\n", tp, msg);
  guarantee(false, "Z assembly code requires stop: %s", msg);
}

void MacroAssembler::stop(int type, const char* msg, int id) {
  BLOCK_COMMENT(err_msg("stop: %s {", msg));

  // Setup arguments.
  load_const(Z_ARG1, (void*) stop_types[type%stop_end]);
  load_const(Z_ARG2, (void*) msg);
  get_PC(Z_R14); // Following code pushes a frame without entering a new function. Use current pc as return address.
  save_return_pc();    // Saves return pc Z_R14.
  push_frame_abi160(0);
  call_VM_leaf(CAST_FROM_FN_PTR(address, stop_on_request), Z_ARG1, Z_ARG2);
  // The plain disassembler does not recognize illtrap. It instead displays
  // a 32-bit value. Issueing two illtraps assures the disassembler finds
  // the proper beginning of the next instruction.
  z_illtrap(); // Illegal instruction.
  z_illtrap(); // Illegal instruction.

  BLOCK_COMMENT(" } stop");
}

// Special version of stop() for code size reduction.
// Reuses the previously generated call sequence, if any.
// Generates the call sequence on its own, if necessary.
// Note: This code will work only in non-relocatable code!
//       The relative address of the data elements (arg1, arg2) must not change.
//       The reentry point must not move relative to it's users. This prerequisite
//       should be given for "hand-written" code, if all chain calls are in the same code blob.
//       Generated code must not undergo any transformation, e.g. ShortenBranches, to be safe.
address MacroAssembler::stop_chain(address reentry, int type, const char* msg, int id, bool allow_relocation) {
  BLOCK_COMMENT(err_msg("stop_chain(%s,%s): %s {", reentry==NULL?"init":"cont", allow_relocation?"reloc ":"static", msg));

  // Setup arguments.
  if (allow_relocation) {
    // Relocatable version (for comparison purposes). Remove after some time.
    load_const(Z_ARG1, (void*) stop_types[type%stop_end]);
    load_const(Z_ARG2, (void*) msg);
  } else {
    load_absolute_address(Z_ARG1, (address)stop_types[type%stop_end]);
    load_absolute_address(Z_ARG2, (address)msg);
  }
  if ((reentry != NULL) && RelAddr::is_in_range_of_RelAddr16(reentry, pc())) {
    BLOCK_COMMENT("branch to reentry point:");
    z_brc(bcondAlways, reentry);
  } else {
    BLOCK_COMMENT("reentry point:");
    reentry = pc();      // Re-entry point for subsequent stop calls.
    save_return_pc();    // Saves return pc Z_R14.
    push_frame_abi160(0);
    if (allow_relocation) {
      reentry = NULL;    // Prevent reentry if code relocation is allowed.
      call_VM_leaf(CAST_FROM_FN_PTR(address, stop_on_request), Z_ARG1, Z_ARG2);
    } else {
      call_VM_leaf_static(CAST_FROM_FN_PTR(address, stop_on_request), Z_ARG1, Z_ARG2);
    }
    z_illtrap(); // Illegal instruction as emergency stop, should the above call return.
  }
  BLOCK_COMMENT(" } stop_chain");

  return reentry;
}

// Special version of stop() for code size reduction.
// Assumes constant relative addresses for data and runtime call.
void MacroAssembler::stop_static(int type, const char* msg, int id) {
  stop_chain(NULL, type, msg, id, false);
}

void MacroAssembler::stop_subroutine() {
  unimplemented("stop_subroutine", 710);
}

// Prints msg to stdout from within generated code..
void MacroAssembler::warn(const char* msg) {
  RegisterSaver::save_live_registers(this, RegisterSaver::all_registers, Z_R14);
  load_absolute_address(Z_R1, (address) warning);
  load_absolute_address(Z_ARG1, (address) msg);
  (void) call(Z_R1);
  RegisterSaver::restore_live_registers(this, RegisterSaver::all_registers);
}

#ifndef PRODUCT

// Write pattern 0x0101010101010101 in region [low-before, high+after].
void MacroAssembler::zap_from_to(Register low, Register high, Register val, Register addr, int before, int after) {
  if (!ZapEmptyStackFields) return;
  BLOCK_COMMENT("zap memory region {");
  load_const_optimized(val, 0x0101010101010101);
  int size = before + after;
  if (low == high && size < 5 && size > 0) {
    int offset = -before*BytesPerWord;
    for (int i = 0; i < size; ++i) {
      z_stg(val, Address(low, offset));
      offset +=(1*BytesPerWord);
    }
  } else {
    add2reg(addr, -before*BytesPerWord, low);
    if (after) {
#ifdef ASSERT
      jlong check = after * BytesPerWord;
      assert(Immediate::is_simm32(check) && Immediate::is_simm32(-check), "value not encodable !");
#endif
      add2reg(high, after * BytesPerWord);
    }
    NearLabel loop;
    bind(loop);
    z_stg(val, Address(addr));
    add2reg(addr, 8);
    compare64_and_branch(addr, high, bcondNotHigh, loop);
    if (after) {
      add2reg(high, -after * BytesPerWord);
    }
  }
  BLOCK_COMMENT("} zap memory region");
}
#endif // !PRODUCT

SkipIfEqual::SkipIfEqual(MacroAssembler* masm, const bool* flag_addr, bool value, Register _rscratch) {
  _masm = masm;
  _masm->load_absolute_address(_rscratch, (address)flag_addr);
  _masm->load_and_test_int(_rscratch, Address(_rscratch));
  if (value) {
    _masm->z_brne(_label); // Skip if true, i.e. != 0.
  } else {
    _masm->z_bre(_label);  // Skip if false, i.e. == 0.
  }
}

SkipIfEqual::~SkipIfEqual() {
  _masm->bind(_label);
}
