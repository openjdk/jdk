/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_sharedRuntime_sparc.cpp.incl"

#define __ masm->

#ifdef COMPILER2
UncommonTrapBlob*   SharedRuntime::_uncommon_trap_blob;
#endif // COMPILER2

DeoptimizationBlob* SharedRuntime::_deopt_blob;
SafepointBlob*      SharedRuntime::_polling_page_safepoint_handler_blob;
SafepointBlob*      SharedRuntime::_polling_page_return_handler_blob;
RuntimeStub*        SharedRuntime::_wrong_method_blob;
RuntimeStub*        SharedRuntime::_ic_miss_blob;
RuntimeStub*        SharedRuntime::_resolve_opt_virtual_call_blob;
RuntimeStub*        SharedRuntime::_resolve_virtual_call_blob;
RuntimeStub*        SharedRuntime::_resolve_static_call_blob;

class RegisterSaver {

  // Used for saving volatile registers. This is Gregs, Fregs, I/L/O.
  // The Oregs are problematic. In the 32bit build the compiler can
  // have O registers live with 64 bit quantities. A window save will
  // cut the heads off of the registers. We have to do a very extensive
  // stack dance to save and restore these properly.

  // Note that the Oregs problem only exists if we block at either a polling
  // page exception a compiled code safepoint that was not originally a call
  // or deoptimize following one of these kinds of safepoints.

  // Lots of registers to save.  For all builds, a window save will preserve
  // the %i and %l registers.  For the 32-bit longs-in-two entries and 64-bit
  // builds a window-save will preserve the %o registers.  In the LION build
  // we need to save the 64-bit %o registers which requires we save them
  // before the window-save (as then they become %i registers and get their
  // heads chopped off on interrupt).  We have to save some %g registers here
  // as well.
  enum {
    // This frame's save area.  Includes extra space for the native call:
    // vararg's layout space and the like.  Briefly holds the caller's
    // register save area.
    call_args_area = frame::register_save_words_sp_offset +
                     frame::memory_parameter_word_sp_offset*wordSize,
    // Make sure save locations are always 8 byte aligned.
    // can't use round_to because it doesn't produce compile time constant
    start_of_extra_save_area = ((call_args_area + 7) & ~7),
    g1_offset = start_of_extra_save_area, // g-regs needing saving
    g3_offset = g1_offset+8,
    g4_offset = g3_offset+8,
    g5_offset = g4_offset+8,
    o0_offset = g5_offset+8,
    o1_offset = o0_offset+8,
    o2_offset = o1_offset+8,
    o3_offset = o2_offset+8,
    o4_offset = o3_offset+8,
    o5_offset = o4_offset+8,
    start_of_flags_save_area = o5_offset+8,
    ccr_offset = start_of_flags_save_area,
    fsr_offset = ccr_offset + 8,
    d00_offset = fsr_offset+8,  // Start of float save area
    register_save_size = d00_offset+8*32
  };


  public:

  static int Oexception_offset() { return o0_offset; };
  static int G3_offset() { return g3_offset; };
  static int G5_offset() { return g5_offset; };
  static OopMap* save_live_registers(MacroAssembler* masm, int additional_frame_words, int* total_frame_words);
  static void restore_live_registers(MacroAssembler* masm);

  // During deoptimization only the result register need to be restored
  // all the other values have already been extracted.

  static void restore_result_registers(MacroAssembler* masm);
};

OopMap* RegisterSaver::save_live_registers(MacroAssembler* masm, int additional_frame_words, int* total_frame_words) {
  // Record volatile registers as callee-save values in an OopMap so their save locations will be
  // propagated to the caller frame's RegisterMap during StackFrameStream construction (needed for
  // deoptimization; see compiledVFrame::create_stack_value).  The caller's I, L and O registers
  // are saved in register windows - I's and L's in the caller's frame and O's in the stub frame
  // (as the stub's I's) when the runtime routine called by the stub creates its frame.
  int i;
  // Always make the frame size 16 byte aligned.
  int frame_size = round_to(additional_frame_words + register_save_size, 16);
  // OopMap frame size is in c2 stack slots (sizeof(jint)) not bytes or words
  int frame_size_in_slots = frame_size / sizeof(jint);
  // CodeBlob frame size is in words.
  *total_frame_words = frame_size / wordSize;
  // OopMap* map = new OopMap(*total_frame_words, 0);
  OopMap* map = new OopMap(frame_size_in_slots, 0);

#if !defined(_LP64)

  // Save 64-bit O registers; they will get their heads chopped off on a 'save'.
  __ stx(O0, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+0*8);
  __ stx(O1, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+1*8);
  __ stx(O2, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+2*8);
  __ stx(O3, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+3*8);
  __ stx(O4, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+4*8);
  __ stx(O5, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+5*8);
#endif /* _LP64 */

  __ save(SP, -frame_size, SP);

#ifndef _LP64
  // Reload the 64 bit Oregs. Although they are now Iregs we load them
  // to Oregs here to avoid interrupts cutting off their heads

  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+0*8, O0);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+1*8, O1);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+2*8, O2);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+3*8, O3);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+4*8, O4);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+5*8, O5);

  __ stx(O0, SP, o0_offset+STACK_BIAS);
  map->set_callee_saved(VMRegImpl::stack2reg((o0_offset + 4)>>2), O0->as_VMReg());

  __ stx(O1, SP, o1_offset+STACK_BIAS);

  map->set_callee_saved(VMRegImpl::stack2reg((o1_offset + 4)>>2), O1->as_VMReg());

  __ stx(O2, SP, o2_offset+STACK_BIAS);
  map->set_callee_saved(VMRegImpl::stack2reg((o2_offset + 4)>>2), O2->as_VMReg());

  __ stx(O3, SP, o3_offset+STACK_BIAS);
  map->set_callee_saved(VMRegImpl::stack2reg((o3_offset + 4)>>2), O3->as_VMReg());

  __ stx(O4, SP, o4_offset+STACK_BIAS);
  map->set_callee_saved(VMRegImpl::stack2reg((o4_offset + 4)>>2), O4->as_VMReg());

  __ stx(O5, SP, o5_offset+STACK_BIAS);
  map->set_callee_saved(VMRegImpl::stack2reg((o5_offset + 4)>>2), O5->as_VMReg());
#endif /* _LP64 */


#ifdef _LP64
  int debug_offset = 0;
#else
  int debug_offset = 4;
#endif
  // Save the G's
  __ stx(G1, SP, g1_offset+STACK_BIAS);
  map->set_callee_saved(VMRegImpl::stack2reg((g1_offset + debug_offset)>>2), G1->as_VMReg());

  __ stx(G3, SP, g3_offset+STACK_BIAS);
  map->set_callee_saved(VMRegImpl::stack2reg((g3_offset + debug_offset)>>2), G3->as_VMReg());

  __ stx(G4, SP, g4_offset+STACK_BIAS);
  map->set_callee_saved(VMRegImpl::stack2reg((g4_offset + debug_offset)>>2), G4->as_VMReg());

  __ stx(G5, SP, g5_offset+STACK_BIAS);
  map->set_callee_saved(VMRegImpl::stack2reg((g5_offset + debug_offset)>>2), G5->as_VMReg());

  // This is really a waste but we'll keep things as they were for now
  if (true) {
#ifndef _LP64
    map->set_callee_saved(VMRegImpl::stack2reg((o0_offset)>>2), O0->as_VMReg()->next());
    map->set_callee_saved(VMRegImpl::stack2reg((o1_offset)>>2), O1->as_VMReg()->next());
    map->set_callee_saved(VMRegImpl::stack2reg((o2_offset)>>2), O2->as_VMReg()->next());
    map->set_callee_saved(VMRegImpl::stack2reg((o3_offset)>>2), O3->as_VMReg()->next());
    map->set_callee_saved(VMRegImpl::stack2reg((o4_offset)>>2), O4->as_VMReg()->next());
    map->set_callee_saved(VMRegImpl::stack2reg((o5_offset)>>2), O5->as_VMReg()->next());
    map->set_callee_saved(VMRegImpl::stack2reg((g1_offset)>>2), G1->as_VMReg()->next());
    map->set_callee_saved(VMRegImpl::stack2reg((g3_offset)>>2), G3->as_VMReg()->next());
    map->set_callee_saved(VMRegImpl::stack2reg((g4_offset)>>2), G4->as_VMReg()->next());
    map->set_callee_saved(VMRegImpl::stack2reg((g5_offset)>>2), G5->as_VMReg()->next());
#endif /* _LP64 */
  }


  // Save the flags
  __ rdccr( G5 );
  __ stx(G5, SP, ccr_offset+STACK_BIAS);
  __ stxfsr(SP, fsr_offset+STACK_BIAS);

  // Save all the FP registers: 32 doubles (32 floats correspond to the 2 halves of the first 16 doubles)
  int offset = d00_offset;
  for( int i=0; i<FloatRegisterImpl::number_of_registers; i+=2 ) {
    FloatRegister f = as_FloatRegister(i);
    __ stf(FloatRegisterImpl::D,  f, SP, offset+STACK_BIAS);
    // Record as callee saved both halves of double registers (2 float registers).
    map->set_callee_saved(VMRegImpl::stack2reg(offset>>2), f->as_VMReg());
    map->set_callee_saved(VMRegImpl::stack2reg((offset + sizeof(float))>>2), f->as_VMReg()->next());
    offset += sizeof(double);
  }

  // And we're done.

  return map;
}


// Pop the current frame and restore all the registers that we
// saved.
void RegisterSaver::restore_live_registers(MacroAssembler* masm) {

  // Restore all the FP registers
  for( int i=0; i<FloatRegisterImpl::number_of_registers; i+=2 ) {
    __ ldf(FloatRegisterImpl::D, SP, d00_offset+i*sizeof(float)+STACK_BIAS, as_FloatRegister(i));
  }

  __ ldx(SP, ccr_offset+STACK_BIAS, G1);
  __ wrccr (G1) ;

  // Restore the G's
  // Note that G2 (AKA GThread) must be saved and restored separately.
  // TODO-FIXME: save and restore some of the other ASRs, viz., %asi and %gsr.

  __ ldx(SP, g1_offset+STACK_BIAS, G1);
  __ ldx(SP, g3_offset+STACK_BIAS, G3);
  __ ldx(SP, g4_offset+STACK_BIAS, G4);
  __ ldx(SP, g5_offset+STACK_BIAS, G5);


#if !defined(_LP64)
  // Restore the 64-bit O's.
  __ ldx(SP, o0_offset+STACK_BIAS, O0);
  __ ldx(SP, o1_offset+STACK_BIAS, O1);
  __ ldx(SP, o2_offset+STACK_BIAS, O2);
  __ ldx(SP, o3_offset+STACK_BIAS, O3);
  __ ldx(SP, o4_offset+STACK_BIAS, O4);
  __ ldx(SP, o5_offset+STACK_BIAS, O5);

  // And temporarily place them in TLS

  __ stx(O0, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+0*8);
  __ stx(O1, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+1*8);
  __ stx(O2, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+2*8);
  __ stx(O3, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+3*8);
  __ stx(O4, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+4*8);
  __ stx(O5, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+5*8);
#endif /* _LP64 */

  // Restore flags

  __ ldxfsr(SP, fsr_offset+STACK_BIAS);

  __ restore();

#if !defined(_LP64)
  // Now reload the 64bit Oregs after we've restore the window.
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+0*8, O0);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+1*8, O1);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+2*8, O2);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+3*8, O3);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+4*8, O4);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+5*8, O5);
#endif /* _LP64 */

}

// Pop the current frame and restore the registers that might be holding
// a result.
void RegisterSaver::restore_result_registers(MacroAssembler* masm) {

#if !defined(_LP64)
  // 32bit build returns longs in G1
  __ ldx(SP, g1_offset+STACK_BIAS, G1);

  // Retrieve the 64-bit O's.
  __ ldx(SP, o0_offset+STACK_BIAS, O0);
  __ ldx(SP, o1_offset+STACK_BIAS, O1);
  // and save to TLS
  __ stx(O0, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+0*8);
  __ stx(O1, G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+1*8);
#endif /* _LP64 */

  __ ldf(FloatRegisterImpl::D, SP, d00_offset+STACK_BIAS, as_FloatRegister(0));

  __ restore();

#if !defined(_LP64)
  // Now reload the 64bit Oregs after we've restore the window.
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+0*8, O0);
  __ ldx(G2_thread, JavaThread::o_reg_temps_offset_in_bytes()+1*8, O1);
#endif /* _LP64 */

}

// The java_calling_convention describes stack locations as ideal slots on
// a frame with no abi restrictions. Since we must observe abi restrictions
// (like the placement of the register window) the slots must be biased by
// the following value.
static int reg2offset(VMReg r) {
  return (r->reg2stack() + SharedRuntime::out_preserve_stack_slots()) * VMRegImpl::stack_slot_size;
}

// ---------------------------------------------------------------------------
// Read the array of BasicTypes from a signature, and compute where the
// arguments should go.  Values in the VMRegPair regs array refer to 4-byte (VMRegImpl::stack_slot_size)
// quantities.  Values less than VMRegImpl::stack0 are registers, those above
// refer to 4-byte stack slots.  All stack slots are based off of the window
// top.  VMRegImpl::stack0 refers to the first slot past the 16-word window,
// and VMRegImpl::stack0+1 refers to the memory word 4-byes higher.  Register
// values 0-63 (up to RegisterImpl::number_of_registers) are the 64-bit
// integer registers.  Values 64-95 are the (32-bit only) float registers.
// Each 32-bit quantity is given its own number, so the integer registers
// (in either 32- or 64-bit builds) use 2 numbers.  For example, there is
// an O0-low and an O0-high.  Essentially, all int register numbers are doubled.

// Register results are passed in O0-O5, for outgoing call arguments.  To
// convert to incoming arguments, convert all O's to I's.  The regs array
// refer to the low and hi 32-bit words of 64-bit registers or stack slots.
// If the regs[].second() field is set to VMRegImpl::Bad(), it means it's unused (a
// 32-bit value was passed).  If both are VMRegImpl::Bad(), it means no value was
// passed (used as a placeholder for the other half of longs and doubles in
// the 64-bit build).  regs[].second() is either VMRegImpl::Bad() or regs[].second() is
// regs[].first()+1 (regs[].first() may be misaligned in the C calling convention).
// Sparc never passes a value in regs[].second() but not regs[].first() (regs[].first()
// == VMRegImpl::Bad() && regs[].second() != VMRegImpl::Bad()) nor unrelated values in the
// same VMRegPair.

// Note: the INPUTS in sig_bt are in units of Java argument words, which are
// either 32-bit or 64-bit depending on the build.  The OUTPUTS are in 32-bit
// units regardless of build.


// ---------------------------------------------------------------------------
// The compiled Java calling convention.  The Java convention always passes
// 64-bit values in adjacent aligned locations (either registers or stack),
// floats in float registers and doubles in aligned float pairs.  Values are
// packed in the registers.  There is no backing varargs store for values in
// registers.  In the 32-bit build, longs are passed in G1 and G4 (cannot be
// passed in I's, because longs in I's get their heads chopped off at
// interrupt).
int SharedRuntime::java_calling_convention(const BasicType *sig_bt,
                                           VMRegPair *regs,
                                           int total_args_passed,
                                           int is_outgoing) {
  assert(F31->as_VMReg()->is_reg(), "overlapping stack/register numbers");

  // Convention is to pack the first 6 int/oop args into the first 6 registers
  // (I0-I5), extras spill to the stack.  Then pack the first 8 float args
  // into F0-F7, extras spill to the stack.  Then pad all register sets to
  // align.  Then put longs and doubles into the same registers as they fit,
  // else spill to the stack.
  const int int_reg_max = SPARC_ARGS_IN_REGS_NUM;
  const int flt_reg_max = 8;
  //
  // Where 32-bit 1-reg longs start being passed
  // In tiered we must pass on stack because c1 can't use a "pair" in a single reg.
  // So make it look like we've filled all the G regs that c2 wants to use.
  Register g_reg = TieredCompilation ? noreg : G1;

  // Count int/oop and float args.  See how many stack slots we'll need and
  // where the longs & doubles will go.
  int int_reg_cnt   = 0;
  int flt_reg_cnt   = 0;
  // int stk_reg_pairs = frame::register_save_words*(wordSize>>2);
  // int stk_reg_pairs = SharedRuntime::out_preserve_stack_slots();
  int stk_reg_pairs = 0;
  for (int i = 0; i < total_args_passed; i++) {
    switch (sig_bt[i]) {
    case T_LONG:                // LP64, longs compete with int args
      assert(sig_bt[i+1] == T_VOID, "");
#ifdef _LP64
      if (int_reg_cnt < int_reg_max) int_reg_cnt++;
#endif
      break;
    case T_OBJECT:
    case T_ARRAY:
    case T_ADDRESS: // Used, e.g., in slow-path locking for the lock's stack address
      if (int_reg_cnt < int_reg_max) int_reg_cnt++;
#ifndef _LP64
      else                            stk_reg_pairs++;
#endif
      break;
    case T_INT:
    case T_SHORT:
    case T_CHAR:
    case T_BYTE:
    case T_BOOLEAN:
      if (int_reg_cnt < int_reg_max) int_reg_cnt++;
      else                            stk_reg_pairs++;
      break;
    case T_FLOAT:
      if (flt_reg_cnt < flt_reg_max) flt_reg_cnt++;
      else                            stk_reg_pairs++;
      break;
    case T_DOUBLE:
      assert(sig_bt[i+1] == T_VOID, "");
      break;
    case T_VOID:
      break;
    default:
      ShouldNotReachHere();
    }
  }

  // This is where the longs/doubles start on the stack.
  stk_reg_pairs = (stk_reg_pairs+1) & ~1; // Round

  int int_reg_pairs = (int_reg_cnt+1) & ~1; // 32-bit 2-reg longs only
  int flt_reg_pairs = (flt_reg_cnt+1) & ~1;

  // int stk_reg = frame::register_save_words*(wordSize>>2);
  // int stk_reg = SharedRuntime::out_preserve_stack_slots();
  int stk_reg = 0;
  int int_reg = 0;
  int flt_reg = 0;

  // Now do the signature layout
  for (int i = 0; i < total_args_passed; i++) {
    switch (sig_bt[i]) {
    case T_INT:
    case T_SHORT:
    case T_CHAR:
    case T_BYTE:
    case T_BOOLEAN:
#ifndef _LP64
    case T_OBJECT:
    case T_ARRAY:
    case T_ADDRESS: // Used, e.g., in slow-path locking for the lock's stack address
#endif // _LP64
      if (int_reg < int_reg_max) {
        Register r = is_outgoing ? as_oRegister(int_reg++) : as_iRegister(int_reg++);
        regs[i].set1(r->as_VMReg());
      } else {
        regs[i].set1(VMRegImpl::stack2reg(stk_reg++));
      }
      break;

#ifdef _LP64
    case T_OBJECT:
    case T_ARRAY:
    case T_ADDRESS: // Used, e.g., in slow-path locking for the lock's stack address
      if (int_reg < int_reg_max) {
        Register r = is_outgoing ? as_oRegister(int_reg++) : as_iRegister(int_reg++);
        regs[i].set2(r->as_VMReg());
      } else {
        regs[i].set2(VMRegImpl::stack2reg(stk_reg_pairs));
        stk_reg_pairs += 2;
      }
      break;
#endif // _LP64

    case T_LONG:
      assert(sig_bt[i+1] == T_VOID, "expecting VOID in other half");
#ifdef _LP64
        if (int_reg < int_reg_max) {
          Register r = is_outgoing ? as_oRegister(int_reg++) : as_iRegister(int_reg++);
          regs[i].set2(r->as_VMReg());
        } else {
          regs[i].set2(VMRegImpl::stack2reg(stk_reg_pairs));
          stk_reg_pairs += 2;
        }
#else
#ifdef COMPILER2
        // For 32-bit build, can't pass longs in O-regs because they become
        // I-regs and get trashed.  Use G-regs instead.  G1 and G4 are almost
        // spare and available.  This convention isn't used by the Sparc ABI or
        // anywhere else. If we're tiered then we don't use G-regs because c1
        // can't deal with them as a "pair". (Tiered makes this code think g's are filled)
        // G0: zero
        // G1: 1st Long arg
        // G2: global allocated to TLS
        // G3: used in inline cache check
        // G4: 2nd Long arg
        // G5: used in inline cache check
        // G6: used by OS
        // G7: used by OS

        if (g_reg == G1) {
          regs[i].set2(G1->as_VMReg()); // This long arg in G1
          g_reg = G4;                  // Where the next arg goes
        } else if (g_reg == G4) {
          regs[i].set2(G4->as_VMReg()); // The 2nd long arg in G4
          g_reg = noreg;               // No more longs in registers
        } else {
          regs[i].set2(VMRegImpl::stack2reg(stk_reg_pairs));
          stk_reg_pairs += 2;
        }
#else // COMPILER2
        if (int_reg_pairs + 1 < int_reg_max) {
          if (is_outgoing) {
            regs[i].set_pair(as_oRegister(int_reg_pairs + 1)->as_VMReg(), as_oRegister(int_reg_pairs)->as_VMReg());
          } else {
            regs[i].set_pair(as_iRegister(int_reg_pairs + 1)->as_VMReg(), as_iRegister(int_reg_pairs)->as_VMReg());
          }
          int_reg_pairs += 2;
        } else {
          regs[i].set2(VMRegImpl::stack2reg(stk_reg_pairs));
          stk_reg_pairs += 2;
        }
#endif // COMPILER2
#endif // _LP64
      break;

    case T_FLOAT:
      if (flt_reg < flt_reg_max) regs[i].set1(as_FloatRegister(flt_reg++)->as_VMReg());
      else                       regs[i].set1(    VMRegImpl::stack2reg(stk_reg++));
      break;
    case T_DOUBLE:
      assert(sig_bt[i+1] == T_VOID, "expecting half");
      if (flt_reg_pairs + 1 < flt_reg_max) {
        regs[i].set2(as_FloatRegister(flt_reg_pairs)->as_VMReg());
        flt_reg_pairs += 2;
      } else {
        regs[i].set2(VMRegImpl::stack2reg(stk_reg_pairs));
        stk_reg_pairs += 2;
      }
      break;
    case T_VOID: regs[i].set_bad();  break; // Halves of longs & doubles
    default:
      ShouldNotReachHere();
    }
  }

  // retun the amount of stack space these arguments will need.
  return stk_reg_pairs;

}

// Helper class mostly to avoid passing masm everywhere, and handle
// store displacement overflow logic.
class AdapterGenerator {
  MacroAssembler *masm;
  Register Rdisp;
  void set_Rdisp(Register r)  { Rdisp = r; }

  void patch_callers_callsite();
  void tag_c2i_arg(frame::Tag t, Register base, int st_off, Register scratch);

  // base+st_off points to top of argument
  int arg_offset(const int st_off) { return st_off + Interpreter::value_offset_in_bytes(); }
  int next_arg_offset(const int st_off) {
    return st_off - Interpreter::stackElementSize() + Interpreter::value_offset_in_bytes();
  }

  int tag_offset(const int st_off) { return st_off + Interpreter::tag_offset_in_bytes(); }
  int next_tag_offset(const int st_off) {
    return st_off - Interpreter::stackElementSize() + Interpreter::tag_offset_in_bytes();
  }

  // Argument slot values may be loaded first into a register because
  // they might not fit into displacement.
  RegisterOrConstant arg_slot(const int st_off);
  RegisterOrConstant next_arg_slot(const int st_off);

  RegisterOrConstant tag_slot(const int st_off);
  RegisterOrConstant next_tag_slot(const int st_off);

  // Stores long into offset pointed to by base
  void store_c2i_long(Register r, Register base,
                      const int st_off, bool is_stack);
  void store_c2i_object(Register r, Register base,
                        const int st_off);
  void store_c2i_int(Register r, Register base,
                     const int st_off);
  void store_c2i_double(VMReg r_2,
                        VMReg r_1, Register base, const int st_off);
  void store_c2i_float(FloatRegister f, Register base,
                       const int st_off);

 public:
  void gen_c2i_adapter(int total_args_passed,
                              // VMReg max_arg,
                              int comp_args_on_stack, // VMRegStackSlots
                              const BasicType *sig_bt,
                              const VMRegPair *regs,
                              Label& skip_fixup);
  void gen_i2c_adapter(int total_args_passed,
                              // VMReg max_arg,
                              int comp_args_on_stack, // VMRegStackSlots
                              const BasicType *sig_bt,
                              const VMRegPair *regs);

  AdapterGenerator(MacroAssembler *_masm) : masm(_masm) {}
};


// Patch the callers callsite with entry to compiled code if it exists.
void AdapterGenerator::patch_callers_callsite() {
  Label L;
  __ ld_ptr(G5_method, in_bytes(methodOopDesc::code_offset()), G3_scratch);
  __ br_null(G3_scratch, false, __ pt, L);
  // Schedule the branch target address early.
  __ delayed()->ld_ptr(G5_method, in_bytes(methodOopDesc::interpreter_entry_offset()), G3_scratch);
  // Call into the VM to patch the caller, then jump to compiled callee
  __ save_frame(4);     // Args in compiled layout; do not blow them

  // Must save all the live Gregs the list is:
  // G1: 1st Long arg (32bit build)
  // G2: global allocated to TLS
  // G3: used in inline cache check (scratch)
  // G4: 2nd Long arg (32bit build);
  // G5: used in inline cache check (methodOop)

  // The longs must go to the stack by hand since in the 32 bit build they can be trashed by window ops.

#ifdef _LP64
  // mov(s,d)
  __ mov(G1, L1);
  __ mov(G4, L4);
  __ mov(G5_method, L5);
  __ mov(G5_method, O0);         // VM needs target method
  __ mov(I7, O1);                // VM needs caller's callsite
  // Must be a leaf call...
  // can be very far once the blob has been relocated
  AddressLiteral dest(CAST_FROM_FN_PTR(address, SharedRuntime::fixup_callers_callsite));
  __ relocate(relocInfo::runtime_call_type);
  __ jumpl_to(dest, O7, O7);
  __ delayed()->mov(G2_thread, L7_thread_cache);
  __ mov(L7_thread_cache, G2_thread);
  __ mov(L1, G1);
  __ mov(L4, G4);
  __ mov(L5, G5_method);
#else
  __ stx(G1, FP, -8 + STACK_BIAS);
  __ stx(G4, FP, -16 + STACK_BIAS);
  __ mov(G5_method, L5);
  __ mov(G5_method, O0);         // VM needs target method
  __ mov(I7, O1);                // VM needs caller's callsite
  // Must be a leaf call...
  __ call(CAST_FROM_FN_PTR(address, SharedRuntime::fixup_callers_callsite), relocInfo::runtime_call_type);
  __ delayed()->mov(G2_thread, L7_thread_cache);
  __ mov(L7_thread_cache, G2_thread);
  __ ldx(FP, -8 + STACK_BIAS, G1);
  __ ldx(FP, -16 + STACK_BIAS, G4);
  __ mov(L5, G5_method);
  __ ld_ptr(G5_method, in_bytes(methodOopDesc::interpreter_entry_offset()), G3_scratch);
#endif /* _LP64 */

  __ restore();      // Restore args
  __ bind(L);
}

void AdapterGenerator::tag_c2i_arg(frame::Tag t, Register base, int st_off,
                 Register scratch) {
  if (TaggedStackInterpreter) {
    RegisterOrConstant slot = tag_slot(st_off);
    // have to store zero because local slots can be reused (rats!)
    if (t == frame::TagValue) {
      __ st_ptr(G0, base, slot);
    } else if (t == frame::TagCategory2) {
      __ st_ptr(G0, base, slot);
      __ st_ptr(G0, base, next_tag_slot(st_off));
    } else {
      __ mov(t, scratch);
      __ st_ptr(scratch, base, slot);
    }
  }
}


RegisterOrConstant AdapterGenerator::arg_slot(const int st_off) {
  RegisterOrConstant roc(arg_offset(st_off));
  return __ ensure_simm13_or_reg(roc, Rdisp);
}

RegisterOrConstant AdapterGenerator::next_arg_slot(const int st_off) {
  RegisterOrConstant roc(next_arg_offset(st_off));
  return __ ensure_simm13_or_reg(roc, Rdisp);
}


RegisterOrConstant AdapterGenerator::tag_slot(const int st_off) {
  RegisterOrConstant roc(tag_offset(st_off));
  return __ ensure_simm13_or_reg(roc, Rdisp);
}

RegisterOrConstant AdapterGenerator::next_tag_slot(const int st_off) {
  RegisterOrConstant roc(next_tag_offset(st_off));
  return __ ensure_simm13_or_reg(roc, Rdisp);
}


// Stores long into offset pointed to by base
void AdapterGenerator::store_c2i_long(Register r, Register base,
                                      const int st_off, bool is_stack) {
#ifdef _LP64
  // In V9, longs are given 2 64-bit slots in the interpreter, but the
  // data is passed in only 1 slot.
  __ stx(r, base, next_arg_slot(st_off));
#else
#ifdef COMPILER2
  // Misaligned store of 64-bit data
  __ stw(r, base, arg_slot(st_off));    // lo bits
  __ srlx(r, 32, r);
  __ stw(r, base, next_arg_slot(st_off));  // hi bits
#else
  if (is_stack) {
    // Misaligned store of 64-bit data
    __ stw(r, base, arg_slot(st_off));    // lo bits
    __ srlx(r, 32, r);
    __ stw(r, base, next_arg_slot(st_off));  // hi bits
  } else {
    __ stw(r->successor(), base, arg_slot(st_off)     ); // lo bits
    __ stw(r             , base, next_arg_slot(st_off)); // hi bits
  }
#endif // COMPILER2
#endif // _LP64
  tag_c2i_arg(frame::TagCategory2, base, st_off, r);
}

void AdapterGenerator::store_c2i_object(Register r, Register base,
                      const int st_off) {
  __ st_ptr (r, base, arg_slot(st_off));
  tag_c2i_arg(frame::TagReference, base, st_off, r);
}

void AdapterGenerator::store_c2i_int(Register r, Register base,
                   const int st_off) {
  __ st (r, base, arg_slot(st_off));
  tag_c2i_arg(frame::TagValue, base, st_off, r);
}

// Stores into offset pointed to by base
void AdapterGenerator::store_c2i_double(VMReg r_2,
                      VMReg r_1, Register base, const int st_off) {
#ifdef _LP64
  // In V9, doubles are given 2 64-bit slots in the interpreter, but the
  // data is passed in only 1 slot.
  __ stf(FloatRegisterImpl::D, r_1->as_FloatRegister(), base, next_arg_slot(st_off));
#else
  // Need to marshal 64-bit value from misaligned Lesp loads
  __ stf(FloatRegisterImpl::S, r_1->as_FloatRegister(), base, next_arg_slot(st_off));
  __ stf(FloatRegisterImpl::S, r_2->as_FloatRegister(), base, arg_slot(st_off) );
#endif
  tag_c2i_arg(frame::TagCategory2, base, st_off, G1_scratch);
}

void AdapterGenerator::store_c2i_float(FloatRegister f, Register base,
                                       const int st_off) {
  __ stf(FloatRegisterImpl::S, f, base, arg_slot(st_off));
  tag_c2i_arg(frame::TagValue, base, st_off, G1_scratch);
}

void AdapterGenerator::gen_c2i_adapter(
                            int total_args_passed,
                            // VMReg max_arg,
                            int comp_args_on_stack, // VMRegStackSlots
                            const BasicType *sig_bt,
                            const VMRegPair *regs,
                            Label& skip_fixup) {

  // Before we get into the guts of the C2I adapter, see if we should be here
  // at all.  We've come from compiled code and are attempting to jump to the
  // interpreter, which means the caller made a static call to get here
  // (vcalls always get a compiled target if there is one).  Check for a
  // compiled target.  If there is one, we need to patch the caller's call.
  // However we will run interpreted if we come thru here. The next pass
  // thru the call site will run compiled. If we ran compiled here then
  // we can (theorectically) do endless i2c->c2i->i2c transitions during
  // deopt/uncommon trap cycles. If we always go interpreted here then
  // we can have at most one and don't need to play any tricks to keep
  // from endlessly growing the stack.
  //
  // Actually if we detected that we had an i2c->c2i transition here we
  // ought to be able to reset the world back to the state of the interpreted
  // call and not bother building another interpreter arg area. We don't
  // do that at this point.

  patch_callers_callsite();

  __ bind(skip_fixup);

  // Since all args are passed on the stack, total_args_passed*wordSize is the
  // space we need.  Add in varargs area needed by the interpreter. Round up
  // to stack alignment.
  const int arg_size = total_args_passed * Interpreter::stackElementSize();
  const int varargs_area =
                 (frame::varargs_offset - frame::register_save_words)*wordSize;
  const int extraspace = round_to(arg_size + varargs_area, 2*wordSize);

  int bias = STACK_BIAS;
  const int interp_arg_offset = frame::varargs_offset*wordSize +
                        (total_args_passed-1)*Interpreter::stackElementSize();

  Register base = SP;

#ifdef _LP64
  // In the 64bit build because of wider slots and STACKBIAS we can run
  // out of bits in the displacement to do loads and stores.  Use g3 as
  // temporary displacement.
  if (! __ is_simm13(extraspace)) {
    __ set(extraspace, G3_scratch);
    __ sub(SP, G3_scratch, SP);
  } else {
    __ sub(SP, extraspace, SP);
  }
  set_Rdisp(G3_scratch);
#else
  __ sub(SP, extraspace, SP);
#endif // _LP64

  // First write G1 (if used) to where ever it must go
  for (int i=0; i<total_args_passed; i++) {
    const int st_off = interp_arg_offset - (i*Interpreter::stackElementSize()) + bias;
    VMReg r_1 = regs[i].first();
    VMReg r_2 = regs[i].second();
    if (r_1 == G1_scratch->as_VMReg()) {
      if (sig_bt[i] == T_OBJECT || sig_bt[i] == T_ARRAY) {
        store_c2i_object(G1_scratch, base, st_off);
      } else if (sig_bt[i] == T_LONG) {
        assert(!TieredCompilation, "should not use register args for longs");
        store_c2i_long(G1_scratch, base, st_off, false);
      } else {
        store_c2i_int(G1_scratch, base, st_off);
      }
    }
  }

  // Now write the args into the outgoing interpreter space
  for (int i=0; i<total_args_passed; i++) {
    const int st_off = interp_arg_offset - (i*Interpreter::stackElementSize()) + bias;
    VMReg r_1 = regs[i].first();
    VMReg r_2 = regs[i].second();
    if (!r_1->is_valid()) {
      assert(!r_2->is_valid(), "");
      continue;
    }
    // Skip G1 if found as we did it first in order to free it up
    if (r_1 == G1_scratch->as_VMReg()) {
      continue;
    }
#ifdef ASSERT
    bool G1_forced = false;
#endif // ASSERT
    if (r_1->is_stack()) {        // Pretend stack targets are loaded into G1
#ifdef _LP64
      Register ld_off = Rdisp;
      __ set(reg2offset(r_1) + extraspace + bias, ld_off);
#else
      int ld_off = reg2offset(r_1) + extraspace + bias;
#ifdef ASSERT
      G1_forced = true;
#endif // ASSERT
#endif // _LP64
      r_1 = G1_scratch->as_VMReg();// as part of the load/store shuffle
      if (!r_2->is_valid()) __ ld (base, ld_off, G1_scratch);
      else                  __ ldx(base, ld_off, G1_scratch);
    }

    if (r_1->is_Register()) {
      Register r = r_1->as_Register()->after_restore();
      if (sig_bt[i] == T_OBJECT || sig_bt[i] == T_ARRAY) {
        store_c2i_object(r, base, st_off);
      } else if (sig_bt[i] == T_LONG || sig_bt[i] == T_DOUBLE) {
        if (TieredCompilation) {
          assert(G1_forced || sig_bt[i] != T_LONG, "should not use register args for longs");
        }
        store_c2i_long(r, base, st_off, r_2->is_stack());
      } else {
        store_c2i_int(r, base, st_off);
      }
    } else {
      assert(r_1->is_FloatRegister(), "");
      if (sig_bt[i] == T_FLOAT) {
        store_c2i_float(r_1->as_FloatRegister(), base, st_off);
      } else {
        assert(sig_bt[i] == T_DOUBLE, "wrong type");
        store_c2i_double(r_2, r_1, base, st_off);
      }
    }
  }

#ifdef _LP64
  // Need to reload G3_scratch, used for temporary displacements.
  __ ld_ptr(G5_method, in_bytes(methodOopDesc::interpreter_entry_offset()), G3_scratch);

  // Pass O5_savedSP as an argument to the interpreter.
  // The interpreter will restore SP to this value before returning.
  __ set(extraspace, G1);
  __ add(SP, G1, O5_savedSP);
#else
  // Pass O5_savedSP as an argument to the interpreter.
  // The interpreter will restore SP to this value before returning.
  __ add(SP, extraspace, O5_savedSP);
#endif // _LP64

  __ mov((frame::varargs_offset)*wordSize -
         1*Interpreter::stackElementSize()+bias+BytesPerWord, G1);
  // Jump to the interpreter just as if interpreter was doing it.
  __ jmpl(G3_scratch, 0, G0);
  // Setup Lesp for the call.  Cannot actually set Lesp as the current Lesp
  // (really L0) is in use by the compiled frame as a generic temp.  However,
  // the interpreter does not know where its args are without some kind of
  // arg pointer being passed in.  Pass it in Gargs.
  __ delayed()->add(SP, G1, Gargs);
}

void AdapterGenerator::gen_i2c_adapter(
                            int total_args_passed,
                            // VMReg max_arg,
                            int comp_args_on_stack, // VMRegStackSlots
                            const BasicType *sig_bt,
                            const VMRegPair *regs) {

  // Generate an I2C adapter: adjust the I-frame to make space for the C-frame
  // layout.  Lesp was saved by the calling I-frame and will be restored on
  // return.  Meanwhile, outgoing arg space is all owned by the callee
  // C-frame, so we can mangle it at will.  After adjusting the frame size,
  // hoist register arguments and repack other args according to the compiled
  // code convention.  Finally, end in a jump to the compiled code.  The entry
  // point address is the start of the buffer.

  // We will only enter here from an interpreted frame and never from after
  // passing thru a c2i. Azul allowed this but we do not. If we lose the
  // race and use a c2i we will remain interpreted for the race loser(s).
  // This removes all sorts of headaches on the x86 side and also eliminates
  // the possibility of having c2i -> i2c -> c2i -> ... endless transitions.

  // As you can see from the list of inputs & outputs there are not a lot
  // of temp registers to work with: mostly G1, G3 & G4.

  // Inputs:
  // G2_thread      - TLS
  // G5_method      - Method oop
  // G4 (Gargs)     - Pointer to interpreter's args
  // O0..O4         - free for scratch
  // O5_savedSP     - Caller's saved SP, to be restored if needed
  // O6             - Current SP!
  // O7             - Valid return address
  // L0-L7, I0-I7   - Caller's temps (no frame pushed yet)

  // Outputs:
  // G2_thread      - TLS
  // G1, G4         - Outgoing long args in 32-bit build
  // O0-O5          - Outgoing args in compiled layout
  // O6             - Adjusted or restored SP
  // O7             - Valid return address
  // L0-L7, I0-I7    - Caller's temps (no frame pushed yet)
  // F0-F7          - more outgoing args


  // Gargs is the incoming argument base, and also an outgoing argument.
  __ sub(Gargs, BytesPerWord, Gargs);

#ifdef ASSERT
  {
    // on entry OsavedSP and SP should be equal
    Label ok;
    __ cmp(O5_savedSP, SP);
    __ br(Assembler::equal, false, Assembler::pt, ok);
    __ delayed()->nop();
    __ stop("I5_savedSP not set");
    __ should_not_reach_here();
    __ bind(ok);
  }
#endif

  // ON ENTRY TO THE CODE WE ARE MAKING, WE HAVE AN INTERPRETED FRAME
  // WITH O7 HOLDING A VALID RETURN PC
  //
  // |              |
  // :  java stack  :
  // |              |
  // +--------------+ <--- start of outgoing args
  // |   receiver   |   |
  // : rest of args :   |---size is java-arg-words
  // |              |   |
  // +--------------+ <--- O4_args (misaligned) and Lesp if prior is not C2I
  // |              |   |
  // :    unused    :   |---Space for max Java stack, plus stack alignment
  // |              |   |
  // +--------------+ <--- SP + 16*wordsize
  // |              |
  // :    window    :
  // |              |
  // +--------------+ <--- SP

  // WE REPACK THE STACK.  We use the common calling convention layout as
  // discovered by calling SharedRuntime::calling_convention.  We assume it
  // causes an arbitrary shuffle of memory, which may require some register
  // temps to do the shuffle.  We hope for (and optimize for) the case where
  // temps are not needed.  We may have to resize the stack slightly, in case
  // we need alignment padding (32-bit interpreter can pass longs & doubles
  // misaligned, but the compilers expect them aligned).
  //
  // |              |
  // :  java stack  :
  // |              |
  // +--------------+ <--- start of outgoing args
  // |  pad, align  |   |
  // +--------------+   |
  // | ints, floats |   |---Outgoing stack args, packed low.
  // +--------------+   |   First few args in registers.
  // :   doubles    :   |
  // |   longs      |   |
  // +--------------+ <--- SP' + 16*wordsize
  // |              |
  // :    window    :
  // |              |
  // +--------------+ <--- SP'

  // ON EXIT FROM THE CODE WE ARE MAKING, WE STILL HAVE AN INTERPRETED FRAME
  // WITH O7 HOLDING A VALID RETURN PC - ITS JUST THAT THE ARGS ARE NOW SETUP
  // FOR COMPILED CODE AND THE FRAME SLIGHTLY GROWN.

  // Cut-out for having no stack args.  Since up to 6 args are passed
  // in registers, we will commonly have no stack args.
  if (comp_args_on_stack > 0) {

    // Convert VMReg stack slots to words.
    int comp_words_on_stack = round_to(comp_args_on_stack*VMRegImpl::stack_slot_size, wordSize)>>LogBytesPerWord;
    // Round up to miminum stack alignment, in wordSize
    comp_words_on_stack = round_to(comp_words_on_stack, 2);
    // Now compute the distance from Lesp to SP.  This calculation does not
    // include the space for total_args_passed because Lesp has not yet popped
    // the arguments.
    __ sub(SP, (comp_words_on_stack)*wordSize, SP);
  }

  // Will jump to the compiled code just as if compiled code was doing it.
  // Pre-load the register-jump target early, to schedule it better.
  __ ld_ptr(G5_method, in_bytes(methodOopDesc::from_compiled_offset()), G3);

  // Now generate the shuffle code.  Pick up all register args and move the
  // rest through G1_scratch.
  for (int i=0; i<total_args_passed; i++) {
    if (sig_bt[i] == T_VOID) {
      // Longs and doubles are passed in native word order, but misaligned
      // in the 32-bit build.
      assert(i > 0 && (sig_bt[i-1] == T_LONG || sig_bt[i-1] == T_DOUBLE), "missing half");
      continue;
    }

    // Pick up 0, 1 or 2 words from Lesp+offset.  Assume mis-aligned in the
    // 32-bit build and aligned in the 64-bit build.  Look for the obvious
    // ldx/lddf optimizations.

    // Load in argument order going down.
    const int ld_off = (total_args_passed-i)*Interpreter::stackElementSize();
    set_Rdisp(G1_scratch);

    VMReg r_1 = regs[i].first();
    VMReg r_2 = regs[i].second();
    if (!r_1->is_valid()) {
      assert(!r_2->is_valid(), "");
      continue;
    }
    if (r_1->is_stack()) {        // Pretend stack targets are loaded into F8/F9
      r_1 = F8->as_VMReg();        // as part of the load/store shuffle
      if (r_2->is_valid()) r_2 = r_1->next();
    }
    if (r_1->is_Register()) {  // Register argument
      Register r = r_1->as_Register()->after_restore();
      if (!r_2->is_valid()) {
        __ ld(Gargs, arg_slot(ld_off), r);
      } else {
#ifdef _LP64
        // In V9, longs are given 2 64-bit slots in the interpreter, but the
        // data is passed in only 1 slot.
        RegisterOrConstant slot = (sig_bt[i] == T_LONG) ?
              next_arg_slot(ld_off) : arg_slot(ld_off);
        __ ldx(Gargs, slot, r);
#else
        // Need to load a 64-bit value into G1/G4, but G1/G4 is being used in the
        // stack shuffle.  Load the first 2 longs into G1/G4 later.
#endif
      }
    } else {
      assert(r_1->is_FloatRegister(), "");
      if (!r_2->is_valid()) {
        __ ldf(FloatRegisterImpl::S, Gargs, arg_slot(ld_off), r_1->as_FloatRegister());
      } else {
#ifdef _LP64
        // In V9, doubles are given 2 64-bit slots in the interpreter, but the
        // data is passed in only 1 slot.  This code also handles longs that
        // are passed on the stack, but need a stack-to-stack move through a
        // spare float register.
        RegisterOrConstant slot = (sig_bt[i] == T_LONG || sig_bt[i] == T_DOUBLE) ?
              next_arg_slot(ld_off) : arg_slot(ld_off);
        __ ldf(FloatRegisterImpl::D, Gargs, slot, r_1->as_FloatRegister());
#else
        // Need to marshal 64-bit value from misaligned Lesp loads
        __ ldf(FloatRegisterImpl::S, Gargs, next_arg_slot(ld_off), r_1->as_FloatRegister());
        __ ldf(FloatRegisterImpl::S, Gargs, arg_slot(ld_off), r_2->as_FloatRegister());
#endif
      }
    }
    // Was the argument really intended to be on the stack, but was loaded
    // into F8/F9?
    if (regs[i].first()->is_stack()) {
      assert(r_1->as_FloatRegister() == F8, "fix this code");
      // Convert stack slot to an SP offset
      int st_off = reg2offset(regs[i].first()) + STACK_BIAS;
      // Store down the shuffled stack word.  Target address _is_ aligned.
      RegisterOrConstant slot = __ ensure_simm13_or_reg(st_off, Rdisp);
      if (!r_2->is_valid()) __ stf(FloatRegisterImpl::S, r_1->as_FloatRegister(), SP, slot);
      else                  __ stf(FloatRegisterImpl::D, r_1->as_FloatRegister(), SP, slot);
    }
  }
  bool made_space = false;
#ifndef _LP64
  // May need to pick up a few long args in G1/G4
  bool g4_crushed = false;
  bool g3_crushed = false;
  for (int i=0; i<total_args_passed; i++) {
    if (regs[i].first()->is_Register() && regs[i].second()->is_valid()) {
      // Load in argument order going down
      int ld_off = (total_args_passed-i)*Interpreter::stackElementSize();
      // Need to marshal 64-bit value from misaligned Lesp loads
      Register r = regs[i].first()->as_Register()->after_restore();
      if (r == G1 || r == G4) {
        assert(!g4_crushed, "ordering problem");
        if (r == G4){
          g4_crushed = true;
          __ lduw(Gargs, arg_slot(ld_off)     , G3_scratch); // Load lo bits
          __ ld  (Gargs, next_arg_slot(ld_off), r);          // Load hi bits
        } else {
          // better schedule this way
          __ ld  (Gargs, next_arg_slot(ld_off), r);          // Load hi bits
          __ lduw(Gargs, arg_slot(ld_off)     , G3_scratch); // Load lo bits
        }
        g3_crushed = true;
        __ sllx(r, 32, r);
        __ or3(G3_scratch, r, r);
      } else {
        assert(r->is_out(), "longs passed in two O registers");
        __ ld  (Gargs, arg_slot(ld_off)     , r->successor()); // Load lo bits
        __ ld  (Gargs, next_arg_slot(ld_off), r);              // Load hi bits
      }
    }
  }
#endif

  // Jump to the compiled code just as if compiled code was doing it.
  //
#ifndef _LP64
    if (g3_crushed) {
      // Rats load was wasted, at least it is in cache...
      __ ld_ptr(G5_method, methodOopDesc::from_compiled_offset(), G3);
    }
#endif /* _LP64 */

    // 6243940 We might end up in handle_wrong_method if
    // the callee is deoptimized as we race thru here. If that
    // happens we don't want to take a safepoint because the
    // caller frame will look interpreted and arguments are now
    // "compiled" so it is much better to make this transition
    // invisible to the stack walking code. Unfortunately if
    // we try and find the callee by normal means a safepoint
    // is possible. So we stash the desired callee in the thread
    // and the vm will find there should this case occur.
    Address callee_target_addr(G2_thread, JavaThread::callee_target_offset());
    __ st_ptr(G5_method, callee_target_addr);

    if (StressNonEntrant) {
      // Open a big window for deopt failure
      __ save_frame(0);
      __ mov(G0, L0);
      Label loop;
      __ bind(loop);
      __ sub(L0, 1, L0);
      __ br_null(L0, false, Assembler::pt, loop);
      __ delayed()->nop();

      __ restore();
    }


    __ jmpl(G3, 0, G0);
    __ delayed()->nop();
}

// ---------------------------------------------------------------
AdapterHandlerEntry* SharedRuntime::generate_i2c2i_adapters(MacroAssembler *masm,
                                                            int total_args_passed,
                                                            // VMReg max_arg,
                                                            int comp_args_on_stack, // VMRegStackSlots
                                                            const BasicType *sig_bt,
                                                            const VMRegPair *regs) {
  address i2c_entry = __ pc();

  AdapterGenerator agen(masm);

  agen.gen_i2c_adapter(total_args_passed, comp_args_on_stack, sig_bt, regs);


  // -------------------------------------------------------------------------
  // Generate a C2I adapter.  On entry we know G5 holds the methodOop.  The
  // args start out packed in the compiled layout.  They need to be unpacked
  // into the interpreter layout.  This will almost always require some stack
  // space.  We grow the current (compiled) stack, then repack the args.  We
  // finally end in a jump to the generic interpreter entry point.  On exit
  // from the interpreter, the interpreter will restore our SP (lest the
  // compiled code, which relys solely on SP and not FP, get sick).

  address c2i_unverified_entry = __ pc();
  Label skip_fixup;
  {
#if !defined(_LP64) && defined(COMPILER2)
    Register R_temp   = L0;   // another scratch register
#else
    Register R_temp   = G1;   // another scratch register
#endif

    AddressLiteral ic_miss(SharedRuntime::get_ic_miss_stub());

    __ verify_oop(O0);
    __ verify_oop(G5_method);
    __ load_klass(O0, G3_scratch);
    __ verify_oop(G3_scratch);

#if !defined(_LP64) && defined(COMPILER2)
    __ save(SP, -frame::register_save_words*wordSize, SP);
    __ ld_ptr(G5_method, compiledICHolderOopDesc::holder_klass_offset(), R_temp);
    __ verify_oop(R_temp);
    __ cmp(G3_scratch, R_temp);
    __ restore();
#else
    __ ld_ptr(G5_method, compiledICHolderOopDesc::holder_klass_offset(), R_temp);
    __ verify_oop(R_temp);
    __ cmp(G3_scratch, R_temp);
#endif

    Label ok, ok2;
    __ brx(Assembler::equal, false, Assembler::pt, ok);
    __ delayed()->ld_ptr(G5_method, compiledICHolderOopDesc::holder_method_offset(), G5_method);
    __ jump_to(ic_miss, G3_scratch);
    __ delayed()->nop();

    __ bind(ok);
    // Method might have been compiled since the call site was patched to
    // interpreted if that is the case treat it as a miss so we can get
    // the call site corrected.
    __ ld_ptr(G5_method, in_bytes(methodOopDesc::code_offset()), G3_scratch);
    __ bind(ok2);
    __ br_null(G3_scratch, false, __ pt, skip_fixup);
    __ delayed()->ld_ptr(G5_method, in_bytes(methodOopDesc::interpreter_entry_offset()), G3_scratch);
    __ jump_to(ic_miss, G3_scratch);
    __ delayed()->nop();

  }

  address c2i_entry = __ pc();

  agen.gen_c2i_adapter(total_args_passed, comp_args_on_stack, sig_bt, regs, skip_fixup);

  __ flush();
  return new AdapterHandlerEntry(i2c_entry, c2i_entry, c2i_unverified_entry);

}

// Helper function for native calling conventions
static VMReg int_stk_helper( int i ) {
  // Bias any stack based VMReg we get by ignoring the window area
  // but not the register parameter save area.
  //
  // This is strange for the following reasons. We'd normally expect
  // the calling convention to return an VMReg for a stack slot
  // completely ignoring any abi reserved area. C2 thinks of that
  // abi area as only out_preserve_stack_slots. This does not include
  // the area allocated by the C abi to store down integer arguments
  // because the java calling convention does not use it. So
  // since c2 assumes that there are only out_preserve_stack_slots
  // to bias the optoregs (which impacts VMRegs) when actually referencing any actual stack
  // location the c calling convention must add in this bias amount
  // to make up for the fact that the out_preserve_stack_slots is
  // insufficient for C calls. What a mess. I sure hope those 6
  // stack words were worth it on every java call!

  // Another way of cleaning this up would be for out_preserve_stack_slots
  // to take a parameter to say whether it was C or java calling conventions.
  // Then things might look a little better (but not much).

  int mem_parm_offset = i - SPARC_ARGS_IN_REGS_NUM;
  if( mem_parm_offset < 0 ) {
    return as_oRegister(i)->as_VMReg();
  } else {
    int actual_offset = (mem_parm_offset + frame::memory_parameter_word_sp_offset) * VMRegImpl::slots_per_word;
    // Now return a biased offset that will be correct when out_preserve_slots is added back in
    return VMRegImpl::stack2reg(actual_offset - SharedRuntime::out_preserve_stack_slots());
  }
}


int SharedRuntime::c_calling_convention(const BasicType *sig_bt,
                                         VMRegPair *regs,
                                         int total_args_passed) {

    // Return the number of VMReg stack_slots needed for the args.
    // This value does not include an abi space (like register window
    // save area).

    // The native convention is V8 if !LP64
    // The LP64 convention is the V9 convention which is slightly more sane.

    // We return the amount of VMReg stack slots we need to reserve for all
    // the arguments NOT counting out_preserve_stack_slots. Since we always
    // have space for storing at least 6 registers to memory we start with that.
    // See int_stk_helper for a further discussion.
    int max_stack_slots = (frame::varargs_offset * VMRegImpl::slots_per_word) - SharedRuntime::out_preserve_stack_slots();

#ifdef _LP64
    // V9 convention: All things "as-if" on double-wide stack slots.
    // Hoist any int/ptr/long's in the first 6 to int regs.
    // Hoist any flt/dbl's in the first 16 dbl regs.
    int j = 0;                  // Count of actual args, not HALVES
    for( int i=0; i<total_args_passed; i++, j++ ) {
      switch( sig_bt[i] ) {
      case T_BOOLEAN:
      case T_BYTE:
      case T_CHAR:
      case T_INT:
      case T_SHORT:
        regs[i].set1( int_stk_helper( j ) ); break;
      case T_LONG:
        assert( sig_bt[i+1] == T_VOID, "expecting half" );
      case T_ADDRESS: // raw pointers, like current thread, for VM calls
      case T_ARRAY:
      case T_OBJECT:
        regs[i].set2( int_stk_helper( j ) );
        break;
      case T_FLOAT:
        if ( j < 16 ) {
          // V9ism: floats go in ODD registers
          regs[i].set1(as_FloatRegister(1 + (j<<1))->as_VMReg());
        } else {
          // V9ism: floats go in ODD stack slot
          regs[i].set1(VMRegImpl::stack2reg(1 + (j<<1)));
        }
        break;
      case T_DOUBLE:
        assert( sig_bt[i+1] == T_VOID, "expecting half" );
        if ( j < 16 ) {
          // V9ism: doubles go in EVEN/ODD regs
          regs[i].set2(as_FloatRegister(j<<1)->as_VMReg());
        } else {
          // V9ism: doubles go in EVEN/ODD stack slots
          regs[i].set2(VMRegImpl::stack2reg(j<<1));
        }
        break;
      case T_VOID:  regs[i].set_bad(); j--; break; // Do not count HALVES
      default:
        ShouldNotReachHere();
      }
      if (regs[i].first()->is_stack()) {
        int off =  regs[i].first()->reg2stack();
        if (off > max_stack_slots) max_stack_slots = off;
      }
      if (regs[i].second()->is_stack()) {
        int off =  regs[i].second()->reg2stack();
        if (off > max_stack_slots) max_stack_slots = off;
      }
    }

#else // _LP64
    // V8 convention: first 6 things in O-regs, rest on stack.
    // Alignment is willy-nilly.
    for( int i=0; i<total_args_passed; i++ ) {
      switch( sig_bt[i] ) {
      case T_ADDRESS: // raw pointers, like current thread, for VM calls
      case T_ARRAY:
      case T_BOOLEAN:
      case T_BYTE:
      case T_CHAR:
      case T_FLOAT:
      case T_INT:
      case T_OBJECT:
      case T_SHORT:
        regs[i].set1( int_stk_helper( i ) );
        break;
      case T_DOUBLE:
      case T_LONG:
        assert( sig_bt[i+1] == T_VOID, "expecting half" );
        regs[i].set_pair( int_stk_helper( i+1 ), int_stk_helper( i ) );
        break;
      case T_VOID: regs[i].set_bad(); break;
      default:
        ShouldNotReachHere();
      }
      if (regs[i].first()->is_stack()) {
        int off =  regs[i].first()->reg2stack();
        if (off > max_stack_slots) max_stack_slots = off;
      }
      if (regs[i].second()->is_stack()) {
        int off =  regs[i].second()->reg2stack();
        if (off > max_stack_slots) max_stack_slots = off;
      }
    }
#endif // _LP64

  return round_to(max_stack_slots + 1, 2);

}


// ---------------------------------------------------------------------------
void SharedRuntime::save_native_result(MacroAssembler *masm, BasicType ret_type, int frame_slots) {
  switch (ret_type) {
  case T_FLOAT:
    __ stf(FloatRegisterImpl::S, F0, SP, frame_slots*VMRegImpl::stack_slot_size - 4+STACK_BIAS);
    break;
  case T_DOUBLE:
    __ stf(FloatRegisterImpl::D, F0, SP, frame_slots*VMRegImpl::stack_slot_size - 8+STACK_BIAS);
    break;
  }
}

void SharedRuntime::restore_native_result(MacroAssembler *masm, BasicType ret_type, int frame_slots) {
  switch (ret_type) {
  case T_FLOAT:
    __ ldf(FloatRegisterImpl::S, SP, frame_slots*VMRegImpl::stack_slot_size - 4+STACK_BIAS, F0);
    break;
  case T_DOUBLE:
    __ ldf(FloatRegisterImpl::D, SP, frame_slots*VMRegImpl::stack_slot_size - 8+STACK_BIAS, F0);
    break;
  }
}

// Check and forward and pending exception.  Thread is stored in
// L7_thread_cache and possibly NOT in G2_thread.  Since this is a native call, there
// is no exception handler.  We merely pop this frame off and throw the
// exception in the caller's frame.
static void check_forward_pending_exception(MacroAssembler *masm, Register Rex_oop) {
  Label L;
  __ br_null(Rex_oop, false, Assembler::pt, L);
  __ delayed()->mov(L7_thread_cache, G2_thread); // restore in case we have exception
  // Since this is a native call, we *know* the proper exception handler
  // without calling into the VM: it's the empty function.  Just pop this
  // frame and then jump to forward_exception_entry; O7 will contain the
  // native caller's return PC.
 AddressLiteral exception_entry(StubRoutines::forward_exception_entry());
  __ jump_to(exception_entry, G3_scratch);
  __ delayed()->restore();      // Pop this frame off.
  __ bind(L);
}

// A simple move of integer like type
static void simple_move32(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      __ ld(FP, reg2offset(src.first()) + STACK_BIAS, L5);
      __ st(L5, SP, reg2offset(dst.first()) + STACK_BIAS);
    } else {
      // stack to reg
      __ ld(FP, reg2offset(src.first()) + STACK_BIAS, dst.first()->as_Register());
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    __ st(src.first()->as_Register(), SP, reg2offset(dst.first()) + STACK_BIAS);
  } else {
    __ mov(src.first()->as_Register(), dst.first()->as_Register());
  }
}

// On 64 bit we will store integer like items to the stack as
// 64 bits items (sparc abi) even though java would only store
// 32bits for a parameter. On 32bit it will simply be 32 bits
// So this routine will do 32->32 on 32bit and 32->64 on 64bit
static void move32_64(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      __ ld(FP, reg2offset(src.first()) + STACK_BIAS, L5);
      __ st_ptr(L5, SP, reg2offset(dst.first()) + STACK_BIAS);
    } else {
      // stack to reg
      __ ld(FP, reg2offset(src.first()) + STACK_BIAS, dst.first()->as_Register());
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    __ st_ptr(src.first()->as_Register(), SP, reg2offset(dst.first()) + STACK_BIAS);
  } else {
    __ mov(src.first()->as_Register(), dst.first()->as_Register());
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

  if (src.first()->is_stack()) {
    // Oop is already on the stack
    Register rHandle = dst.first()->is_stack() ? L5 : dst.first()->as_Register();
    __ add(FP, reg2offset(src.first()) + STACK_BIAS, rHandle);
    __ ld_ptr(rHandle, 0, L4);
#ifdef _LP64
    __ movr( Assembler::rc_z, L4, G0, rHandle );
#else
    __ tst( L4 );
    __ movcc( Assembler::zero, false, Assembler::icc, G0, rHandle );
#endif
    if (dst.first()->is_stack()) {
      __ st_ptr(rHandle, SP, reg2offset(dst.first()) + STACK_BIAS);
    }
    int offset_in_older_frame = src.first()->reg2stack() + SharedRuntime::out_preserve_stack_slots();
    if (is_receiver) {
      *receiver_offset = (offset_in_older_frame + framesize_in_slots) * VMRegImpl::stack_slot_size;
    }
    map->set_oop(VMRegImpl::stack2reg(offset_in_older_frame + framesize_in_slots));
  } else {
    // Oop is in an input register pass we must flush it to the stack
    const Register rOop = src.first()->as_Register();
    const Register rHandle = L5;
    int oop_slot = rOop->input_number() * VMRegImpl::slots_per_word + oop_handle_offset;
    int offset = oop_slot*VMRegImpl::stack_slot_size;
    Label skip;
    __ st_ptr(rOop, SP, offset + STACK_BIAS);
    if (is_receiver) {
      *receiver_offset = oop_slot * VMRegImpl::stack_slot_size;
    }
    map->set_oop(VMRegImpl::stack2reg(oop_slot));
    __ add(SP, offset + STACK_BIAS, rHandle);
#ifdef _LP64
    __ movr( Assembler::rc_z, rOop, G0, rHandle );
#else
    __ tst( rOop );
    __ movcc( Assembler::zero, false, Assembler::icc, G0, rHandle );
#endif

    if (dst.first()->is_stack()) {
      __ st_ptr(rHandle, SP, reg2offset(dst.first()) + STACK_BIAS);
    } else {
      __ mov(rHandle, dst.first()->as_Register());
    }
  }
}

// A float arg may have to do float reg int reg conversion
static void float_move(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {
  assert(!src.second()->is_valid() && !dst.second()->is_valid(), "bad float_move");

  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack the easiest of the bunch
      __ ld(FP, reg2offset(src.first()) + STACK_BIAS, L5);
      __ st(L5, SP, reg2offset(dst.first()) + STACK_BIAS);
    } else {
      // stack to reg
      if (dst.first()->is_Register()) {
        __ ld(FP, reg2offset(src.first()) + STACK_BIAS, dst.first()->as_Register());
      } else {
        __ ldf(FloatRegisterImpl::S, FP, reg2offset(src.first()) + STACK_BIAS, dst.first()->as_FloatRegister());
      }
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    if (src.first()->is_Register()) {
      __ st(src.first()->as_Register(), SP, reg2offset(dst.first()) + STACK_BIAS);
    } else {
      __ stf(FloatRegisterImpl::S, src.first()->as_FloatRegister(), SP, reg2offset(dst.first()) + STACK_BIAS);
    }
  } else {
    // reg to reg
    if (src.first()->is_Register()) {
      if (dst.first()->is_Register()) {
        // gpr -> gpr
        __ mov(src.first()->as_Register(), dst.first()->as_Register());
      } else {
        // gpr -> fpr
        __ st(src.first()->as_Register(), FP, -4 + STACK_BIAS);
        __ ldf(FloatRegisterImpl::S, FP, -4 + STACK_BIAS, dst.first()->as_FloatRegister());
      }
    } else if (dst.first()->is_Register()) {
      // fpr -> gpr
      __ stf(FloatRegisterImpl::S, src.first()->as_FloatRegister(), FP, -4 + STACK_BIAS);
      __ ld(FP, -4 + STACK_BIAS, dst.first()->as_Register());
    } else {
      // fpr -> fpr
      // In theory these overlap but the ordering is such that this is likely a nop
      if ( src.first() != dst.first()) {
        __ fmov(FloatRegisterImpl::S, src.first()->as_FloatRegister(), dst.first()->as_FloatRegister());
      }
    }
  }
}

static void split_long_move(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {
  VMRegPair src_lo(src.first());
  VMRegPair src_hi(src.second());
  VMRegPair dst_lo(dst.first());
  VMRegPair dst_hi(dst.second());
  simple_move32(masm, src_lo, dst_lo);
  simple_move32(masm, src_hi, dst_hi);
}

// A long move
static void long_move(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {

  // Do the simple ones here else do two int moves
  if (src.is_single_phys_reg() ) {
    if (dst.is_single_phys_reg()) {
      __ mov(src.first()->as_Register(), dst.first()->as_Register());
    } else {
      // split src into two separate registers
      // Remember hi means hi address or lsw on sparc
      // Move msw to lsw
      if (dst.second()->is_reg()) {
        // MSW -> MSW
        __ srax(src.first()->as_Register(), 32, dst.first()->as_Register());
        // Now LSW -> LSW
        // this will only move lo -> lo and ignore hi
        VMRegPair split(dst.second());
        simple_move32(masm, src, split);
      } else {
        VMRegPair split(src.first(), L4->as_VMReg());
        // MSW -> MSW (lo ie. first word)
        __ srax(src.first()->as_Register(), 32, L4);
        split_long_move(masm, split, dst);
      }
    }
  } else if (dst.is_single_phys_reg()) {
    if (src.is_adjacent_aligned_on_stack(2)) {
      __ ldx(FP, reg2offset(src.first()) + STACK_BIAS, dst.first()->as_Register());
    } else {
      // dst is a single reg.
      // Remember lo is low address not msb for stack slots
      // and lo is the "real" register for registers
      // src is

      VMRegPair split;

      if (src.first()->is_reg()) {
        // src.lo (msw) is a reg, src.hi is stk/reg
        // we will move: src.hi (LSW) -> dst.lo, src.lo (MSW) -> src.lo [the MSW is in the LSW of the reg]
        split.set_pair(dst.first(), src.first());
      } else {
        // msw is stack move to L5
        // lsw is stack move to dst.lo (real reg)
        // we will move: src.hi (LSW) -> dst.lo, src.lo (MSW) -> L5
        split.set_pair(dst.first(), L5->as_VMReg());
      }

      // src.lo -> src.lo/L5, src.hi -> dst.lo (the real reg)
      // msw   -> src.lo/L5,  lsw -> dst.lo
      split_long_move(masm, src, split);

      // So dst now has the low order correct position the
      // msw half
      __ sllx(split.first()->as_Register(), 32, L5);

      const Register d = dst.first()->as_Register();
      __ or3(L5, d, d);
    }
  } else {
    // For LP64 we can probably do better.
    split_long_move(masm, src, dst);
  }
}

// A double move
static void double_move(MacroAssembler* masm, VMRegPair src, VMRegPair dst) {

  // The painful thing here is that like long_move a VMRegPair might be
  // 1: a single physical register
  // 2: two physical registers (v8)
  // 3: a physical reg [lo] and a stack slot [hi] (v8)
  // 4: two stack slots

  // Since src is always a java calling convention we know that the src pair
  // is always either all registers or all stack (and aligned?)

  // in a register [lo] and a stack slot [hi]
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack the easiest of the bunch
      // ought to be a way to do this where if alignment is ok we use ldd/std when possible
      __ ld(FP, reg2offset(src.first()) + STACK_BIAS, L5);
      __ ld(FP, reg2offset(src.second()) + STACK_BIAS, L4);
      __ st(L5, SP, reg2offset(dst.first()) + STACK_BIAS);
      __ st(L4, SP, reg2offset(dst.second()) + STACK_BIAS);
    } else {
      // stack to reg
      if (dst.second()->is_stack()) {
        // stack -> reg, stack -> stack
        __ ld(FP, reg2offset(src.second()) + STACK_BIAS, L4);
        if (dst.first()->is_Register()) {
          __ ld(FP, reg2offset(src.first()) + STACK_BIAS, dst.first()->as_Register());
        } else {
          __ ldf(FloatRegisterImpl::S, FP, reg2offset(src.first()) + STACK_BIAS, dst.first()->as_FloatRegister());
        }
        // This was missing. (very rare case)
        __ st(L4, SP, reg2offset(dst.second()) + STACK_BIAS);
      } else {
        // stack -> reg
        // Eventually optimize for alignment QQQ
        if (dst.first()->is_Register()) {
          __ ld(FP, reg2offset(src.first()) + STACK_BIAS, dst.first()->as_Register());
          __ ld(FP, reg2offset(src.second()) + STACK_BIAS, dst.second()->as_Register());
        } else {
          __ ldf(FloatRegisterImpl::S, FP, reg2offset(src.first()) + STACK_BIAS, dst.first()->as_FloatRegister());
          __ ldf(FloatRegisterImpl::S, FP, reg2offset(src.second()) + STACK_BIAS, dst.second()->as_FloatRegister());
        }
      }
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    if (src.first()->is_Register()) {
      // Eventually optimize for alignment QQQ
      __ st(src.first()->as_Register(), SP, reg2offset(dst.first()) + STACK_BIAS);
      if (src.second()->is_stack()) {
        __ ld(FP, reg2offset(src.second()) + STACK_BIAS, L4);
        __ st(L4, SP, reg2offset(dst.second()) + STACK_BIAS);
      } else {
        __ st(src.second()->as_Register(), SP, reg2offset(dst.second()) + STACK_BIAS);
      }
    } else {
      // fpr to stack
      if (src.second()->is_stack()) {
        ShouldNotReachHere();
      } else {
        // Is the stack aligned?
        if (reg2offset(dst.first()) & 0x7) {
          // No do as pairs
          __ stf(FloatRegisterImpl::S, src.first()->as_FloatRegister(), SP, reg2offset(dst.first()) + STACK_BIAS);
          __ stf(FloatRegisterImpl::S, src.second()->as_FloatRegister(), SP, reg2offset(dst.second()) + STACK_BIAS);
        } else {
          __ stf(FloatRegisterImpl::D, src.first()->as_FloatRegister(), SP, reg2offset(dst.first()) + STACK_BIAS);
        }
      }
    }
  } else {
    // reg to reg
    if (src.first()->is_Register()) {
      if (dst.first()->is_Register()) {
        // gpr -> gpr
        __ mov(src.first()->as_Register(), dst.first()->as_Register());
        __ mov(src.second()->as_Register(), dst.second()->as_Register());
      } else {
        // gpr -> fpr
        // ought to be able to do a single store
        __ stx(src.first()->as_Register(), FP, -8 + STACK_BIAS);
        __ stx(src.second()->as_Register(), FP, -4 + STACK_BIAS);
        // ought to be able to do a single load
        __ ldf(FloatRegisterImpl::S, FP, -8 + STACK_BIAS, dst.first()->as_FloatRegister());
        __ ldf(FloatRegisterImpl::S, FP, -4 + STACK_BIAS, dst.second()->as_FloatRegister());
      }
    } else if (dst.first()->is_Register()) {
      // fpr -> gpr
      // ought to be able to do a single store
      __ stf(FloatRegisterImpl::D, src.first()->as_FloatRegister(), FP, -8 + STACK_BIAS);
      // ought to be able to do a single load
      // REMEMBER first() is low address not LSB
      __ ld(FP, -8 + STACK_BIAS, dst.first()->as_Register());
      if (dst.second()->is_Register()) {
        __ ld(FP, -4 + STACK_BIAS, dst.second()->as_Register());
      } else {
        __ ld(FP, -4 + STACK_BIAS, L4);
        __ st(L4, SP, reg2offset(dst.second()) + STACK_BIAS);
      }
    } else {
      // fpr -> fpr
      // In theory these overlap but the ordering is such that this is likely a nop
      if ( src.first() != dst.first()) {
        __ fmov(FloatRegisterImpl::D, src.first()->as_FloatRegister(), dst.first()->as_FloatRegister());
      }
    }
  }
}

// Creates an inner frame if one hasn't already been created, and
// saves a copy of the thread in L7_thread_cache
static void create_inner_frame(MacroAssembler* masm, bool* already_created) {
  if (!*already_created) {
    __ save_frame(0);
    // Save thread in L7 (INNER FRAME); it crosses a bunch of VM calls below
    // Don't use save_thread because it smashes G2 and we merely want to save a
    // copy
    __ mov(G2_thread, L7_thread_cache);
    *already_created = true;
  }
}

// ---------------------------------------------------------------------------
// Generate a native wrapper for a given method.  The method takes arguments
// in the Java compiled code convention, marshals them to the native
// convention (handlizes oops, etc), transitions to native, makes the call,
// returns to java state (possibly blocking), unhandlizes any result and
// returns.
nmethod *SharedRuntime::generate_native_wrapper(MacroAssembler* masm,
                                                methodHandle method,
                                                int total_in_args,
                                                int comp_args_on_stack, // in VMRegStackSlots
                                                BasicType *in_sig_bt,
                                                VMRegPair *in_regs,
                                                BasicType ret_type) {

  // Native nmethod wrappers never take possesion of the oop arguments.
  // So the caller will gc the arguments. The only thing we need an
  // oopMap for is if the call is static
  //
  // An OopMap for lock (and class if static), and one for the VM call itself
  OopMapSet *oop_maps = new OopMapSet();
  intptr_t start = (intptr_t)__ pc();

  // First thing make an ic check to see if we should even be here
  {
    Label L;
    const Register temp_reg = G3_scratch;
    AddressLiteral ic_miss(SharedRuntime::get_ic_miss_stub());
    __ verify_oop(O0);
    __ load_klass(O0, temp_reg);
    __ cmp(temp_reg, G5_inline_cache_reg);
    __ brx(Assembler::equal, true, Assembler::pt, L);
    __ delayed()->nop();

    __ jump_to(ic_miss, temp_reg);
    __ delayed()->nop();
    __ align(CodeEntryAlignment);
    __ bind(L);
  }

  int vep_offset = ((intptr_t)__ pc()) - start;

#ifdef COMPILER1
  if (InlineObjectHash && method->intrinsic_id() == vmIntrinsics::_hashCode) {
    // Object.hashCode can pull the hashCode from the header word
    // instead of doing a full VM transition once it's been computed.
    // Since hashCode is usually polymorphic at call sites we can't do
    // this optimization at the call site without a lot of work.
    Label slowCase;
    Register receiver             = O0;
    Register result               = O0;
    Register header               = G3_scratch;
    Register hash                 = G3_scratch; // overwrite header value with hash value
    Register mask                 = G1;         // to get hash field from header

    // Read the header and build a mask to get its hash field.  Give up if the object is not unlocked.
    // We depend on hash_mask being at most 32 bits and avoid the use of
    // hash_mask_in_place because it could be larger than 32 bits in a 64-bit
    // vm: see markOop.hpp.
    __ ld_ptr(receiver, oopDesc::mark_offset_in_bytes(), header);
    __ sethi(markOopDesc::hash_mask, mask);
    __ btst(markOopDesc::unlocked_value, header);
    __ br(Assembler::zero, false, Assembler::pn, slowCase);
    if (UseBiasedLocking) {
      // Check if biased and fall through to runtime if so
      __ delayed()->nop();
      __ btst(markOopDesc::biased_lock_bit_in_place, header);
      __ br(Assembler::notZero, false, Assembler::pn, slowCase);
    }
    __ delayed()->or3(mask, markOopDesc::hash_mask & 0x3ff, mask);

    // Check for a valid (non-zero) hash code and get its value.
#ifdef _LP64
    __ srlx(header, markOopDesc::hash_shift, hash);
#else
    __ srl(header, markOopDesc::hash_shift, hash);
#endif
    __ andcc(hash, mask, hash);
    __ br(Assembler::equal, false, Assembler::pn, slowCase);
    __ delayed()->nop();

    // leaf return.
    __ retl();
    __ delayed()->mov(hash, result);
    __ bind(slowCase);
  }
#endif // COMPILER1


  // We have received a description of where all the java arg are located
  // on entry to the wrapper. We need to convert these args to where
  // the jni function will expect them. To figure out where they go
  // we convert the java signature to a C signature by inserting
  // the hidden arguments as arg[0] and possibly arg[1] (static method)

  int total_c_args = total_in_args + 1;
  if (method->is_static()) {
    total_c_args++;
  }

  BasicType* out_sig_bt = NEW_RESOURCE_ARRAY(BasicType, total_c_args);
  VMRegPair  * out_regs   = NEW_RESOURCE_ARRAY(VMRegPair,   total_c_args);

  int argc = 0;
  out_sig_bt[argc++] = T_ADDRESS;
  if (method->is_static()) {
    out_sig_bt[argc++] = T_OBJECT;
  }

  for (int i = 0; i < total_in_args ; i++ ) {
    out_sig_bt[argc++] = in_sig_bt[i];
  }

  // Now figure out where the args must be stored and how much stack space
  // they require (neglecting out_preserve_stack_slots but space for storing
  // the 1st six register arguments). It's weird see int_stk_helper.
  //
  int out_arg_slots;
  out_arg_slots = c_calling_convention(out_sig_bt, out_regs, total_c_args);

  // Compute framesize for the wrapper.  We need to handlize all oops in
  // registers. We must create space for them here that is disjoint from
  // the windowed save area because we have no control over when we might
  // flush the window again and overwrite values that gc has since modified.
  // (The live window race)
  //
  // We always just allocate 6 word for storing down these object. This allow
  // us to simply record the base and use the Ireg number to decide which
  // slot to use. (Note that the reg number is the inbound number not the
  // outbound number).
  // We must shuffle args to match the native convention, and include var-args space.

  // Calculate the total number of stack slots we will need.

  // First count the abi requirement plus all of the outgoing args
  int stack_slots = SharedRuntime::out_preserve_stack_slots() + out_arg_slots;

  // Now the space for the inbound oop handle area

  int oop_handle_offset = stack_slots;
  stack_slots += 6*VMRegImpl::slots_per_word;

  // Now any space we need for handlizing a klass if static method

  int oop_temp_slot_offset = 0;
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

  // Now a place to save return value or as a temporary for any gpr -> fpr moves
  stack_slots += 2;

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
  //      |---------------------| <- oop_handle_offset
  //      | outbound memory     |
  //      | based arguments     |
  //      |                     |
  //      |---------------------|
  //      | vararg area         |
  //      |---------------------|
  //      |                     |
  // SP-> | out_preserved_slots |
  //
  //


  // Now compute actual number of stack words we need rounding to make
  // stack properly aligned.
  stack_slots = round_to(stack_slots, 2 * VMRegImpl::slots_per_word);

  int stack_size = stack_slots * VMRegImpl::stack_slot_size;

  // Generate stack overflow check before creating frame
  __ generate_stack_overflow_check(stack_size);

  // Generate a new frame for the wrapper.
  __ save(SP, -stack_size, SP);

  int frame_complete = ((intptr_t)__ pc()) - start;

  __ verify_thread();


  //
  // We immediately shuffle the arguments so that any vm call we have to
  // make from here on out (sync slow path, jvmti, etc.) we will have
  // captured the oops from our caller and have a valid oopMap for
  // them.

  // -----------------
  // The Grand Shuffle
  //
  // Natives require 1 or 2 extra arguments over the normal ones: the JNIEnv*
  // (derived from JavaThread* which is in L7_thread_cache) and, if static,
  // the class mirror instead of a receiver.  This pretty much guarantees that
  // register layout will not match.  We ignore these extra arguments during
  // the shuffle. The shuffle is described by the two calling convention
  // vectors we have in our possession. We simply walk the java vector to
  // get the source locations and the c vector to get the destinations.
  // Because we have a new window and the argument registers are completely
  // disjoint ( I0 -> O1, I1 -> O2, ...) we have nothing to worry about
  // here.

  // This is a trick. We double the stack slots so we can claim
  // the oops in the caller's frame. Since we are sure to have
  // more args than the caller doubling is enough to make
  // sure we can capture all the incoming oop args from the
  // caller.
  //
  OopMap* map = new OopMap(stack_slots * 2, 0 /* arg_slots*/);
  int c_arg = total_c_args - 1;
  // Record sp-based slot for receiver on stack for non-static methods
  int receiver_offset = -1;

  // We move the arguments backward because the floating point registers
  // destination will always be to a register with a greater or equal register
  // number or the stack.

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

  for ( int i = total_in_args - 1; i >= 0 ; i--, c_arg-- ) {

#ifdef ASSERT
    if (in_regs[i].first()->is_Register()) {
      assert(!reg_destroyed[in_regs[i].first()->as_Register()->encoding()], "ack!");
    } else if (in_regs[i].first()->is_FloatRegister()) {
      assert(!freg_destroyed[in_regs[i].first()->as_FloatRegister()->encoding(FloatRegisterImpl::S)], "ack!");
    }
    if (out_regs[c_arg].first()->is_Register()) {
      reg_destroyed[out_regs[c_arg].first()->as_Register()->encoding()] = true;
    } else if (out_regs[c_arg].first()->is_FloatRegister()) {
      freg_destroyed[out_regs[c_arg].first()->as_FloatRegister()->encoding(FloatRegisterImpl::S)] = true;
    }
#endif /* ASSERT */

    switch (in_sig_bt[i]) {
      case T_ARRAY:
      case T_OBJECT:
        object_move(masm, map, oop_handle_offset, stack_slots, in_regs[i], out_regs[c_arg],
                    ((i == 0) && (!is_static)),
                    &receiver_offset);
        break;
      case T_VOID:
        break;

      case T_FLOAT:
        float_move(masm, in_regs[i], out_regs[c_arg]);
          break;

      case T_DOUBLE:
        assert( i + 1 < total_in_args &&
                in_sig_bt[i + 1] == T_VOID &&
                out_sig_bt[c_arg+1] == T_VOID, "bad arg list");
        double_move(masm, in_regs[i], out_regs[c_arg]);
        break;

      case T_LONG :
        long_move(masm, in_regs[i], out_regs[c_arg]);
        break;

      case T_ADDRESS: assert(false, "found T_ADDRESS in java args");

      default:
        move32_64(masm, in_regs[i], out_regs[c_arg]);
    }
  }

  // Pre-load a static method's oop into O1.  Used both by locking code and
  // the normal JNI call code.
  if (method->is_static()) {
    __ set_oop_constant(JNIHandles::make_local(Klass::cast(method->method_holder())->java_mirror()), O1);

    // Now handlize the static class mirror in O1.  It's known not-null.
    __ st_ptr(O1, SP, klass_offset + STACK_BIAS);
    map->set_oop(VMRegImpl::stack2reg(klass_slot_offset));
    __ add(SP, klass_offset + STACK_BIAS, O1);
  }


  const Register L6_handle = L6;

  if (method->is_synchronized()) {
    __ mov(O1, L6_handle);
  }

  // We have all of the arguments setup at this point. We MUST NOT touch any Oregs
  // except O6/O7. So if we must call out we must push a new frame. We immediately
  // push a new frame and flush the windows.

#ifdef _LP64
  intptr_t thepc = (intptr_t) __ pc();
  {
    address here = __ pc();
    // Call the next instruction
    __ call(here + 8, relocInfo::none);
    __ delayed()->nop();
  }
#else
  intptr_t thepc = __ load_pc_address(O7, 0);
#endif /* _LP64 */

  // We use the same pc/oopMap repeatedly when we call out
  oop_maps->add_gc_map(thepc - start, map);

  // O7 now has the pc loaded that we will use when we finally call to native.

  // Save thread in L7; it crosses a bunch of VM calls below
  // Don't use save_thread because it smashes G2 and we merely
  // want to save a copy
  __ mov(G2_thread, L7_thread_cache);


  // If we create an inner frame once is plenty
  // when we create it we must also save G2_thread
  bool inner_frame_created = false;

  // dtrace method entry support
  {
    SkipIfEqual skip_if(
      masm, G3_scratch, &DTraceMethodProbes, Assembler::zero);
    // create inner frame
    __ save_frame(0);
    __ mov(G2_thread, L7_thread_cache);
    __ set_oop_constant(JNIHandles::make_local(method()), O1);
    __ call_VM_leaf(L7_thread_cache,
         CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_entry),
         G2_thread, O1);
    __ restore();
  }

  // RedefineClasses() tracing support for obsolete method entry
  if (RC_TRACE_IN_RANGE(0x00001000, 0x00002000)) {
    // create inner frame
    __ save_frame(0);
    __ mov(G2_thread, L7_thread_cache);
    __ set_oop_constant(JNIHandles::make_local(method()), O1);
    __ call_VM_leaf(L7_thread_cache,
         CAST_FROM_FN_PTR(address, SharedRuntime::rc_trace_method_entry),
         G2_thread, O1);
    __ restore();
  }

  // We are in the jni frame unless saved_frame is true in which case
  // we are in one frame deeper (the "inner" frame). If we are in the
  // "inner" frames the args are in the Iregs and if the jni frame then
  // they are in the Oregs.
  // If we ever need to go to the VM (for locking, jvmti) then
  // we will always be in the "inner" frame.

  // Lock a synchronized method
  int lock_offset = -1;         // Set if locked
  if (method->is_synchronized()) {
    Register Roop = O1;
    const Register L3_box = L3;

    create_inner_frame(masm, &inner_frame_created);

    __ ld_ptr(I1, 0, O1);
    Label done;

    lock_offset = (lock_slot_offset * VMRegImpl::stack_slot_size);
    __ add(FP, lock_offset+STACK_BIAS, L3_box);
#ifdef ASSERT
    if (UseBiasedLocking) {
      // making the box point to itself will make it clear it went unused
      // but also be obviously invalid
      __ st_ptr(L3_box, L3_box, 0);
    }
#endif // ASSERT
    //
    // Compiler_lock_object (Roop, Rmark, Rbox, Rscratch) -- kills Rmark, Rbox, Rscratch
    //
    __ compiler_lock_object(Roop, L1,    L3_box, L2);
    __ br(Assembler::equal, false, Assembler::pt, done);
    __ delayed() -> add(FP, lock_offset+STACK_BIAS, L3_box);


    // None of the above fast optimizations worked so we have to get into the
    // slow case of monitor enter.  Inline a special case of call_VM that
    // disallows any pending_exception.
    __ mov(Roop, O0);            // Need oop in O0
    __ mov(L3_box, O1);

    // Record last_Java_sp, in case the VM code releases the JVM lock.

    __ set_last_Java_frame(FP, I7);

    // do the call
    __ call(CAST_FROM_FN_PTR(address, SharedRuntime::complete_monitor_locking_C), relocInfo::runtime_call_type);
    __ delayed()->mov(L7_thread_cache, O2);

    __ restore_thread(L7_thread_cache); // restore G2_thread
    __ reset_last_Java_frame();

#ifdef ASSERT
    { Label L;
    __ ld_ptr(G2_thread, in_bytes(Thread::pending_exception_offset()), O0);
    __ br_null(O0, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("no pending exception allowed on exit from IR::monitorenter");
    __ bind(L);
    }
#endif
    __ bind(done);
  }


  // Finally just about ready to make the JNI call

  __ flush_windows();
  if (inner_frame_created) {
    __ restore();
  } else {
    // Store only what we need from this frame
    // QQQ I think that non-v9 (like we care) we don't need these saves
    // either as the flush traps and the current window goes too.
    __ st_ptr(FP, SP, FP->sp_offset_in_saved_window()*wordSize + STACK_BIAS);
    __ st_ptr(I7, SP, I7->sp_offset_in_saved_window()*wordSize + STACK_BIAS);
  }

  // get JNIEnv* which is first argument to native

  __ add(G2_thread, in_bytes(JavaThread::jni_environment_offset()), O0);

  // Use that pc we placed in O7 a while back as the current frame anchor

  __ set_last_Java_frame(SP, O7);

  // Transition from _thread_in_Java to _thread_in_native.
  __ set(_thread_in_native, G3_scratch);
  __ st(G3_scratch, G2_thread, JavaThread::thread_state_offset());

  // We flushed the windows ages ago now mark them as flushed

  // mark windows as flushed
  __ set(JavaFrameAnchor::flushed, G3_scratch);

  Address flags(G2_thread, JavaThread::frame_anchor_offset() + JavaFrameAnchor::flags_offset());

#ifdef _LP64
  AddressLiteral dest(method->native_function());
  __ relocate(relocInfo::runtime_call_type);
  __ jumpl_to(dest, O7, O7);
#else
  __ call(method->native_function(), relocInfo::runtime_call_type);
#endif
  __ delayed()->st(G3_scratch, flags);

  __ restore_thread(L7_thread_cache); // restore G2_thread

  // Unpack native results.  For int-types, we do any needed sign-extension
  // and move things into I0.  The return value there will survive any VM
  // calls for blocking or unlocking.  An FP or OOP result (handle) is done
  // specially in the slow-path code.
  switch (ret_type) {
  case T_VOID:    break;        // Nothing to do!
  case T_FLOAT:   break;        // Got it where we want it (unless slow-path)
  case T_DOUBLE:  break;        // Got it where we want it (unless slow-path)
  // In 64 bits build result is in O0, in O0, O1 in 32bit build
  case T_LONG:
#ifndef _LP64
                  __ mov(O1, I1);
#endif
                  // Fall thru
  case T_OBJECT:                // Really a handle
  case T_ARRAY:
  case T_INT:
                  __ mov(O0, I0);
                  break;
  case T_BOOLEAN: __ subcc(G0, O0, G0); __ addc(G0, 0, I0); break; // !0 => true; 0 => false
  case T_BYTE   : __ sll(O0, 24, O0); __ sra(O0, 24, I0);   break;
  case T_CHAR   : __ sll(O0, 16, O0); __ srl(O0, 16, I0);   break; // cannot use and3, 0xFFFF too big as immediate value!
  case T_SHORT  : __ sll(O0, 16, O0); __ sra(O0, 16, I0);   break;
    break;                      // Cannot de-handlize until after reclaiming jvm_lock
  default:
    ShouldNotReachHere();
  }

  // must we block?

  // Block, if necessary, before resuming in _thread_in_Java state.
  // In order for GC to work, don't clear the last_Java_sp until after blocking.
  { Label no_block;
    AddressLiteral sync_state(SafepointSynchronize::address_of_state());

    // Switch thread to "native transition" state before reading the synchronization state.
    // This additional state is necessary because reading and testing the synchronization
    // state is not atomic w.r.t. GC, as this scenario demonstrates:
    //     Java thread A, in _thread_in_native state, loads _not_synchronized and is preempted.
    //     VM thread changes sync state to synchronizing and suspends threads for GC.
    //     Thread A is resumed to finish this native method, but doesn't block here since it
    //     didn't see any synchronization is progress, and escapes.
    __ set(_thread_in_native_trans, G3_scratch);
    __ st(G3_scratch, G2_thread, JavaThread::thread_state_offset());
    if(os::is_MP()) {
      if (UseMembar) {
        // Force this write out before the read below
        __ membar(Assembler::StoreLoad);
      } else {
        // Write serialization page so VM thread can do a pseudo remote membar.
        // We use the current thread pointer to calculate a thread specific
        // offset to write to within the page. This minimizes bus traffic
        // due to cache line collision.
        __ serialize_memory(G2_thread, G1_scratch, G3_scratch);
      }
    }
    __ load_contents(sync_state, G3_scratch);
    __ cmp(G3_scratch, SafepointSynchronize::_not_synchronized);

    Label L;
    Address suspend_state(G2_thread, JavaThread::suspend_flags_offset());
    __ br(Assembler::notEqual, false, Assembler::pn, L);
    __ delayed()->ld(suspend_state, G3_scratch);
    __ cmp(G3_scratch, 0);
    __ br(Assembler::equal, false, Assembler::pt, no_block);
    __ delayed()->nop();
    __ bind(L);

    // Block.  Save any potential method result value before the operation and
    // use a leaf call to leave the last_Java_frame setup undisturbed. Doing this
    // lets us share the oopMap we used when we went native rather the create
    // a distinct one for this pc
    //
    save_native_result(masm, ret_type, stack_slots);
    __ call_VM_leaf(L7_thread_cache,
                    CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans),
                    G2_thread);

    // Restore any method result value
    restore_native_result(masm, ret_type, stack_slots);
    __ bind(no_block);
  }

  // thread state is thread_in_native_trans. Any safepoint blocking has already
  // happened so we can now change state to _thread_in_Java.


  __ set(_thread_in_Java, G3_scratch);
  __ st(G3_scratch, G2_thread, JavaThread::thread_state_offset());


  Label no_reguard;
  __ ld(G2_thread, JavaThread::stack_guard_state_offset(), G3_scratch);
  __ cmp(G3_scratch, JavaThread::stack_guard_yellow_disabled);
  __ br(Assembler::notEqual, false, Assembler::pt, no_reguard);
  __ delayed()->nop();

    save_native_result(masm, ret_type, stack_slots);
  __ call(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages));
  __ delayed()->nop();

  __ restore_thread(L7_thread_cache); // restore G2_thread
    restore_native_result(masm, ret_type, stack_slots);

  __ bind(no_reguard);

  // Handle possible exception (will unlock if necessary)

  // native result if any is live in freg or I0 (and I1 if long and 32bit vm)

  // Unlock
  if (method->is_synchronized()) {
    Label done;
    Register I2_ex_oop = I2;
    const Register L3_box = L3;
    // Get locked oop from the handle we passed to jni
    __ ld_ptr(L6_handle, 0, L4);
    __ add(SP, lock_offset+STACK_BIAS, L3_box);
    // Must save pending exception around the slow-path VM call.  Since it's a
    // leaf call, the pending exception (if any) can be kept in a register.
    __ ld_ptr(G2_thread, in_bytes(Thread::pending_exception_offset()), I2_ex_oop);
    // Now unlock
    //                       (Roop, Rmark, Rbox,   Rscratch)
    __ compiler_unlock_object(L4,   L1,    L3_box, L2);
    __ br(Assembler::equal, false, Assembler::pt, done);
    __ delayed()-> add(SP, lock_offset+STACK_BIAS, L3_box);

    // save and restore any potential method result value around the unlocking
    // operation.  Will save in I0 (or stack for FP returns).
    save_native_result(masm, ret_type, stack_slots);

    // Must clear pending-exception before re-entering the VM.  Since this is
    // a leaf call, pending-exception-oop can be safely kept in a register.
    __ st_ptr(G0, G2_thread, in_bytes(Thread::pending_exception_offset()));

    // slow case of monitor enter.  Inline a special case of call_VM that
    // disallows any pending_exception.
    __ mov(L3_box, O1);

    __ call(CAST_FROM_FN_PTR(address, SharedRuntime::complete_monitor_unlocking_C), relocInfo::runtime_call_type);
    __ delayed()->mov(L4, O0);              // Need oop in O0

    __ restore_thread(L7_thread_cache); // restore G2_thread

#ifdef ASSERT
    { Label L;
    __ ld_ptr(G2_thread, in_bytes(Thread::pending_exception_offset()), O0);
    __ br_null(O0, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("no pending exception allowed on exit from IR::monitorexit");
    __ bind(L);
    }
#endif
    restore_native_result(masm, ret_type, stack_slots);
    // check_forward_pending_exception jump to forward_exception if any pending
    // exception is set.  The forward_exception routine expects to see the
    // exception in pending_exception and not in a register.  Kind of clumsy,
    // since all folks who branch to forward_exception must have tested
    // pending_exception first and hence have it in a register already.
    __ st_ptr(I2_ex_oop, G2_thread, in_bytes(Thread::pending_exception_offset()));
    __ bind(done);
  }

  // Tell dtrace about this method exit
  {
    SkipIfEqual skip_if(
      masm, G3_scratch, &DTraceMethodProbes, Assembler::zero);
    save_native_result(masm, ret_type, stack_slots);
    __ set_oop_constant(JNIHandles::make_local(method()), O1);
    __ call_VM_leaf(L7_thread_cache,
       CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_exit),
       G2_thread, O1);
    restore_native_result(masm, ret_type, stack_slots);
  }

  // Clear "last Java frame" SP and PC.
  __ verify_thread(); // G2_thread must be correct
  __ reset_last_Java_frame();

  // Unpack oop result
  if (ret_type == T_OBJECT || ret_type == T_ARRAY) {
      Label L;
      __ addcc(G0, I0, G0);
      __ brx(Assembler::notZero, true, Assembler::pt, L);
      __ delayed()->ld_ptr(I0, 0, I0);
      __ mov(G0, I0);
      __ bind(L);
      __ verify_oop(I0);
  }

  // reset handle block
  __ ld_ptr(G2_thread, in_bytes(JavaThread::active_handles_offset()), L5);
  __ st_ptr(G0, L5, JNIHandleBlock::top_offset_in_bytes());

  __ ld_ptr(G2_thread, in_bytes(Thread::pending_exception_offset()), G3_scratch);
  check_forward_pending_exception(masm, G3_scratch);


  // Return

#ifndef _LP64
  if (ret_type == T_LONG) {

    // Must leave proper result in O0,O1 and G1 (c2/tiered only)
    __ sllx(I0, 32, G1);          // Shift bits into high G1
    __ srl (I1, 0, I1);           // Zero extend O1 (harmless?)
    __ or3 (I1, G1, G1);          // OR 64 bits into G1
  }
#endif

  __ ret();
  __ delayed()->restore();

  __ flush();

  nmethod *nm = nmethod::new_native_nmethod(method,
                                            masm->code(),
                                            vep_offset,
                                            frame_complete,
                                            stack_slots / VMRegImpl::slots_per_word,
                                            (is_static ? in_ByteSize(klass_offset) : in_ByteSize(receiver_offset)),
                                            in_ByteSize(lock_offset),
                                            oop_maps);
  return nm;

}

#ifdef HAVE_DTRACE_H
// ---------------------------------------------------------------------------
// Generate a dtrace nmethod for a given signature.  The method takes arguments
// in the Java compiled code convention, marshals them to the native
// abi and then leaves nops at the position you would expect to call a native
// function. When the probe is enabled the nops are replaced with a trap
// instruction that dtrace inserts and the trace will cause a notification
// to dtrace.
//
// The probes are only able to take primitive types and java/lang/String as
// arguments.  No other java types are allowed. Strings are converted to utf8
// strings so that from dtrace point of view java strings are converted to C
// strings. There is an arbitrary fixed limit on the total space that a method
// can use for converting the strings. (256 chars per string in the signature).
// So any java string larger then this is truncated.

static int  fp_offset[ConcreteRegisterImpl::number_of_registers] = { 0 };
static bool offsets_initialized = false;

static VMRegPair reg64_to_VMRegPair(Register r) {
  VMRegPair ret;
  if (wordSize == 8) {
    ret.set2(r->as_VMReg());
  } else {
    ret.set_pair(r->successor()->as_VMReg(), r->as_VMReg());
  }
  return ret;
}


nmethod *SharedRuntime::generate_dtrace_nmethod(
    MacroAssembler *masm, methodHandle method) {


  // generate_dtrace_nmethod is guarded by a mutex so we are sure to
  // be single threaded in this method.
  assert(AdapterHandlerLibrary_lock->owned_by_self(), "must be");

  // Fill in the signature array, for the calling-convention call.
  int total_args_passed = method->size_of_parameters();

  BasicType* in_sig_bt  = NEW_RESOURCE_ARRAY(BasicType, total_args_passed);
  VMRegPair  *in_regs   = NEW_RESOURCE_ARRAY(VMRegPair, total_args_passed);

  // The signature we are going to use for the trap that dtrace will see
  // java/lang/String is converted. We drop "this" and any other object
  // is converted to NULL.  (A one-slot java/lang/Long object reference
  // is converted to a two-slot long, which is why we double the allocation).
  BasicType* out_sig_bt = NEW_RESOURCE_ARRAY(BasicType, total_args_passed * 2);
  VMRegPair* out_regs   = NEW_RESOURCE_ARRAY(VMRegPair, total_args_passed * 2);

  int i=0;
  int total_strings = 0;
  int first_arg_to_pass = 0;
  int total_c_args = 0;

  // Skip the receiver as dtrace doesn't want to see it
  if( !method->is_static() ) {
    in_sig_bt[i++] = T_OBJECT;
    first_arg_to_pass = 1;
  }

  SignatureStream ss(method->signature());
  for ( ; !ss.at_return_type(); ss.next()) {
    BasicType bt = ss.type();
    in_sig_bt[i++] = bt;  // Collect remaining bits of signature
    out_sig_bt[total_c_args++] = bt;
    if( bt == T_OBJECT) {
      symbolOop s = ss.as_symbol_or_null();
      if (s == vmSymbols::java_lang_String()) {
        total_strings++;
        out_sig_bt[total_c_args-1] = T_ADDRESS;
      } else if (s == vmSymbols::java_lang_Boolean() ||
                 s == vmSymbols::java_lang_Byte()) {
        out_sig_bt[total_c_args-1] = T_BYTE;
      } else if (s == vmSymbols::java_lang_Character() ||
                 s == vmSymbols::java_lang_Short()) {
        out_sig_bt[total_c_args-1] = T_SHORT;
      } else if (s == vmSymbols::java_lang_Integer() ||
                 s == vmSymbols::java_lang_Float()) {
        out_sig_bt[total_c_args-1] = T_INT;
      } else if (s == vmSymbols::java_lang_Long() ||
                 s == vmSymbols::java_lang_Double()) {
        out_sig_bt[total_c_args-1] = T_LONG;
        out_sig_bt[total_c_args++] = T_VOID;
      }
    } else if ( bt == T_LONG || bt == T_DOUBLE ) {
      in_sig_bt[i++] = T_VOID;   // Longs & doubles take 2 Java slots
      // We convert double to long
      out_sig_bt[total_c_args-1] = T_LONG;
      out_sig_bt[total_c_args++] = T_VOID;
    } else if ( bt == T_FLOAT) {
      // We convert float to int
      out_sig_bt[total_c_args-1] = T_INT;
    }
  }

  assert(i==total_args_passed, "validly parsed signature");

  // Now get the compiled-Java layout as input arguments
  int comp_args_on_stack;
  comp_args_on_stack = SharedRuntime::java_calling_convention(
      in_sig_bt, in_regs, total_args_passed, false);

  // We have received a description of where all the java arg are located
  // on entry to the wrapper. We need to convert these args to where
  // the a  native (non-jni) function would expect them. To figure out
  // where they go we convert the java signature to a C signature and remove
  // T_VOID for any long/double we might have received.


  // Now figure out where the args must be stored and how much stack space
  // they require (neglecting out_preserve_stack_slots but space for storing
  // the 1st six register arguments). It's weird see int_stk_helper.
  //
  int out_arg_slots;
  out_arg_slots = c_calling_convention(out_sig_bt, out_regs, total_c_args);

  // Calculate the total number of stack slots we will need.

  // First count the abi requirement plus all of the outgoing args
  int stack_slots = SharedRuntime::out_preserve_stack_slots() + out_arg_slots;

  // Plus a temp for possible converion of float/double/long register args

  int conversion_temp = stack_slots;
  stack_slots += 2;


  // Now space for the string(s) we must convert

  int string_locs = stack_slots;
  stack_slots += total_strings *
                   (max_dtrace_string_size / VMRegImpl::stack_slot_size);

  // Ok The space we have allocated will look like:
  //
  //
  // FP-> |                     |
  //      |---------------------|
  //      | string[n]           |
  //      |---------------------| <- string_locs[n]
  //      | string[n-1]         |
  //      |---------------------| <- string_locs[n-1]
  //      | ...                 |
  //      | ...                 |
  //      |---------------------| <- string_locs[1]
  //      | string[0]           |
  //      |---------------------| <- string_locs[0]
  //      | temp                |
  //      |---------------------| <- conversion_temp
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
  stack_slots = round_to(stack_slots, 4 * VMRegImpl::slots_per_word);

  int stack_size = stack_slots * VMRegImpl::stack_slot_size;

  intptr_t start = (intptr_t)__ pc();

  // First thing make an ic check to see if we should even be here

  {
    Label L;
    const Register temp_reg = G3_scratch;
    AddressLiteral ic_miss(SharedRuntime::get_ic_miss_stub());
    __ verify_oop(O0);
    __ ld_ptr(O0, oopDesc::klass_offset_in_bytes(), temp_reg);
    __ cmp(temp_reg, G5_inline_cache_reg);
    __ brx(Assembler::equal, true, Assembler::pt, L);
    __ delayed()->nop();

    __ jump_to(ic_miss, temp_reg);
    __ delayed()->nop();
    __ align(CodeEntryAlignment);
    __ bind(L);
  }

  int vep_offset = ((intptr_t)__ pc()) - start;


  // The instruction at the verified entry point must be 5 bytes or longer
  // because it can be patched on the fly by make_non_entrant. The stack bang
  // instruction fits that requirement.

  // Generate stack overflow check before creating frame
  __ generate_stack_overflow_check(stack_size);

  assert(((intptr_t)__ pc() - start - vep_offset) >= 5,
         "valid size for make_non_entrant");

  // Generate a new frame for the wrapper.
  __ save(SP, -stack_size, SP);

  // Frame is now completed as far a size and linkage.

  int frame_complete = ((intptr_t)__ pc()) - start;

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

  VMRegPair zero;
  const Register g0 = G0; // without this we get a compiler warning (why??)
  zero.set2(g0->as_VMReg());

  int c_arg, j_arg;

  Register conversion_off = noreg;

  for (j_arg = first_arg_to_pass, c_arg = 0 ;
       j_arg < total_args_passed ; j_arg++, c_arg++ ) {

    VMRegPair src = in_regs[j_arg];
    VMRegPair dst = out_regs[c_arg];

#ifdef ASSERT
    if (src.first()->is_Register()) {
      assert(!reg_destroyed[src.first()->as_Register()->encoding()], "ack!");
    } else if (src.first()->is_FloatRegister()) {
      assert(!freg_destroyed[src.first()->as_FloatRegister()->encoding(
                                               FloatRegisterImpl::S)], "ack!");
    }
    if (dst.first()->is_Register()) {
      reg_destroyed[dst.first()->as_Register()->encoding()] = true;
    } else if (dst.first()->is_FloatRegister()) {
      freg_destroyed[dst.first()->as_FloatRegister()->encoding(
                                                 FloatRegisterImpl::S)] = true;
    }
#endif /* ASSERT */

    switch (in_sig_bt[j_arg]) {
      case T_ARRAY:
      case T_OBJECT:
        {
          if (out_sig_bt[c_arg] == T_BYTE  || out_sig_bt[c_arg] == T_SHORT ||
              out_sig_bt[c_arg] == T_INT || out_sig_bt[c_arg] == T_LONG) {
            // need to unbox a one-slot value
            Register in_reg = L0;
            Register tmp = L2;
            if ( src.first()->is_reg() ) {
              in_reg = src.first()->as_Register();
            } else {
              assert(Assembler::is_simm13(reg2offset(src.first()) + STACK_BIAS),
                     "must be");
              __ ld_ptr(FP, reg2offset(src.first()) + STACK_BIAS, in_reg);
            }
            // If the final destination is an acceptable register
            if ( dst.first()->is_reg() ) {
              if ( dst.is_single_phys_reg() || out_sig_bt[c_arg] != T_LONG ) {
                tmp = dst.first()->as_Register();
              }
            }

            Label skipUnbox;
            if ( wordSize == 4 && out_sig_bt[c_arg] == T_LONG ) {
              __ mov(G0, tmp->successor());
            }
            __ br_null(in_reg, true, Assembler::pn, skipUnbox);
            __ delayed()->mov(G0, tmp);

            BasicType bt = out_sig_bt[c_arg];
            int box_offset = java_lang_boxing_object::value_offset_in_bytes(bt);
            switch (bt) {
                case T_BYTE:
                  __ ldub(in_reg, box_offset, tmp); break;
                case T_SHORT:
                  __ lduh(in_reg, box_offset, tmp); break;
                case T_INT:
                  __ ld(in_reg, box_offset, tmp); break;
                case T_LONG:
                  __ ld_long(in_reg, box_offset, tmp); break;
                default: ShouldNotReachHere();
            }

            __ bind(skipUnbox);
            // If tmp wasn't final destination copy to final destination
            if (tmp == L2) {
              VMRegPair tmp_as_VM = reg64_to_VMRegPair(L2);
              if (out_sig_bt[c_arg] == T_LONG) {
                long_move(masm, tmp_as_VM, dst);
              } else {
                move32_64(masm, tmp_as_VM, out_regs[c_arg]);
              }
            }
            if (out_sig_bt[c_arg] == T_LONG) {
              assert(out_sig_bt[c_arg+1] == T_VOID, "must be");
              ++c_arg; // move over the T_VOID to keep the loop indices in sync
            }
          } else if (out_sig_bt[c_arg] == T_ADDRESS) {
            Register s =
                src.first()->is_reg() ? src.first()->as_Register() : L2;
            Register d =
                dst.first()->is_reg() ? dst.first()->as_Register() : L2;

            // We store the oop now so that the conversion pass can reach
            // while in the inner frame. This will be the only store if
            // the oop is NULL.
            if (s != L2) {
              // src is register
              if (d != L2) {
                // dst is register
                __ mov(s, d);
              } else {
                assert(Assembler::is_simm13(reg2offset(dst.first()) +
                          STACK_BIAS), "must be");
                __ st_ptr(s, SP, reg2offset(dst.first()) + STACK_BIAS);
              }
            } else {
                // src not a register
                assert(Assembler::is_simm13(reg2offset(src.first()) +
                           STACK_BIAS), "must be");
                __ ld_ptr(FP, reg2offset(src.first()) + STACK_BIAS, d);
                if (d == L2) {
                  assert(Assembler::is_simm13(reg2offset(dst.first()) +
                             STACK_BIAS), "must be");
                  __ st_ptr(d, SP, reg2offset(dst.first()) + STACK_BIAS);
                }
            }
          } else if (out_sig_bt[c_arg] != T_VOID) {
            // Convert the arg to NULL
            if (dst.first()->is_reg()) {
              __ mov(G0, dst.first()->as_Register());
            } else {
              assert(Assembler::is_simm13(reg2offset(dst.first()) +
                         STACK_BIAS), "must be");
              __ st_ptr(G0, SP, reg2offset(dst.first()) + STACK_BIAS);
            }
          }
        }
        break;
      case T_VOID:
        break;

      case T_FLOAT:
        if (src.first()->is_stack()) {
          // Stack to stack/reg is simple
          move32_64(masm, src, dst);
        } else {
          if (dst.first()->is_reg()) {
            // freg -> reg
            int off =
              STACK_BIAS + conversion_temp * VMRegImpl::stack_slot_size;
            Register d = dst.first()->as_Register();
            if (Assembler::is_simm13(off)) {
              __ stf(FloatRegisterImpl::S, src.first()->as_FloatRegister(),
                     SP, off);
              __ ld(SP, off, d);
            } else {
              if (conversion_off == noreg) {
                __ set(off, L6);
                conversion_off = L6;
              }
              __ stf(FloatRegisterImpl::S, src.first()->as_FloatRegister(),
                     SP, conversion_off);
              __ ld(SP, conversion_off , d);
            }
          } else {
            // freg -> mem
            int off = STACK_BIAS + reg2offset(dst.first());
            if (Assembler::is_simm13(off)) {
              __ stf(FloatRegisterImpl::S, src.first()->as_FloatRegister(),
                     SP, off);
            } else {
              if (conversion_off == noreg) {
                __ set(off, L6);
                conversion_off = L6;
              }
              __ stf(FloatRegisterImpl::S, src.first()->as_FloatRegister(),
                     SP, conversion_off);
            }
          }
        }
        break;

      case T_DOUBLE:
        assert( j_arg + 1 < total_args_passed &&
                in_sig_bt[j_arg + 1] == T_VOID &&
                out_sig_bt[c_arg+1] == T_VOID, "bad arg list");
        if (src.first()->is_stack()) {
          // Stack to stack/reg is simple
          long_move(masm, src, dst);
        } else {
          Register d = dst.first()->is_reg() ? dst.first()->as_Register() : L2;

          // Destination could be an odd reg on 32bit in which case
          // we can't load direct to the destination.

          if (!d->is_even() && wordSize == 4) {
            d = L2;
          }
          int off = STACK_BIAS + conversion_temp * VMRegImpl::stack_slot_size;
          if (Assembler::is_simm13(off)) {
            __ stf(FloatRegisterImpl::D, src.first()->as_FloatRegister(),
                   SP, off);
            __ ld_long(SP, off, d);
          } else {
            if (conversion_off == noreg) {
              __ set(off, L6);
              conversion_off = L6;
            }
            __ stf(FloatRegisterImpl::D, src.first()->as_FloatRegister(),
                   SP, conversion_off);
            __ ld_long(SP, conversion_off, d);
          }
          if (d == L2) {
            long_move(masm, reg64_to_VMRegPair(L2), dst);
          }
        }
        break;

      case T_LONG :
        // 32bit can't do a split move of something like g1 -> O0, O1
        // so use a memory temp
        if (src.is_single_phys_reg() && wordSize == 4) {
          Register tmp = L2;
          if (dst.first()->is_reg() &&
              (wordSize == 8 || dst.first()->as_Register()->is_even())) {
            tmp = dst.first()->as_Register();
          }

          int off = STACK_BIAS + conversion_temp * VMRegImpl::stack_slot_size;
          if (Assembler::is_simm13(off)) {
            __ stx(src.first()->as_Register(), SP, off);
            __ ld_long(SP, off, tmp);
          } else {
            if (conversion_off == noreg) {
              __ set(off, L6);
              conversion_off = L6;
            }
            __ stx(src.first()->as_Register(), SP, conversion_off);
            __ ld_long(SP, conversion_off, tmp);
          }

          if (tmp == L2) {
            long_move(masm, reg64_to_VMRegPair(L2), dst);
          }
        } else {
          long_move(masm, src, dst);
        }
        break;

      case T_ADDRESS: assert(false, "found T_ADDRESS in java args");

      default:
        move32_64(masm, src, dst);
    }
  }


  // If we have any strings we must store any register based arg to the stack
  // This includes any still live xmm registers too.

  if (total_strings > 0 ) {

    // protect all the arg registers
    __ save_frame(0);
    __ mov(G2_thread, L7_thread_cache);
    const Register L2_string_off = L2;

    // Get first string offset
    __ set(string_locs * VMRegImpl::stack_slot_size, L2_string_off);

    for (c_arg = 0 ; c_arg < total_c_args ; c_arg++ ) {
      if (out_sig_bt[c_arg] == T_ADDRESS) {

        VMRegPair dst = out_regs[c_arg];
        const Register d = dst.first()->is_reg() ?
            dst.first()->as_Register()->after_save() : noreg;

        // It's a string the oop and it was already copied to the out arg
        // position
        if (d != noreg) {
          __ mov(d, O0);
        } else {
          assert(Assembler::is_simm13(reg2offset(dst.first()) + STACK_BIAS),
                 "must be");
          __ ld_ptr(FP,  reg2offset(dst.first()) + STACK_BIAS, O0);
        }
        Label skip;

        __ br_null(O0, false, Assembler::pn, skip);
        __ delayed()->add(FP, L2_string_off, O1);

        if (d != noreg) {
          __ mov(O1, d);
        } else {
          assert(Assembler::is_simm13(reg2offset(dst.first()) + STACK_BIAS),
                 "must be");
          __ st_ptr(O1, FP,  reg2offset(dst.first()) + STACK_BIAS);
        }

        __ call(CAST_FROM_FN_PTR(address, SharedRuntime::get_utf),
                relocInfo::runtime_call_type);
        __ delayed()->add(L2_string_off, max_dtrace_string_size, L2_string_off);

        __ bind(skip);

      }

    }
    __ mov(L7_thread_cache, G2_thread);
    __ restore();

  }


  // Ok now we are done. Need to place the nop that dtrace wants in order to
  // patch in the trap

  int patch_offset = ((intptr_t)__ pc()) - start;

  __ nop();


  // Return

  __ ret();
  __ delayed()->restore();

  __ flush();

  nmethod *nm = nmethod::new_dtrace_nmethod(
      method, masm->code(), vep_offset, patch_offset, frame_complete,
      stack_slots / VMRegImpl::slots_per_word);
  return nm;

}

#endif // HAVE_DTRACE_H

// this function returns the adjust size (in number of words) to a c2i adapter
// activation for use during deoptimization
int Deoptimization::last_frame_adjust(int callee_parameters, int callee_locals) {
  assert(callee_locals >= callee_parameters,
          "test and remove; got more parms than locals");
  if (callee_locals < callee_parameters)
    return 0;                   // No adjustment for negative locals
  int diff = (callee_locals - callee_parameters) * Interpreter::stackElementWords();
  return round_to(diff, WordsPerLong);
}

// "Top of Stack" slots that may be unused by the calling convention but must
// otherwise be preserved.
// On Intel these are not necessary and the value can be zero.
// On Sparc this describes the words reserved for storing a register window
// when an interrupt occurs.
uint SharedRuntime::out_preserve_stack_slots() {
  return frame::register_save_words * VMRegImpl::slots_per_word;
}

static void gen_new_frame(MacroAssembler* masm, bool deopt) {
//
// Common out the new frame generation for deopt and uncommon trap
//
  Register        G3pcs              = G3_scratch; // Array of new pcs (input)
  Register        Oreturn0           = O0;
  Register        Oreturn1           = O1;
  Register        O2UnrollBlock      = O2;
  Register        O3array            = O3;         // Array of frame sizes (input)
  Register        O4array_size       = O4;         // number of frames (input)
  Register        O7frame_size       = O7;         // number of frames (input)

  __ ld_ptr(O3array, 0, O7frame_size);
  __ sub(G0, O7frame_size, O7frame_size);
  __ save(SP, O7frame_size, SP);
  __ ld_ptr(G3pcs, 0, I7);                      // load frame's new pc

  #ifdef ASSERT
  // make sure that the frames are aligned properly
#ifndef _LP64
  __ btst(wordSize*2-1, SP);
  __ breakpoint_trap(Assembler::notZero);
#endif
  #endif

  // Deopt needs to pass some extra live values from frame to frame

  if (deopt) {
    __ mov(Oreturn0->after_save(), Oreturn0);
    __ mov(Oreturn1->after_save(), Oreturn1);
  }

  __ mov(O4array_size->after_save(), O4array_size);
  __ sub(O4array_size, 1, O4array_size);
  __ mov(O3array->after_save(), O3array);
  __ mov(O2UnrollBlock->after_save(), O2UnrollBlock);
  __ add(G3pcs, wordSize, G3pcs);               // point to next pc value

  #ifdef ASSERT
  // trash registers to show a clear pattern in backtraces
  __ set(0xDEAD0000, I0);
  __ add(I0,  2, I1);
  __ add(I0,  4, I2);
  __ add(I0,  6, I3);
  __ add(I0,  8, I4);
  // Don't touch I5 could have valuable savedSP
  __ set(0xDEADBEEF, L0);
  __ mov(L0, L1);
  __ mov(L0, L2);
  __ mov(L0, L3);
  __ mov(L0, L4);
  __ mov(L0, L5);

  // trash the return value as there is nothing to return yet
  __ set(0xDEAD0001, O7);
  #endif

  __ mov(SP, O5_savedSP);
}


static void make_new_frames(MacroAssembler* masm, bool deopt) {
  //
  // loop through the UnrollBlock info and create new frames
  //
  Register        G3pcs              = G3_scratch;
  Register        Oreturn0           = O0;
  Register        Oreturn1           = O1;
  Register        O2UnrollBlock      = O2;
  Register        O3array            = O3;
  Register        O4array_size       = O4;
  Label           loop;

  // Before we make new frames, check to see if stack is available.
  // Do this after the caller's return address is on top of stack
  if (UseStackBanging) {
    // Get total frame size for interpreted frames
    __ ld(O2UnrollBlock, Deoptimization::UnrollBlock::total_frame_sizes_offset_in_bytes(), O4);
    __ bang_stack_size(O4, O3, G3_scratch);
  }

  __ ld(O2UnrollBlock, Deoptimization::UnrollBlock::number_of_frames_offset_in_bytes(), O4array_size);
  __ ld_ptr(O2UnrollBlock, Deoptimization::UnrollBlock::frame_pcs_offset_in_bytes(), G3pcs);
  __ ld_ptr(O2UnrollBlock, Deoptimization::UnrollBlock::frame_sizes_offset_in_bytes(), O3array);

  // Adjust old interpreter frame to make space for new frame's extra java locals
  //
  // We capture the original sp for the transition frame only because it is needed in
  // order to properly calculate interpreter_sp_adjustment. Even though in real life
  // every interpreter frame captures a savedSP it is only needed at the transition
  // (fortunately). If we had to have it correct everywhere then we would need to
  // be told the sp_adjustment for each frame we create. If the frame size array
  // were to have twice the frame count entries then we could have pairs [sp_adjustment, frame_size]
  // for each frame we create and keep up the illusion every where.
  //

  __ ld(O2UnrollBlock, Deoptimization::UnrollBlock::caller_adjustment_offset_in_bytes(), O7);
  __ mov(SP, O5_savedSP);       // remember initial sender's original sp before adjustment
  __ sub(SP, O7, SP);

#ifdef ASSERT
  // make sure that there is at least one entry in the array
  __ tst(O4array_size);
  __ breakpoint_trap(Assembler::zero);
#endif

  // Now push the new interpreter frames
  __ bind(loop);

  // allocate a new frame, filling the registers

  gen_new_frame(masm, deopt);        // allocate an interpreter frame

  __ tst(O4array_size);
  __ br(Assembler::notZero, false, Assembler::pn, loop);
  __ delayed()->add(O3array, wordSize, O3array);
  __ ld_ptr(G3pcs, 0, O7);                      // load final frame new pc

}

//------------------------------generate_deopt_blob----------------------------
// Ought to generate an ideal graph & compile, but here's some SPARC ASM
// instead.
void SharedRuntime::generate_deopt_blob() {
  // allocate space for the code
  ResourceMark rm;
  // setup code generation tools
  int pad = VerifyThread ? 512 : 0;// Extra slop space for more verify code
#ifdef _LP64
  CodeBuffer buffer("deopt_blob", 2100+pad, 512);
#else
  // Measured 8/7/03 at 1212 in 32bit debug build (no VerifyThread)
  // Measured 8/7/03 at 1396 in 32bit debug build (VerifyThread)
  CodeBuffer buffer("deopt_blob", 1600+pad, 512);
#endif /* _LP64 */
  MacroAssembler* masm               = new MacroAssembler(&buffer);
  FloatRegister   Freturn0           = F0;
  Register        Greturn1           = G1;
  Register        Oreturn0           = O0;
  Register        Oreturn1           = O1;
  Register        O2UnrollBlock      = O2;
  Register        L0deopt_mode       = L0;
  Register        G4deopt_mode       = G4_scratch;
  int             frame_size_words;
  Address         saved_Freturn0_addr(FP, -sizeof(double) + STACK_BIAS);
#if !defined(_LP64) && defined(COMPILER2)
  Address         saved_Greturn1_addr(FP, -sizeof(double) -sizeof(jlong) + STACK_BIAS);
#endif
  Label           cont;

  OopMapSet *oop_maps = new OopMapSet();

  //
  // This is the entry point for code which is returning to a de-optimized
  // frame.
  // The steps taken by this frame are as follows:
  //   - push a dummy "register_save" and save the return values (O0, O1, F0/F1, G1)
  //     and all potentially live registers (at a pollpoint many registers can be live).
  //
  //   - call the C routine: Deoptimization::fetch_unroll_info (this function
  //     returns information about the number and size of interpreter frames
  //     which are equivalent to the frame which is being deoptimized)
  //   - deallocate the unpack frame, restoring only results values. Other
  //     volatile registers will now be captured in the vframeArray as needed.
  //   - deallocate the deoptimization frame
  //   - in a loop using the information returned in the previous step
  //     push new interpreter frames (take care to propagate the return
  //     values through each new frame pushed)
  //   - create a dummy "unpack_frame" and save the return values (O0, O1, F0)
  //   - call the C routine: Deoptimization::unpack_frames (this function
  //     lays out values on the interpreter frame which was just created)
  //   - deallocate the dummy unpack_frame
  //   - ensure that all the return values are correctly set and then do
  //     a return to the interpreter entry point
  //
  // Refer to the following methods for more information:
  //   - Deoptimization::fetch_unroll_info
  //   - Deoptimization::unpack_frames

  OopMap* map = NULL;

  int start = __ offset();

  // restore G2, the trampoline destroyed it
  __ get_thread();

  // On entry we have been called by the deoptimized nmethod with a call that
  // replaced the original call (or safepoint polling location) so the deoptimizing
  // pc is now in O7. Return values are still in the expected places

  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_words);
  __ ba(false, cont);
  __ delayed()->mov(Deoptimization::Unpack_deopt, L0deopt_mode);

  int exception_offset = __ offset() - start;

  // restore G2, the trampoline destroyed it
  __ get_thread();

  // On entry we have been jumped to by the exception handler (or exception_blob
  // for server).  O0 contains the exception oop and O7 contains the original
  // exception pc.  So if we push a frame here it will look to the
  // stack walking code (fetch_unroll_info) just like a normal call so
  // state will be extracted normally.

  // save exception oop in JavaThread and fall through into the
  // exception_in_tls case since they are handled in same way except
  // for where the pending exception is kept.
  __ st_ptr(Oexception, G2_thread, JavaThread::exception_oop_offset());

  //
  // Vanilla deoptimization with an exception pending in exception_oop
  //
  int exception_in_tls_offset = __ offset() - start;

  // No need to update oop_map  as each call to save_live_registers will produce identical oopmap
  (void) RegisterSaver::save_live_registers(masm, 0, &frame_size_words);

  // Restore G2_thread
  __ get_thread();

#ifdef ASSERT
  {
    // verify that there is really an exception oop in exception_oop
    Label has_exception;
    __ ld_ptr(G2_thread, JavaThread::exception_oop_offset(), Oexception);
    __ br_notnull(Oexception, false, Assembler::pt, has_exception);
    __ delayed()-> nop();
    __ stop("no exception in thread");
    __ bind(has_exception);

    // verify that there is no pending exception
    Label no_pending_exception;
    Address exception_addr(G2_thread, Thread::pending_exception_offset());
    __ ld_ptr(exception_addr, Oexception);
    __ br_null(Oexception, false, Assembler::pt, no_pending_exception);
    __ delayed()->nop();
    __ stop("must not have pending exception here");
    __ bind(no_pending_exception);
  }
#endif

  __ ba(false, cont);
  __ delayed()->mov(Deoptimization::Unpack_exception, L0deopt_mode);;

  //
  // Reexecute entry, similar to c2 uncommon trap
  //
  int reexecute_offset = __ offset() - start;

  // No need to update oop_map  as each call to save_live_registers will produce identical oopmap
  (void) RegisterSaver::save_live_registers(masm, 0, &frame_size_words);

  __ mov(Deoptimization::Unpack_reexecute, L0deopt_mode);

  __ bind(cont);

  __ set_last_Java_frame(SP, noreg);

  // do the call by hand so we can get the oopmap

  __ mov(G2_thread, L7_thread_cache);
  __ call(CAST_FROM_FN_PTR(address, Deoptimization::fetch_unroll_info), relocInfo::runtime_call_type);
  __ delayed()->mov(G2_thread, O0);

  // Set an oopmap for the call site this describes all our saved volatile registers

  oop_maps->add_gc_map( __ offset()-start, map);

  __ mov(L7_thread_cache, G2_thread);

  __ reset_last_Java_frame();

  // NOTE: we know that only O0/O1 will be reloaded by restore_result_registers
  // so this move will survive

  __ mov(L0deopt_mode, G4deopt_mode);

  __ mov(O0, O2UnrollBlock->after_save());

  RegisterSaver::restore_result_registers(masm);

  Label noException;
  __ cmp(G4deopt_mode, Deoptimization::Unpack_exception);   // Was exception pending?
  __ br(Assembler::notEqual, false, Assembler::pt, noException);
  __ delayed()->nop();

  // Move the pending exception from exception_oop to Oexception so
  // the pending exception will be picked up the interpreter.
  __ ld_ptr(G2_thread, in_bytes(JavaThread::exception_oop_offset()), Oexception);
  __ st_ptr(G0, G2_thread, in_bytes(JavaThread::exception_oop_offset()));
  __ bind(noException);

  // deallocate the deoptimization frame taking care to preserve the return values
  __ mov(Oreturn0,     Oreturn0->after_save());
  __ mov(Oreturn1,     Oreturn1->after_save());
  __ mov(O2UnrollBlock, O2UnrollBlock->after_save());
  __ restore();

  // Allocate new interpreter frame(s) and possible c2i adapter frame

  make_new_frames(masm, true);

  // push a dummy "unpack_frame" taking care of float return values and
  // call Deoptimization::unpack_frames to have the unpacker layout
  // information in the interpreter frames just created and then return
  // to the interpreter entry point
  __ save(SP, -frame_size_words*wordSize, SP);
  __ stf(FloatRegisterImpl::D, Freturn0, saved_Freturn0_addr);
#if !defined(_LP64)
#if defined(COMPILER2)
  if (!TieredCompilation) {
    // 32-bit 1-register longs return longs in G1
    __ stx(Greturn1, saved_Greturn1_addr);
  }
#endif
  __ set_last_Java_frame(SP, noreg);
  __ call_VM_leaf(L7_thread_cache, CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames), G2_thread, G4deopt_mode);
#else
  // LP64 uses g4 in set_last_Java_frame
  __ mov(G4deopt_mode, O1);
  __ set_last_Java_frame(SP, G0);
  __ call_VM_leaf(L7_thread_cache, CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames), G2_thread, O1);
#endif
  __ reset_last_Java_frame();
  __ ldf(FloatRegisterImpl::D, saved_Freturn0_addr, Freturn0);

  // In tiered we never use C2 to compile methods returning longs so
  // the result is where we expect it already.

#if !defined(_LP64) && defined(COMPILER2)
  // In 32 bit, C2 returns longs in G1 so restore the saved G1 into
  // I0/I1 if the return value is long.  In the tiered world there is
  // a mismatch between how C1 and C2 return longs compiles and so
  // currently compilation of methods which return longs is disabled
  // for C2 and so is this code.  Eventually C1 and C2 will do the
  // same thing for longs in the tiered world.
  if (!TieredCompilation) {
    Label not_long;
    __ cmp(O0,T_LONG);
    __ br(Assembler::notEqual, false, Assembler::pt, not_long);
    __ delayed()->nop();
    __ ldd(saved_Greturn1_addr,I0);
    __ bind(not_long);
  }
#endif
  __ ret();
  __ delayed()->restore();

  masm->flush();
  _deopt_blob = DeoptimizationBlob::create(&buffer, oop_maps, 0, exception_offset, reexecute_offset, frame_size_words);
  _deopt_blob->set_unpack_with_exception_in_tls_offset(exception_in_tls_offset);
}

#ifdef COMPILER2

//------------------------------generate_uncommon_trap_blob--------------------
// Ought to generate an ideal graph & compile, but here's some SPARC ASM
// instead.
void SharedRuntime::generate_uncommon_trap_blob() {
  // allocate space for the code
  ResourceMark rm;
  // setup code generation tools
  int pad = VerifyThread ? 512 : 0;
#ifdef _LP64
  CodeBuffer buffer("uncommon_trap_blob", 2700+pad, 512);
#else
  // Measured 8/7/03 at 660 in 32bit debug build (no VerifyThread)
  // Measured 8/7/03 at 1028 in 32bit debug build (VerifyThread)
  CodeBuffer buffer("uncommon_trap_blob", 2000+pad, 512);
#endif
  MacroAssembler* masm               = new MacroAssembler(&buffer);
  Register        O2UnrollBlock      = O2;
  Register        O2klass_index      = O2;

  //
  // This is the entry point for all traps the compiler takes when it thinks
  // it cannot handle further execution of compilation code. The frame is
  // deoptimized in these cases and converted into interpreter frames for
  // execution
  // The steps taken by this frame are as follows:
  //   - push a fake "unpack_frame"
  //   - call the C routine Deoptimization::uncommon_trap (this function
  //     packs the current compiled frame into vframe arrays and returns
  //     information about the number and size of interpreter frames which
  //     are equivalent to the frame which is being deoptimized)
  //   - deallocate the "unpack_frame"
  //   - deallocate the deoptimization frame
  //   - in a loop using the information returned in the previous step
  //     push interpreter frames;
  //   - create a dummy "unpack_frame"
  //   - call the C routine: Deoptimization::unpack_frames (this function
  //     lays out values on the interpreter frame which was just created)
  //   - deallocate the dummy unpack_frame
  //   - return to the interpreter entry point
  //
  //  Refer to the following methods for more information:
  //   - Deoptimization::uncommon_trap
  //   - Deoptimization::unpack_frame

  // the unloaded class index is in O0 (first parameter to this blob)

  // push a dummy "unpack_frame"
  // and call Deoptimization::uncommon_trap to pack the compiled frame into
  // vframe array and return the UnrollBlock information
  __ save_frame(0);
  __ set_last_Java_frame(SP, noreg);
  __ mov(I0, O2klass_index);
  __ call_VM_leaf(L7_thread_cache, CAST_FROM_FN_PTR(address, Deoptimization::uncommon_trap), G2_thread, O2klass_index);
  __ reset_last_Java_frame();
  __ mov(O0, O2UnrollBlock->after_save());
  __ restore();

  // deallocate the deoptimized frame taking care to preserve the return values
  __ mov(O2UnrollBlock, O2UnrollBlock->after_save());
  __ restore();

  // Allocate new interpreter frame(s) and possible c2i adapter frame

  make_new_frames(masm, false);

  // push a dummy "unpack_frame" taking care of float return values and
  // call Deoptimization::unpack_frames to have the unpacker layout
  // information in the interpreter frames just created and then return
  // to the interpreter entry point
  __ save_frame(0);
  __ set_last_Java_frame(SP, noreg);
  __ mov(Deoptimization::Unpack_uncommon_trap, O3); // indicate it is the uncommon trap case
  __ call_VM_leaf(L7_thread_cache, CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames), G2_thread, O3);
  __ reset_last_Java_frame();
  __ ret();
  __ delayed()->restore();

  masm->flush();
  _uncommon_trap_blob = UncommonTrapBlob::create(&buffer, NULL, __ total_frame_size_in_bytes(0)/wordSize);
}

#endif // COMPILER2

//------------------------------generate_handler_blob-------------------
//
// Generate a special Compile2Runtime blob that saves all registers, and sets
// up an OopMap.
//
// This blob is jumped to (via a breakpoint and the signal handler) from a
// safepoint in compiled code.  On entry to this blob, O7 contains the
// address in the original nmethod at which we should resume normal execution.
// Thus, this blob looks like a subroutine which must preserve lots of
// registers and return normally.  Note that O7 is never register-allocated,
// so it is guaranteed to be free here.
//

// The hardest part of what this blob must do is to save the 64-bit %o
// registers in the 32-bit build.  A simple 'save' turn the %o's to %i's and
// an interrupt will chop off their heads.  Making space in the caller's frame
// first will let us save the 64-bit %o's before save'ing, but we cannot hand
// the adjusted FP off to the GC stack-crawler: this will modify the caller's
// SP and mess up HIS OopMaps.  So we first adjust the caller's SP, then save
// the 64-bit %o's, then do a save, then fixup the caller's SP (our FP).
// Tricky, tricky, tricky...

static SafepointBlob* generate_handler_blob(address call_ptr, bool cause_return) {
  assert (StubRoutines::forward_exception_entry() != NULL, "must be generated before");

  // allocate space for the code
  ResourceMark rm;
  // setup code generation tools
  // Measured 8/7/03 at 896 in 32bit debug build (no VerifyThread)
  // Measured 8/7/03 at 1080 in 32bit debug build (VerifyThread)
  // even larger with TraceJumps
  int pad = TraceJumps ? 512 : 0;
  CodeBuffer buffer("handler_blob", 1600 + pad, 512);
  MacroAssembler* masm                = new MacroAssembler(&buffer);
  int             frame_size_words;
  OopMapSet *oop_maps = new OopMapSet();
  OopMap* map = NULL;

  int start = __ offset();

  // If this causes a return before the processing, then do a "restore"
  if (cause_return) {
    __ restore();
  } else {
    // Make it look like we were called via the poll
    // so that frame constructor always sees a valid return address
    __ ld_ptr(G2_thread, in_bytes(JavaThread::saved_exception_pc_offset()), O7);
    __ sub(O7, frame::pc_return_offset, O7);
  }

  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_words);

  // setup last_Java_sp (blows G4)
  __ set_last_Java_frame(SP, noreg);

  // call into the runtime to handle illegal instructions exception
  // Do not use call_VM_leaf, because we need to make a GC map at this call site.
  __ mov(G2_thread, O0);
  __ save_thread(L7_thread_cache);
  __ call(call_ptr);
  __ delayed()->nop();

  // Set an oopmap for the call site.
  // We need this not only for callee-saved registers, but also for volatile
  // registers that the compiler might be keeping live across a safepoint.

  oop_maps->add_gc_map( __ offset() - start, map);

  __ restore_thread(L7_thread_cache);
  // clear last_Java_sp
  __ reset_last_Java_frame();

  // Check for exceptions
  Label pending;

  __ ld_ptr(G2_thread, in_bytes(Thread::pending_exception_offset()), O1);
  __ tst(O1);
  __ brx(Assembler::notEqual, true, Assembler::pn, pending);
  __ delayed()->nop();

  RegisterSaver::restore_live_registers(masm);

  // We are back the the original state on entry and ready to go.

  __ retl();
  __ delayed()->nop();

  // Pending exception after the safepoint

  __ bind(pending);

  RegisterSaver::restore_live_registers(masm);

  // We are back the the original state on entry.

  // Tail-call forward_exception_entry, with the issuing PC in O7,
  // so it looks like the original nmethod called forward_exception_entry.
  __ set((intptr_t)StubRoutines::forward_exception_entry(), O0);
  __ JMP(O0, 0);
  __ delayed()->nop();

  // -------------
  // make sure all code is generated
  masm->flush();

  // return exception blob
  return SafepointBlob::create(&buffer, oop_maps, frame_size_words);
}

//
// generate_resolve_blob - call resolution (static/virtual/opt-virtual/ic-miss
//
// Generate a stub that calls into vm to find out the proper destination
// of a java call. All the argument registers are live at this point
// but since this is generic code we don't know what they are and the caller
// must do any gc of the args.
//
static RuntimeStub* generate_resolve_blob(address destination, const char* name) {
  assert (StubRoutines::forward_exception_entry() != NULL, "must be generated before");

  // allocate space for the code
  ResourceMark rm;
  // setup code generation tools
  // Measured 8/7/03 at 896 in 32bit debug build (no VerifyThread)
  // Measured 8/7/03 at 1080 in 32bit debug build (VerifyThread)
  // even larger with TraceJumps
  int pad = TraceJumps ? 512 : 0;
  CodeBuffer buffer(name, 1600 + pad, 512);
  MacroAssembler* masm                = new MacroAssembler(&buffer);
  int             frame_size_words;
  OopMapSet *oop_maps = new OopMapSet();
  OopMap* map = NULL;

  int start = __ offset();

  map = RegisterSaver::save_live_registers(masm, 0, &frame_size_words);

  int frame_complete = __ offset();

  // setup last_Java_sp (blows G4)
  __ set_last_Java_frame(SP, noreg);

  // call into the runtime to handle illegal instructions exception
  // Do not use call_VM_leaf, because we need to make a GC map at this call site.
  __ mov(G2_thread, O0);
  __ save_thread(L7_thread_cache);
  __ call(destination, relocInfo::runtime_call_type);
  __ delayed()->nop();

  // O0 contains the address we are going to jump to assuming no exception got installed

  // Set an oopmap for the call site.
  // We need this not only for callee-saved registers, but also for volatile
  // registers that the compiler might be keeping live across a safepoint.

  oop_maps->add_gc_map( __ offset() - start, map);

  __ restore_thread(L7_thread_cache);
  // clear last_Java_sp
  __ reset_last_Java_frame();

  // Check for exceptions
  Label pending;

  __ ld_ptr(G2_thread, in_bytes(Thread::pending_exception_offset()), O1);
  __ tst(O1);
  __ brx(Assembler::notEqual, true, Assembler::pn, pending);
  __ delayed()->nop();

  // get the returned methodOop

  __ get_vm_result(G5_method);
  __ stx(G5_method, SP, RegisterSaver::G5_offset()+STACK_BIAS);

  // O0 is where we want to jump, overwrite G3 which is saved and scratch

  __ stx(O0, SP, RegisterSaver::G3_offset()+STACK_BIAS);

  RegisterSaver::restore_live_registers(masm);

  // We are back the the original state on entry and ready to go.

  __ JMP(G3, 0);
  __ delayed()->nop();

  // Pending exception after the safepoint

  __ bind(pending);

  RegisterSaver::restore_live_registers(masm);

  // We are back the the original state on entry.

  // Tail-call forward_exception_entry, with the issuing PC in O7,
  // so it looks like the original nmethod called forward_exception_entry.
  __ set((intptr_t)StubRoutines::forward_exception_entry(), O0);
  __ JMP(O0, 0);
  __ delayed()->nop();

  // -------------
  // make sure all code is generated
  masm->flush();

  // return the  blob
  // frame_size_words or bytes??
  return RuntimeStub::new_runtime_stub(name, &buffer, frame_complete, frame_size_words, oop_maps, true);
}

void SharedRuntime::generate_stubs() {

  _wrong_method_blob = generate_resolve_blob(CAST_FROM_FN_PTR(address, SharedRuntime::handle_wrong_method),
                                             "wrong_method_stub");

  _ic_miss_blob = generate_resolve_blob(CAST_FROM_FN_PTR(address, SharedRuntime::handle_wrong_method_ic_miss),
                                        "ic_miss_stub");

  _resolve_opt_virtual_call_blob = generate_resolve_blob(CAST_FROM_FN_PTR(address, SharedRuntime::resolve_opt_virtual_call_C),
                                        "resolve_opt_virtual_call");

  _resolve_virtual_call_blob = generate_resolve_blob(CAST_FROM_FN_PTR(address, SharedRuntime::resolve_virtual_call_C),
                                        "resolve_virtual_call");

  _resolve_static_call_blob = generate_resolve_blob(CAST_FROM_FN_PTR(address, SharedRuntime::resolve_static_call_C),
                                        "resolve_static_call");

  _polling_page_safepoint_handler_blob =
    generate_handler_blob(CAST_FROM_FN_PTR(address,
                   SafepointSynchronize::handle_polling_page_exception), false);

  _polling_page_return_handler_blob =
    generate_handler_blob(CAST_FROM_FN_PTR(address,
                   SafepointSynchronize::handle_polling_page_exception), true);

  generate_deopt_blob();

#ifdef COMPILER2
  generate_uncommon_trap_blob();
#endif // COMPILER2
}
