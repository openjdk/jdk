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

#ifndef SHARE_OPTO_LOOPCONDITIONALPROPAGATION_HPP
#define SHARE_OPTO_LOOPCONDITIONALPROPAGATION_HPP

#include "opto/indexSet.hpp"
#include "opto/loopnode.hpp"
#include "opto/rootnode.hpp"

class PhaseConditionalPropagation :  public StackObj {
private:
  // As part of this optimization the type of node n can be narrowed to a new type t at a control c
  // This class keeps the mapping of (n, c) -> t
  class TypeTable : public ResourceObj {
  protected:
    // The (n, c) -> t mapping is kept with a table that maps a control to a list of type updates:
    // c -> [(n1, t1), (n2, t2)...]
    // The reason for this is that it's common to have to iterate over all updates at a particular control
    // This class implements the list of type updates [(n1, t1), (n2, t2)...]
    class NodeTypesList : public ResourceObj {
    private:
      // This is one entry (n, t) in the list [(n1, t1), (n2, t2)...]
      class NodeTypes {
      public:
        NodeTypes(Node* node, const Type* before, const Type* after)
          : _node(node), _before(before), _after(after) {
        }

        NodeTypes()
          : _node(nullptr), _before(nullptr), _after(nullptr) {
        }

        Node* _node; // the node whose type is narrowed
        const Type* _before; // the current type (before narrowing)
        const Type* _after; // the narrowed type
      };

      GrowableArray<NodeTypes> _node_types; // the list of updates [(n1, t1), (n2, t2)...]
      // We are at some control where some nodes have their types narrowed
      // If we go up the dominator tree, there may be some controls at which some nodes had their type narrowed
      // _prev points to the type updates we would encounter first if we were to follow the dominator tree.
      // This is useful to iterate over all type updates between the current control and some dominating control: follow
      // _prev links comparing their _control with the dominating control we want to stop at
      NodeTypesList* _prev;
      Node* _control; // control at which the update happens
      int _iterations; // iterations of main algorithm at which this was last updated

      NodeTypesList(NodeTypesList* prev, Node* control, int size, int interations)
        : _node_types(size), _prev(prev), _control(control), _iterations(interations) {
      }

    public:
      NodeTypesList(NodeTypesList* prev, Node* control, int iterations)
        : _prev(prev), _control(control), _iterations(iterations) {
      }

      int length() const {
        return _node_types.length();
      }

      Node* node_at(int i) const {
        return _node_types.at(i)._node;
      }

      const Type* prev_type_at(int i) const {
        return _node_types.at(i)._before;
      }

      const Type* type_at(int i) const {
        return _node_types.at(i)._after;
      }

      const Type* type_if_present(Node* n) {
        int i = find(n);
        if (i == -1) {
          return nullptr;
        }
        return _node_types.at(i)._after;
      }

      const Type* prev_type_if_present(Node* n) {
        int i = find(n);
        if (i == -1) {
          return nullptr;
        }
        return _node_types.at(i)._before;
      }

      void set_type_at(int i, const Type* t) {
        _node_types.at(i)._after = t;
      }

      void set_prev_type_at(int i, const Type* t) {
        _node_types.at(i)._before = t;
      }

      bool contains(Node* n) {
        return find(n) != -1;
      }

      void remove_at(int i) {
        _node_types.remove_at(i);
      }

      static int compare_for_find(const Node* const&n, const NodeTypes &e) {
        return n->_idx - e._node->_idx;
      }

      static int compare_for_push_node(const NodeTypes &e1, const NodeTypes &e2) {
        return e1._node->_idx - e2._node->_idx;
      }

      int find(const Node* n) {
        bool found = false;
        int res = _node_types.find_sorted<const Node*, compare_for_find>(n, found);
        if (!found) {
          return -1;
        }
        return res;
      }

      void push_node(Node* node, const Type* old_t, const Type* new_t) {
        _node_types.insert_sorted<compare_for_push_node>(NodeTypes(node, old_t, new_t));
        assert(find(node) != -1 && _node_types.at(find(node))._node == node, "");
      }

      NodeTypesList* prev() const {
        return _prev;
      }

      bool below(NodeTypesList* dom_node_types_list, PhaseConditionalPropagation& conditional_propagation) const {
        return this != dom_node_types_list && (dom_node_types_list == nullptr ||
          !conditional_propagation.is_dominator(control(), dom_node_types_list->control()));
      }

      void set_prev(NodeTypesList* prev) {
        _prev = prev;
      }

      Node* control() const {
        return _control;
      }

      NodeTypesList* copy() const {
        NodeTypesList* c = new NodeTypesList(_prev, _control, _node_types.length(), _iterations);
        for (int i = 0; i < _node_types.length(); ++i) {
          c->_node_types.push(_node_types.at(i));
        }
        return c;
      }

      void set_iterations(int iterations) {
        _iterations = iterations;
      }

      int iterations() const {
        return _iterations;
      }

#ifdef ASSERT
      void dump() const;
#endif
    };

    // The c -> [(n1, t1), (n2, t2)...] mapping is kept in a hash table indexed by c
    using NodeTypesListTable = ResizeableResourceHashtable<Node*, NodeTypesList*, AnyObj::RESOURCE_AREA, mtInternal>;
    NodeTypesListTable* _node_types_list_table;

    NodeTypesList* node_types_list_at(Node* c) const {
      NodeTypesList** node_types_list_ptr = _node_types_list_table->get(_conditional_propagation.known_updates(c));
      if (node_types_list_ptr == nullptr) {
        return nullptr;
      }
      return *node_types_list_ptr;
    }

    const Type* type_if_present(Node* c, Node* n) const {
      NodeTypesList* node_types_list = node_types_list_at(c);
      if (node_types_list == nullptr) {
        return nullptr;
      }
      return node_types_list->type_if_present(n);
    }

    PhaseConditionalPropagation &_conditional_propagation;
    PhaseIdealLoop* _phase;

    TypeTable(PhaseConditionalPropagation &conditional_propagation);
    template <class Callback> bool apply_between_controls_internal(Node* c, Node* dom, Callback callback) const;
  public:

    const Type* find_type_between(const Node* n, Node* c, Node* dom) const;

    const Type* find_prev_type_between(const Node* n, Node* c, Node* dom) const;

    const Type* type(Node* n, Node* c) const ;

    template <class Callback> void apply_at_control(Node* c, Callback callback) const;
    template <class Callback> void apply_at_control_with_updates(Node* c, Callback callback) const;
    bool has_types_at_control(Node* c) const;
  };

  // A TypeTable that can be updated. First phase of the transformation analyzes the graph and collects new types. It
  // uses a WriteableTypeTable. Second phase transforms the graph based on the new types but doesn't make any updates
  // to types: it uses the read only TypeTable.
  class WriteableTypeTable : public TypeTable {
  private:
    // To avoid repetitive queries, we cache some pointers to NodeTypesList
    NodeTypesList* _current_node_types_list; // the one we're currently updating (at _current_ctrl)
    NodeTypesList* _dom_node_types_list; // The one at the immediate dominator
    NodeTypesList* _prev_node_types_list; // The one from the previous iterations of the main algorithm

  public:

    WriteableTypeTable(PhaseConditionalPropagation &conditional_propagation)
            : TypeTable(conditional_propagation),
              _current_node_types_list(nullptr),
              _dom_node_types_list(nullptr),
              _prev_node_types_list(nullptr) {
    }

    void set_current_control(Node* c, bool verify, int iterations);
    bool record_type(Node* c, Node* n, const Type* prev_t,
                     const Type* new_t, int iterations);
    bool types_improved(Node* c, int iterations, bool verify) const;
    const Type* type_at_current_ctrl(Node* n) const;

    const Type* prev_iteration_type(Node* n) const;
    const Type* prev_iteration_type(Node* n, Node* c) const;

    int iterations_at(Node* c) {
      NodeTypesList* node_types_list = node_types_list_at(c);
      if (node_types_list == nullptr) {
        return -1;
      }
      return node_types_list->iterations();
    }

    template <class Callback> void apply_between_controls(Node* c, Node* dom, Callback callback) const;
    int count_updates_between_controls(Node* c, Node* dom) const;
    template <class Callback> void apply_at_prev_iteration(Callback callback) const;
  };

  // When the type of a node is narrowed, there is usually an opportunity to narrow the type of other nodes that depend
  // on that node but, in the general case, processing those nodes only make sense once the main algorithm has reached
  // a particular control. With this WorkQueue implementation, nodes are enqueued for processing at some particular
  // control.
  class WorkQueue : public ResourceObj {
  private:
    // A mapping from some control to a list of nodes that need processing
    using WorkQueues = ResizeableResourceHashtable<Node*, GrowableArray<Node*>*, AnyObj::RESOURCE_AREA, mtInternal>;
    WorkQueues* _work_queues;
    // A cheap way to check if a node was already enqueued
    VectorSet _enqueued;
    // As an optimization, keep track of the current control the main algorithm is analyzing and if a node is enqueued
    // at _current_ctrl, push it on the Unique_Node_List below
    Unique_Node_List _wq;
    Node* _current_ctrl;

    GrowableArray<Node*>* work_queue_at(Node* c) const {
      GrowableArray<Node*>** work_queue_ptr = _work_queues->get(c);
      if (work_queue_ptr == nullptr) {
        return nullptr;
      }
      return *work_queue_ptr;
    }

    bool enqueue_for_delayed_processing(Node* n, Node* c);

  public:
    WorkQueue(Node* root, uint max_elements) :
            _current_ctrl(root) {
      _work_queues = new WorkQueues(8, max_elements);
    }

    bool enqueued(const Node* n) {
      return _enqueued.test(n->_idx) || _wq.member(n);
    }

    bool is_empty(Node* c) const {
      if (c == _current_ctrl) {
        assert(work_queue_at(c) == nullptr, "");
        return _wq.size() == 0;
      }
      return work_queue_at(c) == nullptr;
    }

    bool all_empty() const {
      assert((_work_queues->number_of_entries() == 0) == _enqueued.is_empty(), "inconsistency");
      return _work_queues->number_of_entries() == 0;
    }

    Node* pop(Node* c) {
      assert(c == _current_ctrl && work_queue_at(c) == nullptr, "");
      return _wq.pop();
    }

    void dump() const PRODUCT_RETURN;

    void set_current_control(Node* c);
    bool enqueue(Node* n, Node* c);
  };

  // First phase of the transformation: collect types
  class Analyzer : public PhaseIterGVN {
  private:

    PhaseConditionalPropagation& _conditional_propagation;
    PhaseIdealLoop* _phase;
    int _iterations;
    WorkQueue* _work_queue;
    bool _verify;
#ifdef ASSERT
    VectorSet& _visited;
    bool _progress;
#endif
    Node_List &_rpo_list;
    WriteableTypeTable* _type_table;
    Node* _current_ctrl;

    void enqueue_use(Node* n, Node* queue_control);
    Node* compute_queue_control(Node* u) const;
    Node* compute_queue_control(Node* u, bool at_current_ctrl);
    void maybe_enqueue_if_projections_from_cmp(const Node* u);
    void maybe_enqueue_if_projections(IfNode* iff);
    void handle_region(Node* dom, bool &extra_loop_variable);
    void handle_ifproj();
    void propagate_types(bool &extra_type_init);
    void analyze_allocate_array(const AllocateArrayNode* alloc);
    void analyze_if(const Node* cmp, Node* n);
    bool one_iteration(bool &extra_loop_variable, bool &extra_type_init);
    void merge_with_dominator_types();
    void verify(bool& extra_type_init) PRODUCT_RETURN;
    struct NodeTypePair {
      Node* _n;
      const Type* _t;
    };
    GrowableArray<NodeTypePair> _stack; // This is needed by sync()
    void sync_global_types_with_types_at_control(Node* c);
    Node* _current_types_ctrl;

#ifdef ASSERT
    // During verification, nodes are enqueued for verification as soon as the type of one input is narrowed. So a node
    // can have its type narrowed at a control that dominates early control for the node. Outside of verification that
    // can't happen. Track nodes for which that happens and make sure they all have the same type during and before
    // verification at early control for the node.
    Unique_Node_List _verify_wq;
    bool verify_wq_empty() const {
      return _verify_wq.size() == 0;
    }
#endif

  private:
    const Type* type_at_current_ctrl(Node* n) const;

  public:
    Analyzer(PhaseConditionalPropagation &conditional_propagation, VectorSet& visited, Node_List& rpo_list)
    : PhaseIterGVN(&conditional_propagation._phase->igvn()),
      _conditional_propagation(conditional_propagation),
      _phase(conditional_propagation._phase),
      _iterations(0),
      _work_queue(nullptr),
      _verify(false),
#ifdef ASSERT
      _visited(visited),
      _progress(true),
#endif
      _rpo_list(rpo_list),
      _type_table(nullptr),
      _current_ctrl(nullptr),
      _current_types_ctrl(conditional_propagation._phase->C->root()) {
      _work_queue = new WorkQueue(_phase->C->root(), _conditional_propagation._rpo_list.size());
      _type_table = new WriteableTypeTable(_conditional_propagation);
    }
    const TypeTable* analyze(int rounds);

#ifdef ASSERT
    void maybe_set_progress(Node* n, Node* c) {
      if (_visited.test(c->_idx)) {
        _progress = true;
        assert(n->is_Phi() || n->is_Region(), "only backedges");
      }
    }
#endif

    void enqueue_uses(const Node* n, bool at_current_ctrl = false);

    int iterations() const {
      return _iterations;
    }

    bool verify() const {
      return _verify;
    }
    const Type* type(const Node* n, Node* c) const;

    void set_type(Node* n, const Type* t, const Type* old_t) {
      _type_table->record_type(_current_types_ctrl, n, old_t, t, _iterations);
      PhaseValues::set_type(n, t);
    }

    void enqueue(Node* n, Node* c);
  };

  // Second phase of the transformation: transform the graph from types collected by first phase
  class Transformer : public ResourceObj {
  private:
    PhaseConditionalPropagation& _conditional_propagation;
    PhaseIdealLoop* _phase;
    Unique_Node_List _controls;
    Unique_Node_List _wq;
    const TypeTable* _type_table;
    uint _unique;

    void transform_when_top_seen(Node* c, Node* node, const Type* t);
    void transform_when_constant_seen(Node* c, Node* node, const Type* t, const Type* prev_t);
    void transform_helper(Node* c);
    bool is_safe_for_replacement(Node* c, Node* node, Node* use) const;
    bool is_safe_for_replacement_at_phi(Node* node, Node* use, Node* r, uint j) const;
    void pin_array_access_nodes(Node* c, const IfNode* iff, int con) const;
    void pin_uses_if_needed(const Type* t, Node* use, Node* c);
    void pin_array_access_nodes_if_needed(const Node* node, const Type* t, const Node* use, Node* c) const;
    bool related_node(Node* n, Node* c);
    void create_halt_node(Node* c) const;

  public:
    Transformer(PhaseConditionalPropagation& conditional_propagation, const TypeTable* type_table)
            : _conditional_propagation(conditional_propagation),
              _phase(conditional_propagation._phase),
              _type_table(type_table),
              _unique(_conditional_propagation._phase->C->unique()) {
    }

    ProjNode* always_taken_if_proj(IfNode* iff);

    void do_transform();
  };

  // Utility class: main algorithm needs early control for some nodes. Rather than recompute it, cache the result
  class EarlyCtrls {
  private:
    Node_Stack& _nstack;
    Node_List _intermediate_results;
    PhaseIdealLoop* _phase;
    PhaseConditionalPropagation& _conditional_propagation;
    using NodeToCtrl = ResizeableResourceHashtable<Node*, Node*, AnyObj::RESOURCE_AREA, mtInternal>;
    NodeToCtrl* _node_to_ctrl_table;

    Node* known_early_ctrl(Node* n) const;

    Node* compute_early_ctrl(Node* u);

    Node* update_early_ctrl(Node* early_c, Node* in_c);

  public:
    EarlyCtrls(Node_Stack& nstack, PhaseConditionalPropagation& conditional_propagation);
    Node* get_early_ctrl(Node* u);
  };

  Node* known_updates(Node* c) const {
    return _phase->find_non_split_ctrl(c);
  }

  PhaseIdealLoop* _phase;
  VectorSet& _visited;
  Node_List &_rpo_list;

#ifdef ASSERT
  VectorSet _conditions;
  void record_condition(Node* c) {
    _conditions.set(c->_idx);
  }
  bool condition_recorded(Node* c) const {
    return _conditions.test(c->_idx);
  }
#endif

  EarlyCtrls _early_ctrls;
  Node* get_early_ctrl(Node* u) {
    return _early_ctrls.get_early_ctrl(u);
  }

  const TypeTable* analyze(int rounds);
  void do_transform(const TypeTable* type_table);

  static const int load_factor = 8;

  // Utility class: main algorithm makes heavy use of dominator check. This provides a faster check. It requires a new
  // tree structure to be constructed (A node in the tree is for one control and it is an immediate dominator for its
  // children: it is the existing idom structure upside down) and traversed so each node of the tree is annotated with
  // its pre and post order positions.
  class DominatorTree : public ResourceObj {
  private:
    class DomTreeNode : public ResourceObj {
    public:
      DomTreeNode* _child;
      DomTreeNode* _sibling;
      Node* _node;
      uint _pre;
      uint _post;

      DomTreeNode(Node* node) : _child(nullptr), _sibling(nullptr), _node(node), _pre(0), _post(0) {
      }
    };
    // Node to tree node hash table
    using DomTreeTable = ResizeableResourceHashtable<Node*, DomTreeNode*, AnyObj::RESOURCE_AREA, mtInternal>;
    DomTreeTable* _nodes;
  public:
    DominatorTree(const Node_List& rpo_list, PhaseIdealLoop* phase);

    //           n1 (pre =0, post = 13)
    //         /                 \
    //        n2 (1,6)             n5 (7, 12)
    //       /  \                 /  \
    //(2,3) n3    n4 (4,5) (8,9) n6   n7 (10, 11)
    //
    // n1 dominates n4 because 0 < 4 and 5 < 13
    // n2 doesn't dominates n7: 1 < 10 but 11 > 6
    bool is_dominator(Node* dominator, Node* m) const {
      DomTreeNode* dom_n = *_nodes->get(dominator);
      DomTreeNode* dom_m = *_nodes->get(m);
      return dom_n->_pre < dom_m->_pre && dom_n->_post > dom_m->_post;
    }
  };
  DominatorTree* _dominator_tree;

public:
  PhaseConditionalPropagation(PhaseIdealLoop* phase, VectorSet &visited, Node_Stack &nstack, Node_List &rpo_list);

  void analyze_and_transform(int rounds);

#ifdef ASSERT
  static bool narrows_type(const Type* old_t, const Type* new_t, bool strictly = false);
#endif

  bool is_dominator(Node* dominator, Node* m) const {
    assert(_phase->is_dominator(dominator, m) == (dominator == m || _dominator_tree->is_dominator(dominator, m)), "");
    return (dominator == m || _dominator_tree->is_dominator(dominator, m));
  }

};

#endif // SHARE_OPTO_LOOPCONDITIONALPROPAGATION_HPP
