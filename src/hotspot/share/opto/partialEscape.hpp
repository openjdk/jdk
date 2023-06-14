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
  // clone contents but not refcnt;
  virtual ObjectState* clone() const = 0;

  int ref_cnt() const { return _refcnt; }

  virtual ObjectState& merge(ObjectState* newin, GraphKit* kit, RegionNode* region, int pnum) = 0;
};

class VirtualState: public ObjectState {
  friend class PEAState;
  const TypeOopPtr* const _oop_type;
  int _lockcnt;
  Node** _entries;


 protected:
  VirtualState(const VirtualState& other);

 public:
  VirtualState(const TypeOopPtr* oop_type);

  bool is_virtual() const override { return true; }

  ObjectState* clone() const override {
    return new VirtualState(*this);
  }

  int nfields() const;
  void set_field(ciField* field, Node* val);
  Node* get_field(int idx) const {
    assert(idx >= 0 && idx < nfields(), "sanity check");
    return _entries[idx];
  }

  ObjectState& merge(ObjectState* newin, GraphKit* kit, RegionNode* region, int pnum) override;
#ifndef PRODUCT
  void print_on(outputStream* os) const;
#endif
};

class EscapedState: public ObjectState {
  Node* _materialized;

 public:
  EscapedState(Node* materialized) : _materialized(materialized) {}

  bool is_virtual() const override { return false;}

  Node* materialized_value() const {
    return _materialized;
  }

  void set_materialized_value(Node* node) {
    _materialized = node;
  }

  ObjectState* clone() const override {
    return new EscapedState(_materialized);
  }

  ObjectState& merge(ObjectState* newin, GraphKit* kit, RegionNode* region, int pnum) override {
    assert(0, "not implemented");
    return *this;
  }
};

template<typename K, typename V>
using PEAMap = ResourceHashtable<K, V, 17, /*table_size*/
                                 AnyObj::C_HEAP, mtCompiler>;

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

  Node* get_java_oop(ObjID id, bool materialized) const;

  // Convert the state of obj#id to Escaped.
  // p is the new alias of obj#id. If materialized is true, the materiazation has taken place in code.
  // PEA expects to replace all appearances of the object with its java_oop, or materilzied_value().
  // refer to GraphKit::backfill_materialized.
  EscapedState* escape(ObjID id, Node* p, bool materialized) {
    assert(p != nullptr, "the new alias must be non-null");
    Node* old = nullptr;

    EscapedState* es;
    if (contains(id)) {
      ObjectState* os = get_object_state(id);
      // if os is EscapedState and its materialized_value is not-null,
      if (!os->is_virtual()) {
        materialized |= static_cast<EscapedState*>(os)->materialized_value() != nullptr;
      }
      es = new EscapedState(materialized ? p : nullptr);
      es->ref_cnt(os->ref_cnt()); // copy the refcnt from the original ObjectState.
      old = get_java_oop(id, false);
    } else {
      es = new EscapedState(materialized ? p : nullptr);
    }
    _state.put(id, es);
    // if p == old, no-op
    add_alias(id, p);
    if (old != nullptr && old != p) {
      remove_alias(id, old);
    }
    assert(contains(id), "sanity check");
    return es;
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
  Node* materialize(GraphKit* kit, Node* var);

  int objects(Unique_Node_List& nodes) const;
  int size() const {
    return _state.number_of_entries();
  }

  VirtualState* as_virtual(Node* var) const {
    ObjID id = is_alias(var);
    if (id != nullptr) {
      ObjectState* os = get_object_state(id);

      if (os->is_virtual()) {
        return static_cast<VirtualState*>(os);
      }
    }
    return nullptr;
  }

  void clear() {
    _state.unlink_all();
    _alias.unlink_all();
  }

  void materialize_all();

#ifndef PRODUCT
  void print_on(outputStream* os) const;
#endif

#ifdef ASSERT
  void validate() const;
#endif
};

class AllocationStateMerger {
 private:
  GrowableArray<ObjID> _live; // live objects
  PEAState& _state;

 public:
  AllocationStateMerger(PEAState& target);
  ~AllocationStateMerger(); // purge all objects which are not in live.
  void merge(const PEAState& newin, GraphKit* kit, RegionNode* region, int pnum);
  void process_phi(PhiNode* phi, Node* old);
};

void printPeaStatistics();
#endif // SHARE_OPTO_PARTIAL_ESCAPE_HPP
