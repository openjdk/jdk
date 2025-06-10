/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

/*
  The goal of this pass is to optimize redundant conditions such as
  the second one in:

  if (i < 10) {
    if (i < 42) {

  In the branch of the first if, the type of i can be narrowed down to
  [min_jint, 9] which can then be used to constant fold the second
  condition.

  The compiler already keeps track of type[n] for every node in the
  current compilation unit. That's not sufficient to optimize the
  snippet above though because the type of i can only be narrowed in
  some section of the control flow (that is a subset of all
  controls). The solution is to build a new table that tracks the type
  of n at every control c

  type'[n, root] = type[n] // initialized from igvn's type table
  type'[n, c] = type[n, idom(c)]

  This pass start by iterating over the CFG looking for conditions such as:

  if (i < 10) {

  that allows narrowing the type of i and update the type' table
  accordingly.

  At a region r:

  type'[n, r] = meet(type'[n, r->in(1)], type'[n, r->in(2)]...)

  For a Phi phi at a region r:

  type'[phi, r] = meet(type'[phi->in(1), r->in(1)], type'[phi->in(2), r->in(2)]...)

  Once a type is narrowed, uses are enqueued and their types are
  computed by calling the Value() methods. Value() methods retrieve
  types from the type table, not the type' table. To address that
  issue while leaving Value() methods unchanged, before calling
  Value() at c, the type table is updated so:

  type[n] = type'[n, c]

  An exception is for Phi::Value which needs to retrieve the type of
  nodes are various controls: there, a new type(Node* n, Node* c)
  method is used.

  For most n and c, type'[n, c] is likely the same as type[n], the
  type recorded in the global igvn table (there shouldn't be many
  nodes at only a few control for which we can narrow the type
  down). As a consequence, the types'[n, c] table is implemented as:

  - At c, narrowed down types are stored in a GrowableArray. Each
    entry records the previous type at idom(c) and the narrowed down
    type at c.

  - The GrowableArray of type updates is recorded in a hash table
    indexed by c.

  This pass operates in 2 steps:

  - the Analyzer first iterates over the graph looking for conditions that
    narrow the types of some nodes and propagate type updates to uses
    until a fix point.

  - the Transformer transforms the graph so newly found constant nodes are folded.

*/

#include "memory/resourceArea.hpp"
#include "opto/node.hpp"
#include "opto/loopConditionalPropagation.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/movenode.hpp"
#include "opto/predicates.hpp"
#include "opto/opaquenode.hpp"
#include "opto/loopConditionalPropagation.hpp"

#ifdef ASSERT
void PhaseConditionalPropagation::TypeTable::NodeTypesList::dump() const {
  tty->print("For iteration %d at control", _iterations); _control->dump(""); tty->print_cr(" :");
  for (int i = 0; i < _node_types.length(); i++) {
    tty->print("  "); _node_types.at(i)._node->dump(""); tty->print(" ");
    _node_types.at(i)._before->dump();
    tty->print (" -> ");
    _node_types.at(i)._after->dump();
    tty->cr();
  }
}
#endif

PhaseConditionalPropagation::TypeTable::TypeTable(PhaseConditionalPropagation& conditional_propagation)
        : _node_types_list_table(nullptr),
          _conditional_propagation(conditional_propagation),
          _phase(conditional_propagation._phase) {
  _node_types_list_table = new NodeTypesListTable(8, _conditional_propagation._rpo_list.size());
}

template<class Callback> bool PhaseConditionalPropagation::TypeTable::apply_between_controls_internal(Node* c, Node* dom, Callback callback) const {
  assert(_conditional_propagation.is_dominator(dom, c), "dom should be the dominator");
  NodeTypesList* node_types_list = node_types_list_at(c);
  NodeTypesList* dom_node_types_list = node_types_list_at(dom);
  // c inherits types from its dominators so if c has no types, dominator control must have none either unless c is in a
  // loop and dominator is above the loop: types at the loop head are the result of the merge of the entry and backedge
  // types. The backedge types are not yet computed so the result of the merge is empty.
  while (true) {
    assert(node_types_list != nullptr || dom_node_types_list == nullptr ||
           c->unique_ctrl_out()->is_Loop() ||
           _conditional_propagation.is_dominator(c->unique_ctrl_out(), c) ||
           _phase->C->has_irreducible_loop(), "no types expected at dom ctrl if there's no type at current ctrl");
    if (node_types_list == nullptr) {
      return false;
    }
    if (!node_types_list->below(dom_node_types_list, _conditional_propagation)) {
      return false;
    }
    if (callback(node_types_list)) {
      return true;
    }
    node_types_list = node_types_list->prev();
  }
  return false;
}

const Type* PhaseConditionalPropagation::TypeTable::find_type_between(const Node* n, Node* c, Node* dom) const {
  const Type* res = nullptr;
  auto find_type = [&, n](NodeTypesList* node_types_list) {
    int l = node_types_list->find(n);
    if (l != -1) {
      res = node_types_list->type_at(l);
      return true;
    }
    return false;
  };
  apply_between_controls_internal(c, dom, find_type);
  return res;
}

const Type* PhaseConditionalPropagation::TypeTable::find_prev_type_between(const Node* n, Node* c, Node* dom) const {
  const Type* res = nullptr;
  auto find_type = [&, n](NodeTypesList* node_types_list) {
      int l = node_types_list->find(n);
      if (l != -1) {
        res = node_types_list->prev_type_at(l);
      }
      return false;
  };
  apply_between_controls_internal(c, dom, find_type);
  return res;
}

const Type* PhaseConditionalPropagation::TypeTable::type(Node* n, Node* c) const {
  NodeTypesList* node_types_list = node_types_list_at(c);
  if (node_types_list == nullptr || node_types_list->control() != c) {
    return nullptr;
  }
  return node_types_list->type_if_present(n);
}

template <class Callback> void PhaseConditionalPropagation::TypeTable::apply_at_control(Node* c, Callback callback) const {
  NodeTypesList* node_types_list = node_types_list_at(c);
  if (node_types_list != nullptr && node_types_list->control() == c) {
    for (int i = 0; i < node_types_list->length(); i++) {
      Node* node = node_types_list->node_at(i);
      const Type* t = node_types_list->type_at(i);
      const Type* prev_t = node_types_list->prev_type_at(i);
      callback(node, t, prev_t);
    }
  }
}

template <class Callback> void PhaseConditionalPropagation::TypeTable::apply_at_control_with_updates(Node* c, Callback callback) const {
  NodeTypesList* node_types_list = node_types_list_at(c);
  if (node_types_list != nullptr && node_types_list->control() == c) {
    for (int i = 0; i < node_types_list->length(); ) {
      Node* node = node_types_list->node_at(i);
      const Type* t = node_types_list->type_at(i);
      const Type* prev_t = node_types_list->prev_type_at(i);
      callback(node, t, prev_t);
      if (t == prev_t) {
        node_types_list->remove_at(i);
      } else {
        if (t != node_types_list->type_at(i)) {
          node_types_list->set_type_at(i, t);
        }
        if (prev_t != node_types_list->prev_type_at(i)) {
          node_types_list->set_prev_type_at(i, prev_t);
        }
        i++;
      }
    }
  }
}

bool PhaseConditionalPropagation::TypeTable::has_types_at_control(Node* c) const {
  NodeTypesList* node_types_list = node_types_list_at(c);
  return node_types_list != nullptr && node_types_list->control() == c &&  node_types_list->length() > 0;
}


const Type* PhaseConditionalPropagation::WriteableTypeTable::type_at_current_ctrl(Node* n) const {
  const Type* n_t = nullptr;
  if (_current_node_types_list != nullptr) {
    n_t = _current_node_types_list->type_if_present(n);
  }
  return n_t;
}

const Type* PhaseConditionalPropagation::WriteableTypeTable::prev_iteration_type(Node* n) const {
  if (_prev_node_types_list != nullptr) {
    return _prev_node_types_list->type_if_present(n);
  }
  return nullptr;
}

const Type* PhaseConditionalPropagation::WriteableTypeTable::prev_iteration_type(Node* n, Node* c) const {
  if (_prev_node_types_list != nullptr && _prev_node_types_list->control() == c) {
    return _prev_node_types_list->type_if_present(n);
  }
  return nullptr;
}

template <class Callback> void PhaseConditionalPropagation::WriteableTypeTable::apply_at_prev_iteration(Callback callback) const {
  if (_prev_node_types_list != nullptr) {
    for (int i = 0; i < _prev_node_types_list->length(); ++i) {
      Node* node = _prev_node_types_list->node_at(i);
      const Type* t = _prev_node_types_list->type_at(i);
      const Type* prev_t = _prev_node_types_list->prev_type_at(i);
      callback(node, t, prev_t);
    }
  }
}

template <class Callback> void PhaseConditionalPropagation::WriteableTypeTable::apply_between_controls(Node* c, Node* dom, Callback callback) const {
  auto apply_callback = [&](NodeTypesList* node_types_list) {
      for (int i = 0; i < node_types_list->length(); ++i) {
        Node* node = node_types_list->node_at(i);
        const Type* t = node_types_list->type_at(i);
        const Type* prev_t = node_types_list->prev_type_at(i);
        callback(node, node_types_list->control(), t, prev_t);
      }
      return false;
  };
  apply_between_controls_internal(c, dom, apply_callback);
}

int PhaseConditionalPropagation::WriteableTypeTable::count_updates_between_controls(Node* c, Node* dom) const {
  int cnt = 0;
  auto count_updates = [&](NodeTypesList* node_types_list) {
      cnt += node_types_list->length();
      return false;
  };
  apply_between_controls_internal(c, dom, count_updates);
  return cnt;
}

void PhaseConditionalPropagation::WriteableTypeTable::set_current_control(Node* c, bool verify, int iterations) {
  Node* dom = _phase->idom(c);
  _current_node_types_list = node_types_list_at(c);
  _dom_node_types_list = node_types_list_at(dom);
  _prev_node_types_list = nullptr;
  // no types previously recorded at this control? inherit those (if any) from the dominator
  if (_current_node_types_list == nullptr) {
    _current_node_types_list = _dom_node_types_list;
    if (_current_node_types_list != nullptr) {
      _node_types_list_table->put(c, _current_node_types_list);
      _node_types_list_table->maybe_grow(load_factor);
    }
    return;
  }
  assert(iterations > 1, "types already recorded only if there was a previous pass");
  if (_current_node_types_list == _dom_node_types_list) {
    // no change
    return;
  }
  // On a previous iteration, we inherited some types from a dominating control but, now, dominator has some new types,
  // inherit those.
  if (_current_node_types_list->control() != c) {
    assert(_dom_node_types_list != nullptr, "control have types if dominator doesn't");
    _current_node_types_list = _dom_node_types_list;
    _node_types_list_table->put(c, _current_node_types_list);
    _node_types_list_table->maybe_grow(load_factor);
    return;
  }
  // On a previous iteration, we recorded some types at this control. Make a copy. The algorithm then works on that copy
  // and possibly makes some updates. Figuring whether progress happened is then done by comparing the types from the
  // previous iteration with the possibly updated copy.
  _prev_node_types_list = _current_node_types_list->copy();
  if ((_dom_node_types_list != nullptr && _dom_node_types_list->iterations() > _current_node_types_list->iterations()) || verify) {
    if (_dom_node_types_list != nullptr && _dom_node_types_list->iterations() > _current_node_types_list->iterations()) {
      assert(verify || _dom_node_types_list->iterations() == iterations, "dom types should have been updated already");
      _current_node_types_list->set_iterations(_dom_node_types_list->iterations());
    }
    assert(_dom_node_types_list == nullptr || !_conditional_propagation.is_dominator(_current_node_types_list->control(), _dom_node_types_list->control()),
           "control for dominator types shouldn't be below control for current control's types");
    _current_node_types_list->set_prev(_dom_node_types_list);
  }
}

bool PhaseConditionalPropagation::WriteableTypeTable::record_type(Node* c, Node* n, const Type* prev_t,
                                                                  const Type* new_t, int iterations) {
  if (_current_node_types_list == _dom_node_types_list) {
    _current_node_types_list = new NodeTypesList(_dom_node_types_list, c, iterations);
    _node_types_list_table->put(c, _current_node_types_list);
    _node_types_list_table->maybe_grow(load_factor);
  }
  int i = _current_node_types_list->find(n);
  if (i == -1) {
    _current_node_types_list->push_node(n, prev_t, new_t);
    return true;
  }
  if (_current_node_types_list->type_at(i) != new_t) {
    // Update already recorded type
    const Type* old_t = _current_node_types_list->type_at(i);
    assert(narrows_type(old_t, new_t), "new type should be narrower than old one");
    _current_node_types_list->set_type_at(i, new_t);
    return true;
  }
  return false;
}

// Have types improved at this control on this iteration?
bool PhaseConditionalPropagation::WriteableTypeTable::types_improved(Node* c, int iterations, bool verify) const {
  bool progress = false;
  if (_prev_node_types_list == nullptr) {
    if (_current_node_types_list != nullptr && _current_node_types_list->length() > 0 && _current_node_types_list->control() == c) {
      // Previous iteration (if there was one) didn't record any type but current one did
      progress = true;
    }
  } else {
    int j = 0;
    assert(_current_node_types_list->control() == c, "we only track previous types at current control");
    // Go over types for current and previous iterations and compare them
    for (int i = 0; i < _current_node_types_list->length(); ++i) {
      Node* n = _current_node_types_list->node_at(i);
      const Type* current_t = _current_node_types_list->type_at(i);
      // NodeTypesList is sorted by node _idx
      for (; j < _prev_node_types_list->length() && _prev_node_types_list->node_at(j)->_idx < n->_idx; j++) {
      }
      if (j < _prev_node_types_list->length() && _prev_node_types_list->node_at(j) == n) {
        const Type* prev_t = _prev_node_types_list->type_at(j);
        assert(prev_t == prev_iteration_type(n, c), "cross check that we found the right type");
        assert(narrows_type(prev_t, current_t), "type at current iteration should narrow type at prev iteration");
        if (prev_t != current_t) {
          // Some node has a type at the current and at the previous iteration and it's not the same: we made progress
          progress = true;
        }
        j++;
      } else {
        assert(_prev_node_types_list->find(n) == -1, "cross check that n is missing from prev types");
        assert(prev_iteration_type(n, c) == nullptr, "cross check that n is missing from prev types");
        // We recorded a new type
        if (current_t != _current_node_types_list->prev_type_at(i)) {
          // When verifying, nodes are enqueued eagerly. It's only progress then if the node whose type is updated is
          // used below the current control
          if (!verify || _conditional_propagation.is_dominator(_conditional_propagation.get_early_ctrl(n), c)) {
            progress = true;
          }
        }
      }
    }
  }
  if (progress) {
    // There was progress at this iteration
    _current_node_types_list->set_iterations(iterations);
  }
  return progress;
}

bool PhaseConditionalPropagation::WorkQueue::enqueue_for_delayed_processing(Node* n, Node* c) {
  assert(!n->is_Root(), "Root should never be processed");
  if (_enqueued.test_set(n->_idx)) {
    assert((*_work_queues->get(c))->contains(n), "should already be enqueued");
    return false;
  }
  assert(c != _current_ctrl, "should be enqueued on _wq");
  GrowableArray<Node*>** wq_ptr = _work_queues->get(c);
  GrowableArray<Node*>* wq = nullptr;
  if (wq_ptr != nullptr) {
    wq = *wq_ptr;
  } else {
    wq = new GrowableArray<Node*>();
    _work_queues->put(c, wq);
    _work_queues->maybe_grow(load_factor);
  }
  assert(!wq->contains(n), "should be enqueued only once");
  wq->push(n);
  return true;
}

// Keep track of control of the main algorithm
void PhaseConditionalPropagation::WorkQueue::set_current_control(Node* c) {
  _current_ctrl = c;
  GrowableArray<Node*>* work_queue = work_queue_at(c);
  if (work_queue != nullptr) {
    while (!work_queue->is_empty()) {
      Node* n = work_queue->pop();
      _wq.push(n);
      assert(_enqueued.test(n->_idx), "enqueued node should be marked");
      _enqueued.remove(n->_idx);
    }
    _work_queues->remove(c);
  }
}

bool PhaseConditionalPropagation::WorkQueue::enqueue(Node* n, Node* c) {
  if (c == _current_ctrl) {
    _wq.push(n);
    return false;
  }
  return enqueue_for_delayed_processing(n, c);
}

#ifdef ASSERT
void PhaseConditionalPropagation::WorkQueue::dump() const {
  auto dump_entries = [&](Node* c, GrowableArray<Node*>* queue) {
    c->dump();
    for (int i = 0; i < queue->length(); i++) {
      Node* n = queue->at(i);
      n->dump();
    }
    return true;
  };
  _work_queues->iterate(dump_entries);
}
#endif


// Some node had its type narrowed at control c and u is a candidate for processing. At what control should it be
// enqueued?
Node* PhaseConditionalPropagation::Analyzer::compute_queue_control(Node* u) const {
  if (!_phase->has_node(u) || u->is_Root()) {
    return nullptr;
  }
  Node* u_c = _phase->find_non_split_ctrl(_phase->ctrl_or_self(u));
  // Always process Phi/Region at their control
  if (u->is_Phi() || u->is_Region()) {
    assert(u_c == u->in(0), "strange control");
    return u_c;
  }
  if (_current_ctrl == u_c) {
    return _current_ctrl;
  }
  // A change of type at c cannot affect a CFG node that's not at a dominated control
  if (u->is_CFG()) {
    if (_conditional_propagation.is_dominator(_current_ctrl, u_c)) {
      return u_c;
    }
    return nullptr;
  }
  if (_conditional_propagation.is_dominator(_current_ctrl, u_c)) {
    // u's control is dominated by current control. Enqueue at early control so we keep track of the earliest control
    // at which its type can be narrowed
    u_c = _phase->find_non_split_ctrl(_conditional_propagation.get_early_ctrl(u));
    if (_conditional_propagation.is_dominator(_current_ctrl, u_c)) {
      return u_c;
    }
    return _current_ctrl;
  }
  // Process a data node whose control dominates the current control right away
  if (_conditional_propagation.is_dominator(u_c, _current_ctrl)) {
    return _current_ctrl;
  }
  // Type update at control c can't affect this node
  return nullptr;
}

Node* PhaseConditionalPropagation::Analyzer::compute_queue_control(Node* u, bool at_current_ctrl) {
  Node* queue_control = compute_queue_control(u);
  if (at_current_ctrl && queue_control != _current_ctrl) {
    return nullptr;
  }
  return queue_control;
}

void PhaseConditionalPropagation::Analyzer::enqueue_use(Node* n, Node* queue_control) {
  if (queue_control == nullptr) {
    return;
  }
  if (queue_control == _current_ctrl) {
    enqueue(n, _current_ctrl);
    return;
  }
  if (_verify) {
    // enqueue use early as a stress test
    if (n->is_Phi()) {
      if (n->in(0) == _current_ctrl) {
        enqueue(n, _current_ctrl);
        return;
      }
    } else if (n->is_Region()) {
      if (n == _current_ctrl) {
        enqueue(n, _current_ctrl);
        return;
      }
    } else if (n->is_CFG()) {
      enqueue(n, _current_ctrl);
    } else {
      if (n->in(0) == nullptr) {
        enqueue(n, _current_ctrl);
        return;
      }
      assert(n->in(0)->is_CFG(), "control input of data node should be control node");
      if (_conditional_propagation.is_dominator(n->in(0), _current_ctrl)) {
        enqueue(n, _current_ctrl);
        return;
      }
    }
  }
  enqueue(n, queue_control);
}

void PhaseConditionalPropagation::Analyzer::enqueue_uses(const Node* n, bool at_current_ctrl) {
  assert(_phase->has_node(n), "dead node?");
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* u = n->fast_out(i);
    Node* queue_control = compute_queue_control(u, at_current_ctrl);
    if (queue_control == nullptr) {
      continue;
    }
    enqueue_use(u, queue_control);
    if (u->Opcode() == Op_AddI || u->Opcode() == Op_SubI) {
      for (DUIterator_Fast i2max, i2 = u->fast_outs(i2max); i2 < i2max; i2++) {
        Node* uu = u->fast_out(i2);
        if (uu->Opcode() == Op_CmpU) {
          enqueue_use(uu, compute_queue_control(uu, at_current_ctrl));
          if (_iterations > 1) {
            maybe_enqueue_if_projections_from_cmp(uu);
          }
        }
      }
    }
    if (u->is_AllocateArray()) {
      for (DUIterator_Fast i2max, i2 = u->fast_outs(i2max); i2 < i2max; i2++) {
        Node* uu = u->fast_out(i2);
        if (uu->is_Proj() && uu->as_Proj()->_con == TypeFunc::Control) {
          Node* catch_node = uu->find_out_with(Op_Catch);
          if (catch_node != nullptr) {
            assert(compute_queue_control(catch_node) != _current_ctrl, "");
            enqueue_use(catch_node, compute_queue_control(catch_node, at_current_ctrl));
          }
        }
      }
    }
    if (u->Opcode() == Op_OpaqueZeroTripGuard) {
      Node* cmp = u->unique_out();
      enqueue_use(cmp, compute_queue_control(cmp, at_current_ctrl));
      if (_iterations > 1) {
        maybe_enqueue_if_projections_from_cmp(cmp);
      }
    }
    if (u->is_Opaque1() && u->as_Opaque1()->original_loop_limit() == n) {
      for (DUIterator_Fast i2max, i2 = u->fast_outs(i2max); i2 < i2max; i2++) {
        Node* uu = u->fast_out(i2);
        if (uu->Opcode() == Op_CmpI || uu->Opcode() == Op_CmpL) {
          Node* phi = uu->as_Cmp()->countedloop_phi(u);
          if (phi != nullptr) {
            enqueue_use(phi, compute_queue_control(phi, at_current_ctrl));
          }
        }
      }
    }
    if (u->Opcode() == Op_CmpI || u->Opcode() == Op_CmpL) {
      Node* phi = u->as_Cmp()->countedloop_phi(n);
      if (phi != nullptr) {
        enqueue_use(phi, compute_queue_control(phi, at_current_ctrl));
      }
    }

    if (_iterations > 1) {
      // If this node feeds into a condition that feeds into an If, mark the if as needing work (for iterations > 1)
      maybe_enqueue_if_projections_from_cmp(u);

      if (u->Opcode() == Op_ConvL2I) {
        for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
          Node* u2 = u->fast_out(j);
          maybe_enqueue_if_projections_from_cmp(u2);
        }
      }
    }

    if (u->is_Region()) {
      for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
        Node* uu = u->fast_out(j);
        if (uu->is_Phi()) {
          enqueue_use(uu, compute_queue_control(uu, at_current_ctrl));
        }
      }
    }
  }
}

// The type of one of the inputs of a cmp was narrowed. We may be able to narrow the type of the other input further.
// Enqueue the if projections for processing
void PhaseConditionalPropagation::Analyzer::maybe_enqueue_if_projections_from_cmp(const Node* u) {
  if (!(u->Opcode() == Op_CmpI || u->Opcode() == Op_CmpL || u->Opcode() == Op_CmpU || u->Opcode() == Op_CmpUL)) {
    return;
  }
  for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
    Node* u2 = u->fast_out(j);
    if (u2->is_Bool()) {
      for (DUIterator_Fast kmax, k = u2->fast_outs(kmax); k < kmax; k++) {
        Node* u3 = u2->fast_out(k);
        if (u3->is_If()) {
          maybe_enqueue_if_projections(u3->as_If());
        } else if (u3->Opcode() == Op_OpaqueInitializedAssertionPredicate) {
          for (DUIterator_Fast lmax, l = u3->fast_outs(lmax); l < lmax; l++) {
            Node* u4 = u3->fast_out(l);
            if (u4->is_If()) {
              maybe_enqueue_if_projections(u4->as_If());
            }
          }
        }
      }
    }
  }
}

void PhaseConditionalPropagation::Analyzer::maybe_enqueue_if_projections(IfNode* iff) {
  if (!_conditional_propagation.is_dominator(_current_ctrl, iff)) {
    return;
  }
  if (iff->is_CountedLoopEnd() && iff->as_CountedLoopEnd()->loopnode() != nullptr &&
      iff->as_CountedLoopEnd()->loopnode()->is_strip_mined()) {
    iff = iff->as_CountedLoopEnd()->loopnode()->outer_loop_end();
  }
  ProjNode* proj_false = iff->proj_out(0);
  ProjNode* proj_true = iff->proj_out(1);
  assert(!_visited.test(proj_false->_idx), "already processed in this pass");
  assert(!_visited.test(proj_true->_idx), "already processed in this pass");
  enqueue(proj_false, proj_false);
  enqueue(proj_true, proj_true);
}

const PhaseConditionalPropagation::TypeTable* PhaseConditionalPropagation::Analyzer::analyze(int rounds) {
#ifdef ASSERT
  // The algorithm should need no more than 2 passes when the graph has no loop (one to narrow types, one to check for
  // no extra progress) or no more than 3 passes at most with loops (one to narrow types, one to propagate types at loop
  // heads, one to check for no extra progress).
  // However sometimes the type of a Phi at a Loop narrows slowly iteration after iteration (see PhaseValues::saturate)
  // so a few extra rounds might be needed. The following variable keeps track of that.
  int extra_rounds_loop_variable = 0;
  // Sometimes on entry to this optimization, for some nodes, a call to Value() would return a narrower type (because
  // some graph transformation in thi loop opts pass and Value hasn't been called yet). This can cause a few extra
  // rounds to be needed to reach a fix point. They are counted by the following variable.
  int extra_rounds_type_init = 0;
  bool has_infinite_loop = false;
#endif
  do {
    _iterations++;
    assert(_iterations - extra_rounds_loop_variable - extra_rounds_type_init >= 0, "inconsistent number of iterations");
    assert(_iterations - extra_rounds_type_init <= 2 || _phase->ltree_root()->_child != nullptr || has_infinite_loop, "not converging?");
    assert(_iterations - extra_rounds_loop_variable - extra_rounds_type_init <= 3 || _phase->_has_irreducible_loops, "not converging?");
    assert(_iterations < 100, "not converging");

    bool extra_loop_variable = false;
    bool extra_type_init = false;

#ifdef ASSERT
    _progress = false;
    _visited.clear();
#endif

    if (_iterations == 1) {
      // Go over the entire cfg looking for conditions that allow type narrowing
      for (int i = _rpo_list.size() - 2; i >= 0; i--) {
        Node* c = _rpo_list.at(i);
        _current_ctrl = c;
        _work_queue->set_current_control(c);
        DEBUG_ONLY(has_infinite_loop = has_infinite_loop || (c->in(0)->Opcode() == Op_NeverBranch));

        merge_with_dominator_types();
#ifdef ASSERT
        _visited.set(c->_idx);
#endif
        one_iteration(extra_loop_variable, extra_type_init);
      }
    } else {
      // Another pass of the entire cfg but this time, only process those controls that were marked at previous iteration
      for (int i = _rpo_list.size() - 2; i >= 0; i--) {
        Node* c = _rpo_list.at(i);
        _current_ctrl = c;
        _work_queue->set_current_control(c);
#ifdef ASSERT
        _visited.set(c->_idx);
#endif
        // If we recorded a narrowed type at this control for a node n on a previous pass and on this pass, we narrowed
        // the type of n at some dominating control, we need to merge the 2 updates.
        merge_with_dominator_types();
        // Was control marked as needing work?
        if (!_work_queue->is_empty(c)) {
          one_iteration(extra_loop_variable, extra_type_init);
        } else {
          if (c->is_Region()) {
            uint j;
            for (j = 1; j < c->req(); ++j) {
              Node* in = _conditional_propagation.known_updates(c->in(j));
              if (_type_table->iterations_at(in) == _iterations) {
                break;
              }
            }
            if (j < c->req()) {
              // Process region because there was some update along some of the CFG inputs
              one_iteration(extra_loop_variable, extra_type_init);
            }
          }
        }
      }
    }
#ifdef ASSERT
    if (extra_loop_variable) {
      extra_rounds_loop_variable++;
    } else if (extra_type_init) {
      extra_rounds_type_init++;
    }
#endif
    rounds--;
    if (rounds <= 0) {
      break;
    }
#ifdef ASSERT
    if (!_progress && !_work_queue->all_empty()) {
      _work_queue->dump();
    }
#endif
    assert(_progress == !_work_queue->all_empty(), "");
  } while (!_work_queue->all_empty());

#ifdef ASSERT
  if (!_work_queue->all_empty()) {
    _work_queue->dump();
    fatal("work queue is not empty");
  }
#endif

#ifdef ASSERT
  if (VerifyLoopConditionalPropagation) {
    _verify = true;
    // Verify we've indeed reached a fixed point
    _iterations++;
    bool extra_loop_variable = false;
    bool extra_type_init = false;
    _visited.clear();
    for (int i = _rpo_list.size() - 2; i >= 0; i--) {
      Node* c = _rpo_list.at(i);
      _current_ctrl = c;
      _work_queue->set_current_control(c);

      merge_with_dominator_types();
      bool progress = one_iteration(extra_loop_variable, extra_type_init);
      if (extra_type_init) {
        break;
      }
      assert(!progress, "didn't reach a fix point");
    }
    assert(extra_type_init || verify_wq_empty(), "verification fails");
  }
#endif

  sync_global_types_with_types_at_control(_phase->C->root());
  return _type_table;
}

bool PhaseConditionalPropagation::Analyzer::one_iteration(bool &extra_loop_variable, bool &extra_type_init) {
  Node* dom = _phase->idom(_current_ctrl);

  if (_current_ctrl->is_Region()) {
    handle_region(dom, extra_loop_variable);
  } else if (_current_ctrl->is_IfProj()) {
    handle_ifproj();
  } else if (_current_ctrl->is_CatchProj() && _current_ctrl->in(0)->in(0)->in(0)->is_AllocateArray() &&
             _current_ctrl->as_CatchProj()->_con == CatchProjNode::fall_through_index) {
    // If the allocation succeeds, length is > 0 and less than max supported size
    AllocateArrayNode* alloc = _current_ctrl->in(0)->in(0)->in(0)->as_AllocateArray();
    sync_global_types_with_types_at_control(dom);
    analyze_allocate_array(alloc);
  }

  propagate_types(extra_loop_variable);

  if (VerifyLoopConditionalPropagation) {
    verify(extra_type_init);
  }
  return _type_table->types_improved(_current_ctrl, _iterations, _verify);
}

void PhaseConditionalPropagation::Analyzer::propagate_types(bool &extra_type_init) {
  if (_work_queue->is_empty(_current_ctrl)) {
    return;
  }
  sync_global_types_with_types_at_control(_current_ctrl);
  while (!_work_queue->is_empty(_current_ctrl)) {
    Node* n = _work_queue->pop(_current_ctrl);
    assert(_verify || !n->is_CFG() || _conditional_propagation.is_dominator(_phase->find_non_split_ctrl(_phase->ctrl_or_self(n)), _current_ctrl),
           "only CFG nodes that dominate current control");
    const Type* t = n->Value(this);
    const Type* current_type = PhaseValues::type(n);
    if (n->is_Phi() && _iterations > 1) {
      t = current_type->filter(t);
      const Type* prev_type = _type_table->prev_iteration_type(n);
      if (prev_type != nullptr) {
        const Type* prev_t = t;
        t = prev_type->filter(t);
        assert(t == prev_t, "current type should be narrower than prev type");
        if (!(n->in(0)->is_CountedLoop() &&
              n->in(0)->as_CountedLoop()->phi() == n &&
              PhaseValues::type(n->in(0)->in(LoopNode::EntryControl)) != Type::TOP &&
              PhaseValues::type(n->in(LoopNode::EntryControl)) != Type::TOP)) {
          t = saturate(t, prev_type, nullptr);
        }
      }
      if (_current_ctrl->is_Loop() && t != prev_type) {
        extra_type_init = true;
      }
    }
    t = current_type->filter(t);
    if (t != current_type) {
#ifdef ASSERT
      assert(narrows_type(current_type, t), "new type should be narrower");
#endif
      set_type(n, t, current_type);
      enqueue_uses(n);
    }
  }
}

void PhaseConditionalPropagation::Analyzer::handle_ifproj() {
  IfNode* iff = _current_ctrl->in(0)->as_If();
  if (!(iff->is_CountedLoopEnd() && iff->as_CountedLoopEnd()->loopnode() != nullptr &&
        iff->as_CountedLoopEnd()->loopnode()->is_strip_mined())) {
    Node* bol = iff->in(1);
    if (iff->is_OuterStripMinedLoopEnd()) {
      assert(iff->in(0)->in(0)->in(0)->is_CountedLoopEnd(), "broken strip mined loop");
      bol = iff->in(0)->in(0)->in(0)->in(1);
    }
    if (bol->Opcode() == Op_OpaqueInitializedAssertionPredicate) {
      bol = bol->in(1);
    }
    if (bol->is_Bool()) {
      Node* cmp = bol->in(1);
      if (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpU ||
          cmp->Opcode() == Op_CmpL || cmp->Opcode() == Op_CmpUL) {
        Node* cmp1 = cmp->in(1);
        Node* cmp2 = cmp->in(2);
        // skip counted loop exit condition as limits can be updated later on by unrolling, RC elimination
        if (_current_ctrl->is_IfFalse()) {
          CountedLoopNode* loop = nullptr;
          if (iff->is_CountedLoopEnd()) {
            loop = iff->as_CountedLoopEnd()->loopnode();
          } else if (iff->is_OuterStripMinedLoopEnd()) {
            loop = iff->as_OuterStripMinedLoopEnd()->inner_loop();
          }
          if (loop != nullptr && loop->incr() == cmp1) {
            return;
          }
        }

        sync_global_types_with_types_at_control(iff);
        analyze_if(cmp, cmp1);
        analyze_if(cmp, cmp2);
      }
    }
  }
}

void PhaseConditionalPropagation::Analyzer::handle_region(Node* dom, bool &extra_loop_variable) {
  // Look for nodes whose types are narrowed between this region and the dominator control on all region's inputs
  // First find the region's input that has the smallest number of type updates to keep work as low as possible
  uint in_idx = 1;
  int num_types = max_jint;
  for (uint i = 1; i < _current_ctrl->req(); ++i) {
    Node* in = _current_ctrl->in(i);
    int cnt = _type_table->count_updates_between_controls(in, dom);
    if (cnt < num_types) {
      in_idx = i;
      num_types = cnt;
    }
  }
  Node* in = _current_ctrl->in(in_idx);
  auto improve_type = [&](Node* n, Node* ignored_c, const Type* ignored_t, const Type* ignored_prev_t) {
      const Type* t = _type_table->find_type_between(n, in, dom);
      // and check if the type was updated from other region inputs
      uint k = 1;
      for (; k < _current_ctrl->req(); k++) {
        if (k == in_idx) {
          continue;
        }
        Node* other_in = _current_ctrl->in(k);
        const Type* type_at_in = _type_table->find_type_between(n, other_in, dom);
        if (type_at_in == nullptr) {
          break;
        }
        t = t->meet_speculative(type_at_in);
      }
      // If that's the case, record type update
      if (k == _current_ctrl->req()) {
        const Type* dom_type = _type_table->find_prev_type_between(n, in, dom);

        assert(t == t->filter(dom_type), "");
        if (_iterations > 1) {
          t = dom_type->filter(t); // for consistency with merge_with_dominator_types()
          const Type* prev_t = t;
          const Type* prev_round_t = _type_table->prev_iteration_type(n, _current_ctrl);
          if (prev_round_t == nullptr && _iterations > 2) {
            // we may have lost the prev type if it was recorded at least 2 iterations before. Use dominator type
            // conservatively:
            // iteration i: type of n narrowed at c
            // iteration i+1: type of n narrowed at c, prev type from iteration i factored it
            // iteration i+2: dominator type of n narrowed down for some reason, type of n at c removed because redundant
            // iteration i+3: type of n narrowed at c, no prev type from iteration i+2 at c
            prev_round_t = dom_type;
          }
          if (prev_round_t != nullptr) {
            t = prev_round_t->filter(t);
            assert(t == prev_t, "new type should be narrower than previous round type");
            t = saturate(t, prev_round_t, nullptr);
            if (_current_ctrl->is_Loop() && t != prev_round_t) {
              extra_loop_variable = true;
            }
          }
          t = dom_type->filter(t);
        } else {
          assert(t == t->filter(dom_type), "");
          t = dom_type->filter(t);
        }

        if (t != dom_type) {
          assert(narrows_type(dom_type, t), "new type should be narrower");
          if (_type_table->record_type(_current_ctrl, n, dom_type, t, _iterations) || _verify) {
            enqueue_uses(n);
          }
        }
      }
  };
  _type_table->apply_between_controls(in, dom, improve_type);
}

void PhaseConditionalPropagation::Analyzer::analyze_allocate_array(const AllocateArrayNode* alloc) {
  Node* length = alloc->in(AllocateArrayNode::ALength);
  Node* klass = alloc->in(AllocateNode::KlassNode);
  const Type* klass_t = PhaseValues::type(klass);
  if (klass_t != Type::TOP) {
    const TypeOopPtr* ary_type = klass_t->is_klassptr()->as_instance_type();
    const TypeInt* length_type = PhaseValues::type(length)->isa_int();
    if (ary_type->isa_aryptr() && length_type != nullptr) {
      const Type* narrow_length_type = ary_type->is_aryptr()->narrow_size_type(length_type);
      narrow_length_type = length_type->filter(narrow_length_type);
      assert(narrows_type(length_type, narrow_length_type), "new type should be narrower");
      if (narrow_length_type != length_type) {
        if (_type_table->record_type(_current_ctrl, length, length_type, narrow_length_type, _iterations) || _verify) {
          enqueue_uses(length);
        }
      }
    }
  }
}

void PhaseConditionalPropagation::Analyzer::analyze_if(const Node* cmp, Node* n) {
  const Type* t = IfNode::filtered_int_type(this, n, _current_ctrl, (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpU) ? T_INT : T_LONG);
  if (t != nullptr) {
    const Type* n_t = type_at_current_ctrl(n);
    const Type* new_n_t = n_t->filter(t);
    assert(narrows_type(n_t, new_n_t), "new type should be narrower");
    if (n_t != new_n_t) {
      assert(narrows_type(n_t, new_n_t, true), "");
#ifdef ASSERT
      _conditional_propagation.record_condition(_current_ctrl);
#endif
      if (_type_table->record_type(_current_ctrl, n, n_t, new_n_t, _iterations) || _verify) {
        enqueue_uses(n);
      }
    }
    if (n->Opcode() == Op_ConvL2I) {
      Node* in = n->in(1);
      const Type* in_t = PhaseValues::type(in);

      if (in_t->isa_long() && in_t->is_long()->_lo >= min_jint && in_t->is_long()->_hi <= max_jint) {
        const Type* t_as_long = t->isa_int()
                                  ? TypeLong::make(t->is_int()->_lo, t->is_int()->_hi, t->is_int()->_widen)
                                  : Type::TOP;
        const Type* new_in_t = in_t->filter(t_as_long);
        assert(narrows_type(in_t, new_in_t), "new type should be narrower");
        if (in_t != new_in_t) {
#ifdef ASSERT
          _conditional_propagation.record_condition(_current_ctrl);
#endif
          if (_type_table->record_type(_current_ctrl, in, in_t, new_in_t, _iterations) || _verify) {
            enqueue_uses(in);
          }
        }
      }
    }
  }
}

void PhaseConditionalPropagation::Analyzer::enqueue(Node* n, Node* c) {
  if (_work_queue->enqueue(n, c)) {
#ifdef ASSERT
    maybe_set_progress(n, c);
#endif
  }
}

// Global type table's content is in sync with types at _current_types_ctrl. We want type of n at some other control c
const Type* PhaseConditionalPropagation::Analyzer::type(const Node* n, Node* c) const {
  if (_current_types_ctrl == _phase->C->root()) {
    const Type* res = PhaseValues::type(n);
    return res;
  }
  Node* lca = _phase->dom_lca_internal(_current_types_ctrl, c);
  // find last update between lca and c
  const Type* res = _type_table->find_type_between(n, c, lca);
  if (res != nullptr) {
    return res;
  }
  // if none, find before type of first update between current control and lca
  res = _type_table->find_prev_type_between(n, _current_types_ctrl, lca);
  if (res != nullptr) {
    return res;
  }
  // if none, go with the type at _current_types_ctrl
  res = PhaseValues::type(n);
  return res;
}

void PhaseConditionalPropagation::Analyzer::merge_with_dominator_types() {
  _type_table->set_current_control(_current_ctrl, verify(), _iterations);
  if (!_verify && (_iterations == 1 || !_type_table->has_types_at_control(_current_ctrl))) {
    return;
  }
  Node* dom = _phase->idom(_current_ctrl);
  if (!_verify && (_type_table->iterations_at(dom) != _iterations)) {
    // no updates to types at dom in this iteration
    return;
  }
  sync_global_types_with_types_at_control(dom);
  auto merge_types = [&](Node* node, const Type*& t, const Type*& prev_t) {
      const Type* dom_t = PhaseValues::type(node);
      const Type* new_t = dom_t->filter(t);
      // Updates at this control at a previous iteration combined with updates at a dominating control at the current
      // iteration could allow some progress to be made.
      // For instance: c = a + b
      // on iteration i, a's type is updated at control c
      // on iteration j (j > i), b's type is updated at some control dominating c
      // when we process control c at j, we should enqueue c for processing because narrowed type of a and b could lead
      // to a narrowed type for c
      enqueue_uses(node, true);
      if (new_t == dom_t) {
#ifdef ASSERT
        // During verification types can be narrowed at control dominating early control for the node. We have reached
        // the early control for the node, merging with dominating control results in the type before verification.
        if (verify() && _verify_wq.member(node) &&
            _phase->find_non_split_ctrl(_conditional_propagation.get_early_ctrl(node)) == _current_ctrl &&
            _type_table->prev_iteration_type(node, _current_ctrl) == new_t) {
          _verify_wq.remove(node);
        }
#endif
        enqueue_uses(node);
      } else {
        if (new_t != t) {
          assert(!verify(), "should have reached fixed point");
          enqueue_uses(node);
        }
      }
      prev_t = dom_t;
      t = new_t;
  };
  _type_table->apply_at_control_with_updates(_current_ctrl, merge_types);
}

// PhaseValues::_types is in sync with types at _current_types_ctrl, we want to update it to be in sync with types at c
void PhaseConditionalPropagation::Analyzer::sync_global_types_with_types_at_control(Node* c) {
  Node* lca = _phase->dom_lca_internal(_current_types_ctrl, c);
  // Update PhaseValues::_types to lca by undoing every update between _current_ctrl and lca
  auto sync_type_up = [&](Node* n, Node* c, const Type* ignored_t, const Type* prev_t) {
      PhaseValues::set_type(n, prev_t);
  };
  _type_table->apply_between_controls(_current_types_ctrl, lca, sync_type_up);

  // Update PhaseValues::_types to c by applying every update between lca and c
  auto sync_type_down = [&](Node* n, Node* c, const Type* t, const Type* ignored_prev_t) {
      _stack.push({ n, t} );
  };
  _type_table->apply_between_controls(c, lca, sync_type_down);

  while (!_stack.is_empty()) {
    NodeTypePair node_type = _stack.pop();
    PhaseValues::set_type(node_type._n, node_type._t);
  }

  _current_types_ctrl = c;
}

#ifdef ASSERT
void PhaseConditionalPropagation::Analyzer::verify(bool& extra_type_init) {
  Node* dom = _phase->idom(_current_ctrl);
  sync_global_types_with_types_at_control(dom);

  auto verify_current = [&](Node* node, const Type* t, const Type* prev_t) {
      assert(prev_t != t, "should have recorded a type change");
      assert(prev_t == PhaseValues::type(node), "prev should be type at dom");
      assert(narrows_type(PhaseValues::type(node), t), "only type narrowing");
      const Type* prev_round_t = _type_table->prev_iteration_type(node, _current_ctrl);
      assert(prev_round_t == nullptr || narrows_type(prev_round_t, t), "should narrow from one round to the other");
      assert(!verify() || !_conditional_propagation.is_dominator(_conditional_propagation.get_early_ctrl(node), _current_ctrl) ||
             prev_round_t == t, "there should be no more progress once we've reached verification");
      // When verify() is true, prev round type is expected to be the final type
      if (prev_round_t != nullptr) {
        // Types at this round and at previous round are the same: verification doesn't find a narrower type so previous
        // pass did reach the best type for this node at this control. No need to track the node further.
        if (prev_round_t == t && verify() && _verify_wq.member(node) &&
            _phase->find_non_split_ctrl(_conditional_propagation.get_early_ctrl(node)) == _current_ctrl) {
          _verify_wq.remove(node);
        }
      } else {
        // When verifying, nodes are enqueued for processing immediately (rather than at their early control), so new
        // types can be recorded at an earlier control than in previous passes. We keep track of them here.
        if (verify() &&
            !_conditional_propagation.is_dominator(_conditional_propagation.get_early_ctrl(node), _current_ctrl)) {
          _verify_wq.push(node);
        }
      }
  };
  _type_table->apply_at_control(_current_ctrl, verify_current);
  sync_global_types_with_types_at_control(_current_ctrl);
  auto verify_prev = [&](Node* node, const Type* t, const Type* prev_t) {
      const Type* current_round_t = _type_table->type(node, _current_ctrl);
      assert(current_round_t == nullptr || narrows_type(t, current_round_t),
             "should narrow from one round to the other");
      assert(!verify() || t == PhaseValues::type(node), "");
  };
  _type_table->apply_at_prev_iteration(verify_prev);

  sync_global_types_with_types_at_control(_phase->C->root());
  auto verify_current2 = [&](Node* node, const Type* t, const Type* prev_t) {
      if (PhaseValues::type(node) != node->Value(this) &&
          prev_t == PhaseValues::type(node)) {
        if (t == PhaseValues::type(node)->filter(node->Value(this))) {
          extra_type_init = true;
        } else if (node->is_Phi() && _current_ctrl->is_Loop() && _type_table->type(node->in(LoopNode::LoopBackControl), _current_ctrl) != nullptr) {
          assert(narrows_type(PhaseValues::type(node)->filter(node->Value(this)), t), "");
          extra_type_init = true;
        }
      }
  };
  _type_table->apply_at_control(_current_ctrl, verify_current2);
}

#endif

const Type* PhaseConditionalPropagation::Analyzer::type_at_current_ctrl(Node* n) const {
  assert(_current_types_ctrl == _phase->idom(_current_ctrl), "logic assumes PhaseValues::type() returns type at dominator");
  const Type* n_t = _type_table->type_at_current_ctrl(n);
  if (n_t == nullptr) {
    n_t = PhaseValues::type(n);
  }
  return n_t;
}

ProjNode* PhaseConditionalPropagation::Transformer::always_taken_if_proj(IfNode* iff) {
  assert(!iff->in(0)->is_top(), "");
  Node* bol = iff->in(1);
  const TypeInt* bol_t = bol->bottom_type()->is_int();
  if (bol->Opcode() == Op_OpaqueInitializedAssertionPredicate) {
    bol_t = TypeInt::ONE;
  }
  if (bol_t->is_con()) {
    return iff->proj_out(bol_t->get_con());
  }
  return nullptr;
}

// Transform the graph: constant fold subgraphs that were found constant by the Analyzer
void PhaseConditionalPropagation::Transformer::do_transform() {
  _controls.push(_phase->C->root());
  for (uint i = 0; i < _controls.size(); i++) {
    Node* c = _controls.at(i);

    if (c->is_CatchProj() && c->in(0)->in(0)->in(0)->is_AllocateArray()) {
      const Type* t = _type_table->find_type_between(c, c, _phase->C->root());
      if (t == Type::TOP) {
        _phase->igvn().replace_node(c, _phase->C->top());
        _phase->C->set_major_progress();
        continue;
      }
    }

    assert(c->_idx >= _unique || _type_table->find_type_between(c, c, _phase->C->root()) != Type::TOP,
           "for If we don't follow dead projections");
    transform_helper(c);

    if (c->is_If()) {
      IfNode* iff = c->as_If();
      Node* always_taken_proj = always_taken_if_proj(iff);
      if (always_taken_proj != nullptr) {
        assert(_type_table->type(always_taken_proj, always_taken_proj) != Type::TOP, "should not be dead");
        _controls.push(always_taken_proj);
        continue;
      }
    } else if (c->is_IfProj()) {
      IfNode* iff = c->in(0)->as_If();
      if (iff->in(0)->is_top()) {
        continue;
      }
      Node* always_taken_proj = always_taken_if_proj(iff);
      if (always_taken_proj != nullptr && always_taken_proj != c) {
        continue;
      }
    }

    for (DUIterator i = c->outs(); c->has_out(i); i++) {
      Node* u = c->out(i);
      if (u->is_CFG()) {
        _controls.push(u);
      }
    }
  }
}

bool PhaseConditionalPropagation::Transformer::related_node(Node* n, Node* c) {
  assert(_wq.size() == 0, "need to start from an empty work list");
  _wq.push(n);
  for (uint i = 0; i < _wq.size(); i++) {
    Node* node = _wq.at(i);
    assert(!node->is_CFG(), "only following data nodes");
    for (DUIterator_Fast jmax, j = node->fast_outs(jmax); j < jmax; j++) {
      Node* u = node->fast_out(j);
      if (!_phase->has_node(u)) {
        continue;
      }
      if (u->is_CFG()) {
        if (_conditional_propagation.is_dominator(u, c) || _conditional_propagation.is_dominator(c, u)) {
          _wq.clear();
          return true;
        }
      } else if (u->is_Phi()) {
        for (uint k = 1; k < u->req(); k++) {
          if (u->in(k) == node && !u->in(0)->in(k)->is_top() &&
              (_conditional_propagation.is_dominator(u->in(0)->in(k), c) || _conditional_propagation.is_dominator(c, u->in(0)->in(k)))) {
            _wq.clear();
            return true;
          }
        }
      } else {
        _wq.push(u);
      }
    }
  }
  _wq.clear();
  return false;
}

bool PhaseConditionalPropagation::Transformer::is_safe_for_replacement(Node* c, Node* node, Node* use) const {
  // if the exit test of a counted loop doesn't constant fold, preserve the shape of the exit test
  Node* node_c = _phase->get_ctrl(node);
  IdealLoopTree* loop = _phase->get_loop(node_c);
  Node* head = loop->_head;
  if (head->is_BaseCountedLoop()) {
    BaseCountedLoopNode* cl = head->as_BaseCountedLoop();
    if (cl->is_valid_counted_loop(cl->bt())) {
      Node* cmp = cl->loopexit()->cmp_node();
      if (((node == cl->phi() && use == cl->incr()) ||
           (node == cl->incr() && use == cmp))) {
        const Type* cmp_t = _type_table->find_type_between(cmp, cl->loopexit(), _phase->idom(c));
        if (cmp_t == nullptr || !cmp_t->singleton()) {
          return false;
        }
      }
    }
  }
  if (use->is_CallStaticJava() && use->as_CallStaticJava()->is_uncommon_trap()) {
    // Constant folding at uncommon traps can go wrong:
    //
    // if (0 >=u array.length) {  // range check from array[0]
    //   uncommon_trap(); // array.length constant folded as 0 here
    // }
    // ..
    // if (1 >=u array.length) {  // range check from array[1]
    //   uncommon_trap();
    // }
    // transformed by RC smearing into:
    // if (1 >=u array.length) {
    //   uncommon_trap(); // array.length constant folded as 0 here but could actually be 1
    // }
    // ..
    return false;
  }
  return true;
}

/*
 With the following code snippet:
 if (i - 1) > 0) {
    // i - 1 in [1, max]
   if (i == 0) {
     // i - 1 is both -1 and [1, max] so top

 The second if is redundant but first if updates the type of i-1, not i alone, we can't tell i != 0.
 Because i-1 becomes top in the second if branch, we can tell that branch is dead
 */
void PhaseConditionalPropagation::Transformer::transform_when_top_seen(Node* c, Node* node, const Type* t) {
  if (t->singleton()) {
    if (node->is_CFG()) {
      return;
    }
    if (t == Type::TOP) {
#ifdef ASSERT
      if (PrintLoopConditionalPropagation) {
        tty->print("top at %d", c->_idx);
        node->dump();
      }
#endif
      if (c->is_IfProj()) {
        // make sure the node has some use that dominates or are dominated by the current control
        if (!related_node(node, c)) {
          return;
        }
        IfNode* iff = c->in(0)->as_If();
        if (iff->in(0)->is_top()) {
          return;
        }
        Node* bol = iff->in(1);
        const Type* bol_t = bol->bottom_type();
        if (bol->Opcode() == Op_OpaqueInitializedAssertionPredicate) {
          bol_t = TypeInt::ONE;
        }
        const Type* new_bol_t = TypeInt::make(1 - c->as_IfProj()->_con);
        if (bol_t != new_bol_t) {
          assert((c->is_IfProj() && _conditional_propagation.condition_recorded(c)), "only for conditions that saw some type narrowing");
          jint new_bol_con = new_bol_t->is_int()->get_con();
          if (bol_t->is_int()->is_con() && bol_t->is_int()->get_con() != new_bol_con) {
            // We already constant folded the condition to the opposite constant: this path is dead
            create_halt_node(iff->in(0));
            _phase->igvn().replace_input_of(iff, 0, _phase->C->top());
          } else {
#ifndef PRODUCT
            Atomic::inc(&PhaseIdealLoop::_loop_conditional_constants);
#endif
#ifndef PRODUCT
            Atomic::inc(&PhaseIdealLoop::_loop_conditional_test);
#endif
            Node* con = _phase->igvn().makecon(new_bol_t);
            _phase->set_ctrl(con, _phase->C->root());
            _phase->igvn().rehash_node_delayed(iff);
            iff->set_req_X(1, con, &_phase->igvn());
            _phase->C->set_major_progress();
          }
#ifdef ASSERT
          if (PrintLoopConditionalPropagation) {
            tty->print_cr("killing path");
            node->dump();
            bol_t->dump();
            tty->cr();
            new_bol_t->dump();
            tty->cr();
            c->dump();
          }
#endif
        }
      } else if (node->is_Type() && related_node(node, c)) {
        node->as_Type()->make_paths_from_here_dead(&_phase->igvn(), _phase, "conditional propagation");
        _phase->C->set_major_progress();
      }
    }
  }
}

void PhaseConditionalPropagation::Transformer::create_halt_node(Node* c) const {
  Node* frame = new ParmNode(_phase->C->start(), TypeFunc::FramePtr);
  _phase->register_new_node(frame, _phase->C->start());
  Node* halt = new HaltNode(c, frame, "dead path discovered by PhaseConditionalPropagation");
  _phase->igvn().add_input_to(_phase->igvn().C->root(), halt);
  _phase->register_control(halt, _phase->ltree_root(), c);
}

void PhaseConditionalPropagation::Transformer::transform_when_constant_seen(Node* c, Node* node, const Type* t, const Type* prev_t) {
  if (t->singleton()) {
    if (node->is_CFG()) {
      return;
    } {
      Node* con = nullptr;
      for (DUIterator i = node->outs(); node->has_out(i); i++) {
        Node* use = node->out(i);
        if (use->is_Phi()) {
          Node* r = use->in(0);
          int nb_deleted = 0;
          for (uint j = 1; j < use->req(); ++j) {
            if (use->in(j) == node && !r->in(j)->is_top() && _conditional_propagation.is_dominator(c, r->in(j)) &&
                is_safe_for_replacement_at_phi(node, use, r, j)) {
              if (con == nullptr) {
                con = _phase->igvn().makecon(t);
                _phase->set_ctrl(con, _phase->igvn().C->root());
              }
              _phase->igvn().replace_input_of(use, j, con);
#ifndef PRODUCT
              Atomic::inc(&PhaseIdealLoop::_loop_conditional_constants);
#endif

              nb_deleted++;
#ifdef ASSERT
              if (PrintLoopConditionalPropagation) {
                tty->print_cr("constant folding");
                node->dump();
                tty->print("input %d of ", j);
                use->dump();
                prev_t->dump();
                tty->cr();
                t->dump();
                tty->cr();
              }
#endif
            }
          }
          if (nb_deleted > 0) {
            --i;
          }
        } else if (_conditional_propagation.is_dominator(c, _phase->ctrl_or_self(use)) &&
                   is_safe_for_replacement(c, node, use)) {
          pin_array_access_nodes_if_needed(node, t, use, c);
          pin_uses_if_needed(t, use, c);
          if (con == nullptr) {
            con = _phase->igvn().makecon(t);
            _phase->set_ctrl(con, _phase->igvn().C->root());
          }
          _phase->igvn().rehash_node_delayed(use);
          int nb = use->replace_edge(node, con, &_phase->igvn());
          --i;
#ifndef PRODUCT
          Atomic::add(&PhaseIdealLoop::_loop_conditional_constants, nb);
#endif
#ifdef ASSERT
          if (PrintLoopConditionalPropagation) {
            tty->print_cr("constant folding");
            node->dump();
            use->dump();
            prev_t->dump();
            tty->cr();
            t->dump();
            tty->cr();
          }
#endif
          if (use->is_If()) {
#ifndef PRODUCT
            Atomic::inc(&PhaseIdealLoop::_loop_conditional_test);
#endif
            _phase->C->set_major_progress();
          }
        }
      }
    }
  }
}

// Eliminating a condition that guards an array access: we need to pin the array access otherwise, it could float if it's
// dependent on a new condition that ends up being replaced by an identical dominating one.
void PhaseConditionalPropagation::Transformer::pin_array_access_nodes_if_needed(const Node* node, const Type* t, const Node* use,
                                                                                Node* c) const {
  if (t == Type::TOP) {
    return;
  }
  if (node->is_Bool()) {
    if (use->is_RangeCheck() && node->in(1)->is_Cmp()) {
      IfNode* iff = use->as_If();
      int con = t->is_int()->get_con();
      pin_array_access_nodes(c, iff, con);
    }
  } else if (node->is_Cmp()) {
    if (use->is_Bool()) {
      BoolNode* bol = use->as_Bool();
      for (DUIterator_Fast imax, i = use->fast_outs(imax); i < imax; i++) {
        Node* u = use->fast_out(i);
        if (u->is_RangeCheck()) {
          pin_array_access_nodes(c, u->as_If(), bol->_test.cc2logical(t)->is_int()->get_con());
        }
      }
    }
  }
}

// With something like:
// int div = i / (i + j)
// if (i == some_constant) {
//   res += div;
// }
// Given, div is only used in the if block, i is replaced with some_constant in the division.
// Now, Let's say i gets a value different from some_constant but j gets -some_constant. The division by i + j wouldn't
// fault but, with i constant folded to some_constant, it does fault and shouldn't be scheduled outside the if block.
void PhaseConditionalPropagation::Transformer::pin_uses_if_needed(const Type* t, Node* use, Node* c) {
  assert(_wq.size() == 0, "need to start from an empty work list");
  if (t == Type::TOP) {
    return;
  }
  _wq.push(use);
  for (uint i = 0; i < _wq.size(); ++i) {
    Node* n = _wq.at(i);
    if (n->is_CFG() || n->is_Phi() || n->bottom_type() == Type::MEMORY) {
      continue;
    }
    if (n->is_div_or_mod(T_INT) || n->is_div_or_mod(T_LONG)) {
      if (n->in(0) != nullptr && n->in(0) != c) {
        Node* early_ctrl = _phase->compute_early_ctrl(n, _phase->get_ctrl(n));
        if (early_ctrl != c && _conditional_propagation.is_dominator(early_ctrl, c)) {
          _phase->igvn().replace_input_of(n, 0, c);
        }
      }
    } else if (n->is_Load()) {
      if (n->in(0) != nullptr && n->in(0) != c) {
        Node* early_ctrl = _phase->compute_early_ctrl(n, _phase->get_ctrl(n));
        if (early_ctrl != c && _conditional_propagation.is_dominator(early_ctrl, c)) {
          Node* clone = n->pin_array_access_node();
          if (clone != nullptr) {
            clone->set_req(0, c);
            _phase->register_new_node(clone, c);
            _phase->igvn().replace_node(n, clone);
          }
        }
      }
    }
    for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
      Node* u = n->fast_out(j);
      if (_conditional_propagation.is_dominator(c, _phase->ctrl_or_self(u))) {
        _wq.push(u);
      }
    }
  }
  _wq.clear();
}

void PhaseConditionalPropagation::Transformer::pin_array_access_nodes(Node* c, const IfNode* iff, int con) const {
  ProjNode* proj = iff->proj_out(con);
  for (DUIterator i = proj->outs(); proj->has_out(i); i++) {
    Node* u = proj->out(i);
    if (u->depends_only_on_test()) {
      Node* clone = u->pin_array_access_node();
      if (clone != nullptr) {
        clone->set_req(0, c);
        _phase->register_new_node(clone, _phase->get_ctrl(u));
        _phase->igvn().replace_node(u, clone);
        --i;
      }
    }
  }
}

// We don't want to constant fold only the iv incr if the cmp doesn't constant fold as well
bool PhaseConditionalPropagation::Transformer::is_safe_for_replacement_at_phi(Node* node, Node* use, Node* r, uint j) const {
  if (!(r->is_BaseCountedLoop() &&
        j == LoopNode::LoopBackControl &&
        use == r->as_BaseCountedLoop()->phi() &&
        node == r->as_BaseCountedLoop()->incr())) {
    return false;
  }
  BaseCountedLoopNode* cl = r->as_BaseCountedLoop();
  if (!cl->is_valid_counted_loop(cl->bt())) {
    return true;
  }
  BaseCountedLoopEndNode* le = cl->loopexit();
  const Type* cmp_type = _type_table->find_type_between(le->cmp_node(), le, _phase->idom(r));
  return cmp_type != nullptr && cmp_type->singleton();
}

void PhaseConditionalPropagation::Transformer::transform_helper(Node* c) {
  auto transform_top = [&](Node* node, const Type* t, const Type* prev_t) {
    transform_when_top_seen(c, node, t);
  };
  _type_table->apply_at_control(c, transform_top);
  auto transform_constant = [&](Node* node, const Type* t, const Type* prev_t) {
    transform_when_constant_seen(c, node, t, prev_t);
  };
  _type_table->apply_at_control(c, transform_constant);
}

// Compute and cache early control for data nodes
PhaseConditionalPropagation::EarlyCtrls::EarlyCtrls(Node_Stack& nstack, PhaseConditionalPropagation& conditional_propagation)
  : _nstack(nstack), _phase(conditional_propagation._phase), _conditional_propagation(conditional_propagation) {
  _node_to_ctrl_table = new NodeToCtrl(8, _phase->C->live_nodes());
}

Node* PhaseConditionalPropagation::EarlyCtrls::known_early_ctrl(Node* n) const {
  if (n->is_CFG()) {
    return n;
  }
  if (n->pinned()) {
    return n->in(0);
  }
  Node** c_ptr = _node_to_ctrl_table->get(n);
  if (c_ptr != nullptr) {
    assert(*c_ptr == _phase->compute_early_ctrl(n, _phase->ctrl_or_self(n)), "");
    return *c_ptr;
  }
  return nullptr;
}

Node* PhaseConditionalPropagation::EarlyCtrls::compute_early_ctrl(Node* u) {
  _nstack.push(u, 0);
  Node* early_c = nullptr;
  _intermediate_results.push(nullptr); // current early ctrl: unknown
  do {
    assert(_nstack.size() == _intermediate_results.size(), "should be in sync");
    Node* n = _nstack.node();
    uint idx = _nstack.index();
    if (idx >= n->req()) {
      assert(early_c == _phase->compute_early_ctrl(n, _phase->get_ctrl(n)), "incorrect result");
      assert(_node_to_ctrl_table->get(n) == nullptr, "shouldn't have been cached already");
      _node_to_ctrl_table->put(n, early_c);
      _node_to_ctrl_table->maybe_grow(load_factor);
      _nstack.pop();
      Node* in_c = early_c;
      // pop intermediate result for early ctrl and compute updated one
      early_c = update_early_ctrl(_intermediate_results.pop(), in_c);
    } else {
      Node* in = n->in(idx);
      idx++;
      _nstack.set_index(idx);
      if (in != nullptr) {
        Node* in_c = known_early_ctrl(in);
        if (in_c == nullptr) {
          _nstack.push(in, 0);
          _intermediate_results.push(early_c); // save intermediate result for early control
          early_c = nullptr;
        } else {
          early_c = update_early_ctrl(early_c, in_c);
        }
      }
    }
  } while (_nstack.is_nonempty());
  assert(early_c == _phase->compute_early_ctrl(u, _phase->get_ctrl(u)), "incorrect result");
  return early_c;
}

// Compute and cache early control for data node
Node* PhaseConditionalPropagation::EarlyCtrls::get_early_ctrl(Node* u) {
  assert(_nstack.is_empty(), "non empty stack");
  Node* early_c = known_early_ctrl(u);
  if (early_c != nullptr) {
    return early_c;
  }
  return compute_early_ctrl(u);
}

Node* PhaseConditionalPropagation::EarlyCtrls::update_early_ctrl(Node* early_c, Node* in_c) {
  if (early_c == nullptr || _conditional_propagation.is_dominator(early_c, in_c)) {
    return in_c;
  }
  return early_c;
}

const PhaseConditionalPropagation::TypeTable* PhaseConditionalPropagation::analyze(int rounds) {
  Analyzer analyzer(*this, _visited, _rpo_list);
  return analyzer.analyze(rounds);
}

void PhaseConditionalPropagation::analyze_and_transform(int rounds) {
  const TypeTable* type_table;
  {
    TraceTime tt("loop conditional propagation analyze", UseNewCode);
    type_table = analyze(rounds);
  }
  {
    TraceTime tt("loop conditional propagation transform", UseNewCode);
    do_transform(type_table);
  }
}

#ifdef ASSERT
bool PhaseConditionalPropagation::narrows_type(const Type* old_t, const Type* new_t, bool strictly) {
  if (old_t == new_t) {
    return !strictly;
  }

  if (new_t == Type::TOP) {
    return true;
  }

  if (old_t == Type::TOP) {
    return false;
  }

  if (!new_t->isa_int() && !new_t->isa_long()) {
    return true;
  }

  assert(old_t->isa_int() || old_t->isa_long(), "can't be narrower");
  assert((old_t->isa_int() != nullptr) == (new_t->isa_int() != nullptr), "should be same basic type");

  BasicType bt = new_t->isa_int() ? T_INT : T_LONG;

  const TypeInteger* new_int = new_t->is_integer(bt);
  const TypeInteger* old_int = old_t->is_integer(bt);

  if (new_int->lo_as_long() < old_int->lo_as_long()) {
    return false;
  }

  if (new_int->hi_as_long() > old_int->hi_as_long()) {
    return false;
  }

  return true;
}
#endif

void PhaseConditionalPropagation::do_transform(const TypeTable* type_table) {
  Transformer transformer(*this, type_table);
  transformer.do_transform();
}

PhaseConditionalPropagation::DominatorTree::DominatorTree(const Node_List& rpo_list, PhaseIdealLoop* phase):
  _nodes(nullptr) {
  _nodes = new DomTreeTable(8, rpo_list.size());

  for (int i = rpo_list.size() - 1; i >= 0; i--) {
    Node* n = rpo_list.at(i);
    DomTreeNode* dt_n = new DomTreeNode(n);
    _nodes->put(n, dt_n);
    _nodes->maybe_grow(load_factor);
    if (n->is_Root()) {
      continue;
    }
    Node* dom = phase->idom(n);
    DomTreeNode** dt_dom = _nodes->get(dom);
    dt_n->_sibling = (*dt_dom)->_child;
    (*dt_dom)->_child = dt_n;
  }
  {
    ResourceMark rm;
    GrowableArray<DomTreeNode*> stack;
    stack.push(*_nodes->get(phase->C->root()));
    uint i = 1;
    while (stack.is_nonempty()) {
      DomTreeNode* current = stack.top();
      DomTreeNode* next = current->_child;
      if (next != nullptr) {
        stack.push(next);
        next->_pre = i;
        i++;
      } else {
        do {
          current = stack.pop();
          current->_post = i;
          i++;
          next = current->_sibling;
          if (next != nullptr) {
            stack.push(next);
            next->_pre = i;
            i++;
            break;
          }
        } while (stack.is_nonempty());
      }
    }
  }
}

PhaseConditionalPropagation::PhaseConditionalPropagation(PhaseIdealLoop* phase, VectorSet &visited, Node_Stack &nstack,
                                                         Node_List &rpo_list)
  : _phase(phase),
    _visited(visited),
    _rpo_list(rpo_list),
    _early_ctrls(nstack, *this),
    _dominator_tree(nullptr) {
  assert(nstack.is_empty(), "non empty stack as argument");
  assert(_rpo_list.size() == 0, "non empty list as argument");
  phase->rpo(phase->C->root(), nstack, _visited, _rpo_list);
  _dominator_tree = new DominatorTree(_rpo_list, _phase);
  // Remove control nodes at which no type update is possible
  int shift = 0;
  for (uint i = 0; i < _rpo_list.size(); ++i) {
    Node* n = _rpo_list.at(i);
    if (n->is_MultiBranch()) {
      // no type update at non projections.
      shift++;
    } else if (shift > 0) {
      _rpo_list.map(i - shift, n);
    }
  }
  while (shift > 0) {
    shift--;
    _rpo_list.pop();
  }
}

void PhaseIdealLoop::conditional_elimination(VectorSet &visited, Node_Stack &nstack, Node_List &rpo_list, int rounds) {
  TraceTime tt("loop conditional propagation", UseNewCode);
  PhaseConditionalPropagation pcp(this, visited, nstack, rpo_list);
  pcp.analyze_and_transform(rounds);
}


