/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_interp_masm_sparc.cpp.incl"

#ifndef CC_INTERP
#ifndef FAST_DISPATCH
#define FAST_DISPATCH 1
#endif
#undef FAST_DISPATCH

// Implementation of InterpreterMacroAssembler

// This file specializes the assember with interpreter-specific macros

const Address InterpreterMacroAssembler::l_tmp(FP, (frame::interpreter_frame_l_scratch_fp_offset * wordSize) + STACK_BIAS);
const Address InterpreterMacroAssembler::d_tmp(FP, (frame::interpreter_frame_d_scratch_fp_offset * wordSize) + STACK_BIAS);

#else // CC_INTERP
#ifndef STATE
#define STATE(field_name) Lstate, in_bytes(byte_offset_of(BytecodeInterpreter, field_name))
#endif // STATE

#endif // CC_INTERP

void InterpreterMacroAssembler::compute_extra_locals_size_in_bytes(Register args_size, Register locals_size, Register delta) {
  // Note: this algorithm is also used by C1's OSR entry sequence.
  // Any changes should also be applied to CodeEmitter::emit_osr_entry().
  assert_different_registers(args_size, locals_size);
  // max_locals*2 for TAGS.  Assumes that args_size has already been adjusted.
  subcc(locals_size, args_size, delta);// extra space for non-arguments locals in words
  // Use br/mov combination because it works on both V8 and V9 and is
  // faster.
  Label skip_move;
  br(Assembler::negative, true, Assembler::pt, skip_move);
  delayed()->mov(G0, delta);
  bind(skip_move);
  round_to(delta, WordsPerLong);       // make multiple of 2 (SP must be 2-word aligned)
  sll(delta, LogBytesPerWord, delta);  // extra space for locals in bytes
}

#ifndef CC_INTERP

// Dispatch code executed in the prolog of a bytecode which does not do it's
// own dispatch. The dispatch address is computed and placed in IdispatchAddress
void InterpreterMacroAssembler::dispatch_prolog(TosState state, int bcp_incr) {
  assert_not_delayed();
#ifdef FAST_DISPATCH
  // FAST_DISPATCH and ProfileInterpreter are mutually exclusive since
  // they both use I2.
  assert(!ProfileInterpreter, "FAST_DISPATCH and +ProfileInterpreter are mutually exclusive");
  ldub(Lbcp, bcp_incr, Lbyte_code);                     // load next bytecode
  add(Lbyte_code, Interpreter::distance_from_dispatch_table(state), Lbyte_code);
                                                        // add offset to correct dispatch table
  sll(Lbyte_code, LogBytesPerWord, Lbyte_code);         // multiply by wordSize
  ld_ptr(IdispatchTables, Lbyte_code, IdispatchAddress);// get entry addr
#else
  ldub( Lbcp, bcp_incr, Lbyte_code);                    // load next bytecode
  // dispatch table to use
  AddressLiteral tbl(Interpreter::dispatch_table(state));
  sll(Lbyte_code, LogBytesPerWord, Lbyte_code);         // multiply by wordSize
  set(tbl, G3_scratch);                                 // compute addr of table
  ld_ptr(G3_scratch, Lbyte_code, IdispatchAddress);     // get entry addr
#endif
}


// Dispatch code executed in the epilog of a bytecode which does not do it's
// own dispatch. The dispatch address in IdispatchAddress is used for the
// dispatch.
void InterpreterMacroAssembler::dispatch_epilog(TosState state, int bcp_incr) {
  assert_not_delayed();
  verify_FPU(1, state);
  interp_verify_oop(Otos_i, state, __FILE__, __LINE__);
  jmp( IdispatchAddress, 0 );
  if (bcp_incr != 0)  delayed()->inc(Lbcp, bcp_incr);
  else                delayed()->nop();
}


void InterpreterMacroAssembler::dispatch_next(TosState state, int bcp_incr) {
  // %%%% consider branching to a single shared dispatch stub (for each bcp_incr)
  assert_not_delayed();
  ldub( Lbcp, bcp_incr, Lbyte_code);               // load next bytecode
  dispatch_Lbyte_code(state, Interpreter::dispatch_table(state), bcp_incr);
}


void InterpreterMacroAssembler::dispatch_next_noverify_oop(TosState state, int bcp_incr) {
  // %%%% consider branching to a single shared dispatch stub (for each bcp_incr)
  assert_not_delayed();
  ldub( Lbcp, bcp_incr, Lbyte_code);               // load next bytecode
  dispatch_Lbyte_code(state, Interpreter::dispatch_table(state), bcp_incr, false);
}


void InterpreterMacroAssembler::dispatch_via(TosState state, address* table) {
  // load current bytecode
  assert_not_delayed();
  ldub( Lbcp, 0, Lbyte_code);               // load next bytecode
  dispatch_base(state, table);
}


void InterpreterMacroAssembler::call_VM_leaf_base(
  Register java_thread,
  address  entry_point,
  int      number_of_arguments
) {
  if (!java_thread->is_valid())
    java_thread = L7_thread_cache;
  // super call
  MacroAssembler::call_VM_leaf_base(java_thread, entry_point, number_of_arguments);
}


void InterpreterMacroAssembler::call_VM_base(
  Register        oop_result,
  Register        java_thread,
  Register        last_java_sp,
  address         entry_point,
  int             number_of_arguments,
  bool            check_exception
) {
  if (!java_thread->is_valid())
    java_thread = L7_thread_cache;
  // See class ThreadInVMfromInterpreter, which assumes that the interpreter
  // takes responsibility for setting its own thread-state on call-out.
  // However, ThreadInVMfromInterpreter resets the state to "in_Java".

  //save_bcp();                                  // save bcp
  MacroAssembler::call_VM_base(oop_result, java_thread, last_java_sp, entry_point, number_of_arguments, check_exception);
  //restore_bcp();                               // restore bcp
  //restore_locals();                            // restore locals pointer
}


void InterpreterMacroAssembler::check_and_handle_popframe(Register scratch_reg) {
  if (JvmtiExport::can_pop_frame()) {
    Label L;

    // Check the "pending popframe condition" flag in the current thread
    ld(G2_thread, JavaThread::popframe_condition_offset(), scratch_reg);

    // Initiate popframe handling only if it is not already being processed.  If the flag
    // has the popframe_processing bit set, it means that this code is called *during* popframe
    // handling - we don't want to reenter.
    btst(JavaThread::popframe_pending_bit, scratch_reg);
    br(zero, false, pt, L);
    delayed()->nop();
    btst(JavaThread::popframe_processing_bit, scratch_reg);
    br(notZero, false, pt, L);
    delayed()->nop();

    // Call Interpreter::remove_activation_preserving_args_entry() to get the
    // address of the same-named entrypoint in the generated interpreter code.
    call_VM_leaf(noreg, CAST_FROM_FN_PTR(address, Interpreter::remove_activation_preserving_args_entry));

    // Jump to Interpreter::_remove_activation_preserving_args_entry
    jmpl(O0, G0, G0);
    delayed()->nop();
    bind(L);
  }
}


void InterpreterMacroAssembler::load_earlyret_value(TosState state) {
  Register thr_state = G4_scratch;
  ld_ptr(G2_thread, JavaThread::jvmti_thread_state_offset(), thr_state);
  const Address tos_addr(thr_state, JvmtiThreadState::earlyret_tos_offset());
  const Address oop_addr(thr_state, JvmtiThreadState::earlyret_oop_offset());
  const Address val_addr(thr_state, JvmtiThreadState::earlyret_value_offset());
  switch (state) {
  case ltos: ld_long(val_addr, Otos_l);                   break;
  case atos: ld_ptr(oop_addr, Otos_l);
             st_ptr(G0, oop_addr);                        break;
  case btos:                                           // fall through
  case ctos:                                           // fall through
  case stos:                                           // fall through
  case itos: ld(val_addr, Otos_l1);                       break;
  case ftos: ldf(FloatRegisterImpl::S, val_addr, Ftos_f); break;
  case dtos: ldf(FloatRegisterImpl::D, val_addr, Ftos_d); break;
  case vtos: /* nothing to do */                          break;
  default  : ShouldNotReachHere();
  }
  // Clean up tos value in the jvmti thread state
  or3(G0, ilgl, G3_scratch);
  stw(G3_scratch, tos_addr);
  st_long(G0, val_addr);
  interp_verify_oop(Otos_i, state, __FILE__, __LINE__);
}


void InterpreterMacroAssembler::check_and_handle_earlyret(Register scratch_reg) {
  if (JvmtiExport::can_force_early_return()) {
    Label L;
    Register thr_state = G3_scratch;
    ld_ptr(G2_thread, JavaThread::jvmti_thread_state_offset(), thr_state);
    tst(thr_state);
    br(zero, false, pt, L); // if (thread->jvmti_thread_state() == NULL) exit;
    delayed()->nop();

    // Initiate earlyret handling only if it is not already being processed.
    // If the flag has the earlyret_processing bit set, it means that this code
    // is called *during* earlyret handling - we don't want to reenter.
    ld(thr_state, JvmtiThreadState::earlyret_state_offset(), G4_scratch);
    cmp(G4_scratch, JvmtiThreadState::earlyret_pending);
    br(Assembler::notEqual, false, pt, L);
    delayed()->nop();

    // Call Interpreter::remove_activation_early_entry() to get the address of the
    // same-named entrypoint in the generated interpreter code
    ld(thr_state, JvmtiThreadState::earlyret_tos_offset(), Otos_l1);
    call_VM_leaf(noreg, CAST_FROM_FN_PTR(address, Interpreter::remove_activation_early_entry), Otos_l1);

    // Jump to Interpreter::_remove_activation_early_entry
    jmpl(O0, G0, G0);
    delayed()->nop();
    bind(L);
  }
}


void InterpreterMacroAssembler::super_call_VM_leaf(Register thread_cache, address entry_point, Register arg_1, Register arg_2) {
  mov(arg_1, O0);
  mov(arg_2, O1);
  MacroAssembler::call_VM_leaf_base(thread_cache, entry_point, 2);
}
#endif /* CC_INTERP */


#ifndef CC_INTERP

void InterpreterMacroAssembler::dispatch_base(TosState state, address* table) {
  assert_not_delayed();
  dispatch_Lbyte_code(state, table);
}


void InterpreterMacroAssembler::dispatch_normal(TosState state) {
  dispatch_base(state, Interpreter::normal_table(state));
}


void InterpreterMacroAssembler::dispatch_only(TosState state) {
  dispatch_base(state, Interpreter::dispatch_table(state));
}


// common code to dispatch and dispatch_only
// dispatch value in Lbyte_code and increment Lbcp

void InterpreterMacroAssembler::dispatch_Lbyte_code(TosState state, address* table, int bcp_incr, bool verify) {
  verify_FPU(1, state);
  // %%%%% maybe implement +VerifyActivationFrameSize here
  //verify_thread(); //too slow; we will just verify on method entry & exit
  if (verify) interp_verify_oop(Otos_i, state, __FILE__, __LINE__);
#ifdef FAST_DISPATCH
  if (table == Interpreter::dispatch_table(state)) {
    // use IdispatchTables
    add(Lbyte_code, Interpreter::distance_from_dispatch_table(state), Lbyte_code);
                                                        // add offset to correct dispatch table
    sll(Lbyte_code, LogBytesPerWord, Lbyte_code);       // multiply by wordSize
    ld_ptr(IdispatchTables, Lbyte_code, G3_scratch);    // get entry addr
  } else {
#endif
    // dispatch table to use
    AddressLiteral tbl(table);
    sll(Lbyte_code, LogBytesPerWord, Lbyte_code);       // multiply by wordSize
    set(tbl, G3_scratch);                               // compute addr of table
    ld_ptr(G3_scratch, Lbyte_code, G3_scratch);         // get entry addr
#ifdef FAST_DISPATCH
  }
#endif
  jmp( G3_scratch, 0 );
  if (bcp_incr != 0)  delayed()->inc(Lbcp, bcp_incr);
  else                delayed()->nop();
}


// Helpers for expression stack

// Longs and doubles are Category 2 computational types in the
// JVM specification (section 3.11.1) and take 2 expression stack or
// local slots.
// Aligning them on 32 bit with tagged stacks is hard because the code generated
// for the dup* bytecodes depends on what types are already on the stack.
// If the types are split into the two stack/local slots, that is much easier
// (and we can use 0 for non-reference tags).

// Known good alignment in _LP64 but unknown otherwise
void InterpreterMacroAssembler::load_unaligned_double(Register r1, int offset, FloatRegister d) {
  assert_not_delayed();

#ifdef _LP64
  ldf(FloatRegisterImpl::D, r1, offset, d);
#else
  ldf(FloatRegisterImpl::S, r1, offset, d);
  ldf(FloatRegisterImpl::S, r1, offset + Interpreter::stackElementSize, d->successor());
#endif
}

// Known good alignment in _LP64 but unknown otherwise
void InterpreterMacroAssembler::store_unaligned_double(FloatRegister d, Register r1, int offset) {
  assert_not_delayed();

#ifdef _LP64
  stf(FloatRegisterImpl::D, d, r1, offset);
  // store something more useful here
  debug_only(stx(G0, r1, offset+Interpreter::stackElementSize);)
#else
  stf(FloatRegisterImpl::S, d, r1, offset);
  stf(FloatRegisterImpl::S, d->successor(), r1, offset + Interpreter::stackElementSize);
#endif
}


// Known good alignment in _LP64 but unknown otherwise
void InterpreterMacroAssembler::load_unaligned_long(Register r1, int offset, Register rd) {
  assert_not_delayed();
#ifdef _LP64
  ldx(r1, offset, rd);
#else
  ld(r1, offset, rd);
  ld(r1, offset + Interpreter::stackElementSize, rd->successor());
#endif
}

// Known good alignment in _LP64 but unknown otherwise
void InterpreterMacroAssembler::store_unaligned_long(Register l, Register r1, int offset) {
  assert_not_delayed();

#ifdef _LP64
  stx(l, r1, offset);
  // store something more useful here
  debug_only(stx(G0, r1, offset+Interpreter::stackElementSize);)
#else
  st(l, r1, offset);
  st(l->successor(), r1, offset + Interpreter::stackElementSize);
#endif
}

void InterpreterMacroAssembler::pop_i(Register r) {
  assert_not_delayed();
  ld(Lesp, Interpreter::expr_offset_in_bytes(0), r);
  inc(Lesp, Interpreter::stackElementSize);
  debug_only(verify_esp(Lesp));
}

void InterpreterMacroAssembler::pop_ptr(Register r, Register scratch) {
  assert_not_delayed();
  ld_ptr(Lesp, Interpreter::expr_offset_in_bytes(0), r);
  inc(Lesp, Interpreter::stackElementSize);
  debug_only(verify_esp(Lesp));
}

void InterpreterMacroAssembler::pop_l(Register r) {
  assert_not_delayed();
  load_unaligned_long(Lesp, Interpreter::expr_offset_in_bytes(0), r);
  inc(Lesp, 2*Interpreter::stackElementSize);
  debug_only(verify_esp(Lesp));
}


void InterpreterMacroAssembler::pop_f(FloatRegister f, Register scratch) {
  assert_not_delayed();
  ldf(FloatRegisterImpl::S, Lesp, Interpreter::expr_offset_in_bytes(0), f);
  inc(Lesp, Interpreter::stackElementSize);
  debug_only(verify_esp(Lesp));
}


void InterpreterMacroAssembler::pop_d(FloatRegister f, Register scratch) {
  assert_not_delayed();
  load_unaligned_double(Lesp, Interpreter::expr_offset_in_bytes(0), f);
  inc(Lesp, 2*Interpreter::stackElementSize);
  debug_only(verify_esp(Lesp));
}


void InterpreterMacroAssembler::push_i(Register r) {
  assert_not_delayed();
  debug_only(verify_esp(Lesp));
  st(r, Lesp, 0);
  dec(Lesp, Interpreter::stackElementSize);
}

void InterpreterMacroAssembler::push_ptr(Register r) {
  assert_not_delayed();
  st_ptr(r, Lesp, 0);
  dec(Lesp, Interpreter::stackElementSize);
}

// remember: our convention for longs in SPARC is:
// O0 (Otos_l1) has high-order part in first word,
// O1 (Otos_l2) has low-order part in second word

void InterpreterMacroAssembler::push_l(Register r) {
  assert_not_delayed();
  debug_only(verify_esp(Lesp));
  // Longs are stored in memory-correct order, even if unaligned.
  int offset = -Interpreter::stackElementSize;
  store_unaligned_long(r, Lesp, offset);
  dec(Lesp, 2 * Interpreter::stackElementSize);
}


void InterpreterMacroAssembler::push_f(FloatRegister f) {
  assert_not_delayed();
  debug_only(verify_esp(Lesp));
  stf(FloatRegisterImpl::S, f, Lesp, 0);
  dec(Lesp, Interpreter::stackElementSize);
}


void InterpreterMacroAssembler::push_d(FloatRegister d)   {
  assert_not_delayed();
  debug_only(verify_esp(Lesp));
  // Longs are stored in memory-correct order, even if unaligned.
  int offset = -Interpreter::stackElementSize;
  store_unaligned_double(d, Lesp, offset);
  dec(Lesp, 2 * Interpreter::stackElementSize);
}


void InterpreterMacroAssembler::push(TosState state) {
  interp_verify_oop(Otos_i, state, __FILE__, __LINE__);
  switch (state) {
    case atos: push_ptr();            break;
    case btos: push_i();              break;
    case ctos:
    case stos: push_i();              break;
    case itos: push_i();              break;
    case ltos: push_l();              break;
    case ftos: push_f();              break;
    case dtos: push_d();              break;
    case vtos: /* nothing to do */    break;
    default  : ShouldNotReachHere();
  }
}


void InterpreterMacroAssembler::pop(TosState state) {
  switch (state) {
    case atos: pop_ptr();            break;
    case btos: pop_i();              break;
    case ctos:
    case stos: pop_i();              break;
    case itos: pop_i();              break;
    case ltos: pop_l();              break;
    case ftos: pop_f();              break;
    case dtos: pop_d();              break;
    case vtos: /* nothing to do */   break;
    default  : ShouldNotReachHere();
  }
  interp_verify_oop(Otos_i, state, __FILE__, __LINE__);
}


// Helpers for swap and dup
void InterpreterMacroAssembler::load_ptr(int n, Register val) {
  ld_ptr(Lesp, Interpreter::expr_offset_in_bytes(n), val);
}
void InterpreterMacroAssembler::store_ptr(int n, Register val) {
  st_ptr(val, Lesp, Interpreter::expr_offset_in_bytes(n));
}


void InterpreterMacroAssembler::load_receiver(Register param_count,
                                              Register recv) {
  sll(param_count, Interpreter::logStackElementSize, param_count);
  ld_ptr(Lesp, param_count, recv);                      // gets receiver Oop
}

void InterpreterMacroAssembler::empty_expression_stack() {
  // Reset Lesp.
  sub( Lmonitors, wordSize, Lesp );

  // Reset SP by subtracting more space from Lesp.
  Label done;
  verify_oop(Lmethod);
  assert(G4_scratch != Gframe_size, "Only you can prevent register aliasing!");

  // A native does not need to do this, since its callee does not change SP.
  ld(Lmethod, methodOopDesc::access_flags_offset(), Gframe_size);  // Load access flags.
  btst(JVM_ACC_NATIVE, Gframe_size);
  br(Assembler::notZero, false, Assembler::pt, done);
  delayed()->nop();

  // Compute max expression stack+register save area
  lduh(Lmethod, in_bytes(methodOopDesc::max_stack_offset()), Gframe_size);  // Load max stack.
  add( Gframe_size, frame::memory_parameter_word_sp_offset, Gframe_size );

  //
  // now set up a stack frame with the size computed above
  //
  //round_to( Gframe_size, WordsPerLong ); // -- moved down to the "and" below
  sll( Gframe_size, LogBytesPerWord, Gframe_size );
  sub( Lesp, Gframe_size, Gframe_size );
  and3( Gframe_size, -(2 * wordSize), Gframe_size );          // align SP (downwards) to an 8/16-byte boundary
  debug_only(verify_sp(Gframe_size, G4_scratch));
#ifdef _LP64
  sub(Gframe_size, STACK_BIAS, Gframe_size );
#endif
  mov(Gframe_size, SP);

  bind(done);
}


#ifdef ASSERT
void InterpreterMacroAssembler::verify_sp(Register Rsp, Register Rtemp) {
  Label Bad, OK;

  // Saved SP must be aligned.
#ifdef _LP64
  btst(2*BytesPerWord-1, Rsp);
#else
  btst(LongAlignmentMask, Rsp);
#endif
  br(Assembler::notZero, false, Assembler::pn, Bad);
  delayed()->nop();

  // Saved SP, plus register window size, must not be above FP.
  add(Rsp, frame::register_save_words * wordSize, Rtemp);
#ifdef _LP64
  sub(Rtemp, STACK_BIAS, Rtemp);  // Bias Rtemp before cmp to FP
#endif
  cmp(Rtemp, FP);
  brx(Assembler::greaterUnsigned, false, Assembler::pn, Bad);
  delayed()->nop();

  // Saved SP must not be ridiculously below current SP.
  size_t maxstack = MAX2(JavaThread::stack_size_at_create(), (size_t) 4*K*K);
  set(maxstack, Rtemp);
  sub(SP, Rtemp, Rtemp);
#ifdef _LP64
  add(Rtemp, STACK_BIAS, Rtemp);  // Unbias Rtemp before cmp to Rsp
#endif
  cmp(Rsp, Rtemp);
  brx(Assembler::lessUnsigned, false, Assembler::pn, Bad);
  delayed()->nop();

  br(Assembler::always, false, Assembler::pn, OK);
  delayed()->nop();

  bind(Bad);
  stop("on return to interpreted call, restored SP is corrupted");

  bind(OK);
}


void InterpreterMacroAssembler::verify_esp(Register Resp) {
  // about to read or write Resp[0]
  // make sure it is not in the monitors or the register save area
  Label OK1, OK2;

  cmp(Resp, Lmonitors);
  brx(Assembler::lessUnsigned, true, Assembler::pt, OK1);
  delayed()->sub(Resp, frame::memory_parameter_word_sp_offset * wordSize, Resp);
  stop("too many pops:  Lesp points into monitor area");
  bind(OK1);
#ifdef _LP64
  sub(Resp, STACK_BIAS, Resp);
#endif
  cmp(Resp, SP);
  brx(Assembler::greaterEqualUnsigned, false, Assembler::pt, OK2);
  delayed()->add(Resp, STACK_BIAS + frame::memory_parameter_word_sp_offset * wordSize, Resp);
  stop("too many pushes:  Lesp points into register window");
  bind(OK2);
}
#endif // ASSERT

// Load compiled (i2c) or interpreter entry when calling from interpreted and
// do the call. Centralized so that all interpreter calls will do the same actions.
// If jvmti single stepping is on for a thread we must not call compiled code.
void InterpreterMacroAssembler::call_from_interpreter(Register target, Register scratch, Register Rret) {

  // Assume we want to go compiled if available

  ld_ptr(G5_method, in_bytes(methodOopDesc::from_interpreted_offset()), target);

  if (JvmtiExport::can_post_interpreter_events()) {
    // JVMTI events, such as single-stepping, are implemented partly by avoiding running
    // compiled code in threads for which the event is enabled.  Check here for
    // interp_only_mode if these events CAN be enabled.
    verify_thread();
    Label skip_compiled_code;

    const Address interp_only(G2_thread, JavaThread::interp_only_mode_offset());
    ld(interp_only, scratch);
    tst(scratch);
    br(Assembler::notZero, true, Assembler::pn, skip_compiled_code);
    delayed()->ld_ptr(G5_method, in_bytes(methodOopDesc::interpreter_entry_offset()), target);
    bind(skip_compiled_code);
  }

  // the i2c_adapters need methodOop in G5_method (right? %%%)
  // do the call
#ifdef ASSERT
  {
    Label ok;
    br_notnull(target, false, Assembler::pt, ok);
    delayed()->nop();
    stop("null entry point");
    bind(ok);
  }
#endif // ASSERT

  // Adjust Rret first so Llast_SP can be same as Rret
  add(Rret, -frame::pc_return_offset, O7);
  add(Lesp, BytesPerWord, Gargs); // setup parameter pointer
  // Record SP so we can remove any stack space allocated by adapter transition
  jmp(target, 0);
  delayed()->mov(SP, Llast_SP);
}

void InterpreterMacroAssembler::if_cmp(Condition cc, bool ptr_compare) {
  assert_not_delayed();

  Label not_taken;
  if (ptr_compare) brx(cc, false, Assembler::pn, not_taken);
  else             br (cc, false, Assembler::pn, not_taken);
  delayed()->nop();

  TemplateTable::branch(false,false);

  bind(not_taken);

  profile_not_taken_branch(G3_scratch);
}


void InterpreterMacroAssembler::get_2_byte_integer_at_bcp(
                                  int         bcp_offset,
                                  Register    Rtmp,
                                  Register    Rdst,
                                  signedOrNot is_signed,
                                  setCCOrNot  should_set_CC ) {
  assert(Rtmp != Rdst, "need separate temp register");
  assert_not_delayed();
  switch (is_signed) {
   default: ShouldNotReachHere();

   case   Signed:  ldsb( Lbcp, bcp_offset, Rdst  );  break; // high byte
   case Unsigned:  ldub( Lbcp, bcp_offset, Rdst  );  break; // high byte
  }
  ldub( Lbcp, bcp_offset + 1, Rtmp ); // low byte
  sll( Rdst, BitsPerByte, Rdst);
  switch (should_set_CC ) {
   default: ShouldNotReachHere();

   case      set_CC:  orcc( Rdst, Rtmp, Rdst ); break;
   case dont_set_CC:  or3(  Rdst, Rtmp, Rdst ); break;
  }
}


void InterpreterMacroAssembler::get_4_byte_integer_at_bcp(
                                  int        bcp_offset,
                                  Register   Rtmp,
                                  Register   Rdst,
                                  setCCOrNot should_set_CC ) {
  assert(Rtmp != Rdst, "need separate temp register");
  assert_not_delayed();
  add( Lbcp, bcp_offset, Rtmp);
  andcc( Rtmp, 3, G0);
  Label aligned;
  switch (should_set_CC ) {
   default: ShouldNotReachHere();

   case      set_CC: break;
   case dont_set_CC: break;
  }

  br(Assembler::zero, true, Assembler::pn, aligned);
#ifdef _LP64
  delayed()->ldsw(Rtmp, 0, Rdst);
#else
  delayed()->ld(Rtmp, 0, Rdst);
#endif

  ldub(Lbcp, bcp_offset + 3, Rdst);
  ldub(Lbcp, bcp_offset + 2, Rtmp);  sll(Rtmp,  8, Rtmp);  or3(Rtmp, Rdst, Rdst);
  ldub(Lbcp, bcp_offset + 1, Rtmp);  sll(Rtmp, 16, Rtmp);  or3(Rtmp, Rdst, Rdst);
#ifdef _LP64
  ldsb(Lbcp, bcp_offset + 0, Rtmp);  sll(Rtmp, 24, Rtmp);
#else
  // Unsigned load is faster than signed on some implementations
  ldub(Lbcp, bcp_offset + 0, Rtmp);  sll(Rtmp, 24, Rtmp);
#endif
  or3(Rtmp, Rdst, Rdst );

  bind(aligned);
  if (should_set_CC == set_CC) tst(Rdst);
}


void InterpreterMacroAssembler::get_cache_index_at_bcp(Register cache, Register tmp,
                                                       int bcp_offset, size_t index_size) {
  assert(bcp_offset > 0, "bcp is still pointing to start of bytecode");
  if (index_size == sizeof(u2)) {
    get_2_byte_integer_at_bcp(bcp_offset, cache, tmp, Unsigned);
  } else if (index_size == sizeof(u4)) {
    assert(EnableInvokeDynamic, "giant index used only for EnableInvokeDynamic");
    get_4_byte_integer_at_bcp(bcp_offset, cache, tmp);
    assert(constantPoolCacheOopDesc::decode_secondary_index(~123) == 123, "else change next line");
    xor3(tmp, -1, tmp);  // convert to plain index
  } else if (index_size == sizeof(u1)) {
    assert(EnableMethodHandles, "tiny index used only for EnableMethodHandles");
    ldub(Lbcp, bcp_offset, tmp);
  } else {
    ShouldNotReachHere();
  }
}


void InterpreterMacroAssembler::get_cache_and_index_at_bcp(Register cache, Register tmp,
                                                           int bcp_offset, size_t index_size) {
  assert(bcp_offset > 0, "bcp is still pointing to start of bytecode");
  assert_different_registers(cache, tmp);
  assert_not_delayed();
  get_cache_index_at_bcp(cache, tmp, bcp_offset, index_size);
  // convert from field index to ConstantPoolCacheEntry index and from
  // word index to byte offset
  sll(tmp, exact_log2(in_words(ConstantPoolCacheEntry::size()) * BytesPerWord), tmp);
  add(LcpoolCache, tmp, cache);
}


void InterpreterMacroAssembler::get_cache_entry_pointer_at_bcp(Register cache, Register tmp,
                                                               int bcp_offset, size_t index_size) {
  assert(bcp_offset > 0, "bcp is still pointing to start of bytecode");
  assert_different_registers(cache, tmp);
  assert_not_delayed();
  if (index_size == sizeof(u2)) {
    get_2_byte_integer_at_bcp(bcp_offset, cache, tmp, Unsigned);
  } else {
    ShouldNotReachHere();  // other sizes not supported here
  }
              // convert from field index to ConstantPoolCacheEntry index
              // and from word index to byte offset
  sll(tmp, exact_log2(in_words(ConstantPoolCacheEntry::size()) * BytesPerWord), tmp);
              // skip past the header
  add(tmp, in_bytes(constantPoolCacheOopDesc::base_offset()), tmp);
              // construct pointer to cache entry
  add(LcpoolCache, tmp, cache);
}


// Generate a subtype check: branch to ok_is_subtype if sub_klass is
// a subtype of super_klass.  Blows registers Rsuper_klass, Rsub_klass, tmp1, tmp2.
void InterpreterMacroAssembler::gen_subtype_check(Register Rsub_klass,
                                                  Register Rsuper_klass,
                                                  Register Rtmp1,
                                                  Register Rtmp2,
                                                  Register Rtmp3,
                                                  Label &ok_is_subtype ) {
  Label not_subtype;

  // Profile the not-null value's klass.
  profile_typecheck(Rsub_klass, Rtmp1);

  check_klass_subtype_fast_path(Rsub_klass, Rsuper_klass,
                                Rtmp1, Rtmp2,
                                &ok_is_subtype, &not_subtype, NULL);

  check_klass_subtype_slow_path(Rsub_klass, Rsuper_klass,
                                Rtmp1, Rtmp2, Rtmp3, /*hack:*/ noreg,
                                &ok_is_subtype, NULL);

  bind(not_subtype);
  profile_typecheck_failed(Rtmp1);
}

// Separate these two to allow for delay slot in middle
// These are used to do a test and full jump to exception-throwing code.

// %%%%% Could possibly reoptimize this by testing to see if could use
// a single conditional branch (i.e. if span is small enough.
// If you go that route, than get rid of the split and give up
// on the delay-slot hack.

void InterpreterMacroAssembler::throw_if_not_1_icc( Condition ok_condition,
                                                    Label&    ok ) {
  assert_not_delayed();
  br(ok_condition, true, pt, ok);
  // DELAY SLOT
}

void InterpreterMacroAssembler::throw_if_not_1_xcc( Condition ok_condition,
                                                    Label&    ok ) {
  assert_not_delayed();
  bp( ok_condition, true, Assembler::xcc, pt, ok);
  // DELAY SLOT
}

void InterpreterMacroAssembler::throw_if_not_1_x( Condition ok_condition,
                                                  Label&    ok ) {
  assert_not_delayed();
  brx(ok_condition, true, pt, ok);
  // DELAY SLOT
}

void InterpreterMacroAssembler::throw_if_not_2( address  throw_entry_point,
                                                Register Rscratch,
                                                Label&   ok ) {
  assert(throw_entry_point != NULL, "entry point must be generated by now");
  AddressLiteral dest(throw_entry_point);
  jump_to(dest, Rscratch);
  delayed()->nop();
  bind(ok);
}


// And if you cannot use the delay slot, here is a shorthand:

void InterpreterMacroAssembler::throw_if_not_icc( Condition ok_condition,
                                                  address   throw_entry_point,
                                                  Register  Rscratch ) {
  Label ok;
  if (ok_condition != never) {
    throw_if_not_1_icc( ok_condition, ok);
    delayed()->nop();
  }
  throw_if_not_2( throw_entry_point, Rscratch, ok);
}
void InterpreterMacroAssembler::throw_if_not_xcc( Condition ok_condition,
                                                  address   throw_entry_point,
                                                  Register  Rscratch ) {
  Label ok;
  if (ok_condition != never) {
    throw_if_not_1_xcc( ok_condition, ok);
    delayed()->nop();
  }
  throw_if_not_2( throw_entry_point, Rscratch, ok);
}
void InterpreterMacroAssembler::throw_if_not_x( Condition ok_condition,
                                                address   throw_entry_point,
                                                Register  Rscratch ) {
  Label ok;
  if (ok_condition != never) {
    throw_if_not_1_x( ok_condition, ok);
    delayed()->nop();
  }
  throw_if_not_2( throw_entry_point, Rscratch, ok);
}

// Check that index is in range for array, then shift index by index_shift, and put arrayOop + shifted_index into res
// Note: res is still shy of address by array offset into object.

void InterpreterMacroAssembler::index_check_without_pop(Register array, Register index, int index_shift, Register tmp, Register res) {
  assert_not_delayed();

  verify_oop(array);
#ifdef _LP64
  // sign extend since tos (index) can be a 32bit value
  sra(index, G0, index);
#endif // _LP64

  // check array
  Label ptr_ok;
  tst(array);
  throw_if_not_1_x( notZero, ptr_ok );
  delayed()->ld( array, arrayOopDesc::length_offset_in_bytes(), tmp ); // check index
  throw_if_not_2( Interpreter::_throw_NullPointerException_entry, G3_scratch, ptr_ok);

  Label index_ok;
  cmp(index, tmp);
  throw_if_not_1_icc( lessUnsigned, index_ok );
  if (index_shift > 0)  delayed()->sll(index, index_shift, index);
  else                  delayed()->add(array, index, res); // addr - const offset in index
  // convention: move aberrant index into G3_scratch for exception message
  mov(index, G3_scratch);
  throw_if_not_2( Interpreter::_throw_ArrayIndexOutOfBoundsException_entry, G4_scratch, index_ok);

  // add offset if didn't do it in delay slot
  if (index_shift > 0)   add(array, index, res); // addr - const offset in index
}


void InterpreterMacroAssembler::index_check(Register array, Register index, int index_shift, Register tmp, Register res) {
  assert_not_delayed();

  // pop array
  pop_ptr(array);

  // check array
  index_check_without_pop(array, index, index_shift, tmp, res);
}


void InterpreterMacroAssembler::get_constant_pool(Register Rdst) {
  ld_ptr(Lmethod, in_bytes(methodOopDesc::constants_offset()), Rdst);
}


void InterpreterMacroAssembler::get_constant_pool_cache(Register Rdst) {
  get_constant_pool(Rdst);
  ld_ptr(Rdst, constantPoolOopDesc::cache_offset_in_bytes(), Rdst);
}


void InterpreterMacroAssembler::get_cpool_and_tags(Register Rcpool, Register Rtags) {
  get_constant_pool(Rcpool);
  ld_ptr(Rcpool, constantPoolOopDesc::tags_offset_in_bytes(), Rtags);
}


// unlock if synchronized method
//
// Unlock the receiver if this is a synchronized method.
// Unlock any Java monitors from syncronized blocks.
//
// If there are locked Java monitors
//    If throw_monitor_exception
//       throws IllegalMonitorStateException
//    Else if install_monitor_exception
//       installs IllegalMonitorStateException
//    Else
//       no error processing
void InterpreterMacroAssembler::unlock_if_synchronized_method(TosState state,
                                                              bool throw_monitor_exception,
                                                              bool install_monitor_exception) {
  Label unlocked, unlock, no_unlock;

  // get the value of _do_not_unlock_if_synchronized into G1_scratch
  const Address do_not_unlock_if_synchronized(G2_thread,
    JavaThread::do_not_unlock_if_synchronized_offset());
  ldbool(do_not_unlock_if_synchronized, G1_scratch);
  stbool(G0, do_not_unlock_if_synchronized); // reset the flag

  // check if synchronized method
  const Address access_flags(Lmethod, methodOopDesc::access_flags_offset());
  interp_verify_oop(Otos_i, state, __FILE__, __LINE__);
  push(state); // save tos
  ld(access_flags, G3_scratch); // Load access flags.
  btst(JVM_ACC_SYNCHRONIZED, G3_scratch);
  br(zero, false, pt, unlocked);
  delayed()->nop();

  // Don't unlock anything if the _do_not_unlock_if_synchronized flag
  // is set.
  tstbool(G1_scratch);
  br(Assembler::notZero, false, pn, no_unlock);
  delayed()->nop();

  // BasicObjectLock will be first in list, since this is a synchronized method. However, need
  // to check that the object has not been unlocked by an explicit monitorexit bytecode.

  //Intel: if (throw_monitor_exception) ... else ...
  // Entry already unlocked, need to throw exception
  //...

  // pass top-most monitor elem
  add( top_most_monitor(), O1 );

  ld_ptr(O1, BasicObjectLock::obj_offset_in_bytes(), G3_scratch);
  br_notnull(G3_scratch, false, pt, unlock);
  delayed()->nop();

  if (throw_monitor_exception) {
    // Entry already unlocked need to throw an exception
    MacroAssembler::call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_illegal_monitor_state_exception));
    should_not_reach_here();
  } else {
    // Monitor already unlocked during a stack unroll.
    // If requested, install an illegal_monitor_state_exception.
    // Continue with stack unrolling.
    if (install_monitor_exception) {
      MacroAssembler::call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::new_illegal_monitor_state_exception));
    }
    ba(false, unlocked);
    delayed()->nop();
  }

  bind(unlock);

  unlock_object(O1);

  bind(unlocked);

  // I0, I1: Might contain return value

  // Check that all monitors are unlocked
  { Label loop, exception, entry, restart;

    Register Rmptr   = O0;
    Register Rtemp   = O1;
    Register Rlimit  = Lmonitors;
    const jint delta = frame::interpreter_frame_monitor_size() * wordSize;
    assert( (delta & LongAlignmentMask) == 0,
            "sizeof BasicObjectLock must be even number of doublewords");

    #ifdef ASSERT
    add(top_most_monitor(), Rmptr, delta);
    { Label L;
      // ensure that Rmptr starts out above (or at) Rlimit
      cmp(Rmptr, Rlimit);
      brx(Assembler::greaterEqualUnsigned, false, pn, L);
      delayed()->nop();
      stop("monitor stack has negative size");
      bind(L);
    }
    #endif
    bind(restart);
    ba(false, entry);
    delayed()->
    add(top_most_monitor(), Rmptr, delta);      // points to current entry, starting with bottom-most entry

    // Entry is still locked, need to throw exception
    bind(exception);
    if (throw_monitor_exception) {
      MacroAssembler::call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_illegal_monitor_state_exception));
      should_not_reach_here();
    } else {
      // Stack unrolling. Unlock object and if requested, install illegal_monitor_exception.
      // Unlock does not block, so don't have to worry about the frame
      unlock_object(Rmptr);
      if (install_monitor_exception) {
        MacroAssembler::call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::new_illegal_monitor_state_exception));
      }
      ba(false, restart);
      delayed()->nop();
    }

    bind(loop);
    cmp(Rtemp, G0);                             // check if current entry is used
    brx(Assembler::notEqual, false, pn, exception);
    delayed()->
    dec(Rmptr, delta);                          // otherwise advance to next entry
    #ifdef ASSERT
    { Label L;
      // ensure that Rmptr has not somehow stepped below Rlimit
      cmp(Rmptr, Rlimit);
      brx(Assembler::greaterEqualUnsigned, false, pn, L);
      delayed()->nop();
      stop("ran off the end of the monitor stack");
      bind(L);
    }
    #endif
    bind(entry);
    cmp(Rmptr, Rlimit);                         // check if bottom reached
    brx(Assembler::notEqual, true, pn, loop);   // if not at bottom then check this entry
    delayed()->
    ld_ptr(Rmptr, BasicObjectLock::obj_offset_in_bytes() - delta, Rtemp);
  }

  bind(no_unlock);
  pop(state);
  interp_verify_oop(Otos_i, state, __FILE__, __LINE__);
}


// remove activation
//
// Unlock the receiver if this is a synchronized method.
// Unlock any Java monitors from syncronized blocks.
// Remove the activation from the stack.
//
// If there are locked Java monitors
//    If throw_monitor_exception
//       throws IllegalMonitorStateException
//    Else if install_monitor_exception
//       installs IllegalMonitorStateException
//    Else
//       no error processing
void InterpreterMacroAssembler::remove_activation(TosState state,
                                                  bool throw_monitor_exception,
                                                  bool install_monitor_exception) {

  unlock_if_synchronized_method(state, throw_monitor_exception, install_monitor_exception);

  // save result (push state before jvmti call and pop it afterwards) and notify jvmti
  notify_method_exit(false, state, NotifyJVMTI);

  interp_verify_oop(Otos_i, state, __FILE__, __LINE__);
  verify_oop(Lmethod);
  verify_thread();

  // return tos
  assert(Otos_l1 == Otos_i, "adjust code below");
  switch (state) {
#ifdef _LP64
  case ltos: mov(Otos_l, Otos_l->after_save()); break; // O0 -> I0
#else
  case ltos: mov(Otos_l2, Otos_l2->after_save()); // fall through  // O1 -> I1
#endif
  case btos:                                      // fall through
  case ctos:
  case stos:                                      // fall through
  case atos:                                      // fall through
  case itos: mov(Otos_l1, Otos_l1->after_save());    break;        // O0 -> I0
  case ftos:                                      // fall through
  case dtos:                                      // fall through
  case vtos: /* nothing to do */                     break;
  default  : ShouldNotReachHere();
  }

#if defined(COMPILER2) && !defined(_LP64)
  if (state == ltos) {
    // C2 expects long results in G1 we can't tell if we're returning to interpreted
    // or compiled so just be safe use G1 and O0/O1

    // Shift bits into high (msb) of G1
    sllx(Otos_l1->after_save(), 32, G1);
    // Zero extend low bits
    srl (Otos_l2->after_save(), 0, Otos_l2->after_save());
    or3 (Otos_l2->after_save(), G1, G1);
  }
#endif /* COMPILER2 */

}
#endif /* CC_INTERP */


// Lock object
//
// Argument - lock_reg points to the BasicObjectLock to be used for locking,
//            it must be initialized with the object to lock
void InterpreterMacroAssembler::lock_object(Register lock_reg, Register Object) {
  if (UseHeavyMonitors) {
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorenter), lock_reg);
  }
  else {
    Register obj_reg = Object;
    Register mark_reg = G4_scratch;
    Register temp_reg = G1_scratch;
    Address  lock_addr(lock_reg, BasicObjectLock::lock_offset_in_bytes());
    Address  mark_addr(obj_reg, oopDesc::mark_offset_in_bytes());
    Label    done;

    Label slow_case;

    assert_different_registers(lock_reg, obj_reg, mark_reg, temp_reg);

    // load markOop from object into mark_reg
    ld_ptr(mark_addr, mark_reg);

    if (UseBiasedLocking) {
      biased_locking_enter(obj_reg, mark_reg, temp_reg, done, &slow_case);
    }

    // get the address of basicLock on stack that will be stored in the object
    // we need a temporary register here as we do not want to clobber lock_reg
    // (cas clobbers the destination register)
    mov(lock_reg, temp_reg);
    // set mark reg to be (markOop of object | UNLOCK_VALUE)
    or3(mark_reg, markOopDesc::unlocked_value, mark_reg);
    // initialize the box  (Must happen before we update the object mark!)
    st_ptr(mark_reg, lock_addr, BasicLock::displaced_header_offset_in_bytes());
    // compare and exchange object_addr, markOop | 1, stack address of basicLock
    assert(mark_addr.disp() == 0, "cas must take a zero displacement");
    casx_under_lock(mark_addr.base(), mark_reg, temp_reg,
      (address)StubRoutines::Sparc::atomic_memory_operation_lock_addr());

    // if the compare and exchange succeeded we are done (we saw an unlocked object)
    cmp(mark_reg, temp_reg);
    brx(Assembler::equal, true, Assembler::pt, done);
    delayed()->nop();

    // We did not see an unlocked object so try the fast recursive case

    // Check if owner is self by comparing the value in the markOop of object
    // with the stack pointer
    sub(temp_reg, SP, temp_reg);
#ifdef _LP64
    sub(temp_reg, STACK_BIAS, temp_reg);
#endif
    assert(os::vm_page_size() > 0xfff, "page size too small - change the constant");

    // Composite "andcc" test:
    // (a) %sp -vs- markword proximity check, and,
    // (b) verify mark word LSBs == 0 (Stack-locked).
    //
    // FFFFF003/FFFFFFFFFFFF003 is (markOopDesc::lock_mask_in_place | -os::vm_page_size())
    // Note that the page size used for %sp proximity testing is arbitrary and is
    // unrelated to the actual MMU page size.  We use a 'logical' page size of
    // 4096 bytes.   F..FFF003 is designed to fit conveniently in the SIMM13 immediate
    // field of the andcc instruction.
    andcc (temp_reg, 0xFFFFF003, G0) ;

    // if condition is true we are done and hence we can store 0 in the displaced
    // header indicating it is a recursive lock and be done
    brx(Assembler::zero, true, Assembler::pt, done);
    delayed()->st_ptr(G0, lock_addr, BasicLock::displaced_header_offset_in_bytes());

    // none of the above fast optimizations worked so we have to get into the
    // slow case of monitor enter
    bind(slow_case);
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorenter), lock_reg);

    bind(done);
  }
}

// Unlocks an object. Used in monitorexit bytecode and remove_activation.
//
// Argument - lock_reg points to the BasicObjectLock for lock
// Throw IllegalMonitorException if object is not locked by current thread
void InterpreterMacroAssembler::unlock_object(Register lock_reg) {
  if (UseHeavyMonitors) {
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorexit), lock_reg);
  } else {
    Register obj_reg = G3_scratch;
    Register mark_reg = G4_scratch;
    Register displaced_header_reg = G1_scratch;
    Address  lockobj_addr(lock_reg, BasicObjectLock::obj_offset_in_bytes());
    Address  mark_addr(obj_reg, oopDesc::mark_offset_in_bytes());
    Label    done;

    if (UseBiasedLocking) {
      // load the object out of the BasicObjectLock
      ld_ptr(lockobj_addr, obj_reg);
      biased_locking_exit(mark_addr, mark_reg, done, true);
      st_ptr(G0, lockobj_addr);  // free entry
    }

    // Test first if we are in the fast recursive case
    Address lock_addr(lock_reg, BasicObjectLock::lock_offset_in_bytes() + BasicLock::displaced_header_offset_in_bytes());
    ld_ptr(lock_addr, displaced_header_reg);
    br_null(displaced_header_reg, true, Assembler::pn, done);
    delayed()->st_ptr(G0, lockobj_addr);  // free entry

    // See if it is still a light weight lock, if so we just unlock
    // the object and we are done

    if (!UseBiasedLocking) {
      // load the object out of the BasicObjectLock
      ld_ptr(lockobj_addr, obj_reg);
    }

    // we have the displaced header in displaced_header_reg
    // we expect to see the stack address of the basicLock in case the
    // lock is still a light weight lock (lock_reg)
    assert(mark_addr.disp() == 0, "cas must take a zero displacement");
    casx_under_lock(mark_addr.base(), lock_reg, displaced_header_reg,
      (address)StubRoutines::Sparc::atomic_memory_operation_lock_addr());
    cmp(lock_reg, displaced_header_reg);
    brx(Assembler::equal, true, Assembler::pn, done);
    delayed()->st_ptr(G0, lockobj_addr);  // free entry

    // The lock has been converted into a heavy lock and hence
    // we need to get into the slow case

    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorexit), lock_reg);

    bind(done);
  }
}

#ifndef CC_INTERP

// Get the method data pointer from the methodOop and set the
// specified register to its value.

void InterpreterMacroAssembler::set_method_data_pointer_offset(Register Roff) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  Label get_continue;

  ld_ptr(Lmethod, in_bytes(methodOopDesc::method_data_offset()), ImethodDataPtr);
  test_method_data_pointer(get_continue);
  add(ImethodDataPtr, in_bytes(methodDataOopDesc::data_offset()), ImethodDataPtr);
  if (Roff != noreg)
    // Roff contains a method data index ("mdi").  It defaults to zero.
    add(ImethodDataPtr, Roff, ImethodDataPtr);
  bind(get_continue);
}

// Set the method data pointer for the current bcp.

void InterpreterMacroAssembler::set_method_data_pointer_for_bcp() {
  assert(ProfileInterpreter, "must be profiling interpreter");
  Label zero_continue;

  // Test MDO to avoid the call if it is NULL.
  ld_ptr(Lmethod, methodOopDesc::method_data_offset(), ImethodDataPtr);
  test_method_data_pointer(zero_continue);
  call_VM_leaf(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::bcp_to_di), Lmethod, Lbcp);
  set_method_data_pointer_offset(O0);
  bind(zero_continue);
}

// Test ImethodDataPtr.  If it is null, continue at the specified label

void InterpreterMacroAssembler::test_method_data_pointer(Label& zero_continue) {
  assert(ProfileInterpreter, "must be profiling interpreter");
#ifdef _LP64
  bpr(Assembler::rc_z, false, Assembler::pn, ImethodDataPtr, zero_continue);
#else
  tst(ImethodDataPtr);
  br(Assembler::zero, false, Assembler::pn, zero_continue);
#endif
  delayed()->nop();
}

void InterpreterMacroAssembler::verify_method_data_pointer() {
  assert(ProfileInterpreter, "must be profiling interpreter");
#ifdef ASSERT
  Label verify_continue;
  test_method_data_pointer(verify_continue);

  // If the mdp is valid, it will point to a DataLayout header which is
  // consistent with the bcp.  The converse is highly probable also.
  lduh(ImethodDataPtr, in_bytes(DataLayout::bci_offset()), G3_scratch);
  ld_ptr(Lmethod, methodOopDesc::const_offset(), O5);
  add(G3_scratch, in_bytes(constMethodOopDesc::codes_offset()), G3_scratch);
  add(G3_scratch, O5, G3_scratch);
  cmp(Lbcp, G3_scratch);
  brx(Assembler::equal, false, Assembler::pt, verify_continue);

  Register temp_reg = O5;
  delayed()->mov(ImethodDataPtr, temp_reg);
  // %%% should use call_VM_leaf here?
  //call_VM_leaf(noreg, ..., Lmethod, Lbcp, ImethodDataPtr);
  save_frame_and_mov(sizeof(jdouble) / wordSize, Lmethod, O0, Lbcp, O1);
  Address d_save(FP, -sizeof(jdouble) + STACK_BIAS);
  stf(FloatRegisterImpl::D, Ftos_d, d_save);
  mov(temp_reg->after_save(), O2);
  save_thread(L7_thread_cache);
  call(CAST_FROM_FN_PTR(address, InterpreterRuntime::verify_mdp), relocInfo::none);
  delayed()->nop();
  restore_thread(L7_thread_cache);
  ldf(FloatRegisterImpl::D, d_save, Ftos_d);
  restore();
  bind(verify_continue);
#endif // ASSERT
}

void InterpreterMacroAssembler::test_invocation_counter_for_mdp(Register invocation_count,
                                                                Register cur_bcp,
                                                                Register Rtmp,
                                                                Label &profile_continue) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  // Control will flow to "profile_continue" if the counter is less than the
  // limit or if we call profile_method()

  Label done;

  // if no method data exists, and the counter is high enough, make one
#ifdef _LP64
  bpr(Assembler::rc_nz, false, Assembler::pn, ImethodDataPtr, done);
#else
  tst(ImethodDataPtr);
  br(Assembler::notZero, false, Assembler::pn, done);
#endif

  // Test to see if we should create a method data oop
  AddressLiteral profile_limit((address) &InvocationCounter::InterpreterProfileLimit);
#ifdef _LP64
  delayed()->nop();
  sethi(profile_limit, Rtmp);
#else
  delayed()->sethi(profile_limit, Rtmp);
#endif
  ld(Rtmp, profile_limit.low10(), Rtmp);
  cmp(invocation_count, Rtmp);
  br(Assembler::lessUnsigned, false, Assembler::pn, profile_continue);
  delayed()->nop();

  // Build it now.
  call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::profile_method), cur_bcp);
  set_method_data_pointer_offset(O0);
  ba(false, profile_continue);
  delayed()->nop();
  bind(done);
}

// Store a value at some constant offset from the method data pointer.

void InterpreterMacroAssembler::set_mdp_data_at(int constant, Register value) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  st_ptr(value, ImethodDataPtr, constant);
}

void InterpreterMacroAssembler::increment_mdp_data_at(Address counter,
                                                      Register bumped_count,
                                                      bool decrement) {
  assert(ProfileInterpreter, "must be profiling interpreter");

  // Load the counter.
  ld_ptr(counter, bumped_count);

  if (decrement) {
    // Decrement the register.  Set condition codes.
    subcc(bumped_count, DataLayout::counter_increment, bumped_count);

    // If the decrement causes the counter to overflow, stay negative
    Label L;
    brx(Assembler::negative, true, Assembler::pn, L);

    // Store the decremented counter, if it is still negative.
    delayed()->st_ptr(bumped_count, counter);
    bind(L);
  } else {
    // Increment the register.  Set carry flag.
    addcc(bumped_count, DataLayout::counter_increment, bumped_count);

    // If the increment causes the counter to overflow, pull back by 1.
    assert(DataLayout::counter_increment == 1, "subc works");
    subc(bumped_count, G0, bumped_count);

    // Store the incremented counter.
    st_ptr(bumped_count, counter);
  }
}

// Increment the value at some constant offset from the method data pointer.

void InterpreterMacroAssembler::increment_mdp_data_at(int constant,
                                                      Register bumped_count,
                                                      bool decrement) {
  // Locate the counter at a fixed offset from the mdp:
  Address counter(ImethodDataPtr, constant);
  increment_mdp_data_at(counter, bumped_count, decrement);
}

// Increment the value at some non-fixed (reg + constant) offset from
// the method data pointer.

void InterpreterMacroAssembler::increment_mdp_data_at(Register reg,
                                                      int constant,
                                                      Register bumped_count,
                                                      Register scratch2,
                                                      bool decrement) {
  // Add the constant to reg to get the offset.
  add(ImethodDataPtr, reg, scratch2);
  Address counter(scratch2, constant);
  increment_mdp_data_at(counter, bumped_count, decrement);
}

// Set a flag value at the current method data pointer position.
// Updates a single byte of the header, to avoid races with other header bits.

void InterpreterMacroAssembler::set_mdp_flag_at(int flag_constant,
                                                Register scratch) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  // Load the data header
  ldub(ImethodDataPtr, in_bytes(DataLayout::flags_offset()), scratch);

  // Set the flag
  or3(scratch, flag_constant, scratch);

  // Store the modified header.
  stb(scratch, ImethodDataPtr, in_bytes(DataLayout::flags_offset()));
}

// Test the location at some offset from the method data pointer.
// If it is not equal to value, branch to the not_equal_continue Label.
// Set condition codes to match the nullness of the loaded value.

void InterpreterMacroAssembler::test_mdp_data_at(int offset,
                                                 Register value,
                                                 Label& not_equal_continue,
                                                 Register scratch) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  ld_ptr(ImethodDataPtr, offset, scratch);
  cmp(value, scratch);
  brx(Assembler::notEqual, false, Assembler::pn, not_equal_continue);
  delayed()->tst(scratch);
}

// Update the method data pointer by the displacement located at some fixed
// offset from the method data pointer.

void InterpreterMacroAssembler::update_mdp_by_offset(int offset_of_disp,
                                                     Register scratch) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  ld_ptr(ImethodDataPtr, offset_of_disp, scratch);
  add(ImethodDataPtr, scratch, ImethodDataPtr);
}

// Update the method data pointer by the displacement located at the
// offset (reg + offset_of_disp).

void InterpreterMacroAssembler::update_mdp_by_offset(Register reg,
                                                     int offset_of_disp,
                                                     Register scratch) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  add(reg, offset_of_disp, scratch);
  ld_ptr(ImethodDataPtr, scratch, scratch);
  add(ImethodDataPtr, scratch, ImethodDataPtr);
}

// Update the method data pointer by a simple constant displacement.

void InterpreterMacroAssembler::update_mdp_by_constant(int constant) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  add(ImethodDataPtr, constant, ImethodDataPtr);
}

// Update the method data pointer for a _ret bytecode whose target
// was not among our cached targets.

void InterpreterMacroAssembler::update_mdp_for_ret(TosState state,
                                                   Register return_bci) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  push(state);
  st_ptr(return_bci, l_tmp);  // protect return_bci, in case it is volatile
  call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::update_mdp_for_ret), return_bci);
  ld_ptr(l_tmp, return_bci);
  pop(state);
}

// Count a taken branch in the bytecodes.

void InterpreterMacroAssembler::profile_taken_branch(Register scratch, Register bumped_count) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    // We are taking a branch.  Increment the taken count.
    increment_mdp_data_at(in_bytes(JumpData::taken_offset()), bumped_count);

    // The method data pointer needs to be updated to reflect the new target.
    update_mdp_by_offset(in_bytes(JumpData::displacement_offset()), scratch);
    bind (profile_continue);
  }
}


// Count a not-taken branch in the bytecodes.

void InterpreterMacroAssembler::profile_not_taken_branch(Register scratch) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    // We are taking a branch.  Increment the not taken count.
    increment_mdp_data_at(in_bytes(BranchData::not_taken_offset()), scratch);

    // The method data pointer needs to be updated to correspond to the
    // next bytecode.
    update_mdp_by_constant(in_bytes(BranchData::branch_data_size()));
    bind (profile_continue);
  }
}


// Count a non-virtual call in the bytecodes.

void InterpreterMacroAssembler::profile_call(Register scratch) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    // We are making a call.  Increment the count.
    increment_mdp_data_at(in_bytes(CounterData::count_offset()), scratch);

    // The method data pointer needs to be updated to reflect the new target.
    update_mdp_by_constant(in_bytes(CounterData::counter_data_size()));
    bind (profile_continue);
  }
}


// Count a final call in the bytecodes.

void InterpreterMacroAssembler::profile_final_call(Register scratch) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    // We are making a call.  Increment the count.
    increment_mdp_data_at(in_bytes(CounterData::count_offset()), scratch);

    // The method data pointer needs to be updated to reflect the new target.
    update_mdp_by_constant(in_bytes(VirtualCallData::virtual_call_data_size()));
    bind (profile_continue);
  }
}


// Count a virtual call in the bytecodes.

void InterpreterMacroAssembler::profile_virtual_call(Register receiver,
                                                     Register scratch,
                                                     bool receiver_can_be_null) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);


    Label skip_receiver_profile;
    if (receiver_can_be_null) {
      Label not_null;
      tst(receiver);
      brx(Assembler::notZero, false, Assembler::pt, not_null);
      delayed()->nop();
      // We are making a call.  Increment the count for null receiver.
      increment_mdp_data_at(in_bytes(CounterData::count_offset()), scratch);
      ba(false, skip_receiver_profile);
      delayed()->nop();
      bind(not_null);
    }

    // Record the receiver type.
    record_klass_in_profile(receiver, scratch, true);
    bind(skip_receiver_profile);

    // The method data pointer needs to be updated to reflect the new target.
    update_mdp_by_constant(in_bytes(VirtualCallData::virtual_call_data_size()));
    bind (profile_continue);
  }
}

void InterpreterMacroAssembler::record_klass_in_profile_helper(
                                        Register receiver, Register scratch,
                                        int start_row, Label& done, bool is_virtual_call) {
  if (TypeProfileWidth == 0) {
    if (is_virtual_call) {
      increment_mdp_data_at(in_bytes(CounterData::count_offset()), scratch);
    }
    return;
  }

  int last_row = VirtualCallData::row_limit() - 1;
  assert(start_row <= last_row, "must be work left to do");
  // Test this row for both the receiver and for null.
  // Take any of three different outcomes:
  //   1. found receiver => increment count and goto done
  //   2. found null => keep looking for case 1, maybe allocate this cell
  //   3. found something else => keep looking for cases 1 and 2
  // Case 3 is handled by a recursive call.
  for (int row = start_row; row <= last_row; row++) {
    Label next_test;
    bool test_for_null_also = (row == start_row);

    // See if the receiver is receiver[n].
    int recvr_offset = in_bytes(VirtualCallData::receiver_offset(row));
    test_mdp_data_at(recvr_offset, receiver, next_test, scratch);
    // delayed()->tst(scratch);

    // The receiver is receiver[n].  Increment count[n].
    int count_offset = in_bytes(VirtualCallData::receiver_count_offset(row));
    increment_mdp_data_at(count_offset, scratch);
    ba(false, done);
    delayed()->nop();
    bind(next_test);

    if (test_for_null_also) {
      Label found_null;
      // Failed the equality check on receiver[n]...  Test for null.
      if (start_row == last_row) {
        // The only thing left to do is handle the null case.
        if (is_virtual_call) {
          brx(Assembler::zero, false, Assembler::pn, found_null);
          delayed()->nop();
          // Receiver did not match any saved receiver and there is no empty row for it.
          // Increment total counter to indicate polymorphic case.
          increment_mdp_data_at(in_bytes(CounterData::count_offset()), scratch);
          ba(false, done);
          delayed()->nop();
          bind(found_null);
        } else {
          brx(Assembler::notZero, false, Assembler::pt, done);
          delayed()->nop();
        }
        break;
      }
      // Since null is rare, make it be the branch-taken case.
      brx(Assembler::zero, false, Assembler::pn, found_null);
      delayed()->nop();

      // Put all the "Case 3" tests here.
      record_klass_in_profile_helper(receiver, scratch, start_row + 1, done, is_virtual_call);

      // Found a null.  Keep searching for a matching receiver,
      // but remember that this is an empty (unused) slot.
      bind(found_null);
    }
  }

  // In the fall-through case, we found no matching receiver, but we
  // observed the receiver[start_row] is NULL.

  // Fill in the receiver field and increment the count.
  int recvr_offset = in_bytes(VirtualCallData::receiver_offset(start_row));
  set_mdp_data_at(recvr_offset, receiver);
  int count_offset = in_bytes(VirtualCallData::receiver_count_offset(start_row));
  mov(DataLayout::counter_increment, scratch);
  set_mdp_data_at(count_offset, scratch);
  if (start_row > 0) {
    ba(false, done);
    delayed()->nop();
  }
}

void InterpreterMacroAssembler::record_klass_in_profile(Register receiver,
                                                        Register scratch, bool is_virtual_call) {
  assert(ProfileInterpreter, "must be profiling");
  Label done;

  record_klass_in_profile_helper(receiver, scratch, 0, done, is_virtual_call);

  bind (done);
}


// Count a ret in the bytecodes.

void InterpreterMacroAssembler::profile_ret(TosState state,
                                            Register return_bci,
                                            Register scratch) {
  if (ProfileInterpreter) {
    Label profile_continue;
    uint row;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    // Update the total ret count.
    increment_mdp_data_at(in_bytes(CounterData::count_offset()), scratch);

    for (row = 0; row < RetData::row_limit(); row++) {
      Label next_test;

      // See if return_bci is equal to bci[n]:
      test_mdp_data_at(in_bytes(RetData::bci_offset(row)),
                       return_bci, next_test, scratch);

      // return_bci is equal to bci[n].  Increment the count.
      increment_mdp_data_at(in_bytes(RetData::bci_count_offset(row)), scratch);

      // The method data pointer needs to be updated to reflect the new target.
      update_mdp_by_offset(in_bytes(RetData::bci_displacement_offset(row)), scratch);
      ba(false, profile_continue);
      delayed()->nop();
      bind(next_test);
    }

    update_mdp_for_ret(state, return_bci);

    bind (profile_continue);
  }
}

// Profile an unexpected null in the bytecodes.
void InterpreterMacroAssembler::profile_null_seen(Register scratch) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    set_mdp_flag_at(BitData::null_seen_byte_constant(), scratch);

    // The method data pointer needs to be updated.
    int mdp_delta = in_bytes(BitData::bit_data_size());
    if (TypeProfileCasts) {
      mdp_delta = in_bytes(VirtualCallData::virtual_call_data_size());
    }
    update_mdp_by_constant(mdp_delta);

    bind (profile_continue);
  }
}

void InterpreterMacroAssembler::profile_typecheck(Register klass,
                                                  Register scratch) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    int mdp_delta = in_bytes(BitData::bit_data_size());
    if (TypeProfileCasts) {
      mdp_delta = in_bytes(VirtualCallData::virtual_call_data_size());

      // Record the object type.
      record_klass_in_profile(klass, scratch, false);
    }

    // The method data pointer needs to be updated.
    update_mdp_by_constant(mdp_delta);

    bind (profile_continue);
  }
}

void InterpreterMacroAssembler::profile_typecheck_failed(Register scratch) {
  if (ProfileInterpreter && TypeProfileCasts) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    int count_offset = in_bytes(CounterData::count_offset());
    // Back up the address, since we have already bumped the mdp.
    count_offset -= in_bytes(VirtualCallData::virtual_call_data_size());

    // *Decrement* the counter.  We expect to see zero or small negatives.
    increment_mdp_data_at(count_offset, scratch, true);

    bind (profile_continue);
  }
}

// Count the default case of a switch construct.

void InterpreterMacroAssembler::profile_switch_default(Register scratch) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    // Update the default case count
    increment_mdp_data_at(in_bytes(MultiBranchData::default_count_offset()),
                          scratch);

    // The method data pointer needs to be updated.
    update_mdp_by_offset(
                    in_bytes(MultiBranchData::default_displacement_offset()),
                    scratch);

    bind (profile_continue);
  }
}

// Count the index'th case of a switch construct.

void InterpreterMacroAssembler::profile_switch_case(Register index,
                                                    Register scratch,
                                                    Register scratch2,
                                                    Register scratch3) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(profile_continue);

    // Build the base (index * per_case_size_in_bytes()) + case_array_offset_in_bytes()
    set(in_bytes(MultiBranchData::per_case_size()), scratch);
    smul(index, scratch, scratch);
    add(scratch, in_bytes(MultiBranchData::case_array_offset()), scratch);

    // Update the case count
    increment_mdp_data_at(scratch,
                          in_bytes(MultiBranchData::relative_count_offset()),
                          scratch2,
                          scratch3);

    // The method data pointer needs to be updated.
    update_mdp_by_offset(scratch,
                     in_bytes(MultiBranchData::relative_displacement_offset()),
                     scratch2);

    bind (profile_continue);
  }
}

// add a InterpMonitorElem to stack (see frame_sparc.hpp)

void InterpreterMacroAssembler::add_monitor_to_stack( bool stack_is_empty,
                                                      Register Rtemp,
                                                      Register Rtemp2 ) {

  Register Rlimit = Lmonitors;
  const jint delta = frame::interpreter_frame_monitor_size() * wordSize;
  assert( (delta & LongAlignmentMask) == 0,
          "sizeof BasicObjectLock must be even number of doublewords");

  sub( SP,        delta, SP);
  sub( Lesp,      delta, Lesp);
  sub( Lmonitors, delta, Lmonitors);

  if (!stack_is_empty) {

    // must copy stack contents down

    Label start_copying, next;

    // untested("monitor stack expansion");
    compute_stack_base(Rtemp);
    ba( false, start_copying );
    delayed()->cmp( Rtemp, Rlimit); // done? duplicated below

    // note: must copy from low memory upwards
    // On entry to loop,
    // Rtemp points to new base of stack, Lesp points to new end of stack (1 past TOS)
    // Loop mutates Rtemp

    bind( next);

    st_ptr(Rtemp2, Rtemp, 0);
    inc(Rtemp, wordSize);
    cmp(Rtemp, Rlimit); // are we done? (duplicated above)

    bind( start_copying );

    brx( notEqual, true, pn, next );
    delayed()->ld_ptr( Rtemp, delta, Rtemp2 );

    // done copying stack
  }
}

// Locals
void InterpreterMacroAssembler::access_local_ptr( Register index, Register dst ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
  ld_ptr(index, 0, dst);
  // Note:  index must hold the effective address--the iinc template uses it
}

// Just like access_local_ptr but the tag is a returnAddress
void InterpreterMacroAssembler::access_local_returnAddress(Register index,
                                                           Register dst ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
  ld_ptr(index, 0, dst);
}

void InterpreterMacroAssembler::access_local_int( Register index, Register dst ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
  ld(index, 0, dst);
  // Note:  index must hold the effective address--the iinc template uses it
}


void InterpreterMacroAssembler::access_local_long( Register index, Register dst ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
  // First half stored at index n+1 (which grows down from Llocals[n])
  load_unaligned_long(index, Interpreter::local_offset_in_bytes(1), dst);
}


void InterpreterMacroAssembler::access_local_float( Register index, FloatRegister dst ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
  ldf(FloatRegisterImpl::S, index, 0, dst);
}


void InterpreterMacroAssembler::access_local_double( Register index, FloatRegister dst ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
  load_unaligned_double(index, Interpreter::local_offset_in_bytes(1), dst);
}


#ifdef ASSERT
void InterpreterMacroAssembler::check_for_regarea_stomp(Register Rindex, int offset, Register Rlimit, Register Rscratch, Register Rscratch1) {
  Label L;

  assert(Rindex != Rscratch, "Registers cannot be same");
  assert(Rindex != Rscratch1, "Registers cannot be same");
  assert(Rlimit != Rscratch, "Registers cannot be same");
  assert(Rlimit != Rscratch1, "Registers cannot be same");
  assert(Rscratch1 != Rscratch, "Registers cannot be same");

  // untested("reg area corruption");
  add(Rindex, offset, Rscratch);
  add(Rlimit, 64 + STACK_BIAS, Rscratch1);
  cmp(Rscratch, Rscratch1);
  brx(Assembler::greaterEqualUnsigned, false, pn, L);
  delayed()->nop();
  stop("regsave area is being clobbered");
  bind(L);
}
#endif // ASSERT


void InterpreterMacroAssembler::store_local_int( Register index, Register src ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
  debug_only(check_for_regarea_stomp(index, 0, FP, G1_scratch, G4_scratch);)
  st(src, index, 0);
}

void InterpreterMacroAssembler::store_local_ptr( Register index, Register src ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
#ifdef ASSERT
  check_for_regarea_stomp(index, 0, FP, G1_scratch, G4_scratch);
#endif
  st_ptr(src, index, 0);
}



void InterpreterMacroAssembler::store_local_ptr( int n, Register src ) {
  st_ptr(src, Llocals, Interpreter::local_offset_in_bytes(n));
}

void InterpreterMacroAssembler::store_local_long( Register index, Register src ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
#ifdef ASSERT
  check_for_regarea_stomp(index, Interpreter::local_offset_in_bytes(1), FP, G1_scratch, G4_scratch);
#endif
  store_unaligned_long(src, index, Interpreter::local_offset_in_bytes(1)); // which is n+1
}


void InterpreterMacroAssembler::store_local_float( Register index, FloatRegister src ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
#ifdef ASSERT
  check_for_regarea_stomp(index, 0, FP, G1_scratch, G4_scratch);
#endif
  stf(FloatRegisterImpl::S, src, index, 0);
}


void InterpreterMacroAssembler::store_local_double( Register index, FloatRegister src ) {
  assert_not_delayed();
  sll(index, Interpreter::logStackElementSize, index);
  sub(Llocals, index, index);
#ifdef ASSERT
  check_for_regarea_stomp(index, Interpreter::local_offset_in_bytes(1), FP, G1_scratch, G4_scratch);
#endif
  store_unaligned_double(src, index, Interpreter::local_offset_in_bytes(1));
}


int InterpreterMacroAssembler::top_most_monitor_byte_offset() {
  const jint delta = frame::interpreter_frame_monitor_size() * wordSize;
  int rounded_vm_local_words = ::round_to(frame::interpreter_frame_vm_local_words, WordsPerLong);
  return ((-rounded_vm_local_words * wordSize) - delta ) + STACK_BIAS;
}


Address InterpreterMacroAssembler::top_most_monitor() {
  return Address(FP, top_most_monitor_byte_offset());
}


void InterpreterMacroAssembler::compute_stack_base( Register Rdest ) {
  add( Lesp,      wordSize,                                    Rdest );
}

#endif /* CC_INTERP */

void InterpreterMacroAssembler::increment_invocation_counter( Register Rtmp, Register Rtmp2 ) {
  assert(UseCompiler, "incrementing must be useful");
#ifdef CC_INTERP
  Address inv_counter(G5_method, methodOopDesc::invocation_counter_offset() +
                                 InvocationCounter::counter_offset());
  Address be_counter (G5_method, methodOopDesc::backedge_counter_offset() +
                                 InvocationCounter::counter_offset());
#else
  Address inv_counter(Lmethod, methodOopDesc::invocation_counter_offset() +
                               InvocationCounter::counter_offset());
  Address be_counter (Lmethod, methodOopDesc::backedge_counter_offset() +
                               InvocationCounter::counter_offset());
#endif /* CC_INTERP */
  int delta = InvocationCounter::count_increment;

  // Load each counter in a register
  ld( inv_counter, Rtmp );
  ld( be_counter, Rtmp2 );

  assert( is_simm13( delta ), " delta too large.");

  // Add the delta to the invocation counter and store the result
  add( Rtmp, delta, Rtmp );

  // Mask the backedge counter
  and3( Rtmp2, InvocationCounter::count_mask_value, Rtmp2 );

  // Store value
  st( Rtmp, inv_counter);

  // Add invocation counter + backedge counter
  add( Rtmp, Rtmp2, Rtmp);

  // Note that this macro must leave the backedge_count + invocation_count in Rtmp!
}

void InterpreterMacroAssembler::increment_backedge_counter( Register Rtmp, Register Rtmp2 ) {
  assert(UseCompiler, "incrementing must be useful");
#ifdef CC_INTERP
  Address be_counter (G5_method, methodOopDesc::backedge_counter_offset() +
                                 InvocationCounter::counter_offset());
  Address inv_counter(G5_method, methodOopDesc::invocation_counter_offset() +
                                 InvocationCounter::counter_offset());
#else
  Address be_counter (Lmethod, methodOopDesc::backedge_counter_offset() +
                               InvocationCounter::counter_offset());
  Address inv_counter(Lmethod, methodOopDesc::invocation_counter_offset() +
                               InvocationCounter::counter_offset());
#endif /* CC_INTERP */
  int delta = InvocationCounter::count_increment;
  // Load each counter in a register
  ld( be_counter, Rtmp );
  ld( inv_counter, Rtmp2 );

  // Add the delta to the backedge counter
  add( Rtmp, delta, Rtmp );

  // Mask the invocation counter, add to backedge counter
  and3( Rtmp2, InvocationCounter::count_mask_value, Rtmp2 );

  // and store the result to memory
  st( Rtmp, be_counter );

  // Add backedge + invocation counter
  add( Rtmp, Rtmp2, Rtmp );

  // Note that this macro must leave backedge_count + invocation_count in Rtmp!
}

#ifndef CC_INTERP
void InterpreterMacroAssembler::test_backedge_count_for_osr( Register backedge_count,
                                                             Register branch_bcp,
                                                             Register Rtmp ) {
  Label did_not_overflow;
  Label overflow_with_error;
  assert_different_registers(backedge_count, Rtmp, branch_bcp);
  assert(UseOnStackReplacement,"Must UseOnStackReplacement to test_backedge_count_for_osr");

  AddressLiteral limit(&InvocationCounter::InterpreterBackwardBranchLimit);
  load_contents(limit, Rtmp);
  cmp(backedge_count, Rtmp);
  br(Assembler::lessUnsigned, false, Assembler::pt, did_not_overflow);
  delayed()->nop();

  // When ProfileInterpreter is on, the backedge_count comes from the
  // methodDataOop, which value does not get reset on the call to
  // frequency_counter_overflow().  To avoid excessive calls to the overflow
  // routine while the method is being compiled, add a second test to make sure
  // the overflow function is called only once every overflow_frequency.
  if (ProfileInterpreter) {
    const int overflow_frequency = 1024;
    andcc(backedge_count, overflow_frequency-1, Rtmp);
    brx(Assembler::notZero, false, Assembler::pt, did_not_overflow);
    delayed()->nop();
  }

  // overflow in loop, pass branch bytecode
  set(6,Rtmp);
  call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow), branch_bcp, Rtmp);

  // Was an OSR adapter generated?
  // O0 = osr nmethod
  tst(O0);
  brx(Assembler::zero, false, Assembler::pn, overflow_with_error);
  delayed()->nop();

  // Has the nmethod been invalidated already?
  ld(O0, nmethod::entry_bci_offset(), O2);
  cmp(O2, InvalidOSREntryBci);
  br(Assembler::equal, false, Assembler::pn, overflow_with_error);
  delayed()->nop();

  // migrate the interpreter frame off of the stack

  mov(G2_thread, L7);
  // save nmethod
  mov(O0, L6);
  set_last_Java_frame(SP, noreg);
  call_VM_leaf(noreg, CAST_FROM_FN_PTR(address, SharedRuntime::OSR_migration_begin), L7);
  reset_last_Java_frame();
  mov(L7, G2_thread);

  // move OSR nmethod to I1
  mov(L6, I1);

  // OSR buffer to I0
  mov(O0, I0);

  // remove the interpreter frame
  restore(I5_savedSP, 0, SP);

  // Jump to the osr code.
  ld_ptr(O1, nmethod::osr_entry_point_offset(), O2);
  jmp(O2, G0);
  delayed()->nop();

  bind(overflow_with_error);

  bind(did_not_overflow);
}



void InterpreterMacroAssembler::interp_verify_oop(Register reg, TosState state, const char * file, int line) {
  if (state == atos) { MacroAssembler::_verify_oop(reg, "broken oop ", file, line); }
}


// local helper function for the verify_oop_or_return_address macro
static bool verify_return_address(methodOopDesc* m, int bci) {
#ifndef PRODUCT
  address pc = (address)(m->constMethod())
             + in_bytes(constMethodOopDesc::codes_offset()) + bci;
  // assume it is a valid return address if it is inside m and is preceded by a jsr
  if (!m->contains(pc))                                          return false;
  address jsr_pc;
  jsr_pc = pc - Bytecodes::length_for(Bytecodes::_jsr);
  if (*jsr_pc == Bytecodes::_jsr   && jsr_pc >= m->code_base())    return true;
  jsr_pc = pc - Bytecodes::length_for(Bytecodes::_jsr_w);
  if (*jsr_pc == Bytecodes::_jsr_w && jsr_pc >= m->code_base())    return true;
#endif // PRODUCT
  return false;
}


void InterpreterMacroAssembler::verify_oop_or_return_address(Register reg, Register Rtmp) {
  if (!VerifyOops)  return;
  // the VM documentation for the astore[_wide] bytecode allows
  // the TOS to be not only an oop but also a return address
  Label test;
  Label skip;
  // See if it is an address (in the current method):

  mov(reg, Rtmp);
  const int log2_bytecode_size_limit = 16;
  srl(Rtmp, log2_bytecode_size_limit, Rtmp);
  br_notnull( Rtmp, false, pt, test );
  delayed()->nop();

  // %%% should use call_VM_leaf here?
  save_frame_and_mov(0, Lmethod, O0, reg, O1);
  save_thread(L7_thread_cache);
  call(CAST_FROM_FN_PTR(address,verify_return_address), relocInfo::none);
  delayed()->nop();
  restore_thread(L7_thread_cache);
  br_notnull( O0, false, pt, skip );
  delayed()->restore();

  // Perform a more elaborate out-of-line call
  // Not an address; verify it:
  bind(test);
  verify_oop(reg);
  bind(skip);
}


void InterpreterMacroAssembler::verify_FPU(int stack_depth, TosState state) {
  if (state == ftos || state == dtos) MacroAssembler::verify_FPU(stack_depth);
}
#endif /* CC_INTERP */

// Inline assembly for:
//
// if (thread is in interp_only_mode) {
//   InterpreterRuntime::post_method_entry();
// }
// if (DTraceMethodProbes) {
//   SharedRuntime::dtrace_method_entry(method, receiver);
// }
// if (RC_TRACE_IN_RANGE(0x00001000, 0x00002000)) {
//   SharedRuntime::rc_trace_method_entry(method, receiver);
// }

void InterpreterMacroAssembler::notify_method_entry() {

  // C++ interpreter only uses this for native methods.

  // Whenever JVMTI puts a thread in interp_only_mode, method
  // entry/exit events are sent for that thread to track stack
  // depth.  If it is possible to enter interp_only_mode we add
  // the code to check if the event should be sent.
  if (JvmtiExport::can_post_interpreter_events()) {
    Label L;
    Register temp_reg = O5;
    const Address interp_only(G2_thread, JavaThread::interp_only_mode_offset());
    ld(interp_only, temp_reg);
    tst(temp_reg);
    br(zero, false, pt, L);
    delayed()->nop();
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_method_entry));
    bind(L);
  }

  {
    Register temp_reg = O5;
    SkipIfEqual skip_if(this, temp_reg, &DTraceMethodProbes, zero);
    call_VM_leaf(noreg,
      CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_entry),
      G2_thread, Lmethod);
  }

  // RedefineClasses() tracing support for obsolete method entry
  if (RC_TRACE_IN_RANGE(0x00001000, 0x00002000)) {
    call_VM_leaf(noreg,
      CAST_FROM_FN_PTR(address, SharedRuntime::rc_trace_method_entry),
      G2_thread, Lmethod);
  }
}


// Inline assembly for:
//
// if (thread is in interp_only_mode) {
//   // save result
//   InterpreterRuntime::post_method_exit();
//   // restore result
// }
// if (DTraceMethodProbes) {
//   SharedRuntime::dtrace_method_exit(thread, method);
// }
//
// Native methods have their result stored in d_tmp and l_tmp
// Java methods have their result stored in the expression stack

void InterpreterMacroAssembler::notify_method_exit(bool is_native_method,
                                                   TosState state,
                                                   NotifyMethodExitMode mode) {
  // C++ interpreter only uses this for native methods.

  // Whenever JVMTI puts a thread in interp_only_mode, method
  // entry/exit events are sent for that thread to track stack
  // depth.  If it is possible to enter interp_only_mode we add
  // the code to check if the event should be sent.
  if (mode == NotifyJVMTI && JvmtiExport::can_post_interpreter_events()) {
    Label L;
    Register temp_reg = O5;
    const Address interp_only(G2_thread, JavaThread::interp_only_mode_offset());
    ld(interp_only, temp_reg);
    tst(temp_reg);
    br(zero, false, pt, L);
    delayed()->nop();

    // Note: frame::interpreter_frame_result has a dependency on how the
    // method result is saved across the call to post_method_exit. For
    // native methods it assumes the result registers are saved to
    // l_scratch and d_scratch. If this changes then the interpreter_frame_result
    // implementation will need to be updated too.

    save_return_value(state, is_native_method);
    call_VM(noreg,
            CAST_FROM_FN_PTR(address, InterpreterRuntime::post_method_exit));
    restore_return_value(state, is_native_method);
    bind(L);
  }

  {
    Register temp_reg = O5;
    // Dtrace notification
    SkipIfEqual skip_if(this, temp_reg, &DTraceMethodProbes, zero);
    save_return_value(state, is_native_method);
    call_VM_leaf(
      noreg,
      CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_exit),
      G2_thread, Lmethod);
    restore_return_value(state, is_native_method);
  }
}

void InterpreterMacroAssembler::save_return_value(TosState state, bool is_native_call) {
#ifdef CC_INTERP
  // result potentially in O0/O1: save it across calls
  stf(FloatRegisterImpl::D, F0, STATE(_native_fresult));
#ifdef _LP64
  stx(O0, STATE(_native_lresult));
#else
  std(O0, STATE(_native_lresult));
#endif
#else // CC_INTERP
  if (is_native_call) {
    stf(FloatRegisterImpl::D, F0, d_tmp);
#ifdef _LP64
    stx(O0, l_tmp);
#else
    std(O0, l_tmp);
#endif
  } else {
    push(state);
  }
#endif // CC_INTERP
}

void InterpreterMacroAssembler::restore_return_value( TosState state, bool is_native_call) {
#ifdef CC_INTERP
  ldf(FloatRegisterImpl::D, STATE(_native_fresult), F0);
#ifdef _LP64
  ldx(STATE(_native_lresult), O0);
#else
  ldd(STATE(_native_lresult), O0);
#endif
#else // CC_INTERP
  if (is_native_call) {
    ldf(FloatRegisterImpl::D, d_tmp, F0);
#ifdef _LP64
    ldx(l_tmp, O0);
#else
    ldd(l_tmp, O0);
#endif
  } else {
    pop(state);
  }
#endif // CC_INTERP
}

// Jump if ((*counter_addr += increment) & mask) satisfies the condition.
void InterpreterMacroAssembler::increment_mask_and_jump(Address counter_addr,
                                                        int increment, int mask,
                                                        Register scratch1, Register scratch2,
                                                        Condition cond, Label *where) {
  ld(counter_addr, scratch1);
  add(scratch1, increment, scratch1);
  if (is_simm13(mask)) {
    andcc(scratch1, mask, G0);
  } else {
    set(mask, scratch2);
    andcc(scratch1, scratch2,  G0);
  }
  br(cond, false, Assembler::pn, *where);
  delayed()->st(scratch1, counter_addr);
}
