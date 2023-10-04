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
      if (n->outcnt() != 0 && n != improved) {
        if (n->is_Phi()) {
          Node* region = n->in(0);
          Node* prev = stack.node_at(stack.size() - 2);
          for (uint j = 1; j < region->req(); ++j) {
            if (n->in(j) == prev) {
              Node* in = region->in(j);
              if (in != nullptr && !in->is_top()) {
                if (is_dominator(ctl, in)) {
                  clone_uses_and_replace(C, initial, improved, stack, j);
                }
              }
            }
          }
        } else if (n->is_CFG()) {
          if (is_dominator(ctl, n)) {
            clone_uses_and_replace(C, initial, improved, stack, -1);
          }
        } else if (n->in(0) != nullptr && !n->in(0)->is_top()) {
          Node* c = n->in(0);
          if (is_dominator(ctl, c)) {
            clone_uses_and_replace(C, initial, improved, stack, -1);
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
}

// Clone all nodes on the stack and replace initial by improved for the use at the bottom of the stack
void ReplacedNodes::clone_uses_and_replace(Compile* C, Node* initial, Node* improved, const Node_Stack& stack, int i) const {
  Node* prev = stack.node();
  for (uint k = stack.size() - 2; k > 0 ; k--) {
    Node* n = stack.node_at(k);
    Node* clone = n->clone();
    bool is_in_table = C->initial_gvn()->hash_delete(prev);
    if (i == -1) {
      int replaced = prev->replace_edge(n, clone);
      assert(replaced > 0, "expected some use");
    } else {
      assert(k == (stack.size() - 2) && prev->is_Phi(), "");
      assert(prev->in(i) == n, "not a use?");
      prev->set_req(i, clone);
      i = -1;
    }
    C->record_for_igvn(prev);
    if (is_in_table) {
      C->initial_gvn()->hash_find_insert(prev);
    }
    C->initial_gvn()->set_type_bottom(clone);
    prev = clone;
  }
  bool is_in_table = C->initial_gvn()->hash_delete(prev);
  if (i == -1) {
    int replaced = prev->replace_edge(initial, improved);
    assert(replaced > 0, "expected some use");
  } else {
    assert(prev->is_Phi(), "only for Phis");
    assert(prev->in(i) == initial, "not a use?");
    prev->set_req(i, improved);
  }
  C->record_for_igvn(prev);
  if (is_in_table) {
    C->initial_gvn()->hash_find_insert(prev);
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
