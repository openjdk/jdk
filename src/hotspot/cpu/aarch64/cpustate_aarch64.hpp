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

#ifndef _CPU_STATE_H
#define _CPU_STATE_H

#include <sys/types.h>

/*
 * symbolic names used to identify general registers which also match
 * the registers indices in machine code
 *
 * We have 32 general registers which can be read/written as 32 bit or
 * 64 bit sources/sinks and are appropriately referred to as Wn or Xn
 * in the assembly code.  Some instructions mix these access modes
 * (e.g. ADD X0, X1, W2) so the implementation of the instruction
 * needs to *know* which type of read or write access is required.
 */
enum GReg {
  R0,
  R1,
  R2,
  R3,
  R4,
  R5,
  R6,
  R7,
  R8,
  R9,
  R10,
  R11,
  R12,
  R13,
  R14,
  R15,
  R16,
  R17,
  R18,
  R19,
  R20,
  R21,
  R22,
  R23,
  R24,
  R25,
  R26,
  R27,
  R28,
  R29,
  R30,
  R31,
  // and now the aliases
  RSCRATCH1=R8,
  RSCRATCH2=R9,
  RMETHOD=R12,
  RESP=R20,
  RDISPATCH=R21,
  RBCP=R22,
  RLOCALS=R24,
  RMONITORS=R25,
  RCPOOL=R26,
  RHEAPBASE=R27,
  RTHREAD=R28,
  FP = R29,
  LR = R30,
  SP = R31,
  ZR = R31
};

/*
 * symbolic names used to refer to floating point registers which also
 * match the registers indices in machine code
 *
 * We have 32 FP registers which can be read/written as 8, 16, 32, 64
 * and 128 bit sources/sinks and are appropriately referred to as Bn,
 * Hn, Sn, Dn and Qn in the assembly code. Some instructions mix these
 * access modes (e.g. FCVT S0, D0) so the implementation of the
 * instruction needs to *know* which type of read or write access is
 * required.
 */

enum VReg {
  V0,
  V1,
  V2,
  V3,
  V4,
  V5,
  V6,
  V7,
  V8,
  V9,
  V10,
  V11,
  V12,
  V13,
  V14,
  V15,
  V16,
  V17,
  V18,
  V19,
  V20,
  V21,
  V22,
  V23,
  V24,
  V25,
  V26,
  V27,
  V28,
  V29,
  V30,
  V31,
};

/**
 * all the different integer bit patterns for the components of a
 * general register are overlaid here using a union so as to allow all
 * reading and writing of the desired bits.
 *
 * n.b. the ARM spec says that when you write a 32 bit register you
 * are supposed to write the low 32 bits and zero the high 32
 * bits. But we don't actually have to care about this because Java
 * will only ever consume the 32 bits value as a 64 bit quantity after
 * an explicit extend.
 */
union GRegisterValue
{
  int8_t s8;
  int16_t s16;
  int32_t s32;
  int64_t s64;
  u_int8_t u8;
  u_int16_t u16;
  u_int32_t u32;
  u_int64_t u64;
};

class GRegister
{
public:
  GRegisterValue value;
};

/*
 * float registers provide for storage of a single, double or quad
 * word format float in the same register. single floats are not
 * paired within each double register as per 32 bit arm. instead each
 * 128 bit register Vn embeds the bits for Sn, and Dn in the lower
 * quarter and half, respectively, of the bits for Qn.
 *
 * The upper bits can also be accessed as single or double floats by
 * the float vector operations using indexing e.g. V1.D[1], V1.S[3]
 * etc and, for SIMD operations using a horrible index range notation.
 *
 * The spec also talks about accessing float registers as half words
 * and bytes with Hn and Bn providing access to the low 16 and 8 bits
 * of Vn but it is not really clear what these bits represent. We can
 * probably ignore this for Java anyway. However, we do need to access
 * the raw bits at 32 and 64 bit resolution to load to/from integer
 * registers.
 */

union FRegisterValue
{
  float s;
  double d;
  long double q;
  // eventually we will need to be able to access the data as a vector
  // the integral array elements allow us to access the bits in s, d,
  // q, vs and vd at an appropriate level of granularity
  u_int8_t vb[16];
  u_int16_t vh[8];
  u_int32_t vw[4];
  u_int64_t vx[2];
  float vs[4];
  double vd[2];
};

class FRegister
{
public:
  FRegisterValue value;
};

/*
 * CPSR register -- this does not exist as a directly accessible
 * register but we need to store the flags so we can implement
 * flag-seting and flag testing operations
 *
 * we can possibly use injected x86 asm to report the outcome of flag
 * setting operations. if so we will need to grab the flags
 * immediately after the operation in order to ensure we don't lose
 * them because of the actions of the simulator. so we still need
 * somewhere to store the condition codes.
 */

class CPSRRegister
{
public:
  u_int32_t value;

/*
 * condition register bit select values
 *
 * the order of bits here is important because some of
 * the flag setting conditional instructions employ a
 * bit field to populate the flags when a false condition
 * bypasses execution of the operation and we want to
 * be able to assign the flags register using the
 * supplied value.
 */

  enum CPSRIdx {
    V_IDX,
    C_IDX,
    Z_IDX,
    N_IDX
  };

  enum CPSRMask {
    V = 1 << V_IDX,
    C = 1 << C_IDX,
    Z = 1 << Z_IDX,
    N = 1 << N_IDX
  };

  static const int CPSR_ALL_FLAGS = (V | C | Z | N);
};

// auxiliary function to assemble the relevant bits from
// the x86 EFLAGS register into an ARM CPSR value

#define X86_V_IDX 11
#define X86_C_IDX 0
#define X86_Z_IDX 6
#define X86_N_IDX 7

#define X86_V (1 << X86_V_IDX)
#define X86_C (1 << X86_C_IDX)
#define X86_Z (1 << X86_Z_IDX)
#define X86_N (1 << X86_N_IDX)

inline u_int32_t convertX86Flags(u_int32_t x86flags)
{
  u_int32_t flags;
  // set N flag
  flags = ((x86flags & X86_N) >> X86_N_IDX);
  // shift then or in Z flag
  flags <<= 1;
  flags |= ((x86flags & X86_Z) >> X86_Z_IDX);
  // shift then or in C flag
  flags <<= 1;
  flags |= ((x86flags & X86_C) >> X86_C_IDX);
  // shift then or in V flag
  flags <<= 1;
  flags |= ((x86flags & X86_V) >> X86_V_IDX);

  return flags;
}

inline u_int32_t convertX86FlagsFP(u_int32_t x86flags)
{
  // x86 flags set by fcomi(x,y) are ZF:PF:CF
  // (yes, that's PF for parity, WTF?)
  // where
  // 0) 0:0:0 means x > y
  // 1) 0:0:1 means x < y
  // 2) 1:0:0 means x = y
  // 3) 1:1:1 means x and y are unordered
  // note that we don't have to check PF so
  // we really have a simple 2-bit case switch
  // the corresponding ARM64 flags settings
  //  in hi->lo bit order are
  // 0) --C-
  // 1) N---
  // 2) -ZC-
  // 3) --CV

  static u_int32_t armFlags[] = {
      0b0010,
      0b1000,
      0b0110,
      0b0011
  };
  // pick out the ZF and CF bits
  u_int32_t zc = ((x86flags & X86_Z) >> X86_Z_IDX);
  zc <<= 1;
  zc |= ((x86flags & X86_C) >> X86_C_IDX);

  return armFlags[zc];
}

/*
 * FPSR register -- floating point status register

 * this register includes IDC, IXC, UFC, OFC, DZC, IOC and QC bits,
 * and the floating point N, Z, C, V bits but the latter are unused in
 * aarch64 mode. the sim ignores QC for now.
 *
 * bit positions are as per the ARMv7 FPSCR register
 *
 * IDC :  7 ==> Input Denormal (cumulative exception bit)
 * IXC :  4 ==> Inexact
 * UFC :  3 ==> Underflow
 * OFC :  2 ==> Overflow
 * DZC :  1 ==> Division by Zero
 * IOC :  0 ==> Invalid Operation
 */

class FPSRRegister
{
public:
  u_int32_t value;
  // indices for bits in the FPSR register value
  enum FPSRIdx {
    IO_IDX = 0,
    DZ_IDX = 1,
    OF_IDX = 2,
    UF_IDX = 3,
    IX_IDX = 4,
    ID_IDX = 7
  };
  // corresponding bits as numeric values
  enum FPSRMask {
    IO = (1 << IO_IDX),
    DZ = (1 << DZ_IDX),
    OF = (1 << OF_IDX),
    UF = (1 << UF_IDX),
    IX = (1 << IX_IDX),
    ID = (1 << ID_IDX)
  };
  static const int FPSR_ALL_FPSRS = (IO | DZ | OF | UF | IX | ID);
};

// debugger support

enum PrintFormat
{
  FMT_DECIMAL,
  FMT_HEX,
  FMT_SINGLE,
  FMT_DOUBLE,
  FMT_QUAD,
  FMT_MULTI
};

/*
 * model of the registers and other state associated with the cpu
 */
class CPUState
{
  friend class AArch64Simulator;
private:
  // this is the PC of the instruction being executed
  u_int64_t pc;
  // this is the PC of the instruction to be executed next
  // it is defaulted to pc + 4 at instruction decode but
  // execute may reset it

  u_int64_t nextpc;
  GRegister gr[33];             // extra register at index 32 is used
                                // to hold zero value
  FRegister fr[32];
  CPSRRegister cpsr;
  FPSRRegister fpsr;

public:

  CPUState() {
    gr[20].value.u64 = 0;  // establish initial condition for
                           // checkAssertions()
    trace_counter = 0;
  }

  // General Register access macros

  // only xreg or xregs can be used as an lvalue in order to update a
  // register. this ensures that the top part of a register is always
  // assigned when it is written by the sim.

  inline u_int64_t &xreg(GReg reg, int r31_is_sp) {
    if (reg == R31 && !r31_is_sp) {
      return gr[32].value.u64;
    } else {
      return gr[reg].value.u64;
    }
  }

  inline int64_t &xregs(GReg reg, int r31_is_sp) {
    if (reg == R31 && !r31_is_sp) {
      return gr[32].value.s64;
    } else {
      return gr[reg].value.s64;
    }
  }

  inline u_int32_t wreg(GReg reg, int r31_is_sp) {
    if (reg == R31 && !r31_is_sp) {
      return gr[32].value.u32;
    } else {
      return gr[reg].value.u32;
    }
  }

  inline int32_t wregs(GReg reg, int r31_is_sp) {
    if (reg == R31 && !r31_is_sp) {
      return gr[32].value.s32;
    } else {
      return gr[reg].value.s32;
    }
  }

  inline u_int32_t hreg(GReg reg, int r31_is_sp) {
    if (reg == R31 && !r31_is_sp) {
      return gr[32].value.u16;
    } else {
      return gr[reg].value.u16;
    }
  }

  inline int32_t hregs(GReg reg, int r31_is_sp) {
    if (reg == R31 && !r31_is_sp) {
      return gr[32].value.s16;
    } else {
      return gr[reg].value.s16;
    }
  }

  inline u_int32_t breg(GReg reg, int r31_is_sp) {
    if (reg == R31 && !r31_is_sp) {
      return gr[32].value.u8;
    } else {
      return gr[reg].value.u8;
    }
  }

  inline int32_t bregs(GReg reg, int r31_is_sp) {
    if (reg == R31 && !r31_is_sp) {
      return gr[32].value.s8;
    } else {
      return gr[reg].value.s8;
    }
  }

  // FP Register access macros

  // all non-vector accessors return a reference so we can both read
  // and assign

  inline float &sreg(VReg reg) {
    return fr[reg].value.s;
  }

  inline double &dreg(VReg reg) {
    return fr[reg].value.d;
  }

  inline long double &qreg(VReg reg) {
    return fr[reg].value.q;
  }

  // all vector register accessors return a pointer

  inline float *vsreg(VReg reg) {
    return &fr[reg].value.vs[0];
  }

  inline double *vdreg(VReg reg) {
    return &fr[reg].value.vd[0];
  }

  inline u_int8_t *vbreg(VReg reg) {
    return &fr[reg].value.vb[0];
  }

  inline u_int16_t *vhreg(VReg reg) {
    return &fr[reg].value.vh[0];
  }

  inline u_int32_t *vwreg(VReg reg) {
    return &fr[reg].value.vw[0];
  }

  inline u_int64_t *vxreg(VReg reg) {
    return &fr[reg].value.vx[0];
  }

  union GRegisterValue prev_sp, prev_fp;

  static const int trace_size = 256;
  u_int64_t trace_buffer[trace_size];
  int trace_counter;

  bool checkAssertions()
  {
    // Make sure that SP is 16-aligned
    // Also make sure that ESP is above SP.
    // We don't care about checking ESP if it is null, i.e. it hasn't
    // been used yet.
    if (gr[31].value.u64 & 0x0f) {
      asm volatile("nop");
      return false;
    }
    return true;
  }

  // pc register accessors

  // this instruction can be used to fetch the current PC
  u_int64_t getPC();
  // instead of setting the current PC directly you can
  // first set the next PC (either absolute or PC-relative)
  // and later copy the next PC into the current PC
  // this supports a default increment by 4 at instruction
  // fetch with an optional reset by control instructions
  u_int64_t getNextPC();
  void setNextPC(u_int64_t next);
  void offsetNextPC(int64_t offset);
  // install nextpc as current pc
  void updatePC();

  // this instruction can be used to save the next PC to LR
  // just before installing a branch PC
  inline void saveLR() { gr[LR].value.u64 = nextpc; }

  // cpsr register accessors
  u_int32_t getCPSRRegister();
  void setCPSRRegister(u_int32_t flags);
  // read a specific subset of the flags as a bit pattern
  // mask should be composed using elements of enum FlagMask
  u_int32_t getCPSRBits(u_int32_t mask);
  // assign a specific subset of the flags as a bit pattern
  // mask and value should be composed using elements of enum FlagMask
  void setCPSRBits(u_int32_t mask, u_int32_t value);
  // test the value of a single flag returned as 1 or 0
  u_int32_t testCPSR(CPSRRegister::CPSRIdx idx);
  // set a single flag
  void setCPSR(CPSRRegister::CPSRIdx idx);
  // clear a single flag
  void clearCPSR(CPSRRegister::CPSRIdx idx);
  // utility method to set ARM CSPR flags from an x86 bit mask generated by integer arithmetic
  void setCPSRRegisterFromX86(u_int64_t x86Flags);
  // utility method to set ARM CSPR flags from an x86 bit mask generated by floating compare
  void setCPSRRegisterFromX86FP(u_int64_t x86Flags);

  // fpsr register accessors
  u_int32_t getFPSRRegister();
  void setFPSRRegister(u_int32_t flags);
  // read a specific subset of the fprs bits as a bit pattern
  // mask should be composed using elements of enum FPSRRegister::FlagMask
  u_int32_t getFPSRBits(u_int32_t mask);
  // assign a specific subset of the flags as a bit pattern
  // mask and value should be composed using elements of enum FPSRRegister::FlagMask
  void setFPSRBits(u_int32_t mask, u_int32_t value);
  // test the value of a single flag returned as 1 or 0
  u_int32_t testFPSR(FPSRRegister::FPSRIdx idx);
  // set a single flag
  void setFPSR(FPSRRegister::FPSRIdx idx);
  // clear a single flag
  void clearFPSR(FPSRRegister::FPSRIdx idx);

  // debugger support
  void printPC(int pending, const char *trailing = "\n");
  void printInstr(u_int32_t instr, void (*dasm)(u_int64_t), const char *trailing = "\n");
  void printGReg(GReg reg, PrintFormat format = FMT_HEX, const char *trailing = "\n");
  void printVReg(VReg reg, PrintFormat format = FMT_HEX, const char *trailing = "\n");
  void printCPSR(const char *trailing = "\n");
  void printFPSR(const char *trailing = "\n");
  void dumpState();
};

#endif // ifndef _CPU_STATE_H
