/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2025, Red Hat Inc. All rights reserved.
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

#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "asm/register.hpp"
#include "atomic_aarch64.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/universe.hpp"
#include "nativeInst_aarch64.hpp"
#include "oops/instanceOop.hpp"
#include "oops/method.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "prims/upcallLinker.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/align.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/intpow.hpp"
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
  // we save r30 (lr) as the return PC at the base of the frame and
  // link r29 (fp) below it as the frame pointer installing sp (r31)
  // into fp.
  //
  // we save r0-r7, which accounts for all the c arguments.
  //
  // TODO: strictly do we need to save them all? they are treated as
  // volatile by C so could we omit saving the ones we are going to
  // place in global registers (thread? method?) or those we only use
  // during setup of the Java call?
  //
  // we don't need to save r8 which C uses as an indirect result location
  // return register.
  //
  // we don't need to save r9-r15 which both C and Java treat as
  // volatile
  //
  // we don't need to save r16-18 because Java does not use them
  //
  // we save r19-r28 which Java uses as scratch registers and C
  // expects to be callee-save
  //
  // we save the bottom 64 bits of each value stored in v8-v15; it is
  // the responsibility of the caller to preserve larger values.
  //
  // so the stub frame looks like this when we enter Java code
  //
  //     [ return_from_Java     ] <--- sp
  //     [ argument word n      ]
  //      ...
  // -29 [ argument word 1      ]
  // -28 [ saved Floating-point Control Register ]
  // -26 [ saved v15            ] <--- sp_after_call
  // -25 [ saved v14            ]
  // -24 [ saved v13            ]
  // -23 [ saved v12            ]
  // -22 [ saved v11            ]
  // -21 [ saved v10            ]
  // -20 [ saved v9             ]
  // -19 [ saved v8             ]
  // -18 [ saved r28            ]
  // -17 [ saved r27            ]
  // -16 [ saved r26            ]
  // -15 [ saved r25            ]
  // -14 [ saved r24            ]
  // -13 [ saved r23            ]
  // -12 [ saved r22            ]
  // -11 [ saved r21            ]
  // -10 [ saved r20            ]
  //  -9 [ saved r19            ]
  //  -8 [ call wrapper    (r0) ]
  //  -7 [ result          (r1) ]
  //  -6 [ result type     (r2) ]
  //  -5 [ method          (r3) ]
  //  -4 [ entry point     (r4) ]
  //  -3 [ parameters      (r5) ]
  //  -2 [ parameter size  (r6) ]
  //  -1 [ thread (r7)          ]
  //   0 [ saved fp       (r29) ] <--- fp == saved sp (r31)
  //   1 [ saved lr       (r30) ]

  // Call stub stack layout word offsets from fp
  enum call_stub_layout {
    sp_after_call_off  = -28,

    fpcr_off           = sp_after_call_off,
    d15_off            = -26,
    d13_off            = -24,
    d11_off            = -22,
    d9_off             = -20,

    r28_off            = -18,
    r26_off            = -16,
    r24_off            = -14,
    r22_off            = -12,
    r20_off            = -10,
    call_wrapper_off   =  -8,
    result_off         =  -7,
    result_type_off    =  -6,
    method_off         =  -5,
    entry_point_off    =  -4,
    parameter_size_off =  -2,
    thread_off         =  -1,
    fp_f               =   0,
    retaddr_off        =   1,
  };

  address generate_call_stub(address& return_address) {
    assert((int)frame::entry_frame_after_call_words == -(int)sp_after_call_off + 1 &&
           (int)frame::entry_frame_call_wrapper_offset == (int)call_wrapper_off,
           "adjust this code");

    StubId stub_id = StubId::stubgen_call_stub_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    const Address sp_after_call (rfp, sp_after_call_off * wordSize);

    const Address fpcr_save     (rfp, fpcr_off           * wordSize);
    const Address call_wrapper  (rfp, call_wrapper_off   * wordSize);
    const Address result        (rfp, result_off         * wordSize);
    const Address result_type   (rfp, result_type_off    * wordSize);
    const Address method        (rfp, method_off         * wordSize);
    const Address entry_point   (rfp, entry_point_off    * wordSize);
    const Address parameter_size(rfp, parameter_size_off * wordSize);

    const Address thread        (rfp, thread_off         * wordSize);

    const Address d15_save      (rfp, d15_off * wordSize);
    const Address d13_save      (rfp, d13_off * wordSize);
    const Address d11_save      (rfp, d11_off * wordSize);
    const Address d9_save       (rfp, d9_off * wordSize);

    const Address r28_save      (rfp, r28_off * wordSize);
    const Address r26_save      (rfp, r26_off * wordSize);
    const Address r24_save      (rfp, r24_off * wordSize);
    const Address r22_save      (rfp, r22_off * wordSize);
    const Address r20_save      (rfp, r20_off * wordSize);

    // stub code

    address aarch64_entry = __ pc();

    // set up frame and move sp to end of save area
    __ enter();
    __ sub(sp, rfp, -sp_after_call_off * wordSize);

    // save register parameters and Java scratch/global registers
    // n.b. we save thread even though it gets installed in
    // rthread because we want to sanity check rthread later
    __ str(c_rarg7,  thread);
    __ strw(c_rarg6, parameter_size);
    __ stp(c_rarg4, c_rarg5,  entry_point);
    __ stp(c_rarg2, c_rarg3,  result_type);
    __ stp(c_rarg0, c_rarg1,  call_wrapper);

    __ stp(r20, r19,   r20_save);
    __ stp(r22, r21,   r22_save);
    __ stp(r24, r23,   r24_save);
    __ stp(r26, r25,   r26_save);
    __ stp(r28, r27,   r28_save);

    __ stpd(v9,  v8,   d9_save);
    __ stpd(v11, v10,  d11_save);
    __ stpd(v13, v12,  d13_save);
    __ stpd(v15, v14,  d15_save);

    __ get_fpcr(rscratch1);
    __ str(rscratch1, fpcr_save);
    // Set FPCR to the state we need. We do want Round to Nearest. We
    // don't want non-IEEE rounding modes or floating-point traps.
    __ bfi(rscratch1, zr, 22, 4); // Clear DN, FZ, and Rmode
    __ bfi(rscratch1, zr, 8, 5);  // Clear exception-control bits (8-12)
    __ set_fpcr(rscratch1);

    // install Java thread in global register now we have saved
    // whatever value it held
    __ mov(rthread, c_rarg7);
    // And method
    __ mov(rmethod, c_rarg3);

    // set up the heapbase register
    __ reinit_heapbase();

#ifdef ASSERT
    // make sure we have no pending exceptions
    {
      Label L;
      __ ldr(rscratch1, Address(rthread, in_bytes(Thread::pending_exception_offset())));
      __ cmp(rscratch1, (u1)NULL_WORD);
      __ br(Assembler::EQ, L);
      __ stop("StubRoutines::call_stub: entered with pending exception");
      __ BIND(L);
    }
#endif
    // pass parameters if any
    __ mov(esp, sp);
    __ sub(rscratch1, sp, c_rarg6, ext::uxtw, LogBytesPerWord); // Move SP out of the way
    __ andr(sp, rscratch1, -2 * wordSize);

    BLOCK_COMMENT("pass parameters if any");
    Label parameters_done;
    // parameter count is still in c_rarg6
    // and parameter pointer identifying param 1 is in c_rarg5
    __ cbzw(c_rarg6, parameters_done);

    address loop = __ pc();
    __ ldr(rscratch1, Address(__ post(c_rarg5, wordSize)));
    __ subsw(c_rarg6, c_rarg6, 1);
    __ push(rscratch1);
    __ br(Assembler::GT, loop);

    __ BIND(parameters_done);

    // call Java entry -- passing methdoOop, and current sp
    //      rmethod: Method*
    //      r19_sender_sp: sender sp
    BLOCK_COMMENT("call Java function");
    __ mov(r19_sender_sp, sp);
    __ blr(c_rarg4);

    // we do this here because the notify will already have been done
    // if we get to the next instruction via an exception
    //
    // n.b. adding this instruction here affects the calculation of
    // whether or not a routine returns to the call stub (used when
    // doing stack walks) since the normal test is to check the return
    // pc against the address saved below. so we may need to allow for
    // this extra instruction in the check.

    // save current address for use by exception handling code

    return_address = __ pc();

    // store result depending on type (everything that is not
    // T_OBJECT, T_LONG, T_FLOAT or T_DOUBLE is treated as T_INT)
    // n.b. this assumes Java returns an integral result in r0
    // and a floating result in j_farg0
    __ ldr(j_rarg2, result);
    Label is_long, is_float, is_double, exit;
    __ ldr(j_rarg1, result_type);
    __ cmp(j_rarg1, (u1)T_OBJECT);
    __ br(Assembler::EQ, is_long);
    __ cmp(j_rarg1, (u1)T_LONG);
    __ br(Assembler::EQ, is_long);
    __ cmp(j_rarg1, (u1)T_FLOAT);
    __ br(Assembler::EQ, is_float);
    __ cmp(j_rarg1, (u1)T_DOUBLE);
    __ br(Assembler::EQ, is_double);

    // handle T_INT case
    __ strw(r0, Address(j_rarg2));

    __ BIND(exit);

    // pop parameters
    __ sub(esp, rfp, -sp_after_call_off * wordSize);

#ifdef ASSERT
    // verify that threads correspond
    {
      Label L, S;
      __ ldr(rscratch1, thread);
      __ cmp(rthread, rscratch1);
      __ br(Assembler::NE, S);
      __ get_thread(rscratch1);
      __ cmp(rthread, rscratch1);
      __ br(Assembler::EQ, L);
      __ BIND(S);
      __ stop("StubRoutines::call_stub: threads must correspond");
      __ BIND(L);
    }
#endif

    __ pop_cont_fastpath(rthread);

    // restore callee-save registers
    __ ldpd(v15, v14,  d15_save);
    __ ldpd(v13, v12,  d13_save);
    __ ldpd(v11, v10,  d11_save);
    __ ldpd(v9,  v8,   d9_save);

    __ ldp(r28, r27,   r28_save);
    __ ldp(r26, r25,   r26_save);
    __ ldp(r24, r23,   r24_save);
    __ ldp(r22, r21,   r22_save);
    __ ldp(r20, r19,   r20_save);

    // restore fpcr
    __ ldr(rscratch1,  fpcr_save);
    __ set_fpcr(rscratch1);

    __ ldp(c_rarg0, c_rarg1,  call_wrapper);
    __ ldrw(c_rarg2, result_type);
    __ ldr(c_rarg3,  method);
    __ ldp(c_rarg4, c_rarg5,  entry_point);
    __ ldp(c_rarg6, c_rarg7,  parameter_size);

    // leave frame and return to caller
    __ leave();
    __ ret(lr);

    // handle return types different from T_INT

    __ BIND(is_long);
    __ str(r0, Address(j_rarg2, 0));
    __ br(Assembler::AL, exit);

    __ BIND(is_float);
    __ strs(j_farg0, Address(j_rarg2, 0));
    __ br(Assembler::AL, exit);

    __ BIND(is_double);
    __ strd(j_farg0, Address(j_rarg2, 0));
    __ br(Assembler::AL, exit);

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
  // r0: exception oop

  address generate_catch_exception() {
    StubId stub_id = StubId::stubgen_catch_exception_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    // same as in generate_call_stub():
    const Address sp_after_call(rfp, sp_after_call_off * wordSize);
    const Address thread        (rfp, thread_off         * wordSize);

#ifdef ASSERT
    // verify that threads correspond
    {
      Label L, S;
      __ ldr(rscratch1, thread);
      __ cmp(rthread, rscratch1);
      __ br(Assembler::NE, S);
      __ get_thread(rscratch1);
      __ cmp(rthread, rscratch1);
      __ br(Assembler::EQ, L);
      __ bind(S);
      __ stop("StubRoutines::catch_exception: threads must correspond");
      __ bind(L);
    }
#endif

    // set pending exception
    __ verify_oop(r0);

    __ str(r0, Address(rthread, Thread::pending_exception_offset()));
    __ mov(rscratch1, (address)__FILE__);
    __ str(rscratch1, Address(rthread, Thread::exception_file_offset()));
    __ movw(rscratch1, (int)__LINE__);
    __ strw(rscratch1, Address(rthread, Thread::exception_line_offset()));

    // complete return to VM
    assert(StubRoutines::_call_stub_return_address != nullptr,
           "_call_stub_return_address must have been generated before");
    __ b(StubRoutines::_call_stub_return_address);

    return start;
  }

  // Continuation point for runtime calls returning with a pending
  // exception.  The pending exception check happened in the runtime
  // or native call stub.  The pending exception in Thread is
  // converted into a Java-level exception.
  //
  // Contract with Java-level exception handlers:
  // r0: exception
  // r3: throwing pc
  //
  // NOTE: At entry of this stub, exception-pc must be in LR !!

  // NOTE: this is always used as a jump target within generated code
  // so it just needs to be generated code with no x86 prolog

  address generate_forward_exception() {
    StubId stub_id = StubId::stubgen_forward_exception_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    // Upon entry, LR points to the return address returning into
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
      __ ldr(rscratch1, Address(rthread, Thread::pending_exception_offset()));
      __ cbnz(rscratch1, L);
      __ stop("StubRoutines::forward exception: no pending exception (1)");
      __ bind(L);
    }
#endif

    // compute exception handler into r19

    // call the VM to find the handler address associated with the
    // caller address. pass thread in r0 and caller pc (ret address)
    // in r1. n.b. the caller pc is in lr, unlike x86 where it is on
    // the stack.
    __ mov(c_rarg1, lr);
    // lr will be trashed by the VM call so we move it to R19
    // (callee-saved) because we also need to pass it to the handler
    // returned by this call.
    __ mov(r19, lr);
    BLOCK_COMMENT("call exception_handler_for_return_address");
    __ call_VM_leaf(CAST_FROM_FN_PTR(address,
                         SharedRuntime::exception_handler_for_return_address),
                    rthread, c_rarg1);
    // Reinitialize the ptrue predicate register, in case the external runtime
    // call clobbers ptrue reg, as we may return to SVE compiled code.
    __ reinitialize_ptrue();

    // we should not really care that lr is no longer the callee
    // address. we saved the value the handler needs in r19 so we can
    // just copy it to r3. however, the C2 handler will push its own
    // frame and then calls into the VM and the VM code asserts that
    // the PC for the frame above the handler belongs to a compiled
    // Java method. So, we restore lr here to satisfy that assert.
    __ mov(lr, r19);
    // setup r0 & r3 & clear pending exception
    __ mov(r3, r19);
    __ mov(r19, r0);
    __ ldr(r0, Address(rthread, Thread::pending_exception_offset()));
    __ str(zr, Address(rthread, Thread::pending_exception_offset()));

#ifdef ASSERT
    // make sure exception is set
    {
      Label L;
      __ cbnz(r0, L);
      __ stop("StubRoutines::forward exception: no pending exception (2)");
      __ bind(L);
    }
#endif

    // continue at exception handler
    // r0: exception
    // r3: throwing pc
    // r19: exception handler
    __ verify_oop(r0);
    __ br(r19);

    return start;
  }

  // Non-destructive plausibility checks for oops
  //
  // Arguments:
  //    r0: oop to verify
  //    rscratch1: error message
  //
  // Stack after saving c_rarg3:
  //    [tos + 0]: saved c_rarg3
  //    [tos + 1]: saved c_rarg2
  //    [tos + 2]: saved lr
  //    [tos + 3]: saved rscratch2
  //    [tos + 4]: saved r0
  //    [tos + 5]: saved rscratch1
  address generate_verify_oop() {
    StubId stub_id = StubId::stubgen_verify_oop_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Label exit, error;

    // save c_rarg2 and c_rarg3
    __ stp(c_rarg3, c_rarg2, Address(__ pre(sp, -16)));

    // __ incrementl(ExternalAddress((address) StubRoutines::verify_oop_count_addr()));
    __ lea(c_rarg2, ExternalAddress((address) StubRoutines::verify_oop_count_addr()));
    __ ldr(c_rarg3, Address(c_rarg2));
    __ add(c_rarg3, c_rarg3, 1);
    __ str(c_rarg3, Address(c_rarg2));

    // object is in r0
    // make sure object is 'reasonable'
    __ cbz(r0, exit); // if obj is null it is OK

    BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
    bs_asm->check_oop(_masm, r0, c_rarg2, c_rarg3, error);

    // return if everything seems ok
    __ bind(exit);

    __ ldp(c_rarg3, c_rarg2, Address(__ post(sp, 16)));
    __ ret(lr);

    // handle errors
    __ bind(error);
    __ ldp(c_rarg3, c_rarg2, Address(__ post(sp, 16)));

    __ push(RegSet::range(r0, r29), sp);
    // debug(char* msg, int64_t pc, int64_t regs[])
    __ mov(c_rarg0, rscratch1);      // pass address of error message
    __ mov(c_rarg1, lr);             // pass return address
    __ mov(c_rarg2, sp);             // pass address of regs on stack
#ifndef PRODUCT
    assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
#endif
    BLOCK_COMMENT("call MacroAssembler::debug");
    __ mov(rscratch1, CAST_FROM_FN_PTR(address, MacroAssembler::debug64));
    __ blr(rscratch1);
    __ hlt(0);

    return start;
  }

  // Generate indices for iota vector.
  address generate_iota_indices(StubId stub_id) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    // B
    __ emit_data64(0x0706050403020100, relocInfo::none);
    __ emit_data64(0x0F0E0D0C0B0A0908, relocInfo::none);
    // H
    __ emit_data64(0x0003000200010000, relocInfo::none);
    __ emit_data64(0x0007000600050004, relocInfo::none);
    // S
    __ emit_data64(0x0000000100000000, relocInfo::none);
    __ emit_data64(0x0000000300000002, relocInfo::none);
    // D
    __ emit_data64(0x0000000000000000, relocInfo::none);
    __ emit_data64(0x0000000000000001, relocInfo::none);
    // S - FP
    __ emit_data64(0x3F80000000000000, relocInfo::none); // 0.0f, 1.0f
    __ emit_data64(0x4040000040000000, relocInfo::none); // 2.0f, 3.0f
    // D - FP
    __ emit_data64(0x0000000000000000, relocInfo::none); // 0.0d
    __ emit_data64(0x3FF0000000000000, relocInfo::none); // 1.0d
    return start;
  }

  // The inner part of zero_words().  This is the bulk operation,
  // zeroing words in blocks, possibly using DC ZVA to do it.  The
  // caller is responsible for zeroing the last few words.
  //
  // Inputs:
  // r10: the HeapWord-aligned base address of an array to zero.
  // r11: the count in HeapWords, r11 > 0.
  //
  // Returns r10 and r11, adjusted for the caller to clear.
  // r10: the base address of the tail of words left to clear.
  // r11: the number of words in the tail.
  //      r11 < MacroAssembler::zero_words_block_size.

  address generate_zero_blocks() {
    Label done;
    Label base_aligned;

    Register base = r10, cnt = r11;

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_zero_blocks_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    if (UseBlockZeroing) {
      int zva_length = VM_Version::zva_length();

      // Ensure ZVA length can be divided by 16. This is required by
      // the subsequent operations.
      assert (zva_length % 16 == 0, "Unexpected ZVA Length");

      __ tbz(base, 3, base_aligned);
      __ str(zr, Address(__ post(base, 8)));
      __ sub(cnt, cnt, 1);
      __ bind(base_aligned);

      // Ensure count >= zva_length * 2 so that it still deserves a zva after
      // alignment.
      Label small;
      int low_limit = MAX2(zva_length * 2, (int)BlockZeroingLowLimit);
      __ subs(rscratch1, cnt, low_limit >> 3);
      __ br(Assembler::LT, small);
      __ zero_dcache_blocks(base, cnt);
      __ bind(small);
    }

    {
      // Number of stp instructions we'll unroll
      const int unroll =
        MacroAssembler::zero_words_block_size / 2;
      // Clear the remaining blocks.
      Label loop;
      __ subs(cnt, cnt, unroll * 2);
      __ br(Assembler::LT, done);
      __ bind(loop);
      for (int i = 0; i < unroll; i++)
        __ stp(zr, zr, __ post(base, 16));
      __ subs(cnt, cnt, unroll * 2);
      __ br(Assembler::GE, loop);
      __ bind(done);
      __ add(cnt, cnt, unroll * 2);
    }

    __ ret(lr);

    return start;
  }


  typedef enum {
    copy_forwards = 1,
    copy_backwards = -1
  } copy_direction;

  // Helper object to reduce noise when telling the GC barriers how to perform loads and stores
  // for arraycopy stubs.
  class ArrayCopyBarrierSetHelper : StackObj {
    BarrierSetAssembler* _bs_asm;
    MacroAssembler* _masm;
    DecoratorSet _decorators;
    BasicType _type;
    Register _gct1;
    Register _gct2;
    Register _gct3;
    FloatRegister _gcvt1;
    FloatRegister _gcvt2;
    FloatRegister _gcvt3;

  public:
    ArrayCopyBarrierSetHelper(MacroAssembler* masm,
                              DecoratorSet decorators,
                              BasicType type,
                              Register gct1,
                              Register gct2,
                              Register gct3,
                              FloatRegister gcvt1,
                              FloatRegister gcvt2,
                              FloatRegister gcvt3)
      : _bs_asm(BarrierSet::barrier_set()->barrier_set_assembler()),
        _masm(masm),
        _decorators(decorators),
        _type(type),
        _gct1(gct1),
        _gct2(gct2),
        _gct3(gct3),
        _gcvt1(gcvt1),
        _gcvt2(gcvt2),
        _gcvt3(gcvt3) {
    }

    void copy_load_at_32(FloatRegister dst1, FloatRegister dst2, Address src) {
      _bs_asm->copy_load_at(_masm, _decorators, _type, 32,
                            dst1, dst2, src,
                            _gct1, _gct2, _gcvt1);
    }

    void copy_store_at_32(Address dst, FloatRegister src1, FloatRegister src2) {
      _bs_asm->copy_store_at(_masm, _decorators, _type, 32,
                             dst, src1, src2,
                             _gct1, _gct2, _gct3, _gcvt1, _gcvt2, _gcvt3);
    }

    void copy_load_at_16(Register dst1, Register dst2, Address src) {
      _bs_asm->copy_load_at(_masm, _decorators, _type, 16,
                            dst1, dst2, src,
                            _gct1);
    }

    void copy_store_at_16(Address dst, Register src1, Register src2) {
      _bs_asm->copy_store_at(_masm, _decorators, _type, 16,
                             dst, src1, src2,
                             _gct1, _gct2, _gct3);
    }

    void copy_load_at_8(Register dst, Address src) {
      _bs_asm->copy_load_at(_masm, _decorators, _type, 8,
                            dst, noreg, src,
                            _gct1);
    }

    void copy_store_at_8(Address dst, Register src) {
      _bs_asm->copy_store_at(_masm, _decorators, _type, 8,
                             dst, src, noreg,
                             _gct1, _gct2, _gct3);
    }
  };

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
  void generate_copy_longs(StubId stub_id, DecoratorSet decorators, Label &start, Register s, Register d, Register count) {
    BasicType type;
    copy_direction direction;

    switch (stub_id) {
    case StubId::stubgen_copy_byte_f_id:
      direction = copy_forwards;
      type = T_BYTE;
      break;
    case StubId::stubgen_copy_byte_b_id:
      direction = copy_backwards;
      type = T_BYTE;
      break;
    case StubId::stubgen_copy_oop_f_id:
      direction = copy_forwards;
      type = T_OBJECT;
      break;
    case StubId::stubgen_copy_oop_b_id:
      direction = copy_backwards;
      type = T_OBJECT;
      break;
    case StubId::stubgen_copy_oop_uninit_f_id:
      direction = copy_forwards;
      type = T_OBJECT;
      break;
    case StubId::stubgen_copy_oop_uninit_b_id:
      direction = copy_backwards;
      type = T_OBJECT;
      break;
    default:
      ShouldNotReachHere();
    }

    int unit = wordSize * direction;
    int bias = (UseSIMDForMemoryOps ? 4:2) * wordSize;

    const Register t0 = r3, t1 = r4, t2 = r5, t3 = r6,
      t4 = r7, t5 = r11, t6 = r12, t7 = r13;
    const Register stride = r14;
    const Register gct1 = rscratch1, gct2 = rscratch2, gct3 = r10;
    const FloatRegister gcvt1 = v6, gcvt2 = v7, gcvt3 = v16; // Note that v8-v15 are callee saved
    ArrayCopyBarrierSetHelper bs(_masm, decorators, type, gct1, gct2, gct3, gcvt1, gcvt2, gcvt3);

    assert_different_registers(rscratch1, rscratch2, t0, t1, t2, t3, t4, t5, t6, t7);
    assert_different_registers(s, d, count, rscratch1, rscratch2);

    Label again, drain;

    __ align(CodeEntryAlignment);

    StubCodeMark mark(this, stub_id);

    __ bind(start);

    Label unaligned_copy_long;
    if (AvoidUnalignedAccesses) {
      __ tbnz(d, 3, unaligned_copy_long);
    }

    if (direction == copy_forwards) {
      __ sub(s, s, bias);
      __ sub(d, d, bias);
    }

#ifdef ASSERT
    // Make sure we are never given < 8 words
    {
      Label L;
      __ cmp(count, (u1)8);
      __ br(Assembler::GE, L);
      __ stop("genrate_copy_longs called with < 8 words");
      __ bind(L);
    }
#endif

    // Fill 8 registers
    if (UseSIMDForMemoryOps) {
      bs.copy_load_at_32(v0, v1, Address(s, 4 * unit));
      bs.copy_load_at_32(v2, v3, Address(__ pre(s, 8 * unit)));
    } else {
      bs.copy_load_at_16(t0, t1, Address(s, 2 * unit));
      bs.copy_load_at_16(t2, t3, Address(s, 4 * unit));
      bs.copy_load_at_16(t4, t5, Address(s, 6 * unit));
      bs.copy_load_at_16(t6, t7, Address(__ pre(s, 8 * unit)));
    }

    __ subs(count, count, 16);
    __ br(Assembler::LO, drain);

    int prefetch = PrefetchCopyIntervalInBytes;
    bool use_stride = false;
    if (direction == copy_backwards) {
       use_stride = prefetch > 256;
       prefetch = -prefetch;
       if (use_stride) __ mov(stride, prefetch);
    }

    __ bind(again);

    if (PrefetchCopyIntervalInBytes > 0)
      __ prfm(use_stride ? Address(s, stride) : Address(s, prefetch), PLDL1KEEP);

    if (UseSIMDForMemoryOps) {
      bs.copy_store_at_32(Address(d, 4 * unit), v0, v1);
      bs.copy_load_at_32(v0, v1, Address(s, 4 * unit));
      bs.copy_store_at_32(Address(__ pre(d, 8 * unit)), v2, v3);
      bs.copy_load_at_32(v2, v3, Address(__ pre(s, 8 * unit)));
    } else {
      bs.copy_store_at_16(Address(d, 2 * unit), t0, t1);
      bs.copy_load_at_16(t0, t1, Address(s, 2 * unit));
      bs.copy_store_at_16(Address(d, 4 * unit), t2, t3);
      bs.copy_load_at_16(t2, t3, Address(s, 4 * unit));
      bs.copy_store_at_16(Address(d, 6 * unit), t4, t5);
      bs.copy_load_at_16(t4, t5, Address(s, 6 * unit));
      bs.copy_store_at_16(Address(__ pre(d, 8 * unit)), t6, t7);
      bs.copy_load_at_16(t6, t7, Address(__ pre(s, 8 * unit)));
    }

    __ subs(count, count, 8);
    __ br(Assembler::HS, again);

    // Drain
    __ bind(drain);
    if (UseSIMDForMemoryOps) {
      bs.copy_store_at_32(Address(d, 4 * unit), v0, v1);
      bs.copy_store_at_32(Address(__ pre(d, 8 * unit)), v2, v3);
    } else {
      bs.copy_store_at_16(Address(d, 2 * unit), t0, t1);
      bs.copy_store_at_16(Address(d, 4 * unit), t2, t3);
      bs.copy_store_at_16(Address(d, 6 * unit), t4, t5);
      bs.copy_store_at_16(Address(__ pre(d, 8 * unit)), t6, t7);
    }

    {
      Label L1, L2;
      __ tbz(count, exact_log2(4), L1);
      if (UseSIMDForMemoryOps) {
        bs.copy_load_at_32(v0, v1, Address(__ pre(s, 4 * unit)));
        bs.copy_store_at_32(Address(__ pre(d, 4 * unit)), v0, v1);
      } else {
        bs.copy_load_at_16(t0, t1, Address(s, 2 * unit));
        bs.copy_load_at_16(t2, t3, Address(__ pre(s, 4 * unit)));
        bs.copy_store_at_16(Address(d, 2 * unit), t0, t1);
        bs.copy_store_at_16(Address(__ pre(d, 4 * unit)), t2, t3);
      }
      __ bind(L1);

      if (direction == copy_forwards) {
        __ add(s, s, bias);
        __ add(d, d, bias);
      }

      __ tbz(count, 1, L2);
      bs.copy_load_at_16(t0, t1, Address(__ adjust(s, 2 * unit, direction == copy_backwards)));
      bs.copy_store_at_16(Address(__ adjust(d, 2 * unit, direction == copy_backwards)), t0, t1);
      __ bind(L2);
    }

    __ ret(lr);

    if (AvoidUnalignedAccesses) {
      Label drain, again;
      // Register order for storing. Order is different for backward copy.

      __ bind(unaligned_copy_long);

      // source address is even aligned, target odd aligned
      //
      // when forward copying word pairs we read long pairs at offsets
      // {0, 2, 4, 6} (in long words). when backwards copying we read
      // long pairs at offsets {-2, -4, -6, -8}. We adjust the source
      // address by -2 in the forwards case so we can compute the
      // source offsets for both as {2, 4, 6, 8} * unit where unit = 1
      // or -1.
      //
      // when forward copying we need to store 1 word, 3 pairs and
      // then 1 word at offsets {0, 1, 3, 5, 7}. Rather than use a
      // zero offset We adjust the destination by -1 which means we
      // have to use offsets { 1, 2, 4, 6, 8} * unit for the stores.
      //
      // When backwards copyng we need to store 1 word, 3 pairs and
      // then 1 word at offsets {-1, -3, -5, -7, -8} i.e. we use
      // offsets {1, 3, 5, 7, 8} * unit.

      if (direction == copy_forwards) {
        __ sub(s, s, 16);
        __ sub(d, d, 8);
      }

      // Fill 8 registers
      //
      // for forwards copy s was offset by -16 from the original input
      // value of s so the register contents are at these offsets
      // relative to the 64 bit block addressed by that original input
      // and so on for each successive 64 byte block when s is updated
      //
      // t0 at offset 0,  t1 at offset 8
      // t2 at offset 16, t3 at offset 24
      // t4 at offset 32, t5 at offset 40
      // t6 at offset 48, t7 at offset 56

      // for backwards copy s was not offset so the register contents
      // are at these offsets into the preceding 64 byte block
      // relative to that original input and so on for each successive
      // preceding 64 byte block when s is updated. this explains the
      // slightly counter-intuitive looking pattern of register usage
      // in the stp instructions for backwards copy.
      //
      // t0 at offset -16, t1 at offset -8
      // t2 at offset -32, t3 at offset -24
      // t4 at offset -48, t5 at offset -40
      // t6 at offset -64, t7 at offset -56

      bs.copy_load_at_16(t0, t1, Address(s, 2 * unit));
      bs.copy_load_at_16(t2, t3, Address(s, 4 * unit));
      bs.copy_load_at_16(t4, t5, Address(s, 6 * unit));
      bs.copy_load_at_16(t6, t7, Address(__ pre(s, 8 * unit)));

      __ subs(count, count, 16);
      __ br(Assembler::LO, drain);

      int prefetch = PrefetchCopyIntervalInBytes;
      bool use_stride = false;
      if (direction == copy_backwards) {
         use_stride = prefetch > 256;
         prefetch = -prefetch;
         if (use_stride) __ mov(stride, prefetch);
      }

      __ bind(again);

      if (PrefetchCopyIntervalInBytes > 0)
        __ prfm(use_stride ? Address(s, stride) : Address(s, prefetch), PLDL1KEEP);

      if (direction == copy_forwards) {
       // allowing for the offset of -8 the store instructions place
       // registers into the target 64 bit block at the following
       // offsets
       //
       // t0 at offset 0
       // t1 at offset 8,  t2 at offset 16
       // t3 at offset 24, t4 at offset 32
       // t5 at offset 40, t6 at offset 48
       // t7 at offset 56

        bs.copy_store_at_8(Address(d, 1 * unit), t0);
        bs.copy_store_at_16(Address(d, 2 * unit), t1, t2);
        bs.copy_load_at_16(t0, t1, Address(s, 2 * unit));
        bs.copy_store_at_16(Address(d, 4 * unit), t3, t4);
        bs.copy_load_at_16(t2, t3, Address(s, 4 * unit));
        bs.copy_store_at_16(Address(d, 6 * unit), t5, t6);
        bs.copy_load_at_16(t4, t5, Address(s, 6 * unit));
        bs.copy_store_at_8(Address(__ pre(d, 8 * unit)), t7);
        bs.copy_load_at_16(t6, t7, Address(__ pre(s, 8 * unit)));
      } else {
       // d was not offset when we started so the registers are
       // written into the 64 bit block preceding d with the following
       // offsets
       //
       // t1 at offset -8
       // t3 at offset -24, t0 at offset -16
       // t5 at offset -48, t2 at offset -32
       // t7 at offset -56, t4 at offset -48
       //                   t6 at offset -64
       //
       // note that this matches the offsets previously noted for the
       // loads

        bs.copy_store_at_8(Address(d, 1 * unit), t1);
        bs.copy_store_at_16(Address(d, 3 * unit), t3, t0);
        bs.copy_load_at_16(t0, t1, Address(s, 2 * unit));
        bs.copy_store_at_16(Address(d, 5 * unit), t5, t2);
        bs.copy_load_at_16(t2, t3, Address(s, 4 * unit));
        bs.copy_store_at_16(Address(d, 7 * unit), t7, t4);
        bs.copy_load_at_16(t4, t5, Address(s, 6 * unit));
        bs.copy_store_at_8(Address(__ pre(d, 8 * unit)), t6);
        bs.copy_load_at_16(t6, t7, Address(__ pre(s, 8 * unit)));
      }

      __ subs(count, count, 8);
      __ br(Assembler::HS, again);

      // Drain
      //
      // this uses the same pattern of offsets and register arguments
      // as above
      __ bind(drain);
      if (direction == copy_forwards) {
        bs.copy_store_at_8(Address(d, 1 * unit), t0);
        bs.copy_store_at_16(Address(d, 2 * unit), t1, t2);
        bs.copy_store_at_16(Address(d, 4 * unit), t3, t4);
        bs.copy_store_at_16(Address(d, 6 * unit), t5, t6);
        bs.copy_store_at_8(Address(__ pre(d, 8 * unit)), t7);
      } else {
        bs.copy_store_at_8(Address(d, 1 * unit), t1);
        bs.copy_store_at_16(Address(d, 3 * unit), t3, t0);
        bs.copy_store_at_16(Address(d, 5 * unit), t5, t2);
        bs.copy_store_at_16(Address(d, 7 * unit), t7, t4);
        bs.copy_store_at_8(Address(__ pre(d, 8 * unit)), t6);
      }
      // now we need to copy any remaining part block which may
      // include a 4 word block subblock and/or a 2 word subblock.
      // bits 2 and 1 in the count are the tell-tale for whether we
      // have each such subblock
      {
        Label L1, L2;
        __ tbz(count, exact_log2(4), L1);
       // this is the same as above but copying only 4 longs hence
       // with only one intervening stp between the str instructions
       // but note that the offsets and registers still follow the
       // same pattern
        bs.copy_load_at_16(t0, t1, Address(s, 2 * unit));
        bs.copy_load_at_16(t2, t3, Address(__ pre(s, 4 * unit)));
        if (direction == copy_forwards) {
          bs.copy_store_at_8(Address(d, 1 * unit), t0);
          bs.copy_store_at_16(Address(d, 2 * unit), t1, t2);
          bs.copy_store_at_8(Address(__ pre(d, 4 * unit)), t3);
        } else {
          bs.copy_store_at_8(Address(d, 1 * unit), t1);
          bs.copy_store_at_16(Address(d, 3 * unit), t3, t0);
          bs.copy_store_at_8(Address(__ pre(d, 4 * unit)), t2);
        }
        __ bind(L1);

        __ tbz(count, 1, L2);
       // this is the same as above but copying only 2 longs hence
       // there is no intervening stp between the str instructions
       // but note that the offset and register patterns are still
       // the same
        bs.copy_load_at_16(t0, t1, Address(__ pre(s, 2 * unit)));
        if (direction == copy_forwards) {
          bs.copy_store_at_8(Address(d, 1 * unit), t0);
          bs.copy_store_at_8(Address(__ pre(d, 2 * unit)), t1);
        } else {
          bs.copy_store_at_8(Address(d, 1 * unit), t1);
          bs.copy_store_at_8(Address(__ pre(d, 2 * unit)), t0);
        }
        __ bind(L2);

       // for forwards copy we need to re-adjust the offsets we
       // applied so that s and d are follow the last words written

       if (direction == copy_forwards) {
         __ add(s, s, 16);
         __ add(d, d, 8);
       }

      }

      __ ret(lr);
      }
  }

  // Small copy: less than 16 bytes.
  //
  // NB: Ignores all of the bits of count which represent more than 15
  // bytes, so a caller doesn't have to mask them.

  void copy_memory_small(DecoratorSet decorators, BasicType type, Register s, Register d, Register count, int step) {
    bool is_backwards = step < 0;
    size_t granularity = g_uabs(step);
    int direction = is_backwards ? -1 : 1;

    Label Lword, Lint, Lshort, Lbyte;

    assert(granularity
           && granularity <= sizeof (jlong), "Impossible granularity in copy_memory_small");

    const Register t0 = r3;
    const Register gct1 = rscratch1, gct2 = rscratch2, gct3 = r10;
    ArrayCopyBarrierSetHelper bs(_masm, decorators, type, gct1, gct2, gct3, fnoreg, fnoreg, fnoreg);

    // ??? I don't know if this bit-test-and-branch is the right thing
    // to do.  It does a lot of jumping, resulting in several
    // mispredicted branches.  It might make more sense to do this
    // with something like Duff's device with a single computed branch.

    __ tbz(count, 3 - exact_log2(granularity), Lword);
    bs.copy_load_at_8(t0, Address(__ adjust(s, direction * wordSize, is_backwards)));
    bs.copy_store_at_8(Address(__ adjust(d, direction * wordSize, is_backwards)), t0);
    __ bind(Lword);

    if (granularity <= sizeof (jint)) {
      __ tbz(count, 2 - exact_log2(granularity), Lint);
      __ ldrw(t0, Address(__ adjust(s, sizeof (jint) * direction, is_backwards)));
      __ strw(t0, Address(__ adjust(d, sizeof (jint) * direction, is_backwards)));
      __ bind(Lint);
    }

    if (granularity <= sizeof (jshort)) {
      __ tbz(count, 1 - exact_log2(granularity), Lshort);
      __ ldrh(t0, Address(__ adjust(s, sizeof (jshort) * direction, is_backwards)));
      __ strh(t0, Address(__ adjust(d, sizeof (jshort) * direction, is_backwards)));
      __ bind(Lshort);
    }

    if (granularity <= sizeof (jbyte)) {
      __ tbz(count, 0, Lbyte);
      __ ldrb(t0, Address(__ adjust(s, sizeof (jbyte) * direction, is_backwards)));
      __ strb(t0, Address(__ adjust(d, sizeof (jbyte) * direction, is_backwards)));
      __ bind(Lbyte);
    }
  }

  Label copy_f, copy_b;
  Label copy_obj_f, copy_obj_b;
  Label copy_obj_uninit_f, copy_obj_uninit_b;

  // All-singing all-dancing memory copy.
  //
  // Copy count units of memory from s to d.  The size of a unit is
  // step, which can be positive or negative depending on the direction
  // of copy.  If is_aligned is false, we align the source address.
  //

  void copy_memory(DecoratorSet decorators, BasicType type, bool is_aligned,
                   Register s, Register d, Register count, int step) {
    copy_direction direction = step < 0 ? copy_backwards : copy_forwards;
    bool is_backwards = step < 0;
    unsigned int granularity = g_uabs(step);
    const Register t0 = r3, t1 = r4;

    // <= 80 (or 96 for SIMD) bytes do inline. Direction doesn't matter because we always
    // load all the data before writing anything
    Label copy4, copy8, copy16, copy32, copy80, copy_big, finish;
    const Register t2 = r5, t3 = r6, t4 = r7, t5 = r11;
    const Register t6 = r12, t7 = r13, t8 = r14, t9 = r15;
    const Register send = r17, dend = r16;
    const Register gct1 = rscratch1, gct2 = rscratch2, gct3 = r10;
    const FloatRegister gcvt1 = v6, gcvt2 = v7, gcvt3 = v16; // Note that v8-v15 are callee saved
    ArrayCopyBarrierSetHelper bs(_masm, decorators, type, gct1, gct2, gct3, gcvt1, gcvt2, gcvt3);

    if (PrefetchCopyIntervalInBytes > 0)
      __ prfm(Address(s, 0), PLDL1KEEP);
    __ cmp(count, u1((UseSIMDForMemoryOps ? 96:80)/granularity));
    __ br(Assembler::HI, copy_big);

    __ lea(send, Address(s, count, Address::lsl(exact_log2(granularity))));
    __ lea(dend, Address(d, count, Address::lsl(exact_log2(granularity))));

    __ cmp(count, u1(16/granularity));
    __ br(Assembler::LS, copy16);

    __ cmp(count, u1(64/granularity));
    __ br(Assembler::HI, copy80);

    __ cmp(count, u1(32/granularity));
    __ br(Assembler::LS, copy32);

    // 33..64 bytes
    if (UseSIMDForMemoryOps) {
      bs.copy_load_at_32(v0, v1, Address(s, 0));
      bs.copy_load_at_32(v2, v3, Address(send, -32));
      bs.copy_store_at_32(Address(d, 0), v0, v1);
      bs.copy_store_at_32(Address(dend, -32), v2, v3);
    } else {
      bs.copy_load_at_16(t0, t1, Address(s, 0));
      bs.copy_load_at_16(t2, t3, Address(s, 16));
      bs.copy_load_at_16(t4, t5, Address(send, -32));
      bs.copy_load_at_16(t6, t7, Address(send, -16));

      bs.copy_store_at_16(Address(d, 0), t0, t1);
      bs.copy_store_at_16(Address(d, 16), t2, t3);
      bs.copy_store_at_16(Address(dend, -32), t4, t5);
      bs.copy_store_at_16(Address(dend, -16), t6, t7);
    }
    __ b(finish);

    // 17..32 bytes
    __ bind(copy32);
    bs.copy_load_at_16(t0, t1, Address(s, 0));
    bs.copy_load_at_16(t6, t7, Address(send, -16));

    bs.copy_store_at_16(Address(d, 0), t0, t1);
    bs.copy_store_at_16(Address(dend, -16), t6, t7);
    __ b(finish);

    // 65..80/96 bytes
    // (96 bytes if SIMD because we do 32 byes per instruction)
    __ bind(copy80);
    if (UseSIMDForMemoryOps) {
      bs.copy_load_at_32(v0, v1, Address(s, 0));
      bs.copy_load_at_32(v2, v3, Address(s, 32));
      // Unaligned pointers can be an issue for copying.
      // The issue has more chances to happen when granularity of data is
      // less than 4(sizeof(jint)). Pointers for arrays of jint are at least
      // 4 byte aligned. Pointers for arrays of jlong are 8 byte aligned.
      // The most performance drop has been seen for the range 65-80 bytes.
      // For such cases using the pair of ldp/stp instead of the third pair of
      // ldpq/stpq fixes the performance issue.
      if (granularity < sizeof (jint)) {
        Label copy96;
        __ cmp(count, u1(80/granularity));
        __ br(Assembler::HI, copy96);
        bs.copy_load_at_16(t0, t1, Address(send, -16));

        bs.copy_store_at_32(Address(d, 0), v0, v1);
        bs.copy_store_at_32(Address(d, 32), v2, v3);

        bs.copy_store_at_16(Address(dend, -16), t0, t1);
        __ b(finish);

        __ bind(copy96);
      }
      bs.copy_load_at_32(v4, v5, Address(send, -32));

      bs.copy_store_at_32(Address(d, 0), v0, v1);
      bs.copy_store_at_32(Address(d, 32), v2, v3);

      bs.copy_store_at_32(Address(dend, -32), v4, v5);
    } else {
      bs.copy_load_at_16(t0, t1, Address(s, 0));
      bs.copy_load_at_16(t2, t3, Address(s, 16));
      bs.copy_load_at_16(t4, t5, Address(s, 32));
      bs.copy_load_at_16(t6, t7, Address(s, 48));
      bs.copy_load_at_16(t8, t9, Address(send, -16));

      bs.copy_store_at_16(Address(d, 0), t0, t1);
      bs.copy_store_at_16(Address(d, 16), t2, t3);
      bs.copy_store_at_16(Address(d, 32), t4, t5);
      bs.copy_store_at_16(Address(d, 48), t6, t7);
      bs.copy_store_at_16(Address(dend, -16), t8, t9);
    }
    __ b(finish);

    // 0..16 bytes
    __ bind(copy16);
    __ cmp(count, u1(8/granularity));
    __ br(Assembler::LO, copy8);

    // 8..16 bytes
    bs.copy_load_at_8(t0, Address(s, 0));
    bs.copy_load_at_8(t1, Address(send, -8));
    bs.copy_store_at_8(Address(d, 0), t0);
    bs.copy_store_at_8(Address(dend, -8), t1);
    __ b(finish);

    if (granularity < 8) {
      // 4..7 bytes
      __ bind(copy8);
      __ tbz(count, 2 - exact_log2(granularity), copy4);
      __ ldrw(t0, Address(s, 0));
      __ ldrw(t1, Address(send, -4));
      __ strw(t0, Address(d, 0));
      __ strw(t1, Address(dend, -4));
      __ b(finish);
      if (granularity < 4) {
        // 0..3 bytes
        __ bind(copy4);
        __ cbz(count, finish); // get rid of 0 case
        if (granularity == 2) {
          __ ldrh(t0, Address(s, 0));
          __ strh(t0, Address(d, 0));
        } else { // granularity == 1
          // Now 1..3 bytes. Handle the 1 and 2 byte case by copying
          // the first and last byte.
          // Handle the 3 byte case by loading and storing base + count/2
          // (count == 1 (s+0)->(d+0), count == 2,3 (s+1) -> (d+1))
          // This does means in the 1 byte case we load/store the same
          // byte 3 times.
          __ lsr(count, count, 1);
          __ ldrb(t0, Address(s, 0));
          __ ldrb(t1, Address(send, -1));
          __ ldrb(t2, Address(s, count));
          __ strb(t0, Address(d, 0));
          __ strb(t1, Address(dend, -1));
          __ strb(t2, Address(d, count));
        }
        __ b(finish);
      }
    }

    __ bind(copy_big);
    if (is_backwards) {
      __ lea(s, Address(s, count, Address::lsl(exact_log2(-step))));
      __ lea(d, Address(d, count, Address::lsl(exact_log2(-step))));
    }

    // Now we've got the small case out of the way we can align the
    // source address on a 2-word boundary.

    // Here we will materialize a count in r15, which is used by copy_memory_small
    // and the various generate_copy_longs stubs that we use for 2 word aligned bytes.
    // Up until here, we have used t9, which aliases r15, but from here on, that register
    // can not be used as a temp register, as it contains the count.

    Label aligned;

    if (is_aligned) {
      // We may have to adjust by 1 word to get s 2-word-aligned.
      __ tbz(s, exact_log2(wordSize), aligned);
      bs.copy_load_at_8(t0, Address(__ adjust(s, direction * wordSize, is_backwards)));
      bs.copy_store_at_8(Address(__ adjust(d, direction * wordSize, is_backwards)), t0);
      __ sub(count, count, wordSize/granularity);
    } else {
      if (is_backwards) {
        __ andr(r15, s, 2 * wordSize - 1);
      } else {
        __ neg(r15, s);
        __ andr(r15, r15, 2 * wordSize - 1);
      }
      // r15 is the byte adjustment needed to align s.
      __ cbz(r15, aligned);
      int shift = exact_log2(granularity);
      if (shift > 0) {
        __ lsr(r15, r15, shift);
      }
      __ sub(count, count, r15);

#if 0
      // ?? This code is only correct for a disjoint copy.  It may or
      // may not make sense to use it in that case.

      // Copy the first pair; s and d may not be aligned.
      __ ldp(t0, t1, Address(s, is_backwards ? -2 * wordSize : 0));
      __ stp(t0, t1, Address(d, is_backwards ? -2 * wordSize : 0));

      // Align s and d, adjust count
      if (is_backwards) {
        __ sub(s, s, r15);
        __ sub(d, d, r15);
      } else {
        __ add(s, s, r15);
        __ add(d, d, r15);
      }
#else
      copy_memory_small(decorators, type, s, d, r15, step);
#endif
    }

    __ bind(aligned);

    // s is now 2-word-aligned.

    // We have a count of units and some trailing bytes. Adjust the
    // count and do a bulk copy of words. If the shift is zero
    // perform a move instead to benefit from zero latency moves.
    int shift = exact_log2(wordSize/granularity);
    if (shift > 0) {
      __ lsr(r15, count, shift);
    } else {
      __ mov(r15, count);
    }
    if (direction == copy_forwards) {
      if (type != T_OBJECT) {
        __ bl(copy_f);
      } else if ((decorators & IS_DEST_UNINITIALIZED) != 0) {
        __ bl(copy_obj_uninit_f);
      } else {
        __ bl(copy_obj_f);
      }
    } else {
      if (type != T_OBJECT) {
        __ bl(copy_b);
      } else if ((decorators & IS_DEST_UNINITIALIZED) != 0) {
        __ bl(copy_obj_uninit_b);
      } else {
        __ bl(copy_obj_b);
      }
    }

    // And the tail.
    copy_memory_small(decorators, type, s, d, count, step);

    if (granularity >= 8) __ bind(copy8);
    if (granularity >= 4) __ bind(copy4);
    __ bind(finish);
  }


  void clobber_registers() {
#ifdef ASSERT
    RegSet clobbered
      = MacroAssembler::call_clobbered_gp_registers() - rscratch1;
    __ mov(rscratch1, (uint64_t)0xdeadbeef);
    __ orr(rscratch1, rscratch1, rscratch1, Assembler::LSL, 32);
    for (RegSetIterator<Register> it = clobbered.begin(); *it != noreg; ++it) {
      __ mov(*it, rscratch1);
    }
#endif

  }

  // Scan over array at a for count oops, verifying each one.
  // Preserves a and count, clobbers rscratch1 and rscratch2.
  void verify_oop_array (int size, Register a, Register count, Register temp) {
    Label loop, end;
    __ mov(rscratch1, a);
    __ mov(rscratch2, zr);
    __ bind(loop);
    __ cmp(rscratch2, count);
    __ br(Assembler::HS, end);
    if (size == wordSize) {
      __ ldr(temp, Address(a, rscratch2, Address::lsl(exact_log2(size))));
      __ verify_oop(temp);
    } else {
      __ ldrw(temp, Address(a, rscratch2, Address::lsl(exact_log2(size))));
      __ decode_heap_oop(temp); // calls verify_oop
    }
    __ add(rscratch2, rscratch2, 1);
    __ b(loop);
    __ bind(end);
  }

  // Arguments:
  //   stub_id - is used to name the stub and identify all details of
  //             how to perform the copy.
  //
  //   entry - is assigned to the stub's post push entry point unless
  //           it is null
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
  // Side Effects: entry is set to the (post push) entry point so it
  //               can be used by the corresponding conjoint copy
  //               method
  //
  address generate_disjoint_copy(StubId stub_id, address *entry) {
    Register s = c_rarg0, d = c_rarg1, count = c_rarg2;
    RegSet saved_reg = RegSet::of(s, d, count);
    int size;
    bool aligned;
    bool is_oop;
    bool dest_uninitialized;
    switch (stub_id) {
    case StubId::stubgen_jbyte_disjoint_arraycopy_id:
      size = sizeof(jbyte);
      aligned = false;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_arrayof_jbyte_disjoint_arraycopy_id:
      size = sizeof(jbyte);
      aligned = true;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_jshort_disjoint_arraycopy_id:
      size = sizeof(jshort);
      aligned = false;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_arrayof_jshort_disjoint_arraycopy_id:
      size = sizeof(jshort);
      aligned = true;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_jint_disjoint_arraycopy_id:
      size = sizeof(jint);
      aligned = false;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_arrayof_jint_disjoint_arraycopy_id:
      size = sizeof(jint);
      aligned = true;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_jlong_disjoint_arraycopy_id:
      // since this is always aligned we can (should!) use the same
      // stub as for case StubId::stubgen_arrayof_jlong_disjoint_arraycopy
      ShouldNotReachHere();
      break;
    case StubId::stubgen_arrayof_jlong_disjoint_arraycopy_id:
      size = sizeof(jlong);
      aligned = true;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_oop_disjoint_arraycopy_id:
      size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
      aligned = !UseCompressedOops;
      is_oop = true;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_arrayof_oop_disjoint_arraycopy_id:
      size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
      aligned = !UseCompressedOops;
      is_oop = true;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_oop_disjoint_arraycopy_uninit_id:
      size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
      aligned = !UseCompressedOops;
      is_oop = true;
      dest_uninitialized = true;
      break;
    case StubId::stubgen_arrayof_oop_disjoint_arraycopy_uninit_id:
      size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
      aligned = !UseCompressedOops;
      is_oop = true;
      dest_uninitialized = true;
      break;
    default:
      ShouldNotReachHere();
      break;
    }

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
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
      __ push(RegSet::of(d, count), sp);
    }
    {
      // UnsafeMemoryAccess page error: continue after unsafe access
      bool add_entry = !is_oop && (!aligned || sizeof(jlong) == size);
      UnsafeMemoryAccessMark umam(this, add_entry, true);
      copy_memory(decorators, is_oop ? T_OBJECT : T_BYTE, aligned, s, d, count, size);
    }

    if (is_oop) {
      __ pop(RegSet::of(d, count), sp);
      if (VerifyOops)
        verify_oop_array(size, d, count, r16);
    }

    bs->arraycopy_epilogue(_masm, decorators, is_oop, d, count, rscratch1, RegSet());

    __ leave();
    __ mov(r0, zr); // return 0
    __ ret(lr);
    return start;
  }

  // Arguments:
  //   stub_id - is used to name the stub and identify all details of
  //             how to perform the copy.
  //
  //   nooverlap_target - identifes the (post push) entry for the
  //             corresponding disjoint copy routine which can be
  //             jumped to if the ranges do not actually overlap
  //
  //   entry - is assigned to the stub's post push entry point unless
  //           it is null
  //
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
  //   entry is set to the no-overlap entry point so it can be used by
  //   some other conjoint copy method
  //
  address generate_conjoint_copy(StubId stub_id, address nooverlap_target, address *entry) {
    Register s = c_rarg0, d = c_rarg1, count = c_rarg2;
    RegSet saved_regs = RegSet::of(s, d, count);
    int size;
    bool aligned;
    bool is_oop;
    bool dest_uninitialized;
    switch (stub_id) {
    case StubId::stubgen_jbyte_arraycopy_id:
      size = sizeof(jbyte);
      aligned = false;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_arrayof_jbyte_arraycopy_id:
      size = sizeof(jbyte);
      aligned = true;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_jshort_arraycopy_id:
      size = sizeof(jshort);
      aligned = false;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_arrayof_jshort_arraycopy_id:
      size = sizeof(jshort);
      aligned = true;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_jint_arraycopy_id:
      size = sizeof(jint);
      aligned = false;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_arrayof_jint_arraycopy_id:
      size = sizeof(jint);
      aligned = true;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_jlong_arraycopy_id:
      // since this is always aligned we can (should!) use the same
      // stub as for case StubId::stubgen_arrayof_jlong_disjoint_arraycopy
      ShouldNotReachHere();
      break;
    case StubId::stubgen_arrayof_jlong_arraycopy_id:
      size = sizeof(jlong);
      aligned = true;
      is_oop = false;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_oop_arraycopy_id:
      size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
      aligned = !UseCompressedOops;
      is_oop = true;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_arrayof_oop_arraycopy_id:
      size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
      aligned = !UseCompressedOops;
      is_oop = true;
      dest_uninitialized = false;
      break;
    case StubId::stubgen_oop_arraycopy_uninit_id:
      size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
      aligned = !UseCompressedOops;
      is_oop = true;
      dest_uninitialized = true;
      break;
    case StubId::stubgen_arrayof_oop_arraycopy_uninit_id:
      size = UseCompressedOops ? sizeof (jint) : sizeof (jlong);
      aligned = !UseCompressedOops;
      is_oop = true;
      dest_uninitialized = true;
      break;
    default:
      ShouldNotReachHere();
    }

    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    if (entry != nullptr) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    // use fwd copy when (d-s) above_equal (count*size)
    __ sub(rscratch1, d, s);
    __ cmp(rscratch1, count, Assembler::LSL, exact_log2(size));
    __ br(Assembler::HS, nooverlap_target);

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
      __ push(RegSet::of(d, count), sp);
    }
    {
      // UnsafeMemoryAccess page error: continue after unsafe access
      bool add_entry = !is_oop && (!aligned || sizeof(jlong) == size);
      UnsafeMemoryAccessMark umam(this, add_entry, true);
      copy_memory(decorators, is_oop ? T_OBJECT : T_BYTE, aligned, s, d, count, -size);
    }
    if (is_oop) {
      __ pop(RegSet::of(d, count), sp);
      if (VerifyOops)
        verify_oop_array(size, d, count, r16);
    }
    bs->arraycopy_epilogue(_masm, decorators, is_oop, d, count, rscratch1, RegSet());
    __ leave();
    __ mov(r0, zr); // return 0
    __ ret(lr);
    return start;
  }

  // Helper for generating a dynamic type check.
  // Smashes rscratch1, rscratch2.
  void generate_type_check(Register sub_klass,
                           Register super_check_offset,
                           Register super_klass,
                           Register temp1,
                           Register temp2,
                           Register result,
                           Label& L_success) {
    assert_different_registers(sub_klass, super_check_offset, super_klass);

    BLOCK_COMMENT("type_check:");

    Label L_miss;

    __ check_klass_subtype_fast_path(sub_klass, super_klass, noreg,        &L_success, &L_miss, nullptr,
                                     super_check_offset);
    __ check_klass_subtype_slow_path(sub_klass, super_klass, temp1, temp2, &L_success, nullptr);

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
  //    r0 ==  0  -  success
  //    r0 == -1^K - failure, where K is partial transfer count
  //
  address generate_checkcast_copy(StubId stub_id, address *entry) {
    bool dest_uninitialized;
    switch (stub_id) {
    case StubId::stubgen_checkcast_arraycopy_id:
      dest_uninitialized = false;
      break;
    case StubId::stubgen_checkcast_arraycopy_uninit_id:
      dest_uninitialized = true;
      break;
    default:
      ShouldNotReachHere();
    }

    Label L_load_element, L_store_element, L_do_card_marks, L_done, L_done_pop;

    // Input registers (after setup_arg_regs)
    const Register from        = c_rarg0;   // source array address
    const Register to          = c_rarg1;   // destination array address
    const Register count       = c_rarg2;   // elementscount
    const Register ckoff       = c_rarg3;   // super_check_offset
    const Register ckval       = c_rarg4;   // super_klass

    RegSet wb_pre_saved_regs = RegSet::range(c_rarg0, c_rarg4);
    RegSet wb_post_saved_regs = RegSet::of(count);

    // Registers used as temps (r19, r20, r21, r22 are save-on-entry)
    const Register copied_oop  = r22;       // actual oop copied
    const Register count_save  = r21;       // orig elementscount
    const Register start_to    = r20;       // destination array start address
    const Register r19_klass   = r19;       // oop._klass

    // Registers used as gc temps (r5, r6, r7 are save-on-call)
    const Register gct1 = r5, gct2 = r6, gct3 = r7;

    //---------------------------------------------------------------
    // Assembler stub will be used for this call to arraycopy
    // if the two arrays are subtypes of Object[] but the
    // destination array type is not equal to or a supertype
    // of the source type.  Each element must be separately
    // checked.

    assert_different_registers(from, to, count, ckoff, ckval, start_to,
                               copied_oop, r19_klass, count_save);

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef ASSERT
    // caller guarantees that the arrays really are different
    // otherwise, we would have to make conjoint checks
    { Label L;
      __ b(L);                  // conjoint check not yet implemented
      __ stop("checkcast_copy within a single array");
      __ bind(L);
    }
#endif //ASSERT

    // Caller of this entry point must set up the argument registers.
    if (entry != nullptr) {
      *entry = __ pc();
      BLOCK_COMMENT("Entry:");
    }

     // Empty array:  Nothing to do.
    __ cbz(count, L_done);
    __ push(RegSet::of(r19, r20, r21, r22), sp);

#ifdef ASSERT
    BLOCK_COMMENT("assert consistent ckoff/ckval");
    // The ckoff and ckval must be mutually consistent,
    // even though caller generates both.
    { Label L;
      int sco_offset = in_bytes(Klass::super_check_offset_offset());
      __ ldrw(start_to, Address(ckval, sco_offset));
      __ cmpw(ckoff, start_to);
      __ br(Assembler::EQ, L);
      __ stop("super_check_offset inconsistent");
      __ bind(L);
    }
#endif //ASSERT

    DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_CHECKCAST | ARRAYCOPY_DISJOINT;
    bool is_oop = true;
    int element_size = UseCompressedOops ? 4 : 8;
    if (dest_uninitialized) {
      decorators |= IS_DEST_UNINITIALIZED;
    }

    BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
    bs->arraycopy_prologue(_masm, decorators, is_oop, from, to, count, wb_pre_saved_regs);

    // save the original count
    __ mov(count_save, count);

    // Copy from low to high addresses
    __ mov(start_to, to);              // Save destination array start address
    __ b(L_load_element);

    // ======== begin loop ========
    // (Loop is rotated; its entry is L_load_element.)
    // Loop control:
    //   for (; count != 0; count--) {
    //     copied_oop = load_heap_oop(from++);
    //     ... generate_type_check ...;
    //     store_heap_oop(to++, copied_oop);
    //   }
    __ align(OptoLoopAlignment);

    __ BIND(L_store_element);
    bs->copy_store_at(_masm, decorators, T_OBJECT, element_size,
                      __ post(to, element_size), copied_oop, noreg,
                      gct1, gct2, gct3);
    __ sub(count, count, 1);
    __ cbz(count, L_do_card_marks);

    // ======== loop entry is here ========
    __ BIND(L_load_element);
    bs->copy_load_at(_masm, decorators, T_OBJECT, element_size,
                     copied_oop, noreg, __ post(from, element_size),
                     gct1);
    __ cbz(copied_oop, L_store_element);

    __ load_klass(r19_klass, copied_oop);// query the object klass

    BLOCK_COMMENT("type_check:");
    generate_type_check(/*sub_klass*/r19_klass,
                        /*super_check_offset*/ckoff,
                        /*super_klass*/ckval,
                        /*r_array_base*/gct1,
                        /*temp2*/gct2,
                        /*result*/r10, L_store_element);

    // Fall through on failure!

    // ======== end loop ========

    // It was a real error; we must depend on the caller to finish the job.
    // Register count = remaining oops, count_orig = total oops.
    // Emit GC store barriers for the oops we have copied and report
    // their number to the caller.

    __ subs(count, count_save, count);     // K = partially copied oop count
    __ eon(count, count, zr);              // report (-1^K) to caller
    __ br(Assembler::EQ, L_done_pop);

    __ BIND(L_do_card_marks);
    bs->arraycopy_epilogue(_masm, decorators, is_oop, start_to, count_save, rscratch1, wb_post_saved_regs);

    __ bind(L_done_pop);
    __ pop(RegSet::of(r19, r20, r21, r22), sp);
    inc_counter_np(SharedRuntime::_checkcast_array_copy_ctr);

    __ bind(L_done);
    __ mov(r0, count);
    __ leave();
    __ ret(lr);

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

    assert_different_registers(rscratch1, temp);

    //  if (src_pos + length > arrayOop(src)->length())  FAIL;
    __ ldrw(rscratch1, Address(src, arrayOopDesc::length_offset_in_bytes()));
    __ addw(temp, length, src_pos);
    __ cmpw(temp, rscratch1);
    __ br(Assembler::HI, L_failed);

    //  if (dst_pos + length > arrayOop(dst)->length())  FAIL;
    __ ldrw(rscratch1, Address(dst, arrayOopDesc::length_offset_in_bytes()));
    __ addw(temp, length, dst_pos);
    __ cmpw(temp, rscratch1);
    __ br(Assembler::HI, L_failed);

    // Have to clean up high 32 bits of 'src_pos' and 'dst_pos'.
    __ movw(src_pos, src_pos);
    __ movw(dst_pos, dst_pos);

    BLOCK_COMMENT("arraycopy_range_checks done");
  }

  // These stubs get called from some dumb test routine.
  // I'll write them properly when they're called from
  // something that's actually doing something.
  static void fake_arraycopy_stub(address src, address dst, int count) {
    assert(count == 0, "huh?");
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
  address generate_unsafe_copy(address byte_copy_entry,
                               address short_copy_entry,
                               address int_copy_entry,
                               address long_copy_entry) {
    StubId stub_id = StubId::stubgen_unsafe_arraycopy_id;

    Label L_long_aligned, L_int_aligned, L_short_aligned;
    Register s = c_rarg0, d = c_rarg1, count = c_rarg2;

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter(); // required for proper stackwalking of RuntimeStub frame

    // bump this on entry, not on exit:
    inc_counter_np(SharedRuntime::_unsafe_array_copy_ctr);

    __ orr(rscratch1, s, d);
    __ orr(rscratch1, rscratch1, count);

    __ andr(rscratch1, rscratch1, BytesPerLong-1);
    __ cbz(rscratch1, L_long_aligned);
    __ andr(rscratch1, rscratch1, BytesPerInt-1);
    __ cbz(rscratch1, L_int_aligned);
    __ tbz(rscratch1, 0, L_short_aligned);
    __ b(RuntimeAddress(byte_copy_entry));

    __ BIND(L_short_aligned);
    __ lsr(count, count, LogBytesPerShort);  // size => short_count
    __ b(RuntimeAddress(short_copy_entry));
    __ BIND(L_int_aligned);
    __ lsr(count, count, LogBytesPerInt);    // size => int_count
    __ b(RuntimeAddress(int_copy_entry));
    __ BIND(L_long_aligned);
    __ lsr(count, count, LogBytesPerLong);   // size => long_count
    __ b(RuntimeAddress(long_copy_entry));

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
  //    r0 ==  0  -  success
  //    r0 == -1^K - failure, where K is partial transfer count
  //
  address generate_generic_copy(address byte_copy_entry, address short_copy_entry,
                                address int_copy_entry, address oop_copy_entry,
                                address long_copy_entry, address checkcast_copy_entry) {
    StubId stub_id = StubId::stubgen_generic_arraycopy_id;

    Label L_failed, L_objArray;
    Label L_copy_bytes, L_copy_shorts, L_copy_ints, L_copy_longs;

    // Input registers
    const Register src        = c_rarg0;  // source array oop
    const Register src_pos    = c_rarg1;  // source position
    const Register dst        = c_rarg2;  // destination array oop
    const Register dst_pos    = c_rarg3;  // destination position
    const Register length     = c_rarg4;


    // Registers used as temps
    const Register dst_klass  = c_rarg5;

    __ align(CodeEntryAlignment);

    StubCodeMark mark(this, stub_id);

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

    //  if (src == nullptr) return -1;
    __ cbz(src, L_failed);

    //  if (src_pos < 0) return -1;
    __ tbnz(src_pos, 31, L_failed);  // i.e. sign bit set

    //  if (dst == nullptr) return -1;
    __ cbz(dst, L_failed);

    //  if (dst_pos < 0) return -1;
    __ tbnz(dst_pos, 31, L_failed);  // i.e. sign bit set

    // registers used as temp
    const Register scratch_length    = r16; // elements count to copy
    const Register scratch_src_klass = r17; // array klass
    const Register lh                = r15; // layout helper

    //  if (length < 0) return -1;
    __ movw(scratch_length, length);        // length (elements count, 32-bits value)
    __ tbnz(scratch_length, 31, L_failed);  // i.e. sign bit set

    __ load_klass(scratch_src_klass, src);
#ifdef ASSERT
    //  assert(src->klass() != nullptr);
    {
      BLOCK_COMMENT("assert klasses not null {");
      Label L1, L2;
      __ cbnz(scratch_src_klass, L2);   // it is broken if klass is null
      __ bind(L1);
      __ stop("broken null klass");
      __ bind(L2);
      __ load_klass(rscratch1, dst);
      __ cbz(rscratch1, L1);     // this would be broken also
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
    __ ldrw(lh, Address(scratch_src_klass, lh_offset));
    __ movw(rscratch1, objArray_lh);
    __ eorw(rscratch2, lh, rscratch1);
    __ cbzw(rscratch2, L_objArray);

    //  if (src->klass() != dst->klass()) return -1;
    __ load_klass(rscratch2, dst);
    __ eor(rscratch2, rscratch2, scratch_src_klass);
    __ cbnz(rscratch2, L_failed);

    //  if (!src->is_Array()) return -1;
    __ tbz(lh, 31, L_failed);  // i.e. (lh >= 0)

    // At this point, it is known to be a typeArray (array_tag 0x3).
#ifdef ASSERT
    {
      BLOCK_COMMENT("assert primitive array {");
      Label L;
      __ movw(rscratch2, Klass::_lh_array_tag_type_value << Klass::_lh_array_tag_shift);
      __ cmpw(lh, rscratch2);
      __ br(Assembler::GE, L);
      __ stop("must be a primitive array");
      __ bind(L);
      BLOCK_COMMENT("} assert primitive array done");
    }
#endif

    arraycopy_range_checks(src, src_pos, dst, dst_pos, scratch_length,
                           rscratch2, L_failed);

    // TypeArrayKlass
    //
    // src_addr = (src + array_header_in_bytes()) + (src_pos << log2elemsize);
    // dst_addr = (dst + array_header_in_bytes()) + (dst_pos << log2elemsize);
    //

    const Register rscratch1_offset = rscratch1;    // array offset
    const Register r15_elsize = lh; // element size

    __ ubfx(rscratch1_offset, lh, Klass::_lh_header_size_shift,
           exact_log2(Klass::_lh_header_size_mask+1));   // array_offset
    __ add(src, src, rscratch1_offset);           // src array offset
    __ add(dst, dst, rscratch1_offset);           // dst array offset
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
    __ tbnz(r15_elsize, 1, L_copy_ints);
    __ tbnz(r15_elsize, 0, L_copy_shorts);
    __ lea(from, Address(src, src_pos));// src_addr
    __ lea(to,   Address(dst, dst_pos));// dst_addr
    __ movw(count, scratch_length); // length
    __ b(RuntimeAddress(byte_copy_entry));

  __ BIND(L_copy_shorts);
    __ lea(from, Address(src, src_pos, Address::lsl(1)));// src_addr
    __ lea(to,   Address(dst, dst_pos, Address::lsl(1)));// dst_addr
    __ movw(count, scratch_length); // length
    __ b(RuntimeAddress(short_copy_entry));

  __ BIND(L_copy_ints);
    __ tbnz(r15_elsize, 0, L_copy_longs);
    __ lea(from, Address(src, src_pos, Address::lsl(2)));// src_addr
    __ lea(to,   Address(dst, dst_pos, Address::lsl(2)));// dst_addr
    __ movw(count, scratch_length); // length
    __ b(RuntimeAddress(int_copy_entry));

  __ BIND(L_copy_longs);
#ifdef ASSERT
    {
      BLOCK_COMMENT("assert long copy {");
      Label L;
      __ andw(lh, lh, Klass::_lh_log2_element_size_mask); // lh -> r15_elsize
      __ cmpw(r15_elsize, LogBytesPerLong);
      __ br(Assembler::EQ, L);
      __ stop("must be long copy, but elsize is wrong");
      __ bind(L);
      BLOCK_COMMENT("} assert long copy done");
    }
#endif
    __ lea(from, Address(src, src_pos, Address::lsl(3)));// src_addr
    __ lea(to,   Address(dst, dst_pos, Address::lsl(3)));// dst_addr
    __ movw(count, scratch_length); // length
    __ b(RuntimeAddress(long_copy_entry));

    // ObjArrayKlass
  __ BIND(L_objArray);
    // live at this point:  scratch_src_klass, scratch_length, src[_pos], dst[_pos]

    Label L_plain_copy, L_checkcast_copy;
    //  test array classes for subtyping
    __ load_klass(r15, dst);
    __ cmp(scratch_src_klass, r15); // usual case is exact equality
    __ br(Assembler::NE, L_checkcast_copy);

    // Identically typed arrays can be copied without element-wise checks.
    arraycopy_range_checks(src, src_pos, dst, dst_pos, scratch_length,
                           rscratch2, L_failed);

    __ lea(from, Address(src, src_pos, Address::lsl(LogBytesPerHeapOop)));
    __ add(from, from, arrayOopDesc::base_offset_in_bytes(T_OBJECT));
    __ lea(to, Address(dst, dst_pos, Address::lsl(LogBytesPerHeapOop)));
    __ add(to, to, arrayOopDesc::base_offset_in_bytes(T_OBJECT));
    __ movw(count, scratch_length); // length
  __ BIND(L_plain_copy);
    __ b(RuntimeAddress(oop_copy_entry));

  __ BIND(L_checkcast_copy);
    // live at this point:  scratch_src_klass, scratch_length, r15 (dst_klass)
    {
      // Before looking at dst.length, make sure dst is also an objArray.
      __ ldrw(rscratch1, Address(r15, lh_offset));
      __ movw(rscratch2, objArray_lh);
      __ eorw(rscratch1, rscratch1, rscratch2);
      __ cbnzw(rscratch1, L_failed);

      // It is safe to examine both src.length and dst.length.
      arraycopy_range_checks(src, src_pos, dst, dst_pos, scratch_length,
                             r15, L_failed);

      __ load_klass(dst_klass, dst); // reload

      // Marshal the base address arguments now, freeing registers.
      __ lea(from, Address(src, src_pos, Address::lsl(LogBytesPerHeapOop)));
      __ add(from, from, arrayOopDesc::base_offset_in_bytes(T_OBJECT));
      __ lea(to, Address(dst, dst_pos, Address::lsl(LogBytesPerHeapOop)));
      __ add(to, to, arrayOopDesc::base_offset_in_bytes(T_OBJECT));
      __ movw(count, length);           // length (reloaded)
      Register sco_temp = c_rarg3;      // this register is free now
      assert_different_registers(from, to, count, sco_temp,
                                 dst_klass, scratch_src_klass);
      // assert_clean_int(count, sco_temp);

      // Generate the type check.
      const int sco_offset = in_bytes(Klass::super_check_offset_offset());
      __ ldrw(sco_temp, Address(dst_klass, sco_offset));

      // Smashes rscratch1, rscratch2
      generate_type_check(scratch_src_klass, sco_temp, dst_klass, /*temps*/ noreg, noreg, noreg,
                          L_plain_copy);

      // Fetch destination element klass from the ObjArrayKlass header.
      int ek_offset = in_bytes(ObjArrayKlass::element_klass_offset());
      __ ldr(dst_klass, Address(dst_klass, ek_offset));
      __ ldrw(sco_temp, Address(dst_klass, sco_offset));

      // the checkcast_copy loop needs two extra arguments:
      assert(c_rarg3 == sco_temp, "#3 already in place");
      // Set up arguments for checkcast_copy_entry.
      __ mov(c_rarg4, dst_klass);  // dst.klass.element_klass
      __ b(RuntimeAddress(checkcast_copy_entry));
    }

  __ BIND(L_failed);
    __ mov(r0, -1);
    __ leave();   // required for proper stackwalking of RuntimeStub frame
    __ ret(lr);

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
  address generate_fill(StubId stub_id) {
    BasicType t;
    bool aligned;

    switch (stub_id) {
    case StubId::stubgen_jbyte_fill_id:
      t = T_BYTE;
      aligned = false;
      break;
    case StubId::stubgen_jshort_fill_id:
      t = T_SHORT;
      aligned = false;
      break;
    case StubId::stubgen_jint_fill_id:
      t = T_INT;
      aligned = false;
      break;
    case StubId::stubgen_arrayof_jbyte_fill_id:
      t = T_BYTE;
      aligned = true;
      break;
    case StubId::stubgen_arrayof_jshort_fill_id:
      t = T_SHORT;
      aligned = true;
      break;
    case StubId::stubgen_arrayof_jint_fill_id:
      t = T_INT;
      aligned = true;
      break;
    default:
      ShouldNotReachHere();
    };

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    BLOCK_COMMENT("Entry:");

    const Register to        = c_rarg0;  // source array address
    const Register value     = c_rarg1;  // value
    const Register count     = c_rarg2;  // elements count

    const Register bz_base = r10;        // base for block_zero routine
    const Register cnt_words = r11;      // temp register

    __ enter();

    Label L_fill_elements, L_exit1;

    int shift = -1;
    switch (t) {
      case T_BYTE:
        shift = 0;
        __ cmpw(count, 8 >> shift); // Short arrays (< 8 bytes) fill by element
        __ bfi(value, value, 8, 8);   // 8 bit -> 16 bit
        __ bfi(value, value, 16, 16); // 16 bit -> 32 bit
        __ br(Assembler::LO, L_fill_elements);
        break;
      case T_SHORT:
        shift = 1;
        __ cmpw(count, 8 >> shift); // Short arrays (< 8 bytes) fill by element
        __ bfi(value, value, 16, 16); // 16 bit -> 32 bit
        __ br(Assembler::LO, L_fill_elements);
        break;
      case T_INT:
        shift = 2;
        __ cmpw(count, 8 >> shift); // Short arrays (< 8 bytes) fill by element
        __ br(Assembler::LO, L_fill_elements);
        break;
      default: ShouldNotReachHere();
    }

    // Align source address at 8 bytes address boundary.
    Label L_skip_align1, L_skip_align2, L_skip_align4;
    if (!aligned) {
      switch (t) {
        case T_BYTE:
          // One byte misalignment happens only for byte arrays.
          __ tbz(to, 0, L_skip_align1);
          __ strb(value, Address(__ post(to, 1)));
          __ subw(count, count, 1);
          __ bind(L_skip_align1);
          // Fallthrough
        case T_SHORT:
          // Two bytes misalignment happens only for byte and short (char) arrays.
          __ tbz(to, 1, L_skip_align2);
          __ strh(value, Address(__ post(to, 2)));
          __ subw(count, count, 2 >> shift);
          __ bind(L_skip_align2);
          // Fallthrough
        case T_INT:
          // Align to 8 bytes, we know we are 4 byte aligned to start.
          __ tbz(to, 2, L_skip_align4);
          __ strw(value, Address(__ post(to, 4)));
          __ subw(count, count, 4 >> shift);
          __ bind(L_skip_align4);
          break;
        default: ShouldNotReachHere();
      }
    }

    //
    //  Fill large chunks
    //
    __ lsrw(cnt_words, count, 3 - shift); // number of words
    __ bfi(value, value, 32, 32);         // 32 bit -> 64 bit
    __ subw(count, count, cnt_words, Assembler::LSL, 3 - shift);
    if (UseBlockZeroing) {
      Label non_block_zeroing, rest;
      // If the fill value is zero we can use the fast zero_words().
      __ cbnz(value, non_block_zeroing);
      __ mov(bz_base, to);
      __ add(to, to, cnt_words, Assembler::LSL, LogBytesPerWord);
      address tpc = __ zero_words(bz_base, cnt_words);
      if (tpc == nullptr) {
        fatal("CodeCache is full at generate_fill");
      }
      __ b(rest);
      __ bind(non_block_zeroing);
      __ fill_words(to, cnt_words, value);
      __ bind(rest);
    } else {
      __ fill_words(to, cnt_words, value);
    }

    // Remaining count is less than 8 bytes. Fill it by a single store.
    // Note that the total length is no less than 8 bytes.
    if (t == T_BYTE || t == T_SHORT) {
      Label L_exit1;
      __ cbzw(count, L_exit1);
      __ add(to, to, count, Assembler::LSL, shift); // points to the end
      __ str(value, Address(to, -8));    // overwrite some elements
      __ bind(L_exit1);
      __ leave();
      __ ret(lr);
    }

    // Handle copies less than 8 bytes.
    Label L_fill_2, L_fill_4, L_exit2;
    __ bind(L_fill_elements);
    switch (t) {
      case T_BYTE:
        __ tbz(count, 0, L_fill_2);
        __ strb(value, Address(__ post(to, 1)));
        __ bind(L_fill_2);
        __ tbz(count, 1, L_fill_4);
        __ strh(value, Address(__ post(to, 2)));
        __ bind(L_fill_4);
        __ tbz(count, 2, L_exit2);
        __ strw(value, Address(to));
        break;
      case T_SHORT:
        __ tbz(count, 0, L_fill_4);
        __ strh(value, Address(__ post(to, 2)));
        __ bind(L_fill_4);
        __ tbz(count, 1, L_exit2);
        __ strw(value, Address(to));
        break;
      case T_INT:
        __ cbzw(count, L_exit2);
        __ strw(value, Address(to));
        break;
      default: ShouldNotReachHere();
    }
    __ bind(L_exit2);
    __ leave();
    __ ret(lr);
    return start;
  }

  address generate_unsafecopy_common_error_exit() {
    address start_pc = __ pc();
      __ leave();
      __ mov(r0, 0);
      __ ret(lr);
    return start_pc;
  }

  //
  //  Generate 'unsafe' set memory stub
  //  Though just as safe as the other stubs, it takes an unscaled
  //  size_t (# bytes) argument instead of an element count.
  //
  //  This fill operation is atomicity preserving: as long as the
  //  address supplied is sufficiently aligned, all writes of up to 64
  //  bits in size are single-copy atomic.
  //
  //  Input:
  //    c_rarg0   - destination array address
  //    c_rarg1   - byte count (size_t)
  //    c_rarg2   - byte value
  //
  address generate_unsafe_setmemory() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, StubId::stubgen_unsafe_setmemory_id);
    address start = __ pc();

    Register dest = c_rarg0, count = c_rarg1, value = c_rarg2;
    Label tail;

    UnsafeMemoryAccessMark umam(this, true, false);

    __ enter(); // required for proper stackwalking of RuntimeStub frame

    __ dup(v0, __ T16B, value);

    if (AvoidUnalignedAccesses) {
      __ cmp(count, (u1)16);
      __ br(__ LO, tail);

      __ mov(rscratch1, 16);
      __ andr(rscratch2, dest, 15);
      __ sub(rscratch1, rscratch1, rscratch2);  // Bytes needed to 16-align dest
      __ strq(v0, Address(dest));
      __ sub(count, count, rscratch1);
      __ add(dest, dest, rscratch1);
    }

    __ subs(count, count, (u1)64);
    __ br(__ LO, tail);
    {
      Label again;
      __ bind(again);
      __ stpq(v0, v0, Address(dest));
      __ stpq(v0, v0, Address(dest, 32));

      __ subs(count, count, 64);
      __ add(dest, dest, 64);
      __ br(__ HS, again);
    }

    __ bind(tail);
    // The count of bytes is off by 64, but we don't need to correct
    // it because we're only going to use the least-significant few
    // count bits from here on.
    // __ add(count, count, 64);

    {
      Label dont;
      __ tbz(count, exact_log2(32), dont);
      __ stpq(v0, v0, __ post(dest, 32));
      __ bind(dont);
    }
    {
      Label dont;
      __ tbz(count, exact_log2(16), dont);
      __ strq(v0, __ post(dest, 16));
      __ bind(dont);
    }
    {
      Label dont;
      __ tbz(count, exact_log2(8), dont);
      __ strd(v0, __ post(dest, 8));
      __ bind(dont);
    }

    Label finished;
    __ tst(count, 7);
    __ br(__ EQ, finished);

    {
      Label dont;
      __ tbz(count, exact_log2(4), dont);
      __ strs(v0, __ post(dest, 4));
      __ bind(dont);
    }
    {
      Label dont;
      __ tbz(count, exact_log2(2), dont);
      __ bfi(value, value, 8, 8);
      __ strh(value, __ post(dest, 2));
      __ bind(dont);
    }
    {
      Label dont;
      __ tbz(count, exact_log2(1), dont);
      __ strb(value, Address(dest));
      __ bind(dont);
    }

    __ bind(finished);
    __ leave();
    __ ret(lr);

    return start;
  }

  address generate_data_cache_writeback() {
    const Register line        = c_rarg0;  // address of line to write back

    __ align(CodeEntryAlignment);

    StubId stub_id = StubId::stubgen_data_cache_writeback_id;
    StubCodeMark mark(this, stub_id);

    address start = __ pc();
    __ enter();
    __ cache_wb(Address(line, 0));
    __ leave();
    __ ret(lr);

    return start;
  }

  address generate_data_cache_writeback_sync() {
    const Register is_pre     = c_rarg0;  // pre or post sync

    __ align(CodeEntryAlignment);

    StubId stub_id = StubId::stubgen_data_cache_writeback_sync_id;
    StubCodeMark mark(this, stub_id);

    // pre wbsync is a no-op
    // post wbsync translates to an sfence

    Label skip;
    address start = __ pc();
    __ enter();
    __ cbnz(is_pre, skip);
    __ cache_wbsync(false);
    __ bind(skip);
    __ leave();
    __ ret(lr);

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

    // generate the common exit first so later stubs can rely on it if
    // they want an UnsafeMemoryAccess exit non-local to the stub
    StubRoutines::_unsafecopy_common_exit = generate_unsafecopy_common_error_exit();
    // register the stub as the default exit with class UnsafeMemoryAccess
    UnsafeMemoryAccess::set_common_exit_stub_pc(StubRoutines::_unsafecopy_common_exit);

    generate_copy_longs(StubId::stubgen_copy_byte_f_id, IN_HEAP | IS_ARRAY, copy_f, r0, r1, r15);
    generate_copy_longs(StubId::stubgen_copy_byte_b_id, IN_HEAP | IS_ARRAY, copy_b, r0, r1, r15);

    generate_copy_longs(StubId::stubgen_copy_oop_f_id, IN_HEAP | IS_ARRAY, copy_obj_f, r0, r1, r15);
    generate_copy_longs(StubId::stubgen_copy_oop_b_id, IN_HEAP | IS_ARRAY, copy_obj_b, r0, r1, r15);

    generate_copy_longs(StubId::stubgen_copy_oop_uninit_f_id, IN_HEAP | IS_ARRAY | IS_DEST_UNINITIALIZED, copy_obj_uninit_f, r0, r1, r15);
    generate_copy_longs(StubId::stubgen_copy_oop_uninit_b_id, IN_HEAP | IS_ARRAY | IS_DEST_UNINITIALIZED, copy_obj_uninit_b, r0, r1, r15);

    StubRoutines::aarch64::_zero_blocks = generate_zero_blocks();

    //*** jbyte
    // Always need aligned and unaligned versions
    StubRoutines::_jbyte_disjoint_arraycopy         = generate_disjoint_copy(StubId::stubgen_jbyte_disjoint_arraycopy_id, &entry);
    StubRoutines::_jbyte_arraycopy                  = generate_conjoint_copy(StubId::stubgen_jbyte_arraycopy_id, entry, &entry_jbyte_arraycopy);
    StubRoutines::_arrayof_jbyte_disjoint_arraycopy = generate_disjoint_copy(StubId::stubgen_arrayof_jbyte_disjoint_arraycopy_id, &entry);
    StubRoutines::_arrayof_jbyte_arraycopy          = generate_conjoint_copy(StubId::stubgen_arrayof_jbyte_arraycopy_id, entry, nullptr);

    //*** jshort
    // Always need aligned and unaligned versions
    StubRoutines::_jshort_disjoint_arraycopy         = generate_disjoint_copy(StubId::stubgen_jshort_disjoint_arraycopy_id, &entry);
    StubRoutines::_jshort_arraycopy                  = generate_conjoint_copy(StubId::stubgen_jshort_arraycopy_id, entry, &entry_jshort_arraycopy);
    StubRoutines::_arrayof_jshort_disjoint_arraycopy = generate_disjoint_copy(StubId::stubgen_arrayof_jshort_disjoint_arraycopy_id, &entry);
    StubRoutines::_arrayof_jshort_arraycopy          = generate_conjoint_copy(StubId::stubgen_arrayof_jshort_arraycopy_id, entry, nullptr);

    //*** jint
    // Aligned versions
    StubRoutines::_arrayof_jint_disjoint_arraycopy = generate_disjoint_copy(StubId::stubgen_arrayof_jint_disjoint_arraycopy_id, &entry);
    StubRoutines::_arrayof_jint_arraycopy          = generate_conjoint_copy(StubId::stubgen_arrayof_jint_arraycopy_id, entry, &entry_jint_arraycopy);
    // In 64 bit we need both aligned and unaligned versions of jint arraycopy.
    // entry_jint_arraycopy always points to the unaligned version
    StubRoutines::_jint_disjoint_arraycopy         = generate_disjoint_copy(StubId::stubgen_jint_disjoint_arraycopy_id, &entry);
    StubRoutines::_jint_arraycopy                  = generate_conjoint_copy(StubId::stubgen_jint_arraycopy_id, entry, &entry_jint_arraycopy);

    //*** jlong
    // It is always aligned
    StubRoutines::_arrayof_jlong_disjoint_arraycopy = generate_disjoint_copy(StubId::stubgen_arrayof_jlong_disjoint_arraycopy_id, &entry);
    StubRoutines::_arrayof_jlong_arraycopy          = generate_conjoint_copy(StubId::stubgen_arrayof_jlong_arraycopy_id, entry, &entry_jlong_arraycopy);
    StubRoutines::_jlong_disjoint_arraycopy         = StubRoutines::_arrayof_jlong_disjoint_arraycopy;
    StubRoutines::_jlong_arraycopy                  = StubRoutines::_arrayof_jlong_arraycopy;

    //*** oops
    {
      // With compressed oops we need unaligned versions; notice that
      // we overwrite entry_oop_arraycopy.
      bool aligned = !UseCompressedOops;

      StubRoutines::_arrayof_oop_disjoint_arraycopy
        = generate_disjoint_copy(StubId::stubgen_arrayof_oop_disjoint_arraycopy_id, &entry);
      StubRoutines::_arrayof_oop_arraycopy
        = generate_conjoint_copy(StubId::stubgen_arrayof_oop_arraycopy_id, entry, &entry_oop_arraycopy);
      // Aligned versions without pre-barriers
      StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit
        = generate_disjoint_copy(StubId::stubgen_arrayof_oop_disjoint_arraycopy_uninit_id, &entry);
      StubRoutines::_arrayof_oop_arraycopy_uninit
        = generate_conjoint_copy(StubId::stubgen_arrayof_oop_arraycopy_uninit_id, entry, nullptr);
    }

    StubRoutines::_oop_disjoint_arraycopy            = StubRoutines::_arrayof_oop_disjoint_arraycopy;
    StubRoutines::_oop_arraycopy                     = StubRoutines::_arrayof_oop_arraycopy;
    StubRoutines::_oop_disjoint_arraycopy_uninit     = StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit;
    StubRoutines::_oop_arraycopy_uninit              = StubRoutines::_arrayof_oop_arraycopy_uninit;

    StubRoutines::_checkcast_arraycopy        = generate_checkcast_copy(StubId::stubgen_checkcast_arraycopy_id, &entry_checkcast_arraycopy);
    StubRoutines::_checkcast_arraycopy_uninit = generate_checkcast_copy(StubId::stubgen_checkcast_arraycopy_uninit_id, nullptr);

    StubRoutines::_unsafe_arraycopy    = generate_unsafe_copy(entry_jbyte_arraycopy,
                                                              entry_jshort_arraycopy,
                                                              entry_jint_arraycopy,
                                                              entry_jlong_arraycopy);

    StubRoutines::_generic_arraycopy   = generate_generic_copy(entry_jbyte_arraycopy,
                                                               entry_jshort_arraycopy,
                                                               entry_jint_arraycopy,
                                                               entry_oop_arraycopy,
                                                               entry_jlong_arraycopy,
                                                               entry_checkcast_arraycopy);

    StubRoutines::_jbyte_fill = generate_fill(StubId::stubgen_jbyte_fill_id);
    StubRoutines::_jshort_fill = generate_fill(StubId::stubgen_jshort_fill_id);
    StubRoutines::_jint_fill = generate_fill(StubId::stubgen_jint_fill_id);
    StubRoutines::_arrayof_jbyte_fill = generate_fill(StubId::stubgen_arrayof_jbyte_fill_id);
    StubRoutines::_arrayof_jshort_fill = generate_fill(StubId::stubgen_arrayof_jshort_fill_id);
    StubRoutines::_arrayof_jint_fill = generate_fill(StubId::stubgen_arrayof_jint_fill_id);
  }

  void generate_math_stubs() { Unimplemented(); }

  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - source byte array address
  //   c_rarg1   - destination byte array address
  //   c_rarg2   - K (key) in little endian int array
  //
  address generate_aescrypt_encryptBlock() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_aescrypt_encryptBlock_id;
    StubCodeMark mark(this, stub_id);

    const Register from        = c_rarg0;  // source array address
    const Register to          = c_rarg1;  // destination array address
    const Register key         = c_rarg2;  // key array address
    const Register keylen      = rscratch1;

    address start = __ pc();
    __ enter();

    __ ldrw(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    __ aesenc_loadkeys(key, keylen);
    __ aesecb_encrypt(from, to, keylen);

    __ mov(r0, 0);

    __ leave();
    __ ret(lr);

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
    assert(UseAES, "need AES cryptographic extension support");
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_aescrypt_decryptBlock_id;
    StubCodeMark mark(this, stub_id);
    Label L_doLast;

    const Register from        = c_rarg0;  // source array address
    const Register to          = c_rarg1;  // destination array address
    const Register key         = c_rarg2;  // key array address
    const Register keylen      = rscratch1;

    address start = __ pc();
    __ enter(); // required for proper stackwalking of RuntimeStub frame

    __ ldrw(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    __ aesecb_decrypt(from, to, key, keylen);

    __ mov(r0, 0);

    __ leave();
    __ ret(lr);

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
  //   x0        - input length
  //
  address generate_cipherBlockChaining_encryptAESCrypt() {
    assert(UseAES, "need AES cryptographic extension support");
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_cipherBlockChaining_encryptAESCrypt_id;
    StubCodeMark mark(this, stub_id);

    Label L_loadkeys_44, L_loadkeys_52, L_aes_loop, L_rounds_44, L_rounds_52;

    const Register from        = c_rarg0;  // source array address
    const Register to          = c_rarg1;  // destination array address
    const Register key         = c_rarg2;  // key array address
    const Register rvec        = c_rarg3;  // r byte array initialized from initvector array address
                                           // and left with the results of the last encryption block
    const Register len_reg     = c_rarg4;  // src len (must be multiple of blocksize 16)
    const Register keylen      = rscratch1;

    address start = __ pc();

      __ enter();

      __ movw(rscratch2, len_reg);

      __ ldrw(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

      __ ld1(v0, __ T16B, rvec);

      __ cmpw(keylen, 52);
      __ br(Assembler::CC, L_loadkeys_44);
      __ br(Assembler::EQ, L_loadkeys_52);

      __ ld1(v17, v18, __ T16B, __ post(key, 32));
      __ rev32(v17, __ T16B, v17);
      __ rev32(v18, __ T16B, v18);
    __ BIND(L_loadkeys_52);
      __ ld1(v19, v20, __ T16B, __ post(key, 32));
      __ rev32(v19, __ T16B, v19);
      __ rev32(v20, __ T16B, v20);
    __ BIND(L_loadkeys_44);
      __ ld1(v21, v22, v23, v24, __ T16B, __ post(key, 64));
      __ rev32(v21, __ T16B, v21);
      __ rev32(v22, __ T16B, v22);
      __ rev32(v23, __ T16B, v23);
      __ rev32(v24, __ T16B, v24);
      __ ld1(v25, v26, v27, v28, __ T16B, __ post(key, 64));
      __ rev32(v25, __ T16B, v25);
      __ rev32(v26, __ T16B, v26);
      __ rev32(v27, __ T16B, v27);
      __ rev32(v28, __ T16B, v28);
      __ ld1(v29, v30, v31, __ T16B, key);
      __ rev32(v29, __ T16B, v29);
      __ rev32(v30, __ T16B, v30);
      __ rev32(v31, __ T16B, v31);

    __ BIND(L_aes_loop);
      __ ld1(v1, __ T16B, __ post(from, 16));
      __ eor(v0, __ T16B, v0, v1);

      __ br(Assembler::CC, L_rounds_44);
      __ br(Assembler::EQ, L_rounds_52);

      __ aese(v0, v17); __ aesmc(v0, v0);
      __ aese(v0, v18); __ aesmc(v0, v0);
    __ BIND(L_rounds_52);
      __ aese(v0, v19); __ aesmc(v0, v0);
      __ aese(v0, v20); __ aesmc(v0, v0);
    __ BIND(L_rounds_44);
      __ aese(v0, v21); __ aesmc(v0, v0);
      __ aese(v0, v22); __ aesmc(v0, v0);
      __ aese(v0, v23); __ aesmc(v0, v0);
      __ aese(v0, v24); __ aesmc(v0, v0);
      __ aese(v0, v25); __ aesmc(v0, v0);
      __ aese(v0, v26); __ aesmc(v0, v0);
      __ aese(v0, v27); __ aesmc(v0, v0);
      __ aese(v0, v28); __ aesmc(v0, v0);
      __ aese(v0, v29); __ aesmc(v0, v0);
      __ aese(v0, v30);
      __ eor(v0, __ T16B, v0, v31);

      __ st1(v0, __ T16B, __ post(to, 16));

      __ subw(len_reg, len_reg, 16);
      __ cbnzw(len_reg, L_aes_loop);

      __ st1(v0, __ T16B, rvec);

      __ mov(r0, rscratch2);

      __ leave();
      __ ret(lr);

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
  //   r0        - input length
  //
  address generate_cipherBlockChaining_decryptAESCrypt() {
    assert(UseAES, "need AES cryptographic extension support");
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_cipherBlockChaining_decryptAESCrypt_id;
    StubCodeMark mark(this, stub_id);

    Label L_loadkeys_44, L_loadkeys_52, L_aes_loop, L_rounds_44, L_rounds_52;

    const Register from        = c_rarg0;  // source array address
    const Register to          = c_rarg1;  // destination array address
    const Register key         = c_rarg2;  // key array address
    const Register rvec        = c_rarg3;  // r byte array initialized from initvector array address
                                           // and left with the results of the last encryption block
    const Register len_reg     = c_rarg4;  // src len (must be multiple of blocksize 16)
    const Register keylen      = rscratch1;

    address start = __ pc();

      __ enter();

      __ movw(rscratch2, len_reg);

      __ ldrw(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

      __ ld1(v2, __ T16B, rvec);

      __ ld1(v31, __ T16B, __ post(key, 16));
      __ rev32(v31, __ T16B, v31);

      __ cmpw(keylen, 52);
      __ br(Assembler::CC, L_loadkeys_44);
      __ br(Assembler::EQ, L_loadkeys_52);

      __ ld1(v17, v18, __ T16B, __ post(key, 32));
      __ rev32(v17, __ T16B, v17);
      __ rev32(v18, __ T16B, v18);
    __ BIND(L_loadkeys_52);
      __ ld1(v19, v20, __ T16B, __ post(key, 32));
      __ rev32(v19, __ T16B, v19);
      __ rev32(v20, __ T16B, v20);
    __ BIND(L_loadkeys_44);
      __ ld1(v21, v22, v23, v24, __ T16B, __ post(key, 64));
      __ rev32(v21, __ T16B, v21);
      __ rev32(v22, __ T16B, v22);
      __ rev32(v23, __ T16B, v23);
      __ rev32(v24, __ T16B, v24);
      __ ld1(v25, v26, v27, v28, __ T16B, __ post(key, 64));
      __ rev32(v25, __ T16B, v25);
      __ rev32(v26, __ T16B, v26);
      __ rev32(v27, __ T16B, v27);
      __ rev32(v28, __ T16B, v28);
      __ ld1(v29, v30, __ T16B, key);
      __ rev32(v29, __ T16B, v29);
      __ rev32(v30, __ T16B, v30);

    __ BIND(L_aes_loop);
      __ ld1(v0, __ T16B, __ post(from, 16));
      __ orr(v1, __ T16B, v0, v0);

      __ br(Assembler::CC, L_rounds_44);
      __ br(Assembler::EQ, L_rounds_52);

      __ aesd(v0, v17); __ aesimc(v0, v0);
      __ aesd(v0, v18); __ aesimc(v0, v0);
    __ BIND(L_rounds_52);
      __ aesd(v0, v19); __ aesimc(v0, v0);
      __ aesd(v0, v20); __ aesimc(v0, v0);
    __ BIND(L_rounds_44);
      __ aesd(v0, v21); __ aesimc(v0, v0);
      __ aesd(v0, v22); __ aesimc(v0, v0);
      __ aesd(v0, v23); __ aesimc(v0, v0);
      __ aesd(v0, v24); __ aesimc(v0, v0);
      __ aesd(v0, v25); __ aesimc(v0, v0);
      __ aesd(v0, v26); __ aesimc(v0, v0);
      __ aesd(v0, v27); __ aesimc(v0, v0);
      __ aesd(v0, v28); __ aesimc(v0, v0);
      __ aesd(v0, v29); __ aesimc(v0, v0);
      __ aesd(v0, v30);
      __ eor(v0, __ T16B, v0, v31);
      __ eor(v0, __ T16B, v0, v2);

      __ st1(v0, __ T16B, __ post(to, 16));
      __ orr(v2, __ T16B, v1, v1);

      __ subw(len_reg, len_reg, 16);
      __ cbnzw(len_reg, L_aes_loop);

      __ st1(v2, __ T16B, rvec);

      __ mov(r0, rscratch2);

      __ leave();
      __ ret(lr);

    return start;
  }

  // Big-endian 128-bit + 64-bit -> 128-bit addition.
  // Inputs: 128-bits. in is preserved.
  // The least-significant 64-bit word is in the upper dword of each vector.
  // inc (the 64-bit increment) is preserved. Its lower dword must be zero.
  // Output: result
  void be_add_128_64(FloatRegister result, FloatRegister in,
                     FloatRegister inc, FloatRegister tmp) {
    assert_different_registers(result, tmp, inc);

    __ addv(result, __ T2D, in, inc);      // Add inc to the least-significant dword of
                                           // input
    __ cm(__ HI, tmp, __ T2D, inc, result);// Check for result overflowing
    __ ext(tmp, __ T16B, tmp, tmp, 0x08);  // Swap LSD of comparison result to MSD and
                                           // MSD == 0 (must be!) to LSD
    __ subv(result, __ T2D, result, tmp);  // Subtract -1 from MSD if there was an overflow
  }

  // CTR AES crypt.
  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - source byte array address
  //   c_rarg1   - destination byte array address
  //   c_rarg2   - K (key) in little endian int array
  //   c_rarg3   - counter vector byte array address
  //   c_rarg4   - input length
  //   c_rarg5   - saved encryptedCounter start
  //   c_rarg6   - saved used length
  //
  // Output:
  //   r0       - input length
  //
  address generate_counterMode_AESCrypt() {
    const Register in = c_rarg0;
    const Register out = c_rarg1;
    const Register key = c_rarg2;
    const Register counter = c_rarg3;
    const Register saved_len = c_rarg4, len = r10;
    const Register saved_encrypted_ctr = c_rarg5;
    const Register used_ptr = c_rarg6, used = r12;

    const Register offset = r7;
    const Register keylen = r11;

    const unsigned char block_size = 16;
    const int bulk_width = 4;
    // NB: bulk_width can be 4 or 8. 8 gives slightly faster
    // performance with larger data sizes, but it also means that the
    // fast path isn't used until you have at least 8 blocks, and up
    // to 127 bytes of data will be executed on the slow path. For
    // that reason, and also so as not to blow away too much icache, 4
    // blocks seems like a sensible compromise.

    // Algorithm:
    //
    //    if (len == 0) {
    //        goto DONE;
    //    }
    //    int result = len;
    //    do {
    //        if (used >= blockSize) {
    //            if (len >= bulk_width * blockSize) {
    //                CTR_large_block();
    //                if (len == 0)
    //                    goto DONE;
    //            }
    //            for (;;) {
    //                16ByteVector v0 = counter;
    //                embeddedCipher.encryptBlock(v0, 0, encryptedCounter, 0);
    //                used = 0;
    //                if (len < blockSize)
    //                    break;    /* goto NEXT */
    //                16ByteVector v1 = load16Bytes(in, offset);
    //                v1 = v1 ^ encryptedCounter;
    //                store16Bytes(out, offset);
    //                used = blockSize;
    //                offset += blockSize;
    //                len -= blockSize;
    //                if (len == 0)
    //                    goto DONE;
    //            }
    //        }
    //      NEXT:
    //        out[outOff++] = (byte)(in[inOff++] ^ encryptedCounter[used++]);
    //        len--;
    //    } while (len != 0);
    //  DONE:
    //    return result;
    //
    // CTR_large_block()
    //    Wide bulk encryption of whole blocks.

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_counterMode_AESCrypt_id;
    StubCodeMark mark(this, stub_id);
    const address start = __ pc();
    __ enter();

    Label DONE, CTR_large_block, large_block_return;
    __ ldrw(used, Address(used_ptr));
    __ cbzw(saved_len, DONE);

    __ mov(len, saved_len);
    __ mov(offset, 0);

    // Compute #rounds for AES based on the length of the key array
    __ ldrw(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    __ aesenc_loadkeys(key, keylen);

    {
      Label L_CTR_loop, NEXT;

      __ bind(L_CTR_loop);

      __ cmp(used, block_size);
      __ br(__ LO, NEXT);

      // Maybe we have a lot of data
      __ subsw(rscratch1, len, bulk_width * block_size);
      __ br(__ HS, CTR_large_block);
      __ BIND(large_block_return);
      __ cbzw(len, DONE);

      // Setup the counter
      __ movi(v4, __ T4S, 0);
      __ movi(v5, __ T4S, 1);
      __ ins(v4, __ S, v5, 2, 2); // v4 contains { 0, 1 }

      // 128-bit big-endian increment
      __ ld1(v0, __ T16B, counter);
      __ rev64(v16, __ T16B, v0);
      be_add_128_64(v16, v16, v4, /*tmp*/v5);
      __ rev64(v16, __ T16B, v16);
      __ st1(v16, __ T16B, counter);
      // Previous counter value is in v0
      // v4 contains { 0, 1 }

      {
        // We have fewer than bulk_width blocks of data left. Encrypt
        // them one by one until there is less than a full block
        // remaining, being careful to save both the encrypted counter
        // and the counter.

        Label inner_loop;
        __ bind(inner_loop);
        // Counter to encrypt is in v0
        __ aesecb_encrypt(noreg, noreg, keylen);
        __ st1(v0, __ T16B, saved_encrypted_ctr);

        // Do we have a remaining full block?

        __ mov(used, 0);
        __ cmp(len, block_size);
        __ br(__ LO, NEXT);

        // Yes, we have a full block
        __ ldrq(v1, Address(in, offset));
        __ eor(v1, __ T16B, v1, v0);
        __ strq(v1, Address(out, offset));
        __ mov(used, block_size);
        __ add(offset, offset, block_size);

        __ subw(len, len, block_size);
        __ cbzw(len, DONE);

        // Increment the counter, store it back
        __ orr(v0, __ T16B, v16, v16);
        __ rev64(v16, __ T16B, v16);
        be_add_128_64(v16, v16, v4, /*tmp*/v5);
        __ rev64(v16, __ T16B, v16);
        __ st1(v16, __ T16B, counter); // Save the incremented counter back

        __ b(inner_loop);
      }

      __ BIND(NEXT);

      // Encrypt a single byte, and loop.
      // We expect this to be a rare event.
      __ ldrb(rscratch1, Address(in, offset));
      __ ldrb(rscratch2, Address(saved_encrypted_ctr, used));
      __ eor(rscratch1, rscratch1, rscratch2);
      __ strb(rscratch1, Address(out, offset));
      __ add(offset, offset, 1);
      __ add(used, used, 1);
      __ subw(len, len,1);
      __ cbnzw(len, L_CTR_loop);
    }

    __ bind(DONE);
    __ strw(used, Address(used_ptr));
    __ mov(r0, saved_len);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(lr);

    // Bulk encryption

    __ BIND (CTR_large_block);
    assert(bulk_width == 4 || bulk_width == 8, "must be");

    if (bulk_width == 8) {
      __ sub(sp, sp, 4 * 16);
      __ st1(v12, v13, v14, v15, __ T16B, Address(sp));
    }
    __ sub(sp, sp, 4 * 16);
    __ st1(v8, v9, v10, v11, __ T16B, Address(sp));
    RegSet saved_regs = (RegSet::of(in, out, offset)
                         + RegSet::of(saved_encrypted_ctr, used_ptr, len));
    __ push(saved_regs, sp);
    __ andr(len, len, -16 * bulk_width);  // 8/4 encryptions, 16 bytes per encryption
    __ add(in, in, offset);
    __ add(out, out, offset);

    // Keys should already be loaded into the correct registers

    __ ld1(v0, __ T16B, counter); // v0 contains the first counter
    __ rev64(v16, __ T16B, v0); // v16 contains byte-reversed counter

    // AES/CTR loop
    {
      Label L_CTR_loop;
      __ BIND(L_CTR_loop);

      // Setup the counters
      __ movi(v8, __ T4S, 0);
      __ movi(v9, __ T4S, 1);
      __ ins(v8, __ S, v9, 2, 2); // v8 contains { 0, 1 }

      for (int i = 0; i < bulk_width; i++) {
        FloatRegister v0_ofs = as_FloatRegister(v0->encoding() + i);
        __ rev64(v0_ofs, __ T16B, v16);
        be_add_128_64(v16, v16, v8, /*tmp*/v9);
      }

      __ ld1(v8, v9, v10, v11, __ T16B, __ post(in, 4 * 16));

      // Encrypt the counters
      __ aesecb_encrypt(noreg, noreg, keylen, v0, bulk_width);

      if (bulk_width == 8) {
        __ ld1(v12, v13, v14, v15, __ T16B, __ post(in, 4 * 16));
      }

      // XOR the encrypted counters with the inputs
      for (int i = 0; i < bulk_width; i++) {
        FloatRegister v0_ofs = as_FloatRegister(v0->encoding() + i);
        FloatRegister v8_ofs = as_FloatRegister(v8->encoding() + i);
        __ eor(v0_ofs, __ T16B, v0_ofs, v8_ofs);
      }

      // Write the encrypted data
      __ st1(v0, v1, v2, v3, __ T16B, __ post(out, 4 * 16));
      if (bulk_width == 8) {
        __ st1(v4, v5, v6, v7, __ T16B, __ post(out, 4 * 16));
      }

      __ subw(len, len, 16 * bulk_width);
      __ cbnzw(len, L_CTR_loop);
    }

    // Save the counter back where it goes
    __ rev64(v16, __ T16B, v16);
    __ st1(v16, __ T16B, counter);

    __ pop(saved_regs, sp);

    __ ld1(v8, v9, v10, v11, __ T16B, __ post(sp, 4 * 16));
    if (bulk_width == 8) {
      __ ld1(v12, v13, v14, v15, __ T16B, __ post(sp, 4 * 16));
    }

    __ andr(rscratch1, len, -16 * bulk_width);
    __ sub(len, len, rscratch1);
    __ add(offset, offset, rscratch1);
    __ mov(used, 16);
    __ strw(used, Address(used_ptr));
    __ b(large_block_return);

    return start;
  }

  // Vector AES Galois Counter Mode implementation. Parameters:
  //
  // in = c_rarg0
  // len = c_rarg1
  // ct = c_rarg2 - ciphertext that ghash will read (in for encrypt, out for decrypt)
  // out = c_rarg3
  // key = c_rarg4
  // state = c_rarg5 - GHASH.state
  // subkeyHtbl = c_rarg6 - powers of H
  // counter = c_rarg7 - 16 bytes of CTR
  // return - number of processed bytes
  address generate_galoisCounterMode_AESCrypt() {
    address ghash_polynomial = __ pc();
    __ emit_int64(0x87);  // The low-order bits of the field
                          // polynomial (i.e. p = z^7+z^2+z+1)
                          // repeated in the low and high parts of a
                          // 128-bit vector
    __ emit_int64(0x87);

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_galoisCounterMode_AESCrypt_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register in = c_rarg0;
    const Register len = c_rarg1;
    const Register ct = c_rarg2;
    const Register out = c_rarg3;
    // and updated with the incremented counter in the end

    const Register key = c_rarg4;
    const Register state = c_rarg5;

    const Register subkeyHtbl = c_rarg6;

    const Register counter = c_rarg7;

    const Register keylen = r10;
    // Save state before entering routine
    __ sub(sp, sp, 4 * 16);
    __ st1(v12, v13, v14, v15, __ T16B, Address(sp));
    __ sub(sp, sp, 4 * 16);
    __ st1(v8, v9, v10, v11, __ T16B, Address(sp));

    // __ andr(len, len, -512);
    __ andr(len, len, -16 * 8);  // 8 encryptions, 16 bytes per encryption
    __ str(len, __ pre(sp, -2 * wordSize));

    Label DONE;
    __ cbz(len, DONE);

    // Compute #rounds for AES based on the length of the key array
    __ ldrw(keylen, Address(key, arrayOopDesc::length_offset_in_bytes() - arrayOopDesc::base_offset_in_bytes(T_INT)));

    __ aesenc_loadkeys(key, keylen);
    __ ld1(v0, __ T16B, counter); // v0 contains the first counter
    __ rev32(v16, __ T16B, v0); // v16 contains byte-reversed counter

    // AES/CTR loop
    {
      Label L_CTR_loop;
      __ BIND(L_CTR_loop);

      // Setup the counters
      __ movi(v8, __ T4S, 0);
      __ movi(v9, __ T4S, 1);
      __ ins(v8, __ S, v9, 3, 3); // v8 contains { 0, 0, 0, 1 }

      assert(v0->encoding() < v8->encoding(), "");
      for (int i = v0->encoding(); i < v8->encoding(); i++) {
        FloatRegister f = as_FloatRegister(i);
        __ rev32(f, __ T16B, v16);
        __ addv(v16, __ T4S, v16, v8);
      }

      __ ld1(v8, v9, v10, v11, __ T16B, __ post(in, 4 * 16));

      // Encrypt the counters
      __ aesecb_encrypt(noreg, noreg, keylen, v0, /*unrolls*/8);

      __ ld1(v12, v13, v14, v15, __ T16B, __ post(in, 4 * 16));

      // XOR the encrypted counters with the inputs
      for (int i = 0; i < 8; i++) {
        FloatRegister v0_ofs = as_FloatRegister(v0->encoding() + i);
        FloatRegister v8_ofs = as_FloatRegister(v8->encoding() + i);
        __ eor(v0_ofs, __ T16B, v0_ofs, v8_ofs);
      }
      __ st1(v0, v1, v2, v3, __ T16B, __ post(out, 4 * 16));
      __ st1(v4, v5, v6, v7, __ T16B, __ post(out, 4 * 16));

      __ subw(len, len, 16 * 8);
      __ cbnzw(len, L_CTR_loop);
    }

    __ rev32(v16, __ T16B, v16);
    __ st1(v16, __ T16B, counter);

    __ ldr(len, Address(sp));
    __ lsr(len, len, exact_log2(16));  // We want the count of blocks

    // GHASH/CTR loop
    __ ghash_processBlocks_wide(ghash_polynomial, state, subkeyHtbl, ct,
                                len, /*unrolls*/4);

#ifdef ASSERT
    { Label L;
      __ cmp(len, (unsigned char)0);
      __ br(Assembler::EQ, L);
      __ stop("stubGenerator: abort");
      __ bind(L);
  }
#endif

  __ bind(DONE);
    // Return the number of bytes processed
    __ ldr(r0, __ post(sp, 2 * wordSize));

    __ ld1(v8, v9, v10, v11, __ T16B, __ post(sp, 4 * 16));
    __ ld1(v12, v13, v14, v15, __ T16B, __ post(sp, 4 * 16));

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(lr);
     return start;
  }

  class Cached64Bytes {
  private:
    MacroAssembler *_masm;
    Register _regs[8];

  public:
    Cached64Bytes(MacroAssembler *masm, RegSet rs): _masm(masm) {
      assert(rs.size() == 8, "%u registers are used to cache 16 4-byte data", rs.size());
      auto it = rs.begin();
      for (auto &r: _regs) {
        r = *it;
        ++it;
      }
    }

    void gen_loads(Register base) {
      for (int i = 0; i < 8; i += 2) {
        __ ldp(_regs[i], _regs[i + 1], Address(base, 8 * i));
      }
    }

    // Generate code extracting i-th unsigned word (4 bytes) from cached 64 bytes.
    void extract_u32(Register dest, int i) {
      __ ubfx(dest, _regs[i / 2], 32 * (i % 2), 32);
    }
  };

  // Utility routines for md5.
  // Clobbers r10 and r11.
  void md5_FF(Cached64Bytes& reg_cache, Register r1, Register r2, Register r3, Register r4,
              int k, int s, int t) {
    Register rscratch3 = r10;
    Register rscratch4 = r11;

    __ eorw(rscratch3, r3, r4);
    __ movw(rscratch2, t);
    __ andw(rscratch3, rscratch3, r2);
    __ addw(rscratch4, r1, rscratch2);
    reg_cache.extract_u32(rscratch1, k);
    __ eorw(rscratch3, rscratch3, r4);
    __ addw(rscratch4, rscratch4, rscratch1);
    __ addw(rscratch3, rscratch3, rscratch4);
    __ rorw(rscratch2, rscratch3, 32 - s);
    __ addw(r1, rscratch2, r2);
  }

  void md5_GG(Cached64Bytes& reg_cache, Register r1, Register r2, Register r3, Register r4,
              int k, int s, int t) {
    Register rscratch3 = r10;
    Register rscratch4 = r11;

    reg_cache.extract_u32(rscratch1, k);
    __ movw(rscratch2, t);
    __ addw(rscratch4, r1, rscratch2);
    __ addw(rscratch4, rscratch4, rscratch1);
    __ bicw(rscratch2, r3, r4);
    __ andw(rscratch3, r2, r4);
    __ addw(rscratch2, rscratch2, rscratch4);
    __ addw(rscratch2, rscratch2, rscratch3);
    __ rorw(rscratch2, rscratch2, 32 - s);
    __ addw(r1, rscratch2, r2);
  }

  void md5_HH(Cached64Bytes& reg_cache, Register r1, Register r2, Register r3, Register r4,
              int k, int s, int t) {
    Register rscratch3 = r10;
    Register rscratch4 = r11;

    __ eorw(rscratch3, r3, r4);
    __ movw(rscratch2, t);
    __ addw(rscratch4, r1, rscratch2);
    reg_cache.extract_u32(rscratch1, k);
    __ eorw(rscratch3, rscratch3, r2);
    __ addw(rscratch4, rscratch4, rscratch1);
    __ addw(rscratch3, rscratch3, rscratch4);
    __ rorw(rscratch2, rscratch3, 32 - s);
    __ addw(r1, rscratch2, r2);
  }

  void md5_II(Cached64Bytes& reg_cache, Register r1, Register r2, Register r3, Register r4,
              int k, int s, int t) {
    Register rscratch3 = r10;
    Register rscratch4 = r11;

    __ movw(rscratch3, t);
    __ ornw(rscratch2, r2, r4);
    __ addw(rscratch4, r1, rscratch3);
    reg_cache.extract_u32(rscratch1, k);
    __ eorw(rscratch3, rscratch2, r3);
    __ addw(rscratch4, rscratch4, rscratch1);
    __ addw(rscratch3, rscratch3, rscratch4);
    __ rorw(rscratch2, rscratch3, 32 - s);
    __ addw(r1, rscratch2, r2);
  }

  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - byte[]  source+offset
  //   c_rarg1   - int[]   SHA.state
  //   c_rarg2   - int     offset
  //   c_rarg3   - int     limit
  //
  address generate_md5_implCompress(StubId stub_id) {
    bool multi_block;
    switch (stub_id) {
    case StubId::stubgen_md5_implCompress_id:
      multi_block = false;
      break;
    case StubId::stubgen_md5_implCompressMB_id:
      multi_block = true;
      break;
    default:
      ShouldNotReachHere();
    }
    __ align(CodeEntryAlignment);

    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Register buf       = c_rarg0;
    Register state     = c_rarg1;
    Register ofs       = c_rarg2;
    Register limit     = c_rarg3;
    Register a         = r4;
    Register b         = r5;
    Register c         = r6;
    Register d         = r7;
    Register rscratch3 = r10;
    Register rscratch4 = r11;

    Register state_regs[2] = { r12, r13 };
    RegSet saved_regs = RegSet::range(r16, r22) - r18_tls;
    Cached64Bytes reg_cache(_masm, RegSet::of(r14, r15) + saved_regs);  // using 8 registers

    __ push(saved_regs, sp);

    __ ldp(state_regs[0], state_regs[1], Address(state));
    __ ubfx(a, state_regs[0],  0, 32);
    __ ubfx(b, state_regs[0], 32, 32);
    __ ubfx(c, state_regs[1],  0, 32);
    __ ubfx(d, state_regs[1], 32, 32);

    Label md5_loop;
    __ BIND(md5_loop);

    reg_cache.gen_loads(buf);

    // Round 1
    md5_FF(reg_cache, a, b, c, d,  0,  7, 0xd76aa478);
    md5_FF(reg_cache, d, a, b, c,  1, 12, 0xe8c7b756);
    md5_FF(reg_cache, c, d, a, b,  2, 17, 0x242070db);
    md5_FF(reg_cache, b, c, d, a,  3, 22, 0xc1bdceee);
    md5_FF(reg_cache, a, b, c, d,  4,  7, 0xf57c0faf);
    md5_FF(reg_cache, d, a, b, c,  5, 12, 0x4787c62a);
    md5_FF(reg_cache, c, d, a, b,  6, 17, 0xa8304613);
    md5_FF(reg_cache, b, c, d, a,  7, 22, 0xfd469501);
    md5_FF(reg_cache, a, b, c, d,  8,  7, 0x698098d8);
    md5_FF(reg_cache, d, a, b, c,  9, 12, 0x8b44f7af);
    md5_FF(reg_cache, c, d, a, b, 10, 17, 0xffff5bb1);
    md5_FF(reg_cache, b, c, d, a, 11, 22, 0x895cd7be);
    md5_FF(reg_cache, a, b, c, d, 12,  7, 0x6b901122);
    md5_FF(reg_cache, d, a, b, c, 13, 12, 0xfd987193);
    md5_FF(reg_cache, c, d, a, b, 14, 17, 0xa679438e);
    md5_FF(reg_cache, b, c, d, a, 15, 22, 0x49b40821);

    // Round 2
    md5_GG(reg_cache, a, b, c, d,  1,  5, 0xf61e2562);
    md5_GG(reg_cache, d, a, b, c,  6,  9, 0xc040b340);
    md5_GG(reg_cache, c, d, a, b, 11, 14, 0x265e5a51);
    md5_GG(reg_cache, b, c, d, a,  0, 20, 0xe9b6c7aa);
    md5_GG(reg_cache, a, b, c, d,  5,  5, 0xd62f105d);
    md5_GG(reg_cache, d, a, b, c, 10,  9, 0x02441453);
    md5_GG(reg_cache, c, d, a, b, 15, 14, 0xd8a1e681);
    md5_GG(reg_cache, b, c, d, a,  4, 20, 0xe7d3fbc8);
    md5_GG(reg_cache, a, b, c, d,  9,  5, 0x21e1cde6);
    md5_GG(reg_cache, d, a, b, c, 14,  9, 0xc33707d6);
    md5_GG(reg_cache, c, d, a, b,  3, 14, 0xf4d50d87);
    md5_GG(reg_cache, b, c, d, a,  8, 20, 0x455a14ed);
    md5_GG(reg_cache, a, b, c, d, 13,  5, 0xa9e3e905);
    md5_GG(reg_cache, d, a, b, c,  2,  9, 0xfcefa3f8);
    md5_GG(reg_cache, c, d, a, b,  7, 14, 0x676f02d9);
    md5_GG(reg_cache, b, c, d, a, 12, 20, 0x8d2a4c8a);

    // Round 3
    md5_HH(reg_cache, a, b, c, d,  5,  4, 0xfffa3942);
    md5_HH(reg_cache, d, a, b, c,  8, 11, 0x8771f681);
    md5_HH(reg_cache, c, d, a, b, 11, 16, 0x6d9d6122);
    md5_HH(reg_cache, b, c, d, a, 14, 23, 0xfde5380c);
    md5_HH(reg_cache, a, b, c, d,  1,  4, 0xa4beea44);
    md5_HH(reg_cache, d, a, b, c,  4, 11, 0x4bdecfa9);
    md5_HH(reg_cache, c, d, a, b,  7, 16, 0xf6bb4b60);
    md5_HH(reg_cache, b, c, d, a, 10, 23, 0xbebfbc70);
    md5_HH(reg_cache, a, b, c, d, 13,  4, 0x289b7ec6);
    md5_HH(reg_cache, d, a, b, c,  0, 11, 0xeaa127fa);
    md5_HH(reg_cache, c, d, a, b,  3, 16, 0xd4ef3085);
    md5_HH(reg_cache, b, c, d, a,  6, 23, 0x04881d05);
    md5_HH(reg_cache, a, b, c, d,  9,  4, 0xd9d4d039);
    md5_HH(reg_cache, d, a, b, c, 12, 11, 0xe6db99e5);
    md5_HH(reg_cache, c, d, a, b, 15, 16, 0x1fa27cf8);
    md5_HH(reg_cache, b, c, d, a,  2, 23, 0xc4ac5665);

    // Round 4
    md5_II(reg_cache, a, b, c, d,  0,  6, 0xf4292244);
    md5_II(reg_cache, d, a, b, c,  7, 10, 0x432aff97);
    md5_II(reg_cache, c, d, a, b, 14, 15, 0xab9423a7);
    md5_II(reg_cache, b, c, d, a,  5, 21, 0xfc93a039);
    md5_II(reg_cache, a, b, c, d, 12,  6, 0x655b59c3);
    md5_II(reg_cache, d, a, b, c,  3, 10, 0x8f0ccc92);
    md5_II(reg_cache, c, d, a, b, 10, 15, 0xffeff47d);
    md5_II(reg_cache, b, c, d, a,  1, 21, 0x85845dd1);
    md5_II(reg_cache, a, b, c, d,  8,  6, 0x6fa87e4f);
    md5_II(reg_cache, d, a, b, c, 15, 10, 0xfe2ce6e0);
    md5_II(reg_cache, c, d, a, b,  6, 15, 0xa3014314);
    md5_II(reg_cache, b, c, d, a, 13, 21, 0x4e0811a1);
    md5_II(reg_cache, a, b, c, d,  4,  6, 0xf7537e82);
    md5_II(reg_cache, d, a, b, c, 11, 10, 0xbd3af235);
    md5_II(reg_cache, c, d, a, b,  2, 15, 0x2ad7d2bb);
    md5_II(reg_cache, b, c, d, a,  9, 21, 0xeb86d391);

    __ addw(a, state_regs[0], a);
    __ ubfx(rscratch2, state_regs[0], 32, 32);
    __ addw(b, rscratch2, b);
    __ addw(c, state_regs[1], c);
    __ ubfx(rscratch4, state_regs[1], 32, 32);
    __ addw(d, rscratch4, d);

    __ orr(state_regs[0], a, b, Assembler::LSL, 32);
    __ orr(state_regs[1], c, d, Assembler::LSL, 32);

    if (multi_block) {
      __ add(buf, buf, 64);
      __ add(ofs, ofs, 64);
      __ cmp(ofs, limit);
      __ br(Assembler::LE, md5_loop);
      __ mov(c_rarg0, ofs); // return ofs
    }

    // write hash values back in the correct order
    __ stp(state_regs[0], state_regs[1], Address(state));

    __ pop(saved_regs, sp);

    __ ret(lr);

    return start;
  }

  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - byte[]  source+offset
  //   c_rarg1   - int[]   SHA.state
  //   c_rarg2   - int     offset
  //   c_rarg3   - int     limit
  //
  address generate_sha1_implCompress(StubId stub_id) {
    bool multi_block;
    switch (stub_id) {
    case StubId::stubgen_sha1_implCompress_id:
      multi_block = false;
      break;
    case StubId::stubgen_sha1_implCompressMB_id:
      multi_block = true;
      break;
    default:
      ShouldNotReachHere();
    }

    __ align(CodeEntryAlignment);

    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Register buf   = c_rarg0;
    Register state = c_rarg1;
    Register ofs   = c_rarg2;
    Register limit = c_rarg3;

    Label keys;
    Label sha1_loop;

    // load the keys into v0..v3
    __ adr(rscratch1, keys);
    __ ld4r(v0, v1, v2, v3, __ T4S, Address(rscratch1));
    // load 5 words state into v6, v7
    __ ldrq(v6, Address(state, 0));
    __ ldrs(v7, Address(state, 16));


    __ BIND(sha1_loop);
    // load 64 bytes of data into v16..v19
    __ ld1(v16, v17, v18, v19, __ T4S, multi_block ? __ post(buf, 64) : buf);
    __ rev32(v16, __ T16B, v16);
    __ rev32(v17, __ T16B, v17);
    __ rev32(v18, __ T16B, v18);
    __ rev32(v19, __ T16B, v19);

    // do the sha1
    __ addv(v4, __ T4S, v16, v0);
    __ orr(v20, __ T16B, v6, v6);

    FloatRegister d0 = v16;
    FloatRegister d1 = v17;
    FloatRegister d2 = v18;
    FloatRegister d3 = v19;

    for (int round = 0; round < 20; round++) {
      FloatRegister tmp1 = (round & 1) ? v4 : v5;
      FloatRegister tmp2 = (round & 1) ? v21 : v22;
      FloatRegister tmp3 = round ? ((round & 1) ? v22 : v21) : v7;
      FloatRegister tmp4 = (round & 1) ? v5 : v4;
      FloatRegister key = (round < 4) ? v0 : ((round < 9) ? v1 : ((round < 14) ? v2 : v3));

      if (round < 16) __ sha1su0(d0, __ T4S, d1, d2);
      if (round < 19) __ addv(tmp1, __ T4S, d1, key);
      __ sha1h(tmp2, __ T4S, v20);
      if (round < 5)
        __ sha1c(v20, __ T4S, tmp3, tmp4);
      else if (round < 10 || round >= 15)
        __ sha1p(v20, __ T4S, tmp3, tmp4);
      else
        __ sha1m(v20, __ T4S, tmp3, tmp4);
      if (round < 16) __ sha1su1(d0, __ T4S, d3);

      tmp1 = d0; d0 = d1; d1 = d2; d2 = d3; d3 = tmp1;
    }

    __ addv(v7, __ T2S, v7, v21);
    __ addv(v6, __ T4S, v6, v20);

    if (multi_block) {
      __ add(ofs, ofs, 64);
      __ cmp(ofs, limit);
      __ br(Assembler::LE, sha1_loop);
      __ mov(c_rarg0, ofs); // return ofs
    }

    __ strq(v6, Address(state, 0));
    __ strs(v7, Address(state, 16));

    __ ret(lr);

    __ bind(keys);
    __ emit_int32(0x5a827999);
    __ emit_int32(0x6ed9eba1);
    __ emit_int32(0x8f1bbcdc);
    __ emit_int32(0xca62c1d6);

    return start;
  }


  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - byte[]  source+offset
  //   c_rarg1   - int[]   SHA.state
  //   c_rarg2   - int     offset
  //   c_rarg3   - int     limit
  //
  address generate_sha256_implCompress(StubId stub_id) {
    bool multi_block;
    switch (stub_id) {
    case StubId::stubgen_sha256_implCompress_id:
      multi_block = false;
      break;
    case StubId::stubgen_sha256_implCompressMB_id:
      multi_block = true;
      break;
    default:
      ShouldNotReachHere();
    }

    static const uint32_t round_consts[64] = {
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

    __ align(CodeEntryAlignment);

    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Register buf   = c_rarg0;
    Register state = c_rarg1;
    Register ofs   = c_rarg2;
    Register limit = c_rarg3;

    Label sha1_loop;

    __ stpd(v8, v9, __ pre(sp, -32));
    __ stpd(v10, v11, Address(sp, 16));

// dga == v0
// dgb == v1
// dg0 == v2
// dg1 == v3
// dg2 == v4
// t0 == v6
// t1 == v7

    // load 16 keys to v16..v31
    __ lea(rscratch1, ExternalAddress((address)round_consts));
    __ ld1(v16, v17, v18, v19, __ T4S, __ post(rscratch1, 64));
    __ ld1(v20, v21, v22, v23, __ T4S, __ post(rscratch1, 64));
    __ ld1(v24, v25, v26, v27, __ T4S, __ post(rscratch1, 64));
    __ ld1(v28, v29, v30, v31, __ T4S, rscratch1);

    // load 8 words (256 bits) state
    __ ldpq(v0, v1, state);

    __ BIND(sha1_loop);
    // load 64 bytes of data into v8..v11
    __ ld1(v8, v9, v10, v11, __ T4S, multi_block ? __ post(buf, 64) : buf);
    __ rev32(v8, __ T16B, v8);
    __ rev32(v9, __ T16B, v9);
    __ rev32(v10, __ T16B, v10);
    __ rev32(v11, __ T16B, v11);

    __ addv(v6, __ T4S, v8, v16);
    __ orr(v2, __ T16B, v0, v0);
    __ orr(v3, __ T16B, v1, v1);

    FloatRegister d0 = v8;
    FloatRegister d1 = v9;
    FloatRegister d2 = v10;
    FloatRegister d3 = v11;


    for (int round = 0; round < 16; round++) {
      FloatRegister tmp1 = (round & 1) ? v6 : v7;
      FloatRegister tmp2 = (round & 1) ? v7 : v6;
      FloatRegister tmp3 = (round & 1) ? v2 : v4;
      FloatRegister tmp4 = (round & 1) ? v4 : v2;

      if (round < 12) __ sha256su0(d0, __ T4S, d1);
       __ orr(v4, __ T16B, v2, v2);
      if (round < 15)
        __ addv(tmp1, __ T4S, d1, as_FloatRegister(round + 17));
      __ sha256h(v2, __ T4S, v3, tmp2);
      __ sha256h2(v3, __ T4S, v4, tmp2);
      if (round < 12) __ sha256su1(d0, __ T4S, d2, d3);

      tmp1 = d0; d0 = d1; d1 = d2; d2 = d3; d3 = tmp1;
    }

    __ addv(v0, __ T4S, v0, v2);
    __ addv(v1, __ T4S, v1, v3);

    if (multi_block) {
      __ add(ofs, ofs, 64);
      __ cmp(ofs, limit);
      __ br(Assembler::LE, sha1_loop);
      __ mov(c_rarg0, ofs); // return ofs
    }

    __ ldpd(v10, v11, Address(sp, 16));
    __ ldpd(v8, v9, __ post(sp, 32));

    __ stpq(v0, v1, state);

    __ ret(lr);

    return start;
  }

  // Double rounds for sha512.
  void sha512_dround(int dr,
                     FloatRegister vi0, FloatRegister vi1,
                     FloatRegister vi2, FloatRegister vi3,
                     FloatRegister vi4, FloatRegister vrc0,
                     FloatRegister vrc1, FloatRegister vin0,
                     FloatRegister vin1, FloatRegister vin2,
                     FloatRegister vin3, FloatRegister vin4) {
      if (dr < 36) {
        __ ld1(vrc1, __ T2D, __ post(rscratch2, 16));
      }
      __ addv(v5, __ T2D, vrc0, vin0);
      __ ext(v6, __ T16B, vi2, vi3, 8);
      __ ext(v5, __ T16B, v5, v5, 8);
      __ ext(v7, __ T16B, vi1, vi2, 8);
      __ addv(vi3, __ T2D, vi3, v5);
      if (dr < 32) {
        __ ext(v5, __ T16B, vin3, vin4, 8);
        __ sha512su0(vin0, __ T2D, vin1);
      }
      __ sha512h(vi3, __ T2D, v6, v7);
      if (dr < 32) {
        __ sha512su1(vin0, __ T2D, vin2, v5);
      }
      __ addv(vi4, __ T2D, vi1, vi3);
      __ sha512h2(vi3, __ T2D, vi1, vi0);
  }

  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - byte[]  source+offset
  //   c_rarg1   - int[]   SHA.state
  //   c_rarg2   - int     offset
  //   c_rarg3   - int     limit
  //
  address generate_sha512_implCompress(StubId stub_id) {
    bool multi_block;
    switch (stub_id) {
    case StubId::stubgen_sha512_implCompress_id:
      multi_block = false;
      break;
    case StubId::stubgen_sha512_implCompressMB_id:
      multi_block = true;
      break;
    default:
      ShouldNotReachHere();
    }

    static const uint64_t round_consts[80] = {
      0x428A2F98D728AE22L, 0x7137449123EF65CDL, 0xB5C0FBCFEC4D3B2FL,
      0xE9B5DBA58189DBBCL, 0x3956C25BF348B538L, 0x59F111F1B605D019L,
      0x923F82A4AF194F9BL, 0xAB1C5ED5DA6D8118L, 0xD807AA98A3030242L,
      0x12835B0145706FBEL, 0x243185BE4EE4B28CL, 0x550C7DC3D5FFB4E2L,
      0x72BE5D74F27B896FL, 0x80DEB1FE3B1696B1L, 0x9BDC06A725C71235L,
      0xC19BF174CF692694L, 0xE49B69C19EF14AD2L, 0xEFBE4786384F25E3L,
      0x0FC19DC68B8CD5B5L, 0x240CA1CC77AC9C65L, 0x2DE92C6F592B0275L,
      0x4A7484AA6EA6E483L, 0x5CB0A9DCBD41FBD4L, 0x76F988DA831153B5L,
      0x983E5152EE66DFABL, 0xA831C66D2DB43210L, 0xB00327C898FB213FL,
      0xBF597FC7BEEF0EE4L, 0xC6E00BF33DA88FC2L, 0xD5A79147930AA725L,
      0x06CA6351E003826FL, 0x142929670A0E6E70L, 0x27B70A8546D22FFCL,
      0x2E1B21385C26C926L, 0x4D2C6DFC5AC42AEDL, 0x53380D139D95B3DFL,
      0x650A73548BAF63DEL, 0x766A0ABB3C77B2A8L, 0x81C2C92E47EDAEE6L,
      0x92722C851482353BL, 0xA2BFE8A14CF10364L, 0xA81A664BBC423001L,
      0xC24B8B70D0F89791L, 0xC76C51A30654BE30L, 0xD192E819D6EF5218L,
      0xD69906245565A910L, 0xF40E35855771202AL, 0x106AA07032BBD1B8L,
      0x19A4C116B8D2D0C8L, 0x1E376C085141AB53L, 0x2748774CDF8EEB99L,
      0x34B0BCB5E19B48A8L, 0x391C0CB3C5C95A63L, 0x4ED8AA4AE3418ACBL,
      0x5B9CCA4F7763E373L, 0x682E6FF3D6B2B8A3L, 0x748F82EE5DEFB2FCL,
      0x78A5636F43172F60L, 0x84C87814A1F0AB72L, 0x8CC702081A6439ECL,
      0x90BEFFFA23631E28L, 0xA4506CEBDE82BDE9L, 0xBEF9A3F7B2C67915L,
      0xC67178F2E372532BL, 0xCA273ECEEA26619CL, 0xD186B8C721C0C207L,
      0xEADA7DD6CDE0EB1EL, 0xF57D4F7FEE6ED178L, 0x06F067AA72176FBAL,
      0x0A637DC5A2C898A6L, 0x113F9804BEF90DAEL, 0x1B710B35131C471BL,
      0x28DB77F523047D84L, 0x32CAAB7B40C72493L, 0x3C9EBE0A15C9BEBCL,
      0x431D67C49C100D4CL, 0x4CC5D4BECB3E42B6L, 0x597F299CFC657E2AL,
      0x5FCB6FAB3AD6FAECL, 0x6C44198C4A475817L
    };

    __ align(CodeEntryAlignment);

    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Register buf   = c_rarg0;
    Register state = c_rarg1;
    Register ofs   = c_rarg2;
    Register limit = c_rarg3;

    __ stpd(v8, v9, __ pre(sp, -64));
    __ stpd(v10, v11, Address(sp, 16));
    __ stpd(v12, v13, Address(sp, 32));
    __ stpd(v14, v15, Address(sp, 48));

    Label sha512_loop;

    // load state
    __ ld1(v8, v9, v10, v11, __ T2D, state);

    // load first 4 round constants
    __ lea(rscratch1, ExternalAddress((address)round_consts));
    __ ld1(v20, v21, v22, v23, __ T2D, __ post(rscratch1, 64));

    __ BIND(sha512_loop);
    // load 128B of data into v12..v19
    __ ld1(v12, v13, v14, v15, __ T2D, __ post(buf, 64));
    __ ld1(v16, v17, v18, v19, __ T2D, __ post(buf, 64));
    __ rev64(v12, __ T16B, v12);
    __ rev64(v13, __ T16B, v13);
    __ rev64(v14, __ T16B, v14);
    __ rev64(v15, __ T16B, v15);
    __ rev64(v16, __ T16B, v16);
    __ rev64(v17, __ T16B, v17);
    __ rev64(v18, __ T16B, v18);
    __ rev64(v19, __ T16B, v19);

    __ mov(rscratch2, rscratch1);

    __ mov(v0, __ T16B, v8);
    __ mov(v1, __ T16B, v9);
    __ mov(v2, __ T16B, v10);
    __ mov(v3, __ T16B, v11);

    sha512_dround( 0, v0, v1, v2, v3, v4, v20, v24, v12, v13, v19, v16, v17);
    sha512_dround( 1, v3, v0, v4, v2, v1, v21, v25, v13, v14, v12, v17, v18);
    sha512_dround( 2, v2, v3, v1, v4, v0, v22, v26, v14, v15, v13, v18, v19);
    sha512_dround( 3, v4, v2, v0, v1, v3, v23, v27, v15, v16, v14, v19, v12);
    sha512_dround( 4, v1, v4, v3, v0, v2, v24, v28, v16, v17, v15, v12, v13);
    sha512_dround( 5, v0, v1, v2, v3, v4, v25, v29, v17, v18, v16, v13, v14);
    sha512_dround( 6, v3, v0, v4, v2, v1, v26, v30, v18, v19, v17, v14, v15);
    sha512_dround( 7, v2, v3, v1, v4, v0, v27, v31, v19, v12, v18, v15, v16);
    sha512_dround( 8, v4, v2, v0, v1, v3, v28, v24, v12, v13, v19, v16, v17);
    sha512_dround( 9, v1, v4, v3, v0, v2, v29, v25, v13, v14, v12, v17, v18);
    sha512_dround(10, v0, v1, v2, v3, v4, v30, v26, v14, v15, v13, v18, v19);
    sha512_dround(11, v3, v0, v4, v2, v1, v31, v27, v15, v16, v14, v19, v12);
    sha512_dround(12, v2, v3, v1, v4, v0, v24, v28, v16, v17, v15, v12, v13);
    sha512_dround(13, v4, v2, v0, v1, v3, v25, v29, v17, v18, v16, v13, v14);
    sha512_dround(14, v1, v4, v3, v0, v2, v26, v30, v18, v19, v17, v14, v15);
    sha512_dround(15, v0, v1, v2, v3, v4, v27, v31, v19, v12, v18, v15, v16);
    sha512_dround(16, v3, v0, v4, v2, v1, v28, v24, v12, v13, v19, v16, v17);
    sha512_dround(17, v2, v3, v1, v4, v0, v29, v25, v13, v14, v12, v17, v18);
    sha512_dround(18, v4, v2, v0, v1, v3, v30, v26, v14, v15, v13, v18, v19);
    sha512_dround(19, v1, v4, v3, v0, v2, v31, v27, v15, v16, v14, v19, v12);
    sha512_dround(20, v0, v1, v2, v3, v4, v24, v28, v16, v17, v15, v12, v13);
    sha512_dround(21, v3, v0, v4, v2, v1, v25, v29, v17, v18, v16, v13, v14);
    sha512_dround(22, v2, v3, v1, v4, v0, v26, v30, v18, v19, v17, v14, v15);
    sha512_dround(23, v4, v2, v0, v1, v3, v27, v31, v19, v12, v18, v15, v16);
    sha512_dround(24, v1, v4, v3, v0, v2, v28, v24, v12, v13, v19, v16, v17);
    sha512_dround(25, v0, v1, v2, v3, v4, v29, v25, v13, v14, v12, v17, v18);
    sha512_dround(26, v3, v0, v4, v2, v1, v30, v26, v14, v15, v13, v18, v19);
    sha512_dround(27, v2, v3, v1, v4, v0, v31, v27, v15, v16, v14, v19, v12);
    sha512_dround(28, v4, v2, v0, v1, v3, v24, v28, v16, v17, v15, v12, v13);
    sha512_dround(29, v1, v4, v3, v0, v2, v25, v29, v17, v18, v16, v13, v14);
    sha512_dround(30, v0, v1, v2, v3, v4, v26, v30, v18, v19, v17, v14, v15);
    sha512_dround(31, v3, v0, v4, v2, v1, v27, v31, v19, v12, v18, v15, v16);
    sha512_dround(32, v2, v3, v1, v4, v0, v28, v24, v12,  v0,  v0,  v0,  v0);
    sha512_dround(33, v4, v2, v0, v1, v3, v29, v25, v13,  v0,  v0,  v0,  v0);
    sha512_dround(34, v1, v4, v3, v0, v2, v30, v26, v14,  v0,  v0,  v0,  v0);
    sha512_dround(35, v0, v1, v2, v3, v4, v31, v27, v15,  v0,  v0,  v0,  v0);
    sha512_dround(36, v3, v0, v4, v2, v1, v24,  v0, v16,  v0,  v0,  v0,  v0);
    sha512_dround(37, v2, v3, v1, v4, v0, v25,  v0, v17,  v0,  v0,  v0,  v0);
    sha512_dround(38, v4, v2, v0, v1, v3, v26,  v0, v18,  v0,  v0,  v0,  v0);
    sha512_dround(39, v1, v4, v3, v0, v2, v27,  v0, v19,  v0,  v0,  v0,  v0);

    __ addv(v8, __ T2D, v8, v0);
    __ addv(v9, __ T2D, v9, v1);
    __ addv(v10, __ T2D, v10, v2);
    __ addv(v11, __ T2D, v11, v3);

    if (multi_block) {
      __ add(ofs, ofs, 128);
      __ cmp(ofs, limit);
      __ br(Assembler::LE, sha512_loop);
      __ mov(c_rarg0, ofs); // return ofs
    }

    __ st1(v8, v9, v10, v11, __ T2D, state);

    __ ldpd(v14, v15, Address(sp, 48));
    __ ldpd(v12, v13, Address(sp, 32));
    __ ldpd(v10, v11, Address(sp, 16));
    __ ldpd(v8, v9, __ post(sp, 64));

    __ ret(lr);

    return start;
  }

  // Execute one round of keccak of two computations in parallel.
  // One of the states should be loaded into the lower halves of
  // the vector registers v0-v24, the other should be loaded into
  // the upper halves of those registers. The ld1r instruction loads
  // the round constant into both halves of register v31.
  // Intermediate results c0...c5 and d0...d5 are computed
  // in registers v25...v30.
  // All vector instructions that are used operate on both register
  // halves in parallel.
  // If only a single computation is needed, one can only load the lower halves.
  void keccak_round(Register rscratch1) {
  __ eor3(v29, __ T16B, v4, v9, v14);       // c4 = a4 ^ a9 ^ a14
  __ eor3(v26, __ T16B, v1, v6, v11);       // c1 = a1 ^ a16 ^ a11
  __ eor3(v28, __ T16B, v3, v8, v13);       // c3 = a3 ^ a8 ^a13
  __ eor3(v25, __ T16B, v0, v5, v10);       // c0 = a0 ^ a5 ^ a10
  __ eor3(v27, __ T16B, v2, v7, v12);       // c2 = a2 ^ a7 ^ a12
  __ eor3(v29, __ T16B, v29, v19, v24);     // c4 ^= a19 ^ a24
  __ eor3(v26, __ T16B, v26, v16, v21);     // c1 ^= a16 ^ a21
  __ eor3(v28, __ T16B, v28, v18, v23);     // c3 ^= a18 ^ a23
  __ eor3(v25, __ T16B, v25, v15, v20);     // c0 ^= a15 ^ a20
  __ eor3(v27, __ T16B, v27, v17, v22);     // c2 ^= a17 ^ a22

  __ rax1(v30, __ T2D, v29, v26);           // d0 = c4 ^ rol(c1, 1)
  __ rax1(v26, __ T2D, v26, v28);           // d2 = c1 ^ rol(c3, 1)
  __ rax1(v28, __ T2D, v28, v25);           // d4 = c3 ^ rol(c0, 1)
  __ rax1(v25, __ T2D, v25, v27);           // d1 = c0 ^ rol(c2, 1)
  __ rax1(v27, __ T2D, v27, v29);           // d3 = c2 ^ rol(c4, 1)

  __ eor(v0, __ T16B, v0, v30);             // a0 = a0 ^ d0
  __ xar(v29, __ T2D, v1,  v25, (64 - 1));  // a10' = rol((a1^d1), 1)
  __ xar(v1,  __ T2D, v6,  v25, (64 - 44)); // a1 = rol(a6^d1), 44)
  __ xar(v6,  __ T2D, v9,  v28, (64 - 20)); // a6 = rol((a9^d4), 20)
  __ xar(v9,  __ T2D, v22, v26, (64 - 61)); // a9 = rol((a22^d2), 61)
  __ xar(v22, __ T2D, v14, v28, (64 - 39)); // a22 = rol((a14^d4), 39)
  __ xar(v14, __ T2D, v20, v30, (64 - 18)); // a14 = rol((a20^d0), 18)
  __ xar(v31, __ T2D, v2,  v26, (64 - 62)); // a20' = rol((a2^d2), 62)
  __ xar(v2,  __ T2D, v12, v26, (64 - 43)); // a2 = rol((a12^d2), 43)
  __ xar(v12, __ T2D, v13, v27, (64 - 25)); // a12 = rol((a13^d3), 25)
  __ xar(v13, __ T2D, v19, v28, (64 - 8));  // a13 = rol((a19^d4), 8)
  __ xar(v19, __ T2D, v23, v27, (64 - 56)); // a19 = rol((a23^d3), 56)
  __ xar(v23, __ T2D, v15, v30, (64 - 41)); // a23 = rol((a15^d0), 41)
  __ xar(v15, __ T2D, v4,  v28, (64 - 27)); // a15 = rol((a4^d4), 27)
  __ xar(v28, __ T2D, v24, v28, (64 - 14)); // a4' = rol((a24^d4), 14)
  __ xar(v24, __ T2D, v21, v25, (64 - 2));  // a24 = rol((a21^d1), 2)
  __ xar(v8,  __ T2D, v8,  v27, (64 - 55)); // a21' = rol((a8^d3), 55)
  __ xar(v4,  __ T2D, v16, v25, (64 - 45)); // a8' = rol((a16^d1), 45)
  __ xar(v16, __ T2D, v5,  v30, (64 - 36)); // a16 = rol((a5^d0), 36)
  __ xar(v5,  __ T2D, v3,  v27, (64 - 28)); // a5 = rol((a3^d3), 28)
  __ xar(v27, __ T2D, v18, v27, (64 - 21)); // a3' = rol((a18^d3), 21)
  __ xar(v3,  __ T2D, v17, v26, (64 - 15)); // a18' = rol((a17^d2), 15)
  __ xar(v25, __ T2D, v11, v25, (64 - 10)); // a17' = rol((a11^d1), 10)
  __ xar(v26, __ T2D, v7,  v26, (64 - 6));  // a11' = rol((a7^d2), 6)
  __ xar(v30, __ T2D, v10, v30, (64 - 3));  // a7' = rol((a10^d0), 3)

  __ bcax(v20, __ T16B, v31, v22, v8);      // a20 = a20' ^ (~a21 & a22')
  __ bcax(v21, __ T16B, v8,  v23, v22);     // a21 = a21' ^ (~a22 & a23)
  __ bcax(v22, __ T16B, v22, v24, v23);     // a22 = a22 ^ (~a23 & a24)
  __ bcax(v23, __ T16B, v23, v31, v24);     // a23 = a23 ^ (~a24 & a20')
  __ bcax(v24, __ T16B, v24, v8,  v31);     // a24 = a24 ^ (~a20' & a21')

  __ ld1r(v31, __ T2D, __ post(rscratch1, 8)); // rc = round_constants[i]

  __ bcax(v17, __ T16B, v25, v19, v3);      // a17 = a17' ^ (~a18' & a19)
  __ bcax(v18, __ T16B, v3,  v15, v19);     // a18 = a18' ^ (~a19 & a15')
  __ bcax(v19, __ T16B, v19, v16, v15);     // a19 = a19 ^ (~a15 & a16)
  __ bcax(v15, __ T16B, v15, v25, v16);     // a15 = a15 ^ (~a16 & a17')
  __ bcax(v16, __ T16B, v16, v3,  v25);     // a16 = a16 ^ (~a17' & a18')

  __ bcax(v10, __ T16B, v29, v12, v26);     // a10 = a10' ^ (~a11' & a12)
  __ bcax(v11, __ T16B, v26, v13, v12);     // a11 = a11' ^ (~a12 & a13)
  __ bcax(v12, __ T16B, v12, v14, v13);     // a12 = a12 ^ (~a13 & a14)
  __ bcax(v13, __ T16B, v13, v29, v14);     // a13 = a13 ^ (~a14 & a10')
  __ bcax(v14, __ T16B, v14, v26, v29);     // a14 = a14 ^ (~a10' & a11')

  __ bcax(v7, __ T16B, v30, v9,  v4);       // a7 = a7' ^ (~a8' & a9)
  __ bcax(v8, __ T16B, v4,  v5,  v9);       // a8 = a8' ^ (~a9 & a5)
  __ bcax(v9, __ T16B, v9,  v6,  v5);       // a9 = a9 ^ (~a5 & a6)
  __ bcax(v5, __ T16B, v5,  v30, v6);       // a5 = a5 ^ (~a6 & a7)
  __ bcax(v6, __ T16B, v6,  v4,  v30);      // a6 = a6 ^ (~a7 & a8')

  __ bcax(v3, __ T16B, v27, v0,  v28);      // a3 = a3' ^ (~a4' & a0)
  __ bcax(v4, __ T16B, v28, v1,  v0);       // a4 = a4' ^ (~a0 & a1)
  __ bcax(v0, __ T16B, v0,  v2,  v1);       // a0 = a0 ^ (~a1 & a2)
  __ bcax(v1, __ T16B, v1,  v27, v2);       // a1 = a1 ^ (~a2 & a3)
  __ bcax(v2, __ T16B, v2,  v28, v27);      // a2 = a2 ^ (~a3 & a4')

  __ eor(v0, __ T16B, v0, v31);             // a0 = a0 ^ rc
  }

  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - byte[]  source+offset
  //   c_rarg1   - byte[]  SHA.state
  //   c_rarg2   - int     block_size
  //   c_rarg3   - int     offset
  //   c_rarg4   - int     limit
  //
  address generate_sha3_implCompress(StubId stub_id) {
    bool multi_block;
    switch (stub_id) {
    case StubId::stubgen_sha3_implCompress_id:
      multi_block = false;
      break;
    case StubId::stubgen_sha3_implCompressMB_id:
      multi_block = true;
      break;
    default:
      ShouldNotReachHere();
    }

    static const uint64_t round_consts[24] = {
      0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL,
      0x8000000080008000L, 0x000000000000808BL, 0x0000000080000001L,
      0x8000000080008081L, 0x8000000000008009L, 0x000000000000008AL,
      0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
      0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L,
      0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
      0x000000000000800AL, 0x800000008000000AL, 0x8000000080008081L,
      0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    };

    __ align(CodeEntryAlignment);

    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Register buf           = c_rarg0;
    Register state         = c_rarg1;
    Register block_size    = c_rarg2;
    Register ofs           = c_rarg3;
    Register limit         = c_rarg4;

    Label sha3_loop, rounds24_loop;
    Label sha3_512_or_sha3_384, shake128;

    __ stpd(v8, v9, __ pre(sp, -64));
    __ stpd(v10, v11, Address(sp, 16));
    __ stpd(v12, v13, Address(sp, 32));
    __ stpd(v14, v15, Address(sp, 48));

    // load state
    __ add(rscratch1, state, 32);
    __ ld1(v0, v1, v2,  v3,  __ T1D, state);
    __ ld1(v4, v5, v6,  v7,  __ T1D, __ post(rscratch1, 32));
    __ ld1(v8, v9, v10, v11, __ T1D, __ post(rscratch1, 32));
    __ ld1(v12, v13, v14, v15, __ T1D, __ post(rscratch1, 32));
    __ ld1(v16, v17, v18, v19, __ T1D, __ post(rscratch1, 32));
    __ ld1(v20, v21, v22, v23, __ T1D, __ post(rscratch1, 32));
    __ ld1(v24, __ T1D, rscratch1);

    __ BIND(sha3_loop);

    // 24 keccak rounds
    __ movw(rscratch2, 24);

    // load round_constants base
    __ lea(rscratch1, ExternalAddress((address) round_consts));

    // load input
    __ ld1(v25, v26, v27, v28, __ T8B, __ post(buf, 32));
    __ ld1(v29, v30, v31, __ T8B, __ post(buf, 24));
    __ eor(v0, __ T8B, v0, v25);
    __ eor(v1, __ T8B, v1, v26);
    __ eor(v2, __ T8B, v2, v27);
    __ eor(v3, __ T8B, v3, v28);
    __ eor(v4, __ T8B, v4, v29);
    __ eor(v5, __ T8B, v5, v30);
    __ eor(v6, __ T8B, v6, v31);

    // block_size == 72, SHA3-512; block_size == 104, SHA3-384
    __ tbz(block_size, 7, sha3_512_or_sha3_384);

    __ ld1(v25, v26, v27, v28, __ T8B, __ post(buf, 32));
    __ ld1(v29, v30, v31, __ T8B, __ post(buf, 24));
    __ eor(v7, __ T8B, v7, v25);
    __ eor(v8, __ T8B, v8, v26);
    __ eor(v9, __ T8B, v9, v27);
    __ eor(v10, __ T8B, v10, v28);
    __ eor(v11, __ T8B, v11, v29);
    __ eor(v12, __ T8B, v12, v30);
    __ eor(v13, __ T8B, v13, v31);

    __ ld1(v25, v26, v27,  __ T8B, __ post(buf, 24));
    __ eor(v14, __ T8B, v14, v25);
    __ eor(v15, __ T8B, v15, v26);
    __ eor(v16, __ T8B, v16, v27);

    // block_size == 136, bit4 == 0 and bit5 == 0, SHA3-256 or SHAKE256
    __ andw(c_rarg5, block_size, 48);
    __ cbzw(c_rarg5, rounds24_loop);

    __ tbnz(block_size, 5, shake128);
    // block_size == 144, bit5 == 0, SHA3-224
    __ ldrd(v28, __ post(buf, 8));
    __ eor(v17, __ T8B, v17, v28);
    __ b(rounds24_loop);

    __ BIND(shake128);
    __ ld1(v28, v29, v30, v31, __ T8B, __ post(buf, 32));
    __ eor(v17, __ T8B, v17, v28);
    __ eor(v18, __ T8B, v18, v29);
    __ eor(v19, __ T8B, v19, v30);
    __ eor(v20, __ T8B, v20, v31);
    __ b(rounds24_loop); // block_size == 168, SHAKE128

    __ BIND(sha3_512_or_sha3_384);
    __ ld1(v25, v26, __ T8B, __ post(buf, 16));
    __ eor(v7, __ T8B, v7, v25);
    __ eor(v8, __ T8B, v8, v26);
    __ tbz(block_size, 5, rounds24_loop); // SHA3-512

    // SHA3-384
    __ ld1(v27, v28, v29, v30, __ T8B, __ post(buf, 32));
    __ eor(v9,  __ T8B, v9,  v27);
    __ eor(v10, __ T8B, v10, v28);
    __ eor(v11, __ T8B, v11, v29);
    __ eor(v12, __ T8B, v12, v30);

    __ BIND(rounds24_loop);
    __ subw(rscratch2, rscratch2, 1);

    keccak_round(rscratch1);

    __ cbnzw(rscratch2, rounds24_loop);

    if (multi_block) {
      __ add(ofs, ofs, block_size);
      __ cmp(ofs, limit);
      __ br(Assembler::LE, sha3_loop);
      __ mov(c_rarg0, ofs); // return ofs
    }

    __ st1(v0, v1, v2,  v3,  __ T1D, __ post(state, 32));
    __ st1(v4, v5, v6,  v7,  __ T1D, __ post(state, 32));
    __ st1(v8, v9, v10, v11, __ T1D, __ post(state, 32));
    __ st1(v12, v13, v14, v15, __ T1D, __ post(state, 32));
    __ st1(v16, v17, v18, v19, __ T1D, __ post(state, 32));
    __ st1(v20, v21, v22, v23, __ T1D, __ post(state, 32));
    __ st1(v24, __ T1D, state);

    // restore callee-saved registers
    __ ldpd(v14, v15, Address(sp, 48));
    __ ldpd(v12, v13, Address(sp, 32));
    __ ldpd(v10, v11, Address(sp, 16));
    __ ldpd(v8, v9, __ post(sp, 64));

    __ ret(lr);

    return start;
  }

  // Inputs:
  //   c_rarg0   - long[]  state0
  //   c_rarg1   - long[]  state1
  address generate_double_keccak() {
    static const uint64_t round_consts[24] = {
      0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL,
      0x8000000080008000L, 0x000000000000808BL, 0x0000000080000001L,
      0x8000000080008081L, 0x8000000000008009L, 0x000000000000008AL,
      0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
      0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L,
      0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
      0x000000000000800AL, 0x800000008000000AL, 0x8000000080008081L,
      0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    };

    // Implements the double_keccak() method of the
    // sun.secyrity.provider.SHA3Parallel class
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "double_keccak");
    address start = __ pc();
    __ enter();

    Register state0        = c_rarg0;
    Register state1        = c_rarg1;

    Label rounds24_loop;

    // save callee-saved registers
    __ stpd(v8, v9, __ pre(sp, -64));
    __ stpd(v10, v11, Address(sp, 16));
    __ stpd(v12, v13, Address(sp, 32));
    __ stpd(v14, v15, Address(sp, 48));

    // load states
    __ add(rscratch1, state0, 32);
    __ ld4(v0, v1, v2,  v3, __ D, 0,  state0);
    __ ld4(v4, v5, v6,  v7, __ D, 0, __ post(rscratch1, 32));
    __ ld4(v8, v9, v10, v11, __ D, 0, __ post(rscratch1, 32));
    __ ld4(v12, v13, v14, v15, __ D, 0, __ post(rscratch1, 32));
    __ ld4(v16, v17, v18, v19, __ D, 0, __ post(rscratch1, 32));
    __ ld4(v20, v21, v22, v23, __ D, 0, __ post(rscratch1, 32));
    __ ld1(v24, __ D, 0, rscratch1);
    __ add(rscratch1, state1, 32);
    __ ld4(v0, v1, v2,  v3,  __ D, 1, state1);
    __ ld4(v4, v5, v6,  v7, __ D, 1, __ post(rscratch1, 32));
    __ ld4(v8, v9, v10, v11, __ D, 1, __ post(rscratch1, 32));
    __ ld4(v12, v13, v14, v15, __ D, 1, __ post(rscratch1, 32));
    __ ld4(v16, v17, v18, v19, __ D, 1, __ post(rscratch1, 32));
    __ ld4(v20, v21, v22, v23, __ D, 1, __ post(rscratch1, 32));
    __ ld1(v24, __ D, 1, rscratch1);

    // 24 keccak rounds
    __ movw(rscratch2, 24);

    // load round_constants base
    __ lea(rscratch1, ExternalAddress((address) round_consts));

    __ BIND(rounds24_loop);
    __ subw(rscratch2, rscratch2, 1);
    keccak_round(rscratch1);
    __ cbnzw(rscratch2, rounds24_loop);

    __ st4(v0, v1, v2,  v3,  __ D, 0, __ post(state0, 32));
    __ st4(v4, v5, v6,  v7,  __ D, 0, __ post(state0, 32));
    __ st4(v8, v9, v10, v11, __ D, 0, __ post(state0, 32));
    __ st4(v12, v13, v14, v15, __ D, 0, __ post(state0, 32));
    __ st4(v16, v17, v18, v19, __ D, 0, __ post(state0, 32));
    __ st4(v20, v21, v22, v23, __ D, 0, __ post(state0, 32));
    __ st1(v24, __ D, 0, state0);
    __ st4(v0, v1, v2,  v3,  __ D, 1, __ post(state1, 32));
    __ st4(v4, v5, v6,  v7, __ D, 1, __ post(state1, 32));
    __ st4(v8, v9, v10, v11, __ D, 1, __ post(state1, 32));
    __ st4(v12, v13, v14, v15, __ D, 1, __ post(state1, 32));
    __ st4(v16, v17, v18, v19, __ D, 1, __ post(state1, 32));
    __ st4(v20, v21, v22, v23, __ D, 1, __ post(state1, 32));
    __ st1(v24, __ D, 1, state1);

    // restore callee-saved vector registers
    __ ldpd(v14, v15, Address(sp, 48));
    __ ldpd(v12, v13, Address(sp, 32));
    __ ldpd(v10, v11, Address(sp, 16));
    __ ldpd(v8, v9, __ post(sp, 64));

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // ChaCha20 block function.  This version parallelizes the 32-bit
  // state elements on each of 16 vectors, producing 4 blocks of
  // keystream at a time.
  //
  // state (int[16]) = c_rarg0
  // keystream (byte[256]) = c_rarg1
  // return - number of bytes of produced keystream (always 256)
  //
  // This implementation takes each 32-bit integer from the state
  // array and broadcasts it across all 4 32-bit lanes of a vector register
  // (e.g. state[0] is replicated on all 4 lanes of v4, state[1] to all 4 lanes
  // of v5, etc.).  Once all 16 elements have been broadcast onto 16 vectors,
  // the quarter round schedule is implemented as outlined in RFC 7539 section
  // 2.3.  However, instead of sequentially processing the 3 quarter round
  // operations represented by one QUARTERROUND function, we instead stack all
  // the adds, xors and left-rotations from the first 4 quarter rounds together
  // and then do the same for the second set of 4 quarter rounds.  This removes
  // some latency that would otherwise be incurred by waiting for an add to
  // complete before performing an xor (which depends on the result of the
  // add), etc. An adjustment happens between the first and second groups of 4
  // quarter rounds, but this is done only in the inputs to the macro functions
  // that generate the assembly instructions - these adjustments themselves are
  // not part of the resulting assembly.
  // The 4 registers v0-v3 are used during the quarter round operations as
  // scratch registers.  Once the 20 rounds are complete, these 4 scratch
  // registers become the vectors involved in adding the start state back onto
  // the post-QR working state.  After the adds are complete, each of the 16
  // vectors write their first lane back to the keystream buffer, followed
  // by the second lane from all vectors and so on.
  address generate_chacha20Block_blockpar() {
    Label L_twoRounds, L_cc20_const;
    // The constant data is broken into two 128-bit segments to be loaded
    // onto FloatRegisters.  The first 128 bits are a counter add overlay
    // that adds +0/+1/+2/+3 to the vector holding replicated state[12].
    // The second 128-bits is a table constant used for 8-bit left rotations.
    __ BIND(L_cc20_const);
    __ emit_int64(0x0000000100000000UL);
    __ emit_int64(0x0000000300000002UL);
    __ emit_int64(0x0605040702010003UL);
    __ emit_int64(0x0E0D0C0F0A09080BUL);

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_chacha20Block_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    int i, j;
    const Register state = c_rarg0;
    const Register keystream = c_rarg1;
    const Register loopCtr = r10;
    const Register tmpAddr = r11;
    const FloatRegister ctrAddOverlay = v28;
    const FloatRegister lrot8Tbl = v29;

    // Organize SIMD registers in an array that facilitates
    // putting repetitive opcodes into loop structures.  It is
    // important that each grouping of 4 registers is monotonically
    // increasing to support the requirements of multi-register
    // instructions (e.g. ld4r, st4, etc.)
    const FloatRegister workSt[16] = {
         v4,  v5,  v6,  v7, v16, v17, v18, v19,
        v20, v21, v22, v23, v24, v25, v26, v27
    };

    // Pull in constant data.  The first 16 bytes are the add overlay
    // which is applied to the vector holding the counter (state[12]).
    // The second 16 bytes is the index register for the 8-bit left
    // rotation tbl instruction.
    __ adr(tmpAddr, L_cc20_const);
    __ ldpq(ctrAddOverlay, lrot8Tbl, Address(tmpAddr));

    // Load from memory and interlace across 16 SIMD registers,
    // With each word from memory being broadcast to all lanes of
    // each successive SIMD register.
    //      Addr(0) -> All lanes in workSt[i]
    //      Addr(4) -> All lanes workSt[i + 1], etc.
    __ mov(tmpAddr, state);
    for (i = 0; i < 16; i += 4) {
      __ ld4r(workSt[i], workSt[i + 1], workSt[i + 2], workSt[i + 3], __ T4S,
          __ post(tmpAddr, 16));
    }
    __ addv(workSt[12], __ T4S, workSt[12], ctrAddOverlay); // Add ctr overlay

    // Before entering the loop, create 5 4-register arrays.  These
    // will hold the 4 registers that represent the a/b/c/d fields
    // in the quarter round operation.  For instance the "b" field
    // for the first 4 quarter round operations is the set of v16/v17/v18/v19,
    // but in the second 4 quarter rounds it gets adjusted to v17/v18/v19/v16
    // since it is part of a diagonal organization.  The aSet and scratch
    // register sets are defined at declaration time because they do not change
    // organization at any point during the 20-round processing.
    FloatRegister aSet[4] = { v4, v5, v6, v7 };
    FloatRegister bSet[4];
    FloatRegister cSet[4];
    FloatRegister dSet[4];
    FloatRegister scratch[4] = { v0, v1, v2, v3 };

    // Set up the 10 iteration loop and perform all 8 quarter round ops
    __ mov(loopCtr, 10);
    __ BIND(L_twoRounds);

    // Set to columnar organization and do the following 4 quarter-rounds:
    // QUARTERROUND(0, 4, 8, 12)
    // QUARTERROUND(1, 5, 9, 13)
    // QUARTERROUND(2, 6, 10, 14)
    // QUARTERROUND(3, 7, 11, 15)
    __ cc20_set_qr_registers(bSet, workSt, 4, 5, 6, 7);
    __ cc20_set_qr_registers(cSet, workSt, 8, 9, 10, 11);
    __ cc20_set_qr_registers(dSet, workSt, 12, 13, 14, 15);

    __ cc20_qr_add4(aSet, bSet);                    // a += b
    __ cc20_qr_xor4(dSet, aSet, dSet);              // d ^= a
    __ cc20_qr_lrot4(dSet, dSet, 16, lrot8Tbl);     // d <<<= 16

    __ cc20_qr_add4(cSet, dSet);                    // c += d
    __ cc20_qr_xor4(bSet, cSet, scratch);           // b ^= c (scratch)
    __ cc20_qr_lrot4(scratch, bSet, 12, lrot8Tbl);  // b <<<= 12

    __ cc20_qr_add4(aSet, bSet);                    // a += b
    __ cc20_qr_xor4(dSet, aSet, dSet);              // d ^= a
    __ cc20_qr_lrot4(dSet, dSet, 8, lrot8Tbl);      // d <<<= 8

    __ cc20_qr_add4(cSet, dSet);                    // c += d
    __ cc20_qr_xor4(bSet, cSet, scratch);           // b ^= c (scratch)
    __ cc20_qr_lrot4(scratch, bSet, 7, lrot8Tbl);   // b <<<= 12

    // Set to diagonal organization and do the next 4 quarter-rounds:
    // QUARTERROUND(0, 5, 10, 15)
    // QUARTERROUND(1, 6, 11, 12)
    // QUARTERROUND(2, 7, 8, 13)
    // QUARTERROUND(3, 4, 9, 14)
    __ cc20_set_qr_registers(bSet, workSt, 5, 6, 7, 4);
    __ cc20_set_qr_registers(cSet, workSt, 10, 11, 8, 9);
    __ cc20_set_qr_registers(dSet, workSt, 15, 12, 13, 14);

    __ cc20_qr_add4(aSet, bSet);                    // a += b
    __ cc20_qr_xor4(dSet, aSet, dSet);              // d ^= a
    __ cc20_qr_lrot4(dSet, dSet, 16, lrot8Tbl);     // d <<<= 16

    __ cc20_qr_add4(cSet, dSet);                    // c += d
    __ cc20_qr_xor4(bSet, cSet, scratch);           // b ^= c (scratch)
    __ cc20_qr_lrot4(scratch, bSet, 12, lrot8Tbl);  // b <<<= 12

    __ cc20_qr_add4(aSet, bSet);                    // a += b
    __ cc20_qr_xor4(dSet, aSet, dSet);              // d ^= a
    __ cc20_qr_lrot4(dSet, dSet, 8, lrot8Tbl);      // d <<<= 8

    __ cc20_qr_add4(cSet, dSet);                    // c += d
    __ cc20_qr_xor4(bSet, cSet, scratch);           // b ^= c (scratch)
    __ cc20_qr_lrot4(scratch, bSet, 7, lrot8Tbl);   // b <<<= 12

    // Decrement and iterate
    __ sub(loopCtr, loopCtr, 1);
    __ cbnz(loopCtr, L_twoRounds);

    __ mov(tmpAddr, state);

    // Add the starting state back to the post-loop keystream
    // state.  We read/interlace the state array from memory into
    // 4 registers similar to what we did in the beginning.  Then
    // add the counter overlay onto workSt[12] at the end.
    for (i = 0; i < 16; i += 4) {
      __ ld4r(v0, v1, v2, v3, __ T4S, __ post(tmpAddr, 16));
      __ addv(workSt[i], __ T4S, workSt[i], v0);
      __ addv(workSt[i + 1], __ T4S, workSt[i + 1], v1);
      __ addv(workSt[i + 2], __ T4S, workSt[i + 2], v2);
      __ addv(workSt[i + 3], __ T4S, workSt[i + 3], v3);
    }
    __ addv(workSt[12], __ T4S, workSt[12], ctrAddOverlay); // Add ctr overlay

    // Write working state into the keystream buffer.  This is accomplished
    // by taking the lane "i" from each of the four vectors and writing
    // it to consecutive 4-byte offsets, then post-incrementing by 16 and
    // repeating with the next 4 vectors until all 16 vectors have been used.
    // Then move to the next lane and repeat the process until all lanes have
    // been written.
    for (i = 0; i < 4; i++) {
      for (j = 0; j < 16; j += 4) {
        __ st4(workSt[j], workSt[j + 1], workSt[j + 2], workSt[j + 3], __ S, i,
            __ post(keystream, 16));
      }
    }

    __ mov(r0, 256);             // Return length of output keystream
    __ leave();
    __ ret(lr);

    return start;
  }

  // Helpers to schedule parallel operation bundles across vector
  // register sequences of size 2, 4 or 8.

  // Implement various primitive computations across vector sequences

  template<int N>
  void vs_addv(const VSeq<N>& v, Assembler::SIMD_Arrangement T,
               const VSeq<N>& v1, const VSeq<N>& v2) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    assert(!vs_write_before_read(v, v2), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ addv(v[i], T, v1[i], v2[i]);
    }
  }

  template<int N>
  void vs_subv(const VSeq<N>& v, Assembler::SIMD_Arrangement T,
               const VSeq<N>& v1, const VSeq<N>& v2) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    assert(!vs_write_before_read(v, v2), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ subv(v[i], T, v1[i], v2[i]);
    }
  }

  template<int N>
  void vs_mulv(const VSeq<N>& v, Assembler::SIMD_Arrangement T,
               const VSeq<N>& v1, const VSeq<N>& v2) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    assert(!vs_write_before_read(v, v2), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ mulv(v[i], T, v1[i], v2[i]);
    }
  }

  template<int N>
  void vs_negr(const VSeq<N>& v, Assembler::SIMD_Arrangement T, const VSeq<N>& v1) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ negr(v[i], T, v1[i]);
    }
  }

  template<int N>
  void vs_sshr(const VSeq<N>& v, Assembler::SIMD_Arrangement T,
               const VSeq<N>& v1, int shift) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ sshr(v[i], T, v1[i], shift);
    }
  }

  template<int N>
  void vs_andr(const VSeq<N>& v, const VSeq<N>& v1, const VSeq<N>& v2) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    assert(!vs_write_before_read(v, v2), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ andr(v[i], __ T16B, v1[i], v2[i]);
    }
  }

  template<int N>
  void vs_orr(const VSeq<N>& v, const VSeq<N>& v1, const VSeq<N>& v2) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    assert(!vs_write_before_read(v, v2), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ orr(v[i], __ T16B, v1[i], v2[i]);
    }
  }

  template<int N>
  void vs_notr(const VSeq<N>& v, const VSeq<N>& v1) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ notr(v[i], __ T16B, v1[i]);
    }
  }

  template<int N>
  void vs_sqdmulh(const VSeq<N>& v, Assembler::SIMD_Arrangement T, const VSeq<N>& v1, const VSeq<N>& v2) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    assert(!vs_write_before_read(v, v2), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ sqdmulh(v[i], T, v1[i], v2[i]);
    }
  }

  template<int N>
  void vs_mlsv(const VSeq<N>& v, Assembler::SIMD_Arrangement T, const VSeq<N>& v1, VSeq<N>& v2) {
    // output must not be constant
    assert(N == 1  || !v.is_constant(), "cannot output multiple values to a constant vector");
    // output cannot overwrite pending inputs
    assert(!vs_write_before_read(v, v1), "output overwrites input");
    assert(!vs_write_before_read(v, v2), "output overwrites input");
    for (int i = 0; i < N; i++) {
      __ mlsv(v[i], T, v1[i], v2[i]);
    }
  }

  // load N/2 successive pairs of quadword values from memory in order
  // into N successive vector registers of the sequence via the
  // address supplied in base.
  template<int N>
  void vs_ldpq(const VSeq<N>& v, Register base) {
    for (int i = 0; i < N; i += 2) {
      __ ldpq(v[i], v[i+1], Address(base, 32 * i));
    }
  }

  // load N/2 successive pairs of quadword values from memory in order
  // into N vector registers of the sequence via the address supplied
  // in base using post-increment addressing
  template<int N>
  void vs_ldpq_post(const VSeq<N>& v, Register base) {
    static_assert((N & (N - 1)) == 0, "sequence length must be even");
    for (int i = 0; i < N; i += 2) {
      __ ldpq(v[i], v[i+1], __ post(base, 32));
    }
  }

  // store N successive vector registers of the sequence into N/2
  // successive pairs of quadword memory locations via the address
  // supplied in base using post-increment addressing
  template<int N>
  void vs_stpq_post(const VSeq<N>& v, Register base) {
    static_assert((N & (N - 1)) == 0, "sequence length must be even");
    for (int i = 0; i < N; i += 2) {
      __ stpq(v[i], v[i+1], __ post(base, 32));
    }
  }

  // load N/2 pairs of quadword values from memory de-interleaved into
  // N vector registers 2 at a time via the address supplied in base
  // using post-increment addressing.
  template<int N>
  void vs_ld2_post(const VSeq<N>& v, Assembler::SIMD_Arrangement T, Register base) {
    static_assert((N & (N - 1)) == 0, "sequence length must be even");
    for (int i = 0; i < N; i += 2) {
      __ ld2(v[i], v[i+1], T, __ post(base, 32));
    }
  }

  // store N vector registers interleaved into N/2 pairs of quadword
  // memory locations via the address supplied in base using
  // post-increment addressing.
  template<int N>
  void vs_st2_post(const VSeq<N>& v, Assembler::SIMD_Arrangement T, Register base) {
    static_assert((N & (N - 1)) == 0, "sequence length must be even");
    for (int i = 0; i < N; i += 2) {
      __ st2(v[i], v[i+1], T, __ post(base, 32));
    }
  }

  // load N quadword values from memory de-interleaved into N vector
  // registers 3 elements at a time via the address supplied in base.
  template<int N>
  void vs_ld3(const VSeq<N>& v, Assembler::SIMD_Arrangement T, Register base) {
    static_assert(N == ((N / 3) * 3), "sequence length must be multiple of 3");
    for (int i = 0; i < N; i += 3) {
      __ ld3(v[i], v[i+1], v[i+2], T, base);
    }
  }

  // load N quadword values from memory de-interleaved into N vector
  // registers 3 elements at a time via the address supplied in base
  // using post-increment addressing.
  template<int N>
  void vs_ld3_post(const VSeq<N>& v, Assembler::SIMD_Arrangement T, Register base) {
    static_assert(N == ((N / 3) * 3), "sequence length must be multiple of 3");
    for (int i = 0; i < N; i += 3) {
      __ ld3(v[i], v[i+1], v[i+2], T, __ post(base, 48));
    }
  }

  // load N/2 pairs of quadword values from memory into N vector
  // registers via the address supplied in base with each pair indexed
  // using the the start offset plus the corresponding entry in the
  // offsets array
  template<int N>
  void vs_ldpq_indexed(const VSeq<N>& v, Register base, int start, int (&offsets)[N/2]) {
    for (int i = 0; i < N/2; i++) {
      __ ldpq(v[2*i], v[2*i+1], Address(base, start + offsets[i]));
    }
  }

  // store N vector registers into N/2 pairs of quadword memory
  // locations via the address supplied in base with each pair indexed
  // using the the start offset plus the corresponding entry in the
  // offsets array
  template<int N>
  void vs_stpq_indexed(const VSeq<N>& v, Register base, int start, int offsets[N/2]) {
    for (int i = 0; i < N/2; i++) {
      __ stpq(v[2*i], v[2*i+1], Address(base, start + offsets[i]));
    }
  }

  // load N single quadword values from memory into N vector registers
  // via the address supplied in base with each value indexed using
  // the the start offset plus the corresponding entry in the offsets
  // array
  template<int N>
  void vs_ldr_indexed(const VSeq<N>& v, Assembler::SIMD_RegVariant T, Register base,
                      int start, int (&offsets)[N]) {
    for (int i = 0; i < N; i++) {
      __ ldr(v[i], T, Address(base, start + offsets[i]));
    }
  }

  // store N vector registers into N single quadword memory locations
  // via the address supplied in base with each value indexed using
  // the the start offset plus the corresponding entry in the offsets
  // array
  template<int N>
  void vs_str_indexed(const VSeq<N>& v, Assembler::SIMD_RegVariant T, Register base,
                      int start, int (&offsets)[N]) {
    for (int i = 0; i < N; i++) {
      __ str(v[i], T, Address(base, start + offsets[i]));
    }
  }

  // load N/2 pairs of quadword values from memory de-interleaved into
  // N vector registers 2 at a time via the address supplied in base
  // with each pair indexed using the the start offset plus the
  // corresponding entry in the offsets array
  template<int N>
  void vs_ld2_indexed(const VSeq<N>& v, Assembler::SIMD_Arrangement T, Register base,
                      Register tmp, int start, int (&offsets)[N/2]) {
    for (int i = 0; i < N/2; i++) {
      __ add(tmp, base, start + offsets[i]);
      __ ld2(v[2*i], v[2*i+1], T, tmp);
    }
  }

  // store N vector registers 2 at a time interleaved into N/2 pairs
  // of quadword memory locations via the address supplied in base
  // with each pair indexed using the the start offset plus the
  // corresponding entry in the offsets array
  template<int N>
  void vs_st2_indexed(const VSeq<N>& v, Assembler::SIMD_Arrangement T, Register base,
                      Register tmp, int start, int (&offsets)[N/2]) {
    for (int i = 0; i < N/2; i++) {
      __ add(tmp, base, start + offsets[i]);
      __ st2(v[2*i], v[2*i+1], T, tmp);
    }
  }

  // Helper routines for various flavours of Montgomery multiply

  // Perform 16 32-bit (4x4S) or 32 16-bit (4 x 8H) Montgomery
  // multiplications in parallel
  //

  // See the montMul() method of the sun.security.provider.ML_DSA
  // class.
  //
  // Computes 4x4S results or 8x8H results
  //    a = b * c * 2^MONT_R_BITS mod MONT_Q
  // Inputs:  vb, vc - 4x4S or 4x8H vector register sequences
  //          vq - 2x4S or 2x8H constants <MONT_Q, MONT_Q_INV_MOD_R>
  // Temps:   vtmp - 4x4S or 4x8H vector sequence trashed after call
  // Outputs: va - 4x4S or 4x8H vector register sequences
  // vb, vc, vtmp and vq must all be disjoint
  // va must be disjoint from all other inputs/temps or must equal vc
  // va must have a non-zero delta i.e. it must not be a constant vseq.
  // n.b. MONT_R_BITS is 16 or 32, so the right shift by it is implicit.
  void vs_montmul4(const VSeq<4>& va, const VSeq<4>& vb, const VSeq<4>& vc,
                   Assembler::SIMD_Arrangement T,
                   const VSeq<4>& vtmp, const VSeq<2>& vq) {
    assert (T == __ T4S || T == __ T8H, "invalid arrangement for montmul");
    assert(vs_disjoint(vb, vc), "vb and vc overlap");
    assert(vs_disjoint(vb, vq), "vb and vq overlap");
    assert(vs_disjoint(vb, vtmp), "vb and vtmp overlap");

    assert(vs_disjoint(vc, vq), "vc and vq overlap");
    assert(vs_disjoint(vc, vtmp), "vc and vtmp overlap");

    assert(vs_disjoint(vq, vtmp), "vq and vtmp overlap");

    assert(vs_disjoint(va, vc) || vs_same(va, vc), "va and vc neither disjoint nor equal");
    assert(vs_disjoint(va, vb), "va and vb overlap");
    assert(vs_disjoint(va, vq), "va and vq overlap");
    assert(vs_disjoint(va, vtmp), "va and vtmp overlap");
    assert(!va.is_constant(), "output vector must identify 4 different registers");

    // schedule 4 streams of instructions across the vector sequences
    for (int i = 0; i < 4; i++) {
      __ sqdmulh(vtmp[i], T, vb[i], vc[i]); // aHigh = hi32(2 * b * c)
      __ mulv(va[i], T, vb[i], vc[i]);    // aLow = lo32(b * c)
    }

    for (int i = 0; i < 4; i++) {
      __ mulv(va[i], T, va[i], vq[0]);     // m = aLow * qinv
    }

    for (int i = 0; i < 4; i++) {
      __ sqdmulh(va[i], T, va[i], vq[1]);  // n = hi32(2 * m * q)
    }

    for (int i = 0; i < 4; i++) {
      __ shsubv(va[i], T, vtmp[i], va[i]);   // a = (aHigh - n) / 2
    }
  }

  // Perform 8 32-bit (4x4S) or 16 16-bit (2 x 8H) Montgomery
  // multiplications in parallel
  //

  // See the montMul() method of the sun.security.provider.ML_DSA
  // class.
  //
  // Computes 4x4S results or 8x8H results
  //    a = b * c * 2^MONT_R_BITS mod MONT_Q
  // Inputs:  vb, vc - 4x4S or 4x8H vector register sequences
  //          vq - 2x4S or 2x8H constants <MONT_Q, MONT_Q_INV_MOD_R>
  // Temps:   vtmp - 4x4S or 4x8H vector sequence trashed after call
  // Outputs: va - 4x4S or 4x8H vector register sequences
  // vb, vc, vtmp and vq must all be disjoint
  // va must be disjoint from all other inputs/temps or must equal vc
  // va must have a non-zero delta i.e. it must not be a constant vseq.
  // n.b. MONT_R_BITS is 16 or 32, so the right shift by it is implicit.
  void vs_montmul2(const VSeq<2>& va, const VSeq<2>& vb, const VSeq<2>& vc,
                   Assembler::SIMD_Arrangement T,
                   const VSeq<2>& vtmp, const VSeq<2>& vq) {
    assert (T == __ T4S || T == __ T8H, "invalid arrangement for montmul");
    assert(vs_disjoint(vb, vc), "vb and vc overlap");
    assert(vs_disjoint(vb, vq), "vb and vq overlap");
    assert(vs_disjoint(vb, vtmp), "vb and vtmp overlap");

    assert(vs_disjoint(vc, vq), "vc and vq overlap");
    assert(vs_disjoint(vc, vtmp), "vc and vtmp overlap");

    assert(vs_disjoint(vq, vtmp), "vq and vtmp overlap");

    assert(vs_disjoint(va, vc) || vs_same(va, vc), "va and vc neither disjoint nor equal");
    assert(vs_disjoint(va, vb), "va and vb overlap");
    assert(vs_disjoint(va, vq), "va and vq overlap");
    assert(vs_disjoint(va, vtmp), "va and vtmp overlap");
    assert(!va.is_constant(), "output vector must identify 2 different registers");

    // schedule 2 streams of instructions across the vector sequences
    for (int i = 0; i < 2; i++) {
      __ sqdmulh(vtmp[i], T, vb[i], vc[i]); // aHigh = hi32(2 * b * c)
      __ mulv(va[i], T, vb[i], vc[i]);    // aLow = lo32(b * c)
    }

    for (int i = 0; i < 2; i++) {
      __ mulv(va[i], T, va[i], vq[0]);     // m = aLow * qinv
    }

    for (int i = 0; i < 2; i++) {
      __ sqdmulh(va[i], T, va[i], vq[1]);  // n = hi32(2 * m * q)
    }

    for (int i = 0; i < 2; i++) {
      __ shsubv(va[i], T, vtmp[i], va[i]);   // a = (aHigh - n) / 2
    }
  }

  // Perform 16 16-bit Montgomery multiplications in parallel.
  void kyber_montmul16(const VSeq<2>& va, const VSeq<2>& vb, const VSeq<2>& vc,
                       const VSeq<2>& vtmp, const VSeq<2>& vq) {
    // Use the helper routine to schedule a 2x8H Montgomery multiply.
    // It will assert that the register use is valid
    vs_montmul2(va, vb, vc, __ T8H, vtmp, vq);
  }

  // Perform 32 16-bit Montgomery multiplications in parallel.
  void kyber_montmul32(const VSeq<4>& va, const VSeq<4>& vb, const VSeq<4>& vc,
                       const VSeq<4>& vtmp, const VSeq<2>& vq) {
    // Use the helper routine to schedule a 4x8H Montgomery multiply.
    // It will assert that the register use is valid
    vs_montmul4(va, vb, vc, __ T8H, vtmp, vq);
  }

  // Perform 64 16-bit Montgomery multiplications in parallel.
  void kyber_montmul64(const VSeq<8>& va, const VSeq<8>& vb, const VSeq<8>& vc,
                       const VSeq<4>& vtmp, const VSeq<2>& vq) {
    // Schedule two successive 4x8H multiplies via the montmul helper
    // on the front and back halves of va, vb and vc. The helper will
    // assert that the register use has no overlap conflicts on each
    // individual call but we also need to ensure that the necessary
    // disjoint/equality constraints are met across both calls.

    // vb, vc, vtmp and vq must be disjoint. va must either be
    // disjoint from all other registers or equal vc

    assert(vs_disjoint(vb, vc), "vb and vc overlap");
    assert(vs_disjoint(vb, vq), "vb and vq overlap");
    assert(vs_disjoint(vb, vtmp), "vb and vtmp overlap");

    assert(vs_disjoint(vc, vq), "vc and vq overlap");
    assert(vs_disjoint(vc, vtmp), "vc and vtmp overlap");

    assert(vs_disjoint(vq, vtmp), "vq and vtmp overlap");

    assert(vs_disjoint(va, vc) || vs_same(va, vc), "va and vc neither disjoint nor equal");
    assert(vs_disjoint(va, vb), "va and vb overlap");
    assert(vs_disjoint(va, vq), "va and vq overlap");
    assert(vs_disjoint(va, vtmp), "va and vtmp overlap");

    // we multiply the front and back halves of each sequence 4 at a
    // time because
    //
    // 1) we are currently only able to get 4-way instruction
    // parallelism at best
    //
    // 2) we need registers for the constants in vq and temporary
    // scratch registers to hold intermediate results so vtmp can only
    // be a VSeq<4> which means we only have 4 scratch slots

    vs_montmul4(vs_front(va), vs_front(vb), vs_front(vc), __ T8H, vtmp, vq);
    vs_montmul4(vs_back(va), vs_back(vb), vs_back(vc), __ T8H, vtmp, vq);
  }

  void kyber_montmul32_sub_add(const VSeq<4>& va0, const VSeq<4>& va1,
                               const VSeq<4>& vc,
                               const VSeq<4>& vtmp,
                               const VSeq<2>& vq) {
    // compute a = montmul(a1, c)
    kyber_montmul32(vc, va1, vc, vtmp, vq);
    // ouptut a1 = a0 - a
    vs_subv(va1, __ T8H, va0, vc);
    //    and a0 = a0 + a
    vs_addv(va0, __ T8H, va0, vc);
  }

  void kyber_sub_add_montmul32(const VSeq<4>& va0, const VSeq<4>& va1,
                               const VSeq<4>& vb,
                               const VSeq<4>& vtmp1,
                               const VSeq<4>& vtmp2,
                               const VSeq<2>& vq) {
    // compute c = a0 - a1
    vs_subv(vtmp1, __ T8H, va0, va1);
    // output a0 = a0 + a1
    vs_addv(va0, __ T8H, va0, va1);
    // output a1 = b montmul c
    kyber_montmul32(va1, vtmp1, vb, vtmp2, vq);
  }

  void load64shorts(const VSeq<8>& v, Register shorts) {
    vs_ldpq_post(v, shorts);
  }

  void load32shorts(const VSeq<4>& v, Register shorts) {
    vs_ldpq_post(v, shorts);
  }

  void store64shorts(VSeq<8> v, Register tmpAddr) {
    vs_stpq_post(v, tmpAddr);
  }

  // Kyber NTT function.
  // Implements
  // static int implKyberNtt(short[] poly, short[] ntt_zetas) {}
  //
  // coeffs (short[256]) = c_rarg0
  // ntt_zetas (short[256]) = c_rarg1
  address generate_kyberNtt() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_kyberNtt_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register coeffs = c_rarg0;
    const Register zetas = c_rarg1;

    const Register kyberConsts = r10;
    const Register tmpAddr = r11;

    VSeq<8> vs1(0), vs2(16), vs3(24);  // 3 sets of 8x8H inputs/outputs
    VSeq<4> vtmp = vs_front(vs3);      // n.b. tmp registers overlap vs3
    VSeq<2> vq(30);                    // n.b. constants overlap vs3

    __ lea(kyberConsts, ExternalAddress((address) StubRoutines::aarch64::_kyberConsts));
    // load the montmul constants
    vs_ldpq(vq, kyberConsts);

    // Each level corresponds to an iteration of the outermost loop of the
    // Java method seilerNTT(int[] coeffs). There are some differences
    // from what is done in the seilerNTT() method, though:
    // 1. The computation is using 16-bit signed values, we do not convert them
    // to ints here.
    // 2. The zetas are delivered in a bigger array, 128 zetas are stored in
    // this array for each level, it is easier that way to fill up the vector
    // registers.
    // 3. In the seilerNTT() method we use R = 2^20 for the Montgomery
    // multiplications (this is because that way there should not be any
    // overflow during the inverse NTT computation), here we usr R = 2^16 so
    // that we can use the 16-bit arithmetic in the vector unit.
    //
    // On each level, we fill up the vector registers in such a way that the
    // array elements that need to be multiplied by the zetas go into one
    // set of vector registers while the corresponding ones that don't need to
    // be multiplied, go into another set.
    // We can do 32 Montgomery multiplications in parallel, using 12 vector
    // registers interleaving the steps of 4 identical computations,
    // each done on 8 16-bit values per register.

    // At levels 0-3 the coefficients multiplied by or added/subtracted
    // to the zetas occur in discrete blocks whose size is some multiple
    // of 32.

    // level 0
    __ add(tmpAddr, coeffs, 256);
    load64shorts(vs1, tmpAddr);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    __ add(tmpAddr, coeffs, 0);
    load64shorts(vs1, tmpAddr);
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 0);
    vs_stpq_post(vs1, tmpAddr);
    __ add(tmpAddr, coeffs, 256);
    vs_stpq_post(vs3, tmpAddr);
    // restore montmul constants
    vs_ldpq(vq, kyberConsts);
    load64shorts(vs1, tmpAddr);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    __ add(tmpAddr, coeffs, 128);
    load64shorts(vs1, tmpAddr);
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 128);
    store64shorts(vs1, tmpAddr);
    __ add(tmpAddr, coeffs, 384);
    store64shorts(vs3, tmpAddr);

    // level 1
    // restore montmul constants
    vs_ldpq(vq, kyberConsts);
    __ add(tmpAddr, coeffs, 128);
    load64shorts(vs1, tmpAddr);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    __ add(tmpAddr, coeffs, 0);
    load64shorts(vs1, tmpAddr);
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 0);
    store64shorts(vs1, tmpAddr);
    store64shorts(vs3, tmpAddr);
    vs_ldpq(vq, kyberConsts);
    __ add(tmpAddr, coeffs, 384);
    load64shorts(vs1, tmpAddr);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    __ add(tmpAddr, coeffs, 256);
    load64shorts(vs1, tmpAddr);
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 256);
    store64shorts(vs1, tmpAddr);
    store64shorts(vs3, tmpAddr);

    // level 2
    vs_ldpq(vq, kyberConsts);
    int offsets1[4] = { 0, 32, 128, 160 };
    vs_ldpq_indexed(vs1, coeffs, 64, offsets1);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_ldpq_indexed(vs1, coeffs, 0, offsets1);
    // kyber_subv_addv64();
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 0);
    vs_stpq_post(vs_front(vs1), tmpAddr);
    vs_stpq_post(vs_front(vs3), tmpAddr);
    vs_stpq_post(vs_back(vs1), tmpAddr);
    vs_stpq_post(vs_back(vs3), tmpAddr);
    vs_ldpq(vq, kyberConsts);
    vs_ldpq_indexed(vs1, tmpAddr, 64, offsets1);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_ldpq_indexed(vs1,  coeffs, 256, offsets1);
    // kyber_subv_addv64();
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 256);
    vs_stpq_post(vs_front(vs1), tmpAddr);
    vs_stpq_post(vs_front(vs3), tmpAddr);
    vs_stpq_post(vs_back(vs1), tmpAddr);
    vs_stpq_post(vs_back(vs3), tmpAddr);

    // level 3
    vs_ldpq(vq, kyberConsts);
    int offsets2[4] = { 0, 64, 128, 192 };
    vs_ldpq_indexed(vs1, coeffs, 32, offsets2);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_ldpq_indexed(vs1, coeffs, 0, offsets2);
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    vs_stpq_indexed(vs1, coeffs, 0, offsets2);
    vs_stpq_indexed(vs3, coeffs, 32, offsets2);

    vs_ldpq(vq, kyberConsts);
    vs_ldpq_indexed(vs1, coeffs, 256 + 32, offsets2);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_ldpq_indexed(vs1, coeffs, 256, offsets2);
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    vs_stpq_indexed(vs1, coeffs, 256, offsets2);
    vs_stpq_indexed(vs3, coeffs, 256 + 32, offsets2);

    // level 4
    // At level 4 coefficients occur in 8 discrete blocks of size 16
    // so they are loaded using employing an ldr at 8 distinct offsets.

    vs_ldpq(vq, kyberConsts);
    int offsets3[8] = { 0, 32, 64, 96, 128, 160, 192, 224 };
    vs_ldr_indexed(vs1, __ Q, coeffs, 16, offsets3);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_ldr_indexed(vs1, __ Q, coeffs, 0, offsets3);
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    vs_str_indexed(vs1, __ Q, coeffs, 0, offsets3);
    vs_str_indexed(vs3, __ Q, coeffs, 16, offsets3);

    vs_ldpq(vq, kyberConsts);
    vs_ldr_indexed(vs1, __ Q, coeffs, 256 + 16, offsets3);
    load64shorts(vs2, zetas);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_ldr_indexed(vs1, __ Q, coeffs, 256, offsets3);
    vs_subv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_addv(vs1, __ T8H, vs1, vs2);
    vs_str_indexed(vs1, __ Q, coeffs, 256, offsets3);
    vs_str_indexed(vs3, __ Q, coeffs, 256 + 16, offsets3);

    // level 5
    // At level 5 related coefficients occur in discrete blocks of size 8 so
    // need to be loaded interleaved using an ld2 operation with arrangement 2D.

    vs_ldpq(vq, kyberConsts);
    int offsets4[4] = { 0, 32, 64, 96 };
    vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, 0, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_montmul32_sub_add(vs_even(vs1), vs_odd(vs1), vs_front(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, 0, offsets4);
    vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, 128, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_montmul32_sub_add(vs_even(vs1), vs_odd(vs1), vs_front(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, 128, offsets4);
    vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, 256, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_montmul32_sub_add(vs_even(vs1), vs_odd(vs1), vs_front(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, 256, offsets4);

    vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, 384, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_montmul32_sub_add(vs_even(vs1), vs_odd(vs1), vs_front(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, 384, offsets4);

    // level 6
    // At level 6 related coefficients occur in discrete blocks of size 4 so
    // need to be loaded interleaved using an ld2 operation with arrangement 4S.

    vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, 0, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_montmul32_sub_add(vs_even(vs1), vs_odd(vs1), vs_front(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, 0, offsets4);
    vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, 128, offsets4);
    // __ ldpq(v18, v19, __ post(zetas, 32));
    load32shorts(vs_front(vs2), zetas);
    kyber_montmul32_sub_add(vs_even(vs1), vs_odd(vs1), vs_front(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, 128, offsets4);

    vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, 256, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_montmul32_sub_add(vs_even(vs1), vs_odd(vs1), vs_front(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, 256, offsets4);

    vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, 384, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_montmul32_sub_add(vs_even(vs1), vs_odd(vs1), vs_front(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, 384, offsets4);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // Kyber Inverse NTT function
  // Implements
  // static int implKyberInverseNtt(short[] poly, short[] zetas) {}
  //
  // coeffs (short[256]) = c_rarg0
  // ntt_zetas (short[256]) = c_rarg1
  address generate_kyberInverseNtt() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_kyberInverseNtt_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register coeffs = c_rarg0;
    const Register zetas = c_rarg1;

    const Register kyberConsts = r10;
    const Register tmpAddr = r11;
    const Register tmpAddr2 = c_rarg2;

    VSeq<8> vs1(0), vs2(16), vs3(24);  // 3 sets of 8x8H inputs/outputs
    VSeq<4> vtmp = vs_front(vs3);      // n.b. tmp registers overlap vs3
    VSeq<2> vq(30);                    // n.b. constants overlap vs3

    __ lea(kyberConsts,
             ExternalAddress((address) StubRoutines::aarch64::_kyberConsts));

    // level 0
    // At level 0 related coefficients occur in discrete blocks of size 4 so
    // need to be loaded interleaved using an ld2 operation with arrangement 4S.

    vs_ldpq(vq, kyberConsts);
    int offsets4[4] = { 0, 32, 64, 96 };
    vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, 0, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_sub_add_montmul32(vs_even(vs1), vs_odd(vs1),
                            vs_front(vs2), vs_back(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, 0, offsets4);
    vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, 128, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_sub_add_montmul32(vs_even(vs1), vs_odd(vs1),
                            vs_front(vs2), vs_back(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, 128, offsets4);
    vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, 256, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_sub_add_montmul32(vs_even(vs1), vs_odd(vs1),
                            vs_front(vs2), vs_back(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, 256, offsets4);
    vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, 384, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_sub_add_montmul32(vs_even(vs1), vs_odd(vs1),
                            vs_front(vs2), vs_back(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, 384, offsets4);

    // level 1
    // At level 1 related coefficients occur in discrete blocks of size 8 so
    // need to be loaded interleaved using an ld2 operation with arrangement 2D.

    vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, 0, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_sub_add_montmul32(vs_even(vs1), vs_odd(vs1),
                            vs_front(vs2), vs_back(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, 0, offsets4);
    vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, 128, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_sub_add_montmul32(vs_even(vs1), vs_odd(vs1),
                            vs_front(vs2), vs_back(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, 128, offsets4);

    vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, 256, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_sub_add_montmul32(vs_even(vs1), vs_odd(vs1),
                            vs_front(vs2), vs_back(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, 256, offsets4);
    vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, 384, offsets4);
    load32shorts(vs_front(vs2), zetas);
    kyber_sub_add_montmul32(vs_even(vs1), vs_odd(vs1),
                            vs_front(vs2), vs_back(vs2), vtmp, vq);
    vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, 384, offsets4);

    // level 2
    // At level 2 coefficients occur in 8 discrete blocks of size 16
    // so they are loaded using employing an ldr at 8 distinct offsets.

    int offsets3[8] = { 0, 32, 64, 96, 128, 160, 192, 224 };
    vs_ldr_indexed(vs1, __ Q, coeffs, 0, offsets3);
    vs_ldr_indexed(vs2, __ Q, coeffs, 16, offsets3);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    vs_str_indexed(vs3, __ Q, coeffs, 0, offsets3);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_str_indexed(vs2, __ Q, coeffs, 16, offsets3);

    vs_ldr_indexed(vs1, __ Q, coeffs, 256, offsets3);
    vs_ldr_indexed(vs2, __ Q, coeffs, 256 + 16, offsets3);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    vs_str_indexed(vs3, __ Q, coeffs, 256, offsets3);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_str_indexed(vs2, __ Q, coeffs, 256 + 16, offsets3);

    // Barrett reduction at indexes where overflow may happen

    // load q and the multiplier for the Barrett reduction
    __ add(tmpAddr, kyberConsts, 16);
    vs_ldpq(vq, tmpAddr);

    VSeq<8> vq1 = VSeq<8>(vq[0], 0); // 2 constant 8 sequences
    VSeq<8> vq2 = VSeq<8>(vq[1], 0); // for above two kyber constants
    VSeq<8> vq3 = VSeq<8>(v29, 0);   // 3rd sequence for const montmul
    vs_ldr_indexed(vs1, __ Q, coeffs, 0, offsets3);
    vs_sqdmulh(vs2, __ T8H, vs1, vq2);
    vs_sshr(vs2, __ T8H, vs2, 11);
    vs_mlsv(vs1, __ T8H, vs2, vq1);
    vs_str_indexed(vs1, __ Q, coeffs, 0, offsets3);
    vs_ldr_indexed(vs1, __ Q, coeffs, 256, offsets3);
    vs_sqdmulh(vs2, __ T8H, vs1, vq2);
    vs_sshr(vs2, __ T8H, vs2, 11);
    vs_mlsv(vs1, __ T8H, vs2, vq1);
    vs_str_indexed(vs1, __ Q, coeffs, 256, offsets3);

    // level 3
    // From level 3 upwards coefficients occur in discrete blocks whose size is
    // some multiple of 32 so can be loaded using ldpq and suitable indexes.

    int offsets2[4] = { 0, 64, 128, 192 };
    vs_ldpq_indexed(vs1, coeffs, 0, offsets2);
    vs_ldpq_indexed(vs2, coeffs, 32, offsets2);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    vs_stpq_indexed(vs3, coeffs, 0, offsets2);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_stpq_indexed(vs2, coeffs, 32, offsets2);

    vs_ldpq_indexed(vs1, coeffs, 256, offsets2);
    vs_ldpq_indexed(vs2, coeffs, 256 + 32, offsets2);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    vs_stpq_indexed(vs3, coeffs, 256, offsets2);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_stpq_indexed(vs2, coeffs, 256 + 32, offsets2);

    // level 4

    int offsets1[4] = { 0, 32, 128, 160 };
    vs_ldpq_indexed(vs1, coeffs, 0, offsets1);
    vs_ldpq_indexed(vs2, coeffs, 64, offsets1);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    vs_stpq_indexed(vs3, coeffs, 0, offsets1);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_stpq_indexed(vs2, coeffs, 64, offsets1);

    vs_ldpq_indexed(vs1, coeffs, 256, offsets1);
    vs_ldpq_indexed(vs2, coeffs, 256 + 64, offsets1);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    vs_stpq_indexed(vs3, coeffs, 256, offsets1);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    vs_stpq_indexed(vs2, coeffs, 256 + 64, offsets1);

    // level 5

    __ add(tmpAddr, coeffs, 0);
    load64shorts(vs1, tmpAddr);
    __ add(tmpAddr, coeffs, 128);
    load64shorts(vs2, tmpAddr);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 0);
    store64shorts(vs3, tmpAddr);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    __ add(tmpAddr, coeffs, 128);
    store64shorts(vs2, tmpAddr);

    load64shorts(vs1, tmpAddr);
    __ add(tmpAddr, coeffs, 384);
    load64shorts(vs2, tmpAddr);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 256);
    store64shorts(vs3, tmpAddr);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    __ add(tmpAddr, coeffs, 384);
    store64shorts(vs2, tmpAddr);

    // Barrett reduction at indexes where overflow may happen

    // load q and the multiplier for the Barrett reduction
    __ add(tmpAddr, kyberConsts, 16);
    vs_ldpq(vq, tmpAddr);

    int offsets0[2] = { 0, 256 };
    vs_ldpq_indexed(vs_front(vs1), coeffs, 0, offsets0);
    vs_sqdmulh(vs2, __ T8H, vs1, vq2);
    vs_sshr(vs2, __ T8H, vs2, 11);
    vs_mlsv(vs1, __ T8H, vs2, vq1);
    vs_stpq_indexed(vs_front(vs1), coeffs, 0, offsets0);

    // level 6

    __ add(tmpAddr, coeffs, 0);
    load64shorts(vs1, tmpAddr);
    __ add(tmpAddr, coeffs, 256);
    load64shorts(vs2, tmpAddr);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 0);
    store64shorts(vs3, tmpAddr);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    __ add(tmpAddr, coeffs, 256);
    store64shorts(vs2, tmpAddr);

    __ add(tmpAddr, coeffs, 128);
    load64shorts(vs1, tmpAddr);
    __ add(tmpAddr, coeffs, 384);
    load64shorts(vs2, tmpAddr);
    vs_addv(vs3, __ T8H, vs1, vs2); // n.b. trashes vq
    vs_subv(vs1, __ T8H, vs1, vs2);
    __ add(tmpAddr, coeffs, 128);
    store64shorts(vs3, tmpAddr);
    load64shorts(vs2, zetas);
    vs_ldpq(vq, kyberConsts);
    kyber_montmul64(vs2, vs1, vs2, vtmp, vq);
    __ add(tmpAddr, coeffs, 384);
    store64shorts(vs2, tmpAddr);

    // multiply by 2^-n

    // load toMont(2^-n mod q)
    __ add(tmpAddr, kyberConsts, 48);
    __ ldr(v29, __ Q, tmpAddr);

    vs_ldpq(vq, kyberConsts);
    __ add(tmpAddr, coeffs, 0);
    load64shorts(vs1, tmpAddr);
    kyber_montmul64(vs2, vs1, vq3, vtmp, vq);
    __ add(tmpAddr, coeffs, 0);
    store64shorts(vs2, tmpAddr);

    // now tmpAddr contains coeffs + 128 because store64shorts adjusted it so
    load64shorts(vs1, tmpAddr);
    kyber_montmul64(vs2, vs1, vq3, vtmp, vq);
    __ add(tmpAddr, coeffs, 128);
    store64shorts(vs2, tmpAddr);

    // now tmpAddr contains coeffs + 256
    load64shorts(vs1, tmpAddr);
    kyber_montmul64(vs2, vs1, vq3, vtmp, vq);
    __ add(tmpAddr, coeffs, 256);
    store64shorts(vs2, tmpAddr);

    // now tmpAddr contains coeffs + 384
    load64shorts(vs1, tmpAddr);
    kyber_montmul64(vs2, vs1, vq3, vtmp, vq);
    __ add(tmpAddr, coeffs, 384);
    store64shorts(vs2, tmpAddr);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // Kyber multiply polynomials in the NTT domain.
  // Implements
  // static int implKyberNttMult(
  //              short[] result, short[] ntta, short[] nttb, short[] zetas) {}
  //
  // result (short[256]) = c_rarg0
  // ntta (short[256]) = c_rarg1
  // nttb (short[256]) = c_rarg2
  // zetas (short[128]) = c_rarg3
  address generate_kyberNttMult() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_kyberNttMult_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register result = c_rarg0;
    const Register ntta = c_rarg1;
    const Register nttb = c_rarg2;
    const Register zetas = c_rarg3;

    const Register kyberConsts = r10;
    const Register limit = r11;

    VSeq<4> vs1(0), vs2(4);  // 4 sets of 8x8H inputs/outputs/tmps
    VSeq<4> vs3(16), vs4(20);
    VSeq<2> vq(30);          // pair of constants for montmul: q, qinv
    VSeq<2> vz(28);          // pair of zetas
    VSeq<4> vc(27, 0);       // constant sequence for montmul: montRSquareModQ

    __ lea(kyberConsts,
             ExternalAddress((address) StubRoutines::aarch64::_kyberConsts));

    Label kyberNttMult_loop;

    __ add(limit, result, 512);

    // load q and qinv
    vs_ldpq(vq, kyberConsts);

    // load R^2 mod q (to convert back from Montgomery representation)
    __ add(kyberConsts, kyberConsts, 64);
    __ ldr(v27, __ Q, kyberConsts);

    __ BIND(kyberNttMult_loop);

    // load 16 zetas
    vs_ldpq_post(vz, zetas);

    // load 2 sets of 32 coefficients from the two input arrays
    // interleaved as shorts. i.e. pairs of shorts adjacent in memory
    // are striped across pairs of vector registers
    vs_ld2_post(vs_front(vs1), __ T8H, ntta); // <a0, a1> x 8H
    vs_ld2_post(vs_back(vs1), __ T8H, nttb);  // <b0, b1> x 8H
    vs_ld2_post(vs_front(vs4), __ T8H, ntta); // <a2, a3> x 8H
    vs_ld2_post(vs_back(vs4), __ T8H, nttb);  // <b2, b3> x 8H

    // compute 4 montmul cross-products for pairs (a0,a1) and (b0,b1)
    // i.e. montmul the first and second halves of vs1 in order and
    // then with one sequence reversed storing the two results in vs3
    //
    // vs3[0] <- montmul(a0, b0)
    // vs3[1] <- montmul(a1, b1)
    // vs3[2] <- montmul(a0, b1)
    // vs3[3] <- montmul(a1, b0)
    kyber_montmul16(vs_front(vs3), vs_front(vs1), vs_back(vs1), vs_front(vs2), vq);
    kyber_montmul16(vs_back(vs3),
                    vs_front(vs1), vs_reverse(vs_back(vs1)), vs_back(vs2), vq);

    // compute 4 montmul cross-products for pairs (a2,a3) and (b2,b3)
    // i.e. montmul the first and second halves of vs4 in order and
    // then with one sequence reversed storing the two results in vs1
    //
    // vs1[0] <- montmul(a2, b2)
    // vs1[1] <- montmul(a3, b3)
    // vs1[2] <- montmul(a2, b3)
    // vs1[3] <- montmul(a3, b2)
    kyber_montmul16(vs_front(vs1), vs_front(vs4), vs_back(vs4), vs_front(vs2), vq);
    kyber_montmul16(vs_back(vs1),
                    vs_front(vs4), vs_reverse(vs_back(vs4)), vs_back(vs2), vq);

    // montmul result 2 of each cross-product i.e. (a1*b1, a3*b3) by a zeta.
    // We can schedule two montmuls at a time if we use a suitable vector
    // sequence <vs3[1], vs1[1]>.
    int delta = vs1[1]->encoding() - vs3[1]->encoding();
    VSeq<2> vs5(vs3[1], delta);

    // vs3[1] <- montmul(montmul(a1, b1), z0)
    // vs1[1] <- montmul(montmul(a3, b3), z1)
    kyber_montmul16(vs5, vz, vs5, vs_front(vs2), vq);

    // add results in pairs storing in vs3
    // vs3[0] <- montmul(a0, b0) + montmul(montmul(a1, b1), z0);
    // vs3[1] <- montmul(a0, b1) + montmul(a1, b0);
    vs_addv(vs_front(vs3), __ T8H, vs_even(vs3), vs_odd(vs3));

    // vs3[2] <- montmul(a2, b2) + montmul(montmul(a3, b3), z1);
    // vs3[3] <- montmul(a2, b3) + montmul(a3, b2);
    vs_addv(vs_back(vs3), __ T8H, vs_even(vs1), vs_odd(vs1));

    // vs1 <- montmul(vs3, montRSquareModQ)
    kyber_montmul32(vs1, vs3, vc, vs2, vq);

    // store back the two pairs of result vectors de-interleaved as 8H elements
    // i.e. storing each pairs of shorts striped across a register pair adjacent
    // in memory
    vs_st2_post(vs1, __ T8H, result);

    __ cmp(result, limit);
    __ br(Assembler::NE, kyberNttMult_loop);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // Kyber add 2 polynomials.
  // Implements
  // static int implKyberAddPoly(short[] result, short[] a, short[] b) {}
  //
  // result (short[256]) = c_rarg0
  // a (short[256]) = c_rarg1
  // b (short[256]) = c_rarg2
  address generate_kyberAddPoly_2() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_kyberAddPoly_2_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register result = c_rarg0;
    const Register a = c_rarg1;
    const Register b = c_rarg2;

    const Register kyberConsts = r11;

    // We sum 256 sets of values in total i.e. 32 x 8H quadwords.
    // So, we can load, add and store the data in 3 groups of 11,
    // 11 and 10 at a time i.e. we need to map sets of 10 or 11
    // registers. A further constraint is that the mapping needs
    // to skip callee saves. So, we allocate the register
    // sequences using two 8 sequences, two 2 sequences and two
    // single registers.
    VSeq<8> vs1_1(0);
    VSeq<2> vs1_2(16);
    FloatRegister vs1_3 = v28;
    VSeq<8> vs2_1(18);
    VSeq<2> vs2_2(26);
    FloatRegister vs2_3 = v29;

    // two constant vector sequences
    VSeq<8> vc_1(31, 0);
    VSeq<2> vc_2(31, 0);

    FloatRegister vc_3 = v31;
    __ lea(kyberConsts,
             ExternalAddress((address) StubRoutines::aarch64::_kyberConsts));

    __ ldr(vc_3, __ Q, Address(kyberConsts, 16)); // q
    for (int i = 0; i < 3; i++) {
      // load 80 or 88 values from a into vs1_1/2/3
      vs_ldpq_post(vs1_1, a);
      vs_ldpq_post(vs1_2, a);
      if (i < 2) {
        __ ldr(vs1_3, __ Q, __ post(a, 16));
      }
      // load 80 or 88 values from b into vs2_1/2/3
      vs_ldpq_post(vs2_1, b);
      vs_ldpq_post(vs2_2, b);
      if (i < 2) {
        __ ldr(vs2_3, __ Q, __ post(b, 16));
      }
      // sum 80 or 88 values across vs1 and vs2 into vs1
      vs_addv(vs1_1, __ T8H, vs1_1, vs2_1);
      vs_addv(vs1_2, __ T8H, vs1_2, vs2_2);
      if (i < 2) {
        __ addv(vs1_3, __ T8H, vs1_3, vs2_3);
      }
      // add constant to all 80 or 88 results
      vs_addv(vs1_1, __ T8H, vs1_1, vc_1);
      vs_addv(vs1_2, __ T8H, vs1_2, vc_2);
      if (i < 2) {
        __ addv(vs1_3, __ T8H, vs1_3, vc_3);
      }
      // store 80 or 88 values
      vs_stpq_post(vs1_1, result);
      vs_stpq_post(vs1_2, result);
      if (i < 2) {
        __ str(vs1_3, __ Q, __ post(result, 16));
      }
    }

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // Kyber add 3 polynomials.
  // Implements
  // static int implKyberAddPoly(short[] result, short[] a, short[] b, short[] c) {}
  //
  // result (short[256]) = c_rarg0
  // a (short[256]) = c_rarg1
  // b (short[256]) = c_rarg2
  // c (short[256]) = c_rarg3
  address generate_kyberAddPoly_3() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_kyberAddPoly_3_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register result = c_rarg0;
    const Register a = c_rarg1;
    const Register b = c_rarg2;
    const Register c = c_rarg3;

    const Register kyberConsts = r11;

    // As above we sum 256 sets of values in total i.e. 32 x 8H
    // quadwords.  So, we can load, add and store the data in 3
    // groups of 11, 11 and 10 at a time i.e. we need to map sets
    // of 10 or 11 registers. A further constraint is that the
    // mapping needs to skip callee saves. So, we allocate the
    // register sequences using two 8 sequences, two 2 sequences
    // and two single registers.
    VSeq<8> vs1_1(0);
    VSeq<2> vs1_2(16);
    FloatRegister vs1_3 = v28;
    VSeq<8> vs2_1(18);
    VSeq<2> vs2_2(26);
    FloatRegister vs2_3 = v29;

    // two constant vector sequences
    VSeq<8> vc_1(31, 0);
    VSeq<2> vc_2(31, 0);

    FloatRegister vc_3 = v31;

    __ lea(kyberConsts,
             ExternalAddress((address) StubRoutines::aarch64::_kyberConsts));

    __ ldr(vc_3, __ Q, Address(kyberConsts, 16)); // q
    for (int i = 0; i < 3; i++) {
      // load 80 or 88 values from a into vs1_1/2/3
      vs_ldpq_post(vs1_1, a);
      vs_ldpq_post(vs1_2, a);
      if (i < 2) {
        __ ldr(vs1_3, __ Q, __ post(a, 16));
      }
      // load 80 or 88 values from b into vs2_1/2/3
      vs_ldpq_post(vs2_1, b);
      vs_ldpq_post(vs2_2, b);
      if (i < 2) {
        __ ldr(vs2_3, __ Q, __ post(b, 16));
      }
      // sum 80 or 88 values across vs1 and vs2 into vs1
      vs_addv(vs1_1, __ T8H, vs1_1, vs2_1);
      vs_addv(vs1_2, __ T8H, vs1_2, vs2_2);
      if (i < 2) {
        __ addv(vs1_3, __ T8H, vs1_3, vs2_3);
      }
      // load 80 or 88 values from c into vs2_1/2/3
      vs_ldpq_post(vs2_1, c);
      vs_ldpq_post(vs2_2, c);
      if (i < 2) {
        __ ldr(vs2_3, __ Q, __ post(c, 16));
      }
      // sum 80 or 88 values across vs1 and vs2 into vs1
      vs_addv(vs1_1, __ T8H, vs1_1, vs2_1);
      vs_addv(vs1_2, __ T8H, vs1_2, vs2_2);
      if (i < 2) {
        __ addv(vs1_3, __ T8H, vs1_3, vs2_3);
      }
      // add constant to all 80 or 88 results
      vs_addv(vs1_1, __ T8H, vs1_1, vc_1);
      vs_addv(vs1_2, __ T8H, vs1_2, vc_2);
      if (i < 2) {
        __ addv(vs1_3, __ T8H, vs1_3, vc_3);
      }
      // store 80 or 88 values
      vs_stpq_post(vs1_1, result);
      vs_stpq_post(vs1_2, result);
      if (i < 2) {
        __ str(vs1_3, __ Q, __ post(result, 16));
      }
    }

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // Kyber parse XOF output to polynomial coefficient candidates
  // or decodePoly(12, ...).
  // Implements
  // static int implKyber12To16(
  //         byte[] condensed, int index, short[] parsed, int parsedLength) {}
  //
  // (parsedLength or (parsedLength - 48) must be divisible by 64.)
  //
  // condensed (byte[]) = c_rarg0
  // condensedIndex = c_rarg1
  // parsed (short[112 or 256]) = c_rarg2
  // parsedLength (112 or 256) = c_rarg3
  address generate_kyber12To16() {
    Label L_F00, L_loop, L_end;

    __ BIND(L_F00);
    __ emit_int64(0x0f000f000f000f00);
    __ emit_int64(0x0f000f000f000f00);

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_kyber12To16_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register condensed = c_rarg0;
    const Register condensedOffs = c_rarg1;
    const Register parsed = c_rarg2;
    const Register parsedLength = c_rarg3;

    const Register tmpAddr = r11;

    // Data is input 96 bytes at a time i.e. in groups of 6 x 16B
    // quadwords so we need a 6 vector sequence for the inputs.
    // Parsing produces 64 shorts, employing two 8 vector
    // sequences to store and combine the intermediate data.
    VSeq<6> vin(24);
    VSeq<8> va(0), vb(16);

    __ adr(tmpAddr, L_F00);
    __ ldr(v31, __ Q, tmpAddr); // 8H times 0x0f00
    __ add(condensed, condensed, condensedOffs);

    __ BIND(L_loop);
    // load 96 (6 x 16B) byte values
    vs_ld3_post(vin, __ T16B, condensed);

    // The front half of sequence vin (vin[0], vin[1] and vin[2])
    // holds 48 (16x3) contiguous bytes from memory striped
    // horizontally across each of the 16 byte lanes. Equivalently,
    // that is 16 pairs of 12-bit integers. Likewise the back half
    // holds the next 48 bytes in the same arrangement.

    // Each vector in the front half can also be viewed as a vertical
    // strip across the 16 pairs of 12 bit integers. Each byte in
    // vin[0] stores the low 8 bits of the first int in a pair. Each
    // byte in vin[1] stores the high 4 bits of the first int and the
    // low 4 bits of the second int. Each byte in vin[2] stores the
    // high 8 bits of the second int. Likewise the vectors in second
    // half.

    // Converting the data to 16-bit shorts requires first of all
    // expanding each of the 6 x 16B vectors into 6 corresponding
    // pairs of 8H vectors. Mask, shift and add operations on the
    // resulting vector pairs can be used to combine 4 and 8 bit
    // parts of related 8H vector elements.
    //
    // The middle vectors (vin[2] and vin[5]) are actually expanded
    // twice, one copy manipulated to provide the lower 4 bits
    // belonging to the first short in a pair and another copy
    // manipulated to provide the higher 4 bits belonging to the
    // second short in a pair. This is why the the vector sequences va
    // and vb used to hold the expanded 8H elements are of length 8.

    // Expand vin[0] into va[0:1], and vin[1] into va[2:3] and va[4:5]
    // n.b. target elements 2 and 3 duplicate elements 4 and 5
    __ ushll(va[0], __ T8H, vin[0], __ T8B, 0);
    __ ushll2(va[1], __ T8H, vin[0], __ T16B, 0);
    __ ushll(va[2], __ T8H, vin[1], __ T8B, 0);
    __ ushll2(va[3], __ T8H, vin[1], __ T16B, 0);
    __ ushll(va[4], __ T8H, vin[1], __ T8B, 0);
    __ ushll2(va[5], __ T8H, vin[1], __ T16B, 0);

    // likewise expand vin[3] into vb[0:1], and vin[4] into vb[2:3]
    // and vb[4:5]
    __ ushll(vb[0], __ T8H, vin[3], __ T8B, 0);
    __ ushll2(vb[1], __ T8H, vin[3], __ T16B, 0);
    __ ushll(vb[2], __ T8H, vin[4], __ T8B, 0);
    __ ushll2(vb[3], __ T8H, vin[4], __ T16B, 0);
    __ ushll(vb[4], __ T8H, vin[4], __ T8B, 0);
    __ ushll2(vb[5], __ T8H, vin[4], __ T16B, 0);

    // shift lo byte of copy 1 of the middle stripe into the high byte
    __ shl(va[2], __ T8H, va[2], 8);
    __ shl(va[3], __ T8H, va[3], 8);
    __ shl(vb[2], __ T8H, vb[2], 8);
    __ shl(vb[3], __ T8H, vb[3], 8);

    // expand vin[2] into va[6:7] and vin[5] into vb[6:7] but this
    // time pre-shifted by 4 to ensure top bits of input 12-bit int
    // are in bit positions [4..11].
    __ ushll(va[6], __ T8H, vin[2], __ T8B, 4);
    __ ushll2(va[7], __ T8H, vin[2], __ T16B, 4);
    __ ushll(vb[6], __ T8H, vin[5], __ T8B, 4);
    __ ushll2(vb[7], __ T8H, vin[5], __ T16B, 4);

    // mask hi 4 bits of the 1st 12-bit int in a pair from copy1 and
    // shift lo 4 bits of the 2nd 12-bit int in a pair to the bottom of
    // copy2
    __ andr(va[2], __ T16B, va[2], v31);
    __ andr(va[3], __ T16B, va[3], v31);
    __ ushr(va[4], __ T8H, va[4], 4);
    __ ushr(va[5], __ T8H, va[5], 4);
    __ andr(vb[2], __ T16B, vb[2], v31);
    __ andr(vb[3], __ T16B, vb[3], v31);
    __ ushr(vb[4], __ T8H, vb[4], 4);
    __ ushr(vb[5], __ T8H, vb[5], 4);

    // sum hi 4 bits and lo 8 bits of the 1st 12-bit int in each pair and
    // hi 8 bits plus lo 4 bits of the 2nd 12-bit int in each pair
    // n.b. the ordering ensures: i) inputs are consumed before they
    // are overwritten ii) the order of 16-bit results across successive
    // pairs of vectors in va and then vb reflects the order of the
    // corresponding 12-bit inputs
    __ addv(va[0], __ T8H, va[0], va[2]);
    __ addv(va[2], __ T8H, va[1], va[3]);
    __ addv(va[1], __ T8H, va[4], va[6]);
    __ addv(va[3], __ T8H, va[5], va[7]);
    __ addv(vb[0], __ T8H, vb[0], vb[2]);
    __ addv(vb[2], __ T8H, vb[1], vb[3]);
    __ addv(vb[1], __ T8H, vb[4], vb[6]);
    __ addv(vb[3], __ T8H, vb[5], vb[7]);

    // store 64 results interleaved as shorts
    vs_st2_post(vs_front(va), __ T8H, parsed);
    vs_st2_post(vs_front(vb), __ T8H, parsed);

    __ sub(parsedLength, parsedLength, 64);
    __ cmp(parsedLength, (u1)64);
    __ br(Assembler::GE, L_loop);
    __ cbz(parsedLength, L_end);

    // if anything is left it should be a final 72 bytes of input
    // i.e. a final 48 12-bit values. so we handle this by loading
    // 48 bytes into all 16B lanes of front(vin) and only 24
    // bytes into the lower 8B lane of back(vin)
    vs_ld3_post(vs_front(vin), __ T16B, condensed);
    vs_ld3(vs_back(vin), __ T8B, condensed);

    // Expand vin[0] into va[0:1], and vin[1] into va[2:3] and va[4:5]
    // n.b. target elements 2 and 3 of va duplicate elements 4 and
    // 5 and target element 2 of vb duplicates element 4.
    __ ushll(va[0], __ T8H, vin[0], __ T8B, 0);
    __ ushll2(va[1], __ T8H, vin[0], __ T16B, 0);
    __ ushll(va[2], __ T8H, vin[1], __ T8B, 0);
    __ ushll2(va[3], __ T8H, vin[1], __ T16B, 0);
    __ ushll(va[4], __ T8H, vin[1], __ T8B, 0);
    __ ushll2(va[5], __ T8H, vin[1], __ T16B, 0);

    // This time expand just the lower 8 lanes
    __ ushll(vb[0], __ T8H, vin[3], __ T8B, 0);
    __ ushll(vb[2], __ T8H, vin[4], __ T8B, 0);
    __ ushll(vb[4], __ T8H, vin[4], __ T8B, 0);

    // shift lo byte of copy 1 of the middle stripe into the high byte
    __ shl(va[2], __ T8H, va[2], 8);
    __ shl(va[3], __ T8H, va[3], 8);
    __ shl(vb[2], __ T8H, vb[2], 8);

    // expand vin[2] into va[6:7] and lower 8 lanes of vin[5] into
    // vb[6] pre-shifted by 4 to ensure top bits of the input 12-bit
    // int are in bit positions [4..11].
    __ ushll(va[6], __ T8H, vin[2], __ T8B, 4);
    __ ushll2(va[7], __ T8H, vin[2], __ T16B, 4);
    __ ushll(vb[6], __ T8H, vin[5], __ T8B, 4);

    // mask hi 4 bits of each 1st 12-bit int in pair from copy1 and
    // shift lo 4 bits of each 2nd 12-bit int in pair to bottom of
    // copy2
    __ andr(va[2], __ T16B, va[2], v31);
    __ andr(va[3], __ T16B, va[3], v31);
    __ ushr(va[4], __ T8H, va[4], 4);
    __ ushr(va[5], __ T8H, va[5], 4);
    __ andr(vb[2], __ T16B, vb[2], v31);
    __ ushr(vb[4], __ T8H, vb[4], 4);



    // sum hi 4 bits and lo 8 bits of each 1st 12-bit int in pair and
    // hi 8 bits plus lo 4 bits of each 2nd 12-bit int in pair

    // n.b. ordering ensures: i) inputs are consumed before they are
    // overwritten ii) order of 16-bit results across succsessive
    // pairs of vectors in va and then lower half of vb reflects order
    // of corresponding 12-bit inputs
    __ addv(va[0], __ T8H, va[0], va[2]);
    __ addv(va[2], __ T8H, va[1], va[3]);
    __ addv(va[1], __ T8H, va[4], va[6]);
    __ addv(va[3], __ T8H, va[5], va[7]);
    __ addv(vb[0], __ T8H, vb[0], vb[2]);
    __ addv(vb[1], __ T8H, vb[4], vb[6]);

    // store 48 results interleaved as shorts
    vs_st2_post(vs_front(va), __ T8H, parsed);
    vs_st2_post(vs_front(vs_front(vb)), __ T8H, parsed);

    __ BIND(L_end);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // Kyber Barrett reduce function.
  // Implements
  // static int implKyberBarrettReduce(short[] coeffs) {}
  //
  // coeffs (short[256]) = c_rarg0
  address generate_kyberBarrettReduce() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_kyberBarrettReduce_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register coeffs = c_rarg0;

    const Register kyberConsts = r10;
    const Register result = r11;

    // As above we process 256 sets of values in total i.e. 32 x
    // 8H quadwords. So, we can load, add and store the data in 3
    // groups of 11, 11 and 10 at a time i.e. we need to map sets
    // of 10 or 11 registers. A further constraint is that the
    // mapping needs to skip callee saves. So, we allocate the
    // register sequences using two 8 sequences, two 2 sequences
    // and two single registers.
    VSeq<8> vs1_1(0);
    VSeq<2> vs1_2(16);
    FloatRegister vs1_3 = v28;
    VSeq<8> vs2_1(18);
    VSeq<2> vs2_2(26);
    FloatRegister vs2_3 = v29;

    // we also need a pair of corresponding constant sequences

    VSeq<8> vc1_1(30, 0);
    VSeq<2> vc1_2(30, 0);
    FloatRegister vc1_3 = v30; // for kyber_q

    VSeq<8> vc2_1(31, 0);
    VSeq<2> vc2_2(31, 0);
    FloatRegister vc2_3 = v31; // for kyberBarrettMultiplier

    __ add(result, coeffs, 0);
    __ lea(kyberConsts,
             ExternalAddress((address) StubRoutines::aarch64::_kyberConsts));

    // load q and the multiplier for the Barrett reduction
    __ add(kyberConsts, kyberConsts, 16);
    __ ldpq(vc1_3, vc2_3, kyberConsts);

    for (int i = 0; i < 3; i++) {
      // load 80 or 88 coefficients
      vs_ldpq_post(vs1_1, coeffs);
      vs_ldpq_post(vs1_2, coeffs);
      if (i < 2) {
        __ ldr(vs1_3, __ Q, __ post(coeffs, 16));
      }

      // vs2 <- (2 * vs1 * kyberBarrettMultiplier) >> 16
      vs_sqdmulh(vs2_1, __ T8H, vs1_1, vc2_1);
      vs_sqdmulh(vs2_2, __ T8H, vs1_2, vc2_2);
      if (i < 2) {
        __ sqdmulh(vs2_3, __ T8H, vs1_3, vc2_3);
      }

      // vs2 <- (vs1 * kyberBarrettMultiplier) >> 26
      vs_sshr(vs2_1, __ T8H, vs2_1, 11);
      vs_sshr(vs2_2, __ T8H, vs2_2, 11);
      if (i < 2) {
        __ sshr(vs2_3, __ T8H, vs2_3, 11);
      }

      // vs1 <- vs1 - vs2 * kyber_q
      vs_mlsv(vs1_1, __ T8H, vs2_1, vc1_1);
      vs_mlsv(vs1_2, __ T8H, vs2_2, vc1_2);
      if (i < 2) {
        __ mlsv(vs1_3, __ T8H, vs2_3, vc1_3);
      }

      vs_stpq_post(vs1_1, result);
      vs_stpq_post(vs1_2, result);
      if (i < 2) {
        __ str(vs1_3, __ Q, __ post(result, 16));
      }
    }

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }


  // Dilithium-specific montmul helper routines that generate parallel
  // code for, respectively, a single 4x4s vector sequence montmul or
  // two such multiplies in a row.

  // Perform 16 32-bit Montgomery multiplications in parallel
  void dilithium_montmul16(const VSeq<4>& va, const VSeq<4>& vb, const VSeq<4>& vc,
                           const VSeq<4>& vtmp, const VSeq<2>& vq) {
    // Use the helper routine to schedule a 4x4S Montgomery multiply.
    // It will assert that the register use is valid
    vs_montmul4(va, vb, vc, __ T4S, vtmp, vq);
  }

  // Perform 2x16 32-bit Montgomery multiplications in parallel
  void dilithium_montmul32(const VSeq<8>& va, const VSeq<8>& vb, const VSeq<8>& vc,
                           const VSeq<4>& vtmp, const VSeq<2>& vq) {
    // Schedule two successive 4x4S multiplies via the montmul helper
    // on the front and back halves of va, vb and vc. The helper will
    // assert that the register use has no overlap conflicts on each
    // individual call but we also need to ensure that the necessary
    // disjoint/equality constraints are met across both calls.

    // vb, vc, vtmp and vq must be disjoint. va must either be
    // disjoint from all other registers or equal vc

    assert(vs_disjoint(vb, vc), "vb and vc overlap");
    assert(vs_disjoint(vb, vq), "vb and vq overlap");
    assert(vs_disjoint(vb, vtmp), "vb and vtmp overlap");

    assert(vs_disjoint(vc, vq), "vc and vq overlap");
    assert(vs_disjoint(vc, vtmp), "vc and vtmp overlap");

    assert(vs_disjoint(vq, vtmp), "vq and vtmp overlap");

    assert(vs_disjoint(va, vc) || vs_same(va, vc), "va and vc neither disjoint nor equal");
    assert(vs_disjoint(va, vb), "va and vb overlap");
    assert(vs_disjoint(va, vq), "va and vq overlap");
    assert(vs_disjoint(va, vtmp), "va and vtmp overlap");

    // We multiply the front and back halves of each sequence 4 at a
    // time because
    //
    // 1) we are currently only able to get 4-way instruction
    // parallelism at best
    //
    // 2) we need registers for the constants in vq and temporary
    // scratch registers to hold intermediate results so vtmp can only
    // be a VSeq<4> which means we only have 4 scratch slots.

    vs_montmul4(vs_front(va), vs_front(vb), vs_front(vc), __ T4S, vtmp, vq);
    vs_montmul4(vs_back(va), vs_back(vb), vs_back(vc), __ T4S, vtmp, vq);
  }

  // Perform combined montmul then add/sub on 4x4S vectors.
  void dilithium_montmul16_sub_add(
          const VSeq<4>& va0, const VSeq<4>& va1, const VSeq<4>& vc,
          const VSeq<4>& vtmp, const VSeq<2>& vq) {
    // compute a = montmul(a1, c)
    dilithium_montmul16(vc, va1, vc, vtmp, vq);
    // ouptut a1 = a0 - a
    vs_subv(va1, __ T4S, va0, vc);
    //    and a0 = a0 + a
    vs_addv(va0, __ T4S, va0, vc);
  }

  // Perform combined add/sub then montul on 4x4S vectors.
  void dilithium_sub_add_montmul16(
          const VSeq<4>& va0, const VSeq<4>& va1, const VSeq<4>& vb,
          const VSeq<4>& vtmp1, const VSeq<4>& vtmp2, const VSeq<2>& vq) {
    // compute c = a0 - a1
    vs_subv(vtmp1, __ T4S, va0, va1);
    // output a0 = a0 + a1
    vs_addv(va0, __ T4S, va0, va1);
    // output a1 = b montmul c
    dilithium_montmul16(va1, vtmp1, vb, vtmp2, vq);
  }

  // At these levels, the indices that correspond to the 'j's (and 'j+l's)
  // in the Java implementation come in sequences of at least 8, so we
  // can use ldpq to collect the corresponding data into pairs of vector
  // registers.
  // We collect the coefficients corresponding to the 'j+l' indexes into
  // the vector registers v0-v7, the zetas into the vector registers v16-v23
  // then we do the (Montgomery) multiplications by the zetas in parallel
  // into v16-v23, load the coeffs corresponding to the 'j' indexes into
  // v0-v7, then do the additions into v24-v31 and the subtractions into
  // v0-v7 and finally save the results back to the coeffs array.
  void dilithiumNttLevel0_4(const Register dilithiumConsts,
    const Register coeffs, const Register zetas) {
    int c1 = 0;
    int c2 = 512;
    int startIncr;
    // don't use callee save registers v8 - v15
    VSeq<8> vs1(0), vs2(16), vs3(24);  // 3 sets of 8x4s inputs/outputs
    VSeq<4> vtmp = vs_front(vs3);         // n.b. tmp registers overlap vs3
    VSeq<2> vq(30);                    // n.b. constants overlap vs3
    int offsets[4] = { 0, 32, 64, 96 };

    for (int level = 0; level < 5; level++) {
      int c1Start = c1;
      int c2Start = c2;
      if (level == 3) {
        offsets[1] = 32;
        offsets[2] = 128;
        offsets[3] = 160;
      } else if (level == 4) {
        offsets[1] = 64;
        offsets[2] = 128;
        offsets[3] = 192;
      }

      // For levels 1 - 4 we simply load 2 x 4 adjacent values at a
      // time at 4 different offsets and multiply them in order by the
      // next set of input values. So we employ indexed load and store
      // pair instructions with arrangement 4S.
      for (int i = 0; i < 4; i++) {
        // reload q and qinv
        vs_ldpq(vq, dilithiumConsts); // qInv, q
        // load 8x4S coefficients via second start pos == c2
        vs_ldpq_indexed(vs1, coeffs, c2Start, offsets);
        // load next 8x4S inputs == b
        vs_ldpq_post(vs2, zetas);
        // compute a == c2 * b mod MONT_Q
        dilithium_montmul32(vs2, vs1, vs2, vtmp, vq);
        // load 8x4s coefficients via first start pos == c1
        vs_ldpq_indexed(vs1, coeffs, c1Start, offsets);
        // compute a1 =  c1 + a
        vs_addv(vs3, __ T4S, vs1, vs2);
        // compute a2 =  c1 - a
        vs_subv(vs1, __ T4S, vs1, vs2);
        // output a1 and a2
        vs_stpq_indexed(vs3, coeffs, c1Start, offsets);
        vs_stpq_indexed(vs1, coeffs, c2Start, offsets);

        int k = 4 * level + i;

        if (k > 7) {
          startIncr = 256;
        } else if (k == 5) {
          startIncr = 384;
        } else {
          startIncr = 128;
        }

        c1Start += startIncr;
        c2Start += startIncr;
      }

      c2 /= 2;
    }
  }

  // Dilithium NTT function except for the final "normalization" to |coeff| < Q.
  // Implements the method
  // static int implDilithiumAlmostNtt(int[] coeffs, int zetas[]) {}
  // of the Java class sun.security.provider
  //
  // coeffs (int[256]) = c_rarg0
  // zetas (int[256]) = c_rarg1
  address generate_dilithiumAlmostNtt() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_dilithiumAlmostNtt_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register coeffs = c_rarg0;
    const Register zetas = c_rarg1;

    const Register tmpAddr = r9;
    const Register dilithiumConsts = r10;
    const Register result = r11;
    // don't use callee save registers v8 - v15
    VSeq<8> vs1(0), vs2(16), vs3(24);  // 3 sets of 8x4s inputs/outputs
    VSeq<4> vtmp = vs_front(vs3);         // n.b. tmp registers overlap vs3
    VSeq<2> vq(30);                    // n.b. constants overlap vs3
    int offsets[4] = { 0, 32, 64, 96};
    int offsets1[8] = { 16, 48, 80, 112, 144, 176, 208, 240 };
    int offsets2[8] = { 0, 32, 64, 96, 128, 160, 192, 224 };
    __ add(result, coeffs, 0);
    __ lea(dilithiumConsts,
             ExternalAddress((address) StubRoutines::aarch64::_dilithiumConsts));

    // Each level represents one iteration of the outer for loop of the Java version.

    // level 0-4
    dilithiumNttLevel0_4(dilithiumConsts, coeffs, zetas);

    // level 5

    // At level 5 the coefficients we need to combine with the zetas
    // are grouped in memory in blocks of size 4. So, for both sets of
    // coefficients we load 4 adjacent values at 8 different offsets
    // using an indexed ldr with register variant Q and multiply them
    // in sequence order by the next set of inputs. Likewise we store
    // the resuls using an indexed str with register variant Q.
    for (int i = 0; i < 1024; i += 256) {
      // reload constants q, qinv each iteration as they get clobbered later
      vs_ldpq(vq, dilithiumConsts); // qInv, q
      // load 32 (8x4S) coefficients via first offsets = c1
      vs_ldr_indexed(vs1, __ Q, coeffs, i, offsets1);
      // load next 32 (8x4S) inputs = b
      vs_ldpq_post(vs2, zetas);
      // a = b montul c1
      dilithium_montmul32(vs2, vs1, vs2, vtmp, vq);
      // load 32 (8x4S) coefficients via second offsets = c2
      vs_ldr_indexed(vs1, __ Q, coeffs, i, offsets2);
      // add/sub with result of multiply
      vs_addv(vs3, __ T4S, vs1, vs2);     // a1 = a - c2
      vs_subv(vs1, __ T4S, vs1, vs2);     // a0 = a + c1
      // write back new coefficients using same offsets
      vs_str_indexed(vs3, __ Q, coeffs, i, offsets2);
      vs_str_indexed(vs1, __ Q, coeffs, i, offsets1);
    }

    // level 6
    // At level 6 the coefficients we need to combine with the zetas
    // are grouped in memory in pairs, the first two being montmul
    // inputs and the second add/sub inputs. We can still implement
    // the montmul+sub+add using 4-way parallelism but only if we
    // combine the coefficients with the zetas 16 at a time. We load 8
    // adjacent values at 4 different offsets using an ld2 load with
    // arrangement 2D. That interleaves the lower and upper halves of
    // each pair of quadwords into successive vector registers. We
    // then need to montmul the 4 even elements of the coefficients
    // register sequence by the zetas in order and then add/sub the 4
    // odd elements of the coefficients register sequence. We use an
    // equivalent st2 operation to store the results back into memory
    // de-interleaved.
    for (int i = 0; i < 1024; i += 128) {
      // reload constants q, qinv each iteration as they get clobbered later
      vs_ldpq(vq, dilithiumConsts); // qInv, q
      // load interleaved 16 (4x2D) coefficients via offsets
      vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, i, offsets);
      // load next 16 (4x4S) inputs
      vs_ldpq_post(vs_front(vs2), zetas);
      // mont multiply odd elements of vs1 by vs2 and add/sub into odds/evens
      dilithium_montmul16_sub_add(vs_even(vs1), vs_odd(vs1),
                                  vs_front(vs2), vtmp, vq);
      // store interleaved 16 (4x2D) coefficients via offsets
      vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, i, offsets);
    }

    // level 7
    // At level 7 the coefficients we need to combine with the zetas
    // occur singly with montmul inputs alterating with add/sub
    // inputs. Once again we can use 4-way parallelism to combine 16
    // zetas at a time. However, we have to load 8 adjacent values at
    // 4 different offsets using an ld2 load with arrangement 4S. That
    // interleaves the the odd words of each pair into one
    // coefficients vector register and the even words of the pair
    // into the next register. We then need to montmul the 4 even
    // elements of the coefficients register sequence by the zetas in
    // order and then add/sub the 4 odd elements of the coefficients
    // register sequence. We use an equivalent st2 operation to store
    // the results back into memory de-interleaved.

    for (int i = 0; i < 1024; i += 128) {
      // reload constants q, qinv each iteration as they get clobbered later
      vs_ldpq(vq, dilithiumConsts); // qInv, q
      // load interleaved 16 (4x4S) coefficients via offsets
      vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, i, offsets);
      // load next 16 (4x4S) inputs
      vs_ldpq_post(vs_front(vs2), zetas);
      // mont multiply odd elements of vs1 by vs2 and add/sub into odds/evens
      dilithium_montmul16_sub_add(vs_even(vs1), vs_odd(vs1),
                                  vs_front(vs2), vtmp, vq);
      // store interleaved 16 (4x4S) coefficients via offsets
      vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, i, offsets);
    }
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // At these levels, the indices that correspond to the 'j's (and 'j+l's)
  // in the Java implementation come in sequences of at least 8, so we
  // can use ldpq to collect the corresponding data into pairs of vector
  // registers
  // We collect the coefficients that correspond to the 'j's into vs1
  // the coefficiets that correspond to the 'j+l's into vs2 then
  // do the additions into vs3 and the subtractions into vs1 then
  // save the result of the additions, load the zetas into vs2
  // do the (Montgomery) multiplications by zeta in parallel into vs2
  // finally save the results back to the coeffs array
  void dilithiumInverseNttLevel3_7(const Register dilithiumConsts,
    const Register coeffs, const Register zetas) {
    int c1 = 0;
    int c2 = 32;
    int startIncr;
    int offsets[4];
    VSeq<8> vs1(0), vs2(16), vs3(24);  // 3 sets of 8x4s inputs/outputs
    VSeq<4> vtmp = vs_front(vs3);      // n.b. tmp registers overlap vs3
    VSeq<2> vq(30);                    // n.b. constants overlap vs3

    offsets[0] = 0;

    for (int level = 3; level < 8; level++) {
      int c1Start = c1;
      int c2Start = c2;
      if (level == 3) {
        offsets[1] = 64;
        offsets[2] = 128;
        offsets[3] = 192;
      } else if (level == 4) {
        offsets[1] = 32;
        offsets[2] = 128;
        offsets[3] = 160;
      } else {
        offsets[1] = 32;
        offsets[2] = 64;
        offsets[3] = 96;
      }

      // For levels 3 - 7 we simply load 2 x 4 adjacent values at a
      // time at 4 different offsets and multiply them in order by the
      // next set of input values. So we employ indexed load and store
      // pair instructions with arrangement 4S.
      for (int i = 0; i < 4; i++) {
        // load v1 32 (8x4S) coefficients relative to first start index
        vs_ldpq_indexed(vs1, coeffs, c1Start, offsets);
        // load v2 32 (8x4S) coefficients relative to second start index
        vs_ldpq_indexed(vs2, coeffs, c2Start, offsets);
        // a0 = v1 + v2 -- n.b. clobbers vqs
        vs_addv(vs3, __ T4S, vs1, vs2);
        // a1 = v1 - v2
        vs_subv(vs1, __ T4S, vs1, vs2);
        // save a1 relative to first start index
        vs_stpq_indexed(vs3, coeffs, c1Start, offsets);
        // load constants q, qinv each iteration as they get clobbered above
        vs_ldpq(vq, dilithiumConsts); // qInv, q
        // load b next 32 (8x4S) inputs
        vs_ldpq_post(vs2, zetas);
        // a = a1 montmul b
        dilithium_montmul32(vs2, vs1, vs2, vtmp, vq);
        // save a relative to second start index
        vs_stpq_indexed(vs2, coeffs, c2Start, offsets);

        int k = 4 * level + i;

        if (k < 24) {
          startIncr = 256;
        } else if (k == 25) {
          startIncr = 384;
        } else {
          startIncr = 128;
        }

        c1Start += startIncr;
        c2Start += startIncr;
      }

      c2 *= 2;
    }
  }

  // Dilithium Inverse NTT function except the final mod Q division by 2^256.
  // Implements the method
  // static int implDilithiumAlmostInverseNtt(int[] coeffs, int[] zetas) {} of
  // the sun.security.provider.ML_DSA class.
  //
  // coeffs (int[256]) = c_rarg0
  // zetas (int[256]) = c_rarg1
  address generate_dilithiumAlmostInverseNtt() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_dilithiumAlmostInverseNtt_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    const Register coeffs = c_rarg0;
    const Register zetas = c_rarg1;

    const Register tmpAddr = r9;
    const Register dilithiumConsts = r10;
    const Register result = r11;
    VSeq<8> vs1(0), vs2(16), vs3(24);  // 3 sets of 8x4s inputs/outputs
    VSeq<4> vtmp = vs_front(vs3);     // n.b. tmp registers overlap vs3
    VSeq<2> vq(30);                    // n.b. constants overlap vs3
    int offsets[4] = { 0, 32, 64, 96 };
    int offsets1[8] = { 0, 32, 64, 96, 128, 160, 192, 224 };
    int offsets2[8] = { 16, 48, 80, 112, 144, 176, 208, 240 };

    __ add(result, coeffs, 0);
    __ lea(dilithiumConsts,
             ExternalAddress((address) StubRoutines::aarch64::_dilithiumConsts));

    // Each level represents one iteration of the outer for loop of the Java version

    // level 0
    // At level 0 we need to interleave adjacent quartets of
    // coefficients before we multiply and add/sub by the next 16
    // zetas just as we did for level 7 in the multiply code. So we
    // load and store the values using an ld2/st2 with arrangement 4S.
    for (int i = 0; i < 1024; i += 128) {
      // load constants q, qinv
      // n.b. this can be moved out of the loop as they do not get
      // clobbered by first two loops
      vs_ldpq(vq, dilithiumConsts); // qInv, q
      // a0/a1 load interleaved 32 (8x4S) coefficients
      vs_ld2_indexed(vs1, __ T4S, coeffs, tmpAddr, i, offsets);
      // b load next 32 (8x4S) inputs
      vs_ldpq_post(vs_front(vs2), zetas);
      // compute in parallel (a0, a1) = (a0 + a1, (a0 - a1) montmul b)
      // n.b. second half of vs2 provides temporary register storage
      dilithium_sub_add_montmul16(vs_even(vs1), vs_odd(vs1),
                                  vs_front(vs2), vs_back(vs2), vtmp, vq);
      // a0/a1 store interleaved 32 (8x4S) coefficients
      vs_st2_indexed(vs1, __ T4S, coeffs, tmpAddr, i, offsets);
    }

    // level 1
    // At level 1 we need to interleave pairs of adjacent pairs of
    // coefficients before we multiply by the next 16 zetas just as we
    // did for level 6 in the multiply code. So we load and store the
    // values an ld2/st2 with arrangement 2D.
    for (int i = 0; i < 1024; i += 128) {
      // a0/a1 load interleaved 32 (8x2D) coefficients
      vs_ld2_indexed(vs1, __ T2D, coeffs, tmpAddr, i, offsets);
      // b load next 16 (4x4S) inputs
      vs_ldpq_post(vs_front(vs2), zetas);
      // compute in parallel (a0, a1) = (a0 + a1, (a0 - a1) montmul b)
      // n.b. second half of vs2 provides temporary register storage
      dilithium_sub_add_montmul16(vs_even(vs1), vs_odd(vs1),
                                  vs_front(vs2), vs_back(vs2), vtmp, vq);
      // a0/a1 store interleaved 32 (8x2D) coefficients
      vs_st2_indexed(vs1, __ T2D, coeffs, tmpAddr, i, offsets);
    }

    // level 2
    // At level 2 coefficients come in blocks of 4. So, we load 4
    // adjacent coefficients at 8 distinct offsets for both the first
    // and second coefficient sequences, using an ldr with register
    // variant Q then combine them with next set of 32 zetas. Likewise
    // we store the results using an str with register variant Q.
    for (int i = 0; i < 1024; i += 256) {
      // c0 load 32 (8x4S) coefficients via first offsets
      vs_ldr_indexed(vs1, __ Q, coeffs, i, offsets1);
      // c1 load 32 (8x4S) coefficients via second offsets
      vs_ldr_indexed(vs2, __ Q,coeffs, i, offsets2);
      // a0 = c0 + c1  n.b. clobbers vq which overlaps vs3
      vs_addv(vs3, __ T4S, vs1, vs2);
      // c = c0 - c1
      vs_subv(vs1, __ T4S, vs1, vs2);
      // store a0 32 (8x4S) coefficients via first offsets
      vs_str_indexed(vs3, __ Q, coeffs, i, offsets1);
      // b load 32 (8x4S) next inputs
      vs_ldpq_post(vs2, zetas);
      // reload constants q, qinv -- they were clobbered earlier
      vs_ldpq(vq, dilithiumConsts); // qInv, q
      // compute a1 = b montmul c
      dilithium_montmul32(vs2, vs1, vs2, vtmp, vq);
      // store a1 32 (8x4S) coefficients via second offsets
      vs_str_indexed(vs2, __ Q, coeffs, i, offsets2);
    }

    // level 3-7
    dilithiumInverseNttLevel3_7(dilithiumConsts, coeffs, zetas);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // Dilithium multiply polynomials in the NTT domain.
  // Straightforward implementation of the method
  // static int implDilithiumNttMult(
  //              int[] result, int[] ntta, int[] nttb {} of
  // the sun.security.provider.ML_DSA class.
  //
  // result (int[256]) = c_rarg0
  // poly1 (int[256]) = c_rarg1
  // poly2 (int[256]) = c_rarg2
  address generate_dilithiumNttMult() {

        __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_dilithiumNttMult_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    Label L_loop;

    const Register result = c_rarg0;
    const Register poly1 = c_rarg1;
    const Register poly2 = c_rarg2;

    const Register dilithiumConsts = r10;
    const Register len = r11;

    VSeq<8> vs1(0), vs2(16), vs3(24);  // 3 sets of 8x4s inputs/outputs
    VSeq<4> vtmp = vs_front(vs3);         // n.b. tmp registers overlap vs3
    VSeq<2> vq(30);                    // n.b. constants overlap vs3
    VSeq<8> vrsquare(29, 0);           // for montmul by constant RSQUARE

    __ lea(dilithiumConsts,
             ExternalAddress((address) StubRoutines::aarch64::_dilithiumConsts));

    // load constants q, qinv
    vs_ldpq(vq, dilithiumConsts); // qInv, q
    // load constant rSquare into v29
    __ ldr(v29, __ Q, Address(dilithiumConsts, 48));  // rSquare

    __ mov(len, zr);
    __ add(len, len, 1024);

    __ BIND(L_loop);

    // b load 32 (8x4S) next inputs from poly1
    vs_ldpq_post(vs1, poly1);
    // c load 32 (8x4S) next inputs from poly2
    vs_ldpq_post(vs2, poly2);
    // compute a = b montmul c
    dilithium_montmul32(vs2, vs1, vs2, vtmp, vq);
    // compute a = rsquare montmul a
    dilithium_montmul32(vs2, vrsquare, vs2, vtmp, vq);
    // save a 32 (8x4S) results
    vs_stpq_post(vs2, result);

    __ sub(len, len, 128);
    __ cmp(len, (u1)128);
    __ br(Assembler::GE, L_loop);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // Dilithium Motgomery multiply an array by a constant.
  // A straightforward implementation of the method
  // static int implDilithiumMontMulByConstant(int[] coeffs, int constant) {}
  // of the sun.security.provider.MLDSA class
  //
  // coeffs (int[256]) = c_rarg0
  // constant (int) = c_rarg1
  address generate_dilithiumMontMulByConstant() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_dilithiumMontMulByConstant_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ enter();

    Label L_loop;

    const Register coeffs = c_rarg0;
    const Register constant = c_rarg1;

    const Register dilithiumConsts = r10;
    const Register result = r11;
    const Register len = r12;

    VSeq<8> vs1(0), vs2(16), vs3(24);  // 3 sets of 8x4s inputs/outputs
    VSeq<4> vtmp = vs_front(vs3);      // n.b. tmp registers overlap vs3
    VSeq<2> vq(30);                    // n.b. constants overlap vs3
    VSeq<8> vconst(29, 0);             // for montmul by constant

    // results track inputs
    __ add(result, coeffs, 0);
    __ lea(dilithiumConsts,
             ExternalAddress((address) StubRoutines::aarch64::_dilithiumConsts));

    // load constants q, qinv -- they do not get clobbered by first two loops
    vs_ldpq(vq, dilithiumConsts); // qInv, q
    // copy caller supplied constant across vconst
    __ dup(vconst[0], __ T4S, constant);
    __ mov(len, zr);
    __ add(len, len, 1024);

    __ BIND(L_loop);

    // load next 32 inputs
    vs_ldpq_post(vs2, coeffs);
    // mont mul by constant
    dilithium_montmul32(vs2, vconst, vs2, vtmp, vq);
    // write next 32 results
    vs_stpq_post(vs2, result);

    __ sub(len, len, 128);
    __ cmp(len, (u1)128);
    __ br(Assembler::GE, L_loop);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  // Dilithium decompose poly.
  // Implements the method
  // static int implDilithiumDecomposePoly(int[] coeffs, int constant) {}
  // of the sun.security.provider.ML_DSA class
  //
  // input (int[256]) = c_rarg0
  // lowPart (int[256]) = c_rarg1
  // highPart (int[256]) = c_rarg2
  // twoGamma2  (int) = c_rarg3
  // multiplier (int) = c_rarg4
  address generate_dilithiumDecomposePoly() {

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_dilithiumDecomposePoly_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    Label L_loop;

    const Register input = c_rarg0;
    const Register lowPart = c_rarg1;
    const Register highPart = c_rarg2;
    const Register twoGamma2 = c_rarg3;
    const Register multiplier = c_rarg4;

    const Register len = r9;
    const Register dilithiumConsts = r10;
    const Register tmp = r11;

    // 6 independent sets of 4x4s values
    VSeq<4> vs1(0), vs2(4), vs3(8);
    VSeq<4> vs4(12), vs5(16), vtmp(20);

    // 7 constants for cross-multiplying
    VSeq<4> one(25, 0);
    VSeq<4> qminus1(26, 0);
    VSeq<4> g2(27, 0);
    VSeq<4> twog2(28, 0);
    VSeq<4> mult(29, 0);
    VSeq<4> q(30, 0);
    VSeq<4> qadd(31, 0);

    __ enter();

    __ lea(dilithiumConsts,
             ExternalAddress((address) StubRoutines::aarch64::_dilithiumConsts));

    // save callee-saved registers
    __ stpd(v8, v9, __ pre(sp, -64));
    __ stpd(v10, v11, Address(sp, 16));
    __ stpd(v12, v13, Address(sp, 32));
    __ stpd(v14, v15, Address(sp, 48));

    // populate constant registers
    __ mov(tmp, zr);
    __ add(tmp, tmp, 1);
    __ dup(one[0], __ T4S, tmp); // 1
    __ ldr(q[0], __ Q, Address(dilithiumConsts, 16)); // q
    __ ldr(qadd[0], __ Q, Address(dilithiumConsts, 64)); // addend for mod q reduce
    __ dup(twog2[0], __ T4S, twoGamma2); // 2 * gamma2
    __ dup(mult[0], __ T4S, multiplier); // multiplier for mod 2 * gamma reduce
    __ subv(qminus1[0], __ T4S, v30, v25); // q - 1
    __ sshr(g2[0], __ T4S, v28, 1); // gamma2

    __ mov(len, zr);
    __ add(len, len, 1024);

    __ BIND(L_loop);

    // load next 4x4S inputs interleaved: rplus --> vs1
    __ ld4(vs1[0], vs1[1], vs1[2], vs1[3], __ T4S, __ post(input, 64));

    //  rplus = rplus - ((rplus + qadd) >> 23) * q
    vs_addv(vtmp, __ T4S, vs1, qadd);
    vs_sshr(vtmp, __ T4S, vtmp, 23);
    vs_mulv(vtmp, __ T4S, vtmp, q);
    vs_subv(vs1, __ T4S, vs1, vtmp);

    // rplus = rplus + ((rplus >> 31) & dilithium_q);
    vs_sshr(vtmp, __ T4S, vs1, 31);
    vs_andr(vtmp, vtmp, q);
    vs_addv(vs1, __ T4S, vs1, vtmp);

    // quotient --> vs2
    // int quotient = (rplus * multiplier) >> 22;
    vs_mulv(vtmp, __ T4S, vs1, mult);
    vs_sshr(vs2, __ T4S, vtmp, 22);

    // r0 --> vs3
    // int r0 = rplus - quotient * twoGamma2;
    vs_mulv(vtmp, __ T4S, vs2, twog2);
    vs_subv(vs3, __ T4S, vs1, vtmp);

    // mask --> vs4
    // int mask = (twoGamma2 - r0) >> 22;
    vs_subv(vtmp, __ T4S, twog2, vs3);
    vs_sshr(vs4, __ T4S, vtmp, 22);

    // r0 -= (mask & twoGamma2);
    vs_andr(vtmp, vs4, twog2);
    vs_subv(vs3, __ T4S, vs3, vtmp);

    //  quotient += (mask & 1);
    vs_andr(vtmp, vs4, one);
    vs_addv(vs2, __ T4S, vs2, vtmp);

    // mask = (twoGamma2 / 2 - r0) >> 31;
    vs_subv(vtmp, __ T4S, g2, vs3);
    vs_sshr(vs4, __ T4S, vtmp, 31);

    // r0 -= (mask & twoGamma2);
    vs_andr(vtmp, vs4, twog2);
    vs_subv(vs3, __ T4S, vs3, vtmp);

    // quotient += (mask & 1);
    vs_andr(vtmp, vs4, one);
    vs_addv(vs2, __ T4S, vs2, vtmp);

    // r1 --> vs5
    // int r1 = rplus - r0 - (dilithium_q - 1);
    vs_subv(vtmp, __ T4S, vs1, vs3);
    vs_subv(vs5, __ T4S, vtmp, qminus1);

    // r1 --> vs1 (overwriting rplus)
    // r1 = (r1 | (-r1)) >> 31; // 0 if rplus - r0 == (dilithium_q - 1), -1 otherwise
    vs_negr(vtmp, __ T4S, vs5);
    vs_orr(vtmp, vs5, vtmp);
    vs_sshr(vs1, __ T4S, vtmp, 31);

    // r0 += ~r1;
    vs_notr(vtmp, vs1);
    vs_addv(vs3, __ T4S, vs3, vtmp);

    // r1 = r1 & quotient;
    vs_andr(vs1, vs2, vs1);

    // store results inteleaved
    // lowPart[m] = r0;
    // highPart[m] = r1;
    __ st4(vs3[0], vs3[1], vs3[2], vs3[3], __ T4S, __ post(lowPart, 64));
    __ st4(vs1[0], vs1[1], vs1[2], vs1[3], __ T4S, __ post(highPart, 64));

    __ sub(len, len, 64);
    __ cmp(len, (u1)64);
    __ br(Assembler::GE, L_loop);

    // restore callee-saved vector registers
    __ ldpd(v14, v15, Address(sp, 48));
    __ ldpd(v12, v13, Address(sp, 32));
    __ ldpd(v10, v11, Address(sp, 16));
    __ ldpd(v8, v9, __ post(sp, 64));

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ mov(r0, zr); // return 0
    __ ret(lr);

    return start;
  }

  void bcax5(Register a0, Register a1, Register a2, Register a3, Register a4,
             Register tmp0, Register tmp1, Register tmp2) {
    __ bic(tmp0, a2, a1); // for a0
    __ bic(tmp1, a3, a2); // for a1
    __ bic(tmp2, a4, a3); // for a2
    __ eor(a2, a2, tmp2);
    __ bic(tmp2, a0, a4); // for a3
    __ eor(a3, a3, tmp2);
    __ bic(tmp2, a1, a0); // for a4
    __ eor(a0, a0, tmp0);
    __ eor(a1, a1, tmp1);
    __ eor(a4, a4, tmp2);
  }

  void keccak_round_gpr(bool can_use_fp, bool can_use_r18, Register rc,
                        Register a0, Register a1, Register a2, Register a3, Register a4,
                        Register a5, Register a6, Register a7, Register a8, Register a9,
                        Register a10, Register a11, Register a12, Register a13, Register a14,
                        Register a15, Register a16, Register a17, Register a18, Register a19,
                        Register a20, Register a21, Register a22, Register a23, Register a24,
                        Register tmp0, Register tmp1, Register tmp2) {
    __ eor3(tmp1, a4, a9, a14);
    __ eor3(tmp0, tmp1, a19, a24); // tmp0 = a4^a9^a14^a19^a24 = c4
    __ eor3(tmp2, a1, a6, a11);
    __ eor3(tmp1, tmp2, a16, a21); // tmp1 = a1^a6^a11^a16^a21 = c1
    __ rax1(tmp2, tmp0, tmp1); // d0
    {

      Register tmp3, tmp4;
      if (can_use_fp && can_use_r18) {
        tmp3 = rfp;
        tmp4 = r18_tls;
      } else {
        tmp3 = a4;
        tmp4 = a9;
        __ stp(tmp3, tmp4, __ pre(sp, -16));
      }

      __ eor3(tmp3, a0, a5, a10);
      __ eor3(tmp4, tmp3, a15, a20); // tmp4 = a0^a5^a10^a15^a20 = c0
      __ eor(a0, a0, tmp2);
      __ eor(a5, a5, tmp2);
      __ eor(a10, a10, tmp2);
      __ eor(a15, a15, tmp2);
      __ eor(a20, a20, tmp2); // d0(tmp2)
      __ eor3(tmp3, a2, a7, a12);
      __ eor3(tmp2, tmp3, a17, a22); // tmp2 = a2^a7^a12^a17^a22 = c2
      __ rax1(tmp3, tmp4, tmp2); // d1
      __ eor(a1, a1, tmp3);
      __ eor(a6, a6, tmp3);
      __ eor(a11, a11, tmp3);
      __ eor(a16, a16, tmp3);
      __ eor(a21, a21, tmp3); // d1(tmp3)
      __ rax1(tmp3, tmp2, tmp0); // d3
      __ eor3(tmp2, a3, a8, a13);
      __ eor3(tmp0, tmp2, a18, a23);  // tmp0 = a3^a8^a13^a18^a23 = c3
      __ eor(a3, a3, tmp3);
      __ eor(a8, a8, tmp3);
      __ eor(a13, a13, tmp3);
      __ eor(a18, a18, tmp3);
      __ eor(a23, a23, tmp3);
      __ rax1(tmp2, tmp1, tmp0); // d2
      __ eor(a2, a2, tmp2);
      __ eor(a7, a7, tmp2);
      __ eor(a12, a12, tmp2);
      __ rax1(tmp0, tmp0, tmp4); // d4
      if (!can_use_fp || !can_use_r18) {
        __ ldp(tmp3, tmp4, __ post(sp, 16));
      }
      __ eor(a17, a17, tmp2);
      __ eor(a22, a22, tmp2);
      __ eor(a4, a4, tmp0);
      __ eor(a9, a9, tmp0);
      __ eor(a14, a14, tmp0);
      __ eor(a19, a19, tmp0);
      __ eor(a24, a24, tmp0);
    }

    __ rol(tmp0, a10, 3);
    __ rol(a10, a1, 1);
    __ rol(a1, a6, 44);
    __ rol(a6, a9, 20);
    __ rol(a9, a22, 61);
    __ rol(a22, a14, 39);
    __ rol(a14, a20, 18);
    __ rol(a20, a2, 62);
    __ rol(a2, a12, 43);
    __ rol(a12, a13, 25);
    __ rol(a13, a19, 8) ;
    __ rol(a19, a23, 56);
    __ rol(a23, a15, 41);
    __ rol(a15, a4, 27);
    __ rol(a4, a24, 14);
    __ rol(a24, a21, 2);
    __ rol(a21, a8, 55);
    __ rol(a8, a16, 45);
    __ rol(a16, a5, 36);
    __ rol(a5, a3, 28);
    __ rol(a3, a18, 21);
    __ rol(a18, a17, 15);
    __ rol(a17, a11, 10);
    __ rol(a11, a7, 6);
    __ mov(a7, tmp0);

    bcax5(a0, a1, a2, a3, a4, tmp0, tmp1, tmp2);
    bcax5(a5, a6, a7, a8, a9, tmp0, tmp1, tmp2);
    bcax5(a10, a11, a12, a13, a14, tmp0, tmp1, tmp2);
    bcax5(a15, a16, a17, a18, a19, tmp0, tmp1, tmp2);
    bcax5(a20, a21, a22, a23, a24, tmp0, tmp1, tmp2);

    __ ldr(tmp1, __ post(rc, 8));
    __ eor(a0, a0, tmp1);

  }

  // Arguments:
  //
  // Inputs:
  //   c_rarg0   - byte[]  source+offset
  //   c_rarg1   - byte[]  SHA.state
  //   c_rarg2   - int     block_size
  //   c_rarg3   - int     offset
  //   c_rarg4   - int     limit
  //
  address generate_sha3_implCompress_gpr(StubId stub_id) {
    bool multi_block;
    switch (stub_id) {
    case StubId::stubgen_sha3_implCompress_id:
      multi_block = false;
      break;
    case StubId::stubgen_sha3_implCompressMB_id:
      multi_block = true;
      break;
    default:
      ShouldNotReachHere();
    }

    static const uint64_t round_consts[24] = {
      0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL,
      0x8000000080008000L, 0x000000000000808BL, 0x0000000080000001L,
      0x8000000080008081L, 0x8000000000008009L, 0x000000000000008AL,
      0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
      0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L,
      0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
      0x000000000000800AL, 0x800000008000000AL, 0x8000000080008081L,
      0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    };

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Register buf           = c_rarg0;
    Register state         = c_rarg1;
    Register block_size    = c_rarg2;
    Register ofs           = c_rarg3;
    Register limit         = c_rarg4;

    // use r3.r17,r19..r28 to keep a0..a24.
    // a0..a24 are respective locals from SHA3.java
    Register a0 = r25,
             a1 = r26,
             a2 = r27,
             a3 = r3,
             a4 = r4,
             a5 = r5,
             a6 = r6,
             a7 = r7,
             a8 = rscratch1, // r8
             a9 = rscratch2, // r9
             a10 = r10,
             a11 = r11,
             a12 = r12,
             a13 = r13,
             a14 = r14,
             a15 = r15,
             a16 = r16,
             a17 = r17,
             a18 = r28,
             a19 = r19,
             a20 = r20,
             a21 = r21,
             a22 = r22,
             a23 = r23,
             a24 = r24;

    Register tmp0 = block_size, tmp1 = buf, tmp2 = state, tmp3 = r30;

    Label sha3_loop, rounds24_preloop, loop_body;
    Label sha3_512_or_sha3_384, shake128;

    bool can_use_r18 = false;
#ifndef R18_RESERVED
    can_use_r18 = true;
#endif
    bool can_use_fp = !PreserveFramePointer;

    __ enter();

    // save almost all yet unsaved gpr registers on stack
    __ str(block_size, __ pre(sp, -128));
    if (multi_block) {
      __ stpw(ofs, limit, Address(sp, 8));
    }
    // 8 bytes at sp+16 will be used to keep buf
    __ stp(r19, r20, Address(sp, 32));
    __ stp(r21, r22, Address(sp, 48));
    __ stp(r23, r24, Address(sp, 64));
    __ stp(r25, r26, Address(sp, 80));
    __ stp(r27, r28, Address(sp, 96));
    if (can_use_r18 && can_use_fp) {
      __ stp(r18_tls, state, Address(sp, 112));
    } else {
      __ str(state, Address(sp, 112));
    }

    // begin sha3 calculations: loading a0..a24 from state arrary
    __ ldp(a0, a1, state);
    __ ldp(a2, a3, Address(state, 16));
    __ ldp(a4, a5, Address(state, 32));
    __ ldp(a6, a7, Address(state, 48));
    __ ldp(a8, a9, Address(state, 64));
    __ ldp(a10, a11, Address(state, 80));
    __ ldp(a12, a13, Address(state, 96));
    __ ldp(a14, a15, Address(state, 112));
    __ ldp(a16, a17, Address(state, 128));
    __ ldp(a18, a19, Address(state, 144));
    __ ldp(a20, a21, Address(state, 160));
    __ ldp(a22, a23, Address(state, 176));
    __ ldr(a24, Address(state, 192));

    __ BIND(sha3_loop);

    // load input
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a0, a0, tmp3);
    __ eor(a1, a1, tmp2);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a2, a2, tmp3);
    __ eor(a3, a3, tmp2);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a4, a4, tmp3);
    __ eor(a5, a5, tmp2);
    __ ldr(tmp3, __ post(buf, 8));
    __ eor(a6, a6, tmp3);

    // block_size == 72, SHA3-512; block_size == 104, SHA3-384
    __ tbz(block_size, 7, sha3_512_or_sha3_384);

    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a7, a7, tmp3);
    __ eor(a8, a8, tmp2);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a9, a9, tmp3);
    __ eor(a10, a10, tmp2);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a11, a11, tmp3);
    __ eor(a12, a12, tmp2);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a13, a13, tmp3);
    __ eor(a14, a14, tmp2);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a15, a15, tmp3);
    __ eor(a16, a16, tmp2);

    // block_size == 136, bit4 == 0 and bit5 == 0, SHA3-256 or SHAKE256
    __ andw(tmp2, block_size, 48);
    __ cbzw(tmp2, rounds24_preloop);
    __ tbnz(block_size, 5, shake128);
    // block_size == 144, bit5 == 0, SHA3-244
    __ ldr(tmp3, __ post(buf, 8));
    __ eor(a17, a17, tmp3);
    __ b(rounds24_preloop);

    __ BIND(shake128);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a17, a17, tmp3);
    __ eor(a18, a18, tmp2);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a19, a19, tmp3);
    __ eor(a20, a20, tmp2);
    __ b(rounds24_preloop); // block_size == 168, SHAKE128

    __ BIND(sha3_512_or_sha3_384);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a7, a7, tmp3);
    __ eor(a8, a8, tmp2);
    __ tbz(block_size, 5, rounds24_preloop); // SHA3-512

    // SHA3-384
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a9, a9, tmp3);
    __ eor(a10, a10, tmp2);
    __ ldp(tmp3, tmp2, __ post(buf, 16));
    __ eor(a11, a11, tmp3);
    __ eor(a12, a12, tmp2);

    __ BIND(rounds24_preloop);
    __ fmovs(v0, 24.0); // float loop counter,
    __ fmovs(v1, 1.0);  // exact representation

    __ str(buf, Address(sp, 16));
    __ lea(tmp3, ExternalAddress((address) round_consts));

    __ BIND(loop_body);
    keccak_round_gpr(can_use_fp, can_use_r18, tmp3,
                     a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12,
                     a13, a14, a15, a16, a17, a18, a19, a20, a21, a22, a23, a24,
                     tmp0, tmp1, tmp2);
    __ fsubs(v0, v0, v1);
    __ fcmps(v0, 0.0);
    __ br(__ NE, loop_body);

    if (multi_block) {
      __ ldrw(block_size, sp); // block_size
      __ ldpw(tmp2, tmp1, Address(sp, 8)); // offset, limit
      __ addw(tmp2, tmp2, block_size);
      __ cmpw(tmp2, tmp1);
      __ strw(tmp2, Address(sp, 8)); // store offset in case we're jumping
      __ ldr(buf, Address(sp, 16)); // restore buf in case we're jumping
      __ br(Assembler::LE, sha3_loop);
      __ movw(c_rarg0, tmp2); // return offset
    }
    if (can_use_fp && can_use_r18) {
      __ ldp(r18_tls, state, Address(sp, 112));
    } else {
      __ ldr(state, Address(sp, 112));
    }
    // save calculated sha3 state
    __ stp(a0, a1, Address(state));
    __ stp(a2, a3, Address(state, 16));
    __ stp(a4, a5, Address(state, 32));
    __ stp(a6, a7, Address(state, 48));
    __ stp(a8, a9, Address(state, 64));
    __ stp(a10, a11, Address(state, 80));
    __ stp(a12, a13, Address(state, 96));
    __ stp(a14, a15, Address(state, 112));
    __ stp(a16, a17, Address(state, 128));
    __ stp(a18, a19, Address(state, 144));
    __ stp(a20, a21, Address(state, 160));
    __ stp(a22, a23, Address(state, 176));
    __ str(a24, Address(state, 192));

    // restore required registers from stack
    __ ldp(r19, r20, Address(sp, 32));
    __ ldp(r21, r22, Address(sp, 48));
    __ ldp(r23, r24, Address(sp, 64));
    __ ldp(r25, r26, Address(sp, 80));
    __ ldp(r27, r28, Address(sp, 96));
    if (can_use_fp && can_use_r18) {
      __ add(rfp, sp, 128); // leave() will copy rfp to sp below
    } // else no need to recalculate rfp, since it wasn't changed

    __ leave();

    __ ret(lr);

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
  address generate_updateBytesCRC32() {
    assert(UseCRC32Intrinsics, "what are we doing here?");

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_updateBytesCRC32_id;
    StubCodeMark mark(this, stub_id);

    address start = __ pc();

    const Register crc   = c_rarg0;  // crc
    const Register buf   = c_rarg1;  // source java byte array address
    const Register len   = c_rarg2;  // length
    const Register table0 = c_rarg3; // crc_table address
    const Register table1 = c_rarg4;
    const Register table2 = c_rarg5;
    const Register table3 = c_rarg6;
    const Register tmp3 = c_rarg7;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame

    __ kernel_crc32(crc, buf, len,
              table0, table1, table2, table3, rscratch1, rscratch2, tmp3);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(lr);

    return start;
  }

  /**
   *  Arguments:
   *
   * Inputs:
   *   c_rarg0   - int crc
   *   c_rarg1   - byte* buf
   *   c_rarg2   - int length
   *   c_rarg3   - int* table
   *
   * Output:
   *       r0   - int crc result
   */
  address generate_updateBytesCRC32C() {
    assert(UseCRC32CIntrinsics, "what are we doing here?");

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_updateBytesCRC32C_id;
    StubCodeMark mark(this, stub_id);

    address start = __ pc();

    const Register crc   = c_rarg0;  // crc
    const Register buf   = c_rarg1;  // source java byte array address
    const Register len   = c_rarg2;  // length
    const Register table0 = c_rarg3; // crc_table address
    const Register table1 = c_rarg4;
    const Register table2 = c_rarg5;
    const Register table3 = c_rarg6;
    const Register tmp3 = c_rarg7;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame

    __ kernel_crc32c(crc, buf, len,
              table0, table1, table2, table3, rscratch1, rscratch2, tmp3);

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(lr);

    return start;
  }

  /***
   *  Arguments:
   *
   *  Inputs:
   *   c_rarg0   - int   adler
   *   c_rarg1   - byte* buff
   *   c_rarg2   - int   len
   *
   * Output:
   *   c_rarg0   - int adler result
   */
  address generate_updateBytesAdler32() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_updateBytesAdler32_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Label L_simple_by1_loop, L_nmax, L_nmax_loop, L_by16, L_by16_loop, L_by1_loop, L_do_mod, L_combine, L_by1;

    // Aliases
    Register adler  = c_rarg0;
    Register s1     = c_rarg0;
    Register s2     = c_rarg3;
    Register buff   = c_rarg1;
    Register len    = c_rarg2;
    Register nmax  = r4;
    Register base  = r5;
    Register count = r6;
    Register temp0 = rscratch1;
    Register temp1 = rscratch2;
    FloatRegister vbytes = v0;
    FloatRegister vs1acc = v1;
    FloatRegister vs2acc = v2;
    FloatRegister vtable = v3;

    // Max number of bytes we can process before having to take the mod
    // 0x15B0 is 5552 in decimal, the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
    uint64_t BASE = 0xfff1;
    uint64_t NMAX = 0x15B0;

    __ mov(base, BASE);
    __ mov(nmax, NMAX);

    // Load accumulation coefficients for the upper 16 bits
    __ lea(temp0, ExternalAddress((address) StubRoutines::aarch64::_adler_table));
    __ ld1(vtable, __ T16B, Address(temp0));

    // s1 is initialized to the lower 16 bits of adler
    // s2 is initialized to the upper 16 bits of adler
    __ ubfx(s2, adler, 16, 16);  // s2 = ((adler >> 16) & 0xffff)
    __ uxth(s1, adler);          // s1 = (adler & 0xffff)

    // The pipelined loop needs at least 16 elements for 1 iteration
    // It does check this, but it is more effective to skip to the cleanup loop
    __ cmp(len, (u1)16);
    __ br(Assembler::HS, L_nmax);
    __ cbz(len, L_combine);

    __ bind(L_simple_by1_loop);
    __ ldrb(temp0, Address(__ post(buff, 1)));
    __ add(s1, s1, temp0);
    __ add(s2, s2, s1);
    __ subs(len, len, 1);
    __ br(Assembler::HI, L_simple_by1_loop);

    // s1 = s1 % BASE
    __ subs(temp0, s1, base);
    __ csel(s1, temp0, s1, Assembler::HS);

    // s2 = s2 % BASE
    __ lsr(temp0, s2, 16);
    __ lsl(temp1, temp0, 4);
    __ sub(temp1, temp1, temp0);
    __ add(s2, temp1, s2, ext::uxth);

    __ subs(temp0, s2, base);
    __ csel(s2, temp0, s2, Assembler::HS);

    __ b(L_combine);

    __ bind(L_nmax);
    __ subs(len, len, nmax);
    __ sub(count, nmax, 16);
    __ br(Assembler::LO, L_by16);

    __ bind(L_nmax_loop);

    generate_updateBytesAdler32_accum(s1, s2, buff, temp0, temp1,
                                      vbytes, vs1acc, vs2acc, vtable);

    __ subs(count, count, 16);
    __ br(Assembler::HS, L_nmax_loop);

    // s1 = s1 % BASE
    __ lsr(temp0, s1, 16);
    __ lsl(temp1, temp0, 4);
    __ sub(temp1, temp1, temp0);
    __ add(temp1, temp1, s1, ext::uxth);

    __ lsr(temp0, temp1, 16);
    __ lsl(s1, temp0, 4);
    __ sub(s1, s1, temp0);
    __ add(s1, s1, temp1, ext:: uxth);

    __ subs(temp0, s1, base);
    __ csel(s1, temp0, s1, Assembler::HS);

    // s2 = s2 % BASE
    __ lsr(temp0, s2, 16);
    __ lsl(temp1, temp0, 4);
    __ sub(temp1, temp1, temp0);
    __ add(temp1, temp1, s2, ext::uxth);

    __ lsr(temp0, temp1, 16);
    __ lsl(s2, temp0, 4);
    __ sub(s2, s2, temp0);
    __ add(s2, s2, temp1, ext:: uxth);

    __ subs(temp0, s2, base);
    __ csel(s2, temp0, s2, Assembler::HS);

    __ subs(len, len, nmax);
    __ sub(count, nmax, 16);
    __ br(Assembler::HS, L_nmax_loop);

    __ bind(L_by16);
    __ adds(len, len, count);
    __ br(Assembler::LO, L_by1);

    __ bind(L_by16_loop);

    generate_updateBytesAdler32_accum(s1, s2, buff, temp0, temp1,
                                      vbytes, vs1acc, vs2acc, vtable);

    __ subs(len, len, 16);
    __ br(Assembler::HS, L_by16_loop);

    __ bind(L_by1);
    __ adds(len, len, 15);
    __ br(Assembler::LO, L_do_mod);

    __ bind(L_by1_loop);
    __ ldrb(temp0, Address(__ post(buff, 1)));
    __ add(s1, temp0, s1);
    __ add(s2, s2, s1);
    __ subs(len, len, 1);
    __ br(Assembler::HS, L_by1_loop);

    __ bind(L_do_mod);
    // s1 = s1 % BASE
    __ lsr(temp0, s1, 16);
    __ lsl(temp1, temp0, 4);
    __ sub(temp1, temp1, temp0);
    __ add(temp1, temp1, s1, ext::uxth);

    __ lsr(temp0, temp1, 16);
    __ lsl(s1, temp0, 4);
    __ sub(s1, s1, temp0);
    __ add(s1, s1, temp1, ext:: uxth);

    __ subs(temp0, s1, base);
    __ csel(s1, temp0, s1, Assembler::HS);

    // s2 = s2 % BASE
    __ lsr(temp0, s2, 16);
    __ lsl(temp1, temp0, 4);
    __ sub(temp1, temp1, temp0);
    __ add(temp1, temp1, s2, ext::uxth);

    __ lsr(temp0, temp1, 16);
    __ lsl(s2, temp0, 4);
    __ sub(s2, s2, temp0);
    __ add(s2, s2, temp1, ext:: uxth);

    __ subs(temp0, s2, base);
    __ csel(s2, temp0, s2, Assembler::HS);

    // Combine lower bits and higher bits
    __ bind(L_combine);
    __ orr(s1, s1, s2, Assembler::LSL, 16); // adler = s1 | (s2 << 16)

    __ ret(lr);

    return start;
  }

  void generate_updateBytesAdler32_accum(Register s1, Register s2, Register buff,
          Register temp0, Register temp1, FloatRegister vbytes,
          FloatRegister vs1acc, FloatRegister vs2acc, FloatRegister vtable) {
    // Below is a vectorized implementation of updating s1 and s2 for 16 bytes.
    // We use b1, b2, ..., b16 to denote the 16 bytes loaded in each iteration.
    // In non-vectorized code, we update s1 and s2 as:
    //   s1 <- s1 + b1
    //   s2 <- s2 + s1
    //   s1 <- s1 + b2
    //   s2 <- s2 + b1
    //   ...
    //   s1 <- s1 + b16
    //   s2 <- s2 + s1
    // Putting above assignments together, we have:
    //   s1_new = s1 + b1 + b2 + ... + b16
    //   s2_new = s2 + (s1 + b1) + (s1 + b1 + b2) + ... + (s1 + b1 + b2 + ... + b16)
    //          = s2 + s1 * 16 + (b1 * 16 + b2 * 15 + ... + b16 * 1)
    //          = s2 + s1 * 16 + (b1, b2, ... b16) dot (16, 15, ... 1)
    __ ld1(vbytes, __ T16B, Address(__ post(buff, 16)));

    // s2 = s2 + s1 * 16
    __ add(s2, s2, s1, Assembler::LSL, 4);

    // vs1acc = b1 + b2 + b3 + ... + b16
    // vs2acc = (b1 * 16) + (b2 * 15) + (b3 * 14) + ... + (b16 * 1)
    __ umullv(vs2acc, __ T8B, vtable, vbytes);
    __ umlalv(vs2acc, __ T16B, vtable, vbytes);
    __ uaddlv(vs1acc, __ T16B, vbytes);
    __ uaddlv(vs2acc, __ T8H, vs2acc);

    // s1 = s1 + vs1acc, s2 = s2 + vs2acc
    __ fmovd(temp0, vs1acc);
    __ fmovd(temp1, vs2acc);
    __ add(s1, s1, temp0);
    __ add(s2, s2, temp1);
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
  address generate_multiplyToLen() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_multiplyToLen_id;
    StubCodeMark mark(this, stub_id);

    address start = __ pc();
    const Register x     = r0;
    const Register xlen  = r1;
    const Register y     = r2;
    const Register ylen  = r3;
    const Register z     = r4;

    const Register tmp0  = r5;
    const Register tmp1  = r10;
    const Register tmp2  = r11;
    const Register tmp3  = r12;
    const Register tmp4  = r13;
    const Register tmp5  = r14;
    const Register tmp6  = r15;
    const Register tmp7  = r16;

    BLOCK_COMMENT("Entry:");
    __ enter(); // required for proper stackwalking of RuntimeStub frame
    __ multiply_to_len(x, xlen, y, ylen, z, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7);
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(lr);

    return start;
  }

  address generate_squareToLen() {
    // squareToLen algorithm for sizes 1..127 described in java code works
    // faster than multiply_to_len on some CPUs and slower on others, but
    // multiply_to_len shows a bit better overall results
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_squareToLen_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    const Register x     = r0;
    const Register xlen  = r1;
    const Register z     = r2;
    const Register y     = r4; // == x
    const Register ylen  = r5; // == xlen

    const Register tmp0  = r3;
    const Register tmp1  = r10;
    const Register tmp2  = r11;
    const Register tmp3  = r12;
    const Register tmp4  = r13;
    const Register tmp5  = r14;
    const Register tmp6  = r15;
    const Register tmp7  = r16;

    RegSet spilled_regs = RegSet::of(y, ylen);
    BLOCK_COMMENT("Entry:");
    __ enter();
    __ push(spilled_regs, sp);
    __ mov(y, x);
    __ mov(ylen, xlen);
    __ multiply_to_len(x, xlen, y, ylen, z, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7);
    __ pop(spilled_regs, sp);
    __ leave();
    __ ret(lr);
    return start;
  }

  address generate_mulAdd() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_mulAdd_id;
    StubCodeMark mark(this, stub_id);

    address start = __ pc();

    const Register out     = r0;
    const Register in      = r1;
    const Register offset  = r2;
    const Register len     = r3;
    const Register k       = r4;

    BLOCK_COMMENT("Entry:");
    __ enter();
    __ mul_add(out, in, offset, len, k);
    __ leave();
    __ ret(lr);

    return start;
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
    StubId stub_id = StubId::stubgen_bigIntegerRightShiftWorker_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Label ShiftSIMDLoop, ShiftTwoLoop, ShiftThree, ShiftTwo, ShiftOne, Exit;

    Register newArr        = c_rarg0;
    Register oldArr        = c_rarg1;
    Register newIdx        = c_rarg2;
    Register shiftCount    = c_rarg3;
    Register numIter       = c_rarg4;
    Register idx           = numIter;

    Register newArrCur     = rscratch1;
    Register shiftRevCount = rscratch2;
    Register oldArrCur     = r13;
    Register oldArrNext    = r14;

    FloatRegister oldElem0        = v0;
    FloatRegister oldElem1        = v1;
    FloatRegister newElem         = v2;
    FloatRegister shiftVCount     = v3;
    FloatRegister shiftVRevCount  = v4;

    __ cbz(idx, Exit);

    __ add(newArr, newArr, newIdx, Assembler::LSL, 2);

    // left shift count
    __ movw(shiftRevCount, 32);
    __ subw(shiftRevCount, shiftRevCount, shiftCount);

    // numIter too small to allow a 4-words SIMD loop, rolling back
    __ cmp(numIter, (u1)4);
    __ br(Assembler::LT, ShiftThree);

    __ dup(shiftVCount,    __ T4S, shiftCount);
    __ dup(shiftVRevCount, __ T4S, shiftRevCount);
    __ negr(shiftVCount,   __ T4S, shiftVCount);

    __ BIND(ShiftSIMDLoop);

    // Calculate the load addresses
    __ sub(idx, idx, 4);
    __ add(oldArrNext, oldArr, idx, Assembler::LSL, 2);
    __ add(newArrCur,  newArr, idx, Assembler::LSL, 2);
    __ add(oldArrCur,  oldArrNext, 4);

    // Load 4 words and process
    __ ld1(oldElem0,  __ T4S,  Address(oldArrCur));
    __ ld1(oldElem1,  __ T4S,  Address(oldArrNext));
    __ ushl(oldElem0, __ T4S,  oldElem0, shiftVCount);
    __ ushl(oldElem1, __ T4S,  oldElem1, shiftVRevCount);
    __ orr(newElem,   __ T16B, oldElem0, oldElem1);
    __ st1(newElem,   __ T4S,  Address(newArrCur));

    __ cmp(idx, (u1)4);
    __ br(Assembler::LT, ShiftTwoLoop);
    __ b(ShiftSIMDLoop);

    __ BIND(ShiftTwoLoop);
    __ cbz(idx, Exit);
    __ cmp(idx, (u1)1);
    __ br(Assembler::EQ, ShiftOne);

    // Calculate the load addresses
    __ sub(idx, idx, 2);
    __ add(oldArrNext, oldArr, idx, Assembler::LSL, 2);
    __ add(newArrCur,  newArr, idx, Assembler::LSL, 2);
    __ add(oldArrCur,  oldArrNext, 4);

    // Load 2 words and process
    __ ld1(oldElem0,  __ T2S, Address(oldArrCur));
    __ ld1(oldElem1,  __ T2S, Address(oldArrNext));
    __ ushl(oldElem0, __ T2S, oldElem0, shiftVCount);
    __ ushl(oldElem1, __ T2S, oldElem1, shiftVRevCount);
    __ orr(newElem,   __ T8B, oldElem0, oldElem1);
    __ st1(newElem,   __ T2S, Address(newArrCur));
    __ b(ShiftTwoLoop);

    __ BIND(ShiftThree);
    __ tbz(idx, 1, ShiftOne);
    __ tbz(idx, 0, ShiftTwo);
    __ ldrw(r10,  Address(oldArr, 12));
    __ ldrw(r11,  Address(oldArr, 8));
    __ lsrvw(r10, r10, shiftCount);
    __ lslvw(r11, r11, shiftRevCount);
    __ orrw(r12,  r10, r11);
    __ strw(r12,  Address(newArr, 8));

    __ BIND(ShiftTwo);
    __ ldrw(r10,  Address(oldArr, 8));
    __ ldrw(r11,  Address(oldArr, 4));
    __ lsrvw(r10, r10, shiftCount);
    __ lslvw(r11, r11, shiftRevCount);
    __ orrw(r12,  r10, r11);
    __ strw(r12,  Address(newArr, 4));

    __ BIND(ShiftOne);
    __ ldrw(r10,  Address(oldArr, 4));
    __ ldrw(r11,  Address(oldArr));
    __ lsrvw(r10, r10, shiftCount);
    __ lslvw(r11, r11, shiftRevCount);
    __ orrw(r12,  r10, r11);
    __ strw(r12,  Address(newArr));

    __ BIND(Exit);
    __ ret(lr);

    return start;
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
    StubId stub_id = StubId::stubgen_bigIntegerLeftShiftWorker_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Label ShiftSIMDLoop, ShiftTwoLoop, ShiftThree, ShiftTwo, ShiftOne, Exit;

    Register newArr        = c_rarg0;
    Register oldArr        = c_rarg1;
    Register newIdx        = c_rarg2;
    Register shiftCount    = c_rarg3;
    Register numIter       = c_rarg4;

    Register shiftRevCount = rscratch1;
    Register oldArrNext    = rscratch2;

    FloatRegister oldElem0        = v0;
    FloatRegister oldElem1        = v1;
    FloatRegister newElem         = v2;
    FloatRegister shiftVCount     = v3;
    FloatRegister shiftVRevCount  = v4;

    __ cbz(numIter, Exit);

    __ add(oldArrNext, oldArr, 4);
    __ add(newArr, newArr, newIdx, Assembler::LSL, 2);

    // right shift count
    __ movw(shiftRevCount, 32);
    __ subw(shiftRevCount, shiftRevCount, shiftCount);

    // numIter too small to allow a 4-words SIMD loop, rolling back
    __ cmp(numIter, (u1)4);
    __ br(Assembler::LT, ShiftThree);

    __ dup(shiftVCount,     __ T4S, shiftCount);
    __ dup(shiftVRevCount,  __ T4S, shiftRevCount);
    __ negr(shiftVRevCount, __ T4S, shiftVRevCount);

    __ BIND(ShiftSIMDLoop);

    // load 4 words and process
    __ ld1(oldElem0,  __ T4S,  __ post(oldArr, 16));
    __ ld1(oldElem1,  __ T4S,  __ post(oldArrNext, 16));
    __ ushl(oldElem0, __ T4S,  oldElem0, shiftVCount);
    __ ushl(oldElem1, __ T4S,  oldElem1, shiftVRevCount);
    __ orr(newElem,   __ T16B, oldElem0, oldElem1);
    __ st1(newElem,   __ T4S,  __ post(newArr, 16));
    __ sub(numIter,   numIter, 4);

    __ cmp(numIter, (u1)4);
    __ br(Assembler::LT, ShiftTwoLoop);
    __ b(ShiftSIMDLoop);

    __ BIND(ShiftTwoLoop);
    __ cbz(numIter, Exit);
    __ cmp(numIter, (u1)1);
    __ br(Assembler::EQ, ShiftOne);

    // load 2 words and process
    __ ld1(oldElem0,  __ T2S,  __ post(oldArr, 8));
    __ ld1(oldElem1,  __ T2S,  __ post(oldArrNext, 8));
    __ ushl(oldElem0, __ T2S,  oldElem0, shiftVCount);
    __ ushl(oldElem1, __ T2S,  oldElem1, shiftVRevCount);
    __ orr(newElem,   __ T8B,  oldElem0, oldElem1);
    __ st1(newElem,   __ T2S,  __ post(newArr, 8));
    __ sub(numIter,   numIter, 2);
    __ b(ShiftTwoLoop);

    __ BIND(ShiftThree);
    __ ldrw(r10,  __ post(oldArr, 4));
    __ ldrw(r11,  __ post(oldArrNext, 4));
    __ lslvw(r10, r10, shiftCount);
    __ lsrvw(r11, r11, shiftRevCount);
    __ orrw(r12,  r10, r11);
    __ strw(r12,  __ post(newArr, 4));
    __ tbz(numIter, 1, Exit);
    __ tbz(numIter, 0, ShiftOne);

    __ BIND(ShiftTwo);
    __ ldrw(r10,  __ post(oldArr, 4));
    __ ldrw(r11,  __ post(oldArrNext, 4));
    __ lslvw(r10, r10, shiftCount);
    __ lsrvw(r11, r11, shiftRevCount);
    __ orrw(r12,  r10, r11);
    __ strw(r12,  __ post(newArr, 4));

    __ BIND(ShiftOne);
    __ ldrw(r10,  Address(oldArr));
    __ ldrw(r11,  Address(oldArrNext));
    __ lslvw(r10, r10, shiftCount);
    __ lsrvw(r11, r11, shiftRevCount);
    __ orrw(r12,  r10, r11);
    __ strw(r12,  Address(newArr));

    __ BIND(Exit);
    __ ret(lr);

    return start;
  }

  address generate_count_positives(address &count_positives_long) {
    const u1 large_loop_size = 64;
    const uint64_t UPPER_BIT_MASK=0x8080808080808080;
    int dcache_line = VM_Version::dcache_line_size();

    Register ary1 = r1, len = r2, result = r0;

    __ align(CodeEntryAlignment);

    StubId stub_id = StubId::stubgen_count_positives_id;
    StubCodeMark mark(this, stub_id);

    address entry = __ pc();

    __ enter();
    // precondition: a copy of len is already in result
    // __ mov(result, len);

  Label RET_ADJUST, RET_ADJUST_16, RET_ADJUST_LONG, RET_NO_POP, RET_LEN, ALIGNED, LOOP16, CHECK_16,
        LARGE_LOOP, POST_LOOP16, LEN_OVER_15, LEN_OVER_8, POST_LOOP16_LOAD_TAIL;

  __ cmp(len, (u1)15);
  __ br(Assembler::GT, LEN_OVER_15);
  // The only case when execution falls into this code is when pointer is near
  // the end of memory page and we have to avoid reading next page
  __ add(ary1, ary1, len);
  __ subs(len, len, 8);
  __ br(Assembler::GT, LEN_OVER_8);
  __ ldr(rscratch2, Address(ary1, -8));
  __ sub(rscratch1, zr, len, __ LSL, 3);  // LSL 3 is to get bits from bytes.
  __ lsrv(rscratch2, rscratch2, rscratch1);
  __ tst(rscratch2, UPPER_BIT_MASK);
  __ csel(result, zr, result, Assembler::NE);
  __ leave();
  __ ret(lr);
  __ bind(LEN_OVER_8);
  __ ldp(rscratch1, rscratch2, Address(ary1, -16));
  __ sub(len, len, 8); // no data dep., then sub can be executed while loading
  __ tst(rscratch2, UPPER_BIT_MASK);
  __ br(Assembler::NE, RET_NO_POP);
  __ sub(rscratch2, zr, len, __ LSL, 3); // LSL 3 is to get bits from bytes
  __ lsrv(rscratch1, rscratch1, rscratch2);
  __ tst(rscratch1, UPPER_BIT_MASK);
  __ bind(RET_NO_POP);
  __ csel(result, zr, result, Assembler::NE);
  __ leave();
  __ ret(lr);

  Register tmp1 = r3, tmp2 = r4, tmp3 = r5, tmp4 = r6, tmp5 = r7, tmp6 = r10;
  const RegSet spilled_regs = RegSet::range(tmp1, tmp5) + tmp6;

  count_positives_long = __ pc(); // 2nd entry point

  __ enter();

  __ bind(LEN_OVER_15);
    __ push(spilled_regs, sp);
    __ andr(rscratch2, ary1, 15); // check pointer for 16-byte alignment
    __ cbz(rscratch2, ALIGNED);
    __ ldp(tmp6, tmp1, Address(ary1));
    __ mov(tmp5, 16);
    __ sub(rscratch1, tmp5, rscratch2); // amount of bytes until aligned address
    __ add(ary1, ary1, rscratch1);
    __ orr(tmp6, tmp6, tmp1);
    __ tst(tmp6, UPPER_BIT_MASK);
    __ br(Assembler::NE, RET_ADJUST);
    __ sub(len, len, rscratch1);

  __ bind(ALIGNED);
    __ cmp(len, large_loop_size);
    __ br(Assembler::LT, CHECK_16);
    // Perform 16-byte load as early return in pre-loop to handle situation
    // when initially aligned large array has negative values at starting bytes,
    // so LARGE_LOOP would do 4 reads instead of 1 (in worst case), which is
    // slower. Cases with negative bytes further ahead won't be affected that
    // much. In fact, it'll be faster due to early loads, less instructions and
    // less branches in LARGE_LOOP.
    __ ldp(tmp6, tmp1, Address(__ post(ary1, 16)));
    __ sub(len, len, 16);
    __ orr(tmp6, tmp6, tmp1);
    __ tst(tmp6, UPPER_BIT_MASK);
    __ br(Assembler::NE, RET_ADJUST_16);
    __ cmp(len, large_loop_size);
    __ br(Assembler::LT, CHECK_16);

    if (SoftwarePrefetchHintDistance >= 0
        && SoftwarePrefetchHintDistance >= dcache_line) {
      // initial prefetch
      __ prfm(Address(ary1, SoftwarePrefetchHintDistance - dcache_line));
    }
  __ bind(LARGE_LOOP);
    if (SoftwarePrefetchHintDistance >= 0) {
      __ prfm(Address(ary1, SoftwarePrefetchHintDistance));
    }
    // Issue load instructions first, since it can save few CPU/MEM cycles, also
    // instead of 4 triples of "orr(...), addr(...);cbnz(...);" (for each ldp)
    // better generate 7 * orr(...) + 1 andr(...) + 1 cbnz(...) which saves 3
    // instructions per cycle and have less branches, but this approach disables
    // early return, thus, all 64 bytes are loaded and checked every time.
    __ ldp(tmp2, tmp3, Address(ary1));
    __ ldp(tmp4, tmp5, Address(ary1, 16));
    __ ldp(rscratch1, rscratch2, Address(ary1, 32));
    __ ldp(tmp6, tmp1, Address(ary1, 48));
    __ add(ary1, ary1, large_loop_size);
    __ sub(len, len, large_loop_size);
    __ orr(tmp2, tmp2, tmp3);
    __ orr(tmp4, tmp4, tmp5);
    __ orr(rscratch1, rscratch1, rscratch2);
    __ orr(tmp6, tmp6, tmp1);
    __ orr(tmp2, tmp2, tmp4);
    __ orr(rscratch1, rscratch1, tmp6);
    __ orr(tmp2, tmp2, rscratch1);
    __ tst(tmp2, UPPER_BIT_MASK);
    __ br(Assembler::NE, RET_ADJUST_LONG);
    __ cmp(len, large_loop_size);
    __ br(Assembler::GE, LARGE_LOOP);

  __ bind(CHECK_16); // small 16-byte load pre-loop
    __ cmp(len, (u1)16);
    __ br(Assembler::LT, POST_LOOP16);

  __ bind(LOOP16); // small 16-byte load loop
    __ ldp(tmp2, tmp3, Address(__ post(ary1, 16)));
    __ sub(len, len, 16);
    __ orr(tmp2, tmp2, tmp3);
    __ tst(tmp2, UPPER_BIT_MASK);
    __ br(Assembler::NE, RET_ADJUST_16);
    __ cmp(len, (u1)16);
    __ br(Assembler::GE, LOOP16); // 16-byte load loop end

  __ bind(POST_LOOP16); // 16-byte aligned, so we can read unconditionally
    __ cmp(len, (u1)8);
    __ br(Assembler::LE, POST_LOOP16_LOAD_TAIL);
    __ ldr(tmp3, Address(__ post(ary1, 8)));
    __ tst(tmp3, UPPER_BIT_MASK);
    __ br(Assembler::NE, RET_ADJUST);
    __ sub(len, len, 8);

  __ bind(POST_LOOP16_LOAD_TAIL);
    __ cbz(len, RET_LEN); // Can't shift left by 64 when len==0
    __ ldr(tmp1, Address(ary1));
    __ mov(tmp2, 64);
    __ sub(tmp4, tmp2, len, __ LSL, 3);
    __ lslv(tmp1, tmp1, tmp4);
    __ tst(tmp1, UPPER_BIT_MASK);
    __ br(Assembler::NE, RET_ADJUST);
    // Fallthrough

  __ bind(RET_LEN);
    __ pop(spilled_regs, sp);
    __ leave();
    __ ret(lr);

    // difference result - len is the count of guaranteed to be
    // positive bytes

  __ bind(RET_ADJUST_LONG);
    __ add(len, len, (u1)(large_loop_size - 16));
  __ bind(RET_ADJUST_16);
    __ add(len, len, 16);
  __ bind(RET_ADJUST);
    __ pop(spilled_regs, sp);
    __ leave();
    __ sub(result, result, len);
    __ ret(lr);

    return entry;
  }

  void generate_large_array_equals_loop_nonsimd(int loopThreshold,
        bool usePrefetch, Label &NOT_EQUAL) {
    Register a1 = r1, a2 = r2, result = r0, cnt1 = r10, tmp1 = rscratch1,
        tmp2 = rscratch2, tmp3 = r3, tmp4 = r4, tmp5 = r5, tmp6 = r11,
        tmp7 = r12, tmp8 = r13;
    Label LOOP;

    __ ldp(tmp1, tmp3, Address(__ post(a1, 2 * wordSize)));
    __ ldp(tmp2, tmp4, Address(__ post(a2, 2 * wordSize)));
    __ bind(LOOP);
    if (usePrefetch) {
      __ prfm(Address(a1, SoftwarePrefetchHintDistance));
      __ prfm(Address(a2, SoftwarePrefetchHintDistance));
    }
    __ ldp(tmp5, tmp7, Address(__ post(a1, 2 * wordSize)));
    __ eor(tmp1, tmp1, tmp2);
    __ eor(tmp3, tmp3, tmp4);
    __ ldp(tmp6, tmp8, Address(__ post(a2, 2 * wordSize)));
    __ orr(tmp1, tmp1, tmp3);
    __ cbnz(tmp1, NOT_EQUAL);
    __ ldp(tmp1, tmp3, Address(__ post(a1, 2 * wordSize)));
    __ eor(tmp5, tmp5, tmp6);
    __ eor(tmp7, tmp7, tmp8);
    __ ldp(tmp2, tmp4, Address(__ post(a2, 2 * wordSize)));
    __ orr(tmp5, tmp5, tmp7);
    __ cbnz(tmp5, NOT_EQUAL);
    __ ldp(tmp5, tmp7, Address(__ post(a1, 2 * wordSize)));
    __ eor(tmp1, tmp1, tmp2);
    __ eor(tmp3, tmp3, tmp4);
    __ ldp(tmp6, tmp8, Address(__ post(a2, 2 * wordSize)));
    __ orr(tmp1, tmp1, tmp3);
    __ cbnz(tmp1, NOT_EQUAL);
    __ ldp(tmp1, tmp3, Address(__ post(a1, 2 * wordSize)));
    __ eor(tmp5, tmp5, tmp6);
    __ sub(cnt1, cnt1, 8 * wordSize);
    __ eor(tmp7, tmp7, tmp8);
    __ ldp(tmp2, tmp4, Address(__ post(a2, 2 * wordSize)));
    // tmp6 is not used. MacroAssembler::subs is used here (rather than
    // cmp) because subs allows an unlimited range of immediate operand.
    __ subs(tmp6, cnt1, loopThreshold);
    __ orr(tmp5, tmp5, tmp7);
    __ cbnz(tmp5, NOT_EQUAL);
    __ br(__ GE, LOOP);
    // post-loop
    __ eor(tmp1, tmp1, tmp2);
    __ eor(tmp3, tmp3, tmp4);
    __ orr(tmp1, tmp1, tmp3);
    __ sub(cnt1, cnt1, 2 * wordSize);
    __ cbnz(tmp1, NOT_EQUAL);
  }

  void generate_large_array_equals_loop_simd(int loopThreshold,
        bool usePrefetch, Label &NOT_EQUAL) {
    Register a1 = r1, a2 = r2, result = r0, cnt1 = r10, tmp1 = rscratch1,
        tmp2 = rscratch2;
    Label LOOP;

    __ bind(LOOP);
    if (usePrefetch) {
      __ prfm(Address(a1, SoftwarePrefetchHintDistance));
      __ prfm(Address(a2, SoftwarePrefetchHintDistance));
    }
    __ ld1(v0, v1, v2, v3, __ T2D, Address(__ post(a1, 4 * 2 * wordSize)));
    __ sub(cnt1, cnt1, 8 * wordSize);
    __ ld1(v4, v5, v6, v7, __ T2D, Address(__ post(a2, 4 * 2 * wordSize)));
    __ subs(tmp1, cnt1, loopThreshold);
    __ eor(v0, __ T16B, v0, v4);
    __ eor(v1, __ T16B, v1, v5);
    __ eor(v2, __ T16B, v2, v6);
    __ eor(v3, __ T16B, v3, v7);
    __ orr(v0, __ T16B, v0, v1);
    __ orr(v1, __ T16B, v2, v3);
    __ orr(v0, __ T16B, v0, v1);
    __ umov(tmp1, v0, __ D, 0);
    __ umov(tmp2, v0, __ D, 1);
    __ orr(tmp1, tmp1, tmp2);
    __ cbnz(tmp1, NOT_EQUAL);
    __ br(__ GE, LOOP);
  }

  // a1 = r1 - array1 address
  // a2 = r2 - array2 address
  // result = r0 - return value. Already contains "false"
  // cnt1 = r10 - amount of elements left to check, reduced by wordSize
  // r3-r5 are reserved temporary registers
  // Clobbers: v0-v7 when UseSIMDForArrayEquals, rscratch1, rscratch2
  address generate_large_array_equals() {
    Register a1 = r1, a2 = r2, result = r0, cnt1 = r10, tmp1 = rscratch1,
        tmp2 = rscratch2, tmp3 = r3, tmp4 = r4, tmp5 = r5, tmp6 = r11,
        tmp7 = r12, tmp8 = r13;
    Label TAIL, NOT_EQUAL, EQUAL, NOT_EQUAL_NO_POP, NO_PREFETCH_LARGE_LOOP,
        SMALL_LOOP, POST_LOOP;
    const int PRE_LOOP_SIZE = UseSIMDForArrayEquals ? 0 : 16;
    // calculate if at least 32 prefetched bytes are used
    int prefetchLoopThreshold = SoftwarePrefetchHintDistance + 32;
    int nonPrefetchLoopThreshold = (64 + PRE_LOOP_SIZE);
    RegSet spilled_regs = RegSet::range(tmp6, tmp8);
    assert_different_registers(a1, a2, result, cnt1, tmp1, tmp2, tmp3, tmp4,
        tmp5, tmp6, tmp7, tmp8);

    __ align(CodeEntryAlignment);

    StubId stub_id = StubId::stubgen_large_array_equals_id;
    StubCodeMark mark(this, stub_id);

    address entry = __ pc();
    __ enter();
    __ sub(cnt1, cnt1, wordSize);  // first 8 bytes were loaded outside of stub
    // also advance pointers to use post-increment instead of pre-increment
    __ add(a1, a1, wordSize);
    __ add(a2, a2, wordSize);
    if (AvoidUnalignedAccesses) {
      // both implementations (SIMD/nonSIMD) are using relatively large load
      // instructions (ld1/ldp), which has huge penalty (up to x2 exec time)
      // on some CPUs in case of address is not at least 16-byte aligned.
      // Arrays are 8-byte aligned currently, so, we can make additional 8-byte
      // load if needed at least for 1st address and make if 16-byte aligned.
      Label ALIGNED16;
      __ tbz(a1, 3, ALIGNED16);
      __ ldr(tmp1, Address(__ post(a1, wordSize)));
      __ ldr(tmp2, Address(__ post(a2, wordSize)));
      __ sub(cnt1, cnt1, wordSize);
      __ eor(tmp1, tmp1, tmp2);
      __ cbnz(tmp1, NOT_EQUAL_NO_POP);
      __ bind(ALIGNED16);
    }
    if (UseSIMDForArrayEquals) {
      if (SoftwarePrefetchHintDistance >= 0) {
        __ subs(tmp1, cnt1, prefetchLoopThreshold);
        __ br(__ LE, NO_PREFETCH_LARGE_LOOP);
        generate_large_array_equals_loop_simd(prefetchLoopThreshold,
            /* prfm = */ true, NOT_EQUAL);
        __ subs(zr, cnt1, nonPrefetchLoopThreshold);
        __ br(__ LT, TAIL);
      }
      __ bind(NO_PREFETCH_LARGE_LOOP);
      generate_large_array_equals_loop_simd(nonPrefetchLoopThreshold,
          /* prfm = */ false, NOT_EQUAL);
    } else {
      __ push(spilled_regs, sp);
      if (SoftwarePrefetchHintDistance >= 0) {
        __ subs(tmp1, cnt1, prefetchLoopThreshold);
        __ br(__ LE, NO_PREFETCH_LARGE_LOOP);
        generate_large_array_equals_loop_nonsimd(prefetchLoopThreshold,
            /* prfm = */ true, NOT_EQUAL);
        __ subs(zr, cnt1, nonPrefetchLoopThreshold);
        __ br(__ LT, TAIL);
      }
      __ bind(NO_PREFETCH_LARGE_LOOP);
      generate_large_array_equals_loop_nonsimd(nonPrefetchLoopThreshold,
          /* prfm = */ false, NOT_EQUAL);
    }
    __ bind(TAIL);
      __ cbz(cnt1, EQUAL);
      __ subs(cnt1, cnt1, wordSize);
      __ br(__ LE, POST_LOOP);
    __ bind(SMALL_LOOP);
      __ ldr(tmp1, Address(__ post(a1, wordSize)));
      __ ldr(tmp2, Address(__ post(a2, wordSize)));
      __ subs(cnt1, cnt1, wordSize);
      __ eor(tmp1, tmp1, tmp2);
      __ cbnz(tmp1, NOT_EQUAL);
      __ br(__ GT, SMALL_LOOP);
    __ bind(POST_LOOP);
      __ ldr(tmp1, Address(a1, cnt1));
      __ ldr(tmp2, Address(a2, cnt1));
      __ eor(tmp1, tmp1, tmp2);
      __ cbnz(tmp1, NOT_EQUAL);
    __ bind(EQUAL);
      __ mov(result, true);
    __ bind(NOT_EQUAL);
      if (!UseSIMDForArrayEquals) {
        __ pop(spilled_regs, sp);
      }
    __ bind(NOT_EQUAL_NO_POP);
    __ leave();
    __ ret(lr);
    return entry;
  }

  // result = r0 - return value. Contains initial hashcode value on entry.
  // ary = r1 - array address
  // cnt = r2 - elements count
  // Clobbers: v0-v13, rscratch1, rscratch2
  address generate_large_arrays_hashcode(BasicType eltype) {
    const Register result = r0, ary = r1, cnt = r2;
    const FloatRegister vdata0 = v3, vdata1 = v2, vdata2 = v1, vdata3 = v0;
    const FloatRegister vmul0 = v4, vmul1 = v5, vmul2 = v6, vmul3 = v7;
    const FloatRegister vpow = v12;  // powers of 31: <31^3, ..., 31^0>
    const FloatRegister vpowm = v13;

    ARRAYS_HASHCODE_REGISTERS;

    Label SMALL_LOOP, LARGE_LOOP_PREHEADER, LARGE_LOOP, TAIL, TAIL_SHORTCUT, BR_BASE;

    unsigned int vf; // vectorization factor
    bool multiply_by_halves;
    Assembler::SIMD_Arrangement load_arrangement;
    switch (eltype) {
    case T_BOOLEAN:
    case T_BYTE:
      load_arrangement = Assembler::T8B;
      multiply_by_halves = true;
      vf = 8;
      break;
    case T_CHAR:
    case T_SHORT:
      load_arrangement = Assembler::T8H;
      multiply_by_halves = true;
      vf = 8;
      break;
    case T_INT:
      load_arrangement = Assembler::T4S;
      multiply_by_halves = false;
      vf = 4;
      break;
    default:
      ShouldNotReachHere();
    }

    // Unroll factor
    const unsigned uf = 4;

    // Effective vectorization factor
    const unsigned evf = vf * uf;

    __ align(CodeEntryAlignment);

    StubId stub_id;
    switch (eltype) {
    case T_BOOLEAN:
      stub_id = StubId::stubgen_large_arrays_hashcode_boolean_id;
      break;
    case T_BYTE:
      stub_id = StubId::stubgen_large_arrays_hashcode_byte_id;
      break;
    case T_CHAR:
      stub_id = StubId::stubgen_large_arrays_hashcode_char_id;
      break;
    case T_SHORT:
      stub_id = StubId::stubgen_large_arrays_hashcode_short_id;
      break;
    case T_INT:
      stub_id = StubId::stubgen_large_arrays_hashcode_int_id;
      break;
    default:
      stub_id = StubId::NO_STUBID;
      ShouldNotReachHere();
    };

    StubCodeMark mark(this, stub_id);

    address entry = __ pc();
    __ enter();

    // Put 0-3'th powers of 31 into a single SIMD register together. The register will be used in
    // the SMALL and LARGE LOOPS' epilogues. The initialization is hoisted here and the register's
    // value shouldn't change throughout both loops.
    __ movw(rscratch1, intpow(31U, 3));
    __ mov(vpow, Assembler::S, 0, rscratch1);
    __ movw(rscratch1, intpow(31U, 2));
    __ mov(vpow, Assembler::S, 1, rscratch1);
    __ movw(rscratch1, intpow(31U, 1));
    __ mov(vpow, Assembler::S, 2, rscratch1);
    __ movw(rscratch1, intpow(31U, 0));
    __ mov(vpow, Assembler::S, 3, rscratch1);

    __ mov(vmul0, Assembler::T16B, 0);
    __ mov(vmul0, Assembler::S, 3, result);

    __ andr(rscratch2, cnt, (uf - 1) * vf);
    __ cbz(rscratch2, LARGE_LOOP_PREHEADER);

    __ movw(rscratch1, intpow(31U, multiply_by_halves ? vf / 2 : vf));
    __ mov(vpowm, Assembler::S, 0, rscratch1);

    // SMALL LOOP
    __ bind(SMALL_LOOP);

    __ ld1(vdata0, load_arrangement, Address(__ post(ary, vf * type2aelembytes(eltype))));
    __ mulvs(vmul0, Assembler::T4S, vmul0, vpowm, 0);
    __ subsw(rscratch2, rscratch2, vf);

    if (load_arrangement == Assembler::T8B) {
      // Extend 8B to 8H to be able to use vector multiply
      // instructions
      assert(load_arrangement == Assembler::T8B, "expected to extend 8B to 8H");
      if (is_signed_subword_type(eltype)) {
        __ sxtl(vdata0, Assembler::T8H, vdata0, load_arrangement);
      } else {
        __ uxtl(vdata0, Assembler::T8H, vdata0, load_arrangement);
      }
    }

    switch (load_arrangement) {
    case Assembler::T4S:
      __ addv(vmul0, load_arrangement, vmul0, vdata0);
      break;
    case Assembler::T8B:
    case Assembler::T8H:
      assert(is_subword_type(eltype), "subword type expected");
      if (is_signed_subword_type(eltype)) {
        __ saddwv(vmul0, vmul0, Assembler::T4S, vdata0, Assembler::T4H);
      } else {
        __ uaddwv(vmul0, vmul0, Assembler::T4S, vdata0, Assembler::T4H);
      }
      break;
    default:
      __ should_not_reach_here();
    }

    // Process the upper half of a vector
    if (load_arrangement == Assembler::T8B || load_arrangement == Assembler::T8H) {
      __ mulvs(vmul0, Assembler::T4S, vmul0, vpowm, 0);
      if (is_signed_subword_type(eltype)) {
        __ saddwv2(vmul0, vmul0, Assembler::T4S, vdata0, Assembler::T8H);
      } else {
        __ uaddwv2(vmul0, vmul0, Assembler::T4S, vdata0, Assembler::T8H);
      }
    }

    __ br(Assembler::HI, SMALL_LOOP);

    // SMALL LOOP'S EPILOQUE
    __ lsr(rscratch2, cnt, exact_log2(evf));
    __ cbnz(rscratch2, LARGE_LOOP_PREHEADER);

    __ mulv(vmul0, Assembler::T4S, vmul0, vpow);
    __ addv(vmul0, Assembler::T4S, vmul0);
    __ umov(result, vmul0, Assembler::S, 0);

    // TAIL
    __ bind(TAIL);

    // The andr performs cnt % vf. The subtract shifted by 3 offsets past vf - 1 - (cnt % vf) pairs
    // of load + madd insns i.e. it only executes cnt % vf load + madd pairs.
    assert(is_power_of_2(vf), "can't use this value to calculate the jump target PC");
    __ andr(rscratch2, cnt, vf - 1);
    __ bind(TAIL_SHORTCUT);
    __ adr(rscratch1, BR_BASE);
    // For Cortex-A53 offset is 4 because 2 nops are generated.
    __ sub(rscratch1, rscratch1, rscratch2, ext::uxtw, VM_Version::supports_a53mac() ? 4 : 3);
    __ movw(rscratch2, 0x1f);
    __ br(rscratch1);

    for (size_t i = 0; i < vf - 1; ++i) {
      __ load(rscratch1, Address(__ post(ary, type2aelembytes(eltype))),
                                   eltype);
      __ maddw(result, result, rscratch2, rscratch1);
      // maddw generates an extra nop for Cortex-A53 (see maddw definition in macroAssembler).
      // Generate 2nd nop to have 4 instructions per iteration.
      if (VM_Version::supports_a53mac()) {
        __ nop();
      }
    }
    __ bind(BR_BASE);

    __ leave();
    __ ret(lr);

    // LARGE LOOP
    __ bind(LARGE_LOOP_PREHEADER);

    __ lsr(rscratch2, cnt, exact_log2(evf));

    if (multiply_by_halves) {
      // 31^4 - multiplier between lower and upper parts of a register
      __ movw(rscratch1, intpow(31U, vf / 2));
      __ mov(vpowm, Assembler::S, 1, rscratch1);
      // 31^28 - remainder of the iteraion multiplier, 28 = 32 - 4
      __ movw(rscratch1, intpow(31U, evf - vf / 2));
      __ mov(vpowm, Assembler::S, 0, rscratch1);
    } else {
      // 31^16
      __ movw(rscratch1, intpow(31U, evf));
      __ mov(vpowm, Assembler::S, 0, rscratch1);
    }

    __ mov(vmul3, Assembler::T16B, 0);
    __ mov(vmul2, Assembler::T16B, 0);
    __ mov(vmul1, Assembler::T16B, 0);

    __ bind(LARGE_LOOP);

    __ mulvs(vmul3, Assembler::T4S, vmul3, vpowm, 0);
    __ mulvs(vmul2, Assembler::T4S, vmul2, vpowm, 0);
    __ mulvs(vmul1, Assembler::T4S, vmul1, vpowm, 0);
    __ mulvs(vmul0, Assembler::T4S, vmul0, vpowm, 0);

    __ ld1(vdata3, vdata2, vdata1, vdata0, load_arrangement,
           Address(__ post(ary, evf * type2aelembytes(eltype))));

    if (load_arrangement == Assembler::T8B) {
      // Extend 8B to 8H to be able to use vector multiply
      // instructions
      assert(load_arrangement == Assembler::T8B, "expected to extend 8B to 8H");
      if (is_signed_subword_type(eltype)) {
        __ sxtl(vdata3, Assembler::T8H, vdata3, load_arrangement);
        __ sxtl(vdata2, Assembler::T8H, vdata2, load_arrangement);
        __ sxtl(vdata1, Assembler::T8H, vdata1, load_arrangement);
        __ sxtl(vdata0, Assembler::T8H, vdata0, load_arrangement);
      } else {
        __ uxtl(vdata3, Assembler::T8H, vdata3, load_arrangement);
        __ uxtl(vdata2, Assembler::T8H, vdata2, load_arrangement);
        __ uxtl(vdata1, Assembler::T8H, vdata1, load_arrangement);
        __ uxtl(vdata0, Assembler::T8H, vdata0, load_arrangement);
      }
    }

    switch (load_arrangement) {
    case Assembler::T4S:
      __ addv(vmul3, load_arrangement, vmul3, vdata3);
      __ addv(vmul2, load_arrangement, vmul2, vdata2);
      __ addv(vmul1, load_arrangement, vmul1, vdata1);
      __ addv(vmul0, load_arrangement, vmul0, vdata0);
      break;
    case Assembler::T8B:
    case Assembler::T8H:
      assert(is_subword_type(eltype), "subword type expected");
      if (is_signed_subword_type(eltype)) {
        __ saddwv(vmul3, vmul3, Assembler::T4S, vdata3, Assembler::T4H);
        __ saddwv(vmul2, vmul2, Assembler::T4S, vdata2, Assembler::T4H);
        __ saddwv(vmul1, vmul1, Assembler::T4S, vdata1, Assembler::T4H);
        __ saddwv(vmul0, vmul0, Assembler::T4S, vdata0, Assembler::T4H);
      } else {
        __ uaddwv(vmul3, vmul3, Assembler::T4S, vdata3, Assembler::T4H);
        __ uaddwv(vmul2, vmul2, Assembler::T4S, vdata2, Assembler::T4H);
        __ uaddwv(vmul1, vmul1, Assembler::T4S, vdata1, Assembler::T4H);
        __ uaddwv(vmul0, vmul0, Assembler::T4S, vdata0, Assembler::T4H);
      }
      break;
    default:
      __ should_not_reach_here();
    }

    // Process the upper half of a vector
    if (load_arrangement == Assembler::T8B || load_arrangement == Assembler::T8H) {
      __ mulvs(vmul3, Assembler::T4S, vmul3, vpowm, 1);
      __ mulvs(vmul2, Assembler::T4S, vmul2, vpowm, 1);
      __ mulvs(vmul1, Assembler::T4S, vmul1, vpowm, 1);
      __ mulvs(vmul0, Assembler::T4S, vmul0, vpowm, 1);
      if (is_signed_subword_type(eltype)) {
        __ saddwv2(vmul3, vmul3, Assembler::T4S, vdata3, Assembler::T8H);
        __ saddwv2(vmul2, vmul2, Assembler::T4S, vdata2, Assembler::T8H);
        __ saddwv2(vmul1, vmul1, Assembler::T4S, vdata1, Assembler::T8H);
        __ saddwv2(vmul0, vmul0, Assembler::T4S, vdata0, Assembler::T8H);
      } else {
        __ uaddwv2(vmul3, vmul3, Assembler::T4S, vdata3, Assembler::T8H);
        __ uaddwv2(vmul2, vmul2, Assembler::T4S, vdata2, Assembler::T8H);
        __ uaddwv2(vmul1, vmul1, Assembler::T4S, vdata1, Assembler::T8H);
        __ uaddwv2(vmul0, vmul0, Assembler::T4S, vdata0, Assembler::T8H);
      }
    }

    __ subsw(rscratch2, rscratch2, 1);
    __ br(Assembler::HI, LARGE_LOOP);

    __ mulv(vmul3, Assembler::T4S, vmul3, vpow);
    __ addv(vmul3, Assembler::T4S, vmul3);
    __ umov(result, vmul3, Assembler::S, 0);

    __ mov(rscratch2, intpow(31U, vf));

    __ mulv(vmul2, Assembler::T4S, vmul2, vpow);
    __ addv(vmul2, Assembler::T4S, vmul2);
    __ umov(rscratch1, vmul2, Assembler::S, 0);
    __ maddw(result, result, rscratch2, rscratch1);

    __ mulv(vmul1, Assembler::T4S, vmul1, vpow);
    __ addv(vmul1, Assembler::T4S, vmul1);
    __ umov(rscratch1, vmul1, Assembler::S, 0);
    __ maddw(result, result, rscratch2, rscratch1);

    __ mulv(vmul0, Assembler::T4S, vmul0, vpow);
    __ addv(vmul0, Assembler::T4S, vmul0);
    __ umov(rscratch1, vmul0, Assembler::S, 0);
    __ maddw(result, result, rscratch2, rscratch1);

    __ andr(rscratch2, cnt, vf - 1);
    __ cbnz(rscratch2, TAIL_SHORTCUT);

    __ leave();
    __ ret(lr);

    return entry;
  }

  address generate_dsin_dcos(bool isCos) {
    __ align(CodeEntryAlignment);
    StubId stub_id = (isCos ? StubId::stubgen_dcos_id : StubId::stubgen_dsin_id);
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    __ generate_dsin_dcos(isCos, (address)StubRoutines::aarch64::_npio2_hw,
        (address)StubRoutines::aarch64::_two_over_pi,
        (address)StubRoutines::aarch64::_pio2,
        (address)StubRoutines::aarch64::_dsin_coef,
        (address)StubRoutines::aarch64::_dcos_coef);
    return start;
  }

  // code for comparing 16 characters of strings with Latin1 and Utf16 encoding
  void compare_string_16_x_LU(Register tmpL, Register tmpU, Label &DIFF1,
      Label &DIFF2) {
    Register cnt1 = r2, tmp2 = r11, tmp3 = r12;
    FloatRegister vtmp = v1, vtmpZ = v0, vtmp3 = v2;

    __ ldrq(vtmp, Address(__ post(tmp2, 16)));
    __ ldr(tmpU, Address(__ post(cnt1, 8)));
    __ zip1(vtmp3, __ T16B, vtmp, vtmpZ);
    // now we have 32 bytes of characters (converted to U) in vtmp:vtmp3

    __ fmovd(tmpL, vtmp3);
    __ eor(rscratch2, tmp3, tmpL);
    __ cbnz(rscratch2, DIFF2);

    __ ldr(tmp3, Address(__ post(cnt1, 8)));
    __ umov(tmpL, vtmp3, __ D, 1);
    __ eor(rscratch2, tmpU, tmpL);
    __ cbnz(rscratch2, DIFF1);

    __ zip2(vtmp, __ T16B, vtmp, vtmpZ);
    __ ldr(tmpU, Address(__ post(cnt1, 8)));
    __ fmovd(tmpL, vtmp);
    __ eor(rscratch2, tmp3, tmpL);
    __ cbnz(rscratch2, DIFF2);

    __ ldr(tmp3, Address(__ post(cnt1, 8)));
    __ umov(tmpL, vtmp, __ D, 1);
    __ eor(rscratch2, tmpU, tmpL);
    __ cbnz(rscratch2, DIFF1);
  }

  // r0  = result
  // r1  = str1
  // r2  = cnt1
  // r3  = str2
  // r4  = cnt2
  // r10 = tmp1
  // r11 = tmp2
  address generate_compare_long_string_different_encoding(bool isLU) {
    __ align(CodeEntryAlignment);
    StubId stub_id = (isLU ? StubId::stubgen_compare_long_string_LU_id : StubId::stubgen_compare_long_string_UL_id);
    StubCodeMark mark(this, stub_id);
    address entry = __ pc();
    Label SMALL_LOOP, TAIL, TAIL_LOAD_16, LOAD_LAST, DIFF1, DIFF2,
        DONE, CALCULATE_DIFFERENCE, LARGE_LOOP_PREFETCH, NO_PREFETCH,
        LARGE_LOOP_PREFETCH_REPEAT1, LARGE_LOOP_PREFETCH_REPEAT2;
    Register result = r0, str1 = r1, cnt1 = r2, str2 = r3, cnt2 = r4,
        tmp1 = r10, tmp2 = r11, tmp3 = r12, tmp4 = r14;
    FloatRegister vtmpZ = v0, vtmp = v1, vtmp3 = v2;
    RegSet spilled_regs = RegSet::of(tmp3, tmp4);

    int prefetchLoopExitCondition = MAX2(64, SoftwarePrefetchHintDistance/2);

    __ eor(vtmpZ, __ T16B, vtmpZ, vtmpZ);
    // cnt2 == amount of characters left to compare
    // Check already loaded first 4 symbols(vtmp and tmp2(LU)/tmp1(UL))
    __ zip1(vtmp, __ T8B, vtmp, vtmpZ);
    __ add(str1, str1, isLU ? wordSize/2 : wordSize);
    __ add(str2, str2, isLU ? wordSize : wordSize/2);
    __ fmovd(isLU ? tmp1 : tmp2, vtmp);
    __ subw(cnt2, cnt2, 8); // Already loaded 4 symbols. Last 4 is special case.
    __ eor(rscratch2, tmp1, tmp2);
    __ mov(rscratch1, tmp2);
    __ cbnz(rscratch2, CALCULATE_DIFFERENCE);
    Register tmpU = isLU ? rscratch1 : tmp1, // where to keep U for comparison
             tmpL = isLU ? tmp1 : rscratch1; // where to keep L for comparison
    __ push(spilled_regs, sp);
    __ mov(tmp2, isLU ? str1 : str2); // init the pointer to L next load
    __ mov(cnt1, isLU ? str2 : str1); // init the pointer to U next load

    __ ldr(tmp3, Address(__ post(cnt1, 8)));

    if (SoftwarePrefetchHintDistance >= 0) {
      __ subs(rscratch2, cnt2, prefetchLoopExitCondition);
      __ br(__ LT, NO_PREFETCH);
      __ bind(LARGE_LOOP_PREFETCH);
        __ prfm(Address(tmp2, SoftwarePrefetchHintDistance));
        __ mov(tmp4, 2);
        __ prfm(Address(cnt1, SoftwarePrefetchHintDistance));
        __ bind(LARGE_LOOP_PREFETCH_REPEAT1);
          compare_string_16_x_LU(tmpL, tmpU, DIFF1, DIFF2);
          __ subs(tmp4, tmp4, 1);
          __ br(__ GT, LARGE_LOOP_PREFETCH_REPEAT1);
          __ prfm(Address(cnt1, SoftwarePrefetchHintDistance));
          __ mov(tmp4, 2);
        __ bind(LARGE_LOOP_PREFETCH_REPEAT2);
          compare_string_16_x_LU(tmpL, tmpU, DIFF1, DIFF2);
          __ subs(tmp4, tmp4, 1);
          __ br(__ GT, LARGE_LOOP_PREFETCH_REPEAT2);
          __ sub(cnt2, cnt2, 64);
          __ subs(rscratch2, cnt2, prefetchLoopExitCondition);
          __ br(__ GE, LARGE_LOOP_PREFETCH);
    }
    __ cbz(cnt2, LOAD_LAST); // no characters left except last load
    __ bind(NO_PREFETCH);
    __ subs(cnt2, cnt2, 16);
    __ br(__ LT, TAIL);
    __ align(OptoLoopAlignment);
    __ bind(SMALL_LOOP); // smaller loop
      __ subs(cnt2, cnt2, 16);
      compare_string_16_x_LU(tmpL, tmpU, DIFF1, DIFF2);
      __ br(__ GE, SMALL_LOOP);
      __ cmn(cnt2, (u1)16);
      __ br(__ EQ, LOAD_LAST);
    __ bind(TAIL); // 1..15 characters left until last load (last 4 characters)
      __ add(cnt1, cnt1, cnt2, __ LSL, 1); // Address of 32 bytes before last 4 characters in UTF-16 string
      __ add(tmp2, tmp2, cnt2); // Address of 16 bytes before last 4 characters in Latin1 string
      __ ldr(tmp3, Address(cnt1, -8));
      compare_string_16_x_LU(tmpL, tmpU, DIFF1, DIFF2); // last 16 characters before last load
      __ b(LOAD_LAST);
    __ bind(DIFF2);
      __ mov(tmpU, tmp3);
    __ bind(DIFF1);
      __ pop(spilled_regs, sp);
      __ b(CALCULATE_DIFFERENCE);
    __ bind(LOAD_LAST);
      // Last 4 UTF-16 characters are already pre-loaded into tmp3 by compare_string_16_x_LU.
      // No need to load it again
      __ mov(tmpU, tmp3);
      __ pop(spilled_regs, sp);

      // tmp2 points to the address of the last 4 Latin1 characters right now
      __ ldrs(vtmp, Address(tmp2));
      __ zip1(vtmp, __ T8B, vtmp, vtmpZ);
      __ fmovd(tmpL, vtmp);

      __ eor(rscratch2, tmpU, tmpL);
      __ cbz(rscratch2, DONE);

    // Find the first different characters in the longwords and
    // compute their difference.
    __ bind(CALCULATE_DIFFERENCE);
      __ rev(rscratch2, rscratch2);
      __ clz(rscratch2, rscratch2);
      __ andr(rscratch2, rscratch2, -16);
      __ lsrv(tmp1, tmp1, rscratch2);
      __ uxthw(tmp1, tmp1);
      __ lsrv(rscratch1, rscratch1, rscratch2);
      __ uxthw(rscratch1, rscratch1);
      __ subw(result, tmp1, rscratch1);
    __ bind(DONE);
      __ ret(lr);
    return entry;
  }

  // r0 = input (float16)
  // v0 = result (float)
  // v1 = temporary float register
  address generate_float16ToFloat() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_hf2f_id;
    StubCodeMark mark(this, stub_id);
    address entry = __ pc();
    BLOCK_COMMENT("Entry:");
    __ flt16_to_flt(v0, r0, v1);
    __ ret(lr);
    return entry;
  }

  // v0 = input (float)
  // r0 = result (float16)
  // v1 = temporary float register
  address generate_floatToFloat16() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_f2hf_id;
    StubCodeMark mark(this, stub_id);
    address entry = __ pc();
    BLOCK_COMMENT("Entry:");
    __ flt_to_flt16(r0, v0, v1);
    __ ret(lr);
    return entry;
  }

  address generate_method_entry_barrier() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_method_entry_barrier_id;
    StubCodeMark mark(this, stub_id);

    Label deoptimize_label;

    address start = __ pc();

    BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();

    if (bs_asm->nmethod_patching_type() == NMethodPatchingType::conc_instruction_and_data_patch) {
      BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
      // We can get here despite the nmethod being good, if we have not
      // yet applied our cross modification fence (or data fence).
      Address thread_epoch_addr(rthread, in_bytes(bs_nm->thread_disarmed_guard_value_offset()) + 4);
      __ lea(rscratch2, ExternalAddress(bs_asm->patching_epoch_addr()));
      __ ldrw(rscratch2, rscratch2);
      __ strw(rscratch2, thread_epoch_addr);
      __ isb();
      __ membar(__ LoadLoad);
    }

    __ set_last_Java_frame(sp, rfp, lr, rscratch1);

    __ enter();
    __ add(rscratch2, sp, wordSize);  // rscratch2 points to the saved lr

    __ sub(sp, sp, 4 * wordSize);  // four words for the returned {sp, fp, lr, pc}

    __ push_call_clobbered_registers();

    __ mov(c_rarg0, rscratch2);
    __ call_VM_leaf
         (CAST_FROM_FN_PTR
          (address, BarrierSetNMethod::nmethod_stub_entry_barrier), 1);

    __ reset_last_Java_frame(true);

    __ mov(rscratch1, r0);

    __ pop_call_clobbered_registers();

    __ cbnz(rscratch1, deoptimize_label);

    __ leave();
    __ ret(lr);

    __ BIND(deoptimize_label);

    __ ldp(/* new sp */ rscratch1, rfp, Address(sp, 0 * wordSize));
    __ ldp(lr, /* new pc*/ rscratch2, Address(sp, 2 * wordSize));

    __ mov(sp, rscratch1);
    __ br(rscratch2);

    return start;
  }

  // r0  = result
  // r1  = str1
  // r2  = cnt1
  // r3  = str2
  // r4  = cnt2
  // r10 = tmp1
  // r11 = tmp2
  address generate_compare_long_string_same_encoding(bool isLL) {
    __ align(CodeEntryAlignment);
    StubId stub_id = (isLL ? StubId::stubgen_compare_long_string_LL_id : StubId::stubgen_compare_long_string_UU_id);
    StubCodeMark mark(this, stub_id);
    address entry = __ pc();
    Register result = r0, str1 = r1, cnt1 = r2, str2 = r3, cnt2 = r4,
        tmp1 = r10, tmp2 = r11, tmp1h = rscratch1, tmp2h = rscratch2;

    Label LARGE_LOOP_PREFETCH, LOOP_COMPARE16, DIFF, LESS16, LESS8, CAL_DIFFERENCE, LENGTH_DIFF;

    // exit from large loop when less than 64 bytes left to read or we're about
    // to prefetch memory behind array border
    int largeLoopExitCondition = MAX2(64, SoftwarePrefetchHintDistance)/(isLL ? 1 : 2);

    // before jumping to stub, pre-load 8 bytes already, so do comparison directly
    __ eor(rscratch2, tmp1, tmp2);
    __ cbnz(rscratch2, CAL_DIFFERENCE);

    __ sub(cnt2, cnt2, wordSize/(isLL ? 1 : 2));
    // update pointers, because of previous read
    __ add(str1, str1, wordSize);
    __ add(str2, str2, wordSize);
    if (SoftwarePrefetchHintDistance >= 0) {
      __ align(OptoLoopAlignment);
      __ bind(LARGE_LOOP_PREFETCH);
        __ prfm(Address(str1, SoftwarePrefetchHintDistance));
        __ prfm(Address(str2, SoftwarePrefetchHintDistance));

        for (int i = 0; i < 4; i++) {
          __ ldp(tmp1, tmp1h, Address(str1, i * 16));
          __ ldp(tmp2, tmp2h, Address(str2, i * 16));
          __ cmp(tmp1, tmp2);
          __ ccmp(tmp1h, tmp2h, 0, Assembler::EQ);
          __ br(Assembler::NE, DIFF);
        }
        __ sub(cnt2, cnt2, isLL ? 64 : 32);
        __ add(str1, str1, 64);
        __ add(str2, str2, 64);
        __ subs(rscratch2, cnt2, largeLoopExitCondition);
        __ br(Assembler::GE, LARGE_LOOP_PREFETCH);
        __ cbz(cnt2, LENGTH_DIFF); // no more chars left?
    }

    __ subs(rscratch1, cnt2, isLL ? 16 : 8);
    __ br(Assembler::LE, LESS16);
    __ align(OptoLoopAlignment);
    __ bind(LOOP_COMPARE16);
      __ ldp(tmp1, tmp1h, Address(__ post(str1, 16)));
      __ ldp(tmp2, tmp2h, Address(__ post(str2, 16)));
      __ cmp(tmp1, tmp2);
      __ ccmp(tmp1h, tmp2h, 0, Assembler::EQ);
      __ br(Assembler::NE, DIFF);
      __ sub(cnt2, cnt2, isLL ? 16 : 8);
      __ subs(rscratch2, cnt2, isLL ? 16 : 8);
      __ br(Assembler::LT, LESS16);

      __ ldp(tmp1, tmp1h, Address(__ post(str1, 16)));
      __ ldp(tmp2, tmp2h, Address(__ post(str2, 16)));
      __ cmp(tmp1, tmp2);
      __ ccmp(tmp1h, tmp2h, 0, Assembler::EQ);
      __ br(Assembler::NE, DIFF);
      __ sub(cnt2, cnt2, isLL ? 16 : 8);
      __ subs(rscratch2, cnt2, isLL ? 16 : 8);
      __ br(Assembler::GE, LOOP_COMPARE16);
      __ cbz(cnt2, LENGTH_DIFF);

    __ bind(LESS16);
      // each 8 compare
      __ subs(cnt2, cnt2, isLL ? 8 : 4);
      __ br(Assembler::LE, LESS8);
      __ ldr(tmp1, Address(__ post(str1, 8)));
      __ ldr(tmp2, Address(__ post(str2, 8)));
      __ eor(rscratch2, tmp1, tmp2);
      __ cbnz(rscratch2, CAL_DIFFERENCE);
      __ sub(cnt2, cnt2, isLL ? 8 : 4);

    __ bind(LESS8); // directly load last 8 bytes
      if (!isLL) {
        __ add(cnt2, cnt2, cnt2);
      }
      __ ldr(tmp1, Address(str1, cnt2));
      __ ldr(tmp2, Address(str2, cnt2));
      __ eor(rscratch2, tmp1, tmp2);
      __ cbz(rscratch2, LENGTH_DIFF);
      __ b(CAL_DIFFERENCE);

    __ bind(DIFF);
      __ cmp(tmp1, tmp2);
      __ csel(tmp1, tmp1, tmp1h, Assembler::NE);
      __ csel(tmp2, tmp2, tmp2h, Assembler::NE);
      // reuse rscratch2 register for the result of eor instruction
      __ eor(rscratch2, tmp1, tmp2);

    __ bind(CAL_DIFFERENCE);
      __ rev(rscratch2, rscratch2);
      __ clz(rscratch2, rscratch2);
      __ andr(rscratch2, rscratch2, isLL ? -8 : -16);
      __ lsrv(tmp1, tmp1, rscratch2);
      __ lsrv(tmp2, tmp2, rscratch2);
      if (isLL) {
        __ uxtbw(tmp1, tmp1);
        __ uxtbw(tmp2, tmp2);
      } else {
        __ uxthw(tmp1, tmp1);
        __ uxthw(tmp2, tmp2);
      }
      __ subw(result, tmp1, tmp2);

    __ bind(LENGTH_DIFF);
      __ ret(lr);
    return entry;
  }

  enum string_compare_mode {
    LL,
    LU,
    UL,
    UU,
  };

  // The following registers are declared in aarch64.ad
  // r0  = result
  // r1  = str1
  // r2  = cnt1
  // r3  = str2
  // r4  = cnt2
  // r10 = tmp1
  // r11 = tmp2
  // z0  = ztmp1
  // z1  = ztmp2
  // p0  = pgtmp1
  // p1  = pgtmp2
  address generate_compare_long_string_sve(string_compare_mode mode) {
    StubId stub_id;
    switch (mode) {
      case LL: stub_id = StubId::stubgen_compare_long_string_LL_id;  break;
      case LU: stub_id = StubId::stubgen_compare_long_string_LU_id; break;
      case UL: stub_id = StubId::stubgen_compare_long_string_UL_id; break;
      case UU: stub_id = StubId::stubgen_compare_long_string_UU_id; break;
      default: ShouldNotReachHere();
    }

    __ align(CodeEntryAlignment);
    address entry = __ pc();
    Register result = r0, str1 = r1, cnt1 = r2, str2 = r3, cnt2 = r4,
             tmp1 = r10, tmp2 = r11;

    Label LOOP, DONE, MISMATCH;
    Register vec_len = tmp1;
    Register idx = tmp2;
    // The minimum of the string lengths has been stored in cnt2.
    Register cnt = cnt2;
    FloatRegister ztmp1 = z0, ztmp2 = z1;
    PRegister pgtmp1 = p0, pgtmp2 = p1;

#define LOAD_PAIR(ztmp1, ztmp2, pgtmp1, src1, src2, idx)                       \
    switch (mode) {                                                            \
      case LL:                                                                 \
        __ sve_ld1b(ztmp1, __ B, pgtmp1, Address(str1, idx));                  \
        __ sve_ld1b(ztmp2, __ B, pgtmp1, Address(str2, idx));                  \
        break;                                                                 \
      case LU:                                                                 \
        __ sve_ld1b(ztmp1, __ H, pgtmp1, Address(str1, idx));                  \
        __ sve_ld1h(ztmp2, __ H, pgtmp1, Address(str2, idx, Address::lsl(1))); \
        break;                                                                 \
      case UL:                                                                 \
        __ sve_ld1h(ztmp1, __ H, pgtmp1, Address(str1, idx, Address::lsl(1))); \
        __ sve_ld1b(ztmp2, __ H, pgtmp1, Address(str2, idx));                  \
        break;                                                                 \
      case UU:                                                                 \
        __ sve_ld1h(ztmp1, __ H, pgtmp1, Address(str1, idx, Address::lsl(1))); \
        __ sve_ld1h(ztmp2, __ H, pgtmp1, Address(str2, idx, Address::lsl(1))); \
        break;                                                                 \
      default:                                                                 \
        ShouldNotReachHere();                                                  \
    }

    StubCodeMark mark(this, stub_id);

    __ mov(idx, 0);
    __ sve_whilelt(pgtmp1, mode == LL ? __ B : __ H, idx, cnt);

    if (mode == LL) {
      __ sve_cntb(vec_len);
    } else {
      __ sve_cnth(vec_len);
    }

    __ sub(rscratch1, cnt, vec_len);

    __ bind(LOOP);

      // main loop
      LOAD_PAIR(ztmp1, ztmp2, pgtmp1, src1, src2, idx);
      __ add(idx, idx, vec_len);
      // Compare strings.
      __ sve_cmp(Assembler::NE, pgtmp2, mode == LL ? __ B : __ H, pgtmp1, ztmp1, ztmp2);
      __ br(__ NE, MISMATCH);
      __ cmp(idx, rscratch1);
      __ br(__ LT, LOOP);

    // post loop, last iteration
    __ sve_whilelt(pgtmp1, mode == LL ? __ B : __ H, idx, cnt);

    LOAD_PAIR(ztmp1, ztmp2, pgtmp1, src1, src2, idx);
    __ sve_cmp(Assembler::NE, pgtmp2, mode == LL ? __ B : __ H, pgtmp1, ztmp1, ztmp2);
    __ br(__ EQ, DONE);

    __ bind(MISMATCH);

    // Crop the vector to find its location.
    __ sve_brkb(pgtmp2, pgtmp1, pgtmp2, false /* isMerge */);
    // Extract the first different characters of each string.
    __ sve_lasta(rscratch1, mode == LL ? __ B : __ H, pgtmp2, ztmp1);
    __ sve_lasta(rscratch2, mode == LL ? __ B : __ H, pgtmp2, ztmp2);

    // Compute the difference of the first different characters.
    __ sub(result, rscratch1, rscratch2);

    __ bind(DONE);
    __ ret(lr);
#undef LOAD_PAIR
    return entry;
  }

  void generate_compare_long_strings() {
    if (UseSVE == 0) {
      StubRoutines::aarch64::_compare_long_string_LL
          = generate_compare_long_string_same_encoding(true);
      StubRoutines::aarch64::_compare_long_string_UU
          = generate_compare_long_string_same_encoding(false);
      StubRoutines::aarch64::_compare_long_string_LU
          = generate_compare_long_string_different_encoding(true);
      StubRoutines::aarch64::_compare_long_string_UL
          = generate_compare_long_string_different_encoding(false);
    } else {
      StubRoutines::aarch64::_compare_long_string_LL
          = generate_compare_long_string_sve(LL);
      StubRoutines::aarch64::_compare_long_string_UU
          = generate_compare_long_string_sve(UU);
      StubRoutines::aarch64::_compare_long_string_LU
          = generate_compare_long_string_sve(LU);
      StubRoutines::aarch64::_compare_long_string_UL
          = generate_compare_long_string_sve(UL);
    }
  }

  // R0 = result
  // R1 = str2
  // R2 = cnt1
  // R3 = str1
  // R4 = cnt2
  // Clobbers: rscratch1, rscratch2, v0, v1, rflags
  //
  // This generic linear code use few additional ideas, which makes it faster:
  // 1) we can safely keep at least 1st register of pattern(since length >= 8)
  // in order to skip initial loading(help in systems with 1 ld pipeline)
  // 2) we can use "fast" algorithm of finding single character to search for
  // first symbol with less branches(1 branch per each loaded register instead
  // of branch for each symbol), so, this is where constants like
  // 0x0101...01, 0x00010001...0001, 0x7f7f...7f, 0x7fff7fff...7fff comes from
  // 3) after loading and analyzing 1st register of source string, it can be
  // used to search for every 1st character entry, saving few loads in
  // comparison with "simplier-but-slower" implementation
  // 4) in order to avoid lots of push/pop operations, code below is heavily
  // re-using/re-initializing/compressing register values, which makes code
  // larger and a bit less readable, however, most of extra operations are
  // issued during loads or branches, so, penalty is minimal
  address generate_string_indexof_linear(bool str1_isL, bool str2_isL) {
    StubId stub_id;
    if (str1_isL) {
      if (str2_isL) {
        stub_id = StubId::stubgen_string_indexof_linear_ll_id;
      } else {
        stub_id = StubId::stubgen_string_indexof_linear_ul_id;
      }
    } else {
      if (str2_isL) {
        ShouldNotReachHere();
      } else {
        stub_id = StubId::stubgen_string_indexof_linear_uu_id;
      }
    }
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, stub_id);
    address entry = __ pc();

    int str1_chr_size = str1_isL ? 1 : 2;
    int str2_chr_size = str2_isL ? 1 : 2;
    int str1_chr_shift = str1_isL ? 0 : 1;
    int str2_chr_shift = str2_isL ? 0 : 1;
    bool isL = str1_isL && str2_isL;
   // parameters
    Register result = r0, str2 = r1, cnt1 = r2, str1 = r3, cnt2 = r4;
    // temporary registers
    Register tmp1 = r20, tmp2 = r21, tmp3 = r22, tmp4 = r23;
    RegSet spilled_regs = RegSet::range(tmp1, tmp4);
    // redefinitions
    Register ch1 = rscratch1, ch2 = rscratch2, first = tmp3;

    __ push(spilled_regs, sp);
    Label L_LOOP, L_LOOP_PROCEED, L_SMALL, L_HAS_ZERO,
        L_HAS_ZERO_LOOP, L_CMP_LOOP, L_CMP_LOOP_NOMATCH, L_SMALL_PROCEED,
        L_SMALL_HAS_ZERO_LOOP, L_SMALL_CMP_LOOP_NOMATCH, L_SMALL_CMP_LOOP,
        L_POST_LOOP, L_CMP_LOOP_LAST_CMP, L_HAS_ZERO_LOOP_NOMATCH,
        L_SMALL_CMP_LOOP_LAST_CMP, L_SMALL_CMP_LOOP_LAST_CMP2,
        L_CMP_LOOP_LAST_CMP2, DONE, NOMATCH;
    // Read whole register from str1. It is safe, because length >=8 here
    __ ldr(ch1, Address(str1));
    // Read whole register from str2. It is safe, because length >=8 here
    __ ldr(ch2, Address(str2));
    __ sub(cnt2, cnt2, cnt1);
    __ andr(first, ch1, str1_isL ? 0xFF : 0xFFFF);
    if (str1_isL != str2_isL) {
      __ eor(v0, __ T16B, v0, v0);
    }
    __ mov(tmp1, str2_isL ? 0x0101010101010101 : 0x0001000100010001);
    __ mul(first, first, tmp1);
    // check if we have less than 1 register to check
    __ subs(cnt2, cnt2, wordSize/str2_chr_size - 1);
    if (str1_isL != str2_isL) {
      __ fmovd(v1, ch1);
    }
    __ br(__ LE, L_SMALL);
    __ eor(ch2, first, ch2);
    if (str1_isL != str2_isL) {
      __ zip1(v1, __ T16B, v1, v0);
    }
    __ sub(tmp2, ch2, tmp1);
    __ orr(ch2, ch2, str2_isL ? 0x7f7f7f7f7f7f7f7f : 0x7fff7fff7fff7fff);
    __ bics(tmp2, tmp2, ch2);
    if (str1_isL != str2_isL) {
      __ fmovd(ch1, v1);
    }
    __ br(__ NE, L_HAS_ZERO);
    __ subs(cnt2, cnt2, wordSize/str2_chr_size);
    __ add(result, result, wordSize/str2_chr_size);
    __ add(str2, str2, wordSize);
    __ br(__ LT, L_POST_LOOP);
    __ BIND(L_LOOP);
      __ ldr(ch2, Address(str2));
      __ eor(ch2, first, ch2);
      __ sub(tmp2, ch2, tmp1);
      __ orr(ch2, ch2, str2_isL ? 0x7f7f7f7f7f7f7f7f : 0x7fff7fff7fff7fff);
      __ bics(tmp2, tmp2, ch2);
      __ br(__ NE, L_HAS_ZERO);
    __ BIND(L_LOOP_PROCEED);
      __ subs(cnt2, cnt2, wordSize/str2_chr_size);
      __ add(str2, str2, wordSize);
      __ add(result, result, wordSize/str2_chr_size);
      __ br(__ GE, L_LOOP);
    __ BIND(L_POST_LOOP);
      __ subs(zr, cnt2, -wordSize/str2_chr_size); // no extra characters to check
      __ br(__ LE, NOMATCH);
      __ ldr(ch2, Address(str2));
      __ sub(cnt2, zr, cnt2, __ LSL, LogBitsPerByte + str2_chr_shift);
      __ eor(ch2, first, ch2);
      __ sub(tmp2, ch2, tmp1);
      __ orr(ch2, ch2, str2_isL ? 0x7f7f7f7f7f7f7f7f : 0x7fff7fff7fff7fff);
      __ mov(tmp4, -1); // all bits set
      __ b(L_SMALL_PROCEED);
    __ align(OptoLoopAlignment);
    __ BIND(L_SMALL);
      __ sub(cnt2, zr, cnt2, __ LSL, LogBitsPerByte + str2_chr_shift);
      __ eor(ch2, first, ch2);
      if (str1_isL != str2_isL) {
        __ zip1(v1, __ T16B, v1, v0);
      }
      __ sub(tmp2, ch2, tmp1);
      __ mov(tmp4, -1); // all bits set
      __ orr(ch2, ch2, str2_isL ? 0x7f7f7f7f7f7f7f7f : 0x7fff7fff7fff7fff);
      if (str1_isL != str2_isL) {
        __ fmovd(ch1, v1); // move converted 4 symbols
      }
    __ BIND(L_SMALL_PROCEED);
      __ lsrv(tmp4, tmp4, cnt2); // mask. zeroes on useless bits.
      __ bic(tmp2, tmp2, ch2);
      __ ands(tmp2, tmp2, tmp4); // clear useless bits and check
      __ rbit(tmp2, tmp2);
      __ br(__ EQ, NOMATCH);
    __ BIND(L_SMALL_HAS_ZERO_LOOP);
      __ clz(tmp4, tmp2); // potentially long. Up to 4 cycles on some cpu's
      __ cmp(cnt1, u1(wordSize/str2_chr_size));
      __ br(__ LE, L_SMALL_CMP_LOOP_LAST_CMP2);
      if (str2_isL) { // LL
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte); // address of "index"
        __ ldr(ch2, Address(str2)); // read whole register of str2. Safe.
        __ lslv(tmp2, tmp2, tmp4); // shift off leading zeroes from match info
        __ add(result, result, tmp4, __ LSR, LogBitsPerByte);
        __ lsl(tmp2, tmp2, 1); // shift off leading "1" from match info
      } else {
        __ mov(ch2, 0xE); // all bits in byte set except last one
        __ andr(ch2, ch2, tmp4, __ LSR, LogBitsPerByte); // byte shift amount
        __ ldr(ch2, Address(str2, ch2)); // read whole register of str2. Safe.
        __ lslv(tmp2, tmp2, tmp4);
        __ add(result, result, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ lsl(tmp2, tmp2, 1); // shift off leading "1" from match info
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
      }
      __ cmp(ch1, ch2);
      __ mov(tmp4, wordSize/str2_chr_size);
      __ br(__ NE, L_SMALL_CMP_LOOP_NOMATCH);
    __ BIND(L_SMALL_CMP_LOOP);
      str1_isL ? __ ldrb(first, Address(str1, tmp4, Address::lsl(str1_chr_shift)))
               : __ ldrh(first, Address(str1, tmp4, Address::lsl(str1_chr_shift)));
      str2_isL ? __ ldrb(ch2, Address(str2, tmp4, Address::lsl(str2_chr_shift)))
               : __ ldrh(ch2, Address(str2, tmp4, Address::lsl(str2_chr_shift)));
      __ add(tmp4, tmp4, 1);
      __ cmp(tmp4, cnt1);
      __ br(__ GE, L_SMALL_CMP_LOOP_LAST_CMP);
      __ cmp(first, ch2);
      __ br(__ EQ, L_SMALL_CMP_LOOP);
    __ BIND(L_SMALL_CMP_LOOP_NOMATCH);
      __ cbz(tmp2, NOMATCH); // no more matches. exit
      __ clz(tmp4, tmp2);
      __ add(result, result, 1); // advance index
      __ add(str2, str2, str2_chr_size); // advance pointer
      __ b(L_SMALL_HAS_ZERO_LOOP);
    __ align(OptoLoopAlignment);
    __ BIND(L_SMALL_CMP_LOOP_LAST_CMP);
      __ cmp(first, ch2);
      __ br(__ NE, L_SMALL_CMP_LOOP_NOMATCH);
      __ b(DONE);
    __ align(OptoLoopAlignment);
    __ BIND(L_SMALL_CMP_LOOP_LAST_CMP2);
      if (str2_isL) { // LL
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte); // address of "index"
        __ ldr(ch2, Address(str2)); // read whole register of str2. Safe.
        __ lslv(tmp2, tmp2, tmp4); // shift off leading zeroes from match info
        __ add(result, result, tmp4, __ LSR, LogBitsPerByte);
        __ lsl(tmp2, tmp2, 1); // shift off leading "1" from match info
      } else {
        __ mov(ch2, 0xE); // all bits in byte set except last one
        __ andr(ch2, ch2, tmp4, __ LSR, LogBitsPerByte); // byte shift amount
        __ ldr(ch2, Address(str2, ch2)); // read whole register of str2. Safe.
        __ lslv(tmp2, tmp2, tmp4);
        __ add(result, result, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ lsl(tmp2, tmp2, 1); // shift off leading "1" from match info
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
      }
      __ cmp(ch1, ch2);
      __ br(__ NE, L_SMALL_CMP_LOOP_NOMATCH);
      __ b(DONE);
    __ align(OptoLoopAlignment);
    __ BIND(L_HAS_ZERO);
      __ rbit(tmp2, tmp2);
      __ clz(tmp4, tmp2); // potentially long. Up to 4 cycles on some CPU's
      // Now, perform compression of counters(cnt2 and cnt1) into one register.
      // It's fine because both counters are 32bit and are not changed in this
      // loop. Just restore it on exit. So, cnt1 can be re-used in this loop.
      __ orr(cnt2, cnt2, cnt1, __ LSL, BitsPerByte * wordSize / 2);
      __ sub(result, result, 1);
    __ BIND(L_HAS_ZERO_LOOP);
      __ mov(cnt1, wordSize/str2_chr_size);
      __ cmp(cnt1, cnt2, __ LSR, BitsPerByte * wordSize / 2);
      __ br(__ GE, L_CMP_LOOP_LAST_CMP2); // case of 8 bytes only to compare
      if (str2_isL) {
        __ lsr(ch2, tmp4, LogBitsPerByte + str2_chr_shift); // char index
        __ ldr(ch2, Address(str2, ch2)); // read whole register of str2. Safe.
        __ lslv(tmp2, tmp2, tmp4);
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ add(tmp4, tmp4, 1);
        __ add(result, result, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ lsl(tmp2, tmp2, 1);
        __ mov(tmp4, wordSize/str2_chr_size);
      } else {
        __ mov(ch2, 0xE);
        __ andr(ch2, ch2, tmp4, __ LSR, LogBitsPerByte); // byte shift amount
        __ ldr(ch2, Address(str2, ch2)); // read whole register of str2. Safe.
        __ lslv(tmp2, tmp2, tmp4);
        __ add(tmp4, tmp4, 1);
        __ add(result, result, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte);
        __ lsl(tmp2, tmp2, 1);
        __ mov(tmp4, wordSize/str2_chr_size);
        __ sub(str2, str2, str2_chr_size);
      }
      __ cmp(ch1, ch2);
      __ mov(tmp4, wordSize/str2_chr_size);
      __ br(__ NE, L_CMP_LOOP_NOMATCH);
    __ BIND(L_CMP_LOOP);
      str1_isL ? __ ldrb(cnt1, Address(str1, tmp4, Address::lsl(str1_chr_shift)))
               : __ ldrh(cnt1, Address(str1, tmp4, Address::lsl(str1_chr_shift)));
      str2_isL ? __ ldrb(ch2, Address(str2, tmp4, Address::lsl(str2_chr_shift)))
               : __ ldrh(ch2, Address(str2, tmp4, Address::lsl(str2_chr_shift)));
      __ add(tmp4, tmp4, 1);
      __ cmp(tmp4, cnt2, __ LSR, BitsPerByte * wordSize / 2);
      __ br(__ GE, L_CMP_LOOP_LAST_CMP);
      __ cmp(cnt1, ch2);
      __ br(__ EQ, L_CMP_LOOP);
    __ BIND(L_CMP_LOOP_NOMATCH);
      // here we're not matched
      __ cbz(tmp2, L_HAS_ZERO_LOOP_NOMATCH); // no more matches. Proceed to main loop
      __ clz(tmp4, tmp2);
      __ add(str2, str2, str2_chr_size); // advance pointer
      __ b(L_HAS_ZERO_LOOP);
    __ align(OptoLoopAlignment);
    __ BIND(L_CMP_LOOP_LAST_CMP);
      __ cmp(cnt1, ch2);
      __ br(__ NE, L_CMP_LOOP_NOMATCH);
      __ b(DONE);
    __ align(OptoLoopAlignment);
    __ BIND(L_CMP_LOOP_LAST_CMP2);
      if (str2_isL) {
        __ lsr(ch2, tmp4, LogBitsPerByte + str2_chr_shift); // char index
        __ ldr(ch2, Address(str2, ch2)); // read whole register of str2. Safe.
        __ lslv(tmp2, tmp2, tmp4);
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ add(tmp4, tmp4, 1);
        __ add(result, result, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ lsl(tmp2, tmp2, 1);
      } else {
        __ mov(ch2, 0xE);
        __ andr(ch2, ch2, tmp4, __ LSR, LogBitsPerByte); // byte shift amount
        __ ldr(ch2, Address(str2, ch2)); // read whole register of str2. Safe.
        __ lslv(tmp2, tmp2, tmp4);
        __ add(tmp4, tmp4, 1);
        __ add(result, result, tmp4, __ LSR, LogBitsPerByte + str2_chr_shift);
        __ add(str2, str2, tmp4, __ LSR, LogBitsPerByte);
        __ lsl(tmp2, tmp2, 1);
        __ sub(str2, str2, str2_chr_size);
      }
      __ cmp(ch1, ch2);
      __ br(__ NE, L_CMP_LOOP_NOMATCH);
      __ b(DONE);
    __ align(OptoLoopAlignment);
    __ BIND(L_HAS_ZERO_LOOP_NOMATCH);
      // 1) Restore "result" index. Index was wordSize/str2_chr_size * N until
      // L_HAS_ZERO block. Byte octet was analyzed in L_HAS_ZERO_LOOP,
      // so, result was increased at max by wordSize/str2_chr_size - 1, so,
      // respective high bit wasn't changed. L_LOOP_PROCEED will increase
      // result by analyzed characters value, so, we can just reset lower bits
      // in result here. Clear 2 lower bits for UU/UL and 3 bits for LL
      // 2) restore cnt1 and cnt2 values from "compressed" cnt2
      // 3) advance str2 value to represent next str2 octet. result & 7/3 is
      // index of last analyzed substring inside current octet. So, str2 in at
      // respective start address. We need to advance it to next octet
      __ andr(tmp2, result, wordSize/str2_chr_size - 1); // symbols analyzed
      __ lsr(cnt1, cnt2, BitsPerByte * wordSize / 2);
      __ bfm(result, zr, 0, 2 - str2_chr_shift);
      __ sub(str2, str2, tmp2, __ LSL, str2_chr_shift); // restore str2
      __ movw(cnt2, cnt2);
      __ b(L_LOOP_PROCEED);
    __ align(OptoLoopAlignment);
    __ BIND(NOMATCH);
      __ mov(result, -1);
    __ BIND(DONE);
      __ pop(spilled_regs, sp);
      __ ret(lr);
    return entry;
  }

  void generate_string_indexof_stubs() {
    StubRoutines::aarch64::_string_indexof_linear_ll = generate_string_indexof_linear(true, true);
    StubRoutines::aarch64::_string_indexof_linear_uu = generate_string_indexof_linear(false, false);
    StubRoutines::aarch64::_string_indexof_linear_ul = generate_string_indexof_linear(true, false);
  }

  void inflate_and_store_2_fp_registers(bool generatePrfm,
      FloatRegister src1, FloatRegister src2) {
    Register dst = r1;
    __ zip1(v1, __ T16B, src1, v0);
    __ zip2(v2, __ T16B, src1, v0);
    if (generatePrfm) {
      __ prfm(Address(dst, SoftwarePrefetchHintDistance), PSTL1STRM);
    }
    __ zip1(v3, __ T16B, src2, v0);
    __ zip2(v4, __ T16B, src2, v0);
    __ st1(v1, v2, v3, v4, __ T16B, Address(__ post(dst, 64)));
  }

  // R0 = src
  // R1 = dst
  // R2 = len
  // R3 = len >> 3
  // V0 = 0
  // v1 = loaded 8 bytes
  // Clobbers: r0, r1, r3, rscratch1, rflags, v0-v6
  address generate_large_byte_array_inflate() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_large_byte_array_inflate_id;
    StubCodeMark mark(this, stub_id);
    address entry = __ pc();
    Label LOOP, LOOP_START, LOOP_PRFM, LOOP_PRFM_START, DONE;
    Register src = r0, dst = r1, len = r2, octetCounter = r3;
    const int large_loop_threshold = MAX2(64, SoftwarePrefetchHintDistance)/8 + 4;

    // do one more 8-byte read to have address 16-byte aligned in most cases
    // also use single store instruction
    __ ldrd(v2, __ post(src, 8));
    __ sub(octetCounter, octetCounter, 2);
    __ zip1(v1, __ T16B, v1, v0);
    __ zip1(v2, __ T16B, v2, v0);
    __ st1(v1, v2, __ T16B, __ post(dst, 32));
    __ ld1(v3, v4, v5, v6, __ T16B, Address(__ post(src, 64)));
    __ subs(rscratch1, octetCounter, large_loop_threshold);
    __ br(__ LE, LOOP_START);
    __ b(LOOP_PRFM_START);
    __ bind(LOOP_PRFM);
      __ ld1(v3, v4, v5, v6, __ T16B, Address(__ post(src, 64)));
    __ bind(LOOP_PRFM_START);
      __ prfm(Address(src, SoftwarePrefetchHintDistance));
      __ sub(octetCounter, octetCounter, 8);
      __ subs(rscratch1, octetCounter, large_loop_threshold);
      inflate_and_store_2_fp_registers(true, v3, v4);
      inflate_and_store_2_fp_registers(true, v5, v6);
      __ br(__ GT, LOOP_PRFM);
      __ cmp(octetCounter, (u1)8);
      __ br(__ LT, DONE);
    __ bind(LOOP);
      __ ld1(v3, v4, v5, v6, __ T16B, Address(__ post(src, 64)));
      __ bind(LOOP_START);
      __ sub(octetCounter, octetCounter, 8);
      __ cmp(octetCounter, (u1)8);
      inflate_and_store_2_fp_registers(false, v3, v4);
      inflate_and_store_2_fp_registers(false, v5, v6);
      __ br(__ GE, LOOP);
    __ bind(DONE);
      __ ret(lr);
    return entry;
  }

  /**
   *  Arguments:
   *
   *  Input:
   *  c_rarg0   - current state address
   *  c_rarg1   - H key address
   *  c_rarg2   - data address
   *  c_rarg3   - number of blocks
   *
   *  Output:
   *  Updated state at c_rarg0
   */
  address generate_ghash_processBlocks() {
    // Bafflingly, GCM uses little-endian for the byte order, but
    // big-endian for the bit order.  For example, the polynomial 1 is
    // represented as the 16-byte string 80 00 00 00 | 12 bytes of 00.
    //
    // So, we must either reverse the bytes in each word and do
    // everything big-endian or reverse the bits in each byte and do
    // it little-endian.  On AArch64 it's more idiomatic to reverse
    // the bits in each byte (we have an instruction, RBIT, to do
    // that) and keep the data in little-endian bit order through the
    // calculation, bit-reversing the inputs and outputs.

    StubId stub_id = StubId::stubgen_ghash_processBlocks_id;
    StubCodeMark mark(this, stub_id);
    __ align(wordSize * 2);
    address p = __ pc();
    __ emit_int64(0x87);  // The low-order bits of the field
                          // polynomial (i.e. p = z^7+z^2+z+1)
                          // repeated in the low and high parts of a
                          // 128-bit vector
    __ emit_int64(0x87);

    __ align(CodeEntryAlignment);
    address start = __ pc();

    Register state   = c_rarg0;
    Register subkeyH = c_rarg1;
    Register data    = c_rarg2;
    Register blocks  = c_rarg3;

    FloatRegister vzr = v30;
    __ eor(vzr, __ T16B, vzr, vzr); // zero register

    __ ldrq(v24, p);    // The field polynomial

    __ ldrq(v0, Address(state));
    __ ldrq(v1, Address(subkeyH));

    __ rev64(v0, __ T16B, v0);          // Bit-reverse words in state and subkeyH
    __ rbit(v0, __ T16B, v0);
    __ rev64(v1, __ T16B, v1);
    __ rbit(v1, __ T16B, v1);

    __ ext(v4, __ T16B, v1, v1, 0x08); // long-swap subkeyH into v1
    __ eor(v4, __ T16B, v4, v1);       // xor subkeyH into subkeyL (Karatsuba: (A1+A0))

    {
      Label L_ghash_loop;
      __ bind(L_ghash_loop);

      __ ldrq(v2, Address(__ post(data, 0x10))); // Load the data, bit
                                                 // reversing each byte
      __ rbit(v2, __ T16B, v2);
      __ eor(v2, __ T16B, v0, v2);   // bit-swapped data ^ bit-swapped state

      // Multiply state in v2 by subkey in v1
      __ ghash_multiply(/*result_lo*/v5, /*result_hi*/v7,
                        /*a*/v1, /*b*/v2, /*a1_xor_a0*/v4,
                        /*temps*/v6, v3, /*reuse/clobber b*/v2);
      // Reduce v7:v5 by the field polynomial
      __ ghash_reduce(/*result*/v0, /*lo*/v5, /*hi*/v7, /*p*/v24, vzr, /*temp*/v3);

      __ sub(blocks, blocks, 1);
      __ cbnz(blocks, L_ghash_loop);
    }

    // The bit-reversed result is at this point in v0
    __ rev64(v0, __ T16B, v0);
    __ rbit(v0, __ T16B, v0);

    __ st1(v0, __ T16B, state);
    __ ret(lr);

    return start;
  }

  address generate_ghash_processBlocks_wide() {
    address small = generate_ghash_processBlocks();

    StubId stub_id = StubId::stubgen_ghash_processBlocks_wide_id;
    StubCodeMark mark(this, stub_id);
    __ align(wordSize * 2);
    address p = __ pc();
    __ emit_int64(0x87);  // The low-order bits of the field
                          // polynomial (i.e. p = z^7+z^2+z+1)
                          // repeated in the low and high parts of a
                          // 128-bit vector
    __ emit_int64(0x87);

    __ align(CodeEntryAlignment);
    address start = __ pc();

    Register state   = c_rarg0;
    Register subkeyH = c_rarg1;
    Register data    = c_rarg2;
    Register blocks  = c_rarg3;

    const int unroll = 4;

    __ cmp(blocks, (unsigned char)(unroll * 2));
    __ br(__ LT, small);

    if (unroll > 1) {
    // Save state before entering routine
      __ sub(sp, sp, 4 * 16);
      __ st1(v12, v13, v14, v15, __ T16B, Address(sp));
      __ sub(sp, sp, 4 * 16);
      __ st1(v8, v9, v10, v11, __ T16B, Address(sp));
    }

    __ ghash_processBlocks_wide(p, state, subkeyH, data, blocks, unroll);

    if (unroll > 1) {
      // And restore state
      __ ld1(v8, v9, v10, v11, __ T16B, __ post(sp, 4 * 16));
      __ ld1(v12, v13, v14, v15, __ T16B, __ post(sp, 4 * 16));
    }

    __ cmp(blocks, (unsigned char)0);
    __ br(__ GT, small);

    __ ret(lr);

    return start;
  }

  void generate_base64_encode_simdround(Register src, Register dst,
        FloatRegister codec, u8 size) {

    FloatRegister in0  = v4,  in1  = v5,  in2  = v6;
    FloatRegister out0 = v16, out1 = v17, out2 = v18, out3 = v19;
    FloatRegister ind0 = v20, ind1 = v21, ind2 = v22, ind3 = v23;

    Assembler::SIMD_Arrangement arrangement = size == 16 ? __ T16B : __ T8B;

    __ ld3(in0, in1, in2, arrangement, __ post(src, 3 * size));

    __ ushr(ind0, arrangement, in0,  2);

    __ ushr(ind1, arrangement, in1,  2);
    __ shl(in0,   arrangement, in0,  6);
    __ orr(ind1,  arrangement, ind1, in0);
    __ ushr(ind1, arrangement, ind1, 2);

    __ ushr(ind2, arrangement, in2,  4);
    __ shl(in1,   arrangement, in1,  4);
    __ orr(ind2,  arrangement, in1,  ind2);
    __ ushr(ind2, arrangement, ind2, 2);

    __ shl(ind3,  arrangement, in2,  2);
    __ ushr(ind3, arrangement, ind3, 2);

    __ tbl(out0,  arrangement, codec,  4, ind0);
    __ tbl(out1,  arrangement, codec,  4, ind1);
    __ tbl(out2,  arrangement, codec,  4, ind2);
    __ tbl(out3,  arrangement, codec,  4, ind3);

    __ st4(out0,  out1, out2, out3, arrangement, __ post(dst, 4 * size));
  }

   /**
   *  Arguments:
   *
   *  Input:
   *  c_rarg0   - src_start
   *  c_rarg1   - src_offset
   *  c_rarg2   - src_length
   *  c_rarg3   - dest_start
   *  c_rarg4   - dest_offset
   *  c_rarg5   - isURL
   *
   */
  address generate_base64_encodeBlock() {

    static const char toBase64[64] = {
      'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
      'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
      'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
      'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    };

    static const char toBase64URL[64] = {
      'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
      'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
      'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
      'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
    };

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_base64_encodeBlock_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Register src   = c_rarg0;  // source array
    Register soff  = c_rarg1;  // source start offset
    Register send  = c_rarg2;  // source end offset
    Register dst   = c_rarg3;  // dest array
    Register doff  = c_rarg4;  // position for writing to dest array
    Register isURL = c_rarg5;  // Base64 or URL character set

    // c_rarg6 and c_rarg7 are free to use as temps
    Register codec  = c_rarg6;
    Register length = c_rarg7;

    Label ProcessData, Process48B, Process24B, Process3B, SIMDExit, Exit;

    __ add(src, src, soff);
    __ add(dst, dst, doff);
    __ sub(length, send, soff);

    // load the codec base address
    __ lea(codec, ExternalAddress((address) toBase64));
    __ cbz(isURL, ProcessData);
    __ lea(codec, ExternalAddress((address) toBase64URL));

    __ BIND(ProcessData);

    // too short to formup a SIMD loop, roll back
    __ cmp(length, (u1)24);
    __ br(Assembler::LT, Process3B);

    __ ld1(v0, v1, v2, v3, __ T16B, Address(codec));

    __ BIND(Process48B);
    __ cmp(length, (u1)48);
    __ br(Assembler::LT, Process24B);
    generate_base64_encode_simdround(src, dst, v0, 16);
    __ sub(length, length, 48);
    __ b(Process48B);

    __ BIND(Process24B);
    __ cmp(length, (u1)24);
    __ br(Assembler::LT, SIMDExit);
    generate_base64_encode_simdround(src, dst, v0, 8);
    __ sub(length, length, 24);

    __ BIND(SIMDExit);
    __ cbz(length, Exit);

    __ BIND(Process3B);
    //  3 src bytes, 24 bits
    __ ldrb(r10, __ post(src, 1));
    __ ldrb(r11, __ post(src, 1));
    __ ldrb(r12, __ post(src, 1));
    __ orrw(r11, r11, r10, Assembler::LSL, 8);
    __ orrw(r12, r12, r11, Assembler::LSL, 8);
    // codec index
    __ ubfmw(r15, r12, 18, 23);
    __ ubfmw(r14, r12, 12, 17);
    __ ubfmw(r13, r12, 6,  11);
    __ andw(r12,  r12, 63);
    // get the code based on the codec
    __ ldrb(r15, Address(codec, r15, Address::uxtw(0)));
    __ ldrb(r14, Address(codec, r14, Address::uxtw(0)));
    __ ldrb(r13, Address(codec, r13, Address::uxtw(0)));
    __ ldrb(r12, Address(codec, r12, Address::uxtw(0)));
    __ strb(r15, __ post(dst, 1));
    __ strb(r14, __ post(dst, 1));
    __ strb(r13, __ post(dst, 1));
    __ strb(r12, __ post(dst, 1));
    __ sub(length, length, 3);
    __ cbnz(length, Process3B);

    __ BIND(Exit);
    __ ret(lr);

    return start;
  }

  void generate_base64_decode_simdround(Register src, Register dst,
        FloatRegister codecL, FloatRegister codecH, int size, Label& Exit) {

    FloatRegister in0  = v16, in1  = v17,  in2 = v18,  in3 = v19;
    FloatRegister out0 = v20, out1 = v21, out2 = v22;

    FloatRegister decL0 = v23, decL1 = v24, decL2 = v25, decL3 = v26;
    FloatRegister decH0 = v28, decH1 = v29, decH2 = v30, decH3 = v31;

    Label NoIllegalData, ErrorInLowerHalf, StoreLegalData;

    Assembler::SIMD_Arrangement arrangement = size == 16 ? __ T16B : __ T8B;

    __ ld4(in0, in1, in2, in3, arrangement, __ post(src, 4 * size));

    // we need unsigned saturating subtract, to make sure all input values
    // in range [0, 63] will have 0U value in the higher half lookup
    __ uqsubv(decH0, __ T16B, in0, v27);
    __ uqsubv(decH1, __ T16B, in1, v27);
    __ uqsubv(decH2, __ T16B, in2, v27);
    __ uqsubv(decH3, __ T16B, in3, v27);

    // lower half lookup
    __ tbl(decL0, arrangement, codecL, 4, in0);
    __ tbl(decL1, arrangement, codecL, 4, in1);
    __ tbl(decL2, arrangement, codecL, 4, in2);
    __ tbl(decL3, arrangement, codecL, 4, in3);

    // higher half lookup
    __ tbx(decH0, arrangement, codecH, 4, decH0);
    __ tbx(decH1, arrangement, codecH, 4, decH1);
    __ tbx(decH2, arrangement, codecH, 4, decH2);
    __ tbx(decH3, arrangement, codecH, 4, decH3);

    // combine lower and higher
    __ orr(decL0, arrangement, decL0, decH0);
    __ orr(decL1, arrangement, decL1, decH1);
    __ orr(decL2, arrangement, decL2, decH2);
    __ orr(decL3, arrangement, decL3, decH3);

    // check illegal inputs, value larger than 63 (maximum of 6 bits)
    __ cm(Assembler::HI, decH0, arrangement, decL0, v27);
    __ cm(Assembler::HI, decH1, arrangement, decL1, v27);
    __ cm(Assembler::HI, decH2, arrangement, decL2, v27);
    __ cm(Assembler::HI, decH3, arrangement, decL3, v27);
    __ orr(in0, arrangement, decH0, decH1);
    __ orr(in1, arrangement, decH2, decH3);
    __ orr(in2, arrangement, in0,   in1);
    __ umaxv(in3, arrangement, in2);
    __ umov(rscratch2, in3, __ B, 0);

    // get the data to output
    __ shl(out0,  arrangement, decL0, 2);
    __ ushr(out1, arrangement, decL1, 4);
    __ orr(out0,  arrangement, out0,  out1);
    __ shl(out1,  arrangement, decL1, 4);
    __ ushr(out2, arrangement, decL2, 2);
    __ orr(out1,  arrangement, out1,  out2);
    __ shl(out2,  arrangement, decL2, 6);
    __ orr(out2,  arrangement, out2,  decL3);

    __ cbz(rscratch2, NoIllegalData);

    // handle illegal input
    __ umov(r10, in2, __ D, 0);
    if (size == 16) {
      __ cbnz(r10, ErrorInLowerHalf);

      // illegal input is in higher half, store the lower half now.
      __ st3(out0, out1, out2, __ T8B, __ post(dst, 24));

      __ umov(r10, in2,  __ D, 1);
      __ umov(r11, out0, __ D, 1);
      __ umov(r12, out1, __ D, 1);
      __ umov(r13, out2, __ D, 1);
      __ b(StoreLegalData);

      __ BIND(ErrorInLowerHalf);
    }
    __ umov(r11, out0, __ D, 0);
    __ umov(r12, out1, __ D, 0);
    __ umov(r13, out2, __ D, 0);

    __ BIND(StoreLegalData);
    __ tbnz(r10, 5, Exit); // 0xff indicates illegal input
    __ strb(r11, __ post(dst, 1));
    __ strb(r12, __ post(dst, 1));
    __ strb(r13, __ post(dst, 1));
    __ lsr(r10, r10, 8);
    __ lsr(r11, r11, 8);
    __ lsr(r12, r12, 8);
    __ lsr(r13, r13, 8);
    __ b(StoreLegalData);

    __ BIND(NoIllegalData);
    __ st3(out0, out1, out2, arrangement, __ post(dst, 3 * size));
  }


   /**
   *  Arguments:
   *
   *  Input:
   *  c_rarg0   - src_start
   *  c_rarg1   - src_offset
   *  c_rarg2   - src_length
   *  c_rarg3   - dest_start
   *  c_rarg4   - dest_offset
   *  c_rarg5   - isURL
   *  c_rarg6   - isMIME
   *
   */
  address generate_base64_decodeBlock() {

    // The SIMD part of this Base64 decode intrinsic is based on the algorithm outlined
    // on http://0x80.pl/articles/base64-simd-neon.html#encoding-quadwords, in section
    // titled "Base64 decoding".

    // Non-SIMD lookup tables are mostly dumped from fromBase64 array used in java.util.Base64,
    // except the trailing character '=' is also treated illegal value in this intrinsic. That
    // is java.util.Base64.fromBase64['='] = -2, while fromBase(URL)64ForNoSIMD['='] = 255 here.
    static const uint8_t fromBase64ForNoSIMD[256] = {
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,  62u, 255u, 255u, 255u,  63u,
       52u,  53u,  54u,  55u,  56u,  57u,  58u,  59u,  60u,  61u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u,   0u,   1u,   2u,   3u,   4u,   5u,   6u,   7u,   8u,   9u,  10u,  11u,  12u,  13u,  14u,
       15u,  16u,  17u,  18u,  19u,  20u,  21u,  22u,  23u,  24u,  25u, 255u, 255u, 255u, 255u, 255u,
      255u,  26u,  27u,  28u,  29u,  30u,  31u,  32u,  33u,  34u,  35u,  36u,  37u,  38u,  39u,  40u,
       41u,  42u,  43u,  44u,  45u,  46u,  47u,  48u,  49u,  50u,  51u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
    };

    static const uint8_t fromBase64URLForNoSIMD[256] = {
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,  62u, 255u, 255u,
       52u,  53u,  54u,  55u,  56u,  57u,  58u,  59u,  60u,  61u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u,   0u,   1u,   2u,   3u,   4u,   5u,   6u,   7u,   8u,   9u,  10u,  11u,  12u,  13u,  14u,
       15u,  16u,  17u,  18u,  19u,  20u,  21u,  22u,  23u,  24u,  25u, 255u, 255u, 255u, 255u,  63u,
      255u,  26u,  27u,  28u,  29u,  30u,  31u,  32u,  33u,  34u,  35u,  36u,  37u,  38u,  39u,  40u,
       41u,  42u,  43u,  44u,  45u,  46u,  47u,  48u,  49u,  50u,  51u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
    };

    // A legal value of base64 code is in range [0, 127].  We need two lookups
    // with tbl/tbx and combine them to get the decode data. The 1st table vector
    // lookup use tbl, out of range indices are set to 0 in destination. The 2nd
    // table vector lookup use tbx, out of range indices are unchanged in
    // destination. Input [64..126] is mapped to index [65, 127] in second lookup.
    // The value of index 64 is set to 0, so that we know that we already get the
    // decoded data with the 1st lookup.
    static const uint8_t fromBase64ForSIMD[128] = {
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,  62u, 255u, 255u, 255u,  63u,
       52u,  53u,  54u,  55u,  56u,  57u,  58u,  59u,  60u,  61u, 255u, 255u, 255u, 255u, 255u, 255u,
        0u, 255u,   0u,   1u,   2u,   3u,   4u,   5u,   6u,   7u,   8u,   9u,  10u,  11u,  12u,  13u,
       14u,  15u,  16u,  17u,  18u,  19u,  20u,  21u,  22u,  23u,  24u,  25u, 255u, 255u, 255u, 255u,
      255u, 255u,  26u,  27u,  28u,  29u,  30u,  31u,  32u,  33u,  34u,  35u,  36u,  37u,  38u,  39u,
       40u,  41u,  42u,  43u,  44u,  45u,  46u,  47u,  48u,  49u,  50u,  51u, 255u, 255u, 255u, 255u,
    };

    static const uint8_t fromBase64URLForSIMD[128] = {
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,
      255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u, 255u,  62u, 255u, 255u,
       52u,  53u,  54u,  55u,  56u,  57u,  58u,  59u,  60u,  61u, 255u, 255u, 255u, 255u, 255u, 255u,
        0u, 255u,   0u,   1u,   2u,   3u,   4u,   5u,   6u,   7u,   8u,   9u,  10u,  11u,  12u,  13u,
       14u,  15u,  16u,  17u,  18u,  19u,  20u,  21u,  22u,  23u,  24u,  25u, 255u, 255u, 255u, 255u,
       63u, 255u,  26u,  27u,  28u,  29u,  30u,  31u,  32u,  33u,  34u,  35u,  36u,  37u,  38u,  39u,
       40u,  41u,  42u,  43u,  44u,  45u,  46u,  47u,  48u,  49u,  50u,  51u, 255u, 255u, 255u, 255u,
    };

    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_base64_decodeBlock_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    Register src    = c_rarg0;  // source array
    Register soff   = c_rarg1;  // source start offset
    Register send   = c_rarg2;  // source end offset
    Register dst    = c_rarg3;  // dest array
    Register doff   = c_rarg4;  // position for writing to dest array
    Register isURL  = c_rarg5;  // Base64 or URL character set
    Register isMIME = c_rarg6;  // Decoding MIME block - unused in this implementation

    Register length = send;    // reuse send as length of source data to process

    Register simd_codec   = c_rarg6;
    Register nosimd_codec = c_rarg7;

    Label ProcessData, Process64B, Process32B, Process4B, SIMDEnter, SIMDExit, Exit;

    __ enter();

    __ add(src, src, soff);
    __ add(dst, dst, doff);

    __ mov(doff, dst);

    __ sub(length, send, soff);
    __ bfm(length, zr, 0, 1);

    __ lea(nosimd_codec, ExternalAddress((address) fromBase64ForNoSIMD));
    __ cbz(isURL, ProcessData);
    __ lea(nosimd_codec, ExternalAddress((address) fromBase64URLForNoSIMD));

    __ BIND(ProcessData);
    __ mov(rscratch1, length);
    __ cmp(length, (u1)144); // 144 = 80 + 64
    __ br(Assembler::LT, Process4B);

    // In the MIME case, the line length cannot be more than 76
    // bytes (see RFC 2045). This is too short a block for SIMD
    // to be worthwhile, so we use non-SIMD here.
    __ movw(rscratch1, 79);

    __ BIND(Process4B);
    __ ldrw(r14, __ post(src, 4));
    __ ubfxw(r10, r14, 0,  8);
    __ ubfxw(r11, r14, 8,  8);
    __ ubfxw(r12, r14, 16, 8);
    __ ubfxw(r13, r14, 24, 8);
    // get the de-code
    __ ldrb(r10, Address(nosimd_codec, r10, Address::uxtw(0)));
    __ ldrb(r11, Address(nosimd_codec, r11, Address::uxtw(0)));
    __ ldrb(r12, Address(nosimd_codec, r12, Address::uxtw(0)));
    __ ldrb(r13, Address(nosimd_codec, r13, Address::uxtw(0)));
    // error detection, 255u indicates an illegal input
    __ orrw(r14, r10, r11);
    __ orrw(r15, r12, r13);
    __ orrw(r14, r14, r15);
    __ tbnz(r14, 7, Exit);
    // recover the data
    __ lslw(r14, r10, 10);
    __ bfiw(r14, r11, 4, 6);
    __ bfmw(r14, r12, 2, 5);
    __ rev16w(r14, r14);
    __ bfiw(r13, r12, 6, 2);
    __ strh(r14, __ post(dst, 2));
    __ strb(r13, __ post(dst, 1));
    // non-simd loop
    __ subsw(rscratch1, rscratch1, 4);
    __ br(Assembler::GT, Process4B);

    // if exiting from PreProcess80B, rscratch1 == -1;
    // otherwise, rscratch1 == 0.
    __ cbzw(rscratch1, Exit);
    __ sub(length, length, 80);

    __ lea(simd_codec, ExternalAddress((address) fromBase64ForSIMD));
    __ cbz(isURL, SIMDEnter);
    __ lea(simd_codec, ExternalAddress((address) fromBase64URLForSIMD));

    __ BIND(SIMDEnter);
    __ ld1(v0, v1, v2, v3, __ T16B, __ post(simd_codec, 64));
    __ ld1(v4, v5, v6, v7, __ T16B, Address(simd_codec));
    __ mov(rscratch1, 63);
    __ dup(v27, __ T16B, rscratch1);

    __ BIND(Process64B);
    __ cmp(length, (u1)64);
    __ br(Assembler::LT, Process32B);
    generate_base64_decode_simdround(src, dst, v0, v4, 16, Exit);
    __ sub(length, length, 64);
    __ b(Process64B);

    __ BIND(Process32B);
    __ cmp(length, (u1)32);
    __ br(Assembler::LT, SIMDExit);
    generate_base64_decode_simdround(src, dst, v0, v4, 8, Exit);
    __ sub(length, length, 32);
    __ b(Process32B);

    __ BIND(SIMDExit);
    __ cbz(length, Exit);
    __ movw(rscratch1, length);
    __ b(Process4B);

    __ BIND(Exit);
    __ sub(c_rarg0, dst, doff);

    __ leave();
    __ ret(lr);

    return start;
  }

  // Support for spin waits.
  address generate_spin_wait() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_spin_wait_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    __ spin_wait();
    __ ret(lr);

    return start;
  }

  void generate_lookup_secondary_supers_table_stub() {
    StubId stub_id = StubId::stubgen_lookup_secondary_supers_table_id;
    StubCodeMark mark(this, stub_id);

    const Register
      r_super_klass  = r0,
      r_array_base   = r1,
      r_array_length = r2,
      r_array_index  = r3,
      r_sub_klass    = r4,
      r_bitmap       = rscratch2,
      result         = r5;
    const FloatRegister
      vtemp          = v0;

    for (int slot = 0; slot < Klass::SECONDARY_SUPERS_TABLE_SIZE; slot++) {
      StubRoutines::_lookup_secondary_supers_table_stubs[slot] = __ pc();
      Label L_success;
      __ enter();
      __ lookup_secondary_supers_table_const(r_sub_klass, r_super_klass,
                                             r_array_base, r_array_length, r_array_index,
                                             vtemp, result, slot,
                                             /*stub_is_near*/true);
      __ leave();
      __ ret(lr);
    }
  }

  // Slow path implementation for UseSecondarySupersTable.
  address generate_lookup_secondary_supers_table_slow_path_stub() {
    StubId stub_id = StubId::stubgen_lookup_secondary_supers_table_slow_path_id;
    StubCodeMark mark(this, stub_id);

    address start = __ pc();
    const Register
      r_super_klass  = r0,        // argument
      r_array_base   = r1,        // argument
      temp1          = r2,        // temp
      r_array_index  = r3,        // argument
      r_bitmap       = rscratch2, // argument
      result         = r5;        // argument

    __ lookup_secondary_supers_table_slow_path(r_super_klass, r_array_base, r_array_index, r_bitmap, temp1, result);
    __ ret(lr);

    return start;
  }

#if defined (LINUX) && !defined (__ARM_FEATURE_ATOMICS)

  // ARMv8.1 LSE versions of the atomic stubs used by Atomic::PlatformXX.
  //
  // If LSE is in use, generate LSE versions of all the stubs. The
  // non-LSE versions are in atomic_aarch64.S.

  // class AtomicStubMark records the entry point of a stub and the
  // stub pointer which will point to it. The stub pointer is set to
  // the entry point when ~AtomicStubMark() is called, which must be
  // after ICache::invalidate_range. This ensures safe publication of
  // the generated code.
  class AtomicStubMark {
    address _entry_point;
    aarch64_atomic_stub_t *_stub;
    MacroAssembler *_masm;
  public:
    AtomicStubMark(MacroAssembler *masm, aarch64_atomic_stub_t *stub) {
      _masm = masm;
      __ align(32);
      _entry_point = __ pc();
      _stub = stub;
    }
    ~AtomicStubMark() {
      *_stub = (aarch64_atomic_stub_t)_entry_point;
    }
  };

  // NB: For memory_order_conservative we need a trailing membar after
  // LSE atomic operations but not a leading membar.
  //
  // We don't need a leading membar because a clause in the Arm ARM
  // says:
  //
  //   Barrier-ordered-before
  //
  //   Barrier instructions order prior Memory effects before subsequent
  //   Memory effects generated by the same Observer. A read or a write
  //   RW1 is Barrier-ordered-before a read or a write RW 2 from the same
  //   Observer if and only if RW1 appears in program order before RW 2
  //   and [ ... ] at least one of RW 1 and RW 2 is generated by an atomic
  //   instruction with both Acquire and Release semantics.
  //
  // All the atomic instructions {ldaddal, swapal, casal} have Acquire
  // and Release semantics, therefore we don't need a leading
  // barrier. However, there is no corresponding Barrier-ordered-after
  // relationship, therefore we need a trailing membar to prevent a
  // later store or load from being reordered with the store in an
  // atomic instruction.
  //
  // This was checked by using the herd7 consistency model simulator
  // (http://diy.inria.fr/) with this test case:
  //
  // AArch64 LseCas
  // { 0:X1=x; 0:X2=y; 1:X1=x; 1:X2=y; }
  // P0 | P1;
  // LDR W4, [X2] | MOV W3, #0;
  // DMB LD       | MOV W4, #1;
  // LDR W3, [X1] | CASAL W3, W4, [X1];
  //              | DMB ISH;
  //              | STR W4, [X2];
  // exists
  // (0:X3=0 /\ 0:X4=1)
  //
  // If X3 == 0 && X4 == 1, the store to y in P1 has been reordered
  // with the store to x in P1. Without the DMB in P1 this may happen.
  //
  // At the time of writing we don't know of any AArch64 hardware that
  // reorders stores in this way, but the Reference Manual permits it.

  void gen_cas_entry(Assembler::operand_size size,
                     atomic_memory_order order) {
    Register prev = r3, ptr = c_rarg0, compare_val = c_rarg1,
      exchange_val = c_rarg2;
    bool acquire, release;
    switch (order) {
      case memory_order_relaxed:
        acquire = false;
        release = false;
        break;
      case memory_order_release:
        acquire = false;
        release = true;
        break;
      default:
        acquire = true;
        release = true;
        break;
    }
    __ mov(prev, compare_val);
    __ lse_cas(prev, exchange_val, ptr, size, acquire, release, /*not_pair*/true);
    if (order == memory_order_conservative) {
      __ membar(Assembler::StoreStore|Assembler::StoreLoad);
    }
    if (size == Assembler::xword) {
      __ mov(r0, prev);
    } else {
      __ movw(r0, prev);
    }
    __ ret(lr);
  }

  void gen_ldadd_entry(Assembler::operand_size size, atomic_memory_order order) {
    Register prev = r2, addr = c_rarg0, incr = c_rarg1;
    // If not relaxed, then default to conservative.  Relaxed is the only
    // case we use enough to be worth specializing.
    if (order == memory_order_relaxed) {
      __ ldadd(size, incr, prev, addr);
    } else {
      __ ldaddal(size, incr, prev, addr);
      __ membar(Assembler::StoreStore|Assembler::StoreLoad);
    }
    if (size == Assembler::xword) {
      __ mov(r0, prev);
    } else {
      __ movw(r0, prev);
    }
    __ ret(lr);
  }

  void gen_swpal_entry(Assembler::operand_size size) {
    Register prev = r2, addr = c_rarg0, incr = c_rarg1;
    __ swpal(size, incr, prev, addr);
    __ membar(Assembler::StoreStore|Assembler::StoreLoad);
    if (size == Assembler::xword) {
      __ mov(r0, prev);
    } else {
      __ movw(r0, prev);
    }
    __ ret(lr);
  }

  void generate_atomic_entry_points() {
    if (! UseLSE) {
      return;
    }
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_atomic_entry_points_id;
    StubCodeMark mark(this, stub_id);
    address first_entry = __ pc();

    // ADD, memory_order_conservative
    AtomicStubMark mark_fetch_add_4(_masm, &aarch64_atomic_fetch_add_4_impl);
    gen_ldadd_entry(Assembler::word, memory_order_conservative);
    AtomicStubMark mark_fetch_add_8(_masm, &aarch64_atomic_fetch_add_8_impl);
    gen_ldadd_entry(Assembler::xword, memory_order_conservative);

    // ADD, memory_order_relaxed
    AtomicStubMark mark_fetch_add_4_relaxed
      (_masm, &aarch64_atomic_fetch_add_4_relaxed_impl);
    gen_ldadd_entry(MacroAssembler::word, memory_order_relaxed);
    AtomicStubMark mark_fetch_add_8_relaxed
      (_masm, &aarch64_atomic_fetch_add_8_relaxed_impl);
    gen_ldadd_entry(MacroAssembler::xword, memory_order_relaxed);

    // XCHG, memory_order_conservative
    AtomicStubMark mark_xchg_4(_masm, &aarch64_atomic_xchg_4_impl);
    gen_swpal_entry(Assembler::word);
    AtomicStubMark mark_xchg_8_impl(_masm, &aarch64_atomic_xchg_8_impl);
    gen_swpal_entry(Assembler::xword);

    // CAS, memory_order_conservative
    AtomicStubMark mark_cmpxchg_1(_masm, &aarch64_atomic_cmpxchg_1_impl);
    gen_cas_entry(MacroAssembler::byte, memory_order_conservative);
    AtomicStubMark mark_cmpxchg_4(_masm, &aarch64_atomic_cmpxchg_4_impl);
    gen_cas_entry(MacroAssembler::word, memory_order_conservative);
    AtomicStubMark mark_cmpxchg_8(_masm, &aarch64_atomic_cmpxchg_8_impl);
    gen_cas_entry(MacroAssembler::xword, memory_order_conservative);

    // CAS, memory_order_relaxed
    AtomicStubMark mark_cmpxchg_1_relaxed
      (_masm, &aarch64_atomic_cmpxchg_1_relaxed_impl);
    gen_cas_entry(MacroAssembler::byte, memory_order_relaxed);
    AtomicStubMark mark_cmpxchg_4_relaxed
      (_masm, &aarch64_atomic_cmpxchg_4_relaxed_impl);
    gen_cas_entry(MacroAssembler::word, memory_order_relaxed);
    AtomicStubMark mark_cmpxchg_8_relaxed
      (_masm, &aarch64_atomic_cmpxchg_8_relaxed_impl);
    gen_cas_entry(MacroAssembler::xword, memory_order_relaxed);

    AtomicStubMark mark_cmpxchg_4_release
      (_masm, &aarch64_atomic_cmpxchg_4_release_impl);
    gen_cas_entry(MacroAssembler::word, memory_order_release);
    AtomicStubMark mark_cmpxchg_8_release
      (_masm, &aarch64_atomic_cmpxchg_8_release_impl);
    gen_cas_entry(MacroAssembler::xword, memory_order_release);

    AtomicStubMark mark_cmpxchg_4_seq_cst
      (_masm, &aarch64_atomic_cmpxchg_4_seq_cst_impl);
    gen_cas_entry(MacroAssembler::word, memory_order_seq_cst);
    AtomicStubMark mark_cmpxchg_8_seq_cst
      (_masm, &aarch64_atomic_cmpxchg_8_seq_cst_impl);
    gen_cas_entry(MacroAssembler::xword, memory_order_seq_cst);

    ICache::invalidate_range(first_entry, __ pc() - first_entry);
  }
#endif // LINUX

  address generate_cont_thaw(Continuation::thaw_kind kind) {
    bool return_barrier = Continuation::is_thaw_return_barrier(kind);
    bool return_barrier_exception = Continuation::is_thaw_return_barrier_exception(kind);

    address start = __ pc();

    if (return_barrier) {
      __ ldr(rscratch1, Address(rthread, JavaThread::cont_entry_offset()));
      __ mov(sp, rscratch1);
    }
    assert_asm(_masm, (__ ldr(rscratch1, Address(rthread, JavaThread::cont_entry_offset())), __ cmp(sp, rscratch1)), Assembler::EQ, "incorrect sp");

    if (return_barrier) {
      // preserve possible return value from a method returning to the return barrier
      __ fmovd(rscratch1, v0);
      __ stp(rscratch1, r0, Address(__ pre(sp, -2 * wordSize)));
    }

    __ movw(c_rarg1, (return_barrier ? 1 : 0));
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, Continuation::prepare_thaw), rthread, c_rarg1);
    __ mov(rscratch2, r0); // r0 contains the size of the frames to thaw, 0 if overflow or no more frames

    if (return_barrier) {
      // restore return value (no safepoint in the call to thaw, so even an oop return value should be OK)
      __ ldp(rscratch1, r0, Address(__ post(sp, 2 * wordSize)));
      __ fmovd(v0, rscratch1);
    }
    assert_asm(_masm, (__ ldr(rscratch1, Address(rthread, JavaThread::cont_entry_offset())), __ cmp(sp, rscratch1)), Assembler::EQ, "incorrect sp");


    Label thaw_success;
    // rscratch2 contains the size of the frames to thaw, 0 if overflow or no more frames
    __ cbnz(rscratch2, thaw_success);
    __ lea(rscratch1, RuntimeAddress(SharedRuntime::throw_StackOverflowError_entry()));
    __ br(rscratch1);
    __ bind(thaw_success);

    // make room for the thawed frames
    __ sub(rscratch1, sp, rscratch2);
    __ andr(rscratch1, rscratch1, -16); // align
    __ mov(sp, rscratch1);

    if (return_barrier) {
      // save original return value -- again
      __ fmovd(rscratch1, v0);
      __ stp(rscratch1, r0, Address(__ pre(sp, -2 * wordSize)));
    }

    // If we want, we can templatize thaw by kind, and have three different entries
    __ movw(c_rarg1, (uint32_t)kind);

    __ call_VM_leaf(Continuation::thaw_entry(), rthread, c_rarg1);
    __ mov(rscratch2, r0); // r0 is the sp of the yielding frame

    if (return_barrier) {
      // restore return value (no safepoint in the call to thaw, so even an oop return value should be OK)
      __ ldp(rscratch1, r0, Address(__ post(sp, 2 * wordSize)));
      __ fmovd(v0, rscratch1);
    } else {
      __ mov(r0, zr); // return 0 (success) from doYield
    }

    // we're now on the yield frame (which is in an address above us b/c rsp has been pushed down)
    __ sub(sp, rscratch2, 2*wordSize); // now pointing to rfp spill
    __ mov(rfp, sp);

    if (return_barrier_exception) {
      __ ldr(c_rarg1, Address(rfp, wordSize)); // return address
      __ authenticate_return_address(c_rarg1);
      __ verify_oop(r0);
      // save return value containing the exception oop in callee-saved R19
      __ mov(r19, r0);

      __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), rthread, c_rarg1);

      // Reinitialize the ptrue predicate register, in case the external runtime call clobbers ptrue reg, as we may return to SVE compiled code.
      // __ reinitialize_ptrue();

      // see OptoRuntime::generate_exception_blob: r0 -- exception oop, r3 -- exception pc

      __ mov(r1, r0); // the exception handler
      __ mov(r0, r19); // restore return value containing the exception oop
      __ verify_oop(r0);

      __ leave();
      __ mov(r3, lr);
      __ br(r1); // the exception handler
    } else {
      // We're "returning" into the topmost thawed frame; see Thaw::push_return_frame
      __ leave();
      __ ret(lr);
    }

    return start;
  }

  address generate_cont_thaw() {
    if (!Continuations::enabled()) return nullptr;

    StubId stub_id = StubId::stubgen_cont_thaw_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    generate_cont_thaw(Continuation::thaw_top);
    return start;
  }

  address generate_cont_returnBarrier() {
    if (!Continuations::enabled()) return nullptr;

    // TODO: will probably need multiple return barriers depending on return type
    StubId stub_id = StubId::stubgen_cont_returnBarrier_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    generate_cont_thaw(Continuation::thaw_return_barrier);

    return start;
  }

  address generate_cont_returnBarrier_exception() {
    if (!Continuations::enabled()) return nullptr;

    StubId stub_id = StubId::stubgen_cont_returnBarrierExc_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    generate_cont_thaw(Continuation::thaw_return_barrier_exception);

    return start;
  }

  address generate_cont_preempt_stub() {
    if (!Continuations::enabled()) return nullptr;
    StubId stub_id = StubId::stubgen_cont_preempt_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    __ reset_last_Java_frame(true);

    // Set sp to enterSpecial frame, i.e. remove all frames copied into the heap.
    __ ldr(rscratch2, Address(rthread, JavaThread::cont_entry_offset()));
    __ mov(sp, rscratch2);

    Label preemption_cancelled;
    __ ldrb(rscratch1, Address(rthread, JavaThread::preemption_cancelled_offset()));
    __ cbnz(rscratch1, preemption_cancelled);

    // Remove enterSpecial frame from the stack and return to Continuation.run() to unmount.
    SharedRuntime::continuation_enter_cleanup(_masm);
    __ leave();
    __ ret(lr);

    // We acquired the monitor after freezing the frames so call thaw to continue execution.
    __ bind(preemption_cancelled);
    __ strb(zr, Address(rthread, JavaThread::preemption_cancelled_offset()));
    __ lea(rfp, Address(sp, checked_cast<int32_t>(ContinuationEntry::size())));
    __ lea(rscratch1, ExternalAddress(ContinuationEntry::thaw_call_pc_address()));
    __ ldr(rscratch1, Address(rscratch1));
    __ br(rscratch1);

    return start;
  }

  // In sun.security.util.math.intpoly.IntegerPolynomial1305, integers
  // are represented as long[5], with BITS_PER_LIMB = 26.
  // Pack five 26-bit limbs into three 64-bit registers.
  void pack_26(Register dest0, Register dest1, Register dest2, Register src) {
    __ ldp(dest0, rscratch1, Address(src, 0));     // 26 bits
    __ add(dest0, dest0, rscratch1, Assembler::LSL, 26);  // 26 bits
    __ ldp(rscratch1, rscratch2, Address(src, 2 * sizeof (jlong)));
    __ add(dest0, dest0, rscratch1, Assembler::LSL, 52);  // 12 bits

    __ add(dest1, zr, rscratch1, Assembler::LSR, 12);     // 14 bits
    __ add(dest1, dest1, rscratch2, Assembler::LSL, 14);  // 26 bits
    __ ldr(rscratch1, Address(src, 4 * sizeof (jlong)));
    __ add(dest1, dest1, rscratch1, Assembler::LSL, 40);  // 24 bits

    if (dest2->is_valid()) {
      __ add(dest2, zr, rscratch1, Assembler::LSR, 24);     // 2 bits
    } else {
#ifdef ASSERT
      Label OK;
      __ cmp(zr, rscratch1, Assembler::LSR, 24);     // 2 bits
      __ br(__ EQ, OK);
      __ stop("high bits of Poly1305 integer should be zero");
      __ should_not_reach_here();
      __ bind(OK);
#endif
    }
  }

  // As above, but return only a 128-bit integer, packed into two
  // 64-bit registers.
  void pack_26(Register dest0, Register dest1, Register src) {
    pack_26(dest0, dest1, noreg, src);
  }

  // Multiply and multiply-accumulate unsigned 64-bit registers.
  void wide_mul(Register prod_lo, Register prod_hi, Register n, Register m) {
    __ mul(prod_lo, n, m);
    __ umulh(prod_hi, n, m);
  }
  void wide_madd(Register sum_lo, Register sum_hi, Register n, Register m) {
    wide_mul(rscratch1, rscratch2, n, m);
    __ adds(sum_lo, sum_lo, rscratch1);
    __ adc(sum_hi, sum_hi, rscratch2);
  }

  // Poly1305, RFC 7539

  // See https://loup-vaillant.fr/tutorials/poly1305-design for a
  // description of the tricks used to simplify and accelerate this
  // computation.

  address generate_poly1305_processBlocks() {
    __ align(CodeEntryAlignment);
    StubId stub_id = StubId::stubgen_poly1305_processBlocks_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();
    Label here;
    __ enter();
    RegSet callee_saved = RegSet::range(r19, r28);
    __ push(callee_saved, sp);

    RegSetIterator<Register> regs = (RegSet::range(c_rarg0, r28) - r18_tls - rscratch1 - rscratch2).begin();

    // Arguments
    const Register input_start = *regs, length = *++regs, acc_start = *++regs, r_start = *++regs;

    // R_n is the 128-bit randomly-generated key, packed into two
    // registers.  The caller passes this key to us as long[5], with
    // BITS_PER_LIMB = 26.
    const Register R_0 = *++regs, R_1 = *++regs;
    pack_26(R_0, R_1, r_start);

    // RR_n is (R_n >> 2) * 5
    const Register RR_0 = *++regs, RR_1 = *++regs;
    __ lsr(RR_0, R_0, 2);
    __ add(RR_0, RR_0, RR_0, Assembler::LSL, 2);
    __ lsr(RR_1, R_1, 2);
    __ add(RR_1, RR_1, RR_1, Assembler::LSL, 2);

    // U_n is the current checksum
    const Register U_0 = *++regs, U_1 = *++regs, U_2 = *++regs;
    pack_26(U_0, U_1, U_2, acc_start);

    static constexpr int BLOCK_LENGTH = 16;
    Label DONE, LOOP;

    __ cmp(length, checked_cast<u1>(BLOCK_LENGTH));
    __ br(Assembler::LT, DONE); {
      __ bind(LOOP);

      // S_n is to be the sum of U_n and the next block of data
      const Register S_0 = *++regs, S_1 = *++regs, S_2 = *++regs;
      __ ldp(S_0, S_1, __ post(input_start, 2 * wordSize));
      __ adds(S_0, U_0, S_0);
      __ adcs(S_1, U_1, S_1);
      __ adc(S_2, U_2, zr);
      __ add(S_2, S_2, 1);

      const Register U_0HI = *++regs, U_1HI = *++regs;

      // NB: this logic depends on some of the special properties of
      // Poly1305 keys. In particular, because we know that the top
      // four bits of R_0 and R_1 are zero, we can add together
      // partial products without any risk of needing to propagate a
      // carry out.
      wide_mul(U_0, U_0HI, S_0, R_0);  wide_madd(U_0, U_0HI, S_1, RR_1); wide_madd(U_0, U_0HI, S_2, RR_0);
      wide_mul(U_1, U_1HI, S_0, R_1);  wide_madd(U_1, U_1HI, S_1, R_0);  wide_madd(U_1, U_1HI, S_2, RR_1);
      __ andr(U_2, R_0, 3);
      __ mul(U_2, S_2, U_2);

      // Recycle registers S_0, S_1, S_2
      regs = (regs.remaining() + S_0 + S_1 + S_2).begin();

      // Partial reduction mod 2**130 - 5
      __ adds(U_1, U_0HI, U_1);
      __ adc(U_2, U_1HI, U_2);
      // Sum now in U_2:U_1:U_0.
      // Dead: U_0HI, U_1HI.
      regs = (regs.remaining() + U_0HI + U_1HI).begin();

      // U_2:U_1:U_0 += (U_2 >> 2) * 5 in two steps

      // First, U_2:U_1:U_0 += (U_2 >> 2)
      __ lsr(rscratch1, U_2, 2);
      __ andr(U_2, U_2, (u8)3);
      __ adds(U_0, U_0, rscratch1);
      __ adcs(U_1, U_1, zr);
      __ adc(U_2, U_2, zr);
      // Second, U_2:U_1:U_0 += (U_2 >> 2) << 2
      __ adds(U_0, U_0, rscratch1, Assembler::LSL, 2);
      __ adcs(U_1, U_1, zr);
      __ adc(U_2, U_2, zr);

      __ sub(length, length, checked_cast<u1>(BLOCK_LENGTH));
      __ cmp(length, checked_cast<u1>(BLOCK_LENGTH));
      __ br(~ Assembler::LT, LOOP);
    }

    // Further reduce modulo 2^130 - 5
    __ lsr(rscratch1, U_2, 2);
    __ add(rscratch1, rscratch1, rscratch1, Assembler::LSL, 2); // rscratch1 = U_2 * 5
    __ adds(U_0, U_0, rscratch1); // U_0 += U_2 * 5
    __ adcs(U_1, U_1, zr);
    __ andr(U_2, U_2, (u1)3);
    __ adc(U_2, U_2, zr);

    // Unpack the sum into five 26-bit limbs and write to memory.
    __ ubfiz(rscratch1, U_0, 0, 26);
    __ ubfx(rscratch2, U_0, 26, 26);
    __ stp(rscratch1, rscratch2, Address(acc_start));
    __ ubfx(rscratch1, U_0, 52, 12);
    __ bfi(rscratch1, U_1, 12, 14);
    __ ubfx(rscratch2, U_1, 14, 26);
    __ stp(rscratch1, rscratch2, Address(acc_start, 2 * sizeof (jlong)));
    __ ubfx(rscratch1, U_1, 40, 24);
    __ bfi(rscratch1, U_2, 24, 3);
    __ str(rscratch1, Address(acc_start, 4 * sizeof (jlong)));

    __ bind(DONE);
    __ pop(callee_saved, sp);
    __ leave();
    __ ret(lr);

    return start;
  }

  // exception handler for upcall stubs
  address generate_upcall_stub_exception_handler() {
    StubId stub_id = StubId::stubgen_upcall_stub_exception_handler_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    // Native caller has no idea how to handle exceptions,
    // so we just crash here. Up to callee to catch exceptions.
    __ verify_oop(r0);
    __ movptr(rscratch1, CAST_FROM_FN_PTR(uint64_t, UpcallLinker::handle_uncaught_exception));
    __ blr(rscratch1);
    __ should_not_reach_here();

    return start;
  }

  // load Method* target of MethodHandle
  // j_rarg0 = jobject receiver
  // rmethod = result
  address generate_upcall_stub_load_target() {
    StubId stub_id = StubId::stubgen_upcall_stub_load_target_id;
    StubCodeMark mark(this, stub_id);
    address start = __ pc();

    __ resolve_global_jobject(j_rarg0, rscratch1, rscratch2);
      // Load target method from receiver
    __ load_heap_oop(rmethod, Address(j_rarg0, java_lang_invoke_MethodHandle::form_offset()), rscratch1, rscratch2);
    __ load_heap_oop(rmethod, Address(rmethod, java_lang_invoke_LambdaForm::vmentry_offset()), rscratch1, rscratch2);
    __ load_heap_oop(rmethod, Address(rmethod, java_lang_invoke_MemberName::method_offset()), rscratch1, rscratch2);
    __ access_load_at(T_ADDRESS, IN_HEAP, rmethod,
                      Address(rmethod, java_lang_invoke_ResolvedMethodName::vmtarget_offset()),
                      noreg, noreg);
    __ str(rmethod, Address(rthread, JavaThread::callee_target_offset())); // just in case callee is deoptimized

    __ ret(lr);

    return start;
  }

#undef __
#define __ masm->

  class MontgomeryMultiplyGenerator : public MacroAssembler {

    Register Pa_base, Pb_base, Pn_base, Pm_base, inv, Rlen, Ra, Rb, Rm, Rn,
      Pa, Pb, Pn, Pm, Rhi_ab, Rlo_ab, Rhi_mn, Rlo_mn, t0, t1, t2, Ri, Rj;

    RegSet _toSave;
    bool _squaring;

  public:
    MontgomeryMultiplyGenerator (Assembler *as, bool squaring)
      : MacroAssembler(as->code()), _squaring(squaring) {

      // Register allocation

      RegSetIterator<Register> regs = (RegSet::range(r0, r26) - r18_tls).begin();
      Pa_base = *regs;       // Argument registers
      if (squaring)
        Pb_base = Pa_base;
      else
        Pb_base = *++regs;
      Pn_base = *++regs;
      Rlen= *++regs;
      inv = *++regs;
      Pm_base = *++regs;

                          // Working registers:
      Ra =  *++regs;        // The current digit of a, b, n, and m.
      Rb =  *++regs;
      Rm =  *++regs;
      Rn =  *++regs;

      Pa =  *++regs;        // Pointers to the current/next digit of a, b, n, and m.
      Pb =  *++regs;
      Pm =  *++regs;
      Pn =  *++regs;

      t0 =  *++regs;        // Three registers which form a
      t1 =  *++regs;        // triple-precision accumuator.
      t2 =  *++regs;

      Ri =  *++regs;        // Inner and outer loop indexes.
      Rj =  *++regs;

      Rhi_ab = *++regs;     // Product registers: low and high parts
      Rlo_ab = *++regs;     // of a*b and m*n.
      Rhi_mn = *++regs;
      Rlo_mn = *++regs;

      // r19 and up are callee-saved.
      _toSave = RegSet::range(r19, *regs) + Pm_base;
    }

  private:
    void save_regs() {
      push(_toSave, sp);
    }

    void restore_regs() {
      pop(_toSave, sp);
    }

    template <typename T>
    void unroll_2(Register count, T block) {
      Label loop, end, odd;
      tbnz(count, 0, odd);
      cbz(count, end);
      align(16);
      bind(loop);
      (this->*block)();
      bind(odd);
      (this->*block)();
      subs(count, count, 2);
      br(Assembler::GT, loop);
      bind(end);
    }

    template <typename T>
    void unroll_2(Register count, T block, Register d, Register s, Register tmp) {
      Label loop, end, odd;
      tbnz(count, 0, odd);
      cbz(count, end);
      align(16);
      bind(loop);
      (this->*block)(d, s, tmp);
      bind(odd);
      (this->*block)(d, s, tmp);
      subs(count, count, 2);
      br(Assembler::GT, loop);
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
      ldr(Ra, Address(Pa_base));
      ldr(Rb, Address(Pb_base, i, Address::uxtw(LogBytesPerWord)));
      ldr(Rm, Address(Pm_base));
      ldr(Rn, Address(Pn_base, i, Address::uxtw(LogBytesPerWord)));
      lea(Pa, Address(Pa_base));
      lea(Pb, Address(Pb_base, i, Address::uxtw(LogBytesPerWord)));
      lea(Pm, Address(Pm_base));
      lea(Pn, Address(Pn_base, i, Address::uxtw(LogBytesPerWord)));

      // Zero the m*n result.
      mov(Rhi_mn, zr);
      mov(Rlo_mn, zr);
    }

    // The core multiply-accumulate step of a Montgomery
    // multiplication.  The idea is to schedule operations as a
    // pipeline so that instructions with long latencies (loads and
    // multiplies) have time to complete before their results are
    // used.  This most benefits in-order implementations of the
    // architecture but out-of-order ones also benefit.
    void step() {
      block_comment("step");
      // MACC(Ra, Rb, t0, t1, t2);
      // Ra = *++Pa;
      // Rb = *--Pb;
      umulh(Rhi_ab, Ra, Rb);
      mul(Rlo_ab, Ra, Rb);
      ldr(Ra, pre(Pa, wordSize));
      ldr(Rb, pre(Pb, -wordSize));
      acc(Rhi_mn, Rlo_mn, t0, t1, t2); // The pending m*n from the
                                       // previous iteration.
      // MACC(Rm, Rn, t0, t1, t2);
      // Rm = *++Pm;
      // Rn = *--Pn;
      umulh(Rhi_mn, Rm, Rn);
      mul(Rlo_mn, Rm, Rn);
      ldr(Rm, pre(Pm, wordSize));
      ldr(Rn, pre(Pn, -wordSize));
      acc(Rhi_ab, Rlo_ab, t0, t1, t2);
    }

    void post1() {
      block_comment("post1");

      // MACC(Ra, Rb, t0, t1, t2);
      // Ra = *++Pa;
      // Rb = *--Pb;
      umulh(Rhi_ab, Ra, Rb);
      mul(Rlo_ab, Ra, Rb);
      acc(Rhi_mn, Rlo_mn, t0, t1, t2);  // The pending m*n
      acc(Rhi_ab, Rlo_ab, t0, t1, t2);

      // *Pm = Rm = t0 * inv;
      mul(Rm, t0, inv);
      str(Rm, Address(Pm));

      // MACC(Rm, Rn, t0, t1, t2);
      // t0 = t1; t1 = t2; t2 = 0;
      umulh(Rhi_mn, Rm, Rn);

#ifndef PRODUCT
      // assert(m[i] * n[0] + t0 == 0, "broken Montgomery multiply");
      {
        mul(Rlo_mn, Rm, Rn);
        add(Rlo_mn, t0, Rlo_mn);
        Label ok;
        cbz(Rlo_mn, ok); {
          stop("broken Montgomery multiply");
        } bind(ok);
      }
#endif
      // We have very carefully set things up so that
      // m[i]*n[0] + t0 == 0 (mod b), so we don't have to calculate
      // the lower half of Rm * Rn because we know the result already:
      // it must be -t0.  t0 + (-t0) must generate a carry iff
      // t0 != 0.  So, rather than do a mul and an adds we just set
      // the carry flag iff t0 is nonzero.
      //
      // mul(Rlo_mn, Rm, Rn);
      // adds(zr, t0, Rlo_mn);
      subs(zr, t0, 1); // Set carry iff t0 is nonzero
      adcs(t0, t1, Rhi_mn);
      adc(t1, t2, zr);
      mov(t2, zr);
    }

    void pre2(RegisterOrConstant i, RegisterOrConstant len) {
      block_comment("pre2");
      // Pa = Pa_base + i-len;
      // Pb = Pb_base + len;
      // Pm = Pm_base + i-len;
      // Pn = Pn_base + len;

      if (i.is_register()) {
        sub(Rj, i.as_register(), len);
      } else {
        mov(Rj, i.as_constant());
        sub(Rj, Rj, len);
      }
      // Rj == i-len

      lea(Pa, Address(Pa_base, Rj, Address::uxtw(LogBytesPerWord)));
      lea(Pb, Address(Pb_base, len, Address::uxtw(LogBytesPerWord)));
      lea(Pm, Address(Pm_base, Rj, Address::uxtw(LogBytesPerWord)));
      lea(Pn, Address(Pn_base, len, Address::uxtw(LogBytesPerWord)));

      // Ra = *++Pa;
      // Rb = *--Pb;
      // Rm = *++Pm;
      // Rn = *--Pn;
      ldr(Ra, pre(Pa, wordSize));
      ldr(Rb, pre(Pb, -wordSize));
      ldr(Rm, pre(Pm, wordSize));
      ldr(Rn, pre(Pn, -wordSize));

      mov(Rhi_mn, zr);
      mov(Rlo_mn, zr);
    }

    void post2(RegisterOrConstant i, RegisterOrConstant len) {
      block_comment("post2");
      if (i.is_constant()) {
        mov(Rj, i.as_constant()-len.as_constant());
      } else {
        sub(Rj, i.as_register(), len);
      }

      adds(t0, t0, Rlo_mn); // The pending m*n, low part

      // As soon as we know the least significant digit of our result,
      // store it.
      // Pm_base[i-len] = t0;
      str(t0, Address(Pm_base, Rj, Address::uxtw(LogBytesPerWord)));

      // t0 = t1; t1 = t2; t2 = 0;
      adcs(t0, t1, Rhi_mn); // The pending m*n, high part
      adc(t1, t2, zr);
      mov(t2, zr);
    }

    // A carry in t0 after Montgomery multiplication means that we
    // should subtract multiples of n from our result in m.  We'll
    // keep doing that until there is no carry.
    void normalize(RegisterOrConstant len) {
      block_comment("normalize");
      // while (t0)
      //   t0 = sub(Pm_base, Pn_base, t0, len);
      Label loop, post, again;
      Register cnt = t1, i = t2; // Re-use registers; we're done with them now
      cbz(t0, post); {
        bind(again); {
          mov(i, zr);
          mov(cnt, len);
          ldr(Rm, Address(Pm_base, i, Address::uxtw(LogBytesPerWord)));
          ldr(Rn, Address(Pn_base, i, Address::uxtw(LogBytesPerWord)));
          subs(zr, zr, zr); // set carry flag, i.e. no borrow
          align(16);
          bind(loop); {
            sbcs(Rm, Rm, Rn);
            str(Rm, Address(Pm_base, i, Address::uxtw(LogBytesPerWord)));
            add(i, i, 1);
            ldr(Rm, Address(Pm_base, i, Address::uxtw(LogBytesPerWord)));
            ldr(Rn, Address(Pn_base, i, Address::uxtw(LogBytesPerWord)));
            sub(cnt, cnt, 1);
          } cbnz(cnt, loop);
          sbc(t0, t0, zr);
        } cbnz(t0, again);
      } bind(post);
    }

    // Move memory at s to d, reversing words.
    //    Increments d to end of copied memory
    //    Destroys tmp1, tmp2
    //    Preserves len
    //    Leaves s pointing to the address which was in d at start
    void reverse(Register d, Register s, Register len, Register tmp1, Register tmp2) {
      assert(tmp1->encoding() < r19->encoding(), "register corruption");
      assert(tmp2->encoding() < r19->encoding(), "register corruption");

      lea(s, Address(s, len, Address::uxtw(LogBytesPerWord)));
      mov(tmp1, len);
      unroll_2(tmp1, &MontgomeryMultiplyGenerator::reverse1, d, s, tmp2);
      sub(s, d, len, ext::uxtw, LogBytesPerWord);
    }
    // where
    void reverse1(Register d, Register s, Register tmp) {
      ldr(tmp, pre(s, -wordSize));
      ror(tmp, tmp, 32);
      str(tmp, post(d, wordSize));
    }

    void step_squaring() {
      // An extra ACC
      step();
      acc(Rhi_ab, Rlo_ab, t0, t1, t2);
    }

    void last_squaring(RegisterOrConstant i) {
      Label dont;
      // if ((i & 1) == 0) {
      tbnz(i.as_register(), 0, dont); {
        // MACC(Ra, Rb, t0, t1, t2);
        // Ra = *++Pa;
        // Rb = *--Pb;
        umulh(Rhi_ab, Ra, Rb);
        mul(Rlo_ab, Ra, Rb);
        acc(Rhi_ab, Rlo_ab, t0, t1, t2);
      } bind(dont);
    }

    void extra_step_squaring() {
      acc(Rhi_mn, Rlo_mn, t0, t1, t2);  // The pending m*n

      // MACC(Rm, Rn, t0, t1, t2);
      // Rm = *++Pm;
      // Rn = *--Pn;
      umulh(Rhi_mn, Rm, Rn);
      mul(Rlo_mn, Rm, Rn);
      ldr(Rm, pre(Pm, wordSize));
      ldr(Rn, pre(Pn, -wordSize));
    }

    void post1_squaring() {
      acc(Rhi_mn, Rlo_mn, t0, t1, t2);  // The pending m*n

      // *Pm = Rm = t0 * inv;
      mul(Rm, t0, inv);
      str(Rm, Address(Pm));

      // MACC(Rm, Rn, t0, t1, t2);
      // t0 = t1; t1 = t2; t2 = 0;
      umulh(Rhi_mn, Rm, Rn);

#ifndef PRODUCT
      // assert(m[i] * n[0] + t0 == 0, "broken Montgomery multiply");
      {
        mul(Rlo_mn, Rm, Rn);
        add(Rlo_mn, t0, Rlo_mn);
        Label ok;
        cbz(Rlo_mn, ok); {
          stop("broken Montgomery multiply");
        } bind(ok);
      }
#endif
      // We have very carefully set things up so that
      // m[i]*n[0] + t0 == 0 (mod b), so we don't have to calculate
      // the lower half of Rm * Rn because we know the result already:
      // it must be -t0.  t0 + (-t0) must generate a carry iff
      // t0 != 0.  So, rather than do a mul and an adds we just set
      // the carry flag iff t0 is nonzero.
      //
      // mul(Rlo_mn, Rm, Rn);
      // adds(zr, t0, Rlo_mn);
      subs(zr, t0, 1); // Set carry iff t0 is nonzero
      adcs(t0, t1, Rhi_mn);
      adc(t1, t2, zr);
      mov(t2, zr);
    }

    void acc(Register Rhi, Register Rlo,
             Register t0, Register t1, Register t2) {
      adds(t0, t0, Rlo);
      adcs(t1, t1, Rhi);
      adc(t2, t2, zr);
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

      cbzw(Rlen, nothing);

      enter();

      // Make room.
      cmpw(Rlen, 512);
      br(Assembler::HI, argh);
      sub(Ra, sp, Rlen, ext::uxtw, exact_log2(4 * sizeof (jint)));
      andr(sp, Ra, -2 * wordSize);

      lsrw(Rlen, Rlen, 1);  // length in longwords = len/2

      {
        // Copy input args, reversing as we go.  We use Ra as a
        // temporary variable.
        reverse(Ra, Pa_base, Rlen, t0, t1);
        if (!_squaring)
          reverse(Ra, Pb_base, Rlen, t0, t1);
        reverse(Ra, Pn_base, Rlen, t0, t1);
      }

      // Push all call-saved registers and also Pm_base which we'll need
      // at the end.
      save_regs();

#ifndef PRODUCT
      // assert(inv * n[0] == -1UL, "broken inverse in Montgomery multiply");
      {
        ldr(Rn, Address(Pn_base, 0));
        mul(Rlo_mn, Rn, inv);
        subs(zr, Rlo_mn, -1);
        Label ok;
        br(EQ, ok); {
          stop("broken inverse in Montgomery multiply");
        } bind(ok);
      }
#endif

      mov(Pm_base, Ra);

      mov(t0, zr);
      mov(t1, zr);
      mov(t2, zr);

      block_comment("for (int i = 0; i < len; i++) {");
      mov(Ri, zr); {
        Label loop, end;
        cmpw(Ri, Rlen);
        br(Assembler::GE, end);

        bind(loop);
        pre1(Ri);

        block_comment("  for (j = i; j; j--) {"); {
          movw(Rj, Ri);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::step);
        } block_comment("  } // j");

        post1();
        addw(Ri, Ri, 1);
        cmpw(Ri, Rlen);
        br(Assembler::LT, loop);
        bind(end);
        block_comment("} // i");
      }

      block_comment("for (int i = len; i < 2*len; i++) {");
      mov(Ri, Rlen); {
        Label loop, end;
        cmpw(Ri, Rlen, Assembler::LSL, 1);
        br(Assembler::GE, end);

        bind(loop);
        pre2(Ri, Rlen);

        block_comment("  for (j = len*2-i-1; j; j--) {"); {
          lslw(Rj, Rlen, 1);
          subw(Rj, Rj, Ri);
          subw(Rj, Rj, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::step);
        } block_comment("  } // j");

        post2(Ri, Rlen);
        addw(Ri, Ri, 1);
        cmpw(Ri, Rlen, Assembler::LSL, 1);
        br(Assembler::LT, loop);
        bind(end);
      }
      block_comment("} // i");

      normalize(Rlen);

      mov(Ra, Pm_base);  // Save Pm_base in Ra
      restore_regs();  // Restore caller's Pm_base

      // Copy our result into caller's Pm_base
      reverse(Pm_base, Ra, Rlen, t0, t1);

      leave();
      bind(nothing);
      ret(lr);

      return entry;
    }
    // In C, approximately:

    // void
    // montgomery_multiply(julong Pa_base[], julong Pb_base[],
    //                     julong Pn_base[], julong Pm_base[],
    //                     julong inv, int len) {
    //   julong t0 = 0, t1 = 0, t2 = 0; // Triple-precision accumulator
    //   julong *Pa, *Pb, *Pn, *Pm;
    //   julong Ra, Rb, Rn, Rm;

    //   int i;

    //   assert(inv * Pn_base[0] == -1UL, "broken inverse in Montgomery multiply");

    //   for (i = 0; i < len; i++) {
    //     int j;

    //     Pa = Pa_base;
    //     Pb = Pb_base + i;
    //     Pm = Pm_base;
    //     Pn = Pn_base + i;

    //     Ra = *Pa;
    //     Rb = *Pb;
    //     Rm = *Pm;
    //     Rn = *Pn;

    //     int iters = i;
    //     for (j = 0; iters--; j++) {
    //       assert(Ra == Pa_base[j] && Rb == Pb_base[i-j], "must be");
    //       MACC(Ra, Rb, t0, t1, t2);
    //       Ra = *++Pa;
    //       Rb = *--Pb;
    //       assert(Rm == Pm_base[j] && Rn == Pn_base[i-j], "must be");
    //       MACC(Rm, Rn, t0, t1, t2);
    //       Rm = *++Pm;
    //       Rn = *--Pn;
    //     }

    //     assert(Ra == Pa_base[i] && Rb == Pb_base[0], "must be");
    //     MACC(Ra, Rb, t0, t1, t2);
    //     *Pm = Rm = t0 * inv;
    //     assert(Rm == Pm_base[i] && Rn == Pn_base[0], "must be");
    //     MACC(Rm, Rn, t0, t1, t2);

    //     assert(t0 == 0, "broken Montgomery multiply");

    //     t0 = t1; t1 = t2; t2 = 0;
    //   }

    //   for (i = len; i < 2*len; i++) {
    //     int j;

    //     Pa = Pa_base + i-len;
    //     Pb = Pb_base + len;
    //     Pm = Pm_base + i-len;
    //     Pn = Pn_base + len;

    //     Ra = *++Pa;
    //     Rb = *--Pb;
    //     Rm = *++Pm;
    //     Rn = *--Pn;

    //     int iters = len*2-i-1;
    //     for (j = i-len+1; iters--; j++) {
    //       assert(Ra == Pa_base[j] && Rb == Pb_base[i-j], "must be");
    //       MACC(Ra, Rb, t0, t1, t2);
    //       Ra = *++Pa;
    //       Rb = *--Pb;
    //       assert(Rm == Pm_base[j] && Rn == Pn_base[i-j], "must be");
    //       MACC(Rm, Rn, t0, t1, t2);
    //       Rm = *++Pm;
    //       Rn = *--Pn;
    //     }

    //     Pm_base[i-len] = t0;
    //     t0 = t1; t1 = t2; t2 = 0;
    //   }

    //   while (t0)
    //     t0 = sub(Pm_base, Pn_base, t0, len);
    // }

    /**
     * Fast Montgomery squaring.  This uses asymptotically 25% fewer
     * multiplies than Montgomery multiplication so it should be up to
     * 25% faster.  However, its loop control is more complex and it
     * may actually run slower on some machines.
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
      cmpw(Rlen, 512);
      br(Assembler::HI, argh);
      sub(Ra, sp, Rlen, ext::uxtw, exact_log2(4 * sizeof (jint)));
      andr(sp, Ra, -2 * wordSize);

      lsrw(Rlen, Rlen, 1);  // length in longwords = len/2

      {
        // Copy input args, reversing as we go.  We use Ra as a
        // temporary variable.
        reverse(Ra, Pa_base, Rlen, t0, t1);
        reverse(Ra, Pn_base, Rlen, t0, t1);
      }

      // Push all call-saved registers and also Pm_base which we'll need
      // at the end.
      save_regs();

      mov(Pm_base, Ra);

      mov(t0, zr);
      mov(t1, zr);
      mov(t2, zr);

      block_comment("for (int i = 0; i < len; i++) {");
      mov(Ri, zr); {
        Label loop, end;
        bind(loop);
        cmp(Ri, Rlen);
        br(Assembler::GE, end);

        pre1(Ri);

        block_comment("for (j = (i+1)/2; j; j--) {"); {
          add(Rj, Ri, 1);
          lsr(Rj, Rj, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::step_squaring);
        } block_comment("  } // j");

        last_squaring(Ri);

        block_comment("  for (j = i/2; j; j--) {"); {
          lsr(Rj, Ri, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::extra_step_squaring);
        } block_comment("  } // j");

        post1_squaring();
        add(Ri, Ri, 1);
        cmp(Ri, Rlen);
        br(Assembler::LT, loop);

        bind(end);
        block_comment("} // i");
      }

      block_comment("for (int i = len; i < 2*len; i++) {");
      mov(Ri, Rlen); {
        Label loop, end;
        bind(loop);
        cmp(Ri, Rlen, Assembler::LSL, 1);
        br(Assembler::GE, end);

        pre2(Ri, Rlen);

        block_comment("  for (j = (2*len-i-1)/2; j; j--) {"); {
          lsl(Rj, Rlen, 1);
          sub(Rj, Rj, Ri);
          sub(Rj, Rj, 1);
          lsr(Rj, Rj, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::step_squaring);
        } block_comment("  } // j");

        last_squaring(Ri);

        block_comment("  for (j = (2*len-i)/2; j; j--) {"); {
          lsl(Rj, Rlen, 1);
          sub(Rj, Rj, Ri);
          lsr(Rj, Rj, 1);
          unroll_2(Rj, &MontgomeryMultiplyGenerator::extra_step_squaring);
        } block_comment("  } // j");

        post2(Ri, Rlen);
        add(Ri, Ri, 1);
        cmp(Ri, Rlen, Assembler::LSL, 1);

        br(Assembler::LT, loop);
        bind(end);
        block_comment("} // i");
      }

      normalize(Rlen);

      mov(Ra, Pm_base);  // Save Pm_base in Ra
      restore_regs();  // Restore caller's Pm_base

      // Copy our result into caller's Pm_base
      reverse(Pm_base, Ra, Rlen, t0, t1);

      leave();
      ret(lr);

      return entry;
    }
    // In C, approximately:

    // void
    // montgomery_square(julong Pa_base[], julong Pn_base[],
    //                   julong Pm_base[], julong inv, int len) {
    //   julong t0 = 0, t1 = 0, t2 = 0; // Triple-precision accumulator
    //   julong *Pa, *Pb, *Pn, *Pm;
    //   julong Ra, Rb, Rn, Rm;

    //   int i;

    //   assert(inv * Pn_base[0] == -1UL, "broken inverse in Montgomery multiply");

    //   for (i = 0; i < len; i++) {
    //     int j;

    //     Pa = Pa_base;
    //     Pb = Pa_base + i;
    //     Pm = Pm_base;
    //     Pn = Pn_base + i;

    //     Ra = *Pa;
    //     Rb = *Pb;
    //     Rm = *Pm;
    //     Rn = *Pn;

    //     int iters = (i+1)/2;
    //     for (j = 0; iters--; j++) {
    //       assert(Ra == Pa_base[j] && Rb == Pa_base[i-j], "must be");
    //       MACC2(Ra, Rb, t0, t1, t2);
    //       Ra = *++Pa;
    //       Rb = *--Pb;
    //       assert(Rm == Pm_base[j] && Rn == Pn_base[i-j], "must be");
    //       MACC(Rm, Rn, t0, t1, t2);
    //       Rm = *++Pm;
    //       Rn = *--Pn;
    //     }
    //     if ((i & 1) == 0) {
    //       assert(Ra == Pa_base[j], "must be");
    //       MACC(Ra, Ra, t0, t1, t2);
    //     }
    //     iters = i/2;
    //     assert(iters == i-j, "must be");
    //     for (; iters--; j++) {
    //       assert(Rm == Pm_base[j] && Rn == Pn_base[i-j], "must be");
    //       MACC(Rm, Rn, t0, t1, t2);
    //       Rm = *++Pm;
    //       Rn = *--Pn;
    //     }

    //     *Pm = Rm = t0 * inv;
    //     assert(Rm == Pm_base[i] && Rn == Pn_base[0], "must be");
    //     MACC(Rm, Rn, t0, t1, t2);

    //     assert(t0 == 0, "broken Montgomery multiply");

    //     t0 = t1; t1 = t2; t2 = 0;
    //   }

    //   for (i = len; i < 2*len; i++) {
    //     int start = i-len+1;
    //     int end = start + (len - start)/2;
    //     int j;

    //     Pa = Pa_base + i-len;
    //     Pb = Pa_base + len;
    //     Pm = Pm_base + i-len;
    //     Pn = Pn_base + len;

    //     Ra = *++Pa;
    //     Rb = *--Pb;
    //     Rm = *++Pm;
    //     Rn = *--Pn;

    //     int iters = (2*len-i-1)/2;
    //     assert(iters == end-start, "must be");
    //     for (j = start; iters--; j++) {
    //       assert(Ra == Pa_base[j] && Rb == Pa_base[i-j], "must be");
    //       MACC2(Ra, Rb, t0, t1, t2);
    //       Ra = *++Pa;
    //       Rb = *--Pb;
    //       assert(Rm == Pm_base[j] && Rn == Pn_base[i-j], "must be");
    //       MACC(Rm, Rn, t0, t1, t2);
    //       Rm = *++Pm;
    //       Rn = *--Pn;
    //     }
    //     if ((i & 1) == 0) {
    //       assert(Ra == Pa_base[j], "must be");
    //       MACC(Ra, Ra, t0, t1, t2);
    //     }
    //     iters =  (2*len-i)/2;
    //     assert(iters == len-j, "must be");
    //     for (; iters--; j++) {
    //       assert(Rm == Pm_base[j] && Rn == Pn_base[i-j], "must be");
    //       MACC(Rm, Rn, t0, t1, t2);
    //       Rm = *++Pm;
    //       Rn = *--Pn;
    //     }
    //     Pm_base[i-len] = t0;
    //     t0 = t1; t1 = t2; t2 = 0;
    //   }

    //   while (t0)
    //     t0 = sub(Pm_base, Pn_base, t0, len);
    // }
  };

  // Initialization
  void generate_preuniverse_stubs() {
    // preuniverse stubs are not needed for aarch64
  }

  void generate_initial_stubs() {
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

    // Initialize table for copy memory (arraycopy) check.
    if (UnsafeMemoryAccess::_table == nullptr) {
      UnsafeMemoryAccess::create_table(8 + 4); // 8 for copyMemory; 4 for setMemory
    }

    if (UseCRC32Intrinsics) {
      StubRoutines::_updateBytesCRC32 = generate_updateBytesCRC32();
    }

    if (UseCRC32CIntrinsics) {
      StubRoutines::_updateBytesCRC32C = generate_updateBytesCRC32C();
    }

    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_dsin)) {
      StubRoutines::_dsin = generate_dsin_dcos(/* isCos = */ false);
    }

    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_dcos)) {
      StubRoutines::_dcos = generate_dsin_dcos(/* isCos = */ true);
    }

    if (vmIntrinsics::is_intrinsic_available(vmIntrinsics::_float16ToFloat) &&
        vmIntrinsics::is_intrinsic_available(vmIntrinsics::_floatToFloat16)) {
      StubRoutines::_hf2f = generate_float16ToFloat();
      StubRoutines::_f2hf = generate_floatToFloat16();
    }
  }

  void generate_continuation_stubs() {
    // Continuation stubs:
    StubRoutines::_cont_thaw          = generate_cont_thaw();
    StubRoutines::_cont_returnBarrier = generate_cont_returnBarrier();
    StubRoutines::_cont_returnBarrierExc = generate_cont_returnBarrier_exception();
    StubRoutines::_cont_preempt_stub = generate_cont_preempt_stub();
  }

  void generate_final_stubs() {
    // support for verify_oop (must happen after universe_init)
    if (VerifyOops) {
      StubRoutines::_verify_oop_subroutine_entry   = generate_verify_oop();
    }

    // arraycopy stubs used by compilers
    generate_arraycopy_stubs();

    StubRoutines::_method_entry_barrier = generate_method_entry_barrier();

    StubRoutines::aarch64::_spin_wait = generate_spin_wait();

    StubRoutines::_upcall_stub_exception_handler = generate_upcall_stub_exception_handler();
    StubRoutines::_upcall_stub_load_target = generate_upcall_stub_load_target();

#if defined (LINUX) && !defined (__ARM_FEATURE_ATOMICS)

    generate_atomic_entry_points();

#endif // LINUX

#ifdef COMPILER2
    if (UseSecondarySupersTable) {
      StubRoutines::_lookup_secondary_supers_table_slow_path_stub = generate_lookup_secondary_supers_table_slow_path_stub();
      if (! InlineSecondarySupersTest) {
        generate_lookup_secondary_supers_table_stub();
      }
    }
#endif

    StubRoutines::_unsafe_setmemory = generate_unsafe_setmemory();

    StubRoutines::aarch64::set_completed(); // Inidicate that arraycopy and zero_blocks stubs are generated
  }

  void generate_compiler_stubs() {
#if COMPILER2_OR_JVMCI

    if (UseSVE == 0) {
      StubRoutines::aarch64::_vector_iota_indices = generate_iota_indices(StubId::stubgen_vector_iota_indices_id);
    }

    // array equals stub for large arrays.
    if (!UseSimpleArrayEquals) {
      StubRoutines::aarch64::_large_array_equals = generate_large_array_equals();
    }

    // arrays_hascode stub for large arrays.
    StubRoutines::aarch64::_large_arrays_hashcode_boolean = generate_large_arrays_hashcode(T_BOOLEAN);
    StubRoutines::aarch64::_large_arrays_hashcode_byte = generate_large_arrays_hashcode(T_BYTE);
    StubRoutines::aarch64::_large_arrays_hashcode_char = generate_large_arrays_hashcode(T_CHAR);
    StubRoutines::aarch64::_large_arrays_hashcode_int = generate_large_arrays_hashcode(T_INT);
    StubRoutines::aarch64::_large_arrays_hashcode_short = generate_large_arrays_hashcode(T_SHORT);

    // byte_array_inflate stub for large arrays.
    StubRoutines::aarch64::_large_byte_array_inflate = generate_large_byte_array_inflate();

    // countPositives stub for large arrays.
    StubRoutines::aarch64::_count_positives = generate_count_positives(StubRoutines::aarch64::_count_positives_long);

    generate_compare_long_strings();

    generate_string_indexof_stubs();

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

    if (UseSIMDForBigIntegerShiftIntrinsics) {
      StubRoutines::_bigIntegerRightShiftWorker = generate_bigIntegerRightShift();
      StubRoutines::_bigIntegerLeftShiftWorker  = generate_bigIntegerLeftShift();
    }

    if (UseMontgomeryMultiplyIntrinsic) {
      StubId stub_id = StubId::stubgen_montgomeryMultiply_id;
      StubCodeMark mark(this, stub_id);
      MontgomeryMultiplyGenerator g(_masm, /*squaring*/false);
      StubRoutines::_montgomeryMultiply = g.generate_multiply();
    }

    if (UseMontgomerySquareIntrinsic) {
      StubId stub_id = StubId::stubgen_montgomerySquare_id;
      StubCodeMark mark(this, stub_id);
      MontgomeryMultiplyGenerator g(_masm, /*squaring*/true);
      // We use generate_multiply() rather than generate_square()
      // because it's faster for the sizes of modulus we care about.
      StubRoutines::_montgomerySquare = g.generate_multiply();
    }

#endif // COMPILER2

    if (UseChaCha20Intrinsics) {
      StubRoutines::_chacha20Block = generate_chacha20Block_blockpar();
    }

    if (UseKyberIntrinsics) {
      StubRoutines::_kyberNtt = generate_kyberNtt();
      StubRoutines::_kyberInverseNtt = generate_kyberInverseNtt();
      StubRoutines::_kyberNttMult = generate_kyberNttMult();
      StubRoutines::_kyberAddPoly_2 = generate_kyberAddPoly_2();
      StubRoutines::_kyberAddPoly_3 = generate_kyberAddPoly_3();
      StubRoutines::_kyber12To16 = generate_kyber12To16();
      StubRoutines::_kyberBarrettReduce = generate_kyberBarrettReduce();
    }

    if (UseDilithiumIntrinsics) {
      StubRoutines::_dilithiumAlmostNtt = generate_dilithiumAlmostNtt();
      StubRoutines::_dilithiumAlmostInverseNtt = generate_dilithiumAlmostInverseNtt();
      StubRoutines::_dilithiumNttMult = generate_dilithiumNttMult();
      StubRoutines::_dilithiumMontMulByConstant = generate_dilithiumMontMulByConstant();
      StubRoutines::_dilithiumDecomposePoly = generate_dilithiumDecomposePoly();
    }

    if (UseBASE64Intrinsics) {
        StubRoutines::_base64_encodeBlock = generate_base64_encodeBlock();
        StubRoutines::_base64_decodeBlock = generate_base64_decodeBlock();
    }

    // data cache line writeback
    StubRoutines::_data_cache_writeback = generate_data_cache_writeback();
    StubRoutines::_data_cache_writeback_sync = generate_data_cache_writeback_sync();

    if (UseAESIntrinsics) {
      StubRoutines::_aescrypt_encryptBlock = generate_aescrypt_encryptBlock();
      StubRoutines::_aescrypt_decryptBlock = generate_aescrypt_decryptBlock();
      StubRoutines::_cipherBlockChaining_encryptAESCrypt = generate_cipherBlockChaining_encryptAESCrypt();
      StubRoutines::_cipherBlockChaining_decryptAESCrypt = generate_cipherBlockChaining_decryptAESCrypt();
      StubRoutines::_counterMode_AESCrypt = generate_counterMode_AESCrypt();
    }
    if (UseGHASHIntrinsics) {
      // StubRoutines::_ghash_processBlocks = generate_ghash_processBlocks();
      StubRoutines::_ghash_processBlocks = generate_ghash_processBlocks_wide();
    }
    if (UseAESIntrinsics && UseGHASHIntrinsics) {
      StubRoutines::_galoisCounterMode_AESCrypt = generate_galoisCounterMode_AESCrypt();
    }

    if (UseMD5Intrinsics) {
      StubRoutines::_md5_implCompress      = generate_md5_implCompress(StubId::stubgen_md5_implCompress_id);
      StubRoutines::_md5_implCompressMB    = generate_md5_implCompress(StubId::stubgen_md5_implCompressMB_id);
    }
    if (UseSHA1Intrinsics) {
      StubRoutines::_sha1_implCompress     = generate_sha1_implCompress(StubId::stubgen_sha1_implCompress_id);
      StubRoutines::_sha1_implCompressMB   = generate_sha1_implCompress(StubId::stubgen_sha1_implCompressMB_id);
    }
    if (UseSHA256Intrinsics) {
      StubRoutines::_sha256_implCompress   = generate_sha256_implCompress(StubId::stubgen_sha256_implCompress_id);
      StubRoutines::_sha256_implCompressMB = generate_sha256_implCompress(StubId::stubgen_sha256_implCompressMB_id);
    }
    if (UseSHA512Intrinsics) {
      StubRoutines::_sha512_implCompress   = generate_sha512_implCompress(StubId::stubgen_sha512_implCompress_id);
      StubRoutines::_sha512_implCompressMB = generate_sha512_implCompress(StubId::stubgen_sha512_implCompressMB_id);
    }
    if (UseSHA3Intrinsics) {

      StubRoutines::_double_keccak         = generate_double_keccak();
      if (UseSIMDForSHA3Intrinsic) {
         StubRoutines::_sha3_implCompress     = generate_sha3_implCompress(StubId::stubgen_sha3_implCompress_id);
         StubRoutines::_sha3_implCompressMB   = generate_sha3_implCompress(StubId::stubgen_sha3_implCompressMB_id);
      } else {
         StubRoutines::_sha3_implCompress     = generate_sha3_implCompress_gpr(StubId::stubgen_sha3_implCompress_id);
         StubRoutines::_sha3_implCompressMB   = generate_sha3_implCompress_gpr(StubId::stubgen_sha3_implCompressMB_id);
      }
    }

    if (UsePoly1305Intrinsics) {
      StubRoutines::_poly1305_processBlocks = generate_poly1305_processBlocks();
    }

    // generate Adler32 intrinsics code
    if (UseAdler32Intrinsics) {
      StubRoutines::_updateBytesAdler32 = generate_updateBytesAdler32();
    }

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
}; // end class declaration

void StubGenerator_generate(CodeBuffer* code, BlobId blob_id) {
  StubGenerator g(code, blob_id);
}


#if defined (LINUX)

// Define pointers to atomic stubs and initialize them to point to the
// code in atomic_aarch64.S.

#define DEFAULT_ATOMIC_OP(OPNAME, SIZE, RELAXED)                                \
  extern "C" uint64_t aarch64_atomic_ ## OPNAME ## _ ## SIZE ## RELAXED ## _default_impl \
    (volatile void *ptr, uint64_t arg1, uint64_t arg2);                 \
  aarch64_atomic_stub_t aarch64_atomic_ ## OPNAME ## _ ## SIZE ## RELAXED ## _impl \
    = aarch64_atomic_ ## OPNAME ## _ ## SIZE ## RELAXED ## _default_impl;

DEFAULT_ATOMIC_OP(fetch_add, 4, )
DEFAULT_ATOMIC_OP(fetch_add, 8, )
DEFAULT_ATOMIC_OP(fetch_add, 4, _relaxed)
DEFAULT_ATOMIC_OP(fetch_add, 8, _relaxed)
DEFAULT_ATOMIC_OP(xchg, 4, )
DEFAULT_ATOMIC_OP(xchg, 8, )
DEFAULT_ATOMIC_OP(cmpxchg, 1, )
DEFAULT_ATOMIC_OP(cmpxchg, 4, )
DEFAULT_ATOMIC_OP(cmpxchg, 8, )
DEFAULT_ATOMIC_OP(cmpxchg, 1, _relaxed)
DEFAULT_ATOMIC_OP(cmpxchg, 4, _relaxed)
DEFAULT_ATOMIC_OP(cmpxchg, 8, _relaxed)
DEFAULT_ATOMIC_OP(cmpxchg, 4, _release)
DEFAULT_ATOMIC_OP(cmpxchg, 8, _release)
DEFAULT_ATOMIC_OP(cmpxchg, 4, _seq_cst)
DEFAULT_ATOMIC_OP(cmpxchg, 8, _seq_cst)

#undef DEFAULT_ATOMIC_OP

#endif // LINUX
