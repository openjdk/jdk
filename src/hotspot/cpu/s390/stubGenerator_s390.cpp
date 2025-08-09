/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, 2024 SAP SE. All rights reserved.
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

#include "asm/macroAssembler.inline.hpp"
#include "registerSaver_s390.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interp_masm.hpp"
#include "memory/universe.hpp"
#include "nativeInst_s390.hpp"
#include "oops/instanceOop.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "prims/upcallLinker.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"

// Declaration and definition of StubGenerator (no .hpp file).
// For a more detailed description of the stub routine structure
// see the comment in stubRoutines.hpp.

#ifdef PRODUCT
#define __ _masm->
#else
#define __ (Verbose ? (_masm->block_comment(FILE_AND_LINE),_masm):_masm)->
#endif

#define BLOCK_COMMENT(str) if (PrintAssembly || PrintStubCode) __ block_comment(str)
#define BIND(label)        bind(label); BLOCK_COMMENT(#label ":")


  // These static, partially const, variables are for the AES intrinsics.
  // They are declared/initialized here to make them available across function bodies.

      static const int AES_parmBlk_align    = 32;                  // octoword alignment.
      static const int AES_stackSpace_incr  = AES_parmBlk_align;   // add'l stack space is allocated in such increments.
                                                                   // Must be multiple of AES_parmBlk_align.

      static int AES_ctrVal_len  = 0;                              // ctr init value len (in bytes), expected: length of dataBlk (16)
      static int AES_ctrVec_len  = 0;                              // # of ctr vector elements. That many block can be ciphered with one instruction execution
      static int AES_ctrArea_len = 0;                              // reserved stack space (in bytes) for ctr (= ctrVal_len * ctrVec_len)

      static int AES_parmBlk_addspace = 0;  // Must be multiple of AES_parmblk_align.
                                            // Will be set by stub generator to stub specific value.
      static int AES_dataBlk_space    = 0;  // Must be multiple of AES_parmblk_align.
                                            // Will be set by stub generator to stub specific value.
      static int AES_dataBlk_offset   = 0;  // offset of the local src and dst dataBlk buffers
                                            // Will be set by stub generator to stub specific value.

      // These offsets are relative to the parameter block address (Register parmBlk = Z_R1)
      static const int keylen_offset     =  -1;
      static const int fCode_offset      =  -2;
      static const int ctrVal_len_offset =  -4;
      static const int msglen_offset     =  -8;
      static const int unextSP_offset    = -16;
      static const int rem_msgblk_offset = -20;
      static const int argsave_offset    = -2*AES_parmBlk_align;
      static const int regsave_offset    = -4*AES_parmBlk_align; // save space for work regs (Z_R10..13)
      static const int msglen_red_offset = regsave_offset + AES_parmBlk_align; // reduced len after preLoop;
      static const int counter_offset    = msglen_red_offset+8;  // current counter vector position.
      static const int localSpill_offset = argsave_offset + 24;  // arg2..arg4 are saved


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
    // Set up a new C frame, copy Java arguments, call template interpreter
    // or native_entry, and process result.

    StubId stub_id = StubId::stubgen_call_stub_id;
    StubCodeMark mark(this, stub_id);
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
      // Call template interpreter or native entry.

      //
      // Register state on entry to template interpreter / native entry:
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
      // Stack on entry to template interpreter / native entry:
      //
      //     F0      [TOP_IJAVA_FRAME_ABI]
      //             [outgoing Java arguments]
      //             [ENTRY_FRAME_LOCALS]
      //     F1      [C_FRAME]
      //             ...
      //

      // Do a light-weight C-call here, r_new_arg_entry holds the address
      // of the interpreter entry point (template interpreter or native entry)
      // and save runtime-value of return_pc in return_address
      // (call by reference argument).
      return_address = __ call_stub(r_new_arg_entry);
    }
    BLOCK_COMMENT("} call");

    {
      BLOCK_COMMENT("restore registers {");
      // Returned from template interpreter or native entry.
      // Now pop frame, process result, and return to caller.

      //
      // Stack on exit from template interpreter / native entry:
      //
      //     F0      [ABI]
      //             ...
      //             [ENTRY_FRAME_LOCALS]
      //     F1      [C_FRAME]
      //             ...
      //
      // Just pop the topmost frame ...
      //

      // Restore frame pointer.
      __ z_lg(r_entryframe_fp, _z_abi(callers_sp), Z_SP);
      // Pop frame. Done here to minimize stalls.
      __ pop_frame();

      // Reload some volatile registers which we've spilled before the call
      // to template interpreter / native entry.
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
      __ asm_assert(Assembler::bcondNotLow, assertMsg, 0x0234);
      __ z_chi(r_arg_result_type, T_NARROWOOP);
      __ asm_assert(Assembler::bcondNotHigh, assertMsg, 0x0235);
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
    StubId stub_id = StubId::stubgen_catch_exception_id;
    StubCodeMark mark(this, stub_id);

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
    assert(StubRoutines::_call_stub_return_address != nullptr, "must have been generated before");

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
    StubId stub_id = StubId::stubgen_forward_exception_id;
    StubCodeMark mark(this, stub_id);
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

#undef __
#ifdef PRODUCT
#define __ _masm->
#else
#define __ (Verbose ? (_masm->block_comment(FILE_AND_LINE),_masm):_masm)->
#endif

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
    StubId stub_id = StubId::stubgen_partial_subtype_check_id;
    StubCodeMark mark(this, stub_id);
    Label miss;

    address start = __ pc();

    const Register Rsubklass   = Z_ARG2; // subklass
    const Register Rsuperklass = Z_ARG3; // superklass

    // No args, but tmp registers that are killed.
    const Register Rlength     = Z_ARG4; // cache array length
    const Register Rarray_ptr  = Z_ARG5; // Current value from cache array.

    if (UseCompressedOops) {
      assert(Universe::heap() != nullptr, "java heap must be initialized to generate partial_subtype_check stub");
    }

    // Always take the slow path.
    __ check_klass_subtype_slow_path(Rsubklass, Rsuperklass,
                                     Rarray_ptr, Rlength, nullptr, &miss);

    // Match falls through here.
    __ clear_reg(Z_RET);               // Zero indicates a match. Set EQ flag in CC.
    __ z_br(Z_R14);

    __ BIND(miss);
    __ load_const_optimized(Z_RET, 1); // One indicates a miss.
    __ z_ltgr(Z_RET, Z_RET);           // Set NE flag in CR.
    __ z_br(Z_R14);

    return start;
  }

  void generate_lookup_secondary_supers_table_stub() {
    StubId stub_id = StubId::stubgen_lookup_secondary_supers_table_id;
    StubCodeMark mark(this, stub_id);

    const Register
        r_super_klass  = Z_ARG1,
        r_sub_klass    = Z_ARG2,
        r_array_index  = Z_ARG3,
        r_array_length = Z_ARG4,
        r_array_base   = Z_ARG5,
        r_bitmap       = Z_R10,
        r_result       = Z_R11;
    for (int slot = 0; slot < Klass::SECONDARY_SUPERS_TABLE_SIZE; slot++) {
      StubRoutines::_lookup_secondary_supers_table_stubs[slot] = __ pc();
      __ lookup_secondary_supers_table_const(r_sub_klass, r_super_klass,
                                             r_array_base, r_array_length, r_array_index,
                                             r_bitmap, r_result, slot);

      __ z_br(Z_R14);
    }
  }

  // Slow path implementation for UseSecondarySupersTable.
  address generate_lookup_secondary_supers_table_slow_path_stub() {
    StubId stub_id = StubId::stubgen_lookup_secondary_supers_table_slow_path_id;
    StubCodeMark mark(this, stub_id);

    address start = __ pc();

    const Register
        r_super_klass  = Z_ARG1,
        r_array_base   = Z_ARG5,
        r_temp1        = Z_ARG4,
        r_array_index  = Z_ARG3,
        r_bitmap       = Z_R10,
        r_result       = Z_R11;

    __ lookup_secondary_supers_table_slow_path(r_super_klass, r_array_base,
                                               r_array_index, r_bitmap, r_temp1, r_result, /* is_stub */ true);

    __ z_br(Z_R14);

    return start;
  }

#if !defined(PRODUCT)
  // Wrapper which calls oopDesc::is_oop_or_null()
  // Only called by MacroAssembler::verify_oop
  static void verify_oop_helper(const char* message, oopDesc* o) {
    if (!oopDesc::is_oop_or_null(o)) {
      fatal("%s. oop: " PTR_FORMAT, message, p2i(o));
    }
    ++ StubRoutines::_verify_oop_count;
  }
#endif

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

    address start = nullptr;

#if !defined(PRODUCT)
    start = CAST_FROM_FN_PTR(address, verify_oop_helper);
#endif

    return start;
  }

  // This is to test that the count register contains a positive int value.
  // Required because C2 does not respect int to long conversion for stub calls.
  void assert_positive_int(Register count) {
#ifdef ASSERT
    __ z_srag(Z_R0, count, 31);  // Just leave the sign (must be zero) in Z_R0.
    __ asm_assert(Assembler::bcondZero, "missing zero extend", 0xAFFE);
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

  address generate_disjoint_nonoop_copy(StubId stub_id) {
    bool aligned;
    int element_size;
    switch (stub_id) {
    case StubId::stubgen_jbyte_disjoint_arraycopy_id:
      aligned = false;
      element_size = 1;
      break;
    case StubId::stubgen_arrayof_jbyte_disjoint_arraycopy_id:
      aligned = true;
      element_size = 1;
      break;
    case StubId::stubgen_jshort_disjoint_arraycopy_id:
      aligned = false;
      element_size = 2;
      break;
    case StubId::stubgen_arrayof_jshort_disjoint_arraycopy_id:
      aligned = true;
      element_size = 2;
      break;
    case StubId::stubgen_jint_disjoint_arraycopy_id:
      aligned = false;
      element_size = 4;
      break;
    case StubId::stubgen_arrayof_jint_disjoint_arraycopy_id:
      aligned = true;
      element_size = 4;
      break;
    case StubId::stubgen_jlong_disjoint_arraycopy_id:
      aligned = false;
      element_size = 8;
      break;
    case StubId::stubgen_arrayof_jlong_disjoint_arraycopy_id:
      aligned = true;
      element_size = 8;
      break;
    default:
      ShouldNotReachHere();
    }
    StubCodeMark mark(this, stub_id);
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    generate_disjoint_copy(aligned, element_size, false, false);
    return __ addr_at(start_off);
  }

  address generate_disjoint_oop_copy(StubId stub_id) {
    bool aligned;
    bool dest_uninitialized;
    switch (stub_id) {
    case StubId::stubgen_oop_disjoint_arraycopy_id:
      aligned = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_arrayof_oop_disjoint_arraycopy_id:
      aligned = true;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_oop_disjoint_arraycopy_uninit_id:
      aligned = false;
      dest_uninitialized = true;
      break;
    case StubId::stubgen_arrayof_oop_disjoint_arraycopy_uninit_id:
      aligned = true;
      dest_uninitialized = true;
      break;
    default:
      ShouldNotReachHere();
    }
    StubCodeMark mark(this, stub_id);
    // This is the zarch specific stub generator for oop array copy.
    // Refer to generate_disjoint_copy for a list of prereqs and features.
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    unsigned int size      = UseCompressedOops ? 4 : 8;

    DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_DISJOINT;
    if (dest_uninitialized) {
      decorators |= IS_DEST_UNINITIALIZED;
    }
    if (aligned) {
      decorators |= ARRAYCOPY_ALIGNED;
    }

    BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
    bs->arraycopy_prologue(_masm, decorators, T_OBJECT, Z_ARG1, Z_ARG2, Z_ARG3);

    generate_disjoint_copy(aligned, size, true, true);

    bs->arraycopy_epilogue(_masm, decorators, T_OBJECT, Z_ARG2, Z_ARG3, true);

    return __ addr_at(start_off);
  }

  address generate_conjoint_nonoop_copy(StubId stub_id) {
    bool aligned;
    int shift; // i.e. log2(element size)
    address nooverlap_target;
    switch (stub_id) {
    case StubId::stubgen_jbyte_arraycopy_id:
      aligned = false;
      shift = 0;
      nooverlap_target = StubRoutines::jbyte_disjoint_arraycopy();
      break;
    case StubId::stubgen_arrayof_jbyte_arraycopy_id:
      aligned = true;
      shift = 0;
      nooverlap_target = StubRoutines::arrayof_jbyte_disjoint_arraycopy();
      break;
    case StubId::stubgen_jshort_arraycopy_id:
      aligned = false;
      shift = 1;
      nooverlap_target = StubRoutines::jshort_disjoint_arraycopy();
      break;
    case StubId::stubgen_arrayof_jshort_arraycopy_id:
      aligned = true;
      shift = 1;
      nooverlap_target = StubRoutines::arrayof_jshort_disjoint_arraycopy();
      break;
    case StubId::stubgen_jint_arraycopy_id:
      aligned = false;
      shift = 2;
      nooverlap_target = StubRoutines::jint_disjoint_arraycopy();
      break;
    case StubId::stubgen_arrayof_jint_arraycopy_id:
      aligned = true;
      shift = 2;
      nooverlap_target = StubRoutines::arrayof_jint_disjoint_arraycopy();
      break;
    case StubId::stubgen_jlong_arraycopy_id:
      aligned = false;
      shift = 3;
      nooverlap_target = StubRoutines::jlong_disjoint_arraycopy();
      break;
    case StubId::stubgen_arrayof_jlong_arraycopy_id:
      aligned = true;
      shift = 3;
      nooverlap_target = StubRoutines::arrayof_jlong_disjoint_arraycopy();
      break;
    default:
      ShouldNotReachHere();
    }
    StubCodeMark mark(this, stub_id);
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    array_overlap_test(nooverlap_target, shift); // Branch away to nooverlap_target if disjoint.
    generate_conjoint_copy(aligned, 1 << shift, false);
    return __ addr_at(start_off);
  }

  address generate_conjoint_oop_copy(StubId stub_id) {
    bool aligned;
    bool dest_uninitialized;
    address nooverlap_target;
    switch (stub_id) {
    case StubId::stubgen_oop_arraycopy_id:
      aligned = false;
      dest_uninitialized = false;
      nooverlap_target = StubRoutines::oop_disjoint_arraycopy(dest_uninitialized);
      break;
    case StubId::stubgen_arrayof_oop_arraycopy_id:
      aligned = true;
      dest_uninitialized = false;
      nooverlap_target = StubRoutines::arrayof_oop_disjoint_arraycopy(dest_uninitialized);
      break;
    case StubId::stubgen_oop_arraycopy_uninit_id:
      aligned = false;
      dest_uninitialized = true;
      nooverlap_target = StubRoutines::oop_disjoint_arraycopy(dest_uninitialized);
      break;
    case StubId::stubgen_arrayof_oop_arraycopy_uninit_id:
      aligned = true;
      dest_uninitialized = true;
      nooverlap_target = StubRoutines::arrayof_oop_disjoint_arraycopy(dest_uninitialized);
      break;
    default:
      ShouldNotReachHere();
    }
    StubCodeMark mark(this, stub_id);
    // This is the zarch specific stub generator for overlapping oop array copy.
    // Refer to generate_conjoint_copy for a list of prereqs and features.
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).
    unsigned int size      = UseCompressedOops ? 4 : 8;
    unsigned int shift     = UseCompressedOops ? 2 : 3;

    // Branch to disjoint_copy (if applicable) before pre_barrier to avoid double pre_barrier.
    array_overlap_test(nooverlap_target, shift);  // Branch away to nooverlap_target if disjoint.

    DecoratorSet decorators = IN_HEAP | IS_ARRAY;
    if (dest_uninitialized) {
      decorators |= IS_DEST_UNINITIALIZED;
    }
    if (aligned) {
      decorators |= ARRAYCOPY_ALIGNED;
    }

    BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
    bs->arraycopy_prologue(_masm, decorators, T_OBJECT, Z_ARG1, Z_ARG2, Z_ARG3);

    generate_conjoint_copy(aligned, size, true);  // Must preserve ARG2, ARG3.

    bs->arraycopy_epilogue(_masm, decorators, T_OBJECT, Z_ARG2, Z_ARG3, true);

    return __ addr_at(start_off);
  }

  //
  //  Generate 'unsafe' set memory stub
  //  Though just as safe as the other stubs, it takes an unscaled
  //  size_t (# bytes) argument instead of an element count.
  //
  //  Input:
  //    Z_ARG1   - destination array address
  //    Z_ARG2   - byte count (size_t)
  //    Z_ARG3   - byte value
  //
  address generate_unsafe_setmemory(address unsafe_byte_fill) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, StubId::stubgen_unsafe_setmemory_id);
    unsigned int start_off = __ offset();

    // bump this on entry, not on exit:
    // inc_counter_np(SharedRuntime::_unsafe_set_memory_ctr);

    const Register dest = Z_ARG1;
    const Register size = Z_ARG2;
    const Register byteVal = Z_ARG3;
    NearLabel tail, finished;
    // fill_to_memory_atomic(unsigned char*, unsigned long, unsigned char)

    // Mark remaining code as such which performs Unsafe accesses.
    UnsafeMemoryAccessMark umam(this, true, false);

    __ z_vlvgb(Z_V0, byteVal, 0);
    __ z_vrepb(Z_V0, Z_V0, 0);

    __ z_aghi(size, -32);
    __ z_brl(tail);

    {
      NearLabel again;
      __ bind(again);
      __ z_vst(Z_V0, Address(dest, 0));
      __ z_vst(Z_V0, Address(dest, 16));
      __ z_aghi(dest, 32);
      __ z_aghi(size, -32);
      __ z_brnl(again);
    }

    __ bind(tail);

    {
      NearLabel dont;
      __ testbit(size, 4);
      __ z_brz(dont);
      __ z_vst(Z_V0, Address(dest, 0));
      __ z_aghi(dest, 16);
      __ bind(dont);
    }

    {
      NearLabel dont;
      __ testbit(size, 3);
      __ z_brz(dont);
      __ z_vsteg(Z_V0, 0, Z_R0, dest, 0);
      __ z_aghi(dest, 8);
      __ bind(dont);
    }

    __ z_tmll(size, 7);
    __ z_brc(Assembler::bcondAllZero, finished);

    {
      NearLabel dont;
      __ testbit(size, 2);
      __ z_brz(dont);
      __ z_vstef(Z_V0, 0, Z_R0, dest, 0);
      __ z_aghi(dest, 4);
      __ bind(dont);
    }

    {
      NearLabel dont;
      __ testbit(size, 1);
      __ z_brz(dont);
      __ z_vsteh(Z_V0, 0, Z_R0, dest, 0);
      __ z_aghi(dest, 2);
      __ bind(dont);
    }

    {
      NearLabel dont;
      __ testbit(size, 0);
      __ z_brz(dont);
      __ z_vsteb(Z_V0, 0, Z_R0, dest, 0);
      __ bind(dont);
    }

    __ bind(finished);
    __ z_br(Z_R14);

    return __ addr_at(start_off);
  }

  // This is common errorexit stub for UnsafeMemoryAccess.
  address generate_unsafecopy_common_error_exit() {
    unsigned int start_off = __ offset();
    __ z_lghi(Z_RET, 0); // return 0
    __ z_br(Z_R14);
    return __ addr_at(start_off);
  }

  void generate_arraycopy_stubs() {

    // they want an UnsafeMemoryAccess exit non-local to the stub
    StubRoutines::_unsafecopy_common_exit = generate_unsafecopy_common_error_exit();
    // register the stub as the default exit with class UnsafeMemoryAccess
    UnsafeMemoryAccess::set_common_exit_stub_pc(StubRoutines::_unsafecopy_common_exit);

    // Note: the disjoint stubs must be generated first, some of
    // the conjoint stubs use them.

    StubRoutines::_jbyte_disjoint_arraycopy      = generate_disjoint_nonoop_copy (StubId::stubgen_jbyte_disjoint_arraycopy_id);
    StubRoutines::_jshort_disjoint_arraycopy     = generate_disjoint_nonoop_copy(StubId::stubgen_jshort_disjoint_arraycopy_id);
    StubRoutines::_jint_disjoint_arraycopy       = generate_disjoint_nonoop_copy  (StubId::stubgen_jint_disjoint_arraycopy_id);
    StubRoutines::_jlong_disjoint_arraycopy      = generate_disjoint_nonoop_copy (StubId::stubgen_jlong_disjoint_arraycopy_id);
    StubRoutines::_oop_disjoint_arraycopy        = generate_disjoint_oop_copy  (StubId::stubgen_oop_disjoint_arraycopy_id);
    StubRoutines::_oop_disjoint_arraycopy_uninit = generate_disjoint_oop_copy  (StubId::stubgen_oop_disjoint_arraycopy_uninit_id);

    StubRoutines::_arrayof_jbyte_disjoint_arraycopy      = generate_disjoint_nonoop_copy (StubId::stubgen_arrayof_jbyte_disjoint_arraycopy_id);
    StubRoutines::_arrayof_jshort_disjoint_arraycopy     = generate_disjoint_nonoop_copy(StubId::stubgen_arrayof_jshort_disjoint_arraycopy_id);
    StubRoutines::_arrayof_jint_disjoint_arraycopy       = generate_disjoint_nonoop_copy  (StubId::stubgen_arrayof_jint_disjoint_arraycopy_id);
    StubRoutines::_arrayof_jlong_disjoint_arraycopy      = generate_disjoint_nonoop_copy (StubId::stubgen_arrayof_jlong_disjoint_arraycopy_id);
    StubRoutines::_arrayof_oop_disjoint_arraycopy        = generate_disjoint_oop_copy  (StubId::stubgen_arrayof_oop_disjoint_arraycopy_id);
    StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit = generate_disjoint_oop_copy  (StubId::stubgen_arrayof_oop_disjoint_arraycopy_uninit_id);

    StubRoutines::_jbyte_arraycopy           = generate_conjoint_nonoop_copy(StubId::stubgen_jbyte_arraycopy_id);
    StubRoutines::_jshort_arraycopy          = generate_conjoint_nonoop_copy(StubId::stubgen_jshort_arraycopy_id);
    StubRoutines::_jint_arraycopy            = generate_conjoint_nonoop_copy(StubId::stubgen_jint_arraycopy_id);
    StubRoutines::_jlong_arraycopy           = generate_conjoint_nonoop_copy(StubId::stubgen_jlong_arraycopy_id);
    StubRoutines::_oop_arraycopy             = generate_conjoint_oop_copy(StubId::stubgen_oop_arraycopy_id);
    StubRoutines::_oop_arraycopy_uninit      = generate_conjoint_oop_copy(StubId::stubgen_oop_arraycopy_uninit_id);

    StubRoutines::_arrayof_jbyte_arraycopy      = generate_conjoint_nonoop_copy(StubId::stubgen_arrayof_jbyte_arraycopy_id);
    StubRoutines::_arrayof_jshort_arraycopy     = generate_conjoint_nonoop_copy(StubId::stubgen_arrayof_jshort_arraycopy_id);
    StubRoutines::_arrayof_jint_arraycopy       = generate_conjoint_nonoop_copy (StubId::stubgen_arrayof_jint_arraycopy_id);
    StubRoutines::_arrayof_jlong_arraycopy      = generate_conjoint_nonoop_copy(StubId::stubgen_arrayof_jlong_arraycopy_id);
    StubRoutines::_arrayof_oop_arraycopy        = generate_conjoint_oop_copy(StubId::stubgen_arrayof_oop_arraycopy_id);
    StubRoutines::_arrayof_oop_arraycopy_uninit = generate_conjoint_oop_copy(StubId::stubgen_arrayof_oop_arraycopy_uninit_id);

#ifdef COMPILER2
    StubRoutines::_unsafe_setmemory =
             VM_Version::has_VectorFacility() ? generate_unsafe_setmemory(StubRoutines::_jbyte_fill) : nullptr;

#endif // COMPILER2
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
  //  - load the function code in register fCode (== Z_R0).
  //  - load the data block length (depends on cipher function) into register srclen if requested.
  //  - is_decipher switches between cipher/decipher function codes
  //  - set_len requests (if true) loading the data block length in register srclen
  void generate_load_AES_fCode(Register keylen, Register fCode, Register srclen, bool is_decipher) {

    BLOCK_COMMENT("Set fCode {"); {
      Label fCode_set;
      int   mode = is_decipher ? VM_Version::CipherMode::decipher : VM_Version::CipherMode::cipher;
      bool  identical_dataBlk_len =  (VM_Version::Cipher::_AES128_dataBlk == VM_Version::Cipher::_AES192_dataBlk)
                                  && (VM_Version::Cipher::_AES128_dataBlk == VM_Version::Cipher::_AES256_dataBlk);
      // Expanded key length is 44/52/60 * 4 bytes for AES-128/AES-192/AES-256.
      __ z_cghi(keylen, 52); // Check only once at the beginning. keylen and fCode may share the same register.

      __ z_lghi(fCode, VM_Version::Cipher::_AES128 + mode);
      if (!identical_dataBlk_len) {
        __ z_lghi(srclen, VM_Version::Cipher::_AES128_dataBlk);
      }
      __ z_brl(fCode_set);  // keyLen <  52: AES128

      __ z_lghi(fCode, VM_Version::Cipher::_AES192 + mode);
      if (!identical_dataBlk_len) {
        __ z_lghi(srclen, VM_Version::Cipher::_AES192_dataBlk);
      }
      __ z_bre(fCode_set);  // keyLen == 52: AES192

      __ z_lghi(fCode, VM_Version::Cipher::_AES256 + mode);
      if (!identical_dataBlk_len) {
        __ z_lghi(srclen, VM_Version::Cipher::_AES256_dataBlk);
      }
      // __ z_brh(fCode_set);  // keyLen <  52: AES128           // fallthru

      __ bind(fCode_set);
      if (identical_dataBlk_len) {
        __ z_lghi(srclen, VM_Version::Cipher::_AES128_dataBlk);
      }
    }
    BLOCK_COMMENT("} Set fCode");
  }

  // Push a parameter block for the cipher/decipher instruction on the stack.
  // Layout of the additional stack space allocated for AES_cipherBlockChaining:
  //
  //   |        |
  //   +--------+ <-- SP before expansion
  //   |        |
  //   :        :  alignment loss (part 2), 0..(AES_parmBlk_align-1) bytes
  //   |        |
  //   +--------+
  //   |        |
  //   :        :  space for parameter block, size VM_Version::Cipher::_AES*_parmBlk_C
  //   |        |
  //   +--------+ <-- parmBlk, octoword-aligned, start of parameter block
  //   |        |
  //   :        :  additional stack space for spills etc., size AES_parmBlk_addspace, DW @ Z_SP not usable!!!
  //   |        |
  //   +--------+ <-- Z_SP + alignment loss, octoword-aligned
  //   |        |
  //   :        :  alignment loss (part 1), 0..(AES_parmBlk_align-1) bytes. DW @ Z_SP not usable!!!
  //   |        |
  //   +--------+ <-- Z_SP after expansion

  void generate_push_Block(int dataBlk_len, int parmBlk_len, int crypto_fCode,
                           Register parmBlk, Register keylen, Register fCode, Register cv, Register key) {

    AES_parmBlk_addspace = AES_parmBlk_align; // Must be multiple of AES_parmblk_align.
                                              // spill space for regs etc., don't use DW @SP!
    const int cv_len     = dataBlk_len;
    const int key_len    = parmBlk_len - cv_len;
    // This len must be known at JIT compile time. Only then are we able to recalc the SP before resize.
    // We buy this knowledge by wasting some (up to AES_parmBlk_align) bytes of stack space.
    const int resize_len = cv_len + key_len + AES_parmBlk_align + AES_parmBlk_addspace;

    // Use parmBlk as temp reg here to hold the frame pointer.
    __ resize_frame(-resize_len, parmBlk, true);

    // calculate parmBlk address from updated (resized) SP.
    __ add2reg(parmBlk, resize_len - (cv_len + key_len), Z_SP);
    __ z_nill(parmBlk, (~(AES_parmBlk_align-1)) & 0xffff); // Align parameter block.

    // There is room for stuff in the range [parmBlk-AES_parmBlk_addspace+8, parmBlk).
    __ z_stg(keylen,  -8, parmBlk);                        // Spill keylen for later use.

    // calculate (SP before resize) from updated SP.
    __ add2reg(keylen, resize_len, Z_SP);                  // keylen holds prev SP for now.
    __ z_stg(keylen, -16, parmBlk);                        // Spill prev SP for easy revert.

    __ z_mvc(0,      cv_len-1,  parmBlk, 0, cv);     // Copy cv.
    __ z_mvc(cv_len, key_len-1, parmBlk, 0, key);    // Copy key.
    __ z_lghi(fCode, crypto_fCode);
  }

  // NOTE:
  //   Before returning, the stub has to copy the chaining value from
  //   the parmBlk, where it was updated by the crypto instruction, back
  //   to the chaining value array the address of which was passed in the cv argument.
  //   As all the available registers are used and modified by KMC, we need to save
  //   the key length across the KMC instruction. We do so by spilling it to the stack,
  //   just preceding the parmBlk (at (parmBlk - 8)).
  void generate_push_parmBlk(Register keylen, Register fCode, Register parmBlk, Register key, Register cv, bool is_decipher) {
    int       mode = is_decipher ? VM_Version::CipherMode::decipher : VM_Version::CipherMode::cipher;
    Label     parmBlk_128, parmBlk_192, parmBlk_256, parmBlk_set;

    BLOCK_COMMENT("push parmBlk {");
    // We have just three cipher strengths which translates into three
    // possible extended key lengths: 44, 52, and 60 bytes.
    // We therefore can compare the actual length against the "middle" length
    // and get: lt -> len=44, eq -> len=52, gt -> len=60.
    __ z_cghi(keylen, 52);
    if (VM_Version::has_Crypto_AES128()) { __ z_brl(parmBlk_128); }  // keyLen <  52: AES128
    if (VM_Version::has_Crypto_AES192()) { __ z_bre(parmBlk_192); }  // keyLen == 52: AES192
    if (VM_Version::has_Crypto_AES256()) { __ z_brh(parmBlk_256); }  // keyLen >  52: AES256

    // Security net: requested AES function not available on this CPU.
    // NOTE:
    //   As of now (March 2015), this safety net is not required. JCE policy files limit the
    //   cryptographic strength of the keys used to 128 bit. If we have AES hardware support
    //   at all, we have at least AES-128.
    __ stop_static("AES key strength not supported by CPU. Use -XX:-UseAES as remedy.", 0);

    if (VM_Version::has_Crypto_AES256()) {
      __ bind(parmBlk_256);
      generate_push_Block(VM_Version::Cipher::_AES256_dataBlk,
                          VM_Version::Cipher::_AES256_parmBlk_C,
                          VM_Version::Cipher::_AES256 + mode,
                          parmBlk, keylen, fCode, cv, key);
      if (VM_Version::has_Crypto_AES128() || VM_Version::has_Crypto_AES192()) {
        __ z_bru(parmBlk_set);  // Fallthru otherwise.
      }
    }

    if (VM_Version::has_Crypto_AES192()) {
      __ bind(parmBlk_192);
      generate_push_Block(VM_Version::Cipher::_AES192_dataBlk,
                          VM_Version::Cipher::_AES192_parmBlk_C,
                          VM_Version::Cipher::_AES192 + mode,
                          parmBlk, keylen, fCode, cv, key);
      if (VM_Version::has_Crypto_AES128()) {
        __ z_bru(parmBlk_set);  // Fallthru otherwise.
      }
    }

    if (VM_Version::has_Crypto_AES128()) {
      __ bind(parmBlk_128);
      generate_push_Block(VM_Version::Cipher::_AES128_dataBlk,
                          VM_Version::Cipher::_AES128_parmBlk_C,
                          VM_Version::Cipher::_AES128 + mode,
                          parmBlk, keylen, fCode, cv, key);
      // Fallthru
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
    __ z_lg(Z_SP, -16, parmBlk); // Revert resize_frame_absolute. Z_SP saved by push_parmBlk.
    BLOCK_COMMENT("} pop parmBlk");
  }

  // Compute AES encrypt/decrypt function.
  void generate_AES_cipherBlock(bool is_decipher) {
    // Incoming arguments.
    Register       from    = Z_ARG1; // source byte array
    Register       to      = Z_ARG2; // destination byte array
    Register       key     = Z_ARG3; // expanded key array

    const Register keylen  = Z_R0;   // Temporarily (until fCode is set) holds the expanded key array length.

    // Register definitions as required by KM instruction.
    const Register fCode   = Z_R0;   // crypto function code
    const Register parmBlk = Z_R1;   // parameter block address (points to crypto key)
    const Register src     = Z_ARG1; // Must be even reg (KM requirement).
    const Register srclen  = Z_ARG2; // Must be odd reg and pair with src. Overwrites destination address.
    const Register dst     = Z_ARG3; // Must be even reg (KM requirement). Overwrites expanded key address.

    // Read key len of expanded key (in 4-byte words).
    __ z_lgf(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    // Copy arguments to registers as required by crypto instruction.
    __ z_lgr(parmBlk, key);          // crypto key (in T_INT array).
    __ lgr_if_needed(src, from);     // Copy src address. Will not emit, src/from are identical.
    __ z_lgr(dst, to);               // Copy dst address, even register required.

    // Construct function code into fCode(Z_R0), data block length into srclen(Z_ARG2).
    generate_load_AES_fCode(keylen, fCode, srclen, is_decipher);

    __ km(dst, src);                 // Cipher the message.

    __ z_br(Z_R14);
  }

  // Compute AES encrypt function.
  address generate_AES_encryptBlock() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_aescrypt_encryptBlock_id;
    StubCodeMark mark(this, stub_id);
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).

    generate_AES_cipherBlock(false);

    return __ addr_at(start_off);
  }

  // Compute AES decrypt function.
  address generate_AES_decryptBlock() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_aescrypt_decryptBlock_id;
    StubCodeMark mark(this, stub_id);
    unsigned int start_off = __ offset();  // Remember stub start address (is rtn value).

    generate_AES_cipherBlock(true);

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
  void generate_AES_cipherBlockChaining(bool is_decipher) {

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
    // Construct function code in fCode (Z_R0).
    generate_push_parmBlk(keylen, fCode, parmBlk, key, cv, is_decipher);

    // Prepare other registers for instruction.
    __ lgr_if_needed(src, from);     // Copy src address. Will not emit, src/from are identical.
    __ z_lgr(dst, to);
    __ z_llgfr(srclen, msglen);      // We pass the offsets as ints, not as longs as required.

    __ kmc(dst, src);                // Cipher the message.

    generate_pop_parmBlk(keylen, parmBlk, key, cv);

    __ z_llgfr(Z_RET, msglen);       // We pass the offsets as ints, not as longs as required.
    __ z_br(Z_R14);
  }

  // Compute chained AES encrypt function.
  address generate_cipherBlockChaining_AES_encrypt() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_cipherBlockChaining_encryptAESCrypt_id;
    StubCodeMark mark(this, stub_id);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    generate_AES_cipherBlockChaining(false);

    return __ addr_at(start_off);
  }

  // Compute chained AES decrypt function.
  address generate_cipherBlockChaining_AES_decrypt() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_cipherBlockChaining_decryptAESCrypt_id;
    StubCodeMark mark(this, stub_id);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    generate_AES_cipherBlockChaining(true);

    return __ addr_at(start_off);
  }


  // *****************************************************************************

  // AES CounterMode
  // Push a parameter block for the cipher/decipher instruction on the stack.
  // Layout of the additional stack space allocated for counterMode_AES_cipherBlock
  //
  //   |        |
  //   +--------+ <-- SP before expansion
  //   |        |
  //   :        :  alignment loss (part 2), 0..(AES_parmBlk_align-1) bytes.
  //   |        |
  //   +--------+ <-- gap = parmBlk + parmBlk_len + ctrArea_len
  //   |        |
  //   :        :  byte[] ctr - kmctr expects a counter vector the size of the input vector.
  //   :        :         The interface only provides byte[16] iv, the init vector.
  //   :        :         The size of this area is a tradeoff between stack space, init effort, and speed.
  //   |        |         Each counter is a 128bit int. Vector element [0] is a copy of iv.
  //   |        |         Vector element [i] is formed by incrementing element [i-1].
  //   +--------+ <-- ctr = parmBlk + parmBlk_len
  //   |        |
  //   :        :  space for parameter block, size VM_Version::Cipher::_AES*_parmBlk_G
  //   |        |
  //   +--------+ <-- parmBlk = Z_SP + (alignment loss (part 1+2)) + AES_dataBlk_space + AES_parmBlk_addSpace, octoword-aligned, start of parameter block
  //   |        |
  //   :        :  additional stack space for spills etc., min. size AES_parmBlk_addspace, all bytes usable.
  //   |        |
  //   +--------+ <-- Z_SP + alignment loss (part 1+2) + AES_dataBlk_space, octoword-aligned
  //   |        |
  //   :        :  space for one source data block and one dest data block.
  //   |        |
  //   +--------+ <-- Z_SP + alignment loss (part 1+2), octoword-aligned
  //   |        |
  //   :        :  additional alignment loss. Blocks above can't tolerate unusable DW @SP.
  //   |        |
  //   +--------+ <-- Z_SP + alignment loss (part 1), octoword-aligned
  //   |        |
  //   :        :  alignment loss (part 1), 0..(AES_parmBlk_align-1) bytes. DW @ Z_SP holds frame ptr.
  //   |        |
  //   +--------+ <-- Z_SP after expansion
  //
  //   additional space allocation (per DW):
  //    spillSpace = parmBlk - AES_parmBlk_addspace
  //    dataBlocks = spillSpace - AES_dataBlk_space
  //
  //    parmBlk-8  various fields of various lengths
  //               parmBlk-1: key_len (only one byte is stored at parmBlk-1)
  //               parmBlk-2: fCode (only one byte is stored at parmBlk-2)
  //               parmBlk-4: ctrVal_len (as retrieved from iv array), in bytes, as HW
  //               parmBlk-8: msglen length (in bytes) of crypto msg, as passed in by caller
  //                          return value is calculated from this: rv = msglen - processed.
  //    parmBlk-16 old_SP (SP before resize)
  //    parmBlk-24 temp values
  //                up to and including main loop in generate_counterMode_AES
  //                 - parmBlk-20: remmsg_len remaining msg len (aka unprocessed msg bytes)
  //                after main loop in generate_counterMode_AES
  //                 - parmBlk-24: spill slot for various address values
  //
  //    parmBlk-40 free spill slot, used for local spills.
  //    parmBlk-64 ARG2(dst) ptr spill slot
  //    parmBlk-56 ARG3(crypto key) ptr spill slot
  //    parmBlk-48 ARG4(icv value) ptr spill slot
  //
  //    parmBlk-72
  //    parmBlk-80
  //    parmBlk-88 counter vector current position
  //    parmBlk-96 reduced msg len (after preLoop processing)
  //
  //    parmBlk-104 Z_R13 spill slot (preLoop only)
  //    parmBlk-112 Z_R12 spill slot (preLoop only)
  //    parmBlk-120 Z_R11 spill slot (preLoop only)
  //    parmBlk-128 Z_R10 spill slot (preLoop only)
  //
  //
  // Layout of the parameter block (instruction KMCTR, function KMCTR-AES*
  //
  //   +--------+ key_len: +16 (AES-128), +24 (AES-192), +32 (AES-256)
  //   |        |
  //   |        |  cryptographic key
  //   |        |
  //   +--------+ <-- parmBlk
  //
  // On exit:
  //   Z_SP     points to resized frame
  //            Z_SP before resize available from -16(parmBlk)
  //   parmBlk  points to crypto instruction parameter block
  //            parameter block is filled with crypto key.
  //   msglen   unchanged, saved for later at -24(parmBlk)
  //   fCode    contains function code for instruction
  //   key      unchanged
  //
  void generate_counterMode_prepare_Stack(Register parmBlk, Register ctr, Register counter, Register scratch) {

    BLOCK_COMMENT("prepare stack counterMode_AESCrypt {");

    // save argument registers.
    //   ARG1(from) is Z_RET as well. Not saved or restored.
    //   ARG5(msglen) is restored by other means.
    __ z_stmg(Z_ARG2, Z_ARG4, argsave_offset,    parmBlk);

    assert(AES_ctrVec_len > 0, "sanity. We need a counter vector");
    __ add2reg(counter, AES_parmBlk_align, parmBlk);       // counter array is located behind crypto key. Available range is disp12 only.
    __ z_mvc(0, AES_ctrVal_len-1, counter, 0, ctr);        // move first copy of iv
    for (int j = 1; j < AES_ctrVec_len; j+=j) {            // j (and amount of moved data) doubles with every iteration
      int offset = j * AES_ctrVal_len;
      if (offset <= 256) {
        __ z_mvc(offset, offset-1, counter, 0, counter);   // move iv
      } else {
        for (int k = 0; k < offset; k += 256) {
          __ z_mvc(offset+k, 255, counter, 0, counter);
        }
      }
    }

    Label noCarry, done;
    __ z_lg(scratch, Address(ctr, 8));                     // get low-order DW of initial counter.
    __ z_algfi(scratch, AES_ctrVec_len);                   // check if we will overflow during init.
    __ z_brc(Assembler::bcondLogNoCarry, noCarry);         // No, 64-bit increment is sufficient.

    for (int j = 1; j < AES_ctrVec_len; j++) {             // start with j = 1; no need to add 0 to the first counter value.
      int offset = j * AES_ctrVal_len;
      generate_increment128(counter, offset, j, scratch);  // increment iv by index value
    }
    __ z_bru(done);

    __ bind(noCarry);
    for (int j = 1; j < AES_ctrVec_len; j++) {             // start with j = 1; no need to add 0 to the first counter value.
      int offset = j * AES_ctrVal_len;
      generate_increment64(counter, offset, j);            // increment iv by index value
    }

    __ bind(done);

    BLOCK_COMMENT("} prepare stack counterMode_AESCrypt");
  }


  void generate_counterMode_increment_ctrVector(Register parmBlk, Register counter, Register scratch, bool v0_only) {

    BLOCK_COMMENT("increment ctrVector counterMode_AESCrypt {");

    __ add2reg(counter, AES_parmBlk_align, parmBlk);       // ptr to counter array needs to be restored

    if (v0_only) {
      int offset = 0;
      generate_increment128(counter, offset, AES_ctrVec_len, scratch); // increment iv by # vector elements
    } else {
      int j = 0;
      if (VM_Version::has_VectorFacility()) {
        bool first_call = true;
        for (; j < (AES_ctrVec_len - 3); j+=4) {                       // increment blocks of 4 iv elements
          int offset = j * AES_ctrVal_len;
          generate_increment128x4(counter, offset, AES_ctrVec_len, first_call);
          first_call = false;
        }
      }
      for (; j < AES_ctrVec_len; j++) {
        int offset = j * AES_ctrVal_len;
        generate_increment128(counter, offset, AES_ctrVec_len, scratch); // increment iv by # vector elements
      }
    }

    BLOCK_COMMENT("} increment ctrVector counterMode_AESCrypt");
  }

  // IBM s390 (IBM z/Architecture, to be more exact) uses Big-Endian number representation.
  // Therefore, the bits are ordered from most significant to least significant. The address
  // of a number in memory points to its lowest location where the most significant bit is stored.
  void generate_increment64(Register counter, int offset, int increment) {
    __ z_algsi(offset + 8, counter, increment);            // increment, no overflow check
  }

  void generate_increment128(Register counter, int offset, int increment, Register scratch) {
    __ clear_reg(scratch);                                 // prepare to add carry to high-order DW
    __ z_algsi(offset + 8, counter, increment);            // increment low order DW
    __ z_alcg(scratch, Address(counter, offset));          // add carry to high-order DW
    __ z_stg(scratch, Address(counter, offset));           // store back
  }

  void generate_increment128(Register counter, int offset, Register increment, Register scratch) {
    __ clear_reg(scratch);                                 // prepare to add carry to high-order DW
    __ z_alg(increment, Address(counter, offset + 8));     // increment low order DW
    __ z_stg(increment, Address(counter, offset + 8));     // store back
    __ z_alcg(scratch, Address(counter, offset));          // add carry to high-order DW
    __ z_stg(scratch, Address(counter, offset));           // store back
  }

  // This is the vector variant of increment128, incrementing 4 ctr vector elements per call.
  void generate_increment128x4(Register counter, int offset, int increment, bool init) {
    VectorRegister Vincr      = Z_V16;
    VectorRegister Vctr0      = Z_V20;
    VectorRegister Vctr1      = Z_V21;
    VectorRegister Vctr2      = Z_V22;
    VectorRegister Vctr3      = Z_V23;

    // Initialize the increment value only once for a series of increments.
    // It must be assured that the non-initializing generator calls are
    // immediately subsequent. Otherwise, there is no guarantee for Vincr to be unchanged.
    if (init) {
      __ z_vzero(Vincr);                                   // preset VReg with constant increment
      __ z_vleih(Vincr, increment, 7);                     // rightmost HW has ix = 7
    }

    __ z_vlm(Vctr0, Vctr3, offset, counter);               // get the counter values
    __ z_vaq(Vctr0, Vctr0, Vincr);                         // increment them
    __ z_vaq(Vctr1, Vctr1, Vincr);
    __ z_vaq(Vctr2, Vctr2, Vincr);
    __ z_vaq(Vctr3, Vctr3, Vincr);
    __ z_vstm(Vctr0, Vctr3, offset, counter);              // store the counter values
  }

  unsigned int generate_counterMode_push_Block(int dataBlk_len, int parmBlk_len, int crypto_fCode,
                           Register parmBlk, Register msglen, Register fCode, Register key) {

    // space for data blocks (src and dst, one each) for partial block processing)
    AES_parmBlk_addspace = AES_stackSpace_incr             // spill space (temp data)
                         + AES_stackSpace_incr             // for argument save/restore
                         + AES_stackSpace_incr*2           // for work reg save/restore
                         ;
    AES_dataBlk_space    = roundup(2*dataBlk_len, AES_parmBlk_align);
    AES_dataBlk_offset   = -(AES_parmBlk_addspace+AES_dataBlk_space);
    const int key_len    = parmBlk_len;                    // The length of the unextended key (16, 24, 32)

    assert((AES_ctrVal_len == 0) || (AES_ctrVal_len == dataBlk_len), "varying dataBlk_len is not supported.");
    AES_ctrVal_len  = dataBlk_len;                         // ctr init value len (in bytes)
    AES_ctrArea_len = AES_ctrVec_len * AES_ctrVal_len;     // space required on stack for ctr vector

    // This len must be known at JIT compile time. Only then are we able to recalc the SP before resize.
    // We buy this knowledge by wasting some (up to AES_parmBlk_align) bytes of stack space.
    const int resize_len = AES_parmBlk_align               // room for alignment of parmBlk
                         + AES_parmBlk_align               // extra room for alignment
                         + AES_dataBlk_space               // one src and one dst data blk
                         + AES_parmBlk_addspace            // spill space for local data
                         + roundup(parmBlk_len, AES_parmBlk_align)  // aligned length of parmBlk
                         + AES_ctrArea_len                 // stack space for ctr vector
                         ;
    Register scratch     = fCode;  // We can use fCode as a scratch register. It's contents on entry
                                   // is irrelevant and it is set at the very end of this code block.

    assert(key_len < 256, "excessive crypto key len: %d, limit: 256", key_len);

    BLOCK_COMMENT(err_msg("push_Block (%d bytes) counterMode_AESCrypt%d {", resize_len, parmBlk_len*8));

    // After the frame is resized, the parmBlk is positioned such
    // that it is octoword-aligned. This potentially creates some
    // alignment waste in addspace and/or in the gap area.
    // After resize_frame, scratch contains the frame pointer.
    __ resize_frame(-resize_len, scratch, true);
#ifdef ASSERT
    __ clear_mem(Address(Z_SP, (intptr_t)8), resize_len - 8);
#endif

    // calculate aligned parmBlk address from updated (resized) SP.
    __ add2reg(parmBlk, AES_parmBlk_addspace + AES_dataBlk_space + (2*AES_parmBlk_align-1), Z_SP);
    __ z_nill(parmBlk, (~(AES_parmBlk_align-1)) & 0xffff); // Align parameter block.

    // There is room to spill stuff in the range [parmBlk-AES_parmBlk_addspace+8, parmBlk).
    __ z_mviy(keylen_offset, parmBlk, key_len - 1);        // Spill crypto key length for later use. Decrement by one for direct use with xc template.
    __ z_mviy(fCode_offset,  parmBlk, crypto_fCode);       // Crypto function code, will be loaded into Z_R0 later.
    __ z_sty(msglen, msglen_offset, parmBlk);              // full plaintext/ciphertext len.
    __ z_sty(msglen, msglen_red_offset, parmBlk);          // save for main loop, may get updated in preLoop.
    __ z_sra(msglen, exact_log2(dataBlk_len));             // # full cipher blocks that can be formed from input text.
    __ z_sty(msglen, rem_msgblk_offset, parmBlk);

    __ add2reg(scratch, resize_len, Z_SP);                 // calculate (SP before resize) from resized SP.
    __ z_stg(scratch, unextSP_offset, parmBlk);            // Spill unextended SP for easy revert.
    __ z_stmg(Z_R10, Z_R13, regsave_offset, parmBlk);      // make some regs available as work registers

    // Fill parmBlk with all required data
    __ z_mvc(0, key_len-1, parmBlk, 0, key);               // Copy key. Need to do it here - key_len is only known here.
    BLOCK_COMMENT(err_msg("} push_Block (%d bytes) counterMode_AESCrypt%d", resize_len, parmBlk_len*8));
    return resize_len;
  }


  void generate_counterMode_pop_Block(Register parmBlk, Register msglen, Label& eraser) {
    // For added safety, clear the stack area where the crypto key was stored.
    Register scratch = msglen;
    assert_different_registers(scratch, Z_R0);             // can't use Z_R0 for exrl.

    // wipe out key on stack
    __ z_llgc(scratch, keylen_offset, parmBlk);            // get saved (key_len-1) value (we saved just one byte!)
    __ z_exrl(scratch, eraser);                            // template relies on parmBlk still pointing to key on stack

    // restore argument registers.
    //   ARG1(from) is Z_RET as well. Not restored - will hold return value anyway.
    //   ARG5(msglen) is restored further down.
    __ z_lmg(Z_ARG2, Z_ARG4, argsave_offset,    parmBlk);

    // restore work registers
    __ z_lmg(Z_R10, Z_R13, regsave_offset, parmBlk);       // make some regs available as work registers

    __ z_lgf(msglen, msglen_offset,  parmBlk);             // Restore msglen, only low order FW is valid
#ifdef ASSERT
    {
      Label skip2last, skip2done;
      // Z_RET (aka Z_R2) can be used as scratch as well. It will be set from msglen before return.
      __ z_lgr(Z_RET, Z_SP);                                 // save extended SP
      __ z_lg(Z_SP,    unextSP_offset, parmBlk);             // trim stack back to unextended size
      __ z_sgrk(Z_R1, Z_SP, Z_RET);

      __ z_cghi(Z_R1, 256);
      __ z_brl(skip2last);
      __ z_xc(0, 255, Z_RET, 0, Z_RET);
      __ z_aghi(Z_RET, 256);
      __ z_aghi(Z_R1, -256);

      __ z_cghi(Z_R1, 256);
      __ z_brl(skip2last);
      __ z_xc(0, 255, Z_RET, 0, Z_RET);
      __ z_aghi(Z_RET, 256);
      __ z_aghi(Z_R1, -256);

      __ z_cghi(Z_R1, 256);
      __ z_brl(skip2last);
      __ z_xc(0, 255, Z_RET, 0, Z_RET);
      __ z_aghi(Z_RET, 256);
      __ z_aghi(Z_R1, -256);

      __ bind(skip2last);
      __ z_lgr(Z_R0, Z_RET);
      __ z_aghik(Z_RET, Z_R1, -1);  // decrement for exrl
      __ z_brl(skip2done);
      __ z_lgr(parmBlk, Z_R0);      // parmBlk == Z_R1, used in eraser template
      __ z_exrl(Z_RET, eraser);

      __ bind(skip2done);
    }
#else
    __ z_lg(Z_SP,    unextSP_offset, parmBlk);             // trim stack back to unextended size
#endif
  }


  int generate_counterMode_push_parmBlk(Register parmBlk, Register msglen, Register fCode, Register key, bool is_decipher) {
    int       resize_len = 0;
    int       mode = is_decipher ? VM_Version::CipherMode::decipher : VM_Version::CipherMode::cipher;
    Label     parmBlk_128, parmBlk_192, parmBlk_256, parmBlk_set;
    Register  keylen = fCode;      // Expanded key length, as read from key array, Temp only.
                                   // use fCode as scratch; fCode receives its final value later.

    // Read key len of expanded key (in 4-byte words).
    __ z_lgf(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));
    __ z_cghi(keylen, 52);
    if (VM_Version::has_Crypto_AES_CTR256()) { __ z_brh(parmBlk_256); }  // keyLen >  52: AES256. Assume: most frequent
    if (VM_Version::has_Crypto_AES_CTR128()) { __ z_brl(parmBlk_128); }  // keyLen <  52: AES128.
    if (VM_Version::has_Crypto_AES_CTR192()) { __ z_bre(parmBlk_192); }  // keyLen == 52: AES192. Assume: least frequent

    // Safety net: requested AES_CTR function for requested keylen not available on this CPU.
    __ stop_static("AES key strength not supported by CPU. Use -XX:-UseAESCTRIntrinsics as remedy.", 0);

    if (VM_Version::has_Crypto_AES_CTR128()) {
      __ bind(parmBlk_128);
      resize_len = generate_counterMode_push_Block(VM_Version::Cipher::_AES128_dataBlk,
                          VM_Version::Cipher::_AES128_parmBlk_G,
                          VM_Version::Cipher::_AES128 + mode,
                          parmBlk, msglen, fCode, key);
      if (VM_Version::has_Crypto_AES_CTR256() || VM_Version::has_Crypto_AES_CTR192()) {
        __ z_bru(parmBlk_set);  // Fallthru otherwise.
      }
    }

    if (VM_Version::has_Crypto_AES_CTR192()) {
      __ bind(parmBlk_192);
      resize_len = generate_counterMode_push_Block(VM_Version::Cipher::_AES192_dataBlk,
                          VM_Version::Cipher::_AES192_parmBlk_G,
                          VM_Version::Cipher::_AES192 + mode,
                          parmBlk, msglen, fCode, key);
      if (VM_Version::has_Crypto_AES_CTR256()) {
        __ z_bru(parmBlk_set);  // Fallthru otherwise.
      }
    }

    if (VM_Version::has_Crypto_AES_CTR256()) {
      __ bind(parmBlk_256);
      resize_len = generate_counterMode_push_Block(VM_Version::Cipher::_AES256_dataBlk,
                          VM_Version::Cipher::_AES256_parmBlk_G,
                          VM_Version::Cipher::_AES256 + mode,
                          parmBlk, msglen, fCode, key);
      // Fallthru
    }

    __ bind(parmBlk_set);
    return resize_len;
  }


  void generate_counterMode_pop_parmBlk(Register parmBlk, Register msglen, Label& eraser) {

    BLOCK_COMMENT("pop parmBlk counterMode_AESCrypt {");

    generate_counterMode_pop_Block(parmBlk, msglen, eraser);

    BLOCK_COMMENT("} pop parmBlk counterMode_AESCrypt");
  }

  // Implementation of counter-mode AES encrypt/decrypt function.
  //
  void generate_counterMode_AES_impl(bool is_decipher) {

    // On entry:
    // if there was a previous call to update(), and this previous call did not fully use
    // the current encrypted counter, that counter is available at arg6_Offset(Z_SP).
    // The index of the first unused bayte in the encrypted counter is available at arg7_Offset(Z_SP).
    // The index is in the range [1..AES_ctrVal_len] ([1..16]), where index == 16 indicates a fully
    // used previous encrypted counter.
    // The unencrypted counter has already been incremented and is ready to be used for the next
    // data block, after the unused bytes from the previous call have been consumed.
    // The unencrypted counter follows the "increment-after use" principle.

    // On exit:
    // The index of the first unused byte of the encrypted counter is written back to arg7_Offset(Z_SP).
    // A value of AES_ctrVal_len (16) indicates there is no leftover byte.
    // If there is at least one leftover byte (1 <= index < AES_ctrVal_len), the encrypted counter value
    // is written back to arg6_Offset(Z_SP). If there is no leftover, nothing is written back.
    // The unencrypted counter value is written back after having been incremented.

    Register       from    = Z_ARG1; // byte[], source byte array (clear text)
    Register       to      = Z_ARG2; // byte[], destination byte array (ciphered)
    Register       key     = Z_ARG3; // byte[], expanded key array.
    Register       ctr     = Z_ARG4; // byte[], counter byte array.
    const Register msglen  = Z_ARG5; // int, Total length of the msg to be encrypted. Value must be
                                     // returned in Z_RET upon completion of this stub.
                                     // This is a jint. Negative values are illegal, but technically possible.
                                     // Do not rely on high word. Contents is undefined.
               // encCtr   = Z_ARG6  - encrypted counter (byte array),
               //                      address passed on stack at _z_abi(remaining_cargs) + 0 * WordSize
               // cvIndex  = Z_ARG7  - # used (consumed) bytes of encrypted counter,
               //                      passed on stack at _z_abi(remaining_cargs) + 1 * WordSize
               //                      Caution:4-byte value, right-justified in 8-byte stack word

    const Register fCode   = Z_R0;   // crypto function code
    const Register parmBlk = Z_R1;   // parameter block address (points to crypto key)
    const Register src     = Z_ARG1; // is Z_R2, forms even/odd pair with srclen
    const Register srclen  = Z_ARG2; // Overwrites destination address.
    const Register dst     = Z_ARG3; // Overwrites key address.
    const Register counter = Z_ARG5; // Overwrites msglen. Must have counter array in an even register.

    Label srcMover, dstMover, fromMover, ctrXOR, dataEraser;  // EXRL (execution) templates.
    Label CryptoLoop, CryptoLoop_doit, CryptoLoop_end, CryptoLoop_setupAndDoLast, CryptoLoop_ctrVal_inc;
    Label allDone, allDone_noInc, popAndExit, Exit;

    int    arg6_Offset = _z_abi(remaining_cargs) + 0 * HeapWordSize;
    int    arg7_Offset = _z_abi(remaining_cargs) + 1 * HeapWordSize; // stack slot holds ptr to int value
    int   oldSP_Offset = 0;

    // Is there anything to do at all? Protect against negative len as well.
    __ z_ltr(msglen, msglen);
    __ z_brnh(Exit);

    // Expand stack, load parm block address into parmBlk (== Z_R1), copy crypto key to parm block.
    oldSP_Offset = generate_counterMode_push_parmBlk(parmBlk, msglen, fCode, key, is_decipher);
    arg6_Offset += oldSP_Offset;
    arg7_Offset += oldSP_Offset;

    // Check if there is a leftover, partially used encrypted counter from last invocation.
    // If so, use those leftover counter bytes first before starting the "normal" encryption.

    // We do not have access to the encrypted counter value. It is generated and used only
    // internally within the previous kmctr instruction. But, at the end of call to this stub,
    // the last encrypted couner is extracted by ciphering a 0x00 byte stream. The result is
    // stored at the arg6 location for use with the subsequent call.
    //
    // The #used bytes of the encrypted counter (from a previous call) is provided via arg7.
    // It is used as index into the encrypted counter to access the first byte availabla for ciphering.
    // To cipher the input text, we move the number of remaining bytes in the encrypted counter from
    // input to output. Then we simply XOR the output bytes with the associated encrypted counter bytes.

    Register cvIxAddr  = Z_R10;                  // Address of index into encCtr. Preserved for use @CryptoLoop_end.
    __ z_lg(cvIxAddr, arg7_Offset, Z_SP);        // arg7: addr of field encCTR_index.

    {
      Register cvUnused  = Z_R11;                // # unused bytes of encrypted counter value (= 16 - cvIndex)
      Register encCtr    = Z_R12;                // encrypted counter value, points to first ununsed byte.
      Register cvIndex   = Z_R13;                // # index of first unused byte of encrypted counter value
      Label    preLoop_end;

      // preLoop is necessary only if there is a partially used encrypted counter (encCtr).
      // Partially used means cvIndex is in [1, dataBlk_len-1].
      // cvIndex == 0:           encCtr is set up but not used at all. Should not occur.
      // cvIndex == dataBlk_len: encCtr is exhausted, all bytes used.
      // Using unsigned compare protects against cases where (cvIndex < 0).
      __ z_clfhsi(0, cvIxAddr, AES_ctrVal_len);  // check #used bytes in encCtr against ctr len.
      __ z_brnl(preLoop_end);                    // if encCtr is fully used, skip to normal processing.
      __ z_ltgf(cvIndex, 0, Z_R0, cvIxAddr);     // # used bytes in encCTR.
      __ z_brz(preLoop_end);                     // if encCtr has no used bytes, skip to normal processing.

      __ z_lg(encCtr, arg6_Offset, Z_SP);        // encrypted counter from last call to update()
      __ z_agr(encCtr, cvIndex);                 // now points to first unused byte

      __ add2reg(cvUnused, -AES_ctrVal_len, cvIndex); // calculate #unused bytes in encCtr.
      __ z_lcgr(cvUnused, cvUnused);             // previous checks ensure cvUnused in range [1, dataBlk_len-1]

      __ z_lgf(msglen, msglen_offset, parmBlk);  // Restore msglen (jint value)
      __ z_cr(cvUnused, msglen);                 // check if msg can consume all unused encCtr bytes
      __ z_locr(cvUnused, msglen, Assembler::bcondHigh); // take the shorter length
      __ z_aghi(cvUnused, -1);                   // decrement # unused bytes by 1 for exrl instruction
                                                 // preceding checks ensure cvUnused in range [1, dataBlk_len-1]
      __ z_exrl(cvUnused, fromMover);
      __ z_exrl(cvUnused, ctrXOR);

      __ z_aghi(cvUnused, 1);                    // revert decrement from above
      __ z_agr(cvIndex, cvUnused);               // update index into encCtr (first unused byte)
      __ z_st(cvIndex, 0, cvIxAddr);             // write back arg7, cvIxAddr is still valid

      // update pointers and counters to prepare for main loop
      __ z_agr(from, cvUnused);
      __ z_agr(to, cvUnused);
      __ z_sr(msglen, cvUnused);                 // #bytes not yet processed
      __ z_sty(msglen, msglen_red_offset, parmBlk); // save for calculations in main loop
      __ z_srak(Z_R0, msglen, exact_log2(AES_ctrVal_len));// # full cipher blocks that can be formed from input text.
      __ z_sty(Z_R0, rem_msgblk_offset, parmBlk);

      // check remaining msglen. If zero, all msg bytes were processed in preLoop.
      __ z_ltr(msglen, msglen);
      __ z_brnh(popAndExit);

      __ bind(preLoop_end);
    }

    // Create count vector on stack to accommodate up to AES_ctrVec_len blocks.
    generate_counterMode_prepare_Stack(parmBlk, ctr, counter, fCode);

    // Prepare other registers for instruction.
    __ lgr_if_needed(src, from);     // Copy src address. Will not emit, src/from are identical.
    __ z_lgr(dst, to);
    __ z_llgc(fCode, fCode_offset, Z_R0, parmBlk);

    __ bind(CryptoLoop);
      __ z_lghi(srclen, AES_ctrArea_len);                     // preset len (#bytes) for next iteration: max possible.
      __ z_asi(rem_msgblk_offset, parmBlk, -AES_ctrVec_len);  // decrement #remaining blocks (16 bytes each). Range: [+127..-128]
      __ z_brl(CryptoLoop_setupAndDoLast);                    // Handling the last iteration (using less than max #blocks) out-of-line

      __ bind(CryptoLoop_doit);
      __ kmctr(dst, counter, src);   // Cipher the message.

      __ z_lt(srclen, rem_msgblk_offset, Z_R0, parmBlk);      // check if this was the last iteration
      __ z_brz(CryptoLoop_ctrVal_inc);                        // == 0: ctrVector fully used. Need to increment the first
                                                              //       vector element to encrypt remaining unprocessed bytes.
//    __ z_brl(CryptoLoop_end);                               //  < 0: this was detected before and handled at CryptoLoop_setupAndDoLast
                                                              //  > 0: this is the fallthru case, need another iteration

      generate_counterMode_increment_ctrVector(parmBlk, counter, srclen, false); // srclen unused here (serves as scratch)
      __ z_bru(CryptoLoop);

    __ bind(CryptoLoop_end);

    // OK, when we arrive here, we have encrypted all of the "from" byte stream
    // except for the last few [0..dataBlk_len) bytes. In addition, we know that
    // there are no more unused bytes in the previously generated encrypted counter.
    // The (unencrypted) counter, however, is ready to use (it was incremented before).

    // To encrypt the few remaining bytes, we need to form an extra src and dst
    // data block of dataBlk_len each. This is because we can only process full
    // blocks but we must not read or write beyond the boundaries of the argument
    // arrays. Here is what we do:
    //  - The ctrVector has at least one unused element. This is ensured by CryptoLoop code.
    //  - The (first) unused element is pointed at by the counter register.
    //  - The src data block is filled with the remaining "from" bytes, remainder of block undefined.
    //  - The single src data block is encrypted into the dst data block.
    //  - The dst data block is copied into the "to" array, but only the leftmost few bytes
    //    (as many as were left in the source byte stream).
    //  - The counter value to be used is pointed at by the counter register.
    //  - Fortunately, the crypto instruction (kmctr) has updated all related addresses such that
    //    we know where to continue with "from" and "to" and which counter value to use next.

    Register encCtr    = Z_R12;  // encrypted counter value, points to stub argument.
    Register tmpDst    = Z_R12;  // addr of temp destination (for last partial block encryption)

    __ z_lgf(srclen, msglen_red_offset, parmBlk);          // plaintext/ciphertext len after potential preLoop processing.
    __ z_nilf(srclen, AES_ctrVal_len - 1);                 // those rightmost bits indicate the unprocessed #bytes
    __ z_stg(srclen, localSpill_offset, parmBlk);          // save for later reuse
    __ z_mvhi(0, cvIxAddr, 16);                            // write back arg7 (default 16 in case of allDone).
    __ z_braz(allDone_noInc);                              // no unprocessed bytes? Then we are done.
                                                           // This also means the last block of data processed was
                                                           // a full-sized block (AES_ctrVal_len bytes) which results
                                                           // in no leftover encrypted counter bytes.
    __ z_st(srclen, 0, cvIxAddr);                          // This will be the index of the first unused byte in the encrypted counter.
    __ z_stg(counter, counter_offset, parmBlk);            // save counter location for easy later restore

    // calculate address (on stack) for final dst and src blocks.
    __ add2reg(tmpDst, AES_dataBlk_offset, parmBlk);       // tmp dst (on stack) is right before tmp src

    // We have a residue of [1..15] unprocessed bytes, srclen holds the exact number.
    // Residue == 0 was checked just above, residue == AES_ctrVal_len would be another
    // full-sized block and would have been handled by CryptoLoop.

    __ add2reg(srclen, -1);                                // decrement for exrl
    __ z_exrl(srclen, srcMover);                           // copy remaining bytes of src byte stream
    __ load_const_optimized(srclen, AES_ctrVal_len);       // kmctr processes only complete blocks
    __ add2reg(src, AES_ctrVal_len, tmpDst);               // tmp dst is right before tmp src

    __ kmctr(tmpDst, counter, src);                        // Cipher the remaining bytes.

    __ add2reg(tmpDst, -AES_ctrVal_len, tmpDst);           // restore tmp dst address
    __ z_lg(srclen, localSpill_offset, parmBlk);           // residual len, saved above
    __ add2reg(srclen, -1);                                // decrement for exrl
    __ z_exrl(srclen, dstMover);

    // Write back new encrypted counter
    __ add2reg(src, AES_dataBlk_offset, parmBlk);
    __ clear_mem(Address(src, RegisterOrConstant((intptr_t)0)), AES_ctrVal_len);
    __ load_const_optimized(srclen, AES_ctrVal_len);       // kmctr processes only complete blocks
    __ z_lg(encCtr, arg6_Offset, Z_SP);                    // write encrypted counter to arg6
    __ z_lg(counter, counter_offset, parmBlk);             // restore counter
    __ kmctr(encCtr, counter, src);

    // The last used element of the counter vector contains the latest counter value that was used.
    // As described above, the counter value on exit must be the one to be used next.
    __ bind(allDone);
    __ z_lg(counter, counter_offset, parmBlk);             // restore counter
    generate_increment128(counter, 0, 1, Z_R0);

    __ bind(allDone_noInc);
    __ z_mvc(0, AES_ctrVal_len, ctr, 0, counter);

    __ bind(popAndExit);
    generate_counterMode_pop_parmBlk(parmBlk, msglen, dataEraser);

    __ bind(Exit);
    __ z_lgfr(Z_RET, msglen);

    __ z_br(Z_R14);

    //----------------------------
    //---<  out-of-line code  >---
    //----------------------------
    __ bind(CryptoLoop_setupAndDoLast);
      __ z_lgf(srclen, rem_msgblk_offset, parmBlk);           // remaining #blocks in memory is < 0
      __ z_aghi(srclen, AES_ctrVec_len);                      // recalculate the actually remaining #blocks
      __ z_sllg(srclen, srclen, exact_log2(AES_ctrVal_len));  // convert to #bytes. Counter value is same length as data block
      __ kmctr(dst, counter, src);                            // Cipher the last integral blocks of the message.
      __ z_bru(CryptoLoop_end);                               // There is at least one unused counter vector element.
                                                              // no need to increment.

    __ bind(CryptoLoop_ctrVal_inc);
      generate_counterMode_increment_ctrVector(parmBlk, counter, srclen, true); // srclen unused here (serves as scratch)
      __ z_bru(CryptoLoop_end);

    //-------------------------------------------
    //---<  execution templates for preLoop  >---
    //-------------------------------------------
    __ bind(fromMover);
    __ z_mvc(0, 0, to, 0, from);               // Template instruction to move input data to dst.
    __ bind(ctrXOR);
    __ z_xc(0,  0, to, 0, encCtr);             // Template instruction to XOR input data (now in to) with encrypted counter.

    //-------------------------------
    //---<  execution templates  >---
    //-------------------------------
    __ bind(dataEraser);
    __ z_xc(0, 0, parmBlk, 0, parmBlk);  // Template instruction to erase crypto key on stack.
    __ bind(dstMover);
    __ z_mvc(0, 0, dst, 0, tmpDst);      // Template instruction to move encrypted reminder from stack to dst.
    __ bind(srcMover);
    __ z_mvc(AES_ctrVal_len, 0, tmpDst, 0, src); // Template instruction to move reminder of source byte stream to stack.
  }


  // Create two intrinsic variants, optimized for short and long plaintexts.
  void generate_counterMode_AES(bool is_decipher) {

    const Register msglen  = Z_ARG5;    // int, Total length of the msg to be encrypted. Value must be
                                        // returned in Z_RET upon completion of this stub.
    const int threshold = 256;          // above this length (in bytes), text is considered long.
    const int vec_short = threshold>>6; // that many blocks (16 bytes each) per iteration, max 4 loop iterations
    const int vec_long  = threshold>>2; // that many blocks (16 bytes each) per iteration.

    Label AESCTR_short, AESCTR_long;

    __ z_chi(msglen, threshold);
    __ z_brh(AESCTR_long);

    __ bind(AESCTR_short);

    BLOCK_COMMENT(err_msg("counterMode_AESCrypt (text len <= %d, block size = %d) {", threshold, vec_short*16));

    AES_ctrVec_len = vec_short;
    generate_counterMode_AES_impl(false);   // control of generated code will not return

    BLOCK_COMMENT(err_msg("} counterMode_AESCrypt (text len <= %d, block size = %d)", threshold, vec_short*16));

    __ align(32); // Octoword alignment benefits branch targets.

    BLOCK_COMMENT(err_msg("counterMode_AESCrypt (text len > %d, block size = %d) {", threshold, vec_long*16));

    __ bind(AESCTR_long);
    AES_ctrVec_len = vec_long;
    generate_counterMode_AES_impl(false);   // control of generated code will not return

    BLOCK_COMMENT(err_msg("} counterMode_AESCrypt (text len > %d, block size = %d)", threshold, vec_long*16));
  }


  // Compute AES-CTR crypto function.
  // Encrypt or decrypt is selected via parameters. Only one stub is necessary.
  address generate_counterMode_AESCrypt() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_counterMode_AESCrypt_id;
    StubCodeMark mark(this, stub_id);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    generate_counterMode_AES(false);

    return __ addr_at(start_off);
  }

// *****************************************************************************

  // Compute GHASH function.
  address generate_ghash_processBlocks() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_ghash_processBlocks_id;
    StubCodeMark mark(this, stub_id);
    unsigned int start_off = __ offset();   // Remember stub start address (is rtn value).

    const Register state   = Z_ARG1;
    const Register subkeyH = Z_ARG2;
    const Register data    = Z_ARG3; // 1st of even-odd register pair.
    const Register blocks  = Z_ARG4;
    const Register len     = blocks; // 2nd of even-odd register pair.

    const int param_block_size = 4 * 8;
    const int frame_resize = param_block_size + 8; // Extra space for copy of fp.

    // Reserve stack space for parameter block (R1).
    __ z_lgr(Z_R1, Z_SP);
    __ resize_frame(-frame_resize, Z_R0, true);
    __ z_aghi(Z_R1, -param_block_size);

    // Fill parameter block.
    __ z_mvc(Address(Z_R1)    , Address(state)  , 16);
    __ z_mvc(Address(Z_R1, 16), Address(subkeyH), 16);

    // R4+5: data pointer + length
    __ z_llgfr(len, blocks);  // Cast to 64-bit.

    // R0: function code
    __ load_const_optimized(Z_R0, (int)VM_Version::MsgDigest::_GHASH);

    // Compute.
    __ z_sllg(len, len, 4);  // In bytes.
    __ kimd(data);

    // Copy back result and free parameter block.
    __ z_mvc(Address(state), Address(Z_R1), 16);
    __ z_xc(Address(Z_R1), param_block_size, Address(Z_R1));
    __ z_aghi(Z_SP, frame_resize);

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
  address generate_SHA1_stub(StubId stub_id) {
    bool multiBlock;
    switch (stub_id) {
    case StubId::stubgen_sha1_implCompress_id:
      multiBlock = false;
      break;
    case StubId::stubgen_sha1_implCompressMB_id:
      multiBlock = true;
      break;
    default:
      ShouldNotReachHere();
    }
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
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
      // spanning the range between [srcOff(Z_ARG3), srcLimit(Z_ARG4)). As detailed above,
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
  address generate_SHA256_stub(StubId stub_id) {
    bool multiBlock;
    switch (stub_id) {
    case StubId::stubgen_sha256_implCompress_id:
      multiBlock = false;
      break;
    case StubId::stubgen_sha256_implCompressMB_id:
      multiBlock = true;
      break;
    default:
      ShouldNotReachHere();
    }
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
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
      // spanning the range between [srcOff(Z_ARG3), srcLimit(Z_ARG4)). As detailed above,
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
  address generate_SHA512_stub(StubId stub_id) {
    bool multiBlock;
    switch (stub_id) {
    case StubId::stubgen_sha512_implCompress_id:
      multiBlock = false;
      break;
    case StubId::stubgen_sha512_implCompressMB_id:
      multiBlock = true;
      break;
    default:
      ShouldNotReachHere();
    }
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
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
      // spanning the range between [srcOff(Z_ARG3), srcLimit(Z_ARG4)). As detailed above,
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


  /**
   *  Arguments:
   *
   * Inputs:
   *   Z_ARG1    - int   crc
   *   Z_ARG2    - byte* buf
   *   Z_ARG3    - int   length (of buffer)
   *
   * Result:
   *   Z_RET     - int   crc result
   **/
  // Compute CRC function (generic, for all polynomials).
  void generate_CRC_updateBytes(Register table, bool invertCRC) {

    // arguments to kernel_crc32:
    Register       crc     = Z_ARG1;  // Current checksum, preset by caller or result from previous call, int.
    Register       data    = Z_ARG2;  // source byte array
    Register       dataLen = Z_ARG3;  // #bytes to process, int
//    Register       table   = Z_ARG4;  // crc table address. Preloaded and passed in by caller.
    const Register t0      = Z_R10;   // work reg for kernel* emitters
    const Register t1      = Z_R11;   // work reg for kernel* emitters
    const Register t2      = Z_R12;   // work reg for kernel* emitters
    const Register t3      = Z_R13;   // work reg for kernel* emitters


    assert_different_registers(crc, data, dataLen, table);

    // We pass these values as ints, not as longs as required by C calling convention.
    // Crc used as int.
    __ z_llgfr(dataLen, dataLen);

    __ resize_frame(-(6*8), Z_R0, true); // Resize frame to provide add'l space to spill 5 registers.
    __ z_stmg(Z_R10, Z_R13, 1*8, Z_SP);  // Spill regs 10..11 to make them available as work registers.
    __ kernel_crc32_1word(crc, data, dataLen, table, t0, t1, t2, t3, invertCRC);
    __ z_lmg(Z_R10, Z_R13, 1*8, Z_SP);   // Spill regs 10..11 back from stack.
    __ resize_frame(+(6*8), Z_R0, true); // Resize frame to provide add'l space to spill 5 registers.

    __ z_llgfr(Z_RET, crc);  // Updated crc is function result. No copying required, just zero upper 32 bits.
    __ z_br(Z_R14);          // Result already in Z_RET == Z_ARG1.
  }


  // Compute CRC32 function.
  address generate_CRC32_updateBytes() {
    __ align(CodeEntryAlignment);
    StubId stub_id =  StubId::stubgen_updateBytesCRC32_id;
    StubCodeMark mark(this, stub_id);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    assert(UseCRC32Intrinsics, "should not generate this stub (%s) with CRC32 intrinsics disabled", StubRoutines::get_stub_name(stub_id));

    BLOCK_COMMENT("CRC32_updateBytes {");
    Register       table   = Z_ARG4;  // crc32 table address.
    StubRoutines::zarch::generate_load_crc_table_addr(_masm, table);

    generate_CRC_updateBytes(table, true);
    BLOCK_COMMENT("} CRC32_updateBytes");

    return __ addr_at(start_off);
  }


  // Compute CRC32C function.
  address generate_CRC32C_updateBytes() {
    __ align(CodeEntryAlignment);
    StubId stub_id =  StubId::stubgen_updateBytesCRC32C_id;
    StubCodeMark mark(this, stub_id);
    unsigned int   start_off = __ offset();  // Remember stub start address (is rtn value).

    assert(UseCRC32CIntrinsics, "should not generate this stub (%s) with CRC32C intrinsics disabled", StubRoutines::get_stub_name(stub_id));

    BLOCK_COMMENT("CRC32C_updateBytes {");
    Register       table   = Z_ARG4;  // crc32c table address.
    StubRoutines::zarch::generate_load_crc32c_table_addr(_masm, table);

    generate_CRC_updateBytes(table, false);
    BLOCK_COMMENT("} CRC32C_updateBytes");

    return __ addr_at(start_off);
  }


  // Arguments:
  //   Z_ARG1    - x address
  //   Z_ARG2    - x length
  //   Z_ARG3    - y address
  //   Z_ARG4    - y length
  //   Z_ARG5    - z address
  address generate_multiplyToLen() {
    __ align(CodeEntryAlignment);
    StubId stub_id =  StubId::stubgen_multiplyToLen_id;
    StubCodeMark mark(this, stub_id);

    address start = __ pc();

    const Register x    = Z_ARG1;
    const Register xlen = Z_ARG2;
    const Register y    = Z_ARG3;
    const Register ylen = Z_ARG4;
    const Register z    = Z_ARG5;

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

  address generate_method_entry_barrier() {
    __ align(CodeEntryAlignment);
    StubId stub_id =  StubId::stubgen_method_entry_barrier_id;
    StubCodeMark mark(this, stub_id);

    address start = __ pc();

    int nbytes_volatile = (8 + 5) * BytesPerWord;

    // VM-Call Prologue
    __ save_return_pc();
    __ push_frame_abi160(nbytes_volatile);
    __ save_volatile_regs(Z_SP, frame::z_abi_160_size, true, false);

    // Prep arg for VM call
    // Create ptr to stored return_pc in caller frame.
    __ z_la(Z_ARG1, _z_abi(return_pc) + frame::z_abi_160_size + nbytes_volatile, Z_R0, Z_SP);

    // VM-Call: BarrierSetNMethod::nmethod_stub_entry_barrier(address* return_address_ptr)
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, BarrierSetNMethod::nmethod_stub_entry_barrier));
    __ z_ltr(Z_RET, Z_RET);

    // VM-Call Epilogue
    __ restore_volatile_regs(Z_SP, frame::z_abi_160_size, true, false);
    __ pop_frame();
    __ restore_return_pc();

    // Check return val of VM-Call
    __ z_bcr(Assembler::bcondZero, Z_R14);

    // Pop frame built in prologue.
    // Required so wrong_method_stub can deduce caller.
    __ pop_frame();
    __ restore_return_pc();

    // VM-Call indicates deoptimization required
    __ load_const_optimized(Z_R1_scratch, SharedRuntime::get_handle_wrong_method_stub());
    __ z_br(Z_R1_scratch);

    return start;
  }

  address generate_cont_thaw(bool return_barrier, bool exception) {
    if (!Continuations::enabled()) return nullptr;
    Unimplemented();
    return nullptr;
  }

  address generate_cont_thaw() {
    if (!Continuations::enabled()) return nullptr;
    Unimplemented();
    return nullptr;
  }

  address generate_cont_returnBarrier() {
    if (!Continuations::enabled()) return nullptr;
    Unimplemented();
    return nullptr;
  }

  address generate_cont_returnBarrier_exception() {
    if (!Continuations::enabled()) return nullptr;
    Unimplemented();
    return nullptr;
  }

  // exception handler for upcall stubs
  address generate_upcall_stub_exception_handler() {
    StubId stub_id =  StubId::stubgen_upcall_stub_exception_handler_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    // Native caller has no idea how to handle exceptions,
    // so we just crash here. Up to callee to catch exceptions.
    __ verify_oop(Z_ARG1);
    __ load_const_optimized(Z_R1_scratch, CAST_FROM_FN_PTR(uint64_t, UpcallLinker::handle_uncaught_exception));
    __ call_c(Z_R1_scratch);
    __ should_not_reach_here();

    return start;
  }

  // load Method* target of MethodHandle
  // Z_ARG1 = jobject receiver
  // Z_method = Method* result
  address generate_upcall_stub_load_target() {
    StubId stub_id =  StubId::stubgen_upcall_stub_load_target_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    __ resolve_global_jobject(Z_ARG1, Z_tmp_1, Z_tmp_2);
      // Load target method from receiver
    __ load_heap_oop(Z_method, Address(Z_ARG1, java_lang_invoke_MethodHandle::form_offset()),
                    noreg, noreg, IS_NOT_NULL);
    __ load_heap_oop(Z_method, Address(Z_method, java_lang_invoke_LambdaForm::vmentry_offset()),
                    noreg, noreg, IS_NOT_NULL);
    __ load_heap_oop(Z_method, Address(Z_method, java_lang_invoke_MemberName::method_offset()),
                    noreg, noreg, IS_NOT_NULL);
    __ z_lg(Z_method, Address(Z_method, java_lang_invoke_ResolvedMethodName::vmtarget_offset()));
    __ z_stg(Z_method, Address(Z_thread, JavaThread::callee_target_offset())); // just in case callee is deoptimized

    __ z_br(Z_R14);

    return start;
  }

  void generate_preuniverse_stubs() {
    // preuniverse stubs are not needed for s390
  }

  void generate_initial_stubs() {
    // Generates all stubs and initializes the entry points.

    // Entry points that exist in all platforms.
    // Note: This is code that could be shared among different
    // platforms - however the benefit seems to be smaller than the
    // disadvantage of having a much more complicated generator
    // structure. See also comment in stubRoutines.hpp.
    StubRoutines::_forward_exception_entry                 = generate_forward_exception();

    StubRoutines::_call_stub_entry                         = generate_call_stub(StubRoutines::_call_stub_return_address);
    StubRoutines::_catch_exception_entry                   = generate_catch_exception();

    //----------------------------------------------------------------------
    // Entry points that are platform specific.

    if (UnsafeMemoryAccess::_table == nullptr) {
      UnsafeMemoryAccess::create_table(4); // 4 for setMemory
    }

    if (UseCRC32Intrinsics) {
      StubRoutines::_updateBytesCRC32  = generate_CRC32_updateBytes();
    }

    if (UseCRC32CIntrinsics) {
      StubRoutines::_updateBytesCRC32C = generate_CRC32C_updateBytes();
    }

    // Comapct string intrinsics: Translate table for string inflate intrinsic. Used by trot instruction.
    StubRoutines::zarch::_trot_table_addr = (address)StubRoutines::zarch::_trot_table;
  }

  void generate_continuation_stubs() {
    if (!Continuations::enabled()) return;

    // Continuation stubs:
    StubRoutines::_cont_thaw          = generate_cont_thaw();
    StubRoutines::_cont_returnBarrier = generate_cont_returnBarrier();
    StubRoutines::_cont_returnBarrierExc = generate_cont_returnBarrier_exception();
  }

  void generate_final_stubs() {
    // Generates all stubs and initializes the entry points.

    // Support for verify_oop (must happen after universe_init).
    StubRoutines::_verify_oop_subroutine_entry             = generate_verify_oop_subroutine();

    // Arraycopy stubs used by compilers.
    generate_arraycopy_stubs();

    // nmethod entry barriers for concurrent class unloading
    StubRoutines::_method_entry_barrier = generate_method_entry_barrier();

#ifdef COMPILER2
    if (UseSecondarySupersTable) {
      StubRoutines::_lookup_secondary_supers_table_slow_path_stub = generate_lookup_secondary_supers_table_slow_path_stub();
      if (!InlineSecondarySupersTest) {
        generate_lookup_secondary_supers_table_stub();
      }
    }
#endif // COMPILER2

    StubRoutines::_upcall_stub_exception_handler = generate_upcall_stub_exception_handler();
    StubRoutines::_upcall_stub_load_target = generate_upcall_stub_load_target();
  }

  void generate_compiler_stubs() {

    StubRoutines::zarch::_partial_subtype_check            = generate_partial_subtype_check();

#if COMPILER2_OR_JVMCI
    // Generate AES intrinsics code.
    if (UseAESIntrinsics) {
      if (VM_Version::has_Crypto_AES()) {
        StubRoutines::_aescrypt_encryptBlock = generate_AES_encryptBlock();
        StubRoutines::_aescrypt_decryptBlock = generate_AES_decryptBlock();
        StubRoutines::_cipherBlockChaining_encryptAESCrypt = generate_cipherBlockChaining_AES_encrypt();
        StubRoutines::_cipherBlockChaining_decryptAESCrypt = generate_cipherBlockChaining_AES_decrypt();
      } else {
        // In PRODUCT builds, the function pointers will keep their initial (null) value.
        // LibraryCallKit::try_to_inline() will return false then, preventing the intrinsic to be called.
        assert(VM_Version::has_Crypto_AES(), "Inconsistent settings. Check vm_version_s390.cpp");
      }
    }

    if (UseAESCTRIntrinsics) {
      if (VM_Version::has_Crypto_AES_CTR()) {
        StubRoutines::_counterMode_AESCrypt = generate_counterMode_AESCrypt();
      } else {
        // In PRODUCT builds, the function pointers will keep their initial (null) value.
        // LibraryCallKit::try_to_inline() will return false then, preventing the intrinsic to be called.
        assert(VM_Version::has_Crypto_AES_CTR(), "Inconsistent settings. Check vm_version_s390.cpp");
      }
    }

    // Generate GHASH intrinsics code
    if (UseGHASHIntrinsics) {
      StubRoutines::_ghash_processBlocks = generate_ghash_processBlocks();
    }

    // Generate SHA1/SHA256/SHA512 intrinsics code.
    if (UseSHA1Intrinsics) {
      StubRoutines::_sha1_implCompress     = generate_SHA1_stub(StubId::stubgen_sha1_implCompress_id);
      StubRoutines::_sha1_implCompressMB   = generate_SHA1_stub(StubId::stubgen_sha1_implCompressMB_id);
    }
    if (UseSHA256Intrinsics) {
      StubRoutines::_sha256_implCompress   = generate_SHA256_stub(StubId::stubgen_sha256_implCompress_id);
      StubRoutines::_sha256_implCompressMB = generate_SHA256_stub(StubId::stubgen_sha256_implCompressMB_id);
    }
    if (UseSHA512Intrinsics) {
      StubRoutines::_sha512_implCompress   = generate_SHA512_stub(StubId::stubgen_sha512_implCompress_id);
      StubRoutines::_sha512_implCompressMB = generate_SHA512_stub(StubId::stubgen_sha512_implCompressMB_id);
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
#endif // COMPILER2_OR_JVMCI
  }

 public:
  StubGenerator(CodeBuffer* code, BlobId blob_id) : StubCodeGenerator(code, blob_id) {
    switch(blob_id) {
    case BlobId::stubgen_preuniverse_id:
      generate_preuniverse_stubs();
      break;
    case BlobId::stubgen_initial_id:
      generate_initial_stubs();
      break;
    case BlobId::stubgen_continuation_id:
      generate_continuation_stubs();
      break;
    case BlobId::stubgen_compiler_id:
      generate_compiler_stubs();
      break;
    case BlobId::stubgen_final_id:
      generate_final_stubs();
      break;
    default:
      fatal("unexpected blob id: %s", StubInfo::name(blob_id));
      break;
    };
  }

 private:
  int _stub_count;
  void stub_prolog(StubCodeDesc* cdesc) {
#ifdef ASSERT
    // Put extra information in the stub code, to make it more readable.
    // Write the high part of the address.
    // [RGV] Check if there is a dependency on the size of this prolog.
    __ emit_data((intptr_t)cdesc >> 32);
    __ emit_data((intptr_t)cdesc);
    __ emit_data(++_stub_count);
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
        __ z_illtrap();
      }
    } else {
      while ((intptr_t)(__ pc()) % icache_half_line_size != 0) {
        __ z_nop();
      }
    }
  }

};

void StubGenerator_generate(CodeBuffer* code, BlobId blob_id) {
  StubGenerator g(code, blob_id);
}
