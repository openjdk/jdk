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

  This pass iterates over the CFG looking for conditions such as:

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
  nodes are various controls: there a new type(Node* n, Node* c)
  method is used.

  For most n and c, type'[n, c] is likely the same as type[n], the
  type recorded in the global igvn table (that there shouldn't be many
  nodes at only a few control for which we can narrow the type
  down). As a consequence, the types'[n, c] table is implemented as:

  - At c, narrowed down types are stored in a GrowableArray. Each
    entry records the previous type at idom(c) and the narrowed down
    type at c.

  - The GrowableArray of type updates is recorded in a hash table
    indexed by c. If there's no update at c, there's no entry in the
    hash table.

  This pass operates in 2 steps:

  - it first iterates over the graph looking for conditions that
    narrow the types of some nodes and propagate type updates to uses
    until a fix point.

  - it transforms the graph so newly found constant nodes are folded.

*/

#include "memory/resourceArea.hpp"
#include "node.hpp"
#include "precompiled.hpp"
#include "opto/loopConditionalPropagation.hpp"
#include "opto/callnode.hpp"
#include "opto/movenode.hpp"
#include "opto/opaquenode.hpp"


bool PhaseConditionalPropagation::related_use(Node* u, Node* c) {
  if (!_phase->has_node(u)) {
    return false;
  }
  if (u->is_Phi()) {
    if (u->in(0) ==  c) {
      return true;
    }
    int iterations = _iterations;
    if (_visited.test(u->in(0)->_idx)) {
      _progress = true;
      iterations = _iterations + 1;
    }
    // mark as needing processing either in the pass over the CFG (control not yet processed) or on the next one
    _control_dependent_node[iterations%2].set(u->in(0)->_idx);
    _control_dependent_node[iterations%2].set(u->_idx);
    return false;
  }
  Node* u_c = _phase->ctrl_or_self(u);
  if (!_phase->is_dominator(c, u_c) && (u->is_CFG() || !_phase->is_dominator(u_c, c))) {
    return false;
  }
  if (!u->is_CFG() && u->in(0) != nullptr && u->in(0)->is_CFG() && !_phase->is_dominator(u->in(0), c)) {
    // mark as needing processing either in the pass over the CFG (control not yet processed) or on the next one
    assert(!_visited.test(u->in(0)->_idx), "");
    _control_dependent_node[_iterations%2].set(u->in(0)->_idx);
    _control_dependent_node[_iterations%2].set(u->_idx);
    return false;
  }
  return true;
}

void PhaseConditionalPropagation::enqueue_uses(const Node* n, Node* c) {
  assert(_phase->has_node(const_cast<Node*>(n)), "");
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* u = n->fast_out(i);
    if (related_use(u, c)) {
      _wq.push(u);
      if (u->Opcode() == Op_AddI || u->Opcode() == Op_SubI) {
        for (DUIterator_Fast i2max, i2 = u->fast_outs(i2max); i2 < i2max; i2++) {
          Node* uu = u->fast_out(i2);
          if (uu->Opcode() == Op_CmpU && related_use(uu, c)) {
            _wq.push(uu);
          }
        }
      }
      if (u->is_AllocateArray()) {
        for (DUIterator_Fast i2max, i2 = u->fast_outs(i2max); i2 < i2max; i2++) {
          Node* uu = u->fast_out(i2);
          if (uu->is_Proj() && uu->as_Proj()->_con == TypeFunc::Control) {
            Node* catch_node = uu->find_out_with(Op_Catch);
            if (catch_node != nullptr) {
              _wq.push(catch_node);
            }
          }
        }
      }
      if (u->Opcode() == Op_OpaqueZeroTripGuard) {
        Node* cmp = u->unique_out();
        if (related_use(cmp, c)) {
          _wq.push(cmp);
        }
      }
      if (u->is_Opaque1() && u->as_Opaque1()->original_loop_limit() == n) {
        for (DUIterator_Fast i2max, i2 = u->fast_outs(i2max); i2 < i2max; i2++) {
          Node* uu = u->fast_out(i2);
          if (uu->Opcode() == Op_CmpI || uu->Opcode() == Op_CmpL) {
            Node* phi = uu->as_Cmp()->countedloop_phi(u);
            if (phi != nullptr && related_use(phi, c)) {
              _wq.push(phi);
            }
          }
        }
      }
      if (u->Opcode() == Op_CmpI || u->Opcode() == Op_CmpL) {
        Node* phi = u->as_Cmp()->countedloop_phi(n);
        if (phi != nullptr && related_use(phi, c)) {
          _wq.push(phi);
        }
      }

      // If this node feeds into a condition that feeds into an If, mark the if as needing work (for iterations > 1)
      mark_if_from_cmp(u, c);

      if (u->Opcode() == Op_ConvL2I) {
        for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
          Node* u2 = u->fast_out(j);
          mark_if_from_cmp(u2, c);
        }
      }

      if (u->is_Region()) {
        for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
          Node* uu = u->fast_out(j);
          if (uu->is_Phi() && related_use(uu, c)) {
            _wq.push(uu);
          }
        }
      }
    }
  }
}

void PhaseConditionalPropagation::mark_if_from_cmp(const Node* u, Node* c) {
  if (u->Opcode() == Op_CmpI || u->Opcode() == Op_CmpL || u->Opcode() == Op_CmpU || u->Opcode() == Op_CmpUL) {
    for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
      Node* u2 = u->fast_out(j);
      if (u2->is_Bool()) {
        for (DUIterator_Fast kmax, k = u2->fast_outs(kmax); k < kmax; k++) {
          Node* u3 = u2->fast_out(k);
          if (u3->is_If()) {
            mark_if(u3->as_If(), c);
          } else if (u3->Opcode() == Op_Opaque4) {
            for (DUIterator_Fast lmax, l = u3->fast_outs(lmax); l < lmax; l++) {
              Node* u4 = u3->fast_out(l);
              if (u4->is_If()) {
                mark_if(u4->as_If(), c);
              }
            }
          }
        }
      }
    }
  }
}

void PhaseConditionalPropagation::mark_if(IfNode* iff, Node* c) {
  if (_phase->is_dominator(c, iff)) {
    ProjNode* proj_false = iff->proj_out(0);
    ProjNode* proj_true = iff->proj_out(1);
    assert(!_visited.test(proj_false->_idx), "");
    assert(!_visited.test(proj_true->_idx), "");
    _control_dependent_node[_iterations % 2].set(proj_false->_idx);
    _control_dependent_node[_iterations % 2].set(proj_true->_idx);
  }
}

// PhaseValues::_types is in sync with types at _current_ctl, we want to update it to be in sync with types at c
void PhaseConditionalPropagation::sync(Node* c) {
  Node* lca = _phase->dom_lca_internal(_current_ctrl, c);
  // Update PhaseValues::_types to lca by undoing every update between _current_ctrl and lca
  TypeUpdate* lca_updates = updates_at(lca);
  {
    TypeUpdate* updates = updates_at(_current_ctrl);
    while (updates != lca_updates) {
      assert(updates != nullptr, "");
      assert(lca_updates == nullptr || !_phase->is_dominator(updates->control(), lca_updates->control()), "");

      for (int i = 0; i < updates->length(); ++i) {
        Node* n = updates->node_at(i);
        const Type* t = updates->prev_type_at(i);
        PhaseValues::set_type(n, t);
      }
      updates = updates->prev();
    }
  }
  // Update PhaseValues::_types to c by applying every update between lca and c
  {
    TypeUpdate* updates = updates_at(c);
    assert(_stack.length() == 0, "");
    while (updates != lca_updates) {
      assert(updates != nullptr, "");
      assert(lca_updates == nullptr || !_phase->is_dominator(updates->control(), lca_updates->control()), "");
      _stack.push(updates);
      updates = updates->prev();
    }
    while (_stack.length() > 0) {
      TypeUpdate* updates = _stack.pop();
      for (int i = 0; i < updates->length(); ++i) {
        Node* n = updates->node_at(i);
        const Type* t = updates->type_at(i);
        PhaseValues::set_type(n, t);
      }
    }
  }
  _current_ctrl = c;
}

void PhaseConditionalPropagation::analyze(int rounds) {
#ifdef ASSERT
  int extra_rounds = 0;
  int extra_rounds2 = 0;
  bool has_infinite_loop = false;
#endif
  VectorSet updated;
  while (_progress) {
    _control_dependent_node[_iterations % 2].clear();
    _iterations++;
    assert(_iterations - extra_rounds - extra_rounds2 >= 0, "");
    assert(_iterations - extra_rounds2 <= 2 || _phase->ltree_root()->_child != nullptr || has_infinite_loop, "");
    assert(_iterations - extra_rounds - extra_rounds2 <= 3 || _phase->_has_irreducible_loops, "");
    assert(_iterations < 100, "");

    bool extra = false;
    bool extra2 = false;
    _progress = false;

    _visited.clear();

    if (_iterations == 1) {
      // Go over the entire cfg looking for conditions that allow type narrowing
      for (int i = _rpo_list.size() - 1; i >= 0; i--) {
        Node* c = _rpo_list.at(i);
        DEBUG_ONLY(has_infinite_loop = has_infinite_loop || (c->in(0)->Opcode() == Op_NeverBranch));

        adjust_updates(c, false);
        _visited.set(c->_idx);
        one_iteration(c, extra, extra2, false);
      }
    } else {
      // Another pass of the entire cfg but this time, only process those controls that were marked at previous iteration
      updated.clear();
      for (int i = _rpo_list.size() - 1; i >= 0; i--) {
        assert(_wq.size() == 0, "");
        Node* c = _rpo_list.at(i);
        _visited.set(c->_idx);
        Node* dom = known_updates(_phase->idom(c));
        bool adjust_updates_called = false;
        // If we recorded a narrowed type at this control for a node n on a previous pass and on this pass, we narrowed
        // the type of n at some dominating control, we need to merge the 2 updates. The "updated" set is used to keep track
        // of parts of the CFG where updates happened on this pass.
        if (updated.test(dom->_idx)) {
          adjust_updates_called = true;
          adjust_updates(c, false);
          updated.set(c->_idx);
        }
        // Was control marked as needing work?
        if (_control_dependent_node[_iterations % 2].test(c->_idx) || _wq.size() > 0) {
          if (!adjust_updates_called) {
            adjust_updates(c, false);
          }
          if (one_iteration(c, extra, extra2, false)) {
            updated.set(c->_idx);
          }
        } else {
          if (c->is_Region()) {
            uint j;
            for (j = 1; j < c->req(); ++j) {
              if (updated.test(known_updates(c->in(j))->_idx)) {
                break;
              }
            }
            if (j < c->req()) {
              if (!adjust_updates_called) {
                adjust_updates(c, false);
              }
              // Process region because there was some update along some of the CFG inputs
              if (one_iteration(c, extra, extra2, false)) {
                updated.set(c->_idx);
              }
            }
          }
        }
      }
    }
#ifdef ASSERT
    if (extra) {
      extra_rounds++;
    } else if (extra2) {
      extra_rounds2++;
    }
#endif
    rounds--;
    if (rounds <= 0) {
      break;
    }
  }

#ifdef ASSERT
  // Verify we've indeed reached a fixed point
  _control_dependent_node[_iterations % 2].clear();
  _iterations++;
  bool extra = false;
  bool extra2 = false;
  _visited.clear();
  for (int i = _rpo_list.size() - 1; i >= 0; i--) {
    int rpo = _rpo_list.size() - 1 - i;
    Node* c = _rpo_list.at(i);

    adjust_updates(c, true);
    bool progress = one_iteration(c, extra, extra2, true);
    if (extra2) {
      break;
    }
    assert(!progress, "");
  }
#endif

  sync(C->root());
}

bool PhaseConditionalPropagation::one_iteration(Node* c, bool& extra, bool& extra2, bool verify) {
  Node* dom = _phase->idom(c);

  if (c->is_Region()) {
    // Look for nodes whose types are narrowed between this region and the dominator control on all region's inputs
    // First find the region's input that has the smallest number of type updates to keep processing as low as possible
    uint in_idx = 1;
    int num_types = max_jint;
    for (uint i = 1 ; i < c->req(); ++i) {
      Node* in = c->in(i);
      TypeUpdate* updates = updates_at(in);
      int cnt = 0;
      while(updates != nullptr && updates->below(_dom_updates, _phase)) {
        cnt += updates->length();
        updates = updates->prev();
      }
      if (cnt < num_types) {
        in_idx = i;
        num_types = cnt;
      }
    }
    Node* in = c->in(in_idx);
    Node* ctrl = in;
    TypeUpdate* updates = updates_at(ctrl);
    assert(updates != nullptr || _dom_updates == nullptr || _phase->is_dominator(c, in) || C->has_irreducible_loop(), "");
    // now go over all type updates between this region input and the dominator
    while(updates != nullptr && updates->below(_dom_updates, _phase)) {
      for (int j = 0; j < updates->length(); ++j) {
        Node* n = updates->node_at(j);
        if (n->is_CMove()) {
          /*
           If we have:
           // type of v1 is [min, max]
           v2 = v1 > 0 ? v1 : 0; // type of v2 is [min, max]
           if (v1 > 0) {
             // type of v1 is [1, max]
             ..
             // type of v2 is [1, max]
           } else {
             // type of v1 is [min, 0]
             ..
             // type of v2 is 0
           }
           // type of v2 is [0, max]

           The if with the identical condition helps narrow down the value of the CMove beyond what C2 can usually figure
           out. Now if the if is eliminated as the compilation process moves on, a subsequent pass of this optimization
           may not be able  to narrow the type of CMove again. This graph shape shows up in the loop opts pass that creates
           the CMove (corresponding if not yet eliminated). Inconsistencies between passes of this optimization can cause
           issues so skip merging of CMove entirely.
           */
          continue;
        }
        const Type* t = find_type_between(n, in, dom);
        // and check if the type was updated from other region inputs
        uint k = 1;
        for (; k < c->req(); k++) {
          if (k == in_idx) {
            continue;
          }
          Node* other_in = c->in(k);
          const Type* type_at_in = find_type_between(n, other_in, dom);
          if (type_at_in == nullptr) {
            break;
          }
          t = t->meet_speculative(type_at_in);
        }
        // If that's the case, record type update
        if (k == c->req()) {
          const Type* current_type = find_prev_type_between(n, in, dom);

          if (_iterations > 1) {
            t = current_type->filter(t);
            const Type* prev_t = t;
            const Type* prev_round_t = nullptr;
            if (_prev_updates != nullptr && _prev_updates->control() == c) {
              prev_round_t = _prev_updates->type_if_present(n);
            }
            if (prev_round_t == nullptr) {
              prev_round_t = current_type;
            }
            if (prev_round_t != nullptr) {
              t = prev_round_t->filter(t);
              assert(t == prev_t, "");
              t = saturate(t, prev_round_t, nullptr);
              if (c->is_Loop() && t != prev_round_t) {
                extra = true;
              }
            }
          }
          t = current_type->filter(t);

          if (t != current_type) {
            assert(narrows_type(current_type, t), "");
            if (record_update(c, n, current_type, t)) {
              enqueue_uses(n, c);
            }
          }
        }
      }
      updates = updates->prev();
      assert(updates != nullptr || _dom_updates == nullptr || _phase->is_dominator(c, in) || C->has_irreducible_loop(), "");
    }
  } else if (c->is_IfProj()) {
    Node* iff = c->in(0);
    assert(iff->is_If(), "");
    if (!(iff->is_CountedLoopEnd() && iff->as_CountedLoopEnd()->loopnode() != nullptr &&
          iff->as_CountedLoopEnd()->loopnode()->is_strip_mined())) {
      Node* bol = iff->in(1);
      if (iff->is_OuterStripMinedLoopEnd()) {
        assert(iff->in(0)->in(0)->in(0)->is_CountedLoopEnd(), "");
        bol = iff->in(0)->in(0)->in(0)->in(1);
      }
      if (bol->Opcode() == Op_Opaque4) {
        bol = bol->in(1);
      }
      if (bol->is_Bool()) {
        Node* cmp = bol->in(1);
        if (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpU ||
            cmp->Opcode() == Op_CmpL || cmp->Opcode() == Op_CmpUL) {
          Node* cmp1 = cmp->in(1);
          Node* cmp2 = cmp->in(2);
          sync(iff);
          // narrowing the type of a LoadRange could cause a range check to optimize out and a Load to be hoisted above
          // checks that guarantee its within bounds
          if (cmp1->Opcode() != Op_LoadRange) {
            analyze_if(c, cmp, cmp1);
          }
          if (cmp2->Opcode() != Op_LoadRange) {
            analyze_if(c, cmp, cmp2);
          }
        }
      }
    }
  } else if (c->is_CatchProj() && c->in(0)->in(0)->in(0)->is_AllocateArray() &&
             c->as_CatchProj()->_con == CatchProjNode::fall_through_index) {
    // If the allocation succeeds, length is > 0 and less than max supported size
    AllocateArrayNode* alloc = c->in(0)->in(0)->in(0)->as_AllocateArray();
    sync(dom);
    analyze_allocate_array(c, alloc);
  }
  if (verify || _control_dependent_node[_iterations%2].test(c->_idx)) {
    for (DUIterator_Fast imax, i = c->fast_outs(imax); i < imax; i++) {
      Node* u = c->fast_out(i);
      if (!u->is_CFG() && u->in(0) == c && u->Opcode() != Op_CheckCastPP && _phase->has_node(u) && (verify || _control_dependent_node[_iterations%2].test(u->_idx))) {
        _wq.push(u);
      }
    }
  }

  sync(c);
  while (_wq.size() > 0) {
    Node* n = _wq.pop();
    const Type* t = n->Value(this);
    const Type* current_type = PhaseValues::type(n);
    if (n->is_Phi() && _iterations > 1) {
      t = current_type->filter(t);
      const Type* prev_type = nullptr;
      if (_prev_updates != nullptr) {
        prev_type = _prev_updates->type_if_present(n);
      }
      if (prev_type != nullptr) {
        const Type* prev_t = t;
        t = prev_type->filter(t);
        assert(t == prev_t, "");
        if (!(n->in(0)->is_CountedLoop() && n->in(0)->as_CountedLoop()->phi() == n &&
              n->in(0)->as_CountedLoop()->can_be_counted_loop(this))) {
          t = saturate(t, prev_type, nullptr);
        }
      }
      if (c->is_Loop() && t != prev_type) {
        extra = true;
      }
    }
    t = current_type->filter(t);
    if (t != current_type) {
#ifdef ASSERT
      assert(narrows_type(current_type, t), "");
#endif
      set_type(n, t, current_type);
      enqueue_uses(n, c);
    }
  }

  bool progress = false;
  if (_prev_updates == nullptr) {
    if (_current_updates != nullptr && _current_updates->length() > 0 && _current_updates->control() == c) {
      progress = true;
#ifdef ASSERT
      sync(dom);
      for (int i = 0; i < _current_updates->length(); ++i) {
        Node* n = _current_updates->node_at(i);
        assert(_current_updates->prev_type_at(i) == PhaseValues::type(n), "");
        assert(narrows_type(PhaseValues::type(n), _current_updates->type_at(i)), "");
      }
#endif
    }
  } else {
#ifdef ASSERT
    sync(dom);
#endif

    int j = 0;
    assert(_current_updates->control() == c, "");
    for (int i = 0; i < _current_updates->length(); ++i) {
      Node* n = _current_updates->node_at(i);
      assert(_current_updates->prev_type_at(i) == PhaseValues::type(n), "");
      const Type* current_t = _current_updates->type_at(i);
      assert(narrows_type(PhaseValues::type(n), current_t), "");
      for (; j < _prev_updates->length() && _prev_updates->node_at(j)->_idx < n->_idx; j++) {
        assert(narrows_type(_prev_updates->type_at(j), PhaseValues::type(_prev_updates->node_at(j))), "");
      }
      if (j < _prev_updates->length() && _prev_updates->node_at(j) == n) {
        const Type* prev_t = _prev_updates->type_at(j);
        assert(narrows_type(prev_t, current_t), "");
        if (prev_t != current_t) {
          progress = true;
        }
        j++;
      } else {
        assert(_prev_updates->find(n) == -1, "");
        if (current_t != _current_updates->prev_type_at(i)) {
          progress = true;
        }
      }
    }
#ifdef ASSERT
    for (; j < _prev_updates->length(); j++) {
      assert(narrows_type(_prev_updates->type_at(j), PhaseValues::type(_prev_updates->node_at(j))), "");
    }
#endif
  }
#ifdef ASSERT
  if (_current_updates != nullptr && _current_updates->control() == c) {
    sync(C->root());
    for (int i = 0; i < _current_updates->length(); ++i) {
      Node* n = _current_updates->node_at(i);
      if (PhaseValues::type(n) != n->Value(this) &&
          _current_updates->prev_type_at(i) == PhaseValues::type(n)) {
        if (_current_updates->type_at(i) == PhaseValues::type(n)->filter(n->Value(this))) {
          extra2 = true;
        } else if (n->is_Phi() && c->is_Loop() && _current_updates->find(n->in(LoopNode::LoopBackControl)) != -1) {
          assert(narrows_type(PhaseValues::type(n)->filter(n->Value(this)), _current_updates->type_at(i)), "");
          extra2 = true;
        }
      }
    }
  }
#endif
  return progress;
}

void PhaseConditionalPropagation::adjust_updates(Node* c, bool verify) {
  Node* dom = _phase->idom(c);
  _current_updates = updates_at(c);
  _dom_updates = updates_at(dom);
  _prev_updates = nullptr;
  if (_current_updates == nullptr) {
    _current_updates = _dom_updates;
    if (_current_updates != nullptr) {
      _updates->put(c, _current_updates);
      _updates->maybe_grow(load_factor);
    }
  } else {
    assert(_iterations > 1, "");
    if (_current_updates == _dom_updates) {
      // do nothing
    } else if (_current_updates->control() != c) {
      assert(_dom_updates != nullptr, "");
      _current_updates = _dom_updates;
      _updates->put(c, _current_updates);
      _updates->maybe_grow(load_factor);
    } else {
      _prev_updates = _current_updates->copy();
      sync(dom);
      for (int j = 0; j < _current_updates->length();) {
        Node* n = _current_updates->node_at(j);
        const Type* dom_t = PhaseValues::type(n);
        const Type* t = _current_updates->type_at(j);
        const Type* new_t = dom_t->filter(t);
        if (new_t == dom_t) {
          _current_updates->remove_at(j);
          enqueue_uses(n, c);
        } else {
          _current_updates->set_prev_type_at(j, dom_t);
          if (new_t != t) {
            assert(!verify, "");
            _current_updates->set_type_at(j, new_t);
            enqueue_uses(n, c);
          }
          j++;
        }
      }
      assert(_dom_updates == nullptr || !_phase->is_dominator(_current_updates->control(), _dom_updates->control()), "");
      _current_updates->set_prev(_dom_updates);
    }
  }
}

void PhaseConditionalPropagation::analyze_allocate_array(Node* c, const AllocateArrayNode* alloc) {
  Node* length = alloc->in(AllocateArrayNode::ALength);
  Node* klass = alloc->in(AllocateNode::KlassNode);
  const Type* klass_t = PhaseValues::type(klass);
  if (klass_t != Type::TOP) {
    const TypeOopPtr* ary_type = klass_t->is_klassptr()->as_instance_type();
    const TypeInt* length_type = PhaseValues::type(length)->isa_int();
    if (ary_type->isa_aryptr() && length_type != nullptr) {
      const Type* narrow_length_type = ary_type->is_aryptr()->narrow_size_type(length_type);
      narrow_length_type = length_type->filter(narrow_length_type);
      assert(narrows_type(length_type, narrow_length_type), "");
      if (narrow_length_type != length_type) {
        if (record_update(c, length, length_type, narrow_length_type)) {
          enqueue_uses(length, c);
        }
      }
    }
  }
}

const Type* PhaseConditionalPropagation::find_type_between(Node* n, Node* c, Node* dom) const {
  assert(_phase->is_dominator(dom, c), "");
  TypeUpdate* updates = updates_at(c);
  TypeUpdate* dom_updates = updates_at(dom);
  while(updates != nullptr && updates->below(dom_updates, _phase)) {
    int l = updates->find(n);
    if (l != -1) {
      return updates->type_at(l);
    }
    updates = updates->prev();
  }
  return nullptr;
}

const Type* PhaseConditionalPropagation::find_prev_type_between(Node* n, Node* c, Node* dom) const {
  assert(_phase->is_dominator(dom, c), "");
  TypeUpdate* updates = updates_at(c);
  TypeUpdate* dom_updates = updates_at(dom);
  const Type* res = nullptr;
  while(updates->below(dom_updates, _phase)) {
    assert(updates != nullptr,"");
    int l = updates->find(n);
    if (l != -1) {
      res = updates->prev_type_at(l);
    }
    updates = updates->prev();
  }
  return res;
}

bool PhaseConditionalPropagation::record_update(Node* c, const Node* n, const Type* old_t, const Type* new_t) {
  if (_current_updates == _dom_updates) {
    _current_updates = new TypeUpdate(_dom_updates, c);
    _updates->put(c, _current_updates);
    _updates->maybe_grow(load_factor);
  }
  int i = _current_updates->find(n);
  if (i == -1) {
    _current_updates->push_node(n, old_t, new_t);
    return true;
  } else if (_current_updates->type_at(i) != new_t) {
    _current_updates->set_type_at(i, new_t);
    return true;
  }
  return false;
}

void PhaseConditionalPropagation::analyze_if(Node* c, const Node* cmp, Node* n) {
  const Type* t = IfNode::filtered_int_type(this, n, c, (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpU) ? T_INT : T_LONG);
  if (t != nullptr) {
    const Type* n_t = PhaseValues::type(n);
    const Type* new_n_t = n_t->filter(t);
    assert(narrows_type(n_t, new_n_t), "");
    if (n_t != new_n_t) {
#ifdef ASSERT
      _conditions.set(c->_idx);
#endif
      if (record_update(c, n, n_t, new_n_t)) {
        enqueue_uses(n, c);
        if (!n->is_Phi()) {
          _wq.push(n);
        }
      }
    }
    if (n->Opcode() == Op_ConvL2I) {
      Node* in = n->in(1);
      const Type* in_t = PhaseValues::type(in);

      if (in_t->isa_long() && in_t->is_long()->_lo >= min_jint && in_t->is_long()->_hi <= max_jint) {
        const Type* t_as_long = t->isa_int() ? TypeLong::make(t->is_int()->_lo, t->is_int()->_hi, t->is_int()->_widen) : Type::TOP;
        const Type* new_in_t = in_t->filter(t_as_long);
        assert(narrows_type(in_t, new_in_t), "");
        if (in_t != new_in_t) {
#ifdef ASSERT
          _conditions.set(c->_idx);
#endif
          if (record_update(c, in, in_t, new_in_t)) {
            enqueue_uses(in, c);
            if (!in->is_Phi()) {
              _wq.push(in);
            }
          }
        }
      }
    }
  }
}

#ifdef ASSERT
bool PhaseConditionalPropagation::narrows_type(const Type* old_t, const Type* new_t) {
  if (old_t == new_t) {
    return true;
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

  assert(old_t->isa_int() || old_t->isa_long(), "");
  assert((old_t->isa_int() != nullptr) == (new_t->isa_int() != nullptr), "");

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

void PhaseConditionalPropagation::do_transform() {
  _wq.push(_phase->C->root());
  bool progress = false;
  for (uint i = 0; i < _wq.size(); i++) {
    Node* c = _wq.at(i);

    if (c->is_CatchProj() && c->in(0)->in(0)->in(0)->is_AllocateArray()) {
      const Type* t = find_type_between(c, c, C->root());
      if (t == Type::TOP) {
        replace_node(c, _phase->C->top());
        _phase->C->set_major_progress();
        continue;
      }
    } else {
      assert(find_type_between(c, c, C->root()) != Type::TOP, "");
    }

    for (DUIterator i = c->outs(); c->has_out(i); i++) {
      Node* u = c->out(i);
      if (u->is_CFG() && !_wq.member(u)) {
        if (transform_helper(u)) {
          progress = true;
        }
      }
    }

  }
}

bool PhaseConditionalPropagation::validate_control(Node* n, Node* c) {
  ResourceMark rm;
  Unique_Node_List wq;
  wq.push(n);
  for (uint i = 0; i < wq.size(); i++) {
    Node* node = wq.at(i);
    assert(!node->is_CFG(), "");
    for (DUIterator_Fast jmax, j = node->fast_outs(jmax); j < jmax; j++) {
      Node* u = node->fast_out(j);
      if (!_phase->has_node(u)) {
        continue;
      }
      if (u->is_CFG()) {
        if (_phase->is_dominator(u, c) || _phase->is_dominator(c, u)) {
          return true;
        }
      } else if (u->is_Phi()) {
        for (uint k = 1; k < u->req(); k++) {
          if (u->in(k) == node && (_phase->is_dominator(u->in(0)->in(k), c) || _phase->is_dominator(c, u->in(0)->in(k)))) {
            return true;
          }
        }
      } else {
        wq.push(u);
      }
    }
  }
  return false;
}

bool PhaseConditionalPropagation::is_safe_for_replacement(Node* c, Node* node, Node* use) {
  // if the exit test of a counted loop doesn't constant fold, preserve the shape of the exit test
  Node* node_c = _phase->get_ctrl(node);
  IdealLoopTree* loop = _phase->get_loop(node_c);
  Node* head = loop->_head;
  if (head->is_BaseCountedLoop()) {
    BaseCountedLoopNode* cl = head->as_BaseCountedLoop();
    Node* cmp = cl->loopexit()->cmp_node();
    if (((node == cl->phi() && use == cl->incr()) ||
         (node == cl->incr() && use == cmp))) {
      const Type* cmp_t = find_type_between(cmp, cl->loopexit(), c);
      if (cmp_t == nullptr || !cmp_t->singleton()) {
        return false;
      }
    }
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
bool PhaseConditionalPropagation::transform_when_top_seen(Node* c, Node* node, const Type* t) {
  if (t->singleton()) {
    if (node->is_CFG()) {
      return false;
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
        if (!validate_control(node, c)) {
          return false;
        }
        Node* iff = c->in(0);
        if (iff->in(0)->is_top()) {
          return false;
        }
        Node* bol = iff->in(1);
        const Type* bol_t = bol->bottom_type();
        const Type* new_bol_t = TypeInt::make(1 - c->as_IfProj()->_con);
        if (bol_t != new_bol_t) {
          assert((c->is_IfProj() && _conditions.test(c->_idx)), "");
          if (bol_t->is_int()->is_con() && bol_t->is_int()->get_con() != new_bol_t->is_int()->get_con()) {
            // undetected dead path
            Node* frame = new ParmNode(C->start(), TypeFunc::FramePtr);
            // can't use register_new_node here
            register_new_node_with_optimizer(frame);
            _phase->set_ctrl(frame, C->start());
            Node* halt = new HaltNode(iff->in(0), frame, "dead path discovered by PhaseConditionalPropagation");
            add_input_to(C->root(), halt);
            // can't use register_control here
            register_new_node_with_optimizer(halt);
            _phase->set_loop(halt, _phase->ltree_root());
            _phase->set_idom(halt, iff->in(0), _phase->dom_depth(iff->in(0))+1);
            replace_input_of(iff, 0, C->top());
          } else {
            Node* con = makecon(new_bol_t);
            _phase->set_ctrl(con, C->root());
            rehash_node_delayed(iff);
            iff->set_req_X(1, con, this);
          }
          _phase->C->set_major_progress();
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
          return true;
        }
      }
    }
  }
  return false;
}

bool PhaseConditionalPropagation::transform_when_constant_seen(Node* c, Node* node, const Type* t, const Type* prev_t) {
  if (t->singleton()) {
    if (node->is_CFG()) {
      return false;
    }
    {
      Node* con = nullptr;
      bool progress = false;
      assert(_wq2.size() == 0, "");
      for (DUIterator_Fast imax, i = node->fast_outs(imax); i < imax; i++) {
        Node* use = node->fast_out(i);
        if (_wq2.member(use)) {
          continue;
        }
        _wq2.push(use);
        if (use->is_Phi()) {
          Node* r = use->in(0);
          if (r->Opcode() == Op_Region && r->req() == 3 &&
              ((r->in(1)->is_IfProj() && r->in(1)->in(0)->is_CountedLoopEnd() &&
                r->in(1)->in(0)->as_CountedLoopEnd()->loopnode() != nullptr &&
                r->in(1)->in(0)->as_CountedLoopEnd()->loopnode()->is_main_loop()) ||
               (r->in(2)->is_IfProj() && r->in(2)->in(0)->is_CountedLoopEnd() &&
                r->in(2)->in(0)->as_CountedLoopEnd()->loopnode() != nullptr &&
                r->in(2)->in(0)->as_CountedLoopEnd()->loopnode()->is_main_loop()))) {
            // Bounds of main loop may be adjusted. Can't constant fold.
            continue;
          }
          int nb_deleted = 0;
          for (uint j = 1; j < use->req(); ++j) {
            if (use->in(j) == node && _phase->is_dominator(c, r->in(j)) &&
                is_safe_for_replacement_at_phi(node, use, r, j)) {
              progress = true;
              if (con == NULL) {
                con = makecon(t);
                _phase->set_ctrl(con, C->root());
              }
              replace_input_of(use, j, con);
              nb_deleted++;
#ifdef ASSERT
              if (PrintLoopConditionalPropagation) {
                tty->print_cr("constant folding");
                node->dump();
                tty->print("input %d of ", j); use->dump();
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
            imax -= nb_deleted;
          }
        } else if (_phase->is_dominator(c, _phase->ctrl_or_self(use)) && is_safe_for_replacement(c, node, use)) {
          progress = true;
          if (con == nullptr) {
            con = makecon(t);
            _phase->set_ctrl(con, C->root());
          }
          rehash_node_delayed(use);
          int nb = use->replace_edge(node, con, this);
          _worklist.push(use);
          --i, imax -= nb;
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
            _phase->C->set_major_progress();
          }
        }
      }
      while (_wq2.size() != 0) {
        _wq2.pop();
      }
      return progress;
    }
  }
  return false;
}

// We don't want to constant fold only the iv incr if the cmp doesn't constant fold as well
bool PhaseConditionalPropagation::is_safe_for_replacement_at_phi(Node* node, Node* use, Node* r, uint j) const {
  if (!(r->is_BaseCountedLoop() &&
        j == LoopNode::LoopBackControl &&
        use == r->as_BaseCountedLoop()->phi() &&
        node == r->as_BaseCountedLoop()->incr())) {
    return false;
  }
  const Type* cmp_type = find_type_between(r->as_BaseCountedLoop()->loopexit()->cmp_node(),
                                           r->as_BaseCountedLoop()->loopexit(), r);
  return cmp_type != nullptr && cmp_type->singleton();
}

bool PhaseConditionalPropagation::transform_helper(Node* c) {
  bool progress = false;
  {
    TypeUpdate* updates = updates_at(c);
    if (updates != nullptr && updates->control() == c) {
      for (int i = 0; i < updates->length(); ++i) {
        Node* node = updates->node_at(i);
        const Type* t = updates->type_at(i);
        if (transform_when_top_seen(c, node, t)) {
          progress = true;
        }
      }
    }
  }

  {
    TypeUpdate* updates = updates_at(c);
    if (updates != nullptr && updates->control() == c) {
      for (int i = 0; i < updates->length(); ++i) {
        Node* node = updates->node_at(i);
        const Type* t = updates->type_at(i);
        const Type* prev_t = updates->prev_type_at(i);
        if (transform_when_constant_seen(c, node, t, prev_t)) {
          progress = true;
        }
      }
    }
  }

  if (c->is_IfProj()) {
    IfNode* iff = c->in(0)->as_If();
    if (!iff->in(0)->is_top()) {
      const TypeInt* bol_t = iff->in(1)->bottom_type()->is_int();
      if (bol_t->is_con()) {
        if (iff->proj_out(bol_t->get_con()) == c) {
          _wq.push(c);
          assert(!(updates_at(c) != nullptr && updates_at(c)->type_if_present(c) == Type::TOP), "");
        }
      } else {
        _wq.push(c);
      }
    }
  } else {
    _wq.push(c);
  }

  return progress;
}

const Type* PhaseConditionalPropagation::type(const Node* n, Node* c) const {
  if (_current_ctrl == C->root()) {
    return PhaseValues::type(n);
  }
  assert(c->is_CFG(), "");
  const Type* res = nullptr;
  assert(_current_ctrl->is_Region() && _current_ctrl->find_edge(c) != -1, "");
  Node* dom = _phase->idom(_current_ctrl);
  TypeUpdate* updates = updates_at(c);
  TypeUpdate* dom_updates = updates_at(dom);
  assert(updates != nullptr || dom_updates == nullptr || _phase->is_dominator(_current_ctrl, c) || C->has_irreducible_loop(), "");
  while (updates != nullptr && updates->below(dom_updates, _phase)) {
    int idx = updates->find(n);
    if (idx != -1) {
      res = updates->type_at(idx);
      break;
    }
    updates = updates->prev();
    assert(updates != nullptr || dom_updates == nullptr || _phase->is_dominator(_current_ctrl, c) || C->has_irreducible_loop(), "");
  }
  if (res == nullptr) {
    res = PhaseValues::type(n);
  }
  return res;
}

PhaseConditionalPropagation::PhaseConditionalPropagation(PhaseIdealLoop* phase, VectorSet& visited, Node_Stack& nstack,
                                                         Node_List& rpo_list)
        : PhaseIterGVN(&phase->igvn()),
          _updates(nullptr),
          _phase(phase),
          _visited(visited),
          _rpo_list(rpo_list),
          _current_ctrl(phase->C->root()),
          _progress(true),
          _iterations(0),
          _current_updates(nullptr),
          _dom_updates(nullptr),
          _prev_updates(nullptr) {
  assert(nstack.is_empty(), "");
  assert(_rpo_list.size() == 0, "");
  phase->rpo(C->root(), nstack, _visited, _rpo_list);
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
  Node* root = _rpo_list.pop();
  assert(root == C->root(), "");
  _updates = new Updates(8, _rpo_list.size());
}

void PhaseIdealLoop::conditional_elimination(VectorSet& visited, Node_Stack& nstack, Node_List& rpo_list, int rounds) {
  TraceTime tt("loop conditional propagation", UseNewCode);
  PhaseConditionalPropagation pcp(this, visited, nstack, rpo_list);
  pcp.analyze(rounds);
  pcp.do_transform();
  _igvn.reset_from_igvn(&pcp);
}
