/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "compiler/oopMap.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/universe.hpp"
#include "nativeInst_riscv.hpp"
#include "oops/instanceOop.hpp"
#include "oops/method.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "prims/upcallLinker.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/align.hpp"
#include "utilities/powerOfTwo.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// Declaration and definition of StubGenerator (no .hpp file).
// For a more detailed description of the stub routine structure
// see the comment in stubRoutines.hpp

#undef __
#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

// Stub Code definitions

class StubGenerator: public StubCodeGenerator {
 private:

#ifdef PRODUCT
#define inc_counter_np(counter) ((void)0)
#else
  void inc_counter_np_(uint& counter) {
    __ incrementw(ExternalAddress((address)&counter));
  }
#define inc_counter_np(counter) \
  BLOCK_COMMENT("inc_counter " #counter); \
  inc_counter_np_(counter);
#endif

  // Call stubs are used to call Java from C
  //
  // Arguments:
  //    c_rarg0:   call wrapper address                   address
  //    c_rarg1:   result                                 address
  //    c_rarg2:   result type                            BasicType
  //    c_rarg3:   method                                 Method*
  //    c_rarg4:   (interpreter) entry point              address
  //    c_rarg5:   parameters                             intptr_t*
  //    c_rarg6:   parameter size (in words)              int
  //    c_rarg7:   thread                                 Thread*
  //
  // There is no return from the stub itself as any Java result
  // is written to result
  //
  // we save x1 (ra) as the return PC at the base of the frame and
  // link x8 (fp) below it as the frame pointer installing sp (x2)
  // into fp.
  //
  // we save x10-x17, which accounts for all the c arguments.
  //
  // TODO: strictly do we need to save them all? they are treated as
  // volatile by C so could we omit saving the ones we are going to
  // place in global registers (thread? method?) or those we only use
  // during setup of the Java call?
  //
  // we don't need to save x5 which C uses as an indirect result location
  // return register.
  //
  // we don't need to save x6-x7 and x28-x31 which both C and Java treat as
  // volatile
  //
  // we save x9, x18-x27, f8-f9, and f18-f27 which Java uses as temporary
  // registers and C expects to be callee-save
  //
  // so the stub frame looks like this when we enter Java code
  //
  //     [ return_from_Java     ] <--- sp
  //     [ argument word n      ]
  //      ...
  // -35 [ argument word 1      ]
  // -34 [ saved FRM in Floating-point Control and Status Register ] <--- sp_after_call
  // -33 [ saved f27            ]
  // -32 [ saved f26            ]
  // -31 [ saved f25            ]
  // -30 [ saved f24            ]
  // -29 [ saved f23            ]
  // -28 [ saved f22            ]
  // -27 [ saved f21            ]
  // -26 [ saved f20            ]
  // -25 [ saved f19            ]
  // -24 [ saved f18            ]
  // -23 [ saved f9             ]
  // -22 [ saved f8             ]
  // -21 [ saved x27            ]
  // -20 [ saved x26            ]
  // -19 [ saved x25            ]
  // -18 [ saved x24            ]
  // -17 [ saved x23            ]
  // -16 [ saved x22            ]
  // -15 [ saved x21            ]
  // -14 [ saved x20            ]
  // -13 [ saved x19            ]
  // -12 [ saved x18            ]
  // -11 [ saved x9             ]
  // -10 [ call wrapper   (x10) ]
  //  -9 [ result         (x11) ]
  //  -8 [ result type    (x12) ]
  //  -7 [ method         (x13) ]
  //  -6 [ entry point    (x14) ]
  //  -5 [ parameters     (x15) ]
  //  -4 [ parameter size (x16) ]
  //  -3 [ thread         (x17) ]
  //  -2 [ saved fp       (x8)  ]
  //  -1 [ saved ra       (x1)  ]
  //   0 [                      ] <--- fp == saved sp (x2)

  // Call stub stack layout word offsets from fp
  enum call_stub_layout {
    sp_after_call_off  = -34,

    frm_off            = sp_after_call_off,
    f27_off            = -33,
    f26_off            = -32,
    f25_off            = -31,
    f24_off            = -30,
    f23_off            = -29,
    f22_off            = -28,
    f21_off            = -27,
    f20_off            = -26,
    f19_off            = -25,
    f18_off            = -24,
    f9_off             = -23,
    f8_off             = -22,

    x27_off            = -21,
    x26_off            = -20,
    x25_off            = -19,
    x24_off            = -18,
    x23_off            = -17,
    x22_off            = -16,
    x21_off            = -15,
    x20_off            = -14,
    x19_off            = -13,
    x18_off            = -12,
    x9_off             = -11,

    call_wrapper_off   = -10,
    result_off         = -9,
    result_type_off    = -8,
    method_off         = -7,
    entry_point_off    = -6,
    parameters_off     = -5,
    parameter_size_off = -4,
    thread_off         = -3,
    fp_f               = -2,
    retaddr_off        = -1,
  };

  address generate_call_stub(address& return_address) {
    assert((int)frame::entry_frame_after_call_words == -(int)sp_after_call_off + 1 &&
           (int)frame::entry_frame_call_wrapper_offset == (int)call_wrapper_off,
           "adjust this code");

    StubCodeMark mark(this, "StubRoutines", "call_stub");
    address start = __ pc();

    const Address sp_after_call (fp, sp_after_call_off  * wordSize);

    const Address frm_save      (fp, frm_off           * wordSize);
    const Address call_wrapper  (fp, call_wrapper_off   * wordSize);
    const Address result        (fp, result_off         * wordSize);
    const Address result_type   (fp, result_type_off    * wordSize);
    const Address method        (fp, method_off         * wordSize);
    const Address entry_point   (fp, entry_point_off    * wordSize);
    const Address parameters    (fp, parameters_off     * wordSize);
    const Address parameter_size(fp, parameter_size_off * wordSize);

    const Address thread        (fp, thread_off         * wordSize);

    const Address f27_save      (fp, f27_off            * wordSize);
    const Address f26_save      (fp, f26_off            * wordSize);
    const Address f25_save      (fp, f25_off            * wordSize);
    const Address f24_save      (fp, f24_off            * wordSize);
    const Address f23_save      (fp, f23_off            * wordSize);
    const Address f22_save      (fp, f22_off            * wordSize);
    const Address f21_save      (fp, f21_off            * wordSize);
    const Address f20_save      (fp, f20_off            * wordSize);
    const Address f19_save      (fp, f19_off            * wordSize);
    const Address f18_save      (fp, f18_off            * wordSize);
    const Address f9_save       (fp, f9_off             * wordSize);
    const Address f8_save       (fp, f8_off             * wordSize);

    const Address x27_save      (fp, x27_off            * wordSize);
    const Address x26_save      (fp, x26_off            * wordSize);
    const Address x25_save      (fp, x25_off            * wordSize);
    const Address x24_save      (fp, x24_off            * wordSize);
    const Address x23_save      (fp, x23_off            * wordSize);
    const Address x22_save      (fp, x22_off            * wordSize);
    const Address x21_save      (fp, x21_off            * wordSize);
    const Address x20_save      (fp, x20_off            * wordSize);
    const Address x19_save      (fp, x19_off            * wordSize);
    const Address x18_save      (fp, x18_off            * wordSize);

    const Address x9_save       (fp, x9_off             * wordSize);

    // stub code

    address riscv_entry = __ pc();

    // set up frame and move sp to end of save area
    __ enter();
    __ addi(sp, fp, sp_after_call_off * wordSize);

    // save register parameters and Java temporary/global registers
    // n.b. we save thread even though it gets installed in
    // xthread because we want to sanity check tp later
    __ sd(c_rarg7, thread);
    __ sw(c_rarg6, parameter_size);
    __ sd(c_rarg5, parameters);
    __ sd(c_rarg4, entry_point);
    __ sd(c_rarg3, method);
    __ sd(c_rarg2, result_type);
    __ sd(c_rarg1, result);
    __ sd(c_rarg0, call_wrapper);

    __ sd(x9, x9_save);

    __ sd(x18, x18_save);
    __ sd(x19, x19_save);
    __ sd(x20, x20_save);
    __ sd(x21, x21_save);
    __ sd(x22, x22_save);
    __ sd(x23, x23_save);
    __ sd(x24, x24_save);
    __ sd(x25, x25_save);
    __ sd(x26, x26_save);
    __ sd(x27, x27_save);

    __ fsd(f8,  f8_save);
    __ fsd(f9,  f9_save);
    __ fsd(f18, f18_save);
    __ fsd(f19, f19_save);
    __ fsd(f20, f20_save);
    __ fsd(f21, f21_save);
    __ fsd(f22, f22_save);
    __ fsd(f23, f23_save);
    __ fsd(f24, f24_save);
    __ fsd(f25, f25_save);
    __ fsd(f26, f26_save);
    __ fsd(f27, f27_save);

    __ frrm(t0);
    __ sd(t0, frm_save);
    // Set frm to the state we need. We do want Round to Nearest. We
    // don't want non-IEEE rounding modes.
    Label skip_fsrmi;
    guarantee(__ RoundingMode::rne == 0, "must be");
    __ beqz(t0, skip_fsrmi);
    __ fsrmi(__ RoundingMode::rne);
    __ bind(skip_fsrmi);

    // install Java thread in global register now we have saved
    // whatever value it held
    __ mv(xthread, c_rarg7);

    // And method
    __ mv(xmethod, c_rarg3);

    // set up the heapbase register
    __ reinit_heapbase();

#ifdef ASSERT
    // make sure we have no pending exceptions
    {
      Label L;
      __ ld(t0, Address(xthread, in_bytes(Thread::pending_exception_offset())));
      __ beqz(t0, L);
      __ stop("StubRoutines::call_stub: entered with pending exception");
      __ BIND(L);
    }
#endif
    // pass parameters if any
    __ mv(esp, sp);
    __ slli(t0, c_rarg6, LogBytesPerWord);
    __ sub(t0, sp, t0); // Move SP out of the way
    __ andi(sp, t0, -2 * wordSize);

    BLOCK_COMMENT("pass parameters if any");
    Label parameters_done;
    // parameter count is still in c_rarg6
    // and parameter pointer identifying param 1 is in c_rarg5
    __ beqz(c_rarg6, parameters_done);

    address loop = __ pc();
    __ ld(t0, Address(c_rarg5, 0));
    __ addi(c_rarg5, c_rarg5, wordSize);
    __ addi(c_rarg6, c_rarg6, -1);
    __ push_reg(t0);
    __ bgtz(c_rarg6, loop);

    __ BIND(parameters_done);

    // call Java entry -- passing methdoOop, and current sp
    //      xmethod: Method*
    //      x19_sender_sp: sender sp
    BLOCK_COMMENT("call Java function");
    __ mv(x19_sender_sp, sp);
    __ jalr(c_rarg4);

    // save current address for use by exception handling code

    return_address = __ pc();

    // store result depending on type (everything that is not
    // T_OBJECT, T_LONG, T_FLOAT or T_DOUBLE is treated as T_INT)
    // n.b. this assumes Java returns an integral result in x10
    // and a floating result in j_farg0
    __ ld(j_rarg2, result);
    Label is_long, is_float, is_double, exit;
    __ ld(j_rarg1, result_type);
    __ mv(t0, (u1)T_OBJECT);
    __ beq(j_rarg1, t0, is_long);
    __ mv(t0, (u1)T_LONG);
    __ beq(j_rarg1, t0, is_long);
    __ mv(t0, (u1)T_FLOAT);
    __ beq(j_rarg1, t0, is_float);
    __ mv(t0, (u1)T_DOUBLE);
    __ beq(j_rarg1, t0, is_double);

    // handle T_INT case
    __ sw(x10, Address(j_rarg2));

    __ BIND(exit);

    // pop parameters
    __ addi(esp, fp, sp_after_call_off * wordSize);

#ifdef ASSERT
    // verify that threads correspond
    {
      Label L, S;
      __ ld(t0, thread);
      __ bne(xthread, t0, S);
      __ get_thread(t0);
      __ beq(xthread, t0, L);
      __ BIND(S);
      __ stop("StubRoutines::call_stub: threads must correspond");
      __ BIND(L);
    }
#endif

    __ pop_cont_fastpath(xthread);

    // restore callee-save registers
    __ fld(f27, f27_save);
    __ fld(f26, f26_save);
    __ fld(f25, f25_save);
    __ fld(f24, f24_save);
    __ fld(f23, f23_save);
    __ fld(f22, f22_save);
    __ fld(f21, f21_save);
    __ fld(f20, f20_save);
    __ fld(f19, f19_save);
    __ fld(f18, f18_save);
    __ fld(f9,  f9_save);
    __ fld(f8,  f8_save);

    __ ld(x27, x27_save);
    __ ld(x26, x26_save);
    __ ld(x25, x25_save);
    __ ld(x24, x24_save);
    __ ld(x23, x23_save);
    __ ld(x22, x22_save);
    __ ld(x21, x21_save);
    __ ld(x20, x20_save);
    __ ld(x19, x19_save);
    __ ld(x18, x18_save);

    __ ld(x9, x9_save);

    // restore frm
    Label skip_fsrm;
    __ ld(t0, frm_save);
    __ frrm(t1);
    __ beq(t0, t1, skip_fsrm);
    __ fsrm(t0);
    __ bind(skip_fsrm);

    __ ld(c_rarg0, call_wrapper);
    __ ld(c_rarg1, result);
    __ ld(c_rarg2, result_type);
    __ ld(c_rarg3, method);
    __ ld(c_rarg4, entry_point);
    __ ld(c_rarg5, parameters);
    __ ld(c_rarg6, parameter_size);
    __ ld(c_rarg7, thread);

    // leave frame and return to caller
    __ leave();
    __ ret();

    // handle return types different from T_INT

    __ BIND(is_long);
    __ sd(x10, Address(j_rarg2, 0));
    __ j(exit);

    __ BIND(is_float);
    __ fsw(j_farg0, Address(j_rarg2, 0), t0);
    __ j(exit);

    __ BIND(is_double);
    __ fsd(j_farg0, Address(j_rarg2, 0), t0);
    __ j(exit);

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
  // sp.
  //
  // x10: exception oop

  address generate_catch_exception() {
    StubCodeMark mark(this, "StubRoutines", "catch_exception");
    address start = __ pc();

    // same as in generate_call_stub():
    const Address thread(fp, thread_off * wordSize);

#ifdef ASSERT
    // verify that threads correspond
    {
      Label L, S;
      __ ld(t0, thread);
      __ bne(xthread, t0, S);
      __ get_thread(t0);
      __ beq(xthread, t0, L);
      __ bind(S);
      __ stop("StubRoutines::catch_exception: threads must correspond");
      __ bind(L);
    }
#endif

    // set pending exception
    __ verify_oop(x10);

    __ sd(x10, Address(xthread, Thread::pending_exception_offset()));
    __ mv(t0, (address)__FILE__);
    __ sd(t0, Address(xthread, Thread::exception_file_offset()));
    __ mv(t0, (int)__LINE__);
    __ sw(t0, Address(xthread, Thread::exception_line_offset()));

    // complete return to VM
    assert(StubRoutines::_call_stub_return_address != nullptr,
           "_call_stub_return_address must have been generated before");
    __ j(StubRoutines::_call_stub_return_address);

    return start;
  }

  // Continuation point for runtime calls returning with a pending
  // exception.  The pending exception check happened in the runtime
  // or native call stub.  The pending exception in Thread is
  // converted into a Java-level exception.
  //
  // Contract with Java-level exception handlers:
  // x10: exception
  // x13: throwing pc
  //
  // NOTE: At entry of this stub, exception-pc must be in RA !!

  // NOTE: this is always used as a jump target within generated code
  // so it just needs to be generated code with no x86 prolog

  address generate_forward_exception() {
    StubCodeMark mark(this, "StubRoutines", "forward exception");
    address start = __ pc();

    // Upon entry, RA points to the return address returning into
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
      __ ld(t0, Address(xthread, Thread::pending_exception_offset()));
      __ bnez(t0, L);
      __ stop("StubRoutines::forward exception: no pending exception (1)");
      __ bind(L);
    }
#endif

    // compute exception handler into x9

    // call the VM to find the handler address associated with the
    // caller address. pass thread in x10 and caller pc (ret address)
    // in x11. n.b. the caller pc is in ra, unlike x86 where it is on
    // the stack.
    __ mv(c_rarg1, ra);
    // ra will be trashed by the VM call so we move it to x9
    // (callee-saved) because we also need to pass it to the handler
    // returned by this call.
    __ mv(x9, ra);
    BLOCK_COMMENT("call exception_handler_for_return_address");
    __ call_VM_leaf(CAST_FROM_FN_PTR(address,
                         SharedRuntime::exception_handler_for_return_address),
                    xthread, c_rarg1);
    // we should not really care that ra is no longer the callee
    // address. we saved the value the handler needs in x9 so we can
    // just copy it to x13. however, the C2 handler will push its own
    // frame and then calls into the VM and the VM code asserts that
    // the PC for the frame above the handler belongs to a compiled
    // Java method. So, we restore ra here to satisfy that assert.
    __ mv(ra, x9);
    // setup x10 & x13 & clear pending exception
    __ mv(x13, x9);
    __ mv(x9, x10);
    __ ld(x10, Address(xthread, Thread::pending_exception_offset()));
    __ sd(zr, Address(xthread, Thread::pending_exception_offset()));

#ifdef ASSERT
    // make sure exception is set
    {
      Label L;
      __ bnez(x10, L);
      __ stop("StubRoutines::forward exception: no pending exception (2)");
      __ bind(L);
    }
#endif

    // continue at exception handler
    // x10: exception
    // x13: throwing pc
    // x9: exception handler
    __ verify_oop(x10);
    __ jr(x9);

    return start;
  }

  // Non-destructive plausibility checks for oops
  //
  // Arguments:
  //    x10: oop to verify
  //    t0: error message
  //
  // Stack after saving c_rarg3:
  //    [tos + 0]: saved c_rarg3
  //    [tos + 1]: saved c_rarg2
  //    [tos + 2]: saved ra
  //    [tos + 3]: saved t1
  //    [tos + 4]: saved x10
  //    [tos + 5]: saved t0
  address generate_verify_oop() {

    StubCodeMark mark(this, "StubRoutines", "verify_oop");
    address start = __ pc();

    Label exit, error;

    __ push_reg(RegSet::of(c_rarg2, c_rarg3), sp); // save c_rarg2 and c_rarg3

    __ la(c_rarg2, ExternalAddress((address) StubRoutines::verify_oop_count_addr()));
    __ ld(c_rarg3, Address(c_rarg2));
    __ add(c_rarg3, c_rarg3, 1);
    __ sd(c_rarg3, Address(c_rarg2));

    // object is in x10
    // make sure object is 'reasonable'
    __ beqz(x10, exit); // if obj is null it is OK

    BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
    bs_asm->check_oop(_masm, x10, c_rarg2, c_rarg3, error);

    // return if everything seems ok
    __ bind(exit);

    __ pop_reg(RegSet::of(c_rarg2, c_rarg3), sp);  // pop c_rarg2 and c_rarg3
    __ ret();

    // handle errors
    __ bind(error);
    __ pop_reg(RegSet::of(c_rarg2, c_rarg3), sp); // pop c_rarg2 and c_rarg3

    __ push_reg(RegSet::range(x0, x31), sp);
    // debug(char* msg, int64_t pc, int64_t regs[])
    __ mv(c_rarg0, t0);             // pass address of error message
    __ mv(c_rarg1, ra);             // pass return address
    __ mv(c_rarg2, sp);             // pass address of regs on stack
#ifndef PRODUCT
    assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
#endif
    BLOCK_COMMENT("call MacroAssembler::debug");
    __ rt_call(CAST_FROM_FN_PTR(address, MacroAssembler::debug64));
    __ ebreak();

    return start;
  }

  // The inner part of zero_words().
  //
  // Inputs:
  // x28: the HeapWord-aligned base address of an array to zero.
  // x29: the count in HeapWords, x29 > 0.
  //
  // Returns x28 and x29, adjusted for the caller to clear.
  // x28: the base address of the tail of words left to clear.
  // x29: the number of words in the tail.
  //      x29 < MacroAssembler::zero_words_block_size.

  address generate_zero_blocks() {
    Label done;

    const Register base = x28, cnt = x29, tmp1 = x30, tmp2 = x31;

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "zero_blocks");
    address start = __ pc();

    if (UseBlockZeroing) {
      // Ensure count >= 2*CacheLineSize so that it still deserves a cbo.zero
      // after alignment.
      Label small;
      int low_limit = MAX2(2 * CacheLineSize, BlockZeroingLowLimit) / wordSize;
      __ mv(tmp1, low_limit);
      __ blt(cnt, tmp1, small);
      __ zero_dcache_blocks(base, cnt, tmp1, tmp2);
      __ bind(small);
    }

    {
      // Clear the remaining blocks.
      Label loop;
      __ mv(tmp1, MacroAssembler::zero_words_block_size);
      __ blt(cnt, tmp1, done);
      __ bind(loop);
      for (int i = 0; i < MacroAssembler::zero_words_block_size; i++) {
        __ sd(zr, Address(base, i * wordSize));
      }
      __ add(base, base, MacroAssembler::zero_words_block_size * wordSize);
      __ sub(cnt, cnt, MacroAssembler::zero_words_block_size);
      __ bge(cnt, tmp1, loop);
      __ bind(done);
    }

    __ ret();

    return start;
  }

  typedef enum {
    copy_forwards = 1,
    copy_backwards = -1
  } copy_direction;

  // Bulk copy of blocks of 8 words.
  //
  // count is a count of words.
  //
  // Precondition: count >= 8
  //
  // Postconditions:
  //
  // The least significant bit of count contains the remaining count
  // of words to copy.  The rest of count is trash.
  //
  // s and d are adjusted to point to the remaining words to copy
  //
  void generate_copy_longs(Label &start, Register s, Register d, Register count,
                           copy_direction direction) {
    int unit = wordSize * direction;
    int bias = wordSize;

    const Register tmp_reg0 = x13, tmp_reg1 = x14, tmp_reg2 = x15, tmp_reg3 = x16,
      tmp_reg4 = x17, tmp_reg5 = x7, tmp_reg6 = x28, tmp_reg7 = x29;

    const Register stride = x30;

    assert_different_registers(t0, tmp_reg0, tmp_reg1, tmp_reg2, tmp_reg3,
      tmp_reg4, tmp_reg5, tmp_reg6, tmp_reg7);
    assert_different_registers(s, d, count, t0);

    Label again, drain;
    const char* stub_name = nullptr;
    if (direction == copy_forwards) {
      stub_name = "forward_copy_longs";
    } else {
      stub_name = "backward_copy_longs";
    }
    StubCodeMark mark(this, "StubRoutines", stub_name);
    __ align(CodeEntryAlignment);
    __ bind(start);

    if (direction == copy_forwards) {
      __ sub(s, s, bias);
      __ sub(d, d, bias);
    }

#ifdef ASSERT
    // Make sure we are never given < 8 words
    {
      Label L;

      __ mv(t0, 8);
      __ bge(count, t0, L);
      __ stop("genrate_copy_longs called with < 8 words");
      __ bind(L);
    }
#endif

    __ ld(tmp_reg0, Address(s, 1 * unit));
    __ ld(tmp_reg1, Address(s, 2 * unit));
    __ ld(tmp_reg2, Address(s, 3 * unit));
    __ ld(tmp_reg3, Address(s, 4 * unit));
    __ ld(tmp_reg4, Address(s, 5 * unit));
    __ ld(tmp_reg5, Address(s, 6 * unit));
    __ ld(tmp_reg6, Address(s, 7 * unit));
    __ ld(tmp_reg7, Address(s, 8 * unit));
    __ addi(s, s, 8 * unit);

    __ sub(count, count, 16);
    __ bltz(count, drain);

    __ bind(again);

    __ sd(tmp_reg0, Address(d, 1 * unit));
    __ sd(tmp_reg1, Address(d, 2 * unit));
    __ sd(tmp_reg2, Address(d, 3 * unit));
    __ sd(tmp_reg3, Address(d, 4 * unit));
    __ sd(tmp_reg4, Address(d, 5 * unit));
    __ sd(tmp_reg5, Address(d, 6 * unit));
    __ sd(tmp_reg6, Address(d, 7 * unit));
    __ sd(tmp_reg7, Address(d, 8 * unit));

    __ ld(tmp_reg0, Address(s, 1 * unit));
    __ ld(tmp_reg1, Address(s, 2 * unit));
    __ ld(tmp_reg2, Address(s, 3 * unit));
    __ ld(tmp_reg3, Address(s, 4 * unit));
    __ ld(tmp_reg4, Address(s, 5 * unit));
    __ ld(tmp_reg5, Address(s, 6 * unit));
    __ ld(tmp_reg6, Address(s, 7 * unit));
    __ ld(tmp_reg7, Address(s, 8 * unit));

    __ addi(s, s, 8 * unit);
    __ addi(d, d, 8 * unit);

    __ sub(count, count, 8);
    __ bgez(count, again);

    // Drain
    __ bind(drain);

    __ sd(tmp_reg0, Address(d, 1 * unit));
    __ sd(tmp_reg1, Address(d, 2 * unit));
    __ sd(tmp_reg2, Address(d, 3 * unit));
    __ sd(tmp_reg3, Address(d, 4 * unit));
    __ sd(tmp_reg4, Address(d, 5 * unit));
    __ sd(tmp_reg5, Address(d, 6 * unit));
    __ sd(tmp_reg6, Address(d, 7 * unit));
    __ sd(tmp_reg7, Address(d, 8 * unit));
    __ addi(d, d, 8 * unit);

    {
      Label L1, L2;
      __ test_bit(t0, count, 2);
      __ beqz(t0, L1);

      __ ld(tmp_reg0, Address(s, 1 * unit));
      __ ld(tmp_reg1, Address(s, 2 * unit));
      __ ld(tmp_reg2, Address(s, 3 * unit));
      __ ld(tmp_reg3, Address(s, 4 * unit));
      __ addi(s, s, 4 * unit);

      __ sd(tmp_reg0, Address(d, 1 * unit));
      __ sd(tmp_reg1, Address(d, 2 * unit));
      __ sd(tmp_reg2, Address(d, 3 * unit));
      __ sd(tmp_reg3, Address(d, 4 * unit));
      __ addi(d, d, 4 * unit);

      __ bind(L1);

      if (direction == copy_forwards) {
        __ addi(s, s, bias);
        __ addi(d, d, bias);
      }

      __ test_bit(t0, count, 1);
      __ beqz(t0, L2);
      if (direction == copy_backwards) {
        __ addi(s, s, 2 * unit);
        __ ld(tmp_reg0, Address(s));
        __ ld(tmp_reg1, Address(s, wordSize));
        __ addi(d, d, 2 * unit);
        __ sd(tmp_reg0, Address(d));
        __ sd(tmp_reg1, Address(d, wordSize));
      } else {
        __ ld(tmp_reg0, Address(s));
        __ ld(tmp_reg1, Address(s, wordSize));
        __ addi(s, s, 2 * unit);
        __ sd(tmp_reg0, Address(d));
        __ sd(tmp_reg1, Address(d, wordSize));
        __ addi(d, d, 2 * unit);
      }
      __ bind(L2);
    }

    __ ret();
  }

  Label copy_f, copy_b;

  typedef void (MacroAssembler::*copy_insn)(Register Rd, const Address &adr, Register temp);

  void copy_memory_v(Register s, Register d, Register count, int step) {
    bool is_backward = step < 0;
    int granularity = uabs(step);

    const Register src = x30, dst = x31, vl = x14, cnt = x15, tmp1 = x16, tmp2 = x17;
    assert_different_registers(s, d, cnt, vl, tmp1, tmp2);
    Assembler::SEW sew = Assembler::elembytes_to_sew(granularity);
    Label loop_forward, loop_backward, done;

    __ mv(dst, d);
    __ mv(src, s);
    __ mv(cnt, count);

    __ bind(loop_forward);
    __ vsetvli(vl, cnt, sew, Assembler::m8);
    if (is_backward) {
      __ bne(vl, cnt, loop_backward);
    }

    __ vlex_v(v0, src, sew);
    __ sub(cnt, cnt, vl);
    if (sew != Assembler::e8) {
      // when sew == e8 (e.g., elem size is 1 byte), slli R, R, 0 is a nop and unnecessary
      __ slli(vl, vl, sew);
    }
    __ add(src, src, vl);

    __ vsex_v(v0, dst, sew);
    __ add(dst, dst, vl);
    __ bnez(cnt, loop_forward);

    if (is_backward) {
      __ j(done);

      __ bind(loop_backward);
      __ sub(t0, cnt, vl);
      if (sew != Assembler::e8) {
        // when sew == e8 (e.g., elem size is 1 byte), slli R, R, 0 is a nop and unnecessary
        __ slli(t0, t0, sew);
      }
      __ add(tmp1, s, t0);
      __ vlex_v(v0, tmp1, sew);
      __ add(tmp2, d, t0);
      __ vsex_v(v0, tmp2, sew);
      __ sub(cnt, cnt, vl);
      __ bnez(cnt, loop_forward);
      __ bind(done);
    }
  }

  // All-singing all-dancing memory copy.
  //
  // Copy count units of memory from s to d.  The size of a unit is
  // step, which can be positive or negative depending on the direction
  // of copy.
  //
  void copy_memory(DecoratorSet decorators, BasicType type, bool is_aligned,
                   Register s, Register d, Register count, int step) {
    BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
    if (UseRVV && (!is_reference_type(type) || bs_asm->supports_rvv_arraycopy())) {
      return copy_memory_v(s, d, count, step);
    }

    bool is_backwards = step < 0;
    int granularity = uabs(step);

    const Register src = x30, dst = x31, cnt = x15, tmp3 = x16, tmp4 = x17, tmp5 = x14, tmp6 = x13;
    const Register gct1 = x28, gct2 = x29, gct3 = t2;

    Label same_aligned;
    Label copy_big, copy32_loop, copy8_loop, copy_small, done;

    // The size of copy32_loop body increases significantly with ZGC GC barriers.
    // Need conditional far branches to reach a point beyond the loop in this case.
    bool is_far = UseZGC && ZGenerational;

    __ beqz(count, done, is_far);
    __ slli(cnt, count, exact_log2(granularity));
    if (is_backwards) {
      __ add(src, s, cnt);
      __ add(dst, d, cnt);
    } else {
      __ mv(src, s);
      __ mv(dst, d);
    }

    if (is_aligned) {
      __ addi(t0, cnt, -32);
      __ bgez(t0, copy32_loop);
      __ addi(t0, cnt, -8);
      __ bgez(t0, copy8_loop, is_far);
      __ j(copy_small);
    } else {
      __ mv(t0, 16);
      __ blt(cnt, t0, copy_small, is_far);

      __ xorr(t0, src, dst);
      __ andi(t0, t0, 0b111);
      __ bnez(t0, copy_small, is_far);

      __ bind(same_aligned);
      __ andi(t0, src, 0b111);
      __ beqz(t0, copy_big);
      if (is_backwards) {
        __ addi(src, src, step);
        __ addi(dst, dst, step);
      }
      bs_asm->copy_load_at(_masm, decorators, type, granularity, tmp3, Address(src), gct1);
      bs_asm->copy_store_at(_masm, decorators, type, granularity, Address(dst), tmp3, gct1, gct2, gct3);
      if (!is_backwards) {
        __ addi(src, src, step);
        __ addi(dst, dst, step);
      }
      __ addi(cnt, cnt, -granularity);
      __ beqz(cnt, done, is_far);
      __ j(same_aligned);

      __ bind(copy_big);
      __ mv(t0, 32);
      __ blt(cnt, t0, copy8_loop, is_far);
    }

    __ bind(copy32_loop);
    if (is_backwards) {
      __ addi(src, src, -wordSize * 4);
      __ addi(dst, dst, -wordSize * 4);
    }
    // we first load 32 bytes, then write it, so the direction here doesn't matter
    bs_asm->copy_load_at(_masm, decorators, type, 8, tmp3, Address(src),     gct1);
    bs_asm->copy_load_at(_masm, decorators, type, 8, tmp4, Address(src, 8),  gct1);
    bs_asm->copy_load_at(_masm, decorators, type, 8, tmp5, Address(src, 16), gct1);
    bs_asm->copy_load_at(_masm, decorators, type, 8, tmp6, Address(src, 24), gct1);

    bs_asm->copy_store_at(_masm, decorators, type, 8, Address(dst),     tmp3, gct1, gct2, gct3);
    bs_asm->copy_store_at(_masm, decorators, type, 8, Address(dst, 8),  tmp4, gct1, gct2, gct3);
    bs_asm->copy_store_at(_masm, decorators, type, 8, Address(dst, 16), tmp5, gct1, gct2, gct3);
    bs_asm->copy_store_at(_masm, decorators, type, 8, Address(dst, 24), tmp6, gct1, gct2, gct3);

    if (!is_backwards) {
      __ addi(src, src, wordSize * 4);
      __ addi(dst, dst, wordSize * 4);
    }
    __ addi(t0, cnt, -(32 + wordSize * 4));
    __ addi(cnt, cnt, -wordSize * 4);
    __ bgez(t0, copy32_loop); // cnt >= 32, do next loop

    __ beqz(cnt, done); // if that's all - done

    __ addi(t0, cnt, -8); // if not - copy the reminder
    __ bltz(t0, copy_small); // cnt < 8, go to copy_small, else fall through to copy8_loop

    __ bind(copy8_loop);
    if (is_backwards) {
      __ addi(src, src, -wordSize);
      __ addi(dst, dst, -wordSize);
    }
    bs_asm->copy_load_at(_masm, decorators, type, 8, tmp3, Address(src), gct1);
    bs_asm->copy_store_at(_masm, decorators, type, 8, Address(dst), tmp3, gct1, gct2, gct3);

    if (!is_backwards) {
      __ addi(src, src, wordSize);
      __ addi(dst, dst, wordSize);
    }
    __ addi(t0, cnt, -(8 + wordSize));
    __ addi(cnt, cnt, -wordSize);
    __ bgez(t0, copy8_loop); // cnt >= 8, do next loop

    __ beqz(cnt, done); // if that's all - done

    __ bind(copy_small);
    if (is_backwards) {
      __ addi(src, src, step);
      __ addi(dst, dst, step);
    }

    bs_asm->copy_load_at(_masm, decorators, type, granularity, tmp3, Address(src), gct1);
    bs_asm->copy_store_at(_masm, decorators, type, granularity, Address(dst), tmp3, gct1, gct2, gct3);

    if (!is_backwards) {
      __ addi(src, src, step);
      __ addi(dst, dst, step);
    }
    __ addi(cnt, cnt, -granularity);
    __ bgtz(cnt, copy_small);

    __ bind(done);
  }

  // Scan over array at a for count oops, verifying each one.
  // Preserves a and count, clobbers t0 and t1.
  void verify_oop_array(size_t size, Register a, Register count, Register temp) {
    Label loop, end;
    __ mv(t1, zr);
    __ slli(t0, count, exact_log2(size));
    __ bind(loop);
    __ bgeu(t1, t0, end);

    __ add(temp, a, t1);
    if (size == (size_t)wordSize) {
      __ ld(temp, Address(temp, 0));
      __ verify_oop(temp);
    } else {
      __ lwu(temp, Address(temp, 0));
      __ decode_heap_oop(temp); // calls verify_oop
    }
    __ add(t1, t1, size);
    __ j(loop);
    __ bind(end);
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
  // cache line boundaries will still be loaded and stored atomically.
  //
  // Side Effects:
  //   disjoint_int_copy_entry is set to the no-overlap entry point
  //   used by generate_conjoint_int_oop_copy().
  //
  address generate_disjoint_copy(size_t size, bool aligned, bool is_oop, address* entry,
                                 const char* name, bool dest_uninitialized = false) {
    const Register s = c_rarg0, d = c_rarg1, count = c_rarg2;
    RegSet saved_reg = RegSet::of(s, d, count);
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();
    __ enter();

    if (entry != nullptr) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_DISJOINT;
    if (dest_uninitialized) {
      decorators |= IS_DEST_UNINITIALIZED;
    }
    if (aligned) {
      decorators |= ARRAYCOPY_ALIGNED;
    }

    BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
    bs->arraycopy_prologue(_masm, decorators, is_oop, s, d, count, saved_reg);

    if (is_oop) {
      // save regs before copy_memory
      __ push_reg(RegSet::of(d, count), sp);
    }

    {
      // UnsafeMemoryAccess page error: continue after unsafe access
      bool add_entry = !is_oop && (!aligned || sizeof(jlong) == size);
      UnsafeMemoryAccessMark umam(this, add_entry, true);
      copy_memory(decorators, is_oop ? T_OBJECT : T_BYTE, aligned, s, d, count, size);
    }

    if (is_oop) {
      __ pop_reg(RegSet::of(d, count), sp);
      if (VerifyOops) {
        verify_oop_array(size, d, count, t2);
      }
    }

    bs->arraycopy_epilogue(_masm, decorators, is_oop, d, count, t0, RegSet());

    __ leave();
    __ mv(x10, zr); // return 0
    __ ret();
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
  // cache line boundaries will still be loaded and stored atomically.
  //
  address generate_conjoint_copy(size_t size, bool aligned, bool is_oop, address nooverlap_target,
                                 address* entry, const char* name,
                                 bool dest_uninitialized = false) {
    const Register s = c_rarg0, d = c_rarg1, count = c_rarg2;
    RegSet saved_regs = RegSet::of(s, d, count);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();
    __ enter();

    if (entry != nullptr) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    // use fwd copy when (d-s) above_equal (count*size)
    __ sub(t0, d, s);
    __ slli(t1, count, exact_log2(size));
    Label L_continue;
    __ bltu(t0, t1, L_continue);
    __ j(nooverlap_target);
    __ bind(L_continue);

    DecoratorSet decorators = IN_HEAP | IS_ARRAY;
    if (dest_uninitialized) {
      decorators |= IS_DEST_UNINITIALIZED;
    }
    if (aligned) {
      decorators |= ARRAYCOPY_ALIGNED;
    }

    BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
    bs->arraycopy_prologue(_masm, decorators, is_oop, s, d, count, saved_regs);

    if (is_oop) {
      // save regs before copy_memory
      __ push_reg(RegSet::of(d, count), sp);
    }

    {
      // UnsafeMemoryAccess page error: continue after unsafe access
      bool add_entry = !is_oop && (!aligned || sizeof(jlong) == size);
      UnsafeMemoryAccessMark umam(this, add_entry, true);
      copy_memory(decorators, is_oop ? T_OBJECT : T_BYTE, aligned, s, d, count, -size);
    }

    if (is_oop) {
      __ pop_reg(RegSet::of(d, count), sp);
      if (VerifyOops) {
        verify_oop_array(size, d, count, t2);
      }
    }
    bs->arraycopy_epilogue(_masm, decorators, is_oop, d, count, t0, RegSet());
    __ leave();
    __ mv(x10, zr); // return 0
    __ ret();
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
  // Side Effects:
  //   disjoint_byte_copy_entry is set to the no-overlap entry point  //
  // If 'from' and/or 'to' are aligned on 4-, 2-, or 1-byte boundaries,
  // we let the hardware handle it.  The one to eight bytes within words,
  // dwords or qwords that span cache line boundaries will still be loaded
  // and stored atomically.
  //
  // Side Effects:
  //   disjoint_byte_copy_entry is set to the no-overlap entry point
  //   used by generate_conjoint_byte_copy().
  //
  address generate_disjoint_byte_copy(bool aligned, address* entry, const char* name) {
    const bool not_oop = false;
    return generate_disjoint_copy(sizeof (jbyte), aligned, not_oop, entry, name);
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
                                      address* entry, const char* name) {
    const bool not_oop = false;
    return generate_conjoint_copy(sizeof (jbyte), aligned, not_oop, nooverlap_target, entry, name);
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
  address generate_disjoint_short_copy(bool aligned,
                                       address* entry, const char* name) {
    const bool not_oop = false;
    return generate_disjoint_copy(sizeof (jshort), aligned, not_oop, entry, name);
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
                                       address* entry, const char* name) {
    const bool not_oop = false;
    return generate_conjoint_copy(sizeof (jshort), aligned, not_oop, nooverlap_target, entry, name);
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
  // If 'from' and/or 'to' are aligned on 4-byte boundaries, we let
  // the hardware handle it.  The two dwords within qwords that span
  // cache line boundaries will still be loaded and stored atomically.
  //
  // Side Effects:
  //   disjoint_int_copy_entry is set to the no-overlap entry point
  //   used by generate_conjoint_int_oop_copy().
  //
  address generate_disjoint_int_copy(bool aligned, address* entry,
                                     const char* name, bool dest_uninitialized = false) {
    const bool not_oop = false;
    return generate_disjoint_copy(sizeof (jint), aligned, not_oop, entry, name);
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
  // If 'from' and/or 'to' are aligned on 4-byte boundaries, we let
  // the hardware handle it.  The two dwords within qwords that span
  // cache line boundaries will still be loaded and stored atomically.
  //
  address generate_conjoint_int_copy(bool aligned, address nooverlap_target,
                                     address* entry, const char* name,
                                     bool dest_uninitialized = false) {
    const bool not_oop = false;
    return generate_conjoint_copy(sizeof (jint), aligned, not_oop, nooverlap_target, entry, name);
  }


  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord boundary == 8 bytes
  //             ignored
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as size_t, can be zero
  //
  // Side Effects:
  //   disjoint_oop_copy_entry or disjoint_long_copy_entry is set to the
  //   no-overlap entry point used by generate_conjoint_long_oop_copy().
  //
  address generate_disjoint_long_copy(bool aligned, address* entry,
                                      const char* name, bool dest_uninitialized = false) {
    const bool not_oop = false;
    return generate_disjoint_copy(sizeof (jlong), aligned, not_oop, entry, name);
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord boundary == 8 bytes
  //             ignored
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as size_t, can be zero
  //
  address generate_conjoint_long_copy(bool aligned,
                                      address nooverlap_target, address* entry,
                                      const char* name, bool dest_uninitialized = false) {
    const bool not_oop = false;
    return generate_conjoint_copy(sizeof (jlong), aligned, not_oop, nooverlap_target, entry, name);
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord boundary == 8 bytes
  //             ignored
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as size_t, can be zero
  //
  // Side Effects:
  //   disjoint_oop_copy_entry or disjoint_long_copy_entry is set to the
  //   no-overlap entry point used by generate_conjoint_long_oop_copy().
  //
  address generate_disjoint_oop_copy(bool aligned, address* entry,
                                     const char* name, bool dest_uninitialized) {
    const bool is_oop = true;
    const size_t size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
    return generate_disjoint_copy(size, aligned, is_oop, entry, name, dest_uninitialized);
  }

  // Arguments:
  //   aligned - true => Input and output aligned on a HeapWord boundary == 8 bytes
  //             ignored
  //   name    - stub name string
  //
  // Inputs:
  //   c_rarg0   - source array address
  //   c_rarg1   - destination array address
  //   c_rarg2   - element count, treated as size_t, can be zero
  //
  address generate_conjoint_oop_copy(bool aligned,
                                     address nooverlap_target, address* entry,
                                     const char* name, bool dest_uninitialized) {
    const bool is_oop = true;
    const size_t size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
    return generate_conjoint_copy(size, aligned, is_oop, nooverlap_target, entry,
                                  name, dest_uninitialized);
  }

  // Helper for generating a dynamic type check.
  // Smashes t0, t1.
  void generate_type_check(Register sub_klass,
                           Register super_check_offset,
                           Register super_klass,
                           Label& L_success) {
    assert_different_registers(sub_klass, super_check_offset, super_klass);

    BLOCK_COMMENT("type_check:");

    Label L_miss;

    __ check_klass_subtype_fast_path(sub_klass, super_klass, noreg, &L_success, &L_miss, nullptr, super_check_offset);
    __ check_klass_subtype_slow_path(sub_klass, super_klass, noreg, noreg, &L_success, nullptr);

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
  //    c_rarg4   - oop ckval (super_klass)
  //
  //  Output:
  //    x10 ==  0  -  success
  //    x10 == -1^K - failure, where K is partial transfer count
  //
  address generate_checkcast_copy(const char* name, address* entry,
                                  bool dest_uninitialized = false) {
    Label L_load_element, L_store_element, L_do_card_marks, L_done, L_done_pop;

    // Input registers (after setup_arg_regs)
    const Register from        = c_rarg0;   // source array address
    const Register to          = c_rarg1;   // destination array address
    const Register count       = c_rarg2;   // elementscount
    const Register ckoff       = c_rarg3;   // super_check_offset
    const Register ckval       = c_rarg4;   // super_klass

    RegSet wb_pre_saved_regs   = RegSet::range(c_rarg0, c_rarg4);
    RegSet wb_post_saved_regs  = RegSet::of(count);

    // Registers used as temps (x7, x9, x18 are save-on-entry)
    const Register count_save  = x19;       // orig elementscount
    const Register start_to    = x18;       // destination array start address
    const Register copied_oop  = x7;        // actual oop copied
    const Register r9_klass    = x9;        // oop._klass

    // Registers used as gc temps (x15, x16, x17 are save-on-call)
    const Register gct1 = x15, gct2 = x16, gct3 = x17;

    //---------------------------------------------------------------
    // Assembler stub will be used for this call to arraycopy
    // if the two arrays are subtypes of Object[] but the
    // destination array type is not equal to or a supertype
    // of the source type.  Each element must be separately
    // checked.

    assert_different_registers(from, to, count, ckoff, ckval, start_to,
                               copied_oop, r9_klass, count_save);

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // Caller of this entry point must set up the argument registers.
    if (entry != nullptr) {
      *entry = __ pc();
      BLOCK_COMMENT("Entry:");
    }

    // Empty array:  Nothing to do
    __ beqz(count, L_done);

    __ push_reg(RegSet::of(x7, x9, x18, x19), sp);

#ifdef ASSERT
    BLOCK_COMMENT("assert consistent ckoff/ckval");
    // The ckoff and ckval must be mutually consistent,
    // even though caller generates both.
    { Label L;
      int sco_offset = in_bytes(Klass::super_check_offset_offset());
      __ lwu(start_to, Address(ckval, sco_offset));
      __ beq(ckoff, start_to, L);
      __ stop("super_check_offset inconsistent");
      __ bind(L);
    }
#endif //ASSERT

    DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_CHECKCAST | ARRAYCOPY_DISJOINT;
    if (dest_uninitialized) {
      decorators |= IS_DEST_UNINITIALIZED;
    }

    bool is_oop = true;
    int element_size = UseCompressedOops ? 4 : 8;

    BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
    bs->arraycopy_prologue(_masm, decorators, is_oop, from, to, count, wb_pre_saved_regs);

    // save the original count
    __ mv(count_save, count);

    // Copy from low to high addresses
    __ mv(start_to, to);              // Save destination array start address
    __ j(L_load_element);

    // ======== begin loop ========
    // (Loop is rotated; its entry is L_load_element.)
    // Loop control:
    //   for count to 0 do
    //     copied_oop = load_heap_oop(from++)
    //     ... generate_type_check ...
    //     store_heap_oop(to++, copied_oop)
    //   end

    __ align(OptoLoopAlignment);

    __ BIND(L_store_element);
    bs->copy_store_at(_masm, decorators, T_OBJECT, element_size,
                      Address(to, 0), copied_oop,
                      gct1, gct2, gct3);
    __ add(to, to, UseCompressedOops ? 4 : 8);
    __ sub(count, count, 1);
    __ beqz(count, L_do_card_marks);

    // ======== loop entry is here ========
    __ BIND(L_load_element);
    bs->copy_load_at(_masm, decorators, T_OBJECT, element_size,
                     copied_oop, Address(from, 0),
                     gct1);
    __ add(from, from, UseCompressedOops ? 4 : 8);
    __ beqz(copied_oop, L_store_element);

    __ load_klass(r9_klass, copied_oop);// query the object klass
    generate_type_check(r9_klass, ckoff, ckval, L_store_element);
    // ======== end loop ========

    // It was a real error; we must depend on the caller to finish the job.
    // Register count = remaining oops, count_orig = total oops.
    // Emit GC store barriers for the oops we have copied and report
    // their number to the caller.

    __ sub(count, count_save, count);     // K = partially copied oop count
    __ xori(count, count, -1);                   // report (-1^K) to caller
    __ beqz(count, L_done_pop);

    __ BIND(L_do_card_marks);
    bs->arraycopy_epilogue(_masm, decorators, is_oop, start_to, count_save, t0, wb_post_saved_regs);

    __ bind(L_done_pop);
    __ pop_reg(RegSet::of(x7, x9, x18, x19), sp);
    inc_counter_np(SharedRuntime::_checkcast_array_copy_ctr);

    __ bind(L_done);
    __ mv(x10, count);
    __ leave();
    __ ret();

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

    assert_different_registers(t0, temp);

    // if [src_pos + length > arrayOop(src)->length()] then FAIL
    __ lwu(t0, Address(src, arrayOopDesc::length_offset_in_bytes()));
    __ addw(temp, length, src_pos);
    __ bgtu(temp, t0, L_failed);

    // if [dst_pos + length > arrayOop(dst)->length()] then FAIL
    __ lwu(t0, Address(dst, arrayOopDesc::length_offset_in_bytes()));
    __ addw(temp, length, dst_pos);
    __ bgtu(temp, t0, L_failed);

    // Have to clean up high 32 bits of 'src_pos' and 'dst_pos'.
    __ zero_extend(src_pos, src_pos, 32);
    __ zero_extend(dst_pos, dst_pos, 32);

    BLOCK_COMMENT("arraycopy_range_checks done");
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
  address generate_unsafe_copy(const char* name,
                               address byte_copy_entry,
                               address short_copy_entry,
                               address int_copy_entry,
                               address long_copy_entry) {
    assert_cond(byte_copy_entry != nullptr && short_copy_entry != nullptr &&
                int_copy_entry != nullptr && long_copy_entry != nullptr);
    Label L_long_aligned, L_int_aligned, L_short_aligned;
    const Register s = c_rarg0, d = c_rarg1, count = c_rarg2;

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();
    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // bump this on entry, not on exit:
    inc_counter_np(SharedRuntime::_unsafe_array_copy_ctr);

    __ orr(t0, s, d);
    __ orr(t0, t0, count);

    __ andi(t0, t0, BytesPerLong - 1);
    __ beqz(t0, L_long_aligned);
    __ andi(t0, t0, BytesPerInt - 1);
    __ beqz(t0, L_int_aligned);
    __ test_bit(t0, t0, 0);
    __ beqz(t0, L_short_aligned);
    __ j(RuntimeAddress(byte_copy_entry));

    __ BIND(L_short_aligned);
    __ srli(count, count, LogBytesPerShort);  // size => short_count
    __ j(RuntimeAddress(short_copy_entry));
    __ BIND(L_int_aligned);
    __ srli(count, count, LogBytesPerInt);    // size => int_count
    __ j(RuntimeAddress(int_copy_entry));
    __ BIND(L_long_aligned);
    __ srli(count, count, LogBytesPerLong);   // size => long_count
    __ j(RuntimeAddress(long_copy_entry));

    return start;
  }

  //
  //  Generate generic array copy stubs
  //
  //  Input:
  //    c_rarg0    -  src oop
  //    c_rarg1    -  src_pos (32-bits)
  //    c_rarg2    -  dst oop
  //    c_rarg3    -  dst_pos (32-bits)
  //    c_rarg4    -  element count (32-bits)
  //
  //  Output:
  //    x10 ==  0  -  success
  //    x10 == -1^K - failure, where K is partial transfer count
  //
  address generate_generic_copy(const char* name,
                                address byte_copy_entry, address short_copy_entry,
                                address int_copy_entry, address oop_copy_entry,
                                address long_copy_entry, address checkcast_copy_entry) {
    assert_cond(byte_copy_entry != nullptr && short_copy_entry != nullptr &&
                int_copy_entry != nullptr && oop_copy_entry != nullptr &&
                long_copy_entry != nullptr && checkcast_copy_entry != nullptr);
    Label L_failed, L_failed_0, L_objArray;
    Label L_copy_bytes, L_copy_shorts, L_copy_ints, L_copy_longs;

    // Input registers
    const Register src        = c_rarg0;  // source array oop
    const Register src_pos    = c_rarg1;  // source position
    const Register dst        = c_rarg2;  // destination array oop
    const Register dst_pos    = c_rarg3;  // destination position
    const Register length     = c_rarg4;

    // Registers used as temps
    const Register dst_klass = c_rarg5;

    __ align(CodeEntryAlignment);

    StubCodeMark mark(this, "StubRoutines", name);

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
    // (5) src klass and dst klass should be the same and not null.
    // (6) src and dst should be arrays.
    // (7) src_pos + length must not exceed length of src.
    // (8) dst_pos + length must not exceed length of dst.
    //

    // if src is null then return -1
    __ beqz(src, L_failed);

    // if [src_pos < 0] then return -1
    __ sign_extend(t0, src_pos, 32);
    __ bltz(t0, L_failed);

    // if dst is null then return -1
    __ beqz(dst, L_failed);

    // if [dst_pos < 0] then return -1
    __ sign_extend(t0, dst_pos, 32);
    __ bltz(t0, L_failed);

    // registers used as temp
    const Register scratch_length    = x28; // elements count to copy
    const Register scratch_src_klass = x29; // array klass
    const Register lh                = x30; // layout helper

    // if [length < 0] then return -1
    __ sign_extend(scratch_length, length, 32);    // length (elements count, 32-bits value)
    __ bltz(scratch_length, L_failed);

    __ load_klass(scratch_src_klass, src);
#ifdef ASSERT
    {
      BLOCK_COMMENT("assert klasses not null {");
      Label L1, L2;
      __ bnez(scratch_src_klass, L2);   // it is broken if klass is null
      __ bind(L1);
      __ stop("broken null klass");
      __ bind(L2);
      __ load_klass(t0, dst, t1);
      __ beqz(t0, L1);     // this would be broken also
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
    __ lw(lh, Address(scratch_src_klass, lh_offset));
    __ mv(t0, objArray_lh);
    __ beq(lh, t0, L_objArray);

    // if [src->klass() != dst->klass()] then return -1
    __ load_klass(t1, dst);
    __ bne(t1, scratch_src_klass, L_failed);

    // if src->is_Array() isn't null then return -1
    // i.e. (lh >= 0)
    __ bgez(lh, L_failed);

    // At this point, it is known to be a typeArray (array_tag 0x3).
#ifdef ASSERT
    {
      BLOCK_COMMENT("assert primitive array {");
      Label L;
      __ mv(t1, (int32_t)(Klass::_lh_array_tag_type_value << Klass::_lh_array_tag_shift));
      __ bge(lh, t1, L);
      __ stop("must be a primitive array");
      __ bind(L);
      BLOCK_COMMENT("} assert primitive array done");
    }
#endif

    arraycopy_range_checks(src, src_pos, dst, dst_pos, scratch_length,
                           t1, L_failed);

    // TypeArrayKlass
    //
    // src_addr = (src + array_header_in_bytes()) + (src_pos << log2elemsize)
    // dst_addr = (dst + array_header_in_bytes()) + (dst_pos << log2elemsize)
    //

    const Register t0_offset = t0;    // array offset
    const Register x30_elsize = lh;   // element size

    // Get array_header_in_bytes()
    int lh_header_size_width = exact_log2(Klass::_lh_header_size_mask + 1);
    int lh_header_size_msb = Klass::_lh_header_size_shift + lh_header_size_width;
    __ slli(t0_offset, lh, XLEN - lh_header_size_msb);          // left shift to remove 24 ~ 32;
    __ srli(t0_offset, t0_offset, XLEN - lh_header_size_width); // array_offset

    __ add(src, src, t0_offset);           // src array offset
    __ add(dst, dst, t0_offset);           // dst array offset
    BLOCK_COMMENT("choose copy loop based on element size");

    // next registers should be set before the jump to corresponding stub
    const Register from     = c_rarg0;  // source array address
    const Register to       = c_rarg1;  // destination array address
    const Register count    = c_rarg2;  // elements count

    // 'from', 'to', 'count' registers should be set in such order
    // since they are the same as 'src', 'src_pos', 'dst'.

    assert(Klass::_lh_log2_element_size_shift == 0, "fix this code");

    // The possible values of elsize are 0-3, i.e. exact_log2(element
    // size in bytes).  We do a simple bitwise binary search.
  __ BIND(L_copy_bytes);
    __ test_bit(t0, x30_elsize, 1);
    __ bnez(t0, L_copy_ints);
    __ test_bit(t0, x30_elsize, 0);
    __ bnez(t0, L_copy_shorts);
    __ add(from, src, src_pos); // src_addr
    __ add(to, dst, dst_pos); // dst_addr
    __ sign_extend(count, scratch_length, 32); // length
    __ j(RuntimeAddress(byte_copy_entry));

  __ BIND(L_copy_shorts);
    __ shadd(from, src_pos, src, t0, 1); // src_addr
    __ shadd(to, dst_pos, dst, t0, 1); // dst_addr
    __ sign_extend(count, scratch_length, 32); // length
    __ j(RuntimeAddress(short_copy_entry));

  __ BIND(L_copy_ints);
    __ test_bit(t0, x30_elsize, 0);
    __ bnez(t0, L_copy_longs);
    __ shadd(from, src_pos, src, t0, 2); // src_addr
    __ shadd(to, dst_pos, dst, t0, 2); // dst_addr
    __ sign_extend(count, scratch_length, 32); // length
    __ j(RuntimeAddress(int_copy_entry));

  __ BIND(L_copy_longs);
#ifdef ASSERT
    {
      BLOCK_COMMENT("assert long copy {");
      Label L;
      __ andi(lh, lh, Klass::_lh_log2_element_size_mask); // lh -> x30_elsize
      __ sign_extend(lh, lh, 32);
      __ mv(t0, LogBytesPerLong);
      __ beq(x30_elsize, t0, L);
      __ stop("must be long copy, but elsize is wrong");
      __ bind(L);
      BLOCK_COMMENT("} assert long copy done");
    }
#endif
    __ shadd(from, src_pos, src, t0, 3); // src_addr
    __ shadd(to, dst_pos, dst, t0, 3); // dst_addr
    __ sign_extend(count, scratch_length, 32); // length
    __ j(RuntimeAddress(long_copy_entry));

    // ObjArrayKlass
  __ BIND(L_objArray);
    // live at this point:  scratch_src_klass, scratch_length, src[_pos], dst[_pos]

    Label L_plain_copy, L_checkcast_copy;
    // test array classes for subtyping
    __ load_klass(t2, dst);
    __ bne(scratch_src_klass, t2, L_checkcast_copy); // usual case is exact equality

    // Identically typed arrays can be copied without element-wise checks.
    arraycopy_range_checks(src, src_pos, dst, dst_pos, scratch_length,
                           t1, L_failed);

    __ shadd(from, src_pos, src, t0, LogBytesPerHeapOop);
    __ add(from, from, arrayOopDesc::base_offset_in_bytes(T_OBJECT));
    __ shadd(to, dst_pos, dst, t0, LogBytesPerHeapOop);
    __ add(to, to, arrayOopDesc::base_offset_in_bytes(T_OBJECT));
    __ sign_extend(count, scratch_length, 32); // length
  __ BIND(L_plain_copy);
    __ j(RuntimeAddress(oop_copy_entry));

  __ BIND(L_checkcast_copy);
    // live at this point:  scratch_src_klass, scratch_length, t2 (dst_klass)
    {
      // Before looking at dst.length, make sure dst is also an objArray.
      __ lwu(t0, Address(t2, lh_offset));
      __ mv(t1, objArray_lh);
      __ bne(t0, t1, L_failed);

      // It is safe to examine both src.length and dst.length.
      arraycopy_range_checks(src, src_pos, dst, dst_pos, scratch_length,
                             t2, L_failed);

      __ load_klass(dst_klass, dst); // reload

      // Marshal the base address arguments now, freeing registers.
      __ shadd(from, src_pos, src, t0, LogBytesPerHeapOop);
      __ add(from, from, arrayOopDesc::base_offset_in_bytes(T_OBJECT));
      __ shadd(to, dst_pos, dst, t0, LogBytesPerHeapOop);
      __ add(to, to, arrayOopDesc::base_offset_in_bytes(T_OBJECT));
      __ sign_extend(count, length, 32);      // length (reloaded)
      const Register sco_temp = c_rarg3;      // this register is free now
      assert_different_registers(from, to, count, sco_temp,
                                 dst_klass, scratch_src_klass);

      // Generate the type check.
      const int sco_offset = in_bytes(Klass::super_check_offset_offset());
      __ lwu(sco_temp, Address(dst_klass, sco_offset));

      // Smashes t0, t1
      generate_type_check(scratch_src_klass, sco_temp, dst_klass, L_plain_copy);

      // Fetch destination element klass from the ObjArrayKlass header.
      int ek_offset = in_bytes(ObjArrayKlass::element_klass_offset());
      __ ld(dst_klass, Address(dst_klass, ek_offset));
      __ lwu(sco_temp, Address(dst_klass, sco_offset));

      // the checkcast_copy loop needs two extra arguments:
      assert(c_rarg3 == sco_temp, "#3 already in place");
      // Set up arguments for checkcast_copy_entry.
      __ mv(c_rarg4, dst_klass);  // dst.klass.element_klass
      __ j(RuntimeAddress(checkcast_copy_entry));
    }

  __ BIND(L_failed);
    __ mv(x10, -1);
    __ leave();   // required for proper stackwalking of RuntimeStub frame
    __ ret();

    return start;
  }

  //
  // Generate stub for array fill. If "aligned" is true, the
  // "to" address is assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //   to:    c_rarg0
  //   value: c_rarg1
  //   count: c_rarg2 treated as signed
  //
  address generate_fill(BasicType t, bool aligned, const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    BLOCK_COMMENT("Entry:");

    const Register to        = c_rarg0;  // source array address
    const Register value     = c_rarg1;  // value
    const Register count     = c_rarg2;  // elements count

    const Register bz_base   = x28;      // base for block_zero routine
    const Register cnt_words = x29;      // temp register
    const Register tmp_reg   = t1;

    __ enter();

    Label L_fill_elements, L_exit1;

    int shift = -1;
    switch (t) {
      case T_BYTE:
        shift = 0;

        // Zero extend value
        // 8 bit -> 16 bit
        __ andi(value, value, 0xff);
        __ mv(tmp_reg, value);
        __ slli(tmp_reg, tmp_reg, 8);
        __ orr(value, value, tmp_reg);

        // 16 bit -> 32 bit
        __ mv(tmp_reg, value);
        __ slli(tmp_reg, tmp_reg, 16);
        __ orr(value, value, tmp_reg);

        __ mv(tmp_reg, 8 >> shift); // Short arrays (< 8 bytes) fill by element
        __ bltu(count, tmp_reg, L_fill_elements);
        break;
      case T_SHORT:
        shift = 1;
        // Zero extend value
        // 16 bit -> 32 bit
        __ andi(value, value, 0xffff);
        __ mv(tmp_reg, value);
        __ slli(tmp_reg, tmp_reg, 16);
        __ orr(value, value, tmp_reg);

        // Short arrays (< 8 bytes) fill by element
        __ mv(tmp_reg, 8 >> shift);
        __ bltu(count, tmp_reg, L_fill_elements);
        break;
      case T_INT:
        shift = 2;

        // Short arrays (< 8 bytes) fill by element
        __ mv(tmp_reg, 8 >> shift);
        __ bltu(count, tmp_reg, L_fill_elements);
        break;
      default: ShouldNotReachHere();
    }

    // Align source address at 8 bytes address boundary.
    Label L_skip_align1, L_skip_align2, L_skip_align4;
    if (!aligned) {
      switch (t) {
        case T_BYTE:
          // One byte misalignment happens only for byte arrays.
          __ test_bit(t0, to, 0);
          __ beqz(t0, L_skip_align1);
          __ sb(value, Address(to, 0));
          __ addi(to, to, 1);
          __ addiw(count, count, -1);
          __ bind(L_skip_align1);
          // Fallthrough
        case T_SHORT:
          // Two bytes misalignment happens only for byte and short (char) arrays.
          __ test_bit(t0, to, 1);
          __ beqz(t0, L_skip_align2);
          __ sh(value, Address(to, 0));
          __ addi(to, to, 2);
          __ addiw(count, count, -(2 >> shift));
          __ bind(L_skip_align2);
          // Fallthrough
        case T_INT:
          // Align to 8 bytes, we know we are 4 byte aligned to start.
          __ test_bit(t0, to, 2);
          __ beqz(t0, L_skip_align4);
          __ sw(value, Address(to, 0));
          __ addi(to, to, 4);
          __ addiw(count, count, -(4 >> shift));
          __ bind(L_skip_align4);
          break;
        default: ShouldNotReachHere();
      }
    }

    //
    //  Fill large chunks
    //
    __ srliw(cnt_words, count, 3 - shift); // number of words

    // 32 bit -> 64 bit
    __ andi(value, value, 0xffffffff);
    __ mv(tmp_reg, value);
    __ slli(tmp_reg, tmp_reg, 32);
    __ orr(value, value, tmp_reg);

    __ slli(tmp_reg, cnt_words, 3 - shift);
    __ subw(count, count, tmp_reg);
    {
      __ fill_words(to, cnt_words, value);
    }

    // Remaining count is less than 8 bytes. Fill it by a single store.
    // Note that the total length is no less than 8 bytes.
    if (t == T_BYTE || t == T_SHORT) {
      __ beqz(count, L_exit1);
      __ shadd(to, count, to, tmp_reg, shift); // points to the end
      __ sd(value, Address(to, -8)); // overwrite some elements
      __ bind(L_exit1);
      __ leave();
      __ ret();
    }

    // Handle copies less than 8 bytes.
    Label L_fill_2, L_fill_4, L_exit2;
    __ bind(L_fill_elements);
    switch (t) {
      case T_BYTE:
        __ test_bit(t0, count, 0);
        __ beqz(t0, L_fill_2);
        __ sb(value, Address(to, 0));
        __ addi(to, to, 1);
        __ bind(L_fill_2);
        __ test_bit(t0, count, 1);
        __ beqz(t0, L_fill_4);
        __ sh(value, Address(to, 0));
        __ addi(to, to, 2);
        __ bind(L_fill_4);
        __ test_bit(t0, count, 2);
        __ beqz(t0, L_exit2);
        __ sw(value, Address(to, 0));
        break;
      case T_SHORT:
        __ test_bit(t0, count, 0);
        __ beqz(t0, L_fill_4);
        __ sh(value, Address(to, 0));
        __ addi(to, to, 2);
        __ bind(L_fill_4);
        __ test_bit(t0, count, 1);
        __ beqz(t0, L_exit2);
        __ sw(value, Address(to, 0));
        break;
      case T_INT:
        __ beqz(count, L_exit2);
        __ sw(value, Address(to, 0));
        break;
      default: ShouldNotReachHere();
    }
    __ bind(L_exit2);
    __ leave();
    __ ret();
    return start;
  }

  void generate_arraycopy_stubs() {
    address entry                     = nullptr;
    address entry_jbyte_arraycopy     = nullptr;
    address entry_jshort_arraycopy    = nullptr;
    address entry_jint_arraycopy      = nullptr;
    address entry_oop_arraycopy       = nullptr;
    address entry_jlong_arraycopy     = nullptr;
    address entry_checkcast_arraycopy = nullptr;

    generate_copy_longs(copy_f, c_rarg0, c_rarg1, t1, copy_forwards);
    generate_copy_longs(copy_b, c_rarg0, c_rarg1, t1, copy_backwards);

    StubRoutines::riscv::_zero_blocks = generate_zero_blocks();

    //*** jbyte
    // Always need aligned and unaligned versions
    StubRoutines::_jbyte_disjoint_arraycopy          = generate_disjoint_byte_copy(false, &entry,
                                                                                   "jbyte_disjoint_arraycopy");
    StubRoutines::_jbyte_arraycopy                   = generate_conjoint_byte_copy(false, entry,
                                                                                   &entry_jbyte_arraycopy,
                                                                                   "jbyte_arraycopy");
    StubRoutines::_arrayof_jbyte_disjoint_arraycopy  = generate_disjoint_byte_copy(true, &entry,
                                                                                   "arrayof_jbyte_disjoint_arraycopy");
    StubRoutines::_arrayof_jbyte_arraycopy           = generate_conjoint_byte_copy(true, entry, nullptr,
                                                                                   "arrayof_jbyte_arraycopy");

    //*** jshort
    // Always need aligned and unaligned versions
    StubRoutines::_jshort_disjoint_arraycopy         = generate_disjoint_short_copy(false, &entry,
                                                                                    "jshort_disjoint_arraycopy");
    StubRoutines::_jshort_arraycopy                  = generate_conjoint_short_copy(false, entry,
                                                                                    &entry_jshort_arraycopy,
                                                                                    "jshort_arraycopy");
    StubRoutines::_arrayof_jshort_disjoint_arraycopy = generate_disjoint_short_copy(true, &entry,
                                                                                    "arrayof_jshort_disjoint_arraycopy");
    StubRoutines::_arrayof_jshort_arraycopy          = generate_conjoint_short_copy(true, entry, nullptr,
                                                                                    "arrayof_jshort_arraycopy");

    //*** jint
    // Aligned versions
    StubRoutines::_arrayof_jint_disjoint_arraycopy   = generate_disjoint_int_copy(true, &entry,
                                                                                  "arrayof_jint_disjoint_arraycopy");
    StubRoutines::_arrayof_jint_arraycopy            = generate_conjoint_int_copy(true, entry, &entry_jint_arraycopy,
                                                                                  "arrayof_jint_arraycopy");
    // In 64 bit we need both aligned and unaligned versions of jint arraycopy.
    // entry_jint_arraycopy always points to the unaligned version
    StubRoutines::_jint_disjoint_arraycopy           = generate_disjoint_int_copy(false, &entry,
                                                                                  "jint_disjoint_arraycopy");
    StubRoutines::_jint_arraycopy                    = generate_conjoint_int_copy(false, entry,
                                                                                  &entry_jint_arraycopy,
                                                                                  "jint_arraycopy");

    //*** jlong
    // It is always aligned
    StubRoutines::_arrayof_jlong_disjoint_arraycopy  = generate_disjoint_long_copy(true, &entry,
                                                                                   "arrayof_jlong_disjoint_arraycopy");
    StubRoutines::_arrayof_jlong_arraycopy           = generate_conjoint_long_copy(true, entry, &entry_jlong_arraycopy,
                                                                                   "arrayof_jlong_arraycopy");
    StubRoutines::_jlong_disjoint_arraycopy          = StubRoutines::_arrayof_jlong_disjoint_arraycopy;
    StubRoutines::_jlong_arraycopy                   = StubRoutines::_arrayof_jlong_arraycopy;

    //*** oops
    {
      // With compressed oops we need unaligned versions; notice that
      // we overwrite entry_oop_arraycopy.
      bool aligned = !UseCompressedOops;

      StubRoutines::_arrayof_oop_disjoint_arraycopy
        = generate_disjoint_oop_copy(aligned, &entry, "arrayof_oop_disjoint_arraycopy",
                                     /*dest_uninitialized*/false);
      StubRoutines::_arrayof_oop_arraycopy
        = generate_conjoint_oop_copy(aligned, entry, &entry_oop_arraycopy, "arrayof_oop_arraycopy",
                                     /*dest_uninitialized*/false);
      // Aligned versions without pre-barriers
      StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit
        = generate_disjoint_oop_copy(aligned, &entry, "arrayof_oop_disjoint_arraycopy_uninit",
                                     /*dest_uninitialized*/true);
      StubRoutines::_arrayof_oop_arraycopy_uninit
        = generate_conjoint_oop_copy(aligned, entry, nullptr, "arrayof_oop_arraycopy_uninit",
                                     /*dest_uninitialized*/true);
    }

    StubRoutines::_oop_disjoint_arraycopy            = StubRoutines::_arrayof_oop_disjoint_arraycopy;
    StubRoutines::_oop_arraycopy                     = StubRoutines::_arrayof_oop_arraycopy;
    StubRoutines::_oop_disjoint_arraycopy_uninit     = StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit;
    StubRoutines::_oop_arraycopy_uninit              = StubRoutines::_arrayof_oop_arraycopy_uninit;

    StubRoutines::_checkcast_arraycopy        = generate_checkcast_copy("checkcast_arraycopy", &entry_checkcast_arraycopy);
    StubRoutines::_checkcast_arraycopy_uninit = generate_checkcast_copy("checkcast_arraycopy_uninit", nullptr,
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
  }

  // code for comparing 16 bytes of strings with same encoding
  void compare_string_16_bytes_same(Label &DIFF1, Label &DIFF2) {
    const Register result = x10, str1 = x11, cnt1 = x12, str2 = x13, tmp1 = x28, tmp2 = x29, tmp4 = x7, tmp5 = x31;
    __ ld(tmp5, Address(str1));
    __ addi(str1, str1, 8);
    __ xorr(tmp4, tmp1, tmp2);
    __ ld(cnt1, Address(str2));
    __ addi(str2, str2, 8);
    __ bnez(tmp4, DIFF1);
    __ ld(tmp1, Address(str1));
    __ addi(str1, str1, 8);
    __ xorr(tmp4, tmp5, cnt1);
    __ ld(tmp2, Address(str2));
    __ addi(str2, str2, 8);
    __ bnez(tmp4, DIFF2);
  }

  // code for comparing 8 characters of strings with Latin1 and Utf16 encoding
  void compare_string_8_x_LU(Register tmpL, Register tmpU, Register strL, Register strU, Label& DIFF) {
    const Register tmp = x30, tmpLval = x12;
    __ ld(tmpLval, Address(strL));
    __ addi(strL, strL, wordSize);
    __ ld(tmpU, Address(strU));
    __ addi(strU, strU, wordSize);
    __ inflate_lo32(tmpL, tmpLval);
    __ xorr(tmp, tmpU, tmpL);
    __ bnez(tmp, DIFF);

    __ ld(tmpU, Address(strU));
    __ addi(strU, strU, wordSize);
    __ inflate_hi32(tmpL, tmpLval);
    __ xorr(tmp, tmpU, tmpL);
    __ bnez(tmp, DIFF);
  }

  // x10  = result
  // x11  = str1
  // x12  = cnt1
  // x13  = str2
  // x14  = cnt2
  // x28  = tmp1
  // x29  = tmp2
  // x30  = tmp3
  address generate_compare_long_string_different_encoding(bool isLU) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", isLU ? "compare_long_string_different_encoding LU" : "compare_long_string_different_encoding UL");
    address entry = __ pc();
    Label SMALL_LOOP, TAIL, LOAD_LAST, DONE, CALCULATE_DIFFERENCE;
    const Register result = x10, str1 = x11, str2 = x13, cnt2 = x14,
                   tmp1 = x28, tmp2 = x29, tmp3 = x30, tmp4 = x12;

    // cnt2 == amount of characters left to compare
    // Check already loaded first 4 symbols
    __ inflate_lo32(tmp3, isLU ? tmp1 : tmp2);
    __ mv(isLU ? tmp1 : tmp2, tmp3);
    __ addi(str1, str1, isLU ? wordSize / 2 : wordSize);
    __ addi(str2, str2, isLU ? wordSize : wordSize / 2);
    __ sub(cnt2, cnt2, wordSize / 2); // Already loaded 4 symbols

    __ xorr(tmp3, tmp1, tmp2);
    __ bnez(tmp3, CALCULATE_DIFFERENCE);

    Register strU = isLU ? str2 : str1,
             strL = isLU ? str1 : str2,
             tmpU = isLU ? tmp2 : tmp1, // where to keep U for comparison
             tmpL = isLU ? tmp1 : tmp2; // where to keep L for comparison

    // make sure main loop is 8 byte-aligned, we should load another 4 bytes from strL
    // cnt2 is >= 68 here, no need to check it for >= 0
    __ lwu(tmpL, Address(strL));
    __ addi(strL, strL, wordSize / 2);
    __ ld(tmpU, Address(strU));
    __ addi(strU, strU, wordSize);
    __ inflate_lo32(tmp3, tmpL);
    __ mv(tmpL, tmp3);
    __ xorr(tmp3, tmpU, tmpL);
    __ bnez(tmp3, CALCULATE_DIFFERENCE);
    __ addi(cnt2, cnt2, -wordSize / 2);

    // we are now 8-bytes aligned on strL
    __ sub(cnt2, cnt2, wordSize * 2);
    __ bltz(cnt2, TAIL);
    __ bind(SMALL_LOOP); // smaller loop
      __ sub(cnt2, cnt2, wordSize * 2);
      compare_string_8_x_LU(tmpL, tmpU, strL, strU, CALCULATE_DIFFERENCE);
      compare_string_8_x_LU(tmpL, tmpU, strL, strU, CALCULATE_DIFFERENCE);
      __ bgez(cnt2, SMALL_LOOP);
      __ addi(t0, cnt2, wordSize * 2);
      __ beqz(t0, DONE);
    __ bind(TAIL);  // 1..15 characters left
      // Aligned access. Load bytes in portions - 4, 2, 1.

      __ addi(t0, cnt2, wordSize);
      __ addi(cnt2, cnt2, wordSize * 2); // amount of characters left to process
      __ bltz(t0, LOAD_LAST);
      // remaining characters are greater than or equals to 8, we can do one compare_string_8_x_LU
      compare_string_8_x_LU(tmpL, tmpU, strL, strU, CALCULATE_DIFFERENCE);
      __ addi(cnt2, cnt2, -wordSize);
      __ beqz(cnt2, DONE);  // no character left
      __ bind(LOAD_LAST);   // cnt2 = 1..7 characters left

      __ addi(cnt2, cnt2, -wordSize); // cnt2 is now an offset in strL which points to last 8 bytes
      __ slli(t0, cnt2, 1);     // t0 is now an offset in strU which points to last 16 bytes
      __ add(strL, strL, cnt2); // Address of last 8 bytes in Latin1 string
      __ add(strU, strU, t0);   // Address of last 16 bytes in UTF-16 string
      __ load_int_misaligned(tmpL, Address(strL), t0, false);
      __ load_long_misaligned(tmpU, Address(strU), t0, 2);
      __ inflate_lo32(tmp3, tmpL);
      __ mv(tmpL, tmp3);
      __ xorr(tmp3, tmpU, tmpL);
      __ bnez(tmp3, CALCULATE_DIFFERENCE);

      __ addi(strL, strL, wordSize / 2); // Address of last 4 bytes in Latin1 string
      __ addi(strU, strU, wordSize);   // Address of last 8 bytes in UTF-16 string
      __ load_int_misaligned(tmpL, Address(strL), t0, false);
      __ load_long_misaligned(tmpU, Address(strU), t0, 2);
      __ inflate_lo32(tmp3, tmpL);
      __ mv(tmpL, tmp3);
      __ xorr(tmp3, tmpU, tmpL);
      __ bnez(tmp3, CALCULATE_DIFFERENCE);
      __ j(DONE); // no character left

      // Find the first different characters in the longwords and
      // compute their difference.
    __ bind(CALCULATE_DIFFERENCE);
      __ ctzc_bit(tmp4, tmp3);
      __ srl(tmp1, tmp1, tmp4);
      __ srl(tmp2, tmp2, tmp4);
      __ andi(tmp1, tmp1, 0xFFFF);
      __ andi(tmp2, tmp2, 0xFFFF);
      __ sub(result, tmp1, tmp2);
    __ bind(DONE);
      __ ret();
    return entry;
  }

  address generate_method_entry_barrier() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "nmethod_entry_barrier");

    Label deoptimize_label;

    address start = __ pc();

    BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();

    if (bs_asm->nmethod_patching_type() == NMethodPatchingType::conc_instruction_and_data_patch) {
      BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
      Address thread_epoch_addr(xthread, in_bytes(bs_nm->thread_disarmed_guard_value_offset()) + 4);
      __ la(t1, ExternalAddress(bs_asm->patching_epoch_addr()));
      __ lwu(t1, t1);
      __ sw(t1, thread_epoch_addr);
      __ membar(__ LoadLoad);
    }

    __ set_last_Java_frame(sp, fp, ra);

    __ enter();
    __ add(t1, sp, wordSize);

    __ sub(sp, sp, 4 * wordSize);

    __ push_call_clobbered_registers();

    __ mv(c_rarg0, t1);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, BarrierSetNMethod::nmethod_stub_entry_barrier), 1);

    __ reset_last_Java_frame(true);

    __ mv(t0, x10);

    __ pop_call_clobbered_registers();

    __ bnez(t0, deoptimize_label);

    __ leave();
    __ ret();

    __ BIND(deoptimize_label);

    __ ld(t0, Address(sp, 0));
    __ ld(fp, Address(sp, wordSize));
    __ ld(ra, Address(sp, wordSize * 2));
    __ ld(t1, Address(sp, wordSize * 3));

    __ mv(sp, t0);
    __ jr(t1);

    return start;
  }

  // x10  = result
  // x11  = str1
  // x12  = cnt1
  // x13  = str2
  // x14  = cnt2
  // x28  = tmp1
  // x29  = tmp2
  // x30  = tmp3
  // x31  = tmp4
  address generate_compare_long_string_same_encoding(bool isLL) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", isLL ?
                      "compare_long_string_same_encoding LL" : "compare_long_string_same_encoding UU");
    address entry = __ pc();
    Label SMALL_LOOP, CHECK_LAST, DIFF2, TAIL,
          LENGTH_DIFF, DIFF, LAST_CHECK_AND_LENGTH_DIFF;
    const Register result = x10, str1 = x11, cnt1 = x12, str2 = x13, cnt2 = x14,
                   tmp1 = x28, tmp2 = x29, tmp3 = x30, tmp4 = x7, tmp5 = x31;
    RegSet spilled_regs = RegSet::of(tmp4, tmp5);

    // cnt1/cnt2 contains amount of characters to compare. cnt1 can be re-used
    // update cnt2 counter with already loaded 8 bytes
    __ sub(cnt2, cnt2, wordSize / (isLL ? 1 : 2));
    // update pointers, because of previous read
    __ add(str1, str1, wordSize);
    __ add(str2, str2, wordSize);
    // less than 16 bytes left?
    __ sub(cnt2, cnt2, isLL ? 16 : 8);
    __ push_reg(spilled_regs, sp);
    __ bltz(cnt2, TAIL);
    __ bind(SMALL_LOOP);
      compare_string_16_bytes_same(DIFF, DIFF2);
      __ sub(cnt2, cnt2, isLL ? 16 : 8);
      __ bgez(cnt2, SMALL_LOOP);
    __ bind(TAIL);
      __ addi(cnt2, cnt2, isLL ? 16 : 8);
      __ beqz(cnt2, LAST_CHECK_AND_LENGTH_DIFF);
      __ sub(cnt2, cnt2, isLL ? 8 : 4);
      __ blez(cnt2, CHECK_LAST);
      __ xorr(tmp4, tmp1, tmp2);
      __ bnez(tmp4, DIFF);
      __ ld(tmp1, Address(str1));
      __ addi(str1, str1, 8);
      __ ld(tmp2, Address(str2));
      __ addi(str2, str2, 8);
      __ sub(cnt2, cnt2, isLL ? 8 : 4);
    __ bind(CHECK_LAST);
      if (!isLL) {
        __ add(cnt2, cnt2, cnt2); // now in bytes
      }
      __ xorr(tmp4, tmp1, tmp2);
      __ bnez(tmp4, DIFF);
      __ add(str1, str1, cnt2);
      __ load_long_misaligned(tmp5, Address(str1), tmp3, isLL ? 1 : 2);
      __ add(str2, str2, cnt2);
      __ load_long_misaligned(cnt1, Address(str2), tmp3, isLL ? 1 : 2);
      __ xorr(tmp4, tmp5, cnt1);
      __ beqz(tmp4, LENGTH_DIFF);
      // Find the first different characters in the longwords and
      // compute their difference.
    __ bind(DIFF2);
      __ ctzc_bit(tmp3, tmp4, isLL); // count zero from lsb to msb
      __ srl(tmp5, tmp5, tmp3);
      __ srl(cnt1, cnt1, tmp3);
      if (isLL) {
        __ andi(tmp5, tmp5, 0xFF);
        __ andi(cnt1, cnt1, 0xFF);
      } else {
        __ andi(tmp5, tmp5, 0xFFFF);
        __ andi(cnt1, cnt1, 0xFFFF);
      }
      __ sub(result, tmp5, cnt1);
      __ j(LENGTH_DIFF);
    __ bind(DIFF);
      __ ctzc_bit(tmp3, tmp4, isLL); // count zero from lsb to msb
      __ srl(tmp1, tmp1, tmp3);
      __ srl(tmp2, tmp2, tmp3);
      if (isLL) {
        __ andi(tmp1, tmp1, 0xFF);
        __ andi(tmp2, tmp2, 0xFF);
      } else {
        __ andi(tmp1, tmp1, 0xFFFF);
        __ andi(tmp2, tmp2, 0xFFFF);
      }
      __ sub(result, tmp1, tmp2);
      __ j(LENGTH_DIFF);
    __ bind(LAST_CHECK_AND_LENGTH_DIFF);
      __ xorr(tmp4, tmp1, tmp2);
      __ bnez(tmp4, DIFF);
    __ bind(LENGTH_DIFF);
      __ pop_reg(spilled_regs, sp);
      __ ret();
    return entry;
  }

  void generate_compare_long_strings() {
    StubRoutines::riscv::_compare_long_string_LL = generate_compare_long_string_same_encoding(true);
    StubRoutines::riscv::_compare_long_string_UU = generate_compare_long_string_same_encoding(false);
    StubRoutines::riscv::_compare_long_string_LU = generate_compare_long_string_different_encoding(true);
    StubRoutines::riscv::_compare_long_string_UL = generate_compare_long_string_different_encoding(false);
  }

  // x10 result
  // x11 src
  // x12 src count
  // x13 pattern
  // x14 pattern count
  address generate_string_indexof_linear(bool needle_isL, bool haystack_isL)
  {
    const char* stubName = needle_isL
           ? (haystack_isL ? "indexof_linear_ll" : "indexof_linear_ul")
           : "indexof_linear_uu";
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", stubName);
    address entry = __ pc();

    int needle_chr_size = needle_isL ? 1 : 2;
    int haystack_chr_size = haystack_isL ? 1 : 2;
    int needle_chr_shift = needle_isL ? 0 : 1;
    int haystack_chr_shift = haystack_isL ? 0 : 1;
    bool isL = needle_isL && haystack_isL;
    // parameters
    Register result = x10, haystack = x11, haystack_len = x12, needle = x13, needle_len = x14;
    // temporary registers
    Register mask1 = x20, match_mask = x21, first = x22, trailing_zeros = x23, mask2 = x24, tmp = x25;
    // redefinitions
    Register ch1 = x28, ch2 = x29;
    RegSet spilled_regs = RegSet::range(x20, x25) + RegSet::range(x28, x29);

    __ push_reg(spilled_regs, sp);

    Label L_LOOP, L_LOOP_PROCEED, L_SMALL, L_HAS_ZERO,
          L_HAS_ZERO_LOOP, L_CMP_LOOP, L_CMP_LOOP_NOMATCH, L_SMALL_PROCEED,
          L_SMALL_HAS_ZERO_LOOP, L_SMALL_CMP_LOOP_NOMATCH, L_SMALL_CMP_LOOP,
          L_POST_LOOP, L_CMP_LOOP_LAST_CMP, L_HAS_ZERO_LOOP_NOMATCH,
          L_SMALL_CMP_LOOP_LAST_CMP, L_SMALL_CMP_LOOP_LAST_CMP2,
          L_CMP_LOOP_LAST_CMP2, DONE, NOMATCH;

    __ ld(ch1, Address(needle));
    __ ld(ch2, Address(haystack));
    // src.length - pattern.length
    __ sub(haystack_len, haystack_len, needle_len);

    // first is needle[0]
    __ andi(first, ch1, needle_isL ? 0xFF : 0xFFFF, first);
    uint64_t mask0101 = UCONST64(0x0101010101010101);
    uint64_t mask0001 = UCONST64(0x0001000100010001);
    __ mv(mask1, haystack_isL ? mask0101 : mask0001);
    __ mul(first, first, mask1);
    uint64_t mask7f7f = UCONST64(0x7f7f7f7f7f7f7f7f);
    uint64_t mask7fff = UCONST64(0x7fff7fff7fff7fff);
    __ mv(mask2, haystack_isL ? mask7f7f : mask7fff);
    if (needle_isL != haystack_isL) {
      __ mv(tmp, ch1);
    }
    __ sub(haystack_len, haystack_len, wordSize / haystack_chr_size - 1);
    __ blez(haystack_len, L_SMALL);

    if (needle_isL != haystack_isL) {
      __ inflate_lo32(ch1, tmp, match_mask, trailing_zeros);
    }
    // xorr, sub, orr, notr, andr
    // compare and set match_mask[i] with 0x80/0x8000 (Latin1/UTF16) if ch2[i] == first[i]
    // eg:
    // first:        aa aa aa aa aa aa aa aa
    // ch2:          aa aa li nx jd ka aa aa
    // match_mask:   80 80 00 00 00 00 80 80
    __ compute_match_mask(ch2, first, match_mask, mask1, mask2);

    // search first char of needle, if success, goto L_HAS_ZERO;
    __ bnez(match_mask, L_HAS_ZERO);
    __ sub(haystack_len, haystack_len, wordSize / haystack_chr_size);
    __ add(result, result, wordSize / haystack_chr_size);
    __ add(haystack, haystack, wordSize);
    __ bltz(haystack_len, L_POST_LOOP);

    __ bind(L_LOOP);
    __ ld(ch2, Address(haystack));
    __ compute_match_mask(ch2, first, match_mask, mask1, mask2);
    __ bnez(match_mask, L_HAS_ZERO);

    __ bind(L_LOOP_PROCEED);
    __ sub(haystack_len, haystack_len, wordSize / haystack_chr_size);
    __ add(haystack, haystack, wordSize);
    __ add(result, result, wordSize / haystack_chr_size);
    __ bgez(haystack_len, L_LOOP);

    __ bind(L_POST_LOOP);
    __ mv(ch2, -wordSize / haystack_chr_size);
    __ ble(haystack_len, ch2, NOMATCH); // no extra characters to check
    __ ld(ch2, Address(haystack));
    __ slli(haystack_len, haystack_len, LogBitsPerByte + haystack_chr_shift);
    __ neg(haystack_len, haystack_len);
    __ xorr(ch2, first, ch2);
    __ sub(match_mask, ch2, mask1);
    __ orr(ch2, ch2, mask2);
    __ mv(trailing_zeros, -1); // all bits set
    __ j(L_SMALL_PROCEED);

    __ align(OptoLoopAlignment);
    __ bind(L_SMALL);
    __ slli(haystack_len, haystack_len, LogBitsPerByte + haystack_chr_shift);
    __ neg(haystack_len, haystack_len);
    if (needle_isL != haystack_isL) {
      __ inflate_lo32(ch1, tmp, match_mask, trailing_zeros);
    }
    __ xorr(ch2, first, ch2);
    __ sub(match_mask, ch2, mask1);
    __ orr(ch2, ch2, mask2);
    __ mv(trailing_zeros, -1); // all bits set

    __ bind(L_SMALL_PROCEED);
    __ srl(trailing_zeros, trailing_zeros, haystack_len); // mask. zeroes on useless bits.
    __ notr(ch2, ch2);
    __ andr(match_mask, match_mask, ch2);
    __ andr(match_mask, match_mask, trailing_zeros); // clear useless bits and check
    __ beqz(match_mask, NOMATCH);

    __ bind(L_SMALL_HAS_ZERO_LOOP);
    __ ctzc_bit(trailing_zeros, match_mask, haystack_isL, ch2, tmp); // count trailing zeros
    __ addi(trailing_zeros, trailing_zeros, haystack_isL ? 7 : 15);
    __ mv(ch2, wordSize / haystack_chr_size);
    __ ble(needle_len, ch2, L_SMALL_CMP_LOOP_LAST_CMP2);
    __ compute_index(haystack, trailing_zeros, match_mask, result, ch2, tmp, haystack_isL);
    __ mv(trailing_zeros, wordSize / haystack_chr_size);
    __ bne(ch1, ch2, L_SMALL_CMP_LOOP_NOMATCH);

    __ bind(L_SMALL_CMP_LOOP);
    __ shadd(first, trailing_zeros, needle, first, needle_chr_shift);
    __ shadd(ch2, trailing_zeros, haystack, ch2, haystack_chr_shift);
    needle_isL ? __ lbu(first, Address(first)) : __ lhu(first, Address(first));
    haystack_isL ? __ lbu(ch2, Address(ch2)) : __ lhu(ch2, Address(ch2));
    __ add(trailing_zeros, trailing_zeros, 1);
    __ bge(trailing_zeros, needle_len, L_SMALL_CMP_LOOP_LAST_CMP);
    __ beq(first, ch2, L_SMALL_CMP_LOOP);

    __ bind(L_SMALL_CMP_LOOP_NOMATCH);
    __ beqz(match_mask, NOMATCH);
    __ ctzc_bit(trailing_zeros, match_mask, haystack_isL, tmp, ch2);
    __ addi(trailing_zeros, trailing_zeros, haystack_isL ? 7 : 15);
    __ add(result, result, 1);
    __ add(haystack, haystack, haystack_chr_size);
    __ j(L_SMALL_HAS_ZERO_LOOP);

    __ align(OptoLoopAlignment);
    __ bind(L_SMALL_CMP_LOOP_LAST_CMP);
    __ bne(first, ch2, L_SMALL_CMP_LOOP_NOMATCH);
    __ j(DONE);

    __ align(OptoLoopAlignment);
    __ bind(L_SMALL_CMP_LOOP_LAST_CMP2);
    __ compute_index(haystack, trailing_zeros, match_mask, result, ch2, tmp, haystack_isL);
    __ bne(ch1, ch2, L_SMALL_CMP_LOOP_NOMATCH);
    __ j(DONE);

    __ align(OptoLoopAlignment);
    __ bind(L_HAS_ZERO);
    __ ctzc_bit(trailing_zeros, match_mask, haystack_isL, tmp, ch2);
    __ addi(trailing_zeros, trailing_zeros, haystack_isL ? 7 : 15);
    __ slli(needle_len, needle_len, BitsPerByte * wordSize / 2);
    __ orr(haystack_len, haystack_len, needle_len); // restore needle_len(32bits)
    __ sub(result, result, 1); // array index from 0, so result -= 1

    __ bind(L_HAS_ZERO_LOOP);
    __ mv(needle_len, wordSize / haystack_chr_size);
    __ srli(ch2, haystack_len, BitsPerByte * wordSize / 2);
    __ bge(needle_len, ch2, L_CMP_LOOP_LAST_CMP2);
    // load next 8 bytes from haystack, and increase result index
    __ compute_index(haystack, trailing_zeros, match_mask, result, ch2, tmp, haystack_isL);
    __ add(result, result, 1);
    __ mv(trailing_zeros, wordSize / haystack_chr_size);
    __ bne(ch1, ch2, L_CMP_LOOP_NOMATCH);

    // compare one char
    __ bind(L_CMP_LOOP);
    __ shadd(needle_len, trailing_zeros, needle, needle_len, needle_chr_shift);
    needle_isL ? __ lbu(needle_len, Address(needle_len)) : __ lhu(needle_len, Address(needle_len));
    __ shadd(ch2, trailing_zeros, haystack, ch2, haystack_chr_shift);
    haystack_isL ? __ lbu(ch2, Address(ch2)) : __ lhu(ch2, Address(ch2));
    __ add(trailing_zeros, trailing_zeros, 1); // next char index
    __ srli(tmp, haystack_len, BitsPerByte * wordSize / 2);
    __ bge(trailing_zeros, tmp, L_CMP_LOOP_LAST_CMP);
    __ beq(needle_len, ch2, L_CMP_LOOP);

    __ bind(L_CMP_LOOP_NOMATCH);
    __ beqz(match_mask, L_HAS_ZERO_LOOP_NOMATCH);
    __ ctzc_bit(trailing_zeros, match_mask, haystack_isL, needle_len, ch2); // find next "first" char index
    __ addi(trailing_zeros, trailing_zeros, haystack_isL ? 7 : 15);
    __ add(haystack, haystack, haystack_chr_size);
    __ j(L_HAS_ZERO_LOOP);

    __ align(OptoLoopAlignment);
    __ bind(L_CMP_LOOP_LAST_CMP);
    __ bne(needle_len, ch2, L_CMP_LOOP_NOMATCH);
    __ j(DONE);

    __ align(OptoLoopAlignment);
    __ bind(L_CMP_LOOP_LAST_CMP2);
    __ compute_index(haystack, trailing_zeros, match_mask, result, ch2, tmp, haystack_isL);
    __ add(result, result, 1);
    __ bne(ch1, ch2, L_CMP_LOOP_NOMATCH);
    __ j(DONE);

    __ align(OptoLoopAlignment);
    __ bind(L_HAS_ZERO_LOOP_NOMATCH);
    // 1) Restore "result" index. Index was wordSize/str2_chr_size * N until
    // L_HAS_ZERO block. Byte octet was analyzed in L_HAS_ZERO_LOOP,
    // so, result was increased at max by wordSize/str2_chr_size - 1, so,
    // respective high bit wasn't changed. L_LOOP_PROCEED will increase
    // result by analyzed characters value, so, we can just reset lower bits
    // in result here. Clear 2 lower bits for UU/UL and 3 bits for LL
    // 2) restore needle_len and haystack_len values from "compressed" haystack_len
    // 3) advance haystack value to represent next haystack octet. result & 7/3 is
    // index of last analyzed substring inside current octet. So, haystack in at
    // respective start address. We need to advance it to next octet
    __ andi(match_mask, result, wordSize / haystack_chr_size - 1);
    __ srli(needle_len, haystack_len, BitsPerByte * wordSize / 2);
    __ andi(result, result, haystack_isL ? -8 : -4);
    __ slli(tmp, match_mask, haystack_chr_shift);
    __ sub(haystack, haystack, tmp);
    __ sign_extend(haystack_len, haystack_len, 32);
    __ j(L_LOOP_PROCEED);

    __ align(OptoLoopAlignment);
    __ bind(NOMATCH);
    __ mv(result, -1);

    __ bind(DONE);
    __ pop_reg(spilled_regs, sp);
    __ ret();
    return entry;
  }

  void generate_string_indexof_stubs()
  {
    StubRoutines::riscv::_string_indexof_linear_ll = generate_string_indexof_linear(true, true);
    StubRoutines::riscv::_string_indexof_linear_uu = generate_string_indexof_linear(false, false);
    StubRoutines::riscv::_string_indexof_linear_ul = generate_string_indexof_linear(true, false);
  }

#ifdef COMPILER2
  address generate_lookup_secondary_supers_table_stub(u1 super_klass_index) {
    StubCodeMark mark(this, "StubRoutines", "lookup_secondary_supers_table");

    address start = __ pc();
    const Register
      r_super_klass  = x10,
      r_array_base   = x11,
      r_array_length = x12,
      r_array_index  = x13,
      r_sub_klass    = x14,
      result         = x15,
      r_bitmap       = x16;

    Label L_success;
    __ enter();
    __ lookup_secondary_supers_table(r_sub_klass, r_super_klass, result,
                                     r_array_base, r_array_length, r_array_index,
                                     r_bitmap, super_klass_index, /*stub_is_near*/true);
    __ leave();
    __ ret();

    return start;
  }

  // Slow path implementation for UseSecondarySupersTable.
  address generate_lookup_secondary_supers_table_slow_path_stub() {
    StubCodeMark mark(this, "StubRoutines", "lookup_secondary_supers_table_slow_path");

    address start = __ pc();
    const Register
      r_super_klass  = x10,        // argument
      r_array_base   = x11,        // argument
      temp1          = x12,        // tmp
      r_array_index  = x13,        // argument
      result         = x15,        // argument
      r_bitmap       = x16;        // argument


    __ lookup_secondary_supers_table_slow_path(r_super_klass, r_array_base, r_array_index, r_bitmap, result, temp1);
    __ ret();

    return start;
  }

  address generate_mulAdd()
  {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "mulAdd");

    address entry = __ pc();

    const Register out     = x10;
    const Register in      = x11;
    const Register offset  = x12;
    const Register len     = x13;
    const Register k       = x14;
    const Register tmp     = x28;

    BLOCK_COMMENT("Entry:");
    __ enter();
    __ mul_add(out, in, offset, len, k, tmp);
    __ leave();
    __ ret();

    return entry;
  }

  /**
   *  Arguments:
   *
   *  Input:
   *    c_rarg0   - x address
   *    c_rarg1   - x length
   *    c_rarg2   - y address
   *    c_rarg3   - y length
   *    c_rarg4   - z address
   */
  address generate_multiplyToLen()
  {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "multiplyToLen");
    address entry = __ pc();

    const Register x     = x10;
    const Register xlen  = x11;
    const Register y     = x12;
    const Register ylen  = x13;
    const Register z     = x14;

    const Register tmp0  = x15;
    const Register tmp1  = x16;
    const Register tmp2  = x17;
    const Register tmp3  = x7;
    const Register tmp4  = x28;
    const Register tmp5  = x29;
    const Register tmp6  = x30;
    const Register tmp7  = x31;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame
    __ multiply_to_len(x, xlen, y, ylen, z, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7);
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret();

    return entry;
  }

  address generate_squareToLen()
  {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "squareToLen");
    address entry = __ pc();

    const Register x     = x10;
    const Register xlen  = x11;
    const Register z     = x12;
    const Register y     = x14; // == x
    const Register ylen  = x15; // == xlen

    const Register tmp0  = x13; // zlen, unused
    const Register tmp1  = x16;
    const Register tmp2  = x17;
    const Register tmp3  = x7;
    const Register tmp4  = x28;
    const Register tmp5  = x29;
    const Register tmp6  = x30;
    const Register tmp7  = x31;

    BLOCK_COMMENT("Entry:");
    __ enter();
    __ mv(y, x);
    __ mv(ylen, xlen);
    __ multiply_to_len(x, xlen, y, ylen, z, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7);
    __ leave();
    __ ret();

    return entry;
  }

  // Arguments:
  //
  // Input:
  //   c_rarg0   - newArr address
  //   c_rarg1   - oldArr address
  //   c_rarg2   - newIdx
  //   c_rarg3   - shiftCount
  //   c_rarg4   - numIter
  //
  address generate_bigIntegerLeftShift() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "bigIntegerLeftShiftWorker");
    address entry = __ pc();

    Label loop, exit;

    Register newArr        = c_rarg0;
    Register oldArr        = c_rarg1;
    Register newIdx        = c_rarg2;
    Register shiftCount    = c_rarg3;
    Register numIter       = c_rarg4;

    Register shiftRevCount = c_rarg5;
    Register oldArrNext    = t1;

    __ beqz(numIter, exit);
    __ shadd(newArr, newIdx, newArr, t0, 2);

    __ mv(shiftRevCount, 32);
    __ sub(shiftRevCount, shiftRevCount, shiftCount);

    __ bind(loop);
    __ addi(oldArrNext, oldArr, 4);
    __ vsetvli(t0, numIter, Assembler::e32, Assembler::m4);
    __ vle32_v(v0, oldArr);
    __ vle32_v(v4, oldArrNext);
    __ vsll_vx(v0, v0, shiftCount);
    __ vsrl_vx(v4, v4, shiftRevCount);
    __ vor_vv(v0, v0, v4);
    __ vse32_v(v0, newArr);
    __ sub(numIter, numIter, t0);
    __ shadd(oldArr, t0, oldArr, t1, 2);
    __ shadd(newArr, t0, newArr, t1, 2);
    __ bnez(numIter, loop);

    __ bind(exit);
    __ ret();

    return entry;
  }

  // Arguments:
  //
  // Input:
  //   c_rarg0   - newArr address
  //   c_rarg1   - oldArr address
  //   c_rarg2   - newIdx
  //   c_rarg3   - shiftCount
  //   c_rarg4   - numIter
  //
  address generate_bigIntegerRightShift() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "bigIntegerRightShiftWorker");
    address entry = __ pc();

    Label loop, exit;

    Register newArr        = c_rarg0;
    Register oldArr        = c_rarg1;
    Register newIdx        = c_rarg2;
    Register shiftCount    = c_rarg3;
    Register numIter       = c_rarg4;
    Register idx           = numIter;

    Register shiftRevCount = c_rarg5;
    Register oldArrNext    = c_rarg6;
    Register newArrCur     = t0;
    Register oldArrCur     = t1;

    __ beqz(idx, exit);
    __ shadd(newArr, newIdx, newArr, t0, 2);

    __ mv(shiftRevCount, 32);
    __ sub(shiftRevCount, shiftRevCount, shiftCount);

    __ bind(loop);
    __ vsetvli(t0, idx, Assembler::e32, Assembler::m4);
    __ sub(idx, idx, t0);
    __ shadd(oldArrNext, idx, oldArr, t1, 2);
    __ shadd(newArrCur, idx, newArr, t1, 2);
    __ addi(oldArrCur, oldArrNext, 4);
    __ vle32_v(v0, oldArrCur);
    __ vle32_v(v4, oldArrNext);
    __ vsrl_vx(v0, v0, shiftCount);
    __ vsll_vx(v4, v4, shiftRevCount);
    __ vor_vv(v0, v0, v4);
    __ vse32_v(v0, newArrCur);
    __ bnez(idx, loop);

    __ bind(exit);
    __ ret();

    return entry;
  }
#endif

#ifdef COMPILER2
  class MontgomeryMultiplyGenerator : public MacroAssembler {

    Register Pa_base, Pb_base, Pn_base, Pm_base, inv, Rlen, Ra, Rb, Rm, Rn,
      Pa, Pb, Pn, Pm, Rhi_ab, Rlo_ab, Rhi_mn, Rlo_mn, tmp0, tmp1, tmp2, Ri, Rj;

    RegSet _toSave;
    bool _squaring;

  public:
    MontgomeryMultiplyGenerator (Assembler *as, bool squaring)
      : MacroAssembler(as->code()), _squaring(squaring) {

      // Register allocation

      RegSetIterator<Register> regs = RegSet::range(x10, x26).begin();
      Pa_base = *regs;       // Argument registers
      if (squaring) {
        Pb_base = Pa_base;
      } else {
        Pb_base = *++regs;
      }
      Pn_base = *++regs;
      Rlen= *++regs;
      inv = *++regs;
      Pm_base = *++regs;

                        // Working registers:
      Ra =  *++regs;    // The current digit of a, b, n, and m.
      Rb =  *++regs;
      Rm =  *++regs;
      Rn =  *++regs;

      Pa =  *++regs;      // Pointers to the current/next digit of a, b, n, and m.
      Pb =  *++regs;
      Pm =  *++regs;
      Pn =  *++regs;

      tmp0 =  *++regs;    // Three registers which form a
      tmp1 =  *++regs;    // triple-precision accumuator.
      tmp2 =  *++regs;

      Ri =  x6;         // Inner and outer loop indexes.
      Rj =  x7;

      Rhi_ab = x28;     // Product registers: low and high parts
      Rlo_ab = x29;     // of a*b and m*n.
      Rhi_mn = x30;
      Rlo_mn = x31;

      // x18 and up are callee-saved.
      _toSave = RegSet::range(x18, *regs) + Pm_base;
    }

  private:
    void save_regs() {
      push_reg(_toSave, sp);
    }

    void restore_regs() {
      pop_reg(_toSave, sp);
    }

    template <typename T>
    void unroll_2(Register count, T block) {
      Label loop, end, odd;
      beqz(count, end);
      test_bit(t0, count, 0);
      bnez(t0, odd);
      align(16);
      bind(loop);
      (this->*block)();
      bind(odd);
      (this->*block)();
      addi(count, count, -2);
      bgtz(count, loop);
      bind(end);
    }

    template <typename T>
    void unroll_2(Register count, T block, Register d, Register s, Register tmp) {
      Label loop, end, odd;
      beqz(count, end);
      test_bit(tmp, count, 0);
      bnez(tmp, odd);
      align(16);
      bind(loop);
      (this->*block)(d, s, tmp);
      bind(odd);
      (this->*block)(d, s, tmp);
      addi(count, count, -2);
      bgtz(count, loop);
      bind(end);
    }

    void pre1(RegisterOrConstant i) {
      block_comment("pre1");
      // Pa = Pa_base;
      // Pb = Pb_base + i;
      // Pm = Pm_base;
      // Pn = Pn_base + i;
      // Ra = *Pa;
      // Rb = *Pb;
      // Rm = *Pm;
      // Rn = *Pn;
      if (i.is_register()) {
        slli(t0, i.as_register(), LogBytesPerWord);
      } else {
        mv(t0, i.as_constant());
        slli(t0, t0, LogBytesPerWord);
      }

      mv(Pa, Pa_base);
      add(Pb, Pb_base, t0);
      mv(Pm, Pm_base);
      add(Pn, Pn_base, t0);

      ld(Ra, Address(Pa));
      ld(Rb, Address(Pb));
      ld(Rm, Address(Pm));
      ld(Rn, Address(Pn));

      // Zero the m*n result.
      mv(Rhi_mn, zr);
      mv(Rlo_mn, zr);
    }

    // The core multiply-accumulate step of a Montgomery
    // multiplication.  The idea is to schedule operations as a
    // pipeline so that instructions with long latencies (loads and
    // multiplies) have time to complete before their results are
    // used.  This most benefits in-order implementations of the
    // architecture but out-of-order ones also benefit.
    void step() {
      block_comment("step");
      // MACC(Ra, Rb, tmp0, tmp1, tmp2);
      // Ra = *++Pa;
      // Rb = *--Pb;
      mulhu(Rhi_ab, Ra, Rb);
      mul(Rlo_ab, Ra, Rb);
      addi(Pa, Pa, wordSize);
      ld(Ra, Address(Pa));
      addi(Pb, Pb, -wordSize);
      ld(Rb, Address(Pb));
      acc(Rhi_mn, Rlo_mn, tmp0, tmp1, tmp2); // The pending m*n from the
                                            // previous iteration.
      // MACC(Rm, Rn, tmp0, tmp1, tmp2);
      // Rm = *++Pm;
      // Rn = *--Pn;
      mulhu(Rhi_mn, Rm, Rn);
      mul(Rlo_mn, Rm, Rn);
      addi(Pm, Pm, wordSize);
      ld(Rm, Address(Pm));
      addi(Pn, Pn, -wordSize);
      ld(Rn, Address(Pn));
      acc(Rhi_ab, Rlo_ab, tmp0, tmp1, tmp2);
    }

    void post1() {
      block_comment("post1");

      // MACC(Ra, Rb, tmp0, tmp1, tmp2);
      // Ra = *++Pa;
      // Rb = *--Pb;
      mulhu(Rhi_ab, Ra, Rb);
      mul(Rlo_ab, Ra, Rb);
      acc(Rhi_mn, Rlo_mn, tmp0, tmp1, tmp2);  // The pending m*n
      acc(Rhi_ab, Rlo_ab, tmp0, tmp1, tmp2);

      // *Pm = Rm = tmp0 * inv;
      mul(Rm, tmp0, inv);
      sd(Rm, Address(Pm));

      // MACC(Rm, Rn, tmp0, tmp1, tmp2);
      // tmp0 = tmp1; tmp1 = tmp2; tmp2 = 0;
      mulhu(Rhi_mn, Rm, Rn);

#ifndef PRODUCT
      // assert(m[i] * n[0] + tmp0 == 0, "broken Montgomery multiply");
      {
        mul(Rlo_mn, Rm, Rn);
        add(Rlo_mn, tmp0, Rlo_mn);
        Label ok;
        beqz(Rlo_mn, ok);
        stop("broken Montgomery multiply");
        bind(ok);
      }
#endif
      // We have very carefully set things up so that
      // m[i]*n[0] + tmp0 == 0 (mod b), so we don't have to calculate
      // the lower half of Rm * Rn because we know the result already:
      // it must be -tmp0.  tmp0 + (-tmp0) must generate a carry iff
      // tmp0 != 0.  So, rather than do a mul and an cad we just set
      // the carry flag iff tmp0 is nonzero.
      //
      // mul(Rlo_mn, Rm, Rn);
      // cad(zr, tmp0, Rlo_mn);
      addi(t0, tmp0, -1);
      sltu(t0, t0, tmp0); // Set carry iff tmp0 is nonzero
      cadc(tmp0, tmp1, Rhi_mn, t0);
      adc(tmp1, tmp2, zr, t0);
      mv(tmp2, zr);
    }

    void pre2(Register i, Register len) {
      block_comment("pre2");
      // Pa = Pa_base + i-len;
      // Pb = Pb_base + len;
      // Pm = Pm_base + i-len;
      // Pn = Pn_base + len;

      sub(Rj, i, len);
      // Rj == i-len

      // Ra as temp register
      slli(Ra, Rj, LogBytesPerWord);
      add(Pa, Pa_base, Ra);
      add(Pm, Pm_base, Ra);
      slli(Ra, len, LogBytesPerWord);
      add(Pb, Pb_base, Ra);
      add(Pn, Pn_base, Ra);

      // Ra = *++Pa;
      // Rb = *--Pb;
      // Rm = *++Pm;
      // Rn = *--Pn;
      add(Pa, Pa, wordSize);
      ld(Ra, Address(Pa));
      add(Pb, Pb, -wordSize);
      ld(Rb, Address(Pb));
      add(Pm, Pm, wordSize);
      ld(Rm, Address(Pm));
      add(Pn, Pn, -wordSize);
      ld(Rn, Address(Pn));

      mv(Rhi_mn, zr);
      mv(Rlo_mn, zr);
    }

    void post2(Register i, Register len) {
      block_comment("post2");
      sub(Rj, i, len);

      cad(tmp0, tmp0, Rlo_mn, t0); // The pending m*n, low part

      // As soon as we know the least significant digit of our result,
      // store it.
      // Pm_base[i-len] = tmp0;
      // Rj as temp register
      slli(Rj, Rj, LogBytesPerWord);
      add(Rj, Pm_base, Rj);
      sd(tmp0, Address(Rj));

      // tmp0 = tmp1; tmp1 = tmp2; tmp2 = 0;
      cadc(tmp0, tmp1, Rhi_mn, t0); // The pending m*n, high part
      adc(tmp1, tmp2, zr, t0);
      mv(tmp2, zr);
    }

    // A carry in tmp0 after Montgomery multiplication means that we
    // should subtract multiples of n from our result in m.  We'll
    // keep doing that until there is no carry.
    void normalize(Register len) {
      block_comment("normalize");
      // while (tmp0)
      //   tmp0 = sub(Pm_base, Pn_base, tmp0, len);
      Label loop, post, again;
      Register cnt = tmp1, i = tmp2; // Re-use registers; we're done with them now
      beqz(tmp0, post); {
        bind(again); {
          mv(i, zr);
          mv(cnt, len);
          slli(Rn, i, LogBytesPerWord);
          add(Rm, Pm_base, Rn);
          ld(Rm, Address(Rm));
          add(Rn, Pn_base, Rn);
          ld(Rn, Address(Rn));
          mv(t0, 1); // set carry flag, i.e. no borrow
          align(16);
          bind(loop); {
            notr(Rn, Rn);
            add(Rm, Rm, t0);
            add(Rm, Rm, Rn);
            sltu(t0, Rm, Rn);
            slli(Rn, i, LogBytesPerWord); // Rn as temp register
            add(Rn, Pm_base, Rn);
            sd(Rm, Address(Rn));
            add(i, i, 1);
            slli(Rn, i, LogBytesPerWord);
            add(Rm, Pm_base, Rn);
            ld(Rm, Address(Rm));
            add(Rn, Pn_base, Rn);
            ld(Rn, Address(Rn));
            sub(cnt, cnt, 1);
          } bnez(cnt, loop);
          addi(tmp0, tmp0, -1);
          add(tmp0, tmp0, t0);
        } bnez(tmp0, again);
      } bind(post);
    }

    // Move memory at s to d, reversing words.
    //    Increments d to end of copied memory
    //    Destroys tmp1, tmp2
    //    Preserves len
    //    Leaves s pointing to the address which was in d at start
    void reverse(Register d, Register s, Register len, Register tmp1, Register tmp2) {
      assert(tmp1->encoding() < x28->encoding(), "register corruption");
      assert(tmp2->encoding() < x28->encoding(), "register corruption");

      shadd(s, len, s, tmp1, LogBytesPerWord);
      mv(tmp1, len);
      unroll_2(tmp1,  &MontgomeryMultiplyGenerator::reverse1, d, s, tmp2);
      slli(tmp1, len, LogBytesPerWord);
      sub(s, d, tmp1);
    }
    // [63...0] -> [31...0][63...32]
    void reverse1(Register d, Register s, Register tmp) {
      addi(s, s, -wordSize);
      ld(tmp, Address(s));
      ror_imm(tmp, tmp, 32, t0);
      sd(tmp, Address(d));
      addi(d, d, wordSize);
    }

    void step_squaring() {
      // An extra ACC
      step();
      acc(Rhi_ab, Rlo_ab, tmp0, tmp1, tmp2);
    }

    void last_squaring(Register i) {
      Label dont;
      // if ((i & 1) == 0) {
      test_bit(t0, i, 0);
      bnez(t0, dont); {
        // MACC(Ra, Rb, tmp0, tmp1, tmp2);
        // Ra = *++Pa;
        // Rb = *--Pb;
        mulhu(Rhi_ab, Ra, Rb);
        mul(Rlo_ab, Ra, Rb);
        acc(Rhi_ab, Rlo_ab, tmp0, tmp1, tmp2);
      } bind(dont);
    }

    void extra_step_squaring() {
      acc(Rhi_mn, Rlo_mn, tmp0, tmp1, tmp2);  // The pending m*n

      // MACC(Rm, Rn, tmp0, tmp1, tmp2);
      // Rm = *++Pm;
      // Rn = *--Pn;
      mulhu(Rhi_mn, Rm, Rn);
      mul(Rlo_mn, Rm, Rn);
      addi(Pm, Pm, wordSize);
      ld(Rm, Address(Pm));
      addi(Pn, Pn, -wordSize);
      ld(Rn, Address(Pn));
    }

    void post1_squaring() {
      acc(Rhi_mn, Rlo_mn, tmp0, tmp1, tmp2);  // The pending m*n

      // *Pm = Rm = tmp0 * inv;
      mul(Rm, tmp0, inv);
      sd(Rm, Address(Pm));

      // MACC(Rm, Rn, tmp0, tmp1, tmp2);
      // tmp0 = tmp1; tmp1 = tmp2; tmp2 = 0;
      mulhu(Rhi_mn, Rm, Rn);

#ifndef PRODUCT
      // assert(m[i] * n[0] + tmp0 == 0, "broken Montgomery multiply");
      {
        mul(Rlo_mn, Rm, Rn);
        add(Rlo_mn, tmp0, Rlo_mn);
        Label ok;
        beqz(Rlo_mn, ok); {
          stop("broken Montgomery multiply");
        } bind(ok);
      }
#endif
      // We have very carefully set things up so that
      // m[i]*n[0] + tmp0 == 0 (mod b), so we don't have to calculate
      // the lower half of Rm * Rn because we know the result already:
      // it must be -tmp0.  tmp0 + (-tmp0) must generate a carry iff
      // tmp0 != 0.  So, rather than do a mul and a cad we just set
      // the carry flag iff tmp0 is nonzero.
      //
      // mul(Rlo_mn, Rm, Rn);
      // cad(zr, tmp, Rlo_mn);
      addi(t0, tmp0, -1);
      sltu(t0, t0, tmp0); // Set carry iff tmp0 is nonzero
      cadc(tmp0, tmp1, Rhi_mn, t0);
      adc(tmp1, tmp2, zr, t0);
      mv(tmp2, zr);
    }

    // use t0 as carry
    void acc(Register Rhi, Register Rlo,
             Register tmp0, Register tmp1, Register tmp2) {
      cad(tmp0, tmp0, Rlo, t0);
      cadc(tmp1, tmp1, Rhi, t0);
      adc(tmp2, tmp2, zr, t0);
    }

  public:
    /**
     * Fast Montgomery multiplication.  The derivation of the
     * algorithm is in A Cryptographic Library for the Motorola
     * DSP56000, Dusse and Kaliski, Proc. EUROCRYPT 90, pp. 230-237.
     *
     * Arguments:
     *
     * Inputs for multiplication:
     *   c_rarg0   - int array elements a
     *   c_rarg1   - int array elements b
     *   c_rarg2   - int array elements n (the modulus)
     *   c_rarg3   - int length
     *   c_rarg4   - int inv
     *   c_rarg5   - int array elements m (the result)
     *
     * Inputs for squaring:
     *   c_rarg0   - int array elements a
     *   c_rarg1   - int array elements n (the modulus)
     *   c_rarg2   - int length
     *   c_rarg3   - int inv
     *   c_rarg4   - int array elements m (the result)
     *
     */
    address generate_multiply() {
      Label argh, nothing;
      bind(argh);
      stop("MontgomeryMultiply total_allocation must be <= 8192");

      align(CodeEntryAlignment);
      address entry = pc();

      beqz(Rlen, nothing);

      enter();

      // Make room.
      mv(Ra, 512);
      bgt(Rlen, Ra, argh);
      slli(Ra, Rlen, exact_log2(4 * sizeof(jint)));
      sub(Ra, sp, Ra);
      andi(sp, Ra, -2 * wordSize);

      srliw(Rlen, Rlen, 1);  // length in longwords = len/2

      {
        // Copy input args, reversing as we go.  We use Ra as a
        // temporary variable.
        reverse(Ra, Pa_base, Rlen, Ri, Rj);
        if (!_squaring)
          reverse(Ra, Pb_base, Rlen, Ri, Rj);
        reverse(Ra, Pn_base, Rlen, Ri, Rj);
      }

      // Push all call-saved registers and also Pm_base which we'll need
      // at the end.
      save_regs();

#ifndef PRODUCT
      // assert(inv * n[0] == -1UL, "broken inverse in Montgomery multiply");
      {
        ld(Rn, Address(Pn_base));
        mul(Rlo_mn, Rn, inv);
        mv(t0, -1);
        Label ok;
        beq(Rlo_mn, t0, ok);
        stop("broken inverse in Montgomery multiply");
        bind(ok);
      }
#endif

      mv(Pm_base, Ra);

      mv(tmp0, zr);
      mv(tmp1, zr);
      mv(tmp2, zr);

      block_comment("for (int i = 0; i < len; i++) {");
      mv(Ri, zr); {
        Label loop, end;
        bge(Ri, Rlen, end);

        bind(loop);
        pre1(Ri);

        block_comment("  for (j = i; j; j--) {"); {
          mv(Rj, Ri);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::step);
        } block_comment("  } // j");

        post1();
        addw(Ri, Ri, 1);
        blt(Ri, Rlen, loop);
        bind(end);
        block_comment("} // i");
      }

      block_comment("for (int i = len; i < 2*len; i++) {");
      mv(Ri, Rlen); {
        Label loop, end;
        slli(t0, Rlen, 1);
        bge(Ri, t0, end);

        bind(loop);
        pre2(Ri, Rlen);

        block_comment("  for (j = len*2-i-1; j; j--) {"); {
          slliw(Rj, Rlen, 1);
          subw(Rj, Rj, Ri);
          subw(Rj, Rj, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::step);
        } block_comment("  } // j");

        post2(Ri, Rlen);
        addw(Ri, Ri, 1);
        slli(t0, Rlen, 1);
        blt(Ri, t0, loop);
        bind(end);
      }
      block_comment("} // i");

      normalize(Rlen);

      mv(Ra, Pm_base);  // Save Pm_base in Ra
      restore_regs();  // Restore caller's Pm_base

      // Copy our result into caller's Pm_base
      reverse(Pm_base, Ra, Rlen, Ri, Rj);

      leave();
      bind(nothing);
      ret();

      return entry;
    }

    /**
     *
     * Arguments:
     *
     * Inputs:
     *   c_rarg0   - int array elements a
     *   c_rarg1   - int array elements n (the modulus)
     *   c_rarg2   - int length
     *   c_rarg3   - int inv
     *   c_rarg4   - int array elements m (the result)
     *
     */
    address generate_square() {
      Label argh;
      bind(argh);
      stop("MontgomeryMultiply total_allocation must be <= 8192");

      align(CodeEntryAlignment);
      address entry = pc();

      enter();

      // Make room.
      mv(Ra, 512);
      bgt(Rlen, Ra, argh);
      slli(Ra, Rlen, exact_log2(4 * sizeof(jint)));
      sub(Ra, sp, Ra);
      andi(sp, Ra, -2 * wordSize);

      srliw(Rlen, Rlen, 1);  // length in longwords = len/2

      {
        // Copy input args, reversing as we go.  We use Ra as a
        // temporary variable.
        reverse(Ra, Pa_base, Rlen, Ri, Rj);
        reverse(Ra, Pn_base, Rlen, Ri, Rj);
      }

      // Push all call-saved registers and also Pm_base which we'll need
      // at the end.
      save_regs();

      mv(Pm_base, Ra);

      mv(tmp0, zr);
      mv(tmp1, zr);
      mv(tmp2, zr);

      block_comment("for (int i = 0; i < len; i++) {");
      mv(Ri, zr); {
        Label loop, end;
        bind(loop);
        bge(Ri, Rlen, end);

        pre1(Ri);

        block_comment("for (j = (i+1)/2; j; j--) {"); {
          addi(Rj, Ri, 1);
          srliw(Rj, Rj, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::step_squaring);
        } block_comment("  } // j");

        last_squaring(Ri);

        block_comment("  for (j = i/2; j; j--) {"); {
          srliw(Rj, Ri, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::extra_step_squaring);
        } block_comment("  } // j");

        post1_squaring();
        addi(Ri, Ri, 1);
        blt(Ri, Rlen, loop);

        bind(end);
        block_comment("} // i");
      }

      block_comment("for (int i = len; i < 2*len; i++) {");
      mv(Ri, Rlen); {
        Label loop, end;
        bind(loop);
        slli(t0, Rlen, 1);
        bge(Ri, t0, end);

        pre2(Ri, Rlen);

        block_comment("  for (j = (2*len-i-1)/2; j; j--) {"); {
          slli(Rj, Rlen, 1);
          sub(Rj, Rj, Ri);
          sub(Rj, Rj, 1);
          srliw(Rj, Rj, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::step_squaring);
        } block_comment("  } // j");

        last_squaring(Ri);

        block_comment("  for (j = (2*len-i)/2; j; j--) {"); {
          slli(Rj, Rlen, 1);
          sub(Rj, Rj, Ri);
          srliw(Rj, Rj, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::extra_step_squaring);
        } block_comment("  } // j");

        post2(Ri, Rlen);
        addi(Ri, Ri, 1);
        slli(t0, Rlen, 1);
        blt(Ri, t0, loop);

        bind(end);
        block_comment("} // i");
      }

      normalize(Rlen);

      mv(Ra, Pm_base);  // Save Pm_base in Ra
      restore_regs();  // Restore caller's Pm_base

      // Copy our result into caller's Pm_base
      reverse(Pm_base, Ra, Rlen, Ri, Rj);

      leave();
      ret();

      return entry;
    }
  };

#endif // COMPILER2

  address generate_cont_thaw(Continuation::thaw_kind kind) {
    bool return_barrier = Continuation::is_thaw_return_barrier(kind);
    bool return_barrier_exception = Continuation::is_thaw_return_barrier_exception(kind);

    address start = __ pc();

    if (return_barrier) {
      __ ld(sp, Address(xthread, JavaThread::cont_entry_offset()));
    }

#ifndef PRODUCT
    {
      Label OK;
      __ ld(t0, Address(xthread, JavaThread::cont_entry_offset()));
      __ beq(sp, t0, OK);
      __ stop("incorrect sp");
      __ bind(OK);
    }
#endif

    if (return_barrier) {
      // preserve possible return value from a method returning to the return barrier
      __ sub(sp, sp, 2 * wordSize);
      __ fsd(f10, Address(sp, 0 * wordSize));
      __ sd(x10, Address(sp, 1 * wordSize));
    }

    __ mv(c_rarg1, (return_barrier ? 1 : 0));
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, Continuation::prepare_thaw), xthread, c_rarg1);
    __ mv(t1, x10); // x10 contains the size of the frames to thaw, 0 if overflow or no more frames

    if (return_barrier) {
      // restore return value (no safepoint in the call to thaw, so even an oop return value should be OK)
      __ ld(x10, Address(sp, 1 * wordSize));
      __ fld(f10, Address(sp, 0 * wordSize));
      __ add(sp, sp, 2 * wordSize);
    }

#ifndef PRODUCT
    {
      Label OK;
      __ ld(t0, Address(xthread, JavaThread::cont_entry_offset()));
      __ beq(sp, t0, OK);
      __ stop("incorrect sp");
      __ bind(OK);
    }
#endif

    Label thaw_success;
    // t1 contains the size of the frames to thaw, 0 if overflow or no more frames
    __ bnez(t1, thaw_success);
    __ la(t0, ExternalAddress(StubRoutines::throw_StackOverflowError_entry()));
    __ jr(t0);
    __ bind(thaw_success);

    // make room for the thawed frames
    __ sub(t0, sp, t1);
    __ andi(sp, t0, -16); // align

    if (return_barrier) {
      // save original return value -- again
      __ sub(sp, sp, 2 * wordSize);
      __ fsd(f10, Address(sp, 0 * wordSize));
      __ sd(x10, Address(sp, 1 * wordSize));
    }

    // If we want, we can templatize thaw by kind, and have three different entries
    __ mv(c_rarg1, kind);

    __ call_VM_leaf(Continuation::thaw_entry(), xthread, c_rarg1);
    __ mv(t1, x10); // x10 is the sp of the yielding frame

    if (return_barrier) {
      // restore return value (no safepoint in the call to thaw, so even an oop return value should be OK)
      __ ld(x10, Address(sp, 1 * wordSize));
      __ fld(f10, Address(sp, 0 * wordSize));
      __ add(sp, sp, 2 * wordSize);
    } else {
      __ mv(x10, zr); // return 0 (success) from doYield
    }

    // we're now on the yield frame (which is in an address above us b/c sp has been pushed down)
    __ mv(fp, t1);
    __ sub(sp, t1, 2 * wordSize); // now pointing to fp spill

    if (return_barrier_exception) {
      __ ld(c_rarg1, Address(fp, -1 * wordSize)); // return address
      __ verify_oop(x10);
      __ mv(x9, x10); // save return value contaning the exception oop in callee-saved x9

      __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), xthread, c_rarg1);

      // see OptoRuntime::generate_exception_blob: x10 -- exception oop, x13 -- exception pc

      __ mv(x11, x10); // the exception handler
      __ mv(x10, x9); // restore return value contaning the exception oop
      __ verify_oop(x10);

      __ leave();
      __ mv(x13, ra);
      __ jr(x11); // the exception handler
    } else {
      // We're "returning" into the topmost thawed frame; see Thaw::push_return_frame
      __ leave();
      __ ret();
    }

    return start;
  }

  address generate_cont_thaw() {
    if (!Continuations::enabled()) return nullptr;

    StubCodeMark mark(this, "StubRoutines", "Cont thaw");
    address start = __ pc();
    generate_cont_thaw(Continuation::thaw_top);
    return start;
  }

  address generate_cont_returnBarrier() {
    if (!Continuations::enabled()) return nullptr;

    // TODO: will probably need multiple return barriers depending on return type
    StubCodeMark mark(this, "StubRoutines", "cont return barrier");
    address start = __ pc();

    generate_cont_thaw(Continuation::thaw_return_barrier);

    return start;
  }

  address generate_cont_returnBarrier_exception() {
    if (!Continuations::enabled()) return nullptr;

    StubCodeMark mark(this, "StubRoutines", "cont return barrier exception handler");
    address start = __ pc();

    generate_cont_thaw(Continuation::thaw_return_barrier_exception);

    return start;
  }

#if COMPILER2_OR_JVMCI

#undef __
#define __ this->

  class Sha2Generator : public MacroAssembler {
    StubCodeGenerator* _cgen;
   public:
      Sha2Generator(MacroAssembler* masm, StubCodeGenerator* cgen) : MacroAssembler(masm->code()), _cgen(cgen) {}
      address generate_sha256_implCompress(bool multi_block) {
        return generate_sha2_implCompress(Assembler::e32, multi_block);
      }
      address generate_sha512_implCompress(bool multi_block) {
        return generate_sha2_implCompress(Assembler::e64, multi_block);
      }
   private:

    void vleXX_v(Assembler::SEW vset_sew, VectorRegister vr, Register sr) {
      if (vset_sew == Assembler::e32) __ vle32_v(vr, sr);
      else                            __ vle64_v(vr, sr);
    }

    void vseXX_v(Assembler::SEW vset_sew, VectorRegister vr, Register sr) {
      if (vset_sew == Assembler::e32) __ vse32_v(vr, sr);
      else                            __ vse64_v(vr, sr);
    }

    // Overview of the logic in each "quad round".
    //
    // The code below repeats 16/20 times the logic implementing four rounds
    // of the SHA-256/512 core loop as documented by NIST. 16/20 "quad rounds"
    // to implementing the 64/80 single rounds.
    //
    //    // Load four word (u32/64) constants (K[t+3], K[t+2], K[t+1], K[t+0])
    //    // Output:
    //    //   vTmp1 = {K[t+3], K[t+2], K[t+1], K[t+0]}
    //    vl1reXX.v vTmp1, ofs
    //
    //    // Increment word constant address by stride (16/32 bytes, 4*4B/8B, 128b/256b)
    //    addi ofs, ofs, 16/32
    //
    //    // Add constants to message schedule words:
    //    //  Input
    //    //    vTmp1 = {K[t+3], K[t+2], K[t+1], K[t+0]}
    //    //    vW0 = {W[t+3], W[t+2], W[t+1], W[t+0]}; // Vt0 = W[3:0];
    //    //  Output
    //    //    vTmp0 = {W[t+3]+K[t+3], W[t+2]+K[t+2], W[t+1]+K[t+1], W[t+0]+K[t+0]}
    //    vadd.vv vTmp0, vTmp1, vW0
    //
    //    //  2 rounds of working variables updates.
    //    //     vState1[t+4] <- vState1[t], vState0[t], vTmp0[t]
    //    //  Input:
    //    //    vState1 = {c[t],d[t],g[t],h[t]}   " = vState1[t] "
    //    //    vState0 = {a[t],b[t],e[t],f[t]}
    //    //    vTmp0 = {W[t+3]+K[t+3], W[t+2]+K[t+2], W[t+1]+K[t+1], W[t+0]+K[t+0]}
    //    //  Output:
    //    //    vState1 = {f[t+2],e[t+2],b[t+2],a[t+2]}  " = vState0[t+2] "
    //    //        = {h[t+4],g[t+4],d[t+4],c[t+4]}  " = vState1[t+4] "
    //    vsha2cl.vv vState1, vState0, vTmp0
    //
    //    //  2 rounds of working variables updates.
    //    //     vState0[t+4] <- vState0[t], vState0[t+2], vTmp0[t]
    //    //  Input
    //    //   vState0 = {a[t],b[t],e[t],f[t]}       " = vState0[t] "
    //    //       = {h[t+2],g[t+2],d[t+2],c[t+2]}   " = vState1[t+2] "
    //    //   vState1 = {f[t+2],e[t+2],b[t+2],a[t+2]}   " = vState0[t+2] "
    //    //   vTmp0 = {W[t+3]+K[t+3], W[t+2]+K[t+2], W[t+1]+K[t+1], W[t+0]+K[t+0]}
    //    //  Output:
    //    //   vState0 = {f[t+4],e[t+4],b[t+4],a[t+4]}   " = vState0[t+4] "
    //    vsha2ch.vv vState0, vState1, vTmp0
    //
    //    // Combine 2QW into 1QW
    //    //
    //    // To generate the next 4 words, "new_vW0"/"vTmp0" from vW0-vW3, vsha2ms needs
    //    //     vW0[0..3], vW1[0], vW2[1..3], vW3[0, 2..3]
    //    // and it can only take 3 vectors as inputs. Hence we need to combine
    //    // vW1[0] and vW2[1..3] in a single vector.
    //    //
    //    // vmerge Vt4, Vt1, Vt2, V0
    //    // Input
    //    //  V0 = mask // first word from vW2, 1..3 words from vW1
    //    //  vW2 = {Wt-8, Wt-7, Wt-6, Wt-5}
    //    //  vW1 = {Wt-12, Wt-11, Wt-10, Wt-9}
    //    // Output
    //    //  Vt4 = {Wt-12, Wt-7, Wt-6, Wt-5}
    //    vmerge.vvm vTmp0, vW2, vW1, v0
    //
    //    // Generate next Four Message Schedule Words (hence allowing for 4 more rounds)
    //    // Input
    //    //  vW0 = {W[t+ 3], W[t+ 2], W[t+ 1], W[t+ 0]}     W[ 3: 0]
    //    //  vW3 = {W[t+15], W[t+14], W[t+13], W[t+12]}     W[15:12]
    //    //  vTmp0 = {W[t+11], W[t+10], W[t+ 9], W[t+ 4]}     W[11: 9,4]
    //    // Output (next four message schedule words)
    //    //  vW0 = {W[t+19],  W[t+18],  W[t+17],  W[t+16]}  W[19:16]
    //    vsha2ms.vv vW0, vTmp0, vW3
    //
    // BEFORE
    //  vW0 - vW3 hold the message schedule words (initially the block words)
    //    vW0 = W[ 3: 0]   "oldest"
    //    vW1 = W[ 7: 4]
    //    vW2 = W[11: 8]
    //    vW3 = W[15:12]   "newest"
    //
    //  vt6 - vt7 hold the working state variables
    //    vState0 = {a[t],b[t],e[t],f[t]}   // initially {H5,H4,H1,H0}
    //    vState1 = {c[t],d[t],g[t],h[t]}   // initially {H7,H6,H3,H2}
    //
    // AFTER
    //  vW0 - vW3 hold the message schedule words (initially the block words)
    //    vW1 = W[ 7: 4]   "oldest"
    //    vW2 = W[11: 8]
    //    vW3 = W[15:12]
    //    vW0 = W[19:16]   "newest"
    //
    //  vState0 and vState1 hold the working state variables
    //    vState0 = {a[t+4],b[t+4],e[t+4],f[t+4]}
    //    vState1 = {c[t+4],d[t+4],g[t+4],h[t+4]}
    //
    //  The group of vectors vW0,vW1,vW2,vW3 is "rotated" by one in each quad-round,
    //  hence the uses of those vectors rotate in each round, and we get back to the
    //  initial configuration every 4 quad-rounds. We could avoid those changes at
    //  the cost of moving those vectors at the end of each quad-rounds.
    void sha2_quad_round(Assembler::SEW vset_sew, VectorRegister rot1, VectorRegister rot2, VectorRegister rot3, VectorRegister rot4,
                         Register scalarconst, VectorRegister vtemp, VectorRegister vtemp2, VectorRegister v_abef, VectorRegister v_cdgh,
                         bool gen_words = true, bool step_const = true) {
      __ vleXX_v(vset_sew, vtemp, scalarconst);
      if (step_const) {
        __ addi(scalarconst, scalarconst, vset_sew == Assembler::e32 ? 16 : 32);
      }
      __ vadd_vv(vtemp2, vtemp, rot1);
      __ vsha2cl_vv(v_cdgh, v_abef, vtemp2);
      __ vsha2ch_vv(v_abef, v_cdgh, vtemp2);
      if (gen_words) {
        __ vmerge_vvm(vtemp2, rot3, rot2);
        __ vsha2ms_vv(rot1, vtemp2, rot4);
      }
    }

    const char* stub_name(Assembler::SEW vset_sew, bool multi_block) {
      if (vset_sew == Assembler::e32 && !multi_block) return "sha256_implCompress";
      if (vset_sew == Assembler::e32 &&  multi_block) return "sha256_implCompressMB";
      if (vset_sew == Assembler::e64 && !multi_block) return "sha512_implCompress";
      if (vset_sew == Assembler::e64 &&  multi_block) return "sha512_implCompressMB";
      ShouldNotReachHere();
      return "bad name lookup";
    }

    // Arguments:
    //
    // Inputs:
    //   c_rarg0   - byte[]  source+offset
    //   c_rarg1   - int[]   SHA.state
    //   c_rarg2   - int     offset
    //   c_rarg3   - int     limit
    //
    address generate_sha2_implCompress(Assembler::SEW vset_sew, bool multi_block) {
      alignas(64) static const uint32_t round_consts_256[64] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
        0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
        0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
        0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
        0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
      };
      alignas(64) static const uint64_t round_consts_512[80] = {
        0x428a2f98d728ae22l, 0x7137449123ef65cdl, 0xb5c0fbcfec4d3b2fl,
        0xe9b5dba58189dbbcl, 0x3956c25bf348b538l, 0x59f111f1b605d019l,
        0x923f82a4af194f9bl, 0xab1c5ed5da6d8118l, 0xd807aa98a3030242l,
        0x12835b0145706fbel, 0x243185be4ee4b28cl, 0x550c7dc3d5ffb4e2l,
        0x72be5d74f27b896fl, 0x80deb1fe3b1696b1l, 0x9bdc06a725c71235l,
        0xc19bf174cf692694l, 0xe49b69c19ef14ad2l, 0xefbe4786384f25e3l,
        0x0fc19dc68b8cd5b5l, 0x240ca1cc77ac9c65l, 0x2de92c6f592b0275l,
        0x4a7484aa6ea6e483l, 0x5cb0a9dcbd41fbd4l, 0x76f988da831153b5l,
        0x983e5152ee66dfabl, 0xa831c66d2db43210l, 0xb00327c898fb213fl,
        0xbf597fc7beef0ee4l, 0xc6e00bf33da88fc2l, 0xd5a79147930aa725l,
        0x06ca6351e003826fl, 0x142929670a0e6e70l, 0x27b70a8546d22ffcl,
        0x2e1b21385c26c926l, 0x4d2c6dfc5ac42aedl, 0x53380d139d95b3dfl,
        0x650a73548baf63del, 0x766a0abb3c77b2a8l, 0x81c2c92e47edaee6l,
        0x92722c851482353bl, 0xa2bfe8a14cf10364l, 0xa81a664bbc423001l,
        0xc24b8b70d0f89791l, 0xc76c51a30654be30l, 0xd192e819d6ef5218l,
        0xd69906245565a910l, 0xf40e35855771202al, 0x106aa07032bbd1b8l,
        0x19a4c116b8d2d0c8l, 0x1e376c085141ab53l, 0x2748774cdf8eeb99l,
        0x34b0bcb5e19b48a8l, 0x391c0cb3c5c95a63l, 0x4ed8aa4ae3418acbl,
        0x5b9cca4f7763e373l, 0x682e6ff3d6b2b8a3l, 0x748f82ee5defb2fcl,
        0x78a5636f43172f60l, 0x84c87814a1f0ab72l, 0x8cc702081a6439ecl,
        0x90befffa23631e28l, 0xa4506cebde82bde9l, 0xbef9a3f7b2c67915l,
        0xc67178f2e372532bl, 0xca273eceea26619cl, 0xd186b8c721c0c207l,
        0xeada7dd6cde0eb1el, 0xf57d4f7fee6ed178l, 0x06f067aa72176fbal,
        0x0a637dc5a2c898a6l, 0x113f9804bef90dael, 0x1b710b35131c471bl,
        0x28db77f523047d84l, 0x32caab7b40c72493l, 0x3c9ebe0a15c9bebcl,
        0x431d67c49c100d4cl, 0x4cc5d4becb3e42b6l, 0x597f299cfc657e2al,
        0x5fcb6fab3ad6faecl, 0x6c44198c4a475817l
      };
      const int const_add = vset_sew == Assembler::e32 ? 16 : 32;

      __ align(CodeEntryAlignment);
      StubCodeMark mark(_cgen, "StubRoutines", stub_name(vset_sew, multi_block));
      address start = __ pc();

      Register buf   = c_rarg0;
      Register state = c_rarg1;
      Register ofs   = c_rarg2;
      Register limit = c_rarg3;
      Register consts =  t2; // caller saved
      Register state_c = x28; // caller saved
      VectorRegister vindex = v2;
      VectorRegister vW0 = v4;
      VectorRegister vW1 = v6;
      VectorRegister vW2 = v8;
      VectorRegister vW3 = v10;
      VectorRegister vState0 = v12;
      VectorRegister vState1 = v14;
      VectorRegister vHash0  = v16;
      VectorRegister vHash1  = v18;
      VectorRegister vTmp0   = v20;
      VectorRegister vTmp1   = v22;

      Label multi_block_loop;

      __ enter();

      address constant_table = vset_sew == Assembler::e32 ? (address)round_consts_256 : (address)round_consts_512;
      la(consts, ExternalAddress(constant_table));

      // Register use in this function:
      //
      // VECTORS
      //  vW0 - vW3 (512/1024-bits / 4*128/256 bits / 4*4*32/65 bits), hold the message
      //             schedule words (Wt). They start with the message block
      //             content (W0 to W15), then further words in the message
      //             schedule generated via vsha2ms from previous Wt.
      //   Initially:
      //     vW0 = W[  3:0] = { W3,  W2,  W1,  W0}
      //     vW1 = W[  7:4] = { W7,  W6,  W5,  W4}
      //     vW2 = W[ 11:8] = {W11, W10,  W9,  W8}
      //     vW3 = W[15:12] = {W15, W14, W13, W12}
      //
      //  vState0 - vState1 hold the working state variables (a, b, ..., h)
      //    vState0 = {f[t],e[t],b[t],a[t]}
      //    vState1 = {h[t],g[t],d[t],c[t]}
      //   Initially:
      //    vState0 = {H5i-1, H4i-1, H1i-1 , H0i-1}
      //    vState1 = {H7i-i, H6i-1, H3i-1 , H2i-1}
      //
      //  v0 = masks for vrgather/vmerge. Single value during the 16 rounds.
      //
      //  vTmp0 = temporary, Wt+Kt
      //  vTmp1 = temporary, Kt
      //
      //  vHash0/vHash1 = hold the initial values of the hash, byte-swapped.
      //
      // During most of the function the vector state is configured so that each
      // vector is interpreted as containing four 32/64 bits (e32/e64) elements (128/256 bits).

      // vsha2ch/vsha2cl uses EGW of 4*SEW.
      // SHA256 SEW = e32, EGW = 128-bits
      // SHA512 SEW = e64, EGW = 256-bits
      //
      // VLEN is required to be at least 128.
      // For the case of VLEN=128 and SHA512 we need LMUL=2 to work with 4*e64 (EGW = 256)
      //
      // m1: LMUL=1/2
      // ta: tail agnostic (don't care about those lanes)
      // ma: mask agnostic (don't care about those lanes)
      // x0 is not written, we known the number of vector elements.

      if (vset_sew == Assembler::e64 && MaxVectorSize == 16) { // SHA512 and VLEN = 128
        __ vsetivli(x0, 4, vset_sew, Assembler::m2, Assembler::ma, Assembler::ta);
      } else {
        __ vsetivli(x0, 4, vset_sew, Assembler::m1, Assembler::ma, Assembler::ta);
      }

      int64_t indexes = vset_sew == Assembler::e32 ? 0x00041014ul : 0x00082028ul;
      __ li(t0, indexes);
      __ vmv_v_x(vindex, t0);

      // Step-over a,b, so we are pointing to c.
      // const_add is equal to 4x state variable, div by 2 is thus 2, a,b
      __ addi(state_c, state, const_add/2);

      // Use index-load to get {f,e,b,a},{h,g,d,c}
      __ vluxei8_v(vState0, state, vindex);
      __ vluxei8_v(vState1, state_c, vindex);

      __ bind(multi_block_loop);

      // Capture the initial H values in vHash0 and vHash1 to allow for computing
      // the resulting H', since H' = H+{a',b',c',...,h'}.
      __ vmv_v_v(vHash0, vState0);
      __ vmv_v_v(vHash1, vState1);

      // Load the 512/1024-bits of the message block in vW0-vW3 and perform
      // an endian swap on each 4/8 bytes element.
      //
      // If Zvkb is not implemented one can use vrgather
      // with an index sequence to byte-swap.
      //  sequence = [3 2 1 0   7 6 5 4  11 10 9 8   15 14 13 12]
      //   <https://oeis.org/A004444> gives us "N ^ 3" as a nice formula to generate
      //  this sequence. 'vid' gives us the N.
      __ vleXX_v(vset_sew, vW0, buf);
      __ vrev8_v(vW0, vW0);
      __ addi(buf, buf, const_add);
      __ vleXX_v(vset_sew, vW1, buf);
      __ vrev8_v(vW1, vW1);
      __ addi(buf, buf, const_add);
      __ vleXX_v(vset_sew, vW2, buf);
      __ vrev8_v(vW2, vW2);
      __ addi(buf, buf, const_add);
      __ vleXX_v(vset_sew, vW3, buf);
      __ vrev8_v(vW3, vW3);
      __ addi(buf, buf, const_add);

      // Set v0 up for the vmerge that replaces the first word (idx==0)
      __ vid_v(v0);
      __ vmseq_vi(v0, v0, 0x0);  // v0.mask[i] = (i == 0 ? 1 : 0)

      VectorRegister rotation_regs[] = {vW0, vW1, vW2, vW3};
      int rot_pos = 0;
      // Quad-round #0 (+0, vW0->vW1->vW2->vW3) ... #11 (+3, vW3->vW0->vW1->vW2)
      const int qr_end = vset_sew == Assembler::e32 ? 12 : 16;
      for (int i = 0; i < qr_end; i++) {
        sha2_quad_round(vset_sew,
                   rotation_regs[(rot_pos + 0) & 0x3],
                   rotation_regs[(rot_pos + 1) & 0x3],
                   rotation_regs[(rot_pos + 2) & 0x3],
                   rotation_regs[(rot_pos + 3) & 0x3],
                   consts,
                   vTmp1, vTmp0, vState0, vState1);
        ++rot_pos;
      }
      // Quad-round #12 (+0, vW0->vW1->vW2->vW3) ... #15 (+3, vW3->vW0->vW1->vW2)
      // Note that we stop generating new message schedule words (Wt, vW0-13)
      // as we already generated all the words we end up consuming (i.e., W[63:60]).
      const int qr_c_end = qr_end + 4;
      for (int i = qr_end; i < qr_c_end; i++) {
        sha2_quad_round(vset_sew,
                   rotation_regs[(rot_pos + 0) & 0x3],
                   rotation_regs[(rot_pos + 1) & 0x3],
                   rotation_regs[(rot_pos + 2) & 0x3],
                   rotation_regs[(rot_pos + 3) & 0x3],
                   consts,
                   vTmp1, vTmp0, vState0, vState1, false, i < (qr_c_end-1));
        ++rot_pos;
      }

      //--------------------------------------------------------------------------------
      // Compute the updated hash value H'
      //   H' = H + {h',g',...,b',a'}
      //      = {h,g,...,b,a} + {h',g',...,b',a'}
      //      = {h+h',g+g',...,b+b',a+a'}

      // H' = H+{a',b',c',...,h'}
      __ vadd_vv(vState0, vHash0, vState0);
      __ vadd_vv(vState1, vHash1, vState1);

      if (multi_block) {
        int total_adds = vset_sew == Assembler::e32 ? 240 : 608;
        __ addi(consts, consts, -total_adds);
        __ add(ofs, ofs, vset_sew == Assembler::e32 ? 64 : 128);
        __ ble(ofs, limit, multi_block_loop);
        __ mv(c_rarg0, ofs); // return ofs
      }

      // Store H[0..8] = {a,b,c,d,e,f,g,h} from
      //  vState0 = {f,e,b,a}
      //  vState1 = {h,g,d,c}
      __ vsuxei8_v(vState0, state,   vindex);
      __ vsuxei8_v(vState1, state_c, vindex);

      __ leave();
      __ ret();

      return start;
    }
  };

#undef __
#define __ _masm->

  // Set of L registers that correspond to a contiguous memory area.
  // Each 64-bit register typically corresponds to 2 32-bit integers.
  template <uint L>
  class RegCache {
  private:
    MacroAssembler *_masm;
    Register _regs[L];

  public:
    RegCache(MacroAssembler *masm, RegSet rs): _masm(masm) {
      assert(rs.size() == L, "%u registers are used to cache %u 4-byte data", rs.size(), 2 * L);
      auto it = rs.begin();
      for (auto &r: _regs) {
        r = *it;
        ++it;
      }
    }

    // generate load for the i'th register
    void gen_load(uint i, Register base) {
      assert(i < L, "invalid i: %u", i);
      __ ld(_regs[i], Address(base, 8 * i));
    }

    // add i'th 32-bit integer to dest
    void add_u32(const Register dest, uint i, const Register rtmp = t0) {
      assert(i < 2 * L, "invalid i: %u", i);

      if (is_even(i)) {
        // Use the bottom 32 bits. No need to mask off the top 32 bits
        // as addw will do the right thing.
        __ addw(dest, dest, _regs[i / 2]);
      } else {
        // Use the top 32 bits by right-shifting them.
        __ srli(rtmp, _regs[i / 2], 32);
        __ addw(dest, dest, rtmp);
      }
    }
  };

  typedef RegCache<8> BufRegCache;

  // a += value + x + ac;
  // a = Integer.rotateLeft(a, s) + b;
  void m5_FF_GG_HH_II_epilogue(BufRegCache& reg_cache,
                               Register a, Register b, Register c, Register d,
                               int k, int s, int t,
                               Register value) {
    // a += ac
    __ addw(a, a, t, t1);

    // a += x;
    reg_cache.add_u32(a, k);
    // a += value;
    __ addw(a, a, value);

    // a = Integer.rotateLeft(a, s) + b;
    __ rolw_imm(a, a, s);
    __ addw(a, a, b);
  }

  // a += ((b & c) | ((~b) & d)) + x + ac;
  // a = Integer.rotateLeft(a, s) + b;
  void md5_FF(BufRegCache& reg_cache,
              Register a, Register b, Register c, Register d,
              int k, int s, int t,
              Register rtmp1, Register rtmp2) {
    // rtmp1 = b & c
    __ andr(rtmp1, b, c);

    // rtmp2 = (~b) & d
    __ andn(rtmp2, d, b);

    // rtmp1 = (b & c) | ((~b) & d)
    __ orr(rtmp1, rtmp1, rtmp2);

    m5_FF_GG_HH_II_epilogue(reg_cache, a, b, c, d, k, s, t, rtmp1);
  }

  // a += ((b & d) | (c & (~d))) + x + ac;
  // a = Integer.rotateLeft(a, s) + b;
  void md5_GG(BufRegCache& reg_cache,
              Register a, Register b, Register c, Register d,
              int k, int s, int t,
              Register rtmp1, Register rtmp2) {
    // rtmp1 = b & d
    __ andr(rtmp1, b, d);

    // rtmp2 = c & (~d)
    __ andn(rtmp2, c, d);

    // rtmp1 = (b & d) | (c & (~d))
    __ orr(rtmp1, rtmp1, rtmp2);

    m5_FF_GG_HH_II_epilogue(reg_cache, a, b, c, d, k, s, t, rtmp1);
  }

  // a += ((b ^ c) ^ d) + x + ac;
  // a = Integer.rotateLeft(a, s) + b;
  void md5_HH(BufRegCache& reg_cache,
              Register a, Register b, Register c, Register d,
              int k, int s, int t,
              Register rtmp1, Register rtmp2) {
    // rtmp1 = (b ^ c) ^ d
    __ xorr(rtmp2, b, c);
    __ xorr(rtmp1, rtmp2, d);

    m5_FF_GG_HH_II_epilogue(reg_cache, a, b, c, d, k, s, t, rtmp1);
  }

  // a += (c ^ (b | (~d))) + x + ac;
  // a = Integer.rotateLeft(a, s) + b;
  void md5_II(BufRegCache& reg_cache,
              Register a, Register b, Register c, Register d,
              int k, int s, int t,
              Register rtmp1, Register rtmp2) {
    // rtmp1 = c ^ (b | (~d))
    __ orn(rtmp2, b, d);
    __ xorr(rtmp1, c, rtmp2);

    m5_FF_GG_HH_II_epilogue(reg_cache, a, b, c, d, k, s, t, rtmp1);
  }

  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - byte[]  source+offset
  //   c_rarg1   - int[]   SHA.state
  //   c_rarg2   - int     offset  (multi_block == True)
  //   c_rarg3   - int     limit   (multi_block == True)
  //
  // Registers:
  //    x0   zero  (zero)
  //    x1     ra  (return address)
  //    x2     sp  (stack pointer)
  //    x3     gp  (global pointer)
  //    x4     tp  (thread pointer)
  //    x5     t0  (tmp register)
  //    x6     t1  (tmp register)
  //    x7     t2  state0
  //    x8  f0/s0  (frame pointer)
  //    x9     s1
  //   x10     a0  rtmp1 / c_rarg0
  //   x11     a1  rtmp2 / c_rarg1
  //   x12     a2  a     / c_rarg2
  //   x13     a3  b     / c_rarg3
  //   x14     a4  c
  //   x15     a5  d
  //   x16     a6  buf
  //   x17     a7  state
  //   x18     s2  ofs     [saved-reg]  (multi_block == True)
  //   x19     s3  limit   [saved-reg]  (multi_block == True)
  //   x20     s4  state1  [saved-reg]
  //   x21     s5  state2  [saved-reg]
  //   x22     s6  state3  [saved-reg]
  //   x23     s7
  //   x24     s8  buf0    [saved-reg]
  //   x25     s9  buf1    [saved-reg]
  //   x26    s10  buf2    [saved-reg]
  //   x27    s11  buf3    [saved-reg]
  //   x28     t3  buf4
  //   x29     t4  buf5
  //   x30     t5  buf6
  //   x31     t6  buf7
  address generate_md5_implCompress(bool multi_block, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    // rotation constants
    const int S11 = 7;
    const int S12 = 12;
    const int S13 = 17;
    const int S14 = 22;
    const int S21 = 5;
    const int S22 = 9;
    const int S23 = 14;
    const int S24 = 20;
    const int S31 = 4;
    const int S32 = 11;
    const int S33 = 16;
    const int S34 = 23;
    const int S41 = 6;
    const int S42 = 10;
    const int S43 = 15;
    const int S44 = 21;

    const int64_t mask32 = 0xffffffff;

    Register buf_arg   = c_rarg0; // a0
    Register state_arg = c_rarg1; // a1
    Register ofs_arg   = c_rarg2; // a2
    Register limit_arg = c_rarg3; // a3

    // we'll copy the args to these registers to free up a0-a3
    // to use for other values manipulated by instructions
    // that can be compressed
    Register buf       = x16; // a6
    Register state     = x17; // a7
    Register ofs       = x18; // s2
    Register limit     = x19; // s3

    // using x12->15 to allow compressed instructions
    Register a         = x12; // a2
    Register b         = x13; // a3
    Register c         = x14; // a4
    Register d         = x15; // a5

    Register state0    =  x7; // t2
    Register state1    = x20; // s4
    Register state2    = x21; // s5
    Register state3    = x22; // s6

    // using x10->x11 to allow compressed instructions
    Register rtmp1     = x10; // a0
    Register rtmp2     = x11; // a1

    RegSet reg_cache_saved_regs = RegSet::of(x24, x25, x26, x27); // s8, s9, s10, s11
    RegSet reg_cache_regs;
    reg_cache_regs += reg_cache_saved_regs;
    reg_cache_regs += RegSet::of(x28, x29, x30, x31); // t3, t4, t5, t6
    BufRegCache reg_cache(_masm, reg_cache_regs);

    RegSet saved_regs;
    if (multi_block) {
      saved_regs += RegSet::of(ofs, limit);
    }
    saved_regs += RegSet::of(state1, state2, state3);
    saved_regs += reg_cache_saved_regs;

    __ push_reg(saved_regs, sp);

    __ mv(buf, buf_arg);
    __ mv(state, state_arg);
    if (multi_block) {
      __ mv(ofs, ofs_arg);
      __ mv(limit, limit_arg);
    }

    // to minimize the number of memory operations:
    // read the 4 state 4-byte values in pairs, with a single ld,
    // and split them into 2 registers.
    //
    // And, as the core algorithm of md5 works on 32-bits words, so
    // in the following code, it does not care about the content of
    // higher 32-bits in state[x]. Based on this observation,
    // we can apply further optimization, which is to just ignore the
    // higher 32-bits in state0/state2, rather than set the higher
    // 32-bits of state0/state2 to zero explicitly with extra instructions.
    __ ld(state0, Address(state));
    __ srli(state1, state0, 32);
    __ ld(state2, Address(state, 8));
    __ srli(state3, state2, 32);

    Label md5_loop;
    __ BIND(md5_loop);

    __ mv(a, state0);
    __ mv(b, state1);
    __ mv(c, state2);
    __ mv(d, state3);

    // Round 1
    reg_cache.gen_load(0, buf);
    md5_FF(reg_cache, a, b, c, d,  0, S11, 0xd76aa478, rtmp1, rtmp2);
    md5_FF(reg_cache, d, a, b, c,  1, S12, 0xe8c7b756, rtmp1, rtmp2);
    reg_cache.gen_load(1, buf);
    md5_FF(reg_cache, c, d, a, b,  2, S13, 0x242070db, rtmp1, rtmp2);
    md5_FF(reg_cache, b, c, d, a,  3, S14, 0xc1bdceee, rtmp1, rtmp2);
    reg_cache.gen_load(2, buf);
    md5_FF(reg_cache, a, b, c, d,  4, S11, 0xf57c0faf, rtmp1, rtmp2);
    md5_FF(reg_cache, d, a, b, c,  5, S12, 0x4787c62a, rtmp1, rtmp2);
    reg_cache.gen_load(3, buf);
    md5_FF(reg_cache, c, d, a, b,  6, S13, 0xa8304613, rtmp1, rtmp2);
    md5_FF(reg_cache, b, c, d, a,  7, S14, 0xfd469501, rtmp1, rtmp2);
    reg_cache.gen_load(4, buf);
    md5_FF(reg_cache, a, b, c, d,  8, S11, 0x698098d8, rtmp1, rtmp2);
    md5_FF(reg_cache, d, a, b, c,  9, S12, 0x8b44f7af, rtmp1, rtmp2);
    reg_cache.gen_load(5, buf);
    md5_FF(reg_cache, c, d, a, b, 10, S13, 0xffff5bb1, rtmp1, rtmp2);
    md5_FF(reg_cache, b, c, d, a, 11, S14, 0x895cd7be, rtmp1, rtmp2);
    reg_cache.gen_load(6, buf);
    md5_FF(reg_cache, a, b, c, d, 12, S11, 0x6b901122, rtmp1, rtmp2);
    md5_FF(reg_cache, d, a, b, c, 13, S12, 0xfd987193, rtmp1, rtmp2);
    reg_cache.gen_load(7, buf);
    md5_FF(reg_cache, c, d, a, b, 14, S13, 0xa679438e, rtmp1, rtmp2);
    md5_FF(reg_cache, b, c, d, a, 15, S14, 0x49b40821, rtmp1, rtmp2);

    // Round 2
    md5_GG(reg_cache, a, b, c, d,  1, S21, 0xf61e2562, rtmp1, rtmp2);
    md5_GG(reg_cache, d, a, b, c,  6, S22, 0xc040b340, rtmp1, rtmp2);
    md5_GG(reg_cache, c, d, a, b, 11, S23, 0x265e5a51, rtmp1, rtmp2);
    md5_GG(reg_cache, b, c, d, a,  0, S24, 0xe9b6c7aa, rtmp1, rtmp2);
    md5_GG(reg_cache, a, b, c, d,  5, S21, 0xd62f105d, rtmp1, rtmp2);
    md5_GG(reg_cache, d, a, b, c, 10, S22, 0x02441453, rtmp1, rtmp2);
    md5_GG(reg_cache, c, d, a, b, 15, S23, 0xd8a1e681, rtmp1, rtmp2);
    md5_GG(reg_cache, b, c, d, a,  4, S24, 0xe7d3fbc8, rtmp1, rtmp2);
    md5_GG(reg_cache, a, b, c, d,  9, S21, 0x21e1cde6, rtmp1, rtmp2);
    md5_GG(reg_cache, d, a, b, c, 14, S22, 0xc33707d6, rtmp1, rtmp2);
    md5_GG(reg_cache, c, d, a, b,  3, S23, 0xf4d50d87, rtmp1, rtmp2);
    md5_GG(reg_cache, b, c, d, a,  8, S24, 0x455a14ed, rtmp1, rtmp2);
    md5_GG(reg_cache, a, b, c, d, 13, S21, 0xa9e3e905, rtmp1, rtmp2);
    md5_GG(reg_cache, d, a, b, c,  2, S22, 0xfcefa3f8, rtmp1, rtmp2);
    md5_GG(reg_cache, c, d, a, b,  7, S23, 0x676f02d9, rtmp1, rtmp2);
    md5_GG(reg_cache, b, c, d, a, 12, S24, 0x8d2a4c8a, rtmp1, rtmp2);

    // Round 3
    md5_HH(reg_cache, a, b, c, d,  5, S31, 0xfffa3942, rtmp1, rtmp2);
    md5_HH(reg_cache, d, a, b, c,  8, S32, 0x8771f681, rtmp1, rtmp2);
    md5_HH(reg_cache, c, d, a, b, 11, S33, 0x6d9d6122, rtmp1, rtmp2);
    md5_HH(reg_cache, b, c, d, a, 14, S34, 0xfde5380c, rtmp1, rtmp2);
    md5_HH(reg_cache, a, b, c, d,  1, S31, 0xa4beea44, rtmp1, rtmp2);
    md5_HH(reg_cache, d, a, b, c,  4, S32, 0x4bdecfa9, rtmp1, rtmp2);
    md5_HH(reg_cache, c, d, a, b,  7, S33, 0xf6bb4b60, rtmp1, rtmp2);
    md5_HH(reg_cache, b, c, d, a, 10, S34, 0xbebfbc70, rtmp1, rtmp2);
    md5_HH(reg_cache, a, b, c, d, 13, S31, 0x289b7ec6, rtmp1, rtmp2);
    md5_HH(reg_cache, d, a, b, c,  0, S32, 0xeaa127fa, rtmp1, rtmp2);
    md5_HH(reg_cache, c, d, a, b,  3, S33, 0xd4ef3085, rtmp1, rtmp2);
    md5_HH(reg_cache, b, c, d, a,  6, S34, 0x04881d05, rtmp1, rtmp2);
    md5_HH(reg_cache, a, b, c, d,  9, S31, 0xd9d4d039, rtmp1, rtmp2);
    md5_HH(reg_cache, d, a, b, c, 12, S32, 0xe6db99e5, rtmp1, rtmp2);
    md5_HH(reg_cache, c, d, a, b, 15, S33, 0x1fa27cf8, rtmp1, rtmp2);
    md5_HH(reg_cache, b, c, d, a,  2, S34, 0xc4ac5665, rtmp1, rtmp2);

    // Round 4
    md5_II(reg_cache, a, b, c, d,  0, S41, 0xf4292244, rtmp1, rtmp2);
    md5_II(reg_cache, d, a, b, c,  7, S42, 0x432aff97, rtmp1, rtmp2);
    md5_II(reg_cache, c, d, a, b, 14, S43, 0xab9423a7, rtmp1, rtmp2);
    md5_II(reg_cache, b, c, d, a,  5, S44, 0xfc93a039, rtmp1, rtmp2);
    md5_II(reg_cache, a, b, c, d, 12, S41, 0x655b59c3, rtmp1, rtmp2);
    md5_II(reg_cache, d, a, b, c,  3, S42, 0x8f0ccc92, rtmp1, rtmp2);
    md5_II(reg_cache, c, d, a, b, 10, S43, 0xffeff47d, rtmp1, rtmp2);
    md5_II(reg_cache, b, c, d, a,  1, S44, 0x85845dd1, rtmp1, rtmp2);
    md5_II(reg_cache, a, b, c, d,  8, S41, 0x6fa87e4f, rtmp1, rtmp2);
    md5_II(reg_cache, d, a, b, c, 15, S42, 0xfe2ce6e0, rtmp1, rtmp2);
    md5_II(reg_cache, c, d, a, b,  6, S43, 0xa3014314, rtmp1, rtmp2);
    md5_II(reg_cache, b, c, d, a, 13, S44, 0x4e0811a1, rtmp1, rtmp2);
    md5_II(reg_cache, a, b, c, d,  4, S41, 0xf7537e82, rtmp1, rtmp2);
    md5_II(reg_cache, d, a, b, c, 11, S42, 0xbd3af235, rtmp1, rtmp2);
    md5_II(reg_cache, c, d, a, b,  2, S43, 0x2ad7d2bb, rtmp1, rtmp2);
    md5_II(reg_cache, b, c, d, a,  9, S44, 0xeb86d391, rtmp1, rtmp2);

    __ addw(state0, state0, a);
    __ addw(state1, state1, b);
    __ addw(state2, state2, c);
    __ addw(state3, state3, d);

    if (multi_block) {
      __ addi(buf, buf, 64);
      __ addi(ofs, ofs, 64);
      // if (ofs <= limit) goto m5_loop
      __ bge(limit, ofs, md5_loop);
      __ mv(c_rarg0, ofs); // return ofs
    }

    // to minimize the number of memory operations:
    // write back the 4 state 4-byte values in pairs, with a single sd
    __ mv(t0, mask32);
    __ andr(state0, state0, t0);
    __ slli(state1, state1, 32);
    __ orr(state0, state0, state1);
    __ sd(state0, Address(state));
    __ andr(state2, state2, t0);
    __ slli(state3, state3, 32);
    __ orr(state2, state2, state3);
    __ sd(state2, Address(state, 8));

    __ pop_reg(saved_regs, sp);
    __ ret();

    return (address) start;
  }

  /**
   * Perform the quarter round calculations on values contained within four vector registers.
   *
   * @param aVec the SIMD register containing only the "a" values
   * @param bVec the SIMD register containing only the "b" values
   * @param cVec the SIMD register containing only the "c" values
   * @param dVec the SIMD register containing only the "d" values
   * @param tmp_vr temporary vector register holds intermedia values.
   */
  void chacha20_quarter_round(VectorRegister aVec, VectorRegister bVec,
                          VectorRegister cVec, VectorRegister dVec, VectorRegister tmp_vr) {
    // a += b, d ^= a, d <<<= 16
    __ vadd_vv(aVec, aVec, bVec);
    __ vxor_vv(dVec, dVec, aVec);
    __ vrole32_vi(dVec, 16, tmp_vr);

    // c += d, b ^= c, b <<<= 12
    __ vadd_vv(cVec, cVec, dVec);
    __ vxor_vv(bVec, bVec, cVec);
    __ vrole32_vi(bVec, 12, tmp_vr);

    // a += b, d ^= a, d <<<= 8
    __ vadd_vv(aVec, aVec, bVec);
    __ vxor_vv(dVec, dVec, aVec);
    __ vrole32_vi(dVec, 8, tmp_vr);

    // c += d, b ^= c, b <<<= 7
    __ vadd_vv(cVec, cVec, dVec);
    __ vxor_vv(bVec, bVec, cVec);
    __ vrole32_vi(bVec, 7, tmp_vr);
  }

  /**
   * int com.sun.crypto.provider.ChaCha20Cipher.implChaCha20Block(int[] initState, byte[] result)
   *
   *  Input arguments:
   *  c_rarg0   - state, the starting state
   *  c_rarg1   - key_stream, the array that will hold the result of the ChaCha20 block function
   *
   *  Implementation Note:
   *   Parallelization is achieved by loading individual state elements into vectors for N blocks.
   *   N depends on single vector register length.
   */
  address generate_chacha20Block() {
    Label L_Rounds;

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "chacha20Block");
    address start = __ pc();
    __ enter();

    const int states_len = 16;
    const int step = 4;
    const Register state = c_rarg0;
    const Register key_stream = c_rarg1;
    const Register tmp_addr = t0;
    const Register length = t1;

    // Organize vector registers in an array that facilitates
    // putting repetitive opcodes into loop structures below.
    const VectorRegister work_vrs[16] = {
      v0, v1, v2,  v3,  v4,  v5,  v6,  v7,
      v8, v9, v10, v11, v12, v13, v14, v15
    };
    const VectorRegister tmp_vr = v16;
    const VectorRegister counter_vr = v17;

    {
      // Put 16 here, as com.sun.crypto.providerChaCha20Cipher.KS_MAX_LEN is 1024
      // in java level.
      __ vsetivli(length, 16, Assembler::e32, Assembler::m1);
    }

    // Load from source state.
    // Every element in source state is duplicated to all elements in the corresponding vector.
    __ mv(tmp_addr, state);
    for (int i = 0; i < states_len; i += 1) {
      __ vlse32_v(work_vrs[i], tmp_addr, zr);
      __ addi(tmp_addr, tmp_addr, step);
    }
    // Adjust counter for every individual block.
    __ vid_v(counter_vr);
    __ vadd_vv(work_vrs[12], work_vrs[12], counter_vr);

    // Perform 10 iterations of the 8 quarter round set
    {
      const Register loop = t2; // share t2 with other non-overlapping usages.
      __ mv(loop, 10);
      __ BIND(L_Rounds);

      chacha20_quarter_round(work_vrs[0], work_vrs[4], work_vrs[8],  work_vrs[12], tmp_vr);
      chacha20_quarter_round(work_vrs[1], work_vrs[5], work_vrs[9],  work_vrs[13], tmp_vr);
      chacha20_quarter_round(work_vrs[2], work_vrs[6], work_vrs[10], work_vrs[14], tmp_vr);
      chacha20_quarter_round(work_vrs[3], work_vrs[7], work_vrs[11], work_vrs[15], tmp_vr);

      chacha20_quarter_round(work_vrs[0], work_vrs[5], work_vrs[10], work_vrs[15], tmp_vr);
      chacha20_quarter_round(work_vrs[1], work_vrs[6], work_vrs[11], work_vrs[12], tmp_vr);
      chacha20_quarter_round(work_vrs[2], work_vrs[7], work_vrs[8],  work_vrs[13], tmp_vr);
      chacha20_quarter_round(work_vrs[3], work_vrs[4], work_vrs[9],  work_vrs[14], tmp_vr);

      __ sub(loop, loop, 1);
      __ bnez(loop, L_Rounds);
    }

    // Add the original state into the end working state.
    // We do this by first duplicating every element in source state array to the corresponding
    // vector, then adding it to the post-loop working state.
    __ mv(tmp_addr, state);
    for (int i = 0; i < states_len; i += 1) {
      __ vlse32_v(tmp_vr, tmp_addr, zr);
      __ addi(tmp_addr, tmp_addr, step);
      __ vadd_vv(work_vrs[i], work_vrs[i], tmp_vr);
    }
    // Add the counter overlay onto work_vrs[12] at the end.
    __ vadd_vv(work_vrs[12], work_vrs[12], counter_vr);

    // Store result to key stream.
    {
      const Register stride = t2; // share t2 with other non-overlapping usages.
      // Every block occupies 64 bytes, so we use 64 as stride of the vector store.
      __ mv(stride, 64);
      for (int i = 0; i < states_len; i += 1) {
        __ vsse32_v(work_vrs[i], key_stream, stride);
        __ addi(key_stream, key_stream, step);
      }
    }

    // Return length of output key_stream
    __ slli(c_rarg0, length, 6);

    __ leave();
    __ ret();

    return (address) start;
  }


  // ------------------------ SHA-1 intrinsic ------------------------

  // K't =
  //    5a827999, 0  <= t <= 19
  //    6ed9eba1, 20 <= t <= 39
  //    8f1bbcdc, 40 <= t <= 59
  //    ca62c1d6, 60 <= t <= 79
  void sha1_prepare_k(Register cur_k, int round) {
    assert(round >= 0 && round < 80, "must be");

    static const int64_t ks[] = {0x5a827999, 0x6ed9eba1, 0x8f1bbcdc, 0xca62c1d6};
    if ((round % 20) == 0) {
      __ mv(cur_k, ks[round/20]);
    }
  }

  // W't =
  //    M't,                                      0 <=  t <= 15
  //    ROTL'1(W't-3 ^ W't-8 ^ W't-14 ^ W't-16),  16 <= t <= 79
  void sha1_prepare_w(Register cur_w, Register ws[], Register buf, int round) {
    assert(round >= 0 && round < 80, "must be");

    if (round < 16) {
      // in the first 16 rounds, in ws[], every register contains 2 W't, e.g.
      //   in ws[0], high part contains W't-0, low part contains W't-1,
      //   in ws[1], high part contains W't-2, low part contains W't-3,
      //   ...
      //   in ws[7], high part contains W't-14, low part contains W't-15.

      if ((round % 2) == 0) {
        __ ld(ws[round/2], Address(buf, (round/2) * 8));
        // reverse bytes, as SHA-1 is defined in big-endian.
        __ revb(ws[round/2], ws[round/2]);
        __ srli(cur_w, ws[round/2], 32);
      } else {
        __ mv(cur_w, ws[round/2]);
      }

      return;
    }

    if ((round % 2) == 0) {
      int idx = 16;
      // W't = ROTL'1(W't-3 ^ W't-8 ^ W't-14 ^ W't-16),  16 <= t <= 79
      __ srli(t1, ws[(idx-8)/2], 32);
      __ xorr(t0, ws[(idx-3)/2], t1);

      __ srli(t1, ws[(idx-14)/2], 32);
      __ srli(cur_w, ws[(idx-16)/2], 32);
      __ xorr(cur_w, cur_w, t1);

      __ xorr(cur_w, cur_w, t0);
      __ rolw_imm(cur_w, cur_w, 1, t0);

      // copy the cur_w value to ws[8].
      // now, valid w't values are at:
      //  w0:       ws[0]'s lower 32 bits
      //  w1 ~ w14: ws[1] ~ ws[7]
      //  w15:      ws[8]'s higher 32 bits
      __ slli(ws[idx/2], cur_w, 32);

      return;
    }

    int idx = 17;
    // W't = ROTL'1(W't-3 ^ W't-8 ^ W't-14 ^ W't-16),  16 <= t <= 79
    __ srli(t1, ws[(idx-3)/2], 32);
    __ xorr(t0, t1, ws[(idx-8)/2]);

    __ xorr(cur_w, ws[(idx-16)/2], ws[(idx-14)/2]);

    __ xorr(cur_w, cur_w, t0);
    __ rolw_imm(cur_w, cur_w, 1, t0);

    // copy the cur_w value to ws[8]
    __ zero_extend(cur_w, cur_w, 32);
    __ orr(ws[idx/2], ws[idx/2], cur_w);

    // shift the w't registers, so they start from ws[0] again.
    // now, valid w't values are at:
    //  w0 ~ w15: ws[0] ~ ws[7]
    Register ws_0 = ws[0];
    for (int i = 0; i < 16/2; i++) {
      ws[i] = ws[i+1];
    }
    ws[8] = ws_0;
  }

  // f't(x, y, z) =
  //    Ch(x, y, z)     = (x & y) ^ (~x & z)            , 0  <= t <= 19
  //    Parity(x, y, z) = x ^ y ^ z                     , 20 <= t <= 39
  //    Maj(x, y, z)    = (x & y) ^ (x & z) ^ (y & z)   , 40 <= t <= 59
  //    Parity(x, y, z) = x ^ y ^ z                     , 60 <= t <= 79
  void sha1_f(Register dst, Register x, Register y, Register z, int round) {
    assert(round >= 0 && round < 80, "must be");
    assert_different_registers(dst, x, y, z, t0, t1);

    if (round < 20) {
      // (x & y) ^ (~x & z)
      __ andr(t0, x, y);
      __ andn(dst, z, x);
      __ xorr(dst, dst, t0);
    } else if (round >= 40 && round < 60) {
      // (x & y) ^ (x & z) ^ (y & z)
      __ andr(t0, x, y);
      __ andr(t1, x, z);
      __ andr(dst, y, z);
      __ xorr(dst, dst, t0);
      __ xorr(dst, dst, t1);
    } else {
      // x ^ y ^ z
      __ xorr(dst, x, y);
      __ xorr(dst, dst, z);
    }
  }

  // T = ROTL'5(a) + f't(b, c, d) + e + K't + W't
  // e = d
  // d = c
  // c = ROTL'30(b)
  // b = a
  // a = T
  void sha1_process_round(Register a, Register b, Register c, Register d, Register e,
                          Register cur_k, Register cur_w, Register tmp, int round) {
    assert(round >= 0 && round < 80, "must be");
    assert_different_registers(a, b, c, d, e, cur_w, cur_k, tmp, t0);

    // T = ROTL'5(a) + f't(b, c, d) + e + K't + W't

    // cur_w will be recalculated at the beginning of each round,
    // so, we can reuse it as a temp register here.
    Register tmp2 = cur_w;

    // reuse e as a temporary register, as we will mv new value into it later
    Register tmp3 = e;
    __ add(tmp2, cur_k, tmp2);
    __ add(tmp3, tmp3, tmp2);
    __ rolw_imm(tmp2, a, 5, t0);

    sha1_f(tmp, b, c, d, round);

    __ add(tmp2, tmp2, tmp);
    __ add(tmp2, tmp2, tmp3);

    // e = d
    // d = c
    // c = ROTL'30(b)
    // b = a
    // a = T
    __ mv(e, d);
    __ mv(d, c);

    __ rolw_imm(c, b, 30);
    __ mv(b, a);
    __ mv(a, tmp2);
  }

  // H(i)0 = a + H(i-1)0
  // H(i)1 = b + H(i-1)1
  // H(i)2 = c + H(i-1)2
  // H(i)3 = d + H(i-1)3
  // H(i)4 = e + H(i-1)4
  void sha1_calculate_im_hash(Register a, Register b, Register c, Register d, Register e,
                              Register prev_ab, Register prev_cd, Register prev_e) {
    assert_different_registers(a, b, c, d, e, prev_ab, prev_cd, prev_e);

    __ add(a, a, prev_ab);
    __ srli(prev_ab, prev_ab, 32);
    __ add(b, b, prev_ab);

    __ add(c, c, prev_cd);
    __ srli(prev_cd, prev_cd, 32);
    __ add(d, d, prev_cd);

    __ add(e, e, prev_e);
  }

  void sha1_preserve_prev_abcde(Register a, Register b, Register c, Register d, Register e,
                                Register prev_ab, Register prev_cd, Register prev_e) {
    assert_different_registers(a, b, c, d, e, prev_ab, prev_cd, prev_e, t0);

    __ slli(t0, b, 32);
    __ zero_extend(prev_ab, a, 32);
    __ orr(prev_ab, prev_ab, t0);

    __ slli(t0, d, 32);
    __ zero_extend(prev_cd, c, 32);
    __ orr(prev_cd, prev_cd, t0);

    __ mv(prev_e, e);
  }

  // Intrinsic for:
  //   void sun.security.provider.SHA.implCompress0(byte[] buf, int ofs)
  //   void sun.security.provider.DigestBase.implCompressMultiBlock0(byte[] b, int ofs, int limit)
  //
  // Arguments:
  //
  // Inputs:
  //   c_rarg0: byte[]  src array + offset
  //   c_rarg1: int[]   SHA.state
  //   - - - - - - below are only for implCompressMultiBlock0 - - - - - -
  //   c_rarg2: int     offset
  //   c_rarg3: int     limit
  //
  // Outputs:
  //   - - - - - - below are only for implCompressMultiBlock0 - - - - - -
  //   c_rarg0: int offset, when (multi_block == true)
  //
  address generate_sha1_implCompress(bool multi_block, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);

    address start = __ pc();
    __ enter();

    RegSet saved_regs = RegSet::range(x18, x27);
    if (multi_block) {
      // use x9 as src below.
      saved_regs += RegSet::of(x9);
    }
    __ push_reg(saved_regs, sp);

    // c_rarg0 - c_rarg3: x10 - x13
    Register buf    = c_rarg0;
    Register state  = c_rarg1;
    Register offset = c_rarg2;
    Register limit  = c_rarg3;
    // use src to contain the original start point of the array.
    Register src    = x9;

    if (multi_block) {
      __ sub(limit, limit, offset);
      __ add(limit, limit, buf);
      __ sub(src, buf, offset);
    }

    // [args-reg]:  x14 - x17
    // [temp-reg]:  x28 - x31
    // [saved-reg]: x18 - x27

    // h0/1/2/3/4
    const Register a = x14, b = x15, c = x16, d = x17, e = x28;
    // w0, w1, ... w15
    // put two adjecent w's in one register:
    //    one at high word part, another at low word part
    // at different round (even or odd), w't value reside in different items in ws[].
    // w0 ~ w15, either reside in
    //    ws[0] ~ ws[7], where
    //      w0 at higher 32 bits of ws[0],
    //      w1 at lower 32 bits of ws[0],
    //      ...
    //      w14 at higher 32 bits of ws[7],
    //      w15 at lower 32 bits of ws[7].
    // or, reside in
    //    w0:       ws[0]'s lower 32 bits
    //    w1 ~ w14: ws[1] ~ ws[7]
    //    w15:      ws[8]'s higher 32 bits
    Register ws[9] = {x29, x30, x31, x18,
                      x19, x20, x21, x22,
                      x23}; // auxiliary register for calculating w's value
    // current k't's value
    const Register cur_k = x24;
    // current w't's value
    const Register cur_w = x25;
    // values of a, b, c, d, e in the previous round
    const Register prev_ab = x26, prev_cd = x27;
    const Register prev_e = offset; // reuse offset/c_rarg2

    // load 5 words state into a, b, c, d, e.
    //
    // To minimize the number of memory operations, we apply following
    // optimization: read the states (a/b/c/d) of 4-byte values in pairs,
    // with a single ld, and split them into 2 registers.
    //
    // And, as the core algorithm of SHA-1 works on 32-bits words, so
    // in the following code, it does not care about the content of
    // higher 32-bits in a/b/c/d/e. Based on this observation,
    // we can apply further optimization, which is to just ignore the
    // higher 32-bits in a/c/e, rather than set the higher
    // 32-bits of a/c/e to zero explicitly with extra instructions.
    __ ld(a, Address(state, 0));
    __ srli(b, a, 32);
    __ ld(c, Address(state, 8));
    __ srli(d, c, 32);
    __ lw(e, Address(state, 16));

    Label L_sha1_loop;
    if (multi_block) {
      __ BIND(L_sha1_loop);
    }

    sha1_preserve_prev_abcde(a, b, c, d, e, prev_ab, prev_cd, prev_e);

    for (int round = 0; round < 80; round++) {
      // prepare K't value
      sha1_prepare_k(cur_k, round);

      // prepare W't value
      sha1_prepare_w(cur_w, ws, buf, round);

      // one round process
      sha1_process_round(a, b, c, d, e, cur_k, cur_w, t2, round);
    }

    // compute the intermediate hash value
    sha1_calculate_im_hash(a, b, c, d, e, prev_ab, prev_cd, prev_e);

    if (multi_block) {
      int64_t block_bytes = 16 * 4;
      __ addi(buf, buf, block_bytes);

      __ bge(limit, buf, L_sha1_loop, true);
    }

    // store back the state.
    __ zero_extend(a, a, 32);
    __ slli(b, b, 32);
    __ orr(a, a, b);
    __ sd(a, Address(state, 0));
    __ zero_extend(c, c, 32);
    __ slli(d, d, 32);
    __ orr(c, c, d);
    __ sd(c, Address(state, 8));
    __ sw(e, Address(state, 16));

    // return offset
    if (multi_block) {
      __ sub(c_rarg0, buf, src);
    }

    __ pop_reg(saved_regs, sp);

    __ leave();
    __ ret();

    return (address) start;
  }

  void adler32_process_bytes(Register buff, Register s1, Register s2, VectorRegister vtable,
    VectorRegister vzero, VectorRegister vbytes, VectorRegister vs1acc, VectorRegister vs2acc,
    Register temp0, Register temp1, Register temp2,  Register temp3,
    VectorRegister vtemp1, VectorRegister vtemp2, int step, Assembler::LMUL lmul) {

    assert((lmul == Assembler::m4 && step == 64) ||
           (lmul == Assembler::m2 && step == 32) ||
           (lmul == Assembler::m1 && step == 16),
           "LMUL should be aligned with step: m4 and 64, m2 and 32 or m1 and 16");
    // Below is function for calculating Adler32 checksum with 64-, 32- or 16-byte step. LMUL=m4, m2 or m1 is used.
    // The results are in v12, v13, ..., v22, v23. Example below is for 64-byte step case.
    // We use b1, b2, ..., b64 to denote the 64 bytes loaded in each iteration.
    // In non-vectorized code, we update s1 and s2 as:
    //   s1 <- s1 + b1
    //   s2 <- s2 + s1
    //   s1 <- s1 + b2
    //   s2 <- s2 + b1
    //   ...
    //   s1 <- s1 + b64
    //   s2 <- s2 + s1
    // Putting above assignments together, we have:
    //   s1_new = s1 + b1 + b2 + ... + b64
    //   s2_new = s2 + (s1 + b1) + (s1 + b1 + b2) + ... + (s1 + b1 + b2 + ... + b64) =
    //          = s2 + s1 * 64 + (b1 * 64 + b2 * 63 + ... + b64 * 1) =
    //          = s2 + s1 * 64 + (b1, b2, ... b64) dot (64, 63, ... 1)

    __ mv(temp3, step);
    // Load data
    __ vsetvli(temp0, temp3, Assembler::e8, lmul);
    __ vle8_v(vbytes, buff);
    __ addi(buff, buff, step);

    // Upper bound reduction sum for s1_new:
    // 0xFF * 64 = 0x3FC0, so:
    // 1. Need to do vector-widening reduction sum
    // 2. It is safe to perform sign-extension during vmv.x.s with 16-bits elements
    __ vwredsumu_vs(vs1acc, vbytes, vzero);
    // Multiplication for s2_new
    __ vwmulu_vv(vs2acc, vtable, vbytes);

    // s2 = s2 + s1 * log2(step)
    __ slli(temp1, s1, exact_log2(step));
    __ add(s2, s2, temp1);

    // Summing up calculated results for s2_new
    if (MaxVectorSize > 16) {
      __ vsetvli(temp0, temp3, Assembler::e16, lmul);
    } else {
      // Half of vector-widening multiplication result is in successor of vs2acc
      // group for vlen == 16, in which case we need to double vector register
      // group width in order to reduction sum all of them
      Assembler::LMUL lmulx2 = (lmul == Assembler::m1) ? Assembler::m2 :
                               (lmul == Assembler::m2) ? Assembler::m4 : Assembler::m8;
      __ vsetvli(temp0, temp3, Assembler::e16, lmulx2);
    }
    // Upper bound for reduction sum:
    // 0xFF * (64 + 63 + ... + 2 + 1) = 0x817E0 max for whole register group, so:
    // 1. Need to do vector-widening reduction sum
    // 2. It is safe to perform sign-extension during vmv.x.s with 32-bits elements
    __ vwredsumu_vs(vtemp1, vs2acc, vzero);

    // Extracting results for:
    // s1_new
    __ vmv_x_s(temp0, vs1acc);
    __ add(s1, s1, temp0);
    // s2_new
    __ vsetvli(temp0, temp3, Assembler::e32, Assembler::m1);
    __ vmv_x_s(temp1, vtemp1);
    __ add(s2, s2, temp1);
  }

  /***
   *  int java.util.zip.Adler32.updateBytes(int adler, byte[] b, int off, int len)
   *
   *  Arguments:
   *
   *  Inputs:
   *   c_rarg0   - int   adler
   *   c_rarg1   - byte* buff (b + off)
   *   c_rarg2   - int   len
   *
   *  Output:
   *   c_rarg0   - int adler result
   */
  address generate_updateBytesAdler32() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "updateBytesAdler32");
    address start = __ pc();

    Label L_nmax, L_nmax_loop, L_nmax_loop_entry, L_by16, L_by16_loop,
      L_by16_loop_unroll, L_by1_loop, L_do_mod, L_combine, L_by1;

    // Aliases
    Register adler  = c_rarg0;
    Register s1     = c_rarg0;
    Register s2     = c_rarg3;
    Register buff   = c_rarg1;
    Register len    = c_rarg2;
    Register nmax  = c_rarg4;
    Register base  = c_rarg5;
    Register count = c_rarg6;
    Register temp0 = x28; // t3
    Register temp1 = x29; // t4
    Register temp2 = x30; // t5
    Register temp3 = x31; // t6

    VectorRegister vzero = v31;
    VectorRegister vbytes = v8; // group: v8, v9, v10, v11
    VectorRegister vs1acc = v12; // group: v12, v13, v14, v15
    VectorRegister vs2acc = v16; // group: v16, v17, v18, v19, v20, v21, v22, v23
    VectorRegister vtable_64 = v24; // group: v24, v25, v26, v27
    VectorRegister vtable_32 = v4; // group: v4, v5
    VectorRegister vtable_16 = v30;
    VectorRegister vtemp1 = v28;
    VectorRegister vtemp2 = v29;

    // Max number of bytes we can process before having to take the mod
    // 0x15B0 is 5552 in decimal, the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
    const uint64_t BASE = 0xfff1;
    const uint64_t NMAX = 0x15B0;

    // Loops steps
    int step_64 = 64;
    int step_32 = 32;
    int step_16 = 16;
    int step_1  = 1;

    __ enter(); // Required for proper stackwalking of RuntimeStub frame
    __ mv(temp1, 64);
    __ vsetvli(temp0, temp1, Assembler::e8, Assembler::m4);

    // Generating accumulation coefficients for further calculations
    // vtable_64:
    __ vid_v(vtemp1);
    __ vrsub_vx(vtable_64, vtemp1, temp1);
    // vtable_64 group now contains { 0x40, 0x3f, 0x3e, ..., 0x3, 0x2, 0x1 }

    // vtable_32:
    __ mv(temp1, 32);
    __ vsetvli(temp0, temp1, Assembler::e8, Assembler::m2);
    __ vid_v(vtemp1);
    __ vrsub_vx(vtable_32, vtemp1, temp1);
    // vtable_32 group now contains { 0x20, 0x1f, 0x1e, ..., 0x3, 0x2, 0x1 }

    __ vsetivli(temp0, 16, Assembler::e8, Assembler::m1);
    // vtable_16:
    __ mv(temp1, 16);
    __ vid_v(vtemp1);
    __ vrsub_vx(vtable_16, vtemp1, temp1);
    // vtable_16 now contains { 0x10, 0xf, 0xe, ..., 0x3, 0x2, 0x1 }

    __ vmv_v_i(vzero, 0);

    __ mv(base, BASE);
    __ mv(nmax, NMAX);

    // s1 is initialized to the lower 16 bits of adler
    // s2 is initialized to the upper 16 bits of adler
    __ srliw(s2, adler, 16); // s2 = ((adler >> 16) & 0xffff)
    __ zero_extend(s1, adler, 16); // s1 = (adler & 0xffff)

    // The pipelined loop needs at least 16 elements for 1 iteration
    // It does check this, but it is more effective to skip to the cleanup loop
    __ mv(temp0, step_16);
    __ bgeu(len, temp0, L_nmax);
    __ beqz(len, L_combine);

    // Jumping to L_by1_loop
    __ sub(len, len, step_1);
    __ j(L_by1_loop);

  __ bind(L_nmax);
    __ sub(len, len, nmax);
    __ sub(count, nmax, 16);
    __ bltz(len, L_by16);

  // Align L_nmax loop by 64
  __ bind(L_nmax_loop_entry);
    __ sub(count, count, 32);

  __ bind(L_nmax_loop);
    adler32_process_bytes(buff, s1, s2, vtable_64, vzero,
      vbytes, vs1acc, vs2acc, temp0, temp1, temp2, temp3,
      vtemp1, vtemp2, step_64, Assembler::m4);
    __ sub(count, count, step_64);
    __ bgtz(count, L_nmax_loop);

    // There are three iterations left to do
    adler32_process_bytes(buff, s1, s2, vtable_32, vzero,
      vbytes, vs1acc, vs2acc, temp0, temp1, temp2, temp3,
      vtemp1, vtemp2, step_32, Assembler::m2);
    adler32_process_bytes(buff, s1, s2, vtable_16, vzero,
      vbytes, vs1acc, vs2acc, temp0, temp1, temp2, temp3,
      vtemp1, vtemp2, step_16, Assembler::m1);

    // s1 = s1 % BASE
    __ remuw(s1, s1, base);
    // s2 = s2 % BASE
    __ remuw(s2, s2, base);

    __ sub(len, len, nmax);
    __ sub(count, nmax, 16);
    __ bgez(len, L_nmax_loop_entry);

  __ bind(L_by16);
    __ add(len, len, count);
    __ bltz(len, L_by1);
    // Trying to unroll
    __ mv(temp3, step_64);
    __ blt(len, temp3, L_by16_loop);

  __ bind(L_by16_loop_unroll);
    adler32_process_bytes(buff, s1, s2, vtable_64, vzero,
      vbytes, vs1acc, vs2acc, temp0, temp1, temp2, temp3,
      vtemp1, vtemp2, step_64, Assembler::m4);
    __ sub(len, len, step_64);
    // By now the temp3 should still be 64
    __ bge(len, temp3, L_by16_loop_unroll);

  __ bind(L_by16_loop);
    adler32_process_bytes(buff, s1, s2, vtable_16, vzero,
      vbytes, vs1acc, vs2acc, temp0, temp1, temp2, temp3,
      vtemp1, vtemp2, step_16, Assembler::m1);
    __ sub(len, len, step_16);
    __ bgez(len, L_by16_loop);

  __ bind(L_by1);
    __ add(len, len, 15);
    __ bltz(len, L_do_mod);

  __ bind(L_by1_loop);
    __ lbu(temp0, Address(buff, 0));
    __ addi(buff, buff, step_1);
    __ add(s1, temp0, s1);
    __ add(s2, s2, s1);
    __ sub(len, len, step_1);
    __ bgez(len, L_by1_loop);

  __ bind(L_do_mod);
    // s1 = s1 % BASE
    __ remuw(s1, s1, base);
    // s2 = s2 % BASE
    __ remuw(s2, s2, base);

    // Combine lower bits and higher bits
    // adler = s1 | (s2 << 16)
  __ bind(L_combine);
    __ slli(s2, s2, 16);
    __ orr(s1, s1, s2);

    __ leave(); // Required for proper stackwalking of RuntimeStub frame
    __ ret();

    return start;
  }

#endif // COMPILER2_OR_JVMCI

#ifdef COMPILER2

static const int64_t right_2_bits = right_n_bits(2);
static const int64_t right_3_bits = right_n_bits(3);

  // In sun.security.util.math.intpoly.IntegerPolynomial1305, integers
  // are represented as long[5], with BITS_PER_LIMB = 26.
  // Pack five 26-bit limbs into three 64-bit registers.
  void poly1305_pack_26(Register dest0, Register dest1, Register dest2, Register src, Register tmp1, Register tmp2) {
    assert_different_registers(dest0, dest1, dest2, src, tmp1, tmp2);

    // The goal is to have 128-bit value in dest2:dest1:dest0
    __ ld(dest0, Address(src, 0));    // 26 bits in dest0

    __ ld(tmp1, Address(src, sizeof(jlong)));
    __ slli(tmp1, tmp1, 26);
    __ add(dest0, dest0, tmp1);       // 52 bits in dest0

    __ ld(tmp2, Address(src, 2 * sizeof(jlong)));
    __ slli(tmp1, tmp2, 52);
    __ add(dest0, dest0, tmp1);       // dest0 is full

    __ srli(dest1, tmp2, 12);         // 14-bit in dest1

    __ ld(tmp1, Address(src, 3 * sizeof(jlong)));
    __ slli(tmp1, tmp1, 14);
    __ add(dest1, dest1, tmp1);       // 40-bit in dest1

    __ ld(tmp1, Address(src, 4 * sizeof(jlong)));
    __ slli(tmp2, tmp1, 40);
    __ add(dest1, dest1, tmp2);       // dest1 is full

    if (dest2->is_valid()) {
      __ srli(tmp1, tmp1, 24);
      __ mv(dest2, tmp1);               // 2 bits in dest2
    } else {
#ifdef ASSERT
      Label OK;
      __ srli(tmp1, tmp1, 24);
      __ beq(zr, tmp1, OK);           // 2 bits
      __ stop("high bits of Poly1305 integer should be zero");
      __ should_not_reach_here();
      __ bind(OK);
#endif
    }
  }

  // As above, but return only a 128-bit integer, packed into two
  // 64-bit registers.
  void poly1305_pack_26(Register dest0, Register dest1, Register src, Register tmp1, Register tmp2) {
    poly1305_pack_26(dest0, dest1, noreg, src, tmp1, tmp2);
  }

  // U_2:U_1:U_0: += (U_2 >> 2) * 5
  void poly1305_reduce(Register U_2, Register U_1, Register U_0, Register tmp1, Register tmp2) {
    assert_different_registers(U_2, U_1, U_0, tmp1, tmp2);

    // First, U_2:U_1:U_0 += (U_2 >> 2)
    __ srli(tmp1, U_2, 2);
    __ cad(U_0, U_0, tmp1, tmp2); // Add tmp1 to U_0 with carry output to tmp2
    __ andi(U_2, U_2, right_2_bits); // Clear U_2 except for the lowest two bits
    __ cad(U_1, U_1, tmp2, tmp2); // Add carry to U_1 with carry output to tmp2
    __ add(U_2, U_2, tmp2);

    // Second, U_2:U_1:U_0 += (U_2 >> 2) << 2
    __ slli(tmp1, tmp1, 2);
    __ cad(U_0, U_0, tmp1, tmp2); // Add tmp1 to U_0 with carry output to tmp2
    __ cad(U_1, U_1, tmp2, tmp2); // Add carry to U_1 with carry output to tmp2
    __ add(U_2, U_2, tmp2);
  }

  // Poly1305, RFC 7539
  // void com.sun.crypto.provider.Poly1305.processMultipleBlocks(byte[] input, int offset, int length, long[] aLimbs, long[] rLimbs)

  // Arguments:
  //    c_rarg0:   input_start -- where the input is stored
  //    c_rarg1:   length
  //    c_rarg2:   acc_start -- where the output will be stored
  //    c_rarg3:   r_start -- where the randomly generated 128-bit key is stored

  // See https://loup-vaillant.fr/tutorials/poly1305-design for a
  // description of the tricks used to simplify and accelerate this
  // computation.

  address generate_poly1305_processBlocks() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "poly1305_processBlocks");
    address start = __ pc();
    __ enter();
    Label here;

    RegSet saved_regs = RegSet::range(x18, x21);
    RegSetIterator<Register> regs = (RegSet::range(x14, x31) - RegSet::range(x22, x27)).begin();
    __ push_reg(saved_regs, sp);

    // Arguments
    const Register input_start = c_rarg0, length = c_rarg1, acc_start = c_rarg2, r_start = c_rarg3;

    // R_n is the 128-bit randomly-generated key, packed into two
    // registers. The caller passes this key to us as long[5], with
    // BITS_PER_LIMB = 26.
    const Register R_0 = *regs, R_1 = *++regs;
    poly1305_pack_26(R_0, R_1, r_start, t1, t2);

    // RR_n is (R_n >> 2) * 5
    const Register RR_0 = *++regs, RR_1 = *++regs;
    __ srli(t1, R_0, 2);
    __ shadd(RR_0, t1, t1, t2, 2);
    __ srli(t1, R_1, 2);
    __ shadd(RR_1, t1, t1, t2, 2);

    // U_n is the current checksum
    const Register U_0 = *++regs, U_1 = *++regs, U_2 = *++regs;
    poly1305_pack_26(U_0, U_1, U_2, acc_start, t1, t2);

    static constexpr int BLOCK_LENGTH = 16;
    Label DONE, LOOP;

    __ mv(t1, BLOCK_LENGTH);
    __ blt(length, t1, DONE); {
      __ bind(LOOP);

      // S_n is to be the sum of U_n and the next block of data
      const Register S_0 = *++regs, S_1 = *++regs, S_2 = *++regs;
      __ ld(S_0, Address(input_start, 0));
      __ ld(S_1, Address(input_start, wordSize));

      __ cad(S_0, S_0, U_0, t1); // Add U_0 to S_0 with carry output to t1
      __ cadc(S_1, S_1, U_1, t1); // Add U_1 with carry to S_1 with carry output to t1
      __ add(S_2, U_2, t1);

      __ addi(S_2, S_2, 1);

      const Register U_0HI = *++regs, U_1HI = *++regs;

      // NB: this logic depends on some of the special properties of
      // Poly1305 keys. In particular, because we know that the top
      // four bits of R_0 and R_1 are zero, we can add together
      // partial products without any risk of needing to propagate a
      // carry out.
      __ wide_mul(U_0, U_0HI, S_0, R_0);
      __ wide_madd(U_0, U_0HI, S_1, RR_1, t1, t2);
      __ wide_madd(U_0, U_0HI, S_2, RR_0, t1, t2);

      __ wide_mul(U_1, U_1HI, S_0, R_1);
      __ wide_madd(U_1, U_1HI, S_1, R_0, t1, t2);
      __ wide_madd(U_1, U_1HI, S_2, RR_1, t1, t2);

      __ andi(U_2, R_0, right_2_bits);
      __ mul(U_2, S_2, U_2);

      // Partial reduction mod 2**130 - 5
      __ cad(U_1, U_1, U_0HI, t1); // Add U_0HI to U_1 with carry output to t1
      __ adc(U_2, U_2, U_1HI, t1);
      // Sum is now in U_2:U_1:U_0.

      // U_2:U_1:U_0: += (U_2 >> 2) * 5
      poly1305_reduce(U_2, U_1, U_0, t1, t2);

      __ sub(length, length, BLOCK_LENGTH);
      __ addi(input_start, input_start, BLOCK_LENGTH);
      __ mv(t1, BLOCK_LENGTH);
      __ bge(length, t1, LOOP);
    }

    // Further reduce modulo 2^130 - 5
    poly1305_reduce(U_2, U_1, U_0, t1, t2);

    // Unpack the sum into five 26-bit limbs and write to memory.
    // First 26 bits is the first limb
    __ slli(t1, U_0, 38); // Take lowest 26 bits
    __ srli(t1, t1, 38);
    __ sd(t1, Address(acc_start)); // First 26-bit limb

    // 27-52 bits of U_0 is the second limb
    __ slli(t1, U_0, 12); // Take next 27-52 bits
    __ srli(t1, t1, 38);
    __ sd(t1, Address(acc_start, sizeof (jlong))); // Second 26-bit limb

    // Getting 53-64 bits of U_0 and 1-14 bits of U_1 in one register
    __ srli(t1, U_0, 52);
    __ slli(t2, U_1, 50);
    __ srli(t2, t2, 38);
    __ add(t1, t1, t2);
    __ sd(t1, Address(acc_start, 2 * sizeof (jlong))); // Third 26-bit limb

    // Storing 15-40 bits of U_1
    __ slli(t1, U_1, 24); // Already used up 14 bits
    __ srli(t1, t1, 38); // Clear all other bits from t1
    __ sd(t1, Address(acc_start, 3 * sizeof (jlong))); // Fourth 26-bit limb

    // Storing 41-64 bits of U_1 and first three bits from U_2 in one register
    __ srli(t1, U_1, 40);
    __ andi(t2, U_2, right_3_bits);
    __ slli(t2, t2, 24);
    __ add(t1, t1, t2);
    __ sd(t1, Address(acc_start, 4 * sizeof (jlong))); // Fifth 26-bit limb

    __ bind(DONE);
    __ pop_reg(saved_regs, sp);
    __ leave(); // Required for proper stackwalking
    __ ret();

    return start;
  }

#endif // COMPILER2

  /**
   *  Arguments:
   *
   * Inputs:
   *   c_rarg0   - int crc
   *   c_rarg1   - byte* buf
   *   c_rarg2   - int length
   *
   * Output:
   *   c_rarg0   - int crc result
   */
  address generate_updateBytesCRC32() {
    assert(UseCRC32Intrinsics, "what are we doing here?");

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "updateBytesCRC32");

    address start = __ pc();

    const Register crc    = c_rarg0;  // crc
    const Register buf    = c_rarg1;  // source java byte array address
    const Register len    = c_rarg2;  // length
    const Register table0 = c_rarg3;  // crc_table address
    const Register table1 = c_rarg4;
    const Register table2 = c_rarg5;
    const Register table3 = c_rarg6;

    const Register tmp1 = c_rarg7;
    const Register tmp2 = t2;
    const Register tmp3 = x28; // t3
    const Register tmp4 = x29; // t4
    const Register tmp5 = x30; // t5
    const Register tmp6 = x31; // t6

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame

    __ kernel_crc32(crc, buf, len, table0, table1, table2,
                    table3, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret();

    return start;
  }

#if INCLUDE_JFR

  static void jfr_prologue(address the_pc, MacroAssembler* _masm, Register thread) {
    __ set_last_Java_frame(sp, fp, the_pc, t0);
    __ mv(c_rarg0, thread);
  }

  static void jfr_epilogue(MacroAssembler* _masm) {
    __ reset_last_Java_frame(true);
  }
  // For c2: c_rarg0 is junk, call to runtime to write a checkpoint.
  // It returns a jobject handle to the event writer.
  // The handle is dereferenced and the return value is the event writer oop.
  static RuntimeStub* generate_jfr_write_checkpoint() {
    enum layout {
      fp_off,
      fp_off2,
      return_off,
      return_off2,
      framesize // inclusive of return address
    };

    int insts_size = 1024;
    int locs_size = 64;
    CodeBuffer code("jfr_write_checkpoint", insts_size, locs_size);
    OopMapSet* oop_maps = new OopMapSet();
    MacroAssembler* masm = new MacroAssembler(&code);
    MacroAssembler* _masm = masm;

    address start = __ pc();
    __ enter();
    int frame_complete = __ pc() - start;
    address the_pc = __ pc();
    jfr_prologue(the_pc, _masm, xthread);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, JfrIntrinsicSupport::write_checkpoint), 1);

    jfr_epilogue(_masm);
    __ resolve_global_jobject(x10, t0, t1);
    __ leave();
    __ ret();

    OopMap* map = new OopMap(framesize, 1);
    oop_maps->add_gc_map(the_pc - start, map);

    RuntimeStub* stub = // codeBlob framesize is in words (not VMRegImpl::slot_size)
      RuntimeStub::new_runtime_stub("jfr_write_checkpoint", &code, frame_complete,
                                    (framesize >> (LogBytesPerWord - LogBytesPerInt)),
                                    oop_maps, false);
    return stub;
  }

  // For c2: call to return a leased buffer.
  static RuntimeStub* generate_jfr_return_lease() {
    enum layout {
      fp_off,
      fp_off2,
      return_off,
      return_off2,
      framesize // inclusive of return address
    };

    int insts_size = 1024;
    int locs_size = 64;
    CodeBuffer code("jfr_return_lease", insts_size, locs_size);
    OopMapSet* oop_maps = new OopMapSet();
    MacroAssembler* masm = new MacroAssembler(&code);
    MacroAssembler* _masm = masm;

    address start = __ pc();
    __ enter();
    int frame_complete = __ pc() - start;
    address the_pc = __ pc();
    jfr_prologue(the_pc, _masm, xthread);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, JfrIntrinsicSupport::return_lease), 1);

    jfr_epilogue(_masm);
    __ leave();
    __ ret();

    OopMap* map = new OopMap(framesize, 1);
    oop_maps->add_gc_map(the_pc - start, map);

    RuntimeStub* stub = // codeBlob framesize is in words (not VMRegImpl::slot_size)
      RuntimeStub::new_runtime_stub("jfr_return_lease", &code, frame_complete,
                                    (framesize >> (LogBytesPerWord - LogBytesPerInt)),
                                    oop_maps, false);
    return stub;
  }

#endif // INCLUDE_JFR

  // exception handler for upcall stubs
  address generate_upcall_stub_exception_handler() {
    StubCodeMark mark(this, "StubRoutines", "upcall stub exception handler");
    address start = __ pc();

    // Native caller has no idea how to handle exceptions,
    // so we just crash here. Up to callee to catch exceptions.
    __ verify_oop(x10); // return a exception oop in a0
    __ rt_call(CAST_FROM_FN_PTR(address, UpcallLinker::handle_uncaught_exception));
    __ should_not_reach_here();

    return start;
  }

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

#undef __
#define __ masm->

  address generate_throw_exception(const char* name,
                                   address runtime_entry,
                                   Register arg1 = noreg,
                                   Register arg2 = noreg) {
    // Information about frame layout at time of blocking runtime call.
    // Note that we only have to preserve callee-saved registers since
    // the compilers are responsible for supplying a continuation point
    // if they expect all registers to be preserved.
    // n.b. riscv asserts that frame::arg_reg_save_area_bytes == 0
    assert_cond(runtime_entry != nullptr);
    enum layout {
      fp_off = 0,
      fp_off2,
      return_off,
      return_off2,
      framesize // inclusive of return address
    };

    const int insts_size = 1024;
    const int locs_size  = 64;

    CodeBuffer code(name, insts_size, locs_size);
    OopMapSet* oop_maps  = new OopMapSet();
    MacroAssembler* masm = new MacroAssembler(&code);
    assert_cond(oop_maps != nullptr && masm != nullptr);

    address start = __ pc();

    // This is an inlined and slightly modified version of call_VM
    // which has the ability to fetch the return PC out of
    // thread-local storage and also sets up last_Java_sp slightly
    // differently than the real call_VM

    __ enter(); // Save FP and RA before call

    assert(is_even(framesize / 2), "sp not 16-byte aligned");

    // ra and fp are already in place
    __ addi(sp, fp, 0 - ((unsigned)framesize << LogBytesPerInt)); // prolog

    int frame_complete = __ pc() - start;

    // Set up last_Java_sp and last_Java_fp
    address the_pc = __ pc();
    __ set_last_Java_frame(sp, fp, the_pc, t0);

    // Call runtime
    if (arg1 != noreg) {
      assert(arg2 != c_rarg1, "clobbered");
      __ mv(c_rarg1, arg1);
    }
    if (arg2 != noreg) {
      __ mv(c_rarg2, arg2);
    }
    __ mv(c_rarg0, xthread);
    BLOCK_COMMENT("call runtime_entry");
    __ rt_call(runtime_entry);

    // Generate oop map
    OopMap* map = new OopMap(framesize, 0);
    assert_cond(map != nullptr);

    oop_maps->add_gc_map(the_pc - start, map);

    __ reset_last_Java_frame(true);

    __ leave();

    // check for pending exceptions
#ifdef ASSERT
    Label L;
    __ ld(t0, Address(xthread, Thread::pending_exception_offset()));
    __ bnez(t0, L);
    __ should_not_reach_here();
    __ bind(L);
#endif // ASSERT
    __ far_jump(RuntimeAddress(StubRoutines::forward_exception_entry()));

    // codeBlob framesize is in words (not VMRegImpl::slot_size)
    RuntimeStub* stub =
      RuntimeStub::new_runtime_stub(name,
                                    &code,
                                    frame_complete,
                                    (framesize >> (LogBytesPerWord - LogBytesPerInt)),
                                    oop_maps, false);
    assert(stub != nullptr, "create runtime stub fail!");
    return stub->entry_point();
  }

#undef __

  // Initialization
  void generate_initial_stubs() {
    // Generate initial stubs and initializes the entry points

    // entry points that exist in all platforms Note: This is code
    // that could be shared among different platforms - however the
    // benefit seems to be smaller than the disadvantage of having a
    // much more complicated generator structure. See also comment in
    // stubRoutines.hpp.

    StubRoutines::_forward_exception_entry = generate_forward_exception();

    if (UnsafeMemoryAccess::_table == nullptr) {
      UnsafeMemoryAccess::create_table(8 + 4); // 8 for copyMemory; 4 for setMemory
    }

    StubRoutines::_call_stub_entry =
      generate_call_stub(StubRoutines::_call_stub_return_address);

    // is referenced by megamorphic call
    StubRoutines::_catch_exception_entry = generate_catch_exception();

    // Build this early so it's available for the interpreter.
    StubRoutines::_throw_StackOverflowError_entry =
      generate_throw_exception("StackOverflowError throw_exception",
                               CAST_FROM_FN_PTR(address,
                                                SharedRuntime::throw_StackOverflowError));
    StubRoutines::_throw_delayed_StackOverflowError_entry =
      generate_throw_exception("delayed StackOverflowError throw_exception",
                               CAST_FROM_FN_PTR(address,
                                                SharedRuntime::throw_delayed_StackOverflowError));

    if (UseCRC32Intrinsics) {
      // set table address before stub generation which use it
      StubRoutines::_crc_table_adr = (address)StubRoutines::riscv::_crc_table;
      StubRoutines::_updateBytesCRC32 = generate_updateBytesCRC32();
    }
  }

  void generate_continuation_stubs() {
    // Continuation stubs:
    StubRoutines::_cont_thaw             = generate_cont_thaw();
    StubRoutines::_cont_returnBarrier    = generate_cont_returnBarrier();
    StubRoutines::_cont_returnBarrierExc = generate_cont_returnBarrier_exception();

    JFR_ONLY(generate_jfr_stubs();)
  }

#if INCLUDE_JFR
  void generate_jfr_stubs() {
    StubRoutines::_jfr_write_checkpoint_stub = generate_jfr_write_checkpoint();
    StubRoutines::_jfr_write_checkpoint = StubRoutines::_jfr_write_checkpoint_stub->entry_point();
    StubRoutines::_jfr_return_lease_stub = generate_jfr_return_lease();
    StubRoutines::_jfr_return_lease = StubRoutines::_jfr_return_lease_stub->entry_point();
  }
#endif // INCLUDE_JFR

  void generate_final_stubs() {
    // support for verify_oop (must happen after universe_init)
    if (VerifyOops) {
      StubRoutines::_verify_oop_subroutine_entry = generate_verify_oop();
    }

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
    // arraycopy stubs used by compilers
    generate_arraycopy_stubs();

    BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
    if (bs_nm != nullptr) {
      StubRoutines::_method_entry_barrier = generate_method_entry_barrier();
    }

#ifdef COMPILER2
    if (UseSecondarySupersTable) {
      StubRoutines::_lookup_secondary_supers_table_slow_path_stub = generate_lookup_secondary_supers_table_slow_path_stub();
      if (!InlineSecondarySupersTest) {
        for (int slot = 0; slot < Klass::SECONDARY_SUPERS_TABLE_SIZE; slot++) {
          StubRoutines::_lookup_secondary_supers_table_stubs[slot]
            = generate_lookup_secondary_supers_table_stub(slot);
        }
      }
    }
#endif // COMPILER2

    StubRoutines::_upcall_stub_exception_handler = generate_upcall_stub_exception_handler();

    StubRoutines::riscv::set_completed();
  }

  void generate_compiler_stubs() {
#ifdef COMPILER2
    if (UseMulAddIntrinsic) {
      StubRoutines::_mulAdd = generate_mulAdd();
    }

    if (UseMultiplyToLenIntrinsic) {
      StubRoutines::_multiplyToLen = generate_multiplyToLen();
    }

    if (UseSquareToLenIntrinsic) {
      StubRoutines::_squareToLen = generate_squareToLen();
    }

    if (UseMontgomeryMultiplyIntrinsic) {
      StubCodeMark mark(this, "StubRoutines", "montgomeryMultiply");
      MontgomeryMultiplyGenerator g(_masm, /*squaring*/false);
      StubRoutines::_montgomeryMultiply = g.generate_multiply();
    }

    if (UseMontgomerySquareIntrinsic) {
      StubCodeMark mark(this, "StubRoutines", "montgomerySquare");
      MontgomeryMultiplyGenerator g(_masm, /*squaring*/true);
      StubRoutines::_montgomerySquare = g.generate_square();
    }

    if (UsePoly1305Intrinsics) {
      StubRoutines::_poly1305_processBlocks = generate_poly1305_processBlocks();
    }

    if (UseRVVForBigIntegerShiftIntrinsics) {
      StubRoutines::_bigIntegerLeftShiftWorker = generate_bigIntegerLeftShift();
      StubRoutines::_bigIntegerRightShiftWorker = generate_bigIntegerRightShift();
    }

    if (UseSHA256Intrinsics) {
      Sha2Generator sha2(_masm, this);
      StubRoutines::_sha256_implCompress   = sha2.generate_sha256_implCompress(false);
      StubRoutines::_sha256_implCompressMB = sha2.generate_sha256_implCompress(true);
    }

    if (UseSHA512Intrinsics) {
      Sha2Generator sha2(_masm, this);
      StubRoutines::_sha512_implCompress   = sha2.generate_sha512_implCompress(false);
      StubRoutines::_sha512_implCompressMB = sha2.generate_sha512_implCompress(true);
    }

    if (UseMD5Intrinsics) {
      StubRoutines::_md5_implCompress   = generate_md5_implCompress(false, "md5_implCompress");
      StubRoutines::_md5_implCompressMB = generate_md5_implCompress(true,  "md5_implCompressMB");
    }

    if (UseChaCha20Intrinsics) {
      StubRoutines::_chacha20Block = generate_chacha20Block();
    }

    if (UseSHA1Intrinsics) {
      StubRoutines::_sha1_implCompress     = generate_sha1_implCompress(false, "sha1_implCompress");
      StubRoutines::_sha1_implCompressMB   = generate_sha1_implCompress(true, "sha1_implCompressMB");
    }

    if (UseAdler32Intrinsics) {
      StubRoutines::_updateBytesAdler32 = generate_updateBytesAdler32();
    }

    generate_compare_long_strings();

    generate_string_indexof_stubs();

#endif // COMPILER2
  }

 public:
  StubGenerator(CodeBuffer* code, StubsKind kind) : StubCodeGenerator(code) {
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
}; // end class declaration

void StubGenerator_generate(CodeBuffer* code, StubCodeGenerator::StubsKind kind) {
  StubGenerator g(code, kind);
}
