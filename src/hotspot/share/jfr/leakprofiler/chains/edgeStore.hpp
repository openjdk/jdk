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

#ifndef SHARE_VM_LEAKPROFILER_CHAINS_EDGESTORE_HPP
#define SHARE_VM_LEAKPROFILER_CHAINS_EDGESTORE_HPP

#include "jfr/utilities/jfrHashtable.hpp"
#include "jfr/leakprofiler/chains/edge.hpp"
#include "memory/allocation.hpp"

typedef u8 traceid;

class RoutableEdge : public Edge {
 private:
  mutable const RoutableEdge* _skip_edge;
  mutable size_t _skip_length;
  mutable bool _processed;

 public:
  RoutableEdge();
  RoutableEdge(const Edge* parent, const oop* reference);
  RoutableEdge(const Edge& edge);
  RoutableEdge(const RoutableEdge& edge);
  void operator=(const RoutableEdge& edge);

  const RoutableEdge* skip_edge() const { return _skip_edge; }
  size_t skip_length() const { return _skip_length; }

  bool is_skip_edge() const { return _skip_edge != NULL; }
  bool processed() const { return _processed; }
  bool is_sentinel() const {
    return _skip_edge == NULL && _skip_length == 1;
  }

  void set_skip_edge(const RoutableEdge* edge) const {
    assert(!is_skip_edge(), "invariant");
    assert(edge != this, "invariant");
    _skip_edge = edge;
  }

  void set_skip_length(size_t length) const {
    _skip_length = length;
  }

  void set_processed() const {
    assert(!_processed, "invariant");
    _processed = true;
  }

  // true navigation according to physical tree representation
  const RoutableEdge* physical_parent() const {
    return static_cast<const RoutableEdge*>(parent());
  }

  // logical navigation taking skip levels into account
  const RoutableEdge* logical_parent() const {
    return is_skip_edge() ? skip_edge() : physical_parent();
  }

  size_t logical_distance_to_root() const;
};

class EdgeStore : public CHeapObj<mtTracing> {
  typedef HashTableHost<RoutableEdge, traceid, Entry, EdgeStore> EdgeHashTable;
  typedef EdgeHashTable::HashEntry EdgeEntry;
  template <typename,
            typename,
            template<typename, typename> class,
            typename,
            size_t>
  friend class HashTableHost;
 private:
  static traceid _edge_id_counter;
  EdgeHashTable* _edges;

  // Hash table callbacks
  void assign_id(EdgeEntry* entry);
  bool equals(const Edge& query, uintptr_t hash, const EdgeEntry* entry);

  const Edge* get_edge(const Edge* edge) const;
  const Edge* put(const Edge* edge);

 public:
  EdgeStore();
  ~EdgeStore();

  void add_chain(const Edge* chain, size_t length);
  bool is_empty() const;
  size_t number_of_entries() const;

  traceid get_id(const Edge* edge) const;
  traceid get_root_id(const Edge* edge) const;

  template <typename T>
  void iterate_edges(T& functor) const { _edges->iterate_value<T>(functor); }
};

#endif // SHARE_VM_LEAKPROFILER_CHAINS_EDGESTORE_HPP
