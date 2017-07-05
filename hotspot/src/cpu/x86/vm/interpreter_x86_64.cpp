/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_interpreter_x86_64.cpp.incl"

#define __ _masm->


#ifdef _WIN64
address AbstractInterpreterGenerator::generate_slow_signature_handler() {
  address entry = __ pc();

  // rbx: method
  // r14: pointer to locals
  // c_rarg3: first stack arg - wordSize
  __ movq(c_rarg3, rsp);
  // adjust rsp
  __ subq(rsp, 4 * wordSize);
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::slow_signature_handler),
             rbx, r14, c_rarg3);

  // rax: result handler

  // Stack layout:
  // rsp: 3 integer or float args (if static first is unused)
  //      1 float/double identifiers
  //        return address
  //        stack args
  //        garbage
  //        expression stack bottom
  //        bcp (NULL)
  //        ...

  // Do FP first so we can use c_rarg3 as temp
  __ movl(c_rarg3, Address(rsp, 3 * wordSize)); // float/double identifiers

  for ( int i= 0; i < Argument::n_int_register_parameters_c-1; i++ ) {
    XMMRegister floatreg = as_XMMRegister(i+1);
    Label isfloatordouble, isdouble, next;

    __ testl(c_rarg3, 1 << (i*2));      // Float or Double?
    __ jcc(Assembler::notZero, isfloatordouble);

    // Do Int register here
    switch ( i ) {
      case 0:
        __ movl(rscratch1, Address(rbx, methodOopDesc::access_flags_offset()));
        __ testl(rscratch1, JVM_ACC_STATIC);
        __ cmovq(Assembler::zero, c_rarg1, Address(rsp, 0));
        break;
      case 1:
        __ movq(c_rarg2, Address(rsp, wordSize));
        break;
      case 2:
        __ movq(c_rarg3, Address(rsp, 2 * wordSize));
        break;
      default:
        break;
    }

    __ jmp (next);

    __ bind(isfloatordouble);
    __ testl(c_rarg3, 1 << ((i*2)+1));     // Double?
    __ jcc(Assembler::notZero, isdouble);

// Do Float Here
    __ movflt(floatreg, Address(rsp, i * wordSize));
    __ jmp(next);

// Do Double here
    __ bind(isdouble);
    __ movdbl(floatreg, Address(rsp, i * wordSize));

    __ bind(next);
  }


  // restore rsp
  __ addq(rsp, 4 * wordSize);

  __ ret(0);

  return entry;
}
#else
address AbstractInterpreterGenerator::generate_slow_signature_handler() {
  address entry = __ pc();

  // rbx: method
  // r14: pointer to locals
  // c_rarg3: first stack arg - wordSize
  __ movq(c_rarg3, rsp);
  // adjust rsp
  __ subq(rsp, 14 * wordSize);
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::slow_signature_handler),
             rbx, r14, c_rarg3);

  // rax: result handler

  // Stack layout:
  // rsp: 5 integer args (if static first is unused)
  //      1 float/double identifiers
  //      8 double args
  //        return address
  //        stack args
  //        garbage
  //        expression stack bottom
  //        bcp (NULL)
  //        ...

  // Do FP first so we can use c_rarg3 as temp
  __ movl(c_rarg3, Address(rsp, 5 * wordSize)); // float/double identifiers

  for (int i = 0; i < Argument::n_float_register_parameters_c; i++) {
    const XMMRegister r = as_XMMRegister(i);

    Label d, done;

    __ testl(c_rarg3, 1 << i);
    __ jcc(Assembler::notZero, d);
    __ movflt(r, Address(rsp, (6 + i) * wordSize));
    __ jmp(done);
    __ bind(d);
    __ movdbl(r, Address(rsp, (6 + i) * wordSize));
    __ bind(done);
  }

  // Now handle integrals.  Only do c_rarg1 if not static.
  __ movl(c_rarg3, Address(rbx, methodOopDesc::access_flags_offset()));
  __ testl(c_rarg3, JVM_ACC_STATIC);
  __ cmovq(Assembler::zero, c_rarg1, Address(rsp, 0));

  __ movq(c_rarg2, Address(rsp, wordSize));
  __ movq(c_rarg3, Address(rsp, 2 * wordSize));
  __ movq(c_rarg4, Address(rsp, 3 * wordSize));
  __ movq(c_rarg5, Address(rsp, 4 * wordSize));

  // restore rsp
  __ addq(rsp, 14 * wordSize);

  __ ret(0);

  return entry;
}
#endif


//
// Various method entries
//

address InterpreterGenerator::generate_math_entry(
  AbstractInterpreter::MethodKind kind) {
  // rbx: methodOop

  if (!InlineIntrinsics) return NULL; // Generate a vanilla entry

  assert(kind == Interpreter::java_lang_math_sqrt,
         "Other intrinsics are not special");

  address entry_point = __ pc();

  // These don't need a safepoint check because they aren't virtually
  // callable. We won't enter these intrinsics from compiled code.
  // If in the future we added an intrinsic which was virtually callable
  // we'd have to worry about how to safepoint so that this code is used.

  // mathematical functions inlined by compiler
  // (interpreter must provide identical implementation
  // in order to avoid monotonicity bugs when switching
  // from interpreter to compiler in the middle of some
  // computation)

  // Note: For JDK 1.2 StrictMath doesn't exist and Math.sin/cos/sqrt are
  //       native methods. Interpreter::method_kind(...) does a check for
  //       native methods first before checking for intrinsic methods and
  //       thus will never select this entry point. Make sure it is not
  //       called accidentally since the SharedRuntime entry points will
  //       not work for JDK 1.2.
  //
  // We no longer need to check for JDK 1.2 since it's EOL'ed.
  // The following check existed in pre 1.6 implementation,
  //    if (Universe::is_jdk12x_version()) {
  //      __ should_not_reach_here();
  //    }
  // Universe::is_jdk12x_version() always returns false since
  // the JDK version is not yet determined when this method is called.
  // This method is called during interpreter_init() whereas
  // JDK version is only determined when universe2_init() is called.

  // Note: For JDK 1.3 StrictMath exists and Math.sin/cos/sqrt are
  //       java methods.  Interpreter::method_kind(...) will select
  //       this entry point for the corresponding methods in JDK 1.3.
  __ sqrtsd(xmm0, Address(rsp, wordSize));

  __ popq(rax);
  __ movq(rsp, r13);
  __ jmp(rax);

  return entry_point;
}


// Abstract method entry
// Attempt to execute abstract method. Throw exception
address InterpreterGenerator::generate_abstract_entry(void) {
  // rbx: methodOop
  // r13: sender SP

  address entry_point = __ pc();

  // abstract method entry
  // remove return address. Not really needed, since exception
  // handling throws away expression stack
  __ popq(rbx);

  // adjust stack to what a normal return would do
  __ movq(rsp, r13);

  // throw exception
  __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                             InterpreterRuntime::throw_AbstractMethodError));
  // the call_VM checks for exception, so we should never return here.
  __ should_not_reach_here();

  return entry_point;
}


// Empty method, generate a very fast return.

address InterpreterGenerator::generate_empty_entry(void) {
  // rbx: methodOop
  // r13: sender sp must set sp to this value on return

  if (!UseFastEmptyMethods) {
    return NULL;
  }

  address entry_point = __ pc();

  // If we need a safepoint check, generate full interpreter entry.
  Label slow_path;
  __ cmp32(ExternalAddress(SafepointSynchronize::address_of_state()),
           SafepointSynchronize::_not_synchronized);
  __ jcc(Assembler::notEqual, slow_path);

  // do nothing for empty methods (do not even increment invocation counter)
  // Code: _return
  // _return
  // return w/o popping parameters
  __ popq(rax);
  __ movq(rsp, r13);
  __ jmp(rax);

  __ bind(slow_path);
  (void) generate_normal_entry(false);
  return entry_point;

}

// Call an accessor method (assuming it is resolved, otherwise drop
// into vanilla (slow path) entry
address InterpreterGenerator::generate_accessor_entry(void) {
  // rbx: methodOop

  // r13: senderSP must preserver for slow path, set SP to it on fast path

  address entry_point = __ pc();
  Label xreturn_path;

  // do fastpath for resolved accessor methods
  if (UseFastAccessorMethods) {
    // Code: _aload_0, _(i|a)getfield, _(i|a)return or any rewrites
    //       thereof; parameter size = 1
    // Note: We can only use this code if the getfield has been resolved
    //       and if we don't have a null-pointer exception => check for
    //       these conditions first and use slow path if necessary.
    Label slow_path;
    // If we need a safepoint check, generate full interpreter entry.
    __ cmp32(ExternalAddress(SafepointSynchronize::address_of_state()),
             SafepointSynchronize::_not_synchronized);

    __ jcc(Assembler::notEqual, slow_path);
    // rbx: method
    __ movq(rax, Address(rsp, wordSize));

    // check if local 0 != NULL and read field
    __ testq(rax, rax);
    __ jcc(Assembler::zero, slow_path);

    __ movq(rdi, Address(rbx, methodOopDesc::constants_offset()));
    // read first instruction word and extract bytecode @ 1 and index @ 2
    __ movq(rdx, Address(rbx, methodOopDesc::const_offset()));
    __ movl(rdx, Address(rdx, constMethodOopDesc::codes_offset()));
    // Shift codes right to get the index on the right.
    // The bytecode fetched looks like <index><0xb4><0x2a>
    __ shrl(rdx, 2 * BitsPerByte);
    __ shll(rdx, exact_log2(in_words(ConstantPoolCacheEntry::size())));
    __ movq(rdi, Address(rdi, constantPoolOopDesc::cache_offset_in_bytes()));

    // rax: local 0
    // rbx: method
    // rdx: constant pool cache index
    // rdi: constant pool cache

    // check if getfield has been resolved and read constant pool cache entry
    // check the validity of the cache entry by testing whether _indices field
    // contains Bytecode::_getfield in b1 byte.
    assert(in_words(ConstantPoolCacheEntry::size()) == 4,
           "adjust shift below");
    __ movl(rcx,
            Address(rdi,
                    rdx,
                    Address::times_8,
                    constantPoolCacheOopDesc::base_offset() +
                    ConstantPoolCacheEntry::indices_offset()));
    __ shrl(rcx, 2 * BitsPerByte);
    __ andl(rcx, 0xFF);
    __ cmpl(rcx, Bytecodes::_getfield);
    __ jcc(Assembler::notEqual, slow_path);

    // Note: constant pool entry is not valid before bytecode is resolved
    __ movq(rcx,
            Address(rdi,
                    rdx,
                    Address::times_8,
                    constantPoolCacheOopDesc::base_offset() +
                    ConstantPoolCacheEntry::f2_offset()));
    // edx: flags
    __ movl(rdx,
            Address(rdi,
                    rdx,
                    Address::times_8,
                    constantPoolCacheOopDesc::base_offset() +
                    ConstantPoolCacheEntry::flags_offset()));

    Label notObj, notInt, notByte, notShort;
    const Address field_address(rax, rcx, Address::times_1);

    // Need to differentiate between igetfield, agetfield, bgetfield etc.
    // because they are different sizes.
    // Use the type from the constant pool cache
    __ shrl(rdx, ConstantPoolCacheEntry::tosBits);
    // Make sure we don't need to mask edx for tosBits after the above shift
    ConstantPoolCacheEntry::verify_tosBits();

    __ cmpl(rdx, atos);
    __ jcc(Assembler::notEqual, notObj);
    // atos
    __ load_heap_oop(rax, field_address);
    __ jmp(xreturn_path);

    __ bind(notObj);
    __ cmpl(rdx, itos);
    __ jcc(Assembler::notEqual, notInt);
    // itos
    __ movl(rax, field_address);
    __ jmp(xreturn_path);

    __ bind(notInt);
    __ cmpl(rdx, btos);
    __ jcc(Assembler::notEqual, notByte);
    // btos
    __ load_signed_byte(rax, field_address);
    __ jmp(xreturn_path);

    __ bind(notByte);
    __ cmpl(rdx, stos);
    __ jcc(Assembler::notEqual, notShort);
    // stos
    __ load_signed_word(rax, field_address);
    __ jmp(xreturn_path);

    __ bind(notShort);
#ifdef ASSERT
    Label okay;
    __ cmpl(rdx, ctos);
    __ jcc(Assembler::equal, okay);
    __ stop("what type is this?");
    __ bind(okay);
#endif
    // ctos
    __ load_unsigned_word(rax, field_address);

    __ bind(xreturn_path);

    // _ireturn/_areturn
    __ popq(rdi);
    __ movq(rsp, r13);
    __ jmp(rdi);
    __ ret(0);

    // generate a vanilla interpreter entry as the slow path
    __ bind(slow_path);
    (void) generate_normal_entry(false);
  } else {
    (void) generate_normal_entry(false);
  }

  return entry_point;
}

// This method tells the deoptimizer how big an interpreted frame must be:
int AbstractInterpreter::size_activation(methodOop method,
                                         int tempcount,
                                         int popframe_extra_args,
                                         int moncount,
                                         int callee_param_count,
                                         int callee_locals,
                                         bool is_top_frame) {
  return layout_activation(method,
                           tempcount, popframe_extra_args, moncount,
                           callee_param_count, callee_locals,
                           (frame*) NULL, (frame*) NULL, is_top_frame);
}

void Deoptimization::unwind_callee_save_values(frame* f, vframeArray* vframe_array) {

  // This code is sort of the equivalent of C2IAdapter::setup_stack_frame back in
  // the days we had adapter frames. When we deoptimize a situation where a
  // compiled caller calls a compiled caller will have registers it expects
  // to survive the call to the callee. If we deoptimize the callee the only
  // way we can restore these registers is to have the oldest interpreter
  // frame that we create restore these values. That is what this routine
  // will accomplish.

  // At the moment we have modified c2 to not have any callee save registers
  // so this problem does not exist and this routine is just a place holder.

  assert(f->is_interpreted_frame(), "must be interpreted");
}
