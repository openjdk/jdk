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
#include "jfr/leakprofiler/chains/dfsClosure.hpp"
#include "jfr/leakprofiler/chains/edge.hpp"
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/utilities/granularTimer.hpp"
#include "jfr/leakprofiler/chains/bitset.hpp"
#include "jfr/leakprofiler/utilities/unifiedOop.hpp"
#include "jfr/leakprofiler/utilities/rootType.hpp"
#include "jfr/leakprofiler/chains/rootSetClosure.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/align.hpp"

// max dfs depth should not exceed size of stack
static const size_t max_dfs_depth = 5000;

EdgeStore* DFSClosure::_edge_store = NULL;
BitSet* DFSClosure::_mark_bits = NULL;
const Edge* DFSClosure::_start_edge = NULL;
size_t DFSClosure::_max_depth = max_dfs_depth;
bool DFSClosure::_ignore_root_set = false;

DFSClosure::DFSClosure() :
  _parent(NULL),
  _reference(NULL),
  _depth(0) {
}

DFSClosure::DFSClosure(DFSClosure* parent, size_t depth) :
  _parent(parent),
  _reference(NULL),
  _depth(depth) {
}

void DFSClosure::find_leaks_from_edge(EdgeStore* edge_store,
                                      BitSet* mark_bits,
                                      const Edge* start_edge) {
  assert(edge_store != NULL, "invariant");
  assert(mark_bits != NULL," invariant");
  assert(start_edge != NULL, "invariant");

  _edge_store = edge_store;
  _mark_bits = mark_bits;
  _start_edge = start_edge;
  _ignore_root_set = false;
  assert(_max_depth == max_dfs_depth, "invariant");

  // Depth-first search, starting from a BFS egde
  DFSClosure dfs;
  start_edge->pointee()->oop_iterate(&dfs);
}

void DFSClosure::find_leaks_from_root_set(EdgeStore* edge_store,
                                          BitSet* mark_bits) {
  assert(edge_store != NULL, "invariant");
  assert(mark_bits != NULL, "invariant");

  _edge_store = edge_store;
  _mark_bits = mark_bits;
  _start_edge = NULL;

  // Mark root set, to avoid going sideways
  _max_depth = 1;
  _ignore_root_set = false;
  DFSClosure dfs1;
  RootSetClosure::process_roots(&dfs1);

  // Depth-first search
  _max_depth = max_dfs_depth;
  _ignore_root_set = true;
  assert(_start_edge == NULL, "invariant");
  DFSClosure dfs2;
  RootSetClosure::process_roots(&dfs2);
}

void DFSClosure::closure_impl(const oop* reference, const oop pointee) {
  assert(pointee != NULL, "invariant");
  assert(reference != NULL, "invariant");

  if (GranularTimer::is_finished()) {
     return;
  }
  if (_depth == 0 && _ignore_root_set) {
    // Root set is already marked, but we want
    // to continue, so skip is_marked check.
    assert(_mark_bits->is_marked(pointee), "invariant");
  } else {
    if (_mark_bits->is_marked(pointee)) {
      return;
    }
  }

  _reference = reference;
  _mark_bits->mark_obj(pointee);
  assert(_mark_bits->is_marked(pointee), "invariant");

  // is the pointee a sample object?
  if (NULL == pointee->mark()) {
    add_chain();
  }

  assert(_max_depth >= 1, "invariant");
  if (_depth < _max_depth - 1) {
    DFSClosure next_level(this, _depth + 1);
    pointee->oop_iterate(&next_level);
  }
}

void DFSClosure::add_chain() {
  const size_t length = _start_edge == NULL ? _depth + 1 :
                        _start_edge->distance_to_root() + 1 + _depth + 1;

  ResourceMark rm;
  Edge* const chain = NEW_RESOURCE_ARRAY(Edge, length);
  size_t idx = 0;

  // aggregate from depth-first search
  const DFSClosure* c = this;
  while (c != NULL) {
    chain[idx++] = Edge(NULL, c->reference());
    c = c->parent();
  }

  assert(idx == _depth + 1, "invariant");

  // aggregate from breadth-first search
  const Edge* current = _start_edge;
  while (current != NULL) {
    chain[idx++] = Edge(NULL, current->reference());
    current = current->parent();
  }
  assert(idx == length, "invariant");
  _edge_store->add_chain(chain, length);
}

void DFSClosure::do_oop(oop* ref) {
  assert(ref != NULL, "invariant");
  assert(is_aligned(ref, HeapWordSize), "invariant");
  const oop pointee = *ref;
  if (pointee != NULL) {
    closure_impl(ref, pointee);
  }
}

void DFSClosure::do_oop(narrowOop* ref) {
  assert(ref != NULL, "invariant");
  assert(is_aligned(ref, sizeof(narrowOop)), "invariant");
  const oop pointee = RawAccess<>::oop_load(ref);
  if (pointee != NULL) {
    closure_impl(UnifiedOop::encode(ref), pointee);
  }
}
