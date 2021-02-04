/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
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

  if (_num_int_args < Argument::n_int_register_parameters_c-1) {
    __ ldr(as_Register(_num_int_args + c_rarg1->encoding()), src);
  } else {
    __ ldrw(r0, src);
    __ strw(r0, Address(to(), _stack_offset));
    _stack_offset += wordSize;
  }

  _num_int_args++;
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_long() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset() + 1));

  if (_num_int_args < Argument::n_int_register_parameters_c-1) {
    __ ldr(as_Register(_num_int_args + c_rarg1->encoding()), src);
  } else {
    __ ldr(r0, src);
    __ str(r0, Address(to(), _stack_offset));
    _stack_offset += wordSize;
  }

  _num_int_args++;
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_float() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset()));

  if (_num_fp_args < Argument::n_float_register_parameters_c) {
    __ ldrs(as_FloatRegister(_num_fp_args), src);
  } else {
    __ ldrw(r0, src);
    __ strw(r0, Address(to(), _stack_offset));
    _stack_offset += wordSize;
  }
  _num_fp_args++;
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_double() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset() + 1));

  if (_num_fp_args < Argument::n_float_register_parameters_c) {
    __ ldrd(as_FloatRegister(_num_fp_args), src);
  } else {
    __ ldr(r0, src);
    __ str(r0, Address(to(), _stack_offset));
    _stack_offset += wordSize;
  }
  _num_fp_args++;
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_object() {

  if (_num_int_args == 0) {
    assert(offset() == 0, "argument register 1 can only be (non-null) receiver");
    __ add(c_rarg1, from(), Interpreter::local_offset_in_bytes(offset()));
  } else if (_num_int_args < Argument::n_int_register_parameters_c-1) {
    Register target = as_Register(_num_int_args + c_rarg1->encoding());
    __ add(r0, from(), Interpreter::local_offset_in_bytes(offset()));
    __ mov(target, 0);
    __ ldr(temp(), r0);
    Label L;
    __ cbz(temp(), L);
    __ mov(target, r0);
    __ bind(L);
  } else {
    __ add(r0, from(), Interpreter::local_offset_in_bytes(offset()));
    __ ldr(temp(), r0);
    Label L;
    __ cbnz(temp(), L);
    __ mov(r0, zr);
    __ bind(L);
    __ str(r0, Address(to(), _stack_offset));
    _stack_offset += wordSize;
  }

  _num_int_args++;
}

void InterpreterRuntime::SignatureHandlerGenerator::generate(uint64_t fingerprint) {
  // generate code to handle arguments
  iterate(fingerprint);

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

  void pass(BasicType type) {
    intptr_t* from_addr = (intptr_t*)(_from+
        Interpreter::local_offset_in_bytes(is_double_word_type(type)));
    _from -= (1+is_double_word_type(type))*Interpreter::stackElementSize;

    intptr_t from_val = type != T_OBJECT ? (*from_addr) :
      (*from_addr == 0 ? NULL : (intptr_t)from_addr);

    if (is_integral_type(type) && _num_int_args < Argument::n_int_register_parameters_c-1) {
      *_int_args++ = from_val;
    } else if (is_floating_point_type(type) && _num_fp_args < Argument::n_float_register_parameters_c) {
      *_fp_args++ = from_val;
      if (type == T_DOUBLE) {
        *_fp_identifiers |= (1ull << _num_fp_args); // mark as double
      }
    } else {
      *_to++ = from_val;
    }

    if (is_integral_type(type)) {
      ++_num_int_args;
    } else if (is_floating_point_type(type)) {
      ++_num_fp_args;
    }
  }

  virtual void pass_int()    { pass(T_INT);    }
  virtual void pass_long()   { pass(T_LONG);   }
  virtual void pass_object() { pass(T_OBJECT); }
  virtual void pass_float()  { pass(T_FLOAT);  }
  virtual void pass_double() { pass(T_DOUBLE); }

#if 0
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
    } else {
      *_to++ = from_obj;
    }
    _num_int_args++;
  }

  virtual void pass_object()
  {
    intptr_t *from_addr = (intptr_t*)(_from + Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;

    if (_num_int_args < Argument::n_int_register_parameters_c-1) {
      *_int_args++ = (*from_addr == 0) ? NULL : (intptr_t)from_addr;
    } else {
      *_to++ = (*from_addr == 0) ? NULL : (intptr_t) from_addr;
    }
    _num_int_args++;
  }

  virtual void pass_float()
  {
    jint from_obj = *(jint*)(_from+Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;

    if (_num_fp_args < Argument::n_float_register_parameters_c) {
      *_fp_args++ = from_obj;
    } else {
      *_to++ = from_obj;
    }
    _num_fp_args++;
  }

  virtual void pass_double()
  {
    intptr_t from_obj = *(intptr_t*)(_from+Interpreter::local_offset_in_bytes(1));
    _from -= 2*Interpreter::stackElementSize;

    if (_num_fp_args < Argument::n_float_register_parameters_c) {
      *_fp_args++ = from_obj;
      *_fp_identifiers |= (1ull << _num_fp_args); // mark as double
    } else {
      *_to++ = from_obj;
    }
    _num_fp_args++;
  }
#endif

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

};


JRT_ENTRY(address,
          InterpreterRuntime::slow_signature_handler(JavaThread* thread,
                                                     Method* method,
                                                     intptr_t* from,
                                                     intptr_t* to))
  methodHandle m(thread, (Method*)method);
  assert(m->is_native(), "sanity check");

  // handle arguments
  SlowSignatureHandler ssh(m, (address)from, to);
  ssh.iterate((uint64_t)CONST64(-1));

  // return result handler
  return Interpreter::result_handler(m->result_type());
JRT_END
