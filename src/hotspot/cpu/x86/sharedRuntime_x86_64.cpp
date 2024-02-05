/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
#ifndef _WINDOWS
#include "alloca.h"
#endif
#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "code/compiledIC.hpp"
#include "code/debugInfoRec.hpp"
#include "code/icBuffer.hpp"
#include "code/nativeInst.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "interpreter/interpreter.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/compiledICHolder.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/align.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/formatBuffer.hpp"
#include "vmreg_x86.inline.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif
#if INCLUDE_JVMCI
#include "jvmci/jvmciJavaClasses.hpp"
#endif

#define __ masm->

const int StackAlignmentInSlots = StackAlignmentInBytes / VMRegImpl::stack_slot_size;

class SimpleRuntimeFrame {

  public:

  // Most of the runtime stubs have this simple frame layout.
  // This class exists to make the layout shared in one place.
  // Offsets are for compiler stack slots, which are jints.
  enum layout {
    // The frame sender code expects that rbp will be in the "natural" place and
    // will override any oopMap setting for it. We must therefore force the layout
    // so that it agrees with the frame sender code.
    rbp_off = frame::arg_reg_save_area_bytes/BytesPerInt,
    rbp_off2,
    return_off, return_off2,
    framesize
  };
};

class RegisterSaver {
  // Capture info about frame layout.  Layout offsets are in jint
  // units because compiler frame slots are jints.
#define XSAVE_AREA_BEGIN 160
#define XSAVE_AREA_YMM_BEGIN 576
#define XSAVE_AREA_OPMASK_BEGIN 1088
#define XSAVE_AREA_ZMM_BEGIN 1152
#define XSAVE_AREA_UPPERBANK 1664
#define DEF_XMM_OFFS(regnum)       xmm ## regnum ## _off = xmm_off + (regnum)*16/BytesPerInt, xmm ## regnum ## H_off
#define DEF_YMM_OFFS(regnum)       ymm ## regnum ## _off = ymm_off + (regnum)*16/BytesPerInt, ymm ## regnum ## H_off
#define DEF_ZMM_OFFS(regnum)       zmm ## regnum ## _off = zmm_off + (regnum)*32/BytesPerInt, zmm ## regnum ## H_off
#define DEF_OPMASK_OFFS(regnum)    opmask ## regnum ## _off = opmask_off + (regnum)*8/BytesPerInt,     opmask ## regnum ## H_off
#define DEF_ZMM_UPPER_OFFS(regnum) zmm ## regnum ## _off = zmm_upper_off + (regnum-16)*64/BytesPerInt, zmm ## regnum ## H_off
  enum layout {
    fpu_state_off = frame::arg_reg_save_area_bytes/BytesPerInt, // fxsave save area
    xmm_off       = fpu_state_off + XSAVE_AREA_BEGIN/BytesPerInt,            // offset in fxsave save area
    DEF_XMM_OFFS(0),
    DEF_XMM_OFFS(1),
    // 2..15 are implied in range usage
    ymm_off = xmm_off + (XSAVE_AREA_YMM_BEGIN - XSAVE_AREA_BEGIN)/BytesPerInt,
    DEF_YMM_OFFS(0),
    DEF_YMM_OFFS(1),
    // 2..15 are implied in range usage
    opmask_off         = xmm_off + (XSAVE_AREA_OPMASK_BEGIN - XSAVE_AREA_BEGIN)/BytesPerInt,
    DEF_OPMASK_OFFS(0),
    DEF_OPMASK_OFFS(1),
    // 2..7 are implied in range usage
    zmm_off = xmm_off + (XSAVE_AREA_ZMM_BEGIN - XSAVE_AREA_BEGIN)/BytesPerInt,
    DEF_ZMM_OFFS(0),
    DEF_ZMM_OFFS(1),
    zmm_upper_off = xmm_off + (XSAVE_AREA_UPPERBANK - XSAVE_AREA_BEGIN)/BytesPerInt,
    DEF_ZMM_UPPER_OFFS(16),
    DEF_ZMM_UPPER_OFFS(17),
    // 18..31 are implied in range usage
    fpu_state_end = fpu_state_off + ((FPUStateSizeInWords-1)*wordSize / BytesPerInt),
    fpu_stateH_end,
    r15_off, r15H_off,
    r14_off, r14H_off,
    r13_off, r13H_off,
    r12_off, r12H_off,
    r11_off, r11H_off,
    r10_off, r10H_off,
    r9_off,  r9H_off,
    r8_off,  r8H_off,
    rdi_off, rdiH_off,
    rsi_off, rsiH_off,
    ignore_off, ignoreH_off,  // extra copy of rbp
    rsp_off, rspH_off,
    rbx_off, rbxH_off,
    rdx_off, rdxH_off,
    rcx_off, rcxH_off,
    rax_off, raxH_off,
    // 16-byte stack alignment fill word: see MacroAssembler::push/pop_IU_state
    align_off, alignH_off,
    flags_off, flagsH_off,
    // The frame sender code expects that rbp will be in the "natural" place and
    // will override any oopMap setting for it. We must therefore force the layout
    // so that it agrees with the frame sender code.
    rbp_off, rbpH_off,        // copy of rbp we will restore
    return_off, returnH_off,  // slot for return address
    reg_save_size             // size in compiler stack slots
  };

 public:
  static OopMap* save_live_registers(MacroAssembler* masm, int additional_frame_words, int* total_frame_words, bool save_wide_vectors);
  static void restore_live_registers(MacroAssembler* masm, bool restore_wide_vectors = false);

  // Offsets into the register save area
  // Used by deoptimization when it is managing result register
  // values on its own

  static int rax_offset_in_bytes(void)    { return BytesPerInt * rax_off; }
  static int rdx_offset_in_bytes(void)    { return BytesPerInt * rdx_off; }
  static int rbx_offset_in_bytes(void)    { return BytesPerInt * rbx_off; }
  static int xmm0_offset_in_bytes(void)   { return BytesPerInt * xmm0_off; }
  static int return_offset_in_bytes(void) { return BytesPerInt * return_off; }

  // During deoptimization only the result registers need to be restored,
  // all the other values have already been extracted.
  static void restore_result_registers(MacroAssembler* masm);
};

OopMap* RegisterSaver::save_live_registers(MacroAssembler* masm, int additional_frame_words, int* total_frame_words, bool save_wide_vectors) {
  int off = 0;
  int num_xmm_regs = XMMRegister::available_xmm_registers();
#if COMPILER2_OR_JVMCI
  if (save_wide_vectors && UseAVX == 0) {
    save_wide_vectors = false; // vectors larger than 16 byte long are supported only with AVX
  }
  assert(!save_wide_vectors || MaxVectorSize <= 64, "Only up to 64 byte long vectors are supported");
#else
  save_wide_vectors = false; // vectors are generated only by C2 and JVMCI
#endif

  // Always make the frame size 16-byte aligned, both vector and non vector stacks are always allocated
  int frame_size_in_bytes = align_up(reg_save_size*BytesPerInt, num_xmm_regs);
  // OopMap frame size is in compiler stack slots (jint's) not bytes or words
  int frame_size_in_slots = frame_size_in_bytes / BytesPerInt;
  // CodeBlob frame size is in words.
  int frame_size_in_words = frame_size_in_bytes / wordSize;
  *total_frame_words = frame_size_in_words;

  // Save registers, fpu state, and flags.
  // We assume caller has already pushed the return address onto the
  // stack, so rsp is 8-byte aligned here.
  // We push rpb twice in this sequence because we want the real rbp
  // to be under the return like a normal enter.

  __ enter();          // rsp becomes 16-byte aligned here
  __ push_CPU_state(); // Push a multiple of 16 bytes

  // push cpu state handles this on EVEX enabled targets
  if (save_wide_vectors) {
    // Save upper half of YMM registers(0..15)
    int base_addr = XSAVE_AREA_YMM_BEGIN;
    for (int n = 0; n < 16; n++) {
      __ vextractf128_high(Address(rsp, base_addr+n*16), as_XMMRegister(n));
    }
    if (VM_Version::supports_evex()) {
      // Save upper half of ZMM registers(0..15)
      base_addr = XSAVE_AREA_ZMM_BEGIN;
      for (int n = 0; n < 16; n++) {
        __ vextractf64x4_high(Address(rsp, base_addr+n*32), as_XMMRegister(n));
      }
      // Save full ZMM registers(16..num_xmm_regs)
      base_addr = XSAVE_AREA_UPPERBANK;
      off = 0;
      int vector_len = Assembler::AVX_512bit;
      for (int n = 16; n < num_xmm_regs; n++) {
        __ evmovdqul(Address(rsp, base_addr+(off++*64)), as_XMMRegister(n), vector_len);
      }
#if COMPILER2_OR_JVMCI
      base_addr = XSAVE_AREA_OPMASK_BEGIN;
      off = 0;
      for(int n = 0; n < KRegister::number_of_registers; n++) {
        __ kmov(Address(rsp, base_addr+(off++*8)), as_KRegister(n));
      }
#endif
    }
  } else {
    if (VM_Version::supports_evex()) {
      // Save upper bank of XMM registers(16..31) for scalar or 16-byte vector usage
      int base_addr = XSAVE_AREA_UPPERBANK;
      off = 0;
      int vector_len = VM_Version::supports_avx512vl() ?  Assembler::AVX_128bit : Assembler::AVX_512bit;
      for (int n = 16; n < num_xmm_regs; n++) {
        __ evmovdqul(Address(rsp, base_addr+(off++*64)), as_XMMRegister(n), vector_len);
      }
#if COMPILER2_OR_JVMCI
      base_addr = XSAVE_AREA_OPMASK_BEGIN;
      off = 0;
      for(int n = 0; n < KRegister::number_of_registers; n++) {
        __ kmov(Address(rsp, base_addr+(off++*8)), as_KRegister(n));
      }
#endif
    }
  }
  __ vzeroupper();
  if (frame::arg_reg_save_area_bytes != 0) {
    // Allocate argument register save area
    __ subptr(rsp, frame::arg_reg_save_area_bytes);
  }

  // Set an oopmap for the call site.  This oopmap will map all
  // oop-registers and debug-info registers as callee-saved.  This
  // will allow deoptimization at this safepoint to find all possible
  // debug-info recordings, as well as let GC find all oops.

  OopMapSet *oop_maps = new OopMapSet();
  OopMap* map = new OopMap(frame_size_in_slots, 0);

#define STACK_OFFSET(x) VMRegImpl::stack2reg((x))

  map->set_callee_saved(STACK_OFFSET( rax_off ), rax->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( rcx_off ), rcx->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( rdx_off ), rdx->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( rbx_off ), rbx->as_VMReg());
  // rbp location is known implicitly by the frame sender code, needs no oopmap
  // and the location where rbp was saved by is ignored
  map->set_callee_saved(STACK_OFFSET( rsi_off ), rsi->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( rdi_off ), rdi->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( r8_off  ), r8->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( r9_off  ), r9->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( r10_off ), r10->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( r11_off ), r11->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( r12_off ), r12->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( r13_off ), r13->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( r14_off ), r14->as_VMReg());
  map->set_callee_saved(STACK_OFFSET( r15_off ), r15->as_VMReg());
  // For both AVX and EVEX we will use the legacy FXSAVE area for xmm0..xmm15,
  // on EVEX enabled targets, we get it included in the xsave area
  off = xmm0_off;
  int delta = xmm1_off - off;
  for (int n = 0; n < 16; n++) {
    XMMRegister xmm_name = as_XMMRegister(n);
    map->set_callee_saved(STACK_OFFSET(off), xmm_name->as_VMReg());
    off += delta;
  }
  if (UseAVX > 2) {
    // Obtain xmm16..xmm31 from the XSAVE area on EVEX enabled targets
    off = zmm16_off;
    delta = zmm17_off - off;
    for (int n = 16; n < num_xmm_regs; n++) {
      XMMRegister zmm_name = as_XMMRegister(n);
      map->set_callee_saved(STACK_OFFSET(off), zmm_name->as_VMReg());
      off += delta;
    }
  }

#if COMPILER2_OR_JVMCI
  if (save_wide_vectors) {
    // Save upper half of YMM registers(0..15)
    off = ymm0_off;
    delta = ymm1_off - ymm0_off;
    for (int n = 0; n < 16; n++) {
      XMMRegister ymm_name = as_XMMRegister(n);
      map->set_callee_saved(STACK_OFFSET(off), ymm_name->as_VMReg()->next(4));
      off += delta;
    }
    if (VM_Version::supports_evex()) {
      // Save upper half of ZMM registers(0..15)
      off = zmm0_off;
      delta = zmm1_off - zmm0_off;
      for (int n = 0; n < 16; n++) {
        XMMRegister zmm_name = as_XMMRegister(n);
        map->set_callee_saved(STACK_OFFSET(off), zmm_name->as_VMReg()->next(8));
        off += delta;
      }
    }
  }
#endif // COMPILER2_OR_JVMCI

  // %%% These should all be a waste but we'll keep things as they were for now
  if (true) {
    map->set_callee_saved(STACK_OFFSET( raxH_off ), rax->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( rcxH_off ), rcx->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( rdxH_off ), rdx->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( rbxH_off ), rbx->as_VMReg()->next());
    // rbp location is known implicitly by the frame sender code, needs no oopmap
    map->set_callee_saved(STACK_OFFSET( rsiH_off ), rsi->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( rdiH_off ), rdi->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( r8H_off  ), r8->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( r9H_off  ), r9->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( r10H_off ), r10->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( r11H_off ), r11->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( r12H_off ), r12->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( r13H_off ), r13->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( r14H_off ), r14->as_VMReg()->next());
    map->set_callee_saved(STACK_OFFSET( r15H_off ), r15->as_VMReg()->next());
    // For both AVX and EVEX we will use the legacy FXSAVE area for xmm0..xmm15,
    // on EVEX enabled targets, we get it included in the xsave area
    off = xmm0H_off;
    delta = xmm1H_off - off;
    for (int n = 0; n < 16; n++) {
      XMMRegister xmm_name = as_XMMRegister(n);
      map->set_callee_saved(STACK_OFFSET(off), xmm_name->as_VMReg()->next());
      off += delta;
    }
    if (UseAVX > 2) {
      // Obtain xmm16..xmm31 from the XSAVE area on EVEX enabled targets
      off = zmm16H_off;
      delta = zmm17H_off - off;
      for (int n = 16; n < num_xmm_regs; n++) {
        XMMRegister zmm_name = as_XMMRegister(n);
        map->set_callee_saved(STACK_OFFSET(off), zmm_name->as_VMReg()->next());
        off += delta;
      }
    }
  }

  return map;
}

void RegisterSaver::restore_live_registers(MacroAssembler* masm, bool restore_wide_vectors) {
  int num_xmm_regs = XMMRegister::available_xmm_registers();
  if (frame::arg_reg_save_area_bytes != 0) {
    // Pop arg register save area
    __ addptr(rsp, frame::arg_reg_save_area_bytes);
  }

#if COMPILER2_OR_JVMCI
  if (restore_wide_vectors) {
    assert(UseAVX > 0, "Vectors larger than 16 byte long are supported only with AVX");
    assert(MaxVectorSize <= 64, "Only up to 64 byte long vectors are supported");
  }
#else
  assert(!restore_wide_vectors, "vectors are generated only by C2");
#endif

  __ vzeroupper();

  // On EVEX enabled targets everything is handled in pop fpu state
  if (restore_wide_vectors) {
    // Restore upper half of YMM registers (0..15)
    int base_addr = XSAVE_AREA_YMM_BEGIN;
    for (int n = 0; n < 16; n++) {
      __ vinsertf128_high(as_XMMRegister(n), Address(rsp, base_addr+n*16));
    }
    if (VM_Version::supports_evex()) {
      // Restore upper half of ZMM registers (0..15)
      base_addr = XSAVE_AREA_ZMM_BEGIN;
      for (int n = 0; n < 16; n++) {
        __ vinsertf64x4_high(as_XMMRegister(n), Address(rsp, base_addr+n*32));
      }
      // Restore full ZMM registers(16..num_xmm_regs)
      base_addr = XSAVE_AREA_UPPERBANK;
      int vector_len = Assembler::AVX_512bit;
      int off = 0;
      for (int n = 16; n < num_xmm_regs; n++) {
        __ evmovdqul(as_XMMRegister(n), Address(rsp, base_addr+(off++*64)), vector_len);
      }
#if COMPILER2_OR_JVMCI
      base_addr = XSAVE_AREA_OPMASK_BEGIN;
      off = 0;
      for (int n = 0; n < KRegister::number_of_registers; n++) {
        __ kmov(as_KRegister(n), Address(rsp, base_addr+(off++*8)));
      }
#endif
    }
  } else {
    if (VM_Version::supports_evex()) {
      // Restore upper bank of XMM registers(16..31) for scalar or 16-byte vector usage
      int base_addr = XSAVE_AREA_UPPERBANK;
      int off = 0;
      int vector_len = VM_Version::supports_avx512vl() ?  Assembler::AVX_128bit : Assembler::AVX_512bit;
      for (int n = 16; n < num_xmm_regs; n++) {
        __ evmovdqul(as_XMMRegister(n), Address(rsp, base_addr+(off++*64)), vector_len);
      }
#if COMPILER2_OR_JVMCI
      base_addr = XSAVE_AREA_OPMASK_BEGIN;
      off = 0;
      for (int n = 0; n < KRegister::number_of_registers; n++) {
        __ kmov(as_KRegister(n), Address(rsp, base_addr+(off++*8)));
      }
#endif
    }
  }

  // Recover CPU state
  __ pop_CPU_state();
  // Get the rbp described implicitly by the calling convention (no oopMap)
  __ pop(rbp);
}

void RegisterSaver::restore_result_registers(MacroAssembler* masm) {

  // Just restore result register. Only used by deoptimization. By
  // now any callee save register that needs to be restored to a c2
  // caller of the deoptee has been extracted into the vframeArray
  // and will be stuffed into the c2i adapter we create for later
  // restoration so only result registers need to be restored here.

  // Restore fp result register
  __ movdbl(xmm0, Address(rsp, xmm0_offset_in_bytes()));
  // Restore integer result register
  __ movptr(rax, Address(rsp, rax_offset_in_bytes()));
  __ movptr(rdx, Address(rsp, rdx_offset_in_bytes()));

  // Pop all of the register save are off the stack except the return address
  __ addptr(rsp, return_offset_in_bytes());
}

// Is vector's size (in bytes) bigger than a size saved by default?
// 16 bytes XMM registers are saved by default using fxsave/fxrstor instructions.
bool SharedRuntime::is_wide_vector(int size) {
  return size > 16;
}

// ---------------------------------------------------------------------------
// Read the array of BasicTypes from a signature, and compute where the
// arguments should go.  Values in the VMRegPair regs array refer to 4-byte
// quantities.  Values less than VMRegImpl::stack0 are registers, those above
// refer to 4-byte stack slots.  All stack slots are based off of the stack pointer
// as framesizes are fixed.
// VMRegImpl::stack0 refers to the first slot 0(sp).
// and VMRegImpl::stack0+1 refers to the memory word 4-byes higher.
// Register up to Register::number_of_registers are the 64-bit
// integer registers.

// Note: the INPUTS in sig_bt are in units of Java argument words, which are
// either 32-bit or 64-bit depending on the build.  The OUTPUTS are in 32-bit
// units regardless of build. Of course for i486 there is no 64 bit build

// The Java calling convention is a "shifted" version of the C ABI.
// By skipping the first C ABI register we can call non-static jni methods
// with small numbers of arguments without having to shuffle the arguments
// at all. Since we control the java ABI we ought to at least get some
// advantage out of it.

int SharedRuntime::java_calling_convention(const BasicType *sig_bt,
                                           VMRegPair *regs,
                                           int total_args_passed) {

  // Create the mapping between argument positions and
  // registers.
  static const Register INT_ArgReg[Argument::n_int_register_parameters_j] = {
    j_rarg0, j_rarg1, j_rarg2, j_rarg3, j_rarg4, j_rarg5
  };
  static const XMMRegister FP_ArgReg[Argument::n_float_register_parameters_j] = {
    j_farg0, j_farg1, j_farg2, j_farg3,
    j_farg4, j_farg5, j_farg6, j_farg7
  };


  uint int_args = 0;
  uint fp_args = 0;
  uint stk_args = 0;

  for (int i = 0; i < total_args_passed; i++) {
    switch (sig_bt[i]) {
    case T_BOOLEAN:
    case T_CHAR:
    case T_BYTE:
    case T_SHORT:
    case T_INT:
      if (int_args < Argument::n_int_register_parameters_j) {
        regs[i].set1(INT_ArgReg[int_args++]->as_VMReg());
      } else {
        stk_args = align_up(stk_args, 2);
        regs[i].set1(VMRegImpl::stack2reg(stk_args));
        stk_args += 1;
      }
      break;
    case T_VOID:
      // halves of T_LONG or T_DOUBLE
      assert(i != 0 && (sig_bt[i - 1] == T_LONG || sig_bt[i - 1] == T_DOUBLE), "expecting half");
      regs[i].set_bad();
      break;
    case T_LONG:
      assert((i + 1) < total_args_passed && sig_bt[i + 1] == T_VOID, "expecting half");
      // fall through
    case T_OBJECT:
    case T_ARRAY:
    case T_ADDRESS:
      if (int_args < Argument::n_int_register_parameters_j) {
        regs[i].set2(INT_ArgReg[int_args++]->as_VMReg());
      } else {
        stk_args = align_up(stk_args, 2);
        regs[i].set2(VMRegImpl::stack2reg(stk_args));
        stk_args += 2;
      }
      break;
    case T_FLOAT:
      if (fp_args < Argument::n_float_register_parameters_j) {
        regs[i].set1(FP_ArgReg[fp_args++]->as_VMReg());
      } else {
        stk_args = align_up(stk_args, 2);
        regs[i].set1(VMRegImpl::stack2reg(stk_args));
        stk_args += 1;
      }
      break;
    case T_DOUBLE:
      assert((i + 1) < total_args_passed && sig_bt[i + 1] == T_VOID, "expecting half");
      if (fp_args < Argument::n_float_register_parameters_j) {
        regs[i].set2(FP_ArgReg[fp_args++]->as_VMReg());
      } else {
        stk_args = align_up(stk_args, 2);
        regs[i].set2(VMRegImpl::stack2reg(stk_args));
        stk_args += 2;
      }
      break;
    default:
      ShouldNotReachHere();
      break;
    }
  }

  return stk_args;
}

// Patch the callers callsite with entry to compiled code if it exists.
static void patch_callers_callsite(MacroAssembler *masm) {
  Label L;
  __ cmpptr(Address(rbx, in_bytes(Method::code_offset())), NULL_WORD);
  __ jcc(Assembler::equal, L);

  // Save the current stack pointer
  __ mov(r13, rsp);
  // Schedule the branch target address early.
  // Call into the VM to patch the caller, then jump to compiled callee
  // rax isn't live so capture return address while we easily can
  __ movptr(rax, Address(rsp, 0));

  // align stack so push_CPU_state doesn't fault
  __ andptr(rsp, -(StackAlignmentInBytes));
  __ push_CPU_state();
  __ vzeroupper();
  // VM needs caller's callsite
  // VM needs target method
  // This needs to be a long call since we will relocate this adapter to
  // the codeBuffer and it may not reach

  // Allocate argument register save area
  if (frame::arg_reg_save_area_bytes != 0) {
    __ subptr(rsp, frame::arg_reg_save_area_bytes);
  }
  __ mov(c_rarg0, rbx);
  __ mov(c_rarg1, rax);
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::fixup_callers_callsite)));

  // De-allocate argument register save area
  if (frame::arg_reg_save_area_bytes != 0) {
    __ addptr(rsp, frame::arg_reg_save_area_bytes);
  }

  __ vzeroupper();
  __ pop_CPU_state();
  // restore sp
  __ mov(rsp, r13);
  __ bind(L);
}


static void gen_c2i_adapter(MacroAssembler *masm,
                            int total_args_passed,
                            int comp_args_on_stack,
                            const BasicType *sig_bt,
                            const VMRegPair *regs,
                            Label& skip_fixup) {
  // Before we get into the guts of the C2I adapter, see if we should be here
  // at all.  We've come from compiled code and are attempting to jump to the
  // interpreter, which means the caller made a static call to get here
  // (vcalls always get a compiled target if there is one).  Check for a
  // compiled target.  If there is one, we need to patch the caller's call.
  patch_callers_callsite(masm);

  __ bind(skip_fixup);

  // Since all args are passed on the stack, total_args_passed *
  // Interpreter::stackElementSize is the space we need.

  assert(total_args_passed >= 0, "total_args_passed is %d", total_args_passed);

  int extraspace = (total_args_passed * Interpreter::stackElementSize);

  // stack is aligned, keep it that way
  // This is not currently needed or enforced by the interpreter, but
  // we might as well conform to the ABI.
  extraspace = align_up(extraspace, 2*wordSize);

  // set senderSP value
  __ lea(r13, Address(rsp, wordSize));

#ifdef ASSERT
  __ check_stack_alignment(r13, "sender stack not aligned");
#endif
  if (extraspace > 0) {
    // Pop the return address
    __ pop(rax);

    __ subptr(rsp, extraspace);

    // Push the return address
    __ push(rax);

    // Account for the return address location since we store it first rather
    // than hold it in a register across all the shuffling
    extraspace += wordSize;
  }

#ifdef ASSERT
  __ check_stack_alignment(rsp, "callee stack not aligned", wordSize, rax);
#endif

  // Now write the args into the outgoing interpreter space
  for (int i = 0; i < total_args_passed; i++) {
    if (sig_bt[i] == T_VOID) {
      assert(i > 0 && (sig_bt[i-1] == T_LONG || sig_bt[i-1] == T_DOUBLE), "missing half");
      continue;
    }

    // offset to start parameters
    int st_off   = (total_args_passed - i) * Interpreter::stackElementSize;
    int next_off = st_off - Interpreter::stackElementSize;

    // Say 4 args:
    // i   st_off
    // 0   32 T_LONG
    // 1   24 T_VOID
    // 2   16 T_OBJECT
    // 3    8 T_BOOL
    // -    0 return address
    //
    // However to make thing extra confusing. Because we can fit a long/double in
    // a single slot on a 64 bt vm and it would be silly to break them up, the interpreter
    // leaves one slot empty and only stores to a single slot. In this case the
    // slot that is occupied is the T_VOID slot. See I said it was confusing.

    VMReg r_1 = regs[i].first();
    VMReg r_2 = regs[i].second();
    if (!r_1->is_valid()) {
      assert(!r_2->is_valid(), "");
      continue;
    }
    if (r_1->is_stack()) {
      // memory to memory use rax
      int ld_off = r_1->reg2stack() * VMRegImpl::stack_slot_size + extraspace;
      if (!r_2->is_valid()) {
        // sign extend??
        __ movl(rax, Address(rsp, ld_off));
        __ movptr(Address(rsp, st_off), rax);

      } else {

        __ movq(rax, Address(rsp, ld_off));

        // Two VMREgs|OptoRegs can be T_OBJECT, T_ADDRESS, T_DOUBLE, T_LONG
        // T_DOUBLE and T_LONG use two slots in the interpreter
        if ( sig_bt[i] == T_LONG || sig_bt[i] == T_DOUBLE) {
          // ld_off == LSW, ld_off+wordSize == MSW
          // st_off == MSW, next_off == LSW
          __ movq(Address(rsp, next_off), rax);
#ifdef ASSERT
          // Overwrite the unused slot with known junk
          __ mov64(rax, CONST64(0xdeadffffdeadaaaa));
          __ movptr(Address(rsp, st_off), rax);
#endif /* ASSERT */
        } else {
          __ movq(Address(rsp, st_off), rax);
        }
      }
    } else if (r_1->is_Register()) {
      Register r = r_1->as_Register();
      if (!r_2->is_valid()) {
        // must be only an int (or less ) so move only 32bits to slot
        // why not sign extend??
        __ movl(Address(rsp, st_off), r);
      } else {
        // Two VMREgs|OptoRegs can be T_OBJECT, T_ADDRESS, T_DOUBLE, T_LONG
        // T_DOUBLE and T_LONG use two slots in the interpreter
        if ( sig_bt[i] == T_LONG || sig_bt[i] == T_DOUBLE) {
          // long/double in gpr
#ifdef ASSERT
          // Overwrite the unused slot with known junk
          __ mov64(rax, CONST64(0xdeadffffdeadaaab));
          __ movptr(Address(rsp, st_off), rax);
#endif /* ASSERT */
          __ movq(Address(rsp, next_off), r);
        } else {
          __ movptr(Address(rsp, st_off), r);
        }
      }
    } else {
      assert(r_1->is_XMMRegister(), "");
      if (!r_2->is_valid()) {
        // only a float use just part of the slot
        __ movflt(Address(rsp, st_off), r_1->as_XMMRegister());
      } else {
#ifdef ASSERT
        // Overwrite the unused slot with known junk
        __ mov64(rax, CONST64(0xdeadffffdeadaaac));
        __ movptr(Address(rsp, st_off), rax);
#endif /* ASSERT */
        __ movdbl(Address(rsp, next_off), r_1->as_XMMRegister());
      }
    }
  }

  // Schedule the branch target address early.
  __ movptr(rcx, Address(rbx, in_bytes(Method::interpreter_entry_offset())));
  __ jmp(rcx);
}

static void range_check(MacroAssembler* masm, Register pc_reg, Register temp_reg,
                        address code_start, address code_end,
                        Label& L_ok) {
  Label L_fail;
  __ lea(temp_reg, ExternalAddress(code_start));
  __ cmpptr(pc_reg, temp_reg);
  __ jcc(Assembler::belowEqual, L_fail);
  __ lea(temp_reg, ExternalAddress(code_end));
  __ cmpptr(pc_reg, temp_reg);
  __ jcc(Assembler::below, L_ok);
  __ bind(L_fail);
}

void SharedRuntime::gen_i2c_adapter(MacroAssembler *masm,
                                    int total_args_passed,
                                    int comp_args_on_stack,
                                    const BasicType *sig_bt,
                                    const VMRegPair *regs) {

  // Note: r13 contains the senderSP on entry. We must preserve it since
  // we may do a i2c -> c2i transition if we lose a race where compiled
  // code goes non-entrant while we get args ready.
  // In addition we use r13 to locate all the interpreter args as
  // we must align the stack to 16 bytes on an i2c entry else we
  // lose alignment we expect in all compiled code and register
  // save code can segv when fxsave instructions find improperly
  // aligned stack pointer.

  // Adapters can be frameless because they do not require the caller
  // to perform additional cleanup work, such as correcting the stack pointer.
  // An i2c adapter is frameless because the *caller* frame, which is interpreted,
  // routinely repairs its own stack pointer (from interpreter_frame_last_sp),
  // even if a callee has modified the stack pointer.
  // A c2i adapter is frameless because the *callee* frame, which is interpreted,
  // routinely repairs its caller's stack pointer (from sender_sp, which is set
  // up via the senderSP register).
  // In other words, if *either* the caller or callee is interpreted, we can
  // get the stack pointer repaired after a call.
  // This is why c2i and i2c adapters cannot be indefinitely composed.
  // In particular, if a c2i adapter were to somehow call an i2c adapter,
  // both caller and callee would be compiled methods, and neither would
  // clean up the stack pointer changes performed by the two adapters.
  // If this happens, control eventually transfers back to the compiled
  // caller, but with an uncorrected stack, causing delayed havoc.

  if (VerifyAdapterCalls &&
      (Interpreter::code() != nullptr || StubRoutines::final_stubs_code() != nullptr)) {
    // So, let's test for cascading c2i/i2c adapters right now.
    //  assert(Interpreter::contains($return_addr) ||
    //         StubRoutines::contains($return_addr),
    //         "i2c adapter must return to an interpreter frame");
    __ block_comment("verify_i2c { ");
    // Pick up the return address
    __ movptr(rax, Address(rsp, 0));
    Label L_ok;
    if (Interpreter::code() != nullptr) {
      range_check(masm, rax, r11,
                  Interpreter::code()->code_start(),
                  Interpreter::code()->code_end(),
                  L_ok);
    }
    if (StubRoutines::initial_stubs_code() != nullptr) {
      range_check(masm, rax, r11,
                  StubRoutines::initial_stubs_code()->code_begin(),
                  StubRoutines::initial_stubs_code()->code_end(),
                  L_ok);
    }
    if (StubRoutines::final_stubs_code() != nullptr) {
      range_check(masm, rax, r11,
                  StubRoutines::final_stubs_code()->code_begin(),
                  StubRoutines::final_stubs_code()->code_end(),
                  L_ok);
    }
    const char* msg = "i2c adapter must return to an interpreter frame";
    __ block_comment(msg);
    __ stop(msg);
    __ bind(L_ok);
    __ block_comment("} verify_i2ce ");
  }

  // Must preserve original SP for loading incoming arguments because
  // we need to align the outgoing SP for compiled code.
  __ movptr(r11, rsp);

  // Pick up the return address
  __ pop(rax);

  // Convert 4-byte c2 stack slots to words.
  int comp_words_on_stack = align_up(comp_args_on_stack*VMRegImpl::stack_slot_size, wordSize)>>LogBytesPerWord;

  if (comp_args_on_stack) {
    __ subptr(rsp, comp_words_on_stack * wordSize);
  }

  // Ensure compiled code always sees stack at proper alignment
  __ andptr(rsp, -16);

  // push the return address and misalign the stack that youngest frame always sees
  // as far as the placement of the call instruction
  __ push(rax);

  // Put saved SP in another register
  const Register saved_sp = rax;
  __ movptr(saved_sp, r11);

  // Will jump to the compiled code just as if compiled code was doing it.
  // Pre-load the register-jump target early, to schedule it better.
  __ movptr(r11, Address(rbx, in_bytes(Method::from_compiled_offset())));

#if INCLUDE_JVMCI
  if (EnableJVMCI) {
    // check if this call should be routed towards a specific entry point
    __ cmpptr(Address(r15_thread, in_bytes(JavaThread::jvmci_alternate_call_target_offset())), 0);
    Label no_alternative_target;
    __ jcc(Assembler::equal, no_alternative_target);
    __ movptr(r11, Address(r15_thread, in_bytes(JavaThread::jvmci_alternate_call_target_offset())));
    __ movptr(Address(r15_thread, in_bytes(JavaThread::jvmci_alternate_call_target_offset())), 0);
    __ bind(no_alternative_target);
  }
#endif // INCLUDE_JVMCI

  // Now generate the shuffle code.  Pick up all register args and move the
  // rest through the floating point stack top.
  for (int i = 0; i < total_args_passed; i++) {
    if (sig_bt[i] == T_VOID) {
      // Longs and doubles are passed in native word order, but misaligned
      // in the 32-bit build.
      assert(i > 0 && (sig_bt[i-1] == T_LONG || sig_bt[i-1] == T_DOUBLE), "missing half");
      continue;
    }

    // Pick up 0, 1 or 2 words from SP+offset.

    assert(!regs[i].second()->is_valid() || regs[i].first()->next() == regs[i].second(),
            "scrambled load targets?");
    // Load in argument order going down.
    int ld_off = (total_args_passed - i)*Interpreter::stackElementSize;
    // Point to interpreter value (vs. tag)
    int next_off = ld_off - Interpreter::stackElementSize;
    //
    //
    //
    VMReg r_1 = regs[i].first();
    VMReg r_2 = regs[i].second();
    if (!r_1->is_valid()) {
      assert(!r_2->is_valid(), "");
      continue;
    }
    if (r_1->is_stack()) {
      // Convert stack slot to an SP offset (+ wordSize to account for return address )
      int st_off = regs[i].first()->reg2stack()*VMRegImpl::stack_slot_size + wordSize;

      // We can use r13 as a temp here because compiled code doesn't need r13 as an input
      // and if we end up going thru a c2i because of a miss a reasonable value of r13
      // will be generated.
      if (!r_2->is_valid()) {
        // sign extend???
        __ movl(r13, Address(saved_sp, ld_off));
        __ movptr(Address(rsp, st_off), r13);
      } else {
        //
        // We are using two optoregs. This can be either T_OBJECT, T_ADDRESS, T_LONG, or T_DOUBLE
        // the interpreter allocates two slots but only uses one for thr T_LONG or T_DOUBLE case
        // So we must adjust where to pick up the data to match the interpreter.
        //
        // Interpreter local[n] == MSW, local[n+1] == LSW however locals
        // are accessed as negative so LSW is at LOW address

        // ld_off is MSW so get LSW
        const int offset = (sig_bt[i]==T_LONG||sig_bt[i]==T_DOUBLE)?
                           next_off : ld_off;
        __ movq(r13, Address(saved_sp, offset));
        // st_off is LSW (i.e. reg.first())
        __ movq(Address(rsp, st_off), r13);
      }
    } else if (r_1->is_Register()) {  // Register argument
      Register r = r_1->as_Register();
      assert(r != rax, "must be different");
      if (r_2->is_valid()) {
        //
        // We are using two VMRegs. This can be either T_OBJECT, T_ADDRESS, T_LONG, or T_DOUBLE
        // the interpreter allocates two slots but only uses one for thr T_LONG or T_DOUBLE case
        // So we must adjust where to pick up the data to match the interpreter.

        const int offset = (sig_bt[i]==T_LONG||sig_bt[i]==T_DOUBLE)?
                           next_off : ld_off;

        // this can be a misaligned move
        __ movq(r, Address(saved_sp, offset));
      } else {
        // sign extend and use a full word?
        __ movl(r, Address(saved_sp, ld_off));
      }
    } else {
      if (!r_2->is_valid()) {
        __ movflt(r_1->as_XMMRegister(), Address(saved_sp, ld_off));
      } else {
        __ movdbl(r_1->as_XMMRegister(), Address(saved_sp, next_off));
      }
    }
  }

  __ push_cont_fastpath(); // Set JavaThread::_cont_fastpath to the sp of the oldest interpreted frame we know about

  // 6243940 We might end up in handle_wrong_method if
  // the callee is deoptimized as we race thru here. If that
  // happens we don't want to take a safepoint because the
  // caller frame will look interpreted and arguments are now
  // "compiled" so it is much better to make this transition
  // invisible to the stack walking code. Unfortunately if
  // we try and find the callee by normal means a safepoint
  // is possible. So we stash the desired callee in the thread
  // and the vm will find there should this case occur.

  __ movptr(Address(r15_thread, JavaThread::callee_target_offset()), rbx);

  // put Method* where a c2i would expect should we end up there
  // only needed because eof c2 resolve stubs return Method* as a result in
  // rax
  __ mov(rax, rbx);
  __ jmp(r11);
}

// ---------------------------------------------------------------
AdapterHandlerEntry* SharedRuntime::generate_i2c2i_adapters(MacroAssembler *masm,
                                                            int total_args_passed,
                                                            int comp_args_on_stack,
                                                            const BasicType *sig_bt,
                                                            const VMRegPair *regs,
                                                            AdapterFingerPrint* fingerprint) {
  address i2c_entry = __ pc();

  gen_i2c_adapter(masm, total_args_passed, comp_args_on_stack, sig_bt, regs);

  // -------------------------------------------------------------------------
  // Generate a C2I adapter.  On entry we know rbx holds the Method* during calls
  // to the interpreter.  The args start out packed in the compiled layout.  They
  // need to be unpacked into the interpreter layout.  This will almost always
  // require some stack space.  We grow the current (compiled) stack, then repack
  // the args.  We  finally end in a jump to the generic interpreter entry point.
  // On exit from the interpreter, the interpreter will restore our SP (lest the
  // compiled code, which relies solely on SP and not RBP, get sick).

  address c2i_unverified_entry = __ pc();
  Label skip_fixup;
  Label ok;

  Register holder = rax;
  Register receiver = j_rarg0;
  Register temp = rbx;

  {
    __ load_klass(temp, receiver, rscratch1);
    __ cmpptr(temp, Address(holder, CompiledICHolder::holder_klass_offset()));
    __ movptr(rbx, Address(holder, CompiledICHolder::holder_metadata_offset()));
    __ jcc(Assembler::equal, ok);
    __ jump(RuntimeAddress(SharedRuntime::get_ic_miss_stub()));

    __ bind(ok);
    // Method might have been compiled since the call site was patched to
    // interpreted if that is the case treat it as a miss so we can get
    // the call site corrected.
    __ cmpptr(Address(rbx, in_bytes(Method::code_offset())), NULL_WORD);
    __ jcc(Assembler::equal, skip_fixup);
    __ jump(RuntimeAddress(SharedRuntime::get_ic_miss_stub()));
  }

  address c2i_entry = __ pc();

  // Class initialization barrier for static methods
  address c2i_no_clinit_check_entry = nullptr;
  if (VM_Version::supports_fast_class_init_checks()) {
    Label L_skip_barrier;
    Register method = rbx;

    { // Bypass the barrier for non-static methods
      Register flags = rscratch1;
      __ movl(flags, Address(method, Method::access_flags_offset()));
      __ testl(flags, JVM_ACC_STATIC);
      __ jcc(Assembler::zero, L_skip_barrier); // non-static
    }

    Register klass = rscratch1;
    __ load_method_holder(klass, method);
    __ clinit_barrier(klass, r15_thread, &L_skip_barrier /*L_fast_path*/);

    __ jump(RuntimeAddress(SharedRuntime::get_handle_wrong_method_stub())); // slow path

    __ bind(L_skip_barrier);
    c2i_no_clinit_check_entry = __ pc();
  }

  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->c2i_entry_barrier(masm);

  gen_c2i_adapter(masm, total_args_passed, comp_args_on_stack, sig_bt, regs, skip_fixup);

  return AdapterHandlerLibrary::new_entry(fingerprint, i2c_entry, c2i_entry, c2i_unverified_entry, c2i_no_clinit_check_entry);
}

int SharedRuntime::c_calling_convention(const BasicType *sig_bt,
                                         VMRegPair *regs,
                                         int total_args_passed) {

// We return the amount of VMRegImpl stack slots we need to reserve for all
// the arguments NOT counting out_preserve_stack_slots.

// NOTE: These arrays will have to change when c1 is ported
#ifdef _WIN64
    static const Register INT_ArgReg[Argument::n_int_register_parameters_c] = {
      c_rarg0, c_rarg1, c_rarg2, c_rarg3
    };
    static const XMMRegister FP_ArgReg[Argument::n_float_register_parameters_c] = {
      c_farg0, c_farg1, c_farg2, c_farg3
    };
#else
    static const Register INT_ArgReg[Argument::n_int_register_parameters_c] = {
      c_rarg0, c_rarg1, c_rarg2, c_rarg3, c_rarg4, c_rarg5
    };
    static const XMMRegister FP_ArgReg[Argument::n_float_register_parameters_c] = {
      c_farg0, c_farg1, c_farg2, c_farg3,
      c_farg4, c_farg5, c_farg6, c_farg7
    };
#endif // _WIN64


    uint int_args = 0;
    uint fp_args = 0;
    uint stk_args = 0; // inc by 2 each time

    for (int i = 0; i < total_args_passed; i++) {
      switch (sig_bt[i]) {
      case T_BOOLEAN:
      case T_CHAR:
      case T_BYTE:
      case T_SHORT:
      case T_INT:
        if (int_args < Argument::n_int_register_parameters_c) {
          regs[i].set1(INT_ArgReg[int_args++]->as_VMReg());
#ifdef _WIN64
          fp_args++;
          // Allocate slots for callee to stuff register args the stack.
          stk_args += 2;
#endif
        } else {
          regs[i].set1(VMRegImpl::stack2reg(stk_args));
          stk_args += 2;
        }
        break;
      case T_LONG:
        assert((i + 1) < total_args_passed && sig_bt[i + 1] == T_VOID, "expecting half");
        // fall through
      case T_OBJECT:
      case T_ARRAY:
      case T_ADDRESS:
      case T_METADATA:
        if (int_args < Argument::n_int_register_parameters_c) {
          regs[i].set2(INT_ArgReg[int_args++]->as_VMReg());
#ifdef _WIN64
          fp_args++;
          stk_args += 2;
#endif
        } else {
          regs[i].set2(VMRegImpl::stack2reg(stk_args));
          stk_args += 2;
        }
        break;
      case T_FLOAT:
        if (fp_args < Argument::n_float_register_parameters_c) {
          regs[i].set1(FP_ArgReg[fp_args++]->as_VMReg());
#ifdef _WIN64
          int_args++;
          // Allocate slots for callee to stuff register args the stack.
          stk_args += 2;
#endif
        } else {
          regs[i].set1(VMRegImpl::stack2reg(stk_args));
          stk_args += 2;
        }
        break;
      case T_DOUBLE:
        assert((i + 1) < total_args_passed && sig_bt[i + 1] == T_VOID, "expecting half");
        if (fp_args < Argument::n_float_register_parameters_c) {
          regs[i].set2(FP_ArgReg[fp_args++]->as_VMReg());
#ifdef _WIN64
          int_args++;
          // Allocate slots for callee to stuff register args the stack.
          stk_args += 2;
#endif
        } else {
          regs[i].set2(VMRegImpl::stack2reg(stk_args));
          stk_args += 2;
        }
        break;
      case T_VOID: // Halves of longs and doubles
        assert(i != 0 && (sig_bt[i - 1] == T_LONG || sig_bt[i - 1] == T_DOUBLE), "expecting half");
        regs[i].set_bad();
        break;
      default:
        ShouldNotReachHere();
        break;
      }
    }
#ifdef _WIN64
  // windows abi requires that we always allocate enough stack space
  // for 4 64bit registers to be stored down.
  if (stk_args < 8) {
    stk_args = 8;
  }
#endif // _WIN64

  return stk_args;
}

int SharedRuntime::vector_calling_convention(VMRegPair *regs,
                                             uint num_bits,
                                             uint total_args_passed) {
  assert(num_bits == 64 || num_bits == 128 || num_bits == 256 || num_bits == 512,
         "only certain vector sizes are supported for now");

  static const XMMRegister VEC_ArgReg[32] = {
     xmm0,  xmm1,  xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
     xmm8,  xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15,
    xmm16, xmm17, xmm18, xmm19, xmm20, xmm21, xmm22, xmm23,
    xmm24, xmm25, xmm26, xmm27, xmm28, xmm29, xmm30, xmm31
  };

  uint stk_args = 0;
  uint fp_args = 0;

  for (uint i = 0; i < total_args_passed; i++) {
    VMReg vmreg = VEC_ArgReg[fp_args++]->as_VMReg();
    int next_val = num_bits == 64 ? 1 : (num_bits == 128 ? 3 : (num_bits  == 256 ? 7 : 15));
    regs[i].set_pair(vmreg->next(next_val), vmreg);
  }

  return stk_args;
}

void SharedRuntime::save_native_result(MacroAssembler *masm, BasicType ret_type, int frame_slots) {
  // We always ignore the frame_slots arg and just use the space just below frame pointer
  // which by this time is free to use
  switch (ret_type) {
  case T_FLOAT:
    __ movflt(Address(rbp, -wordSize), xmm0);
    break;
  case T_DOUBLE:
    __ movdbl(Address(rbp, -wordSize), xmm0);
    break;
  case T_VOID:  break;
  default: {
    __ movptr(Address(rbp, -wordSize), rax);
    }
  }
}

void SharedRuntime::restore_native_result(MacroAssembler *masm, BasicType ret_type, int frame_slots) {
  // We always ignore the frame_slots arg and just use the space just below frame pointer
  // which by this time is free to use
  switch (ret_type) {
  case T_FLOAT:
    __ movflt(xmm0, Address(rbp, -wordSize));
    break;
  case T_DOUBLE:
    __ movdbl(xmm0, Address(rbp, -wordSize));
    break;
  case T_VOID:  break;
  default: {
    __ movptr(rax, Address(rbp, -wordSize));
    }
  }
}

static void save_args(MacroAssembler *masm, int arg_count, int first_arg, VMRegPair *args) {
    for ( int i = first_arg ; i < arg_count ; i++ ) {
      if (args[i].first()->is_Register()) {
        __ push(args[i].first()->as_Register());
      } else if (args[i].first()->is_XMMRegister()) {
        __ subptr(rsp, 2*wordSize);
        __ movdbl(Address(rsp, 0), args[i].first()->as_XMMRegister());
      }
    }
}

static void restore_args(MacroAssembler *masm, int arg_count, int first_arg, VMRegPair *args) {
    for ( int i = arg_count - 1 ; i >= first_arg ; i-- ) {
      if (args[i].first()->is_Register()) {
        __ pop(args[i].first()->as_Register());
      } else if (args[i].first()->is_XMMRegister()) {
        __ movdbl(args[i].first()->as_XMMRegister(), Address(rsp, 0));
        __ addptr(rsp, 2*wordSize);
      }
    }
}

static void verify_oop_args(MacroAssembler* masm,
                            const methodHandle& method,
                            const BasicType* sig_bt,
                            const VMRegPair* regs) {
  Register temp_reg = rbx;  // not part of any compiled calling seq
  if (VerifyOops) {
    for (int i = 0; i < method->size_of_parameters(); i++) {
      if (is_reference_type(sig_bt[i])) {
        VMReg r = regs[i].first();
        assert(r->is_valid(), "bad oop arg");
        if (r->is_stack()) {
          __ movptr(temp_reg, Address(rsp, r->reg2stack() * VMRegImpl::stack_slot_size + wordSize));
          __ verify_oop(temp_reg);
        } else {
          __ verify_oop(r->as_Register());
        }
      }
    }
  }
}

static void check_continuation_enter_argument(VMReg actual_vmreg,
                                              Register expected_reg,
                                              const char* name) {
  assert(!actual_vmreg->is_stack(), "%s cannot be on stack", name);
  assert(actual_vmreg->as_Register() == expected_reg,
         "%s is in unexpected register: %s instead of %s",
         name, actual_vmreg->as_Register()->name(), expected_reg->name());
}


//---------------------------- continuation_enter_setup ---------------------------
//
// Arguments:
//   None.
//
// Results:
//   rsp: pointer to blank ContinuationEntry
//
// Kills:
//   rax
//
static OopMap* continuation_enter_setup(MacroAssembler* masm, int& stack_slots) {
  assert(ContinuationEntry::size() % VMRegImpl::stack_slot_size == 0, "");
  assert(in_bytes(ContinuationEntry::cont_offset())  % VMRegImpl::stack_slot_size == 0, "");
  assert(in_bytes(ContinuationEntry::chunk_offset()) % VMRegImpl::stack_slot_size == 0, "");

  stack_slots += checked_cast<int>(ContinuationEntry::size()) / wordSize;
  __ subptr(rsp, checked_cast<int32_t>(ContinuationEntry::size()));

  int frame_size = (checked_cast<int>(ContinuationEntry::size()) + wordSize) / VMRegImpl::stack_slot_size;
  OopMap* map = new OopMap(frame_size, 0);

  __ movptr(rax, Address(r15_thread, JavaThread::cont_entry_offset()));
  __ movptr(Address(rsp, ContinuationEntry::parent_offset()), rax);
  __ movptr(Address(r15_thread, JavaThread::cont_entry_offset()), rsp);

  return map;
}

//---------------------------- fill_continuation_entry ---------------------------
//
// Arguments:
//   rsp: pointer to blank Continuation entry
//   reg_cont_obj: pointer to the continuation
//   reg_flags: flags
//
// Results:
//   rsp: pointer to filled out ContinuationEntry
//
// Kills:
//   rax
//
static void fill_continuation_entry(MacroAssembler* masm, Register reg_cont_obj, Register reg_flags) {
  assert_different_registers(rax, reg_cont_obj, reg_flags);
#ifdef ASSERT
  __ movl(Address(rsp, ContinuationEntry::cookie_offset()), ContinuationEntry::cookie_value());
#endif
  __ movptr(Address(rsp, ContinuationEntry::cont_offset()), reg_cont_obj);
  __ movl  (Address(rsp, ContinuationEntry::flags_offset()), reg_flags);
  __ movptr(Address(rsp, ContinuationEntry::chunk_offset()), 0);
  __ movl(Address(rsp, ContinuationEntry::argsize_offset()), 0);
  __ movl(Address(rsp, ContinuationEntry::pin_count_offset()), 0);

  __ movptr(rax, Address(r15_thread, JavaThread::cont_fastpath_offset()));
  __ movptr(Address(rsp, ContinuationEntry::parent_cont_fastpath_offset()), rax);
  __ movq(rax, Address(r15_thread, JavaThread::held_monitor_count_offset()));
  __ movq(Address(rsp, ContinuationEntry::parent_held_monitor_count_offset()), rax);

  __ movptr(Address(r15_thread, JavaThread::cont_fastpath_offset()), 0);
  __ movq(Address(r15_thread, JavaThread::held_monitor_count_offset()), 0);
}

//---------------------------- continuation_enter_cleanup ---------------------------
//
// Arguments:
//   rsp: pointer to the ContinuationEntry
//
// Results:
//   rsp: pointer to the spilled rbp in the entry frame
//
// Kills:
//   rbx
//
void static continuation_enter_cleanup(MacroAssembler* masm) {
#ifdef ASSERT
  Label L_good_sp;
  __ cmpptr(rsp, Address(r15_thread, JavaThread::cont_entry_offset()));
  __ jcc(Assembler::equal, L_good_sp);
  __ stop("Incorrect rsp at continuation_enter_cleanup");
  __ bind(L_good_sp);
#endif

  __ movptr(rbx, Address(rsp, ContinuationEntry::parent_cont_fastpath_offset()));
  __ movptr(Address(r15_thread, JavaThread::cont_fastpath_offset()), rbx);
  __ movq(rbx, Address(rsp, ContinuationEntry::parent_held_monitor_count_offset()));
  __ movq(Address(r15_thread, JavaThread::held_monitor_count_offset()), rbx);

  __ movptr(rbx, Address(rsp, ContinuationEntry::parent_offset()));
  __ movptr(Address(r15_thread, JavaThread::cont_entry_offset()), rbx);
  __ addptr(rsp, checked_cast<int32_t>(ContinuationEntry::size()));
}

static void gen_continuation_enter(MacroAssembler* masm,
                                   const VMRegPair* regs,
                                   int& exception_offset,
                                   OopMapSet* oop_maps,
                                   int& frame_complete,
                                   int& stack_slots,
                                   int& interpreted_entry_offset,
                                   int& compiled_entry_offset) {

  // enterSpecial(Continuation c, boolean isContinue, boolean isVirtualThread)
  int pos_cont_obj   = 0;
  int pos_is_cont    = 1;
  int pos_is_virtual = 2;

  // The platform-specific calling convention may present the arguments in various registers.
  // To simplify the rest of the code, we expect the arguments to reside at these known
  // registers, and we additionally check the placement here in case calling convention ever
  // changes.
  Register reg_cont_obj   = c_rarg1;
  Register reg_is_cont    = c_rarg2;
  Register reg_is_virtual = c_rarg3;

  check_continuation_enter_argument(regs[pos_cont_obj].first(),   reg_cont_obj,   "Continuation object");
  check_continuation_enter_argument(regs[pos_is_cont].first(),    reg_is_cont,    "isContinue");
  check_continuation_enter_argument(regs[pos_is_virtual].first(), reg_is_virtual, "isVirtualThread");

  // Utility methods kill rax, make sure there are no collisions
  assert_different_registers(rax, reg_cont_obj, reg_is_cont, reg_is_virtual);

  AddressLiteral resolve(SharedRuntime::get_resolve_static_call_stub(),
                         relocInfo::static_call_type);

  address start = __ pc();

  Label L_thaw, L_exit;

  // i2i entry used at interp_only_mode only
  interpreted_entry_offset = __ pc() - start;
  {
#ifdef ASSERT
    Label is_interp_only;
    __ cmpb(Address(r15_thread, JavaThread::interp_only_mode_offset()), 0);
    __ jcc(Assembler::notEqual, is_interp_only);
    __ stop("enterSpecial interpreter entry called when not in interp_only_mode");
    __ bind(is_interp_only);
#endif

    __ pop(rax); // return address
    // Read interpreter arguments into registers (this is an ad-hoc i2c adapter)
    __ movptr(c_rarg1, Address(rsp, Interpreter::stackElementSize*2));
    __ movl(c_rarg2,   Address(rsp, Interpreter::stackElementSize*1));
    __ movl(c_rarg3,   Address(rsp, Interpreter::stackElementSize*0));
    __ andptr(rsp, -16); // Ensure compiled code always sees stack at proper alignment
    __ push(rax); // return address
    __ push_cont_fastpath();

    __ enter();

    stack_slots = 2; // will be adjusted in setup
    OopMap* map = continuation_enter_setup(masm, stack_slots);
    // The frame is complete here, but we only record it for the compiled entry, so the frame would appear unsafe,
    // but that's okay because at the very worst we'll miss an async sample, but we're in interp_only_mode anyway.

    __ verify_oop(reg_cont_obj);

    fill_continuation_entry(masm, reg_cont_obj, reg_is_virtual);

    // If continuation, call to thaw. Otherwise, resolve the call and exit.
    __ testptr(reg_is_cont, reg_is_cont);
    __ jcc(Assembler::notZero, L_thaw);

    // --- Resolve path

    // Make sure the call is patchable
    __ align(BytesPerWord, __ offset() + NativeCall::displacement_offset);
    // Emit stub for static call
    CodeBuffer* cbuf = masm->code_section()->outer();
    address stub = CompiledStaticCall::emit_to_interp_stub(*cbuf, __ pc());
    if (stub == nullptr) {
      fatal("CodeCache is full at gen_continuation_enter");
    }
    __ call(resolve);
    oop_maps->add_gc_map(__ pc() - start, map);
    __ post_call_nop();

    __ jmp(L_exit);
  }

  // compiled entry
  __ align(CodeEntryAlignment);
  compiled_entry_offset = __ pc() - start;
  __ enter();

  stack_slots = 2; // will be adjusted in setup
  OopMap* map = continuation_enter_setup(masm, stack_slots);

  // Frame is now completed as far as size and linkage.
  frame_complete = __ pc() - start;

  __ verify_oop(reg_cont_obj);

  fill_continuation_entry(masm, reg_cont_obj, reg_is_virtual);

  // If isContinue, call to thaw. Otherwise, call Continuation.enter(Continuation c, boolean isContinue)
  __ testptr(reg_is_cont, reg_is_cont);
  __ jccb(Assembler::notZero, L_thaw);

  // --- call Continuation.enter(Continuation c, boolean isContinue)

  // Make sure the call is patchable
  __ align(BytesPerWord, __ offset() + NativeCall::displacement_offset);

  // Emit stub for static call
  CodeBuffer* cbuf = masm->code_section()->outer();
  address stub = CompiledStaticCall::emit_to_interp_stub(*cbuf, __ pc());
  if (stub == nullptr) {
    fatal("CodeCache is full at gen_continuation_enter");
  }

  // The call needs to be resolved. There's a special case for this in
  // SharedRuntime::find_callee_info_helper() which calls
  // LinkResolver::resolve_continuation_enter() which resolves the call to
  // Continuation.enter(Continuation c, boolean isContinue).
  __ call(resolve);

  oop_maps->add_gc_map(__ pc() - start, map);
  __ post_call_nop();

  __ jmpb(L_exit);

  // --- Thawing path

  __ bind(L_thaw);

  __ call(RuntimeAddress(StubRoutines::cont_thaw()));

  ContinuationEntry::_return_pc_offset = __ pc() - start;
  oop_maps->add_gc_map(__ pc() - start, map->deep_copy());
  __ post_call_nop();

  // --- Normal exit (resolve/thawing)

  __ bind(L_exit);

  continuation_enter_cleanup(masm);
  __ pop(rbp);
  __ ret(0);

  // --- Exception handling path

  exception_offset = __ pc() - start;

  continuation_enter_cleanup(masm);
  __ pop(rbp);

  __ movptr(c_rarg0, r15_thread);
  __ movptr(c_rarg1, Address(rsp, 0)); // return address

  // rax still holds the original exception oop, save it before the call
  __ push(rax);

  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), 2);
  __ movptr(rbx, rax);

  // Continue at exception handler:
  //   rax: exception oop
  //   rbx: exception handler
  //   rdx: exception pc
  __ pop(rax);
  __ verify_oop(rax);
  __ pop(rdx);
  __ jmp(rbx);
}

static void gen_continuation_yield(MacroAssembler* masm,
                                   const VMRegPair* regs,
                                   OopMapSet* oop_maps,
                                   int& frame_complete,
                                   int& stack_slots,
                                   int& compiled_entry_offset) {
  enum layout {
    rbp_off,
    rbpH_off,
    return_off,
    return_off2,
    framesize // inclusive of return address
  };
  stack_slots = framesize /  VMRegImpl::slots_per_word;
  assert(stack_slots == 2, "recheck layout");

  address start = __ pc();
  compiled_entry_offset = __ pc() - start;
  __ enter();
  address the_pc = __ pc();

  frame_complete = the_pc - start;

  // This nop must be exactly at the PC we push into the frame info.
  // We use this nop for fast CodeBlob lookup, associate the OopMap
  // with it right away.
  __ post_call_nop();
  OopMap* map = new OopMap(framesize, 1);
  oop_maps->add_gc_map(frame_complete, map);

  __ set_last_Java_frame(rsp, rbp, the_pc, rscratch1);
  __ movptr(c_rarg0, r15_thread);
  __ movptr(c_rarg1, rsp);
  __ call_VM_leaf(Continuation::freeze_entry(), 2);
  __ reset_last_Java_frame(true);

  Label L_pinned;

  __ testptr(rax, rax);
  __ jcc(Assembler::notZero, L_pinned);

  __ movptr(rsp, Address(r15_thread, JavaThread::cont_entry_offset()));
  continuation_enter_cleanup(masm);
  __ pop(rbp);
  __ ret(0);

  __ bind(L_pinned);

  // Pinned, return to caller

  // handle pending exception thrown by freeze
  __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()), NULL_WORD);
  Label ok;
  __ jcc(Assembler::equal, ok);
  __ leave();
  __ jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
  __ bind(ok);

  __ leave();
  __ ret(0);
}

static void gen_special_dispatch(MacroAssembler* masm,
                                 const methodHandle& method,
                                 const BasicType* sig_bt,
                                 const VMRegPair* regs) {
  verify_oop_args(masm, method, sig_bt, regs);
  vmIntrinsics::ID iid = method->intrinsic_id();

  // Now write the args into the outgoing interpreter space
  bool     has_receiver   = false;
  Register receiver_reg   = noreg;
  int      member_arg_pos = -1;
  Register member_reg     = noreg;
  int      ref_kind       = MethodHandles::signature_polymorphic_intrinsic_ref_kind(iid);
  if (ref_kind != 0) {
    member_arg_pos = method->size_of_parameters() - 1;  // trailing MemberName argument
    member_reg = rbx;  // known to be free at this point
    has_receiver = MethodHandles::ref_kind_has_receiver(ref_kind);
  } else if (iid == vmIntrinsics::_invokeBasic) {
    has_receiver = true;
  } else if (iid == vmIntrinsics::_linkToNative) {
    member_arg_pos = method->size_of_parameters() - 1;  // trailing NativeEntryPoint argument
    member_reg = rbx;  // known to be free at this point
  } else {
    fatal("unexpected intrinsic id %d", vmIntrinsics::as_int(iid));
  }

  if (member_reg != noreg) {
    // Load the member_arg into register, if necessary.
    SharedRuntime::check_member_name_argument_is_last_argument(method, sig_bt, regs);
    VMReg r = regs[member_arg_pos].first();
    if (r->is_stack()) {
      __ movptr(member_reg, Address(rsp, r->reg2stack() * VMRegImpl::stack_slot_size + wordSize));
    } else {
      // no data motion is needed
      member_reg = r->as_Register();
    }
  }

  if (has_receiver) {
    // Make sure the receiver is loaded into a register.
    assert(method->size_of_parameters() > 0, "oob");
    assert(sig_bt[0] == T_OBJECT, "receiver argument must be an object");
    VMReg r = regs[0].first();
    assert(r->is_valid(), "bad receiver arg");
    if (r->is_stack()) {
      // Porting note:  This assumes that compiled calling conventions always
      // pass the receiver oop in a register.  If this is not true on some
      // platform, pick a temp and load the receiver from stack.
      fatal("receiver always in a register");
      receiver_reg = j_rarg0;  // known to be free at this point
      __ movptr(receiver_reg, Address(rsp, r->reg2stack() * VMRegImpl::stack_slot_size + wordSize));
    } else {
      // no data motion is needed
      receiver_reg = r->as_Register();
    }
  }

  // Figure out which address we are really jumping to:
  MethodHandles::generate_method_handle_dispatch(masm, iid,
                                                 receiver_reg, member_reg, /*for_compiler_entry:*/ true);
}

// ---------------------------------------------------------------------------
// Generate a native wrapper for a given method.  The method takes arguments
// in the Java compiled code convention, marshals them to the native
// convention (handlizes oops, etc), transitions to native, makes the call,
// returns to java state (possibly blocking), unhandlizes any result and
// returns.
//
// Critical native functions are a shorthand for the use of
// GetPrimtiveArrayCritical and disallow the use of any other JNI
// functions.  The wrapper is expected to unpack the arguments before
// passing them to the callee. Critical native functions leave the state _in_Java,
// since they cannot stop for GC.
// Some other parts of JNI setup are skipped like the tear down of the JNI handle
// block and the check for pending exceptions it's impossible for them
// to be thrown.
//
nmethod* SharedRuntime::generate_native_wrapper(MacroAssembler* masm,
                                                const methodHandle& method,
                                                int compile_id,
                                                BasicType* in_sig_bt,
                                                VMRegPair* in_regs,
                                                BasicType ret_type) {
  if (method->is_continuation_native_intrinsic()) {
    int exception_offset = -1;
    OopMapSet* oop_maps = new OopMapSet();
    int frame_complete = -1;
    int stack_slots = -1;
    int interpreted_entry_offset = -1;
    int vep_offset = -1;
    if (method->is_continuation_enter_intrinsic()) {
      gen_continuation_enter(masm,
                             in_regs,
                             exception_offset,
                             oop_maps,
                             frame_complete,
                             stack_slots,
                             interpreted_entry_offset,
                             vep_offset);
    } else if (method->is_continuation_yield_intrinsic()) {
      gen_continuation_yield(masm,
                             in_regs,
                             oop_maps,
                             frame_complete,
                             stack_slots,
                             vep_offset);
    } else {
      guarantee(false, "Unknown Continuation native intrinsic");
    }

#ifdef ASSERT
    if (method->is_continuation_enter_intrinsic()) {
      assert(interpreted_entry_offset != -1, "Must be set");
      assert(exception_offset != -1,         "Must be set");
    } else {
      assert(interpreted_entry_offset == -1, "Must be unset");
      assert(exception_offset == -1,         "Must be unset");
    }
    assert(frame_complete != -1,    "Must be set");
    assert(stack_slots != -1,       "Must be set");
    assert(vep_offset != -1,        "Must be set");
#endif

    __ flush();
    nmethod* nm = nmethod::new_native_nmethod(method,
                                              compile_id,
                                              masm->code(),
                                              vep_offset,
                                              frame_complete,
                                              stack_slots,
                                              in_ByteSize(-1),
                                              in_ByteSize(-1),
                                              oop_maps,
                                              exception_offset);
    if (method->is_continuation_enter_intrinsic()) {
      ContinuationEntry::set_enter_code(nm, interpreted_entry_offset);
    } else if (method->is_continuation_yield_intrinsic()) {
      _cont_doYield_stub = nm;
    }
    return nm;
  }

  if (method->is_method_handle_intrinsic()) {
    vmIntrinsics::ID iid = method->intrinsic_id();
    intptr_t start = (intptr_t)__ pc();
    int vep_offset = ((intptr_t)__ pc()) - start;
    gen_special_dispatch(masm,
                         method,
                         in_sig_bt,
                         in_regs);
    int frame_complete = ((intptr_t)__ pc()) - start;  // not complete, period
    __ flush();
    int stack_slots = SharedRuntime::out_preserve_stack_slots();  // no out slots at all, actually
    return nmethod::new_native_nmethod(method,
                                       compile_id,
                                       masm->code(),
                                       vep_offset,
                                       frame_complete,
                                       stack_slots / VMRegImpl::slots_per_word,
                                       in_ByteSize(-1),
                                       in_ByteSize(-1),
                                       nullptr);
  }
  address native_func = method->native_function();
  assert(native_func != nullptr, "must have function");

  // An OopMap for lock (and class if static)
  OopMapSet *oop_maps = new OopMapSet();
  intptr_t start = (intptr_t)__ pc();

  // We have received a description of where all the java arg are located
  // on entry to the wrapper. We need to convert these args to where
  // the jni function will expect them. To figure out where they go
  // we convert the java signature to a C signature by inserting
  // the hidden arguments as arg[0] and possibly arg[1] (static method)

  const int total_in_args = method->size_of_parameters();
  int total_c_args = total_in_args + (method->is_static() ? 2 : 1);

  BasicType* out_sig_bt = NEW_RESOURCE_ARRAY(BasicType, total_c_args);
  VMRegPair* out_regs   = NEW_RESOURCE_ARRAY(VMRegPair, total_c_args);
  BasicType* in_elem_bt = nullptr;

  int argc = 0;
  out_sig_bt[argc++] = T_ADDRESS;
  if (method->is_static()) {
    out_sig_bt[argc++] = T_OBJECT;
  }

  for (int i = 0; i < total_in_args ; i++ ) {
    out_sig_bt[argc++] = in_sig_bt[i];
  }

  // Now figure out where the args must be stored and how much stack space
  // they require.
  int out_arg_slots;
  out_arg_slots = c_calling_convention(out_sig_bt, out_regs, total_c_args);

  // Compute framesize for the wrapper.  We need to handlize all oops in
  // incoming registers

  // Calculate the total number of stack slots we will need.

  // First count the abi requirement plus all of the outgoing args
  int stack_slots = SharedRuntime::out_preserve_stack_slots() + out_arg_slots;

  // Now the space for the inbound oop handle area
  int total_save_slots = 6 * VMRegImpl::slots_per_word;  // 6 arguments passed in registers

  int oop_handle_offset = stack_slots;
  stack_slots += total_save_slots;

  // Now any space we need for handlizing a klass if static method

  int klass_slot_offset = 0;
  int klass_offset = -1;
  int lock_slot_offset = 0;
  bool is_static = false;

  if (method->is_static()) {
    klass_slot_offset = stack_slots;
    stack_slots += VMRegImpl::slots_per_word;
    klass_offset = klass_slot_offset * VMRegImpl::stack_slot_size;
    is_static = true;
  }

  // Plus a lock if needed

  if (method->is_synchronized()) {
    lock_slot_offset = stack_slots;
    stack_slots += VMRegImpl::slots_per_word;
  }

  // Now a place (+2) to save return values or temp during shuffling
  // + 4 for return address (which we own) and saved rbp
  stack_slots += 6;

  // Ok The space we have allocated will look like:
  //
  //
  // FP-> |                     |
  //      |---------------------|
  //      | 2 slots for moves   |
  //      |---------------------|
  //      | lock box (if sync)  |
  //      |---------------------| <- lock_slot_offset
  //      | klass (if static)   |
  //      |---------------------| <- klass_slot_offset
  //      | oopHandle area      |
  //      |---------------------| <- oop_handle_offset (6 java arg registers)
  //      | outbound memory     |
  //      | based arguments     |
  //      |                     |
  //      |---------------------|
  //      |                     |
  // SP-> | out_preserved_slots |
  //
  //


  // Now compute actual number of stack words we need rounding to make
  // stack properly aligned.
  stack_slots = align_up(stack_slots, StackAlignmentInSlots);

  int stack_size = stack_slots * VMRegImpl::stack_slot_size;

  // First thing make an ic check to see if we should even be here

  // We are free to use all registers as temps without saving them and
  // restoring them except rbp. rbp is the only callee save register
  // as far as the interpreter and the compiler(s) are concerned.


  const Register ic_reg = rax;
  const Register receiver = j_rarg0;

  Label hit;
  Label exception_pending;

  assert_different_registers(ic_reg, receiver, rscratch1, rscratch2);
  __ verify_oop(receiver);
  __ load_klass(rscratch1, receiver, rscratch2);
  __ cmpq(ic_reg, rscratch1);
  __ jcc(Assembler::equal, hit);

  __ jump(RuntimeAddress(SharedRuntime::get_ic_miss_stub()));

  // Verified entry point must be aligned
  __ align(8);

  __ bind(hit);

  int vep_offset = ((intptr_t)__ pc()) - start;

  if (VM_Version::supports_fast_class_init_checks() && method->needs_clinit_barrier()) {
    Label L_skip_barrier;
    Register klass = r10;
    __ mov_metadata(klass, method->method_holder()); // InstanceKlass*
    __ clinit_barrier(klass, r15_thread, &L_skip_barrier /*L_fast_path*/);

    __ jump(RuntimeAddress(SharedRuntime::get_handle_wrong_method_stub())); // slow path

    __ bind(L_skip_barrier);
  }

#ifdef COMPILER1
  // For Object.hashCode, System.identityHashCode try to pull hashCode from object header if available.
  if ((InlineObjectHash && method->intrinsic_id() == vmIntrinsics::_hashCode) || (method->intrinsic_id() == vmIntrinsics::_identityHashCode)) {
    inline_check_hashcode_from_object_header(masm, method, j_rarg0 /*obj_reg*/, rax /*result*/);
  }
#endif // COMPILER1

  // The instruction at the verified entry point must be 5 bytes or longer
  // because it can be patched on the fly by make_non_entrant. The stack bang
  // instruction fits that requirement.

  // Generate stack overflow check
  __ bang_stack_with_offset((int)StackOverflow::stack_shadow_zone_size());

  // Generate a new frame for the wrapper.
  __ enter();
  // -2 because return address is already present and so is saved rbp
  __ subptr(rsp, stack_size - 2*wordSize);

  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  // native wrapper is not hot enough to micro optimize the nmethod entry barrier with an out-of-line stub
  bs->nmethod_entry_barrier(masm, nullptr /* slow_path */, nullptr /* continuation */);

  // Frame is now completed as far as size and linkage.
  int frame_complete = ((intptr_t)__ pc()) - start;

    if (UseRTMLocking) {
      // Abort RTM transaction before calling JNI
      // because critical section will be large and will be
      // aborted anyway. Also nmethod could be deoptimized.
      __ xabort(0);
    }

#ifdef ASSERT
  __ check_stack_alignment(rsp, "improperly aligned stack");
#endif /* ASSERT */


  // We use r14 as the oop handle for the receiver/klass
  // It is callee save so it survives the call to native

  const Register oop_handle_reg = r14;

  //
  // We immediately shuffle the arguments so that any vm call we have to
  // make from here on out (sync slow path, jvmti, etc.) we will have
  // captured the oops from our caller and have a valid oopMap for
  // them.

  // -----------------
  // The Grand Shuffle

  // The Java calling convention is either equal (linux) or denser (win64) than the
  // c calling convention. However the because of the jni_env argument the c calling
  // convention always has at least one more (and two for static) arguments than Java.
  // Therefore if we move the args from java -> c backwards then we will never have
  // a register->register conflict and we don't have to build a dependency graph
  // and figure out how to break any cycles.
  //

  // Record esp-based slot for receiver on stack for non-static methods
  int receiver_offset = -1;

  // This is a trick. We double the stack slots so we can claim
  // the oops in the caller's frame. Since we are sure to have
  // more args than the caller doubling is enough to make
  // sure we can capture all the incoming oop args from the
  // caller.
  //
  OopMap* map = new OopMap(stack_slots * 2, 0 /* arg_slots*/);

  // Mark location of rbp (someday)
  // map->set_callee_saved(VMRegImpl::stack2reg( stack_slots - 2), stack_slots * 2, 0, vmreg(rbp));

  // Use eax, ebx as temporaries during any memory-memory moves we have to do
  // All inbound args are referenced based on rbp and all outbound args via rsp.


#ifdef ASSERT
  bool reg_destroyed[Register::number_of_registers];
  bool freg_destroyed[XMMRegister::number_of_registers];
  for ( int r = 0 ; r < Register::number_of_registers ; r++ ) {
    reg_destroyed[r] = false;
  }
  for ( int f = 0 ; f < XMMRegister::number_of_registers ; f++ ) {
    freg_destroyed[f] = false;
  }

#endif /* ASSERT */

  // For JNI natives the incoming and outgoing registers are offset upwards.
  GrowableArray<int> arg_order(2 * total_in_args);

  VMRegPair tmp_vmreg;
  tmp_vmreg.set2(rbx->as_VMReg());

  for (int i = total_in_args - 1, c_arg = total_c_args - 1; i >= 0; i--, c_arg--) {
    arg_order.push(i);
    arg_order.push(c_arg);
  }

  int temploc = -1;
  for (int ai = 0; ai < arg_order.length(); ai += 2) {
    int i = arg_order.at(ai);
    int c_arg = arg_order.at(ai + 1);
    __ block_comment(err_msg("move %d -> %d", i, c_arg));
#ifdef ASSERT
    if (in_regs[i].first()->is_Register()) {
      assert(!reg_destroyed[in_regs[i].first()->as_Register()->encoding()], "destroyed reg!");
    } else if (in_regs[i].first()->is_XMMRegister()) {
      assert(!freg_destroyed[in_regs[i].first()->as_XMMRegister()->encoding()], "destroyed reg!");
    }
    if (out_regs[c_arg].first()->is_Register()) {
      reg_destroyed[out_regs[c_arg].first()->as_Register()->encoding()] = true;
    } else if (out_regs[c_arg].first()->is_XMMRegister()) {
      freg_destroyed[out_regs[c_arg].first()->as_XMMRegister()->encoding()] = true;
    }
#endif /* ASSERT */
    switch (in_sig_bt[i]) {
      case T_ARRAY:
      case T_OBJECT:
        __ object_move(map, oop_handle_offset, stack_slots, in_regs[i], out_regs[c_arg],
                    ((i == 0) && (!is_static)),
                    &receiver_offset);
        break;
      case T_VOID:
        break;

      case T_FLOAT:
        __ float_move(in_regs[i], out_regs[c_arg]);
          break;

      case T_DOUBLE:
        assert( i + 1 < total_in_args &&
                in_sig_bt[i + 1] == T_VOID &&
                out_sig_bt[c_arg+1] == T_VOID, "bad arg list");
        __ double_move(in_regs[i], out_regs[c_arg]);
        break;

      case T_LONG :
        __ long_move(in_regs[i], out_regs[c_arg]);
        break;

      case T_ADDRESS: assert(false, "found T_ADDRESS in java args");

      default:
        __ move32_64(in_regs[i], out_regs[c_arg]);
    }
  }

  int c_arg;

  // Pre-load a static method's oop into r14.  Used both by locking code and
  // the normal JNI call code.
  // point c_arg at the first arg that is already loaded in case we
  // need to spill before we call out
  c_arg = total_c_args - total_in_args;

  if (method->is_static()) {

    //  load oop into a register
    __ movoop(oop_handle_reg, JNIHandles::make_local(method->method_holder()->java_mirror()));

    // Now handlize the static class mirror it's known not-null.
    __ movptr(Address(rsp, klass_offset), oop_handle_reg);
    map->set_oop(VMRegImpl::stack2reg(klass_slot_offset));

    // Now get the handle
    __ lea(oop_handle_reg, Address(rsp, klass_offset));
    // store the klass handle as second argument
    __ movptr(c_rarg1, oop_handle_reg);
    // and protect the arg if we must spill
    c_arg--;
  }

  // Change state to native (we save the return address in the thread, since it might not
  // be pushed on the stack when we do a stack traversal). It is enough that the pc()
  // points into the right code segment. It does not have to be the correct return pc.
  // We use the same pc/oopMap repeatedly when we call out

  intptr_t the_pc = (intptr_t) __ pc();
  oop_maps->add_gc_map(the_pc - start, map);

  __ set_last_Java_frame(rsp, noreg, (address)the_pc, rscratch1);


  // We have all of the arguments setup at this point. We must not touch any register
  // argument registers at this point (what if we save/restore them there are no oop?

  {
    SkipIfEqual skip(masm, &DTraceMethodProbes, false, rscratch1);
    // protect the args we've loaded
    save_args(masm, total_c_args, c_arg, out_regs);
    __ mov_metadata(c_rarg1, method());
    __ call_VM_leaf(
      CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_entry),
      r15_thread, c_rarg1);
    restore_args(masm, total_c_args, c_arg, out_regs);
  }

  // RedefineClasses() tracing support for obsolete method entry
  if (log_is_enabled(Trace, redefine, class, obsolete)) {
    // protect the args we've loaded
    save_args(masm, total_c_args, c_arg, out_regs);
    __ mov_metadata(c_rarg1, method());
    __ call_VM_leaf(
      CAST_FROM_FN_PTR(address, SharedRuntime::rc_trace_method_entry),
      r15_thread, c_rarg1);
    restore_args(masm, total_c_args, c_arg, out_regs);
  }

  // Lock a synchronized method

  // Register definitions used by locking and unlocking

  const Register swap_reg = rax;  // Must use rax for cmpxchg instruction
  const Register obj_reg  = rbx;  // Will contain the oop
  const Register lock_reg = r13;  // Address of compiler lock object (BasicLock)
  const Register old_hdr  = r13;  // value of old header at unlock time

  Label slow_path_lock;
  Label lock_done;

  if (method->is_synchronized()) {
    Label count_mon;

    const int mark_word_offset = BasicLock::displaced_header_offset_in_bytes();

    // Get the handle (the 2nd argument)
    __ mov(oop_handle_reg, c_rarg1);

    // Get address of the box

    __ lea(lock_reg, Address(rsp, lock_slot_offset * VMRegImpl::stack_slot_size));

    // Load the oop from the handle
    __ movptr(obj_reg, Address(oop_handle_reg, 0));

    if (LockingMode == LM_MONITOR) {
      __ jmp(slow_path_lock);
    } else if (LockingMode == LM_LEGACY) {
      // Load immediate 1 into swap_reg %rax
      __ movl(swap_reg, 1);

      // Load (object->mark() | 1) into swap_reg %rax
      __ orptr(swap_reg, Address(obj_reg, oopDesc::mark_offset_in_bytes()));

      // Save (object->mark() | 1) into BasicLock's displaced header
      __ movptr(Address(lock_reg, mark_word_offset), swap_reg);

      // src -> dest iff dest == rax else rax <- dest
      __ lock();
      __ cmpxchgptr(lock_reg, Address(obj_reg, oopDesc::mark_offset_in_bytes()));
      __ jcc(Assembler::equal, count_mon);

      // Hmm should this move to the slow path code area???

      // Test if the oopMark is an obvious stack pointer, i.e.,
      //  1) (mark & 3) == 0, and
      //  2) rsp <= mark < mark + os::pagesize()
      // These 3 tests can be done by evaluating the following
      // expression: ((mark - rsp) & (3 - os::vm_page_size())),
      // assuming both stack pointer and pagesize have their
      // least significant 2 bits clear.
      // NOTE: the oopMark is in swap_reg %rax as the result of cmpxchg

      __ subptr(swap_reg, rsp);
      __ andptr(swap_reg, 3 - (int)os::vm_page_size());

      // Save the test result, for recursive case, the result is zero
      __ movptr(Address(lock_reg, mark_word_offset), swap_reg);
      __ jcc(Assembler::notEqual, slow_path_lock);
    } else {
      assert(LockingMode == LM_LIGHTWEIGHT, "must be");
      // Load object header
      __ movptr(swap_reg, Address(obj_reg, oopDesc::mark_offset_in_bytes()));
      __ lightweight_lock(obj_reg, swap_reg, r15_thread, rscratch1, slow_path_lock);
    }
    __ bind(count_mon);
    __ inc_held_monitor_count();

    // Slow path will re-enter here
    __ bind(lock_done);
  }

  // Finally just about ready to make the JNI call

  // get JNIEnv* which is first argument to native
  __ lea(c_rarg0, Address(r15_thread, in_bytes(JavaThread::jni_environment_offset())));

  // Now set thread in native
  __ movl(Address(r15_thread, JavaThread::thread_state_offset()), _thread_in_native);

  __ call(RuntimeAddress(native_func));

  // Verify or restore cpu control state after JNI call
  __ restore_cpu_control_state_after_jni(rscratch1);

  // Unpack native results.
  switch (ret_type) {
  case T_BOOLEAN: __ c2bool(rax);            break;
  case T_CHAR   : __ movzwl(rax, rax);      break;
  case T_BYTE   : __ sign_extend_byte (rax); break;
  case T_SHORT  : __ sign_extend_short(rax); break;
  case T_INT    : /* nothing to do */        break;
  case T_DOUBLE :
  case T_FLOAT  :
    // Result is in xmm0 we'll save as needed
    break;
  case T_ARRAY:                 // Really a handle
  case T_OBJECT:                // Really a handle
      break; // can't de-handlize until after safepoint check
  case T_VOID: break;
  case T_LONG: break;
  default       : ShouldNotReachHere();
  }

  Label after_transition;

  // Switch thread to "native transition" state before reading the synchronization state.
  // This additional state is necessary because reading and testing the synchronization
  // state is not atomic w.r.t. GC, as this scenario demonstrates:
  //     Java thread A, in _thread_in_native state, loads _not_synchronized and is preempted.
  //     VM thread changes sync state to synchronizing and suspends threads for GC.
  //     Thread A is resumed to finish this native method, but doesn't block here since it
  //     didn't see any synchronization is progress, and escapes.
  __ movl(Address(r15_thread, JavaThread::thread_state_offset()), _thread_in_native_trans);

  // Force this write out before the read below
  if (!UseSystemMemoryBarrier) {
    __ membar(Assembler::Membar_mask_bits(
              Assembler::LoadLoad | Assembler::LoadStore |
              Assembler::StoreLoad | Assembler::StoreStore));
  }

  // check for safepoint operation in progress and/or pending suspend requests
  {
    Label Continue;
    Label slow_path;

    __ safepoint_poll(slow_path, r15_thread, true /* at_return */, false /* in_nmethod */);

    __ cmpl(Address(r15_thread, JavaThread::suspend_flags_offset()), 0);
    __ jcc(Assembler::equal, Continue);
    __ bind(slow_path);

    // Don't use call_VM as it will see a possible pending exception and forward it
    // and never return here preventing us from clearing _last_native_pc down below.
    // Also can't use call_VM_leaf either as it will check to see if rsi & rdi are
    // preserved and correspond to the bcp/locals pointers. So we do a runtime call
    // by hand.
    //
    __ vzeroupper();
    save_native_result(masm, ret_type, stack_slots);
    __ mov(c_rarg0, r15_thread);
    __ mov(r12, rsp); // remember sp
    __ subptr(rsp, frame::arg_reg_save_area_bytes); // windows
    __ andptr(rsp, -16); // align stack as required by ABI
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans)));
    __ mov(rsp, r12); // restore sp
    __ reinit_heapbase();
    // Restore any method result value
    restore_native_result(masm, ret_type, stack_slots);
    __ bind(Continue);
  }

  // change thread state
  __ movl(Address(r15_thread, JavaThread::thread_state_offset()), _thread_in_Java);
  __ bind(after_transition);

  Label reguard;
  Label reguard_done;
  __ cmpl(Address(r15_thread, JavaThread::stack_guard_state_offset()), StackOverflow::stack_guard_yellow_reserved_disabled);
  __ jcc(Assembler::equal, reguard);
  __ bind(reguard_done);

  // native result if any is live

  // Unlock
  Label slow_path_unlock;
  Label unlock_done;
  if (method->is_synchronized()) {

    Label fast_done;

    // Get locked oop from the handle we passed to jni
    __ movptr(obj_reg, Address(oop_handle_reg, 0));

    if (LockingMode == LM_LEGACY) {
      Label not_recur;
      // Simple recursive lock?
      __ cmpptr(Address(rsp, lock_slot_offset * VMRegImpl::stack_slot_size), NULL_WORD);
      __ jcc(Assembler::notEqual, not_recur);
      __ dec_held_monitor_count();
      __ jmpb(fast_done);
      __ bind(not_recur);
    }

    // Must save rax if it is live now because cmpxchg must use it
    if (ret_type != T_FLOAT && ret_type != T_DOUBLE && ret_type != T_VOID) {
      save_native_result(masm, ret_type, stack_slots);
    }

    if (LockingMode == LM_MONITOR) {
      __ jmp(slow_path_unlock);
    } else if (LockingMode == LM_LEGACY) {
      // get address of the stack lock
      __ lea(rax, Address(rsp, lock_slot_offset * VMRegImpl::stack_slot_size));
      //  get old displaced header
      __ movptr(old_hdr, Address(rax, 0));

      // Atomic swap old header if oop still contains the stack lock
      __ lock();
      __ cmpxchgptr(old_hdr, Address(obj_reg, oopDesc::mark_offset_in_bytes()));
      __ jcc(Assembler::notEqual, slow_path_unlock);
      __ dec_held_monitor_count();
    } else {
      assert(LockingMode == LM_LIGHTWEIGHT, "must be");
      __ movptr(swap_reg, Address(obj_reg, oopDesc::mark_offset_in_bytes()));
      __ andptr(swap_reg, ~(int32_t)markWord::lock_mask_in_place);
      __ lightweight_unlock(obj_reg, swap_reg, lock_reg, slow_path_unlock);
      __ dec_held_monitor_count();
    }

    // slow path re-enters here
    __ bind(unlock_done);
    if (ret_type != T_FLOAT && ret_type != T_DOUBLE && ret_type != T_VOID) {
      restore_native_result(masm, ret_type, stack_slots);
    }

    __ bind(fast_done);
  }
  {
    SkipIfEqual skip(masm, &DTraceMethodProbes, false, rscratch1);
    save_native_result(masm, ret_type, stack_slots);
    __ mov_metadata(c_rarg1, method());
    __ call_VM_leaf(
         CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_exit),
         r15_thread, c_rarg1);
    restore_native_result(masm, ret_type, stack_slots);
  }

  __ reset_last_Java_frame(false);

  // Unbox oop result, e.g. JNIHandles::resolve value.
  if (is_reference_type(ret_type)) {
    __ resolve_jobject(rax /* value */,
                       r15_thread /* thread */,
                       rcx /* tmp */);
  }

  if (CheckJNICalls) {
    // clear_pending_jni_exception_check
    __ movptr(Address(r15_thread, JavaThread::pending_jni_exception_check_fn_offset()), NULL_WORD);
  }

  // reset handle block
  __ movptr(rcx, Address(r15_thread, JavaThread::active_handles_offset()));
  __ movl(Address(rcx, JNIHandleBlock::top_offset()), NULL_WORD);

  // pop our frame

  __ leave();

  // Any exception pending?
  __ cmpptr(Address(r15_thread, in_bytes(Thread::pending_exception_offset())), NULL_WORD);
  __ jcc(Assembler::notEqual, exception_pending);

  // Return

  __ ret(0);

  // Unexpected paths are out of line and go here

  // forward the exception
  __ bind(exception_pending);

  // and forward the exception
  __ jump(RuntimeAddress(StubRoutines::forward_exception_entry()));

  // Slow path locking & unlocking
  if (method->is_synchronized()) {

    // BEGIN Slow path lock
    __ bind(slow_path_lock);

    // has last_Java_frame setup. No exceptions so do vanilla call not call_VM
    // args are (oop obj, BasicLock* lock, JavaThread* thread)

    // protect the args we've loaded
    save_args(masm, total_c_args, c_arg, out_regs);

    __ mov(c_rarg0, obj_reg);
    __ mov(c_rarg1, lock_reg);
    __ mov(c_rarg2, r15_thread);

    // Not a leaf but we have last_Java_frame setup as we want
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::complete_monitor_locking_C), 3);
    restore_args(masm, total_c_args, c_arg, out_regs);

#ifdef ASSERT
    { Label L;
    __ cmpptr(Address(r15_thread, in_bytes(Thread::pending_exception_offset())), NULL_WORD);
    __ jcc(Assembler::equal, L);
    __ stop("no pending exception allowed on exit from monitorenter");
    __ bind(L);
    }
#endif
    __ jmp(lock_done);

    // END Slow path lock

    // BEGIN Slow path unlock
    __ bind(slow_path_unlock);

    // If we haven't already saved the native result we must save it now as xmm registers
    // are still exposed.
    __ vzeroupper();
    if (ret_type == T_FLOAT || ret_type == T_DOUBLE ) {
      save_native_result(masm, ret_type, stack_slots);
    }

    __ lea(c_rarg1, Address(rsp, lock_slot_offset * VMRegImpl::stack_slot_size));

    __ mov(c_rarg0, obj_reg);
    __ mov(c_rarg2, r15_thread);
    __ mov(r12, rsp); // remember sp
    __ subptr(rsp, frame::arg_reg_save_area_bytes); // windows
    __ andptr(rsp, -16); // align stack as required by ABI

    // Save pending exception around call to VM (which contains an EXCEPTION_MARK)
    // NOTE that obj_reg == rbx currently
    __ movptr(rbx, Address(r15_thread, in_bytes(Thread::pending_exception_offset())));
    __ movptr(Address(r15_thread, in_bytes(Thread::pending_exception_offset())), NULL_WORD);

    // args are (oop obj, BasicLock* lock, JavaThread* thread)
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::complete_monitor_unlocking_C)));
    __ mov(rsp, r12); // restore sp
    __ reinit_heapbase();
#ifdef ASSERT
    {
      Label L;
      __ cmpptr(Address(r15_thread, in_bytes(Thread::pending_exception_offset())), NULL_WORD);
      __ jcc(Assembler::equal, L);
      __ stop("no pending exception allowed on exit complete_monitor_unlocking_C");
      __ bind(L);
    }
#endif /* ASSERT */

    __ movptr(Address(r15_thread, in_bytes(Thread::pending_exception_offset())), rbx);

    if (ret_type == T_FLOAT || ret_type == T_DOUBLE ) {
      restore_native_result(masm, ret_type, stack_slots);
    }
    __ jmp(unlock_done);

    // END Slow path unlock

  } // synchronized

  // SLOW PATH Reguard the stack if needed

  __ bind(reguard);
  __ vzeroupper();
  save_native_result(masm, ret_type, stack_slots);
  __ mov(r12, rsp); // remember sp
  __ subptr(rsp, frame::arg_reg_save_area_bytes); // windows
  __ andptr(rsp, -16); // align stack as required by ABI
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages)));
  __ mov(rsp, r12); // restore sp
  __ reinit_heapbase();
  restore_native_result(masm, ret_type, stack_slots);
  // and continue
  __ jmp(reguard_done);



  __ flush();

  nmethod *nm = nmethod::new_native_nmethod(method,
                                            compile_id,
                                            masm->code(),
                                            vep_offset,
                                            frame_complete,
                                            stack_slots / VMRegImpl::slots_per_word,
                                            (is_static ? in_ByteSize(klass_offset) : in_ByteSize(receiver_offset)),
                                            in_ByteSize(lock_slot_offset*VMRegImpl::stack_slot_size),
                                            oop_maps);

  return nm;
}

// this function returns the adjust size (in number of words) to a c2i adapter
// activation for use during deoptimization
int Deoptimization::last_frame_adjust(int callee_parameters, int callee_locals ) {
  return (callee_locals - callee_parameters) * Interpreter::stackElementWords;
}


uint SharedRuntime::out_preserve_stack_slots() {
  return 0;
}


// Number of stack slots between incoming argument block and the start of
// a new frame.  The PROLOG must add this many slots to the stack.  The
// EPILOG must remove this many slots.  amd64 needs two slots for
// return address.
uint SharedRuntime::in_preserve_stack_slots() {
  return 4 + 2 * VerifyStackAtCalls;
}

//------------------------------generate_deopt_blob----------------------------
void SharedRuntime::generate_deopt_blob() {
  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  int pad = 0;
  if (UseAVX > 2) {
    pad += 1024;
  }
#if INCLUDE_JVMCI
  if (EnableJVMCI) {
    pad += 512; // Increase the buffer size when compiling for JVMCI
  }
#endif
  CodeBuffer buffer("deopt_blob", 2560+pad, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);
  int frame_size_in_words;
  OopMap* map = nullptr;
  OopMapSet *oop_maps = new OopMapSet();

  // -------------
  // This code enters when returning to a de-optimized nmethod.  A return
  // address has been pushed on the stack, and return values are in
  // registers.
  // If we are doing a normal deopt then we were called from the patched
  // nmethod from the point we returned to the nmethod. So the return
  // address on the stack is wrong by NativeCall::instruction_size
  // We will adjust the value so it looks like we have the original return
  // address on the stack (like when we eagerly deoptimized).
  // In the case of an exception pending when deoptimizing, we enter
  // with a return address on the stack that points after the call we patched
  // into the exception handler. We have the following register state from,
  // e.g., the forward exception stub (see stubGenerator_x86_64.cpp).
  //    rax: exception oop
  //    rbx: exception handler
  //    rdx: throwing pc
  // So in this case we simply jam rdx into the useless return address and
  // the stack looks just like we want.
  //
  // At this point we need to de-opt.  We save the argument return
  // registers.  We call the first C routine, fetch_unroll_info().  This
  // routine captures the return values and returns a structure which
  // describes the current frame size and the sizes of all replacement frames.
  // The current frame is compiled code and may contain many inlined
  // functions, each with their own JVM state.  We pop the current frame, then
  // push all the new frames.  Then we call the C routine unpack_frames() to
  // populate these frames.  Finally unpack_frames() returns us the new target
  // address.  Notice that callee-save registers are BLOWN here; they have
  // already been captured in the vframeArray at the time the return PC was
  // patched.
  address start = __ pc();
  Label cont;

  // Prolog for non exception case!

  // Save everything in sight.
  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words, /*save_wide_vectors*/ true);

  // Normal deoptimization.  Save exec mode for unpack_frames.
  __ movl(r14, Deoptimization::Unpack_deopt); // callee-saved
  __ jmp(cont);

  int reexecute_offset = __ pc() - start;
#if INCLUDE_JVMCI && !defined(COMPILER1)
  if (EnableJVMCI && UseJVMCICompiler) {
    // JVMCI does not use this kind of deoptimization
    __ should_not_reach_here();
  }
#endif

  // Reexecute case
  // return address is the pc describes what bci to do re-execute at

  // No need to update map as each call to save_live_registers will produce identical oopmap
  (void) RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words, /*save_wide_vectors*/ true);

  __ movl(r14, Deoptimization::Unpack_reexecute); // callee-saved
  __ jmp(cont);

#if INCLUDE_JVMCI
  Label after_fetch_unroll_info_call;
  int implicit_exception_uncommon_trap_offset = 0;
  int uncommon_trap_offset = 0;

  if (EnableJVMCI) {
    implicit_exception_uncommon_trap_offset = __ pc() - start;

    __ pushptr(Address(r15_thread, in_bytes(JavaThread::jvmci_implicit_exception_pc_offset())));
    __ movptr(Address(r15_thread, in_bytes(JavaThread::jvmci_implicit_exception_pc_offset())), NULL_WORD);

    uncommon_trap_offset = __ pc() - start;

    // Save everything in sight.
    RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words, /*save_wide_vectors*/ true);
    // fetch_unroll_info needs to call last_java_frame()
    __ set_last_Java_frame(noreg, noreg, nullptr, rscratch1);

    __ movl(c_rarg1, Address(r15_thread, in_bytes(JavaThread::pending_deoptimization_offset())));
    __ movl(Address(r15_thread, in_bytes(JavaThread::pending_deoptimization_offset())), -1);

    __ movl(r14, Deoptimization::Unpack_reexecute);
    __ mov(c_rarg0, r15_thread);
    __ movl(c_rarg2, r14); // exec mode
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::uncommon_trap)));
    oop_maps->add_gc_map( __ pc()-start, map->deep_copy());

    __ reset_last_Java_frame(false);

    __ jmp(after_fetch_unroll_info_call);
  } // EnableJVMCI
#endif // INCLUDE_JVMCI

  int exception_offset = __ pc() - start;

  // Prolog for exception case

  // all registers are dead at this entry point, except for rax, and
  // rdx which contain the exception oop and exception pc
  // respectively.  Set them in TLS and fall thru to the
  // unpack_with_exception_in_tls entry point.

  __ movptr(Address(r15_thread, JavaThread::exception_pc_offset()), rdx);
  __ movptr(Address(r15_thread, JavaThread::exception_oop_offset()), rax);

  int exception_in_tls_offset = __ pc() - start;

  // new implementation because exception oop is now passed in JavaThread

  // Prolog for exception case
  // All registers must be preserved because they might be used by LinearScan
  // Exceptiop oop and throwing PC are passed in JavaThread
  // tos: stack at point of call to method that threw the exception (i.e. only
  // args are on the stack, no return address)

  // make room on stack for the return address
  // It will be patched later with the throwing pc. The correct value is not
  // available now because loading it from memory would destroy registers.
  __ push(0);

  // Save everything in sight.
  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words, /*save_wide_vectors*/ true);

  // Now it is safe to overwrite any register

  // Deopt during an exception.  Save exec mode for unpack_frames.
  __ movl(r14, Deoptimization::Unpack_exception); // callee-saved

  // load throwing pc from JavaThread and patch it as the return address
  // of the current frame. Then clear the field in JavaThread

  __ movptr(rdx, Address(r15_thread, JavaThread::exception_pc_offset()));
  __ movptr(Address(rbp, wordSize), rdx);
  __ movptr(Address(r15_thread, JavaThread::exception_pc_offset()), NULL_WORD);

#ifdef ASSERT
  // verify that there is really an exception oop in JavaThread
  __ movptr(rax, Address(r15_thread, JavaThread::exception_oop_offset()));
  __ verify_oop(rax);

  // verify that there is no pending exception
  Label no_pending_exception;
  __ movptr(rax, Address(r15_thread, Thread::pending_exception_offset()));
  __ testptr(rax, rax);
  __ jcc(Assembler::zero, no_pending_exception);
  __ stop("must not have pending exception here");
  __ bind(no_pending_exception);
#endif

  __ bind(cont);

  // Call C code.  Need thread and this frame, but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.
  //
  // UnrollBlock* fetch_unroll_info(JavaThread* thread)

  // fetch_unroll_info needs to call last_java_frame().

  __ set_last_Java_frame(noreg, noreg, nullptr, rscratch1);
#ifdef ASSERT
  { Label L;
    __ cmpptr(Address(r15_thread, JavaThread::last_Java_fp_offset()), NULL_WORD);
    __ jcc(Assembler::equal, L);
    __ stop("SharedRuntime::generate_deopt_blob: last_Java_fp not cleared");
    __ bind(L);
  }
#endif // ASSERT
  __ mov(c_rarg0, r15_thread);
  __ movl(c_rarg1, r14); // exec_mode
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::fetch_unroll_info)));

  // Need to have an oopmap that tells fetch_unroll_info where to
  // find any register it might need.
  oop_maps->add_gc_map(__ pc() - start, map);

  __ reset_last_Java_frame(false);

#if INCLUDE_JVMCI
  if (EnableJVMCI) {
    __ bind(after_fetch_unroll_info_call);
  }
#endif

  // Load UnrollBlock* into rdi
  __ mov(rdi, rax);

  __ movl(r14, Address(rdi, Deoptimization::UnrollBlock::unpack_kind_offset()));
   Label noException;
  __ cmpl(r14, Deoptimization::Unpack_exception);   // Was exception pending?
  __ jcc(Assembler::notEqual, noException);
  __ movptr(rax, Address(r15_thread, JavaThread::exception_oop_offset()));
  // QQQ this is useless it was null above
  __ movptr(rdx, Address(r15_thread, JavaThread::exception_pc_offset()));
  __ movptr(Address(r15_thread, JavaThread::exception_oop_offset()), NULL_WORD);
  __ movptr(Address(r15_thread, JavaThread::exception_pc_offset()), NULL_WORD);

  __ verify_oop(rax);

  // Overwrite the result registers with the exception results.
  __ movptr(Address(rsp, RegisterSaver::rax_offset_in_bytes()), rax);
  // I think this is useless
  __ movptr(Address(rsp, RegisterSaver::rdx_offset_in_bytes()), rdx);

  __ bind(noException);

  // Only register save data is on the stack.
  // Now restore the result registers.  Everything else is either dead
  // or captured in the vframeArray.
  RegisterSaver::restore_result_registers(masm);

  // All of the register save area has been popped of the stack. Only the
  // return address remains.

  // Pop all the frames we must move/replace.
  //
  // Frame picture (youngest to oldest)
  // 1: self-frame (no frame link)
  // 2: deopting frame  (no frame link)
  // 3: caller of deopting frame (could be compiled/interpreted).
  //
  // Note: by leaving the return address of self-frame on the stack
  // and using the size of frame 2 to adjust the stack
  // when we are done the return to frame 3 will still be on the stack.

  // Pop deoptimized frame
  __ movl(rcx, Address(rdi, Deoptimization::UnrollBlock::size_of_deoptimized_frame_offset()));
  __ addptr(rsp, rcx);

  // rsp should be pointing at the return address to the caller (3)

  // Pick up the initial fp we should save
  // restore rbp before stack bang because if stack overflow is thrown it needs to be pushed (and preserved)
  __ movptr(rbp, Address(rdi, Deoptimization::UnrollBlock::initial_info_offset()));

#ifdef ASSERT
  // Compilers generate code that bang the stack by as much as the
  // interpreter would need. So this stack banging should never
  // trigger a fault. Verify that it does not on non product builds.
  __ movl(rbx, Address(rdi, Deoptimization::UnrollBlock::total_frame_sizes_offset()));
  __ bang_stack_size(rbx, rcx);
#endif

  // Load address of array of frame pcs into rcx
  __ movptr(rcx, Address(rdi, Deoptimization::UnrollBlock::frame_pcs_offset()));

  // Trash the old pc
  __ addptr(rsp, wordSize);

  // Load address of array of frame sizes into rsi
  __ movptr(rsi, Address(rdi, Deoptimization::UnrollBlock::frame_sizes_offset()));

  // Load counter into rdx
  __ movl(rdx, Address(rdi, Deoptimization::UnrollBlock::number_of_frames_offset()));

  // Now adjust the caller's stack to make up for the extra locals
  // but record the original sp so that we can save it in the skeletal interpreter
  // frame and the stack walking of interpreter_sender will get the unextended sp
  // value and not the "real" sp value.

  const Register sender_sp = r8;

  __ mov(sender_sp, rsp);
  __ movl(rbx, Address(rdi,
                       Deoptimization::UnrollBlock::
                       caller_adjustment_offset()));
  __ subptr(rsp, rbx);

  // Push interpreter frames in a loop
  Label loop;
  __ bind(loop);
  __ movptr(rbx, Address(rsi, 0));      // Load frame size
  __ subptr(rbx, 2*wordSize);           // We'll push pc and ebp by hand
  __ pushptr(Address(rcx, 0));          // Save return address
  __ enter();                           // Save old & set new ebp
  __ subptr(rsp, rbx);                  // Prolog
  // This value is corrected by layout_activation_impl
  __ movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD);
  __ movptr(Address(rbp, frame::interpreter_frame_sender_sp_offset * wordSize), sender_sp); // Make it walkable
  __ mov(sender_sp, rsp);               // Pass sender_sp to next frame
  __ addptr(rsi, wordSize);             // Bump array pointer (sizes)
  __ addptr(rcx, wordSize);             // Bump array pointer (pcs)
  __ decrementl(rdx);                   // Decrement counter
  __ jcc(Assembler::notZero, loop);
  __ pushptr(Address(rcx, 0));          // Save final return address

  // Re-push self-frame
  __ enter();                           // Save old & set new ebp

  // Allocate a full sized register save area.
  // Return address and rbp are in place, so we allocate two less words.
  __ subptr(rsp, (frame_size_in_words - 2) * wordSize);

  // Restore frame locals after moving the frame
  __ movdbl(Address(rsp, RegisterSaver::xmm0_offset_in_bytes()), xmm0);
  __ movptr(Address(rsp, RegisterSaver::rax_offset_in_bytes()), rax);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // restore return values to their stack-slots with the new SP.
  //
  // void Deoptimization::unpack_frames(JavaThread* thread, int exec_mode)

  // Use rbp because the frames look interpreted now
  // Save "the_pc" since it cannot easily be retrieved using the last_java_SP after we aligned SP.
  // Don't need the precise return PC here, just precise enough to point into this code blob.
  address the_pc = __ pc();
  __ set_last_Java_frame(noreg, rbp, the_pc, rscratch1);

  __ andptr(rsp, -(StackAlignmentInBytes));  // Fix stack alignment as required by ABI
  __ mov(c_rarg0, r15_thread);
  __ movl(c_rarg1, r14); // second arg: exec_mode
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames)));
  // Revert SP alignment after call since we're going to do some SP relative addressing below
  __ movptr(rsp, Address(r15_thread, JavaThread::last_Java_sp_offset()));

  // Set an oopmap for the call site
  // Use the same PC we used for the last java frame
  oop_maps->add_gc_map(the_pc - start,
                       new OopMap( frame_size_in_words, 0 ));

  // Clear fp AND pc
  __ reset_last_Java_frame(true);

  // Collect return values
  __ movdbl(xmm0, Address(rsp, RegisterSaver::xmm0_offset_in_bytes()));
  __ movptr(rax, Address(rsp, RegisterSaver::rax_offset_in_bytes()));
  // I think this is useless (throwing pc?)
  __ movptr(rdx, Address(rsp, RegisterSaver::rdx_offset_in_bytes()));

  // Pop self-frame.
  __ leave();                           // Epilog

  // Jump to interpreter
  __ ret(0);

  // Make sure all code is generated
  masm->flush();

  _deopt_blob = DeoptimizationBlob::create(&buffer, oop_maps, 0, exception_offset, reexecute_offset, frame_size_in_words);
  _deopt_blob->set_unpack_with_exception_in_tls_offset(exception_in_tls_offset);
#if INCLUDE_JVMCI
  if (EnableJVMCI) {
    _deopt_blob->set_uncommon_trap_offset(uncommon_trap_offset);
    _deopt_blob->set_implicit_exception_uncommon_trap_offset(implicit_exception_uncommon_trap_offset);
  }
#endif
}

#ifdef COMPILER2
//------------------------------generate_uncommon_trap_blob--------------------
void SharedRuntime::generate_uncommon_trap_blob() {
  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  CodeBuffer buffer("uncommon_trap_blob", 2048, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);

  assert(SimpleRuntimeFrame::framesize % 4 == 0, "sp not 16-byte aligned");

  address start = __ pc();

  if (UseRTMLocking) {
    // Abort RTM transaction before possible nmethod deoptimization.
    __ xabort(0);
  }

  // Push self-frame.  We get here with a return address on the
  // stack, so rsp is 8-byte aligned until we allocate our frame.
  __ subptr(rsp, SimpleRuntimeFrame::return_off << LogBytesPerInt); // Epilog!

  // No callee saved registers. rbp is assumed implicitly saved
  __ movptr(Address(rsp, SimpleRuntimeFrame::rbp_off << LogBytesPerInt), rbp);

  // compiler left unloaded_class_index in j_rarg0 move to where the
  // runtime expects it.
  __ movl(c_rarg1, j_rarg0);

  __ set_last_Java_frame(noreg, noreg, nullptr, rscratch1);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // capture callee-saved registers as well as return values.
  // Thread is in rdi already.
  //
  // UnrollBlock* uncommon_trap(JavaThread* thread, jint unloaded_class_index);

  __ mov(c_rarg0, r15_thread);
  __ movl(c_rarg2, Deoptimization::Unpack_uncommon_trap);
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::uncommon_trap)));

  // Set an oopmap for the call site
  OopMapSet* oop_maps = new OopMapSet();
  OopMap* map = new OopMap(SimpleRuntimeFrame::framesize, 0);

  // location of rbp is known implicitly by the frame sender code

  oop_maps->add_gc_map(__ pc() - start, map);

  __ reset_last_Java_frame(false);

  // Load UnrollBlock* into rdi
  __ mov(rdi, rax);

#ifdef ASSERT
  { Label L;
    __ cmpptr(Address(rdi, Deoptimization::UnrollBlock::unpack_kind_offset()),
              Deoptimization::Unpack_uncommon_trap);
    __ jcc(Assembler::equal, L);
    __ stop("SharedRuntime::generate_uncommon_trap_blob: expected Unpack_uncommon_trap");
    __ bind(L);
  }
#endif

  // Pop all the frames we must move/replace.
  //
  // Frame picture (youngest to oldest)
  // 1: self-frame (no frame link)
  // 2: deopting frame  (no frame link)
  // 3: caller of deopting frame (could be compiled/interpreted).

  // Pop self-frame.  We have no frame, and must rely only on rax and rsp.
  __ addptr(rsp, (SimpleRuntimeFrame::framesize - 2) << LogBytesPerInt); // Epilog!

  // Pop deoptimized frame (int)
  __ movl(rcx, Address(rdi,
                       Deoptimization::UnrollBlock::
                       size_of_deoptimized_frame_offset()));
  __ addptr(rsp, rcx);

  // rsp should be pointing at the return address to the caller (3)

  // Pick up the initial fp we should save
  // restore rbp before stack bang because if stack overflow is thrown it needs to be pushed (and preserved)
  __ movptr(rbp, Address(rdi, Deoptimization::UnrollBlock::initial_info_offset()));

#ifdef ASSERT
  // Compilers generate code that bang the stack by as much as the
  // interpreter would need. So this stack banging should never
  // trigger a fault. Verify that it does not on non product builds.
  __ movl(rbx, Address(rdi ,Deoptimization::UnrollBlock::total_frame_sizes_offset()));
  __ bang_stack_size(rbx, rcx);
#endif

  // Load address of array of frame pcs into rcx (address*)
  __ movptr(rcx, Address(rdi, Deoptimization::UnrollBlock::frame_pcs_offset()));

  // Trash the return pc
  __ addptr(rsp, wordSize);

  // Load address of array of frame sizes into rsi (intptr_t*)
  __ movptr(rsi, Address(rdi, Deoptimization::UnrollBlock:: frame_sizes_offset()));

  // Counter
  __ movl(rdx, Address(rdi, Deoptimization::UnrollBlock:: number_of_frames_offset())); // (int)

  // Now adjust the caller's stack to make up for the extra locals but
  // record the original sp so that we can save it in the skeletal
  // interpreter frame and the stack walking of interpreter_sender
  // will get the unextended sp value and not the "real" sp value.

  const Register sender_sp = r8;

  __ mov(sender_sp, rsp);
  __ movl(rbx, Address(rdi, Deoptimization::UnrollBlock:: caller_adjustment_offset())); // (int)
  __ subptr(rsp, rbx);

  // Push interpreter frames in a loop
  Label loop;
  __ bind(loop);
  __ movptr(rbx, Address(rsi, 0)); // Load frame size
  __ subptr(rbx, 2 * wordSize);    // We'll push pc and rbp by hand
  __ pushptr(Address(rcx, 0));     // Save return address
  __ enter();                      // Save old & set new rbp
  __ subptr(rsp, rbx);             // Prolog
  __ movptr(Address(rbp, frame::interpreter_frame_sender_sp_offset * wordSize),
            sender_sp);            // Make it walkable
  // This value is corrected by layout_activation_impl
  __ movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD);
  __ mov(sender_sp, rsp);          // Pass sender_sp to next frame
  __ addptr(rsi, wordSize);        // Bump array pointer (sizes)
  __ addptr(rcx, wordSize);        // Bump array pointer (pcs)
  __ decrementl(rdx);              // Decrement counter
  __ jcc(Assembler::notZero, loop);
  __ pushptr(Address(rcx, 0));     // Save final return address

  // Re-push self-frame
  __ enter();                 // Save old & set new rbp
  __ subptr(rsp, (SimpleRuntimeFrame::framesize - 4) << LogBytesPerInt);
                              // Prolog

  // Use rbp because the frames look interpreted now
  // Save "the_pc" since it cannot easily be retrieved using the last_java_SP after we aligned SP.
  // Don't need the precise return PC here, just precise enough to point into this code blob.
  address the_pc = __ pc();
  __ set_last_Java_frame(noreg, rbp, the_pc, rscratch1);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // restore return values to their stack-slots with the new SP.
  // Thread is in rdi already.
  //
  // BasicType unpack_frames(JavaThread* thread, int exec_mode);

  __ andptr(rsp, -(StackAlignmentInBytes)); // Align SP as required by ABI
  __ mov(c_rarg0, r15_thread);
  __ movl(c_rarg1, Deoptimization::Unpack_uncommon_trap);
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames)));

  // Set an oopmap for the call site
  // Use the same PC we used for the last java frame
  oop_maps->add_gc_map(the_pc - start, new OopMap(SimpleRuntimeFrame::framesize, 0));

  // Clear fp AND pc
  __ reset_last_Java_frame(true);

  // Pop self-frame.
  __ leave();                 // Epilog

  // Jump to interpreter
  __ ret(0);

  // Make sure all code is generated
  masm->flush();

  _uncommon_trap_blob =  UncommonTrapBlob::create(&buffer, oop_maps,
                                                 SimpleRuntimeFrame::framesize >> 1);
}
#endif // COMPILER2

//------------------------------generate_handler_blob------
//
// Generate a special Compile2Runtime blob that saves all registers,
// and setup oopmap.
//
SafepointBlob* SharedRuntime::generate_handler_blob(address call_ptr, int poll_type) {
  assert(StubRoutines::forward_exception_entry() != nullptr,
         "must be generated before");

  ResourceMark rm;
  OopMapSet *oop_maps = new OopMapSet();
  OopMap* map;

  // Allocate space for the code.  Setup code generation tools.
  CodeBuffer buffer("handler_blob", 2048, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);

  address start   = __ pc();
  address call_pc = nullptr;
  int frame_size_in_words;
  bool cause_return = (poll_type == POLL_AT_RETURN);
  bool save_wide_vectors = (poll_type == POLL_AT_VECTOR_LOOP);

  if (UseRTMLocking) {
    // Abort RTM transaction before calling runtime
    // because critical section will be large and will be
    // aborted anyway. Also nmethod could be deoptimized.
    __ xabort(0);
  }

  // Make room for return address (or push it again)
  if (!cause_return) {
    __ push(rbx);
  }

  // Save registers, fpu state, and flags
  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words, save_wide_vectors);

  // The following is basically a call_VM.  However, we need the precise
  // address of the call in order to generate an oopmap. Hence, we do all the
  // work ourselves.

  __ set_last_Java_frame(noreg, noreg, nullptr, rscratch1);  // JavaFrameAnchor::capture_last_Java_pc() will get the pc from the return address, which we store next:

  // The return address must always be correct so that frame constructor never
  // sees an invalid pc.

  if (!cause_return) {
    // Get the return pc saved by the signal handler and stash it in its appropriate place on the stack.
    // Additionally, rbx is a callee saved register and we can look at it later to determine
    // if someone changed the return address for us!
    __ movptr(rbx, Address(r15_thread, JavaThread::saved_exception_pc_offset()));
    __ movptr(Address(rbp, wordSize), rbx);
  }

  // Do the call
  __ mov(c_rarg0, r15_thread);
  __ call(RuntimeAddress(call_ptr));

  // Set an oopmap for the call site.  This oopmap will map all
  // oop-registers and debug-info registers as callee-saved.  This
  // will allow deoptimization at this safepoint to find all possible
  // debug-info recordings, as well as let GC find all oops.

  oop_maps->add_gc_map( __ pc() - start, map);

  Label noException;

  __ reset_last_Java_frame(false);

  __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()), NULL_WORD);
  __ jcc(Assembler::equal, noException);

  // Exception pending

  RegisterSaver::restore_live_registers(masm, save_wide_vectors);

  __ jump(RuntimeAddress(StubRoutines::forward_exception_entry()));

  // No exception case
  __ bind(noException);

  Label no_adjust;
#ifdef ASSERT
  Label bail;
#endif
  if (!cause_return) {
    Label no_prefix, not_special;

    // If our stashed return pc was modified by the runtime we avoid touching it
    __ cmpptr(rbx, Address(rbp, wordSize));
    __ jccb(Assembler::notEqual, no_adjust);

    // Skip over the poll instruction.
    // See NativeInstruction::is_safepoint_poll()
    // Possible encodings:
    //      85 00       test   %eax,(%rax)
    //      85 01       test   %eax,(%rcx)
    //      85 02       test   %eax,(%rdx)
    //      85 03       test   %eax,(%rbx)
    //      85 06       test   %eax,(%rsi)
    //      85 07       test   %eax,(%rdi)
    //
    //   41 85 00       test   %eax,(%r8)
    //   41 85 01       test   %eax,(%r9)
    //   41 85 02       test   %eax,(%r10)
    //   41 85 03       test   %eax,(%r11)
    //   41 85 06       test   %eax,(%r14)
    //   41 85 07       test   %eax,(%r15)
    //
    //      85 04 24    test   %eax,(%rsp)
    //   41 85 04 24    test   %eax,(%r12)
    //      85 45 00    test   %eax,0x0(%rbp)
    //   41 85 45 00    test   %eax,0x0(%r13)

    __ cmpb(Address(rbx, 0), NativeTstRegMem::instruction_rex_b_prefix);
    __ jcc(Assembler::notEqual, no_prefix);
    __ addptr(rbx, 1);
    __ bind(no_prefix);
#ifdef ASSERT
    __ movptr(rax, rbx); // remember where 0x85 should be, for verification below
#endif
    // r12/r13/rsp/rbp base encoding takes 3 bytes with the following register values:
    // r12/rsp 0x04
    // r13/rbp 0x05
    __ movzbq(rcx, Address(rbx, 1));
    __ andptr(rcx, 0x07); // looking for 0x04 .. 0x05
    __ subptr(rcx, 4);    // looking for 0x00 .. 0x01
    __ cmpptr(rcx, 1);
    __ jcc(Assembler::above, not_special);
    __ addptr(rbx, 1);
    __ bind(not_special);
#ifdef ASSERT
    // Verify the correct encoding of the poll we're about to skip.
    __ cmpb(Address(rax, 0), NativeTstRegMem::instruction_code_memXregl);
    __ jcc(Assembler::notEqual, bail);
    // Mask out the modrm bits
    __ testb(Address(rax, 1), NativeTstRegMem::modrm_mask);
    // rax encodes to 0, so if the bits are nonzero it's incorrect
    __ jcc(Assembler::notZero, bail);
#endif
    // Adjust return pc forward to step over the safepoint poll instruction
    __ addptr(rbx, 2);
    __ movptr(Address(rbp, wordSize), rbx);
  }

  __ bind(no_adjust);
  // Normal exit, restore registers and exit.
  RegisterSaver::restore_live_registers(masm, save_wide_vectors);
  __ ret(0);

#ifdef ASSERT
  __ bind(bail);
  __ stop("Attempting to adjust pc to skip safepoint poll but the return point is not what we expected");
#endif

  // Make sure all code is generated
  masm->flush();

  // Fill-out other meta info
  return SafepointBlob::create(&buffer, oop_maps, frame_size_in_words);
}

//
// generate_resolve_blob - call resolution (static/virtual/opt-virtual/ic-miss
//
// Generate a stub that calls into vm to find out the proper destination
// of a java call. All the argument registers are live at this point
// but since this is generic code we don't know what they are and the caller
// must do any gc of the args.
//
RuntimeStub* SharedRuntime::generate_resolve_blob(address destination, const char* name) {
  assert (StubRoutines::forward_exception_entry() != nullptr, "must be generated before");

  // allocate space for the code
  ResourceMark rm;

  CodeBuffer buffer(name, 1200, 512);
  MacroAssembler* masm = new MacroAssembler(&buffer);

  int frame_size_in_words;

  OopMapSet *oop_maps = new OopMapSet();
  OopMap* map = nullptr;

  int start = __ offset();

  // No need to save vector registers since they are caller-saved anyway.
  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words, /*save_wide_vectors*/ false);

  int frame_complete = __ offset();

  __ set_last_Java_frame(noreg, noreg, nullptr, rscratch1);

  __ mov(c_rarg0, r15_thread);

  __ call(RuntimeAddress(destination));


  // Set an oopmap for the call site.
  // We need this not only for callee-saved registers, but also for volatile
  // registers that the compiler might be keeping live across a safepoint.

  oop_maps->add_gc_map( __ offset() - start, map);

  // rax contains the address we are going to jump to assuming no exception got installed

  // clear last_Java_sp
  __ reset_last_Java_frame(false);
  // check for pending exceptions
  Label pending;
  __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()), NULL_WORD);
  __ jcc(Assembler::notEqual, pending);

  // get the returned Method*
  __ get_vm_result_2(rbx, r15_thread);
  __ movptr(Address(rsp, RegisterSaver::rbx_offset_in_bytes()), rbx);

  __ movptr(Address(rsp, RegisterSaver::rax_offset_in_bytes()), rax);

  RegisterSaver::restore_live_registers(masm);

  // We are back to the original state on entry and ready to go.

  __ jmp(rax);

  // Pending exception after the safepoint

  __ bind(pending);

  RegisterSaver::restore_live_registers(masm);

  // exception pending => remove activation and forward to exception handler

  __ movptr(Address(r15_thread, JavaThread::vm_result_offset()), NULL_WORD);

  __ movptr(rax, Address(r15_thread, Thread::pending_exception_offset()));
  __ jump(RuntimeAddress(StubRoutines::forward_exception_entry()));

  // -------------
  // make sure all code is generated
  masm->flush();

  // return the  blob
  // frame_size_words or bytes??
  return RuntimeStub::new_runtime_stub(name, &buffer, frame_complete, frame_size_in_words, oop_maps, true);
}

//------------------------------Montgomery multiplication------------------------
//

#ifndef _WINDOWS

// Subtract 0:b from carry:a.  Return carry.
static julong
sub(julong a[], julong b[], julong carry, long len) {
  long long i = 0, cnt = len;
  julong tmp;
  asm volatile("clc; "
               "0: ; "
               "mov (%[b], %[i], 8), %[tmp]; "
               "sbb %[tmp], (%[a], %[i], 8); "
               "inc %[i]; dec %[cnt]; "
               "jne 0b; "
               "mov %[carry], %[tmp]; sbb $0, %[tmp]; "
               : [i]"+r"(i), [cnt]"+r"(cnt), [tmp]"=&r"(tmp)
               : [a]"r"(a), [b]"r"(b), [carry]"r"(carry)
               : "memory");
  return tmp;
}

// Multiply (unsigned) Long A by Long B, accumulating the double-
// length result into the accumulator formed of T0, T1, and T2.
#define MACC(A, B, T0, T1, T2)                                  \
do {                                                            \
  unsigned long hi, lo;                                         \
  __asm__ ("mul %5; add %%rax, %2; adc %%rdx, %3; adc $0, %4"   \
           : "=&d"(hi), "=a"(lo), "+r"(T0), "+r"(T1), "+g"(T2)  \
           : "r"(A), "a"(B) : "cc");                            \
 } while(0)

// As above, but add twice the double-length result into the
// accumulator.
#define MACC2(A, B, T0, T1, T2)                                 \
do {                                                            \
  unsigned long hi, lo;                                         \
  __asm__ ("mul %5; add %%rax, %2; adc %%rdx, %3; adc $0, %4; " \
           "add %%rax, %2; adc %%rdx, %3; adc $0, %4"           \
           : "=&d"(hi), "=a"(lo), "+r"(T0), "+r"(T1), "+g"(T2)  \
           : "r"(A), "a"(B) : "cc");                            \
 } while(0)

#else //_WINDOWS

static julong
sub(julong a[], julong b[], julong carry, long len) {
  long i;
  julong tmp;
  unsigned char c = 1;
  for (i = 0; i < len; i++) {
    c = _addcarry_u64(c, a[i], ~b[i], &tmp);
    a[i] = tmp;
  }
  c = _addcarry_u64(c, carry, ~0, &tmp);
  return tmp;
}

// Multiply (unsigned) Long A by Long B, accumulating the double-
// length result into the accumulator formed of T0, T1, and T2.
#define MACC(A, B, T0, T1, T2)                          \
do {                                                    \
  julong hi, lo;                            \
  lo = _umul128(A, B, &hi);                             \
  unsigned char c = _addcarry_u64(0, lo, T0, &T0);      \
  c = _addcarry_u64(c, hi, T1, &T1);                    \
  _addcarry_u64(c, T2, 0, &T2);                         \
 } while(0)

// As above, but add twice the double-length result into the
// accumulator.
#define MACC2(A, B, T0, T1, T2)                         \
do {                                                    \
  julong hi, lo;                            \
  lo = _umul128(A, B, &hi);                             \
  unsigned char c = _addcarry_u64(0, lo, T0, &T0);      \
  c = _addcarry_u64(c, hi, T1, &T1);                    \
  _addcarry_u64(c, T2, 0, &T2);                         \
  c = _addcarry_u64(0, lo, T0, &T0);                    \
  c = _addcarry_u64(c, hi, T1, &T1);                    \
  _addcarry_u64(c, T2, 0, &T2);                         \
 } while(0)

#endif //_WINDOWS

// Fast Montgomery multiplication.  The derivation of the algorithm is
// in  A Cryptographic Library for the Motorola DSP56000,
// Dusse and Kaliski, Proc. EUROCRYPT 90, pp. 230-237.

static void NOINLINE
montgomery_multiply(julong a[], julong b[], julong n[],
                    julong m[], julong inv, int len) {
  julong t0 = 0, t1 = 0, t2 = 0; // Triple-precision accumulator
  int i;

  assert(inv * n[0] == ULLONG_MAX, "broken inverse in Montgomery multiply");

  for (i = 0; i < len; i++) {
    int j;
    for (j = 0; j < i; j++) {
      MACC(a[j], b[i-j], t0, t1, t2);
      MACC(m[j], n[i-j], t0, t1, t2);
    }
    MACC(a[i], b[0], t0, t1, t2);
    m[i] = t0 * inv;
    MACC(m[i], n[0], t0, t1, t2);

    assert(t0 == 0, "broken Montgomery multiply");

    t0 = t1; t1 = t2; t2 = 0;
  }

  for (i = len; i < 2*len; i++) {
    int j;
    for (j = i-len+1; j < len; j++) {
      MACC(a[j], b[i-j], t0, t1, t2);
      MACC(m[j], n[i-j], t0, t1, t2);
    }
    m[i-len] = t0;
    t0 = t1; t1 = t2; t2 = 0;
  }

  while (t0)
    t0 = sub(m, n, t0, len);
}

// Fast Montgomery squaring.  This uses asymptotically 25% fewer
// multiplies so it should be up to 25% faster than Montgomery
// multiplication.  However, its loop control is more complex and it
// may actually run slower on some machines.

static void NOINLINE
montgomery_square(julong a[], julong n[],
                  julong m[], julong inv, int len) {
  julong t0 = 0, t1 = 0, t2 = 0; // Triple-precision accumulator
  int i;

  assert(inv * n[0] == ULLONG_MAX, "broken inverse in Montgomery square");

  for (i = 0; i < len; i++) {
    int j;
    int end = (i+1)/2;
    for (j = 0; j < end; j++) {
      MACC2(a[j], a[i-j], t0, t1, t2);
      MACC(m[j], n[i-j], t0, t1, t2);
    }
    if ((i & 1) == 0) {
      MACC(a[j], a[j], t0, t1, t2);
    }
    for (; j < i; j++) {
      MACC(m[j], n[i-j], t0, t1, t2);
    }
    m[i] = t0 * inv;
    MACC(m[i], n[0], t0, t1, t2);

    assert(t0 == 0, "broken Montgomery square");

    t0 = t1; t1 = t2; t2 = 0;
  }

  for (i = len; i < 2*len; i++) {
    int start = i-len+1;
    int end = start + (len - start)/2;
    int j;
    for (j = start; j < end; j++) {
      MACC2(a[j], a[i-j], t0, t1, t2);
      MACC(m[j], n[i-j], t0, t1, t2);
    }
    if ((i & 1) == 0) {
      MACC(a[j], a[j], t0, t1, t2);
    }
    for (; j < len; j++) {
      MACC(m[j], n[i-j], t0, t1, t2);
    }
    m[i-len] = t0;
    t0 = t1; t1 = t2; t2 = 0;
  }

  while (t0)
    t0 = sub(m, n, t0, len);
}

// Swap words in a longword.
static julong swap(julong x) {
  return (x << 32) | (x >> 32);
}

// Copy len longwords from s to d, word-swapping as we go.  The
// destination array is reversed.
static void reverse_words(julong *s, julong *d, int len) {
  d += len;
  while(len-- > 0) {
    d--;
    *d = swap(*s);
    s++;
  }
}

// The threshold at which squaring is advantageous was determined
// experimentally on an i7-3930K (Ivy Bridge) CPU @ 3.5GHz.
#define MONTGOMERY_SQUARING_THRESHOLD 64

void SharedRuntime::montgomery_multiply(jint *a_ints, jint *b_ints, jint *n_ints,
                                        jint len, jlong inv,
                                        jint *m_ints) {
  assert(len % 2 == 0, "array length in montgomery_multiply must be even");
  int longwords = len/2;

  // Make very sure we don't use so much space that the stack might
  // overflow.  512 jints corresponds to an 16384-bit integer and
  // will use here a total of 8k bytes of stack space.
  int divisor = sizeof(julong) * 4;
  guarantee(longwords <= 8192 / divisor, "must be");
  int total_allocation = longwords * sizeof (julong) * 4;
  julong *scratch = (julong *)alloca(total_allocation);

  // Local scratch arrays
  julong
    *a = scratch + 0 * longwords,
    *b = scratch + 1 * longwords,
    *n = scratch + 2 * longwords,
    *m = scratch + 3 * longwords;

  reverse_words((julong *)a_ints, a, longwords);
  reverse_words((julong *)b_ints, b, longwords);
  reverse_words((julong *)n_ints, n, longwords);

  ::montgomery_multiply(a, b, n, m, (julong)inv, longwords);

  reverse_words(m, (julong *)m_ints, longwords);
}

void SharedRuntime::montgomery_square(jint *a_ints, jint *n_ints,
                                      jint len, jlong inv,
                                      jint *m_ints) {
  assert(len % 2 == 0, "array length in montgomery_square must be even");
  int longwords = len/2;

  // Make very sure we don't use so much space that the stack might
  // overflow.  512 jints corresponds to an 16384-bit integer and
  // will use here a total of 6k bytes of stack space.
  int divisor = sizeof(julong) * 3;
  guarantee(longwords <= (8192 / divisor), "must be");
  int total_allocation = longwords * sizeof (julong) * 3;
  julong *scratch = (julong *)alloca(total_allocation);

  // Local scratch arrays
  julong
    *a = scratch + 0 * longwords,
    *n = scratch + 1 * longwords,
    *m = scratch + 2 * longwords;

  reverse_words((julong *)a_ints, a, longwords);
  reverse_words((julong *)n_ints, n, longwords);

  if (len >= MONTGOMERY_SQUARING_THRESHOLD) {
    ::montgomery_square(a, n, m, (julong)inv, longwords);
  } else {
    ::montgomery_multiply(a, a, n, m, (julong)inv, longwords);
  }

  reverse_words(m, (julong *)m_ints, longwords);
}

#ifdef COMPILER2
// This is here instead of runtime_x86_64.cpp because it uses SimpleRuntimeFrame
//
//------------------------------generate_exception_blob---------------------------
// creates exception blob at the end
// Using exception blob, this code is jumped from a compiled method.
// (see emit_exception_handler in x86_64.ad file)
//
// Given an exception pc at a call we call into the runtime for the
// handler in this method. This handler might merely restore state
// (i.e. callee save registers) unwind the frame and jump to the
// exception handler for the nmethod if there is no Java level handler
// for the nmethod.
//
// This code is entered with a jmp.
//
// Arguments:
//   rax: exception oop
//   rdx: exception pc
//
// Results:
//   rax: exception oop
//   rdx: exception pc in caller or ???
//   destination: exception handler of caller
//
// Note: the exception pc MUST be at a call (precise debug information)
//       Registers rax, rdx, rcx, rsi, rdi, r8-r11 are not callee saved.
//

void OptoRuntime::generate_exception_blob() {
  assert(!OptoRuntime::is_callee_saved_register(RDX_num), "");
  assert(!OptoRuntime::is_callee_saved_register(RAX_num), "");
  assert(!OptoRuntime::is_callee_saved_register(RCX_num), "");

  assert(SimpleRuntimeFrame::framesize % 4 == 0, "sp not 16-byte aligned");

  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  CodeBuffer buffer("exception_blob", 2048, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);


  address start = __ pc();

  // Exception pc is 'return address' for stack walker
  __ push(rdx);
  __ subptr(rsp, SimpleRuntimeFrame::return_off << LogBytesPerInt); // Prolog

  // Save callee-saved registers.  See x86_64.ad.

  // rbp is an implicitly saved callee saved register (i.e., the calling
  // convention will save/restore it in the prolog/epilog). Other than that
  // there are no callee save registers now that adapter frames are gone.

  __ movptr(Address(rsp, SimpleRuntimeFrame::rbp_off << LogBytesPerInt), rbp);

  // Store exception in Thread object. We cannot pass any arguments to the
  // handle_exception call, since we do not want to make any assumption
  // about the size of the frame where the exception happened in.
  // c_rarg0 is either rdi (Linux) or rcx (Windows).
  __ movptr(Address(r15_thread, JavaThread::exception_oop_offset()),rax);
  __ movptr(Address(r15_thread, JavaThread::exception_pc_offset()), rdx);

  // This call does all the hard work.  It checks if an exception handler
  // exists in the method.
  // If so, it returns the handler address.
  // If not, it prepares for stack-unwinding, restoring the callee-save
  // registers of the frame being removed.
  //
  // address OptoRuntime::handle_exception_C(JavaThread* thread)

  // At a method handle call, the stack may not be properly aligned
  // when returning with an exception.
  address the_pc = __ pc();
  __ set_last_Java_frame(noreg, noreg, the_pc, rscratch1);
  __ mov(c_rarg0, r15_thread);
  __ andptr(rsp, -(StackAlignmentInBytes));    // Align stack
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, OptoRuntime::handle_exception_C)));

  // Set an oopmap for the call site.  This oopmap will only be used if we
  // are unwinding the stack.  Hence, all locations will be dead.
  // Callee-saved registers will be the same as the frame above (i.e.,
  // handle_exception_stub), since they were restored when we got the
  // exception.

  OopMapSet* oop_maps = new OopMapSet();

  oop_maps->add_gc_map(the_pc - start, new OopMap(SimpleRuntimeFrame::framesize, 0));

  __ reset_last_Java_frame(false);

  // Restore callee-saved registers

  // rbp is an implicitly saved callee-saved register (i.e., the calling
  // convention will save restore it in prolog/epilog) Other than that
  // there are no callee save registers now that adapter frames are gone.

  __ movptr(rbp, Address(rsp, SimpleRuntimeFrame::rbp_off << LogBytesPerInt));

  __ addptr(rsp, SimpleRuntimeFrame::return_off << LogBytesPerInt); // Epilog
  __ pop(rdx);                  // No need for exception pc anymore

  // rax: exception handler

  // We have a handler in rax (could be deopt blob).
  __ mov(r8, rax);

  // Get the exception oop
  __ movptr(rax, Address(r15_thread, JavaThread::exception_oop_offset()));
  // Get the exception pc in case we are deoptimized
  __ movptr(rdx, Address(r15_thread, JavaThread::exception_pc_offset()));
#ifdef ASSERT
  __ movptr(Address(r15_thread, JavaThread::exception_handler_pc_offset()), NULL_WORD);
  __ movptr(Address(r15_thread, JavaThread::exception_pc_offset()), NULL_WORD);
#endif
  // Clear the exception oop so GC no longer processes it as a root.
  __ movptr(Address(r15_thread, JavaThread::exception_oop_offset()), NULL_WORD);

  // rax: exception oop
  // r8:  exception handler
  // rdx: exception pc
  // Jump to handler

  __ jmp(r8);

  // Make sure all code is generated
  masm->flush();

  // Set exception blob
  _exception_blob =  ExceptionBlob::create(&buffer, oop_maps, SimpleRuntimeFrame::framesize >> 1);
}
#endif // COMPILER2

