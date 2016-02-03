/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_LOOPNODE_HPP
#define SHARE_VM_OPTO_LOOPNODE_HPP

#include "opto/cfgnode.hpp"
#include "opto/multnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/subnode.hpp"
#include "opto/type.hpp"

class CmpNode;
class CountedLoopEndNode;
class CountedLoopNode;
class IdealLoopTree;
class LoopNode;
class Node;
class PhaseIdealLoop;
class CountedLoopReserveKit;
class VectorSet;
class Invariance;
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
  enum { Normal=0, Pre=1, Main=2, Post=3, PreMainPostFlagsMask=3,
         MainHasNoPreLoop=4,
         HasExactTripCount=8,
         InnerLoop=16,
         PartialPeelLoop=32,
         PartialPeelFailed=64,
         HasReductions=128,
         WasSlpAnalyzed=256,
         PassedSlpAnalysis=512,
         DoUnrollOnly=1024 };
  char _unswitch_count;
  enum { _unswitch_max=3 };

public:
  // Names for edge indices
  enum { Self=0, EntryControl, LoopBackControl };

  int is_inner_loop() const { return _loop_flags & InnerLoop; }
  void set_inner_loop() { _loop_flags |= InnerLoop; }

  int is_partial_peel_loop() const { return _loop_flags & PartialPeelLoop; }
  void set_partial_peel_loop() { _loop_flags |= PartialPeelLoop; }
  int partial_peel_has_failed() const { return _loop_flags & PartialPeelFailed; }
  void mark_partial_peel_failed() { _loop_flags |= PartialPeelFailed; }
  void mark_has_reductions() { _loop_flags |= HasReductions; }
  void mark_was_slp() { _loop_flags |= WasSlpAnalyzed; }
  void mark_passed_slp() { _loop_flags |= PassedSlpAnalysis; }
  void mark_do_unroll_only() { _loop_flags |= DoUnrollOnly; }

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
  bool is_valid_counted_loop() const;
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif
};

//------------------------------Counted Loops----------------------------------
// Counted loops are all trip-counted loops, with exactly 1 trip-counter exit
// path (and maybe some other exit paths).  The trip-counter exit is always
// last in the loop.  The trip-counter have to stride by a constant;
// the exit value is also loop invariant.

// CountedLoopNodes and CountedLoopEndNodes come in matched pairs.  The
// CountedLoopNode has the incoming loop control and the loop-back-control
// which is always the IfTrue before the matching CountedLoopEndNode.  The
// CountedLoopEndNode has an incoming control (possibly not the
// CountedLoopNode if there is control flow in the loop), the post-increment
// trip-counter value, and the limit.  The trip-counter value is always of
// the form (Op old-trip-counter stride).  The old-trip-counter is produced
// by a Phi connected to the CountedLoopNode.  The stride is constant.
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

  // Known trip count calculated by compute_exact_trip_count()
  uint  _trip_count;

  // Expected trip count from profile data
  float _profile_trip_cnt;

  // Log2 of original loop bodies in unrolled loop
  int _unrolled_count_log2;

  // Node count prior to last unrolling - used to decide if
  // unroll,optimize,unroll,optimize,... is making progress
  int _node_count_before_unroll;

  // If slp analysis is performed we record the maximum
  // vector mapped unroll factor here
  int _slp_maximum_unroll_factor;

public:
  CountedLoopNode( Node *entry, Node *backedge )
    : LoopNode(entry, backedge), _main_idx(0), _trip_count(max_juint),
      _profile_trip_cnt(COUNT_UNKNOWN), _unrolled_count_log2(0),
      _node_count_before_unroll(0), _slp_maximum_unroll_factor(0) {
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
  int is_normal_loop   () const { return (_loop_flags&PreMainPostFlagsMask) == Normal; }
  int is_pre_loop      () const { return (_loop_flags&PreMainPostFlagsMask) == Pre;    }
  int is_main_loop     () const { return (_loop_flags&PreMainPostFlagsMask) == Main;   }
  int is_post_loop     () const { return (_loop_flags&PreMainPostFlagsMask) == Post;   }
  int is_reduction_loop() const { return (_loop_flags&HasReductions) == HasReductions; }
  int was_slp_analyzed () const { return (_loop_flags&WasSlpAnalyzed) == WasSlpAnalyzed; }
  int has_passed_slp   () const { return (_loop_flags&PassedSlpAnalysis) == PassedSlpAnalysis; }
  int do_unroll_only      () const { return (_loop_flags&DoUnrollOnly) == DoUnrollOnly; }
  int is_main_no_pre_loop() const { return _loop_flags & MainHasNoPreLoop; }
  void set_main_no_pre_loop() { _loop_flags |= MainHasNoPreLoop; }

  int main_idx() const { return _main_idx; }


  void set_pre_loop  (CountedLoopNode *main) { assert(is_normal_loop(),""); _loop_flags |= Pre ; _main_idx = main->_idx; }
  void set_main_loop (                     ) { assert(is_normal_loop(),""); _loop_flags |= Main;                         }
  void set_post_loop (CountedLoopNode *main) { assert(is_normal_loop(),""); _loop_flags |= Post; _main_idx = main->_idx; }
  void set_normal_loop(                    ) { _loop_flags &= ~PreMainPostFlagsMask; }

  void set_trip_count(uint tc) { _trip_count = tc; }
  uint trip_count()            { return _trip_count; }

  bool has_exact_trip_count() const { return (_loop_flags & HasExactTripCount) != 0; }
  void set_exact_trip_count(uint tc) {
    _trip_count = tc;
    _loop_flags |= HasExactTripCount;
  }
  void set_nonexact_trip_count() {
    _loop_flags &= ~HasExactTripCount;
  }
  void set_notpassed_slp() {
    _loop_flags &= ~PassedSlpAnalysis;
  }

  void set_profile_trip_cnt(float ptc) { _profile_trip_cnt = ptc; }
  float profile_trip_cnt()             { return _profile_trip_cnt; }

  void double_unrolled_count() { _unrolled_count_log2++; }
  int  unrolled_count()        { return 1 << MIN2(_unrolled_count_log2, BitsPerInt-3); }

  void set_node_count_before_unroll(int ct)  { _node_count_before_unroll = ct; }
  int  node_count_before_unroll()            { return _node_count_before_unroll; }
  void set_slp_max_unroll(int unroll_factor) { _slp_maximum_unroll_factor = unroll_factor; }
  int  slp_max_unroll() const                { return _slp_maximum_unroll_factor; }

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
    // The CountedLoopNode that goes with this CountedLoopEndNode may
    // have been optimized out by the IGVN so be cautious with the
    // pattern matching on the graph
    if (phi() == NULL) {
      return NULL;
    }
    assert(phi()->is_Phi(), "should be PhiNode");
    Node *ln = phi()->in(0);
    if (ln->is_CountedLoop() && ln->as_CountedLoop()->loopexit() == this) {
      return (CountedLoopNode*)ln;
    }
    return NULL;
  }

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

//------------------------------LoopLimitNode-----------------------------
// Counted Loop limit node which represents exact final iterator value:
// trip_count = (limit - init_trip + stride - 1)/stride
// final_value= trip_count * stride + init_trip.
// Use HW instructions to calculate it when it can overflow in integer.
// Note, final_value should fit into integer since counted loop has
// limit check: limit <= max_int-stride.
class LoopLimitNode : public Node {
  enum { Init=1, Limit=2, Stride=3 };
 public:
  LoopLimitNode( Compile* C, Node *init, Node *limit, Node *stride ) : Node(0,init,limit,stride) {
    // Put it on the Macro nodes list to optimize during macro nodes expansion.
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
};

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
  int _local_loop_unroll_limit;
  int _local_loop_unroll_factor;

  Node_List _body;              // Loop body for inner loops

  uint8_t _nest;                // Nesting depth
  uint8_t _irreducible:1,       // True if irreducible
          _has_call:1,          // True if has call safepoint
          _has_sfpt:1,          // True if has non-call safepoint
          _rce_candidate:1;     // True if candidate for range check elimination

  Node_List* _safepts;          // List of safepoints in this loop
  Node_List* _required_safept;  // A inner loop cannot delete these safepts;
  bool  _allow_optimizations;   // Allow loop optimizations

  IdealLoopTree( PhaseIdealLoop* phase, Node *head, Node *tail )
    : _parent(0), _next(0), _child(0),
      _head(head), _tail(tail),
      _phase(phase),
      _safepts(NULL),
      _required_safept(NULL),
      _allow_optimizations(true),
      _nest(0), _irreducible(0), _has_call(0), _has_sfpt(0), _rce_candidate(0),
      _local_loop_unroll_limit(0), _local_loop_unroll_factor(0)
  { }

  // Is 'l' a member of 'this'?
  bool is_member(const IdealLoopTree *l) const; // Test for nested membership

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

  // Perform optimization to use the loop predicates for null checks and range checks.
  // Applies to any loop level (not just the innermost one)
  bool loop_predication( PhaseIdealLoop *phase);

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

  // Remove safepoints from loop. Optionally keeping one.
  void remove_safepoints(PhaseIdealLoop* phase, bool keep_one);

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

  // Convert one iteration loop into normal code.
  bool policy_do_one_iteration_loop( PhaseIdealLoop *phase );

  // Return TRUE or FALSE if the loop should be peeled or not.  Peel if we can
  // make some loop-invariant test (usually a null-check) happen before the
  // loop.
  bool policy_peeling( PhaseIdealLoop *phase ) const;

  // Return TRUE or FALSE if the loop should be maximally unrolled. Stash any
  // known trip count in the counted loop node.
  bool policy_maximally_unroll( PhaseIdealLoop *phase ) const;

  // Return TRUE or FALSE if the loop should be unrolled or not.  Unroll if
  // the loop is a CountedLoop and the body is small enough.
  bool policy_unroll(PhaseIdealLoop *phase);

  // Loop analyses to map to a maximal superword unrolling for vectorization.
  void policy_unroll_slp_analysis(CountedLoopNode *cl, PhaseIdealLoop *phase, int future_unroll_ct);

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

  // Return TRUE if "iff" is a range check.
  bool is_range_check_if(IfNode *iff, PhaseIdealLoop *phase, Invariance& invar) const;

  // Compute loop exact trip count if possible
  void compute_exact_trip_count( PhaseIdealLoop *phase );

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

  void remove_main_post_loops(CountedLoopNode *cl, PhaseIdealLoop *phase);

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
  friend class CountedLoopReserveKit;

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

  bool cast_incr_before_loop(Node* incr, Node* ctrl, Node* loop);

public:
  bool has_node( Node* n ) const {
    guarantee(n != NULL, "No Node.");
    return _nodes[n->_idx] != NULL;
  }
  // check if transform created new nodes that need _ctrl recorded
  Node *get_late_ctrl( Node *n, Node *early );
  Node *get_early_ctrl( Node *n );
  Node *get_early_ctrl_for_expensive(Node *n, Node* earliest);
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
  // true if CFG node d dominates CFG node n
  bool is_dominator(Node *d, Node *n);
  // return get_ctrl for a data node and self(n) for a CFG node
  Node* ctrl_or_self(Node* n) {
    if (has_ctrl(n))
      return get_ctrl(n);
    else {
      assert (n->is_CFG(), "must be a CFG node");
      return n;
    }
  }

private:
  Node *get_ctrl_no_update_helper(Node *i) const {
    assert(has_ctrl(i), "should be control, not loop");
    return (Node*)(((intptr_t)_nodes[i->_idx]) & ~1);
  }

  Node *get_ctrl_no_update(Node *i) const {
    assert( has_ctrl(i), "" );
    Node *n = get_ctrl_no_update_helper(i);
    if (!n->in(0)) {
      // Skip dead CFG nodes
      do {
        n = get_ctrl_no_update_helper(n);
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
  // replaces loop reference, since that is not needed for dead node.
public:
  void lazy_update(Node *old_node, Node *new_node) {
    assert(old_node != new_node, "no cycles please");
    // Re-use the side array slot for this node to provide the
    // forwarding pointer.
    _nodes.map(old_node->_idx, (Node*)((intptr_t)new_node + 1));
  }
  void lazy_replace(Node *old_node, Node *new_node) {
    _igvn.replace_node(old_node, new_node);
    lazy_update(old_node, new_node);
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
    guarantee(d != NULL, "Null dominator info.");
    guarantee(d->_idx < _idom_size, "");
    return _dom_depth[d->_idx];
  }
  void set_idom(Node* d, Node* n, uint dom_depth);
  // Locally compute IDOM using dom_lca call
  Node *compute_idom( Node *region ) const;
  // Recompute dom_depth
  void recompute_dom_depth();

  // Is safept not required by an outer loop?
  bool is_deleteable_safept(Node* sfpt);

  // Replace parallel induction variable (parallel to trip counter)
  void replace_parallel_iv(IdealLoopTree *loop);

  // Perform verification that the graph is valid.
  PhaseIdealLoop( PhaseIterGVN &igvn) :
    PhaseTransform(Ideal_Loop),
    _igvn(igvn),
    _dom_lca_tags(arena()), // Thread::resource_area
    _verify_me(NULL),
    _verify_only(true) {
    build_and_optimize(false, false);
  }

  // build the loop tree and perform any requested optimizations
  void build_and_optimize(bool do_split_if, bool skip_loop_opts);

public:
  // Dominators for the sea of nodes
  void Dominators();
  Node *dom_lca( Node *n1, Node *n2 ) const {
    return find_non_split_ctrl(dom_lca_internal(n1, n2));
  }
  Node *dom_lca_internal( Node *n1, Node *n2 ) const;

  // Compute the Ideal Node to Loop mapping
  PhaseIdealLoop( PhaseIterGVN &igvn, bool do_split_ifs, bool skip_loop_opts = false) :
    PhaseTransform(Ideal_Loop),
    _igvn(igvn),
    _dom_lca_tags(arena()), // Thread::resource_area
    _verify_me(NULL),
    _verify_only(false) {
    build_and_optimize(do_split_ifs, skip_loop_opts);
  }

  // Verify that verify_me made the same decisions as a fresh run.
  PhaseIdealLoop( PhaseIterGVN &igvn, const PhaseIdealLoop *verify_me) :
    PhaseTransform(Ideal_Loop),
    _igvn(igvn),
    _dom_lca_tags(arena()), // Thread::resource_area
    _verify_me(verify_me),
    _verify_only(false) {
    build_and_optimize(false, false);
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

  bool is_counted_loop( Node *x, IdealLoopTree *loop );

  Node* exact_limit( IdealLoopTree *loop );

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
  Node *clone_up_backedge_goo( Node *back_ctrl, Node *preheader_ctrl, Node *n, VectorSet &visited, Node_Stack &clones );

  // Take steps to maximally unroll the loop.  Peel any odd iterations, then
  // unroll to do double iterations.  The next round of major loop transforms
  // will repeat till the doubled loop body does all remaining iterations in 1
  // pass.
  void do_maximally_unroll( IdealLoopTree *loop, Node_List &old_new );

  // Unroll the loop body one step - make each trip do 2 iterations.
  void do_unroll( IdealLoopTree *loop, Node_List &old_new, bool adjust_min_trip );

  // Mark vector reduction candidates before loop unrolling
  void mark_reductions( IdealLoopTree *loop );

  // Return true if exp is a constant times an induction var
  bool is_scaled_iv(Node* exp, Node* iv, int* p_scale);

  // Return true if exp is a scaled induction var plus (or minus) constant
  bool is_scaled_iv_plus_offset(Node* exp, Node* iv, int* p_scale, Node** p_offset, int depth = 0);

  // Create a new if above the uncommon_trap_if_pattern for the predicate to be promoted
  ProjNode* create_new_if_for_predicate(ProjNode* cont_proj, Node* new_entry,
                                        Deoptimization::DeoptReason reason,
                                        int opcode);
  void register_control(Node* n, IdealLoopTree *loop, Node* pred);

  // Clone loop predicates to cloned loops (peeled, unswitched)
  static ProjNode* clone_predicate(ProjNode* predicate_proj, Node* new_entry,
                                   Deoptimization::DeoptReason reason,
                                   PhaseIdealLoop* loop_phase,
                                   PhaseIterGVN* igvn);

  static Node* clone_loop_predicates(Node* old_entry, Node* new_entry,
                                         bool clone_limit_check,
                                         PhaseIdealLoop* loop_phase,
                                         PhaseIterGVN* igvn);
  Node* clone_loop_predicates(Node* old_entry, Node* new_entry, bool clone_limit_check);

  static Node* skip_loop_predicates(Node* entry);

  // Find a good location to insert a predicate
  static ProjNode* find_predicate_insertion_point(Node* start_c, Deoptimization::DeoptReason reason);
  // Find a predicate
  static Node* find_predicate(Node* entry);
  // Construct a range check for a predicate if
  BoolNode* rc_predicate(IdealLoopTree *loop, Node* ctrl,
                         int scale, Node* offset,
                         Node* init, Node* limit, Node* stride,
                         Node* range, bool upper);

  // Implementation of the loop predication to promote checks outside the loop
  bool loop_predication_impl(IdealLoopTree *loop);

  // Helper function to collect predicate for eliminating the useless ones
  void collect_potentially_useful_predicates(IdealLoopTree *loop, Unique_Node_List &predicate_opaque1);
  void eliminate_useless_predicates();

  // Change the control input of expensive nodes to allow commoning by
  // IGVN when it is guaranteed to not result in a more frequent
  // execution of the expensive node. Return true if progress.
  bool process_expensive_nodes();

  // Check whether node has become unreachable
  bool is_node_unreachable(Node *n) const {
    return !has_node(n) || n->is_unreachable(_igvn);
  }

  // Eliminate range-checks and other trip-counter vs loop-invariant tests.
  void do_range_check( IdealLoopTree *loop, Node_List &old_new );

  // Create a slow version of the loop by cloning the loop
  // and inserting an if to select fast-slow versions.
  ProjNode* create_slow_version_of_loop(IdealLoopTree *loop,
                                        Node_List &old_new,
                                        int opcode);

  // Clone a loop and return the clone head (clone_loop_head).
  // Added nodes include int(1), int(0) - disconnected, If, IfTrue, IfFalse,
  // This routine was created for usage in CountedLoopReserveKit.
  //
  //    int(1) -> If -> IfTrue -> original_loop_head
  //              |
  //              V
  //           IfFalse -> clone_loop_head (returned by function pointer)
  //
  LoopNode* create_reserve_version_of_loop(IdealLoopTree *loop, CountedLoopReserveKit* lk);
  // Clone loop with an invariant test (that does not exit) and
  // insert a clone of the test that selects which version to
  // execute.
  void do_unswitching (IdealLoopTree *loop, Node_List &old_new);

  // Find candidate "if" for unswitching
  IfNode* find_unswitching_candidate(const IdealLoopTree *loop) const;

  // Range Check Elimination uses this function!
  // Constrain the main loop iterations so the affine function:
  //    low_limit <= scale_con * I + offset  <  upper_limit
  // always holds true.  That is, either increase the number of iterations in
  // the pre-loop or the post-loop until the condition holds true in the main
  // loop.  Scale_con, offset and limit are all loop invariant.
  void add_constraint( int stride_con, int scale_con, Node *offset, Node *low_limit, Node *upper_limit, Node *pre_ctrl, Node **pre_limit, Node **main_limit );
  // Helper function for add_constraint().
  Node* adjust_limit( int stride_con, Node * scale, Node *offset, Node *rc_limit, Node *loop_limit, Node *pre_ctrl );

  // Partially peel loop up through last_peel node.
  bool partial_peel( IdealLoopTree *loop, Node_List &old_new );

  // Create a scheduled list of nodes control dependent on ctrl set.
  void scheduled_nodelist( IdealLoopTree *loop, VectorSet& ctrl, Node_List &sched );
  // Has a use in the vector set
  bool has_use_in_set( Node* n, VectorSet& vset );
  // Has use internal to the vector set (ie. not in a phi at the loop head)
  bool has_use_internal_to_set( Node* n, VectorSet& vset, IdealLoopTree *loop );
  // clone "n" for uses that are outside of loop
  int  clone_for_use_outside_loop( IdealLoopTree *loop, Node* n, Node_List& worklist );
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
  void dominated_by( Node *prevdom, Node *iff, bool flip = false, bool exclude_loop_predicate = false );

  // Split Node 'n' through merge point
  Node *split_thru_region( Node *n, Node *region );
  // Split Node 'n' through merge point if there is enough win.
  Node *split_thru_phi( Node *n, Node *region, int policy );
  // Found an If getting its condition-code input from a Phi in the
  // same block.  Split thru the Region.
  void do_split_if( Node *iff );

  // Conversion of fill/copy patterns into intrisic versions
  bool do_intrinsify_fill();
  bool intrinsify_fill(IdealLoopTree* lpt);
  bool match_fill_loop(IdealLoopTree* lpt, Node*& store, Node*& store_value,
                       Node*& shift, Node*& offset);

private:
  // Return a type based on condition control flow
  const TypeInt* filtered_type( Node *n, Node* n_ctrl);
  const TypeInt* filtered_type( Node *n ) { return filtered_type(n, NULL); }
 // Helpers for filtered type
  const TypeInt* filtered_type_from_dominators( Node* val, Node *val_ctrl);

  // Helper functions
  Node *spinup( Node *iff, Node *new_false, Node *new_true, Node *region, Node *phi, small_cache *cache );
  Node *find_use_block( Node *use, Node *def, Node *old_false, Node *new_false, Node *old_true, Node *new_true );
  void handle_use( Node *use, Node *def, small_cache *cache, Node *region_dom, Node *new_false, Node *new_true, Node *old_false, Node *old_true );
  bool split_up( Node *n, Node *blk1, Node *blk2 );
  void sink_use( Node *use, Node *post_loop );
  Node *place_near_use( Node *useblock ) const;
  Node* try_move_store_before_loop(Node* n, Node *n_ctrl);
  void try_move_store_after_loop(Node* n);
  bool identical_backtoback_ifs(Node *n);
  bool can_split_if(Node *n_ctrl);

  bool _created_loop_node;
public:
  void set_created_loop_node() { _created_loop_node = true; }
  bool created_loop_node()     { return _created_loop_node; }
  void register_new_node( Node *n, Node *blk );

#ifdef ASSERT
  void dump_bad_graph(const char* msg, Node* n, Node* early, Node* LCA);
#endif

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

// This kit may be used for making of a reserved copy of a loop before this loop
//  goes under non-reversible changes.
//
// Function create_reserve() creates a reserved copy (clone) of the loop.
// The reserved copy is created by calling
// PhaseIdealLoop::create_reserve_version_of_loop - see there how
// the original and reserved loops are connected in the outer graph.
// If create_reserve succeeded, it returns 'true' and _has_reserved is set to 'true'.
//
// By default the reserved copy (clone) of the loop is created as dead code - it is
// dominated in the outer loop by this node chain:
//   intcon(1)->If->IfFalse->reserved_copy.
// The original loop is dominated by the the same node chain but IfTrue projection:
//   intcon(0)->If->IfTrue->original_loop.
//
// In this implementation of CountedLoopReserveKit the ctor includes create_reserve()
// and the dtor, checks _use_new value.
// If _use_new == false, it "switches" control to reserved copy of the loop
// by simple replacing of node intcon(1) with node intcon(0).
//
// Here is a proposed example of usage (see also SuperWord::output in superword.cpp).
//
// void CountedLoopReserveKit_example()
// {
//    CountedLoopReserveKit lrk((phase, lpt, DoReserveCopy = true); // create local object
//    if (DoReserveCopy && !lrk.has_reserved()) {
//      return; //failed to create reserved loop copy
//    }
//    ...
//    //something is wrong, switch to original loop
///   if(something_is_wrong) return; // ~CountedLoopReserveKit makes the switch
//    ...
//    //everything worked ok, return with the newly modified loop
//    lrk.use_new();
//    return; // ~CountedLoopReserveKit does nothing once use_new() was called
//  }
//
// Keep in mind, that by default if create_reserve() is not followed by use_new()
// the dtor will "switch to the original" loop.
// NOTE. You you modify outside of the original loop this class is no help.
//
class CountedLoopReserveKit {
  private:
    PhaseIdealLoop* _phase;
    IdealLoopTree*  _lpt;
    LoopNode*       _lp;
    IfNode*         _iff;
    LoopNode*       _lp_reserved;
    bool            _has_reserved;
    bool            _use_new;
    const bool      _active; //may be set to false in ctor, then the object is dummy

  public:
    CountedLoopReserveKit(PhaseIdealLoop* phase, IdealLoopTree *loop, bool active);
    ~CountedLoopReserveKit();
    void use_new()                {_use_new = true;}
    void set_iff(IfNode* x)       {_iff = x;}
    bool has_reserved()     const { return _active && _has_reserved;}
  private:
    bool create_reserve();
};// class CountedLoopReserveKit

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

#endif // SHARE_VM_OPTO_LOOPNODE_HPP
