/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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
Register InterpreterRuntime::SignatureHandlerGenerator::from() { return xlocals; }
Register InterpreterRuntime::SignatureHandlerGenerator::to()   { return sp; }
Register InterpreterRuntime::SignatureHandlerGenerator::temp() { return t0; }

InterpreterRuntime::SignatureHandlerGenerator::SignatureHandlerGenerator(
  const methodHandle& method, CodeBuffer* buffer) : NativeSignatureIterator(method) {
  _masm = new MacroAssembler(buffer); // allocate on resourse area by default
  _num_int_args = (method->is_static() ? 1 : 0);
  _num_fp_args = 0;
  _stack_offset = 0;
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_int() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset()));

  if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
    __ lw(g_INTArgReg[++_num_int_args], src);
  } else {
    __ lw(x10, src);
    __ sw(x10, Address(to(), _stack_offset));
    _stack_offset += wordSize;
    _num_int_args++;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_long() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset() + 1));

  if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
    __ ld(g_INTArgReg[++_num_int_args], src);
  } else {
    __ ld(x10, src);
    __ sd(x10, Address(to(), _stack_offset));
    _stack_offset += wordSize;
    _num_int_args++;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_float() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset()));

  if (_num_fp_args < Argument::n_float_register_parameters_c) {
    // to c_farg
    __ flw(g_FPArgReg[_num_fp_args++], src);
  } else if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
    // to c_rarg
    __ lwu(g_INTArgReg[++_num_int_args], src);
  } else {
    // to stack
    __ lwu(x10, src);
    __ sw(x10, Address(to(), _stack_offset));
    _stack_offset += wordSize;
    _num_fp_args++;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_double() {
  const Address src(from(), Interpreter::local_offset_in_bytes(offset() + 1));

  if (_num_fp_args < Argument::n_float_register_parameters_c) {
    // to c_farg
    __ fld(g_FPArgReg[_num_fp_args++], src);
  } else if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
    // to c_rarg
    __ ld(g_INTArgReg[++_num_int_args], src);
  } else {
    // to stack
    __ ld(x10, src);
    __ sd(x10, Address(to(), _stack_offset));
    _stack_offset += wordSize;
    _num_fp_args++;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::pass_object() {

  if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
    // to reg
    if (_num_int_args == 0) {
      assert(offset() == 0, "argument register 1 can only be (non-null) receiver");
      __ addi(c_rarg1, from(), Interpreter::local_offset_in_bytes(offset()));
      _num_int_args++;
    } else {
      // c_rarg2-c_rarg7
      __ addi(x10, from(), Interpreter::local_offset_in_bytes(offset()));
      __ mv(g_INTArgReg[++_num_int_args], 0); //_num_int_args:c_rarg -> 1:c_rarg2,  2:c_rarg3...
      __ ld(temp(), x10);
      Label L;
      __ beqz(temp(), L);
      __ mv(g_INTArgReg[_num_int_args], x10);
      __ bind(L);
    }
  } else {
    //to stack
    __ addi(x10, from(), Interpreter::local_offset_in_bytes(offset()));
    __ ld(temp(), x10);
    Label L;
    __ bnez(temp(), L);
    __ mv(x10, zr);
    __ bind(L);
    __ sd(x10, Address(to(), _stack_offset));
    _stack_offset += wordSize;
    _num_int_args++;
  }
}

void InterpreterRuntime::SignatureHandlerGenerator::generate(uint64_t fingerprint) {
  // generate code to handle arguments
  iterate(fingerprint);

  // return result handler
  __ la(x10, ExternalAddress(Interpreter::result_handler(method()->result_type())));
  __ ret();

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
    jint from_obj = *(jint *)(_from + Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;

    if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
      *_int_args++ = from_obj;
      _num_int_args++;
    } else {
      *_to++ = from_obj;
      _num_int_args++;
    }
  }

  virtual void pass_long()
  {
    intptr_t from_obj = *(intptr_t*)(_from + Interpreter::local_offset_in_bytes(1));
    _from -= 2*Interpreter::stackElementSize;

    if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
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

    if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
      *_int_args++ = (*from_addr == 0) ? NULL : (intptr_t)from_addr;
      _num_int_args++;
    } else {
      *_to++ = (*from_addr == 0) ? NULL : (intptr_t) from_addr;
      _num_int_args++;
    }
  }

  virtual void pass_float()
  {
    jint from_obj = *(jint*)(_from + Interpreter::local_offset_in_bytes(0));
    _from -= Interpreter::stackElementSize;

    if (_num_fp_args < Argument::n_float_register_parameters_c) {
      *_fp_args++ = from_obj;
      _num_fp_args++;
    } else if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
      *_int_args++ = from_obj;
      _num_int_args++;
    } else {
      *_to++ = from_obj;
      _num_fp_args++;
    }
  }

  virtual void pass_double()
  {
    intptr_t from_obj = *(intptr_t*)(_from + Interpreter::local_offset_in_bytes(1));
    _from -= 2*Interpreter::stackElementSize;

    if (_num_fp_args < Argument::n_float_register_parameters_c) {
      *_fp_args++ = from_obj;
      *_fp_identifiers |= (1ull << _num_fp_args); // mark as double
      _num_fp_args++;
    } else if (_num_int_args < Argument::n_int_register_parameters_c - 1) {
      // ld/st from_obj as integer, no need to mark _fp_identifiers
      *_int_args++ = from_obj;
      _num_int_args++;
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
  ~SlowSignatureHandler()
  {
    _from           = NULL;
    _to             = NULL;
    _int_args       = NULL;
    _fp_args        = NULL;
    _fp_identifiers = NULL;
  }
};


JRT_ENTRY(address,
          InterpreterRuntime::slow_signature_handler(JavaThread* current,
                                                     Method* method,
                                                     intptr_t* from,
                                                     intptr_t* to))
  methodHandle m(current, (Method*)method);
  assert(m->is_native(), "sanity check");

  // handle arguments
  SlowSignatureHandler ssh(m, (address)from, to);
  ssh.iterate(UCONST64(-1));

  // return result handler
  return Interpreter::result_handler(m->result_type());
JRT_END
