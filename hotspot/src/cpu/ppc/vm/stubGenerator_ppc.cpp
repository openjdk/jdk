/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2015 SAP AG. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "nativeInst_ppc.hpp"
#include "oops/instanceOop.hpp"
#include "oops/method.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/top.hpp"
#include "runtime/thread.inline.hpp"

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) // nothing
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

class StubGenerator: public StubCodeGenerator {
 private:

  // Call stubs are used to call Java from C
  //
  // Arguments:
  //
  //   R3  - call wrapper address     : address
  //   R4  - result                   : intptr_t*
  //   R5  - result type              : BasicType
  //   R6  - method                   : Method
  //   R7  - frame mgr entry point    : address
  //   R8  - parameter block          : intptr_t*
  //   R9  - parameter count in words : int
  //   R10 - thread                   : Thread*
  //
  address generate_call_stub(address& return_address) {
    // Setup a new c frame, copy java arguments, call frame manager or
    // native_entry, and process result.

    StubCodeMark mark(this, "StubRoutines", "call_stub");

    address start = __ function_entry();

    // some sanity checks
    assert((sizeof(frame::abi_minframe) % 16) == 0,           "unaligned");
    assert((sizeof(frame::abi_reg_args) % 16) == 0,           "unaligned");
    assert((sizeof(frame::spill_nonvolatiles) % 16) == 0,     "unaligned");
    assert((sizeof(frame::parent_ijava_frame_abi) % 16) == 0, "unaligned");
    assert((sizeof(frame::entry_frame_locals) % 16) == 0,     "unaligned");

    Register r_arg_call_wrapper_addr        = R3;
    Register r_arg_result_addr              = R4;
    Register r_arg_result_type              = R5;
    Register r_arg_method                   = R6;
    Register r_arg_entry                    = R7;
    Register r_arg_thread                   = R10;

    Register r_temp                         = R24;
    Register r_top_of_arguments_addr        = R25;
    Register r_entryframe_fp                = R26;

    {
      // Stack on entry to call_stub:
      //
      //      F1      [C_FRAME]
      //              ...

      Register r_arg_argument_addr          = R8;
      Register r_arg_argument_count         = R9;
      Register r_frame_alignment_in_bytes   = R27;
      Register r_argument_addr              = R28;
      Register r_argumentcopy_addr          = R29;
      Register r_argument_size_in_bytes     = R30;
      Register r_frame_size                 = R23;

      Label arguments_copied;

      // Save LR/CR to caller's C_FRAME.
      __ save_LR_CR(R0);

      // Zero extend arg_argument_count.
      __ clrldi(r_arg_argument_count, r_arg_argument_count, 32);

      // Save non-volatiles GPRs to ENTRY_FRAME (not yet pushed, but it's safe).
      __ save_nonvolatile_gprs(R1_SP, _spill_nonvolatiles_neg(r14));

      // Keep copy of our frame pointer (caller's SP).
      __ mr(r_entryframe_fp, R1_SP);

      BLOCK_COMMENT("Push ENTRY_FRAME including arguments");
      // Push ENTRY_FRAME including arguments:
      //
      //      F0      [TOP_IJAVA_FRAME_ABI]
      //              alignment (optional)
      //              [outgoing Java arguments]
      //              [ENTRY_FRAME_LOCALS]
      //      F1      [C_FRAME]
      //              ...

      // calculate frame size

      // unaligned size of arguments
      __ sldi(r_argument_size_in_bytes,
                  r_arg_argument_count, Interpreter::logStackElementSize);
      // arguments alignment (max 1 slot)
      // FIXME: use round_to() here
      __ andi_(r_frame_alignment_in_bytes, r_arg_argument_count, 1);
      __ sldi(r_frame_alignment_in_bytes,
              r_frame_alignment_in_bytes, Interpreter::logStackElementSize);

      // size = unaligned size of arguments + top abi's size
      __ addi(r_frame_size, r_argument_size_in_bytes,
              frame::top_ijava_frame_abi_size);
      // size += arguments alignment
      __ add(r_frame_size,
             r_frame_size, r_frame_alignment_in_bytes);
      // size += size of call_stub locals
      __ addi(r_frame_size,
              r_frame_size, frame::entry_frame_locals_size);

      // push ENTRY_FRAME
      __ push_frame(r_frame_size, r_temp);

      // initialize call_stub locals (step 1)
      __ std(r_arg_call_wrapper_addr,
             _entry_frame_locals_neg(call_wrapper_address), r_entryframe_fp);
      __ std(r_arg_result_addr,
             _entry_frame_locals_neg(result_address), r_entryframe_fp);
      __ std(r_arg_result_type,
             _entry_frame_locals_neg(result_type), r_entryframe_fp);
      // we will save arguments_tos_address later


      BLOCK_COMMENT("Copy Java arguments");
      // copy Java arguments

      // Calculate top_of_arguments_addr which will be R17_tos (not prepushed) later.
      // FIXME: why not simply use SP+frame::top_ijava_frame_size?
      __ addi(r_top_of_arguments_addr,
              R1_SP, frame::top_ijava_frame_abi_size);
      __ add(r_top_of_arguments_addr,
             r_top_of_arguments_addr, r_frame_alignment_in_bytes);

      // any arguments to copy?
      __ cmpdi(CCR0, r_arg_argument_count, 0);
      __ beq(CCR0, arguments_copied);

      // prepare loop and copy arguments in reverse order
      {
        // init CTR with arg_argument_count
        __ mtctr(r_arg_argument_count);

        // let r_argumentcopy_addr point to last outgoing Java arguments P
        __ mr(r_argumentcopy_addr, r_top_of_arguments_addr);

        // let r_argument_addr point to last incoming java argument
        __ add(r_argument_addr,
                   r_arg_argument_addr, r_argument_size_in_bytes);
        __ addi(r_argument_addr, r_argument_addr, -BytesPerWord);

        // now loop while CTR > 0 and copy arguments
        {
          Label next_argument;
          __ bind(next_argument);

          __ ld(r_temp, 0, r_argument_addr);
          // argument_addr--;
          __ addi(r_argument_addr, r_argument_addr, -BytesPerWord);
          __ std(r_temp, 0, r_argumentcopy_addr);
          // argumentcopy_addr++;
          __ addi(r_argumentcopy_addr, r_argumentcopy_addr, BytesPerWord);

          __ bdnz(next_argument);
        }
      }

      // Arguments copied, continue.
      __ bind(arguments_copied);
    }

    {
      BLOCK_COMMENT("Call frame manager or native entry.");
      // Call frame manager or native entry.
      Register r_new_arg_entry = R14;
      assert_different_registers(r_new_arg_entry, r_top_of_arguments_addr,
                                 r_arg_method, r_arg_thread);

      __ mr(r_new_arg_entry, r_arg_entry);

      // Register state on entry to frame manager / native entry:
      //
      //   tos         -  intptr_t*    sender tos (prepushed) Lesp = (SP) + copied_arguments_offset - 8
      //   R19_method  -  Method
      //   R16_thread  -  JavaThread*

      // Tos must point to last argument - element_size.
      const Register tos = R15_esp;

      __ addi(tos, r_top_of_arguments_addr, -Interpreter::stackElementSize);

      // initialize call_stub locals (step 2)
      // now save tos as arguments_tos_address
      __ std(tos, _entry_frame_locals_neg(arguments_tos_address), r_entryframe_fp);

      // load argument registers for call
      __ mr(R19_method, r_arg_method);
      __ mr(R16_thread, r_arg_thread);
      assert(tos != r_arg_method, "trashed r_arg_method");
      assert(tos != r_arg_thread && R19_method != r_arg_thread, "trashed r_arg_thread");

      // Set R15_prev_state to 0 for simplifying checks in callee.
      __ load_const_optimized(R25_templateTableBase, (address)Interpreter::dispatch_table((TosState)0), R11_scratch1);
      // Stack on entry to frame manager / native entry:
      //
      //      F0      [TOP_IJAVA_FRAME_ABI]
      //              alignment (optional)
      //              [outgoing Java arguments]
      //              [ENTRY_FRAME_LOCALS]
      //      F1      [C_FRAME]
      //              ...
      //

      // global toc register
      __ load_const(R29, MacroAssembler::global_toc(), R11_scratch1);

      // Remember the senderSP so we interpreter can pop c2i arguments off of the stack
      // when called via a c2i.

      // Pass initial_caller_sp to framemanager.
      __ mr(R21_tmp1, R1_SP);

      // Do a light-weight C-call here, r_new_arg_entry holds the address
      // of the interpreter entry point (frame manager or native entry)
      // and save runtime-value of LR in return_address.
      assert(r_new_arg_entry != tos && r_new_arg_entry != R19_method && r_new_arg_entry != R16_thread,
             "trashed r_new_arg_entry");
      return_address = __ call_stub(r_new_arg_entry);
    }

    {
      BLOCK_COMMENT("Returned from frame manager or native entry.");
      // Returned from frame manager or native entry.
      // Now pop frame, process result, and return to caller.

      // Stack on exit from frame manager / native entry:
      //
      //      F0      [ABI]
      //              ...
      //              [ENTRY_FRAME_LOCALS]
      //      F1      [C_FRAME]
      //              ...
      //
      // Just pop the topmost frame ...
      //

      Label ret_is_object;
      Label ret_is_long;
      Label ret_is_float;
      Label ret_is_double;

      Register r_entryframe_fp = R30;
      Register r_lr            = R7_ARG5;
      Register r_cr            = R8_ARG6;

      // Reload some volatile registers which we've spilled before the call
      // to frame manager / native entry.
      // Access all locals via frame pointer, because we know nothing about
      // the topmost frame's size.
      __ ld(r_entryframe_fp, _abi(callers_sp), R1_SP);
      assert_different_registers(r_entryframe_fp, R3_RET, r_arg_result_addr, r_arg_result_type, r_cr, r_lr);
      __ ld(r_arg_result_addr,
            _entry_frame_locals_neg(result_address), r_entryframe_fp);
      __ ld(r_arg_result_type,
            _entry_frame_locals_neg(result_type), r_entryframe_fp);
      __ ld(r_cr, _abi(cr), r_entryframe_fp);
      __ ld(r_lr, _abi(lr), r_entryframe_fp);

      // pop frame and restore non-volatiles, LR and CR
      __ mr(R1_SP, r_entryframe_fp);
      __ mtcr(r_cr);
      __ mtlr(r_lr);

      // Store result depending on type. Everything that is not
      // T_OBJECT, T_LONG, T_FLOAT, or T_DOUBLE is treated as T_INT.
      __ cmpwi(CCR0, r_arg_result_type, T_OBJECT);
      __ cmpwi(CCR1, r_arg_result_type, T_LONG);
      __ cmpwi(CCR5, r_arg_result_type, T_FLOAT);
      __ cmpwi(CCR6, r_arg_result_type, T_DOUBLE);

      // restore non-volatile registers
      __ restore_nonvolatile_gprs(R1_SP, _spill_nonvolatiles_neg(r14));


      // Stack on exit from call_stub:
      //
      //      0       [C_FRAME]
      //              ...
      //
      //  no call_stub frames left.

      // All non-volatiles have been restored at this point!!
      assert(R3_RET == R3, "R3_RET should be R3");

      __ beq(CCR0, ret_is_object);
      __ beq(CCR1, ret_is_long);
      __ beq(CCR5, ret_is_float);
      __ beq(CCR6, ret_is_double);

      // default:
      __ stw(R3_RET, 0, r_arg_result_addr);
      __ blr(); // return to caller

      // case T_OBJECT:
      __ bind(ret_is_object);
      __ std(R3_RET, 0, r_arg_result_addr);
      __ blr(); // return to caller

      // case T_LONG:
      __ bind(ret_is_long);
      __ std(R3_RET, 0, r_arg_result_addr);
      __ blr(); // return to caller

      // case T_FLOAT:
      __ bind(ret_is_float);
      __ stfs(F1_RET, 0, r_arg_result_addr);
      __ blr(); // return to caller

      // case T_DOUBLE:
      __ bind(ret_is_double);
      __ stfd(F1_RET, 0, r_arg_result_addr);
      __ blr(); // return to caller
    }

    return start;
  }

  // Return point for a Java call if there's an exception thrown in
  // Java code.  The exception is caught and transformed into a
  // pending exception stored in JavaThread that can be tested from
  // within the VM.
  //
  address generate_catch_exception() {
    StubCodeMark mark(this, "StubRoutines", "catch_exception");

    address start = __ pc();

    // Registers alive
    //
    //  R16_thread
    //  R3_ARG1 - address of pending exception
    //  R4_ARG2 - return address in call stub

    const Register exception_file = R21_tmp1;
    const Register exception_line = R22_tmp2;

    __ load_const(exception_file, (void*)__FILE__);
    __ load_const(exception_line, (void*)__LINE__);

    __ std(R3_ARG1, in_bytes(JavaThread::pending_exception_offset()), R16_thread);
    // store into `char *'
    __ std(exception_file, in_bytes(JavaThread::exception_file_offset()), R16_thread);
    // store into `int'
    __ stw(exception_line, in_bytes(JavaThread::exception_line_offset()), R16_thread);

    // complete return to VM
    assert(StubRoutines::_call_stub_return_address != NULL, "must have been generated before");

    __ mtlr(R4_ARG2);
    // continue in call stub
    __ blr();

    return start;
  }

  // Continuation point for runtime calls returning with a pending
  // exception.  The pending exception check happened in the runtime
  // or native call stub.  The pending exception in Thread is
  // converted into a Java-level exception.
  //
  // Read:
  //
  //   LR:     The pc the runtime library callee wants to return to.
  //           Since the exception occurred in the callee, the return pc
  //           from the point of view of Java is the exception pc.
  //   thread: Needed for method handles.
  //
  // Invalidate:
  //
  //   volatile registers (except below).
  //
  // Update:
  //
  //   R4_ARG2: exception
  //
  // (LR is unchanged and is live out).
  //
  address generate_forward_exception() {
    StubCodeMark mark(this, "StubRoutines", "forward_exception");
    address start = __ pc();

#if !defined(PRODUCT)
    if (VerifyOops) {
      // Get pending exception oop.
      __ ld(R3_ARG1,
                in_bytes(Thread::pending_exception_offset()),
                R16_thread);
      // Make sure that this code is only executed if there is a pending exception.
      {
        Label L;
        __ cmpdi(CCR0, R3_ARG1, 0);
        __ bne(CCR0, L);
        __ stop("StubRoutines::forward exception: no pending exception (1)");
        __ bind(L);
      }
      __ verify_oop(R3_ARG1, "StubRoutines::forward exception: not an oop");
    }
#endif

    // Save LR/CR and copy exception pc (LR) into R4_ARG2.
    __ save_LR_CR(R4_ARG2);
    __ push_frame_reg_args(0, R0);
    // Find exception handler.
    __ call_VM_leaf(CAST_FROM_FN_PTR(address,
                     SharedRuntime::exception_handler_for_return_address),
                    R16_thread,
                    R4_ARG2);
    // Copy handler's address.
    __ mtctr(R3_RET);
    __ pop_frame();
    __ restore_LR_CR(R0);

    // Set up the arguments for the exception handler:
    //  - R3_ARG1: exception oop
    //  - R4_ARG2: exception pc.

    // Load pending exception oop.
    __ ld(R3_ARG1,
              in_bytes(Thread::pending_exception_offset()),
              R16_thread);

    // The exception pc is the return address in the caller.
    // Must load it into R4_ARG2.
    __ mflr(R4_ARG2);

#ifdef ASSERT
    // Make sure exception is set.
    {
      Label L;
      __ cmpdi(CCR0, R3_ARG1, 0);
      __ bne(CCR0, L);
      __ stop("StubRoutines::forward exception: no pending exception (2)");
      __ bind(L);
    }
#endif

    // Clear the pending exception.
    __ li(R0, 0);
    __ std(R0,
               in_bytes(Thread::pending_exception_offset()),
               R16_thread);
    // Jump to exception handler.
    __ bctr();

    return start;
  }

#undef __
#define __ masm->
  // Continuation point for throwing of implicit exceptions that are
  // not handled in the current activation. Fabricates an exception
  // oop and initiates normal exception dispatching in this
  // frame. Only callee-saved registers are preserved (through the
  // normal register window / RegisterMap handling).  If the compiler
  // needs all registers to be preserved between the fault point and
  // the exception handler then it must assume responsibility for that
  // in AbstractCompiler::continuation_for_implicit_null_exception or
  // continuation_for_implicit_division_by_zero_exception. All other
  // implicit exceptions (e.g., NullPointerException or
  // AbstractMethodError on entry) are either at call sites or
  // otherwise assume that stack unwinding will be initiated, so
  // caller saved registers were assumed volatile in the compiler.
  //
  // Note that we generate only this stub into a RuntimeStub, because
  // it needs to be properly traversed and ignored during GC, so we
  // change the meaning of the "__" macro within this method.
  //
  // Note: the routine set_pc_not_at_call_for_caller in
  // SharedRuntime.cpp requires that this code be generated into a
  // RuntimeStub.
  address generate_throw_exception(const char* name, address runtime_entry, bool restore_saved_exception_pc,
                                   Register arg1 = noreg, Register arg2 = noreg) {
    CodeBuffer code(name, 1024 DEBUG_ONLY(+ 512), 0);
    MacroAssembler* masm = new MacroAssembler(&code);

    OopMapSet* oop_maps  = new OopMapSet();
    int frame_size_in_bytes = frame::abi_reg_args_size;
    OopMap* map = new OopMap(frame_size_in_bytes / sizeof(jint), 0);

    address start = __ pc();

    __ save_LR_CR(R11_scratch1);

    // Push a frame.
    __ push_frame_reg_args(0, R11_scratch1);

    address frame_complete_pc = __ pc();

    if (restore_saved_exception_pc) {
      __ unimplemented("StubGenerator::throw_exception with restore_saved_exception_pc", 74);
    }

    // Note that we always have a runtime stub frame on the top of
    // stack by this point. Remember the offset of the instruction
    // whose address will be moved to R11_scratch1.
    address gc_map_pc = __ get_PC_trash_LR(R11_scratch1);

    __ set_last_Java_frame(/*sp*/R1_SP, /*pc*/R11_scratch1);

    __ mr(R3_ARG1, R16_thread);
    if (arg1 != noreg) {
      __ mr(R4_ARG2, arg1);
    }
    if (arg2 != noreg) {
      __ mr(R5_ARG3, arg2);
    }
#if defined(ABI_ELFv2)
    __ call_c(runtime_entry, relocInfo::none);
#else
    __ call_c(CAST_FROM_FN_PTR(FunctionDescriptor*, runtime_entry), relocInfo::none);
#endif

    // Set an oopmap for the call site.
    oop_maps->add_gc_map((int)(gc_map_pc - start), map);

    __ reset_last_Java_frame();

#ifdef ASSERT
    // Make sure that this code is only executed if there is a pending
    // exception.
    {
      Label L;
      __ ld(R0,
                in_bytes(Thread::pending_exception_offset()),
                R16_thread);
      __ cmpdi(CCR0, R0, 0);
      __ bne(CCR0, L);
      __ stop("StubRoutines::throw_exception: no pending exception");
      __ bind(L);
    }
#endif

    // Pop frame.
    __ pop_frame();

    __ restore_LR_CR(R11_scratch1);

    __ load_const(R11_scratch1, StubRoutines::forward_exception_entry());
    __ mtctr(R11_scratch1);
    __ bctr();

    // Create runtime stub with OopMap.
    RuntimeStub* stub =
      RuntimeStub::new_runtime_stub(name, &code,
                                    /*frame_complete=*/ (int)(frame_complete_pc - start),
                                    frame_size_in_bytes/wordSize,
                                    oop_maps,
                                    false);
    return stub->entry_point();
  }
#undef __
#define __ _masm->

  //  Generate G1 pre-write barrier for array.
  //
  //  Input:
  //     from     - register containing src address (only needed for spilling)
  //     to       - register containing starting address
  //     count    - register containing element count
  //     tmp      - scratch register
  //
  //  Kills:
  //     nothing
  //
  void gen_write_ref_array_pre_barrier(Register from, Register to, Register count, bool dest_uninitialized, Register Rtmp1) {
    BarrierSet* const bs = Universe::heap()->barrier_set();
    switch (bs->kind()) {
      case BarrierSet::G1SATBCTLogging:
        // With G1, don't generate the call if we statically know that the target in uninitialized
        if (!dest_uninitialized) {
          const int spill_slots = 4 * wordSize;
          const int frame_size  = frame::abi_reg_args_size + spill_slots;
          Label filtered;

          // Is marking active?
          if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
            __ lwz(Rtmp1, in_bytes(JavaThread::satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_active()), R16_thread);
          } else {
            guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
            __ lbz(Rtmp1, in_bytes(JavaThread::satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_active()), R16_thread);
          }
          __ cmpdi(CCR0, Rtmp1, 0);
          __ beq(CCR0, filtered);

          __ save_LR_CR(R0);
          __ push_frame_reg_args(spill_slots, R0);
          __ std(from,  frame_size - 1 * wordSize, R1_SP);
          __ std(to,    frame_size - 2 * wordSize, R1_SP);
          __ std(count, frame_size - 3 * wordSize, R1_SP);

          __ call_VM_leaf(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_pre), to, count);

          __ ld(from,  frame_size - 1 * wordSize, R1_SP);
          __ ld(to,    frame_size - 2 * wordSize, R1_SP);
          __ ld(count, frame_size - 3 * wordSize, R1_SP);
          __ pop_frame();
          __ restore_LR_CR(R0);

          __ bind(filtered);
        }
        break;
      case BarrierSet::CardTableForRS:
      case BarrierSet::CardTableExtension:
      case BarrierSet::ModRef:
        break;
      default:
        ShouldNotReachHere();
    }
  }

  //  Generate CMS/G1 post-write barrier for array.
  //
  //  Input:
  //     addr     - register containing starting address
  //     count    - register containing element count
  //     tmp      - scratch register
  //
  //  The input registers and R0 are overwritten.
  //
  void gen_write_ref_array_post_barrier(Register addr, Register count, Register tmp, bool branchToEnd) {
    BarrierSet* const bs = Universe::heap()->barrier_set();

    switch (bs->kind()) {
      case BarrierSet::G1SATBCTLogging:
        {
          if (branchToEnd) {
            __ save_LR_CR(R0);
            // We need this frame only to spill LR.
            __ push_frame_reg_args(0, R0);
            __ call_VM_leaf(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_post), addr, count);
            __ pop_frame();
            __ restore_LR_CR(R0);
          } else {
            // Tail call: fake call from stub caller by branching without linking.
            address entry_point = (address)CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_post);
            __ mr_if_needed(R3_ARG1, addr);
            __ mr_if_needed(R4_ARG2, count);
            __ load_const(R11, entry_point, R0);
            __ call_c_and_return_to_caller(R11);
          }
        }
        break;
      case BarrierSet::CardTableForRS:
      case BarrierSet::CardTableExtension:
        {
          Label Lskip_loop, Lstore_loop;
          if (UseConcMarkSweepGC) {
            // TODO PPC port: contribute optimization / requires shared changes
            __ release();
          }

          CardTableModRefBS* const ct = barrier_set_cast<CardTableModRefBS>(bs);
          assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");
          assert_different_registers(addr, count, tmp);

          __ sldi(count, count, LogBytesPerHeapOop);
          __ addi(count, count, -BytesPerHeapOop);
          __ add(count, addr, count);
          // Use two shifts to clear out those low order two bits! (Cannot opt. into 1.)
          __ srdi(addr, addr, CardTableModRefBS::card_shift);
          __ srdi(count, count, CardTableModRefBS::card_shift);
          __ subf(count, addr, count);
          assert_different_registers(R0, addr, count, tmp);
          __ load_const(tmp, (address)ct->byte_map_base);
          __ addic_(count, count, 1);
          __ beq(CCR0, Lskip_loop);
          __ li(R0, 0);
          __ mtctr(count);
          // Byte store loop
          __ bind(Lstore_loop);
          __ stbx(R0, tmp, addr);
          __ addi(addr, addr, 1);
          __ bdnz(Lstore_loop);
          __ bind(Lskip_loop);

          if (!branchToEnd) __ blr();
        }
      break;
      case BarrierSet::ModRef:
        if (!branchToEnd) __ blr();
        break;
      default:
        ShouldNotReachHere();
    }
  }

  // Support for void zero_words_aligned8(HeapWord* to, size_t count)
  //
  // Arguments:
  //   to:
  //   count:
  //
  // Destroys:
  //
  address generate_zero_words_aligned8() {
    StubCodeMark mark(this, "StubRoutines", "zero_words_aligned8");

    // Implemented as in ClearArray.
    address start = __ function_entry();

    Register base_ptr_reg   = R3_ARG1; // tohw (needs to be 8b aligned)
    Register cnt_dwords_reg = R4_ARG2; // count (in dwords)
    Register tmp1_reg       = R5_ARG3;
    Register tmp2_reg       = R6_ARG4;
    Register zero_reg       = R7_ARG5;

    // Procedure for large arrays (uses data cache block zero instruction).
    Label dwloop, fast, fastloop, restloop, lastdword, done;
    int cl_size=VM_Version::get_cache_line_size(), cl_dwords=cl_size>>3, cl_dwordaddr_bits=exact_log2(cl_dwords);
    int min_dcbz=2; // Needs to be positive, apply dcbz only to at least min_dcbz cache lines.

    // Clear up to 128byte boundary if long enough, dword_cnt=(16-(base>>3))%16.
    __ dcbtst(base_ptr_reg);                    // Indicate write access to first cache line ...
    __ andi(tmp2_reg, cnt_dwords_reg, 1);       // to check if number of dwords is even.
    __ srdi_(tmp1_reg, cnt_dwords_reg, 1);      // number of double dwords
    __ load_const_optimized(zero_reg, 0L);      // Use as zero register.

    __ cmpdi(CCR1, tmp2_reg, 0);                // cnt_dwords even?
    __ beq(CCR0, lastdword);                    // size <= 1
    __ mtctr(tmp1_reg);                         // Speculatively preload counter for rest loop (>0).
    __ cmpdi(CCR0, cnt_dwords_reg, (min_dcbz+1)*cl_dwords-1); // Big enough to ensure >=min_dcbz cache lines are included?
    __ neg(tmp1_reg, base_ptr_reg);             // bit 0..58: bogus, bit 57..60: (16-(base>>3))%16, bit 61..63: 000

    __ blt(CCR0, restloop);                     // Too small. (<31=(2*cl_dwords)-1 is sufficient, but bigger performs better.)
    __ rldicl_(tmp1_reg, tmp1_reg, 64-3, 64-cl_dwordaddr_bits); // Extract number of dwords to 128byte boundary=(16-(base>>3))%16.

    __ beq(CCR0, fast);                         // already 128byte aligned
    __ mtctr(tmp1_reg);                         // Set ctr to hit 128byte boundary (0<ctr<cnt).
    __ subf(cnt_dwords_reg, tmp1_reg, cnt_dwords_reg); // rest (>0 since size>=256-8)

    // Clear in first cache line dword-by-dword if not already 128byte aligned.
    __ bind(dwloop);
      __ std(zero_reg, 0, base_ptr_reg);        // Clear 8byte aligned block.
      __ addi(base_ptr_reg, base_ptr_reg, 8);
    __ bdnz(dwloop);

    // clear 128byte blocks
    __ bind(fast);
    __ srdi(tmp1_reg, cnt_dwords_reg, cl_dwordaddr_bits); // loop count for 128byte loop (>0 since size>=256-8)
    __ andi(tmp2_reg, cnt_dwords_reg, 1);       // to check if rest even

    __ mtctr(tmp1_reg);                         // load counter
    __ cmpdi(CCR1, tmp2_reg, 0);                // rest even?
    __ rldicl_(tmp1_reg, cnt_dwords_reg, 63, 65-cl_dwordaddr_bits); // rest in double dwords

    __ bind(fastloop);
      __ dcbz(base_ptr_reg);                    // Clear 128byte aligned block.
      __ addi(base_ptr_reg, base_ptr_reg, cl_size);
    __ bdnz(fastloop);

    //__ dcbtst(base_ptr_reg);                  // Indicate write access to last cache line.
    __ beq(CCR0, lastdword);                    // rest<=1
    __ mtctr(tmp1_reg);                         // load counter

    // Clear rest.
    __ bind(restloop);
      __ std(zero_reg, 0, base_ptr_reg);        // Clear 8byte aligned block.
      __ std(zero_reg, 8, base_ptr_reg);        // Clear 8byte aligned block.
      __ addi(base_ptr_reg, base_ptr_reg, 16);
    __ bdnz(restloop);

    __ bind(lastdword);
    __ beq(CCR1, done);
    __ std(zero_reg, 0, base_ptr_reg);
    __ bind(done);
    __ blr();                                   // return

    return start;
  }

  // The following routine generates a subroutine to throw an asynchronous
  // UnknownError when an unsafe access gets a fault that could not be
  // reasonably prevented by the programmer.  (Example: SIGBUS/OBJERR.)
  //
  address generate_handler_for_unsafe_access() {
    StubCodeMark mark(this, "StubRoutines", "handler_for_unsafe_access");
    address start = __ function_entry();
    __ unimplemented("StubRoutines::handler_for_unsafe_access", 93);
    return start;
  }

#if !defined(PRODUCT)
  // Wrapper which calls oopDesc::is_oop_or_null()
  // Only called by MacroAssembler::verify_oop
  static void verify_oop_helper(const char* message, oop o) {
    if (!o->is_oop_or_null()) {
      fatal("%s", message);
    }
    ++ StubRoutines::_verify_oop_count;
  }
#endif

  // Return address of code to be called from code generated by
  // MacroAssembler::verify_oop.
  //
  // Don't generate, rather use C++ code.
  address generate_verify_oop() {
    // this is actually a `FunctionDescriptor*'.
    address start = 0;

#if !defined(PRODUCT)
    start = CAST_FROM_FN_PTR(address, verify_oop_helper);
#endif

    return start;
  }

  // Fairer handling of safepoints for native methods.
  //
  // Generate code which reads from the polling page. This special handling is needed as the
  // linux-ppc64 kernel before 2.6.6 doesn't set si_addr on some segfaults in 64bit mode
  // (cf. http://www.kernel.org/pub/linux/kernel/v2.6/ChangeLog-2.6.6), especially when we try
  // to read from the safepoint polling page.
  address generate_load_from_poll() {
    StubCodeMark mark(this, "StubRoutines", "generate_load_from_poll");
    address start = __ function_entry();
    __ unimplemented("StubRoutines::verify_oop", 95);  // TODO PPC port
    return start;
  }

  // -XX:+OptimizeFill : convert fill/copy loops into intrinsic
  //
  // The code is implemented(ported from sparc) as we believe it benefits JVM98, however
  // tracing(-XX:+TraceOptimizeFill) shows the intrinsic replacement doesn't happen at all!
  //
  // Source code in function is_range_check_if() shows that OptimizeFill relaxed the condition
  // for turning on loop predication optimization, and hence the behavior of "array range check"
  // and "loop invariant check" could be influenced, which potentially boosted JVM98.
  //
  // Generate stub for disjoint short fill. If "aligned" is true, the
  // "to" address is assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //   to:    R3_ARG1
  //   value: R4_ARG2
  //   count: R5_ARG3 treated as signed
  //
  address generate_fill(BasicType t, bool aligned, const char* name) {
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();

    const Register to    = R3_ARG1;   // source array address
    const Register value = R4_ARG2;   // fill value
    const Register count = R5_ARG3;   // elements count
    const Register temp  = R6_ARG4;   // temp register

    //assert_clean_int(count, O3);    // Make sure 'count' is clean int.

    Label L_exit, L_skip_align1, L_skip_align2, L_fill_byte;
    Label L_fill_2_bytes, L_fill_4_bytes, L_fill_elements, L_fill_32_bytes;

    int shift = -1;
    switch (t) {
       case T_BYTE:
        shift = 2;
        // Clone bytes (zero extend not needed because store instructions below ignore high order bytes).
        __ rldimi(value, value, 8, 48);     // 8 bit -> 16 bit
        __ cmpdi(CCR0, count, 2<<shift);    // Short arrays (< 8 bytes) fill by element.
        __ blt(CCR0, L_fill_elements);
        __ rldimi(value, value, 16, 32);    // 16 bit -> 32 bit
        break;
       case T_SHORT:
        shift = 1;
        // Clone bytes (zero extend not needed because store instructions below ignore high order bytes).
        __ rldimi(value, value, 16, 32);    // 16 bit -> 32 bit
        __ cmpdi(CCR0, count, 2<<shift);    // Short arrays (< 8 bytes) fill by element.
        __ blt(CCR0, L_fill_elements);
        break;
      case T_INT:
        shift = 0;
        __ cmpdi(CCR0, count, 2<<shift);    // Short arrays (< 8 bytes) fill by element.
        __ blt(CCR0, L_fill_4_bytes);
        break;
      default: ShouldNotReachHere();
    }

    if (!aligned && (t == T_BYTE || t == T_SHORT)) {
      // Align source address at 4 bytes address boundary.
      if (t == T_BYTE) {
        // One byte misalignment happens only for byte arrays.
        __ andi_(temp, to, 1);
        __ beq(CCR0, L_skip_align1);
        __ stb(value, 0, to);
        __ addi(to, to, 1);
        __ addi(count, count, -1);
        __ bind(L_skip_align1);
      }
      // Two bytes misalignment happens only for byte and short (char) arrays.
      __ andi_(temp, to, 2);
      __ beq(CCR0, L_skip_align2);
      __ sth(value, 0, to);
      __ addi(to, to, 2);
      __ addi(count, count, -(1 << (shift - 1)));
      __ bind(L_skip_align2);
    }

    if (!aligned) {
      // Align to 8 bytes, we know we are 4 byte aligned to start.
      __ andi_(temp, to, 7);
      __ beq(CCR0, L_fill_32_bytes);
      __ stw(value, 0, to);
      __ addi(to, to, 4);
      __ addi(count, count, -(1 << shift));
      __ bind(L_fill_32_bytes);
    }

    __ li(temp, 8<<shift);                  // Prepare for 32 byte loop.
    // Clone bytes int->long as above.
    __ rldimi(value, value, 32, 0);         // 32 bit -> 64 bit

    Label L_check_fill_8_bytes;
    // Fill 32-byte chunks.
    __ subf_(count, temp, count);
    __ blt(CCR0, L_check_fill_8_bytes);

    Label L_fill_32_bytes_loop;
    __ align(32);
    __ bind(L_fill_32_bytes_loop);

    __ std(value, 0, to);
    __ std(value, 8, to);
    __ subf_(count, temp, count);           // Update count.
    __ std(value, 16, to);
    __ std(value, 24, to);

    __ addi(to, to, 32);
    __ bge(CCR0, L_fill_32_bytes_loop);

    __ bind(L_check_fill_8_bytes);
    __ add_(count, temp, count);
    __ beq(CCR0, L_exit);
    __ addic_(count, count, -(2 << shift));
    __ blt(CCR0, L_fill_4_bytes);

    //
    // Length is too short, just fill 8 bytes at a time.
    //
    Label L_fill_8_bytes_loop;
    __ bind(L_fill_8_bytes_loop);
    __ std(value, 0, to);
    __ addic_(count, count, -(2 << shift));
    __ addi(to, to, 8);
    __ bge(CCR0, L_fill_8_bytes_loop);

    // Fill trailing 4 bytes.
    __ bind(L_fill_4_bytes);
    __ andi_(temp, count, 1<<shift);
    __ beq(CCR0, L_fill_2_bytes);

    __ stw(value, 0, to);
    if (t == T_BYTE || t == T_SHORT) {
      __ addi(to, to, 4);
      // Fill trailing 2 bytes.
      __ bind(L_fill_2_bytes);
      __ andi_(temp, count, 1<<(shift-1));
      __ beq(CCR0, L_fill_byte);
      __ sth(value, 0, to);
      if (t == T_BYTE) {
        __ addi(to, to, 2);
        // Fill trailing byte.
        __ bind(L_fill_byte);
        __ andi_(count, count, 1);
        __ beq(CCR0, L_exit);
        __ stb(value, 0, to);
      } else {
        __ bind(L_fill_byte);
      }
    } else {
      __ bind(L_fill_2_bytes);
    }
    __ bind(L_exit);
    __ blr();

    // Handle copies less than 8 bytes. Int is handled elsewhere.
    if (t == T_BYTE) {
      __ bind(L_fill_elements);
      Label L_fill_2, L_fill_4;
      __ andi_(temp, count, 1);
      __ beq(CCR0, L_fill_2);
      __ stb(value, 0, to);
      __ addi(to, to, 1);
      __ bind(L_fill_2);
      __ andi_(temp, count, 2);
      __ beq(CCR0, L_fill_4);
      __ stb(value, 0, to);
      __ stb(value, 0, to);
      __ addi(to, to, 2);
      __ bind(L_fill_4);
      __ andi_(temp, count, 4);
      __ beq(CCR0, L_exit);
      __ stb(value, 0, to);
      __ stb(value, 1, to);
      __ stb(value, 2, to);
      __ stb(value, 3, to);
      __ blr();
    }

    if (t == T_SHORT) {
      Label L_fill_2;
      __ bind(L_fill_elements);
      __ andi_(temp, count, 1);
      __ beq(CCR0, L_fill_2);
      __ sth(value, 0, to);
      __ addi(to, to, 2);
      __ bind(L_fill_2);
      __ andi_(temp, count, 2);
      __ beq(CCR0, L_exit);
      __ sth(value, 0, to);
      __ sth(value, 2, to);
      __ blr();
    }
    return start;
  }


  // Generate overlap test for array copy stubs.
  //
  // Input:
  //   R3_ARG1    -  from
  //   R4_ARG2    -  to
  //   R5_ARG3    -  element count
  //
  void array_overlap_test(address no_overlap_target, int log2_elem_size) {
    Register tmp1 = R6_ARG4;
    Register tmp2 = R7_ARG5;

    Label l_overlap;
#ifdef ASSERT
    __ srdi_(tmp2, R5_ARG3, 31);
    __ asm_assert_eq("missing zero extend", 0xAFFE);
#endif

    __ subf(tmp1, R3_ARG1, R4_ARG2); // distance in bytes
    __ sldi(tmp2, R5_ARG3, log2_elem_size); // size in bytes
    __ cmpld(CCR0, R3_ARG1, R4_ARG2); // Use unsigned comparison!
    __ cmpld(CCR1, tmp1, tmp2);
    __ crand(CCR0, Assembler::less, CCR1, Assembler::less);
    __ blt(CCR0, l_overlap); // Src before dst and distance smaller than size.

    // need to copy forwards
    if (__ is_within_range_of_b(no_overlap_target, __ pc())) {
      __ b(no_overlap_target);
    } else {
      __ load_const(tmp1, no_overlap_target, tmp2);
      __ mtctr(tmp1);
      __ bctr();
    }

    __ bind(l_overlap);
    // need to copy backwards
  }

  // The guideline in the implementations of generate_disjoint_xxx_copy
  // (xxx=byte,short,int,long,oop) is to copy as many elements as possible with
  // single instructions, but to avoid alignment interrupts (see subsequent
  // comment). Furthermore, we try to minimize misaligned access, even
  // though they cause no alignment interrupt.
  //
  // In Big-Endian mode, the PowerPC architecture requires implementations to
  // handle automatically misaligned integer halfword and word accesses,
  // word-aligned integer doubleword accesses, and word-aligned floating-point
  // accesses. Other accesses may or may not generate an Alignment interrupt
  // depending on the implementation.
  // Alignment interrupt handling may require on the order of hundreds of cycles,
  // so every effort should be made to avoid misaligned memory values.
  //
  //
  // Generate stub for disjoint byte copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  address generate_disjoint_byte_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();

    Register tmp1 = R6_ARG4;
    Register tmp2 = R7_ARG5;
    Register tmp3 = R8_ARG6;
    Register tmp4 = R9_ARG7;


    Label l_1, l_2, l_3, l_4, l_5, l_6, l_7, l_8, l_9;
    // Don't try anything fancy if arrays don't have many elements.
    __ li(tmp3, 0);
    __ cmpwi(CCR0, R5_ARG3, 17);
    __ ble(CCR0, l_6); // copy 4 at a time

    if (!aligned) {
      __ xorr(tmp1, R3_ARG1, R4_ARG2);
      __ andi_(tmp1, tmp1, 3);
      __ bne(CCR0, l_6); // If arrays don't have the same alignment mod 4, do 4 element copy.

      // Copy elements if necessary to align to 4 bytes.
      __ neg(tmp1, R3_ARG1); // Compute distance to alignment boundary.
      __ andi_(tmp1, tmp1, 3);
      __ beq(CCR0, l_2);

      __ subf(R5_ARG3, tmp1, R5_ARG3);
      __ bind(l_9);
      __ lbz(tmp2, 0, R3_ARG1);
      __ addic_(tmp1, tmp1, -1);
      __ stb(tmp2, 0, R4_ARG2);
      __ addi(R3_ARG1, R3_ARG1, 1);
      __ addi(R4_ARG2, R4_ARG2, 1);
      __ bne(CCR0, l_9);

      __ bind(l_2);
    }

    // copy 8 elements at a time
    __ xorr(tmp2, R3_ARG1, R4_ARG2); // skip if src & dest have differing alignment mod 8
    __ andi_(tmp1, tmp2, 7);
    __ bne(CCR0, l_7); // not same alignment -> to or from is aligned -> copy 8

    // copy a 2-element word if necessary to align to 8 bytes
    __ andi_(R0, R3_ARG1, 7);
    __ beq(CCR0, l_7);

    __ lwzx(tmp2, R3_ARG1, tmp3);
    __ addi(R5_ARG3, R5_ARG3, -4);
    __ stwx(tmp2, R4_ARG2, tmp3);
    { // FasterArrayCopy
      __ addi(R3_ARG1, R3_ARG1, 4);
      __ addi(R4_ARG2, R4_ARG2, 4);
    }
    __ bind(l_7);

    { // FasterArrayCopy
      __ cmpwi(CCR0, R5_ARG3, 31);
      __ ble(CCR0, l_6); // copy 2 at a time if less than 32 elements remain

      __ srdi(tmp1, R5_ARG3, 5);
      __ andi_(R5_ARG3, R5_ARG3, 31);
      __ mtctr(tmp1);

      __ bind(l_8);
      // Use unrolled version for mass copying (copy 32 elements a time)
      // Load feeding store gets zero latency on Power6, however not on Power5.
      // Therefore, the following sequence is made for the good of both.
      __ ld(tmp1, 0, R3_ARG1);
      __ ld(tmp2, 8, R3_ARG1);
      __ ld(tmp3, 16, R3_ARG1);
      __ ld(tmp4, 24, R3_ARG1);
      __ std(tmp1, 0, R4_ARG2);
      __ std(tmp2, 8, R4_ARG2);
      __ std(tmp3, 16, R4_ARG2);
      __ std(tmp4, 24, R4_ARG2);
      __ addi(R3_ARG1, R3_ARG1, 32);
      __ addi(R4_ARG2, R4_ARG2, 32);
      __ bdnz(l_8);
    }

    __ bind(l_6);

    // copy 4 elements at a time
    __ cmpwi(CCR0, R5_ARG3, 4);
    __ blt(CCR0, l_1);
    __ srdi(tmp1, R5_ARG3, 2);
    __ mtctr(tmp1); // is > 0
    __ andi_(R5_ARG3, R5_ARG3, 3);

    { // FasterArrayCopy
      __ addi(R3_ARG1, R3_ARG1, -4);
      __ addi(R4_ARG2, R4_ARG2, -4);
      __ bind(l_3);
      __ lwzu(tmp2, 4, R3_ARG1);
      __ stwu(tmp2, 4, R4_ARG2);
      __ bdnz(l_3);
      __ addi(R3_ARG1, R3_ARG1, 4);
      __ addi(R4_ARG2, R4_ARG2, 4);
    }

    // do single element copy
    __ bind(l_1);
    __ cmpwi(CCR0, R5_ARG3, 0);
    __ beq(CCR0, l_4);

    { // FasterArrayCopy
      __ mtctr(R5_ARG3);
      __ addi(R3_ARG1, R3_ARG1, -1);
      __ addi(R4_ARG2, R4_ARG2, -1);

      __ bind(l_5);
      __ lbzu(tmp2, 1, R3_ARG1);
      __ stbu(tmp2, 1, R4_ARG2);
      __ bdnz(l_5);
    }

    __ bind(l_4);
    __ blr();

    return start;
  }

  // Generate stub for conjoint byte copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  address generate_conjoint_byte_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();

    Register tmp1 = R6_ARG4;
    Register tmp2 = R7_ARG5;
    Register tmp3 = R8_ARG6;

#if defined(ABI_ELFv2)
    address nooverlap_target = aligned ?
      StubRoutines::arrayof_jbyte_disjoint_arraycopy() :
      StubRoutines::jbyte_disjoint_arraycopy();
#else
    address nooverlap_target = aligned ?
      ((FunctionDescriptor*)StubRoutines::arrayof_jbyte_disjoint_arraycopy())->entry() :
      ((FunctionDescriptor*)StubRoutines::jbyte_disjoint_arraycopy())->entry();
#endif

    array_overlap_test(nooverlap_target, 0);
    // Do reverse copy. We assume the case of actual overlap is rare enough
    // that we don't have to optimize it.
    Label l_1, l_2;

    __ b(l_2);
    __ bind(l_1);
    __ stbx(tmp1, R4_ARG2, R5_ARG3);
    __ bind(l_2);
    __ addic_(R5_ARG3, R5_ARG3, -1);
    __ lbzx(tmp1, R3_ARG1, R5_ARG3);
    __ bge(CCR0, l_1);

    __ blr();

    return start;
  }

  // Generate stub for disjoint short copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //  elm.count: R5_ARG3 treated as signed
  //
  // Strategy for aligned==true:
  //
  //  If length <= 9:
  //     1. copy 2 elements at a time (l_6)
  //     2. copy last element if original element count was odd (l_1)
  //
  //  If length > 9:
  //     1. copy 4 elements at a time until less than 4 elements are left (l_7)
  //     2. copy 2 elements at a time until less than 2 elements are left (l_6)
  //     3. copy last element if one was left in step 2. (l_1)
  //
  //
  // Strategy for aligned==false:
  //
  //  If length <= 9: same as aligned==true case, but NOTE: load/stores
  //                  can be unaligned (see comment below)
  //
  //  If length > 9:
  //     1. continue with step 6. if the alignment of from and to mod 4
  //        is different.
  //     2. align from and to to 4 bytes by copying 1 element if necessary
  //     3. at l_2 from and to are 4 byte aligned; continue with
  //        5. if they cannot be aligned to 8 bytes because they have
  //        got different alignment mod 8.
  //     4. at this point we know that both, from and to, have the same
  //        alignment mod 8, now copy one element if necessary to get
  //        8 byte alignment of from and to.
  //     5. copy 4 elements at a time until less than 4 elements are
  //        left; depending on step 3. all load/stores are aligned or
  //        either all loads or all stores are unaligned.
  //     6. copy 2 elements at a time until less than 2 elements are
  //        left (l_6); arriving here from step 1., there is a chance
  //        that all accesses are unaligned.
  //     7. copy last element if one was left in step 6. (l_1)
  //
  //  There are unaligned data accesses using integer load/store
  //  instructions in this stub. POWER allows such accesses.
  //
  //  According to the manuals (PowerISA_V2.06_PUBLIC, Book II,
  //  Chapter 2: Effect of Operand Placement on Performance) unaligned
  //  integer load/stores have good performance. Only unaligned
  //  floating point load/stores can have poor performance.
  //
  //  TODO:
  //
  //  1. check if aligning the backbranch target of loops is beneficial
  //
  address generate_disjoint_short_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);

    Register tmp1 = R6_ARG4;
    Register tmp2 = R7_ARG5;
    Register tmp3 = R8_ARG6;
    Register tmp4 = R9_ARG7;

    address start = __ function_entry();

      Label l_1, l_2, l_3, l_4, l_5, l_6, l_7, l_8;
    // don't try anything fancy if arrays don't have many elements
    __ li(tmp3, 0);
    __ cmpwi(CCR0, R5_ARG3, 9);
    __ ble(CCR0, l_6); // copy 2 at a time

    if (!aligned) {
      __ xorr(tmp1, R3_ARG1, R4_ARG2);
      __ andi_(tmp1, tmp1, 3);
      __ bne(CCR0, l_6); // if arrays don't have the same alignment mod 4, do 2 element copy

      // At this point it is guaranteed that both, from and to have the same alignment mod 4.

      // Copy 1 element if necessary to align to 4 bytes.
      __ andi_(tmp1, R3_ARG1, 3);
      __ beq(CCR0, l_2);

      __ lhz(tmp2, 0, R3_ARG1);
      __ addi(R3_ARG1, R3_ARG1, 2);
      __ sth(tmp2, 0, R4_ARG2);
      __ addi(R4_ARG2, R4_ARG2, 2);
      __ addi(R5_ARG3, R5_ARG3, -1);
      __ bind(l_2);

      // At this point the positions of both, from and to, are at least 4 byte aligned.

      // Copy 4 elements at a time.
      // Align to 8 bytes, but only if both, from and to, have same alignment mod 8.
      __ xorr(tmp2, R3_ARG1, R4_ARG2);
      __ andi_(tmp1, tmp2, 7);
      __ bne(CCR0, l_7); // not same alignment mod 8 -> copy 4, either from or to will be unaligned

      // Copy a 2-element word if necessary to align to 8 bytes.
      __ andi_(R0, R3_ARG1, 7);
      __ beq(CCR0, l_7);

      __ lwzx(tmp2, R3_ARG1, tmp3);
      __ addi(R5_ARG3, R5_ARG3, -2);
      __ stwx(tmp2, R4_ARG2, tmp3);
      { // FasterArrayCopy
        __ addi(R3_ARG1, R3_ARG1, 4);
        __ addi(R4_ARG2, R4_ARG2, 4);
      }
    }

    __ bind(l_7);

    // Copy 4 elements at a time; either the loads or the stores can
    // be unaligned if aligned == false.

    { // FasterArrayCopy
      __ cmpwi(CCR0, R5_ARG3, 15);
      __ ble(CCR0, l_6); // copy 2 at a time if less than 16 elements remain

      __ srdi(tmp1, R5_ARG3, 4);
      __ andi_(R5_ARG3, R5_ARG3, 15);
      __ mtctr(tmp1);

      __ bind(l_8);
      // Use unrolled version for mass copying (copy 16 elements a time).
      // Load feeding store gets zero latency on Power6, however not on Power5.
      // Therefore, the following sequence is made for the good of both.
      __ ld(tmp1, 0, R3_ARG1);
      __ ld(tmp2, 8, R3_ARG1);
      __ ld(tmp3, 16, R3_ARG1);
      __ ld(tmp4, 24, R3_ARG1);
      __ std(tmp1, 0, R4_ARG2);
      __ std(tmp2, 8, R4_ARG2);
      __ std(tmp3, 16, R4_ARG2);
      __ std(tmp4, 24, R4_ARG2);
      __ addi(R3_ARG1, R3_ARG1, 32);
      __ addi(R4_ARG2, R4_ARG2, 32);
      __ bdnz(l_8);
    }
    __ bind(l_6);

    // copy 2 elements at a time
    { // FasterArrayCopy
      __ cmpwi(CCR0, R5_ARG3, 2);
      __ blt(CCR0, l_1);
      __ srdi(tmp1, R5_ARG3, 1);
      __ andi_(R5_ARG3, R5_ARG3, 1);

      __ addi(R3_ARG1, R3_ARG1, -4);
      __ addi(R4_ARG2, R4_ARG2, -4);
      __ mtctr(tmp1);

      __ bind(l_3);
      __ lwzu(tmp2, 4, R3_ARG1);
      __ stwu(tmp2, 4, R4_ARG2);
      __ bdnz(l_3);

      __ addi(R3_ARG1, R3_ARG1, 4);
      __ addi(R4_ARG2, R4_ARG2, 4);
    }

    // do single element copy
    __ bind(l_1);
    __ cmpwi(CCR0, R5_ARG3, 0);
    __ beq(CCR0, l_4);

    { // FasterArrayCopy
      __ mtctr(R5_ARG3);
      __ addi(R3_ARG1, R3_ARG1, -2);
      __ addi(R4_ARG2, R4_ARG2, -2);

      __ bind(l_5);
      __ lhzu(tmp2, 2, R3_ARG1);
      __ sthu(tmp2, 2, R4_ARG2);
      __ bdnz(l_5);
    }
    __ bind(l_4);
    __ blr();

    return start;
  }

  // Generate stub for conjoint short copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  address generate_conjoint_short_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();

    Register tmp1 = R6_ARG4;
    Register tmp2 = R7_ARG5;
    Register tmp3 = R8_ARG6;

#if defined(ABI_ELFv2)
    address nooverlap_target = aligned ?
        StubRoutines::arrayof_jshort_disjoint_arraycopy() :
        StubRoutines::jshort_disjoint_arraycopy();
#else
    address nooverlap_target = aligned ?
        ((FunctionDescriptor*)StubRoutines::arrayof_jshort_disjoint_arraycopy())->entry() :
        ((FunctionDescriptor*)StubRoutines::jshort_disjoint_arraycopy())->entry();
#endif

    array_overlap_test(nooverlap_target, 1);

    Label l_1, l_2;
    __ sldi(tmp1, R5_ARG3, 1);
    __ b(l_2);
    __ bind(l_1);
    __ sthx(tmp2, R4_ARG2, tmp1);
    __ bind(l_2);
    __ addic_(tmp1, tmp1, -2);
    __ lhzx(tmp2, R3_ARG1, tmp1);
    __ bge(CCR0, l_1);

    __ blr();

    return start;
  }

  // Generate core code for disjoint int copy (and oop copy on 32-bit).  If "aligned"
  // is true, the "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  void generate_disjoint_int_copy_core(bool aligned) {
    Register tmp1 = R6_ARG4;
    Register tmp2 = R7_ARG5;
    Register tmp3 = R8_ARG6;
    Register tmp4 = R0;

    Label l_1, l_2, l_3, l_4, l_5, l_6;
    // for short arrays, just do single element copy
    __ li(tmp3, 0);
    __ cmpwi(CCR0, R5_ARG3, 5);
    __ ble(CCR0, l_2);

    if (!aligned) {
        // check if arrays have same alignment mod 8.
        __ xorr(tmp1, R3_ARG1, R4_ARG2);
        __ andi_(R0, tmp1, 7);
        // Not the same alignment, but ld and std just need to be 4 byte aligned.
        __ bne(CCR0, l_4); // to OR from is 8 byte aligned -> copy 2 at a time

        // copy 1 element to align to and from on an 8 byte boundary
        __ andi_(R0, R3_ARG1, 7);
        __ beq(CCR0, l_4);

        __ lwzx(tmp2, R3_ARG1, tmp3);
        __ addi(R5_ARG3, R5_ARG3, -1);
        __ stwx(tmp2, R4_ARG2, tmp3);
        { // FasterArrayCopy
          __ addi(R3_ARG1, R3_ARG1, 4);
          __ addi(R4_ARG2, R4_ARG2, 4);
        }
        __ bind(l_4);
      }

    { // FasterArrayCopy
      __ cmpwi(CCR0, R5_ARG3, 7);
      __ ble(CCR0, l_2); // copy 1 at a time if less than 8 elements remain

      __ srdi(tmp1, R5_ARG3, 3);
      __ andi_(R5_ARG3, R5_ARG3, 7);
      __ mtctr(tmp1);

      __ bind(l_6);
      // Use unrolled version for mass copying (copy 8 elements a time).
      // Load feeding store gets zero latency on power6, however not on power 5.
      // Therefore, the following sequence is made for the good of both.
      __ ld(tmp1, 0, R3_ARG1);
      __ ld(tmp2, 8, R3_ARG1);
      __ ld(tmp3, 16, R3_ARG1);
      __ ld(tmp4, 24, R3_ARG1);
      __ std(tmp1, 0, R4_ARG2);
      __ std(tmp2, 8, R4_ARG2);
      __ std(tmp3, 16, R4_ARG2);
      __ std(tmp4, 24, R4_ARG2);
      __ addi(R3_ARG1, R3_ARG1, 32);
      __ addi(R4_ARG2, R4_ARG2, 32);
      __ bdnz(l_6);
    }

    // copy 1 element at a time
    __ bind(l_2);
    __ cmpwi(CCR0, R5_ARG3, 0);
    __ beq(CCR0, l_1);

    { // FasterArrayCopy
      __ mtctr(R5_ARG3);
      __ addi(R3_ARG1, R3_ARG1, -4);
      __ addi(R4_ARG2, R4_ARG2, -4);

      __ bind(l_3);
      __ lwzu(tmp2, 4, R3_ARG1);
      __ stwu(tmp2, 4, R4_ARG2);
      __ bdnz(l_3);
    }

    __ bind(l_1);
    return;
  }

  // Generate stub for disjoint int copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  address generate_disjoint_int_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();
    generate_disjoint_int_copy_core(aligned);
    __ blr();
    return start;
  }

  // Generate core code for conjoint int copy (and oop copy on
  // 32-bit).  If "aligned" is true, the "from" and "to" addresses
  // are assumed to be heapword aligned.
  //
  // Arguments:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  void generate_conjoint_int_copy_core(bool aligned) {
    // Do reverse copy.  We assume the case of actual overlap is rare enough
    // that we don't have to optimize it.

    Label l_1, l_2, l_3, l_4, l_5, l_6;

    Register tmp1 = R6_ARG4;
    Register tmp2 = R7_ARG5;
    Register tmp3 = R8_ARG6;
    Register tmp4 = R0;

    { // FasterArrayCopy
      __ cmpwi(CCR0, R5_ARG3, 0);
      __ beq(CCR0, l_6);

      __ sldi(R5_ARG3, R5_ARG3, 2);
      __ add(R3_ARG1, R3_ARG1, R5_ARG3);
      __ add(R4_ARG2, R4_ARG2, R5_ARG3);
      __ srdi(R5_ARG3, R5_ARG3, 2);

      __ cmpwi(CCR0, R5_ARG3, 7);
      __ ble(CCR0, l_5); // copy 1 at a time if less than 8 elements remain

      __ srdi(tmp1, R5_ARG3, 3);
      __ andi(R5_ARG3, R5_ARG3, 7);
      __ mtctr(tmp1);

      __ bind(l_4);
      // Use unrolled version for mass copying (copy 4 elements a time).
      // Load feeding store gets zero latency on Power6, however not on Power5.
      // Therefore, the following sequence is made for the good of both.
      __ addi(R3_ARG1, R3_ARG1, -32);
      __ addi(R4_ARG2, R4_ARG2, -32);
      __ ld(tmp4, 24, R3_ARG1);
      __ ld(tmp3, 16, R3_ARG1);
      __ ld(tmp2, 8, R3_ARG1);
      __ ld(tmp1, 0, R3_ARG1);
      __ std(tmp4, 24, R4_ARG2);
      __ std(tmp3, 16, R4_ARG2);
      __ std(tmp2, 8, R4_ARG2);
      __ std(tmp1, 0, R4_ARG2);
      __ bdnz(l_4);

      __ cmpwi(CCR0, R5_ARG3, 0);
      __ beq(CCR0, l_6);

      __ bind(l_5);
      __ mtctr(R5_ARG3);
      __ bind(l_3);
      __ lwz(R0, -4, R3_ARG1);
      __ stw(R0, -4, R4_ARG2);
      __ addi(R3_ARG1, R3_ARG1, -4);
      __ addi(R4_ARG2, R4_ARG2, -4);
      __ bdnz(l_3);

      __ bind(l_6);
    }
  }

  // Generate stub for conjoint int copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  address generate_conjoint_int_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();

#if defined(ABI_ELFv2)
    address nooverlap_target = aligned ?
      StubRoutines::arrayof_jint_disjoint_arraycopy() :
      StubRoutines::jint_disjoint_arraycopy();
#else
    address nooverlap_target = aligned ?
      ((FunctionDescriptor*)StubRoutines::arrayof_jint_disjoint_arraycopy())->entry() :
      ((FunctionDescriptor*)StubRoutines::jint_disjoint_arraycopy())->entry();
#endif

    array_overlap_test(nooverlap_target, 2);

    generate_conjoint_int_copy_core(aligned);

    __ blr();

    return start;
  }

  // Generate core code for disjoint long copy (and oop copy on
  // 64-bit).  If "aligned" is true, the "from" and "to" addresses
  // are assumed to be heapword aligned.
  //
  // Arguments:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  void generate_disjoint_long_copy_core(bool aligned) {
    Register tmp1 = R6_ARG4;
    Register tmp2 = R7_ARG5;
    Register tmp3 = R8_ARG6;
    Register tmp4 = R0;

    Label l_1, l_2, l_3, l_4;

    { // FasterArrayCopy
      __ cmpwi(CCR0, R5_ARG3, 3);
      __ ble(CCR0, l_3); // copy 1 at a time if less than 4 elements remain

      __ srdi(tmp1, R5_ARG3, 2);
      __ andi_(R5_ARG3, R5_ARG3, 3);
      __ mtctr(tmp1);

      __ bind(l_4);
      // Use unrolled version for mass copying (copy 4 elements a time).
      // Load feeding store gets zero latency on Power6, however not on Power5.
      // Therefore, the following sequence is made for the good of both.
      __ ld(tmp1, 0, R3_ARG1);
      __ ld(tmp2, 8, R3_ARG1);
      __ ld(tmp3, 16, R3_ARG1);
      __ ld(tmp4, 24, R3_ARG1);
      __ std(tmp1, 0, R4_ARG2);
      __ std(tmp2, 8, R4_ARG2);
      __ std(tmp3, 16, R4_ARG2);
      __ std(tmp4, 24, R4_ARG2);
      __ addi(R3_ARG1, R3_ARG1, 32);
      __ addi(R4_ARG2, R4_ARG2, 32);
      __ bdnz(l_4);
    }

    // copy 1 element at a time
    __ bind(l_3);
    __ cmpwi(CCR0, R5_ARG3, 0);
    __ beq(CCR0, l_1);

    { // FasterArrayCopy
      __ mtctr(R5_ARG3);
      __ addi(R3_ARG1, R3_ARG1, -8);
      __ addi(R4_ARG2, R4_ARG2, -8);

      __ bind(l_2);
      __ ldu(R0, 8, R3_ARG1);
      __ stdu(R0, 8, R4_ARG2);
      __ bdnz(l_2);

    }
    __ bind(l_1);
  }

  // Generate stub for disjoint long copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  address generate_disjoint_long_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();
    generate_disjoint_long_copy_core(aligned);
    __ blr();

    return start;
  }

  // Generate core code for conjoint long copy (and oop copy on
  // 64-bit).  If "aligned" is true, the "from" and "to" addresses
  // are assumed to be heapword aligned.
  //
  // Arguments:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  void generate_conjoint_long_copy_core(bool aligned) {
    Register tmp1 = R6_ARG4;
    Register tmp2 = R7_ARG5;
    Register tmp3 = R8_ARG6;
    Register tmp4 = R0;

    Label l_1, l_2, l_3, l_4, l_5;

    __ cmpwi(CCR0, R5_ARG3, 0);
    __ beq(CCR0, l_1);

    { // FasterArrayCopy
      __ sldi(R5_ARG3, R5_ARG3, 3);
      __ add(R3_ARG1, R3_ARG1, R5_ARG3);
      __ add(R4_ARG2, R4_ARG2, R5_ARG3);
      __ srdi(R5_ARG3, R5_ARG3, 3);

      __ cmpwi(CCR0, R5_ARG3, 3);
      __ ble(CCR0, l_5); // copy 1 at a time if less than 4 elements remain

      __ srdi(tmp1, R5_ARG3, 2);
      __ andi(R5_ARG3, R5_ARG3, 3);
      __ mtctr(tmp1);

      __ bind(l_4);
      // Use unrolled version for mass copying (copy 4 elements a time).
      // Load feeding store gets zero latency on Power6, however not on Power5.
      // Therefore, the following sequence is made for the good of both.
      __ addi(R3_ARG1, R3_ARG1, -32);
      __ addi(R4_ARG2, R4_ARG2, -32);
      __ ld(tmp4, 24, R3_ARG1);
      __ ld(tmp3, 16, R3_ARG1);
      __ ld(tmp2, 8, R3_ARG1);
      __ ld(tmp1, 0, R3_ARG1);
      __ std(tmp4, 24, R4_ARG2);
      __ std(tmp3, 16, R4_ARG2);
      __ std(tmp2, 8, R4_ARG2);
      __ std(tmp1, 0, R4_ARG2);
      __ bdnz(l_4);

      __ cmpwi(CCR0, R5_ARG3, 0);
      __ beq(CCR0, l_1);

      __ bind(l_5);
      __ mtctr(R5_ARG3);
      __ bind(l_3);
      __ ld(R0, -8, R3_ARG1);
      __ std(R0, -8, R4_ARG2);
      __ addi(R3_ARG1, R3_ARG1, -8);
      __ addi(R4_ARG2, R4_ARG2, -8);
      __ bdnz(l_3);

    }
    __ bind(l_1);
  }

  // Generate stub for conjoint long copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //
  address generate_conjoint_long_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();

#if defined(ABI_ELFv2)
    address nooverlap_target = aligned ?
      StubRoutines::arrayof_jlong_disjoint_arraycopy() :
      StubRoutines::jlong_disjoint_arraycopy();
#else
    address nooverlap_target = aligned ?
      ((FunctionDescriptor*)StubRoutines::arrayof_jlong_disjoint_arraycopy())->entry() :
      ((FunctionDescriptor*)StubRoutines::jlong_disjoint_arraycopy())->entry();
#endif

    array_overlap_test(nooverlap_target, 3);
    generate_conjoint_long_copy_core(aligned);

    __ blr();

    return start;
  }

  // Generate stub for conjoint oop copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //      dest_uninitialized: G1 support
  //
  address generate_conjoint_oop_copy(bool aligned, const char * name, bool dest_uninitialized) {
    StubCodeMark mark(this, "StubRoutines", name);

    address start = __ function_entry();

#if defined(ABI_ELFv2)
    address nooverlap_target = aligned ?
      StubRoutines::arrayof_oop_disjoint_arraycopy() :
      StubRoutines::oop_disjoint_arraycopy();
#else
    address nooverlap_target = aligned ?
      ((FunctionDescriptor*)StubRoutines::arrayof_oop_disjoint_arraycopy())->entry() :
      ((FunctionDescriptor*)StubRoutines::oop_disjoint_arraycopy())->entry();
#endif

    gen_write_ref_array_pre_barrier(R3_ARG1, R4_ARG2, R5_ARG3, dest_uninitialized, R9_ARG7);

    // Save arguments.
    __ mr(R9_ARG7, R4_ARG2);
    __ mr(R10_ARG8, R5_ARG3);

    if (UseCompressedOops) {
      array_overlap_test(nooverlap_target, 2);
      generate_conjoint_int_copy_core(aligned);
    } else {
      array_overlap_test(nooverlap_target, 3);
      generate_conjoint_long_copy_core(aligned);
    }

    gen_write_ref_array_post_barrier(R9_ARG7, R10_ARG8, R11_scratch1, /*branchToEnd*/ false);
    return start;
  }

  // Generate stub for disjoint oop copy.  If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  R3_ARG1
  //      to:    R4_ARG2
  //      count: R5_ARG3 treated as signed
  //      dest_uninitialized: G1 support
  //
  address generate_disjoint_oop_copy(bool aligned, const char * name, bool dest_uninitialized) {
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();

    gen_write_ref_array_pre_barrier(R3_ARG1, R4_ARG2, R5_ARG3, dest_uninitialized, R9_ARG7);

    // save some arguments, disjoint_long_copy_core destroys them.
    // needed for post barrier
    __ mr(R9_ARG7, R4_ARG2);
    __ mr(R10_ARG8, R5_ARG3);

    if (UseCompressedOops) {
      generate_disjoint_int_copy_core(aligned);
    } else {
      generate_disjoint_long_copy_core(aligned);
    }

    gen_write_ref_array_post_barrier(R9_ARG7, R10_ARG8, R11_scratch1, /*branchToEnd*/ false);

    return start;
  }

  void generate_arraycopy_stubs() {
    // Note: the disjoint stubs must be generated first, some of
    // the conjoint stubs use them.

    // non-aligned disjoint versions
    StubRoutines::_jbyte_disjoint_arraycopy       = generate_disjoint_byte_copy(false, "jbyte_disjoint_arraycopy");
    StubRoutines::_jshort_disjoint_arraycopy      = generate_disjoint_short_copy(false, "jshort_disjoint_arraycopy");
    StubRoutines::_jint_disjoint_arraycopy        = generate_disjoint_int_copy(false, "jint_disjoint_arraycopy");
    StubRoutines::_jlong_disjoint_arraycopy       = generate_disjoint_long_copy(false, "jlong_disjoint_arraycopy");
    StubRoutines::_oop_disjoint_arraycopy         = generate_disjoint_oop_copy(false, "oop_disjoint_arraycopy", false);
    StubRoutines::_oop_disjoint_arraycopy_uninit  = generate_disjoint_oop_copy(false, "oop_disjoint_arraycopy_uninit", true);

    // aligned disjoint versions
    StubRoutines::_arrayof_jbyte_disjoint_arraycopy      = generate_disjoint_byte_copy(true, "arrayof_jbyte_disjoint_arraycopy");
    StubRoutines::_arrayof_jshort_disjoint_arraycopy     = generate_disjoint_short_copy(true, "arrayof_jshort_disjoint_arraycopy");
    StubRoutines::_arrayof_jint_disjoint_arraycopy       = generate_disjoint_int_copy(true, "arrayof_jint_disjoint_arraycopy");
    StubRoutines::_arrayof_jlong_disjoint_arraycopy      = generate_disjoint_long_copy(true, "arrayof_jlong_disjoint_arraycopy");
    StubRoutines::_arrayof_oop_disjoint_arraycopy        = generate_disjoint_oop_copy(true, "arrayof_oop_disjoint_arraycopy", false);
    StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit = generate_disjoint_oop_copy(true, "oop_disjoint_arraycopy_uninit", true);

    // non-aligned conjoint versions
    StubRoutines::_jbyte_arraycopy      = generate_conjoint_byte_copy(false, "jbyte_arraycopy");
    StubRoutines::_jshort_arraycopy     = generate_conjoint_short_copy(false, "jshort_arraycopy");
    StubRoutines::_jint_arraycopy       = generate_conjoint_int_copy(false, "jint_arraycopy");
    StubRoutines::_jlong_arraycopy      = generate_conjoint_long_copy(false, "jlong_arraycopy");
    StubRoutines::_oop_arraycopy        = generate_conjoint_oop_copy(false, "oop_arraycopy", false);
    StubRoutines::_oop_arraycopy_uninit = generate_conjoint_oop_copy(false, "oop_arraycopy_uninit", true);

    // aligned conjoint versions
    StubRoutines::_arrayof_jbyte_arraycopy      = generate_conjoint_byte_copy(true, "arrayof_jbyte_arraycopy");
    StubRoutines::_arrayof_jshort_arraycopy     = generate_conjoint_short_copy(true, "arrayof_jshort_arraycopy");
    StubRoutines::_arrayof_jint_arraycopy       = generate_conjoint_int_copy(true, "arrayof_jint_arraycopy");
    StubRoutines::_arrayof_jlong_arraycopy      = generate_conjoint_long_copy(true, "arrayof_jlong_arraycopy");
    StubRoutines::_arrayof_oop_arraycopy        = generate_conjoint_oop_copy(true, "arrayof_oop_arraycopy", false);
    StubRoutines::_arrayof_oop_arraycopy_uninit = generate_conjoint_oop_copy(true, "arrayof_oop_arraycopy", true);

    // fill routines
    StubRoutines::_jbyte_fill          = generate_fill(T_BYTE,  false, "jbyte_fill");
    StubRoutines::_jshort_fill         = generate_fill(T_SHORT, false, "jshort_fill");
    StubRoutines::_jint_fill           = generate_fill(T_INT,   false, "jint_fill");
    StubRoutines::_arrayof_jbyte_fill  = generate_fill(T_BYTE,  true, "arrayof_jbyte_fill");
    StubRoutines::_arrayof_jshort_fill = generate_fill(T_SHORT, true, "arrayof_jshort_fill");
    StubRoutines::_arrayof_jint_fill   = generate_fill(T_INT,   true, "arrayof_jint_fill");
  }

  // Safefetch stubs.
  void generate_safefetch(const char* name, int size, address* entry, address* fault_pc, address* continuation_pc) {
    // safefetch signatures:
    //   int      SafeFetch32(int*      adr, int      errValue);
    //   intptr_t SafeFetchN (intptr_t* adr, intptr_t errValue);
    //
    // arguments:
    //   R3_ARG1 = adr
    //   R4_ARG2 = errValue
    //
    // result:
    //   R3_RET  = *adr or errValue

    StubCodeMark mark(this, "StubRoutines", name);

    // Entry point, pc or function descriptor.
    *entry = __ function_entry();

    // Load *adr into R4_ARG2, may fault.
    *fault_pc = __ pc();
    switch (size) {
      case 4:
        // int32_t, signed extended
        __ lwa(R4_ARG2, 0, R3_ARG1);
        break;
      case 8:
        // int64_t
        __ ld(R4_ARG2, 0, R3_ARG1);
        break;
      default:
        ShouldNotReachHere();
    }

    // return errValue or *adr
    *continuation_pc = __ pc();
    __ mr(R3_RET, R4_ARG2);
    __ blr();
  }

  // Stub for BigInteger::multiplyToLen()
  //
  //  Arguments:
  //
  //  Input:
  //    R3 - x address
  //    R4 - x length
  //    R5 - y address
  //    R6 - y length
  //    R7 - z address
  //    R8 - z length
  //
  address generate_multiplyToLen() {

    StubCodeMark mark(this, "StubRoutines", "multiplyToLen");

    address start = __ function_entry();

    const Register x     = R3;
    const Register xlen  = R4;
    const Register y     = R5;
    const Register ylen  = R6;
    const Register z     = R7;
    const Register zlen  = R8;

    const Register tmp1  = R2; // TOC not used.
    const Register tmp2  = R9;
    const Register tmp3  = R10;
    const Register tmp4  = R11;
    const Register tmp5  = R12;

    // non-volatile regs
    const Register tmp6  = R31;
    const Register tmp7  = R30;
    const Register tmp8  = R29;
    const Register tmp9  = R28;
    const Register tmp10 = R27;
    const Register tmp11 = R26;
    const Register tmp12 = R25;
    const Register tmp13 = R24;

    BLOCK_COMMENT("Entry:");

    // Save non-volatile regs (frameless).
    int current_offs = 8;
    __ std(R24, -current_offs, R1_SP); current_offs += 8;
    __ std(R25, -current_offs, R1_SP); current_offs += 8;
    __ std(R26, -current_offs, R1_SP); current_offs += 8;
    __ std(R27, -current_offs, R1_SP); current_offs += 8;
    __ std(R28, -current_offs, R1_SP); current_offs += 8;
    __ std(R29, -current_offs, R1_SP); current_offs += 8;
    __ std(R30, -current_offs, R1_SP); current_offs += 8;
    __ std(R31, -current_offs, R1_SP);

    __ multiply_to_len(x, xlen, y, ylen, z, zlen, tmp1, tmp2, tmp3, tmp4, tmp5,
                       tmp6, tmp7, tmp8, tmp9, tmp10, tmp11, tmp12, tmp13);

    // Restore non-volatile regs.
    current_offs = 8;
    __ ld(R24, -current_offs, R1_SP); current_offs += 8;
    __ ld(R25, -current_offs, R1_SP); current_offs += 8;
    __ ld(R26, -current_offs, R1_SP); current_offs += 8;
    __ ld(R27, -current_offs, R1_SP); current_offs += 8;
    __ ld(R28, -current_offs, R1_SP); current_offs += 8;
    __ ld(R29, -current_offs, R1_SP); current_offs += 8;
    __ ld(R30, -current_offs, R1_SP); current_offs += 8;
    __ ld(R31, -current_offs, R1_SP);

    __ blr();  // Return to caller.

    return start;
  }

  /**
   * Arguments:
   *
   * Inputs:
   *   R3_ARG1    - int   crc
   *   R4_ARG2    - byte* buf
   *   R5_ARG3    - int   length (of buffer)
   *
   * scratch:
   *   R6_ARG4    - crc table address
   *   R7_ARG5    - tmp1
   *   R8_ARG6    - tmp2
   *
   * Ouput:
   *   R3_RET     - int   crc result
   */
  // Compute CRC32 function.
  address generate_CRC32_updateBytes(const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ function_entry();  // Remember stub start address (is rtn value).

    // arguments to kernel_crc32:
    Register       crc     = R3_ARG1;  // Current checksum, preset by caller or result from previous call.
    Register       data    = R4_ARG2;  // source byte array
    Register       dataLen = R5_ARG3;  // #bytes to process
    Register       table   = R6_ARG4;  // crc table address

    Register       t0      = R9;       // work reg for kernel* emitters
    Register       t1      = R10;      // work reg for kernel* emitters
    Register       t2      = R11;      // work reg for kernel* emitters
    Register       t3      = R12;      // work reg for kernel* emitters

    BLOCK_COMMENT("Stub body {");
    assert_different_registers(crc, data, dataLen, table);

    StubRoutines::ppc64::generate_load_crc_table_addr(_masm, table);

    __ kernel_crc32_1byte(crc, data, dataLen, table, t0, t1, t2, t3);

    BLOCK_COMMENT("return");
    __ mr_if_needed(R3_RET, crc);      // Updated crc is function result. No copying required (R3_ARG1 == R3_RET).
    __ blr();

    BLOCK_COMMENT("} Stub body");
    return start;
  }

  // Initialization
  void generate_initial() {
    // Generates all stubs and initializes the entry points

    // Entry points that exist in all platforms.
    // Note: This is code that could be shared among different platforms - however the
    // benefit seems to be smaller than the disadvantage of having a
    // much more complicated generator structure. See also comment in
    // stubRoutines.hpp.

    StubRoutines::_forward_exception_entry          = generate_forward_exception();
    StubRoutines::_call_stub_entry                  = generate_call_stub(StubRoutines::_call_stub_return_address);
    StubRoutines::_catch_exception_entry            = generate_catch_exception();

    // Build this early so it's available for the interpreter.
    StubRoutines::_throw_StackOverflowError_entry   =
      generate_throw_exception("StackOverflowError throw_exception",
                               CAST_FROM_FN_PTR(address, SharedRuntime::throw_StackOverflowError), false);

    // CRC32 Intrinsics.
    if (UseCRC32Intrinsics) {
      StubRoutines::_crc_table_adr    = (address)StubRoutines::ppc64::_crc_table;
      StubRoutines::_updateBytesCRC32 = generate_CRC32_updateBytes("CRC32_updateBytes");
    }
  }

  void generate_all() {
    // Generates all stubs and initializes the entry points

    // These entry points require SharedInfo::stack0 to be set up in
    // non-core builds
    StubRoutines::_throw_AbstractMethodError_entry         = generate_throw_exception("AbstractMethodError throw_exception",          CAST_FROM_FN_PTR(address, SharedRuntime::throw_AbstractMethodError),  false);
    // Handle IncompatibleClassChangeError in itable stubs.
    StubRoutines::_throw_IncompatibleClassChangeError_entry= generate_throw_exception("IncompatibleClassChangeError throw_exception", CAST_FROM_FN_PTR(address, SharedRuntime::throw_IncompatibleClassChangeError),  false);
    StubRoutines::_throw_NullPointerException_at_call_entry= generate_throw_exception("NullPointerException at call throw_exception", CAST_FROM_FN_PTR(address, SharedRuntime::throw_NullPointerException_at_call), false);

    StubRoutines::_handler_for_unsafe_access_entry         = generate_handler_for_unsafe_access();

    // support for verify_oop (must happen after universe_init)
    StubRoutines::_verify_oop_subroutine_entry             = generate_verify_oop();

    // arraycopy stubs used by compilers
    generate_arraycopy_stubs();

    if (UseAESIntrinsics) {
      guarantee(!UseAESIntrinsics, "not yet implemented.");
    }

    // Safefetch stubs.
    generate_safefetch("SafeFetch32", sizeof(int),     &StubRoutines::_safefetch32_entry,
                                                       &StubRoutines::_safefetch32_fault_pc,
                                                       &StubRoutines::_safefetch32_continuation_pc);
    generate_safefetch("SafeFetchN", sizeof(intptr_t), &StubRoutines::_safefetchN_entry,
                                                       &StubRoutines::_safefetchN_fault_pc,
                                                       &StubRoutines::_safefetchN_continuation_pc);

#ifdef COMPILER2
    if (UseMultiplyToLenIntrinsic) {
      StubRoutines::_multiplyToLen = generate_multiplyToLen();
    }
#endif
  }

 public:
  StubGenerator(CodeBuffer* code, bool all) : StubCodeGenerator(code) {
    // replace the standard masm with a special one:
    _masm = new MacroAssembler(code);
    if (all) {
      generate_all();
    } else {
      generate_initial();
    }
  }
};

void StubGenerator_generate(CodeBuffer* code, bool all) {
  StubGenerator g(code, all);
}
