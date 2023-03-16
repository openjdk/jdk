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

#include "opto/type.hpp"
#include "utilities/resourceHash.hpp"

// forward declaration
class GraphKit;

class ObjectState {
  friend class PEAState;

  //  ObjectState(const ObjectState&) = delete;
 protected:
  int _refcnt = 0;
  void ref_inc() { _refcnt++; }
  int ref_dec() { return --_refcnt; }
  int ref_cnt(int cnt) {
    int old = _refcnt;
    _refcnt = cnt;
    return old;
  }

 public:
  inline void* operator new(size_t x) throw() {
    Compile* C = Compile::current();
    return C->parser_arena()->AmallocWords(x);
  }

  virtual bool is_virtual() const = 0;
  virtual Node* get_materialized_value() = 0;
  // clone contents but not refcnt;
  virtual ObjectState* clone() const = 0;

  int ref_cnt() const { return _refcnt; }

  virtual ObjectState& merge(ObjectState& newin, GraphKit* kit, const TypeOopPtr* oop_type,
                             RegionNode* region, int pnum) = 0;
};

class VirtualState: public ObjectState {
  friend class PEAState;
  int _lockCount;
  int _nfields;
  Node** _entries;

 protected:
  VirtualState(const VirtualState& other);

 public:
  VirtualState(int nfields);

  bool is_virtual() const override { return true; }
  Node* get_materialized_value() override { return nullptr; }
  ObjectState* clone() const override {
    return new VirtualState(*this);
  }

  void set_field(int idx, Node* val);
  Node* get_field(int idx) const {
    assert(idx >= 0 && idx < _nfields, "sanity check");
    return _entries[idx];
  }
  ObjectState& merge(ObjectState& newin, GraphKit* kit, const TypeOopPtr* oop_type,
                     RegionNode* region, int pnum) override;
#ifndef PRODUCT
  void print_on(outputStream* os) const;
#endif
};

class EscapedState: public ObjectState {
  Node* const _materialized;

 public:
  EscapedState(Node* materialized) : _materialized(materialized) {}

  bool is_virtual() const override { return false;}
  Node* get_materialized_value() override { return _materialized; }
  ObjectState* clone() const override {
    return new EscapedState(_materialized);
  }
  ObjectState& merge(ObjectState& newin,GraphKit* kit, const TypeOopPtr* oop_type,
                     RegionNode* region, int pnum) override {
    return *this;
  }
};

template<typename K, typename V>
using PEAMap = ResourceHashtable<K, V, 17, /*table_size*/
                                 AnyObj::C_HEAP, mtCompiler>;
using ObjID = AllocateNode*;

class PEAState {
  friend class Parse;
  PEAMap<ObjID, ObjectState*> _state;
  PEAMap<Node*, ObjID>        _alias;

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

  bool contains(ObjID id) const {
#ifdef ASSERT
    if (id == nullptr) {
      assert(!_state.contains(id), "PEAState must exclude nullptr");
    }
#endif
    return _state.contains(id);
  }

  void update(ObjID id, ObjectState* os) {
    if (contains(id)) {
      os->ref_cnt(get_object_state(id)->ref_cnt());
    }
    _state.put(id, os);
  }

  // refcount is the no. of aliases which refer to the object.
  // we do garbage collection if refcnt drops to 0.
  void add_alias(ObjID id, Node* var) {
    assert(contains(id), "sanity check");
    if (_alias.contains(var)) return;
    _alias.put(var, id);
    get_object_state(id)->ref_inc();
  }

  void remove_alias(ObjID id, Node* var);

  void add_new_allocation(Node* obj);
  EscapedState* materialize(GraphKit* kit, Node* var);

#ifndef PRODUCT
  void print_on(outputStream* os) const;
#endif

#ifdef ASSERT
  void validate() const;
#endif
};

#endif // SHARE_OPTO_PARTIAL_ESCAPE_HPP
