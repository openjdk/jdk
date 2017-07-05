/*
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef _DECODE_H
#define _DECODE_H

#include <sys/types.h>
#include "cpustate_aarch64.hpp"

// bitfield immediate expansion helper

extern int expandLogicalImmediate(u_int32_t immN, u_int32_t immr,
                                    u_int32_t imms, u_int64_t &bimm);


/*
 * codes used in conditional instructions
 *
 * these are passed to conditional operations to identify which
 * condition to test for
 */
enum CondCode {
  EQ = 0b0000, // meaning Z == 1
  NE = 0b0001, // meaning Z == 0
  HS = 0b0010, // meaning C == 1
  CS = HS,
  LO = 0b0011, // meaning C == 0
  CC = LO,
  MI = 0b0100, // meaning N == 1
  PL = 0b0101, // meaning N == 0
  VS = 0b0110, // meaning V == 1
  VC = 0b0111, // meaning V == 0
  HI = 0b1000, // meaning C == 1 && Z == 0
  LS = 0b1001, // meaning !(C == 1 && Z == 0)
  GE = 0b1010, // meaning N == V
  LT = 0b1011, // meaning N != V
  GT = 0b1100, // meaning Z == 0 && N == V
  LE = 0b1101, // meaning !(Z == 0 && N == V)
  AL = 0b1110, // meaning ANY
  NV = 0b1111  // ditto
};

/*
 * certain addressing modes for load require pre or post writeback of
 * the computed address to a base register
 */
enum WriteBack {
  Post = 0,
  Pre = 1
};

/*
 * certain addressing modes for load require an offset to
 * be optionally scaled so the decode needs to pass that
 * through to the execute routine
 */
enum Scaling {
  Unscaled = 0,
  Scaled = 1
};

/*
 * when we do have to scale we do so by shifting using
 * log(bytes in data element - 1) as the shift count.
 * so we don't have to scale offsets when loading
 * bytes.
 */
enum ScaleShift {
  ScaleShift16 = 1,
  ScaleShift32 = 2,
  ScaleShift64 = 3,
  ScaleShift128 = 4
};

/*
 * one of the addressing modes for load requires a 32-bit register
 * value to be either zero- or sign-extended for these instructions
 * UXTW or SXTW should be passed
 *
 * arithmetic register data processing operations can optionally
 * extend a portion of the second register value for these
 * instructions the value supplied must identify the portion of the
 * register which is to be zero- or sign-exended
 */
enum Extension {
  UXTB = 0,
  UXTH = 1,
  UXTW = 2,
  UXTX = 3,
  SXTB = 4,
  SXTH = 5,
  SXTW = 6,
  SXTX = 7
};

/*
 * arithmetic and logical register data processing operations
 * optionally perform a shift on the second register value
 */
enum Shift {
  LSL = 0,
  LSR = 1,
  ASR = 2,
  ROR = 3
};

/*
 * bit twiddling helpers for instruction decode
 */

// 32 bit mask with bits [hi,...,lo] set

static inline u_int32_t mask32(int hi = 31, int lo = 0)
{
  int nbits = (hi + 1) - lo;
  return ((1 << nbits) - 1) << lo;
}

static inline u_int64_t mask64(int hi = 63, int lo = 0)
{
  int nbits = (hi + 1) - lo;
  return ((1L << nbits) - 1) << lo;
}

// pick bits [hi,...,lo] from val
static inline u_int32_t pick32(u_int32_t val, int hi = 31, int lo = 0)
{
  return (val & mask32(hi, lo));
}

// pick bits [hi,...,lo] from val
static inline u_int64_t pick64(u_int64_t val, int hi = 31, int lo = 0)
{
  return (val & mask64(hi, lo));
}

// pick bits [hi,...,lo] from val and shift to [(hi-(newlo - lo)),newlo]
static inline u_int32_t pickshift32(u_int32_t val, int hi = 31,
                                    int lo = 0, int newlo = 0)
{
  u_int32_t bits = pick32(val, hi, lo);
  if (lo < newlo) {
    return (bits << (newlo - lo));
  } else {
    return (bits >> (lo - newlo));
  }
}
// mask [hi,lo] and shift down to start at bit 0
static inline u_int32_t pickbits32(u_int32_t val, int hi = 31, int lo = 0)
{
  return (pick32(val, hi, lo) >> lo);
}

// mask [hi,lo] and shift down to start at bit 0
static inline u_int64_t pickbits64(u_int64_t val, int hi = 63, int lo = 0)
{
  return (pick64(val, hi, lo) >> lo);
}

/*
 * decode registers, immediates and constants of various types
 */

static inline GReg greg(u_int32_t val, int lo)
{
  return (GReg)pickbits32(val, lo + 4, lo);
}

static inline VReg vreg(u_int32_t val, int lo)
{
  return (VReg)pickbits32(val, lo + 4, lo);
}

static inline u_int32_t uimm(u_int32_t val, int hi, int lo)
{
  return pickbits32(val, hi, lo);
}

static inline int32_t simm(u_int32_t val, int hi = 31, int lo = 0) {
  union {
    u_int32_t u;
    int32_t n;
  };

  u = val << (31 - hi);
  n = n >> (31 - hi + lo);
  return n;
}

static inline int64_t simm(u_int64_t val, int hi = 63, int lo = 0) {
  union {
    u_int64_t u;
    int64_t n;
  };

  u = val << (63 - hi);
  n = n >> (63 - hi + lo);
  return n;
}

static inline Shift shift(u_int32_t val, int lo)
{
  return (Shift)pickbits32(val, lo+1, lo);
}

static inline Extension extension(u_int32_t val, int lo)
{
  return (Extension)pickbits32(val, lo+2, lo);
}

static inline Scaling scaling(u_int32_t val, int lo)
{
  return (Scaling)pickbits32(val, lo, lo);
}

static inline WriteBack writeback(u_int32_t val, int lo)
{
  return (WriteBack)pickbits32(val, lo, lo);
}

static inline CondCode condcode(u_int32_t val, int lo)
{
  return (CondCode)pickbits32(val, lo+3, lo);
}

/*
 * operation decode
 */
// bits [28,25] are the primary dispatch vector

static inline u_int32_t dispatchGroup(u_int32_t val)
{
  return pickshift32(val, 28, 25, 0);
}

/*
 * the 16 possible values for bits [28,25] identified by tags which
 * map them to the 5 main instruction groups LDST, DPREG, ADVSIMD,
 * BREXSYS and DPIMM.
 *
 * An extra group PSEUDO is included in one of the unallocated ranges
 * for simulator-specific pseudo-instructions.
 */
enum DispatchGroup {
  GROUP_PSEUDO_0000,
  GROUP_UNALLOC_0001,
  GROUP_UNALLOC_0010,
  GROUP_UNALLOC_0011,
  GROUP_LDST_0100,
  GROUP_DPREG_0101,
  GROUP_LDST_0110,
  GROUP_ADVSIMD_0111,
  GROUP_DPIMM_1000,
  GROUP_DPIMM_1001,
  GROUP_BREXSYS_1010,
  GROUP_BREXSYS_1011,
  GROUP_LDST_1100,
  GROUP_DPREG_1101,
  GROUP_LDST_1110,
  GROUP_ADVSIMD_1111
};

// bits [31, 29] of a Pseudo are the secondary dispatch vector

static inline u_int32_t dispatchPseudo(u_int32_t val)
{
  return pickshift32(val, 31, 29, 0);
}

/*
 * the 8 possible values for bits [31,29] in a Pseudo Instruction.
 * Bits [28,25] are always 0000.
 */

enum DispatchPseudo {
  PSEUDO_UNALLOC_000, // unallocated
  PSEUDO_UNALLOC_001, // ditto
  PSEUDO_UNALLOC_010, // ditto
  PSEUDO_UNALLOC_011, // ditto
  PSEUDO_UNALLOC_100, // ditto
  PSEUDO_UNALLOC_101, // ditto
  PSEUDO_CALLOUT_110, // CALLOUT -- bits [24,0] identify call/ret sig
  PSEUDO_HALT_111     // HALT -- bits [24, 0] identify halt code
};

// bits [25, 23] of a DPImm are the secondary dispatch vector

static inline u_int32_t dispatchDPImm(u_int32_t instr)
{
  return pickshift32(instr, 25, 23, 0);
}

/*
 * the 8 possible values for bits [25,23] in a Data Processing Immediate
 * Instruction. Bits [28,25] are always 100_.
 */

enum DispatchDPImm {
  DPIMM_PCADR_000,  // PC-rel-addressing
  DPIMM_PCADR_001,  // ditto
  DPIMM_ADDSUB_010,  // Add/Subtract (immediate)
  DPIMM_ADDSUB_011, // ditto
  DPIMM_LOG_100,    // Logical (immediate)
  DPIMM_MOV_101,    // Move Wide (immediate)
  DPIMM_BITF_110,   // Bitfield
  DPIMM_EXTR_111    // Extract
};

// bits [29,28:26] of a LS are the secondary dispatch vector

static inline u_int32_t dispatchLS(u_int32_t instr)
{
  return (pickshift32(instr, 29, 28, 1) |
          pickshift32(instr, 26, 26, 0));
}

/*
 * the 8 possible values for bits [29,28:26] in a Load/Store
 * Instruction. Bits [28,25] are always _1_0
 */

enum DispatchLS {
  LS_EXCL_000,    // Load/store exclusive (includes some unallocated)
  LS_ADVSIMD_001, // AdvSIMD load/store (various -- includes some unallocated)
  LS_LIT_010,     // Load register literal (includes some unallocated)
  LS_LIT_011,     // ditto
  LS_PAIR_100,    // Load/store register pair (various)
  LS_PAIR_101,    // ditto
  LS_OTHER_110,   // other load/store formats
  LS_OTHER_111    // ditto
};

// bits [28:24:21] of a DPReg are the secondary dispatch vector

static inline u_int32_t dispatchDPReg(u_int32_t instr)
{
  return (pickshift32(instr, 28, 28, 2) |
          pickshift32(instr, 24, 24, 1) |
          pickshift32(instr, 21, 21, 0));
}

/*
 * the 8 possible values for bits [28:24:21] in a Data Processing
 * Register Instruction. Bits [28,25] are always _101
 */

enum DispatchDPReg {
  DPREG_LOG_000,     // Logical (shifted register)
  DPREG_LOG_001,     // ditto
  DPREG_ADDSHF_010,  // Add/subtract (shifted register)
  DPREG_ADDEXT_011,  // Add/subtract (extended register)
  DPREG_ADDCOND_100, // Add/subtract (with carry) AND
                     // Cond compare/select AND
                     // Data Processing (1/2 source)
  DPREG_UNALLOC_101, // Unallocated
  DPREG_3SRC_110, // Data Processing (3 source)
  DPREG_3SRC_111  // Data Processing (3 source)
};

// bits [31,29] of a BrExSys are the secondary dispatch vector

static inline u_int32_t dispatchBrExSys(u_int32_t instr)
{
  return pickbits32(instr, 31, 29);
}

/*
 * the 8 possible values for bits [31,29] in a Branch/Exception/System
 * Instruction. Bits [28,25] are always 101_
 */

enum DispatchBr {
  BR_IMM_000,     // Unconditional branch (immediate)
  BR_IMMCMP_001,  // Compare & branch (immediate) AND
                  // Test & branch (immediate)
  BR_IMMCOND_010, // Conditional branch (immediate) AND Unallocated
  BR_UNALLOC_011, // Unallocated
  BR_IMM_100,     // Unconditional branch (immediate)
  BR_IMMCMP_101,  // Compare & branch (immediate) AND
                  // Test & branch (immediate)
  BR_REG_110,     // Unconditional branch (register) AND System AND
                  // Excn gen AND Unallocated
  BR_UNALLOC_111  // Unallocated
};

/*
 * TODO still need to provide secondary decode and dispatch for
 * AdvSIMD Insructions with instr[28,25] = 0111 or 1111
 */

#endif // ifndef DECODE_H
