/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_NODE_HPP
#define SHARE_OPTO_NODE_HPP

#include "libadt/vectset.hpp"
#include "opto/compile.hpp"
#include "opto/type.hpp"
#include "utilities/copy.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style


class AbstractLockNode;
class AddNode;
class AddPNode;
class AliasInfo;
class AllocateArrayNode;
class AllocateNode;
class ArrayCopyNode;
class BaseCountedLoopNode;
class BaseCountedLoopEndNode;
class BlackholeNode;
class Block;
class BoolNode;
class BoxLockNode;
class CMoveNode;
class CallDynamicJavaNode;
class CallJavaNode;
class CallLeafNode;
class CallLeafNoFPNode;
class CallNode;
class CallRuntimeNode;
class CallStaticJavaNode;
class CastFFNode;
class CastDDNode;
class CastVVNode;
class CastIINode;
class CastLLNode;
class CastPPNode;
class CatchNode;
class CatchProjNode;
class CheckCastPPNode;
class ClearArrayNode;
class CmpNode;
class CodeBuffer;
class ConstraintCastNode;
class ConNode;
class ConINode;
class ConvertNode;
class CompareAndSwapNode;
class CompareAndExchangeNode;
class CountedLoopNode;
class CountedLoopEndNode;
class DecodeNarrowPtrNode;
class DecodeNNode;
class DecodeNKlassNode;
class EncodeNarrowPtrNode;
class EncodePNode;
class EncodePKlassNode;
class FastLockNode;
class FastUnlockNode;
class HaltNode;
class IfNode;
class IfProjNode;
class IfFalseNode;
class IfTrueNode;
class InitializeNode;
class JVMState;
class JumpNode;
class JumpProjNode;
class LoadNode;
class LoadStoreNode;
class LoadStoreConditionalNode;
class LockNode;
class LongCountedLoopNode;
class LongCountedLoopEndNode;
class LoopNode;
class LShiftNode;
class MachBranchNode;
class MachCallDynamicJavaNode;
class MachCallJavaNode;
class MachCallLeafNode;
class MachCallNode;
class MachCallRuntimeNode;
class MachCallStaticJavaNode;
class MachConstantBaseNode;
class MachConstantNode;
class MachGotoNode;
class MachIfNode;
class MachJumpNode;
class MachNode;
class MachNullCheckNode;
class MachProjNode;
class MachReturnNode;
class MachSafePointNode;
class MachSpillCopyNode;
class MachTempNode;
class MachMergeNode;
class MachMemBarNode;
class Matcher;
class MemBarNode;
class MemBarStoreStoreNode;
class MemNode;
class MergeMemNode;
class MoveNode;
class MulNode;
class MultiNode;
class MultiBranchNode;
class NegNode;
class NegVNode;
class NeverBranchNode;
class Opaque1Node;
class OpaqueLoopInitNode;
class OpaqueLoopStrideNode;
class Opaque4Node;
class OpaqueInitializedAssertionPredicateNode;
class OuterStripMinedLoopNode;
class OuterStripMinedLoopEndNode;
class Node;
class Node_Array;
class Node_List;
class Node_Stack;
class OopMap;
class ParmNode;
class ParsePredicateNode;
class PCTableNode;
class PhaseCCP;
class PhaseGVN;
class PhaseIterGVN;
class PhaseRegAlloc;
class PhaseTransform;
class PhaseValues;
class PhiNode;
class Pipeline;
class PopulateIndexNode;
class ProjNode;
class RangeCheckNode;
class ReductionNode;
class RegMask;
class RegionNode;
class RootNode;
class SafePointNode;
class SafePointScalarObjectNode;
class SafePointScalarMergeNode;
class StartNode;
class State;
class StoreNode;
class SubNode;
class SubTypeCheckNode;
class Type;
class TypeNode;
class UnlockNode;
class UnorderedReductionNode;
class VectorNode;
class LoadVectorNode;
class LoadVectorMaskedNode;
class StoreVectorMaskedNode;
class LoadVectorGatherNode;
class LoadVectorGatherMaskedNode;
class StoreVectorNode;
class StoreVectorScatterNode;
class StoreVectorScatterMaskedNode;
class VerifyVectorAlignmentNode;
class VectorMaskCmpNode;
class VectorUnboxNode;
class VectorSet;
class VectorReinterpretNode;
class ShiftVNode;
class ExpandVNode;
class CompressVNode;
class CompressMNode;
class C2_MacroAssembler;


#ifndef OPTO_DU_ITERATOR_ASSERT
#ifdef ASSERT
#define OPTO_DU_ITERATOR_ASSERT 1
#else
#define OPTO_DU_ITERATOR_ASSERT 0
#endif
#endif //OPTO_DU_ITERATOR_ASSERT

#if OPTO_DU_ITERATOR_ASSERT
class DUIterator;
class DUIterator_Fast;
class DUIterator_Last;
#else
typedef uint   DUIterator;
typedef Node** DUIterator_Fast;
typedef Node** DUIterator_Last;
#endif

typedef ResizeableResourceHashtable<Node*, Node*, AnyObj::RESOURCE_AREA, mtCompiler> OrigToNewHashtable;

// Node Sentinel
#define NodeSentinel (Node*)-1

// Unknown count frequency
#define COUNT_UNKNOWN (-1.0f)

//------------------------------Node-------------------------------------------
// Nodes define actions in the program.  They create values, which have types.
// They are both vertices in a directed graph and program primitives.  Nodes
// are labeled; the label is the "opcode", the primitive function in the lambda
// calculus sense that gives meaning to the Node.  Node inputs are ordered (so
// that "a-b" is different from "b-a").  The inputs to a Node are the inputs to
// the Node's function.  These inputs also define a Type equation for the Node.
// Solving these Type equations amounts to doing dataflow analysis.
// Control and data are uniformly represented in the graph.  Finally, Nodes
// have a unique dense integer index which is used to index into side arrays
// whenever I have phase-specific information.

class Node {
  friend class VMStructs;

  // Lots of restrictions on cloning Nodes
  NONCOPYABLE(Node);

public:
  friend class Compile;
  #if OPTO_DU_ITERATOR_ASSERT
  friend class DUIterator_Common;
  friend class DUIterator;
  friend class DUIterator_Fast;
  friend class DUIterator_Last;
  #endif

  // Because Nodes come and go, I define an Arena of Node structures to pull
  // from.  This should allow fast access to node creation & deletion.  This
  // field is a local cache of a value defined in some "program fragment" for
  // which these Nodes are just a part of.

  inline void* operator new(size_t x) throw() {
    Compile* C = Compile::current();
    Node* n = (Node*)C->node_arena()->AmallocWords(x);
    return (void*)n;
  }

  // Delete is a NOP
  void operator delete( void *ptr ) {}
  // Fancy destructor; eagerly attempt to reclaim Node numberings and storage
  void destruct(PhaseValues* phase);

  // Create a new Node.  Required is the number is of inputs required for
  // semantic correctness.
  Node( uint required );

  // Create a new Node with given input edges.
  // This version requires use of the "edge-count" new.
  // E.g.  new (C,3) FooNode( C, nullptr, left, right );
  Node( Node *n0 );
  Node( Node *n0, Node *n1 );
  Node( Node *n0, Node *n1, Node *n2 );
  Node( Node *n0, Node *n1, Node *n2, Node *n3 );
  Node( Node *n0, Node *n1, Node *n2, Node *n3, Node *n4 );
  Node( Node *n0, Node *n1, Node *n2, Node *n3, Node *n4, Node *n5 );
  Node( Node *n0, Node *n1, Node *n2, Node *n3,
            Node *n4, Node *n5, Node *n6 );

  // Clone an inherited Node given only the base Node type.
  Node* clone() const;

  // Clone a Node, immediately supplying one or two new edges.
  // The first and second arguments, if non-null, replace in(1) and in(2),
  // respectively.
  Node* clone_with_data_edge(Node* in1, Node* in2 = nullptr) const {
    Node* nn = clone();
    if (in1 != nullptr)  nn->set_req(1, in1);
    if (in2 != nullptr)  nn->set_req(2, in2);
    return nn;
  }

private:
  // Shared setup for the above constructors.
  // Handles all interactions with Compile::current.
  // Puts initial values in all Node fields except _idx.
  // Returns the initial value for _idx, which cannot
  // be initialized by assignment.
  inline int Init(int req);

//----------------- input edge handling
protected:
  friend class PhaseCFG;        // Access to address of _in array elements
  Node **_in;                   // Array of use-def references to Nodes
  Node **_out;                  // Array of def-use references to Nodes

  // Input edges are split into two categories.  Required edges are required
  // for semantic correctness; order is important and nulls are allowed.
  // Precedence edges are used to help determine execution order and are
  // added, e.g., for scheduling purposes.  They are unordered and not
  // duplicated; they have no embedded nulls.  Edges from 0 to _cnt-1
  // are required, from _cnt to _max-1 are precedence edges.
  node_idx_t _cnt;              // Total number of required Node inputs.

  node_idx_t _max;              // Actual length of input array.

  // Output edges are an unordered list of def-use edges which exactly
  // correspond to required input edges which point from other nodes
  // to this one.  Thus the count of the output edges is the number of
  // users of this node.
  node_idx_t _outcnt;           // Total number of Node outputs.

  node_idx_t _outmax;           // Actual length of output array.

  // Grow the actual input array to the next larger power-of-2 bigger than len.
  void grow( uint len );
  // Grow the output array to the next larger power-of-2 bigger than len.
  void out_grow( uint len );

 public:
  // Each Node is assigned a unique small/dense number.  This number is used
  // to index into auxiliary arrays of data and bit vectors.
  // The field _idx is declared constant to defend against inadvertent assignments,
  // since it is used by clients as a naked field. However, the field's value can be
  // changed using the set_idx() method.
  //
  // The PhaseRenumberLive phase renumbers nodes based on liveness information.
  // Therefore, it updates the value of the _idx field. The parse-time _idx is
  // preserved in _parse_idx.
  const node_idx_t _idx;
  DEBUG_ONLY(const node_idx_t _parse_idx;)
  // IGV node identifier. Two nodes, possibly in different compilation phases,
  // have the same IGV identifier if (and only if) they are the very same node
  // (same memory address) or one is "derived" from the other (by e.g.
  // renumbering or matching). This identifier makes it possible to follow the
  // entire lifetime of a node in IGV even if its C2 identifier (_idx) changes.
  NOT_PRODUCT(node_idx_t _igv_idx;)

  // Get the (read-only) number of input edges
  uint req() const { return _cnt; }
  uint len() const { return _max; }
  // Get the (read-only) number of output edges
  uint outcnt() const { return _outcnt; }

#if OPTO_DU_ITERATOR_ASSERT
  // Iterate over the out-edges of this node.  Deletions are illegal.
  inline DUIterator outs() const;
  // Use this when the out array might have changed to suppress asserts.
  inline DUIterator& refresh_out_pos(DUIterator& i) const;
  // Does the node have an out at this position?  (Used for iteration.)
  inline bool has_out(DUIterator& i) const;
  inline Node*    out(DUIterator& i) const;
  // Iterate over the out-edges of this node.  All changes are illegal.
  inline DUIterator_Fast fast_outs(DUIterator_Fast& max) const;
  inline Node*    fast_out(DUIterator_Fast& i) const;
  // Iterate over the out-edges of this node, deleting one at a time.
  inline DUIterator_Last last_outs(DUIterator_Last& min) const;
  inline Node*    last_out(DUIterator_Last& i) const;
  // The inline bodies of all these methods are after the iterator definitions.
#else
  // Iterate over the out-edges of this node.  Deletions are illegal.
  // This iteration uses integral indexes, to decouple from array reallocations.
  DUIterator outs() const  { return 0; }
  // Use this when the out array might have changed to suppress asserts.
  DUIterator refresh_out_pos(DUIterator i) const { return i; }

  // Reference to the i'th output Node.  Error if out of bounds.
  Node*    out(DUIterator i) const { assert(i < _outcnt, "oob"); return _out[i]; }
  // Does the node have an out at this position?  (Used for iteration.)
  bool has_out(DUIterator i) const { return i < _outcnt; }

  // Iterate over the out-edges of this node.  All changes are illegal.
  // This iteration uses a pointer internal to the out array.
  DUIterator_Fast fast_outs(DUIterator_Fast& max) const {
    Node** out = _out;
    // Assign a limit pointer to the reference argument:
    max = out + (ptrdiff_t)_outcnt;
    // Return the base pointer:
    return out;
  }
  Node*    fast_out(DUIterator_Fast i) const  { return *i; }
  // Iterate over the out-edges of this node, deleting one at a time.
  // This iteration uses a pointer internal to the out array.
  DUIterator_Last last_outs(DUIterator_Last& min) const {
    Node** out = _out;
    // Assign a limit pointer to the reference argument:
    min = out;
    // Return the pointer to the start of the iteration:
    return out + (ptrdiff_t)_outcnt - 1;
  }
  Node*    last_out(DUIterator_Last i) const  { return *i; }
#endif

  // Reference to the i'th input Node.  Error if out of bounds.
  Node* in(uint i) const { assert(i < _max, "oob: i=%d, _max=%d", i, _max); return _in[i]; }
  // Reference to the i'th input Node.  null if out of bounds.
  Node* lookup(uint i) const { return ((i < _max) ? _in[i] : nullptr); }
  // Reference to the i'th output Node.  Error if out of bounds.
  // Use this accessor sparingly.  We are going trying to use iterators instead.
  Node* raw_out(uint i) const { assert(i < _outcnt,"oob"); return _out[i]; }
  // Return the unique out edge.
  Node* unique_out() const { assert(_outcnt==1,"not unique"); return _out[0]; }
  // Delete out edge at position 'i' by moving last out edge to position 'i'
  void  raw_del_out(uint i) {
    assert(i < _outcnt,"oob");
    assert(_outcnt > 0,"oob");
    #if OPTO_DU_ITERATOR_ASSERT
    // Record that a change happened here.
    debug_only(_last_del = _out[i]; ++_del_tick);
    #endif
    _out[i] = _out[--_outcnt];
    // Smash the old edge so it can't be used accidentally.
    debug_only(_out[_outcnt] = (Node *)(uintptr_t)0xdeadbeef);
  }

#ifdef ASSERT
  bool is_dead() const;
  static bool is_not_dead(const Node* n);
  bool is_reachable_from_root() const;
#endif
  // Check whether node has become unreachable
  bool is_unreachable(PhaseIterGVN &igvn) const;

  // Set a required input edge, also updates corresponding output edge
  void add_req( Node *n ); // Append a NEW required input
  void add_req( Node *n0, Node *n1 ) {
    add_req(n0); add_req(n1); }
  void add_req( Node *n0, Node *n1, Node *n2 ) {
    add_req(n0); add_req(n1); add_req(n2); }
  void add_req_batch( Node* n, uint m ); // Append m NEW required inputs (all n).
  void del_req( uint idx ); // Delete required edge & compact
  void del_req_ordered( uint idx ); // Delete required edge & compact with preserved order
  void ins_req( uint i, Node *n ); // Insert a NEW required input
  void set_req( uint i, Node *n ) {
    assert( is_not_dead(n), "can not use dead node");
    assert( i < _cnt, "oob: i=%d, _cnt=%d", i, _cnt);
    assert( !VerifyHashTableKeys || _hash_lock == 0,
            "remove node from hash table before modifying it");
    Node** p = &_in[i];    // cache this._in, across the del_out call
    if (*p != nullptr)  (*p)->del_out((Node *)this);
    (*p) = n;
    if (n != nullptr)      n->add_out((Node *)this);
    Compile::current()->record_modified_node(this);
  }
  // Light version of set_req() to init inputs after node creation.
  void init_req( uint i, Node *n ) {
    assert( (i == 0 && this == n) ||
            is_not_dead(n), "can not use dead node");
    assert( i < _cnt, "oob");
    assert( !VerifyHashTableKeys || _hash_lock == 0,
            "remove node from hash table before modifying it");
    assert( _in[i] == nullptr, "sanity");
    _in[i] = n;
    if (n != nullptr)      n->add_out((Node *)this);
    Compile::current()->record_modified_node(this);
  }
  // Find first occurrence of n among my edges:
  int find_edge(Node* n);
  int find_prec_edge(Node* n) {
    for (uint i = req(); i < len(); i++) {
      if (_in[i] == n) return i;
      if (_in[i] == nullptr) {
        DEBUG_ONLY( while ((++i) < len()) assert(_in[i] == nullptr, "Gap in prec edges!"); )
        break;
      }
    }
    return -1;
  }
  int replace_edge(Node* old, Node* neww, PhaseGVN* gvn = nullptr);
  int replace_edges_in_range(Node* old, Node* neww, int start, int end, PhaseGVN* gvn);
  // null out all inputs to eliminate incoming Def-Use edges.
  void disconnect_inputs(Compile* C);

  // Quickly, return true if and only if I am Compile::current()->top().
  bool is_top() const {
    assert((this == (Node*) Compile::current()->top()) == (_out == nullptr), "");
    return (_out == nullptr);
  }
  // Reaffirm invariants for is_top.  (Only from Compile::set_cached_top_node.)
  void setup_is_top();

  // Strip away casting.  (It is depth-limited.)
  Node* uncast(bool keep_deps = false) const;
  // Return whether two Nodes are equivalent, after stripping casting.
  bool eqv_uncast(const Node* n, bool keep_deps = false) const {
    return (this->uncast(keep_deps) == n->uncast(keep_deps));
  }

  // Find out of current node that matches opcode.
  Node* find_out_with(int opcode);
  // Return true if the current node has an out that matches opcode.
  bool has_out_with(int opcode);
  // Return true if the current node has an out that matches any of the opcodes.
  bool has_out_with(int opcode1, int opcode2, int opcode3, int opcode4);

private:
  static Node* uncast_helper(const Node* n, bool keep_deps);

  // Add an output edge to the end of the list
  void add_out( Node *n ) {
    if (is_top())  return;
    if( _outcnt == _outmax ) out_grow(_outcnt);
    _out[_outcnt++] = n;
  }
  // Delete an output edge
  void del_out( Node *n ) {
    if (is_top())  return;
    Node** outp = &_out[_outcnt];
    // Find and remove n
    do {
      assert(outp > _out, "Missing Def-Use edge");
    } while (*--outp != n);
    *outp = _out[--_outcnt];
    // Smash the old edge so it can't be used accidentally.
    debug_only(_out[_outcnt] = (Node *)(uintptr_t)0xdeadbeef);
    // Record that a change happened here.
    #if OPTO_DU_ITERATOR_ASSERT
    debug_only(_last_del = n; ++_del_tick);
    #endif
  }
  // Close gap after removing edge.
  void close_prec_gap_at(uint gap) {
    assert(_cnt <= gap && gap < _max, "no valid prec edge");
    uint i = gap;
    Node *last = nullptr;
    for (; i < _max-1; ++i) {
      Node *next = _in[i+1];
      if (next == nullptr) break;
      last = next;
    }
    _in[gap] = last;  // Move last slot to empty one.
    _in[i] = nullptr; // null out last slot.
  }

public:
  // Globally replace this node by a given new node, updating all uses.
  void replace_by(Node* new_node);
  // Globally replace this node by a given new node, updating all uses
  // and cutting input edges of old node.
  void subsume_by(Node* new_node, Compile* c) {
    replace_by(new_node);
    disconnect_inputs(c);
  }
  void set_req_X(uint i, Node *n, PhaseIterGVN *igvn);
  void set_req_X(uint i, Node *n, PhaseGVN *gvn);
  // Find the one non-null required input.  RegionNode only
  Node *nonnull_req() const;
  // Add or remove precedence edges
  void add_prec( Node *n );
  void rm_prec( uint i );

  // Note: prec(i) will not necessarily point to n if edge already exists.
  void set_prec( uint i, Node *n ) {
    assert(i < _max, "oob: i=%d, _max=%d", i, _max);
    assert(is_not_dead(n), "can not use dead node");
    assert(i >= _cnt, "not a precedence edge");
    // Avoid spec violation: duplicated prec edge.
    if (_in[i] == n) return;
    if (n == nullptr || find_prec_edge(n) != -1) {
      rm_prec(i);
      return;
    }
    if (_in[i] != nullptr) _in[i]->del_out((Node *)this);
    _in[i] = n;
    n->add_out((Node *)this);
    Compile::current()->record_modified_node(this);
  }

  // Set this node's index, used by cisc_version to replace current node
  void set_idx(uint new_idx) {
    const node_idx_t* ref = &_idx;
    *(node_idx_t*)ref = new_idx;
  }
  // Swap input edge order.  (Edge indexes i1 and i2 are usually 1 and 2.)
  void swap_edges(uint i1, uint i2) {
    debug_only(uint check_hash = (VerifyHashTableKeys && _hash_lock) ? hash() : NO_HASH);
    // Def-Use info is unchanged
    Node* n1 = in(i1);
    Node* n2 = in(i2);
    _in[i1] = n2;
    _in[i2] = n1;
    // If this node is in the hash table, make sure it doesn't need a rehash.
    assert(check_hash == NO_HASH || check_hash == hash(), "edge swap must preserve hash code");
    // Flip swapped edges flag.
    if (has_swapped_edges()) {
      remove_flag(Node::Flag_has_swapped_edges);
    } else {
      add_flag(Node::Flag_has_swapped_edges);
    }
  }

  // Iterators over input Nodes for a Node X are written as:
  // for( i = 0; i < X.req(); i++ ) ... X[i] ...
  // NOTE: Required edges can contain embedded null pointers.

//----------------- Other Node Properties

  // Generate class IDs for (some) ideal nodes so that it is possible to determine
  // the type of a node using a non-virtual method call (the method is_<Node>() below).
  //
  // A class ID of an ideal node is a set of bits. In a class ID, a single bit determines
  // the type of the node the ID represents; another subset of an ID's bits are reserved
  // for the superclasses of the node represented by the ID.
  //
  // By design, if A is a supertype of B, A.is_B() returns true and B.is_A()
  // returns false. A.is_A() returns true.
  //
  // If two classes, A and B, have the same superclass, a different bit of A's class id
  // is reserved for A's type than for B's type. That bit is specified by the third
  // parameter in the macro DEFINE_CLASS_ID.
  //
  // By convention, classes with deeper hierarchy are declared first. Moreover,
  // classes with the same hierarchy depth are sorted by usage frequency.
  //
  // The query method masks the bits to cut off bits of subclasses and then compares
  // the result with the class id (see the macro DEFINE_CLASS_QUERY below).
  //
  //  Class_MachCall=30, ClassMask_MachCall=31
  // 12               8               4               0
  //  0   0   0   0   0   0   0   0   1   1   1   1   0
  //                                  |   |   |   |
  //                                  |   |   |   Bit_Mach=2
  //                                  |   |   Bit_MachReturn=4
  //                                  |   Bit_MachSafePoint=8
  //                                  Bit_MachCall=16
  //
  //  Class_CountedLoop=56, ClassMask_CountedLoop=63
  // 12               8               4               0
  //  0   0   0   0   0   0   0   1   1   1   0   0   0
  //                              |   |   |
  //                              |   |   Bit_Region=8
  //                              |   Bit_Loop=16
  //                              Bit_CountedLoop=32

  #define DEFINE_CLASS_ID(cl, supcl, subn) \
  Bit_##cl = (Class_##supcl == 0) ? 1 << subn : (Bit_##supcl) << (1 + subn) , \
  Class_##cl = Class_##supcl + Bit_##cl , \
  ClassMask_##cl = ((Bit_##cl << 1) - 1) ,

  // This enum is used only for C2 ideal and mach nodes with is_<node>() methods
  // so that its values fit into 32 bits.
  enum NodeClasses {
    Bit_Node   = 0x00000000,
    Class_Node = 0x00000000,
    ClassMask_Node = 0xFFFFFFFF,

    DEFINE_CLASS_ID(Multi, Node, 0)
      DEFINE_CLASS_ID(SafePoint, Multi, 0)
        DEFINE_CLASS_ID(Call,      SafePoint, 0)
          DEFINE_CLASS_ID(CallJava,         Call, 0)
            DEFINE_CLASS_ID(CallStaticJava,   CallJava, 0)
            DEFINE_CLASS_ID(CallDynamicJava,  CallJava, 1)
          DEFINE_CLASS_ID(CallRuntime,      Call, 1)
            DEFINE_CLASS_ID(CallLeaf,         CallRuntime, 0)
              DEFINE_CLASS_ID(CallLeafNoFP,     CallLeaf, 0)
          DEFINE_CLASS_ID(Allocate,         Call, 2)
            DEFINE_CLASS_ID(AllocateArray,    Allocate, 0)
          DEFINE_CLASS_ID(AbstractLock,     Call, 3)
            DEFINE_CLASS_ID(Lock,             AbstractLock, 0)
            DEFINE_CLASS_ID(Unlock,           AbstractLock, 1)
          DEFINE_CLASS_ID(ArrayCopy,        Call, 4)
      DEFINE_CLASS_ID(MultiBranch, Multi, 1)
        DEFINE_CLASS_ID(PCTable,     MultiBranch, 0)
          DEFINE_CLASS_ID(Catch,       PCTable, 0)
          DEFINE_CLASS_ID(Jump,        PCTable, 1)
        DEFINE_CLASS_ID(If,          MultiBranch, 1)
          DEFINE_CLASS_ID(BaseCountedLoopEnd,     If, 0)
            DEFINE_CLASS_ID(CountedLoopEnd,       BaseCountedLoopEnd, 0)
            DEFINE_CLASS_ID(LongCountedLoopEnd,   BaseCountedLoopEnd, 1)
          DEFINE_CLASS_ID(RangeCheck,             If, 1)
          DEFINE_CLASS_ID(OuterStripMinedLoopEnd, If, 2)
          DEFINE_CLASS_ID(ParsePredicate,         If, 3)
        DEFINE_CLASS_ID(NeverBranch, MultiBranch, 2)
      DEFINE_CLASS_ID(Start,       Multi, 2)
      DEFINE_CLASS_ID(MemBar,      Multi, 3)
        DEFINE_CLASS_ID(Initialize,       MemBar, 0)
        DEFINE_CLASS_ID(MemBarStoreStore, MemBar, 1)

    DEFINE_CLASS_ID(Mach,  Node, 1)
      DEFINE_CLASS_ID(MachReturn, Mach, 0)
        DEFINE_CLASS_ID(MachSafePoint, MachReturn, 0)
          DEFINE_CLASS_ID(MachCall, MachSafePoint, 0)
            DEFINE_CLASS_ID(MachCallJava,         MachCall, 0)
              DEFINE_CLASS_ID(MachCallStaticJava,   MachCallJava, 0)
              DEFINE_CLASS_ID(MachCallDynamicJava,  MachCallJava, 1)
            DEFINE_CLASS_ID(MachCallRuntime,      MachCall, 1)
              DEFINE_CLASS_ID(MachCallLeaf,         MachCallRuntime, 0)
      DEFINE_CLASS_ID(MachBranch, Mach, 1)
        DEFINE_CLASS_ID(MachIf,         MachBranch, 0)
        DEFINE_CLASS_ID(MachGoto,       MachBranch, 1)
        DEFINE_CLASS_ID(MachNullCheck,  MachBranch, 2)
      DEFINE_CLASS_ID(MachSpillCopy,    Mach, 2)
      DEFINE_CLASS_ID(MachTemp,         Mach, 3)
      DEFINE_CLASS_ID(MachConstantBase, Mach, 4)
      DEFINE_CLASS_ID(MachConstant,     Mach, 5)
        DEFINE_CLASS_ID(MachJump,       MachConstant, 0)
      DEFINE_CLASS_ID(MachMerge,        Mach, 6)
      DEFINE_CLASS_ID(MachMemBar,       Mach, 7)

    DEFINE_CLASS_ID(Type,  Node, 2)
      DEFINE_CLASS_ID(Phi,   Type, 0)
      DEFINE_CLASS_ID(ConstraintCast, Type, 1)
        DEFINE_CLASS_ID(CastII, ConstraintCast, 0)
        DEFINE_CLASS_ID(CheckCastPP, ConstraintCast, 1)
        DEFINE_CLASS_ID(CastLL, ConstraintCast, 2)
        DEFINE_CLASS_ID(CastFF, ConstraintCast, 3)
        DEFINE_CLASS_ID(CastDD, ConstraintCast, 4)
        DEFINE_CLASS_ID(CastVV, ConstraintCast, 5)
        DEFINE_CLASS_ID(CastPP, ConstraintCast, 6)
      DEFINE_CLASS_ID(CMove, Type, 3)
      DEFINE_CLASS_ID(SafePointScalarObject, Type, 4)
      DEFINE_CLASS_ID(DecodeNarrowPtr, Type, 5)
        DEFINE_CLASS_ID(DecodeN, DecodeNarrowPtr, 0)
        DEFINE_CLASS_ID(DecodeNKlass, DecodeNarrowPtr, 1)
      DEFINE_CLASS_ID(EncodeNarrowPtr, Type, 6)
        DEFINE_CLASS_ID(EncodeP, EncodeNarrowPtr, 0)
        DEFINE_CLASS_ID(EncodePKlass, EncodeNarrowPtr, 1)
      DEFINE_CLASS_ID(Vector, Type, 7)
        DEFINE_CLASS_ID(VectorMaskCmp, Vector, 0)
        DEFINE_CLASS_ID(VectorUnbox, Vector, 1)
        DEFINE_CLASS_ID(VectorReinterpret, Vector, 2)
        DEFINE_CLASS_ID(ShiftV, Vector, 3)
        DEFINE_CLASS_ID(CompressV, Vector, 4)
        DEFINE_CLASS_ID(ExpandV, Vector, 5)
        DEFINE_CLASS_ID(CompressM, Vector, 6)
        DEFINE_CLASS_ID(Reduction, Vector, 7)
          DEFINE_CLASS_ID(UnorderedReduction, Reduction, 0)
        DEFINE_CLASS_ID(NegV, Vector, 8)
      DEFINE_CLASS_ID(Con, Type, 8)
          DEFINE_CLASS_ID(ConI, Con, 0)
      DEFINE_CLASS_ID(SafePointScalarMerge, Type, 9)
      DEFINE_CLASS_ID(Convert, Type, 10)


    DEFINE_CLASS_ID(Proj,  Node, 3)
      DEFINE_CLASS_ID(CatchProj, Proj, 0)
      DEFINE_CLASS_ID(JumpProj,  Proj, 1)
      DEFINE_CLASS_ID(IfProj,    Proj, 2)
        DEFINE_CLASS_ID(IfTrue,    IfProj, 0)
        DEFINE_CLASS_ID(IfFalse,   IfProj, 1)
      DEFINE_CLASS_ID(Parm,      Proj, 4)
      DEFINE_CLASS_ID(MachProj,  Proj, 5)

    DEFINE_CLASS_ID(Mem, Node, 4)
      DEFINE_CLASS_ID(Load, Mem, 0)
        DEFINE_CLASS_ID(LoadVector,  Load, 0)
          DEFINE_CLASS_ID(LoadVectorGather, LoadVector, 0)
          DEFINE_CLASS_ID(LoadVectorGatherMasked, LoadVector, 1)
          DEFINE_CLASS_ID(LoadVectorMasked, LoadVector, 2)
      DEFINE_CLASS_ID(Store, Mem, 1)
        DEFINE_CLASS_ID(StoreVector, Store, 0)
          DEFINE_CLASS_ID(StoreVectorScatter, StoreVector, 0)
          DEFINE_CLASS_ID(StoreVectorScatterMasked, StoreVector, 1)
          DEFINE_CLASS_ID(StoreVectorMasked, StoreVector, 2)
      DEFINE_CLASS_ID(LoadStore, Mem, 2)
        DEFINE_CLASS_ID(LoadStoreConditional, LoadStore, 0)
          DEFINE_CLASS_ID(CompareAndSwap, LoadStoreConditional, 0)
        DEFINE_CLASS_ID(CompareAndExchangeNode, LoadStore, 1)

    DEFINE_CLASS_ID(Region, Node, 5)
      DEFINE_CLASS_ID(Loop, Region, 0)
        DEFINE_CLASS_ID(Root,                Loop, 0)
        DEFINE_CLASS_ID(BaseCountedLoop,     Loop, 1)
          DEFINE_CLASS_ID(CountedLoop,       BaseCountedLoop, 0)
          DEFINE_CLASS_ID(LongCountedLoop,   BaseCountedLoop, 1)
        DEFINE_CLASS_ID(OuterStripMinedLoop, Loop, 2)

    DEFINE_CLASS_ID(Sub,   Node, 6)
      DEFINE_CLASS_ID(Cmp,   Sub, 0)
        DEFINE_CLASS_ID(FastLock,   Cmp, 0)
        DEFINE_CLASS_ID(FastUnlock, Cmp, 1)
        DEFINE_CLASS_ID(SubTypeCheck,Cmp, 2)

    DEFINE_CLASS_ID(MergeMem, Node, 7)
    DEFINE_CLASS_ID(Bool,     Node, 8)
    DEFINE_CLASS_ID(AddP,     Node, 9)
    DEFINE_CLASS_ID(BoxLock,  Node, 10)
    DEFINE_CLASS_ID(Add,      Node, 11)
    DEFINE_CLASS_ID(Mul,      Node, 12)
    DEFINE_CLASS_ID(ClearArray, Node, 14)
    DEFINE_CLASS_ID(Halt,     Node, 15)
    DEFINE_CLASS_ID(Opaque1,  Node, 16)
      DEFINE_CLASS_ID(OpaqueLoopInit, Opaque1, 0)
      DEFINE_CLASS_ID(OpaqueLoopStride, Opaque1, 1)
    DEFINE_CLASS_ID(Opaque4,  Node, 17)
    DEFINE_CLASS_ID(OpaqueInitializedAssertionPredicate,  Node, 18)
    DEFINE_CLASS_ID(Move,     Node, 19)
    DEFINE_CLASS_ID(LShift,   Node, 20)
    DEFINE_CLASS_ID(Neg,      Node, 21)

    _max_classes  = ClassMask_Neg
  };
  #undef DEFINE_CLASS_ID

  // Flags are sorted by usage frequency.
  enum NodeFlags {
    Flag_is_Copy                     = 1 << 0, // should be first bit to avoid shift
    Flag_rematerialize               = 1 << 1,
    Flag_needs_anti_dependence_check = 1 << 2,
    Flag_is_macro                    = 1 << 3,
    Flag_is_Con                      = 1 << 4,
    Flag_is_cisc_alternate           = 1 << 5,
    Flag_is_dead_loop_safe           = 1 << 6,
    Flag_may_be_short_branch         = 1 << 7,
    Flag_avoid_back_to_back_before   = 1 << 8,
    Flag_avoid_back_to_back_after    = 1 << 9,
    Flag_has_call                    = 1 << 10,
    Flag_has_swapped_edges           = 1 << 11,
    Flag_is_scheduled                = 1 << 12,
    Flag_is_expensive                = 1 << 13,
    Flag_is_predicated_vector        = 1 << 14,
    Flag_for_post_loop_opts_igvn     = 1 << 15,
    Flag_is_removed_by_peephole      = 1 << 16,
    Flag_is_predicated_using_blend   = 1 << 17,
    _last_flag                       = Flag_is_predicated_using_blend
  };

  class PD;

private:
  juint _class_id;
  juint _flags;

  static juint max_flags();

protected:
  // These methods should be called from constructors only.
  void init_class_id(juint c) {
    _class_id = c; // cast out const
  }
  void init_flags(uint fl) {
    assert(fl <= max_flags(), "invalid node flag");
    _flags |= fl;
  }
  void clear_flag(uint fl) {
    assert(fl <= max_flags(), "invalid node flag");
    _flags &= ~fl;
  }

public:
  juint class_id() const { return _class_id; }

  juint flags() const { return _flags; }

  void add_flag(juint fl) { init_flags(fl); }

  void remove_flag(juint fl) { clear_flag(fl); }

  // Return a dense integer opcode number
  virtual int Opcode() const;

  // Virtual inherited Node size
  virtual uint size_of() const;

  // Other interesting Node properties
  #define DEFINE_CLASS_QUERY(type)                           \
  bool is_##type() const {                                   \
    return ((_class_id & ClassMask_##type) == Class_##type); \
  }                                                          \
  type##Node *as_##type() const {                            \
    assert(is_##type(), "invalid node class: %s", Name());   \
    return (type##Node*)this;                                \
  }                                                          \
  type##Node* isa_##type() const {                           \
    return (is_##type()) ? as_##type() : nullptr;            \
  }

  DEFINE_CLASS_QUERY(AbstractLock)
  DEFINE_CLASS_QUERY(Add)
  DEFINE_CLASS_QUERY(AddP)
  DEFINE_CLASS_QUERY(Allocate)
  DEFINE_CLASS_QUERY(AllocateArray)
  DEFINE_CLASS_QUERY(ArrayCopy)
  DEFINE_CLASS_QUERY(BaseCountedLoop)
  DEFINE_CLASS_QUERY(BaseCountedLoopEnd)
  DEFINE_CLASS_QUERY(Bool)
  DEFINE_CLASS_QUERY(BoxLock)
  DEFINE_CLASS_QUERY(Call)
  DEFINE_CLASS_QUERY(CallDynamicJava)
  DEFINE_CLASS_QUERY(CallJava)
  DEFINE_CLASS_QUERY(CallLeaf)
  DEFINE_CLASS_QUERY(CallLeafNoFP)
  DEFINE_CLASS_QUERY(CallRuntime)
  DEFINE_CLASS_QUERY(CallStaticJava)
  DEFINE_CLASS_QUERY(Catch)
  DEFINE_CLASS_QUERY(CatchProj)
  DEFINE_CLASS_QUERY(CheckCastPP)
  DEFINE_CLASS_QUERY(CastII)
  DEFINE_CLASS_QUERY(CastLL)
  DEFINE_CLASS_QUERY(ConI)
  DEFINE_CLASS_QUERY(CastPP)
  DEFINE_CLASS_QUERY(ConstraintCast)
  DEFINE_CLASS_QUERY(ClearArray)
  DEFINE_CLASS_QUERY(CMove)
  DEFINE_CLASS_QUERY(Cmp)
  DEFINE_CLASS_QUERY(Convert)
  DEFINE_CLASS_QUERY(CountedLoop)
  DEFINE_CLASS_QUERY(CountedLoopEnd)
  DEFINE_CLASS_QUERY(DecodeNarrowPtr)
  DEFINE_CLASS_QUERY(DecodeN)
  DEFINE_CLASS_QUERY(DecodeNKlass)
  DEFINE_CLASS_QUERY(EncodeNarrowPtr)
  DEFINE_CLASS_QUERY(EncodeP)
  DEFINE_CLASS_QUERY(EncodePKlass)
  DEFINE_CLASS_QUERY(FastLock)
  DEFINE_CLASS_QUERY(FastUnlock)
  DEFINE_CLASS_QUERY(Halt)
  DEFINE_CLASS_QUERY(If)
  DEFINE_CLASS_QUERY(RangeCheck)
  DEFINE_CLASS_QUERY(IfProj)
  DEFINE_CLASS_QUERY(IfFalse)
  DEFINE_CLASS_QUERY(IfTrue)
  DEFINE_CLASS_QUERY(Initialize)
  DEFINE_CLASS_QUERY(Jump)
  DEFINE_CLASS_QUERY(JumpProj)
  DEFINE_CLASS_QUERY(LongCountedLoop)
  DEFINE_CLASS_QUERY(LongCountedLoopEnd)
  DEFINE_CLASS_QUERY(Load)
  DEFINE_CLASS_QUERY(LoadStore)
  DEFINE_CLASS_QUERY(LoadStoreConditional)
  DEFINE_CLASS_QUERY(Lock)
  DEFINE_CLASS_QUERY(Loop)
  DEFINE_CLASS_QUERY(LShift)
  DEFINE_CLASS_QUERY(Mach)
  DEFINE_CLASS_QUERY(MachBranch)
  DEFINE_CLASS_QUERY(MachCall)
  DEFINE_CLASS_QUERY(MachCallDynamicJava)
  DEFINE_CLASS_QUERY(MachCallJava)
  DEFINE_CLASS_QUERY(MachCallLeaf)
  DEFINE_CLASS_QUERY(MachCallRuntime)
  DEFINE_CLASS_QUERY(MachCallStaticJava)
  DEFINE_CLASS_QUERY(MachConstantBase)
  DEFINE_CLASS_QUERY(MachConstant)
  DEFINE_CLASS_QUERY(MachGoto)
  DEFINE_CLASS_QUERY(MachIf)
  DEFINE_CLASS_QUERY(MachJump)
  DEFINE_CLASS_QUERY(MachNullCheck)
  DEFINE_CLASS_QUERY(MachProj)
  DEFINE_CLASS_QUERY(MachReturn)
  DEFINE_CLASS_QUERY(MachSafePoint)
  DEFINE_CLASS_QUERY(MachSpillCopy)
  DEFINE_CLASS_QUERY(MachTemp)
  DEFINE_CLASS_QUERY(MachMemBar)
  DEFINE_CLASS_QUERY(MachMerge)
  DEFINE_CLASS_QUERY(Mem)
  DEFINE_CLASS_QUERY(MemBar)
  DEFINE_CLASS_QUERY(MemBarStoreStore)
  DEFINE_CLASS_QUERY(MergeMem)
  DEFINE_CLASS_QUERY(Move)
  DEFINE_CLASS_QUERY(Mul)
  DEFINE_CLASS_QUERY(Multi)
  DEFINE_CLASS_QUERY(MultiBranch)
  DEFINE_CLASS_QUERY(Neg)
  DEFINE_CLASS_QUERY(NegV)
  DEFINE_CLASS_QUERY(NeverBranch)
  DEFINE_CLASS_QUERY(Opaque1)
  DEFINE_CLASS_QUERY(Opaque4)
  DEFINE_CLASS_QUERY(OpaqueInitializedAssertionPredicate)
  DEFINE_CLASS_QUERY(OpaqueLoopInit)
  DEFINE_CLASS_QUERY(OpaqueLoopStride)
  DEFINE_CLASS_QUERY(OuterStripMinedLoop)
  DEFINE_CLASS_QUERY(OuterStripMinedLoopEnd)
  DEFINE_CLASS_QUERY(Parm)
  DEFINE_CLASS_QUERY(ParsePredicate)
  DEFINE_CLASS_QUERY(PCTable)
  DEFINE_CLASS_QUERY(Phi)
  DEFINE_CLASS_QUERY(Proj)
  DEFINE_CLASS_QUERY(Reduction)
  DEFINE_CLASS_QUERY(Region)
  DEFINE_CLASS_QUERY(Root)
  DEFINE_CLASS_QUERY(SafePoint)
  DEFINE_CLASS_QUERY(SafePointScalarObject)
  DEFINE_CLASS_QUERY(SafePointScalarMerge)
  DEFINE_CLASS_QUERY(Start)
  DEFINE_CLASS_QUERY(Store)
  DEFINE_CLASS_QUERY(Sub)
  DEFINE_CLASS_QUERY(SubTypeCheck)
  DEFINE_CLASS_QUERY(Type)
  DEFINE_CLASS_QUERY(UnorderedReduction)
  DEFINE_CLASS_QUERY(Vector)
  DEFINE_CLASS_QUERY(VectorMaskCmp)
  DEFINE_CLASS_QUERY(VectorUnbox)
  DEFINE_CLASS_QUERY(VectorReinterpret)
  DEFINE_CLASS_QUERY(CompressV)
  DEFINE_CLASS_QUERY(ExpandV)
  DEFINE_CLASS_QUERY(CompressM)
  DEFINE_CLASS_QUERY(LoadVector)
  DEFINE_CLASS_QUERY(LoadVectorGather)
  DEFINE_CLASS_QUERY(LoadVectorMasked)
  DEFINE_CLASS_QUERY(LoadVectorGatherMasked)
  DEFINE_CLASS_QUERY(StoreVector)
  DEFINE_CLASS_QUERY(StoreVectorScatter)
  DEFINE_CLASS_QUERY(StoreVectorMasked)
  DEFINE_CLASS_QUERY(StoreVectorScatterMasked)
  DEFINE_CLASS_QUERY(ShiftV)
  DEFINE_CLASS_QUERY(Unlock)

  #undef DEFINE_CLASS_QUERY

  // duplicate of is_MachSpillCopy()
  bool is_SpillCopy () const {
    return ((_class_id & ClassMask_MachSpillCopy) == Class_MachSpillCopy);
  }

  bool is_Con () const { return (_flags & Flag_is_Con) != 0; }
  // The data node which is safe to leave in dead loop during IGVN optimization.
  bool is_dead_loop_safe() const;

  // is_Copy() returns copied edge index (0 or 1)
  uint is_Copy() const { return (_flags & Flag_is_Copy); }

  virtual bool is_CFG() const { return false; }

  // If this node is control-dependent on a test, can it be
  // rerouted to a dominating equivalent test?  This is usually
  // true of non-CFG nodes, but can be false for operations which
  // depend for their correct sequencing on more than one test.
  // (In that case, hoisting to a dominating test may silently
  // skip some other important test.)
  virtual bool depends_only_on_test() const { assert(!is_CFG(), ""); return true; };

  // When building basic blocks, I need to have a notion of block beginning
  // Nodes, next block selector Nodes (block enders), and next block
  // projections.  These calls need to work on their machine equivalents.  The
  // Ideal beginning Nodes are RootNode, RegionNode and StartNode.
  bool is_block_start() const {
    if ( is_Region() )
      return this == (const Node*)in(0);
    else
      return is_Start();
  }

  // The Ideal control projection Nodes are IfTrue/IfFalse, JumpProjNode, Root,
  // Goto and Return.  This call also returns the block ending Node.
  virtual const Node *is_block_proj() const;

  // The node is a "macro" node which needs to be expanded before matching
  bool is_macro() const { return (_flags & Flag_is_macro) != 0; }
  // The node is expensive: the best control is set during loop opts
  bool is_expensive() const { return (_flags & Flag_is_expensive) != 0 && in(0) != nullptr; }
  // The node's original edge position is swapped.
  bool has_swapped_edges() const { return (_flags & Flag_has_swapped_edges) != 0; }

  bool is_predicated_vector() const { return (_flags & Flag_is_predicated_vector) != 0; }

  bool is_predicated_using_blend() const { return (_flags & Flag_is_predicated_using_blend) != 0; }

  // Used in lcm to mark nodes that have scheduled
  bool is_scheduled() const { return (_flags & Flag_is_scheduled) != 0; }

  bool for_post_loop_opts_igvn() const { return (_flags & Flag_for_post_loop_opts_igvn) != 0; }

  // Is 'n' possibly a loop entry (i.e. a Parse Predicate projection)?
  static bool may_be_loop_entry(Node* n) {
    return n != nullptr && n->is_IfProj() && n->in(0)->is_ParsePredicate();
  }

//----------------- Optimization

  // Get the worst-case Type output for this Node.
  virtual const class Type *bottom_type() const;

  // If we find a better type for a node, try to record it permanently.
  // Return true if this node actually changed.
  // Be sure to do the hash_delete game in the "rehash" variant.
  void raise_bottom_type(const Type* new_type);

  // Get the address type with which this node uses and/or defs memory,
  // or null if none.  The address type is conservatively wide.
  // Returns non-null for calls, membars, loads, stores, etc.
  // Returns TypePtr::BOTTOM if the node touches memory "broadly".
  virtual const class TypePtr *adr_type() const { return nullptr; }

  // Return an existing node which computes the same function as this node.
  // The optimistic combined algorithm requires this to return a Node which
  // is a small number of steps away (e.g., one of my inputs).
  virtual Node* Identity(PhaseGVN* phase);

  // Return the set of values this Node can take on at runtime.
  virtual const Type* Value(PhaseGVN* phase) const;

  // Return a node which is more "ideal" than the current node.
  // The invariants on this call are subtle.  If in doubt, read the
  // treatise in node.cpp above the default implementation AND TEST WITH
  // -XX:VerifyIterativeGVN=1
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);

  // Some nodes have specific Ideal subgraph transformations only if they are
  // unique users of specific nodes. Such nodes should be put on IGVN worklist
  // for the transformations to happen.
  bool has_special_unique_user() const;

  // Skip Proj and CatchProj nodes chains. Check for Null and Top.
  Node* find_exact_control(Node* ctrl);

  // Check if 'this' node dominates or equal to 'sub'.
  bool dominates(Node* sub, Node_List &nlist);

protected:
  bool remove_dead_region(PhaseGVN *phase, bool can_reshape);
public:

  // See if there is valid pipeline info
  static  const Pipeline *pipeline_class();
  virtual const Pipeline *pipeline() const;

  // Compute the latency from the def to this instruction of the ith input node
  uint latency(uint i);

  // Hash & compare functions, for pessimistic value numbering

  // If the hash function returns the special sentinel value NO_HASH,
  // the node is guaranteed never to compare equal to any other node.
  // If we accidentally generate a hash with value NO_HASH the node
  // won't go into the table and we'll lose a little optimization.
  static const uint NO_HASH = 0;
  virtual uint hash() const;
  virtual bool cmp( const Node &n ) const;

  // Operation appears to be iteratively computed (such as an induction variable)
  // It is possible for this operation to return false for a loop-varying
  // value, if it appears (by local graph inspection) to be computed by a simple conditional.
  bool is_iteratively_computed();

  // Determine if a node is a counted loop induction variable.
  // NOTE: The method is defined in "loopnode.cpp".
  bool is_cloop_ind_var() const;

  // Return a node with opcode "opc" and same inputs as "this" if one can
  // be found; Otherwise return null;
  Node* find_similar(int opc);

  // Return the unique control out if only one. Null if none or more than one.
  Node* unique_ctrl_out_or_null() const;
  // Return the unique control out. Asserts if none or more than one control out.
  Node* unique_ctrl_out() const;

  // Set control or add control as precedence edge
  void ensure_control_or_add_prec(Node* c);

  // Visit boundary uses of the node and apply a callback function for each.
  // Recursively traverse uses, stopping and applying the callback when
  // reaching a boundary node, defined by is_boundary. Note: the function
  // definition appears after the complete type definition of Node_List.
  template <typename Callback, typename Check>
  void visit_uses(Callback callback, Check is_boundary) const;

  // Returns a clone of the current node that's pinned (if the current node is not) for nodes found in array accesses
  // (Load and range check CastII nodes).
  // This is used when an array access is made dependent on 2 or more range checks (range check smearing or Loop Predication).
  virtual Node* pin_array_access_node() const {
    return nullptr;
  }

  //----------------- Code Generation

  // Ideal register class for Matching.  Zero means unmatched instruction
  // (these are cloned instead of converted to machine nodes).
  virtual uint ideal_reg() const;

  static const uint NotAMachineReg;   // must be > max. machine register

  // Do we Match on this edge index or not?  Generally false for Control
  // and true for everything else.  Weird for calls & returns.
  virtual uint match_edge(uint idx) const;

  // Register class output is returned in
  virtual const RegMask &out_RegMask() const;
  // Register class input is expected in
  virtual const RegMask &in_RegMask(uint) const;
  // Should we clone rather than spill this instruction?
  bool rematerialize() const;

  // Return JVM State Object if this Node carries debug info, or null otherwise
  virtual JVMState* jvms() const;

  // Print as assembly
  virtual void format( PhaseRegAlloc *, outputStream* st = tty ) const;
  // Emit bytes using C2_MacroAssembler
  virtual void emit(C2_MacroAssembler *masm, PhaseRegAlloc *ra_) const;
  // Size of instruction in bytes
  virtual uint size(PhaseRegAlloc *ra_) const;

  // Convenience function to extract an integer constant from a node.
  // If it is not an integer constant (either Con, CastII, or Mach),
  // return value_if_unknown.
  jint find_int_con(jint value_if_unknown) const {
    const TypeInt* t = find_int_type();
    return (t != nullptr && t->is_con()) ? t->get_con() : value_if_unknown;
  }
  // Return the constant, knowing it is an integer constant already
  jint get_int() const {
    const TypeInt* t = find_int_type();
    guarantee(t != nullptr, "must be con");
    return t->get_con();
  }
  // Here's where the work is done.  Can produce non-constant int types too.
  const TypeInt* find_int_type() const;
  const TypeInteger* find_integer_type(BasicType bt) const;

  // Same thing for long (and intptr_t, via type.hpp):
  jlong get_long() const {
    const TypeLong* t = find_long_type();
    guarantee(t != nullptr, "must be con");
    return t->get_con();
  }
  jlong find_long_con(jint value_if_unknown) const {
    const TypeLong* t = find_long_type();
    return (t != nullptr && t->is_con()) ? t->get_con() : value_if_unknown;
  }
  const TypeLong* find_long_type() const;

  jlong get_integer_as_long(BasicType bt) const {
    const TypeInteger* t = find_integer_type(bt);
    guarantee(t != nullptr && t->is_con(), "must be con");
    return t->get_con_as_long(bt);
  }
  jlong find_integer_as_long(BasicType bt, jlong value_if_unknown) const {
    const TypeInteger* t = find_integer_type(bt);
    if (t == nullptr || !t->is_con())  return value_if_unknown;
    return t->get_con_as_long(bt);
  }
  const TypePtr* get_ptr_type() const;

  // These guys are called by code generated by ADLC:
  intptr_t get_ptr() const;
  intptr_t get_narrowcon() const;
  jdouble getd() const;
  jfloat getf() const;

  // Nodes which are pinned into basic blocks
  virtual bool pinned() const { return false; }

  // Nodes which use memory without consuming it, hence need antidependences
  // More specifically, needs_anti_dependence_check returns true iff the node
  // (a) does a load, and (b) does not perform a store (except perhaps to a
  // stack slot or some other unaliased location).
  bool needs_anti_dependence_check() const;

  // Return which operand this instruction may cisc-spill. In other words,
  // return operand position that can convert from reg to memory access
  virtual int cisc_operand() const { return AdlcVMDeps::Not_cisc_spillable; }
  bool is_cisc_alternate() const { return (_flags & Flag_is_cisc_alternate) != 0; }

  // Whether this is a memory-writing machine node.
  bool is_memory_writer() const { return is_Mach() && bottom_type()->has_memory(); }

  // Whether this is a memory phi node
  bool is_memory_phi() const { return is_Phi() && bottom_type() == Type::MEMORY; }

//----------------- Printing, etc
#ifndef PRODUCT
 public:
  Node* find(int idx, bool only_ctrl = false); // Search the graph for the given idx.
  Node* find_ctrl(int idx); // Search control ancestors for the given idx.
  void dump_bfs(const int max_distance, Node* target, const char* options, outputStream* st) const;
  void dump_bfs(const int max_distance, Node* target, const char* options) const; // directly to tty
  void dump_bfs(const int max_distance) const; // dump_bfs(max_distance, nullptr, nullptr)
  class DumpConfig {
   public:
    // overridden to implement coloring of node idx
    virtual void pre_dump(outputStream *st, const Node* n) = 0;
    virtual void post_dump(outputStream *st) = 0;
  };
  void dump_idx(bool align = false, outputStream* st = tty, DumpConfig* dc = nullptr) const;
  void dump_name(outputStream* st = tty, DumpConfig* dc = nullptr) const;
  void dump() const; // print node with newline
  void dump(const char* suffix, bool mark = false, outputStream* st = tty, DumpConfig* dc = nullptr) const; // Print this node.
  void dump(int depth) const;        // Print this node, recursively to depth d
  void dump_ctrl(int depth) const;   // Print control nodes, to depth d
  void dump_comp() const;            // Print this node in compact representation.
  // Print this node in compact representation.
  void dump_comp(const char* suffix, outputStream *st = tty) const;
 private:
  virtual void dump_req(outputStream* st = tty, DumpConfig* dc = nullptr) const;    // Print required-edge info
  virtual void dump_prec(outputStream* st = tty, DumpConfig* dc = nullptr) const;   // Print precedence-edge info
  virtual void dump_out(outputStream* st = tty, DumpConfig* dc = nullptr) const;    // Print the output edge info
 public:
  virtual void dump_spec(outputStream *st) const {};      // Print per-node info
  // Print compact per-node info
  virtual void dump_compact_spec(outputStream *st) const { dump_spec(st); }

  static void verify(int verify_depth, VectorSet& visited, Node_List& worklist);

  // This call defines a class-unique string used to identify class instances
  virtual const char *Name() const;

  void dump_format(PhaseRegAlloc *ra) const; // debug access to MachNode::format(...)
  static bool in_dump() { return Compile::current()->_in_dump_cnt > 0; } // check if we are in a dump call
#endif
#ifdef ASSERT
  void verify_construction();
  bool verify_jvms(const JVMState* jvms) const;

  Node* _debug_orig;                   // Original version of this, if any.
  Node*  debug_orig() const            { return _debug_orig; }
  void   set_debug_orig(Node* orig);   // _debug_orig = orig
  void   dump_orig(outputStream *st, bool print_key = true) const;

  uint64_t _debug_idx;                 // Unique value assigned to every node.
  uint64_t debug_idx() const           { return _debug_idx; }
  void set_debug_idx(uint64_t debug_idx) { _debug_idx = debug_idx; }

  int        _hash_lock;               // Barrier to modifications of nodes in the hash table
  void  enter_hash_lock() { ++_hash_lock; assert(_hash_lock < 99, "in too many hash tables?"); }
  void   exit_hash_lock() { --_hash_lock; assert(_hash_lock >= 0, "mispaired hash locks"); }

  static void init_NodeProperty();

  #if OPTO_DU_ITERATOR_ASSERT
  const Node* _last_del;               // The last deleted node.
  uint        _del_tick;               // Bumped when a deletion happens..
  #endif
#endif
};

inline bool not_a_node(const Node* n) {
  if (n == nullptr)                return true;
  if (((intptr_t)n & 1) != 0)      return true;  // uninitialized, etc.
  if (*(address*)n == badAddress)  return true;  // kill by Node::destruct
  return false;
}

//-----------------------------------------------------------------------------
// Iterators over DU info, and associated Node functions.

#if OPTO_DU_ITERATOR_ASSERT

// Common code for assertion checking on DU iterators.
class DUIterator_Common {
#ifdef ASSERT
 protected:
  bool         _vdui;               // cached value of VerifyDUIterators
  const Node*  _node;               // the node containing the _out array
  uint         _outcnt;             // cached node->_outcnt
  uint         _del_tick;           // cached node->_del_tick
  Node*        _last;               // last value produced by the iterator

  void sample(const Node* node);    // used by c'tor to set up for verifies
  void verify(const Node* node, bool at_end_ok = false);
  void verify_resync();
  void reset(const DUIterator_Common& that);

// The VDUI_ONLY macro protects code conditionalized on VerifyDUIterators
  #define I_VDUI_ONLY(i,x) { if ((i)._vdui) { x; } }
#else
  #define I_VDUI_ONLY(i,x) { }
#endif //ASSERT
};

#define VDUI_ONLY(x)     I_VDUI_ONLY(*this, x)

// Default DU iterator.  Allows appends onto the out array.
// Allows deletion from the out array only at the current point.
// Usage:
//  for (DUIterator i = x->outs(); x->has_out(i); i++) {
//    Node* y = x->out(i);
//    ...
//  }
// Compiles in product mode to a unsigned integer index, which indexes
// onto a repeatedly reloaded base pointer of x->_out.  The loop predicate
// also reloads x->_outcnt.  If you delete, you must perform "--i" just
// before continuing the loop.  You must delete only the last-produced
// edge.  You must delete only a single copy of the last-produced edge,
// or else you must delete all copies at once (the first time the edge
// is produced by the iterator).
class DUIterator : public DUIterator_Common {
  friend class Node;

  // This is the index which provides the product-mode behavior.
  // Whatever the product-mode version of the system does to the
  // DUI index is done to this index.  All other fields in
  // this class are used only for assertion checking.
  uint         _idx;

  #ifdef ASSERT
  uint         _refresh_tick;    // Records the refresh activity.

  void sample(const Node* node); // Initialize _refresh_tick etc.
  void verify(const Node* node, bool at_end_ok = false);
  void verify_increment();       // Verify an increment operation.
  void verify_resync();          // Verify that we can back up over a deletion.
  void verify_finish();          // Verify that the loop terminated properly.
  void refresh();                // Resample verification info.
  void reset(const DUIterator& that);  // Resample after assignment.
  #endif

  DUIterator(const Node* node, int dummy_to_avoid_conversion)
    { _idx = 0;                         debug_only(sample(node)); }

 public:
  // initialize to garbage; clear _vdui to disable asserts
  DUIterator()
    { /*initialize to garbage*/         debug_only(_vdui = false); }

  DUIterator(const DUIterator& that)
    { _idx = that._idx;                 debug_only(_vdui = false; reset(that)); }

  void operator++(int dummy_to_specify_postfix_op)
    { _idx++;                           VDUI_ONLY(verify_increment()); }

  void operator--()
    { VDUI_ONLY(verify_resync());       --_idx; }

  ~DUIterator()
    { VDUI_ONLY(verify_finish()); }

  void operator=(const DUIterator& that)
    { _idx = that._idx;                 debug_only(reset(that)); }
};

DUIterator Node::outs() const
  { return DUIterator(this, 0); }
DUIterator& Node::refresh_out_pos(DUIterator& i) const
  { I_VDUI_ONLY(i, i.refresh());        return i; }
bool Node::has_out(DUIterator& i) const
  { I_VDUI_ONLY(i, i.verify(this,true));return i._idx < _outcnt; }
Node*    Node::out(DUIterator& i) const
  { I_VDUI_ONLY(i, i.verify(this));     return debug_only(i._last=) _out[i._idx]; }


// Faster DU iterator.  Disallows insertions into the out array.
// Allows deletion from the out array only at the current point.
// Usage:
//  for (DUIterator_Fast imax, i = x->fast_outs(imax); i < imax; i++) {
//    Node* y = x->fast_out(i);
//    ...
//  }
// Compiles in product mode to raw Node** pointer arithmetic, with
// no reloading of pointers from the original node x.  If you delete,
// you must perform "--i; --imax" just before continuing the loop.
// If you delete multiple copies of the same edge, you must decrement
// imax, but not i, multiple times:  "--i, imax -= num_edges".
class DUIterator_Fast : public DUIterator_Common {
  friend class Node;
  friend class DUIterator_Last;

  // This is the pointer which provides the product-mode behavior.
  // Whatever the product-mode version of the system does to the
  // DUI pointer is done to this pointer.  All other fields in
  // this class are used only for assertion checking.
  Node**       _outp;

  #ifdef ASSERT
  void verify(const Node* node, bool at_end_ok = false);
  void verify_limit();
  void verify_resync();
  void verify_relimit(uint n);
  void reset(const DUIterator_Fast& that);
  #endif

  // Note:  offset must be signed, since -1 is sometimes passed
  DUIterator_Fast(const Node* node, ptrdiff_t offset)
    { _outp = node->_out + offset;      debug_only(sample(node)); }

 public:
  // initialize to garbage; clear _vdui to disable asserts
  DUIterator_Fast()
    { /*initialize to garbage*/         debug_only(_vdui = false); }

  DUIterator_Fast(const DUIterator_Fast& that)
    { _outp = that._outp;               debug_only(_vdui = false; reset(that)); }

  void operator++(int dummy_to_specify_postfix_op)
    { _outp++;                          VDUI_ONLY(verify(_node, true)); }

  void operator--()
    { VDUI_ONLY(verify_resync());       --_outp; }

  void operator-=(uint n)   // applied to the limit only
    { _outp -= n;           VDUI_ONLY(verify_relimit(n));  }

  bool operator<(DUIterator_Fast& limit) {
    I_VDUI_ONLY(*this, this->verify(_node, true));
    I_VDUI_ONLY(limit, limit.verify_limit());
    return _outp < limit._outp;
  }

  void operator=(const DUIterator_Fast& that)
    { _outp = that._outp;               debug_only(reset(that)); }
};

DUIterator_Fast Node::fast_outs(DUIterator_Fast& imax) const {
  // Assign a limit pointer to the reference argument:
  imax = DUIterator_Fast(this, (ptrdiff_t)_outcnt);
  // Return the base pointer:
  return DUIterator_Fast(this, 0);
}
Node* Node::fast_out(DUIterator_Fast& i) const {
  I_VDUI_ONLY(i, i.verify(this));
  return debug_only(i._last=) *i._outp;
}


// Faster DU iterator.  Requires each successive edge to be removed.
// Does not allow insertion of any edges.
// Usage:
//  for (DUIterator_Last imin, i = x->last_outs(imin); i >= imin; i -= num_edges) {
//    Node* y = x->last_out(i);
//    ...
//  }
// Compiles in product mode to raw Node** pointer arithmetic, with
// no reloading of pointers from the original node x.
class DUIterator_Last : private DUIterator_Fast {
  friend class Node;

  #ifdef ASSERT
  void verify(const Node* node, bool at_end_ok = false);
  void verify_limit();
  void verify_step(uint num_edges);
  #endif

  // Note:  offset must be signed, since -1 is sometimes passed
  DUIterator_Last(const Node* node, ptrdiff_t offset)
    : DUIterator_Fast(node, offset) { }

  void operator++(int dummy_to_specify_postfix_op) {} // do not use
  void operator<(int)                              {} // do not use

 public:
  DUIterator_Last() { }
  // initialize to garbage

  DUIterator_Last(const DUIterator_Last& that) = default;

  void operator--()
    { _outp--;              VDUI_ONLY(verify_step(1));  }

  void operator-=(uint n)
    { _outp -= n;           VDUI_ONLY(verify_step(n));  }

  bool operator>=(DUIterator_Last& limit) {
    I_VDUI_ONLY(*this, this->verify(_node, true));
    I_VDUI_ONLY(limit, limit.verify_limit());
    return _outp >= limit._outp;
  }

  DUIterator_Last& operator=(const DUIterator_Last& that) = default;
};

DUIterator_Last Node::last_outs(DUIterator_Last& imin) const {
  // Assign a limit pointer to the reference argument:
  imin = DUIterator_Last(this, 0);
  // Return the initial pointer:
  return DUIterator_Last(this, (ptrdiff_t)_outcnt - 1);
}
Node* Node::last_out(DUIterator_Last& i) const {
  I_VDUI_ONLY(i, i.verify(this));
  return debug_only(i._last=) *i._outp;
}

#endif //OPTO_DU_ITERATOR_ASSERT

#undef I_VDUI_ONLY
#undef VDUI_ONLY

// An Iterator that truly follows the iterator pattern.  Doesn't
// support deletion but could be made to.
//
//   for (SimpleDUIterator i(n); i.has_next(); i.next()) {
//     Node* m = i.get();
//
class SimpleDUIterator : public StackObj {
 private:
  Node* node;
  DUIterator_Fast imax;
  DUIterator_Fast i;
 public:
  SimpleDUIterator(Node* n): node(n), i(n->fast_outs(imax)) {}
  bool has_next() { return i < imax; }
  void next() { i++; }
  Node* get() { return node->fast_out(i); }
};


//-----------------------------------------------------------------------------
// Map dense integer indices to Nodes.  Uses classic doubling-array trick.
// Abstractly provides an infinite array of Node*'s, initialized to null.
// Note that the constructor just zeros things, and since I use Arena
// allocation I do not need a destructor to reclaim storage.
class Node_Array : public AnyObj {
  friend class VMStructs;
protected:
  Arena* _a;                    // Arena to allocate in
  uint   _max;
  Node** _nodes;
  void   grow( uint i );        // Grow array node to fit
public:
  Node_Array(Arena* a, uint max = OptoNodeListSize) : _a(a), _max(max) {
    _nodes = NEW_ARENA_ARRAY(a, Node*, max);
    clear();
  }
  Node_Array() : Node_Array(Thread::current()->resource_area()) {}

  NONCOPYABLE(Node_Array);
  Node_Array& operator=(Node_Array&&) = delete;
  // Allow move constructor for && (eg. capture return of function)
  Node_Array(Node_Array&&) = default;

  Node *operator[] ( uint i ) const // Lookup, or null for not mapped
  { return (i<_max) ? _nodes[i] : (Node*)nullptr; }
  Node* at(uint i) const { assert(i<_max,"oob"); return _nodes[i]; }
  Node** adr() { return _nodes; }
  // Extend the mapping: index i maps to Node *n.
  void map( uint i, Node *n ) { if( i>=_max ) grow(i); _nodes[i] = n; }
  void insert( uint i, Node *n );
  void remove( uint i );        // Remove, preserving order
  // Clear all entries in _nodes to null but keep storage
  void clear() {
    Copy::zero_to_bytes(_nodes, _max * sizeof(Node*));
  }

  uint max() const { return _max; }
  void dump() const;
};

class Node_List : public Node_Array {
  friend class VMStructs;
  uint _cnt;
public:
  Node_List(uint max = OptoNodeListSize) : Node_Array(Thread::current()->resource_area(), max), _cnt(0) {}
  Node_List(Arena *a, uint max = OptoNodeListSize) : Node_Array(a, max), _cnt(0) {}

  NONCOPYABLE(Node_List);
  Node_List& operator=(Node_List&&) = delete;
  // Allow move constructor for && (eg. capture return of function)
  Node_List(Node_List&&) = default;

  bool contains(const Node* n) const {
    for (uint e = 0; e < size(); e++) {
      if (at(e) == n) return true;
    }
    return false;
  }
  void insert( uint i, Node *n ) { Node_Array::insert(i,n); _cnt++; }
  void remove( uint i ) { Node_Array::remove(i); _cnt--; }
  void push( Node *b ) { map(_cnt++,b); }
  void yank( Node *n );         // Find and remove
  Node *pop() { return _nodes[--_cnt]; }
  void clear() { _cnt = 0; Node_Array::clear(); } // retain storage
  void copy(const Node_List& from) {
    if (from._max > _max) {
      grow(from._max);
    }
    _cnt = from._cnt;
    Copy::conjoint_words_to_higher((HeapWord*)&from._nodes[0], (HeapWord*)&_nodes[0], from._max * sizeof(Node*));
  }

  uint size() const { return _cnt; }
  void dump() const;
  void dump_simple() const;
};

// Definition must appear after complete type definition of Node_List
template <typename Callback, typename Check>
void Node::visit_uses(Callback callback, Check is_boundary) const {
  ResourceMark rm;
  VectorSet visited;
  Node_List worklist;

  // The initial worklist consists of the direct uses
  for (DUIterator_Fast kmax, k = fast_outs(kmax); k < kmax; k++) {
    Node* out = fast_out(k);
    if (!visited.test_set(out->_idx)) { worklist.push(out); }
  }

  while (worklist.size() > 0) {
    Node* use = worklist.pop();
    // Apply callback on boundary nodes
    if (is_boundary(use)) {
      callback(use);
    } else {
      // Not a boundary node, continue search
      for (DUIterator_Fast kmax, k = use->fast_outs(kmax); k < kmax; k++) {
        Node* out = use->fast_out(k);
        if (!visited.test_set(out->_idx)) { worklist.push(out); }
      }
    }
  }
}


//------------------------------Unique_Node_List-------------------------------
class Unique_Node_List : public Node_List {
  friend class VMStructs;
  VectorSet _in_worklist;
  uint _clock_index;            // Index in list where to pop from next
public:
  Unique_Node_List() : Node_List(), _clock_index(0) {}
  Unique_Node_List(Arena *a) : Node_List(a), _in_worklist(a), _clock_index(0) {}

  NONCOPYABLE(Unique_Node_List);
  Unique_Node_List& operator=(Unique_Node_List&&) = delete;
  // Allow move constructor for && (eg. capture return of function)
  Unique_Node_List(Unique_Node_List&&) = default;

  void remove( Node *n );
  bool member( Node *n ) { return _in_worklist.test(n->_idx) != 0; }
  VectorSet& member_set(){ return _in_worklist; }

  void push(Node* b) {
    if( !_in_worklist.test_set(b->_idx) )
      Node_List::push(b);
  }
  void push_non_cfg_inputs_of(const Node* node) {
    for (uint i = 1; i < node->req(); i++) {
      Node* input = node->in(i);
      if (input != nullptr && !input->is_CFG()) {
        push(input);
      }
    }
  }

  void push_outputs_of(const Node* node) {
    for (DUIterator_Fast imax, i = node->fast_outs(imax); i < imax; i++) {
      Node* output = node->fast_out(i);
      push(output);
    }
  }

  Node *pop() {
    if( _clock_index >= size() ) _clock_index = 0;
    Node *b = at(_clock_index);
    map( _clock_index, Node_List::pop());
    if (size() != 0) _clock_index++; // Always start from 0
    _in_worklist.remove(b->_idx);
    return b;
  }
  Node *remove(uint i) {
    Node *b = Node_List::at(i);
    _in_worklist.remove(b->_idx);
    map(i,Node_List::pop());
    return b;
  }
  void yank(Node *n) {
    _in_worklist.remove(n->_idx);
    Node_List::yank(n);
  }
  void  clear() {
    _in_worklist.clear();        // Discards storage but grows automatically
    Node_List::clear();
    _clock_index = 0;
  }
  void ensure_empty() {
    assert(size() == 0, "must be empty");
    clear(); // just in case
  }

  // Used after parsing to remove useless nodes before Iterative GVN
  void remove_useless_nodes(VectorSet& useful);

  // If the idx of the Nodes change, we must recompute the VectorSet
  void recompute_idx_set() {
    _in_worklist.clear();
    for (uint i = 0; i < size(); i++) {
      Node* n = at(i);
      _in_worklist.set(n->_idx);
    }
  }

#ifdef ASSERT
  bool is_subset_of(Unique_Node_List& other) {
    for (uint i = 0; i < size(); i++) {
      Node* n = at(i);
      if (!other.member(n)) {
        return false;
      }
    }
    return true;
  }
#endif

  bool contains(const Node* n) const {
    fatal("use faster member() instead");
    return false;
  }

#ifndef PRODUCT
  void print_set() const { _in_worklist.print(); }
#endif
};

// Unique_Mixed_Node_List
// unique: nodes are added only once
// mixed: allow new and old nodes
class Unique_Mixed_Node_List : public ResourceObj {
public:
  Unique_Mixed_Node_List() : _visited_set(cmpkey, hashkey) {}

  void add(Node* node) {
    if (not_a_node(node)) {
      return; // Gracefully handle null, -1, 0xabababab, etc.
    }
    if (_visited_set[node] == nullptr) {
      _visited_set.Insert(node, node);
      _worklist.push(node);
    }
  }

  Node* operator[] (uint i) const {
    return _worklist[i];
  }

  size_t size() {
    return _worklist.size();
  }

private:
  Dict _visited_set;
  Node_List _worklist;
};

// Inline definition of Compile::record_for_igvn must be deferred to this point.
inline void Compile::record_for_igvn(Node* n) {
  _igvn_worklist->push(n);
}

// Inline definition of Compile::remove_for_igvn must be deferred to this point.
inline void Compile::remove_for_igvn(Node* n) {
  _igvn_worklist->remove(n);
}

//------------------------------Node_Stack-------------------------------------
class Node_Stack {
  friend class VMStructs;
protected:
  struct INode {
    Node *node; // Processed node
    uint  indx; // Index of next node's child
  };
  INode *_inode_top; // tos, stack grows up
  INode *_inode_max; // End of _inodes == _inodes + _max
  INode *_inodes;    // Array storage for the stack
  Arena *_a;         // Arena to allocate in
  void grow();
public:
  Node_Stack(int size) {
    size_t max = (size > OptoNodeListSize) ? size : OptoNodeListSize;
    _a = Thread::current()->resource_area();
    _inodes = NEW_ARENA_ARRAY( _a, INode, max );
    _inode_max = _inodes + max;
    _inode_top = _inodes - 1; // stack is empty
  }

  Node_Stack(Arena *a, int size) : _a(a) {
    size_t max = (size > OptoNodeListSize) ? size : OptoNodeListSize;
    _inodes = NEW_ARENA_ARRAY( _a, INode, max );
    _inode_max = _inodes + max;
    _inode_top = _inodes - 1; // stack is empty
  }

  void pop() {
    assert(_inode_top >= _inodes, "node stack underflow");
    --_inode_top;
  }
  void push(Node *n, uint i) {
    ++_inode_top;
    if (_inode_top >= _inode_max) grow();
    INode *top = _inode_top; // optimization
    top->node = n;
    top->indx = i;
  }
  Node *node() const {
    return _inode_top->node;
  }
  Node* node_at(uint i) const {
    assert(_inodes + i <= _inode_top, "in range");
    return _inodes[i].node;
  }
  uint index() const {
    return _inode_top->indx;
  }
  uint index_at(uint i) const {
    assert(_inodes + i <= _inode_top, "in range");
    return _inodes[i].indx;
  }
  void set_node(Node *n) {
    _inode_top->node = n;
  }
  void set_index(uint i) {
    _inode_top->indx = i;
  }
  uint size_max() const { return (uint)pointer_delta(_inode_max, _inodes,  sizeof(INode)); } // Max size
  uint size() const { return (uint)pointer_delta((_inode_top+1), _inodes,  sizeof(INode)); } // Current size
  bool is_nonempty() const { return (_inode_top >= _inodes); }
  bool is_empty() const { return (_inode_top < _inodes); }
  void clear() { _inode_top = _inodes - 1; } // retain storage

  // Node_Stack is used to map nodes.
  Node* find(uint idx) const;

  NONCOPYABLE(Node_Stack);
};


//-----------------------------Node_Notes--------------------------------------
// Debugging or profiling annotations loosely and sparsely associated
// with some nodes.  See Compile::node_notes_at for the accessor.
class Node_Notes {
  friend class VMStructs;
  JVMState* _jvms;

public:
  Node_Notes(JVMState* jvms = nullptr) {
    _jvms = jvms;
  }

  JVMState* jvms()            { return _jvms; }
  void  set_jvms(JVMState* x) {        _jvms = x; }

  // True if there is nothing here.
  bool is_clear() {
    return (_jvms == nullptr);
  }

  // Make there be nothing here.
  void clear() {
    _jvms = nullptr;
  }

  // Make a new, clean node notes.
  static Node_Notes* make(Compile* C) {
    Node_Notes* nn = NEW_ARENA_ARRAY(C->comp_arena(), Node_Notes, 1);
    nn->clear();
    return nn;
  }

  Node_Notes* clone(Compile* C) {
    Node_Notes* nn = NEW_ARENA_ARRAY(C->comp_arena(), Node_Notes, 1);
    (*nn) = (*this);
    return nn;
  }

  // Absorb any information from source.
  bool update_from(Node_Notes* source) {
    bool changed = false;
    if (source != nullptr) {
      if (source->jvms() != nullptr) {
        set_jvms(source->jvms());
        changed = true;
      }
    }
    return changed;
  }
};

// Inlined accessors for Compile::node_nodes that require the preceding class:
inline Node_Notes*
Compile::locate_node_notes(GrowableArray<Node_Notes*>* arr,
                           int idx, bool can_grow) {
  assert(idx >= 0, "oob");
  int block_idx = (idx >> _log2_node_notes_block_size);
  int grow_by = (block_idx - (arr == nullptr? 0: arr->length()));
  if (grow_by >= 0) {
    if (!can_grow) return nullptr;
    grow_node_notes(arr, grow_by + 1);
  }
  if (arr == nullptr) return nullptr;
  // (Every element of arr is a sub-array of length _node_notes_block_size.)
  return arr->at(block_idx) + (idx & (_node_notes_block_size-1));
}

inline bool
Compile::set_node_notes_at(int idx, Node_Notes* value) {
  if (value == nullptr || value->is_clear())
    return false;  // nothing to write => write nothing
  Node_Notes* loc = locate_node_notes(_node_note_array, idx, true);
  assert(loc != nullptr, "");
  return loc->update_from(value);
}


//------------------------------TypeNode---------------------------------------
// Node with a Type constant.
class TypeNode : public Node {
protected:
  virtual uint hash() const;    // Check the type
  virtual bool cmp( const Node &n ) const;
  virtual uint size_of() const; // Size is bigger
  const Type* const _type;
public:
  void set_type(const Type* t) {
    assert(t != nullptr, "sanity");
    debug_only(uint check_hash = (VerifyHashTableKeys && _hash_lock) ? hash() : NO_HASH);
    *(const Type**)&_type = t;   // cast away const-ness
    // If this node is in the hash table, make sure it doesn't need a rehash.
    assert(check_hash == NO_HASH || check_hash == hash(), "type change must preserve hash code");
  }
  const Type* type() const { assert(_type != nullptr, "sanity"); return _type; };
  TypeNode( const Type *t, uint required ) : Node(required), _type(t) {
    init_class_id(Class_Type);
  }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type *bottom_type() const;
  virtual       uint  ideal_reg() const;
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
  virtual void dump_compact_spec(outputStream *st) const;
#endif
};

#include "opto/opcodes.hpp"

#define Op_IL(op) \
  inline int Op_ ## op(BasicType bt) { \
  assert(bt == T_INT || bt == T_LONG, "only for int or longs"); \
  if (bt == T_INT) { \
    return Op_## op ## I; \
  } \
  return Op_## op ## L; \
}

Op_IL(Add)
Op_IL(Sub)
Op_IL(Mul)
Op_IL(URShift)
Op_IL(LShift)
Op_IL(Xor)
Op_IL(Cmp)

inline int Op_ConIL(BasicType bt) {
  assert(bt == T_INT || bt == T_LONG, "only for int or longs");
  if (bt == T_INT) {
    return Op_ConI;
  }
  return Op_ConL;
}

inline int Op_Cmp_unsigned(BasicType bt) {
  assert(bt == T_INT || bt == T_LONG, "only for int or longs");
  if (bt == T_INT) {
    return Op_CmpU;
  }
  return Op_CmpUL;
}

inline int Op_Cast(BasicType bt) {
  assert(bt == T_INT || bt == T_LONG, "only for int or longs");
  if (bt == T_INT) {
    return Op_CastII;
  }
  return Op_CastLL;
}

#endif // SHARE_OPTO_NODE_HPP
