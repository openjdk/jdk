/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "opto/cfgnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/replacednodes.hpp"

void ReplacedNodes::allocate_if_necessary() {
  if (_replaced_nodes == nullptr) {
    _replaced_nodes = new GrowableArray<ReplacedNode>();
  }
}

bool ReplacedNodes::is_empty() const {
  return _replaced_nodes == nullptr || _replaced_nodes->length() == 0;
}

bool ReplacedNodes::has_node(const ReplacedNode& r) const {
  return _replaced_nodes->find(r) != -1;
}

bool ReplacedNodes::has_target_node(Node* n) const {
  for (int i = 0; i < _replaced_nodes->length(); i++) {
    if (_replaced_nodes->at(i).improved() == n) {
      return true;
    }
  }
  return false;
}

// Record replaced node if not seen before
void ReplacedNodes::record(Node* initial, Node* improved) {
  allocate_if_necessary();
  ReplacedNode r(initial, improved);
  if (!has_node(r)) {
    _replaced_nodes->push(r);
  }
}

// Copy replaced nodes from one map to another. idx is used to
// identify nodes that are too new to be of interest in the target
// node list.
void ReplacedNodes::transfer_from(const ReplacedNodes& other, uint idx) {
  if (other.is_empty()) {
    return;
  }
  allocate_if_necessary();
  for (int i = 0; i < other._replaced_nodes->length(); i++) {
    ReplacedNode replaced = other._replaced_nodes->at(i);
    // Only transfer the nodes that can actually be useful
    if (!has_node(replaced) && (replaced.initial()->_idx < idx || has_target_node(replaced.initial()))) {
      _replaced_nodes->push(replaced);
    }
  }
}

void ReplacedNodes::clone() {
  if (_replaced_nodes != nullptr) {
    GrowableArray<ReplacedNode>* replaced_nodes_clone = new GrowableArray<ReplacedNode>();
    replaced_nodes_clone->appendAll(_replaced_nodes);
    _replaced_nodes = replaced_nodes_clone;
  }
}

void ReplacedNodes::reset() {
  if (_replaced_nodes != nullptr) {
    _replaced_nodes->clear();
  }
}

// Perform node replacement (used when returning to caller)
void ReplacedNodes::apply(Node* n, uint idx) {
  if (is_empty()) {
    return;
  }
  for (int i = 0; i < _replaced_nodes->length(); i++) {
    ReplacedNode replaced = _replaced_nodes->at(i);
    // Only apply if improved node was created in a callee to avoid
    // issues with irreducible loops in the caller
    if (replaced.improved()->_idx >= idx) {
      n->replace_edge(replaced.initial(), replaced.improved());
    }
  }
}

// Perform node replacement following late inlining.
void ReplacedNodes::apply(Compile* C, Node* ctl) {
  // ctl is the control on exit of the method that was late inlined
  if (is_empty()) {
    return;
  }
  ResourceMark rm;
  Node_Stack stack(0);
  Unique_Node_List to_fix; // nodes to clone + uses at the end of the chain that need to updated
  VectorSet seen;
  VectorSet valid_control;

  for (int i = 0; i < _replaced_nodes->length(); i++) {
    ReplacedNode replaced = _replaced_nodes->at(i);
    Node* initial = replaced.initial();
    Node* improved = replaced.improved();
    assert (ctl != nullptr && !ctl->is_top(), "replaced node should have actual control");

    if (initial->outcnt() == 0) {
      continue;
    }

    // Find uses of initial that are dominated by ctl so, initial can be replaced by improved.
    // Proving domination here is not straightforward. To do so, we follow uses of initial, and uses of uses until we
    // encounter a node which is a control node or is pinned at some control. Then, we try to prove this control is
    // dominated by ctl. If that's the case, it's legal to replace initial by improved but for this chain of uses only.
    // It may not be the case for some other chain of uses, so we clone that chain and perform the replacement only for
    // these uses.
    assert(stack.is_empty(), "");
    stack.push(initial, 1);
    Node* use = initial->raw_out(0);
    stack.push(use, 0);

    while (!stack.is_empty()) {
      assert(stack.size() > 1, "at least initial + one use");
      Node* n = stack.node();

      uint current_size = stack.size();

      if (seen.test_set(n->_idx)) {
        if (to_fix.member(n)) {
          collect_nodes_to_clone(stack, to_fix);
        }
      } else if (n->outcnt() != 0 && n != improved) {
        if (n->is_Phi()) {
          Node* region = n->in(0);
          if (n->req() == region->req()) { // ignore dead phis
            Node* prev = stack.node_at(stack.size() - 2);
            for (uint j = 1; j < region->req(); ++j) {
              if (n->in(j) == prev) {
                Node* in = region->in(j);
                if (in != nullptr && !in->is_top() && is_dominator(ctl, in)) {
                  valid_control.set(in->_idx);
                  collect_nodes_to_clone(stack, to_fix);
                }
              }
            }
          }
        } else if (n->is_CFG()) {
          if (is_dominator(ctl, n)) {
            collect_nodes_to_clone(stack, to_fix);
          }
        } else if (n->in(0) != nullptr && n->in(0)->is_CFG()) {
          Node* c = n->in(0);
          if (is_dominator(ctl, c)) {
            collect_nodes_to_clone(stack, to_fix);
          }
        } else {
          uint idx = stack.index();
          if (idx < n->outcnt()) {
            stack.set_index(idx + 1);
            stack.push(n->raw_out(idx), 0);
          }
        }
      }
      if (stack.size() == current_size) {
        for (;;) {
          stack.pop();
          if (stack.is_empty()) {
            break;
          }
          n = stack.node();
          uint idx = stack.index();
          if (idx < n->outcnt()) {
            stack.set_index(idx + 1);
            stack.push(n->raw_out(idx), 0);
            break;
          }
        }
      }
    }
  }
  if (to_fix.size() > 0) {
    uint hash_table_size = _replaced_nodes->length();
    for (uint i = 0; i < to_fix.size(); ++i) {
      Node* n = to_fix.at(i);
      if (n->is_CFG() || n->in(0) != nullptr) { // End of a chain is not cloned
        continue;
      }
      hash_table_size++;
    }
    // Map from current node to cloned/replaced node
    ResizeableResourceHashtable<Node*, Node*, AnyObj::RESOURCE_AREA, mtCompiler> clones(hash_table_size, hash_table_size);
    // Record mapping from initial to improved nodes
    for (int i = 0; i < _replaced_nodes->length(); i++) {
      ReplacedNode replaced = _replaced_nodes->at(i);
      Node* initial = replaced.initial();
      Node* improved = replaced.improved();
      clones.put(initial, improved);
      // If initial needs to be cloned but is also improved then there's no need to clone it.
      if (to_fix.member(initial)) {
        to_fix.remove(initial);
      }
    }

    // Clone nodes and record mapping from current to cloned nodes
    uint index_before_clone = C->unique();
    for (uint i = 0; i < to_fix.size(); ++i) {
      Node* n = to_fix.at(i);
      if (n->is_CFG() || n->in(0) != nullptr) { // End of a chain
        continue;
      }
      Node* clone = n->clone();
      bool added = clones.put(n, clone);
      assert(added, "clone node must be added to mapping");
      C->initial_gvn()->set_type_bottom(clone);
      to_fix.map(i, clone); // Update list of nodes with cloned node
    }

    // Fix edges in cloned nodes and use at the end of the chain
    for (uint i = 0; i < to_fix.size(); ++i) {
      Node* n = to_fix.at(i);
      bool is_in_table = C->initial_gvn()->hash_delete(n);
      uint updates = 0;
      for (uint j = 0; j < n->req(); ++j) {
        Node* in = n->in(j);
        if (in == nullptr || (n->is_Phi() && n->in(0)->in(j) == nullptr)) {
          continue;
        }
        if (n->is_Phi() && !valid_control.test(n->in(0)->in(j)->_idx)) {
          continue;
        }
        Node** clone_ptr = clones.get(in);
        if (clone_ptr != nullptr) {
          Node* clone = *clone_ptr;
          n->set_req(j, clone);
          if (n->_idx < index_before_clone) {
            PhaseIterGVN::add_users_of_use_to_worklist(clone, n, *C->igvn_worklist());
          }
          updates++;
        }
      }
      assert(updates > 0, "");
      C->record_for_igvn(n);
      if (is_in_table) {
        C->initial_gvn()->hash_find_insert(n);
      }
    }
  }
}

bool ReplacedNodes::is_dominator(const Node* ctl, Node* n) const {
  assert(n->is_CFG(), "should be CFG now");
  int depth = 0;
  while (n != ctl) {
    n = IfNode::up_one_dom(n);
    depth++;
    // limit search depth
    if (depth >= 100 || n == nullptr) {
      return false;
    }
  }
  return true;
}

void ReplacedNodes::dump(outputStream *st) const {
  if (!is_empty()) {
    st->print("replaced nodes: ");
    for (int i = 0; i < _replaced_nodes->length(); i++) {
      st->print("%d->%d", _replaced_nodes->at(i).initial()->_idx, _replaced_nodes->at(i).improved()->_idx);
      if (i < _replaced_nodes->length()-1) {
        st->print(",");
      }
    }
  }
}

// Merge 2 list of replaced node at a point where control flow paths merge
void ReplacedNodes::merge_with(const ReplacedNodes& other) {
  if (is_empty()) {
    return;
  }
  if (other.is_empty()) {
    reset();
    return;
  }
  int shift = 0;
  int len = _replaced_nodes->length();
  for (int i = 0; i < len; i++) {
    if (!other.has_node(_replaced_nodes->at(i))) {
      shift++;
    } else if (shift > 0) {
      _replaced_nodes->at_put(i-shift, _replaced_nodes->at(i));
    }
  }
  if (shift > 0) {
    _replaced_nodes->trunc_to(len - shift);
  }
}

void ReplacedNodes::collect_nodes_to_clone(const Node_Stack& stack, Unique_Node_List& to_fix) {
  for (uint i = stack.size() - 1; i >= 1; i--) {
    Node* n = stack.node_at(i);
    to_fix.push(n);
  }
}
