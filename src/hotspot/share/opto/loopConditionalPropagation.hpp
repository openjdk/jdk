/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_OPTO_LOOPCONDITIONALPROPAGATION_HPP
#define SHARE_OPTO_LOOPCONDITIONALPROPAGATION_HPP

#include "opto/loopnode.hpp"
#include "opto/rootnode.hpp"

class PhaseConditionalPropagation : public PhaseIterGVN {
private:

  class TypeUpdate : public ResourceObj {
  private:
    class Entry {
    public:
      Entry(const Node* node, const Type* before, const Type* after)
              : _node(node), _before(before), _after(after) {
      }
      Entry()
              : _node(nullptr), _before(nullptr), _after(nullptr) {
      }

      const Node* _node;
      const Type* _before;
      const Type* _after;
    };
    GrowableArray<Entry> _updates;
    TypeUpdate* _prev;
    Node* _control;

    TypeUpdate(TypeUpdate* prev, Node* control, int size)
            : _updates(size), _prev(prev), _control(control) {
    }

  public:

    TypeUpdate(TypeUpdate* prev, Node* control)
            : _prev(prev), _control(control) {
    }

    int length() const {
      return _updates.length();
    }

    Node* node_at(int i) const {
      return const_cast<Node*>(_updates.at(i)._node);
    }

    const Type* prev_type_at(int i) const {
      return _updates.at(i)._before;
    }

    const Type* type_at(int i) const {
      return _updates.at(i)._after;
    }

    const Type* type_if_present(Node* n) {
      int i = find(n);
      if (i == -1) {
        return nullptr;
      }
      return _updates.at(i)._after;
    }


    void set_type_at(int i, const Type* t) {
      _updates.at(i)._after = t;
    }

    void set_prev_type_at(int i, const Type* t) {
      _updates.at(i)._before = t;
    }

    bool contains(Node* n) {
      return find(n) != -1;
    }

    void remove_at(int i) {
      _updates.remove_at(i);
    }

    static int compare1(const Node* const& n, const Entry& e) {
      return  n->_idx - e._node->_idx;
    }

    static int compare2(const Entry& e1, const Entry& e2) {
      return e1._node->_idx - e2._node->_idx;
    }

    int find(const Node* n) {
      bool found = false;
      int res = _updates.find_sorted<const Node*, compare1>(n, found);
      if (!found) {
        return -1;
      }
      return res;
    }

    void push_node(const Node* node, const Type* old_t, const Type* new_t) {
      _updates.insert_sorted<compare2>(Entry(node, old_t, new_t));
      assert(find(node) != -1 && _updates.at(find(node))._node == node, "");
    }

    TypeUpdate* prev() const {
      return _prev;
    }

    bool below(TypeUpdate* dom_updates, PhaseIdealLoop* phase) const {
      return this != dom_updates && (dom_updates == nullptr || !phase->is_dominator(control(), dom_updates->control()));
    }

    void set_prev(TypeUpdate* prev) {
      _prev = prev;
    }

    Node* control() const {
      return _control;
    }

    TypeUpdate* copy() const {
      TypeUpdate* c = new TypeUpdate(_prev, _control, _updates.length());
      for (int i = 0; i < _updates.length(); ++i) {
        c->_updates.push(_updates.at(i));
      }
      return c;
    }
  };

  using Updates = ResizeableResourceHashtable<Node*, TypeUpdate*, AnyObj::RESOURCE_AREA, mtInternal>;
  Updates* _updates;

  bool related_use(Node* u, Node* c);

  void enqueue_uses(const Node* n, Node* c);

  void set_type(const Node* n, const Type* t, const Type* old_t) {
    record_update(_current_ctrl, n, old_t, t);
    PhaseValues::set_type(n, t);
  }

  GrowableArray<TypeUpdate*> _stack;

  void sync(Node* c);

  PhaseIdealLoop* _phase;
  VectorSet& _visited;
  VectorSet _control_dependent_node[2];
  Node_List& _rpo_list;
  Node* _current_ctrl;
#ifdef ASSERT
  VectorSet _conditions;
#endif
  Unique_Node_List _wq;
  Unique_Node_List _wq2;

  bool _progress;
  int _iterations;

public:
  PhaseConditionalPropagation(PhaseIdealLoop* phase, VectorSet &visited, Node_Stack &nstack, Node_List &rpo_list);

  Node* known_updates(Node* c) const {
    return _phase->find_non_split_ctrl(c);
  }

  void analyze(int rounds);

  static const int load_factor = 8;

  bool one_iteration(Node* c, bool& extra, bool& extra2, bool verify);

  void analyze_allocate_array(Node* c, const AllocateArrayNode* alloc);

  TypeUpdate* updates_at(Node* c) const {
    TypeUpdate** updates_ptr = _updates->get(known_updates(c));
    if (updates_ptr == nullptr) {
      return nullptr;
    }
    return *updates_ptr;
  }

  const Type* type_if_present(Node* c, Node* n) const {
    TypeUpdate* updates = updates_at(c);
    if (updates == nullptr) {
      return nullptr;
    }
    return updates->type_if_present(n);
  }


  const Type* find_type_between(Node* n, Node* c, Node* dom) const;

  const Type* find_prev_type_between(Node* n, Node* c, Node* dom) const;

  bool record_update(Node* c, const Node* n, const Type* old_t, const Type* new_t);

  void analyze_if(Node* c, const Node* cmp, Node* n);

#ifdef ASSERT
  bool narrows_type(const Type* old_t, const Type* new_t);
#endif

  void do_transform();

  bool validate_control(Node* n, Node* c);

  bool is_safe_for_replacement(Node* c, Node* node, Node* use);

  bool transform_when_top_seen(Node* c, Node* node, const Type* t);

  bool transform_when_constant_seen(Node* c, Node* node, const Type* t, const Type* prev_t);

  bool is_safe_for_replacement_at_phi(Node* node, Node* use, Node* r, uint j) const;

  bool transform_helper(Node* c);

  const Type* type(const Node* n, Node* c) const;

  virtual PhaseConditionalPropagation* is_ConditionalPropagation() { return this; }

  TypeUpdate* _current_updates;
  TypeUpdate* _dom_updates;
  TypeUpdate* _prev_updates;

  void adjust_updates(Node* c, bool verify);

  void mark_if(IfNode* iff, Node* c);

  void mark_if_from_cmp(const Node* u, Node* c);
};

#endif // SHARE_OPTO_LOOPCONDITIONALPROPAGATION_HPP
