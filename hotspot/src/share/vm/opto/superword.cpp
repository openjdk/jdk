/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

#include "incls/_precompiled.incl"
#include "incls/_superword.cpp.incl"

//
//                  S U P E R W O R D   T R A N S F O R M
//=============================================================================

//------------------------------SuperWord---------------------------
SuperWord::SuperWord(PhaseIdealLoop* phase) :
  _phase(phase),
  _igvn(phase->_igvn),
  _arena(phase->C->comp_arena()),
  _packset(arena(), 8,  0, NULL),         // packs for the current block
  _bb_idx(arena(), (int)(1.10 * phase->C->unique()), 0, 0), // node idx to index in bb
  _block(arena(), 8,  0, NULL),           // nodes in current block
  _data_entry(arena(), 8,  0, NULL),      // nodes with all inputs from outside
  _mem_slice_head(arena(), 8,  0, NULL),  // memory slice heads
  _mem_slice_tail(arena(), 8,  0, NULL),  // memory slice tails
  _node_info(arena(), 8,  0, SWNodeInfo::initial), // info needed per node
  _align_to_ref(NULL),                    // memory reference to align vectors to
  _disjoint_ptrs(arena(), 8,  0, OrderedPair::initial), // runtime disambiguated pointer pairs
  _dg(_arena),                            // dependence graph
  _visited(arena()),                      // visited node set
  _post_visited(arena()),                 // post visited node set
  _n_idx_list(arena(), 8),                // scratch list of (node,index) pairs
  _stk(arena(), 8, 0, NULL),              // scratch stack of nodes
  _nlist(arena(), 8, 0, NULL),            // scratch list of nodes
  _lpt(NULL),                             // loop tree node
  _lp(NULL),                              // LoopNode
  _bb(NULL),                              // basic block
  _iv(NULL)                               // induction var
{}

//------------------------------transform_loop---------------------------
void SuperWord::transform_loop(IdealLoopTree* lpt) {
  assert(lpt->_head->is_CountedLoop(), "must be");
  CountedLoopNode *cl = lpt->_head->as_CountedLoop();

  if (!cl->is_main_loop() ) return; // skip normal, pre, and post loops

  // Check for no control flow in body (other than exit)
  Node *cl_exit = cl->loopexit();
  if (cl_exit->in(0) != lpt->_head) return;

  // Make sure the are no extra control users of the loop backedge
  if (cl->back_control()->outcnt() != 1) {
    return;
  }

  // Check for pre-loop ending with CountedLoopEnd(Bool(Cmp(x,Opaque1(limit))))
  CountedLoopEndNode* pre_end = get_pre_loop_end(cl);
  if (pre_end == NULL) return;
  Node *pre_opaq1 = pre_end->limit();
  if (pre_opaq1->Opcode() != Op_Opaque1) return;

  // Do vectors exist on this architecture?
  if (vector_width_in_bytes() == 0) return;

  init(); // initialize data structures

  set_lpt(lpt);
  set_lp(cl);

 // For now, define one block which is the entire loop body
  set_bb(cl);

  assert(_packset.length() == 0, "packset must be empty");
  SLP_extract();
}

//------------------------------SLP_extract---------------------------
// Extract the superword level parallelism
//
// 1) A reverse post-order of nodes in the block is constructed.  By scanning
//    this list from first to last, all definitions are visited before their uses.
//
// 2) A point-to-point dependence graph is constructed between memory references.
//    This simplies the upcoming "independence" checker.
//
// 3) The maximum depth in the node graph from the beginning of the block
//    to each node is computed.  This is used to prune the graph search
//    in the independence checker.
//
// 4) For integer types, the necessary bit width is propagated backwards
//    from stores to allow packed operations on byte, char, and short
//    integers.  This reverses the promotion to type "int" that javac
//    did for operations like: char c1,c2,c3;  c1 = c2 + c3.
//
// 5) One of the memory references is picked to be an aligned vector reference.
//    The pre-loop trip count is adjusted to align this reference in the
//    unrolled body.
//
// 6) The initial set of pack pairs is seeded with memory references.
//
// 7) The set of pack pairs is extended by following use->def and def->use links.
//
// 8) The pairs are combined into vector sized packs.
//
// 9) Reorder the memory slices to co-locate members of the memory packs.
//
// 10) Generate ideal vector nodes for the final set of packs and where necessary,
//    inserting scalar promotion, vector creation from multiple scalars, and
//    extraction of scalar values from vectors.
//
void SuperWord::SLP_extract() {

  // Ready the block

  construct_bb();

  dependence_graph();

  compute_max_depth();

  compute_vector_element_type();

  // Attempt vectorization

  find_adjacent_refs();

  extend_packlist();

  combine_packs();

  construct_my_pack_map();

  filter_packs();

  schedule();

  output();
}

//------------------------------find_adjacent_refs---------------------------
// Find the adjacent memory references and create pack pairs for them.
// This is the initial set of packs that will then be extended by
// following use->def and def->use links.  The align positions are
// assigned relative to the reference "align_to_ref"
void SuperWord::find_adjacent_refs() {
  // Get list of memory operations
  Node_List memops;
  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    if (n->is_Mem() && in_bb(n) &&
        is_java_primitive(n->as_Mem()->memory_type())) {
      int align = memory_alignment(n->as_Mem(), 0);
      if (align != bottom_align) {
        memops.push(n);
      }
    }
  }
  if (memops.size() == 0) return;

  // Find a memory reference to align to.  The pre-loop trip count
  // is modified to align this reference to a vector-aligned address
  find_align_to_ref(memops);
  if (align_to_ref() == NULL) return;

  SWPointer align_to_ref_p(align_to_ref(), this);
  int offset = align_to_ref_p.offset_in_bytes();
  int scale  = align_to_ref_p.scale_in_bytes();
  int vw              = vector_width_in_bytes();
  int stride_sign     = (scale * iv_stride()) > 0 ? 1 : -1;
  int iv_adjustment   = (stride_sign * vw - (offset % vw)) % vw;

#ifndef PRODUCT
  if (TraceSuperWord)
    tty->print_cr("\noffset = %d iv_adjustment = %d  elt_align = %d scale = %d iv_stride = %d",
                  offset, iv_adjustment, align_to_ref_p.memory_size(), align_to_ref_p.scale_in_bytes(), iv_stride());
#endif

  // Set alignment relative to "align_to_ref"
  for (int i = memops.size() - 1; i >= 0; i--) {
    MemNode* s = memops.at(i)->as_Mem();
    SWPointer p2(s, this);
    if (p2.comparable(align_to_ref_p)) {
      int align = memory_alignment(s, iv_adjustment);
      set_alignment(s, align);
    } else {
      memops.remove(i);
    }
  }

  // Create initial pack pairs of memory operations
  for (uint i = 0; i < memops.size(); i++) {
    Node* s1 = memops.at(i);
    for (uint j = 0; j < memops.size(); j++) {
      Node* s2 = memops.at(j);
      if (s1 != s2 && are_adjacent_refs(s1, s2)) {
        int align = alignment(s1);
        if (stmts_can_pack(s1, s2, align)) {
          Node_List* pair = new Node_List();
          pair->push(s1);
          pair->push(s2);
          _packset.append(pair);
        }
      }
    }
  }

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print_cr("\nAfter find_adjacent_refs");
    print_packset();
  }
#endif
}

//------------------------------find_align_to_ref---------------------------
// Find a memory reference to align the loop induction variable to.
// Looks first at stores then at loads, looking for a memory reference
// with the largest number of references similar to it.
void SuperWord::find_align_to_ref(Node_List &memops) {
  GrowableArray<int> cmp_ct(arena(), memops.size(), memops.size(), 0);

  // Count number of comparable memory ops
  for (uint i = 0; i < memops.size(); i++) {
    MemNode* s1 = memops.at(i)->as_Mem();
    SWPointer p1(s1, this);
    // Discard if pre loop can't align this reference
    if (!ref_is_alignable(p1)) {
      *cmp_ct.adr_at(i) = 0;
      continue;
    }
    for (uint j = i+1; j < memops.size(); j++) {
      MemNode* s2 = memops.at(j)->as_Mem();
      if (isomorphic(s1, s2)) {
        SWPointer p2(s2, this);
        if (p1.comparable(p2)) {
          (*cmp_ct.adr_at(i))++;
          (*cmp_ct.adr_at(j))++;
        }
      }
    }
  }

  // Find Store (or Load) with the greatest number of "comparable" references
  int max_ct        = 0;
  int max_idx       = -1;
  int min_size      = max_jint;
  int min_iv_offset = max_jint;
  for (uint j = 0; j < memops.size(); j++) {
    MemNode* s = memops.at(j)->as_Mem();
    if (s->is_Store()) {
      SWPointer p(s, this);
      if (cmp_ct.at(j) > max_ct ||
          cmp_ct.at(j) == max_ct && (data_size(s) < min_size ||
                                     data_size(s) == min_size &&
                                        p.offset_in_bytes() < min_iv_offset)) {
        max_ct = cmp_ct.at(j);
        max_idx = j;
        min_size = data_size(s);
        min_iv_offset = p.offset_in_bytes();
      }
    }
  }
  // If no stores, look at loads
  if (max_ct == 0) {
    for (uint j = 0; j < memops.size(); j++) {
      MemNode* s = memops.at(j)->as_Mem();
      if (s->is_Load()) {
        SWPointer p(s, this);
        if (cmp_ct.at(j) > max_ct ||
            cmp_ct.at(j) == max_ct && (data_size(s) < min_size ||
                                       data_size(s) == min_size &&
                                          p.offset_in_bytes() < min_iv_offset)) {
          max_ct = cmp_ct.at(j);
          max_idx = j;
          min_size = data_size(s);
          min_iv_offset = p.offset_in_bytes();
        }
      }
    }
  }

  if (max_ct > 0)
    set_align_to_ref(memops.at(max_idx)->as_Mem());

#ifndef PRODUCT
  if (TraceSuperWord && Verbose) {
    tty->print_cr("\nVector memops after find_align_to_refs");
    for (uint i = 0; i < memops.size(); i++) {
      MemNode* s = memops.at(i)->as_Mem();
      s->dump();
    }
  }
#endif
}

//------------------------------ref_is_alignable---------------------------
// Can the preloop align the reference to position zero in the vector?
bool SuperWord::ref_is_alignable(SWPointer& p) {
  if (!p.has_iv()) {
    return true;   // no induction variable
  }
  CountedLoopEndNode* pre_end = get_pre_loop_end(lp()->as_CountedLoop());
  assert(pre_end->stride_is_con(), "pre loop stride is constant");
  int preloop_stride = pre_end->stride_con();

  int span = preloop_stride * p.scale_in_bytes();

  // Stride one accesses are alignable.
  if (ABS(span) == p.memory_size())
    return true;

  // If initial offset from start of object is computable,
  // compute alignment within the vector.
  int vw = vector_width_in_bytes();
  if (vw % span == 0) {
    Node* init_nd = pre_end->init_trip();
    if (init_nd->is_Con() && p.invar() == NULL) {
      int init = init_nd->bottom_type()->is_int()->get_con();

      int init_offset = init * p.scale_in_bytes() + p.offset_in_bytes();
      assert(init_offset >= 0, "positive offset from object start");

      if (span > 0) {
        return (vw - (init_offset % vw)) % span == 0;
      } else {
        assert(span < 0, "nonzero stride * scale");
        return (init_offset % vw) % -span == 0;
      }
    }
  }
  return false;
}

//---------------------------dependence_graph---------------------------
// Construct dependency graph.
// Add dependence edges to load/store nodes for memory dependence
//    A.out()->DependNode.in(1) and DependNode.out()->B.prec(x)
void SuperWord::dependence_graph() {
  // First, assign a dependence node to each memory node
  for (int i = 0; i < _block.length(); i++ ) {
    Node *n = _block.at(i);
    if (n->is_Mem() || n->is_Phi() && n->bottom_type() == Type::MEMORY) {
      _dg.make_node(n);
    }
  }

  // For each memory slice, create the dependences
  for (int i = 0; i < _mem_slice_head.length(); i++) {
    Node* n      = _mem_slice_head.at(i);
    Node* n_tail = _mem_slice_tail.at(i);

    // Get slice in predecessor order (last is first)
    mem_slice_preds(n_tail, n, _nlist);

    // Make the slice dependent on the root
    DepMem* slice = _dg.dep(n);
    _dg.make_edge(_dg.root(), slice);

    // Create a sink for the slice
    DepMem* slice_sink = _dg.make_node(NULL);
    _dg.make_edge(slice_sink, _dg.tail());

    // Now visit each pair of memory ops, creating the edges
    for (int j = _nlist.length() - 1; j >= 0 ; j--) {
      Node* s1 = _nlist.at(j);

      // If no dependency yet, use slice
      if (_dg.dep(s1)->in_cnt() == 0) {
        _dg.make_edge(slice, s1);
      }
      SWPointer p1(s1->as_Mem(), this);
      bool sink_dependent = true;
      for (int k = j - 1; k >= 0; k--) {
        Node* s2 = _nlist.at(k);
        if (s1->is_Load() && s2->is_Load())
          continue;
        SWPointer p2(s2->as_Mem(), this);

        int cmp = p1.cmp(p2);
        if (SuperWordRTDepCheck &&
            p1.base() != p2.base() && p1.valid() && p2.valid()) {
          // Create a runtime check to disambiguate
          OrderedPair pp(p1.base(), p2.base());
          _disjoint_ptrs.append_if_missing(pp);
        } else if (!SWPointer::not_equal(cmp)) {
          // Possibly same address
          _dg.make_edge(s1, s2);
          sink_dependent = false;
        }
      }
      if (sink_dependent) {
        _dg.make_edge(s1, slice_sink);
      }
    }
#ifndef PRODUCT
    if (TraceSuperWord) {
      tty->print_cr("\nDependence graph for slice: %d", n->_idx);
      for (int q = 0; q < _nlist.length(); q++) {
        _dg.print(_nlist.at(q));
      }
      tty->cr();
    }
#endif
    _nlist.clear();
  }

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print_cr("\ndisjoint_ptrs: %s", _disjoint_ptrs.length() > 0 ? "" : "NONE");
    for (int r = 0; r < _disjoint_ptrs.length(); r++) {
      _disjoint_ptrs.at(r).print();
      tty->cr();
    }
    tty->cr();
  }
#endif
}

//---------------------------mem_slice_preds---------------------------
// Return a memory slice (node list) in predecessor order starting at "start"
void SuperWord::mem_slice_preds(Node* start, Node* stop, GrowableArray<Node*> &preds) {
  assert(preds.length() == 0, "start empty");
  Node* n = start;
  Node* prev = NULL;
  while (true) {
    assert(in_bb(n), "must be in block");
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* out = n->fast_out(i);
      if (out->is_Load()) {
        if (in_bb(out)) {
          preds.push(out);
        }
      } else {
        // FIXME
        if (out->is_MergeMem() && !in_bb(out)) {
          // Either unrolling is causing a memory edge not to disappear,
          // or need to run igvn.optimize() again before SLP
        } else if (out->is_Phi() && out->bottom_type() == Type::MEMORY && !in_bb(out)) {
          // Ditto.  Not sure what else to check further.
        } else if (out->Opcode() == Op_StoreCM && out->in(4) == n) {
          // StoreCM has an input edge used as a precedence edge.
          // Maybe an issue when oop stores are vectorized.
        } else {
          assert(out == prev || prev == NULL, "no branches off of store slice");
        }
      }
    }
    if (n == stop) break;
    preds.push(n);
    prev = n;
    n = n->in(MemNode::Memory);
  }
}

//------------------------------stmts_can_pack---------------------------
// Can s1 and s2 be in a pack with s1 immediately preceeding s2 and
// s1 aligned at "align"
bool SuperWord::stmts_can_pack(Node* s1, Node* s2, int align) {
  if (isomorphic(s1, s2)) {
    if (independent(s1, s2)) {
      if (!exists_at(s1, 0) && !exists_at(s2, 1)) {
        if (!s1->is_Mem() || are_adjacent_refs(s1, s2)) {
          int s1_align = alignment(s1);
          int s2_align = alignment(s2);
          if (s1_align == top_align || s1_align == align) {
            if (s2_align == top_align || s2_align == align + data_size(s1)) {
              return true;
            }
          }
        }
      }
    }
  }
  return false;
}

//------------------------------exists_at---------------------------
// Does s exist in a pack at position pos?
bool SuperWord::exists_at(Node* s, uint pos) {
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    if (p->at(pos) == s) {
      return true;
    }
  }
  return false;
}

//------------------------------are_adjacent_refs---------------------------
// Is s1 immediately before s2 in memory?
bool SuperWord::are_adjacent_refs(Node* s1, Node* s2) {
  if (!s1->is_Mem() || !s2->is_Mem()) return false;
  if (!in_bb(s1)    || !in_bb(s2))    return false;
  // FIXME - co_locate_pack fails on Stores in different mem-slices, so
  // only pack memops that are in the same alias set until that's fixed.
  if (_phase->C->get_alias_index(s1->as_Mem()->adr_type()) !=
      _phase->C->get_alias_index(s2->as_Mem()->adr_type()))
    return false;
  SWPointer p1(s1->as_Mem(), this);
  SWPointer p2(s2->as_Mem(), this);
  if (p1.base() != p2.base() || !p1.comparable(p2)) return false;
  int diff = p2.offset_in_bytes() - p1.offset_in_bytes();
  return diff == data_size(s1);
}

//------------------------------isomorphic---------------------------
// Are s1 and s2 similar?
bool SuperWord::isomorphic(Node* s1, Node* s2) {
  if (s1->Opcode() != s2->Opcode()) return false;
  if (s1->req() != s2->req()) return false;
  if (s1->in(0) != s2->in(0)) return false;
  if (velt_type(s1) != velt_type(s2)) return false;
  return true;
}

//------------------------------independent---------------------------
// Is there no data path from s1 to s2 or s2 to s1?
bool SuperWord::independent(Node* s1, Node* s2) {
  //  assert(s1->Opcode() == s2->Opcode(), "check isomorphic first");
  int d1 = depth(s1);
  int d2 = depth(s2);
  if (d1 == d2) return s1 != s2;
  Node* deep    = d1 > d2 ? s1 : s2;
  Node* shallow = d1 > d2 ? s2 : s1;

  visited_clear();

  return independent_path(shallow, deep);
}

//------------------------------independent_path------------------------------
// Helper for independent
bool SuperWord::independent_path(Node* shallow, Node* deep, uint dp) {
  if (dp >= 1000) return false; // stop deep recursion
  visited_set(deep);
  int shal_depth = depth(shallow);
  assert(shal_depth <= depth(deep), "must be");
  for (DepPreds preds(deep, _dg); !preds.done(); preds.next()) {
    Node* pred = preds.current();
    if (in_bb(pred) && !visited_test(pred)) {
      if (shallow == pred) {
        return false;
      }
      if (shal_depth < depth(pred) && !independent_path(shallow, pred, dp+1)) {
        return false;
      }
    }
  }
  return true;
}

//------------------------------set_alignment---------------------------
void SuperWord::set_alignment(Node* s1, Node* s2, int align) {
  set_alignment(s1, align);
  set_alignment(s2, align + data_size(s1));
}

//------------------------------data_size---------------------------
int SuperWord::data_size(Node* s) {
  const Type* t = velt_type(s);
  BasicType  bt = t->array_element_basic_type();
  int bsize = type2aelembytes(bt);
  assert(bsize != 0, "valid size");
  return bsize;
}

//------------------------------extend_packlist---------------------------
// Extend packset by following use->def and def->use links from pack members.
void SuperWord::extend_packlist() {
  bool changed;
  do {
    changed = false;
    for (int i = 0; i < _packset.length(); i++) {
      Node_List* p = _packset.at(i);
      changed |= follow_use_defs(p);
      changed |= follow_def_uses(p);
    }
  } while (changed);

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print_cr("\nAfter extend_packlist");
    print_packset();
  }
#endif
}

//------------------------------follow_use_defs---------------------------
// Extend the packset by visiting operand definitions of nodes in pack p
bool SuperWord::follow_use_defs(Node_List* p) {
  Node* s1 = p->at(0);
  Node* s2 = p->at(1);
  assert(p->size() == 2, "just checking");
  assert(s1->req() == s2->req(), "just checking");
  assert(alignment(s1) + data_size(s1) == alignment(s2), "just checking");

  if (s1->is_Load()) return false;

  int align = alignment(s1);
  bool changed = false;
  int start = s1->is_Store() ? MemNode::ValueIn   : 1;
  int end   = s1->is_Store() ? MemNode::ValueIn+1 : s1->req();
  for (int j = start; j < end; j++) {
    Node* t1 = s1->in(j);
    Node* t2 = s2->in(j);
    if (!in_bb(t1) || !in_bb(t2))
      continue;
    if (stmts_can_pack(t1, t2, align)) {
      if (est_savings(t1, t2) >= 0) {
        Node_List* pair = new Node_List();
        pair->push(t1);
        pair->push(t2);
        _packset.append(pair);
        set_alignment(t1, t2, align);
        changed = true;
      }
    }
  }
  return changed;
}

//------------------------------follow_def_uses---------------------------
// Extend the packset by visiting uses of nodes in pack p
bool SuperWord::follow_def_uses(Node_List* p) {
  bool changed = false;
  Node* s1 = p->at(0);
  Node* s2 = p->at(1);
  assert(p->size() == 2, "just checking");
  assert(s1->req() == s2->req(), "just checking");
  assert(alignment(s1) + data_size(s1) == alignment(s2), "just checking");

  if (s1->is_Store()) return false;

  int align = alignment(s1);
  int savings = -1;
  Node* u1 = NULL;
  Node* u2 = NULL;
  for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
    Node* t1 = s1->fast_out(i);
    if (!in_bb(t1)) continue;
    for (DUIterator_Fast jmax, j = s2->fast_outs(jmax); j < jmax; j++) {
      Node* t2 = s2->fast_out(j);
      if (!in_bb(t2)) continue;
      if (!opnd_positions_match(s1, t1, s2, t2))
        continue;
      if (stmts_can_pack(t1, t2, align)) {
        int my_savings = est_savings(t1, t2);
        if (my_savings > savings) {
          savings = my_savings;
          u1 = t1;
          u2 = t2;
        }
      }
    }
  }
  if (savings >= 0) {
    Node_List* pair = new Node_List();
    pair->push(u1);
    pair->push(u2);
    _packset.append(pair);
    set_alignment(u1, u2, align);
    changed = true;
  }
  return changed;
}

//---------------------------opnd_positions_match-------------------------
// Is the use of d1 in u1 at the same operand position as d2 in u2?
bool SuperWord::opnd_positions_match(Node* d1, Node* u1, Node* d2, Node* u2) {
  uint ct = u1->req();
  if (ct != u2->req()) return false;
  uint i1 = 0;
  uint i2 = 0;
  do {
    for (i1++; i1 < ct; i1++) if (u1->in(i1) == d1) break;
    for (i2++; i2 < ct; i2++) if (u2->in(i2) == d2) break;
    if (i1 != i2) {
      return false;
    }
  } while (i1 < ct);
  return true;
}

//------------------------------est_savings---------------------------
// Estimate the savings from executing s1 and s2 as a pack
int SuperWord::est_savings(Node* s1, Node* s2) {
  int save = 2 - 1; // 2 operations per instruction in packed form

  // inputs
  for (uint i = 1; i < s1->req(); i++) {
    Node* x1 = s1->in(i);
    Node* x2 = s2->in(i);
    if (x1 != x2) {
      if (are_adjacent_refs(x1, x2)) {
        save += adjacent_profit(x1, x2);
      } else if (!in_packset(x1, x2)) {
        save -= pack_cost(2);
      } else {
        save += unpack_cost(2);
      }
    }
  }

  // uses of result
  uint ct = 0;
  for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
    Node* s1_use = s1->fast_out(i);
    for (int j = 0; j < _packset.length(); j++) {
      Node_List* p = _packset.at(j);
      if (p->at(0) == s1_use) {
        for (DUIterator_Fast kmax, k = s2->fast_outs(kmax); k < kmax; k++) {
          Node* s2_use = s2->fast_out(k);
          if (p->at(p->size()-1) == s2_use) {
            ct++;
            if (are_adjacent_refs(s1_use, s2_use)) {
              save += adjacent_profit(s1_use, s2_use);
            }
          }
        }
      }
    }
  }

  if (ct < s1->outcnt()) save += unpack_cost(1);
  if (ct < s2->outcnt()) save += unpack_cost(1);

  return save;
}

//------------------------------costs---------------------------
int SuperWord::adjacent_profit(Node* s1, Node* s2) { return 2; }
int SuperWord::pack_cost(int ct)   { return ct; }
int SuperWord::unpack_cost(int ct) { return ct; }

//------------------------------combine_packs---------------------------
// Combine packs A and B with A.last == B.first into A.first..,A.last,B.second,..B.last
void SuperWord::combine_packs() {
  bool changed;
  do {
    changed = false;
    for (int i = 0; i < _packset.length(); i++) {
      Node_List* p1 = _packset.at(i);
      if (p1 == NULL) continue;
      for (int j = 0; j < _packset.length(); j++) {
        Node_List* p2 = _packset.at(j);
        if (p2 == NULL) continue;
        if (p1->at(p1->size()-1) == p2->at(0)) {
          for (uint k = 1; k < p2->size(); k++) {
            p1->push(p2->at(k));
          }
          _packset.at_put(j, NULL);
          changed = true;
        }
      }
    }
  } while (changed);

  for (int i = _packset.length() - 1; i >= 0; i--) {
    Node_List* p1 = _packset.at(i);
    if (p1 == NULL) {
      _packset.remove_at(i);
    }
  }

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print_cr("\nAfter combine_packs");
    print_packset();
  }
#endif
}

//-----------------------------construct_my_pack_map--------------------------
// Construct the map from nodes to packs.  Only valid after the
// point where a node is only in one pack (after combine_packs).
void SuperWord::construct_my_pack_map() {
  Node_List* rslt = NULL;
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    for (uint j = 0; j < p->size(); j++) {
      Node* s = p->at(j);
      assert(my_pack(s) == NULL, "only in one pack");
      set_my_pack(s, p);
    }
  }
}

//------------------------------filter_packs---------------------------
// Remove packs that are not implemented or not profitable.
void SuperWord::filter_packs() {

  // Remove packs that are not implemented
  for (int i = _packset.length() - 1; i >= 0; i--) {
    Node_List* pk = _packset.at(i);
    bool impl = implemented(pk);
    if (!impl) {
#ifndef PRODUCT
      if (TraceSuperWord && Verbose) {
        tty->print_cr("Unimplemented");
        pk->at(0)->dump();
      }
#endif
      remove_pack_at(i);
    }
  }

  // Remove packs that are not profitable
  bool changed;
  do {
    changed = false;
    for (int i = _packset.length() - 1; i >= 0; i--) {
      Node_List* pk = _packset.at(i);
      bool prof = profitable(pk);
      if (!prof) {
#ifndef PRODUCT
        if (TraceSuperWord && Verbose) {
          tty->print_cr("Unprofitable");
          pk->at(0)->dump();
        }
#endif
        remove_pack_at(i);
        changed = true;
      }
    }
  } while (changed);

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print_cr("\nAfter filter_packs");
    print_packset();
    tty->cr();
  }
#endif
}

//------------------------------implemented---------------------------
// Can code be generated for pack p?
bool SuperWord::implemented(Node_List* p) {
  Node* p0 = p->at(0);
  int vopc = VectorNode::opcode(p0->Opcode(), p->size(), velt_type(p0));
  return vopc > 0 && Matcher::has_match_rule(vopc);
}

//------------------------------profitable---------------------------
// For pack p, are all operands and all uses (with in the block) vector?
bool SuperWord::profitable(Node_List* p) {
  Node* p0 = p->at(0);
  uint start, end;
  vector_opd_range(p0, &start, &end);

  // Return false if some input is not vector and inside block
  for (uint i = start; i < end; i++) {
    if (!is_vector_use(p0, i)) {
      // For now, return false if not scalar promotion case (inputs are the same.)
      // Later, implement PackNode and allow differring, non-vector inputs
      // (maybe just the ones from outside the block.)
      Node* p0_def = p0->in(i);
      for (uint j = 1; j < p->size(); j++) {
        Node* use = p->at(j);
        Node* def = use->in(i);
        if (p0_def != def)
          return false;
      }
    }
  }
  if (!p0->is_Store()) {
    // For now, return false if not all uses are vector.
    // Later, implement ExtractNode and allow non-vector uses (maybe
    // just the ones outside the block.)
    for (uint i = 0; i < p->size(); i++) {
      Node* def = p->at(i);
      for (DUIterator_Fast jmax, j = def->fast_outs(jmax); j < jmax; j++) {
        Node* use = def->fast_out(j);
        for (uint k = 0; k < use->req(); k++) {
          Node* n = use->in(k);
          if (def == n) {
            if (!is_vector_use(use, k)) {
              return false;
            }
          }
        }
      }
    }
  }
  return true;
}

//------------------------------schedule---------------------------
// Adjust the memory graph for the packed operations
void SuperWord::schedule() {

  // Co-locate in the memory graph the members of each memory pack
  for (int i = 0; i < _packset.length(); i++) {
    co_locate_pack(_packset.at(i));
  }
}

//------------------------------co_locate_pack---------------------------
// Within a pack, move stores down to the last executed store,
// and move loads up to the first executed load.
void SuperWord::co_locate_pack(Node_List* pk) {
  if (pk->at(0)->is_Store()) {
    // Push Stores down towards last executed pack member
    MemNode* first     = executed_first(pk)->as_Mem();
    MemNode* last      = executed_last(pk)->as_Mem();
    MemNode* insert_pt = last;
    MemNode* current   = last->in(MemNode::Memory)->as_Mem();
    while (true) {
      assert(in_bb(current), "stay in block");
      Node* my_mem = current->in(MemNode::Memory);
      if (in_pack(current, pk)) {
        // Forward users of my memory state to my input memory state
        _igvn.hash_delete(current);
        _igvn.hash_delete(my_mem);
        for (DUIterator i = current->outs(); current->has_out(i); i++) {
          Node* use = current->out(i);
          if (use->is_Mem()) {
            assert(use->in(MemNode::Memory) == current, "must be");
            _igvn.hash_delete(use);
            use->set_req(MemNode::Memory, my_mem);
            _igvn._worklist.push(use);
            --i; // deleted this edge; rescan position
          }
        }
        // put current immediately before insert_pt
        current->set_req(MemNode::Memory, insert_pt->in(MemNode::Memory));
        _igvn.hash_delete(insert_pt);
        insert_pt->set_req(MemNode::Memory, current);
        _igvn._worklist.push(insert_pt);
        _igvn._worklist.push(current);
        insert_pt = current;
      }
      if (current == first) break;
      current = my_mem->as_Mem();
    }
  } else if (pk->at(0)->is_Load()) {
    // Pull Loads up towards first executed pack member
    LoadNode* first = executed_first(pk)->as_Load();
    Node* first_mem = first->in(MemNode::Memory);
    _igvn.hash_delete(first_mem);
    // Give each load same memory state as first
    for (uint i = 0; i < pk->size(); i++) {
      LoadNode* ld = pk->at(i)->as_Load();
      _igvn.hash_delete(ld);
      ld->set_req(MemNode::Memory, first_mem);
      _igvn._worklist.push(ld);
    }
  }
}

//------------------------------output---------------------------
// Convert packs into vector node operations
void SuperWord::output() {
  if (_packset.length() == 0) return;

  // MUST ENSURE main loop's initial value is properly aligned:
  //  (iv_initial_value + min_iv_offset) % vector_width_in_bytes() == 0

  align_initial_loop_index(align_to_ref());

  // Insert extract (unpack) operations for scalar uses
  for (int i = 0; i < _packset.length(); i++) {
    insert_extracts(_packset.at(i));
  }

  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    Node_List* p = my_pack(n);
    if (p && n == executed_last(p)) {
      uint vlen = p->size();
      Node* vn = NULL;
      Node* low_adr = p->at(0);
      Node* first   = executed_first(p);
      if (n->is_Load()) {
        int   opc = n->Opcode();
        Node* ctl = n->in(MemNode::Control);
        Node* mem = first->in(MemNode::Memory);
        Node* adr = low_adr->in(MemNode::Address);
        const TypePtr* atyp = n->adr_type();
        vn = VectorLoadNode::make(_phase->C, opc, ctl, mem, adr, atyp, vlen);

      } else if (n->is_Store()) {
        // Promote value to be stored to vector
        VectorNode* val = vector_opd(p, MemNode::ValueIn);

        int   opc = n->Opcode();
        Node* ctl = n->in(MemNode::Control);
        Node* mem = first->in(MemNode::Memory);
        Node* adr = low_adr->in(MemNode::Address);
        const TypePtr* atyp = n->adr_type();
        vn = VectorStoreNode::make(_phase->C, opc, ctl, mem, adr, atyp, val, vlen);

      } else if (n->req() == 3) {
        // Promote operands to vector
        Node* in1 = vector_opd(p, 1);
        Node* in2 = vector_opd(p, 2);
        vn = VectorNode::make(_phase->C, n->Opcode(), in1, in2, vlen, velt_type(n));

      } else {
        ShouldNotReachHere();
      }

      _phase->_igvn.register_new_node_with_optimizer(vn);
      _phase->set_ctrl(vn, _phase->get_ctrl(p->at(0)));
      for (uint j = 0; j < p->size(); j++) {
        Node* pm = p->at(j);
        _igvn.hash_delete(pm);
        _igvn.subsume_node(pm, vn);
      }
      _igvn._worklist.push(vn);
    }
  }
}

//------------------------------vector_opd---------------------------
// Create a vector operand for the nodes in pack p for operand: in(opd_idx)
VectorNode* SuperWord::vector_opd(Node_List* p, int opd_idx) {
  Node* p0 = p->at(0);
  uint vlen = p->size();
  Node* opd = p0->in(opd_idx);

  bool same_opd = true;
  for (uint i = 1; i < vlen; i++) {
    Node* pi = p->at(i);
    Node* in = pi->in(opd_idx);
    if (opd != in) {
      same_opd = false;
      break;
    }
  }

  if (same_opd) {
    if (opd->is_Vector()) {
      return (VectorNode*)opd; // input is matching vector
    }
    // Convert scalar input to vector. Use p0's type because it's container
    // maybe smaller than the operand's container.
    const Type* opd_t = velt_type(!in_bb(opd) ? p0 : opd);
    const Type* p0_t  = velt_type(p0);
    if (p0_t->higher_equal(opd_t)) opd_t = p0_t;
    VectorNode* vn    = VectorNode::scalar2vector(_phase->C, opd, vlen, opd_t);

    _phase->_igvn.register_new_node_with_optimizer(vn);
    _phase->set_ctrl(vn, _phase->get_ctrl(opd));
    return vn;
  }

  // Insert pack operation
  const Type* opd_t = velt_type(!in_bb(opd) ? p0 : opd);
  PackNode* pk = PackNode::make(_phase->C, opd, opd_t);

  for (uint i = 1; i < vlen; i++) {
    Node* pi = p->at(i);
    Node* in = pi->in(opd_idx);
    assert(my_pack(in) == NULL, "Should already have been unpacked");
    assert(opd_t == velt_type(!in_bb(in) ? pi : in), "all same type");
    pk->add_opd(in);
  }
  _phase->_igvn.register_new_node_with_optimizer(pk);
  _phase->set_ctrl(pk, _phase->get_ctrl(opd));
  return pk;
}

//------------------------------insert_extracts---------------------------
// If a use of pack p is not a vector use, then replace the
// use with an extract operation.
void SuperWord::insert_extracts(Node_List* p) {
  if (p->at(0)->is_Store()) return;
  assert(_n_idx_list.is_empty(), "empty (node,index) list");

  // Inspect each use of each pack member.  For each use that is
  // not a vector use, replace the use with an extract operation.

  for (uint i = 0; i < p->size(); i++) {
    Node* def = p->at(i);
    for (DUIterator_Fast jmax, j = def->fast_outs(jmax); j < jmax; j++) {
      Node* use = def->fast_out(j);
      for (uint k = 0; k < use->req(); k++) {
        Node* n = use->in(k);
        if (def == n) {
          if (!is_vector_use(use, k)) {
            _n_idx_list.push(use, k);
          }
        }
      }
    }
  }

  while (_n_idx_list.is_nonempty()) {
    Node* use = _n_idx_list.node();
    int   idx = _n_idx_list.index();
    _n_idx_list.pop();
    Node* def = use->in(idx);

    // Insert extract operation
    _igvn.hash_delete(def);
    _igvn.hash_delete(use);
    int def_pos = alignment(def) / data_size(def);
    const Type* def_t = velt_type(def);

    Node* ex = ExtractNode::make(_phase->C, def, def_pos, def_t);
    _phase->_igvn.register_new_node_with_optimizer(ex);
    _phase->set_ctrl(ex, _phase->get_ctrl(def));
    use->set_req(idx, ex);
    _igvn._worklist.push(def);
    _igvn._worklist.push(use);

    bb_insert_after(ex, bb_idx(def));
    set_velt_type(ex, def_t);
  }
}

//------------------------------is_vector_use---------------------------
// Is use->in(u_idx) a vector use?
bool SuperWord::is_vector_use(Node* use, int u_idx) {
  Node_List* u_pk = my_pack(use);
  if (u_pk == NULL) return false;
  Node* def = use->in(u_idx);
  Node_List* d_pk = my_pack(def);
  if (d_pk == NULL) {
    // check for scalar promotion
    Node* n = u_pk->at(0)->in(u_idx);
    for (uint i = 1; i < u_pk->size(); i++) {
      if (u_pk->at(i)->in(u_idx) != n) return false;
    }
    return true;
  }
  if (u_pk->size() != d_pk->size())
    return false;
  for (uint i = 0; i < u_pk->size(); i++) {
    Node* ui = u_pk->at(i);
    Node* di = d_pk->at(i);
    if (ui->in(u_idx) != di || alignment(ui) != alignment(di))
      return false;
  }
  return true;
}

//------------------------------construct_bb---------------------------
// Construct reverse postorder list of block members
void SuperWord::construct_bb() {
  Node* entry = bb();

  assert(_stk.length() == 0,            "stk is empty");
  assert(_block.length() == 0,          "block is empty");
  assert(_data_entry.length() == 0,     "data_entry is empty");
  assert(_mem_slice_head.length() == 0, "mem_slice_head is empty");
  assert(_mem_slice_tail.length() == 0, "mem_slice_tail is empty");

  // Find non-control nodes with no inputs from within block,
  // create a temporary map from node _idx to bb_idx for use
  // by the visited and post_visited sets,
  // and count number of nodes in block.
  int bb_ct = 0;
  for (uint i = 0; i < lpt()->_body.size(); i++ ) {
    Node *n = lpt()->_body.at(i);
    set_bb_idx(n, i); // Create a temporary map
    if (in_bb(n)) {
      bb_ct++;
      if (!n->is_CFG()) {
        bool found = false;
        for (uint j = 0; j < n->req(); j++) {
          Node* def = n->in(j);
          if (def && in_bb(def)) {
            found = true;
            break;
          }
        }
        if (!found) {
          assert(n != entry, "can't be entry");
          _data_entry.push(n);
        }
      }
    }
  }

  // Find memory slices (head and tail)
  for (DUIterator_Fast imax, i = lp()->fast_outs(imax); i < imax; i++) {
    Node *n = lp()->fast_out(i);
    if (in_bb(n) && (n->is_Phi() && n->bottom_type() == Type::MEMORY)) {
      Node* n_tail  = n->in(LoopNode::LoopBackControl);
      if (n_tail != n->in(LoopNode::EntryControl)) {
        _mem_slice_head.push(n);
        _mem_slice_tail.push(n_tail);
      }
    }
  }

  // Create an RPO list of nodes in block

  visited_clear();
  post_visited_clear();

  // Push all non-control nodes with no inputs from within block, then control entry
  for (int j = 0; j < _data_entry.length(); j++) {
    Node* n = _data_entry.at(j);
    visited_set(n);
    _stk.push(n);
  }
  visited_set(entry);
  _stk.push(entry);

  // Do a depth first walk over out edges
  int rpo_idx = bb_ct - 1;
  int size;
  while ((size = _stk.length()) > 0) {
    Node* n = _stk.top(); // Leave node on stack
    if (!visited_test_set(n)) {
      // forward arc in graph
    } else if (!post_visited_test(n)) {
      // cross or back arc
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        Node *use = n->fast_out(i);
        if (in_bb(use) && !visited_test(use) &&
            // Don't go around backedge
            (!use->is_Phi() || n == entry)) {
          _stk.push(use);
        }
      }
      if (_stk.length() == size) {
        // There were no additional uses, post visit node now
        _stk.pop(); // Remove node from stack
        assert(rpo_idx >= 0, "");
        _block.at_put_grow(rpo_idx, n);
        rpo_idx--;
        post_visited_set(n);
        assert(rpo_idx >= 0 || _stk.is_empty(), "");
      }
    } else {
      _stk.pop(); // Remove post-visited node from stack
    }
  }

  // Create real map of block indices for nodes
  for (int j = 0; j < _block.length(); j++) {
    Node* n = _block.at(j);
    set_bb_idx(n, j);
  }

  initialize_bb(); // Ensure extra info is allocated.

#ifndef PRODUCT
  if (TraceSuperWord) {
    print_bb();
    tty->print_cr("\ndata entry nodes: %s", _data_entry.length() > 0 ? "" : "NONE");
    for (int m = 0; m < _data_entry.length(); m++) {
      tty->print("%3d ", m);
      _data_entry.at(m)->dump();
    }
    tty->print_cr("\nmemory slices: %s", _mem_slice_head.length() > 0 ? "" : "NONE");
    for (int m = 0; m < _mem_slice_head.length(); m++) {
      tty->print("%3d ", m); _mem_slice_head.at(m)->dump();
      tty->print("    ");    _mem_slice_tail.at(m)->dump();
    }
  }
#endif
  assert(rpo_idx == -1 && bb_ct == _block.length(), "all block members found");
}

//------------------------------initialize_bb---------------------------
// Initialize per node info
void SuperWord::initialize_bb() {
  Node* last = _block.at(_block.length() - 1);
  grow_node_info(bb_idx(last));
}

//------------------------------bb_insert_after---------------------------
// Insert n into block after pos
void SuperWord::bb_insert_after(Node* n, int pos) {
  int n_pos = pos + 1;
  // Make room
  for (int i = _block.length() - 1; i >= n_pos; i--) {
    _block.at_put_grow(i+1, _block.at(i));
  }
  for (int j = _node_info.length() - 1; j >= n_pos; j--) {
    _node_info.at_put_grow(j+1, _node_info.at(j));
  }
  // Set value
  _block.at_put_grow(n_pos, n);
  _node_info.at_put_grow(n_pos, SWNodeInfo::initial);
  // Adjust map from node->_idx to _block index
  for (int i = n_pos; i < _block.length(); i++) {
    set_bb_idx(_block.at(i), i);
  }
}

//------------------------------compute_max_depth---------------------------
// Compute max depth for expressions from beginning of block
// Use to prune search paths during test for independence.
void SuperWord::compute_max_depth() {
  int ct = 0;
  bool again;
  do {
    again = false;
    for (int i = 0; i < _block.length(); i++) {
      Node* n = _block.at(i);
      if (!n->is_Phi()) {
        int d_orig = depth(n);
        int d_in   = 0;
        for (DepPreds preds(n, _dg); !preds.done(); preds.next()) {
          Node* pred = preds.current();
          if (in_bb(pred)) {
            d_in = MAX2(d_in, depth(pred));
          }
        }
        if (d_in + 1 != d_orig) {
          set_depth(n, d_in + 1);
          again = true;
        }
      }
    }
    ct++;
  } while (again);
#ifndef PRODUCT
  if (TraceSuperWord && Verbose)
    tty->print_cr("compute_max_depth iterated: %d times", ct);
#endif
}

//-------------------------compute_vector_element_type-----------------------
// Compute necessary vector element type for expressions
// This propagates backwards a narrower integer type when the
// upper bits of the value are not needed.
// Example:  char a,b,c;  a = b + c;
// Normally the type of the add is integer, but for packed character
// operations the type of the add needs to be char.
void SuperWord::compute_vector_element_type() {
#ifndef PRODUCT
  if (TraceSuperWord && Verbose)
    tty->print_cr("\ncompute_velt_type:");
#endif

  // Initial type
  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    const Type* t  = n->is_Mem() ? Type::get_const_basic_type(n->as_Mem()->memory_type())
                                 : _igvn.type(n);
    const Type* vt = container_type(t);
    set_velt_type(n, vt);
  }

  // Propagate narrowed type backwards through operations
  // that don't depend on higher order bits
  for (int i = _block.length() - 1; i >= 0; i--) {
    Node* n = _block.at(i);
    // Only integer types need be examined
    if (n->bottom_type()->isa_int()) {
      uint start, end;
      vector_opd_range(n, &start, &end);
      const Type* vt = velt_type(n);

      for (uint j = start; j < end; j++) {
        Node* in  = n->in(j);
        // Don't propagate through a type conversion
        if (n->bottom_type() != in->bottom_type())
          continue;
        switch(in->Opcode()) {
        case Op_AddI:    case Op_AddL:
        case Op_SubI:    case Op_SubL:
        case Op_MulI:    case Op_MulL:
        case Op_AndI:    case Op_AndL:
        case Op_OrI:     case Op_OrL:
        case Op_XorI:    case Op_XorL:
        case Op_LShiftI: case Op_LShiftL:
        case Op_CMoveI:  case Op_CMoveL:
          if (in_bb(in)) {
            bool same_type = true;
            for (DUIterator_Fast kmax, k = in->fast_outs(kmax); k < kmax; k++) {
              Node *use = in->fast_out(k);
              if (!in_bb(use) || velt_type(use) != vt) {
                same_type = false;
                break;
              }
            }
            if (same_type) {
              set_velt_type(in, vt);
            }
          }
        }
      }
    }
  }
#ifndef PRODUCT
  if (TraceSuperWord && Verbose) {
    for (int i = 0; i < _block.length(); i++) {
      Node* n = _block.at(i);
      velt_type(n)->dump();
      tty->print("\t");
      n->dump();
    }
  }
#endif
}

//------------------------------memory_alignment---------------------------
// Alignment within a vector memory reference
int SuperWord::memory_alignment(MemNode* s, int iv_adjust_in_bytes) {
  SWPointer p(s, this);
  if (!p.valid()) {
    return bottom_align;
  }
  int offset  = p.offset_in_bytes();
  offset     += iv_adjust_in_bytes;
  int off_rem = offset % vector_width_in_bytes();
  int off_mod = off_rem >= 0 ? off_rem : off_rem + vector_width_in_bytes();
  return off_mod;
}

//---------------------------container_type---------------------------
// Smallest type containing range of values
const Type* SuperWord::container_type(const Type* t) {
  const Type* tp = t->make_ptr();
  if (tp && tp->isa_aryptr()) {
    t = tp->is_aryptr()->elem();
  }
  if (t->basic_type() == T_INT) {
    if (t->higher_equal(TypeInt::BOOL))  return TypeInt::BOOL;
    if (t->higher_equal(TypeInt::BYTE))  return TypeInt::BYTE;
    if (t->higher_equal(TypeInt::CHAR))  return TypeInt::CHAR;
    if (t->higher_equal(TypeInt::SHORT)) return TypeInt::SHORT;
    return TypeInt::INT;
  }
  return t;
}

//-------------------------vector_opd_range-----------------------
// (Start, end] half-open range defining which operands are vector
void SuperWord::vector_opd_range(Node* n, uint* start, uint* end) {
  switch (n->Opcode()) {
  case Op_LoadB:   case Op_LoadUS:
  case Op_LoadI:   case Op_LoadL:
  case Op_LoadF:   case Op_LoadD:
  case Op_LoadP:
    *start = 0;
    *end   = 0;
    return;
  case Op_StoreB:  case Op_StoreC:
  case Op_StoreI:  case Op_StoreL:
  case Op_StoreF:  case Op_StoreD:
  case Op_StoreP:
    *start = MemNode::ValueIn;
    *end   = *start + 1;
    return;
  case Op_LShiftI: case Op_LShiftL:
    *start = 1;
    *end   = 2;
    return;
  case Op_CMoveI:  case Op_CMoveL:  case Op_CMoveF:  case Op_CMoveD:
    *start = 2;
    *end   = n->req();
    return;
  }
  *start = 1;
  *end   = n->req(); // default is all operands
}

//------------------------------in_packset---------------------------
// Are s1 and s2 in a pack pair and ordered as s1,s2?
bool SuperWord::in_packset(Node* s1, Node* s2) {
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    assert(p->size() == 2, "must be");
    if (p->at(0) == s1 && p->at(p->size()-1) == s2) {
      return true;
    }
  }
  return false;
}

//------------------------------in_pack---------------------------
// Is s in pack p?
Node_List* SuperWord::in_pack(Node* s, Node_List* p) {
  for (uint i = 0; i < p->size(); i++) {
    if (p->at(i) == s) {
      return p;
    }
  }
  return NULL;
}

//------------------------------remove_pack_at---------------------------
// Remove the pack at position pos in the packset
void SuperWord::remove_pack_at(int pos) {
  Node_List* p = _packset.at(pos);
  for (uint i = 0; i < p->size(); i++) {
    Node* s = p->at(i);
    set_my_pack(s, NULL);
  }
  _packset.remove_at(pos);
}

//------------------------------executed_first---------------------------
// Return the node executed first in pack p.  Uses the RPO block list
// to determine order.
Node* SuperWord::executed_first(Node_List* p) {
  Node* n = p->at(0);
  int n_rpo = bb_idx(n);
  for (uint i = 1; i < p->size(); i++) {
    Node* s = p->at(i);
    int s_rpo = bb_idx(s);
    if (s_rpo < n_rpo) {
      n = s;
      n_rpo = s_rpo;
    }
  }
  return n;
}

//------------------------------executed_last---------------------------
// Return the node executed last in pack p.
Node* SuperWord::executed_last(Node_List* p) {
  Node* n = p->at(0);
  int n_rpo = bb_idx(n);
  for (uint i = 1; i < p->size(); i++) {
    Node* s = p->at(i);
    int s_rpo = bb_idx(s);
    if (s_rpo > n_rpo) {
      n = s;
      n_rpo = s_rpo;
    }
  }
  return n;
}

//----------------------------align_initial_loop_index---------------------------
// Adjust pre-loop limit so that in main loop, a load/store reference
// to align_to_ref will be a position zero in the vector.
//   (iv + k) mod vector_align == 0
void SuperWord::align_initial_loop_index(MemNode* align_to_ref) {
  CountedLoopNode *main_head = lp()->as_CountedLoop();
  assert(main_head->is_main_loop(), "");
  CountedLoopEndNode* pre_end = get_pre_loop_end(main_head);
  assert(pre_end != NULL, "");
  Node *pre_opaq1 = pre_end->limit();
  assert(pre_opaq1->Opcode() == Op_Opaque1, "");
  Opaque1Node *pre_opaq = (Opaque1Node*)pre_opaq1;
  Node *lim0 = pre_opaq->in(1);

  // Where we put new limit calculations
  Node *pre_ctrl = pre_end->loopnode()->in(LoopNode::EntryControl);

  // Ensure the original loop limit is available from the
  // pre-loop Opaque1 node.
  Node *orig_limit = pre_opaq->original_loop_limit();
  assert(orig_limit != NULL && _igvn.type(orig_limit) != Type::TOP, "");

  SWPointer align_to_ref_p(align_to_ref, this);

  // Given:
  //     lim0 == original pre loop limit
  //     V == v_align (power of 2)
  //     invar == extra invariant piece of the address expression
  //     e == k [ +/- invar ]
  //
  // When reassociating expressions involving '%' the basic rules are:
  //     (a - b) % k == 0   =>  a % k == b % k
  // and:
  //     (a + b) % k == 0   =>  a % k == (k - b) % k
  //
  // For stride > 0 && scale > 0,
  //   Derive the new pre-loop limit "lim" such that the two constraints:
  //     (1) lim = lim0 + N           (where N is some positive integer < V)
  //     (2) (e + lim) % V == 0
  //   are true.
  //
  //   Substituting (1) into (2),
  //     (e + lim0 + N) % V == 0
  //   solve for N:
  //     N = (V - (e + lim0)) % V
  //   substitute back into (1), so that new limit
  //     lim = lim0 + (V - (e + lim0)) % V
  //
  // For stride > 0 && scale < 0
  //   Constraints:
  //     lim = lim0 + N
  //     (e - lim) % V == 0
  //   Solving for lim:
  //     (e - lim0 - N) % V == 0
  //     N = (e - lim0) % V
  //     lim = lim0 + (e - lim0) % V
  //
  // For stride < 0 && scale > 0
  //   Constraints:
  //     lim = lim0 - N
  //     (e + lim) % V == 0
  //   Solving for lim:
  //     (e + lim0 - N) % V == 0
  //     N = (e + lim0) % V
  //     lim = lim0 - (e + lim0) % V
  //
  // For stride < 0 && scale < 0
  //   Constraints:
  //     lim = lim0 - N
  //     (e - lim) % V == 0
  //   Solving for lim:
  //     (e - lim0 + N) % V == 0
  //     N = (V - (e - lim0)) % V
  //     lim = lim0 - (V - (e - lim0)) % V

  int stride   = iv_stride();
  int scale    = align_to_ref_p.scale_in_bytes();
  int elt_size = align_to_ref_p.memory_size();
  int v_align  = vector_width_in_bytes() / elt_size;
  int k        = align_to_ref_p.offset_in_bytes() / elt_size;

  Node *kn   = _igvn.intcon(k);

  Node *e = kn;
  if (align_to_ref_p.invar() != NULL) {
    // incorporate any extra invariant piece producing k +/- invar >>> log2(elt)
    Node* log2_elt = _igvn.intcon(exact_log2(elt_size));
    Node* aref     = new (_phase->C, 3) URShiftINode(align_to_ref_p.invar(), log2_elt);
    _phase->_igvn.register_new_node_with_optimizer(aref);
    _phase->set_ctrl(aref, pre_ctrl);
    if (align_to_ref_p.negate_invar()) {
      e = new (_phase->C, 3) SubINode(e, aref);
    } else {
      e = new (_phase->C, 3) AddINode(e, aref);
    }
    _phase->_igvn.register_new_node_with_optimizer(e);
    _phase->set_ctrl(e, pre_ctrl);
  }

  // compute e +/- lim0
  if (scale < 0) {
    e = new (_phase->C, 3) SubINode(e, lim0);
  } else {
    e = new (_phase->C, 3) AddINode(e, lim0);
  }
  _phase->_igvn.register_new_node_with_optimizer(e);
  _phase->set_ctrl(e, pre_ctrl);

  if (stride * scale > 0) {
    // compute V - (e +/- lim0)
    Node* va  = _igvn.intcon(v_align);
    e = new (_phase->C, 3) SubINode(va, e);
    _phase->_igvn.register_new_node_with_optimizer(e);
    _phase->set_ctrl(e, pre_ctrl);
  }
  // compute N = (exp) % V
  Node* va_msk = _igvn.intcon(v_align - 1);
  Node* N = new (_phase->C, 3) AndINode(e, va_msk);
  _phase->_igvn.register_new_node_with_optimizer(N);
  _phase->set_ctrl(N, pre_ctrl);

  //   substitute back into (1), so that new limit
  //     lim = lim0 + N
  Node* lim;
  if (stride < 0) {
    lim = new (_phase->C, 3) SubINode(lim0, N);
  } else {
    lim = new (_phase->C, 3) AddINode(lim0, N);
  }
  _phase->_igvn.register_new_node_with_optimizer(lim);
  _phase->set_ctrl(lim, pre_ctrl);
  Node* constrained =
    (stride > 0) ? (Node*) new (_phase->C,3) MinINode(lim, orig_limit)
                 : (Node*) new (_phase->C,3) MaxINode(lim, orig_limit);
  _phase->_igvn.register_new_node_with_optimizer(constrained);
  _phase->set_ctrl(constrained, pre_ctrl);
  _igvn.hash_delete(pre_opaq);
  pre_opaq->set_req(1, constrained);
}

//----------------------------get_pre_loop_end---------------------------
// Find pre loop end from main loop.  Returns null if none.
CountedLoopEndNode* SuperWord::get_pre_loop_end(CountedLoopNode *cl) {
  Node *ctrl = cl->in(LoopNode::EntryControl);
  if (!ctrl->is_IfTrue() && !ctrl->is_IfFalse()) return NULL;
  Node *iffm = ctrl->in(0);
  if (!iffm->is_If()) return NULL;
  Node *p_f = iffm->in(0);
  if (!p_f->is_IfFalse()) return NULL;
  if (!p_f->in(0)->is_CountedLoopEnd()) return NULL;
  CountedLoopEndNode *pre_end = p_f->in(0)->as_CountedLoopEnd();
  if (!pre_end->loopnode()->is_pre_loop()) return NULL;
  return pre_end;
}


//------------------------------init---------------------------
void SuperWord::init() {
  _dg.init();
  _packset.clear();
  _disjoint_ptrs.clear();
  _block.clear();
  _data_entry.clear();
  _mem_slice_head.clear();
  _mem_slice_tail.clear();
  _node_info.clear();
  _align_to_ref = NULL;
  _lpt = NULL;
  _lp = NULL;
  _bb = NULL;
  _iv = NULL;
}

//------------------------------print_packset---------------------------
void SuperWord::print_packset() {
#ifndef PRODUCT
  tty->print_cr("packset");
  for (int i = 0; i < _packset.length(); i++) {
    tty->print_cr("Pack: %d", i);
    Node_List* p = _packset.at(i);
    print_pack(p);
  }
#endif
}

//------------------------------print_pack---------------------------
void SuperWord::print_pack(Node_List* p) {
  for (uint i = 0; i < p->size(); i++) {
    print_stmt(p->at(i));
  }
}

//------------------------------print_bb---------------------------
void SuperWord::print_bb() {
#ifndef PRODUCT
  tty->print_cr("\nBlock");
  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    tty->print("%d ", i);
    if (n) {
      n->dump();
    }
  }
#endif
}

//------------------------------print_stmt---------------------------
void SuperWord::print_stmt(Node* s) {
#ifndef PRODUCT
  tty->print(" align: %d \t", alignment(s));
  s->dump();
#endif
}

//------------------------------blank---------------------------
char* SuperWord::blank(uint depth) {
  static char blanks[101];
  assert(depth < 101, "too deep");
  for (uint i = 0; i < depth; i++) blanks[i] = ' ';
  blanks[depth] = '\0';
  return blanks;
}


//==============================SWPointer===========================

//----------------------------SWPointer------------------------
SWPointer::SWPointer(MemNode* mem, SuperWord* slp) :
  _mem(mem), _slp(slp),  _base(NULL),  _adr(NULL),
  _scale(0), _offset(0), _invar(NULL), _negate_invar(false) {

  Node* adr = mem->in(MemNode::Address);
  if (!adr->is_AddP()) {
    assert(!valid(), "too complex");
    return;
  }
  // Match AddP(base, AddP(ptr, k*iv [+ invariant]), constant)
  Node* base = adr->in(AddPNode::Base);
  for (int i = 0; i < 3; i++) {
    if (!scaled_iv_plus_offset(adr->in(AddPNode::Offset))) {
      assert(!valid(), "too complex");
      return;
    }
    adr = adr->in(AddPNode::Address);
    if (base == adr || !adr->is_AddP()) {
      break; // stop looking at addp's
    }
  }
  _base = base;
  _adr  = adr;
  assert(valid(), "Usable");
}

// Following is used to create a temporary object during
// the pattern match of an address expression.
SWPointer::SWPointer(SWPointer* p) :
  _mem(p->_mem), _slp(p->_slp),  _base(NULL),  _adr(NULL),
  _scale(0), _offset(0), _invar(NULL), _negate_invar(false) {}

//------------------------scaled_iv_plus_offset--------------------
// Match: k*iv + offset
// where: k is a constant that maybe zero, and
//        offset is (k2 [+/- invariant]) where k2 maybe zero and invariant is optional
bool SWPointer::scaled_iv_plus_offset(Node* n) {
  if (scaled_iv(n)) {
    return true;
  }
  if (offset_plus_k(n)) {
    return true;
  }
  int opc = n->Opcode();
  if (opc == Op_AddI) {
    if (scaled_iv(n->in(1)) && offset_plus_k(n->in(2))) {
      return true;
    }
    if (scaled_iv(n->in(2)) && offset_plus_k(n->in(1))) {
      return true;
    }
  } else if (opc == Op_SubI) {
    if (scaled_iv(n->in(1)) && offset_plus_k(n->in(2), true)) {
      return true;
    }
    if (scaled_iv(n->in(2)) && offset_plus_k(n->in(1))) {
      _scale *= -1;
      return true;
    }
  }
  return false;
}

//----------------------------scaled_iv------------------------
// Match: k*iv where k is a constant that's not zero
bool SWPointer::scaled_iv(Node* n) {
  if (_scale != 0) {
    return false;  // already found a scale
  }
  if (n == iv()) {
    _scale = 1;
    return true;
  }
  int opc = n->Opcode();
  if (opc == Op_MulI) {
    if (n->in(1) == iv() && n->in(2)->is_Con()) {
      _scale = n->in(2)->get_int();
      return true;
    } else if (n->in(2) == iv() && n->in(1)->is_Con()) {
      _scale = n->in(1)->get_int();
      return true;
    }
  } else if (opc == Op_LShiftI) {
    if (n->in(1) == iv() && n->in(2)->is_Con()) {
      _scale = 1 << n->in(2)->get_int();
      return true;
    }
  } else if (opc == Op_ConvI2L) {
    if (scaled_iv_plus_offset(n->in(1))) {
      return true;
    }
  } else if (opc == Op_LShiftL) {
    if (!has_iv() && _invar == NULL) {
      // Need to preserve the current _offset value, so
      // create a temporary object for this expression subtree.
      // Hacky, so should re-engineer the address pattern match.
      SWPointer tmp(this);
      if (tmp.scaled_iv_plus_offset(n->in(1))) {
        if (tmp._invar == NULL) {
          int mult = 1 << n->in(2)->get_int();
          _scale   = tmp._scale  * mult;
          _offset += tmp._offset * mult;
          return true;
        }
      }
    }
  }
  return false;
}

//----------------------------offset_plus_k------------------------
// Match: offset is (k [+/- invariant])
// where k maybe zero and invariant is optional, but not both.
bool SWPointer::offset_plus_k(Node* n, bool negate) {
  int opc = n->Opcode();
  if (opc == Op_ConI) {
    _offset += negate ? -(n->get_int()) : n->get_int();
    return true;
  } else if (opc == Op_ConL) {
    // Okay if value fits into an int
    const TypeLong* t = n->find_long_type();
    if (t->higher_equal(TypeLong::INT)) {
      jlong loff = n->get_long();
      jint  off  = (jint)loff;
      _offset += negate ? -off : loff;
      return true;
    }
    return false;
  }
  if (_invar != NULL) return false; // already have an invariant
  if (opc == Op_AddI) {
    if (n->in(2)->is_Con() && invariant(n->in(1))) {
      _negate_invar = negate;
      _invar = n->in(1);
      _offset += negate ? -(n->in(2)->get_int()) : n->in(2)->get_int();
      return true;
    } else if (n->in(1)->is_Con() && invariant(n->in(2))) {
      _offset += negate ? -(n->in(1)->get_int()) : n->in(1)->get_int();
      _negate_invar = negate;
      _invar = n->in(2);
      return true;
    }
  }
  if (opc == Op_SubI) {
    if (n->in(2)->is_Con() && invariant(n->in(1))) {
      _negate_invar = negate;
      _invar = n->in(1);
      _offset += !negate ? -(n->in(2)->get_int()) : n->in(2)->get_int();
      return true;
    } else if (n->in(1)->is_Con() && invariant(n->in(2))) {
      _offset += negate ? -(n->in(1)->get_int()) : n->in(1)->get_int();
      _negate_invar = !negate;
      _invar = n->in(2);
      return true;
    }
  }
  if (invariant(n)) {
    _negate_invar = negate;
    _invar = n;
    return true;
  }
  return false;
}

//----------------------------print------------------------
void SWPointer::print() {
#ifndef PRODUCT
  tty->print("base: %d  adr: %d  scale: %d  offset: %d  invar: %c%d\n",
             _base != NULL ? _base->_idx : 0,
             _adr  != NULL ? _adr->_idx  : 0,
             _scale, _offset,
             _negate_invar?'-':'+',
             _invar != NULL ? _invar->_idx : 0);
#endif
}

// ========================= OrderedPair =====================

const OrderedPair OrderedPair::initial;

// ========================= SWNodeInfo =====================

const SWNodeInfo SWNodeInfo::initial;


// ============================ DepGraph ===========================

//------------------------------make_node---------------------------
// Make a new dependence graph node for an ideal node.
DepMem* DepGraph::make_node(Node* node) {
  DepMem* m = new (_arena) DepMem(node);
  if (node != NULL) {
    assert(_map.at_grow(node->_idx) == NULL, "one init only");
    _map.at_put_grow(node->_idx, m);
  }
  return m;
}

//------------------------------make_edge---------------------------
// Make a new dependence graph edge from dpred -> dsucc
DepEdge* DepGraph::make_edge(DepMem* dpred, DepMem* dsucc) {
  DepEdge* e = new (_arena) DepEdge(dpred, dsucc, dsucc->in_head(), dpred->out_head());
  dpred->set_out_head(e);
  dsucc->set_in_head(e);
  return e;
}

// ========================== DepMem ========================

//------------------------------in_cnt---------------------------
int DepMem::in_cnt() {
  int ct = 0;
  for (DepEdge* e = _in_head; e != NULL; e = e->next_in()) ct++;
  return ct;
}

//------------------------------out_cnt---------------------------
int DepMem::out_cnt() {
  int ct = 0;
  for (DepEdge* e = _out_head; e != NULL; e = e->next_out()) ct++;
  return ct;
}

//------------------------------print-----------------------------
void DepMem::print() {
#ifndef PRODUCT
  tty->print("  DepNode %d (", _node->_idx);
  for (DepEdge* p = _in_head; p != NULL; p = p->next_in()) {
    Node* pred = p->pred()->node();
    tty->print(" %d", pred != NULL ? pred->_idx : 0);
  }
  tty->print(") [");
  for (DepEdge* s = _out_head; s != NULL; s = s->next_out()) {
    Node* succ = s->succ()->node();
    tty->print(" %d", succ != NULL ? succ->_idx : 0);
  }
  tty->print_cr(" ]");
#endif
}

// =========================== DepEdge =========================

//------------------------------DepPreds---------------------------
void DepEdge::print() {
#ifndef PRODUCT
  tty->print_cr("DepEdge: %d [ %d ]", _pred->node()->_idx, _succ->node()->_idx);
#endif
}

// =========================== DepPreds =========================
// Iterator over predecessor edges in the dependence graph.

//------------------------------DepPreds---------------------------
DepPreds::DepPreds(Node* n, DepGraph& dg) {
  _n = n;
  _done = false;
  if (_n->is_Store() || _n->is_Load()) {
    _next_idx = MemNode::Address;
    _end_idx  = n->req();
    _dep_next = dg.dep(_n)->in_head();
  } else if (_n->is_Mem()) {
    _next_idx = 0;
    _end_idx  = 0;
    _dep_next = dg.dep(_n)->in_head();
  } else {
    _next_idx = 1;
    _end_idx  = _n->req();
    _dep_next = NULL;
  }
  next();
}

//------------------------------next---------------------------
void DepPreds::next() {
  if (_dep_next != NULL) {
    _current  = _dep_next->pred()->node();
    _dep_next = _dep_next->next_in();
  } else if (_next_idx < _end_idx) {
    _current  = _n->in(_next_idx++);
  } else {
    _done = true;
  }
}

// =========================== DepSuccs =========================
// Iterator over successor edges in the dependence graph.

//------------------------------DepSuccs---------------------------
DepSuccs::DepSuccs(Node* n, DepGraph& dg) {
  _n = n;
  _done = false;
  if (_n->is_Load()) {
    _next_idx = 0;
    _end_idx  = _n->outcnt();
    _dep_next = dg.dep(_n)->out_head();
  } else if (_n->is_Mem() || _n->is_Phi() && _n->bottom_type() == Type::MEMORY) {
    _next_idx = 0;
    _end_idx  = 0;
    _dep_next = dg.dep(_n)->out_head();
  } else {
    _next_idx = 0;
    _end_idx  = _n->outcnt();
    _dep_next = NULL;
  }
  next();
}

//-------------------------------next---------------------------
void DepSuccs::next() {
  if (_dep_next != NULL) {
    _current  = _dep_next->succ()->node();
    _dep_next = _dep_next->next_out();
  } else if (_next_idx < _end_idx) {
    _current  = _n->raw_out(_next_idx++);
  } else {
    _done = true;
  }
}
