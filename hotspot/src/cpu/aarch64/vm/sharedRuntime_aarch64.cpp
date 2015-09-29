/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "code/debugInfoRec.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interp_masm.hpp"
#include "oops/compiledICHolder.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vframeArray.hpp"
#include "vmreg_aarch64.inline.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif
#ifdef COMPILER2
#include "adfiles/ad_aarch64.hpp"
#include "opto/runtime.hpp"
#endif

#ifdef BUILTIN_SIM
#include "../../../../../../simulator/simulator.hpp"
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
    // we don't expect any arg reg save area so aarch64 asserts that
    // frame::arg_reg_save_area_bytes == 0
    rbp_off = 0,
    rbp_off2,
    return_off, return_off2,
    framesize
  };
};

// FIXME -- this is used by C1
class RegisterSaver {
 public:
  static OopMap* save_live_registers(MacroAssembler* masm, int additional_frame_words, int* total_frame_words);
  static void restore_live_registers(MacroAssembler* masm);

  // Offsets into the register save area
  // Used by deoptimization when it is managing result register
  // values on its own

  static int r0_offset_in_bytes(void)    { return (32 + r0->encoding()) * wordSize; }
  static int reg_offset_in_bytes(Register r)    { return r0_offset_in_bytes() + r->encoding() * wordSize; }
  static int rmethod_offset_in_bytes(void)    { return reg_offset_in_bytes(rmethod); }
  static int rscratch1_offset_in_bytes(void)    { return (32 + rscratch1->encoding()) * wordSize; }
  static int v0_offset_in_bytes(void)   { return 0; }
  static int return_offset_in_bytes(void) { return (32 /* floats*/ + 31 /* gregs*/) * wordSize; }

  // During deoptimization only the result registers need to be restored,
  // all the other values have already been extracted.
  static void restore_result_registers(MacroAssembler* masm);

    // Capture info about frame layout
  enum layout {
                fpu_state_off = 0,
                fpu_state_end = fpu_state_off+FPUStateSizeInWords-1,
                // The frame sender code expects that rfp will be in
                // the "natural" place and will override any oopMap
                // setting for it. We must therefore force the layout
                // so that it agrees with the frame sender code.
                r0_off = fpu_state_off+FPUStateSizeInWords,
                rfp_off = r0_off + 30 * 2,
                return_off = rfp_off + 2,      // slot for return address
                reg_save_size = return_off + 2};

};

OopMap* RegisterSaver::save_live_registers(MacroAssembler* masm, int additional_frame_words, int* total_frame_words) {
  int frame_size_in_bytes = round_to(additional_frame_words*wordSize +
                                     reg_save_size*BytesPerInt, 16);
  // OopMap frame size is in compiler stack slots (jint's) not bytes or words
  int frame_size_in_slots = frame_size_in_bytes / BytesPerInt;
  // The caller will allocate additional_frame_words
  int additional_frame_slots = additional_frame_words*wordSize / BytesPerInt;
  // CodeBlob frame size is in words.
  int frame_size_in_words = frame_size_in_bytes / wordSize;
  *total_frame_words = frame_size_in_words;

  // Save registers, fpu state, and flags.

  __ enter();
  __ push_CPU_state();

  // Set an oopmap for the call site.  This oopmap will map all
  // oop-registers and debug-info registers as callee-saved.  This
  // will allow deoptimization at this safepoint to find all possible
  // debug-info recordings, as well as let GC find all oops.

  OopMapSet *oop_maps = new OopMapSet();
  OopMap* oop_map = new OopMap(frame_size_in_slots, 0);

  for (int i = 0; i < RegisterImpl::number_of_registers; i++) {
    Register r = as_Register(i);
    if (r < rheapbase && r != rscratch1 && r != rscratch2) {
      int sp_offset = 2 * (i + 32); // SP offsets are in 4-byte words,
                                    // register slots are 8 bytes
                                    // wide, 32 floating-point
                                    // registers
      oop_map->set_callee_saved(VMRegImpl::stack2reg(sp_offset),
                                r->as_VMReg());
    }
  }

  for (int i = 0; i < FloatRegisterImpl::number_of_registers; i++) {
    FloatRegister r = as_FloatRegister(i);
    int sp_offset = 2 * i;
    oop_map->set_callee_saved(VMRegImpl::stack2reg(sp_offset),
                              r->as_VMReg());
  }

  return oop_map;
}

void RegisterSaver::restore_live_registers(MacroAssembler* masm) {
  __ pop_CPU_state();
  __ leave();
}

void RegisterSaver::restore_result_registers(MacroAssembler* masm) {

  // Just restore result register. Only used by deoptimization. By
  // now any callee save register that needs to be restored to a c2
  // caller of the deoptee has been extracted into the vframeArray
  // and will be stuffed into the c2i adapter we create for later
  // restoration so only result registers need to be restored here.

  // Restore fp result register
  __ ldrd(v0, Address(sp, v0_offset_in_bytes()));
  // Restore integer result register
  __ ldr(r0, Address(sp, r0_offset_in_bytes()));

  // Pop all of the register save are off the stack
  __ add(sp, sp, round_to(return_offset_in_bytes(), 16));
}

// Is vector's size (in bytes) bigger than a size saved by default?
// 16 bytes XMM registers are saved by default using fxsave/fxrstor instructions.
bool SharedRuntime::is_wide_vector(int size) {
  return size > 16;
}
// The java_calling_convention describes stack locations as ideal slots on
// a frame with no abi restrictions. Since we must observe abi restrictions
// (like the placement of the register window) the slots must be biased by
// the following value.
static int reg2offset_in(VMReg r) {
  // Account for saved rfp and lr
  // This should really be in_preserve_stack_slots
  return (r->reg2stack() + 4) * VMRegImpl::stack_slot_size;
}

static int reg2offset_out(VMReg r) {
  return (r->reg2stack() + SharedRuntime::out_preserve_stack_slots()) * VMRegImpl::stack_slot_size;
}

template <class T> static const T& min (const T& a, const T& b) {
  return (a > b) ? b : a;
}

// ---------------------------------------------------------------------------
// Read the array of BasicTypes from a signature, and compute where the
// arguments should go.  Values in the VMRegPair regs array refer to 4-byte
// quantities.  Values less than VMRegImpl::stack0 are registers, those above
// refer to 4-byte stack slots.  All stack slots are based off of the stack pointer
// as framesizes are fixed.
// VMRegImpl::stack0 refers to the first slot 0(sp).
// and VMRegImpl::stack0+1 refers to the memory word 4-byes higher.  Register
// up to RegisterImpl::number_of_registers) are the 64-bit
// integer registers.

// Note: the INPUTS in sig_bt are in units of Java argument words,
// which are 64-bit.  The OUTPUTS are in 32-bit units.

// The Java calling convention is a "shifted" version of the C ABI.
// By skipping the first C ABI register we can call non-static jni
// methods with small numbers of arguments without having to shuffle
// the arguments at all. Since we control the java ABI we ought to at
// least get some advantage out of it.

int SharedRuntime::java_calling_convention(const BasicType *sig_bt,
                                           VMRegPair *regs,
                                           int total_args_passed,
                                           int is_outgoing) {

  // Create the mapping between argument positions and
  // registers.
  static const Register INT_ArgReg[Argument::n_int_register_parameters_j] = {
    j_rarg0, j_rarg1, j_rarg2, j_rarg3, j_rarg4, j_rarg5, j_rarg6, j_rarg7
  };
  static const FloatRegister FP_ArgReg[Argument::n_float_register_parameters_j] = {
    j_farg0, j_farg1, j_farg2, j_farg3,
    j_farg4, j_farg5, j_farg6, j_farg7
  };


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
      if (int_args < Argument::n_int_register_parameters_j) {
        regs[i].set1(INT_ArgReg[int_args++]->as_VMReg());
      } else {
        regs[i].set1(VMRegImpl::stack2reg(stk_args));
        stk_args += 2;
      }
      break;
    case T_VOID:
      // halves of T_LONG or T_DOUBLE
      assert(i != 0 && (sig_bt[i - 1] == T_LONG || sig_bt[i - 1] == T_DOUBLE), "expecting half");
      regs[i].set_bad();
      break;
    case T_LONG:
      assert(sig_bt[i + 1] == T_VOID, "expecting half");
      // fall through
    case T_OBJECT:
    case T_ARRAY:
    case T_ADDRESS:
      if (int_args < Argument::n_int_register_parameters_j) {
        regs[i].set2(INT_ArgReg[int_args++]->as_VMReg());
      } else {
        regs[i].set2(VMRegImpl::stack2reg(stk_args));
        stk_args += 2;
      }
      break;
    case T_FLOAT:
      if (fp_args < Argument::n_float_register_parameters_j) {
        regs[i].set1(FP_ArgReg[fp_args++]->as_VMReg());
      } else {
        regs[i].set1(VMRegImpl::stack2reg(stk_args));
        stk_args += 2;
      }
      break;
    case T_DOUBLE:
      assert(sig_bt[i + 1] == T_VOID, "expecting half");
      if (fp_args < Argument::n_float_register_parameters_j) {
        regs[i].set2(FP_ArgReg[fp_args++]->as_VMReg());
      } else {
        regs[i].set2(VMRegImpl::stack2reg(stk_args));
        stk_args += 2;
      }
      break;
    default:
      ShouldNotReachHere();
      break;
    }
  }

  return round_to(stk_args, 2);
}

// Patch the callers callsite with entry to compiled code if it exists.
static void patch_callers_callsite(MacroAssembler *masm) {
  Label L;
  __ ldr(rscratch1, Address(rmethod, in_bytes(Method::code_offset())));
  __ cbz(rscratch1, L);

  __ enter();
  __ push_CPU_state();

  // VM needs caller's callsite
  // VM needs target method
  // This needs to be a long call since we will relocate this adapter to
  // the codeBuffer and it may not reach

#ifndef PRODUCT
  assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
#endif

  __ mov(c_rarg0, rmethod);
  __ mov(c_rarg1, lr);
  __ lea(rscratch1, RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::fixup_callers_callsite)));
  __ blrt(rscratch1, 2, 0, 0);
  __ maybe_isb();

  __ pop_CPU_state();
  // restore sp
  __ leave();
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

  int words_pushed = 0;

  // Since all args are passed on the stack, total_args_passed *
  // Interpreter::stackElementSize is the space we need.

  int extraspace = total_args_passed * Interpreter::stackElementSize;

  __ mov(r13, sp);

  // stack is aligned, keep it that way
  extraspace = round_to(extraspace, 2*wordSize);

  if (extraspace)
    __ sub(sp, sp, extraspace);

  // Now write the args into the outgoing interpreter space
  for (int i = 0; i < total_args_passed; i++) {
    if (sig_bt[i] == T_VOID) {
      assert(i > 0 && (sig_bt[i-1] == T_LONG || sig_bt[i-1] == T_DOUBLE), "missing half");
      continue;
    }

    // offset to start parameters
    int st_off   = (total_args_passed - i - 1) * Interpreter::stackElementSize;
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
      // memory to memory use rscratch1
      int ld_off = (r_1->reg2stack() * VMRegImpl::stack_slot_size
                    + extraspace
                    + words_pushed * wordSize);
      if (!r_2->is_valid()) {
        // sign extend??
        __ ldrw(rscratch1, Address(sp, ld_off));
        __ str(rscratch1, Address(sp, st_off));

      } else {

        __ ldr(rscratch1, Address(sp, ld_off));

        // Two VMREgs|OptoRegs can be T_OBJECT, T_ADDRESS, T_DOUBLE, T_LONG
        // T_DOUBLE and T_LONG use two slots in the interpreter
        if ( sig_bt[i] == T_LONG || sig_bt[i] == T_DOUBLE) {
          // ld_off == LSW, ld_off+wordSize == MSW
          // st_off == MSW, next_off == LSW
          __ str(rscratch1, Address(sp, next_off));
#ifdef ASSERT
          // Overwrite the unused slot with known junk
          __ mov(rscratch1, 0xdeadffffdeadaaaaul);
          __ str(rscratch1, Address(sp, st_off));
#endif /* ASSERT */
        } else {
          __ str(rscratch1, Address(sp, st_off));
        }
      }
    } else if (r_1->is_Register()) {
      Register r = r_1->as_Register();
      if (!r_2->is_valid()) {
        // must be only an int (or less ) so move only 32bits to slot
        // why not sign extend??
        __ str(r, Address(sp, st_off));
      } else {
        // Two VMREgs|OptoRegs can be T_OBJECT, T_ADDRESS, T_DOUBLE, T_LONG
        // T_DOUBLE and T_LONG use two slots in the interpreter
        if ( sig_bt[i] == T_LONG || sig_bt[i] == T_DOUBLE) {
          // long/double in gpr
#ifdef ASSERT
          // Overwrite the unused slot with known junk
          __ mov(rscratch1, 0xdeadffffdeadaaabul);
          __ str(rscratch1, Address(sp, st_off));
#endif /* ASSERT */
          __ str(r, Address(sp, next_off));
        } else {
          __ str(r, Address(sp, st_off));
        }
      }
    } else {
      assert(r_1->is_FloatRegister(), "");
      if (!r_2->is_valid()) {
        // only a float use just part of the slot
        __ strs(r_1->as_FloatRegister(), Address(sp, st_off));
      } else {
#ifdef ASSERT
        // Overwrite the unused slot with known junk
        __ mov(rscratch1, 0xdeadffffdeadaaacul);
        __ str(rscratch1, Address(sp, st_off));
#endif /* ASSERT */
        __ strd(r_1->as_FloatRegister(), Address(sp, next_off));
      }
    }
  }

  __ mov(esp, sp); // Interp expects args on caller's expression stack

  __ ldr(rscratch1, Address(rmethod, in_bytes(Method::interpreter_entry_offset())));
  __ br(rscratch1);
}


static void gen_i2c_adapter(MacroAssembler *masm,
                            int total_args_passed,
                            int comp_args_on_stack,
                            const BasicType *sig_bt,
                            const VMRegPair *regs) {

  // Note: r13 contains the senderSP on entry. We must preserve it since
  // we may do a i2c -> c2i transition if we lose a race where compiled
  // code goes non-entrant while we get args ready.

  // In addition we use r13 to locate all the interpreter args because
  // we must align the stack to 16 bytes.

  // Adapters are frameless.

  // An i2c adapter is frameless because the *caller* frame, which is
  // interpreted, routinely repairs its own esp (from
  // interpreter_frame_last_sp), even if a callee has modified the
  // stack pointer.  It also recalculates and aligns sp.

  // A c2i adapter is frameless because the *callee* frame, which is
  // interpreted, routinely repairs its caller's sp (from sender_sp,
  // which is set up via the senderSP register).

  // In other words, if *either* the caller or callee is interpreted, we can
  // get the stack pointer repaired after a call.

  // This is why c2i and i2c adapters cannot be indefinitely composed.
  // In particular, if a c2i adapter were to somehow call an i2c adapter,
  // both caller and callee would be compiled methods, and neither would
  // clean up the stack pointer changes performed by the two adapters.
  // If this happens, control eventually transfers back to the compiled
  // caller, but with an uncorrected stack, causing delayed havoc.

  if (VerifyAdapterCalls &&
      (Interpreter::code() != NULL || StubRoutines::code1() != NULL)) {
#if 0
    // So, let's test for cascading c2i/i2c adapters right now.
    //  assert(Interpreter::contains($return_addr) ||
    //         StubRoutines::contains($return_addr),
    //         "i2c adapter must return to an interpreter frame");
    __ block_comment("verify_i2c { ");
    Label L_ok;
    if (Interpreter::code() != NULL)
      range_check(masm, rax, r11,
                  Interpreter::code()->code_start(), Interpreter::code()->code_end(),
                  L_ok);
    if (StubRoutines::code1() != NULL)
      range_check(masm, rax, r11,
                  StubRoutines::code1()->code_begin(), StubRoutines::code1()->code_end(),
                  L_ok);
    if (StubRoutines::code2() != NULL)
      range_check(masm, rax, r11,
                  StubRoutines::code2()->code_begin(), StubRoutines::code2()->code_end(),
                  L_ok);
    const char* msg = "i2c adapter must return to an interpreter frame";
    __ block_comment(msg);
    __ stop(msg);
    __ bind(L_ok);
    __ block_comment("} verify_i2ce ");
#endif
  }

  // Cut-out for having no stack args.
  int comp_words_on_stack = round_to(comp_args_on_stack*VMRegImpl::stack_slot_size, wordSize)>>LogBytesPerWord;
  if (comp_args_on_stack) {
    __ sub(rscratch1, sp, comp_words_on_stack * wordSize);
    __ andr(sp, rscratch1, -16);
  }

  // Will jump to the compiled code just as if compiled code was doing it.
  // Pre-load the register-jump target early, to schedule it better.
  __ ldr(rscratch1, Address(rmethod, in_bytes(Method::from_compiled_offset())));

  // Now generate the shuffle code.
  for (int i = 0; i < total_args_passed; i++) {
    if (sig_bt[i] == T_VOID) {
      assert(i > 0 && (sig_bt[i-1] == T_LONG || sig_bt[i-1] == T_DOUBLE), "missing half");
      continue;
    }

    // Pick up 0, 1 or 2 words from SP+offset.

    assert(!regs[i].second()->is_valid() || regs[i].first()->next() == regs[i].second(),
            "scrambled load targets?");
    // Load in argument order going down.
    int ld_off = (total_args_passed - i - 1)*Interpreter::stackElementSize;
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
      int st_off = regs[i].first()->reg2stack()*VMRegImpl::stack_slot_size;
      if (!r_2->is_valid()) {
        // sign extend???
        __ ldrsw(rscratch2, Address(esp, ld_off));
        __ str(rscratch2, Address(sp, st_off));
      } else {
        //
        // We are using two optoregs. This can be either T_OBJECT,
        // T_ADDRESS, T_LONG, or T_DOUBLE the interpreter allocates
        // two slots but only uses one for thr T_LONG or T_DOUBLE case
        // So we must adjust where to pick up the data to match the
        // interpreter.
        //
        // Interpreter local[n] == MSW, local[n+1] == LSW however locals
        // are accessed as negative so LSW is at LOW address

        // ld_off is MSW so get LSW
        const int offset = (sig_bt[i]==T_LONG||sig_bt[i]==T_DOUBLE)?
                           next_off : ld_off;
        __ ldr(rscratch2, Address(esp, offset));
        // st_off is LSW (i.e. reg.first())
        __ str(rscratch2, Address(sp, st_off));
      }
    } else if (r_1->is_Register()) {  // Register argument
      Register r = r_1->as_Register();
      if (r_2->is_valid()) {
        //
        // We are using two VMRegs. This can be either T_OBJECT,
        // T_ADDRESS, T_LONG, or T_DOUBLE the interpreter allocates
        // two slots but only uses one for thr T_LONG or T_DOUBLE case
        // So we must adjust where to pick up the data to match the
        // interpreter.

        const int offset = (sig_bt[i]==T_LONG||sig_bt[i]==T_DOUBLE)?
                           next_off : ld_off;

        // this can be a misaligned move
        __ ldr(r, Address(esp, offset));
      } else {
        // sign extend and use a full word?
        __ ldrw(r, Address(esp, ld_off));
      }
    } else {
      if (!r_2->is_valid()) {
        __ ldrs(r_1->as_FloatRegister(), Address(esp, ld_off));
      } else {
        __ ldrd(r_1->as_FloatRegister(), Address(esp, next_off));
      }
    }
  }

  // 6243940 We might end up in handle_wrong_method if
  // the callee is deoptimized as we race thru here. If that
  // happens we don't want to take a safepoint because the
  // caller frame will look interpreted and arguments are now
  // "compiled" so it is much better to make this transition
  // invisible to the stack walking code. Unfortunately if
  // we try and find the callee by normal means a safepoint
  // is possible. So we stash the desired callee in the thread
  // and the vm will find there should this case occur.

  __ str(rmethod, Address(rthread, JavaThread::callee_target_offset()));

  __ br(rscratch1);
}

#ifdef BUILTIN_SIM
static void generate_i2c_adapter_name(char *result, int total_args_passed, const BasicType *sig_bt)
{
  strcpy(result, "i2c(");
  int idx = 4;
  for (int i = 0; i < total_args_passed; i++) {
    switch(sig_bt[i]) {
    case T_BOOLEAN:
      result[idx++] = 'Z';
      break;
    case T_CHAR:
      result[idx++] = 'C';
      break;
    case T_FLOAT:
      result[idx++] = 'F';
      break;
    case T_DOUBLE:
      assert((i < (total_args_passed - 1)) && (sig_bt[i+1] == T_VOID),
             "double must be followed by void");
      i++;
      result[idx++] = 'D';
      break;
    case T_BYTE:
      result[idx++] = 'B';
      break;
    case T_SHORT:
      result[idx++] = 'S';
      break;
    case T_INT:
      result[idx++] = 'I';
      break;
    case T_LONG:
      assert((i < (total_args_passed - 1)) && (sig_bt[i+1] == T_VOID),
             "long must be followed by void");
      i++;
      result[idx++] = 'L';
      break;
    case T_OBJECT:
      result[idx++] = 'O';
      break;
    case T_ARRAY:
      result[idx++] = '[';
      break;
    case T_ADDRESS:
      result[idx++] = 'P';
      break;
    case T_NARROWOOP:
      result[idx++] = 'N';
      break;
    case T_METADATA:
      result[idx++] = 'M';
      break;
    case T_NARROWKLASS:
      result[idx++] = 'K';
      break;
    default:
      result[idx++] = '?';
      break;
    }
  }
  result[idx++] = ')';
  result[idx] = '\0';
}
#endif

// ---------------------------------------------------------------
AdapterHandlerEntry* SharedRuntime::generate_i2c2i_adapters(MacroAssembler *masm,
                                                            int total_args_passed,
                                                            int comp_args_on_stack,
                                                            const BasicType *sig_bt,
                                                            const VMRegPair *regs,
                                                            AdapterFingerPrint* fingerprint) {
  address i2c_entry = __ pc();
#ifdef BUILTIN_SIM
  char *name = NULL;
  AArch64Simulator *sim = NULL;
  size_t len = 65536;
  if (NotifySimulator) {
    name = NEW_C_HEAP_ARRAY(char, len, mtInternal);
  }

  if (name) {
    generate_i2c_adapter_name(name, total_args_passed, sig_bt);
    sim = AArch64Simulator::get_current(UseSimulatorCache, DisableBCCheck);
    sim->notifyCompile(name, i2c_entry);
  }
#endif
  gen_i2c_adapter(masm, total_args_passed, comp_args_on_stack, sig_bt, regs);

  address c2i_unverified_entry = __ pc();
  Label skip_fixup;

  Label ok;

  Register holder = rscratch2;
  Register receiver = j_rarg0;
  Register tmp = r10;  // A call-clobbered register not used for arg passing

  // -------------------------------------------------------------------------
  // Generate a C2I adapter.  On entry we know rmethod holds the Method* during calls
  // to the interpreter.  The args start out packed in the compiled layout.  They
  // need to be unpacked into the interpreter layout.  This will almost always
  // require some stack space.  We grow the current (compiled) stack, then repack
  // the args.  We  finally end in a jump to the generic interpreter entry point.
  // On exit from the interpreter, the interpreter will restore our SP (lest the
  // compiled code, which relys solely on SP and not FP, get sick).

  {
    __ block_comment("c2i_unverified_entry {");
    __ load_klass(rscratch1, receiver);
    __ ldr(tmp, Address(holder, CompiledICHolder::holder_klass_offset()));
    __ cmp(rscratch1, tmp);
    __ ldr(rmethod, Address(holder, CompiledICHolder::holder_method_offset()));
    __ br(Assembler::EQ, ok);
    __ far_jump(RuntimeAddress(SharedRuntime::get_ic_miss_stub()));

    __ bind(ok);
    // Method might have been compiled since the call site was patched to
    // interpreted; if that is the case treat it as a miss so we can get
    // the call site corrected.
    __ ldr(rscratch1, Address(rmethod, in_bytes(Method::code_offset())));
    __ cbz(rscratch1, skip_fixup);
    __ far_jump(RuntimeAddress(SharedRuntime::get_ic_miss_stub()));
    __ block_comment("} c2i_unverified_entry");
  }

  address c2i_entry = __ pc();

#ifdef BUILTIN_SIM
  if (name) {
    name[0] = 'c';
    name[2] = 'i';
    sim->notifyCompile(name, c2i_entry);
    FREE_C_HEAP_ARRAY(char, name, mtInternal);
  }
#endif

  gen_c2i_adapter(masm, total_args_passed, comp_args_on_stack, sig_bt, regs, skip_fixup);

  __ flush();
  return AdapterHandlerLibrary::new_entry(fingerprint, i2c_entry, c2i_entry, c2i_unverified_entry);
}

int SharedRuntime::c_calling_convention(const BasicType *sig_bt,
                                         VMRegPair *regs,
                                         VMRegPair *regs2,
                                         int total_args_passed) {
  assert(regs2 == NULL, "not needed on AArch64");

// We return the amount of VMRegImpl stack slots we need to reserve for all
// the arguments NOT counting out_preserve_stack_slots.

    static const Register INT_ArgReg[Argument::n_int_register_parameters_c] = {
      c_rarg0, c_rarg1, c_rarg2, c_rarg3, c_rarg4, c_rarg5,  c_rarg6,  c_rarg7
    };
    static const FloatRegister FP_ArgReg[Argument::n_float_register_parameters_c] = {
      c_farg0, c_farg1, c_farg2, c_farg3,
      c_farg4, c_farg5, c_farg6, c_farg7
    };

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
        } else {
          regs[i].set1(VMRegImpl::stack2reg(stk_args));
          stk_args += 2;
        }
        break;
      case T_LONG:
        assert(sig_bt[i + 1] == T_VOID, "expecting half");
        // fall through
      case T_OBJECT:
      case T_ARRAY:
      case T_ADDRESS:
      case T_METADATA:
        if (int_args < Argument::n_int_register_parameters_c) {
          regs[i].set2(INT_ArgReg[int_args++]->as_VMReg());
        } else {
          regs[i].set2(VMRegImpl::stack2reg(stk_args));
          stk_args += 2;
        }
        break;
      case T_FLOAT:
        if (fp_args < Argument::n_float_register_parameters_c) {
          regs[i].set1(FP_ArgReg[fp_args++]->as_VMReg());
        } else {
          regs[i].set1(VMRegImpl::stack2reg(stk_args));
          stk_args += 2;
        }
        break;
      case T_DOUBLE:
        assert(sig_bt[i + 1] == T_VOID, "expecting half");
        if (fp_args < Argument::n_float_register_parameters_c) {
          regs[i].set2(FP_ArgReg[fp_args++]->as_VMReg());
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

  return stk_args;
}

// On 64 bit we will store integer like items to the stack as
// 64 bits items (sparc abi) even though java would only store
// 32bits for a parameter. On 32bit it will simply be 32 bits
// So this routine will do 32->32 on 32bit and 32->64 on 64bit
static void move32_64(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      __ ldr(rscratch1, Address(rfp, reg2offset_in(src.first())));
      __ str(rscratch1, Address(sp, reg2offset_out(dst.first())));
    } else {
      // stack to reg
      __ ldrsw(dst.first()->as_Register(), Address(rfp, reg2offset_in(src.first())));
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    // Do we really have to sign extend???
    // __ movslq(src.first()->as_Register(), src.first()->as_Register());
    __ str(src.first()->as_Register(), Address(sp, reg2offset_out(dst.first())));
  } else {
    if (dst.first() != src.first()) {
      __ sxtw(dst.first()->as_Register(), src.first()->as_Register());
    }
  }
}

// An oop arg. Must pass a handle not the oop itself
static void object_move(MacroAssembler* masm,
                        OopMap* map,
                        int oop_handle_offset,
                        int framesize_in_slots,
                        VMRegPair src,
                        VMRegPair dst,
                        bool is_receiver,
                        int* receiver_offset) {

  // must pass a handle. First figure out the location we use as a handle

  Register rHandle = dst.first()->is_stack() ? rscratch2 : dst.first()->as_Register();

  // See if oop is NULL if it is we need no handle

  if (src.first()->is_stack()) {

    // Oop is already on the stack as an argument
    int offset_in_older_frame = src.first()->reg2stack() + SharedRuntime::out_preserve_stack_slots();
    map->set_oop(VMRegImpl::stack2reg(offset_in_older_frame + framesize_in_slots));
    if (is_receiver) {
      *receiver_offset = (offset_in_older_frame + framesize_in_slots) * VMRegImpl::stack_slot_size;
    }

    __ ldr(rscratch1, Address(rfp, reg2offset_in(src.first())));
    __ lea(rHandle, Address(rfp, reg2offset_in(src.first())));
    // conditionally move a NULL
    __ cmp(rscratch1, zr);
    __ csel(rHandle, zr, rHandle, Assembler::EQ);
  } else {

    // Oop is in an a register we must store it to the space we reserve
    // on the stack for oop_handles and pass a handle if oop is non-NULL

    const Register rOop = src.first()->as_Register();
    int oop_slot;
    if (rOop == j_rarg0)
      oop_slot = 0;
    else if (rOop == j_rarg1)
      oop_slot = 1;
    else if (rOop == j_rarg2)
      oop_slot = 2;
    else if (rOop == j_rarg3)
      oop_slot = 3;
    else if (rOop == j_rarg4)
      oop_slot = 4;
    else if (rOop == j_rarg5)
      oop_slot = 5;
    else if (rOop == j_rarg6)
      oop_slot = 6;
    else {
      assert(rOop == j_rarg7, "wrong register");
      oop_slot = 7;
    }

    oop_slot = oop_slot * VMRegImpl::slots_per_word + oop_handle_offset;
    int offset = oop_slot*VMRegImpl::stack_slot_size;

    map->set_oop(VMRegImpl::stack2reg(oop_slot));
    // Store oop in handle area, may be NULL
    __ str(rOop, Address(sp, offset));
    if (is_receiver) {
      *receiver_offset = offset;
    }

    __ cmp(rOop, zr);
    __ lea(rHandle, Address(sp, offset));
    // conditionally move a NULL
    __ csel(rHandle, zr, rHandle, Assembler::EQ);
  }

  // If arg is on the stack then place it otherwise it is already in correct reg.
  if (dst.first()->is_stack()) {
    __ str(rHandle, Address(sp, reg2offset_out(dst.first())));
  }
}

// A float arg may have to do float reg int reg conversion
static void float_move(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {
  if (src.first() != dst.first()) {
    if (src.is_single_phys_reg() && dst.is_single_phys_reg())
      __ fmovs(dst.first()->as_FloatRegister(), src.first()->as_FloatRegister());
    else
      ShouldNotReachHere();
  }
}

// A long move
static void long_move(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      __ ldr(rscratch1, Address(rfp, reg2offset_in(src.first())));
      __ str(rscratch1, Address(sp, reg2offset_out(dst.first())));
    } else {
      // stack to reg
      __ ldr(dst.first()->as_Register(), Address(rfp, reg2offset_in(src.first())));
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    // Do we really have to sign extend???
    // __ movslq(src.first()->as_Register(), src.first()->as_Register());
    __ str(src.first()->as_Register(), Address(sp, reg2offset_out(dst.first())));
  } else {
    if (dst.first() != src.first()) {
      __ mov(dst.first()->as_Register(), src.first()->as_Register());
    }
  }
}


// A double move
static void double_move(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {
  if (src.first() != dst.first()) {
    if (src.is_single_phys_reg() && dst.is_single_phys_reg())
      __ fmovd(dst.first()->as_FloatRegister(), src.first()->as_FloatRegister());
    else
      ShouldNotReachHere();
  }
}


void SharedRuntime::save_native_result(MacroAssembler *masm, BasicType ret_type, int frame_slots) {
  // We always ignore the frame_slots arg and just use the space just below frame pointer
  // which by this time is free to use
  switch (ret_type) {
  case T_FLOAT:
    __ strs(v0, Address(rfp, -wordSize));
    break;
  case T_DOUBLE:
    __ strd(v0, Address(rfp, -wordSize));
    break;
  case T_VOID:  break;
  default: {
    __ str(r0, Address(rfp, -wordSize));
    }
  }
}

void SharedRuntime::restore_native_result(MacroAssembler *masm, BasicType ret_type, int frame_slots) {
  // We always ignore the frame_slots arg and just use the space just below frame pointer
  // which by this time is free to use
  switch (ret_type) {
  case T_FLOAT:
    __ ldrs(v0, Address(rfp, -wordSize));
    break;
  case T_DOUBLE:
    __ ldrd(v0, Address(rfp, -wordSize));
    break;
  case T_VOID:  break;
  default: {
    __ ldr(r0, Address(rfp, -wordSize));
    }
  }
}
static void save_args(MacroAssembler *masm, int arg_count, int first_arg, VMRegPair *args) {
  RegSet x;
  for ( int i = first_arg ; i < arg_count ; i++ ) {
    if (args[i].first()->is_Register()) {
      x = x + args[i].first()->as_Register();
    } else if (args[i].first()->is_FloatRegister()) {
      __ strd(args[i].first()->as_FloatRegister(), Address(__ pre(sp, -2 * wordSize)));
    }
  }
  __ push(x, sp);
}

static void restore_args(MacroAssembler *masm, int arg_count, int first_arg, VMRegPair *args) {
  RegSet x;
  for ( int i = first_arg ; i < arg_count ; i++ ) {
    if (args[i].first()->is_Register()) {
      x = x + args[i].first()->as_Register();
    } else {
      ;
    }
  }
  __ pop(x, sp);
  for ( int i = first_arg ; i < arg_count ; i++ ) {
    if (args[i].first()->is_Register()) {
      ;
    } else if (args[i].first()->is_FloatRegister()) {
      __ ldrd(args[i].first()->as_FloatRegister(), Address(__ post(sp, 2 * wordSize)));
    }
  }
}


// Check GC_locker::needs_gc and enter the runtime if it's true.  This
// keeps a new JNI critical region from starting until a GC has been
// forced.  Save down any oops in registers and describe them in an
// OopMap.
static void check_needs_gc_for_critical_native(MacroAssembler* masm,
                                               int stack_slots,
                                               int total_c_args,
                                               int total_in_args,
                                               int arg_save_area,
                                               OopMapSet* oop_maps,
                                               VMRegPair* in_regs,
                                               BasicType* in_sig_bt) { Unimplemented(); }

// Unpack an array argument into a pointer to the body and the length
// if the array is non-null, otherwise pass 0 for both.
static void unpack_array_argument(MacroAssembler* masm, VMRegPair reg, BasicType in_elem_type, VMRegPair body_arg, VMRegPair length_arg) { Unimplemented(); }


class ComputeMoveOrder: public StackObj {
  class MoveOperation: public ResourceObj {
    friend class ComputeMoveOrder;
   private:
    VMRegPair        _src;
    VMRegPair        _dst;
    int              _src_index;
    int              _dst_index;
    bool             _processed;
    MoveOperation*  _next;
    MoveOperation*  _prev;

    static int get_id(VMRegPair r) { Unimplemented(); return 0; }

   public:
    MoveOperation(int src_index, VMRegPair src, int dst_index, VMRegPair dst):
      _src(src)
    , _src_index(src_index)
    , _dst(dst)
    , _dst_index(dst_index)
    , _next(NULL)
    , _prev(NULL)
    , _processed(false) { Unimplemented(); }

    VMRegPair src() const              { Unimplemented(); return _src; }
    int src_id() const                 { Unimplemented(); return 0; }
    int src_index() const              { Unimplemented(); return 0; }
    VMRegPair dst() const              { Unimplemented(); return _src; }
    void set_dst(int i, VMRegPair dst) { Unimplemented(); }
    int dst_index() const              { Unimplemented(); return 0; }
    int dst_id() const                 { Unimplemented(); return 0; }
    MoveOperation* next() const        { Unimplemented(); return 0; }
    MoveOperation* prev() const        { Unimplemented(); return 0; }
    void set_processed()               { Unimplemented(); }
    bool is_processed() const          { Unimplemented(); return 0; }

    // insert
    void break_cycle(VMRegPair temp_register) { Unimplemented(); }

    void link(GrowableArray<MoveOperation*>& killer) { Unimplemented(); }
  };

 private:
  GrowableArray<MoveOperation*> edges;

 public:
  ComputeMoveOrder(int total_in_args, VMRegPair* in_regs, int total_c_args, VMRegPair* out_regs,
                    BasicType* in_sig_bt, GrowableArray<int>& arg_order, VMRegPair tmp_vmreg) { Unimplemented(); }

  // Collected all the move operations
  void add_edge(int src_index, VMRegPair src, int dst_index, VMRegPair dst) { Unimplemented(); }

  // Walk the edges breaking cycles between moves.  The result list
  // can be walked in order to produce the proper set of loads
  GrowableArray<MoveOperation*>* get_store_order(VMRegPair temp_register) { Unimplemented(); return 0; }
};


static void rt_call(MacroAssembler* masm, address dest, int gpargs, int fpargs, int type) {
  CodeBlob *cb = CodeCache::find_blob(dest);
  if (cb) {
    __ far_call(RuntimeAddress(dest));
  } else {
    assert((unsigned)gpargs < 256, "eek!");
    assert((unsigned)fpargs < 32, "eek!");
    __ lea(rscratch1, RuntimeAddress(dest));
    __ mov(rscratch2, (gpargs << 6) | (fpargs << 2) | type);
    __ blrt(rscratch1, rscratch2);
    __ maybe_isb();
  }
}

static void verify_oop_args(MacroAssembler* masm,
                            methodHandle method,
                            const BasicType* sig_bt,
                            const VMRegPair* regs) {
  Register temp_reg = r19;  // not part of any compiled calling seq
  if (VerifyOops) {
    for (int i = 0; i < method->size_of_parameters(); i++) {
      if (sig_bt[i] == T_OBJECT ||
          sig_bt[i] == T_ARRAY) {
        VMReg r = regs[i].first();
        assert(r->is_valid(), "bad oop arg");
        if (r->is_stack()) {
          __ ldr(temp_reg, Address(sp, r->reg2stack() * VMRegImpl::stack_slot_size));
          __ verify_oop(temp_reg);
        } else {
          __ verify_oop(r->as_Register());
        }
      }
    }
  }
}

static void gen_special_dispatch(MacroAssembler* masm,
                                 methodHandle method,
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
    member_reg = r19;  // known to be free at this point
    has_receiver = MethodHandles::ref_kind_has_receiver(ref_kind);
  } else if (iid == vmIntrinsics::_invokeBasic) {
    has_receiver = true;
  } else {
    fatal("unexpected intrinsic id %d", iid);
  }

  if (member_reg != noreg) {
    // Load the member_arg into register, if necessary.
    SharedRuntime::check_member_name_argument_is_last_argument(method, sig_bt, regs);
    VMReg r = regs[member_arg_pos].first();
    if (r->is_stack()) {
      __ ldr(member_reg, Address(sp, r->reg2stack() * VMRegImpl::stack_slot_size));
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
      receiver_reg = r2;  // known to be free at this point
      __ ldr(receiver_reg, Address(sp, r->reg2stack() * VMRegImpl::stack_slot_size));
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
// passing them to the callee and perform checks before and after the
// native call to ensure that they GC_locker
// lock_critical/unlock_critical semantics are followed.  Some other
// parts of JNI setup are skipped like the tear down of the JNI handle
// block and the check for pending exceptions it's impossible for them
// to be thrown.
//
// They are roughly structured like this:
//    if (GC_locker::needs_gc())
//      SharedRuntime::block_for_jni_critical();
//    tranistion to thread_in_native
//    unpack arrray arguments and call native entry point
//    check for safepoint in progress
//    check if any thread suspend flags are set
//      call into JVM and possible unlock the JNI critical
//      if a GC was suppressed while in the critical native.
//    transition back to thread_in_Java
//    return to caller
//
nmethod* SharedRuntime::generate_native_wrapper(MacroAssembler* masm,
                                                methodHandle method,
                                                int compile_id,
                                                BasicType* in_sig_bt,
                                                VMRegPair* in_regs,
                                                BasicType ret_type) {
#ifdef BUILTIN_SIM
  if (NotifySimulator) {
    // Names are up to 65536 chars long.  UTF8-coded strings are up to
    // 3 bytes per character.  We concatenate three such strings.
    // Yes, I know this is ridiculous, but it's debug code and glibc
    // allocates large arrays very efficiently.
    size_t len = (65536 * 3) * 3;
    char *name = new char[len];

    strncpy(name, method()->method_holder()->name()->as_utf8(), len);
    strncat(name, ".", len);
    strncat(name, method()->name()->as_utf8(), len);
    strncat(name, method()->signature()->as_utf8(), len);
    AArch64Simulator::get_current(UseSimulatorCache, DisableBCCheck)->notifyCompile(name, __ pc());
    delete[] name;
  }
#endif

  if (method->is_method_handle_intrinsic()) {
    vmIntrinsics::ID iid = method->intrinsic_id();
    intptr_t start = (intptr_t)__ pc();
    int vep_offset = ((intptr_t)__ pc()) - start;

    // First instruction must be a nop as it may need to be patched on deoptimisation
    __ nop();
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
                                       (OopMapSet*)NULL);
  }
  bool is_critical_native = true;
  address native_func = method->critical_native_function();
  if (native_func == NULL) {
    native_func = method->native_function();
    is_critical_native = false;
  }
  assert(native_func != NULL, "must have function");

  // An OopMap for lock (and class if static)
  OopMapSet *oop_maps = new OopMapSet();
  intptr_t start = (intptr_t)__ pc();

  // We have received a description of where all the java arg are located
  // on entry to the wrapper. We need to convert these args to where
  // the jni function will expect them. To figure out where they go
  // we convert the java signature to a C signature by inserting
  // the hidden arguments as arg[0] and possibly arg[1] (static method)

  const int total_in_args = method->size_of_parameters();
  int total_c_args = total_in_args;
  if (!is_critical_native) {
    total_c_args += 1;
    if (method->is_static()) {
      total_c_args++;
    }
  } else {
    for (int i = 0; i < total_in_args; i++) {
      if (in_sig_bt[i] == T_ARRAY) {
        total_c_args++;
      }
    }
  }

  BasicType* out_sig_bt = NEW_RESOURCE_ARRAY(BasicType, total_c_args);
  VMRegPair* out_regs   = NEW_RESOURCE_ARRAY(VMRegPair, total_c_args);
  BasicType* in_elem_bt = NULL;

  int argc = 0;
  if (!is_critical_native) {
    out_sig_bt[argc++] = T_ADDRESS;
    if (method->is_static()) {
      out_sig_bt[argc++] = T_OBJECT;
    }

    for (int i = 0; i < total_in_args ; i++ ) {
      out_sig_bt[argc++] = in_sig_bt[i];
    }
  } else {
    Thread* THREAD = Thread::current();
    in_elem_bt = NEW_RESOURCE_ARRAY(BasicType, total_in_args);
    SignatureStream ss(method->signature());
    for (int i = 0; i < total_in_args ; i++ ) {
      if (in_sig_bt[i] == T_ARRAY) {
        // Arrays are passed as int, elem* pair
        out_sig_bt[argc++] = T_INT;
        out_sig_bt[argc++] = T_ADDRESS;
        Symbol* atype = ss.as_symbol(CHECK_NULL);
        const char* at = atype->as_C_string();
        if (strlen(at) == 2) {
          assert(at[0] == '[', "must be");
          switch (at[1]) {
            case 'B': in_elem_bt[i]  = T_BYTE; break;
            case 'C': in_elem_bt[i]  = T_CHAR; break;
            case 'D': in_elem_bt[i]  = T_DOUBLE; break;
            case 'F': in_elem_bt[i]  = T_FLOAT; break;
            case 'I': in_elem_bt[i]  = T_INT; break;
            case 'J': in_elem_bt[i]  = T_LONG; break;
            case 'S': in_elem_bt[i]  = T_SHORT; break;
            case 'Z': in_elem_bt[i]  = T_BOOLEAN; break;
            default: ShouldNotReachHere();
          }
        }
      } else {
        out_sig_bt[argc++] = in_sig_bt[i];
        in_elem_bt[i] = T_VOID;
      }
      if (in_sig_bt[i] != T_VOID) {
        assert(in_sig_bt[i] == ss.type(), "must match");
        ss.next();
      }
    }
  }

  // Now figure out where the args must be stored and how much stack space
  // they require.
  int out_arg_slots;
  out_arg_slots = c_calling_convention(out_sig_bt, out_regs, NULL, total_c_args);

  // Compute framesize for the wrapper.  We need to handlize all oops in
  // incoming registers

  // Calculate the total number of stack slots we will need.

  // First count the abi requirement plus all of the outgoing args
  int stack_slots = SharedRuntime::out_preserve_stack_slots() + out_arg_slots;

  // Now the space for the inbound oop handle area
  int total_save_slots = 8 * VMRegImpl::slots_per_word;  // 8 arguments passed in registers
  if (is_critical_native) {
    // Critical natives may have to call out so they need a save area
    // for register arguments.
    int double_slots = 0;
    int single_slots = 0;
    for ( int i = 0; i < total_in_args; i++) {
      if (in_regs[i].first()->is_Register()) {
        const Register reg = in_regs[i].first()->as_Register();
        switch (in_sig_bt[i]) {
          case T_BOOLEAN:
          case T_BYTE:
          case T_SHORT:
          case T_CHAR:
          case T_INT:  single_slots++; break;
          case T_ARRAY:  // specific to LP64 (7145024)
          case T_LONG: double_slots++; break;
          default:  ShouldNotReachHere();
        }
      } else if (in_regs[i].first()->is_FloatRegister()) {
        ShouldNotReachHere();
      }
    }
    total_save_slots = double_slots * 2 + single_slots;
    // align the save area
    if (double_slots != 0) {
      stack_slots = round_to(stack_slots, 2);
    }
  }

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
  // + 4 for return address (which we own) and saved rfp
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
  //      |---------------------| <- oop_handle_offset (8 java arg registers)
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
  stack_slots = round_to(stack_slots, StackAlignmentInSlots);

  int stack_size = stack_slots * VMRegImpl::stack_slot_size;

  // First thing make an ic check to see if we should even be here

  // We are free to use all registers as temps without saving them and
  // restoring them except rfp. rfp is the only callee save register
  // as far as the interpreter and the compiler(s) are concerned.


  const Register ic_reg = rscratch2;
  const Register receiver = j_rarg0;

  Label hit;
  Label exception_pending;

  assert_different_registers(ic_reg, receiver, rscratch1);
  __ verify_oop(receiver);
  __ cmp_klass(receiver, ic_reg, rscratch1);
  __ br(Assembler::EQ, hit);

  __ far_jump(RuntimeAddress(SharedRuntime::get_ic_miss_stub()));

  // Verified entry point must be aligned
  __ align(8);

  __ bind(hit);

  int vep_offset = ((intptr_t)__ pc()) - start;

  // Generate stack overflow check

  // If we have to make this method not-entrant we'll overwrite its
  // first instruction with a jump.  For this action to be legal we
  // must ensure that this first instruction is a B, BL, NOP, BKPT,
  // SVC, HVC, or SMC.  Make it a NOP.
  __ nop();

  if (UseStackBanging) {
    __ bang_stack_with_offset(StackShadowPages*os::vm_page_size());
  } else {
    Unimplemented();
  }

  // Generate a new frame for the wrapper.
  __ enter();
  // -2 because return address is already present and so is saved rfp
  __ sub(sp, sp, stack_size - 2*wordSize);

  // Frame is now completed as far as size and linkage.
  int frame_complete = ((intptr_t)__ pc()) - start;

  // record entry into native wrapper code
  if (NotifySimulator) {
    __ notify(Assembler::method_entry);
  }

  // We use r20 as the oop handle for the receiver/klass
  // It is callee save so it survives the call to native

  const Register oop_handle_reg = r20;

  if (is_critical_native) {
    check_needs_gc_for_critical_native(masm, stack_slots, total_c_args, total_in_args,
                                       oop_handle_offset, oop_maps, in_regs, in_sig_bt);
  }

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

  // Mark location of rfp (someday)
  // map->set_callee_saved(VMRegImpl::stack2reg( stack_slots - 2), stack_slots * 2, 0, vmreg(rfp));


  int float_args = 0;
  int int_args = 0;

#ifdef ASSERT
  bool reg_destroyed[RegisterImpl::number_of_registers];
  bool freg_destroyed[FloatRegisterImpl::number_of_registers];
  for ( int r = 0 ; r < RegisterImpl::number_of_registers ; r++ ) {
    reg_destroyed[r] = false;
  }
  for ( int f = 0 ; f < FloatRegisterImpl::number_of_registers ; f++ ) {
    freg_destroyed[f] = false;
  }

#endif /* ASSERT */

  // This may iterate in two different directions depending on the
  // kind of native it is.  The reason is that for regular JNI natives
  // the incoming and outgoing registers are offset upwards and for
  // critical natives they are offset down.
  GrowableArray<int> arg_order(2 * total_in_args);
  VMRegPair tmp_vmreg;
  tmp_vmreg.set1(r19->as_VMReg());

  if (!is_critical_native) {
    for (int i = total_in_args - 1, c_arg = total_c_args - 1; i >= 0; i--, c_arg--) {
      arg_order.push(i);
      arg_order.push(c_arg);
    }
  } else {
    // Compute a valid move order, using tmp_vmreg to break any cycles
    ComputeMoveOrder cmo(total_in_args, in_regs, total_c_args, out_regs, in_sig_bt, arg_order, tmp_vmreg);
  }

  int temploc = -1;
  for (int ai = 0; ai < arg_order.length(); ai += 2) {
    int i = arg_order.at(ai);
    int c_arg = arg_order.at(ai + 1);
    __ block_comment(err_msg("move %d -> %d", i, c_arg));
    if (c_arg == -1) {
      assert(is_critical_native, "should only be required for critical natives");
      // This arg needs to be moved to a temporary
      __ mov(tmp_vmreg.first()->as_Register(), in_regs[i].first()->as_Register());
      in_regs[i] = tmp_vmreg;
      temploc = i;
      continue;
    } else if (i == -1) {
      assert(is_critical_native, "should only be required for critical natives");
      // Read from the temporary location
      assert(temploc != -1, "must be valid");
      i = temploc;
      temploc = -1;
    }
#ifdef ASSERT
    if (in_regs[i].first()->is_Register()) {
      assert(!reg_destroyed[in_regs[i].first()->as_Register()->encoding()], "destroyed reg!");
    } else if (in_regs[i].first()->is_FloatRegister()) {
      assert(!freg_destroyed[in_regs[i].first()->as_FloatRegister()->encoding()], "destroyed reg!");
    }
    if (out_regs[c_arg].first()->is_Register()) {
      reg_destroyed[out_regs[c_arg].first()->as_Register()->encoding()] = true;
    } else if (out_regs[c_arg].first()->is_FloatRegister()) {
      freg_destroyed[out_regs[c_arg].first()->as_FloatRegister()->encoding()] = true;
    }
#endif /* ASSERT */
    switch (in_sig_bt[i]) {
      case T_ARRAY:
        if (is_critical_native) {
          unpack_array_argument(masm, in_regs[i], in_elem_bt[i], out_regs[c_arg + 1], out_regs[c_arg]);
          c_arg++;
#ifdef ASSERT
          if (out_regs[c_arg].first()->is_Register()) {
            reg_destroyed[out_regs[c_arg].first()->as_Register()->encoding()] = true;
          } else if (out_regs[c_arg].first()->is_FloatRegister()) {
            freg_destroyed[out_regs[c_arg].first()->as_FloatRegister()->encoding()] = true;
          }
#endif
          int_args++;
          break;
        }
      case T_OBJECT:
        assert(!is_critical_native, "no oop arguments");
        object_move(masm, map, oop_handle_offset, stack_slots, in_regs[i], out_regs[c_arg],
                    ((i == 0) && (!is_static)),
                    &receiver_offset);
        int_args++;
        break;
      case T_VOID:
        break;

      case T_FLOAT:
        float_move(masm, in_regs[i], out_regs[c_arg]);
        float_args++;
        break;

      case T_DOUBLE:
        assert( i + 1 < total_in_args &&
                in_sig_bt[i + 1] == T_VOID &&
                out_sig_bt[c_arg+1] == T_VOID, "bad arg list");
        double_move(masm, in_regs[i], out_regs[c_arg]);
        float_args++;
        break;

      case T_LONG :
        long_move(masm, in_regs[i], out_regs[c_arg]);
        int_args++;
        break;

      case T_ADDRESS: assert(false, "found T_ADDRESS in java args");

      default:
        move32_64(masm, in_regs[i], out_regs[c_arg]);
        int_args++;
    }
  }

  // point c_arg at the first arg that is already loaded in case we
  // need to spill before we call out
  int c_arg = total_c_args - total_in_args;

  // Pre-load a static method's oop into r20.  Used both by locking code and
  // the normal JNI call code.
  if (method->is_static() && !is_critical_native) {

    //  load oop into a register
    __ movoop(oop_handle_reg,
              JNIHandles::make_local(method->method_holder()->java_mirror()),
              /*immediate*/true);

    // Now handlize the static class mirror it's known not-null.
    __ str(oop_handle_reg, Address(sp, klass_offset));
    map->set_oop(VMRegImpl::stack2reg(klass_slot_offset));

    // Now get the handle
    __ lea(oop_handle_reg, Address(sp, klass_offset));
    // store the klass handle as second argument
    __ mov(c_rarg1, oop_handle_reg);
    // and protect the arg if we must spill
    c_arg--;
  }

  // Change state to native (we save the return address in the thread, since it might not
  // be pushed on the stack when we do a a stack traversal). It is enough that the pc()
  // points into the right code segment. It does not have to be the correct return pc.
  // We use the same pc/oopMap repeatedly when we call out

  intptr_t the_pc = (intptr_t) __ pc();
  oop_maps->add_gc_map(the_pc - start, map);

  __ set_last_Java_frame(sp, noreg, (address)the_pc, rscratch1);


  // We have all of the arguments setup at this point. We must not touch any register
  // argument registers at this point (what if we save/restore them there are no oop?

  {
    SkipIfEqual skip(masm, &DTraceMethodProbes, false);
    // protect the args we've loaded
    save_args(masm, total_c_args, c_arg, out_regs);
    __ mov_metadata(c_rarg1, method());
    __ call_VM_leaf(
      CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_entry),
      rthread, c_rarg1);
    restore_args(masm, total_c_args, c_arg, out_regs);
  }

  // RedefineClasses() tracing support for obsolete method entry
  if (RC_TRACE_IN_RANGE(0x00001000, 0x00002000)) {
    // protect the args we've loaded
    save_args(masm, total_c_args, c_arg, out_regs);
    __ mov_metadata(c_rarg1, method());
    __ call_VM_leaf(
      CAST_FROM_FN_PTR(address, SharedRuntime::rc_trace_method_entry),
      rthread, c_rarg1);
    restore_args(masm, total_c_args, c_arg, out_regs);
  }

  // Lock a synchronized method

  // Register definitions used by locking and unlocking

  const Register swap_reg = r0;
  const Register obj_reg  = r19;  // Will contain the oop
  const Register lock_reg = r13;  // Address of compiler lock object (BasicLock)
  const Register old_hdr  = r13;  // value of old header at unlock time
  const Register tmp = c_rarg3;

  Label slow_path_lock;
  Label lock_done;

  if (method->is_synchronized()) {
    assert(!is_critical_native, "unhandled");


    const int mark_word_offset = BasicLock::displaced_header_offset_in_bytes();

    // Get the handle (the 2nd argument)
    __ mov(oop_handle_reg, c_rarg1);

    // Get address of the box

    __ lea(lock_reg, Address(sp, lock_slot_offset * VMRegImpl::stack_slot_size));

    // Load the oop from the handle
    __ ldr(obj_reg, Address(oop_handle_reg, 0));

    if (UseBiasedLocking) {
      __ biased_locking_enter(lock_reg, obj_reg, swap_reg, tmp, false, lock_done, &slow_path_lock);
    }

    // Load (object->mark() | 1) into swap_reg %r0
    __ ldr(rscratch1, Address(obj_reg, 0));
    __ orr(swap_reg, rscratch1, 1);

    // Save (object->mark() | 1) into BasicLock's displaced header
    __ str(swap_reg, Address(lock_reg, mark_word_offset));

    // src -> dest iff dest == r0 else r0 <- dest
    { Label here;
      __ cmpxchgptr(r0, lock_reg, obj_reg, rscratch1, lock_done, /*fallthrough*/NULL);
    }

    // Hmm should this move to the slow path code area???

    // Test if the oopMark is an obvious stack pointer, i.e.,
    //  1) (mark & 3) == 0, and
    //  2) sp <= mark < mark + os::pagesize()
    // These 3 tests can be done by evaluating the following
    // expression: ((mark - sp) & (3 - os::vm_page_size())),
    // assuming both stack pointer and pagesize have their
    // least significant 2 bits clear.
    // NOTE: the oopMark is in swap_reg %r0 as the result of cmpxchg

    __ sub(swap_reg, sp, swap_reg);
    __ neg(swap_reg, swap_reg);
    __ ands(swap_reg, swap_reg, 3 - os::vm_page_size());

    // Save the test result, for recursive case, the result is zero
    __ str(swap_reg, Address(lock_reg, mark_word_offset));
    __ br(Assembler::NE, slow_path_lock);

    // Slow path will re-enter here

    __ bind(lock_done);
  }


  // Finally just about ready to make the JNI call


  // get JNIEnv* which is first argument to native
  if (!is_critical_native) {
    __ lea(c_rarg0, Address(rthread, in_bytes(JavaThread::jni_environment_offset())));
  }

  // Now set thread in native
  __ mov(rscratch1, _thread_in_native);
  __ lea(rscratch2, Address(rthread, JavaThread::thread_state_offset()));
  __ stlrw(rscratch1, rscratch2);

  {
    int return_type = 0;
    switch (ret_type) {
    case T_VOID: break;
      return_type = 0; break;
    case T_CHAR:
    case T_BYTE:
    case T_SHORT:
    case T_INT:
    case T_BOOLEAN:
    case T_LONG:
      return_type = 1; break;
    case T_ARRAY:
    case T_OBJECT:
      return_type = 1; break;
    case T_FLOAT:
      return_type = 2; break;
    case T_DOUBLE:
      return_type = 3; break;
    default:
      ShouldNotReachHere();
    }
    rt_call(masm, native_func,
            int_args + 2, // AArch64 passes up to 8 args in int registers
            float_args,   // and up to 8 float args
            return_type);
  }

  // Unpack native results.
  switch (ret_type) {
  case T_BOOLEAN: __ ubfx(r0, r0, 0, 8);            break;
  case T_CHAR   : __ ubfx(r0, r0, 0, 16);            break;
  case T_BYTE   : __ sbfx(r0, r0, 0, 8);            break;
  case T_SHORT  : __ sbfx(r0, r0, 0, 16);            break;
  case T_INT    : __ sbfx(r0, r0, 0, 32);            break;
  case T_DOUBLE :
  case T_FLOAT  :
    // Result is in v0 we'll save as needed
    break;
  case T_ARRAY:                 // Really a handle
  case T_OBJECT:                // Really a handle
      break; // can't de-handlize until after safepoint check
  case T_VOID: break;
  case T_LONG: break;
  default       : ShouldNotReachHere();
  }

  // Switch thread to "native transition" state before reading the synchronization state.
  // This additional state is necessary because reading and testing the synchronization
  // state is not atomic w.r.t. GC, as this scenario demonstrates:
  //     Java thread A, in _thread_in_native state, loads _not_synchronized and is preempted.
  //     VM thread changes sync state to synchronizing and suspends threads for GC.
  //     Thread A is resumed to finish this native method, but doesn't block here since it
  //     didn't see any synchronization is progress, and escapes.
  __ mov(rscratch1, _thread_in_native_trans);
  __ lea(rscratch2, Address(rthread, JavaThread::thread_state_offset()));
  __ stlrw(rscratch1, rscratch2);

  if(os::is_MP()) {
    if (UseMembar) {
      // Force this write out before the read below
      __ dmb(Assembler::SY);
    } else {
      // Write serialization page so VM thread can do a pseudo remote membar.
      // We use the current thread pointer to calculate a thread specific
      // offset to write to within the page. This minimizes bus traffic
      // due to cache line collision.
      __ serialize_memory(rthread, r2);
    }
  }

  Label after_transition;

  // check for safepoint operation in progress and/or pending suspend requests
  {
    Label Continue;

    { unsigned long offset;
      __ adrp(rscratch1,
              ExternalAddress((address)SafepointSynchronize::address_of_state()),
              offset);
      __ ldrw(rscratch1, Address(rscratch1, offset));
    }
    __ cmpw(rscratch1, SafepointSynchronize::_not_synchronized);

    Label L;
    __ br(Assembler::NE, L);
    __ ldrw(rscratch1, Address(rthread, JavaThread::suspend_flags_offset()));
    __ cbz(rscratch1, Continue);
    __ bind(L);

    // Don't use call_VM as it will see a possible pending exception and forward it
    // and never return here preventing us from clearing _last_native_pc down below.
    //
    save_native_result(masm, ret_type, stack_slots);
    __ mov(c_rarg0, rthread);
#ifndef PRODUCT
  assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
#endif
    if (!is_critical_native) {
      __ lea(rscratch1, RuntimeAddress(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans)));
    } else {
      __ lea(rscratch1, RuntimeAddress(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans_and_transition)));
    }
    __ blrt(rscratch1, 1, 0, 1);
    __ maybe_isb();
    // Restore any method result value
    restore_native_result(masm, ret_type, stack_slots);

    if (is_critical_native) {
      // The call above performed the transition to thread_in_Java so
      // skip the transition logic below.
      __ b(after_transition);
    }

    __ bind(Continue);
  }

  // change thread state
  __ mov(rscratch1, _thread_in_Java);
  __ lea(rscratch2, Address(rthread, JavaThread::thread_state_offset()));
  __ stlrw(rscratch1, rscratch2);
  __ bind(after_transition);

  Label reguard;
  Label reguard_done;
  __ ldrb(rscratch1, Address(rthread, JavaThread::stack_guard_state_offset()));
  __ cmpw(rscratch1, JavaThread::stack_guard_yellow_disabled);
  __ br(Assembler::EQ, reguard);
  __ bind(reguard_done);

  // native result if any is live

  // Unlock
  Label unlock_done;
  Label slow_path_unlock;
  if (method->is_synchronized()) {

    // Get locked oop from the handle we passed to jni
    __ ldr(obj_reg, Address(oop_handle_reg, 0));

    Label done;

    if (UseBiasedLocking) {
      __ biased_locking_exit(obj_reg, old_hdr, done);
    }

    // Simple recursive lock?

    __ ldr(rscratch1, Address(sp, lock_slot_offset * VMRegImpl::stack_slot_size));
    __ cbz(rscratch1, done);

    // Must save r0 if if it is live now because cmpxchg must use it
    if (ret_type != T_FLOAT && ret_type != T_DOUBLE && ret_type != T_VOID) {
      save_native_result(masm, ret_type, stack_slots);
    }


    // get address of the stack lock
    __ lea(r0, Address(sp, lock_slot_offset * VMRegImpl::stack_slot_size));
    //  get old displaced header
    __ ldr(old_hdr, Address(r0, 0));

    // Atomic swap old header if oop still contains the stack lock
    Label succeed;
    __ cmpxchgptr(r0, old_hdr, obj_reg, rscratch1, succeed, &slow_path_unlock);
    __ bind(succeed);

    // slow path re-enters here
    __ bind(unlock_done);
    if (ret_type != T_FLOAT && ret_type != T_DOUBLE && ret_type != T_VOID) {
      restore_native_result(masm, ret_type, stack_slots);
    }

    __ bind(done);

  }
  {
    SkipIfEqual skip(masm, &DTraceMethodProbes, false);
    save_native_result(masm, ret_type, stack_slots);
    __ mov_metadata(c_rarg1, method());
    __ call_VM_leaf(
         CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_exit),
         rthread, c_rarg1);
    restore_native_result(masm, ret_type, stack_slots);
  }

  __ reset_last_Java_frame(false, true);

  // Unpack oop result
  if (ret_type == T_OBJECT || ret_type == T_ARRAY) {
      Label L;
      __ cbz(r0, L);
      __ ldr(r0, Address(r0, 0));
      __ bind(L);
      __ verify_oop(r0);
  }

  if (!is_critical_native) {
    // reset handle block
    __ ldr(r2, Address(rthread, JavaThread::active_handles_offset()));
    __ str(zr, Address(r2, JNIHandleBlock::top_offset_in_bytes()));
  }

  __ leave();

  if (!is_critical_native) {
    // Any exception pending?
    __ ldr(rscratch1, Address(rthread, in_bytes(Thread::pending_exception_offset())));
    __ cbnz(rscratch1, exception_pending);
  }

  // record exit from native wrapper code
  if (NotifySimulator) {
    __ notify(Assembler::method_reentry);
  }

  // We're done
  __ ret(lr);

  // Unexpected paths are out of line and go here

  if (!is_critical_native) {
    // forward the exception
    __ bind(exception_pending);

    // and forward the exception
    __ far_jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
  }

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
    __ mov(c_rarg2, rthread);

    // Not a leaf but we have last_Java_frame setup as we want
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::complete_monitor_locking_C), 3);
    restore_args(masm, total_c_args, c_arg, out_regs);

#ifdef ASSERT
    { Label L;
      __ ldr(rscratch1, Address(rthread, in_bytes(Thread::pending_exception_offset())));
      __ cbz(rscratch1, L);
      __ stop("no pending exception allowed on exit from monitorenter");
      __ bind(L);
    }
#endif
    __ b(lock_done);

    // END Slow path lock

    // BEGIN Slow path unlock
    __ bind(slow_path_unlock);

    // If we haven't already saved the native result we must save it now as xmm registers
    // are still exposed.

    if (ret_type == T_FLOAT || ret_type == T_DOUBLE ) {
      save_native_result(masm, ret_type, stack_slots);
    }

    __ mov(c_rarg2, rthread);
    __ lea(c_rarg1, Address(sp, lock_slot_offset * VMRegImpl::stack_slot_size));
    __ mov(c_rarg0, obj_reg);

    // Save pending exception around call to VM (which contains an EXCEPTION_MARK)
    // NOTE that obj_reg == r19 currently
    __ ldr(r19, Address(rthread, in_bytes(Thread::pending_exception_offset())));
    __ str(zr, Address(rthread, in_bytes(Thread::pending_exception_offset())));

    rt_call(masm, CAST_FROM_FN_PTR(address, SharedRuntime::complete_monitor_unlocking_C), 3, 0, 1);

#ifdef ASSERT
    {
      Label L;
      __ ldr(rscratch1, Address(rthread, in_bytes(Thread::pending_exception_offset())));
      __ cbz(rscratch1, L);
      __ stop("no pending exception allowed on exit complete_monitor_unlocking_C");
      __ bind(L);
    }
#endif /* ASSERT */

    __ str(r19, Address(rthread, in_bytes(Thread::pending_exception_offset())));

    if (ret_type == T_FLOAT || ret_type == T_DOUBLE ) {
      restore_native_result(masm, ret_type, stack_slots);
    }
    __ b(unlock_done);

    // END Slow path unlock

  } // synchronized

  // SLOW PATH Reguard the stack if needed

  __ bind(reguard);
  save_native_result(masm, ret_type, stack_slots);
  rt_call(masm, CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages), 0, 0, 0);
  restore_native_result(masm, ret_type, stack_slots);
  // and continue
  __ b(reguard_done);



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

  if (is_critical_native) {
    nm->set_lazy_critical_native(true);
  }

  return nm;

}

// this function returns the adjust size (in number of words) to a c2i adapter
// activation for use during deoptimization
int Deoptimization::last_frame_adjust(int callee_parameters, int callee_locals) {
  assert(callee_locals >= callee_parameters,
          "test and remove; got more parms than locals");
  if (callee_locals < callee_parameters)
    return 0;                   // No adjustment for negative locals
  int diff = (callee_locals - callee_parameters) * Interpreter::stackElementWords;
  // diff is counted in stack words
  return round_to(diff, 2);
}


//------------------------------generate_deopt_blob----------------------------
void SharedRuntime::generate_deopt_blob() {
  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  CodeBuffer buffer("deopt_blob", 2048, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);
  int frame_size_in_words;
  OopMap* map = NULL;
  OopMapSet *oop_maps = new OopMapSet();

#ifdef BUILTIN_SIM
  AArch64Simulator *simulator;
  if (NotifySimulator) {
    simulator = AArch64Simulator::get_current(UseSimulatorCache, DisableBCCheck);
    simulator->notifyCompile(const_cast<char*>("SharedRuntime::deopt_blob"), __ pc());
  }
#endif

  // -------------
  // This code enters when returning to a de-optimized nmethod.  A return
  // address has been pushed on the the stack, and return values are in
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
  //    r0: exception oop
  //    r19: exception handler
  //    r3: throwing pc
  // So in this case we simply jam r3 into the useless return address and
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
  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words);

  // Normal deoptimization.  Save exec mode for unpack_frames.
  __ movw(rcpool, Deoptimization::Unpack_deopt); // callee-saved
  __ b(cont);

  int reexecute_offset = __ pc() - start;

  // Reexecute case
  // return address is the pc describes what bci to do re-execute at

  // No need to update map as each call to save_live_registers will produce identical oopmap
  (void) RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words);

  __ movw(rcpool, Deoptimization::Unpack_reexecute); // callee-saved
  __ b(cont);

  int exception_offset = __ pc() - start;

  // Prolog for exception case

  // all registers are dead at this entry point, except for r0, and
  // r3 which contain the exception oop and exception pc
  // respectively.  Set them in TLS and fall thru to the
  // unpack_with_exception_in_tls entry point.

  __ str(r3, Address(rthread, JavaThread::exception_pc_offset()));
  __ str(r0, Address(rthread, JavaThread::exception_oop_offset()));

  int exception_in_tls_offset = __ pc() - start;

  // new implementation because exception oop is now passed in JavaThread

  // Prolog for exception case
  // All registers must be preserved because they might be used by LinearScan
  // Exceptiop oop and throwing PC are passed in JavaThread
  // tos: stack at point of call to method that threw the exception (i.e. only
  // args are on the stack, no return address)

  // The return address pushed by save_live_registers will be patched
  // later with the throwing pc. The correct value is not available
  // now because loading it from memory would destroy registers.

  // NB: The SP at this point must be the SP of the method that is
  // being deoptimized.  Deoptimization assumes that the frame created
  // here by save_live_registers is immediately below the method's SP.
  // This is a somewhat fragile mechanism.

  // Save everything in sight.
  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words);

  // Now it is safe to overwrite any register

  // Deopt during an exception.  Save exec mode for unpack_frames.
  __ mov(rcpool, Deoptimization::Unpack_exception); // callee-saved

  // load throwing pc from JavaThread and patch it as the return address
  // of the current frame. Then clear the field in JavaThread

  __ ldr(r3, Address(rthread, JavaThread::exception_pc_offset()));
  __ str(r3, Address(rfp, wordSize));
  __ str(zr, Address(rthread, JavaThread::exception_pc_offset()));

#ifdef ASSERT
  // verify that there is really an exception oop in JavaThread
  __ ldr(r0, Address(rthread, JavaThread::exception_oop_offset()));
  __ verify_oop(r0);

  // verify that there is no pending exception
  Label no_pending_exception;
  __ ldr(rscratch1, Address(rthread, Thread::pending_exception_offset()));
  __ cbz(rscratch1, no_pending_exception);
  __ stop("must not have pending exception here");
  __ bind(no_pending_exception);
#endif

  __ bind(cont);

  // Call C code.  Need thread and this frame, but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.
  //
  // UnrollBlock* fetch_unroll_info(JavaThread* thread)

  // fetch_unroll_info needs to call last_java_frame().

  Label retaddr;
  __ set_last_Java_frame(sp, noreg, retaddr, rscratch1);
#ifdef ASSERT0
  { Label L;
    __ ldr(rscratch1, Address(rthread,
                              JavaThread::last_Java_fp_offset()));
    __ cbz(rscratch1, L);
    __ stop("SharedRuntime::generate_deopt_blob: last_Java_fp not cleared");
    __ bind(L);
  }
#endif // ASSERT
  __ mov(c_rarg0, rthread);
  __ lea(rscratch1, RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::fetch_unroll_info)));
  __ blrt(rscratch1, 1, 0, 1);
  __ bind(retaddr);

  // Need to have an oopmap that tells fetch_unroll_info where to
  // find any register it might need.
  oop_maps->add_gc_map(__ pc() - start, map);

  __ reset_last_Java_frame(false, true);

  // Load UnrollBlock* into rdi
  __ mov(r5, r0);

   Label noException;
  __ cmpw(rcpool, Deoptimization::Unpack_exception);   // Was exception pending?
  __ br(Assembler::NE, noException);
  __ ldr(r0, Address(rthread, JavaThread::exception_oop_offset()));
  // QQQ this is useless it was NULL above
  __ ldr(r3, Address(rthread, JavaThread::exception_pc_offset()));
  __ str(zr, Address(rthread, JavaThread::exception_oop_offset()));
  __ str(zr, Address(rthread, JavaThread::exception_pc_offset()));

  __ verify_oop(r0);

  // Overwrite the result registers with the exception results.
  __ str(r0, Address(sp, RegisterSaver::r0_offset_in_bytes()));
  // I think this is useless
  // __ str(r3, Address(sp, RegisterSaver::r3_offset_in_bytes()));

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
  __ ldrw(r2, Address(r5, Deoptimization::UnrollBlock::size_of_deoptimized_frame_offset_in_bytes()));
  __ sub(r2, r2, 2 * wordSize);
  __ add(sp, sp, r2);
  __ ldp(rfp, lr, __ post(sp, 2 * wordSize));
  // LR should now be the return address to the caller (3)

#ifdef ASSERT
  // Compilers generate code that bang the stack by as much as the
  // interpreter would need. So this stack banging should never
  // trigger a fault. Verify that it does not on non product builds.
  if (UseStackBanging) {
    __ ldrw(r19, Address(r5, Deoptimization::UnrollBlock::total_frame_sizes_offset_in_bytes()));
    __ bang_stack_size(r19, r2);
  }
#endif
  // Load address of array of frame pcs into r2
  __ ldr(r2, Address(r5, Deoptimization::UnrollBlock::frame_pcs_offset_in_bytes()));

  // Trash the old pc
  // __ addptr(sp, wordSize);  FIXME ????

  // Load address of array of frame sizes into r4
  __ ldr(r4, Address(r5, Deoptimization::UnrollBlock::frame_sizes_offset_in_bytes()));

  // Load counter into r3
  __ ldrw(r3, Address(r5, Deoptimization::UnrollBlock::number_of_frames_offset_in_bytes()));

  // Now adjust the caller's stack to make up for the extra locals
  // but record the original sp so that we can save it in the skeletal interpreter
  // frame and the stack walking of interpreter_sender will get the unextended sp
  // value and not the "real" sp value.

  const Register sender_sp = r6;

  __ mov(sender_sp, sp);
  __ ldrw(r19, Address(r5,
                       Deoptimization::UnrollBlock::
                       caller_adjustment_offset_in_bytes()));
  __ sub(sp, sp, r19);

  // Push interpreter frames in a loop
  __ mov(rscratch1, (address)0xDEADDEAD);        // Make a recognizable pattern
  __ mov(rscratch2, rscratch1);
  Label loop;
  __ bind(loop);
  __ ldr(r19, Address(__ post(r4, wordSize)));          // Load frame size
  __ sub(r19, r19, 2*wordSize);           // We'll push pc and fp by hand
  __ ldr(lr, Address(__ post(r2, wordSize)));  // Load pc
  __ enter();                           // Save old & set new fp
  __ sub(sp, sp, r19);                  // Prolog
  // This value is corrected by layout_activation_impl
  __ str(zr, Address(rfp, frame::interpreter_frame_last_sp_offset * wordSize));
  __ str(sender_sp, Address(rfp, frame::interpreter_frame_sender_sp_offset * wordSize)); // Make it walkable
  __ mov(sender_sp, sp);               // Pass sender_sp to next frame
  __ sub(r3, r3, 1);                   // Decrement counter
  __ cbnz(r3, loop);

    // Re-push self-frame
  __ ldr(lr, Address(r2));
  __ enter();

  // Allocate a full sized register save area.  We subtract 2 because
  // enter() just pushed 2 words
  __ sub(sp, sp, (frame_size_in_words - 2) * wordSize);

  // Restore frame locals after moving the frame
  __ strd(v0, Address(sp, RegisterSaver::v0_offset_in_bytes()));
  __ str(r0, Address(sp, RegisterSaver::r0_offset_in_bytes()));

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // restore return values to their stack-slots with the new SP.
  //
  // void Deoptimization::unpack_frames(JavaThread* thread, int exec_mode)

  // Use rfp because the frames look interpreted now
  // Don't need the precise return PC here, just precise enough to point into this code blob.
  address the_pc = __ pc();
  __ set_last_Java_frame(sp, rfp, the_pc, rscratch1);

  __ mov(c_rarg0, rthread);
  __ movw(c_rarg1, rcpool); // second arg: exec_mode
  __ lea(rscratch1, RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames)));
  __ blrt(rscratch1, 2, 0, 0);

  // Set an oopmap for the call site
  // Use the same PC we used for the last java frame
  oop_maps->add_gc_map(the_pc - start,
                       new OopMap( frame_size_in_words, 0 ));

  // Clear fp AND pc
  __ reset_last_Java_frame(true, true);

  // Collect return values
  __ ldrd(v0, Address(sp, RegisterSaver::v0_offset_in_bytes()));
  __ ldr(r0, Address(sp, RegisterSaver::r0_offset_in_bytes()));
  // I think this is useless (throwing pc?)
  // __ ldr(r3, Address(sp, RegisterSaver::r3_offset_in_bytes()));

  // Pop self-frame.
  __ leave();                           // Epilog

  // Jump to interpreter
  __ ret(lr);

  // Make sure all code is generated
  masm->flush();

  _deopt_blob = DeoptimizationBlob::create(&buffer, oop_maps, 0, exception_offset, reexecute_offset, frame_size_in_words);
  _deopt_blob->set_unpack_with_exception_in_tls_offset(exception_in_tls_offset);

#ifdef BUILTIN_SIM
  if (NotifySimulator) {
    unsigned char *base = _deopt_blob->code_begin();
    simulator->notifyRelocate(start, base - start);
  }
#endif
}

uint SharedRuntime::out_preserve_stack_slots() {
  return 0;
}

#ifdef COMPILER2
//------------------------------generate_uncommon_trap_blob--------------------
void SharedRuntime::generate_uncommon_trap_blob() {
  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  CodeBuffer buffer("uncommon_trap_blob", 2048, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);

#ifdef BUILTIN_SIM
  AArch64Simulator *simulator;
  if (NotifySimulator) {
    simulator = AArch64Simulator::get_current(UseSimulatorCache, DisableBCCheck);
    simulator->notifyCompile(const_cast<char*>("SharedRuntime:uncommon_trap_blob"), __ pc());
  }
#endif

  assert(SimpleRuntimeFrame::framesize % 4 == 0, "sp not 16-byte aligned");

  address start = __ pc();

  // Push self-frame.  We get here with a return address in LR
  // and sp should be 16 byte aligned
  // push rfp and retaddr by hand
  __ stp(rfp, lr, Address(__ pre(sp, -2 * wordSize)));
  // we don't expect an arg reg save area
#ifndef PRODUCT
  assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
#endif
  // compiler left unloaded_class_index in j_rarg0 move to where the
  // runtime expects it.
  if (c_rarg1 != j_rarg0) {
    __ movw(c_rarg1, j_rarg0);
  }

  // we need to set the past SP to the stack pointer of the stub frame
  // and the pc to the address where this runtime call will return
  // although actually any pc in this code blob will do).
  Label retaddr;
  __ set_last_Java_frame(sp, noreg, retaddr, rscratch1);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // capture callee-saved registers as well as return values.
  // Thread is in rdi already.
  //
  // UnrollBlock* uncommon_trap(JavaThread* thread, jint unloaded_class_index);
  //
  // n.b. 2 gp args, 0 fp args, integral return type

  __ mov(c_rarg0, rthread);
  __ lea(rscratch1,
         RuntimeAddress(CAST_FROM_FN_PTR(address,
                                         Deoptimization::uncommon_trap)));
  __ blrt(rscratch1, 2, 0, MacroAssembler::ret_type_integral);
  __ bind(retaddr);

  // Set an oopmap for the call site
  OopMapSet* oop_maps = new OopMapSet();
  OopMap* map = new OopMap(SimpleRuntimeFrame::framesize, 0);

  // location of rfp is known implicitly by the frame sender code

  oop_maps->add_gc_map(__ pc() - start, map);

  __ reset_last_Java_frame(false, true);

  // move UnrollBlock* into r4
  __ mov(r4, r0);

  // Pop all the frames we must move/replace.
  //
  // Frame picture (youngest to oldest)
  // 1: self-frame (no frame link)
  // 2: deopting frame  (no frame link)
  // 3: caller of deopting frame (could be compiled/interpreted).

  // Pop self-frame.  We have no frame, and must rely only on r0 and sp.
  __ add(sp, sp, (SimpleRuntimeFrame::framesize) << LogBytesPerInt); // Epilog!

  // Pop deoptimized frame (int)
  __ ldrw(r2, Address(r4,
                      Deoptimization::UnrollBlock::
                      size_of_deoptimized_frame_offset_in_bytes()));
  __ sub(r2, r2, 2 * wordSize);
  __ add(sp, sp, r2);
  __ ldp(rfp, lr, __ post(sp, 2 * wordSize));
  // LR should now be the return address to the caller (3) frame

#ifdef ASSERT
  // Compilers generate code that bang the stack by as much as the
  // interpreter would need. So this stack banging should never
  // trigger a fault. Verify that it does not on non product builds.
  if (UseStackBanging) {
    __ ldrw(r1, Address(r4,
                        Deoptimization::UnrollBlock::
                        total_frame_sizes_offset_in_bytes()));
    __ bang_stack_size(r1, r2);
  }
#endif

  // Load address of array of frame pcs into r2 (address*)
  __ ldr(r2, Address(r4,
                     Deoptimization::UnrollBlock::frame_pcs_offset_in_bytes()));

  // Load address of array of frame sizes into r5 (intptr_t*)
  __ ldr(r5, Address(r4,
                     Deoptimization::UnrollBlock::
                     frame_sizes_offset_in_bytes()));

  // Counter
  __ ldrw(r3, Address(r4,
                      Deoptimization::UnrollBlock::
                      number_of_frames_offset_in_bytes())); // (int)

  // Now adjust the caller's stack to make up for the extra locals but
  // record the original sp so that we can save it in the skeletal
  // interpreter frame and the stack walking of interpreter_sender
  // will get the unextended sp value and not the "real" sp value.

  const Register sender_sp = r8;

  __ mov(sender_sp, sp);
  __ ldrw(r1, Address(r4,
                      Deoptimization::UnrollBlock::
                      caller_adjustment_offset_in_bytes())); // (int)
  __ sub(sp, sp, r1);

  // Push interpreter frames in a loop
  Label loop;
  __ bind(loop);
  __ ldr(r1, Address(r5, 0));       // Load frame size
  __ sub(r1, r1, 2 * wordSize);     // We'll push pc and rfp by hand
  __ ldr(lr, Address(r2, 0));       // Save return address
  __ enter();                       // and old rfp & set new rfp
  __ sub(sp, sp, r1);               // Prolog
  __ str(sender_sp, Address(rfp, frame::interpreter_frame_sender_sp_offset * wordSize)); // Make it walkable
  // This value is corrected by layout_activation_impl
  __ str(zr, Address(rfp, frame::interpreter_frame_last_sp_offset * wordSize));
  __ mov(sender_sp, sp);          // Pass sender_sp to next frame
  __ add(r5, r5, wordSize);       // Bump array pointer (sizes)
  __ add(r2, r2, wordSize);       // Bump array pointer (pcs)
  __ subsw(r3, r3, 1);            // Decrement counter
  __ br(Assembler::GT, loop);
  __ ldr(lr, Address(r2, 0));     // save final return address
  // Re-push self-frame
  __ enter();                     // & old rfp & set new rfp

  // Use rfp because the frames look interpreted now
  // Save "the_pc" since it cannot easily be retrieved using the last_java_SP after we aligned SP.
  // Don't need the precise return PC here, just precise enough to point into this code blob.
  address the_pc = __ pc();
  __ set_last_Java_frame(sp, rfp, the_pc, rscratch1);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // restore return values to their stack-slots with the new SP.
  // Thread is in rdi already.
  //
  // BasicType unpack_frames(JavaThread* thread, int exec_mode);
  //
  // n.b. 2 gp args, 0 fp args, integral return type

  // sp should already be aligned
  __ mov(c_rarg0, rthread);
  __ movw(c_rarg1, (unsigned)Deoptimization::Unpack_uncommon_trap);
  __ lea(rscratch1, RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames)));
  __ blrt(rscratch1, 2, 0, MacroAssembler::ret_type_integral);

  // Set an oopmap for the call site
  // Use the same PC we used for the last java frame
  oop_maps->add_gc_map(the_pc - start, new OopMap(SimpleRuntimeFrame::framesize, 0));

  // Clear fp AND pc
  __ reset_last_Java_frame(true, true);

  // Pop self-frame.
  __ leave();                 // Epilog

  // Jump to interpreter
  __ ret(lr);

  // Make sure all code is generated
  masm->flush();

  _uncommon_trap_blob =  UncommonTrapBlob::create(&buffer, oop_maps,
                                                 SimpleRuntimeFrame::framesize >> 1);

#ifdef BUILTIN_SIM
  if (NotifySimulator) {
    unsigned char *base = _deopt_blob->code_begin();
    simulator->notifyRelocate(start, base - start);
  }
#endif
}
#endif // COMPILER2


//------------------------------generate_handler_blob------
//
// Generate a special Compile2Runtime blob that saves all registers,
// and setup oopmap.
//
SafepointBlob* SharedRuntime::generate_handler_blob(address call_ptr, int poll_type) {
  ResourceMark rm;
  OopMapSet *oop_maps = new OopMapSet();
  OopMap* map;

  // Allocate space for the code.  Setup code generation tools.
  CodeBuffer buffer("handler_blob", 2048, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);

  address start   = __ pc();
  address call_pc = NULL;
  int frame_size_in_words;
  bool cause_return = (poll_type == POLL_AT_RETURN);
  bool save_vectors = (poll_type == POLL_AT_VECTOR_LOOP);

  // Save registers, fpu state, and flags
  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words);

  // The following is basically a call_VM.  However, we need the precise
  // address of the call in order to generate an oopmap. Hence, we do all the
  // work outselves.

  Label retaddr;
  __ set_last_Java_frame(sp, noreg, retaddr, rscratch1);

  // The return address must always be correct so that frame constructor never
  // sees an invalid pc.

  if (!cause_return) {
    // overwrite the return address pushed by save_live_registers
    __ ldr(c_rarg0, Address(rthread, JavaThread::saved_exception_pc_offset()));
    __ str(c_rarg0, Address(rfp, wordSize));
  }

  // Do the call
  __ mov(c_rarg0, rthread);
  __ lea(rscratch1, RuntimeAddress(call_ptr));
  __ blrt(rscratch1, 1, 0, 1);
  __ bind(retaddr);

  // Set an oopmap for the call site.  This oopmap will map all
  // oop-registers and debug-info registers as callee-saved.  This
  // will allow deoptimization at this safepoint to find all possible
  // debug-info recordings, as well as let GC find all oops.

  oop_maps->add_gc_map( __ pc() - start, map);

  Label noException;

  __ reset_last_Java_frame(false, true);

  __ maybe_isb();
  __ membar(Assembler::LoadLoad | Assembler::LoadStore);

  __ ldr(rscratch1, Address(rthread, Thread::pending_exception_offset()));
  __ cbz(rscratch1, noException);

  // Exception pending

  RegisterSaver::restore_live_registers(masm);

  __ far_jump(RuntimeAddress(StubRoutines::forward_exception_entry()));

  // No exception case
  __ bind(noException);

  // Normal exit, restore registers and exit.
  RegisterSaver::restore_live_registers(masm);

  __ ret(lr);

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
  assert (StubRoutines::forward_exception_entry() != NULL, "must be generated before");

  // allocate space for the code
  ResourceMark rm;

  CodeBuffer buffer(name, 1000, 512);
  MacroAssembler* masm                = new MacroAssembler(&buffer);

  int frame_size_in_words;

  OopMapSet *oop_maps = new OopMapSet();
  OopMap* map = NULL;

  int start = __ offset();

  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_in_words);

  int frame_complete = __ offset();

  {
    Label retaddr;
    __ set_last_Java_frame(sp, noreg, retaddr, rscratch1);

    __ mov(c_rarg0, rthread);
    __ lea(rscratch1, RuntimeAddress(destination));

    __ blrt(rscratch1, 1, 0, 1);
    __ bind(retaddr);
  }

  // Set an oopmap for the call site.
  // We need this not only for callee-saved registers, but also for volatile
  // registers that the compiler might be keeping live across a safepoint.

  oop_maps->add_gc_map( __ offset() - start, map);

  __ maybe_isb();

  // r0 contains the address we are going to jump to assuming no exception got installed

  // clear last_Java_sp
  __ reset_last_Java_frame(false, true);
  // check for pending exceptions
  Label pending;
  __ ldr(rscratch1, Address(rthread, Thread::pending_exception_offset()));
  __ cbnz(rscratch1, pending);

  // get the returned Method*
  __ get_vm_result_2(rmethod, rthread);
  __ str(rmethod, Address(sp, RegisterSaver::reg_offset_in_bytes(rmethod)));

  // r0 is where we want to jump, overwrite rscratch1 which is saved and scratch
  __ str(r0, Address(sp, RegisterSaver::rscratch1_offset_in_bytes()));
  RegisterSaver::restore_live_registers(masm);

  // We are back the the original state on entry and ready to go.

  __ br(rscratch1);

  // Pending exception after the safepoint

  __ bind(pending);

  RegisterSaver::restore_live_registers(masm);

  // exception pending => remove activation and forward to exception handler

  __ str(zr, Address(rthread, JavaThread::vm_result_offset()));

  __ ldr(r0, Address(rthread, Thread::pending_exception_offset()));
  __ far_jump(RuntimeAddress(StubRoutines::forward_exception_entry()));

  // -------------
  // make sure all code is generated
  masm->flush();

  // return the  blob
  // frame_size_words or bytes??
  return RuntimeStub::new_runtime_stub(name, &buffer, frame_complete, frame_size_in_words, oop_maps, true);
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
//   r0: exception oop
//   r3: exception pc
//
// Results:
//   r0: exception oop
//   r3: exception pc in caller or ???
//   destination: exception handler of caller
//
// Note: the exception pc MUST be at a call (precise debug information)
//       Registers r0, r3, r2, r4, r5, r8-r11 are not callee saved.
//

void OptoRuntime::generate_exception_blob() {
  assert(!OptoRuntime::is_callee_saved_register(R3_num), "");
  assert(!OptoRuntime::is_callee_saved_register(R0_num), "");
  assert(!OptoRuntime::is_callee_saved_register(R2_num), "");

  assert(SimpleRuntimeFrame::framesize % 4 == 0, "sp not 16-byte aligned");

  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  CodeBuffer buffer("exception_blob", 2048, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);

  // TODO check various assumptions made here
  //
  // make sure we do so before running this

  address start = __ pc();

  // push rfp and retaddr by hand
  // Exception pc is 'return address' for stack walker
  __ stp(rfp, lr, Address(__ pre(sp, -2 * wordSize)));
  // there are no callee save registers and we don't expect an
  // arg reg save area
#ifndef PRODUCT
  assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
#endif
  // Store exception in Thread object. We cannot pass any arguments to the
  // handle_exception call, since we do not want to make any assumption
  // about the size of the frame where the exception happened in.
  __ str(r0, Address(rthread, JavaThread::exception_oop_offset()));
  __ str(r3, Address(rthread, JavaThread::exception_pc_offset()));

  // This call does all the hard work.  It checks if an exception handler
  // exists in the method.
  // If so, it returns the handler address.
  // If not, it prepares for stack-unwinding, restoring the callee-save
  // registers of the frame being removed.
  //
  // address OptoRuntime::handle_exception_C(JavaThread* thread)
  //
  // n.b. 1 gp arg, 0 fp args, integral return type

  // the stack should always be aligned
  address the_pc = __ pc();
  __ set_last_Java_frame(sp, noreg, the_pc, rscratch1);
  __ mov(c_rarg0, rthread);
  __ lea(rscratch1, RuntimeAddress(CAST_FROM_FN_PTR(address, OptoRuntime::handle_exception_C)));
  __ blrt(rscratch1, 1, 0, MacroAssembler::ret_type_integral);
  __ maybe_isb();

  // Set an oopmap for the call site.  This oopmap will only be used if we
  // are unwinding the stack.  Hence, all locations will be dead.
  // Callee-saved registers will be the same as the frame above (i.e.,
  // handle_exception_stub), since they were restored when we got the
  // exception.

  OopMapSet* oop_maps = new OopMapSet();

  oop_maps->add_gc_map(the_pc - start, new OopMap(SimpleRuntimeFrame::framesize, 0));

  __ reset_last_Java_frame(false, true);

  // Restore callee-saved registers

  // rfp is an implicitly saved callee saved register (i.e. the calling
  // convention will save restore it in prolog/epilog) Other than that
  // there are no callee save registers now that adapter frames are gone.
  // and we dont' expect an arg reg save area
  __ ldp(rfp, r3, Address(__ post(sp, 2 * wordSize)));

  // r0: exception handler

  // We have a handler in r0 (could be deopt blob).
  __ mov(r8, r0);

  // Get the exception oop
  __ ldr(r0, Address(rthread, JavaThread::exception_oop_offset()));
  // Get the exception pc in case we are deoptimized
  __ ldr(r4, Address(rthread, JavaThread::exception_pc_offset()));
#ifdef ASSERT
  __ str(zr, Address(rthread, JavaThread::exception_handler_pc_offset()));
  __ str(zr, Address(rthread, JavaThread::exception_pc_offset()));
#endif
  // Clear the exception oop so GC no longer processes it as a root.
  __ str(zr, Address(rthread, JavaThread::exception_oop_offset()));

  // r0: exception oop
  // r8:  exception handler
  // r4: exception pc
  // Jump to handler

  __ br(r8);

  // Make sure all code is generated
  masm->flush();

  // Set exception blob
  _exception_blob =  ExceptionBlob::create(&buffer, oop_maps, SimpleRuntimeFrame::framesize >> 1);
}
#endif // COMPILER2
