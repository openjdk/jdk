/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/cppInterpreter.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterGenerator.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "oops/arrayOop.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#ifdef SHARK
#include "shark/shark_globals.hpp"
#endif

#ifdef CC_INTERP

// Routine exists to make tracebacks look decent in debugger
// while we are recursed in the frame manager/c++ interpreter.
// We could use an address in the frame manager but having
// frames look natural in the debugger is a plus.
extern "C" void RecursiveInterpreterActivation(interpreterState istate )
{
  //
  ShouldNotReachHere();
}


#define __ _masm->
#define STATE(field_name) (Address(state, byte_offset_of(BytecodeInterpreter, field_name)))

Label fast_accessor_slow_entry_path;  // fast accessor methods need to be able to jmp to unsynchronized
                                      // c++ interpreter entry point this holds that entry point label.

// default registers for state and sender_sp
// state and sender_sp are the same on 32bit because we have no choice.
// state could be rsi on 64bit but it is an arg reg and not callee save
// so r13 is better choice.

const Register state = NOT_LP64(rsi) LP64_ONLY(r13);
const Register sender_sp_on_entry = NOT_LP64(rsi) LP64_ONLY(r13);

// NEEDED for JVMTI?
// address AbstractInterpreter::_remove_activation_preserving_args_entry;

static address unctrap_frame_manager_entry  = NULL;

static address deopt_frame_manager_return_atos  = NULL;
static address deopt_frame_manager_return_btos  = NULL;
static address deopt_frame_manager_return_itos  = NULL;
static address deopt_frame_manager_return_ltos  = NULL;
static address deopt_frame_manager_return_ftos  = NULL;
static address deopt_frame_manager_return_dtos  = NULL;
static address deopt_frame_manager_return_vtos  = NULL;

int AbstractInterpreter::BasicType_as_index(BasicType type) {
  int i = 0;
  switch (type) {
    case T_BOOLEAN: i = 0; break;
    case T_CHAR   : i = 1; break;
    case T_BYTE   : i = 2; break;
    case T_SHORT  : i = 3; break;
    case T_INT    : i = 4; break;
    case T_VOID   : i = 5; break;
    case T_FLOAT  : i = 8; break;
    case T_LONG   : i = 9; break;
    case T_DOUBLE : i = 6; break;
    case T_OBJECT : // fall through
    case T_ARRAY  : i = 7; break;
    default       : ShouldNotReachHere();
  }
  assert(0 <= i && i < AbstractInterpreter::number_of_result_handlers, "index out of bounds");
  return i;
}

// Is this pc anywhere within code owned by the interpreter?
// This only works for pc that might possibly be exposed to frame
// walkers. It clearly misses all of the actual c++ interpreter
// implementation
bool CppInterpreter::contains(address pc)            {
    return (_code->contains(pc) ||
            pc == CAST_FROM_FN_PTR(address, RecursiveInterpreterActivation));
}


address CppInterpreterGenerator::generate_result_handler_for(BasicType type) {
  address entry = __ pc();
  switch (type) {
    case T_BOOLEAN: __ c2bool(rax);            break;
    case T_CHAR   : __ andl(rax, 0xFFFF);      break;
    case T_BYTE   : __ sign_extend_byte (rax); break;
    case T_SHORT  : __ sign_extend_short(rax); break;
    case T_VOID   : // fall thru
    case T_LONG   : // fall thru
    case T_INT    : /* nothing to do */        break;

    case T_DOUBLE :
    case T_FLOAT  :
      {
        const Register t = InterpreterRuntime::SignatureHandlerGenerator::temp();
        __ pop(t);                            // remove return address first
        // Must return a result for interpreter or compiler. In SSE
        // mode, results are returned in xmm0 and the FPU stack must
        // be empty.
        if (type == T_FLOAT && UseSSE >= 1) {
#ifndef _LP64
          // Load ST0
          __ fld_d(Address(rsp, 0));
          // Store as float and empty fpu stack
          __ fstp_s(Address(rsp, 0));
#endif // !_LP64
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
        __ push(t);                            // restore return address
      }
      break;
    case T_OBJECT :
      // retrieve result from frame
      __ movptr(rax, STATE(_oop_temp));
      // and verify it
      __ verify_oop(rax);
      break;
    default       : ShouldNotReachHere();
  }
  __ ret(0);                                   // return from result handler
  return entry;
}

// tosca based result to c++ interpreter stack based result.
// Result goes to top of native stack.

#undef EXTEND  // SHOULD NOT BE NEEDED
address CppInterpreterGenerator::generate_tosca_to_stack_converter(BasicType type) {
  // A result is in the tosca (abi result) from either a native method call or compiled
  // code. Place this result on the java expression stack so C++ interpreter can use it.
  address entry = __ pc();

  const Register t = InterpreterRuntime::SignatureHandlerGenerator::temp();
  __ pop(t);                            // remove return address first
  switch (type) {
    case T_VOID:
       break;
    case T_BOOLEAN:
#ifdef EXTEND
      __ c2bool(rax);
#endif
      __ push(rax);
      break;
    case T_CHAR   :
#ifdef EXTEND
      __ andl(rax, 0xFFFF);
#endif
      __ push(rax);
      break;
    case T_BYTE   :
#ifdef EXTEND
      __ sign_extend_byte (rax);
#endif
      __ push(rax);
      break;
    case T_SHORT  :
#ifdef EXTEND
      __ sign_extend_short(rax);
#endif
      __ push(rax);
      break;
    case T_LONG    :
      __ push(rdx);                             // pushes useless junk on 64bit
      __ push(rax);
      break;
    case T_INT    :
      __ push(rax);
      break;
    case T_FLOAT  :
      // Result is in ST(0)/xmm0
      __ subptr(rsp, wordSize);
      if ( UseSSE < 1) {
        __ fstp_s(Address(rsp, 0));
      } else {
        __ movflt(Address(rsp, 0), xmm0);
      }
      break;
    case T_DOUBLE  :
      __ subptr(rsp, 2*wordSize);
      if ( UseSSE < 2 ) {
        __ fstp_d(Address(rsp, 0));
      } else {
        __ movdbl(Address(rsp, 0), xmm0);
      }
      break;
    case T_OBJECT :
      __ verify_oop(rax);                      // verify it
      __ push(rax);
      break;
    default       : ShouldNotReachHere();
  }
  __ jmp(t);                                   // return from result handler
  return entry;
}

address CppInterpreterGenerator::generate_stack_to_stack_converter(BasicType type) {
  // A result is in the java expression stack of the interpreted method that has just
  // returned. Place this result on the java expression stack of the caller.
  //
  // The current interpreter activation in rsi/r13 is for the method just returning its
  // result. So we know that the result of this method is on the top of the current
  // execution stack (which is pre-pushed) and will be return to the top of the caller
  // stack. The top of the callers stack is the bottom of the locals of the current
  // activation.
  // Because of the way activation are managed by the frame manager the value of rsp is
  // below both the stack top of the current activation and naturally the stack top
  // of the calling activation. This enable this routine to leave the return address
  // to the frame manager on the stack and do a vanilla return.
  //
  // On entry: rsi/r13 - interpreter state of activation returning a (potential) result
  // On Return: rsi/r13 - unchanged
  //            rax - new stack top for caller activation (i.e. activation in _prev_link)
  //
  // Can destroy rdx, rcx.
  //

  address entry = __ pc();
  const Register t = InterpreterRuntime::SignatureHandlerGenerator::temp();
  switch (type) {
    case T_VOID:
      __ movptr(rax, STATE(_locals));                                   // pop parameters get new stack value
      __ addptr(rax, wordSize);                                         // account for prepush before we return
      break;
    case T_FLOAT  :
    case T_BOOLEAN:
    case T_CHAR   :
    case T_BYTE   :
    case T_SHORT  :
    case T_INT    :
      // 1 word result
      __ movptr(rdx, STATE(_stack));
      __ movptr(rax, STATE(_locals));                                   // address for result
      __ movl(rdx, Address(rdx, wordSize));                             // get result
      __ movptr(Address(rax, 0), rdx);                                  // and store it
      break;
    case T_LONG    :
    case T_DOUBLE  :
      // return top two words on current expression stack to caller's expression stack
      // The caller's expression stack is adjacent to the current frame manager's intepretState
      // except we allocated one extra word for this intepretState so we won't overwrite it
      // when we return a two word result.

      __ movptr(rax, STATE(_locals));                                   // address for result
      __ movptr(rcx, STATE(_stack));
      __ subptr(rax, wordSize);                                         // need addition word besides locals[0]
      __ movptr(rdx, Address(rcx, 2*wordSize));                         // get result word (junk in 64bit)
      __ movptr(Address(rax, wordSize), rdx);                           // and store it
      __ movptr(rdx, Address(rcx, wordSize));                           // get result word
      __ movptr(Address(rax, 0), rdx);                                  // and store it
      break;
    case T_OBJECT :
      __ movptr(rdx, STATE(_stack));
      __ movptr(rax, STATE(_locals));                                   // address for result
      __ movptr(rdx, Address(rdx, wordSize));                           // get result
      __ verify_oop(rdx);                                               // verify it
      __ movptr(Address(rax, 0), rdx);                                  // and store it
      break;
    default       : ShouldNotReachHere();
  }
  __ ret(0);
  return entry;
}

address CppInterpreterGenerator::generate_stack_to_native_abi_converter(BasicType type) {
  // A result is in the java expression stack of the interpreted method that has just
  // returned. Place this result in the native abi that the caller expects.
  //
  // Similar to generate_stack_to_stack_converter above. Called at a similar time from the
  // frame manager execept in this situation the caller is native code (c1/c2/call_stub)
  // and so rather than return result onto caller's java expression stack we return the
  // result in the expected location based on the native abi.
  // On entry: rsi/r13 - interpreter state of activation returning a (potential) result
  // On Return: rsi/r13 - unchanged
  // Other registers changed [rax/rdx/ST(0) as needed for the result returned]

  address entry = __ pc();
  switch (type) {
    case T_VOID:
       break;
    case T_BOOLEAN:
    case T_CHAR   :
    case T_BYTE   :
    case T_SHORT  :
    case T_INT    :
      __ movptr(rdx, STATE(_stack));                                    // get top of stack
      __ movl(rax, Address(rdx, wordSize));                             // get result word 1
      break;
    case T_LONG    :
      __ movptr(rdx, STATE(_stack));                                    // get top of stack
      __ movptr(rax, Address(rdx, wordSize));                           // get result low word
      NOT_LP64(__ movl(rdx, Address(rdx, 2*wordSize));)                 // get result high word
      break;
    case T_FLOAT  :
      __ movptr(rdx, STATE(_stack));                                    // get top of stack
      if ( UseSSE >= 1) {
        __ movflt(xmm0, Address(rdx, wordSize));
      } else {
        __ fld_s(Address(rdx, wordSize));                               // pushd float result
      }
      break;
    case T_DOUBLE  :
      __ movptr(rdx, STATE(_stack));                                    // get top of stack
      if ( UseSSE > 1) {
        __ movdbl(xmm0, Address(rdx, wordSize));
      } else {
        __ fld_d(Address(rdx, wordSize));                               // push double result
      }
      break;
    case T_OBJECT :
      __ movptr(rdx, STATE(_stack));                                    // get top of stack
      __ movptr(rax, Address(rdx, wordSize));                           // get result word 1
      __ verify_oop(rax);                                               // verify it
      break;
    default       : ShouldNotReachHere();
  }
  __ ret(0);
  return entry;
}

address CppInterpreter::return_entry(TosState state, int length) {
  // make it look good in the debugger
  return CAST_FROM_FN_PTR(address, RecursiveInterpreterActivation);
}

address CppInterpreter::deopt_entry(TosState state, int length) {
  address ret = NULL;
  if (length != 0) {
    switch (state) {
      case atos: ret = deopt_frame_manager_return_atos; break;
      case btos: ret = deopt_frame_manager_return_btos; break;
      case ctos:
      case stos:
      case itos: ret = deopt_frame_manager_return_itos; break;
      case ltos: ret = deopt_frame_manager_return_ltos; break;
      case ftos: ret = deopt_frame_manager_return_ftos; break;
      case dtos: ret = deopt_frame_manager_return_dtos; break;
      case vtos: ret = deopt_frame_manager_return_vtos; break;
    }
  } else {
    ret = unctrap_frame_manager_entry;  // re-execute the bytecode ( e.g. uncommon trap)
  }
  assert(ret != NULL, "Not initialized");
  return ret;
}

// C++ Interpreter
void CppInterpreterGenerator::generate_compute_interpreter_state(const Register state,
                                                                 const Register locals,
                                                                 const Register sender_sp,
                                                                 bool native) {

  // On entry the "locals" argument points to locals[0] (or where it would be in case no locals in
  // a static method). "state" contains any previous frame manager state which we must save a link
  // to in the newly generated state object. On return "state" is a pointer to the newly allocated
  // state object. We must allocate and initialize a new interpretState object and the method
  // expression stack. Because the returned result (if any) of the method will be placed on the caller's
  // expression stack and this will overlap with locals[0] (and locals[1] if double/long) we must
  // be sure to leave space on the caller's stack so that this result will not overwrite values when
  // locals[0] and locals[1] do not exist (and in fact are return address and saved rbp). So when
  // we are non-native we in essence ensure that locals[0-1] exist. We play an extra trick in
  // non-product builds and initialize this last local with the previous interpreterState as
  // this makes things look real nice in the debugger.

  // State on entry
  // Assumes locals == &locals[0]
  // Assumes state == any previous frame manager state (assuming call path from c++ interpreter)
  // Assumes rax = return address
  // rcx == senders_sp
  // rbx == method
  // Modifies rcx, rdx, rax
  // Returns:
  // state == address of new interpreterState
  // rsp == bottom of method's expression stack.

  const Address const_offset      (rbx, Method::const_offset());


  // On entry sp is the sender's sp. This includes the space for the arguments
  // that the sender pushed. If the sender pushed no args (a static) and the
  // caller returns a long then we need two words on the sender's stack which
  // are not present (although when we return a restore full size stack the
  // space will be present). If we didn't allocate two words here then when
  // we "push" the result of the caller's stack we would overwrite the return
  // address and the saved rbp. Not good. So simply allocate 2 words now
  // just to be safe. This is the "static long no_params() method" issue.
  // See Lo.java for a testcase.
  // We don't need this for native calls because they return result in
  // register and the stack is expanded in the caller before we store
  // the results on the stack.

  if (!native) {
#ifdef PRODUCT
    __ subptr(rsp, 2*wordSize);
#else /* PRODUCT */
    __ push((int32_t)NULL_WORD);
    __ push(state);                         // make it look like a real argument
#endif /* PRODUCT */
  }

  // Now that we are assure of space for stack result, setup typical linkage

  __ push(rax);
  __ enter();

  __ mov(rax, state);                                  // save current state

  __ lea(rsp, Address(rsp, -(int)sizeof(BytecodeInterpreter)));
  __ mov(state, rsp);

  // rsi/r13 == state/locals rax == prevstate

  // initialize the "shadow" frame so that use since C++ interpreter not directly
  // recursive. Simpler to recurse but we can't trim expression stack as we call
  // new methods.
  __ movptr(STATE(_locals), locals);                    // state->_locals = locals()
  __ movptr(STATE(_self_link), state);                  // point to self
  __ movptr(STATE(_prev_link), rax);                    // state->_link = state on entry (NULL or previous state)
  __ movptr(STATE(_sender_sp), sender_sp);              // state->_sender_sp = sender_sp
#ifdef _LP64
  __ movptr(STATE(_thread), r15_thread);                // state->_bcp = codes()
#else
  __ get_thread(rax);                                   // get vm's javathread*
  __ movptr(STATE(_thread), rax);                       // state->_bcp = codes()
#endif // _LP64
  __ movptr(rdx, Address(rbx, Method::const_offset())); // get constantMethodOop
  __ lea(rdx, Address(rdx, ConstMethod::codes_offset())); // get code base
  if (native) {
    __ movptr(STATE(_bcp), (int32_t)NULL_WORD);         // state->_bcp = NULL
  } else {
    __ movptr(STATE(_bcp), rdx);                        // state->_bcp = codes()
  }
  __ xorptr(rdx, rdx);
  __ movptr(STATE(_oop_temp), rdx);                     // state->_oop_temp = NULL (only really needed for native)
  __ movptr(STATE(_mdx), rdx);                          // state->_mdx = NULL
  __ movptr(rdx, Address(rbx, Method::const_offset()));
  __ movptr(rdx, Address(rdx, ConstMethod::constants_offset()));
  __ movptr(rdx, Address(rdx, ConstantPool::cache_offset_in_bytes()));
  __ movptr(STATE(_constants), rdx);                    // state->_constants = constants()

  __ movptr(STATE(_method), rbx);                       // state->_method = method()
  __ movl(STATE(_msg), (int32_t) BytecodeInterpreter::method_entry);   // state->_msg = initial method entry
  __ movptr(STATE(_result._to_call._callee), (int32_t) NULL_WORD); // state->_result._to_call._callee_callee = NULL


  __ movptr(STATE(_monitor_base), rsp);                 // set monitor block bottom (grows down) this would point to entry [0]
                                                        // entries run from -1..x where &monitor[x] ==

  {
    // Must not attempt to lock method until we enter interpreter as gc won't be able to find the
    // initial frame. However we allocate a free monitor so we don't have to shuffle the expression stack
    // immediately.

    // synchronize method
    const Address access_flags      (rbx, Method::access_flags_offset());
    const int entry_size            = frame::interpreter_frame_monitor_size() * wordSize;
    Label not_synced;

    __ movl(rax, access_flags);
    __ testl(rax, JVM_ACC_SYNCHRONIZED);
    __ jcc(Assembler::zero, not_synced);

    // Allocate initial monitor and pre initialize it
    // get synchronization object

    Label done;
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());
    __ movl(rax, access_flags);
    __ testl(rax, JVM_ACC_STATIC);
    __ movptr(rax, Address(locals, 0));                   // get receiver (assume this is frequent case)
    __ jcc(Assembler::zero, done);
    __ movptr(rax, Address(rbx, Method::const_offset()));
    __ movptr(rax, Address(rax, ConstMethod::constants_offset()));
    __ movptr(rax, Address(rax, ConstantPool::pool_holder_offset_in_bytes()));
    __ movptr(rax, Address(rax, mirror_offset));
    __ bind(done);
    // add space for monitor & lock
    __ subptr(rsp, entry_size);                                           // add space for a monitor entry
    __ movptr(Address(rsp, BasicObjectLock::obj_offset_in_bytes()), rax); // store object
    __ bind(not_synced);
  }

  __ movptr(STATE(_stack_base), rsp);                                     // set expression stack base ( == &monitors[-count])
  if (native) {
    __ movptr(STATE(_stack), rsp);                                        // set current expression stack tos
    __ movptr(STATE(_stack_limit), rsp);
  } else {
    __ subptr(rsp, wordSize);                                             // pre-push stack
    __ movptr(STATE(_stack), rsp);                                        // set current expression stack tos

    // compute full expression stack limit

    __ movptr(rdx, Address(rbx, Method::const_offset()));
    __ load_unsigned_short(rdx, Address(rdx, ConstMethod::max_stack_offset())); // get size of expression stack in words
    __ negptr(rdx);                                                       // so we can subtract in next step
    // Allocate expression stack
    __ lea(rsp, Address(rsp, rdx, Address::times_ptr, -Method::extra_stack_words()));
    __ movptr(STATE(_stack_limit), rsp);
  }

#ifdef _LP64
  // Make sure stack is properly aligned and sized for the abi
  __ subptr(rsp, frame::arg_reg_save_area_bytes); // windows
  __ andptr(rsp, -16); // must be 16 byte boundary (see amd64 ABI)
#endif // _LP64



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
  Label done;
  const Address invocation_counter(rax,
                MethodCounters::invocation_counter_offset() +
                InvocationCounter::counter_offset());
  const Address backedge_counter  (rax,
                MethodCounter::backedge_counter_offset() +
                InvocationCounter::counter_offset());

  __ get_method_counters(rbx, rax, done);

  if (ProfileInterpreter) {
    __ incrementl(Address(rax,
            MethodCounters::interpreter_invocation_counter_offset()));
  }
  // Update standard invocation counters
  __ movl(rcx, invocation_counter);
  __ increment(rcx, InvocationCounter::count_increment);
  __ movl(invocation_counter, rcx);             // save invocation count

  __ movl(rax, backedge_counter);               // load backedge counter
  __ andl(rax, InvocationCounter::count_mask_value);  // mask out the status bits

  __ addl(rcx, rax);                            // add both counters

  // profile_method is non-null only for interpreted method so
  // profile_method != NULL == !native_call
  // BytecodeInterpreter only calls for native so code is elided.

  __ cmp32(rcx,
           ExternalAddress((address)&InvocationCounter::InterpreterInvocationLimit));
  __ jcc(Assembler::aboveEqual, *overflow);
  __ bind(done);
}

void InterpreterGenerator::generate_counter_overflow(Label* do_continue) {

  // C++ interpreter on entry
  // rsi/r13 - new interpreter state pointer
  // rbp - interpreter frame pointer
  // rbx - method

  // On return (i.e. jump to entry_point) [ back to invocation of interpreter ]
  // rbx, - method
  // rcx - rcvr (assuming there is one)
  // top of stack return address of interpreter caller
  // rsp - sender_sp

  // C++ interpreter only
  // rsi/r13 - previous interpreter state pointer

  // InterpreterRuntime::frequency_counter_overflow takes one argument
  // indicating if the counter overflow occurs at a backwards branch (non-NULL bcp).
  // The call returns the address of the verified entry point for the method or NULL
  // if the compilation did not complete (either went background or bailed out).
  __ movptr(rax, (int32_t)false);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow), rax);

  // for c++ interpreter can rsi really be munged?
  __ lea(state, Address(rbp, -(int)sizeof(BytecodeInterpreter)));                               // restore state
  __ movptr(rbx, Address(state, byte_offset_of(BytecodeInterpreter, _method)));            // restore method
  __ movptr(rdi, Address(state, byte_offset_of(BytecodeInterpreter, _locals)));            // get locals pointer

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
  // rbx,: Method*

  // C++ Interpreter
  // rsi/r13: previous interpreter frame state object
  // rdi: &locals[0]
  // rcx: # of locals
  // rdx: number of additional locals this frame needs (what we must check)
  // rbx: Method*

  // destroyed on exit
  // rax,

  // NOTE:  since the additional locals are also always pushed (wasn't obvious in
  // generate_method_entry) so the guard should work for them too.
  //

  // monitor entry size: see picture of stack set (generate_method_entry) and frame_i486.hpp
  const int entry_size    = frame::interpreter_frame_monitor_size() * wordSize;

  // total overhead size: entry_size + (saved rbp, thru expr stack bottom).
  // be sure to change this if you add/subtract anything to/from the overhead area
  const int overhead_size = (int)sizeof(BytecodeInterpreter);

  const int page_size = os::vm_page_size();

  Label after_frame_check;

  // compute rsp as if this were going to be the last frame on
  // the stack before the red zone

  Label after_frame_check_pop;

  // save rsi == caller's bytecode ptr (c++ previous interp. state)
  // QQQ problem here?? rsi overload????
  __ push(state);

  const Register thread = LP64_ONLY(r15_thread) NOT_LP64(rsi);

  NOT_LP64(__ get_thread(thread));

  const Address stack_base(thread, Thread::stack_base_offset());
  const Address stack_size(thread, Thread::stack_size_offset());

  // locals + overhead, in bytes
  // Always give one monitor to allow us to start interp if sync method.
  // Any additional monitors need a check when moving the expression stack
  const int one_monitor = frame::interpreter_frame_monitor_size() * wordSize;
  __ movptr(rax, Address(rbx, Method::const_offset()));
  __ load_unsigned_short(rax, Address(rax, ConstMethod::max_stack_offset())); // get size of expression stack in words
  __ lea(rax, Address(noreg, rax, Interpreter::stackElementScale(), one_monitor+Method::extra_stack_words()));
  __ lea(rax, Address(rax, rdx, Interpreter::stackElementScale(), overhead_size));

#ifdef ASSERT
  Label stack_base_okay, stack_size_okay;
  // verify that thread stack base is non-zero
  __ cmpptr(stack_base, (int32_t)0);
  __ jcc(Assembler::notEqual, stack_base_okay);
  __ stop("stack base is zero");
  __ bind(stack_base_okay);
  // verify that thread stack size is non-zero
  __ cmpptr(stack_size, (int32_t)0);
  __ jcc(Assembler::notEqual, stack_size_okay);
  __ stop("stack size is zero");
  __ bind(stack_size_okay);
#endif

  // Add stack base to locals and subtract stack size
  __ addptr(rax, stack_base);
  __ subptr(rax, stack_size);

  // We should have a magic number here for the size of the c++ interpreter frame.
  // We can't actually tell this ahead of time. The debug version size is around 3k
  // product is 1k and fastdebug is 4k
  const int slop = 6 * K;

  // Use the maximum number of pages we might bang.
  const int max_pages = StackShadowPages > (StackRedPages+StackYellowPages) ? StackShadowPages :
                                                                              (StackRedPages+StackYellowPages);
  // Only need this if we are stack banging which is temporary while
  // we're debugging.
  __ addptr(rax, slop + 2*max_pages * page_size);

  // check against the current stack bottom
  __ cmpptr(rsp, rax);
  __ jcc(Assembler::above, after_frame_check_pop);

  __ pop(state);  //  get c++ prev state.

     // throw exception return address becomes throwing pc
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_StackOverflowError));

  // all done with frame size check
  __ bind(after_frame_check_pop);
  __ pop(state);

  __ bind(after_frame_check);
}

// Find preallocated  monitor and lock method (C++ interpreter)
// rbx - Method*
//
void InterpreterGenerator::lock_method(void) {
  // assumes state == rsi/r13 == pointer to current interpreterState
  // minimally destroys rax, rdx|c_rarg1, rdi
  //
  // synchronize method
  const int entry_size            = frame::interpreter_frame_monitor_size() * wordSize;
  const Address access_flags      (rbx, Method::access_flags_offset());

  const Register monitor  = NOT_LP64(rdx) LP64_ONLY(c_rarg1);

  // find initial monitor i.e. monitors[-1]
  __ movptr(monitor, STATE(_monitor_base));                                   // get monitor bottom limit
  __ subptr(monitor, entry_size);                                             // point to initial monitor

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
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());
    __ movl(rax, access_flags);
    __ movptr(rdi, STATE(_locals));                                     // prepare to get receiver (assume common case)
    __ testl(rax, JVM_ACC_STATIC);
    __ movptr(rax, Address(rdi, 0));                                    // get receiver (assume this is frequent case)
    __ jcc(Assembler::zero, done);
    __ movptr(rax, Address(rbx, Method::const_offset()));
    __ movptr(rax, Address(rax, ConstMethod::constants_offset()));
    __ movptr(rax, Address(rax, ConstantPool::pool_holder_offset_in_bytes()));
    __ movptr(rax, Address(rax, mirror_offset));
    __ bind(done);
  }
#ifdef ASSERT
  { Label L;
    __ cmpptr(rax, Address(monitor, BasicObjectLock::obj_offset_in_bytes()));   // correct object?
    __ jcc(Assembler::equal, L);
    __ stop("wrong synchronization lobject");
    __ bind(L);
  }
#endif // ASSERT
  // can destroy rax, rdx|c_rarg1, rcx, and (via call_VM) rdi!
  __ lock_object(monitor);
}

// Call an accessor method (assuming it is resolved, otherwise drop into vanilla (slow path) entry

address InterpreterGenerator::generate_accessor_entry(void) {

  // rbx: Method*

  // rsi/r13: senderSP must preserved for slow path, set SP to it on fast path

  Label xreturn_path;

  // do fastpath for resolved accessor methods
  if (UseFastAccessorMethods) {

    address entry_point = __ pc();

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

    // read first instruction word and extract bytecode @ 1 and index @ 2
    __ movptr(rdx, Address(rbx, Method::const_offset()));
    __ movptr(rdi, Address(rdx, ConstMethod::constants_offset()));
    __ movl(rdx, Address(rdx, ConstMethod::codes_offset()));
    // Shift codes right to get the index on the right.
    // The bytecode fetched looks like <index><0xb4><0x2a>
    __ shrl(rdx, 2*BitsPerByte);
    __ shll(rdx, exact_log2(in_words(ConstantPoolCacheEntry::size())));
    __ movptr(rdi, Address(rdi, ConstantPool::cache_offset_in_bytes()));

    // rax,: local 0
    // rbx,: method
    // rcx: receiver - do not destroy since it is needed for slow path!
    // rcx: scratch
    // rdx: constant pool cache index
    // rdi: constant pool cache
    // rsi/r13: sender sp

    // check if getfield has been resolved and read constant pool cache entry
    // check the validity of the cache entry by testing whether _indices field
    // contains Bytecode::_getfield in b1 byte.
    assert(in_words(ConstantPoolCacheEntry::size()) == 4, "adjust shift below");
    __ movl(rcx,
            Address(rdi,
                    rdx,
                    Address::times_ptr, ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::indices_offset()));
    __ shrl(rcx, 2*BitsPerByte);
    __ andl(rcx, 0xFF);
    __ cmpl(rcx, Bytecodes::_getfield);
    __ jcc(Assembler::notEqual, slow_path);

    // Note: constant pool entry is not valid before bytecode is resolved
    __ movptr(rcx,
            Address(rdi,
                    rdx,
                    Address::times_ptr, ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::f2_offset()));
    __ movl(rdx,
            Address(rdi,
                    rdx,
                    Address::times_ptr, ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::flags_offset()));

    Label notByte, notShort, notChar;
    const Address field_address (rax, rcx, Address::times_1);

    // Need to differentiate between igetfield, agetfield, bgetfield etc.
    // because they are different sizes.
    // Use the type from the constant pool cache
    __ shrl(rdx, ConstantPoolCacheEntry::tos_state_shift);
    // Make sure we don't need to mask rdx after the above shift
    ConstantPoolCacheEntry::verify_tos_state_shift();
#ifdef _LP64
    Label notObj;
    __ cmpl(rdx, atos);
    __ jcc(Assembler::notEqual, notObj);
    // atos
    __ movptr(rax, field_address);
    __ jmp(xreturn_path);

    __ bind(notObj);
#endif // _LP64
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
#ifndef _LP64
    __ cmpl(rdx, atos);
    __ jcc(Assembler::equal, okay);
#endif // _LP64
    __ cmpl(rdx, itos);
    __ jcc(Assembler::equal, okay);
    __ stop("what type is this?");
    __ bind(okay);
#endif // ASSERT
    // All the rest are a 32 bit wordsize
    __ movl(rax, field_address);

    __ bind(xreturn_path);

    // _ireturn/_areturn
    __ pop(rdi);                               // get return address
    __ mov(rsp, sender_sp_on_entry);           // set sp to sender sp
    __ jmp(rdi);

    // generate a vanilla interpreter entry as the slow path
    __ bind(slow_path);
    // We will enter c++ interpreter looking like it was
    // called by the call_stub this will cause it to return
    // a tosca result to the invoker which might have been
    // the c++ interpreter itself.

    __ jmp(fast_accessor_slow_entry_path);
    return entry_point;

  } else {
    return NULL;
  }

}

address InterpreterGenerator::generate_Reference_get_entry(void) {
#if INCLUDE_ALL_GCS
  if (UseG1GC) {
    // We need to generate have a routine that generates code to:
    //   * load the value in the referent field
    //   * passes that value to the pre-barrier.
    //
    // In the case of G1 this will record the value of the
    // referent in an SATB buffer if marking is active.
    // This will cause concurrent marking to mark the referent
    // field as live.
    Unimplemented();
  }
#endif // INCLUDE_ALL_GCS

  // If G1 is not enabled then attempt to go through the accessor entry point
  // Reference.get is an accessor
  return generate_accessor_entry();
}

//
// C++ Interpreter stub for calling a native method.
// This sets up a somewhat different looking stack for calling the native method
// than the typical interpreter frame setup but still has the pointer to
// an interpreter state.
//

address InterpreterGenerator::generate_native_entry(bool synchronized) {
  // determine code generation flags
  bool inc_counter  = UseCompiler || CountCompiledCalls;

  // rbx: Method*
  // rcx: receiver (unused)
  // rsi/r13: previous interpreter state (if called from C++ interpreter) must preserve
  //      in any case. If called via c1/c2/call_stub rsi/r13 is junk (to use) but harmless
  //      to save/restore.
  address entry_point = __ pc();

  const Address constMethod       (rbx, Method::const_offset());
  const Address access_flags      (rbx, Method::access_flags_offset());
  const Address size_of_parameters(rcx, ConstMethod::size_of_parameters_offset());

  // rsi/r13 == state/locals rdi == prevstate
  const Register locals = rdi;

  // get parameter size (always needed)
  __ movptr(rcx, constMethod);
  __ load_unsigned_short(rcx, size_of_parameters);

  // rbx: Method*
  // rcx: size of parameters
  __ pop(rax);                                       // get return address
  // for natives the size of locals is zero

  // compute beginning of parameters /locals

  __ lea(locals, Address(rsp, rcx, Address::times_ptr, -wordSize));

  // initialize fixed part of activation frame

  // Assumes rax = return address

  // allocate and initialize new interpreterState and method expression stack
  // IN(locals) ->  locals
  // IN(state) -> previous frame manager state (NULL from stub/c1/c2)
  // destroys rax, rcx, rdx
  // OUT (state) -> new interpreterState
  // OUT(rsp) -> bottom of methods expression stack

  // save sender_sp
  __ mov(rcx, sender_sp_on_entry);
  // start with NULL previous state
  __ movptr(state, (int32_t)NULL_WORD);
  generate_compute_interpreter_state(state, locals, rcx, true);

#ifdef ASSERT
  { Label L;
    __ movptr(rax, STATE(_stack_base));
#ifdef _LP64
    // duplicate the alignment rsp got after setting stack_base
    __ subptr(rax, frame::arg_reg_save_area_bytes); // windows
    __ andptr(rax, -16); // must be 16 byte boundary (see amd64 ABI)
#endif // _LP64
    __ cmpptr(rax, rsp);
    __ jcc(Assembler::equal, L);
    __ stop("broken stack frame setup in interpreter");
    __ bind(L);
  }
#endif

  const Register unlock_thread = LP64_ONLY(r15_thread) NOT_LP64(rax);
  NOT_LP64(__ movptr(unlock_thread, STATE(_thread));) // get thread
  // Since at this point in the method invocation the exception handler
  // would try to exit the monitor of synchronized methods which hasn't
  // been entered yet, we set the thread local variable
  // _do_not_unlock_if_synchronized to true. The remove_activation will
  // check this flag.

  const Address do_not_unlock_if_synchronized(unlock_thread,
        in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()));
  __ movbool(do_not_unlock_if_synchronized, true);

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


  // increment invocation count & check for overflow
  Label invocation_counter_overflow;
  if (inc_counter) {
    generate_counter_incr(&invocation_counter_overflow, NULL, NULL);
  }

  Label continue_after_compile;

  __ bind(continue_after_compile);

  bang_stack_shadow_pages(true);

  // reset the _do_not_unlock_if_synchronized flag
  NOT_LP64(__ movl(rax, STATE(_thread));)                       // get thread
  __ movbool(do_not_unlock_if_synchronized, false);


  // check for synchronized native methods
  //
  // Note: This must happen *after* invocation counter check, since
  //       when overflow happens, the method should not be locked.
  if (synchronized) {
    // potentially kills rax, rcx, rdx, rdi
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

  // jvmti support
  __ notify_method_entry();

  // work registers
  const Register method = rbx;
  const Register thread = LP64_ONLY(r15_thread) NOT_LP64(rdi);
  const Register t      = InterpreterRuntime::SignatureHandlerGenerator::temp();    // rcx|rscratch1
  const Address constMethod       (method, Method::const_offset());
  const Address size_of_parameters(t, ConstMethod::size_of_parameters_offset());

  // allocate space for parameters
  __ movptr(method, STATE(_method));
  __ verify_method_ptr(method);
  __ movptr(t, constMethod);
  __ load_unsigned_short(t, size_of_parameters);
  __ shll(t, 2);
#ifdef _LP64
  __ subptr(rsp, t);
  __ subptr(rsp, frame::arg_reg_save_area_bytes); // windows
  __ andptr(rsp, -16); // must be 16 byte boundary (see amd64 ABI)
#else
  __ addptr(t, 2*wordSize);     // allocate two more slots for JNIEnv and possible mirror
  __ subptr(rsp, t);
  __ andptr(rsp, -(StackAlignmentInBytes)); // gcc needs 16 byte aligned stacks to do XMM intrinsics
#endif // _LP64

  // get signature handler
    Label pending_exception_present;

  { Label L;
    __ movptr(t, Address(method, Method::signature_handler_offset()));
    __ testptr(t, t);
    __ jcc(Assembler::notZero, L);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::prepare_native_call), method, false);
    __ movptr(method, STATE(_method));
    __ cmpptr(Address(thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::notEqual, pending_exception_present);
    __ verify_method_ptr(method);
    __ movptr(t, Address(method, Method::signature_handler_offset()));
    __ bind(L);
  }
#ifdef ASSERT
  {
    Label L;
    __ push(t);
    __ get_thread(t);                                   // get vm's javathread*
    __ cmpptr(t, STATE(_thread));
    __ jcc(Assembler::equal, L);
    __ int3();
    __ bind(L);
    __ pop(t);
  }
#endif //

  const Register from_ptr = InterpreterRuntime::SignatureHandlerGenerator::from();
  // call signature handler
  assert(InterpreterRuntime::SignatureHandlerGenerator::to  () == rsp, "adjust this code");

  // The generated handlers do not touch RBX (the method oop).
  // However, large signatures cannot be cached and are generated
  // each time here.  The slow-path generator will blow RBX
  // sometime, so we must reload it after the call.
  __ movptr(from_ptr, STATE(_locals));  // get the from pointer
  __ call(t);
  __ movptr(method, STATE(_method));
  __ verify_method_ptr(method);

  // result handler is in rax
  // set result handler
  __ movptr(STATE(_result_handler), rax);


  // get native function entry point
  { Label L;
    __ movptr(rax, Address(method, Method::native_function_offset()));
    __ testptr(rax, rax);
    __ jcc(Assembler::notZero, L);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::prepare_native_call), method);
    __ movptr(method, STATE(_method));
    __ verify_method_ptr(method);
    __ movptr(rax, Address(method, Method::native_function_offset()));
    __ bind(L);
  }

  // pass mirror handle if static call
  { Label L;
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());
    __ movl(t, Address(method, Method::access_flags_offset()));
    __ testl(t, JVM_ACC_STATIC);
    __ jcc(Assembler::zero, L);
    // get mirror
    __ movptr(t, Address(method, Method:: const_offset()));
    __ movptr(t, Address(t, ConstMethod::constants_offset()));
    __ movptr(t, Address(t, ConstantPool::pool_holder_offset_in_bytes()));
    __ movptr(t, Address(t, mirror_offset));
    // copy mirror into activation object
    __ movptr(STATE(_oop_temp), t);
    // pass handle to mirror
#ifdef _LP64
    __ lea(c_rarg1, STATE(_oop_temp));
#else
    __ lea(t, STATE(_oop_temp));
    __ movptr(Address(rsp, wordSize), t);
#endif // _LP64
    __ bind(L);
  }
#ifdef ASSERT
  {
    Label L;
    __ push(t);
    __ get_thread(t);                                   // get vm's javathread*
    __ cmpptr(t, STATE(_thread));
    __ jcc(Assembler::equal, L);
    __ int3();
    __ bind(L);
    __ pop(t);
  }
#endif //

  // pass JNIEnv
#ifdef _LP64
  __ lea(c_rarg0, Address(thread, JavaThread::jni_environment_offset()));
#else
  __ movptr(thread, STATE(_thread));          // get thread
  __ lea(t, Address(thread, JavaThread::jni_environment_offset()));

  __ movptr(Address(rsp, 0), t);
#endif // _LP64

#ifdef ASSERT
  {
    Label L;
    __ push(t);
    __ get_thread(t);                                   // get vm's javathread*
    __ cmpptr(t, STATE(_thread));
    __ jcc(Assembler::equal, L);
    __ int3();
    __ bind(L);
    __ pop(t);
  }
#endif //

#ifdef ASSERT
  { Label L;
    __ movl(t, Address(thread, JavaThread::thread_state_offset()));
    __ cmpl(t, _thread_in_Java);
    __ jcc(Assembler::equal, L);
    __ stop("Wrong thread state in native stub");
    __ bind(L);
  }
#endif

  // Change state to native (we save the return address in the thread, since it might not
  // be pushed on the stack when we do a a stack traversal). It is enough that the pc()
  // points into the right code segment. It does not have to be the correct return pc.

  __ set_last_Java_frame(thread, noreg, rbp, __ pc());

  __ movl(Address(thread, JavaThread::thread_state_offset()), _thread_in_native);

  __ call(rax);

  // result potentially in rdx:rax or ST0
  __ movptr(method, STATE(_method));
  NOT_LP64(__ movptr(thread, STATE(_thread));)                  // get thread

  // The potential result is in ST(0) & rdx:rax
  // With C++ interpreter we leave any possible result in ST(0) until we are in result handler and then
  // we do the appropriate stuff for returning the result. rdx:rax must always be saved because just about
  // anything we do here will destroy it, st(0) is only saved if we re-enter the vm where it would
  // be destroyed.
  // It is safe to do these pushes because state is _thread_in_native and return address will be found
  // via _last_native_pc and not via _last_jave_sp

    // Must save the value of ST(0)/xmm0 since it could be destroyed before we get to result handler
    { Label Lpush, Lskip;
      ExternalAddress float_handler(AbstractInterpreter::result_handler(T_FLOAT));
      ExternalAddress double_handler(AbstractInterpreter::result_handler(T_DOUBLE));
      __ cmpptr(STATE(_result_handler), float_handler.addr());
      __ jcc(Assembler::equal, Lpush);
      __ cmpptr(STATE(_result_handler), double_handler.addr());
      __ jcc(Assembler::notEqual, Lskip);
      __ bind(Lpush);
      __ subptr(rsp, 2*wordSize);
      if ( UseSSE < 2 ) {
        __ fstp_d(Address(rsp, 0));
      } else {
        __ movdbl(Address(rsp, 0), xmm0);
      }
      __ bind(Lskip);
    }

  // save rax:rdx for potential use by result handler.
  __ push(rax);
#ifndef _LP64
  __ push(rdx);
#endif // _LP64

  // Verify or restore cpu control state after JNI call
  __ restore_cpu_control_state_after_jni();

  // change thread state
  __ movl(Address(thread, JavaThread::thread_state_offset()), _thread_in_native_trans);
  if(os::is_MP()) {
    // Write serialization page so VM thread can do a pseudo remote membar.
    // We use the current thread pointer to calculate a thread specific
    // offset to write to within the page. This minimizes bus traffic
    // due to cache line collision.
    __ serialize_memory(thread, rcx);
  }

  // check for safepoint operation in progress and/or pending suspend requests
  { Label Continue;

    __ cmp32(ExternalAddress(SafepointSynchronize::address_of_state()),
             SafepointSynchronize::_not_synchronized);

    // threads running native code and they are expected to self-suspend
    // when leaving the _thread_in_native state. We need to check for
    // pending suspend requests here.
    Label L;
    __ jcc(Assembler::notEqual, L);
    __ cmpl(Address(thread, JavaThread::suspend_flags_offset()), 0);
    __ jcc(Assembler::equal, Continue);
    __ bind(L);

    // Don't use call_VM as it will see a possible pending exception and forward it
    // and never return here preventing us from clearing _last_native_pc down below.
    // Also can't use call_VM_leaf either as it will check to see if rsi & rdi are
    // preserved and correspond to the bcp/locals pointers.
    //

    ((MacroAssembler*)_masm)->call_VM_leaf(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans),
                          thread);
    __ increment(rsp, wordSize);

    __ movptr(method, STATE(_method));
    __ verify_method_ptr(method);
    __ movptr(thread, STATE(_thread));                       // get thread

    __ bind(Continue);
  }

  // change thread state
  __ movl(Address(thread, JavaThread::thread_state_offset()), _thread_in_Java);

  __ reset_last_Java_frame(thread, true, true);

  // reset handle block
  __ movptr(t, Address(thread, JavaThread::active_handles_offset()));
  __ movptr(Address(t, JNIHandleBlock::top_offset_in_bytes()), (int32_t)NULL_WORD);

  // If result was an oop then unbox and save it in the frame
  { Label L;
    Label no_oop, store_result;
      ExternalAddress oop_handler(AbstractInterpreter::result_handler(T_OBJECT));
    __ cmpptr(STATE(_result_handler), oop_handler.addr());
    __ jcc(Assembler::notEqual, no_oop);
#ifndef _LP64
    __ pop(rdx);
#endif // _LP64
    __ pop(rax);
    __ testptr(rax, rax);
    __ jcc(Assembler::zero, store_result);
    // unbox
    __ movptr(rax, Address(rax, 0));
    __ bind(store_result);
    __ movptr(STATE(_oop_temp), rax);
    // keep stack depth as expected by pushing oop which will eventually be discarded
    __ push(rax);
#ifndef _LP64
    __ push(rdx);
#endif // _LP64
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


  // QQQ Seems like for native methods we simply return and the caller will see the pending
  // exception and do the right thing. Certainly the interpreter will, don't know about
  // compiled methods.
  // Seems that the answer to above is no this is wrong. The old code would see the exception
  // and forward it before doing the unlocking and notifying jvmdi that method has exited.
  // This seems wrong need to investigate the spec.

  // handle exceptions (exception handling will handle unlocking!)
  { Label L;
    __ cmpptr(Address(thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::zero, L);
    __ bind(pending_exception_present);

    // There are potential results on the stack (rax/rdx, ST(0)) we ignore these and simply
    // return and let caller deal with exception. This skips the unlocking here which
    // seems wrong but seems to be what asm interpreter did. Can't find this in the spec.
    // Note: must preverve method in rbx
    //

    // remove activation

    __ movptr(t, STATE(_sender_sp));
    __ leave();                                  // remove frame anchor
    __ pop(rdi);                                 // get return address
    __ movptr(state, STATE(_prev_link));         // get previous state for return
    __ mov(rsp, t);                              // set sp to sender sp
    __ push(rdi);                                // push throwing pc
    // The skips unlocking!! This seems to be what asm interpreter does but seems
    // very wrong. Not clear if this violates the spec.
    __ jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
    __ bind(L);
  }

  // do unlocking if necessary
  { Label L;
    __ movl(t, Address(method, Method::access_flags_offset()));
    __ testl(t, JVM_ACC_SYNCHRONIZED);
    __ jcc(Assembler::zero, L);
    // the code below should be shared with interpreter macro assembler implementation
    { Label unlock;
    const Register monitor = NOT_LP64(rdx) LP64_ONLY(c_rarg1);
      // BasicObjectLock will be first in list, since this is a synchronized method. However, need
      // to check that the object has not been unlocked by an explicit monitorexit bytecode.
      __ movptr(monitor, STATE(_monitor_base));
      __ subptr(monitor, frame::interpreter_frame_monitor_size() * wordSize);  // address of initial monitor

      __ movptr(t, Address(monitor, BasicObjectLock::obj_offset_in_bytes()));
      __ testptr(t, t);
      __ jcc(Assembler::notZero, unlock);

      // Entry already unlocked, need to throw exception
      __ MacroAssembler::call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_illegal_monitor_state_exception));
      __ should_not_reach_here();

      __ bind(unlock);
      __ unlock_object(monitor);
      // unlock can blow rbx so restore it for path that needs it below
      __ movptr(method, STATE(_method));
    }
    __ bind(L);
  }

  // jvmti support
  // Note: This must happen _after_ handling/throwing any exceptions since
  //       the exception handler code notifies the runtime of method exits
  //       too. If this happens before, method entry/exit notifications are
  //       not properly paired (was bug - gri 11/22/99).
  __ notify_method_exit(vtos, InterpreterMacroAssembler::NotifyJVMTI);

  // restore potential result in rdx:rax, call result handler to restore potential result in ST0 & handle result
#ifndef _LP64
  __ pop(rdx);
#endif // _LP64
  __ pop(rax);
  __ movptr(t, STATE(_result_handler));       // get result handler
  __ call(t);                                 // call result handler to convert to tosca form

  // remove activation

  __ movptr(t, STATE(_sender_sp));

  __ leave();                                  // remove frame anchor
  __ pop(rdi);                                 // get return address
  __ movptr(state, STATE(_prev_link));         // get previous state for return (if c++ interpreter was caller)
  __ mov(rsp, t);                              // set sp to sender sp
  __ jmp(rdi);

  // invocation counter overflow
  if (inc_counter) {
    // Handle overflow of counter and compile method
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(&continue_after_compile);
  }

  return entry_point;
}

// Generate entries that will put a result type index into rcx
void CppInterpreterGenerator::generate_deopt_handling() {

  Label return_from_deopt_common;

  // Generate entries that will put a result type index into rcx
  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_atos  = __ pc();

  // rax is live here
  __ movl(rcx, AbstractInterpreter::BasicType_as_index(T_OBJECT));    // Result stub address array index
  __ jmp(return_from_deopt_common);


  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_btos  = __ pc();

  // rax is live here
  __ movl(rcx, AbstractInterpreter::BasicType_as_index(T_BOOLEAN));    // Result stub address array index
  __ jmp(return_from_deopt_common);

  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_itos  = __ pc();

  // rax is live here
  __ movl(rcx, AbstractInterpreter::BasicType_as_index(T_INT));    // Result stub address array index
  __ jmp(return_from_deopt_common);

  // deopt needs to jump to here to enter the interpreter (return a result)

  deopt_frame_manager_return_ltos  = __ pc();
  // rax,rdx are live here
  __ movl(rcx, AbstractInterpreter::BasicType_as_index(T_LONG));    // Result stub address array index
  __ jmp(return_from_deopt_common);

  // deopt needs to jump to here to enter the interpreter (return a result)

  deopt_frame_manager_return_ftos  = __ pc();
  // st(0) is live here
  __ movl(rcx, AbstractInterpreter::BasicType_as_index(T_FLOAT));    // Result stub address array index
  __ jmp(return_from_deopt_common);

  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_dtos  = __ pc();

  // st(0) is live here
  __ movl(rcx, AbstractInterpreter::BasicType_as_index(T_DOUBLE));    // Result stub address array index
  __ jmp(return_from_deopt_common);

  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_vtos  = __ pc();

  __ movl(rcx, AbstractInterpreter::BasicType_as_index(T_VOID));

  // Deopt return common
  // an index is present in rcx that lets us move any possible result being
  // return to the interpreter's stack
  //
  // Because we have a full sized interpreter frame on the youngest
  // activation the stack is pushed too deep to share the tosca to
  // stack converters directly. We shrink the stack to the desired
  // amount and then push result and then re-extend the stack.
  // We could have the code in size_activation layout a short
  // frame for the top activation but that would look different
  // than say sparc (which needs a full size activation because
  // the windows are in the way. Really it could be short? QQQ
  //
  __ bind(return_from_deopt_common);

  __ lea(state, Address(rbp, -(int)sizeof(BytecodeInterpreter)));

  // setup rsp so we can push the "result" as needed.
  __ movptr(rsp, STATE(_stack));                                   // trim stack (is prepushed)
  __ addptr(rsp, wordSize);                                        // undo prepush

  ExternalAddress tosca_to_stack((address)CppInterpreter::_tosca_to_stack);
  // Address index(noreg, rcx, Address::times_ptr);
  __ movptr(rcx, ArrayAddress(tosca_to_stack, Address(noreg, rcx, Address::times_ptr)));
  // __ movl(rcx, Address(noreg, rcx, Address::times_ptr, int(AbstractInterpreter::_tosca_to_stack)));
  __ call(rcx);                                                   // call result converter

  __ movl(STATE(_msg), (int)BytecodeInterpreter::deopt_resume);
  __ lea(rsp, Address(rsp, -wordSize));                            // prepush stack (result if any already present)
  __ movptr(STATE(_stack), rsp);                                   // inform interpreter of new stack depth (parameters removed,
                                                                   // result if any on stack already )
  __ movptr(rsp, STATE(_stack_limit));                             // restore expression stack to full depth
}

// Generate the code to handle a more_monitors message from the c++ interpreter
void CppInterpreterGenerator::generate_more_monitors() {


  Label entry, loop;
  const int entry_size = frame::interpreter_frame_monitor_size() * wordSize;
  // 1. compute new pointers                     // rsp: old expression stack top
  __ movptr(rdx, STATE(_stack_base));            // rdx: old expression stack bottom
  __ subptr(rsp, entry_size);                    // move expression stack top limit
  __ subptr(STATE(_stack), entry_size);          // update interpreter stack top
  __ subptr(STATE(_stack_limit), entry_size);    // inform interpreter
  __ subptr(rdx, entry_size);                    // move expression stack bottom
  __ movptr(STATE(_stack_base), rdx);            // inform interpreter
  __ movptr(rcx, STATE(_stack));                 // set start value for copy loop
  __ jmp(entry);
  // 2. move expression stack contents
  __ bind(loop);
  __ movptr(rbx, Address(rcx, entry_size));      // load expression stack word from old location
  __ movptr(Address(rcx, 0), rbx);               // and store it at new location
  __ addptr(rcx, wordSize);                      // advance to next word
  __ bind(entry);
  __ cmpptr(rcx, rdx);                           // check if bottom reached
  __ jcc(Assembler::notEqual, loop);             // if not at bottom then copy next word
  // now zero the slot so we can find it.
  __ movptr(Address(rdx, BasicObjectLock::obj_offset_in_bytes()), (int32_t) NULL_WORD);
  __ movl(STATE(_msg), (int)BytecodeInterpreter::got_monitors);
}


// Initial entry to C++ interpreter from the call_stub.
// This entry point is called the frame manager since it handles the generation
// of interpreter activation frames via requests directly from the vm (via call_stub)
// and via requests from the interpreter. The requests from the call_stub happen
// directly thru the entry point. Requests from the interpreter happen via returning
// from the interpreter and examining the message the interpreter has returned to
// the frame manager. The frame manager can take the following requests:

// NO_REQUEST - error, should never happen.
// MORE_MONITORS - need a new monitor. Shuffle the expression stack on down and
//                 allocate a new monitor.
// CALL_METHOD - setup a new activation to call a new method. Very similar to what
//               happens during entry during the entry via the call stub.
// RETURN_FROM_METHOD - remove an activation. Return to interpreter or call stub.
//
// Arguments:
//
// rbx: Method*
// rcx: receiver - unused (retrieved from stack as needed)
// rsi/r13: previous frame manager state (NULL from the call_stub/c1/c2)
//
//
// Stack layout at entry
//
// [ return address     ] <--- rsp
// [ parameter n        ]
//   ...
// [ parameter 1        ]
// [ expression stack   ]
//
//
// We are free to blow any registers we like because the call_stub which brought us here
// initially has preserved the callee save registers already.
//
//

static address interpreter_frame_manager = NULL;

address InterpreterGenerator::generate_normal_entry(bool synchronized) {

  // rbx: Method*
  // rsi/r13: sender sp

  // Because we redispatch "recursive" interpreter entries thru this same entry point
  // the "input" register usage is a little strange and not what you expect coming
  // from the call_stub. From the call stub rsi/rdi (current/previous) interpreter
  // state are NULL but on "recursive" dispatches they are what you'd expect.
  // rsi: current interpreter state (C++ interpreter) must preserve (null from call_stub/c1/c2)


  // A single frame manager is plenty as we don't specialize for synchronized. We could and
  // the code is pretty much ready. Would need to change the test below and for good measure
  // modify generate_interpreter_state to only do the (pre) sync stuff stuff for synchronized
  // routines. Not clear this is worth it yet.

  if (interpreter_frame_manager) return interpreter_frame_manager;

  address entry_point = __ pc();

  // Fast accessor methods share this entry point.
  // This works because frame manager is in the same codelet
  if (UseFastAccessorMethods && !synchronized) __ bind(fast_accessor_slow_entry_path);

  Label dispatch_entry_2;
  __ movptr(rcx, sender_sp_on_entry);
  __ movptr(state, (int32_t)NULL_WORD);                              // no current activation

  __ jmp(dispatch_entry_2);

  const Register locals  = rdi;

  Label re_dispatch;

  __ bind(re_dispatch);

  // save sender sp (doesn't include return address
  __ lea(rcx, Address(rsp, wordSize));

  __ bind(dispatch_entry_2);

  // save sender sp
  __ push(rcx);

  const Address constMethod       (rbx, Method::const_offset());
  const Address access_flags      (rbx, Method::access_flags_offset());
  const Address size_of_parameters(rdx, ConstMethod::size_of_parameters_offset());
  const Address size_of_locals    (rdx, ConstMethod::size_of_locals_offset());

  // const Address monitor_block_top (rbp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
  // const Address monitor_block_bot (rbp, frame::interpreter_frame_initial_sp_offset        * wordSize);
  // const Address monitor(rbp, frame::interpreter_frame_initial_sp_offset * wordSize - (int)sizeof(BasicObjectLock));

  // get parameter size (always needed)
  __ movptr(rdx, constMethod);
  __ load_unsigned_short(rcx, size_of_parameters);

  // rbx: Method*
  // rcx: size of parameters
  __ load_unsigned_short(rdx, size_of_locals);                     // get size of locals in words

  __ subptr(rdx, rcx);                                             // rdx = no. of additional locals

  // see if we've got enough room on the stack for locals plus overhead.
  generate_stack_overflow_check();                                 // C++

  // c++ interpreter does not use stack banging or any implicit exceptions
  // leave for now to verify that check is proper.
  bang_stack_shadow_pages(false);



  // compute beginning of parameters (rdi)
  __ lea(locals, Address(rsp, rcx, Address::times_ptr, wordSize));

  // save sender's sp
  // __ movl(rcx, rsp);

  // get sender's sp
  __ pop(rcx);

  // get return address
  __ pop(rax);

  // rdx - # of additional locals
  // allocate space for locals
  // explicitly initialize locals
  {
    Label exit, loop;
    __ testl(rdx, rdx);                               // (32bit ok)
    __ jcc(Assembler::lessEqual, exit);               // do nothing if rdx <= 0
    __ bind(loop);
    __ push((int32_t)NULL_WORD);                      // initialize local variables
    __ decrement(rdx);                                // until everything initialized
    __ jcc(Assembler::greater, loop);
    __ bind(exit);
  }


  // Assumes rax = return address

  // allocate and initialize new interpreterState and method expression stack
  // IN(locals) ->  locals
  // IN(state) -> any current interpreter activation
  // destroys rax, rcx, rdx, rdi
  // OUT (state) -> new interpreterState
  // OUT(rsp) -> bottom of methods expression stack

  generate_compute_interpreter_state(state, locals, rcx, false);

  // Call interpreter

  Label call_interpreter;
  __ bind(call_interpreter);

  // c++ interpreter does not use stack banging or any implicit exceptions
  // leave for now to verify that check is proper.
  bang_stack_shadow_pages(false);


  // Call interpreter enter here if message is
  // set and we know stack size is valid

  Label call_interpreter_2;

  __ bind(call_interpreter_2);

  {
    const Register thread  = NOT_LP64(rcx) LP64_ONLY(r15_thread);

#ifdef _LP64
    __ mov(c_rarg0, state);
#else
    __ push(state);                                                 // push arg to interpreter
    __ movptr(thread, STATE(_thread));
#endif // _LP64

    // We can setup the frame anchor with everything we want at this point
    // as we are thread_in_Java and no safepoints can occur until we go to
    // vm mode. We do have to clear flags on return from vm but that is it
    //
    __ movptr(Address(thread, JavaThread::last_Java_fp_offset()), rbp);
    __ movptr(Address(thread, JavaThread::last_Java_sp_offset()), rsp);

    // Call the interpreter

    RuntimeAddress normal(CAST_FROM_FN_PTR(address, BytecodeInterpreter::run));
    RuntimeAddress checking(CAST_FROM_FN_PTR(address, BytecodeInterpreter::runWithChecks));

    __ call(JvmtiExport::can_post_interpreter_events() ? checking : normal);
    NOT_LP64(__ pop(rax);)                                          // discard parameter to run
    //
    // state is preserved since it is callee saved
    //

    // reset_last_Java_frame

    NOT_LP64(__ movl(thread, STATE(_thread));)
    __ reset_last_Java_frame(thread, true, true);
  }

  // examine msg from interpreter to determine next action

  __ movl(rdx, STATE(_msg));                                       // Get new message

  Label call_method;
  Label return_from_interpreted_method;
  Label throw_exception;
  Label bad_msg;
  Label do_OSR;

  __ cmpl(rdx, (int32_t)BytecodeInterpreter::call_method);
  __ jcc(Assembler::equal, call_method);
  __ cmpl(rdx, (int32_t)BytecodeInterpreter::return_from_method);
  __ jcc(Assembler::equal, return_from_interpreted_method);
  __ cmpl(rdx, (int32_t)BytecodeInterpreter::do_osr);
  __ jcc(Assembler::equal, do_OSR);
  __ cmpl(rdx, (int32_t)BytecodeInterpreter::throwing_exception);
  __ jcc(Assembler::equal, throw_exception);
  __ cmpl(rdx, (int32_t)BytecodeInterpreter::more_monitors);
  __ jcc(Assembler::notEqual, bad_msg);

  // Allocate more monitor space, shuffle expression stack....

  generate_more_monitors();

  __ jmp(call_interpreter);

  // uncommon trap needs to jump to here to enter the interpreter (re-execute current bytecode)
  unctrap_frame_manager_entry  = __ pc();
  //
  // Load the registers we need.
  __ lea(state, Address(rbp, -(int)sizeof(BytecodeInterpreter)));
  __ movptr(rsp, STATE(_stack_limit));                             // restore expression stack to full depth
  __ jmp(call_interpreter_2);



  //=============================================================================
  // Returning from a compiled method into a deopted method. The bytecode at the
  // bcp has completed. The result of the bytecode is in the native abi (the tosca
  // for the template based interpreter). Any stack space that was used by the
  // bytecode that has completed has been removed (e.g. parameters for an invoke)
  // so all that we have to do is place any pending result on the expression stack
  // and resume execution on the next bytecode.


  generate_deopt_handling();
  __ jmp(call_interpreter);


  // Current frame has caught an exception we need to dispatch to the
  // handler. We can get here because a native interpreter frame caught
  // an exception in which case there is no handler and we must rethrow
  // If it is a vanilla interpreted frame the we simply drop into the
  // interpreter and let it do the lookup.

  Interpreter::_rethrow_exception_entry = __ pc();
  // rax: exception
  // rdx: return address/pc that threw exception

  Label return_with_exception;
  Label unwind_and_forward;

  // restore state pointer.
  __ lea(state, Address(rbp,  -(int)sizeof(BytecodeInterpreter)));

  __ movptr(rbx, STATE(_method));                       // get method
#ifdef _LP64
  __ movptr(Address(r15_thread, Thread::pending_exception_offset()), rax);
#else
  __ movl(rcx, STATE(_thread));                       // get thread

  // Store exception with interpreter will expect it
  __ movptr(Address(rcx, Thread::pending_exception_offset()), rax);
#endif // _LP64

  // is current frame vanilla or native?

  __ movl(rdx, access_flags);
  __ testl(rdx, JVM_ACC_NATIVE);
  __ jcc(Assembler::zero, return_with_exception);     // vanilla interpreted frame, handle directly

  // We drop thru to unwind a native interpreted frame with a pending exception
  // We jump here for the initial interpreter frame with exception pending
  // We unwind the current acivation and forward it to our caller.

  __ bind(unwind_and_forward);

  // unwind rbp, return stack to unextended value and re-push return address

  __ movptr(rcx, STATE(_sender_sp));
  __ leave();
  __ pop(rdx);
  __ mov(rsp, rcx);
  __ push(rdx);
  __ jump(RuntimeAddress(StubRoutines::forward_exception_entry()));

  // Return point from a call which returns a result in the native abi
  // (c1/c2/jni-native). This result must be processed onto the java
  // expression stack.
  //
  // A pending exception may be present in which case there is no result present

  Label resume_interpreter;
  Label do_float;
  Label do_double;
  Label done_conv;

  // The FPU stack is clean if UseSSE >= 2 but must be cleaned in other cases
  if (UseSSE < 2) {
    __ lea(state, Address(rbp,  -(int)sizeof(BytecodeInterpreter)));
    __ movptr(rbx, STATE(_result._to_call._callee));                   // get method just executed
    __ movl(rcx, Address(rbx, Method::result_index_offset()));
    __ cmpl(rcx, AbstractInterpreter::BasicType_as_index(T_FLOAT));    // Result stub address array index
    __ jcc(Assembler::equal, do_float);
    __ cmpl(rcx, AbstractInterpreter::BasicType_as_index(T_DOUBLE));    // Result stub address array index
    __ jcc(Assembler::equal, do_double);
#if !defined(_LP64) || defined(COMPILER1) || !defined(COMPILER2)
    __ empty_FPU_stack();
#endif // COMPILER2
    __ jmp(done_conv);

    __ bind(do_float);
#ifdef COMPILER2
    for (int i = 1; i < 8; i++) {
      __ ffree(i);
    }
#endif // COMPILER2
    __ jmp(done_conv);
    __ bind(do_double);
#ifdef COMPILER2
    for (int i = 1; i < 8; i++) {
      __ ffree(i);
    }
#endif // COMPILER2
    __ jmp(done_conv);
  } else {
    __ MacroAssembler::verify_FPU(0, "generate_return_entry_for compiled");
    __ jmp(done_conv);
  }

  // Return point to interpreter from compiled/native method
  InternalAddress return_from_native_method(__ pc());

  __ bind(done_conv);


  // Result if any is in tosca. The java expression stack is in the state that the
  // calling convention left it (i.e. params may or may not be present)
  // Copy the result from tosca and place it on java expression stack.

  // Restore rsi/r13 as compiled code may not preserve it

  __ lea(state, Address(rbp,  -(int)sizeof(BytecodeInterpreter)));

  // restore stack to what we had when we left (in case i2c extended it)

  __ movptr(rsp, STATE(_stack));
  __ lea(rsp, Address(rsp, wordSize));

  // If there is a pending exception then we don't really have a result to process

#ifdef _LP64
  __ cmpptr(Address(r15_thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
#else
  __ movptr(rcx, STATE(_thread));                       // get thread
  __ cmpptr(Address(rcx, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
#endif // _LP64
  __ jcc(Assembler::notZero, return_with_exception);

  // get method just executed
  __ movptr(rbx, STATE(_result._to_call._callee));

  // callee left args on top of expression stack, remove them
  __ movptr(rcx, constMethod);
  __ load_unsigned_short(rcx, Address(rcx, ConstMethod::size_of_parameters_offset()));

  __ lea(rsp, Address(rsp, rcx, Address::times_ptr));

  __ movl(rcx, Address(rbx, Method::result_index_offset()));
  ExternalAddress tosca_to_stack((address)CppInterpreter::_tosca_to_stack);
  // Address index(noreg, rax, Address::times_ptr);
  __ movptr(rcx, ArrayAddress(tosca_to_stack, Address(noreg, rcx, Address::times_ptr)));
  // __ movl(rcx, Address(noreg, rcx, Address::times_ptr, int(AbstractInterpreter::_tosca_to_stack)));
  __ call(rcx);                                               // call result converter
  __ jmp(resume_interpreter);

  // An exception is being caught on return to a vanilla interpreter frame.
  // Empty the stack and resume interpreter

  __ bind(return_with_exception);

  // Exception present, empty stack
  __ movptr(rsp, STATE(_stack_base));
  __ jmp(resume_interpreter);

  // Return from interpreted method we return result appropriate to the caller (i.e. "recursive"
  // interpreter call, or native) and unwind this interpreter activation.
  // All monitors should be unlocked.

  __ bind(return_from_interpreted_method);

  Label return_to_initial_caller;

  __ movptr(rbx, STATE(_method));                                   // get method just executed
  __ cmpptr(STATE(_prev_link), (int32_t)NULL_WORD);                 // returning from "recursive" interpreter call?
  __ movl(rax, Address(rbx, Method::result_index_offset())); // get result type index
  __ jcc(Assembler::equal, return_to_initial_caller);               // back to native code (call_stub/c1/c2)

  // Copy result to callers java stack
  ExternalAddress stack_to_stack((address)CppInterpreter::_stack_to_stack);
  // Address index(noreg, rax, Address::times_ptr);

  __ movptr(rax, ArrayAddress(stack_to_stack, Address(noreg, rax, Address::times_ptr)));
  // __ movl(rax, Address(noreg, rax, Address::times_ptr, int(AbstractInterpreter::_stack_to_stack)));
  __ call(rax);                                                     // call result converter

  Label unwind_recursive_activation;
  __ bind(unwind_recursive_activation);

  // returning to interpreter method from "recursive" interpreter call
  // result converter left rax pointing to top of the java stack for method we are returning
  // to. Now all we must do is unwind the state from the completed call

  __ movptr(state, STATE(_prev_link));                              // unwind state
  __ leave();                                                       // pop the frame
  __ mov(rsp, rax);                                                 // unwind stack to remove args

  // Resume the interpreter. The current frame contains the current interpreter
  // state object.
  //

  __ bind(resume_interpreter);

  // state == interpreterState object for method we are resuming

  __ movl(STATE(_msg), (int)BytecodeInterpreter::method_resume);
  __ lea(rsp, Address(rsp, -wordSize));                            // prepush stack (result if any already present)
  __ movptr(STATE(_stack), rsp);                                   // inform interpreter of new stack depth (parameters removed,
                                                                   // result if any on stack already )
  __ movptr(rsp, STATE(_stack_limit));                             // restore expression stack to full depth
  __ jmp(call_interpreter_2);                                      // No need to bang

  // interpreter returning to native code (call_stub/c1/c2)
  // convert result and unwind initial activation
  // rax - result index

  __ bind(return_to_initial_caller);
  ExternalAddress stack_to_native((address)CppInterpreter::_stack_to_native_abi);
  // Address index(noreg, rax, Address::times_ptr);

  __ movptr(rax, ArrayAddress(stack_to_native, Address(noreg, rax, Address::times_ptr)));
  __ call(rax);                                                    // call result converter

  Label unwind_initial_activation;
  __ bind(unwind_initial_activation);

  // RETURN TO CALL_STUB/C1/C2 code (result if any in rax/rdx ST(0))

  /* Current stack picture

        [ incoming parameters ]
        [ extra locals ]
        [ return address to CALL_STUB/C1/C2]
  fp -> [ CALL_STUB/C1/C2 fp ]
        BytecodeInterpreter object
        expression stack
  sp ->

  */

  // return restoring the stack to the original sender_sp value

  __ movptr(rcx, STATE(_sender_sp));
  __ leave();
  __ pop(rdi);                                                        // get return address
  // set stack to sender's sp
  __ mov(rsp, rcx);
  __ jmp(rdi);                                                        // return to call_stub

  // OSR request, adjust return address to make current frame into adapter frame
  // and enter OSR nmethod

  __ bind(do_OSR);

  Label remove_initial_frame;

  // We are going to pop this frame. Is there another interpreter frame underneath
  // it or is it callstub/compiled?

  // Move buffer to the expected parameter location
  __ movptr(rcx, STATE(_result._osr._osr_buf));

  __ movptr(rax, STATE(_result._osr._osr_entry));

  __ cmpptr(STATE(_prev_link), (int32_t)NULL_WORD);            // returning from "recursive" interpreter call?
  __ jcc(Assembler::equal, remove_initial_frame);              // back to native code (call_stub/c1/c2)

  __ movptr(sender_sp_on_entry, STATE(_sender_sp));            // get sender's sp in expected register
  __ leave();                                                  // pop the frame
  __ mov(rsp, sender_sp_on_entry);                             // trim any stack expansion


  // We know we are calling compiled so push specialized return
  // method uses specialized entry, push a return so we look like call stub setup
  // this path will handle fact that result is returned in registers and not
  // on the java stack.

  __ pushptr(return_from_native_method.addr());

  __ jmp(rax);

  __ bind(remove_initial_frame);

  __ movptr(rdx, STATE(_sender_sp));
  __ leave();
  // get real return
  __ pop(rsi);
  // set stack to sender's sp
  __ mov(rsp, rdx);
  // repush real return
  __ push(rsi);
  // Enter OSR nmethod
  __ jmp(rax);




  // Call a new method. All we do is (temporarily) trim the expression stack
  // push a return address to bring us back to here and leap to the new entry.

  __ bind(call_method);

  // stack points to next free location and not top element on expression stack
  // method expects sp to be pointing to topmost element

  __ movptr(rsp, STATE(_stack));                                     // pop args to c++ interpreter, set sp to java stack top
  __ lea(rsp, Address(rsp, wordSize));

  __ movptr(rbx, STATE(_result._to_call._callee));                   // get method to execute

  // don't need a return address if reinvoking interpreter

  // Make it look like call_stub calling conventions

  // Get (potential) receiver
  // get size of parameters in words
  __ movptr(rcx, constMethod);
  __ load_unsigned_short(rcx, Address(rcx, ConstMethod::size_of_parameters_offset()));

  ExternalAddress recursive(CAST_FROM_FN_PTR(address, RecursiveInterpreterActivation));
  __ pushptr(recursive.addr());                                      // make it look good in the debugger

  InternalAddress entry(entry_point);
  __ cmpptr(STATE(_result._to_call._callee_entry_point), entry.addr()); // returning to interpreter?
  __ jcc(Assembler::equal, re_dispatch);                             // yes

  __ pop(rax);                                                       // pop dummy address


  // get specialized entry
  __ movptr(rax, STATE(_result._to_call._callee_entry_point));
  // set sender SP
  __ mov(sender_sp_on_entry, rsp);

  // method uses specialized entry, push a return so we look like call stub setup
  // this path will handle fact that result is returned in registers and not
  // on the java stack.

  __ pushptr(return_from_native_method.addr());

  __ jmp(rax);

  __ bind(bad_msg);
  __ stop("Bad message from interpreter");

  // Interpreted method "returned" with an exception pass it on...
  // Pass result, unwind activation and continue/return to interpreter/call_stub
  // We handle result (if any) differently based on return to interpreter or call_stub

  Label unwind_initial_with_pending_exception;

  __ bind(throw_exception);
  __ cmpptr(STATE(_prev_link), (int32_t)NULL_WORD);                 // returning from recursive interpreter call?
  __ jcc(Assembler::equal, unwind_initial_with_pending_exception);  // no, back to native code (call_stub/c1/c2)
  __ movptr(rax, STATE(_locals));                                   // pop parameters get new stack value
  __ addptr(rax, wordSize);                                         // account for prepush before we return
  __ jmp(unwind_recursive_activation);

  __ bind(unwind_initial_with_pending_exception);

  // We will unwind the current (initial) interpreter frame and forward
  // the exception to the caller. We must put the exception in the
  // expected register and clear pending exception and then forward.

  __ jmp(unwind_and_forward);

  interpreter_frame_manager = entry_point;
  return entry_point;
}

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
    case Interpreter::java_lang_ref_reference_get
                                             : entry_point = ((InterpreterGenerator*)this)->generate_Reference_get_entry(); break;
    default                                  : ShouldNotReachHere();                                                       break;
  }

  if (entry_point) return entry_point;

  return ((InterpreterGenerator*)this)->generate_normal_entry(synchronized);

}

InterpreterGenerator::InterpreterGenerator(StubQueue* code)
 : CppInterpreterGenerator(code) {
   generate_all(); // down here so it can be "virtual"
}

// Deoptimization helpers for C++ interpreter

// How much stack a method activation needs in words.
int AbstractInterpreter::size_top_interpreter_activation(Method* method) {

  const int stub_code = 4;  // see generate_call_stub
  // Save space for one monitor to get into the interpreted method in case
  // the method is synchronized
  int monitor_size    = method->is_synchronized() ?
                                1*frame::interpreter_frame_monitor_size() : 0;

  // total static overhead size. Account for interpreter state object, return
  // address, saved rbp and 2 words for a "static long no_params() method" issue.

  const int overhead_size = sizeof(BytecodeInterpreter)/wordSize +
    ( frame::sender_sp_offset - frame::link_offset) + 2;

  const int method_stack = (method->max_locals() + method->max_stack()) *
                           Interpreter::stackElementWords;
  return overhead_size + method_stack + stub_code;
}

// returns the activation size.
static int size_activation_helper(int extra_locals_size, int monitor_size) {
  return (extra_locals_size +                  // the addition space for locals
          2*BytesPerWord +                     // return address and saved rbp
          2*BytesPerWord +                     // "static long no_params() method" issue
          sizeof(BytecodeInterpreter) +               // interpreterState
          monitor_size);                       // monitors
}

void BytecodeInterpreter::layout_interpreterState(interpreterState to_fill,
                                           frame* caller,
                                           frame* current,
                                           Method* method,
                                           intptr_t* locals,
                                           intptr_t* stack,
                                           intptr_t* stack_base,
                                           intptr_t* monitor_base,
                                           intptr_t* frame_bottom,
                                           bool is_top_frame
                                           )
{
  // What about any vtable?
  //
  to_fill->_thread = JavaThread::current();
  // This gets filled in later but make it something recognizable for now
  to_fill->_bcp = method->code_base();
  to_fill->_locals = locals;
  to_fill->_constants = method->constants()->cache();
  to_fill->_method = method;
  to_fill->_mdx = NULL;
  to_fill->_stack = stack;
  if (is_top_frame && JavaThread::current()->popframe_forcing_deopt_reexecution() ) {
    to_fill->_msg = deopt_resume2;
  } else {
    to_fill->_msg = method_resume;
  }
  to_fill->_result._to_call._bcp_advance = 0;
  to_fill->_result._to_call._callee_entry_point = NULL; // doesn't matter to anyone
  to_fill->_result._to_call._callee = NULL; // doesn't matter to anyone
  to_fill->_prev_link = NULL;

  to_fill->_sender_sp = caller->unextended_sp();

  if (caller->is_interpreted_frame()) {
    interpreterState prev  = caller->get_interpreterState();
    to_fill->_prev_link = prev;
    // *current->register_addr(GR_Iprev_state) = (intptr_t) prev;
    // Make the prev callee look proper
    prev->_result._to_call._callee = method;
    if (*prev->_bcp == Bytecodes::_invokeinterface) {
      prev->_result._to_call._bcp_advance = 5;
    } else {
      prev->_result._to_call._bcp_advance = 3;
    }
  }
  to_fill->_oop_temp = NULL;
  to_fill->_stack_base = stack_base;
  // Need +1 here because stack_base points to the word just above the first expr stack entry
  // and stack_limit is supposed to point to the word just below the last expr stack entry.
  // See generate_compute_interpreter_state.
  to_fill->_stack_limit = stack_base - (method->max_stack() + 1);
  to_fill->_monitor_base = (BasicObjectLock*) monitor_base;

  to_fill->_self_link = to_fill;
  assert(stack >= to_fill->_stack_limit && stack < to_fill->_stack_base,
         "Stack top out of range");
}

int AbstractInterpreter::layout_activation(Method* method,
                                           int tempcount,  //
                                           int popframe_extra_args,
                                           int moncount,
                                           int caller_actual_parameters,
                                           int callee_param_count,
                                           int callee_locals,
                                           frame* caller,
                                           frame* interpreter_frame,
                                           bool is_top_frame,
                                           bool is_bottom_frame) {

  assert(popframe_extra_args == 0, "FIX ME");
  // NOTE this code must exactly mimic what InterpreterGenerator::generate_compute_interpreter_state()
  // does as far as allocating an interpreter frame.
  // If interpreter_frame!=NULL, set up the method, locals, and monitors.
  // The frame interpreter_frame, if not NULL, is guaranteed to be the right size,
  // as determined by a previous call to this method.
  // It is also guaranteed to be walkable even though it is in a skeletal state
  // NOTE: return size is in words not bytes
  // NOTE: tempcount is the current size of the java expression stack. For top most
  //       frames we will allocate a full sized expression stack and not the curback
  //       version that non-top frames have.

  // Calculate the amount our frame will be adjust by the callee. For top frame
  // this is zero.

  // NOTE: ia64 seems to do this wrong (or at least backwards) in that it
  // calculates the extra locals based on itself. Not what the callee does
  // to it. So it ignores last_frame_adjust value. Seems suspicious as far
  // as getting sender_sp correct.

  int extra_locals_size = (callee_locals - callee_param_count) * BytesPerWord;
  int monitor_size = sizeof(BasicObjectLock) * moncount;

  // First calculate the frame size without any java expression stack
  int short_frame_size = size_activation_helper(extra_locals_size,
                                                monitor_size);

  // Now with full size expression stack
  int full_frame_size = short_frame_size + method->max_stack() * BytesPerWord;

  // and now with only live portion of the expression stack
  short_frame_size = short_frame_size + tempcount * BytesPerWord;

  // the size the activation is right now. Only top frame is full size
  int frame_size = (is_top_frame ? full_frame_size : short_frame_size);

  if (interpreter_frame != NULL) {
#ifdef ASSERT
    assert(caller->unextended_sp() == interpreter_frame->interpreter_frame_sender_sp(), "Frame not properly walkable");
#endif

    // MUCHO HACK

    intptr_t* frame_bottom = (intptr_t*) ((intptr_t)interpreter_frame->sp() - (full_frame_size - frame_size));

    /* Now fillin the interpreterState object */

    // The state object is the first thing on the frame and easily located

    interpreterState cur_state = (interpreterState) ((intptr_t)interpreter_frame->fp() - sizeof(BytecodeInterpreter));


    // Find the locals pointer. This is rather simple on x86 because there is no
    // confusing rounding at the callee to account for. We can trivially locate
    // our locals based on the current fp().
    // Note: the + 2 is for handling the "static long no_params() method" issue.
    // (too bad I don't really remember that issue well...)

    intptr_t* locals;
    // If the caller is interpreted we need to make sure that locals points to the first
    // argument that the caller passed and not in an area where the stack might have been extended.
    // because the stack to stack to converter needs a proper locals value in order to remove the
    // arguments from the caller and place the result in the proper location. Hmm maybe it'd be
    // simpler if we simply stored the result in the BytecodeInterpreter object and let the c++ code
    // adjust the stack?? HMMM QQQ
    //
    if (caller->is_interpreted_frame()) {
      // locals must agree with the caller because it will be used to set the
      // caller's tos when we return.
      interpreterState prev  = caller->get_interpreterState();
      // stack() is prepushed.
      locals = prev->stack() + method->size_of_parameters();
      // locals = caller->unextended_sp() + (method->size_of_parameters() - 1);
      if (locals != interpreter_frame->fp() + frame::sender_sp_offset + (method->max_locals() - 1) + 2) {
        // os::breakpoint();
      }
    } else {
      // this is where a c2i would have placed locals (except for the +2)
      locals = interpreter_frame->fp() + frame::sender_sp_offset + (method->max_locals() - 1) + 2;
    }

    intptr_t* monitor_base = (intptr_t*) cur_state;
    intptr_t* stack_base = (intptr_t*) ((intptr_t) monitor_base - monitor_size);
    /* +1 because stack is always prepushed */
    intptr_t* stack = (intptr_t*) ((intptr_t) stack_base - (tempcount + 1) * BytesPerWord);


    BytecodeInterpreter::layout_interpreterState(cur_state,
                                          caller,
                                          interpreter_frame,
                                          method,
                                          locals,
                                          stack,
                                          stack_base,
                                          monitor_base,
                                          frame_bottom,
                                          is_top_frame);

    // BytecodeInterpreter::pd_layout_interpreterState(cur_state, interpreter_return_address, interpreter_frame->fp());
  }
  return frame_size/BytesPerWord;
}

#endif // CC_INTERP (all)
