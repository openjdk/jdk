/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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
Register InterpreterRuntime::SignatureHandlerGenerator::from() { return rlocals; }
Register InterpreterRuntime::SignatureHandlerGenerator::to()   { return sp; }
Register InterpreterRuntime::SignatureHandlerGenerator::temp() { return rscratch1; }

InterpreterRuntime::SignatureHandlerGenerator::SignatureHandlerGenerator(
      const methodHandle& method, CodeBuffer* buffer) : NativeSignatureIterator(method) {
  _masm = new MacroAssembler(buffer);
  _num_int_args = (method->is_static() ? 1 : 0);
  _num_fp_args = 0;
  _stack_offset = 0;
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_int() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset()));

  switch (_num_int_args) {
  case 0:
    __ ldr(c_rarg1, src);
    _num_int_args++;
    break;
  case 1:
    __ ldr(c_rarg2, src);
    _num_int_args++;
    break;
  case 2:
    __ ldr(c_rarg3, src);
    _num_int_args++;
    break;
  case 3:
    __ ldr(c_rarg4, src);
    _num_int_args++;
    break;
  case 4:
    __ ldr(c_rarg5, src);
    _num_int_args++;
    break;
  case 5:
    __ ldr(c_rarg6, src);
    _num_int_args++;
    break;
  case 6:
    __ ldr(c_rarg7, src);
    _num_int_args++;
    break;
  default:
    __ ldr(r0, src);
    __ str(r0, Address(to(), _stack_offset));
    _stack_offset += wordSize;
    _num_int_args++;
    break;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_long() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset() + 1));

  switch (_num_int_args) {
  case 0:
    __ ldr(c_rarg1, src);
    _num_int_args++;
    break;
  case 1:
    __ ldr(c_rarg2, src);
    _num_int_args++;
    break;
  case 2:
    __ ldr(c_rarg3, src);
    _num_int_args++;
    break;
  case 3:
    __ ldr(c_rarg4, src);
    _num_int_args++;
    break;
  case 4:
    __ ldr(c_rarg5, src);
    _num_int_args++;
    break;
  case 5:
    __ ldr(c_rarg6, src);
    _num_int_args++;
    break;
  case 6:
    __ ldr(c_rarg7, src);
    _num_int_args++;
    break;
  default:
    __ ldr(r0, src);
    __ str(r0, Address(to(), _stack_offset));
    _stack_offset += wordSize;
    _num_int_args++;
    break;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_float() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset()));

  if (_num_fp_args < Argument::n_float_register_parameters_c) {
    __ ldrs(as_FloatRegister(_num_fp_args++), src);
  } else {
    __ ldrw(r0, src);
    __ strw(r0, Address(to(), _stack_offset));
    _stack_offset += wordSize;
    _num_fp_args++;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_double() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset() + 1));

  if (_num_fp_args < Argument::n_float_register_parameters_c) {
    __ ldrd(as_FloatRegister(_num_fp_args++), src);
  } else {
    __ ldr(r0, src);
    __ str(r0, Address(to(), _stack_offset));
    _stack_offset += wordSize;
    _num_fp_args++;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_object() {

  switch (_num_int_args) {
  case 0:
    assert(offset() == 0, "argument register 1 can only be (non-null) receiver");
    __ add(c_rarg1, from(), Interpreter::local_offset_in_bytes(offset()));
    _num_int_args++;
    break;
  case 1:
    {
      __ add(r0, from(), Interpreter::local_offset_in_bytes(offset()));
      __ mov(c_rarg2, 0);
      __ ldr(temp(), r0);
      Label L;
      __ cbz(temp(), L);
      __ mov(c_rarg2, r0);
      __ bind(L);
      _num_int_args++;
      break;
    }
  case 2:
    {
      __ add(r0, from(), Interpreter::local_offset_in_bytes(offset()));
      __ mov(c_rarg3, 0);
      __ ldr(temp(), r0);
      Label L;
      __ cbz(temp(), L);
      __ mov(c_rarg3, r0);
      __ bind(L);
      _num_int_args++;
      break;
    }
  case 3:
    {
      __ add(r0, from(), Interpreter::local_offset_in_bytes(offset()));
      __ mov(c_rarg4, 0);
      __ ldr(temp(), r0);
      Label L;
      __ cbz(temp(), L);
      __ mov(c_rarg4, r0);
      __ bind(L);
      _num_int_args++;
      break;
    }
  case 4:
    {
      __ add(r0, from(), Interpreter::local_offset_in_bytes(offset()));
      __ mov(c_rarg5, 0);
      __ ldr(temp(), r0);
      Label L;
      __ cbz(temp(), L);
      __ mov(c_rarg5, r0);
      __ bind(L);
      _num_int_args++;
      break;
    }
  case 5:
    {
      __ add(r0, from(), Interpreter::local_offset_in_bytes(offset()));
      __ mov(c_rarg6, 0);
      __ ldr(temp(), r0);
      Label L;
      __ cbz(temp(), L);
      __ mov(c_rarg6, r0);
      __ bind(L);
      _num_int_args++;
      break;
    }
  case 6:
    {
      __ add(r0, from(), Interpreter::local_offset_in_bytes(offset()));
      __ mov(c_rarg7, 0);
      __ ldr(temp(), r0);
      Label L;
      __ cbz(temp(), L);
      __ mov(c_rarg7, r0);
      __ bind(L);
      _num_int_args++;
      break;
    }
 default:
   {
      __ add(r0, from(), Interpreter::local_offset_in_bytes(offset()));
      __ ldr(temp(), r0);
      Label L;
      __ cbnz(temp(), L);
      __ mov(r0, zr);
      __ bind(L);
      __ str(r0, Address(to(), _stack_offset));
      _stack_offset += wordSize;
      _num_int_args++;
      break;
   }
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::generate(uint64_t fingerprint) {
  // generate code to handle arguments
  iterate(fingerprint);

  // set the call format
  // n.b. allow extra 1 for the JNI_Env in c_rarg0
  unsigned int call_format = ((_num_int_args + 1) << 6) | (_num_fp_args << 2);

  switch (method()->result_type()) {
  case T_VOID:
    call_format |= MacroAssembler::ret_type_void;
    break;
  case T_FLOAT:
    call_format |= MacroAssembler::ret_type_float;
    break;
  case T_DOUBLE:
    call_format |= MacroAssembler::ret_type_double;
    break;
  default:
    call_format |= MacroAssembler::ret_type_integral;
    break;
  }

  // // store the call format in the method
  // __ movw(r0, call_format);
  // __ str(r0, Address(rmethod, Method::call_format_offset()));

  // return result handler
  __ lea(r0, ExternalAddress(Interpreter::result_handler(method()->result_type())));
  __ ret(lr);

  __ flush();
}


// Implementation of SignatureHandlerLibrary

void SignatureHandlerLibrary::pd_set_handler(address handler) {}


class SlowSignatureHandler
  : public NativeSignatureIterator {
 private:
  address   _from;
  intptr_t* _to;
  intptr_t* _int_args;
  intptr_t* _fp_args;
  intptr_t* _fp_identifiers;
  unsigned int _num_int_args;
  unsigned int _num_fp_args;

  virtual void pass_int()
  {
    jint from_obj = *(jint *)(_from+Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;

    if (_num_int_args < Argument::n_int_register_parameters_c-1) {
      *_int_args++ = from_obj;
      _num_int_args++;
    } else {
      *_to++ = from_obj;
      _num_int_args++;
    }
  }

  virtual void pass_long()
  {
    intptr_t from_obj = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
    _from -= 2*Interpreter::stackElementSize;

    if (_num_int_args < Argument::n_int_register_parameters_c-1) {
      *_int_args++ = from_obj;
      _num_int_args++;
    } else {
      *_to++ = from_obj;
      _num_int_args++;
    }
  }

  virtual void pass_object()
  {
    intptr_t *from_addr = (intptr_t*)(_from + Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;

    if (_num_int_args < Argument::n_int_register_parameters_c-1) {
      *_int_args++ = (*from_addr == 0) ? NULL : (intptr_t)from_addr;
      _num_int_args++;
    } else {
      *_to++ = (*from_addr == 0) ? NULL : (intptr_t) from_addr;
      _num_int_args++;
    }
  }

  virtual void pass_float()
  {
    jint from_obj = *(jint*)(_from+Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;

    if (_num_fp_args < Argument::n_float_register_parameters_c) {
      *_fp_args++ = from_obj;
      _num_fp_args++;
    } else {
      *_to++ = from_obj;
      _num_fp_args++;
    }
  }

  virtual void pass_double()
  {
    intptr_t from_obj = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
    _from -= 2*Interpreter::stackElementSize;

    if (_num_fp_args < Argument::n_float_register_parameters_c) {
      *_fp_args++ = from_obj;
      *_fp_identifiers |= (1 << _num_fp_args); // mark as double
      _num_fp_args++;
    } else {
      *_to++ = from_obj;
      _num_fp_args++;
    }
  }

 public:
  SlowSignatureHandler(const methodHandle& method, address from, intptr_t* to)
    : NativeSignatureIterator(method)
  {
    _from = from;
    _to   = to;

    _int_args = to - (method->is_static() ? 16 : 17);
    _fp_args =  to - 8;
    _fp_identifiers = to - 9;
    *(int*) _fp_identifiers = 0;
    _num_int_args = (method->is_static() ? 1 : 0);
    _num_fp_args = 0;
  }

  // n.b. allow extra 1 for the JNI_Env in c_rarg0
  unsigned int get_call_format()
  {
    unsigned int call_format = ((_num_int_args + 1) << 6) | (_num_fp_args << 2);

    switch (method()->result_type()) {
    case T_VOID:
      call_format |= MacroAssembler::ret_type_void;
      break;
    case T_FLOAT:
      call_format |= MacroAssembler::ret_type_float;
      break;
    case T_DOUBLE:
      call_format |= MacroAssembler::ret_type_double;
      break;
    default:
      call_format |= MacroAssembler::ret_type_integral;
      break;
    }

    return call_format;
  }
};


IRT_ENTRY(address,
          InterpreterRuntime::slow_signature_handler(JavaThread* thread,
                                                     Method* method,
                                                     intptr_t* from,
                                                     intptr_t* to))
  methodHandle m(thread, (Method*)method);
  assert(m->is_native(), "sanity check");

  // handle arguments
  SlowSignatureHandler ssh(m, (address)from, to);
  ssh.iterate(UCONST64(-1));

  // // set the call format
  // method->set_call_format(ssh.get_call_format());

  // return result handler
  return Interpreter::result_handler(m->result_type());
IRT_END
