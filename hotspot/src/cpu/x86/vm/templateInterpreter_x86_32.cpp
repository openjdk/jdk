/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_templateInterpreter_x86_32.cpp.incl"

#define __ _masm->


#ifndef CC_INTERP
const int method_offset = frame::interpreter_frame_method_offset * wordSize;
const int bci_offset    = frame::interpreter_frame_bcx_offset    * wordSize;
const int locals_offset = frame::interpreter_frame_locals_offset * wordSize;

//------------------------------------------------------------------------------------------------------------------------

address TemplateInterpreterGenerator::generate_StackOverflowError_handler() {
  address entry = __ pc();

  // Note: There should be a minimal interpreter frame set up when stack
  // overflow occurs since we check explicitly for it now.
  //
#ifdef ASSERT
  { Label L;
    __ lea(rax, Address(rbp,
                frame::interpreter_frame_monitor_block_top_offset * wordSize));
    __ cmpptr(rax, rsp);  // rax, = maximal rsp for current rbp,
                        //  (stack grows negative)
    __ jcc(Assembler::aboveEqual, L); // check if frame is complete
    __ stop ("interpreter frame not set up");
    __ bind(L);
  }
#endif // ASSERT
  // Restore bcp under the assumption that the current frame is still
  // interpreted
  __ restore_bcp();

  // expression stack must be empty before entering the VM if an exception
  // happened
  __ empty_expression_stack();
  __ empty_FPU_stack();
  // throw exception
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_StackOverflowError));
  return entry;
}

address TemplateInterpreterGenerator::generate_ArrayIndexOutOfBounds_handler(const char* name) {
  address entry = __ pc();
  // expression stack must be empty before entering the VM if an exception happened
  __ empty_expression_stack();
  __ empty_FPU_stack();
  // setup parameters
  // ??? convention: expect aberrant index in register rbx,
  __ lea(rax, ExternalAddress((address)name));
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_ArrayIndexOutOfBoundsException), rax, rbx);
  return entry;
}

address TemplateInterpreterGenerator::generate_ClassCastException_handler() {
  address entry = __ pc();
  // object is at TOS
  __ pop(rax);
  // expression stack must be empty before entering the VM if an exception
  // happened
  __ empty_expression_stack();
  __ empty_FPU_stack();
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::throw_ClassCastException),
             rax);
  return entry;
}

// Arguments are: required type at TOS+8, failing object (or NULL) at TOS+4.
// pc at TOS (just for debugging)
address TemplateInterpreterGenerator::generate_WrongMethodType_handler() {
  address entry = __ pc();

  __ pop(rbx);                  // actual failing object is at TOS
  __ pop(rax);                  // required type is at TOS+4

  __ verify_oop(rbx);
  __ verify_oop(rax);

  // Various method handle types use interpreter registers as temps.
  __ restore_bcp();
  __ restore_locals();

  // Expression stack must be empty before entering the VM for an exception.
  __ empty_expression_stack();
  __ empty_FPU_stack();
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::throw_WrongMethodTypeException),
             // pass required type, failing object (or NULL)
             rax, rbx);
  return entry;
}


address TemplateInterpreterGenerator::generate_exception_handler_common(const char* name, const char* message, bool pass_oop) {
  assert(!pass_oop || message == NULL, "either oop or message but not both");
  address entry = __ pc();
  if (pass_oop) {
    // object is at TOS
    __ pop(rbx);
  }
  // expression stack must be empty before entering the VM if an exception happened
  __ empty_expression_stack();
  __ empty_FPU_stack();
  // setup parameters
  __ lea(rax, ExternalAddress((address)name));
  if (pass_oop) {
    __ call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::create_klass_exception), rax, rbx);
  } else {
    if (message != NULL) {
      __ lea(rbx, ExternalAddress((address)message));
    } else {
      __ movptr(rbx, NULL_WORD);
    }
    __ call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::create_exception), rax, rbx);
  }
  // throw exception
  __ jump(ExternalAddress(Interpreter::throw_exception_entry()));
  return entry;
}


address TemplateInterpreterGenerator::generate_continuation_for(TosState state) {
  address entry = __ pc();
  // NULL last_sp until next java call
  __ movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD);
  __ dispatch_next(state);
  return entry;
}


address TemplateInterpreterGenerator::generate_return_entry_for(TosState state, int step) {
  Label interpreter_entry;
  address compiled_entry = __ pc();

#ifdef COMPILER2
  // The FPU stack is clean if UseSSE >= 2 but must be cleaned in other cases
  if ((state == ftos && UseSSE < 1) || (state == dtos && UseSSE < 2)) {
    for (int i = 1; i < 8; i++) {
        __ ffree(i);
    }
  } else if (UseSSE < 2) {
    __ empty_FPU_stack();
  }
#endif
  if ((state == ftos && UseSSE < 1) || (state == dtos && UseSSE < 2)) {
    __ MacroAssembler::verify_FPU(1, "generate_return_entry_for compiled");
  } else {
    __ MacroAssembler::verify_FPU(0, "generate_return_entry_for compiled");
  }

  __ jmp(interpreter_entry, relocInfo::none);
  // emit a sentinel we can test for when converting an interpreter
  // entry point to a compiled entry point.
  __ a_long(Interpreter::return_sentinel);
  __ a_long((int)compiled_entry);
  address entry = __ pc();
  __ bind(interpreter_entry);

  // In SSE mode, interpreter returns FP results in xmm0 but they need
  // to end up back on the FPU so it can operate on them.
  if (state == ftos && UseSSE >= 1) {
    __ subptr(rsp, wordSize);
    __ movflt(Address(rsp, 0), xmm0);
    __ fld_s(Address(rsp, 0));
    __ addptr(rsp, wordSize);
  } else if (state == dtos && UseSSE >= 2) {
    __ subptr(rsp, 2*wordSize);
    __ movdbl(Address(rsp, 0), xmm0);
    __ fld_d(Address(rsp, 0));
    __ addptr(rsp, 2*wordSize);
  }

  __ MacroAssembler::verify_FPU(state == ftos || state == dtos ? 1 : 0, "generate_return_entry_for in interpreter");

  // Restore stack bottom in case i2c adjusted stack
  __ movptr(rsp, Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize));
  // and NULL it as marker that rsp is now tos until next java call
  __ movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD);

  __ restore_bcp();
  __ restore_locals();
  __ get_cache_and_index_at_bcp(rbx, rcx, 1);
  __ movl(rbx, Address(rbx, rcx,
                    Address::times_ptr, constantPoolCacheOopDesc::base_offset() +
                    ConstantPoolCacheEntry::flags_offset()));
  __ andptr(rbx, 0xFF);
  __ lea(rsp, Address(rsp, rbx, Interpreter::stackElementScale()));
  __ dispatch_next(state, step);
  return entry;
}


address TemplateInterpreterGenerator::generate_deopt_entry_for(TosState state, int step) {
  address entry = __ pc();

  // In SSE mode, FP results are in xmm0
  if (state == ftos && UseSSE > 0) {
    __ subptr(rsp, wordSize);
    __ movflt(Address(rsp, 0), xmm0);
    __ fld_s(Address(rsp, 0));
    __ addptr(rsp, wordSize);
  } else if (state == dtos && UseSSE >= 2) {
    __ subptr(rsp, 2*wordSize);
    __ movdbl(Address(rsp, 0), xmm0);
    __ fld_d(Address(rsp, 0));
    __ addptr(rsp, 2*wordSize);
  }

  __ MacroAssembler::verify_FPU(state == ftos || state == dtos ? 1 : 0, "generate_deopt_entry_for in interpreter");

  // The stack is not extended by deopt but we must NULL last_sp as this
  // entry is like a "return".
  __ movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD);
  __ restore_bcp();
  __ restore_locals();
  // handle exceptions
  { Label L;
    const Register thread = rcx;
    __ get_thread(thread);
    __ cmpptr(Address(thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::zero, L);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_pending_exception));
    __ should_not_reach_here();
    __ bind(L);
  }
  __ dispatch_next(state, step);
  return entry;
}


int AbstractInterpreter::BasicType_as_index(BasicType type) {
  int i = 0;
  switch (type) {
    case T_BOOLEAN: i = 0; break;
    case T_CHAR   : i = 1; break;
    case T_BYTE   : i = 2; break;
    case T_SHORT  : i = 3; break;
    case T_INT    : // fall through
    case T_LONG   : // fall through
    case T_VOID   : i = 4; break;
    case T_FLOAT  : i = 5; break;  // have to treat float and double separately for SSE
    case T_DOUBLE : i = 6; break;
    case T_OBJECT : // fall through
    case T_ARRAY  : i = 7; break;
    default       : ShouldNotReachHere();
  }
  assert(0 <= i && i < AbstractInterpreter::number_of_result_handlers, "index out of bounds");
  return i;
}


address TemplateInterpreterGenerator::generate_result_handler_for(BasicType type) {
  address entry = __ pc();
  switch (type) {
    case T_BOOLEAN: __ c2bool(rax);            break;
    case T_CHAR   : __ andptr(rax, 0xFFFF);    break;
    case T_BYTE   : __ sign_extend_byte (rax); break;
    case T_SHORT  : __ sign_extend_short(rax); break;
    case T_INT    : /* nothing to do */        break;
    case T_DOUBLE :
    case T_FLOAT  :
      { const Register t = InterpreterRuntime::SignatureHandlerGenerator::temp();
        __ pop(t);                            // remove return address first
        __ pop_dtos_to_rsp();
        // Must return a result for interpreter or compiler. In SSE
        // mode, results are returned in xmm0 and the FPU stack must
        // be empty.
        if (type == T_FLOAT && UseSSE >= 1) {
          // Load ST0
          __ fld_d(Address(rsp, 0));
          // Store as float and empty fpu stack
          __ fstp_s(Address(rsp, 0));
          // and reload
          __ movflt(xmm0, Address(rsp, 0));
        } else if (type == T_DOUBLE && UseSSE >= 2 ) {
          __ movdbl(xmm0, Address(rsp, 0));
        } else {
          // restore ST0
          __ fld_d(Address(rsp, 0));
        }
        // and pop the temp
        __ addptr(rsp, 2 * wordSize);
        __ push(t);                           // restore return address
      }
      break;
    case T_OBJECT :
      // retrieve result from frame
      __ movptr(rax, Address(rbp, frame::interpreter_frame_oop_temp_offset*wordSize));
      // and verify it
      __ verify_oop(rax);
      break;
    default       : ShouldNotReachHere();
  }
  __ ret(0);                                   // return from result handler
  return entry;
}

address TemplateInterpreterGenerator::generate_safept_entry_for(TosState state, address runtime_entry) {
  address entry = __ pc();
  __ push(state);
  __ call_VM(noreg, runtime_entry);
  __ dispatch_via(vtos, Interpreter::_normal_table.table_for(vtos));
  return entry;
}


// Helpers for commoning out cases in the various type of method entries.
//

// increment invocation count & check for overflow
//
// Note: checking for negative value instead of overflow
//       so we have a 'sticky' overflow test
//
// rbx,: method
// rcx: invocation counter
//
void InterpreterGenerator::generate_counter_incr(Label* overflow, Label* profile_method, Label* profile_method_continue) {

  const Address invocation_counter(rbx, methodOopDesc::invocation_counter_offset() + InvocationCounter::counter_offset());
  const Address backedge_counter  (rbx, methodOopDesc::backedge_counter_offset() + InvocationCounter::counter_offset());

  if (ProfileInterpreter) { // %%% Merge this into methodDataOop
    __ incrementl(Address(rbx,methodOopDesc::interpreter_invocation_counter_offset()));
  }
  // Update standard invocation counters
  __ movl(rax, backedge_counter);               // load backedge counter

  __ incrementl(rcx, InvocationCounter::count_increment);
  __ andl(rax, InvocationCounter::count_mask_value);  // mask out the status bits

  __ movl(invocation_counter, rcx);             // save invocation count
  __ addl(rcx, rax);                            // add both counters

  // profile_method is non-null only for interpreted method so
  // profile_method != NULL == !native_call
  // BytecodeInterpreter only calls for native so code is elided.

  if (ProfileInterpreter && profile_method != NULL) {
    // Test to see if we should create a method data oop
    __ cmp32(rcx,
             ExternalAddress((address)&InvocationCounter::InterpreterProfileLimit));
    __ jcc(Assembler::less, *profile_method_continue);

    // if no method data exists, go to profile_method
    __ test_method_data_pointer(rax, *profile_method);
  }

  __ cmp32(rcx,
           ExternalAddress((address)&InvocationCounter::InterpreterInvocationLimit));
  __ jcc(Assembler::aboveEqual, *overflow);

}

void InterpreterGenerator::generate_counter_overflow(Label* do_continue) {

  // Asm interpreter on entry
  // rdi - locals
  // rsi - bcp
  // rbx, - method
  // rdx - cpool
  // rbp, - interpreter frame

  // C++ interpreter on entry
  // rsi - new interpreter state pointer
  // rbp - interpreter frame pointer
  // rbx - method

  // On return (i.e. jump to entry_point) [ back to invocation of interpreter ]
  // rbx, - method
  // rcx - rcvr (assuming there is one)
  // top of stack return address of interpreter caller
  // rsp - sender_sp

  // C++ interpreter only
  // rsi - previous interpreter state pointer

  const Address size_of_parameters(rbx, methodOopDesc::size_of_parameters_offset());

  // InterpreterRuntime::frequency_counter_overflow takes one argument
  // indicating if the counter overflow occurs at a backwards branch (non-NULL bcp).
  // The call returns the address of the verified entry point for the method or NULL
  // if the compilation did not complete (either went background or bailed out).
  __ movptr(rax, (intptr_t)false);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow), rax);

  __ movptr(rbx, Address(rbp, method_offset));   // restore methodOop

  // Preserve invariant that rsi/rdi contain bcp/locals of sender frame
  // and jump to the interpreted entry.
  __ jmp(*do_continue, relocInfo::none);

}

void InterpreterGenerator::generate_stack_overflow_check(void) {
  // see if we've got enough room on the stack for locals plus overhead.
  // the expression stack grows down incrementally, so the normal guard
  // page mechanism will work for that.
  //
  // Registers live on entry:
  //
  // Asm interpreter
  // rdx: number of additional locals this frame needs (what we must check)
  // rbx,: methodOop

  // destroyed on exit
  // rax,

  // NOTE:  since the additional locals are also always pushed (wasn't obvious in
  // generate_method_entry) so the guard should work for them too.
  //

  // monitor entry size: see picture of stack set (generate_method_entry) and frame_x86.hpp
  const int entry_size    = frame::interpreter_frame_monitor_size() * wordSize;

  // total overhead size: entry_size + (saved rbp, thru expr stack bottom).
  // be sure to change this if you add/subtract anything to/from the overhead area
  const int overhead_size = -(frame::interpreter_frame_initial_sp_offset*wordSize) + entry_size;

  const int page_size = os::vm_page_size();

  Label after_frame_check;

  // see if the frame is greater than one page in size. If so,
  // then we need to verify there is enough stack space remaining
  // for the additional locals.
  __ cmpl(rdx, (page_size - overhead_size)/Interpreter::stackElementSize());
  __ jcc(Assembler::belowEqual, after_frame_check);

  // compute rsp as if this were going to be the last frame on
  // the stack before the red zone

  Label after_frame_check_pop;

  __ push(rsi);

  const Register thread = rsi;

  __ get_thread(thread);

  const Address stack_base(thread, Thread::stack_base_offset());
  const Address stack_size(thread, Thread::stack_size_offset());

  // locals + overhead, in bytes
  __ lea(rax, Address(noreg, rdx, Interpreter::stackElementScale(), overhead_size));

#ifdef ASSERT
  Label stack_base_okay, stack_size_okay;
  // verify that thread stack base is non-zero
  __ cmpptr(stack_base, (int32_t)NULL_WORD);
  __ jcc(Assembler::notEqual, stack_base_okay);
  __ stop("stack base is zero");
  __ bind(stack_base_okay);
  // verify that thread stack size is non-zero
  __ cmpptr(stack_size, 0);
  __ jcc(Assembler::notEqual, stack_size_okay);
  __ stop("stack size is zero");
  __ bind(stack_size_okay);
#endif

  // Add stack base to locals and subtract stack size
  __ addptr(rax, stack_base);
  __ subptr(rax, stack_size);

  // Use the maximum number of pages we might bang.
  const int max_pages = StackShadowPages > (StackRedPages+StackYellowPages) ? StackShadowPages :
                                                                              (StackRedPages+StackYellowPages);
  __ addptr(rax, max_pages * page_size);

  // check against the current stack bottom
  __ cmpptr(rsp, rax);
  __ jcc(Assembler::above, after_frame_check_pop);

  __ pop(rsi);  // get saved bcp / (c++ prev state ).

  __ pop(rax);  // get return address
  __ jump(ExternalAddress(Interpreter::throw_StackOverflowError_entry()));

  // all done with frame size check
  __ bind(after_frame_check_pop);
  __ pop(rsi);

  __ bind(after_frame_check);
}

// Allocate monitor and lock method (asm interpreter)
// rbx, - methodOop
//
void InterpreterGenerator::lock_method(void) {
  // synchronize method
  const Address access_flags      (rbx, methodOopDesc::access_flags_offset());
  const Address monitor_block_top (rbp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
  const int entry_size            = frame::interpreter_frame_monitor_size() * wordSize;

  #ifdef ASSERT
    { Label L;
      __ movl(rax, access_flags);
      __ testl(rax, JVM_ACC_SYNCHRONIZED);
      __ jcc(Assembler::notZero, L);
      __ stop("method doesn't need synchronization");
      __ bind(L);
    }
  #endif // ASSERT
  // get synchronization object
  { Label done;
    const int mirror_offset = klassOopDesc::klass_part_offset_in_bytes() + Klass::java_mirror_offset_in_bytes();
    __ movl(rax, access_flags);
    __ testl(rax, JVM_ACC_STATIC);
    __ movptr(rax, Address(rdi, Interpreter::local_offset_in_bytes(0)));  // get receiver (assume this is frequent case)
    __ jcc(Assembler::zero, done);
    __ movptr(rax, Address(rbx, methodOopDesc::constants_offset()));
    __ movptr(rax, Address(rax, constantPoolOopDesc::pool_holder_offset_in_bytes()));
    __ movptr(rax, Address(rax, mirror_offset));
    __ bind(done);
  }
  // add space for monitor & lock
  __ subptr(rsp, entry_size);                                           // add space for a monitor entry
  __ movptr(monitor_block_top, rsp);                                    // set new monitor block top
  __ movptr(Address(rsp, BasicObjectLock::obj_offset_in_bytes()), rax); // store object
  __ mov(rdx, rsp);                                                    // object address
  __ lock_object(rdx);
}

//
// Generate a fixed interpreter frame. This is identical setup for interpreted methods
// and for native methods hence the shared code.

void TemplateInterpreterGenerator::generate_fixed_frame(bool native_call) {
  // initialize fixed part of activation frame
  __ push(rax);                                       // save return address
  __ enter();                                         // save old & set new rbp,


  __ push(rsi);                                       // set sender sp
  __ push((int32_t)NULL_WORD);                        // leave last_sp as null
  __ movptr(rsi, Address(rbx,methodOopDesc::const_offset())); // get constMethodOop
  __ lea(rsi, Address(rsi,constMethodOopDesc::codes_offset())); // get codebase
  __ push(rbx);                                      // save methodOop
  if (ProfileInterpreter) {
    Label method_data_continue;
    __ movptr(rdx, Address(rbx, in_bytes(methodOopDesc::method_data_offset())));
    __ testptr(rdx, rdx);
    __ jcc(Assembler::zero, method_data_continue);
    __ addptr(rdx, in_bytes(methodDataOopDesc::data_offset()));
    __ bind(method_data_continue);
    __ push(rdx);                                       // set the mdp (method data pointer)
  } else {
    __ push(0);
  }

  __ movptr(rdx, Address(rbx, methodOopDesc::constants_offset()));
  __ movptr(rdx, Address(rdx, constantPoolOopDesc::cache_offset_in_bytes()));
  __ push(rdx);                                       // set constant pool cache
  __ push(rdi);                                       // set locals pointer
  if (native_call) {
    __ push(0);                                       // no bcp
  } else {
    __ push(rsi);                                     // set bcp
    }
  __ push(0);                                         // reserve word for pointer to expression stack bottom
  __ movptr(Address(rsp, 0), rsp);                    // set expression stack bottom
}

// End of helpers

//
// Various method entries
//------------------------------------------------------------------------------------------------------------------------
//
//

// Call an accessor method (assuming it is resolved, otherwise drop into vanilla (slow path) entry

address InterpreterGenerator::generate_accessor_entry(void) {

  // rbx,: methodOop
  // rcx: receiver (preserve for slow entry into asm interpreter)

  // rsi: senderSP must preserved for slow path, set SP to it on fast path

  address entry_point = __ pc();
  Label xreturn_path;

  // do fastpath for resolved accessor methods
  if (UseFastAccessorMethods) {
    Label slow_path;
    // If we need a safepoint check, generate full interpreter entry.
    ExternalAddress state(SafepointSynchronize::address_of_state());
    __ cmp32(ExternalAddress(SafepointSynchronize::address_of_state()),
             SafepointSynchronize::_not_synchronized);

    __ jcc(Assembler::notEqual, slow_path);
    // ASM/C++ Interpreter
    // Code: _aload_0, _(i|a)getfield, _(i|a)return or any rewrites thereof; parameter size = 1
    // Note: We can only use this code if the getfield has been resolved
    //       and if we don't have a null-pointer exception => check for
    //       these conditions first and use slow path if necessary.
    // rbx,: method
    // rcx: receiver
    __ movptr(rax, Address(rsp, wordSize));

    // check if local 0 != NULL and read field
    __ testptr(rax, rax);
    __ jcc(Assembler::zero, slow_path);

    __ movptr(rdi, Address(rbx, methodOopDesc::constants_offset()));
    // read first instruction word and extract bytecode @ 1 and index @ 2
    __ movptr(rdx, Address(rbx, methodOopDesc::const_offset()));
    __ movl(rdx, Address(rdx, constMethodOopDesc::codes_offset()));
    // Shift codes right to get the index on the right.
    // The bytecode fetched looks like <index><0xb4><0x2a>
    __ shrl(rdx, 2*BitsPerByte);
    __ shll(rdx, exact_log2(in_words(ConstantPoolCacheEntry::size())));
    __ movptr(rdi, Address(rdi, constantPoolOopDesc::cache_offset_in_bytes()));

    // rax,: local 0
    // rbx,: method
    // rcx: receiver - do not destroy since it is needed for slow path!
    // rcx: scratch
    // rdx: constant pool cache index
    // rdi: constant pool cache
    // rsi: sender sp

    // check if getfield has been resolved and read constant pool cache entry
    // check the validity of the cache entry by testing whether _indices field
    // contains Bytecode::_getfield in b1 byte.
    assert(in_words(ConstantPoolCacheEntry::size()) == 4, "adjust shift below");
    __ movl(rcx,
            Address(rdi,
                    rdx,
                    Address::times_ptr, constantPoolCacheOopDesc::base_offset() + ConstantPoolCacheEntry::indices_offset()));
    __ shrl(rcx, 2*BitsPerByte);
    __ andl(rcx, 0xFF);
    __ cmpl(rcx, Bytecodes::_getfield);
    __ jcc(Assembler::notEqual, slow_path);

    // Note: constant pool entry is not valid before bytecode is resolved
    __ movptr(rcx,
              Address(rdi,
                      rdx,
                      Address::times_ptr, constantPoolCacheOopDesc::base_offset() + ConstantPoolCacheEntry::f2_offset()));
    __ movl(rdx,
            Address(rdi,
                    rdx,
                    Address::times_ptr, constantPoolCacheOopDesc::base_offset() + ConstantPoolCacheEntry::flags_offset()));

    Label notByte, notShort, notChar;
    const Address field_address (rax, rcx, Address::times_1);

    // Need to differentiate between igetfield, agetfield, bgetfield etc.
    // because they are different sizes.
    // Use the type from the constant pool cache
    __ shrl(rdx, ConstantPoolCacheEntry::tosBits);
    // Make sure we don't need to mask rdx for tosBits after the above shift
    ConstantPoolCacheEntry::verify_tosBits();
    __ cmpl(rdx, btos);
    __ jcc(Assembler::notEqual, notByte);
    __ load_signed_byte(rax, field_address);
    __ jmp(xreturn_path);

    __ bind(notByte);
    __ cmpl(rdx, stos);
    __ jcc(Assembler::notEqual, notShort);
    __ load_signed_short(rax, field_address);
    __ jmp(xreturn_path);

    __ bind(notShort);
    __ cmpl(rdx, ctos);
    __ jcc(Assembler::notEqual, notChar);
    __ load_unsigned_short(rax, field_address);
    __ jmp(xreturn_path);

    __ bind(notChar);
#ifdef ASSERT
    Label okay;
    __ cmpl(rdx, atos);
    __ jcc(Assembler::equal, okay);
    __ cmpl(rdx, itos);
    __ jcc(Assembler::equal, okay);
    __ stop("what type is this?");
    __ bind(okay);
#endif // ASSERT
    // All the rest are a 32 bit wordsize
    // This is ok for now. Since fast accessors should be going away
    __ movptr(rax, field_address);

    __ bind(xreturn_path);

    // _ireturn/_areturn
    __ pop(rdi);                               // get return address
    __ mov(rsp, rsi);                          // set sp to sender sp
    __ jmp(rdi);

    // generate a vanilla interpreter entry as the slow path
    __ bind(slow_path);

    (void) generate_normal_entry(false);
    return entry_point;
  }
  return NULL;

}

//
// Interpreter stub for calling a native method. (asm interpreter)
// This sets up a somewhat different looking stack for calling the native method
// than the typical interpreter frame setup.
//

address InterpreterGenerator::generate_native_entry(bool synchronized) {
  // determine code generation flags
  bool inc_counter  = UseCompiler || CountCompiledCalls;

  // rbx,: methodOop
  // rsi: sender sp
  // rsi: previous interpreter state (C++ interpreter) must preserve
  address entry_point = __ pc();


  const Address size_of_parameters(rbx, methodOopDesc::size_of_parameters_offset());
  const Address invocation_counter(rbx, methodOopDesc::invocation_counter_offset() + InvocationCounter::counter_offset());
  const Address access_flags      (rbx, methodOopDesc::access_flags_offset());

  // get parameter size (always needed)
  __ load_unsigned_short(rcx, size_of_parameters);

  // native calls don't need the stack size check since they have no expression stack
  // and the arguments are already on the stack and we only add a handful of words
  // to the stack

  // rbx,: methodOop
  // rcx: size of parameters
  // rsi: sender sp

  __ pop(rax);                                       // get return address
  // for natives the size of locals is zero

  // compute beginning of parameters (rdi)
  __ lea(rdi, Address(rsp, rcx, Interpreter::stackElementScale(), -wordSize));


  // add 2 zero-initialized slots for native calls
  // NULL result handler
  __ push((int32_t)NULL_WORD);
  // NULL oop temp (mirror or jni oop result)
  __ push((int32_t)NULL_WORD);

  if (inc_counter) __ movl(rcx, invocation_counter);  // (pre-)fetch invocation count
  // initialize fixed part of activation frame

  generate_fixed_frame(true);

  // make sure method is native & not abstract
#ifdef ASSERT
  __ movl(rax, access_flags);
  {
    Label L;
    __ testl(rax, JVM_ACC_NATIVE);
    __ jcc(Assembler::notZero, L);
    __ stop("tried to execute non-native method as native");
    __ bind(L);
  }
  { Label L;
    __ testl(rax, JVM_ACC_ABSTRACT);
    __ jcc(Assembler::zero, L);
    __ stop("tried to execute abstract method in interpreter");
    __ bind(L);
  }
#endif

  // Since at this point in the method invocation the exception handler
  // would try to exit the monitor of synchronized methods which hasn't
  // been entered yet, we set the thread local variable
  // _do_not_unlock_if_synchronized to true. The remove_activation will
  // check this flag.

  __ get_thread(rax);
  const Address do_not_unlock_if_synchronized(rax,
        in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()));
  __ movbool(do_not_unlock_if_synchronized, true);

  // increment invocation count & check for overflow
  Label invocation_counter_overflow;
  if (inc_counter) {
    generate_counter_incr(&invocation_counter_overflow, NULL, NULL);
  }

  Label continue_after_compile;
  __ bind(continue_after_compile);

  bang_stack_shadow_pages(true);

  // reset the _do_not_unlock_if_synchronized flag
  __ get_thread(rax);
  __ movbool(do_not_unlock_if_synchronized, false);

  // check for synchronized methods
  // Must happen AFTER invocation_counter check and stack overflow check,
  // so method is not locked if overflows.
  //
  if (synchronized) {
    lock_method();
  } else {
    // no synchronization necessary
#ifdef ASSERT
      { Label L;
        __ movl(rax, access_flags);
        __ testl(rax, JVM_ACC_SYNCHRONIZED);
        __ jcc(Assembler::zero, L);
        __ stop("method needs synchronization");
        __ bind(L);
      }
#endif
  }

  // start execution
#ifdef ASSERT
  { Label L;
    const Address monitor_block_top (rbp,
                 frame::interpreter_frame_monitor_block_top_offset * wordSize);
    __ movptr(rax, monitor_block_top);
    __ cmpptr(rax, rsp);
    __ jcc(Assembler::equal, L);
    __ stop("broken stack frame setup in interpreter");
    __ bind(L);
  }
#endif

  // jvmti/dtrace support
  __ notify_method_entry();

  // work registers
  const Register method = rbx;
  const Register thread = rdi;
  const Register t      = rcx;

  // allocate space for parameters
  __ get_method(method);
  __ verify_oop(method);
  __ load_unsigned_short(t, Address(method, methodOopDesc::size_of_parameters_offset()));
  __ shlptr(t, Interpreter::logStackElementSize());
  __ addptr(t, 2*wordSize);     // allocate two more slots for JNIEnv and possible mirror
  __ subptr(rsp, t);
  __ andptr(rsp, -(StackAlignmentInBytes)); // gcc needs 16 byte aligned stacks to do XMM intrinsics

  // get signature handler
  { Label L;
    __ movptr(t, Address(method, methodOopDesc::signature_handler_offset()));
    __ testptr(t, t);
    __ jcc(Assembler::notZero, L);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::prepare_native_call), method);
    __ get_method(method);
    __ movptr(t, Address(method, methodOopDesc::signature_handler_offset()));
    __ bind(L);
  }

  // call signature handler
  assert(InterpreterRuntime::SignatureHandlerGenerator::from() == rdi, "adjust this code");
  assert(InterpreterRuntime::SignatureHandlerGenerator::to  () == rsp, "adjust this code");
  assert(InterpreterRuntime::SignatureHandlerGenerator::temp() == t  , "adjust this code");
  // The generated handlers do not touch RBX (the method oop).
  // However, large signatures cannot be cached and are generated
  // each time here.  The slow-path generator will blow RBX
  // sometime, so we must reload it after the call.
  __ call(t);
  __ get_method(method);        // slow path call blows RBX on DevStudio 5.0

  // result handler is in rax,
  // set result handler
  __ movptr(Address(rbp, frame::interpreter_frame_result_handler_offset*wordSize), rax);

  // pass mirror handle if static call
  { Label L;
    const int mirror_offset = klassOopDesc::klass_part_offset_in_bytes() + Klass::java_mirror_offset_in_bytes();
    __ movl(t, Address(method, methodOopDesc::access_flags_offset()));
    __ testl(t, JVM_ACC_STATIC);
    __ jcc(Assembler::zero, L);
    // get mirror
    __ movptr(t, Address(method, methodOopDesc:: constants_offset()));
    __ movptr(t, Address(t, constantPoolOopDesc::pool_holder_offset_in_bytes()));
    __ movptr(t, Address(t, mirror_offset));
    // copy mirror into activation frame
    __ movptr(Address(rbp, frame::interpreter_frame_oop_temp_offset * wordSize), t);
    // pass handle to mirror
    __ lea(t, Address(rbp, frame::interpreter_frame_oop_temp_offset * wordSize));
    __ movptr(Address(rsp, wordSize), t);
    __ bind(L);
  }

  // get native function entry point
  { Label L;
    __ movptr(rax, Address(method, methodOopDesc::native_function_offset()));
    ExternalAddress unsatisfied(SharedRuntime::native_method_throw_unsatisfied_link_error_entry());
    __ cmpptr(rax, unsatisfied.addr());
    __ jcc(Assembler::notEqual, L);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::prepare_native_call), method);
    __ get_method(method);
    __ verify_oop(method);
    __ movptr(rax, Address(method, methodOopDesc::native_function_offset()));
    __ bind(L);
  }

  // pass JNIEnv
  __ get_thread(thread);
  __ lea(t, Address(thread, JavaThread::jni_environment_offset()));
  __ movptr(Address(rsp, 0), t);

  // set_last_Java_frame_before_call
  // It is enough that the pc()
  // points into the right code segment. It does not have to be the correct return pc.
  __ set_last_Java_frame(thread, noreg, rbp, __ pc());

  // change thread state
#ifdef ASSERT
  { Label L;
    __ movl(t, Address(thread, JavaThread::thread_state_offset()));
    __ cmpl(t, _thread_in_Java);
    __ jcc(Assembler::equal, L);
    __ stop("Wrong thread state in native stub");
    __ bind(L);
  }
#endif

  // Change state to native
  __ movl(Address(thread, JavaThread::thread_state_offset()), _thread_in_native);
  __ call(rax);

  // result potentially in rdx:rax or ST0

  // Either restore the MXCSR register after returning from the JNI Call
  // or verify that it wasn't changed.
  if (VM_Version::supports_sse()) {
    if (RestoreMXCSROnJNICalls) {
      __ ldmxcsr(ExternalAddress(StubRoutines::addr_mxcsr_std()));
    }
    else if (CheckJNICalls ) {
      __ call(RuntimeAddress(StubRoutines::x86::verify_mxcsr_entry()));
    }
  }

  // Either restore the x87 floating pointer control word after returning
  // from the JNI call or verify that it wasn't changed.
  if (CheckJNICalls) {
    __ call(RuntimeAddress(StubRoutines::x86::verify_fpu_cntrl_wrd_entry()));
  }

  // save potential result in ST(0) & rdx:rax
  // (if result handler is the T_FLOAT or T_DOUBLE handler, result must be in ST0 -
  // the check is necessary to avoid potential Intel FPU overflow problems by saving/restoring 'empty' FPU registers)
  // It is safe to do this push because state is _thread_in_native and return address will be found
  // via _last_native_pc and not via _last_jave_sp

  // NOTE: the order of theses push(es) is known to frame::interpreter_frame_result.
  // If the order changes or anything else is added to the stack the code in
  // interpreter_frame_result will have to be changed.

  { Label L;
    Label push_double;
    ExternalAddress float_handler(AbstractInterpreter::result_handler(T_FLOAT));
    ExternalAddress double_handler(AbstractInterpreter::result_handler(T_DOUBLE));
    __ cmpptr(Address(rbp, (frame::interpreter_frame_oop_temp_offset + 1)*wordSize),
              float_handler.addr());
    __ jcc(Assembler::equal, push_double);
    __ cmpptr(Address(rbp, (frame::interpreter_frame_oop_temp_offset + 1)*wordSize),
              double_handler.addr());
    __ jcc(Assembler::notEqual, L);
    __ bind(push_double);
    __ push(dtos);
    __ bind(L);
  }
  __ push(ltos);

  // change thread state
  __ get_thread(thread);
  __ movl(Address(thread, JavaThread::thread_state_offset()), _thread_in_native_trans);
  if(os::is_MP()) {
    if (UseMembar) {
      // Force this write out before the read below
      __ membar(Assembler::Membar_mask_bits(
           Assembler::LoadLoad | Assembler::LoadStore |
           Assembler::StoreLoad | Assembler::StoreStore));
    } else {
      // Write serialization page so VM thread can do a pseudo remote membar.
      // We use the current thread pointer to calculate a thread specific
      // offset to write to within the page. This minimizes bus traffic
      // due to cache line collision.
      __ serialize_memory(thread, rcx);
    }
  }

  if (AlwaysRestoreFPU) {
    //  Make sure the control word is correct.
    __ fldcw(ExternalAddress(StubRoutines::addr_fpu_cntrl_wrd_std()));
  }

  // check for safepoint operation in progress and/or pending suspend requests
  { Label Continue;

    __ cmp32(ExternalAddress(SafepointSynchronize::address_of_state()),
             SafepointSynchronize::_not_synchronized);

    Label L;
    __ jcc(Assembler::notEqual, L);
    __ cmpl(Address(thread, JavaThread::suspend_flags_offset()), 0);
    __ jcc(Assembler::equal, Continue);
    __ bind(L);

    // Don't use call_VM as it will see a possible pending exception and forward it
    // and never return here preventing us from clearing _last_native_pc down below.
    // Also can't use call_VM_leaf either as it will check to see if rsi & rdi are
    // preserved and correspond to the bcp/locals pointers. So we do a runtime call
    // by hand.
    //
    __ push(thread);
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address,
                                            JavaThread::check_special_condition_for_native_trans)));
    __ increment(rsp, wordSize);
    __ get_thread(thread);

    __ bind(Continue);
  }

  // change thread state
  __ movl(Address(thread, JavaThread::thread_state_offset()), _thread_in_Java);

  __ reset_last_Java_frame(thread, true, true);

  // reset handle block
  __ movptr(t, Address(thread, JavaThread::active_handles_offset()));
  __ movptr(Address(t, JNIHandleBlock::top_offset_in_bytes()), NULL_WORD);

  // If result was an oop then unbox and save it in the frame
  { Label L;
    Label no_oop, store_result;
    ExternalAddress handler(AbstractInterpreter::result_handler(T_OBJECT));
    __ cmpptr(Address(rbp, frame::interpreter_frame_result_handler_offset*wordSize),
              handler.addr());
    __ jcc(Assembler::notEqual, no_oop);
    __ cmpptr(Address(rsp, 0), (int32_t)NULL_WORD);
    __ pop(ltos);
    __ testptr(rax, rax);
    __ jcc(Assembler::zero, store_result);
    // unbox
    __ movptr(rax, Address(rax, 0));
    __ bind(store_result);
    __ movptr(Address(rbp, (frame::interpreter_frame_oop_temp_offset)*wordSize), rax);
    // keep stack depth as expected by pushing oop which will eventually be discarded
    __ push(ltos);
    __ bind(no_oop);
  }

  {
     Label no_reguard;
     __ cmpl(Address(thread, JavaThread::stack_guard_state_offset()), JavaThread::stack_guard_yellow_disabled);
     __ jcc(Assembler::notEqual, no_reguard);

     __ pusha();
     __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages)));
     __ popa();

     __ bind(no_reguard);
   }

  // restore rsi to have legal interpreter frame,
  // i.e., bci == 0 <=> rsi == code_base()
  // Can't call_VM until bcp is within reasonable.
  __ get_method(method);      // method is junk from thread_in_native to now.
  __ verify_oop(method);
  __ movptr(rsi, Address(method,methodOopDesc::const_offset()));   // get constMethodOop
  __ lea(rsi, Address(rsi,constMethodOopDesc::codes_offset()));    // get codebase

  // handle exceptions (exception handling will handle unlocking!)
  { Label L;
    __ cmpptr(Address(thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::zero, L);
    // Note: At some point we may want to unify this with the code used in call_VM_base();
    //       i.e., we should use the StubRoutines::forward_exception code. For now this
    //       doesn't work here because the rsp is not correctly set at this point.
    __ MacroAssembler::call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_pending_exception));
    __ should_not_reach_here();
    __ bind(L);
  }

  // do unlocking if necessary
  { Label L;
    __ movl(t, Address(method, methodOopDesc::access_flags_offset()));
    __ testl(t, JVM_ACC_SYNCHRONIZED);
    __ jcc(Assembler::zero, L);
    // the code below should be shared with interpreter macro assembler implementation
    { Label unlock;
      // BasicObjectLock will be first in list, since this is a synchronized method. However, need
      // to check that the object has not been unlocked by an explicit monitorexit bytecode.
      const Address monitor(rbp, frame::interpreter_frame_initial_sp_offset * wordSize - (int)sizeof(BasicObjectLock));

      __ lea(rdx, monitor);                   // address of first monitor

      __ movptr(t, Address(rdx, BasicObjectLock::obj_offset_in_bytes()));
      __ testptr(t, t);
      __ jcc(Assembler::notZero, unlock);

      // Entry already unlocked, need to throw exception
      __ MacroAssembler::call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_illegal_monitor_state_exception));
      __ should_not_reach_here();

      __ bind(unlock);
      __ unlock_object(rdx);
    }
    __ bind(L);
  }

  // jvmti/dtrace support
  // Note: This must happen _after_ handling/throwing any exceptions since
  //       the exception handler code notifies the runtime of method exits
  //       too. If this happens before, method entry/exit notifications are
  //       not properly paired (was bug - gri 11/22/99).
  __ notify_method_exit(vtos, InterpreterMacroAssembler::NotifyJVMTI);

  // restore potential result in rdx:rax, call result handler to restore potential result in ST0 & handle result
  __ pop(ltos);
  __ movptr(t, Address(rbp, frame::interpreter_frame_result_handler_offset*wordSize));
  __ call(t);

  // remove activation
  __ movptr(t, Address(rbp, frame::interpreter_frame_sender_sp_offset * wordSize)); // get sender sp
  __ leave();                                // remove frame anchor
  __ pop(rdi);                               // get return address
  __ mov(rsp, t);                            // set sp to sender sp
  __ jmp(rdi);

  if (inc_counter) {
    // Handle overflow of counter and compile method
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(&continue_after_compile);
  }

  return entry_point;
}

//
// Generic interpreted method entry to (asm) interpreter
//
address InterpreterGenerator::generate_normal_entry(bool synchronized) {
  // determine code generation flags
  bool inc_counter  = UseCompiler || CountCompiledCalls;

  // rbx,: methodOop
  // rsi: sender sp
  address entry_point = __ pc();


  const Address size_of_parameters(rbx, methodOopDesc::size_of_parameters_offset());
  const Address size_of_locals    (rbx, methodOopDesc::size_of_locals_offset());
  const Address invocation_counter(rbx, methodOopDesc::invocation_counter_offset() + InvocationCounter::counter_offset());
  const Address access_flags      (rbx, methodOopDesc::access_flags_offset());

  // get parameter size (always needed)
  __ load_unsigned_short(rcx, size_of_parameters);

  // rbx,: methodOop
  // rcx: size of parameters

  // rsi: sender_sp (could differ from sp+wordSize if we were called via c2i )

  __ load_unsigned_short(rdx, size_of_locals);       // get size of locals in words
  __ subl(rdx, rcx);                                // rdx = no. of additional locals

  // see if we've got enough room on the stack for locals plus overhead.
  generate_stack_overflow_check();

  // get return address
  __ pop(rax);

  // compute beginning of parameters (rdi)
  __ lea(rdi, Address(rsp, rcx, Interpreter::stackElementScale(), -wordSize));

  // rdx - # of additional locals
  // allocate space for locals
  // explicitly initialize locals
  {
    Label exit, loop;
    __ testl(rdx, rdx);
    __ jcc(Assembler::lessEqual, exit);               // do nothing if rdx <= 0
    __ bind(loop);
    if (TaggedStackInterpreter) {
      __ push((int32_t)NULL_WORD);                    // push tag
    }
    __ push((int32_t)NULL_WORD);                      // initialize local variables
    __ decrement(rdx);                                // until everything initialized
    __ jcc(Assembler::greater, loop);
    __ bind(exit);
  }

  if (inc_counter) __ movl(rcx, invocation_counter);  // (pre-)fetch invocation count
  // initialize fixed part of activation frame
  generate_fixed_frame(false);

  // make sure method is not native & not abstract
#ifdef ASSERT
  __ movl(rax, access_flags);
  {
    Label L;
    __ testl(rax, JVM_ACC_NATIVE);
    __ jcc(Assembler::zero, L);
    __ stop("tried to execute native method as non-native");
    __ bind(L);
  }
  { Label L;
    __ testl(rax, JVM_ACC_ABSTRACT);
    __ jcc(Assembler::zero, L);
    __ stop("tried to execute abstract method in interpreter");
    __ bind(L);
  }
#endif

  // Since at this point in the method invocation the exception handler
  // would try to exit the monitor of synchronized methods which hasn't
  // been entered yet, we set the thread local variable
  // _do_not_unlock_if_synchronized to true. The remove_activation will
  // check this flag.

  __ get_thread(rax);
  const Address do_not_unlock_if_synchronized(rax,
        in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()));
  __ movbool(do_not_unlock_if_synchronized, true);

  // increment invocation count & check for overflow
  Label invocation_counter_overflow;
  Label profile_method;
  Label profile_method_continue;
  if (inc_counter) {
    generate_counter_incr(&invocation_counter_overflow, &profile_method, &profile_method_continue);
    if (ProfileInterpreter) {
      __ bind(profile_method_continue);
    }
  }
  Label continue_after_compile;
  __ bind(continue_after_compile);

  bang_stack_shadow_pages(false);

  // reset the _do_not_unlock_if_synchronized flag
  __ get_thread(rax);
  __ movbool(do_not_unlock_if_synchronized, false);

  // check for synchronized methods
  // Must happen AFTER invocation_counter check and stack overflow check,
  // so method is not locked if overflows.
  //
  if (synchronized) {
    // Allocate monitor and lock method
    lock_method();
  } else {
    // no synchronization necessary
#ifdef ASSERT
      { Label L;
        __ movl(rax, access_flags);
        __ testl(rax, JVM_ACC_SYNCHRONIZED);
        __ jcc(Assembler::zero, L);
        __ stop("method needs synchronization");
        __ bind(L);
      }
#endif
  }

  // start execution
#ifdef ASSERT
  { Label L;
     const Address monitor_block_top (rbp,
                 frame::interpreter_frame_monitor_block_top_offset * wordSize);
    __ movptr(rax, monitor_block_top);
    __ cmpptr(rax, rsp);
    __ jcc(Assembler::equal, L);
    __ stop("broken stack frame setup in interpreter");
    __ bind(L);
  }
#endif

  // jvmti support
  __ notify_method_entry();

  __ dispatch_next(vtos);

  // invocation counter overflow
  if (inc_counter) {
    if (ProfileInterpreter) {
      // We have decided to profile this method in the interpreter
      __ bind(profile_method);

      __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::profile_method), rsi, true);

      __ movptr(rbx, Address(rbp, method_offset));   // restore methodOop
      __ movptr(rax, Address(rbx, in_bytes(methodOopDesc::method_data_offset())));
      __ movptr(Address(rbp, frame::interpreter_frame_mdx_offset * wordSize), rax);
      __ test_method_data_pointer(rax, profile_method_continue);
      __ addptr(rax, in_bytes(methodDataOopDesc::data_offset()));
      __ movptr(Address(rbp, frame::interpreter_frame_mdx_offset * wordSize), rax);
      __ jmp(profile_method_continue);
    }
    // Handle overflow of counter and compile method
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(&continue_after_compile);
  }

  return entry_point;
}

//------------------------------------------------------------------------------------------------------------------------
// Entry points
//
// Here we generate the various kind of entries into the interpreter.
// The two main entry type are generic bytecode methods and native call method.
// These both come in synchronized and non-synchronized versions but the
// frame layout they create is very similar. The other method entry
// types are really just special purpose entries that are really entry
// and interpretation all in one. These are for trivial methods like
// accessor, empty, or special math methods.
//
// When control flow reaches any of the entry types for the interpreter
// the following holds ->
//
// Arguments:
//
// rbx,: methodOop
// rcx: receiver
//
//
// Stack layout immediately at entry
//
// [ return address     ] <--- rsp
// [ parameter n        ]
//   ...
// [ parameter 1        ]
// [ expression stack   ] (caller's java expression stack)

// Assuming that we don't go to one of the trivial specialized
// entries the stack will look like below when we are ready to execute
// the first bytecode (or call the native routine). The register usage
// will be as the template based interpreter expects (see interpreter_x86.hpp).
//
// local variables follow incoming parameters immediately; i.e.
// the return address is moved to the end of the locals).
//
// [ monitor entry      ] <--- rsp
//   ...
// [ monitor entry      ]
// [ expr. stack bottom ]
// [ saved rsi          ]
// [ current rdi        ]
// [ methodOop          ]
// [ saved rbp,          ] <--- rbp,
// [ return address     ]
// [ local variable m   ]
//   ...
// [ local variable 1   ]
// [ parameter n        ]
//   ...
// [ parameter 1        ] <--- rdi

address AbstractInterpreterGenerator::generate_method_entry(AbstractInterpreter::MethodKind kind) {
  // determine code generation flags
  bool synchronized = false;
  address entry_point = NULL;

  switch (kind) {
    case Interpreter::zerolocals             :                                                                             break;
    case Interpreter::zerolocals_synchronized: synchronized = true;                                                        break;
    case Interpreter::native                 : entry_point = ((InterpreterGenerator*)this)->generate_native_entry(false);  break;
    case Interpreter::native_synchronized    : entry_point = ((InterpreterGenerator*)this)->generate_native_entry(true);   break;
    case Interpreter::empty                  : entry_point = ((InterpreterGenerator*)this)->generate_empty_entry();        break;
    case Interpreter::accessor               : entry_point = ((InterpreterGenerator*)this)->generate_accessor_entry();     break;
    case Interpreter::abstract               : entry_point = ((InterpreterGenerator*)this)->generate_abstract_entry();     break;
    case Interpreter::method_handle          : entry_point = ((InterpreterGenerator*)this)->generate_method_handle_entry(); break;

    case Interpreter::java_lang_math_sin     : // fall thru
    case Interpreter::java_lang_math_cos     : // fall thru
    case Interpreter::java_lang_math_tan     : // fall thru
    case Interpreter::java_lang_math_abs     : // fall thru
    case Interpreter::java_lang_math_log     : // fall thru
    case Interpreter::java_lang_math_log10   : // fall thru
    case Interpreter::java_lang_math_sqrt    : entry_point = ((InterpreterGenerator*)this)->generate_math_entry(kind);     break;
    default                                  : ShouldNotReachHere();                                                       break;
  }

  if (entry_point) return entry_point;

  return ((InterpreterGenerator*)this)->generate_normal_entry(synchronized);

}

// How much stack a method activation needs in words.
int AbstractInterpreter::size_top_interpreter_activation(methodOop method) {

  const int stub_code = 4;  // see generate_call_stub
  // Save space for one monitor to get into the interpreted method in case
  // the method is synchronized
  int monitor_size    = method->is_synchronized() ?
                                1*frame::interpreter_frame_monitor_size() : 0;

  // total overhead size: entry_size + (saved rbp, thru expr stack bottom).
  // be sure to change this if you add/subtract anything to/from the overhead area
  const int overhead_size = -frame::interpreter_frame_initial_sp_offset;

  const int extra_stack = methodOopDesc::extra_stack_entries();
  const int method_stack = (method->max_locals() + method->max_stack() + extra_stack) *
                           Interpreter::stackElementWords();
  return overhead_size + method_stack + stub_code;
}

// asm based interpreter deoptimization helpers

int AbstractInterpreter::layout_activation(methodOop method,
                                           int tempcount,
                                           int popframe_extra_args,
                                           int moncount,
                                           int callee_param_count,
                                           int callee_locals,
                                           frame* caller,
                                           frame* interpreter_frame,
                                           bool is_top_frame) {
  // Note: This calculation must exactly parallel the frame setup
  // in AbstractInterpreterGenerator::generate_method_entry.
  // If interpreter_frame!=NULL, set up the method, locals, and monitors.
  // The frame interpreter_frame, if not NULL, is guaranteed to be the right size,
  // as determined by a previous call to this method.
  // It is also guaranteed to be walkable even though it is in a skeletal state
  // NOTE: return size is in words not bytes

  // fixed size of an interpreter frame:
  int max_locals = method->max_locals() * Interpreter::stackElementWords();
  int extra_locals = (method->max_locals() - method->size_of_parameters()) *
                     Interpreter::stackElementWords();

  int overhead = frame::sender_sp_offset - frame::interpreter_frame_initial_sp_offset;

  // Our locals were accounted for by the caller (or last_frame_adjust on the transistion)
  // Since the callee parameters already account for the callee's params we only need to account for
  // the extra locals.


  int size = overhead +
         ((callee_locals - callee_param_count)*Interpreter::stackElementWords()) +
         (moncount*frame::interpreter_frame_monitor_size()) +
         tempcount*Interpreter::stackElementWords() + popframe_extra_args;

  if (interpreter_frame != NULL) {
#ifdef ASSERT
    assert(caller->unextended_sp() == interpreter_frame->interpreter_frame_sender_sp(), "Frame not properly walkable");
    assert(caller->sp() == interpreter_frame->sender_sp(), "Frame not properly walkable(2)");
#endif

    interpreter_frame->interpreter_frame_set_method(method);
    // NOTE the difference in using sender_sp and interpreter_frame_sender_sp
    // interpreter_frame_sender_sp is the original sp of the caller (the unextended_sp)
    // and sender_sp is fp+8
    intptr_t* locals = interpreter_frame->sender_sp() + max_locals - 1;

    interpreter_frame->interpreter_frame_set_locals(locals);
    BasicObjectLock* montop = interpreter_frame->interpreter_frame_monitor_begin();
    BasicObjectLock* monbot = montop - moncount;
    interpreter_frame->interpreter_frame_set_monitor_end(monbot);

    // Set last_sp
    intptr_t*  rsp = (intptr_t*) monbot  -
                     tempcount*Interpreter::stackElementWords() -
                     popframe_extra_args;
    interpreter_frame->interpreter_frame_set_last_sp(rsp);

    // All frames but the initial (oldest) interpreter frame we fill in have a
    // value for sender_sp that allows walking the stack but isn't
    // truly correct. Correct the value here.

    if (extra_locals != 0 &&
        interpreter_frame->sender_sp() == interpreter_frame->interpreter_frame_sender_sp() ) {
      interpreter_frame->set_interpreter_frame_sender_sp(caller->sp() + extra_locals);
    }
    *interpreter_frame->interpreter_frame_cache_addr() =
      method->constants()->cache();
  }
  return size;
}


//------------------------------------------------------------------------------------------------------------------------
// Exceptions

void TemplateInterpreterGenerator::generate_throw_exception() {
  // Entry point in previous activation (i.e., if the caller was interpreted)
  Interpreter::_rethrow_exception_entry = __ pc();

  // Restore sp to interpreter_frame_last_sp even though we are going
  // to empty the expression stack for the exception processing.
  __ movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD);
  // rax,: exception
  // rdx: return address/pc that threw exception
  __ restore_bcp();                              // rsi points to call/send
  __ restore_locals();

  // Entry point for exceptions thrown within interpreter code
  Interpreter::_throw_exception_entry = __ pc();
  // expression stack is undefined here
  // rax,: exception
  // rsi: exception bcp
  __ verify_oop(rax);

  // expression stack must be empty before entering the VM in case of an exception
  __ empty_expression_stack();
  __ empty_FPU_stack();
  // find exception handler address and preserve exception oop
  __ call_VM(rdx, CAST_FROM_FN_PTR(address, InterpreterRuntime::exception_handler_for_exception), rax);
  // rax,: exception handler entry point
  // rdx: preserved exception oop
  // rsi: bcp for exception handler
  __ push_ptr(rdx);                              // push exception which is now the only value on the stack
  __ jmp(rax);                                   // jump to exception handler (may be _remove_activation_entry!)

  // If the exception is not handled in the current frame the frame is removed and
  // the exception is rethrown (i.e. exception continuation is _rethrow_exception).
  //
  // Note: At this point the bci is still the bxi for the instruction which caused
  //       the exception and the expression stack is empty. Thus, for any VM calls
  //       at this point, GC will find a legal oop map (with empty expression stack).

  // In current activation
  // tos: exception
  // rsi: exception bcp

  //
  // JVMTI PopFrame support
  //

   Interpreter::_remove_activation_preserving_args_entry = __ pc();
  __ empty_expression_stack();
  __ empty_FPU_stack();
  // Set the popframe_processing bit in pending_popframe_condition indicating that we are
  // currently handling popframe, so that call_VMs that may happen later do not trigger new
  // popframe handling cycles.
  __ get_thread(rcx);
  __ movl(rdx, Address(rcx, JavaThread::popframe_condition_offset()));
  __ orl(rdx, JavaThread::popframe_processing_bit);
  __ movl(Address(rcx, JavaThread::popframe_condition_offset()), rdx);

  {
    // Check to see whether we are returning to a deoptimized frame.
    // (The PopFrame call ensures that the caller of the popped frame is
    // either interpreted or compiled and deoptimizes it if compiled.)
    // In this case, we can't call dispatch_next() after the frame is
    // popped, but instead must save the incoming arguments and restore
    // them after deoptimization has occurred.
    //
    // Note that we don't compare the return PC against the
    // deoptimization blob's unpack entry because of the presence of
    // adapter frames in C2.
    Label caller_not_deoptimized;
    __ movptr(rdx, Address(rbp, frame::return_addr_offset * wordSize));
    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::interpreter_contains), rdx);
    __ testl(rax, rax);
    __ jcc(Assembler::notZero, caller_not_deoptimized);

    // Compute size of arguments for saving when returning to deoptimized caller
    __ get_method(rax);
    __ verify_oop(rax);
    __ load_unsigned_short(rax, Address(rax, in_bytes(methodOopDesc::size_of_parameters_offset())));
    __ shlptr(rax, Interpreter::logStackElementSize());
    __ restore_locals();
    __ subptr(rdi, rax);
    __ addptr(rdi, wordSize);
    // Save these arguments
    __ get_thread(rcx);
    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, Deoptimization::popframe_preserve_args), rcx, rax, rdi);

    __ remove_activation(vtos, rdx,
                         /* throw_monitor_exception */ false,
                         /* install_monitor_exception */ false,
                         /* notify_jvmdi */ false);

    // Inform deoptimization that it is responsible for restoring these arguments
    __ get_thread(rcx);
    __ movl(Address(rcx, JavaThread::popframe_condition_offset()), JavaThread::popframe_force_deopt_reexecution_bit);

    // Continue in deoptimization handler
    __ jmp(rdx);

    __ bind(caller_not_deoptimized);
  }

  __ remove_activation(vtos, rdx,
                       /* throw_monitor_exception */ false,
                       /* install_monitor_exception */ false,
                       /* notify_jvmdi */ false);

  // Finish with popframe handling
  // A previous I2C followed by a deoptimization might have moved the
  // outgoing arguments further up the stack. PopFrame expects the
  // mutations to those outgoing arguments to be preserved and other
  // constraints basically require this frame to look exactly as
  // though it had previously invoked an interpreted activation with
  // no space between the top of the expression stack (current
  // last_sp) and the top of stack. Rather than force deopt to
  // maintain this kind of invariant all the time we call a small
  // fixup routine to move the mutated arguments onto the top of our
  // expression stack if necessary.
  __ mov(rax, rsp);
  __ movptr(rbx, Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize));
  __ get_thread(rcx);
  // PC must point into interpreter here
  __ set_last_Java_frame(rcx, noreg, rbp, __ pc());
  __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::popframe_move_outgoing_args), rcx, rax, rbx);
  __ get_thread(rcx);
  __ reset_last_Java_frame(rcx, true, true);
  // Restore the last_sp and null it out
  __ movptr(rsp, Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize));
  __ movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD);

  __ restore_bcp();
  __ restore_locals();
  // The method data pointer was incremented already during
  // call profiling. We have to restore the mdp for the current bcp.
  if (ProfileInterpreter) {
    __ set_method_data_pointer_for_bcp();
  }

  // Clear the popframe condition flag
  __ get_thread(rcx);
  __ movl(Address(rcx, JavaThread::popframe_condition_offset()), JavaThread::popframe_inactive);

  __ dispatch_next(vtos);
  // end of PopFrame support

  Interpreter::_remove_activation_entry = __ pc();

  // preserve exception over this code sequence
  __ pop_ptr(rax);
  __ get_thread(rcx);
  __ movptr(Address(rcx, JavaThread::vm_result_offset()), rax);
  // remove the activation (without doing throws on illegalMonitorExceptions)
  __ remove_activation(vtos, rdx, false, true, false);
  // restore exception
  __ get_thread(rcx);
  __ movptr(rax, Address(rcx, JavaThread::vm_result_offset()));
  __ movptr(Address(rcx, JavaThread::vm_result_offset()), NULL_WORD);
  __ verify_oop(rax);

  // Inbetween activations - previous activation type unknown yet
  // compute continuation point - the continuation point expects
  // the following registers set up:
  //
  // rax,: exception
  // rdx: return address/pc that threw exception
  // rsp: expression stack of caller
  // rbp,: rbp, of caller
  __ push(rax);                                  // save exception
  __ push(rdx);                                  // save return address
  __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), rdx);
  __ mov(rbx, rax);                              // save exception handler
  __ pop(rdx);                                   // restore return address
  __ pop(rax);                                   // restore exception
  // Note that an "issuing PC" is actually the next PC after the call
  __ jmp(rbx);                                   // jump to exception handler of caller
}


//
// JVMTI ForceEarlyReturn support
//
address TemplateInterpreterGenerator::generate_earlyret_entry_for(TosState state) {
  address entry = __ pc();

  __ restore_bcp();
  __ restore_locals();
  __ empty_expression_stack();
  __ empty_FPU_stack();
  __ load_earlyret_value(state);

  __ get_thread(rcx);
  __ movptr(rcx, Address(rcx, JavaThread::jvmti_thread_state_offset()));
  const Address cond_addr(rcx, JvmtiThreadState::earlyret_state_offset());

  // Clear the earlyret state
  __ movl(cond_addr, JvmtiThreadState::earlyret_inactive);

  __ remove_activation(state, rsi,
                       false, /* throw_monitor_exception */
                       false, /* install_monitor_exception */
                       true); /* notify_jvmdi */
  __ jmp(rsi);
  return entry;
} // end of ForceEarlyReturn support


//------------------------------------------------------------------------------------------------------------------------
// Helper for vtos entry point generation

void TemplateInterpreterGenerator::set_vtos_entry_points (Template* t, address& bep, address& cep, address& sep, address& aep, address& iep, address& lep, address& fep, address& dep, address& vep) {
  assert(t->is_valid() && t->tos_in() == vtos, "illegal template");
  Label L;
  fep = __ pc(); __ push(ftos); __ jmp(L);
  dep = __ pc(); __ push(dtos); __ jmp(L);
  lep = __ pc(); __ push(ltos); __ jmp(L);
  aep = __ pc(); __ push(atos); __ jmp(L);
  bep = cep = sep =             // fall through
  iep = __ pc(); __ push(itos); // fall through
  vep = __ pc(); __ bind(L);    // fall through
  generate_and_dispatch(t);
}

//------------------------------------------------------------------------------------------------------------------------
// Generation of individual instructions

// helpers for generate_and_dispatch



InterpreterGenerator::InterpreterGenerator(StubQueue* code)
 : TemplateInterpreterGenerator(code) {
   generate_all(); // down here so it can be "virtual"
}

//------------------------------------------------------------------------------------------------------------------------

// Non-product code
#ifndef PRODUCT
address TemplateInterpreterGenerator::generate_trace_code(TosState state) {
  address entry = __ pc();

  // prepare expression stack
  __ pop(rcx);          // pop return address so expression stack is 'pure'
  __ push(state);       // save tosca

  // pass tosca registers as arguments & call tracer
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, SharedRuntime::trace_bytecode), rcx, rax, rdx);
  __ mov(rcx, rax);     // make sure return address is not destroyed by pop(state)
  __ pop(state);        // restore tosca

  // return
  __ jmp(rcx);

  return entry;
}


void TemplateInterpreterGenerator::count_bytecode() {
  __ incrementl(ExternalAddress((address) &BytecodeCounter::_counter_value));
}


void TemplateInterpreterGenerator::histogram_bytecode(Template* t) {
  __ incrementl(ExternalAddress((address) &BytecodeHistogram::_counters[t->bytecode()]));
}


void TemplateInterpreterGenerator::histogram_bytecode_pair(Template* t) {
  __ mov32(ExternalAddress((address) &BytecodePairHistogram::_index), rbx);
  __ shrl(rbx, BytecodePairHistogram::log2_number_of_codes);
  __ orl(rbx, ((int)t->bytecode()) << BytecodePairHistogram::log2_number_of_codes);
  ExternalAddress table((address) BytecodePairHistogram::_counters);
  Address index(noreg, rbx, Address::times_4);
  __ incrementl(ArrayAddress(table, index));
}


void TemplateInterpreterGenerator::trace_bytecode(Template* t) {
  // Call a little run-time stub to avoid blow-up for each bytecode.
  // The run-time runtime saves the right registers, depending on
  // the tosca in-state for the given template.
  assert(Interpreter::trace_code(t->tos_in()) != NULL,
         "entry must have been generated");
  __ call(RuntimeAddress(Interpreter::trace_code(t->tos_in())));
}


void TemplateInterpreterGenerator::stop_interpreter_at() {
  Label L;
  __ cmp32(ExternalAddress((address) &BytecodeCounter::_counter_value),
           StopInterpreterAt);
  __ jcc(Assembler::notEqual, L);
  __ int3();
  __ bind(L);
}
#endif // !PRODUCT
#endif // CC_INTERP
