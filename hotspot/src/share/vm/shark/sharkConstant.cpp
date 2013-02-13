/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009 Red Hat, Inc.
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
#include "ci/ciInstance.hpp"
#include "ci/ciStreams.hpp"
#include "shark/sharkBuilder.hpp"
#include "shark/sharkConstant.hpp"
#include "shark/sharkValue.hpp"

using namespace llvm;

SharkConstant* SharkConstant::for_ldc(ciBytecodeStream *iter) {
  ciConstant constant = iter->get_constant();
  ciType *type = NULL;
  if (constant.basic_type() == T_OBJECT) {
    ciEnv *env = ciEnv::current();

    assert(constant.as_object()->klass() == env->String_klass()
           || constant.as_object()->klass() == env->Class_klass()
           || constant.as_object()->klass()->is_subtype_of(env->MethodType_klass())
           || constant.as_object()->klass()->is_subtype_of(env->MethodHandle_klass()), "should be");

    type = constant.as_object()->klass();
  }
  return new SharkConstant(constant, type);
}

SharkConstant* SharkConstant::for_field(ciBytecodeStream *iter) {
  bool will_link;
  ciField *field = iter->get_field(will_link);
  assert(will_link, "typeflow responsibility");

  return new SharkConstant(field->constant_value(), field->type());
}

SharkConstant::SharkConstant(ciConstant constant, ciType *type) {
  SharkValue *value = NULL;

  switch (constant.basic_type()) {
  case T_BOOLEAN:
  case T_BYTE:
  case T_CHAR:
  case T_SHORT:
  case T_INT:
    value = SharkValue::jint_constant(constant.as_int());
    break;

  case T_LONG:
    value = SharkValue::jlong_constant(constant.as_long());
    break;

  case T_FLOAT:
    value = SharkValue::jfloat_constant(constant.as_float());
    break;

  case T_DOUBLE:
    value = SharkValue::jdouble_constant(constant.as_double());
    break;

  case T_OBJECT:
  case T_ARRAY:
    break;

  case T_ILLEGAL:
    // out of memory
    _is_loaded = false;
    return;

  default:
    tty->print_cr("Unhandled type %s", type2name(constant.basic_type()));
    ShouldNotReachHere();
  }

  // Handle primitive types.  We create SharkValues for these
  // now; doing so doesn't emit any code, and it allows us to
  // delegate a bunch of stuff to the SharkValue code.
  if (value) {
    _value       = value;
    _is_loaded   = true;
    _is_nonzero  = value->zero_checked();
    _is_two_word = value->is_two_word();
    return;
  }

  // Handle reference types.  This is tricky because some
  // ciObjects are psuedo-objects that refer to oops which
  // have yet to be created.  We need to spot the unloaded
  // objects (which differ between ldc* and get*, thanks!)
  ciObject *object = constant.as_object();
  assert(type != NULL, "shouldn't be");

  if ((! object->is_null_object()) && object->klass() == ciEnv::current()->Class_klass()) {
    ciKlass *klass = object->klass();
    if (! klass->is_loaded()) {
      _is_loaded = false;
      return;
    }
  }

  if (object->is_null_object() || ! object->can_be_constant() || ! object->is_loaded()) {
    _is_loaded = false;
    return;
  }

  _value       = NULL;
  _object      = object;
  _type        = type;
  _is_loaded   = true;
  _is_nonzero  = true;
  _is_two_word = false;
}
