/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

//
// Adaptation for C2 of the escape analysis algorithm described in:
//
// [Choi99] Jong-Deok Shoi, Manish Gupta, Mauricio Seffano,
//          Vugranam C. Sreedhar, Sam Midkiff,
//          "Escape Analysis for Java", Procedings of ACM SIGPLAN
//          OOPSLA  Conference, November 1, 1999
//
// The flow-insensitive analysis described in the paper has been implemented.
//
// The analysis requires construction of a "connection graph" (CG) for
// the method being analyzed.  The nodes of the connection graph are:
//
//     -  Java objects (JO)
//     -  Local variables (LV)
//     -  Fields of an object (OF),  these also include array elements
//
// The CG contains 3 types of edges:
//
//   -  PointsTo  (-P>)    {LV, OF} to JO
//   -  Deferred  (-D>)    from {LV, OF} to {LV, OF}
//   -  Field     (-F>)    from JO to OF
//
// The following  utility functions is used by the algorithm:
//
//   PointsTo(n) - n is any CG node, it returns the set of JO that n could
//                 point to.
//
// The algorithm describes how to construct the connection graph
// in the following 4 cases:
//
//          Case                  Edges Created
//
// (1)   p   = new T()              LV -P> JO
// (2)   p   = q                    LV -D> LV
// (3)   p.f = q                    JO -F> OF,  OF -D> LV
// (4)   p   = q.f                  JO -F> OF,  LV -D> OF
//
// In all these cases, p and q are local variables.  For static field
// references, we can construct a local variable containing a reference
// to the static memory.
//
// C2 does not have local variables.  However for the purposes of constructing
// the connection graph, the following IR nodes are treated as local variables:
//     Phi    (pointer values)
//     LoadP
//     Proj#5 (value returned from callnodes including allocations)
//     CheckCastPP, CastPP
//
// The LoadP, Proj and CheckCastPP behave like variables assigned to only once.
// Only a Phi can have multiple assignments.  Each input to a Phi is treated
// as an assignment to it.
//
// The following node types are JavaObject:
//
//     top()
//     Allocate
//     AllocateArray
//     Parm  (for incoming arguments)
//     CastX2P ("unsafe" operations)
//     CreateEx
//     ConP
//     LoadKlass
//     ThreadLocal
//
// AddP nodes are fields.
//
// After building the graph, a pass is made over the nodes, deleting deferred
// nodes and copying the edges from the target of the deferred edge to the
// source.  This results in a graph with no deferred edges, only:
//
//    LV -P> JO
//    OF -P> JO (the object whose oop is stored in the field)
//    JO -F> OF
//
// Then, for each node which is GlobalEscape, anything it could point to
// is marked GlobalEscape.  Finally, for any node marked ArgEscape, anything
// it could point to is marked ArgEscape.
//

class  Compile;
class  Node;
class  CallNode;
class  PhiNode;
class  PhaseTransform;
class  Type;
class  TypePtr;
class  VectorSet;

class PointsToNode {
friend class ConnectionGraph;
public:
  typedef enum {
    UnknownType = 0,
    JavaObject  = 1,
    LocalVar    = 2,
    Field       = 3
  } NodeType;

  typedef enum {
    UnknownEscape = 0,
    NoEscape      = 1, // A scalar replaceable object with unique type.
    ArgEscape     = 2, // An object passed as argument or referenced by
                       // argument (and not globally escape during call).
    GlobalEscape  = 3  // An object escapes the method and thread.
  } EscapeState;

  typedef enum {
    UnknownEdge   = 0,
    PointsToEdge  = 1,
    DeferredEdge  = 2,
    FieldEdge     = 3
  } EdgeType;

private:
  enum {
    EdgeMask = 3,
    EdgeShift = 2,

    INITIAL_EDGE_COUNT = 4
  };

  NodeType             _type;
  EscapeState          _escape;
  GrowableArray<uint>* _edges;   // outgoing edges

public:
  Node* _node;              // Ideal node corresponding to this PointsTo node.
  int   _offset;            // Object fields offsets.
  bool  _scalar_replaceable;// Not escaped object could be replaced with scalar
  bool  _hidden_alias;      // This node is an argument to a function.
                            // which may return it creating a hidden alias.

  PointsToNode():
    _type(UnknownType),
    _escape(UnknownEscape),
    _edges(NULL),
    _node(NULL),
    _offset(-1),
    _scalar_replaceable(true),
    _hidden_alias(false) {}


  EscapeState escape_state() const { return _escape; }
  NodeType node_type() const { return _type;}
  int offset() { return _offset;}

  void set_offset(int offs) { _offset = offs;}
  void set_escape_state(EscapeState state) { _escape = state; }
  void set_node_type(NodeType ntype) {
    assert(_type == UnknownType || _type == ntype, "Can't change node type");
    _type = ntype;
  }

  // count of outgoing edges
  uint edge_count() const { return (_edges == NULL) ? 0 : _edges->length(); }

  // node index of target of outgoing edge "e"
  uint edge_target(uint e) const {
    assert(_edges != NULL, "valid edge index");
    return (_edges->at(e) >> EdgeShift);
  }
  // type of outgoing edge "e"
  EdgeType edge_type(uint e) const {
    assert(_edges != NULL, "valid edge index");
    return (EdgeType) (_edges->at(e) & EdgeMask);
  }

  // add a edge of the specified type pointing to the specified target
  void add_edge(uint targIdx, EdgeType et);

  // remove an edge of the specified type pointing to the specified target
  void remove_edge(uint targIdx, EdgeType et);

#ifndef PRODUCT
  void dump(bool print_state=true) const;
#endif

};

class ConnectionGraph: public ResourceObj {
private:
  GrowableArray<PointsToNode>  _nodes; // Connection graph nodes indexed
                                       // by ideal node index.

  Unique_Node_List  _delayed_worklist; // Nodes to be processed before
                                       // the call build_connection_graph().

  GrowableArray<MergeMemNode *>  _mergemem_worklist; // List of all MergeMem nodes

  VectorSet                _processed; // Records which nodes have been
                                       // processed.

  bool                    _collecting; // Indicates whether escape information
                                       // is still being collected. If false,
                                       // no new nodes will be processed.

  uint                _phantom_object; // Index of globally escaping object
                                       // that pointer values loaded from
                                       // a field which has not been set
                                       // are assumed to point to.
  uint                      _oop_null; // ConP(#NULL)
  uint                     _noop_null; // ConN(#NULL)

  Compile *                  _compile; // Compile object for current compilation
  PhaseIterGVN *                _igvn; // Value numbering

  // Address of an element in _nodes.  Used when the element is to be modified
  PointsToNode *ptnode_adr(uint idx) const {
    // There should be no new ideal nodes during ConnectionGraph build,
    // growableArray::adr_at() will throw assert otherwise.
    return _nodes.adr_at(idx);
  }
  uint nodes_size() const { return _nodes.length(); }

  // Add node to ConnectionGraph.
  void add_node(Node *n, PointsToNode::NodeType nt, PointsToNode::EscapeState es, bool done);

  // offset of a field reference
  int address_offset(Node* adr, PhaseTransform *phase);

  // compute the escape state for arguments to a call
  void process_call_arguments(CallNode *call, PhaseTransform *phase);

  // compute the escape state for the return value of a call
  void process_call_result(ProjNode *resproj, PhaseTransform *phase);

  // Populate Connection Graph with Ideal nodes.
  void record_for_escape_analysis(Node *n, PhaseTransform *phase);

  // Build Connection Graph and set nodes escape state.
  void build_connection_graph(Node *n, PhaseTransform *phase);

  // walk the connection graph starting at the node corresponding to "n" and
  // add the index of everything it could point to, to "ptset".  This may cause
  // Phi's encountered to get (re)processed  (which requires "phase".)
  void PointsTo(VectorSet &ptset, Node * n);

  //  Edge manipulation.  The "from_i" and "to_i" arguments are the
  //  node indices of the source and destination of the edge
  void add_pointsto_edge(uint from_i, uint to_i);
  void add_deferred_edge(uint from_i, uint to_i);
  void add_field_edge(uint from_i, uint to_i, int offs);


  // Add an edge to node given by "to_i" from any field of adr_i whose offset
  // matches "offset"  A deferred edge is added if to_i is a LocalVar, and
  // a pointsto edge is added if it is a JavaObject
  void add_edge_from_fields(uint adr, uint to_i, int offs);

  // Add a deferred  edge from node given by "from_i" to any field
  // of adr_i whose offset matches "offset"
  void add_deferred_edge_to_fields(uint from_i, uint adr, int offs);


  // Remove outgoing deferred edges from the node referenced by "ni".
  // Any outgoing edges from the target of the deferred edge are copied
  // to "ni".
  void remove_deferred(uint ni, GrowableArray<uint>* deferred_edges, VectorSet* visited);

  Node_Array _node_map; // used for bookeeping during type splitting
                        // Used for the following purposes:
                        // Memory Phi    - most recent unique Phi split out
                        //                 from this Phi
                        // MemNode       - new memory input for this node
                        // ChecCastPP    - allocation that this is a cast of
                        // allocation    - CheckCastPP of the allocation
  bool split_AddP(Node *addp, Node *base,  PhaseGVN  *igvn);
  PhiNode *create_split_phi(PhiNode *orig_phi, int alias_idx, GrowableArray<PhiNode *>  &orig_phi_worklist, PhaseGVN  *igvn, bool &new_created);
  PhiNode *split_memory_phi(PhiNode *orig_phi, int alias_idx, GrowableArray<PhiNode *>  &orig_phi_worklist, PhaseGVN  *igvn);
  void  move_inst_mem(Node* n, GrowableArray<PhiNode *>  &orig_phis, PhaseGVN *igvn);
  Node *find_inst_mem(Node *mem, int alias_idx,GrowableArray<PhiNode *>  &orig_phi_worklist,  PhaseGVN  *igvn);

  // Propagate unique types created for unescaped allocated objects
  // through the graph
  void split_unique_types(GrowableArray<Node *>  &alloc_worklist);

  // manage entries in _node_map
  void  set_map(int idx, Node *n)        { _node_map.map(idx, n); }
  Node *get_map(int idx)                 { return _node_map[idx]; }
  PhiNode *get_map_phi(int idx) {
    Node *phi = _node_map[idx];
    return (phi == NULL) ? NULL : phi->as_Phi();
  }

  // Notify optimizer that a node has been modified
  // Node:  This assumes that escape analysis is run before
  //        PhaseIterGVN creation
  void record_for_optimizer(Node *n) {
    _igvn->_worklist.push(n);
  }

  // Set the escape state of a node
  void set_escape_state(uint ni, PointsToNode::EscapeState es);

  // Search for objects which are not scalar replaceable.
  void verify_escape_state(int nidx, VectorSet& ptset, PhaseTransform* phase);

public:
  ConnectionGraph(Compile *C, PhaseIterGVN *igvn);

  // Check for non-escaping candidates
  static bool has_candidates(Compile *C);

  // Perform escape analysis
  static void do_analysis(Compile *C, PhaseIterGVN *igvn);

  // Compute the escape information
  bool compute_escape();

  // escape state of a node
  PointsToNode::EscapeState escape_state(Node *n);

  // other information we have collected
  bool is_scalar_replaceable(Node *n) {
    if (_collecting || (n->_idx >= nodes_size()))
      return false;
    PointsToNode* ptn = ptnode_adr(n->_idx);
    return ptn->escape_state() == PointsToNode::NoEscape && ptn->_scalar_replaceable;
  }

  bool hidden_alias(Node *n) {
    if (_collecting || (n->_idx >= nodes_size()))
      return true;
    PointsToNode* ptn = ptnode_adr(n->_idx);
    return (ptn->escape_state() != PointsToNode::NoEscape) || ptn->_hidden_alias;
  }

#ifndef PRODUCT
  void dump();
#endif
};
