/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciArray.hpp"
#include "ci/ciConstant.hpp"
#include "ci/ciField.hpp"
#include "ci/ciFlatArray.hpp"
#include "ci/ciInlineKlass.hpp"
#include "ci/ciUtilities.inline.hpp"
#include "oops/oop.inline.hpp"

// Current value of an element.
// Returns T_ILLEGAL if there is no element at the given index.
ciConstant ciFlatArray::null_marker_of_element_by_index(int index) {
  ciConstant nm = field_value(index, nullptr);
  postcond(!nm.is_valid() || nm.basic_type() == T_BOOLEAN);
  return nm;
}

ciConstant ciFlatArray::null_marker_of_element_by_offset(intptr_t element_offset) {
  FlatArrayKlass* faklass;
  GUARDED_VM_ENTRY(faklass = FlatArrayKlass::cast(get_arrayOop()->klass());)
  int lh = faklass->layout_helper();
  int shift = Klass::layout_helper_log2_element_size(lh);
  intptr_t header = arrayOopDesc::base_offset_in_bytes(T_FLAT_ELEMENT);
  intptr_t index = (element_offset - header) >> shift;
  intptr_t offset = header + (index << shift);
  if (offset != element_offset || index != (jint) index || index < 0 || index >= length()) {
    return ciConstant();
  }
  return null_marker_of_element_by_index((jint) index);
}

ciConstant ciFlatArray::element_value_by_offset(intptr_t element_offset) {
  FlatArrayKlass* faklass;
  GUARDED_VM_ENTRY(faklass = FlatArrayKlass::cast(get_arrayOop()->klass());)
  int lh = faklass->layout_helper();
  int shift = Klass::layout_helper_log2_element_size(lh);
  intptr_t header = arrayOopDesc::base_offset_in_bytes(T_FLAT_ELEMENT);
  intptr_t index = (element_offset - header) >> shift;
  intptr_t offset = header + (index << shift);
  if (offset != element_offset || index != (jint) index || index < 0 || index >= length()) {
    return ciConstant();
  }
  return element_value((jint) index);
}

ciConstant ciFlatArray::field_value_by_offset(intptr_t field_offset) {
  ciInlineKlass* elt_type = element_type()->as_inline_klass();
  FlatArrayKlass* faklass;
  GUARDED_VM_ENTRY(faklass = FlatArrayKlass::cast(get_arrayOop()->klass());)
  int lh = faklass->layout_helper();
  int shift = Klass::layout_helper_log2_element_size(lh);
  intptr_t header = arrayOopDesc::base_offset_in_bytes(T_FLAT_ELEMENT);
  intptr_t index = (field_offset - header) >> shift;
  intptr_t element_offset = header + (index << shift);
  int field_offset_in_element = (int)(field_offset - element_offset);
  ciField* field = elt_type->get_field_by_offset(elt_type->payload_offset() + field_offset_in_element, false);
  if (field == nullptr) {
    if (field_offset_in_element != elt_type->null_marker_offset_in_payload()) {
      return ciConstant();
    }
  }

  if (index != (jint) index || index < 0 || index >= length()) {
    return ciConstant();
  }
  ciConstant elt = field_value((jint) index, field);

  return elt;
}

ciConstant ciFlatArray::field_value(int index, ciField* field) {
  auto get_field_from_object_constant = [field](const ciConstant& v) -> ciConstant {
    ciObject* obj = v.as_object();
    if (obj->is_null_object()) {
      if (field == nullptr) {
        return ciConstant::make_zero_or_null(T_BOOLEAN);
      }
      return ciConstant::make_zero_or_null(field->type()->basic_type());
    }
    // obj cannot be an ciArray since it is an element of a flat array, so it must be a value class, which arrays are not.
    ciInstance* inst = obj->as_instance();
    if (field == nullptr) {
      return ciConstant(T_BOOLEAN, 1);
    }
    return inst->field_value(field);
  };

  BasicType elembt = element_basic_type();
  ciConstant value = check_constant_value_cache(index, elembt);
  if (value.is_valid()) {
    return get_field_from_object_constant(value);
  }
  GUARDED_VM_ENTRY(
    value = element_value_impl(T_OBJECT, get_arrayOop(), index);
  )

  if (!value.is_valid()) {
    return ciConstant();
  }

  add_to_constant_value_cache(index, value);
  return get_field_from_object_constant(value);
}

