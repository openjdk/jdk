/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interp_masm.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/universe.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/icache.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/signature.hpp"


#define __ _masm->


// Implementation of SignatureHandlerGenerator
InterpreterRuntime::SignatureHandlerGenerator::SignatureHandlerGenerator(
    const methodHandle& method, CodeBuffer* buffer) : NativeSignatureIterator(method) {
  _masm = new MacroAssembler(buffer);
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_word(int size_of_arg, int offset_in_arg) {
  Argument  jni_arg(jni_offset() + offset_in_arg, false);
  Register     Rtmp = O0;
  __ ld(Llocals, Interpreter::local_offset_in_bytes(offset()), Rtmp);

  __ store_argument(Rtmp, jni_arg);
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_long() {
  Argument  jni_arg(jni_offset(), false);
  Register  Rtmp = O0;

  __ ldx(Llocals, Interpreter::local_offset_in_bytes(offset() + 1), Rtmp);
  __ store_long_argument(Rtmp, jni_arg);
}


void InterpreterRuntime::SignatureHandlerGenerator::pass_float() {
  Argument  jni_arg(jni_offset(), false);
  FloatRegister  Rtmp = F0;
  __ ldf(FloatRegisterImpl::S, Llocals, Interpreter::local_offset_in_bytes(offset()), Rtmp);
  __ store_float_argument(Rtmp, jni_arg);
}


void InterpreterRuntime::SignatureHandlerGenerator::pass_double() {
  Argument  jni_arg(jni_offset(), false);
  FloatRegister  Rtmp = F0;
  __ ldf(FloatRegisterImpl::D, Llocals, Interpreter::local_offset_in_bytes(offset() + 1), Rtmp);
  __ store_double_argument(Rtmp, jni_arg);
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_object() {
  Argument  jni_arg(jni_offset(), false);
  Argument java_arg(    offset(), true);
  Register    Rtmp1 = O0;
  Register    Rtmp2 =  jni_arg.is_register() ?  jni_arg.as_register() : O0;
  Register    Rtmp3 =  G3_scratch;

  // the handle for a receiver will never be null
  bool do_NULL_check = offset() != 0 || is_static();

  Address     h_arg = Address(Llocals, Interpreter::local_offset_in_bytes(offset()));
  __ ld_ptr(h_arg, Rtmp1);
  if (!do_NULL_check) {
    __ add(h_arg.base(), h_arg.disp(), Rtmp2);
  } else {
    if (Rtmp1 == Rtmp2)
          __ tst(Rtmp1);
    else  __ addcc(G0, Rtmp1, Rtmp2); // optimize mov/test pair
    Label L;
    __ brx(Assembler::notZero, true, Assembler::pt, L);
    __ delayed()->add(h_arg.base(), h_arg.disp(), Rtmp2);
    __ bind(L);
  }
  __ store_ptr_argument(Rtmp2, jni_arg);    // this is often a no-op
}


void InterpreterRuntime::SignatureHandlerGenerator::generate(uint64_t fingerprint) {

  // generate code to handle arguments
  iterate(fingerprint);

  // return result handler
  AddressLiteral result_handler(Interpreter::result_handler(method()->result_type()));
  __ sethi(result_handler, Lscratch);
  __ retl();
  __ delayed()->add(Lscratch, result_handler.low10(), Lscratch);

  __ flush();
}


// Implementation of SignatureHandlerLibrary

void SignatureHandlerLibrary::pd_set_handler(address handler) {}


class SlowSignatureHandler: public NativeSignatureIterator {
 private:
  address   _from;
  intptr_t* _to;
  intptr_t* _RegArgSignature;                   // Signature of first Arguments to be passed in Registers
  uint      _argcount;

  enum {                                        // We need to differenciate float from non floats in reg args
    non_float  = 0,
    float_sig  = 1,
    double_sig = 2,
    long_sig   = 3
  };

  virtual void pass_int() {
    *_to++ = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;
    add_signature( non_float );
  }

  virtual void pass_object() {
    // pass address of from
    intptr_t *from_addr = (intptr_t*)(_from + Interpreter::local_offset_in_bytes(0));
    *_to++ = (*from_addr == 0) ? NULL : (intptr_t) from_addr;
    _from -= Interpreter::stackElementSize;
    add_signature( non_float );
   }

  virtual void pass_float()  {
    *_to++ = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;
    add_signature( float_sig );
   }

  virtual void pass_double() {
    *_to++ = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
    _from -= 2*Interpreter::stackElementSize;
   add_signature( double_sig );
   }

  virtual void pass_long() {
    _to[0] = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
    _to += 1;
    _from -= 2*Interpreter::stackElementSize;
    add_signature( long_sig );
  }

  virtual void add_signature( intptr_t sig_type ) {
    if ( _argcount < (sizeof (intptr_t))*4 ) {
      *_RegArgSignature |= (sig_type << (_argcount*2) );
      _argcount++;
    }
  }


 public:
  SlowSignatureHandler(const methodHandle& method, address from, intptr_t* to, intptr_t *RegArgSig) : NativeSignatureIterator(method) {
    _from = from;
    _to   = to;
    _RegArgSignature = RegArgSig;
    *_RegArgSignature = 0;
    _argcount = method->is_static() ? 2 : 1;
  }
};


IRT_ENTRY(address, InterpreterRuntime::slow_signature_handler(
                                                    JavaThread* thread,
                                                    Method* method,
                                                    intptr_t* from,
                                                    intptr_t* to ))
  methodHandle m(thread, method);
  assert(m->is_native(), "sanity check");
  // handle arguments
  // Warning: We use reg arg slot 00 temporarily to return the RegArgSignature
  // back to the code that pops the arguments into the CPU registers
  SlowSignatureHandler(m, (address)from, m->is_static() ? to+2 : to+1, to).iterate((uint64_t)CONST64(-1));
  // return result handler
  return Interpreter::result_handler(m->result_type());
IRT_END
