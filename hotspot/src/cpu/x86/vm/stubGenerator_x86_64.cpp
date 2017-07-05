/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "nativeInst_x86.hpp"
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
#include "runtime/thread.inline.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// Declaration and definition of StubGenerator (no .hpp file).
// For a more detailed description of the stub routine structure
// see the comment in stubRoutines.hpp

#define __ _masm->
#define TIMES_OOP (UseCompressedOops ? Address::times_4 : Address::times_8)
#define a__ ((Assembler*)_masm)->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")
const int MXCSR_MASK = 0xFFC0;  // Mask out any pending exceptions

// Stub Code definitions

static address handle_unsafe_access() {
  JavaThread* thread = JavaThread::current();
  address pc = thread->saved_exception_pc();
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
#define inc_counter_np(counter) ((void)0)
#else
  void inc_counter_np_(int& counter) {
    // This can destroy rscratch1 if counter is far from the code cache
    __ incrementl(ExternalAddress((address)&counter));
  }
#define inc_counter_np(counter) \
  BLOCK_COMMENT("inc_counter " #counter); \
  inc_counter_np_(counter);
#endif

  // Call stubs are used to call Java from C
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
  // -60 [ argument word 1      ]
  // -59 [ saved xmm31          ] <--- rsp after_call
  //     [ saved xmm16-xmm30    ] (EVEX enabled, else the space is blank)
  // -27 [ saved xmm15          ]
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
  enum call_stub_layout {
#ifdef _WIN64
    xmm_save_first     = 6,  // save from xmm6
    xmm_save_last      = 31, // to xmm31
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
#else
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
#endif
  };

#ifdef _WIN64
  Address xmm_save(int reg) {
    assert(reg >= xmm_save_first && reg <= xmm_save_last, "XMM register number out of range");
    return Address(rbp, (xmm_save_base - (reg - xmm_save_first) * 2) * wordSize);
  }
#endif

  address generate_call_stub(address& return_address) {
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
    if (UseAVX > 2) {
      __ movl(rbx, 0xffff);
      __ kmovwl(k1, rbx);
    }
#ifdef _WIN64
    int last_reg = 15;
    if (UseAVX > 2) {
      last_reg = 31;
    }
    if (VM_Version::supports_evex()) {
      for (int i = xmm_save_first; i <= last_reg; i++) {
        __ vextractf32x4(xmm_save(i), as_XMMRegister(i), 0);
      }
    } else {
      for (int i = xmm_save_first; i <= last_reg; i++) {
        __ movdqu(xmm_save(i), as_XMMRegister(i));
      }
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
      __ andl(rax, MXCSR_MASK);    // Only check control and mask bits
      ExternalAddress mxcsr_std(StubRoutines::addr_mxcsr_std());
      __ cmp32(rax, mxcsr_std);
      __ jcc(Assembler::equal, skip_ldmx);
      __ ldmxcsr(mxcsr_std);
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
      __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
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

    // restore regs belonging to calling function
#ifdef _WIN64
    // emit the restores for xmm regs
    if (VM_Version::supports_evex()) {
      for (int i = xmm_save_first; i <= last_reg; i++) {
        __ vinsertf32x4(as_XMMRegister(i), as_XMMRegister(i), xmm_save(i), 0);
      }
    } else {
      for (int i = xmm_save_first; i <= last_reg; i++) {
        __ movdqu(as_XMMRegister(i), xmm_save(i));
      }
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

  address generate_catch_exception() {
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
    assert(StubRoutines::_call_stub_return_address != NULL,
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

  address generate_forward_exception() {
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
      __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()), (int32_t) NULL);
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
    __ movptr(Address(r15_thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);

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

  // Support for jint atomic::xchg(jint exchange_value, volatile jint* dest)
  //
  // Arguments :
  //    c_rarg0: exchange_value
  //    c_rarg0: dest
  //
  // Result:
  //    *dest <- ex, return (orig *dest)
  address generate_atomic_xchg() {
    StubCodeMark mark(this, "StubRoutines", "atomic_xchg");
    address start = __ pc();

    __ movl(rax, c_rarg0); // Copy to eax we need a return value anyhow
    __ xchgl(rax, Address(c_rarg1, 0)); // automatic LOCK
    __ ret(0);

    return start;
  }

  // Support for intptr_t atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest)
  //
  // Arguments :
  //    c_rarg0: exchange_value
  //    c_rarg1: dest
  //
  // Result:
  //    *dest <- ex, return (orig *dest)
  address generate_atomic_xchg_ptr() {
    StubCodeMark mark(this, "StubRoutines", "atomic_xchg_ptr");
    address start = __ pc();

    __ movptr(rax, c_rarg0); // Copy to eax we need a return value anyhow
    __ xchgptr(rax, Address(c_rarg1, 0)); // automatic LOCK
    __ ret(0);

    return start;
  }

  // Support for jint atomic::atomic_cmpxchg(jint exchange_value, volatile jint* dest,
  //                                         jint compare_value)
  //
  // Arguments :
  //    c_rarg0: exchange_value
  //    c_rarg1: dest
  //    c_rarg2: compare_value
  //
  // Result:
  //    if ( compare_value == *dest ) {
  //       *dest = exchange_value
  //       return compare_value;
  //    else
  //       return *dest;
  address generate_atomic_cmpxchg() {
    StubCodeMark mark(this, "StubRoutines", "atomic_cmpxchg");
    address start = __ pc();

    __ movl(rax, c_rarg2);
   if ( os::is_MP() ) __ lock();
    __ cmpxchgl(c_rarg0, Address(c_rarg1, 0));
    __ ret(0);

    return start;
  }

  // Support for jbyte atomic::atomic_cmpxchg(jbyte exchange_value, volatile jbyte* dest,
  //                                          jbyte compare_value)
  //
  // Arguments :
  //    c_rarg0: exchange_value
  //    c_rarg1: dest
  //    c_rarg2: compare_value
  //
  // Result:
  //    if ( compare_value == *dest ) {
  //       *dest = exchange_value
  //       return compare_value;
  //    else
  //       return *dest;
  address generate_atomic_cmpxchg_byte() {
    StubCodeMark mark(this, "StubRoutines", "atomic_cmpxchg_byte");
    address start = __ pc();

    __ movsbq(rax, c_rarg2);
   if ( os::is_MP() ) __ lock();
    __ cmpxchgb(c_rarg0, Address(c_rarg1, 0));
    __ ret(0);

    return start;
  }

  // Support for jlong atomic::atomic_cmpxchg(jlong exchange_value,
  //                                          volatile jlong* dest,
  //                                          jlong compare_value)
  // Arguments :
  //    c_rarg0: exchange_value
  //    c_rarg1: dest
  //    c_rarg2: compare_value
  //
  // Result:
  //    if ( compare_value == *dest ) {
  //       *dest = exchange_value
  //       return compare_value;
  //    else
  //       return *dest;
  address generate_atomic_cmpxchg_long() {
    StubCodeMark mark(this, "StubRoutines", "atomic_cmpxchg_long");
    address start = __ pc();

    __ movq(rax, c_rarg2);
   if ( os::is_MP() ) __ lock();
    __ cmpxchgq(c_rarg0, Address(c_rarg1, 0));
    __ ret(0);

    return start;
  }

  // Support for jint atomic::add(jint add_value, volatile jint* dest)
  //
  // Arguments :
  //    c_rarg0: add_value
  //    c_rarg1: dest
  //
  // Result:
  //    *dest += add_value
  //    return *dest;
  address generate_atomic_add() {
    StubCodeMark mark(this, "StubRoutines", "atomic_add");
    address start = __ pc();

    __ movl(rax, c_rarg0);
   if ( os::is_MP() ) __ lock();
    __ xaddl(Address(c_rarg1, 0), c_rarg0);
    __ addl(rax, c_rarg0);
    __ ret(0);

    return start;
  }

  // Support for intptr_t atomic::add_ptr(intptr_t add_value, volatile intptr_t* dest)
  //
  // Arguments :
  //    c_rarg0: add_value
  //    c_rarg1: dest
  //
  // Result:
  //    *dest += add_value
  //    return *dest;
  address generate_atomic_add_ptr() {
    StubCodeMark mark(this, "StubRoutines", "atomic_add_ptr");
    address start = __ pc();

    __ movptr(rax, c_rarg0); // Copy to eax we need a return value anyhow
   if ( os::is_MP() ) __ lock();
    __ xaddptr(Address(c_rarg1, 0), c_rarg0);
    __ addptr(rax, c_rarg0);
    __ ret(0);

    return start;
  }

  // Support for intptr_t OrderAccess::fence()
  //
  // Arguments :
  //
  // Result:
  address generate_orderaccess_fence() {
    StubCodeMark mark(this, "StubRoutines", "orderaccess_fence");
    address start = __ pc();
    __ membar(Assembler::StoreLoad);
    __ ret(0);

    return start;
  }

  // Support for intptr_t get_previous_fp()
  //
  // This routine is used to find the previous frame pointer for the
  // caller (current_frame_guess). This is used as part of debugging
  // ps() is seemingly lost trying to find frames.
  // This code assumes that caller current_frame_guess) has a frame.
  address generate_get_previous_fp() {
    StubCodeMark mark(this, "StubRoutines", "get_previous_fp");
    const Address old_fp(rbp, 0);
    const Address older_fp(rax, 0);
    address start = __ pc();

    __ enter();
    __ movptr(rax, old_fp); // callers fp
    __ movptr(rax, older_fp); // the frame for ps()
    __ pop(rbp);
    __ ret(0);

    return start;
  }

  // Support for intptr_t get_previous_sp()
  //
  // This routine is used to find the previous stack pointer for the
  // caller.
  address generate_get_previous_sp() {
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

  address generate_verify_mxcsr() {
    StubCodeMark mark(this, "StubRoutines", "verify_mxcsr");
    address start = __ pc();

    const Address mxcsr_save(rsp, 0);

    if (CheckJNICalls) {
      Label ok_ret;
      ExternalAddress mxcsr_std(StubRoutines::addr_mxcsr_std());
      __ push(rax);
      __ subptr(rsp, wordSize);      // allocate a temp location
      __ stmxcsr(mxcsr_save);
      __ movl(rax, mxcsr_save);
      __ andl(rax, MXCSR_MASK);    // Only check control and mask bits
      __ cmp32(rax, mxcsr_std);
      __ jcc(Assembler::equal, ok_ret);

      __ warn("MXCSR changed by native JNI code, use -XX:+RestoreMXCSROnJNICall");

      __ ldmxcsr(mxcsr_std);

      __ bind(ok_ret);
      __ addptr(rsp, wordSize);
      __ pop(rax);
    }

    __ ret(0);

    return start;
  }

  address generate_f2i_fixup() {
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

  address generate_f2l_fixup() {
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

  address generate_d2i_fixup() {
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

  address generate_d2l_fixup() {
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

  address generate_fp_mask(const char *stub_name, int64_t mask) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", stub_name);
    address start = __ pc();

    __ emit_data64( mask, relocInfo::none );
    __ emit_data64( mask, relocInfo::none );

    return start;
  }

  // The following routine generates a subroutine to throw an
  // asynchronous UnknownError when an unsafe access gets a fault that
  // could not be reasonably prevented by the programmer.  (Example:
  // SIGBUS/OBJERR.)
  address generate_handler_for_unsafe_access() {
    StubCodeMark mark(this, "StubRoutines", "handler_for_unsafe_access");
    address start = __ pc();

    __ push(0);                       // hole for return address-to-be
    __ pusha();                       // push registers
    Address next_pc(rsp, RegisterImpl::number_of_registers * BytesPerWord);

    // FIXME: this probably needs alignment logic

    __ subptr(rsp, frame::arg_reg_save_area_bytes);
    BLOCK_COMMENT("call handle_unsafe_access");
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, handle_unsafe_access)));
    __ addptr(rsp, frame::arg_reg_save_area_bytes);

    __ movptr(next_pc, rax);          // stuff next address
    __ popa();
    __ ret(0);                        // jump to next address

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
  address generate_verify_oop() {
    StubCodeMark mark(this, "StubRoutines", "verify_oop");
    address start = __ pc();

    Label exit, error;

    __ pushf();
    __ incrementl(ExternalAddress((address) StubRoutines::verify_oop_count_addr()));

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
    __ jcc(Assembler::zero, exit); // if obj is NULL it is OK
    // Check if the oop is in the right area of memory
    __ movptr(c_rarg2, rax);
    __ movptr(c_rarg3, (intptr_t) Universe::verify_oop_mask());
    __ andptr(c_rarg2, c_rarg3);
    __ movptr(c_rarg3, (intptr_t) Universe::verify_oop_bits());
    __ cmpptr(c_rarg2, c_rarg3);
    __ jcc(Assembler::notZero, error);

    // set r12 to heapbase for load_klass()
    __ reinit_heapbase();

    // make sure klass is 'reasonable', which is not zero.
    __ load_klass(rax, rax);  // get klass
    __ testptr(rax, rax);
    __ jcc(Assembler::zero, error); // if klass is NULL it is broken

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
    __ mov(rsp, r12);                               // restore rsp
    __ popa();                                      // pop registers (includes r12)
    __ ret(4 * wordSize);                           // pop caller saved stuff

    return start;
  }

  //
  // Verify that a register contains clean 32-bits positive value
  // (high 32-bits are 0) so it could be used in 64-bits shifts.
  //
  //  Input:
  //    Rint  -  32-bits value
  //    Rtmp  -  scratch
  //
  void assert_clean_int(Register Rint, Register Rtmp) {
#ifdef ASSERT
    Label L;
    assert_different_registers(Rtmp, Rint);
    __ movslq(Rtmp, Rint);
    __ cmpq(Rtmp, Rint);
    __ jcc(Assembler::equal, L);
    __ stop("high 32-bits of int value are not 0");
    __ bind(L);
#endif
  }

  //  Generate overlap test for array copy stubs
  //
  //  Input:
  //     c_rarg0 - from
  //     c_rarg1 - to
  //     c_rarg2 - element count
  //
  //  Output:
  //     rax   - &from[element count - 1]
  //
  void array_overlap_test(address no_overlap_target, Address::ScaleFactor sf) {
    assert(no_overlap_target != NULL, "must be generated");
    array_overlap_test(no_overlap_target, NULL, sf);
  }
  void array_overlap_test(Label& L_no_overlap, Address::ScaleFactor sf) {
    array_overlap_test(NULL, &L_no_overlap, sf);
  }
  void array_overlap_test(address no_overlap_target, Label* NOLp, Address::ScaleFactor sf) {
    const Register from     = c_rarg0;
    const Register to       = c_rarg1;
    const Register count    = c_rarg2;
    const Register end_from = rax;

    __ cmpptr(to, from);
    __ lea(end_from, Address(from, count, sf, 0));
    if (NOLp == NULL) {
      ExternalAddress no_overlap(no_overlap_target);
      __ jump_cc(Assembler::belowEqual, no_overlap);
      __ cmpptr(to, end_from);
      __ jump_cc(Assembler::aboveEqual, no_overlap);
    } else {
      __ jcc(Assembler::belowEqual, (*NOLp));
      __ cmpptr(to, end_from);
      __ jcc(Assembler::aboveEqual, (*NOLp));
    }
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
  void setup_arg_regs(int nargs = 3) {
    const Register saved_rdi = r9;
    const Register saved_rsi = r10;
    assert(nargs == 3 || nargs == 4, "else fix");
#ifdef _WIN64
    assert(c_rarg0 == rcx && c_rarg1 == rdx && c_rarg2 == r8 && c_rarg3 == r9,
           "unexpected argument registers");
    if (nargs >= 4)
      __ mov(rax, r9);  // r9 is also saved_rdi
    __ movptr(saved_rdi, rdi);
    __ movptr(saved_rsi, rsi);
    __ mov(rdi, rcx); // c_rarg0
    __ mov(rsi, rdx); // c_rarg1
    __ mov(rdx, r8);  // c_rarg2
    if (nargs >= 4)
      __ mov(rcx, rax); // c_rarg3 (via rax)
#else
    assert(c_rarg0 == rdi && c_rarg1 == rsi && c_rarg2 == rdx && c_rarg3 == rcx,
           "unexpected argument registers");
#endif
  }

  void restore_arg_regs() {
    const Register saved_rdi = r9;
    const Register saved_rsi = r10;
#ifdef _WIN64
    __ movptr(rdi, saved_rdi);
    __ movptr(rsi, saved_rsi);
#endif
  }

  // Generate code for an array write pre barrier
  //
  //     addr    -  starting address
  //     count   -  element count
  //     tmp     - scratch register
  //
  //     Destroy no registers!
  //
  void  gen_write_ref_array_pre_barrier(Register addr, Register count, bool dest_uninitialized) {
    BarrierSet* bs = Universe::heap()->barrier_set();
    switch (bs->kind()) {
      case BarrierSet::G1SATBCTLogging:
        // With G1, don't generate the call if we statically know that the target in uninitialized
        if (!dest_uninitialized) {
           __ pusha();                      // push registers
           if (count == c_rarg0) {
             if (addr == c_rarg1) {
               // exactly backwards!!
               __ xchgptr(c_rarg1, c_rarg0);
             } else {
               __ movptr(c_rarg1, count);
               __ movptr(c_rarg0, addr);
             }
           } else {
             __ movptr(c_rarg0, addr);
             __ movptr(c_rarg1, count);
           }
           __ call_VM_leaf(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_pre), 2);
           __ popa();
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

  //
  // Generate code for an array write post barrier
  //
  //  Input:
  //     start    - register containing starting address of destination array
  //     count    - elements count
  //     scratch  - scratch register
  //
  //  The input registers are overwritten.
  //
  void  gen_write_ref_array_post_barrier(Register start, Register count, Register scratch) {
    assert_different_registers(start, count, scratch);
    BarrierSet* bs = Universe::heap()->barrier_set();
    switch (bs->kind()) {
      case BarrierSet::G1SATBCTLogging:
        {
          __ pusha();             // push registers (overkill)
          if (c_rarg0 == count) { // On win64 c_rarg0 == rcx
            assert_different_registers(c_rarg1, start);
            __ mov(c_rarg1, count);
            __ mov(c_rarg0, start);
          } else {
            assert_different_registers(c_rarg0, count);
            __ mov(c_rarg0, start);
            __ mov(c_rarg1, count);
          }
          __ call_VM_leaf(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_post), 2);
          __ popa();
        }
        break;
      case BarrierSet::CardTableForRS:
      case BarrierSet::CardTableExtension:
        {
          CardTableModRefBS* ct = barrier_set_cast<CardTableModRefBS>(bs);
          assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");

          Label L_loop;
          const Register end = count;

          __ leaq(end, Address(start, count, TIMES_OOP, 0));  // end == start+count*oop_size
          __ subptr(end, BytesPerHeapOop); // end - 1 to make inclusive
          __ shrptr(start, CardTableModRefBS::card_shift);
          __ shrptr(end,   CardTableModRefBS::card_shift);
          __ subptr(end, start); // end --> cards count

          int64_t disp = (int64_t) ct->byte_map_base;
          __ mov64(scratch, disp);
          __ addptr(start, scratch);
        __ BIND(L_loop);
          __ movb(Address(start, count, Address::times_1), 0);
          __ decrement(count);
          __ jcc(Assembler::greaterEqual, L_loop);
        }
        break;
      default:
        ShouldNotReachHere();

    }
  }


  // Copy big chunks forward
  //
  // Inputs:
  //   end_from     - source arrays end address
  //   end_to       - destination array end address
  //   qword_count  - 64-bits element count, negative
  //   to           - scratch
  //   L_copy_bytes - entry label
  //   L_copy_8_bytes  - exit  label
  //
  void copy_bytes_forward(Register end_from, Register end_to,
                             Register qword_count, Register to,
                             Label& L_copy_bytes, Label& L_copy_8_bytes) {
    DEBUG_ONLY(__ stop("enter at entry label, not here"));
    Label L_loop;
    __ align(OptoLoopAlignment);
    if (UseUnalignedLoadStores) {
      Label L_end;
      if (UseAVX > 2) {
        __ movl(to, 0xffff);
        __ kmovwl(k1, to);
      }
      // Copy 64-bytes per iteration
      __ BIND(L_loop);
      if (UseAVX > 2) {
        __ evmovdqul(xmm0, Address(end_from, qword_count, Address::times_8, -56), Assembler::AVX_512bit);
        __ evmovdqul(Address(end_to, qword_count, Address::times_8, -56), xmm0, Assembler::AVX_512bit);
      } else if (UseAVX == 2) {
        __ vmovdqu(xmm0, Address(end_from, qword_count, Address::times_8, -56));
        __ vmovdqu(Address(end_to, qword_count, Address::times_8, -56), xmm0);
        __ vmovdqu(xmm1, Address(end_from, qword_count, Address::times_8, -24));
        __ vmovdqu(Address(end_to, qword_count, Address::times_8, -24), xmm1);
      } else {
        __ movdqu(xmm0, Address(end_from, qword_count, Address::times_8, -56));
        __ movdqu(Address(end_to, qword_count, Address::times_8, -56), xmm0);
        __ movdqu(xmm1, Address(end_from, qword_count, Address::times_8, -40));
        __ movdqu(Address(end_to, qword_count, Address::times_8, -40), xmm1);
        __ movdqu(xmm2, Address(end_from, qword_count, Address::times_8, -24));
        __ movdqu(Address(end_to, qword_count, Address::times_8, -24), xmm2);
        __ movdqu(xmm3, Address(end_from, qword_count, Address::times_8, - 8));
        __ movdqu(Address(end_to, qword_count, Address::times_8, - 8), xmm3);
      }
      __ BIND(L_copy_bytes);
      __ addptr(qword_count, 8);
      __ jcc(Assembler::lessEqual, L_loop);
      __ subptr(qword_count, 4);  // sub(8) and add(4)
      __ jccb(Assembler::greater, L_end);
      // Copy trailing 32 bytes
      if (UseAVX >= 2) {
        __ vmovdqu(xmm0, Address(end_from, qword_count, Address::times_8, -24));
        __ vmovdqu(Address(end_to, qword_count, Address::times_8, -24), xmm0);
      } else {
        __ movdqu(xmm0, Address(end_from, qword_count, Address::times_8, -24));
        __ movdqu(Address(end_to, qword_count, Address::times_8, -24), xmm0);
        __ movdqu(xmm1, Address(end_from, qword_count, Address::times_8, - 8));
        __ movdqu(Address(end_to, qword_count, Address::times_8, - 8), xmm1);
      }
      __ addptr(qword_count, 4);
      __ BIND(L_end);
      if (UseAVX >= 2) {
        // clean upper bits of YMM registers
        __ vpxor(xmm0, xmm0);
        __ vpxor(xmm1, xmm1);
      }
    } else {
      // Copy 32-bytes per iteration
      __ BIND(L_loop);
      __ movq(to, Address(end_from, qword_count, Address::times_8, -24));
      __ movq(Address(end_to, qword_count, Address::times_8, -24), to);
      __ movq(to, Address(end_from, qword_count, Address::times_8, -16));
      __ movq(Address(end_to, qword_count, Address::times_8, -16), to);
      __ movq(to, Address(end_from, qword_count, Address::times_8, - 8));
      __ movq(Address(end_to, qword_count, Address::times_8, - 8), to);
      __ movq(to, Address(end_from, qword_count, Address::times_8, - 0));
      __ movq(Address(end_to, qword_count, Address::times_8, - 0), to);

      __ BIND(L_copy_bytes);
      __ addptr(qword_count, 4);
      __ jcc(Assembler::lessEqual, L_loop);
    }
    __ subptr(qword_count, 4);
    __ jcc(Assembler::less, L_copy_8_bytes); // Copy trailing qwords
  }

  // Copy big chunks backward
  //
  // Inputs:
  //   from         - source arrays address
  //   dest         - destination array address
  //   qword_count  - 64-bits element count
  //   to           - scratch
  //   L_copy_bytes - entry label
  //   L_copy_8_bytes  - exit  label
  //
  void copy_bytes_backward(Register from, Register dest,
                              Register qword_count, Register to,
                              Label& L_copy_bytes, Label& L_copy_8_bytes) {
    DEBUG_ONLY(__ stop("enter at entry label, not here"));
    Label L_loop;
    __ align(OptoLoopAlignment);
    if (UseUnalignedLoadStores) {
      Label L_end;
      if (UseAVX > 2) {
        __ movl(to, 0xffff);
        __ kmovwl(k1, to);
      }
      // Copy 64-bytes per iteration
      __ BIND(L_loop);
      if (UseAVX > 2) {
        __ evmovdqul(xmm0, Address(from, qword_count, Address::times_8, 0), Assembler::AVX_512bit);
        __ evmovdqul(Address(dest, qword_count, Address::times_8, 0), xmm0, Assembler::AVX_512bit);
      } else if (UseAVX == 2) {
        __ vmovdqu(xmm0, Address(from, qword_count, Address::times_8, 32));
        __ vmovdqu(Address(dest, qword_count, Address::times_8, 32), xmm0);
        __ vmovdqu(xmm1, Address(from, qword_count, Address::times_8,  0));
        __ vmovdqu(Address(dest, qword_count, Address::times_8,  0), xmm1);
      } else {
        __ movdqu(xmm0, Address(from, qword_count, Address::times_8, 48));
        __ movdqu(Address(dest, qword_count, Address::times_8, 48), xmm0);
        __ movdqu(xmm1, Address(from, qword_count, Address::times_8, 32));
        __ movdqu(Address(dest, qword_count, Address::times_8, 32), xmm1);
        __ movdqu(xmm2, Address(from, qword_count, Address::times_8, 16));
        __ movdqu(Address(dest, qword_count, Address::times_8, 16), xmm2);
        __ movdqu(xmm3, Address(from, qword_count, Address::times_8,  0));
        __ movdqu(Address(dest, qword_count, Address::times_8,  0), xmm3);
      }
      __ BIND(L_copy_bytes);
      __ subptr(qword_count, 8);
      __ jcc(Assembler::greaterEqual, L_loop);

      __ addptr(qword_count, 4);  // add(8) and sub(4)
      __ jccb(Assembler::less, L_end);
      // Copy trailing 32 bytes
      if (UseAVX >= 2) {
        __ vmovdqu(xmm0, Address(from, qword_count, Address::times_8, 0));
        __ vmovdqu(Address(dest, qword_count, Address::times_8, 0), xmm0);
      } else {
        __ movdqu(xmm0, Address(from, qword_count, Address::times_8, 16));
        __ movdqu(Address(dest, qword_count, Address::times_8, 16), xmm0);
        __ movdqu(xmm1, Address(from, qword_count, Address::times_8,  0));
        __ movdqu(Address(dest, qword_count, Address::times_8,  0), xmm1);
      }
      __ subptr(qword_count, 4);
      __ BIND(L_end);
      if (UseAVX >= 2) {
        // clean upper bits of YMM registers
        __ vpxor(xmm0, xmm0);
        __ vpxor(xmm1, xmm1);
      }
    } else {
      // Copy 32-bytes per iteration
      __ BIND(L_loop);
      __ movq(to, Address(from, qword_count, Address::times_8, 24));
      __ movq(Address(dest, qword_count, Address::times_8, 24), to);
      __ movq(to, Address(from, qword_count, Address::times_8, 16));
      __ movq(Address(dest, qword_count, Address::times_8, 16), to);
      __ movq(to, Address(from, qword_count, Address::times_8,  8));
      __ movq(Address(dest, qword_count, Address::times_8,  8), to);
      __ movq(to, Address(from, qword_count, Address::times_8,  0));
      __ movq(Address(dest, qword_count, Address::times_8,  0), to);

      __ BIND(L_copy_bytes);
      __ subptr(qword_count, 4);
      __ jcc(Assembler::greaterEqual, L_loop);
    }
    __ addptr(qword_count, 4);
    __ jcc(Assembler::greater, L_copy_8_bytes); // Copy trailing qwords
  }


  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
  //             ignored
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as ssize_t, can be zero
  //
  // If 'from' and/or 'to' are aligned on 4-, 2-, or 1-byte boundaries,
  // we let the hardware handle it.  The one to eight bytes within words,
  // dwords or qwords that span cache line boundaries will still be loaded
  // and stored atomically.
  //
  // Side Effects:
  //   disjoint_byte_copy_entry is set to the no-overlap entry point
  //   used by generate_conjoint_byte_copy().
  //
  address generate_disjoint_byte_copy(bool aligned, address* entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes, L_copy_2_bytes;
    Label L_copy_byte, L_exit;
    const Register from        = rdi;  // source array address
    const Register to          = rsi;  // destination array address
    const Register count       = rdx;  // elements count
    const Register byte_count  = rcx;
    const Register qword_count = count;
    const Register end_from    = from; // source array end address
    const Register end_to      = to;   // destination array end address
    // End pointers are inclusive, and if count is not zero they point
    // to the last unit copied:  end_to[0] := end_from[0]

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
       // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                      // r9 and r10 may be used to save non-volatile registers

    // 'from', 'to' and 'count' are now valid
    __ movptr(byte_count, count);
    __ shrptr(count, 3); // count => qword_count

    // Copy from low to high addresses.  Use 'to' as scratch.
    __ lea(end_from, Address(from, qword_count, Address::times_8, -8));
    __ lea(end_to,   Address(to,   qword_count, Address::times_8, -8));
    __ negptr(qword_count); // make the count negative
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(end_from, qword_count, Address::times_8, 8));
    __ movq(Address(end_to, qword_count, Address::times_8, 8), rax);
    __ increment(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(byte_count, 4);
    __ jccb(Assembler::zero, L_copy_2_bytes);
    __ movl(rax, Address(end_from, 8));
    __ movl(Address(end_to, 8), rax);

    __ addptr(end_from, 4);
    __ addptr(end_to, 4);

    // Check for and copy trailing word
  __ BIND(L_copy_2_bytes);
    __ testl(byte_count, 2);
    __ jccb(Assembler::zero, L_copy_byte);
    __ movw(rax, Address(end_from, 8));
    __ movw(Address(end_to, 8), rax);

    __ addptr(end_from, 2);
    __ addptr(end_to, 2);

    // Check for and copy trailing byte
  __ BIND(L_copy_byte);
    __ testl(byte_count, 1);
    __ jccb(Assembler::zero, L_exit);
    __ movb(rax, Address(end_from, 8));
    __ movb(Address(end_to, 8), rax);

  __ BIND(L_exit);
    restore_arg_regs();
    inc_counter_np(SharedRuntime::_jbyte_array_copy_ctr); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    // Copy in multi-bytes chunks
    copy_bytes_forward(end_from, end_to, qword_count, rax, L_copy_bytes, L_copy_8_bytes);
    __ jmp(L_copy_4_bytes);

    return start;
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
  //             ignored
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as ssize_t, can be zero
  //
  // If 'from' and/or 'to' are aligned on 4-, 2-, or 1-byte boundaries,
  // we let the hardware handle it.  The one to eight bytes within words,
  // dwords or qwords that span cache line boundaries will still be loaded
  // and stored atomically.
  //
  address generate_conjoint_byte_copy(bool aligned, address nooverlap_target,
                                      address* entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes, L_copy_2_bytes;
    const Register from        = rdi;  // source array address
    const Register to          = rsi;  // destination array address
    const Register count       = rdx;  // elements count
    const Register byte_count  = rcx;
    const Register qword_count = count;

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    array_overlap_test(nooverlap_target, Address::times_1);
    setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                      // r9 and r10 may be used to save non-volatile registers

    // 'from', 'to' and 'count' are now valid
    __ movptr(byte_count, count);
    __ shrptr(count, 3);   // count => qword_count

    // Copy from high to low addresses.

    // Check for and copy trailing byte
    __ testl(byte_count, 1);
    __ jcc(Assembler::zero, L_copy_2_bytes);
    __ movb(rax, Address(from, byte_count, Address::times_1, -1));
    __ movb(Address(to, byte_count, Address::times_1, -1), rax);
    __ decrement(byte_count); // Adjust for possible trailing word

    // Check for and copy trailing word
  __ BIND(L_copy_2_bytes);
    __ testl(byte_count, 2);
    __ jcc(Assembler::zero, L_copy_4_bytes);
    __ movw(rax, Address(from, byte_count, Address::times_1, -2));
    __ movw(Address(to, byte_count, Address::times_1, -2), rax);

    // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(byte_count, 4);
    __ jcc(Assembler::zero, L_copy_bytes);
    __ movl(rax, Address(from, qword_count, Address::times_8));
    __ movl(Address(to, qword_count, Address::times_8), rax);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(from, qword_count, Address::times_8, -8));
    __ movq(Address(to, qword_count, Address::times_8, -8), rax);
    __ decrement(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    restore_arg_regs();
    inc_counter_np(SharedRuntime::_jbyte_array_copy_ctr); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    // Copy in multi-bytes chunks
    copy_bytes_backward(from, to, qword_count, rax, L_copy_bytes, L_copy_8_bytes);

    restore_arg_regs();
    inc_counter_np(SharedRuntime::_jbyte_array_copy_ctr); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
  //             ignored
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as ssize_t, can be zero
  //
  // If 'from' and/or 'to' are aligned on 4- or 2-byte boundaries, we
  // let the hardware handle it.  The two or four words within dwords
  // or qwords that span cache line boundaries will still be loaded
  // and stored atomically.
  //
  // Side Effects:
  //   disjoint_short_copy_entry is set to the no-overlap entry point
  //   used by generate_conjoint_short_copy().
  //
  address generate_disjoint_short_copy(bool aligned, address *entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes,L_copy_2_bytes,L_exit;
    const Register from        = rdi;  // source array address
    const Register to          = rsi;  // destination array address
    const Register count       = rdx;  // elements count
    const Register word_count  = rcx;
    const Register qword_count = count;
    const Register end_from    = from; // source array end address
    const Register end_to      = to;   // destination array end address
    // End pointers are inclusive, and if count is not zero they point
    // to the last unit copied:  end_to[0] := end_from[0]

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                      // r9 and r10 may be used to save non-volatile registers

    // 'from', 'to' and 'count' are now valid
    __ movptr(word_count, count);
    __ shrptr(count, 2); // count => qword_count

    // Copy from low to high addresses.  Use 'to' as scratch.
    __ lea(end_from, Address(from, qword_count, Address::times_8, -8));
    __ lea(end_to,   Address(to,   qword_count, Address::times_8, -8));
    __ negptr(qword_count);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(end_from, qword_count, Address::times_8, 8));
    __ movq(Address(end_to, qword_count, Address::times_8, 8), rax);
    __ increment(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    // Original 'dest' is trashed, so we can't use it as a
    // base register for a possible trailing word copy

    // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(word_count, 2);
    __ jccb(Assembler::zero, L_copy_2_bytes);
    __ movl(rax, Address(end_from, 8));
    __ movl(Address(end_to, 8), rax);

    __ addptr(end_from, 4);
    __ addptr(end_to, 4);

    // Check for and copy trailing word
  __ BIND(L_copy_2_bytes);
    __ testl(word_count, 1);
    __ jccb(Assembler::zero, L_exit);
    __ movw(rax, Address(end_from, 8));
    __ movw(Address(end_to, 8), rax);

  __ BIND(L_exit);
    restore_arg_regs();
    inc_counter_np(SharedRuntime::_jshort_array_copy_ctr); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    // Copy in multi-bytes chunks
    copy_bytes_forward(end_from, end_to, qword_count, rax, L_copy_bytes, L_copy_8_bytes);
    __ jmp(L_copy_4_bytes);

    return start;
  }

  address generate_fill(BasicType t, bool aligned, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    BLOCK_COMMENT("Entry:");

    const Register to       = c_rarg0;  // source array address
    const Register value    = c_rarg1;  // value
    const Register count    = c_rarg2;  // elements count

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    __ generate_fill(t, aligned, to, value, count, rax, xmm0);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);
    return start;
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
  //             ignored
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as ssize_t, can be zero
  //
  // If 'from' and/or 'to' are aligned on 4- or 2-byte boundaries, we
  // let the hardware handle it.  The two or four words within dwords
  // or qwords that span cache line boundaries will still be loaded
  // and stored atomically.
  //
  address generate_conjoint_short_copy(bool aligned, address nooverlap_target,
                                       address *entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes;
    const Register from        = rdi;  // source array address
    const Register to          = rsi;  // destination array address
    const Register count       = rdx;  // elements count
    const Register word_count  = rcx;
    const Register qword_count = count;

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    array_overlap_test(nooverlap_target, Address::times_2);
    setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                      // r9 and r10 may be used to save non-volatile registers

    // 'from', 'to' and 'count' are now valid
    __ movptr(word_count, count);
    __ shrptr(count, 2); // count => qword_count

    // Copy from high to low addresses.  Use 'to' as scratch.

    // Check for and copy trailing word
    __ testl(word_count, 1);
    __ jccb(Assembler::zero, L_copy_4_bytes);
    __ movw(rax, Address(from, word_count, Address::times_2, -2));
    __ movw(Address(to, word_count, Address::times_2, -2), rax);

    // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(word_count, 2);
    __ jcc(Assembler::zero, L_copy_bytes);
    __ movl(rax, Address(from, qword_count, Address::times_8));
    __ movl(Address(to, qword_count, Address::times_8), rax);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(from, qword_count, Address::times_8, -8));
    __ movq(Address(to, qword_count, Address::times_8, -8), rax);
    __ decrement(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    restore_arg_regs();
    inc_counter_np(SharedRuntime::_jshort_array_copy_ctr); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    // Copy in multi-bytes chunks
    copy_bytes_backward(from, to, qword_count, rax, L_copy_bytes, L_copy_8_bytes);

    restore_arg_regs();
    inc_counter_np(SharedRuntime::_jshort_array_copy_ctr); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
  //             ignored
  //   is_oop  - true => oop array, so generate store check code
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as ssize_t, can be zero
  //
  // If 'from' and/or 'to' are aligned on 4-byte boundaries, we let
  // the hardware handle it.  The two dwords within qwords that span
  // cache line boundaries will still be loaded and stored atomicly.
  //
  // Side Effects:
  //   disjoint_int_copy_entry is set to the no-overlap entry point
  //   used by generate_conjoint_int_oop_copy().
  //
  address generate_disjoint_int_oop_copy(bool aligned, bool is_oop, address* entry,
                                         const char *name, bool dest_uninitialized = false) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes, L_exit;
    const Register from        = rdi;  // source array address
    const Register to          = rsi;  // destination array address
    const Register count       = rdx;  // elements count
    const Register dword_count = rcx;
    const Register qword_count = count;
    const Register end_from    = from; // source array end address
    const Register end_to      = to;   // destination array end address
    const Register saved_to    = r11;  // saved destination array address
    // End pointers are inclusive, and if count is not zero they point
    // to the last unit copied:  end_to[0] := end_from[0]

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                      // r9 and r10 may be used to save non-volatile registers
    if (is_oop) {
      __ movq(saved_to, to);
      gen_write_ref_array_pre_barrier(to, count, dest_uninitialized);
    }

    // 'from', 'to' and 'count' are now valid
    __ movptr(dword_count, count);
    __ shrptr(count, 1); // count => qword_count

    // Copy from low to high addresses.  Use 'to' as scratch.
    __ lea(end_from, Address(from, qword_count, Address::times_8, -8));
    __ lea(end_to,   Address(to,   qword_count, Address::times_8, -8));
    __ negptr(qword_count);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(end_from, qword_count, Address::times_8, 8));
    __ movq(Address(end_to, qword_count, Address::times_8, 8), rax);
    __ increment(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(dword_count, 1); // Only byte test since the value is 0 or 1
    __ jccb(Assembler::zero, L_exit);
    __ movl(rax, Address(end_from, 8));
    __ movl(Address(end_to, 8), rax);

  __ BIND(L_exit);
    if (is_oop) {
      gen_write_ref_array_post_barrier(saved_to, dword_count, rax);
    }
    restore_arg_regs();
    inc_counter_np(SharedRuntime::_jint_array_copy_ctr); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    // Copy in multi-bytes chunks
    copy_bytes_forward(end_from, end_to, qword_count, rax, L_copy_bytes, L_copy_8_bytes);
    __ jmp(L_copy_4_bytes);

    return start;
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
  //             ignored
  //   is_oop  - true => oop array, so generate store check code
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as ssize_t, can be zero
  //
  // If 'from' and/or 'to' are aligned on 4-byte boundaries, we let
  // the hardware handle it.  The two dwords within qwords that span
  // cache line boundaries will still be loaded and stored atomicly.
  //
  address generate_conjoint_int_oop_copy(bool aligned, bool is_oop, address nooverlap_target,
                                         address *entry, const char *name,
                                         bool dest_uninitialized = false) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_bytes, L_copy_8_bytes, L_copy_2_bytes, L_exit;
    const Register from        = rdi;  // source array address
    const Register to          = rsi;  // destination array address
    const Register count       = rdx;  // elements count
    const Register dword_count = rcx;
    const Register qword_count = count;

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
       // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    array_overlap_test(nooverlap_target, Address::times_4);
    setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                      // r9 and r10 may be used to save non-volatile registers

    if (is_oop) {
      // no registers are destroyed by this call
      gen_write_ref_array_pre_barrier(to, count, dest_uninitialized);
    }

    assert_clean_int(count, rax); // Make sure 'count' is clean int.
    // 'from', 'to' and 'count' are now valid
    __ movptr(dword_count, count);
    __ shrptr(count, 1); // count => qword_count

    // Copy from high to low addresses.  Use 'to' as scratch.

    // Check for and copy trailing dword
    __ testl(dword_count, 1);
    __ jcc(Assembler::zero, L_copy_bytes);
    __ movl(rax, Address(from, dword_count, Address::times_4, -4));
    __ movl(Address(to, dword_count, Address::times_4, -4), rax);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(from, qword_count, Address::times_8, -8));
    __ movq(Address(to, qword_count, Address::times_8, -8), rax);
    __ decrement(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    if (is_oop) {
      __ jmp(L_exit);
    }
    restore_arg_regs();
    inc_counter_np(SharedRuntime::_jint_array_copy_ctr); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    // Copy in multi-bytes chunks
    copy_bytes_backward(from, to, qword_count, rax, L_copy_bytes, L_copy_8_bytes);

  __ BIND(L_exit);
    if (is_oop) {
      gen_write_ref_array_post_barrier(to, dword_count, rax);
    }
    restore_arg_regs();
    inc_counter_np(SharedRuntime::_jint_array_copy_ctr); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord boundary == 8 bytes
  //             ignored
  //   is_oop  - true => oop array, so generate store check code
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as ssize_t, can be zero
  //
 // Side Effects:
  //   disjoint_oop_copy_entry or disjoint_long_copy_entry is set to the
  //   no-overlap entry point used by generate_conjoint_long_oop_copy().
  //
  address generate_disjoint_long_oop_copy(bool aligned, bool is_oop, address *entry,
                                          const char *name, bool dest_uninitialized = false) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_bytes, L_copy_8_bytes, L_exit;
    const Register from        = rdi;  // source array address
    const Register to          = rsi;  // destination array address
    const Register qword_count = rdx;  // elements count
    const Register end_from    = from; // source array end address
    const Register end_to      = rcx;  // destination array end address
    const Register saved_to    = to;
    const Register saved_count = r11;
    // End pointers are inclusive, and if count is not zero they point
    // to the last unit copied:  end_to[0] := end_from[0]

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    // Save no-overlap entry point for generate_conjoint_long_oop_copy()
    assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                      // r9 and r10 may be used to save non-volatile registers
    // 'from', 'to' and 'qword_count' are now valid
    if (is_oop) {
      // Save to and count for store barrier
      __ movptr(saved_count, qword_count);
      // no registers are destroyed by this call
      gen_write_ref_array_pre_barrier(to, qword_count, dest_uninitialized);
    }

    // Copy from low to high addresses.  Use 'to' as scratch.
    __ lea(end_from, Address(from, qword_count, Address::times_8, -8));
    __ lea(end_to,   Address(to,   qword_count, Address::times_8, -8));
    __ negptr(qword_count);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(end_from, qword_count, Address::times_8, 8));
    __ movq(Address(end_to, qword_count, Address::times_8, 8), rax);
    __ increment(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    if (is_oop) {
      __ jmp(L_exit);
    } else {
      restore_arg_regs();
      inc_counter_np(SharedRuntime::_jlong_array_copy_ctr); // Update counter after rscratch1 is free
      __ xorptr(rax, rax); // return 0
      __ leave(); // required for proper stackwalking of RuntimeStub frame
      __ ret(0);
    }

    // Copy in multi-bytes chunks
    copy_bytes_forward(end_from, end_to, qword_count, rax, L_copy_bytes, L_copy_8_bytes);

    if (is_oop) {
    __ BIND(L_exit);
      gen_write_ref_array_post_barrier(saved_to, saved_count, rax);
    }
    restore_arg_regs();
    if (is_oop) {
      inc_counter_np(SharedRuntime::_oop_array_copy_ctr); // Update counter after rscratch1 is free
    } else {
      inc_counter_np(SharedRuntime::_jlong_array_copy_ctr); // Update counter after rscratch1 is free
    }
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord boundary == 8 bytes
  //             ignored
  //   is_oop  - true => oop array, so generate store check code
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as ssize_t, can be zero
  //
  address generate_conjoint_long_oop_copy(bool aligned, bool is_oop,
                                          address nooverlap_target, address *entry,
                                          const char *name, bool dest_uninitialized = false) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_copy_bytes, L_copy_8_bytes, L_exit;
    const Register from        = rdi;  // source array address
    const Register to          = rsi;  // destination array address
    const Register qword_count = rdx;  // elements count
    const Register saved_count = rcx;

    __ enter(); // required for proper stackwalking of RuntimeStub frame
    assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    array_overlap_test(nooverlap_target, Address::times_8);
    setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                      // r9 and r10 may be used to save non-volatile registers
    // 'from', 'to' and 'qword_count' are now valid
    if (is_oop) {
      // Save to and count for store barrier
      __ movptr(saved_count, qword_count);
      // No registers are destroyed by this call
      gen_write_ref_array_pre_barrier(to, saved_count, dest_uninitialized);
    }

    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(from, qword_count, Address::times_8, -8));
    __ movq(Address(to, qword_count, Address::times_8, -8), rax);
    __ decrement(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    if (is_oop) {
      __ jmp(L_exit);
    } else {
      restore_arg_regs();
      inc_counter_np(SharedRuntime::_jlong_array_copy_ctr); // Update counter after rscratch1 is free
      __ xorptr(rax, rax); // return 0
      __ leave(); // required for proper stackwalking of RuntimeStub frame
      __ ret(0);
    }

    // Copy in multi-bytes chunks
    copy_bytes_backward(from, to, qword_count, rax, L_copy_bytes, L_copy_8_bytes);

    if (is_oop) {
    __ BIND(L_exit);
      gen_write_ref_array_post_barrier(to, saved_count, rax);
    }
    restore_arg_regs();
    if (is_oop) {
      inc_counter_np(SharedRuntime::_oop_array_copy_ctr); // Update counter after rscratch1 is free
    } else {
      inc_counter_np(SharedRuntime::_jlong_array_copy_ctr); // Update counter after rscratch1 is free
    }
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;
  }


  // Helper for generating a dynamic type check.
  // Smashes no registers.
  void generate_type_check(Register sub_klass,
                           Register super_check_offset,
                           Register super_klass,
                           Label& L_success) {
    assert_different_registers(sub_klass, super_check_offset, super_klass);

    BLOCK_COMMENT("type_check:");

    Label L_miss;

    __ check_klass_subtype_fast_path(sub_klass, super_klass, noreg,        &L_success, &L_miss, NULL,
                                     super_check_offset);
    __ check_klass_subtype_slow_path(sub_klass, super_klass, noreg, noreg, &L_success, NULL);

    // Fall through on failure!
    __ BIND(L_miss);
  }

  //
  //  Generate checkcasting array copy stub
  //
  //  Input:
  //    c_rarg0   - source array address
  //    c_rarg1   - destination array address
  //    c_rarg2   - element count, treated as ssize_t, can be zero
  //    c_rarg3   - size_t ckoff (super_check_offset)
  // not Win64
  //    c_rarg4   - oop ckval (super_klass)
  // Win64
  //    rsp+40    - oop ckval (super_klass)
  //
  //  Output:
  //    rax ==  0  -  success
  //    rax == -1^K - failure, where K is partial transfer count
  //
  address generate_checkcast_copy(const char *name, address *entry,
                                  bool dest_uninitialized = false) {

    Label L_load_element, L_store_element, L_do_card_marks, L_done;

    // Input registers (after setup_arg_regs)
    const Register from        = rdi;   // source array address
    const Register to          = rsi;   // destination array address
    const Register length      = rdx;   // elements count
    const Register ckoff       = rcx;   // super_check_offset
    const Register ckval       = r8;    // super_klass

    // Registers used as temps (r13, r14 are save-on-entry)
    const Register end_from    = from;  // source array end address
    const Register end_to      = r13;   // destination array end address
    const Register count       = rdx;   // -(count_remaining)
    const Register r14_length  = r14;   // saved copy of length
    // End pointers are inclusive, and if length is not zero they point
    // to the last unit copied:  end_to[0] := end_from[0]

    const Register rax_oop    = rax;    // actual oop copied
    const Register r11_klass  = r11;    // oop._klass

    //---------------------------------------------------------------
    // Assembler stub will be used for this call to arraycopy
    // if the two arrays are subtypes of Object[] but the
    // destination array type is not equal to or a supertype
    // of the source type.  Each element must be separately
    // checked.

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef ASSERT
    // caller guarantees that the arrays really are different
    // otherwise, we would have to make conjoint checks
    { Label L;
      array_overlap_test(L, TIMES_OOP);
      __ stop("checkcast_copy within a single array");
      __ bind(L);
    }
#endif //ASSERT

    setup_arg_regs(4); // from => rdi, to => rsi, length => rdx
                       // ckoff => rcx, ckval => r8
                       // r9 and r10 may be used to save non-volatile registers
#ifdef _WIN64
    // last argument (#4) is on stack on Win64
    __ movptr(ckval, Address(rsp, 6 * wordSize));
#endif

    // Caller of this entry point must set up the argument registers.
    if (entry != NULL) {
      *entry = __ pc();
      BLOCK_COMMENT("Entry:");
    }

    // allocate spill slots for r13, r14
    enum {
      saved_r13_offset,
      saved_r14_offset,
      saved_rbp_offset
    };
    __ subptr(rsp, saved_rbp_offset * wordSize);
    __ movptr(Address(rsp, saved_r13_offset * wordSize), r13);
    __ movptr(Address(rsp, saved_r14_offset * wordSize), r14);

    // check that int operands are properly extended to size_t
    assert_clean_int(length, rax);
    assert_clean_int(ckoff, rax);

#ifdef ASSERT
    BLOCK_COMMENT("assert consistent ckoff/ckval");
    // The ckoff and ckval must be mutually consistent,
    // even though caller generates both.
    { Label L;
      int sco_offset = in_bytes(Klass::super_check_offset_offset());
      __ cmpl(ckoff, Address(ckval, sco_offset));
      __ jcc(Assembler::equal, L);
      __ stop("super_check_offset inconsistent");
      __ bind(L);
    }
#endif //ASSERT

    // Loop-invariant addresses.  They are exclusive end pointers.
    Address end_from_addr(from, length, TIMES_OOP, 0);
    Address   end_to_addr(to,   length, TIMES_OOP, 0);
    // Loop-variant addresses.  They assume post-incremented count < 0.
    Address from_element_addr(end_from, count, TIMES_OOP, 0);
    Address   to_element_addr(end_to,   count, TIMES_OOP, 0);

    gen_write_ref_array_pre_barrier(to, count, dest_uninitialized);

    // Copy from low to high addresses, indexed from the end of each array.
    __ lea(end_from, end_from_addr);
    __ lea(end_to,   end_to_addr);
    __ movptr(r14_length, length);        // save a copy of the length
    assert(length == count, "");          // else fix next line:
    __ negptr(count);                     // negate and test the length
    __ jcc(Assembler::notZero, L_load_element);

    // Empty array:  Nothing to do.
    __ xorptr(rax, rax);                  // return 0 on (trivial) success
    __ jmp(L_done);

    // ======== begin loop ========
    // (Loop is rotated; its entry is L_load_element.)
    // Loop control:
    //   for (count = -count; count != 0; count++)
    // Base pointers src, dst are biased by 8*(count-1),to last element.
    __ align(OptoLoopAlignment);

    __ BIND(L_store_element);
    __ store_heap_oop(to_element_addr, rax_oop);  // store the oop
    __ increment(count);               // increment the count toward zero
    __ jcc(Assembler::zero, L_do_card_marks);

    // ======== loop entry is here ========
    __ BIND(L_load_element);
    __ load_heap_oop(rax_oop, from_element_addr); // load the oop
    __ testptr(rax_oop, rax_oop);
    __ jcc(Assembler::zero, L_store_element);

    __ load_klass(r11_klass, rax_oop);// query the object klass
    generate_type_check(r11_klass, ckoff, ckval, L_store_element);
    // ======== end loop ========

    // It was a real error; we must depend on the caller to finish the job.
    // Register rdx = -1 * number of *remaining* oops, r14 = *total* oops.
    // Emit GC store barriers for the oops we have copied (r14 + rdx),
    // and report their number to the caller.
    assert_different_registers(rax, r14_length, count, to, end_to, rcx, rscratch1);
    Label L_post_barrier;
    __ addptr(r14_length, count);     // K = (original - remaining) oops
    __ movptr(rax, r14_length);       // save the value
    __ notptr(rax);                   // report (-1^K) to caller (does not affect flags)
    __ jccb(Assembler::notZero, L_post_barrier);
    __ jmp(L_done); // K == 0, nothing was copied, skip post barrier

    // Come here on success only.
    __ BIND(L_do_card_marks);
    __ xorptr(rax, rax);              // return 0 on success

    __ BIND(L_post_barrier);
    gen_write_ref_array_post_barrier(to, r14_length, rscratch1);

    // Common exit point (success or failure).
    __ BIND(L_done);
    __ movptr(r13, Address(rsp, saved_r13_offset * wordSize));
    __ movptr(r14, Address(rsp, saved_r14_offset * wordSize));
    restore_arg_regs();
    inc_counter_np(SharedRuntime::_checkcast_array_copy_ctr); // Update counter after rscratch1 is free
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
  //    c_rarg0   - source array address
  //    c_rarg1   - destination array address
  //    c_rarg2   - byte count, treated as ssize_t, can be zero
  //
  // Examines the alignment of the operands and dispatches
  // to a long, int, short, or byte copy loop.
  //
  address generate_unsafe_copy(const char *name,
                               address byte_copy_entry, address short_copy_entry,
                               address int_copy_entry, address long_copy_entry) {

    Label L_long_aligned, L_int_aligned, L_short_aligned;

    // Input registers (before setup_arg_regs)
    const Register from        = c_rarg0;  // source array address
    const Register to          = c_rarg1;  // destination array address
    const Register size        = c_rarg2;  // byte count (size_t)

    // Register used as a temp
    const Register bits        = rax;      // test copy of low bits

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // bump this on entry, not on exit:
    inc_counter_np(SharedRuntime::_unsafe_array_copy_ctr);

    __ mov(bits, from);
    __ orptr(bits, to);
    __ orptr(bits, size);

    __ testb(bits, BytesPerLong-1);
    __ jccb(Assembler::zero, L_long_aligned);

    __ testb(bits, BytesPerInt-1);
    __ jccb(Assembler::zero, L_int_aligned);

    __ testb(bits, BytesPerShort-1);
    __ jump_cc(Assembler::notZero, RuntimeAddress(byte_copy_entry));

    __ BIND(L_short_aligned);
    __ shrptr(size, LogBytesPerShort); // size => short_count
    __ jump(RuntimeAddress(short_copy_entry));

    __ BIND(L_int_aligned);
    __ shrptr(size, LogBytesPerInt); // size => int_count
    __ jump(RuntimeAddress(int_copy_entry));

    __ BIND(L_long_aligned);
    __ shrptr(size, LogBytesPerLong); // size => qword_count
    __ jump(RuntimeAddress(long_copy_entry));

    return start;
  }

  // Perform range checks on the proposed arraycopy.
  // Kills temp, but nothing else.
  // Also, clean the sign bits of src_pos and dst_pos.
  void arraycopy_range_checks(Register src,     // source array oop (c_rarg0)
                              Register src_pos, // source position (c_rarg1)
                              Register dst,     // destination array oo (c_rarg2)
                              Register dst_pos, // destination position (c_rarg3)
                              Register length,
                              Register temp,
                              Label& L_failed) {
    BLOCK_COMMENT("arraycopy_range_checks:");

    //  if (src_pos + length > arrayOop(src)->length())  FAIL;
    __ movl(temp, length);
    __ addl(temp, src_pos);             // src_pos + length
    __ cmpl(temp, Address(src, arrayOopDesc::length_offset_in_bytes()));
    __ jcc(Assembler::above, L_failed);

    //  if (dst_pos + length > arrayOop(dst)->length())  FAIL;
    __ movl(temp, length);
    __ addl(temp, dst_pos);             // dst_pos + length
    __ cmpl(temp, Address(dst, arrayOopDesc::length_offset_in_bytes()));
    __ jcc(Assembler::above, L_failed);

    // Have to clean up high 32-bits of 'src_pos' and 'dst_pos'.
    // Move with sign extension can be used since they are positive.
    __ movslq(src_pos, src_pos);
    __ movslq(dst_pos, dst_pos);

    BLOCK_COMMENT("arraycopy_range_checks done");
  }

  //
  //  Generate generic array copy stubs
  //
  //  Input:
  //    c_rarg0    -  src oop
  //    c_rarg1    -  src_pos (32-bits)
  //    c_rarg2    -  dst oop
  //    c_rarg3    -  dst_pos (32-bits)
  // not Win64
  //    c_rarg4    -  element count (32-bits)
  // Win64
  //    rsp+40     -  element count (32-bits)
  //
  //  Output:
  //    rax ==  0  -  success
  //    rax == -1^K - failure, where K is partial transfer count
  //
  address generate_generic_copy(const char *name,
                                address byte_copy_entry, address short_copy_entry,
                                address int_copy_entry, address oop_copy_entry,
                                address long_copy_entry, address checkcast_copy_entry) {

    Label L_failed, L_failed_0, L_objArray;
    Label L_copy_bytes, L_copy_shorts, L_copy_ints, L_copy_longs;

    // Input registers
    const Register src        = c_rarg0;  // source array oop
    const Register src_pos    = c_rarg1;  // source position
    const Register dst        = c_rarg2;  // destination array oop
    const Register dst_pos    = c_rarg3;  // destination position
#ifndef _WIN64
    const Register length     = c_rarg4;
#else
    const Address  length(rsp, 6 * wordSize);  // elements count is on stack on Win64
#endif

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

    // bump this on entry, not on exit:
    inc_counter_np(SharedRuntime::_generic_array_copy_ctr);

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

    //  if (src == NULL) return -1;
    __ testptr(src, src);         // src oop
    size_t j1off = __ offset();
    __ jccb(Assembler::zero, L_failed_0);

    //  if (src_pos < 0) return -1;
    __ testl(src_pos, src_pos); // src_pos (32-bits)
    __ jccb(Assembler::negative, L_failed_0);

    //  if (dst == NULL) return -1;
    __ testptr(dst, dst);         // dst oop
    __ jccb(Assembler::zero, L_failed_0);

    //  if (dst_pos < 0) return -1;
    __ testl(dst_pos, dst_pos); // dst_pos (32-bits)
    size_t j4off = __ offset();
    __ jccb(Assembler::negative, L_failed_0);

    // The first four tests are very dense code,
    // but not quite dense enough to put four
    // jumps in a 16-byte instruction fetch buffer.
    // That's good, because some branch predicters
    // do not like jumps so close together.
    // Make sure of this.
    guarantee(((j1off ^ j4off) & ~15) != 0, "I$ line of 1st & 4th jumps");

    // registers used as temp
    const Register r11_length    = r11; // elements count to copy
    const Register r10_src_klass = r10; // array klass

    //  if (length < 0) return -1;
    __ movl(r11_length, length);        // length (elements count, 32-bits value)
    __ testl(r11_length, r11_length);
    __ jccb(Assembler::negative, L_failed_0);

    __ load_klass(r10_src_klass, src);
#ifdef ASSERT
    //  assert(src->klass() != NULL);
    {
      BLOCK_COMMENT("assert klasses not null {");
      Label L1, L2;
      __ testptr(r10_src_klass, r10_src_klass);
      __ jcc(Assembler::notZero, L2);   // it is broken if klass is NULL
      __ bind(L1);
      __ stop("broken null klass");
      __ bind(L2);
      __ load_klass(rax, dst);
      __ cmpq(rax, 0);
      __ jcc(Assembler::equal, L1);     // this would be broken also
      BLOCK_COMMENT("} assert klasses not null done");
    }
#endif

    // Load layout helper (32-bits)
    //
    //  |array_tag|     | header_size | element_type |     |log2_element_size|
    // 32        30    24            16              8     2                 0
    //
    //   array_tag: typeArray = 0x3, objArray = 0x2, non-array = 0x0
    //

    const int lh_offset = in_bytes(Klass::layout_helper_offset());

    // Handle objArrays completely differently...
    const jint objArray_lh = Klass::array_layout_helper(T_OBJECT);
    __ cmpl(Address(r10_src_klass, lh_offset), objArray_lh);
    __ jcc(Assembler::equal, L_objArray);

    //  if (src->klass() != dst->klass()) return -1;
    __ load_klass(rax, dst);
    __ cmpq(r10_src_klass, rax);
    __ jcc(Assembler::notEqual, L_failed);

    const Register rax_lh = rax;  // layout helper
    __ movl(rax_lh, Address(r10_src_klass, lh_offset));

    //  if (!src->is_Array()) return -1;
    __ cmpl(rax_lh, Klass::_lh_neutral_value);
    __ jcc(Assembler::greaterEqual, L_failed);

    // At this point, it is known to be a typeArray (array_tag 0x3).
#ifdef ASSERT
    {
      BLOCK_COMMENT("assert primitive array {");
      Label L;
      __ cmpl(rax_lh, (Klass::_lh_array_tag_type_value << Klass::_lh_array_tag_shift));
      __ jcc(Assembler::greaterEqual, L);
      __ stop("must be a primitive array");
      __ bind(L);
      BLOCK_COMMENT("} assert primitive array done");
    }
#endif

    arraycopy_range_checks(src, src_pos, dst, dst_pos, r11_length,
                           r10, L_failed);

    // TypeArrayKlass
    //
    // src_addr = (src + array_header_in_bytes()) + (src_pos << log2elemsize);
    // dst_addr = (dst + array_header_in_bytes()) + (dst_pos << log2elemsize);
    //

    const Register r10_offset = r10;    // array offset
    const Register rax_elsize = rax_lh; // element size

    __ movl(r10_offset, rax_lh);
    __ shrl(r10_offset, Klass::_lh_header_size_shift);
    __ andptr(r10_offset, Klass::_lh_header_size_mask);   // array_offset
    __ addptr(src, r10_offset);           // src array offset
    __ addptr(dst, r10_offset);           // dst array offset
    BLOCK_COMMENT("choose copy loop based on element size");
    __ andl(rax_lh, Klass::_lh_log2_element_size_mask); // rax_lh -> rax_elsize

    // next registers should be set before the jump to corresponding stub
    const Register from     = c_rarg0;  // source array address
    const Register to       = c_rarg1;  // destination array address
    const Register count    = c_rarg2;  // elements count

    // 'from', 'to', 'count' registers should be set in such order
    // since they are the same as 'src', 'src_pos', 'dst'.

  __ BIND(L_copy_bytes);
    __ cmpl(rax_elsize, 0);
    __ jccb(Assembler::notEqual, L_copy_shorts);
    __ lea(from, Address(src, src_pos, Address::times_1, 0));// src_addr
    __ lea(to,   Address(dst, dst_pos, Address::times_1, 0));// dst_addr
    __ movl2ptr(count, r11_length); // length
    __ jump(RuntimeAddress(byte_copy_entry));

  __ BIND(L_copy_shorts);
    __ cmpl(rax_elsize, LogBytesPerShort);
    __ jccb(Assembler::notEqual, L_copy_ints);
    __ lea(from, Address(src, src_pos, Address::times_2, 0));// src_addr
    __ lea(to,   Address(dst, dst_pos, Address::times_2, 0));// dst_addr
    __ movl2ptr(count, r11_length); // length
    __ jump(RuntimeAddress(short_copy_entry));

  __ BIND(L_copy_ints);
    __ cmpl(rax_elsize, LogBytesPerInt);
    __ jccb(Assembler::notEqual, L_copy_longs);
    __ lea(from, Address(src, src_pos, Address::times_4, 0));// src_addr
    __ lea(to,   Address(dst, dst_pos, Address::times_4, 0));// dst_addr
    __ movl2ptr(count, r11_length); // length
    __ jump(RuntimeAddress(int_copy_entry));

  __ BIND(L_copy_longs);
#ifdef ASSERT
    {
      BLOCK_COMMENT("assert long copy {");
      Label L;
      __ cmpl(rax_elsize, LogBytesPerLong);
      __ jcc(Assembler::equal, L);
      __ stop("must be long copy, but elsize is wrong");
      __ bind(L);
      BLOCK_COMMENT("} assert long copy done");
    }
#endif
    __ lea(from, Address(src, src_pos, Address::times_8, 0));// src_addr
    __ lea(to,   Address(dst, dst_pos, Address::times_8, 0));// dst_addr
    __ movl2ptr(count, r11_length); // length
    __ jump(RuntimeAddress(long_copy_entry));

    // ObjArrayKlass
  __ BIND(L_objArray);
    // live at this point:  r10_src_klass, r11_length, src[_pos], dst[_pos]

    Label L_plain_copy, L_checkcast_copy;
    //  test array classes for subtyping
    __ load_klass(rax, dst);
    __ cmpq(r10_src_klass, rax); // usual case is exact equality
    __ jcc(Assembler::notEqual, L_checkcast_copy);

    // Identically typed arrays can be copied without element-wise checks.
    arraycopy_range_checks(src, src_pos, dst, dst_pos, r11_length,
                           r10, L_failed);

    __ lea(from, Address(src, src_pos, TIMES_OOP,
                 arrayOopDesc::base_offset_in_bytes(T_OBJECT))); // src_addr
    __ lea(to,   Address(dst, dst_pos, TIMES_OOP,
                 arrayOopDesc::base_offset_in_bytes(T_OBJECT))); // dst_addr
    __ movl2ptr(count, r11_length); // length
  __ BIND(L_plain_copy);
    __ jump(RuntimeAddress(oop_copy_entry));

  __ BIND(L_checkcast_copy);
    // live at this point:  r10_src_klass, r11_length, rax (dst_klass)
    {
      // Before looking at dst.length, make sure dst is also an objArray.
      __ cmpl(Address(rax, lh_offset), objArray_lh);
      __ jcc(Assembler::notEqual, L_failed);

      // It is safe to examine both src.length and dst.length.
      arraycopy_range_checks(src, src_pos, dst, dst_pos, r11_length,
                             rax, L_failed);

      const Register r11_dst_klass = r11;
      __ load_klass(r11_dst_klass, dst); // reload

      // Marshal the base address arguments now, freeing registers.
      __ lea(from, Address(src, src_pos, TIMES_OOP,
                   arrayOopDesc::base_offset_in_bytes(T_OBJECT)));
      __ lea(to,   Address(dst, dst_pos, TIMES_OOP,
                   arrayOopDesc::base_offset_in_bytes(T_OBJECT)));
      __ movl(count, length);           // length (reloaded)
      Register sco_temp = c_rarg3;      // this register is free now
      assert_different_registers(from, to, count, sco_temp,
                                 r11_dst_klass, r10_src_klass);
      assert_clean_int(count, sco_temp);

      // Generate the type check.
      const int sco_offset = in_bytes(Klass::super_check_offset_offset());
      __ movl(sco_temp, Address(r11_dst_klass, sco_offset));
      assert_clean_int(sco_temp, rax);
      generate_type_check(r10_src_klass, sco_temp, r11_dst_klass, L_plain_copy);

      // Fetch destination element klass from the ObjArrayKlass header.
      int ek_offset = in_bytes(ObjArrayKlass::element_klass_offset());
      __ movptr(r11_dst_klass, Address(r11_dst_klass, ek_offset));
      __ movl(  sco_temp,      Address(r11_dst_klass, sco_offset));
      assert_clean_int(sco_temp, rax);

      // the checkcast_copy loop needs two extra arguments:
      assert(c_rarg3 == sco_temp, "#3 already in place");
      // Set up arguments for checkcast_copy_entry.
      setup_arg_regs(4);
      __ movptr(r8, r11_dst_klass);  // dst.klass.element_klass, r8 is c_rarg4 on Linux/Solaris
      __ jump(RuntimeAddress(checkcast_copy_entry));
    }

  __ BIND(L_failed);
    __ xorptr(rax, rax);
    __ notptr(rax); // return -1
    __ leave();   // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

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

    StubRoutines::_jbyte_disjoint_arraycopy  = generate_disjoint_byte_copy(false, &entry,
                                                                           "jbyte_disjoint_arraycopy");
    StubRoutines::_jbyte_arraycopy           = generate_conjoint_byte_copy(false, entry, &entry_jbyte_arraycopy,
                                                                           "jbyte_arraycopy");

    StubRoutines::_jshort_disjoint_arraycopy = generate_disjoint_short_copy(false, &entry,
                                                                            "jshort_disjoint_arraycopy");
    StubRoutines::_jshort_arraycopy          = generate_conjoint_short_copy(false, entry, &entry_jshort_arraycopy,
                                                                            "jshort_arraycopy");

    StubRoutines::_jint_disjoint_arraycopy   = generate_disjoint_int_oop_copy(false, false, &entry,
                                                                              "jint_disjoint_arraycopy");
    StubRoutines::_jint_arraycopy            = generate_conjoint_int_oop_copy(false, false, entry,
                                                                              &entry_jint_arraycopy, "jint_arraycopy");

    StubRoutines::_jlong_disjoint_arraycopy  = generate_disjoint_long_oop_copy(false, false, &entry,
                                                                               "jlong_disjoint_arraycopy");
    StubRoutines::_jlong_arraycopy           = generate_conjoint_long_oop_copy(false, false, entry,
                                                                               &entry_jlong_arraycopy, "jlong_arraycopy");


    if (UseCompressedOops) {
      StubRoutines::_oop_disjoint_arraycopy  = generate_disjoint_int_oop_copy(false, true, &entry,
                                                                              "oop_disjoint_arraycopy");
      StubRoutines::_oop_arraycopy           = generate_conjoint_int_oop_copy(false, true, entry,
                                                                              &entry_oop_arraycopy, "oop_arraycopy");
      StubRoutines::_oop_disjoint_arraycopy_uninit  = generate_disjoint_int_oop_copy(false, true, &entry,
                                                                                     "oop_disjoint_arraycopy_uninit",
                                                                                     /*dest_uninitialized*/true);
      StubRoutines::_oop_arraycopy_uninit           = generate_conjoint_int_oop_copy(false, true, entry,
                                                                                     NULL, "oop_arraycopy_uninit",
                                                                                     /*dest_uninitialized*/true);
    } else {
      StubRoutines::_oop_disjoint_arraycopy  = generate_disjoint_long_oop_copy(false, true, &entry,
                                                                               "oop_disjoint_arraycopy");
      StubRoutines::_oop_arraycopy           = generate_conjoint_long_oop_copy(false, true, entry,
                                                                               &entry_oop_arraycopy, "oop_arraycopy");
      StubRoutines::_oop_disjoint_arraycopy_uninit  = generate_disjoint_long_oop_copy(false, true, &entry,
                                                                                      "oop_disjoint_arraycopy_uninit",
                                                                                      /*dest_uninitialized*/true);
      StubRoutines::_oop_arraycopy_uninit           = generate_conjoint_long_oop_copy(false, true, entry,
                                                                                      NULL, "oop_arraycopy_uninit",
                                                                                      /*dest_uninitialized*/true);
    }

    StubRoutines::_checkcast_arraycopy        = generate_checkcast_copy("checkcast_arraycopy", &entry_checkcast_arraycopy);
    StubRoutines::_checkcast_arraycopy_uninit = generate_checkcast_copy("checkcast_arraycopy_uninit", NULL,
                                                                        /*dest_uninitialized*/true);

    StubRoutines::_unsafe_arraycopy    = generate_unsafe_copy("unsafe_arraycopy",
                                                              entry_jbyte_arraycopy,
                                                              entry_jshort_arraycopy,
                                                              entry_jint_arraycopy,
                                                              entry_jlong_arraycopy);
    StubRoutines::_generic_arraycopy   = generate_generic_copy("generic_arraycopy",
                                                               entry_jbyte_arraycopy,
                                                               entry_jshort_arraycopy,
                                                               entry_jint_arraycopy,
                                                               entry_oop_arraycopy,
                                                               entry_jlong_arraycopy,
                                                               entry_checkcast_arraycopy);

    StubRoutines::_jbyte_fill = generate_fill(T_BYTE, false, "jbyte_fill");
    StubRoutines::_jshort_fill = generate_fill(T_SHORT, false, "jshort_fill");
    StubRoutines::_jint_fill = generate_fill(T_INT, false, "jint_fill");
    StubRoutines::_arrayof_jbyte_fill = generate_fill(T_BYTE, true, "arrayof_jbyte_fill");
    StubRoutines::_arrayof_jshort_fill = generate_fill(T_SHORT, true, "arrayof_jshort_fill");
    StubRoutines::_arrayof_jint_fill = generate_fill(T_INT, true, "arrayof_jint_fill");

    // We don't generate specialized code for HeapWord-aligned source
    // arrays, so just use the code we've already generated
    StubRoutines::_arrayof_jbyte_disjoint_arraycopy  = StubRoutines::_jbyte_disjoint_arraycopy;
    StubRoutines::_arrayof_jbyte_arraycopy           = StubRoutines::_jbyte_arraycopy;

    StubRoutines::_arrayof_jshort_disjoint_arraycopy = StubRoutines::_jshort_disjoint_arraycopy;
    StubRoutines::_arrayof_jshort_arraycopy          = StubRoutines::_jshort_arraycopy;

    StubRoutines::_arrayof_jint_disjoint_arraycopy   = StubRoutines::_jint_disjoint_arraycopy;
    StubRoutines::_arrayof_jint_arraycopy            = StubRoutines::_jint_arraycopy;

    StubRoutines::_arrayof_jlong_disjoint_arraycopy  = StubRoutines::_jlong_disjoint_arraycopy;
    StubRoutines::_arrayof_jlong_arraycopy           = StubRoutines::_jlong_arraycopy;

    StubRoutines::_arrayof_oop_disjoint_arraycopy    = StubRoutines::_oop_disjoint_arraycopy;
    StubRoutines::_arrayof_oop_arraycopy             = StubRoutines::_oop_arraycopy;

    StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit    = StubRoutines::_oop_disjoint_arraycopy_uninit;
    StubRoutines::_arrayof_oop_arraycopy_uninit             = StubRoutines::_oop_arraycopy_uninit;
  }

  void generate_math_stubs() {
    {
      StubCodeMark mark(this, "StubRoutines", "log10");
      StubRoutines::_intrinsic_log10 = (double (*)(double)) __ pc();

      __ subq(rsp, 8);
      __ movdbl(Address(rsp, 0), xmm0);
      __ fld_d(Address(rsp, 0));
      __ flog10();
      __ fstp_d(Address(rsp, 0));
      __ movdbl(xmm0, Address(rsp, 0));
      __ addq(rsp, 8);
      __ ret(0);
    }
    {
      StubCodeMark mark(this, "StubRoutines", "tan");
      StubRoutines::_intrinsic_tan = (double (*)(double)) __ pc();

      __ subq(rsp, 8);
      __ movdbl(Address(rsp, 0), xmm0);
      __ fld_d(Address(rsp, 0));
      __ trigfunc('t');
      __ fstp_d(Address(rsp, 0));
      __ movdbl(xmm0, Address(rsp, 0));
      __ addq(rsp, 8);
      __ ret(0);
    }
  }

  // AES intrinsic stubs
  enum {AESBlockSize = 16};

  address generate_key_shuffle_mask() {
    __ align(16);
    StubCodeMark mark(this, "StubRoutines", "key_shuffle_mask");
    address start = __ pc();
    __ emit_data64( 0x0405060700010203, relocInfo::none );
    __ emit_data64( 0x0c0d0e0f08090a0b, relocInfo::none );
    return start;
  }

  address generate_counter_shuffle_mask() {
    __ align(16);
    StubCodeMark mark(this, "StubRoutines", "counter_shuffle_mask");
    address start = __ pc();
    __ emit_data64(0x08090a0b0c0d0e0f, relocInfo::none);
    __ emit_data64(0x0001020304050607, relocInfo::none);
    return start;
  }

  // Utility routine for loading a 128-bit key word in little endian format
  // can optionally specify that the shuffle mask is already in an xmmregister
  void load_key(XMMRegister xmmdst, Register key, int offset, XMMRegister xmm_shuf_mask=NULL) {
    __ movdqu(xmmdst, Address(key, offset));
    if (xmm_shuf_mask != NULL) {
      __ pshufb(xmmdst, xmm_shuf_mask);
    } else {
      __ pshufb(xmmdst, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));
    }
  }

  // Utility routine for increase 128bit counter (iv in CTR mode)
  void inc_counter(Register reg, XMMRegister xmmdst, int inc_delta, Label& next_block) {
    __ pextrq(reg, xmmdst, 0x0);
    __ addq(reg, inc_delta);
    __ pinsrq(xmmdst, reg, 0x0);
    __ jcc(Assembler::carryClear, next_block); // jump if no carry
    __ pextrq(reg, xmmdst, 0x01); // Carry
    __ addq(reg, 0x01);
    __ pinsrq(xmmdst, reg, 0x01); //Carry end
    __ BIND(next_block);          // next instruction
  }

  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - source byte array address
  //   c_rarg1   - destination byte array address
  //   c_rarg2   - K (key) in little endian int array
  //
  address generate_aescrypt_encryptBlock() {
    assert(UseAES, "need AES instructions and misaligned SSE support");
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "aescrypt_encryptBlock");
    Label L_doLast;
    address start = __ pc();

    const Register from        = c_rarg0;  // source array address
    const Register to          = c_rarg1;  // destination array address
    const Register key         = c_rarg2;  // key array address
    const Register keylen      = rax;

    const XMMRegister xmm_result = xmm0;
    const XMMRegister xmm_key_shuf_mask = xmm1;
    // On win64 xmm6-xmm15 must be preserved so don't use them.
    const XMMRegister xmm_temp1  = xmm2;
    const XMMRegister xmm_temp2  = xmm3;
    const XMMRegister xmm_temp3  = xmm4;
    const XMMRegister xmm_temp4  = xmm5;

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
    // context for the registers used, where all instructions below are using 128-bit mode
    // On EVEX without VL and BW, these instructions will all be AVX.
    if (VM_Version::supports_avx512vlbw()) {
      __ movl(rax, 0xffff);
      __ kmovql(k1, rax);
    }

    // keylen could be only {11, 13, 15} * 4 = {44, 52, 60}
    __ movl(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    __ movdqu(xmm_key_shuf_mask, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));
    __ movdqu(xmm_result, Address(from, 0));  // get 16 bytes of input

    // For encryption, the java expanded key ordering is just what we need
    // we don't know if the key is aligned, hence not using load-execute form

    load_key(xmm_temp1, key, 0x00, xmm_key_shuf_mask);
    __ pxor(xmm_result, xmm_temp1);

    load_key(xmm_temp1, key, 0x10, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0x20, xmm_key_shuf_mask);
    load_key(xmm_temp3, key, 0x30, xmm_key_shuf_mask);
    load_key(xmm_temp4, key, 0x40, xmm_key_shuf_mask);

    __ aesenc(xmm_result, xmm_temp1);
    __ aesenc(xmm_result, xmm_temp2);
    __ aesenc(xmm_result, xmm_temp3);
    __ aesenc(xmm_result, xmm_temp4);

    load_key(xmm_temp1, key, 0x50, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0x60, xmm_key_shuf_mask);
    load_key(xmm_temp3, key, 0x70, xmm_key_shuf_mask);
    load_key(xmm_temp4, key, 0x80, xmm_key_shuf_mask);

    __ aesenc(xmm_result, xmm_temp1);
    __ aesenc(xmm_result, xmm_temp2);
    __ aesenc(xmm_result, xmm_temp3);
    __ aesenc(xmm_result, xmm_temp4);

    load_key(xmm_temp1, key, 0x90, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0xa0, xmm_key_shuf_mask);

    __ cmpl(keylen, 44);
    __ jccb(Assembler::equal, L_doLast);

    __ aesenc(xmm_result, xmm_temp1);
    __ aesenc(xmm_result, xmm_temp2);

    load_key(xmm_temp1, key, 0xb0, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0xc0, xmm_key_shuf_mask);

    __ cmpl(keylen, 52);
    __ jccb(Assembler::equal, L_doLast);

    __ aesenc(xmm_result, xmm_temp1);
    __ aesenc(xmm_result, xmm_temp2);

    load_key(xmm_temp1, key, 0xd0, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0xe0, xmm_key_shuf_mask);

    __ BIND(L_doLast);
    __ aesenc(xmm_result, xmm_temp1);
    __ aesenclast(xmm_result, xmm_temp2);
    __ movdqu(Address(to, 0), xmm_result);        // store the result
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;
  }


  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - source byte array address
  //   c_rarg1   - destination byte array address
  //   c_rarg2   - K (key) in little endian int array
  //
  address generate_aescrypt_decryptBlock() {
    assert(UseAES, "need AES instructions and misaligned SSE support");
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "aescrypt_decryptBlock");
    Label L_doLast;
    address start = __ pc();

    const Register from        = c_rarg0;  // source array address
    const Register to          = c_rarg1;  // destination array address
    const Register key         = c_rarg2;  // key array address
    const Register keylen      = rax;

    const XMMRegister xmm_result = xmm0;
    const XMMRegister xmm_key_shuf_mask = xmm1;
    // On win64 xmm6-xmm15 must be preserved so don't use them.
    const XMMRegister xmm_temp1  = xmm2;
    const XMMRegister xmm_temp2  = xmm3;
    const XMMRegister xmm_temp3  = xmm4;
    const XMMRegister xmm_temp4  = xmm5;

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
    // context for the registers used, where all instructions below are using 128-bit mode
    // On EVEX without VL and BW, these instructions will all be AVX.
    if (VM_Version::supports_avx512vlbw()) {
      __ movl(rax, 0xffff);
      __ kmovql(k1, rax);
    }

    // keylen could be only {11, 13, 15} * 4 = {44, 52, 60}
    __ movl(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    __ movdqu(xmm_key_shuf_mask, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));
    __ movdqu(xmm_result, Address(from, 0));

    // for decryption java expanded key ordering is rotated one position from what we want
    // so we start from 0x10 here and hit 0x00 last
    // we don't know if the key is aligned, hence not using load-execute form
    load_key(xmm_temp1, key, 0x10, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0x20, xmm_key_shuf_mask);
    load_key(xmm_temp3, key, 0x30, xmm_key_shuf_mask);
    load_key(xmm_temp4, key, 0x40, xmm_key_shuf_mask);

    __ pxor  (xmm_result, xmm_temp1);
    __ aesdec(xmm_result, xmm_temp2);
    __ aesdec(xmm_result, xmm_temp3);
    __ aesdec(xmm_result, xmm_temp4);

    load_key(xmm_temp1, key, 0x50, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0x60, xmm_key_shuf_mask);
    load_key(xmm_temp3, key, 0x70, xmm_key_shuf_mask);
    load_key(xmm_temp4, key, 0x80, xmm_key_shuf_mask);

    __ aesdec(xmm_result, xmm_temp1);
    __ aesdec(xmm_result, xmm_temp2);
    __ aesdec(xmm_result, xmm_temp3);
    __ aesdec(xmm_result, xmm_temp4);

    load_key(xmm_temp1, key, 0x90, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0xa0, xmm_key_shuf_mask);
    load_key(xmm_temp3, key, 0x00, xmm_key_shuf_mask);

    __ cmpl(keylen, 44);
    __ jccb(Assembler::equal, L_doLast);

    __ aesdec(xmm_result, xmm_temp1);
    __ aesdec(xmm_result, xmm_temp2);

    load_key(xmm_temp1, key, 0xb0, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0xc0, xmm_key_shuf_mask);

    __ cmpl(keylen, 52);
    __ jccb(Assembler::equal, L_doLast);

    __ aesdec(xmm_result, xmm_temp1);
    __ aesdec(xmm_result, xmm_temp2);

    load_key(xmm_temp1, key, 0xd0, xmm_key_shuf_mask);
    load_key(xmm_temp2, key, 0xe0, xmm_key_shuf_mask);

    __ BIND(L_doLast);
    __ aesdec(xmm_result, xmm_temp1);
    __ aesdec(xmm_result, xmm_temp2);

    // for decryption the aesdeclast operation is always on key+0x00
    __ aesdeclast(xmm_result, xmm_temp3);
    __ movdqu(Address(to, 0), xmm_result);  // store the result
    __ xorptr(rax, rax); // return 0
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;
  }


  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - source byte array address
  //   c_rarg1   - destination byte array address
  //   c_rarg2   - K (key) in little endian int array
  //   c_rarg3   - r vector byte array address
  //   c_rarg4   - input length
  //
  // Output:
  //   rax       - input length
  //
  address generate_cipherBlockChaining_encryptAESCrypt() {
    assert(UseAES, "need AES instructions and misaligned SSE support");
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "cipherBlockChaining_encryptAESCrypt");
    address start = __ pc();

    Label L_exit, L_key_192_256, L_key_256, L_loopTop_128, L_loopTop_192, L_loopTop_256;
    const Register from        = c_rarg0;  // source array address
    const Register to          = c_rarg1;  // destination array address
    const Register key         = c_rarg2;  // key array address
    const Register rvec        = c_rarg3;  // r byte array initialized from initvector array address
                                           // and left with the results of the last encryption block
#ifndef _WIN64
    const Register len_reg     = c_rarg4;  // src len (must be multiple of blocksize 16)
#else
    const Address  len_mem(rbp, 6 * wordSize);  // length is on stack on Win64
    const Register len_reg     = r10;      // pick the first volatile windows register
#endif
    const Register pos         = rax;

    // xmm register assignments for the loops below
    const XMMRegister xmm_result = xmm0;
    const XMMRegister xmm_temp   = xmm1;
    // keys 0-10 preloaded into xmm2-xmm12
    const int XMM_REG_NUM_KEY_FIRST = 2;
    const int XMM_REG_NUM_KEY_LAST  = 15;
    const XMMRegister xmm_key0   = as_XMMRegister(XMM_REG_NUM_KEY_FIRST);
    const XMMRegister xmm_key10  = as_XMMRegister(XMM_REG_NUM_KEY_FIRST+10);
    const XMMRegister xmm_key11  = as_XMMRegister(XMM_REG_NUM_KEY_FIRST+11);
    const XMMRegister xmm_key12  = as_XMMRegister(XMM_REG_NUM_KEY_FIRST+12);
    const XMMRegister xmm_key13  = as_XMMRegister(XMM_REG_NUM_KEY_FIRST+13);

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
    // context for the registers used, where all instructions below are using 128-bit mode
    // On EVEX without VL and BW, these instructions will all be AVX.
    if (VM_Version::supports_avx512vlbw()) {
      __ movl(rax, 0xffff);
      __ kmovql(k1, rax);
    }

#ifdef _WIN64
    // on win64, fill len_reg from stack position
    __ movl(len_reg, len_mem);
    // save the xmm registers which must be preserved 6-15
    __ subptr(rsp, -rsp_after_call_off * wordSize);
    for (int i = 6; i <= XMM_REG_NUM_KEY_LAST; i++) {
      __ movdqu(xmm_save(i), as_XMMRegister(i));
    }
#else
    __ push(len_reg); // Save
#endif

    const XMMRegister xmm_key_shuf_mask = xmm_temp;  // used temporarily to swap key bytes up front
    __ movdqu(xmm_key_shuf_mask, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));
    // load up xmm regs xmm2 thru xmm12 with key 0x00 - 0xa0
    for (int rnum = XMM_REG_NUM_KEY_FIRST, offset = 0x00; rnum <= XMM_REG_NUM_KEY_FIRST+10; rnum++) {
      load_key(as_XMMRegister(rnum), key, offset, xmm_key_shuf_mask);
      offset += 0x10;
    }
    __ movdqu(xmm_result, Address(rvec, 0x00));   // initialize xmm_result with r vec

    // now split to different paths depending on the keylen (len in ints of AESCrypt.KLE array (52=192, or 60=256))
    __ movl(rax, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));
    __ cmpl(rax, 44);
    __ jcc(Assembler::notEqual, L_key_192_256);

    // 128 bit code follows here
    __ movptr(pos, 0);
    __ align(OptoLoopAlignment);

    __ BIND(L_loopTop_128);
    __ movdqu(xmm_temp, Address(from, pos, Address::times_1, 0));   // get next 16 bytes of input
    __ pxor  (xmm_result, xmm_temp);               // xor with the current r vector
    __ pxor  (xmm_result, xmm_key0);               // do the aes rounds
    for (int rnum = XMM_REG_NUM_KEY_FIRST + 1; rnum <= XMM_REG_NUM_KEY_FIRST + 9; rnum++) {
      __ aesenc(xmm_result, as_XMMRegister(rnum));
    }
    __ aesenclast(xmm_result, xmm_key10);
    __ movdqu(Address(to, pos, Address::times_1, 0), xmm_result);     // store into the next 16 bytes of output
    // no need to store r to memory until we exit
    __ addptr(pos, AESBlockSize);
    __ subptr(len_reg, AESBlockSize);
    __ jcc(Assembler::notEqual, L_loopTop_128);

    __ BIND(L_exit);
    __ movdqu(Address(rvec, 0), xmm_result);     // final value of r stored in rvec of CipherBlockChaining object

#ifdef _WIN64
    // restore xmm regs belonging to calling function
    for (int i = 6; i <= XMM_REG_NUM_KEY_LAST; i++) {
      __ movdqu(as_XMMRegister(i), xmm_save(i));
    }
    __ movl(rax, len_mem);
#else
    __ pop(rax); // return length
#endif
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    __ BIND(L_key_192_256);
    // here rax = len in ints of AESCrypt.KLE array (52=192, or 60=256)
    load_key(xmm_key11, key, 0xb0, xmm_key_shuf_mask);
    load_key(xmm_key12, key, 0xc0, xmm_key_shuf_mask);
    __ cmpl(rax, 52);
    __ jcc(Assembler::notEqual, L_key_256);

    // 192-bit code follows here (could be changed to use more xmm registers)
    __ movptr(pos, 0);
    __ align(OptoLoopAlignment);

    __ BIND(L_loopTop_192);
    __ movdqu(xmm_temp, Address(from, pos, Address::times_1, 0));   // get next 16 bytes of input
    __ pxor  (xmm_result, xmm_temp);               // xor with the current r vector
    __ pxor  (xmm_result, xmm_key0);               // do the aes rounds
    for (int rnum = XMM_REG_NUM_KEY_FIRST + 1; rnum  <= XMM_REG_NUM_KEY_FIRST + 11; rnum++) {
      __ aesenc(xmm_result, as_XMMRegister(rnum));
    }
    __ aesenclast(xmm_result, xmm_key12);
    __ movdqu(Address(to, pos, Address::times_1, 0), xmm_result);     // store into the next 16 bytes of output
    // no need to store r to memory until we exit
    __ addptr(pos, AESBlockSize);
    __ subptr(len_reg, AESBlockSize);
    __ jcc(Assembler::notEqual, L_loopTop_192);
    __ jmp(L_exit);

    __ BIND(L_key_256);
    // 256-bit code follows here (could be changed to use more xmm registers)
    load_key(xmm_key13, key, 0xd0, xmm_key_shuf_mask);
    __ movptr(pos, 0);
    __ align(OptoLoopAlignment);

    __ BIND(L_loopTop_256);
    __ movdqu(xmm_temp, Address(from, pos, Address::times_1, 0));   // get next 16 bytes of input
    __ pxor  (xmm_result, xmm_temp);               // xor with the current r vector
    __ pxor  (xmm_result, xmm_key0);               // do the aes rounds
    for (int rnum = XMM_REG_NUM_KEY_FIRST + 1; rnum  <= XMM_REG_NUM_KEY_FIRST + 13; rnum++) {
      __ aesenc(xmm_result, as_XMMRegister(rnum));
    }
    load_key(xmm_temp, key, 0xe0);
    __ aesenclast(xmm_result, xmm_temp);
    __ movdqu(Address(to, pos, Address::times_1, 0), xmm_result);     // store into the next 16 bytes of output
    // no need to store r to memory until we exit
    __ addptr(pos, AESBlockSize);
    __ subptr(len_reg, AESBlockSize);
    __ jcc(Assembler::notEqual, L_loopTop_256);
    __ jmp(L_exit);

    return start;
  }

  // Safefetch stubs.
  void generate_safefetch(const char* name, int size, address* entry,
                          address* fault_pc, address* continuation_pc) {
    // safefetch signatures:
    //   int      SafeFetch32(int*      adr, int      errValue);
    //   intptr_t SafeFetchN (intptr_t* adr, intptr_t errValue);
    //
    // arguments:
    //   c_rarg0 = adr
    //   c_rarg1 = errValue
    //
    // result:
    //   PPC_RET  = *adr or errValue

    StubCodeMark mark(this, "StubRoutines", name);

    // Entry point, pc or function descriptor.
    *entry = __ pc();

    // Load *adr into c_rarg1, may fault.
    *fault_pc = __ pc();
    switch (size) {
      case 4:
        // int32_t
        __ movl(c_rarg1, Address(c_rarg0, 0));
        break;
      case 8:
        // int64_t
        __ movq(c_rarg1, Address(c_rarg0, 0));
        break;
      default:
        ShouldNotReachHere();
    }

    // return errValue or *adr
    *continuation_pc = __ pc();
    __ movq(rax, c_rarg1);
    __ ret(0);
  }

  // This is a version of CBC/AES Decrypt which does 4 blocks in a loop at a time
  // to hide instruction latency
  //
  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - source byte array address
  //   c_rarg1   - destination byte array address
  //   c_rarg2   - K (key) in little endian int array
  //   c_rarg3   - r vector byte array address
  //   c_rarg4   - input length
  //
  // Output:
  //   rax       - input length
  //
  address generate_cipherBlockChaining_decryptAESCrypt_Parallel() {
    assert(UseAES, "need AES instructions and misaligned SSE support");
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "cipherBlockChaining_decryptAESCrypt");
    address start = __ pc();

    const Register from        = c_rarg0;  // source array address
    const Register to          = c_rarg1;  // destination array address
    const Register key         = c_rarg2;  // key array address
    const Register rvec        = c_rarg3;  // r byte array initialized from initvector array address
                                           // and left with the results of the last encryption block
#ifndef _WIN64
    const Register len_reg     = c_rarg4;  // src len (must be multiple of blocksize 16)
#else
    const Address  len_mem(rbp, 6 * wordSize);  // length is on stack on Win64
    const Register len_reg     = r10;      // pick the first volatile windows register
#endif
    const Register pos         = rax;

    const int PARALLEL_FACTOR = 4;
    const int ROUNDS[3] = { 10, 12, 14 }; // aes rounds for key128, key192, key256

    Label L_exit;
    Label L_singleBlock_loopTopHead[3]; // 128, 192, 256
    Label L_singleBlock_loopTopHead2[3]; // 128, 192, 256
    Label L_singleBlock_loopTop[3]; // 128, 192, 256
    Label L_multiBlock_loopTopHead[3]; // 128, 192, 256
    Label L_multiBlock_loopTop[3]; // 128, 192, 256

    // keys 0-10 preloaded into xmm5-xmm15
    const int XMM_REG_NUM_KEY_FIRST = 5;
    const int XMM_REG_NUM_KEY_LAST  = 15;
    const XMMRegister xmm_key_first = as_XMMRegister(XMM_REG_NUM_KEY_FIRST);
    const XMMRegister xmm_key_last  = as_XMMRegister(XMM_REG_NUM_KEY_LAST);

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
    // context for the registers used, where all instructions below are using 128-bit mode
    // On EVEX without VL and BW, these instructions will all be AVX.
    if (VM_Version::supports_avx512vlbw()) {
      __ movl(rax, 0xffff);
      __ kmovql(k1, rax);
    }

#ifdef _WIN64
    // on win64, fill len_reg from stack position
    __ movl(len_reg, len_mem);
    // save the xmm registers which must be preserved 6-15
    __ subptr(rsp, -rsp_after_call_off * wordSize);
    for (int i = 6; i <= XMM_REG_NUM_KEY_LAST; i++) {
      __ movdqu(xmm_save(i), as_XMMRegister(i));
    }
#else
    __ push(len_reg); // Save
#endif
    __ push(rbx);
    // the java expanded key ordering is rotated one position from what we want
    // so we start from 0x10 here and hit 0x00 last
    const XMMRegister xmm_key_shuf_mask = xmm1;  // used temporarily to swap key bytes up front
    __ movdqu(xmm_key_shuf_mask, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));
    // load up xmm regs 5 thru 15 with key 0x10 - 0xa0 - 0x00
    for (int rnum = XMM_REG_NUM_KEY_FIRST, offset = 0x10; rnum < XMM_REG_NUM_KEY_LAST; rnum++) {
      load_key(as_XMMRegister(rnum), key, offset, xmm_key_shuf_mask);
      offset += 0x10;
    }
    load_key(xmm_key_last, key, 0x00, xmm_key_shuf_mask);

    const XMMRegister xmm_prev_block_cipher = xmm1;  // holds cipher of previous block

    // registers holding the four results in the parallelized loop
    const XMMRegister xmm_result0 = xmm0;
    const XMMRegister xmm_result1 = xmm2;
    const XMMRegister xmm_result2 = xmm3;
    const XMMRegister xmm_result3 = xmm4;

    __ movdqu(xmm_prev_block_cipher, Address(rvec, 0x00));   // initialize with initial rvec

    __ xorptr(pos, pos);

    // now split to different paths depending on the keylen (len in ints of AESCrypt.KLE array (52=192, or 60=256))
    __ movl(rbx, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));
    __ cmpl(rbx, 52);
    __ jcc(Assembler::equal, L_multiBlock_loopTopHead[1]);
    __ cmpl(rbx, 60);
    __ jcc(Assembler::equal, L_multiBlock_loopTopHead[2]);

#define DoFour(opc, src_reg)           \
  __ opc(xmm_result0, src_reg);         \
  __ opc(xmm_result1, src_reg);         \
  __ opc(xmm_result2, src_reg);         \
  __ opc(xmm_result3, src_reg);         \

    for (int k = 0; k < 3; ++k) {
      __ BIND(L_multiBlock_loopTopHead[k]);
      if (k != 0) {
        __ cmpptr(len_reg, PARALLEL_FACTOR * AESBlockSize); // see if at least 4 blocks left
        __ jcc(Assembler::less, L_singleBlock_loopTopHead2[k]);
      }
      if (k == 1) {
        __ subptr(rsp, 6 * wordSize);
        __ movdqu(Address(rsp, 0), xmm15); //save last_key from xmm15
        load_key(xmm15, key, 0xb0); // 0xb0; 192-bit key goes up to 0xc0
        __ movdqu(Address(rsp, 2 * wordSize), xmm15);
        load_key(xmm1, key, 0xc0);  // 0xc0;
        __ movdqu(Address(rsp, 4 * wordSize), xmm1);
      } else if (k == 2) {
        __ subptr(rsp, 10 * wordSize);
        __ movdqu(Address(rsp, 0), xmm15); //save last_key from xmm15
        load_key(xmm15, key, 0xd0); // 0xd0; 256-bit key goes upto 0xe0
        __ movdqu(Address(rsp, 6 * wordSize), xmm15);
        load_key(xmm1, key, 0xe0);  // 0xe0;
        __ movdqu(Address(rsp, 8 * wordSize), xmm1);
        load_key(xmm15, key, 0xb0); // 0xb0;
        __ movdqu(Address(rsp, 2 * wordSize), xmm15);
        load_key(xmm1, key, 0xc0);  // 0xc0;
        __ movdqu(Address(rsp, 4 * wordSize), xmm1);
      }
      __ align(OptoLoopAlignment);
      __ BIND(L_multiBlock_loopTop[k]);
      __ cmpptr(len_reg, PARALLEL_FACTOR * AESBlockSize); // see if at least 4 blocks left
      __ jcc(Assembler::less, L_singleBlock_loopTopHead[k]);

      if  (k != 0) {
        __ movdqu(xmm15, Address(rsp, 2 * wordSize));
        __ movdqu(xmm1, Address(rsp, 4 * wordSize));
      }

      __ movdqu(xmm_result0, Address(from, pos, Address::times_1, 0 * AESBlockSize)); // get next 4 blocks into xmmresult registers
      __ movdqu(xmm_result1, Address(from, pos, Address::times_1, 1 * AESBlockSize));
      __ movdqu(xmm_result2, Address(from, pos, Address::times_1, 2 * AESBlockSize));
      __ movdqu(xmm_result3, Address(from, pos, Address::times_1, 3 * AESBlockSize));

      DoFour(pxor, xmm_key_first);
      if (k == 0) {
        for (int rnum = 1; rnum < ROUNDS[k]; rnum++) {
          DoFour(aesdec, as_XMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
        }
        DoFour(aesdeclast, xmm_key_last);
      } else if (k == 1) {
        for (int rnum = 1; rnum <= ROUNDS[k]-2; rnum++) {
          DoFour(aesdec, as_XMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
        }
        __ movdqu(xmm_key_last, Address(rsp, 0)); // xmm15 needs to be loaded again.
        DoFour(aesdec, xmm1);  // key : 0xc0
        __ movdqu(xmm_prev_block_cipher, Address(rvec, 0x00));  // xmm1 needs to be loaded again
        DoFour(aesdeclast, xmm_key_last);
      } else if (k == 2) {
        for (int rnum = 1; rnum <= ROUNDS[k] - 4; rnum++) {
          DoFour(aesdec, as_XMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
        }
        DoFour(aesdec, xmm1);  // key : 0xc0
        __ movdqu(xmm15, Address(rsp, 6 * wordSize));
        __ movdqu(xmm1, Address(rsp, 8 * wordSize));
        DoFour(aesdec, xmm15);  // key : 0xd0
        __ movdqu(xmm_key_last, Address(rsp, 0)); // xmm15 needs to be loaded again.
        DoFour(aesdec, xmm1);  // key : 0xe0
        __ movdqu(xmm_prev_block_cipher, Address(rvec, 0x00));  // xmm1 needs to be loaded again
        DoFour(aesdeclast, xmm_key_last);
      }

      // for each result, xor with the r vector of previous cipher block
      __ pxor(xmm_result0, xmm_prev_block_cipher);
      __ movdqu(xmm_prev_block_cipher, Address(from, pos, Address::times_1, 0 * AESBlockSize));
      __ pxor(xmm_result1, xmm_prev_block_cipher);
      __ movdqu(xmm_prev_block_cipher, Address(from, pos, Address::times_1, 1 * AESBlockSize));
      __ pxor(xmm_result2, xmm_prev_block_cipher);
      __ movdqu(xmm_prev_block_cipher, Address(from, pos, Address::times_1, 2 * AESBlockSize));
      __ pxor(xmm_result3, xmm_prev_block_cipher);
      __ movdqu(xmm_prev_block_cipher, Address(from, pos, Address::times_1, 3 * AESBlockSize));   // this will carry over to next set of blocks
      if (k != 0) {
        __ movdqu(Address(rvec, 0x00), xmm_prev_block_cipher);
      }

      __ movdqu(Address(to, pos, Address::times_1, 0 * AESBlockSize), xmm_result0);     // store 4 results into the next 64 bytes of output
      __ movdqu(Address(to, pos, Address::times_1, 1 * AESBlockSize), xmm_result1);
      __ movdqu(Address(to, pos, Address::times_1, 2 * AESBlockSize), xmm_result2);
      __ movdqu(Address(to, pos, Address::times_1, 3 * AESBlockSize), xmm_result3);

      __ addptr(pos, PARALLEL_FACTOR * AESBlockSize);
      __ subptr(len_reg, PARALLEL_FACTOR * AESBlockSize);
      __ jmp(L_multiBlock_loopTop[k]);

      // registers used in the non-parallelized loops
      // xmm register assignments for the loops below
      const XMMRegister xmm_result = xmm0;
      const XMMRegister xmm_prev_block_cipher_save = xmm2;
      const XMMRegister xmm_key11 = xmm3;
      const XMMRegister xmm_key12 = xmm4;
      const XMMRegister key_tmp = xmm4;

      __ BIND(L_singleBlock_loopTopHead[k]);
      if (k == 1) {
        __ addptr(rsp, 6 * wordSize);
      } else if (k == 2) {
        __ addptr(rsp, 10 * wordSize);
      }
      __ cmpptr(len_reg, 0); // any blocks left??
      __ jcc(Assembler::equal, L_exit);
      __ BIND(L_singleBlock_loopTopHead2[k]);
      if (k == 1) {
        load_key(xmm_key11, key, 0xb0); // 0xb0; 192-bit key goes upto 0xc0
        load_key(xmm_key12, key, 0xc0); // 0xc0; 192-bit key goes upto 0xc0
      }
      if (k == 2) {
        load_key(xmm_key11, key, 0xb0); // 0xb0; 256-bit key goes upto 0xe0
      }
      __ align(OptoLoopAlignment);
      __ BIND(L_singleBlock_loopTop[k]);
      __ movdqu(xmm_result, Address(from, pos, Address::times_1, 0)); // get next 16 bytes of cipher input
      __ movdqa(xmm_prev_block_cipher_save, xmm_result); // save for next r vector
      __ pxor(xmm_result, xmm_key_first); // do the aes dec rounds
      for (int rnum = 1; rnum <= 9 ; rnum++) {
          __ aesdec(xmm_result, as_XMMRegister(rnum + XMM_REG_NUM_KEY_FIRST));
      }
      if (k == 1) {
        __ aesdec(xmm_result, xmm_key11);
        __ aesdec(xmm_result, xmm_key12);
      }
      if (k == 2) {
        __ aesdec(xmm_result, xmm_key11);
        load_key(key_tmp, key, 0xc0);
        __ aesdec(xmm_result, key_tmp);
        load_key(key_tmp, key, 0xd0);
        __ aesdec(xmm_result, key_tmp);
        load_key(key_tmp, key, 0xe0);
        __ aesdec(xmm_result, key_tmp);
      }

      __ aesdeclast(xmm_result, xmm_key_last); // xmm15 always came from key+0
      __ pxor(xmm_result, xmm_prev_block_cipher); // xor with the current r vector
      __ movdqu(Address(to, pos, Address::times_1, 0), xmm_result); // store into the next 16 bytes of output
      // no need to store r to memory until we exit
      __ movdqa(xmm_prev_block_cipher, xmm_prev_block_cipher_save); // set up next r vector with cipher input from this block
      __ addptr(pos, AESBlockSize);
      __ subptr(len_reg, AESBlockSize);
      __ jcc(Assembler::notEqual, L_singleBlock_loopTop[k]);
      if (k != 2) {
        __ jmp(L_exit);
      }
    } //for 128/192/256

    __ BIND(L_exit);
    __ movdqu(Address(rvec, 0), xmm_prev_block_cipher);     // final value of r stored in rvec of CipherBlockChaining object
    __ pop(rbx);
#ifdef _WIN64
    // restore regs belonging to calling function
    for (int i = 6; i <= XMM_REG_NUM_KEY_LAST; i++) {
      __ movdqu(as_XMMRegister(i), xmm_save(i));
    }
    __ movl(rax, len_mem);
#else
    __ pop(rax); // return length
#endif
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);
    return start;
}

  address generate_upper_word_mask() {
    __ align(64);
    StubCodeMark mark(this, "StubRoutines", "upper_word_mask");
    address start = __ pc();
    __ emit_data64(0x0000000000000000, relocInfo::none);
    __ emit_data64(0xFFFFFFFF00000000, relocInfo::none);
    return start;
  }

  address generate_shuffle_byte_flip_mask() {
    __ align(64);
    StubCodeMark mark(this, "StubRoutines", "shuffle_byte_flip_mask");
    address start = __ pc();
    __ emit_data64(0x08090a0b0c0d0e0f, relocInfo::none);
    __ emit_data64(0x0001020304050607, relocInfo::none);
    return start;
  }

  // ofs and limit are use for multi-block byte array.
  // int com.sun.security.provider.DigestBase.implCompressMultiBlock(byte[] b, int ofs, int limit)
  address generate_sha1_implCompress(bool multi_block, const char *name) {
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

#ifdef _WIN64
    // save the xmm registers which must be preserved 6-7
    __ subptr(rsp, 4 * wordSize);
    __ movdqu(Address(rsp, 0), xmm6);
    __ movdqu(Address(rsp, 2 * wordSize), xmm7);
#endif

    __ subptr(rsp, 4 * wordSize);

    __ fast_sha1(abcd, e0, e1, msg0, msg1, msg2, msg3, shuf_mask,
      buf, state, ofs, limit, rsp, multi_block);

    __ addptr(rsp, 4 * wordSize);
#ifdef _WIN64
    // restore xmm regs belonging to calling function
    __ movdqu(xmm6, Address(rsp, 0));
    __ movdqu(xmm7, Address(rsp, 2 * wordSize));
    __ addptr(rsp, 4 * wordSize);
#endif

    __ leave();
    __ ret(0);
    return start;
  }

  address generate_pshuffle_byte_flip_mask() {
    __ align(64);
    StubCodeMark mark(this, "StubRoutines", "pshuffle_byte_flip_mask");
    address start = __ pc();
    __ emit_data64(0x0405060700010203, relocInfo::none);
    __ emit_data64(0x0c0d0e0f08090a0b, relocInfo::none);
    return start;
  }

// ofs and limit are use for multi-block byte array.
// int com.sun.security.provider.DigestBase.implCompressMultiBlock(byte[] b, int ofs, int limit)
  address generate_sha256_implCompress(bool multi_block, const char *name) {
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
#ifdef _WIN64
    // save the xmm registers which must be preserved 6-7
    __ subptr(rsp, 6 * wordSize);
    __ movdqu(Address(rsp, 0), xmm6);
    __ movdqu(Address(rsp, 2 * wordSize), xmm7);
    __ movdqu(Address(rsp, 4 * wordSize), xmm8);
#endif

    __ subptr(rsp, 4 * wordSize);

    __ fast_sha256(msg, state0, state1, msgtmp0, msgtmp1, msgtmp2, msgtmp3, msgtmp4,
      buf, state, ofs, limit, rsp, multi_block, shuf_mask);

    __ addptr(rsp, 4 * wordSize);
#ifdef _WIN64
    // restore xmm regs belonging to calling function
    __ movdqu(xmm6, Address(rsp, 0));
    __ movdqu(xmm7, Address(rsp, 2 * wordSize));
    __ movdqu(xmm8, Address(rsp, 4 * wordSize));
    __ addptr(rsp, 6 * wordSize);
#endif
    __ leave();
    __ ret(0);
    return start;
  }

  // This is a version of CTR/AES crypt which does 6 blocks in a loop at a time
  // to hide instruction latency
  //
  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - source byte array address
  //   c_rarg1   - destination byte array address
  //   c_rarg2   - K (key) in little endian int array
  //   c_rarg3   - counter vector byte array address
  //   Linux
  //     c_rarg4   -          input length
  //     c_rarg5   -          saved encryptedCounter start
  //     rbp + 6 * wordSize - saved used length
  //   Windows
  //     rbp + 6 * wordSize - input length
  //     rbp + 7 * wordSize - saved encryptedCounter start
  //     rbp + 8 * wordSize - saved used length
  //
  // Output:
  //   rax       - input length
  //
  address generate_counterMode_AESCrypt_Parallel() {
    assert(UseAES, "need AES instructions and misaligned SSE support");
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "counterMode_AESCrypt");
    address start = __ pc();
    const Register from = c_rarg0; // source array address
    const Register to = c_rarg1; // destination array address
    const Register key = c_rarg2; // key array address
    const Register counter = c_rarg3; // counter byte array initialized from counter array address
                                      // and updated with the incremented counter in the end
#ifndef _WIN64
    const Register len_reg = c_rarg4;
    const Register saved_encCounter_start = c_rarg5;
    const Register used_addr = r10;
    const Address  used_mem(rbp, 2 * wordSize);
    const Register used = r11;
#else
    const Address len_mem(rbp, 6 * wordSize); // length is on stack on Win64
    const Address saved_encCounter_mem(rbp, 7 * wordSize); // length is on stack on Win64
    const Address used_mem(rbp, 8 * wordSize); // length is on stack on Win64
    const Register len_reg = r10; // pick the first volatile windows register
    const Register saved_encCounter_start = r11;
    const Register used_addr = r13;
    const Register used = r14;
#endif
    const Register pos = rax;

    const int PARALLEL_FACTOR = 6;
    const XMMRegister xmm_counter_shuf_mask = xmm0;
    const XMMRegister xmm_key_shuf_mask = xmm1; // used temporarily to swap key bytes up front
    const XMMRegister xmm_curr_counter = xmm2;

    const XMMRegister xmm_key_tmp0 = xmm3;
    const XMMRegister xmm_key_tmp1 = xmm4;

    // registers holding the four results in the parallelized loop
    const XMMRegister xmm_result0 = xmm5;
    const XMMRegister xmm_result1 = xmm6;
    const XMMRegister xmm_result2 = xmm7;
    const XMMRegister xmm_result3 = xmm8;
    const XMMRegister xmm_result4 = xmm9;
    const XMMRegister xmm_result5 = xmm10;

    const XMMRegister xmm_from0 = xmm11;
    const XMMRegister xmm_from1 = xmm12;
    const XMMRegister xmm_from2 = xmm13;
    const XMMRegister xmm_from3 = xmm14; //the last one is xmm14. we have to preserve it on WIN64.
    const XMMRegister xmm_from4 = xmm3; //reuse xmm3~4. Because xmm_key_tmp0~1 are useless when loading input text
    const XMMRegister xmm_from5 = xmm4;

    //for key_128, key_192, key_256
    const int rounds[3] = {10, 12, 14};
    Label L_exit_preLoop, L_preLoop_start;
    Label L_multiBlock_loopTop[3];
    Label L_singleBlockLoopTop[3];
    Label L__incCounter[3][6]; //for 6 blocks
    Label L__incCounter_single[3]; //for single block, key128, key192, key256
    Label L_processTail_insr[3], L_processTail_4_insr[3], L_processTail_2_insr[3], L_processTail_1_insr[3], L_processTail_exit_insr[3];
    Label L_processTail_extr[3], L_processTail_4_extr[3], L_processTail_2_extr[3], L_processTail_1_extr[3], L_processTail_exit_extr[3];

    Label L_exit;

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
    // context for the registers used, where all instructions below are using 128-bit mode
    // On EVEX without VL and BW, these instructions will all be AVX.
    if (VM_Version::supports_avx512vlbw()) {
        __ movl(rax, 0xffff);
        __ kmovql(k1, rax);
    }

#ifdef _WIN64
    // save the xmm registers which must be preserved 6-14
    const int XMM_REG_NUM_KEY_LAST = 14;
    __ subptr(rsp, -rsp_after_call_off * wordSize);
    for (int i = 6; i <= XMM_REG_NUM_KEY_LAST; i++) {
      __ movdqu(xmm_save(i), as_XMMRegister(i));
    }

    const Address r13_save(rbp, rdi_off * wordSize);
    const Address r14_save(rbp, rsi_off * wordSize);

    __ movptr(r13_save, r13);
    __ movptr(r14_save, r14);

    // on win64, fill len_reg from stack position
    __ movl(len_reg, len_mem);
    __ movptr(saved_encCounter_start, saved_encCounter_mem);
    __ movptr(used_addr, used_mem);
    __ movl(used, Address(used_addr, 0));
#else
    __ push(len_reg); // Save
    __ movptr(used_addr, used_mem);
    __ movl(used, Address(used_addr, 0));
#endif

    __ push(rbx); // Save RBX
    __ movdqu(xmm_curr_counter, Address(counter, 0x00)); // initialize counter with initial counter
    __ movdqu(xmm_counter_shuf_mask, ExternalAddress(StubRoutines::x86::counter_shuffle_mask_addr()));
    __ pshufb(xmm_curr_counter, xmm_counter_shuf_mask); //counter is shuffled
    __ movptr(pos, 0);

    // Use the partially used encrpyted counter from last invocation
    __ BIND(L_preLoop_start);
    __ cmpptr(used, 16);
    __ jcc(Assembler::aboveEqual, L_exit_preLoop);
      __ cmpptr(len_reg, 0);
      __ jcc(Assembler::lessEqual, L_exit_preLoop);
      __ movb(rbx, Address(saved_encCounter_start, used));
      __ xorb(rbx, Address(from, pos));
      __ movb(Address(to, pos), rbx);
      __ addptr(pos, 1);
      __ addptr(used, 1);
      __ subptr(len_reg, 1);

    __ jmp(L_preLoop_start);

    __ BIND(L_exit_preLoop);
    __ movl(Address(used_addr, 0), used);

    // key length could be only {11, 13, 15} * 4 = {44, 52, 60}
    __ movdqu(xmm_key_shuf_mask, ExternalAddress(StubRoutines::x86::key_shuffle_mask_addr()));
    __ movl(rbx, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));
    __ cmpl(rbx, 52);
    __ jcc(Assembler::equal, L_multiBlock_loopTop[1]);
    __ cmpl(rbx, 60);
    __ jcc(Assembler::equal, L_multiBlock_loopTop[2]);

#define CTR_DoSix(opc, src_reg)                \
    __ opc(xmm_result0, src_reg);              \
    __ opc(xmm_result1, src_reg);              \
    __ opc(xmm_result2, src_reg);              \
    __ opc(xmm_result3, src_reg);              \
    __ opc(xmm_result4, src_reg);              \
    __ opc(xmm_result5, src_reg);

    // k == 0 :  generate code for key_128
    // k == 1 :  generate code for key_192
    // k == 2 :  generate code for key_256
    for (int k = 0; k < 3; ++k) {
      //multi blocks starts here
      __ align(OptoLoopAlignment);
      __ BIND(L_multiBlock_loopTop[k]);
      __ cmpptr(len_reg, PARALLEL_FACTOR * AESBlockSize); // see if at least PARALLEL_FACTOR blocks left
      __ jcc(Assembler::less, L_singleBlockLoopTop[k]);
      load_key(xmm_key_tmp0, key, 0x00, xmm_key_shuf_mask);

      //load, then increase counters
      CTR_DoSix(movdqa, xmm_curr_counter);
      inc_counter(rbx, xmm_result1, 0x01, L__incCounter[k][0]);
      inc_counter(rbx, xmm_result2, 0x02, L__incCounter[k][1]);
      inc_counter(rbx, xmm_result3, 0x03, L__incCounter[k][2]);
      inc_counter(rbx, xmm_result4, 0x04, L__incCounter[k][3]);
      inc_counter(rbx, xmm_result5,  0x05, L__incCounter[k][4]);
      inc_counter(rbx, xmm_curr_counter, 0x06, L__incCounter[k][5]);
      CTR_DoSix(pshufb, xmm_counter_shuf_mask); // after increased, shuffled counters back for PXOR
      CTR_DoSix(pxor, xmm_key_tmp0);   //PXOR with Round 0 key

      //load two ROUND_KEYs at a time
      for (int i = 1; i < rounds[k]; ) {
        load_key(xmm_key_tmp1, key, (0x10 * i), xmm_key_shuf_mask);
        load_key(xmm_key_tmp0, key, (0x10 * (i+1)), xmm_key_shuf_mask);
        CTR_DoSix(aesenc, xmm_key_tmp1);
        i++;
        if (i != rounds[k]) {
          CTR_DoSix(aesenc, xmm_key_tmp0);
        } else {
          CTR_DoSix(aesenclast, xmm_key_tmp0);
        }
        i++;
      }

      // get next PARALLEL_FACTOR blocks into xmm_result registers
      __ movdqu(xmm_from0, Address(from, pos, Address::times_1, 0 * AESBlockSize));
      __ movdqu(xmm_from1, Address(from, pos, Address::times_1, 1 * AESBlockSize));
      __ movdqu(xmm_from2, Address(from, pos, Address::times_1, 2 * AESBlockSize));
      __ movdqu(xmm_from3, Address(from, pos, Address::times_1, 3 * AESBlockSize));
      __ movdqu(xmm_from4, Address(from, pos, Address::times_1, 4 * AESBlockSize));
      __ movdqu(xmm_from5, Address(from, pos, Address::times_1, 5 * AESBlockSize));

      __ pxor(xmm_result0, xmm_from0);
      __ pxor(xmm_result1, xmm_from1);
      __ pxor(xmm_result2, xmm_from2);
      __ pxor(xmm_result3, xmm_from3);
      __ pxor(xmm_result4, xmm_from4);
      __ pxor(xmm_result5, xmm_from5);

      // store 6 results into the next 64 bytes of output
      __ movdqu(Address(to, pos, Address::times_1, 0 * AESBlockSize), xmm_result0);
      __ movdqu(Address(to, pos, Address::times_1, 1 * AESBlockSize), xmm_result1);
      __ movdqu(Address(to, pos, Address::times_1, 2 * AESBlockSize), xmm_result2);
      __ movdqu(Address(to, pos, Address::times_1, 3 * AESBlockSize), xmm_result3);
      __ movdqu(Address(to, pos, Address::times_1, 4 * AESBlockSize), xmm_result4);
      __ movdqu(Address(to, pos, Address::times_1, 5 * AESBlockSize), xmm_result5);

      __ addptr(pos, PARALLEL_FACTOR * AESBlockSize); // increase the length of crypt text
      __ subptr(len_reg, PARALLEL_FACTOR * AESBlockSize); // decrease the remaining length
      __ jmp(L_multiBlock_loopTop[k]);

      // singleBlock starts here
      __ align(OptoLoopAlignment);
      __ BIND(L_singleBlockLoopTop[k]);
      __ cmpptr(len_reg, 0);
      __ jcc(Assembler::lessEqual, L_exit);
      load_key(xmm_key_tmp0, key, 0x00, xmm_key_shuf_mask);
      __ movdqa(xmm_result0, xmm_curr_counter);
      inc_counter(rbx, xmm_curr_counter, 0x01, L__incCounter_single[k]);
      __ pshufb(xmm_result0, xmm_counter_shuf_mask);
      __ pxor(xmm_result0, xmm_key_tmp0);
      for (int i = 1; i < rounds[k]; i++) {
        load_key(xmm_key_tmp0, key, (0x10 * i), xmm_key_shuf_mask);
        __ aesenc(xmm_result0, xmm_key_tmp0);
      }
      load_key(xmm_key_tmp0, key, (rounds[k] * 0x10), xmm_key_shuf_mask);
      __ aesenclast(xmm_result0, xmm_key_tmp0);
      __ cmpptr(len_reg, AESBlockSize);
      __ jcc(Assembler::less, L_processTail_insr[k]);
        __ movdqu(xmm_from0, Address(from, pos, Address::times_1, 0 * AESBlockSize));
        __ pxor(xmm_result0, xmm_from0);
        __ movdqu(Address(to, pos, Address::times_1, 0 * AESBlockSize), xmm_result0);
        __ addptr(pos, AESBlockSize);
        __ subptr(len_reg, AESBlockSize);
        __ jmp(L_singleBlockLoopTop[k]);
      __ BIND(L_processTail_insr[k]);                               // Process the tail part of the input array
        __ addptr(pos, len_reg);                                    // 1. Insert bytes from src array into xmm_from0 register
        __ testptr(len_reg, 8);
        __ jcc(Assembler::zero, L_processTail_4_insr[k]);
          __ subptr(pos,8);
          __ pinsrq(xmm_from0, Address(from, pos), 0);
        __ BIND(L_processTail_4_insr[k]);
        __ testptr(len_reg, 4);
        __ jcc(Assembler::zero, L_processTail_2_insr[k]);
          __ subptr(pos,4);
          __ pslldq(xmm_from0, 4);
          __ pinsrd(xmm_from0, Address(from, pos), 0);
        __ BIND(L_processTail_2_insr[k]);
        __ testptr(len_reg, 2);
        __ jcc(Assembler::zero, L_processTail_1_insr[k]);
          __ subptr(pos, 2);
          __ pslldq(xmm_from0, 2);
          __ pinsrw(xmm_from0, Address(from, pos), 0);
        __ BIND(L_processTail_1_insr[k]);
        __ testptr(len_reg, 1);
        __ jcc(Assembler::zero, L_processTail_exit_insr[k]);
          __ subptr(pos, 1);
          __ pslldq(xmm_from0, 1);
          __ pinsrb(xmm_from0, Address(from, pos), 0);
        __ BIND(L_processTail_exit_insr[k]);

        __ movdqu(Address(saved_encCounter_start, 0), xmm_result0);  // 2. Perform pxor of the encrypted counter and plaintext Bytes.
        __ pxor(xmm_result0, xmm_from0);                             //    Also the encrypted counter is saved for next invocation.

        __ testptr(len_reg, 8);
        __ jcc(Assembler::zero, L_processTail_4_extr[k]);            // 3. Extract bytes from xmm_result0 into the dest. array
          __ pextrq(Address(to, pos), xmm_result0, 0);
          __ psrldq(xmm_result0, 8);
          __ addptr(pos, 8);
        __ BIND(L_processTail_4_extr[k]);
        __ testptr(len_reg, 4);
        __ jcc(Assembler::zero, L_processTail_2_extr[k]);
          __ pextrd(Address(to, pos), xmm_result0, 0);
          __ psrldq(xmm_result0, 4);
          __ addptr(pos, 4);
        __ BIND(L_processTail_2_extr[k]);
        __ testptr(len_reg, 2);
        __ jcc(Assembler::zero, L_processTail_1_extr[k]);
          __ pextrw(Address(to, pos), xmm_result0, 0);
          __ psrldq(xmm_result0, 2);
          __ addptr(pos, 2);
        __ BIND(L_processTail_1_extr[k]);
        __ testptr(len_reg, 1);
        __ jcc(Assembler::zero, L_processTail_exit_extr[k]);
          __ pextrb(Address(to, pos), xmm_result0, 0);

        __ BIND(L_processTail_exit_extr[k]);
        __ movl(Address(used_addr, 0), len_reg);
        __ jmp(L_exit);

    }

    __ BIND(L_exit);
    __ pshufb(xmm_curr_counter, xmm_counter_shuf_mask); //counter is shuffled back.
    __ movdqu(Address(counter, 0), xmm_curr_counter); //save counter back
    __ pop(rbx); // pop the saved RBX.
#ifdef _WIN64
    // restore regs belonging to calling function
    for (int i = 6; i <= XMM_REG_NUM_KEY_LAST; i++) {
      __ movdqu(as_XMMRegister(i), xmm_save(i));
    }
    __ movl(rax, len_mem);
    __ movptr(r13, r13_save);
    __ movptr(r14, r14_save);
#else
    __ pop(rax); // return 'len'
#endif
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);
    return start;
  }

  // byte swap x86 long
  address generate_ghash_long_swap_mask() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "ghash_long_swap_mask");
    address start = __ pc();
    __ emit_data64(0x0f0e0d0c0b0a0908, relocInfo::none );
    __ emit_data64(0x0706050403020100, relocInfo::none );
  return start;
  }

  // byte swap x86 byte array
  address generate_ghash_byte_swap_mask() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "ghash_byte_swap_mask");
    address start = __ pc();
    __ emit_data64(0x08090a0b0c0d0e0f, relocInfo::none );
    __ emit_data64(0x0001020304050607, relocInfo::none );
  return start;
  }

  /* Single and multi-block ghash operations */
  address generate_ghash_processBlocks() {
    __ align(CodeEntryAlignment);
    Label L_ghash_loop, L_exit;
    StubCodeMark mark(this, "StubRoutines", "ghash_processBlocks");
    address start = __ pc();

    const Register state        = c_rarg0;
    const Register subkeyH      = c_rarg1;
    const Register data         = c_rarg2;
    const Register blocks       = c_rarg3;

#ifdef _WIN64
    const int XMM_REG_LAST  = 10;
#endif

    const XMMRegister xmm_temp0 = xmm0;
    const XMMRegister xmm_temp1 = xmm1;
    const XMMRegister xmm_temp2 = xmm2;
    const XMMRegister xmm_temp3 = xmm3;
    const XMMRegister xmm_temp4 = xmm4;
    const XMMRegister xmm_temp5 = xmm5;
    const XMMRegister xmm_temp6 = xmm6;
    const XMMRegister xmm_temp7 = xmm7;
    const XMMRegister xmm_temp8 = xmm8;
    const XMMRegister xmm_temp9 = xmm9;
    const XMMRegister xmm_temp10 = xmm10;

    __ enter();

    // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
    // context for the registers used, where all instructions below are using 128-bit mode
    // On EVEX without VL and BW, these instructions will all be AVX.
    if (VM_Version::supports_avx512vlbw()) {
      __ movl(rax, 0xffff);
      __ kmovql(k1, rax);
    }

#ifdef _WIN64
    // save the xmm registers which must be preserved 6-10
    __ subptr(rsp, -rsp_after_call_off * wordSize);
    for (int i = 6; i <= XMM_REG_LAST; i++) {
      __ movdqu(xmm_save(i), as_XMMRegister(i));
    }
#endif

    __ movdqu(xmm_temp10, ExternalAddress(StubRoutines::x86::ghash_long_swap_mask_addr()));

    __ movdqu(xmm_temp0, Address(state, 0));
    __ pshufb(xmm_temp0, xmm_temp10);


    __ BIND(L_ghash_loop);
    __ movdqu(xmm_temp2, Address(data, 0));
    __ pshufb(xmm_temp2, ExternalAddress(StubRoutines::x86::ghash_byte_swap_mask_addr()));

    __ movdqu(xmm_temp1, Address(subkeyH, 0));
    __ pshufb(xmm_temp1, xmm_temp10);

    __ pxor(xmm_temp0, xmm_temp2);

    //
    // Multiply with the hash key
    //
    __ movdqu(xmm_temp3, xmm_temp0);
    __ pclmulqdq(xmm_temp3, xmm_temp1, 0);      // xmm3 holds a0*b0
    __ movdqu(xmm_temp4, xmm_temp0);
    __ pclmulqdq(xmm_temp4, xmm_temp1, 16);     // xmm4 holds a0*b1

    __ movdqu(xmm_temp5, xmm_temp0);
    __ pclmulqdq(xmm_temp5, xmm_temp1, 1);      // xmm5 holds a1*b0
    __ movdqu(xmm_temp6, xmm_temp0);
    __ pclmulqdq(xmm_temp6, xmm_temp1, 17);     // xmm6 holds a1*b1

    __ pxor(xmm_temp4, xmm_temp5);      // xmm4 holds a0*b1 + a1*b0

    __ movdqu(xmm_temp5, xmm_temp4);    // move the contents of xmm4 to xmm5
    __ psrldq(xmm_temp4, 8);    // shift by xmm4 64 bits to the right
    __ pslldq(xmm_temp5, 8);    // shift by xmm5 64 bits to the left
    __ pxor(xmm_temp3, xmm_temp5);
    __ pxor(xmm_temp6, xmm_temp4);      // Register pair <xmm6:xmm3> holds the result
                                        // of the carry-less multiplication of
                                        // xmm0 by xmm1.

    // We shift the result of the multiplication by one bit position
    // to the left to cope for the fact that the bits are reversed.
    __ movdqu(xmm_temp7, xmm_temp3);
    __ movdqu(xmm_temp8, xmm_temp6);
    __ pslld(xmm_temp3, 1);
    __ pslld(xmm_temp6, 1);
    __ psrld(xmm_temp7, 31);
    __ psrld(xmm_temp8, 31);
    __ movdqu(xmm_temp9, xmm_temp7);
    __ pslldq(xmm_temp8, 4);
    __ pslldq(xmm_temp7, 4);
    __ psrldq(xmm_temp9, 12);
    __ por(xmm_temp3, xmm_temp7);
    __ por(xmm_temp6, xmm_temp8);
    __ por(xmm_temp6, xmm_temp9);

    //
    // First phase of the reduction
    //
    // Move xmm3 into xmm7, xmm8, xmm9 in order to perform the shifts
    // independently.
    __ movdqu(xmm_temp7, xmm_temp3);
    __ movdqu(xmm_temp8, xmm_temp3);
    __ movdqu(xmm_temp9, xmm_temp3);
    __ pslld(xmm_temp7, 31);    // packed right shift shifting << 31
    __ pslld(xmm_temp8, 30);    // packed right shift shifting << 30
    __ pslld(xmm_temp9, 25);    // packed right shift shifting << 25
    __ pxor(xmm_temp7, xmm_temp8);      // xor the shifted versions
    __ pxor(xmm_temp7, xmm_temp9);
    __ movdqu(xmm_temp8, xmm_temp7);
    __ pslldq(xmm_temp7, 12);
    __ psrldq(xmm_temp8, 4);
    __ pxor(xmm_temp3, xmm_temp7);      // first phase of the reduction complete

    //
    // Second phase of the reduction
    //
    // Make 3 copies of xmm3 in xmm2, xmm4, xmm5 for doing these
    // shift operations.
    __ movdqu(xmm_temp2, xmm_temp3);
    __ movdqu(xmm_temp4, xmm_temp3);
    __ movdqu(xmm_temp5, xmm_temp3);
    __ psrld(xmm_temp2, 1);     // packed left shifting >> 1
    __ psrld(xmm_temp4, 2);     // packed left shifting >> 2
    __ psrld(xmm_temp5, 7);     // packed left shifting >> 7
    __ pxor(xmm_temp2, xmm_temp4);      // xor the shifted versions
    __ pxor(xmm_temp2, xmm_temp5);
    __ pxor(xmm_temp2, xmm_temp8);
    __ pxor(xmm_temp3, xmm_temp2);
    __ pxor(xmm_temp6, xmm_temp3);      // the result is in xmm6

    __ decrement(blocks);
    __ jcc(Assembler::zero, L_exit);
    __ movdqu(xmm_temp0, xmm_temp6);
    __ addptr(data, 16);
    __ jmp(L_ghash_loop);

    __ BIND(L_exit);
    __ pshufb(xmm_temp6, xmm_temp10);          // Byte swap 16-byte result
    __ movdqu(Address(state, 0), xmm_temp6);   // store the result

#ifdef _WIN64
    // restore xmm regs belonging to calling function
    for (int i = 6; i <= XMM_REG_LAST; i++) {
      __ movdqu(as_XMMRegister(i), xmm_save(i));
    }
#endif
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
   * Ouput:
   *       rax   - int crc result
   */
  address generate_updateBytesCRC32() {
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
    const Register tmp   = r11;
    assert_different_registers(crc, buf, len, table, tmp, rax);

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame

    __ kernel_crc32(crc, buf, len, table, tmp);

    __ movl(rax, crc);
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
  *   c_rarg3   - table_start - optional (present only when doing a library_calll,
  *              not used by x86 algorithm)
  *
  * Ouput:
  *       rax   - int crc result
  */
  address generate_updateBytesCRC32C(bool is_pclmulqdq_supported) {
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
#ifdef _WIN64
      __ push(y);
      __ push(z);
#endif
      __ crc32c_ipl_alg2_alt2(crc, buf, len,
                              a, j, k,
                              l, y, z,
                              c_farg0, c_farg1, c_farg2,
                              is_pclmulqdq_supported);
      __ movl(rax, crc);
#ifdef _WIN64
      __ pop(z);
      __ pop(y);
#endif
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
   *    c_rarg3   - y lenth
   * not Win64
   *    c_rarg4   - z address
   *    c_rarg5   - z length
   * Win64
   *    rsp+40    - z address
   *    rsp+48    - z length
   */
  address generate_multiplyToLen() {
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
  */
  address generate_vectorizedMismatch() {
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
  //    c_rarg3   - z lenth
   *
   */
  address generate_squareToLen() {

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
  address generate_mulAdd() {
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

  address generate_libmExp() {
    address start = __ pc();

    const XMMRegister x0  = xmm0;
    const XMMRegister x1  = xmm1;
    const XMMRegister x2  = xmm2;
    const XMMRegister x3  = xmm3;

    const XMMRegister x4  = xmm4;
    const XMMRegister x5  = xmm5;
    const XMMRegister x6  = xmm6;
    const XMMRegister x7  = xmm7;

    const Register tmp   = r11;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
    // save the xmm registers which must be preserved 6-7
    __ subptr(rsp, 4 * wordSize);
    __ movdqu(Address(rsp, 0), xmm6);
    __ movdqu(Address(rsp, 2 * wordSize), xmm7);
#endif
      __ fast_exp(x0, x1, x2, x3, x4, x5, x6, x7, rax, rcx, rdx, tmp);

#ifdef _WIN64
    // restore xmm regs belonging to calling function
      __ movdqu(xmm6, Address(rsp, 0));
      __ movdqu(xmm7, Address(rsp, 2 * wordSize));
      __ addptr(rsp, 4 * wordSize);
#endif

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;

  }

  address generate_libmLog() {
    address start = __ pc();

    const XMMRegister x0 = xmm0;
    const XMMRegister x1 = xmm1;
    const XMMRegister x2 = xmm2;
    const XMMRegister x3 = xmm3;

    const XMMRegister x4 = xmm4;
    const XMMRegister x5 = xmm5;
    const XMMRegister x6 = xmm6;
    const XMMRegister x7 = xmm7;

    const Register tmp1 = r11;
    const Register tmp2 = r8;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
    // save the xmm registers which must be preserved 6-7
    __ subptr(rsp, 4 * wordSize);
    __ movdqu(Address(rsp, 0), xmm6);
    __ movdqu(Address(rsp, 2 * wordSize), xmm7);
#endif
    __ fast_log(x0, x1, x2, x3, x4, x5, x6, x7, rax, rcx, rdx, tmp1, tmp2);

#ifdef _WIN64
    // restore xmm regs belonging to calling function
    __ movdqu(xmm6, Address(rsp, 0));
    __ movdqu(xmm7, Address(rsp, 2 * wordSize));
    __ addptr(rsp, 4 * wordSize);
#endif

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;

  }

  address generate_libmPow() {
    address start = __ pc();

    const XMMRegister x0 = xmm0;
    const XMMRegister x1 = xmm1;
    const XMMRegister x2 = xmm2;
    const XMMRegister x3 = xmm3;

    const XMMRegister x4 = xmm4;
    const XMMRegister x5 = xmm5;
    const XMMRegister x6 = xmm6;
    const XMMRegister x7 = xmm7;

    const Register tmp1 = r8;
    const Register tmp2 = r9;
    const Register tmp3 = r10;
    const Register tmp4 = r11;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
    // save the xmm registers which must be preserved 6-7
    __ subptr(rsp, 4 * wordSize);
    __ movdqu(Address(rsp, 0), xmm6);
    __ movdqu(Address(rsp, 2 * wordSize), xmm7);
#endif
    __ fast_pow(x0, x1, x2, x3, x4, x5, x6, x7, rax, rcx, rdx, tmp1, tmp2, tmp3, tmp4);

#ifdef _WIN64
    // restore xmm regs belonging to calling function
    __ movdqu(xmm6, Address(rsp, 0));
    __ movdqu(xmm7, Address(rsp, 2 * wordSize));
    __ addptr(rsp, 4 * wordSize);
#endif

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;

  }

  address generate_libmSin() {
    address start = __ pc();

    const XMMRegister x0 = xmm0;
    const XMMRegister x1 = xmm1;
    const XMMRegister x2 = xmm2;
    const XMMRegister x3 = xmm3;

    const XMMRegister x4 = xmm4;
    const XMMRegister x5 = xmm5;
    const XMMRegister x6 = xmm6;
    const XMMRegister x7 = xmm7;

    const Register tmp1 = r8;
    const Register tmp2 = r9;
    const Register tmp3 = r10;
    const Register tmp4 = r11;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
    // save the xmm registers which must be preserved 6-7
    __ subptr(rsp, 4 * wordSize);
    __ movdqu(Address(rsp, 0), xmm6);
    __ movdqu(Address(rsp, 2 * wordSize), xmm7);
#endif
    __ fast_sin(x0, x1, x2, x3, x4, x5, x6, x7, rax, rbx, rcx, rdx, tmp1, tmp2, tmp3, tmp4);

#ifdef _WIN64
    // restore xmm regs belonging to calling function
    __ movdqu(xmm6, Address(rsp, 0));
    __ movdqu(xmm7, Address(rsp, 2 * wordSize));
    __ addptr(rsp, 4 * wordSize);
#endif

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;

  }

  address generate_libmCos() {
    address start = __ pc();

    const XMMRegister x0 = xmm0;
    const XMMRegister x1 = xmm1;
    const XMMRegister x2 = xmm2;
    const XMMRegister x3 = xmm3;

    const XMMRegister x4 = xmm4;
    const XMMRegister x5 = xmm5;
    const XMMRegister x6 = xmm6;
    const XMMRegister x7 = xmm7;

    const Register tmp1 = r8;
    const Register tmp2 = r9;
    const Register tmp3 = r10;
    const Register tmp4 = r11;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
    // save the xmm registers which must be preserved 6-7
    __ subptr(rsp, 4 * wordSize);
    __ movdqu(Address(rsp, 0), xmm6);
    __ movdqu(Address(rsp, 2 * wordSize), xmm7);
#endif
    __ fast_cos(x0, x1, x2, x3, x4, x5, x6, x7, rax, rcx, rdx, tmp1, tmp2, tmp3, tmp4);

#ifdef _WIN64
    // restore xmm regs belonging to calling function
    __ movdqu(xmm6, Address(rsp, 0));
    __ movdqu(xmm7, Address(rsp, 2 * wordSize));
    __ addptr(rsp, 4 * wordSize);
#endif

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    return start;

  }

#undef __
#define __ masm->

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
  address generate_throw_exception(const char* name,
                                   address runtime_entry,
                                   Register arg1 = noreg,
                                   Register arg2 = noreg) {
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
    MacroAssembler* masm = new MacroAssembler(&code);

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
    __ set_last_Java_frame(rsp, rbp, the_pc);
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

    __ reset_last_Java_frame(true, true);

    __ leave(); // required for proper stackwalking of RuntimeStub frame

    // check for pending exceptions
#ifdef ASSERT
    Label L;
    __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()),
            (int32_t) NULL_WORD);
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

  // Initialization
  void generate_initial() {
    // Generates all stubs and initializes the entry points

    // This platform-specific settings are needed by generate_call_stub()
    create_control_words();

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
    StubRoutines::_atomic_xchg_entry         = generate_atomic_xchg();
    StubRoutines::_atomic_xchg_ptr_entry     = generate_atomic_xchg_ptr();
    StubRoutines::_atomic_cmpxchg_entry      = generate_atomic_cmpxchg();
    StubRoutines::_atomic_cmpxchg_byte_entry = generate_atomic_cmpxchg_byte();
    StubRoutines::_atomic_cmpxchg_long_entry = generate_atomic_cmpxchg_long();
    StubRoutines::_atomic_add_entry          = generate_atomic_add();
    StubRoutines::_atomic_add_ptr_entry      = generate_atomic_add_ptr();
    StubRoutines::_fence_entry               = generate_orderaccess_fence();

    StubRoutines::_handler_for_unsafe_access_entry =
      generate_handler_for_unsafe_access();

    // platform dependent
    StubRoutines::x86::_get_previous_fp_entry = generate_get_previous_fp();
    StubRoutines::x86::_get_previous_sp_entry = generate_get_previous_sp();

    StubRoutines::x86::_verify_mxcsr_entry    = generate_verify_mxcsr();

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
    if (VM_Version::supports_sse2()) {
      StubRoutines::_dexp = generate_libmExp();
      StubRoutines::_dlog = generate_libmLog();
      StubRoutines::_dpow = generate_libmPow();
      if (UseLibmSinIntrinsic) {
        StubRoutines::_dsin = generate_libmSin();
      }
      if (UseLibmCosIntrinsic) {
        StubRoutines::_dcos = generate_libmCos();
      }
    }
  }

  void generate_all() {
    // Generates all stubs and initializes the entry points

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

    // entry points that are platform specific
    StubRoutines::x86::_f2i_fixup = generate_f2i_fixup();
    StubRoutines::x86::_f2l_fixup = generate_f2l_fixup();
    StubRoutines::x86::_d2i_fixup = generate_d2i_fixup();
    StubRoutines::x86::_d2l_fixup = generate_d2l_fixup();

    StubRoutines::x86::_float_sign_mask  = generate_fp_mask("float_sign_mask",  0x7FFFFFFF7FFFFFFF);
    StubRoutines::x86::_float_sign_flip  = generate_fp_mask("float_sign_flip",  0x8000000080000000);
    StubRoutines::x86::_double_sign_mask = generate_fp_mask("double_sign_mask", 0x7FFFFFFFFFFFFFFF);
    StubRoutines::x86::_double_sign_flip = generate_fp_mask("double_sign_flip", 0x8000000000000000);

    // support for verify_oop (must happen after universe_init)
    StubRoutines::_verify_oop_subroutine_entry = generate_verify_oop();

    // arraycopy stubs used by compilers
    generate_arraycopy_stubs();

    generate_math_stubs();

    // don't bother generating these AES intrinsic stubs unless global flag is set
    if (UseAESIntrinsics) {
      StubRoutines::x86::_key_shuffle_mask_addr = generate_key_shuffle_mask();  // needed by the others
      StubRoutines::_aescrypt_encryptBlock = generate_aescrypt_encryptBlock();
      StubRoutines::_aescrypt_decryptBlock = generate_aescrypt_decryptBlock();
      StubRoutines::_cipherBlockChaining_encryptAESCrypt = generate_cipherBlockChaining_encryptAESCrypt();
      StubRoutines::_cipherBlockChaining_decryptAESCrypt = generate_cipherBlockChaining_decryptAESCrypt_Parallel();
    }
    if (UseAESCTRIntrinsics){
      StubRoutines::x86::_counter_shuffle_mask_addr = generate_counter_shuffle_mask();
      StubRoutines::_counterMode_AESCrypt = generate_counterMode_AESCrypt_Parallel();
    }

    if (UseSHA1Intrinsics) {
      StubRoutines::x86::_upper_word_mask_addr = generate_upper_word_mask();
      StubRoutines::x86::_shuffle_byte_flip_mask_addr = generate_shuffle_byte_flip_mask();
      StubRoutines::_sha1_implCompress = generate_sha1_implCompress(false, "sha1_implCompress");
      StubRoutines::_sha1_implCompressMB = generate_sha1_implCompress(true, "sha1_implCompressMB");
    }
    if (UseSHA256Intrinsics) {
      StubRoutines::x86::_k256_adr = (address)StubRoutines::x86::_k256;
      StubRoutines::x86::_pshuffle_byte_flip_mask_addr = generate_pshuffle_byte_flip_mask();
      StubRoutines::_sha256_implCompress = generate_sha256_implCompress(false, "sha256_implCompress");
      StubRoutines::_sha256_implCompressMB = generate_sha256_implCompress(true, "sha256_implCompressMB");
    }

    // Generate GHASH intrinsics code
    if (UseGHASHIntrinsics) {
      StubRoutines::x86::_ghash_long_swap_mask_addr = generate_ghash_long_swap_mask();
      StubRoutines::x86::_ghash_byte_swap_mask_addr = generate_ghash_byte_swap_mask();
      StubRoutines::_ghash_processBlocks = generate_ghash_processBlocks();
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
    if (UseSquareToLenIntrinsic) {
      StubRoutines::_squareToLen = generate_squareToLen();
    }
    if (UseMulAddIntrinsic) {
      StubRoutines::_mulAdd = generate_mulAdd();
    }
    if (UseVectorizedMismatchIntrinsic) {
      StubRoutines::_vectorizedMismatch = generate_vectorizedMismatch();
    }
#ifndef _WINDOWS
    if (UseMontgomeryMultiplyIntrinsic) {
      StubRoutines::_montgomeryMultiply
        = CAST_FROM_FN_PTR(address, SharedRuntime::montgomery_multiply);
    }
    if (UseMontgomerySquareIntrinsic) {
      StubRoutines::_montgomerySquare
        = CAST_FROM_FN_PTR(address, SharedRuntime::montgomery_square);
    }
#endif // WINDOWS
#endif // COMPILER2
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
