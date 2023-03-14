/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
#include "opto/loopnode.hpp"
#include "opto/rootnode.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"

class PhaseConditionalPropagation : public PhaseIterGVN {
private:
  class TreeNode : public ResourceObj {
  private:
    Node* _node;
    const Type* _type;
    TreeNode* _left;
    TreeNode* _right;
    int _rpo;

  public:
    TreeNode(Node* n, const Type* type)
            : _node(n), _type(type), _left(nullptr), _right(nullptr), _rpo(0) {
    }

    TreeNode(Node* n, const Type* type, int rpo, TreeNode* left, TreeNode* right)
            : _node(n), _type(type), _left(left), _right(right), _rpo(rpo) {
    }

    TreeNode()
            : _node(nullptr), _type(nullptr), _left(nullptr), _right(nullptr), _rpo(0) {
    }

    const Node* node() const { return _node; };

    const node_idx_t idx() const { return _node->_idx; }

    void set_left(TreeNode* left) {
      _left = left;
    }

    void set_right(TreeNode* right) {
      _right = right;
    }

    const TreeNode* left() const {
      return _left;
    }

    const TreeNode* right() const {
      return _right;
    }

    const Type* type() const {
      return _type;
    }

    const TreeNode* find(const Node* node) const {
      const TreeNode* tn = this;
      node_idx_t idx = node->_idx;
      do {
        if (tn->_node == node) {
          return tn;
        } else {
          if (idx < tn->idx()) {
            tn = tn->_left;
          } else if (idx > tn->idx()) {
            tn = tn->_right;
          }
        }
      } while (tn != nullptr);
      return nullptr;
    }

    TreeNode* set_type(Node* n, const Type* t, int rpo) {
      assert(_rpo <= rpo, "");
      if (_node == n) {
        if (_rpo < rpo) {
          return new TreeNode(_node, t, rpo, _left, _right);
        } else {
          _type = t;
          return this;
        }
      } else if (n->_idx < idx()) {
        assert(_left != nullptr, "");
        TreeNode* tn = _left->set_type(n, t, rpo);
        if (_rpo == rpo) {
          _left = tn;
          return this;
        } else {
          assert(tn != _left, "");
          return new TreeNode(_node, _type, rpo, tn, _right);
        }
      } else if (n->_idx > idx()) {
        assert(_right != nullptr, "");
        TreeNode* tn = _right->set_type(n, t, rpo);
        if (_rpo == rpo) {
          _right = tn;
          return this;
        } else {
          assert(tn != _right, "");
          return new TreeNode(_node, _type, rpo, _left, tn);
        }
      }
      ShouldNotReachHere();
      return nullptr;
    }

    class Iterator : public StackObj {
    private:
      TreeNode* _current1;
      TreeNode* _current2;
      GrowableArray<TreeNode*> _stack1;
      GrowableArray<TreeNode*> _stack2;
    public:
      Iterator(TreeNode* root1, TreeNode* root2) :
              _current1(nullptr), _current2(nullptr) {
        _stack1.push(root1);
        _stack2.push(root2);
      }

      bool next() {
        _current1 = _current2 = nullptr;
        assert(_stack1.length() == _stack2.length(), "");
        while (_stack1.is_nonempty()) {
          TreeNode* tn1 = _stack1.pop();
          TreeNode* tn2 = _stack2.pop();
          assert(tn1->_node = tn2->_node, "");
          assert((tn1->_left != nullptr) == (tn2->_left != nullptr), "");
          assert((tn1->_right != nullptr) == (tn2->_right != nullptr), "");
          if (tn1 == tn2) {
            continue;
          }

          if (tn1->_left != nullptr) {
            _stack1.push(tn1->_left);
            _stack2.push(tn2->_left);
          }
          if (tn1->_right != nullptr) {
            _stack1.push(tn1->_right);
            _stack2.push(tn2->_right);
          }

          if (tn1->_type != tn2->_type) {
            _current1 = tn1;
            _current2 = tn2;
            return true;
          }
        }
        return false;
      }

      const Type* type1() const {
        return _current1->_type;
      }

      const Type* type2() const {
        return _current2->_type;
      }

      Node* node() const {
        return _current1->_node;
      }
    };

    const Type* get_type(const Node* n) {
      const TreeNode* tn = find(n);
      assert(tn != nullptr, "");
      return tn->_type;
    }
  };

  struct interval {
    int beg;
    int end;
  };

  void build_types_tree(VectorSet &visited) {
    GrowableArray<TreeNode> nodes;
    Compile* C = _phase->C;
    nodes.push(TreeNode(C->root(), PhaseTransform::type(C->root())));
    visited.set(C->root()->_idx);
    for (int i = 0; i < nodes.length(); i++) {
      TreeNode tn = nodes.at(i);
      for (uint j = 0; j < tn.node()->req(); j++) {
        Node* in = tn.node()->in(j);
        if (in != nullptr && !visited.test_set(in->_idx)) {
          nodes.push(TreeNode(in, PhaseTransform::type(in)));
        }
      }
    }
    nodes.sort([](TreeNode* tn1, TreeNode* tn2) { return tn1->idx() < tn2->idx() ? -1 : (tn1->idx() > tn2->idx() ? 1 : 0); });
    int length = nodes.length();
#ifdef ASSERT
    for (int i = 1; i < length; i++) {
      assert(nodes.at(i).idx() > nodes.at(i-1).idx(), "");
    }
#endif
    GrowableArray<interval> stack;
    stack.push({0, length - 1});
    int root = (length - 1) / 2;
    do {
      interval i = stack.pop();
      int current = (i.end - i.beg) / 2 + i.beg;
      TreeNode& current_node = nodes.at(current);
      int left = (current - 1 - i.beg) / 2 + i.beg;
      if (left != current) {
        current_node.set_left(nodes.adr_at(left));
      }
      if (current - i.beg > 1) {
        stack.push({i.beg, current - 1});
      }
      int right = (i.end - (current + 1)) / 2 + current + 1;
      if (right != current) {
        current_node.set_right(nodes.adr_at(right));
      }
      if (i.end - current > 1) {
        stack.push({current + 1, i.end});
      }
    } while (stack.is_nonempty());
    TreeNode* tree_root = nodes.adr_at(root);
    _types_at_ctrl.Insert(C->root(), tree_root);
  }

  bool valid_use(Node* u, Node* c) const {
    if (u->is_Phi() || !_visited.test(u->_idx)) {
      return false;
    }
    Node* u_c = _phase->ctrl_or_self(u);
    if (!_phase->is_dominator(c, u_c) && (u->is_CFG() || !_phase->is_dominator(u_c, c))) {
      return false;
    }
    if (!u->is_CFG() && u->in(0) != nullptr && u->in(0)->is_CFG() && !_phase->is_dominator(u->in(0), c)) {
      return false;
    }
    return true;
  }

  void enqueue_uses(const Node* n, Node* c) {
    assert(_visited.test(n->_idx), "");
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
          _wq.push(cmp);
        }
      }
    }
  }

  void set_type(Node* n, const Type* t, int rpo) {
    PhaseTransform::set_type(n, t);
    _current_types = _current_types->set_type(n, t, rpo);
  }

  void sync_from_tree(Node* c) {
    _current_types = (TreeNode*) _types_at_ctrl[c];
    assert(_current_types != nullptr, "");
    TreeNode::Iterator iter((TreeNode*)_types_at_ctrl[_current_ctrl], (TreeNode*) _current_types);
    while (iter.next()) {
      Node* node = iter.node();
      const Type* t = iter.type2();
      PhaseTransform::set_type(node, t);
    }
    _current_ctrl = c;
  }

  PhaseIdealLoop* _phase;
  VectorSet _visited;
  Dict _types_at_ctrl;
  Node_List _rpo_list;
  TreeNode* _current_types;
  Node* _current_ctrl;
#ifdef ASSERT
  VectorSet _conditions;
#endif
  Unique_Node_List _wq;


public:
  PhaseConditionalPropagation(PhaseIdealLoop* phase, VectorSet &visited, Node_Stack &nstack, Node_List &rpo_list)
  : PhaseIterGVN(&phase->igvn()),
    _phase(phase),
    _visited(visited),
    _types_at_ctrl(cmpkey, hashptr),
    _rpo_list(rpo_list),
    _current_types(nullptr),
    _current_ctrl(phase->C->root()) {
    assert(nstack.is_empty(), "");
    assert(_rpo_list.size() == 0, "");
    phase->rpo(C->root(), nstack, _visited, _rpo_list);
    Node* root = _rpo_list.pop();
    assert(root == C->root(), "");

    _visited.clear();
    build_types_tree(_visited);
  }

  void analyze() {
    bool progress = true;
    int iterations = 0;
    int extra_rounds = 0;
    int extra_rounds2 = 0;
    bool has_infinite_loop = false;
    while (progress) {
      iterations++;
      assert(iterations - extra_rounds - extra_rounds2 >= 0, "");
      assert(iterations - extra_rounds2 <= 2 || _phase->ltree_root()->_child != nullptr || has_infinite_loop, "");
      assert(iterations - extra_rounds - extra_rounds2 <= 3 || _phase->_has_irreducible_loops, "");
      assert(iterations < 100, "");

      progress = false;
      bool extra = false;
      bool extra2 = false;
      for (int i = _rpo_list.size() - 1; i >= 0; i--) {
        int rpo = _rpo_list.size() - 1 - i;
        Node* c = _rpo_list.at(i);

        has_infinite_loop = has_infinite_loop || (c->Opcode() == Op_NeverBranch);

        Node* dom = _phase->idom(c);

        TreeNode* types_at_dom = (TreeNode*) (_types_at_ctrl[dom]);
        TreeNode* prev_types_at_c = (TreeNode*) (_types_at_ctrl[c]);
        TreeNode* types_at_c = types_at_dom;
        if (c->is_Region()) {
          Node* in = c->in(1);
          TreeNode* types_at_in1 = (TreeNode*) (_types_at_ctrl[in]);
          if (types_at_in1 != nullptr) {
            TreeNode::Iterator iter(types_at_dom, types_at_in1);
            while (iter.next()) {
              Node* node = iter.node();
              uint j = 2;
              const Type* t = iter.type2();
              const Type* current_type = types_at_dom->get_type(node);
              for (; j < c->req(); j++) {
                in = c->in(j);
                TreeNode* types_at_in = (TreeNode*) (_types_at_ctrl[in]);
                if (types_at_in == nullptr) {
                  assert(!c->is_Loop() && (_phase->get_loop(c)->_irreducible || _phase->is_dominator(c, in)), "");
                  break;
                }
                const Type* type_at_in = types_at_in->get_type(node);
                if (type_at_in == current_type) {
                  break;
                }
                t = t->meet_speculative(type_at_in);
              }
              if (j == c->req()) {
                if (prev_types_at_c != nullptr) {
                  const Type* prev_t = t;
                  t = t->filter(prev_types_at_c->get_type(node));
                  assert(t == prev_t, "");
                  t = saturate(t, prev_types_at_c->get_type(node), nullptr);
                  if (c->is_Loop() && t != prev_types_at_c->get_type(node)) {
                    extra = true;
                  }
                  t = t->filter(current_type);
                }

                if (t != current_type) {
                  if (types_at_c->get_type(node) != t) {
#ifdef ASSERT
                    assert(narrows_type(types_at_c->get_type(node), t), "");
#endif
                    types_at_c = types_at_c->set_type(node, t, rpo);
                    enqueue_uses(node, c);
                  }
                }
              }
            }
          } else {
            assert(!c->is_Loop() && (_phase->get_loop(c)->_irreducible || _phase->is_dominator(c, in)), "");
          }
        } else if (c->is_IfProj()) {
          Node* iff = c->in(0);
          assert(iff->is_If(), "");
          if (/*iff->as_If()->safe_for_optimizations() &&*/
              !(iff->is_CountedLoopEnd() && iff->as_CountedLoopEnd()->loopnode() != nullptr && iff->as_CountedLoopEnd()->loopnode()->is_strip_mined())) {
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
                sync_from_tree(iff);
                // narrowing the type of a LoadRange could cause a range check to optimize out and a Load to be hoisted above
                // checks that guarantee its within bounds
                if (cmp1->Opcode() != Op_LoadRange) {
                  types_at_c = analyze_if(rpo, c, types_at_c, cmp, cmp1);
                }
                if (cmp2->Opcode() != Op_LoadRange) {
                  types_at_c = analyze_if(rpo, c, types_at_c, cmp, cmp2);
                }
              }
            }
          }
        } else if (c->is_CatchProj() && c->in(0)->in(0)->in(0)->is_AllocateArray() && c->as_CatchProj()->_con == CatchProjNode::fall_through_index) {
          AllocateArrayNode* alloc = c->in(0)->in(0)->in(0)->as_AllocateArray();
          Node* length = alloc->in(AllocateArrayNode::ALength);
          Node* klass = alloc->in(AllocateNode::KlassNode);
          const Type* klass_t = types_at_c->get_type(klass);
          if (klass_t != Type::TOP) {
            const TypeOopPtr* ary_type = types_at_c->get_type(klass)->is_klassptr()->as_instance_type();
            const TypeInt* length_type = types_at_c->get_type(length)->isa_int();
            if (ary_type->isa_aryptr() && length_type != nullptr) {
              const Type* narrow_length_type = ary_type->is_aryptr()->narrow_size_type(length_type);
              narrow_length_type = narrow_length_type->filter(length_type);
              assert(narrows_type(length_type, narrow_length_type), "");
              if (narrow_length_type != length_type) {
                types_at_c = types_at_c->set_type(length, narrow_length_type, rpo);
                enqueue_uses(length, c);
              }
            }
          }
        }
        for (DUIterator_Fast imax, i = c->fast_outs(imax); i < imax; i++) {
          Node* u = c->fast_out(i);
//          if (u->is_Phi() && _visited.test(u->_idx)) {
//            _wq.push(u);
//          }
          if (!u->is_CFG() && u->in(0) == c && u->Opcode() != Op_CheckCastPP && _visited.test(u->_idx)) {
            _wq.push(u);
          }
        }

        _types_at_ctrl.Insert(c, types_at_c);

        sync_from_tree(c);
        while (_wq.size() > 0) {
          Node* n = _wq.pop();
          const Type* t = n->Value(this);
          if (n->is_Phi() && prev_types_at_c != nullptr) {
            const Type* prev_t = t;
            t = t->filter(prev_types_at_c->get_type(n));
            assert(t == prev_t, "");
            if (!(n->in(0)->is_CountedLoop() && n->in(0)->as_CountedLoop()->phi() == n && n->in(0)->as_CountedLoop()->can_be_counted_loop(this))) {
              t = saturate(t, prev_types_at_c->get_type(n), nullptr);
            }
            if (c->is_Loop() && t != prev_types_at_c->get_type(n)) {
              extra = true;
            }
          }
          t = t->filter(PhaseTransform::type(n));
          if (t != PhaseTransform::type(n)) {
#ifdef ASSERT
            assert(narrows_type(PhaseTransform::type(n), t), "");
#endif
            set_type(n, t, rpo);
            enqueue_uses(n, c);
          }
        }
        if (types_at_c != _current_types) {
          _types_at_ctrl.Insert(c, _current_types);
          types_at_c = _current_types;
        }
        if (prev_types_at_c == nullptr && types_at_c != types_at_dom) {
          progress = true;
        } else if (prev_types_at_c != nullptr && TreeNode::Iterator(prev_types_at_c, types_at_c).next()) {
          progress = true;
#ifdef ASSERT
          sync_from_tree(C->root());
          TreeNode::Iterator iter(prev_types_at_c, types_at_c);
          int last_expected = (_phase->ltree_root()->_child != nullptr || has_infinite_loop) ? 3 : 2;
          if (iterations == last_expected) {
            while (iter.next() && !extra) {
              if (iter.node()->bottom_type()->make_oopptr() &&
                  PhaseTransform::type(iter.node()) != iter.node()->Value(this) &&
                  iter.type1() == PhaseTransform::type(iter.node()) &&
                  iter.type2() == iter.node()->Value(this)) {
                extra2 = true;
              }
            }
          }
#endif
        }
#ifdef ASSERT
        if (prev_types_at_c != nullptr || (types_at_c != types_at_dom)) {
          if (PrintLoopConditionalPropagation) {
            TreeNode::Iterator iter(types_at_dom, types_at_c);
            bool failure = false;
            while (iter.next()) {
              const Type* t1 = iter.type1();
              const Type* t2 = iter.type2();
              tty->print("@ iteration %d for node %d at control %d: ", iterations, iter.node()->_idx, c->_idx);
              tty->print(" ");
              t1->dump();
              tty->print(" - ");
              t2->dump();
              tty->cr();
            }
          }
          {
            TreeNode::Iterator iter(prev_types_at_c != nullptr ? prev_types_at_c : types_at_dom, types_at_c);
            bool failure = false;
            while (iter.next()) {
              const Type* t1 = iter.type1();
              const Type* t2 = iter.type2();
              if (!narrows_type(t1, t2)) {
                failure = true;
                if (PrintLoopConditionalPropagation) {
                  tty->print("XXX ");
                  tty->print("@ iteration %d for node %d at control %d: ", iterations, iter.node()->_idx, c->_idx);
                  tty->print(" ");
                  t1->dump();
                  tty->print(" - ");
                  t2->dump();
                  tty->cr();
                }
              }
            }
            assert(!failure, "");
          }
        }
#endif
      }
      if (extra) {
        extra_rounds++;
      }
      if (extra2) {
        extra_rounds2++;
      }
    }

    sync_from_tree(C->root());
  }

  TreeNode* analyze_if(int rpo, Node* c, TreeNode* types_at_c, const Node* cmp, Node* n) {
    const Type* t = IfNode::filtered_int_type(this, n, c, (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpU) ? T_INT : T_LONG);
    if (t != nullptr) {
      const Type* n_t = types_at_c->get_type(n);
      const Type* new_n_t = n_t->filter(t);
      assert(narrows_type(n_t, new_n_t), "");
      if (n_t != new_n_t) {
#ifdef ASSERT
        _conditions.set(c->_idx);
#endif
        types_at_c = types_at_c->set_type(n, new_n_t, rpo);
        enqueue_uses(n, c);
      }
      if (n->Opcode() == Op_ConvL2I) {
        Node* in = n->in(1);
        const Type* in_t = types_at_c->get_type(in);
        if (in_t->isa_long() && in_t->is_long()->_lo >= min_jint && in_t->is_long()->_hi <= max_jint) {
          const Type* t_as_long = t->isa_int() ? TypeLong::make(t->is_int()->_lo, t->is_int()->_hi, t->is_int()->_widen) : Type::TOP;
          const Type* new_in_t = in_t->filter(t_as_long);
          assert(narrows_type(in_t, new_in_t), "");
          if (in_t != new_in_t) {
#ifdef ASSERT
            _conditions.set(c->_idx);
#endif
//            tty->print_cr("XXXX");
//            in->dump();
//            c->dump();
//            in_t->dump(); tty->cr();
//            new_in_t->dump(); tty->cr();
//            tty->print_cr("XXXX");
            types_at_c = types_at_c->set_type(in, new_in_t, rpo);
            enqueue_uses(in, c);
          }
        }
      }
    }
    return types_at_c;
  }

  bool narrows_type(const Type* old_t, const Type* new_t) {
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

  void do_transform() {
    _wq.push(_phase->C->root());
    bool progress = false;
    for (uint i = 0; i < _wq.size(); i++) {
      Node* c = _wq.at(i);

      TreeNode* types = (TreeNode*) _types_at_ctrl[c];
      if (types->get_type(c) == Type::TOP) {
        assert(c->is_CatchProj() && c->in(0)->in(0)->in(0)->is_AllocateArray(), "");
        replace_node(c, _phase->C->top());
        _phase->C->set_major_progress();
        continue;
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

  bool validate_control(Node* n, Node* c) {
    ResourceMark rm;
    Unique_Node_List wq;
    wq.push(n);
    for (uint i = 0; i < wq.size(); i++) {
      Node* node = wq.at(i);
      assert(!node->is_CFG(), "");
      for (DUIterator_Fast jmax, j = node->fast_outs(jmax); j < jmax; j++) {
        Node* u = node->fast_out(j);
        if (!_visited.test(u->_idx)) {
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

  bool is_safe_for_replacement(Node* node, Node* use, TreeNode* types) {
    // if the exit test of a counted loop doesn't constant fold, preserve the shape of the exit test
    Node* node_c = _phase->get_ctrl(node);
    IdealLoopTree* loop = _phase->get_loop(node_c);
    Node* head = loop->_head;
    if (head->is_BaseCountedLoop()) {
      BaseCountedLoopNode* cl = head->as_BaseCountedLoop();
      Node* cmp = cl->loopexit()->cmp_node();
      if (((node == cl->phi() && use == cl->incr()) ||
           (node == cl->incr() && use == cmp)) &&
          !types->get_type(cmp)->singleton()) {
        return false;
      }
    }
    return true;
  }

  bool transform_helper(Node* c) {
    bool progress = false;
    TreeNode* types = (TreeNode*) _types_at_ctrl[c];
    {
      TreeNode::Iterator iter((TreeNode*) _types_at_ctrl[_phase->idom(c)], types);
      while (iter.next()) {
        Node* node = iter.node();
        const Type* t = iter.type2();
        if (t->singleton()) {
          if (node->is_CFG()) {
            continue;
          }
          if (t == Type::TOP) {
#ifdef ASSERT
            if (PrintLoopConditionalPropagation) {
              tty->print("top at %d", c->_idx);
              iter.node()->dump();
            }
#endif
            if (c->is_IfProj()) {
              if (!validate_control(node, c)) {
                continue;
              }
              Node* iff = c->in(0);
              if (iff->in(0)->is_top()) {
                continue;
              }
              Node* bol = iff->in(1);
              const Type* bol_t = bol->bottom_type();
              const Type* new_bol_t = TypeInt::make(1 - c->as_IfProj()->_con);
              if (bol_t != new_bol_t) {
                progress = true;
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
                  iter.node()->dump();
                  bol_t->dump();
                  tty->cr();
                  new_bol_t->dump();
                  tty->cr();
                  c->dump();
                }
#endif
              }
            }
          }
        }
      }
    }

    {
      TreeNode::Iterator iter((TreeNode*) _types_at_ctrl[_phase->idom(c)], types);
      while (iter.next()) {
        Node* node = iter.node();
        const Type* t = iter.type2();
        if (t->singleton()) {
          if (node->is_CFG()) {
            continue;
          }
          {
            Node* con = nullptr;
            for (DUIterator_Fast imax, i = node->fast_outs(imax); i < imax; i++) {
              Node* use = node->fast_out(i);
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
                      !(r->is_BaseCountedLoop() &&
                        j == LoopNode::LoopBackControl &&
                        use == r->as_BaseCountedLoop()->phi() &&
                        node == r->as_BaseCountedLoop()->incr() &&
                        !((TreeNode*) _types_at_ctrl[r->as_BaseCountedLoop()->loopexit()])->get_type(r->as_BaseCountedLoop()->loopexit()->cmp_node())->singleton())) {
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
                      iter.node()->dump();
                      tty->print("input %d of ", j); use->dump();
                      iter.type1()->dump();
                      tty->cr();
                      iter.type2()->dump();
                      tty->cr();
                    }
#endif
                  }
                }
                if (nb_deleted > 0) {
//                  stringStream ss;
//                  C->method()->print_short_name(&ss);
//                  tty->print("XXX %d %s", C->compile_id(), ss.as_string());
//                  use->dump();
//                  con->dump();
                  --i;
                  imax -= nb_deleted;
                }
              } else if (_phase->is_dominator(c, _phase->ctrl_or_self(use)) && is_safe_for_replacement(node, use, types)) {
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
                  iter.node()->dump();
                  use->dump();
                  iter.type1()->dump();
                  tty->cr();
                  iter.type2()->dump();
                  tty->cr();
                }
#endif
                if (use->is_If()) {
                  _phase->C->set_major_progress();
                }
              }
            }
//            if (!node->is_CFG() && node->outcnt() > 0) {
//              Node* node_c = _phase->get_ctrl(node);
//              IdealLoopTree* node_loop = _phase->get_loop(node_c);
//              Node* node_loop_head = node_loop->head();
//              if (node_loop_head->is_OuterStripMinedLoop()) {
//                Node* late_ctrl = _phase->get_late_ctrl(node, node_c);
//                if (!node_loop->is_member(_phase->get_loop(late_ctrl))) {
//                  _phase->set_ctrl(node, node_loop_head->as_OuterStripMinedLoop()->outer_loop_exit());
//                }
//              }
//            }
          }
#if 0
        } else if (node->is_Type() && c == node->in(0)) {
          progress = true;
#ifdef ASSERT
          assert(narrows_type(node->bottom_type(), t), "");
          if (PrintLoopConditionalPropagation) {
            tty->print_cr("improved type for");
            node->dump();
            iter.type1()->dump();
            tty->cr();
            iter.type2()->dump();
            tty->cr();
          }
#endif
          rehash_node_delayed(node);
          node->as_Type()->set_type(t);
          PhaseTransform::set_type(node, t);
          add_users_to_worklist(node);
          if (node->is_Phi() && node->in(0)->is_BaseCountedLoop() &&
              node->in(0)->as_BaseCountedLoop()->phi() == node) {
            BaseCountedLoopNode* head = node->in(0)->as_BaseCountedLoop();

            Node* limit = head->limit();
            Node* entry = head->skip_strip_mined(1)->in(LoopNode::EntryControl);
            if (!limit->is_Con()) {
              const Type* limit_t = types->get_type(limit);
              if (limit_t != ((TreeNode*) _types_at_ctrl[C->start()])->get_type(limit)) {
                Node* earliest = c;
                if (head->is_CountedLoop() && head->as_CountedLoop()->is_main_loop()) {
                  CountedLoopNode* cl = head->as_CountedLoop();
                  Node* opaq = cl->is_canonical_loop_entry();
                  if (opaq != nullptr) {
                    assert(opaq->in(1) == limit, "");
                    earliest = cl->skip_predicates()->in(0)->in(0);
                  }
                }
                Node* cast = create_cast_for_counted_loop(head, limit, limit_t, earliest);
                if (cast != nullptr) {
                  replace_input_of(head->loopexit()->cmp_node(), 2, cast);
                  if (head->is_CountedLoop() && head->as_CountedLoop()->is_main_loop()) {
                    CountedLoopNode* cl = head->as_CountedLoop();
                    Node* opaq = cl->is_canonical_loop_entry();
                    if (opaq != nullptr) {
                      replace_input_of(opaq, 1, cast);
                    }
                  }
                }
              }
            }
            Node* init = head->init_trip();
            if (!init->is_Con()) {
              const Type* init_t = types->get_type(init);
              if (init_t != ((TreeNode*) _types_at_ctrl[C->start()])->get_type(init)) {
                Node* earliest = c;
                Node* cast = create_cast_for_counted_loop(head, init, init_t, earliest);

                if (cast != nullptr) {
                  replace_input_of(head->phi(), LoopNode::EntryControl, cast);
                }
              }
            }
          }
#endif
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
            assert(((TreeNode*) _types_at_ctrl[iff])->get_type(c) != Type::TOP, "");
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

#if 0
  Node* create_cast_for_counted_loop(BaseCountedLoopNode* head, Node* n, const Type* n_t, Node* earliest) {
    Node* early = _phase->get_ctrl(n);
    assert(_phase->is_dominator(early, head), "");
    Node* best = earliest;

    while (_phase->is_dominator(early, earliest) && earliest != early) {
      Node* next = _phase->idom(earliest);
      const Type* next_t = ((TreeNode*) _types_at_ctrl[next])->get_type(n);
      if (next_t != n_t) {
        Node* cast = ConstraintCastNode::make(earliest, n, n_t, ConstraintCastNode::CountedLoopDependency,
                                              head->bt());
        register_new_node_with_optimizer(cast);
        _phase->set_ctrl(cast, best);
        IdealLoopTree* loop = _phase->get_loop(best);
        assert(!(loop->_child == nullptr && loop != _phase->ltree_root()), "");
        return cast;
      }
      earliest = next;
      if (_phase->get_loop(earliest)->_nest <= _phase->get_loop(best)->_nest) {
        best = earliest;
      }
    }
    return nullptr;
  }
#endif

  const Type* type(const Node* n, const Node* c) const {
    assert(c->is_CFG(), "");
    TreeNode* types = (TreeNode*) _types_at_ctrl[c];
    if (types == nullptr) {
      return PhaseTransform::type(n);
    }
    return types->get_type(n);
  }

  virtual PhaseConditionalPropagation* is_ConditionalPropagation() { return this; }

};

void PhaseIdealLoop::conditional_elimination(VectorSet &visited, Node_Stack &nstack, Node_List &rpo_list) {
  C->print_method(PHASE_DEBUG, 2);
  PhaseConditionalPropagation pcp(this, visited, nstack, rpo_list);
  pcp.analyze();
  pcp.do_transform();
  _igvn = pcp;
  C->print_method(PHASE_DEBUG, 2);
}

