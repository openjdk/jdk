/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "nativeInst_sparc.hpp"
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
#include "utilities/top.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// Declaration and definition of StubGenerator (no .hpp file).
// For a more detailed description of the stub routine structure
// see the comment in stubRoutines.hpp.

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

// Note:  The register L7 is used as L7_thread_cache, and may not be used
//        any other way within this module.


static const Register& Lstub_temp = L2;

// -------------------------------------------------------------------------------------------------------------------------
// Stub Code definitions

static address handle_unsafe_access() {
  JavaThread* thread = JavaThread::current();
  address pc  = thread->saved_exception_pc();
  address npc = thread->saved_exception_npc();
  // pc is the instruction which we must emulate
  // doing a no-op is fine:  return garbage from the load

  // request an async exception
  thread->set_pending_unsafe_access_error();

  // return address of next instruction to execute
  return npc;
}

class StubGenerator: public StubCodeGenerator {
 private:

#ifdef PRODUCT
#define inc_counter_np(a,b,c) (0)
#else
#define inc_counter_np(counter, t1, t2) \
  BLOCK_COMMENT("inc_counter " #counter); \
  __ inc_counter(&counter, t1, t2);
#endif

  //----------------------------------------------------------------------------------------------------
  // Call stubs are used to call Java from C

  address generate_call_stub(address& return_pc) {
    StubCodeMark mark(this, "StubRoutines", "call_stub");
    address start = __ pc();

    // Incoming arguments:
    //
    // o0         : call wrapper address
    // o1         : result (address)
    // o2         : result type
    // o3         : method
    // o4         : (interpreter) entry point
    // o5         : parameters (address)
    // [sp + 0x5c]: parameter size (in words)
    // [sp + 0x60]: thread
    //
    // +---------------+ <--- sp + 0
    // |               |
    // . reg save area .
    // |               |
    // +---------------+ <--- sp + 0x40
    // |               |
    // . extra 7 slots .
    // |               |
    // +---------------+ <--- sp + 0x5c
    // |  param. size  |
    // +---------------+ <--- sp + 0x60
    // |    thread     |
    // +---------------+
    // |               |

    // note: if the link argument position changes, adjust
    //       the code in frame::entry_frame_call_wrapper()

    const Argument link           = Argument(0, false); // used only for GC
    const Argument result         = Argument(1, false);
    const Argument result_type    = Argument(2, false);
    const Argument method         = Argument(3, false);
    const Argument entry_point    = Argument(4, false);
    const Argument parameters     = Argument(5, false);
    const Argument parameter_size = Argument(6, false);
    const Argument thread         = Argument(7, false);

    // setup thread register
    __ ld_ptr(thread.as_address(), G2_thread);
    __ reinit_heapbase();

#ifdef ASSERT
    // make sure we have no pending exceptions
    { const Register t = G3_scratch;
      Label L;
      __ ld_ptr(G2_thread, in_bytes(Thread::pending_exception_offset()), t);
      __ br_null_short(t, Assembler::pt, L);
      __ stop("StubRoutines::call_stub: entered with pending exception");
      __ bind(L);
    }
#endif

    // create activation frame & allocate space for parameters
    { const Register t = G3_scratch;
      __ ld_ptr(parameter_size.as_address(), t);                // get parameter size (in words)
      __ add(t, frame::memory_parameter_word_sp_offset, t);     // add space for save area (in words)
      __ round_to(t, WordsPerLong);                             // make sure it is multiple of 2 (in words)
      __ sll(t, Interpreter::logStackElementSize, t);           // compute number of bytes
      __ neg(t);                                                // negate so it can be used with save
      __ save(SP, t, SP);                                       // setup new frame
    }

    // +---------------+ <--- sp + 0
    // |               |
    // . reg save area .
    // |               |
    // +---------------+ <--- sp + 0x40
    // |               |
    // . extra 7 slots .
    // |               |
    // +---------------+ <--- sp + 0x5c
    // |  empty slot   |      (only if parameter size is even)
    // +---------------+
    // |               |
    // .  parameters   .
    // |               |
    // +---------------+ <--- fp + 0
    // |               |
    // . reg save area .
    // |               |
    // +---------------+ <--- fp + 0x40
    // |               |
    // . extra 7 slots .
    // |               |
    // +---------------+ <--- fp + 0x5c
    // |  param. size  |
    // +---------------+ <--- fp + 0x60
    // |    thread     |
    // +---------------+
    // |               |

    // pass parameters if any
    BLOCK_COMMENT("pass parameters if any");
    { const Register src = parameters.as_in().as_register();
      const Register dst = Lentry_args;
      const Register tmp = G3_scratch;
      const Register cnt = G4_scratch;

      // test if any parameters & setup of Lentry_args
      Label exit;
      __ ld_ptr(parameter_size.as_in().as_address(), cnt);      // parameter counter
      __ add( FP, STACK_BIAS, dst );
      __ cmp_zero_and_br(Assembler::zero, cnt, exit);
      __ delayed()->sub(dst, BytesPerWord, dst);                 // setup Lentry_args

      // copy parameters if any
      Label loop;
      __ BIND(loop);
      // Store parameter value
      __ ld_ptr(src, 0, tmp);
      __ add(src, BytesPerWord, src);
      __ st_ptr(tmp, dst, 0);
      __ deccc(cnt);
      __ br(Assembler::greater, false, Assembler::pt, loop);
      __ delayed()->sub(dst, Interpreter::stackElementSize, dst);

      // done
      __ BIND(exit);
    }

    // setup parameters, method & call Java function
#ifdef ASSERT
    // layout_activation_impl checks it's notion of saved SP against
    // this register, so if this changes update it as well.
    const Register saved_SP = Lscratch;
    __ mov(SP, saved_SP);                               // keep track of SP before call
#endif

    // setup parameters
    const Register t = G3_scratch;
    __ ld_ptr(parameter_size.as_in().as_address(), t); // get parameter size (in words)
    __ sll(t, Interpreter::logStackElementSize, t);    // compute number of bytes
    __ sub(FP, t, Gargs);                              // setup parameter pointer
#ifdef _LP64
    __ add( Gargs, STACK_BIAS, Gargs );                // Account for LP64 stack bias
#endif
    __ mov(SP, O5_savedSP);


    // do the call
    //
    // the following register must be setup:
    //
    // G2_thread
    // G5_method
    // Gargs
    BLOCK_COMMENT("call Java function");
    __ jmpl(entry_point.as_in().as_register(), G0, O7);
    __ delayed()->mov(method.as_in().as_register(), G5_method);   // setup method

    BLOCK_COMMENT("call_stub_return_address:");
    return_pc = __ pc();

    // The callee, if it wasn't interpreted, can return with SP changed so
    // we can no longer assert of change of SP.

    // store result depending on type
    // (everything that is not T_OBJECT, T_LONG, T_FLOAT, or T_DOUBLE
    //  is treated as T_INT)
    { const Register addr = result     .as_in().as_register();
      const Register type = result_type.as_in().as_register();
      Label is_long, is_float, is_double, is_object, exit;
      __            cmp(type, T_OBJECT);  __ br(Assembler::equal, false, Assembler::pn, is_object);
      __ delayed()->cmp(type, T_FLOAT);   __ br(Assembler::equal, false, Assembler::pn, is_float);
      __ delayed()->cmp(type, T_DOUBLE);  __ br(Assembler::equal, false, Assembler::pn, is_double);
      __ delayed()->cmp(type, T_LONG);    __ br(Assembler::equal, false, Assembler::pn, is_long);
      __ delayed()->nop();

      // store int result
      __ st(O0, addr, G0);

      __ BIND(exit);
      __ ret();
      __ delayed()->restore();

      __ BIND(is_object);
      __ ba(exit);
      __ delayed()->st_ptr(O0, addr, G0);

      __ BIND(is_float);
      __ ba(exit);
      __ delayed()->stf(FloatRegisterImpl::S, F0, addr, G0);

      __ BIND(is_double);
      __ ba(exit);
      __ delayed()->stf(FloatRegisterImpl::D, F0, addr, G0);

      __ BIND(is_long);
#ifdef _LP64
      __ ba(exit);
      __ delayed()->st_long(O0, addr, G0);      // store entire long
#else
#if defined(COMPILER2)
  // All return values are where we want them, except for Longs.  C2 returns
  // longs in G1 in the 32-bit build whereas the interpreter wants them in O0/O1.
  // Since the interpreter will return longs in G1 and O0/O1 in the 32bit
  // build we simply always use G1.
  // Note: I tried to make c2 return longs in O0/O1 and G1 so we wouldn't have to
  // do this here. Unfortunately if we did a rethrow we'd see an machepilog node
  // first which would move g1 -> O0/O1 and destroy the exception we were throwing.

      __ ba(exit);
      __ delayed()->stx(G1, addr, G0);  // store entire long
#else
      __ st(O1, addr, BytesPerInt);
      __ ba(exit);
      __ delayed()->st(O0, addr, G0);
#endif /* COMPILER2 */
#endif /* _LP64 */
     }
     return start;
  }


  //----------------------------------------------------------------------------------------------------
  // Return point for a Java call if there's an exception thrown in Java code.
  // The exception is caught and transformed into a pending exception stored in
  // JavaThread that can be tested from within the VM.
  //
  // Oexception: exception oop

  address generate_catch_exception() {
    StubCodeMark mark(this, "StubRoutines", "catch_exception");

    address start = __ pc();
    // verify that thread corresponds
    __ verify_thread();

    const Register& temp_reg = Gtemp;
    Address pending_exception_addr    (G2_thread, Thread::pending_exception_offset());
    Address exception_file_offset_addr(G2_thread, Thread::exception_file_offset   ());
    Address exception_line_offset_addr(G2_thread, Thread::exception_line_offset   ());

    // set pending exception
    __ verify_oop(Oexception);
    __ st_ptr(Oexception, pending_exception_addr);
    __ set((intptr_t)__FILE__, temp_reg);
    __ st_ptr(temp_reg, exception_file_offset_addr);
    __ set((intptr_t)__LINE__, temp_reg);
    __ st(temp_reg, exception_line_offset_addr);

    // complete return to VM
    assert(StubRoutines::_call_stub_return_address != NULL, "must have been generated before");

    AddressLiteral stub_ret(StubRoutines::_call_stub_return_address);
    __ jump_to(stub_ret, temp_reg);
    __ delayed()->nop();

    return start;
  }


  //----------------------------------------------------------------------------------------------------
  // Continuation point for runtime calls returning with a pending exception
  // The pending exception check happened in the runtime or native call stub
  // The pending exception in Thread is converted into a Java-level exception
  //
  // Contract with Java-level exception handler: O0 = exception
  //                                             O1 = throwing pc

  address generate_forward_exception() {
    StubCodeMark mark(this, "StubRoutines", "forward_exception");
    address start = __ pc();

    // Upon entry, O7 has the return address returning into Java
    // (interpreted or compiled) code; i.e. the return address
    // becomes the throwing pc.

    const Register& handler_reg = Gtemp;

    Address exception_addr(G2_thread, Thread::pending_exception_offset());

#ifdef ASSERT
    // make sure that this code is only executed if there is a pending exception
    { Label L;
      __ ld_ptr(exception_addr, Gtemp);
      __ br_notnull_short(Gtemp, Assembler::pt, L);
      __ stop("StubRoutines::forward exception: no pending exception (1)");
      __ bind(L);
    }
#endif

    // compute exception handler into handler_reg
    __ get_thread();
    __ ld_ptr(exception_addr, Oexception);
    __ verify_oop(Oexception);
    __ save_frame(0);             // compensates for compiler weakness
    __ add(O7->after_save(), frame::pc_return_offset, Lscratch); // save the issuing PC
    BLOCK_COMMENT("call exception_handler_for_return_address");
    __ call_VM_leaf(L7_thread_cache, CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), G2_thread, Lscratch);
    __ mov(O0, handler_reg);
    __ restore();                 // compensates for compiler weakness

    __ ld_ptr(exception_addr, Oexception);
    __ add(O7, frame::pc_return_offset, Oissuing_pc); // save the issuing PC

#ifdef ASSERT
    // make sure exception is set
    { Label L;
      __ br_notnull_short(Oexception, Assembler::pt, L);
      __ stop("StubRoutines::forward exception: no pending exception (2)");
      __ bind(L);
    }
#endif
    // jump to exception handler
    __ jmp(handler_reg, 0);
    // clear pending exception
    __ delayed()->st_ptr(G0, exception_addr);

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
    //   o0 = adr
    //   o1 = errValue
    //
    // result:
    //   o0  = *adr or errValue

    StubCodeMark mark(this, "StubRoutines", name);

    // Entry point, pc or function descriptor.
    __ align(CodeEntryAlignment);
    *entry = __ pc();

    __ mov(O0, G1);  // g1 = o0
    __ mov(O1, O0);  // o0 = o1
    // Load *adr into c_rarg1, may fault.
    *fault_pc = __ pc();
    switch (size) {
      case 4:
        // int32_t
        __ ldsw(G1, 0, O0);  // o0 = [g1]
        break;
      case 8:
        // int64_t
        __ ldx(G1, 0, O0);   // o0 = [g1]
        break;
      default:
        ShouldNotReachHere();
    }

    // return errValue or *adr
    *continuation_pc = __ pc();
    // By convention with the trap handler we ensure there is a non-CTI
    // instruction in the trap shadow.
    __ nop();
    __ retl();
    __ delayed()->nop();
  }

  //------------------------------------------------------------------------------------------------------------------------
  // Continuation point for throwing of implicit exceptions that are not handled in
  // the current activation. Fabricates an exception oop and initiates normal
  // exception dispatching in this frame. Only callee-saved registers are preserved
  // (through the normal register window / RegisterMap handling).
  // If the compiler needs all registers to be preserved between the fault
  // point and the exception handler then it must assume responsibility for that in
  // AbstractCompiler::continuation_for_implicit_null_exception or
  // continuation_for_implicit_division_by_zero_exception. All other implicit
  // exceptions (e.g., NullPointerException or AbstractMethodError on entry) are
  // either at call sites or otherwise assume that stack unwinding will be initiated,
  // so caller saved registers were assumed volatile in the compiler.

  // Note that we generate only this stub into a RuntimeStub, because it needs to be
  // properly traversed and ignored during GC, so we change the meaning of the "__"
  // macro within this method.
#undef __
#define __ masm->

  address generate_throw_exception(const char* name, address runtime_entry,
                                   Register arg1 = noreg, Register arg2 = noreg) {
#ifdef ASSERT
    int insts_size = VerifyThread ? 1 * K : 600;
#else
    int insts_size = VerifyThread ? 1 * K : 256;
#endif /* ASSERT */
    int locs_size  = 32;

    CodeBuffer      code(name, insts_size, locs_size);
    MacroAssembler* masm = new MacroAssembler(&code);

    __ verify_thread();

    // This is an inlined and slightly modified version of call_VM
    // which has the ability to fetch the return PC out of thread-local storage
    __ assert_not_delayed();

    // Note that we always push a frame because on the SPARC
    // architecture, for all of our implicit exception kinds at call
    // sites, the implicit exception is taken before the callee frame
    // is pushed.
    __ save_frame(0);

    int frame_complete = __ offset();

    // Note that we always have a runtime stub frame on the top of stack by this point
    Register last_java_sp = SP;
    // 64-bit last_java_sp is biased!
    __ set_last_Java_frame(last_java_sp, G0);
    if (VerifyThread)  __ mov(G2_thread, O0); // about to be smashed; pass early
    __ save_thread(noreg);
    if (arg1 != noreg) {
      assert(arg2 != O1, "clobbered");
      __ mov(arg1, O1);
    }
    if (arg2 != noreg) {
      __ mov(arg2, O2);
    }
    // do the call
    BLOCK_COMMENT("call runtime_entry");
    __ call(runtime_entry, relocInfo::runtime_call_type);
    if (!VerifyThread)
      __ delayed()->mov(G2_thread, O0);  // pass thread as first argument
    else
      __ delayed()->nop();             // (thread already passed)
    __ restore_thread(noreg);
    __ reset_last_Java_frame();

    // check for pending exceptions. use Gtemp as scratch register.
#ifdef ASSERT
    Label L;

    Address exception_addr(G2_thread, Thread::pending_exception_offset());
    Register scratch_reg = Gtemp;
    __ ld_ptr(exception_addr, scratch_reg);
    __ br_notnull_short(scratch_reg, Assembler::pt, L);
    __ should_not_reach_here();
    __ bind(L);
#endif // ASSERT
    BLOCK_COMMENT("call forward_exception_entry");
    __ call(StubRoutines::forward_exception_entry(), relocInfo::runtime_call_type);
    // we use O7 linkage so that forward_exception_entry has the issuing PC
    __ delayed()->restore();

    RuntimeStub* stub = RuntimeStub::new_runtime_stub(name, &code, frame_complete, masm->total_frame_size_in_bytes(0), NULL, false);
    return stub->entry_point();
  }

#undef __
#define __ _masm->


  // Generate a routine that sets all the registers so we
  // can tell if the stop routine prints them correctly.
  address generate_test_stop() {
    StubCodeMark mark(this, "StubRoutines", "test_stop");
    address start = __ pc();

    int i;

    __ save_frame(0);

    static jfloat zero = 0.0, one = 1.0;

    // put addr in L0, then load through L0 to F0
    __ set((intptr_t)&zero, L0);  __ ldf( FloatRegisterImpl::S, L0, 0, F0);
    __ set((intptr_t)&one,  L0);  __ ldf( FloatRegisterImpl::S, L0, 0, F1); // 1.0 to F1

    // use add to put 2..18 in F2..F18
    for ( i = 2;  i <= 18;  ++i ) {
      __ fadd( FloatRegisterImpl::S, F1, as_FloatRegister(i-1),  as_FloatRegister(i));
    }

    // Now put double 2 in F16, double 18 in F18
    __ ftof( FloatRegisterImpl::S, FloatRegisterImpl::D, F2, F16 );
    __ ftof( FloatRegisterImpl::S, FloatRegisterImpl::D, F18, F18 );

    // use add to put 20..32 in F20..F32
    for (i = 20; i < 32; i += 2) {
      __ fadd( FloatRegisterImpl::D, F16, as_FloatRegister(i-2),  as_FloatRegister(i));
    }

    // put 0..7 in i's, 8..15 in l's, 16..23 in o's, 24..31 in g's
    for ( i = 0; i < 8; ++i ) {
      if (i < 6) {
        __ set(     i, as_iRegister(i));
        __ set(16 + i, as_oRegister(i));
        __ set(24 + i, as_gRegister(i));
      }
      __ set( 8 + i, as_lRegister(i));
    }

    __ stop("testing stop");


    __ ret();
    __ delayed()->restore();

    return start;
  }


  address generate_stop_subroutine() {
    StubCodeMark mark(this, "StubRoutines", "stop_subroutine");
    address start = __ pc();

    __ stop_subroutine();

    return start;
  }

  address generate_flush_callers_register_windows() {
    StubCodeMark mark(this, "StubRoutines", "flush_callers_register_windows");
    address start = __ pc();

    __ flushw();
    __ retl(false);
    __ delayed()->add( FP, STACK_BIAS, O0 );
    // The returned value must be a stack pointer whose register save area
    // is flushed, and will stay flushed while the caller executes.

    return start;
  }

  // Support for jint Atomic::xchg(jint exchange_value, volatile jint* dest).
  //
  // Arguments:
  //
  //      exchange_value: O0
  //      dest:           O1
  //
  // Results:
  //
  //     O0: the value previously stored in dest
  //
  address generate_atomic_xchg() {
    StubCodeMark mark(this, "StubRoutines", "atomic_xchg");
    address start = __ pc();

    if (UseCASForSwap) {
      // Use CAS instead of swap, just in case the MP hardware
      // prefers to work with just one kind of synch. instruction.
      Label retry;
      __ BIND(retry);
      __ mov(O0, O3);       // scratch copy of exchange value
      __ ld(O1, 0, O2);     // observe the previous value
      // try to replace O2 with O3
      __ cas(O1, O2, O3);
      __ cmp_and_br_short(O2, O3, Assembler::notEqual, Assembler::pn, retry);

      __ retl(false);
      __ delayed()->mov(O2, O0);  // report previous value to caller
    } else {
      __ retl(false);
      __ delayed()->swap(O1, 0, O0);
    }

    return start;
  }


  // Support for jint Atomic::cmpxchg(jint exchange_value, volatile jint* dest, jint compare_value)
  //
  // Arguments:
  //
  //      exchange_value: O0
  //      dest:           O1
  //      compare_value:  O2
  //
  // Results:
  //
  //     O0: the value previously stored in dest
  //
  address generate_atomic_cmpxchg() {
    StubCodeMark mark(this, "StubRoutines", "atomic_cmpxchg");
    address start = __ pc();

    // cmpxchg(dest, compare_value, exchange_value)
    __ cas(O1, O2, O0);
    __ retl(false);
    __ delayed()->nop();

    return start;
  }

  // Support for jlong Atomic::cmpxchg(jlong exchange_value, volatile jlong *dest, jlong compare_value)
  //
  // Arguments:
  //
  //      exchange_value: O1:O0
  //      dest:           O2
  //      compare_value:  O4:O3
  //
  // Results:
  //
  //     O1:O0: the value previously stored in dest
  //
  // Overwrites: G1,G2,G3
  //
  address generate_atomic_cmpxchg_long() {
    StubCodeMark mark(this, "StubRoutines", "atomic_cmpxchg_long");
    address start = __ pc();

    __ sllx(O0, 32, O0);
    __ srl(O1, 0, O1);
    __ or3(O0,O1,O0);      // O0 holds 64-bit value from compare_value
    __ sllx(O3, 32, O3);
    __ srl(O4, 0, O4);
    __ or3(O3,O4,O3);     // O3 holds 64-bit value from exchange_value
    __ casx(O2, O3, O0);
    __ srl(O0, 0, O1);    // unpacked return value in O1:O0
    __ retl(false);
    __ delayed()->srlx(O0, 32, O0);

    return start;
  }


  // Support for jint Atomic::add(jint add_value, volatile jint* dest).
  //
  // Arguments:
  //
  //      add_value: O0   (e.g., +1 or -1)
  //      dest:      O1
  //
  // Results:
  //
  //     O0: the new value stored in dest
  //
  // Overwrites: O3
  //
  address generate_atomic_add() {
    StubCodeMark mark(this, "StubRoutines", "atomic_add");
    address start = __ pc();
    __ BIND(_atomic_add_stub);

    Label(retry);
    __ BIND(retry);

    __ lduw(O1, 0, O2);
    __ add(O0, O2, O3);
    __ cas(O1, O2, O3);
    __ cmp_and_br_short(O2, O3, Assembler::notEqual, Assembler::pn, retry);
    __ retl(false);
    __ delayed()->add(O0, O2, O0); // note that cas made O2==O3

    return start;
  }
  Label _atomic_add_stub;  // called from other stubs


  //------------------------------------------------------------------------------------------------------------------------
  // The following routine generates a subroutine to throw an asynchronous
  // UnknownError when an unsafe access gets a fault that could not be
  // reasonably prevented by the programmer.  (Example: SIGBUS/OBJERR.)
  //
  // Arguments :
  //
  //      trapping PC:    O7
  //
  // Results:
  //     posts an asynchronous exception, skips the trapping instruction
  //

  address generate_handler_for_unsafe_access() {
    StubCodeMark mark(this, "StubRoutines", "handler_for_unsafe_access");
    address start = __ pc();

    const int preserve_register_words = (64 * 2);
    Address preserve_addr(FP, (-preserve_register_words * wordSize) + STACK_BIAS);

    Register Lthread = L7_thread_cache;
    int i;

    __ save_frame(0);
    __ mov(G1, L1);
    __ mov(G2, L2);
    __ mov(G3, L3);
    __ mov(G4, L4);
    __ mov(G5, L5);
    for (i = 0; i < 64; i += 2) {
      __ stf(FloatRegisterImpl::D, as_FloatRegister(i), preserve_addr, i * wordSize);
    }

    address entry_point = CAST_FROM_FN_PTR(address, handle_unsafe_access);
    BLOCK_COMMENT("call handle_unsafe_access");
    __ call(entry_point, relocInfo::runtime_call_type);
    __ delayed()->nop();

    __ mov(L1, G1);
    __ mov(L2, G2);
    __ mov(L3, G3);
    __ mov(L4, G4);
    __ mov(L5, G5);
    for (i = 0; i < 64; i += 2) {
      __ ldf(FloatRegisterImpl::D, preserve_addr, as_FloatRegister(i), i * wordSize);
    }

    __ verify_thread();

    __ jmp(O0, 0);
    __ delayed()->restore();

    return start;
  }


  // Support for uint StubRoutine::Sparc::partial_subtype_check( Klass sub, Klass super );
  // Arguments :
  //
  //      ret  : O0, returned
  //      icc/xcc: set as O0 (depending on wordSize)
  //      sub  : O1, argument, not changed
  //      super: O2, argument, not changed
  //      raddr: O7, blown by call
  address generate_partial_subtype_check() {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", "partial_subtype_check");
    address start = __ pc();
    Label miss;

#if defined(COMPILER2) && !defined(_LP64)
    // Do not use a 'save' because it blows the 64-bit O registers.
    __ add(SP,-4*wordSize,SP);  // Make space for 4 temps (stack must be 2 words aligned)
    __ st_ptr(L0,SP,(frame::register_save_words+0)*wordSize);
    __ st_ptr(L1,SP,(frame::register_save_words+1)*wordSize);
    __ st_ptr(L2,SP,(frame::register_save_words+2)*wordSize);
    __ st_ptr(L3,SP,(frame::register_save_words+3)*wordSize);
    Register Rret   = O0;
    Register Rsub   = O1;
    Register Rsuper = O2;
#else
    __ save_frame(0);
    Register Rret   = I0;
    Register Rsub   = I1;
    Register Rsuper = I2;
#endif

    Register L0_ary_len = L0;
    Register L1_ary_ptr = L1;
    Register L2_super   = L2;
    Register L3_index   = L3;

    __ check_klass_subtype_slow_path(Rsub, Rsuper,
                                     L0, L1, L2, L3,
                                     NULL, &miss);

    // Match falls through here.
    __ addcc(G0,0,Rret);        // set Z flags, Z result

#if defined(COMPILER2) && !defined(_LP64)
    __ ld_ptr(SP,(frame::register_save_words+0)*wordSize,L0);
    __ ld_ptr(SP,(frame::register_save_words+1)*wordSize,L1);
    __ ld_ptr(SP,(frame::register_save_words+2)*wordSize,L2);
    __ ld_ptr(SP,(frame::register_save_words+3)*wordSize,L3);
    __ retl();                  // Result in Rret is zero; flags set to Z
    __ delayed()->add(SP,4*wordSize,SP);
#else
    __ ret();                   // Result in Rret is zero; flags set to Z
    __ delayed()->restore();
#endif

    __ BIND(miss);
    __ addcc(G0,1,Rret);        // set NZ flags, NZ result

#if defined(COMPILER2) && !defined(_LP64)
    __ ld_ptr(SP,(frame::register_save_words+0)*wordSize,L0);
    __ ld_ptr(SP,(frame::register_save_words+1)*wordSize,L1);
    __ ld_ptr(SP,(frame::register_save_words+2)*wordSize,L2);
    __ ld_ptr(SP,(frame::register_save_words+3)*wordSize,L3);
    __ retl();                  // Result in Rret is != 0; flags set to NZ
    __ delayed()->add(SP,4*wordSize,SP);
#else
    __ ret();                   // Result in Rret is != 0; flags set to NZ
    __ delayed()->restore();
#endif

    return start;
  }


  // Called from MacroAssembler::verify_oop
  //
  address generate_verify_oop_subroutine() {
    StubCodeMark mark(this, "StubRoutines", "verify_oop_stub");

    address start = __ pc();

    __ verify_oop_subroutine();

    return start;
  }


  //
  // Verify that a register contains clean 32-bits positive value
  // (high 32-bits are 0) so it could be used in 64-bits shifts (sllx, srax).
  //
  //  Input:
  //    Rint  -  32-bits value
  //    Rtmp  -  scratch
  //
  void assert_clean_int(Register Rint, Register Rtmp) {
#if defined(ASSERT) && defined(_LP64)
    __ signx(Rint, Rtmp);
    __ cmp(Rint, Rtmp);
    __ breakpoint_trap(Assembler::notEqual, Assembler::xcc);
#endif
  }

  //
  //  Generate overlap test for array copy stubs
  //
  //  Input:
  //    O0    -  array1
  //    O1    -  array2
  //    O2    -  element count
  //
  //  Kills temps:  O3, O4
  //
  void array_overlap_test(address no_overlap_target, int log2_elem_size) {
    assert(no_overlap_target != NULL, "must be generated");
    array_overlap_test(no_overlap_target, NULL, log2_elem_size);
  }
  void array_overlap_test(Label& L_no_overlap, int log2_elem_size) {
    array_overlap_test(NULL, &L_no_overlap, log2_elem_size);
  }
  void array_overlap_test(address no_overlap_target, Label* NOLp, int log2_elem_size) {
    const Register from       = O0;
    const Register to         = O1;
    const Register count      = O2;
    const Register to_from    = O3; // to - from
    const Register byte_count = O4; // count << log2_elem_size

      __ subcc(to, from, to_from);
      __ sll_ptr(count, log2_elem_size, byte_count);
      if (NOLp == NULL)
        __ brx(Assembler::lessEqualUnsigned, false, Assembler::pt, no_overlap_target);
      else
        __ brx(Assembler::lessEqualUnsigned, false, Assembler::pt, (*NOLp));
      __ delayed()->cmp(to_from, byte_count);
      if (NOLp == NULL)
        __ brx(Assembler::greaterEqualUnsigned, false, Assembler::pt, no_overlap_target);
      else
        __ brx(Assembler::greaterEqualUnsigned, false, Assembler::pt, (*NOLp));
      __ delayed()->nop();
  }

  //
  //  Generate pre-write barrier for array.
  //
  //  Input:
  //     addr     - register containing starting address
  //     count    - register containing element count
  //     tmp      - scratch register
  //
  //  The input registers are overwritten.
  //
  void gen_write_ref_array_pre_barrier(Register addr, Register count, bool dest_uninitialized) {
    BarrierSet* bs = Universe::heap()->barrier_set();
    switch (bs->kind()) {
      case BarrierSet::G1SATBCT:
      case BarrierSet::G1SATBCTLogging:
        // With G1, don't generate the call if we statically know that the target in uninitialized
        if (!dest_uninitialized) {
          __ save_frame(0);
          // Save the necessary global regs... will be used after.
          if (addr->is_global()) {
            __ mov(addr, L0);
          }
          if (count->is_global()) {
            __ mov(count, L1);
          }
          __ mov(addr->after_save(), O0);
          // Get the count into O1
          __ call(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_pre));
          __ delayed()->mov(count->after_save(), O1);
          if (addr->is_global()) {
            __ mov(L0, addr);
          }
          if (count->is_global()) {
            __ mov(L1, count);
          }
          __ restore();
        }
        break;
      case BarrierSet::CardTableModRef:
      case BarrierSet::CardTableExtension:
      case BarrierSet::ModRef:
        break;
      default:
        ShouldNotReachHere();
    }
  }
  //
  //  Generate post-write barrier for array.
  //
  //  Input:
  //     addr     - register containing starting address
  //     count    - register containing element count
  //     tmp      - scratch register
  //
  //  The input registers are overwritten.
  //
  void gen_write_ref_array_post_barrier(Register addr, Register count,
                                        Register tmp) {
    BarrierSet* bs = Universe::heap()->barrier_set();

    switch (bs->kind()) {
      case BarrierSet::G1SATBCT:
      case BarrierSet::G1SATBCTLogging:
        {
          // Get some new fresh output registers.
          __ save_frame(0);
          __ mov(addr->after_save(), O0);
          __ call(CAST_FROM_FN_PTR(address, BarrierSet::static_write_ref_array_post));
          __ delayed()->mov(count->after_save(), O1);
          __ restore();
        }
        break;
      case BarrierSet::CardTableModRef:
      case BarrierSet::CardTableExtension:
        {
          CardTableModRefBS* ct = (CardTableModRefBS*)bs;
          assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");
          assert_different_registers(addr, count, tmp);

          Label L_loop;

          __ sll_ptr(count, LogBytesPerHeapOop, count);
          __ sub(count, BytesPerHeapOop, count);
          __ add(count, addr, count);
          // Use two shifts to clear out those low order two bits! (Cannot opt. into 1.)
          __ srl_ptr(addr, CardTableModRefBS::card_shift, addr);
          __ srl_ptr(count, CardTableModRefBS::card_shift, count);
          __ sub(count, addr, count);
          AddressLiteral rs(ct->byte_map_base);
          __ set(rs, tmp);
        __ BIND(L_loop);
          __ stb(G0, tmp, addr);
          __ subcc(count, 1, count);
          __ brx(Assembler::greaterEqual, false, Assembler::pt, L_loop);
          __ delayed()->add(addr, 1, addr);
        }
        break;
      case BarrierSet::ModRef:
        break;
      default:
        ShouldNotReachHere();
    }
  }

  //
  // Generate main code for disjoint arraycopy
  //
  typedef void (StubGenerator::*CopyLoopFunc)(Register from, Register to, Register count, int count_dec,
                                              Label& L_loop, bool use_prefetch, bool use_bis);

  void disjoint_copy_core(Register from, Register to, Register count, int log2_elem_size,
                          int iter_size, CopyLoopFunc copy_loop_func) {
    Label L_copy;

    assert(log2_elem_size <= 3, "the following code should be changed");
    int count_dec = 16>>log2_elem_size;

    int prefetch_dist = MAX2(ArraycopySrcPrefetchDistance, ArraycopyDstPrefetchDistance);
    assert(prefetch_dist < 4096, "invalid value");
    prefetch_dist = (prefetch_dist + (iter_size-1)) & (-iter_size); // round up to one iteration copy size
    int prefetch_count = (prefetch_dist >> log2_elem_size); // elements count

    if (UseBlockCopy) {
      Label L_block_copy, L_block_copy_prefetch, L_skip_block_copy;

      // 64 bytes tail + bytes copied in one loop iteration
      int tail_size = 64 + iter_size;
      int block_copy_count = (MAX2(tail_size, (int)BlockCopyLowLimit)) >> log2_elem_size;
      // Use BIS copy only for big arrays since it requires membar.
      __ set(block_copy_count, O4);
      __ cmp_and_br_short(count, O4, Assembler::lessUnsigned, Assembler::pt, L_skip_block_copy);
      // This code is for disjoint source and destination:
      //   to <= from || to >= from+count
      // but BIS will stomp over 'from' if (to > from-tail_size && to <= from)
      __ sub(from, to, O4);
      __ srax(O4, 4, O4); // divide by 16 since following short branch have only 5 bits for imm.
      __ cmp_and_br_short(O4, (tail_size>>4), Assembler::lessEqualUnsigned, Assembler::pn, L_skip_block_copy);

      __ wrasi(G0, Assembler::ASI_ST_BLKINIT_PRIMARY);
      // BIS should not be used to copy tail (64 bytes+iter_size)
      // to avoid zeroing of following values.
      __ sub(count, (tail_size>>log2_elem_size), count); // count is still positive >= 0

      if (prefetch_count > 0) { // rounded up to one iteration count
        // Do prefetching only if copy size is bigger
        // than prefetch distance.
        __ set(prefetch_count, O4);
        __ cmp_and_brx_short(count, O4, Assembler::less, Assembler::pt, L_block_copy);
        __ sub(count, prefetch_count, count);

        (this->*copy_loop_func)(from, to, count, count_dec, L_block_copy_prefetch, true, true);
        __ add(count, prefetch_count, count); // restore count

      } // prefetch_count > 0

      (this->*copy_loop_func)(from, to, count, count_dec, L_block_copy, false, true);
      __ add(count, (tail_size>>log2_elem_size), count); // restore count

      __ wrasi(G0, Assembler::ASI_PRIMARY_NOFAULT);
      // BIS needs membar.
      __ membar(Assembler::StoreLoad);
      // Copy tail
      __ ba_short(L_copy);

      __ BIND(L_skip_block_copy);
    } // UseBlockCopy

    if (prefetch_count > 0) { // rounded up to one iteration count
      // Do prefetching only if copy size is bigger
      // than prefetch distance.
      __ set(prefetch_count, O4);
      __ cmp_and_brx_short(count, O4, Assembler::lessUnsigned, Assembler::pt, L_copy);
      __ sub(count, prefetch_count, count);

      Label L_copy_prefetch;
      (this->*copy_loop_func)(from, to, count, count_dec, L_copy_prefetch, true, false);
      __ add(count, prefetch_count, count); // restore count

    } // prefetch_count > 0

    (this->*copy_loop_func)(from, to, count, count_dec, L_copy, false, false);
  }



  //
  // Helper methods for copy_16_bytes_forward_with_shift()
  //
  void copy_16_bytes_shift_loop(Register from, Register to, Register count, int count_dec,
                                Label& L_loop, bool use_prefetch, bool use_bis) {

    const Register left_shift  = G1; // left  shift bit counter
    const Register right_shift = G5; // right shift bit counter

    __ align(OptoLoopAlignment);
    __ BIND(L_loop);
    if (use_prefetch) {
      if (ArraycopySrcPrefetchDistance > 0) {
        __ prefetch(from, ArraycopySrcPrefetchDistance, Assembler::severalReads);
      }
      if (ArraycopyDstPrefetchDistance > 0) {
        __ prefetch(to, ArraycopyDstPrefetchDistance, Assembler::severalWritesAndPossiblyReads);
      }
    }
    __ ldx(from, 0, O4);
    __ ldx(from, 8, G4);
    __ inc(to, 16);
    __ inc(from, 16);
    __ deccc(count, count_dec); // Can we do next iteration after this one?
    __ srlx(O4, right_shift, G3);
    __ bset(G3, O3);
    __ sllx(O4, left_shift,  O4);
    __ srlx(G4, right_shift, G3);
    __ bset(G3, O4);
    if (use_bis) {
      __ stxa(O3, to, -16);
      __ stxa(O4, to, -8);
    } else {
      __ stx(O3, to, -16);
      __ stx(O4, to, -8);
    }
    __ brx(Assembler::greaterEqual, false, Assembler::pt, L_loop);
    __ delayed()->sllx(G4, left_shift,  O3);
  }

  // Copy big chunks forward with shift
  //
  // Inputs:
  //   from      - source arrays
  //   to        - destination array aligned to 8-bytes
  //   count     - elements count to copy >= the count equivalent to 16 bytes
  //   count_dec - elements count's decrement equivalent to 16 bytes
  //   L_copy_bytes - copy exit label
  //
  void copy_16_bytes_forward_with_shift(Register from, Register to,
                     Register count, int log2_elem_size, Label& L_copy_bytes) {
    Label L_aligned_copy, L_copy_last_bytes;
    assert(log2_elem_size <= 3, "the following code should be changed");
    int count_dec = 16>>log2_elem_size;

    // if both arrays have the same alignment mod 8, do 8 bytes aligned copy
    __ andcc(from, 7, G1); // misaligned bytes
    __ br(Assembler::zero, false, Assembler::pt, L_aligned_copy);
    __ delayed()->nop();

    const Register left_shift  = G1; // left  shift bit counter
    const Register right_shift = G5; // right shift bit counter

    __ sll(G1, LogBitsPerByte, left_shift);
    __ mov(64, right_shift);
    __ sub(right_shift, left_shift, right_shift);

    //
    // Load 2 aligned 8-bytes chunks and use one from previous iteration
    // to form 2 aligned 8-bytes chunks to store.
    //
    __ dec(count, count_dec);   // Pre-decrement 'count'
    __ andn(from, 7, from);     // Align address
    __ ldx(from, 0, O3);
    __ inc(from, 8);
    __ sllx(O3, left_shift,  O3);

    disjoint_copy_core(from, to, count, log2_elem_size, 16, copy_16_bytes_shift_loop);

    __ inccc(count, count_dec>>1 ); // + 8 bytes
    __ brx(Assembler::negative, true, Assembler::pn, L_copy_last_bytes);
    __ delayed()->inc(count, count_dec>>1); // restore 'count'

    // copy 8 bytes, part of them already loaded in O3
    __ ldx(from, 0, O4);
    __ inc(to, 8);
    __ inc(from, 8);
    __ srlx(O4, right_shift, G3);
    __ bset(O3, G3);
    __ stx(G3, to, -8);

    __ BIND(L_copy_last_bytes);
    __ srl(right_shift, LogBitsPerByte, right_shift); // misaligned bytes
    __ br(Assembler::always, false, Assembler::pt, L_copy_bytes);
    __ delayed()->sub(from, right_shift, from);       // restore address

    __ BIND(L_aligned_copy);
  }

  // Copy big chunks backward with shift
  //
  // Inputs:
  //   end_from  - source arrays end address
  //   end_to    - destination array end address aligned to 8-bytes
  //   count     - elements count to copy >= the count equivalent to 16 bytes
  //   count_dec - elements count's decrement equivalent to 16 bytes
  //   L_aligned_copy - aligned copy exit label
  //   L_copy_bytes   - copy exit label
  //
  void copy_16_bytes_backward_with_shift(Register end_from, Register end_to,
                     Register count, int count_dec,
                     Label& L_aligned_copy, Label& L_copy_bytes) {
    Label L_loop, L_copy_last_bytes;

    // if both arrays have the same alignment mod 8, do 8 bytes aligned copy
      __ andcc(end_from, 7, G1); // misaligned bytes
      __ br(Assembler::zero, false, Assembler::pt, L_aligned_copy);
      __ delayed()->deccc(count, count_dec); // Pre-decrement 'count'

    const Register left_shift  = G1; // left  shift bit counter
    const Register right_shift = G5; // right shift bit counter

      __ sll(G1, LogBitsPerByte, left_shift);
      __ mov(64, right_shift);
      __ sub(right_shift, left_shift, right_shift);

    //
    // Load 2 aligned 8-bytes chunks and use one from previous iteration
    // to form 2 aligned 8-bytes chunks to store.
    //
      __ andn(end_from, 7, end_from);     // Align address
      __ ldx(end_from, 0, O3);
      __ align(OptoLoopAlignment);
    __ BIND(L_loop);
      __ ldx(end_from, -8, O4);
      __ deccc(count, count_dec); // Can we do next iteration after this one?
      __ ldx(end_from, -16, G4);
      __ dec(end_to, 16);
      __ dec(end_from, 16);
      __ srlx(O3, right_shift, O3);
      __ sllx(O4, left_shift,  G3);
      __ bset(G3, O3);
      __ stx(O3, end_to, 8);
      __ srlx(O4, right_shift, O4);
      __ sllx(G4, left_shift,  G3);
      __ bset(G3, O4);
      __ stx(O4, end_to, 0);
      __ brx(Assembler::greaterEqual, false, Assembler::pt, L_loop);
      __ delayed()->mov(G4, O3);

      __ inccc(count, count_dec>>1 ); // + 8 bytes
      __ brx(Assembler::negative, true, Assembler::pn, L_copy_last_bytes);
      __ delayed()->inc(count, count_dec>>1); // restore 'count'

      // copy 8 bytes, part of them already loaded in O3
      __ ldx(end_from, -8, O4);
      __ dec(end_to, 8);
      __ dec(end_from, 8);
      __ srlx(O3, right_shift, O3);
      __ sllx(O4, left_shift,  G3);
      __ bset(O3, G3);
      __ stx(G3, end_to, 0);

    __ BIND(L_copy_last_bytes);
      __ srl(left_shift, LogBitsPerByte, left_shift);    // misaligned bytes
      __ br(Assembler::always, false, Assembler::pt, L_copy_bytes);
      __ delayed()->add(end_from, left_shift, end_from); // restore address
  }

  //
  //  Generate stub for disjoint byte copy.  If "aligned" is true, the
  //  "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_disjoint_byte_copy(bool aligned, address *entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_skip_alignment, L_align;
    Label L_copy_byte, L_copy_byte_loop, L_exit;

    const Register from      = O0;   // source array address
    const Register to        = O1;   // destination array address
    const Register count     = O2;   // elements count
    const Register offset    = O5;   // offset from start of arrays
    // O3, O4, G3, G4 are used as temp registers

    assert_clean_int(count, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    // for short arrays, just do single element copy
    __ cmp(count, 23); // 16 + 7
    __ brx(Assembler::less, false, Assembler::pn, L_copy_byte);
    __ delayed()->mov(G0, offset);

    if (aligned) {
      // 'aligned' == true when it is known statically during compilation
      // of this arraycopy call site that both 'from' and 'to' addresses
      // are HeapWordSize aligned (see LibraryCallKit::basictype2arraycopy()).
      //
      // Aligned arrays have 4 bytes alignment in 32-bits VM
      // and 8 bytes - in 64-bits VM. So we do it only for 32-bits VM
      //
#ifndef _LP64
      // copy a 4-bytes word if necessary to align 'to' to 8 bytes
      __ andcc(to, 7, G0);
      __ br(Assembler::zero, false, Assembler::pn, L_skip_alignment);
      __ delayed()->ld(from, 0, O3);
      __ inc(from, 4);
      __ inc(to, 4);
      __ dec(count, 4);
      __ st(O3, to, -4);
    __ BIND(L_skip_alignment);
#endif
    } else {
      // copy bytes to align 'to' on 8 byte boundary
      __ andcc(to, 7, G1); // misaligned bytes
      __ br(Assembler::zero, false, Assembler::pt, L_skip_alignment);
      __ delayed()->neg(G1);
      __ inc(G1, 8);       // bytes need to copy to next 8-bytes alignment
      __ sub(count, G1, count);
    __ BIND(L_align);
      __ ldub(from, 0, O3);
      __ deccc(G1);
      __ inc(from);
      __ stb(O3, to, 0);
      __ br(Assembler::notZero, false, Assembler::pt, L_align);
      __ delayed()->inc(to);
    __ BIND(L_skip_alignment);
    }
#ifdef _LP64
    if (!aligned)
#endif
    {
      // Copy with shift 16 bytes per iteration if arrays do not have
      // the same alignment mod 8, otherwise fall through to the next
      // code for aligned copy.
      // The compare above (count >= 23) guarantes 'count' >= 16 bytes.
      // Also jump over aligned copy after the copy with shift completed.

      copy_16_bytes_forward_with_shift(from, to, count, 0, L_copy_byte);
    }

    // Both array are 8 bytes aligned, copy 16 bytes at a time
      __ and3(count, 7, G4); // Save count
      __ srl(count, 3, count);
     generate_disjoint_long_copy_core(aligned);
      __ mov(G4, count);     // Restore count

    // copy tailing bytes
    __ BIND(L_copy_byte);
      __ cmp_and_br_short(count, 0, Assembler::equal, Assembler::pt, L_exit);
      __ align(OptoLoopAlignment);
    __ BIND(L_copy_byte_loop);
      __ ldub(from, offset, O3);
      __ deccc(count);
      __ stb(O3, to, offset);
      __ brx(Assembler::notZero, false, Assembler::pt, L_copy_byte_loop);
      __ delayed()->inc(offset);

    __ BIND(L_exit);
      // O3, O4 are used as temp registers
      inc_counter_np(SharedRuntime::_jbyte_array_copy_ctr, O3, O4);
      __ retl();
      __ delayed()->mov(G0, O0); // return 0
    return start;
  }

  //
  //  Generate stub for conjoint byte copy.  If "aligned" is true, the
  //  "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_conjoint_byte_copy(bool aligned, address nooverlap_target,
                                      address *entry, const char *name) {
    // Do reverse copy.

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_skip_alignment, L_align, L_aligned_copy;
    Label L_copy_byte, L_copy_byte_loop, L_exit;

    const Register from      = O0;   // source array address
    const Register to        = O1;   // destination array address
    const Register count     = O2;   // elements count
    const Register end_from  = from; // source array end address
    const Register end_to    = to;   // destination array end address

    assert_clean_int(count, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    array_overlap_test(nooverlap_target, 0);

    __ add(to, count, end_to);       // offset after last copied element

    // for short arrays, just do single element copy
    __ cmp(count, 23); // 16 + 7
    __ brx(Assembler::less, false, Assembler::pn, L_copy_byte);
    __ delayed()->add(from, count, end_from);

    {
      // Align end of arrays since they could be not aligned even
      // when arrays itself are aligned.

      // copy bytes to align 'end_to' on 8 byte boundary
      __ andcc(end_to, 7, G1); // misaligned bytes
      __ br(Assembler::zero, false, Assembler::pt, L_skip_alignment);
      __ delayed()->nop();
      __ sub(count, G1, count);
    __ BIND(L_align);
      __ dec(end_from);
      __ dec(end_to);
      __ ldub(end_from, 0, O3);
      __ deccc(G1);
      __ brx(Assembler::notZero, false, Assembler::pt, L_align);
      __ delayed()->stb(O3, end_to, 0);
    __ BIND(L_skip_alignment);
    }
#ifdef _LP64
    if (aligned) {
      // Both arrays are aligned to 8-bytes in 64-bits VM.
      // The 'count' is decremented in copy_16_bytes_backward_with_shift()
      // in unaligned case.
      __ dec(count, 16);
    } else
#endif
    {
      // Copy with shift 16 bytes per iteration if arrays do not have
      // the same alignment mod 8, otherwise jump to the next
      // code for aligned copy (and substracting 16 from 'count' before jump).
      // The compare above (count >= 11) guarantes 'count' >= 16 bytes.
      // Also jump over aligned copy after the copy with shift completed.

      copy_16_bytes_backward_with_shift(end_from, end_to, count, 16,
                                        L_aligned_copy, L_copy_byte);
    }
    // copy 4 elements (16 bytes) at a time
      __ align(OptoLoopAlignment);
    __ BIND(L_aligned_copy);
      __ dec(end_from, 16);
      __ ldx(end_from, 8, O3);
      __ ldx(end_from, 0, O4);
      __ dec(end_to, 16);
      __ deccc(count, 16);
      __ stx(O3, end_to, 8);
      __ brx(Assembler::greaterEqual, false, Assembler::pt, L_aligned_copy);
      __ delayed()->stx(O4, end_to, 0);
      __ inc(count, 16);

    // copy 1 element (2 bytes) at a time
    __ BIND(L_copy_byte);
      __ cmp_and_br_short(count, 0, Assembler::equal, Assembler::pt, L_exit);
      __ align(OptoLoopAlignment);
    __ BIND(L_copy_byte_loop);
      __ dec(end_from);
      __ dec(end_to);
      __ ldub(end_from, 0, O4);
      __ deccc(count);
      __ brx(Assembler::greater, false, Assembler::pt, L_copy_byte_loop);
      __ delayed()->stb(O4, end_to, 0);

    __ BIND(L_exit);
    // O3, O4 are used as temp registers
    inc_counter_np(SharedRuntime::_jbyte_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->mov(G0, O0); // return 0
    return start;
  }

  //
  //  Generate stub for disjoint short copy.  If "aligned" is true, the
  //  "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_disjoint_short_copy(bool aligned, address *entry, const char * name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_skip_alignment, L_skip_alignment2;
    Label L_copy_2_bytes, L_copy_2_bytes_loop, L_exit;

    const Register from      = O0;   // source array address
    const Register to        = O1;   // destination array address
    const Register count     = O2;   // elements count
    const Register offset    = O5;   // offset from start of arrays
    // O3, O4, G3, G4 are used as temp registers

    assert_clean_int(count, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    // for short arrays, just do single element copy
    __ cmp(count, 11); // 8 + 3  (22 bytes)
    __ brx(Assembler::less, false, Assembler::pn, L_copy_2_bytes);
    __ delayed()->mov(G0, offset);

    if (aligned) {
      // 'aligned' == true when it is known statically during compilation
      // of this arraycopy call site that both 'from' and 'to' addresses
      // are HeapWordSize aligned (see LibraryCallKit::basictype2arraycopy()).
      //
      // Aligned arrays have 4 bytes alignment in 32-bits VM
      // and 8 bytes - in 64-bits VM.
      //
#ifndef _LP64
      // copy a 2-elements word if necessary to align 'to' to 8 bytes
      __ andcc(to, 7, G0);
      __ br(Assembler::zero, false, Assembler::pt, L_skip_alignment);
      __ delayed()->ld(from, 0, O3);
      __ inc(from, 4);
      __ inc(to, 4);
      __ dec(count, 2);
      __ st(O3, to, -4);
    __ BIND(L_skip_alignment);
#endif
    } else {
      // copy 1 element if necessary to align 'to' on an 4 bytes
      __ andcc(to, 3, G0);
      __ br(Assembler::zero, false, Assembler::pt, L_skip_alignment);
      __ delayed()->lduh(from, 0, O3);
      __ inc(from, 2);
      __ inc(to, 2);
      __ dec(count);
      __ sth(O3, to, -2);
    __ BIND(L_skip_alignment);

      // copy 2 elements to align 'to' on an 8 byte boundary
      __ andcc(to, 7, G0);
      __ br(Assembler::zero, false, Assembler::pn, L_skip_alignment2);
      __ delayed()->lduh(from, 0, O3);
      __ dec(count, 2);
      __ lduh(from, 2, O4);
      __ inc(from, 4);
      __ inc(to, 4);
      __ sth(O3, to, -4);
      __ sth(O4, to, -2);
    __ BIND(L_skip_alignment2);
    }
#ifdef _LP64
    if (!aligned)
#endif
    {
      // Copy with shift 16 bytes per iteration if arrays do not have
      // the same alignment mod 8, otherwise fall through to the next
      // code for aligned copy.
      // The compare above (count >= 11) guarantes 'count' >= 16 bytes.
      // Also jump over aligned copy after the copy with shift completed.

      copy_16_bytes_forward_with_shift(from, to, count, 1, L_copy_2_bytes);
    }

    // Both array are 8 bytes aligned, copy 16 bytes at a time
      __ and3(count, 3, G4); // Save
      __ srl(count, 2, count);
     generate_disjoint_long_copy_core(aligned);
      __ mov(G4, count); // restore

    // copy 1 element at a time
    __ BIND(L_copy_2_bytes);
      __ cmp_and_br_short(count, 0, Assembler::equal, Assembler::pt, L_exit);
      __ align(OptoLoopAlignment);
    __ BIND(L_copy_2_bytes_loop);
      __ lduh(from, offset, O3);
      __ deccc(count);
      __ sth(O3, to, offset);
      __ brx(Assembler::notZero, false, Assembler::pt, L_copy_2_bytes_loop);
      __ delayed()->inc(offset, 2);

    __ BIND(L_exit);
      // O3, O4 are used as temp registers
      inc_counter_np(SharedRuntime::_jshort_array_copy_ctr, O3, O4);
      __ retl();
      __ delayed()->mov(G0, O0); // return 0
    return start;
  }

  //
  //  Generate stub for disjoint short fill.  If "aligned" is true, the
  //  "to" address is assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      to:    O0
  //      value: O1
  //      count: O2 treated as signed
  //
  address generate_fill(BasicType t, bool aligned, const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    const Register to        = O0;   // source array address
    const Register value     = O1;   // fill value
    const Register count     = O2;   // elements count
    // O3 is used as a temp register

    assert_clean_int(count, O3);     // Make sure 'count' is clean int.

    Label L_exit, L_skip_align1, L_skip_align2, L_fill_byte;
    Label L_fill_2_bytes, L_fill_elements, L_fill_32_bytes;

    int shift = -1;
    switch (t) {
       case T_BYTE:
        shift = 2;
        break;
       case T_SHORT:
        shift = 1;
        break;
      case T_INT:
         shift = 0;
        break;
      default: ShouldNotReachHere();
    }

    BLOCK_COMMENT("Entry:");

    if (t == T_BYTE) {
      // Zero extend value
      __ and3(value, 0xff, value);
      __ sllx(value, 8, O3);
      __ or3(value, O3, value);
    }
    if (t == T_SHORT) {
      // Zero extend value
      __ sllx(value, 48, value);
      __ srlx(value, 48, value);
    }
    if (t == T_BYTE || t == T_SHORT) {
      __ sllx(value, 16, O3);
      __ or3(value, O3, value);
    }

    __ cmp(count, 2<<shift); // Short arrays (< 8 bytes) fill by element
    __ brx(Assembler::lessUnsigned, false, Assembler::pn, L_fill_elements); // use unsigned cmp
    __ delayed()->andcc(count, 1, G0);

    if (!aligned && (t == T_BYTE || t == T_SHORT)) {
      // align source address at 4 bytes address boundary
      if (t == T_BYTE) {
        // One byte misalignment happens only for byte arrays
        __ andcc(to, 1, G0);
        __ br(Assembler::zero, false, Assembler::pt, L_skip_align1);
        __ delayed()->nop();
        __ stb(value, to, 0);
        __ inc(to, 1);
        __ dec(count, 1);
        __ BIND(L_skip_align1);
      }
      // Two bytes misalignment happens only for byte and short (char) arrays
      __ andcc(to, 2, G0);
      __ br(Assembler::zero, false, Assembler::pt, L_skip_align2);
      __ delayed()->nop();
      __ sth(value, to, 0);
      __ inc(to, 2);
      __ dec(count, 1 << (shift - 1));
      __ BIND(L_skip_align2);
    }
#ifdef _LP64
    if (!aligned) {
#endif
    // align to 8 bytes, we know we are 4 byte aligned to start
    __ andcc(to, 7, G0);
    __ br(Assembler::zero, false, Assembler::pt, L_fill_32_bytes);
    __ delayed()->nop();
    __ stw(value, to, 0);
    __ inc(to, 4);
    __ dec(count, 1 << shift);
    __ BIND(L_fill_32_bytes);
#ifdef _LP64
    }
#endif

    if (t == T_INT) {
      // Zero extend value
      __ srl(value, 0, value);
    }
    if (t == T_BYTE || t == T_SHORT || t == T_INT) {
      __ sllx(value, 32, O3);
      __ or3(value, O3, value);
    }

    Label L_check_fill_8_bytes;
    // Fill 32-byte chunks
    __ subcc(count, 8 << shift, count);
    __ brx(Assembler::less, false, Assembler::pt, L_check_fill_8_bytes);
    __ delayed()->nop();

    Label L_fill_32_bytes_loop, L_fill_4_bytes;
    __ align(16);
    __ BIND(L_fill_32_bytes_loop);

    __ stx(value, to, 0);
    __ stx(value, to, 8);
    __ stx(value, to, 16);
    __ stx(value, to, 24);

    __ subcc(count, 8 << shift, count);
    __ brx(Assembler::greaterEqual, false, Assembler::pt, L_fill_32_bytes_loop);
    __ delayed()->add(to, 32, to);

    __ BIND(L_check_fill_8_bytes);
    __ addcc(count, 8 << shift, count);
    __ brx(Assembler::zero, false, Assembler::pn, L_exit);
    __ delayed()->subcc(count, 1 << (shift + 1), count);
    __ brx(Assembler::less, false, Assembler::pn, L_fill_4_bytes);
    __ delayed()->andcc(count, 1<<shift, G0);

    //
    // length is too short, just fill 8 bytes at a time
    //
    Label L_fill_8_bytes_loop;
    __ BIND(L_fill_8_bytes_loop);
    __ stx(value, to, 0);
    __ subcc(count, 1 << (shift + 1), count);
    __ brx(Assembler::greaterEqual, false, Assembler::pn, L_fill_8_bytes_loop);
    __ delayed()->add(to, 8, to);

    // fill trailing 4 bytes
    __ andcc(count, 1<<shift, G0);  // in delay slot of branches
    if (t == T_INT) {
      __ BIND(L_fill_elements);
    }
    __ BIND(L_fill_4_bytes);
    __ brx(Assembler::zero, false, Assembler::pt, L_fill_2_bytes);
    if (t == T_BYTE || t == T_SHORT) {
      __ delayed()->andcc(count, 1<<(shift-1), G0);
    } else {
      __ delayed()->nop();
    }
    __ stw(value, to, 0);
    if (t == T_BYTE || t == T_SHORT) {
      __ inc(to, 4);
      // fill trailing 2 bytes
      __ andcc(count, 1<<(shift-1), G0); // in delay slot of branches
      __ BIND(L_fill_2_bytes);
      __ brx(Assembler::zero, false, Assembler::pt, L_fill_byte);
      __ delayed()->andcc(count, 1, count);
      __ sth(value, to, 0);
      if (t == T_BYTE) {
        __ inc(to, 2);
        // fill trailing byte
        __ andcc(count, 1, count);  // in delay slot of branches
        __ BIND(L_fill_byte);
        __ brx(Assembler::zero, false, Assembler::pt, L_exit);
        __ delayed()->nop();
        __ stb(value, to, 0);
      } else {
        __ BIND(L_fill_byte);
      }
    } else {
      __ BIND(L_fill_2_bytes);
    }
    __ BIND(L_exit);
    __ retl();
    __ delayed()->nop();

    // Handle copies less than 8 bytes.  Int is handled elsewhere.
    if (t == T_BYTE) {
      __ BIND(L_fill_elements);
      Label L_fill_2, L_fill_4;
      // in delay slot __ andcc(count, 1, G0);
      __ brx(Assembler::zero, false, Assembler::pt, L_fill_2);
      __ delayed()->andcc(count, 2, G0);
      __ stb(value, to, 0);
      __ inc(to, 1);
      __ BIND(L_fill_2);
      __ brx(Assembler::zero, false, Assembler::pt, L_fill_4);
      __ delayed()->andcc(count, 4, G0);
      __ stb(value, to, 0);
      __ stb(value, to, 1);
      __ inc(to, 2);
      __ BIND(L_fill_4);
      __ brx(Assembler::zero, false, Assembler::pt, L_exit);
      __ delayed()->nop();
      __ stb(value, to, 0);
      __ stb(value, to, 1);
      __ stb(value, to, 2);
      __ retl();
      __ delayed()->stb(value, to, 3);
    }

    if (t == T_SHORT) {
      Label L_fill_2;
      __ BIND(L_fill_elements);
      // in delay slot __ andcc(count, 1, G0);
      __ brx(Assembler::zero, false, Assembler::pt, L_fill_2);
      __ delayed()->andcc(count, 2, G0);
      __ sth(value, to, 0);
      __ inc(to, 2);
      __ BIND(L_fill_2);
      __ brx(Assembler::zero, false, Assembler::pt, L_exit);
      __ delayed()->nop();
      __ sth(value, to, 0);
      __ retl();
      __ delayed()->sth(value, to, 2);
    }
    return start;
  }

  //
  //  Generate stub for conjoint short copy.  If "aligned" is true, the
  //  "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_conjoint_short_copy(bool aligned, address nooverlap_target,
                                       address *entry, const char *name) {
    // Do reverse copy.

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    Label L_skip_alignment, L_skip_alignment2, L_aligned_copy;
    Label L_copy_2_bytes, L_copy_2_bytes_loop, L_exit;

    const Register from      = O0;   // source array address
    const Register to        = O1;   // destination array address
    const Register count     = O2;   // elements count
    const Register end_from  = from; // source array end address
    const Register end_to    = to;   // destination array end address

    const Register byte_count = O3;  // bytes count to copy

    assert_clean_int(count, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    array_overlap_test(nooverlap_target, 1);

    __ sllx(count, LogBytesPerShort, byte_count);
    __ add(to, byte_count, end_to);  // offset after last copied element

    // for short arrays, just do single element copy
    __ cmp(count, 11); // 8 + 3  (22 bytes)
    __ brx(Assembler::less, false, Assembler::pn, L_copy_2_bytes);
    __ delayed()->add(from, byte_count, end_from);

    {
      // Align end of arrays since they could be not aligned even
      // when arrays itself are aligned.

      // copy 1 element if necessary to align 'end_to' on an 4 bytes
      __ andcc(end_to, 3, G0);
      __ br(Assembler::zero, false, Assembler::pt, L_skip_alignment);
      __ delayed()->lduh(end_from, -2, O3);
      __ dec(end_from, 2);
      __ dec(end_to, 2);
      __ dec(count);
      __ sth(O3, end_to, 0);
    __ BIND(L_skip_alignment);

      // copy 2 elements to align 'end_to' on an 8 byte boundary
      __ andcc(end_to, 7, G0);
      __ br(Assembler::zero, false, Assembler::pn, L_skip_alignment2);
      __ delayed()->lduh(end_from, -2, O3);
      __ dec(count, 2);
      __ lduh(end_from, -4, O4);
      __ dec(end_from, 4);
      __ dec(end_to, 4);
      __ sth(O3, end_to, 2);
      __ sth(O4, end_to, 0);
    __ BIND(L_skip_alignment2);
    }
#ifdef _LP64
    if (aligned) {
      // Both arrays are aligned to 8-bytes in 64-bits VM.
      // The 'count' is decremented in copy_16_bytes_backward_with_shift()
      // in unaligned case.
      __ dec(count, 8);
    } else
#endif
    {
      // Copy with shift 16 bytes per iteration if arrays do not have
      // the same alignment mod 8, otherwise jump to the next
      // code for aligned copy (and substracting 8 from 'count' before jump).
      // The compare above (count >= 11) guarantes 'count' >= 16 bytes.
      // Also jump over aligned copy after the copy with shift completed.

      copy_16_bytes_backward_with_shift(end_from, end_to, count, 8,
                                        L_aligned_copy, L_copy_2_bytes);
    }
    // copy 4 elements (16 bytes) at a time
      __ align(OptoLoopAlignment);
    __ BIND(L_aligned_copy);
      __ dec(end_from, 16);
      __ ldx(end_from, 8, O3);
      __ ldx(end_from, 0, O4);
      __ dec(end_to, 16);
      __ deccc(count, 8);
      __ stx(O3, end_to, 8);
      __ brx(Assembler::greaterEqual, false, Assembler::pt, L_aligned_copy);
      __ delayed()->stx(O4, end_to, 0);
      __ inc(count, 8);

    // copy 1 element (2 bytes) at a time
    __ BIND(L_copy_2_bytes);
      __ cmp_and_br_short(count, 0, Assembler::equal, Assembler::pt, L_exit);
    __ BIND(L_copy_2_bytes_loop);
      __ dec(end_from, 2);
      __ dec(end_to, 2);
      __ lduh(end_from, 0, O4);
      __ deccc(count);
      __ brx(Assembler::greater, false, Assembler::pt, L_copy_2_bytes_loop);
      __ delayed()->sth(O4, end_to, 0);

    __ BIND(L_exit);
    // O3, O4 are used as temp registers
    inc_counter_np(SharedRuntime::_jshort_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->mov(G0, O0); // return 0
    return start;
  }

  //
  // Helper methods for generate_disjoint_int_copy_core()
  //
  void copy_16_bytes_loop(Register from, Register to, Register count, int count_dec,
                          Label& L_loop, bool use_prefetch, bool use_bis) {

    __ align(OptoLoopAlignment);
    __ BIND(L_loop);
    if (use_prefetch) {
      if (ArraycopySrcPrefetchDistance > 0) {
        __ prefetch(from, ArraycopySrcPrefetchDistance, Assembler::severalReads);
      }
      if (ArraycopyDstPrefetchDistance > 0) {
        __ prefetch(to, ArraycopyDstPrefetchDistance, Assembler::severalWritesAndPossiblyReads);
      }
    }
    __ ldx(from, 4, O4);
    __ ldx(from, 12, G4);
    __ inc(to, 16);
    __ inc(from, 16);
    __ deccc(count, 4); // Can we do next iteration after this one?

    __ srlx(O4, 32, G3);
    __ bset(G3, O3);
    __ sllx(O4, 32, O4);
    __ srlx(G4, 32, G3);
    __ bset(G3, O4);
    if (use_bis) {
      __ stxa(O3, to, -16);
      __ stxa(O4, to, -8);
    } else {
      __ stx(O3, to, -16);
      __ stx(O4, to, -8);
    }
    __ brx(Assembler::greaterEqual, false, Assembler::pt, L_loop);
    __ delayed()->sllx(G4, 32,  O3);

  }

  //
  //  Generate core code for disjoint int copy (and oop copy on 32-bit).
  //  If "aligned" is true, the "from" and "to" addresses are assumed
  //  to be heapword aligned.
  //
  // Arguments:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  void generate_disjoint_int_copy_core(bool aligned) {

    Label L_skip_alignment, L_aligned_copy;
    Label L_copy_4_bytes, L_copy_4_bytes_loop, L_exit;

    const Register from      = O0;   // source array address
    const Register to        = O1;   // destination array address
    const Register count     = O2;   // elements count
    const Register offset    = O5;   // offset from start of arrays
    // O3, O4, G3, G4 are used as temp registers

    // 'aligned' == true when it is known statically during compilation
    // of this arraycopy call site that both 'from' and 'to' addresses
    // are HeapWordSize aligned (see LibraryCallKit::basictype2arraycopy()).
    //
    // Aligned arrays have 4 bytes alignment in 32-bits VM
    // and 8 bytes - in 64-bits VM.
    //
#ifdef _LP64
    if (!aligned)
#endif
    {
      // The next check could be put under 'ifndef' since the code in
      // generate_disjoint_long_copy_core() has own checks and set 'offset'.

      // for short arrays, just do single element copy
      __ cmp(count, 5); // 4 + 1 (20 bytes)
      __ brx(Assembler::lessEqual, false, Assembler::pn, L_copy_4_bytes);
      __ delayed()->mov(G0, offset);

      // copy 1 element to align 'to' on an 8 byte boundary
      __ andcc(to, 7, G0);
      __ br(Assembler::zero, false, Assembler::pt, L_skip_alignment);
      __ delayed()->ld(from, 0, O3);
      __ inc(from, 4);
      __ inc(to, 4);
      __ dec(count);
      __ st(O3, to, -4);
    __ BIND(L_skip_alignment);

    // if arrays have same alignment mod 8, do 4 elements copy
      __ andcc(from, 7, G0);
      __ br(Assembler::zero, false, Assembler::pt, L_aligned_copy);
      __ delayed()->ld(from, 0, O3);

    //
    // Load 2 aligned 8-bytes chunks and use one from previous iteration
    // to form 2 aligned 8-bytes chunks to store.
    //
    // copy_16_bytes_forward_with_shift() is not used here since this
    // code is more optimal.

    // copy with shift 4 elements (16 bytes) at a time
      __ dec(count, 4);   // The cmp at the beginning guaranty count >= 4
      __ sllx(O3, 32,  O3);

      disjoint_copy_core(from, to, count, 2, 16, copy_16_bytes_loop);

      __ br(Assembler::always, false, Assembler::pt, L_copy_4_bytes);
      __ delayed()->inc(count, 4); // restore 'count'

    __ BIND(L_aligned_copy);
    } // !aligned

    // copy 4 elements (16 bytes) at a time
      __ and3(count, 1, G4); // Save
      __ srl(count, 1, count);
     generate_disjoint_long_copy_core(aligned);
      __ mov(G4, count);     // Restore

    // copy 1 element at a time
    __ BIND(L_copy_4_bytes);
      __ cmp_and_br_short(count, 0, Assembler::equal, Assembler::pt, L_exit);
    __ BIND(L_copy_4_bytes_loop);
      __ ld(from, offset, O3);
      __ deccc(count);
      __ st(O3, to, offset);
      __ brx(Assembler::notZero, false, Assembler::pt, L_copy_4_bytes_loop);
      __ delayed()->inc(offset, 4);
    __ BIND(L_exit);
  }

  //
  //  Generate stub for disjoint int copy.  If "aligned" is true, the
  //  "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_disjoint_int_copy(bool aligned, address *entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    const Register count = O2;
    assert_clean_int(count, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    generate_disjoint_int_copy_core(aligned);

    // O3, O4 are used as temp registers
    inc_counter_np(SharedRuntime::_jint_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->mov(G0, O0); // return 0
    return start;
  }

  //
  //  Generate core code for conjoint int copy (and oop copy on 32-bit).
  //  If "aligned" is true, the "from" and "to" addresses are assumed
  //  to be heapword aligned.
  //
  // Arguments:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  void generate_conjoint_int_copy_core(bool aligned) {
    // Do reverse copy.

    Label L_skip_alignment, L_aligned_copy;
    Label L_copy_16_bytes,  L_copy_4_bytes, L_copy_4_bytes_loop, L_exit;

    const Register from      = O0;   // source array address
    const Register to        = O1;   // destination array address
    const Register count     = O2;   // elements count
    const Register end_from  = from; // source array end address
    const Register end_to    = to;   // destination array end address
    // O3, O4, O5, G3 are used as temp registers

    const Register byte_count = O3;  // bytes count to copy

      __ sllx(count, LogBytesPerInt, byte_count);
      __ add(to, byte_count, end_to); // offset after last copied element

      __ cmp(count, 5); // for short arrays, just do single element copy
      __ brx(Assembler::lessEqual, false, Assembler::pn, L_copy_4_bytes);
      __ delayed()->add(from, byte_count, end_from);

    // copy 1 element to align 'to' on an 8 byte boundary
      __ andcc(end_to, 7, G0);
      __ br(Assembler::zero, false, Assembler::pt, L_skip_alignment);
      __ delayed()->nop();
      __ dec(count);
      __ dec(end_from, 4);
      __ dec(end_to,   4);
      __ ld(end_from, 0, O4);
      __ st(O4, end_to, 0);
    __ BIND(L_skip_alignment);

    // Check if 'end_from' and 'end_to' has the same alignment.
      __ andcc(end_from, 7, G0);
      __ br(Assembler::zero, false, Assembler::pt, L_aligned_copy);
      __ delayed()->dec(count, 4); // The cmp at the start guaranty cnt >= 4

    // copy with shift 4 elements (16 bytes) at a time
    //
    // Load 2 aligned 8-bytes chunks and use one from previous iteration
    // to form 2 aligned 8-bytes chunks to store.
    //
      __ ldx(end_from, -4, O3);
      __ align(OptoLoopAlignment);
    __ BIND(L_copy_16_bytes);
      __ ldx(end_from, -12, O4);
      __ deccc(count, 4);
      __ ldx(end_from, -20, O5);
      __ dec(end_to, 16);
      __ dec(end_from, 16);
      __ srlx(O3, 32, O3);
      __ sllx(O4, 32, G3);
      __ bset(G3, O3);
      __ stx(O3, end_to, 8);
      __ srlx(O4, 32, O4);
      __ sllx(O5, 32, G3);
      __ bset(O4, G3);
      __ stx(G3, end_to, 0);
      __ brx(Assembler::greaterEqual, false, Assembler::pt, L_copy_16_bytes);
      __ delayed()->mov(O5, O3);

      __ br(Assembler::always, false, Assembler::pt, L_copy_4_bytes);
      __ delayed()->inc(count, 4);

    // copy 4 elements (16 bytes) at a time
      __ align(OptoLoopAlignment);
    __ BIND(L_aligned_copy);
      __ dec(end_from, 16);
      __ ldx(end_from, 8, O3);
      __ ldx(end_from, 0, O4);
      __ dec(end_to, 16);
      __ deccc(count, 4);
      __ stx(O3, end_to, 8);
      __ brx(Assembler::greaterEqual, false, Assembler::pt, L_aligned_copy);
      __ delayed()->stx(O4, end_to, 0);
      __ inc(count, 4);

    // copy 1 element (4 bytes) at a time
    __ BIND(L_copy_4_bytes);
      __ cmp_and_br_short(count, 0, Assembler::equal, Assembler::pt, L_exit);
    __ BIND(L_copy_4_bytes_loop);
      __ dec(end_from, 4);
      __ dec(end_to, 4);
      __ ld(end_from, 0, O4);
      __ deccc(count);
      __ brx(Assembler::greater, false, Assembler::pt, L_copy_4_bytes_loop);
      __ delayed()->st(O4, end_to, 0);
    __ BIND(L_exit);
  }

  //
  //  Generate stub for conjoint int copy.  If "aligned" is true, the
  //  "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_conjoint_int_copy(bool aligned, address nooverlap_target,
                                     address *entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    assert_clean_int(O2, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    array_overlap_test(nooverlap_target, 2);

    generate_conjoint_int_copy_core(aligned);

    // O3, O4 are used as temp registers
    inc_counter_np(SharedRuntime::_jint_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->mov(G0, O0); // return 0
    return start;
  }

  //
  // Helper methods for generate_disjoint_long_copy_core()
  //
  void copy_64_bytes_loop(Register from, Register to, Register count, int count_dec,
                          Label& L_loop, bool use_prefetch, bool use_bis) {
    __ align(OptoLoopAlignment);
    __ BIND(L_loop);
    for (int off = 0; off < 64; off += 16) {
      if (use_prefetch && (off & 31) == 0) {
        if (ArraycopySrcPrefetchDistance > 0) {
          __ prefetch(from, ArraycopySrcPrefetchDistance+off, Assembler::severalReads);
        }
        if (ArraycopyDstPrefetchDistance > 0) {
          __ prefetch(to, ArraycopyDstPrefetchDistance+off, Assembler::severalWritesAndPossiblyReads);
        }
      }
      __ ldx(from,  off+0, O4);
      __ ldx(from,  off+8, O5);
      if (use_bis) {
        __ stxa(O4, to,  off+0);
        __ stxa(O5, to,  off+8);
      } else {
        __ stx(O4, to,  off+0);
        __ stx(O5, to,  off+8);
      }
    }
    __ deccc(count, 8);
    __ inc(from, 64);
    __ brx(Assembler::greaterEqual, false, Assembler::pt, L_loop);
    __ delayed()->inc(to, 64);
  }

  //
  //  Generate core code for disjoint long copy (and oop copy on 64-bit).
  //  "aligned" is ignored, because we must make the stronger
  //  assumption that both addresses are always 64-bit aligned.
  //
  // Arguments:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  // count -= 2;
  // if ( count >= 0 ) { // >= 2 elements
  //   if ( count > 6) { // >= 8 elements
  //     count -= 6; // original count - 8
  //     do {
  //       copy_8_elements;
  //       count -= 8;
  //     } while ( count >= 0 );
  //     count += 6;
  //   }
  //   if ( count >= 0 ) { // >= 2 elements
  //     do {
  //       copy_2_elements;
  //     } while ( (count=count-2) >= 0 );
  //   }
  // }
  // count += 2;
  // if ( count != 0 ) { // 1 element left
  //   copy_1_element;
  // }
  //
  void generate_disjoint_long_copy_core(bool aligned) {
    Label L_copy_8_bytes, L_copy_16_bytes, L_exit;
    const Register from    = O0;  // source array address
    const Register to      = O1;  // destination array address
    const Register count   = O2;  // elements count
    const Register offset0 = O4;  // element offset
    const Register offset8 = O5;  // next element offset

    __ deccc(count, 2);
    __ mov(G0, offset0);   // offset from start of arrays (0)
    __ brx(Assembler::negative, false, Assembler::pn, L_copy_8_bytes );
    __ delayed()->add(offset0, 8, offset8);

    // Copy by 64 bytes chunks

    const Register from64 = O3;  // source address
    const Register to64   = G3;  // destination address
    __ subcc(count, 6, O3);
    __ brx(Assembler::negative, false, Assembler::pt, L_copy_16_bytes );
    __ delayed()->mov(to,   to64);
    // Now we can use O4(offset0), O5(offset8) as temps
    __ mov(O3, count);
    // count >= 0 (original count - 8)
    __ mov(from, from64);

    disjoint_copy_core(from64, to64, count, 3, 64, copy_64_bytes_loop);

      // Restore O4(offset0), O5(offset8)
      __ sub(from64, from, offset0);
      __ inccc(count, 6); // restore count
      __ brx(Assembler::negative, false, Assembler::pn, L_copy_8_bytes );
      __ delayed()->add(offset0, 8, offset8);

      // Copy by 16 bytes chunks
      __ align(OptoLoopAlignment);
    __ BIND(L_copy_16_bytes);
      __ ldx(from, offset0, O3);
      __ ldx(from, offset8, G3);
      __ deccc(count, 2);
      __ stx(O3, to, offset0);
      __ inc(offset0, 16);
      __ stx(G3, to, offset8);
      __ brx(Assembler::greaterEqual, false, Assembler::pt, L_copy_16_bytes);
      __ delayed()->inc(offset8, 16);

      // Copy last 8 bytes
    __ BIND(L_copy_8_bytes);
      __ inccc(count, 2);
      __ brx(Assembler::zero, true, Assembler::pn, L_exit );
      __ delayed()->mov(offset0, offset8); // Set O5 used by other stubs
      __ ldx(from, offset0, O3);
      __ stx(O3, to, offset0);
    __ BIND(L_exit);
  }

  //
  //  Generate stub for disjoint long copy.
  //  "aligned" is ignored, because we must make the stronger
  //  assumption that both addresses are always 64-bit aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_disjoint_long_copy(bool aligned, address *entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    assert_clean_int(O2, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    generate_disjoint_long_copy_core(aligned);

    // O3, O4 are used as temp registers
    inc_counter_np(SharedRuntime::_jlong_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->mov(G0, O0); // return 0
    return start;
  }

  //
  //  Generate core code for conjoint long copy (and oop copy on 64-bit).
  //  "aligned" is ignored, because we must make the stronger
  //  assumption that both addresses are always 64-bit aligned.
  //
  // Arguments:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  void generate_conjoint_long_copy_core(bool aligned) {
    // Do reverse copy.
    Label L_copy_8_bytes, L_copy_16_bytes, L_exit;
    const Register from    = O0;  // source array address
    const Register to      = O1;  // destination array address
    const Register count   = O2;  // elements count
    const Register offset8 = O4;  // element offset
    const Register offset0 = O5;  // previous element offset

      __ subcc(count, 1, count);
      __ brx(Assembler::lessEqual, false, Assembler::pn, L_copy_8_bytes );
      __ delayed()->sllx(count, LogBytesPerLong, offset8);
      __ sub(offset8, 8, offset0);
      __ align(OptoLoopAlignment);
    __ BIND(L_copy_16_bytes);
      __ ldx(from, offset8, O2);
      __ ldx(from, offset0, O3);
      __ stx(O2, to, offset8);
      __ deccc(offset8, 16);      // use offset8 as counter
      __ stx(O3, to, offset0);
      __ brx(Assembler::greater, false, Assembler::pt, L_copy_16_bytes);
      __ delayed()->dec(offset0, 16);

    __ BIND(L_copy_8_bytes);
      __ brx(Assembler::negative, false, Assembler::pn, L_exit );
      __ delayed()->nop();
      __ ldx(from, 0, O3);
      __ stx(O3, to, 0);
    __ BIND(L_exit);
  }

  //  Generate stub for conjoint long copy.
  //  "aligned" is ignored, because we must make the stronger
  //  assumption that both addresses are always 64-bit aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_conjoint_long_copy(bool aligned, address nooverlap_target,
                                      address *entry, const char *name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    assert(aligned, "Should always be aligned");

    assert_clean_int(O2, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
      BLOCK_COMMENT("Entry:");
    }

    array_overlap_test(nooverlap_target, 3);

    generate_conjoint_long_copy_core(aligned);

    // O3, O4 are used as temp registers
    inc_counter_np(SharedRuntime::_jlong_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->mov(G0, O0); // return 0
    return start;
  }

  //  Generate stub for disjoint oop copy.  If "aligned" is true, the
  //  "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_disjoint_oop_copy(bool aligned, address *entry, const char *name,
                                     bool dest_uninitialized = false) {

    const Register from  = O0;  // source array address
    const Register to    = O1;  // destination array address
    const Register count = O2;  // elements count

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    assert_clean_int(count, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here
      BLOCK_COMMENT("Entry:");
    }

    // save arguments for barrier generation
    __ mov(to, G1);
    __ mov(count, G5);
    gen_write_ref_array_pre_barrier(G1, G5, dest_uninitialized);
  #ifdef _LP64
    assert_clean_int(count, O3);     // Make sure 'count' is clean int.
    if (UseCompressedOops) {
      generate_disjoint_int_copy_core(aligned);
    } else {
      generate_disjoint_long_copy_core(aligned);
    }
  #else
    generate_disjoint_int_copy_core(aligned);
  #endif
    // O0 is used as temp register
    gen_write_ref_array_post_barrier(G1, G5, O0);

    // O3, O4 are used as temp registers
    inc_counter_np(SharedRuntime::_oop_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->mov(G0, O0); // return 0
    return start;
  }

  //  Generate stub for conjoint oop copy.  If "aligned" is true, the
  //  "from" and "to" addresses are assumed to be heapword aligned.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  address generate_conjoint_oop_copy(bool aligned, address nooverlap_target,
                                     address *entry, const char *name,
                                     bool dest_uninitialized = false) {

    const Register from  = O0;  // source array address
    const Register to    = O1;  // destination array address
    const Register count = O2;  // elements count

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    assert_clean_int(count, O3);     // Make sure 'count' is clean int.

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here
      BLOCK_COMMENT("Entry:");
    }

    array_overlap_test(nooverlap_target, LogBytesPerHeapOop);

    // save arguments for barrier generation
    __ mov(to, G1);
    __ mov(count, G5);
    gen_write_ref_array_pre_barrier(G1, G5, dest_uninitialized);

  #ifdef _LP64
    if (UseCompressedOops) {
      generate_conjoint_int_copy_core(aligned);
    } else {
      generate_conjoint_long_copy_core(aligned);
    }
  #else
    generate_conjoint_int_copy_core(aligned);
  #endif

    // O0 is used as temp register
    gen_write_ref_array_post_barrier(G1, G5, O0);

    // O3, O4 are used as temp registers
    inc_counter_np(SharedRuntime::_oop_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->mov(G0, O0); // return 0
    return start;
  }


  // Helper for generating a dynamic type check.
  // Smashes only the given temp registers.
  void generate_type_check(Register sub_klass,
                           Register super_check_offset,
                           Register super_klass,
                           Register temp,
                           Label& L_success) {
    assert_different_registers(sub_klass, super_check_offset, super_klass, temp);

    BLOCK_COMMENT("type_check:");

    Label L_miss, L_pop_to_miss;

    assert_clean_int(super_check_offset, temp);

    __ check_klass_subtype_fast_path(sub_klass, super_klass, temp, noreg,
                                     &L_success, &L_miss, NULL,
                                     super_check_offset);

    BLOCK_COMMENT("type_check_slow_path:");
    __ save_frame(0);
    __ check_klass_subtype_slow_path(sub_klass->after_save(),
                                     super_klass->after_save(),
                                     L0, L1, L2, L4,
                                     NULL, &L_pop_to_miss);
    __ ba(L_success);
    __ delayed()->restore();

    __ bind(L_pop_to_miss);
    __ restore();

    // Fall through on failure!
    __ BIND(L_miss);
  }


  //  Generate stub for checked oop copy.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //      ckoff: O3 (super_check_offset)
  //      ckval: O4 (super_klass)
  //      ret:   O0 zero for success; (-1^K) where K is partial transfer count
  //
  address generate_checkcast_copy(const char *name, address *entry, bool dest_uninitialized = false) {

    const Register O0_from   = O0;      // source array address
    const Register O1_to     = O1;      // destination array address
    const Register O2_count  = O2;      // elements count
    const Register O3_ckoff  = O3;      // super_check_offset
    const Register O4_ckval  = O4;      // super_klass

    const Register O5_offset = O5;      // loop var, with stride wordSize
    const Register G1_remain = G1;      // loop var, with stride -1
    const Register G3_oop    = G3;      // actual oop copied
    const Register G4_klass  = G4;      // oop._klass
    const Register G5_super  = G5;      // oop._klass._primary_supers[ckval]

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

#ifdef ASSERT
    // We sometimes save a frame (see generate_type_check below).
    // If this will cause trouble, let's fail now instead of later.
    __ save_frame(0);
    __ restore();
#endif

    assert_clean_int(O2_count, G1);     // Make sure 'count' is clean int.

#ifdef ASSERT
    // caller guarantees that the arrays really are different
    // otherwise, we would have to make conjoint checks
    { Label L;
      __ mov(O3, G1);           // spill: overlap test smashes O3
      __ mov(O4, G4);           // spill: overlap test smashes O4
      array_overlap_test(L, LogBytesPerHeapOop);
      __ stop("checkcast_copy within a single array");
      __ bind(L);
      __ mov(G1, O3);
      __ mov(G4, O4);
    }
#endif //ASSERT

    if (entry != NULL) {
      *entry = __ pc();
      // caller can pass a 64-bit byte count here (from generic stub)
      BLOCK_COMMENT("Entry:");
    }
    gen_write_ref_array_pre_barrier(O1_to, O2_count, dest_uninitialized);

    Label load_element, store_element, do_card_marks, fail, done;
    __ addcc(O2_count, 0, G1_remain);   // initialize loop index, and test it
    __ brx(Assembler::notZero, false, Assembler::pt, load_element);
    __ delayed()->mov(G0, O5_offset);   // offset from start of arrays

    // Empty array:  Nothing to do.
    inc_counter_np(SharedRuntime::_checkcast_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->set(0, O0);           // return 0 on (trivial) success

    // ======== begin loop ========
    // (Loop is rotated; its entry is load_element.)
    // Loop variables:
    //   (O5 = 0; ; O5 += wordSize) --- offset from src, dest arrays
    //   (O2 = len; O2 != 0; O2--) --- number of oops *remaining*
    //   G3, G4, G5 --- current oop, oop.klass, oop.klass.super
    __ align(OptoLoopAlignment);

    __ BIND(store_element);
    __ deccc(G1_remain);                // decrement the count
    __ store_heap_oop(G3_oop, O1_to, O5_offset); // store the oop
    __ inc(O5_offset, heapOopSize);     // step to next offset
    __ brx(Assembler::zero, true, Assembler::pt, do_card_marks);
    __ delayed()->set(0, O0);           // return -1 on success

    // ======== loop entry is here ========
    __ BIND(load_element);
    __ load_heap_oop(O0_from, O5_offset, G3_oop);  // load the oop
    __ br_null_short(G3_oop, Assembler::pt, store_element);

    __ load_klass(G3_oop, G4_klass); // query the object klass

    generate_type_check(G4_klass, O3_ckoff, O4_ckval, G5_super,
                        // branch to this on success:
                        store_element);
    // ======== end loop ========

    // It was a real error; we must depend on the caller to finish the job.
    // Register G1 has number of *remaining* oops, O2 number of *total* oops.
    // Emit GC store barriers for the oops we have copied (O2 minus G1),
    // and report their number to the caller.
    __ BIND(fail);
    __ subcc(O2_count, G1_remain, O2_count);
    __ brx(Assembler::zero, false, Assembler::pt, done);
    __ delayed()->not1(O2_count, O0);   // report (-1^K) to caller

    __ BIND(do_card_marks);
    gen_write_ref_array_post_barrier(O1_to, O2_count, O3);   // store check on O1[0..O2]

    __ BIND(done);
    inc_counter_np(SharedRuntime::_checkcast_array_copy_ctr, O3, O4);
    __ retl();
    __ delayed()->nop();             // return value in 00

    return start;
  }


  //  Generate 'unsafe' array copy stub
  //  Though just as safe as the other stubs, it takes an unscaled
  //  size_t argument instead of an element count.
  //
  // Arguments for generated stub:
  //      from:  O0
  //      to:    O1
  //      count: O2 byte count, treated as ssize_t, can be zero
  //
  // Examines the alignment of the operands and dispatches
  // to a long, int, short, or byte copy loop.
  //
  address generate_unsafe_copy(const char* name,
                               address byte_copy_entry,
                               address short_copy_entry,
                               address int_copy_entry,
                               address long_copy_entry) {

    const Register O0_from   = O0;      // source array address
    const Register O1_to     = O1;      // destination array address
    const Register O2_count  = O2;      // elements count

    const Register G1_bits   = G1;      // test copy of low bits

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    // bump this on entry, not on exit:
    inc_counter_np(SharedRuntime::_unsafe_array_copy_ctr, G1, G3);

    __ or3(O0_from, O1_to, G1_bits);
    __ or3(O2_count,       G1_bits, G1_bits);

    __ btst(BytesPerLong-1, G1_bits);
    __ br(Assembler::zero, true, Assembler::pt,
          long_copy_entry, relocInfo::runtime_call_type);
    // scale the count on the way out:
    __ delayed()->srax(O2_count, LogBytesPerLong, O2_count);

    __ btst(BytesPerInt-1, G1_bits);
    __ br(Assembler::zero, true, Assembler::pt,
          int_copy_entry, relocInfo::runtime_call_type);
    // scale the count on the way out:
    __ delayed()->srax(O2_count, LogBytesPerInt, O2_count);

    __ btst(BytesPerShort-1, G1_bits);
    __ br(Assembler::zero, true, Assembler::pt,
          short_copy_entry, relocInfo::runtime_call_type);
    // scale the count on the way out:
    __ delayed()->srax(O2_count, LogBytesPerShort, O2_count);

    __ br(Assembler::always, false, Assembler::pt,
          byte_copy_entry, relocInfo::runtime_call_type);
    __ delayed()->nop();

    return start;
  }


  // Perform range checks on the proposed arraycopy.
  // Kills the two temps, but nothing else.
  // Also, clean the sign bits of src_pos and dst_pos.
  void arraycopy_range_checks(Register src,     // source array oop (O0)
                              Register src_pos, // source position (O1)
                              Register dst,     // destination array oo (O2)
                              Register dst_pos, // destination position (O3)
                              Register length,  // length of copy (O4)
                              Register temp1, Register temp2,
                              Label& L_failed) {
    BLOCK_COMMENT("arraycopy_range_checks:");

    //  if (src_pos + length > arrayOop(src)->length() ) FAIL;

    const Register array_length = temp1;  // scratch
    const Register end_pos      = temp2;  // scratch

    // Note:  This next instruction may be in the delay slot of a branch:
    __ add(length, src_pos, end_pos);  // src_pos + length
    __ lduw(src, arrayOopDesc::length_offset_in_bytes(), array_length);
    __ cmp(end_pos, array_length);
    __ br(Assembler::greater, false, Assembler::pn, L_failed);

    //  if (dst_pos + length > arrayOop(dst)->length() ) FAIL;
    __ delayed()->add(length, dst_pos, end_pos); // dst_pos + length
    __ lduw(dst, arrayOopDesc::length_offset_in_bytes(), array_length);
    __ cmp(end_pos, array_length);
    __ br(Assembler::greater, false, Assembler::pn, L_failed);

    // Have to clean up high 32-bits of 'src_pos' and 'dst_pos'.
    // Move with sign extension can be used since they are positive.
    __ delayed()->signx(src_pos, src_pos);
    __ signx(dst_pos, dst_pos);

    BLOCK_COMMENT("arraycopy_range_checks done");
  }


  //
  //  Generate generic array copy stubs
  //
  //  Input:
  //    O0    -  src oop
  //    O1    -  src_pos
  //    O2    -  dst oop
  //    O3    -  dst_pos
  //    O4    -  element count
  //
  //  Output:
  //    O0 ==  0  -  success
  //    O0 == -1  -  need to call System.arraycopy
  //
  address generate_generic_copy(const char *name,
                                address entry_jbyte_arraycopy,
                                address entry_jshort_arraycopy,
                                address entry_jint_arraycopy,
                                address entry_oop_arraycopy,
                                address entry_jlong_arraycopy,
                                address entry_checkcast_arraycopy) {
    Label L_failed, L_objArray;

    // Input registers
    const Register src      = O0;  // source array oop
    const Register src_pos  = O1;  // source position
    const Register dst      = O2;  // destination array oop
    const Register dst_pos  = O3;  // destination position
    const Register length   = O4;  // elements count

    // registers used as temp
    const Register G3_src_klass = G3; // source array klass
    const Register G4_dst_klass = G4; // destination array klass
    const Register G5_lh        = G5; // layout handler
    const Register O5_temp      = O5;

    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    // bump this on entry, not on exit:
    inc_counter_np(SharedRuntime::_generic_array_copy_ctr, G1, G3);

    // In principle, the int arguments could be dirty.
    //assert_clean_int(src_pos, G1);
    //assert_clean_int(dst_pos, G1);
    //assert_clean_int(length, G1);

    //-----------------------------------------------------------------------
    // Assembler stubs will be used for this call to arraycopy
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
    BLOCK_COMMENT("arraycopy initial argument checks");

    //  if (src == NULL) return -1;
    __ br_null(src, false, Assembler::pn, L_failed);

    //  if (src_pos < 0) return -1;
    __ delayed()->tst(src_pos);
    __ br(Assembler::negative, false, Assembler::pn, L_failed);
    __ delayed()->nop();

    //  if (dst == NULL) return -1;
    __ br_null(dst, false, Assembler::pn, L_failed);

    //  if (dst_pos < 0) return -1;
    __ delayed()->tst(dst_pos);
    __ br(Assembler::negative, false, Assembler::pn, L_failed);

    //  if (length < 0) return -1;
    __ delayed()->tst(length);
    __ br(Assembler::negative, false, Assembler::pn, L_failed);

    BLOCK_COMMENT("arraycopy argument klass checks");
    //  get src->klass()
    if (UseCompressedKlassPointers) {
      __ delayed()->nop(); // ??? not good
      __ load_klass(src, G3_src_klass);
    } else {
      __ delayed()->ld_ptr(src, oopDesc::klass_offset_in_bytes(), G3_src_klass);
    }

#ifdef ASSERT
    //  assert(src->klass() != NULL);
    BLOCK_COMMENT("assert klasses not null");
    { Label L_a, L_b;
      __ br_notnull_short(G3_src_klass, Assembler::pt, L_b); // it is broken if klass is NULL
      __ bind(L_a);
      __ stop("broken null klass");
      __ bind(L_b);
      __ load_klass(dst, G4_dst_klass);
      __ br_null(G4_dst_klass, false, Assembler::pn, L_a); // this would be broken also
      __ delayed()->mov(G0, G4_dst_klass);      // scribble the temp
      BLOCK_COMMENT("assert done");
    }
#endif

    // Load layout helper
    //
    //  |array_tag|     | header_size | element_type |     |log2_element_size|
    // 32        30    24            16              8     2                 0
    //
    //   array_tag: typeArray = 0x3, objArray = 0x2, non-array = 0x0
    //

    int lh_offset = in_bytes(Klass::layout_helper_offset());

    // Load 32-bits signed value. Use br() instruction with it to check icc.
    __ lduw(G3_src_klass, lh_offset, G5_lh);

    if (UseCompressedKlassPointers) {
      __ load_klass(dst, G4_dst_klass);
    }
    // Handle objArrays completely differently...
    juint objArray_lh = Klass::array_layout_helper(T_OBJECT);
    __ set(objArray_lh, O5_temp);
    __ cmp(G5_lh,       O5_temp);
    __ br(Assembler::equal, false, Assembler::pt, L_objArray);
    if (UseCompressedKlassPointers) {
      __ delayed()->nop();
    } else {
      __ delayed()->ld_ptr(dst, oopDesc::klass_offset_in_bytes(), G4_dst_klass);
    }

    //  if (src->klass() != dst->klass()) return -1;
    __ cmp_and_brx_short(G3_src_klass, G4_dst_klass, Assembler::notEqual, Assembler::pn, L_failed);

    //  if (!src->is_Array()) return -1;
    __ cmp(G5_lh, Klass::_lh_neutral_value); // < 0
    __ br(Assembler::greaterEqual, false, Assembler::pn, L_failed);

    // At this point, it is known to be a typeArray (array_tag 0x3).
#ifdef ASSERT
    __ delayed()->nop();
    { Label L;
      jint lh_prim_tag_in_place = (Klass::_lh_array_tag_type_value << Klass::_lh_array_tag_shift);
      __ set(lh_prim_tag_in_place, O5_temp);
      __ cmp(G5_lh,                O5_temp);
      __ br(Assembler::greaterEqual, false, Assembler::pt, L);
      __ delayed()->nop();
      __ stop("must be a primitive array");
      __ bind(L);
    }
#else
    __ delayed();                               // match next insn to prev branch
#endif

    arraycopy_range_checks(src, src_pos, dst, dst_pos, length,
                           O5_temp, G4_dst_klass, L_failed);

    // TypeArrayKlass
    //
    // src_addr = (src + array_header_in_bytes()) + (src_pos << log2elemsize);
    // dst_addr = (dst + array_header_in_bytes()) + (dst_pos << log2elemsize);
    //

    const Register G4_offset = G4_dst_klass;    // array offset
    const Register G3_elsize = G3_src_klass;    // log2 element size

    __ srl(G5_lh, Klass::_lh_header_size_shift, G4_offset);
    __ and3(G4_offset, Klass::_lh_header_size_mask, G4_offset); // array_offset
    __ add(src, G4_offset, src);       // src array offset
    __ add(dst, G4_offset, dst);       // dst array offset
    __ and3(G5_lh, Klass::_lh_log2_element_size_mask, G3_elsize); // log2 element size

    // next registers should be set before the jump to corresponding stub
    const Register from     = O0;  // source array address
    const Register to       = O1;  // destination array address
    const Register count    = O2;  // elements count

    // 'from', 'to', 'count' registers should be set in this order
    // since they are the same as 'src', 'src_pos', 'dst'.

    BLOCK_COMMENT("scale indexes to element size");
    __ sll_ptr(src_pos, G3_elsize, src_pos);
    __ sll_ptr(dst_pos, G3_elsize, dst_pos);
    __ add(src, src_pos, from);       // src_addr
    __ add(dst, dst_pos, to);         // dst_addr

    BLOCK_COMMENT("choose copy loop based on element size");
    __ cmp(G3_elsize, 0);
    __ br(Assembler::equal, true, Assembler::pt, entry_jbyte_arraycopy);
    __ delayed()->signx(length, count); // length

    __ cmp(G3_elsize, LogBytesPerShort);
    __ br(Assembler::equal, true, Assembler::pt, entry_jshort_arraycopy);
    __ delayed()->signx(length, count); // length

    __ cmp(G3_elsize, LogBytesPerInt);
    __ br(Assembler::equal, true, Assembler::pt, entry_jint_arraycopy);
    __ delayed()->signx(length, count); // length
#ifdef ASSERT
    { Label L;
      __ cmp_and_br_short(G3_elsize, LogBytesPerLong, Assembler::equal, Assembler::pt, L);
      __ stop("must be long copy, but elsize is wrong");
      __ bind(L);
    }
#endif
    __ br(Assembler::always, false, Assembler::pt, entry_jlong_arraycopy);
    __ delayed()->signx(length, count); // length

    // ObjArrayKlass
  __ BIND(L_objArray);
    // live at this point:  G3_src_klass, G4_dst_klass, src[_pos], dst[_pos], length

    Label L_plain_copy, L_checkcast_copy;
    //  test array classes for subtyping
    __ cmp(G3_src_klass, G4_dst_klass);         // usual case is exact equality
    __ brx(Assembler::notEqual, true, Assembler::pn, L_checkcast_copy);
    __ delayed()->lduw(G4_dst_klass, lh_offset, O5_temp); // hoisted from below

    // Identically typed arrays can be copied without element-wise checks.
    arraycopy_range_checks(src, src_pos, dst, dst_pos, length,
                           O5_temp, G5_lh, L_failed);

    __ add(src, arrayOopDesc::base_offset_in_bytes(T_OBJECT), src); //src offset
    __ add(dst, arrayOopDesc::base_offset_in_bytes(T_OBJECT), dst); //dst offset
    __ sll_ptr(src_pos, LogBytesPerHeapOop, src_pos);
    __ sll_ptr(dst_pos, LogBytesPerHeapOop, dst_pos);
    __ add(src, src_pos, from);       // src_addr
    __ add(dst, dst_pos, to);         // dst_addr
  __ BIND(L_plain_copy);
    __ br(Assembler::always, false, Assembler::pt, entry_oop_arraycopy);
    __ delayed()->signx(length, count); // length

  __ BIND(L_checkcast_copy);
    // live at this point:  G3_src_klass, G4_dst_klass
    {
      // Before looking at dst.length, make sure dst is also an objArray.
      // lduw(G4_dst_klass, lh_offset, O5_temp); // hoisted to delay slot
      __ cmp(G5_lh,                    O5_temp);
      __ br(Assembler::notEqual, false, Assembler::pn, L_failed);

      // It is safe to examine both src.length and dst.length.
      __ delayed();                             // match next insn to prev branch
      arraycopy_range_checks(src, src_pos, dst, dst_pos, length,
                             O5_temp, G5_lh, L_failed);

      // Marshal the base address arguments now, freeing registers.
      __ add(src, arrayOopDesc::base_offset_in_bytes(T_OBJECT), src); //src offset
      __ add(dst, arrayOopDesc::base_offset_in_bytes(T_OBJECT), dst); //dst offset
      __ sll_ptr(src_pos, LogBytesPerHeapOop, src_pos);
      __ sll_ptr(dst_pos, LogBytesPerHeapOop, dst_pos);
      __ add(src, src_pos, from);               // src_addr
      __ add(dst, dst_pos, to);                 // dst_addr
      __ signx(length, count);                  // length (reloaded)

      Register sco_temp = O3;                   // this register is free now
      assert_different_registers(from, to, count, sco_temp,
                                 G4_dst_klass, G3_src_klass);

      // Generate the type check.
      int sco_offset = in_bytes(Klass::super_check_offset_offset());
      __ lduw(G4_dst_klass, sco_offset, sco_temp);
      generate_type_check(G3_src_klass, sco_temp, G4_dst_klass,
                          O5_temp, L_plain_copy);

      // Fetch destination element klass from the ObjArrayKlass header.
      int ek_offset = in_bytes(ObjArrayKlass::element_klass_offset());

      // the checkcast_copy loop needs two extra arguments:
      __ ld_ptr(G4_dst_klass, ek_offset, O4);   // dest elem klass
      // lduw(O4, sco_offset, O3);              // sco of elem klass

      __ br(Assembler::always, false, Assembler::pt, entry_checkcast_arraycopy);
      __ delayed()->lduw(O4, sco_offset, O3);
    }

  __ BIND(L_failed);
    __ retl();
    __ delayed()->sub(G0, 1, O0); // return -1
    return start;
  }

  //
  //  Generate stub for heap zeroing.
  //  "to" address is aligned to jlong (8 bytes).
  //
  // Arguments for generated stub:
  //      to:    O0
  //      count: O1 treated as signed (count of HeapWord)
  //             count could be 0
  //
  address generate_zero_aligned_words(const char* name) {
    __ align(CodeEntryAlignment);
    StubCodeMark mark(this, "StubRoutines", name);
    address start = __ pc();

    const Register to    = O0;   // source array address
    const Register count = O1;   // HeapWords count
    const Register temp  = O2;   // scratch

    Label Ldone;
    __ sllx(count, LogHeapWordSize, count); // to bytes count
    // Use BIS for zeroing
    __ bis_zeroing(to, count, temp, Ldone);
    __ bind(Ldone);
    __ retl();
    __ delayed()->nop();
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

    //*** jbyte
    // Always need aligned and unaligned versions
    StubRoutines::_jbyte_disjoint_arraycopy         = generate_disjoint_byte_copy(false, &entry,
                                                                                  "jbyte_disjoint_arraycopy");
    StubRoutines::_jbyte_arraycopy                  = generate_conjoint_byte_copy(false, entry,
                                                                                  &entry_jbyte_arraycopy,
                                                                                  "jbyte_arraycopy");
    StubRoutines::_arrayof_jbyte_disjoint_arraycopy = generate_disjoint_byte_copy(true, &entry,
                                                                                  "arrayof_jbyte_disjoint_arraycopy");
    StubRoutines::_arrayof_jbyte_arraycopy          = generate_conjoint_byte_copy(true, entry, NULL,
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
    StubRoutines::_arrayof_jint_disjoint_arraycopy = generate_disjoint_int_copy(true, &entry,
                                                                                "arrayof_jint_disjoint_arraycopy");
    StubRoutines::_arrayof_jint_arraycopy          = generate_conjoint_int_copy(true, entry, &entry_jint_arraycopy,
                                                                                "arrayof_jint_arraycopy");
#ifdef _LP64
    // In 64 bit we need both aligned and unaligned versions of jint arraycopy.
    // entry_jint_arraycopy always points to the unaligned version (notice that we overwrite it).
    StubRoutines::_jint_disjoint_arraycopy         = generate_disjoint_int_copy(false, &entry,
                                                                                "jint_disjoint_arraycopy");
    StubRoutines::_jint_arraycopy                  = generate_conjoint_int_copy(false, entry,
                                                                                &entry_jint_arraycopy,
                                                                                "jint_arraycopy");
#else
    // In 32 bit jints are always HeapWordSize aligned, so always use the aligned version
    // (in fact in 32bit we always have a pre-loop part even in the aligned version,
    //  because it uses 64-bit loads/stores, so the aligned flag is actually ignored).
    StubRoutines::_jint_disjoint_arraycopy = StubRoutines::_arrayof_jint_disjoint_arraycopy;
    StubRoutines::_jint_arraycopy          = StubRoutines::_arrayof_jint_arraycopy;
#endif


    //*** jlong
    // It is always aligned
    StubRoutines::_arrayof_jlong_disjoint_arraycopy = generate_disjoint_long_copy(true, &entry,
                                                                                  "arrayof_jlong_disjoint_arraycopy");
    StubRoutines::_arrayof_jlong_arraycopy          = generate_conjoint_long_copy(true, entry, &entry_jlong_arraycopy,
                                                                                  "arrayof_jlong_arraycopy");
    StubRoutines::_jlong_disjoint_arraycopy         = StubRoutines::_arrayof_jlong_disjoint_arraycopy;
    StubRoutines::_jlong_arraycopy                  = StubRoutines::_arrayof_jlong_arraycopy;


    //*** oops
    // Aligned versions
    StubRoutines::_arrayof_oop_disjoint_arraycopy        = generate_disjoint_oop_copy(true, &entry,
                                                                                      "arrayof_oop_disjoint_arraycopy");
    StubRoutines::_arrayof_oop_arraycopy                 = generate_conjoint_oop_copy(true, entry, &entry_oop_arraycopy,
                                                                                      "arrayof_oop_arraycopy");
    // Aligned versions without pre-barriers
    StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit = generate_disjoint_oop_copy(true, &entry,
                                                                                      "arrayof_oop_disjoint_arraycopy_uninit",
                                                                                      /*dest_uninitialized*/true);
    StubRoutines::_arrayof_oop_arraycopy_uninit          = generate_conjoint_oop_copy(true, entry, NULL,
                                                                                      "arrayof_oop_arraycopy_uninit",
                                                                                      /*dest_uninitialized*/true);
#ifdef _LP64
    if (UseCompressedOops) {
      // With compressed oops we need unaligned versions, notice that we overwrite entry_oop_arraycopy.
      StubRoutines::_oop_disjoint_arraycopy            = generate_disjoint_oop_copy(false, &entry,
                                                                                    "oop_disjoint_arraycopy");
      StubRoutines::_oop_arraycopy                     = generate_conjoint_oop_copy(false, entry, &entry_oop_arraycopy,
                                                                                    "oop_arraycopy");
      // Unaligned versions without pre-barriers
      StubRoutines::_oop_disjoint_arraycopy_uninit     = generate_disjoint_oop_copy(false, &entry,
                                                                                    "oop_disjoint_arraycopy_uninit",
                                                                                    /*dest_uninitialized*/true);
      StubRoutines::_oop_arraycopy_uninit              = generate_conjoint_oop_copy(false, entry, NULL,
                                                                                    "oop_arraycopy_uninit",
                                                                                    /*dest_uninitialized*/true);
    } else
#endif
    {
      // oop arraycopy is always aligned on 32bit and 64bit without compressed oops
      StubRoutines::_oop_disjoint_arraycopy            = StubRoutines::_arrayof_oop_disjoint_arraycopy;
      StubRoutines::_oop_arraycopy                     = StubRoutines::_arrayof_oop_arraycopy;
      StubRoutines::_oop_disjoint_arraycopy_uninit     = StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit;
      StubRoutines::_oop_arraycopy_uninit              = StubRoutines::_arrayof_oop_arraycopy_uninit;
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

    if (UseBlockZeroing) {
      StubRoutines::_zero_aligned_words = generate_zero_aligned_words("zero_aligned_words");
    }
  }

  void generate_initial() {
    // Generates all stubs and initializes the entry points

    //------------------------------------------------------------------------------------------------------------------------
    // entry points that exist in all platforms
    // Note: This is code that could be shared among different platforms - however the benefit seems to be smaller than
    //       the disadvantage of having a much more complicated generator structure. See also comment in stubRoutines.hpp.
    StubRoutines::_forward_exception_entry                 = generate_forward_exception();

    StubRoutines::_call_stub_entry                         = generate_call_stub(StubRoutines::_call_stub_return_address);
    StubRoutines::_catch_exception_entry                   = generate_catch_exception();

    //------------------------------------------------------------------------------------------------------------------------
    // entry points that are platform specific
    StubRoutines::Sparc::_test_stop_entry                  = generate_test_stop();

    StubRoutines::Sparc::_stop_subroutine_entry            = generate_stop_subroutine();
    StubRoutines::Sparc::_flush_callers_register_windows_entry = generate_flush_callers_register_windows();

#if !defined(COMPILER2) && !defined(_LP64)
    StubRoutines::_atomic_xchg_entry         = generate_atomic_xchg();
    StubRoutines::_atomic_cmpxchg_entry      = generate_atomic_cmpxchg();
    StubRoutines::_atomic_add_entry          = generate_atomic_add();
    StubRoutines::_atomic_xchg_ptr_entry     = StubRoutines::_atomic_xchg_entry;
    StubRoutines::_atomic_cmpxchg_ptr_entry  = StubRoutines::_atomic_cmpxchg_entry;
    StubRoutines::_atomic_cmpxchg_long_entry = generate_atomic_cmpxchg_long();
    StubRoutines::_atomic_add_ptr_entry      = StubRoutines::_atomic_add_entry;
#endif  // COMPILER2 !=> _LP64

    // Build this early so it's available for the interpreter.
    StubRoutines::_throw_StackOverflowError_entry          = generate_throw_exception("StackOverflowError throw_exception",           CAST_FROM_FN_PTR(address, SharedRuntime::throw_StackOverflowError));
  }


  void generate_all() {
    // Generates all stubs and initializes the entry points

    // Generate partial_subtype_check first here since its code depends on
    // UseZeroBaseCompressedOops which is defined after heap initialization.
    StubRoutines::Sparc::_partial_subtype_check                = generate_partial_subtype_check();
    // These entry points require SharedInfo::stack0 to be set up in non-core builds
    StubRoutines::_throw_AbstractMethodError_entry         = generate_throw_exception("AbstractMethodError throw_exception",          CAST_FROM_FN_PTR(address, SharedRuntime::throw_AbstractMethodError));
    StubRoutines::_throw_IncompatibleClassChangeError_entry= generate_throw_exception("IncompatibleClassChangeError throw_exception", CAST_FROM_FN_PTR(address, SharedRuntime::throw_IncompatibleClassChangeError));
    StubRoutines::_throw_NullPointerException_at_call_entry= generate_throw_exception("NullPointerException at call throw_exception", CAST_FROM_FN_PTR(address, SharedRuntime::throw_NullPointerException_at_call));

    StubRoutines::_handler_for_unsafe_access_entry =
      generate_handler_for_unsafe_access();

    // support for verify_oop (must happen after universe_init)
    StubRoutines::_verify_oop_subroutine_entry     = generate_verify_oop_subroutine();

    // arraycopy stubs used by compilers
    generate_arraycopy_stubs();

    // Don't initialize the platform math functions since sparc
    // doesn't have intrinsics for these operations.

    // Safefetch stubs.
    generate_safefetch("SafeFetch32", sizeof(int),     &StubRoutines::_safefetch32_entry,
                                                       &StubRoutines::_safefetch32_fault_pc,
                                                       &StubRoutines::_safefetch32_continuation_pc);
    generate_safefetch("SafeFetchN", sizeof(intptr_t), &StubRoutines::_safefetchN_entry,
                                                       &StubRoutines::_safefetchN_fault_pc,
                                                       &StubRoutines::_safefetchN_continuation_pc);
  }


 public:
  StubGenerator(CodeBuffer* code, bool all) : StubCodeGenerator(code) {
    // replace the standard masm with a special one:
    _masm = new MacroAssembler(code);

    _stub_count = !all ? 0x100 : 0x200;
    if (all) {
      generate_all();
    } else {
      generate_initial();
    }

    // make sure this stub is available for all local calls
    if (_atomic_add_stub.is_unbound()) {
      // generate a second time, if necessary
      (void) generate_atomic_add();
    }
  }


 private:
  int _stub_count;
  void stub_prolog(StubCodeDesc* cdesc) {
    # ifdef ASSERT
      // put extra information in the stub code, to make it more readable
#ifdef _LP64
// Write the high part of the address
// [RGV] Check if there is a dependency on the size of this prolog
      __ emit_data((intptr_t)cdesc >> 32,    relocInfo::none);
#endif
      __ emit_data((intptr_t)cdesc,    relocInfo::none);
      __ emit_data(++_stub_count, relocInfo::none);
    # endif
    align(true);
  }

  void align(bool at_header = false) {
    // %%%%% move this constant somewhere else
    // UltraSPARC cache line size is 8 instructions:
    const unsigned int icache_line_size = 32;
    const unsigned int icache_half_line_size = 16;

    if (at_header) {
      while ((intptr_t)(__ pc()) % icache_line_size != 0) {
        __ emit_data(0, relocInfo::none);
      }
    } else {
      while ((intptr_t)(__ pc()) % icache_half_line_size != 0) {
        __ nop();
      }
    }
  }

}; // end class declaration

void StubGenerator_generate(CodeBuffer* code, bool all) {
  StubGenerator g(code, all);
}
