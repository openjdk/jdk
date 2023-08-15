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
  Node* get_field(ciField* field) const;
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
  bool  _materialized; // we may skip passive materialization.
  Node* _merged_value; // the latest java_oop

 public:
  EscapedState(bool materialized, Node* value) : _materialized(materialized), _merged_value(value) {}

  bool is_virtual() const override { return false;}

  Node* materialized_value() const {
    return _materialized ? _merged_value : nullptr;
  }

  Node* merged_value() const {
    return _merged_value;
  }

  bool has_materialized() const {
    return _materialized;
  }

  void update(bool materialized, Node* node) {
    assert(node != nullptr, "assign a nullptr as merged value");
    assert(materialized || !_materialized, "reverting _materialized is wrong");

    _materialized = materialized;
    _merged_value = node;
  }

  void update(Node* node) {
    update(_materialized, node);
  }

  ObjectState* clone() const override {
    return new EscapedState(_materialized, _merged_value);
  }

  ObjectState& merge(ObjectState* newin, GraphKit* kit, RegionNode* region, int pnum) override {
    assert(0, "not implemented");
    return *this;
  }

#ifndef PRODUCT
  void print_on(outputStream* os) const;
#endif
};

template<typename K, typename V>
using PEAMap = ResourceHashtable<K, V, 17, /*table_size*/
                                 AnyObj::C_HEAP, mtCompiler>;

class PartialEscapeAnalysis : public AnyObj {
  // alias maps from node to ObjID.
  PEAMap<Node*, ObjID>  _aliases;
  GrowableArray<ObjID>  _objects;

public:
  PartialEscapeAnalysis(Arena* arena): _aliases(), _objects(arena, 2, 0, nullptr) {}

  ObjID is_alias(Node* node) const {
    assert(node != nullptr || !_aliases.contains(node), "_aliases.contain(nullptr) must return false");
    if (_aliases.contains(node)) {
      return *(_aliases.get(node));
    } else {
      return nullptr;
    }
  }

  // refcount is the no. of aliases which refer to the object.
  // we do garbage collection if refcnt drops to 0.
  void add_alias(ObjID id, Node* var) {
    if (_aliases.contains(var)) return;
    _aliases.put(var, id);
  }

  void remove_alias(ObjID id, Node* var) {
    assert(_aliases.contains(var) && (*_aliases.get(var)) == id, "sanity check");
    _aliases.remove(var);
  }

  // PEA tracks all new instances in the current compilation unit
  // so we could bisect for bugs.
  int add_object(ObjID obj) {
    _objects.push(obj);
    return _objects.length() - 1;
  }

  int object_idx(ObjID obj) const {
    return _objects.find(obj);
  }

  const GrowableArray<ObjID>& all_objects() const {
    return _objects;
  }
};

//TODO: rename it to allocation state.
class PEAState {
  friend class Parse;
  PEAMap<ObjID, ObjectState*> _state;

 public:
  PEAState& operator=(const PEAState& init);

  ObjectState* get_object_state(ObjID id) const {
    assert(contains(id), "object doesn't exist in allocation state");
    return *(_state.get(id));
  }

  bool contains(ObjID id) const {
#ifdef ASSERT
    if (id == nullptr) {
      assert(!_state.contains(id), "PEAState must exclude nullptr");
    }
#endif
    return _state.contains(id);
  }

  Node* get_materialized_value(ObjID id) const;

  Node* get_java_oop(ObjID id) const;

  // Convert the state of obj#id to Escaped.
  // p is the new alias of obj#id. If materialized is true, the materiazation has taken place in code.
  // PEA expects to replace all appearances of the object with its java_oop, or materilzied_value().
  // refer to GraphKit::backfill_materialized.
  EscapedState* escape(ObjID id, Node* p, bool materialized);

  void add_new_allocation(GraphKit* kit, Node* obj);
  Node* materialize(GraphKit* kit, Node* var);

  int objects(Unique_Node_List& nodes) const;
  int size() const {
    return _state.number_of_entries();
  }

  VirtualState* as_virtual(const PartialEscapeAnalysis* pea, Node* var) const {
    ObjID id = pea->is_alias(var);
    if (id != nullptr && contains(id)) {
      ObjectState* os = get_object_state(id);

      if (os->is_virtual()) {
        return static_cast<VirtualState*>(os);
      }
    }
    return nullptr;
  }

  EscapedState* as_escaped(const PartialEscapeAnalysis* pea, Node* var) const {
    ObjID id = pea->is_alias(var);
    if (id != nullptr && contains(id)) {
      ObjectState* os = get_object_state(id);

      if (!os->is_virtual()) {
        return static_cast<EscapedState*>(os);
      }
    }
    return nullptr;
  }

  void clear() {
    _state.unlink_all();
  }

  // we drop out all virtual objects when we encounter a loop header.
  void mark_all_escaped();

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
  void process_phi(PhiNode* phi, GraphKit* kit, RegionNode* region, int pnum);

 public:
  AllocationStateMerger(PEAState& target);
  ~AllocationStateMerger(); // purge all objects which are not in live.
  void merge(PEAState& newin, GraphKit* kit, RegionNode* region, int pnum);
  void merge_at_phi_creation(const PartialEscapeAnalysis* pea, PEAState& newin, PhiNode* phi, Node* m, Node* n);
};

void printPeaStatistics();
#endif // SHARE_OPTO_PARTIAL_ESCAPE_HPP
