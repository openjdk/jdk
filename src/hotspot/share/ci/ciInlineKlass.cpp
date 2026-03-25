/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciConstant.hpp"
#include "ci/ciField.hpp"
#include "ci/ciInlineKlass.hpp"
#include "ci/ciUtilities.inline.hpp"
#include "oops/array.hpp"
#include "oops/inlineKlass.inline.hpp"
#include "runtime/signature.hpp"
#include "utilities/globalDefinitions.hpp"

// Offset of the first field in the inline type
int ciInlineKlass::payload_offset() const {
  GUARDED_VM_ENTRY(return to_InlineKlass()->payload_offset();)
}

// Could any array containing an instance of this value class ever be flat?
bool ciInlineKlass::maybe_flat_in_array() const {
  GUARDED_VM_ENTRY(return to_InlineKlass()->maybe_flat_in_array();)
}

// Are arrays containing an instance of this value class always flat?
bool ciInlineKlass::is_always_flat_in_array() const {
  GUARDED_VM_ENTRY(return to_InlineKlass()->is_always_flat_in_array();)
}

// Can this inline type be passed as multiple values?
bool ciInlineKlass::can_be_passed_as_fields() const {
  GUARDED_VM_ENTRY(return to_InlineKlass()->can_be_passed_as_fields();)
}

// Can this inline type be returned as multiple values?
bool ciInlineKlass::can_be_returned_as_fields() const {
  GUARDED_VM_ENTRY(return to_InlineKlass()->can_be_returned_as_fields();)
}

bool ciInlineKlass::is_empty() {
  // Do not use InlineKlass::is_empty_inline_type here because it does
  // consider the container empty even if fields of empty inline types
  // are not flat
  return nof_declared_nonstatic_fields() == 0;
}

// When passing an inline type's fields as arguments, count the number
// of argument slots that are needed
int ciInlineKlass::inline_arg_slots() {
  VM_ENTRY_MARK;
  const Array<SigEntry>* sig_vk = get_InlineKlass()->extended_sig();
  int slots = 0;
  for (int i = 0; i < sig_vk->length(); i++) {
    BasicType bt = sig_vk->at(i)._bt;
    if (bt == T_METADATA || bt == T_VOID) {
      continue;
    }
    slots += type2size[bt];
  }
  return slots;
}

bool ciInlineKlass::contains_oops() const {
  GUARDED_VM_ENTRY(return get_InlineKlass()->contains_oops();)
}

int ciInlineKlass::oop_count() const {
  GUARDED_VM_ENTRY(return get_InlineKlass()->nonstatic_oop_count();)
}

address ciInlineKlass::pack_handler() const {
  GUARDED_VM_ENTRY(return get_InlineKlass()->pack_handler();)
}

address ciInlineKlass::unpack_handler() const {
  GUARDED_VM_ENTRY(return get_InlineKlass()->unpack_handler();)
}

InlineKlass* ciInlineKlass::get_InlineKlass() const {
  GUARDED_VM_ENTRY(return to_InlineKlass();)
}

bool ciInlineKlass::has_null_free_non_atomic_layout() const {
  GUARDED_VM_ENTRY(return get_InlineKlass()->has_null_free_non_atomic_layout();)
}

bool ciInlineKlass::has_null_free_atomic_layout() const {
  GUARDED_VM_ENTRY(return get_InlineKlass()->has_null_free_atomic_layout();)
}

bool ciInlineKlass::has_nullable_atomic_layout() const {
  GUARDED_VM_ENTRY(return get_InlineKlass()->has_nullable_atomic_layout();)
}

int ciInlineKlass::null_marker_offset_in_payload() const {
  GUARDED_VM_ENTRY(return get_InlineKlass()->null_marker_offset_in_payload();)
}

// Convert size of atomic layout in bytes to corresponding BasicType
BasicType ciInlineKlass::atomic_size_to_basic_type(bool null_free) const {
  VM_ENTRY_MARK
  InlineKlass* vk = get_InlineKlass();
  assert(!null_free || vk->has_null_free_atomic_layout(), "No null-free atomic layout available");
  assert( null_free || vk->has_nullable_atomic_layout(), "No nullable atomic layout available");
  int size = null_free ? vk->null_free_atomic_size_in_bytes() : vk->nullable_atomic_size_in_bytes();
  BasicType bt = T_ILLEGAL;
  if (size == sizeof(jlong)) {
    bt = T_LONG;
  } else if (size == sizeof(jint)) {
    bt = T_INT;
  } else if (size == sizeof(jshort)) {
    bt = T_SHORT;
  } else if (size == sizeof(jbyte)) {
    bt = T_BYTE;
  } else {
    assert(false, "Unsupported size: %d", size);
  }
  return bt;
}

bool ciInlineKlass::is_naturally_atomic(bool null_free) {
  return null_free ? (nof_nonstatic_fields() <= 1) : (nof_nonstatic_fields() == 0);
}

int ciInlineKlass::field_map_offset() const {
  GUARDED_VM_ENTRY(return get_InlineKlass()->acmp_maps_offset();)
}

ciConstant ciInlineKlass::get_field_map() const {
  VM_ENTRY_MARK
  InlineKlass* vk = get_InlineKlass();
  oop array = vk->java_mirror()->obj_field(vk->acmp_maps_offset());
  return ciConstant(T_ARRAY, CURRENT_ENV->get_object(array));
}

// All fields of this object are zero even if they are null-free. As a result, this object should
// only be used to reset the payload of fields or array elements and should not be leaked
// elsewhere.
ciConstant ciInlineKlass::get_null_reset_value() {
  assert(is_initialized(), "null_reset_value is only allocated during initialization of %s", name()->as_utf8());
  VM_ENTRY_MARK
  InlineKlass* vk = get_InlineKlass();
  oop null_reset_value = vk->null_reset_value();
  return ciConstant(T_OBJECT, CURRENT_ENV->get_object(null_reset_value));
}
