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

#include "precompiled.hpp"
#include "opto/addnode.hpp"
#include "opto/convertnode.hpp"
#include "opto/vmaskloop.hpp"

//        L O O P   V E C T O R   M A S K   T R A N S F O R M A T I O N
// ============================================================================

// -------------------------------- Constructor -------------------------------
VectorMaskedLoop::VectorMaskedLoop(PhaseIdealLoop* phase) :
  _phase(phase),
  _igvn(&(phase->_igvn)),
  _arena(phase->C->comp_arena()),

  _lpt(nullptr),
  _cl(nullptr),
  _cle(nullptr),
  _iv(nullptr),

  _core_set(_arena),
  _body_set(_arena),
  _body_nodes(_arena, 32, 0, nullptr),
  _rpo_idx(_arena, 32, 0, 0),
  _elem_bt(_arena, 32, 0, T_ILLEGAL),
  _stmts(_arena, 2, 0, nullptr),
  _swptrs(_arena, 8, 0, nullptr),
  _size_stats(_arena)
{}

// ------------------- Entry function of vector masked loop -------------------
void VectorMaskedLoop::try_vectorize_loop(IdealLoopTree* lpt) {
  assert(UseMaskedLoop, "Option should be enabled");
  assert(lpt->is_counted(), "Loop must be counted");
  assert(lpt->is_innermost(), "Loop must be innermost");

  CountedLoopNode* cl = lpt->_head->as_CountedLoop();
  assert(cl->is_post_loop() && !cl->is_vector_masked(),
         "Current loop should be a post loop and not vector masked");

  if (!cl->is_valid_counted_loop(T_INT)) {
    trace_msg(nullptr, "Loop is not a valid counted loop");
    return;
  }
  if (abs(cl->stride_con()) != 1) {
    trace_msg(nullptr, "Loop has unsupported stride value");
    return;
  }
  if (cl->loopexit()->in(0) != cl) {
    trace_msg(nullptr, "Loop has unsupported control flow");
    return;
  }
  if (cl->back_control()->outcnt() != 1) {
    trace_msg(nullptr, "Loop has node pinned to the backedge");
    return;
  }

  // Init data structures and collect loop nodes
  init(lpt);
  if (!collect_loop_nodes()) return;

  // Collect loop statements and analyze vectorizability
  if (!collect_statements()) return;
  if (!analyze_vectorizability()) return;

  // Try creating a vector mask with the smallest vector element size
  const TypeVectMask* t_vmask = create_vector_mask_type();
  if (t_vmask == nullptr || !t_vmask->isa_vectmask()) return;

  // Transform the loop and set flags
  transform_loop(t_vmask);
  cl->mark_loop_vectorized();
  cl->mark_vector_masked();
  _phase->C->set_max_vector_size(MaxVectorSize);
  trace_msg(nullptr, "Loop is vector masked");
}

// ----------------------------------- Init -----------------------------------
void VectorMaskedLoop::init(IdealLoopTree* lpt) {
  // Set current loop info
  _lpt = lpt;
  _cl = lpt->_head->as_CountedLoop();
  _cle = _cl->loopexit();
  _iv = _cle->phi();

  // Reset data structures
  _core_set.clear();
  _body_set.clear();
  _body_nodes.clear();
  _rpo_idx.clear();
  _elem_bt.clear();
  _stmts.clear();
  _swptrs.clear();
  _size_stats.clear();
}

// ------------------- Loop vectorizable analysis functions -------------------
// Collect loop nodes into an array with reverse postorder for convenience of
// future traversal. Do early bail out if unsupported node is found.
bool VectorMaskedLoop::collect_loop_nodes() {
  ResourceMark rm;

  // Collect 7 (see EMPTY_LOOP_SIZE) core nodes of the loop
  _lpt->collect_loop_core_nodes(_phase, _core_set);

  // Push loop nodes into a node set for fast membership check, also create a
  // temporary index map for RPO visit
  int node_cnt = _lpt->_body.size();
  for (int i = 0; i < node_cnt; i++) {
    Node* n = _lpt->_body.at(i);
    if (n->is_LoadStore() || n->is_RangeCheck() || n->is_Call()) {
      trace_msg(n, "Found unsupported node in the loop");
      return false;
    }
    _body_set.push(n);
    set_rpo_idx(n, i);
  }

  // Visit all loop nodes from the head to create reverse postorder
  VectorSet visited;
  VectorSet post_visited;
  GrowableArray<Node*> stack(node_cnt, 0, nullptr);
  stack.push(_cl);
  int idx = node_cnt - 1;
  while (stack.length() > 0) {
    Node* n = stack.top();
    if (!visited.test(rpo_idx(n))) {
      // Forward arc in graph
      visited.set(rpo_idx(n));
    } else if (!post_visited.test(rpo_idx(n))) {
      // Cross or backward arc in graph
      if (!n->is_memory_phi()) {
        // Push all users in loop for non-mem-phi nodes
        for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
          Node* use = n->fast_out(i);
          if (in_body(use) && !visited.test(rpo_idx(use))) {
            stack.push(use);
          }
        }
      }
      if (n == stack.top()) {
        // Node is still at the top - no additional use is pushed, visit it.
        // Also initialize node info at this time.
        stack.pop();
        assert(idx >= 0, "Is some node visited more than once?");
        _body_nodes.at_put_grow(idx, n);
        _elem_bt.at_put_grow(idx, T_ILLEGAL);
        idx--;
        post_visited.set(rpo_idx(n));
      }
    } else {
      stack.pop();
    }
  }

  // Bail out if loop has unreachable node while traversing from head
  if (idx != -1) {
    trace_msg(nullptr, "Loop has unreachable node while traversing from head");
    return false;
  }
  // Create a real index map for future use
  for (int i = 0; i < _body_nodes.length(); i++) {
    set_rpo_idx(_body_nodes.at(i), i);
  }

#ifndef PRODUCT
  if (TraceMaskedLoop) {
    tty->print_cr("Collected loop nodes in reverse postorder");
    for (int i = 0; i < _body_nodes.length(); i++) {
      tty->print(" rpo=%d\t", i);
      _body_nodes.at(i)->dump();
    }
    tty->cr();
  }
#endif

  return true;
}

// Try including a node's input at specified index into current statement
bool VectorMaskedLoop::collect_statements_helper(
          const Node* node, const uint idx, Node_List* stmt, Node_List* worklist) {
  Node* in = node->in(idx);
  if (stmt->contains(in) || !in_body(in)) {
    // Input is already included in current statement or out of loop
    return true;
  }

  // Check support for special inputs first and then general ones
  if (is_loop_iv_or_incr(in)) {
    // 1) Check the support of loop iv or increment node input
    BasicType bt = statement_bottom_type(stmt);
    bt = is_subword_type(bt) ? bt : T_INT;
    if (VectorNode::is_populate_index_supported(bt)) {
      return true;
    } else {
      trace_msg(in, "Populate index operation is not supported");
      return false;
    }
  } else if (in->is_Phi()) {
    // 2) We don't support phi nodes except the iv phi of the loop and memory
    //    phi's cannot be reached
    trace_msg(in, "Found unsupported phi input");
    return false;
  } else if (in->is_Load()) {
    // 3) Ok to include a load node if it's supported memory access
    if (supported_mem_access(in->as_Load())) {
      stmt->push(in);
      return true;
    } else {
      trace_msg(in, "Found unsupported memory load input");
      return false;
    }
  } else if (VectorNode::is_shift(in) && in_body(in->in(2))) {
    // 4) We don't support shift operations with variant shift count
    trace_msg(in, "Variant shift count is not supported");
    return false;
  } else {
    // 5) For other general inputs, include it and also push it into the
    //    worklist to collect inputs recursively
    worklist->push(in);
    stmt->push(in);
    return true;
  }
}

// Collect lists of nodes that make up loop statements
bool VectorMaskedLoop::collect_statements() {
  // First, initialize each statement from a store node.
  for (int idx = 0; idx < _body_nodes.length(); idx++) {
    Node* node = _body_nodes.at(idx);
    if (node->is_Store() && supported_mem_access(node->as_Store())) {
      // Create a new statement and add the store into its node list
      Node_List* stmt = new Node_List(_arena);
      stmt->push(node);
      _stmts.append(stmt);
    }
  }

  // Do early bail out if no statement is created
  int num_stmts = _stmts.length();
  if (num_stmts == 0) {
    trace_msg(nullptr, "No vectorizable statement is found");
    return false;
  }

  // Then, extend each statement by recursively including input nodes. Bail out
  // if unsupported node is found.
  for (int idx = 0; idx < num_stmts; idx++) {
    Node_List* stmt = _stmts.at(idx);
    assert(stmt->size() == 1, "Each statement should have exactly one node");
    // Create a new worklist and add the initial node of a statement
    Node_List* worklist = new Node_List(_arena);
    worklist->push(stmt->at(0));
    // Continue adding nodes until the worklist is empty
    while (worklist->size() > 0) {
      Node* node = worklist->pop();
      uint start, end;
      VectorNode::vector_operands(node, &start, &end);
      for (uint idx = start; idx < end; idx++) {
        if (!collect_statements_helper(node, idx, stmt, worklist)) {
          return false;
        }
      }
    }
#ifndef PRODUCT
    if (TraceMaskedLoop) {
      tty->print_cr("Nodes in statement [%d] with element type '%s'",
                    idx, type2name(statement_bottom_type(stmt)));
      for (uint i = 0; i < stmt->size(); i++) {
        stmt->at(i)->dump();
      }
      tty->cr();
    }
#endif
  }

  return true;
}

// Analyze loop statements and bail out if any of them is not vectorizable
bool VectorMaskedLoop::analyze_vectorizability() {
  if (!find_vector_element_types()) {
    return false;
  }
  if (!vector_nodes_implemented()) {
    return false;
  }
  // Delegate data dependence check to SWPointer utility
  if (SWPointer::has_potential_dependence(_swptrs)) {
    trace_msg(nullptr, "Potential data dependence is found in the loop");
    return false;
  }
  if (!analyze_loop_body_nodes()) {
    return false;
  }
  return true;
}

// Find element basic type for each vectorization candidate node
bool VectorMaskedLoop::find_vector_element_types() {
  for (int idx = 0; idx < _stmts.length(); idx++) {
    Node_List* stmt = _stmts.at(idx);
    BasicType stmt_bottom_type = statement_bottom_type(stmt);
    bool subword_stmt = is_subword_type(stmt_bottom_type);

    // Record vector element size
    _size_stats.record_size(type2aelembytes(stmt_bottom_type));

    // Set element type for each statement node from bottom to top. Bail out if
    // the pattern is unsupported
    for (int i = stmt->size() - 1; i >= 0; i--) {
      Node* node = stmt->at(i);
      if (node->is_Mem()) {
        // Use memory type as its element basic type for memory node
        BasicType mem_type = node->as_Mem()->memory_type();
        set_elem_bt(node, mem_type);
        if (node->is_Load()) {
          // For load node, check if it has the same vector element size with
          // the bottom type of the statement
          if (!same_element_size(mem_type, stmt_bottom_type)) {
            trace_msg(node, "Vector element size does not match");
            return false;
          }
        }
      } else {
        int opc = node->Opcode();
        if (subword_stmt &&
            (opc == Op_RShiftI || opc == Op_URShiftI ||
             opc == Op_AbsI || opc == Op_ReverseBytesI)) {
          // In any Java arithmetic operation, operands of small integer types
          // (boolean, byte, char & short) should be promoted to int first. For
          // some operations, the compiler has to know the operand's higher
          // order bits, which will be lost in narrowed type. These operations
          // shouldn't be vectorized if the higher order bits info is unknown.
          Node* in1 = node->in(1);
          if (in1->is_Load()) {
            BasicType mem_type = in1->as_Mem()->memory_type();
            set_elem_bt(node, mem_type);
          } else {
            trace_msg(node, "Subword operand does not have precise type");
            return false;
          }
        } else {
          // Otherwise, use signed subword type or the statement's bottom type
          if (subword_stmt) {
            set_elem_bt(node, get_signed_subword_bt(stmt_bottom_type));
          } else {
            BasicType self_type = node->bottom_type()->array_element_basic_type();
            if (!same_element_size(self_type, stmt_bottom_type)) {
              trace_msg(node, "Inconsistent vector element size in one statement");
              return false;
            }
            set_elem_bt(node, self_type);
          }
        }
      }
    }
  }

#ifndef PRODUCT
  if (TraceMaskedLoop) {
    tty->print_cr("Element basic types of nodes in the loop");
    for (int idx = 0; idx < _body_nodes.length(); idx++) {
      Node* node = _body_nodes.at(idx);
      if (has_valid_elem_bt(node)) {
        tty->print(" %s\t", type2name(elem_bt(node)));
        node->dump();
      }
    }
    tty->cr();
  }
#endif

  return true;
}

// Check if all vector operations required are implemented in current backend.
// Bail out if any of the vector op is not implemented.
bool VectorMaskedLoop::vector_nodes_implemented() {
  for (int idx = 0; idx < _stmts.length(); idx++) {
    Node_List* stmt = _stmts.at(idx);
    for (int i = stmt->size() - 1; i >= 0; i--) {
      Node* node = stmt->at(i);
      int opc = node->Opcode();
      BasicType bt = elem_bt(node);
      int vlen = Matcher::max_vector_size(bt);
      if (vlen == 0) {
        // Bail out if vector cannot hold such elements
        return false;
      }
      // We check special convert and min/max ops first and then general ops
      if (VectorNode::is_convert_opcode(opc)) {
        Node* in = node->in(1);
        BasicType in_bt = is_loop_iv_or_incr(in) ? T_INT : elem_bt(in);
        if (in_bt == T_ILLEGAL || !same_element_size(in_bt, bt) ||
            !VectorCastNode::implemented(opc, vlen, in_bt, bt)) {
          trace_msg(node, "Found unimplemented vector cast node");
          return false;
        }
      } else if (VectorNode::is_minmax_opcode(opc) && is_subword_type(bt)) {
        // Java API for Math.min/max operations supports only int, long, float
        // and double types. Bail out for subword min/max operations.
        return false;
      } else {
        int vopc = 0;
        if (node->is_Mem()) {
          assert(node->is_Load() || node->is_Store(), "Must be load or store");
          vopc = node->is_Store() ? Op_StoreVectorMasked : Op_LoadVectorMasked;
          if (!Matcher::match_rule_supported_vector_masked(vopc, vlen, bt)) {
            trace_msg(node, "Vector masked memory access is not implemented");
            return false;
          }
        } else {
          vopc = VectorNode::opcode(opc, bt);
          if (vopc == 0 ||
            !Matcher::match_rule_supported_vector(vopc, vlen, bt)) {
            trace_msg(node, "Vector replacement node is not implemented");
            return false;
          }
        }
      }
    }
  }
  return true;
}

// Find unhandled out-of-loop use of loop body nodes and untracked loop body
// nodes to bail out for complex loops
bool VectorMaskedLoop::analyze_loop_body_nodes() {
  ResourceMark rm;
  VectorSet tracked;
  int n_nodes = _body_nodes.length();
  // 1) Track all vectorization candidates and loop iv phi nodes
  for (int idx = 0; idx < n_nodes; idx++) {
    Node* node = _body_nodes.at(idx);
    if (has_valid_elem_bt(node) || is_loop_iv(node)) {
      tracked.set(idx);
    }
  }
  // 2) Track memory address computing nodes in SWPointer node stacks
  for (int ptridx = 0; ptridx < _swptrs.length(); ptridx++) {
    Node_Stack* nstack = _swptrs.at(ptridx)->node_stack();
    while (nstack->is_nonempty()) {
      Node* node = nstack->node();
      if (in_body(node)) {
        tracked.set(rpo_idx(node));
      }
      nstack->pop();
    }
  }
  // 3) Up to this point, all tracked nodes shouldn't have out-of-loop users
  for (int idx = 0; idx < n_nodes; idx++) {
    Node* node = _body_nodes.at(idx);
    if (node->is_Store()) {
      // Only store nodes are exceptions
      continue;
    }
    if (tracked.test(idx)) {
      for (DUIterator_Fast imax, i = node->fast_outs(imax); i < imax; i++) {
        Node* out = node->fast_out(i);
        if (!in_body(out)) {
          trace_msg(node, "Node has out-of-loop user found");
          return false;
        }
      }
    }
  }
  // 4) Bail out if the loop body has extra node
  for (int idx = 0; idx < n_nodes; idx++) {
    Node* node = _body_nodes.at(idx);
    if (!tracked.test(idx) && !in_core(node) && !node->is_memory_phi()) {
      trace_msg(node, "Found extra loop node in loop body");
      return false;
    }
  }
  return true;
}

// Try creating a vector mask with the smallest vector element size
const TypeVectMask* VectorMaskedLoop::create_vector_mask_type() {
  BasicType vmask_bt = size_to_basic_type(_size_stats.smallest_size());
  int vlen = Matcher::max_vector_size(vmask_bt);
  if (!Matcher::match_rule_supported_vector(Op_LoopVectorMask, vlen, vmask_bt)) {
    // Unable to create vector mask with the vlen & bt on this platform
    return nullptr;
  }
  return (TypeVectMask*) TypeVect::makemask(vmask_bt, vlen);
}

// This checks if memory access node is our supported pattern
bool VectorMaskedLoop::supported_mem_access(MemNode* mem) {
  // First do a quick check by searching existing SWPointer(s)
  for (int idx = 0; idx < _swptrs.length(); idx++) {
    if (_swptrs.at(idx)->mem() == mem) {
      return true;
    }
  }
  // If not found, try creating a new SWPointer and insert it
  SWPointer* ptr = mem_access_to_swpointer(mem);
  if (ptr != nullptr) {
    _swptrs.push(ptr);
    return true;
  }
  return false;
}

// This tries creating an SWPointer object associated to the memory access.
// Return nullptr if it fails or the SWPointer is not valid.
SWPointer* VectorMaskedLoop::mem_access_to_swpointer(MemNode* mem) {
  // Should access memory of a Java primitive value
  BasicType mem_type = mem->memory_type();
  if (!is_java_primitive(mem_type)) {
    trace_msg(mem, "Only memory accesses of primitive types are supported");
    return nullptr;
  }
  // addp: memory address for loading/storing an array element. It should be an
  // AddP node operating on an array of specific type
  Node* addp = mem->in(MemNode::Address);
  if (!addp->is_AddP() || !operates_on_array_of_type(addp, mem_type)) {
    trace_msg(mem, "Memory access has inconsistent type");
    return nullptr;
  }
  // Create a Node_Stack for SWPointer's initial stack
  Node_Stack* nstack = new Node_Stack(_arena, 5);
  nstack->push(addp, 0);
  // addp2: another possible AddP node for array element addressing. It should
  // operate on the same memory type and have the same base with previous AddP.
  Node* addp2 = addp->in(AddPNode::Address);
  if (addp2->is_AddP()) {
    if (!operates_on_array_of_type(addp2, mem_type) ||
        addp->in(AddPNode::Base) != addp2->in(AddPNode::Base)) {
      trace_msg(mem, "Memory access has inconsistent type or base");
      return nullptr;
    }
    nstack->push(addp2, 1);
  }

  // Check supported memory access via SWPointer. It's not supported if
  //  1) The constructed SWPointer is invalid
  //  2) Address is growing down (index scale * loop stride < 0)
  //  3) Memory access scale is different from data size
  //  4) The loop increment node is on the SWPointer's node stack
  SWPointer* ptr = new (_arena) SWPointer(mem, _phase, _lpt, nstack, true);
  if (!ptr->valid()) {
    trace_msg(mem, "Memory access has unsupported address pattern");
    return nullptr;
  }
  int scale_in_bytes = ptr->scale_in_bytes();
  int element_size = type2aelembytes(mem_type);
  if (scale_in_bytes * _cl->stride_con() < 0 ||
      abs(scale_in_bytes) != element_size) {
    trace_msg(mem, "Memory access has unsupported direction or scale");
    return nullptr;
  }
  for (uint i = 0; i < nstack->size(); i++) {
    if (nstack->node_at(i) == _cl->incr()) {
      trace_msg(mem, "Memory access unexpectedly uses loop increment node");
      return nullptr;
    }
  }

  return ptr;
}

// Check if node operates on an array of specific type
bool VectorMaskedLoop::operates_on_array_of_type(Node* node, BasicType bt) {
  const TypeAryPtr* aryptr = node->bottom_type()->isa_aryptr();
  if (aryptr == nullptr) {
    return false;
  }
  BasicType elem_bt = aryptr->elem()->array_element_basic_type();
  return same_type_or_subword_size(elem_bt, bt);
}

// ------------------- Actual loop transformation functions -------------------
// Create a tree of vector masks for use of vectorized operations in the loop
Node_List* VectorMaskedLoop::create_vmask_tree(const TypeVectMask* t_vmask) {
  // Create the root vector mask node from given vector type
  int max_trip_cnt = _cl->trip_count();
  Node* root_vmask = _cl->stride_con() > 0 ?
      new LoopVectorMaskNode(_iv, _cl->limit(), t_vmask, max_trip_cnt) :
      new LoopVectorMaskNode(_cl->limit(), _iv, t_vmask, max_trip_cnt);
  _igvn->register_new_node_with_optimizer(root_vmask);

  // Compute the depth of vector mask tree
  uint small = _size_stats.smallest_size();
  uint large = _size_stats.largest_size();
  uint tree_depth = exact_log2(large) - exact_log2(small) + 1;
  // All vector masks construct a perfect binary tree of "2 ^ depth - 1" nodes
  // We create a list of "2 ^ depth" nodes for easier computation.
  Node_List* vmask_tree = new Node_List(_arena, 1 << tree_depth);
  // The root vector mask is always placed at index 1
  vmask_tree->insert(1, root_vmask);

  // Place extracted vector masks from the root mask
  for (uint lev = 0; lev < tree_depth - 1; lev++) {
    uint idx_start = 1 << lev;
    uint idx_end = 1 << (lev + 1);
    for (uint idx = idx_start; idx < idx_end; idx++) {
      // Calculate children's vector mask type from the parent's type
      Node* parent = vmask_tree->at(idx);
      int parent_size = type2aelembytes(Matcher::vector_element_basic_type(parent));
      BasicType child_bt = size_to_basic_type(parent_size * 2);
      int child_vlen = Matcher::max_vector_size(child_bt);
      const TypeVectMask* t_vmask = (TypeVectMask*) TypeVect::makemask(child_bt, child_vlen);
      // Create left and right child of the parent
      Node* left = new ExtractLowMaskNode(parent, t_vmask);
      _igvn->register_new_node_with_optimizer(left);
      vmask_tree->insert(2 * idx, left);
      Node* right = new ExtractHighMaskNode(parent, t_vmask);
      _igvn->register_new_node_with_optimizer(right);
      vmask_tree->insert(2 * idx + 1, right);
    }
  }

#ifndef PRODUCT
  if (TraceMaskedLoop) {
    tty->print_cr("Generated vector masks in vmask tree");
    for (uint lev = 0; lev < tree_depth; lev++) {
      uint lane_size = 1 << (exact_log2(small) + lev);
      tty->print_cr("Lane_size = %d", lane_size);
      uint idx_start = 1 << lev;
      uint idx_end = 1 << (lev + 1);
      for (uint idx = idx_start; idx < idx_end; idx++) {
        Node* node = vmask_tree->at(idx);
        node->dump();
      }
    }
    tty->cr();
  }
#endif

  return vmask_tree;
}

// Helper method for finding or creating a vector input at specified index
Node* VectorMaskedLoop::get_vector_input(Node* node, uint idx) {
  assert(node != nullptr, "Given node shouldn't be nullptr");
  BasicType bt = elem_bt(node);
  Node* in = node->in(idx);
  assert(in != nullptr, "Input node shouldn't be nullptr");

  // If input is already a vector node, just use it
  if (in->is_Vector() || in->is_LoadVector()) {
    return in;
  }

  // Create a vector input for different scalar input cases
  int vlen = Matcher::max_vector_size(bt);
  if (is_loop_iv_or_incr(in)) {
    // Input is the loop iv or increment node
    BasicType pop_index_bt = is_subword_type(bt) ?
                             get_signed_subword_bt(bt) : T_INT;
    const TypeVect* vt = TypeVect::make(pop_index_bt, vlen);
    Node* n_stride = _igvn->intcon(_cl->stride_con());
    Node* start_index = _iv;
    if (is_loop_incr(in)) {
      start_index = new AddINode(_iv, n_stride);
      _igvn->register_new_node_with_optimizer(start_index);
    }
    Node* popindex = new PopulateIndexNode(start_index, n_stride, vt);
    _igvn->register_new_node_with_optimizer(popindex);
    VectorNode::trace_new_vector(popindex, "VectorMasked");
    return popindex;
  } else {
    // Input is a scalar value not in this loop
    assert(!in_body(in), "Node shouldn't be in this loop");
    if (VectorNode::is_roundopD(node) && idx == 2) {
      // 1) Just return the scalar input
      return in;
    } else {
      // 2) Need replicate the scalar input
      Node* vrep = nullptr;
      if (VectorNode::is_shift(node) && idx == 2) {
        // 2.1) Input is the 2nd (shift count) of left/right shift
        assert(is_integral_type(bt), "Shift operation should work on integers");
        Node* mask_con = _igvn->intcon((bt == T_LONG) ?
                                       (BitsPerLong - 1) : (BitsPerInt - 1));
        Node* mask_op = new AndINode(in, mask_con);
        _igvn->register_new_node_with_optimizer(mask_op);
        vrep = VectorNode::shift_count(node->Opcode(), mask_op, vlen, bt);
      } else if (VectorNode::is_scalar_rotate(node) && idx == 2) {
        // 2.2) Input is the 2nd (rotate shift count) of rotate shift
        Node* conv = in;
        if (bt == T_LONG) {
          conv = new ConvI2LNode(in);
          _igvn->register_new_node_with_optimizer(conv);
        }
        vrep = VectorNode::scalar2vector(conv, vlen, Type::get_const_basic_type(bt));
      } else {
        // 2.3) Other general scalar inputs
        const Type* type = Type::get_const_basic_type(get_signed_subword_bt(bt));
        vrep = VectorNode::scalar2vector(in, vlen, type);
      }
      _igvn->register_new_node_with_optimizer(vrep);
      VectorNode::trace_new_vector(vrep, "VectorMasked");
      return vrep;
    }
  }
}

// Replace scalar nodes in the loop by vector nodes from top to bottom and
// return the node map of scalar to vector replacement. The node map is used
// for vector duplication for larger types.
Node_List* VectorMaskedLoop::replace_scalar_ops(Node* mask) {
  // Create a node map of scalar to vector replacement
  int n_nodes = _body_nodes.length();
  Node_List* s2v_map = new Node_List(_arena, n_nodes);

  // Replace each node with valid element basic type set
  for (int idx = 0; idx < n_nodes; idx++) {
    Node* snode = _body_nodes.at(idx);
    if (has_valid_elem_bt(snode)) {
      Node* vnode;
      int opc = snode->Opcode();
      BasicType bt = elem_bt(snode);
      int vlen = Matcher::max_vector_size(bt);
      if (snode->is_Mem()) {
        Node* ctrl = snode->in(MemNode::Control);
        Node* mem = snode->in(MemNode::Memory);
        Node* addr = snode->in(MemNode::Address);
        const TypePtr* at = snode->as_Mem()->adr_type();
        const TypeVect* vt = TypeVect::make(Type::get_const_basic_type(bt), vlen);
        if (snode->is_Load()) {
          vnode = new LoadVectorMaskedNode(ctrl, mem, addr, at, vt, mask);
        } else {
          assert(snode->is_Store(), "Unexpected memory op");
          Node* val = get_vector_input(snode, MemNode::ValueIn);
          vnode = new StoreVectorMaskedNode(ctrl, mem, addr, val, at, mask);
        }
      } else if (VectorNode::is_convert_opcode(opc)) {
        Node* in = get_vector_input(snode, 1);
        int vopc = VectorCastNode::opcode(opc, in->bottom_type()->is_vect()->element_basic_type());
        vnode = VectorCastNode::make(vopc, in, bt, vlen);
      } else {
        uint start, end;
        VectorNode::vector_operands(snode, &start, &end);
        assert(start == 1, "Start should be 1 for all currently supported ops");
        // The 1st operand is always there
        Node* in1 = get_vector_input(snode, 1);
        // The 2nd operand is optional and may be vector shift count
        Node* in2 = nullptr;
        if (end > 2 || VectorNode::is_shift(snode) || VectorNode::is_roundopD(snode)) {
          in2 = get_vector_input(snode, 2);
        }
        // The 3rd operand is optional
        if (end > 3) {
          Node* in3 = get_vector_input(snode, 3);
          vnode = VectorNode::make(opc, in1, in2, in3, vlen, bt);
        } else {
          vnode = VectorNode::make(opc, in1, in2, vlen, bt);
        }
      }
      VectorNode::trace_new_vector(vnode, "VectorMasked");
      _phase->set_ctrl(vnode, _phase->get_ctrl(snode));
      _igvn->replace_node(snode, _igvn->register_new_node_with_optimizer(vnode, snode));
      s2v_map->map(rpo_idx(snode), vnode);
    }
  }

#ifndef PRODUCT
  if (TraceMaskedLoop) {
    tty->print_cr("Node scalar to vector replacements");
    for (int idx = 0; idx < _body_nodes.length(); idx++) {
      Node* snode = _body_nodes.at(idx);
      if (has_valid_elem_bt(snode)) {
        Node* vnode = s2v_map->at(rpo_idx(snode));
        tty->print(" Scalar:\t");
        snode->dump();
        tty->print("  Vector:\t");
        vnode->dump();
      }
    }
    tty->cr();
  }
#endif

  return s2v_map;
}

// Duplicate vectorized operations with given vector element size
void VectorMaskedLoop::duplicate_vector_ops(
                Node_List* vmask_tree, Node_List* s2v_map, int lane_size) {
  // Compute vector duplication count and the vmask tree level
  int dup_cnt = lane_size / _size_stats.smallest_size();
  int vmask_tree_level = exact_log2(dup_cnt);

  // Collect and clone all vector nodes with given vector element size
  Node_List* clone_list = new Node_List(_arena);
  for (int idx = 0; idx < _stmts.length(); idx++) {
    Node_List* stmt = _stmts.at(idx);
    if (type2aelembytes(statement_bottom_type(stmt)) != lane_size) {
      continue;
    }

    // Collect all nodes to be cloned
    for (uint i = 0; i < stmt->size(); i++) {
      Node* vnode = s2v_map->at(rpo_idx(stmt->at(i)));
      if (!clone_list->contains(vnode)) {
        clone_list->push(vnode);
      }
      // Also include vector operands of populate index nodes, because those
      // nodes also need to be cloned and adjusted
      uint start, end;
      VectorNode::vector_operands(vnode, &start, &end);
      for (uint i = start; i < end; i++) {
        Node* vopd = vnode->in(i);
        if (vopd->Opcode() == Op_PopulateIndex) {
          Node* init_idx = vopd->in(1);
          if (is_loop_iv(init_idx) || is_loop_incr_pattern(init_idx)) {
            if (!clone_list->contains(vopd)) {
              clone_list->push(vopd);
            }
          }
        }
      }
    }
  }

  // Clone "dup_cnt - 1" copies of collected vector nodes and insert the lists
  // of cloned nodes into an array. Also insert the list of the original vector
  // nodes at the array end.
  GrowableArray<Node_List*> vector_copies(_arena, dup_cnt, 0, nullptr);
  for (int i = 0; i < dup_cnt - 1; i++) {
    Node_List* cloned = clone_node_list(clone_list);
    vector_copies.push(cloned);
  }
  vector_copies.push(clone_list);

  // As vector store nodes have phi output, to make adjustment simpler, we use
  // the original list to handle operations at max mask offset "dup_cnt - 1".
  // The cloned lists are for small mask offset from "0" to "dup_cnt - 2".
  Node* prev_store = nullptr;
  for (int level_offset = 0; level_offset < dup_cnt; level_offset++) {
    Node_List* vnodes = vector_copies.at(level_offset);
    for (uint i = 0; i < vnodes->size(); i++) {
      Node* vn = vnodes->at(i);
      // Do general vector node adjustment for the vector nodes
      adjust_vector_node(vn, vmask_tree, vmask_tree_level, level_offset);
      // Do cross-node adjustment for vector store nodes.
      if (vn->is_StoreVector()) {
        // For vector store nodes, we re-connect memory edges to the previous
        // vector store we just iterated
        if (prev_store != nullptr) {
          vn->set_req(MemNode::Memory, prev_store);
        }
        prev_store = vn;
      }
    }
  }

#ifndef PRODUCT
  if (TraceMaskedLoop) {
    tty->print_cr("Duplicated vector nodes with lane size = %d", lane_size);
    for (int level_offset = 0; level_offset < dup_cnt; level_offset++) {
      Node_List* vp = vector_copies.at(level_offset);
      tty->print_cr("Offset = %d", level_offset);
      for (uint i = 0; i < vp->size(); i++) {
        vp->at(i)->dump();
      }
    }
    tty->cr();
  }
#endif
}

// Helper function for general vector node adjustment after duplication
void VectorMaskedLoop::adjust_vector_node(Node* vn, Node_List* vmask_tree,
                                          int vmask_tree_level, int level_offset) {
  Node* vmask = vmask_tree->at((1 << vmask_tree_level) + level_offset);
  BasicType elem_bt = Matcher::vector_element_basic_type(vmask);
  int lane_size = type2aelembytes(elem_bt);
  uint vector_size_in_bytes = Matcher::max_vector_size(T_BYTE);
  assert(Matcher::vector_width_in_bytes(elem_bt) == (int) vector_size_in_bytes,
         "should get the same vector width");
  if (vn->is_Mem()) {
    // 1) For mem accesses, update the mask input, and add additional address
    //    offset if mask offset is non-zero
    vn->set_req(vn->req() - 1, vmask);
    if (level_offset != 0) {
      Node* ptr = vn->in(MemNode::Address);
      Node* base = ptr->in(AddPNode::Base);
      Node* off = _igvn->MakeConX(vector_size_in_bytes * level_offset);
      Node* new_ptr = new AddPNode(base, ptr, off);
      _igvn->register_new_node_with_optimizer(new_ptr, ptr);
      vn->set_req(MemNode::Address, new_ptr);
    }
  } else if (vn->Opcode() == Op_PopulateIndex) {
    // 2) For populate index, update start index for non-zero mask offset
    if (level_offset != 0) {
      int v_stride = vector_size_in_bytes / lane_size * _cl->stride_con();
      Node* idx_off = _igvn->intcon(v_stride * level_offset);
      Node* new_base = new AddINode(vn->in(1), idx_off);
      _igvn->register_new_node_with_optimizer(new_base, vn->in(1));
      vn->set_req(1, new_base);
    }
  }
}

// Helper function for duplicating a subgraph of nodes
Node_List* VectorMaskedLoop::clone_node_list(const Node_List* list) {
  assert(list != nullptr && list->size() > 0, "Should not be empty");
  uint size = list->size();
  Node_List* new_list = new Node_List(_arena, size);
  Node_List* clone_map = new Node_List(_arena, size);
  // Clone each node in the list
  for (uint i = 0; i < size; i++) {
    Node* old = list->at(i);
    Node* new_node = old->clone();
    clone_map->map(old->_idx, new_node);
    _igvn->register_new_node_with_optimizer(new_node, old);
    VectorNode::trace_new_vector(new_node, "VectorMasked");
    new_list->push(new_node);
  }
  // Re-connect input edges to the cloned node
  for (uint i = 0; i < size; i++) {
    Node* new_node = new_list->at(i);
    for (uint j = 0; j < new_node->req(); j++) {
      Node* in = new_node->in(j);
      if (in != nullptr && in->_idx < clone_map->max()) {
        Node* new_in = clone_map->at(in->_idx);
        if (new_in != nullptr) {
          new_node->set_req(j, new_in);
        }
      }
    }
  }
  return new_list;
}

// Entry function of actual vector mask transformation
void VectorMaskedLoop::transform_loop(const TypeVectMask* t_vmask) {
  // Create a tree of vector masks for different vector element sizes
  Node_List* vmask_tree = create_vmask_tree(t_vmask);
  Node* root_vmask = vmask_tree->at(1);

  // Replace vectorization candidate nodes to vector nodes. For now we only
  // generate a single vector node per scalar node. And that the duplication
  // afterwards makes sure that all scalar nodes are "widened" to the same
  // number of elements. The smalles type using a single vector, larger types
  // using multiple (duplicated) vectors per scalar node.
  Node_List* s2v_map = replace_scalar_ops(root_vmask);

  // Duplicate and adjust vector operations with larger vector element sizes
  // which need multiple vectors to process
  int small = _size_stats.smallest_size();
  int large = _size_stats.largest_size();
  for (int lane_size = small * 2; lane_size <= large; lane_size *= 2) {
    if (_size_stats.count_size(lane_size) > 0) {
      duplicate_vector_ops(vmask_tree, s2v_map, lane_size);
    }
  }

  // Update loop increment/decrement to the vector mask true count
  Node* true_cnt = new VectorMaskTrueCountNode(root_vmask, TypeInt::INT);
  _igvn->register_new_node_with_optimizer(true_cnt);
  Node* new_incr;
  if (_cl->stride_con() > 0) {
    new_incr = new AddINode(_iv, true_cnt);
  } else {
    new_incr = new SubINode(_iv, true_cnt);
  }
  _igvn->register_new_node_with_optimizer(new_incr);
  _igvn->replace_node(_cl->incr(), new_incr);
}

// ------------------------------ Debug printing ------------------------------
void VectorMaskedLoop::trace_msg(Node* n, const char* msg) {
#ifndef PRODUCT
  if (TraceMaskedLoop) {
    tty->print_cr("%s", msg);
    if (n != nullptr) {
      n->dump();
    }
  }
#endif
}
