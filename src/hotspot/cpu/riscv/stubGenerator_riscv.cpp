/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/powerOfTwo.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif
#if INCLUDE_ZGC
#include "gc/z/zThreadLocalData.hpp"
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
  void inc_counter_np_(int& counter) {
    __ la(t1, ExternalAddress((address)&counter));
    __ lwu(t0, Address(t1, 0));
    __ addiw(t0, t0, 1);
    __ sw(t0, Address(t1, 0));
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
  // -34 [ argument word 1      ]
  // -33 [ saved f27            ] <--- sp_after_call
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
    sp_after_call_off  = -33,

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
    //      x30: sender sp
    BLOCK_COMMENT("call Java function");
    __ mv(x30, sp);
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
    assert(StubRoutines::_call_stub_return_address != NULL,
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
    __ beqz(x10, exit); // if obj is NULL it is OK

#if INCLUDE_ZGC
    if (UseZGC) {
      // Check if mask is good.
      // verifies that ZAddressBadMask & x10 == 0
      __ ld(c_rarg3, Address(xthread, ZThreadLocalData::address_bad_mask_offset()));
      __ andr(c_rarg2, x10, c_rarg3);
      __ bnez(c_rarg2, error);
    }
#endif

    // Check if the oop is in the right area of memory
    __ mv(c_rarg3, (intptr_t) Universe::verify_oop_mask());
    __ andr(c_rarg2, x10, c_rarg3);
    __ mv(c_rarg3, (intptr_t) Universe::verify_oop_bits());

    // Compare c_rarg2 and c_rarg3.
    __ bne(c_rarg2, c_rarg3, error);

    // make sure klass is 'reasonable', which is not zero.
    __ load_klass(x10, x10);  // get klass
    __ beqz(x10, error);      // if klass is NULL it is broken

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
    __ call(CAST_FROM_FN_PTR(address, MacroAssembler::debug64));
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

    const Register base = x28, cnt = x29;

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "zero_blocks");
    address start = __ pc();

    {
      // Clear the remaining blocks.
      Label loop;
      __ sub(cnt, cnt, MacroAssembler::zero_words_block_size);
      __ bltz(cnt, done);
      __ bind(loop);
      for (int i = 0; i < MacroAssembler::zero_words_block_size; i++) {
        __ sd(zr, Address(base, 0));
        __ add(base, base, 8);
      }
      __ sub(cnt, cnt, MacroAssembler::zero_words_block_size);
      __ bgez(cnt, loop);
      __ bind(done);
      __ add(cnt, cnt, MacroAssembler::zero_words_block_size);
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
    const char* stub_name = NULL;
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

  // All-singing all-dancing memory copy.
  //
  // Copy count units of memory from s to d.  The size of a unit is
  // step, which can be positive or negative depending on the direction
  // of copy.  If is_aligned is false, we align the source address.
  //
  /*
   * if (is_aligned) {
   *   if (count >= 32)
   *     goto copy32_loop;
   *   if (count >= 8)
   *     goto copy8_loop;
   *   goto copy_small;
   * }
   * bool is_backwards = step < 0;
   * int granularity = uabs(step);
   * count = count  *  granularity;   * count bytes
   *
   * if (is_backwards) {
   *   s += count;
   *   d += count;
   * }
   *
   * count limit maybe greater than 16, for better performance
   * if (count < 16) {
   *   goto copy_small;
   * }
   *
   * if ((dst % 8) == (src % 8)) {
   *   aligned;
   *   goto copy_big;
   * }
   *
   * copy_big:
   * if the amount to copy is more than (or equal to) 32 bytes goto copy32_loop
   *  else goto copy8_loop
   * copy_small:
   *   load element one by one;
   * done;
   */

  typedef void (MacroAssembler::*copy_insn)(Register Rd, const Address &adr, Register temp);

  void copy_memory_v(Register s, Register d, Register count, Register tmp, int step) {
    bool is_backward = step < 0;
    int granularity = uabs(step);

    const Register src = x30, dst = x31, vl = x14, cnt = x15, tmp1 = x16, tmp2 = x17;
    assert_different_registers(s, d, cnt, vl, tmp, tmp1, tmp2);
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
    __ slli(vl, vl, (int)sew);
    __ add(src, src, vl);

    __ vsex_v(v0, dst, sew);
    __ add(dst, dst, vl);
    __ bnez(cnt, loop_forward);

    if (is_backward) {
      __ j(done);

      __ bind(loop_backward);
      __ sub(tmp, cnt, vl);
      __ slli(tmp, tmp, sew);
      __ add(tmp1, s, tmp);
      __ vlex_v(v0, tmp1, sew);
      __ add(tmp2, d, tmp);
      __ vsex_v(v0, tmp2, sew);
      __ sub(cnt, cnt, vl);
      __ bnez(cnt, loop_forward);
      __ bind(done);
    }
  }

  void copy_memory(bool is_aligned, Register s, Register d,
                   Register count, Register tmp, int step) {
    if (UseRVV) {
      return copy_memory_v(s, d, count, tmp, step);
    }

    bool is_backwards = step < 0;
    int granularity = uabs(step);

    const Register src = x30, dst = x31, cnt = x15, tmp3 = x16, tmp4 = x17, tmp5 = x14, tmp6 = x13;

    Label same_aligned;
    Label copy_big, copy32_loop, copy8_loop, copy_small, done;

    copy_insn ld_arr = NULL, st_arr = NULL;
    switch (granularity) {
      case 1 :
        ld_arr = (copy_insn)&MacroAssembler::lbu;
        st_arr = (copy_insn)&MacroAssembler::sb;
        break;
      case 2 :
        ld_arr = (copy_insn)&MacroAssembler::lhu;
        st_arr = (copy_insn)&MacroAssembler::sh;
        break;
      case 4 :
        ld_arr = (copy_insn)&MacroAssembler::lwu;
        st_arr = (copy_insn)&MacroAssembler::sw;
        break;
      case 8 :
        ld_arr = (copy_insn)&MacroAssembler::ld;
        st_arr = (copy_insn)&MacroAssembler::sd;
        break;
      default :
        ShouldNotReachHere();
    }

    __ beqz(count, done);
    __ slli(cnt, count, exact_log2(granularity));
    if (is_backwards) {
      __ add(src, s, cnt);
      __ add(dst, d, cnt);
    } else {
      __ mv(src, s);
      __ mv(dst, d);
    }

    if (is_aligned) {
      __ addi(tmp, cnt, -32);
      __ bgez(tmp, copy32_loop);
      __ addi(tmp, cnt, -8);
      __ bgez(tmp, copy8_loop);
      __ j(copy_small);
    } else {
      __ mv(tmp, 16);
      __ blt(cnt, tmp, copy_small);

      __ xorr(tmp, src, dst);
      __ andi(tmp, tmp, 0b111);
      __ bnez(tmp, copy_small);

      __ bind(same_aligned);
      __ andi(tmp, src, 0b111);
      __ beqz(tmp, copy_big);
      if (is_backwards) {
        __ addi(src, src, step);
        __ addi(dst, dst, step);
      }
      (_masm->*ld_arr)(tmp3, Address(src), t0);
      (_masm->*st_arr)(tmp3, Address(dst), t0);
      if (!is_backwards) {
        __ addi(src, src, step);
        __ addi(dst, dst, step);
      }
      __ addi(cnt, cnt, -granularity);
      __ beqz(cnt, done);
      __ j(same_aligned);

      __ bind(copy_big);
      __ mv(tmp, 32);
      __ blt(cnt, tmp, copy8_loop);
    }
    __ bind(copy32_loop);
    if (is_backwards) {
      __ addi(src, src, -wordSize * 4);
      __ addi(dst, dst, -wordSize * 4);
    }
    // we first load 32 bytes, then write it, so the direction here doesn't matter
    __ ld(tmp3, Address(src));
    __ ld(tmp4, Address(src, 8));
    __ ld(tmp5, Address(src, 16));
    __ ld(tmp6, Address(src, 24));
    __ sd(tmp3, Address(dst));
    __ sd(tmp4, Address(dst, 8));
    __ sd(tmp5, Address(dst, 16));
    __ sd(tmp6, Address(dst, 24));

    if (!is_backwards) {
      __ addi(src, src, wordSize * 4);
      __ addi(dst, dst, wordSize * 4);
    }
    __ addi(tmp, cnt, -(32 + wordSize * 4));
    __ addi(cnt, cnt, -wordSize * 4);
    __ bgez(tmp, copy32_loop); // cnt >= 32, do next loop

    __ beqz(cnt, done); // if that's all - done

    __ addi(tmp, cnt, -8); // if not - copy the reminder
    __ bltz(tmp, copy_small); // cnt < 8, go to copy_small, else fall throught to copy8_loop

    __ bind(copy8_loop);
    if (is_backwards) {
      __ addi(src, src, -wordSize);
      __ addi(dst, dst, -wordSize);
    }
    __ ld(tmp3, Address(src));
    __ sd(tmp3, Address(dst));
    if (!is_backwards) {
      __ addi(src, src, wordSize);
      __ addi(dst, dst, wordSize);
    }
    __ addi(tmp, cnt, -(8 + wordSize));
    __ addi(cnt, cnt, -wordSize);
    __ bgez(tmp, copy8_loop); // cnt >= 8, do next loop

    __ beqz(cnt, done); // if that's all - done

    __ bind(copy_small);
    if (is_backwards) {
      __ addi(src, src, step);
      __ addi(dst, dst, step);
    }
    (_masm->*ld_arr)(tmp3, Address(src), t0);
    (_masm->*st_arr)(tmp3, Address(dst), t0);
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
  // cache line boundaries will still be loaded and stored atomicly.
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

    if (entry != NULL) {
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
      // UnsafeCopyMemory page error: continue after ucm
      bool add_entry = !is_oop && (!aligned || sizeof(jlong) == size);
      UnsafeCopyMemoryMark ucmm(this, add_entry, true);
      copy_memory(aligned, s, d, count, t0, size);
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
  // cache line boundaries will still be loaded and stored atomicly.
  //
  address generate_conjoint_copy(size_t size, bool aligned, bool is_oop, address nooverlap_target,
                                 address* entry, const char* name,
                                 bool dest_uninitialized = false) {
    const Register s = c_rarg0, d = c_rarg1, count = c_rarg2;
    RegSet saved_regs = RegSet::of(s, d, count);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();
    __ enter();

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    // use fwd copy when (d-s) above_equal (count*size)
    __ sub(t0, d, s);
    __ slli(t1, count, exact_log2(size));
    __ bgeu(t0, t1, nooverlap_target);

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
      // UnsafeCopyMemory page error: continue after ucm
      bool add_entry = !is_oop && (!aligned || sizeof(jlong) == size);
      UnsafeCopyMemoryMark ucmm(this, add_entry, true);
      copy_memory(aligned, s, d, count, t0, -size);
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
  // cache line boundaries will still be loaded and stored atomicly.
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
  // cache line boundaries will still be loaded and stored atomicly.
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

    __ check_klass_subtype_fast_path(sub_klass, super_klass, noreg, &L_success, &L_miss, NULL, super_check_offset);
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
    if (entry != NULL) {
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
    bool is_oop = true;
    if (dest_uninitialized) {
      decorators |= IS_DEST_UNINITIALIZED;
    }

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
    __ store_heap_oop(Address(to, 0), copied_oop, noreg, noreg, AS_RAW);  // store the oop
    __ add(to, to, UseCompressedOops ? 4 : 8);
    __ sub(count, count, 1);
    __ beqz(count, L_do_card_marks);

    // ======== loop entry is here ========
    __ BIND(L_load_element);
    __ load_heap_oop(copied_oop, Address(from, 0), noreg, noreg, AS_RAW); // load the oop
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
    assert_cond(byte_copy_entry != NULL && short_copy_entry != NULL &&
                int_copy_entry != NULL && long_copy_entry != NULL);
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
    assert_cond(byte_copy_entry != NULL && short_copy_entry != NULL &&
                int_copy_entry != NULL && oop_copy_entry != NULL &&
                long_copy_entry != NULL && checkcast_copy_entry != NULL);
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
    // (5) src klass and dst klass should be the same and not NULL.
    // (6) src and dst should be arrays.
    // (7) src_pos + length must not exceed length of src.
    // (8) dst_pos + length must not exceed length of dst.
    //

    // if [src == NULL] then return -1
    __ beqz(src, L_failed);

    // if [src_pos < 0] then return -1
    __ sign_extend(t0, src_pos, 32);
    __ bltz(t0, L_failed);

    // if [dst == NULL] then return -1
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
      __ bnez(scratch_src_klass, L2);   // it is broken if klass is NULL
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

    // if [src->is_Array() != NULL] then return -1
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
    address entry                     = NULL;
    address entry_jbyte_arraycopy     = NULL;
    address entry_jshort_arraycopy    = NULL;
    address entry_jint_arraycopy      = NULL;
    address entry_oop_arraycopy       = NULL;
    address entry_jlong_arraycopy     = NULL;
    address entry_checkcast_arraycopy = NULL;

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
    StubRoutines::_arrayof_jbyte_arraycopy           = generate_conjoint_byte_copy(true, entry, NULL,
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
    StubRoutines::_arrayof_jshort_arraycopy          = generate_conjoint_short_copy(true, entry, NULL,
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
        = generate_conjoint_oop_copy(aligned, entry, NULL, "arrayof_oop_arraycopy_uninit",
                                     /*dest_uninitialized*/true);
    }

    StubRoutines::_oop_disjoint_arraycopy            = StubRoutines::_arrayof_oop_disjoint_arraycopy;
    StubRoutines::_oop_arraycopy                     = StubRoutines::_arrayof_oop_arraycopy;
    StubRoutines::_oop_disjoint_arraycopy_uninit     = StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit;
    StubRoutines::_oop_arraycopy_uninit              = StubRoutines::_arrayof_oop_arraycopy_uninit;

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
  }

  // Safefetch stubs.
  void generate_safefetch(const char* name, int size, address* entry,
                          address* fault_pc, address* continuation_pc) {
    // safefetch signatures:
    //   int      SafeFetch32(int*      adr, int      errValue)
    //   intptr_t SafeFetchN (intptr_t* adr, intptr_t errValue)
    //
    // arguments:
    //   c_rarg0 = adr
    //   c_rarg1 = errValue
    //
    // result:
    //   PPC_RET  = *adr or errValue
    assert_cond(entry != NULL && fault_pc != NULL && continuation_pc != NULL);
    StubCodeMark mark(this, "StubRoutines", name);

    // Entry point, pc or function descriptor.
    *entry = __ pc();

    // Load *adr into c_rarg1, may fault.
    *fault_pc = __ pc();
    switch (size) {
      case 4:
        // int32_t
        __ lw(c_rarg1, Address(c_rarg0, 0));
        break;
      case 8:
        // int64_t
        __ ld(c_rarg1, Address(c_rarg0, 0));
        break;
      default:
        ShouldNotReachHere();
    }

    // return errValue or *adr
    *continuation_pc = __ pc();
    __ mv(x10, c_rarg1);
    __ ret();
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
  void compare_string_8_x_LU(Register tmpL, Register tmpU, Label &DIFF1,
                              Label &DIFF2) {
    const Register strU = x12, curU = x7, strL = x29, tmp = x30;
    __ ld(tmpL, Address(strL));
    __ addi(strL, strL, 8);
    __ ld(tmpU, Address(strU));
    __ addi(strU, strU, 8);
    __ inflate_lo32(tmp, tmpL);
    __ mv(t0, tmp);
    __ xorr(tmp, curU, t0);
    __ bnez(tmp, DIFF2);

    __ ld(curU, Address(strU));
    __ addi(strU, strU, 8);
    __ inflate_hi32(tmp, tmpL);
    __ mv(t0, tmp);
    __ xorr(tmp, tmpU, t0);
    __ bnez(tmp, DIFF1);
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
    Label SMALL_LOOP, TAIL, TAIL_LOAD_16, LOAD_LAST, DIFF1, DIFF2,
          DONE, CALCULATE_DIFFERENCE;
    const Register result = x10, str1 = x11, cnt1 = x12, str2 = x13, cnt2 = x14,
                   tmp1 = x28, tmp2 = x29, tmp3 = x30, tmp4 = x7, tmp5 = x31;
    RegSet spilled_regs = RegSet::of(tmp4, tmp5);

    // cnt2 == amount of characters left to compare
    // Check already loaded first 4 symbols
    __ inflate_lo32(tmp3, isLU ? tmp1 : tmp2);
    __ mv(isLU ? tmp1 : tmp2, tmp3);
    __ addi(str1, str1, isLU ? wordSize / 2 : wordSize);
    __ addi(str2, str2, isLU ? wordSize : wordSize / 2);
    __ sub(cnt2, cnt2, 8); // Already loaded 4 symbols. Last 4 is special case.
    __ push_reg(spilled_regs, sp);

    if (isLU) {
      __ add(str1, str1, cnt2);
      __ shadd(str2, cnt2, str2, t0, 1);
    } else {
      __ shadd(str1, cnt2, str1, t0, 1);
      __ add(str2, str2, cnt2);
    }
    __ xorr(tmp3, tmp1, tmp2);
    __ mv(tmp5, tmp2);
    __ bnez(tmp3, CALCULATE_DIFFERENCE);

    Register strU = isLU ? str2 : str1,
             strL = isLU ? str1 : str2,
             tmpU = isLU ? tmp5 : tmp1, // where to keep U for comparison
             tmpL = isLU ? tmp1 : tmp5; // where to keep L for comparison

    __ sub(tmp2, strL, cnt2); // strL pointer to load from
    __ slli(t0, cnt2, 1);
    __ sub(cnt1, strU, t0); // strU pointer to load from

    __ ld(tmp4, Address(cnt1));
    __ addi(cnt1, cnt1, 8);
    __ beqz(cnt2, LOAD_LAST); // no characters left except last load
    __ sub(cnt2, cnt2, 16);
    __ bltz(cnt2, TAIL);
    __ bind(SMALL_LOOP); // smaller loop
      __ sub(cnt2, cnt2, 16);
      compare_string_8_x_LU(tmpL, tmpU, DIFF1, DIFF2);
      compare_string_8_x_LU(tmpL, tmpU, DIFF1, DIFF2);
      __ bgez(cnt2, SMALL_LOOP);
      __ addi(t0, cnt2, 16);
      __ beqz(t0, LOAD_LAST);
    __ bind(TAIL); // 1..15 characters left until last load (last 4 characters)
      // Address of 8 bytes before last 4 characters in UTF-16 string
      __ shadd(cnt1, cnt2, cnt1, t0, 1);
      // Address of 16 bytes before last 4 characters in Latin1 string
      __ add(tmp2, tmp2, cnt2);
      __ ld(tmp4, Address(cnt1, -8));
      // last 16 characters before last load
      compare_string_8_x_LU(tmpL, tmpU, DIFF1, DIFF2);
      compare_string_8_x_LU(tmpL, tmpU, DIFF1, DIFF2);
      __ j(LOAD_LAST);
    __ bind(DIFF2);
      __ mv(tmpU, tmp4);
    __ bind(DIFF1);
      __ mv(tmpL, t0);
      __ j(CALCULATE_DIFFERENCE);
    __ bind(LOAD_LAST);
      // Last 4 UTF-16 characters are already pre-loaded into tmp4 by compare_string_8_x_LU.
      // No need to load it again
      __ mv(tmpU, tmp4);
      __ ld(tmpL, Address(strL));
      __ inflate_lo32(tmp3, tmpL);
      __ mv(tmpL, tmp3);
      __ xorr(tmp3, tmpU, tmpL);
      __ beqz(tmp3, DONE);

      // Find the first different characters in the longwords and
      // compute their difference.
    __ bind(CALCULATE_DIFFERENCE);
      __ ctzc_bit(tmp4, tmp3);
      __ srl(tmp1, tmp1, tmp4);
      __ srl(tmp5, tmp5, tmp4);
      __ andi(tmp1, tmp1, 0xFFFF);
      __ andi(tmp5, tmp5, 0xFFFF);
      __ sub(result, tmp1, tmp5);
    __ bind(DONE);
      __ pop_reg(spilled_regs, sp);
      __ ret();
    return entry;
  }

  address generate_method_entry_barrier() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "nmethod_entry_barrier");

    Label deoptimize_label;

    address start = __ pc();

    __ set_last_Java_frame(sp, fp, ra, t0);

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
      __ ld(tmp5, Address(str1));
      __ add(str2, str2, cnt2);
      __ ld(cnt1, Address(str2));
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
   *    c_rarg5   - z length
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
    const Register zlen  = x15;

    const Register tmp1  = x16;
    const Register tmp2  = x17;
    const Register tmp3  = x7;
    const Register tmp4  = x28;
    const Register tmp5  = x29;
    const Register tmp6  = x30;
    const Register tmp7  = x31;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame
    __ multiply_to_len(x, xlen, y, ylen, z, zlen, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7);
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
    const Register zlen  = x13;
    const Register y     = x14; // == x
    const Register ylen  = x15; // == xlen

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
    __ multiply_to_len(x, xlen, y, ylen, z, zlen, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7);
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

      Register reg = c_rarg0;
      Pa_base = reg;       // Argument registers
      if (squaring) {
        Pb_base = Pa_base;
      } else {
        Pb_base = ++reg;
      }
      Pn_base = ++reg;
      Rlen= ++reg;
      inv = ++reg;
      Pm_base = ++reg;

                        // Working registers:
      Ra =  ++reg;      // The current digit of a, b, n, and m.
      Rb =  ++reg;
      Rm =  ++reg;
      Rn =  ++reg;

      Pa =  ++reg;      // Pointers to the current/next digit of a, b, n, and m.
      Pb =  ++reg;
      Pm =  ++reg;
      Pn =  ++reg;

      tmp0 =  ++reg;    // Three registers which form a
      tmp1 =  ++reg;    // triple-precision accumuator.
      tmp2 =  ++reg;

      Ri =  x6;         // Inner and outer loop indexes.
      Rj =  x7;

      Rhi_ab = x28;     // Product registers: low and high parts
      Rlo_ab = x29;     // of a*b and m*n.
      Rhi_mn = x30;
      Rlo_mn = x31;

      // x18 and up are callee-saved.
      _toSave = RegSet::range(x18, reg) + Pm_base;
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
      assert(tmp1 < x28 && tmp2 < x28, "register corruption");

      slli(tmp1, len, LogBytesPerWord);
      add(s, s, tmp1);
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
    assert_cond(runtime_entry != NULL);
    enum layout {
      fp_off = 0,
      fp_off2,
      return_off,
      return_off2,
      framesize // inclusive of return address
    };

    const int insts_size = 512;
    const int locs_size  = 64;

    CodeBuffer code(name, insts_size, locs_size);
    OopMapSet* oop_maps  = new OopMapSet();
    MacroAssembler* masm = new MacroAssembler(&code);
    assert_cond(oop_maps != NULL && masm != NULL);

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
    __ call(runtime_entry);

    // Generate oop map
    OopMap* map = new OopMap(framesize, 0);
    assert_cond(map != NULL);

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
    assert(stub != NULL, "create runtime stub fail!");
    return stub->entry_point();
  }

  // Initialization
  void generate_initial() {
    // Generate initial stubs and initializes the entry points

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

    // Build this early so it's available for the interpreter.
    StubRoutines::_throw_StackOverflowError_entry =
      generate_throw_exception("StackOverflowError throw_exception",
                               CAST_FROM_FN_PTR(address,
                                                SharedRuntime::throw_StackOverflowError));
    StubRoutines::_throw_delayed_StackOverflowError_entry =
      generate_throw_exception("delayed StackOverflowError throw_exception",
                               CAST_FROM_FN_PTR(address,
                                                SharedRuntime::throw_delayed_StackOverflowError));
    // Safefetch stubs.
    generate_safefetch("SafeFetch32", sizeof(int),     &StubRoutines::_safefetch32_entry,
                                                       &StubRoutines::_safefetch32_fault_pc,
                                                       &StubRoutines::_safefetch32_continuation_pc);
    generate_safefetch("SafeFetchN", sizeof(intptr_t), &StubRoutines::_safefetchN_entry,
                                                       &StubRoutines::_safefetchN_fault_pc,
                                                       &StubRoutines::_safefetchN_continuation_pc);
  }

  void generate_all() {
    // support for verify_oop (must happen after universe_init)
    StubRoutines::_verify_oop_subroutine_entry     = generate_verify_oop();
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

    if (UseRVVForBigIntegerShiftIntrinsics) {
      StubRoutines::_bigIntegerLeftShiftWorker = generate_bigIntegerLeftShift();
      StubRoutines::_bigIntegerRightShiftWorker = generate_bigIntegerRightShift();
    }
#endif

    generate_compare_long_strings();

    generate_string_indexof_stubs();

    BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
    if (bs_nm != NULL) {
      StubRoutines::riscv::_method_entry_barrier = generate_method_entry_barrier();
    }

    StubRoutines::riscv::set_completed();
  }

 public:
  StubGenerator(CodeBuffer* code, bool all) : StubCodeGenerator(code) {
    if (all) {
      generate_all();
    } else {
      generate_initial();
    }
  }

  ~StubGenerator() {}
}; // end class declaration

#define UCM_TABLE_MAX_ENTRIES 8
void StubGenerator_generate(CodeBuffer* code, bool all) {
  if (UnsafeCopyMemory::_table == NULL) {
    UnsafeCopyMemory::create_table(UCM_TABLE_MAX_ENTRIES);
  }

  StubGenerator g(code, all);
}
