/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_OPTO_PARTIAL_ESCAPE_HPP
#define SHARE_OPTO_PARTIAL_ESCAPE_HPP

#include "opto/callnode.hpp"
#include "opto/type.hpp"
#include "utilities/resourceHash.hpp"

class ObjectState {
 public:
  inline void* operator new(size_t x) throw() {
    Compile* C = Compile::current();
    return C->parser_arena()->AmallocWords(x);
  }

  virtual bool is_virtual() const = 0;
  virtual Node* get_materialized_value() = 0;
};

class VirtualState: public ObjectState {
  int _lockCount;
  Node** _entries;

 public:
  VirtualState(uint nfields);
  bool is_virtual() const override { return true; }
  Node* get_materialized_value() override { return nullptr; }
};

class EscapedState: public ObjectState {
  Node* const _materialized;

 public:
  EscapedState(Node* materialized) : _materialized(materialized) {}
  bool is_virtual() const override { return false;}
  Node* get_materialized_value() override { return _materialized; }
};

template<typename K, typename V>
using PEAMap = ResourceHashtable<K, V, 17, /*table_size*/
                          ResourceObj::RESOURCE_AREA, mtCompiler>;
// forward declaration
class GraphKit;

using ObjID = AllocateNode*;

class PEAState {
  friend class Parse;
  PEAMap<ObjID, ObjectState*> _state;
  PEAMap<Node*, ObjID>        _alias;

  State& intersect(const State& rhs);

 public:
  PEAState& operator=(const PEAState& init);

  ObjectState* get_object_state(ObjID id) const {
    return *(_state.get(id));
  }

  ObjID is_alias(Node* node) const {
    if (_alias.contains(node)) {
      return *(_alias.get(node));
    } else {
      return nullptr;
    }
  }

  void add_new_allocation(Node* obj);
  void merge(const PEAState& merge);

  EscapedState* materialize(GraphKit* kit, ObjID alloc, SafePointNode* map = nullptr);
};

#endif // SHARE_OPTO_PARTIAL_ESCAPE_HPP
