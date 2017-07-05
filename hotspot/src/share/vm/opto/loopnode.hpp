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

class CmpNode;
class CountedLoopEndNode;
class CountedLoopNode;
class IdealLoopTree;
class LoopNode;
class Node;
class PhaseIdealLoop;
class VectorSet;
struct small_cache;

//
//                  I D E A L I Z E D   L O O P S
//
// Idealized loops are the set of loops I perform more interesting
// transformations on, beyond simple hoisting.

//------------------------------LoopNode---------------------------------------
// Simple loop header.  Fall in path on left, loop-back path on right.
class LoopNode : public RegionNode {
  // Size is bigger to hold the flags.  However, the flags do not change
  // the semantics so it does not appear in the hash & cmp functions.
  virtual uint size_of() const { return sizeof(*this); }
protected:
  short _loop_flags;
  // Names for flag bitfields
  enum { pre_post_main=0, inner_loop=8, partial_peel_loop=16, partial_peel_failed=32  };
  char _unswitch_count;
  enum { _unswitch_max=3 };

public:
  // Names for edge indices
  enum { Self=0, EntryControl, LoopBackControl };

  int is_inner_loop() const { return _loop_flags & inner_loop; }
  void set_inner_loop() { _loop_flags |= inner_loop; }

  int is_partial_peel_loop() const { return _loop_flags & partial_peel_loop; }
  void set_partial_peel_loop() { _loop_flags |= partial_peel_loop; }
  int partial_peel_has_failed() const { return _loop_flags & partial_peel_failed; }
  void mark_partial_peel_failed() { _loop_flags |= partial_peel_failed; }

  int unswitch_max() { return _unswitch_max; }
  int unswitch_count() { return _unswitch_count; }
  void set_unswitch_count(int val) {
    assert (val <= unswitch_max(), "too many unswitches");
    _unswitch_count = val;
  }

  LoopNode( Node *entry, Node *backedge ) : RegionNode(3), _loop_flags(0), _unswitch_count(0) {
    init_class_id(Class_Loop);
    init_req(EntryControl, entry);
    init_req(LoopBackControl, backedge);
  }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual int Opcode() const;
  bool can_be_counted_loop(PhaseTransform* phase) const {
    return req() == 3 && in(0) != NULL &&
      in(1) != NULL && phase->type(in(1)) != Type::TOP &&
      in(2) != NULL && phase->type(in(2)) != Type::TOP;
  }
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif
};

//------------------------------Counted Loops----------------------------------
// Counted loops are all trip-counted loops, with exactly 1 trip-counter exit
// path (and maybe some other exit paths).  The trip-counter exit is always
// last in the loop.  The trip-counter does not have to stride by a constant,
// but it does have to stride by a loop-invariant amount; the exit value is
// also loop invariant.

// CountedLoopNodes and CountedLoopEndNodes come in matched pairs.  The
// CountedLoopNode has the incoming loop control and the loop-back-control
// which is always the IfTrue before the matching CountedLoopEndNode.  The
// CountedLoopEndNode has an incoming control (possibly not the
// CountedLoopNode if there is control flow in the loop), the post-increment
// trip-counter value, and the limit.  The trip-counter value is always of
// the form (Op old-trip-counter stride).  The old-trip-counter is produced
// by a Phi connected to the CountedLoopNode.  The stride is loop invariant.
// The Op is any commutable opcode, including Add, Mul, Xor.  The
// CountedLoopEndNode also takes in the loop-invariant limit value.

// From a CountedLoopNode I can reach the matching CountedLoopEndNode via the
// loop-back control.  From CountedLoopEndNodes I can reach CountedLoopNodes
// via the old-trip-counter from the Op node.

//------------------------------CountedLoopNode--------------------------------
// CountedLoopNodes head simple counted loops.  CountedLoopNodes have as
// inputs the incoming loop-start control and the loop-back control, so they
// act like RegionNodes.  They also take in the initial trip counter, the
// loop-invariant stride and the loop-invariant limit value.  CountedLoopNodes
// produce a loop-body control and the trip counter value.  Since
// CountedLoopNodes behave like RegionNodes I still have a standard CFG model.

class CountedLoopNode : public LoopNode {
  // Size is bigger to hold _main_idx.  However, _main_idx does not change
  // the semantics so it does not appear in the hash & cmp functions.
  virtual uint size_of() const { return sizeof(*this); }

  // For Pre- and Post-loops during debugging ONLY, this holds the index of
  // the Main CountedLoop.  Used to assert that we understand the graph shape.
  node_idx_t _main_idx;

  // Known trip count calculated by policy_maximally_unroll
  int   _trip_count;

  // Expected trip count from profile data
  float _profile_trip_cnt;

  // Log2 of original loop bodies in unrolled loop
  int _unrolled_count_log2;

  // Node count prior to last unrolling - used to decide if
  // unroll,optimize,unroll,optimize,... is making progress
  int _node_count_before_unroll;

public:
  CountedLoopNode( Node *entry, Node *backedge )
    : LoopNode(entry, backedge), _trip_count(max_jint),
      _profile_trip_cnt(COUNT_UNKNOWN), _unrolled_count_log2(0),
      _node_count_before_unroll(0) {
    init_class_id(Class_CountedLoop);
    // Initialize _trip_count to the largest possible value.
    // Will be reset (lower) if the loop's trip count is known.
  }

  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);

  Node *init_control() const { return in(EntryControl); }
  Node *back_control() const { return in(LoopBackControl); }
  CountedLoopEndNode *loopexit() const;
  Node *init_trip() const;
  Node *stride() const;
  int   stride_con() const;
  bool  stride_is_con() const;
  Node *limit() const;
  Node *incr() const;
  Node *phi() const;

  // Match increment with optional truncation
  static Node* match_incr_with_optional_truncation(Node* expr, Node** trunc1, Node** trunc2, const TypeInt** trunc_type);

  // A 'main' loop has a pre-loop and a post-loop.  The 'main' loop
  // can run short a few iterations and may start a few iterations in.
  // It will be RCE'd and unrolled and aligned.

  // A following 'post' loop will run any remaining iterations.  Used
  // during Range Check Elimination, the 'post' loop will do any final
  // iterations with full checks.  Also used by Loop Unrolling, where
  // the 'post' loop will do any epilog iterations needed.  Basically,
  // a 'post' loop can not profitably be further unrolled or RCE'd.

  // A preceding 'pre' loop will run at least 1 iteration (to do peeling),
  // it may do under-flow checks for RCE and may do alignment iterations
  // so the following main loop 'knows' that it is striding down cache
  // lines.

  // A 'main' loop that is ONLY unrolled or peeled, never RCE'd or
  // Aligned, may be missing it's pre-loop.
  enum { Normal=0, Pre=1, Main=2, Post=3, PrePostFlagsMask=3, Main_Has_No_Pre_Loop=4 };
  int is_normal_loop() const { return (_loop_flags&PrePostFlagsMask) == Normal; }
  int is_pre_loop   () const { return (_loop_flags&PrePostFlagsMask) == Pre;    }
  int is_main_loop  () const { return (_loop_flags&PrePostFlagsMask) == Main;   }
  int is_post_loop  () const { return (_loop_flags&PrePostFlagsMask) == Post;   }
  int is_main_no_pre_loop() const { return _loop_flags & Main_Has_No_Pre_Loop; }
  void set_main_no_pre_loop() { _loop_flags |= Main_Has_No_Pre_Loop; }

  int main_idx() const { return _main_idx; }


  void set_pre_loop  (CountedLoopNode *main) { assert(is_normal_loop(),""); _loop_flags |= Pre ; _main_idx = main->_idx; }
  void set_main_loop (                     ) { assert(is_normal_loop(),""); _loop_flags |= Main;                         }
  void set_post_loop (CountedLoopNode *main) { assert(is_normal_loop(),""); _loop_flags |= Post; _main_idx = main->_idx; }
  void set_normal_loop(                    ) { _loop_flags &= ~PrePostFlagsMask; }

  void set_trip_count(int tc) { _trip_count = tc; }
  int trip_count()            { return _trip_count; }

  void set_profile_trip_cnt(float ptc) { _profile_trip_cnt = ptc; }
  float profile_trip_cnt()             { return _profile_trip_cnt; }

  void double_unrolled_count() { _unrolled_count_log2++; }
  int  unrolled_count()        { return 1 << MIN2(_unrolled_count_log2, BitsPerInt-3); }

  void set_node_count_before_unroll(int ct) { _node_count_before_unroll = ct; }
  int  node_count_before_unroll()           { return _node_count_before_unroll; }

#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif
};

//------------------------------CountedLoopEndNode-----------------------------
// CountedLoopEndNodes end simple trip counted loops.  They act much like
// IfNodes.
class CountedLoopEndNode : public IfNode {
public:
  enum { TestControl, TestValue };

  CountedLoopEndNode( Node *control, Node *test, float prob, float cnt )
    : IfNode( control, test, prob, cnt) {
    init_class_id(Class_CountedLoopEnd);
  }
  virtual int Opcode() const;

  Node *cmp_node() const            { return (in(TestValue)->req() >=2) ? in(TestValue)->in(1) : NULL; }
  Node *incr() const                { Node *tmp = cmp_node(); return (tmp && tmp->req()==3) ? tmp->in(1) : NULL; }
  Node *limit() const               { Node *tmp = cmp_node(); return (tmp && tmp->req()==3) ? tmp->in(2) : NULL; }
  Node *stride() const              { Node *tmp = incr    (); return (tmp && tmp->req()==3) ? tmp->in(2) : NULL; }
  Node *phi() const                 { Node *tmp = incr    (); return (tmp && tmp->req()==3) ? tmp->in(1) : NULL; }
  Node *init_trip() const           { Node *tmp = phi     (); return (tmp && tmp->req()==3) ? tmp->in(1) : NULL; }
  int stride_con() const;
  bool stride_is_con() const        { Node *tmp = stride  (); return (tmp != NULL && tmp->is_Con()); }
  BoolTest::mask test_trip() const  { return in(TestValue)->as_Bool()->_test._test; }
  CountedLoopNode *loopnode() const {
    Node *ln = phi()->in(0);
    assert( ln->Opcode() == Op_CountedLoop, "malformed loop" );
    return (CountedLoopNode*)ln; }

#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif
};


inline CountedLoopEndNode *CountedLoopNode::loopexit() const {
  Node *bc = back_control();
  if( bc == NULL ) return NULL;
  Node *le = bc->in(0);
  if( le->Opcode() != Op_CountedLoopEnd )
    return NULL;
  return (CountedLoopEndNode*)le;
}
inline Node *CountedLoopNode::init_trip() const { return loopexit() ? loopexit()->init_trip() : NULL; }
inline Node *CountedLoopNode::stride() const { return loopexit() ? loopexit()->stride() : NULL; }
inline int CountedLoopNode::stride_con() const { return loopexit() ? loopexit()->stride_con() : 0; }
inline bool CountedLoopNode::stride_is_con() const { return loopexit() && loopexit()->stride_is_con(); }
inline Node *CountedLoopNode::limit() const { return loopexit() ? loopexit()->limit() : NULL; }
inline Node *CountedLoopNode::incr() const { return loopexit() ? loopexit()->incr() : NULL; }
inline Node *CountedLoopNode::phi() const { return loopexit() ? loopexit()->phi() : NULL; }


// -----------------------------IdealLoopTree----------------------------------
class IdealLoopTree : public ResourceObj {
public:
  IdealLoopTree *_parent;       // Parent in loop tree
  IdealLoopTree *_next;         // Next sibling in loop tree
  IdealLoopTree *_child;        // First child in loop tree

  // The head-tail backedge defines the loop.
  // If tail is NULL then this loop has multiple backedges as part of the
  // same loop.  During cleanup I'll peel off the multiple backedges; merge
  // them at the loop bottom and flow 1 real backedge into the loop.
  Node *_head;                  // Head of loop
  Node *_tail;                  // Tail of loop
  inline Node *tail();          // Handle lazy update of _tail field
  PhaseIdealLoop* _phase;

  Node_List _body;              // Loop body for inner loops

  uint8 _nest;                  // Nesting depth
  uint8 _irreducible:1,         // True if irreducible
        _has_call:1,            // True if has call safepoint
        _has_sfpt:1,            // True if has non-call safepoint
        _rce_candidate:1;       // True if candidate for range check elimination

  Node_List* _required_safept;  // A inner loop cannot delete these safepts;
  bool  _allow_optimizations;   // Allow loop optimizations

  IdealLoopTree( PhaseIdealLoop* phase, Node *head, Node *tail )
    : _parent(0), _next(0), _child(0),
      _head(head), _tail(tail),
      _phase(phase),
      _required_safept(NULL),
      _allow_optimizations(true),
      _nest(0), _irreducible(0), _has_call(0), _has_sfpt(0), _rce_candidate(0)
  { }

  // Is 'l' a member of 'this'?
  int is_member( const IdealLoopTree *l ) const; // Test for nested membership

  // Set loop nesting depth.  Accumulate has_call bits.
  int set_nest( uint depth );

  // Split out multiple fall-in edges from the loop header.  Move them to a
  // private RegionNode before the loop.  This becomes the loop landing pad.
  void split_fall_in( PhaseIdealLoop *phase, int fall_in_cnt );

  // Split out the outermost loop from this shared header.
  void split_outer_loop( PhaseIdealLoop *phase );

  // Merge all the backedges from the shared header into a private Region.
  // Feed that region as the one backedge to this loop.
  void merge_many_backedges( PhaseIdealLoop *phase );

  // Split shared headers and insert loop landing pads.
  // Insert a LoopNode to replace the RegionNode.
  // Returns TRUE if loop tree is structurally changed.
  bool beautify_loops( PhaseIdealLoop *phase );

  // Perform iteration-splitting on inner loops.  Split iterations to
  // avoid range checks or one-shot null checks.  Returns false if the
  // current round of loop opts should stop.
  bool iteration_split( PhaseIdealLoop *phase, Node_List &old_new );

  // Driver for various flavors of iteration splitting.  Returns false
  // if the current round of loop opts should stop.
  bool iteration_split_impl( PhaseIdealLoop *phase, Node_List &old_new );

  // Given dominators, try to find loops with calls that must always be
  // executed (call dominates loop tail).  These loops do not need non-call
  // safepoints (ncsfpt).
  void check_safepts(VectorSet &visited, Node_List &stack);

  // Allpaths backwards scan from loop tail, terminating each path at first safepoint
  // encountered.
  void allpaths_check_safepts(VectorSet &visited, Node_List &stack);

  // Convert to counted loops where possible
  void counted_loop( PhaseIdealLoop *phase );

  // Check for Node being a loop-breaking test
  Node *is_loop_exit(Node *iff) const;

  // Returns true if ctrl is executed on every complete iteration
  bool dominates_backedge(Node* ctrl);

  // Remove simplistic dead code from loop body
  void DCE_loop_body();

  // Look for loop-exit tests with my 50/50 guesses from the Parsing stage.
  // Replace with a 1-in-10 exit guess.
  void adjust_loop_exit_prob( PhaseIdealLoop *phase );

  // Return TRUE or FALSE if the loop should never be RCE'd or aligned.
  // Useful for unrolling loops with NO array accesses.
  bool policy_peel_only( PhaseIdealLoop *phase ) const;

  // Return TRUE or FALSE if the loop should be unswitched -- clone
  // loop with an invariant test
  bool policy_unswitching( PhaseIdealLoop *phase ) const;

  // Micro-benchmark spamming.  Remove empty loops.
  bool policy_do_remove_empty_loop( PhaseIdealLoop *phase );

  // Return TRUE or FALSE if the loop should be peeled or not.  Peel if we can
  // make some loop-invariant test (usually a null-check) happen before the
  // loop.
  bool policy_peeling( PhaseIdealLoop *phase ) const;

  // Return TRUE or FALSE if the loop should be maximally unrolled. Stash any
  // known trip count in the counted loop node.
  bool policy_maximally_unroll( PhaseIdealLoop *phase ) const;

  // Return TRUE or FALSE if the loop should be unrolled or not.  Unroll if
  // the loop is a CountedLoop and the body is small enough.
  bool policy_unroll( PhaseIdealLoop *phase ) const;

  // Return TRUE or FALSE if the loop should be range-check-eliminated.
  // Gather a list of IF tests that are dominated by iteration splitting;
  // also gather the end of the first split and the start of the 2nd split.
  bool policy_range_check( PhaseIdealLoop *phase ) const;

  // Return TRUE or FALSE if the loop should be cache-line aligned.
  // Gather the expression that does the alignment.  Note that only
  // one array base can be aligned in a loop (unless the VM guarantees
  // mutual alignment).  Note that if we vectorize short memory ops
  // into longer memory ops, we may want to increase alignment.
  bool policy_align( PhaseIdealLoop *phase ) const;

  // Compute loop trip count from profile data
  void compute_profile_trip_cnt( PhaseIdealLoop *phase );

  // Reassociate invariant expressions.
  void reassociate_invariants(PhaseIdealLoop *phase);
  // Reassociate invariant add and subtract expressions.
  Node* reassociate_add_sub(Node* n1, PhaseIdealLoop *phase);
  // Return nonzero index of invariant operand if invariant and variant
  // are combined with an Add or Sub. Helper for reassociate_invariants.
  int is_invariant_addition(Node* n, PhaseIdealLoop *phase);

  // Return true if n is invariant
  bool is_invariant(Node* n) const;

  // Put loop body on igvn work list
  void record_for_igvn();

  bool is_loop()    { return !_irreducible && _tail && !_tail->is_top(); }
  bool is_inner()   { return is_loop() && _child == NULL; }
  bool is_counted() { return is_loop() && _head != NULL && _head->is_CountedLoop(); }

#ifndef PRODUCT
  void dump_head( ) const;      // Dump loop head only
  void dump() const;            // Dump this loop recursively
  void verify_tree(IdealLoopTree *loop, const IdealLoopTree *parent) const;
#endif

};

// -----------------------------PhaseIdealLoop---------------------------------
// Computes the mapping from Nodes to IdealLoopTrees.  Organizes IdealLoopTrees into a
// loop tree.  Drives the loop-based transformations on the ideal graph.
class PhaseIdealLoop : public PhaseTransform {
  friend class IdealLoopTree;
  friend class SuperWord;
  // Pre-computed def-use info
  PhaseIterGVN &_igvn;

  // Head of loop tree
  IdealLoopTree *_ltree_root;

  // Array of pre-order numbers, plus post-visited bit.
  // ZERO for not pre-visited.  EVEN for pre-visited but not post-visited.
  // ODD for post-visited.  Other bits are the pre-order number.
  uint *_preorders;
  uint _max_preorder;

  const PhaseIdealLoop* _verify_me;
  bool _verify_only;

  // Allocate _preorders[] array
  void allocate_preorders() {
    _max_preorder = C->unique()+8;
    _preorders = NEW_RESOURCE_ARRAY(uint, _max_preorder);
    memset(_preorders, 0, sizeof(uint) * _max_preorder);
  }

  // Allocate _preorders[] array
  void reallocate_preorders() {
    if ( _max_preorder < C->unique() ) {
      _preorders = REALLOC_RESOURCE_ARRAY(uint, _preorders, _max_preorder, C->unique());
      _max_preorder = C->unique();
    }
    memset(_preorders, 0, sizeof(uint) * _max_preorder);
  }

  // Check to grow _preorders[] array for the case when build_loop_tree_impl()
  // adds new nodes.
  void check_grow_preorders( ) {
    if ( _max_preorder < C->unique() ) {
      uint newsize = _max_preorder<<1;  // double size of array
      _preorders = REALLOC_RESOURCE_ARRAY(uint, _preorders, _max_preorder, newsize);
      memset(&_preorders[_max_preorder],0,sizeof(uint)*(newsize-_max_preorder));
      _max_preorder = newsize;
    }
  }
  // Check for pre-visited.  Zero for NOT visited; non-zero for visited.
  int is_visited( Node *n ) const { return _preorders[n->_idx]; }
  // Pre-order numbers are written to the Nodes array as low-bit-set values.
  void set_preorder_visited( Node *n, int pre_order ) {
    assert( !is_visited( n ), "already set" );
    _preorders[n->_idx] = (pre_order<<1);
  };
  // Return pre-order number.
  int get_preorder( Node *n ) const { assert( is_visited(n), "" ); return _preorders[n->_idx]>>1; }

  // Check for being post-visited.
  // Should be previsited already (checked with assert(is_visited(n))).
  int is_postvisited( Node *n ) const { assert( is_visited(n), "" ); return _preorders[n->_idx]&1; }

  // Mark as post visited
  void set_postvisited( Node *n ) { assert( !is_postvisited( n ), "" ); _preorders[n->_idx] |= 1; }

  // Set/get control node out.  Set lower bit to distinguish from IdealLoopTree
  // Returns true if "n" is a data node, false if it's a control node.
  bool has_ctrl( Node *n ) const { return ((intptr_t)_nodes[n->_idx]) & 1; }

  // clear out dead code after build_loop_late
  Node_List _deadlist;

  // Support for faster execution of get_late_ctrl()/dom_lca()
  // when a node has many uses and dominator depth is deep.
  Node_Array _dom_lca_tags;
  void   init_dom_lca_tags();
  void   clear_dom_lca_tags();

  // Helper for debugging bad dominance relationships
  bool verify_dominance(Node* n, Node* use, Node* LCA, Node* early);

  Node* compute_lca_of_uses(Node* n, Node* early, bool verify = false);

  // Inline wrapper for frequent cases:
  // 1) only one use
  // 2) a use is the same as the current LCA passed as 'n1'
  Node *dom_lca_for_get_late_ctrl( Node *lca, Node *n, Node *tag ) {
    assert( n->is_CFG(), "" );
    // Fast-path NULL lca
    if( lca != NULL && lca != n ) {
      assert( lca->is_CFG(), "" );
      // find LCA of all uses
      n = dom_lca_for_get_late_ctrl_internal( lca, n, tag );
    }
    return find_non_split_ctrl(n);
  }
  Node *dom_lca_for_get_late_ctrl_internal( Node *lca, Node *n, Node *tag );

  // true if CFG node d dominates CFG node n
  bool is_dominator(Node *d, Node *n);

  // Helper function for directing control inputs away from CFG split
  // points.
  Node *find_non_split_ctrl( Node *ctrl ) const {
    if (ctrl != NULL) {
      if (ctrl->is_MultiBranch()) {
        ctrl = ctrl->in(0);
      }
      assert(ctrl->is_CFG(), "CFG");
    }
    return ctrl;
  }

public:
  bool has_node( Node* n ) const { return _nodes[n->_idx] != NULL; }
  // check if transform created new nodes that need _ctrl recorded
  Node *get_late_ctrl( Node *n, Node *early );
  Node *get_early_ctrl( Node *n );
  void set_early_ctrl( Node *n );
  void set_subtree_ctrl( Node *root );
  void set_ctrl( Node *n, Node *ctrl ) {
    assert( !has_node(n) || has_ctrl(n), "" );
    assert( ctrl->in(0), "cannot set dead control node" );
    assert( ctrl == find_non_split_ctrl(ctrl), "must set legal crtl" );
    _nodes.map( n->_idx, (Node*)((intptr_t)ctrl + 1) );
  }
  // Set control and update loop membership
  void set_ctrl_and_loop(Node* n, Node* ctrl) {
    IdealLoopTree* old_loop = get_loop(get_ctrl(n));
    IdealLoopTree* new_loop = get_loop(ctrl);
    if (old_loop != new_loop) {
      if (old_loop->_child == NULL) old_loop->_body.yank(n);
      if (new_loop->_child == NULL) new_loop->_body.push(n);
    }
    set_ctrl(n, ctrl);
  }
  // Control nodes can be replaced or subsumed.  During this pass they
  // get their replacement Node in slot 1.  Instead of updating the block
  // location of all Nodes in the subsumed block, we lazily do it.  As we
  // pull such a subsumed block out of the array, we write back the final
  // correct block.
  Node *get_ctrl( Node *i ) {
    assert(has_node(i), "");
    Node *n = get_ctrl_no_update(i);
    _nodes.map( i->_idx, (Node*)((intptr_t)n + 1) );
    assert(has_node(i) && has_ctrl(i), "");
    assert(n == find_non_split_ctrl(n), "must return legal ctrl" );
    return n;
  }

private:
  Node *get_ctrl_no_update( Node *i ) const {
    assert( has_ctrl(i), "" );
    Node *n = (Node*)(((intptr_t)_nodes[i->_idx]) & ~1);
    if (!n->in(0)) {
      // Skip dead CFG nodes
      do {
        n = (Node*)(((intptr_t)_nodes[n->_idx]) & ~1);
      } while (!n->in(0));
      n = find_non_split_ctrl(n);
    }
    return n;
  }

  // Check for loop being set
  // "n" must be a control node. Returns true if "n" is known to be in a loop.
  bool has_loop( Node *n ) const {
    assert(!has_node(n) || !has_ctrl(n), "");
    return has_node(n);
  }
  // Set loop
  void set_loop( Node *n, IdealLoopTree *loop ) {
    _nodes.map(n->_idx, (Node*)loop);
  }
  // Lazy-dazy update of 'get_ctrl' and 'idom_at' mechanisms.  Replace
  // the 'old_node' with 'new_node'.  Kill old-node.  Add a reference
  // from old_node to new_node to support the lazy update.  Reference
  // replaces loop reference, since that is not neede for dead node.
public:
  void lazy_update( Node *old_node, Node *new_node ) {
    assert( old_node != new_node, "no cycles please" );
    //old_node->set_req( 1, new_node /*NO DU INFO*/ );
    // Nodes always have DU info now, so re-use the side array slot
    // for this node to provide the forwarding pointer.
    _nodes.map( old_node->_idx, (Node*)((intptr_t)new_node + 1) );
  }
  void lazy_replace( Node *old_node, Node *new_node ) {
    _igvn.hash_delete(old_node);
    _igvn.subsume_node( old_node, new_node );
    lazy_update( old_node, new_node );
  }
  void lazy_replace_proj( Node *old_node, Node *new_node ) {
    assert( old_node->req() == 1, "use this for Projs" );
    _igvn.hash_delete(old_node); // Must hash-delete before hacking edges
    old_node->add_req( NULL );
    lazy_replace( old_node, new_node );
  }

private:

  // Place 'n' in some loop nest, where 'n' is a CFG node
  void build_loop_tree();
  int build_loop_tree_impl( Node *n, int pre_order );
  // Insert loop into the existing loop tree.  'innermost' is a leaf of the
  // loop tree, not the root.
  IdealLoopTree *sort( IdealLoopTree *loop, IdealLoopTree *innermost );

  // Place Data nodes in some loop nest
  void build_loop_early( VectorSet &visited, Node_List &worklist, Node_Stack &nstack );
  void build_loop_late ( VectorSet &visited, Node_List &worklist, Node_Stack &nstack );
  void build_loop_late_post ( Node* n );

  // Array of immediate dominance info for each CFG node indexed by node idx
private:
  uint _idom_size;
  Node **_idom;                 // Array of immediate dominators
  uint *_dom_depth;           // Used for fast LCA test
  GrowableArray<uint>* _dom_stk; // For recomputation of dom depth

  Node* idom_no_update(Node* d) const {
    assert(d->_idx < _idom_size, "oob");
    Node* n = _idom[d->_idx];
    assert(n != NULL,"Bad immediate dominator info.");
    while (n->in(0) == NULL) {  // Skip dead CFG nodes
      //n = n->in(1);
      n = (Node*)(((intptr_t)_nodes[n->_idx]) & ~1);
      assert(n != NULL,"Bad immediate dominator info.");
    }
    return n;
  }
  Node *idom(Node* d) const {
    uint didx = d->_idx;
    Node *n = idom_no_update(d);
    _idom[didx] = n;            // Lazily remove dead CFG nodes from table.
    return n;
  }
  uint dom_depth(Node* d) const {
    assert(d->_idx < _idom_size, "");
    return _dom_depth[d->_idx];
  }
  void set_idom(Node* d, Node* n, uint dom_depth);
  // Locally compute IDOM using dom_lca call
  Node *compute_idom( Node *region ) const;
  // Recompute dom_depth
  void recompute_dom_depth();

  // Is safept not required by an outer loop?
  bool is_deleteable_safept(Node* sfpt);

  // Perform verification that the graph is valid.
  PhaseIdealLoop( PhaseIterGVN &igvn) :
    PhaseTransform(Ideal_Loop),
    _igvn(igvn),
    _dom_lca_tags(C->comp_arena()),
    _verify_me(NULL),
    _verify_only(true) {
    build_and_optimize(false);
  }

  // build the loop tree and perform any requested optimizations
  void build_and_optimize(bool do_split_if);

public:
  // Dominators for the sea of nodes
  void Dominators();
  Node *dom_lca( Node *n1, Node *n2 ) const {
    return find_non_split_ctrl(dom_lca_internal(n1, n2));
  }
  Node *dom_lca_internal( Node *n1, Node *n2 ) const;

  // Compute the Ideal Node to Loop mapping
  PhaseIdealLoop( PhaseIterGVN &igvn, bool do_split_ifs) :
    PhaseTransform(Ideal_Loop),
    _igvn(igvn),
    _dom_lca_tags(C->comp_arena()),
    _verify_me(NULL),
    _verify_only(false) {
    build_and_optimize(do_split_ifs);
  }

  // Verify that verify_me made the same decisions as a fresh run.
  PhaseIdealLoop( PhaseIterGVN &igvn, const PhaseIdealLoop *verify_me) :
    PhaseTransform(Ideal_Loop),
    _igvn(igvn),
    _dom_lca_tags(C->comp_arena()),
    _verify_me(verify_me),
    _verify_only(false) {
    build_and_optimize(false);
  }

  // Build and verify the loop tree without modifying the graph.  This
  // is useful to verify that all inputs properly dominate their uses.
  static void verify(PhaseIterGVN& igvn) {
#ifdef ASSERT
    PhaseIdealLoop v(igvn);
#endif
  }

  // True if the method has at least 1 irreducible loop
  bool _has_irreducible_loops;

  // Per-Node transform
  virtual Node *transform( Node *a_node ) { return 0; }

  Node *is_counted_loop( Node *x, IdealLoopTree *loop );

  // Return a post-walked LoopNode
  IdealLoopTree *get_loop( Node *n ) const {
    // Dead nodes have no loop, so return the top level loop instead
    if (!has_node(n))  return _ltree_root;
    assert(!has_ctrl(n), "");
    return (IdealLoopTree*)_nodes[n->_idx];
  }

  // Is 'n' a (nested) member of 'loop'?
  int is_member( const IdealLoopTree *loop, Node *n ) const {
    return loop->is_member(get_loop(n)); }

  // This is the basic building block of the loop optimizations.  It clones an
  // entire loop body.  It makes an old_new loop body mapping; with this
  // mapping you can find the new-loop equivalent to an old-loop node.  All
  // new-loop nodes are exactly equal to their old-loop counterparts, all
  // edges are the same.  All exits from the old-loop now have a RegionNode
  // that merges the equivalent new-loop path.  This is true even for the
  // normal "loop-exit" condition.  All uses of loop-invariant old-loop values
  // now come from (one or more) Phis that merge their new-loop equivalents.
  // Parameter side_by_side_idom:
  //   When side_by_size_idom is NULL, the dominator tree is constructed for
  //      the clone loop to dominate the original.  Used in construction of
  //      pre-main-post loop sequence.
  //   When nonnull, the clone and original are side-by-side, both are
  //      dominated by the passed in side_by_side_idom node.  Used in
  //      construction of unswitched loops.
  void clone_loop( IdealLoopTree *loop, Node_List &old_new, int dom_depth,
                   Node* side_by_side_idom = NULL);

  // If we got the effect of peeling, either by actually peeling or by
  // making a pre-loop which must execute at least once, we can remove
  // all loop-invariant dominated tests in the main body.
  void peeled_dom_test_elim( IdealLoopTree *loop, Node_List &old_new );

  // Generate code to do a loop peel for the given loop (and body).
  // old_new is a temp array.
  void do_peeling( IdealLoopTree *loop, Node_List &old_new );

  // Add pre and post loops around the given loop.  These loops are used
  // during RCE, unrolling and aligning loops.
  void insert_pre_post_loops( IdealLoopTree *loop, Node_List &old_new, bool peel_only );
  // If Node n lives in the back_ctrl block, we clone a private version of n
  // in preheader_ctrl block and return that, otherwise return n.
  Node *clone_up_backedge_goo( Node *back_ctrl, Node *preheader_ctrl, Node *n );

  // Take steps to maximally unroll the loop.  Peel any odd iterations, then
  // unroll to do double iterations.  The next round of major loop transforms
  // will repeat till the doubled loop body does all remaining iterations in 1
  // pass.
  void do_maximally_unroll( IdealLoopTree *loop, Node_List &old_new );

  // Unroll the loop body one step - make each trip do 2 iterations.
  void do_unroll( IdealLoopTree *loop, Node_List &old_new, bool adjust_min_trip );

  // Return true if exp is a constant times an induction var
  bool is_scaled_iv(Node* exp, Node* iv, int* p_scale);

  // Return true if exp is a scaled induction var plus (or minus) constant
  bool is_scaled_iv_plus_offset(Node* exp, Node* iv, int* p_scale, Node** p_offset, int depth = 0);

  // Eliminate range-checks and other trip-counter vs loop-invariant tests.
  void do_range_check( IdealLoopTree *loop, Node_List &old_new );

  // Create a slow version of the loop by cloning the loop
  // and inserting an if to select fast-slow versions.
  ProjNode* create_slow_version_of_loop(IdealLoopTree *loop,
                                        Node_List &old_new);

  // Clone loop with an invariant test (that does not exit) and
  // insert a clone of the test that selects which version to
  // execute.
  void do_unswitching (IdealLoopTree *loop, Node_List &old_new);

  // Find candidate "if" for unswitching
  IfNode* find_unswitching_candidate(const IdealLoopTree *loop) const;

  // Range Check Elimination uses this function!
  // Constrain the main loop iterations so the affine function:
  //    scale_con * I + offset  <  limit
  // always holds true.  That is, either increase the number of iterations in
  // the pre-loop or the post-loop until the condition holds true in the main
  // loop.  Scale_con, offset and limit are all loop invariant.
  void add_constraint( int stride_con, int scale_con, Node *offset, Node *limit, Node *pre_ctrl, Node **pre_limit, Node **main_limit );

  // Partially peel loop up through last_peel node.
  bool partial_peel( IdealLoopTree *loop, Node_List &old_new );

  // Create a scheduled list of nodes control dependent on ctrl set.
  void scheduled_nodelist( IdealLoopTree *loop, VectorSet& ctrl, Node_List &sched );
  // Has a use in the vector set
  bool has_use_in_set( Node* n, VectorSet& vset );
  // Has use internal to the vector set (ie. not in a phi at the loop head)
  bool has_use_internal_to_set( Node* n, VectorSet& vset, IdealLoopTree *loop );
  // clone "n" for uses that are outside of loop
  void clone_for_use_outside_loop( IdealLoopTree *loop, Node* n, Node_List& worklist );
  // clone "n" for special uses that are in the not_peeled region
  void clone_for_special_use_inside_loop( IdealLoopTree *loop, Node* n,
                                          VectorSet& not_peel, Node_List& sink_list, Node_List& worklist );
  // Insert phi(lp_entry_val, back_edge_val) at use->in(idx) for loop lp if phi does not already exist
  void insert_phi_for_loop( Node* use, uint idx, Node* lp_entry_val, Node* back_edge_val, LoopNode* lp );
#ifdef ASSERT
  // Validate the loop partition sets: peel and not_peel
  bool is_valid_loop_partition( IdealLoopTree *loop, VectorSet& peel, Node_List& peel_list, VectorSet& not_peel );
  // Ensure that uses outside of loop are of the right form
  bool is_valid_clone_loop_form( IdealLoopTree *loop, Node_List& peel_list,
                                 uint orig_exit_idx, uint clone_exit_idx);
  bool is_valid_clone_loop_exit_use( IdealLoopTree *loop, Node* use, uint exit_idx);
#endif

  // Returns nonzero constant stride if-node is a possible iv test (otherwise returns zero.)
  int stride_of_possible_iv( Node* iff );
  bool is_possible_iv_test( Node* iff ) { return stride_of_possible_iv(iff) != 0; }
  // Return the (unique) control output node that's in the loop (if it exists.)
  Node* stay_in_loop( Node* n, IdealLoopTree *loop);
  // Insert a signed compare loop exit cloned from an unsigned compare.
  IfNode* insert_cmpi_loop_exit(IfNode* if_cmpu, IdealLoopTree *loop);
  void remove_cmpi_loop_exit(IfNode* if_cmp, IdealLoopTree *loop);
  // Utility to register node "n" with PhaseIdealLoop
  void register_node(Node* n, IdealLoopTree *loop, Node* pred, int ddepth);
  // Utility to create an if-projection
  ProjNode* proj_clone(ProjNode* p, IfNode* iff);
  // Force the iff control output to be the live_proj
  Node* short_circuit_if(IfNode* iff, ProjNode* live_proj);
  // Insert a region before an if projection
  RegionNode* insert_region_before_proj(ProjNode* proj);
  // Insert a new if before an if projection
  ProjNode* insert_if_before_proj(Node* left, bool Signed, BoolTest::mask relop, Node* right, ProjNode* proj);

  // Passed in a Phi merging (recursively) some nearly equivalent Bool/Cmps.
  // "Nearly" because all Nodes have been cloned from the original in the loop,
  // but the fall-in edges to the Cmp are different.  Clone bool/Cmp pairs
  // through the Phi recursively, and return a Bool.
  BoolNode *clone_iff( PhiNode *phi, IdealLoopTree *loop );
  CmpNode *clone_bool( PhiNode *phi, IdealLoopTree *loop );


  // Rework addressing expressions to get the most loop-invariant stuff
  // moved out.  We'd like to do all associative operators, but it's especially
  // important (common) to do address expressions.
  Node *remix_address_expressions( Node *n );

  // Attempt to use a conditional move instead of a phi/branch
  Node *conditional_move( Node *n );

  // Reorganize offset computations to lower register pressure.
  // Mostly prevent loop-fallout uses of the pre-incremented trip counter
  // (which are then alive with the post-incremented trip counter
  // forcing an extra register move)
  void reorg_offsets( IdealLoopTree *loop );

  // Check for aggressive application of 'split-if' optimization,
  // using basic block level info.
  void  split_if_with_blocks     ( VectorSet &visited, Node_Stack &nstack );
  Node *split_if_with_blocks_pre ( Node *n );
  void  split_if_with_blocks_post( Node *n );
  Node *has_local_phi_input( Node *n );
  // Mark an IfNode as being dominated by a prior test,
  // without actually altering the CFG (and hence IDOM info).
  void dominated_by( Node *prevdom, Node *iff );

  // Split Node 'n' through merge point
  Node *split_thru_region( Node *n, Node *region );
  // Split Node 'n' through merge point if there is enough win.
  Node *split_thru_phi( Node *n, Node *region, int policy );
  // Found an If getting its condition-code input from a Phi in the
  // same block.  Split thru the Region.
  void do_split_if( Node *iff );

private:
  // Return a type based on condition control flow
  const TypeInt* filtered_type( Node *n, Node* n_ctrl);
  const TypeInt* filtered_type( Node *n ) { return filtered_type(n, NULL); }
 // Helpers for filtered type
  const TypeInt* filtered_type_from_dominators( Node* val, Node *val_ctrl);

  // Helper functions
  void register_new_node( Node *n, Node *blk );
  Node *spinup( Node *iff, Node *new_false, Node *new_true, Node *region, Node *phi, small_cache *cache );
  Node *find_use_block( Node *use, Node *def, Node *old_false, Node *new_false, Node *old_true, Node *new_true );
  void handle_use( Node *use, Node *def, small_cache *cache, Node *region_dom, Node *new_false, Node *new_true, Node *old_false, Node *old_true );
  bool split_up( Node *n, Node *blk1, Node *blk2 );
  void sink_use( Node *use, Node *post_loop );
  Node *place_near_use( Node *useblock ) const;

  bool _created_loop_node;
public:
  void set_created_loop_node() { _created_loop_node = true; }
  bool created_loop_node()     { return _created_loop_node; }

#ifndef PRODUCT
  void dump( ) const;
  void dump( IdealLoopTree *loop, uint rpo_idx, Node_List &rpo_list ) const;
  void rpo( Node *start, Node_Stack &stk, VectorSet &visited, Node_List &rpo_list ) const;
  void verify() const;          // Major slow  :-)
  void verify_compare( Node *n, const PhaseIdealLoop *loop_verify, VectorSet &visited ) const;
  IdealLoopTree *get_loop_idx(Node* n) const {
    // Dead nodes have no loop, so return the top level loop instead
    return _nodes[n->_idx] ? (IdealLoopTree*)_nodes[n->_idx] : _ltree_root;
  }
  // Print some stats
  static void print_statistics();
  static int _loop_invokes;     // Count of PhaseIdealLoop invokes
  static int _loop_work;        // Sum of PhaseIdealLoop x _unique
#endif
};

inline Node* IdealLoopTree::tail() {
// Handle lazy update of _tail field
  Node *n = _tail;
  //while( !n->in(0) )  // Skip dead CFG nodes
    //n = n->in(1);
  if (n->in(0) == NULL)
    n = _phase->get_ctrl(n);
  _tail = n;
  return n;
}


// Iterate over the loop tree using a preorder, left-to-right traversal.
//
// Example that visits all counted loops from within PhaseIdealLoop
//
//  for (LoopTreeIterator iter(_ltree_root); !iter.done(); iter.next()) {
//   IdealLoopTree* lpt = iter.current();
//   if (!lpt->is_counted()) continue;
//   ...
class LoopTreeIterator : public StackObj {
private:
  IdealLoopTree* _root;
  IdealLoopTree* _curnt;

public:
  LoopTreeIterator(IdealLoopTree* root) : _root(root), _curnt(root) {}

  bool done() { return _curnt == NULL; }       // Finished iterating?

  void next();                                 // Advance to next loop tree

  IdealLoopTree* current() { return _curnt; }  // Return current value of iterator.
};
