/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style

#include "incls/_precompiled.incl"
#include "incls/_gcm.cpp.incl"

// To avoid float value underflow
#define MIN_BLOCK_FREQUENCY 1.e-35f

//----------------------------schedule_node_into_block-------------------------
// Insert node n into block b. Look for projections of n and make sure they
// are in b also.
void PhaseCFG::schedule_node_into_block( Node *n, Block *b ) {
  // Set basic block of n, Add n to b,
  _bbs.map(n->_idx, b);
  b->add_inst(n);

  // After Matching, nearly any old Node may have projections trailing it.
  // These are usually machine-dependent flags.  In any case, they might
  // float to another block below this one.  Move them up.
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node*  use  = n->fast_out(i);
    if (use->is_Proj()) {
      Block* buse = _bbs[use->_idx];
      if (buse != b) {              // In wrong block?
        if (buse != NULL)
          buse->find_remove(use);   // Remove from wrong block
        _bbs.map(use->_idx, b);     // Re-insert in this block
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
  assert(in0 != NULL, "Only control-dependent");
  const Node *p = in0->is_block_proj();
  if (p != NULL && p != n) {    // Control from a block projection?
    assert(!n->pinned() || n->is_SafePointScalarObject(), "only SafePointScalarObject pinned node is expected here");
    // Find trailing Region
    Block *pb = _bbs[in0->_idx]; // Block-projection already has basic block
    uint j = 0;
    if (pb->_num_succs != 1) {  // More then 1 successor?
      // Search for successor
      uint max = pb->_nodes.size();
      assert( max > 1, "" );
      uint start = max - pb->_num_succs;
      // Find which output path belongs to projection
      for (j = start; j < max; j++) {
        if( pb->_nodes[j] == in0 )
          break;
      }
      assert( j < max, "must find" );
      // Change control to match head of successor basic block
      j -= start;
    }
    n->set_req(0, pb->_succs[j]->head());
  }
}


//------------------------------schedule_pinned_nodes--------------------------
// Set the basic block for Nodes pinned into blocks
void PhaseCFG::schedule_pinned_nodes( VectorSet &visited ) {
  // Allocate node stack of size C->unique()+8 to avoid frequent realloc
  GrowableArray <Node *> spstack(C->unique()+8);
  spstack.push(_root);
  while ( spstack.is_nonempty() ) {
    Node *n = spstack.pop();
    if( !visited.test_set(n->_idx) ) { // Test node and flag it as visited
      if( n->pinned() && !_bbs.lookup(n->_idx) ) {  // Pinned?  Nail it down!
        assert( n->in(0), "pinned Node must have Control" );
        // Before setting block replace block_proj control edge
        replace_block_proj_ctrl(n);
        Node *input = n->in(0);
        while( !input->is_block_start() )
          input = input->in(0);
        Block *b = _bbs[input->_idx];  // Basic block of controlling input
        schedule_node_into_block(n, b);
      }
      for( int i = n->req() - 1; i >= 0; --i ) {  // For all inputs
        if( n->in(i) != NULL )
          spstack.push(n->in(i));
      }
    }
  }
}

#ifdef ASSERT
// Assert that new input b2 is dominated by all previous inputs.
// Check this by by seeing that it is dominated by b1, the deepest
// input observed until b2.
static void assert_dom(Block* b1, Block* b2, Node* n, Block_Array &bbs) {
  if (b1 == NULL)  return;
  assert(b1->_dom_depth < b2->_dom_depth, "sanity");
  Block* tmp = b2;
  while (tmp != b1 && tmp != NULL) {
    tmp = tmp->_idom;
  }
  if (tmp != b1) {
    // Detected an unschedulable graph.  Print some nice stuff and die.
    tty->print_cr("!!! Unschedulable graph !!!");
    for (uint j=0; j<n->len(); j++) { // For all inputs
      Node* inn = n->in(j); // Get input
      if (inn == NULL)  continue;  // Ignore NULL, missing inputs
      Block* inb = bbs[inn->_idx];
      tty->print("B%d idom=B%d depth=%2d ",inb->_pre_order,
                 inb->_idom ? inb->_idom->_pre_order : 0, inb->_dom_depth);
      inn->dump();
    }
    tty->print("Failing node: ");
    n->dump();
    assert(false, "unscheduable graph");
  }
}
#endif

static Block* find_deepest_input(Node* n, Block_Array &bbs) {
  // Find the last input dominated by all other inputs.
  Block* deepb           = NULL;        // Deepest block so far
  int    deepb_dom_depth = 0;
  for (uint k = 0; k < n->len(); k++) { // For all inputs
    Node* inn = n->in(k);               // Get input
    if (inn == NULL)  continue;         // Ignore NULL, missing inputs
    Block* inb = bbs[inn->_idx];
    assert(inb != NULL, "must already have scheduled this input");
    if (deepb_dom_depth < (int) inb->_dom_depth) {
      // The new inb must be dominated by the previous deepb.
      // The various inputs must be linearly ordered in the dom
      // tree, or else there will not be a unique deepest block.
      DEBUG_ONLY(assert_dom(deepb, inb, n, bbs));
      deepb = inb;                      // Save deepest block
      deepb_dom_depth = deepb->_dom_depth;
    }
  }
  assert(deepb != NULL, "must be at least one input to n");
  return deepb;
}


//------------------------------schedule_early---------------------------------
// Find the earliest Block any instruction can be placed in.  Some instructions
// are pinned into Blocks.  Unpinned instructions can appear in last block in
// which all their inputs occur.
bool PhaseCFG::schedule_early(VectorSet &visited, Node_List &roots) {
  // Allocate stack with enough space to avoid frequent realloc
  Node_Stack nstack(roots.Size() + 8); // (unique >> 1) + 24 from Java2D stats
  // roots.push(_root); _root will be processed among C->top() inputs
  roots.push(C->top());
  visited.set(C->top()->_idx);

  while (roots.size() != 0) {
    // Use local variables nstack_top_n & nstack_top_i to cache values
    // on stack's top.
    Node *nstack_top_n = roots.pop();
    uint  nstack_top_i = 0;
//while_nstack_nonempty:
    while (true) {
      // Get parent node and next input's index from stack's top.
      Node *n = nstack_top_n;
      uint  i = nstack_top_i;

      if (i == 0) {
        // Fixup some control.  Constants without control get attached
        // to root and nodes that use is_block_proj() nodes should be attached
        // to the region that starts their block.
        const Node *in0 = n->in(0);
        if (in0 != NULL) {              // Control-dependent?
          replace_block_proj_ctrl(n);
        } else {               // n->in(0) == NULL
          if (n->req() == 1) { // This guy is a constant with NO inputs?
            n->set_req(0, _root);
          }
        }
      }

      // First, visit all inputs and force them to get a block.  If an
      // input is already in a block we quit following inputs (to avoid
      // cycles). Instead we put that Node on a worklist to be handled
      // later (since IT'S inputs may not have a block yet).
      bool done = true;              // Assume all n's inputs will be processed
      while (i < n->len()) {         // For all inputs
        Node *in = n->in(i);         // Get input
        ++i;
        if (in == NULL) continue;    // Ignore NULL, missing inputs
        int is_visited = visited.test_set(in->_idx);
        if (!_bbs.lookup(in->_idx)) { // Missing block selection?
          if (is_visited) {
            // assert( !visited.test(in->_idx), "did not schedule early" );
            return false;
          }
          nstack.push(n, i);         // Save parent node and next input's index.
          nstack_top_n = in;         // Process current input now.
          nstack_top_i = 0;
          done = false;              // Not all n's inputs processed.
          break; // continue while_nstack_nonempty;
        } else if (!is_visited) {    // Input not yet visited?
          roots.push(in);            // Visit this guy later, using worklist
        }
      }
      if (done) {
        // All of n's inputs have been processed, complete post-processing.

        // Some instructions are pinned into a block.  These include Region,
        // Phi, Start, Return, and other control-dependent instructions and
        // any projections which depend on them.
        if (!n->pinned()) {
          // Set earliest legal block.
          _bbs.map(n->_idx, find_deepest_input(n, _bbs));
        } else {
          assert(_bbs[n->_idx] == _bbs[n->in(0)->_idx], "Pinned Node should be at the same block as its control edge");
        }

        if (nstack.is_empty()) {
          // Finished all nodes on stack.
          // Process next node on the worklist 'roots'.
          break;
        }
        // Get saved parent node and next input's index.
        nstack_top_n = nstack.node();
        nstack_top_i = nstack.index();
        nstack.pop();
      } //    if (done)
    }   // while (true)
  }     // while (roots.size() != 0)
  return true;
}

//------------------------------dom_lca----------------------------------------
// Find least common ancestor in dominator tree
// LCA is a current notion of LCA, to be raised above 'this'.
// As a convenient boundary condition, return 'this' if LCA is NULL.
// Find the LCA of those two nodes.
Block* Block::dom_lca(Block* LCA) {
  if (LCA == NULL || LCA == this)  return this;

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
static Block* raise_LCA_above_use(Block* LCA, Node* use, Node* def, Block_Array &bbs) {
  Block* buse = bbs[use->_idx];
  if (buse == NULL)    return LCA;   // Unused killing Projs have no use block
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
      Block* pred = bbs[buse->pred(j)->_idx];
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
static Block* raise_LCA_above_marks(Block* LCA, node_idx_t mark,
                                    Block* early, Block_Array &bbs) {
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
      assert(early->dominates(LCA), "early is high enough");
      // Resume searching at that point, skipping intermediate levels.
      worklist.push(LCA);
      if (LCA == mid)
        continue; // Don't mark as visited to avoid early termination.
    } else {
      // Keep searching through this block's predecessors.
      for (uint j = 1, jmax = mid->num_preds(); j < jmax; j++) {
        Block* mid_parent = bbs[ mid->pred(j)->_idx ];
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
static Block* memory_early_block(Node* load, Block* early, Block_Array &bbs) {
  Node* base;
  Node* index;
  Node* store = load->in(MemNode::Memory);
  load->as_Mach()->memory_inputs(base, index);

  assert(base != NodeSentinel && index != NodeSentinel,
         "unexpected base/index inputs");

  Node* mem_inputs[4];
  int mem_inputs_length = 0;
  if (base != NULL)  mem_inputs[mem_inputs_length++] = base;
  if (index != NULL) mem_inputs[mem_inputs_length++] = index;
  if (store != NULL) mem_inputs[mem_inputs_length++] = store;

  // In the comparision below, add one to account for the control input,
  // which may be null, but always takes up a spot in the in array.
  if (mem_inputs_length + 1 < (int) load->req()) {
    // This "load" has more inputs than just the memory, base and index inputs.
    // For purposes of checking anti-dependences, we need to start
    // from the early block of only the address portion of the instruction,
    // and ignore other blocks that may have factored into the wider
    // schedule_early calculation.
    if (load->in(0) != NULL) mem_inputs[mem_inputs_length++] = load->in(0);

    Block* deepb           = NULL;        // Deepest block so far
    int    deepb_dom_depth = 0;
    for (int i = 0; i < mem_inputs_length; i++) {
      Block* inb = bbs[mem_inputs[i]->_idx];
      if (deepb_dom_depth < (int) inb->_dom_depth) {
        // The new inb must be dominated by the previous deepb.
        // The various inputs must be linearly ordered in the dom
        // tree, or else there will not be a unique deepest block.
        DEBUG_ONLY(assert_dom(deepb, inb, load, bbs));
        deepb = inb;                      // Save deepest block
        deepb_dom_depth = deepb->_dom_depth;
      }
    }
    early = deepb;
  }

  return early;
}

//--------------------------insert_anti_dependences---------------------------
// A load may need to witness memory that nearby stores can overwrite.
// For each nearby store, either insert an "anti-dependence" edge
// from the load to the store, or else move LCA upward to force the
// load to (eventually) be scheduled in a block above the store.
//
// Do not add edges to stores on distinct control-flow paths;
// only add edges to stores which might interfere.
//
// Return the (updated) LCA.  There will not be any possibly interfering
// store between the load's "early block" and the updated LCA.
// Any stores in the updated LCA will have new precedence edges
// back to the load.  The caller is expected to schedule the load
// in the LCA, in which case the precedence edges will make LCM
// preserve anti-dependences.  The caller may also hoist the load
// above the LCA, if it is not the early block.
Block* PhaseCFG::insert_anti_dependences(Block* LCA, Node* load, bool verify) {
  assert(load->needs_anti_dependence_check(), "must be a load of some sort");
  assert(LCA != NULL, "");
  DEBUG_ONLY(Block* LCA_orig = LCA);

  // Compute the alias index.  Loads and stores with different alias indices
  // do not need anti-dependence edges.
  uint load_alias_idx = C->get_alias_index(load->adr_type());
#ifdef ASSERT
  if (load_alias_idx == Compile::AliasIdxBot && C->AliasLevel() > 0 &&
      (PrintOpto || VerifyAliases ||
       PrintMiscellaneous && (WizardMode || Verbose))) {
    // Load nodes should not consume all of memory.
    // Reporting a bottom type indicates a bug in adlc.
    // If some particular type of node validly consumes all of memory,
    // sharpen the preceding "if" to exclude it, so we can catch bugs here.
    tty->print_cr("*** Possible Anti-Dependence Bug:  Load consumes all of memory.");
    load->dump(2);
    if (VerifyAliases)  assert(load_alias_idx != Compile::AliasIdxBot, "");
  }
#endif
  assert(load_alias_idx || (load->is_Mach() && load->as_Mach()->ideal_Opcode() == Op_StrComp),
         "String compare is only known 'load' that does not conflict with any stores");
  assert(load_alias_idx || (load->is_Mach() && load->as_Mach()->ideal_Opcode() == Op_StrEquals),
         "String equals is a 'load' that does not conflict with any stores");
  assert(load_alias_idx || (load->is_Mach() && load->as_Mach()->ideal_Opcode() == Op_StrIndexOf),
         "String indexOf is a 'load' that does not conflict with any stores");
  assert(load_alias_idx || (load->is_Mach() && load->as_Mach()->ideal_Opcode() == Op_AryEq),
         "Arrays equals is a 'load' that do not conflict with any stores");

  if (!C->alias_type(load_alias_idx)->is_rewritable()) {
    // It is impossible to spoil this load by putting stores before it,
    // because we know that the stores will never update the value
    // which 'load' must witness.
    return LCA;
  }

  node_idx_t load_index = load->_idx;

  // Note the earliest legal placement of 'load', as determined by
  // by the unique point in the dom tree where all memory effects
  // and other inputs are first available.  (Computed by schedule_early.)
  // For normal loads, 'early' is the shallowest place (dom graph wise)
  // to look for anti-deps between this load and any store.
  Block* early = _bbs[load_index];

  // If we are subsuming loads, compute an "early" block that only considers
  // memory or address inputs. This block may be different than the
  // schedule_early block in that it could be at an even shallower depth in the
  // dominator tree, and allow for a broader discovery of anti-dependences.
  if (C->subsume_loads()) {
    early = memory_early_block(load, early, _bbs);
  }

  ResourceArea *area = Thread::current()->resource_area();
  Node_List worklist_mem(area);     // prior memory state to store
  Node_List worklist_store(area);   // possible-def to explore
  Node_List worklist_visited(area); // visited mergemem nodes
  Node_List non_early_stores(area); // all relevant stores outside of early
  bool must_raise_LCA = false;

#ifdef TRACK_PHI_INPUTS
  // %%% This extra checking fails because MergeMem nodes are not GVNed.
  // Provide "phi_inputs" to check if every input to a PhiNode is from the
  // original memory state.  This indicates a PhiNode for which should not
  // prevent the load from sinking.  For such a block, set_raise_LCA_mark
  // may be overly conservative.
  // Mechanism: count inputs seen for each Phi encountered in worklist_store.
  DEBUG_ONLY(GrowableArray<uint> phi_inputs(area, C->unique(),0,0));
#endif

  // 'load' uses some memory state; look for users of the same state.
  // Recurse through MergeMem nodes to the stores that use them.

  // Each of these stores is a possible definition of memory
  // that 'load' needs to use.  We need to force 'load'
  // to occur before each such store.  When the store is in
  // the same block as 'load', we insert an anti-dependence
  // edge load->store.

  // The relevant stores "nearby" the load consist of a tree rooted
  // at initial_mem, with internal nodes of type MergeMem.
  // Therefore, the branches visited by the worklist are of this form:
  //    initial_mem -> (MergeMem ->)* store
  // The anti-dependence constraints apply only to the fringe of this tree.

  Node* initial_mem = load->in(MemNode::Memory);
  worklist_store.push(initial_mem);
  worklist_visited.push(initial_mem);
  worklist_mem.push(NULL);
  while (worklist_store.size() > 0) {
    // Examine a nearby store to see if it might interfere with our load.
    Node* mem   = worklist_mem.pop();
    Node* store = worklist_store.pop();
    uint op = store->Opcode();

    // MergeMems do not directly have anti-deps.
    // Treat them as internal nodes in a forward tree of memory states,
    // the leaves of which are each a 'possible-def'.
    if (store == initial_mem    // root (exclusive) of tree we are searching
        || op == Op_MergeMem    // internal node of tree we are searching
        ) {
      mem = store;   // It's not a possibly interfering store.
      if (store == initial_mem)
        initial_mem = NULL;  // only process initial memory once

      for (DUIterator_Fast imax, i = mem->fast_outs(imax); i < imax; i++) {
        store = mem->fast_out(i);
        if (store->is_MergeMem()) {
          // Be sure we don't get into combinatorial problems.
          // (Allow phis to be repeated; they can merge two relevant states.)
          uint j = worklist_visited.size();
          for (; j > 0; j--) {
            if (worklist_visited.at(j-1) == store)  break;
          }
          if (j > 0)  continue; // already on work list; do not repeat
          worklist_visited.push(store);
        }
        worklist_mem.push(mem);
        worklist_store.push(store);
      }
      continue;
    }

    if (op == Op_MachProj || op == Op_Catch)   continue;
    if (store->needs_anti_dependence_check())  continue;  // not really a store

    // Compute the alias index.  Loads and stores with different alias
    // indices do not need anti-dependence edges.  Wide MemBar's are
    // anti-dependent on everything (except immutable memories).
    const TypePtr* adr_type = store->adr_type();
    if (!C->can_alias(adr_type, load_alias_idx))  continue;

    // Most slow-path runtime calls do NOT modify Java memory, but
    // they can block and so write Raw memory.
    if (store->is_Mach()) {
      MachNode* mstore = store->as_Mach();
      if (load_alias_idx != Compile::AliasIdxRaw) {
        // Check for call into the runtime using the Java calling
        // convention (and from there into a wrapper); it has no
        // _method.  Can't do this optimization for Native calls because
        // they CAN write to Java memory.
        if (mstore->ideal_Opcode() == Op_CallStaticJava) {
          assert(mstore->is_MachSafePoint(), "");
          MachSafePointNode* ms = (MachSafePointNode*) mstore;
          assert(ms->is_MachCallJava(), "");
          MachCallJavaNode* mcj = (MachCallJavaNode*) ms;
          if (mcj->_method == NULL) {
            // These runtime calls do not write to Java visible memory
            // (other than Raw) and so do not require anti-dependence edges.
            continue;
          }
        }
        // Same for SafePoints: they read/write Raw but only read otherwise.
        // This is basically a workaround for SafePoints only defining control
        // instead of control + memory.
        if (mstore->ideal_Opcode() == Op_SafePoint)
          continue;
      } else {
        // Some raw memory, such as the load of "top" at an allocation,
        // can be control dependent on the previous safepoint. See
        // comments in GraphKit::allocate_heap() about control input.
        // Inserting an anti-dep between such a safepoint and a use
        // creates a cycle, and will cause a subsequent failure in
        // local scheduling.  (BugId 4919904)
        // (%%% How can a control input be a safepoint and not a projection??)
        if (mstore->ideal_Opcode() == Op_SafePoint && load->in(0) == mstore)
          continue;
      }
    }

    // Identify a block that the current load must be above,
    // or else observe that 'store' is all the way up in the
    // earliest legal block for 'load'.  In the latter case,
    // immediately insert an anti-dependence edge.
    Block* store_block = _bbs[store->_idx];
    assert(store_block != NULL, "unused killing projections skipped above");

    if (store->is_Phi()) {
      // 'load' uses memory which is one (or more) of the Phi's inputs.
      // It must be scheduled not before the Phi, but rather before
      // each of the relevant Phi inputs.
      //
      // Instead of finding the LCA of all inputs to a Phi that match 'mem',
      // we mark each corresponding predecessor block and do a combined
      // hoisting operation later (raise_LCA_above_marks).
      //
      // Do not assert(store_block != early, "Phi merging memory after access")
      // PhiNode may be at start of block 'early' with backedge to 'early'
      DEBUG_ONLY(bool found_match = false);
      for (uint j = PhiNode::Input, jmax = store->req(); j < jmax; j++) {
        if (store->in(j) == mem) {   // Found matching input?
          DEBUG_ONLY(found_match = true);
          Block* pred_block = _bbs[store_block->pred(j)->_idx];
          if (pred_block != early) {
            // If any predecessor of the Phi matches the load's "early block",
            // we do not need a precedence edge between the Phi and 'load'
            // since the load will be forced into a block preceding the Phi.
            pred_block->set_raise_LCA_mark(load_index);
            assert(!LCA_orig->dominates(pred_block) ||
                   early->dominates(pred_block), "early is high enough");
            must_raise_LCA = true;
          } else {
            // anti-dependent upon PHI pinned below 'early', no edge needed
            LCA = early;             // but can not schedule below 'early'
          }
        }
      }
      assert(found_match, "no worklist bug");
#ifdef TRACK_PHI_INPUTS
#ifdef ASSERT
      // This assert asks about correct handling of PhiNodes, which may not
      // have all input edges directly from 'mem'. See BugId 4621264
      int num_mem_inputs = phi_inputs.at_grow(store->_idx,0) + 1;
      // Increment by exactly one even if there are multiple copies of 'mem'
      // coming into the phi, because we will run this block several times
      // if there are several copies of 'mem'.  (That's how DU iterators work.)
      phi_inputs.at_put(store->_idx, num_mem_inputs);
      assert(PhiNode::Input + num_mem_inputs < store->req(),
             "Expect at least one phi input will not be from original memory state");
#endif //ASSERT
#endif //TRACK_PHI_INPUTS
    } else if (store_block != early) {
      // 'store' is between the current LCA and earliest possible block.
      // Label its block, and decide later on how to raise the LCA
      // to include the effect on LCA of this store.
      // If this store's block gets chosen as the raised LCA, we
      // will find him on the non_early_stores list and stick him
      // with a precedence edge.
      // (But, don't bother if LCA is already raised all the way.)
      if (LCA != early) {
        store_block->set_raise_LCA_mark(load_index);
        must_raise_LCA = true;
        non_early_stores.push(store);
      }
    } else {
      // Found a possibly-interfering store in the load's 'early' block.
      // This means 'load' cannot sink at all in the dominator tree.
      // Add an anti-dep edge, and squeeze 'load' into the highest block.
      assert(store != load->in(0), "dependence cycle found");
      if (verify) {
        assert(store->find_edge(load) != -1, "missing precedence edge");
      } else {
        store->add_prec(load);
      }
      LCA = early;
      // This turns off the process of gathering non_early_stores.
    }
  }
  // (Worklist is now empty; all nearby stores have been visited.)

  // Finished if 'load' must be scheduled in its 'early' block.
  // If we found any stores there, they have already been given
  // precedence edges.
  if (LCA == early)  return LCA;

  // We get here only if there are no possibly-interfering stores
  // in the load's 'early' block.  Move LCA up above all predecessors
  // which contain stores we have noted.
  //
  // The raised LCA block can be a home to such interfering stores,
  // but its predecessors must not contain any such stores.
  //
  // The raised LCA will be a lower bound for placing the load,
  // preventing the load from sinking past any block containing
  // a store that may invalidate the memory state required by 'load'.
  if (must_raise_LCA)
    LCA = raise_LCA_above_marks(LCA, load->_idx, early, _bbs);
  if (LCA == early)  return LCA;

  // Insert anti-dependence edges from 'load' to each store
  // in the non-early LCA block.
  // Mine the non_early_stores list for such stores.
  if (LCA->raise_LCA_mark() == load_index) {
    while (non_early_stores.size() > 0) {
      Node* store = non_early_stores.pop();
      Block* store_block = _bbs[store->_idx];
      if (store_block == LCA) {
        // add anti_dependence from store to load in its own block
        assert(store != load->in(0), "dependence cycle found");
        if (verify) {
          assert(store->find_edge(load) != -1, "missing precedence edge");
        } else {
          store->add_prec(load);
        }
      } else {
        assert(store_block->raise_LCA_mark() == load_index, "block was marked");
        // Any other stores we found must be either inside the new LCA
        // or else outside the original LCA.  In the latter case, they
        // did not interfere with any use of 'load'.
        assert(LCA->dominates(store_block)
               || !LCA_orig->dominates(store_block), "no stray stores");
      }
    }
  }

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
  Node_Backward_Iterator(Node *root, VectorSet &visited, Node_List &stack, Block_Array &bbs);

  // Postincrement operator to iterate over the nodes
  Node *next();

private:
  VectorSet   &_visited;
  Node_List   &_stack;
  Block_Array &_bbs;
};

// Constructor for the Node_Backward_Iterator
Node_Backward_Iterator::Node_Backward_Iterator( Node *root, VectorSet &visited, Node_List &stack, Block_Array &bbs )
  : _visited(visited), _stack(stack), _bbs(bbs) {
  // The stack should contain exactly the root
  stack.clear();
  stack.push(root);

  // Clear the visited bits
  visited.Clear();
}

// Iterator for the Node_Backward_Iterator
Node *Node_Backward_Iterator::next() {

  // If the _stack is empty, then just return NULL: finished.
  if ( !_stack.size() )
    return NULL;

  // '_stack' is emulating a real _stack.  The 'visit-all-users' loop has been
  // made stateless, so I do not need to record the index 'i' on my _stack.
  // Instead I visit all users each time, scanning for unvisited users.
  // I visit unvisited not-anti-dependence users first, then anti-dependent
  // children next.
  Node *self = _stack.pop();

  // I cycle here when I am entering a deeper level of recursion.
  // The key variable 'self' was set prior to jumping here.
  while( 1 ) {

    _visited.set(self->_idx);

    // Now schedule all uses as late as possible.
    uint src     = self->is_Proj() ? self->in(0)->_idx : self->_idx;
    uint src_rpo = _bbs[src]->_rpo;

    // Schedule all nodes in a post-order visit
    Node *unvisited = NULL;  // Unvisited anti-dependent Node, if any

    // Scan for unvisited nodes
    for (DUIterator_Fast imax, i = self->fast_outs(imax); i < imax; i++) {
      // For all uses, schedule late
      Node* n = self->fast_out(i); // Use

      // Skip already visited children
      if ( _visited.test(n->_idx) )
        continue;

      // do not traverse backward control edges
      Node *use = n->is_Proj() ? n->in(0) : n;
      uint use_rpo = _bbs[use->_idx]->_rpo;

      if ( use_rpo < src_rpo )
        continue;

      // Phi nodes always precede uses in a basic block
      if ( use_rpo == src_rpo && use->is_Phi() )
        continue;

      unvisited = n;      // Found unvisited

      // Check for possible-anti-dependent
      if( !n->needs_anti_dependence_check() )
        break;            // Not visited, not anti-dep; schedule it NOW
    }

    // Did I find an unvisited not-anti-dependent Node?
    if ( !unvisited )
      break;                  // All done with children; post-visit 'self'

    // Visit the unvisited Node.  Contains the obvious push to
    // indicate I'm entering a deeper level of recursion.  I push the
    // old state onto the _stack and set a new state and loop (recurse).
    _stack.push(self);
    self = unvisited;
  } // End recursion loop

  return self;
}

//------------------------------ComputeLatenciesBackwards----------------------
// Compute the latency of all the instructions.
void PhaseCFG::ComputeLatenciesBackwards(VectorSet &visited, Node_List &stack) {
#ifndef PRODUCT
  if (trace_opto_pipelining())
    tty->print("\n#---- ComputeLatenciesBackwards ----\n");
#endif

  Node_Backward_Iterator iter((Node *)_root, visited, stack, _bbs);
  Node *n;

  // Walk over all the nodes from last to first
  while (n = iter.next()) {
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
    tty->print("# latency_to_inputs: node_latency[%d] = %d for node",
               n->_idx, _node_latency->at_grow(n->_idx));
    dump();
  }
#endif

  if (n->is_Proj())
    n = n->in(0);

  if (n->is_Root())
    return;

  uint nlen = n->len();
  uint use_latency = _node_latency->at_grow(n->_idx);
  uint use_pre_order = _bbs[n->_idx]->_pre_order;

  for ( uint j=0; j<nlen; j++ ) {
    Node *def = n->in(j);

    if (!def || def == n)
      continue;

    // Walk backwards thru projections
    if (def->is_Proj())
      def = def->in(0);

#ifndef PRODUCT
    if (trace_opto_pipelining()) {
      tty->print("#    in(%2d): ", j);
      def->dump();
    }
#endif

    // If the defining block is not known, assume it is ok
    Block *def_block = _bbs[def->_idx];
    uint def_pre_order = def_block ? def_block->_pre_order : 0;

    if ( (use_pre_order <  def_pre_order) ||
         (use_pre_order == def_pre_order && n->is_Phi()) )
      continue;

    uint delta_latency = n->latency(j);
    uint current_latency = delta_latency + use_latency;

    if (_node_latency->at_grow(def->_idx) < current_latency) {
      _node_latency->at_put_grow(def->_idx, current_latency);
    }

#ifndef PRODUCT
    if (trace_opto_pipelining()) {
      tty->print_cr("#      %d + edge_latency(%d) == %d -> %d, node_latency[%d] = %d",
                    use_latency, j, delta_latency, current_latency, def->_idx,
                    _node_latency->at_grow(def->_idx));
    }
#endif
  }
}

//------------------------------latency_from_use-------------------------------
// Compute the latency of a specific use
int PhaseCFG::latency_from_use(Node *n, const Node *def, Node *use) {
  // If self-reference, return no latency
  if (use == n || use->is_Root())
    return 0;

  uint def_pre_order = _bbs[def->_idx]->_pre_order;
  uint latency = 0;

  // If the use is not a projection, then it is simple...
  if (!use->is_Proj()) {
#ifndef PRODUCT
    if (trace_opto_pipelining()) {
      tty->print("#    out(): ");
      use->dump();
    }
#endif

    uint use_pre_order = _bbs[use->_idx]->_pre_order;

    if (use_pre_order < def_pre_order)
      return 0;

    if (use_pre_order == def_pre_order && use->is_Phi())
      return 0;

    uint nlen = use->len();
    uint nl = _node_latency->at_grow(use->_idx);

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
    tty->print("# latency_from_outputs: node_latency[%d] = %d for node",
               n->_idx, _node_latency->at_grow(n->_idx));
    dump();
  }
#endif
  uint latency=0;
  const Node *def = n->is_Proj() ? n->in(0): n;

  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    uint l = latency_from_use(n, def, n->fast_out(i));

    if (latency < l) latency = l;
  }

  _node_latency->at_put_grow(n->_idx, latency);
}

//------------------------------hoist_to_cheaper_block-------------------------
// Pick a block for node self, between early and LCA, that is a cheaper
// alternative to LCA.
Block* PhaseCFG::hoist_to_cheaper_block(Block* LCA, Block* early, Node* self) {
  const double delta = 1+PROB_UNLIKELY_MAG(4);
  Block* least       = LCA;
  double least_freq  = least->_freq;
  uint target        = _node_latency->at_grow(self->_idx);
  uint start_latency = _node_latency->at_grow(LCA->_nodes[0]->_idx);
  uint end_latency   = _node_latency->at_grow(LCA->_nodes[LCA->end_idx()]->_idx);
  bool in_latency    = (target <= start_latency);
  const Block* root_block = _bbs[_root->_idx];

  // Turn off latency scheduling if scheduling is just plain off
  if (!C->do_scheduling())
    in_latency = true;

  // Do not hoist (to cover latency) instructions which target a
  // single register.  Hoisting stretches the live range of the
  // single register and may force spilling.
  MachNode* mach = self->is_Mach() ? self->as_Mach() : NULL;
  if (mach && mach->out_RegMask().is_bound1() && mach->out_RegMask().is_NotEmpty())
    in_latency = true;

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("# Find cheaper block for latency %d: ",
      _node_latency->at_grow(self->_idx));
    self->dump();
    tty->print_cr("#   B%d: start latency for [%4d]=%d, end latency for [%4d]=%d, freq=%g",
      LCA->_pre_order,
      LCA->_nodes[0]->_idx,
      start_latency,
      LCA->_nodes[LCA->end_idx()]->_idx,
      end_latency,
      least_freq);
  }
#endif

  // Walk up the dominator tree from LCA (Lowest common ancestor) to
  // the earliest legal location.  Capture the least execution frequency.
  while (LCA != early) {
    LCA = LCA->_idom;         // Follow up the dominator tree

    if (LCA == NULL) {
      // Bailout without retry
      C->record_method_not_compilable("late schedule failed: LCA == NULL");
      return least;
    }

    // Don't hoist machine instructions to the root basic block
    if (mach && LCA == root_block)
      break;

    uint start_lat = _node_latency->at_grow(LCA->_nodes[0]->_idx);
    uint end_idx   = LCA->end_idx();
    uint end_lat   = _node_latency->at_grow(LCA->_nodes[end_idx]->_idx);
    double LCA_freq = LCA->_freq;
#ifndef PRODUCT
    if (trace_opto_pipelining()) {
      tty->print_cr("#   B%d: start latency for [%4d]=%d, end latency for [%4d]=%d, freq=%g",
        LCA->_pre_order, LCA->_nodes[0]->_idx, start_lat, end_idx, end_lat, LCA_freq);
    }
#endif
    if (LCA_freq < least_freq              || // Better Frequency
        ( !in_latency                   &&    // No block containing latency
          LCA_freq < least_freq * delta &&    // No worse frequency
          target >= end_lat             &&    // within latency range
          !self->is_iteratively_computed() )  // But don't hoist IV increments
             // because they may end up above other uses of their phi forcing
             // their result register to be different from their input.
       ) {
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
    _node_latency->at_put_grow(self->_idx, end_latency);
    partial_latency_of_defs(self);
  }

  return least;
}


//------------------------------schedule_late-----------------------------------
// Now schedule all codes as LATE as possible.  This is the LCA in the
// dominator tree of all USES of a value.  Pick the block with the least
// loop nesting depth that is lowest in the dominator tree.
extern const char must_clone[];
void PhaseCFG::schedule_late(VectorSet &visited, Node_List &stack) {
#ifndef PRODUCT
  if (trace_opto_pipelining())
    tty->print("\n#---- schedule_late ----\n");
#endif

  Node_Backward_Iterator iter((Node *)_root, visited, stack, _bbs);
  Node *self;

  // Walk over all the nodes from last to first
  while (self = iter.next()) {
    Block* early = _bbs[self->_idx];   // Earliest legal placement

    if (self->is_top()) {
      // Top node goes in bb #2 with other constants.
      // It must be special-cased, because it has no out edges.
      early->add_inst(self);
      continue;
    }

    // No uses, just terminate
    if (self->outcnt() == 0) {
      assert(self->Opcode() == Op_MachProj, "sanity");
      continue;                   // Must be a dead machine projection
    }

    // If node is pinned in the block, then no scheduling can be done.
    if( self->pinned() )          // Pinned in block?
      continue;

    MachNode* mach = self->is_Mach() ? self->as_Mach() : NULL;
    if (mach) {
      switch (mach->ideal_Opcode()) {
      case Op_CreateEx:
        // Don't move exception creation
        early->add_inst(self);
        continue;
        break;
      case Op_CheckCastPP:
        // Don't move CheckCastPP nodes away from their input, if the input
        // is a rawptr (5071820).
        Node *def = self->in(1);
        if (def != NULL && def->bottom_type()->base() == Type::RawPtr) {
          early->add_inst(self);
#ifdef ASSERT
          _raw_oops.push(def);
#endif
          continue;
        }
        break;
      }
    }

    // Gather LCA of all uses
    Block *LCA = NULL;
    {
      for (DUIterator_Fast imax, i = self->fast_outs(imax); i < imax; i++) {
        // For all uses, find LCA
        Node* use = self->fast_out(i);
        LCA = raise_LCA_above_use(LCA, use, self, _bbs);
      }
    }  // (Hide defs of imax, i from rest of block.)

    // Place temps in the block of their use.  This isn't a
    // requirement for correctness but it reduces useless
    // interference between temps and other nodes.
    if (mach != NULL && mach->is_MachTemp()) {
      _bbs.map(self->_idx, LCA);
      LCA->add_inst(self);
      continue;
    }

    // Check if 'self' could be anti-dependent on memory
    if (self->needs_anti_dependence_check()) {
      // Hoist LCA above possible-defs and insert anti-dependences to
      // defs in new LCA block.
      LCA = insert_anti_dependences(LCA, self);
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
        C->record_method_not_compilable("late schedule failed: incorrect graph");
      }
      return;
    }

    // If there is no opportunity to hoist, then we're done.
    bool try_to_hoist = (LCA != early);

    // Must clone guys stay next to use; no hoisting allowed.
    // Also cannot hoist guys that alter memory or are otherwise not
    // allocatable (hoisting can make a value live longer, leading to
    // anti and output dependency problems which are normally resolved
    // by the register allocator giving everyone a different register).
    if (mach != NULL && must_clone[mach->ideal_Opcode()])
      try_to_hoist = false;

    Block* late = NULL;
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
void PhaseCFG::GlobalCodeMotion( Matcher &matcher, uint unique, Node_List &proj_list ) {
  ResourceMark rm;

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("\n---- Start GlobalCodeMotion ----\n");
  }
#endif

  // Initialize the bbs.map for things on the proj_list
  uint i;
  for( i=0; i < proj_list.size(); i++ )
    _bbs.map(proj_list[i]->_idx, NULL);

  // Set the basic block for Nodes pinned into blocks
  Arena *a = Thread::current()->resource_area();
  VectorSet visited(a);
  schedule_pinned_nodes( visited );

  // Find the earliest Block any instruction can be placed in.  Some
  // instructions are pinned into Blocks.  Unpinned instructions can
  // appear in last block in which all their inputs occur.
  visited.Clear();
  Node_List stack(a);
  stack.map( (unique >> 1) + 16, NULL); // Pre-grow the list
  if (!schedule_early(visited, stack)) {
    // Bailout without retry
    C->record_method_not_compilable("early schedule failed");
    return;
  }

  // Build Def-Use edges.
  proj_list.push(_root);        // Add real root as another root
  proj_list.pop();

  // Compute the latency information (via backwards walk) for all the
  // instructions in the graph
  _node_latency = new GrowableArray<uint>(); // resource_area allocation

  if( C->do_scheduling() )
    ComputeLatenciesBackwards(visited, stack);

  // Now schedule all codes as LATE as possible.  This is the LCA in the
  // dominator tree of all USES of a value.  Pick the block with the least
  // loop nesting depth that is lowest in the dominator tree.
  // ( visited.Clear() called in schedule_late()->Node_Backward_Iterator() )
  schedule_late(visited, stack);
  if( C->failing() ) {
    // schedule_late fails only when graph is incorrect.
    assert(!VerifyGraphEdges, "verification should have failed");
    return;
  }

  unique = C->unique();

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("\n---- Detect implicit null checks ----\n");
  }
#endif

  // Detect implicit-null-check opportunities.  Basically, find NULL checks
  // with suitable memory ops nearby.  Use the memory op to do the NULL check.
  // I can generate a memory op if there is not one nearby.
  if (C->is_method_compilation()) {
    // Don't do it for natives, adapters, or runtime stubs
    int allowed_reasons = 0;
    // ...and don't do it when there have been too many traps, globally.
    for (int reason = (int)Deoptimization::Reason_none+1;
         reason < Compile::trapHistLength; reason++) {
      assert(reason < BitsPerInt, "recode bit map");
      if (!C->too_many_traps((Deoptimization::DeoptReason) reason))
        allowed_reasons |= nth_bit(reason);
    }
    // By reversing the loop direction we get a very minor gain on mpegaudio.
    // Feel free to revert to a forward loop for clarity.
    // for( int i=0; i < (int)matcher._null_check_tests.size(); i+=2 ) {
    for( int i= matcher._null_check_tests.size()-2; i>=0; i-=2 ) {
      Node *proj = matcher._null_check_tests[i  ];
      Node *val  = matcher._null_check_tests[i+1];
      _bbs[proj->_idx]->implicit_null_check(this, proj, val, allowed_reasons);
      // The implicit_null_check will only perform the transformation
      // if the null branch is truly uncommon, *and* it leads to an
      // uncommon trap.  Combined with the too_many_traps guards
      // above, this prevents SEGV storms reported in 6366351,
      // by recompiling offending methods without this optimization.
    }
  }

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("\n---- Start Local Scheduling ----\n");
  }
#endif

  // Schedule locally.  Right now a simple topological sort.
  // Later, do a real latency aware scheduler.
  int *ready_cnt = NEW_RESOURCE_ARRAY(int,C->unique());
  memset( ready_cnt, -1, C->unique() * sizeof(int) );
  visited.Clear();
  for (i = 0; i < _num_blocks; i++) {
    if (!_blocks[i]->schedule_local(this, matcher, ready_cnt, visited)) {
      if (!C->failure_reason_is(C2Compiler::retry_no_subsuming_loads())) {
        C->record_method_not_compilable("local schedule failed");
      }
      return;
    }
  }

  // If we inserted any instructions between a Call and his CatchNode,
  // clone the instructions on all paths below the Catch.
  for( i=0; i < _num_blocks; i++ )
    _blocks[i]->call_catch_cleanup(_bbs);

#ifndef PRODUCT
  if (trace_opto_pipelining()) {
    tty->print("\n---- After GlobalCodeMotion ----\n");
    for (uint i = 0; i < _num_blocks; i++) {
      _blocks[i]->dump();
    }
  }
#endif
  // Dead.
  _node_latency = (GrowableArray<uint> *)0xdeadbeef;
}


//------------------------------Estimate_Block_Frequency-----------------------
// Estimate block frequencies based on IfNode probabilities.
void PhaseCFG::Estimate_Block_Frequency() {

  // Force conditional branches leading to uncommon traps to be unlikely,
  // not because we get to the uncommon_trap with less relative frequency,
  // but because an uncommon_trap typically causes a deopt, so we only get
  // there once.
  if (C->do_freq_based_layout()) {
    Block_List worklist;
    Block* root_blk = _blocks[0];
    for (uint i = 1; i < root_blk->num_preds(); i++) {
      Block *pb = _bbs[root_blk->pred(i)->_idx];
      if (pb->has_uncommon_code()) {
        worklist.push(pb);
      }
    }
    while (worklist.size() > 0) {
      Block* uct = worklist.pop();
      if (uct == _broot) continue;
      for (uint i = 1; i < uct->num_preds(); i++) {
        Block *pb = _bbs[uct->pred(i)->_idx];
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
  _outer_loop_freq = _root_loop->outer_loop_freq();

  // force paths ending at uncommon traps to be infrequent
  if (!C->do_freq_based_layout()) {
    Block_List worklist;
    Block* root_blk = _blocks[0];
    for (uint i = 1; i < root_blk->num_preds(); i++) {
      Block *pb = _bbs[root_blk->pred(i)->_idx];
      if (pb->has_uncommon_code()) {
        worklist.push(pb);
      }
    }
    while (worklist.size() > 0) {
      Block* uct = worklist.pop();
      uct->_freq = PROB_MIN;
      for (uint i = 1; i < uct->num_preds(); i++) {
        Block *pb = _bbs[uct->pred(i)->_idx];
        if (pb->_num_succs == 1 && pb->_freq > PROB_MIN) {
          worklist.push(pb);
        }
      }
    }
  }

#ifdef ASSERT
  for (uint i = 0; i < _num_blocks; i++ ) {
    Block *b = _blocks[i];
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
  assert( _blocks[0] == _broot, "" );
  for (uint i = 0; i < _num_blocks; i++ ) {
    Block *b = _blocks[i];
    // Check that _loop field are clear...we could clear them if not.
    assert(b->_loop == NULL, "clear _loop expected");
    // Sanity check that the RPO numbering is reflected in the _blocks array.
    // It doesn't have to be for the loop tree to be built, but if it is not,
    // then the blocks have been reordered since dom graph building...which
    // may question the RPO numbering
    assert(b->_rpo == i, "unexpected reverse post order number");
  }
#endif

  int idct = 0;
  CFGLoop* root_loop = new CFGLoop(idct++);

  Block_List worklist;

  // Assign blocks to loops
  for(uint i = _num_blocks - 1; i > 0; i-- ) { // skip Root block
    Block *b = _blocks[i];

    if (b->head()->is_Loop()) {
      Block* loop_head = b;
      assert(loop_head->num_preds() - 1 == 2, "loop must have 2 predecessors");
      Node* tail_n = loop_head->pred(LoopNode::LoopBackControl);
      Block* tail = _bbs[tail_n->_idx];

      // Defensively filter out Loop nodes for non-single-entry loops.
      // For all reasonable loops, the head occurs before the tail in RPO.
      if (i <= tail->_rpo) {

        // The tail and (recursive) predecessors of the tail
        // are made members of a new loop.

        assert(worklist.size() == 0, "nonempty worklist");
        CFGLoop* nloop = new CFGLoop(idct++);
        assert(loop_head->_loop == NULL, "just checking");
        loop_head->_loop = nloop;
        // Add to nloop so push_pred() will skip over inner loops
        nloop->add_member(loop_head);
        nloop->push_pred(loop_head, LoopNode::LoopBackControl, worklist, _bbs);

        while (worklist.size() > 0) {
          Block* member = worklist.pop();
          if (member != loop_head) {
            for (uint j = 1; j < member->num_preds(); j++) {
              nloop->push_pred(member, j, worklist, _bbs);
            }
          }
        }
      }
    }
  }

  // Create a member list for each loop consisting
  // of both blocks and (immediate child) loops.
  for (uint i = 0; i < _num_blocks; i++) {
    Block *b = _blocks[i];
    CFGLoop* lp = b->_loop;
    if (lp == NULL) {
      // Not assigned to a loop. Add it to the method's pseudo loop.
      b->_loop = root_loop;
      lp = root_loop;
    }
    if (lp == root_loop || b != lp->head()) { // loop heads are already members
      lp->add_member(b);
    }
    if (lp != root_loop) {
      if (lp->parent() == NULL) {
        // Not a nested loop. Make it a child of the method's pseudo loop.
        root_loop->add_nested_loop(lp);
      }
      if (b == lp->head()) {
        // Add nested loop to member list of parent loop.
        lp->parent()->add_member(lp);
      }
    }
  }

  return root_loop;
}

//------------------------------push_pred--------------------------------------
void CFGLoop::push_pred(Block* blk, int i, Block_List& worklist, Block_Array& node_to_blk) {
  Node* pred_n = blk->pred(i);
  Block* pred = node_to_blk[pred_n->_idx];
  CFGLoop *pred_loop = pred->_loop;
  if (pred_loop == NULL) {
    // Filter out blocks for non-single-entry loops.
    // For all reasonable loops, the head occurs before the tail in RPO.
    if (pred->_rpo > head()->_rpo) {
      pred->_loop = this;
      worklist.push(pred);
    }
  } else if (pred_loop != this) {
    // Nested loop.
    while (pred_loop->_parent != NULL && pred_loop->_parent != this) {
      pred_loop = pred_loop->_parent;
    }
    // Make pred's loop be a child
    if (pred_loop->_parent == NULL) {
      add_nested_loop(pred_loop);
      // Continue with loop entry predecessor.
      Block* pred_head = pred_loop->head();
      assert(pred_head->num_preds() - 1 == 2, "loop must have 2 predecessors");
      assert(pred_head != head(), "loop head in only one loop");
      push_pred(pred_head, LoopNode::EntryControl, worklist, node_to_blk);
    } else {
      assert(pred_loop->_parent == this && _parent == NULL, "just checking");
    }
  }
}

//------------------------------add_nested_loop--------------------------------
// Make cl a child of the current loop in the loop tree.
void CFGLoop::add_nested_loop(CFGLoop* cl) {
  assert(_parent == NULL, "no parent yet");
  assert(cl != this, "not my own parent");
  cl->_parent = this;
  CFGLoop* ch = _child;
  if (ch == NULL) {
    _child = cl;
  } else {
    while (ch->_sibling != NULL) { ch = ch->_sibling; }
    ch->_sibling = cl;
  }
}

//------------------------------compute_loop_depth-----------------------------
// Store the loop depth in each CFGLoop object.
// Recursively walk the children to do the same for them.
void CFGLoop::compute_loop_depth(int depth) {
  _depth = depth;
  CFGLoop* ch = _child;
  while (ch != NULL) {
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
  while (ch != NULL) {
    ch->compute_freq();
    ch = ch->_sibling;
  }
  assert (_members.length() > 0, "no empty loops");
  Block* hd = head();
  hd->_freq = 1.0f;
  for (int i = 0; i < _members.length(); i++) {
    CFGElement* s = _members.at(i);
    float freq = s->_freq;
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
        float prob = lp->_exits.at(k).get_prob();
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
    float exits_sum = 0.0f;
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
  Node *n = _nodes[eidx];  // Get ending Node

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
    if( _nodes[i + eidx + 1]->Opcode() == Op_IfFalse ) {
      return 1.0f - prob; // not taken
    } else {
      return prob; // taken
    }
  }

  case Op_Jump:
    // Divide the frequency between all successors evenly
    return 1.0f/_num_succs;

  case Op_Catch: {
    const CatchProjNode *ci = _nodes[i + eidx + 1]->as_CatchProj();
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

  case Op_NeverBranch:
    return 0.0f;

  case Op_TailCall:
  case Op_TailJump:
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
  Node *n = _nodes[eidx];  // Get ending Node

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
      const CatchProjNode *ci = _nodes[i + eidx + 1]->as_CatchProj();
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
  Node *n = _nodes[eidx];  // Get ending Node

  int op = n->Opcode();
  if (n->is_Mach()) {
    if (n->is_MachNullCheck()) {
      // In theory, either side can fall-thru, for simplicity sake,
      // let's say only the false branch can now.
      return _nodes[i + eidx + 1]->Opcode() == Op_IfFalse;
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
    const CatchProjNode *ci = _nodes[i + eidx + 1]->as_CatchProj();
    return ci->_con == CatchProjNode::fall_through_index;
  }

  case Op_Jump:
  case Op_NeverBranch:
  case Op_TailCall:
  case Op_TailJump:
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
  Node *n = _nodes[eidx];  // Get ending Node

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
  bool invert = (_nodes[s + eidx + 1]->Opcode() == Op_IfFalse);

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
void CFGLoop::update_succ_freq(Block* b, float freq) {
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
  float loop_freq = _freq * trip_count();
  _freq = loop_freq;
  for (int i = 0; i < _members.length(); i++) {
    CFGElement* s = _members.at(i);
    float block_freq = s->_freq * loop_freq;
    if (g_isnan(block_freq) || block_freq < MIN_BLOCK_FREQUENCY)
      block_freq = MIN_BLOCK_FREQUENCY;
    s->_freq = block_freq;
  }
  CFGLoop* ch = _child;
  while (ch != NULL) {
    ch->scale_freq();
    ch = ch->_sibling;
  }
}

// Frequency of outer loop
float CFGLoop::outer_loop_freq() const {
  if (_child != NULL) {
    return _child->_freq;
  }
  return _freq;
}

#ifndef PRODUCT
//------------------------------dump_tree--------------------------------------
void CFGLoop::dump_tree() const {
  dump();
  if (_child != NULL)   _child->dump_tree();
  if (_sibling != NULL) _sibling->dump_tree();
}

//------------------------------dump-------------------------------------------
void CFGLoop::dump() const {
  for (int i = 0; i < _depth; i++) tty->print("   ");
  tty->print("%s: %d  trip_count: %6.0f freq: %6.0f\n",
             _depth == 0 ? "Method" : "Loop", _id, trip_count(), _freq);
  for (int i = 0; i < _depth; i++) tty->print("   ");
  tty->print("         members:", _id);
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
    float prob = _exits.at(i).get_prob();
    tty->print(" ->%d@%d%%", blk->_pre_order, (int)(prob*100));
  }
  tty->print("\n");
}
#endif
