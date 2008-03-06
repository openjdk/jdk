/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_escape.cpp.incl"

uint PointsToNode::edge_target(uint e) const {
  assert(_edges != NULL && e < (uint)_edges->length(), "valid edge index");
  return (_edges->at(e) >> EdgeShift);
}

PointsToNode::EdgeType PointsToNode::edge_type(uint e) const {
  assert(_edges != NULL && e < (uint)_edges->length(), "valid edge index");
  return (EdgeType) (_edges->at(e) & EdgeMask);
}

void PointsToNode::add_edge(uint targIdx, PointsToNode::EdgeType et) {
  uint v = (targIdx << EdgeShift) + ((uint) et);
  if (_edges == NULL) {
     Arena *a = Compile::current()->comp_arena();
    _edges = new(a) GrowableArray<uint>(a, INITIAL_EDGE_COUNT, 0, 0);
  }
  _edges->append_if_missing(v);
}

void PointsToNode::remove_edge(uint targIdx, PointsToNode::EdgeType et) {
  uint v = (targIdx << EdgeShift) + ((uint) et);

  _edges->remove(v);
}

#ifndef PRODUCT
static char *node_type_names[] = {
  "UnknownType",
  "JavaObject",
  "LocalVar",
  "Field"
};

static char *esc_names[] = {
  "UnknownEscape",
  "NoEscape     ",
  "ArgEscape    ",
  "GlobalEscape "
};

static char *edge_type_suffix[] = {
 "?", // UnknownEdge
 "P", // PointsToEdge
 "D", // DeferredEdge
 "F"  // FieldEdge
};

void PointsToNode::dump() const {
  NodeType nt = node_type();
  EscapeState es = escape_state();
  tty->print("%s  %s  [[", node_type_names[(int) nt], esc_names[(int) es]);
  for (uint i = 0; i < edge_count(); i++) {
    tty->print(" %d%s", edge_target(i), edge_type_suffix[(int) edge_type(i)]);
  }
  tty->print("]]  ");
  if (_node == NULL)
    tty->print_cr("<null>");
  else
    _node->dump();
}
#endif

ConnectionGraph::ConnectionGraph(Compile * C) : _processed(C->comp_arena()), _node_map(C->comp_arena()) {
  _collecting = true;
  this->_compile = C;
  const PointsToNode &dummy = PointsToNode();
  _nodes = new(C->comp_arena()) GrowableArray<PointsToNode>(C->comp_arena(), (int) INITIAL_NODE_COUNT, 0, dummy);
  _phantom_object = C->top()->_idx;
  PointsToNode *phn = ptnode_adr(_phantom_object);
  phn->set_node_type(PointsToNode::JavaObject);
  phn->set_escape_state(PointsToNode::GlobalEscape);
}

void ConnectionGraph::add_pointsto_edge(uint from_i, uint to_i) {
  PointsToNode *f = ptnode_adr(from_i);
  PointsToNode *t = ptnode_adr(to_i);

  assert(f->node_type() != PointsToNode::UnknownType && t->node_type() != PointsToNode::UnknownType, "node types must be set");
  assert(f->node_type() == PointsToNode::LocalVar || f->node_type() == PointsToNode::Field, "invalid source of PointsTo edge");
  assert(t->node_type() == PointsToNode::JavaObject, "invalid destination of PointsTo edge");
  f->add_edge(to_i, PointsToNode::PointsToEdge);
}

void ConnectionGraph::add_deferred_edge(uint from_i, uint to_i) {
  PointsToNode *f = ptnode_adr(from_i);
  PointsToNode *t = ptnode_adr(to_i);

  assert(f->node_type() != PointsToNode::UnknownType && t->node_type() != PointsToNode::UnknownType, "node types must be set");
  assert(f->node_type() == PointsToNode::LocalVar || f->node_type() == PointsToNode::Field, "invalid source of Deferred edge");
  assert(t->node_type() == PointsToNode::LocalVar || t->node_type() == PointsToNode::Field, "invalid destination of Deferred edge");
  // don't add a self-referential edge, this can occur during removal of
  // deferred edges
  if (from_i != to_i)
    f->add_edge(to_i, PointsToNode::DeferredEdge);
}

int ConnectionGraph::type_to_offset(const Type *t) {
  const TypePtr *t_ptr = t->isa_ptr();
  assert(t_ptr != NULL, "must be a pointer type");
  return t_ptr->offset();
}

void ConnectionGraph::add_field_edge(uint from_i, uint to_i, int offset) {
  PointsToNode *f = ptnode_adr(from_i);
  PointsToNode *t = ptnode_adr(to_i);

  assert(f->node_type() != PointsToNode::UnknownType && t->node_type() != PointsToNode::UnknownType, "node types must be set");
  assert(f->node_type() == PointsToNode::JavaObject, "invalid destination of Field edge");
  assert(t->node_type() == PointsToNode::Field, "invalid destination of Field edge");
  assert (t->offset() == -1 || t->offset() == offset, "conflicting field offsets");
  t->set_offset(offset);

  f->add_edge(to_i, PointsToNode::FieldEdge);
}

void ConnectionGraph::set_escape_state(uint ni, PointsToNode::EscapeState es) {
  PointsToNode *npt = ptnode_adr(ni);
  PointsToNode::EscapeState old_es = npt->escape_state();
  if (es > old_es)
    npt->set_escape_state(es);
}

PointsToNode::EscapeState ConnectionGraph::escape_state(Node *n, PhaseTransform *phase) {
  uint idx = n->_idx;
  PointsToNode::EscapeState es;

  // If we are still collecting we don't know the answer yet
  if (_collecting)
    return PointsToNode::UnknownEscape;

  // if the node was created after the escape computation, return
  // UnknownEscape
  if (idx >= (uint)_nodes->length())
    return PointsToNode::UnknownEscape;

  es = _nodes->at_grow(idx).escape_state();

  // if we have already computed a value, return it
  if (es != PointsToNode::UnknownEscape)
    return es;

  // compute max escape state of anything this node could point to
  VectorSet ptset(Thread::current()->resource_area());
  PointsTo(ptset, n, phase);
  for( VectorSetI i(&ptset); i.test() && es != PointsToNode::GlobalEscape; ++i ) {
    uint pt = i.elem;
    PointsToNode::EscapeState pes = _nodes->at(pt).escape_state();
    if (pes > es)
      es = pes;
  }
  // cache the computed escape state
  assert(es != PointsToNode::UnknownEscape, "should have computed an escape state");
  _nodes->adr_at(idx)->set_escape_state(es);
  return es;
}

void ConnectionGraph::PointsTo(VectorSet &ptset, Node * n, PhaseTransform *phase) {
  VectorSet visited(Thread::current()->resource_area());
  GrowableArray<uint>  worklist;

  n = skip_casts(n);
  PointsToNode  npt = _nodes->at_grow(n->_idx);

  // If we have a JavaObject, return just that object
  if (npt.node_type() == PointsToNode::JavaObject) {
    ptset.set(n->_idx);
    return;
  }
  // we may have a Phi which has not been processed
  if (npt._node == NULL) {
    assert(n->is_Phi(), "unprocessed node must be a Phi");
    record_for_escape_analysis(n);
    npt = _nodes->at(n->_idx);
  }
  worklist.push(n->_idx);
  while(worklist.length() > 0) {
    int ni = worklist.pop();
    PointsToNode pn = _nodes->at_grow(ni);
    if (!visited.test(ni)) {
      visited.set(ni);

      // ensure that all inputs of a Phi have been processed
      if (_collecting && pn._node->is_Phi()) {
        PhiNode *phi = pn._node->as_Phi();
        process_phi_escape(phi, phase);
      }

      int edges_processed = 0;
      for (uint e = 0; e < pn.edge_count(); e++) {
        PointsToNode::EdgeType et = pn.edge_type(e);
        if (et == PointsToNode::PointsToEdge) {
          ptset.set(pn.edge_target(e));
          edges_processed++;
        } else if (et == PointsToNode::DeferredEdge) {
          worklist.push(pn.edge_target(e));
          edges_processed++;
        }
      }
      if (edges_processed == 0) {
        // no deferred or pointsto edges found.  Assume the value was set outside
        // this method.  Add the phantom object to the pointsto set.
        ptset.set(_phantom_object);
      }
    }
  }
}

void ConnectionGraph::remove_deferred(uint ni) {
  VectorSet visited(Thread::current()->resource_area());

  uint i = 0;
  PointsToNode *ptn = ptnode_adr(ni);

  while(i < ptn->edge_count()) {
    if (ptn->edge_type(i) != PointsToNode::DeferredEdge) {
      i++;
    } else {
      uint t = ptn->edge_target(i);
      PointsToNode *ptt = ptnode_adr(t);
      ptn->remove_edge(t, PointsToNode::DeferredEdge);
      if(!visited.test(t)) {
        visited.set(t);
        for (uint j = 0; j < ptt->edge_count(); j++) {
          uint n1 = ptt->edge_target(j);
          PointsToNode *pt1 = ptnode_adr(n1);
          switch(ptt->edge_type(j)) {
            case PointsToNode::PointsToEdge:
               add_pointsto_edge(ni, n1);
              break;
            case PointsToNode::DeferredEdge:
              add_deferred_edge(ni, n1);
              break;
            case PointsToNode::FieldEdge:
              assert(false, "invalid connection graph");
              break;
          }
        }
      }
    }
  }
}


//  Add an edge to node given by "to_i" from any field of adr_i whose offset
//  matches "offset"  A deferred edge is added if to_i is a LocalVar, and
//  a pointsto edge is added if it is a JavaObject

void ConnectionGraph::add_edge_from_fields(uint adr_i, uint to_i, int offs) {
  PointsToNode an = _nodes->at_grow(adr_i);
  PointsToNode to = _nodes->at_grow(to_i);
  bool deferred = (to.node_type() == PointsToNode::LocalVar);

  for (uint fe = 0; fe < an.edge_count(); fe++) {
    assert(an.edge_type(fe) == PointsToNode::FieldEdge, "expecting a field edge");
    int fi = an.edge_target(fe);
    PointsToNode pf = _nodes->at_grow(fi);
    int po = pf.offset();
    if (po == offs || po == Type::OffsetBot || offs == Type::OffsetBot) {
      if (deferred)
        add_deferred_edge(fi, to_i);
      else
        add_pointsto_edge(fi, to_i);
    }
  }
}

//  Add a deferred  edge from node given by "from_i" to any field of adr_i whose offset
//  matches "offset"
void ConnectionGraph::add_deferred_edge_to_fields(uint from_i, uint adr_i, int offs) {
  PointsToNode an = _nodes->at_grow(adr_i);
  for (uint fe = 0; fe < an.edge_count(); fe++) {
    assert(an.edge_type(fe) == PointsToNode::FieldEdge, "expecting a field edge");
    int fi = an.edge_target(fe);
    PointsToNode pf = _nodes->at_grow(fi);
    int po = pf.offset();
    if (pf.edge_count() == 0) {
      // we have not seen any stores to this field, assume it was set outside this method
      add_pointsto_edge(fi, _phantom_object);
    }
    if (po == offs || po == Type::OffsetBot || offs == Type::OffsetBot) {
      add_deferred_edge(from_i, fi);
    }
  }
}

//
// Search memory chain of "mem" to find a MemNode whose address
// is the specified alias index.  Returns the MemNode found or the
// first non-MemNode encountered.
//
Node *ConnectionGraph::find_mem(Node *mem, int alias_idx, PhaseGVN  *igvn) {
  if (mem == NULL)
    return mem;
  while (mem->is_Mem()) {
    const Type *at = igvn->type(mem->in(MemNode::Address));
    if (at != Type::TOP) {
      assert (at->isa_ptr() != NULL, "pointer type required.");
      int idx = _compile->get_alias_index(at->is_ptr());
      if (idx == alias_idx)
        break;
    }
    mem = mem->in(MemNode::Memory);
  }
  return mem;
}

//
// Adjust the type and inputs of an AddP which computes the
// address of a field of an instance
//
void ConnectionGraph::split_AddP(Node *addp, Node *base,  PhaseGVN  *igvn) {
  const TypeOopPtr *t = igvn->type(addp)->isa_oopptr();
  const TypeOopPtr *base_t = igvn->type(base)->isa_oopptr();
  assert(t != NULL,  "expecting oopptr");
  assert(base_t != NULL && base_t->is_instance(), "expecting instance oopptr");
  uint inst_id =  base_t->instance_id();
  assert(!t->is_instance() || t->instance_id() == inst_id,
                             "old type must be non-instance or match new type");
  const TypeOopPtr *tinst = base_t->add_offset(t->offset())->is_oopptr();
  // ensure an alias index is allocated for the instance type
  int alias_idx = _compile->get_alias_index(tinst);
  igvn->set_type(addp, tinst);
  // record the allocation in the node map
  set_map(addp->_idx, get_map(base->_idx));
  // if the Address input is not the appropriate instance type (due to intervening
  // casts,) insert a cast
  Node *adr = addp->in(AddPNode::Address);
  const TypeOopPtr  *atype = igvn->type(adr)->isa_oopptr();
  if (atype->instance_id() != inst_id) {
    assert(!atype->is_instance(), "no conflicting instances");
    const TypeOopPtr *new_atype = base_t->add_offset(atype->offset())->isa_oopptr();
    Node *acast = new (_compile, 2) CastPPNode(adr, new_atype);
    acast->set_req(0, adr->in(0));
    igvn->set_type(acast, new_atype);
    record_for_optimizer(acast);
    Node *bcast = acast;
    Node *abase = addp->in(AddPNode::Base);
    if (abase != adr) {
      bcast = new (_compile, 2) CastPPNode(abase, base_t);
      bcast->set_req(0, abase->in(0));
      igvn->set_type(bcast, base_t);
      record_for_optimizer(bcast);
    }
    igvn->hash_delete(addp);
    addp->set_req(AddPNode::Base, bcast);
    addp->set_req(AddPNode::Address, acast);
    igvn->hash_insert(addp);
    record_for_optimizer(addp);
  }
}

//
// Create a new version of orig_phi if necessary. Returns either the newly
// created phi or an existing phi.  Sets create_new to indicate wheter  a new
// phi was created.  Cache the last newly created phi in the node map.
//
PhiNode *ConnectionGraph::create_split_phi(PhiNode *orig_phi, int alias_idx, GrowableArray<PhiNode *>  &orig_phi_worklist, PhaseGVN  *igvn, bool &new_created) {
  Compile *C = _compile;
  new_created = false;
  int phi_alias_idx = C->get_alias_index(orig_phi->adr_type());
  // nothing to do if orig_phi is bottom memory or matches alias_idx
  if (phi_alias_idx == Compile::AliasIdxBot || phi_alias_idx == alias_idx) {
    return orig_phi;
  }
  // have we already created a Phi for this alias index?
  PhiNode *result = get_map_phi(orig_phi->_idx);
  const TypePtr *atype = C->get_adr_type(alias_idx);
  if (result != NULL && C->get_alias_index(result->adr_type()) == alias_idx) {
    return result;
  }
  if ((int)C->unique() + 2*NodeLimitFudgeFactor > MaxNodeLimit) {
    if (C->do_escape_analysis() == true && !C->failing()) {
      // Retry compilation without escape analysis.
      // If this is the first failure, the sentinel string will "stick"
      // to the Compile object, and the C2Compiler will see it and retry.
      C->record_failure(C2Compiler::retry_no_escape_analysis());
    }
    return NULL;
  }

  orig_phi_worklist.append_if_missing(orig_phi);
  result = PhiNode::make(orig_phi->in(0), NULL, Type::MEMORY, atype);
  set_map_phi(orig_phi->_idx, result);
  igvn->set_type(result, result->bottom_type());
  record_for_optimizer(result);
  new_created = true;
  return result;
}

//
// Return a new version  of Memory Phi "orig_phi" with the inputs having the
// specified alias index.
//
PhiNode *ConnectionGraph::split_memory_phi(PhiNode *orig_phi, int alias_idx, GrowableArray<PhiNode *>  &orig_phi_worklist, PhaseGVN  *igvn) {

  assert(alias_idx != Compile::AliasIdxBot, "can't split out bottom memory");
  Compile *C = _compile;
  bool new_phi_created;
  PhiNode *result =  create_split_phi(orig_phi, alias_idx, orig_phi_worklist, igvn, new_phi_created);
  if (!new_phi_created) {
    return result;
  }

  GrowableArray<PhiNode *>  phi_list;
  GrowableArray<uint>  cur_input;

  PhiNode *phi = orig_phi;
  uint idx = 1;
  bool finished = false;
  while(!finished) {
    while (idx < phi->req()) {
      Node *mem = find_mem(phi->in(idx), alias_idx, igvn);
      if (mem != NULL && mem->is_Phi()) {
        PhiNode *nphi = create_split_phi(mem->as_Phi(), alias_idx, orig_phi_worklist, igvn, new_phi_created);
        if (new_phi_created) {
          // found an phi for which we created a new split, push current one on worklist and begin
          // processing new one
          phi_list.push(phi);
          cur_input.push(idx);
          phi = mem->as_Phi();
          result = nphi;
          idx = 1;
          continue;
        } else {
          mem = nphi;
        }
      }
      if (C->failing()) {
        return NULL;
      }
      result->set_req(idx++, mem);
    }
#ifdef ASSERT
    // verify that the new Phi has an input for each input of the original
    assert( phi->req() == result->req(), "must have same number of inputs.");
    assert( result->in(0) != NULL && result->in(0) == phi->in(0), "regions must match");
    for (uint i = 1; i < phi->req(); i++) {
      assert((phi->in(i) == NULL) == (result->in(i) == NULL), "inputs must correspond.");
    }
#endif
    // we have finished processing a Phi, see if there are any more to do
    finished = (phi_list.length() == 0 );
    if (!finished) {
      phi = phi_list.pop();
      idx = cur_input.pop();
      PhiNode *prev_phi = get_map_phi(phi->_idx);
      prev_phi->set_req(idx++, result);
      result = prev_phi;
    }
  }
  return result;
}

//
//  Convert the types of unescaped object to instance types where possible,
//  propagate the new type information through the graph, and update memory
//  edges and MergeMem inputs to reflect the new type.
//
//  We start with allocations (and calls which may be allocations)  on alloc_worklist.
//  The processing is done in 4 phases:
//
//  Phase 1:  Process possible allocations from alloc_worklist.  Create instance
//            types for the CheckCastPP for allocations where possible.
//            Propagate the the new types through users as follows:
//               casts and Phi:  push users on alloc_worklist
//               AddP:  cast Base and Address inputs to the instance type
//                      push any AddP users on alloc_worklist and push any memnode
//                      users onto memnode_worklist.
//  Phase 2:  Process MemNode's from memnode_worklist. compute new address type and
//            search the Memory chain for a store with the appropriate type
//            address type.  If a Phi is found, create a new version with
//            the approriate memory slices from each of the Phi inputs.
//            For stores, process the users as follows:
//               MemNode:  push on memnode_worklist
//               MergeMem: push on mergemem_worklist
//  Phase 3:  Process MergeMem nodes from mergemem_worklist.  Walk each memory slice
//            moving the first node encountered of each  instance type to the
//            the input corresponding to its alias index.
//            appropriate memory slice.
//  Phase 4:  Update the inputs of non-instance memory Phis and the Memory input of memnodes.
//
// In the following example, the CheckCastPP nodes are the cast of allocation
// results and the allocation of node 29 is unescaped and eligible to be an
// instance type.
//
// We start with:
//
//     7 Parm #memory
//    10  ConI  "12"
//    19  CheckCastPP   "Foo"
//    20  AddP  _ 19 19 10  Foo+12  alias_index=4
//    29  CheckCastPP   "Foo"
//    30  AddP  _ 29 29 10  Foo+12  alias_index=4
//
//    40  StoreP  25   7  20   ... alias_index=4
//    50  StoreP  35  40  30   ... alias_index=4
//    60  StoreP  45  50  20   ... alias_index=4
//    70  LoadP    _  60  30   ... alias_index=4
//    80  Phi     75  50  60   Memory alias_index=4
//    90  LoadP    _  80  30   ... alias_index=4
//   100  LoadP    _  80  20   ... alias_index=4
//
//
// Phase 1 creates an instance type for node 29 assigning it an instance id of 24
// and creating a new alias index for node 30.  This gives:
//
//     7 Parm #memory
//    10  ConI  "12"
//    19  CheckCastPP   "Foo"
//    20  AddP  _ 19 19 10  Foo+12  alias_index=4
//    29  CheckCastPP   "Foo"  iid=24
//    30  AddP  _ 29 29 10  Foo+12  alias_index=6  iid=24
//
//    40  StoreP  25   7  20   ... alias_index=4
//    50  StoreP  35  40  30   ... alias_index=6
//    60  StoreP  45  50  20   ... alias_index=4
//    70  LoadP    _  60  30   ... alias_index=6
//    80  Phi     75  50  60   Memory alias_index=4
//    90  LoadP    _  80  30   ... alias_index=6
//   100  LoadP    _  80  20   ... alias_index=4
//
// In phase 2, new memory inputs are computed for the loads and stores,
// And a new version of the phi is created.  In phase 4, the inputs to
// node 80 are updated and then the memory nodes are updated with the
// values computed in phase 2.  This results in:
//
//     7 Parm #memory
//    10  ConI  "12"
//    19  CheckCastPP   "Foo"
//    20  AddP  _ 19 19 10  Foo+12  alias_index=4
//    29  CheckCastPP   "Foo"  iid=24
//    30  AddP  _ 29 29 10  Foo+12  alias_index=6  iid=24
//
//    40  StoreP  25  7   20   ... alias_index=4
//    50  StoreP  35  7   30   ... alias_index=6
//    60  StoreP  45  40  20   ... alias_index=4
//    70  LoadP    _  50  30   ... alias_index=6
//    80  Phi     75  40  60   Memory alias_index=4
//   120  Phi     75  50  50   Memory alias_index=6
//    90  LoadP    _ 120  30   ... alias_index=6
//   100  LoadP    _  80  20   ... alias_index=4
//
void ConnectionGraph::split_unique_types(GrowableArray<Node *>  &alloc_worklist) {
  GrowableArray<Node *>  memnode_worklist;
  GrowableArray<Node *>  mergemem_worklist;
  GrowableArray<PhiNode *>  orig_phis;
  PhaseGVN  *igvn = _compile->initial_gvn();
  uint new_index_start = (uint) _compile->num_alias_types();
  VectorSet visited(Thread::current()->resource_area());
  VectorSet ptset(Thread::current()->resource_area());

  //  Phase 1:  Process possible allocations from alloc_worklist.  Create instance
  //            types for the CheckCastPP for allocations where possible.
  while (alloc_worklist.length() != 0) {
    Node *n = alloc_worklist.pop();
    uint ni = n->_idx;
    if (n->is_Call()) {
      CallNode *alloc = n->as_Call();
      // copy escape information to call node
      PointsToNode ptn = _nodes->at(alloc->_idx);
      PointsToNode::EscapeState es = escape_state(alloc, igvn);
      alloc->_escape_state = es;
      // find CheckCastPP of call return value
      n = alloc->proj_out(TypeFunc::Parms);
      if (n != NULL && n->outcnt() == 1) {
        n = n->unique_out();
        if (n->Opcode() != Op_CheckCastPP) {
          continue;
        }
      } else {
        continue;
      }
      // we have an allocation or call which returns a Java object, see if it is unescaped
      if (es != PointsToNode::NoEscape || !ptn._unique_type) {
        continue; //  can't make a unique type
      }
      if (alloc->is_Allocate()) {
        // Set the scalar_replaceable flag before the next check.
        alloc->as_Allocate()->_is_scalar_replaceable = true;
      }

      set_map(alloc->_idx, n);
      set_map(n->_idx, alloc);
      const TypeInstPtr *t = igvn->type(n)->isa_instptr();
      // Unique types which are arrays are not currently supported.
      // The check for AllocateArray is needed in case an array
      // allocation is immediately cast to Object
      if (t == NULL || alloc->is_AllocateArray())
        continue;  // not a TypeInstPtr
      const TypeOopPtr *tinst = t->cast_to_instance(ni);
      igvn->hash_delete(n);
      igvn->set_type(n,  tinst);
      n->raise_bottom_type(tinst);
      igvn->hash_insert(n);
    } else if (n->is_AddP()) {
      ptset.Clear();
      PointsTo(ptset, n->in(AddPNode::Address), igvn);
      assert(ptset.Size() == 1, "AddP address is unique");
      Node *base = get_map(ptset.getelem());
      split_AddP(n, base, igvn);
    } else if (n->is_Phi() || n->Opcode() == Op_CastPP || n->Opcode() == Op_CheckCastPP) {
      if (visited.test_set(n->_idx)) {
        assert(n->is_Phi(), "loops only through Phi's");
        continue;  // already processed
      }
      ptset.Clear();
      PointsTo(ptset, n, igvn);
      if (ptset.Size() == 1) {
        TypeNode *tn = n->as_Type();
        Node *val = get_map(ptset.getelem());
        const TypeInstPtr *val_t = igvn->type(val)->isa_instptr();;
        assert(val_t != NULL && val_t->is_instance(), "instance type expected.");
        const TypeInstPtr *tn_t = igvn->type(tn)->isa_instptr();;

        if (tn_t != NULL && val_t->cast_to_instance(TypeOopPtr::UNKNOWN_INSTANCE)->higher_equal(tn_t)) {
          igvn->hash_delete(tn);
          igvn->set_type(tn, val_t);
          tn->set_type(val_t);
          igvn->hash_insert(tn);
        }
      }
    } else {
      continue;
    }
    // push users on appropriate worklist
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node *use = n->fast_out(i);
      if(use->is_Mem() && use->in(MemNode::Address) == n) {
        memnode_worklist.push(use);
      } else if (use->is_AddP() || use->is_Phi() || use->Opcode() == Op_CastPP || use->Opcode() == Op_CheckCastPP) {
        alloc_worklist.push(use);
      }
    }

  }
  uint new_index_end = (uint) _compile->num_alias_types();

  //  Phase 2:  Process MemNode's from memnode_worklist. compute new address type and
  //            compute new values for Memory inputs  (the Memory inputs are not
  //            actually updated until phase 4.)
  if (memnode_worklist.length() == 0)
    return;  // nothing to do


  while (memnode_worklist.length() != 0) {
    Node *n = memnode_worklist.pop();
    if (n->is_Phi()) {
      assert(n->as_Phi()->adr_type() != TypePtr::BOTTOM, "narrow memory slice required");
      // we don't need to do anything, but the users must be pushed if we haven't processed
      // this Phi before
      if (visited.test_set(n->_idx))
        continue;
    } else {
      assert(n->is_Mem(), "memory node required.");
      Node *addr = n->in(MemNode::Address);
      const Type *addr_t = igvn->type(addr);
      if (addr_t == Type::TOP)
        continue;
      assert (addr_t->isa_ptr() != NULL, "pointer type required.");
      int alias_idx = _compile->get_alias_index(addr_t->is_ptr());
      Node *mem = find_mem(n->in(MemNode::Memory), alias_idx, igvn);
      if (mem->is_Phi()) {
        mem = split_memory_phi(mem->as_Phi(), alias_idx, orig_phis, igvn);
      }
      if (_compile->failing()) {
        return;
      }
      if (mem != n->in(MemNode::Memory))
        set_map(n->_idx, mem);
      if (n->is_Load()) {
        continue;  // don't push users
      } else if (n->is_LoadStore()) {
        // get the memory projection
        for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
          Node *use = n->fast_out(i);
          if (use->Opcode() == Op_SCMemProj) {
            n = use;
            break;
          }
        }
        assert(n->Opcode() == Op_SCMemProj, "memory projection required");
      }
    }
    // push user on appropriate worklist
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node *use = n->fast_out(i);
      if (use->is_Phi()) {
        memnode_worklist.push(use);
      } else if(use->is_Mem() && use->in(MemNode::Memory) == n) {
        memnode_worklist.push(use);
      } else if (use->is_MergeMem()) {
        mergemem_worklist.push(use);
      }
    }
  }

  //  Phase 3:  Process MergeMem nodes from mergemem_worklist.  Walk each memory slice
  //            moving the first node encountered of each  instance type to the
  //            the input corresponding to its alias index.
  while (mergemem_worklist.length() != 0) {
    Node *n = mergemem_worklist.pop();
    assert(n->is_MergeMem(), "MergeMem node required.");
    MergeMemNode *nmm = n->as_MergeMem();
    // Note: we don't want to use MergeMemStream here because we only want to
    //       scan inputs which exist at the start, not ones we add during processing
    uint nslices = nmm->req();
    igvn->hash_delete(nmm);
    for (uint i = Compile::AliasIdxRaw+1; i < nslices; i++) {
      Node * mem = nmm->in(i);
      Node * cur = NULL;
      if (mem == NULL || mem->is_top())
        continue;
      while (mem->is_Mem()) {
        const Type *at = igvn->type(mem->in(MemNode::Address));
        if (at != Type::TOP) {
          assert (at->isa_ptr() != NULL, "pointer type required.");
          uint idx = (uint)_compile->get_alias_index(at->is_ptr());
          if (idx == i) {
            if (cur == NULL)
              cur = mem;
          } else {
            if (idx >= nmm->req() || nmm->is_empty_memory(nmm->in(idx))) {
              nmm->set_memory_at(idx, mem);
            }
          }
        }
        mem = mem->in(MemNode::Memory);
      }
      nmm->set_memory_at(i, (cur != NULL) ? cur : mem);
      if (mem->is_Phi()) {
        // We have encountered a Phi, we need to split the Phi for
        // any  instance of the current type if we haven't encountered
        //  a value of the instance along the chain.
        for (uint ni = new_index_start; ni < new_index_end; ni++) {
          if((uint)_compile->get_general_index(ni) == i) {
            Node *m = (ni >= nmm->req()) ? nmm->empty_memory() : nmm->in(ni);
            if (nmm->is_empty_memory(m)) {
              m = split_memory_phi(mem->as_Phi(), ni, orig_phis, igvn);
              if (_compile->failing()) {
                return;
              }
              nmm->set_memory_at(ni, m);
            }
          }
        }
      }
    }
    igvn->hash_insert(nmm);
    record_for_optimizer(nmm);
  }

  //  Phase 4:  Update the inputs of non-instance memory Phis and the Memory input of memnodes
  //
  // First update the inputs of any non-instance Phi's from
  // which we split out an instance Phi.  Note we don't have
  // to recursively process Phi's encounted on the input memory
  // chains as is done in split_memory_phi() since they  will
  // also be processed here.
  while (orig_phis.length() != 0) {
    PhiNode *phi = orig_phis.pop();
    int alias_idx = _compile->get_alias_index(phi->adr_type());
    igvn->hash_delete(phi);
    for (uint i = 1; i < phi->req(); i++) {
      Node *mem = phi->in(i);
      Node *new_mem = find_mem(mem, alias_idx, igvn);
      if (mem != new_mem) {
        phi->set_req(i, new_mem);
      }
    }
    igvn->hash_insert(phi);
    record_for_optimizer(phi);
  }

  // Update the memory inputs of MemNodes with the value we computed
  // in Phase 2.
  for (int i = 0; i < _nodes->length(); i++) {
    Node *nmem = get_map(i);
    if (nmem != NULL) {
      Node *n = _nodes->at(i)._node;
      if (n != NULL && n->is_Mem()) {
        igvn->hash_delete(n);
        n->set_req(MemNode::Memory, nmem);
        igvn->hash_insert(n);
        record_for_optimizer(n);
      }
    }
  }
}

void ConnectionGraph::compute_escape() {
  GrowableArray<int>  worklist;
  GrowableArray<Node *>  alloc_worklist;
  VectorSet visited(Thread::current()->resource_area());
  PhaseGVN  *igvn = _compile->initial_gvn();

  // process Phi nodes from the deferred list, they may not have
  while(_deferred.size() > 0) {
    Node * n = _deferred.pop();
    PhiNode * phi = n->as_Phi();

    process_phi_escape(phi, igvn);
  }

  VectorSet ptset(Thread::current()->resource_area());

  // remove deferred edges from the graph and collect
  // information we will need for type splitting
  for (uint ni = 0; ni < (uint)_nodes->length(); ni++) {
    PointsToNode * ptn = _nodes->adr_at(ni);
    PointsToNode::NodeType nt = ptn->node_type();

    if (nt == PointsToNode::UnknownType) {
      continue;  // not a node we are interested in
    }
    Node *n = ptn->_node;
    if (nt == PointsToNode::LocalVar || nt == PointsToNode::Field) {
      remove_deferred(ni);
      if (n->is_AddP()) {
        // if this AddP computes an address which may point to more that one
        // object, nothing the address points to can be a unique type.
        Node *base = n->in(AddPNode::Base);
        ptset.Clear();
        PointsTo(ptset, base, igvn);
        if (ptset.Size() > 1) {
          for( VectorSetI j(&ptset); j.test(); ++j ) {
            PointsToNode *ptaddr = _nodes->adr_at(j.elem);
            ptaddr->_unique_type = false;
          }
        }
      }
    } else if (n->is_Call()) {
        // initialize _escape_state of calls to GlobalEscape
        n->as_Call()->_escape_state = PointsToNode::GlobalEscape;
        // push call on alloc_worlist (alocations are calls)
        // for processing by split_unique_types()
        alloc_worklist.push(n);
    }
  }
  // push all GlobalEscape nodes on the worklist
  for (uint nj = 0; nj < (uint)_nodes->length(); nj++) {
    if (_nodes->at(nj).escape_state() == PointsToNode::GlobalEscape) {
      worklist.append(nj);
    }
  }
  // mark all node reachable from GlobalEscape nodes
  while(worklist.length() > 0) {
    PointsToNode n = _nodes->at(worklist.pop());
    for (uint ei = 0; ei < n.edge_count(); ei++) {
      uint npi = n.edge_target(ei);
      PointsToNode *np = ptnode_adr(npi);
      if (np->escape_state() != PointsToNode::GlobalEscape) {
        np->set_escape_state(PointsToNode::GlobalEscape);
        worklist.append_if_missing(npi);
      }
    }
  }

  // push all ArgEscape nodes on the worklist
  for (uint nk = 0; nk < (uint)_nodes->length(); nk++) {
    if (_nodes->at(nk).escape_state() == PointsToNode::ArgEscape)
      worklist.push(nk);
  }
  // mark all node reachable from ArgEscape nodes
  while(worklist.length() > 0) {
    PointsToNode n = _nodes->at(worklist.pop());

    for (uint ei = 0; ei < n.edge_count(); ei++) {
      uint npi = n.edge_target(ei);
      PointsToNode *np = ptnode_adr(npi);
      if (np->escape_state() != PointsToNode::ArgEscape) {
        np->set_escape_state(PointsToNode::ArgEscape);
        worklist.append_if_missing(npi);
      }
    }
  }
  _collecting = false;

  // Now use the escape information to create unique types for
  // unescaped objects
  split_unique_types(alloc_worklist);
  if (_compile->failing())  return;

  // Clean up after split unique types.
  ResourceMark rm;
  PhaseRemoveUseless pru(_compile->initial_gvn(), _compile->for_igvn());
}

Node * ConnectionGraph::skip_casts(Node *n) {
  while(n->Opcode() == Op_CastPP || n->Opcode() == Op_CheckCastPP) {
    n = n->in(1);
  }
  return n;
}

void ConnectionGraph::process_phi_escape(PhiNode *phi, PhaseTransform *phase) {

  if (phi->type()->isa_oopptr() == NULL)
    return;  // nothing to do if not an oop

  PointsToNode *ptadr = ptnode_adr(phi->_idx);
  int incount = phi->req();
  int non_null_inputs = 0;

  for (int i = 1; i < incount ; i++) {
    if (phi->in(i) != NULL)
      non_null_inputs++;
  }
  if (non_null_inputs == ptadr->_inputs_processed)
    return;  // no new inputs since the last time this node was processed,
             // the current information is valid

  ptadr->_inputs_processed = non_null_inputs;  // prevent recursive processing of this node
  for (int j = 1; j < incount ; j++) {
    Node * n = phi->in(j);
    if (n == NULL)
      continue;  // ignore NULL
    n =  skip_casts(n);
    if (n->is_top() || n == phi)
      continue;  // ignore top or inputs which go back this node
    int nopc = n->Opcode();
    PointsToNode  npt = _nodes->at(n->_idx);
    if (_nodes->at(n->_idx).node_type() == PointsToNode::JavaObject) {
      add_pointsto_edge(phi->_idx, n->_idx);
    } else {
      add_deferred_edge(phi->_idx, n->_idx);
    }
  }
}

void ConnectionGraph::process_call_arguments(CallNode *call, PhaseTransform *phase) {

    _processed.set(call->_idx);
    switch (call->Opcode()) {

    // arguments to allocation and locking don't escape
    case Op_Allocate:
    case Op_AllocateArray:
    case Op_Lock:
    case Op_Unlock:
      break;

    case Op_CallStaticJava:
    // For a static call, we know exactly what method is being called.
    // Use bytecode estimator to record the call's escape affects
    {
      ciMethod *meth = call->as_CallJava()->method();
      if (meth != NULL) {
        const TypeTuple * d = call->tf()->domain();
        BCEscapeAnalyzer call_analyzer(meth);
        VectorSet ptset(Thread::current()->resource_area());
        for (uint i = TypeFunc::Parms; i < d->cnt(); i++) {
          const Type* at = d->field_at(i);
          int k = i - TypeFunc::Parms;

          if (at->isa_oopptr() != NULL) {
            Node *arg = skip_casts(call->in(i));

            if (!call_analyzer.is_arg_stack(k)) {
              // The argument global escapes, mark everything it could point to
              ptset.Clear();
              PointsTo(ptset, arg, phase);
              for( VectorSetI j(&ptset); j.test(); ++j ) {
                uint pt = j.elem;

                set_escape_state(pt, PointsToNode::GlobalEscape);
              }
            } else if (!call_analyzer.is_arg_local(k)) {
              // The argument itself doesn't escape, but any fields might
              ptset.Clear();
              PointsTo(ptset, arg, phase);
              for( VectorSetI j(&ptset); j.test(); ++j ) {
                uint pt = j.elem;
                add_edge_from_fields(pt, _phantom_object, Type::OffsetBot);
              }
            }
          }
        }
        call_analyzer.copy_dependencies(C()->dependencies());
        break;
      }
      // fall-through if not a Java method
    }

    default:
    // Some other type of call, assume the worst case: all arguments
    // globally escape.
    {
      // adjust escape state for  outgoing arguments
      const TypeTuple * d = call->tf()->domain();
      VectorSet ptset(Thread::current()->resource_area());
      for (uint i = TypeFunc::Parms; i < d->cnt(); i++) {
        const Type* at = d->field_at(i);

        if (at->isa_oopptr() != NULL) {
          Node *arg = skip_casts(call->in(i));
          ptset.Clear();
          PointsTo(ptset, arg, phase);
          for( VectorSetI j(&ptset); j.test(); ++j ) {
            uint pt = j.elem;

            set_escape_state(pt, PointsToNode::GlobalEscape);
          }
        }
      }
    }
  }
}
void ConnectionGraph::process_call_result(ProjNode *resproj, PhaseTransform *phase) {
  CallNode *call = resproj->in(0)->as_Call();

  PointsToNode *ptadr = ptnode_adr(resproj->_idx);

  ptadr->_node = resproj;
  ptadr->set_node_type(PointsToNode::LocalVar);
  set_escape_state(resproj->_idx, PointsToNode::UnknownEscape);
  _processed.set(resproj->_idx);

  switch (call->Opcode()) {
    case Op_Allocate:
    {
      Node *k = call->in(AllocateNode::KlassNode);
      const TypeKlassPtr *kt;
      if (k->Opcode() == Op_LoadKlass) {
        kt = k->as_Load()->type()->isa_klassptr();
      } else {
        kt = k->as_Type()->type()->isa_klassptr();
      }
      assert(kt != NULL, "TypeKlassPtr  required.");
      ciKlass* cik = kt->klass();
      ciInstanceKlass* ciik = cik->as_instance_klass();

      PointsToNode *ptadr = ptnode_adr(call->_idx);
      ptadr->set_node_type(PointsToNode::JavaObject);
      if (cik->is_subclass_of(_compile->env()->Thread_klass()) || ciik->has_finalizer()) {
        set_escape_state(call->_idx, PointsToNode::GlobalEscape);
        add_pointsto_edge(resproj->_idx, _phantom_object);
      } else {
        set_escape_state(call->_idx, PointsToNode::NoEscape);
        add_pointsto_edge(resproj->_idx, call->_idx);
      }
      _processed.set(call->_idx);
      break;
    }

    case Op_AllocateArray:
    {
      PointsToNode *ptadr = ptnode_adr(call->_idx);
      ptadr->set_node_type(PointsToNode::JavaObject);
      set_escape_state(call->_idx, PointsToNode::NoEscape);
      _processed.set(call->_idx);
      add_pointsto_edge(resproj->_idx, call->_idx);
      break;
    }

    case Op_Lock:
    case Op_Unlock:
      break;

    case Op_CallStaticJava:
    // For a static call, we know exactly what method is being called.
    // Use bytecode estimator to record whether the call's return value escapes
    {
      const TypeTuple *r = call->tf()->range();
      const Type* ret_type = NULL;

      if (r->cnt() > TypeFunc::Parms)
        ret_type = r->field_at(TypeFunc::Parms);

      // Note:  we use isa_ptr() instead of isa_oopptr()  here because the
      //        _multianewarray functions return a TypeRawPtr.
      if (ret_type == NULL || ret_type->isa_ptr() == NULL)
        break;  // doesn't return a pointer type

      ciMethod *meth = call->as_CallJava()->method();
      if (meth == NULL) {
        // not a Java method, assume global escape
        set_escape_state(call->_idx, PointsToNode::GlobalEscape);
        if (resproj != NULL)
          add_pointsto_edge(resproj->_idx, _phantom_object);
      } else {
        BCEscapeAnalyzer call_analyzer(meth);
        VectorSet ptset(Thread::current()->resource_area());

        if (call_analyzer.is_return_local() && resproj != NULL) {
          // determine whether any arguments are returned
          const TypeTuple * d = call->tf()->domain();
          set_escape_state(call->_idx, PointsToNode::NoEscape);
          for (uint i = TypeFunc::Parms; i < d->cnt(); i++) {
            const Type* at = d->field_at(i);

            if (at->isa_oopptr() != NULL) {
              Node *arg = skip_casts(call->in(i));

              if (call_analyzer.is_arg_returned(i - TypeFunc::Parms)) {
                PointsToNode *arg_esp = _nodes->adr_at(arg->_idx);
                if (arg_esp->node_type() == PointsToNode::JavaObject)
                  add_pointsto_edge(resproj->_idx, arg->_idx);
                else
                  add_deferred_edge(resproj->_idx, arg->_idx);
                arg_esp->_hidden_alias = true;
              }
            }
          }
        } else {
          set_escape_state(call->_idx, PointsToNode::GlobalEscape);
          if (resproj != NULL)
            add_pointsto_edge(resproj->_idx, _phantom_object);
        }
        call_analyzer.copy_dependencies(C()->dependencies());
      }
      break;
    }

    default:
    // Some other type of call, assume the worst case that the
    // returned value, if any, globally escapes.
    {
      const TypeTuple *r = call->tf()->range();

      if (r->cnt() > TypeFunc::Parms) {
        const Type* ret_type = r->field_at(TypeFunc::Parms);

        // Note:  we use isa_ptr() instead of isa_oopptr()  here because the
        //        _multianewarray functions return a TypeRawPtr.
        if (ret_type->isa_ptr() != NULL) {
          PointsToNode *ptadr = ptnode_adr(call->_idx);
          ptadr->set_node_type(PointsToNode::JavaObject);
          set_escape_state(call->_idx, PointsToNode::GlobalEscape);
          if (resproj != NULL)
            add_pointsto_edge(resproj->_idx, _phantom_object);
        }
      }
    }
  }
}

void ConnectionGraph::record_for_escape_analysis(Node *n) {
  if (_collecting) {
    if (n->is_Phi()) {
      PhiNode *phi = n->as_Phi();
      const Type *pt = phi->type();
      if ((pt->isa_oopptr() != NULL) || pt == TypePtr::NULL_PTR) {
        PointsToNode *ptn = ptnode_adr(phi->_idx);
        ptn->set_node_type(PointsToNode::LocalVar);
        ptn->_node = n;
        _deferred.push(n);
      }
    }
  }
}

void ConnectionGraph::record_escape_work(Node *n, PhaseTransform *phase) {

  int opc = n->Opcode();
  PointsToNode *ptadr = ptnode_adr(n->_idx);

  if (_processed.test(n->_idx))
    return;

  ptadr->_node = n;
  if (n->is_Call()) {
    CallNode *call = n->as_Call();
    process_call_arguments(call, phase);
    return;
  }

  switch (opc) {
    case Op_AddP:
    {
      Node *base = skip_casts(n->in(AddPNode::Base));
      ptadr->set_node_type(PointsToNode::Field);

      // create a field edge to this node from everything adr could point to
      VectorSet ptset(Thread::current()->resource_area());
      PointsTo(ptset, base, phase);
      for( VectorSetI i(&ptset); i.test(); ++i ) {
        uint pt = i.elem;
        add_field_edge(pt, n->_idx, type_to_offset(phase->type(n)));
      }
      break;
    }
    case Op_Parm:
    {
      ProjNode *nproj = n->as_Proj();
      uint con = nproj->_con;
      if (con < TypeFunc::Parms)
        return;
      const Type *t = nproj->in(0)->as_Start()->_domain->field_at(con);
      if (t->isa_ptr() == NULL)
        return;
      ptadr->set_node_type(PointsToNode::JavaObject);
      if (t->isa_oopptr() != NULL) {
        set_escape_state(n->_idx, PointsToNode::ArgEscape);
      } else {
        // this must be the incoming state of an OSR compile, we have to assume anything
        // passed in globally escapes
        assert(_compile->is_osr_compilation(), "bad argument type for non-osr compilation");
        set_escape_state(n->_idx, PointsToNode::GlobalEscape);
      }
      _processed.set(n->_idx);
      break;
    }
    case Op_Phi:
    {
      PhiNode *phi = n->as_Phi();
      if (phi->type()->isa_oopptr() == NULL)
        return;  // nothing to do if not an oop
      ptadr->set_node_type(PointsToNode::LocalVar);
      process_phi_escape(phi, phase);
      break;
    }
    case Op_CreateEx:
    {
      // assume that all exception objects globally escape
      ptadr->set_node_type(PointsToNode::JavaObject);
      set_escape_state(n->_idx, PointsToNode::GlobalEscape);
      _processed.set(n->_idx);
      break;
    }
    case Op_ConP:
    {
      const Type *t = phase->type(n);
      ptadr->set_node_type(PointsToNode::JavaObject);
      // assume all pointer constants globally escape except for null
      if (t == TypePtr::NULL_PTR)
        set_escape_state(n->_idx, PointsToNode::NoEscape);
      else
        set_escape_state(n->_idx, PointsToNode::GlobalEscape);
      _processed.set(n->_idx);
      break;
    }
    case Op_LoadKlass:
    {
      ptadr->set_node_type(PointsToNode::JavaObject);
      set_escape_state(n->_idx, PointsToNode::GlobalEscape);
      _processed.set(n->_idx);
      break;
    }
    case Op_LoadP:
    {
      const Type *t = phase->type(n);
      if (!t->isa_oopptr())
        return;
      ptadr->set_node_type(PointsToNode::LocalVar);
      set_escape_state(n->_idx, PointsToNode::UnknownEscape);

      Node *adr = skip_casts(n->in(MemNode::Address));
      const Type *adr_type = phase->type(adr);
      Node *adr_base = skip_casts((adr->Opcode() == Op_AddP) ? adr->in(AddPNode::Base) : adr);

      // For everything "adr" could point to, create a deferred edge from
      // this node to each field with the same offset as "adr_type"
      VectorSet ptset(Thread::current()->resource_area());
      PointsTo(ptset, adr_base, phase);
      // If ptset is empty, then this value must have been set outside
      // this method, so we add the phantom node
      if (ptset.Size() == 0)
        ptset.set(_phantom_object);
      for( VectorSetI i(&ptset); i.test(); ++i ) {
        uint pt = i.elem;
        add_deferred_edge_to_fields(n->_idx, pt, type_to_offset(adr_type));
      }
      break;
    }
    case Op_StoreP:
    case Op_StorePConditional:
    case Op_CompareAndSwapP:
    {
      Node *adr = n->in(MemNode::Address);
      Node *val = skip_casts(n->in(MemNode::ValueIn));
      const Type *adr_type = phase->type(adr);
      if (!adr_type->isa_oopptr())
        return;

      assert(adr->Opcode() == Op_AddP, "expecting an AddP");
      Node *adr_base = adr->in(AddPNode::Base);

      // For everything "adr_base" could point to, create a deferred edge to "val" from each field
      // with the same offset as "adr_type"
      VectorSet ptset(Thread::current()->resource_area());
      PointsTo(ptset, adr_base, phase);
      for( VectorSetI i(&ptset); i.test(); ++i ) {
        uint pt = i.elem;
        add_edge_from_fields(pt, val->_idx, type_to_offset(adr_type));
      }
      break;
    }
    case Op_Proj:
    {
      ProjNode *nproj = n->as_Proj();
      Node *n0 = nproj->in(0);
      // we are only interested in the result projection from a call
      if (nproj->_con == TypeFunc::Parms && n0->is_Call() ) {
        process_call_result(nproj, phase);
      }

      break;
    }
    case Op_CastPP:
    case Op_CheckCastPP:
    {
      ptadr->set_node_type(PointsToNode::LocalVar);
      int ti = n->in(1)->_idx;
      if (_nodes->at(ti).node_type() == PointsToNode::JavaObject) {
        add_pointsto_edge(n->_idx, ti);
      } else {
        add_deferred_edge(n->_idx, ti);
      }
      break;
    }
    default:
      ;
      // nothing to do
  }
}

void ConnectionGraph::record_escape(Node *n, PhaseTransform *phase) {
  if (_collecting)
    record_escape_work(n, phase);
}

#ifndef PRODUCT
void ConnectionGraph::dump() {
  PhaseGVN  *igvn = _compile->initial_gvn();
  bool first = true;

  for (uint ni = 0; ni < (uint)_nodes->length(); ni++) {
    PointsToNode *esp = _nodes->adr_at(ni);
    if (esp->node_type() == PointsToNode::UnknownType || esp->_node == NULL)
      continue;
    PointsToNode::EscapeState es = escape_state(esp->_node, igvn);
    if (es == PointsToNode::NoEscape || (Verbose &&
            (es != PointsToNode::UnknownEscape || esp->edge_count() != 0))) {
      // don't print null pointer node which almost every method has
      if (esp->_node->Opcode() != Op_ConP || igvn->type(esp->_node) != TypePtr::NULL_PTR) {
        if (first) {
          tty->print("======== Connection graph for ");
          C()->method()->print_short_name();
          tty->cr();
          first = false;
        }
        tty->print("%4d  ", ni);
        esp->dump();
      }
    }
  }
}
#endif
