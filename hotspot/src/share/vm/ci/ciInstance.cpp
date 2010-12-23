/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciConstant.hpp"
#include "ci/ciField.hpp"
#include "ci/ciInstance.hpp"
#include "ci/ciInstanceKlass.hpp"
#include "ci/ciUtilities.hpp"
#include "classfile/systemDictionary.hpp"
#include "oops/oop.inline.hpp"

// ciInstance
//
// This class represents an instanceOop in the HotSpot virtual
// machine.

// ------------------------------------------------------------------
// ciObject::java_mirror_type
ciType* ciInstance::java_mirror_type() {
  VM_ENTRY_MARK;
  oop m = get_oop();
  // Return NULL if it is not java.lang.Class.
  if (m == NULL || m->klass() != SystemDictionary::Class_klass()) {
    return NULL;
  }
  // Return either a primitive type or a klass.
  if (java_lang_Class::is_primitive(m)) {
    return ciType::make(java_lang_Class::primitive_type(m));
  } else {
    klassOop k = java_lang_Class::as_klassOop(m);
    assert(k != NULL, "");
    return CURRENT_THREAD_ENV->get_object(k)->as_klass();
  }
}

// ------------------------------------------------------------------
// ciInstance::field_value
//
// Constant value of a field.
ciConstant ciInstance::field_value(ciField* field) {
  assert(is_loaded() &&
         field->holder()->is_loaded() &&
         klass()->is_subclass_of(field->holder()),
         "invalid access");
  VM_ENTRY_MARK;
  ciConstant result;
  oop obj = get_oop();
  assert(obj != NULL, "bad oop");
  BasicType field_btype = field->type()->basic_type();
  int offset = field->offset();

  switch(field_btype) {
  case T_BYTE:
    return ciConstant(field_btype, obj->byte_field(offset));
    break;
  case T_CHAR:
    return ciConstant(field_btype, obj->char_field(offset));
    break;
  case T_SHORT:
    return ciConstant(field_btype, obj->short_field(offset));
    break;
  case T_BOOLEAN:
    return ciConstant(field_btype, obj->bool_field(offset));
    break;
  case T_INT:
    return ciConstant(field_btype, obj->int_field(offset));
    break;
  case T_FLOAT:
    return ciConstant(obj->float_field(offset));
    break;
  case T_DOUBLE:
    return ciConstant(obj->double_field(offset));
    break;
  case T_LONG:
    return ciConstant(obj->long_field(offset));
    break;
  case T_OBJECT:
  case T_ARRAY:
    {
      oop o = obj->obj_field(offset);

      // A field will be "constant" if it is known always to be
      // a non-null reference to an instance of a particular class,
      // or to a particular array.  This can happen even if the instance
      // or array is not perm.  In such a case, an "unloaded" ciArray
      // or ciInstance is created.  The compiler may be able to use
      // information about the object's class (which is exact) or length.

      if (o == NULL) {
        return ciConstant(field_btype, ciNullObject::make());
      } else {
        return ciConstant(field_btype, CURRENT_ENV->get_object(o));
      }
    }
  }
  ShouldNotReachHere();
  // to shut up the compiler
  return ciConstant();
}

// ------------------------------------------------------------------
// ciInstance::field_value_by_offset
//
// Constant value of a field at the specified offset.
ciConstant ciInstance::field_value_by_offset(int field_offset) {
  ciInstanceKlass* ik = klass()->as_instance_klass();
  ciField* field = ik->get_field_by_offset(field_offset, false);
  return field_value(field);
}

// ------------------------------------------------------------------
// ciInstance::print_impl
//
// Implementation of the print method.
void ciInstance::print_impl(outputStream* st) {
  st->print(" type=");
  klass()->print(st);
}
