/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "c1/c1_Compilation.hpp"
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "c1/c1_ValueStack.hpp"
#include "ci/ciArrayKlass.hpp"
#include "ci/ciInstance.hpp"
#include "gc_interface/collectedHeap.hpp"
#include "memory/barrierSet.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "nativeInst_sparc.hpp"
#include "oops/objArrayKlass.hpp"
#include "runtime/sharedRuntime.hpp"

#define __ _masm->


//------------------------------------------------------------


bool LIR_Assembler::is_small_constant(LIR_Opr opr) {
  if (opr->is_constant()) {
    LIR_Const* constant = opr->as_constant_ptr();
    switch (constant->type()) {
      case T_INT: {
        jint value = constant->as_jint();
        return Assembler::is_simm13(value);
      }

      default:
        return false;
    }
  }
  return false;
}


bool LIR_Assembler::is_single_instruction(LIR_Op* op) {
  switch (op->code()) {
    case lir_null_check:
    return true;


    case lir_add:
    case lir_ushr:
    case lir_shr:
    case lir_shl:
      // integer shifts and adds are always one instruction
      return op->result_opr()->is_single_cpu();


    case lir_move: {
      LIR_Op1* op1 = op->as_Op1();
      LIR_Opr src = op1->in_opr();
      LIR_Opr dst = op1->result_opr();

      if (src == dst) {
        NEEDS_CLEANUP;
        // this works around a problem where moves with the same src and dst
        // end up in the delay slot and then the assembler swallows the mov
        // since it has no effect and then it complains because the delay slot
        // is empty.  returning false stops the optimizer from putting this in
        // the delay slot
        return false;
      }

      // don't put moves involving oops into the delay slot since the VerifyOops code
      // will make it much larger than a single instruction.
      if (VerifyOops) {
        return false;
      }

      if (src->is_double_cpu() || dst->is_double_cpu() || op1->patch_code() != lir_patch_none ||
          ((src->is_double_fpu() || dst->is_double_fpu()) && op1->move_kind() != lir_move_normal)) {
        return false;
      }

      if (UseCompressedOops) {
        if (dst->is_address() && !dst->is_stack() && (dst->type() == T_OBJECT || dst->type() == T_ARRAY)) return false;
        if (src->is_address() && !src->is_stack() && (src->type() == T_OBJECT || src->type() == T_ARRAY)) return false;
      }

      if (dst->is_register()) {
        if (src->is_address() && Assembler::is_simm13(src->as_address_ptr()->disp())) {
          return !PatchALot;
        } else if (src->is_single_stack()) {
          return true;
        }
      }

      if (src->is_register()) {
        if (dst->is_address() && Assembler::is_simm13(dst->as_address_ptr()->disp())) {
          return !PatchALot;
        } else if (dst->is_single_stack()) {
          return true;
        }
      }

      if (dst->is_register() &&
          ((src->is_register() && src->is_single_word() && src->is_same_type(dst)) ||
           (src->is_constant() && LIR_Assembler::is_small_constant(op->as_Op1()->in_opr())))) {
        return true;
      }

      return false;
    }

    default:
      return false;
  }
  ShouldNotReachHere();
}


LIR_Opr LIR_Assembler::receiverOpr() {
  return FrameMap::O0_oop_opr;
}


LIR_Opr LIR_Assembler::incomingReceiverOpr() {
  return FrameMap::I0_oop_opr;
}


LIR_Opr LIR_Assembler::osrBufferPointer() {
  return FrameMap::I0_opr;
}


int LIR_Assembler::initial_frame_size_in_bytes() {
  return in_bytes(frame_map()->framesize_in_bytes());
}


// inline cache check: the inline cached class is in G5_inline_cache_reg(G5);
// we fetch the class of the receiver (O0) and compare it with the cached class.
// If they do not match we jump to slow case.
int LIR_Assembler::check_icache() {
  int offset = __ offset();
  __ inline_cache_check(O0, G5_inline_cache_reg);
  return offset;
}


void LIR_Assembler::osr_entry() {
  // On-stack-replacement entry sequence (interpreter frame layout described in interpreter_sparc.cpp):
  //
  //   1. Create a new compiled activation.
  //   2. Initialize local variables in the compiled activation.  The expression stack must be empty
  //      at the osr_bci; it is not initialized.
  //   3. Jump to the continuation address in compiled code to resume execution.

  // OSR entry point
  offsets()->set_value(CodeOffsets::OSR_Entry, code_offset());
  BlockBegin* osr_entry = compilation()->hir()->osr_entry();
  ValueStack* entry_state = osr_entry->end()->state();
  int number_of_locks = entry_state->locks_size();

  // Create a frame for the compiled activation.
  __ build_frame(initial_frame_size_in_bytes());

  // OSR buffer is
  //
  // locals[nlocals-1..0]
  // monitors[number_of_locks-1..0]
  //
  // locals is a direct copy of the interpreter frame so in the osr buffer
  // so first slot in the local array is the last local from the interpreter
  // and last slot is local[0] (receiver) from the interpreter
  //
  // Similarly with locks. The first lock slot in the osr buffer is the nth lock
  // from the interpreter frame, the nth lock slot in the osr buffer is 0th lock
  // in the interpreter frame (the method lock if a sync method)

  // Initialize monitors in the compiled activation.
  //   I0: pointer to osr buffer
  //
  // All other registers are dead at this point and the locals will be
  // copied into place by code emitted in the IR.

  Register OSR_buf = osrBufferPointer()->as_register();
  { assert(frame::interpreter_frame_monitor_size() == BasicObjectLock::size(), "adjust code below");
    int monitor_offset = BytesPerWord * method()->max_locals() +
      (2 * BytesPerWord) * (number_of_locks - 1);
    // SharedRuntime::OSR_migration_begin() packs BasicObjectLocks in
    // the OSR buffer using 2 word entries: first the lock and then
    // the oop.
    for (int i = 0; i < number_of_locks; i++) {
      int slot_offset = monitor_offset - ((i * 2) * BytesPerWord);
#ifdef ASSERT
      // verify the interpreter's monitor has a non-null object
      {
        Label L;
        __ ld_ptr(OSR_buf, slot_offset + 1*BytesPerWord, O7);
        __ cmp(G0, O7);
        __ br(Assembler::notEqual, false, Assembler::pt, L);
        __ delayed()->nop();
        __ stop("locked object is NULL");
        __ bind(L);
      }
#endif // ASSERT
      // Copy the lock field into the compiled activation.
      __ ld_ptr(OSR_buf, slot_offset + 0, O7);
      __ st_ptr(O7, frame_map()->address_for_monitor_lock(i));
      __ ld_ptr(OSR_buf, slot_offset + 1*BytesPerWord, O7);
      __ st_ptr(O7, frame_map()->address_for_monitor_object(i));
    }
  }
}


// Optimized Library calls
// This is the fast version of java.lang.String.compare; it has not
// OSR-entry and therefore, we generate a slow version for OSR's
void LIR_Assembler::emit_string_compare(LIR_Opr left, LIR_Opr right, LIR_Opr dst, CodeEmitInfo* info) {
  Register str0 = left->as_register();
  Register str1 = right->as_register();

  Label Ldone;

  Register result = dst->as_register();
  {
    // Get a pointer to the first character of string0 in tmp0 and get string0.count in str0
    // Get a pointer to the first character of string1 in tmp1 and get string1.count in str1
    // Also, get string0.count-string1.count in o7 and get the condition code set
    // Note: some instructions have been hoisted for better instruction scheduling

    Register tmp0 = L0;
    Register tmp1 = L1;
    Register tmp2 = L2;

    int  value_offset = java_lang_String:: value_offset_in_bytes(); // char array
    int offset_offset = java_lang_String::offset_offset_in_bytes(); // first character position
    int  count_offset = java_lang_String:: count_offset_in_bytes();

    __ load_heap_oop(str0, value_offset, tmp0);
    __ ld(str0, offset_offset, tmp2);
    __ add(tmp0, arrayOopDesc::base_offset_in_bytes(T_CHAR), tmp0);
    __ ld(str0, count_offset, str0);
    __ sll(tmp2, exact_log2(sizeof(jchar)), tmp2);

    // str1 may be null
    add_debug_info_for_null_check_here(info);

    __ load_heap_oop(str1, value_offset, tmp1);
    __ add(tmp0, tmp2, tmp0);

    __ ld(str1, offset_offset, tmp2);
    __ add(tmp1, arrayOopDesc::base_offset_in_bytes(T_CHAR), tmp1);
    __ ld(str1, count_offset, str1);
    __ sll(tmp2, exact_log2(sizeof(jchar)), tmp2);
    __ subcc(str0, str1, O7);
    __ add(tmp1, tmp2, tmp1);
  }

  {
    // Compute the minimum of the string lengths, scale it and store it in limit
    Register count0 = I0;
    Register count1 = I1;
    Register limit  = L3;

    Label Lskip;
    __ sll(count0, exact_log2(sizeof(jchar)), limit);             // string0 is shorter
    __ br(Assembler::greater, true, Assembler::pt, Lskip);
    __ delayed()->sll(count1, exact_log2(sizeof(jchar)), limit);  // string1 is shorter
    __ bind(Lskip);

    // If either string is empty (or both of them) the result is the difference in lengths
    __ cmp(limit, 0);
    __ br(Assembler::equal, true, Assembler::pn, Ldone);
    __ delayed()->mov(O7, result);  // result is difference in lengths
  }

  {
    // Neither string is empty
    Label Lloop;

    Register base0 = L0;
    Register base1 = L1;
    Register chr0  = I0;
    Register chr1  = I1;
    Register limit = L3;

    // Shift base0 and base1 to the end of the arrays, negate limit
    __ add(base0, limit, base0);
    __ add(base1, limit, base1);
    __ neg(limit);  // limit = -min{string0.count, strin1.count}

    __ lduh(base0, limit, chr0);
    __ bind(Lloop);
    __ lduh(base1, limit, chr1);
    __ subcc(chr0, chr1, chr0);
    __ br(Assembler::notZero, false, Assembler::pn, Ldone);
    assert(chr0 == result, "result must be pre-placed");
    __ delayed()->inccc(limit, sizeof(jchar));
    __ br(Assembler::notZero, true, Assembler::pt, Lloop);
    __ delayed()->lduh(base0, limit, chr0);
  }

  // If strings are equal up to min length, return the length difference.
  __ mov(O7, result);

  // Otherwise, return the difference between the first mismatched chars.
  __ bind(Ldone);
}


// --------------------------------------------------------------------------------------------

void LIR_Assembler::monitorexit(LIR_Opr obj_opr, LIR_Opr lock_opr, Register hdr, int monitor_no) {
  if (!GenerateSynchronizationCode) return;

  Register obj_reg = obj_opr->as_register();
  Register lock_reg = lock_opr->as_register();

  Address mon_addr = frame_map()->address_for_monitor_lock(monitor_no);
  Register reg = mon_addr.base();
  int offset = mon_addr.disp();
  // compute pointer to BasicLock
  if (mon_addr.is_simm13()) {
    __ add(reg, offset, lock_reg);
  }
  else {
    __ set(offset, lock_reg);
    __ add(reg, lock_reg, lock_reg);
  }
  // unlock object
  MonitorAccessStub* slow_case = new MonitorExitStub(lock_opr, UseFastLocking, monitor_no);
  // _slow_case_stubs->append(slow_case);
  // temporary fix: must be created after exceptionhandler, therefore as call stub
  _slow_case_stubs->append(slow_case);
  if (UseFastLocking) {
    // try inlined fast unlocking first, revert to slow locking if it fails
    // note: lock_reg points to the displaced header since the displaced header offset is 0!
    assert(BasicLock::displaced_header_offset_in_bytes() == 0, "lock_reg must point to the displaced header");
    __ unlock_object(hdr, obj_reg, lock_reg, *slow_case->entry());
  } else {
    // always do slow unlocking
    // note: the slow unlocking code could be inlined here, however if we use
    //       slow unlocking, speed doesn't matter anyway and this solution is
    //       simpler and requires less duplicated code - additionally, the
    //       slow unlocking code is the same in either case which simplifies
    //       debugging
    __ br(Assembler::always, false, Assembler::pt, *slow_case->entry());
    __ delayed()->nop();
  }
  // done
  __ bind(*slow_case->continuation());
}


int LIR_Assembler::emit_exception_handler() {
  // if the last instruction is a call (typically to do a throw which
  // is coming at the end after block reordering) the return address
  // must still point into the code area in order to avoid assertion
  // failures when searching for the corresponding bci => add a nop
  // (was bug 5/14/1999 - gri)
  __ nop();

  // generate code for exception handler
  ciMethod* method = compilation()->method();

  address handler_base = __ start_a_stub(exception_handler_size);

  if (handler_base == NULL) {
    // not enough space left for the handler
    bailout("exception handler overflow");
    return -1;
  }

  int offset = code_offset();

  __ call(Runtime1::entry_for(Runtime1::handle_exception_id), relocInfo::runtime_call_type);
  __ delayed()->nop();
  debug_only(__ stop("should have gone to the caller");)
  assert(code_offset() - offset <= exception_handler_size, "overflow");
  __ end_a_stub();

  return offset;
}


// Emit the code to remove the frame from the stack in the exception
// unwind path.
int LIR_Assembler::emit_unwind_handler() {
#ifndef PRODUCT
  if (CommentedAssembly) {
    _masm->block_comment("Unwind handler");
  }
#endif

  int offset = code_offset();

  // Fetch the exception from TLS and clear out exception related thread state
  __ ld_ptr(G2_thread, in_bytes(JavaThread::exception_oop_offset()), O0);
  __ st_ptr(G0, G2_thread, in_bytes(JavaThread::exception_oop_offset()));
  __ st_ptr(G0, G2_thread, in_bytes(JavaThread::exception_pc_offset()));

  __ bind(_unwind_handler_entry);
  __ verify_not_null_oop(O0);
  if (method()->is_synchronized() || compilation()->env()->dtrace_method_probes()) {
    __ mov(O0, I0);  // Preserve the exception
  }

  // Preform needed unlocking
  MonitorExitStub* stub = NULL;
  if (method()->is_synchronized()) {
    monitor_address(0, FrameMap::I1_opr);
    stub = new MonitorExitStub(FrameMap::I1_opr, true, 0);
    __ unlock_object(I3, I2, I1, *stub->entry());
    __ bind(*stub->continuation());
  }

  if (compilation()->env()->dtrace_method_probes()) {
    __ mov(G2_thread, O0);
    jobject2reg(method()->constant_encoding(), O1);
    __ call(CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_exit), relocInfo::runtime_call_type);
    __ delayed()->nop();
  }

  if (method()->is_synchronized() || compilation()->env()->dtrace_method_probes()) {
    __ mov(I0, O0);  // Restore the exception
  }

  // dispatch to the unwind logic
  __ call(Runtime1::entry_for(Runtime1::unwind_exception_id), relocInfo::runtime_call_type);
  __ delayed()->nop();

  // Emit the slow path assembly
  if (stub != NULL) {
    stub->emit_code(this);
  }

  return offset;
}


int LIR_Assembler::emit_deopt_handler() {
  // if the last instruction is a call (typically to do a throw which
  // is coming at the end after block reordering) the return address
  // must still point into the code area in order to avoid assertion
  // failures when searching for the corresponding bci => add a nop
  // (was bug 5/14/1999 - gri)
  __ nop();

  // generate code for deopt handler
  ciMethod* method = compilation()->method();
  address handler_base = __ start_a_stub(deopt_handler_size);
  if (handler_base == NULL) {
    // not enough space left for the handler
    bailout("deopt handler overflow");
    return -1;
  }

  int offset = code_offset();
  AddressLiteral deopt_blob(SharedRuntime::deopt_blob()->unpack());
  __ JUMP(deopt_blob, G3_scratch, 0); // sethi;jmp
  __ delayed()->nop();
  assert(code_offset() - offset <= deopt_handler_size, "overflow");
  debug_only(__ stop("should have gone to the caller");)
  __ end_a_stub();

  return offset;
}


void LIR_Assembler::jobject2reg(jobject o, Register reg) {
  if (o == NULL) {
    __ set(NULL_WORD, reg);
  } else {
    int oop_index = __ oop_recorder()->find_index(o);
    RelocationHolder rspec = oop_Relocation::spec(oop_index);
    __ set(NULL_WORD, reg, rspec); // Will be set when the nmethod is created
  }
}


void LIR_Assembler::jobject2reg_with_patching(Register reg, CodeEmitInfo *info) {
  // Allocate a new index in oop table to hold the oop once it's been patched
  int oop_index = __ oop_recorder()->allocate_index((jobject)NULL);
  PatchingStub* patch = new PatchingStub(_masm, PatchingStub::load_klass_id, oop_index);

  AddressLiteral addrlit(NULL, oop_Relocation::spec(oop_index));
  assert(addrlit.rspec().type() == relocInfo::oop_type, "must be an oop reloc");
  // It may not seem necessary to use a sethi/add pair to load a NULL into dest, but the
  // NULL will be dynamically patched later and the patched value may be large.  We must
  // therefore generate the sethi/add as a placeholders
  __ patchable_set(addrlit, reg);

  patching_epilog(patch, lir_patch_normal, reg, info);
}


void LIR_Assembler::emit_op3(LIR_Op3* op) {
  Register Rdividend = op->in_opr1()->as_register();
  Register Rdivisor  = noreg;
  Register Rscratch  = op->in_opr3()->as_register();
  Register Rresult   = op->result_opr()->as_register();
  int divisor = -1;

  if (op->in_opr2()->is_register()) {
    Rdivisor = op->in_opr2()->as_register();
  } else {
    divisor = op->in_opr2()->as_constant_ptr()->as_jint();
    assert(Assembler::is_simm13(divisor), "can only handle simm13");
  }

  assert(Rdividend != Rscratch, "");
  assert(Rdivisor  != Rscratch, "");
  assert(op->code() == lir_idiv || op->code() == lir_irem, "Must be irem or idiv");

  if (Rdivisor == noreg && is_power_of_2(divisor)) {
    // convert division by a power of two into some shifts and logical operations
    if (op->code() == lir_idiv) {
      if (divisor == 2) {
        __ srl(Rdividend, 31, Rscratch);
      } else {
        __ sra(Rdividend, 31, Rscratch);
        __ and3(Rscratch, divisor - 1, Rscratch);
      }
      __ add(Rdividend, Rscratch, Rscratch);
      __ sra(Rscratch, log2_intptr(divisor), Rresult);
      return;
    } else {
      if (divisor == 2) {
        __ srl(Rdividend, 31, Rscratch);
      } else {
        __ sra(Rdividend, 31, Rscratch);
        __ and3(Rscratch, divisor - 1,Rscratch);
      }
      __ add(Rdividend, Rscratch, Rscratch);
      __ andn(Rscratch, divisor - 1,Rscratch);
      __ sub(Rdividend, Rscratch, Rresult);
      return;
    }
  }

  __ sra(Rdividend, 31, Rscratch);
  __ wry(Rscratch);
  if (!VM_Version::v9_instructions_work()) {
    // v9 doesn't require these nops
    __ nop();
    __ nop();
    __ nop();
    __ nop();
  }

  add_debug_info_for_div0_here(op->info());

  if (Rdivisor != noreg) {
    __ sdivcc(Rdividend, Rdivisor, (op->code() == lir_idiv ? Rresult : Rscratch));
  } else {
    assert(Assembler::is_simm13(divisor), "can only handle simm13");
    __ sdivcc(Rdividend, divisor, (op->code() == lir_idiv ? Rresult : Rscratch));
  }

  Label skip;
  __ br(Assembler::overflowSet, true, Assembler::pn, skip);
  __ delayed()->Assembler::sethi(0x80000000, (op->code() == lir_idiv ? Rresult : Rscratch));
  __ bind(skip);

  if (op->code() == lir_irem) {
    if (Rdivisor != noreg) {
      __ smul(Rscratch, Rdivisor, Rscratch);
    } else {
      __ smul(Rscratch, divisor, Rscratch);
    }
    __ sub(Rdividend, Rscratch, Rresult);
  }
}


void LIR_Assembler::emit_opBranch(LIR_OpBranch* op) {
#ifdef ASSERT
  assert(op->block() == NULL || op->block()->label() == op->label(), "wrong label");
  if (op->block() != NULL)  _branch_target_blocks.append(op->block());
  if (op->ublock() != NULL) _branch_target_blocks.append(op->ublock());
#endif
  assert(op->info() == NULL, "shouldn't have CodeEmitInfo");

  if (op->cond() == lir_cond_always) {
    __ br(Assembler::always, false, Assembler::pt, *(op->label()));
  } else if (op->code() == lir_cond_float_branch) {
    assert(op->ublock() != NULL, "must have unordered successor");
    bool is_unordered = (op->ublock() == op->block());
    Assembler::Condition acond;
    switch (op->cond()) {
      case lir_cond_equal:         acond = Assembler::f_equal;    break;
      case lir_cond_notEqual:      acond = Assembler::f_notEqual; break;
      case lir_cond_less:          acond = (is_unordered ? Assembler::f_unorderedOrLess          : Assembler::f_less);           break;
      case lir_cond_greater:       acond = (is_unordered ? Assembler::f_unorderedOrGreater       : Assembler::f_greater);        break;
      case lir_cond_lessEqual:     acond = (is_unordered ? Assembler::f_unorderedOrLessOrEqual   : Assembler::f_lessOrEqual);    break;
      case lir_cond_greaterEqual:  acond = (is_unordered ? Assembler::f_unorderedOrGreaterOrEqual: Assembler::f_greaterOrEqual); break;
      default :                         ShouldNotReachHere();
    };

    if (!VM_Version::v9_instructions_work()) {
      __ nop();
    }
    __ fb( acond, false, Assembler::pn, *(op->label()));
  } else {
    assert (op->code() == lir_branch, "just checking");

    Assembler::Condition acond;
    switch (op->cond()) {
      case lir_cond_equal:        acond = Assembler::equal;                break;
      case lir_cond_notEqual:     acond = Assembler::notEqual;             break;
      case lir_cond_less:         acond = Assembler::less;                 break;
      case lir_cond_lessEqual:    acond = Assembler::lessEqual;            break;
      case lir_cond_greaterEqual: acond = Assembler::greaterEqual;         break;
      case lir_cond_greater:      acond = Assembler::greater;              break;
      case lir_cond_aboveEqual:   acond = Assembler::greaterEqualUnsigned; break;
      case lir_cond_belowEqual:   acond = Assembler::lessEqualUnsigned;    break;
      default:                         ShouldNotReachHere();
    };

    // sparc has different condition codes for testing 32-bit
    // vs. 64-bit values.  We could always test xcc is we could
    // guarantee that 32-bit loads always sign extended but that isn't
    // true and since sign extension isn't free, it would impose a
    // slight cost.
#ifdef _LP64
    if  (op->type() == T_INT) {
      __ br(acond, false, Assembler::pn, *(op->label()));
    } else
#endif
      __ brx(acond, false, Assembler::pn, *(op->label()));
  }
  // The peephole pass fills the delay slot
}


void LIR_Assembler::emit_opConvert(LIR_OpConvert* op) {
  Bytecodes::Code code = op->bytecode();
  LIR_Opr dst = op->result_opr();

  switch(code) {
    case Bytecodes::_i2l: {
      Register rlo  = dst->as_register_lo();
      Register rhi  = dst->as_register_hi();
      Register rval = op->in_opr()->as_register();
#ifdef _LP64
      __ sra(rval, 0, rlo);
#else
      __ mov(rval, rlo);
      __ sra(rval, BitsPerInt-1, rhi);
#endif
      break;
    }
    case Bytecodes::_i2d:
    case Bytecodes::_i2f: {
      bool is_double = (code == Bytecodes::_i2d);
      FloatRegister rdst = is_double ? dst->as_double_reg() : dst->as_float_reg();
      FloatRegisterImpl::Width w = is_double ? FloatRegisterImpl::D : FloatRegisterImpl::S;
      FloatRegister rsrc = op->in_opr()->as_float_reg();
      if (rsrc != rdst) {
        __ fmov(FloatRegisterImpl::S, rsrc, rdst);
      }
      __ fitof(w, rdst, rdst);
      break;
    }
    case Bytecodes::_f2i:{
      FloatRegister rsrc = op->in_opr()->as_float_reg();
      Address       addr = frame_map()->address_for_slot(dst->single_stack_ix());
      Label L;
      // result must be 0 if value is NaN; test by comparing value to itself
      __ fcmp(FloatRegisterImpl::S, Assembler::fcc0, rsrc, rsrc);
      if (!VM_Version::v9_instructions_work()) {
        __ nop();
      }
      __ fb(Assembler::f_unordered, true, Assembler::pn, L);
      __ delayed()->st(G0, addr); // annuled if contents of rsrc is not NaN
      __ ftoi(FloatRegisterImpl::S, rsrc, rsrc);
      // move integer result from float register to int register
      __ stf(FloatRegisterImpl::S, rsrc, addr.base(), addr.disp());
      __ bind (L);
      break;
    }
    case Bytecodes::_l2i: {
      Register rlo  = op->in_opr()->as_register_lo();
      Register rhi  = op->in_opr()->as_register_hi();
      Register rdst = dst->as_register();
#ifdef _LP64
      __ sra(rlo, 0, rdst);
#else
      __ mov(rlo, rdst);
#endif
      break;
    }
    case Bytecodes::_d2f:
    case Bytecodes::_f2d: {
      bool is_double = (code == Bytecodes::_f2d);
      assert((!is_double && dst->is_single_fpu()) || (is_double && dst->is_double_fpu()), "check");
      LIR_Opr val = op->in_opr();
      FloatRegister rval = (code == Bytecodes::_d2f) ? val->as_double_reg() : val->as_float_reg();
      FloatRegister rdst = is_double ? dst->as_double_reg() : dst->as_float_reg();
      FloatRegisterImpl::Width vw = is_double ? FloatRegisterImpl::S : FloatRegisterImpl::D;
      FloatRegisterImpl::Width dw = is_double ? FloatRegisterImpl::D : FloatRegisterImpl::S;
      __ ftof(vw, dw, rval, rdst);
      break;
    }
    case Bytecodes::_i2s:
    case Bytecodes::_i2b: {
      Register rval = op->in_opr()->as_register();
      Register rdst = dst->as_register();
      int shift = (code == Bytecodes::_i2b) ? (BitsPerInt - T_BYTE_aelem_bytes * BitsPerByte) : (BitsPerInt - BitsPerShort);
      __ sll (rval, shift, rdst);
      __ sra (rdst, shift, rdst);
      break;
    }
    case Bytecodes::_i2c: {
      Register rval = op->in_opr()->as_register();
      Register rdst = dst->as_register();
      int shift = BitsPerInt - T_CHAR_aelem_bytes * BitsPerByte;
      __ sll (rval, shift, rdst);
      __ srl (rdst, shift, rdst);
      break;
    }

    default: ShouldNotReachHere();
  }
}


void LIR_Assembler::align_call(LIR_Code) {
  // do nothing since all instructions are word aligned on sparc
}


void LIR_Assembler::call(LIR_OpJavaCall* op, relocInfo::relocType rtype) {
  __ call(op->addr(), rtype);
  // The peephole pass fills the delay slot, add_call_info is done in
  // LIR_Assembler::emit_delay.
}


void LIR_Assembler::ic_call(LIR_OpJavaCall* op) {
  RelocationHolder rspec = virtual_call_Relocation::spec(pc());
  __ set_oop((jobject)Universe::non_oop_word(), G5_inline_cache_reg);
  __ relocate(rspec);
  __ call(op->addr(), relocInfo::none);
  // The peephole pass fills the delay slot, add_call_info is done in
  // LIR_Assembler::emit_delay.
}


void LIR_Assembler::vtable_call(LIR_OpJavaCall* op) {
  add_debug_info_for_null_check_here(op->info());
  __ load_klass(O0, G3_scratch);
  if (__ is_simm13(op->vtable_offset())) {
    __ ld_ptr(G3_scratch, op->vtable_offset(), G5_method);
  } else {
    // This will generate 2 instructions
    __ set(op->vtable_offset(), G5_method);
    // ld_ptr, set_hi, set
    __ ld_ptr(G3_scratch, G5_method, G5_method);
  }
  __ ld_ptr(G5_method, methodOopDesc::from_compiled_offset(), G3_scratch);
  __ callr(G3_scratch, G0);
  // the peephole pass fills the delay slot
}

int LIR_Assembler::store(LIR_Opr from_reg, Register base, int offset, BasicType type, bool wide, bool unaligned) {
  int store_offset;
  if (!Assembler::is_simm13(offset + (type == T_LONG) ? wordSize : 0)) {
    assert(!unaligned, "can't handle this");
    // for offsets larger than a simm13 we setup the offset in O7
    __ set(offset, O7);
    store_offset = store(from_reg, base, O7, type, wide);
  } else {
    if (type == T_ARRAY || type == T_OBJECT) {
      __ verify_oop(from_reg->as_register());
    }
    store_offset = code_offset();
    switch (type) {
      case T_BOOLEAN: // fall through
      case T_BYTE  : __ stb(from_reg->as_register(), base, offset); break;
      case T_CHAR  : __ sth(from_reg->as_register(), base, offset); break;
      case T_SHORT : __ sth(from_reg->as_register(), base, offset); break;
      case T_INT   : __ stw(from_reg->as_register(), base, offset); break;
      case T_LONG  :
#ifdef _LP64
        if (unaligned || PatchALot) {
          __ srax(from_reg->as_register_lo(), 32, O7);
          __ stw(from_reg->as_register_lo(), base, offset + lo_word_offset_in_bytes);
          __ stw(O7,                         base, offset + hi_word_offset_in_bytes);
        } else {
          __ stx(from_reg->as_register_lo(), base, offset);
        }
#else
        assert(Assembler::is_simm13(offset + 4), "must be");
        __ stw(from_reg->as_register_lo(), base, offset + lo_word_offset_in_bytes);
        __ stw(from_reg->as_register_hi(), base, offset + hi_word_offset_in_bytes);
#endif
        break;
      case T_ADDRESS:
        __ st_ptr(from_reg->as_register(), base, offset);
        break;
      case T_ARRAY : // fall through
      case T_OBJECT:
        {
          if (UseCompressedOops && !wide) {
            __ encode_heap_oop(from_reg->as_register(), G3_scratch);
            store_offset = code_offset();
            __ stw(G3_scratch, base, offset);
          } else {
            __ st_ptr(from_reg->as_register(), base, offset);
          }
          break;
        }

      case T_FLOAT : __ stf(FloatRegisterImpl::S, from_reg->as_float_reg(), base, offset); break;
      case T_DOUBLE:
        {
          FloatRegister reg = from_reg->as_double_reg();
          // split unaligned stores
          if (unaligned || PatchALot) {
            assert(Assembler::is_simm13(offset + 4), "must be");
            __ stf(FloatRegisterImpl::S, reg->successor(), base, offset + 4);
            __ stf(FloatRegisterImpl::S, reg,              base, offset);
          } else {
            __ stf(FloatRegisterImpl::D, reg, base, offset);
          }
          break;
        }
      default      : ShouldNotReachHere();
    }
  }
  return store_offset;
}


int LIR_Assembler::store(LIR_Opr from_reg, Register base, Register disp, BasicType type, bool wide) {
  if (type == T_ARRAY || type == T_OBJECT) {
    __ verify_oop(from_reg->as_register());
  }
  int store_offset = code_offset();
  switch (type) {
    case T_BOOLEAN: // fall through
    case T_BYTE  : __ stb(from_reg->as_register(), base, disp); break;
    case T_CHAR  : __ sth(from_reg->as_register(), base, disp); break;
    case T_SHORT : __ sth(from_reg->as_register(), base, disp); break;
    case T_INT   : __ stw(from_reg->as_register(), base, disp); break;
    case T_LONG  :
#ifdef _LP64
      __ stx(from_reg->as_register_lo(), base, disp);
#else
      assert(from_reg->as_register_hi()->successor() == from_reg->as_register_lo(), "must match");
      __ std(from_reg->as_register_hi(), base, disp);
#endif
      break;
    case T_ADDRESS:
      __ st_ptr(from_reg->as_register(), base, disp);
      break;
    case T_ARRAY : // fall through
    case T_OBJECT:
      {
        if (UseCompressedOops && !wide) {
          __ encode_heap_oop(from_reg->as_register(), G3_scratch);
          store_offset = code_offset();
          __ stw(G3_scratch, base, disp);
        } else {
          __ st_ptr(from_reg->as_register(), base, disp);
        }
        break;
      }
    case T_FLOAT : __ stf(FloatRegisterImpl::S, from_reg->as_float_reg(), base, disp); break;
    case T_DOUBLE: __ stf(FloatRegisterImpl::D, from_reg->as_double_reg(), base, disp); break;
    default      : ShouldNotReachHere();
  }
  return store_offset;
}


int LIR_Assembler::load(Register base, int offset, LIR_Opr to_reg, BasicType type, bool wide, bool unaligned) {
  int load_offset;
  if (!Assembler::is_simm13(offset + (type == T_LONG) ? wordSize : 0)) {
    assert(base != O7, "destroying register");
    assert(!unaligned, "can't handle this");
    // for offsets larger than a simm13 we setup the offset in O7
    __ set(offset, O7);
    load_offset = load(base, O7, to_reg, type, wide);
  } else {
    load_offset = code_offset();
    switch(type) {
      case T_BOOLEAN: // fall through
      case T_BYTE  : __ ldsb(base, offset, to_reg->as_register()); break;
      case T_CHAR  : __ lduh(base, offset, to_reg->as_register()); break;
      case T_SHORT : __ ldsh(base, offset, to_reg->as_register()); break;
      case T_INT   : __ ld(base, offset, to_reg->as_register()); break;
      case T_LONG  :
        if (!unaligned) {
#ifdef _LP64
          __ ldx(base, offset, to_reg->as_register_lo());
#else
          assert(to_reg->as_register_hi()->successor() == to_reg->as_register_lo(),
                 "must be sequential");
          __ ldd(base, offset, to_reg->as_register_hi());
#endif
        } else {
#ifdef _LP64
          assert(base != to_reg->as_register_lo(), "can't handle this");
          assert(O7 != to_reg->as_register_lo(), "can't handle this");
          __ ld(base, offset + hi_word_offset_in_bytes, to_reg->as_register_lo());
          __ lduw(base, offset + lo_word_offset_in_bytes, O7); // in case O7 is base or offset, use it last
          __ sllx(to_reg->as_register_lo(), 32, to_reg->as_register_lo());
          __ or3(to_reg->as_register_lo(), O7, to_reg->as_register_lo());
#else
          if (base == to_reg->as_register_lo()) {
            __ ld(base, offset + hi_word_offset_in_bytes, to_reg->as_register_hi());
            __ ld(base, offset + lo_word_offset_in_bytes, to_reg->as_register_lo());
          } else {
            __ ld(base, offset + lo_word_offset_in_bytes, to_reg->as_register_lo());
            __ ld(base, offset + hi_word_offset_in_bytes, to_reg->as_register_hi());
          }
#endif
        }
        break;
      case T_ADDRESS:  __ ld_ptr(base, offset, to_reg->as_register()); break;
      case T_ARRAY : // fall through
      case T_OBJECT:
        {
          if (UseCompressedOops && !wide) {
            __ lduw(base, offset, to_reg->as_register());
            __ decode_heap_oop(to_reg->as_register());
          } else {
            __ ld_ptr(base, offset, to_reg->as_register());
          }
          break;
        }
      case T_FLOAT:  __ ldf(FloatRegisterImpl::S, base, offset, to_reg->as_float_reg()); break;
      case T_DOUBLE:
        {
          FloatRegister reg = to_reg->as_double_reg();
          // split unaligned loads
          if (unaligned || PatchALot) {
            __ ldf(FloatRegisterImpl::S, base, offset + 4, reg->successor());
            __ ldf(FloatRegisterImpl::S, base, offset,     reg);
          } else {
            __ ldf(FloatRegisterImpl::D, base, offset, to_reg->as_double_reg());
          }
          break;
        }
      default      : ShouldNotReachHere();
    }
    if (type == T_ARRAY || type == T_OBJECT) {
      __ verify_oop(to_reg->as_register());
    }
  }
  return load_offset;
}


int LIR_Assembler::load(Register base, Register disp, LIR_Opr to_reg, BasicType type, bool wide) {
  int load_offset = code_offset();
  switch(type) {
    case T_BOOLEAN: // fall through
    case T_BYTE  :  __ ldsb(base, disp, to_reg->as_register()); break;
    case T_CHAR  :  __ lduh(base, disp, to_reg->as_register()); break;
    case T_SHORT :  __ ldsh(base, disp, to_reg->as_register()); break;
    case T_INT   :  __ ld(base, disp, to_reg->as_register()); break;
    case T_ADDRESS: __ ld_ptr(base, disp, to_reg->as_register()); break;
    case T_ARRAY : // fall through
    case T_OBJECT:
      {
          if (UseCompressedOops && !wide) {
            __ lduw(base, disp, to_reg->as_register());
            __ decode_heap_oop(to_reg->as_register());
          } else {
            __ ld_ptr(base, disp, to_reg->as_register());
          }
          break;
      }
    case T_FLOAT:  __ ldf(FloatRegisterImpl::S, base, disp, to_reg->as_float_reg()); break;
    case T_DOUBLE: __ ldf(FloatRegisterImpl::D, base, disp, to_reg->as_double_reg()); break;
    case T_LONG  :
#ifdef _LP64
      __ ldx(base, disp, to_reg->as_register_lo());
#else
      assert(to_reg->as_register_hi()->successor() == to_reg->as_register_lo(),
             "must be sequential");
      __ ldd(base, disp, to_reg->as_register_hi());
#endif
      break;
    default      : ShouldNotReachHere();
  }
  if (type == T_ARRAY || type == T_OBJECT) {
    __ verify_oop(to_reg->as_register());
  }
  return load_offset;
}

void LIR_Assembler::const2stack(LIR_Opr src, LIR_Opr dest) {
  LIR_Const* c = src->as_constant_ptr();
  switch (c->type()) {
    case T_INT:
    case T_FLOAT: {
      Register src_reg = O7;
      int value = c->as_jint_bits();
      if (value == 0) {
        src_reg = G0;
      } else {
        __ set(value, O7);
      }
      Address addr = frame_map()->address_for_slot(dest->single_stack_ix());
      __ stw(src_reg, addr.base(), addr.disp());
      break;
    }
    case T_ADDRESS: {
      Register src_reg = O7;
      int value = c->as_jint_bits();
      if (value == 0) {
        src_reg = G0;
      } else {
        __ set(value, O7);
      }
      Address addr = frame_map()->address_for_slot(dest->single_stack_ix());
      __ st_ptr(src_reg, addr.base(), addr.disp());
      break;
    }
    case T_OBJECT: {
      Register src_reg = O7;
      jobject2reg(c->as_jobject(), src_reg);
      Address addr = frame_map()->address_for_slot(dest->single_stack_ix());
      __ st_ptr(src_reg, addr.base(), addr.disp());
      break;
    }
    case T_LONG:
    case T_DOUBLE: {
      Address addr = frame_map()->address_for_double_slot(dest->double_stack_ix());

      Register tmp = O7;
      int value_lo = c->as_jint_lo_bits();
      if (value_lo == 0) {
        tmp = G0;
      } else {
        __ set(value_lo, O7);
      }
      __ stw(tmp, addr.base(), addr.disp() + lo_word_offset_in_bytes);
      int value_hi = c->as_jint_hi_bits();
      if (value_hi == 0) {
        tmp = G0;
      } else {
        __ set(value_hi, O7);
      }
      __ stw(tmp, addr.base(), addr.disp() + hi_word_offset_in_bytes);
      break;
    }
    default:
      Unimplemented();
  }
}


void LIR_Assembler::const2mem(LIR_Opr src, LIR_Opr dest, BasicType type, CodeEmitInfo* info, bool wide) {
  LIR_Const* c = src->as_constant_ptr();
  LIR_Address* addr     = dest->as_address_ptr();
  Register base = addr->base()->as_pointer_register();
  int offset = -1;

  switch (c->type()) {
    case T_INT:
    case T_FLOAT:
    case T_ADDRESS: {
      LIR_Opr tmp = FrameMap::O7_opr;
      int value = c->as_jint_bits();
      if (value == 0) {
        tmp = FrameMap::G0_opr;
      } else if (Assembler::is_simm13(value)) {
        __ set(value, O7);
      }
      if (addr->index()->is_valid()) {
        assert(addr->disp() == 0, "must be zero");
        offset = store(tmp, base, addr->index()->as_pointer_register(), type, wide);
      } else {
        assert(Assembler::is_simm13(addr->disp()), "can't handle larger addresses");
        offset = store(tmp, base, addr->disp(), type, wide, false);
      }
      break;
    }
    case T_LONG:
    case T_DOUBLE: {
      assert(!addr->index()->is_valid(), "can't handle reg reg address here");
      assert(Assembler::is_simm13(addr->disp()) &&
             Assembler::is_simm13(addr->disp() + 4), "can't handle larger addresses");

      LIR_Opr tmp = FrameMap::O7_opr;
      int value_lo = c->as_jint_lo_bits();
      if (value_lo == 0) {
        tmp = FrameMap::G0_opr;
      } else {
        __ set(value_lo, O7);
      }
      offset = store(tmp, base, addr->disp() + lo_word_offset_in_bytes, T_INT, wide, false);
      int value_hi = c->as_jint_hi_bits();
      if (value_hi == 0) {
        tmp = FrameMap::G0_opr;
      } else {
        __ set(value_hi, O7);
      }
      offset = store(tmp, base, addr->disp() + hi_word_offset_in_bytes, T_INT, wide, false);
      break;
    }
    case T_OBJECT: {
      jobject obj = c->as_jobject();
      LIR_Opr tmp;
      if (obj == NULL) {
        tmp = FrameMap::G0_opr;
      } else {
        tmp = FrameMap::O7_opr;
        jobject2reg(c->as_jobject(), O7);
      }
      // handle either reg+reg or reg+disp address
      if (addr->index()->is_valid()) {
        assert(addr->disp() == 0, "must be zero");
        offset = store(tmp, base, addr->index()->as_pointer_register(), type, wide);
      } else {
        assert(Assembler::is_simm13(addr->disp()), "can't handle larger addresses");
        offset = store(tmp, base, addr->disp(), type, wide, false);
      }

      break;
    }
    default:
      Unimplemented();
  }
  if (info != NULL) {
    assert(offset != -1, "offset should've been set");
    add_debug_info_for_null_check(offset, info);
  }
}


void LIR_Assembler::const2reg(LIR_Opr src, LIR_Opr dest, LIR_PatchCode patch_code, CodeEmitInfo* info) {
  LIR_Const* c = src->as_constant_ptr();
  LIR_Opr to_reg = dest;

  switch (c->type()) {
    case T_INT:
    case T_ADDRESS:
      {
        jint con = c->as_jint();
        if (to_reg->is_single_cpu()) {
          assert(patch_code == lir_patch_none, "no patching handled here");
          __ set(con, to_reg->as_register());
        } else {
          ShouldNotReachHere();
          assert(to_reg->is_single_fpu(), "wrong register kind");

          __ set(con, O7);
          Address temp_slot(SP, (frame::register_save_words * wordSize) + STACK_BIAS);
          __ st(O7, temp_slot);
          __ ldf(FloatRegisterImpl::S, temp_slot, to_reg->as_float_reg());
        }
      }
      break;

    case T_LONG:
      {
        jlong con = c->as_jlong();

        if (to_reg->is_double_cpu()) {
#ifdef _LP64
          __ set(con,  to_reg->as_register_lo());
#else
          __ set(low(con),  to_reg->as_register_lo());
          __ set(high(con), to_reg->as_register_hi());
#endif
#ifdef _LP64
        } else if (to_reg->is_single_cpu()) {
          __ set(con, to_reg->as_register());
#endif
        } else {
          ShouldNotReachHere();
          assert(to_reg->is_double_fpu(), "wrong register kind");
          Address temp_slot_lo(SP, ((frame::register_save_words  ) * wordSize) + STACK_BIAS);
          Address temp_slot_hi(SP, ((frame::register_save_words) * wordSize) + (longSize/2) + STACK_BIAS);
          __ set(low(con),  O7);
          __ st(O7, temp_slot_lo);
          __ set(high(con), O7);
          __ st(O7, temp_slot_hi);
          __ ldf(FloatRegisterImpl::D, temp_slot_lo, to_reg->as_double_reg());
        }
      }
      break;

    case T_OBJECT:
      {
        if (patch_code == lir_patch_none) {
          jobject2reg(c->as_jobject(), to_reg->as_register());
        } else {
          jobject2reg_with_patching(to_reg->as_register(), info);
        }
      }
      break;

    case T_FLOAT:
      {
        address const_addr = __ float_constant(c->as_jfloat());
        if (const_addr == NULL) {
          bailout("const section overflow");
          break;
        }
        RelocationHolder rspec = internal_word_Relocation::spec(const_addr);
        AddressLiteral const_addrlit(const_addr, rspec);
        if (to_reg->is_single_fpu()) {
          __ patchable_sethi(const_addrlit, O7);
          __ relocate(rspec);
          __ ldf(FloatRegisterImpl::S, O7, const_addrlit.low10(), to_reg->as_float_reg());

        } else {
          assert(to_reg->is_single_cpu(), "Must be a cpu register.");

          __ set(const_addrlit, O7);
          __ ld(O7, 0, to_reg->as_register());
        }
      }
      break;

    case T_DOUBLE:
      {
        address const_addr = __ double_constant(c->as_jdouble());
        if (const_addr == NULL) {
          bailout("const section overflow");
          break;
        }
        RelocationHolder rspec = internal_word_Relocation::spec(const_addr);

        if (to_reg->is_double_fpu()) {
          AddressLiteral const_addrlit(const_addr, rspec);
          __ patchable_sethi(const_addrlit, O7);
          __ relocate(rspec);
          __ ldf (FloatRegisterImpl::D, O7, const_addrlit.low10(), to_reg->as_double_reg());
        } else {
          assert(to_reg->is_double_cpu(), "Must be a long register.");
#ifdef _LP64
          __ set(jlong_cast(c->as_jdouble()), to_reg->as_register_lo());
#else
          __ set(low(jlong_cast(c->as_jdouble())), to_reg->as_register_lo());
          __ set(high(jlong_cast(c->as_jdouble())), to_reg->as_register_hi());
#endif
        }

      }
      break;

    default:
      ShouldNotReachHere();
  }
}

Address LIR_Assembler::as_Address(LIR_Address* addr) {
  Register reg = addr->base()->as_register();
  return Address(reg, addr->disp());
}


void LIR_Assembler::stack2stack(LIR_Opr src, LIR_Opr dest, BasicType type) {
  switch (type) {
    case T_INT:
    case T_FLOAT: {
      Register tmp = O7;
      Address from = frame_map()->address_for_slot(src->single_stack_ix());
      Address to   = frame_map()->address_for_slot(dest->single_stack_ix());
      __ lduw(from.base(), from.disp(), tmp);
      __ stw(tmp, to.base(), to.disp());
      break;
    }
    case T_OBJECT: {
      Register tmp = O7;
      Address from = frame_map()->address_for_slot(src->single_stack_ix());
      Address to   = frame_map()->address_for_slot(dest->single_stack_ix());
      __ ld_ptr(from.base(), from.disp(), tmp);
      __ st_ptr(tmp, to.base(), to.disp());
      break;
    }
    case T_LONG:
    case T_DOUBLE: {
      Register tmp = O7;
      Address from = frame_map()->address_for_double_slot(src->double_stack_ix());
      Address to   = frame_map()->address_for_double_slot(dest->double_stack_ix());
      __ lduw(from.base(), from.disp(), tmp);
      __ stw(tmp, to.base(), to.disp());
      __ lduw(from.base(), from.disp() + 4, tmp);
      __ stw(tmp, to.base(), to.disp() + 4);
      break;
    }

    default:
      ShouldNotReachHere();
  }
}


Address LIR_Assembler::as_Address_hi(LIR_Address* addr) {
  Address base = as_Address(addr);
  return Address(base.base(), base.disp() + hi_word_offset_in_bytes);
}


Address LIR_Assembler::as_Address_lo(LIR_Address* addr) {
  Address base = as_Address(addr);
  return Address(base.base(), base.disp() + lo_word_offset_in_bytes);
}


void LIR_Assembler::mem2reg(LIR_Opr src_opr, LIR_Opr dest, BasicType type,
                            LIR_PatchCode patch_code, CodeEmitInfo* info, bool wide, bool unaligned) {

  LIR_Address* addr = src_opr->as_address_ptr();
  LIR_Opr to_reg = dest;

  Register src = addr->base()->as_pointer_register();
  Register disp_reg = noreg;
  int disp_value = addr->disp();
  bool needs_patching = (patch_code != lir_patch_none);

  if (addr->base()->type() == T_OBJECT) {
    __ verify_oop(src);
  }

  PatchingStub* patch = NULL;
  if (needs_patching) {
    patch = new PatchingStub(_masm, PatchingStub::access_field_id);
    assert(!to_reg->is_double_cpu() ||
           patch_code == lir_patch_none ||
           patch_code == lir_patch_normal, "patching doesn't match register");
  }

  if (addr->index()->is_illegal()) {
    if (!Assembler::is_simm13(disp_value) && (!unaligned || Assembler::is_simm13(disp_value + 4))) {
      if (needs_patching) {
        __ patchable_set(0, O7);
      } else {
        __ set(disp_value, O7);
      }
      disp_reg = O7;
    }
  } else if (unaligned || PatchALot) {
    __ add(src, addr->index()->as_register(), O7);
    src = O7;
  } else {
    disp_reg = addr->index()->as_pointer_register();
    assert(disp_value == 0, "can't handle 3 operand addresses");
  }

  // remember the offset of the load.  The patching_epilog must be done
  // before the call to add_debug_info, otherwise the PcDescs don't get
  // entered in increasing order.
  int offset = code_offset();

  assert(disp_reg != noreg || Assembler::is_simm13(disp_value), "should have set this up");
  if (disp_reg == noreg) {
    offset = load(src, disp_value, to_reg, type, wide, unaligned);
  } else {
    assert(!unaligned, "can't handle this");
    offset = load(src, disp_reg, to_reg, type, wide);
  }

  if (patch != NULL) {
    patching_epilog(patch, patch_code, src, info);
  }
  if (info != NULL) add_debug_info_for_null_check(offset, info);
}


void LIR_Assembler::prefetchr(LIR_Opr src) {
  LIR_Address* addr = src->as_address_ptr();
  Address from_addr = as_Address(addr);

  if (VM_Version::has_v9()) {
    __ prefetch(from_addr, Assembler::severalReads);
  }
}


void LIR_Assembler::prefetchw(LIR_Opr src) {
  LIR_Address* addr = src->as_address_ptr();
  Address from_addr = as_Address(addr);

  if (VM_Version::has_v9()) {
    __ prefetch(from_addr, Assembler::severalWritesAndPossiblyReads);
  }
}


void LIR_Assembler::stack2reg(LIR_Opr src, LIR_Opr dest, BasicType type) {
  Address addr;
  if (src->is_single_word()) {
    addr = frame_map()->address_for_slot(src->single_stack_ix());
  } else if (src->is_double_word())  {
    addr = frame_map()->address_for_double_slot(src->double_stack_ix());
  }

  bool unaligned = (addr.disp() - STACK_BIAS) % 8 != 0;
  load(addr.base(), addr.disp(), dest, dest->type(), true /*wide*/, unaligned);
}


void LIR_Assembler::reg2stack(LIR_Opr from_reg, LIR_Opr dest, BasicType type, bool pop_fpu_stack) {
  Address addr;
  if (dest->is_single_word()) {
    addr = frame_map()->address_for_slot(dest->single_stack_ix());
  } else if (dest->is_double_word())  {
    addr = frame_map()->address_for_slot(dest->double_stack_ix());
  }
  bool unaligned = (addr.disp() - STACK_BIAS) % 8 != 0;
  store(from_reg, addr.base(), addr.disp(), from_reg->type(), true /*wide*/, unaligned);
}


void LIR_Assembler::reg2reg(LIR_Opr from_reg, LIR_Opr to_reg) {
  if (from_reg->is_float_kind() && to_reg->is_float_kind()) {
    if (from_reg->is_double_fpu()) {
      // double to double moves
      assert(to_reg->is_double_fpu(), "should match");
      __ fmov(FloatRegisterImpl::D, from_reg->as_double_reg(), to_reg->as_double_reg());
    } else {
      // float to float moves
      assert(to_reg->is_single_fpu(), "should match");
      __ fmov(FloatRegisterImpl::S, from_reg->as_float_reg(), to_reg->as_float_reg());
    }
  } else if (!from_reg->is_float_kind() && !to_reg->is_float_kind()) {
    if (from_reg->is_double_cpu()) {
#ifdef _LP64
      __ mov(from_reg->as_pointer_register(), to_reg->as_pointer_register());
#else
      assert(to_reg->is_double_cpu() &&
             from_reg->as_register_hi() != to_reg->as_register_lo() &&
             from_reg->as_register_lo() != to_reg->as_register_hi(),
             "should both be long and not overlap");
      // long to long moves
      __ mov(from_reg->as_register_hi(), to_reg->as_register_hi());
      __ mov(from_reg->as_register_lo(), to_reg->as_register_lo());
#endif
#ifdef _LP64
    } else if (to_reg->is_double_cpu()) {
      // int to int moves
      __ mov(from_reg->as_register(), to_reg->as_register_lo());
#endif
    } else {
      // int to int moves
      __ mov(from_reg->as_register(), to_reg->as_register());
    }
  } else {
    ShouldNotReachHere();
  }
  if (to_reg->type() == T_OBJECT || to_reg->type() == T_ARRAY) {
    __ verify_oop(to_reg->as_register());
  }
}


void LIR_Assembler::reg2mem(LIR_Opr from_reg, LIR_Opr dest, BasicType type,
                            LIR_PatchCode patch_code, CodeEmitInfo* info, bool pop_fpu_stack,
                            bool wide, bool unaligned) {
  LIR_Address* addr = dest->as_address_ptr();

  Register src = addr->base()->as_pointer_register();
  Register disp_reg = noreg;
  int disp_value = addr->disp();
  bool needs_patching = (patch_code != lir_patch_none);

  if (addr->base()->is_oop_register()) {
    __ verify_oop(src);
  }

  PatchingStub* patch = NULL;
  if (needs_patching) {
    patch = new PatchingStub(_masm, PatchingStub::access_field_id);
    assert(!from_reg->is_double_cpu() ||
           patch_code == lir_patch_none ||
           patch_code == lir_patch_normal, "patching doesn't match register");
  }

  if (addr->index()->is_illegal()) {
    if (!Assembler::is_simm13(disp_value) && (!unaligned || Assembler::is_simm13(disp_value + 4))) {
      if (needs_patching) {
        __ patchable_set(0, O7);
      } else {
        __ set(disp_value, O7);
      }
      disp_reg = O7;
    }
  } else if (unaligned || PatchALot) {
    __ add(src, addr->index()->as_register(), O7);
    src = O7;
  } else {
    disp_reg = addr->index()->as_pointer_register();
    assert(disp_value == 0, "can't handle 3 operand addresses");
  }

  // remember the offset of the store.  The patching_epilog must be done
  // before the call to add_debug_info_for_null_check, otherwise the PcDescs don't get
  // entered in increasing order.
  int offset;

  assert(disp_reg != noreg || Assembler::is_simm13(disp_value), "should have set this up");
  if (disp_reg == noreg) {
    offset = store(from_reg, src, disp_value, type, wide, unaligned);
  } else {
    assert(!unaligned, "can't handle this");
    offset = store(from_reg, src, disp_reg, type, wide);
  }

  if (patch != NULL) {
    patching_epilog(patch, patch_code, src, info);
  }

  if (info != NULL) add_debug_info_for_null_check(offset, info);
}


void LIR_Assembler::return_op(LIR_Opr result) {
  // the poll may need a register so just pick one that isn't the return register
#if defined(TIERED) && !defined(_LP64)
  if (result->type_field() == LIR_OprDesc::long_type) {
    // Must move the result to G1
    // Must leave proper result in O0,O1 and G1 (TIERED only)
    __ sllx(I0, 32, G1);          // Shift bits into high G1
    __ srl (I1, 0, I1);           // Zero extend O1 (harmless?)
    __ or3 (I1, G1, G1);          // OR 64 bits into G1
#ifdef ASSERT
    // mangle it so any problems will show up
    __ set(0xdeadbeef, I0);
    __ set(0xdeadbeef, I1);
#endif
  }
#endif // TIERED
  __ set((intptr_t)os::get_polling_page(), L0);
  __ relocate(relocInfo::poll_return_type);
  __ ld_ptr(L0, 0, G0);
  __ ret();
  __ delayed()->restore();
}


int LIR_Assembler::safepoint_poll(LIR_Opr tmp, CodeEmitInfo* info) {
  __ set((intptr_t)os::get_polling_page(), tmp->as_register());
  if (info != NULL) {
    add_debug_info_for_branch(info);
  } else {
    __ relocate(relocInfo::poll_type);
  }

  int offset = __ offset();
  __ ld_ptr(tmp->as_register(), 0, G0);

  return offset;
}


void LIR_Assembler::emit_static_call_stub() {
  address call_pc = __ pc();
  address stub = __ start_a_stub(call_stub_size);
  if (stub == NULL) {
    bailout("static call stub overflow");
    return;
  }

  int start = __ offset();
  __ relocate(static_stub_Relocation::spec(call_pc));

  __ set_oop(NULL, G5);
  // must be set to -1 at code generation time
  AddressLiteral addrlit(-1);
  __ jump_to(addrlit, G3);
  __ delayed()->nop();

  assert(__ offset() - start <= call_stub_size, "stub too big");
  __ end_a_stub();
}


void LIR_Assembler::comp_op(LIR_Condition condition, LIR_Opr opr1, LIR_Opr opr2, LIR_Op2* op) {
  if (opr1->is_single_fpu()) {
    __ fcmp(FloatRegisterImpl::S, Assembler::fcc0, opr1->as_float_reg(), opr2->as_float_reg());
  } else if (opr1->is_double_fpu()) {
    __ fcmp(FloatRegisterImpl::D, Assembler::fcc0, opr1->as_double_reg(), opr2->as_double_reg());
  } else if (opr1->is_single_cpu()) {
    if (opr2->is_constant()) {
      switch (opr2->as_constant_ptr()->type()) {
        case T_INT:
          { jint con = opr2->as_constant_ptr()->as_jint();
            if (Assembler::is_simm13(con)) {
              __ cmp(opr1->as_register(), con);
            } else {
              __ set(con, O7);
              __ cmp(opr1->as_register(), O7);
            }
          }
          break;

        case T_OBJECT:
          // there are only equal/notequal comparisions on objects
          { jobject con = opr2->as_constant_ptr()->as_jobject();
            if (con == NULL) {
              __ cmp(opr1->as_register(), 0);
            } else {
              jobject2reg(con, O7);
              __ cmp(opr1->as_register(), O7);
            }
          }
          break;

        default:
          ShouldNotReachHere();
          break;
      }
    } else {
      if (opr2->is_address()) {
        LIR_Address * addr = opr2->as_address_ptr();
        BasicType type = addr->type();
        if ( type == T_OBJECT ) __ ld_ptr(as_Address(addr), O7);
        else                    __ ld(as_Address(addr), O7);
        __ cmp(opr1->as_register(), O7);
      } else {
        __ cmp(opr1->as_register(), opr2->as_register());
      }
    }
  } else if (opr1->is_double_cpu()) {
    Register xlo = opr1->as_register_lo();
    Register xhi = opr1->as_register_hi();
    if (opr2->is_constant() && opr2->as_jlong() == 0) {
      assert(condition == lir_cond_equal || condition == lir_cond_notEqual, "only handles these cases");
#ifdef _LP64
      __ orcc(xhi, G0, G0);
#else
      __ orcc(xhi, xlo, G0);
#endif
    } else if (opr2->is_register()) {
      Register ylo = opr2->as_register_lo();
      Register yhi = opr2->as_register_hi();
#ifdef _LP64
      __ cmp(xlo, ylo);
#else
      __ subcc(xlo, ylo, xlo);
      __ subccc(xhi, yhi, xhi);
      if (condition == lir_cond_equal || condition == lir_cond_notEqual) {
        __ orcc(xhi, xlo, G0);
      }
#endif
    } else {
      ShouldNotReachHere();
    }
  } else if (opr1->is_address()) {
    LIR_Address * addr = opr1->as_address_ptr();
    BasicType type = addr->type();
    assert (opr2->is_constant(), "Checking");
    if ( type == T_OBJECT ) __ ld_ptr(as_Address(addr), O7);
    else                    __ ld(as_Address(addr), O7);
    __ cmp(O7, opr2->as_constant_ptr()->as_jint());
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::comp_fl2i(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dst, LIR_Op2* op){
  if (code == lir_cmp_fd2i || code == lir_ucmp_fd2i) {
    bool is_unordered_less = (code == lir_ucmp_fd2i);
    if (left->is_single_fpu()) {
      __ float_cmp(true, is_unordered_less ? -1 : 1, left->as_float_reg(), right->as_float_reg(), dst->as_register());
    } else if (left->is_double_fpu()) {
      __ float_cmp(false, is_unordered_less ? -1 : 1, left->as_double_reg(), right->as_double_reg(), dst->as_register());
    } else {
      ShouldNotReachHere();
    }
  } else if (code == lir_cmp_l2i) {
#ifdef _LP64
    __ lcmp(left->as_register_lo(), right->as_register_lo(), dst->as_register());
#else
    __ lcmp(left->as_register_hi(),  left->as_register_lo(),
            right->as_register_hi(), right->as_register_lo(),
            dst->as_register());
#endif
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::cmove(LIR_Condition condition, LIR_Opr opr1, LIR_Opr opr2, LIR_Opr result) {

  Assembler::Condition acond;
  switch (condition) {
    case lir_cond_equal:        acond = Assembler::equal;        break;
    case lir_cond_notEqual:     acond = Assembler::notEqual;     break;
    case lir_cond_less:         acond = Assembler::less;         break;
    case lir_cond_lessEqual:    acond = Assembler::lessEqual;    break;
    case lir_cond_greaterEqual: acond = Assembler::greaterEqual; break;
    case lir_cond_greater:      acond = Assembler::greater;      break;
    case lir_cond_aboveEqual:   acond = Assembler::greaterEqualUnsigned;      break;
    case lir_cond_belowEqual:   acond = Assembler::lessEqualUnsigned;      break;
    default:                         ShouldNotReachHere();
  };

  if (opr1->is_constant() && opr1->type() == T_INT) {
    Register dest = result->as_register();
    // load up first part of constant before branch
    // and do the rest in the delay slot.
    if (!Assembler::is_simm13(opr1->as_jint())) {
      __ sethi(opr1->as_jint(), dest);
    }
  } else if (opr1->is_constant()) {
    const2reg(opr1, result, lir_patch_none, NULL);
  } else if (opr1->is_register()) {
    reg2reg(opr1, result);
  } else if (opr1->is_stack()) {
    stack2reg(opr1, result, result->type());
  } else {
    ShouldNotReachHere();
  }
  Label skip;
  __ br(acond, false, Assembler::pt, skip);
  if (opr1->is_constant() && opr1->type() == T_INT) {
    Register dest = result->as_register();
    if (Assembler::is_simm13(opr1->as_jint())) {
      __ delayed()->or3(G0, opr1->as_jint(), dest);
    } else {
      // the sethi has been done above, so just put in the low 10 bits
      __ delayed()->or3(dest, opr1->as_jint() & 0x3ff, dest);
    }
  } else {
    // can't do anything useful in the delay slot
    __ delayed()->nop();
  }
  if (opr2->is_constant()) {
    const2reg(opr2, result, lir_patch_none, NULL);
  } else if (opr2->is_register()) {
    reg2reg(opr2, result);
  } else if (opr2->is_stack()) {
    stack2reg(opr2, result, result->type());
  } else {
    ShouldNotReachHere();
  }
  __ bind(skip);
}


void LIR_Assembler::arith_op(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest, CodeEmitInfo* info, bool pop_fpu_stack) {
  assert(info == NULL, "unused on this code path");
  assert(left->is_register(), "wrong items state");
  assert(dest->is_register(), "wrong items state");

  if (right->is_register()) {
    if (dest->is_float_kind()) {

      FloatRegister lreg, rreg, res;
      FloatRegisterImpl::Width w;
      if (right->is_single_fpu()) {
        w = FloatRegisterImpl::S;
        lreg = left->as_float_reg();
        rreg = right->as_float_reg();
        res  = dest->as_float_reg();
      } else {
        w = FloatRegisterImpl::D;
        lreg = left->as_double_reg();
        rreg = right->as_double_reg();
        res  = dest->as_double_reg();
      }

      switch (code) {
        case lir_add: __ fadd(w, lreg, rreg, res); break;
        case lir_sub: __ fsub(w, lreg, rreg, res); break;
        case lir_mul: // fall through
        case lir_mul_strictfp: __ fmul(w, lreg, rreg, res); break;
        case lir_div: // fall through
        case lir_div_strictfp: __ fdiv(w, lreg, rreg, res); break;
        default: ShouldNotReachHere();
      }

    } else if (dest->is_double_cpu()) {
#ifdef _LP64
      Register dst_lo = dest->as_register_lo();
      Register op1_lo = left->as_pointer_register();
      Register op2_lo = right->as_pointer_register();

      switch (code) {
        case lir_add:
          __ add(op1_lo, op2_lo, dst_lo);
          break;

        case lir_sub:
          __ sub(op1_lo, op2_lo, dst_lo);
          break;

        default: ShouldNotReachHere();
      }
#else
      Register op1_lo = left->as_register_lo();
      Register op1_hi = left->as_register_hi();
      Register op2_lo = right->as_register_lo();
      Register op2_hi = right->as_register_hi();
      Register dst_lo = dest->as_register_lo();
      Register dst_hi = dest->as_register_hi();

      switch (code) {
        case lir_add:
          __ addcc(op1_lo, op2_lo, dst_lo);
          __ addc (op1_hi, op2_hi, dst_hi);
          break;

        case lir_sub:
          __ subcc(op1_lo, op2_lo, dst_lo);
          __ subc (op1_hi, op2_hi, dst_hi);
          break;

        default: ShouldNotReachHere();
      }
#endif
    } else {
      assert (right->is_single_cpu(), "Just Checking");

      Register lreg = left->as_register();
      Register res  = dest->as_register();
      Register rreg = right->as_register();
      switch (code) {
        case lir_add:  __ add  (lreg, rreg, res); break;
        case lir_sub:  __ sub  (lreg, rreg, res); break;
        case lir_mul:  __ mult (lreg, rreg, res); break;
        default: ShouldNotReachHere();
      }
    }
  } else {
    assert (right->is_constant(), "must be constant");

    if (dest->is_single_cpu()) {
      Register lreg = left->as_register();
      Register res  = dest->as_register();
      int    simm13 = right->as_constant_ptr()->as_jint();

      switch (code) {
        case lir_add:  __ add  (lreg, simm13, res); break;
        case lir_sub:  __ sub  (lreg, simm13, res); break;
        case lir_mul:  __ mult (lreg, simm13, res); break;
        default: ShouldNotReachHere();
      }
    } else {
      Register lreg = left->as_pointer_register();
      Register res  = dest->as_register_lo();
      long con = right->as_constant_ptr()->as_jlong();
      assert(Assembler::is_simm13(con), "must be simm13");

      switch (code) {
        case lir_add:  __ add  (lreg, (int)con, res); break;
        case lir_sub:  __ sub  (lreg, (int)con, res); break;
        case lir_mul:  __ mult (lreg, (int)con, res); break;
        default: ShouldNotReachHere();
      }
    }
  }
}


void LIR_Assembler::fpop() {
  // do nothing
}


void LIR_Assembler::intrinsic_op(LIR_Code code, LIR_Opr value, LIR_Opr thread, LIR_Opr dest, LIR_Op* op) {
  switch (code) {
    case lir_sin:
    case lir_tan:
    case lir_cos: {
      assert(thread->is_valid(), "preserve the thread object for performance reasons");
      assert(dest->as_double_reg() == F0, "the result will be in f0/f1");
      break;
    }
    case lir_sqrt: {
      assert(!thread->is_valid(), "there is no need for a thread_reg for dsqrt");
      FloatRegister src_reg = value->as_double_reg();
      FloatRegister dst_reg = dest->as_double_reg();
      __ fsqrt(FloatRegisterImpl::D, src_reg, dst_reg);
      break;
    }
    case lir_abs: {
      assert(!thread->is_valid(), "there is no need for a thread_reg for fabs");
      FloatRegister src_reg = value->as_double_reg();
      FloatRegister dst_reg = dest->as_double_reg();
      __ fabs(FloatRegisterImpl::D, src_reg, dst_reg);
      break;
    }
    default: {
      ShouldNotReachHere();
      break;
    }
  }
}


void LIR_Assembler::logic_op(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest) {
  if (right->is_constant()) {
    if (dest->is_single_cpu()) {
      int simm13 = right->as_constant_ptr()->as_jint();
      switch (code) {
        case lir_logic_and:   __ and3 (left->as_register(), simm13, dest->as_register()); break;
        case lir_logic_or:    __ or3  (left->as_register(), simm13, dest->as_register()); break;
        case lir_logic_xor:   __ xor3 (left->as_register(), simm13, dest->as_register()); break;
        default: ShouldNotReachHere();
      }
    } else {
      long c = right->as_constant_ptr()->as_jlong();
      assert(c == (int)c && Assembler::is_simm13(c), "out of range");
      int simm13 = (int)c;
      switch (code) {
        case lir_logic_and:
#ifndef _LP64
          __ and3 (left->as_register_hi(), 0,      dest->as_register_hi());
#endif
          __ and3 (left->as_register_lo(), simm13, dest->as_register_lo());
          break;

        case lir_logic_or:
#ifndef _LP64
          __ or3 (left->as_register_hi(), 0,      dest->as_register_hi());
#endif
          __ or3 (left->as_register_lo(), simm13, dest->as_register_lo());
          break;

        case lir_logic_xor:
#ifndef _LP64
          __ xor3 (left->as_register_hi(), 0,      dest->as_register_hi());
#endif
          __ xor3 (left->as_register_lo(), simm13, dest->as_register_lo());
          break;

        default: ShouldNotReachHere();
      }
    }
  } else {
    assert(right->is_register(), "right should be in register");

    if (dest->is_single_cpu()) {
      switch (code) {
        case lir_logic_and:   __ and3 (left->as_register(), right->as_register(), dest->as_register()); break;
        case lir_logic_or:    __ or3  (left->as_register(), right->as_register(), dest->as_register()); break;
        case lir_logic_xor:   __ xor3 (left->as_register(), right->as_register(), dest->as_register()); break;
        default: ShouldNotReachHere();
      }
    } else {
#ifdef _LP64
      Register l = (left->is_single_cpu() && left->is_oop_register()) ? left->as_register() :
                                                                        left->as_register_lo();
      Register r = (right->is_single_cpu() && right->is_oop_register()) ? right->as_register() :
                                                                          right->as_register_lo();

      switch (code) {
        case lir_logic_and: __ and3 (l, r, dest->as_register_lo()); break;
        case lir_logic_or:  __ or3  (l, r, dest->as_register_lo()); break;
        case lir_logic_xor: __ xor3 (l, r, dest->as_register_lo()); break;
        default: ShouldNotReachHere();
      }
#else
      switch (code) {
        case lir_logic_and:
          __ and3 (left->as_register_hi(), right->as_register_hi(), dest->as_register_hi());
          __ and3 (left->as_register_lo(), right->as_register_lo(), dest->as_register_lo());
          break;

        case lir_logic_or:
          __ or3 (left->as_register_hi(), right->as_register_hi(), dest->as_register_hi());
          __ or3 (left->as_register_lo(), right->as_register_lo(), dest->as_register_lo());
          break;

        case lir_logic_xor:
          __ xor3 (left->as_register_hi(), right->as_register_hi(), dest->as_register_hi());
          __ xor3 (left->as_register_lo(), right->as_register_lo(), dest->as_register_lo());
          break;

        default: ShouldNotReachHere();
      }
#endif
    }
  }
}


int LIR_Assembler::shift_amount(BasicType t) {
  int elem_size = type2aelembytes(t);
  switch (elem_size) {
    case 1 : return 0;
    case 2 : return 1;
    case 4 : return 2;
    case 8 : return 3;
  }
  ShouldNotReachHere();
  return -1;
}


void LIR_Assembler::throw_op(LIR_Opr exceptionPC, LIR_Opr exceptionOop, CodeEmitInfo* info) {
  assert(exceptionOop->as_register() == Oexception, "should match");
  assert(exceptionPC->as_register() == Oissuing_pc, "should match");

  info->add_register_oop(exceptionOop);

  // reuse the debug info from the safepoint poll for the throw op itself
  address pc_for_athrow  = __ pc();
  int pc_for_athrow_offset = __ offset();
  RelocationHolder rspec = internal_word_Relocation::spec(pc_for_athrow);
  __ set(pc_for_athrow, Oissuing_pc, rspec);
  add_call_info(pc_for_athrow_offset, info); // for exception handler

  __ call(Runtime1::entry_for(Runtime1::handle_exception_id), relocInfo::runtime_call_type);
  __ delayed()->nop();
}


void LIR_Assembler::unwind_op(LIR_Opr exceptionOop) {
  assert(exceptionOop->as_register() == Oexception, "should match");

  __ br(Assembler::always, false, Assembler::pt, _unwind_handler_entry);
  __ delayed()->nop();
}


void LIR_Assembler::emit_arraycopy(LIR_OpArrayCopy* op) {
  Register src = op->src()->as_register();
  Register dst = op->dst()->as_register();
  Register src_pos = op->src_pos()->as_register();
  Register dst_pos = op->dst_pos()->as_register();
  Register length  = op->length()->as_register();
  Register tmp = op->tmp()->as_register();
  Register tmp2 = O7;

  int flags = op->flags();
  ciArrayKlass* default_type = op->expected_type();
  BasicType basic_type = default_type != NULL ? default_type->element_type()->basic_type() : T_ILLEGAL;
  if (basic_type == T_ARRAY) basic_type = T_OBJECT;

  // set up the arraycopy stub information
  ArrayCopyStub* stub = op->stub();

  // always do stub if no type information is available.  it's ok if
  // the known type isn't loaded since the code sanity checks
  // in debug mode and the type isn't required when we know the exact type
  // also check that the type is an array type.
  // We also, for now, always call the stub if the barrier set requires a
  // write_ref_pre barrier (which the stub does, but none of the optimized
  // cases currently does).
  if (op->expected_type() == NULL ||
      Universe::heap()->barrier_set()->has_write_ref_pre_barrier()) {
    __ mov(src,     O0);
    __ mov(src_pos, O1);
    __ mov(dst,     O2);
    __ mov(dst_pos, O3);
    __ mov(length,  O4);
    __ call_VM_leaf(tmp, CAST_FROM_FN_PTR(address, Runtime1::arraycopy));

    __ br_zero(Assembler::less, false, Assembler::pn, O0, *stub->entry());
    __ delayed()->nop();
    __ bind(*stub->continuation());
    return;
  }

  assert(default_type != NULL && default_type->is_array_klass(), "must be true at this point");

  // make sure src and dst are non-null and load array length
  if (flags & LIR_OpArrayCopy::src_null_check) {
    __ tst(src);
    __ brx(Assembler::equal, false, Assembler::pn, *stub->entry());
    __ delayed()->nop();
  }

  if (flags & LIR_OpArrayCopy::dst_null_check) {
    __ tst(dst);
    __ brx(Assembler::equal, false, Assembler::pn, *stub->entry());
    __ delayed()->nop();
  }

  if (flags & LIR_OpArrayCopy::src_pos_positive_check) {
    // test src_pos register
    __ tst(src_pos);
    __ br(Assembler::less, false, Assembler::pn, *stub->entry());
    __ delayed()->nop();
  }

  if (flags & LIR_OpArrayCopy::dst_pos_positive_check) {
    // test dst_pos register
    __ tst(dst_pos);
    __ br(Assembler::less, false, Assembler::pn, *stub->entry());
    __ delayed()->nop();
  }

  if (flags & LIR_OpArrayCopy::length_positive_check) {
    // make sure length isn't negative
    __ tst(length);
    __ br(Assembler::less, false, Assembler::pn, *stub->entry());
    __ delayed()->nop();
  }

  if (flags & LIR_OpArrayCopy::src_range_check) {
    __ ld(src, arrayOopDesc::length_offset_in_bytes(), tmp2);
    __ add(length, src_pos, tmp);
    __ cmp(tmp2, tmp);
    __ br(Assembler::carrySet, false, Assembler::pn, *stub->entry());
    __ delayed()->nop();
  }

  if (flags & LIR_OpArrayCopy::dst_range_check) {
    __ ld(dst, arrayOopDesc::length_offset_in_bytes(), tmp2);
    __ add(length, dst_pos, tmp);
    __ cmp(tmp2, tmp);
    __ br(Assembler::carrySet, false, Assembler::pn, *stub->entry());
    __ delayed()->nop();
  }

  if (flags & LIR_OpArrayCopy::type_check) {
    if (UseCompressedOops) {
      // We don't need decode because we just need to compare
      __ lduw(src, oopDesc::klass_offset_in_bytes(), tmp);
      __ lduw(dst, oopDesc::klass_offset_in_bytes(), tmp2);
      __ cmp(tmp, tmp2);
      __ br(Assembler::notEqual, false, Assembler::pt, *stub->entry());
    } else {
      __ ld_ptr(src, oopDesc::klass_offset_in_bytes(), tmp);
      __ ld_ptr(dst, oopDesc::klass_offset_in_bytes(), tmp2);
      __ cmp(tmp, tmp2);
      __ brx(Assembler::notEqual, false, Assembler::pt, *stub->entry());
    }
    __ delayed()->nop();
  }

#ifdef ASSERT
  if (basic_type != T_OBJECT || !(flags & LIR_OpArrayCopy::type_check)) {
    // Sanity check the known type with the incoming class.  For the
    // primitive case the types must match exactly with src.klass and
    // dst.klass each exactly matching the default type.  For the
    // object array case, if no type check is needed then either the
    // dst type is exactly the expected type and the src type is a
    // subtype which we can't check or src is the same array as dst
    // but not necessarily exactly of type default_type.
    Label known_ok, halt;
    jobject2reg(op->expected_type()->constant_encoding(), tmp);
    if (UseCompressedOops) {
      // tmp holds the default type. It currently comes uncompressed after the
      // load of a constant, so encode it.
      __ encode_heap_oop(tmp);
      // load the raw value of the dst klass, since we will be comparing
      // uncompressed values directly.
      __ lduw(dst, oopDesc::klass_offset_in_bytes(), tmp2);
      if (basic_type != T_OBJECT) {
        __ cmp(tmp, tmp2);
        __ br(Assembler::notEqual, false, Assembler::pn, halt);
        // load the raw value of the src klass.
        __ delayed()->lduw(src, oopDesc::klass_offset_in_bytes(), tmp2);
        __ cmp(tmp, tmp2);
        __ br(Assembler::equal, false, Assembler::pn, known_ok);
        __ delayed()->nop();
      } else {
        __ cmp(tmp, tmp2);
        __ br(Assembler::equal, false, Assembler::pn, known_ok);
        __ delayed()->cmp(src, dst);
        __ brx(Assembler::equal, false, Assembler::pn, known_ok);
        __ delayed()->nop();
      }
    } else {
      __ ld_ptr(dst, oopDesc::klass_offset_in_bytes(), tmp2);
      if (basic_type != T_OBJECT) {
        __ cmp(tmp, tmp2);
        __ brx(Assembler::notEqual, false, Assembler::pn, halt);
        __ delayed()->ld_ptr(src, oopDesc::klass_offset_in_bytes(), tmp2);
        __ cmp(tmp, tmp2);
        __ brx(Assembler::equal, false, Assembler::pn, known_ok);
        __ delayed()->nop();
      } else {
        __ cmp(tmp, tmp2);
        __ brx(Assembler::equal, false, Assembler::pn, known_ok);
        __ delayed()->cmp(src, dst);
        __ brx(Assembler::equal, false, Assembler::pn, known_ok);
        __ delayed()->nop();
      }
    }
    __ bind(halt);
    __ stop("incorrect type information in arraycopy");
    __ bind(known_ok);
  }
#endif

  int shift = shift_amount(basic_type);

  Register src_ptr = O0;
  Register dst_ptr = O1;
  Register len     = O2;

  __ add(src, arrayOopDesc::base_offset_in_bytes(basic_type), src_ptr);
  LP64_ONLY(__ sra(src_pos, 0, src_pos);) //higher 32bits must be null
  if (shift == 0) {
    __ add(src_ptr, src_pos, src_ptr);
  } else {
    __ sll(src_pos, shift, tmp);
    __ add(src_ptr, tmp, src_ptr);
  }

  __ add(dst, arrayOopDesc::base_offset_in_bytes(basic_type), dst_ptr);
  LP64_ONLY(__ sra(dst_pos, 0, dst_pos);) //higher 32bits must be null
  if (shift == 0) {
    __ add(dst_ptr, dst_pos, dst_ptr);
  } else {
    __ sll(dst_pos, shift, tmp);
    __ add(dst_ptr, tmp, dst_ptr);
  }

  if (basic_type != T_OBJECT) {
    if (shift == 0) {
      __ mov(length, len);
    } else {
      __ sll(length, shift, len);
    }
    __ call_VM_leaf(tmp, CAST_FROM_FN_PTR(address, Runtime1::primitive_arraycopy));
  } else {
    // oop_arraycopy takes a length in number of elements, so don't scale it.
    __ mov(length, len);
    __ call_VM_leaf(tmp, CAST_FROM_FN_PTR(address, Runtime1::oop_arraycopy));
  }

  __ bind(*stub->continuation());
}


void LIR_Assembler::shift_op(LIR_Code code, LIR_Opr left, LIR_Opr count, LIR_Opr dest, LIR_Opr tmp) {
  if (dest->is_single_cpu()) {
#ifdef _LP64
    if (left->type() == T_OBJECT) {
      switch (code) {
        case lir_shl:  __ sllx  (left->as_register(), count->as_register(), dest->as_register()); break;
        case lir_shr:  __ srax  (left->as_register(), count->as_register(), dest->as_register()); break;
        case lir_ushr: __ srl   (left->as_register(), count->as_register(), dest->as_register()); break;
        default: ShouldNotReachHere();
      }
    } else
#endif
      switch (code) {
        case lir_shl:  __ sll   (left->as_register(), count->as_register(), dest->as_register()); break;
        case lir_shr:  __ sra   (left->as_register(), count->as_register(), dest->as_register()); break;
        case lir_ushr: __ srl   (left->as_register(), count->as_register(), dest->as_register()); break;
        default: ShouldNotReachHere();
      }
  } else {
#ifdef _LP64
    switch (code) {
      case lir_shl:  __ sllx  (left->as_register_lo(), count->as_register(), dest->as_register_lo()); break;
      case lir_shr:  __ srax  (left->as_register_lo(), count->as_register(), dest->as_register_lo()); break;
      case lir_ushr: __ srlx  (left->as_register_lo(), count->as_register(), dest->as_register_lo()); break;
      default: ShouldNotReachHere();
    }
#else
    switch (code) {
      case lir_shl:  __ lshl  (left->as_register_hi(), left->as_register_lo(), count->as_register(), dest->as_register_hi(), dest->as_register_lo(), G3_scratch); break;
      case lir_shr:  __ lshr  (left->as_register_hi(), left->as_register_lo(), count->as_register(), dest->as_register_hi(), dest->as_register_lo(), G3_scratch); break;
      case lir_ushr: __ lushr (left->as_register_hi(), left->as_register_lo(), count->as_register(), dest->as_register_hi(), dest->as_register_lo(), G3_scratch); break;
      default: ShouldNotReachHere();
    }
#endif
  }
}


void LIR_Assembler::shift_op(LIR_Code code, LIR_Opr left, jint count, LIR_Opr dest) {
#ifdef _LP64
  if (left->type() == T_OBJECT) {
    count = count & 63;  // shouldn't shift by more than sizeof(intptr_t)
    Register l = left->as_register();
    Register d = dest->as_register_lo();
    switch (code) {
      case lir_shl:  __ sllx  (l, count, d); break;
      case lir_shr:  __ srax  (l, count, d); break;
      case lir_ushr: __ srlx  (l, count, d); break;
      default: ShouldNotReachHere();
    }
    return;
  }
#endif

  if (dest->is_single_cpu()) {
    count = count & 0x1F; // Java spec
    switch (code) {
      case lir_shl:  __ sll   (left->as_register(), count, dest->as_register()); break;
      case lir_shr:  __ sra   (left->as_register(), count, dest->as_register()); break;
      case lir_ushr: __ srl   (left->as_register(), count, dest->as_register()); break;
      default: ShouldNotReachHere();
    }
  } else if (dest->is_double_cpu()) {
    count = count & 63; // Java spec
    switch (code) {
      case lir_shl:  __ sllx  (left->as_pointer_register(), count, dest->as_pointer_register()); break;
      case lir_shr:  __ srax  (left->as_pointer_register(), count, dest->as_pointer_register()); break;
      case lir_ushr: __ srlx  (left->as_pointer_register(), count, dest->as_pointer_register()); break;
      default: ShouldNotReachHere();
    }
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::emit_alloc_obj(LIR_OpAllocObj* op) {
  assert(op->tmp1()->as_register()  == G1 &&
         op->tmp2()->as_register()  == G3 &&
         op->tmp3()->as_register()  == G4 &&
         op->obj()->as_register()   == O0 &&
         op->klass()->as_register() == G5, "must be");
  if (op->init_check()) {
    __ ld(op->klass()->as_register(),
          instanceKlass::init_state_offset_in_bytes() + sizeof(oopDesc),
          op->tmp1()->as_register());
    add_debug_info_for_null_check_here(op->stub()->info());
    __ cmp(op->tmp1()->as_register(), instanceKlass::fully_initialized);
    __ br(Assembler::notEqual, false, Assembler::pn, *op->stub()->entry());
    __ delayed()->nop();
  }
  __ allocate_object(op->obj()->as_register(),
                     op->tmp1()->as_register(),
                     op->tmp2()->as_register(),
                     op->tmp3()->as_register(),
                     op->header_size(),
                     op->object_size(),
                     op->klass()->as_register(),
                     *op->stub()->entry());
  __ bind(*op->stub()->continuation());
  __ verify_oop(op->obj()->as_register());
}


void LIR_Assembler::emit_alloc_array(LIR_OpAllocArray* op) {
  assert(op->tmp1()->as_register()  == G1 &&
         op->tmp2()->as_register()  == G3 &&
         op->tmp3()->as_register()  == G4 &&
         op->tmp4()->as_register()  == O1 &&
         op->klass()->as_register() == G5, "must be");
  if (UseSlowPath ||
      (!UseFastNewObjectArray && (op->type() == T_OBJECT || op->type() == T_ARRAY)) ||
      (!UseFastNewTypeArray   && (op->type() != T_OBJECT && op->type() != T_ARRAY))) {
    __ br(Assembler::always, false, Assembler::pt, *op->stub()->entry());
    __ delayed()->nop();
  } else {
    __ allocate_array(op->obj()->as_register(),
                      op->len()->as_register(),
                      op->tmp1()->as_register(),
                      op->tmp2()->as_register(),
                      op->tmp3()->as_register(),
                      arrayOopDesc::header_size(op->type()),
                      type2aelembytes(op->type()),
                      op->klass()->as_register(),
                      *op->stub()->entry());
  }
  __ bind(*op->stub()->continuation());
}


void LIR_Assembler::type_profile_helper(Register mdo, int mdo_offset_bias,
                                        ciMethodData *md, ciProfileData *data,
                                        Register recv, Register tmp1, Label* update_done) {
  uint i;
  for (i = 0; i < VirtualCallData::row_limit(); i++) {
    Label next_test;
    // See if the receiver is receiver[n].
    Address receiver_addr(mdo, md->byte_offset_of_slot(data, ReceiverTypeData::receiver_offset(i)) -
                          mdo_offset_bias);
    __ ld_ptr(receiver_addr, tmp1);
    __ verify_oop(tmp1);
    __ cmp(recv, tmp1);
    __ brx(Assembler::notEqual, false, Assembler::pt, next_test);
    __ delayed()->nop();
    Address data_addr(mdo, md->byte_offset_of_slot(data, ReceiverTypeData::receiver_count_offset(i)) -
                      mdo_offset_bias);
    __ ld_ptr(data_addr, tmp1);
    __ add(tmp1, DataLayout::counter_increment, tmp1);
    __ st_ptr(tmp1, data_addr);
    __ ba(false, *update_done);
    __ delayed()->nop();
    __ bind(next_test);
  }

  // Didn't find receiver; find next empty slot and fill it in
  for (i = 0; i < VirtualCallData::row_limit(); i++) {
    Label next_test;
    Address recv_addr(mdo, md->byte_offset_of_slot(data, ReceiverTypeData::receiver_offset(i)) -
                      mdo_offset_bias);
    __ ld_ptr(recv_addr, tmp1);
    __ br_notnull(tmp1, false, Assembler::pt, next_test);
    __ delayed()->nop();
    __ st_ptr(recv, recv_addr);
    __ set(DataLayout::counter_increment, tmp1);
    __ st_ptr(tmp1, mdo, md->byte_offset_of_slot(data, ReceiverTypeData::receiver_count_offset(i)) -
              mdo_offset_bias);
    __ ba(false, *update_done);
    __ delayed()->nop();
    __ bind(next_test);
  }
}


void LIR_Assembler::setup_md_access(ciMethod* method, int bci,
                                    ciMethodData*& md, ciProfileData*& data, int& mdo_offset_bias) {
  md = method->method_data_or_null();
  assert(md != NULL, "Sanity");
  data = md->bci_to_data(bci);
  assert(data != NULL,       "need data for checkcast");
  assert(data->is_ReceiverTypeData(), "need ReceiverTypeData for type check");
  if (!Assembler::is_simm13(md->byte_offset_of_slot(data, DataLayout::header_offset()) + data->size_in_bytes())) {
    // The offset is large so bias the mdo by the base of the slot so
    // that the ld can use simm13s to reference the slots of the data
    mdo_offset_bias = md->byte_offset_of_slot(data, DataLayout::header_offset());
  }
}

void LIR_Assembler::emit_typecheck_helper(LIR_OpTypeCheck *op, Label* success, Label* failure, Label* obj_is_null) {
  // we always need a stub for the failure case.
  CodeStub* stub = op->stub();
  Register obj = op->object()->as_register();
  Register k_RInfo = op->tmp1()->as_register();
  Register klass_RInfo = op->tmp2()->as_register();
  Register dst = op->result_opr()->as_register();
  Register Rtmp1 = op->tmp3()->as_register();
  ciKlass* k = op->klass();


  if (obj == k_RInfo) {
    k_RInfo = klass_RInfo;
    klass_RInfo = obj;
  }

  ciMethodData* md;
  ciProfileData* data;
  int mdo_offset_bias = 0;
  if (op->should_profile()) {
    ciMethod* method = op->profiled_method();
    assert(method != NULL, "Should have method");
    setup_md_access(method, op->profiled_bci(), md, data, mdo_offset_bias);

    Label not_null;
    __ br_notnull(obj, false, Assembler::pn, not_null);
    __ delayed()->nop();
    Register mdo      = k_RInfo;
    Register data_val = Rtmp1;
    jobject2reg(md->constant_encoding(), mdo);
    if (mdo_offset_bias > 0) {
      __ set(mdo_offset_bias, data_val);
      __ add(mdo, data_val, mdo);
    }
    Address flags_addr(mdo, md->byte_offset_of_slot(data, DataLayout::flags_offset()) - mdo_offset_bias);
    __ ldub(flags_addr, data_val);
    __ or3(data_val, BitData::null_seen_byte_constant(), data_val);
    __ stb(data_val, flags_addr);
    __ ba(false, *obj_is_null);
    __ delayed()->nop();
    __ bind(not_null);
  } else {
    __ br_null(obj, false, Assembler::pn, *obj_is_null);
    __ delayed()->nop();
  }

  Label profile_cast_failure, profile_cast_success;
  Label *failure_target = op->should_profile() ? &profile_cast_failure : failure;
  Label *success_target = op->should_profile() ? &profile_cast_success : success;

  // patching may screw with our temporaries on sparc,
  // so let's do it before loading the class
  if (k->is_loaded()) {
    jobject2reg(k->constant_encoding(), k_RInfo);
  } else {
    jobject2reg_with_patching(k_RInfo, op->info_for_patch());
  }
  assert(obj != k_RInfo, "must be different");

  // get object class
  // not a safepoint as obj null check happens earlier
  __ load_klass(obj, klass_RInfo);
  if (op->fast_check()) {
    assert_different_registers(klass_RInfo, k_RInfo);
    __ cmp(k_RInfo, klass_RInfo);
    __ brx(Assembler::notEqual, false, Assembler::pt, *failure_target);
    __ delayed()->nop();
  } else {
    bool need_slow_path = true;
    if (k->is_loaded()) {
      if (k->super_check_offset() != sizeof(oopDesc) + Klass::secondary_super_cache_offset_in_bytes())
        need_slow_path = false;
      // perform the fast part of the checking logic
      __ check_klass_subtype_fast_path(klass_RInfo, k_RInfo, Rtmp1, noreg,
                                       (need_slow_path ? success_target : NULL),
                                       failure_target, NULL,
                                       RegisterOrConstant(k->super_check_offset()));
    } else {
      // perform the fast part of the checking logic
      __ check_klass_subtype_fast_path(klass_RInfo, k_RInfo, Rtmp1, O7, success_target,
                                       failure_target, NULL);
    }
    if (need_slow_path) {
      // call out-of-line instance of __ check_klass_subtype_slow_path(...):
      assert(klass_RInfo == G3 && k_RInfo == G1, "incorrect call setup");
      __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type);
      __ delayed()->nop();
      __ cmp(G3, 0);
      __ br(Assembler::equal, false, Assembler::pn, *failure_target);
      __ delayed()->nop();
      // Fall through to success case
    }
  }

  if (op->should_profile()) {
    Register mdo  = klass_RInfo, recv = k_RInfo, tmp1 = Rtmp1;
    assert_different_registers(obj, mdo, recv, tmp1);
    __ bind(profile_cast_success);
    jobject2reg(md->constant_encoding(), mdo);
    if (mdo_offset_bias > 0) {
      __ set(mdo_offset_bias, tmp1);
      __ add(mdo, tmp1, mdo);
    }
    __ load_klass(obj, recv);
    type_profile_helper(mdo, mdo_offset_bias, md, data, recv, tmp1, success);
    // Jump over the failure case
    __ ba(false, *success);
    __ delayed()->nop();
    // Cast failure case
    __ bind(profile_cast_failure);
    jobject2reg(md->constant_encoding(), mdo);
    if (mdo_offset_bias > 0) {
      __ set(mdo_offset_bias, tmp1);
      __ add(mdo, tmp1, mdo);
    }
    Address data_addr(mdo, md->byte_offset_of_slot(data, CounterData::count_offset()) - mdo_offset_bias);
    __ ld_ptr(data_addr, tmp1);
    __ sub(tmp1, DataLayout::counter_increment, tmp1);
    __ st_ptr(tmp1, data_addr);
    __ ba(false, *failure);
    __ delayed()->nop();
  }
  __ ba(false, *success);
  __ delayed()->nop();
}

void LIR_Assembler::emit_opTypeCheck(LIR_OpTypeCheck* op) {
  LIR_Code code = op->code();
  if (code == lir_store_check) {
    Register value = op->object()->as_register();
    Register array = op->array()->as_register();
    Register k_RInfo = op->tmp1()->as_register();
    Register klass_RInfo = op->tmp2()->as_register();
    Register Rtmp1 = op->tmp3()->as_register();

    __ verify_oop(value);
    CodeStub* stub = op->stub();
    // check if it needs to be profiled
    ciMethodData* md;
    ciProfileData* data;
    int mdo_offset_bias = 0;
    if (op->should_profile()) {
      ciMethod* method = op->profiled_method();
      assert(method != NULL, "Should have method");
      setup_md_access(method, op->profiled_bci(), md, data, mdo_offset_bias);
    }
    Label profile_cast_success, profile_cast_failure, done;
    Label *success_target = op->should_profile() ? &profile_cast_success : &done;
    Label *failure_target = op->should_profile() ? &profile_cast_failure : stub->entry();

    if (op->should_profile()) {
      Label not_null;
      __ br_notnull(value, false, Assembler::pn, not_null);
      __ delayed()->nop();
      Register mdo      = k_RInfo;
      Register data_val = Rtmp1;
      jobject2reg(md->constant_encoding(), mdo);
      if (mdo_offset_bias > 0) {
        __ set(mdo_offset_bias, data_val);
        __ add(mdo, data_val, mdo);
      }
      Address flags_addr(mdo, md->byte_offset_of_slot(data, DataLayout::flags_offset()) - mdo_offset_bias);
      __ ldub(flags_addr, data_val);
      __ or3(data_val, BitData::null_seen_byte_constant(), data_val);
      __ stb(data_val, flags_addr);
      __ ba(false, done);
      __ delayed()->nop();
      __ bind(not_null);
    } else {
      __ br_null(value, false, Assembler::pn, done);
      __ delayed()->nop();
    }
    add_debug_info_for_null_check_here(op->info_for_exception());
    __ load_klass(array, k_RInfo);
    __ load_klass(value, klass_RInfo);

    // get instance klass
    __ ld_ptr(Address(k_RInfo, objArrayKlass::element_klass_offset_in_bytes() + sizeof(oopDesc)), k_RInfo);
    // perform the fast part of the checking logic
    __ check_klass_subtype_fast_path(klass_RInfo, k_RInfo, Rtmp1, O7, success_target, failure_target, NULL);

    // call out-of-line instance of __ check_klass_subtype_slow_path(...):
    assert(klass_RInfo == G3 && k_RInfo == G1, "incorrect call setup");
    __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type);
    __ delayed()->nop();
    __ cmp(G3, 0);
    __ br(Assembler::equal, false, Assembler::pn, *failure_target);
    __ delayed()->nop();
    // fall through to the success case

    if (op->should_profile()) {
      Register mdo  = klass_RInfo, recv = k_RInfo, tmp1 = Rtmp1;
      assert_different_registers(value, mdo, recv, tmp1);
      __ bind(profile_cast_success);
      jobject2reg(md->constant_encoding(), mdo);
      if (mdo_offset_bias > 0) {
        __ set(mdo_offset_bias, tmp1);
        __ add(mdo, tmp1, mdo);
      }
      __ load_klass(value, recv);
      type_profile_helper(mdo, mdo_offset_bias, md, data, recv, tmp1, &done);
      __ ba(false, done);
      __ delayed()->nop();
      // Cast failure case
      __ bind(profile_cast_failure);
      jobject2reg(md->constant_encoding(), mdo);
      if (mdo_offset_bias > 0) {
        __ set(mdo_offset_bias, tmp1);
        __ add(mdo, tmp1, mdo);
      }
      Address data_addr(mdo, md->byte_offset_of_slot(data, CounterData::count_offset()) - mdo_offset_bias);
      __ ld_ptr(data_addr, tmp1);
      __ sub(tmp1, DataLayout::counter_increment, tmp1);
      __ st_ptr(tmp1, data_addr);
      __ ba(false, *stub->entry());
      __ delayed()->nop();
    }
    __ bind(done);
  } else if (code == lir_checkcast) {
    Register obj = op->object()->as_register();
    Register dst = op->result_opr()->as_register();
    Label success;
    emit_typecheck_helper(op, &success, op->stub()->entry(), &success);
    __ bind(success);
    __ mov(obj, dst);
  } else if (code == lir_instanceof) {
    Register obj = op->object()->as_register();
    Register dst = op->result_opr()->as_register();
    Label success, failure, done;
    emit_typecheck_helper(op, &success, &failure, &failure);
    __ bind(failure);
    __ set(0, dst);
    __ ba(false, done);
    __ delayed()->nop();
    __ bind(success);
    __ set(1, dst);
    __ bind(done);
  } else {
    ShouldNotReachHere();
  }

}


void LIR_Assembler::emit_compare_and_swap(LIR_OpCompareAndSwap* op) {
  if (op->code() == lir_cas_long) {
    assert(VM_Version::supports_cx8(), "wrong machine");
    Register addr = op->addr()->as_pointer_register();
    Register cmp_value_lo = op->cmp_value()->as_register_lo();
    Register cmp_value_hi = op->cmp_value()->as_register_hi();
    Register new_value_lo = op->new_value()->as_register_lo();
    Register new_value_hi = op->new_value()->as_register_hi();
    Register t1 = op->tmp1()->as_register();
    Register t2 = op->tmp2()->as_register();
#ifdef _LP64
    __ mov(cmp_value_lo, t1);
    __ mov(new_value_lo, t2);
#else
    // move high and low halves of long values into single registers
    __ sllx(cmp_value_hi, 32, t1);         // shift high half into temp reg
    __ srl(cmp_value_lo, 0, cmp_value_lo); // clear upper 32 bits of low half
    __ or3(t1, cmp_value_lo, t1);          // t1 holds 64-bit compare value
    __ sllx(new_value_hi, 32, t2);
    __ srl(new_value_lo, 0, new_value_lo);
    __ or3(t2, new_value_lo, t2);          // t2 holds 64-bit value to swap
#endif
    // perform the compare and swap operation
    __ casx(addr, t1, t2);
    // generate condition code - if the swap succeeded, t2 ("new value" reg) was
    // overwritten with the original value in "addr" and will be equal to t1.
    __ cmp(t1, t2);

  } else if (op->code() == lir_cas_int || op->code() == lir_cas_obj) {
    Register addr = op->addr()->as_pointer_register();
    Register cmp_value = op->cmp_value()->as_register();
    Register new_value = op->new_value()->as_register();
    Register t1 = op->tmp1()->as_register();
    Register t2 = op->tmp2()->as_register();
    __ mov(cmp_value, t1);
    __ mov(new_value, t2);
    if (op->code() == lir_cas_obj) {
      if (UseCompressedOops) {
        __ encode_heap_oop(t1);
        __ encode_heap_oop(t2);
        __ cas(addr, t1, t2);
      } else {
        __ casx(addr, t1, t2);
      }
    } else {
      __ cas(addr, t1, t2);
    }
    __ cmp(t1, t2);
  } else {
    Unimplemented();
  }
}

void LIR_Assembler::set_24bit_FPU() {
  Unimplemented();
}


void LIR_Assembler::reset_FPU() {
  Unimplemented();
}


void LIR_Assembler::breakpoint() {
  __ breakpoint_trap();
}


void LIR_Assembler::push(LIR_Opr opr) {
  Unimplemented();
}


void LIR_Assembler::pop(LIR_Opr opr) {
  Unimplemented();
}


void LIR_Assembler::monitor_address(int monitor_no, LIR_Opr dst_opr) {
  Address mon_addr = frame_map()->address_for_monitor_lock(monitor_no);
  Register dst = dst_opr->as_register();
  Register reg = mon_addr.base();
  int offset = mon_addr.disp();
  // compute pointer to BasicLock
  if (mon_addr.is_simm13()) {
    __ add(reg, offset, dst);
  } else {
    __ set(offset, dst);
    __ add(dst, reg, dst);
  }
}


void LIR_Assembler::emit_lock(LIR_OpLock* op) {
  Register obj = op->obj_opr()->as_register();
  Register hdr = op->hdr_opr()->as_register();
  Register lock = op->lock_opr()->as_register();

  // obj may not be an oop
  if (op->code() == lir_lock) {
    MonitorEnterStub* stub = (MonitorEnterStub*)op->stub();
    if (UseFastLocking) {
      assert(BasicLock::displaced_header_offset_in_bytes() == 0, "lock_reg must point to the displaced header");
      // add debug info for NullPointerException only if one is possible
      if (op->info() != NULL) {
        add_debug_info_for_null_check_here(op->info());
      }
      __ lock_object(hdr, obj, lock, op->scratch_opr()->as_register(), *op->stub()->entry());
    } else {
      // always do slow locking
      // note: the slow locking code could be inlined here, however if we use
      //       slow locking, speed doesn't matter anyway and this solution is
      //       simpler and requires less duplicated code - additionally, the
      //       slow locking code is the same in either case which simplifies
      //       debugging
      __ br(Assembler::always, false, Assembler::pt, *op->stub()->entry());
      __ delayed()->nop();
    }
  } else {
    assert (op->code() == lir_unlock, "Invalid code, expected lir_unlock");
    if (UseFastLocking) {
      assert(BasicLock::displaced_header_offset_in_bytes() == 0, "lock_reg must point to the displaced header");
      __ unlock_object(hdr, obj, lock, *op->stub()->entry());
    } else {
      // always do slow unlocking
      // note: the slow unlocking code could be inlined here, however if we use
      //       slow unlocking, speed doesn't matter anyway and this solution is
      //       simpler and requires less duplicated code - additionally, the
      //       slow unlocking code is the same in either case which simplifies
      //       debugging
      __ br(Assembler::always, false, Assembler::pt, *op->stub()->entry());
      __ delayed()->nop();
    }
  }
  __ bind(*op->stub()->continuation());
}


void LIR_Assembler::emit_profile_call(LIR_OpProfileCall* op) {
  ciMethod* method = op->profiled_method();
  int bci          = op->profiled_bci();

  // Update counter for all call types
  ciMethodData* md = method->method_data_or_null();
  assert(md != NULL, "Sanity");
  ciProfileData* data = md->bci_to_data(bci);
  assert(data->is_CounterData(), "need CounterData for calls");
  assert(op->mdo()->is_single_cpu(),  "mdo must be allocated");
  Register mdo  = op->mdo()->as_register();
#ifdef _LP64
  assert(op->tmp1()->is_double_cpu(), "tmp1 must be allocated");
  Register tmp1 = op->tmp1()->as_register_lo();
#else
  assert(op->tmp1()->is_single_cpu(), "tmp1 must be allocated");
  Register tmp1 = op->tmp1()->as_register();
#endif
  jobject2reg(md->constant_encoding(), mdo);
  int mdo_offset_bias = 0;
  if (!Assembler::is_simm13(md->byte_offset_of_slot(data, CounterData::count_offset()) +
                            data->size_in_bytes())) {
    // The offset is large so bias the mdo by the base of the slot so
    // that the ld can use simm13s to reference the slots of the data
    mdo_offset_bias = md->byte_offset_of_slot(data, CounterData::count_offset());
    __ set(mdo_offset_bias, O7);
    __ add(mdo, O7, mdo);
  }

  Address counter_addr(mdo, md->byte_offset_of_slot(data, CounterData::count_offset()) - mdo_offset_bias);
  Bytecodes::Code bc = method->java_code_at_bci(bci);
  // Perform additional virtual call profiling for invokevirtual and
  // invokeinterface bytecodes
  if ((bc == Bytecodes::_invokevirtual || bc == Bytecodes::_invokeinterface) &&
      C1ProfileVirtualCalls) {
    assert(op->recv()->is_single_cpu(), "recv must be allocated");
    Register recv = op->recv()->as_register();
    assert_different_registers(mdo, tmp1, recv);
    assert(data->is_VirtualCallData(), "need VirtualCallData for virtual calls");
    ciKlass* known_klass = op->known_holder();
    if (C1OptimizeVirtualCallProfiling && known_klass != NULL) {
      // We know the type that will be seen at this call site; we can
      // statically update the methodDataOop rather than needing to do
      // dynamic tests on the receiver type

      // NOTE: we should probably put a lock around this search to
      // avoid collisions by concurrent compilations
      ciVirtualCallData* vc_data = (ciVirtualCallData*) data;
      uint i;
      for (i = 0; i < VirtualCallData::row_limit(); i++) {
        ciKlass* receiver = vc_data->receiver(i);
        if (known_klass->equals(receiver)) {
          Address data_addr(mdo, md->byte_offset_of_slot(data,
                                                         VirtualCallData::receiver_count_offset(i)) -
                            mdo_offset_bias);
          __ ld_ptr(data_addr, tmp1);
          __ add(tmp1, DataLayout::counter_increment, tmp1);
          __ st_ptr(tmp1, data_addr);
          return;
        }
      }

      // Receiver type not found in profile data; select an empty slot

      // Note that this is less efficient than it should be because it
      // always does a write to the receiver part of the
      // VirtualCallData rather than just the first time
      for (i = 0; i < VirtualCallData::row_limit(); i++) {
        ciKlass* receiver = vc_data->receiver(i);
        if (receiver == NULL) {
          Address recv_addr(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_offset(i)) -
                            mdo_offset_bias);
          jobject2reg(known_klass->constant_encoding(), tmp1);
          __ st_ptr(tmp1, recv_addr);
          Address data_addr(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_count_offset(i)) -
                            mdo_offset_bias);
          __ ld_ptr(data_addr, tmp1);
          __ add(tmp1, DataLayout::counter_increment, tmp1);
          __ st_ptr(tmp1, data_addr);
          return;
        }
      }
    } else {
      __ load_klass(recv, recv);
      Label update_done;
      type_profile_helper(mdo, mdo_offset_bias, md, data, recv, tmp1, &update_done);
      // Receiver did not match any saved receiver and there is no empty row for it.
      // Increment total counter to indicate polymorphic case.
      __ ld_ptr(counter_addr, tmp1);
      __ add(tmp1, DataLayout::counter_increment, tmp1);
      __ st_ptr(tmp1, counter_addr);

      __ bind(update_done);
    }
  } else {
    // Static call
    __ ld_ptr(counter_addr, tmp1);
    __ add(tmp1, DataLayout::counter_increment, tmp1);
    __ st_ptr(tmp1, counter_addr);
  }
}

void LIR_Assembler::align_backward_branch_target() {
  __ align(OptoLoopAlignment);
}


void LIR_Assembler::emit_delay(LIR_OpDelay* op) {
  // make sure we are expecting a delay
  // this has the side effect of clearing the delay state
  // so we can use _masm instead of _masm->delayed() to do the
  // code generation.
  __ delayed();

  // make sure we only emit one instruction
  int offset = code_offset();
  op->delay_op()->emit_code(this);
#ifdef ASSERT
  if (code_offset() - offset != NativeInstruction::nop_instruction_size) {
    op->delay_op()->print();
  }
  assert(code_offset() - offset == NativeInstruction::nop_instruction_size,
         "only one instruction can go in a delay slot");
#endif

  // we may also be emitting the call info for the instruction
  // which we are the delay slot of.
  CodeEmitInfo* call_info = op->call_info();
  if (call_info) {
    add_call_info(code_offset(), call_info);
  }

  if (VerifyStackAtCalls) {
    _masm->sub(FP, SP, O7);
    _masm->cmp(O7, initial_frame_size_in_bytes());
    _masm->trap(Assembler::notEqual, Assembler::ptr_cc, G0, ST_RESERVED_FOR_USER_0+2 );
  }
}


void LIR_Assembler::negate(LIR_Opr left, LIR_Opr dest) {
  assert(left->is_register(), "can only handle registers");

  if (left->is_single_cpu()) {
    __ neg(left->as_register(), dest->as_register());
  } else if (left->is_single_fpu()) {
    __ fneg(FloatRegisterImpl::S, left->as_float_reg(), dest->as_float_reg());
  } else if (left->is_double_fpu()) {
    __ fneg(FloatRegisterImpl::D, left->as_double_reg(), dest->as_double_reg());
  } else {
    assert (left->is_double_cpu(), "Must be a long");
    Register Rlow = left->as_register_lo();
    Register Rhi = left->as_register_hi();
#ifdef _LP64
    __ sub(G0, Rlow, dest->as_register_lo());
#else
    __ subcc(G0, Rlow, dest->as_register_lo());
    __ subc (G0, Rhi,  dest->as_register_hi());
#endif
  }
}


void LIR_Assembler::fxch(int i) {
  Unimplemented();
}

void LIR_Assembler::fld(int i) {
  Unimplemented();
}

void LIR_Assembler::ffree(int i) {
  Unimplemented();
}

void LIR_Assembler::rt_call(LIR_Opr result, address dest,
                            const LIR_OprList* args, LIR_Opr tmp, CodeEmitInfo* info) {

  // if tmp is invalid, then the function being called doesn't destroy the thread
  if (tmp->is_valid()) {
    __ save_thread(tmp->as_register());
  }
  __ call(dest, relocInfo::runtime_call_type);
  __ delayed()->nop();
  if (info != NULL) {
    add_call_info_here(info);
  }
  if (tmp->is_valid()) {
    __ restore_thread(tmp->as_register());
  }

#ifdef ASSERT
  __ verify_thread();
#endif // ASSERT
}


void LIR_Assembler::volatile_move_op(LIR_Opr src, LIR_Opr dest, BasicType type, CodeEmitInfo* info) {
#ifdef _LP64
  ShouldNotReachHere();
#endif

  NEEDS_CLEANUP;
  if (type == T_LONG) {
    LIR_Address* mem_addr = dest->is_address() ? dest->as_address_ptr() : src->as_address_ptr();

    // (extended to allow indexed as well as constant displaced for JSR-166)
    Register idx = noreg; // contains either constant offset or index

    int disp = mem_addr->disp();
    if (mem_addr->index() == LIR_OprFact::illegalOpr) {
      if (!Assembler::is_simm13(disp)) {
        idx = O7;
        __ set(disp, idx);
      }
    } else {
      assert(disp == 0, "not both indexed and disp");
      idx = mem_addr->index()->as_register();
    }

    int null_check_offset = -1;

    Register base = mem_addr->base()->as_register();
    if (src->is_register() && dest->is_address()) {
      // G4 is high half, G5 is low half
      if (VM_Version::v9_instructions_work()) {
        // clear the top bits of G5, and scale up G4
        __ srl (src->as_register_lo(),  0, G5);
        __ sllx(src->as_register_hi(), 32, G4);
        // combine the two halves into the 64 bits of G4
        __ or3(G4, G5, G4);
        null_check_offset = __ offset();
        if (idx == noreg) {
          __ stx(G4, base, disp);
        } else {
          __ stx(G4, base, idx);
        }
      } else {
        __ mov (src->as_register_hi(), G4);
        __ mov (src->as_register_lo(), G5);
        null_check_offset = __ offset();
        if (idx == noreg) {
          __ std(G4, base, disp);
        } else {
          __ std(G4, base, idx);
        }
      }
    } else if (src->is_address() && dest->is_register()) {
      null_check_offset = __ offset();
      if (VM_Version::v9_instructions_work()) {
        if (idx == noreg) {
          __ ldx(base, disp, G5);
        } else {
          __ ldx(base, idx, G5);
        }
        __ srax(G5, 32, dest->as_register_hi()); // fetch the high half into hi
        __ mov (G5, dest->as_register_lo());     // copy low half into lo
      } else {
        if (idx == noreg) {
          __ ldd(base, disp, G4);
        } else {
          __ ldd(base, idx, G4);
        }
        // G4 is high half, G5 is low half
        __ mov (G4, dest->as_register_hi());
        __ mov (G5, dest->as_register_lo());
      }
    } else {
      Unimplemented();
    }
    if (info != NULL) {
      add_debug_info_for_null_check(null_check_offset, info);
    }

  } else {
    // use normal move for all other volatiles since they don't need
    // special handling to remain atomic.
    move_op(src, dest, type, lir_patch_none, info, false, false, false);
  }
}

void LIR_Assembler::membar() {
  // only StoreLoad membars are ever explicitly needed on sparcs in TSO mode
  __ membar( Assembler::Membar_mask_bits(Assembler::StoreLoad) );
}

void LIR_Assembler::membar_acquire() {
  // no-op on TSO
}

void LIR_Assembler::membar_release() {
  // no-op on TSO
}

// Pack two sequential registers containing 32 bit values
// into a single 64 bit register.
// src and src->successor() are packed into dst
// src and dst may be the same register.
// Note: src is destroyed
void LIR_Assembler::pack64(LIR_Opr src, LIR_Opr dst) {
  Register rs = src->as_register();
  Register rd = dst->as_register_lo();
  __ sllx(rs, 32, rs);
  __ srl(rs->successor(), 0, rs->successor());
  __ or3(rs, rs->successor(), rd);
}

// Unpack a 64 bit value in a register into
// two sequential registers.
// src is unpacked into dst and dst->successor()
void LIR_Assembler::unpack64(LIR_Opr src, LIR_Opr dst) {
  Register rs = src->as_register_lo();
  Register rd = dst->as_register_hi();
  assert_different_registers(rs, rd, rd->successor());
  __ srlx(rs, 32, rd);
  __ srl (rs,  0, rd->successor());
}


void LIR_Assembler::leal(LIR_Opr addr_opr, LIR_Opr dest) {
  LIR_Address* addr = addr_opr->as_address_ptr();
  assert(addr->index()->is_illegal() && addr->scale() == LIR_Address::times_1 && Assembler::is_simm13(addr->disp()), "can't handle complex addresses yet");

  __ add(addr->base()->as_pointer_register(), addr->disp(), dest->as_pointer_register());
}


void LIR_Assembler::get_thread(LIR_Opr result_reg) {
  assert(result_reg->is_register(), "check");
  __ mov(G2_thread, result_reg->as_register());
}


void LIR_Assembler::peephole(LIR_List* lir) {
  LIR_OpList* inst = lir->instructions_list();
  for (int i = 0; i < inst->length(); i++) {
    LIR_Op* op = inst->at(i);
    switch (op->code()) {
      case lir_cond_float_branch:
      case lir_branch: {
        LIR_OpBranch* branch = op->as_OpBranch();
        assert(branch->info() == NULL, "shouldn't be state on branches anymore");
        LIR_Op* delay_op = NULL;
        // we'd like to be able to pull following instructions into
        // this slot but we don't know enough to do it safely yet so
        // only optimize block to block control flow.
        if (LIRFillDelaySlots && branch->block()) {
          LIR_Op* prev = inst->at(i - 1);
          if (prev && LIR_Assembler::is_single_instruction(prev) && prev->info() == NULL) {
            // swap previous instruction into delay slot
            inst->at_put(i - 1, op);
            inst->at_put(i, new LIR_OpDelay(prev, op->info()));
#ifndef PRODUCT
            if (LIRTracePeephole) {
              tty->print_cr("delayed");
              inst->at(i - 1)->print();
              inst->at(i)->print();
              tty->cr();
            }
#endif
            continue;
          }
        }

        if (!delay_op) {
          delay_op = new LIR_OpDelay(new LIR_Op0(lir_nop), NULL);
        }
        inst->insert_before(i + 1, delay_op);
        break;
      }
      case lir_static_call:
      case lir_virtual_call:
      case lir_icvirtual_call:
      case lir_optvirtual_call:
      case lir_dynamic_call: {
        LIR_Op* prev = inst->at(i - 1);
        if (LIRFillDelaySlots && prev && prev->code() == lir_move && prev->info() == NULL &&
            (op->code() != lir_virtual_call ||
             !prev->result_opr()->is_single_cpu() ||
             prev->result_opr()->as_register() != O0) &&
            LIR_Assembler::is_single_instruction(prev)) {
          // Only moves without info can be put into the delay slot.
          // Also don't allow the setup of the receiver in the delay
          // slot for vtable calls.
          inst->at_put(i - 1, op);
          inst->at_put(i, new LIR_OpDelay(prev, op->info()));
#ifndef PRODUCT
          if (LIRTracePeephole) {
            tty->print_cr("delayed");
            inst->at(i - 1)->print();
            inst->at(i)->print();
            tty->cr();
          }
#endif
        } else {
          LIR_Op* delay_op = new LIR_OpDelay(new LIR_Op0(lir_nop), op->as_OpJavaCall()->info());
          inst->insert_before(i + 1, delay_op);
          i++;
        }

#if defined(TIERED) && !defined(_LP64)
        // fixup the return value from G1 to O0/O1 for long returns.
        // It's done here instead of in LIRGenerator because there's
        // such a mismatch between the single reg and double reg
        // calling convention.
        LIR_OpJavaCall* callop = op->as_OpJavaCall();
        if (callop->result_opr() == FrameMap::out_long_opr) {
          LIR_OpJavaCall* call;
          LIR_OprList* arguments = new LIR_OprList(callop->arguments()->length());
          for (int a = 0; a < arguments->length(); a++) {
            arguments[a] = callop->arguments()[a];
          }
          if (op->code() == lir_virtual_call) {
            call = new LIR_OpJavaCall(op->code(), callop->method(), callop->receiver(), FrameMap::g1_long_single_opr,
                                      callop->vtable_offset(), arguments, callop->info());
          } else {
            call = new LIR_OpJavaCall(op->code(), callop->method(), callop->receiver(), FrameMap::g1_long_single_opr,
                                      callop->addr(), arguments, callop->info());
          }
          inst->at_put(i - 1, call);
          inst->insert_before(i + 1, new LIR_Op1(lir_unpack64, FrameMap::g1_long_single_opr, callop->result_opr(),
                                                 T_LONG, lir_patch_none, NULL));
        }
#endif
        break;
      }
    }
  }
}




#undef __
