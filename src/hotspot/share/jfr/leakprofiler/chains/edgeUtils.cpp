/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "jfr/leakprofiler/chains/edge.hpp"
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/chains/edgeUtils.hpp"
#include "jfr/leakprofiler/utilities/unifiedOopRef.inline.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/flatArrayOop.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/valueKlass.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"

static bool is_static_field(const oop ref_owner, const InstanceKlass* ik, int offset) {
  assert(ref_owner != nullptr, "invariant");
  assert(ik != nullptr, "invariant");
  assert(ref_owner->klass() == ik, "invariant");
  return ik->is_mirror_instance_klass() && offset >= InstanceMirrorKlass::cast(ik)->offset_of_static_fields();
}

static int field_offset(const Edge& edge, const oop ref_owner) {
  assert(ref_owner != nullptr, "invariant");
  assert(!ref_owner->is_array(), "invariant");
  assert(ref_owner->is_instance(), "invariant");
  UnifiedOopRef reference = edge.reference();
  assert(!reference.is_null(), "invariant");
  const size_t offset = (reference.addr<uintptr_t>() - cast_from_oop<uintptr_t>(ref_owner));
  assert(offset < ref_owner->size() * HeapWordSize, "invariant");
  assert(offset <= size_t(INT_MAX), "invariant");
  return (int)offset;
}

static bool resolve_field_path(const InstanceKlass* ik, int offset, bool is_static,
  GrowableArray<EdgeField>* fields) {
  while (ik != nullptr) {
    fieldDescriptor fd;
    // Flattened field: the known reference slot lies within its payload.
    // Append this enclosing field to the path, translate the slot's offset
    // into the value class's layout, and continue resolving within that layout.
    // Check containment before exact offsets: the first embedded reference
    // can share the flattened field's starting offset.
    if (!is_static && ik->find_flat_field_containing_offset(offset, &fd)) {
      const ValueKlass* const vk = fd.field_holder()->value_field_info(fd.index()).klass();
      fields->append(EdgeField(fd.name(), fd.access_flags().as_field_flags(), vk));
      offset = offset - fd.offset() + vk->payload_offset();
      ik = vk;
      continue;
    }

    // Ordinary reference field: identify the field naming the known slot,
    // append it to the path, and complete the resolution.
    if (ik->find_local_field_from_offset(offset, is_static, &fd)) {
      assert(!fd.is_flat(), "invariant");
      assert(is_reference_type(fd.field_type()), "invariant");
      fields->append(EdgeField(fd.name(), fd.access_flags().as_field_flags()));
      return true;
    }

    ik = ik->super();
  }
  fields->clear();
  return false;
}

bool EdgeUtils::field_path(const Edge& edge, GrowableArray<EdgeField>* fields) {
  assert(fields != nullptr && fields->is_empty(), "invariant");
  if (edge.is_root()) {
    return false;
  }
  const oop owner = edge.reference_owner();
  assert(owner != nullptr, "invariant");
  // Take note! Flat arrays also satisfy owner->is_array().
  // We must therefore handle owner->is_flatArray() before the general array check below,
  // which would otherwise return false without resolving the fields within their elements.
  if (owner->is_flatArray()) {
    const flatArrayOop array = static_cast<flatArrayOop>(owner);
    const size_t stride = array->klass()->element_byte_size();
    const size_t displacement = pointer_delta(edge.reference().addr<HeapWord*>(), array->base(), 1);
    assert(displacement / stride < static_cast<size_t>(array->length()), "invariant");
    const ValueKlass* vk = array->klass()->element_klass();
    const size_t offset = displacement % stride + vk->payload_offset();
    assert(offset <= static_cast<size_t>(INT_MAX), "invariant");
    return resolve_field_path(vk, static_cast<int>(offset), false, fields);
  }
  if (owner->is_array()) {
    return false;
  }
  const InstanceKlass* ik = InstanceKlass::cast(owner->klass());
  const int offset = field_offset(edge, owner);
  const bool is_static = is_static_field(owner, ik, offset);
  if (is_static) {
    ik = java_lang_Class::as_InstanceKlass(owner);
  }
  return resolve_field_path(ik, offset, is_static, fields);
}

bool EdgeUtils::is_array_element(const Edge& edge) {
  assert(!edge.is_root(), "invariant");
  const oop ref_owner = edge.reference_owner();
  assert(ref_owner != nullptr, "invariant");
  return ref_owner->is_objArray();
}

static int array_offset(const Edge& edge) {
  assert(EdgeUtils::is_array_element(edge), "invariant");
  const oop ref_owner = edge.reference_owner();
  assert(ref_owner != nullptr, "invariant");
  UnifiedOopRef reference = edge.reference();
  assert(!reference.is_null(), "invariant");
  assert(ref_owner->is_array(), "invariant");
  const objArrayOop ref_owner_array = static_cast<objArrayOop>(ref_owner);
  const void* base = ref_owner_array->base();
  size_t element_size = heapOopSize;
  if (ref_owner->is_flatArray()) {
    const flatArrayOop flat_array = static_cast<flatArrayOop>(ref_owner);
    base = flat_array->base();
    element_size = flat_array->klass()->element_byte_size();
  }
  // A reference embedded in a flat element maps to its containing array index.
  const size_t index = pointer_delta(reference.addr<HeapWord*>(), base, element_size);
  assert(index < static_cast<size_t>(ref_owner_array->length()), "invariant");
  return static_cast<int>(index);
}

int EdgeUtils::array_index(const Edge& edge) {
  return array_offset(edge);
}

int EdgeUtils::array_size(const Edge& edge) {
  assert(is_array_element(edge), "invariant");
  const oop ref_owner = edge.reference_owner();
  assert(ref_owner != nullptr, "invariant");
  assert(ref_owner->is_objArray(), "invariant");
  return ((objArrayOop)ref_owner)->length();
}

const Edge* EdgeUtils::root(const Edge& edge) {
  const Edge* current = &edge;
  const Edge* parent = current->parent();
  while (parent != nullptr) {
    current = parent;
    parent = current->parent();
  }
  assert(current != nullptr, "invariant");
  return current;
}

const Edge* EdgeUtils::ancestor(const Edge& edge, size_t distance) {
  const Edge* current = &edge;
  const Edge* parent = current->parent();
  size_t seek = 0;
  while (parent != nullptr && seek != distance) {
    seek++;
    current = parent;
    parent = parent->parent();
  }
  return current;
}
