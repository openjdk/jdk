/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "jfr/leakprofiler/chains/edge.hpp"
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/chains/edgeUtils.hpp"
#include "jfr/leakprofiler/utilities/unifiedOop.hpp"
#include "oops/fieldStreams.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/handles.inline.hpp"

bool EdgeUtils::is_leak_edge(const Edge& edge) {
  return (const Edge*)edge.pointee()->mark() == &edge;
}

static int field_offset(const StoredEdge& edge) {
  assert(!edge.is_root(), "invariant");
  const oop ref_owner = edge.reference_owner();
  assert(ref_owner != NULL, "invariant");
  const oop* reference = UnifiedOop::decode(edge.reference());
  assert(reference != NULL, "invariant");
  assert(!UnifiedOop::is_narrow(reference), "invariant");
  assert(!ref_owner->is_array(), "invariant");
  assert(ref_owner->is_instance(), "invariant");
  const int offset = (int)pointer_delta(reference, ref_owner, sizeof(char));
  assert(offset < (ref_owner->size() * HeapWordSize), "invariant");
  return offset;
}

static const InstanceKlass* field_type(const StoredEdge& edge) {
  assert(!edge.is_root() || !EdgeUtils::is_array_element(edge), "invariant");
  return (const InstanceKlass*)edge.reference_owner_klass();
}

const Symbol* EdgeUtils::field_name_symbol(const Edge& edge) {
  assert(!edge.is_root(), "invariant");
  assert(!is_array_element(edge), "invariant");
  const int offset = field_offset(edge);
  const InstanceKlass* ik = field_type(edge);
  while (ik != NULL) {
    JavaFieldStream jfs(ik);
    while (!jfs.done()) {
      if (offset == jfs.offset()) {
        return jfs.name();
      }
      jfs.next();
    }
    ik = (InstanceKlass*)ik->super();
  }
  return NULL;
}

jshort EdgeUtils::field_modifiers(const Edge& edge) {
  const int offset = field_offset(edge);
  const InstanceKlass* ik = field_type(edge);

  while (ik != NULL) {
    JavaFieldStream jfs(ik);
    while (!jfs.done()) {
      if (offset == jfs.offset()) {
        return jfs.access_flags().as_short();
      }
      jfs.next();
    }
    ik = (InstanceKlass*)ik->super();
  }
  return 0;
}

bool EdgeUtils::is_array_element(const Edge& edge) {
  assert(!edge.is_root(), "invariant");
  const oop ref_owner = edge.reference_owner();
  assert(ref_owner != NULL, "invariant");
  return ref_owner->is_objArray();
}

static int array_offset(const Edge& edge) {
  assert(!edge.is_root(), "invariant");
  const oop ref_owner = edge.reference_owner();
  assert(ref_owner != NULL, "invariant");
  const oop* reference = UnifiedOop::decode(edge.reference());
  assert(reference != NULL, "invariant");
  assert(!UnifiedOop::is_narrow(reference), "invariant");
  assert(ref_owner->is_array(), "invariant");
  const objArrayOop ref_owner_array = static_cast<const objArrayOop>(ref_owner);
  const int offset = (int)pointer_delta(reference, ref_owner_array->base(), heapOopSize);
  assert(offset >= 0 && offset < ref_owner_array->length(), "invariant");
  return offset;
}

int EdgeUtils::array_index(const Edge& edge) {
  return is_array_element(edge) ? array_offset(edge) : 0;
}

int EdgeUtils::array_size(const Edge& edge) {
  if (is_array_element(edge)) {
    const oop ref_owner = edge.reference_owner();
    assert(ref_owner != NULL, "invariant");
    assert(ref_owner->is_objArray(), "invariant");
    return ((objArrayOop)(ref_owner))->length();
  }
  return 0;
}

const Edge* EdgeUtils::root(const Edge& edge) {
  const Edge* current = &edge;
  const Edge* parent = current->parent();
  while (parent != NULL) {
    current = parent;
    parent = current->parent();
  }
  assert(current != NULL, "invariant");
  return current;
}

const Edge* EdgeUtils::ancestor(const Edge& edge, size_t distance) {
  const Edge* current = &edge;
  const Edge* parent = current->parent();
  size_t seek = 0;
  while (parent != NULL && seek != distance) {
    seek++;
    current = parent;
    parent = parent->parent();
  }
  return current;
}
