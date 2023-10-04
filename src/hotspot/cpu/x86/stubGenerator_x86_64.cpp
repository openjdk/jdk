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
#include "asm/macroAssembler.hpp"
#include "classfile/vmIntrinsics.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/universe.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "stubGenerator_x86_64.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#include "opto/c2_globals.hpp"
#endif
#if INCLUDE_JVMCI
#include "jvmci/jvmci_globals.hpp"
#endif
#if INCLUDE_JFR
#include "jfr/support/jfrIntrinsics.hpp"
#endif

// For a more detailed description of the stub routine structure
// see the comment in stubRoutines.hpp

#define __ _masm->
#define TIMES_OOP (UseCompressedOops ? Address::times_4 : Address::times_8)

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif // PRODUCT

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

//
// Linux Arguments:
//    c_rarg0:   call wrapper address                   address
//    c_rarg1:   result                                 address
//    c_rarg2:   result type                            BasicType
//    c_rarg3:   method                                 Method*
//    c_rarg4:   (interpreter) entry point              address
//    c_rarg5:   parameters                             intptr_t*
//    16(rbp): parameter size (in words)              int
//    24(rbp): thread                                 Thread*
//
//     [ return_from_Java     ] <--- rsp
//     [ argument word n      ]
//      ...
// -12 [ argument word 1      ]
// -11 [ saved r15            ] <--- rsp_after_call
// -10 [ saved r14            ]
//  -9 [ saved r13            ]
//  -8 [ saved r12            ]
//  -7 [ saved rbx            ]
//  -6 [ call wrapper         ]
//  -5 [ result               ]
//  -4 [ result type          ]
//  -3 [ method               ]
//  -2 [ entry point          ]
//  -1 [ parameters           ]
//   0 [ saved rbp            ] <--- rbp
//   1 [ return address       ]
//   2 [ parameter size       ]
//   3 [ thread               ]
//
// Windows Arguments:
//    c_rarg0:   call wrapper address                   address
//    c_rarg1:   result                                 address
//    c_rarg2:   result type                            BasicType
//    c_rarg3:   method                                 Method*
//    48(rbp): (interpreter) entry point              address
//    56(rbp): parameters                             intptr_t*
//    64(rbp): parameter size (in words)              int
//    72(rbp): thread                                 Thread*
//
//     [ return_from_Java     ] <--- rsp
//     [ argument word n      ]
//      ...
// -28 [ argument word 1      ]
// -27 [ saved xmm15          ] <--- rsp after_call
//     [ saved xmm7-xmm14     ]
//  -9 [ saved xmm6           ] (each xmm register takes 2 slots)
//  -7 [ saved r15            ]
//  -6 [ saved r14            ]
//  -5 [ saved r13            ]
//  -4 [ saved r12            ]
//  -3 [ saved rdi            ]
//  -2 [ saved rsi            ]
//  -1 [ saved rbx            ]
//   0 [ saved rbp            ] <--- rbp
//   1 [ return address       ]
//   2 [ call wrapper         ]
//   3 [ result               ]
//   4 [ result type          ]
//   5 [ method               ]
//   6 [ entry point          ]
//   7 [ parameters           ]
//   8 [ parameter size       ]
//   9 [ thread               ]
//
//    Windows reserves the callers stack space for arguments 1-4.
//    We spill c_rarg0-c_rarg3 to this space.

// Call stub stack layout word offsets from rbp
#ifdef _WIN64
enum call_stub_layout {
  xmm_save_first     = 6,  // save from xmm6
  xmm_save_last      = 15, // to xmm15
  xmm_save_base      = -9,
  rsp_after_call_off = xmm_save_base - 2 * (xmm_save_last - xmm_save_first), // -27
  r15_off            = -7,
  r14_off            = -6,
  r13_off            = -5,
  r12_off            = -4,
  rdi_off            = -3,
  rsi_off            = -2,
  rbx_off            = -1,
  rbp_off            =  0,
  retaddr_off        =  1,
  call_wrapper_off   =  2,
  result_off         =  3,
  result_type_off    =  4,
  method_off         =  5,
  entry_point_off    =  6,
  parameters_off     =  7,
  parameter_size_off =  8,
  thread_off         =  9
};

static Address xmm_save(int reg) {
  assert(reg >= xmm_save_first && reg <= xmm_save_last, "XMM register number out of range");
  return Address(rbp, (xmm_save_base - (reg - xmm_save_first) * 2) * wordSize);
}
#else // !_WIN64
enum call_stub_layout {
  rsp_after_call_off = -12,
  mxcsr_off          = rsp_after_call_off,
  r15_off            = -11,
  r14_off            = -10,
  r13_off            = -9,
  r12_off            = -8,
  rbx_off            = -7,
  call_wrapper_off   = -6,
  result_off         = -5,
  result_type_off    = -4,
  method_off         = -3,
  entry_point_off    = -2,
  parameters_off     = -1,
  rbp_off            =  0,
  retaddr_off        =  1,
  parameter_size_off =  2,
  thread_off         =  3
};
#endif // _WIN64

address StubGenerator::generate_call_stub(address& return_address) {

  assert((int)frame::entry_frame_after_call_words == -(int)rsp_after_call_off + 1 &&
         (int)frame::entry_frame_call_wrapper_offset == (int)call_wrapper_off,
         "adjust this code");
  StubCodeMark mark(this, "StubRoutines", "call_stub");
  address start = __ pc();

  // same as in generate_catch_exception()!
  const Address rsp_after_call(rbp, rsp_after_call_off * wordSize);

  const Address call_wrapper  (rbp, call_wrapper_off   * wordSize);
  const Address result        (rbp, result_off         * wordSize);
  const Address result_type   (rbp, result_type_off    * wordSize);
  const Address method        (rbp, method_off         * wordSize);
  const Address entry_point   (rbp, entry_point_off    * wordSize);
  const Address parameters    (rbp, parameters_off     * wordSize);
  const Address parameter_size(rbp, parameter_size_off * wordSize);

  // same as in generate_catch_exception()!
  const Address thread        (rbp, thread_off         * wordSize);

  const Address r15_save(rbp, r15_off * wordSize);
  const Address r14_save(rbp, r14_off * wordSize);
  const Address r13_save(rbp, r13_off * wordSize);
  const Address r12_save(rbp, r12_off * wordSize);
  const Address rbx_save(rbp, rbx_off * wordSize);

  // stub code
  __ enter();
  __ subptr(rsp, -rsp_after_call_off * wordSize);

  // save register parameters
#ifndef _WIN64
  __ movptr(parameters,   c_rarg5); // parameters
  __ movptr(entry_point,  c_rarg4); // entry_point
#endif

  __ movptr(method,       c_rarg3); // method
  __ movl(result_type,  c_rarg2);   // result type
  __ movptr(result,       c_rarg1); // result
  __ movptr(call_wrapper, c_rarg0); // call wrapper

  // save regs belonging to calling function
  __ movptr(rbx_save, rbx);
  __ movptr(r12_save, r12);
  __ movptr(r13_save, r13);
  __ movptr(r14_save, r14);
  __ movptr(r15_save, r15);

#ifdef _WIN64
  int last_reg = 15;
  for (int i = xmm_save_first; i <= last_reg; i++) {
    __ movdqu(xmm_save(i), as_XMMRegister(i));
  }

  const Address rdi_save(rbp, rdi_off * wordSize);
  const Address rsi_save(rbp, rsi_off * wordSize);

  __ movptr(rsi_save, rsi);
  __ movptr(rdi_save, rdi);
#else
  const Address mxcsr_save(rbp, mxcsr_off * wordSize);
  {
    Label skip_ldmx;
    __ stmxcsr(mxcsr_save);
    __ movl(rax, mxcsr_save);
    __ andl(rax, 0xFFC0); // Mask out any pending exceptions (only check control and mask bits)
    ExternalAddress mxcsr_std(StubRoutines::x86::addr_mxcsr_std());
    __ cmp32(rax, mxcsr_std, rscratch1);
    __ jcc(Assembler::equal, skip_ldmx);
    __ ldmxcsr(mxcsr_std, rscratch1);
    __ bind(skip_ldmx);
  }
#endif

  // Load up thread register
  __ movptr(r15_thread, thread);
  __ reinit_heapbase();

#ifdef ASSERT
  // make sure we have no pending exceptions
  {
    Label L;
    __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()), NULL_WORD);
    __ jcc(Assembler::equal, L);
    __ stop("StubRoutines::call_stub: entered with pending exception");
    __ bind(L);
  }
#endif

  // pass parameters if any
  BLOCK_COMMENT("pass parameters if any");
  Label parameters_done;
  __ movl(c_rarg3, parameter_size);
  __ testl(c_rarg3, c_rarg3);
  __ jcc(Assembler::zero, parameters_done);

  Label loop;
  __ movptr(c_rarg2, parameters);       // parameter pointer
  __ movl(c_rarg1, c_rarg3);            // parameter counter is in c_rarg1
  __ BIND(loop);
  __ movptr(rax, Address(c_rarg2, 0));// get parameter
  __ addptr(c_rarg2, wordSize);       // advance to next parameter
  __ decrementl(c_rarg1);             // decrement counter
  __ push(rax);                       // pass parameter
  __ jcc(Assembler::notZero, loop);

  // call Java function
  __ BIND(parameters_done);
  __ movptr(rbx, method);             // get Method*
  __ movptr(c_rarg1, entry_point);    // get entry_point
  __ mov(r13, rsp);                   // set sender sp
  BLOCK_COMMENT("call Java function");
  __ call(c_rarg1);

  BLOCK_COMMENT("call_stub_return_address:");
  return_address = __ pc();

  // store result depending on type (everything that is not
  // T_OBJECT, T_LONG, T_FLOAT or T_DOUBLE is treated as T_INT)
  __ movptr(c_rarg0, result);
  Label is_long, is_float, is_double, exit;
  __ movl(c_rarg1, result_type);
  __ cmpl(c_rarg1, T_OBJECT);
  __ jcc(Assembler::equal, is_long);
  __ cmpl(c_rarg1, T_LONG);
  __ jcc(Assembler::equal, is_long);
  __ cmpl(c_rarg1, T_FLOAT);
  __ jcc(Assembler::equal, is_float);
  __ cmpl(c_rarg1, T_DOUBLE);
  __ jcc(Assembler::equal, is_double);
#ifdef ASSERT
  // make sure the type is INT
  {
    Label L;
    __ cmpl(c_rarg1, T_INT);
    __ jcc(Assembler::equal, L);
    __ stop("StubRoutines::call_stub: unexpected result type");
    __ bind(L);
  }
#endif

  // handle T_INT case
  __ movl(Address(c_rarg0, 0), rax);

  __ BIND(exit);

  // pop parameters
  __ lea(rsp, rsp_after_call);

#ifdef ASSERT
  // verify that threads correspond
  {
   Label L1, L2, L3;
    __ cmpptr(r15_thread, thread);
    __ jcc(Assembler::equal, L1);
    __ stop("StubRoutines::call_stub: r15_thread is corrupted");
    __ bind(L1);
    __ get_thread(rbx);
    __ cmpptr(r15_thread, thread);
    __ jcc(Assembler::equal, L2);
    __ stop("StubRoutines::call_stub: r15_thread is modified by call");
    __ bind(L2);
    __ cmpptr(r15_thread, rbx);
    __ jcc(Assembler::equal, L3);
    __ stop("StubRoutines::call_stub: threads must correspond");
    __ bind(L3);
  }
#endif

  __ pop_cont_fastpath();

  // restore regs belonging to calling function
#ifdef _WIN64
  // emit the restores for xmm regs
  for (int i = xmm_save_first; i <= last_reg; i++) {
    __ movdqu(as_XMMRegister(i), xmm_save(i));
  }
#endif
  __ movptr(r15, r15_save);
  __ movptr(r14, r14_save);
  __ movptr(r13, r13_save);
  __ movptr(r12, r12_save);
  __ movptr(rbx, rbx_save);

#ifdef _WIN64
  __ movptr(rdi, rdi_save);
  __ movptr(rsi, rsi_save);
#else
  __ ldmxcsr(mxcsr_save);
#endif

  // restore rsp
  __ addptr(rsp, -rsp_after_call_off * wordSize);

  // return
  __ vzeroupper();
  __ pop(rbp);
  __ ret(0);

  // handle return types different from T_INT
  __ BIND(is_long);
  __ movq(Address(c_rarg0, 0), rax);
  __ jmp(exit);

  __ BIND(is_float);
  __ movflt(Address(c_rarg0, 0), xmm0);
  __ jmp(exit);

  __ BIND(is_double);
  __ movdbl(Address(c_rarg0, 0), xmm0);
  __ jmp(exit);

  return start;
}

// Return point for a Java call if there's an exception thrown in
// Java code.  The exception is caught and transformed into a
// pending exception stored in JavaThread that can be tested from
// within the VM.
//
// Note: Usually the parameters are removed by the callee. In case
// of an exception crossing an activation frame boundary, that is
// not the case if the callee is compiled code => need to setup the
// rsp.
//
// rax: exception oop

address StubGenerator::generate_catch_exception() {
  StubCodeMark mark(this, "StubRoutines", "catch_exception");
  address start = __ pc();

  // same as in generate_call_stub():
  const Address rsp_after_call(rbp, rsp_after_call_off * wordSize);
  const Address thread        (rbp, thread_off         * wordSize);

#ifdef ASSERT
  // verify that threads correspond
  {
    Label L1, L2, L3;
    __ cmpptr(r15_thread, thread);
    __ jcc(Assembler::equal, L1);
    __ stop("StubRoutines::catch_exception: r15_thread is corrupted");
    __ bind(L1);
    __ get_thread(rbx);
    __ cmpptr(r15_thread, thread);
    __ jcc(Assembler::equal, L2);
    __ stop("StubRoutines::catch_exception: r15_thread is modified by call");
    __ bind(L2);
    __ cmpptr(r15_thread, rbx);
    __ jcc(Assembler::equal, L3);
    __ stop("StubRoutines::catch_exception: threads must correspond");
    __ bind(L3);
  }
#endif

  // set pending exception
  __ verify_oop(rax);

  __ movptr(Address(r15_thread, Thread::pending_exception_offset()), rax);
  __ lea(rscratch1, ExternalAddress((address)__FILE__));
  __ movptr(Address(r15_thread, Thread::exception_file_offset()), rscratch1);
  __ movl(Address(r15_thread, Thread::exception_line_offset()), (int)  __LINE__);

  // complete return to VM
  assert(StubRoutines::_call_stub_return_address != nullptr,
         "_call_stub_return_address must have been generated before");
  __ jump(RuntimeAddress(StubRoutines::_call_stub_return_address));

  return start;
}

// Continuation point for runtime calls returning with a pending
// exception.  The pending exception check happened in the runtime
// or native call stub.  The pending exception in Thread is
// converted into a Java-level exception.
//
// Contract with Java-level exception handlers:
// rax: exception
// rdx: throwing pc
//
// NOTE: At entry of this stub, exception-pc must be on stack !!

address StubGenerator::generate_forward_exception() {
  StubCodeMark mark(this, "StubRoutines", "forward exception");
  address start = __ pc();

  // Upon entry, the sp points to the return address returning into
  // Java (interpreted or compiled) code; i.e., the return address
  // becomes the throwing pc.
  //
  // Arguments pushed before the runtime call are still on the stack
  // but the exception handler will reset the stack pointer ->
  // ignore them.  A potential result in registers can be ignored as
  // well.

#ifdef ASSERT
  // make sure this code is only executed if there is a pending exception
  {
    Label L;
    __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()), NULL_WORD);
    __ jcc(Assembler::notEqual, L);
    __ stop("StubRoutines::forward exception: no pending exception (1)");
    __ bind(L);
  }
#endif

  // compute exception handler into rbx
  __ movptr(c_rarg0, Address(rsp, 0));
  BLOCK_COMMENT("call exception_handler_for_return_address");
  __ call_VM_leaf(CAST_FROM_FN_PTR(address,
                       SharedRuntime::exception_handler_for_return_address),
                  r15_thread, c_rarg0);
  __ mov(rbx, rax);

  // setup rax & rdx, remove return address & clear pending exception
  __ pop(rdx);
  __ movptr(rax, Address(r15_thread, Thread::pending_exception_offset()));
  __ movptr(Address(r15_thread, Thread::pending_exception_offset()), NULL_WORD);

#ifdef ASSERT
  // make sure exception is set
  {
    Label L;
    __ testptr(rax, rax);
    __ jcc(Assembler::notEqual, L);
    __ stop("StubRoutines::forward exception: no pending exception (2)");
    __ bind(L);
  }
#endif

  // continue at exception handler (return address removed)
  // rax: exception
  // rbx: exception handler
  // rdx: throwing pc
  __ verify_oop(rax);
  __ jmp(rbx);

  return start;
}

// Support for intptr_t OrderAccess::fence()
//
// Arguments :
//
// Result:
address StubGenerator::generate_orderaccess_fence() {
  StubCodeMark mark(this, "StubRoutines", "orderaccess_fence");
  address start = __ pc();

  __ membar(Assembler::StoreLoad);
  __ ret(0);

  return start;
}


// Support for intptr_t get_previous_sp()
//
// This routine is used to find the previous stack pointer for the
// caller.
address StubGenerator::generate_get_previous_sp() {
  StubCodeMark mark(this, "StubRoutines", "get_previous_sp");
  address start = __ pc();

  __ movptr(rax, rsp);
  __ addptr(rax, 8); // return address is at the top of the stack.
  __ ret(0);

  return start;
}

//----------------------------------------------------------------------------------------------------
// Support for void verify_mxcsr()
//
// This routine is used with -Xcheck:jni to verify that native
// JNI code does not return to Java code without restoring the
// MXCSR register to our expected state.

address StubGenerator::generate_verify_mxcsr() {
  StubCodeMark mark(this, "StubRoutines", "verify_mxcsr");
  address start = __ pc();

  const Address mxcsr_save(rsp, 0);

  if (CheckJNICalls) {
    Label ok_ret;
    ExternalAddress mxcsr_std(StubRoutines::x86::addr_mxcsr_std());
    __ push(rax);
    __ subptr(rsp, wordSize);      // allocate a temp location
    __ stmxcsr(mxcsr_save);
    __ movl(rax, mxcsr_save);
    __ andl(rax, 0xFFC0); // Mask out any pending exceptions (only check control and mask bits)
    __ cmp32(rax, mxcsr_std, rscratch1);
    __ jcc(Assembler::equal, ok_ret);

    __ warn("MXCSR changed by native JNI code, use -XX:+RestoreMXCSROnJNICall");

    __ ldmxcsr(mxcsr_std, rscratch1);

    __ bind(ok_ret);
    __ addptr(rsp, wordSize);
    __ pop(rax);
  }

  __ ret(0);

  return start;
}

address StubGenerator::generate_f2i_fixup() {
  StubCodeMark mark(this, "StubRoutines", "f2i_fixup");
  Address inout(rsp, 5 * wordSize); // return address + 4 saves

  address start = __ pc();

  Label L;

  __ push(rax);
  __ push(c_rarg3);
  __ push(c_rarg2);
  __ push(c_rarg1);

  __ movl(rax, 0x7f800000);
  __ xorl(c_rarg3, c_rarg3);
  __ movl(c_rarg2, inout);
  __ movl(c_rarg1, c_rarg2);
  __ andl(c_rarg1, 0x7fffffff);
  __ cmpl(rax, c_rarg1); // NaN? -> 0
  __ jcc(Assembler::negative, L);
  __ testl(c_rarg2, c_rarg2); // signed ? min_jint : max_jint
  __ movl(c_rarg3, 0x80000000);
  __ movl(rax, 0x7fffffff);
  __ cmovl(Assembler::positive, c_rarg3, rax);

  __ bind(L);
  __ movptr(inout, c_rarg3);

  __ pop(c_rarg1);
  __ pop(c_rarg2);
  __ pop(c_rarg3);
  __ pop(rax);

  __ ret(0);

  return start;
}

address StubGenerator::generate_f2l_fixup() {
  StubCodeMark mark(this, "StubRoutines", "f2l_fixup");
  Address inout(rsp, 5 * wordSize); // return address + 4 saves
  address start = __ pc();

  Label L;

  __ push(rax);
  __ push(c_rarg3);
  __ push(c_rarg2);
  __ push(c_rarg1);

  __ movl(rax, 0x7f800000);
  __ xorl(c_rarg3, c_rarg3);
  __ movl(c_rarg2, inout);
  __ movl(c_rarg1, c_rarg2);
  __ andl(c_rarg1, 0x7fffffff);
  __ cmpl(rax, c_rarg1); // NaN? -> 0
  __ jcc(Assembler::negative, L);
  __ testl(c_rarg2, c_rarg2); // signed ? min_jlong : max_jlong
  __ mov64(c_rarg3, 0x8000000000000000);
  __ mov64(rax, 0x7fffffffffffffff);
  __ cmov(Assembler::positive, c_rarg3, rax);

  __ bind(L);
  __ movptr(inout, c_rarg3);

  __ pop(c_rarg1);
  __ pop(c_rarg2);
  __ pop(c_rarg3);
  __ pop(rax);

  __ ret(0);

  return start;
}

address StubGenerator::generate_d2i_fixup() {
  StubCodeMark mark(this, "StubRoutines", "d2i_fixup");
  Address inout(rsp, 6 * wordSize); // return address + 5 saves

  address start = __ pc();

  Label L;

  __ push(rax);
  __ push(c_rarg3);
  __ push(c_rarg2);
  __ push(c_rarg1);
  __ push(c_rarg0);

  __ movl(rax, 0x7ff00000);
  __ movq(c_rarg2, inout);
  __ movl(c_rarg3, c_rarg2);
  __ mov(c_rarg1, c_rarg2);
  __ mov(c_rarg0, c_rarg2);
  __ negl(c_rarg3);
  __ shrptr(c_rarg1, 0x20);
  __ orl(c_rarg3, c_rarg2);
  __ andl(c_rarg1, 0x7fffffff);
  __ xorl(c_rarg2, c_rarg2);
  __ shrl(c_rarg3, 0x1f);
  __ orl(c_rarg1, c_rarg3);
  __ cmpl(rax, c_rarg1);
  __ jcc(Assembler::negative, L); // NaN -> 0
  __ testptr(c_rarg0, c_rarg0); // signed ? min_jint : max_jint
  __ movl(c_rarg2, 0x80000000);
  __ movl(rax, 0x7fffffff);
  __ cmov(Assembler::positive, c_rarg2, rax);

  __ bind(L);
  __ movptr(inout, c_rarg2);

  __ pop(c_rarg0);
  __ pop(c_rarg1);
  __ pop(c_rarg2);
  __ pop(c_rarg3);
  __ pop(rax);

  __ ret(0);

  return start;
}

address StubGenerator::generate_d2l_fixup() {
  StubCodeMark mark(this, "StubRoutines", "d2l_fixup");
  Address inout(rsp, 6 * wordSize); // return address + 5 saves

  address start = __ pc();

  Label L;

  __ push(rax);
  __ push(c_rarg3);
  __ push(c_rarg2);
  __ push(c_rarg1);
  __ push(c_rarg0);

  __ movl(rax, 0x7ff00000);
  __ movq(c_rarg2, inout);
  __ movl(c_rarg3, c_rarg2);
  __ mov(c_rarg1, c_rarg2);
  __ mov(c_rarg0, c_rarg2);
  __ negl(c_rarg3);
  __ shrptr(c_rarg1, 0x20);
  __ orl(c_rarg3, c_rarg2);
  __ andl(c_rarg1, 0x7fffffff);
  __ xorl(c_rarg2, c_rarg2);
  __ shrl(c_rarg3, 0x1f);
  __ orl(c_rarg1, c_rarg3);
  __ cmpl(rax, c_rarg1);
  __ jcc(Assembler::negative, L); // NaN -> 0
  __ testq(c_rarg0, c_rarg0); // signed ? min_jlong : max_jlong
  __ mov64(c_rarg2, 0x8000000000000000);
  __ mov64(rax, 0x7fffffffffffffff);
  __ cmovq(Assembler::positive, c_rarg2, rax);

  __ bind(L);
  __ movq(inout, c_rarg2);

  __ pop(c_rarg0);
  __ pop(c_rarg1);
  __ pop(c_rarg2);
  __ pop(c_rarg3);
  __ pop(rax);

  __ ret(0);

  return start;
}

address StubGenerator::generate_count_leading_zeros_lut(const char *stub_name) {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(0x0101010102020304, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);
  __ emit_data64(0x0101010102020304, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);
  __ emit_data64(0x0101010102020304, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);
  __ emit_data64(0x0101010102020304, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);

  return start;
}

address StubGenerator::generate_popcount_avx_lut(const char *stub_name) {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(0x0302020102010100, relocInfo::none);
  __ emit_data64(0x0403030203020201, relocInfo::none);
  __ emit_data64(0x0302020102010100, relocInfo::none);
  __ emit_data64(0x0403030203020201, relocInfo::none);
  __ emit_data64(0x0302020102010100, relocInfo::none);
  __ emit_data64(0x0403030203020201, relocInfo::none);
  __ emit_data64(0x0302020102010100, relocInfo::none);
  __ emit_data64(0x0403030203020201, relocInfo::none);

  return start;
}

address StubGenerator::generate_iota_indices(const char *stub_name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();
  // B
  __ emit_data64(0x0706050403020100, relocInfo::none);
  __ emit_data64(0x0F0E0D0C0B0A0908, relocInfo::none);
  __ emit_data64(0x1716151413121110, relocInfo::none);
  __ emit_data64(0x1F1E1D1C1B1A1918, relocInfo::none);
  __ emit_data64(0x2726252423222120, relocInfo::none);
  __ emit_data64(0x2F2E2D2C2B2A2928, relocInfo::none);
  __ emit_data64(0x3736353433323130, relocInfo::none);
  __ emit_data64(0x3F3E3D3C3B3A3938, relocInfo::none);
  // W
  __ emit_data64(0x0003000200010000, relocInfo::none);
  __ emit_data64(0x0007000600050004, relocInfo::none);
  __ emit_data64(0x000B000A00090008, relocInfo::none);
  __ emit_data64(0x000F000E000D000C, relocInfo::none);
  __ emit_data64(0x0013001200110010, relocInfo::none);
  __ emit_data64(0x0017001600150014, relocInfo::none);
  __ emit_data64(0x001B001A00190018, relocInfo::none);
  __ emit_data64(0x001F001E001D001C, relocInfo::none);
  // D
  __ emit_data64(0x0000000100000000, relocInfo::none);
  __ emit_data64(0x0000000300000002, relocInfo::none);
  __ emit_data64(0x0000000500000004, relocInfo::none);
  __ emit_data64(0x0000000700000006, relocInfo::none);
  __ emit_data64(0x0000000900000008, relocInfo::none);
  __ emit_data64(0x0000000B0000000A, relocInfo::none);
  __ emit_data64(0x0000000D0000000C, relocInfo::none);
  __ emit_data64(0x0000000F0000000E, relocInfo::none);
  // Q
  __ emit_data64(0x0000000000000000, relocInfo::none);
  __ emit_data64(0x0000000000000001, relocInfo::none);
  __ emit_data64(0x0000000000000002, relocInfo::none);
  __ emit_data64(0x0000000000000003, relocInfo::none);
  __ emit_data64(0x0000000000000004, relocInfo::none);
  __ emit_data64(0x0000000000000005, relocInfo::none);
  __ emit_data64(0x0000000000000006, relocInfo::none);
  __ emit_data64(0x0000000000000007, relocInfo::none);
  // D - FP
  __ emit_data64(0x3F80000000000000, relocInfo::none); // 0.0f, 1.0f
  __ emit_data64(0x4040000040000000, relocInfo::none); // 2.0f, 3.0f
  __ emit_data64(0x40A0000040800000, relocInfo::none); // 4.0f, 5.0f
  __ emit_data64(0x40E0000040C00000, relocInfo::none); // 6.0f, 7.0f
  __ emit_data64(0x4110000041000000, relocInfo::none); // 8.0f, 9.0f
  __ emit_data64(0x4130000041200000, relocInfo::none); // 10.0f, 11.0f
  __ emit_data64(0x4150000041400000, relocInfo::none); // 12.0f, 13.0f
  __ emit_data64(0x4170000041600000, relocInfo::none); // 14.0f, 15.0f
  // Q - FP
  __ emit_data64(0x0000000000000000, relocInfo::none); // 0.0d
  __ emit_data64(0x3FF0000000000000, relocInfo::none); // 1.0d
  __ emit_data64(0x4000000000000000, relocInfo::none); // 2.0d
  __ emit_data64(0x4008000000000000, relocInfo::none); // 3.0d
  __ emit_data64(0x4010000000000000, relocInfo::none); // 4.0d
  __ emit_data64(0x4014000000000000, relocInfo::none); // 5.0d
  __ emit_data64(0x4018000000000000, relocInfo::none); // 6.0d
  __ emit_data64(0x401c000000000000, relocInfo::none); // 7.0d
  return start;
}

address StubGenerator::generate_vector_reverse_bit_lut(const char *stub_name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(0x0E060A020C040800, relocInfo::none);
  __ emit_data64(0x0F070B030D050901, relocInfo::none);
  __ emit_data64(0x0E060A020C040800, relocInfo::none);
  __ emit_data64(0x0F070B030D050901, relocInfo::none);
  __ emit_data64(0x0E060A020C040800, relocInfo::none);
  __ emit_data64(0x0F070B030D050901, relocInfo::none);
  __ emit_data64(0x0E060A020C040800, relocInfo::none);
  __ emit_data64(0x0F070B030D050901, relocInfo::none);

  return start;
}

address StubGenerator::generate_vector_reverse_byte_perm_mask_long(const char *stub_name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(0x0001020304050607, relocInfo::none);
  __ emit_data64(0x08090A0B0C0D0E0F, relocInfo::none);
  __ emit_data64(0x0001020304050607, relocInfo::none);
  __ emit_data64(0x08090A0B0C0D0E0F, relocInfo::none);
  __ emit_data64(0x0001020304050607, relocInfo::none);
  __ emit_data64(0x08090A0B0C0D0E0F, relocInfo::none);
  __ emit_data64(0x0001020304050607, relocInfo::none);
  __ emit_data64(0x08090A0B0C0D0E0F, relocInfo::none);

  return start;
}

address StubGenerator::generate_vector_reverse_byte_perm_mask_int(const char *stub_name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(0x0405060700010203, relocInfo::none);
  __ emit_data64(0x0C0D0E0F08090A0B, relocInfo::none);
  __ emit_data64(0x0405060700010203, relocInfo::none);
  __ emit_data64(0x0C0D0E0F08090A0B, relocInfo::none);
  __ emit_data64(0x0405060700010203, relocInfo::none);
  __ emit_data64(0x0C0D0E0F08090A0B, relocInfo::none);
  __ emit_data64(0x0405060700010203, relocInfo::none);
  __ emit_data64(0x0C0D0E0F08090A0B, relocInfo::none);

  return start;
}

address StubGenerator::generate_vector_reverse_byte_perm_mask_short(const char *stub_name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(0x0607040502030001, relocInfo::none);
  __ emit_data64(0x0E0F0C0D0A0B0809, relocInfo::none);
  __ emit_data64(0x0607040502030001, relocInfo::none);
  __ emit_data64(0x0E0F0C0D0A0B0809, relocInfo::none);
  __ emit_data64(0x0607040502030001, relocInfo::none);
  __ emit_data64(0x0E0F0C0D0A0B0809, relocInfo::none);
  __ emit_data64(0x0607040502030001, relocInfo::none);
  __ emit_data64(0x0E0F0C0D0A0B0809, relocInfo::none);

  return start;
}

address StubGenerator::generate_vector_byte_shuffle_mask(const char *stub_name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(0x7070707070707070, relocInfo::none);
  __ emit_data64(0x7070707070707070, relocInfo::none);
  __ emit_data64(0xF0F0F0F0F0F0F0F0, relocInfo::none);
  __ emit_data64(0xF0F0F0F0F0F0F0F0, relocInfo::none);

  return start;
}

address StubGenerator::generate_fp_mask(const char *stub_name, int64_t mask) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64( mask, relocInfo::none );
  __ emit_data64( mask, relocInfo::none );

  return start;
}

address StubGenerator::generate_vector_mask(const char *stub_name, int64_t mask) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);

  return start;
}

address StubGenerator::generate_vector_byte_perm_mask(const char *stub_name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(0x0000000000000001, relocInfo::none);
  __ emit_data64(0x0000000000000003, relocInfo::none);
  __ emit_data64(0x0000000000000005, relocInfo::none);
  __ emit_data64(0x0000000000000007, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);
  __ emit_data64(0x0000000000000002, relocInfo::none);
  __ emit_data64(0x0000000000000004, relocInfo::none);
  __ emit_data64(0x0000000000000006, relocInfo::none);

  return start;
}

address StubGenerator::generate_vector_fp_mask(const char *stub_name, int64_t mask) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);
  __ emit_data64(mask, relocInfo::none);

  return start;
}

address StubGenerator::generate_vector_custom_i32(const char *stub_name, Assembler::AvxVectorLen len,
                                   int32_t val0, int32_t val1, int32_t val2, int32_t val3,
                                   int32_t val4, int32_t val5, int32_t val6, int32_t val7,
                                   int32_t val8, int32_t val9, int32_t val10, int32_t val11,
                                   int32_t val12, int32_t val13, int32_t val14, int32_t val15) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", stub_name);
  address start = __ pc();

  assert(len != Assembler::AVX_NoVec, "vector len must be specified");
  __ emit_data(val0, relocInfo::none, 0);
  __ emit_data(val1, relocInfo::none, 0);
  __ emit_data(val2, relocInfo::none, 0);
  __ emit_data(val3, relocInfo::none, 0);
  if (len >= Assembler::AVX_256bit) {
    __ emit_data(val4, relocInfo::none, 0);
    __ emit_data(val5, relocInfo::none, 0);
    __ emit_data(val6, relocInfo::none, 0);
    __ emit_data(val7, relocInfo::none, 0);
    if (len >= Assembler::AVX_512bit) {
      __ emit_data(val8, relocInfo::none, 0);
      __ emit_data(val9, relocInfo::none, 0);
      __ emit_data(val10, relocInfo::none, 0);
      __ emit_data(val11, relocInfo::none, 0);
      __ emit_data(val12, relocInfo::none, 0);
      __ emit_data(val13, relocInfo::none, 0);
      __ emit_data(val14, relocInfo::none, 0);
      __ emit_data(val15, relocInfo::none, 0);
    }
  }
  return start;
}

// Non-destructive plausibility checks for oops
//
// Arguments:
//    all args on stack!
//
// Stack after saving c_rarg3:
//    [tos + 0]: saved c_rarg3
//    [tos + 1]: saved c_rarg2
//    [tos + 2]: saved r12 (several TemplateTable methods use it)
//    [tos + 3]: saved flags
//    [tos + 4]: return address
//  * [tos + 5]: error message (char*)
//  * [tos + 6]: object to verify (oop)
//  * [tos + 7]: saved rax - saved by caller and bashed
//  * [tos + 8]: saved r10 (rscratch1) - saved by caller
//  * = popped on exit
address StubGenerator::generate_verify_oop() {
  StubCodeMark mark(this, "StubRoutines", "verify_oop");
  address start = __ pc();

  Label exit, error;

  __ pushf();
  __ incrementl(ExternalAddress((address) StubRoutines::verify_oop_count_addr()), rscratch1);

  __ push(r12);

  // save c_rarg2 and c_rarg3
  __ push(c_rarg2);
  __ push(c_rarg3);

  enum {
    // After previous pushes.
    oop_to_verify = 6 * wordSize,
    saved_rax     = 7 * wordSize,
    saved_r10     = 8 * wordSize,

    // Before the call to MacroAssembler::debug(), see below.
    return_addr   = 16 * wordSize,
    error_msg     = 17 * wordSize
  };

  // get object
  __ movptr(rax, Address(rsp, oop_to_verify));

  // make sure object is 'reasonable'
  __ testptr(rax, rax);
  __ jcc(Assembler::zero, exit); // if obj is null it is OK

   BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
   bs_asm->check_oop(_masm, rax, c_rarg2, c_rarg3, error);

  // return if everything seems ok
  __ bind(exit);
  __ movptr(rax, Address(rsp, saved_rax));     // get saved rax back
  __ movptr(rscratch1, Address(rsp, saved_r10)); // get saved r10 back
  __ pop(c_rarg3);                             // restore c_rarg3
  __ pop(c_rarg2);                             // restore c_rarg2
  __ pop(r12);                                 // restore r12
  __ popf();                                   // restore flags
  __ ret(4 * wordSize);                        // pop caller saved stuff

  // handle errors
  __ bind(error);
  __ movptr(rax, Address(rsp, saved_rax));     // get saved rax back
  __ movptr(rscratch1, Address(rsp, saved_r10)); // get saved r10 back
  __ pop(c_rarg3);                             // get saved c_rarg3 back
  __ pop(c_rarg2);                             // get saved c_rarg2 back
  __ pop(r12);                                 // get saved r12 back
  __ popf();                                   // get saved flags off stack --
                                               // will be ignored

  __ pusha();                                  // push registers
                                               // (rip is already
                                               // already pushed)
  // debug(char* msg, int64_t pc, int64_t regs[])
  // We've popped the registers we'd saved (c_rarg3, c_rarg2 and flags), and
  // pushed all the registers, so now the stack looks like:
  //     [tos +  0] 16 saved registers
  //     [tos + 16] return address
  //   * [tos + 17] error message (char*)
  //   * [tos + 18] object to verify (oop)
  //   * [tos + 19] saved rax - saved by caller and bashed
  //   * [tos + 20] saved r10 (rscratch1) - saved by caller
  //   * = popped on exit

  __ movptr(c_rarg0, Address(rsp, error_msg));    // pass address of error message
  __ movptr(c_rarg1, Address(rsp, return_addr));  // pass return address
  __ movq(c_rarg2, rsp);                          // pass address of regs on stack
  __ mov(r12, rsp);                               // remember rsp
  __ subptr(rsp, frame::arg_reg_save_area_bytes); // windows
  __ andptr(rsp, -16);                            // align stack as required by ABI
  BLOCK_COMMENT("call MacroAssembler::debug");
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, MacroAssembler::debug64)));
  __ hlt();

  return start;
}


// Shuffle first three arg regs on Windows into Linux/Solaris locations.
//
// Outputs:
//    rdi - rcx
//    rsi - rdx
//    rdx - r8
//    rcx - r9
//
// Registers r9 and r10 are used to save rdi and rsi on Windows, which latter
// are non-volatile.  r9 and r10 should not be used by the caller.
//
void StubGenerator::setup_arg_regs(int nargs) {
  const Register saved_rdi = r9;
  const Register saved_rsi = r10;
  assert(nargs == 3 || nargs == 4, "else fix");
#ifdef _WIN64
  assert(c_rarg0 == rcx && c_rarg1 == rdx && c_rarg2 == r8 && c_rarg3 == r9,
         "unexpected argument registers");
  if (nargs == 4) {
    __ mov(rax, r9);  // r9 is also saved_rdi
  }
  __ movptr(saved_rdi, rdi);
  __ movptr(saved_rsi, rsi);
  __ mov(rdi, rcx); // c_rarg0
  __ mov(rsi, rdx); // c_rarg1
  __ mov(rdx, r8);  // c_rarg2
  if (nargs == 4) {
    __ mov(rcx, rax); // c_rarg3 (via rax)
  }
#else
  assert(c_rarg0 == rdi && c_rarg1 == rsi && c_rarg2 == rdx && c_rarg3 == rcx,
         "unexpected argument registers");
#endif
  DEBUG_ONLY(_regs_in_thread = false;)
}


void StubGenerator::restore_arg_regs() {
  assert(!_regs_in_thread, "wrong call to restore_arg_regs");
  const Register saved_rdi = r9;
  const Register saved_rsi = r10;
#ifdef _WIN64
  __ movptr(rdi, saved_rdi);
  __ movptr(rsi, saved_rsi);
#endif
}


// This is used in places where r10 is a scratch register, and can
// be adapted if r9 is needed also.
void StubGenerator::setup_arg_regs_using_thread(int nargs) {
  const Register saved_r15 = r9;
  assert(nargs == 3 || nargs == 4, "else fix");
#ifdef _WIN64
  if (nargs == 4) {
    __ mov(rax, r9);       // r9 is also saved_r15
  }
  __ mov(saved_r15, r15);  // r15 is callee saved and needs to be restored
  __ get_thread(r15_thread);
  assert(c_rarg0 == rcx && c_rarg1 == rdx && c_rarg2 == r8 && c_rarg3 == r9,
         "unexpected argument registers");
  __ movptr(Address(r15_thread, in_bytes(JavaThread::windows_saved_rdi_offset())), rdi);
  __ movptr(Address(r15_thread, in_bytes(JavaThread::windows_saved_rsi_offset())), rsi);

  __ mov(rdi, rcx); // c_rarg0
  __ mov(rsi, rdx); // c_rarg1
  __ mov(rdx, r8);  // c_rarg2
  if (nargs == 4) {
    __ mov(rcx, rax); // c_rarg3 (via rax)
  }
#else
  assert(c_rarg0 == rdi && c_rarg1 == rsi && c_rarg2 == rdx && c_rarg3 == rcx,
         "unexpected argument registers");
#endif
  DEBUG_ONLY(_regs_in_thread = true;)
}


void StubGenerator::restore_arg_regs_using_thread() {
  assert(_regs_in_thread, "wrong call to restore_arg_regs");
  const Register saved_r15 = r9;
#ifdef _WIN64
  __ get_thread(r15_thread);
  __ movptr(rsi, Address(r15_thread, in_bytes(JavaThread::windows_saved_rsi_offset())));
  __ movptr(rdi, Address(r15_thread, in_bytes(JavaThread::windows_saved_rdi_offset())));
  __ mov(r15, saved_r15);  // r15 is callee saved and needs to be restored
#endif
}


void StubGenerator::setup_argument_regs(BasicType type) {
  if (type == T_BYTE || type == T_SHORT) {
    setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                      // r9 and r10 may be used to save non-volatile registers
  } else {
    setup_arg_regs_using_thread(); // from => rdi, to => rsi, count => rdx
                                   // r9 is used to save r15_thread
  }
}


void StubGenerator::restore_argument_regs(BasicType type) {
  if (type == T_BYTE || type == T_SHORT) {
    restore_arg_regs();
  } else {
    restore_arg_regs_using_thread();
  }
}

address StubGenerator::generate_data_cache_writeback() {
  const Register src        = c_rarg0;  // source address

  __ align(CodeEntryAlignment);

  StubCodeMark mark(this, "StubRoutines", "_data_cache_writeback");

  address start = __ pc();

  __ enter();
  __ cache_wb(Address(src, 0));
  __ leave();
  __ ret(0);

  return start;
}

address StubGenerator::generate_data_cache_writeback_sync() {
  const Register is_pre    = c_rarg0;  // pre or post sync

  __ align(CodeEntryAlignment);

  StubCodeMark mark(this, "StubRoutines", "_data_cache_writeback_sync");

  // pre wbsync is a no-op
  // post wbsync translates to an sfence

  Label skip;
  address start = __ pc();

  __ enter();
  __ cmpl(is_pre, 0);
  __ jcc(Assembler::notEqual, skip);
  __ cache_wbsync(false);
  __ bind(skip);
  __ leave();
  __ ret(0);

  return start;
}

// ofs and limit are use for multi-block byte array.
// int com.sun.security.provider.MD5.implCompress(byte[] b, int ofs)
address StubGenerator::generate_md5_implCompress(bool multi_block, const char *name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  const Register buf_param = r15;
  const Address state_param(rsp, 0 * wordSize);
  const Address ofs_param  (rsp, 1 * wordSize    );
  const Address limit_param(rsp, 1 * wordSize + 4);

  __ enter();
  __ push(rbx);
  __ push(rdi);
  __ push(rsi);
  __ push(r15);
  __ subptr(rsp, 2 * wordSize);

  __ movptr(buf_param, c_rarg0);
  __ movptr(state_param, c_rarg1);
  if (multi_block) {
    __ movl(ofs_param, c_rarg2);
    __ movl(limit_param, c_rarg3);
  }
  __ fast_md5(buf_param, state_param, ofs_param, limit_param, multi_block);

  __ addptr(rsp, 2 * wordSize);
  __ pop(r15);
  __ pop(rsi);
  __ pop(rdi);
  __ pop(rbx);
  __ leave();
  __ ret(0);

  return start;
}

address StubGenerator::generate_upper_word_mask() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "upper_word_mask");
  address start = __ pc();

  __ emit_data64(0x0000000000000000, relocInfo::none);
  __ emit_data64(0xFFFFFFFF00000000, relocInfo::none);

  return start;
}

address StubGenerator::generate_shuffle_byte_flip_mask() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "shuffle_byte_flip_mask");
  address start = __ pc();

  __ emit_data64(0x08090a0b0c0d0e0f, relocInfo::none);
  __ emit_data64(0x0001020304050607, relocInfo::none);

  return start;
}

// ofs and limit are use for multi-block byte array.
// int com.sun.security.provider.DigestBase.implCompressMultiBlock(byte[] b, int ofs, int limit)
address StubGenerator::generate_sha1_implCompress(bool multi_block, const char *name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  Register buf = c_rarg0;
  Register state = c_rarg1;
  Register ofs = c_rarg2;
  Register limit = c_rarg3;

  const XMMRegister abcd = xmm0;
  const XMMRegister e0 = xmm1;
  const XMMRegister e1 = xmm2;
  const XMMRegister msg0 = xmm3;

  const XMMRegister msg1 = xmm4;
  const XMMRegister msg2 = xmm5;
  const XMMRegister msg3 = xmm6;
  const XMMRegister shuf_mask = xmm7;

  __ enter();

  __ subptr(rsp, 4 * wordSize);

  __ fast_sha1(abcd, e0, e1, msg0, msg1, msg2, msg3, shuf_mask,
    buf, state, ofs, limit, rsp, multi_block);

  __ addptr(rsp, 4 * wordSize);

  __ leave();
  __ ret(0);

  return start;
}

address StubGenerator::generate_pshuffle_byte_flip_mask() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "pshuffle_byte_flip_mask");
  address start = __ pc();

  __ emit_data64(0x0405060700010203, relocInfo::none);
  __ emit_data64(0x0c0d0e0f08090a0b, relocInfo::none);

  if (VM_Version::supports_avx2()) {
    __ emit_data64(0x0405060700010203, relocInfo::none); // second copy
    __ emit_data64(0x0c0d0e0f08090a0b, relocInfo::none);
    // _SHUF_00BA
    __ emit_data64(0x0b0a090803020100, relocInfo::none);
    __ emit_data64(0xFFFFFFFFFFFFFFFF, relocInfo::none);
    __ emit_data64(0x0b0a090803020100, relocInfo::none);
    __ emit_data64(0xFFFFFFFFFFFFFFFF, relocInfo::none);
    // _SHUF_DC00
    __ emit_data64(0xFFFFFFFFFFFFFFFF, relocInfo::none);
    __ emit_data64(0x0b0a090803020100, relocInfo::none);
    __ emit_data64(0xFFFFFFFFFFFFFFFF, relocInfo::none);
    __ emit_data64(0x0b0a090803020100, relocInfo::none);
  }

  return start;
}

//Mask for byte-swapping a couple of qwords in an XMM register using (v)pshufb.
address StubGenerator::generate_pshuffle_byte_flip_mask_sha512() {
  __ align32();
  StubCodeMark mark(this, "StubRoutines", "pshuffle_byte_flip_mask_sha512");
  address start = __ pc();

  if (VM_Version::supports_avx2()) {
    __ emit_data64(0x0001020304050607, relocInfo::none); // PSHUFFLE_BYTE_FLIP_MASK
    __ emit_data64(0x08090a0b0c0d0e0f, relocInfo::none);
    __ emit_data64(0x1011121314151617, relocInfo::none);
    __ emit_data64(0x18191a1b1c1d1e1f, relocInfo::none);
    __ emit_data64(0x0000000000000000, relocInfo::none); //MASK_YMM_LO
    __ emit_data64(0x0000000000000000, relocInfo::none);
    __ emit_data64(0xFFFFFFFFFFFFFFFF, relocInfo::none);
    __ emit_data64(0xFFFFFFFFFFFFFFFF, relocInfo::none);
  }

  return start;
}

// ofs and limit are use for multi-block byte array.
// int com.sun.security.provider.DigestBase.implCompressMultiBlock(byte[] b, int ofs, int limit)
address StubGenerator::generate_sha256_implCompress(bool multi_block, const char *name) {
  assert(VM_Version::supports_sha() || VM_Version::supports_avx2(), "");
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  Register buf = c_rarg0;
  Register state = c_rarg1;
  Register ofs = c_rarg2;
  Register limit = c_rarg3;

  const XMMRegister msg = xmm0;
  const XMMRegister state0 = xmm1;
  const XMMRegister state1 = xmm2;
  const XMMRegister msgtmp0 = xmm3;

  const XMMRegister msgtmp1 = xmm4;
  const XMMRegister msgtmp2 = xmm5;
  const XMMRegister msgtmp3 = xmm6;
  const XMMRegister msgtmp4 = xmm7;

  const XMMRegister shuf_mask = xmm8;

  __ enter();

  __ subptr(rsp, 4 * wordSize);

  if (VM_Version::supports_sha()) {
    __ fast_sha256(msg, state0, state1, msgtmp0, msgtmp1, msgtmp2, msgtmp3, msgtmp4,
      buf, state, ofs, limit, rsp, multi_block, shuf_mask);
  } else if (VM_Version::supports_avx2()) {
    __ sha256_AVX2(msg, state0, state1, msgtmp0, msgtmp1, msgtmp2, msgtmp3, msgtmp4,
      buf, state, ofs, limit, rsp, multi_block, shuf_mask);
  }
  __ addptr(rsp, 4 * wordSize);
  __ vzeroupper();
  __ leave();
  __ ret(0);

  return start;
}

address StubGenerator::generate_sha512_implCompress(bool multi_block, const char *name) {
  assert(VM_Version::supports_avx2(), "");
  assert(VM_Version::supports_bmi2(), "");
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  Register buf = c_rarg0;
  Register state = c_rarg1;
  Register ofs = c_rarg2;
  Register limit = c_rarg3;

  const XMMRegister msg = xmm0;
  const XMMRegister state0 = xmm1;
  const XMMRegister state1 = xmm2;
  const XMMRegister msgtmp0 = xmm3;
  const XMMRegister msgtmp1 = xmm4;
  const XMMRegister msgtmp2 = xmm5;
  const XMMRegister msgtmp3 = xmm6;
  const XMMRegister msgtmp4 = xmm7;

  const XMMRegister shuf_mask = xmm8;

  __ enter();

  __ sha512_AVX2(msg, state0, state1, msgtmp0, msgtmp1, msgtmp2, msgtmp3, msgtmp4,
  buf, state, ofs, limit, rsp, multi_block, shuf_mask);

  __ vzeroupper();
  __ leave();
  __ ret(0);

  return start;
}

address StubGenerator::base64_shuffle_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "shuffle_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x0405030401020001, relocInfo::none);
  __ emit_data64(0x0a0b090a07080607, relocInfo::none);
  __ emit_data64(0x10110f100d0e0c0d, relocInfo::none);
  __ emit_data64(0x1617151613141213, relocInfo::none);
  __ emit_data64(0x1c1d1b1c191a1819, relocInfo::none);
  __ emit_data64(0x222321221f201e1f, relocInfo::none);
  __ emit_data64(0x2829272825262425, relocInfo::none);
  __ emit_data64(0x2e2f2d2e2b2c2a2b, relocInfo::none);

  return start;
}

address StubGenerator::base64_avx2_shuffle_addr() {
  __ align32();
  StubCodeMark mark(this, "StubRoutines", "avx2_shuffle_base64");
  address start = __ pc();

  __ emit_data64(0x0809070805060405, relocInfo::none);
  __ emit_data64(0x0e0f0d0e0b0c0a0b, relocInfo::none);
  __ emit_data64(0x0405030401020001, relocInfo::none);
  __ emit_data64(0x0a0b090a07080607, relocInfo::none);

  return start;
}

address StubGenerator::base64_avx2_input_mask_addr() {
  __ align32();
  StubCodeMark mark(this, "StubRoutines", "avx2_input_mask_base64");
  address start = __ pc();

  __ emit_data64(0x8000000000000000, relocInfo::none);
  __ emit_data64(0x8000000080000000, relocInfo::none);
  __ emit_data64(0x8000000080000000, relocInfo::none);
  __ emit_data64(0x8000000080000000, relocInfo::none);

  return start;
}

address StubGenerator::base64_avx2_lut_addr() {
  __ align32();
  StubCodeMark mark(this, "StubRoutines", "avx2_lut_base64");
  address start = __ pc();

  __ emit_data64(0xfcfcfcfcfcfc4741, relocInfo::none);
  __ emit_data64(0x0000f0edfcfcfcfc, relocInfo::none);
  __ emit_data64(0xfcfcfcfcfcfc4741, relocInfo::none);
  __ emit_data64(0x0000f0edfcfcfcfc, relocInfo::none);

  // URL LUT
  __ emit_data64(0xfcfcfcfcfcfc4741, relocInfo::none);
  __ emit_data64(0x000020effcfcfcfc, relocInfo::none);
  __ emit_data64(0xfcfcfcfcfcfc4741, relocInfo::none);
  __ emit_data64(0x000020effcfcfcfc, relocInfo::none);

  return start;
}

address StubGenerator::base64_encoding_table_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "encoding_table_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0, "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x4847464544434241, relocInfo::none);
  __ emit_data64(0x504f4e4d4c4b4a49, relocInfo::none);
  __ emit_data64(0x5857565554535251, relocInfo::none);
  __ emit_data64(0x6665646362615a59, relocInfo::none);
  __ emit_data64(0x6e6d6c6b6a696867, relocInfo::none);
  __ emit_data64(0x767574737271706f, relocInfo::none);
  __ emit_data64(0x333231307a797877, relocInfo::none);
  __ emit_data64(0x2f2b393837363534, relocInfo::none);

  // URL table
  __ emit_data64(0x4847464544434241, relocInfo::none);
  __ emit_data64(0x504f4e4d4c4b4a49, relocInfo::none);
  __ emit_data64(0x5857565554535251, relocInfo::none);
  __ emit_data64(0x6665646362615a59, relocInfo::none);
  __ emit_data64(0x6e6d6c6b6a696867, relocInfo::none);
  __ emit_data64(0x767574737271706f, relocInfo::none);
  __ emit_data64(0x333231307a797877, relocInfo::none);
  __ emit_data64(0x5f2d393837363534, relocInfo::none);

  return start;
}

// Code for generating Base64 encoding.
// Intrinsic function prototype in Base64.java:
// private void encodeBlock(byte[] src, int sp, int sl, byte[] dst, int dp,
// boolean isURL) {
address StubGenerator::generate_base64_encodeBlock()
{
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "implEncode");
  address start = __ pc();

  __ enter();

  // Save callee-saved registers before using them
  __ push(r12);
  __ push(r13);
  __ push(r14);
  __ push(r15);

  // arguments
  const Register source = c_rarg0;       // Source Array
  const Register start_offset = c_rarg1; // start offset
  const Register end_offset = c_rarg2;   // end offset
  const Register dest = c_rarg3;   // destination array

#ifndef _WIN64
  const Register dp = c_rarg4;    // Position for writing to dest array
  const Register isURL = c_rarg5; // Base64 or URL character set
#else
  const Address dp_mem(rbp, 6 * wordSize); // length is on stack on Win64
  const Address isURL_mem(rbp, 7 * wordSize);
  const Register isURL = r10; // pick the volatile windows register
  const Register dp = r12;
  __ movl(dp, dp_mem);
  __ movl(isURL, isURL_mem);
#endif

  const Register length = r14;
  const Register encode_table = r13;
  Label L_process3, L_exit, L_processdata, L_vbmiLoop, L_not512, L_32byteLoop;

  // calculate length from offsets
  __ movl(length, end_offset);
  __ subl(length, start_offset);
  __ jcc(Assembler::lessEqual, L_exit);

  // Code for 512-bit VBMI encoding.  Encodes 48 input bytes into 64
  // output bytes. We read 64 input bytes and ignore the last 16, so be
  // sure not to read past the end of the input buffer.
  if (VM_Version::supports_avx512_vbmi()) {
    __ cmpl(length, 64); // Do not overrun input buffer.
    __ jcc(Assembler::below, L_not512);

    __ shll(isURL, 6); // index into decode table based on isURL
    __ lea(encode_table, ExternalAddress(StubRoutines::x86::base64_encoding_table_addr()));
    __ addptr(encode_table, isURL);
    __ shrl(isURL, 6); // restore isURL

    __ mov64(rax, 0x3036242a1016040aull); // Shifts
    __ evmovdquq(xmm3, ExternalAddress(StubRoutines::x86::base64_shuffle_addr()), Assembler::AVX_512bit, r15);
    __ evmovdquq(xmm2, Address(encode_table, 0), Assembler::AVX_512bit);
    __ evpbroadcastq(xmm1, rax, Assembler::AVX_512bit);

    __ align32();
    __ BIND(L_vbmiLoop);

    __ vpermb(xmm0, xmm3, Address(source, start_offset), Assembler::AVX_512bit);
    __ subl(length, 48);

    // Put the input bytes into the proper lanes for writing, then
    // encode them.
    __ evpmultishiftqb(xmm0, xmm1, xmm0, Assembler::AVX_512bit);
    __ vpermb(xmm0, xmm0, xmm2, Assembler::AVX_512bit);

    // Write to destination
    __ evmovdquq(Address(dest, dp), xmm0, Assembler::AVX_512bit);

    __ addptr(dest, 64);
    __ addptr(source, 48);
    __ cmpl(length, 64);
    __ jcc(Assembler::aboveEqual, L_vbmiLoop);

    __ vzeroupper();
  }

  __ BIND(L_not512);
  if (VM_Version::supports_avx2()) {
    /*
    ** This AVX2 encoder is based off the paper at:
    **      https://dl.acm.org/doi/10.1145/3132709
    **
    ** We use AVX2 SIMD instructions to encode 24 bytes into 32
    ** output bytes.
    **
    */
    // Lengths under 32 bytes are done with scalar routine
    __ cmpl(length, 31);
    __ jcc(Assembler::belowEqual, L_process3);

    // Set up supporting constant table data
    __ vmovdqu(xmm9, ExternalAddress(StubRoutines::x86::base64_avx2_shuffle_addr()), rax);
    // 6-bit mask for 2nd and 4th (and multiples) 6-bit values
    __ movl(rax, 0x0fc0fc00);
    __ movdl(xmm8, rax);
    __ vmovdqu(xmm1, ExternalAddress(StubRoutines::x86::base64_avx2_input_mask_addr()), rax);
    __ vpbroadcastd(xmm8, xmm8, Assembler::AVX_256bit);

    // Multiplication constant for "shifting" right by 6 and 10
    // bits
    __ movl(rax, 0x04000040);

    __ subl(length, 24);
    __ movdl(xmm7, rax);
    __ vpbroadcastd(xmm7, xmm7, Assembler::AVX_256bit);

    // For the first load, we mask off reading of the first 4
    // bytes into the register. This is so we can get 4 3-byte
    // chunks into each lane of the register, avoiding having to
    // handle end conditions.  We then shuffle these bytes into a
    // specific order so that manipulation is easier.
    //
    // The initial read loads the XMM register like this:
    //
    // Lower 128-bit lane:
    // +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
    // | XX | XX | XX | XX | A0 | A1 | A2 | B0 | B1 | B2 | C0 | C1
    // | C2 | D0 | D1 | D2 |
    // +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
    //
    // Upper 128-bit lane:
    // +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
    // | E0 | E1 | E2 | F0 | F1 | F2 | G0 | G1 | G2 | H0 | H1 | H2
    // | XX | XX | XX | XX |
    // +----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+----+
    //
    // Where A0 is the first input byte, B0 is the fourth, etc.
    // The alphabetical significance denotes the 3 bytes to be
    // consumed and encoded into 4 bytes.
    //
    // We then shuffle the register so each 32-bit word contains
    // the sequence:
    //    A1 A0 A2 A1, B1, B0, B2, B1, etc.
    // Each of these byte sequences are then manipulated into 4
    // 6-bit values ready for encoding.
    //
    // If we focus on one set of 3-byte chunks, changing the
    // nomenclature such that A0 => a, A1 => b, and A2 => c, we
    // shuffle such that each 24-bit chunk contains:
    //
    // b7 b6 b5 b4 b3 b2 b1 b0 | a7 a6 a5 a4 a3 a2 a1 a0 | c7 c6
    // c5 c4 c3 c2 c1 c0 | b7 b6 b5 b4 b3 b2 b1 b0
    // Explain this step.
    // b3 b2 b1 b0 c5 c4 c3 c2 | c1 c0 d5 d4 d3 d2 d1 d0 | a5 a4
    // a3 a2 a1 a0 b5 b4 | b3 b2 b1 b0 c5 c4 c3 c2
    //
    // W first and off all but bits 4-9 and 16-21 (c5..c0 and
    // a5..a0) and shift them using a vector multiplication
    // operation (vpmulhuw) which effectively shifts c right by 6
    // bits and a right by 10 bits.  We similarly mask bits 10-15
    // (d5..d0) and 22-27 (b5..b0) and shift them left by 8 and 4
    // bits respectively.  This is done using vpmullw.  We end up
    // with 4 6-bit values, thus splitting the 3 input bytes,
    // ready for encoding:
    //    0 0 d5..d0 0 0 c5..c0 0 0 b5..b0 0 0 a5..a0
    //
    // For translation, we recognize that there are 5 distinct
    // ranges of legal Base64 characters as below:
    //
    //   +-------------+-------------+------------+
    //   | 6-bit value | ASCII range |   offset   |
    //   +-------------+-------------+------------+
    //   |    0..25    |    A..Z     |     65     |
    //   |   26..51    |    a..z     |     71     |
    //   |   52..61    |    0..9     |     -4     |
    //   |     62      |   + or -    | -19 or -17 |
    //   |     63      |   / or _    | -16 or 32  |
    //   +-------------+-------------+------------+
    //
    // We note that vpshufb does a parallel lookup in a
    // destination register using the lower 4 bits of bytes from a
    // source register.  If we use a saturated subtraction and
    // subtract 51 from each 6-bit value, bytes from [0,51]
    // saturate to 0, and [52,63] map to a range of [1,12].  We
    // distinguish the [0,25] and [26,51] ranges by assigning a
    // value of 13 for all 6-bit values less than 26.  We end up
    // with:
    //
    //   +-------------+-------------+------------+
    //   | 6-bit value |   Reduced   |   offset   |
    //   +-------------+-------------+------------+
    //   |    0..25    |     13      |     65     |
    //   |   26..51    |      0      |     71     |
    //   |   52..61    |    0..9     |     -4     |
    //   |     62      |     11      | -19 or -17 |
    //   |     63      |     12      | -16 or 32  |
    //   +-------------+-------------+------------+
    //
    // We then use a final vpshufb to add the appropriate offset,
    // translating the bytes.
    //
    // Load input bytes - only 28 bytes.  Mask the first load to
    // not load into the full register.
    __ vpmaskmovd(xmm1, xmm1, Address(source, start_offset, Address::times_1, -4), Assembler::AVX_256bit);

    // Move 3-byte chunks of input (12 bytes) into 16 bytes,
    // ordering by:
    //   1, 0, 2, 1; 4, 3, 5, 4; etc.  This groups 6-bit chunks
    //   for easy masking
    __ vpshufb(xmm1, xmm1, xmm9, Assembler::AVX_256bit);

    __ addl(start_offset, 24);

    // Load masking register for first and third (and multiples)
    // 6-bit values.
    __ movl(rax, 0x003f03f0);
    __ movdl(xmm6, rax);
    __ vpbroadcastd(xmm6, xmm6, Assembler::AVX_256bit);
    // Multiplication constant for "shifting" left by 4 and 8 bits
    __ movl(rax, 0x01000010);
    __ movdl(xmm5, rax);
    __ vpbroadcastd(xmm5, xmm5, Assembler::AVX_256bit);

    // Isolate 6-bit chunks of interest
    __ vpand(xmm0, xmm8, xmm1, Assembler::AVX_256bit);

    // Load constants for encoding
    __ movl(rax, 0x19191919);
    __ movdl(xmm3, rax);
    __ vpbroadcastd(xmm3, xmm3, Assembler::AVX_256bit);
    __ movl(rax, 0x33333333);
    __ movdl(xmm4, rax);
    __ vpbroadcastd(xmm4, xmm4, Assembler::AVX_256bit);

    // Shift output bytes 0 and 2 into proper lanes
    __ vpmulhuw(xmm2, xmm0, xmm7, Assembler::AVX_256bit);

    // Mask and shift output bytes 1 and 3 into proper lanes and
    // combine
    __ vpand(xmm0, xmm6, xmm1, Assembler::AVX_256bit);
    __ vpmullw(xmm0, xmm5, xmm0, Assembler::AVX_256bit);
    __ vpor(xmm0, xmm0, xmm2, Assembler::AVX_256bit);

    // Find out which are 0..25.  This indicates which input
    // values fall in the range of 'A'-'Z', which require an
    // additional offset (see comments above)
    __ vpcmpgtb(xmm2, xmm0, xmm3, Assembler::AVX_256bit);
    __ vpsubusb(xmm1, xmm0, xmm4, Assembler::AVX_256bit);
    __ vpsubb(xmm1, xmm1, xmm2, Assembler::AVX_256bit);

    // Load the proper lookup table
    __ lea(r11, ExternalAddress(StubRoutines::x86::base64_avx2_lut_addr()));
    __ movl(r15, isURL);
    __ shll(r15, 5);
    __ vmovdqu(xmm2, Address(r11, r15));

    // Shuffle the offsets based on the range calculation done
    // above. This allows us to add the correct offset to the
    // 6-bit value corresponding to the range documented above.
    __ vpshufb(xmm1, xmm2, xmm1, Assembler::AVX_256bit);
    __ vpaddb(xmm0, xmm1, xmm0, Assembler::AVX_256bit);

    // Store the encoded bytes
    __ vmovdqu(Address(dest, dp), xmm0);
    __ addl(dp, 32);

    __ cmpl(length, 31);
    __ jcc(Assembler::belowEqual, L_process3);

    __ align32();
    __ BIND(L_32byteLoop);

    // Get next 32 bytes
    __ vmovdqu(xmm1, Address(source, start_offset, Address::times_1, -4));

    __ subl(length, 24);
    __ addl(start_offset, 24);

    // This logic is identical to the above, with only constant
    // register loads removed.  Shuffle the input, mask off 6-bit
    // chunks, shift them into place, then add the offset to
    // encode.
    __ vpshufb(xmm1, xmm1, xmm9, Assembler::AVX_256bit);

    __ vpand(xmm0, xmm8, xmm1, Assembler::AVX_256bit);
    __ vpmulhuw(xmm10, xmm0, xmm7, Assembler::AVX_256bit);
    __ vpand(xmm0, xmm6, xmm1, Assembler::AVX_256bit);
    __ vpmullw(xmm0, xmm5, xmm0, Assembler::AVX_256bit);
    __ vpor(xmm0, xmm0, xmm10, Assembler::AVX_256bit);
    __ vpcmpgtb(xmm10, xmm0, xmm3, Assembler::AVX_256bit);
    __ vpsubusb(xmm1, xmm0, xmm4, Assembler::AVX_256bit);
    __ vpsubb(xmm1, xmm1, xmm10, Assembler::AVX_256bit);
    __ vpshufb(xmm1, xmm2, xmm1, Assembler::AVX_256bit);
    __ vpaddb(xmm0, xmm1, xmm0, Assembler::AVX_256bit);

    // Store the encoded bytes
    __ vmovdqu(Address(dest, dp), xmm0);
    __ addl(dp, 32);

    __ cmpl(length, 31);
    __ jcc(Assembler::above, L_32byteLoop);

    __ BIND(L_process3);
    __ vzeroupper();
  } else {
    __ BIND(L_process3);
  }

  __ cmpl(length, 3);
  __ jcc(Assembler::below, L_exit);

  // Load the encoding table based on isURL
  __ lea(r11, ExternalAddress(StubRoutines::x86::base64_encoding_table_addr()));
  __ movl(r15, isURL);
  __ shll(r15, 6);
  __ addptr(r11, r15);

  __ BIND(L_processdata);

  // Load 3 bytes
  __ load_unsigned_byte(r15, Address(source, start_offset));
  __ load_unsigned_byte(r10, Address(source, start_offset, Address::times_1, 1));
  __ load_unsigned_byte(r13, Address(source, start_offset, Address::times_1, 2));

  // Build a 32-bit word with bytes 1, 2, 0, 1
  __ movl(rax, r10);
  __ shll(r10, 24);
  __ orl(rax, r10);

  __ subl(length, 3);

  __ shll(r15, 8);
  __ shll(r13, 16);
  __ orl(rax, r15);

  __ addl(start_offset, 3);

  __ orl(rax, r13);
  // At this point, rax contains | byte1 | byte2 | byte0 | byte1
  // r13 has byte2 << 16 - need low-order 6 bits to translate.
  // This translated byte is the fourth output byte.
  __ shrl(r13, 16);
  __ andl(r13, 0x3f);

  // The high-order 6 bits of r15 (byte0) is translated.
  // The translated byte is the first output byte.
  __ shrl(r15, 10);

  __ load_unsigned_byte(r13, Address(r11, r13));
  __ load_unsigned_byte(r15, Address(r11, r15));

  __ movb(Address(dest, dp, Address::times_1, 3), r13);

  // Extract high-order 4 bits of byte1 and low-order 2 bits of byte0.
  // This translated byte is the second output byte.
  __ shrl(rax, 4);
  __ movl(r10, rax);
  __ andl(rax, 0x3f);

  __ movb(Address(dest, dp, Address::times_1, 0), r15);

  __ load_unsigned_byte(rax, Address(r11, rax));

  // Extract low-order 2 bits of byte1 and high-order 4 bits of byte2.
  // This translated byte is the third output byte.
  __ shrl(r10, 18);
  __ andl(r10, 0x3f);

  __ load_unsigned_byte(r10, Address(r11, r10));

  __ movb(Address(dest, dp, Address::times_1, 1), rax);
  __ movb(Address(dest, dp, Address::times_1, 2), r10);

  __ addl(dp, 4);
  __ cmpl(length, 3);
  __ jcc(Assembler::aboveEqual, L_processdata);

  __ BIND(L_exit);
  __ pop(r15);
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);
  __ leave();
  __ ret(0);

  return start;
}

// base64 AVX512vbmi tables
address StubGenerator::base64_vbmi_lookup_lo_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "lookup_lo_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x3f8080803e808080, relocInfo::none);
  __ emit_data64(0x3b3a393837363534, relocInfo::none);
  __ emit_data64(0x8080808080803d3c, relocInfo::none);

  return start;
}

address StubGenerator::base64_vbmi_lookup_hi_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "lookup_hi_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x0605040302010080, relocInfo::none);
  __ emit_data64(0x0e0d0c0b0a090807, relocInfo::none);
  __ emit_data64(0x161514131211100f, relocInfo::none);
  __ emit_data64(0x8080808080191817, relocInfo::none);
  __ emit_data64(0x201f1e1d1c1b1a80, relocInfo::none);
  __ emit_data64(0x2827262524232221, relocInfo::none);
  __ emit_data64(0x302f2e2d2c2b2a29, relocInfo::none);
  __ emit_data64(0x8080808080333231, relocInfo::none);

  return start;
}
address StubGenerator::base64_vbmi_lookup_lo_url_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "lookup_lo_base64url");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x8080808080808080, relocInfo::none);
  __ emit_data64(0x80803e8080808080, relocInfo::none);
  __ emit_data64(0x3b3a393837363534, relocInfo::none);
  __ emit_data64(0x8080808080803d3c, relocInfo::none);

  return start;
}

address StubGenerator::base64_vbmi_lookup_hi_url_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "lookup_hi_base64url");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x0605040302010080, relocInfo::none);
  __ emit_data64(0x0e0d0c0b0a090807, relocInfo::none);
  __ emit_data64(0x161514131211100f, relocInfo::none);
  __ emit_data64(0x3f80808080191817, relocInfo::none);
  __ emit_data64(0x201f1e1d1c1b1a80, relocInfo::none);
  __ emit_data64(0x2827262524232221, relocInfo::none);
  __ emit_data64(0x302f2e2d2c2b2a29, relocInfo::none);
  __ emit_data64(0x8080808080333231, relocInfo::none);

  return start;
}

address StubGenerator::base64_vbmi_pack_vec_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "pack_vec_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x090a040506000102, relocInfo::none);
  __ emit_data64(0x161011120c0d0e08, relocInfo::none);
  __ emit_data64(0x1c1d1e18191a1415, relocInfo::none);
  __ emit_data64(0x292a242526202122, relocInfo::none);
  __ emit_data64(0x363031322c2d2e28, relocInfo::none);
  __ emit_data64(0x3c3d3e38393a3435, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);

  return start;
}

address StubGenerator::base64_vbmi_join_0_1_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "join_0_1_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x090a040506000102, relocInfo::none);
  __ emit_data64(0x161011120c0d0e08, relocInfo::none);
  __ emit_data64(0x1c1d1e18191a1415, relocInfo::none);
  __ emit_data64(0x292a242526202122, relocInfo::none);
  __ emit_data64(0x363031322c2d2e28, relocInfo::none);
  __ emit_data64(0x3c3d3e38393a3435, relocInfo::none);
  __ emit_data64(0x494a444546404142, relocInfo::none);
  __ emit_data64(0x565051524c4d4e48, relocInfo::none);

  return start;
}

address StubGenerator::base64_vbmi_join_1_2_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "join_1_2_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x1c1d1e18191a1415, relocInfo::none);
  __ emit_data64(0x292a242526202122, relocInfo::none);
  __ emit_data64(0x363031322c2d2e28, relocInfo::none);
  __ emit_data64(0x3c3d3e38393a3435, relocInfo::none);
  __ emit_data64(0x494a444546404142, relocInfo::none);
  __ emit_data64(0x565051524c4d4e48, relocInfo::none);
  __ emit_data64(0x5c5d5e58595a5455, relocInfo::none);
  __ emit_data64(0x696a646566606162, relocInfo::none);

  return start;
}

address StubGenerator::base64_vbmi_join_2_3_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "join_2_3_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data64(0x363031322c2d2e28, relocInfo::none);
  __ emit_data64(0x3c3d3e38393a3435, relocInfo::none);
  __ emit_data64(0x494a444546404142, relocInfo::none);
  __ emit_data64(0x565051524c4d4e48, relocInfo::none);
  __ emit_data64(0x5c5d5e58595a5455, relocInfo::none);
  __ emit_data64(0x696a646566606162, relocInfo::none);
  __ emit_data64(0x767071726c6d6e68, relocInfo::none);
  __ emit_data64(0x7c7d7e78797a7475, relocInfo::none);

  return start;
}

address StubGenerator::base64_AVX2_decode_tables_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "AVX2_tables_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  __ emit_data(0x2f2f2f2f, relocInfo::none, 0);
  __ emit_data(0x5f5f5f5f, relocInfo::none, 0);  // for URL

  __ emit_data(0xffffffff, relocInfo::none, 0);
  __ emit_data(0xfcfcfcfc, relocInfo::none, 0);  // for URL

  // Permute table
  __ emit_data64(0x0000000100000000, relocInfo::none);
  __ emit_data64(0x0000000400000002, relocInfo::none);
  __ emit_data64(0x0000000600000005, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);

  // Shuffle table
  __ emit_data64(0x090a040506000102, relocInfo::none);
  __ emit_data64(0xffffffff0c0d0e08, relocInfo::none);
  __ emit_data64(0x090a040506000102, relocInfo::none);
  __ emit_data64(0xffffffff0c0d0e08, relocInfo::none);

  // merge table
  __ emit_data(0x01400140, relocInfo::none, 0);

  // merge multiplier
  __ emit_data(0x00011000, relocInfo::none, 0);

  return start;
}

address StubGenerator::base64_AVX2_decode_LUT_tables_addr() {
  __ align64();
  StubCodeMark mark(this, "StubRoutines", "AVX2_tables_URL_base64");
  address start = __ pc();

  assert(((unsigned long long)start & 0x3f) == 0,
         "Alignment problem (0x%08llx)", (unsigned long long)start);
  // lut_lo
  __ emit_data64(0x1111111111111115, relocInfo::none);
  __ emit_data64(0x1a1b1b1b1a131111, relocInfo::none);
  __ emit_data64(0x1111111111111115, relocInfo::none);
  __ emit_data64(0x1a1b1b1b1a131111, relocInfo::none);

  // lut_roll
  __ emit_data64(0xb9b9bfbf04131000, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);
  __ emit_data64(0xb9b9bfbf04131000, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);

  // lut_lo URL
  __ emit_data64(0x1111111111111115, relocInfo::none);
  __ emit_data64(0x1b1b1a1b1b131111, relocInfo::none);
  __ emit_data64(0x1111111111111115, relocInfo::none);
  __ emit_data64(0x1b1b1a1b1b131111, relocInfo::none);

  // lut_roll URL
  __ emit_data64(0xb9b9bfbf0411e000, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);
  __ emit_data64(0xb9b9bfbf0411e000, relocInfo::none);
  __ emit_data64(0x0000000000000000, relocInfo::none);

  // lut_hi
  __ emit_data64(0x0804080402011010, relocInfo::none);
  __ emit_data64(0x1010101010101010, relocInfo::none);
  __ emit_data64(0x0804080402011010, relocInfo::none);
  __ emit_data64(0x1010101010101010, relocInfo::none);

  return start;
}

address StubGenerator::base64_decoding_table_addr() {
  StubCodeMark mark(this, "StubRoutines", "decoding_table_base64");
  address start = __ pc();

  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0x3fffffff3effffff, relocInfo::none);
  __ emit_data64(0x3b3a393837363534, relocInfo::none);
  __ emit_data64(0xffffffffffff3d3c, relocInfo::none);
  __ emit_data64(0x06050403020100ff, relocInfo::none);
  __ emit_data64(0x0e0d0c0b0a090807, relocInfo::none);
  __ emit_data64(0x161514131211100f, relocInfo::none);
  __ emit_data64(0xffffffffff191817, relocInfo::none);
  __ emit_data64(0x201f1e1d1c1b1aff, relocInfo::none);
  __ emit_data64(0x2827262524232221, relocInfo::none);
  __ emit_data64(0x302f2e2d2c2b2a29, relocInfo::none);
  __ emit_data64(0xffffffffff333231, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);

  // URL table
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffff3effffffffff, relocInfo::none);
  __ emit_data64(0x3b3a393837363534, relocInfo::none);
  __ emit_data64(0xffffffffffff3d3c, relocInfo::none);
  __ emit_data64(0x06050403020100ff, relocInfo::none);
  __ emit_data64(0x0e0d0c0b0a090807, relocInfo::none);
  __ emit_data64(0x161514131211100f, relocInfo::none);
  __ emit_data64(0x3fffffffff191817, relocInfo::none);
  __ emit_data64(0x201f1e1d1c1b1aff, relocInfo::none);
  __ emit_data64(0x2827262524232221, relocInfo::none);
  __ emit_data64(0x302f2e2d2c2b2a29, relocInfo::none);
  __ emit_data64(0xffffffffff333231, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);
  __ emit_data64(0xffffffffffffffff, relocInfo::none);

  return start;
}


// Code for generating Base64 decoding.
//
// Based on the article (and associated code) from https://arxiv.org/abs/1910.05109.
//
// Intrinsic function prototype in Base64.java:
// private void decodeBlock(byte[] src, int sp, int sl, byte[] dst, int dp, boolean isURL, isMIME) {
address StubGenerator::generate_base64_decodeBlock() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "implDecode");
  address start = __ pc();

  __ enter();

  // Save callee-saved registers before using them
  __ push(r12);
  __ push(r13);
  __ push(r14);
  __ push(r15);
  __ push(rbx);

  // arguments
  const Register source = c_rarg0; // Source Array
  const Register start_offset = c_rarg1; // start offset
  const Register end_offset = c_rarg2; // end offset
  const Register dest = c_rarg3; // destination array
  const Register isMIME = rbx;

#ifndef _WIN64
  const Register dp = c_rarg4;  // Position for writing to dest array
  const Register isURL = c_rarg5;// Base64 or URL character set
  __ movl(isMIME, Address(rbp, 2 * wordSize));
#else
  const Address  dp_mem(rbp, 6 * wordSize);  // length is on stack on Win64
  const Address isURL_mem(rbp, 7 * wordSize);
  const Register isURL = r10;      // pick the volatile windows register
  const Register dp = r12;
  __ movl(dp, dp_mem);
  __ movl(isURL, isURL_mem);
  __ movl(isMIME, Address(rbp, 8 * wordSize));
#endif

  const XMMRegister lookup_lo = xmm5;
  const XMMRegister lookup_hi = xmm6;
  const XMMRegister errorvec = xmm7;
  const XMMRegister pack16_op = xmm9;
  const XMMRegister pack32_op = xmm8;
  const XMMRegister input0 = xmm3;
  const XMMRegister input1 = xmm20;
  const XMMRegister input2 = xmm21;
  const XMMRegister input3 = xmm19;
  const XMMRegister join01 = xmm12;
  const XMMRegister join12 = xmm11;
  const XMMRegister join23 = xmm10;
  const XMMRegister translated0 = xmm2;
  const XMMRegister translated1 = xmm1;
  const XMMRegister translated2 = xmm0;
  const XMMRegister translated3 = xmm4;

  const XMMRegister merged0 = xmm2;
  const XMMRegister merged1 = xmm1;
  const XMMRegister merged2 = xmm0;
  const XMMRegister merged3 = xmm4;
  const XMMRegister merge_ab_bc0 = xmm2;
  const XMMRegister merge_ab_bc1 = xmm1;
  const XMMRegister merge_ab_bc2 = xmm0;
  const XMMRegister merge_ab_bc3 = xmm4;

  const XMMRegister pack24bits = xmm4;

  const Register length = r14;
  const Register output_size = r13;
  const Register output_mask = r15;
  const KRegister input_mask = k1;

  const XMMRegister input_initial_valid_b64 = xmm0;
  const XMMRegister tmp = xmm10;
  const XMMRegister mask = xmm0;
  const XMMRegister invalid_b64 = xmm1;

  Label L_process256, L_process64, L_process64Loop, L_exit, L_processdata, L_loadURL;
  Label L_continue, L_finalBit, L_padding, L_donePadding, L_bruteForce;
  Label L_forceLoop, L_bottomLoop, L_checkMIME, L_exit_no_vzero, L_lastChunk;

  // calculate length from offsets
  __ movl(length, end_offset);
  __ subl(length, start_offset);
  __ push(dest);          // Save for return value calc

  // If AVX512 VBMI not supported, just compile non-AVX code
  if(VM_Version::supports_avx512_vbmi() &&
     VM_Version::supports_avx512bw()) {
    __ cmpl(length, 31);     // 32-bytes is break-even for AVX-512
    __ jcc(Assembler::lessEqual, L_lastChunk);

    __ cmpl(isMIME, 0);
    __ jcc(Assembler::notEqual, L_lastChunk);

    // Load lookup tables based on isURL
    __ cmpl(isURL, 0);
    __ jcc(Assembler::notZero, L_loadURL);

    __ evmovdquq(lookup_lo, ExternalAddress(StubRoutines::x86::base64_vbmi_lookup_lo_addr()), Assembler::AVX_512bit, r13);
    __ evmovdquq(lookup_hi, ExternalAddress(StubRoutines::x86::base64_vbmi_lookup_hi_addr()), Assembler::AVX_512bit, r13);

    __ BIND(L_continue);

    __ movl(r15, 0x01400140);
    __ evpbroadcastd(pack16_op, r15, Assembler::AVX_512bit);

    __ movl(r15, 0x00011000);
    __ evpbroadcastd(pack32_op, r15, Assembler::AVX_512bit);

    __ cmpl(length, 0xff);
    __ jcc(Assembler::lessEqual, L_process64);

    // load masks required for decoding data
    __ BIND(L_processdata);
    __ evmovdquq(join01, ExternalAddress(StubRoutines::x86::base64_vbmi_join_0_1_addr()), Assembler::AVX_512bit,r13);
    __ evmovdquq(join12, ExternalAddress(StubRoutines::x86::base64_vbmi_join_1_2_addr()), Assembler::AVX_512bit, r13);
    __ evmovdquq(join23, ExternalAddress(StubRoutines::x86::base64_vbmi_join_2_3_addr()), Assembler::AVX_512bit, r13);

    __ align32();
    __ BIND(L_process256);
    // Grab input data
    __ evmovdquq(input0, Address(source, start_offset, Address::times_1, 0x00), Assembler::AVX_512bit);
    __ evmovdquq(input1, Address(source, start_offset, Address::times_1, 0x40), Assembler::AVX_512bit);
    __ evmovdquq(input2, Address(source, start_offset, Address::times_1, 0x80), Assembler::AVX_512bit);
    __ evmovdquq(input3, Address(source, start_offset, Address::times_1, 0xc0), Assembler::AVX_512bit);

    // Copy the low part of the lookup table into the destination of the permutation
    __ evmovdquq(translated0, lookup_lo, Assembler::AVX_512bit);
    __ evmovdquq(translated1, lookup_lo, Assembler::AVX_512bit);
    __ evmovdquq(translated2, lookup_lo, Assembler::AVX_512bit);
    __ evmovdquq(translated3, lookup_lo, Assembler::AVX_512bit);

    // Translate the base64 input into "decoded" bytes
    __ evpermt2b(translated0, input0, lookup_hi, Assembler::AVX_512bit);
    __ evpermt2b(translated1, input1, lookup_hi, Assembler::AVX_512bit);
    __ evpermt2b(translated2, input2, lookup_hi, Assembler::AVX_512bit);
    __ evpermt2b(translated3, input3, lookup_hi, Assembler::AVX_512bit);

    // OR all of the translations together to check for errors (high-order bit of byte set)
    __ vpternlogd(input0, 0xfe, input1, input2, Assembler::AVX_512bit);

    __ vpternlogd(input3, 0xfe, translated0, translated1, Assembler::AVX_512bit);
    __ vpternlogd(input0, 0xfe, translated2, translated3, Assembler::AVX_512bit);
    __ vpor(errorvec, input3, input0, Assembler::AVX_512bit);

    // Check if there was an error - if so, try 64-byte chunks
    __ evpmovb2m(k3, errorvec, Assembler::AVX_512bit);
    __ kortestql(k3, k3);
    __ jcc(Assembler::notZero, L_process64);

    // The merging and shuffling happens here
    // We multiply each byte pair [00dddddd | 00cccccc | 00bbbbbb | 00aaaaaa]
    // Multiply [00cccccc] by 2^6 added to [00dddddd] to get [0000cccc | ccdddddd]
    // The pack16_op is a vector of 0x01400140, so multiply D by 1 and C by 0x40
    __ vpmaddubsw(merge_ab_bc0, translated0, pack16_op, Assembler::AVX_512bit);
    __ vpmaddubsw(merge_ab_bc1, translated1, pack16_op, Assembler::AVX_512bit);
    __ vpmaddubsw(merge_ab_bc2, translated2, pack16_op, Assembler::AVX_512bit);
    __ vpmaddubsw(merge_ab_bc3, translated3, pack16_op, Assembler::AVX_512bit);

    // Now do the same with packed 16-bit values.
    // We start with [0000cccc | ccdddddd | 0000aaaa | aabbbbbb]
    // pack32_op is 0x00011000 (2^12, 1), so this multiplies [0000aaaa | aabbbbbb] by 2^12
    // and adds [0000cccc | ccdddddd] to yield [00000000 | aaaaaabb | bbbbcccc | ccdddddd]
    __ vpmaddwd(merged0, merge_ab_bc0, pack32_op, Assembler::AVX_512bit);
    __ vpmaddwd(merged1, merge_ab_bc1, pack32_op, Assembler::AVX_512bit);
    __ vpmaddwd(merged2, merge_ab_bc2, pack32_op, Assembler::AVX_512bit);
    __ vpmaddwd(merged3, merge_ab_bc3, pack32_op, Assembler::AVX_512bit);

    // The join vectors specify which byte from which vector goes into the outputs
    // One of every 4 bytes in the extended vector is zero, so we pack them into their
    // final positions in the register for storing (256 bytes in, 192 bytes out)
    __ evpermt2b(merged0, join01, merged1, Assembler::AVX_512bit);
    __ evpermt2b(merged1, join12, merged2, Assembler::AVX_512bit);
    __ evpermt2b(merged2, join23, merged3, Assembler::AVX_512bit);

    // Store result
    __ evmovdquq(Address(dest, dp, Address::times_1, 0x00), merged0, Assembler::AVX_512bit);
    __ evmovdquq(Address(dest, dp, Address::times_1, 0x40), merged1, Assembler::AVX_512bit);
    __ evmovdquq(Address(dest, dp, Address::times_1, 0x80), merged2, Assembler::AVX_512bit);

    __ addptr(source, 0x100);
    __ addptr(dest, 0xc0);
    __ subl(length, 0x100);
    __ cmpl(length, 64 * 4);
    __ jcc(Assembler::greaterEqual, L_process256);

    // At this point, we've decoded 64 * 4 * n bytes.
    // The remaining length will be <= 64 * 4 - 1.
    // UNLESS there was an error decoding the first 256-byte chunk.  In this
    // case, the length will be arbitrarily long.
    //
    // Note that this will be the path for MIME-encoded strings.

    __ BIND(L_process64);

    __ evmovdquq(pack24bits, ExternalAddress(StubRoutines::x86::base64_vbmi_pack_vec_addr()), Assembler::AVX_512bit, r13);

    __ cmpl(length, 63);
    __ jcc(Assembler::lessEqual, L_finalBit);

    __ mov64(rax, 0x0000ffffffffffff);
    __ kmovql(k2, rax);

    __ align32();
    __ BIND(L_process64Loop);

    // Handle first 64-byte block

    __ evmovdquq(input0, Address(source, start_offset), Assembler::AVX_512bit);
    __ evmovdquq(translated0, lookup_lo, Assembler::AVX_512bit);
    __ evpermt2b(translated0, input0, lookup_hi, Assembler::AVX_512bit);

    __ vpor(errorvec, translated0, input0, Assembler::AVX_512bit);

    // Check for error and bomb out before updating dest
    __ evpmovb2m(k3, errorvec, Assembler::AVX_512bit);
    __ kortestql(k3, k3);
    __ jcc(Assembler::notZero, L_exit);

    // Pack output register, selecting correct byte ordering
    __ vpmaddubsw(merge_ab_bc0, translated0, pack16_op, Assembler::AVX_512bit);
    __ vpmaddwd(merged0, merge_ab_bc0, pack32_op, Assembler::AVX_512bit);
    __ vpermb(merged0, pack24bits, merged0, Assembler::AVX_512bit);

    __ evmovdqub(Address(dest, dp), k2, merged0, true, Assembler::AVX_512bit);

    __ subl(length, 64);
    __ addptr(source, 64);
    __ addptr(dest, 48);

    __ cmpl(length, 64);
    __ jcc(Assembler::greaterEqual, L_process64Loop);

    __ cmpl(length, 0);
    __ jcc(Assembler::lessEqual, L_exit);

    __ BIND(L_finalBit);
    // Now have 1 to 63 bytes left to decode

    // I was going to let Java take care of the final fragment
    // however it will repeatedly call this routine for every 4 bytes
    // of input data, so handle the rest here.
    __ movq(rax, -1);
    __ bzhiq(rax, rax, length);    // Input mask in rax

    __ movl(output_size, length);
    __ shrl(output_size, 2);   // Find (len / 4) * 3 (output length)
    __ lea(output_size, Address(output_size, output_size, Address::times_2, 0));
    // output_size in r13

    // Strip pad characters, if any, and adjust length and mask
    __ cmpb(Address(source, length, Address::times_1, -1), '=');
    __ jcc(Assembler::equal, L_padding);

    __ BIND(L_donePadding);

    // Output size is (64 - output_size), output mask is (all 1s >> output_size).
    __ kmovql(input_mask, rax);
    __ movq(output_mask, -1);
    __ bzhiq(output_mask, output_mask, output_size);

    // Load initial input with all valid base64 characters.  Will be used
    // in merging source bytes to avoid masking when determining if an error occurred.
    __ movl(rax, 0x61616161);
    __ evpbroadcastd(input_initial_valid_b64, rax, Assembler::AVX_512bit);

    // A register containing all invalid base64 decoded values
    __ movl(rax, 0x80808080);
    __ evpbroadcastd(invalid_b64, rax, Assembler::AVX_512bit);

    // input_mask is in k1
    // output_size is in r13
    // output_mask is in r15
    // zmm0 - free
    // zmm1 - 0x00011000
    // zmm2 - 0x01400140
    // zmm3 - errorvec
    // zmm4 - pack vector
    // zmm5 - lookup_lo
    // zmm6 - lookup_hi
    // zmm7 - errorvec
    // zmm8 - 0x61616161
    // zmm9 - 0x80808080

    // Load only the bytes from source, merging into our "fully-valid" register
    __ evmovdqub(input_initial_valid_b64, input_mask, Address(source, start_offset, Address::times_1, 0x0), true, Assembler::AVX_512bit);

    // Decode all bytes within our merged input
    __ evmovdquq(tmp, lookup_lo, Assembler::AVX_512bit);
    __ evpermt2b(tmp, input_initial_valid_b64, lookup_hi, Assembler::AVX_512bit);
    __ evporq(mask, tmp, input_initial_valid_b64, Assembler::AVX_512bit);

    // Check for error.  Compare (decoded | initial) to all invalid.
    // If any bytes have their high-order bit set, then we have an error.
    __ evptestmb(k2, mask, invalid_b64, Assembler::AVX_512bit);
    __ kortestql(k2, k2);

    // If we have an error, use the brute force loop to decode what we can (4-byte chunks).
    __ jcc(Assembler::notZero, L_bruteForce);

    // Shuffle output bytes
    __ vpmaddubsw(tmp, tmp, pack16_op, Assembler::AVX_512bit);
    __ vpmaddwd(tmp, tmp, pack32_op, Assembler::AVX_512bit);

    __ vpermb(tmp, pack24bits, tmp, Assembler::AVX_512bit);
    __ kmovql(k1, output_mask);
    __ evmovdqub(Address(dest, dp), k1, tmp, true, Assembler::AVX_512bit);

    __ addptr(dest, output_size);

    __ BIND(L_exit);
    __ vzeroupper();
    __ pop(rax);             // Get original dest value
    __ subptr(dest, rax);      // Number of bytes converted
    __ movptr(rax, dest);
    __ pop(rbx);
    __ pop(r15);
    __ pop(r14);
    __ pop(r13);
    __ pop(r12);
    __ leave();
    __ ret(0);

    __ BIND(L_loadURL);
    __ evmovdquq(lookup_lo, ExternalAddress(StubRoutines::x86::base64_vbmi_lookup_lo_url_addr()), Assembler::AVX_512bit, r13);
    __ evmovdquq(lookup_hi, ExternalAddress(StubRoutines::x86::base64_vbmi_lookup_hi_url_addr()), Assembler::AVX_512bit, r13);
    __ jmp(L_continue);

    __ BIND(L_padding);
    __ decrementq(output_size, 1);
    __ shrq(rax, 1);

    __ cmpb(Address(source, length, Address::times_1, -2), '=');
    __ jcc(Assembler::notEqual, L_donePadding);

    __ decrementq(output_size, 1);
    __ shrq(rax, 1);
    __ jmp(L_donePadding);

    __ align32();
    __ BIND(L_bruteForce);
  }   // End of if(avx512_vbmi)

  if (VM_Version::supports_avx2()) {
    Label L_tailProc, L_topLoop, L_enterLoop;

    __ cmpl(isMIME, 0);
    __ jcc(Assembler::notEqual, L_lastChunk);

    // Check for buffer too small (for algorithm)
    __ subl(length, 0x2c);
    __ jcc(Assembler::less, L_tailProc);

    __ shll(isURL, 2);

    // Algorithm adapted from https://arxiv.org/abs/1704.00605, "Faster Base64
    // Encoding and Decoding using AVX2 Instructions".  URL modifications added.

    // Set up constants
    __ lea(r13, ExternalAddress(StubRoutines::x86::base64_AVX2_decode_tables_addr()));
    __ vpbroadcastd(xmm4, Address(r13, isURL, Address::times_1), Assembler::AVX_256bit);  // 2F or 5F
    __ vpbroadcastd(xmm10, Address(r13, isURL, Address::times_1, 0x08), Assembler::AVX_256bit);  // -1 or -4
    __ vmovdqu(xmm12, Address(r13, 0x10));  // permute
    __ vmovdqu(xmm13, Address(r13, 0x30)); // shuffle
    __ vpbroadcastd(xmm7, Address(r13, 0x50), Assembler::AVX_256bit);  // merge
    __ vpbroadcastd(xmm6, Address(r13, 0x54), Assembler::AVX_256bit);  // merge mult

    __ lea(r13, ExternalAddress(StubRoutines::x86::base64_AVX2_decode_LUT_tables_addr()));
    __ shll(isURL, 4);
    __ vmovdqu(xmm11, Address(r13, isURL, Address::times_1, 0x00));  // lut_lo
    __ vmovdqu(xmm8, Address(r13, isURL, Address::times_1, 0x20)); // lut_roll
    __ shrl(isURL, 6);  // restore isURL
    __ vmovdqu(xmm9, Address(r13, 0x80));  // lut_hi
    __ jmp(L_enterLoop);

    __ align32();
    __ bind(L_topLoop);
    // Add in the offset value (roll) to get 6-bit out values
    __ vpaddb(xmm0, xmm0, xmm2, Assembler::AVX_256bit);
    // Merge and permute the output bits into appropriate output byte lanes
    __ vpmaddubsw(xmm0, xmm0, xmm7, Assembler::AVX_256bit);
    __ vpmaddwd(xmm0, xmm0, xmm6, Assembler::AVX_256bit);
    __ vpshufb(xmm0, xmm0, xmm13, Assembler::AVX_256bit);
    __ vpermd(xmm0, xmm12, xmm0, Assembler::AVX_256bit);
    // Store the output bytes
    __ vmovdqu(Address(dest, dp, Address::times_1, 0), xmm0);
    __ addptr(source, 0x20);
    __ addptr(dest, 0x18);
    __ subl(length, 0x20);
    __ jcc(Assembler::less, L_tailProc);

    __ bind(L_enterLoop);

    // Load in encoded string (32 bytes)
    __ vmovdqu(xmm2, Address(source, start_offset, Address::times_1, 0x0));
    // Extract the high nibble for indexing into the lut tables.  High 4 bits are don't care.
    __ vpsrld(xmm1, xmm2, 0x4, Assembler::AVX_256bit);
    __ vpand(xmm1, xmm4, xmm1, Assembler::AVX_256bit);
    // Extract the low nibble. 5F/2F will isolate the low-order 4 bits.  High 4 bits are don't care.
    __ vpand(xmm3, xmm2, xmm4, Assembler::AVX_256bit);
    // Check for special-case (0x2F or 0x5F (URL))
    __ vpcmpeqb(xmm0, xmm4, xmm2, Assembler::AVX_256bit);
    // Get the bitset based on the low nibble.  vpshufb uses low-order 4 bits only.
    __ vpshufb(xmm3, xmm11, xmm3, Assembler::AVX_256bit);
    // Get the bit value of the high nibble
    __ vpshufb(xmm5, xmm9, xmm1, Assembler::AVX_256bit);
    // Make sure 2F / 5F shows as valid
    __ vpandn(xmm3, xmm0, xmm3, Assembler::AVX_256bit);
    // Make adjustment for roll index.  For non-URL, this is a no-op,
    // for URL, this adjusts by -4.  This is to properly index the
    // roll value for 2F / 5F.
    __ vpand(xmm0, xmm0, xmm10, Assembler::AVX_256bit);
    // If the and of the two is non-zero, we have an invalid input character
    __ vptest(xmm3, xmm5);
    // Extract the "roll" value - value to add to the input to get 6-bit out value
    __ vpaddb(xmm0, xmm0, xmm1, Assembler::AVX_256bit); // Handle 2F / 5F
    __ vpshufb(xmm0, xmm8, xmm0, Assembler::AVX_256bit);
    __ jcc(Assembler::equal, L_topLoop);  // Fall through on error

    __ bind(L_tailProc);

    __ addl(length, 0x2c);

    __ vzeroupper();
  }

  // Use non-AVX code to decode 4-byte chunks into 3 bytes of output

  // Register state (Linux):
  // r12-15 - saved on stack
  // rdi - src
  // rsi - sp
  // rdx - sl
  // rcx - dst
  // r8 - dp
  // r9 - isURL

  // Register state (Windows):
  // r12-15 - saved on stack
  // rcx - src
  // rdx - sp
  // r8 - sl
  // r9 - dst
  // r12 - dp
  // r10 - isURL

  // Registers (common):
  // length (r14) - bytes in src

  const Register decode_table = r11;
  const Register out_byte_count = rbx;
  const Register byte1 = r13;
  const Register byte2 = r15;
  const Register byte3 = WIN64_ONLY(r8) NOT_WIN64(rdx);
  const Register byte4 = WIN64_ONLY(r10) NOT_WIN64(r9);

  __ bind(L_lastChunk);

  __ shrl(length, 2);    // Multiple of 4 bytes only - length is # 4-byte chunks
  __ cmpl(length, 0);
  __ jcc(Assembler::lessEqual, L_exit_no_vzero);

  __ shll(isURL, 8);    // index into decode table based on isURL
  __ lea(decode_table, ExternalAddress(StubRoutines::x86::base64_decoding_table_addr()));
  __ addptr(decode_table, isURL);

  __ jmp(L_bottomLoop);

  __ align32();
  __ BIND(L_forceLoop);
  __ shll(byte1, 18);
  __ shll(byte2, 12);
  __ shll(byte3, 6);
  __ orl(byte1, byte2);
  __ orl(byte1, byte3);
  __ orl(byte1, byte4);

  __ addptr(source, 4);

  __ movb(Address(dest, dp, Address::times_1, 2), byte1);
  __ shrl(byte1, 8);
  __ movb(Address(dest, dp, Address::times_1, 1), byte1);
  __ shrl(byte1, 8);
  __ movb(Address(dest, dp, Address::times_1, 0), byte1);

  __ addptr(dest, 3);
  __ decrementl(length, 1);
  __ jcc(Assembler::zero, L_exit_no_vzero);

  __ BIND(L_bottomLoop);
  __ load_unsigned_byte(byte1, Address(source, start_offset, Address::times_1, 0x00));
  __ load_unsigned_byte(byte2, Address(source, start_offset, Address::times_1, 0x01));
  __ load_signed_byte(byte1, Address(decode_table, byte1));
  __ load_signed_byte(byte2, Address(decode_table, byte2));
  __ load_unsigned_byte(byte3, Address(source, start_offset, Address::times_1, 0x02));
  __ load_unsigned_byte(byte4, Address(source, start_offset, Address::times_1, 0x03));
  __ load_signed_byte(byte3, Address(decode_table, byte3));
  __ load_signed_byte(byte4, Address(decode_table, byte4));

  __ mov(rax, byte1);
  __ orl(rax, byte2);
  __ orl(rax, byte3);
  __ orl(rax, byte4);
  __ jcc(Assembler::positive, L_forceLoop);

  __ BIND(L_exit_no_vzero);
  __ pop(rax);             // Get original dest value
  __ subptr(dest, rax);      // Number of bytes converted
  __ movptr(rax, dest);
  __ pop(rbx);
  __ pop(r15);
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);
  __ leave();
  __ ret(0);

  return start;
}


/**
 *  Arguments:
 *
 * Inputs:
 *   c_rarg0   - int crc
 *   c_rarg1   - byte* buf
 *   c_rarg2   - int length
 *
 * Output:
 *       rax   - int crc result
 */
address StubGenerator::generate_updateBytesCRC32() {
  assert(UseCRC32Intrinsics, "need AVX and CLMUL instructions");

  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "updateBytesCRC32");

  address start = __ pc();

  // Win64: rcx, rdx, r8, r9 (c_rarg0, c_rarg1, ...)
  // Unix:  rdi, rsi, rdx, rcx, r8, r9 (c_rarg0, c_rarg1, ...)
  // rscratch1: r10
  const Register crc   = c_rarg0;  // crc
  const Register buf   = c_rarg1;  // source java byte array address
  const Register len   = c_rarg2;  // length
  const Register table = c_rarg3;  // crc_table address (reuse register)
  const Register tmp1   = r11;
  const Register tmp2   = r10;
  assert_different_registers(crc, buf, len, table, tmp1, tmp2, rax);

  BLOCK_COMMENT("Entry:");
  __ enter(); // required for proper stackwalking of RuntimeStub frame

  if (VM_Version::supports_sse4_1() && VM_Version::supports_avx512_vpclmulqdq() &&
      VM_Version::supports_avx512bw() &&
      VM_Version::supports_avx512vl()) {
      // The constants used in the CRC32 algorithm requires the 1's compliment of the initial crc value.
      // However, the constant table for CRC32-C assumes the original crc value.  Account for this
      // difference before calling and after returning.
    __ lea(table, ExternalAddress(StubRoutines::x86::crc_table_avx512_addr()));
    __ notl(crc);
    __ kernel_crc32_avx512(crc, buf, len, table, tmp1, tmp2);
    __ notl(crc);
  } else {
    __ kernel_crc32(crc, buf, len, table, tmp1);
  }

  __ movl(rax, crc);
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

/**
*  Arguments:
*
* Inputs:
*   c_rarg0   - int crc
*   c_rarg1   - byte* buf
*   c_rarg2   - long length
*   c_rarg3   - table_start - optional (present only when doing a library_call,
*              not used by x86 algorithm)
*
* Output:
*       rax   - int crc result
*/
address StubGenerator::generate_updateBytesCRC32C(bool is_pclmulqdq_supported) {
  assert(UseCRC32CIntrinsics, "need SSE4_2");
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "updateBytesCRC32C");
  address start = __ pc();

  //reg.arg        int#0        int#1        int#2        int#3        int#4        int#5        float regs
  //Windows        RCX          RDX          R8           R9           none         none         XMM0..XMM3
  //Lin / Sol      RDI          RSI          RDX          RCX          R8           R9           XMM0..XMM7
  const Register crc = c_rarg0;  // crc
  const Register buf = c_rarg1;  // source java byte array address
  const Register len = c_rarg2;  // length
  const Register a = rax;
  const Register j = r9;
  const Register k = r10;
  const Register l = r11;
#ifdef _WIN64
  const Register y = rdi;
  const Register z = rsi;
#else
  const Register y = rcx;
  const Register z = r8;
#endif
  assert_different_registers(crc, buf, len, a, j, k, l, y, z);

  BLOCK_COMMENT("Entry:");
  __ enter(); // required for proper stackwalking of RuntimeStub frame
  Label L_continue;

  if (VM_Version::supports_sse4_1() && VM_Version::supports_avx512_vpclmulqdq() &&
      VM_Version::supports_avx512bw() &&
      VM_Version::supports_avx512vl()) {
    Label L_doSmall;

    __ cmpl(len, 384);
    __ jcc(Assembler::lessEqual, L_doSmall);

    __ lea(j, ExternalAddress(StubRoutines::x86::crc32c_table_avx512_addr()));
    __ kernel_crc32_avx512(crc, buf, len, j, l, k);

    __ jmp(L_continue);

    __ bind(L_doSmall);
  }
#ifdef _WIN64
  __ push(y);
  __ push(z);
#endif
  __ crc32c_ipl_alg2_alt2(crc, buf, len,
                          a, j, k,
                          l, y, z,
                          c_farg0, c_farg1, c_farg2,
                          is_pclmulqdq_supported);
#ifdef _WIN64
  __ pop(z);
  __ pop(y);
#endif

  __ bind(L_continue);
  __ movl(rax, crc);
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


/**
 *  Arguments:
 *
 *  Input:
 *    c_rarg0   - x address
 *    c_rarg1   - x length
 *    c_rarg2   - y address
 *    c_rarg3   - y length
 * not Win64
 *    c_rarg4   - z address
 *    c_rarg5   - z length
 * Win64
 *    rsp+40    - z address
 *    rsp+48    - z length
 */
address StubGenerator::generate_multiplyToLen() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "multiplyToLen");
  address start = __ pc();

  // Win64: rcx, rdx, r8, r9 (c_rarg0, c_rarg1, ...)
  // Unix:  rdi, rsi, rdx, rcx, r8, r9 (c_rarg0, c_rarg1, ...)
  const Register x     = rdi;
  const Register xlen  = rax;
  const Register y     = rsi;
  const Register ylen  = rcx;
  const Register z     = r8;
  const Register zlen  = r11;

  // Next registers will be saved on stack in multiply_to_len().
  const Register tmp1  = r12;
  const Register tmp2  = r13;
  const Register tmp3  = r14;
  const Register tmp4  = r15;
  const Register tmp5  = rbx;

  BLOCK_COMMENT("Entry:");
  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifndef _WIN64
  __ movptr(zlen, r9); // Save r9 in r11 - zlen
#endif
  setup_arg_regs(4); // x => rdi, xlen => rsi, y => rdx
                     // ylen => rcx, z => r8, zlen => r11
                     // r9 and r10 may be used to save non-volatile registers
#ifdef _WIN64
  // last 2 arguments (#4, #5) are on stack on Win64
  __ movptr(z, Address(rsp, 6 * wordSize));
  __ movptr(zlen, Address(rsp, 7 * wordSize));
#endif

  __ movptr(xlen, rsi);
  __ movptr(y,    rdx);
  __ multiply_to_len(x, xlen, y, ylen, z, zlen, tmp1, tmp2, tmp3, tmp4, tmp5);

  restore_arg_regs();

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

/**
*  Arguments:
*
*  Input:
*    c_rarg0   - obja     address
*    c_rarg1   - objb     address
*    c_rarg3   - length   length
*    c_rarg4   - scale    log2_array_indxscale
*
*  Output:
*        rax   - int >= mismatched index, < 0 bitwise complement of tail
*/
address StubGenerator::generate_vectorizedMismatch() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "vectorizedMismatch");
  address start = __ pc();

  BLOCK_COMMENT("Entry:");
  __ enter();

#ifdef _WIN64  // Win64: rcx, rdx, r8, r9 (c_rarg0, c_rarg1, ...)
  const Register scale = c_rarg0;  //rcx, will exchange with r9
  const Register objb = c_rarg1;   //rdx
  const Register length = c_rarg2; //r8
  const Register obja = c_rarg3;   //r9
  __ xchgq(obja, scale);  //now obja and scale contains the correct contents

  const Register tmp1 = r10;
  const Register tmp2 = r11;
#endif
#ifndef _WIN64 // Unix:  rdi, rsi, rdx, rcx, r8, r9 (c_rarg0, c_rarg1, ...)
  const Register obja = c_rarg0;   //U:rdi
  const Register objb = c_rarg1;   //U:rsi
  const Register length = c_rarg2; //U:rdx
  const Register scale = c_rarg3;  //U:rcx
  const Register tmp1 = r8;
  const Register tmp2 = r9;
#endif
  const Register result = rax; //return value
  const XMMRegister vec0 = xmm0;
  const XMMRegister vec1 = xmm1;
  const XMMRegister vec2 = xmm2;

  __ vectorized_mismatch(obja, objb, length, scale, result, tmp1, tmp2, vec0, vec1, vec2);

  __ vzeroupper();
  __ leave();
  __ ret(0);

  return start;
}

/**
 *  Arguments:
 *
//  Input:
//    c_rarg0   - x address
//    c_rarg1   - x length
//    c_rarg2   - z address
//    c_rarg3   - z length
 *
 */
address StubGenerator::generate_squareToLen() {

  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "squareToLen");
  address start = __ pc();

  // Win64: rcx, rdx, r8, r9 (c_rarg0, c_rarg1, ...)
  // Unix:  rdi, rsi, rdx, rcx (c_rarg0, c_rarg1, ...)
  const Register x      = rdi;
  const Register len    = rsi;
  const Register z      = r8;
  const Register zlen   = rcx;

 const Register tmp1      = r12;
 const Register tmp2      = r13;
 const Register tmp3      = r14;
 const Register tmp4      = r15;
 const Register tmp5      = rbx;

  BLOCK_COMMENT("Entry:");
  __ enter(); // required for proper stackwalking of RuntimeStub frame

  setup_arg_regs(4); // x => rdi, len => rsi, z => rdx
                     // zlen => rcx
                     // r9 and r10 may be used to save non-volatile registers
  __ movptr(r8, rdx);
  __ square_to_len(x, len, z, zlen, tmp1, tmp2, tmp3, tmp4, tmp5, rdx, rax);

  restore_arg_regs();

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

address StubGenerator::generate_method_entry_barrier() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "nmethod_entry_barrier");
  address start = __ pc();

  Label deoptimize_label;

  __ push(-1); // cookie, this is used for writing the new rsp when deoptimizing

  BLOCK_COMMENT("Entry:");
  __ enter(); // save rbp

  // save c_rarg0, because we want to use that value.
  // We could do without it but then we depend on the number of slots used by pusha
  __ push(c_rarg0);

  __ lea(c_rarg0, Address(rsp, wordSize * 3)); // 1 for cookie, 1 for rbp, 1 for c_rarg0 - this should be the return address

  __ pusha();

  // The method may have floats as arguments, and we must spill them before calling
  // the VM runtime.
  assert(Argument::n_float_register_parameters_j == 8, "Assumption");
  const int xmm_size = wordSize * 2;
  const int xmm_spill_size = xmm_size * Argument::n_float_register_parameters_j;
  __ subptr(rsp, xmm_spill_size);
  __ movdqu(Address(rsp, xmm_size * 7), xmm7);
  __ movdqu(Address(rsp, xmm_size * 6), xmm6);
  __ movdqu(Address(rsp, xmm_size * 5), xmm5);
  __ movdqu(Address(rsp, xmm_size * 4), xmm4);
  __ movdqu(Address(rsp, xmm_size * 3), xmm3);
  __ movdqu(Address(rsp, xmm_size * 2), xmm2);
  __ movdqu(Address(rsp, xmm_size * 1), xmm1);
  __ movdqu(Address(rsp, xmm_size * 0), xmm0);

  __ call_VM_leaf(CAST_FROM_FN_PTR(address, static_cast<int (*)(address*)>(BarrierSetNMethod::nmethod_stub_entry_barrier)), 1);

  __ movdqu(xmm0, Address(rsp, xmm_size * 0));
  __ movdqu(xmm1, Address(rsp, xmm_size * 1));
  __ movdqu(xmm2, Address(rsp, xmm_size * 2));
  __ movdqu(xmm3, Address(rsp, xmm_size * 3));
  __ movdqu(xmm4, Address(rsp, xmm_size * 4));
  __ movdqu(xmm5, Address(rsp, xmm_size * 5));
  __ movdqu(xmm6, Address(rsp, xmm_size * 6));
  __ movdqu(xmm7, Address(rsp, xmm_size * 7));
  __ addptr(rsp, xmm_spill_size);

  __ cmpl(rax, 1); // 1 means deoptimize
  __ jcc(Assembler::equal, deoptimize_label);

  __ popa();
  __ pop(c_rarg0);

  __ leave();

  __ addptr(rsp, 1 * wordSize); // cookie
  __ ret(0);


  __ BIND(deoptimize_label);

  __ popa();
  __ pop(c_rarg0);

  __ leave();

  // this can be taken out, but is good for verification purposes. getting a SIGSEGV
  // here while still having a correct stack is valuable
  __ testptr(rsp, Address(rsp, 0));

  __ movptr(rsp, Address(rsp, 0)); // new rsp was written in the barrier
  __ jmp(Address(rsp, -1 * wordSize)); // jmp target should be callers verified_entry_point

  return start;
}

 /**
 *  Arguments:
 *
 *  Input:
 *    c_rarg0   - out address
 *    c_rarg1   - in address
 *    c_rarg2   - offset
 *    c_rarg3   - len
 * not Win64
 *    c_rarg4   - k
 * Win64
 *    rsp+40    - k
 */
address StubGenerator::generate_mulAdd() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "mulAdd");
  address start = __ pc();

  // Win64: rcx, rdx, r8, r9 (c_rarg0, c_rarg1, ...)
  // Unix:  rdi, rsi, rdx, rcx, r8, r9 (c_rarg0, c_rarg1, ...)
  const Register out     = rdi;
  const Register in      = rsi;
  const Register offset  = r11;
  const Register len     = rcx;
  const Register k       = r8;

  // Next registers will be saved on stack in mul_add().
  const Register tmp1  = r12;
  const Register tmp2  = r13;
  const Register tmp3  = r14;
  const Register tmp4  = r15;
  const Register tmp5  = rbx;

  BLOCK_COMMENT("Entry:");
  __ enter(); // required for proper stackwalking of RuntimeStub frame

  setup_arg_regs(4); // out => rdi, in => rsi, offset => rdx
                     // len => rcx, k => r8
                     // r9 and r10 may be used to save non-volatile registers
#ifdef _WIN64
  // last argument is on stack on Win64
  __ movl(k, Address(rsp, 6 * wordSize));
#endif
  __ movptr(r11, rdx);  // move offset in rdx to offset(r11)
  __ mul_add(out, in, offset, len, k, tmp1, tmp2, tmp3, tmp4, tmp5, rdx, rax);

  restore_arg_regs();

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

address StubGenerator::generate_bigIntegerRightShift() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "bigIntegerRightShiftWorker");
  address start = __ pc();

  Label Shift512Loop, ShiftTwo, ShiftTwoLoop, ShiftOne, Exit;
  // For Unix, the arguments are as follows: rdi, rsi, rdx, rcx, r8.
  const Register newArr = rdi;
  const Register oldArr = rsi;
  const Register newIdx = rdx;
  const Register shiftCount = rcx;  // It was intentional to have shiftCount in rcx since it is used implicitly for shift.
  const Register totalNumIter = r8;

  // For windows, we use r9 and r10 as temps to save rdi and rsi. Thus we cannot allocate them for our temps.
  // For everything else, we prefer using r9 and r10 since we do not have to save them before use.
  const Register tmp1 = r11;                    // Caller save.
  const Register tmp2 = rax;                    // Caller save.
  const Register tmp3 = WIN64_ONLY(r12) NOT_WIN64(r9);   // Windows: Callee save. Linux: Caller save.
  const Register tmp4 = WIN64_ONLY(r13) NOT_WIN64(r10);  // Windows: Callee save. Linux: Caller save.
  const Register tmp5 = r14;                    // Callee save.
  const Register tmp6 = r15;

  const XMMRegister x0 = xmm0;
  const XMMRegister x1 = xmm1;
  const XMMRegister x2 = xmm2;

  BLOCK_COMMENT("Entry:");
  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
  setup_arg_regs(4);
  // For windows, since last argument is on stack, we need to move it to the appropriate register.
  __ movl(totalNumIter, Address(rsp, 6 * wordSize));
  // Save callee save registers.
  __ push(tmp3);
  __ push(tmp4);
#endif
  __ push(tmp5);

  // Rename temps used throughout the code.
  const Register idx = tmp1;
  const Register nIdx = tmp2;

  __ xorl(idx, idx);

  // Start right shift from end of the array.
  // For example, if #iteration = 4 and newIdx = 1
  // then dest[4] = src[4] >> shiftCount  | src[3] <<< (shiftCount - 32)
  // if #iteration = 4 and newIdx = 0
  // then dest[3] = src[4] >> shiftCount  | src[3] <<< (shiftCount - 32)
  __ movl(idx, totalNumIter);
  __ movl(nIdx, idx);
  __ addl(nIdx, newIdx);

  // If vectorization is enabled, check if the number of iterations is at least 64
  // If not, then go to ShifTwo processing 2 iterations
  if (VM_Version::supports_avx512_vbmi2()) {
    __ cmpptr(totalNumIter, (AVX3Threshold/64));
    __ jcc(Assembler::less, ShiftTwo);

    if (AVX3Threshold < 16 * 64) {
      __ cmpl(totalNumIter, 16);
      __ jcc(Assembler::less, ShiftTwo);
    }
    __ evpbroadcastd(x0, shiftCount, Assembler::AVX_512bit);
    __ subl(idx, 16);
    __ subl(nIdx, 16);
    __ BIND(Shift512Loop);
    __ evmovdqul(x2, Address(oldArr, idx, Address::times_4, 4), Assembler::AVX_512bit);
    __ evmovdqul(x1, Address(oldArr, idx, Address::times_4), Assembler::AVX_512bit);
    __ vpshrdvd(x2, x1, x0, Assembler::AVX_512bit);
    __ evmovdqul(Address(newArr, nIdx, Address::times_4), x2, Assembler::AVX_512bit);
    __ subl(nIdx, 16);
    __ subl(idx, 16);
    __ jcc(Assembler::greaterEqual, Shift512Loop);
    __ addl(idx, 16);
    __ addl(nIdx, 16);
  }
  __ BIND(ShiftTwo);
  __ cmpl(idx, 2);
  __ jcc(Assembler::less, ShiftOne);
  __ subl(idx, 2);
  __ subl(nIdx, 2);
  __ BIND(ShiftTwoLoop);
  __ movl(tmp5, Address(oldArr, idx, Address::times_4, 8));
  __ movl(tmp4, Address(oldArr, idx, Address::times_4, 4));
  __ movl(tmp3, Address(oldArr, idx, Address::times_4));
  __ shrdl(tmp5, tmp4);
  __ shrdl(tmp4, tmp3);
  __ movl(Address(newArr, nIdx, Address::times_4, 4), tmp5);
  __ movl(Address(newArr, nIdx, Address::times_4), tmp4);
  __ subl(nIdx, 2);
  __ subl(idx, 2);
  __ jcc(Assembler::greaterEqual, ShiftTwoLoop);
  __ addl(idx, 2);
  __ addl(nIdx, 2);

  // Do the last iteration
  __ BIND(ShiftOne);
  __ cmpl(idx, 1);
  __ jcc(Assembler::less, Exit);
  __ subl(idx, 1);
  __ subl(nIdx, 1);
  __ movl(tmp4, Address(oldArr, idx, Address::times_4, 4));
  __ movl(tmp3, Address(oldArr, idx, Address::times_4));
  __ shrdl(tmp4, tmp3);
  __ movl(Address(newArr, nIdx, Address::times_4), tmp4);
  __ BIND(Exit);
  __ vzeroupper();
  // Restore callee save registers.
  __ pop(tmp5);
#ifdef _WIN64
  __ pop(tmp4);
  __ pop(tmp3);
  restore_arg_regs();
#endif
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

 /**
 *  Arguments:
 *
 *  Input:
 *    c_rarg0   - newArr address
 *    c_rarg1   - oldArr address
 *    c_rarg2   - newIdx
 *    c_rarg3   - shiftCount
 * not Win64
 *    c_rarg4   - numIter
 * Win64
 *    rsp40    - numIter
 */
address StubGenerator::generate_bigIntegerLeftShift() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this,  "StubRoutines", "bigIntegerLeftShiftWorker");
  address start = __ pc();

  Label Shift512Loop, ShiftTwo, ShiftTwoLoop, ShiftOne, Exit;
  // For Unix, the arguments are as follows: rdi, rsi, rdx, rcx, r8.
  const Register newArr = rdi;
  const Register oldArr = rsi;
  const Register newIdx = rdx;
  const Register shiftCount = rcx;  // It was intentional to have shiftCount in rcx since it is used implicitly for shift.
  const Register totalNumIter = r8;
  // For windows, we use r9 and r10 as temps to save rdi and rsi. Thus we cannot allocate them for our temps.
  // For everything else, we prefer using r9 and r10 since we do not have to save them before use.
  const Register tmp1 = r11;                    // Caller save.
  const Register tmp2 = rax;                    // Caller save.
  const Register tmp3 = WIN64_ONLY(r12) NOT_WIN64(r9);   // Windows: Callee save. Linux: Caller save.
  const Register tmp4 = WIN64_ONLY(r13) NOT_WIN64(r10);  // Windows: Callee save. Linux: Caller save.
  const Register tmp5 = r14;                    // Callee save.

  const XMMRegister x0 = xmm0;
  const XMMRegister x1 = xmm1;
  const XMMRegister x2 = xmm2;
  BLOCK_COMMENT("Entry:");
  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
  setup_arg_regs(4);
  // For windows, since last argument is on stack, we need to move it to the appropriate register.
  __ movl(totalNumIter, Address(rsp, 6 * wordSize));
  // Save callee save registers.
  __ push(tmp3);
  __ push(tmp4);
#endif
  __ push(tmp5);

  // Rename temps used throughout the code
  const Register idx = tmp1;
  const Register numIterTmp = tmp2;

  // Start idx from zero.
  __ xorl(idx, idx);
  // Compute interior pointer for new array. We do this so that we can use same index for both old and new arrays.
  __ lea(newArr, Address(newArr, newIdx, Address::times_4));
  __ movl(numIterTmp, totalNumIter);

  // If vectorization is enabled, check if the number of iterations is at least 64
  // If not, then go to ShiftTwo shifting two numbers at a time
  if (VM_Version::supports_avx512_vbmi2()) {
    __ cmpl(totalNumIter, (AVX3Threshold/64));
    __ jcc(Assembler::less, ShiftTwo);

    if (AVX3Threshold < 16 * 64) {
      __ cmpl(totalNumIter, 16);
      __ jcc(Assembler::less, ShiftTwo);
    }
    __ evpbroadcastd(x0, shiftCount, Assembler::AVX_512bit);
    __ subl(numIterTmp, 16);
    __ BIND(Shift512Loop);
    __ evmovdqul(x1, Address(oldArr, idx, Address::times_4), Assembler::AVX_512bit);
    __ evmovdqul(x2, Address(oldArr, idx, Address::times_4, 0x4), Assembler::AVX_512bit);
    __ vpshldvd(x1, x2, x0, Assembler::AVX_512bit);
    __ evmovdqul(Address(newArr, idx, Address::times_4), x1, Assembler::AVX_512bit);
    __ addl(idx, 16);
    __ subl(numIterTmp, 16);
    __ jcc(Assembler::greaterEqual, Shift512Loop);
    __ addl(numIterTmp, 16);
  }
  __ BIND(ShiftTwo);
  __ cmpl(totalNumIter, 1);
  __ jcc(Assembler::less, Exit);
  __ movl(tmp3, Address(oldArr, idx, Address::times_4));
  __ subl(numIterTmp, 2);
  __ jcc(Assembler::less, ShiftOne);

  __ BIND(ShiftTwoLoop);
  __ movl(tmp4, Address(oldArr, idx, Address::times_4, 0x4));
  __ movl(tmp5, Address(oldArr, idx, Address::times_4, 0x8));
  __ shldl(tmp3, tmp4);
  __ shldl(tmp4, tmp5);
  __ movl(Address(newArr, idx, Address::times_4), tmp3);
  __ movl(Address(newArr, idx, Address::times_4, 0x4), tmp4);
  __ movl(tmp3, tmp5);
  __ addl(idx, 2);
  __ subl(numIterTmp, 2);
  __ jcc(Assembler::greaterEqual, ShiftTwoLoop);

  // Do the last iteration
  __ BIND(ShiftOne);
  __ addl(numIterTmp, 2);
  __ cmpl(numIterTmp, 1);
  __ jcc(Assembler::less, Exit);
  __ movl(tmp4, Address(oldArr, idx, Address::times_4, 0x4));
  __ shldl(tmp3, tmp4);
  __ movl(Address(newArr, idx, Address::times_4), tmp3);

  __ BIND(Exit);
  __ vzeroupper();
  // Restore callee save registers.
  __ pop(tmp5);
#ifdef _WIN64
  __ pop(tmp4);
  __ pop(tmp3);
  restore_arg_regs();
#endif
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

void StubGenerator::generate_libm_stubs() {
  if (UseLibmIntrinsic && InlineIntrinsics) {
    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_dsin)) {
      StubRoutines::_dsin = generate_libmSin(); // from stubGenerator_x86_64_sin.cpp
    }
    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_dcos)) {
      StubRoutines::_dcos = generate_libmCos(); // from stubGenerator_x86_64_cos.cpp
    }
    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_dtan)) {
      StubRoutines::_dtan = generate_libmTan(); // from stubGenerator_x86_64_tan.cpp
    }
    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_dexp)) {
      StubRoutines::_dexp = generate_libmExp(); // from stubGenerator_x86_64_exp.cpp
    }
    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_dpow)) {
      StubRoutines::_dpow = generate_libmPow(); // from stubGenerator_x86_64_pow.cpp
    }
    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_dlog)) {
      StubRoutines::_dlog = generate_libmLog(); // from stubGenerator_x86_64_log.cpp
    }
    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_dlog10)) {
      StubRoutines::_dlog10 = generate_libmLog10(); // from stubGenerator_x86_64_log.cpp
    }
  }
}

/**
*  Arguments:
*
*  Input:
*    c_rarg0   - float16  jshort
*
*  Output:
*       xmm0   - float
*/
address StubGenerator::generate_float16ToFloat() {
  StubCodeMark mark(this, "StubRoutines", "float16ToFloat");

  address start = __ pc();

  BLOCK_COMMENT("Entry:");
  // No need for RuntimeStub frame since it is called only during JIT compilation

  // Load value into xmm0 and convert
  __ flt16_to_flt(xmm0, c_rarg0);

  __ ret(0);

  return start;
}

/**
*  Arguments:
*
*  Input:
*       xmm0   - float
*
*  Output:
*        rax   - float16  jshort
*/
address StubGenerator::generate_floatToFloat16() {
  StubCodeMark mark(this, "StubRoutines", "floatToFloat16");

  address start = __ pc();

  BLOCK_COMMENT("Entry:");
  // No need for RuntimeStub frame since it is called only during JIT compilation

  // Convert and put result into rax
  __ flt_to_flt16(rax, xmm0, xmm1);

  __ ret(0);

  return start;
}

address StubGenerator::generate_cont_thaw(const char* label, Continuation::thaw_kind kind) {
  if (!Continuations::enabled()) return nullptr;

  bool return_barrier = Continuation::is_thaw_return_barrier(kind);
  bool return_barrier_exception = Continuation::is_thaw_return_barrier_exception(kind);

  StubCodeMark mark(this, "StubRoutines", label);
  address start = __ pc();

  // TODO: Handle Valhalla return types. May require generating different return barriers.

  if (!return_barrier) {
    // Pop return address. If we don't do this, we get a drift,
    // where the bottom-most frozen frame continuously grows.
    __ pop(c_rarg3);
  } else {
    __ movptr(rsp, Address(r15_thread, JavaThread::cont_entry_offset()));
  }

#ifdef ASSERT
  {
    Label L_good_sp;
    __ cmpptr(rsp, Address(r15_thread, JavaThread::cont_entry_offset()));
    __ jcc(Assembler::equal, L_good_sp);
    __ stop("Incorrect rsp at thaw entry");
    __ BIND(L_good_sp);
  }
#endif // ASSERT

  if (return_barrier) {
    // Preserve possible return value from a method returning to the return barrier.
    __ push(rax);
    __ push_d(xmm0);
  }

  __ movptr(c_rarg0, r15_thread);
  __ movptr(c_rarg1, (return_barrier ? 1 : 0));
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, Continuation::prepare_thaw), 2);
  __ movptr(rbx, rax);

  if (return_barrier) {
    // Restore return value from a method returning to the return barrier.
    // No safepoint in the call to thaw, so even an oop return value should be OK.
    __ pop_d(xmm0);
    __ pop(rax);
  }

#ifdef ASSERT
  {
    Label L_good_sp;
    __ cmpptr(rsp, Address(r15_thread, JavaThread::cont_entry_offset()));
    __ jcc(Assembler::equal, L_good_sp);
    __ stop("Incorrect rsp after prepare thaw");
    __ BIND(L_good_sp);
  }
#endif // ASSERT

  // rbx contains the size of the frames to thaw, 0 if overflow or no more frames
  Label L_thaw_success;
  __ testptr(rbx, rbx);
  __ jccb(Assembler::notZero, L_thaw_success);
  __ jump(ExternalAddress(StubRoutines::throw_StackOverflowError_entry()));
  __ bind(L_thaw_success);

  // Make room for the thawed frames and align the stack.
  __ subptr(rsp, rbx);
  __ andptr(rsp, -StackAlignmentInBytes);

  if (return_barrier) {
    // Preserve possible return value from a method returning to the return barrier. (Again.)
    __ push(rax);
    __ push_d(xmm0);
  }

  // If we want, we can templatize thaw by kind, and have three different entries.
  __ movptr(c_rarg0, r15_thread);
  __ movptr(c_rarg1, kind);
  __ call_VM_leaf(Continuation::thaw_entry(), 2);
  __ movptr(rbx, rax);

  if (return_barrier) {
    // Restore return value from a method returning to the return barrier. (Again.)
    // No safepoint in the call to thaw, so even an oop return value should be OK.
    __ pop_d(xmm0);
    __ pop(rax);
  } else {
    // Return 0 (success) from doYield.
    __ xorptr(rax, rax);
  }

  // After thawing, rbx is the SP of the yielding frame.
  // Move there, and then to saved RBP slot.
  __ movptr(rsp, rbx);
  __ subptr(rsp, 2*wordSize);

  if (return_barrier_exception) {
    __ movptr(c_rarg0, r15_thread);
    __ movptr(c_rarg1, Address(rsp, wordSize)); // return address

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
    __ pop(rbp); // pop out RBP here too
    __ pop(rdx);
    __ jmp(rbx);
  } else {
    // We are "returning" into the topmost thawed frame; see Thaw::push_return_frame
    __ pop(rbp);
    __ ret(0);
  }

  return start;
}

address StubGenerator::generate_cont_thaw() {
  return generate_cont_thaw("Cont thaw", Continuation::thaw_top);
}

// TODO: will probably need multiple return barriers depending on return type

address StubGenerator::generate_cont_returnBarrier() {
  return generate_cont_thaw("Cont thaw return barrier", Continuation::thaw_return_barrier);
}

address StubGenerator::generate_cont_returnBarrier_exception() {
  return generate_cont_thaw("Cont thaw return barrier exception", Continuation::thaw_return_barrier_exception);
}

#if INCLUDE_JFR

// For c2: c_rarg0 is junk, call to runtime to write a checkpoint.
// It returns a jobject handle to the event writer.
// The handle is dereferenced and the return value is the event writer oop.
RuntimeStub* StubGenerator::generate_jfr_write_checkpoint() {
  enum layout {
    rbp_off,
    rbpH_off,
    return_off,
    return_off2,
    framesize // inclusive of return address
  };

  CodeBuffer code("jfr_write_checkpoint", 1024, 64);
  MacroAssembler* _masm = new MacroAssembler(&code);
  address start = __ pc();

  __ enter();
  address the_pc = __ pc();

  int frame_complete = the_pc - start;

  __ set_last_Java_frame(rsp, rbp, the_pc, rscratch1);
  __ movptr(c_rarg0, r15_thread);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, JfrIntrinsicSupport::write_checkpoint), 1);
  __ reset_last_Java_frame(true);

  // rax is jobject handle result, unpack and process it through a barrier.
  __ resolve_global_jobject(rax, r15_thread, c_rarg0);

  __ leave();
  __ ret(0);

  OopMapSet* oop_maps = new OopMapSet();
  OopMap* map = new OopMap(framesize, 1);
  oop_maps->add_gc_map(frame_complete, map);

  RuntimeStub* stub =
    RuntimeStub::new_runtime_stub(code.name(),
                                  &code,
                                  frame_complete,
                                  (framesize >> (LogBytesPerWord - LogBytesPerInt)),
                                  oop_maps,
                                  false);
  return stub;
}

// For c2: call to return a leased buffer.
RuntimeStub* StubGenerator::generate_jfr_return_lease() {
  enum layout {
    rbp_off,
    rbpH_off,
    return_off,
    return_off2,
    framesize // inclusive of return address
  };

  CodeBuffer code("jfr_return_lease", 1024, 64);
  MacroAssembler* _masm = new MacroAssembler(&code);
  address start = __ pc();

  __ enter();
  address the_pc = __ pc();

  int frame_complete = the_pc - start;

  __ set_last_Java_frame(rsp, rbp, the_pc, rscratch2);
  __ movptr(c_rarg0, r15_thread);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, JfrIntrinsicSupport::return_lease), 1);
  __ reset_last_Java_frame(true);

  __ leave();
  __ ret(0);

  OopMapSet* oop_maps = new OopMapSet();
  OopMap* map = new OopMap(framesize, 1);
  oop_maps->add_gc_map(frame_complete, map);

  RuntimeStub* stub =
    RuntimeStub::new_runtime_stub(code.name(),
                                  &code,
                                  frame_complete,
                                  (framesize >> (LogBytesPerWord - LogBytesPerInt)),
                                  oop_maps,
                                  false);
  return stub;
}

#endif // INCLUDE_JFR

// Continuation point for throwing of implicit exceptions that are
// not handled in the current activation. Fabricates an exception
// oop and initiates normal exception dispatching in this
// frame. Since we need to preserve callee-saved values (currently
// only for C2, but done for C1 as well) we need a callee-saved oop
// map and therefore have to make these stubs into RuntimeStubs
// rather than BufferBlobs.  If the compiler needs all registers to
// be preserved between the fault point and the exception handler
// then it must assume responsibility for that in
// AbstractCompiler::continuation_for_implicit_null_exception or
// continuation_for_implicit_division_by_zero_exception. All other
// implicit exceptions (e.g., NullPointerException or
// AbstractMethodError on entry) are either at call sites or
// otherwise assume that stack unwinding will be initiated, so
// caller saved registers were assumed volatile in the compiler.
address StubGenerator::generate_throw_exception(const char* name,
                                                address runtime_entry,
                                                Register arg1,
                                                Register arg2) {
  // Information about frame layout at time of blocking runtime call.
  // Note that we only have to preserve callee-saved registers since
  // the compilers are responsible for supplying a continuation point
  // if they expect all registers to be preserved.
  enum layout {
    rbp_off = frame::arg_reg_save_area_bytes/BytesPerInt,
    rbp_off2,
    return_off,
    return_off2,
    framesize // inclusive of return address
  };

  int insts_size = 512;
  int locs_size  = 64;

  CodeBuffer code(name, insts_size, locs_size);
  OopMapSet* oop_maps  = new OopMapSet();
  MacroAssembler* _masm = new MacroAssembler(&code);

  address start = __ pc();

  // This is an inlined and slightly modified version of call_VM
  // which has the ability to fetch the return PC out of
  // thread-local storage and also sets up last_Java_sp slightly
  // differently than the real call_VM

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  assert(is_even(framesize/2), "sp not 16-byte aligned");

  // return address and rbp are already in place
  __ subptr(rsp, (framesize-4) << LogBytesPerInt); // prolog

  int frame_complete = __ pc() - start;

  // Set up last_Java_sp and last_Java_fp
  address the_pc = __ pc();
  __ set_last_Java_frame(rsp, rbp, the_pc, rscratch1);
  __ andptr(rsp, -(StackAlignmentInBytes));    // Align stack

  // Call runtime
  if (arg1 != noreg) {
    assert(arg2 != c_rarg1, "clobbered");
    __ movptr(c_rarg1, arg1);
  }
  if (arg2 != noreg) {
    __ movptr(c_rarg2, arg2);
  }
  __ movptr(c_rarg0, r15_thread);
  BLOCK_COMMENT("call runtime_entry");
  __ call(RuntimeAddress(runtime_entry));

  // Generate oop map
  OopMap* map = new OopMap(framesize, 0);

  oop_maps->add_gc_map(the_pc - start, map);

  __ reset_last_Java_frame(true);

  __ leave(); // required for proper stackwalking of RuntimeStub frame

  // check for pending exceptions
#ifdef ASSERT
  Label L;
  __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()), NULL_WORD);
  __ jcc(Assembler::notEqual, L);
  __ should_not_reach_here();
  __ bind(L);
#endif // ASSERT
  __ jump(RuntimeAddress(StubRoutines::forward_exception_entry()));


  // codeBlob framesize is in words (not VMRegImpl::slot_size)
  RuntimeStub* stub =
    RuntimeStub::new_runtime_stub(name,
                                  &code,
                                  frame_complete,
                                  (framesize >> (LogBytesPerWord - LogBytesPerInt)),
                                  oop_maps, false);
  return stub->entry_point();
}

void StubGenerator::create_control_words() {
  // Round to nearest, 64-bit mode, exceptions masked
  StubRoutines::x86::_mxcsr_std = 0x1F80;
  // Round to zero, 64-bit mode, exceptions masked
  StubRoutines::x86::_mxcsr_rz = 0x7F80;
}

// Initialization
void StubGenerator::generate_initial_stubs() {
  // Generates all stubs and initializes the entry points

  // This platform-specific settings are needed by generate_call_stub()
  create_control_words();

  // Initialize table for unsafe copy memeory check.
  if (UnsafeCopyMemory::_table == nullptr) {
    UnsafeCopyMemory::create_table(16);
  }

  // entry points that exist in all platforms Note: This is code
  // that could be shared among different platforms - however the
  // benefit seems to be smaller than the disadvantage of having a
  // much more complicated generator structure. See also comment in
  // stubRoutines.hpp.

  StubRoutines::_forward_exception_entry = generate_forward_exception();

  StubRoutines::_call_stub_entry =
    generate_call_stub(StubRoutines::_call_stub_return_address);

  // is referenced by megamorphic call
  StubRoutines::_catch_exception_entry = generate_catch_exception();

  // atomic calls
  StubRoutines::_fence_entry                = generate_orderaccess_fence();

  // platform dependent
  StubRoutines::x86::_get_previous_sp_entry = generate_get_previous_sp();

  StubRoutines::x86::_verify_mxcsr_entry    = generate_verify_mxcsr();

  StubRoutines::x86::_f2i_fixup             = generate_f2i_fixup();
  StubRoutines::x86::_f2l_fixup             = generate_f2l_fixup();
  StubRoutines::x86::_d2i_fixup             = generate_d2i_fixup();
  StubRoutines::x86::_d2l_fixup             = generate_d2l_fixup();

  StubRoutines::x86::_float_sign_mask       = generate_fp_mask("float_sign_mask",  0x7FFFFFFF7FFFFFFF);
  StubRoutines::x86::_float_sign_flip       = generate_fp_mask("float_sign_flip",  0x8000000080000000);
  StubRoutines::x86::_double_sign_mask      = generate_fp_mask("double_sign_mask", 0x7FFFFFFFFFFFFFFF);
  StubRoutines::x86::_double_sign_flip      = generate_fp_mask("double_sign_flip", 0x8000000000000000);

  // Build this early so it's available for the interpreter.
  StubRoutines::_throw_StackOverflowError_entry =
    generate_throw_exception("StackOverflowError throw_exception",
                             CAST_FROM_FN_PTR(address,
                                              SharedRuntime::
                                              throw_StackOverflowError));
  StubRoutines::_throw_delayed_StackOverflowError_entry =
    generate_throw_exception("delayed StackOverflowError throw_exception",
                             CAST_FROM_FN_PTR(address,
                                              SharedRuntime::
                                              throw_delayed_StackOverflowError));
  if (UseCRC32Intrinsics) {
    // set table address before stub generation which use it
    StubRoutines::_crc_table_adr = (address)StubRoutines::x86::_crc_table;
    StubRoutines::_updateBytesCRC32 = generate_updateBytesCRC32();
  }

  if (UseCRC32CIntrinsics) {
    bool supports_clmul = VM_Version::supports_clmul();
    StubRoutines::x86::generate_CRC32C_table(supports_clmul);
    StubRoutines::_crc32c_table_addr = (address)StubRoutines::x86::_crc32c_table;
    StubRoutines::_updateBytesCRC32C = generate_updateBytesCRC32C(supports_clmul);
  }

  if (VM_Version::supports_float16()) {
    // For results consistency both intrinsics should be enabled.
    // vmIntrinsics checks InlineIntrinsics flag, no need to check it here.
    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_float16ToFloat) &&
        vmIntrinsics::is_intrinsic_available(vmIntrinsics::_floatToFloat16)) {
      StubRoutines::_hf2f = generate_float16ToFloat();
      StubRoutines::_f2hf = generate_floatToFloat16();
    }
  }

  generate_libm_stubs();

  StubRoutines::_fmod = generate_libmFmod(); // from stubGenerator_x86_64_fmod.cpp
}

void StubGenerator::generate_continuation_stubs() {
  // Continuation stubs:
  StubRoutines::_cont_thaw          = generate_cont_thaw();
  StubRoutines::_cont_returnBarrier = generate_cont_returnBarrier();
  StubRoutines::_cont_returnBarrierExc = generate_cont_returnBarrier_exception();

  JFR_ONLY(generate_jfr_stubs();)
}

#if INCLUDE_JFR
void StubGenerator::generate_jfr_stubs() {
  StubRoutines::_jfr_write_checkpoint_stub = generate_jfr_write_checkpoint();
  StubRoutines::_jfr_write_checkpoint = StubRoutines::_jfr_write_checkpoint_stub->entry_point();
  StubRoutines::_jfr_return_lease_stub = generate_jfr_return_lease();
  StubRoutines::_jfr_return_lease = StubRoutines::_jfr_return_lease_stub->entry_point();
}
#endif

void StubGenerator::generate_final_stubs() {
  // Generates the rest of stubs and initializes the entry points

  // These entry points require SharedInfo::stack0 to be set up in
  // non-core builds and need to be relocatable, so they each
  // fabricate a RuntimeStub internally.
  StubRoutines::_throw_AbstractMethodError_entry =
    generate_throw_exception("AbstractMethodError throw_exception",
                             CAST_FROM_FN_PTR(address,
                                              SharedRuntime::
                                              throw_AbstractMethodError));

  StubRoutines::_throw_IncompatibleClassChangeError_entry =
    generate_throw_exception("IncompatibleClassChangeError throw_exception",
                             CAST_FROM_FN_PTR(address,
                                              SharedRuntime::
                                              throw_IncompatibleClassChangeError));

  StubRoutines::_throw_NullPointerException_at_call_entry =
    generate_throw_exception("NullPointerException at call throw_exception",
                             CAST_FROM_FN_PTR(address,
                                              SharedRuntime::
                                              throw_NullPointerException_at_call));

  // support for verify_oop (must happen after universe_init)
  if (VerifyOops) {
    StubRoutines::_verify_oop_subroutine_entry = generate_verify_oop();
  }

  // data cache line writeback
  StubRoutines::_data_cache_writeback = generate_data_cache_writeback();
  StubRoutines::_data_cache_writeback_sync = generate_data_cache_writeback_sync();

  // arraycopy stubs used by compilers
  generate_arraycopy_stubs();

  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs_nm != nullptr) {
    StubRoutines::x86::_method_entry_barrier = generate_method_entry_barrier();
  }

  if (UseVectorizedMismatchIntrinsic) {
    StubRoutines::_vectorizedMismatch = generate_vectorizedMismatch();
  }
}

void StubGenerator::generate_compiler_stubs() {
#if COMPILER2_OR_JVMCI

  // Entry points that are C2 compiler specific.

  StubRoutines::x86::_vector_float_sign_mask = generate_vector_mask("vector_float_sign_mask", 0x7FFFFFFF7FFFFFFF);
  StubRoutines::x86::_vector_float_sign_flip = generate_vector_mask("vector_float_sign_flip", 0x8000000080000000);
  StubRoutines::x86::_vector_double_sign_mask = generate_vector_mask("vector_double_sign_mask", 0x7FFFFFFFFFFFFFFF);
  StubRoutines::x86::_vector_double_sign_flip = generate_vector_mask("vector_double_sign_flip", 0x8000000000000000);
  StubRoutines::x86::_vector_all_bits_set = generate_vector_mask("vector_all_bits_set", 0xFFFFFFFFFFFFFFFF);
  StubRoutines::x86::_vector_int_mask_cmp_bits = generate_vector_mask("vector_int_mask_cmp_bits", 0x0000000100000001);
  StubRoutines::x86::_vector_short_to_byte_mask = generate_vector_mask("vector_short_to_byte_mask", 0x00ff00ff00ff00ff);
  StubRoutines::x86::_vector_byte_perm_mask = generate_vector_byte_perm_mask("vector_byte_perm_mask");
  StubRoutines::x86::_vector_int_to_byte_mask = generate_vector_mask("vector_int_to_byte_mask", 0x000000ff000000ff);
  StubRoutines::x86::_vector_int_to_short_mask = generate_vector_mask("vector_int_to_short_mask", 0x0000ffff0000ffff);
  StubRoutines::x86::_vector_32_bit_mask = generate_vector_custom_i32("vector_32_bit_mask", Assembler::AVX_512bit,
                                                                      0xFFFFFFFF, 0, 0, 0);
  StubRoutines::x86::_vector_64_bit_mask = generate_vector_custom_i32("vector_64_bit_mask", Assembler::AVX_512bit,
                                                                      0xFFFFFFFF, 0xFFFFFFFF, 0, 0);
  StubRoutines::x86::_vector_int_shuffle_mask = generate_vector_mask("vector_int_shuffle_mask", 0x0302010003020100);
  StubRoutines::x86::_vector_byte_shuffle_mask = generate_vector_byte_shuffle_mask("vector_byte_shuffle_mask");
  StubRoutines::x86::_vector_short_shuffle_mask = generate_vector_mask("vector_short_shuffle_mask", 0x0100010001000100);
  StubRoutines::x86::_vector_long_shuffle_mask = generate_vector_mask("vector_long_shuffle_mask", 0x0000000100000000);
  StubRoutines::x86::_vector_long_sign_mask = generate_vector_mask("vector_long_sign_mask", 0x8000000000000000);
  StubRoutines::x86::_vector_iota_indices = generate_iota_indices("iota_indices");
  StubRoutines::x86::_vector_count_leading_zeros_lut = generate_count_leading_zeros_lut("count_leading_zeros_lut");
  StubRoutines::x86::_vector_reverse_bit_lut = generate_vector_reverse_bit_lut("reverse_bit_lut");
  StubRoutines::x86::_vector_reverse_byte_perm_mask_long = generate_vector_reverse_byte_perm_mask_long("perm_mask_long");
  StubRoutines::x86::_vector_reverse_byte_perm_mask_int = generate_vector_reverse_byte_perm_mask_int("perm_mask_int");
  StubRoutines::x86::_vector_reverse_byte_perm_mask_short = generate_vector_reverse_byte_perm_mask_short("perm_mask_short");

  if (VM_Version::supports_avx2() && !VM_Version::supports_avx512_vpopcntdq()) {
    // lut implementation influenced by counting 1s algorithm from section 5-1 of Hackers' Delight.
    StubRoutines::x86::_vector_popcount_lut = generate_popcount_avx_lut("popcount_lut");
  }

  generate_aes_stubs();

  generate_ghash_stubs();

  generate_chacha_stubs();

  if (UseAdler32Intrinsics) {
     StubRoutines::_updateBytesAdler32 = generate_updateBytesAdler32();
  }

  if (UsePoly1305Intrinsics) {
    StubRoutines::_poly1305_processBlocks = generate_poly1305_processBlocks();
  }

  if (UseMD5Intrinsics) {
    StubRoutines::_md5_implCompress = generate_md5_implCompress(false, "md5_implCompress");
    StubRoutines::_md5_implCompressMB = generate_md5_implCompress(true, "md5_implCompressMB");
  }

  if (UseSHA1Intrinsics) {
    StubRoutines::x86::_upper_word_mask_addr = generate_upper_word_mask();
    StubRoutines::x86::_shuffle_byte_flip_mask_addr = generate_shuffle_byte_flip_mask();
    StubRoutines::_sha1_implCompress = generate_sha1_implCompress(false, "sha1_implCompress");
    StubRoutines::_sha1_implCompressMB = generate_sha1_implCompress(true, "sha1_implCompressMB");
  }

  if (UseSHA256Intrinsics) {
    StubRoutines::x86::_k256_adr = (address)StubRoutines::x86::_k256;
    char* dst = (char*)StubRoutines::x86::_k256_W;
    char* src = (char*)StubRoutines::x86::_k256;
    for (int ii = 0; ii < 16; ++ii) {
      memcpy(dst + 32 * ii,      src + 16 * ii, 16);
      memcpy(dst + 32 * ii + 16, src + 16 * ii, 16);
    }
    StubRoutines::x86::_k256_W_adr = (address)StubRoutines::x86::_k256_W;
    StubRoutines::x86::_pshuffle_byte_flip_mask_addr = generate_pshuffle_byte_flip_mask();
    StubRoutines::_sha256_implCompress = generate_sha256_implCompress(false, "sha256_implCompress");
    StubRoutines::_sha256_implCompressMB = generate_sha256_implCompress(true, "sha256_implCompressMB");
  }

  if (UseSHA512Intrinsics) {
    StubRoutines::x86::_k512_W_addr = (address)StubRoutines::x86::_k512_W;
    StubRoutines::x86::_pshuffle_byte_flip_mask_addr_sha512 = generate_pshuffle_byte_flip_mask_sha512();
    StubRoutines::_sha512_implCompress = generate_sha512_implCompress(false, "sha512_implCompress");
    StubRoutines::_sha512_implCompressMB = generate_sha512_implCompress(true, "sha512_implCompressMB");
  }

  if (UseBASE64Intrinsics) {
    if(VM_Version::supports_avx2()) {
      StubRoutines::x86::_avx2_shuffle_base64 = base64_avx2_shuffle_addr();
      StubRoutines::x86::_avx2_input_mask_base64 = base64_avx2_input_mask_addr();
      StubRoutines::x86::_avx2_lut_base64 = base64_avx2_lut_addr();
      StubRoutines::x86::_avx2_decode_tables_base64 = base64_AVX2_decode_tables_addr();
      StubRoutines::x86::_avx2_decode_lut_tables_base64 = base64_AVX2_decode_LUT_tables_addr();
    }
    StubRoutines::x86::_encoding_table_base64 = base64_encoding_table_addr();
    if (VM_Version::supports_avx512_vbmi()) {
      StubRoutines::x86::_shuffle_base64 = base64_shuffle_addr();
      StubRoutines::x86::_lookup_lo_base64 = base64_vbmi_lookup_lo_addr();
      StubRoutines::x86::_lookup_hi_base64 = base64_vbmi_lookup_hi_addr();
      StubRoutines::x86::_lookup_lo_base64url = base64_vbmi_lookup_lo_url_addr();
      StubRoutines::x86::_lookup_hi_base64url = base64_vbmi_lookup_hi_url_addr();
      StubRoutines::x86::_pack_vec_base64 = base64_vbmi_pack_vec_addr();
      StubRoutines::x86::_join_0_1_base64 = base64_vbmi_join_0_1_addr();
      StubRoutines::x86::_join_1_2_base64 = base64_vbmi_join_1_2_addr();
      StubRoutines::x86::_join_2_3_base64 = base64_vbmi_join_2_3_addr();
    }
    StubRoutines::x86::_decoding_table_base64 = base64_decoding_table_addr();
    StubRoutines::_base64_encodeBlock = generate_base64_encodeBlock();
    StubRoutines::_base64_decodeBlock = generate_base64_decodeBlock();
  }

#ifdef COMPILER2
  if (UseMultiplyToLenIntrinsic) {
    StubRoutines::_multiplyToLen = generate_multiplyToLen();
  }
  if (UseSquareToLenIntrinsic) {
    StubRoutines::_squareToLen = generate_squareToLen();
  }
  if (UseMulAddIntrinsic) {
    StubRoutines::_mulAdd = generate_mulAdd();
  }
  if (VM_Version::supports_avx512_vbmi2()) {
    StubRoutines::_bigIntegerRightShiftWorker = generate_bigIntegerRightShift();
    StubRoutines::_bigIntegerLeftShiftWorker = generate_bigIntegerLeftShift();
  }
  if (UseMontgomeryMultiplyIntrinsic) {
    StubRoutines::_montgomeryMultiply
      = CAST_FROM_FN_PTR(address, SharedRuntime::montgomery_multiply);
  }
  if (UseMontgomerySquareIntrinsic) {
    StubRoutines::_montgomerySquare
      = CAST_FROM_FN_PTR(address, SharedRuntime::montgomery_square);
  }

  // Get svml stub routine addresses
  void *libjsvml = nullptr;
  char ebuf[1024];
  char dll_name[JVM_MAXPATHLEN];
  if (os::dll_locate_lib(dll_name, sizeof(dll_name), Arguments::get_dll_dir(), "jsvml")) {
    libjsvml = os::dll_load(dll_name, ebuf, sizeof ebuf);
  }
  if (libjsvml != nullptr) {
    // SVML method naming convention
    //   All the methods are named as __jsvml_op<T><N>_ha_<VV>
    //   Where:
    //      ha stands for high accuracy
    //      <T> is optional to indicate float/double
    //              Set to f for vector float operation
    //              Omitted for vector double operation
    //      <N> is the number of elements in the vector
    //              1, 2, 4, 8, 16
    //              e.g. 128 bit float vector has 4 float elements
    //      <VV> indicates the avx/sse level:
    //              z0 is AVX512, l9 is AVX2, e9 is AVX1 and ex is for SSE2
    //      e.g. __jsvml_expf16_ha_z0 is the method for computing 16 element vector float exp using AVX 512 insns
    //           __jsvml_exp8_ha_z0 is the method for computing 8 element vector double exp using AVX 512 insns

    log_info(library)("Loaded library %s, handle " INTPTR_FORMAT, JNI_LIB_PREFIX "jsvml" JNI_LIB_SUFFIX, p2i(libjsvml));
    if (UseAVX > 2) {
      for (int op = 0; op < VectorSupport::NUM_SVML_OP; op++) {
        int vop = VectorSupport::VECTOR_OP_SVML_START + op;
        if ((!VM_Version::supports_avx512dq()) &&
            (vop == VectorSupport::VECTOR_OP_LOG || vop == VectorSupport::VECTOR_OP_LOG10 || vop == VectorSupport::VECTOR_OP_POW)) {
          continue;
        }
        snprintf(ebuf, sizeof(ebuf), "__jsvml_%sf16_ha_z0", VectorSupport::svmlname[op]);
        StubRoutines::_vector_f_math[VectorSupport::VEC_SIZE_512][op] = (address)os::dll_lookup(libjsvml, ebuf);

        snprintf(ebuf, sizeof(ebuf), "__jsvml_%s8_ha_z0", VectorSupport::svmlname[op]);
        StubRoutines::_vector_d_math[VectorSupport::VEC_SIZE_512][op] = (address)os::dll_lookup(libjsvml, ebuf);
      }
    }
    const char* avx_sse_str = (UseAVX >= 2) ? "l9" : ((UseAVX == 1) ? "e9" : "ex");
    for (int op = 0; op < VectorSupport::NUM_SVML_OP; op++) {
      int vop = VectorSupport::VECTOR_OP_SVML_START + op;
      if (vop == VectorSupport::VECTOR_OP_POW) {
        continue;
      }
      snprintf(ebuf, sizeof(ebuf), "__jsvml_%sf4_ha_%s", VectorSupport::svmlname[op], avx_sse_str);
      StubRoutines::_vector_f_math[VectorSupport::VEC_SIZE_64][op] = (address)os::dll_lookup(libjsvml, ebuf);

      snprintf(ebuf, sizeof(ebuf), "__jsvml_%sf4_ha_%s", VectorSupport::svmlname[op], avx_sse_str);
      StubRoutines::_vector_f_math[VectorSupport::VEC_SIZE_128][op] = (address)os::dll_lookup(libjsvml, ebuf);

      snprintf(ebuf, sizeof(ebuf), "__jsvml_%sf8_ha_%s", VectorSupport::svmlname[op], avx_sse_str);
      StubRoutines::_vector_f_math[VectorSupport::VEC_SIZE_256][op] = (address)os::dll_lookup(libjsvml, ebuf);

      snprintf(ebuf, sizeof(ebuf), "__jsvml_%s1_ha_%s", VectorSupport::svmlname[op], avx_sse_str);
      StubRoutines::_vector_d_math[VectorSupport::VEC_SIZE_64][op] = (address)os::dll_lookup(libjsvml, ebuf);

      snprintf(ebuf, sizeof(ebuf), "__jsvml_%s2_ha_%s", VectorSupport::svmlname[op], avx_sse_str);
      StubRoutines::_vector_d_math[VectorSupport::VEC_SIZE_128][op] = (address)os::dll_lookup(libjsvml, ebuf);

      snprintf(ebuf, sizeof(ebuf), "__jsvml_%s4_ha_%s", VectorSupport::svmlname[op], avx_sse_str);
      StubRoutines::_vector_d_math[VectorSupport::VEC_SIZE_256][op] = (address)os::dll_lookup(libjsvml, ebuf);
    }
  }
#endif // COMPILER2
#endif // COMPILER2_OR_JVMCI
}

StubGenerator::StubGenerator(CodeBuffer* code, StubsKind kind) : StubCodeGenerator(code) {
    DEBUG_ONLY( _regs_in_thread = false; )
    switch(kind) {
    case Initial_stubs:
      generate_initial_stubs();
      break;
     case Continuation_stubs:
      generate_continuation_stubs();
      break;
    case Compiler_stubs:
      generate_compiler_stubs();
      break;
    case Final_stubs:
      generate_final_stubs();
      break;
    default:
      fatal("unexpected stubs kind: %d", kind);
      break;
    };
}

void StubGenerator_generate(CodeBuffer* code, StubCodeGenerator::StubsKind kind) {
  StubGenerator g(code, kind);
}

#undef __
