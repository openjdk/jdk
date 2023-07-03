/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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
 */

#ifndef SHARE_OPTO_VMASKLOOP_HPP
#define SHARE_OPTO_VMASKLOOP_HPP

#include "opto/loopnode.hpp"
#include "opto/superword.hpp"
#include "opto/vectornode.hpp"

// ----------------------------- VectorMaskedLoop -----------------------------
class VectorMaskedLoop : public ResourceObj {
 private:
  // Useful handles
  PhaseIdealLoop*            _phase;
  PhaseIterGVN*              _igvn;
  Arena*                     _arena;

  // Loop information
  IdealLoopTree*             _lpt;          // Idealloop tree
  CountedLoopNode*           _cl;           // CountedLoop node
  CountedLoopEndNode*        _cle;          // CountedLoopEnd node
  PhiNode*                   _iv;           // Loop induction variable PhiNode

  // Data structures for loop analysis
  Unique_Node_List           _core_set;     // Loop core nodes set for fast membership check
  Unique_Node_List           _body_set;     // Loop body nodes set for fast membership check
  GrowableArray<Node*>       _body_nodes;   // Loop body nodes with reverse postorder
  GrowableArray<int>         _rpo_idx;      // Map from node index to RPO traversal index
  GrowableArray<BasicType>   _elem_bt;      // Per node vector element basic type
  GrowableArray<Node_List*>  _stmts;        // Lists of nodes that make up loop statements
  GrowableArray<SWPointer*>  _swptrs;       // SWPointer array for memory access nodes
  VectorElementSizeStats     _size_stats;   // Statistics of data sizes in vectors

  // Basic utilities
  bool in_core(Node* n)            { return n != nullptr && _core_set.member(n); }
  bool in_body(Node* n)            { return n != nullptr && _body_set.member(n); }
  int  rpo_idx(Node* n)            { assert(in_body(n), "What?"); return _rpo_idx.at(n->_idx); }
  void set_rpo_idx(Node* n, int i) { assert(in_body(n), "What?"); _rpo_idx.at_put_grow(n->_idx, i); }

  BasicType statement_bottom_type(const Node_List* stmt) const {
    assert(stmt != nullptr && stmt->size() > 0, "should not be empty");
    assert(stmt->at(0)->is_Store(), "Must be a store node");
    return stmt->at(0)->as_Store()->memory_type();
  }

  BasicType size_to_basic_type(const int size) const {
    BasicType bt = T_ILLEGAL;
    switch (size) {
      case 1: bt = T_BYTE;  break;
      case 2: bt = T_SHORT; break;
      case 4: bt = T_INT;   break;
      case 8: bt = T_LONG;  break;
      default: ShouldNotReachHere();
    }
    return bt;
  }

  // Node vector element type accessors
  BasicType elem_bt(Node* n) { return _elem_bt.at(rpo_idx(n)); }
  void set_elem_bt(Node* n, BasicType bt) { _elem_bt.at_put(rpo_idx(n), bt); }
  bool has_valid_elem_bt(Node* n) { return elem_bt(n) != T_ILLEGAL; }

  // Some node check utilities
  bool is_loop_iv(const Node* n) const { return n == _iv; }
  bool is_loop_incr(const Node* n) const { return n == _cl->incr(); }

  bool is_loop_iv_or_incr(const Node* n) const {
    return n == _iv || n == _cl->incr();
  }

  bool is_loop_incr_pattern (const Node* n) const {
    if (n != nullptr && n->is_Add() && n->in(1) == _iv && n->in(2)->is_Con()) {
      const Type* t = n->in(2)->bottom_type();
      return t->is_int()->get_con() == _cl->stride_con();
    }
    return false;
  }

  // Methods for loop vectorizable analysis
  void init(IdealLoopTree* lpt);
  bool collect_loop_nodes();

  bool collect_statements_helper(const Node* node, const uint idx,
                                 Node_List* stmt, Node_List* worklist);
  bool collect_statements();

  bool analyze_vectorizability();
  bool find_vector_element_types();
  bool vector_nodes_implemented();
  bool analyze_loop_body_nodes();

  const TypeVectMask* create_vector_mask_type();

  bool supported_mem_access(MemNode* mem);
  SWPointer* mem_access_to_swpointer(MemNode* mem);
  bool operates_on_array_of_type(Node* node, BasicType bt);

  // Methods for vector masked loop transformation
  Node_List* create_vmask_tree(const TypeVectMask* t_vmask);
  Node* get_vector_input(Node* node, uint idx);
  Node_List* replace_scalar_ops(Node* mask);
  void duplicate_vector_ops(Node_List* vmask_tree, Node_List* s2v_map, int lane_size);
  void adjust_vector_node(Node* vn, Node_List* vmask_tree, int level, int mask_off);
  Node_List* clone_node_list(const Node_List* list);
  void transform_loop(const TypeVectMask* t_vmask);

  // Debug printing
  void trace_msg(Node* n, const char* msg);

 public:
  VectorMaskedLoop(PhaseIdealLoop* phase);
  void try_vectorize_loop(IdealLoopTree* lpt);
};

#endif // SHARE_OPTO_VMASKLOOP_HPP
