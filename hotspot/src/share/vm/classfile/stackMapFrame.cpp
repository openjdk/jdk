/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/stackMapFrame.hpp"
#include "classfile/verifier.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbolOop.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/globalDefinitions.hpp"

StackMapFrame::StackMapFrame(u2 max_locals, u2 max_stack, ClassVerifier* v) :
                      _offset(0), _locals_size(0), _stack_size(0), _flags(0),
                      _max_locals(max_locals), _max_stack(max_stack),
                      _verifier(v) {
  Thread* thr = v->thread();
  _locals = NEW_RESOURCE_ARRAY_IN_THREAD(thr, VerificationType, max_locals);
  _stack = NEW_RESOURCE_ARRAY_IN_THREAD(thr, VerificationType, max_stack);
  int32_t i;
  for(i = 0; i < max_locals; i++) {
    _locals[i] = VerificationType::bogus_type();
  }
  for(i = 0; i < max_stack; i++) {
    _stack[i] = VerificationType::bogus_type();
  }
}

StackMapFrame* StackMapFrame::frame_in_exception_handler(u1 flags) {
  Thread* thr = _verifier->thread();
  VerificationType* stack = NEW_RESOURCE_ARRAY_IN_THREAD(thr, VerificationType, 1);
  StackMapFrame* frame = new StackMapFrame(_offset, flags, _locals_size, 0, _max_locals, _max_stack, _locals, stack, _verifier);
  return frame;
}

bool StackMapFrame::has_new_object() const {
  int32_t i;
  for (i = 0; i < _max_locals; i++) {
    if (_locals[i].is_uninitialized()) {
      return true;
    }
  }
  for (i = 0; i < _stack_size; i++) {
    if (_stack[i].is_uninitialized()) {
      return true;
    }
  }
  return false;
}

void StackMapFrame::initialize_object(
    VerificationType old_object, VerificationType new_object) {
  int32_t i;
  for (i = 0; i < _max_locals; i++) {
    if (_locals[i].equals(old_object)) {
      _locals[i] = new_object;
    }
  }
  for (i = 0; i < _stack_size; i++) {
    if (_stack[i].equals(old_object)) {
      _stack[i] = new_object;
    }
  }
  if (old_object == VerificationType::uninitialized_this_type()) {
    // "this" has been initialized - reset flags
    _flags = 0;
  }
}

VerificationType StackMapFrame::set_locals_from_arg(
    const methodHandle m, VerificationType thisKlass, TRAPS) {
  symbolHandle signature(THREAD, m->signature());
  SignatureStream ss(signature);
  int init_local_num = 0;
  if (!m->is_static()) {
    init_local_num++;
    // add one extra argument for instance method
    if (m->name() == vmSymbols::object_initializer_name() &&
       thisKlass.name() != vmSymbols::java_lang_Object()) {
      _locals[0] = VerificationType::uninitialized_this_type();
      _flags |= FLAG_THIS_UNINIT;
    } else {
      _locals[0] = thisKlass;
    }
  }

  // local num may be greater than size of parameters because long/double occupies two slots
  while(!ss.at_return_type()) {
    init_local_num += _verifier->change_sig_to_verificationType(
      &ss, &_locals[init_local_num],
      CHECK_VERIFY_(verifier(), VerificationType::bogus_type()));
    ss.next();
  }
  _locals_size = init_local_num;

  switch (ss.type()) {
    case T_OBJECT:
    case T_ARRAY:
    {
      symbolOop sig = ss.as_symbol(CHECK_(VerificationType::bogus_type()));
      return VerificationType::reference_type(symbolHandle(THREAD, sig));
    }
    case T_INT:     return VerificationType::integer_type();
    case T_BYTE:    return VerificationType::byte_type();
    case T_CHAR:    return VerificationType::char_type();
    case T_SHORT:   return VerificationType::short_type();
    case T_BOOLEAN: return VerificationType::boolean_type();
    case T_FLOAT:   return VerificationType::float_type();
    case T_DOUBLE:  return VerificationType::double_type();
    case T_LONG:    return VerificationType::long_type();
    case T_VOID:    return VerificationType::bogus_type();
    default:
      ShouldNotReachHere();
  }
  return VerificationType::bogus_type();
}

void StackMapFrame::copy_locals(const StackMapFrame* src) {
  int32_t len = src->locals_size() < _locals_size ?
    src->locals_size() : _locals_size;
  for (int32_t i = 0; i < len; i++) {
    _locals[i] = src->locals()[i];
  }
}

void StackMapFrame::copy_stack(const StackMapFrame* src) {
  int32_t len = src->stack_size() < _stack_size ?
    src->stack_size() : _stack_size;
  for (int32_t i = 0; i < len; i++) {
    _stack[i] = src->stack()[i];
  }
}


bool StackMapFrame::is_assignable_to(
    VerificationType* from, VerificationType* to, int32_t len, TRAPS) const {
  for (int32_t i = 0; i < len; i++) {
    bool subtype = to[i].is_assignable_from(
      from[i], verifier()->current_class(), THREAD);
    if (!subtype) {
      return false;
    }
  }
  return true;
}

bool StackMapFrame::is_assignable_to(const StackMapFrame* target, TRAPS) const {
  if (_max_locals != target->max_locals() || _stack_size != target->stack_size()) {
    return false;
  }
  // Only need to compare type elements up to target->locals() or target->stack().
  // The remaining type elements in this state can be ignored because they are
  // assignable to bogus type.
  bool match_locals = is_assignable_to(
    _locals, target->locals(), target->locals_size(), CHECK_false);
  bool match_stack = is_assignable_to(
    _stack, target->stack(), _stack_size, CHECK_false);
  bool match_flags = (_flags | target->flags()) == target->flags();
  return (match_locals && match_stack && match_flags);
}

VerificationType StackMapFrame::pop_stack_ex(VerificationType type, TRAPS) {
  if (_stack_size <= 0) {
    verifier()->verify_error(_offset, "Operand stack underflow");
    return VerificationType::bogus_type();
  }
  VerificationType top = _stack[--_stack_size];
  bool subtype = type.is_assignable_from(
    top, verifier()->current_class(), CHECK_(VerificationType::bogus_type()));
  if (!subtype) {
    verifier()->verify_error(_offset, "Bad type on operand stack");
    return VerificationType::bogus_type();
  }
  NOT_PRODUCT( _stack[_stack_size] = VerificationType::bogus_type(); )
  return top;
}

VerificationType StackMapFrame::get_local(
    int32_t index, VerificationType type, TRAPS) {
  if (index >= _max_locals) {
    verifier()->verify_error(_offset, "Local variable table overflow");
    return VerificationType::bogus_type();
  }
  bool subtype = type.is_assignable_from(_locals[index],
    verifier()->current_class(), CHECK_(VerificationType::bogus_type()));
  if (!subtype) {
    verifier()->verify_error(_offset, "Bad local variable type");
    return VerificationType::bogus_type();
  }
  if(index >= _locals_size) { _locals_size = index + 1; }
  return _locals[index];
}

void StackMapFrame::get_local_2(
    int32_t index, VerificationType type1, VerificationType type2, TRAPS) {
  assert(type1.is_long() || type1.is_double(), "must be long/double");
  assert(type2.is_long2() || type2.is_double2(), "must be long/double_2");
  if (index >= _locals_size - 1) {
    verifier()->verify_error(_offset, "get long/double overflows locals");
    return;
  }
  bool subtype1 = type1.is_assignable_from(
    _locals[index], verifier()->current_class(), CHECK);
  bool subtype2 = type2.is_assignable_from(
    _locals[index+1], verifier()->current_class(), CHECK);
  if (!subtype1 || !subtype2) {
    verifier()->verify_error(_offset, "Bad local variable type");
    return;
  }
}

void StackMapFrame::set_local(int32_t index, VerificationType type, TRAPS) {
  assert(!type.is_check(), "Must be a real type");
  if (index >= _max_locals) {
    verifier()->verify_error("Local variable table overflow", _offset);
    return;
  }
  // If type at index is double or long, set the next location to be unusable
  if (_locals[index].is_double() || _locals[index].is_long()) {
    assert((index + 1) < _locals_size, "Local variable table overflow");
    _locals[index + 1] = VerificationType::bogus_type();
  }
  // If type at index is double_2 or long_2, set the previous location to be unusable
  if (_locals[index].is_double2() || _locals[index].is_long2()) {
    assert(index >= 1, "Local variable table underflow");
    _locals[index - 1] = VerificationType::bogus_type();
  }
  _locals[index] = type;
  if (index >= _locals_size) {
#ifdef ASSERT
    for (int i=_locals_size; i<index; i++) {
      assert(_locals[i] == VerificationType::bogus_type(),
             "holes must be bogus type");
    }
#endif
    _locals_size = index + 1;
  }
}

void StackMapFrame::set_local_2(
    int32_t index, VerificationType type1, VerificationType type2, TRAPS) {
  assert(type1.is_long() || type1.is_double(), "must be long/double");
  assert(type2.is_long2() || type2.is_double2(), "must be long/double_2");
  if (index >= _max_locals - 1) {
    verifier()->verify_error("Local variable table overflow", _offset);
    return;
  }
  // If type at index+1 is double or long, set the next location to be unusable
  if (_locals[index+1].is_double() || _locals[index+1].is_long()) {
    assert((index + 2) < _locals_size, "Local variable table overflow");
    _locals[index + 2] = VerificationType::bogus_type();
  }
  // If type at index is double_2 or long_2, set the previous location to be unusable
  if (_locals[index].is_double2() || _locals[index].is_long2()) {
    assert(index >= 1, "Local variable table underflow");
    _locals[index - 1] = VerificationType::bogus_type();
  }
  _locals[index] = type1;
  _locals[index+1] = type2;
  if (index >= _locals_size - 1) {
#ifdef ASSERT
    for (int i=_locals_size; i<index; i++) {
      assert(_locals[i] == VerificationType::bogus_type(),
             "holes must be bogus type");
    }
#endif
    _locals_size = index + 2;
  }
}

#ifndef PRODUCT

void StackMapFrame::print() const {
  tty->print_cr("stackmap_frame[%d]:", _offset);
  tty->print_cr("flags = 0x%x", _flags);
  tty->print("locals[%d] = { ", _locals_size);
  for (int32_t i = 0; i < _locals_size; i++) {
    _locals[i].print_on(tty);
  }
  tty->print_cr(" }");
  tty->print("stack[%d] = { ", _stack_size);
  for (int32_t j = 0; j < _stack_size; j++) {
    _stack[j].print_on(tty);
  }
  tty->print_cr(" }");
}

#endif
