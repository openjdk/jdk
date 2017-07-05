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
#include "asm/macroAssembler.inline.hpp"
#include "registerSaver_s390.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interp_masm.hpp"
#include "nativeInst_s390.hpp"
#include "oops/instanceOop.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.inline.hpp"

// Declaration and definition of StubGenerator (no .hpp file).
// For a more detailed description of the stub routine structure
// see the comment in stubRoutines.hpp.

#ifdef PRODUCT
#define __ _masm->
#else
#define __ (Verbose ? (_masm->block_comment(FILE_AND_LINE),_masm):_masm)->
#endif

#define BLOCK_COMMENT(str) if (PrintAssembly) __ block_comment(str)
#define BIND(label)        bind(label); BLOCK_COMMENT(#label ":")

// -----------------------------------------------------------------------
// Stub Code definitions

class StubGenerator: public StubCodeGenerator {
 private:

  //----------------------------------------------------------------------
  // Call stubs are used to call Java from C.

  //
  // Arguments:
  //
  //   R2        - call wrapper address     : address
  //   R3        - result                   : intptr_t*
  //   R4        - result type              : BasicType
  //   R5        - method                   : method
  //   R6        - frame mgr entry point    : address
  //   [SP+160]  - parameter block          : intptr_t*
  //   [SP+172]  - parameter count in words : int
  //   [SP+176]  - thread                   : Thread*
  //
  address generate_call_stub(address& return_address) {
    // Set up a new C frame, copy Java arguments, call frame manager
    // or native_entry, and process result.

    StubCodeMark mark(this, "StubRoutines", "call_stub");
    address start = __ pc();

    Register r_arg_call_wrapper_addr   = Z_ARG1;
    Register r_arg_result_addr         = Z_ARG2;
    Register r_arg_result_type         = Z_ARG3;
    Register r_arg_method              = Z_ARG4;
    Register r_arg_entry               = Z_ARG5;

    // offsets to fp
    #define d_arg_thread 176
    #define d_arg_argument_addr 160
    #define d_arg_argument_count 168+4

    Register r_entryframe_fp           = Z_tmp_1;
    Register r_top_of_arguments_addr   = Z_ARG4;
    Register r_new_arg_entry = Z_R14;

    // macros for frame offsets
    #define call_wrapper_address_offset \
               _z_entry_frame_locals_neg(call_wrapper_address)
    #define result_address_offset \
              _z_entry_frame_locals_neg(result_address)
    #define result_type_offset \
              _z_entry_frame_locals_neg(result_type)
    #define arguments_tos_address_offset \
              _z_entry_frame_locals_neg(arguments_tos_address)

    {
      //
      // STACK on entry to call_stub:
      //
      //     F1      [C_FRAME]
      //            ...
      //

      Register r_argument_addr              = Z_tmp_3;
      Register r_argumentcopy_addr          = Z_tmp_4;
      Register r_argument_size_in_bytes     = Z_ARG5;
      Register r_frame_size                 = Z_R1;

      Label arguments_copied;

      // Save non-volatile registers to ABI of caller frame.
      BLOCK_COMMENT("save registers, push frame {");
      __ z_stmg(Z_R6, Z_R14, 16, Z_SP);
      __ z_std(Z_F8, 96, Z_SP);
      __ z_std(Z_F9, 104, Z_SP);
      __ z_std(Z_F10, 112, Z_SP);
      __ z_std(Z_F11, 120, Z_SP);
      __ z_std(Z_F12, 128, Z_SP);
      __ z_std(Z_F13, 136, Z_SP);
      __ z_std(Z_F14, 144, Z_SP);
      __ z_std(Z_F15, 152, Z_SP);

      //
      // Push ENTRY_FRAME including arguments:
      //
      //     F0      [TOP_IJAVA_FRAME_ABI]
      //             [outgoing Java arguments]
      //             [ENTRY_FRAME_LOCALS]
      //     F1      [C_FRAME]
      //             ...
      //

      // Calculate new frame size and push frame.
      #define abi_plus_locals_size \
                (frame::z_top_ijava_frame_abi_size + frame::z_entry_frame_locals_size)
      if (abi_plus_locals_size % BytesPerWord == 0) {
        // Preload constant part of frame size.
        __ load_const_optimized(r_frame_size, -abi_plus_locals_size/BytesPerWord);
        // Keep copy of our frame pointer (caller's SP).
        __ z_lgr(r_entryframe_fp, Z_SP);
        // Add space required by arguments to frame size.
        __ z_slgf(r_frame_size, d_arg_argument_count, Z_R0, Z_SP);
        // Move Z_ARG5 early, it will be used as a local.
        __ z_lgr(r_new_arg_entry, r_arg_entry);
        // Convert frame size from words to bytes.
        __ z_sllg(r_frame_size, r_frame_size, LogBytesPerWord);
        __ push_frame(r_frame_size, r_entryframe_fp,
                      false/*don't copy SP*/, true /*frame size sign inverted*/);
      } else {
        guarantee(false, "frame sizes should be multiples of word size (BytesPerWord)");
      }
      BLOCK_COMMENT("} save, push");

      // Load argument registers for call.
      BLOCK_COMMENT("prepare/copy arguments {");
      __ z_lgr(Z_method, r_arg_method);
      __ z_lg(Z_thread, d_arg_thread, r_entryframe_fp);

      // Calculate top_of_arguments_addr which will be tos (not prepushed) later.
      // Wimply use SP + frame::top_ijava_frame_size.
      __ add2reg(r_top_of_arguments_addr,
                 frame::z_top_ijava_frame_abi_size - BytesPerWord, Z_SP);

      // Initialize call_stub locals (step 1).
      if ((call_wrapper_address_offset + BytesPerWord == result_address_offset) &&
          (result_address_offset + BytesPerWord == result_type_offset)          &&
          (result_type_offset + BytesPerWord == arguments_tos_address_offset)) {

        __ z_stmg(r_arg_call_wrapper_addr, r_top_of_arguments_addr,
                  call_wrapper_address_offset, r_entryframe_fp);
      } else {
        __ z_stg(r_arg_call_wrapper_addr,
                 call_wrapper_address_offset, r_entryframe_fp);
        __ z_stg(r_arg_result_addr,
                 result_address_offset, r_entryframe_fp);
        __ z_stg(r_arg_result_type,
                 result_type_offset, r_entryframe_fp);
        __ z_stg(r_top_of_arguments_addr,
                 arguments_tos_address_offset, r_entryframe_fp);
      }

      // Copy Java arguments.

      // Any arguments to copy?
      __ load_and_test_int2long(Z_R1, Address(r_entryframe_fp, d_arg_argument_count));
      __ z_bre(arguments_copied);

      // Prepare loop and copy arguments in reverse order.
      {
        // Calculate argument size in bytes.
        __ z_sllg(r_argument_size_in_bytes, Z_R1, LogBytesPerWord);

        // Get addr of first incoming Java argument.
        __ z_lg(r_argument_addr, d_arg_argument_addr, r_entryframe_fp);

        // Let r_argumentcopy_addr point to last outgoing Java argument.
        __ add2reg(r_argumentcopy_addr, BytesPerWord, r_top_of_arguments_addr); // = Z_SP+160 effectively.

        // Let r_argument_addr point to last incoming Java argument.
        __ add2reg_with_index(r_argument_addr, -BytesPerWord,
                              r_argument_size_in_bytes, r_argument_addr);

        // Now loop while Z_R1 > 0 and copy arguments.
        {
          Label next_argument;
          __ bind(next_argument);
          // Mem-mem move.
          __ z_mvc(0, BytesPerWord-1, r_argumentcopy_addr, 0, r_argument_addr);
          __ add2reg(r_argument_addr,    -BytesPerWord);
          __ add2reg(r_argumentcopy_addr, BytesPerWord);
          __ z_brct(Z_R1, next_argument);
        }
      }  // End of argument copy loop.

      __ bind(arguments_copied);
    }
    BLOCK_COMMENT("} arguments");

    BLOCK_COMMENT("call {");
    {
      // Call frame manager or native entry.

      //
      // Register state on entry to frame manager / native entry:
      //
      //   Z_ARG1 = r_top_of_arguments_addr  - intptr_t *sender tos (prepushed)
      //                                       Lesp = (SP) + copied_arguments_offset - 8
      //   Z_method                          - method
      //   Z_thread                          - JavaThread*
      //

      // Here, the usual SP is the initial_caller_sp.
      __ z_lgr(Z_R10, Z_SP);

      // Z_esp points to the slot below the last argument.
      __ z_lgr(Z_esp, r_top_of_arguments_addr);

      //
      // Stack on entry to frame manager / native entry:
      //
      //     F0      [TOP_IJAVA_FRAME_ABI]
      //             [outgoing Java arguments]
      //             [ENTRY_FRAME_LOCALS]
      //     F1      [C_FRAME]
      //             ...
      //

      // Do a light-weight C-call here, r_new_arg_entry holds the address
      // of the interpreter entry point (frame manager or native entry)
      // and save runtime-value of return_pc in return_address
      // (call by reference argument).
      return_address = __ call_stub(r_new_arg_entry);
    }
    BLOCK_COMMENT("} call");

    {
      BLOCK_COMMENT("restore registers {");
      // Returned from frame manager or native entry.
      // Now pop frame, process result, and return to caller.

      //
      // Stack on exit from frame manager / native entry:
      //
      //     F0      [ABI]
      //             ...
      //             [ENTRY_FRAME_LOCALS]
      //     F1      [C_FRAME]
      //             ...
      //
      // Just pop the topmost frame ...
      //

      Label ret_is_object;
      Label ret_is_long;
      Label ret_is_float;
      Label ret_is_double;

      // Restore frame pointer.
      __ z_lg(r_entryframe_fp, _z_abi(callers_sp), Z_SP);
      // Pop frame. Done here to minimize stalls.
      __ z_lg(Z_SP, _z_abi(callers_sp), Z_SP);

      // Reload some volatile registers which we've spilled before the call
      // to frame manager / native entry.
      // Access all locals via frame pointer, because we know nothing about
      // the topmost frame's size.
      __ z_lg(r_arg_result_addr, result_address_offset, r_entryframe_fp);
      __ z_lg(r_arg_result_type, result_type_offset, r_entryframe_fp);

      // Restore non-volatiles.
      __ z_lmg(Z_R6, Z_R14, 16, Z_SP);
      __ z_ld(Z_F8, 96, Z_SP);
      __ z_ld(Z_F9, 104, Z_SP);
      __ z_ld(Z_F10, 112, Z_SP);
      __ z_ld(Z_F11, 120, Z_SP);
      __ z_ld(Z_F12, 128, Z_SP);
      __ z_ld(Z_F13, 136, Z_SP);
      __ z_ld(Z_F14, 144, Z_SP);
      __ z_ld(Z_F15, 152, Z_SP);
      BLOCK_COMMENT("} restore");

      //
      // Stack on exit from call_stub:
      //
      //     0       [C_FRAME]
      //             ...
      //
      // No call_stub frames left.
      //

      // All non-volatiles have been restored at this point!!

      //------------------------------------------------------------------------
      // The following code makes some assumptions on the T_<type> enum values.
      // The enum is defined in globalDefinitions.hpp.
      // The validity of the assumptions is tested as far as possible.
      //   The assigned values should not be shuffled
      //   T_BOOLEAN==4    - lowest used enum value
      //   T_NARROWOOP==16 - largest used enum value
      //------------------------------------------------------------------------
      BLOCK_COMMENT("process result {");
      Label firstHandler;
      int   handlerLen= 8;
#ifdef ASSERT
      char  assertMsg[] = "check BasicType definition in globalDefinitions.hpp";
      __ z_chi(r_arg_result_type, T_BOOLEAN);
      __ asm_assert_low(assertMsg, 0x0234);
      __ z_chi(r_arg_result_type, T_NARROWOOP);
      __ asm_assert_high(assertMsg, 0x0235);
#endif
      __ add2reg(r_arg_result_type, -T_BOOLEAN);          // Remove offset.
      __ z_larl(Z_R1, firstHandler);                      // location of first handler
      __ z_sllg(r_arg_result_type, r_arg_result_type, 3); // Each handler is 8 bytes long.
      __ z_bc(MacroAssembler::bcondAlways, 0, r_arg_result_type, Z_R1);

      __ align(handlerLen);
      __ bind(firstHandler);
      // T_BOOLEAN:
        guarantee(T_BOOLEAN == 4, "check BasicType definition in globalDefinitions.hpp");
        __ z_st(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_CHAR:
        guarantee(T_CHAR == T_BOOLEAN+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_st(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_FLOAT:
        guarantee(T_FLOAT == T_CHAR+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_ste(Z_FRET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_DOUBLE:
        guarantee(T_DOUBLE == T_FLOAT+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_std(Z_FRET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_BYTE:
        guarantee(T_BYTE == T_DOUBLE+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_st(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_SHORT:
        guarantee(T_SHORT == T_BYTE+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_st(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_INT:
        guarantee(T_INT == T_SHORT+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_st(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_LONG:
        guarantee(T_LONG == T_INT+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_stg(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_OBJECT:
        guarantee(T_OBJECT == T_LONG+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_stg(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_ARRAY:
        guarantee(T_ARRAY == T_OBJECT+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_stg(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_VOID:
        guarantee(T_VOID == T_ARRAY+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_stg(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_ADDRESS:
        guarantee(T_ADDRESS == T_VOID+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_stg(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      // T_NARROWOOP:
        guarantee(T_NARROWOOP == T_ADDRESS+1, "check BasicType definition in globalDefinitions.hpp");
        __ z_st(Z_RET, 0, r_arg_result_addr);
        __ z_br(Z_R14); // Return to caller.
        __ align(handlerLen);
      BLOCK_COMMENT("} process result");
    }
    return start;
  }

  // Return point for a Java call if there's an exception thrown in
  // Java code. The exception is caught and transformed into a
  // pending exception stored in JavaThread that can be tested from
  // within the VM.
  address generate_catch_exception() {
    StubCodeMark mark(this, "StubRoutines", "catch_exception");

    address start = __ pc();

    //
    // Registers alive
    //
    //   Z_thread
    //   Z_ARG1 - address of pending exception
    //   Z_ARG2 - return address in call stub
    //

    const Register exception_file = Z_R0;
    const Register exception_line = Z_R1;

    __ load_const_optimized(exception_file, (void*)__FILE__);
    __ load_const_optimized(exception_line, (void*)__LINE__);

    __ z_stg(Z_ARG1, thread_(pending_exception));
    // Store into `char *'.
    __ z_stg(exception_file, thread_(exception_file));
    // Store into `int'.
    __ z_st(exception_line, thread_(exception_line));

    // Complete return to VM.
    assert(StubRoutines::_call_stub_return_address != NULL, "must have been generated before");

    // Continue in call stub.
    __ z_br(Z_ARG2);

    return start;
  }

  // Continuation point for runtime calls returning with a pending
  // exception. The pending exception check happened in the runtime
  // or native call stub. The pending exception in Thread is
  // converted into a Java-level exception.
  //
  // Read:
  //   Z_R14: pc the runtime library callee wants to return to.
  //   Since the exception occurred in the callee, the return pc
  //   from the point of view of Java is the exception pc.
  //
  // Invalidate:
  //   Volatile registers (except below).
  //
  // Update:
  //   Z_ARG1: exception
  //   (Z_R14 is unchanged and is live out).
  //
  address generate_forward_exception() {
    StubCodeMark mark(this, "StubRoutines", "forward_exception");
    address start = __ pc();

    #define pending_exception_offset in_bytes(Thread::pending_exception_offset())
#ifdef ASSERT
    // Get pending exception oop.
    __ z_lg(Z_ARG1, pending_exception_offset, Z_thread);

    // Make sure that this code is only executed if there is a pending exception.
    {
      Label L;
      __ z_ltgr(Z_ARG1, Z_ARG1);
      __ z_brne(L);
      __ stop("StubRoutines::forward exception: no pending exception (1)");
      __ bind(L);
    }

    __ verify_oop(Z_ARG1, "StubRoutines::forward exception: not an oop");
#endif

    __ z_lgr(Z_ARG2, Z_R14); // Copy exception pc into Z_ARG2.
    __ save_return_pc();
    __ push_frame_abi160(0);
    // Find exception handler.
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address),
                    Z_thread,
                    Z_ARG2);
    // Copy handler's address.
    __ z_lgr(Z_R1, Z_RET);
    __ pop_frame();
    __ restore_return_pc();

    // Set up the arguments for the exception handler:
    // - Z_ARG1: exception oop
    // - Z_ARG2: exception pc

    // Load pending exception oop.
    __ z_lg(Z_ARG1, pending_exception_offset, Z_thread);

    // The exception pc is the return address in the caller,
    // must load it into Z_ARG2
    __ z_lgr(Z_ARG2, Z_R14);

#ifdef ASSERT
    // Make sure exception is set.
    { Label L;
      __ z_ltgr(Z_ARG1, Z_ARG1);
      __ z_brne(L);
      __ stop("StubRoutines::forward exception: no pending exception (2)");
      __ bind(L);
    }
#endif
    // Clear the pending exception.
    __ clear_mem(Address(Z_thread, pending_exception_offset), sizeof(void *));
    // Jump to exception handler
    __ z_br(Z_R1 /*handler address*/);

    return start;

    #undef pending_exception_offset
  }

  // Continuation point for throwing of implicit exceptions that are
  // not handled in the current activation. Fabricates an exception
  // oop and initiates normal exception dispatching in this
  // frame. Only callee-saved registers are preserved (through the
  // normal RegisterMap handling). If the compiler
  // needs all registers to be preserved between the fault point and
  // the exception handler then it must assume responsibility for that
  // in AbstractCompiler::continuation_for_implicit_null_exception or
  // continuation_for_implicit_division_by_zero_exception. All other
  // implicit exceptions (e.g., NullPointerException or
  // AbstractMethodError on entry) are either at call sites or
  // otherwise assume that stack unwinding will be initiated, so
  // caller saved registers were assumed volatile in the compiler.

  // Note that we generate only this stub into a RuntimeStub, because
  // it needs to be properly traversed and ignored during GC, so we
  // change the meaning of the "__" macro within this method.

  // Note: the routine set_pc_not_at_call_for_caller in
  // SharedRuntime.cpp requires that this code be generated into a
  // RuntimeStub.
#undef __
#define __ masm->

  address generate_throw_exception(const char* name, address runtime_entry,
                                   bool restore_saved_exception_pc,
                                   Register arg1 = noreg, Register arg2 = noreg) {
    int insts_size = 256;
    int locs_size  = 0;
    CodeBuffer      code(name, insts_size, locs_size);
    MacroAssembler* masm = new MacroAssembler(&code);
    int framesize_in_bytes;
    address start = __ pc();

    __ save_return_pc();
    framesize_in_bytes = __ push_frame_abi160(0);

    address frame_complete_pc = __ pc();
    if (restore_saved_exception_pc) {
      __ unimplemented("StubGenerator::throw_exception", 74);
    }

    // Note that we always have a runtime stub frame on the top of stack at this point.
    __ get_PC(Z_R1);
    __ set_last_Java_frame(/*sp*/Z_SP, /*pc*/Z_R1);

    // Do the call.
    BLOCK_COMMENT("call runtime_entry");
    __ call_VM_leaf(runtime_entry, Z_thread, arg1, arg2);

    __ reset_last_Java_frame();

#ifdef ASSERT
    // Make sure that this code is only executed if there is a pending exception.
    { Label L;
      __ z_lg(Z_R0,
                in_bytes(Thread::pending_exception_offset()),
                Z_thread);
      __ z_ltgr(Z_R0, Z_R0);
      __ z_brne(L);
      __ stop("StubRoutines::throw_exception: no pending exception");
      __ bind(L);
    }
#endif

    __ pop_frame();
    __ restore_return_pc();

    __ load_const_optimized(Z_R1, StubRoutines::forward_exception_entry());
    __ z_br(Z_R1);

    RuntimeStub* stub =
      RuntimeStub::new_runtime_stub(name, &code,
                                    frame_complete_pc - start,
                                    framesize_in_bytes/wordSize,
                                    NULL /*oop_maps*/, false);

    return stub->entry_point();
  }

#undef __
#ifdef PRODUCT
#define __ _masm->
#else
#define __ (Verbose ? (_masm->block_comment(FILE_AND_LINE),_masm):_masm)->
#endif

  //----------------------------------------------------------------------
  // The following routine generates a subroutine to throw an asynchronous
  // UnknownError when an unsafe access gets a fault that could not be
  // reasonably prevented by the programmer. (Example: SIGBUS/OBJERR.)
  //
  // Arguments:
  //   trapping PC: ??
  //
  // Results:
  //   Posts an asynchronous exception, skips the trapping instruction.
  //
  address generate_handler_for_unsafe_access() {
    StubCodeMark mark(this, "StubRoutines", "handler_for_unsafe_access");
    {
      address start = __ pc();
      __ unimplemented("StubRoutines::handler_for_unsafe_access", 86);
      return start;
    }
  }

  // Support for uint StubRoutine::zarch::partial_subtype_check(Klass
  // sub, Klass super);
  //
  // Arguments:
  //   ret  : Z_RET, returned
  //   sub  : Z_ARG2, argument, not changed
  //   super: Z_ARG3, argument, not changed
  //
  //   raddr: Z_R14, blown by call
  //
  address generate_partial_subtype_check() {
    StubCodeMark mark(this, "StubRoutines", "partial_subtype_check");
    Label miss;

    address start = __ pc();

    const Register Rsubklass   = Z_ARG2; // subklass
    const Register Rsuperklass = Z_ARG3; // superklass

    // No args, but tmp registers that are killed.
    const Register Rlength     = Z_ARG4; // cache array length
    const Register Rarray_ptr  = Z_ARG5; // Current value from cache array.

    if (UseCompressedOops) {
      assert(Universe::heap() != NULL, "java heap must be initialized to generate partial_subtype_check stub");
    }

    // Always take the slow path (see SPARC).
    __ check_klass_subtype_slow_path(Rsubklass, Rsuperklass,
                                     Rarray_ptr, Rlength, NULL, &miss);

    // Match falls through here.
    __ clear_reg(Z_RET);               // Zero indicates a match. Set EQ flag in CC.
    __ z_br(Z_R14);

    __ BIND(miss);
    __ load_const_optimized(Z_RET, 1); // One indicates a miss.
    __ z_ltgr(Z_RET, Z_RET);           // Set NE flag in CR.
    __ z_br(Z_R14);

    return start;
  }

  // Return address of code to be called from code generated by
  // MacroAssembler::verify_oop.
  //
  // Don't generate, rather use C++ code.
  address generate_verify_oop_subroutine() {
    // Don't generate a StubCodeMark, because no code is generated!
    // Generating the mark triggers notifying the oprofile jvmti agent
    // about the dynamic code generation, but the stub without
    // code (code_size == 0) confuses opjitconv
    // StubCodeMark mark(this, "StubRoutines", "verify_oop_stub");

    address start = 0;
    return start;
  }

  // Generate pre-write barrier for array.
  //
  // Input:
  //    addr  - register containing starting address
  //    count - register containing element count
  //
  // The input registers are overwritten.
  void gen_write_ref_array_pre_barrier(Register addr, Register count, bool dest_uninitialized) {

    BarrierSet* const bs = Universe::heap()->barrier_set();
    switch (bs->kind()) {
      case BarrierSet::G1SATBCTLogging:
        // With G1, don't generate the call if we statically know that the target in uninitialized.
        if (!dest_uninitialized) {
          // Is marking active?
          Label filtered;
          Register Rtmp1 = Z_R0;
          const int active_offset = in_bytes(JavaThread::satb_mark_queue_offset() +
                                             SATBMarkQueue::byte_offset_of_active());
          if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
            __ load_and_test_int(Rtmp1, Address(Z_thread, active_offset));
          } else {
            guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
            __ load_and_test_byte(Rtmp1, Address(Z_thread, active_offset));
          }
          __ z_bre(filtered); // Activity indicator is zero, so there is no marking going on currently.

          // __ push_frame_abi160(0);
          (void) RegisterSaver::save_live_registers(_masm, RegisterSaver::arg_registers);
          __ call_VM_leaf(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_pre), addr, count);
          (void) RegisterSaver::restore_live_registers(_masm, RegisterSaver::arg_registers);
          // __ pop_frame();

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

  // Generate post-write barrier for array.
  //
  // Input:
  //    addr  - register containing starting address
  //    count - register containing element count
  //
  // The input registers are overwritten.
  void gen_write_ref_array_post_barrier(Register addr, Register count, bool branchToEnd) {
    BarrierSet* const bs = Universe::heap()->barrier_set();
    switch (bs->kind()) {
      case BarrierSet::G1SATBCTLogging:
        {
          if (branchToEnd) {
            // __ push_frame_abi160(0);
            (void) RegisterSaver::save_live_registers(_masm, RegisterSaver::arg_registers);
            __ call_VM_leaf(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_post), addr, count);
            (void) RegisterSaver::restore_live_registers(_masm, RegisterSaver::arg_registers);
            // __ pop_frame();
          } else {
            // Tail call: call c and return to stub caller.
            address entry_point = CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_post);
            if (Z_ARG1 != addr) __ z_lgr(Z_ARG1, addr);
            if (Z_ARG2 != count) __ z_lgr(Z_ARG2, count);
            __ load_const(Z_R1, entry_point);
            __ z_br(Z_R1); // Branch without linking, callee will return to stub caller.
          }
        }
        break;
      case BarrierSet::CardTableForRS:
      case BarrierSet::CardTableExtension:
        // These cases formerly known as
        //   void array_store_check(Register addr, Register count, bool branchToEnd).
        {
          NearLabel doXC, done;
          CardTableModRefBS* ct = (CardTableModRefBS*)bs;
          assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");
          assert_different_registers(Z_R0, Z_R1, addr, count);

          // Nothing to do if count <= 0.
          if (branchToEnd) {
            __ compare64_and_branch(count, (intptr_t) 0, Assembler::bcondNotHigh, done);
          } else {
            __ z_ltgr(count, count);
            __ z_bcr(Assembler::bcondNotPositive, Z_R14);
          }

          // Note: We can't combine the shifts. We could lose a carry
          // from calculating the array end address.
          // count = (count-1)*BytesPerHeapOop + addr
          // Count holds addr of last oop in array then.
          __ z_sllg(count, count, LogBytesPerHeapOop);
          __ add2reg_with_index(count, -BytesPerHeapOop, count, addr);

          // Get base address of card table.
          __ load_const_optimized(Z_R1, (address)ct->byte_map_base);

          // count = (count>>shift) - (addr>>shift)
          __ z_srlg(addr,  addr,  CardTableModRefBS::card_shift);
          __ z_srlg(count, count, CardTableModRefBS::card_shift);

          // Prefetch first elements of card table for update.
          if (VM_Version::has_Prefetch()) {
            __ z_pfd(0x02, 0, addr, Z_R1);
          }

          // Special case: clear just one byte.
          __ clear_reg(Z_R0, true, false);  // Used for doOneByte.
          __ z_sgr(count, addr);            // Count = n-1 now, CC used for brc below.
          __ z_stc(Z_R0, 0, addr, Z_R1);    // Must preserve CC from z_sgr.
          if (branchToEnd) {
            __ z_brz(done);
          } else {
            __ z_bcr(Assembler::bcondZero, Z_R14);
          }

          __ z_cghi(count, 255);
          __ z_brnh(doXC);

          // MVCLE: clear a long area.
          // Start addr of card table range = base + addr.
          // # bytes in    card table range = (count + 1)
          __ add2reg_with_index(Z_R0, 0, Z_R1, addr);
          __ add2reg(Z_R1, 1, count);

          // dirty hack:
          // There are just two callers. Both pass
          // count in Z_ARG3 = Z_R4
          // addr  in Z_ARG2 = Z_R3
          // ==> use Z_ARG2 as src len reg = 0
          //         Z_ARG1 as src addr (ignored)
          assert(count == Z_ARG3, "count: unexpected register number");
          assert(addr  == Z_ARG2, "addr:  unexpected register number");
          __ clear_reg(Z_ARG2, true, false);

          __ MacroAssembler::move_long_ext(Z_R0, Z_ARG1, 0);

          if (branchToEnd) {
            __ z_bru(done);
          } else {
            __ z_bcr(Assembler::bcondAlways, Z_R14);
          }

          // XC: clear a short area.
          Label XC_template; // Instr template, never exec directly!
          __ bind(XC_template);
          __ z_xc(0, 0, addr, 0, addr);

          __ bind(doXC);
          // start addr of card table range = base + addr
          // end   addr of card table range = base + addr + count
          __ add2reg_with_index(addr, 0, Z_R1, addr);

          if (VM_Version::has_ExecuteExtensions()) {
            __ z_exrl(count, XC_template);   // Execute XC with var. len.
          } else {
            __ z_larl(Z_R1, XC_template);
            __ z_ex(count, 0, Z_R0, Z_R1);   // Execute XC with var. len.
          }
          if (!branchToEnd) {
            __ z_br(Z_R14);
          }

          __ bind(done);
        }
        break;
      case BarrierSet::ModRef:
        if (!branchToEnd) { __ z_br(Z_R14); }
        break;
      default:
        ShouldNotReachHere();
    }
  }


  // This is to test that the count register contains a positive int value.
  // Required because C2 does not respect int to long conversion for stub calls.
  void assert_positive_int(Register count) {
#ifdef ASSERT
    __ z_srag(Z_R0, count, 31);  // Just leave the sign (must be zero) in Z_R0.
    __ asm_assert_eq("missing zero extend", 0xAFFE);
#endif
  }

  //  Generate overlap test for array copy stubs.
  //  If no actual overlap is detected, control is transferred to the
  //  "normal" copy stub (entry address passed in disjoint_copy_target).
  //  Otherwise, execution continues with the code generated by the
  //  caller of array_overlap_test.
  //
  //  Input:
  //    Z_ARG1    - from
  //    Z_ARG2    - to
  //    Z_ARG3    - element count
  void array_overlap_test(address disjoint_copy_target, int log2_elem_size) {
    __ MacroAssembler::compare_and_branch_optimized(Z_ARG2, Z_ARG1, Assembler::bcondNotHigh,
                                                    disjoint_copy_target, /*len64=*/true, /*has_sign=*/false);

    Register index = Z_ARG3;
    if (log2_elem_size > 0) {
      __ z_sllg(Z_R1, Z_ARG3, log2_elem_size);  // byte count
      index = Z_R1;
    }
    __ add2reg_with_index(Z_R1, 0, index, Z_ARG1);  // First byte after "from" range.

    __ MacroAssembler::compare_and_branch_optimized(Z_R1, Z_ARG2, Assembler::bcondNotHigh,
                                                    disjoint_copy_target, /*len64=*/true, /*has_sign=*/false);

    // Destructive overlap: let caller generate code for that.
  }

  //  Generate stub for disjoint array copy. If "aligned" is true, the
  //  "from" and "to" addresses are assumed to be heapword aligned.
  //
  //  Arguments for generated stub:
  //      from:  Z_ARG1
  //      to:    Z_ARG2
  //      count: Z_ARG3 treated as signed
  void generate_disjoint_copy(bool aligned, int element_size,
                              bool branchToEnd,
                              bool restoreArgs) {
    // This is the zarch specific stub generator for general array copy tasks.
    // It has the following prereqs and features:
    //
    // - No destructive overlap allowed (else unpredictable results).
    // - Destructive overlap does not exist if the leftmost byte of the target
    //   does not coincide with any of the source bytes (except the leftmost).
    //
    //   Register usage upon entry:
    //      Z_ARG1 == Z_R2 :   address of source array
    //      Z_ARG2 == Z_R3 :   address of target array
    //      Z_ARG3 == Z_R4 :   length of operands (# of elements on entry)
    //
    // Register usage within the generator:
    // - Z_R0 and Z_R1 are KILLed by the stub routine (target addr/len).
    //                 Used as pair register operand in complex moves, scratch registers anyway.
    // - Z_R5 is KILLed by the stub routine (source register pair addr/len) (even/odd reg).
    //                  Same as R0/R1, but no scratch register.
    // - Z_ARG1, Z_ARG2, Z_ARG3 are USEd but preserved by the stub routine,
    //                          but they might get temporarily overwritten.

    Register  save_reg    = Z_ARG4;   // (= Z_R5), holds original target operand address for restore.

    {
      Register   llen_reg = Z_R1;     // Holds left operand len (odd reg).
      Register  laddr_reg = Z_R0;     // Holds left operand addr (even reg), overlaps with data_reg.
      Register   rlen_reg = Z_R5;     // Holds right operand len (odd reg), overlaps with save_reg.
      Register  raddr_reg = Z_R4;     // Holds right operand addr (even reg), overlaps with len_reg.

      Register   data_reg = Z_R0;     // Holds copied data chunk in alignment process and copy loop.
      Register    len_reg = Z_ARG3;   // Holds operand len (#elements at entry, #bytes shortly after).
      Register    dst_reg = Z_ARG2;   // Holds left (target)  operand addr.
      Register    src_reg = Z_ARG1;   // Holds right (source) operand addr.

      Label     doMVCLOOP, doMVCLOOPcount, doMVCLOOPiterate;
      Label     doMVCUnrolled;
      NearLabel doMVC,  doMVCgeneral, done;
      Label     MVC_template;
      address   pcMVCblock_b, pcMVCblock_e;

      bool      usedMVCLE       = true;
      bool      usedMVCLOOP     = true;
      bool      usedMVCUnrolled = false;
      bool      usedMVC         = false;
      bool      usedMVCgeneral  = false;

      int       stride;
      Register  stride_reg;
      Register  ix_reg;

      assert((element_size<=256) && (256%element_size == 0), "element size must be <= 256, power of 2");
      unsigned int log2_size = exact_log2(element_size);

      switch (element_size) {
        case 1:  BLOCK_COMMENT("ARRAYCOPY DISJOINT byte  {"); break;
        case 2:  BLOCK_COMMENT("ARRAYCOPY DISJOINT short {"); break;
        case 4:  BLOCK_COMMENT("ARRAYCOPY DISJOINT int   {"); break;
        case 8:  BLOCK_COMMENT("ARRAYCOPY DISJOINT long  {"); break;
        default: BLOCK_COMMENT("ARRAYCOPY DISJOINT       {"); break;
      }

      assert_positive_int(len_reg);

      BLOCK_COMMENT("preparation {");

      // No copying if len <= 0.
      if (branchToEnd) {
        __ compare64_and_branch(len_reg, (intptr_t) 0, Assembler::bcondNotHigh, done);
      } else {
        if (VM_Version::has_CompareBranch()) {
          __ z_cgib(len_reg, 0, Assembler::bcondNotHigh, 0, Z_R14);
        } else {
          __ z_ltgr(len_reg, len_reg);
          __ z_bcr(Assembler::bcondNotPositive, Z_R14);
        }
      }

      // Prefetch just one cache line. Speculative opt for short arrays.
      // Do not use Z_R1 in prefetch. Is undefined here.
      if (VM_Version::has_Prefetch()) {
        __ z_pfd(0x01, 0, Z_R0, src_reg); // Fetch access.
        __ z_pfd(0x02, 0, Z_R0, dst_reg); // Store access.
      }

      BLOCK_COMMENT("} preparation");

      // Save args only if really needed.
      // Keep len test local to branch. Is generated only once.

      BLOCK_COMMENT("mode selection {");

      // Special handling for arrays with only a few elements.
      // Nothing fancy: just an executed MVC.
      if (log2_size > 0) {
        __ z_sllg(Z_R1, len_reg, log2_size); // Remember #bytes in Z_R1.
      }
      if (element_size != 8) {
        __ z_cghi(len_reg, 256/element_size);
        __ z_brnh(doMVC);
        usedMVC = true;
      }
      if (element_size == 8) { // Long and oop arrays are always aligned.
        __ z_cghi(len_reg, 256/element_size);
        __ z_brnh(doMVCUnrolled);
        usedMVCUnrolled = true;
      }

      // Prefetch another cache line. We, for sure, have more than one line to copy.
      if (VM_Version::has_Prefetch()) {
        __ z_pfd(0x01, 256, Z_R0, src_reg); // Fetch access.
        __ z_pfd(0x02, 256, Z_R0, dst_reg); // Store access.
      }

      if (restoreArgs) {
        // Remember entry value of ARG2 to restore all arguments later from that knowledge.
        __ z_lgr(save_reg, dst_reg);
      }

      __ z_cghi(len_reg, 4096/element_size);
      if (log2_size == 0) {
        __ z_lgr(Z_R1, len_reg); // Init Z_R1 with #bytes
      }
      __ z_brnh(doMVCLOOP);

      // Fall through to MVCLE case.

      BLOCK_COMMENT("} mode selection");

      // MVCLE: for long arrays
      //   DW aligned: Best performance for sizes > 4kBytes.
      //   unaligned:  Least complex for sizes > 256 bytes.
      if (usedMVCLE) {
        BLOCK_COMMENT("mode MVCLE {");

        // Setup registers for mvcle.
        //__ z_lgr(llen_reg, len_reg);// r1 <- r4  #bytes already in Z_R1, aka llen_reg.
        __ z_lgr(laddr_reg, dst_reg); // r0 <- r3
        __ z_lgr(raddr_reg, src_reg); // r4 <- r2
        __ z_lgr(rlen_reg, llen_reg); // r5 <- r1

        __ MacroAssembler::move_long_ext(laddr_reg, raddr_reg, 0xb0);    // special: bypass cache
        // __ MacroAssembler::move_long_ext(laddr_reg, raddr_reg, 0xb8); // special: Hold data in cache.
        // __ MacroAssembler::move_long_ext(laddr_reg, raddr_reg, 0);

        if (restoreArgs) {
          // MVCLE updates the source (Z_R4,Z_R5) and target (Z_R0,Z_R1) register pairs.
          // Dst_reg (Z_ARG2) and src_reg (Z_ARG1) are left untouched. No restore required.
          // Len_reg (Z_ARG3) is destroyed and must be restored.
          __ z_slgr(laddr_reg, dst_reg);    // copied #bytes
          if (log2_size > 0) {
            __ z_srag(Z_ARG3, laddr_reg, log2_size); // Convert back to #elements.
          } else {
            __ z_lgr(Z_ARG3, laddr_reg);
          }
        }
        if (branchToEnd) {
          __ z_bru(done);
        } else {
          __ z_br(Z_R14);
        }
        BLOCK_COMMENT("} mode MVCLE");
      }
      // No fallthru possible here.

      //  MVCUnrolled: for short, aligned arrays.

      if (usedMVCUnrolled) {
        BLOCK_COMMENT("mode MVC unrolled {");
        stride = 8;

        // Generate unrolled MVC instructions.
        for (int ii = 32; ii > 1; ii--) {
          __ z_mvc(0, ii * stride-1, dst_reg, 0, src_reg); // ii*8 byte copy
          if (branchToEnd) {
            __ z_bru(done);
          } else {
            __ z_br(Z_R14);
          }
        }

        pcMVCblock_b = __ pc();
        __ z_mvc(0, 1 * stride-1, dst_reg, 0, src_reg); // 8 byte copy
        if (branchToEnd) {
          __ z_bru(done);
        } else {
          __ z_br(Z_R14);
        }

        pcMVCblock_e = __ pc();
        Label MVC_ListEnd;
        __ bind(MVC_ListEnd);

        // This is an absolute fast path:
        // - Array len in bytes must be not greater than 256.
        // - Array len in bytes must be an integer mult of DW
        //   to save expensive handling of trailing bytes.
        // - Argument restore is not done,
        //   i.e. previous code must not alter arguments (this code doesn't either).

        __ bind(doMVCUnrolled);

        // Avoid mul, prefer shift where possible.
        // Combine shift right (for #DW) with shift left (for block size).
        // Set CC for zero test below (asm_assert).
        // Note: #bytes comes in Z_R1, #DW in len_reg.
        unsigned int MVCblocksize    = pcMVCblock_e - pcMVCblock_b;
        unsigned int logMVCblocksize = 0xffffffffU; // Pacify compiler ("used uninitialized" warning).

        if (log2_size > 0) { // Len was scaled into Z_R1.
          switch (MVCblocksize) {

            case  8: logMVCblocksize = 3;
                     __ z_ltgr(Z_R0, Z_R1); // #bytes is index
                     break;                 // reasonable size, use shift

            case 16: logMVCblocksize = 4;
                     __ z_slag(Z_R0, Z_R1, logMVCblocksize-log2_size);
                     break;                 // reasonable size, use shift

            default: logMVCblocksize = 0;
                     __ z_ltgr(Z_R0, len_reg); // #DW for mul
                     break;                 // all other sizes: use mul
          }
        } else {
          guarantee(log2_size, "doMVCUnrolled: only for DW entities");
        }

        // This test (and branch) is redundant. Previous code makes sure that
        //  - element count > 0
        //  - element size == 8.
        // Thus, len reg should never be zero here. We insert an asm_assert() here,
        // just to double-check and to be on the safe side.
        __ asm_assert(false, "zero len cannot occur", 99);

        __ z_larl(Z_R1, MVC_ListEnd);        // Get addr of last instr block.
        // Avoid mul, prefer shift where possible.
        if (logMVCblocksize == 0) {
          __ z_mghi(Z_R0, MVCblocksize);
        }
        __ z_slgr(Z_R1, Z_R0);
        __ z_br(Z_R1);
        BLOCK_COMMENT("} mode MVC unrolled");
      }
      // No fallthru possible here.

      // MVC execute template
      // Must always generate. Usage may be switched on below.
      // There is no suitable place after here to put the template.
      __ bind(MVC_template);
      __ z_mvc(0,0,dst_reg,0,src_reg);      // Instr template, never exec directly!


      // MVC Loop: for medium-sized arrays

      // Only for DW aligned arrays (src and dst).
      // #bytes to copy must be at least 256!!!
      // Non-aligned cases handled separately.
      stride     = 256;
      stride_reg = Z_R1;   // Holds #bytes when control arrives here.
      ix_reg     = Z_ARG3; // Alias for len_reg.


      if (usedMVCLOOP) {
        BLOCK_COMMENT("mode MVC loop {");
        __ bind(doMVCLOOP);

        __ z_lcgr(ix_reg, Z_R1);         // Ix runs from -(n-2)*stride to 1*stride (inclusive).
        __ z_llill(stride_reg, stride);
        __ add2reg(ix_reg, 2*stride);    // Thus: increment ix by 2*stride.

        __ bind(doMVCLOOPiterate);
          __ z_mvc(0, stride-1, dst_reg, 0, src_reg);
          __ add2reg(dst_reg, stride);
          __ add2reg(src_reg, stride);
          __ bind(doMVCLOOPcount);
          __ z_brxlg(ix_reg, stride_reg, doMVCLOOPiterate);

        // Don 't use add2reg() here, since we must set the condition code!
        __ z_aghi(ix_reg, -2*stride);       // Compensate incr from above: zero diff means "all copied".

        if (restoreArgs) {
          __ z_lcgr(Z_R1, ix_reg);          // Prepare ix_reg for copy loop, #bytes expected in Z_R1.
          __ z_brnz(doMVCgeneral);          // We're not done yet, ix_reg is not zero.

          // ARG1, ARG2, and ARG3 were altered by the code above, so restore them building on save_reg.
          __ z_slgr(dst_reg, save_reg);     // copied #bytes
          __ z_slgr(src_reg, dst_reg);      // = ARG1 (now restored)
          if (log2_size) {
            __ z_srag(Z_ARG3, dst_reg, log2_size); // Convert back to #elements to restore ARG3.
          } else {
            __ z_lgr(Z_ARG3, dst_reg);
          }
          __ z_lgr(Z_ARG2, save_reg);       // ARG2 now restored.

          if (branchToEnd) {
            __ z_bru(done);
          } else {
            __ z_br(Z_R14);
          }

        } else {
            if (branchToEnd) {
              __ z_brz(done);                        // CC set by aghi instr.
          } else {
              __ z_bcr(Assembler::bcondZero, Z_R14); // We're all done if zero.
            }

          __ z_lcgr(Z_R1, ix_reg);    // Prepare ix_reg for copy loop, #bytes expected in Z_R1.
          // __ z_bru(doMVCgeneral);  // fallthru
        }
        usedMVCgeneral = true;
        BLOCK_COMMENT("} mode MVC loop");
      }
      // Fallthru to doMVCgeneral

      // MVCgeneral: for short, unaligned arrays, after other copy operations

      // Somewhat expensive due to use of EX instruction, but simple.
      if (usedMVCgeneral) {
        BLOCK_COMMENT("mode MVC general {");
        __ bind(doMVCgeneral);

        __ add2reg(len_reg, -1, Z_R1);             // Get #bytes-1 for EXECUTE.
        if (VM_Version::has_ExecuteExtensions()) {
          __ z_exrl(len_reg, MVC_template);        // Execute MVC with variable length.
        } else {
          __ z_larl(Z_R1, MVC_template);           // Get addr of instr template.
          __ z_ex(len_reg, 0, Z_R0, Z_R1);         // Execute MVC with variable length.
        }                                          // penalty: 9 ticks

        if (restoreArgs) {
          // ARG1, ARG2, and ARG3 were altered by code executed before, so restore them building on save_reg
          __ z_slgr(dst_reg, save_reg);            // Copied #bytes without the "doMVCgeneral" chunk
          __ z_slgr(src_reg, dst_reg);             // = ARG1 (now restored), was not advanced for "doMVCgeneral" chunk
          __ add2reg_with_index(dst_reg, 1, len_reg, dst_reg); // Len of executed MVC was not accounted for, yet.
          if (log2_size) {
            __ z_srag(Z_ARG3, dst_reg, log2_size); // Convert back to #elements to restore ARG3
          } else {
             __ z_lgr(Z_ARG3, dst_reg);
          }
          __ z_lgr(Z_ARG2, save_reg);              // ARG2 now restored.
        }

        if (usedMVC) {
          if (branchToEnd) {
            __ z_bru(done);
          } else {
            __ z_br(Z_R14);
        }
        } else {
          if (!branchToEnd) __ z_br(Z_R14);
        }
        BLOCK_COMMENT("} mode MVC general");
      }
      // Fallthru possible if following block not generated.

      // MVC: for short, unaligned arrays

      // Somewhat expensive due to use of EX instruction, but simple. penalty: 9 ticks.
      // Differs from doMVCgeneral in reconstruction of ARG2, ARG3, and ARG4.
      if (usedMVC) {
        BLOCK_COMMENT("mode MVC {");
        __ bind(doMVC);

        // get #bytes-1 for EXECUTE
        if (log2_size) {
          __ add2reg(Z_R1, -1);                // Length was scaled into Z_R1.
        } else {
          __ add2reg(Z_R1, -1, len_reg);       // Length was not scaled.
        }

        if (VM_Version::has_ExecuteExtensions()) {
          __ z_exrl(Z_R1, MVC_template);       // Execute MVC with variable length.
        } else {
          __ z_lgr(Z_R0, Z_R5);                // Save ARG4, may be unnecessary.
          __ z_larl(Z_R5, MVC_template);       // Get addr of instr template.
          __ z_ex(Z_R1, 0, Z_R0, Z_R5);        // Execute MVC with variable length.
          __ z_lgr(Z_R5, Z_R0);                // Restore ARG4, may be unnecessary.
        }

        if (!branchToEnd) {
          __ z_br(Z_R14);
        }
        BLOCK_COMMENT("} mode MVC");
      }

      __ bind(done);

      switch (element_size) {
        case 1:  BLOCK_COMMENT("} ARRAYCOPY DISJOINT byte "); break;
        case 2:  BLOCK_COMMENT("} ARRAYCOPY DISJOINT short"); break;
        case 4:  BLOCK_COMMENT("} ARRAYCOPY DISJOINT int  "); break;
        case 8:  BLOCK_COMMENT("} ARRAYCOPY DISJOINT long "); break;
        default: BLOCK_COMMENT("} ARRAYCOPY DISJOINT      "); break;
      }
    }
  }

  // Generate stub for conjoint array copy. If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //   from:  Z_ARG1
  //   to:    Z_ARG2
  //   count: Z_ARG3 treated as signed
  void generate_conjoint_copy(bool aligned, int element_size, bool branchToEnd) {

    // This is the zarch specific stub generator for general array copy tasks.
    // It has the following prereqs and features:
    //
    // - Destructive overlap exists and is handled by reverse copy.
    // - Destructive overlap exists if the leftmost byte of the target
    //   does coincide with any of the source bytes (except the leftmost).
    // - Z_R0 and Z_R1 are KILLed by the stub routine (data and stride)
    // - Z_ARG1 and Z_ARG2 are USEd but preserved by the stub routine.
    // - Z_ARG3 is USED but preserved by the stub routine.
    // - Z_ARG4 is used as index register and is thus KILLed.
    //
    {
      Register stride_reg = Z_R1;     // Stride & compare value in loop (negative element_size).
      Register   data_reg = Z_R0;     // Holds value of currently processed element.
      Register     ix_reg = Z_ARG4;   // Holds byte index of currently processed element.
      Register    len_reg = Z_ARG3;   // Holds length (in #elements) of arrays.
      Register    dst_reg = Z_ARG2;   // Holds left  operand addr.
      Register    src_reg = Z_ARG1;   // Holds right operand addr.

      assert(256%element_size == 0, "Element size must be power of 2.");
      assert(element_size     <= 8, "Can't handle more than DW units.");

      switch (element_size) {
        case 1:  BLOCK_COMMENT("ARRAYCOPY CONJOINT byte  {"); break;
        case 2:  BLOCK_COMMENT("ARRAYCOPY CONJOINT short {"); break;
        case 4:  BLOCK_COMMENT("ARRAYCOPY CONJOINT int   {"); break;
        case 8:  BLOCK_COMMENT("ARRAYCOPY CONJOINT long  {"); break;
        default: BLOCK_COMMENT("ARRAYCOPY CONJOINT       {"); break;
      }

      assert_positive_int(len_reg);

      if (VM_Version::has_Prefetch()) {
        __ z_pfd(0x01, 0, Z_R0, src_reg); // Fetch access.
        __ z_pfd(0x02, 0, Z_R0, dst_reg); // Store access.
      }

      unsigned int log2_size = exact_log2(element_size);
      if (log2_size) {
        __ z_sllg(ix_reg, len_reg, log2_size);
      } else {
        __ z_lgr(ix_reg, len_reg);
      }

      // Optimize reverse copy loop.
      // Main loop copies DW units which may be unaligned. Unaligned access adds some penalty ticks.
      // Unaligned DW access (neither fetch nor store) is DW-atomic, but should be alignment-atomic.
      // Preceding the main loop, some bytes are copied to obtain a DW-multiple remaining length.

      Label countLoop1;
      Label copyLoop1;
      Label skipBY;
      Label skipHW;
      int   stride = -8;

      __ load_const_optimized(stride_reg, stride); // Prepare for DW copy loop.

      if (element_size == 8)    // Nothing to do here.
        __ z_bru(countLoop1);
      else {                    // Do not generate dead code.
        __ z_tmll(ix_reg, 7);   // Check the "odd" bits.
        __ z_bre(countLoop1);   // There are none, very good!
      }

      if (log2_size == 0) {     // Handle leftover Byte.
        __ z_tmll(ix_reg, 1);
        __ z_bre(skipBY);
        __ z_lb(data_reg,   -1, ix_reg, src_reg);
        __ z_stcy(data_reg, -1, ix_reg, dst_reg);
        __ add2reg(ix_reg, -1); // Decrement delayed to avoid AGI.
        __ bind(skipBY);
        // fallthru
      }
      if (log2_size <= 1) {     // Handle leftover HW.
        __ z_tmll(ix_reg, 2);
        __ z_bre(skipHW);
        __ z_lhy(data_reg,  -2, ix_reg, src_reg);
        __ z_sthy(data_reg, -2, ix_reg, dst_reg);
        __ add2reg(ix_reg, -2); // Decrement delayed to avoid AGI.
        __ bind(skipHW);
        __ z_tmll(ix_reg, 4);
        __ z_bre(countLoop1);
        // fallthru
      }
      if (log2_size <= 2) {     // There are just 4 bytes (left) that need to be copied.
        __ z_ly(data_reg,  -4, ix_reg, src_reg);
        __ z_sty(data_reg, -4, ix_reg, dst_reg);
        __ add2reg(ix_reg, -4); // Decrement delayed to avoid AGI.
        __ z_bru(countLoop1);
      }

      // Control can never get to here. Never! Never ever!
      __ z_illtrap(0x99);
      __ bind(copyLoop1);
      __ z_lg(data_reg,  0, ix_reg, src_reg);
      __ z_stg(data_reg, 0, ix_reg, dst_reg);
      __ bind(countLoop1);
      __ z_brxhg(ix_reg, stride_reg, copyLoop1);

      if (!branchToEnd)
        __ z_br(Z_R14);

      switch (element_size) {
        case 1:  BLOCK_COMMENT("} ARRAYCOPY CONJOINT byte "); break;
        case 2:  BLOCK_COMMENT("} ARRAYCOPY CONJOINT short"); break;
        case 4:  BLOCK_COMMENT("} ARRAYCOPY CONJOINT int  "); break;
        case 8:  BLOCK_COMMENT("} ARRAYCOPY CONJOINT long "); break;
        default: BLOCK_COMMENT("} ARRAYCOPY CONJOINT      "); break;
      }
    }
  }

  // Generate stub for disjoint byte copy. If "aligned" is true, the
  // "from" and "to" addresses are assumed to be heapword aligned.
  address generate_disjoint_byte_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);

    // This is the zarch specific stub generator for byte array copy.
    // Refer to generate_disjoint_copy for a list of prereqs and features:
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    generate_disjoint_copy(aligned, 1, false, false);
    return __ addr_at(start_off);
  }


  address generate_disjoint_short_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    // This is the zarch specific stub generator for short array copy.
    // Refer to generate_disjoint_copy for a list of prereqs and features:
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    generate_disjoint_copy(aligned, 2, false, false);
    return __ addr_at(start_off);
  }


  address generate_disjoint_int_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    // This is the zarch specific stub generator for int array copy.
    // Refer to generate_disjoint_copy for a list of prereqs and features:
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    generate_disjoint_copy(aligned, 4, false, false);
    return __ addr_at(start_off);
  }


  address generate_disjoint_long_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    // This is the zarch specific stub generator for long array copy.
    // Refer to generate_disjoint_copy for a list of prereqs and features:
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    generate_disjoint_copy(aligned, 8, false, false);
    return __ addr_at(start_off);
  }


  address generate_disjoint_oop_copy(bool aligned, const char * name, bool dest_uninitialized) {
    StubCodeMark mark(this, "StubRoutines", name);
    // This is the zarch specific stub generator for oop array copy.
    // Refer to generate_disjoint_copy for a list of prereqs and features.
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    unsigned int size      = UseCompressedOops ? 4 : 8;

    gen_write_ref_array_pre_barrier(Z_ARG2, Z_ARG3, dest_uninitialized);

    generate_disjoint_copy(aligned, size, true, true);

    gen_write_ref_array_post_barrier(Z_ARG2, Z_ARG3, false);

    return __ addr_at(start_off);
  }


  address generate_conjoint_byte_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    // This is the zarch specific stub generator for overlapping byte array copy.
    // Refer to generate_conjoint_copy for a list of prereqs and features:
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).
    address nooverlap_target = aligned ? StubRoutines::arrayof_jbyte_disjoint_arraycopy()
                                       : StubRoutines::jbyte_disjoint_arraycopy();

    array_overlap_test(nooverlap_target, 0); // Branch away to nooverlap_target if disjoint.
    generate_conjoint_copy(aligned, 1, false);

    return __ addr_at(start_off);
  }


  address generate_conjoint_short_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    // This is the zarch specific stub generator for overlapping short array copy.
    // Refer to generate_conjoint_copy for a list of prereqs and features:
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).
    address nooverlap_target = aligned ? StubRoutines::arrayof_jshort_disjoint_arraycopy()
                                       : StubRoutines::jshort_disjoint_arraycopy();

    array_overlap_test(nooverlap_target, 1); // Branch away to nooverlap_target if disjoint.
    generate_conjoint_copy(aligned, 2, false);

    return __ addr_at(start_off);
  }

  address generate_conjoint_int_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    // This is the zarch specific stub generator for overlapping int array copy.
    // Refer to generate_conjoint_copy for a list of prereqs and features:

    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).
    address nooverlap_target = aligned ? StubRoutines::arrayof_jint_disjoint_arraycopy()
                                       : StubRoutines::jint_disjoint_arraycopy();

    array_overlap_test(nooverlap_target, 2); // Branch away to nooverlap_target if disjoint.
    generate_conjoint_copy(aligned, 4, false);

    return __ addr_at(start_off);
  }

  address generate_conjoint_long_copy(bool aligned, const char * name) {
    StubCodeMark mark(this, "StubRoutines", name);
    // This is the zarch specific stub generator for overlapping long array copy.
    // Refer to generate_conjoint_copy for a list of prereqs and features:

    unsigned int start_off   = __ offset();  // Remember stub start address (is rtn value).
    address nooverlap_target = aligned ? StubRoutines::arrayof_jlong_disjoint_arraycopy()
                                       : StubRoutines::jlong_disjoint_arraycopy();

    array_overlap_test(nooverlap_target, 3); // Branch away to nooverlap_target if disjoint.
    generate_conjoint_copy(aligned, 8, false);

    return __ addr_at(start_off);
  }

  address generate_conjoint_oop_copy(bool aligned, const char * name, bool dest_uninitialized) {
    StubCodeMark mark(this, "StubRoutines", name);
    // This is the zarch specific stub generator for overlapping oop array copy.
    // Refer to generate_conjoint_copy for a list of prereqs and features.
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    unsigned int size      = UseCompressedOops ? 4 : 8;
    unsigned int shift     = UseCompressedOops ? 2 : 3;

    address nooverlap_target = aligned ? StubRoutines::arrayof_oop_disjoint_arraycopy(dest_uninitialized)
                                       : StubRoutines::oop_disjoint_arraycopy(dest_uninitialized);

    // Branch to disjoint_copy (if applicable) before pre_barrier to avoid double pre_barrier.
    array_overlap_test(nooverlap_target, shift);  // Branch away to nooverlap_target if disjoint.

    gen_write_ref_array_pre_barrier(Z_ARG2, Z_ARG3, dest_uninitialized);

    generate_conjoint_copy(aligned, size, true);  // Must preserve ARG2, ARG3.

    gen_write_ref_array_post_barrier(Z_ARG2, Z_ARG3, false);

    return __ addr_at(start_off);
  }


  void generate_arraycopy_stubs() {

    // Note: the disjoint stubs must be generated first, some of
    // the conjoint stubs use them.
    StubRoutines::_jbyte_disjoint_arraycopy      = generate_disjoint_byte_copy (false, "jbyte_disjoint_arraycopy");
    StubRoutines::_jshort_disjoint_arraycopy     = generate_disjoint_short_copy(false, "jshort_disjoint_arraycopy");
    StubRoutines::_jint_disjoint_arraycopy       = generate_disjoint_int_copy  (false, "jint_disjoint_arraycopy");
    StubRoutines::_jlong_disjoint_arraycopy      = generate_disjoint_long_copy (false, "jlong_disjoint_arraycopy");
    StubRoutines::_oop_disjoint_arraycopy        = generate_disjoint_oop_copy  (false, "oop_disjoint_arraycopy", false);
    StubRoutines::_oop_disjoint_arraycopy_uninit = generate_disjoint_oop_copy  (false, "oop_disjoint_arraycopy_uninit", true);

    StubRoutines::_arrayof_jbyte_disjoint_arraycopy      = generate_disjoint_byte_copy (true, "arrayof_jbyte_disjoint_arraycopy");
    StubRoutines::_arrayof_jshort_disjoint_arraycopy     = generate_disjoint_short_copy(true, "arrayof_jshort_disjoint_arraycopy");
    StubRoutines::_arrayof_jint_disjoint_arraycopy       = generate_disjoint_int_copy  (true, "arrayof_jint_disjoint_arraycopy");
    StubRoutines::_arrayof_jlong_disjoint_arraycopy      = generate_disjoint_long_copy (true, "arrayof_jlong_disjoint_arraycopy");
    StubRoutines::_arrayof_oop_disjoint_arraycopy        = generate_disjoint_oop_copy  (true, "arrayof_oop_disjoint_arraycopy", false);
    StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit = generate_disjoint_oop_copy  (true, "arrayof_oop_disjoint_arraycopy_uninit", true);

    StubRoutines::_jbyte_arraycopy           = generate_conjoint_byte_copy (false, "jbyte_arraycopy");
    StubRoutines::_jshort_arraycopy          = generate_conjoint_short_copy(false, "jshort_arraycopy");
    StubRoutines::_jint_arraycopy            = generate_conjoint_int_copy  (false, "jint_arraycopy");
    StubRoutines::_jlong_arraycopy           = generate_conjoint_long_copy (false, "jlong_arraycopy");
    StubRoutines::_oop_arraycopy             = generate_conjoint_oop_copy  (false, "oop_arraycopy", false);
    StubRoutines::_oop_arraycopy_uninit      = generate_conjoint_oop_copy  (false, "oop_arraycopy_uninit", true);

    StubRoutines::_arrayof_jbyte_arraycopy      = generate_conjoint_byte_copy (true, "arrayof_jbyte_arraycopy");
    StubRoutines::_arrayof_jshort_arraycopy     = generate_conjoint_short_copy(true, "arrayof_jshort_arraycopy");
    StubRoutines::_arrayof_jint_arraycopy       = generate_conjoint_int_copy  (true, "arrayof_jint_arraycopy");
    StubRoutines::_arrayof_jlong_arraycopy      = generate_conjoint_long_copy (true, "arrayof_jlong_arraycopy");
    StubRoutines::_arrayof_oop_arraycopy        = generate_conjoint_oop_copy  (true, "arrayof_oop_arraycopy", false);
    StubRoutines::_arrayof_oop_arraycopy_uninit = generate_conjoint_oop_copy  (true, "arrayof_oop_arraycopy_uninit", true);
  }

  void generate_safefetch(const char* name, int size, address* entry, address* fault_pc, address* continuation_pc) {

    // safefetch signatures:
    //   int      SafeFetch32(int*      adr, int      errValue);
    //   intptr_t SafeFetchN (intptr_t* adr, intptr_t errValue);
    //
    // arguments:
    //   Z_ARG1 = adr
    //   Z_ARG2 = errValue
    //
    // result:
    //   Z_RET  = *adr or errValue

    StubCodeMark mark(this, "StubRoutines", name);

    // entry point
    // Load *adr into Z_ARG2, may fault.
    *entry = *fault_pc = __ pc();
    switch (size) {
      case 4:
        // Sign extended int32_t.
        __ z_lgf(Z_ARG2, 0, Z_ARG1);
        break;
      case 8:
        // int64_t
        __ z_lg(Z_ARG2, 0, Z_ARG1);
        break;
      default:
        ShouldNotReachHere();
    }

    // Return errValue or *adr.
    *continuation_pc = __ pc();
    __ z_lgr(Z_RET, Z_ARG2);
    __ z_br(Z_R14);

  }

  // Call interface for AES_encryptBlock, AES_decryptBlock stubs.
  //
  //   Z_ARG1 - source data block. Ptr to leftmost byte to be processed.
  //   Z_ARG2 - destination data block. Ptr to leftmost byte to be stored.
  //            For in-place encryption/decryption, ARG1 and ARG2 can point
  //            to the same piece of storage.
  //   Z_ARG3 - Crypto key address (expanded key). The first n bits of
  //            the expanded key constitute the original AES-<n> key (see below).
  //
  //   Z_RET  - return value. First unprocessed byte offset in src buffer.
  //
  // Some remarks:
  //   The crypto key, as passed from the caller to these encryption stubs,
  //   is a so-called expanded key. It is derived from the original key
  //   by the Rijndael key schedule, see http://en.wikipedia.org/wiki/Rijndael_key_schedule
  //   With the expanded key, the cipher/decipher task is decomposed in
  //   multiple, less complex steps, called rounds. Sun SPARC and Intel
  //   processors obviously implement support for those less complex steps.
  //   z/Architecture provides instructions for full cipher/decipher complexity.
  //   Therefore, we need the original, not the expanded key here.
  //   Luckily, the first n bits of an AES-<n> expanded key are formed
  //   by the original key itself. That takes us out of trouble. :-)
  //   The key length (in bytes) relation is as follows:
  //     original    expanded   rounds  key bit     keylen
  //    key bytes   key bytes            length   in words
  //           16         176       11      128         44
  //           24         208       13      192         52
  //           32         240       15      256         60
  //
  // The crypto instructions used in the AES* stubs have some specific register requirements.
  //   Z_R0   holds the crypto function code. Please refer to the KM/KMC instruction
  //          description in the "z/Architecture Principles of Operation" manual for details.
  //   Z_R1   holds the parameter block address. The parameter block contains the cryptographic key
  //          (KM instruction) and the chaining value (KMC instruction).
  //   dst    must designate an even-numbered register, holding the address of the output message.
  //   src    must designate an even/odd register pair, holding the address/length of the original message

  // Helper function which generates code to
  //  - load the function code in register fCode (== Z_R0)
  //  - load the data block length (depends on cipher function) in register srclen if requested.
  //  - is_decipher switches between cipher/decipher function codes
  //  - set_len requests (if true) loading the data block length in register srclen
  void generate_load_AES_fCode(Register keylen, Register fCode, Register srclen, bool is_decipher) {

    BLOCK_COMMENT("Set fCode {"); {
      Label fCode_set;
      int   mode = is_decipher ? VM_Version::CipherMode::decipher : VM_Version::CipherMode::cipher;
      bool  identical_dataBlk_len =  (VM_Version::Cipher::_AES128_dataBlk == VM_Version::Cipher::_AES192_dataBlk)
                                  && (VM_Version::Cipher::_AES128_dataBlk == VM_Version::Cipher::_AES256_dataBlk);
      // Expanded key length is 44/52/60 * 4 bytes for AES-128/AES-192/AES-256.
      __ z_cghi(keylen, 52);
      __ z_lghi(fCode, VM_Version::Cipher::_AES256 + mode);
      if (!identical_dataBlk_len) {
        __ z_lghi(srclen, VM_Version::Cipher::_AES256_dataBlk);
      }
      __ z_brh(fCode_set);  // keyLen >  52: AES256

      __ z_lghi(fCode, VM_Version::Cipher::_AES192 + mode);
      if (!identical_dataBlk_len) {
        __ z_lghi(srclen, VM_Version::Cipher::_AES192_dataBlk);
      }
      __ z_bre(fCode_set);  // keyLen == 52: AES192

      __ z_lghi(fCode, VM_Version::Cipher::_AES128 + mode);
      if (!identical_dataBlk_len) {
        __ z_lghi(srclen, VM_Version::Cipher::_AES128_dataBlk);
      }
      // __ z_brl(fCode_set);  // keyLen <  52: AES128           // fallthru
      __ bind(fCode_set);
      if (identical_dataBlk_len) {
        __ z_lghi(srclen, VM_Version::Cipher::_AES128_dataBlk);
      }
    }
    BLOCK_COMMENT("} Set fCode");
  }

  // Push a parameter block for the cipher/decipher instruction on the stack.
  // NOTE:
  //   Before returning, the stub has to copy the chaining value from
  //   the parmBlk, where it was updated by the crypto instruction, back
  //   to the chaining value array the address of which was passed in the cv argument.
  //   As all the available registers are used and modified by KMC, we need to save
  //   the key length across the KMC instruction. We do so by spilling it to the stack,
  //   just preceding the parmBlk (at (parmBlk - 8)).
  void generate_push_parmBlk(Register keylen, Register fCode, Register parmBlk, Register key, Register cv, bool is_decipher) {
    const int AES_parmBlk_align    = 32;
    const int AES_parmBlk_addspace = AES_parmBlk_align; // Must be multiple of AES_parmblk_align.
    int       cv_len, key_len;
    int       mode = is_decipher ? VM_Version::CipherMode::decipher : VM_Version::CipherMode::cipher;
    Label     parmBlk_128, parmBlk_192, parmBlk_256, parmBlk_set;

    BLOCK_COMMENT("push parmBlk {");
    if (VM_Version::has_Crypto_AES()   ) { __ z_cghi(keylen, 52); }
    if (VM_Version::has_Crypto_AES256()) { __ z_brh(parmBlk_256); }  // keyLen >  52: AES256
    if (VM_Version::has_Crypto_AES192()) { __ z_bre(parmBlk_192); }  // keyLen == 52: AES192
    if (VM_Version::has_Crypto_AES128()) { __ z_brl(parmBlk_128); }  // keyLen <  52: AES128

    // Security net: requested AES function not available on this CPU.
    // NOTE:
    //   As of now (March 2015), this safety net is not required. JCE policy files limit the
    //   cryptographic strength of the keys used to 128 bit. If we have AES hardware support
    //   at all, we have at least AES-128.
    __ stop_static("AES key strength not supported by CPU. Use -XX:-UseAES as remedy.", 0);

    if (VM_Version::has_Crypto_AES128()) {
      __ bind(parmBlk_128);
      cv_len  = VM_Version::Cipher::_AES128_dataBlk;
      key_len = VM_Version::Cipher::_AES128_parmBlk_C - cv_len;
      __ z_lay(parmBlk, -(VM_Version::Cipher::_AES128_parmBlk_C+AES_parmBlk_align)+(AES_parmBlk_align-1), Z_SP);
      __ z_nill(parmBlk, (~(AES_parmBlk_align-1)) & 0xffff);  // align parameter block

      // Resize the frame to accommodate for the aligned parameter block and other stuff.
      // There is room for stuff in the range [parmBlk-AES_parmBlk_addspace, parmBlk).
      __ z_stg(keylen, -8, parmBlk);                   // Spill keylen for later use.
      __ z_stg(Z_SP,  -16, parmBlk);                   // Spill SP for easy revert.
      __ z_aghi(parmBlk, -AES_parmBlk_addspace);       // Additional space for keylen, etc..
      __ resize_frame_absolute(parmBlk, keylen, true); // Resize frame with parmBlk being the new SP.
      __ z_aghi(parmBlk,  AES_parmBlk_addspace);       // Restore parameter block address.

      __ z_mvc(0,      cv_len-1,  parmBlk, 0, cv);     // Copy cv.
      __ z_mvc(cv_len, key_len-1, parmBlk, 0, key);    // Copy key.
      __ z_lghi(fCode, VM_Version::Cipher::_AES128 + mode);
      if (VM_Version::has_Crypto_AES192() || VM_Version::has_Crypto_AES256()) {
        __ z_bru(parmBlk_set);  // Fallthru otherwise.
      }
    }

    if (VM_Version::has_Crypto_AES192()) {
      __ bind(parmBlk_192);
      cv_len  = VM_Version::Cipher::_AES192_dataBlk;
      key_len = VM_Version::Cipher::_AES192_parmBlk_C - cv_len;
      __ z_lay(parmBlk, -(VM_Version::Cipher::_AES192_parmBlk_C+AES_parmBlk_align)+(AES_parmBlk_align-1), Z_SP);
      __ z_nill(parmBlk, (~(AES_parmBlk_align-1)) & 0xffff);  // Align parameter block.

      // Resize the frame to accommodate for the aligned parameter block and other stuff.
      // There is room for stuff in the range [parmBlk-AES_parmBlk_addspace, parmBlk).
      __ z_stg(keylen, -8, parmBlk);                   // Spill keylen for later use.
      __ z_stg(Z_SP,  -16, parmBlk);                   // Spill SP for easy revert.
      __ z_aghi(parmBlk, -AES_parmBlk_addspace);       // Additional space for keylen, etc..
      __ resize_frame_absolute(parmBlk, keylen, true); // Resize frame with parmBlk being the new SP.
      __ z_aghi(parmBlk, AES_parmBlk_addspace);        // Restore parameter block address.

      __ z_mvc(0,      cv_len-1,  parmBlk, 0, cv);     // Copy cv.
      __ z_mvc(cv_len, key_len-1, parmBlk, 0, key);    // Copy key.
      __ z_lghi(fCode,    VM_Version::Cipher::_AES192 + mode);
      if (VM_Version::has_Crypto_AES256()) {
        __ z_bru(parmBlk_set);  // Fallthru otherwise.
      }
    }

    if (VM_Version::has_Crypto_AES256()) {
      __ bind(parmBlk_256);
      cv_len  = VM_Version::Cipher::_AES256_dataBlk;
      key_len = VM_Version::Cipher::_AES256_parmBlk_C - cv_len;
      __ z_lay(parmBlk, -(VM_Version::Cipher::_AES256_parmBlk_C+AES_parmBlk_align)+(AES_parmBlk_align-1), Z_SP);
      __ z_nill(parmBlk, (~(AES_parmBlk_align-1)) & 0xffff);  // Align parameter block.

      // Resize the frame to accommodate for the aligned parameter block and other stuff.
      // There is room for stuff in the range [parmBlk-AES_parmBlk_addspace, parmBlk).
      __ z_stg(keylen, -8, parmBlk);                   // Spill keylen for later use.
      __ z_stg(Z_SP,  -16, parmBlk);                   // Spill SP for easy revert.
      __ z_aghi(parmBlk, -AES_parmBlk_addspace);       // Additional space for keylen, etc..
      __ resize_frame_absolute(parmBlk, keylen, true); // Resize frame with parmBlk being the new SP.
      __ z_aghi(parmBlk,  AES_parmBlk_addspace);       // Restore parameter block address.

      __ z_mvc(0,      cv_len-1,  parmBlk, 0, cv);     // Copy cv.
      __ z_mvc(cv_len, key_len-1, parmBlk, 0, key);    // Copy key.
      __ z_lghi(fCode, VM_Version::Cipher::_AES256 + mode);
      // __ z_bru(parmBlk_set);  // fallthru
    }

    __ bind(parmBlk_set);
    BLOCK_COMMENT("} push parmBlk");
  }

  // Pop a parameter block from the stack. The chaining value portion of the parameter block
  // is copied back to the cv array as it is needed for subsequent cipher steps.
  // The keylen value as well as the original SP (before resizing) was pushed to the stack
  // when pushing the parameter block.
  void generate_pop_parmBlk(Register keylen, Register parmBlk, Register key, Register cv) {

    BLOCK_COMMENT("pop parmBlk {");
    bool identical_dataBlk_len =  (VM_Version::Cipher::_AES128_dataBlk == VM_Version::Cipher::_AES192_dataBlk) &&
                                  (VM_Version::Cipher::_AES128_dataBlk == VM_Version::Cipher::_AES256_dataBlk);
    if (identical_dataBlk_len) {
      int cv_len = VM_Version::Cipher::_AES128_dataBlk;
      __ z_mvc(0, cv_len-1, cv, 0, parmBlk);  // Copy cv.
    } else {
      int cv_len;
      Label parmBlk_128, parmBlk_192, parmBlk_256, parmBlk_set;
      __ z_lg(keylen, -8, parmBlk);  // restore keylen
      __ z_cghi(keylen, 52);
      if (VM_Version::has_Crypto_AES256()) __ z_brh(parmBlk_256);  // keyLen >  52: AES256
      if (VM_Version::has_Crypto_AES192()) __ z_bre(parmBlk_192);  // keyLen == 52: AES192
      // if (VM_Version::has_Crypto_AES128()) __ z_brl(parmBlk_128);  // keyLen <  52: AES128  // fallthru

      // Security net: there is no one here. If we would need it, we should have
      // fallen into it already when pushing the parameter block.
      if (VM_Version::has_Crypto_AES128()) {
        __ bind(parmBlk_128);
        cv_len = VM_Version::Cipher::_AES128_dataBlk;
        __ z_mvc(0, cv_len-1, cv, 0, parmBlk);  // Copy cv.
        if (VM_Version::has_Crypto_AES192() || VM_Version::has_Crypto_AES256()) {
          __ z_bru(parmBlk_set);
        }
      }

      if (VM_Version::has_Crypto_AES192()) {
        __ bind(parmBlk_192);
        cv_len = VM_Version::Cipher::_AES192_dataBlk;
        __ z_mvc(0, cv_len-1, cv, 0, parmBlk);  // Copy cv.
        if (VM_Version::has_Crypto_AES256()) {
          __ z_bru(parmBlk_set);
        }
      }

      if (VM_Version::has_Crypto_AES256()) {
        __ bind(parmBlk_256);
        cv_len = VM_Version::Cipher::_AES256_dataBlk;
        __ z_mvc(0, cv_len-1, cv, 0, parmBlk);  // Copy cv.
        // __ z_bru(parmBlk_set);  // fallthru
      }
      __ bind(parmBlk_set);
    }
    __ z_lg(Z_SP, -16, parmBlk); // Revert resize_frame_absolute.
    BLOCK_COMMENT("} pop parmBlk");
  }

  // Compute AES encrypt function.
  address generate_AES_encryptBlock(const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    Register       from    = Z_ARG1; // source byte array
    Register       to      = Z_ARG2; // destination byte array
    Register       key     = Z_ARG3; // expanded key array

    const Register keylen  = Z_R0;   // Temporarily (until fCode is set) holds the expanded key array length.
    const Register fCode   = Z_R0;   // crypto function code
    const Register parmBlk = Z_R1;   // parameter block address (points to crypto key)
    const Register src     = Z_ARG1; // is Z_R2
    const Register srclen  = Z_ARG2; // Overwrites destination address.
    const Register dst     = Z_ARG3; // Overwrites expanded key address.

    // Read key len of expanded key (in 4-byte words).
    __ z_lgf(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    // Copy arguments to registers as required by crypto instruction.
    __ z_lgr(parmBlk, key);          // crypto key (in T_INT array).
    // __ z_lgr(src, from);          // Copy not needed, src/from are identical.
    __ z_lgr(dst, to);               // Copy destination address to even register.

    // Construct function code in Z_R0, data block length in Z_ARG2.
    generate_load_AES_fCode(keylen, fCode, srclen, false);

    __ km(dst, src);          // Cipher the message.

    __ z_br(Z_R14);

    return __ addr_at(start_off);
  }

  // Compute AES decrypt function.
  address generate_AES_decryptBlock(const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    Register       from    = Z_ARG1; // source byte array
    Register       to      = Z_ARG2; // destination byte array
    Register       key     = Z_ARG3; // expanded key array, not preset at entry!!!

    const Register keylen  = Z_R0;   // Temporarily (until fCode is set) holds the expanded key array length.
    const Register fCode   = Z_R0;   // crypto function code
    const Register parmBlk = Z_R1;   // parameter block address (points to crypto key)
    const Register src     = Z_ARG1; // is Z_R2
    const Register srclen  = Z_ARG2; // Overwrites destination address.
    const Register dst     = Z_ARG3; // Overwrites key address.

    // Read key len of expanded key (in 4-byte words).
    __ z_lgf(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    // Copy arguments to registers as required by crypto instruction.
    __ z_lgr(parmBlk, key);     // Copy crypto key address.
    // __ z_lgr(src, from);     // Copy not needed, src/from are identical.
    __ z_lgr(dst, to);          // Copy destination address to even register.

    // Construct function code in Z_R0, data block length in Z_ARG2.
    generate_load_AES_fCode(keylen, fCode, srclen, true);

    __ km(dst, src);          // Cipher the message.

    __ z_br(Z_R14);

    return __ addr_at(start_off);
  }

  // These stubs receive the addresses of the cryptographic key and of the chaining value as two separate
  // arguments (registers "key" and "cv", respectively). The KMC instruction, on the other hand, requires
  // chaining value and key to be, in this sequence, adjacent in storage. Thus, we need to allocate some
  // thread-local working storage. Using heap memory incurs all the hassles of allocating/freeing.
  // Stack space, on the contrary, is deallocated automatically when we return from the stub to the caller.
  // *** WARNING ***
  // Please note that we do not formally allocate stack space, nor do we
  // update the stack pointer. Therefore, no function calls are allowed
  // and nobody else must use the stack range where the parameter block
  // is located.
  // We align the parameter block to the next available octoword.
  //
  // Compute chained AES encrypt function.
  address generate_cipherBlockChaining_AES_encrypt(const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    Register       from    = Z_ARG1; // source byte array (clear text)
    Register       to      = Z_ARG2; // destination byte array (ciphered)
    Register       key     = Z_ARG3; // expanded key array.
    Register       cv      = Z_ARG4; // chaining value
    const Register msglen  = Z_ARG5; // Total length of the msg to be encrypted. Value must be returned
                                     // in Z_RET upon completion of this stub. Is 32-bit integer.

    const Register keylen  = Z_R0;   // Expanded key length, as read from key array. Temp only.
    const Register fCode   = Z_R0;   // crypto function code
    const Register parmBlk = Z_R1;   // parameter block address (points to crypto key)
    const Register src     = Z_ARG1; // is Z_R2
    const Register srclen  = Z_ARG2; // Overwrites destination address.
    const Register dst     = Z_ARG3; // Overwrites key address.

    // Read key len of expanded key (in 4-byte words).
    __ z_lgf(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    // Construct parm block address in parmBlk (== Z_R1), copy cv and key to parm block.
    // Construct function code in Z_R0.
    generate_push_parmBlk(keylen, fCode, parmBlk, key, cv, false);

    // Prepare other registers for instruction.
    // __ z_lgr(src, from);     // Not needed, registers are the same.
    __ z_lgr(dst, to);
    __ z_llgfr(srclen, msglen); // We pass the offsets as ints, not as longs as required.

    __ kmc(dst, src);           // Cipher the message.

    generate_pop_parmBlk(keylen, parmBlk, key, cv);

    __ z_llgfr(Z_RET, msglen);  // We pass the offsets as ints, not as longs as required.
    __ z_br(Z_R14);

    return __ addr_at(start_off);
  }

  // Compute chained AES encrypt function.
  address generate_cipherBlockChaining_AES_decrypt(const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    Register       from    = Z_ARG1; // source byte array (ciphered)
    Register       to      = Z_ARG2; // destination byte array (clear text)
    Register       key     = Z_ARG3; // expanded key array, not preset at entry!!!
    Register       cv      = Z_ARG4; // chaining value
    const Register msglen  = Z_ARG5; // Total length of the msg to be encrypted. Value must be returned
                                     // in Z_RET upon completion of this stub.

    const Register keylen  = Z_R0;   // Expanded key length, as read from key array. Temp only.
    const Register fCode   = Z_R0;   // crypto function code
    const Register parmBlk = Z_R1;   // parameter block address (points to crypto key)
    const Register src     = Z_ARG1; // is Z_R2
    const Register srclen  = Z_ARG2; // Overwrites destination address.
    const Register dst     = Z_ARG3; // Overwrites key address.

    // Read key len of expanded key (in 4-byte words).
    __ z_lgf(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    // Construct parm block address in parmBlk (== Z_R1), copy cv and key to parm block.
    // Construct function code in Z_R0.
    generate_push_parmBlk(keylen, fCode, parmBlk, key, cv, true);

    // Prepare other registers for instruction.
    // __ z_lgr(src, from);     // Not needed, registers are the same.
    __ z_lgr(dst, to);
    __ z_llgfr(srclen, msglen); // We pass the offsets as ints, not as longs as required.

    __ kmc(dst, src);           // Decipher the message.

    generate_pop_parmBlk(keylen, parmBlk, key, cv);

    __ z_llgfr(Z_RET, msglen);  // We pass the offsets as ints, not as longs as required.
    __ z_br(Z_R14);

    return __ addr_at(start_off);
  }


  // Call interface for all SHA* stubs.
  //
  //   Z_ARG1 - source data block. Ptr to leftmost byte to be processed.
  //   Z_ARG2 - current SHA state. Ptr to state area. This area serves as
  //            parameter block as required by the crypto instruction.
  //   Z_ARG3 - current byte offset in source data block.
  //   Z_ARG4 - last byte offset in source data block.
  //            (Z_ARG4 - Z_ARG3) gives the #bytes remaining to be processed.
  //
  //   Z_RET  - return value. First unprocessed byte offset in src buffer.
  //
  //   A few notes on the call interface:
  //    - All stubs, whether they are single-block or multi-block, are assumed to
  //      digest an integer multiple of the data block length of data. All data
  //      blocks are digested using the intermediate message digest (KIMD) instruction.
  //      Special end processing, as done by the KLMD instruction, seems to be
  //      emulated by the calling code.
  //
  //    - Z_ARG1 addresses the first byte of source data. The offset (Z_ARG3) is
  //      already accounted for.
  //
  //    - The current SHA state (the intermediate message digest value) is contained
  //      in an area addressed by Z_ARG2. The area size depends on the SHA variant
  //      and is accessible via the enum VM_Version::MsgDigest::_SHA<n>_parmBlk_I
  //
  //    - The single-block stub is expected to digest exactly one data block, starting
  //      at the address passed in Z_ARG1.
  //
  //    - The multi-block stub is expected to digest all data blocks which start in
  //      the offset interval [srcOff(Z_ARG3), srcLimit(Z_ARG4)). The exact difference
  //      (srcLimit-srcOff), rounded up to the next multiple of the data block length,
  //      gives the number of blocks to digest. It must be assumed that the calling code
  //      provides for a large enough source data buffer.
  //
  // Compute SHA-1 function.
  address generate_SHA1_stub(bool multiBlock, const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    unsigned int start_off = __ offset();   // Remember stub start address (is rtn value).

    const Register srcBuff        = Z_ARG1; // Points to first block to process (offset already added).
    const Register SHAState       = Z_ARG2; // Only on entry. Reused soon thereafter for kimd register pairs.
    const Register srcOff         = Z_ARG3; // int
    const Register srcLimit       = Z_ARG4; // Only passed in multiBlock case. int

    const Register SHAState_local = Z_R1;
    const Register SHAState_save  = Z_ARG3;
    const Register srcBufLen      = Z_ARG2; // Destroys state address, must be copied before.
    Label useKLMD, rtn;

    __ load_const_optimized(Z_R0, (int)VM_Version::MsgDigest::_SHA1);   // function code
    __ z_lgr(SHAState_local, SHAState);                                 // SHAState == parameter block

    if (multiBlock) {  // Process everything from offset to limit.

      // The following description is valid if we get a raw (unpimped) source data buffer,
      // spanning the range between [srcOff(Z_ARG3), srcLimit(Z_ARG4)). As detailled above,
      // the calling convention for these stubs is different. We leave the description in
      // to inform the reader what must be happening hidden in the calling code.
      //
      // The data block to be processed can have arbitrary length, i.e. its length does not
      // need to be an integer multiple of SHA<n>_datablk. Therefore, we need to implement
      // two different paths. If the length is an integer multiple, we use KIMD, saving us
      // to copy the SHA state back and forth. If the length is odd, we copy the SHA state
      // to the stack, execute a KLMD instruction on it and copy the result back to the
      // caller's SHA state location.

      // Total #srcBuff blocks to process.
      if (VM_Version::has_DistinctOpnds()) {
        __ z_srk(srcBufLen, srcLimit, srcOff); // exact difference
        __ z_ahi(srcBufLen, VM_Version::MsgDigest::_SHA1_dataBlk-1);   // round up
        __ z_nill(srcBufLen, (~(VM_Version::MsgDigest::_SHA1_dataBlk-1)) & 0xffff);
        __ z_ark(srcLimit, srcOff, srcBufLen); // Srclimit temporarily holds return value.
        __ z_llgfr(srcBufLen, srcBufLen);      // Cast to 64-bit.
      } else {
        __ z_lgfr(srcBufLen, srcLimit);        // Exact difference. srcLimit passed as int.
        __ z_sgfr(srcBufLen, srcOff);          // SrcOff passed as int, now properly casted to long.
        __ z_aghi(srcBufLen, VM_Version::MsgDigest::_SHA1_dataBlk-1);   // round up
        __ z_nill(srcBufLen, (~(VM_Version::MsgDigest::_SHA1_dataBlk-1)) & 0xffff);
        __ z_lgr(srcLimit, srcOff);            // SrcLimit temporarily holds return value.
        __ z_agr(srcLimit, srcBufLen);
      }

      // Integral #blocks to digest?
      // As a result of the calculations above, srcBufLen MUST be an integer
      // multiple of _SHA1_dataBlk, or else we are in big trouble.
      // We insert an asm_assert into the KLMD case to guard against that.
      __ z_tmll(srcBufLen, VM_Version::MsgDigest::_SHA1_dataBlk-1);
      __ z_brc(Assembler::bcondNotAllZero, useKLMD);

      // Process all full blocks.
      __ kimd(srcBuff);

      __ z_lgr(Z_RET, srcLimit);  // Offset of first unprocessed byte in buffer.
    } else {  // Process one data block only.
      __ load_const_optimized(srcBufLen, (int)VM_Version::MsgDigest::_SHA1_dataBlk);   // #srcBuff bytes to process
      __ kimd(srcBuff);
      __ add2reg(Z_RET, (int)VM_Version::MsgDigest::_SHA1_dataBlk, srcOff);            // Offset of first unprocessed byte in buffer. No 32 to 64 bit extension needed.
    }

    __ bind(rtn);
    __ z_br(Z_R14);

    if (multiBlock) {
      __ bind(useKLMD);

#if 1
      // Security net: this stub is believed to be called for full-sized data blocks only
      // NOTE: The following code is believed to be correct, but is is not tested.
      __ stop_static("SHA128 stub can digest full data blocks only. Use -XX:-UseSHA as remedy.", 0);
#endif
    }

    return __ addr_at(start_off);
  }

  // Compute SHA-256 function.
  address generate_SHA256_stub(bool multiBlock, const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    unsigned int start_off = __ offset();   // Remember stub start address (is rtn value).

    const Register srcBuff        = Z_ARG1;
    const Register SHAState       = Z_ARG2; // Only on entry. Reused soon thereafter.
    const Register SHAState_local = Z_R1;
    const Register SHAState_save  = Z_ARG3;
    const Register srcOff         = Z_ARG3;
    const Register srcLimit       = Z_ARG4;
    const Register srcBufLen      = Z_ARG2; // Destroys state address, must be copied before.
    Label useKLMD, rtn;

    __ load_const_optimized(Z_R0, (int)VM_Version::MsgDigest::_SHA256); // function code
    __ z_lgr(SHAState_local, SHAState);                                 // SHAState == parameter block

    if (multiBlock) {  // Process everything from offset to limit.
      // The following description is valid if we get a raw (unpimped) source data buffer,
      // spanning the range between [srcOff(Z_ARG3), srcLimit(Z_ARG4)). As detailled above,
      // the calling convention for these stubs is different. We leave the description in
      // to inform the reader what must be happening hidden in the calling code.
      //
      // The data block to be processed can have arbitrary length, i.e. its length does not
      // need to be an integer multiple of SHA<n>_datablk. Therefore, we need to implement
      // two different paths. If the length is an integer multiple, we use KIMD, saving us
      // to copy the SHA state back and forth. If the length is odd, we copy the SHA state
      // to the stack, execute a KLMD instruction on it and copy the result back to the
      // caller's SHA state location.

      // total #srcBuff blocks to process
      if (VM_Version::has_DistinctOpnds()) {
        __ z_srk(srcBufLen, srcLimit, srcOff);   // exact difference
        __ z_ahi(srcBufLen, VM_Version::MsgDigest::_SHA256_dataBlk-1); // round up
        __ z_nill(srcBufLen, (~(VM_Version::MsgDigest::_SHA256_dataBlk-1)) & 0xffff);
        __ z_ark(srcLimit, srcOff, srcBufLen);   // Srclimit temporarily holds return value.
        __ z_llgfr(srcBufLen, srcBufLen);        // Cast to 64-bit.
      } else {
        __ z_lgfr(srcBufLen, srcLimit);          // exact difference
        __ z_sgfr(srcBufLen, srcOff);
        __ z_aghi(srcBufLen, VM_Version::MsgDigest::_SHA256_dataBlk-1); // round up
        __ z_nill(srcBufLen, (~(VM_Version::MsgDigest::_SHA256_dataBlk-1)) & 0xffff);
        __ z_lgr(srcLimit, srcOff);              // Srclimit temporarily holds return value.
        __ z_agr(srcLimit, srcBufLen);
      }

      // Integral #blocks to digest?
      // As a result of the calculations above, srcBufLen MUST be an integer
      // multiple of _SHA1_dataBlk, or else we are in big trouble.
      // We insert an asm_assert into the KLMD case to guard against that.
      __ z_tmll(srcBufLen, VM_Version::MsgDigest::_SHA256_dataBlk-1);
      __ z_brc(Assembler::bcondNotAllZero, useKLMD);

      // Process all full blocks.
      __ kimd(srcBuff);

      __ z_lgr(Z_RET, srcLimit);  // Offset of first unprocessed byte in buffer.
    } else {  // Process one data block only.
      __ load_const_optimized(srcBufLen, (int)VM_Version::MsgDigest::_SHA256_dataBlk); // #srcBuff bytes to process
      __ kimd(srcBuff);
      __ add2reg(Z_RET, (int)VM_Version::MsgDigest::_SHA256_dataBlk, srcOff);          // Offset of first unprocessed byte in buffer.
    }

    __ bind(rtn);
    __ z_br(Z_R14);

    if (multiBlock) {
      __ bind(useKLMD);
#if 1
      // Security net: this stub is believed to be called for full-sized data blocks only.
      // NOTE:
      //   The following code is believed to be correct, but is is not tested.
      __ stop_static("SHA256 stub can digest full data blocks only. Use -XX:-UseSHA as remedy.", 0);
#endif
    }

    return __ addr_at(start_off);
  }

  // Compute SHA-512 function.
  address generate_SHA512_stub(bool multiBlock, const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    unsigned int start_off = __ offset();   // Remember stub start address (is rtn value).

    const Register srcBuff        = Z_ARG1;
    const Register SHAState       = Z_ARG2; // Only on entry. Reused soon thereafter.
    const Register SHAState_local = Z_R1;
    const Register SHAState_save  = Z_ARG3;
    const Register srcOff         = Z_ARG3;
    const Register srcLimit       = Z_ARG4;
    const Register srcBufLen      = Z_ARG2; // Destroys state address, must be copied before.
    Label useKLMD, rtn;

    __ load_const_optimized(Z_R0, (int)VM_Version::MsgDigest::_SHA512); // function code
    __ z_lgr(SHAState_local, SHAState);                                 // SHAState == parameter block

    if (multiBlock) {  // Process everything from offset to limit.
      // The following description is valid if we get a raw (unpimped) source data buffer,
      // spanning the range between [srcOff(Z_ARG3), srcLimit(Z_ARG4)). As detailled above,
      // the calling convention for these stubs is different. We leave the description in
      // to inform the reader what must be happening hidden in the calling code.
      //
      // The data block to be processed can have arbitrary length, i.e. its length does not
      // need to be an integer multiple of SHA<n>_datablk. Therefore, we need to implement
      // two different paths. If the length is an integer multiple, we use KIMD, saving us
      // to copy the SHA state back and forth. If the length is odd, we copy the SHA state
      // to the stack, execute a KLMD instruction on it and copy the result back to the
      // caller's SHA state location.

      // total #srcBuff blocks to process
      if (VM_Version::has_DistinctOpnds()) {
        __ z_srk(srcBufLen, srcLimit, srcOff);   // exact difference
        __ z_ahi(srcBufLen, VM_Version::MsgDigest::_SHA512_dataBlk-1); // round up
        __ z_nill(srcBufLen, (~(VM_Version::MsgDigest::_SHA512_dataBlk-1)) & 0xffff);
        __ z_ark(srcLimit, srcOff, srcBufLen);   // Srclimit temporarily holds return value.
        __ z_llgfr(srcBufLen, srcBufLen);        // Cast to 64-bit.
      } else {
        __ z_lgfr(srcBufLen, srcLimit);          // exact difference
        __ z_sgfr(srcBufLen, srcOff);
        __ z_aghi(srcBufLen, VM_Version::MsgDigest::_SHA512_dataBlk-1); // round up
        __ z_nill(srcBufLen, (~(VM_Version::MsgDigest::_SHA512_dataBlk-1)) & 0xffff);
        __ z_lgr(srcLimit, srcOff);              // Srclimit temporarily holds return value.
        __ z_agr(srcLimit, srcBufLen);
      }

      // integral #blocks to digest?
      // As a result of the calculations above, srcBufLen MUST be an integer
      // multiple of _SHA1_dataBlk, or else we are in big trouble.
      // We insert an asm_assert into the KLMD case to guard against that.
      __ z_tmll(srcBufLen, VM_Version::MsgDigest::_SHA512_dataBlk-1);
      __ z_brc(Assembler::bcondNotAllZero, useKLMD);

      // Process all full blocks.
      __ kimd(srcBuff);

      __ z_lgr(Z_RET, srcLimit);  // Offset of first unprocessed byte in buffer.
    } else {  // Process one data block only.
      __ load_const_optimized(srcBufLen, (int)VM_Version::MsgDigest::_SHA512_dataBlk); // #srcBuff bytes to process
      __ kimd(srcBuff);
      __ add2reg(Z_RET, (int)VM_Version::MsgDigest::_SHA512_dataBlk, srcOff);          // Offset of first unprocessed byte in buffer.
    }

    __ bind(rtn);
    __ z_br(Z_R14);

    if (multiBlock) {
      __ bind(useKLMD);
#if 1
      // Security net: this stub is believed to be called for full-sized data blocks only
      // NOTE:
      //   The following code is believed to be correct, but is is not tested.
      __ stop_static("SHA512 stub can digest full data blocks only. Use -XX:-UseSHA as remedy.", 0);
#endif
    }

    return __ addr_at(start_off);
  }



  // Arguments:
  //   Z_ARG1  - int   crc
  //   Z_ARG2  - byte* buf
  //   Z_ARG3  - int   length (of buffer)
  //
  // Result:
  //   Z_RET   - int   crc result
  //
  // Compute CRC32 function.
  address generate_CRC32_updateBytes(const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    // arguments to kernel_crc32:
    Register       crc     = Z_ARG1;  // Current checksum, preset by caller or result from previous call, int.
    Register       data    = Z_ARG2;  // source byte array
    Register       dataLen = Z_ARG3;  // #bytes to process, int
    Register       table   = Z_ARG4;  // crc table address
    const Register t0      = Z_R10;   // work reg for kernel* emitters
    const Register t1      = Z_R11;   // work reg for kernel* emitters
    const Register t2      = Z_R12;   // work reg for kernel* emitters
    const Register t3      = Z_R13;   // work reg for kernel* emitters

    assert_different_registers(crc, data, dataLen, table);

    // We pass these values as ints, not as longs as required by C calling convention.
    // Crc used as int.
    __ z_llgfr(dataLen, dataLen);

    StubRoutines::zarch::generate_load_crc_table_addr(_masm, table);

    __ resize_frame(-(6*8), Z_R0, true); // Resize frame to provide add'l space to spill 5 registers.
    __ z_stmg(Z_R10, Z_R13, 1*8, Z_SP);  // Spill regs 10..11 to make them available as work registers.
    __ kernel_crc32_1word(crc, data, dataLen, table, t0, t1, t2, t3);
    __ z_lmg(Z_R10, Z_R13, 1*8, Z_SP);   // Spill regs 10..11 back from stack.
    __ resize_frame(+(6*8), Z_R0, true); // Resize frame to provide add'l space to spill 5 registers.

    __ z_llgfr(Z_RET, crc);  // Updated crc is function result. No copying required, just zero upper 32 bits.
    __ z_br(Z_R14);          // Result already in Z_RET == Z_ARG1.

    return __ addr_at(start_off);
  }


  // Arguments:
  //   Z_ARG1    - x address
  //   Z_ARG2    - x length
  //   Z_ARG3    - y address
  //   Z_ARG4    - y length
  //   Z_ARG5    - z address
  //   160[Z_SP] - z length
  address generate_multiplyToLen() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "multiplyToLen");

    address start = __ pc();

    const Register x    = Z_ARG1;
    const Register xlen = Z_ARG2;
    const Register y    = Z_ARG3;
    const Register ylen = Z_ARG4;
    const Register z    = Z_ARG5;
    // zlen is passed on the stack:
    // Address zlen(Z_SP, _z_abi(remaining_cargs));

    // Next registers will be saved on stack in multiply_to_len().
    const Register tmp1 = Z_tmp_1;
    const Register tmp2 = Z_tmp_2;
    const Register tmp3 = Z_tmp_3;
    const Register tmp4 = Z_tmp_4;
    const Register tmp5 = Z_R9;

    BLOCK_COMMENT("Entry:");

    __ z_llgfr(xlen, xlen);
    __ z_llgfr(ylen, ylen);

    __ multiply_to_len(x, xlen, y, ylen, z, tmp1, tmp2, tmp3, tmp4, tmp5);

    __ z_br(Z_R14);  // Return to caller.

    return start;
  }

  void generate_initial() {
    // Generates all stubs and initializes the entry points.

    // Entry points that exist in all platforms.
    // Note: This is code that could be shared among different
    // platforms - however the benefit seems to be smaller than the
    // disadvantage of having a much more complicated generator
    // structure. See also comment in stubRoutines.hpp.
    StubRoutines::_forward_exception_entry                 = generate_forward_exception();

    StubRoutines::_call_stub_entry                         = generate_call_stub(StubRoutines::_call_stub_return_address);
    StubRoutines::_catch_exception_entry                   = generate_catch_exception();

    // Build this early so it's available for the interpreter.
    StubRoutines::_throw_StackOverflowError_entry          =
      generate_throw_exception("StackOverflowError throw_exception",
                               CAST_FROM_FN_PTR(address, SharedRuntime::throw_StackOverflowError), false);
    StubRoutines::_throw_delayed_StackOverflowError_entry  =
      generate_throw_exception("delayed StackOverflowError throw_exception",
                               CAST_FROM_FN_PTR(address, SharedRuntime::throw_delayed_StackOverflowError), false);

    //----------------------------------------------------------------------
    // Entry points that are platform specific.

    if (UseCRC32Intrinsics) {
      // We have no CRC32 table on z/Architecture.
      StubRoutines::_crc_table_adr    = (address)StubRoutines::zarch::_crc_table;
      StubRoutines::_updateBytesCRC32 = generate_CRC32_updateBytes("CRC32_updateBytes");
    }

    // Comapct string intrinsics: Translate table for string inflate intrinsic. Used by trot instruction.
    StubRoutines::zarch::_trot_table_addr = (address)StubRoutines::zarch::_trot_table;
  }


  void generate_all() {
    // Generates all stubs and initializes the entry points.

    StubRoutines::zarch::_partial_subtype_check            = generate_partial_subtype_check();

    // These entry points require SharedInfo::stack0 to be set up in non-core builds.
    StubRoutines::_throw_AbstractMethodError_entry         = generate_throw_exception("AbstractMethodError throw_exception",          CAST_FROM_FN_PTR(address, SharedRuntime::throw_AbstractMethodError),  false);
    StubRoutines::_throw_IncompatibleClassChangeError_entry= generate_throw_exception("IncompatibleClassChangeError throw_exception", CAST_FROM_FN_PTR(address, SharedRuntime::throw_IncompatibleClassChangeError),  false);
    StubRoutines::_throw_NullPointerException_at_call_entry= generate_throw_exception("NullPointerException at call throw_exception", CAST_FROM_FN_PTR(address, SharedRuntime::throw_NullPointerException_at_call), false);

    StubRoutines::zarch::_handler_for_unsafe_access_entry  =  generate_handler_for_unsafe_access();

    // Support for verify_oop (must happen after universe_init).
    StubRoutines::_verify_oop_subroutine_entry             = generate_verify_oop_subroutine();

    // Arraycopy stubs used by compilers.
    generate_arraycopy_stubs();

    // safefetch stubs
    generate_safefetch("SafeFetch32", sizeof(int),      &StubRoutines::_safefetch32_entry, &StubRoutines::_safefetch32_fault_pc, &StubRoutines::_safefetch32_continuation_pc);
    generate_safefetch("SafeFetchN",  sizeof(intptr_t), &StubRoutines::_safefetchN_entry,  &StubRoutines::_safefetchN_fault_pc,  &StubRoutines::_safefetchN_continuation_pc);

    // Generate AES intrinsics code.
    if (UseAESIntrinsics) {
      StubRoutines::_aescrypt_encryptBlock = generate_AES_encryptBlock("AES_encryptBlock");
      StubRoutines::_aescrypt_decryptBlock = generate_AES_decryptBlock("AES_decryptBlock");
      StubRoutines::_cipherBlockChaining_encryptAESCrypt = generate_cipherBlockChaining_AES_encrypt("AES_encryptBlock_chaining");
      StubRoutines::_cipherBlockChaining_decryptAESCrypt = generate_cipherBlockChaining_AES_decrypt("AES_decryptBlock_chaining");
    }

    // Generate SHA1/SHA256/SHA512 intrinsics code.
    if (UseSHA1Intrinsics) {
      StubRoutines::_sha1_implCompress     = generate_SHA1_stub(false,   "SHA1_singleBlock");
      StubRoutines::_sha1_implCompressMB   = generate_SHA1_stub(true,    "SHA1_multiBlock");
    }
    if (UseSHA256Intrinsics) {
      StubRoutines::_sha256_implCompress   = generate_SHA256_stub(false, "SHA256_singleBlock");
      StubRoutines::_sha256_implCompressMB = generate_SHA256_stub(true,  "SHA256_multiBlock");
    }
    if (UseSHA512Intrinsics) {
      StubRoutines::_sha512_implCompress   = generate_SHA512_stub(false, "SHA512_singleBlock");
      StubRoutines::_sha512_implCompressMB = generate_SHA512_stub(true,  "SHA512_multiBlock");
    }

#ifdef COMPILER2
    if (UseMultiplyToLenIntrinsic) {
      StubRoutines::_multiplyToLen = generate_multiplyToLen();
    }
    if (UseMontgomeryMultiplyIntrinsic) {
      StubRoutines::_montgomeryMultiply
        = CAST_FROM_FN_PTR(address, SharedRuntime::montgomery_multiply);
    }
    if (UseMontgomerySquareIntrinsic) {
      StubRoutines::_montgomerySquare
        = CAST_FROM_FN_PTR(address, SharedRuntime::montgomery_square);
    }
#endif
  }

 public:
  StubGenerator(CodeBuffer* code, bool all) : StubCodeGenerator(code) {
    // Replace the standard masm with a special one:
    _masm = new MacroAssembler(code);

    _stub_count = !all ? 0x100 : 0x200;
    if (all) {
      generate_all();
    } else {
      generate_initial();
    }
  }

 private:
  int _stub_count;
  void stub_prolog(StubCodeDesc* cdesc) {
#ifdef ASSERT
    // Put extra information in the stub code, to make it more readable.
    // Write the high part of the address.
    // [RGV] Check if there is a dependency on the size of this prolog.
    __ emit_32((intptr_t)cdesc >> 32);
    __ emit_32((intptr_t)cdesc);
    __ emit_32(++_stub_count);
#endif
    align(true);
  }

  void align(bool at_header = false) {
    // z/Architecture cache line size is 256 bytes.
    // There is no obvious benefit in aligning stub
    // code to cache lines. Use CodeEntryAlignment instead.
    const unsigned int icache_line_size      = CodeEntryAlignment;
    const unsigned int icache_half_line_size = MIN2<unsigned int>(32, CodeEntryAlignment);

    if (at_header) {
      while ((intptr_t)(__ pc()) % icache_line_size != 0) {
        __ emit_16(0);
      }
    } else {
      while ((intptr_t)(__ pc()) % icache_half_line_size != 0) {
        __ z_nop();
      }
    }
  }

};

void StubGenerator_generate(CodeBuffer* code, bool all) {
  StubGenerator g(code, all);
}
