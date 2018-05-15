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
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/chains/edgeUtils.hpp"
#include "oops/oop.inline.hpp"

RoutableEdge::RoutableEdge() : Edge() {}
RoutableEdge::RoutableEdge(const Edge* parent, const oop* reference) : Edge(parent, reference),
                                                                       _skip_edge(NULL),
                                                                       _skip_length(0),
                                                                       _processed(false) {}

RoutableEdge::RoutableEdge(const Edge& edge) : Edge(edge),
                                               _skip_edge(NULL),
                                               _skip_length(0),
                                               _processed(false) {}

RoutableEdge::RoutableEdge(const RoutableEdge& edge) : Edge(edge),
                                                      _skip_edge(edge._skip_edge),
                                                      _skip_length(edge._skip_length),
                                                      _processed(edge._processed) {}

void RoutableEdge::operator=(const RoutableEdge& edge) {
  Edge::operator=(edge);
  _skip_edge = edge._skip_edge;
  _skip_length = edge._skip_length;
  _processed = edge._processed;
}

size_t RoutableEdge::logical_distance_to_root() const {
  size_t depth = 0;
  const RoutableEdge* current = logical_parent();
  while (current != NULL) {
    depth++;
    current = current->logical_parent();
  }
  return depth;
}

traceid EdgeStore::_edge_id_counter = 0;

EdgeStore::EdgeStore() : _edges(NULL) {
  _edges = new EdgeHashTable(this);
}

EdgeStore::~EdgeStore() {
  assert(_edges != NULL, "invariant");
  delete _edges;
  _edges = NULL;
}

const Edge* EdgeStore::get_edge(const Edge* edge) const {
  assert(edge != NULL, "invariant");
  EdgeEntry* const entry = _edges->lookup_only(*edge, (uintptr_t)edge->reference());
  return entry != NULL ? entry->literal_addr() : NULL;
}

const Edge* EdgeStore::put(const Edge* edge) {
  assert(edge != NULL, "invariant");
  const RoutableEdge e = *edge;
  assert(NULL == _edges->lookup_only(e, (uintptr_t)e.reference()), "invariant");
  EdgeEntry& entry = _edges->put(e, (uintptr_t)e.reference());
  return entry.literal_addr();
}

traceid EdgeStore::get_id(const Edge* edge) const {
  assert(edge != NULL, "invariant");
  EdgeEntry* const entry = _edges->lookup_only(*edge, (uintptr_t)edge->reference());
  assert(entry != NULL, "invariant");
  return entry->id();
}

traceid EdgeStore::get_root_id(const Edge* edge) const {
  assert(edge != NULL, "invariant");
  const Edge* root = EdgeUtils::root(*edge);
  assert(root != NULL, "invariant");
  return get_id(root);
}

void EdgeStore::add_chain(const Edge* chain, size_t length) {
  assert(chain != NULL, "invariant");
  assert(length > 0, "invariant");

  size_t bottom_index = length - 1;
  const size_t top_index = 0;

  const Edge* stored_parent_edge = NULL;

  // determine level of shared ancestry
  for (; bottom_index > top_index; --bottom_index) {
    const Edge* stored_edge = get_edge(&chain[bottom_index]);
    if (stored_edge != NULL) {
      stored_parent_edge = stored_edge;
      continue;
    }
    break;
  }

  // insertion of new Edges
  for (int i = (int)bottom_index; i >= (int)top_index; --i) {
    Edge edge(stored_parent_edge, chain[i].reference());
    stored_parent_edge = put(&edge);
  }

  const oop sample_object = stored_parent_edge->pointee();
  assert(sample_object != NULL, "invariant");
  assert(NULL == sample_object->mark(), "invariant");

  // Install the "top" edge of the chain into the sample object mark oop.
  // This associates the sample object with its navigable reference chain.
  sample_object->set_mark(markOop(stored_parent_edge));
}

bool EdgeStore::is_empty() const {
  return !_edges->has_entries();
}

size_t EdgeStore::number_of_entries() const {
  return _edges->cardinality();
}

void EdgeStore::assign_id(EdgeEntry* entry) {
  assert(entry != NULL, "invariant");
  assert(entry->id() == 0, "invariant");
  entry->set_id(++_edge_id_counter);
}

bool EdgeStore::equals(const Edge& query, uintptr_t hash, const EdgeEntry* entry) {
  assert(entry != NULL, "invariant");
  assert(entry->hash() == hash, "invariant");
  return true;
}
