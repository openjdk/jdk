/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

bool EdgeUtils::is_root(const Edge& edge) {
  return edge.is_root();
}

static int field_offset(const Edge& edge) {
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

static const InstanceKlass* field_type(const Edge& edge) {
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
  return current;
}

// The number of references associated with the leak node;
// can be viewed as the leak node "context".
// Used to provide leak context for a "capped/skipped" reference chain.
static const size_t leak_context = 100;

// The number of references associated with the root node;
// can be viewed as the root node "context".
// Used to provide root context for a "capped/skipped" reference chain.
static const size_t root_context = 100;

// A limit on the reference chain depth to be serialized,
static const size_t max_ref_chain_depth = leak_context + root_context;

const RoutableEdge* skip_to(const RoutableEdge& edge, size_t skip_length) {
  const RoutableEdge* current = &edge;
  const RoutableEdge* parent = current->physical_parent();
  size_t seek = 0;
  while (parent != NULL && seek != skip_length) {
    seek++;
    current = parent;
    parent = parent->physical_parent();
  }
  return current;
}

#ifdef ASSERT
static void validate_skip_target(const RoutableEdge* skip_target) {
  assert(skip_target != NULL, "invariant");
  assert(skip_target->distance_to_root() + 1 == root_context, "invariant");
  assert(skip_target->is_sentinel(), "invariant");
}

static void validate_new_skip_edge(const RoutableEdge* new_skip_edge, const RoutableEdge* last_skip_edge, size_t adjustment) {
  assert(new_skip_edge != NULL, "invariant");
  assert(new_skip_edge->is_skip_edge(), "invariant");
  if (last_skip_edge != NULL) {
    const RoutableEdge* const target = skip_to(*new_skip_edge->logical_parent(), adjustment);
    validate_skip_target(target->logical_parent());
    return;
  }
  assert(last_skip_edge == NULL, "invariant");
  // only one level of logical indirection
  validate_skip_target(new_skip_edge->logical_parent());
}
#endif // ASSERT

static void install_logical_route(const RoutableEdge* new_skip_edge, size_t skip_target_distance) {
  assert(new_skip_edge != NULL, "invariant");
  assert(!new_skip_edge->is_skip_edge(), "invariant");
  assert(!new_skip_edge->processed(), "invariant");
  const RoutableEdge* const skip_target = skip_to(*new_skip_edge, skip_target_distance);
  assert(skip_target != NULL, "invariant");
  new_skip_edge->set_skip_edge(skip_target);
  new_skip_edge->set_skip_length(skip_target_distance);
  assert(new_skip_edge->is_skip_edge(), "invariant");
  assert(new_skip_edge->logical_parent() == skip_target, "invariant");
}

static const RoutableEdge* find_last_skip_edge(const RoutableEdge& edge, size_t& distance) {
  assert(distance == 0, "invariant");
  const RoutableEdge* current = &edge;
  while (current != NULL) {
    if (current->is_skip_edge() && current->skip_edge()->is_sentinel()) {
      return current;
    }
    current = current->physical_parent();
    ++distance;
  }
  return current;
}

static void collapse_overlapping_chain(const RoutableEdge& edge,
                                       const RoutableEdge* first_processed_edge,
                                       size_t first_processed_distance) {
  assert(first_processed_edge != NULL, "invariant");
  // first_processed_edge is already processed / written
  assert(first_processed_edge->processed(), "invariant");
  assert(first_processed_distance + 1 <= leak_context, "invariant");

  // from this first processed edge, attempt to fetch the last skip edge
  size_t last_skip_edge_distance = 0;
  const RoutableEdge* const last_skip_edge = find_last_skip_edge(*first_processed_edge, last_skip_edge_distance);
  const size_t distance_discovered = first_processed_distance + last_skip_edge_distance + 1;

  if (distance_discovered <= leak_context || (last_skip_edge == NULL && distance_discovered <= max_ref_chain_depth)) {
    // complete chain can be accommodated without modification
    return;
  }

  // backtrack one edge from existing processed edge
  const RoutableEdge* const new_skip_edge = skip_to(edge, first_processed_distance - 1);
  assert(new_skip_edge != NULL, "invariant");
  assert(!new_skip_edge->processed(), "invariant");
  assert(new_skip_edge->parent() == first_processed_edge, "invariant");

  size_t adjustment = 0;
  if (last_skip_edge != NULL) {
    assert(leak_context - 1 > first_processed_distance - 1, "invariant");
    adjustment = leak_context - first_processed_distance - 1;
    assert(last_skip_edge_distance + 1 > adjustment, "invariant");
    install_logical_route(new_skip_edge, last_skip_edge_distance + 1 - adjustment);
  } else {
    install_logical_route(new_skip_edge, last_skip_edge_distance + 1 - root_context);
    new_skip_edge->logical_parent()->set_skip_length(1); // sentinel
  }

  DEBUG_ONLY(validate_new_skip_edge(new_skip_edge, last_skip_edge, adjustment);)
}

static void collapse_non_overlapping_chain(const RoutableEdge& edge,
                                           const RoutableEdge* first_processed_edge,
                                           size_t first_processed_distance) {
  assert(first_processed_edge != NULL, "invariant");
  assert(!first_processed_edge->processed(), "invariant");
  // this implies that the first "processed" edge is the leak context relative "leaf"
  assert(first_processed_distance + 1 == leak_context, "invariant");

  const size_t distance_to_root = edge.distance_to_root();
  if (distance_to_root + 1 <= max_ref_chain_depth) {
    // complete chain can be accommodated without constructing a skip edge
    return;
  }

  install_logical_route(first_processed_edge, distance_to_root + 1 - first_processed_distance - root_context);
  first_processed_edge->logical_parent()->set_skip_length(1); // sentinel

  DEBUG_ONLY(validate_new_skip_edge(first_processed_edge, NULL, 0);)
}

static const RoutableEdge* processed_edge(const RoutableEdge& edge, size_t& distance) {
  assert(distance == 0, "invariant");
  const RoutableEdge* current = &edge;
  while (current != NULL && distance < leak_context - 1) {
    if (current->processed()) {
      return current;
    }
    current = current->physical_parent();
    ++distance;
  }
  assert(distance <= leak_context - 1, "invariant");
  return current;
}

/*
 * Some vocabulary:
 * -----------
 * "Context" is an interval in the chain, it is associcated with an edge and it signifies a number of connected edges.
 * "Processed / written" means an edge that has already been serialized.
 * "Skip edge" is an edge that contains additional information for logical routing purposes.
 * "Skip target" is an edge used as a destination for a skip edge
 */
void EdgeUtils::collapse_chain(const RoutableEdge& edge) {
  assert(is_leak_edge(edge), "invariant");

  // attempt to locate an already processed edge inside current leak context (if any)
  size_t first_processed_distance = 0;
  const RoutableEdge* const first_processed_edge = processed_edge(edge, first_processed_distance);
  if (first_processed_edge == NULL) {
    return;
  }

  if (first_processed_edge->processed()) {
    collapse_overlapping_chain(edge, first_processed_edge, first_processed_distance);
  } else {
    collapse_non_overlapping_chain(edge, first_processed_edge, first_processed_distance);
  }

  assert(edge.logical_distance_to_root() + 1 <= max_ref_chain_depth, "invariant");
}
