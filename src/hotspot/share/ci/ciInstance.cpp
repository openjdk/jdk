/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "ci/ciConstant.hpp"
#include "ci/ciField.hpp"
#include "ci/ciInstance.hpp"
#include "ci/ciInstanceKlass.hpp"
#include "ci/ciNullObject.hpp"
#include "ci/ciUtilities.inline.hpp"
#include "classfile/vmClasses.hpp"
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
  // Return null if it is not java.lang.Class.
  if (m == nullptr || m->klass() != vmClasses::Class_klass()) {
    return nullptr;
  }
  // Return either a primitive type or a klass.
  if (java_lang_Class::is_primitive(m)) {
    return ciType::make(java_lang_Class::primitive_type(m));
  } else {
    Klass* k = java_lang_Class::as_Klass(m);
    assert(k != nullptr, "");
    return CURRENT_THREAD_ENV->get_klass(k);
  }
}

// ------------------------------------------------------------------
// ciInstance::field_value_impl
ciConstant ciInstance::field_value_impl(BasicType field_btype, int offset) {
  ciConstant value = check_constant_value_cache(offset, field_btype);
  if (value.is_valid()) {
    return value;
  }
  VM_ENTRY_MARK;
  oop obj = get_oop();
  assert(obj != nullptr, "bad oop");
  switch(field_btype) {
    case T_BYTE:    value = ciConstant(field_btype, obj->byte_field(offset)); break;
    case T_CHAR:    value = ciConstant(field_btype, obj->char_field(offset)); break;
    case T_SHORT:   value = ciConstant(field_btype, obj->short_field(offset)); break;
    case T_BOOLEAN: value = ciConstant(field_btype, obj->bool_field(offset)); break;
    case T_INT:     value = ciConstant(field_btype, obj->int_field(offset)); break;
    case T_FLOAT:   value = ciConstant(obj->float_field(offset)); break;
    case T_DOUBLE:  value = ciConstant(obj->double_field(offset)); break;
    case T_LONG:    value = ciConstant(obj->long_field(offset)); break;
    case T_OBJECT:  // fall through
    case T_ARRAY: {
      oop o = obj->obj_field(offset);

      // A field will be "constant" if it is known always to be
      // a non-null reference to an instance of a particular class,
      // or to a particular array.  This can happen even if the instance
      // or array is not perm.  In such a case, an "unloaded" ciArray
      // or ciInstance is created.  The compiler may be able to use
      // information about the object's class (which is exact) or length.

      if (o == nullptr) {
        value = ciConstant(field_btype, ciNullObject::make());
      } else {
        value = ciConstant(field_btype, CURRENT_ENV->get_object(o));
      }
      break;
    }
    default:
      fatal("no field value: %s", type2name(field_btype));
  }
  add_to_constant_value_cache(offset, value);
  return value;
}

// ------------------------------------------------------------------
// ciInstance::field_value
//
// Constant value of a field.
ciConstant ciInstance::field_value(ciField* field) {
  assert(is_loaded(), "invalid access - must be loaded");
  assert(field->holder()->is_loaded(), "invalid access - holder must be loaded");
  assert(field->is_static() || klass()->is_subclass_of(field->holder()), "invalid access - must be subclass");
  return field_value_impl(field->type()->basic_type(), field->offset_in_bytes());
}

// ------------------------------------------------------------------
// ciInstance::field_value_by_offset
//
// Constant value of a field at the specified offset.
ciConstant ciInstance::field_value_by_offset(int field_offset) {
  ciInstanceKlass* ik = klass()->as_instance_klass();
  ciField* field = ik->get_field_by_offset(field_offset, false);
  if (field == nullptr)
    return ciConstant();  // T_ILLEGAL
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


ciKlass* ciInstance::java_lang_Class_klass() {
  VM_ENTRY_MARK;
  assert(java_lang_Class::as_Klass(get_oop()) != nullptr, "klass is null");
  return CURRENT_ENV->get_metadata(java_lang_Class::as_Klass(get_oop()))->as_klass();
}
