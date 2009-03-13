/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_ifg.cpp.incl"

#define EXACT_PRESSURE 1

//=============================================================================
//------------------------------IFG--------------------------------------------
PhaseIFG::PhaseIFG( Arena *arena ) : Phase(Interference_Graph), _arena(arena) {
}

//------------------------------init-------------------------------------------
void PhaseIFG::init( uint maxlrg ) {
  _maxlrg = maxlrg;
  _yanked = new (_arena) VectorSet(_arena);
  _is_square = false;
  // Make uninitialized adjacency lists
  _adjs = (IndexSet*)_arena->Amalloc(sizeof(IndexSet)*maxlrg);
  // Also make empty live range structures
  _lrgs = (LRG *)_arena->Amalloc( maxlrg * sizeof(LRG) );
  memset(_lrgs,0,sizeof(LRG)*maxlrg);
  // Init all to empty
  for( uint i = 0; i < maxlrg; i++ ) {
    _adjs[i].initialize(maxlrg);
    _lrgs[i].Set_All();
  }
}

//------------------------------add--------------------------------------------
// Add edge between vertices a & b.  These are sorted (triangular matrix),
// then the smaller number is inserted in the larger numbered array.
int PhaseIFG::add_edge( uint a, uint b ) {
  lrgs(a).invalid_degree();
  lrgs(b).invalid_degree();
  // Sort a and b, so that a is bigger
  assert( !_is_square, "only on triangular" );
  if( a < b ) { uint tmp = a; a = b; b = tmp; }
  return _adjs[a].insert( b );
}

//------------------------------add_vector-------------------------------------
// Add an edge between 'a' and everything in the vector.
void PhaseIFG::add_vector( uint a, IndexSet *vec ) {
  // IFG is triangular, so do the inserts where 'a' < 'b'.
  assert( !_is_square, "only on triangular" );
  IndexSet *adjs_a = &_adjs[a];
  if( !vec->count() ) return;

  IndexSetIterator elements(vec);
  uint neighbor;
  while ((neighbor = elements.next()) != 0) {
    add_edge( a, neighbor );
  }
}

//------------------------------test-------------------------------------------
// Is there an edge between a and b?
int PhaseIFG::test_edge( uint a, uint b ) const {
  // Sort a and b, so that a is larger
  assert( !_is_square, "only on triangular" );
  if( a < b ) { uint tmp = a; a = b; b = tmp; }
  return _adjs[a].member(b);
}

//------------------------------SquareUp---------------------------------------
// Convert triangular matrix to square matrix
void PhaseIFG::SquareUp() {
  assert( !_is_square, "only on triangular" );

  // Simple transpose
  for( uint i = 0; i < _maxlrg; i++ ) {
    IndexSetIterator elements(&_adjs[i]);
    uint datum;
    while ((datum = elements.next()) != 0) {
      _adjs[datum].insert( i );
    }
  }
  _is_square = true;
}

//------------------------------Compute_Effective_Degree-----------------------
// Compute effective degree in bulk
void PhaseIFG::Compute_Effective_Degree() {
  assert( _is_square, "only on square" );

  for( uint i = 0; i < _maxlrg; i++ )
    lrgs(i).set_degree(effective_degree(i));
}

//------------------------------test_edge_sq-----------------------------------
int PhaseIFG::test_edge_sq( uint a, uint b ) const {
  assert( _is_square, "only on square" );
  // Swap, so that 'a' has the lesser count.  Then binary search is on
  // the smaller of a's list and b's list.
  if( neighbor_cnt(a) > neighbor_cnt(b) ) { uint tmp = a; a = b; b = tmp; }
  //return _adjs[a].unordered_member(b);
  return _adjs[a].member(b);
}

//------------------------------Union------------------------------------------
// Union edges of B into A
void PhaseIFG::Union( uint a, uint b ) {
  assert( _is_square, "only on square" );
  IndexSet *A = &_adjs[a];
  IndexSetIterator b_elements(&_adjs[b]);
  uint datum;
  while ((datum = b_elements.next()) != 0) {
    if(A->insert(datum)) {
      _adjs[datum].insert(a);
      lrgs(a).invalid_degree();
      lrgs(datum).invalid_degree();
    }
  }
}

//------------------------------remove_node------------------------------------
// Yank a Node and all connected edges from the IFG.  Return a
// list of neighbors (edges) yanked.
IndexSet *PhaseIFG::remove_node( uint a ) {
  assert( _is_square, "only on square" );
  assert( !_yanked->test(a), "" );
  _yanked->set(a);

  // I remove the LRG from all neighbors.
  IndexSetIterator elements(&_adjs[a]);
  LRG &lrg_a = lrgs(a);
  uint datum;
  while ((datum = elements.next()) != 0) {
    _adjs[datum].remove(a);
    lrgs(datum).inc_degree( -lrg_a.compute_degree(lrgs(datum)) );
  }
  return neighbors(a);
}

//------------------------------re_insert--------------------------------------
// Re-insert a yanked Node.
void PhaseIFG::re_insert( uint a ) {
  assert( _is_square, "only on square" );
  assert( _yanked->test(a), "" );
  (*_yanked) >>= a;

  IndexSetIterator elements(&_adjs[a]);
  uint datum;
  while ((datum = elements.next()) != 0) {
    _adjs[datum].insert(a);
    lrgs(datum).invalid_degree();
  }
}

//------------------------------compute_degree---------------------------------
// Compute the degree between 2 live ranges.  If both live ranges are
// aligned-adjacent powers-of-2 then we use the MAX size.  If either is
// mis-aligned (or for Fat-Projections, not-adjacent) then we have to
// MULTIPLY the sizes.  Inspect Brigg's thesis on register pairs to see why
// this is so.
int LRG::compute_degree( LRG &l ) const {
  int tmp;
  int num_regs = _num_regs;
  int nregs = l.num_regs();
  tmp =  (_fat_proj || l._fat_proj)     // either is a fat-proj?
    ? (num_regs * nregs)                // then use product
    : MAX2(num_regs,nregs);             // else use max
  return tmp;
}

//------------------------------effective_degree-------------------------------
// Compute effective degree for this live range.  If both live ranges are
// aligned-adjacent powers-of-2 then we use the MAX size.  If either is
// mis-aligned (or for Fat-Projections, not-adjacent) then we have to
// MULTIPLY the sizes.  Inspect Brigg's thesis on register pairs to see why
// this is so.
int PhaseIFG::effective_degree( uint lidx ) const {
  int eff = 0;
  int num_regs = lrgs(lidx).num_regs();
  int fat_proj = lrgs(lidx)._fat_proj;
  IndexSet *s = neighbors(lidx);
  IndexSetIterator elements(s);
  uint nidx;
  while((nidx = elements.next()) != 0) {
    LRG &lrgn = lrgs(nidx);
    int nregs = lrgn.num_regs();
    eff += (fat_proj || lrgn._fat_proj) // either is a fat-proj?
      ? (num_regs * nregs)              // then use product
      : MAX2(num_regs,nregs);           // else use max
  }
  return eff;
}


#ifndef PRODUCT
//------------------------------dump-------------------------------------------
void PhaseIFG::dump() const {
  tty->print_cr("-- Interference Graph --%s--",
                _is_square ? "square" : "triangular" );
  if( _is_square ) {
    for( uint i = 0; i < _maxlrg; i++ ) {
      tty->print( (*_yanked)[i] ? "XX " : "  ");
      tty->print("L%d: { ",i);
      IndexSetIterator elements(&_adjs[i]);
      uint datum;
      while ((datum = elements.next()) != 0) {
        tty->print("L%d ", datum);
      }
      tty->print_cr("}");

    }
    return;
  }

  // Triangular
  for( uint i = 0; i < _maxlrg; i++ ) {
    uint j;
    tty->print( (*_yanked)[i] ? "XX " : "  ");
    tty->print("L%d: { ",i);
    for( j = _maxlrg; j > i; j-- )
      if( test_edge(j - 1,i) ) {
        tty->print("L%d ",j - 1);
      }
    tty->print("| ");
    IndexSetIterator elements(&_adjs[i]);
    uint datum;
    while ((datum = elements.next()) != 0) {
      tty->print("L%d ", datum);
    }
    tty->print("}\n");
  }
  tty->print("\n");
}

//------------------------------stats------------------------------------------
void PhaseIFG::stats() const {
  ResourceMark rm;
  int *h_cnt = NEW_RESOURCE_ARRAY(int,_maxlrg*2);
  memset( h_cnt, 0, sizeof(int)*_maxlrg*2 );
  uint i;
  for( i = 0; i < _maxlrg; i++ ) {
    h_cnt[neighbor_cnt(i)]++;
  }
  tty->print_cr("--Histogram of counts--");
  for( i = 0; i < _maxlrg*2; i++ )
    if( h_cnt[i] )
      tty->print("%d/%d ",i,h_cnt[i]);
  tty->print_cr("");
}

//------------------------------verify-----------------------------------------
void PhaseIFG::verify( const PhaseChaitin *pc ) const {
  // IFG is square, sorted and no need for Find
  for( uint i = 0; i < _maxlrg; i++ ) {
    assert(!((*_yanked)[i]) || !neighbor_cnt(i), "Is removed completely" );
    IndexSet *set = &_adjs[i];
    IndexSetIterator elements(set);
    uint idx;
    uint last = 0;
    while ((idx = elements.next()) != 0) {
      assert( idx != i, "Must have empty diagonal");
      assert( pc->Find_const(idx) == idx, "Must not need Find" );
      assert( _adjs[idx].member(i), "IFG not square" );
      assert( !(*_yanked)[idx], "No yanked neighbors" );
      assert( last < idx, "not sorted increasing");
      last = idx;
    }
    assert( !lrgs(i)._degree_valid ||
            effective_degree(i) == lrgs(i).degree(), "degree is valid but wrong" );
  }
}
#endif

//------------------------------interfere_with_live----------------------------
// Interfere this register with everything currently live.  Use the RegMasks
// to trim the set of possible interferences. Return a count of register-only
// interferences as an estimate of register pressure.
void PhaseChaitin::interfere_with_live( uint r, IndexSet *liveout ) {
  uint retval = 0;
  // Interfere with everything live.
  const RegMask &rm = lrgs(r).mask();
  // Check for interference by checking overlap of regmasks.
  // Only interfere if acceptable register masks overlap.
  IndexSetIterator elements(liveout);
  uint l;
  while( (l = elements.next()) != 0 )
    if( rm.overlap( lrgs(l).mask() ) )
      _ifg->add_edge( r, l );
}

//------------------------------build_ifg_virtual------------------------------
// Actually build the interference graph.  Uses virtual registers only, no
// physical register masks.  This allows me to be very aggressive when
// coalescing copies.  Some of this aggressiveness will have to be undone
// later, but I'd rather get all the copies I can now (since unremoved copies
// at this point can end up in bad places).  Copies I re-insert later I have
// more opportunity to insert them in low-frequency locations.
void PhaseChaitin::build_ifg_virtual( ) {

  // For all blocks (in any order) do...
  for( uint i=0; i<_cfg._num_blocks; i++ ) {
    Block *b = _cfg._blocks[i];
    IndexSet *liveout = _live->live(b);

    // The IFG is built by a single reverse pass over each basic block.
    // Starting with the known live-out set, we remove things that get
    // defined and add things that become live (essentially executing one
    // pass of a standard LIVE analysis). Just before a Node defines a value
    // (and removes it from the live-ness set) that value is certainly live.
    // The defined value interferes with everything currently live.  The
    // value is then removed from the live-ness set and it's inputs are
    // added to the live-ness set.
    for( uint j = b->end_idx() + 1; j > 1; j-- ) {
      Node *n = b->_nodes[j-1];

      // Get value being defined
      uint r = n2lidx(n);

      // Some special values do not allocate
      if( r ) {

        // Remove from live-out set
        liveout->remove(r);

        // Copies do not define a new value and so do not interfere.
        // Remove the copies source from the liveout set before interfering.
        uint idx = n->is_Copy();
        if( idx ) liveout->remove( n2lidx(n->in(idx)) );

        // Interfere with everything live
        interfere_with_live( r, liveout );
      }

      // Make all inputs live
      if( !n->is_Phi() ) {      // Phi function uses come from prior block
        for( uint k = 1; k < n->req(); k++ )
          liveout->insert( n2lidx(n->in(k)) );
      }

      // 2-address instructions always have the defined value live
      // on entry to the instruction, even though it is being defined
      // by the instruction.  We pretend a virtual copy sits just prior
      // to the instruction and kills the src-def'd register.
      // In other words, for 2-address instructions the defined value
      // interferes with all inputs.
      uint idx;
      if( n->is_Mach() && (idx = n->as_Mach()->two_adr()) ) {
        const MachNode *mach = n->as_Mach();
        // Sometimes my 2-address ADDs are commuted in a bad way.
        // We generally want the USE-DEF register to refer to the
        // loop-varying quantity, to avoid a copy.
        uint op = mach->ideal_Opcode();
        // Check that mach->num_opnds() == 3 to ensure instruction is
        // not subsuming constants, effectively excludes addI_cin_imm
        // Can NOT swap for instructions like addI_cin_imm since it
        // is adding zero to yhi + carry and the second ideal-input
        // points to the result of adding low-halves.
        // Checking req() and num_opnds() does NOT distinguish addI_cout from addI_cout_imm
        if( (op == Op_AddI && mach->req() == 3 && mach->num_opnds() == 3) &&
            n->in(1)->bottom_type()->base() == Type::Int &&
            // See if the ADD is involved in a tight data loop the wrong way
            n->in(2)->is_Phi() &&
            n->in(2)->in(2) == n ) {
          Node *tmp = n->in(1);
          n->set_req( 1, n->in(2) );
          n->set_req( 2, tmp );
        }
        // Defined value interferes with all inputs
        uint lidx = n2lidx(n->in(idx));
        for( uint k = 1; k < n->req(); k++ ) {
          uint kidx = n2lidx(n->in(k));
          if( kidx != lidx )
            _ifg->add_edge( r, kidx );
        }
      }
    } // End of forall instructions in block
  } // End of forall blocks
}

//------------------------------count_int_pressure-----------------------------
uint PhaseChaitin::count_int_pressure( IndexSet *liveout ) {
  IndexSetIterator elements(liveout);
  uint lidx;
  uint cnt = 0;
  while ((lidx = elements.next()) != 0) {
    if( lrgs(lidx).mask().is_UP() &&
        lrgs(lidx).mask_size() &&
        !lrgs(lidx)._is_float &&
        lrgs(lidx).mask().overlap(*Matcher::idealreg2regmask[Op_RegI]) )
      cnt += lrgs(lidx).reg_pressure();
  }
  return cnt;
}

//------------------------------count_float_pressure---------------------------
uint PhaseChaitin::count_float_pressure( IndexSet *liveout ) {
  IndexSetIterator elements(liveout);
  uint lidx;
  uint cnt = 0;
  while ((lidx = elements.next()) != 0) {
    if( lrgs(lidx).mask().is_UP() &&
        lrgs(lidx).mask_size() &&
        lrgs(lidx)._is_float )
      cnt += lrgs(lidx).reg_pressure();
  }
  return cnt;
}

//------------------------------lower_pressure---------------------------------
// Adjust register pressure down by 1.  Capture last hi-to-low transition,
static void lower_pressure( LRG *lrg, uint where, Block *b, uint *pressure, uint *hrp_index ) {
  if( lrg->mask().is_UP() && lrg->mask_size() ) {
    if( lrg->_is_float ) {
      pressure[1] -= lrg->reg_pressure();
      if( pressure[1] == (uint)FLOATPRESSURE ) {
        hrp_index[1] = where;
#ifdef EXACT_PRESSURE
      if( pressure[1] > b->_freg_pressure )
        b->_freg_pressure = pressure[1]+1;
#else
        b->_freg_pressure = (uint)FLOATPRESSURE+1;
#endif
      }
    } else if( lrg->mask().overlap(*Matcher::idealreg2regmask[Op_RegI]) ) {
      pressure[0] -= lrg->reg_pressure();
      if( pressure[0] == (uint)INTPRESSURE   ) {
        hrp_index[0] = where;
#ifdef EXACT_PRESSURE
      if( pressure[0] > b->_reg_pressure )
        b->_reg_pressure = pressure[0]+1;
#else
        b->_reg_pressure = (uint)INTPRESSURE+1;
#endif
      }
    }
  }
}

//------------------------------build_ifg_physical-----------------------------
// Build the interference graph using physical registers when available.
// That is, if 2 live ranges are simultaneously alive but in their acceptable
// register sets do not overlap, then they do not interfere.
uint PhaseChaitin::build_ifg_physical( ResourceArea *a ) {
  NOT_PRODUCT( Compile::TracePhase t3("buildIFG", &_t_buildIFGphysical, TimeCompiler); )

  uint spill_reg = LRG::SPILL_REG;
  uint must_spill = 0;

  // For all blocks (in any order) do...
  for( uint i = 0; i < _cfg._num_blocks; i++ ) {
    Block *b = _cfg._blocks[i];
    // Clone (rather than smash in place) the liveout info, so it is alive
    // for the "collect_gc_info" phase later.
    IndexSet liveout(_live->live(b));
    uint last_inst = b->end_idx();
    // Compute first nonphi node index
    uint first_inst;
    for( first_inst = 1; first_inst < last_inst; first_inst++ )
      if( !b->_nodes[first_inst]->is_Phi() )
        break;

    // Spills could be inserted before CreateEx node which should be
    // first instruction in block after Phis. Move CreateEx up.
    for( uint insidx = first_inst; insidx < last_inst; insidx++ ) {
      Node *ex = b->_nodes[insidx];
      if( ex->is_SpillCopy() ) continue;
      if( insidx > first_inst && ex->is_Mach() &&
          ex->as_Mach()->ideal_Opcode() == Op_CreateEx ) {
        // If the CreateEx isn't above all the MachSpillCopies
        // then move it to the top.
        b->_nodes.remove(insidx);
        b->_nodes.insert(first_inst, ex);
      }
      // Stop once a CreateEx or any other node is found
      break;
    }

    // Reset block's register pressure values for each ifg construction
    uint pressure[2], hrp_index[2];
    pressure[0] = pressure[1] = 0;
    hrp_index[0] = hrp_index[1] = last_inst+1;
    b->_reg_pressure = b->_freg_pressure = 0;
    // Liveout things are presumed live for the whole block.  We accumulate
    // 'area' accordingly.  If they get killed in the block, we'll subtract
    // the unused part of the block from the area.
    int inst_count = last_inst - first_inst;
    double cost = (inst_count <= 0) ? 0.0 : b->_freq * double(inst_count);
    assert(!(cost < 0.0), "negative spill cost" );
    IndexSetIterator elements(&liveout);
    uint lidx;
    while ((lidx = elements.next()) != 0) {
      LRG &lrg = lrgs(lidx);
      lrg._area += cost;
      // Compute initial register pressure
      if( lrg.mask().is_UP() && lrg.mask_size() ) {
        if( lrg._is_float ) {   // Count float pressure
          pressure[1] += lrg.reg_pressure();
#ifdef EXACT_PRESSURE
          if( pressure[1] > b->_freg_pressure )
            b->_freg_pressure = pressure[1];
#endif
          // Count int pressure, but do not count the SP, flags
        } else if( lrgs(lidx).mask().overlap(*Matcher::idealreg2regmask[Op_RegI]) ) {
          pressure[0] += lrg.reg_pressure();
#ifdef EXACT_PRESSURE
          if( pressure[0] > b->_reg_pressure )
            b->_reg_pressure = pressure[0];
#endif
        }
      }
    }
    assert( pressure[0] == count_int_pressure  (&liveout), "" );
    assert( pressure[1] == count_float_pressure(&liveout), "" );

    // The IFG is built by a single reverse pass over each basic block.
    // Starting with the known live-out set, we remove things that get
    // defined and add things that become live (essentially executing one
    // pass of a standard LIVE analysis).  Just before a Node defines a value
    // (and removes it from the live-ness set) that value is certainly live.
    // The defined value interferes with everything currently live.  The
    // value is then removed from the live-ness set and it's inputs are added
    // to the live-ness set.
    uint j;
    for( j = last_inst + 1; j > 1; j-- ) {
      Node *n = b->_nodes[j - 1];

      // Get value being defined
      uint r = n2lidx(n);

      // Some special values do not allocate
      if( r ) {
        // A DEF normally costs block frequency; rematerialized values are
        // removed from the DEF sight, so LOWER costs here.
        lrgs(r)._cost += n->rematerialize() ? 0 : b->_freq;

        // If it is not live, then this instruction is dead.  Probably caused
        // by spilling and rematerialization.  Who cares why, yank this baby.
        if( !liveout.member(r) && n->Opcode() != Op_SafePoint ) {
          Node *def = n->in(0);
          if( !n->is_Proj() ||
              // Could also be a flags-projection of a dead ADD or such.
              (n2lidx(def) && !liveout.member(n2lidx(def)) ) ) {
            b->_nodes.remove(j - 1);
            if( lrgs(r)._def == n ) lrgs(r)._def = 0;
            n->disconnect_inputs(NULL);
            _cfg._bbs.map(n->_idx,NULL);
            n->replace_by(C->top());
            // Since yanking a Node from block, high pressure moves up one
            hrp_index[0]--;
            hrp_index[1]--;
            continue;
          }

          // Fat-projections kill many registers which cannot be used to
          // hold live ranges.
          if( lrgs(r)._fat_proj ) {
            // Count the int-only registers
            RegMask itmp = lrgs(r).mask();
            itmp.AND(*Matcher::idealreg2regmask[Op_RegI]);
            int iregs = itmp.Size();
#ifdef EXACT_PRESSURE
            if( pressure[0]+iregs > b->_reg_pressure )
              b->_reg_pressure = pressure[0]+iregs;
#endif
            if( pressure[0]       <= (uint)INTPRESSURE &&
                pressure[0]+iregs >  (uint)INTPRESSURE ) {
#ifndef EXACT_PRESSURE
              b->_reg_pressure = (uint)INTPRESSURE+1;
#endif
              hrp_index[0] = j-1;
            }
            // Count the float-only registers
            RegMask ftmp = lrgs(r).mask();
            ftmp.AND(*Matcher::idealreg2regmask[Op_RegD]);
            int fregs = ftmp.Size();
#ifdef EXACT_PRESSURE
            if( pressure[1]+fregs > b->_freg_pressure )
              b->_freg_pressure = pressure[1]+fregs;
#endif
            if( pressure[1]       <= (uint)FLOATPRESSURE &&
                pressure[1]+fregs >  (uint)FLOATPRESSURE ) {
#ifndef EXACT_PRESSURE
              b->_freg_pressure = (uint)FLOATPRESSURE+1;
#endif
              hrp_index[1] = j-1;
            }
          }

        } else {                // Else it is live
          // A DEF also ends 'area' partway through the block.
          lrgs(r)._area -= cost;
          assert(!(lrgs(r)._area < 0.0), "negative spill area" );

          // Insure high score for immediate-use spill copies so they get a color
          if( n->is_SpillCopy()
              && lrgs(r).is_singledef()        // MultiDef live range can still split
              && n->outcnt() == 1              // and use must be in this block
              && _cfg._bbs[n->unique_out()->_idx] == b ) {
            // All single-use MachSpillCopy(s) that immediately precede their
            // use must color early.  If a longer live range steals their
            // color, the spill copy will split and may push another spill copy
            // further away resulting in an infinite spill-split-retry cycle.
            // Assigning a zero area results in a high score() and a good
            // location in the simplify list.
            //

            Node *single_use = n->unique_out();
            assert( b->find_node(single_use) >= j, "Use must be later in block");
            // Use can be earlier in block if it is a Phi, but then I should be a MultiDef

            // Find first non SpillCopy 'm' that follows the current instruction
            // (j - 1) is index for current instruction 'n'
            Node *m = n;
            for( uint i = j; i <= last_inst && m->is_SpillCopy(); ++i ) { m = b->_nodes[i]; }
            if( m == single_use ) {
              lrgs(r)._area = 0.0;
            }
          }

          // Remove from live-out set
          if( liveout.remove(r) ) {
            // Adjust register pressure.
            // Capture last hi-to-lo pressure transition
            lower_pressure( &lrgs(r), j-1, b, pressure, hrp_index );
            assert( pressure[0] == count_int_pressure  (&liveout), "" );
            assert( pressure[1] == count_float_pressure(&liveout), "" );
          }

          // Copies do not define a new value and so do not interfere.
          // Remove the copies source from the liveout set before interfering.
          uint idx = n->is_Copy();
          if( idx ) {
            uint x = n2lidx(n->in(idx));
            if( liveout.remove( x ) ) {
              lrgs(x)._area -= cost;
              // Adjust register pressure.
              lower_pressure( &lrgs(x), j-1, b, pressure, hrp_index );
              assert( pressure[0] == count_int_pressure  (&liveout), "" );
              assert( pressure[1] == count_float_pressure(&liveout), "" );
            }
          }
        } // End of if live or not

        // Interfere with everything live.  If the defined value must
        // go in a particular register, just remove that register from
        // all conflicting parties and avoid the interference.

        // Make exclusions for rematerializable defs.  Since rematerializable
        // DEFs are not bound but the live range is, some uses must be bound.
        // If we spill live range 'r', it can rematerialize at each use site
        // according to its bindings.
        const RegMask &rmask = lrgs(r).mask();
        if( lrgs(r).is_bound() && !(n->rematerialize()) && rmask.is_NotEmpty() ) {
          // Smear odd bits; leave only aligned pairs of bits.
          RegMask r2mask = rmask;
          r2mask.SmearToPairs();
          // Check for common case
          int r_size = lrgs(r).num_regs();
          OptoReg::Name r_reg = (r_size == 1) ? rmask.find_first_elem() : OptoReg::Physical;

          IndexSetIterator elements(&liveout);
          uint l;
          while ((l = elements.next()) != 0) {
            LRG &lrg = lrgs(l);
            // If 'l' must spill already, do not further hack his bits.
            // He'll get some interferences and be forced to spill later.
            if( lrg._must_spill ) continue;
            // Remove bound register(s) from 'l's choices
            RegMask old = lrg.mask();
            uint old_size = lrg.mask_size();
            // Remove the bits from LRG 'r' from LRG 'l' so 'l' no
            // longer interferes with 'r'.  If 'l' requires aligned
            // adjacent pairs, subtract out bit pairs.
            if( lrg.num_regs() == 2 && !lrg._fat_proj ) {
              lrg.SUBTRACT( r2mask );
              lrg.compute_set_mask_size();
            } else if( r_size != 1 ) {
              lrg.SUBTRACT( rmask );
              lrg.compute_set_mask_size();
            } else {            // Common case: size 1 bound removal
              if( lrg.mask().Member(r_reg) ) {
                lrg.Remove(r_reg);
                lrg.set_mask_size(lrg.mask().is_AllStack() ? 65535:old_size-1);
              }
            }
            // If 'l' goes completely dry, it must spill.
            if( lrg.not_free() ) {
              // Give 'l' some kind of reasonable mask, so he picks up
              // interferences (and will spill later).
              lrg.set_mask( old );
              lrg.set_mask_size(old_size);
              must_spill++;
              lrg._must_spill = 1;
              lrg.set_reg(OptoReg::Name(LRG::SPILL_REG));
            }
          }
        } // End of if bound

        // Now interference with everything that is live and has
        // compatible register sets.
        interfere_with_live(r,&liveout);

      } // End of if normal register-allocated value

      // Area remaining in the block
      inst_count--;
      cost = (inst_count <= 0) ? 0.0 : b->_freq * double(inst_count);

      // Make all inputs live
      if( !n->is_Phi() ) {      // Phi function uses come from prior block
        JVMState* jvms = n->jvms();
        uint debug_start = jvms ? jvms->debug_start() : 999999;
        // Start loop at 1 (skip control edge) for most Nodes.
        // SCMemProj's might be the sole use of a StoreLConditional.
        // While StoreLConditionals set memory (the SCMemProj use)
        // they also def flags; if that flag def is unused the
        // allocator sees a flag-setting instruction with no use of
        // the flags and assumes it's dead.  This keeps the (useless)
        // flag-setting behavior alive while also keeping the (useful)
        // memory update effect.
        for( uint k = ((n->Opcode() == Op_SCMemProj) ? 0:1); k < n->req(); k++ ) {
          Node *def = n->in(k);
          uint x = n2lidx(def);
          if( !x ) continue;
          LRG &lrg = lrgs(x);
          // No use-side cost for spilling debug info
          if( k < debug_start )
            // A USE costs twice block frequency (once for the Load, once
            // for a Load-delay).  Rematerialized uses only cost once.
            lrg._cost += (def->rematerialize() ? b->_freq : (b->_freq + b->_freq));
          // It is live now
          if( liveout.insert( x ) ) {
            // Newly live things assumed live from here to top of block
            lrg._area += cost;
            // Adjust register pressure
            if( lrg.mask().is_UP() && lrg.mask_size() ) {
              if( lrg._is_float ) {
                pressure[1] += lrg.reg_pressure();
#ifdef EXACT_PRESSURE
                if( pressure[1] > b->_freg_pressure )
                  b->_freg_pressure = pressure[1];
#endif
              } else if( lrg.mask().overlap(*Matcher::idealreg2regmask[Op_RegI]) ) {
                pressure[0] += lrg.reg_pressure();
#ifdef EXACT_PRESSURE
                if( pressure[0] > b->_reg_pressure )
                  b->_reg_pressure = pressure[0];
#endif
              }
            }
            assert( pressure[0] == count_int_pressure  (&liveout), "" );
            assert( pressure[1] == count_float_pressure(&liveout), "" );
          }
          assert(!(lrg._area < 0.0), "negative spill area" );
        }
      }
    } // End of reverse pass over all instructions in block

    // If we run off the top of the block with high pressure and
    // never see a hi-to-low pressure transition, just record that
    // the whole block is high pressure.
    if( pressure[0] > (uint)INTPRESSURE   ) {
      hrp_index[0] = 0;
#ifdef EXACT_PRESSURE
      if( pressure[0] > b->_reg_pressure )
        b->_reg_pressure = pressure[0];
#else
      b->_reg_pressure = (uint)INTPRESSURE+1;
#endif
    }
    if( pressure[1] > (uint)FLOATPRESSURE ) {
      hrp_index[1] = 0;
#ifdef EXACT_PRESSURE
      if( pressure[1] > b->_freg_pressure )
        b->_freg_pressure = pressure[1];
#else
      b->_freg_pressure = (uint)FLOATPRESSURE+1;
#endif
    }

    // Compute high pressure indice; avoid landing in the middle of projnodes
    j = hrp_index[0];
    if( j < b->_nodes.size() && j < b->end_idx()+1 ) {
      Node *cur = b->_nodes[j];
      while( cur->is_Proj() || (cur->is_MachNullCheck()) || cur->is_Catch() ) {
        j--;
        cur = b->_nodes[j];
      }
    }
    b->_ihrp_index = j;
    j = hrp_index[1];
    if( j < b->_nodes.size() && j < b->end_idx()+1 ) {
      Node *cur = b->_nodes[j];
      while( cur->is_Proj() || (cur->is_MachNullCheck()) || cur->is_Catch() ) {
        j--;
        cur = b->_nodes[j];
      }
    }
    b->_fhrp_index = j;

#ifndef PRODUCT
    // Gather Register Pressure Statistics
    if( PrintOptoStatistics ) {
      if( b->_reg_pressure > (uint)INTPRESSURE || b->_freg_pressure > (uint)FLOATPRESSURE )
        _high_pressure++;
      else
        _low_pressure++;
    }
#endif
  } // End of for all blocks

  return must_spill;
}
