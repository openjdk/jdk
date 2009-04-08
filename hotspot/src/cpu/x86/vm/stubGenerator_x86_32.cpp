/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_stubGenerator_x86_32.cpp.incl"

// Declaration and definition of StubGenerator (no .hpp file).
// For a more detailed description of the stub routine structure
// see the comment in stubRoutines.hpp

#define __ _masm->
#define a__ ((Assembler*)_masm)->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

const int MXCSR_MASK  = 0xFFC0;  // Mask out any pending exceptions
const int FPU_CNTRL_WRD_MASK = 0xFFFF;

// -------------------------------------------------------------------------------------------------------------------------
// Stub Code definitions

static address handle_unsafe_access() {
  JavaThread* thread = JavaThread::current();
  address pc  = thread->saved_exception_pc();
  // pc is the instruction which we must emulate
  // doing a no-op is fine:  return garbage from the load
  // therefore, compute npc
  address npc = Assembler::locate_next_instruction(pc);

  // request an async exception
  thread->set_pending_unsafe_access_error();

  // return address of next instruction to execute
  return npc;
}

class StubGenerator: public StubCodeGenerator {
 private:

#ifdef PRODUCT
#define inc_counter_np(counter) (0)
#else
  void inc_counter_np_(int& counter) {
    __ incrementl(ExternalAddress((address)&counter));
  }
#define inc_counter_np(counter) \
  BLOCK_COMMENT("inc_counter " #counter); \
  inc_counter_np_(counter);
#endif //PRODUCT

  void inc_copy_counter_np(BasicType t) {
#ifndef PRODUCT
    switch (t) {
    case T_BYTE:    inc_counter_np(SharedRuntime::_jbyte_array_copy_ctr); return;
    case T_SHORT:   inc_counter_np(SharedRuntime::_jshort_array_copy_ctr); return;
    case T_INT:     inc_counter_np(SharedRuntime::_jint_array_copy_ctr); return;
    case T_LONG:    inc_counter_np(SharedRuntime::_jlong_array_copy_ctr); return;
    case T_OBJECT:  inc_counter_np(SharedRuntime::_oop_array_copy_ctr); return;
    }
    ShouldNotReachHere();
#endif //PRODUCT
  }

  //------------------------------------------------------------------------------------------------------------------------
  // Call stubs are used to call Java from C
  //
  //    [ return_from_Java     ] <--- rsp
  //    [ argument word n      ]
  //      ...
  // -N [ argument word 1      ]
  // -7 [ Possible padding for stack alignment ]
  // -6 [ Possible padding for stack alignment ]
  // -5 [ Possible padding for stack alignment ]
  // -4 [ mxcsr save           ] <--- rsp_after_call
  // -3 [ saved rbx,            ]
  // -2 [ saved rsi            ]
  // -1 [ saved rdi            ]
  //  0 [ saved rbp,            ] <--- rbp,
  //  1 [ return address       ]
  //  2 [ ptr. to call wrapper ]
  //  3 [ result               ]
  //  4 [ result_type          ]
  //  5 [ method               ]
  //  6 [ entry_point          ]
  //  7 [ parameters           ]
  //  8 [ parameter_size       ]
  //  9 [ thread               ]


  address generate_call_stub(address& return_address) {
    StubCodeMark mark(this, "StubRoutines", "call_stub");
    address start = __ pc();

    // stub code parameters / addresses
    assert(frame::entry_frame_call_wrapper_offset == 2, "adjust this code");
    bool  sse_save = false;
    const Address rsp_after_call(rbp, -4 * wordSize); // same as in generate_catch_exception()!
    const int     locals_count_in_bytes  (4*wordSize);
    const Address mxcsr_save    (rbp, -4 * wordSize);
    const Address saved_rbx     (rbp, -3 * wordSize);
    const Address saved_rsi     (rbp, -2 * wordSize);
    const Address saved_rdi     (rbp, -1 * wordSize);
    const Address result        (rbp,  3 * wordSize);
    const Address result_type   (rbp,  4 * wordSize);
    const Address method        (rbp,  5 * wordSize);
    const Address entry_point   (rbp,  6 * wordSize);
    const Address parameters    (rbp,  7 * wordSize);
    const Address parameter_size(rbp,  8 * wordSize);
    const Address thread        (rbp,  9 * wordSize); // same as in generate_catch_exception()!
    sse_save =  UseSSE > 0;

    // stub code
    __ enter();
    __ movptr(rcx, parameter_size);              // parameter counter
    __ shlptr(rcx, Interpreter::logStackElementSize()); // convert parameter count to bytes
    __ addptr(rcx, locals_count_in_bytes);       // reserve space for register saves
    __ subptr(rsp, rcx);
    __ andptr(rsp, -(StackAlignmentInBytes));    // Align stack

    // save rdi, rsi, & rbx, according to C calling conventions
    __ movptr(saved_rdi, rdi);
    __ movptr(saved_rsi, rsi);
    __ movptr(saved_rbx, rbx);
    // save and initialize %mxcsr
    if (sse_save) {
      Label skip_ldmx;
      __ stmxcsr(mxcsr_save);
      __ movl(rax, mxcsr_save);
      __ andl(rax, MXCSR_MASK);    // Only check control and mask bits
      ExternalAddress mxcsr_std(StubRoutines::addr_mxcsr_std());
      __ cmp32(rax, mxcsr_std);
      __ jcc(Assembler::equal, skip_ldmx);
      __ ldmxcsr(mxcsr_std);
      __ bind(skip_ldmx);
    }

    // make sure the control word is correct.
    __ fldcw(ExternalAddress(StubRoutines::addr_fpu_cntrl_wrd_std()));

#ifdef ASSERT
    // make sure we have no pending exceptions
    { Label L;
      __ movptr(rcx, thread);
      __ cmpptr(Address(rcx, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
      __ jcc(Assembler::equal, L);
      __ stop("StubRoutines::call_stub: entered with pending exception");
      __ bind(L);
    }
#endif

    // pass parameters if any
    BLOCK_COMMENT("pass parameters if any");
    Label parameters_done;
    __ movl(rcx, parameter_size);  // parameter counter
    __ testl(rcx, rcx);
    __ jcc(Assembler::zero, parameters_done);

    // parameter passing loop

    Label loop;
    // Copy Java parameters in reverse order (receiver last)
    // Note that the argument order is inverted in the process
    // source is rdx[rcx: N-1..0]
    // dest   is rsp[rbx: 0..N-1]

    __ movptr(rdx, parameters);          // parameter pointer
    __ xorptr(rbx, rbx);

    __ BIND(loop);
    if (TaggedStackInterpreter) {
      __ movptr(rax, Address(rdx, rcx, Interpreter::stackElementScale(),
                      -2*wordSize));                          // get tag
      __ movptr(Address(rsp, rbx, Interpreter::stackElementScale(),
                      Interpreter::expr_tag_offset_in_bytes(0)), rax);     // store tag
    }

    // get parameter
    __ movptr(rax, Address(rdx, rcx, Interpreter::stackElementScale(), -wordSize));
    __ movptr(Address(rsp, rbx, Interpreter::stackElementScale(),
                    Interpreter::expr_offset_in_bytes(0)), rax);          // store parameter
    __ increment(rbx);
    __ decrement(rcx);
    __ jcc(Assembler::notZero, loop);

    // call Java function
    __ BIND(parameters_done);
    __ movptr(rbx, method);           // get methodOop
    __ movptr(rax, entry_point);      // get entry_point
    __ mov(rsi, rsp);                 // set sender sp
    BLOCK_COMMENT("call Java function");
    __ call(rax);

    BLOCK_COMMENT("call_stub_return_address:");
    return_address = __ pc();

    Label common_return;

    __ BIND(common_return);

    // store result depending on type
    // (everything that is not T_LONG, T_FLOAT or T_DOUBLE is treated as T_INT)
    __ movptr(rdi, result);
    Label is_long, is_float, is_double, exit;
    __ movl(rsi, result_type);
    __ cmpl(rsi, T_LONG);
    __ jcc(Assembler::equal, is_long);
    __ cmpl(rsi, T_FLOAT);
    __ jcc(Assembler::equal, is_float);
    __ cmpl(rsi, T_DOUBLE);
    __ jcc(Assembler::equal, is_double);

    // handle T_INT case
    __ movl(Address(rdi, 0), rax);
    __ BIND(exit);

    // check that FPU stack is empty
    __ verify_FPU(0, "generate_call_stub");

    // pop parameters
    __ lea(rsp, rsp_after_call);

    // restore %mxcsr
    if (sse_save) {
      __ ldmxcsr(mxcsr_save);
    }

    // restore rdi, rsi and rbx,
    __ movptr(rbx, saved_rbx);
    __ movptr(rsi, saved_rsi);
    __ movptr(rdi, saved_rdi);
    __ addptr(rsp, 4*wordSize);

    // return
    __ pop(rbp);
    __ ret(0);

    // handle return types different from T_INT
    __ BIND(is_long);
    __ movl(Address(rdi, 0 * wordSize), rax);
    __ movl(Address(rdi, 1 * wordSize), rdx);
    __ jmp(exit);

    __ BIND(is_float);
    // interpreter uses xmm0 for return values
    if (UseSSE >= 1) {
      __ movflt(Address(rdi, 0), xmm0);
    } else {
      __ fstp_s(Address(rdi, 0));
    }
    __ jmp(exit);

    __ BIND(is_double);
    // interpreter uses xmm0 for return values
    if (UseSSE >= 2) {
      __ movdbl(Address(rdi, 0), xmm0);
    } else {
      __ fstp_d(Address(rdi, 0));
    }
    __ jmp(exit);

    // If we call compiled code directly from the call stub we will
    // need to adjust the return back to the call stub to a specialized
    // piece of code that can handle compiled results and cleaning the fpu
    // stack. compiled code will be set to return here instead of the
    // return above that handles interpreter returns.

    BLOCK_COMMENT("call_stub_compiled_return:");
    StubRoutines::x86::set_call_stub_compiled_return( __ pc());

#ifdef COMPILER2
    if (UseSSE >= 2) {
      __ verify_FPU(0, "call_stub_compiled_return");
    } else {
      for (int i = 1; i < 8; i++) {
        __ ffree(i);
      }

      // UseSSE <= 1 so double result should be left on TOS
      __ movl(rsi, result_type);
      __ cmpl(rsi, T_DOUBLE);
      __ jcc(Assembler::equal, common_return);
      if (UseSSE == 0) {
        // UseSSE == 0 so float result should be left on TOS
        __ cmpl(rsi, T_FLOAT);
        __ jcc(Assembler::equal, common_return);
      }
      __ ffree(0);
    }
#endif /* COMPILER2 */
    __ jmp(common_return);

    return start;
  }


  //------------------------------------------------------------------------------------------------------------------------
  // Return point for a Java call if there's an exception thrown in Java code.
  // The exception is caught and transformed into a pending exception stored in
  // JavaThread that can be tested from within the VM.
  //
  // Note: Usually the parameters are removed by the callee. In case of an exception
  //       crossing an activation frame boundary, that is not the case if the callee
  //       is compiled code => need to setup the rsp.
  //
  // rax,: exception oop

  address generate_catch_exception() {
    StubCodeMark mark(this, "StubRoutines", "catch_exception");
    const Address rsp_after_call(rbp, -4 * wordSize); // same as in generate_call_stub()!
    const Address thread        (rbp,  9 * wordSize); // same as in generate_call_stub()!
    address start = __ pc();

    // get thread directly
    __ movptr(rcx, thread);
#ifdef ASSERT
    // verify that threads correspond
    { Label L;
      __ get_thread(rbx);
      __ cmpptr(rbx, rcx);
      __ jcc(Assembler::equal, L);
      __ stop("StubRoutines::catch_exception: threads must correspond");
      __ bind(L);
    }
#endif
    // set pending exception
    __ verify_oop(rax);
    __ movptr(Address(rcx, Thread::pending_exception_offset()), rax          );
    __ lea(Address(rcx, Thread::exception_file_offset   ()),
           ExternalAddress((address)__FILE__));
    __ movl(Address(rcx, Thread::exception_line_offset   ()), __LINE__ );
    // complete return to VM
    assert(StubRoutines::_call_stub_return_address != NULL, "_call_stub_return_address must have been generated before");
    __ jump(RuntimeAddress(StubRoutines::_call_stub_return_address));

    return start;
  }


  //------------------------------------------------------------------------------------------------------------------------
  // Continuation point for runtime calls returning with a pending exception.
  // The pending exception check happened in the runtime or native call stub.
  // The pending exception in Thread is converted into a Java-level exception.
  //
  // Contract with Java-level exception handlers:
  // rax,: exception
  // rdx: throwing pc
  //
  // NOTE: At entry of this stub, exception-pc must be on stack !!

  address generate_forward_exception() {
    StubCodeMark mark(this, "StubRoutines", "forward exception");
    address start = __ pc();

    // Upon entry, the sp points to the return address returning into Java
    // (interpreted or compiled) code; i.e., the return address becomes the
    // throwing pc.
    //
    // Arguments pushed before the runtime call are still on the stack but
    // the exception handler will reset the stack pointer -> ignore them.
    // A potential result in registers can be ignored as well.

#ifdef ASSERT
    // make sure this code is only executed if there is a pending exception
    { Label L;
      __ get_thread(rcx);
      __ cmpptr(Address(rcx, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
      __ jcc(Assembler::notEqual, L);
      __ stop("StubRoutines::forward exception: no pending exception (1)");
      __ bind(L);
    }
#endif

    // compute exception handler into rbx,
    __ movptr(rax, Address(rsp, 0));
    BLOCK_COMMENT("call exception_handler_for_return_address");
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), rax);
    __ mov(rbx, rax);

    // setup rax, & rdx, remove return address & clear pending exception
    __ get_thread(rcx);
    __ pop(rdx);
    __ movptr(rax, Address(rcx, Thread::pending_exception_offset()));
    __ movptr(Address(rcx, Thread::pending_exception_offset()), NULL_WORD);

#ifdef ASSERT
    // make sure exception is set
    { Label L;
      __ testptr(rax, rax);
      __ jcc(Assembler::notEqual, L);
      __ stop("StubRoutines::forward exception: no pending exception (2)");
      __ bind(L);
    }
#endif

    // continue at exception handler (return address removed)
    // rax,: exception
    // rbx,: exception handler
    // rdx: throwing pc
    __ verify_oop(rax);
    __ jmp(rbx);

    return start;
  }


  //----------------------------------------------------------------------------------------------------
  // Support for jint Atomic::xchg(jint exchange_value, volatile jint* dest)
  //
  // xchg exists as far back as 8086, lock needed for MP only
  // Stack layout immediately after call:
  //
  // 0 [ret addr ] <--- rsp
  // 1 [  ex     ]
  // 2 [  dest   ]
  //
  // Result:   *dest <- ex, return (old *dest)
  //
  // Note: win32 does not currently use this code

  address generate_atomic_xchg() {
    StubCodeMark mark(this, "StubRoutines", "atomic_xchg");
    address start = __ pc();

    __ push(rdx);
    Address exchange(rsp, 2 * wordSize);
    Address dest_addr(rsp, 3 * wordSize);
    __ movl(rax, exchange);
    __ movptr(rdx, dest_addr);
    __ xchgl(rax, Address(rdx, 0));
    __ pop(rdx);
    __ ret(0);

    return start;
  }

  //----------------------------------------------------------------------------------------------------
  // Support for void verify_mxcsr()
  //
  // This routine is used with -Xcheck:jni to verify that native
  // JNI code does not return to Java code without restoring the
  // MXCSR register to our expected state.


  address generate_verify_mxcsr() {
    StubCodeMark mark(this, "StubRoutines", "verify_mxcsr");
    address start = __ pc();

    const Address mxcsr_save(rsp, 0);

    if (CheckJNICalls && UseSSE > 0 ) {
      Label ok_ret;
      ExternalAddress mxcsr_std(StubRoutines::addr_mxcsr_std());
      __ push(rax);
      __ subptr(rsp, wordSize);      // allocate a temp location
      __ stmxcsr(mxcsr_save);
      __ movl(rax, mxcsr_save);
      __ andl(rax, MXCSR_MASK);
      __ cmp32(rax, mxcsr_std);
      __ jcc(Assembler::equal, ok_ret);

      __ warn("MXCSR changed by native JNI code.");

      __ ldmxcsr(mxcsr_std);

      __ bind(ok_ret);
      __ addptr(rsp, wordSize);
      __ pop(rax);
    }

    __ ret(0);

    return start;
  }


  //---------------------------------------------------------------------------
  // Support for void verify_fpu_cntrl_wrd()
  //
  // This routine is used with -Xcheck:jni to verify that native
  // JNI code does not return to Java code without restoring the
  // FP control word to our expected state.

  address generate_verify_fpu_cntrl_wrd() {
    StubCodeMark mark(this, "StubRoutines", "verify_spcw");
    address start = __ pc();

    const Address fpu_cntrl_wrd_save(rsp, 0);

    if (CheckJNICalls) {
      Label ok_ret;
      __ push(rax);
      __ subptr(rsp, wordSize);      // allocate a temp location
      __ fnstcw(fpu_cntrl_wrd_save);
      __ movl(rax, fpu_cntrl_wrd_save);
      __ andl(rax, FPU_CNTRL_WRD_MASK);
      ExternalAddress fpu_std(StubRoutines::addr_fpu_cntrl_wrd_std());
      __ cmp32(rax, fpu_std);
      __ jcc(Assembler::equal, ok_ret);

      __ warn("Floating point control word changed by native JNI code.");

      __ fldcw(fpu_std);

      __ bind(ok_ret);
      __ addptr(rsp, wordSize);
      __ pop(rax);
    }

    __ ret(0);

    return start;
  }

  //---------------------------------------------------------------------------
  // Wrapper for slow-case handling of double-to-integer conversion
  // d2i or f2i fast case failed either because it is nan or because
  // of under/overflow.
  // Input:  FPU TOS: float value
  // Output: rax, (rdx): integer (long) result

  address generate_d2i_wrapper(BasicType t, address fcn) {
    StubCodeMark mark(this, "StubRoutines", "d2i_wrapper");
    address start = __ pc();

  // Capture info about frame layout
  enum layout { FPUState_off         = 0,
                rbp_off              = FPUStateSizeInWords,
                rdi_off,
                rsi_off,
                rcx_off,
                rbx_off,
                saved_argument_off,
                saved_argument_off2, // 2nd half of double
                framesize
  };

  assert(FPUStateSizeInWords == 27, "update stack layout");

    // Save outgoing argument to stack across push_FPU_state()
    __ subptr(rsp, wordSize * 2);
    __ fstp_d(Address(rsp, 0));

    // Save CPU & FPU state
    __ push(rbx);
    __ push(rcx);
    __ push(rsi);
    __ push(rdi);
    __ push(rbp);
    __ push_FPU_state();

    // push_FPU_state() resets the FP top of stack
    // Load original double into FP top of stack
    __ fld_d(Address(rsp, saved_argument_off * wordSize));
    // Store double into stack as outgoing argument
    __ subptr(rsp, wordSize*2);
    __ fst_d(Address(rsp, 0));

    // Prepare FPU for doing math in C-land
    __ empty_FPU_stack();
    // Call the C code to massage the double.  Result in EAX
    if (t == T_INT)
      { BLOCK_COMMENT("SharedRuntime::d2i"); }
    else if (t == T_LONG)
      { BLOCK_COMMENT("SharedRuntime::d2l"); }
    __ call_VM_leaf( fcn, 2 );

    // Restore CPU & FPU state
    __ pop_FPU_state();
    __ pop(rbp);
    __ pop(rdi);
    __ pop(rsi);
    __ pop(rcx);
    __ pop(rbx);
    __ addptr(rsp, wordSize * 2);

    __ ret(0);

    return start;
  }


  //---------------------------------------------------------------------------
  // The following routine generates a subroutine to throw an asynchronous
  // UnknownError when an unsafe access gets a fault that could not be
  // reasonably prevented by the programmer.  (Example: SIGBUS/OBJERR.)
  address generate_handler_for_unsafe_access() {
    StubCodeMark mark(this, "StubRoutines", "handler_for_unsafe_access");
    address start = __ pc();

    __ push(0);                       // hole for return address-to-be
    __ pusha();                       // push registers
    Address next_pc(rsp, RegisterImpl::number_of_registers * BytesPerWord);
    BLOCK_COMMENT("call handle_unsafe_access");
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, handle_unsafe_access)));
    __ movptr(next_pc, rax);          // stuff next address
    __ popa();
    __ ret(0);                        // jump to next address

    return start;
  }


  //----------------------------------------------------------------------------------------------------
  // Non-destructive plausibility checks for oops

  address generate_verify_oop() {
    StubCodeMark mark(this, "StubRoutines", "verify_oop");
    address start = __ pc();

    // Incoming arguments on stack after saving rax,:
    //
    // [tos    ]: saved rdx
    // [tos + 1]: saved EFLAGS
    // [tos + 2]: return address
    // [tos + 3]: char* error message
    // [tos + 4]: oop   object to verify
    // [tos + 5]: saved rax, - saved by caller and bashed

    Label exit, error;
    __ pushf();
    __ incrementl(ExternalAddress((address) StubRoutines::verify_oop_count_addr()));
    __ push(rdx);                                // save rdx
    // make sure object is 'reasonable'
    __ movptr(rax, Address(rsp, 4 * wordSize));    // get object
    __ testptr(rax, rax);
    __ jcc(Assembler::zero, exit);               // if obj is NULL it is ok

    // Check if the oop is in the right area of memory
    const int oop_mask = Universe::verify_oop_mask();
    const int oop_bits = Universe::verify_oop_bits();
    __ mov(rdx, rax);
    __ andptr(rdx, oop_mask);
    __ cmpptr(rdx, oop_bits);
    __ jcc(Assembler::notZero, error);

    // make sure klass is 'reasonable'
    __ movptr(rax, Address(rax, oopDesc::klass_offset_in_bytes())); // get klass
    __ testptr(rax, rax);
    __ jcc(Assembler::zero, error);              // if klass is NULL it is broken

    // Check if the klass is in the right area of memory
    const int klass_mask = Universe::verify_klass_mask();
    const int klass_bits = Universe::verify_klass_bits();
    __ mov(rdx, rax);
    __ andptr(rdx, klass_mask);
    __ cmpptr(rdx, klass_bits);
    __ jcc(Assembler::notZero, error);

    // make sure klass' klass is 'reasonable'
    __ movptr(rax, Address(rax, oopDesc::klass_offset_in_bytes())); // get klass' klass
    __ testptr(rax, rax);
    __ jcc(Assembler::zero, error);              // if klass' klass is NULL it is broken

    __ mov(rdx, rax);
    __ andptr(rdx, klass_mask);
    __ cmpptr(rdx, klass_bits);
    __ jcc(Assembler::notZero, error);           // if klass not in right area
                                                 // of memory it is broken too.

    // return if everything seems ok
    __ bind(exit);
    __ movptr(rax, Address(rsp, 5 * wordSize));  // get saved rax, back
    __ pop(rdx);                                 // restore rdx
    __ popf();                                   // restore EFLAGS
    __ ret(3 * wordSize);                        // pop arguments

    // handle errors
    __ bind(error);
    __ movptr(rax, Address(rsp, 5 * wordSize));  // get saved rax, back
    __ pop(rdx);                                 // get saved rdx back
    __ popf();                                   // get saved EFLAGS off stack -- will be ignored
    __ pusha();                                  // push registers (eip = return address & msg are already pushed)
    BLOCK_COMMENT("call MacroAssembler::debug");
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, MacroAssembler::debug32)));
    __ popa();
    __ ret(3 * wordSize);                        // pop arguments
    return start;
  }

  //
  //  Generate pre-barrier for array stores
  //
  //  Input:
  //     start   -  starting address
  //     end     -  element count
  void  gen_write_ref_array_pre_barrier(Register start, Register count) {
    assert_different_registers(start, count);
    BarrierSet* bs = Universe::heap()->barrier_set();
    switch (bs->kind()) {
      case BarrierSet::G1SATBCT:
      case BarrierSet::G1SATBCTLogging:
        {
          __ pusha();                      // push registers
          __ push(count);
          __ push(start);
          __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_pre)));
          __ addptr(rsp, 2*wordSize);
          __ popa();
        }
        break;
      case BarrierSet::CardTableModRef:
      case BarrierSet::CardTableExtension:
      case BarrierSet::ModRef:
        break;
      default      :
        ShouldNotReachHere();

    }
  }


  //
  // Generate a post-barrier for an array store
  //
  //     start    -  starting address
  //     count    -  element count
  //
  //  The two input registers are overwritten.
  //
  void  gen_write_ref_array_post_barrier(Register start, Register count) {
    BarrierSet* bs = Universe::heap()->barrier_set();
    assert_different_registers(start, count);
    switch (bs->kind()) {
      case BarrierSet::G1SATBCT:
      case BarrierSet::G1SATBCTLogging:
        {
          __ pusha();                      // push registers
          __ push(count);
          __ push(start);
          __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_post)));
          __ addptr(rsp, 2*wordSize);
          __ popa();

        }
        break;

      case BarrierSet::CardTableModRef:
      case BarrierSet::CardTableExtension:
        {
          CardTableModRefBS* ct = (CardTableModRefBS*)bs;
          assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");

          Label L_loop;
          const Register end = count;  // elements count; end == start+count-1
          assert_different_registers(start, end);

          __ lea(end,  Address(start, count, Address::times_ptr, -wordSize));
          __ shrptr(start, CardTableModRefBS::card_shift);
          __ shrptr(end,   CardTableModRefBS::card_shift);
          __ subptr(end, start); // end --> count
        __ BIND(L_loop);
          intptr_t disp = (intptr_t) ct->byte_map_base;
          Address cardtable(start, count, Address::times_1, disp);
          __ movb(cardtable, 0);
          __ decrement(count);
          __ jcc(Assembler::greaterEqual, L_loop);
        }
        break;
      case BarrierSet::ModRef:
        break;
      default      :
        ShouldNotReachHere();

    }
  }


  // Copy 64 bytes chunks
  //
  // Inputs:
  //   from        - source array address
  //   to_from     - destination array address - from
  //   qword_count - 8-bytes element count, negative
  //
  void xmm_copy_forward(Register from, Register to_from, Register qword_count) {
    assert( UseSSE >= 2, "supported cpu only" );
    Label L_copy_64_bytes_loop, L_copy_64_bytes, L_copy_8_bytes, L_exit;
    // Copy 64-byte chunks
    __ jmpb(L_copy_64_bytes);
    __ align(16);
  __ BIND(L_copy_64_bytes_loop);

    if(UseUnalignedLoadStores) {
      __ movdqu(xmm0, Address(from, 0));
      __ movdqu(Address(from, to_from, Address::times_1, 0), xmm0);
      __ movdqu(xmm1, Address(from, 16));
      __ movdqu(Address(from, to_from, Address::times_1, 16), xmm1);
      __ movdqu(xmm2, Address(from, 32));
      __ movdqu(Address(from, to_from, Address::times_1, 32), xmm2);
      __ movdqu(xmm3, Address(from, 48));
      __ movdqu(Address(from, to_from, Address::times_1, 48), xmm3);

    } else {
      __ movq(xmm0, Address(from, 0));
      __ movq(Address(from, to_from, Address::times_1, 0), xmm0);
      __ movq(xmm1, Address(from, 8));
      __ movq(Address(from, to_from, Address::times_1, 8), xmm1);
      __ movq(xmm2, Address(from, 16));
      __ movq(Address(from, to_from, Address::times_1, 16), xmm2);
      __ movq(xmm3, Address(from, 24));
      __ movq(Address(from, to_from, Address::times_1, 24), xmm3);
      __ movq(xmm4, Address(from, 32));
      __ movq(Address(from, to_from, Address::times_1, 32), xmm4);
      __ movq(xmm5, Address(from, 40));
      __ movq(Address(from, to_from, Address::times_1, 40), xmm5);
      __ movq(xmm6, Address(from, 48));
      __ movq(Address(from, to_from, Address::times_1, 48), xmm6);
      __ movq(xmm7, Address(from, 56));
      __ movq(Address(from, to_from, Address::times_1, 56), xmm7);
    }

    __ addl(from, 64);
  __ BIND(L_copy_64_bytes);
    __ subl(qword_count, 8);
    __ jcc(Assembler::greaterEqual, L_copy_64_bytes_loop);
    __ addl(qword_count, 8);
    __ jccb(Assembler::zero, L_exit);
    //
    // length is too short, just copy qwords
    //
  __ BIND(L_copy_8_bytes);
    __ movq(xmm0, Address(from, 0));
    __ movq(Address(from, to_from, Address::times_1), xmm0);
    __ addl(from, 8);
    __ decrement(qword_count);
    __ jcc(Assembler::greater, L_copy_8_bytes);
  __ BIND(L_exit);
  }

  // Copy 64 bytes chunks
  //
  // Inputs:
  //   from        - source array address
  //   to_from     - destination array address - from
  //   qword_count - 8-bytes element count, negative
  //
  void mmx_copy_forward(Register from, Register to_from, Register qword_count) {
    assert( VM_Version::supports_mmx(), "supported cpu only" );
    Label L_copy_64_bytes_loop, L_copy_64_bytes, L_copy_8_bytes, L_exit;
    // Copy 64-byte chunks
    __ jmpb(L_copy_64_bytes);
    __ align(16);
  __ BIND(L_copy_64_bytes_loop);
    __ movq(mmx0, Address(from, 0));
    __ movq(mmx1, Address(from, 8));
    __ movq(mmx2, Address(from, 16));
    __ movq(Address(from, to_from, Address::times_1, 0), mmx0);
    __ movq(mmx3, Address(from, 24));
    __ movq(Address(from, to_from, Address::times_1, 8), mmx1);
    __ movq(mmx4, Address(from, 32));
    __ movq(Address(from, to_from, Address::times_1, 16), mmx2);
    __ movq(mmx5, Address(from, 40));
    __ movq(Address(from, to_from, Address::times_1, 24), mmx3);
    __ movq(mmx6, Address(from, 48));
    __ movq(Address(from, to_from, Address::times_1, 32), mmx4);
    __ movq(mmx7, Address(from, 56));
    __ movq(Address(from, to_from, Address::times_1, 40), mmx5);
    __ movq(Address(from, to_from, Address::times_1, 48), mmx6);
    __ movq(Address(from, to_from, Address::times_1, 56), mmx7);
    __ addptr(from, 64);
  __ BIND(L_copy_64_bytes);
    __ subl(qword_count, 8);
    __ jcc(Assembler::greaterEqual, L_copy_64_bytes_loop);
    __ addl(qword_count, 8);
    __ jccb(Assembler::zero, L_exit);
    //
    // length is too short, just copy qwords
    //
  __ BIND(L_copy_8_bytes);
    __ movq(mmx0, Address(from, 0));
    __ movq(Address(from, to_from, Address::times_1), mmx0);
    __ addptr(from, 8);
    __ decrement(qword_count);
    __ jcc(Assembler::greater, L_copy_8_bytes);
  __ BIND(L_exit);
    __ emms();
  }

  address generate_disjoint_copy(BasicType t, bool aligned,
                                 Address::ScaleFactor sf,
                                 address* entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_0_count, L_exit, L_skip_align1, L_skip_align2, L_copy_byte;
    Label L_copy_2_bytes, L_copy_4_bytes, L_copy_64_bytes;

    int shift = Address::times_ptr - sf;

    const Register from     = rsi;  // source array address
    const Register to       = rdi;  // destination array address
    const Register count    = rcx;  // elements count
    const Register to_from  = to;   // (to - from)
    const Register saved_to = rdx;  // saved destination array address

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    __ push(rsi);
    __ push(rdi);
    __ movptr(from , Address(rsp, 12+ 4));
    __ movptr(to   , Address(rsp, 12+ 8));
    __ movl(count, Address(rsp, 12+ 12));
    if (t == T_OBJECT) {
      __ testl(count, count);
      __ jcc(Assembler::zero, L_0_count);
      gen_write_ref_array_pre_barrier(to, count);
      __ mov(saved_to, to);          // save 'to'
    }

    *entry = __ pc(); // Entry point from conjoint arraycopy stub.
    BLOCK_COMMENT("Entry:");

    __ subptr(to, from); // to --> to_from
    __ cmpl(count, 2<<shift); // Short arrays (< 8 bytes) copy by element
    __ jcc(Assembler::below, L_copy_4_bytes); // use unsigned cmp
    if (!UseUnalignedLoadStores && !aligned && (t == T_BYTE || t == T_SHORT)) {
      // align source address at 4 bytes address boundary
      if (t == T_BYTE) {
        // One byte misalignment happens only for byte arrays
        __ testl(from, 1);
        __ jccb(Assembler::zero, L_skip_align1);
        __ movb(rax, Address(from, 0));
        __ movb(Address(from, to_from, Address::times_1, 0), rax);
        __ increment(from);
        __ decrement(count);
      __ BIND(L_skip_align1);
      }
      // Two bytes misalignment happens only for byte and short (char) arrays
      __ testl(from, 2);
      __ jccb(Assembler::zero, L_skip_align2);
      __ movw(rax, Address(from, 0));
      __ movw(Address(from, to_from, Address::times_1, 0), rax);
      __ addptr(from, 2);
      __ subl(count, 1<<(shift-1));
    __ BIND(L_skip_align2);
    }
    if (!VM_Version::supports_mmx()) {
      __ mov(rax, count);      // save 'count'
      __ shrl(count, shift); // bytes count
      __ addptr(to_from, from);// restore 'to'
      __ rep_mov();
      __ subptr(to_from, from);// restore 'to_from'
      __ mov(count, rax);      // restore 'count'
      __ jmpb(L_copy_2_bytes); // all dwords were copied
    } else {
      if (!UseUnalignedLoadStores) {
        // align to 8 bytes, we know we are 4 byte aligned to start
        __ testptr(from, 4);
        __ jccb(Assembler::zero, L_copy_64_bytes);
        __ movl(rax, Address(from, 0));
        __ movl(Address(from, to_from, Address::times_1, 0), rax);
        __ addptr(from, 4);
        __ subl(count, 1<<shift);
      }
    __ BIND(L_copy_64_bytes);
      __ mov(rax, count);
      __ shrl(rax, shift+1);  // 8 bytes chunk count
      //
      // Copy 8-byte chunks through MMX registers, 8 per iteration of the loop
      //
      if (UseXMMForArrayCopy) {
        xmm_copy_forward(from, to_from, rax);
      } else {
        mmx_copy_forward(from, to_from, rax);
      }
    }
    // copy tailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(count, 1<<shift);
    __ jccb(Assembler::zero, L_copy_2_bytes);
    __ movl(rax, Address(from, 0));
    __ movl(Address(from, to_from, Address::times_1, 0), rax);
    if (t == T_BYTE || t == T_SHORT) {
      __ addptr(from, 4);
    __ BIND(L_copy_2_bytes);
      // copy tailing word
      __ testl(count, 1<<(shift-1));
      __ jccb(Assembler::zero, L_copy_byte);
      __ movw(rax, Address(from, 0));
      __ movw(Address(from, to_from, Address::times_1, 0), rax);
      if (t == T_BYTE) {
        __ addptr(from, 2);
      __ BIND(L_copy_byte);
        // copy tailing byte
        __ testl(count, 1);
        __ jccb(Assembler::zero, L_exit);
        __ movb(rax, Address(from, 0));
        __ movb(Address(from, to_from, Address::times_1, 0), rax);
      __ BIND(L_exit);
      } else {
      __ BIND(L_copy_byte);
      }
    } else {
    __ BIND(L_copy_2_bytes);
    }

    if (t == T_OBJECT) {
      __ movl(count, Address(rsp, 12+12)); // reread 'count'
      __ mov(to, saved_to); // restore 'to'
      gen_write_ref_array_post_barrier(to, count);
    __ BIND(L_0_count);
    }
    inc_copy_counter_np(t);
    __ pop(rdi);
    __ pop(rsi);
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ xorptr(rax, rax); // return 0
    __ ret(0);
    return start;
  }


  address generate_conjoint_copy(BasicType t, bool aligned,
                                 Address::ScaleFactor sf,
                                 address nooverlap_target,
                                 address* entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_0_count, L_exit, L_skip_align1, L_skip_align2, L_copy_byte;
    Label L_copy_2_bytes, L_copy_4_bytes, L_copy_8_bytes, L_copy_8_bytes_loop;

    int shift = Address::times_ptr - sf;

    const Register src   = rax;  // source array address
    const Register dst   = rdx;  // destination array address
    const Register from  = rsi;  // source array address
    const Register to    = rdi;  // destination array address
    const Register count = rcx;  // elements count
    const Register end   = rax;  // array end address

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    __ push(rsi);
    __ push(rdi);
    __ movptr(src  , Address(rsp, 12+ 4));   // from
    __ movptr(dst  , Address(rsp, 12+ 8));   // to
    __ movl2ptr(count, Address(rsp, 12+12)); // count
    if (t == T_OBJECT) {
       gen_write_ref_array_pre_barrier(dst, count);
    }

    if (entry != NULL) {
      *entry = __ pc(); // Entry point from generic arraycopy stub.
      BLOCK_COMMENT("Entry:");
    }

    if (t == T_OBJECT) {
      __ testl(count, count);
      __ jcc(Assembler::zero, L_0_count);
    }
    __ mov(from, src);
    __ mov(to  , dst);

    // arrays overlap test
    RuntimeAddress nooverlap(nooverlap_target);
    __ cmpptr(dst, src);
    __ lea(end, Address(src, count, sf, 0)); // src + count * elem_size
    __ jump_cc(Assembler::belowEqual, nooverlap);
    __ cmpptr(dst, end);
    __ jump_cc(Assembler::aboveEqual, nooverlap);

    // copy from high to low
    __ cmpl(count, 2<<shift); // Short arrays (< 8 bytes) copy by element
    __ jcc(Assembler::below, L_copy_4_bytes); // use unsigned cmp
    if (t == T_BYTE || t == T_SHORT) {
      // Align the end of destination array at 4 bytes address boundary
      __ lea(end, Address(dst, count, sf, 0));
      if (t == T_BYTE) {
        // One byte misalignment happens only for byte arrays
        __ testl(end, 1);
        __ jccb(Assembler::zero, L_skip_align1);
        __ decrement(count);
        __ movb(rdx, Address(from, count, sf, 0));
        __ movb(Address(to, count, sf, 0), rdx);
      __ BIND(L_skip_align1);
      }
      // Two bytes misalignment happens only for byte and short (char) arrays
      __ testl(end, 2);
      __ jccb(Assembler::zero, L_skip_align2);
      __ subptr(count, 1<<(shift-1));
      __ movw(rdx, Address(from, count, sf, 0));
      __ movw(Address(to, count, sf, 0), rdx);
    __ BIND(L_skip_align2);
      __ cmpl(count, 2<<shift); // Short arrays (< 8 bytes) copy by element
      __ jcc(Assembler::below, L_copy_4_bytes);
    }

    if (!VM_Version::supports_mmx()) {
      __ std();
      __ mov(rax, count); // Save 'count'
      __ mov(rdx, to);    // Save 'to'
      __ lea(rsi, Address(from, count, sf, -4));
      __ lea(rdi, Address(to  , count, sf, -4));
      __ shrptr(count, shift); // bytes count
      __ rep_mov();
      __ cld();
      __ mov(count, rax); // restore 'count'
      __ andl(count, (1<<shift)-1);      // mask the number of rest elements
      __ movptr(from, Address(rsp, 12+4)); // reread 'from'
      __ mov(to, rdx);   // restore 'to'
      __ jmpb(L_copy_2_bytes); // all dword were copied
   } else {
      // Align to 8 bytes the end of array. It is aligned to 4 bytes already.
      __ testptr(end, 4);
      __ jccb(Assembler::zero, L_copy_8_bytes);
      __ subl(count, 1<<shift);
      __ movl(rdx, Address(from, count, sf, 0));
      __ movl(Address(to, count, sf, 0), rdx);
      __ jmpb(L_copy_8_bytes);

      __ align(16);
      // Move 8 bytes
    __ BIND(L_copy_8_bytes_loop);
      if (UseXMMForArrayCopy) {
        __ movq(xmm0, Address(from, count, sf, 0));
        __ movq(Address(to, count, sf, 0), xmm0);
      } else {
        __ movq(mmx0, Address(from, count, sf, 0));
        __ movq(Address(to, count, sf, 0), mmx0);
      }
    __ BIND(L_copy_8_bytes);
      __ subl(count, 2<<shift);
      __ jcc(Assembler::greaterEqual, L_copy_8_bytes_loop);
      __ addl(count, 2<<shift);
      if (!UseXMMForArrayCopy) {
        __ emms();
      }
    }
  __ BIND(L_copy_4_bytes);
    // copy prefix qword
    __ testl(count, 1<<shift);
    __ jccb(Assembler::zero, L_copy_2_bytes);
    __ movl(rdx, Address(from, count, sf, -4));
    __ movl(Address(to, count, sf, -4), rdx);

    if (t == T_BYTE || t == T_SHORT) {
        __ subl(count, (1<<shift));
      __ BIND(L_copy_2_bytes);
        // copy prefix dword
        __ testl(count, 1<<(shift-1));
        __ jccb(Assembler::zero, L_copy_byte);
        __ movw(rdx, Address(from, count, sf, -2));
        __ movw(Address(to, count, sf, -2), rdx);
        if (t == T_BYTE) {
          __ subl(count, 1<<(shift-1));
        __ BIND(L_copy_byte);
          // copy prefix byte
          __ testl(count, 1);
          __ jccb(Assembler::zero, L_exit);
          __ movb(rdx, Address(from, 0));
          __ movb(Address(to, 0), rdx);
        __ BIND(L_exit);
        } else {
        __ BIND(L_copy_byte);
        }
    } else {
    __ BIND(L_copy_2_bytes);
    }
    if (t == T_OBJECT) {
      __ movl2ptr(count, Address(rsp, 12+12)); // reread count
      gen_write_ref_array_post_barrier(to, count);
    __ BIND(L_0_count);
    }
    inc_copy_counter_np(t);
    __ pop(rdi);
    __ pop(rsi);
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ xorptr(rax, rax); // return 0
    __ ret(0);
    return start;
  }


  address generate_disjoint_long_copy(address* entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_8_bytes, L_copy_8_bytes_loop;
    const Register from       = rax;  // source array address
    const Register to         = rdx;  // destination array address
    const Register count      = rcx;  // elements count
    const Register to_from    = rdx;  // (to - from)

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    __ movptr(from , Address(rsp, 8+0));       // from
    __ movptr(to   , Address(rsp, 8+4));       // to
    __ movl2ptr(count, Address(rsp, 8+8));     // count

    *entry = __ pc(); // Entry point from conjoint arraycopy stub.
    BLOCK_COMMENT("Entry:");

    __ subptr(to, from); // to --> to_from
    if (VM_Version::supports_mmx()) {
      if (UseXMMForArrayCopy) {
        xmm_copy_forward(from, to_from, count);
      } else {
        mmx_copy_forward(from, to_from, count);
      }
    } else {
      __ jmpb(L_copy_8_bytes);
      __ align(16);
    __ BIND(L_copy_8_bytes_loop);
      __ fild_d(Address(from, 0));
      __ fistp_d(Address(from, to_from, Address::times_1));
      __ addptr(from, 8);
    __ BIND(L_copy_8_bytes);
      __ decrement(count);
      __ jcc(Assembler::greaterEqual, L_copy_8_bytes_loop);
    }
    inc_copy_counter_np(T_LONG);
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ xorptr(rax, rax); // return 0
    __ ret(0);
    return start;
  }

  address generate_conjoint_long_copy(address nooverlap_target,
                                      address* entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_8_bytes, L_copy_8_bytes_loop;
    const Register from       = rax;  // source array address
    const Register to         = rdx;  // destination array address
    const Register count      = rcx;  // elements count
    const Register end_from   = rax;  // source array end address

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    __ movptr(from , Address(rsp, 8+0));       // from
    __ movptr(to   , Address(rsp, 8+4));       // to
    __ movl2ptr(count, Address(rsp, 8+8));     // count

    *entry = __ pc(); // Entry point from generic arraycopy stub.
    BLOCK_COMMENT("Entry:");

    // arrays overlap test
    __ cmpptr(to, from);
    RuntimeAddress nooverlap(nooverlap_target);
    __ jump_cc(Assembler::belowEqual, nooverlap);
    __ lea(end_from, Address(from, count, Address::times_8, 0));
    __ cmpptr(to, end_from);
    __ movptr(from, Address(rsp, 8));  // from
    __ jump_cc(Assembler::aboveEqual, nooverlap);

    __ jmpb(L_copy_8_bytes);

    __ align(16);
  __ BIND(L_copy_8_bytes_loop);
    if (VM_Version::supports_mmx()) {
      if (UseXMMForArrayCopy) {
        __ movq(xmm0, Address(from, count, Address::times_8));
        __ movq(Address(to, count, Address::times_8), xmm0);
      } else {
        __ movq(mmx0, Address(from, count, Address::times_8));
        __ movq(Address(to, count, Address::times_8), mmx0);
      }
    } else {
      __ fild_d(Address(from, count, Address::times_8));
      __ fistp_d(Address(to, count, Address::times_8));
    }
  __ BIND(L_copy_8_bytes);
    __ decrement(count);
    __ jcc(Assembler::greaterEqual, L_copy_8_bytes_loop);

    if (VM_Version::supports_mmx() && !UseXMMForArrayCopy) {
      __ emms();
    }
    inc_copy_counter_np(T_LONG);
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ xorptr(rax, rax); // return 0
    __ ret(0);
    return start;
  }


  // Helper for generating a dynamic type check.
  // The sub_klass must be one of {rbx, rdx, rsi}.
  // The temp is killed.
  void generate_type_check(Register sub_klass,
                           Address& super_check_offset_addr,
                           Address& super_klass_addr,
                           Register temp,
                           Label* L_success, Label* L_failure) {
    BLOCK_COMMENT("type_check:");

    Label L_fallthrough;
#define LOCAL_JCC(assembler_con, label_ptr)                             \
    if (label_ptr != NULL)  __ jcc(assembler_con, *(label_ptr));        \
    else                    __ jcc(assembler_con, L_fallthrough) /*omit semi*/

    // The following is a strange variation of the fast path which requires
    // one less register, because needed values are on the argument stack.
    // __ check_klass_subtype_fast_path(sub_klass, *super_klass*, temp,
    //                                  L_success, L_failure, NULL);
    assert_different_registers(sub_klass, temp);

    int sc_offset = (klassOopDesc::header_size() * HeapWordSize +
                     Klass::secondary_super_cache_offset_in_bytes());

    // if the pointers are equal, we are done (e.g., String[] elements)
    __ cmpptr(sub_klass, super_klass_addr);
    LOCAL_JCC(Assembler::equal, L_success);

    // check the supertype display:
    __ movl2ptr(temp, super_check_offset_addr);
    Address super_check_addr(sub_klass, temp, Address::times_1, 0);
    __ movptr(temp, super_check_addr); // load displayed supertype
    __ cmpptr(temp, super_klass_addr); // test the super type
    LOCAL_JCC(Assembler::equal, L_success);

    // if it was a primary super, we can just fail immediately
    __ cmpl(super_check_offset_addr, sc_offset);
    LOCAL_JCC(Assembler::notEqual, L_failure);

    // The repne_scan instruction uses fixed registers, which will get spilled.
    // We happen to know this works best when super_klass is in rax.
    Register super_klass = temp;
    __ movptr(super_klass, super_klass_addr);
    __ check_klass_subtype_slow_path(sub_klass, super_klass, noreg, noreg,
                                     L_success, L_failure);

    __ bind(L_fallthrough);

    if (L_success == NULL) { BLOCK_COMMENT("L_success:"); }
    if (L_failure == NULL) { BLOCK_COMMENT("L_failure:"); }

#undef LOCAL_JCC
  }

  //
  //  Generate checkcasting array copy stub
  //
  //  Input:
  //    4(rsp)   - source array address
  //    8(rsp)   - destination array address
  //   12(rsp)   - element count, can be zero
  //   16(rsp)   - size_t ckoff (super_check_offset)
  //   20(rsp)   - oop ckval (super_klass)
  //
  //  Output:
  //    rax, ==  0  -  success
  //    rax, == -1^K - failure, where K is partial transfer count
  //
  address generate_checkcast_copy(const char *name, address* entry) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_load_element, L_store_element, L_do_card_marks, L_done;

    // register use:
    //  rax, rdx, rcx -- loop control (end_from, end_to, count)
    //  rdi, rsi      -- element access (oop, klass)
    //  rbx,           -- temp
    const Register from       = rax;    // source array address
    const Register to         = rdx;    // destination array address
    const Register length     = rcx;    // elements count
    const Register elem       = rdi;    // each oop copied
    const Register elem_klass = rsi;    // each elem._klass (sub_klass)
    const Register temp       = rbx;    // lone remaining temp

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    __ push(rsi);
    __ push(rdi);
    __ push(rbx);

    Address   from_arg(rsp, 16+ 4);     // from
    Address     to_arg(rsp, 16+ 8);     // to
    Address length_arg(rsp, 16+12);     // elements count
    Address  ckoff_arg(rsp, 16+16);     // super_check_offset
    Address  ckval_arg(rsp, 16+20);     // super_klass

    // Load up:
    __ movptr(from,     from_arg);
    __ movptr(to,         to_arg);
    __ movl2ptr(length, length_arg);

    *entry = __ pc(); // Entry point from generic arraycopy stub.
    BLOCK_COMMENT("Entry:");

    //---------------------------------------------------------------
    // Assembler stub will be used for this call to arraycopy
    // if the two arrays are subtypes of Object[] but the
    // destination array type is not equal to or a supertype
    // of the source type.  Each element must be separately
    // checked.

    // Loop-invariant addresses.  They are exclusive end pointers.
    Address end_from_addr(from, length, Address::times_ptr, 0);
    Address   end_to_addr(to,   length, Address::times_ptr, 0);

    Register end_from = from;           // re-use
    Register end_to   = to;             // re-use
    Register count    = length;         // re-use

    // Loop-variant addresses.  They assume post-incremented count < 0.
    Address from_element_addr(end_from, count, Address::times_ptr, 0);
    Address   to_element_addr(end_to,   count, Address::times_ptr, 0);
    Address elem_klass_addr(elem, oopDesc::klass_offset_in_bytes());

    // Copy from low to high addresses, indexed from the end of each array.
    gen_write_ref_array_pre_barrier(to, count);
    __ lea(end_from, end_from_addr);
    __ lea(end_to,   end_to_addr);
    assert(length == count, "");        // else fix next line:
    __ negptr(count);                   // negate and test the length
    __ jccb(Assembler::notZero, L_load_element);

    // Empty array:  Nothing to do.
    __ xorptr(rax, rax);                  // return 0 on (trivial) success
    __ jmp(L_done);

    // ======== begin loop ========
    // (Loop is rotated; its entry is L_load_element.)
    // Loop control:
    //   for (count = -count; count != 0; count++)
    // Base pointers src, dst are biased by 8*count,to last element.
    __ align(16);

    __ BIND(L_store_element);
    __ movptr(to_element_addr, elem);     // store the oop
    __ increment(count);                // increment the count toward zero
    __ jccb(Assembler::zero, L_do_card_marks);

    // ======== loop entry is here ========
    __ BIND(L_load_element);
    __ movptr(elem, from_element_addr);   // load the oop
    __ testptr(elem, elem);
    __ jccb(Assembler::zero, L_store_element);

    // (Could do a trick here:  Remember last successful non-null
    // element stored and make a quick oop equality check on it.)

    __ movptr(elem_klass, elem_klass_addr); // query the object klass
    generate_type_check(elem_klass, ckoff_arg, ckval_arg, temp,
                        &L_store_element, NULL);
      // (On fall-through, we have failed the element type check.)
    // ======== end loop ========

    // It was a real error; we must depend on the caller to finish the job.
    // Register "count" = -1 * number of *remaining* oops, length_arg = *total* oops.
    // Emit GC store barriers for the oops we have copied (length_arg + count),
    // and report their number to the caller.
    __ addl(count, length_arg);         // transfers = (length - remaining)
    __ movl2ptr(rax, count);            // save the value
    __ notptr(rax);                     // report (-1^K) to caller
    __ movptr(to, to_arg);              // reload
    assert_different_registers(to, count, rax);
    gen_write_ref_array_post_barrier(to, count);
    __ jmpb(L_done);

    // Come here on success only.
    __ BIND(L_do_card_marks);
    __ movl2ptr(count, length_arg);
    __ movptr(to, to_arg);                // reload
    gen_write_ref_array_post_barrier(to, count);
    __ xorptr(rax, rax);                  // return 0 on success

    // Common exit point (success or failure).
    __ BIND(L_done);
    __ pop(rbx);
    __ pop(rdi);
    __ pop(rsi);
    inc_counter_np(SharedRuntime::_checkcast_array_copy_ctr);
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;
  }

  //
  //  Generate 'unsafe' array copy stub
  //  Though just as safe as the other stubs, it takes an unscaled
  //  size_t argument instead of an element count.
  //
  //  Input:
  //    4(rsp)   - source array address
  //    8(rsp)   - destination array address
  //   12(rsp)   - byte count, can be zero
  //
  //  Output:
  //    rax, ==  0  -  success
  //    rax, == -1  -  need to call System.arraycopy
  //
  // Examines the alignment of the operands and dispatches
  // to a long, int, short, or byte copy loop.
  //
  address generate_unsafe_copy(const char *name,
                               address byte_copy_entry,
                               address short_copy_entry,
                               address int_copy_entry,
                               address long_copy_entry) {

    Label L_long_aligned, L_int_aligned, L_short_aligned;

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    const Register from       = rax;  // source array address
    const Register to         = rdx;  // destination array address
    const Register count      = rcx;  // elements count

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    __ push(rsi);
    __ push(rdi);
    Address  from_arg(rsp, 12+ 4);      // from
    Address    to_arg(rsp, 12+ 8);      // to
    Address count_arg(rsp, 12+12);      // byte count

    // Load up:
    __ movptr(from ,  from_arg);
    __ movptr(to   ,    to_arg);
    __ movl2ptr(count, count_arg);

    // bump this on entry, not on exit:
    inc_counter_np(SharedRuntime::_unsafe_array_copy_ctr);

    const Register bits = rsi;
    __ mov(bits, from);
    __ orptr(bits, to);
    __ orptr(bits, count);

    __ testl(bits, BytesPerLong-1);
    __ jccb(Assembler::zero, L_long_aligned);

    __ testl(bits, BytesPerInt-1);
    __ jccb(Assembler::zero, L_int_aligned);

    __ testl(bits, BytesPerShort-1);
    __ jump_cc(Assembler::notZero, RuntimeAddress(byte_copy_entry));

    __ BIND(L_short_aligned);
    __ shrptr(count, LogBytesPerShort); // size => short_count
    __ movl(count_arg, count);          // update 'count'
    __ jump(RuntimeAddress(short_copy_entry));

    __ BIND(L_int_aligned);
    __ shrptr(count, LogBytesPerInt); // size => int_count
    __ movl(count_arg, count);          // update 'count'
    __ jump(RuntimeAddress(int_copy_entry));

    __ BIND(L_long_aligned);
    __ shrptr(count, LogBytesPerLong); // size => qword_count
    __ movl(count_arg, count);          // update 'count'
    __ pop(rdi); // Do pops here since jlong_arraycopy stub does not do it.
    __ pop(rsi);
    __ jump(RuntimeAddress(long_copy_entry));

    return start;
  }


  // Perform range checks on the proposed arraycopy.
  // Smashes src_pos and dst_pos.  (Uses them up for temps.)
  void arraycopy_range_checks(Register src,
                              Register src_pos,
                              Register dst,
                              Register dst_pos,
                              Address& length,
                              Label& L_failed) {
    BLOCK_COMMENT("arraycopy_range_checks:");
    const Register src_end = src_pos;   // source array end position
    const Register dst_end = dst_pos;   // destination array end position
    __ addl(src_end, length); // src_pos + length
    __ addl(dst_end, length); // dst_pos + length

    //  if (src_pos + length > arrayOop(src)->length() ) FAIL;
    __ cmpl(src_end, Address(src, arrayOopDesc::length_offset_in_bytes()));
    __ jcc(Assembler::above, L_failed);

    //  if (dst_pos + length > arrayOop(dst)->length() ) FAIL;
    __ cmpl(dst_end, Address(dst, arrayOopDesc::length_offset_in_bytes()));
    __ jcc(Assembler::above, L_failed);

    BLOCK_COMMENT("arraycopy_range_checks done");
  }


  //
  //  Generate generic array copy stubs
  //
  //  Input:
  //     4(rsp)    -  src oop
  //     8(rsp)    -  src_pos
  //    12(rsp)    -  dst oop
  //    16(rsp)    -  dst_pos
  //    20(rsp)    -  element count
  //
  //  Output:
  //    rax, ==  0  -  success
  //    rax, == -1^K - failure, where K is partial transfer count
  //
  address generate_generic_copy(const char *name,
                                address entry_jbyte_arraycopy,
                                address entry_jshort_arraycopy,
                                address entry_jint_arraycopy,
                                address entry_oop_arraycopy,
                                address entry_jlong_arraycopy,
                                address entry_checkcast_arraycopy) {
    Label L_failed, L_failed_0, L_objArray;

    { int modulus = CodeEntryAlignment;
      int target  = modulus - 5; // 5 = sizeof jmp(L_failed)
      int advance = target - (__ offset() % modulus);
      if (advance < 0)  advance += modulus;
      if (advance > 0)  __ nop(advance);
    }
    StubCodeMark mark(this, "StubRoutines", name);

    // Short-hop target to L_failed.  Makes for denser prologue code.
    __ BIND(L_failed_0);
    __ jmp(L_failed);
    assert(__ offset() % CodeEntryAlignment == 0, "no further alignment needed");

    __ align(CodeEntryAlignment);
    address start = __ pc();

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    __ push(rsi);
    __ push(rdi);

    // bump this on entry, not on exit:
    inc_counter_np(SharedRuntime::_generic_array_copy_ctr);

    // Input values
    Address SRC     (rsp, 12+ 4);
    Address SRC_POS (rsp, 12+ 8);
    Address DST     (rsp, 12+12);
    Address DST_POS (rsp, 12+16);
    Address LENGTH  (rsp, 12+20);

    //-----------------------------------------------------------------------
    // Assembler stub will be used for this call to arraycopy
    // if the following conditions are met:
    //
    // (1) src and dst must not be null.
    // (2) src_pos must not be negative.
    // (3) dst_pos must not be negative.
    // (4) length  must not be negative.
    // (5) src klass and dst klass should be the same and not NULL.
    // (6) src and dst should be arrays.
    // (7) src_pos + length must not exceed length of src.
    // (8) dst_pos + length must not exceed length of dst.
    //

    const Register src     = rax;       // source array oop
    const Register src_pos = rsi;
    const Register dst     = rdx;       // destination array oop
    const Register dst_pos = rdi;
    const Register length  = rcx;       // transfer count

    //  if (src == NULL) return -1;
    __ movptr(src, SRC);      // src oop
    __ testptr(src, src);
    __ jccb(Assembler::zero, L_failed_0);

    //  if (src_pos < 0) return -1;
    __ movl2ptr(src_pos, SRC_POS);  // src_pos
    __ testl(src_pos, src_pos);
    __ jccb(Assembler::negative, L_failed_0);

    //  if (dst == NULL) return -1;
    __ movptr(dst, DST);      // dst oop
    __ testptr(dst, dst);
    __ jccb(Assembler::zero, L_failed_0);

    //  if (dst_pos < 0) return -1;
    __ movl2ptr(dst_pos, DST_POS);  // dst_pos
    __ testl(dst_pos, dst_pos);
    __ jccb(Assembler::negative, L_failed_0);

    //  if (length < 0) return -1;
    __ movl2ptr(length, LENGTH);   // length
    __ testl(length, length);
    __ jccb(Assembler::negative, L_failed_0);

    //  if (src->klass() == NULL) return -1;
    Address src_klass_addr(src, oopDesc::klass_offset_in_bytes());
    Address dst_klass_addr(dst, oopDesc::klass_offset_in_bytes());
    const Register rcx_src_klass = rcx;    // array klass
    __ movptr(rcx_src_klass, Address(src, oopDesc::klass_offset_in_bytes()));

#ifdef ASSERT
    //  assert(src->klass() != NULL);
    BLOCK_COMMENT("assert klasses not null");
    { Label L1, L2;
      __ testptr(rcx_src_klass, rcx_src_klass);
      __ jccb(Assembler::notZero, L2);   // it is broken if klass is NULL
      __ bind(L1);
      __ stop("broken null klass");
      __ bind(L2);
      __ cmpptr(dst_klass_addr, (int32_t)NULL_WORD);
      __ jccb(Assembler::equal, L1);      // this would be broken also
      BLOCK_COMMENT("assert done");
    }
#endif //ASSERT

    // Load layout helper (32-bits)
    //
    //  |array_tag|     | header_size | element_type |     |log2_element_size|
    // 32        30    24            16              8     2                 0
    //
    //   array_tag: typeArray = 0x3, objArray = 0x2, non-array = 0x0
    //

    int lh_offset = klassOopDesc::header_size() * HeapWordSize +
                    Klass::layout_helper_offset_in_bytes();
    Address src_klass_lh_addr(rcx_src_klass, lh_offset);

    // Handle objArrays completely differently...
    jint objArray_lh = Klass::array_layout_helper(T_OBJECT);
    __ cmpl(src_klass_lh_addr, objArray_lh);
    __ jcc(Assembler::equal, L_objArray);

    //  if (src->klass() != dst->klass()) return -1;
    __ cmpptr(rcx_src_klass, dst_klass_addr);
    __ jccb(Assembler::notEqual, L_failed_0);

    const Register rcx_lh = rcx;  // layout helper
    assert(rcx_lh == rcx_src_klass, "known alias");
    __ movl(rcx_lh, src_klass_lh_addr);

    //  if (!src->is_Array()) return -1;
    __ cmpl(rcx_lh, Klass::_lh_neutral_value);
    __ jcc(Assembler::greaterEqual, L_failed_0); // signed cmp

    // At this point, it is known to be a typeArray (array_tag 0x3).
#ifdef ASSERT
    { Label L;
      __ cmpl(rcx_lh, (Klass::_lh_array_tag_type_value << Klass::_lh_array_tag_shift));
      __ jcc(Assembler::greaterEqual, L); // signed cmp
      __ stop("must be a primitive array");
      __ bind(L);
    }
#endif

    assert_different_registers(src, src_pos, dst, dst_pos, rcx_lh);
    arraycopy_range_checks(src, src_pos, dst, dst_pos, LENGTH, L_failed);

    // typeArrayKlass
    //
    // src_addr = (src + array_header_in_bytes()) + (src_pos << log2elemsize);
    // dst_addr = (dst + array_header_in_bytes()) + (dst_pos << log2elemsize);
    //
    const Register rsi_offset = rsi; // array offset
    const Register src_array  = src; // src array offset
    const Register dst_array  = dst; // dst array offset
    const Register rdi_elsize = rdi; // log2 element size

    __ mov(rsi_offset, rcx_lh);
    __ shrptr(rsi_offset, Klass::_lh_header_size_shift);
    __ andptr(rsi_offset, Klass::_lh_header_size_mask);   // array_offset
    __ addptr(src_array, rsi_offset);  // src array offset
    __ addptr(dst_array, rsi_offset);  // dst array offset
    __ andptr(rcx_lh, Klass::_lh_log2_element_size_mask); // log2 elsize

    // next registers should be set before the jump to corresponding stub
    const Register from       = src; // source array address
    const Register to         = dst; // destination array address
    const Register count      = rcx; // elements count
    // some of them should be duplicated on stack
#define FROM   Address(rsp, 12+ 4)
#define TO     Address(rsp, 12+ 8)   // Not used now
#define COUNT  Address(rsp, 12+12)   // Only for oop arraycopy

    BLOCK_COMMENT("scale indexes to element size");
    __ movl2ptr(rsi, SRC_POS);  // src_pos
    __ shlptr(rsi);             // src_pos << rcx (log2 elsize)
    assert(src_array == from, "");
    __ addptr(from, rsi);       // from = src_array + SRC_POS << log2 elsize
    __ movl2ptr(rdi, DST_POS);  // dst_pos
    __ shlptr(rdi);             // dst_pos << rcx (log2 elsize)
    assert(dst_array == to, "");
    __ addptr(to,  rdi);        // to   = dst_array + DST_POS << log2 elsize
    __ movptr(FROM, from);      // src_addr
    __ mov(rdi_elsize, rcx_lh); // log2 elsize
    __ movl2ptr(count, LENGTH); // elements count

    BLOCK_COMMENT("choose copy loop based on element size");
    __ cmpl(rdi_elsize, 0);

    __ jump_cc(Assembler::equal, RuntimeAddress(entry_jbyte_arraycopy));
    __ cmpl(rdi_elsize, LogBytesPerShort);
    __ jump_cc(Assembler::equal, RuntimeAddress(entry_jshort_arraycopy));
    __ cmpl(rdi_elsize, LogBytesPerInt);
    __ jump_cc(Assembler::equal, RuntimeAddress(entry_jint_arraycopy));
#ifdef ASSERT
    __ cmpl(rdi_elsize, LogBytesPerLong);
    __ jccb(Assembler::notEqual, L_failed);
#endif
    __ pop(rdi); // Do pops here since jlong_arraycopy stub does not do it.
    __ pop(rsi);
    __ jump(RuntimeAddress(entry_jlong_arraycopy));

  __ BIND(L_failed);
    __ xorptr(rax, rax);
    __ notptr(rax); // return -1
    __ pop(rdi);
    __ pop(rsi);
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    // objArrayKlass
  __ BIND(L_objArray);
    // live at this point:  rcx_src_klass, src[_pos], dst[_pos]

    Label L_plain_copy, L_checkcast_copy;
    //  test array classes for subtyping
    __ cmpptr(rcx_src_klass, dst_klass_addr); // usual case is exact equality
    __ jccb(Assembler::notEqual, L_checkcast_copy);

    // Identically typed arrays can be copied without element-wise checks.
    assert_different_registers(src, src_pos, dst, dst_pos, rcx_src_klass);
    arraycopy_range_checks(src, src_pos, dst, dst_pos, LENGTH, L_failed);

  __ BIND(L_plain_copy);
    __ movl2ptr(count, LENGTH); // elements count
    __ movl2ptr(src_pos, SRC_POS);  // reload src_pos
    __ lea(from, Address(src, src_pos, Address::times_ptr,
                 arrayOopDesc::base_offset_in_bytes(T_OBJECT))); // src_addr
    __ movl2ptr(dst_pos, DST_POS);  // reload dst_pos
    __ lea(to,   Address(dst, dst_pos, Address::times_ptr,
                 arrayOopDesc::base_offset_in_bytes(T_OBJECT))); // dst_addr
    __ movptr(FROM,  from);   // src_addr
    __ movptr(TO,    to);     // dst_addr
    __ movl(COUNT, count);  // count
    __ jump(RuntimeAddress(entry_oop_arraycopy));

  __ BIND(L_checkcast_copy);
    // live at this point:  rcx_src_klass, dst[_pos], src[_pos]
    {
      // Handy offsets:
      int  ek_offset = (klassOopDesc::header_size() * HeapWordSize +
                        objArrayKlass::element_klass_offset_in_bytes());
      int sco_offset = (klassOopDesc::header_size() * HeapWordSize +
                        Klass::super_check_offset_offset_in_bytes());

      Register rsi_dst_klass = rsi;
      Register rdi_temp      = rdi;
      assert(rsi_dst_klass == src_pos, "expected alias w/ src_pos");
      assert(rdi_temp      == dst_pos, "expected alias w/ dst_pos");
      Address dst_klass_lh_addr(rsi_dst_klass, lh_offset);

      // Before looking at dst.length, make sure dst is also an objArray.
      __ movptr(rsi_dst_klass, dst_klass_addr);
      __ cmpl(dst_klass_lh_addr, objArray_lh);
      __ jccb(Assembler::notEqual, L_failed);

      // It is safe to examine both src.length and dst.length.
      __ movl2ptr(src_pos, SRC_POS);        // reload rsi
      arraycopy_range_checks(src, src_pos, dst, dst_pos, LENGTH, L_failed);
      // (Now src_pos and dst_pos are killed, but not src and dst.)

      // We'll need this temp (don't forget to pop it after the type check).
      __ push(rbx);
      Register rbx_src_klass = rbx;

      __ mov(rbx_src_klass, rcx_src_klass); // spill away from rcx
      __ movptr(rsi_dst_klass, dst_klass_addr);
      Address super_check_offset_addr(rsi_dst_klass, sco_offset);
      Label L_fail_array_check;
      generate_type_check(rbx_src_klass,
                          super_check_offset_addr, dst_klass_addr,
                          rdi_temp, NULL, &L_fail_array_check);
      // (On fall-through, we have passed the array type check.)
      __ pop(rbx);
      __ jmp(L_plain_copy);

      __ BIND(L_fail_array_check);
      // Reshuffle arguments so we can call checkcast_arraycopy:

      // match initial saves for checkcast_arraycopy
      // push(rsi);    // already done; see above
      // push(rdi);    // already done; see above
      // push(rbx);    // already done; see above

      // Marshal outgoing arguments now, freeing registers.
      Address   from_arg(rsp, 16+ 4);   // from
      Address     to_arg(rsp, 16+ 8);   // to
      Address length_arg(rsp, 16+12);   // elements count
      Address  ckoff_arg(rsp, 16+16);   // super_check_offset
      Address  ckval_arg(rsp, 16+20);   // super_klass

      Address SRC_POS_arg(rsp, 16+ 8);
      Address DST_POS_arg(rsp, 16+16);
      Address  LENGTH_arg(rsp, 16+20);
      // push rbx, changed the incoming offsets (why not just use rbp,??)
      // assert(SRC_POS_arg.disp() == SRC_POS.disp() + 4, "");

      __ movptr(rbx, Address(rsi_dst_klass, ek_offset));
      __ movl2ptr(length, LENGTH_arg);    // reload elements count
      __ movl2ptr(src_pos, SRC_POS_arg);  // reload src_pos
      __ movl2ptr(dst_pos, DST_POS_arg);  // reload dst_pos

      __ movptr(ckval_arg, rbx);          // destination element type
      __ movl(rbx, Address(rbx, sco_offset));
      __ movl(ckoff_arg, rbx);          // corresponding class check offset

      __ movl(length_arg, length);      // outgoing length argument

      __ lea(from, Address(src, src_pos, Address::times_ptr,
                            arrayOopDesc::base_offset_in_bytes(T_OBJECT)));
      __ movptr(from_arg, from);

      __ lea(to, Address(dst, dst_pos, Address::times_ptr,
                          arrayOopDesc::base_offset_in_bytes(T_OBJECT)));
      __ movptr(to_arg, to);
      __ jump(RuntimeAddress(entry_checkcast_arraycopy));
    }

    return start;
  }

  void generate_arraycopy_stubs() {
    address entry;
    address entry_jbyte_arraycopy;
    address entry_jshort_arraycopy;
    address entry_jint_arraycopy;
    address entry_oop_arraycopy;
    address entry_jlong_arraycopy;
    address entry_checkcast_arraycopy;

    StubRoutines::_arrayof_jbyte_disjoint_arraycopy =
        generate_disjoint_copy(T_BYTE,  true, Address::times_1, &entry,
                               "arrayof_jbyte_disjoint_arraycopy");
    StubRoutines::_arrayof_jbyte_arraycopy =
        generate_conjoint_copy(T_BYTE,  true, Address::times_1,  entry,
                               NULL, "arrayof_jbyte_arraycopy");
    StubRoutines::_jbyte_disjoint_arraycopy =
        generate_disjoint_copy(T_BYTE, false, Address::times_1, &entry,
                               "jbyte_disjoint_arraycopy");
    StubRoutines::_jbyte_arraycopy =
        generate_conjoint_copy(T_BYTE, false, Address::times_1,  entry,
                               &entry_jbyte_arraycopy, "jbyte_arraycopy");

    StubRoutines::_arrayof_jshort_disjoint_arraycopy =
        generate_disjoint_copy(T_SHORT,  true, Address::times_2, &entry,
                               "arrayof_jshort_disjoint_arraycopy");
    StubRoutines::_arrayof_jshort_arraycopy =
        generate_conjoint_copy(T_SHORT,  true, Address::times_2,  entry,
                               NULL, "arrayof_jshort_arraycopy");
    StubRoutines::_jshort_disjoint_arraycopy =
        generate_disjoint_copy(T_SHORT, false, Address::times_2, &entry,
                               "jshort_disjoint_arraycopy");
    StubRoutines::_jshort_arraycopy =
        generate_conjoint_copy(T_SHORT, false, Address::times_2,  entry,
                               &entry_jshort_arraycopy, "jshort_arraycopy");

    // Next arrays are always aligned on 4 bytes at least.
    StubRoutines::_jint_disjoint_arraycopy =
        generate_disjoint_copy(T_INT, true, Address::times_4, &entry,
                               "jint_disjoint_arraycopy");
    StubRoutines::_jint_arraycopy =
        generate_conjoint_copy(T_INT, true, Address::times_4,  entry,
                               &entry_jint_arraycopy, "jint_arraycopy");

    StubRoutines::_oop_disjoint_arraycopy =
        generate_disjoint_copy(T_OBJECT, true, Address::times_ptr, &entry,
                               "oop_disjoint_arraycopy");
    StubRoutines::_oop_arraycopy =
        generate_conjoint_copy(T_OBJECT, true, Address::times_ptr,  entry,
                               &entry_oop_arraycopy, "oop_arraycopy");

    StubRoutines::_jlong_disjoint_arraycopy =
        generate_disjoint_long_copy(&entry, "jlong_disjoint_arraycopy");
    StubRoutines::_jlong_arraycopy =
        generate_conjoint_long_copy(entry, &entry_jlong_arraycopy,
                                    "jlong_arraycopy");

    StubRoutines::_arrayof_jint_disjoint_arraycopy  =
        StubRoutines::_jint_disjoint_arraycopy;
    StubRoutines::_arrayof_oop_disjoint_arraycopy   =
        StubRoutines::_oop_disjoint_arraycopy;
    StubRoutines::_arrayof_jlong_disjoint_arraycopy =
        StubRoutines::_jlong_disjoint_arraycopy;

    StubRoutines::_arrayof_jint_arraycopy  = StubRoutines::_jint_arraycopy;
    StubRoutines::_arrayof_oop_arraycopy   = StubRoutines::_oop_arraycopy;
    StubRoutines::_arrayof_jlong_arraycopy = StubRoutines::_jlong_arraycopy;

    StubRoutines::_checkcast_arraycopy =
        generate_checkcast_copy("checkcast_arraycopy",
                                  &entry_checkcast_arraycopy);

    StubRoutines::_unsafe_arraycopy =
        generate_unsafe_copy("unsafe_arraycopy",
                               entry_jbyte_arraycopy,
                               entry_jshort_arraycopy,
                               entry_jint_arraycopy,
                               entry_jlong_arraycopy);

    StubRoutines::_generic_arraycopy =
        generate_generic_copy("generic_arraycopy",
                               entry_jbyte_arraycopy,
                               entry_jshort_arraycopy,
                               entry_jint_arraycopy,
                               entry_oop_arraycopy,
                               entry_jlong_arraycopy,
                               entry_checkcast_arraycopy);
  }

 public:
  // Information about frame layout at time of blocking runtime call.
  // Note that we only have to preserve callee-saved registers since
  // the compilers are responsible for supplying a continuation point
  // if they expect all registers to be preserved.
  enum layout {
    thread_off,    // last_java_sp
    rbp_off,       // callee saved register
    ret_pc,
    framesize
  };

 private:

#undef  __
#define __ masm->

  //------------------------------------------------------------------------------------------------------------------------
  // Continuation point for throwing of implicit exceptions that are not handled in
  // the current activation. Fabricates an exception oop and initiates normal
  // exception dispatching in this frame.
  //
  // Previously the compiler (c2) allowed for callee save registers on Java calls.
  // This is no longer true after adapter frames were removed but could possibly
  // be brought back in the future if the interpreter code was reworked and it
  // was deemed worthwhile. The comment below was left to describe what must
  // happen here if callee saves were resurrected. As it stands now this stub
  // could actually be a vanilla BufferBlob and have now oopMap at all.
  // Since it doesn't make much difference we've chosen to leave it the
  // way it was in the callee save days and keep the comment.

  // If we need to preserve callee-saved values we need a callee-saved oop map and
  // therefore have to make these stubs into RuntimeStubs rather than BufferBlobs.
  // If the compiler needs all registers to be preserved between the fault
  // point and the exception handler then it must assume responsibility for that in
  // AbstractCompiler::continuation_for_implicit_null_exception or
  // continuation_for_implicit_division_by_zero_exception. All other implicit
  // exceptions (e.g., NullPointerException or AbstractMethodError on entry) are
  // either at call sites or otherwise assume that stack unwinding will be initiated,
  // so caller saved registers were assumed volatile in the compiler.
  address generate_throw_exception(const char* name, address runtime_entry,
                                   bool restore_saved_exception_pc) {

    int insts_size = 256;
    int locs_size  = 32;

    CodeBuffer code(name, insts_size, locs_size);
    OopMapSet* oop_maps  = new OopMapSet();
    MacroAssembler* masm = new MacroAssembler(&code);

    address start = __ pc();

    // This is an inlined and slightly modified version of call_VM
    // which has the ability to fetch the return PC out of
    // thread-local storage and also sets up last_Java_sp slightly
    // differently than the real call_VM
    Register java_thread = rbx;
    __ get_thread(java_thread);
    if (restore_saved_exception_pc) {
      __ movptr(rax, Address(java_thread, in_bytes(JavaThread::saved_exception_pc_offset())));
      __ push(rax);
    }

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // pc and rbp, already pushed
    __ subptr(rsp, (framesize-2) * wordSize); // prolog

    // Frame is now completed as far as size and linkage.

    int frame_complete = __ pc() - start;

    // push java thread (becomes first argument of C function)
    __ movptr(Address(rsp, thread_off * wordSize), java_thread);

    // Set up last_Java_sp and last_Java_fp
    __ set_last_Java_frame(java_thread, rsp, rbp, NULL);

    // Call runtime
    BLOCK_COMMENT("call runtime_entry");
    __ call(RuntimeAddress(runtime_entry));
    // Generate oop map
    OopMap* map =  new OopMap(framesize, 0);
    oop_maps->add_gc_map(__ pc() - start, map);

    // restore the thread (cannot use the pushed argument since arguments
    // may be overwritten by C code generated by an optimizing compiler);
    // however can use the register value directly if it is callee saved.
    __ get_thread(java_thread);

    __ reset_last_Java_frame(java_thread, true, false);

    __ leave(); // required for proper stackwalking of RuntimeStub frame

    // check for pending exceptions
#ifdef ASSERT
    Label L;
    __ cmpptr(Address(java_thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::notEqual, L);
    __ should_not_reach_here();
    __ bind(L);
#endif /* ASSERT */
    __ jump(RuntimeAddress(StubRoutines::forward_exception_entry()));


    RuntimeStub* stub = RuntimeStub::new_runtime_stub(name, &code, frame_complete, framesize, oop_maps, false);
    return stub->entry_point();
  }


  void create_control_words() {
    // Round to nearest, 53-bit mode, exceptions masked
    StubRoutines::_fpu_cntrl_wrd_std   = 0x027F;
    // Round to zero, 53-bit mode, exception mased
    StubRoutines::_fpu_cntrl_wrd_trunc = 0x0D7F;
    // Round to nearest, 24-bit mode, exceptions masked
    StubRoutines::_fpu_cntrl_wrd_24    = 0x007F;
    // Round to nearest, 64-bit mode, exceptions masked
    StubRoutines::_fpu_cntrl_wrd_64    = 0x037F;
    // Round to nearest, 64-bit mode, exceptions masked
    StubRoutines::_mxcsr_std           = 0x1F80;
    // Note: the following two constants are 80-bit values
    //       layout is critical for correct loading by FPU.
    // Bias for strict fp multiply/divide
    StubRoutines::_fpu_subnormal_bias1[0]= 0x00000000; // 2^(-15360) == 0x03ff 8000 0000 0000 0000
    StubRoutines::_fpu_subnormal_bias1[1]= 0x80000000;
    StubRoutines::_fpu_subnormal_bias1[2]= 0x03ff;
    // Un-Bias for strict fp multiply/divide
    StubRoutines::_fpu_subnormal_bias2[0]= 0x00000000; // 2^(+15360) == 0x7bff 8000 0000 0000 0000
    StubRoutines::_fpu_subnormal_bias2[1]= 0x80000000;
    StubRoutines::_fpu_subnormal_bias2[2]= 0x7bff;
  }

  //---------------------------------------------------------------------------
  // Initialization

  void generate_initial() {
    // Generates all stubs and initializes the entry points

    //------------------------------------------------------------------------------------------------------------------------
    // entry points that exist in all platforms
    // Note: This is code that could be shared among different platforms - however the benefit seems to be smaller than
    //       the disadvantage of having a much more complicated generator structure. See also comment in stubRoutines.hpp.
    StubRoutines::_forward_exception_entry      = generate_forward_exception();

    StubRoutines::_call_stub_entry              =
      generate_call_stub(StubRoutines::_call_stub_return_address);
    // is referenced by megamorphic call
    StubRoutines::_catch_exception_entry        = generate_catch_exception();

    // These are currently used by Solaris/Intel
    StubRoutines::_atomic_xchg_entry            = generate_atomic_xchg();

    StubRoutines::_handler_for_unsafe_access_entry =
      generate_handler_for_unsafe_access();

    // platform dependent
    create_control_words();

    StubRoutines::x86::_verify_mxcsr_entry                 = generate_verify_mxcsr();
    StubRoutines::x86::_verify_fpu_cntrl_wrd_entry         = generate_verify_fpu_cntrl_wrd();
    StubRoutines::_d2i_wrapper                              = generate_d2i_wrapper(T_INT,
                                                                                   CAST_FROM_FN_PTR(address, SharedRuntime::d2i));
    StubRoutines::_d2l_wrapper                              = generate_d2i_wrapper(T_LONG,
                                                                                   CAST_FROM_FN_PTR(address, SharedRuntime::d2l));
  }


  void generate_all() {
    // Generates all stubs and initializes the entry points

    // These entry points require SharedInfo::stack0 to be set up in non-core builds
    // and need to be relocatable, so they each fabricate a RuntimeStub internally.
    StubRoutines::_throw_AbstractMethodError_entry         = generate_throw_exception("AbstractMethodError throw_exception",          CAST_FROM_FN_PTR(address, SharedRuntime::throw_AbstractMethodError),  false);
    StubRoutines::_throw_IncompatibleClassChangeError_entry= generate_throw_exception("IncompatibleClassChangeError throw_exception", CAST_FROM_FN_PTR(address, SharedRuntime::throw_IncompatibleClassChangeError),  false);
    StubRoutines::_throw_ArithmeticException_entry         = generate_throw_exception("ArithmeticException throw_exception",          CAST_FROM_FN_PTR(address, SharedRuntime::throw_ArithmeticException),  true);
    StubRoutines::_throw_NullPointerException_entry        = generate_throw_exception("NullPointerException throw_exception",         CAST_FROM_FN_PTR(address, SharedRuntime::throw_NullPointerException), true);
    StubRoutines::_throw_NullPointerException_at_call_entry= generate_throw_exception("NullPointerException at call throw_exception", CAST_FROM_FN_PTR(address, SharedRuntime::throw_NullPointerException_at_call), false);
    StubRoutines::_throw_StackOverflowError_entry          = generate_throw_exception("StackOverflowError throw_exception",           CAST_FROM_FN_PTR(address, SharedRuntime::throw_StackOverflowError),   false);

    //------------------------------------------------------------------------------------------------------------------------
    // entry points that are platform specific

    // support for verify_oop (must happen after universe_init)
    StubRoutines::_verify_oop_subroutine_entry     = generate_verify_oop();

    // arraycopy stubs used by compilers
    generate_arraycopy_stubs();

    // generic method handle stubs
    if (EnableMethodHandles && SystemDictionary::MethodHandle_klass() != NULL) {
      for (MethodHandles::EntryKind ek = MethodHandles::_EK_FIRST;
           ek < MethodHandles::_EK_LIMIT;
           ek = MethodHandles::EntryKind(1 + (int)ek)) {
        StubCodeMark mark(this, "MethodHandle", MethodHandles::entry_name(ek));
        MethodHandles::generate_method_handle_stub(_masm, ek);
      }
    }
  }


 public:
  StubGenerator(CodeBuffer* code, bool all) : StubCodeGenerator(code) {
    if (all) {
      generate_all();
    } else {
      generate_initial();
    }
  }
}; // end class declaration


void StubGenerator_generate(CodeBuffer* code, bool all) {
  StubGenerator g(code, all);
}
