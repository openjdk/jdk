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

#include "memory/resourceArea.hpp"
#include "node.hpp"
#include "precompiled.hpp"
#include "opto/loopConditionalPropagation.hpp"
#include "opto/callnode.hpp"
#include "opto/movenode.hpp"
#include "opto/opaquenode.hpp"


bool PhaseConditionalPropagation::valid_use(Node* u, Node* c) {
//    if (u->is_CFG()) {
//      return false;
//    }
//    assert(!UseNewCode3 || _phase->has_node(u) == _visited.test(u->_idx), "");
  if (!_phase->has_node(u)) {
    return false;
  }
  if (u->is_Phi()) {
    if (u->in(0) ==  c) {
      return true;
    }
    _control_dependent_node.set(u->in(0)->_idx);
    _control_dependent_node.set(u->_idx);
    return false;
  }
  Node* u_c = _phase->ctrl_or_self(u);
  if (!_phase->is_dominator(c, u_c) && (u->is_CFG() || !_phase->is_dominator(u_c, c))) {
    return false;
  }
  if (!u->is_CFG() && u->in(0) != nullptr && u->in(0)->is_CFG() && !_phase->is_dominator(u->in(0), c)) {
    _control_dependent_node.set(u->in(0)->_idx);
    _control_dependent_node.set(u->_idx);
    return false;
  }
  return true;
}

void PhaseConditionalPropagation::enqueue_uses(const Node* n, Node* c) {
//    assert(!UseNewCode3 || _visited.test(n->_idx) == _phase->has_node(const_cast<Node*>(n)), "");
  assert(_phase->has_node(const_cast<Node*>(n)), "");
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* u = n->fast_out(i);
    if (valid_use(u, c)) {
      _wq.push(u);
      if (u->Opcode() == Op_AddI || u->Opcode() == Op_SubI) {
        for (DUIterator_Fast i2max, i2 = u->fast_outs(i2max); i2 < i2max; i2++) {
          Node* uu = u->fast_out(i2);
          if (uu->Opcode() == Op_CmpU && valid_use(uu, c)) {
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
        if (valid_use(cmp, c)) {
          _wq.push(cmp);
        }
      }
      if (u->is_Opaque1() && u->as_Opaque1()->original_loop_limit() == n) {
        for (DUIterator_Fast i2max, i2 = u->fast_outs(i2max); i2 < i2max; i2++) {
          Node* uu = u->fast_out(i2);
          if (uu->Opcode() == Op_CmpI || uu->Opcode() == Op_CmpL) {
            Node* phi = uu->as_Cmp()->countedloop_phi(u);
            if (phi != nullptr && valid_use(phi, c)) {
              _wq.push(phi);
            }
          }
        }
      }
      if (u->Opcode() == Op_CmpI || u->Opcode() == Op_CmpL) {
        Node* phi = u->as_Cmp()->countedloop_phi(n);
        if (phi != nullptr && valid_use(phi, c)) {
          _wq.push(phi);
        }
      }

      if (u->is_Region()) {
        for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
          Node* uu = u->fast_out(j);
          if (uu->is_Phi() && valid_use(uu, c)) {
            _wq.push(uu);
          }
        }
      }

//      if (u->is_Cmp()) {
//        for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
//          Node* u2 = u->fast_out(j);
//          if (u2->is_Bool()) {
//            for (DUIterator_Fast kmax, k = u2->fast_outs(kmax); k < kmax; k++) {
//              Node* u3 = u2->fast_out(k);
//              if (u3->is_CMove()) {
//                _wq.push(u3);
//              }
//            }
//          }
//        }
//      }
    }
  }
}

void PhaseConditionalPropagation::sync(Node* c) {
  Node* lca = _phase->dom_lca_internal(_current_ctrl, c);
  TypeUpdate* lca_updates = updates_at(lca);
  {
    TypeUpdate* updates = updates_at(_current_ctrl);
    while (updates != lca_updates) {
      assert(updates != nullptr, "");
      assert(lca_updates == nullptr || !_phase->is_dominator(updates->control(), lca_updates->control()), "");

      for (int i = 0; i < updates->length(); ++i) {
        Node* n = updates->node_at(i);
        const Type* t = updates->prev_type_at(i);
        PhaseTransform::set_type(n, t);
      }
      updates = updates->prev();
    }
  }
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
        PhaseTransform::set_type(n, t);
      }
    }
  }
  _current_ctrl = c;
}

void PhaseConditionalPropagation::analyze(int rounds) {
  for (int i = 0; i < C->cmove_count(); ++i) {
    CMoveNode* cmove = C->cmove_node(i);
    const Type* t = cmove->Value(this);
    if (t != PhaseTransform::type(cmove)) {
      PhaseTransform::set_type(cmove, t);
      enqueue_uses(cmove, C->root());
    }
  }

  while (_wq.size() > 0) {
    Node* n = _wq.pop();
    _value_calls++;
    _value_calls_graph.at_put(n->Opcode(), _value_calls_graph.at_grow(n->Opcode(), 0) + 1);
    const Type* t = n->Value(this);
    const Type* current_type = PhaseTransform::type(n);
    t = t->filter(current_type);
    if (t != current_type) {
#ifdef ASSERT
      assert(narrows_type(current_type, t), "");
#endif
      set_type(n, t, current_type);
      enqueue_uses(n, C->root());
    }
  }


  bool progress = true;
  int iterations = 0;
  int extra_rounds = 0;
  int extra_rounds2 = 0;
  bool has_infinite_loop = false;
  while (progress || _progress) {
    iterations++;
    assert(iterations - extra_rounds - extra_rounds2 >= 0, "");
    assert(iterations - extra_rounds2 <= 2 || _phase->ltree_root()->_child != nullptr || has_infinite_loop, "");
    assert(iterations - extra_rounds - extra_rounds2 <= 3 || _phase->_has_irreducible_loops, "");
    assert(iterations < 100, "");

    progress = false;
    bool extra = false;
    bool extra2 = false;
    _progress = false;

//      _control_dependent_node.clear();

    for (int i = _rpo_list.size() - 1; i >= 0; i--) {
      int rpo = _rpo_list.size() - 1 - i;
      Node* c = _rpo_list.at(i);
      has_infinite_loop = has_infinite_loop || (c->in(0)->Opcode() == Op_NeverBranch);

      one_iteration(iterations, rpo, c, progress, has_infinite_loop, extra, extra2);
    }
    if (extra) {
      extra_rounds++;
    }
    if (extra2) {
      extra_rounds2++;
    }
    rounds--;
    if (rounds <= 0) {
      break;
    }
  }

  if (false) {
    int live_nodes = 0;
    tty->print_cr("XXXXXXXXXXXXXXXX");
    ResourceMark rm;
    Unique_Node_List wq;
    wq.push(C->root());
    for (uint i = 0; i < wq.size(); ++i) {
      Node* n = wq.at(i);
      for (uint j = 0; j < n->req(); ++j) {
        Node* in = n->in(j);
        if (in != nullptr) {
          wq.push(in);
        }
      }
    }
    live_nodes = wq.size();
    for (int i = 0; i < _value_calls_graph.length(); ++i) {
      int cnt = _value_calls_graph.at(i);
      if (cnt == 0) {
        continue;
      }
      extern const char *NodeClassNames[];
      tty->print_cr("XXX %s : %d", NodeClassNames[i], cnt);
    }
    tty->print_cr("XXX value calls %d %d %d %d", _value_calls, iterations, _rpo_list.size(), live_nodes);
    _value_calls_graph.trunc_to(0);
  }

  sync(C->root());
}

void
PhaseConditionalPropagation::one_iteration(int iterations, int rpo, Node* c, bool& progress, bool has_infinite_loop,
                                           bool& extra, bool& extra2) {
  _known_updates.set(c->_idx);

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
    assert(iterations > 1, "");
    if (_current_updates == _dom_updates) {
//              _updates->put(c, _current_updates);
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
        const Type* dom_t = PhaseTransform::type(n);
        const Type* t = _current_updates->type_at(j);
        const Type* new_t = t->filter(dom_t);
/*          BasicType bt = T_ILLEGAL;
          if (new_t->isa_int()) {
            bt = T_INT;
          } else if (new_t->isa_long()) {
            bt = T_LONG;
          }
          if (new_t != t &&
              bt != T_ILLEGAL &&
              new_t->is_integer(bt)->lo_as_long() == dom_t->is_integer(bt)->lo_as_long() &&
              new_t->is_integer(bt)->hi_as_long() == dom_t->is_integer(bt)->hi_as_long()) {
            _current_updates->remove_at(j);
          } else */
        if (new_t == dom_t) {
          _current_updates->remove_at(j);
        } else {
          _current_updates->set_prev_type_at(j, dom_t);
          if (new_t != t) {
            _current_updates->set_type_at(j, new_t);
            enqueue_uses(n, c);
          }
//            _wq.push(n);
          j++;
        }
      }
      assert(_dom_updates == nullptr || !_phase->is_dominator(_current_updates->control(), _dom_updates->control()), "");
      _current_updates->set_prev(_dom_updates);
    }
  }

  if (c->is_Region()) {
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
//      tty->print_cr("X %d %d", c->_idx, in_idx);
    Node* in = c->in(in_idx);
    Node* ctrl = in;
    TypeUpdate* updates = updates_at(ctrl);
    assert(updates != nullptr || _dom_updates == nullptr || _phase->is_dominator(c, in) || C->has_irreducible_loop(), "");
    while(updates != nullptr && updates->below(_dom_updates, _phase)) {
      for (int j = 0; j < updates->length(); ++j) {
        Node* n = updates->node_at(j);
//          tty->print_cr("XXX %d %d %d %d", iterations, c->_idx, n->_idx, c->req());
        const Type* t = find_type_between(n, in, dom);
//          tty->print_cr("XXXX %d %d %d %d", iterations, c->_idx, n->_idx, c->req());
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
//          tty->print_cr("XXXXX %d %d %d %d", iterations, c->_idx, n->_idx, c->req());
        if (k == c->req()) {
//            tty->print_cr("XXXXXX %d %d %d %d", iterations, c->_idx, n->_idx, c->req());
          const Type* prev_t = t;
          const Type* current_type = find_prev_type_between(n, in, dom);
          if (iterations > 1) {
            const Type* prev_round_t = nullptr;
            if (_prev_updates != nullptr && _prev_updates->control() == c) {
              prev_round_t = _prev_updates->type_if_present(n);
            }
            if (prev_round_t != nullptr) {
              t = t->filter(prev_round_t);
              assert(t == prev_t, "");
              t = saturate(t, prev_round_t, nullptr);
              if (c->is_Loop() && t != prev_round_t) {
                extra = true;
              }
            }
          }
          t = t->filter(current_type);

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
    AllocateArrayNode* alloc = c->in(0)->in(0)->in(0)->as_AllocateArray();
    sync(dom);
    analyze_allocate_array(rpo, c, alloc);
  }
  const bool always = false;
  if (_control_dependent_node.test(c->_idx) || always) {
    for (DUIterator_Fast imax, i = c->fast_outs(imax); i < imax; i++) {
      Node* u = c->fast_out(i);
      if (!u->is_CFG() && u->in(0) == c && u->Opcode() != Op_CheckCastPP && _phase->has_node(u) && (always || _control_dependent_node.test(u->_idx))) {
        _wq.push(u);
      }
    }
  }

  sync(c);
  while (_wq.size() > 0) {
    Node* n = _wq.pop();
//      tty->print("[%d]", c->_idx); n->dump();
    _value_calls++;
    _value_calls_graph.at_put(n->Opcode(), _value_calls_graph.at_grow(n->Opcode(), 0) + 1);
    const Type* t = n->Value(this);
    const Type* current_type = PhaseTransform::type(n);
    if (n->is_Phi() && iterations > 1) {
      const Type* prev_type = nullptr;
      if (_prev_updates != nullptr) {
        prev_type = _prev_updates->type_if_present(n);
      }
      if (prev_type != nullptr) {
        const Type* prev_t = t;
        t = t->filter(prev_type);
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
    t = t->filter(current_type);
    if (t != current_type) {
#ifdef ASSERT
      assert(narrows_type(current_type, t), "");
#endif
      set_type(n, t, current_type);
      enqueue_uses(n, c);
    }
  }

  if (_prev_updates == nullptr) {
    if (_current_updates != nullptr && _current_updates->length() > 0 && _current_updates->control() == c) {
      _progress = true;
#ifdef ASSERT
      sync(dom);
      for (int i = 0; i < _current_updates->length(); ++i) {
        Node* n = _current_updates->node_at(i);
        assert(_current_updates->prev_type_at(i) == PhaseTransform::type(n), "");
        assert(narrows_type(PhaseTransform::type(n), _current_updates->type_at(i)), "");
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
      assert(_current_updates->prev_type_at(i) == PhaseTransform::type(n), "");
      const Type* current_t = _current_updates->type_at(i);
      assert(narrows_type(PhaseTransform::type(n), current_t), "");
      for (; j < _prev_updates->length() && _prev_updates->node_at(j)->_idx < n->_idx; j++) {
        assert(narrows_type(_prev_updates->type_at(j), PhaseTransform::type(_prev_updates->node_at(j))), "");
      }
      if (j < _prev_updates->length() && _prev_updates->node_at(j) == n) {
        const Type* prev_t = _prev_updates->type_at(j);
        assert(narrows_type(prev_t, current_t), "");
        if (prev_t != current_t) {
#if 0 //def ASSERT
          BasicType bt = T_ILLEGAL;
            if (current_t->isa_int()) {
              bt = T_INT;
            } else if (current_t->isa_long()) {
              bt = T_LONG;
            }
            assert(bt == T_ILLEGAL ||
                   current_t->is_integer(bt)->lo_as_long() > prev_t->is_integer(bt)->lo_as_long() ||
                   current_t->is_integer(bt)->hi_as_long() < prev_t->is_integer(bt)->hi_as_long(), "");
#endif
          _progress = true;
        }
        j++;
      } else {
        assert(_prev_updates->find(n) == -1, "");
      }
    }
    for (; j < _prev_updates->length(); j++) {
      assert(narrows_type(_prev_updates->type_at(j), PhaseTransform::type(_prev_updates->node_at(j))), "");
    }
  }
#ifdef ASSERT
  if (_current_updates != nullptr && _current_updates->control() == c) {
    sync(C->root());
    int last_expected = (_phase->ltree_root()->_child != nullptr || has_infinite_loop) ? 3 : 2;
    if (iterations == last_expected) {
      for (int i = 0; i < _current_updates->length(); ++i) {
        Node* n = _current_updates->node_at(i);
        if (PhaseTransform::type(n) != n->Value(&_phase->igvn()) &&
            _current_updates->prev_type_at(i) == PhaseTransform::type(n) &&
            _current_updates->type_at(i) == n->Value(&_phase->igvn())) {
          extra2 = true;
        }
      }
    }
  }
#endif
}

void PhaseConditionalPropagation::analyze_allocate_array(int rpo, Node* c, const AllocateArrayNode* alloc) {
  Node* length = alloc->in(AllocateArrayNode::ALength);
  Node* klass = alloc->in(AllocateNode::KlassNode);
  const Type* klass_t = PhaseTransform::type(klass);
  if (klass_t != Type::TOP) {
    const TypeOopPtr* ary_type = klass_t->is_klassptr()->as_instance_type();
    const TypeInt* length_type = PhaseTransform::type(length)->isa_int();
    if (ary_type->isa_aryptr() && length_type != nullptr) {
      const Type* narrow_length_type = ary_type->is_aryptr()->narrow_size_type(length_type);
      narrow_length_type = narrow_length_type->filter(length_type);
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
//      const Type* n_t = type_if_present(c, n);
//      if (n_t == nullptr) {
//        n_t = PhaseTransform::type(n);
//      }
    const Type* n_t = PhaseTransform::type(n);
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
//        const Type* in_t;
//        in_t = type_if_present(c, in);
//        if (in_t == nullptr) {
//          in_t = PhaseTransform::type(in);
//        }
      const Type* in_t = PhaseTransform::type(in);

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

//    if (new_int->hi_as_long() == old_int->hi_as_long() &&
//        new_int->lo_as_long() == old_int->lo_as_long() &&
//        new_int->widen_limit() < old_int->widen_limit()) {
//      return false;
//    }

  return true;
}

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
//      for (DUIterator_Fast imax, i = c->fast_outs(imax); i < imax; i++) {
//        Node* u = c->fast_out(i);
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
//        assert(!UseNewCode3 || _visited.test(u->_idx) == _phase->has_node(u), "");
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
//        Unique_Node_List uses;
      for (DUIterator_Fast imax, i = node->fast_outs(imax); i < imax; i++) {
        Node* use = node->fast_out(i);
        if (_wq2.member(use)) {
          continue;
        }
        _wq2.push(use);
//          uses.push(use);
//        }
//        for (uint j = 0; j < uses.size(); ++j) {
//          Node* use = uses.at(j);
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
    res = PhaseTransform::type(n);
  }
  return res;
}

const Type* PhaseConditionalPropagation::cmove_value(const CMoveNode* cmove) {
  const Type* res = nullptr;
  Node* bol = cmove->in(1);
  if (bol->is_Bool()) {
    Node* cmp = bol->in(1);
    if (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpU ||
        cmp->Opcode() == Op_CmpL || cmp->Opcode() == Op_CmpUL) {
//      cmove->dump();
//      tty->print("XXX before "); PhaseTransform::type(cmove->in(CMoveNode::IfTrue))->dump(); tty->cr();
//      tty->print("XXX before "); PhaseTransform::type(cmove->in(CMoveNode::IfFalse))->dump(); tty->cr();

      Node* c = _phase->get_ctrl(cmove);
      ResourceMark rm;
      Unique_Node_List wq;

      const Type* t1 = analyze_cmove(cmove, bol, cmp, c, wq, true);
      const Type* t2 = analyze_cmove(cmove, bol, cmp, c, wq, false);
      res = t1->meet_speculative(t2);
//      tty->print("XXX res "); res->dump(); tty->cr();
    }
  }
  return res;
}

const Type* PhaseConditionalPropagation::analyze_cmove(const CMoveNode* cmove, const Node* bol, const Node* cmp, Node* c,
                                                       Unique_Node_List& wq, bool taken) {
#ifdef ASSERT
  Type_Array types_clone(Thread::current()->resource_area());
  for (uint i = 0; i < PhaseTransform::_types.Size(); ++i) {
    types_clone.map(i, PhaseTransform::_types.fast_lookup(i));
  }
#endif
  TypeUpdate updates(_current_updates, c);
  _current_updates = &updates;
  Node* cmp1 = cmp->in(1);
  Node* cmp2 = cmp->in(2);
  BasicType bt = (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpU) ? T_INT : T_LONG;
  const Type* t1 = bol->as_Bool()->filtered_int_type(this, cmp1, bt, taken);
  if (t1 != nullptr) {
    const Type* cmp1_t = PhaseTransform::type(cmp1);
    t1 = cmp1_t->filter(t1);
    assert(narrows_type(cmp1_t, t1), "");
    if (t1 != cmp1_t) {
      set_type(cmp1, t1, cmp1_t);
//      t1->dump();
//      tty->cr();
      cmove_enqueue_uses(c, wq, cmp1, cmove);
    }
  }
  const Type* t2 = bol->as_Bool()->filtered_int_type(this, cmp2, bt, taken);
  if (t2 != nullptr) {
    const Type* cmp2_t = PhaseTransform::type(cmp2);
    t2 = cmp2_t->filter(t2);
    assert(narrows_type(cmp2_t, t2), "");
    if (t2 != cmp2_t) {
      set_type(cmp2, t2, cmp2_t);
//      t2->dump();
//      tty->cr();
      cmove_enqueue_uses(c, wq, cmp2, cmove);
    }
  }
//            if (t1 != nullptr || t2 != nullptr) {
//
//            }

  while (wq.size() > 0) {
    Node* n = wq.pop();
    const Type* t = n->Value(this);
    const Type* prev_t = PhaseTransform::type(n);
    t = prev_t->filter(t);
    assert(this->narrows_type(prev_t, t), "");
    if (t != prev_t) {
      set_type(n, t, prev_t);
      cmove_enqueue_uses(c, wq, n, cmove);
    }
  }

  const Type* res = PhaseTransform::type(cmove->in(taken ? CMoveNode::IfTrue : CMoveNode::IfFalse));
//  tty->print("XXX after (%s)", taken ? "taken " : "not taken ");
//  res->dump();
//  tty->cr();

  for (int i = 0; i < updates.length(); ++i) {
    Node* n = updates.node_at(i);
    const Type* t = updates.prev_type_at(i);
    PhaseTransform::set_type(n, t);
  }
#ifdef ASSERT
   for (uint i = 0; i < PhaseTransform::_types.Size(); ++i) {
     assert(PhaseTransform::_types.fast_lookup(i) == types_clone.fast_lookup(i), "");
   }
#endif
  this->_current_updates = this->_current_updates->prev();
  return res;
}

void
PhaseConditionalPropagation::cmove_enqueue_uses(Node* c, Unique_Node_List& wq, const Node* n, const CMoveNode* cmove) {
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* u = n->fast_out(i);
    if (u != cmove && !u->is_CFG() && u->in(0) == nullptr && _phase->is_dominator(_phase->get_ctrl(u), c)) {
      wq.push(u);
    }
  }
}

void PhaseIdealLoop::conditional_elimination(VectorSet& visited, Node_Stack& nstack, Node_List& rpo_list, int rounds) {
  TraceTime tt("loop conditional propagation", UseNewCode);
  C->print_method(PHASE_DEBUG, 2);
  PhaseConditionalPropagation pcp(this, visited, nstack, rpo_list);
  {
    TraceTime tt("loop conditional propagation analyze", UseNewCode);
    pcp.analyze(rounds);
  }
  {
    TraceTime tt("loop conditional propagation transform", UseNewCode);
    pcp.do_transform();
  }
  _igvn = pcp;
  C->print_method(PHASE_DEBUG, 2);
}

