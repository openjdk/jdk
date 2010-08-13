/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009, 2010 Red Hat, Inc.
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

#include "incls/_precompiled.incl"
#include "incls/_sharkContext.cpp.incl"

using namespace llvm;

SharkContext::SharkContext(const char* name)
  : LLVMContext(),
    _free_queue(NULL) {
  // Create a module to build our functions into
  _module = new Module(name, *this);

  // Create basic types
  _void_type    = Type::getVoidTy(*this);
  _bit_type     = Type::getInt1Ty(*this);
  _jbyte_type   = Type::getInt8Ty(*this);
  _jshort_type  = Type::getInt16Ty(*this);
  _jint_type    = Type::getInt32Ty(*this);
  _jlong_type   = Type::getInt64Ty(*this);
  _jfloat_type  = Type::getFloatTy(*this);
  _jdouble_type = Type::getDoubleTy(*this);

  // Create compound types
  _itableOffsetEntry_type = PointerType::getUnqual(
    ArrayType::get(jbyte_type(), itableOffsetEntry::size() * wordSize));

  _klass_type = PointerType::getUnqual(
    ArrayType::get(jbyte_type(), sizeof(Klass)));

  _jniEnv_type = PointerType::getUnqual(
    ArrayType::get(jbyte_type(), sizeof(JNIEnv)));

  _jniHandleBlock_type = PointerType::getUnqual(
    ArrayType::get(jbyte_type(), sizeof(JNIHandleBlock)));

  _methodOop_type = PointerType::getUnqual(
    ArrayType::get(jbyte_type(), sizeof(methodOopDesc)));

  _monitor_type = ArrayType::get(
    jbyte_type(), frame::interpreter_frame_monitor_size() * wordSize);

  _oop_type = PointerType::getUnqual(
    ArrayType::get(jbyte_type(), sizeof(oopDesc)));

  _thread_type = PointerType::getUnqual(
    ArrayType::get(jbyte_type(), sizeof(JavaThread)));

  _zeroStack_type = PointerType::getUnqual(
    ArrayType::get(jbyte_type(), sizeof(ZeroStack)));

  std::vector<const Type*> params;
  params.push_back(methodOop_type());
  params.push_back(intptr_type());
  params.push_back(thread_type());
  _entry_point_type = FunctionType::get(jint_type(), params, false);

  params.clear();
  params.push_back(methodOop_type());
  params.push_back(PointerType::getUnqual(jbyte_type()));
  params.push_back(intptr_type());
  params.push_back(thread_type());
  _osr_entry_point_type = FunctionType::get(jint_type(), params, false);

  // Create mappings
  for (int i = 0; i < T_CONFLICT; i++) {
    switch (i) {
    case T_BOOLEAN:
      _to_stackType[i] = jint_type();
      _to_arrayType[i] = jbyte_type();
      break;

    case T_BYTE:
      _to_stackType[i] = jint_type();
      _to_arrayType[i] = jbyte_type();
      break;

    case T_CHAR:
      _to_stackType[i] = jint_type();
      _to_arrayType[i] = jshort_type();
      break;

    case T_SHORT:
      _to_stackType[i] = jint_type();
      _to_arrayType[i] = jshort_type();
      break;

    case T_INT:
      _to_stackType[i] = jint_type();
      _to_arrayType[i] = jint_type();
      break;

    case T_LONG:
      _to_stackType[i] = jlong_type();
      _to_arrayType[i] = jlong_type();
      break;

    case T_FLOAT:
      _to_stackType[i] = jfloat_type();
      _to_arrayType[i] = jfloat_type();
      break;

    case T_DOUBLE:
      _to_stackType[i] = jdouble_type();
      _to_arrayType[i] = jdouble_type();
      break;

    case T_OBJECT:
    case T_ARRAY:
      _to_stackType[i] = oop_type();
      _to_arrayType[i] = oop_type();
      break;

    case T_ADDRESS:
      _to_stackType[i] = intptr_type();
      _to_arrayType[i] = NULL;
      break;

    default:
      _to_stackType[i] = NULL;
      _to_arrayType[i] = NULL;
    }
  }
}

class SharkFreeQueueItem : public CHeapObj {
 public:
  SharkFreeQueueItem(llvm::Function* function, SharkFreeQueueItem *next)
    : _function(function), _next(next) {}

 private:
  llvm::Function*     _function;
  SharkFreeQueueItem* _next;

 public:
  llvm::Function* function() const {
    return _function;
  }
  SharkFreeQueueItem* next() const {
    return _next;
  }
};

void SharkContext::push_to_free_queue(Function* function) {
  _free_queue = new SharkFreeQueueItem(function, _free_queue);
}

Function* SharkContext::pop_from_free_queue() {
  if (_free_queue == NULL)
    return NULL;

  SharkFreeQueueItem *item = _free_queue;
  Function *function = item->function();
  _free_queue = item->next();
  delete item;
  return function;
}
