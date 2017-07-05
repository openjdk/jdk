/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * Copyright 2007, 2008, 2010 Red Hat, Inc.
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
#include "incls/_interpreterRT_zero.cpp.incl"

void InterpreterRuntime::SignatureHandlerGeneratorBase::pass_int() {
  push(T_INT);
  _cif->nargs++;
}

void InterpreterRuntime::SignatureHandlerGeneratorBase::pass_long() {
  push(T_LONG);
  _cif->nargs++;
}

void InterpreterRuntime::SignatureHandlerGeneratorBase::pass_float() {
  push(T_FLOAT);
  _cif->nargs++;
}

void InterpreterRuntime::SignatureHandlerGeneratorBase::pass_double() {
  push(T_DOUBLE);
  _cif->nargs++;
}

void InterpreterRuntime::SignatureHandlerGeneratorBase::pass_object() {
  push(T_OBJECT);
  _cif->nargs++;
}

void InterpreterRuntime::SignatureHandlerGeneratorBase::push(BasicType type) {
  ffi_type *ftype;
  switch (type) {
  case T_VOID:
    ftype = &ffi_type_void;
    break;

  case T_BOOLEAN:
    ftype = &ffi_type_uint8;
    break;

  case T_CHAR:
    ftype = &ffi_type_uint16;
    break;

  case T_BYTE:
    ftype = &ffi_type_sint8;
    break;

  case T_SHORT:
    ftype = &ffi_type_sint16;
    break;

  case T_INT:
    ftype = &ffi_type_sint32;
    break;

  case T_LONG:
    ftype = &ffi_type_sint64;
    break;

  case T_FLOAT:
    ftype = &ffi_type_float;
    break;

  case T_DOUBLE:
    ftype = &ffi_type_double;
    break;

  case T_OBJECT:
  case T_ARRAY:
    ftype = &ffi_type_pointer;
    break;

  default:
    ShouldNotReachHere();
  }
  push((intptr_t) ftype);
}

// For fast signature handlers the "signature handler" is generated
// into a temporary buffer.  It is then copied to its final location,
// and pd_set_handler is called on it.  We have this two stage thing
// to accomodate this.

void InterpreterRuntime::SignatureHandlerGeneratorBase::generate(
  uint64_t fingerprint) {

  // Build the argument types list
  pass_object();
  if (method()->is_static())
    pass_object();
  iterate(fingerprint);

  // Tack on the result type
  push(method()->result_type());
}

void InterpreterRuntime::SignatureHandler::finalize() {
  ffi_status status =
    ffi_prep_cif(cif(),
                 FFI_DEFAULT_ABI,
                 argument_count(),
                 result_type(),
                 argument_types());

  assert(status == FFI_OK, "should be");
}

IRT_ENTRY(address,
          InterpreterRuntime::slow_signature_handler(JavaThread* thread,
                                                     methodOop   method,
                                                     intptr_t*   unused1,
                                                     intptr_t*   unused2))
  ZeroStack *stack = thread->zero_stack();

  int required_words =
    (align_size_up(sizeof(ffi_cif), wordSize) >> LogBytesPerWord) +
    (method->is_static() ? 2 : 1) + method->size_of_parameters() + 1;

  stack->overflow_check(required_words, CHECK_NULL);

  intptr_t *buf = (intptr_t *) stack->alloc(required_words * wordSize);
  SlowSignatureHandlerGenerator sshg(methodHandle(thread, method), buf);
  sshg.generate(UCONST64(-1));

  SignatureHandler *handler = sshg.handler();
  handler->finalize();

  return (address) handler;
IRT_END

void SignatureHandlerLibrary::pd_set_handler(address handlerAddr) {
  InterpreterRuntime::SignatureHandler *handler =
    InterpreterRuntime::SignatureHandler::from_handlerAddr(handlerAddr);

  handler->finalize();
}
