/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "libadt/vectset.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "opto/block.hpp"
#include "opto/c2compiler.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/chaitin.hpp"
#include "opto/machnode.hpp"
#include "opto/opcodes.hpp"
#include "opto/phaseX.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "runtime/deoptimization.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style

// To avoid float value underflow
#define MIN_BLOCK_FREQUENCY 1.e-35f

//----------------------------schedule_node_into_block-------------------------
// Insert node n into block b. Look for projections of n and make sure they
// are in b also.
void PhaseCFG::schedule_node_into_block( Node *n, Block *b ) {
  // Set basic block of n, Add n to b,
  map_node_to_block(n, b);
  b->add_inst(n);

  // After Matching, nearly any old Node may have projections trailing it.
  // These are usually machine-dependent flags.  In any case, they might
  // float to another block below this one.  Move them up.
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node*  use  = n->fast_out(i);
    if (use->is_Proj()) {
      Block* buse = get_block_for_node(use);
      if (buse != b) {              // In wrong block?
        if (buse != nullptr) {
          buse->find_remove(use);   // Remove from wrong block
        }
        map_node_to_block(use, b);
        b->add_inst(use);
      }
    }
  }
}

//----------------------------replace_block_proj_ctrl-------------------------
// Nodes that have is_block_proj() nodes as their control need to use
// the appropriate Region for their actual block as their control since
// the projection will be in a predecessor block.
void PhaseCFG::replace_block_proj_ctrl( Node *n ) {
  const Node *in0 = n->in(0);
  assert(in0 != nullptr, "Only control-dependent");
  const Node *p = in0->is_block_proj();
  if (p != nullptr && p != n) {    // Control from a block projection?
    assert(!n->pinned() || n->is_MachConstantBase(), "only pinned MachConstantBase node is expected here");
    // Find trailing Region
    Block *pb = get_block_for_node(in0); // Block-projection already has basic block
    uint j = 0;
    if (pb->_num_succs != 1) {  // More then 1 successor?
      // Search for successor
      uint max = pb->number_of_nodes();
      assert( max > 1, "" );
      uint start = max - pb->_num_succs;
      // Find which output path belongs to projection
      for (j = start; j < max; j++) {
        if( pb->get_node(j) == in0 )
          break;
      }
      assert( j < max, "must find" );
      // Change control to match head of successor basic block
      j -= start;
    }
    n->set_req(0, pb->_succs[j]->head());
  }
}

bool PhaseCFG::is_dominator(Node* dom_node, Node* node) {
  assert(is_CFG(node) && is_CFG(dom_node), "node and dom_node must be CFG nodes");
  if (dom_node == node) {
    return true;
  }
  Block* d = find_block_for_node(dom_node);
  Block* n = find_block_for_node(node);
  assert(n != nullptr && d != nullptr, "blocks must exist");

  if (d == n) {
    if (dom_node->is_block_start()) {
      return true;
    }
    if (node->is_block_start()) {
      return false;
    }
    if (dom_node->is_block_proj()) {
      return false;
    }
    if (node->is_block_proj()) {
      return true;
    }

    assert(is_control_proj_or_safepoint(node), "node must be control projection or safepoint");
    assert(is_control_proj_or_safepoint(dom_node), "dom_node must be control projection or safepoint");

    // Neither 'node' nor 'dom_node' is a block start or block projection.
    // Check if 'dom_node' is above 'node' in the control graph.
    if (is_dominating_control(dom_node, node)) {
      return true;
    }

#ifdef ASSERT
    // If 'dom_node' does not dominate 'node' then 'node' has to dominate 'dom_node'
    if (!is_dominating_control(node, dom_node)) {
      node->dump();
      dom_node->dump();
      assert(false, "neither dom_node nor node dominates the other");
    }
#endif

    return false;
  }
  return d->dom_lca(n) == d;
}

bool PhaseCFG::is_CFG(Node* n) {
  return n->is_block_proj() || n->is_block_start() || is_control_proj_or_safepoint(n);
}

bool PhaseCFG::is_control_proj_or_safepoint(Node* n) const {
  bool result = (n->is_Mach() && n->as_Mach()->ideal_Opcode() == Op_SafePoint) || (n->is_Proj() && n->as_Proj()->bottom_type() == Type::CONTROL);
  assert(!result || (n->is_Mach() && n->as_Mach()->ideal_Opcode() == Op_SafePoint)
          || (n->is_Proj() && n->as_Proj()->_con == 0), "If control projection, it must be projection 0");
  return result;
}

Block* PhaseCFG::find_block_for_node(Node* n) const {
  if (n->is_block_start() || n->is_block_proj()) {
    return get_block_for_node(n);
  } else {
    // Walk the control graph up if 'n' is not a block start nor a block projection. In this case 'n' must be
    // an unmatched control projection or a not yet matched safepoint precedence edge in the middle of a block.
    assert(is_control_proj_or_safepoint(n), "must be control projection or safepoint");
    Node* ctrl = n->in(0);
    while (!ctrl->is_block_start()) {
      ctrl = ctrl->in(0);
    }
    return get_block_for_node(ctrl);
  }
}

// Walk up the control graph from 'n' and check if 'dom_ctrl' is found.
bool PhaseCFG::is_dominating_control(Node* dom_ctrl, Node* n) {
  Node* ctrl = n->in(0);
  while (!ctrl->is_block_start()) {
    if (ctrl == dom_ctrl) {
      return true;
    }
    ctrl = ctrl->in(0);
  }
  return false;
}


//------------------------------schedule_pinned_nodes--------------------------
// Set the basic block for Nodes pinned into blocks
void PhaseCFG::schedule_pinned_nodes(VectorSet &visited) {
  // Allocate node stack of size C->live_nodes()+8 to avoid frequent realloc
  GrowableArray <Node*> spstack(C->live_nodes() + 8);
  spstack.push(_root);
  while (spstack.is_nonempty()) {
    Node* node = spstack.pop();
    if (!visited.test_set(node->_idx)) { // Test node and flag it as visited
      if (node->pinned() && !has_block(node)) {  // Pinned?  Nail it down!
        assert(node->in(0), "pinned Node must have Control");
        // Before setting block replace block_proj control edge
        replace_block_proj_ctrl(node);
        Node* input = node->in(0);
        while (!input->is_block_start()) {
          input = input->in(0);
        }
        Block* block = get_block_for_node(input); // Basic block of controlling input
        schedule_node_into_block(node, block);
      }

      // If the node has precedence edges (added when CastPP nodes are
      // removed in final_graph_reshaping), fix the control of the
      // node to cover the precedence edges and remove the
      // dependencies.
      Node* n = nullptr;
      for (uint i = node->len()-1; i >= node->req(); i--) {
        Node* m = node->in(i);
        if (m == nullptr) continue;
        assert(is_CFG(m), "must be a CFG node");
        node->rm_prec(i);
        if (n == nullptr) {
          n = m;
        } else {
          assert(is_dominator(n, m) || is_dominator(m, n), "one must dominate the other");
          n = is_dominator(n, m) ? m : n;
        }
      }
      if (n != nullptr) {
        assert(node->in(0), "control should have been set");
        assert(is_dominator(n, node->in(0)) || is_dominator(node->in(0), n), "one must dominate the other");
        if (!is_dominator(n, node->in(0))) {
          node->set_req(0, n);
        }
      }

      // process all inputs that are non null
      for (int i = node->req()-1; i >= 0; --i) {
        if (node->in(i) != nullptr) {
          spstack.push(node->in(i));
        }
      }
    }
  }
}

// Assert that new input b2 is dominated by all previous inputs.
// Check this by by seeing that it is dominated by b1, the deepest
// input observed until b2.
static void assert_dom(Block* b1, Block* b2, Node* n, const PhaseCFG* cfg) {
  if (b1 == nullptr)  return;
  assert(b1->_dom_depth < b2->_dom_depth, "sanity");
  Block* tmp = b2;
  while (tmp != b1 && tmp != nullptr) {
    tmp = tmp->_idom;
  }
  if (tmp != b1) {
#ifdef ASSERT
    // Detected an unschedulable graph.  Print some nice stuff and die.
    tty->print_cr("!!! Unschedulable graph !!!");
    for (uint j=0; j<n->len(); j++) { // For all inputs
      Node* inn = n->in(j); // Get input
      if (inn == nullptr)  continue;  // Ignore null, missing inputs
      Block* inb = cfg->get_block_for_node(inn);
      tty->print("B%d idom=B%d depth=%2d ",inb->_pre_order,
                 inb->_idom ? inb->_idom->_pre_order : 0, inb->_dom_depth);
      inn->dump();
    }
    tty->print("Failing node: ");
    n->dump();
    assert(false, "unschedulable graph");
#endif
    cfg->C->record_failure("unschedulable graph");
  }
}

static Block* find_deepest_input(Node* n, const PhaseCFG* cfg) {
  // Find the last input dominated by all other inputs.
  Block* deepb           = nullptr;     // Deepest block so far
  int    deepb_dom_depth = 0;
  for (uint k = 0; k < n->len(); k++) { // For all inputs
    Node* inn = n->in(k);               // Get input
    if (inn == nullptr)  continue;      // Ignore null, missing inputs
    Block* inb = cfg->get_block_for_node(inn);
    assert(inb != nullptr, "must already have scheduled this input");
    if (deepb_dom_depth < (int) inb->_dom_depth) {
      // The new inb must be dominated by the previous deepb.
      // The various inputs must be linearly ordered in the dom
      // tree, or else there will not be a unique deepest block.
      assert_dom(deepb, inb, n, cfg);
      if (cfg->C->failing()) {
        return nullptr;
      }
      deepb = inb;                      // Save deepest block
      deepb_dom_depth = deepb->_dom_depth;
    }
  }
  assert(deepb != nullptr, "must be at least one input to n");
  return deepb;
}


//------------------------------schedule_early---------------------------------
// Find the earliest Block any instruction can be placed in.  Some instructions
// are pinned into Blocks.  Unpinned instructions can appear in last block in
// which all their inputs occur.
bool PhaseCFG::schedule_early(VectorSet &visited, Node_Stack &roots) {
  // Allocate stack with enough space to avoid frequent realloc
  Node_Stack nstack(roots.size() + 8);
  // _root will be processed among C->top() inputs
  roots.push(C->top(), 0);
  visited.set(C->top()->_idx);

  while (roots.size() != 0) {
    // Use local variables nstack_top_n & nstack_top_i to cache values
    // on stack's top.
    Node* parent_node = roots.node();
    uint  input_index = 0;
    roots.pop();

    while (true) {
      if (input_index == 0) {
        // Fixup some control.  Constants without control get attached
        // to root and nodes that use is_block_proj() nodes should be attached
        // to the region that starts their block.
        const Node* control_input = parent_node->in(0);
        if (control_input != nullptr) {
          replace_block_proj_ctrl(parent_node);
        } else {
          // Is a constant with NO inputs?
          if (parent_node->req() == 1) {
            parent_node->set_req(0, _root);
          }
        }
      }

      // First, visit all inputs and force them to get a block.  If an
      // input is already in a block we quit following inputs (to avoid
      // cycles). Instead we put that Node on a worklist to be handled
      // later (since IT'S inputs may not have a block yet).

      // Assume all n's inputs will be processed
      bool done = true;

      while (input_index < parent_node->len()) {
        Node* in = parent_node->in(input_index++);
        if (in == nullptr) {
          continue;
        }

        int is_visited = visited.test_set(in->_idx);
        if (!has_block(in)) {
          if (is_visited) {
            assert(false, "graph should be schedulable");
            return false;
          }
          // Save parent node and next input's index.
          nstack.push(parent_node, input_index);
          // Process current input now.
          parent_node = in;
          input_index = 0;
          // Not all n's inputs processed.
          done = false;
          break;
        } else if (!is_visited) {
          // Visit this guy later, using worklist
          roots.push(in, 0);
        }
      }

      if (done) {
        // All of n's inputs have been processed, complete post-processing.

        // Some instructions are pinned into a block.  These include Region,
        // Phi, Start, Return, and other control-dependent instructions and
        // any projections which depend on them.
        if (!parent_node->pinned()) {
          // Set earliest legal block.
          Block* earliest_block = find_deepest_input(parent_node, this);
          if (C->failing()) {
            return false;
          }
          map_node_to_block(parent_node, earliest_block);
        } else {
          assert(get_block_for_node(parent_node) == get_block_for_node(parent_node->in(0)), "Pinned Node should be at the same block as its control edge");
        }

        if (nstack.is_empty()) {
          // Finished all nodes on stack.
          // Process next node on the worklist 'roots'.
          break;
        }
        // Get saved parent node and next input's index.
        parent_node = nstack.node();
        input_index = nstack.index();
        nstack.pop();
      }
    }
  }
  return true;
}

//------------------------------dom_lca----------------------------------------
// Find least common ancestor in dominator tree
// LCA is a current notion of LCA, to be raised above 'this'.
// As a convenient boundary condition, return 'this' if LCA is null.
// Find the LCA of those two nodes.
Block* Block::dom_lca(Block* LCA) {
  if (LCA == nullptr || LCA == this)  return this;

  Block* anc = this;
  while (anc->_dom_depth > LCA->_dom_depth)
    anc = anc->_idom;           // Walk up till anc is as high as LCA

  while (LCA->_dom_depth > anc->_dom_depth)
    LCA = LCA->_idom;           // Walk up till LCA is as high as anc

  while (LCA != anc) {          // Walk both up till they are the same
    LCA = LCA->_idom;
    anc = anc->_idom;
  }

  return LCA;
}

//--------------------------raise_LCA_above_use--------------------------------
// We are placing a definition, and have been given a def->use edge.
// The definition must dominate the use, so move the LCA upward in the
// dominator tree to dominate the use.  If the use is a phi, adjust
// the LCA only with the phi input paths which actually use this def.
static Block* raise_LCA_above_use(Block* LCA, Node* use, Node* def, const PhaseCFG* cfg) {
  Block* buse = cfg->get_block_for_node(use);
  if (buse == nullptr) return LCA;   // Unused killing Projs have no use block
  if (!use->is_Phi())  return buse->dom_lca(LCA);
  uint pmax = use->req();       // Number of Phi inputs
  // Why does not this loop just break after finding the matching input to
  // the Phi?  Well...it's like this.  I do not have true def-use/use-def
  // chains.  Means I cannot distinguish, from the def-use direction, which
  // of many use-defs lead from the same use to the same def.  That is, this
  // Phi might have several uses of the same def.  Each use appears in a
  // different predecessor block.  But when I enter here, I cannot distinguish
  // which use-def edge I should find the predecessor block for.  So I find
  // them all.  Means I do a little extra work if a Phi uses the same value
  // more than once.
  for (uint j=1; j<pmax; j++) { // For all inputs
    if (use->in(j) == def) {    // Found matching input?
      Block* pred = cfg->get_block_for_node(buse->pred(j));
      LCA = pred->dom_lca(LCA);
    }
  }
  return LCA;
}

//----------------------------raise_LCA_above_marks----------------------------
// Return a new LCA that dominates LCA and any of its marked predecessors.
// Search all my parents up to 'early' (exclusive), looking for predecessors
// which are marked with the given index.  Return the LCA (in the dom tree)
// of all marked blocks.  If there are none marked, return the original
// LCA.
static Block* raise_LCA_above_marks(Block* LCA, node_idx_t mark, Block* early, const PhaseCFG* cfg) {
  assert(early->dominates(LCA), "precondition failed");
  Block_List worklist;
  worklist.push(LCA);
  while (worklist.size() > 0) {
    Block* mid = worklist.pop();
    if (mid == early)  continue;  // stop searching here

    // Test and set the visited bit.
    if (mid->raise_LCA_visited() == mark)  continue;  // already visited

    // Don't process the current LCA, otherwise the search may terminate early
    if (mid != LCA && mid->raise_LCA_mark() == mark) {
      // Raise the LCA.
      LCA = mid->dom_lca(LCA);
      if (LCA == early)  break;   // stop searching everywhere
      assert(early->dominates(LCA), "unsound LCA update");
      // Resume searching at that point, skipping intermediate levels.
      worklist.push(LCA);
      if (LCA == mid)
        continue; // Don't mark as visited to avoid early termination.
    } else {
      // Keep searching through this block's predecessors.
      for (uint j = 1, jmax = mid->num_preds(); j < jmax; j++) {
        Block* mid_parent = cfg->get_block_for_node(mid->pred(j));
        worklist.push(mid_parent);
      }
    }
    mid->set_raise_LCA_visited(mark);
  }
  return LCA;
}

//--------------------------memory_early_block--------------------------------
// This is a variation of find_deepest_input, the heart of schedule_early.
// Find the "early" block for a load, if we considered only memory and
// address inputs, that is, if other data inputs were ignored.
//
// Because a subset of edges are considered, the resulting block will
// be earlier (at a shallower dom_depth) than the true schedule_early
// point of the node. We compute this earlier block as a more permissive
// site for anti-dependency insertion, but only if subsume_loads is enabled.
static Block* memory_early_block(Node* load, Block* early, const PhaseCFG* cfg) {
  Node* base;
  Node* index;
  Node* store = load->in(MemNode::Memory);
  load->as_Mach()->memory_inputs(base, index);

  assert(base != NodeSentinel && index != NodeSentinel,
         "unexpected base/index inputs");

  Node* mem_inputs[4];
  int mem_inputs_length = 0;
  if (base != nullptr)  mem_inputs[mem_inputs_length++] = base;
  if (index != nullptr) mem_inputs[mem_inputs_length++] = index;
  if (store != nullptr) mem_inputs[mem_inputs_length++] = store;

  // In the comparison below, add one to account for the control input,
  // which may be null, but always takes up a spot in the in array.
  if (mem_inputs_length + 1 < (int) load->req()) {
    // This "load" has more inputs than just the memory, base and index inputs.
    // For purposes of checking anti-dependences, we need to start
    // from the early block of only the address portion of the instruction,
    // and ignore other blocks that may have factored into the wider
    // schedule_early calculation.
    if (load->in(0) != nullptr) mem_inputs[mem_inputs_length++] = load->in(0);

    Block* deepb           = nullptr;        // Deepest block so far
    int    deepb_dom_depth = 0;
    for (int i = 0; i < mem_inputs_length; i++) {
      Block* inb = cfg->get_block_for_node(mem_inputs[i]);
      if (deepb_dom_depth < (int) inb->_dom_depth) {
        // The new inb must be dominated by the previous deepb.
        // The various inputs must be linearly ordered in the dom
        // tree, or else there will not be a unique deepest block.
        assert_dom(deepb, inb, load, cfg);
        if (cfg->C->failing()) {
          return nullptr;
        }
        deepb = inb;                      // Save deepest block
        deepb_dom_depth = deepb->_dom_depth;
      }
    }
    early = deepb;
  }

  return early;
}

// This function is used by raise_above_anti_dependences to find unrelated loads for stores in implicit null checks.
bool PhaseCFG::unrelated_load_in_store_null_block(Node* store, Node* load) {
  // We expect an anti-dependence edge from 'load' to 'store', except when
  // implicit_null_check() has hoisted 'store' above its early block to
  // perform an implicit null check, and 'load' is placed in the null
  // block. In this case it is safe to ignore the anti-dependence, as the
  // null block is only reached if 'store' tries to write to null object and
  // 'load' read from non-null object (there is preceding check for that)
  // These objects can't be the same.
  Block* store_block = get_block_for_node(store);
  Block* load_block = get_block_for_node(load);
  Node* end = store_block->end();
  if (end->is_MachNullCheck() && (end->in(1) == store) && store_block->dominates(load_block)) {
    Node* if_true = end->find_out_with(Op_IfTrue);
    assert(if_true != nullptr, "null check without null projection");
    Node* null_block_region = if_true->find_out_with(Op_Region);
    assert(null_block_region != nullptr, "null check without null region");
    return get_block_for_node(null_block_region) == load_block;
  }
  return false;
}

class DefUseMemStatesQueue : public StackObj {
private:
  class DefUsePair : public StackObj {
  private:
    Node* _def; // memory state
    Node* _use; // use of the memory state that also modifies the memory state

  public:
    DefUsePair(Node* def, Node* use) :
      _def(def), _use(use) {
    }

    DefUsePair() :
      _def(nullptr), _use(nullptr) {
    }

    Node* def() const {
      return _def;
    }

    Node* use() const {
      return _use;
    }
  };

  GrowableArray<DefUsePair> _queue;
  GrowableArray<MergeMemNode*> _worklist_visited; // visited mergemem nodes

  bool already_enqueued(Node* def_mem, PhiNode* use_phi) const {
    // def_mem is one of the inputs of use_phi and at least one input of use_phi is
    // not def_mem. It's however possible that use_phi has def_mem as input multiple
    // times. If that happens, use_phi is recorded as a use of def_mem multiple
    // times as well. When PhaseCFG::raise_above_anti_dependences() goes over
    // uses of def_mem and enqueues them for processing, use_phi would then be
    // enqueued for processing multiple times when it only needs to be
    // processed once. The code below checks if use_phi as a use of def_mem was
    // already enqueued to avoid redundant processing of use_phi.
    int j = _queue.length()-1;
    // If there are any use of def_mem already enqueued, they were enqueued
    // last (all use of def_mem are processed in one go).
    for (; j >= 0; j--) {
      const DefUsePair& def_use_pair = _queue.at(j);
      if (def_use_pair.def() != def_mem) {
        // We're done with the uses of def_mem
        break;
      }
      if (def_use_pair.use() == use_phi) {
        return true;
      }
    }
#ifdef ASSERT
    for (; j >= 0; j--) {
      const DefUsePair& def_use_pair = _queue.at(j);
      assert(def_use_pair.def() != def_mem, "Should be done with the uses of def_mem");
    }
#endif
    return false;
  }

public:
  DefUseMemStatesQueue(ResourceArea* area) {
  }

  void push(Node* def_mem_state, Node* use_mem_state) {
    if (use_mem_state->is_MergeMem()) {
      // Be sure we don't get into combinatorial problems.
      if (!_worklist_visited.append_if_missing(use_mem_state->as_MergeMem())) {
        return; // already on work list; do not repeat
      }
    } else if (use_mem_state->is_Phi()) {
      // A Phi could have the same mem as input multiple times. If that's the case, we don't need to enqueue it
      // more than once. We otherwise allow phis to be repeated; they can merge two relevant states.
      if (already_enqueued(def_mem_state, use_mem_state->as_Phi())) {
        return;
      }
    }

    _queue.push(DefUsePair(def_mem_state, use_mem_state));
  }

  bool is_nonempty() const {
    return _queue.is_nonempty();
  }

  Node* top_def() const {
    return _queue.top().def();
  }

  Node* top_use() const {
    return _queue.top().use();
  }

  void pop() {
    _queue.pop();
  }
};

// Enforce a scheduling of the given 'load' that ensures anti-dependent stores
// do not overwrite the load's input memory state before the load executes.
//
// The given 'load' has a current scheduling range in the dominator tree that
// starts at the load's early block (computed in schedule_early) and ends at
// the given 'LCA' block for the load. However, there may still exist
// anti-dependent stores between the early block and the LCA that overwrite
// memory that the load must witness. For such stores, we must
//
//   1. raise the load's LCA to force the load to (eventually) be scheduled at
//      latest in the store's block, and
//   2. if the load may get scheduled in the store's block, additionally insert
//      an anti-dependence edge (i.e., precedence edge) from the load to the
//      store to ensure LCM schedules the load before the store within the
//      block.
//
// For a given store, we say that the store is on a _distinct_ control-flow
// path relative to the load if there are no paths from early to LCA that go
// through the store's block. Such stores are not anti-dependent, and there is
// no need to update the LCA nor to add anti-dependence edges.
//
// Due to the presence of loops, we must also raise the LCA above
// anti-dependent memory Phis. We defer the details (see later comments in the
// method) and for now look at an example without loops.
//
//          CFG               DOMINATOR TREE
//
//       B1 (early,L)              B1
//       |\________                /\\___
//       |         \              /  \   \
//       B2 (L,S)   \            B2  B7  B6
//      /  \         \           /\\___
//     B3  B4 (S)    B7 (S)     /  \   \
//      \  /         /         B3  B4  B5
//       B5 (LCA,L) /
//        \    ____/
//         \  /
//          B6
//
// Here, the load's scheduling range when calling raise_above_anti_dependences
// is between early and LCA in the dominator tree, i.e., in block B1, B2, or B5
// (indicated with "L"). However, there are a number of stores (indicated with
// "S") that overwrite the memory which the load must witness. First, consider
// the store in B4. We cannot legally schedule the load in B4, so an
// anti-dependence edge is redundant. However, we must raise the LCA above
// B4, which means that the updated LCA is B2. Now, consider the store in B2.
// The LCA is already B2, so we do not need to raise it any further.
// If we, eventually, decide to schedule the load in B2, it could happen that
// LCM decides to place the load after the anti-dependent store in B2.
// Therefore, we now need to add an anti-dependence edge between the load and
// the B2 store, ensuring that the load is scheduled before the store. Finally,
// the store in B7 is on a distinct control-flow path. Therefore, B7 requires
// no action.
//
// The raise_above_anti_dependences method returns the updated LCA and ensures
// there are no anti-dependent stores in any block between the load's early
// block and the updated LCA. Any stores in the updated LCA will have new
// anti-dependence edges back to the load. The caller may schedule the load in
// the updated LCA, or it may hoist the load above the updated LCA, if the
// updated LCA is not the early block.
Block* PhaseCFG::raise_above_anti_dependences(Block* LCA, Node* load, const bool verify) {
  ResourceMark rm;
  assert(load->needs_anti_dependence_check(), "must be a load of some sort");
  assert(LCA != nullptr, "");
  DEBUG_ONLY(Block* LCA_orig = LCA);

  // Compute the alias index.  Loads and stores with different alias indices
  // do not need anti-dependence edges.
  int load_alias_idx = C->get_alias_index(load->adr_type());
#ifdef ASSERT
  assert(Compile::AliasIdxTop <= load_alias_idx && load_alias_idx < C->num_alias_types(), "Invalid alias index");
  if (load_alias_idx == Compile::AliasIdxBot && C->do_aliasing() &&
      (PrintOpto || VerifyAliases ||
       (PrintMiscellaneous && (WizardMode || Verbose)))) {
    // Load nodes should not consume all of memory.
    // Reporting a bottom type indicates a bug in adlc.
    // If some particular type of node validly consumes all of memory,
    // sharpen the preceding "if" to exclude it, so we can catch bugs here.
    tty->print_cr("*** Possible Anti-Dependence Bug:  Load consumes all of memory.");
    load->dump(2);
    if (VerifyAliases)  assert(load_alias_idx != Compile::AliasIdxBot, "");
  }
#endif

  if (!C->alias_type(load_alias_idx)->is_rewritable()) {
    // It is impossible to spoil this load by putting stores before it,
    // because we know that the stores will never update the value
    // which 'load' must witness.
    return LCA;
  }

  node_idx_t load_index = load->_idx;

  // Record the earliest legal placement of 'load', as determined by the unique
  // point in the dominator tree where all memory effects and other inputs are
  // first available (computed by schedule_early). For normal loads, 'early' is
  // the shallowest place (dominator-tree wise) to look for anti-dependences
  // between this load and any store.
  Block* early = get_block_for_node(load);

  // If we are subsuming loads, compute an "early" block that only considers
  // memory or address inputs. This block may be different from the
  // schedule_early block when it is at an even shallower depth in the
  // dominator tree, and allow for a broader discovery of anti-dependences.
  if (C->subsume_loads()) {
    early = memory_early_block(load, early, this);
    if (C->failing()) {
      return nullptr;
    }
  }

  assert(early->dominates(LCA_orig), "precondition failed");

  ResourceArea* area = Thread::current()->resource_area();

  // Bookkeeping of possibly anti-dependent stores that we find outside the
  // early block and that may need anti-dependence edges. Note that stores in
  // non_early_stores are not necessarily dominated by early. The search starts
  // from initial_mem, which can reside in a block that dominates early, and
  // therefore, stores we find may be in blocks that are on completely distinct
  // control-flow paths compared to early. However, in the end, only stores in
  // blocks dominated by early matter. The reason for bookkeeping not only
  // relevant stores is efficiency: we lazily record all possible
  // anti-dependent stores and add anti-dependence edges only to the relevant
  // ones at the very end of this method when we know the final updated LCA.
  Node_List non_early_stores(area);

  // Whether we must raise the LCA after the main worklist loop below.
  bool must_raise_LCA_above_marks = false;

  // The input load uses some memory state (initial_mem).
  Node* initial_mem = load->in(MemNode::Memory);
  // To find anti-dependences we must look for users of the same memory state.
  // To do this, we search the memory graph downwards from initial_mem. During
  // this search, we encounter different types of nodes that we handle
  // according to the following three categories:
  //
  // - MergeMems
  // - Memory-state-modifying nodes (informally referred to as "stores" above
  //   and below)
  // - Memory Phis
  //
  // MergeMems do not modify the memory state. Anti-dependent stores or memory
  // Phis may, however, exist downstream of MergeMems. Therefore, we must
  // permit the search to continue through MergeMems. Stores may raise the LCA
  // and may potentially also require an anti-dependence edge. Memory Phis may
  // raise the LCA but never require anti-dependence edges. See the comments
  // throughout the worklist loop below for further details.
  //
  // It may be useful to think of the anti-dependence search as traversing a
  // tree rooted at initial_mem, with internal nodes of type MergeMem and
  // memory Phis and stores as (potentially repeated) leaves.

  // We don't optimize the memory graph for pinned loads, so we may need to raise the
  // root of our search tree through the corresponding slices of MergeMem nodes to
  // get to the node that really creates the memory state for this slice.
  if (load_alias_idx >= Compile::AliasIdxRaw) {
    while (initial_mem->is_MergeMem()) {
      MergeMemNode* mm = initial_mem->as_MergeMem();
      Node* p = mm->memory_at(load_alias_idx);
      if (p != mm->base_memory()) {
        initial_mem = p;
      } else {
        break;
      }
    }
  }
  // To administer the search, we use a worklist consisting of (def,use)-pairs
  // of memory states, corresponding to edges in the search tree (and edges
  // in the memory graph). We need to keep track of search tree edges in the
  // worklist rather than individual nodes due to memory Phis (see details
  // below).
  DefUseMemStatesQueue worklist(area);
  // We start the search at initial_mem and indicate the search root with the
  // edge (nullptr, initial_mem).
  worklist.push(nullptr, initial_mem);

  // The worklist loop
  while (worklist.is_nonempty()) {
    // Pop the next edge from the worklist
    Node* def_mem_state = worklist.top_def();
    Node* use_mem_state = worklist.top_use();
    worklist.pop();

    // We are either
    // - at the root of the search with the edge (nullptr, initial_mem),
    // - just past initial_mem with the edge (initial_mem, use_mem_state), or
    // - just past a MergeMem with the edge (MergeMem, use_mem_state).
    assert(def_mem_state == nullptr || def_mem_state == initial_mem ||
           def_mem_state->is_MergeMem(),
           "unexpected memory state");

    const uint op = use_mem_state->Opcode();

#ifdef ASSERT
    // CacheWB nodes are peculiar in a sense that they both are anti-dependent and produce memory.
    // Allow them to be treated as a store.
    bool is_cache_wb = false;
    if (use_mem_state->is_Mach()) {
      int ideal_op = use_mem_state->as_Mach()->ideal_Opcode();
      is_cache_wb = (ideal_op == Op_CacheWB);
    }
    assert(!use_mem_state->needs_anti_dependence_check() || is_cache_wb, "no loads");
#endif

    // If we are either at the search root or have found a MergeMem, we step
    // past use_mem_state and populate the search worklist with edges
    // (use_mem_state, child) for use_mem_state's children.
    if (def_mem_state == nullptr // root (exclusive) of tree we are searching
        || op == Op_MergeMem     // internal node of tree we are searching
    ) {
      def_mem_state = use_mem_state;

      for (DUIterator_Fast imax, i = def_mem_state->fast_outs(imax); i < imax; i++) {
        use_mem_state = def_mem_state->fast_out(i);
        if (use_mem_state->needs_anti_dependence_check()) {
          // use_mem_state is also a kind of load (i.e.,
          // needs_anti_dependence_check), and it is not a store nor a memory
          // Phi. Hence, it is not anti-dependent on the load.
          continue;
        }
        worklist.push(def_mem_state, use_mem_state);
      }
      // Nothing more to do for the current (nullptr, initial_mem) or
      // (initial_mem/MergeMem, MergeMem) edge, move on.
      continue;
    }

    assert(!use_mem_state->is_MergeMem(),
           "use_mem_state should be either a store or a memory Phi");

    if (op == Op_MachProj || op == Op_Catch)   continue;

    // Compute the alias index. If the use_mem_state has an alias index
    // different from the load's, it is not anti-dependent. Wide MemBar's
    // are anti-dependent with everything (except immutable memories).
    const TypePtr* adr_type = use_mem_state->adr_type();
    if (!C->can_alias(adr_type, load_alias_idx))  continue;

    // Most slow-path runtime calls do NOT modify Java memory, but
    // they can block and so write Raw memory.
    if (use_mem_state->is_Mach()) {
      MachNode* muse = use_mem_state->as_Mach();
      if (load_alias_idx != Compile::AliasIdxRaw) {
        // Check for call into the runtime using the Java calling
        // convention (and from there into a wrapper); it has no
        // _method.  Can't do this optimization for Native calls because
        // they CAN write to Java memory.
        if (muse->ideal_Opcode() == Op_CallStaticJava) {
          assert(muse->is_MachSafePoint(), "");
          MachSafePointNode* ms = (MachSafePointNode*)muse;
          assert(ms->is_MachCallJava(), "");
          MachCallJavaNode* mcj = (MachCallJavaNode*) ms;
          if (mcj->_method == nullptr) {
            // These runtime calls do not write to Java visible memory
            // (other than Raw) and so are not anti-dependent.
            continue;
          }
        }
        // Same for SafePoints: they read/write Raw but only read otherwise.
        // This is basically a workaround for SafePoints only defining control
        // instead of control + memory.
        if (muse->ideal_Opcode() == Op_SafePoint) {
          continue;
        }
      } else {
        // Some raw memory, such as the load of "top" at an allocation,
        // can be control dependent on the previous safepoint. See
        // comments in GraphKit::allocate_heap() about control input.
        // Inserting an anti-dependence edge between such a safepoint and a use
        // creates a cycle, and will cause a subsequent failure in
        // local scheduling.  (BugId 4919904)
        // (%%% How can a control input be a safepoint and not a projection??)
        if (muse->ideal_Opcode() == Op_SafePoint && load->in(0) == muse) {
          continue;
        }
      }
    }

    // Determine the block of the use_mem_state.
    Block* use_mem_state_block = get_block_for_node(use_mem_state);
    assert(use_mem_state_block != nullptr,
           "unused killing projections skipped above");

    // For efficiency, we take a lazy approach to both raising the LCA and
    // adding anti-dependence edges. In this worklist loop, we only mark blocks
    // which we must raise the LCA above (set_raise_LCA_mark), and keep
    // track of nodes that potentially need anti-dependence edges
    // (non_early_stores). The only exceptions to this are if we
    // immediately see that we have to raise the LCA all the way to the early
    // block, and if we find stores in the early block (which always need
    // anti-dependence edges).
    //
    // After the worklist loop, we perform an efficient combined LCA-raising
    // operation over all marks and only then add anti-dependence edges where
    // strictly necessary according to the new raised LCA.

    if (use_mem_state->is_Phi()) {
      // We have reached a memory Phi node. On our search from initial_mem to
      // the Phi, we have found no anti-dependences (otherwise, we would have
      // already terminated the search along this branch). Consider the example
      // below, indicating a Phi node and its node inputs (we omit the control
      // input).
      //
      //    def_mem_state
      //          |
      //          | ? ?
      //          \ | /
      //           Phi
      //
      // We reached the Phi from def_mem_state and know that, on this
      // particular input, the memory that the load must witness is not
      // overwritten. However, for the Phi's other inputs (? in the
      // illustration), we have no information and must thus conservatively
      // assume that the load's memory is overwritten at and below the Phi.
      //
      // It is impossible to schedule the load before the Phi in
      // the same block as the Phi (use_mem_state_block), and anti-dependence
      // edges are, therefore, redundant. We must, however, find the
      // predecessor block of use_mem_state_block that corresponds to
      // def_mem_state, and raise the LCA above that block. Note that this block
      // is not necessarily def_mem_state's block! See the continuation of our
      // previous example below (now illustrating blocks instead of nodes)
      //
      //    def_mem_state's block
      //          |
      //          |
      //      pred_block
      //          |
      //          |   ?   ?
      //          |   |   |
      //      use_mem_state_block
      //
      // Here, we must raise the LCA above pred_block rather than
      // def_mem_state's block.
      //
      // Do not assert(use_mem_state_block != early, "Phi merging memory after access")
      // PhiNode may be at start of block 'early' with backedge to 'early'
      if (LCA == early) {
        // Don't bother if LCA is already raised all the way
        continue;
      }
      DEBUG_ONLY(bool found_match = false);
      for (uint j = PhiNode::Input, jmax = use_mem_state->req(); j < jmax; j++) {
        if (use_mem_state->in(j) == def_mem_state) {   // Found matching input?
          DEBUG_ONLY(found_match = true);
          Block* pred_block = get_block_for_node(use_mem_state_block->pred(j));
          if (pred_block != early) {
            // Lazily set the LCA mark
            pred_block->set_raise_LCA_mark(load_index);
            must_raise_LCA_above_marks = true;
          } else /* if (pred_block == early) */ {
            // We know already now that we must raise LCA all the way to early.
            LCA = early;
            // This turns off the process of gathering non_early_stores.
          }
        }
      }
      assert(found_match, "no worklist bug");
    } else if (use_mem_state_block != early) {
      // We found an anti-dependent store outside the load's 'early' block. The
      // store may be between the current LCA and the earliest possible block
      // (but it could very well also be on a distinct control-flow path).
      // Lazily set the LCA mark and push to non_early_stores.
      if (LCA == early) {
        // Don't bother if LCA is already raised all the way
        continue;
      }
      if (unrelated_load_in_store_null_block(use_mem_state, load)) {
        continue;
      }
      use_mem_state_block->set_raise_LCA_mark(load_index);
      must_raise_LCA_above_marks = true;
      non_early_stores.push(use_mem_state);
    } else /* if (use_mem_state_block == early) */ {
      // We found an anti-dependent store in the load's 'early' block.
      // Therefore, we know already now that we must raise LCA all the way to
      // early and that we need to add an anti-dependence edge to the store.
      assert(use_mem_state != load->find_exact_control(load->in(0)), "dependence cycle found");
      if (verify) {
        assert(use_mem_state->find_edge(load) != -1 || unrelated_load_in_store_null_block(use_mem_state, load),
               "missing precedence edge");
      } else {
        use_mem_state->add_prec(load);
      }
      LCA = early;
      // This turns off the process of gathering non_early_stores.
    }
  }
  // Worklist is now empty; we have visited all possible anti-dependences.

  // Finished if 'load' must be scheduled in its 'early' block.
  // If we found any stores there, they have already been given
  // anti-dependence edges.
  if (LCA == early) {
    return LCA;
  }

  // We get here only if there are no anti-dependent stores in the load's
  // 'early' block and if no memory Phi has forced LCA to the early block. Now
  // we must raise the LCA above the blocks for all the anti-dependent stores
  // and above the predecessor blocks of anti-dependent memory Phis we reached
  // during the search.
  if (must_raise_LCA_above_marks) {
    LCA = raise_LCA_above_marks(LCA, load->_idx, early, this);
  }

  // If LCA == early at this point, there were no stores that required
  // anti-dependence edges in the early block. Otherwise, we would have eagerly
  // raised the LCA to early already in the worklist loop.
  if (LCA == early) {
    return LCA;
  }

  // The raised LCA block can now be a home to anti-dependent stores for which
  // we still need to add anti-dependence edges, but no LCA predecessor block
  // contains any such stores (otherwise, we would have raised the LCA even
  // higher).
  //
  // The raised LCA will be a lower bound for placing the load, preventing the
  // load from sinking past any block containing a store that may overwrite
  // memory that the load must witness.
  //
  // Now we need to insert the necessary anti-dependence edges from 'load' to
  // each store in the non-early LCA block. We have recorded all such potential
  // stores in non_early_stores.
  //
  // If LCA->raise_LCA_mark() != load_index, it means that we raised the LCA to
  // a block in which we did not find any anti-dependent stores. So, no need to
  // search for any such stores.
  if (LCA->raise_LCA_mark() == load_index) {
    while (non_early_stores.size() > 0) {
      Node* store = non_early_stores.pop();
      Block* store_block = get_block_for_node(store);
      if (store_block == LCA) {
        // Add anti-dependence edge from the load to the store in the non-early
        // LCA.
        assert(store != load->find_exact_control(load->in(0)), "dependence cycle found");
        if (verify) {
          assert(store->find_edge(load) != -1, "missing precedence edge");
        } else {
          store->add_prec(load);
        }
      } else {
        assert(store_block->raise_LCA_mark() == load_index, "block was marked");
      }
    }
  }

  assert(LCA->dominates(LCA_orig), "unsound updated LCA");

  // Return the highest block containing stores; any stores
  // within that block have been given anti-dependence edges.
  return LCA;
}

// This class is used to iterate backwards over the nodes in the graph.

class Node_Backward_Iterator {

private:
  Node_Backward_Iterator();

public:
  // Constructor for the iterator
  Node_Backward_Iterator(Node *root, VectorSet &visited, Node_Stack &stack, PhaseCFG &cfg);

  // Postincrement operator to iterate over the nodes
  Node *next();

private:
  VectorSet   &_visited;
  Node_Stack  &_stack;
  PhaseCFG &_cfg;
};

// Constructor for the Node_Backward_Iterator
Node_Backward_Iterator::Node_Backward_Iterator( Node *root, VectorSet &visited, Node_Stack &stack, PhaseCFG &cfg)
  : _visited(visited), _stack(stack), _cfg(cfg) {
  // The stack should contain exactly the root
  stack.clear();
  stack.push(root, root->outcnt());

  // Clear the visited bits
  visited.clear();
}

// Iterator for the Node_Backward_Iterator
Node *Node_Backward_Iterator::next() {

  // If the _stack is empty, then just return null: finished.
  if ( !_stack.size() )
    return nullptr;

  // I visit unvisited not-anti-dependence users first, then anti-dependent
  // children next. I iterate backwards to support removal of nodes.
  // The stack holds states consisting of 3 values:
  // current Def node, flag which indicates 1st/2nd pass, index of current out edge
  Node *self = (Node*)(((uintptr_t)_stack.node()) & ~1);
  bool iterate_anti_dep = (((uintptr_t)_stack.node()) & 1);
  uint idx = MIN2(_stack.index(), self->outcnt()); // Support removal of nodes.
  _stack.pop();

  // I cycle here when I am entering a deeper level of recursion.
  // The key variable 'self' was set prior to jumping here.
  while( 1 ) {

    _visited.set(self->_idx);

    // Now schedule all uses as late as possible.
    const Node* src = self->is_Proj() ? self->in(0) : self;
    uint src_rpo = _cfg.get_block_for_node(src)->_rpo;

    // Schedule all nodes in a post-order visit
    Node *unvisited = nullptr;  // Unvisited anti-dependent Node, if any

    // Scan for unvisited nodes
    while (idx > 0) {
      // For all uses, schedule late
      Node* n = self->raw_out(--idx); // Use

      // Skip already visited children
      if ( _visited.test(n->_idx) )
        continue;

      // do not traverse backward control edges
      Node *use = n->is_Proj() ? n->in(0) : n;
      uint use_rpo = _cfg.get_block_for_node(use)->_rpo;

      if ( use_rpo < src_rpo )
        continue;

      // Phi nodes always precede uses in a basic block
      if ( use_rpo == src_rpo && use->is_Phi() )
        continue;

      unvisited = n;      // Found unvisited

      // Check for possible-anti-dependent
      // 1st pass: No such nodes, 2nd pass: Only such nodes.
      if (n->needs_anti_dependence_check() == iterate_anti_dep) {
        unvisited = n;      // Found unvisited
        break;
      }
    }

    // Did I find an unvisited not-anti-dependent Node?
    if (!unvisited) {
      if (!iterate_anti_dep) {
        // 2nd pass: Iterate over nodes which needs_anti_dependence_check.
        iterate_anti_dep = true;
        idx = self->outcnt();
        continue;
      }
      break;                  // All done with children; post-visit 'self'
    }

    // Visit the unvisited Node.  Contains the obvious push to
    // indicate I'm entering a deeper level of recursion.  I push the
    // old state onto the _stack and set a new state and loop (recurse).
    _stack.push((Node*)((uintptr_t)self | (uintptr_t)iterate_anti_dep), idx);
    self = unvisited;
    iterate_anti_dep = false;
    idx = self->outcnt();
  } // End recursion loop

  return self;
}

//------------------------------ComputeLatenciesBackwards----------------------
// Compute the latency of all the instructions.
void PhaseCFG::compute_latencies_backwards(VectorSet &visited, Node_Stack &stack) {
#ifndef PRODUCT
  if (trace_opto_pipelining())
    tty->print("\n#---- ComputeLatenciesBackwards ----\n");
#endif

  Node_Backward_Iterator iter((Node *)_root, visited, stack, *this);
  Node *n;

  // Walk over all the nodes from last to first
  while ((n = iter.next())) {
    // Set the latency for the definitions of this instruction
    partial_latency_of_defs(n);
  }
} // end ComputeLatenciesBackwards

//------------------------------partial_latency_of_defs------------------------
// Compute the latency impact of this node on all defs.  This computes
// a number that increases as we approach the beginning of the routine.
void PhaseCFG::partial_latency_of_defs(Node *n) {
  // Set the latency for this instruction
#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("# latency_to_inputs: node_latency[%d] = %d for node", n->_idx, get_latency_for_node(n));
    dump();
  }
#endif

  if (n->is_Proj()) {
    n = n->in(0);
  }

  if (n->is_Root()) {
    return;
  }

  uint nlen = n->len();
  uint use_latency = get_latency_for_node(n);
  uint use_pre_order = get_block_for_node(n)->_pre_order;

  for (uint j = 0; j < nlen; j++) {
    Node *def = n->in(j);

    if (!def || def == n) {
      continue;
    }

    // Walk backwards thru projections
    if (def->is_Proj()) {
      def = def->in(0);
    }

#ifndef PRODUCT
    if (trace_opto_pipelining()) {
      tty->print("#    in(%2d): ", j);
      def->dump();
    }
#endif

    // If the defining block is not known, assume it is ok
    Block *def_block = get_block_for_node(def);
    uint def_pre_order = def_block ? def_block->_pre_order : 0;

    if ((use_pre_order <  def_pre_order) || (use_pre_order == def_pre_order && n->is_Phi())) {
      continue;
    }

    uint delta_latency = n->latency(j);
    uint current_latency = delta_latency + use_latency;

    if (get_latency_for_node(def) < current_latency) {
      set_latency_for_node(def, current_latency);
    }

#ifndef PRODUCT
    if (trace_opto_pipelining()) {
      tty->print_cr("#      %d + edge_latency(%d) == %d -> %d, node_latency[%d] = %d", use_latency, j, delta_latency, current_latency, def->_idx, get_latency_for_node(def));
    }
#endif
  }
}

//------------------------------latency_from_use-------------------------------
// Compute the latency of a specific use
int PhaseCFG::latency_from_use(Node *n, const Node *def, Node *use) {
  // If self-reference, return no latency
  if (use == n || use->is_Root()) {
    return 0;
  }

  uint def_pre_order = get_block_for_node(def)->_pre_order;
  uint latency = 0;

  // If the use is not a projection, then it is simple...
  if (!use->is_Proj()) {
#ifndef PRODUCT
    if (trace_opto_pipelining()) {
      tty->print("#    out(): ");
      use->dump();
    }
#endif

    uint use_pre_order = get_block_for_node(use)->_pre_order;

    if (use_pre_order < def_pre_order)
      return 0;

    if (use_pre_order == def_pre_order && use->is_Phi())
      return 0;

    uint nlen = use->len();
    uint nl = get_latency_for_node(use);

    for ( uint j=0; j<nlen; j++ ) {
      if (use->in(j) == n) {
        // Change this if we want local latencies
        uint ul = use->latency(j);
        uint  l = ul + nl;
        if (latency < l) latency = l;
#ifndef PRODUCT
        if (trace_opto_pipelining()) {
          tty->print_cr("#      %d + edge_latency(%d) == %d -> %d, latency = %d",
                        nl, j, ul, l, latency);
        }
#endif
      }
    }
  } else {
    // This is a projection, just grab the latency of the use(s)
    for (DUIterator_Fast jmax, j = use->fast_outs(jmax); j < jmax; j++) {
      uint l = latency_from_use(use, def, use->fast_out(j));
      if (latency < l) latency = l;
    }
  }

  return latency;
}

//------------------------------latency_from_uses------------------------------
// Compute the latency of this instruction relative to all of it's uses.
// This computes a number that increases as we approach the beginning of the
// routine.
void PhaseCFG::latency_from_uses(Node *n) {
  // Set the latency for this instruction
#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("# latency_from_outputs: node_latency[%d] = %d for node", n->_idx, get_latency_for_node(n));
    dump();
  }
#endif
  uint latency=0;
  const Node *def = n->is_Proj() ? n->in(0): n;

  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    uint l = latency_from_use(n, def, n->fast_out(i));

    if (latency < l) latency = l;
  }

  set_latency_for_node(n, latency);
}

//------------------------------is_cheaper_block-------------------------
// Check if a block between early and LCA block of uses is cheaper by
// frequency-based policy, latency-based policy and random-based policy
bool PhaseCFG::is_cheaper_block(Block* LCA, Node* self, uint target_latency,
                                uint end_latency, double least_freq,
                                int cand_cnt, bool in_latency) {
  if (StressGCM) {
    // Should be randomly accepted in stress mode
    return C->randomized_select(cand_cnt);
  }

  const double delta = 1 + PROB_UNLIKELY_MAG(4);

  // Better Frequency. Add a small delta to the comparison to not needlessly
  // hoist because of, e.g., small numerical inaccuracies.
  if (LCA->_freq * delta < least_freq) {
    return true;
  }

  // Otherwise, choose with latency
  if (!in_latency                     &&  // No block containing latency
      LCA->_freq < least_freq * delta &&  // No worse frequency
      target_latency >= end_latency   &&  // within latency range
      !self->is_iteratively_computed()    // But don't hoist IV increments
            // because they may end up above other uses of their phi forcing
            // their result register to be different from their input.
  ) {
    return true;
  }

  return false;
}

//------------------------------hoist_to_cheaper_block-------------------------
// Pick a block for node self, between early and LCA block of uses, that is a
// cheaper alternative to LCA.
Block* PhaseCFG::hoist_to_cheaper_block(Block* LCA, Block* early, Node* self) {
  Block* least       = LCA;
  double least_freq  = least->_freq;
  uint target        = get_latency_for_node(self);
  uint start_latency = get_latency_for_node(LCA->head());
  uint end_latency   = get_latency_for_node(LCA->get_node(LCA->end_idx()));
  bool in_latency    = (target <= start_latency);
  const Block* root_block = get_block_for_node(_root);

  // Turn off latency scheduling if scheduling is just plain off
  if (!C->do_scheduling())
    in_latency = true;

  // Do not hoist (to cover latency) instructions which target a
  // single register.  Hoisting stretches the live range of the
  // single register and may force spilling.
  MachNode* mach = self->is_Mach() ? self->as_Mach() : nullptr;
  if (mach && mach->out_RegMask().is_bound1() && mach->out_RegMask().is_NotEmpty())
    in_latency = true;

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("# Find cheaper block for latency %d: ", get_latency_for_node(self));
    self->dump();
    tty->print_cr("#   B%d: start latency for [%4d]=%d, end latency for [%4d]=%d, freq=%g",
      LCA->_pre_order,
      LCA->head()->_idx,
      start_latency,
      LCA->get_node(LCA->end_idx())->_idx,
      end_latency,
      least_freq);
  }
#endif

  int cand_cnt = 0;  // number of candidates tried

  // Walk up the dominator tree from LCA (Lowest common ancestor) to
  // the earliest legal location. Capture the least execution frequency,
  // or choose a random block if -XX:+StressGCM, or using latency-based policy
  while (LCA != early) {
    LCA = LCA->_idom;         // Follow up the dominator tree

    if (LCA == nullptr) {
      // Bailout without retry
      assert(false, "graph should be schedulable");
      C->record_method_not_compilable("late schedule failed: LCA is null");
      return least;
    }

    // Don't hoist machine instructions to the root basic block
    if (mach && LCA == root_block)
      break;

    if (self->is_memory_writer() &&
        (LCA->_loop->depth() > early->_loop->depth())) {
      // LCA is an invalid placement for a memory writer: choosing it would
      // cause memory interference, as illustrated in schedule_late().
      continue;
    }
    verify_memory_writer_placement(LCA, self);

    uint start_lat = get_latency_for_node(LCA->head());
    uint end_idx   = LCA->end_idx();
    uint end_lat   = get_latency_for_node(LCA->get_node(end_idx));
    double LCA_freq = LCA->_freq;
#ifndef PRODUCT
    if (trace_opto_pipelining()) {
      tty->print_cr("#   B%d: start latency for [%4d]=%d, end latency for [%4d]=%d, freq=%g",
        LCA->_pre_order, LCA->head()->_idx, start_lat, end_idx, end_lat, LCA_freq);
    }
#endif
    cand_cnt++;
    if (is_cheaper_block(LCA, self, target, end_lat, least_freq, cand_cnt, in_latency)) {
      least = LCA;            // Found cheaper block
      least_freq = LCA_freq;
      start_latency = start_lat;
      end_latency = end_lat;
      if (target <= start_lat)
        in_latency = true;
    }
  }

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print_cr("#  Choose block B%d with start latency=%d and freq=%g",
      least->_pre_order, start_latency, least_freq);
  }
#endif

  // See if the latency needs to be updated
  if (target < end_latency) {
#ifndef PRODUCT
    if (trace_opto_pipelining()) {
      tty->print_cr("#  Change latency for [%4d] from %d to %d", self->_idx, target, end_latency);
    }
#endif
    set_latency_for_node(self, end_latency);
    partial_latency_of_defs(self);
  }

  return least;
}


//------------------------------schedule_late-----------------------------------
// Now schedule all codes as LATE as possible.  This is the LCA in the
// dominator tree of all USES of a value.  Pick the block with the least
// loop nesting depth that is lowest in the dominator tree.
extern const char must_clone[];
void PhaseCFG::schedule_late(VectorSet &visited, Node_Stack &stack) {
#ifndef PRODUCT
  if (trace_opto_pipelining())
    tty->print("\n#---- schedule_late ----\n");
#endif

  Node_Backward_Iterator iter((Node *)_root, visited, stack, *this);
  Node *self;

  // Walk over all the nodes from last to first
  while ((self = iter.next())) {
    Block* early = get_block_for_node(self); // Earliest legal placement

    if (self->is_top()) {
      // Top node goes in bb #2 with other constants.
      // It must be special-cased, because it has no out edges.
      early->add_inst(self);
      continue;
    }

    // No uses, just terminate
    if (self->outcnt() == 0) {
      assert(self->is_MachProj(), "sanity");
      continue;                   // Must be a dead machine projection
    }

    // If node is pinned in the block, then no scheduling can be done.
    if( self->pinned() )          // Pinned in block?
      continue;

#ifdef ASSERT
    // Assert that memory writers (e.g. stores) have a "home" block (the block
    // given by their control input), and that this block corresponds to their
    // earliest possible placement. This guarantees that
    // hoist_to_cheaper_block() will always have at least one valid choice.
    if (self->is_memory_writer()) {
      assert(find_block_for_node(self->in(0)) == early,
             "The home of a memory writer must also be its earliest placement");
    }
#endif

    MachNode* mach = self->is_Mach() ? self->as_Mach() : nullptr;
    if (mach) {
      switch (mach->ideal_Opcode()) {
      case Op_CreateEx:
        // Don't move exception creation
        early->add_inst(self);
        continue;
        break;
      case Op_CheckCastPP: {
        // Don't move CheckCastPP nodes away from their input, if the input
        // is a rawptr (5071820).
        Node *def = self->in(1);
        if (def != nullptr && def->bottom_type()->base() == Type::RawPtr) {
          early->add_inst(self);
#ifdef ASSERT
          _raw_oops.push(def);
#endif
          continue;
        }
        break;
      }
      default:
        break;
      }
      if (C->has_irreducible_loop() && self->is_memory_writer()) {
        // If the CFG is irreducible, place memory writers in their home block.
        // This prevents hoist_to_cheaper_block() from accidentally placing such
        // nodes into deeper loops, as in the following example:
        //
        // Home placement of store in B1 (loop L1):
        //
        // B1 (L1):
        //   m1 <- ..
        //   m2 <- store m1, ..
        // B2 (L2):
        //   jump B2
        // B3 (L1):
        //   .. <- .. m2, ..
        //
        // Wrong "hoisting" of store to B2 (in loop L2, child of L1):
        //
        // B1 (L1):
        //   m1 <- ..
        // B2 (L2):
        //   m2 <- store m1, ..
        //   # Wrong: m1 and m2 interfere at this point.
        //   jump B2
        // B3 (L1):
        //   .. <- .. m2, ..
        //
        // This "hoist inversion" can happen due to different factors such as
        // inaccurate estimation of frequencies for irreducible CFGs, and loops
        // with always-taken exits in reducible CFGs. In the reducible case,
        // hoist inversion is prevented by discarding invalid blocks (those in
        // deeper loops than the home block). In the irreducible case, the
        // invalid blocks cannot be identified due to incomplete loop nesting
        // information, hence a conservative solution is taken.
#ifndef PRODUCT
        if (trace_opto_pipelining()) {
          tty->print_cr("# Irreducible loops: schedule in home block B%d:",
                        early->_pre_order);
          self->dump();
        }
#endif
        schedule_node_into_block(self, early);
        continue;
      }
    }

    // Gather LCA of all uses
    Block *LCA = nullptr;
    {
      for (DUIterator_Fast imax, i = self->fast_outs(imax); i < imax; i++) {
        // For all uses, find LCA
        Node* use = self->fast_out(i);
        LCA = raise_LCA_above_use(LCA, use, self, this);
      }
      guarantee(LCA != nullptr, "There must be a LCA");
    }  // (Hide defs of imax, i from rest of block.)

    // Place temps in the block of their use.  This isn't a
    // requirement for correctness but it reduces useless
    // interference between temps and other nodes.
    if (mach != nullptr && mach->is_MachTemp()) {
      map_node_to_block(self, LCA);
      LCA->add_inst(self);
      continue;
    }

    // Check if 'self' could be anti-dependent on memory
    if (self->needs_anti_dependence_check()) {
      // Hoist LCA above possible-defs and insert anti-dependences to
      // defs in new LCA block.
      LCA = raise_above_anti_dependences(LCA, self);
      if (C->failing()) {
        return;
      }
    }

    if (early->_dom_depth > LCA->_dom_depth) {
      // Somehow the LCA has moved above the earliest legal point.
      // (One way this can happen is via memory_early_block.)
      if (C->subsume_loads() == true && !C->failing()) {
        // Retry with subsume_loads == false
        // If this is the first failure, the sentinel string will "stick"
        // to the Compile object, and the C2Compiler will see it and retry.
        C->record_failure(C2Compiler::retry_no_subsuming_loads());
      } else {
        // Bailout without retry when (early->_dom_depth > LCA->_dom_depth)
        assert(C->failure_is_artificial(), "graph should be schedulable");
        C->record_method_not_compilable("late schedule failed: incorrect graph" DEBUG_ONLY(COMMA true));
      }
      return;
    }

    if (self->is_memory_writer()) {
      // If the LCA of a memory writer is a descendant of its home loop, hoist
      // it into a valid placement.
      while (LCA->_loop->depth() > early->_loop->depth()) {
        LCA = LCA->_idom;
      }
      assert(LCA != nullptr, "a valid LCA must exist");
      verify_memory_writer_placement(LCA, self);
    }

    // If there is no opportunity to hoist, then we're done.
    // In stress mode, try to hoist even the single operations.
    bool try_to_hoist = StressGCM || (LCA != early);

    // Must clone guys stay next to use; no hoisting allowed.
    // Also cannot hoist guys that alter memory or are otherwise not
    // allocatable (hoisting can make a value live longer, leading to
    // anti and output dependency problems which are normally resolved
    // by the register allocator giving everyone a different register).
    if (mach != nullptr && must_clone[mach->ideal_Opcode()])
      try_to_hoist = false;

    Block* late = nullptr;
    if (try_to_hoist) {
      // Now find the block with the least execution frequency.
      // Start at the latest schedule and work up to the earliest schedule
      // in the dominator tree.  Thus the Node will dominate all its uses.
      late = hoist_to_cheaper_block(LCA, early, self);
    } else {
      // Just use the LCA of the uses.
      late = LCA;
    }

    // Put the node into target block
    schedule_node_into_block(self, late);

#ifdef ASSERT
    if (self->needs_anti_dependence_check()) {
      // since precedence edges are only inserted when we're sure they
      // are needed make sure that after placement in a block we don't
      // need any new precedence edges.
      verify_anti_dependences(late, self);
    }
#endif
  } // Loop until all nodes have been visited

} // end ScheduleLate

//------------------------------GlobalCodeMotion-------------------------------
void PhaseCFG::global_code_motion() {
  ResourceMark rm;

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("\n---- Start GlobalCodeMotion ----\n");
  }
#endif

  // Initialize the node to block mapping for things on the proj_list
  for (uint i = 0; i < _matcher.number_of_projections(); i++) {
    unmap_node_from_block(_matcher.get_projection(i));
  }

  // Set the basic block for Nodes pinned into blocks
  VectorSet visited;
  schedule_pinned_nodes(visited);

  // Find the earliest Block any instruction can be placed in.  Some
  // instructions are pinned into Blocks.  Unpinned instructions can
  // appear in last block in which all their inputs occur.
  visited.clear();
  Node_Stack stack((C->live_nodes() >> 2) + 16); // pre-grow
  if (!schedule_early(visited, stack)) {
    // Bailout without retry
    assert(C->failure_is_artificial(), "early schedule failed");
    C->record_method_not_compilable("early schedule failed" DEBUG_ONLY(COMMA true));
    return;
  }

  // Build Def-Use edges.
  // Compute the latency information (via backwards walk) for all the
  // instructions in the graph
  _node_latency = new GrowableArray<uint>(); // resource_area allocation

  if (C->do_scheduling()) {
    compute_latencies_backwards(visited, stack);
  }

  // Now schedule all codes as LATE as possible.  This is the LCA in the
  // dominator tree of all USES of a value.  Pick the block with the least
  // loop nesting depth that is lowest in the dominator tree.
  // ( visited.clear() called in schedule_late()->Node_Backward_Iterator() )
  schedule_late(visited, stack);
  if (C->failing()) {
    return;
  }

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("\n---- Detect implicit null checks ----\n");
  }
#endif

  // Detect implicit-null-check opportunities.  Basically, find null checks
  // with suitable memory ops nearby.  Use the memory op to do the null check.
  // I can generate a memory op if there is not one nearby.
  if (C->is_method_compilation()) {
    // By reversing the loop direction we get a very minor gain on mpegaudio.
    // Feel free to revert to a forward loop for clarity.
    // for( int i=0; i < (int)matcher._null_check_tests.size(); i+=2 ) {
    for (int i = _matcher._null_check_tests.size() - 2; i >= 0; i -= 2) {
      Node* proj = _matcher._null_check_tests[i];
      Node* val  = _matcher._null_check_tests[i + 1];
      Block* block = get_block_for_node(proj);
      implicit_null_check(block, proj, val, C->allowed_deopt_reasons());
      // The implicit_null_check will only perform the transformation
      // if the null branch is truly uncommon, *and* it leads to an
      // uncommon trap.  Combined with the too_many_traps guards
      // above, this prevents SEGV storms reported in 6366351,
      // by recompiling offending methods without this optimization.
      if (C->failing()) {
        return;
      }
    }
  }

  bool block_size_threshold_ok = false;
  intptr_t *recalc_pressure_nodes = nullptr;
  if (OptoRegScheduling) {
    for (uint i = 0; i < number_of_blocks(); i++) {
      Block* block = get_block(i);
      if (block->number_of_nodes() > 10) {
        block_size_threshold_ok = true;
        break;
      }
    }
  }

  // Enabling the scheduler for register pressure plus finding blocks of size to schedule for it
  // is key to enabling this feature.
  PhaseChaitin regalloc(C->unique(), *this, _matcher, true);
  ResourceArea live_arena(mtCompiler, Arena::Tag::tag_reglive);      // Arena for liveness
  ResourceMark rm_live(&live_arena);
  PhaseLive live(*this, regalloc._lrg_map.names(), &live_arena, true);
  PhaseIFG ifg(&live_arena);
  if (OptoRegScheduling && block_size_threshold_ok) {
    regalloc.mark_ssa();
    Compile::TracePhase tp(_t_computeLive);
    rm_live.reset_to_mark();           // Reclaim working storage
    IndexSet::reset_memory(C, &live_arena);
    uint node_size = regalloc._lrg_map.max_lrg_id();
    ifg.init(node_size); // Empty IFG
    regalloc.set_ifg(ifg);
    regalloc.set_live(live);
    regalloc.gather_lrg_masks(false);    // Collect LRG masks
    live.compute(node_size); // Compute liveness

    recalc_pressure_nodes = NEW_RESOURCE_ARRAY(intptr_t, node_size);
    for (uint i = 0; i < node_size; i++) {
      recalc_pressure_nodes[i] = 0;
    }
  }
  _regalloc = &regalloc;

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("\n---- Start Local Scheduling ----\n");
  }
#endif

  // Schedule locally.  Right now a simple topological sort.
  // Later, do a real latency aware scheduler.
  GrowableArray<int> ready_cnt(C->unique(), C->unique(), -1);
  visited.reset();
  for (uint i = 0; i < number_of_blocks(); i++) {
    Block* block = get_block(i);
    if (!schedule_local(block, ready_cnt, visited, recalc_pressure_nodes)) {
      if (!C->failure_reason_is(C2Compiler::retry_no_subsuming_loads())) {
        assert(C->failure_is_artificial(), "local schedule failed");
        C->record_method_not_compilable("local schedule failed" DEBUG_ONLY(COMMA true));
      }
      _regalloc = nullptr;
      return;
    }
  }
  _regalloc = nullptr;

  // If we inserted any instructions between a Call and his CatchNode,
  // clone the instructions on all paths below the Catch.
  for (uint i = 0; i < number_of_blocks(); i++) {
    Block* block = get_block(i);
    call_catch_cleanup(block);
    if (C->failing()) {
      return;
    }
  }

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("\n---- After GlobalCodeMotion ----\n");
    for (uint i = 0; i < number_of_blocks(); i++) {
      Block* block = get_block(i);
      block->dump();
    }
  }
#endif
  // Dead.
  _node_latency = (GrowableArray<uint> *)((intptr_t)0xdeadbeef);
}

bool PhaseCFG::do_global_code_motion() {

  build_dominator_tree();
  if (C->failing()) {
    return false;
  }

  NOT_PRODUCT( C->verify_graph_edges(); )

  estimate_block_frequency();

  global_code_motion();

  if (C->failing()) {
    return false;
  }

  return true;
}

//------------------------------Estimate_Block_Frequency-----------------------
// Estimate block frequencies based on IfNode probabilities.
void PhaseCFG::estimate_block_frequency() {

  // Force conditional branches leading to uncommon traps to be unlikely,
  // not because we get to the uncommon_trap with less relative frequency,
  // but because an uncommon_trap typically causes a deopt, so we only get
  // there once.
  if (C->do_freq_based_layout()) {
    Block_List worklist;
    Block* root_blk = get_block(0);
    for (uint i = 1; i < root_blk->num_preds(); i++) {
      Block *pb = get_block_for_node(root_blk->pred(i));
      if (pb->has_uncommon_code()) {
        worklist.push(pb);
      }
    }
    while (worklist.size() > 0) {
      Block* uct = worklist.pop();
      if (uct == get_root_block()) {
        continue;
      }
      for (uint i = 1; i < uct->num_preds(); i++) {
        Block *pb = get_block_for_node(uct->pred(i));
        if (pb->_num_succs == 1) {
          worklist.push(pb);
        } else if (pb->num_fall_throughs() == 2) {
          pb->update_uncommon_branch(uct);
        }
      }
    }
  }

  // Create the loop tree and calculate loop depth.
  _root_loop = create_loop_tree();
  _root_loop->compute_loop_depth(0);

  // Compute block frequency of each block, relative to a single loop entry.
  _root_loop->compute_freq();

  // Adjust all frequencies to be relative to a single method entry
  _root_loop->_freq = 1.0;
  _root_loop->scale_freq();

  // Save outmost loop frequency for LRG frequency threshold
  _outer_loop_frequency = _root_loop->outer_loop_freq();

  // force paths ending at uncommon traps to be infrequent
  if (!C->do_freq_based_layout()) {
    Block_List worklist;
    Block* root_blk = get_block(0);
    for (uint i = 1; i < root_blk->num_preds(); i++) {
      Block *pb = get_block_for_node(root_blk->pred(i));
      if (pb->has_uncommon_code()) {
        worklist.push(pb);
      }
    }
    while (worklist.size() > 0) {
      Block* uct = worklist.pop();
      uct->_freq = PROB_MIN;
      for (uint i = 1; i < uct->num_preds(); i++) {
        Block *pb = get_block_for_node(uct->pred(i));
        if (pb->_num_succs == 1 && pb->_freq > PROB_MIN) {
          worklist.push(pb);
        }
      }
    }
  }

#ifdef ASSERT
  for (uint i = 0; i < number_of_blocks(); i++) {
    Block* b = get_block(i);
    assert(b->_freq >= MIN_BLOCK_FREQUENCY, "Register Allocator requires meaningful block frequency");
  }
#endif

#ifndef PRODUCT
  if (PrintCFGBlockFreq) {
    tty->print_cr("CFG Block Frequencies");
    _root_loop->dump_tree();
    if (Verbose) {
      tty->print_cr("PhaseCFG dump");
      dump();
      tty->print_cr("Node dump");
      _root->dump(99999);
    }
  }
#endif
}

//----------------------------create_loop_tree--------------------------------
// Create a loop tree from the CFG
CFGLoop* PhaseCFG::create_loop_tree() {

#ifdef ASSERT
  assert(get_block(0) == get_root_block(), "first block should be root block");
  for (uint i = 0; i < number_of_blocks(); i++) {
    Block* block = get_block(i);
    // Check that _loop field are clear...we could clear them if not.
    assert(block->_loop == nullptr, "clear _loop expected");
    // Sanity check that the RPO numbering is reflected in the _blocks array.
    // It doesn't have to be for the loop tree to be built, but if it is not,
    // then the blocks have been reordered since dom graph building...which
    // may question the RPO numbering
    assert(block->_rpo == i, "unexpected reverse post order number");
  }
#endif

  int idct = 0;
  CFGLoop* root_loop = new CFGLoop(idct++);

  Block_List worklist;

  // Assign blocks to loops
  for(uint i = number_of_blocks() - 1; i > 0; i-- ) { // skip Root block
    Block* block = get_block(i);

    if (block->head()->is_Loop()) {
      Block* loop_head = block;
      assert(loop_head->num_preds() - 1 == 2, "loop must have 2 predecessors");
      Node* tail_n = loop_head->pred(LoopNode::LoopBackControl);
      Block* tail = get_block_for_node(tail_n);

      // Defensively filter out Loop nodes for non-single-entry loops.
      // For all reasonable loops, the head occurs before the tail in RPO.
      if (i <= tail->_rpo) {

        // The tail and (recursive) predecessors of the tail
        // are made members of a new loop.

        assert(worklist.size() == 0, "nonempty worklist");
        CFGLoop* nloop = new CFGLoop(idct++);
        assert(loop_head->_loop == nullptr, "just checking");
        loop_head->_loop = nloop;
        // Add to nloop so push_pred() will skip over inner loops
        nloop->add_member(loop_head);
        nloop->push_pred(loop_head, LoopNode::LoopBackControl, worklist, this);

        while (worklist.size() > 0) {
          Block* member = worklist.pop();
          if (member != loop_head) {
            for (uint j = 1; j < member->num_preds(); j++) {
              nloop->push_pred(member, j, worklist, this);
            }
          }
        }
      }
    }
  }

  // Create a member list for each loop consisting
  // of both blocks and (immediate child) loops.
  for (uint i = 0; i < number_of_blocks(); i++) {
    Block* block = get_block(i);
    CFGLoop* lp = block->_loop;
    if (lp == nullptr) {
      // Not assigned to a loop. Add it to the method's pseudo loop.
      block->_loop = root_loop;
      lp = root_loop;
    }
    if (lp == root_loop || block != lp->head()) { // loop heads are already members
      lp->add_member(block);
    }
    if (lp != root_loop) {
      if (lp->parent() == nullptr) {
        // Not a nested loop. Make it a child of the method's pseudo loop.
        root_loop->add_nested_loop(lp);
      }
      if (block == lp->head()) {
        // Add nested loop to member list of parent loop.
        lp->parent()->add_member(lp);
      }
    }
  }

  return root_loop;
}

//------------------------------push_pred--------------------------------------
void CFGLoop::push_pred(Block* blk, int i, Block_List& worklist, PhaseCFG* cfg) {
  Node* pred_n = blk->pred(i);
  Block* pred = cfg->get_block_for_node(pred_n);
  CFGLoop *pred_loop = pred->_loop;
  if (pred_loop == nullptr) {
    // Filter out blocks for non-single-entry loops.
    // For all reasonable loops, the head occurs before the tail in RPO.
    if (pred->_rpo > head()->_rpo) {
      pred->_loop = this;
      worklist.push(pred);
    }
  } else if (pred_loop != this) {
    // Nested loop.
    while (pred_loop->_parent != nullptr && pred_loop->_parent != this) {
      pred_loop = pred_loop->_parent;
    }
    // Make pred's loop be a child
    if (pred_loop->_parent == nullptr) {
      add_nested_loop(pred_loop);
      // Continue with loop entry predecessor.
      Block* pred_head = pred_loop->head();
      assert(pred_head->num_preds() - 1 == 2, "loop must have 2 predecessors");
      assert(pred_head != head(), "loop head in only one loop");
      push_pred(pred_head, LoopNode::EntryControl, worklist, cfg);
    } else {
      assert(pred_loop->_parent == this && _parent == nullptr, "just checking");
    }
  }
}

//------------------------------add_nested_loop--------------------------------
// Make cl a child of the current loop in the loop tree.
void CFGLoop::add_nested_loop(CFGLoop* cl) {
  assert(_parent == nullptr, "no parent yet");
  assert(cl != this, "not my own parent");
  cl->_parent = this;
  CFGLoop* ch = _child;
  if (ch == nullptr) {
    _child = cl;
  } else {
    while (ch->_sibling != nullptr) { ch = ch->_sibling; }
    ch->_sibling = cl;
  }
}

//------------------------------compute_loop_depth-----------------------------
// Store the loop depth in each CFGLoop object.
// Recursively walk the children to do the same for them.
void CFGLoop::compute_loop_depth(int depth) {
  _depth = depth;
  CFGLoop* ch = _child;
  while (ch != nullptr) {
    ch->compute_loop_depth(depth + 1);
    ch = ch->_sibling;
  }
}

//------------------------------compute_freq-----------------------------------
// Compute the frequency of each block and loop, relative to a single entry
// into the dominating loop head.
void CFGLoop::compute_freq() {
  // Bottom up traversal of loop tree (visit inner loops first.)
  // Set loop head frequency to 1.0, then transitively
  // compute frequency for all successors in the loop,
  // as well as for each exit edge.  Inner loops are
  // treated as single blocks with loop exit targets
  // as the successor blocks.

  // Nested loops first
  CFGLoop* ch = _child;
  while (ch != nullptr) {
    ch->compute_freq();
    ch = ch->_sibling;
  }
  assert (_members.length() > 0, "no empty loops");
  Block* hd = head();
  hd->_freq = 1.0;
  for (int i = 0; i < _members.length(); i++) {
    CFGElement* s = _members.at(i);
    double freq = s->_freq;
    if (s->is_block()) {
      Block* b = s->as_Block();
      for (uint j = 0; j < b->_num_succs; j++) {
        Block* sb = b->_succs[j];
        update_succ_freq(sb, freq * b->succ_prob(j));
      }
    } else {
      CFGLoop* lp = s->as_CFGLoop();
      assert(lp->_parent == this, "immediate child");
      for (int k = 0; k < lp->_exits.length(); k++) {
        Block* eb = lp->_exits.at(k).get_target();
        double prob = lp->_exits.at(k).get_prob();
        update_succ_freq(eb, freq * prob);
      }
    }
  }

  // For all loops other than the outer, "method" loop,
  // sum and normalize the exit probability. The "method" loop
  // should keep the initial exit probability of 1, so that
  // inner blocks do not get erroneously scaled.
  if (_depth != 0) {
    // Total the exit probabilities for this loop.
    double exits_sum = 0.0f;
    for (int i = 0; i < _exits.length(); i++) {
      exits_sum += _exits.at(i).get_prob();
    }

    // Normalize the exit probabilities. Until now, the
    // probabilities estimate the possibility of exit per
    // a single loop iteration; afterward, they estimate
    // the probability of exit per loop entry.
    for (int i = 0; i < _exits.length(); i++) {
      Block* et = _exits.at(i).get_target();
      float new_prob = 0.0f;
      if (_exits.at(i).get_prob() > 0.0f) {
        new_prob = _exits.at(i).get_prob() / exits_sum;
      }
      BlockProbPair bpp(et, new_prob);
      _exits.at_put(i, bpp);
    }

    // Save the total, but guard against unreasonable probability,
    // as the value is used to estimate the loop trip count.
    // An infinite trip count would blur relative block
    // frequencies.
    if (exits_sum > 1.0f) exits_sum = 1.0;
    if (exits_sum < PROB_MIN) exits_sum = PROB_MIN;
    _exit_prob = exits_sum;
  }
}

//------------------------------succ_prob-------------------------------------
// Determine the probability of reaching successor 'i' from the receiver block.
float Block::succ_prob(uint i) {
  int eidx = end_idx();
  Node *n = get_node(eidx);  // Get ending Node

  int op = n->Opcode();
  if (n->is_Mach()) {
    if (n->is_MachNullCheck()) {
      // Can only reach here if called after lcm. The original Op_If is gone,
      // so we attempt to infer the probability from one or both of the
      // successor blocks.
      assert(_num_succs == 2, "expecting 2 successors of a null check");
      // If either successor has only one predecessor, then the
      // probability estimate can be derived using the
      // relative frequency of the successor and this block.
      if (_succs[i]->num_preds() == 2) {
        return _succs[i]->_freq / _freq;
      } else if (_succs[1-i]->num_preds() == 2) {
        return 1 - (_succs[1-i]->_freq / _freq);
      } else {
        // Estimate using both successor frequencies
        float freq = _succs[i]->_freq;
        return freq / (freq + _succs[1-i]->_freq);
      }
    }
    op = n->as_Mach()->ideal_Opcode();
  }


  // Switch on branch type
  switch( op ) {
  case Op_CountedLoopEnd:
  case Op_If: {
    assert (i < 2, "just checking");
    // Conditionals pass on only part of their frequency
    float prob  = n->as_MachIf()->_prob;
    assert(prob >= 0.0 && prob <= 1.0, "out of range probability");
    // If succ[i] is the FALSE branch, invert path info
    if( get_node(i + eidx + 1)->Opcode() == Op_IfFalse ) {
      return 1.0f - prob; // not taken
    } else {
      return prob; // taken
    }
  }

  case Op_Jump:
    return n->as_MachJump()->_probs[get_node(i + eidx + 1)->as_JumpProj()->_con];

  case Op_Catch: {
    const CatchProjNode *ci = get_node(i + eidx + 1)->as_CatchProj();
    if (ci->_con == CatchProjNode::fall_through_index) {
      // Fall-thru path gets the lion's share.
      return 1.0f - PROB_UNLIKELY_MAG(5)*_num_succs;
    } else {
      // Presume exceptional paths are equally unlikely
      return PROB_UNLIKELY_MAG(5);
    }
  }

  case Op_Root:
  case Op_Goto:
    // Pass frequency straight thru to target
    return 1.0f;

  case Op_NeverBranch: {
    Node* succ = n->as_NeverBranch()->proj_out(0)->unique_ctrl_out();
    if (_succs[i]->head() == succ) {
      return 1.0f;
    }
    return 0.0f;
  }

  case Op_TailCall:
  case Op_TailJump:
  case Op_ForwardException:
  case Op_Return:
  case Op_Halt:
  case Op_Rethrow:
    // Do not push out freq to root block
    return 0.0f;

  default:
    ShouldNotReachHere();
  }

  return 0.0f;
}

//------------------------------num_fall_throughs-----------------------------
// Return the number of fall-through candidates for a block
int Block::num_fall_throughs() {
  int eidx = end_idx();
  Node *n = get_node(eidx);  // Get ending Node

  int op = n->Opcode();
  if (n->is_Mach()) {
    if (n->is_MachNullCheck()) {
      // In theory, either side can fall-thru, for simplicity sake,
      // let's say only the false branch can now.
      return 1;
    }
    op = n->as_Mach()->ideal_Opcode();
  }

  // Switch on branch type
  switch( op ) {
  case Op_CountedLoopEnd:
  case Op_If:
    return 2;

  case Op_Root:
  case Op_Goto:
    return 1;

  case Op_Catch: {
    for (uint i = 0; i < _num_succs; i++) {
      const CatchProjNode *ci = get_node(i + eidx + 1)->as_CatchProj();
      if (ci->_con == CatchProjNode::fall_through_index) {
        return 1;
      }
    }
    return 0;
  }

  case Op_Jump:
  case Op_NeverBranch:
  case Op_TailCall:
  case Op_TailJump:
  case Op_ForwardException:
  case Op_Return:
  case Op_Halt:
  case Op_Rethrow:
    return 0;

  default:
    ShouldNotReachHere();
  }

  return 0;
}

//------------------------------succ_fall_through-----------------------------
// Return true if a specific successor could be fall-through target.
bool Block::succ_fall_through(uint i) {
  int eidx = end_idx();
  Node *n = get_node(eidx);  // Get ending Node

  int op = n->Opcode();
  if (n->is_Mach()) {
    if (n->is_MachNullCheck()) {
      // In theory, either side can fall-thru, for simplicity sake,
      // let's say only the false branch can now.
      return get_node(i + eidx + 1)->Opcode() == Op_IfFalse;
    }
    op = n->as_Mach()->ideal_Opcode();
  }

  // Switch on branch type
  switch( op ) {
  case Op_CountedLoopEnd:
  case Op_If:
  case Op_Root:
  case Op_Goto:
    return true;

  case Op_Catch: {
    const CatchProjNode *ci = get_node(i + eidx + 1)->as_CatchProj();
    return ci->_con == CatchProjNode::fall_through_index;
  }

  case Op_Jump:
  case Op_NeverBranch:
  case Op_TailCall:
  case Op_TailJump:
  case Op_ForwardException:
  case Op_Return:
  case Op_Halt:
  case Op_Rethrow:
    return false;

  default:
    ShouldNotReachHere();
  }

  return false;
}

//------------------------------update_uncommon_branch------------------------
// Update the probability of a two-branch to be uncommon
void Block::update_uncommon_branch(Block* ub) {
  int eidx = end_idx();
  Node *n = get_node(eidx);  // Get ending Node

  int op = n->as_Mach()->ideal_Opcode();

  assert(op == Op_CountedLoopEnd || op == Op_If, "must be a If");
  assert(num_fall_throughs() == 2, "must be a two way branch block");

  // Which successor is ub?
  uint s;
  for (s = 0; s <_num_succs; s++) {
    if (_succs[s] == ub) break;
  }
  assert(s < 2, "uncommon successor must be found");

  // If ub is the true path, make the proability small, else
  // ub is the false path, and make the probability large
  bool invert = (get_node(s + eidx + 1)->Opcode() == Op_IfFalse);

  // Get existing probability
  float p = n->as_MachIf()->_prob;

  if (invert) p = 1.0 - p;
  if (p > PROB_MIN) {
    p = PROB_MIN;
  }
  if (invert) p = 1.0 - p;

  n->as_MachIf()->_prob = p;
}

//------------------------------update_succ_freq-------------------------------
// Update the appropriate frequency associated with block 'b', a successor of
// a block in this loop.
void CFGLoop::update_succ_freq(Block* b, double freq) {
  if (b->_loop == this) {
    if (b == head()) {
      // back branch within the loop
      // Do nothing now, the loop carried frequency will be
      // adjust later in scale_freq().
    } else {
      // simple branch within the loop
      b->_freq += freq;
    }
  } else if (!in_loop_nest(b)) {
    // branch is exit from this loop
    BlockProbPair bpp(b, freq);
    _exits.append(bpp);
  } else {
    // branch into nested loop
    CFGLoop* ch = b->_loop;
    ch->_freq += freq;
  }
}

//------------------------------in_loop_nest-----------------------------------
// Determine if block b is in the receiver's loop nest.
bool CFGLoop::in_loop_nest(Block* b) {
  int depth = _depth;
  CFGLoop* b_loop = b->_loop;
  int b_depth = b_loop->_depth;
  if (depth == b_depth) {
    return true;
  }
  while (b_depth > depth) {
    b_loop = b_loop->_parent;
    b_depth = b_loop->_depth;
  }
  return b_loop == this;
}

//------------------------------scale_freq-------------------------------------
// Scale frequency of loops and blocks by trip counts from outer loops
// Do a top down traversal of loop tree (visit outer loops first.)
void CFGLoop::scale_freq() {
  double loop_freq = _freq * trip_count();
  _freq = loop_freq;
  for (int i = 0; i < _members.length(); i++) {
    CFGElement* s = _members.at(i);
    double block_freq = s->_freq * loop_freq;
    if (g_isnan(block_freq) || block_freq < MIN_BLOCK_FREQUENCY)
      block_freq = MIN_BLOCK_FREQUENCY;
    s->_freq = block_freq;
  }
  CFGLoop* ch = _child;
  while (ch != nullptr) {
    ch->scale_freq();
    ch = ch->_sibling;
  }
}

// Frequency of outer loop
double CFGLoop::outer_loop_freq() const {
  if (_child != nullptr) {
    return _child->_freq;
  }
  return _freq;
}

#ifndef PRODUCT
//------------------------------dump_tree--------------------------------------
void CFGLoop::dump_tree() const {
  dump();
  if (_child != nullptr)   _child->dump_tree();
  if (_sibling != nullptr) _sibling->dump_tree();
}

//------------------------------dump-------------------------------------------
void CFGLoop::dump() const {
  for (int i = 0; i < _depth; i++) tty->print("   ");
  tty->print("%s: %d  trip_count: %6.0f freq: %6.0f\n",
             _depth == 0 ? "Method" : "Loop", _id, trip_count(), _freq);
  for (int i = 0; i < _depth; i++) tty->print("   ");
  tty->print("         members:");
  int k = 0;
  for (int i = 0; i < _members.length(); i++) {
    if (k++ >= 6) {
      tty->print("\n              ");
      for (int j = 0; j < _depth+1; j++) tty->print("   ");
      k = 0;
    }
    CFGElement *s = _members.at(i);
    if (s->is_block()) {
      Block *b = s->as_Block();
      tty->print(" B%d(%6.3f)", b->_pre_order, b->_freq);
    } else {
      CFGLoop* lp = s->as_CFGLoop();
      tty->print(" L%d(%6.3f)", lp->_id, lp->_freq);
    }
  }
  tty->print("\n");
  for (int i = 0; i < _depth; i++) tty->print("   ");
  tty->print("         exits:  ");
  k = 0;
  for (int i = 0; i < _exits.length(); i++) {
    if (k++ >= 7) {
      tty->print("\n              ");
      for (int j = 0; j < _depth+1; j++) tty->print("   ");
      k = 0;
    }
    Block *blk = _exits.at(i).get_target();
    double prob = _exits.at(i).get_prob();
    tty->print(" ->%d@%d%%", blk->_pre_order, (int)(prob*100));
  }
  tty->print("\n");
}
#endif
