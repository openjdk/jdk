/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// Optimization - Graph Style

#include "incls/_precompiled.incl"
#include "incls/_block.cpp.incl"


//-----------------------------------------------------------------------------
void Block_Array::grow( uint i ) {
  assert(i >= Max(), "must be an overflow");
  debug_only(_limit = i+1);
  if( i < _size )  return;
  if( !_size ) {
    _size = 1;
    _blocks = (Block**)_arena->Amalloc( _size * sizeof(Block*) );
    _blocks[0] = NULL;
  }
  uint old = _size;
  while( i >= _size ) _size <<= 1;      // Double to fit
  _blocks = (Block**)_arena->Arealloc( _blocks, old*sizeof(Block*),_size*sizeof(Block*));
  Copy::zero_to_bytes( &_blocks[old], (_size-old)*sizeof(Block*) );
}

//=============================================================================
void Block_List::remove(uint i) {
  assert(i < _cnt, "index out of bounds");
  Copy::conjoint_words_to_lower((HeapWord*)&_blocks[i+1], (HeapWord*)&_blocks[i], ((_cnt-i-1)*sizeof(Block*)));
  pop(); // shrink list by one block
}

void Block_List::insert(uint i, Block *b) {
  push(b); // grow list by one block
  Copy::conjoint_words_to_higher((HeapWord*)&_blocks[i], (HeapWord*)&_blocks[i+1], ((_cnt-i-1)*sizeof(Block*)));
  _blocks[i] = b;
}


//=============================================================================

uint Block::code_alignment() {
  // Check for Root block
  if( _pre_order == 0 ) return CodeEntryAlignment;
  // Check for Start block
  if( _pre_order == 1 ) return InteriorEntryAlignment;
  // Check for loop alignment
  Node *h = head();
  if( h->is_Loop() && h->as_Loop()->is_inner_loop() )  {
    // Pre- and post-loops have low trip count so do not bother with
    // NOPs for align loop head.  The constants are hidden from tuning
    // but only because my "divide by 4" heuristic surely gets nearly
    // all possible gain (a "do not align at all" heuristic has a
    // chance of getting a really tiny gain).
    if( h->is_CountedLoop() && (h->as_CountedLoop()->is_pre_loop() ||
                                h->as_CountedLoop()->is_post_loop()) )
      return (OptoLoopAlignment > 4) ? (OptoLoopAlignment>>2) : 1;
    // Loops with low backedge frequency should not be aligned.
    Node *n = h->in(LoopNode::LoopBackControl)->in(0);
    if( n->is_MachIf() && n->as_MachIf()->_prob < 0.01 ) {
      return 1;             // Loop does not loop, more often than not!
    }
    return OptoLoopAlignment; // Otherwise align loop head
  }
  return 1;                     // no particular alignment
}

//-----------------------------------------------------------------------------
// Compute the size of first 'inst_cnt' instructions in this block.
// Return the number of instructions left to compute if the block has
// less then 'inst_cnt' instructions.
uint Block::compute_first_inst_size(uint& sum_size, uint inst_cnt,
                                    PhaseRegAlloc* ra) {
  uint last_inst = _nodes.size();
  for( uint j = 0; j < last_inst && inst_cnt > 0; j++ ) {
    uint inst_size = _nodes[j]->size(ra);
    if( inst_size > 0 ) {
      inst_cnt--;
      uint sz = sum_size + inst_size;
      if( sz <= (uint)OptoLoopAlignment ) {
        // Compute size of instructions which fit into fetch buffer only
        // since all inst_cnt instructions will not fit even if we align them.
        sum_size = sz;
      } else {
        return 0;
      }
    }
  }
  return inst_cnt;
}

//-----------------------------------------------------------------------------
uint Block::find_node( const Node *n ) const {
  for( uint i = 0; i < _nodes.size(); i++ ) {
    if( _nodes[i] == n )
      return i;
  }
  ShouldNotReachHere();
  return 0;
}

// Find and remove n from block list
void Block::find_remove( const Node *n ) {
  _nodes.remove(find_node(n));
}

//------------------------------is_Empty---------------------------------------
// Return empty status of a block.  Empty blocks contain only the head, other
// ideal nodes, and an optional trailing goto.
int Block::is_Empty() const {

  // Root or start block is not considered empty
  if (head()->is_Root() || head()->is_Start()) {
    return not_empty;
  }

  int success_result = completely_empty;
  int end_idx = _nodes.size()-1;

  // Check for ending goto
  if ((end_idx > 0) && (_nodes[end_idx]->is_Goto())) {
    success_result = empty_with_goto;
    end_idx--;
  }

  // Unreachable blocks are considered empty
  if (num_preds() <= 1) {
    return success_result;
  }

  // Ideal nodes are allowable in empty blocks: skip them  Only MachNodes
  // turn directly into code, because only MachNodes have non-trivial
  // emit() functions.
  while ((end_idx > 0) && !_nodes[end_idx]->is_Mach()) {
    end_idx--;
  }

  // No room for any interesting instructions?
  if (end_idx == 0) {
    return success_result;
  }

  return not_empty;
}

//------------------------------has_uncommon_code------------------------------
// Return true if the block's code implies that it is not likely to be
// executed infrequently.  Check to see if the block ends in a Halt or
// a low probability call.
bool Block::has_uncommon_code() const {
  Node* en = end();

  if (en->is_Goto())
    en = en->in(0);
  if (en->is_Catch())
    en = en->in(0);
  if (en->is_Proj() && en->in(0)->is_MachCall()) {
    MachCallNode* call = en->in(0)->as_MachCall();
    if (call->cnt() != COUNT_UNKNOWN && call->cnt() <= PROB_UNLIKELY_MAG(4)) {
      // This is true for slow-path stubs like new_{instance,array},
      // slow_arraycopy, complete_monitor_locking, uncommon_trap.
      // The magic number corresponds to the probability of an uncommon_trap,
      // even though it is a count not a probability.
      return true;
    }
  }

  int op = en->is_Mach() ? en->as_Mach()->ideal_Opcode() : en->Opcode();
  return op == Op_Halt;
}

//------------------------------is_uncommon------------------------------------
// True if block is low enough frequency or guarded by a test which
// mostly does not go here.
bool Block::is_uncommon( Block_Array &bbs ) const {
  // Initial blocks must never be moved, so are never uncommon.
  if (head()->is_Root() || head()->is_Start())  return false;

  // Check for way-low freq
  if( _freq < BLOCK_FREQUENCY(0.00001f) ) return true;

  // Look for code shape indicating uncommon_trap or slow path
  if (has_uncommon_code()) return true;

  const float epsilon = 0.05f;
  const float guard_factor = PROB_UNLIKELY_MAG(4) / (1.f - epsilon);
  uint uncommon_preds = 0;
  uint freq_preds = 0;
  uint uncommon_for_freq_preds = 0;

  for( uint i=1; i<num_preds(); i++ ) {
    Block* guard = bbs[pred(i)->_idx];
    // Check to see if this block follows its guard 1 time out of 10000
    // or less.
    //
    // See list of magnitude-4 unlikely probabilities in cfgnode.hpp which
    // we intend to be "uncommon", such as slow-path TLE allocation,
    // predicted call failure, and uncommon trap triggers.
    //
    // Use an epsilon value of 5% to allow for variability in frequency
    // predictions and floating point calculations. The net effect is
    // that guard_factor is set to 9500.
    //
    // Ignore low-frequency blocks.
    // The next check is (guard->_freq < 1.e-5 * 9500.).
    if(guard->_freq*BLOCK_FREQUENCY(guard_factor) < BLOCK_FREQUENCY(0.00001f)) {
      uncommon_preds++;
    } else {
      freq_preds++;
      if( _freq < guard->_freq * guard_factor ) {
        uncommon_for_freq_preds++;
      }
    }
  }
  if( num_preds() > 1 &&
      // The block is uncommon if all preds are uncommon or
      (uncommon_preds == (num_preds()-1) ||
      // it is uncommon for all frequent preds.
       uncommon_for_freq_preds == freq_preds) ) {
    return true;
  }
  return false;
}

//------------------------------dump-------------------------------------------
#ifndef PRODUCT
void Block::dump_bidx(const Block* orig) const {
  if (_pre_order) tty->print("B%d",_pre_order);
  else tty->print("N%d", head()->_idx);

  if (Verbose && orig != this) {
    // Dump the original block's idx
    tty->print(" (");
    orig->dump_bidx(orig);
    tty->print(")");
  }
}

void Block::dump_pred(const Block_Array *bbs, Block* orig) const {
  if (is_connector()) {
    for (uint i=1; i<num_preds(); i++) {
      Block *p = ((*bbs)[pred(i)->_idx]);
      p->dump_pred(bbs, orig);
    }
  } else {
    dump_bidx(orig);
    tty->print(" ");
  }
}

void Block::dump_head( const Block_Array *bbs ) const {
  // Print the basic block
  dump_bidx(this);
  tty->print(": #\t");

  // Print the incoming CFG edges and the outgoing CFG edges
  for( uint i=0; i<_num_succs; i++ ) {
    non_connector_successor(i)->dump_bidx(_succs[i]);
    tty->print(" ");
  }
  tty->print("<- ");
  if( head()->is_block_start() ) {
    for (uint i=1; i<num_preds(); i++) {
      Node *s = pred(i);
      if (bbs) {
        Block *p = (*bbs)[s->_idx];
        p->dump_pred(bbs, p);
      } else {
        while (!s->is_block_start())
          s = s->in(0);
        tty->print("N%d ", s->_idx );
      }
    }
  } else
    tty->print("BLOCK HEAD IS JUNK  ");

  // Print loop, if any
  const Block *bhead = this;    // Head of self-loop
  Node *bh = bhead->head();
  if( bbs && bh->is_Loop() && !head()->is_Root() ) {
    LoopNode *loop = bh->as_Loop();
    const Block *bx = (*bbs)[loop->in(LoopNode::LoopBackControl)->_idx];
    while (bx->is_connector()) {
      bx = (*bbs)[bx->pred(1)->_idx];
    }
    tty->print("\tLoop: B%d-B%d ", bhead->_pre_order, bx->_pre_order);
    // Dump any loop-specific bits, especially for CountedLoops.
    loop->dump_spec(tty);
  }
  tty->print(" Freq: %g",_freq);
  if( Verbose || WizardMode ) {
    tty->print(" IDom: %d/#%d", _idom ? _idom->_pre_order : 0, _dom_depth);
    tty->print(" RegPressure: %d",_reg_pressure);
    tty->print(" IHRP Index: %d",_ihrp_index);
    tty->print(" FRegPressure: %d",_freg_pressure);
    tty->print(" FHRP Index: %d",_fhrp_index);
  }
  tty->print_cr("");
}

void Block::dump() const { dump(0); }

void Block::dump( const Block_Array *bbs ) const {
  dump_head(bbs);
  uint cnt = _nodes.size();
  for( uint i=0; i<cnt; i++ )
    _nodes[i]->dump();
  tty->print("\n");
}
#endif

//=============================================================================
//------------------------------PhaseCFG---------------------------------------
PhaseCFG::PhaseCFG( Arena *a, RootNode *r, Matcher &m ) :
  Phase(CFG),
  _bbs(a),
  _root(r)
#ifndef PRODUCT
  , _trace_opto_pipelining(TraceOptoPipelining || C->method_has_option("TraceOptoPipelining"))
#endif
{
  ResourceMark rm;
  // I'll need a few machine-specific GotoNodes.  Make an Ideal GotoNode,
  // then Match it into a machine-specific Node.  Then clone the machine
  // Node on demand.
  Node *x = new (C, 1) GotoNode(NULL);
  x->init_req(0, x);
  _goto = m.match_tree(x);
  assert(_goto != NULL, "");
  _goto->set_req(0,_goto);

  // Build the CFG in Reverse Post Order
  _num_blocks = build_cfg();
  _broot = _bbs[_root->_idx];
}

//------------------------------build_cfg--------------------------------------
// Build a proper looking CFG.  Make every block begin with either a StartNode
// or a RegionNode.  Make every block end with either a Goto, If or Return.
// The RootNode both starts and ends it's own block.  Do this with a recursive
// backwards walk over the control edges.
uint PhaseCFG::build_cfg() {
  Arena *a = Thread::current()->resource_area();
  VectorSet visited(a);

  // Allocate stack with enough space to avoid frequent realloc
  Node_Stack nstack(a, C->unique() >> 1);
  nstack.push(_root, 0);
  uint sum = 0;                 // Counter for blocks

  while (nstack.is_nonempty()) {
    // node and in's index from stack's top
    // 'np' is _root (see above) or RegionNode, StartNode: we push on stack
    // only nodes which point to the start of basic block (see below).
    Node *np = nstack.node();
    // idx > 0, except for the first node (_root) pushed on stack
    // at the beginning when idx == 0.
    // We will use the condition (idx == 0) later to end the build.
    uint idx = nstack.index();
    Node *proj = np->in(idx);
    const Node *x = proj->is_block_proj();
    // Does the block end with a proper block-ending Node?  One of Return,
    // If or Goto? (This check should be done for visited nodes also).
    if (x == NULL) {                    // Does not end right...
      Node *g = _goto->clone(); // Force it to end in a Goto
      g->set_req(0, proj);
      np->set_req(idx, g);
      x = proj = g;
    }
    if (!visited.test_set(x->_idx)) { // Visit this block once
      // Skip any control-pinned middle'in stuff
      Node *p = proj;
      do {
        proj = p;                   // Update pointer to last Control
        p = p->in(0);               // Move control forward
      } while( !p->is_block_proj() &&
               !p->is_block_start() );
      // Make the block begin with one of Region or StartNode.
      if( !p->is_block_start() ) {
        RegionNode *r = new (C, 2) RegionNode( 2 );
        r->init_req(1, p);         // Insert RegionNode in the way
        proj->set_req(0, r);        // Insert RegionNode in the way
        p = r;
      }
      // 'p' now points to the start of this basic block

      // Put self in array of basic blocks
      Block *bb = new (_bbs._arena) Block(_bbs._arena,p);
      _bbs.map(p->_idx,bb);
      _bbs.map(x->_idx,bb);
      if( x != p )                  // Only for root is x == p
        bb->_nodes.push((Node*)x);

      // Now handle predecessors
      ++sum;                        // Count 1 for self block
      uint cnt = bb->num_preds();
      for (int i = (cnt - 1); i > 0; i-- ) { // For all predecessors
        Node *prevproj = p->in(i);  // Get prior input
        assert( !prevproj->is_Con(), "dead input not removed" );
        // Check to see if p->in(i) is a "control-dependent" CFG edge -
        // i.e., it splits at the source (via an IF or SWITCH) and merges
        // at the destination (via a many-input Region).
        // This breaks critical edges.  The RegionNode to start the block
        // will be added when <p,i> is pulled off the node stack
        if ( cnt > 2 ) {             // Merging many things?
          assert( prevproj== bb->pred(i),"");
          if(prevproj->is_block_proj() != prevproj) { // Control-dependent edge?
            // Force a block on the control-dependent edge
            Node *g = _goto->clone();       // Force it to end in a Goto
            g->set_req(0,prevproj);
            p->set_req(i,g);
          }
        }
        nstack.push(p, i);  // 'p' is RegionNode or StartNode
      }
    } else { // Post-processing visited nodes
      nstack.pop();                 // remove node from stack
      // Check if it the fist node pushed on stack at the beginning.
      if (idx == 0) break;          // end of the build
      // Find predecessor basic block
      Block *pb = _bbs[x->_idx];
      // Insert into nodes array, if not already there
      if( !_bbs.lookup(proj->_idx) ) {
        assert( x != proj, "" );
        // Map basic block of projection
        _bbs.map(proj->_idx,pb);
        pb->_nodes.push(proj);
      }
      // Insert self as a child of my predecessor block
      pb->_succs.map(pb->_num_succs++, _bbs[np->_idx]);
      assert( pb->_nodes[ pb->_nodes.size() - pb->_num_succs ]->is_block_proj(),
              "too many control users, not a CFG?" );
    }
  }
  // Return number of basic blocks for all children and self
  return sum;
}

//------------------------------insert_goto_at---------------------------------
// Inserts a goto & corresponding basic block between
// block[block_no] and its succ_no'th successor block
void PhaseCFG::insert_goto_at(uint block_no, uint succ_no) {
  // get block with block_no
  assert(block_no < _num_blocks, "illegal block number");
  Block* in  = _blocks[block_no];
  // get successor block succ_no
  assert(succ_no < in->_num_succs, "illegal successor number");
  Block* out = in->_succs[succ_no];
  // Compute frequency of the new block. Do this before inserting
  // new block in case succ_prob() needs to infer the probability from
  // surrounding blocks.
  float freq = in->_freq * in->succ_prob(succ_no);
  // get ProjNode corresponding to the succ_no'th successor of the in block
  ProjNode* proj = in->_nodes[in->_nodes.size() - in->_num_succs + succ_no]->as_Proj();
  // create region for basic block
  RegionNode* region = new (C, 2) RegionNode(2);
  region->init_req(1, proj);
  // setup corresponding basic block
  Block* block = new (_bbs._arena) Block(_bbs._arena, region);
  _bbs.map(region->_idx, block);
  C->regalloc()->set_bad(region->_idx);
  // add a goto node
  Node* gto = _goto->clone(); // get a new goto node
  gto->set_req(0, region);
  // add it to the basic block
  block->_nodes.push(gto);
  _bbs.map(gto->_idx, block);
  C->regalloc()->set_bad(gto->_idx);
  // hook up successor block
  block->_succs.map(block->_num_succs++, out);
  // remap successor's predecessors if necessary
  for (uint i = 1; i < out->num_preds(); i++) {
    if (out->pred(i) == proj) out->head()->set_req(i, gto);
  }
  // remap predecessor's successor to new block
  in->_succs.map(succ_no, block);
  // Set the frequency of the new block
  block->_freq = freq;
  // add new basic block to basic block list
  _blocks.insert(block_no + 1, block);
  _num_blocks++;
}

//------------------------------no_flip_branch---------------------------------
// Does this block end in a multiway branch that cannot have the default case
// flipped for another case?
static bool no_flip_branch( Block *b ) {
  int branch_idx = b->_nodes.size() - b->_num_succs-1;
  if( branch_idx < 1 ) return false;
  Node *bra = b->_nodes[branch_idx];
  if( bra->is_Catch() ) return true;
  if( bra->is_Mach() ) {
    if( bra->is_MachNullCheck() ) return true;
    int iop = bra->as_Mach()->ideal_Opcode();
    if( iop == Op_FastLock || iop == Op_FastUnlock )
      return true;
  }
  return false;
}

//------------------------------convert_NeverBranch_to_Goto--------------------
// Check for NeverBranch at block end.  This needs to become a GOTO to the
// true target.  NeverBranch are treated as a conditional branch that always
// goes the same direction for most of the optimizer and are used to give a
// fake exit path to infinite loops.  At this late stage they need to turn
// into Goto's so that when you enter the infinite loop you indeed hang.
void PhaseCFG::convert_NeverBranch_to_Goto(Block *b) {
  // Find true target
  int end_idx = b->end_idx();
  int idx = b->_nodes[end_idx+1]->as_Proj()->_con;
  Block *succ = b->_succs[idx];
  Node* gto = _goto->clone(); // get a new goto node
  gto->set_req(0, b->head());
  Node *bp = b->_nodes[end_idx];
  b->_nodes.map(end_idx,gto); // Slam over NeverBranch
  _bbs.map(gto->_idx, b);
  C->regalloc()->set_bad(gto->_idx);
  b->_nodes.pop();              // Yank projections
  b->_nodes.pop();              // Yank projections
  b->_succs.map(0,succ);        // Map only successor
  b->_num_succs = 1;
  // remap successor's predecessors if necessary
  uint j;
  for( j = 1; j < succ->num_preds(); j++)
    if( succ->pred(j)->in(0) == bp )
      succ->head()->set_req(j, gto);
  // Kill alternate exit path
  Block *dead = b->_succs[1-idx];
  for( j = 1; j < dead->num_preds(); j++)
    if( dead->pred(j)->in(0) == bp )
      break;
  // Scan through block, yanking dead path from
  // all regions and phis.
  dead->head()->del_req(j);
  for( int k = 1; dead->_nodes[k]->is_Phi(); k++ )
    dead->_nodes[k]->del_req(j);
}

//------------------------------MoveToNext-------------------------------------
// Helper function to move block bx to the slot following b_index. Return
// true if the move is successful, otherwise false
bool PhaseCFG::MoveToNext(Block* bx, uint b_index) {
  if (bx == NULL) return false;

  // Return false if bx is already scheduled.
  uint bx_index = bx->_pre_order;
  if ((bx_index <= b_index) && (_blocks[bx_index] == bx)) {
    return false;
  }

  // Find the current index of block bx on the block list
  bx_index = b_index + 1;
  while( bx_index < _num_blocks && _blocks[bx_index] != bx ) bx_index++;
  assert(_blocks[bx_index] == bx, "block not found");

  // If the previous block conditionally falls into bx, return false,
  // because moving bx will create an extra jump.
  for(uint k = 1; k < bx->num_preds(); k++ ) {
    Block* pred = _bbs[bx->pred(k)->_idx];
    if (pred == _blocks[bx_index-1]) {
      if (pred->_num_succs != 1) {
        return false;
      }
    }
  }

  // Reinsert bx just past block 'b'
  _blocks.remove(bx_index);
  _blocks.insert(b_index + 1, bx);
  return true;
}

//------------------------------MoveToEnd--------------------------------------
// Move empty and uncommon blocks to the end.
void PhaseCFG::MoveToEnd(Block *b, uint i) {
  int e = b->is_Empty();
  if (e != Block::not_empty) {
    if (e == Block::empty_with_goto) {
      // Remove the goto, but leave the block.
      b->_nodes.pop();
    }
    // Mark this block as a connector block, which will cause it to be
    // ignored in certain functions such as non_connector_successor().
    b->set_connector();
  }
  // Move the empty block to the end, and don't recheck.
  _blocks.remove(i);
  _blocks.push(b);
}

//------------------------------RemoveEmpty------------------------------------
// Remove empty basic blocks and useless branches.
void PhaseCFG::RemoveEmpty() {
  // Move uncommon blocks to the end
  uint last = _num_blocks;
  uint i;
  assert( _blocks[0] == _broot, "" );
  for( i = 1; i < last; i++ ) {
    Block *b = _blocks[i];

    // Check for NeverBranch at block end.  This needs to become a GOTO to the
    // true target.  NeverBranch are treated as a conditional branch that
    // always goes the same direction for most of the optimizer and are used
    // to give a fake exit path to infinite loops.  At this late stage they
    // need to turn into Goto's so that when you enter the infinite loop you
    // indeed hang.
    if( b->_nodes[b->end_idx()]->Opcode() == Op_NeverBranch )
      convert_NeverBranch_to_Goto(b);

    // Look for uncommon blocks and move to end.
    if( b->is_uncommon(_bbs) ) {
      MoveToEnd(b, i);
      last--;                   // No longer check for being uncommon!
      if( no_flip_branch(b) ) { // Fall-thru case must follow?
        b = _blocks[i];         // Find the fall-thru block
        MoveToEnd(b, i);
        last--;
      }
      i--;                      // backup block counter post-increment
    }
  }

  // Remove empty blocks
  uint j1;
  last = _num_blocks;
  for( i=0; i < last; i++ ) {
    Block *b = _blocks[i];
    if (i > 0) {
      if (b->is_Empty() != Block::not_empty) {
        MoveToEnd(b, i);
        last--;
        i--;
      }
    }
  } // End of for all blocks

  // Fixup final control flow for the blocks.  Remove jump-to-next
  // block.  If neither arm of a IF follows the conditional branch, we
  // have to add a second jump after the conditional.  We place the
  // TRUE branch target in succs[0] for both GOTOs and IFs.
  for( i=0; i < _num_blocks; i++ ) {
    Block *b = _blocks[i];
    b->_pre_order = i;          // turn pre-order into block-index

    // Connector blocks need no further processing.
    if (b->is_connector()) {
      assert((i+1) == _num_blocks || _blocks[i+1]->is_connector(),
             "All connector blocks should sink to the end");
      continue;
    }
    assert(b->is_Empty() != Block::completely_empty,
           "Empty blocks should be connectors");

    Block *bnext = (i < _num_blocks-1) ? _blocks[i+1] : NULL;
    Block *bs0 = b->non_connector_successor(0);

    // Check for multi-way branches where I cannot negate the test to
    // exchange the true and false targets.
    if( no_flip_branch( b ) ) {
      // Find fall through case - if must fall into its target
      int branch_idx = b->_nodes.size() - b->_num_succs;
      for (uint j2 = 0; j2 < b->_num_succs; j2++) {
        const ProjNode* p = b->_nodes[branch_idx + j2]->as_Proj();
        if (p->_con == 0) {
          // successor j2 is fall through case
          if (b->non_connector_successor(j2) != bnext) {
            // but it is not the next block => insert a goto
            insert_goto_at(i, j2);
          }
          // Put taken branch in slot 0
          if( j2 == 0 && b->_num_succs == 2) {
            // Flip targets in succs map
            Block *tbs0 = b->_succs[0];
            Block *tbs1 = b->_succs[1];
            b->_succs.map( 0, tbs1 );
            b->_succs.map( 1, tbs0 );
          }
          break;
        }
      }
      // Remove all CatchProjs
      for (j1 = 0; j1 < b->_num_succs; j1++) b->_nodes.pop();

    } else if (b->_num_succs == 1) {
      // Block ends in a Goto?
      if (bnext == bs0) {
        // We fall into next block; remove the Goto
        b->_nodes.pop();
      }

    } else if( b->_num_succs == 2 ) { // Block ends in a If?
      // Get opcode of 1st projection (matches _succs[0])
      // Note: Since this basic block has 2 exits, the last 2 nodes must
      //       be projections (in any order), the 3rd last node must be
      //       the IfNode (we have excluded other 2-way exits such as
      //       CatchNodes already).
      MachNode *iff   = b->_nodes[b->_nodes.size()-3]->as_Mach();
      ProjNode *proj0 = b->_nodes[b->_nodes.size()-2]->as_Proj();
      ProjNode *proj1 = b->_nodes[b->_nodes.size()-1]->as_Proj();

      // Assert that proj0 and succs[0] match up. Similarly for proj1 and succs[1].
      assert(proj0->raw_out(0) == b->_succs[0]->head(), "Mismatch successor 0");
      assert(proj1->raw_out(0) == b->_succs[1]->head(), "Mismatch successor 1");

      Block *bs1 = b->non_connector_successor(1);

      // Check for neither successor block following the current
      // block ending in a conditional. If so, move one of the
      // successors after the current one, provided that the
      // successor was previously unscheduled, but moveable
      // (i.e., all paths to it involve a branch).
      if( bnext != bs0 && bnext != bs1 ) {

        // Choose the more common successor based on the probability
        // of the conditional branch.
        Block *bx = bs0;
        Block *by = bs1;

        // _prob is the probability of taking the true path. Make
        // p the probability of taking successor #1.
        float p = iff->as_MachIf()->_prob;
        if( proj0->Opcode() == Op_IfTrue ) {
          p = 1.0 - p;
        }

        // Prefer successor #1 if p > 0.5
        if (p > PROB_FAIR) {
          bx = bs1;
          by = bs0;
        }

        // Attempt the more common successor first
        if (MoveToNext(bx, i)) {
          bnext = bx;
        } else if (MoveToNext(by, i)) {
          bnext = by;
        }
      }

      // Check for conditional branching the wrong way.  Negate
      // conditional, if needed, so it falls into the following block
      // and branches to the not-following block.

      // Check for the next block being in succs[0].  We are going to branch
      // to succs[0], so we want the fall-thru case as the next block in
      // succs[1].
      if (bnext == bs0) {
        // Fall-thru case in succs[0], so flip targets in succs map
        Block *tbs0 = b->_succs[0];
        Block *tbs1 = b->_succs[1];
        b->_succs.map( 0, tbs1 );
        b->_succs.map( 1, tbs0 );
        // Flip projection for each target
        { ProjNode *tmp = proj0; proj0 = proj1; proj1 = tmp; }

      } else if( bnext == bs1 ) { // Fall-thru is already in succs[1]

      } else {                  // Else need a double-branch

        // The existing conditional branch need not change.
        // Add a unconditional branch to the false target.
        // Alas, it must appear in its own block and adding a
        // block this late in the game is complicated.  Sigh.
        insert_goto_at(i, 1);
      }

      // Make sure we TRUE branch to the target
      if( proj0->Opcode() == Op_IfFalse )
        iff->negate();

      b->_nodes.pop();          // Remove IfFalse & IfTrue projections
      b->_nodes.pop();

    } else {
      // Multi-exit block, e.g. a switch statement
      // But we don't need to do anything here
    }

  } // End of for all blocks

}


//------------------------------dump-------------------------------------------
#ifndef PRODUCT
void PhaseCFG::_dump_cfg( const Node *end, VectorSet &visited  ) const {
  const Node *x = end->is_block_proj();
  assert( x, "not a CFG" );

  // Do not visit this block again
  if( visited.test_set(x->_idx) ) return;

  // Skip through this block
  const Node *p = x;
  do {
    p = p->in(0);               // Move control forward
    assert( !p->is_block_proj() || p->is_Root(), "not a CFG" );
  } while( !p->is_block_start() );

  // Recursively visit
  for( uint i=1; i<p->req(); i++ )
    _dump_cfg(p->in(i),visited);

  // Dump the block
  _bbs[p->_idx]->dump(&_bbs);
}

void PhaseCFG::dump( ) const {
  tty->print("\n--- CFG --- %d BBs\n",_num_blocks);
  if( _blocks.size() ) {        // Did we do basic-block layout?
    for( uint i=0; i<_num_blocks; i++ )
      _blocks[i]->dump(&_bbs);
  } else {                      // Else do it with a DFS
    VectorSet visited(_bbs._arena);
    _dump_cfg(_root,visited);
  }
}

void PhaseCFG::dump_headers() {
  for( uint i = 0; i < _num_blocks; i++ ) {
    if( _blocks[i] == NULL ) continue;
    _blocks[i]->dump_head(&_bbs);
  }
}

void PhaseCFG::verify( ) const {
  // Verify sane CFG
  for( uint i = 0; i < _num_blocks; i++ ) {
    Block *b = _blocks[i];
    uint cnt = b->_nodes.size();
    uint j;
    for( j = 0; j < cnt; j++ ) {
      Node *n = b->_nodes[j];
      assert( _bbs[n->_idx] == b, "" );
      if( j >= 1 && n->is_Mach() &&
          n->as_Mach()->ideal_Opcode() == Op_CreateEx ) {
        assert( j == 1 || b->_nodes[j-1]->is_Phi(),
                "CreateEx must be first instruction in block" );
      }
      for( uint k = 0; k < n->req(); k++ ) {
        Node *use = n->in(k);
        if( use && use != n ) {
          assert( _bbs[use->_idx] || use->is_Con(),
                  "must have block; constants for debug info ok" );
        }
      }
    }

    j = b->end_idx();
    Node *bp = (Node*)b->_nodes[b->_nodes.size()-1]->is_block_proj();
    assert( bp, "last instruction must be a block proj" );
    assert( bp == b->_nodes[j], "wrong number of successors for this block" );
    if( bp->is_Catch() ) {
      while( b->_nodes[--j]->Opcode() == Op_MachProj ) ;
      assert( b->_nodes[j]->is_Call(), "CatchProj must follow call" );
    }
    else if( bp->is_Mach() && bp->as_Mach()->ideal_Opcode() == Op_If ) {
      assert( b->_num_succs == 2, "Conditional branch must have two targets");
    }
  }
}
#endif

//=============================================================================
//------------------------------UnionFind--------------------------------------
UnionFind::UnionFind( uint max ) : _cnt(max), _max(max), _indices(NEW_RESOURCE_ARRAY(uint,max)) {
  Copy::zero_to_bytes( _indices, sizeof(uint)*max );
}

void UnionFind::extend( uint from_idx, uint to_idx ) {
  _nesting.check();
  if( from_idx >= _max ) {
    uint size = 16;
    while( size <= from_idx ) size <<=1;
    _indices = REALLOC_RESOURCE_ARRAY( uint, _indices, _max, size );
    _max = size;
  }
  while( _cnt <= from_idx ) _indices[_cnt++] = 0;
  _indices[from_idx] = to_idx;
}

void UnionFind::reset( uint max ) {
  assert( max <= max_uint, "Must fit within uint" );
  // Force the Union-Find mapping to be at least this large
  extend(max,0);
  // Initialize to be the ID mapping.
  for( uint i=0; i<_max; i++ ) map(i,i);
}

//------------------------------Find_compress----------------------------------
// Straight out of Tarjan's union-find algorithm
uint UnionFind::Find_compress( uint idx ) {
  uint cur  = idx;
  uint next = lookup(cur);
  while( next != cur ) {        // Scan chain of equivalences
    assert( next < cur, "always union smaller" );
    cur = next;                 // until find a fixed-point
    next = lookup(cur);
  }
  // Core of union-find algorithm: update chain of
  // equivalences to be equal to the root.
  while( idx != next ) {
    uint tmp = lookup(idx);
    map(idx, next);
    idx = tmp;
  }
  return idx;
}

//------------------------------Find_const-------------------------------------
// Like Find above, but no path compress, so bad asymptotic behavior
uint UnionFind::Find_const( uint idx ) const {
  if( idx == 0 ) return idx;    // Ignore the zero idx
  // Off the end?  This can happen during debugging dumps
  // when data structures have not finished being updated.
  if( idx >= _max ) return idx;
  uint next = lookup(idx);
  while( next != idx ) {        // Scan chain of equivalences
    assert( next < idx, "always union smaller" );
    idx = next;                 // until find a fixed-point
    next = lookup(idx);
  }
  return next;
}

//------------------------------Union------------------------------------------
// union 2 sets together.
void UnionFind::Union( uint idx1, uint idx2 ) {
  uint src = Find(idx1);
  uint dst = Find(idx2);
  assert( src, "" );
  assert( dst, "" );
  assert( src < _max, "oob" );
  assert( dst < _max, "oob" );
  assert( src < dst, "always union smaller" );
  map(dst,src);
}
