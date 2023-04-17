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

#include "node.hpp"
#include "precompiled.hpp"
#include "opto/loopnode.hpp"
#include "opto/rootnode.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"

class PhaseConditionalPropagation : public PhaseIterGVN {
private:
  class TreeNode : public ResourceObj {
  private:
    Node* _node;
    const Type* _type;
    TreeNode* _left;
    TreeNode* _right;
    int _rpo;
    Node* _control;

  public:
    TreeNode(Node* n, const Type* type, Node* control)
            : _node(n), _type(type), _left(nullptr), _right(nullptr), _rpo(0), _control(control) {
    }

    TreeNode(Node* n, const Type* type, int rpo, TreeNode* left, TreeNode* right, Node* control)
            : _node(n), _type(type), _left(left), _right(right), _rpo(rpo), _control(control) {
    }

    TreeNode()
            : _node(nullptr), _type(nullptr), _left(nullptr), _right(nullptr), _rpo(0), _control(nullptr) {
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
      assert(UseNewCode3, "");
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

    TreeNode* set_type(Node* n, const Type* t, int rpo, Node* control) {
      assert(UseNewCode3, "");
//      assert(track_type(n), "");
      assert(_rpo <= rpo, "");
      if (_node == n) {
        if (_rpo < rpo) {
          return new TreeNode(_node, t, rpo, _left, _right, control);
        } else {
          _type = t;
          _control = control;
          return this;
        }
      } else if (n->_idx < idx()) {
        assert(_left != nullptr, "");
        TreeNode* tn = _left->set_type(n, t, rpo, control);
        if (_rpo == rpo) {
          _left = tn;
          return this;
        } else {
          assert(tn != _left, "");
          return new TreeNode(_node, _type, rpo, tn, _right, _control);
        }
      } else if (n->_idx > idx()) {
        assert(_right != nullptr, "");
        TreeNode* tn = _right->set_type(n, t, rpo, control);
        if (_rpo == rpo) {
          _right = tn;
          return this;
        } else {
          assert(tn != _right, "");
          return new TreeNode(_node, _type, rpo, _left, tn, _control);
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
        assert(UseNewCode3, "");
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
      assert(UseNewCode3, "");
//      assert(track_type(n), "");
      const TreeNode* tn = find(n);
      assert(tn != nullptr, "");
      return tn->_type;
    }

    const Type* get_type(const Node* n, Node* c) {
      assert(UseNewCode3, "");
//      assert(track_type(n), "");
      const TreeNode* tn = find(n);
      assert(tn != nullptr, "");
      if (tn->_control != c) {
        return nullptr;
      }
      return tn->_type;
    }
  };

  struct interval {
    int beg;
    int end;
  };

  void build_types_tree(VectorSet &visited) {
    assert(UseNewCode3, "");
    GrowableArray<TreeNode> nodes;
    Compile* C = _phase->C;
    nodes.push(TreeNode(C->root(), PhaseTransform::type(C->root()), C->root()));
    visited.set(C->root()->_idx);
    int cfg = 0;
    for (int i = 0; i < nodes.length(); i++) {
      TreeNode tn = nodes.at(i);
      for (uint j = 0; j < tn.node()->req(); j++) {
        Node* in = tn.node()->in(j);
        if (in != nullptr && !visited.test_set(in->_idx)) {
          nodes.push(TreeNode(in, PhaseTransform::type(in), C->root()));
        }
      }
    }
//    if (UseNewCode3) {
//      int shift = 0;
//      for (int i = 0; i < nodes.length(); ++i) {
//        TreeNode tn = nodes.at(i);
//        if (shift > 0) {
//          nodes.at_put(i - shift, tn);
//        }
//        if (!track_type(tn.node())) {
//          shift++;
//        }
//      }
//      tty->print_cr("XXX %d/%d", shift, nodes.length());
//      nodes.trunc_to(nodes.length() - shift);
//    }
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
    set_types_at_ctrl(C->root(), tree_root);
  }

  class ControlDataPair {
  public:
    ControlDataPair(Node* control, Node* data) : _control(control), _data(data) {
    }

    static unsigned hash(const ControlDataPair& pair) {
      unsigned hash = (unsigned)((uintptr_t)pair._control) ^ (unsigned)((uintptr_t)pair._data);
      return hash ^ (hash >> 3);
    }

    static bool equals(const ControlDataPair& pair0, const ControlDataPair& pair1) {
      return pair0._control == pair1._control && pair0._data == pair1._data;
    }

  private:
    Node* _control;
    Node* _data;
  };

  class TypeUpdate : public ResourceObj {
  private:
    class Entry {
    public:
      Entry(Node* node, const Type* before, const Type* after)
              : _node(node), _before(before), _after(after) {
      }
      Entry()
              : _node(nullptr), _before(nullptr), _after(nullptr) {
      }

      Node* _node;
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
      return _updates.at(i)._node;
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

    void push_node(Node* node, const Type* old_t, const Type* new_t) {
      _updates.insert_sorted<compare2>(Entry(node, old_t, new_t));
      assert(find(node) != -1 && _updates.at(find(node))._node == node, "");
    }

    TypeUpdate* prev() const {
      return _prev;
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

  ResizeableResourceHashtable<ControlDataPair, const Type*, AnyObj::RESOURCE_AREA, mtInternal, ControlDataPair::hash, ControlDataPair::equals> _types;
  using Updates = ResizeableResourceHashtable<Node*, TypeUpdate*, AnyObj::RESOURCE_AREA, mtInternal>;
  Updates* _updates;

  bool valid_use(Node* u, Node* c, const Node* n) {
//    assert(!UseNewCode3 || _phase->has_node(u) == _visited.test(u->_idx), "");
    if (!_phase->has_node(u) || (UseNewCode3 && !_visited.test(u->_idx))) {
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

  void enqueue_uses(const Node* n, Node* c) {
//    assert(!UseNewCode3 || _visited.test(n->_idx) == _phase->has_node(const_cast<Node*>(n)), "");
    assert(_phase->has_node(const_cast<Node*>(n)) && (!UseNewCode3 || _visited.test(n->_idx)), "");
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* u = n->fast_out(i);
      if (valid_use(u, c, n)) {
        _wq.push(u);
        if (u->Opcode() == Op_AddI || u->Opcode() == Op_SubI) {
          for (DUIterator_Fast i2max, i2 = u->fast_outs(i2max); i2 < i2max; i2++) {
            Node* uu = u->fast_out(i2);
            if (uu->Opcode() == Op_CmpU && valid_use(uu, c, u)) {
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
        if (u->is_Region()) {
          for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
            Node* uu = u->fast_out(j);
            if (uu->is_Phi() && valid_use(uu, c, n)) {
              _wq.push(uu);
            }
          }
        }
      }
    }
  }

  void set_type(Node* n, const Type* t, const Type* old_t, int rpo) {
    set_type(_current_ctrl, n, old_t, t);
    PhaseTransform::set_type(n, t);
  }

  void set_type_tree(Node* n, const Type* t, const Type* old_t, int rpo, Node* control) {
    assert(UseNewCode3, "");
    PhaseTransform::set_type(n, t);
    _current_types = _current_types->set_type(n, t, rpo, control);
  }

  Type_Array _types_tree_clone;
  Type_Array _types_clone;

  GrowableArray<TypeUpdate*> _stack;

  void sync_from_tree(Node* c) {
    _current_types = types_at_ctrl(c);
    assert(_current_types != nullptr, "");
    TreeNode::Iterator iter(types_at_ctrl(_current_ctrl_tree), _current_types);
    while (iter.next()) {
      Node* node = iter.node();
      const Type* t = iter.type2();
      PhaseTransform::set_type(node, t);
    }
    _current_ctrl_tree = c;
  }

  void sync(Node* c) {
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

  PhaseIdealLoop* _phase;
  VectorSet _visited;
  VectorSet _control_dependent_node;
  VectorSet _known_updates;
  Dict _types_at_ctrl;
  GrowableArray<TreeNode*> _types_at_ctrl2;
  Node_List _rpo_list;
  TreeNode* _current_types;
  Node* _current_ctrl;
  Node* _current_ctrl_tree;
#ifdef ASSERT
  VectorSet _conditions;
#endif
  Unique_Node_List _wq;
  Unique_Node_List _wq2;

  VectorSet _updated_type;
  bool _progress;
  bool _old_version;
  int _value_calls;

public:
  PhaseConditionalPropagation(PhaseIdealLoop* phase, VectorSet &visited, Node_Stack &nstack, Node_List &rpo_list)
  : PhaseIterGVN(&phase->igvn()),
    _types(8, 1000000),
    _updates(nullptr),
    _types_tree_clone(Thread::current()->resource_area()),
    _types_clone(Thread::current()->resource_area()),
    _phase(phase),
    _visited(visited),
    _types_at_ctrl(cmpkey, hashptr),
    _types_at_ctrl2(phase->C->unique()),
    _rpo_list(rpo_list),
    _current_types(nullptr),
    _current_ctrl(phase->C->root()),
    _current_ctrl_tree(phase->C->root()),
    _progress(true),
    _old_version(true),
    _value_calls(0),
    _current_updates(nullptr),
    _dom_updates(nullptr),
    _prev_updates(nullptr) {
    assert(nstack.is_empty(), "");
    assert(_rpo_list.size() == 0, "");
    phase->rpo(C->root(), nstack, _visited, _rpo_list, true);
    Node* root = _rpo_list.pop();
    assert(root == C->root(), "");
    if (UseNewCode2) {
      _updates = new Updates((unsigned int) (_rpo_list.size() / .8));
    }
    if (UseNewCode3) {
      _visited.clear();
      build_types_tree(_visited);
    }
  }

  Node* known_updates(Node* c) const {
    while (!_known_updates.test(c->_idx) && !c->is_Root()) {
      c = _phase->idom(c);
    }
    return c;
  }

  void analyze() {
    bool progress = true;
    int iterations = 0;
    int extra_rounds = 0;
    int extra_rounds2 = 0;
    bool has_infinite_loop = false;
    while (progress || _progress) {
      iterations++;
      assert(iterations - extra_rounds - extra_rounds2 >= 0, "");
      assert(iterations - extra_rounds2 <= 2 || _phase->ltree_root()->_child != nullptr || has_infinite_loop, "");
      assert(!UseNewCode3 || iterations - extra_rounds - extra_rounds2 <= 3 || _phase->_has_irreducible_loops, "");
      assert(iterations < 100, "");

      progress = false;
      bool extra = false;
      bool extra2 = false;
      _progress = false;

//      _control_dependent_node.clear();

      if (UseNewCode2 && UseNewCode3) {
        for (uint i = 0; i < PhaseTransform::_types.Size(); ++i) {
          _types_clone.map(i, PhaseTransform::_types.fast_lookup(i));
        }
        for (uint i = 0; i < PhaseTransform::_types.Size(); ++i) {
          _types_tree_clone.map(i, PhaseTransform::_types.fast_lookup(i));
        }
      }


      for (int i = _rpo_list.size() - 1; i >= 0; i--) {
        int rpo = _rpo_list.size() - 1 - i;
        Node* c = _rpo_list.at(i);
        has_infinite_loop = has_infinite_loop || (c->Opcode() == Op_NeverBranch);

        if (UseNewCode2) {
          _old_version = false;
          one_iteration(iterations, rpo, c, progress, has_infinite_loop, extra, extra2);
        }
        if (UseNewCode3) {
          _old_version = true;
          one_iteration_tree(iterations, rpo, c, progress, has_infinite_loop, extra, extra2);
        }

      }
      if (extra) {
        extra_rounds++;
      }
      if (extra2) {
        extra_rounds2++;
      }
      assert(!(UseNewCode2 && UseNewCode3) || !_progress || progress, "");
//      if (C->has_loops()) {
//        if (iterations == 2) {
//          break;
//        }
////        bool stop = true;
////        for (LoopTreeIterator iter(_phase->ltree_root()); !iter.done() && stop; iter.next()) {
////          IdealLoopTree* lpt = iter.current();
////          if (lpt == _phase->ltree_root()) {
////            continue;
////          }
////          Node* head = lpt->_head;
////          if (head->is_Loop()) {
////            TreeNode::Iterator iter(types_at_ctrl(head->in(LoopNode::LoopBackControl)), types_at_ctrl(head));
////            if (iter.next()) {
////              stop = false;
////            }
////          }
////        }
////        if (stop) {
////          break;
////        }
//      } else {
//        break;
//      }
    }

//    tty->print_cr("XXX value calls %d %d %d", _value_calls, iterations, _rpo_list.size());


    if (UseNewCode2 && UseNewCode3) {
      for (int i = _rpo_list.size() - 1; i >= 0; i--) {
        int rpo = _rpo_list.size() - 1 - i;
        Node* c = _rpo_list.at(i);
        Node* dom = _phase->idom(c);
        TreeNode* types_at_dom = types_at_ctrl(dom);
        TreeNode* types_at_c = types_at_ctrl(c);
        TreeNode::Iterator iter(types_at_dom, types_at_c);
        TypeUpdate* updates = updates_at(c);
        assert((types_at_c == types_at_dom) ==
               !(updates != nullptr && updates->control() == c && updates->length() != 0), "");
        int count = 0;
        while (iter.next()) {
          assert(updates->control() == c, "");
          Node* node = iter.node();
          int idx = updates->find(node);
          assert(idx != -1, "");
          assert(iter.type1() == updates->prev_type_at(idx), "");
          assert(iter.type2() == updates->type_at(idx), "");
//          assert(narrows_type(iter.type1(), updates->prev_type_at(idx)), "");
//          assert(narrows_type(iter.type2(), updates->type_at(idx)), "");
          count++;
        }
        int count2 = 0;
        if (updates != nullptr && updates->control() == c) {
          for (int j = 0; j < updates->length(); ++j) {
            Node* n = updates->node_at(j);
            assert(types_at_c->get_type(n) == updates->type_at(j), "");
            assert(types_at_dom->get_type(n) == updates->prev_type_at(j), "");
//            assert(narrows_type(types_at_c->get_type(n), updates->type_at(j)), "");
//            assert(narrows_type(types_at_dom->get_type(n), updates->prev_type_at(j)), "");
            if (updates->prev_type_at(j) != updates->type_at(j)) {
              count2++;
              assert(types_at_dom->get_type(n) != types_at_c->get_type(n), "");
            }
          }
          assert(count <= count2, "");
        }
      }
    }

    if (UseNewCode2) {
      sync(C->root());
    }
    if (UseNewCode3) {
      sync_from_tree(C->root());
    }
  }

  void one_iteration_tree(int iterations, int rpo, Node* c, bool& progress, bool has_infinite_loop, bool& extra, bool& extra2) {
    if (UseNewCode2) {
      for (uint i = 0; i < PhaseTransform::_types.Size(); ++i) {
        PhaseTransform::_types.map(i, _types_tree_clone.fast_lookup(i));
      }
    }

    Node* dom = _phase->idom(c);
    TreeNode* types_at_dom = types_at_ctrl(dom);

    TreeNode* prev_types_at_c = (TreeNode*) _types_at_ctrl[c];

    TreeNode* types_at_c = types_at_dom;
    if (c->is_Region()) {
      Node* in = c->in(1);
      TreeNode* types_at_in1 = types_at_ctrl(in);
      if (types_at_in1 != nullptr) {
        TreeNode::Iterator iter(types_at_dom, types_at_in1);
        while (iter.next()) {
          Node* node = iter.node();
          uint j = 2;
          const Type* t = iter.type2();
          const Type* current_type = types_at_dom->get_type(node);
          for (; j < c->req(); j++) {
            in = c->in(j);
            TreeNode* types_at_in = types_at_ctrl(in);
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
              const Type* prev_type = prev_types_at_c->get_type(node, c);
              if (prev_type != nullptr) {
                t = t->filter(prev_type);
                assert(t == prev_t, "");
                t = saturate(t, prev_type, nullptr);
                if (c->is_Loop() && t != prev_type) {
                  ShouldNotReachHere();
                  extra = true;
                }
                t = t->filter(current_type);
              }
            }

            if (t != current_type) {
              if (types_at_c->get_type(node) != t) {
#ifdef ASSERT
                assert(narrows_type(types_at_c->get_type(node), t), "");
#endif
                types_at_c = types_at_c->set_type(node, t, rpo, c);
//                      assert(!UseNewCode2 || t == get_type(c, node), "");
//                    set_type(c, node, t);
                enqueue_uses(node, c);
              } else {
                ShouldNotReachHere();
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
            sync_from_tree(iff);
            // narrowing the type of a LoadRange could cause a range check to optimize out and a Load to be hoisted above
            // checks that guarantee its within bounds
            if (cmp1->Opcode() != Op_LoadRange) {
              types_at_c = analyze_if_tree(rpo, c, types_at_c, cmp, cmp1);
            }
            if (cmp2->Opcode() != Op_LoadRange) {
              types_at_c = analyze_if_tree(rpo, c, types_at_c, cmp, cmp2);
            }
          }
        }
      }
    } else if (c->is_CatchProj() && c->in(0)->in(0)->in(0)->is_AllocateArray() &&
               c->as_CatchProj()->_con == CatchProjNode::fall_through_index) {
      AllocateArrayNode* alloc = c->in(0)->in(0)->in(0)->as_AllocateArray();
      sync_from_tree(dom);
      types_at_c = analyze_allocate_array_tree(rpo, c, types_at_c, alloc);
    }
    if (_control_dependent_node.test(c->_idx) || true) {
      for (DUIterator_Fast imax, i = c->fast_outs(imax); i < imax; i++) {
        Node* u = c->fast_out(i);

        if (!u->is_CFG() && u->in(0) == c && u->Opcode() != Op_CheckCastPP && _phase->has_node(u) && _visited.test(u->_idx) /*&& _control_dependent_node.test(u->_idx)*/) {
          _wq.push(u);
        }
      }
    }

    set_types_at_ctrl(c, types_at_c);

    sync_from_tree(c);
    while (_wq.size() > 0) {
      Node* n = _wq.pop();
      _value_calls++;
      const Type* t = n->Value(this);
      if (n->is_Phi() ) {
        const Type* prev_type = nullptr;
        prev_type = prev_types_at_c != nullptr ? prev_types_at_c->get_type(n, c): nullptr;
        if (prev_type != nullptr) {
          const Type* prev_t = t;
          t = t->filter(prev_type);
//          assert(t == prev_t, "");
          if (!(n->in(0)->is_CountedLoop() && n->in(0)->as_CountedLoop()->phi() == n &&
                n->in(0)->as_CountedLoop()->can_be_counted_loop(this))) {
            t = saturate(t, prev_type, nullptr);
          }
          if (c->is_Loop() && t != prev_type) {
            extra = true;
          }
        }
      }
      t = t->filter(PhaseTransform::type(n));
      if (t != PhaseTransform::type(n)) {
#ifdef ASSERT
        assert(narrows_type(PhaseTransform::type(n), t), "");
#endif
        set_type_tree(n, t, PhaseTransform::type(n), rpo, c);
        enqueue_uses(n, c);
      }
    }
    if (types_at_c != _current_types) {
      set_types_at_ctrl(c, _current_types);
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
#endif
    }
    if (UseNewCode2) {
      for (uint i = 0; i < PhaseTransform::_types.Size(); ++i) {
        _types_tree_clone.map(i, PhaseTransform::_types.fast_lookup(i));
      }
    }
  }

  void one_iteration(int iterations, int rpo, Node* c, bool& progress, bool has_infinite_loop, bool& extra, bool& extra2) {
    if (UseNewCode3) {
      for (uint i = 0; i < PhaseTransform::_types.Size(); ++i) {
        PhaseTransform::_types.map(i, _types_clone.fast_lookup(i));
      }
    }

    _known_updates.set(c->_idx);

    Node* dom = _phase->idom(c);
    _current_updates = updates_at(c);
    _dom_updates = updates_at(dom);
    _prev_updates = nullptr;
    if (_current_updates == nullptr) {
      _current_updates = _dom_updates;
      if (_current_updates != nullptr) {
        _updates->put(c, _current_updates);
      }
    } else {
      assert(iterations > 1, "");
      if (_current_updates == _dom_updates) {
//              _updates->put(c, _current_updates);
      } else if (_current_updates->control() != c) {
        assert(_dom_updates != nullptr, "");
        _current_updates = _dom_updates;
        _updates->put(c, _current_updates);
      } else {
        _prev_updates = _current_updates->copy();
        sync(dom);
        for (int j = 0; j < _current_updates->length();) {
          Node* n = _current_updates->node_at(j);
          const Type* dom_t = PhaseTransform::type(n);
          const Type* t = _current_updates->type_at(j);
          const Type* new_t = t->filter(dom_t);
          if (new_t == dom_t) {
            _current_updates->remove_at(j);
          } else {
            _current_updates->set_prev_type_at(j, dom_t);
            _current_updates->set_type_at(j, new_t);
            enqueue_uses(n, c);
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
        while(updates != nullptr && updates != _dom_updates && (_dom_updates == nullptr || !_phase->is_dominator(updates->control(), _dom_updates->control()))) {
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
      assert(updates != nullptr || _dom_updates == nullptr || _phase->is_dominator(c, in), "");
      while(updates != nullptr && updates != _dom_updates && (_dom_updates == nullptr || !_phase->is_dominator(updates->control(), _dom_updates->control()))) {
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
              if (_prev_updates != nullptr) {
                prev_round_t = _prev_updates->type_if_present(n);
              }
              if (prev_round_t != nullptr) {
                t = t->filter(prev_round_t);
                assert(t == prev_t, "");
                t = saturate(t, prev_round_t, nullptr);
                if (c->is_Loop() && t != prev_round_t) {
                  extra = true;
                }
                t = t->filter(current_type);
              }
            }

            if (t != current_type) {
              assert(narrows_type(current_type, t), "");
              if (record_update(c, n, current_type, t)) {
                enqueue_uses(n, c);
              }
            }
          }
        }
        updates = updates->prev();
        assert(updates != nullptr || _dom_updates == nullptr || _phase->is_dominator(c, in), "");
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
    if (_control_dependent_node.test(c->_idx) || true) {
      for (DUIterator_Fast imax, i = c->fast_outs(imax); i < imax; i++) {
        Node* u = c->fast_out(i);
        if (!u->is_CFG() && u->in(0) == c && u->Opcode() != Op_CheckCastPP && _phase->has_node(u) && (!UseNewCode3 || _visited.test(u->_idx)) /*&& _control_dependent_node.test(u->_idx)*/) {
          _wq.push(u);
        }
      }
    }

    sync(c);
    while (_wq.size() > 0) {
      Node* n = _wq.pop();
      _value_calls++;
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
          if (c->is_Loop() && t != prev_type) {
            extra = true;
          }
        }
      }
      t = t->filter(current_type);
      if (t != current_type) {
#ifdef ASSERT
        assert(narrows_type(current_type, t), "");
#endif
        set_type(n, t, current_type, rpo);
        enqueue_uses(n, c);
      }
    }

    if (UseNewCode3) {
      for (uint i = 0; i < PhaseTransform::_types.Size(); ++i) {
        _types_clone.map(i, PhaseTransform::_types.fast_lookup(i));
      }
    }
  }

  TreeNode* analyze_allocate_array_tree(int rpo, Node* c, TreeNode* types_at_c, const AllocateArrayNode* alloc) {
    Node* length = alloc->in(AllocateArrayNode::ALength);
    Node* klass = alloc->in(AllocateNode::KlassNode);
    const Type* klass_t = types_at_c->get_type(klass);
    if (klass_t != Type::TOP) {
      const TypeOopPtr* ary_type = klass_t->is_klassptr()->as_instance_type();
      const TypeInt* length_type = types_at_c->get_type(length)->isa_int();
      if (ary_type->isa_aryptr() && length_type != nullptr) {
        const Type* narrow_length_type = ary_type->is_aryptr()->narrow_size_type(length_type);
        narrow_length_type = narrow_length_type->filter(length_type);
        assert(narrows_type(length_type, narrow_length_type), "");
        if (narrow_length_type != length_type) {
          types_at_c = types_at_c->set_type(length, narrow_length_type, rpo, c);
          enqueue_uses(length, c);
        }
      }
    }
    return types_at_c;
  }

  void analyze_allocate_array(int rpo, Node* c, const AllocateArrayNode* alloc) {
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


  const Type* find_type_between(Node* n, Node* c, Node* dom) const {
    assert(_phase->is_dominator(dom, c), "");
    TypeUpdate* updates = updates_at(c);
    TypeUpdate* dom_updates = updates_at(dom);
    while(updates != dom_updates) {
      assert(updates != nullptr,"");
      assert(dom_updates == nullptr || !_phase->is_dominator(updates->control(), dom_updates->control()), "");
      int l = updates->find(n);
      if (l != -1) {
        return updates->type_at(l);
      }
      updates = updates->prev();
    }
    return nullptr;
  }

  const Type* find_prev_type_between(Node* n, Node* c, Node* dom) const {
    assert(_phase->is_dominator(dom, c), "");
    TypeUpdate* updates = updates_at(c);
    TypeUpdate* dom_updates = updates_at(dom);
    const Type* res = nullptr;
    while(updates != dom_updates) {
      assert(updates != nullptr,"");
      assert(dom_updates == nullptr || !_phase->is_dominator(updates->control(), dom_updates->control()), "");
      int l = updates->find(n);
      if (l != -1) {
        res = updates->prev_type_at(l);
      }
      updates = updates->prev();
    }
    return res;
  }

  bool set_type(Node* c, Node* n, const Type* old_t, const Type* t) {
    assert(UseNewCode2, "");
//    ControlDataPair pair = ControlDataPair(c, n);
//    if (_types.put(pair, t)) {
//      _types.maybe_grow();
//    }
    return record_update(c, n, old_t, t);
  }

  bool record_update(Node* c, Node* n, const Type* old_t, const Type* new_t) {
    assert(UseNewCode2, "");
    if (_current_updates == _dom_updates) {
      _current_updates = new TypeUpdate(_dom_updates, c);
      _updates->put(c, _current_updates);
    }
    int i = _current_updates->find(n);
    if (i == -1) {
      _progress = true;
      _current_updates->push_node(n, old_t, new_t);
      return true;
    } else if (_current_updates->type_at(i) != new_t) {
      _progress = true;
      _current_updates->set_type_at(i, new_t);
      return true;
    }
    return false;
  }

  const Type* get_type(Node* c, Node* n) {
    ShouldNotReachHere();
    assert(UseNewCode2 && UseNewCode3, "");
    if (!_updated_type.test(n->_idx)){
      return PhaseTransform::type(n);
    }
    for (;;) {
      const Type** t = _types.get(ControlDataPair(c, n));
      if (t != nullptr) {
        return *t;
      }
      if (c == _phase->C->root()) {
        ShouldNotReachHere();
        break;
      }
      c = _phase->idom(c);
    }
    return nullptr;
  }

  void remove_type(Node* c, Node* n) {
    assert(UseNewCode2 && UseNewCode3, "");
    _types.remove(ControlDataPair(c, n));
  }


  void set_types_at_ctrl(Node* c, TreeNode* types_at_c) {
    assert(UseNewCode3, "");
    _types_at_ctrl.Insert(c, types_at_c);
  }

  TreeNode* types_at_ctrl(Node* c) const {
    assert(UseNewCode3, "");
    TreeNode* types;
    for (;;) {
      types = (TreeNode*) (_types_at_ctrl[c]);
      if (types != nullptr) {
        return types;
      }
      c = _phase->idom(c);
    }
  }

  TreeNode* analyze_if_tree(int rpo, Node* c, TreeNode* types_at_c, const Node* cmp, Node* n) {
    const Type* t = IfNode::filtered_int_type(this, n, c, (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpU) ? T_INT : T_LONG);
    if (t != nullptr) {
      const Type* n_t = types_at_c->get_type(n);
      const Type* new_n_t = n_t->filter(t);
      assert(narrows_type(n_t, new_n_t), "");
      if (n_t != new_n_t) {
#ifdef ASSERT
        _conditions.set(c->_idx);
#endif
        types_at_c = types_at_c->set_type(n, new_n_t, rpo, c);
        enqueue_uses(n, c);
      }
      if (n->Opcode() == Op_ConvL2I) {
        Node* in = n->in(1);
        const Type* in_t;
        in_t = types_at_c->get_type(in);
        if (in_t->isa_long() && in_t->is_long()->_lo >= min_jint && in_t->is_long()->_hi <= max_jint) {
          const Type* t_as_long = t->isa_int() ? TypeLong::make(t->is_int()->_lo, t->is_int()->_hi, t->is_int()->_widen) : Type::TOP;
          const Type* new_in_t = in_t->filter(t_as_long);
          assert(narrows_type(in_t, new_in_t), "");
          if (in_t != new_in_t) {
#ifdef ASSERT
            _conditions.set(c->_idx);
#endif
            types_at_c = types_at_c->set_type(in, new_in_t, rpo, c);
            enqueue_uses(in, c);
          }
        }
      }
    }
    return types_at_c;
  }

  void analyze_if(Node* c, const Node* cmp, Node* n) {
    const Type* t = IfNode::filtered_int_type(this, n, c, (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpU) ? T_INT : T_LONG);
    if (t != nullptr) {
      const Type* n_t = type_if_present(c, n);
      if (n_t == nullptr) {
        n_t = PhaseTransform::type(n);
      }
      const Type* new_n_t = n_t->filter(t);
      assert(narrows_type(n_t, new_n_t), "");
      if (n_t != new_n_t) {
#ifdef ASSERT
        _conditions.set(c->_idx);
#endif
        if (record_update(c, n, n_t, new_n_t)) {
          enqueue_uses(n, c);
        }
      }
      if (n->Opcode() == Op_ConvL2I) {
        Node* in = n->in(1);
        const Type* in_t;
        in_t = type_if_present(c, in);
        if (in_t == nullptr) {
          in_t = PhaseTransform::type(in);
        }
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
            }
          }
        }
      }
    }
  }

  bool narrows_type(const Type* old_t, const Type* new_t) {
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
//        new_int->widen_limit() > old_int->widen_limit()) {
//      return false;
//    }

    return true;
  }

  void do_transform() {
    _wq.push(_phase->C->root());
    bool progress = false;
    for (uint i = 0; i < _wq.size(); i++) {
      Node* c = _wq.at(i);

      if (UseNewCode3) {
        TreeNode* types = types_at_ctrl(c);
        assert(!UseNewCode2 || (types->get_type(c) == Type::TOP) == (type_if_present(c, c) == Type::TOP), "");
        if (types->get_type(c) == Type::TOP) {
          assert(c->is_CatchProj() && c->in(0)->in(0)->in(0)->is_AllocateArray(), "");
          replace_node(c, _phase->C->top());
          _phase->C->set_major_progress();
          continue;
        }
      } else if (UseNewCode2) {
        const Type* t = type_if_present(c, c);
        if (t == Type::TOP) {
          assert(c->is_CatchProj() && c->in(0)->in(0)->in(0)->is_AllocateArray(), "");
          replace_node(c, _phase->C->top());
          _phase->C->set_major_progress();
          continue;
        }
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
//        assert(!UseNewCode3 || _visited.test(u->_idx) == _phase->has_node(u), "");
        if (!_phase->has_node(u) || (UseNewCode3 && !_visited.test(u->_idx))) {
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

  bool is_safe_for_replacement(Node* c, Node* node, Node* use) {
    // if the exit test of a counted loop doesn't constant fold, preserve the shape of the exit test
    Node* node_c = _phase->get_ctrl(node);
    IdealLoopTree* loop = _phase->get_loop(node_c);
    Node* head = loop->_head;
    if (head->is_BaseCountedLoop()) {
      BaseCountedLoopNode* cl = head->as_BaseCountedLoop();
      Node* cmp = cl->loopexit()->cmp_node();
      if (((node == cl->phi() && use == cl->incr()) ||
           (node == cl->incr() && use == cmp))) {
        if (UseNewCode3) {
          TreeNode* types = types_at_ctrl(c);
          const Type* cmp_t = types->get_type(cmp);
          if (!cmp_t->singleton()) {
            assert(!UseNewCode2 || type_if_present(c, cmp) == nullptr || !type_if_present(c, cmp)->singleton(), "");
            return false;
          }
          assert(!UseNewCode2 || type_if_present(c, cmp)->singleton(), "");
        } else if (UseNewCode2) {
          const Type* cmp_t = type_if_present(c, cmp);
          if (cmp_t == nullptr || !cmp_t->singleton()) {
            return false;
          }
        }
      }
    }
    return true;
  }

  bool transform_when_top_seen(Node* c, Node* node, const Type* t) {
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

  bool transform_when_constant_seen(Node* c, Node* node, const Type* t, const Type* prev_t) {
    if (t->singleton()) {
      if (node->is_CFG()) {
        return false;
      }
      {
        Node* con = nullptr;
        bool progress = false;
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
                  !(0 && r->is_BaseCountedLoop() &&
                    j == LoopNode::LoopBackControl &&
                    use == r->as_BaseCountedLoop()->phi() &&
                    node == r->as_BaseCountedLoop()->incr() &&
                    !types_at_ctrl(r->as_BaseCountedLoop()->loopexit())->get_type(r->as_BaseCountedLoop()->loopexit()->cmp_node())->singleton())) {
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
        return progress;
      }
    }
    return false;
  }

  bool transform_helper(Node* c) {
    bool progress = false;
    if (UseNewCode3) {
      TreeNode* types = types_at_ctrl(c);
      {
        TreeNode::Iterator iter(types_at_ctrl(_phase->idom(c)), types);
        int processed = 0;
        while (iter.next()) {
          processed++;
          Node* node = iter.node();
          const Type* t = iter.type2();
          assert(!UseNewCode2 || (updates_at(c) != nullptr && updates_at(c)->control() == c && updates_at(c)->type_if_present(node) == t), "");
          if (transform_when_top_seen(c, iter.node(), t)) {
            progress = true;
          }
        }
        assert(!UseNewCode2 || processed != 0 || updates_at(c) == nullptr || updates_at(c)->control() != c || updates_at(c)->length() == 0, "");
        assert(!UseNewCode2 || processed == 0 || (updates_at(c) != nullptr && updates_at(c)->control() == c && processed == updates_at(c)->length()), "");
      }
    } else if (UseNewCode2) {
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

    if (UseNewCode3) {
      TreeNode* types = types_at_ctrl(c);
      TreeNode::Iterator iter(types_at_ctrl(_phase->idom(c)), types);
      int processed = 0;
      while (iter.next()) {
        processed++;
        Node* node = iter.node();
        const Type* t = iter.type2();
        const Type* prev_t = iter.type1();
        assert(!UseNewCode2 || (updates_at(c) != nullptr && updates_at(c)->control() == c && updates_at(c)->type_if_present(node) == t), "");
        if (transform_when_constant_seen(c, iter.node(), t, prev_t)) {
          progress = true;
        }
      }
      assert(!UseNewCode2 || processed != 0 || updates_at(c) == nullptr || updates_at(c)->control() != c || updates_at(c)->length() == 0, "");
      assert(!UseNewCode2 || processed == 0 || (updates_at(c) != nullptr && updates_at(c)->control() == c && processed == updates_at(c)->length()), "");
    } else if (UseNewCode2) {
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
            assert(!UseNewCode3 || types_at_ctrl(iff)->get_type(c) != Type::TOP, "");
            assert(!UseNewCode2 || !(updates_at(c) != nullptr && updates_at(c)->type_if_present(c) == Type::TOP), "");
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
  const Type* type(const Node* n, Node* c) const {
    assert(c->is_CFG(), "");
    const Type* res = nullptr;
    if (!_old_version) {
      assert(_current_ctrl->is_Region() && _current_ctrl->find_edge(c) != -1, "");
      Node* dom = _phase->idom(_current_ctrl);
      TypeUpdate* updates = updates_at(c);
      TypeUpdate* dom_updates = updates_at(dom);
      assert(updates != nullptr || dom_updates == nullptr || _phase->is_dominator(_current_ctrl, c), "");
      while (updates != nullptr && updates != dom_updates && (dom_updates == nullptr || !_phase->is_dominator(updates->control(), dom_updates->control()))) {
        int idx = updates->find(n);
        if (idx != -1) {
          res = updates->type_at(idx);
          break;
        }
        updates = updates->prev();
        assert(updates != nullptr || dom_updates == nullptr || _phase->is_dominator(_current_ctrl, c), "");
      }
      if (res == nullptr) {
        res = PhaseTransform::type(n);
      }
    } else {
      assert(_current_ctrl_tree->is_Region() && _current_ctrl_tree->find_edge(c) != -1, "");
      TreeNode* types;
      for (;;) {
        if (c == _current_ctrl_tree) {
          return PhaseTransform::type(n);
        }
        types = (TreeNode*) _types_at_ctrl[c];
        if (types != nullptr) {
          return types->get_type(n);
        }
        c = _phase->idom(c);
      }
    }
    return res;
  }

  virtual PhaseConditionalPropagation* is_ConditionalPropagation() { return this; }

  TypeUpdate* _current_updates;
  TypeUpdate* _dom_updates;
  TypeUpdate* _prev_updates;
};

void PhaseIdealLoop::conditional_elimination(VectorSet &visited, Node_Stack &nstack, Node_List &rpo_list) {
  if (!UseNewCode2 && !UseNewCode3) {
    return;
  }
  TraceTime tt("loop conditional propagation", UseNewCode);
  C->print_method(PHASE_DEBUG, 2);
  PhaseConditionalPropagation pcp(this, visited, nstack, rpo_list);
  {
    TraceTime tt("loop conditional propagation analyze", UseNewCode);
    pcp.analyze();
  }
  {
    TraceTime tt("loop conditional propagation transform", UseNewCode);
    pcp.do_transform();
  }
  _igvn = pcp;
  C->print_method(PHASE_DEBUG, 2);
}

