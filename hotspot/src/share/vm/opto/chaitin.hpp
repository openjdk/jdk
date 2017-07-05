/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

class LoopTree;
class MachCallNode;
class MachSafePointNode;
class Matcher;
class PhaseCFG;
class PhaseLive;
class PhaseRegAlloc;
class   PhaseChaitin;

#define OPTO_DEBUG_SPLIT_FREQ  BLOCK_FREQUENCY(0.001)
#define OPTO_LRG_HIGH_FREQ     BLOCK_FREQUENCY(0.25)

//------------------------------LRG--------------------------------------------
// Live-RanGe structure.
class LRG : public ResourceObj {
public:
  enum { SPILL_REG=29999 };     // Register number of a spilled LRG

  double _cost;                 // 2 for loads/1 for stores times block freq
  double _area;                 // Sum of all simultaneously live values
  double score() const;         // Compute score from cost and area
  double _maxfreq;              // Maximum frequency of any def or use

  Node *_def;                   // Check for multi-def live ranges
#ifndef PRODUCT
  GrowableArray<Node*>* _defs;
#endif

  uint _risk_bias;              // Index of LRG which we want to avoid color
  uint _copy_bias;              // Index of LRG which we want to share color

  uint _next;                   // Index of next LRG in linked list
  uint _prev;                   // Index of prev LRG in linked list
private:
  uint _reg;                    // Chosen register; undefined if mask is plural
public:
  // Return chosen register for this LRG.  Error if the LRG is not bound to
  // a single register.
  OptoReg::Name reg() const { return OptoReg::Name(_reg); }
  void set_reg( OptoReg::Name r ) { _reg = r; }

private:
  uint _eff_degree;             // Effective degree: Sum of neighbors _num_regs
public:
  int degree() const { assert( _degree_valid, "" ); return _eff_degree; }
  // Degree starts not valid and any change to the IFG neighbor
  // set makes it not valid.
  void set_degree( uint degree ) { _eff_degree = degree; debug_only(_degree_valid = 1;) }
  // Made a change that hammered degree
  void invalid_degree() { debug_only(_degree_valid=0;) }
  // Incrementally modify degree.  If it was correct, it should remain correct
  void inc_degree( uint mod ) { _eff_degree += mod; }
  // Compute the degree between 2 live ranges
  int compute_degree( LRG &l ) const;

private:
  RegMask _mask;                // Allowed registers for this LRG
  uint _mask_size;              // cache of _mask.Size();
public:
  int compute_mask_size() const { return _mask.is_AllStack() ? 65535 : _mask.Size(); }
  void set_mask_size( int size ) {
    assert((size == 65535) || (size == (int)_mask.Size()), "");
    _mask_size = size;
    debug_only(_msize_valid=1;)
    debug_only( if( _num_regs == 2 && !_fat_proj ) _mask.VerifyPairs(); )
  }
  void compute_set_mask_size() { set_mask_size(compute_mask_size()); }
  int mask_size() const { assert( _msize_valid, "mask size not valid" );
                          return _mask_size; }
  // Get the last mask size computed, even if it does not match the
  // count of bits in the current mask.
  int get_invalid_mask_size() const { return _mask_size; }
  const RegMask &mask() const { return _mask; }
  void set_mask( const RegMask &rm ) { _mask = rm; debug_only(_msize_valid=0;)}
  void AND( const RegMask &rm ) { _mask.AND(rm); debug_only(_msize_valid=0;)}
  void SUBTRACT( const RegMask &rm ) { _mask.SUBTRACT(rm); debug_only(_msize_valid=0;)}
  void Clear()   { _mask.Clear()  ; debug_only(_msize_valid=1); _mask_size = 0; }
  void Set_All() { _mask.Set_All(); debug_only(_msize_valid=1); _mask_size = RegMask::CHUNK_SIZE; }
  void Insert( OptoReg::Name reg ) { _mask.Insert(reg);  debug_only(_msize_valid=0;) }
  void Remove( OptoReg::Name reg ) { _mask.Remove(reg);  debug_only(_msize_valid=0;) }
  void ClearToPairs() { _mask.ClearToPairs(); debug_only(_msize_valid=0;) }

  // Number of registers this live range uses when it colors
private:
  uint8 _num_regs;              // 2 for Longs and Doubles, 1 for all else
                                // except _num_regs is kill count for fat_proj
public:
  int num_regs() const { return _num_regs; }
  void set_num_regs( int reg ) { assert( _num_regs == reg || !_num_regs, "" ); _num_regs = reg; }

private:
  // Number of physical registers this live range uses when it colors
  // Architecture and register-set dependent
  uint8 _reg_pressure;
public:
  void set_reg_pressure(int i)  { _reg_pressure = i; }
  int      reg_pressure() const { return _reg_pressure; }

  // How much 'wiggle room' does this live range have?
  // How many color choices can it make (scaled by _num_regs)?
  int degrees_of_freedom() const { return mask_size() - _num_regs; }
  // Bound LRGs have ZERO degrees of freedom.  We also count
  // must_spill as bound.
  bool is_bound  () const { return _is_bound; }
  // Negative degrees-of-freedom; even with no neighbors this
  // live range must spill.
  bool not_free() const { return degrees_of_freedom() <  0; }
  // Is this live range of "low-degree"?  Trivially colorable?
  bool lo_degree () const { return degree() <= degrees_of_freedom(); }
  // Is this live range just barely "low-degree"?  Trivially colorable?
  bool just_lo_degree () const { return degree() == degrees_of_freedom(); }

  uint   _is_oop:1,             // Live-range holds an oop
         _is_float:1,           // True if in float registers
         _was_spilled1:1,       // True if prior spilling on def
         _was_spilled2:1,       // True if twice prior spilling on def
         _is_bound:1,           // live range starts life with no
                                // degrees of freedom.
         _direct_conflict:1,    // True if def and use registers in conflict
         _must_spill:1,         // live range has lost all degrees of freedom
    // If _fat_proj is set, live range does NOT require aligned, adjacent
    // registers and has NO interferences.
    // If _fat_proj is clear, live range requires num_regs() to be a power of
    // 2, and it requires registers to form an aligned, adjacent set.
         _fat_proj:1,           //
         _was_lo:1,             // Was lo-degree prior to coalesce
         _msize_valid:1,        // _mask_size cache valid
         _degree_valid:1,       // _degree cache valid
         _has_copy:1,           // Adjacent to some copy instruction
         _at_risk:1;            // Simplify says this guy is at risk to spill


  // Alive if non-zero, dead if zero
  bool alive() const { return _def != NULL; }
  bool is_multidef() const { return _def == NodeSentinel; }
  bool is_singledef() const { return _def != NodeSentinel; }

#ifndef PRODUCT
  void dump( ) const;
#endif
};

//------------------------------LRG_List---------------------------------------
// Map Node indices to Live RanGe indices.
// Array lookup in the optimized case.
class LRG_List : public ResourceObj {
  uint _cnt, _max;
  uint* _lidxs;
  ReallocMark _nesting;         // assertion check for reallocations
public:
  LRG_List( uint max );

  uint lookup( uint nidx ) const {
    return _lidxs[nidx];
  }
  uint operator[] (uint nidx) const { return lookup(nidx); }

  void map( uint nidx, uint lidx ) {
    assert( nidx < _cnt, "oob" );
    _lidxs[nidx] = lidx;
  }
  void extend( uint nidx, uint lidx );

  uint Size() const { return _cnt; }
};

//------------------------------IFG--------------------------------------------
//                         InterFerence Graph
// An undirected graph implementation.  Created with a fixed number of
// vertices.  Edges can be added & tested.  Vertices can be removed, then
// added back later with all edges intact.  Can add edges between one vertex
// and a list of other vertices.  Can union vertices (and their edges)
// together.  The IFG needs to be really really fast, and also fairly
// abstract!  It needs abstraction so I can fiddle with the implementation to
// get even more speed.
class PhaseIFG : public Phase {
  // Current implementation: a triangular adjacency list.

  // Array of adjacency-lists, indexed by live-range number
  IndexSet *_adjs;

  // Assertion bit for proper use of Squaring
  bool _is_square;

  // Live range structure goes here
  LRG *_lrgs;                   // Array of LRG structures

public:
  // Largest live-range number
  uint _maxlrg;

  Arena *_arena;

  // Keep track of inserted and deleted Nodes
  VectorSet *_yanked;

  PhaseIFG( Arena *arena );
  void init( uint maxlrg );

  // Add edge between a and b.  Returns true if actually addded.
  int add_edge( uint a, uint b );

  // Add edge between a and everything in the vector
  void add_vector( uint a, IndexSet *vec );

  // Test for edge existance
  int test_edge( uint a, uint b ) const;

  // Square-up matrix for faster Union
  void SquareUp();

  // Return number of LRG neighbors
  uint neighbor_cnt( uint a ) const { return _adjs[a].count(); }
  // Union edges of b into a on Squared-up matrix
  void Union( uint a, uint b );
  // Test for edge in Squared-up matrix
  int test_edge_sq( uint a, uint b ) const;
  // Yank a Node and all connected edges from the IFG.  Be prepared to
  // re-insert the yanked Node in reverse order of yanking.  Return a
  // list of neighbors (edges) yanked.
  IndexSet *remove_node( uint a );
  // Reinsert a yanked Node
  void re_insert( uint a );
  // Return set of neighbors
  IndexSet *neighbors( uint a ) const { return &_adjs[a]; }

#ifndef PRODUCT
  // Dump the IFG
  void dump() const;
  void stats() const;
  void verify( const PhaseChaitin * ) const;
#endif

  //--------------- Live Range Accessors
  LRG &lrgs(uint idx) const { assert(idx < _maxlrg, "oob"); return _lrgs[idx]; }

  // Compute and set effective degree.  Might be folded into SquareUp().
  void Compute_Effective_Degree();

  // Compute effective degree as the sum of neighbors' _sizes.
  int effective_degree( uint lidx ) const;
};

// TEMPORARILY REPLACED WITH COMMAND LINE FLAG

//// !!!!! Magic Constants need to move into ad file
#ifdef SPARC
//#define FLOAT_PRESSURE 30  /*     SFLT_REG_mask.Size() - 1 */
//#define INT_PRESSURE   23  /* NOTEMP_I_REG_mask.Size() - 1 */
#define FLOAT_INCREMENT(regs) regs
#else
//#define FLOAT_PRESSURE 6
//#define INT_PRESSURE   6
#define FLOAT_INCREMENT(regs) 1
#endif

//------------------------------Chaitin----------------------------------------
// Briggs-Chaitin style allocation, mostly.
class PhaseChaitin : public PhaseRegAlloc {

  int _trip_cnt;
  int _alternate;

  uint _maxlrg;                 // Max live range number
  LRG &lrgs(uint idx) const { return _ifg->lrgs(idx); }
  PhaseLive *_live;             // Liveness, used in the interference graph
  PhaseIFG *_ifg;               // Interference graph (for original chunk)
  Node_List **_lrg_nodes;       // Array of node; lists for lrgs which spill
  VectorSet _spilled_once;      // Nodes that have been spilled
  VectorSet _spilled_twice;     // Nodes that have been spilled twice

  LRG_List _names;              // Map from Nodes to Live RanGes

  // Union-find map.  Declared as a short for speed.
  // Indexed by live-range number, it returns the compacted live-range number
  LRG_List _uf_map;
  // Reset the Union-Find map to identity
  void reset_uf_map( uint maxlrg );
  // Remove the need for the Union-Find mapping
  void compress_uf_map_for_nodes( );

  // Combine the Live Range Indices for these 2 Nodes into a single live
  // range.  Future requests for any Node in either live range will
  // return the live range index for the combined live range.
  void Union( const Node *src, const Node *dst );

  void new_lrg( const Node *x, uint lrg );

  // Compact live ranges, removing unused ones.  Return new maxlrg.
  void compact();

  uint _lo_degree;              // Head of lo-degree LRGs list
  uint _lo_stk_degree;          // Head of lo-stk-degree LRGs list
  uint _hi_degree;              // Head of hi-degree LRGs list
  uint _simplified;             // Linked list head of simplified LRGs

  // Helper functions for Split()
  uint split_DEF( Node *def, Block *b, int loc, uint max, Node **Reachblock, Node **debug_defs, GrowableArray<uint> splits, int slidx );
  uint split_USE( Node *def, Block *b, Node *use, uint useidx, uint max, bool def_down, bool cisc_sp, GrowableArray<uint> splits, int slidx );
  int clone_projs( Block *b, uint idx, Node *con, Node *copy, uint &maxlrg );
  Node *split_Rematerialize(Node *def, Block *b, uint insidx, uint &maxlrg, GrowableArray<uint> splits,
                            int slidx, uint *lrg2reach, Node **Reachblock, bool walkThru);
  // True if lidx is used before any real register is def'd in the block
  bool prompt_use( Block *b, uint lidx );
  Node *get_spillcopy_wide( Node *def, Node *use, uint uidx );
  // Insert the spill at chosen location.  Skip over any intervening Proj's or
  // Phis.  Skip over a CatchNode and projs, inserting in the fall-through block
  // instead.  Update high-pressure indices.  Create a new live range.
  void insert_proj( Block *b, uint i, Node *spill, uint maxlrg );

  bool is_high_pressure( Block *b, LRG *lrg, uint insidx );

  uint _oldphi;                 // Node index which separates pre-allocation nodes

  Block **_blks;                // Array of blocks sorted by frequency for coalescing

  float _high_frequency_lrg;    // Frequency at which LRG will be spilled for debug info

#ifndef PRODUCT
  bool _trace_spilling;
#endif

public:
  PhaseChaitin( uint unique, PhaseCFG &cfg, Matcher &matcher );
  ~PhaseChaitin() {}

  // Convert a Node into a Live Range Index - a lidx
  uint Find( const Node *n ) {
    uint lidx = n2lidx(n);
    uint uf_lidx = _uf_map[lidx];
    return (uf_lidx == lidx) ? uf_lidx : Find_compress(n);
  }
  uint Find_const( uint lrg ) const;
  uint Find_const( const Node *n ) const;

  // Do all the real work of allocate
  void Register_Allocate();

  uint n2lidx( const Node *n ) const { return _names[n->_idx]; }

  float high_frequency_lrg() const { return _high_frequency_lrg; }

#ifndef PRODUCT
  bool trace_spilling() const { return _trace_spilling; }
#endif

private:
  // De-SSA the world.  Assign registers to Nodes.  Use the same register for
  // all inputs to a PhiNode, effectively coalescing live ranges.  Insert
  // copies as needed.
  void de_ssa();
  uint Find_compress( const Node *n );
  uint Find( uint lidx ) {
    uint uf_lidx = _uf_map[lidx];
    return (uf_lidx == lidx) ? uf_lidx : Find_compress(lidx);
  }
  uint Find_compress( uint lidx );

  uint Find_id( const Node *n ) {
    uint retval = n2lidx(n);
    assert(retval == Find(n),"Invalid node to lidx mapping");
    return retval;
  }

  // Add edge between reg and everything in the vector.
  // Same as _ifg->add_vector(reg,live) EXCEPT use the RegMask
  // information to trim the set of interferences.  Return the
  // count of edges added.
  void interfere_with_live( uint reg, IndexSet *live );
  // Count register pressure for asserts
  uint count_int_pressure( IndexSet *liveout );
  uint count_float_pressure( IndexSet *liveout );

  // Build the interference graph using virtual registers only.
  // Used for aggressive coalescing.
  void build_ifg_virtual( );

  // Build the interference graph using physical registers when available.
  // That is, if 2 live ranges are simultaneously alive but in their
  // acceptable register sets do not overlap, then they do not interfere.
  uint build_ifg_physical( ResourceArea *a );

  // Gather LiveRanGe information, including register masks and base pointer/
  // derived pointer relationships.
  void gather_lrg_masks( bool mod_cisc_masks );

  // Force the bases of derived pointers to be alive at GC points.
  bool stretch_base_pointer_live_ranges( ResourceArea *a );
  // Helper to stretch above; recursively discover the base Node for
  // a given derived Node.  Easy for AddP-related machine nodes, but
  // needs to be recursive for derived Phis.
  Node *find_base_for_derived( Node **derived_base_map, Node *derived, uint &maxlrg );

  // Set the was-lo-degree bit.  Conservative coalescing should not change the
  // colorability of the graph.  If any live range was of low-degree before
  // coalescing, it should Simplify.  This call sets the was-lo-degree bit.
  void set_was_low();

  // Split live-ranges that must spill due to register conflicts (as opposed
  // to capacity spills).  Typically these are things def'd in a register
  // and used on the stack or vice-versa.
  void pre_spill();

  // Init LRG caching of degree, numregs.  Init lo_degree list.
  void cache_lrg_info( );

  // Simplify the IFG by removing LRGs of low degree with no copies
  void Pre_Simplify();

  // Simplify the IFG by removing LRGs of low degree
  void Simplify();

  // Select colors by re-inserting edges into the IFG.
  // Return TRUE if any spills occurred.
  uint Select( );
  // Helper function for select which allows biased coloring
  OptoReg::Name choose_color( LRG &lrg, int chunk );
  // Helper function which implements biasing heuristic
  OptoReg::Name bias_color( LRG &lrg, int chunk );

  // Split uncolorable live ranges
  // Return new number of live ranges
  uint Split( uint maxlrg );

  // Copy 'was_spilled'-edness from one Node to another.
  void copy_was_spilled( Node *src, Node *dst );
  // Set the 'spilled_once' or 'spilled_twice' flag on a node.
  void set_was_spilled( Node *n );

  // Convert ideal spill-nodes into machine loads & stores
  // Set C->failing when fixup spills could not complete, node limit exceeded.
  void fixup_spills();

  // Post-Allocation peephole copy removal
  void post_allocate_copy_removal();
  Node *skip_copies( Node *c );
  // Replace the old node with the current live version of that value
  // and yank the old value if it's dead.
  int replace_and_yank_if_dead( Node *old, OptoReg::Name nreg,
                                Block *current_block, Node_List& value, Node_List& regnd ) {
    Node* v = regnd[nreg];
    assert(v->outcnt() != 0, "no dead values");
    old->replace_by(v);
    return yank_if_dead(old, current_block, &value, &regnd);
  }

  int yank_if_dead( Node *old, Block *current_block, Node_List *value, Node_List *regnd );
  int elide_copy( Node *n, int k, Block *current_block, Node_List &value, Node_List &regnd, bool can_change_regs );
  int use_prior_register( Node *copy, uint idx, Node *def, Block *current_block, Node_List &value, Node_List &regnd );
  bool may_be_copy_of_callee( Node *def ) const;

  // If nreg already contains the same constant as val then eliminate it
  bool eliminate_copy_of_constant(Node* val, Node* n,
                                  Block *current_block, Node_List& value, Node_List &regnd,
                                  OptoReg::Name nreg, OptoReg::Name nreg2);
  // Extend the node to LRG mapping
  void add_reference( const Node *node, const Node *old_node);

private:

  static int _final_loads, _final_stores, _final_copies, _final_memoves;
  static double _final_load_cost, _final_store_cost, _final_copy_cost, _final_memove_cost;
  static int _conserv_coalesce, _conserv_coalesce_pair;
  static int _conserv_coalesce_trie, _conserv_coalesce_quad;
  static int _post_alloc;
  static int _lost_opp_pp_coalesce, _lost_opp_cflow_coalesce;
  static int _used_cisc_instructions, _unused_cisc_instructions;
  static int _allocator_attempts, _allocator_successes;

#ifndef PRODUCT
  static uint _high_pressure, _low_pressure;

  void dump() const;
  void dump( const Node *n ) const;
  void dump( const Block * b ) const;
  void dump_degree_lists() const;
  void dump_simplified() const;
  void dump_lrg( uint lidx ) const;
  void dump_bb( uint pre_order ) const;

  // Verify that base pointers and derived pointers are still sane
  void verify_base_ptrs( ResourceArea *a ) const;

  void verify( ResourceArea *a, bool verify_ifg = false ) const;

  void dump_for_spill_split_recycle() const;

public:
  void dump_frame() const;
  char *dump_register( const Node *n, char *buf  ) const;
private:
  static void print_chaitin_statistics();
#endif
  friend class PhaseCoalesce;
  friend class PhaseAggressiveCoalesce;
  friend class PhaseConservativeCoalesce;
};
