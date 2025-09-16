/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2025, Alibaba Group Holding Limited. All rights reserved.
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

#include "gc/shared/barrierSet.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "libadt/vectset.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "opto/ad.hpp"
#include "opto/callGenerator.hpp"
#include "opto/castnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/connode.hpp"
#include "opto/loopnode.hpp"
#include "opto/machnode.hpp"
#include "opto/matcher.hpp"
#include "opto/node.hpp"
#include "opto/opcodes.hpp"
#include "opto/regmask.hpp"
#include "opto/rootnode.hpp"
#include "opto/type.hpp"
#include "utilities/copy.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/stringUtils.hpp"

class RegMask;
// #include "phase.hpp"
class PhaseTransform;
class PhaseGVN;

// Arena we are currently building Nodes in
const uint Node::NotAMachineReg = 0xffff0000;

#ifndef PRODUCT
extern uint nodes_created;
#endif
#ifdef __clang__
#pragma clang diagnostic push
#pragma GCC diagnostic ignored "-Wuninitialized"
#endif

#ifdef ASSERT

//-------------------------- construct_node------------------------------------
// Set a breakpoint here to identify where a particular node index is built.
void Node::verify_construction() {
  _debug_orig = nullptr;
  // The decimal digits of _debug_idx are <compile_id> followed by 10 digits of <_idx>
  Compile* C = Compile::current();
  assert(C->unique() < (INT_MAX - 1), "Node limit exceeded INT_MAX");
  uint64_t new_debug_idx = (uint64_t)C->compile_id() * 10000000000 + _idx;
  set_debug_idx(new_debug_idx);
  if (!C->phase_optimize_finished()) {
    // Only check assert during parsing and optimization phase. Skip it while generating code.
    assert(C->live_nodes() <= C->max_node_limit(), "Live Node limit exceeded limit");
  }
  if (BreakAtNode != 0 && (_debug_idx == BreakAtNode || (uint64_t)_idx == BreakAtNode)) {
    tty->print_cr("BreakAtNode: _idx=%d _debug_idx=" UINT64_FORMAT, _idx, _debug_idx);
    BREAKPOINT;
  }
#if OPTO_DU_ITERATOR_ASSERT
  _last_del = nullptr;
  _del_tick = 0;
#endif
  _hash_lock = 0;
}


// #ifdef ASSERT ...

#if OPTO_DU_ITERATOR_ASSERT
void DUIterator_Common::sample(const Node* node) {
  _vdui     = VerifyDUIterators;
  _node     = node;
  _outcnt   = node->_outcnt;
  _del_tick = node->_del_tick;
  _last     = nullptr;
}

void DUIterator_Common::verify(const Node* node, bool at_end_ok) {
  assert(_node     == node, "consistent iterator source");
  assert(_del_tick == node->_del_tick, "no unexpected deletions allowed");
}

void DUIterator_Common::verify_resync() {
  // Ensure that the loop body has just deleted the last guy produced.
  const Node* node = _node;
  // Ensure that at least one copy of the last-seen edge was deleted.
  // Note:  It is OK to delete multiple copies of the last-seen edge.
  // Unfortunately, we have no way to verify that all the deletions delete
  // that same edge.  On this point we must use the Honor System.
  assert(node->_del_tick >= _del_tick+1, "must have deleted an edge");
  assert(node->_last_del == _last, "must have deleted the edge just produced");
  // We liked this deletion, so accept the resulting outcnt and tick.
  _outcnt   = node->_outcnt;
  _del_tick = node->_del_tick;
}

void DUIterator_Common::reset(const DUIterator_Common& that) {
  if (this == &that)  return;  // ignore assignment to self
  if (!_vdui) {
    // We need to initialize everything, overwriting garbage values.
    _last = that._last;
    _vdui = that._vdui;
  }
  // Note:  It is legal (though odd) for an iterator over some node x
  // to be reassigned to iterate over another node y.  Some doubly-nested
  // progress loops depend on being able to do this.
  const Node* node = that._node;
  // Re-initialize everything, except _last.
  _node     = node;
  _outcnt   = node->_outcnt;
  _del_tick = node->_del_tick;
}

void DUIterator::sample(const Node* node) {
  DUIterator_Common::sample(node);      // Initialize the assertion data.
  _refresh_tick = 0;                    // No refreshes have happened, as yet.
}

void DUIterator::verify(const Node* node, bool at_end_ok) {
  DUIterator_Common::verify(node, at_end_ok);
  assert(_idx      <  node->_outcnt + (uint)at_end_ok, "idx in range");
}

void DUIterator::verify_increment() {
  if (_refresh_tick & 1) {
    // We have refreshed the index during this loop.
    // Fix up _idx to meet asserts.
    if (_idx > _outcnt)  _idx = _outcnt;
  }
  verify(_node, true);
}

void DUIterator::verify_resync() {
  // Note:  We do not assert on _outcnt, because insertions are OK here.
  DUIterator_Common::verify_resync();
  // Make sure we are still in sync, possibly with no more out-edges:
  verify(_node, true);
}

void DUIterator::reset(const DUIterator& that) {
  if (this == &that)  return;  // self assignment is always a no-op
  assert(that._refresh_tick == 0, "assign only the result of Node::outs()");
  assert(that._idx          == 0, "assign only the result of Node::outs()");
  assert(_idx               == that._idx, "already assigned _idx");
  if (!_vdui) {
    // We need to initialize everything, overwriting garbage values.
    sample(that._node);
  } else {
    DUIterator_Common::reset(that);
    if (_refresh_tick & 1) {
      _refresh_tick++;                  // Clear the "was refreshed" flag.
    }
    assert(_refresh_tick < 2*100000, "DU iteration must converge quickly");
  }
}

void DUIterator::refresh() {
  DUIterator_Common::sample(_node);     // Re-fetch assertion data.
  _refresh_tick |= 1;                   // Set the "was refreshed" flag.
}

void DUIterator::verify_finish() {
  // If the loop has killed the node, do not require it to re-run.
  if (_node->_outcnt == 0)  _refresh_tick &= ~1;
  // If this assert triggers, it means that a loop used refresh_out_pos
  // to re-synch an iteration index, but the loop did not correctly
  // re-run itself, using a "while (progress)" construct.
  // This iterator enforces the rule that you must keep trying the loop
  // until it "runs clean" without any need for refreshing.
  assert(!(_refresh_tick & 1), "the loop must run once with no refreshing");
}


void DUIterator_Fast::verify(const Node* node, bool at_end_ok) {
  DUIterator_Common::verify(node, at_end_ok);
  Node** out    = node->_out;
  uint   cnt    = node->_outcnt;
  assert(cnt == _outcnt, "no insertions allowed");
  assert(_outp >= out && _outp <= out + cnt - !at_end_ok, "outp in range");
  // This last check is carefully designed to work for NO_OUT_ARRAY.
}

void DUIterator_Fast::verify_limit() {
  const Node* node = _node;
  verify(node, true);
  assert(_outp == node->_out + node->_outcnt, "limit still correct");
}

void DUIterator_Fast::verify_resync() {
  const Node* node = _node;
  if (_outp == node->_out + _outcnt) {
    // Note that the limit imax, not the pointer i, gets updated with the
    // exact count of deletions.  (For the pointer it's always "--i".)
    assert(node->_outcnt+node->_del_tick == _outcnt+_del_tick, "no insertions allowed with deletion(s)");
    // This is a limit pointer, with a name like "imax".
    // Fudge the _last field so that the common assert will be happy.
    _last = (Node*) node->_last_del;
    DUIterator_Common::verify_resync();
  } else {
    assert(node->_outcnt < _outcnt, "no insertions allowed with deletion(s)");
    // A normal internal pointer.
    DUIterator_Common::verify_resync();
    // Make sure we are still in sync, possibly with no more out-edges:
    verify(node, true);
  }
}

void DUIterator_Fast::verify_relimit(uint n) {
  const Node* node = _node;
  assert((int)n > 0, "use imax -= n only with a positive count");
  // This must be a limit pointer, with a name like "imax".
  assert(_outp == node->_out + node->_outcnt, "apply -= only to a limit (imax)");
  // The reported number of deletions must match what the node saw.
  assert(node->_del_tick == _del_tick + n, "must have deleted n edges");
  // Fudge the _last field so that the common assert will be happy.
  _last = (Node*) node->_last_del;
  DUIterator_Common::verify_resync();
}

void DUIterator_Fast::reset(const DUIterator_Fast& that) {
  assert(_outp              == that._outp, "already assigned _outp");
  DUIterator_Common::reset(that);
}

void DUIterator_Last::verify(const Node* node, bool at_end_ok) {
  // at_end_ok means the _outp is allowed to underflow by 1
  _outp += at_end_ok;
  DUIterator_Fast::verify(node, at_end_ok);  // check _del_tick, etc.
  _outp -= at_end_ok;
  assert(_outp == (node->_out + node->_outcnt) - 1, "pointer must point to end of nodes");
}

void DUIterator_Last::verify_limit() {
  // Do not require the limit address to be resynched.
  //verify(node, true);
  assert(_outp == _node->_out, "limit still correct");
}

void DUIterator_Last::verify_step(uint num_edges) {
  assert((int)num_edges > 0, "need non-zero edge count for loop progress");
  _outcnt   -= num_edges;
  _del_tick += num_edges;
  // Make sure we are still in sync, possibly with no more out-edges:
  const Node* node = _node;
  verify(node, true);
  assert(node->_last_del == _last, "must have deleted the edge just produced");
}

#endif //OPTO_DU_ITERATOR_ASSERT


#endif //ASSERT


// This constant used to initialize _out may be any non-null value.
// The value null is reserved for the top node only.
#define NO_OUT_ARRAY ((Node**)-1)

// Out-of-line code from node constructors.
// Executed only when extra debug info. is being passed around.
static void init_node_notes(Compile* C, int idx, Node_Notes* nn) {
  C->set_node_notes_at(idx, nn);
}

// Shared initialization code.
inline int Node::Init(int req) {
  Compile* C = Compile::current();
  int idx = C->next_unique();
  NOT_PRODUCT(_igv_idx = C->next_igv_idx());

  // Allocate memory for the necessary number of edges.
  if (req > 0) {
    // Allocate space for _in array to have double alignment.
    _in = (Node **) ((char *) (C->node_arena()->AmallocWords(req * sizeof(void*))));
  }
  // If there are default notes floating around, capture them:
  Node_Notes* nn = C->default_node_notes();
  if (nn != nullptr)  init_node_notes(C, idx, nn);

  // Note:  At this point, C is dead,
  // and we begin to initialize the new Node.

  _cnt = _max = req;
  _outcnt = _outmax = 0;
  _class_id = Class_Node;
  _flags = 0;
  _out = NO_OUT_ARRAY;
  return idx;
}

//------------------------------Node-------------------------------------------
// Create a Node, with a given number of required edges.
Node::Node(uint req)
  : _idx(Init(req))
#ifdef ASSERT
  , _parse_idx(_idx)
#endif
{
  assert( req < Compile::current()->max_node_limit() - NodeLimitFudgeFactor, "Input limit exceeded" );
  DEBUG_ONLY( verify_construction() );
  NOT_PRODUCT(nodes_created++);
  if (req == 0) {
    _in = nullptr;
  } else {
    Node** to = _in;
    for(uint i = 0; i < req; i++) {
      to[i] = nullptr;
    }
  }
}

//------------------------------Node-------------------------------------------
Node::Node(Node *n0)
  : _idx(Init(1))
#ifdef ASSERT
  , _parse_idx(_idx)
#endif
{
  DEBUG_ONLY( verify_construction() );
  NOT_PRODUCT(nodes_created++);
  assert( is_not_dead(n0), "can not use dead node");
  _in[0] = n0; if (n0 != nullptr) n0->add_out((Node *)this);
}

//------------------------------Node-------------------------------------------
Node::Node(Node *n0, Node *n1)
  : _idx(Init(2))
#ifdef ASSERT
  , _parse_idx(_idx)
#endif
{
  DEBUG_ONLY( verify_construction() );
  NOT_PRODUCT(nodes_created++);
  assert( is_not_dead(n0), "can not use dead node");
  assert( is_not_dead(n1), "can not use dead node");
  _in[0] = n0; if (n0 != nullptr) n0->add_out((Node *)this);
  _in[1] = n1; if (n1 != nullptr) n1->add_out((Node *)this);
}

//------------------------------Node-------------------------------------------
Node::Node(Node *n0, Node *n1, Node *n2)
  : _idx(Init(3))
#ifdef ASSERT
  , _parse_idx(_idx)
#endif
{
  DEBUG_ONLY( verify_construction() );
  NOT_PRODUCT(nodes_created++);
  assert( is_not_dead(n0), "can not use dead node");
  assert( is_not_dead(n1), "can not use dead node");
  assert( is_not_dead(n2), "can not use dead node");
  _in[0] = n0; if (n0 != nullptr) n0->add_out((Node *)this);
  _in[1] = n1; if (n1 != nullptr) n1->add_out((Node *)this);
  _in[2] = n2; if (n2 != nullptr) n2->add_out((Node *)this);
}

//------------------------------Node-------------------------------------------
Node::Node(Node *n0, Node *n1, Node *n2, Node *n3)
  : _idx(Init(4))
#ifdef ASSERT
  , _parse_idx(_idx)
#endif
{
  DEBUG_ONLY( verify_construction() );
  NOT_PRODUCT(nodes_created++);
  assert( is_not_dead(n0), "can not use dead node");
  assert( is_not_dead(n1), "can not use dead node");
  assert( is_not_dead(n2), "can not use dead node");
  assert( is_not_dead(n3), "can not use dead node");
  _in[0] = n0; if (n0 != nullptr) n0->add_out((Node *)this);
  _in[1] = n1; if (n1 != nullptr) n1->add_out((Node *)this);
  _in[2] = n2; if (n2 != nullptr) n2->add_out((Node *)this);
  _in[3] = n3; if (n3 != nullptr) n3->add_out((Node *)this);
}

//------------------------------Node-------------------------------------------
Node::Node(Node *n0, Node *n1, Node *n2, Node *n3, Node *n4)
  : _idx(Init(5))
#ifdef ASSERT
  , _parse_idx(_idx)
#endif
{
  DEBUG_ONLY( verify_construction() );
  NOT_PRODUCT(nodes_created++);
  assert( is_not_dead(n0), "can not use dead node");
  assert( is_not_dead(n1), "can not use dead node");
  assert( is_not_dead(n2), "can not use dead node");
  assert( is_not_dead(n3), "can not use dead node");
  assert( is_not_dead(n4), "can not use dead node");
  _in[0] = n0; if (n0 != nullptr) n0->add_out((Node *)this);
  _in[1] = n1; if (n1 != nullptr) n1->add_out((Node *)this);
  _in[2] = n2; if (n2 != nullptr) n2->add_out((Node *)this);
  _in[3] = n3; if (n3 != nullptr) n3->add_out((Node *)this);
  _in[4] = n4; if (n4 != nullptr) n4->add_out((Node *)this);
}

//------------------------------Node-------------------------------------------
Node::Node(Node *n0, Node *n1, Node *n2, Node *n3,
                     Node *n4, Node *n5)
  : _idx(Init(6))
#ifdef ASSERT
  , _parse_idx(_idx)
#endif
{
  DEBUG_ONLY( verify_construction() );
  NOT_PRODUCT(nodes_created++);
  assert( is_not_dead(n0), "can not use dead node");
  assert( is_not_dead(n1), "can not use dead node");
  assert( is_not_dead(n2), "can not use dead node");
  assert( is_not_dead(n3), "can not use dead node");
  assert( is_not_dead(n4), "can not use dead node");
  assert( is_not_dead(n5), "can not use dead node");
  _in[0] = n0; if (n0 != nullptr) n0->add_out((Node *)this);
  _in[1] = n1; if (n1 != nullptr) n1->add_out((Node *)this);
  _in[2] = n2; if (n2 != nullptr) n2->add_out((Node *)this);
  _in[3] = n3; if (n3 != nullptr) n3->add_out((Node *)this);
  _in[4] = n4; if (n4 != nullptr) n4->add_out((Node *)this);
  _in[5] = n5; if (n5 != nullptr) n5->add_out((Node *)this);
}

//------------------------------Node-------------------------------------------
Node::Node(Node *n0, Node *n1, Node *n2, Node *n3,
                     Node *n4, Node *n5, Node *n6)
  : _idx(Init(7))
#ifdef ASSERT
  , _parse_idx(_idx)
#endif
{
  DEBUG_ONLY( verify_construction() );
  NOT_PRODUCT(nodes_created++);
  assert( is_not_dead(n0), "can not use dead node");
  assert( is_not_dead(n1), "can not use dead node");
  assert( is_not_dead(n2), "can not use dead node");
  assert( is_not_dead(n3), "can not use dead node");
  assert( is_not_dead(n4), "can not use dead node");
  assert( is_not_dead(n5), "can not use dead node");
  assert( is_not_dead(n6), "can not use dead node");
  _in[0] = n0; if (n0 != nullptr) n0->add_out((Node *)this);
  _in[1] = n1; if (n1 != nullptr) n1->add_out((Node *)this);
  _in[2] = n2; if (n2 != nullptr) n2->add_out((Node *)this);
  _in[3] = n3; if (n3 != nullptr) n3->add_out((Node *)this);
  _in[4] = n4; if (n4 != nullptr) n4->add_out((Node *)this);
  _in[5] = n5; if (n5 != nullptr) n5->add_out((Node *)this);
  _in[6] = n6; if (n6 != nullptr) n6->add_out((Node *)this);
}

#ifdef __clang__
#pragma clang diagnostic pop
#endif


//------------------------------clone------------------------------------------
// Clone a Node.
Node *Node::clone() const {
  Compile* C = Compile::current();
  uint s = size_of();           // Size of inherited Node
  Node *n = (Node*)C->node_arena()->AmallocWords(size_of() + _max*sizeof(Node*));
  Copy::conjoint_words_to_lower((HeapWord*)this, (HeapWord*)n, s);
  // Set the new input pointer array
  n->_in = (Node**)(((char*)n)+s);
  // Cannot share the old output pointer array, so kill it
  n->_out = NO_OUT_ARRAY;
  // And reset the counters to 0
  n->_outcnt = 0;
  n->_outmax = 0;
  // Unlock this guy, since he is not in any hash table.
  DEBUG_ONLY(n->_hash_lock = 0);
  // Walk the old node's input list to duplicate its edges
  uint i;
  for( i = 0; i < len(); i++ ) {
    Node *x = in(i);
    n->_in[i] = x;
    if (x != nullptr) x->add_out(n);
  }
  if (is_macro()) {
    C->add_macro_node(n);
  }
  if (is_expensive()) {
    C->add_expensive_node(n);
  }
  if (for_post_loop_opts_igvn()) {
    // Don't add cloned node to Compile::_for_post_loop_opts_igvn list automatically.
    // If it is applicable, it will happen anyway when the cloned node is registered with IGVN.
    n->remove_flag(Node::NodeFlags::Flag_for_post_loop_opts_igvn);
  }
  if (for_merge_stores_igvn()) {
    // Don't add cloned node to Compile::_for_merge_stores_igvn list automatically.
    // If it is applicable, it will happen anyway when the cloned node is registered with IGVN.
    n->remove_flag(Node::NodeFlags::Flag_for_merge_stores_igvn);
  }
  if (n->is_ParsePredicate()) {
    C->add_parse_predicate(n->as_ParsePredicate());
  }
  if (n->is_OpaqueTemplateAssertionPredicate()) {
    C->add_template_assertion_predicate_opaque(n->as_OpaqueTemplateAssertionPredicate());
  }

  BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
  bs->register_potential_barrier_node(n);

  n->set_idx(C->next_unique()); // Get new unique index as well
  NOT_PRODUCT(n->_igv_idx = C->next_igv_idx());
  DEBUG_ONLY( n->verify_construction() );
  NOT_PRODUCT(nodes_created++);
  // Do not patch over the debug_idx of a clone, because it makes it
  // impossible to break on the clone's moment of creation.
  //DEBUG_ONLY( n->set_debug_idx( debug_idx() ) );

  C->copy_node_notes_to(n, (Node*) this);

  // MachNode clone
  uint nopnds;
  if (this->is_Mach() && (nopnds = this->as_Mach()->num_opnds()) > 0) {
    MachNode *mach  = n->as_Mach();
    MachNode *mthis = this->as_Mach();
    // Get address of _opnd_array.
    // It should be the same offset since it is the clone of this node.
    MachOper **from = mthis->_opnds;
    MachOper **to = (MachOper **)((size_t)(&mach->_opnds) +
                    pointer_delta((const void*)from,
                                  (const void*)(&mthis->_opnds), 1));
    mach->_opnds = to;
    for ( uint i = 0; i < nopnds; ++i ) {
      to[i] = from[i]->clone();
    }
  }
  if (n->is_Call()) {
    // CallGenerator is linked to the original node.
    CallGenerator* cg = n->as_Call()->generator();
    if (cg != nullptr) {
      CallGenerator* cloned_cg = cg->with_call_node(n->as_Call());
      n->as_Call()->set_generator(cloned_cg);
    }
  }
  if (n->is_SafePoint()) {
    // Scalar replacement and macro expansion might modify the JVMState.
    // Clone it to make sure it's not shared between SafePointNodes.
    n->as_SafePoint()->clone_jvms(C);
    n->as_SafePoint()->clone_replaced_nodes();
  }
  Compile::current()->record_modified_node(n);
  return n;                     // Return the clone
}

//---------------------------setup_is_top--------------------------------------
// Call this when changing the top node, to reassert the invariants
// required by Node::is_top.  See Compile::set_cached_top_node.
void Node::setup_is_top() {
  if (this == (Node*)Compile::current()->top()) {
    // This node has just become top.  Kill its out array.
    _outcnt = _outmax = 0;
    _out = nullptr;                           // marker value for top
    assert(is_top(), "must be top");
  } else {
    if (_out == nullptr)  _out = NO_OUT_ARRAY;
    assert(!is_top(), "must not be top");
  }
}

//------------------------------~Node------------------------------------------
// Fancy destructor; eagerly attempt to reclaim Node numberings and storage
void Node::destruct(PhaseValues* phase) {
  Compile* compile = (phase != nullptr) ? phase->C : Compile::current();
  if (phase != nullptr && phase->is_IterGVN()) {
    phase->is_IterGVN()->_worklist.remove(this);
  }
  // If this is the most recently created node, reclaim its index. Otherwise,
  // record the node as dead to keep liveness information accurate.
  if ((uint)_idx+1 == compile->unique()) {
    compile->set_unique(compile->unique()-1);
  } else {
    compile->record_dead_node(_idx);
  }
  // Clear debug info:
  Node_Notes* nn = compile->node_notes_at(_idx);
  if (nn != nullptr)  nn->clear();
  // Walk the input array, freeing the corresponding output edges
  _cnt = _max;  // forget req/prec distinction
  uint i;
  for( i = 0; i < _max; i++ ) {
    set_req(i, nullptr);
    //assert(def->out(def->outcnt()-1) == (Node *)this,"bad def-use hacking in reclaim");
  }
  assert(outcnt() == 0, "deleting a node must not leave a dangling use");

  if (is_macro()) {
    compile->remove_macro_node(this);
  }
  if (is_expensive()) {
    compile->remove_expensive_node(this);
  }
  if (is_OpaqueTemplateAssertionPredicate()) {
    compile->remove_template_assertion_predicate_opaque(as_OpaqueTemplateAssertionPredicate());
  }
  if (is_ParsePredicate()) {
    compile->remove_parse_predicate(as_ParsePredicate());
  }
  if (for_post_loop_opts_igvn()) {
    compile->remove_from_post_loop_opts_igvn(this);
  }
  if (for_merge_stores_igvn()) {
    compile->remove_from_merge_stores_igvn(this);
  }

  if (is_SafePoint()) {
    as_SafePoint()->delete_replaced_nodes();

    if (is_CallStaticJava()) {
      compile->remove_unstable_if_trap(as_CallStaticJava(), false);
    }
  }
  BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
  bs->unregister_potential_barrier_node(this);

  // See if the input array was allocated just prior to the object
  int edge_size = _max*sizeof(void*);
  int out_edge_size = _outmax*sizeof(void*);
  char *in_array = ((char*)_in);
  char *edge_end = in_array + edge_size;
  char *out_array = (char*)(_out == NO_OUT_ARRAY? nullptr: _out);
  int node_size = size_of();

#ifdef ASSERT
  // We will not actually delete the storage, but we'll make the node unusable.
  compile->remove_modified_node(this);
  *(address*)this = badAddress;  // smash the C++ vtbl, probably
  _in = _out = (Node**) badAddress;
  _max = _cnt = _outmax = _outcnt = 0;
#endif

  // Free the output edge array
  if (out_edge_size > 0) {
    compile->node_arena()->Afree(out_array, out_edge_size);
  }

  // Free the input edge array and the node itself
  if( edge_end == (char*)this ) {
    // It was; free the input array and object all in one hit
#ifndef ASSERT
    compile->node_arena()->Afree(in_array, edge_size+node_size);
#endif
  } else {
    // Free just the input array
    compile->node_arena()->Afree(in_array, edge_size);

    // Free just the object
#ifndef ASSERT
    compile->node_arena()->Afree(this, node_size);
#endif
  }
}

// Resize input or output array to grow it to the next larger power-of-2 bigger
// than len.
void Node::resize_array(Node**& array, node_idx_t& max_size, uint len, bool needs_clearing) {
  Arena* arena = Compile::current()->node_arena();
  uint new_max = max_size;
  if (new_max == 0) {
    max_size = 4;
    array = (Node**)arena->Amalloc(4 * sizeof(Node*));
    if (needs_clearing) {
      array[0] = nullptr;
      array[1] = nullptr;
      array[2] = nullptr;
      array[3] = nullptr;
    }
    return;
  }
  new_max = next_power_of_2(len);
  assert(needs_clearing || (array != nullptr && array != NO_OUT_ARRAY), "out must have sensible value");
  array = (Node**)arena->Arealloc(array, max_size * sizeof(Node*), new_max * sizeof(Node*));
  if (needs_clearing) {
    Copy::zero_to_bytes(&array[max_size], (new_max - max_size) * sizeof(Node*)); // null all new space
  }
  max_size = new_max;               // Record new max length
  // This assertion makes sure that Node::_max is wide enough to
  // represent the numerical value of new_max.
  assert(max_size > len, "int width of _max or _outmax is too small");
}

//------------------------------grow-------------------------------------------
// Grow the input array, making space for more edges
void Node::grow(uint len) {
  resize_array(_in, _max, len, true);
}

//-----------------------------out_grow----------------------------------------
// Grow the input array, making space for more edges
void Node::out_grow(uint len) {
  assert(!is_top(), "cannot grow a top node's out array");
  resize_array(_out, _outmax, len, false);
}

#ifdef ASSERT
//------------------------------is_dead----------------------------------------
bool Node::is_dead() const {
  // Mach and pinch point nodes may look like dead.
  if( is_top() || is_Mach() || (Opcode() == Op_Node && _outcnt > 0) )
    return false;
  for( uint i = 0; i < _max; i++ )
    if( _in[i] != nullptr )
      return false;
  return true;
}

bool Node::is_not_dead(const Node* n) {
  return n == nullptr || !PhaseIterGVN::is_verify_def_use() || !(n->is_dead());
}

bool Node::is_reachable_from_root() const {
  ResourceMark rm;
  Unique_Node_List wq;
  wq.push((Node*)this);
  RootNode* root = Compile::current()->root();
  for (uint i = 0; i < wq.size(); i++) {
    Node* m = wq.at(i);
    if (m == root) {
      return true;
    }
    for (DUIterator_Fast jmax, j = m->fast_outs(jmax); j < jmax; j++) {
      Node* u = m->fast_out(j);
      wq.push(u);
    }
  }
  return false;
}
#endif

//------------------------------is_unreachable---------------------------------
bool Node::is_unreachable(PhaseIterGVN &igvn) const {
  assert(!is_Mach(), "doesn't work with MachNodes");
  return outcnt() == 0 || igvn.type(this) == Type::TOP || (in(0) != nullptr && in(0)->is_top());
}

//------------------------------add_req----------------------------------------
// Add a new required input at the end
void Node::add_req( Node *n ) {
  assert( is_not_dead(n), "can not use dead node");

  // Look to see if I can move precedence down one without reallocating
  if( (_cnt >= _max) || (in(_max-1) != nullptr) )
    grow( _max+1 );

  // Find a precedence edge to move
  if( in(_cnt) != nullptr ) {   // Next precedence edge is busy?
    uint i;
    for( i=_cnt; i<_max; i++ )
      if( in(i) == nullptr )    // Find the null at end of prec edge list
        break;                  // There must be one, since we grew the array
    _in[i] = in(_cnt);          // Move prec over, making space for req edge
  }
  _in[_cnt++] = n;            // Stuff over old prec edge
  if (n != nullptr) n->add_out((Node *)this);
  Compile::current()->record_modified_node(this);
}

//---------------------------add_req_batch-------------------------------------
// Add a new required input at the end
void Node::add_req_batch( Node *n, uint m ) {
  assert( is_not_dead(n), "can not use dead node");
  // check various edge cases
  if ((int)m <= 1) {
    assert((int)m >= 0, "oob");
    if (m != 0)  add_req(n);
    return;
  }

  // Look to see if I can move precedence down one without reallocating
  if( (_cnt+m) > _max || _in[_max-m] )
    grow( _max+m );

  // Find a precedence edge to move
  if( _in[_cnt] != nullptr ) {  // Next precedence edge is busy?
    uint i;
    for( i=_cnt; i<_max; i++ )
      if( _in[i] == nullptr )   // Find the null at end of prec edge list
        break;                  // There must be one, since we grew the array
    // Slide all the precs over by m positions (assume #prec << m).
    Copy::conjoint_words_to_higher((HeapWord*)&_in[_cnt], (HeapWord*)&_in[_cnt+m], ((i-_cnt)*sizeof(Node*)));
  }

  // Stuff over the old prec edges
  for(uint i=0; i<m; i++ ) {
    _in[_cnt++] = n;
  }

  // Insert multiple out edges on the node.
  if (n != nullptr && !n->is_top()) {
    for(uint i=0; i<m; i++ ) {
      n->add_out((Node *)this);
    }
  }
  Compile::current()->record_modified_node(this);
}

//------------------------------del_req----------------------------------------
// Delete the required edge and compact the edge array
void Node::del_req( uint idx ) {
  assert( idx < _cnt, "oob");
  assert( !VerifyHashTableKeys || _hash_lock == 0,
          "remove node from hash table before modifying it");
  // First remove corresponding def-use edge
  Node *n = in(idx);
  if (n != nullptr) n->del_out((Node *)this);
  _in[idx] = in(--_cnt); // Compact the array
  // Avoid spec violation: Gap in prec edges.
  close_prec_gap_at(_cnt);
  Compile::current()->record_modified_node(this);
}

//------------------------------del_req_ordered--------------------------------
// Delete the required edge and compact the edge array with preserved order
void Node::del_req_ordered( uint idx ) {
  assert( idx < _cnt, "oob");
  assert( !VerifyHashTableKeys || _hash_lock == 0,
          "remove node from hash table before modifying it");
  // First remove corresponding def-use edge
  Node *n = in(idx);
  if (n != nullptr) n->del_out((Node *)this);
  if (idx < --_cnt) {    // Not last edge ?
    Copy::conjoint_words_to_lower((HeapWord*)&_in[idx+1], (HeapWord*)&_in[idx], ((_cnt-idx)*sizeof(Node*)));
  }
  // Avoid spec violation: Gap in prec edges.
  close_prec_gap_at(_cnt);
  Compile::current()->record_modified_node(this);
}

//------------------------------ins_req----------------------------------------
// Insert a new required input at the end
void Node::ins_req( uint idx, Node *n ) {
  assert( is_not_dead(n), "can not use dead node");
  add_req(nullptr);                // Make space
  assert( idx < _max, "Must have allocated enough space");
  // Slide over
  if(_cnt-idx-1 > 0) {
    Copy::conjoint_words_to_higher((HeapWord*)&_in[idx], (HeapWord*)&_in[idx+1], ((_cnt-idx-1)*sizeof(Node*)));
  }
  _in[idx] = n;                            // Stuff over old required edge
  if (n != nullptr) n->add_out((Node *)this); // Add reciprocal def-use edge
  Compile::current()->record_modified_node(this);
}

//-----------------------------find_edge---------------------------------------
int Node::find_edge(Node* n) {
  for (uint i = 0; i < len(); i++) {
    if (_in[i] == n)  return i;
  }
  return -1;
}

//----------------------------replace_edge-------------------------------------
int Node::replace_edge(Node* old, Node* neww, PhaseGVN* gvn) {
  if (old == neww)  return 0;  // nothing to do
  uint nrep = 0;
  for (uint i = 0; i < len(); i++) {
    if (in(i) == old) {
      if (i < req()) {
        if (gvn != nullptr) {
          set_req_X(i, neww, gvn);
        } else {
          set_req(i, neww);
        }
      } else {
        assert(gvn == nullptr || gvn->is_IterGVN() == nullptr, "no support for igvn here");
        assert(find_prec_edge(neww) == -1, "spec violation: duplicated prec edge (node %d -> %d)", _idx, neww->_idx);
        set_prec(i, neww);
      }
      nrep++;
    }
  }
  return nrep;
}

/**
 * Replace input edges in the range pointing to 'old' node.
 */
int Node::replace_edges_in_range(Node* old, Node* neww, int start, int end, PhaseGVN* gvn) {
  if (old == neww)  return 0;  // nothing to do
  uint nrep = 0;
  for (int i = start; i < end; i++) {
    if (in(i) == old) {
      set_req_X(i, neww, gvn);
      nrep++;
    }
  }
  return nrep;
}

//-------------------------disconnect_inputs-----------------------------------
// null out all inputs to eliminate incoming Def-Use edges.
void Node::disconnect_inputs(Compile* C) {
  // the layout of Node::_in
  // r: a required input, null is allowed
  // p: a precedence, null values are all at the end
  // -----------------------------------
  // |r|...|r|p|...|p|null|...|null|
  //         |                     |
  //         req()                 len()
  // -----------------------------------
  for (uint i = 0; i < req(); ++i) {
    if (in(i) != nullptr) {
      set_req(i, nullptr);
    }
  }

  // Remove precedence edges if any exist
  // Note: Safepoints may have precedence edges, even during parsing
  for (uint i = len(); i > req(); ) {
    rm_prec(--i);  // no-op if _in[i] is null
  }

#ifdef ASSERT
  // sanity check
  for (uint i = 0; i < len(); ++i) {
    assert(_in[i] == nullptr, "disconnect_inputs() failed!");
  }
#endif

  // Node::destruct requires all out edges be deleted first
  // DEBUG_ONLY(destruct();)   // no reuse benefit expected
  C->record_dead_node(_idx);
}

//-----------------------------uncast---------------------------------------
// %%% Temporary, until we sort out CheckCastPP vs. CastPP.
// Strip away casting.  (It is depth-limited.)
// Optionally, keep casts with dependencies.
Node* Node::uncast(bool keep_deps) const {
  // Should be inline:
  //return is_ConstraintCast() ? uncast_helper(this) : (Node*) this;
  if (is_ConstraintCast()) {
    return uncast_helper(this, keep_deps);
  } else {
    return (Node*) this;
  }
}

// Find out of current node that matches opcode.
Node* Node::find_out_with(int opcode) {
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    Node* use = fast_out(i);
    if (use->Opcode() == opcode) {
      return use;
    }
  }
  return nullptr;
}

// Return true if the current node has an out that matches opcode.
bool Node::has_out_with(int opcode) {
  return (find_out_with(opcode) != nullptr);
}

// Return true if the current node has an out that matches any of the opcodes.
bool Node::has_out_with(int opcode1, int opcode2, int opcode3, int opcode4) {
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
      int opcode = fast_out(i)->Opcode();
      if (opcode == opcode1 || opcode == opcode2 || opcode == opcode3 || opcode == opcode4) {
        return true;
      }
  }
  return false;
}


//---------------------------uncast_helper-------------------------------------
Node* Node::uncast_helper(const Node* p, bool keep_deps) {
#ifdef ASSERT
  uint depth_count = 0;
  const Node* orig_p = p;
#endif

  while (true) {
#ifdef ASSERT
    if (depth_count >= K) {
      orig_p->dump(4);
      if (p != orig_p)
        p->dump(1);
    }
    assert(depth_count++ < K, "infinite loop in Node::uncast_helper");
#endif
    if (p == nullptr || p->req() != 2) {
      break;
    } else if (p->is_ConstraintCast()) {
      if (keep_deps && p->as_ConstraintCast()->carry_dependency()) {
        break; // stop at casts with dependencies
      }
      p = p->in(1);
    } else {
      break;
    }
  }
  return (Node*) p;
}

//------------------------------add_prec---------------------------------------
// Add a new precedence input.  Precedence inputs are unordered, with
// duplicates removed and nulls packed down at the end.
void Node::add_prec( Node *n ) {
  assert( is_not_dead(n), "can not use dead node");

  // Check for null at end
  if( _cnt >= _max || in(_max-1) )
    grow( _max+1 );

  // Find a precedence edge to move
  uint i = _cnt;
  while( in(i) != nullptr ) {
    if (in(i) == n) return; // Avoid spec violation: duplicated prec edge.
    i++;
  }
  _in[i] = n;                                   // Stuff prec edge over null
  if ( n != nullptr) n->add_out((Node *)this);  // Add mirror edge

#ifdef ASSERT
  while ((++i)<_max) { assert(_in[i] == nullptr, "spec violation: Gap in prec edges (node %d)", _idx); }
#endif
  Compile::current()->record_modified_node(this);
}

//------------------------------rm_prec----------------------------------------
// Remove a precedence input.  Precedence inputs are unordered, with
// duplicates removed and nulls packed down at the end.
void Node::rm_prec( uint j ) {
  assert(j < _max, "oob: i=%d, _max=%d", j, _max);
  assert(j >= _cnt, "not a precedence edge");
  if (_in[j] == nullptr) return;   // Avoid spec violation: Gap in prec edges.
  _in[j]->del_out((Node *)this);
  close_prec_gap_at(j);
  Compile::current()->record_modified_node(this);
}

//------------------------------size_of----------------------------------------
uint Node::size_of() const { return sizeof(*this); }

//------------------------------ideal_reg--------------------------------------
uint Node::ideal_reg() const { return 0; }

//------------------------------jvms-------------------------------------------
JVMState* Node::jvms() const { return nullptr; }

#ifdef ASSERT
//------------------------------jvms-------------------------------------------
bool Node::verify_jvms(const JVMState* using_jvms) const {
  for (JVMState* jvms = this->jvms(); jvms != nullptr; jvms = jvms->caller()) {
    if (jvms == using_jvms)  return true;
  }
  return false;
}

//------------------------------init_NodeProperty------------------------------
void Node::init_NodeProperty() {
  assert(_max_classes <= max_juint, "too many NodeProperty classes");
  assert(max_flags() <= max_juint, "too many NodeProperty flags");
}

//-----------------------------max_flags---------------------------------------
juint Node::max_flags() {
  return (PD::_last_flag << 1) - 1; // allow flags combination
}
#endif

//------------------------------format-----------------------------------------
// Print as assembly
void Node::format( PhaseRegAlloc *, outputStream *st ) const {}
//------------------------------emit-------------------------------------------
// Emit bytes using C2_MacroAssembler
void Node::emit(C2_MacroAssembler *masm, PhaseRegAlloc *ra_) const {}
//------------------------------size-------------------------------------------
// Size of instruction in bytes
uint Node::size(PhaseRegAlloc *ra_) const { return 0; }

//------------------------------CFG Construction-------------------------------
// Nodes that end basic blocks, e.g. IfTrue/IfFalse, JumpProjNode, Root,
// Goto and Return.
const Node *Node::is_block_proj() const { return nullptr; }

// Minimum guaranteed type
const Type *Node::bottom_type() const { return Type::BOTTOM; }


//------------------------------raise_bottom_type------------------------------
// Get the worst-case Type output for this Node.
void Node::raise_bottom_type(const Type* new_type) {
  if (is_Type()) {
    TypeNode *n = this->as_Type();
    if (VerifyAliases) {
      assert(new_type->higher_equal_speculative(n->type()), "new type must refine old type");
    }
    n->set_type(new_type);
  } else if (is_Load()) {
    LoadNode *n = this->as_Load();
    if (VerifyAliases) {
      assert(new_type->higher_equal_speculative(n->type()), "new type must refine old type");
    }
    n->set_type(new_type);
  }
}

//------------------------------Identity---------------------------------------
// Return a node that the given node is equivalent to.
Node* Node::Identity(PhaseGVN* phase) {
  return this;                  // Default to no identities
}

//------------------------------Value------------------------------------------
// Compute a new Type for a node using the Type of the inputs.
const Type* Node::Value(PhaseGVN* phase) const {
  return bottom_type();         // Default to worst-case Type
}

//------------------------------Ideal------------------------------------------
//
// 'Idealize' the graph rooted at this Node.
//
// In order to be efficient and flexible there are some subtle invariants
// these Ideal calls need to hold.  Running with '-XX:VerifyIterativeGVN=1' checks
// these invariants, although its too slow to have on by default.  If you are
// hacking an Ideal call, be sure to test with '-XX:VerifyIterativeGVN=1'
//
// The Ideal call almost arbitrarily reshape the graph rooted at the 'this'
// pointer.  If ANY change is made, it must return the root of the reshaped
// graph - even if the root is the same Node.  Example: swapping the inputs
// to an AddINode gives the same answer and same root, but you still have to
// return the 'this' pointer instead of null.
//
// You cannot return an OLD Node, except for the 'this' pointer.  Use the
// Identity call to return an old Node; basically if Identity can find
// another Node have the Ideal call make no change and return null.
// Example: AddINode::Ideal must check for add of zero; in this case it
// returns null instead of doing any graph reshaping.
//
// You cannot modify any old Nodes except for the 'this' pointer.  Due to
// sharing there may be other users of the old Nodes relying on their current
// semantics.  Modifying them will break the other users.
// Example: when reshape "(X+3)+4" into "X+7" you must leave the Node for
// "X+3" unchanged in case it is shared.
//
// If you modify the 'this' pointer's inputs, you should use
// 'set_req'.  If you are making a new Node (either as the new root or
// some new internal piece) you may use 'init_req' to set the initial
// value.  You can make a new Node with either 'new' or 'clone'.  In
// either case, def-use info is correctly maintained.
//
// Example: reshape "(X+3)+4" into "X+7":
//    set_req(1, in(1)->in(1));
//    set_req(2, phase->intcon(7));
//    return this;
// Example: reshape "X*4" into "X<<2"
//    return new LShiftINode(in(1), phase->intcon(2));
//
// You must call 'phase->transform(X)' on any new Nodes X you make, except
// for the returned root node.  Example: reshape "X*31" with "(X<<5)-X".
//    Node *shift=phase->transform(new LShiftINode(in(1),phase->intcon(5)));
//    return new AddINode(shift, in(1));
//
// When making a Node for a constant use 'phase->makecon' or 'phase->intcon'.
// These forms are faster than 'phase->transform(new ConNode())' and Do
// The Right Thing with def-use info.
//
// You cannot bury the 'this' Node inside of a graph reshape.  If the reshaped
// graph uses the 'this' Node it must be the root.  If you want a Node with
// the same Opcode as the 'this' pointer use 'clone'.
//
Node *Node::Ideal(PhaseGVN *phase, bool can_reshape) {
  return nullptr;                  // Default to being Ideal already
}

// Some nodes have specific Ideal subgraph transformations only if they are
// unique users of specific nodes. Such nodes should be put on IGVN worklist
// for the transformations to happen.
bool Node::has_special_unique_user() const {
  assert(outcnt() == 1, "match only for unique out");
  Node* n = unique_out();
  int op  = Opcode();
  if (this->is_Store()) {
    // Condition for back-to-back stores folding.
    return n->Opcode() == op && n->in(MemNode::Memory) == this;
  } else if (this->is_Load() || this->is_DecodeN() || this->is_Phi()) {
    // Condition for removing an unused LoadNode or DecodeNNode from the MemBarAcquire precedence input
    return n->Opcode() == Op_MemBarAcquire;
  } else if (op == Op_AddL) {
    // Condition for convL2I(addL(x,y)) ==> addI(convL2I(x),convL2I(y))
    return n->Opcode() == Op_ConvL2I && n->in(1) == this;
  } else if (op == Op_SubI || op == Op_SubL) {
    // Condition for subI(x,subI(y,z)) ==> subI(addI(x,z),y)
    return n->Opcode() == op && n->in(2) == this;
  } else if (is_If() && (n->is_IfFalse() || n->is_IfTrue())) {
    // See IfProjNode::Identity()
    return true;
  } else if ((is_IfFalse() || is_IfTrue()) && n->is_If()) {
    // See IfNode::fold_compares
    return true;
  } else {
    return false;
  }
};

//--------------------------find_exact_control---------------------------------
// Skip Proj and CatchProj nodes chains. Check for Null and Top.
Node* Node::find_exact_control(Node* ctrl) {
  if (ctrl == nullptr && this->is_Region())
    ctrl = this->as_Region()->is_copy();

  if (ctrl != nullptr && ctrl->is_CatchProj()) {
    if (ctrl->as_CatchProj()->_con == CatchProjNode::fall_through_index)
      ctrl = ctrl->in(0);
    if (ctrl != nullptr && !ctrl->is_top())
      ctrl = ctrl->in(0);
  }

  if (ctrl != nullptr && ctrl->is_Proj())
    ctrl = ctrl->in(0);

  return ctrl;
}

//--------------------------dominates------------------------------------------
// Helper function for MemNode::all_controls_dominate().
// Check if 'this' control node dominates or equal to 'sub' control node.
// We already know that if any path back to Root or Start reaches 'this',
// then all paths so, so this is a simple search for one example,
// not an exhaustive search for a counterexample.
Node::DomResult Node::dominates(Node* sub, Node_List &nlist) {
  assert(this->is_CFG(), "expecting control");
  assert(sub != nullptr && sub->is_CFG(), "expecting control");

  // detect dead cycle without regions
  int iterations_without_region_limit = DominatorSearchLimit;

  Node* orig_sub = sub;
  Node* dom      = this;
  bool  met_dom  = false;
  nlist.clear();

  // Walk 'sub' backward up the chain to 'dom', watching for regions.
  // After seeing 'dom', continue up to Root or Start.
  // If we hit a region (backward split point), it may be a loop head.
  // Keep going through one of the region's inputs.  If we reach the
  // same region again, go through a different input.  Eventually we
  // will either exit through the loop head, or give up.
  // (If we get confused, break out and return a conservative 'false'.)
  while (sub != nullptr) {
    if (sub->is_top()) {
      // Conservative answer for dead code.
      return DomResult::EncounteredDeadCode;
    }
    if (sub == dom) {
      if (nlist.size() == 0) {
        // No Region nodes except loops were visited before and the EntryControl
        // path was taken for loops: it did not walk in a cycle.
        return DomResult::Dominate;
      } else if (met_dom) {
        break;          // already met before: walk in a cycle
      } else {
        // Region nodes were visited. Continue walk up to Start or Root
        // to make sure that it did not walk in a cycle.
        met_dom = true; // first time meet
        iterations_without_region_limit = DominatorSearchLimit; // Reset
     }
    }
    if (sub->is_Start() || sub->is_Root()) {
      // Success if we met 'dom' along a path to Start or Root.
      // We assume there are no alternative paths that avoid 'dom'.
      // (This assumption is up to the caller to ensure!)
      return met_dom ? DomResult::Dominate : DomResult::NotDominate;
    }
    Node* up = sub->in(0);
    // Normalize simple pass-through regions and projections:
    up = sub->find_exact_control(up);
    // If sub == up, we found a self-loop.  Try to push past it.
    if (sub == up && sub->is_Loop()) {
      // Take loop entry path on the way up to 'dom'.
      up = sub->in(1); // in(LoopNode::EntryControl);
    } else if (sub == up && sub->is_Region() && sub->req() == 2) {
      // Take in(1) path on the way up to 'dom' for regions with only one input
      up = sub->in(1);
    } else if (sub == up && sub->is_Region()) {
      // Try both paths for Regions with 2 input paths (it may be a loop head).
      // It could give conservative 'false' answer without information
      // which region's input is the entry path.
      iterations_without_region_limit = DominatorSearchLimit; // Reset

      bool region_was_visited_before = false;
      // Was this Region node visited before?
      // If so, we have reached it because we accidentally took a
      // loop-back edge from 'sub' back into the body of the loop,
      // and worked our way up again to the loop header 'sub'.
      // So, take the first unexplored path on the way up to 'dom'.
      for (int j = nlist.size() - 1; j >= 0; j--) {
        intptr_t ni = (intptr_t)nlist.at(j);
        Node* visited = (Node*)(ni & ~1);
        bool  visited_twice_already = ((ni & 1) != 0);
        if (visited == sub) {
          if (visited_twice_already) {
            // Visited 2 paths, but still stuck in loop body.  Give up.
            return DomResult::NotDominate;
          }
          // The Region node was visited before only once.
          // (We will repush with the low bit set, below.)
          nlist.remove(j);
          // We will find a new edge and re-insert.
          region_was_visited_before = true;
          break;
        }
      }

      // Find an incoming edge which has not been seen yet; walk through it.
      assert(up == sub, "");
      uint skip = region_was_visited_before ? 1 : 0;
      for (uint i = 1; i < sub->req(); i++) {
        Node* in = sub->in(i);
        if (in != nullptr && !in->is_top() && in != sub) {
          if (skip == 0) {
            up = in;
            break;
          }
          --skip;               // skip this nontrivial input
        }
      }

      // Set 0 bit to indicate that both paths were taken.
      nlist.push((Node*)((intptr_t)sub + (region_was_visited_before ? 1 : 0)));
    }

    if (up == sub) {
      break;    // some kind of tight cycle
    }
    if (up == orig_sub && met_dom) {
      // returned back after visiting 'dom'
      break;    // some kind of cycle
    }
    if (--iterations_without_region_limit < 0) {
      break;    // dead cycle
    }
    sub = up;
  }

  // Did not meet Root or Start node in pred. chain.
  return DomResult::NotDominate;
}

//------------------------------remove_dead_region-----------------------------
// This control node is dead.  Follow the subgraph below it making everything
// using it dead as well.  This will happen normally via the usual IterGVN
// worklist but this call is more efficient.  Do not update use-def info
// inside the dead region, just at the borders.
static void kill_dead_code( Node *dead, PhaseIterGVN *igvn ) {
  // Con's are a popular node to re-hit in the hash table again.
  if( dead->is_Con() ) return;

  ResourceMark rm;
  Node_List nstack;
  VectorSet dead_set; // notify uses only once

  Node *top = igvn->C->top();
  nstack.push(dead);
  bool has_irreducible_loop = igvn->C->has_irreducible_loop();

  while (nstack.size() > 0) {
    dead = nstack.pop();
    if (!dead_set.test_set(dead->_idx)) {
      // If dead has any live uses, those are now still attached. Notify them before we lose them.
      igvn->add_users_to_worklist(dead);
    }
    if (dead->Opcode() == Op_SafePoint) {
      dead->as_SafePoint()->disconnect_from_root(igvn);
    }
    if (dead->outcnt() > 0) {
      // Keep dead node on stack until all uses are processed.
      nstack.push(dead);
      // For all Users of the Dead...    ;-)
      for (DUIterator_Last kmin, k = dead->last_outs(kmin); k >= kmin; ) {
        Node* use = dead->last_out(k);
        igvn->hash_delete(use);       // Yank from hash table prior to mod
        if (use->in(0) == dead) {     // Found another dead node
          assert (!use->is_Con(), "Control for Con node should be Root node.");
          use->set_req(0, top);       // Cut dead edge to prevent processing
          nstack.push(use);           // the dead node again.
        } else if (!has_irreducible_loop && // Backedge could be alive in irreducible loop
                   use->is_Loop() && !use->is_Root() &&       // Don't kill Root (RootNode extends LoopNode)
                   use->in(LoopNode::EntryControl) == dead) { // Dead loop if its entry is dead
          use->set_req(LoopNode::EntryControl, top);          // Cut dead edge to prevent processing
          use->set_req(0, top);       // Cut self edge
          nstack.push(use);
        } else {                      // Else found a not-dead user
          // Dead if all inputs are top or null
          bool dead_use = !use->is_Root(); // Keep empty graph alive
          for (uint j = 1; j < use->req(); j++) {
            Node* in = use->in(j);
            if (in == dead) {         // Turn all dead inputs into TOP
              use->set_req(j, top);
            } else if (in != nullptr && !in->is_top()) {
              dead_use = false;
            }
          }
          if (dead_use) {
            if (use->is_Region()) {
              use->set_req(0, top);   // Cut self edge
            }
            nstack.push(use);
          } else {
            igvn->_worklist.push(use);
          }
        }
        // Refresh the iterator, since any number of kills might have happened.
        k = dead->last_outs(kmin);
      }
    } else { // (dead->outcnt() == 0)
      // Done with outputs.
      igvn->hash_delete(dead);
      igvn->_worklist.remove(dead);
      igvn->set_type(dead, Type::TOP);
      // Kill all inputs to the dead guy
      for (uint i=0; i < dead->req(); i++) {
        Node *n = dead->in(i);      // Get input to dead guy
        if (n != nullptr && !n->is_top()) { // Input is valid?
          dead->set_req(i, top);    // Smash input away
          if (n->outcnt() == 0) {   // Input also goes dead?
            if (!n->is_Con())
              nstack.push(n);       // Clear it out as well
          } else if (n->outcnt() == 1 &&
                     n->has_special_unique_user()) {
            igvn->add_users_to_worklist( n );
          } else if (n->outcnt() <= 2 && n->is_Store()) {
            // Push store's uses on worklist to enable folding optimization for
            // store/store and store/load to the same address.
            // The restriction (outcnt() <= 2) is the same as in set_req_X()
            // and remove_globally_dead_node().
            igvn->add_users_to_worklist( n );
          } else if (dead->is_data_proj_of_pure_function(n)) {
            igvn->_worklist.push(n);
          } else {
            BarrierSet::barrier_set()->barrier_set_c2()->enqueue_useful_gc_barrier(igvn, n);
          }
        }
      }
      igvn->C->remove_useless_node(dead);
    } // (dead->outcnt() == 0)
  }   // while (nstack.size() > 0) for outputs
  return;
}

//------------------------------remove_dead_region-----------------------------
bool Node::remove_dead_region(PhaseGVN *phase, bool can_reshape) {
  Node *n = in(0);
  if( !n ) return false;
  // Lost control into this guy?  I.e., it became unreachable?
  // Aggressively kill all unreachable code.
  if (can_reshape && n->is_top()) {
    kill_dead_code(this, phase->is_IterGVN());
    return false; // Node is dead.
  }

  if( n->is_Region() && n->as_Region()->is_copy() ) {
    Node *m = n->nonnull_req();
    set_req(0, m);
    return true;
  }
  return false;
}

//------------------------------hash-------------------------------------------
// Hash function over Nodes.
uint Node::hash() const {
  uint sum = 0;
  for( uint i=0; i<_cnt; i++ )  // Add in all inputs
    sum = (sum<<1)-(uintptr_t)in(i);        // Ignore embedded nulls
  return (sum>>2) + _cnt + Opcode();
}

//------------------------------cmp--------------------------------------------
// Compare special parts of simple Nodes
bool Node::cmp( const Node &n ) const {
  return true;                  // Must be same
}

//------------------------------rematerialize-----------------------------------
// Should we clone rather than spill this instruction?
bool Node::rematerialize() const {
  if ( is_Mach() )
    return this->as_Mach()->rematerialize();
  else
    return (_flags & Flag_rematerialize) != 0;
}

//------------------------------needs_anti_dependence_check---------------------
// Nodes which use memory without consuming it, hence need antidependences.
bool Node::needs_anti_dependence_check() const {
  if (req() < 2 || (_flags & Flag_needs_anti_dependence_check) == 0) {
    return false;
  }
  return in(1)->bottom_type()->has_memory();
}

// Get an integer constant from a ConNode (or CastIINode).
// Return a default value if there is no apparent constant here.
const TypeInt* Node::find_int_type() const {
  if (this->is_Type()) {
    return this->as_Type()->type()->isa_int();
  } else if (this->is_Con()) {
    assert(is_Mach(), "should be ConNode(TypeNode) or else a MachNode");
    return this->bottom_type()->isa_int();
  }
  return nullptr;
}

const TypeInteger* Node::find_integer_type(BasicType bt) const {
  if (this->is_Type()) {
    return this->as_Type()->type()->isa_integer(bt);
  } else if (this->is_Con()) {
    assert(is_Mach(), "should be ConNode(TypeNode) or else a MachNode");
    return this->bottom_type()->isa_integer(bt);
  }
  return nullptr;
}

// Get a pointer constant from a ConstNode.
// Returns the constant if it is a pointer ConstNode
intptr_t Node::get_ptr() const {
  assert( Opcode() == Op_ConP, "" );
  return ((ConPNode*)this)->type()->is_ptr()->get_con();
}

// Get a narrow oop constant from a ConNNode.
intptr_t Node::get_narrowcon() const {
  assert( Opcode() == Op_ConN, "" );
  return ((ConNNode*)this)->type()->is_narrowoop()->get_con();
}

// Get a long constant from a ConNode.
// Return a default value if there is no apparent constant here.
const TypeLong* Node::find_long_type() const {
  if (this->is_Type()) {
    return this->as_Type()->type()->isa_long();
  } else if (this->is_Con()) {
    assert(is_Mach(), "should be ConNode(TypeNode) or else a MachNode");
    return this->bottom_type()->isa_long();
  }
  return nullptr;
}


/**
 * Return a ptr type for nodes which should have it.
 */
const TypePtr* Node::get_ptr_type() const {
  const TypePtr* tp = this->bottom_type()->make_ptr();
#ifdef ASSERT
  if (tp == nullptr) {
    this->dump(1);
    assert((tp != nullptr), "unexpected node type");
  }
#endif
  return tp;
}

// Get a double constant from a ConstNode.
// Returns the constant if it is a double ConstNode
jdouble Node::getd() const {
  assert( Opcode() == Op_ConD, "" );
  return ((ConDNode*)this)->type()->is_double_constant()->getd();
}

// Get a float constant from a ConstNode.
// Returns the constant if it is a float ConstNode
jfloat Node::getf() const {
  assert( Opcode() == Op_ConF, "" );
  return ((ConFNode*)this)->type()->is_float_constant()->getf();
}

// Get a half float constant from a ConstNode.
// Returns the constant if it is a float ConstNode
jshort Node::geth() const {
  assert( Opcode() == Op_ConH, "" );
  return ((ConHNode*)this)->type()->is_half_float_constant()->geth();
}

#ifndef PRODUCT

// Call this from debugger:
Node* old_root() {
  Matcher* matcher = Compile::current()->matcher();
  if (matcher != nullptr) {
    Node* new_root = Compile::current()->root();
    Node* old_root = matcher->find_old_node(new_root);
    if (old_root != nullptr) {
      return old_root;
    }
  }
  tty->print("old_root: not found.\n");
  return nullptr;
}

// BFS traverse all reachable nodes from start, call callback on them
template <typename Callback>
void visit_nodes(Node* start, Callback callback, bool traverse_output, bool only_ctrl) {
  Unique_Mixed_Node_List worklist;
  worklist.add(start);
  for (uint i = 0; i < worklist.size(); i++) {
    Node* n = worklist[i];
    callback(n);
    for (uint i = 0; i < n->len(); i++) {
      if (!only_ctrl || n->is_Region() || (n->Opcode() == Op_Root) || (i == TypeFunc::Control)) {
        // If only_ctrl is set: Add regions, the root node, or control inputs only
        worklist.add(n->in(i));
      }
    }
    if (traverse_output && !only_ctrl) {
      for (uint i = 0; i < n->outcnt(); i++) {
        worklist.add(n->raw_out(i));
      }
    }
  }
}

// BFS traverse from start, return node with idx
static Node* find_node_by_idx(Node* start, uint idx, bool traverse_output, bool only_ctrl) {
  ResourceMark rm;
  Node* result = nullptr;
  auto callback = [&] (Node* n) {
    if (n->_idx == idx) {
      if (result != nullptr) {
        tty->print("find_node_by_idx: " INTPTR_FORMAT " and " INTPTR_FORMAT " both have idx==%d\n",
          (uintptr_t)result, (uintptr_t)n, idx);
      }
      result = n;
    }
  };
  visit_nodes(start, callback, traverse_output, only_ctrl);
  return result;
}

static int node_idx_cmp(const Node** n1, const Node** n2) {
  return (*n1)->_idx - (*n2)->_idx;
}

static void find_nodes_by_name(Node* start, const char* name) {
  ResourceMark rm;
  GrowableArray<const Node*> ns;
  auto callback = [&] (const Node* n) {
    if (StringUtils::is_star_match(name, n->Name())) {
      ns.push(n);
    }
  };
  visit_nodes(start, callback, true, false);
  ns.sort(node_idx_cmp);
  for (int i = 0; i < ns.length(); i++) {
    ns.at(i)->dump();
  }
}

static void find_nodes_by_dump(Node* start, const char* pattern) {
  ResourceMark rm;
  GrowableArray<const Node*> ns;
  auto callback = [&] (const Node* n) {
    stringStream stream;
    n->dump("", false, &stream);
    if (StringUtils::is_star_match(pattern, stream.base())) {
      ns.push(n);
    }
  };
  visit_nodes(start, callback, true, false);
  ns.sort(node_idx_cmp);
  for (int i = 0; i < ns.length(); i++) {
    ns.at(i)->dump();
  }
}

// call from debugger: find node with name pattern in new/current graph
// name can contain "*" in match pattern to match any characters
// the matching is case insensitive
void find_nodes_by_name(const char* name) {
  Node* root = Compile::current()->root();
  find_nodes_by_name(root, name);
}

// call from debugger: find node with name pattern in old graph
// name can contain "*" in match pattern to match any characters
// the matching is case insensitive
void find_old_nodes_by_name(const char* name) {
  Node* root = old_root();
  find_nodes_by_name(root, name);
}

// call from debugger: find node with dump pattern in new/current graph
// can contain "*" in match pattern to match any characters
// the matching is case insensitive
void find_nodes_by_dump(const char* pattern) {
  Node* root = Compile::current()->root();
  find_nodes_by_dump(root, pattern);
}

// call from debugger: find node with name pattern in old graph
// can contain "*" in match pattern to match any characters
// the matching is case insensitive
void find_old_nodes_by_dump(const char* pattern) {
  Node* root = old_root();
  find_nodes_by_dump(root, pattern);
}

// Call this from debugger, search in same graph as n:
Node* find_node(Node* n, const int idx) {
  return n->find(idx);
}

// Call this from debugger, search in new nodes:
Node* find_node(const int idx) {
  return Compile::current()->root()->find(idx);
}

// Call this from debugger, search in old nodes:
Node* find_old_node(const int idx) {
  Node* root = old_root();
  return (root == nullptr) ? nullptr : root->find(idx);
}

// Call this from debugger, search in same graph as n:
Node* find_ctrl(Node* n, const int idx) {
  return n->find_ctrl(idx);
}

// Call this from debugger, search in new nodes:
Node* find_ctrl(const int idx) {
  return Compile::current()->root()->find_ctrl(idx);
}

// Call this from debugger, search in old nodes:
Node* find_old_ctrl(const int idx) {
  Node* root = old_root();
  return (root == nullptr) ? nullptr : root->find_ctrl(idx);
}

//------------------------------find_ctrl--------------------------------------
// Find an ancestor to this node in the control history with given _idx
Node* Node::find_ctrl(int idx) {
  return find(idx, true);
}

//------------------------------find-------------------------------------------
// Tries to find the node with the index |idx| starting from this node. If idx is negative,
// the search also includes forward (out) edges. Returns null if not found.
// If only_ctrl is set, the search will only be done on control nodes. Returns null if
// not found or if the node to be found is not a control node (search will not find it).
Node* Node::find(const int idx, bool only_ctrl) {
  ResourceMark rm;
  return find_node_by_idx(this, abs(idx), (idx < 0), only_ctrl);
}

class PrintBFS {
public:
  PrintBFS(const Node* start, const int max_distance, const Node* target, const char* options, outputStream* st, const frame* fr)
    : _start(start), _max_distance(max_distance), _target(target), _options(options), _output(st), _frame(fr),
    _dcc(this), _info_uid(cmpkey, hashkey) {}

  void run();
private:
  // pipeline steps
  bool configure();
  void collect();
  void select();
  void select_all();
  void select_all_paths();
  void select_shortest_path();
  void sort();
  void print();

  // inputs
  const Node* _start;
  const int _max_distance;
  const Node* _target;
  const char* _options;
  outputStream* _output;
  const frame* _frame;

  // options
  bool _traverse_inputs = false;
  bool _traverse_outputs = false;
  struct Filter {
    bool _control = false;
    bool _memory = false;
    bool _data = false;
    bool _mixed = false;
    bool _other = false;
    bool is_empty() const {
      return !(_control || _memory || _data || _mixed || _other);
    }
    void set_all() {
      _control = true;
      _memory = true;
      _data = true;
      _mixed = true;
      _other = true;
    }
    // Check if the filter accepts the node. Go by the type categories, but also all CFG nodes
    // are considered to have control.
    bool accepts(const Node* n) {
      const Type* t = n->bottom_type();
      return ( _data    &&  t->has_category(Type::Category::Data)                    ) ||
             ( _memory  &&  t->has_category(Type::Category::Memory)                  ) ||
             ( _mixed   &&  t->has_category(Type::Category::Mixed)                   ) ||
             ( _control && (t->has_category(Type::Category::Control) || n->is_CFG()) ) ||
             ( _other   &&  t->has_category(Type::Category::Other)                   );
    }
  };
  Filter _filter_visit;
  Filter _filter_boundary;
  bool _sort_idx = false;
  bool _all_paths = false;
  bool _use_color = false;
  bool _print_blocks = false;
  bool _print_old = false;
  bool _dump_only = false;
  bool _print_igv = false;

  void print_options_help(bool print_examples);
  bool parse_options();

public:
  class DumpConfigColored : public Node::DumpConfig {
  public:
    DumpConfigColored(PrintBFS* bfs) : _bfs(bfs) {};
    virtual void pre_dump(outputStream* st, const Node* n);
    virtual void post_dump(outputStream* st);
  private:
    PrintBFS* _bfs;
  };
private:
  DumpConfigColored _dcc;

  // node info
  static Node* old_node(const Node* n); // mach node -> prior IR node
  void print_node_idx(const Node* n);
  void print_block_id(const Block* b);
  void print_node_block(const Node* n); // _pre_order, head idx, _idom, _dom_depth

  // traversal data structures
  GrowableArray<const Node*> _worklist; // BFS queue
  void maybe_traverse(const Node* src, const Node* dst);

  // node info annotation
  class Info {
  public:
    Info() : Info(nullptr, 0) {};
    Info(const Node* node, int distance)
      : _node(node), _distance_from_start(distance) {};
    const Node* node() const { return _node; };
    int distance() const { return _distance_from_start; };
    int distance_from_target() const { return _distance_from_target; }
    void set_distance_from_target(int d) { _distance_from_target = d; }
    GrowableArray<const Node*> edge_bwd; // pointing toward _start
    bool is_marked() const { return _mark; } // marked to keep during select
    void set_mark() { _mark = true; }
  private:
    const Node* _node;
    int _distance_from_start; // distance from _start
    int _distance_from_target = 0; // distance from _target if _all_paths
    bool _mark = false;
  };
  Dict _info_uid;            // Node -> uid
  GrowableArray<Info> _info; // uid  -> info

  Info* find_info(const Node* n) {
    size_t uid = (size_t)_info_uid[n];
    if (uid == 0) {
      return nullptr;
    }
    return &_info.at((int)uid);
  }

  void make_info(const Node* node, const int distance) {
    assert(find_info(node) == nullptr, "node does not yet have info");
    size_t uid = _info.length() + 1;
    _info_uid.Insert((void*)node, (void*)uid);
    _info.at_put_grow((int)uid, Info(node, distance));
    assert(find_info(node)->node() == node, "stored correct node");
  };

  // filled by sort, printed by print
  GrowableArray<const Node*> _print_list;

  // print header + node table
  void print_header() const;
  void print_node(const Node* n);
};

void PrintBFS::run() {
  if (!configure()) {
    return;
  }
  collect();
  select();
  sort();
  print();
}

// set up configuration for BFS and print
bool PrintBFS::configure() {
  if (_max_distance < 0) {
    _output->print_cr("dump_bfs: max_distance must be non-negative!");
    return false;
  }
  return parse_options();
}

// BFS traverse according to configuration, fill worklist and info
void PrintBFS::collect() {
  maybe_traverse(_start, _start);
  int pos = 0;
  while (pos < _worklist.length()) {
    const Node* n = _worklist.at(pos++); // next node to traverse
    Info* info = find_info(n);
    if (!_filter_visit.accepts(n) && n != _start) {
      continue; // we hit boundary, do not traverse further
    }
    if (n != _start && n->is_Root()) {
      continue; // traversing through root node would lead to unrelated nodes
    }
    if (_traverse_inputs && _max_distance > info->distance()) {
      for (uint i = 0; i < n->req(); i++) {
        maybe_traverse(n, n->in(i));
      }
    }
    if (_traverse_outputs && _max_distance > info->distance()) {
      for (uint i = 0; i < n->outcnt(); i++) {
        maybe_traverse(n, n->raw_out(i));
      }
    }
  }
}

// go through work list, mark those that we want to print
void PrintBFS::select() {
  if (_target == nullptr ) {
    select_all();
  } else {
    if (find_info(_target) == nullptr) {
      _output->print_cr("Could not find target in BFS.");
      return;
    }
    if (_all_paths) {
      select_all_paths();
    } else {
      select_shortest_path();
    }
  }
}

// take all nodes from BFS
void PrintBFS::select_all() {
  for (int i = 0; i < _worklist.length(); i++) {
    const Node* n = _worklist.at(i);
    Info* info = find_info(n);
    info->set_mark();
  }
}

// traverse backward from target, along edges found in BFS
void PrintBFS::select_all_paths() {
  int pos = 0;
  GrowableArray<const Node*> backtrace;
  // start from target
  backtrace.push(_target);
  find_info(_target)->set_mark();
  // traverse backward
  while (pos < backtrace.length()) {
    const Node* n = backtrace.at(pos++);
    Info* info = find_info(n);
    for (int i = 0; i < info->edge_bwd.length(); i++) {
      // all backward edges
      const Node* back = info->edge_bwd.at(i);
      Info* back_info = find_info(back);
      if (!back_info->is_marked()) {
        // not yet found this on way back.
        back_info->set_distance_from_target(info->distance_from_target() + 1);
        if (back_info->distance_from_target() + back_info->distance() <= _max_distance) {
          // total distance is small enough
          back_info->set_mark();
          backtrace.push(back);
        }
      }
    }
  }
}

void PrintBFS::select_shortest_path() {
  const Node* current = _target;
  while (true) {
    Info* info = find_info(current);
    info->set_mark();
    if (current == _start) {
      break;
    }
    // first edge -> leads us one step closer to _start
    current = info->edge_bwd.at(0);
  }
}

// go through worklist in desired order, put the marked ones in print list
void PrintBFS::sort() {
  if (_traverse_inputs && !_traverse_outputs) {
    // reverse order
    for (int i = _worklist.length() - 1; i >= 0; i--) {
      const Node* n = _worklist.at(i);
      Info* info = find_info(n);
      if (info->is_marked()) {
        _print_list.push(n);
      }
    }
  } else {
    // same order as worklist
    for (int i = 0; i < _worklist.length(); i++) {
      const Node* n = _worklist.at(i);
      Info* info = find_info(n);
      if (info->is_marked()) {
        _print_list.push(n);
      }
    }
  }
  if (_sort_idx) {
    _print_list.sort(node_idx_cmp);
  }
}

// go through printlist and print
void PrintBFS::print() {
  if (_print_list.length() > 0 ) {
    print_header();
    for (int i = 0; i < _print_list.length(); i++) {
      const Node* n = _print_list.at(i);
      print_node(n);
    }
    if (_print_igv) {
      Compile* C = Compile::current();
      C->init_igv();
      C->igv_print_graph_to_network(nullptr, _print_list, _frame);
    }
  } else {
    _output->print_cr("No nodes to print.");
  }
}

void PrintBFS::print_options_help(bool print_examples) {
  _output->print_cr("Usage: node->dump_bfs(int max_distance, Node* target, char* options)");
  _output->print_cr("");
  _output->print_cr("Use cases:");
  _output->print_cr("  BFS traversal: no target required");
  _output->print_cr("  shortest path: set target");
  _output->print_cr("  all paths: set target and put 'A' in options");
  _output->print_cr("  detect loop: subcase of all paths, have start==target");
  _output->print_cr("");
  _output->print_cr("Arguments:");
  _output->print_cr("  this/start: staring point of BFS");
  _output->print_cr("  target:");
  _output->print_cr("    if null: simple BFS");
  _output->print_cr("    else: shortest path or all paths between this/start and target");
  _output->print_cr("  options:");
  _output->print_cr("    if null: same as \"cdmox@B\"");
  _output->print_cr("    else: use combination of following characters");
  _output->print_cr("      h: display this help info");
  _output->print_cr("      H: display this help info, with examples");
  _output->print_cr("      +: traverse in-edges (on if neither + nor -)");
  _output->print_cr("      -: traverse out-edges");
  _output->print_cr("      c: visit control nodes");
  _output->print_cr("      d: visit data nodes");
  _output->print_cr("      m: visit memory nodes");
  _output->print_cr("      o: visit other nodes");
  _output->print_cr("      x: visit mixed nodes");
  _output->print_cr("      C: boundary control nodes");
  _output->print_cr("      D: boundary data nodes");
  _output->print_cr("      M: boundary memory nodes");
  _output->print_cr("      O: boundary other nodes");
  _output->print_cr("      X: boundary mixed nodes");
  _output->print_cr("      #: display node category in color (not supported in all terminals)");
  _output->print_cr("      S: sort displayed nodes by node idx");
  _output->print_cr("      A: all paths (not just shortest path to target)");
  _output->print_cr("      @: print old nodes - before matching (if available)");
  _output->print_cr("      B: print scheduling blocks (if available)");
  _output->print_cr("      $: dump only, no header, no other columns");
  _output->print_cr("      !: show nodes on IGV (sent over network stream)");
  _output->print_cr("        (use preferably with dump_bfs(int, Node*, char*, void*, void*, void*)");
  _output->print_cr("         to produce a C2 stack trace along with the graph dump, see examples below)");
  _output->print_cr("");
  _output->print_cr("recursively follow edges to nodes with permitted visit types,");
  _output->print_cr("on the boundary additionally display nodes allowed in boundary types");
  _output->print_cr("Note: the categories can be overlapping. For example a mixed node");
  _output->print_cr("      can contain control and memory output. Some from the other");
  _output->print_cr("      category are also control (Halt, Return, etc).");
  _output->print_cr("");
  _output->print_cr("output columns:");
  _output->print_cr("  dist:  BFS distance to this/start");
  _output->print_cr("  apd:   all paths distance (d_outputart + d_target)");
  _output->print_cr("  block: block identifier, based on _pre_order");
  _output->print_cr("  head:  first node in block");
  _output->print_cr("  idom:  head node of idom block");
  _output->print_cr("  depth: depth of block (_dom_depth)");
  _output->print_cr("  old:   old IR node - before matching");
  _output->print_cr("  dump:  node->dump()");
  _output->print_cr("");
  _output->print_cr("Note: if none of the \"cmdxo\" characters are in the options string");
  _output->print_cr("      then we set all of them.");
  _output->print_cr("      This allows for short strings like \"#\" for colored input traversal");
  _output->print_cr("      or \"-#\" for colored output traversal.");
  if (print_examples) {
    _output->print_cr("");
    _output->print_cr("Examples:");
    _output->print_cr("  if->dump_bfs(10, 0, \"+cxo\")");
    _output->print_cr("    starting at some if node, traverse inputs recursively");
    _output->print_cr("    only along control (mixed and other can also be control)");
    _output->print_cr("  phi->dump_bfs(5, 0, \"-dxo\")");
    _output->print_cr("    starting at phi node, traverse outputs recursively");
    _output->print_cr("    only along data (mixed and other can also have data flow)");
    _output->print_cr("  find_node(385)->dump_bfs(3, 0, \"cdmox+#@B\")");
    _output->print_cr("    find inputs of node 385, up to 3 nodes up (+)");
    _output->print_cr("    traverse all nodes (cdmox), use colors (#)");
    _output->print_cr("    display old nodes and blocks, if they exist");
    _output->print_cr("    useful call to start with");
    _output->print_cr("  find_node(102)->dump_bfs(10, 0, \"dCDMOX-\")");
    _output->print_cr("    find non-data dependencies of a data node");
    _output->print_cr("    follow data node outputs until we find another category");
    _output->print_cr("    node as the boundary");
    _output->print_cr("  x->dump_bfs(10, y, 0)");
    _output->print_cr("    find shortest path from x to y, along any edge or node");
    _output->print_cr("    will not find a path if it is longer than 10");
    _output->print_cr("    useful to find how x and y are related");
    _output->print_cr("  find_node(741)->dump_bfs(20, find_node(746), \"c+\")");
    _output->print_cr("    find shortest control path between two nodes");
    _output->print_cr("  find_node(741)->dump_bfs(8, find_node(746), \"cdmox+A\")");
    _output->print_cr("    find all paths (A) between two nodes of length at most 8");
    _output->print_cr("  find_node(741)->dump_bfs(7, find_node(741), \"c+A\")");
    _output->print_cr("    find all control loops for this node");
    _output->print_cr("  find_node(741)->dump_bfs(7, find_node(741), \"c+A!\", $sp, $fp, $pc)");
    _output->print_cr("    same as above, but printing the resulting subgraph");
    _output->print_cr("    along with a C2 stack trace on IGV");
  }
}

bool PrintBFS::parse_options() {
  if (_options == nullptr) {
    _options = "cdmox@B"; // default options
  }
  size_t len = strlen(_options);
  for (size_t i = 0; i < len; i++) {
    switch (_options[i]) {
      case '+':
        _traverse_inputs = true;
        break;
      case '-':
        _traverse_outputs = true;
        break;
      case 'c':
        _filter_visit._control = true;
        break;
      case 'm':
        _filter_visit._memory = true;
        break;
      case 'd':
        _filter_visit._data = true;
        break;
      case 'x':
        _filter_visit._mixed = true;
        break;
      case 'o':
        _filter_visit._other = true;
        break;
      case 'C':
        _filter_boundary._control = true;
        break;
      case 'M':
        _filter_boundary._memory = true;
        break;
      case 'D':
        _filter_boundary._data = true;
        break;
      case 'X':
        _filter_boundary._mixed = true;
        break;
      case 'O':
        _filter_boundary._other = true;
        break;
      case 'S':
        _sort_idx = true;
        break;
      case 'A':
        _all_paths = true;
        break;
      case '#':
        _use_color = true;
        break;
      case 'B':
        _print_blocks = true;
        break;
      case '@':
        _print_old = true;
        break;
      case '$':
        _dump_only = true;
        break;
      case '!':
        _print_igv = true;
        break;
      case 'h':
        print_options_help(false);
        return false;
       case 'H':
        print_options_help(true);
        return false;
      default:
        _output->print_cr("dump_bfs: Unrecognized option \'%c\'", _options[i]);
        _output->print_cr("for help, run: find_node(0)->dump_bfs(0,0,\"H\")");
        return false;
    }
  }
  if (!_traverse_inputs && !_traverse_outputs) {
    _traverse_inputs = true;
  }
  if (_filter_visit.is_empty()) {
    _filter_visit.set_all();
  }
  Compile* C = Compile::current();
  _print_old &= (C->matcher() != nullptr); // only show old if there are new
  _print_blocks &= (C->cfg() != nullptr); // only show blocks if available
  return true;
}

void PrintBFS::DumpConfigColored::pre_dump(outputStream* st, const Node* n) {
  if (!_bfs->_use_color) {
    return;
  }
  Info* info = _bfs->find_info(n);
  if (info == nullptr || !info->is_marked()) {
    return;
  }

  const Type* t = n->bottom_type();
  switch (t->category()) {
    case Type::Category::Data:
      st->print("\u001b[34m");
      break;
    case Type::Category::Memory:
      st->print("\u001b[32m");
      break;
    case Type::Category::Mixed:
      st->print("\u001b[35m");
      break;
    case Type::Category::Control:
      st->print("\u001b[31m");
      break;
    case Type::Category::Other:
      st->print("\u001b[33m");
      break;
    case Type::Category::Undef:
      n->dump();
      assert(false, "category undef ??");
      break;
    default:
      n->dump();
      assert(false, "not covered");
      break;
  }
}

void PrintBFS::DumpConfigColored::post_dump(outputStream* st) {
  if (!_bfs->_use_color) {
    return;
  }
  st->print("\u001b[0m"); // white
}

Node* PrintBFS::old_node(const Node* n) {
  Compile* C = Compile::current();
  if (C->matcher() == nullptr || !C->node_arena()->contains(n)) {
    return (Node*)nullptr;
  } else {
    return C->matcher()->find_old_node(n);
  }
}

void PrintBFS::print_node_idx(const Node* n) {
  Compile* C = Compile::current();
  char buf[30];
  if (n == nullptr) {
    os::snprintf_checked(buf, sizeof(buf), "_");           // null
  } else if (C->node_arena()->contains(n)) {
    os::snprintf_checked(buf, sizeof(buf), "%d", n->_idx);  // new node
  } else {
    os::snprintf_checked(buf, sizeof(buf), "o%d", n->_idx); // old node
  }
  _output->print("%6s", buf);
}

void PrintBFS::print_block_id(const Block* b) {
  Compile* C = Compile::current();
  char buf[30];
  os::snprintf_checked(buf, sizeof(buf), "B%d", b->_pre_order);
  _output->print("%7s", buf);
}

void PrintBFS::print_node_block(const Node* n) {
  Compile* C = Compile::current();
  Block* b = C->node_arena()->contains(n)
             ? C->cfg()->get_block_for_node(n)
             : nullptr; // guard against old nodes
  if (b == nullptr) {
    _output->print("      _"); // Block
    _output->print("     _");  // head
    _output->print("     _");  // idom
    _output->print("      _"); // depth
  } else {
    print_block_id(b);
    print_node_idx(b->head());
    if (b->_idom) {
      print_node_idx(b->_idom->head());
    } else {
      _output->print("     _"); // idom
    }
    _output->print("%6d ", b->_dom_depth);
  }
}

// filter, and add to worklist, add info, note traversal edges
void PrintBFS::maybe_traverse(const Node* src, const Node* dst) {
  if (dst != nullptr &&
     (_filter_visit.accepts(dst) ||
      _filter_boundary.accepts(dst) ||
      dst == _start)) { // correct category or start?
    if (find_info(dst) == nullptr) {
      // never visited - set up info
      _worklist.push(dst);
      int d = 0;
      if (dst != _start) {
        d = find_info(src)->distance() + 1;
      }
      make_info(dst, d);
    }
    if (src != dst) {
      // traversal edges useful during select
      find_info(dst)->edge_bwd.push(src);
    }
  }
}

void PrintBFS::print_header() const {
  if (_dump_only) {
    return; // no header in dump only mode
  }
  _output->print("dist");                         // distance
  if (_all_paths) {
    _output->print(" apd");                       // all paths distance
  }
  if (_print_blocks) {
    _output->print(" [block  head  idom depth]"); // block
  }
  if (_print_old) {
    _output->print("   old");                     // old node
  }
  _output->print(" dump\n");                      // node dump
  _output->print_cr("---------------------------------------------");
}

void PrintBFS::print_node(const Node* n) {
  if (_dump_only) {
    n->dump("\n", false, _output, &_dcc);
    return;
  }
  _output->print("%4d", find_info(n)->distance());// distance
  if (_all_paths) {
    Info* info = find_info(n);
    int apd = info->distance() + info->distance_from_target();
    _output->print("%4d", apd);                   // all paths distance
  }
  if (_print_blocks) {
    print_node_block(n);                          // block
  }
  if (_print_old) {
    print_node_idx(old_node(n));                  // old node
  }
  _output->print(" ");
  n->dump("\n", false, _output, &_dcc);           // node dump
}

//------------------------------dump_bfs--------------------------------------
// Call this from debugger
// Useful for BFS traversal, shortest path, all path, loop detection, etc
// Designed to be more readable, and provide additional info
// To find all options, run:
//   find_node(0)->dump_bfs(0,0,"H")
void Node::dump_bfs(const int max_distance, Node* target, const char* options) const {
  dump_bfs(max_distance, target, options, tty);
}

// Used to dump to stream.
void Node::dump_bfs(const int max_distance, Node* target, const char* options, outputStream* st, const frame* fr) const {
  PrintBFS bfs(this, max_distance, target, options, st, fr);
  bfs.run();
}

// Call this from debugger, with default arguments
void Node::dump_bfs(const int max_distance) const {
  dump_bfs(max_distance, nullptr, nullptr);
}

// Call this from debugger, with stack handling register arguments for IGV dumps.
// Example: p find_node(741)->dump_bfs(7, find_node(741), "c+A!", $sp, $fp, $pc).
void Node::dump_bfs(const int max_distance, Node* target, const char* options, void* sp, void* fp, void* pc) const {
  frame fr(sp, fp, pc);
  dump_bfs(max_distance, target, options, tty, &fr);
}

// -----------------------------dump_idx---------------------------------------
void Node::dump_idx(bool align, outputStream* st, DumpConfig* dc) const {
  if (dc != nullptr) {
    dc->pre_dump(st, this);
  }
  Compile* C = Compile::current();
  bool is_new = C->node_arena()->contains(this);
  if (align) { // print prefix empty spaces$
    // +1 for leading digit, +1 for "o"
    uint max_width = (C->unique() == 0 ? 0 : static_cast<uint>(log10(static_cast<double>(C->unique())))) + 2;
    // +1 for leading digit, maybe +1 for "o"
    uint width = (_idx == 0 ? 0 : static_cast<uint>(log10(static_cast<double>(_idx)))) + 1 + (is_new ? 0 : 1);
    while (max_width > width) {
      st->print(" ");
      width++;
    }
  }
  if (!is_new) {
    st->print("o");
  }
  st->print("%d", _idx);
  if (dc != nullptr) {
    dc->post_dump(st);
  }
}

// -----------------------------dump_name--------------------------------------
void Node::dump_name(outputStream* st, DumpConfig* dc) const {
  if (dc != nullptr) {
    dc->pre_dump(st, this);
  }
  st->print("%s", Name());
  if (dc != nullptr) {
    dc->post_dump(st);
  }
}

// -----------------------------Name-------------------------------------------
extern const char *NodeClassNames[];
const char *Node::Name() const { return NodeClassNames[Opcode()]; }

static bool is_disconnected(const Node* n) {
  for (uint i = 0; i < n->req(); i++) {
    if (n->in(i) != nullptr)  return false;
  }
  return true;
}

#ifdef ASSERT
void Node::dump_orig(outputStream *st, bool print_key) const {
  Compile* C = Compile::current();
  Node* orig = _debug_orig;
  if (not_a_node(orig)) orig = nullptr;
  if (orig != nullptr && !C->node_arena()->contains(orig)) orig = nullptr;
  if (orig == nullptr) return;
  if (print_key) {
    st->print(" !orig=");
  }
  Node* fast = orig->debug_orig(); // tortoise & hare algorithm to detect loops
  if (not_a_node(fast)) fast = nullptr;
  while (orig != nullptr) {
    bool discon = is_disconnected(orig);  // if discon, print [123] else 123
    if (discon) st->print("[");
    if (!Compile::current()->node_arena()->contains(orig))
      st->print("o");
    st->print("%d", orig->_idx);
    if (discon) st->print("]");
    orig = orig->debug_orig();
    if (not_a_node(orig)) orig = nullptr;
    if (orig != nullptr && !C->node_arena()->contains(orig)) orig = nullptr;
    if (orig != nullptr) st->print(",");
    if (fast != nullptr) {
      // Step fast twice for each single step of orig:
      fast = fast->debug_orig();
      if (not_a_node(fast)) fast = nullptr;
      if (fast != nullptr && fast != orig) {
        fast = fast->debug_orig();
        if (not_a_node(fast)) fast = nullptr;
      }
      if (fast == orig) {
        st->print("...");
        break;
      }
    }
  }
}

void Node::set_debug_orig(Node* orig) {
  _debug_orig = orig;
  if (BreakAtNode == 0)  return;
  if (not_a_node(orig))  orig = nullptr;
  int trip = 10;
  while (orig != nullptr) {
    if (orig->debug_idx() == BreakAtNode || (uintx)orig->_idx == BreakAtNode) {
      tty->print_cr("BreakAtNode: _idx=%d _debug_idx=" UINT64_FORMAT " orig._idx=%d orig._debug_idx=" UINT64_FORMAT,
                    this->_idx, this->debug_idx(), orig->_idx, orig->debug_idx());
      BREAKPOINT;
    }
    orig = orig->debug_orig();
    if (not_a_node(orig))  orig = nullptr;
    if (trip-- <= 0)  break;
  }
}
#endif //ASSERT

//------------------------------dump------------------------------------------
// Dump a Node
void Node::dump(const char* suffix, bool mark, outputStream* st, DumpConfig* dc) const {
  Compile* C = Compile::current();
  bool is_new = C->node_arena()->contains(this);
  C->_in_dump_cnt++;

  // idx mark name ===
  dump_idx(true, st, dc);
  st->print(mark ? " >" : "  ");
  dump_name(st, dc);
  st->print("  === ");

  // Dump the required and precedence inputs
  dump_req(st, dc);
  dump_prec(st, dc);
  // Dump the outputs
  dump_out(st, dc);

  if (is_disconnected(this)) {
#ifdef ASSERT
    st->print("  [" UINT64_FORMAT "]", debug_idx());
    dump_orig(st);
#endif
    st->cr();
    C->_in_dump_cnt--;
    return;                     // don't process dead nodes
  }

  if (C->clone_map().value(_idx) != 0) {
    C->clone_map().dump(_idx, st);
  }
  // Dump node-specific info
  dump_spec(st);
#ifdef ASSERT
  // Dump the non-reset _debug_idx
  if (Verbose && WizardMode) {
    st->print("  [" UINT64_FORMAT "]", debug_idx());
  }
#endif

  const Type *t = bottom_type();

  if (t != nullptr && (t->isa_instptr() || t->isa_instklassptr())) {
    const TypeInstPtr  *toop = t->isa_instptr();
    const TypeInstKlassPtr *tkls = t->isa_instklassptr();
    if (toop) {
      st->print("  Oop:");
    } else if (tkls) {
      st->print("  Klass:");
    }
    t->dump_on(st);
  } else if (t == Type::MEMORY) {
    st->print("  Memory:");
    MemNode::dump_adr_type(this, adr_type(), st);
  } else if (Verbose || WizardMode) {
    st->print("  Type:");
    if (t) {
      t->dump_on(st);
    } else {
      st->print("no type");
    }
  } else if (t->isa_vect() && this->is_MachSpillCopy()) {
    // Dump MachSpillcopy vector type.
    t->dump_on(st);
  }
  if (is_new) {
    DEBUG_ONLY(dump_orig(st));
    Node_Notes* nn = C->node_notes_at(_idx);
    if (nn != nullptr && !nn->is_clear()) {
      if (nn->jvms() != nullptr) {
        st->print(" !jvms:");
        nn->jvms()->dump_spec(st);
      }
    }
  }
  if (suffix) st->print("%s", suffix);
  C->_in_dump_cnt--;
}

// call from debugger: dump node to tty with newline
void Node::dump() const {
  dump("\n");
}

//------------------------------dump_req--------------------------------------
void Node::dump_req(outputStream* st, DumpConfig* dc) const {
  // Dump the required input edges
  for (uint i = 0; i < req(); i++) {    // For all required inputs
    Node* d = in(i);
    if (d == nullptr) {
      st->print("_ ");
    } else if (not_a_node(d)) {
      st->print("not_a_node ");  // uninitialized, sentinel, garbage, etc.
    } else {
      d->dump_idx(false, st, dc);
      st->print(" ");
    }
  }
}


//------------------------------dump_prec-------------------------------------
void Node::dump_prec(outputStream* st, DumpConfig* dc) const {
  // Dump the precedence edges
  int any_prec = 0;
  for (uint i = req(); i < len(); i++) {       // For all precedence inputs
    Node* p = in(i);
    if (p != nullptr) {
      if (!any_prec++) st->print(" |");
      if (not_a_node(p)) { st->print("not_a_node "); continue; }
      p->dump_idx(false, st, dc);
      st->print(" ");
    }
  }
}

//------------------------------dump_out--------------------------------------
void Node::dump_out(outputStream* st, DumpConfig* dc) const {
  // Delimit the output edges
  st->print(" [[ ");
  // Dump the output edges
  for (uint i = 0; i < _outcnt; i++) {    // For all outputs
    Node* u = _out[i];
    if (u == nullptr) {
      st->print("_ ");
    } else if (not_a_node(u)) {
      st->print("not_a_node ");
    } else {
      u->dump_idx(false, st, dc);
      st->print(" ");
    }
  }
  st->print("]] ");
}

//------------------------------dump-------------------------------------------
// call from debugger: dump Node's inputs (or outputs if d negative)
void Node::dump(int d) const {
  dump_bfs(abs(d), nullptr, (d > 0) ? "+$" : "-$");
}

//------------------------------dump_ctrl--------------------------------------
// call from debugger: dump Node's control inputs (or outputs if d negative)
void Node::dump_ctrl(int d) const {
  dump_bfs(abs(d), nullptr, (d > 0) ? "+$c" : "-$c");
}

//-----------------------------dump_compact------------------------------------
void Node::dump_comp() const {
  this->dump_comp("\n");
}

//-----------------------------dump_compact------------------------------------
// Dump a Node in compact representation, i.e., just print its name and index.
// Nodes can specify additional specifics to print in compact representation by
// implementing dump_compact_spec.
void Node::dump_comp(const char* suffix, outputStream *st) const {
  Compile* C = Compile::current();
  C->_in_dump_cnt++;
  st->print("%s(%d)", Name(), _idx);
  this->dump_compact_spec(st);
  if (suffix) {
    st->print("%s", suffix);
  }
  C->_in_dump_cnt--;
}

// VERIFICATION CODE
// Verify all nodes if verify_depth is negative
void Node::verify(int verify_depth, VectorSet& visited, Node_List& worklist) {
  assert(verify_depth != 0, "depth should not be 0");
  Compile* C = Compile::current();
  uint last_index_on_current_depth = worklist.size() - 1;
  verify_depth--; // Visiting the first node on depth 1
  // Only add nodes to worklist if verify_depth is negative (visit all nodes) or greater than 0
  bool add_to_worklist = verify_depth != 0;

  for (uint list_index = 0; list_index < worklist.size(); list_index++) {
    Node* n = worklist[list_index];

    if (n->is_Con() && n->bottom_type() == Type::TOP) {
      if (C->cached_top_node() == nullptr) {
        C->set_cached_top_node((Node*)n);
      }
      assert(C->cached_top_node() == n, "TOP node must be unique");
    }

    uint in_len = n->len();
    for (uint i = 0; i < in_len; i++) {
      Node* x = n->_in[i];
      if (!x || x->is_top()) {
        continue;
      }

      // Verify my input has a def-use edge to me
      // Count use-def edges from n to x
      int cnt = 1;
      for (uint j = 0; j < i; j++) {
        if (n->_in[j] == x) {
          cnt++;
          break;
        }
      }
      if (cnt == 2) {
        // x is already checked as n's previous input, skip its duplicated def-use count checking
        continue;
      }
      for (uint j = i + 1; j < in_len; j++) {
        if (n->_in[j] == x) {
          cnt++;
        }
      }

      // Count def-use edges from x to n
      uint max = x->_outcnt;
      for (uint k = 0; k < max; k++) {
        if (x->_out[k] == n) {
          cnt--;
        }
      }
      assert(cnt == 0, "mismatched def-use edge counts");

      if (add_to_worklist && !visited.test_set(x->_idx)) {
        worklist.push(x);
      }
    }

    if (verify_depth > 0 && list_index == last_index_on_current_depth) {
      // All nodes on this depth were processed and its inputs are on the worklist. Decrement verify_depth and
      // store the current last list index which is the last node in the list with the new depth. All nodes
      // added afterwards will have a new depth again. Stop adding new nodes if depth limit is reached (=0).
      verify_depth--;
      if (verify_depth == 0) {
        add_to_worklist = false;
      }
      last_index_on_current_depth = worklist.size() - 1;
    }
  }
}
#endif // not PRODUCT

//------------------------------Registers--------------------------------------
// Do we Match on this edge index or not?  Generally false for Control
// and true for everything else.  Weird for calls & returns.
uint Node::match_edge(uint idx) const {
  return idx;                   // True for other than index 0 (control)
}

// Register classes are defined for specific machines
const RegMask &Node::out_RegMask() const {
  ShouldNotCallThis();
  return RegMask::Empty;
}

const RegMask &Node::in_RegMask(uint) const {
  ShouldNotCallThis();
  return RegMask::Empty;
}

void Node_Array::grow(uint i) {
  assert(i >= _max, "Should have been checked before, use maybe_grow?");
  assert(_max > 0, "invariant");
  uint old = _max;
  _max = next_power_of_2(i);
  _nodes = (Node**)_a->Arealloc( _nodes, old*sizeof(Node*),_max*sizeof(Node*));
  Copy::zero_to_bytes( &_nodes[old], (_max-old)*sizeof(Node*) );
}

void Node_Array::insert(uint i, Node* n) {
  if (_nodes[_max - 1]) {
    grow(_max);
  }
  Copy::conjoint_words_to_higher((HeapWord*)&_nodes[i], (HeapWord*)&_nodes[i + 1], ((_max - i - 1) * sizeof(Node*)));
  _nodes[i] = n;
}

void Node_Array::remove(uint i) {
  Copy::conjoint_words_to_lower((HeapWord*)&_nodes[i + 1], (HeapWord*)&_nodes[i], ((_max - i - 1) * sizeof(Node*)));
  _nodes[_max - 1] = nullptr;
}

void Node_Array::dump() const {
#ifndef PRODUCT
  for (uint i = 0; i < _max; i++) {
    Node* nn = _nodes[i];
    if (nn != nullptr) {
      tty->print("%5d--> ",i); nn->dump();
    }
  }
#endif
}

//--------------------------is_iteratively_computed------------------------------
// Operation appears to be iteratively computed (such as an induction variable)
// It is possible for this operation to return false for a loop-varying
// value, if it appears (by local graph inspection) to be computed by a simple conditional.
bool Node::is_iteratively_computed() {
  if (ideal_reg()) { // does operation have a result register?
    for (uint i = 1; i < req(); i++) {
      Node* n = in(i);
      if (n != nullptr && n->is_Phi()) {
        for (uint j = 1; j < n->req(); j++) {
          if (n->in(j) == this) {
            return true;
          }
        }
      }
    }
  }
  return false;
}

//--------------------------find_similar------------------------------
// Return a node with opcode "opc" and same inputs as "this" if one can
// be found; Otherwise return null;
Node* Node::find_similar(int opc) {
  if (req() >= 2) {
    Node* def = in(1);
    if (def && def->outcnt() >= 2) {
      for (DUIterator_Fast dmax, i = def->fast_outs(dmax); i < dmax; i++) {
        Node* use = def->fast_out(i);
        if (use != this &&
            use->Opcode() == opc &&
            use->req() == req()) {
          uint j;
          for (j = 0; j < use->req(); j++) {
            if (use->in(j) != in(j)) {
              break;
            }
          }
          if (j == use->req()) {
            return use;
          }
        }
      }
    }
  }
  return nullptr;
}


//--------------------------unique_ctrl_out_or_null-------------------------
// Return the unique control out if only one. Null if none or more than one.
Node* Node::unique_ctrl_out_or_null() const {
  Node* found = nullptr;
  for (uint i = 0; i < outcnt(); i++) {
    Node* use = raw_out(i);
    if (use->is_CFG() && use != this) {
      if (found != nullptr) {
        return nullptr;
      }
      found = use;
    }
  }
  return found;
}

//--------------------------unique_ctrl_out------------------------------
// Return the unique control out. Asserts if none or more than one control out.
Node* Node::unique_ctrl_out() const {
  Node* ctrl = unique_ctrl_out_or_null();
  assert(ctrl != nullptr, "control out is assumed to be unique");
  return ctrl;
}

void Node::ensure_control_or_add_prec(Node* c) {
  if (in(0) == nullptr) {
    set_req(0, c);
  } else if (in(0) != c) {
    add_prec(c);
  }
}

void Node::add_prec_from(Node* n) {
  for (uint i = n->req(); i < n->len(); i++) {
    Node* prec = n->in(i);
    if (prec != nullptr) {
      add_prec(prec);
    }
  }
}

bool Node::is_dead_loop_safe() const {
  if (is_Phi()) {
    return true;
  }
  if (is_Proj() && in(0) == nullptr)  {
    return true;
  }
  if ((_flags & (Flag_is_dead_loop_safe | Flag_is_Con)) != 0) {
    if (!is_Proj()) {
      return true;
    }
    if (in(0)->is_Allocate()) {
      return false;
    }
    // MemNode::can_see_stored_value() peeks through the boxing call
    if (in(0)->is_CallStaticJava() && in(0)->as_CallStaticJava()->is_boxing_method()) {
      return false;
    }
    return true;
  }
  return false;
}

bool Node::is_div_or_mod(BasicType bt) const { return Opcode() == Op_Div(bt) || Opcode() == Op_Mod(bt) ||
                                                      Opcode() == Op_UDiv(bt) || Opcode() == Op_UMod(bt); }

// `maybe_pure_function` is assumed to be the input of `this`. This is a bit redundant,
// but we already have and need maybe_pure_function in all the call sites, so
// it makes it obvious that the `maybe_pure_function` is the same node as in the caller,
// while it takes more thinking to realize that a locally computed in(0) must be equal to
// the local in the caller.
bool Node::is_data_proj_of_pure_function(const Node* maybe_pure_function) const {
  return Opcode() == Op_Proj && as_Proj()->_con == TypeFunc::Parms && maybe_pure_function->is_CallLeafPure();
}

//=============================================================================
//------------------------------yank-------------------------------------------
// Find and remove
void Node_List::yank( Node *n ) {
  uint i;
  for (i = 0; i < _cnt; i++) {
    if (_nodes[i] == n) {
      break;
    }
  }

  if (i < _cnt) {
    _nodes[i] = _nodes[--_cnt];
  }
}

//------------------------------dump-------------------------------------------
void Node_List::dump() const {
#ifndef PRODUCT
  for (uint i = 0; i < _cnt; i++) {
    if (_nodes[i]) {
      tty->print("%5d--> ", i);
      _nodes[i]->dump();
    }
  }
#endif
}

void Node_List::dump_simple() const {
#ifndef PRODUCT
  for (uint i = 0; i < _cnt; i++) {
    if( _nodes[i] ) {
      tty->print(" %d", _nodes[i]->_idx);
    } else {
      tty->print(" null");
    }
  }
#endif
}

//=============================================================================
//------------------------------remove-----------------------------------------
void Unique_Node_List::remove(Node* n) {
  if (_in_worklist.test(n->_idx)) {
    for (uint i = 0; i < size(); i++) {
      if (_nodes[i] == n) {
        map(i, Node_List::pop());
        _in_worklist.remove(n->_idx);
        return;
      }
    }
    ShouldNotReachHere();
  }
}

//-----------------------remove_useless_nodes----------------------------------
// Remove useless nodes from worklist
void Unique_Node_List::remove_useless_nodes(VectorSet &useful) {
  for (uint i = 0; i < size(); ++i) {
    Node *n = at(i);
    assert( n != nullptr, "Did not expect null entries in worklist");
    if (!useful.test(n->_idx)) {
      _in_worklist.remove(n->_idx);
      map(i, Node_List::pop());
      --i;  // Visit popped node
      // If it was last entry, loop terminates since size() was also reduced
    }
  }
}

//=============================================================================
void Node_Stack::grow() {
  size_t old_top = pointer_delta(_inode_top,_inodes,sizeof(INode)); // save _top
  size_t old_max = pointer_delta(_inode_max,_inodes,sizeof(INode));
  size_t max = old_max << 1;             // max * 2
  _inodes = REALLOC_ARENA_ARRAY(_a, INode, _inodes, old_max, max);
  _inode_max = _inodes + max;
  _inode_top = _inodes + old_top;        // restore _top
}

// Node_Stack is used to map nodes.
Node* Node_Stack::find(uint idx) const {
  uint sz = size();
  for (uint i = 0; i < sz; i++) {
    if (idx == index_at(i)) {
      return node_at(i);
    }
  }
  return nullptr;
}

//=============================================================================
uint TypeNode::size_of() const { return sizeof(*this); }
#ifndef PRODUCT
void TypeNode::dump_spec(outputStream *st) const {
  if (!Verbose && !WizardMode) {
    // standard dump does this in Verbose and WizardMode
    st->print(" #"); _type->dump_on(st);
  }
}

void TypeNode::dump_compact_spec(outputStream *st) const {
  st->print("#");
  _type->dump_on(st);
}
#endif
uint TypeNode::hash() const {
  return Node::hash() + _type->hash();
}
bool TypeNode::cmp(const Node& n) const {
  return Type::equals(_type, n.as_Type()->_type);
}
const Type* TypeNode::bottom_type() const { return _type; }
const Type* TypeNode::Value(PhaseGVN* phase) const { return _type; }

//------------------------------ideal_reg--------------------------------------
uint TypeNode::ideal_reg() const {
  return _type->ideal_reg();
}

void TypeNode::make_path_dead(PhaseIterGVN* igvn, PhaseIdealLoop* loop, Node* ctrl_use, uint j, const char* phase_str) {
  Node* c = ctrl_use->in(j);
  if (igvn->type(c) != Type::TOP) {
    igvn->replace_input_of(ctrl_use, j, igvn->C->top());
    create_halt_path(igvn, c, loop, phase_str);
  }
}

// This Type node is dead. It could be because the type that it captures and the type of the node computed from its
// inputs do not intersect anymore. That node has some uses along some control flow paths. Those control flow paths must
// be unreachable as using a dead value makes no sense. For the Type node to capture a narrowed down type, some control
// flow construct must guard the Type node (an If node usually). When the Type node becomes dead, the guard usually
// constant folds and the control flow that leads to the Type node becomes unreachable. There are cases where that
// doesn't happen, however. They are handled here by following uses of the Type node until a CFG or a Phi to find dead
// paths. The dead paths are then replaced by a Halt node.
void TypeNode::make_paths_from_here_dead(PhaseIterGVN* igvn, PhaseIdealLoop* loop, const char* phase_str) {
  Unique_Node_List wq;
  wq.push(this);
  for (uint i = 0; i < wq.size(); ++i) {
    Node* n = wq.at(i);
    for (DUIterator_Fast kmax, k = n->fast_outs(kmax); k < kmax; k++) {
      Node* u = n->fast_out(k);
      if (u->is_CFG()) {
        assert(!u->is_Region(), "Can't reach a Region without going through a Phi");
        make_path_dead(igvn, loop, u, 0, phase_str);
      } else if (u->is_Phi()) {
        Node* r = u->in(0);
        assert(r->is_Region() || r->is_top(), "unexpected Phi's control");
        if (r->is_Region()) {
          for (uint j = 1; j < u->req(); ++j) {
            if (u->in(j) == n && r->in(j) != nullptr) {
              make_path_dead(igvn, loop, r, j, phase_str);
            }
          }
        }
      } else {
        wq.push(u);
      }
    }
  }
}

void TypeNode::create_halt_path(PhaseIterGVN* igvn, Node* c, PhaseIdealLoop* loop, const char* phase_str) const {
  Node* frame = new ParmNode(igvn->C->start(), TypeFunc::FramePtr);
  if (loop == nullptr) {
    igvn->register_new_node_with_optimizer(frame);
  } else {
    loop->register_new_node(frame, igvn->C->start());
  }

  stringStream ss;
  ss.print("dead path discovered by TypeNode during %s", phase_str);

  Node* halt = new HaltNode(c, frame, ss.as_string(igvn->C->comp_arena()));
  if (loop == nullptr) {
    igvn->register_new_node_with_optimizer(halt);
  } else {
    loop->register_control(halt, loop->ltree_root(), c);
  }
  igvn->add_input_to(igvn->C->root(), halt);
}

Node* TypeNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (KillPathsReachableByDeadTypeNode && can_reshape && Value(phase) == Type::TOP) {
    PhaseIterGVN* igvn = phase->is_IterGVN();
    Node* top = igvn->C->top();
    ResourceMark rm;
    make_paths_from_here_dead(igvn, nullptr, "igvn");
    return top;
  }

  return Node::Ideal(phase, can_reshape);
}

