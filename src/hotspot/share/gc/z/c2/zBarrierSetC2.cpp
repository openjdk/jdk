/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "opto/castnode.hpp"
#include "opto/compile.hpp"
#include "opto/escape.hpp"
#include "opto/graphKit.hpp"
#include "opto/loopnode.hpp"
#include "opto/machnode.hpp"
#include "opto/macro.hpp"
#include "opto/memnode.hpp"
#include "opto/movenode.hpp"
#include "opto/node.hpp"
#include "opto/phase.hpp"
#include "opto/phaseX.hpp"
#include "opto/rootnode.hpp"
#include "opto/type.hpp"
#include "utilities/copy.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/c2/zBarrierSetC2.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"

ZBarrierSetC2State::ZBarrierSetC2State(Arena* comp_arena) :
    _load_barrier_nodes(new (comp_arena) GrowableArray<LoadBarrierNode*>(comp_arena, 8,  0, NULL)) {}

int ZBarrierSetC2State::load_barrier_count() const {
  return _load_barrier_nodes->length();
}

void ZBarrierSetC2State::add_load_barrier_node(LoadBarrierNode * n) {
  assert(!_load_barrier_nodes->contains(n), " duplicate entry in expand list");
  _load_barrier_nodes->append(n);
}

void ZBarrierSetC2State::remove_load_barrier_node(LoadBarrierNode * n) {
  // this function may be called twice for a node so check
  // that the node is in the array before attempting to remove it
  if (_load_barrier_nodes->contains(n)) {
    _load_barrier_nodes->remove(n);
  }
}

LoadBarrierNode* ZBarrierSetC2State::load_barrier_node(int idx) const {
  return _load_barrier_nodes->at(idx);
}

void* ZBarrierSetC2::create_barrier_state(Arena* comp_arena) const {
  return new(comp_arena) ZBarrierSetC2State(comp_arena);
}

ZBarrierSetC2State* ZBarrierSetC2::state() const {
  return reinterpret_cast<ZBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

bool ZBarrierSetC2::is_gc_barrier_node(Node* node) const {
  // 1. This step follows potential oop projections of a load barrier before expansion
  if (node->is_Proj()) {
    node = node->in(0);
  }

  // 2. This step checks for unexpanded load barriers
  if (node->is_LoadBarrier()) {
    return true;
  }

  // 3. This step checks for the phi corresponding to an optimized load barrier expansion
  if (node->is_Phi()) {
    PhiNode* phi = node->as_Phi();
    Node* n = phi->in(1);
    if (n != NULL && n->is_LoadBarrierSlowReg()) {
      return true;
    }
  }

  return false;
}

void ZBarrierSetC2::register_potential_barrier_node(Node* node) const {
  if (node->is_LoadBarrier()) {
    state()->add_load_barrier_node(node->as_LoadBarrier());
  }
}

void ZBarrierSetC2::unregister_potential_barrier_node(Node* node) const {
  if (node->is_LoadBarrier()) {
    state()->remove_load_barrier_node(node->as_LoadBarrier());
  }
}

void ZBarrierSetC2::eliminate_useless_gc_barriers(Unique_Node_List &useful, Compile* C) const {
  // Remove useless LoadBarrier nodes
  ZBarrierSetC2State* s = state();
  for (int i = s->load_barrier_count()-1; i >= 0; i--) {
    LoadBarrierNode* n = s->load_barrier_node(i);
    if (!useful.member(n)) {
      unregister_potential_barrier_node(n);
    }
  }
}

void ZBarrierSetC2::enqueue_useful_gc_barrier(PhaseIterGVN* igvn, Node* node) const {
  if (node->is_LoadBarrier() && !node->as_LoadBarrier()->has_true_uses()) {
    igvn->_worklist.push(node);
  }
}

const uint NoBarrier       = 0;
const uint RequireBarrier  = 1;
const uint WeakBarrier     = 2;
const uint ExpandedBarrier = 4;

static bool load_require_barrier(LoadNode* load)      { return (load->barrier_data() & RequireBarrier)  == RequireBarrier; }
static bool load_has_weak_barrier(LoadNode* load)     { return (load->barrier_data() & WeakBarrier)     == WeakBarrier; }
static bool load_has_expanded_barrier(LoadNode* load) { return (load->barrier_data() & ExpandedBarrier) == ExpandedBarrier; }
static void load_set_expanded_barrier(LoadNode* load) { return load->set_barrier_data(ExpandedBarrier); }

static void load_set_barrier(LoadNode* load, bool weak) {
  if (weak) {
    load->set_barrier_data(RequireBarrier | WeakBarrier);
  } else {
    load->set_barrier_data(RequireBarrier);
  }
}

// == LoadBarrierNode ==

LoadBarrierNode::LoadBarrierNode(Compile* C,
                                 Node* c,
                                 Node* mem,
                                 Node* val,
                                 Node* adr,
                                 bool weak) :
    MultiNode(Number_of_Inputs),
    _weak(weak) {
  init_req(Control, c);
  init_req(Memory, mem);
  init_req(Oop, val);
  init_req(Address, adr);
  init_req(Similar, C->top());

  init_class_id(Class_LoadBarrier);
  BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
  bs->register_potential_barrier_node(this);
}

uint LoadBarrierNode::size_of() const {
  return sizeof(*this);
}

bool LoadBarrierNode::cmp(const Node& n) const {
  ShouldNotReachHere();
  return false;
}

const Type *LoadBarrierNode::bottom_type() const {
  const Type** floadbarrier = (const Type **)(Compile::current()->type_arena()->Amalloc_4((Number_of_Outputs)*sizeof(Type*)));
  Node* in_oop = in(Oop);
  floadbarrier[Control] = Type::CONTROL;
  floadbarrier[Memory] = Type::MEMORY;
  floadbarrier[Oop] = in_oop == NULL ? Type::TOP : in_oop->bottom_type();
  return TypeTuple::make(Number_of_Outputs, floadbarrier);
}

const TypePtr* LoadBarrierNode::adr_type() const {
  ShouldNotReachHere();
  return NULL;
}

const Type *LoadBarrierNode::Value(PhaseGVN *phase) const {
  const Type** floadbarrier = (const Type **)(phase->C->type_arena()->Amalloc_4((Number_of_Outputs)*sizeof(Type*)));
  const Type* val_t = phase->type(in(Oop));
  floadbarrier[Control] = Type::CONTROL;
  floadbarrier[Memory]  = Type::MEMORY;
  floadbarrier[Oop]     = val_t;
  return TypeTuple::make(Number_of_Outputs, floadbarrier);
}

bool LoadBarrierNode::is_dominator(PhaseIdealLoop* phase, bool linear_only, Node *d, Node *n) {
  if (phase != NULL) {
    return phase->is_dominator(d, n);
  }

  for (int i = 0; i < 10 && n != NULL; i++) {
    n = IfNode::up_one_dom(n, linear_only);
    if (n == d) {
      return true;
    }
  }

  return false;
}

LoadBarrierNode* LoadBarrierNode::has_dominating_barrier(PhaseIdealLoop* phase, bool linear_only, bool look_for_similar) {
  if (is_weak()) {
    // Weak barriers can't be eliminated
    return NULL;
  }

  Node* val = in(LoadBarrierNode::Oop);
  if (in(Similar)->is_Proj() && in(Similar)->in(0)->is_LoadBarrier()) {
    LoadBarrierNode* lb = in(Similar)->in(0)->as_LoadBarrier();
    assert(lb->in(Address) == in(Address), "");
    // Load barrier on Similar edge dominates so if it now has the Oop field it can replace this barrier.
    if (lb->in(Oop) == in(Oop)) {
      return lb;
    }
    // Follow chain of load barrier through Similar edges
    while (!lb->in(Similar)->is_top()) {
      lb = lb->in(Similar)->in(0)->as_LoadBarrier();
      assert(lb->in(Address) == in(Address), "");
    }
    if (lb != in(Similar)->in(0)) {
      return lb;
    }
  }
  for (DUIterator_Fast imax, i = val->fast_outs(imax); i < imax; i++) {
    Node* u = val->fast_out(i);
    if (u != this && u->is_LoadBarrier() && u->in(Oop) == val && u->as_LoadBarrier()->has_true_uses()) {
      Node* this_ctrl = in(LoadBarrierNode::Control);
      Node* other_ctrl = u->in(LoadBarrierNode::Control);
      if (is_dominator(phase, linear_only, other_ctrl, this_ctrl)) {
        return u->as_LoadBarrier();
      }
    }
  }

  if (can_be_eliminated()) {
    return NULL;
  }

  if (!look_for_similar) {
    return NULL;
  }

  Node* addr = in(LoadBarrierNode::Address);
  for (DUIterator_Fast imax, i = addr->fast_outs(imax); i < imax; i++) {
    Node* u = addr->fast_out(i);
    if (u != this && u->is_LoadBarrier() && u->as_LoadBarrier()->has_true_uses()) {
      Node* this_ctrl = in(LoadBarrierNode::Control);
      Node* other_ctrl = u->in(LoadBarrierNode::Control);
      if (is_dominator(phase, linear_only, other_ctrl, this_ctrl)) {
        ResourceMark rm;
        Unique_Node_List wq;
        wq.push(in(LoadBarrierNode::Control));
        bool ok = true;
        bool dom_found = false;
        for (uint next = 0; next < wq.size(); ++next) {
          Node *n = wq.at(next);
          if (n->is_top()) {
            return NULL;
          }
          assert(n->is_CFG(), "");
          if (n->is_SafePoint()) {
            ok = false;
            break;
          }
          if (n == u) {
            dom_found = true;
            continue;
          }
          if (n->is_Region()) {
            for (uint i = 1; i < n->req(); i++) {
              Node* m = n->in(i);
              if (m != NULL) {
                wq.push(m);
              }
            }
          } else {
            Node* m = n->in(0);
            if (m != NULL) {
              wq.push(m);
            }
          }
        }
        if (ok) {
          assert(dom_found, "");
          return u->as_LoadBarrier();
        }
        break;
      }
    }
  }

  return NULL;
}

void LoadBarrierNode::push_dominated_barriers(PhaseIterGVN* igvn) const {
  // Change to that barrier may affect a dominated barrier so re-push those
  assert(!is_weak(), "sanity");
  Node* val = in(LoadBarrierNode::Oop);

  for (DUIterator_Fast imax, i = val->fast_outs(imax); i < imax; i++) {
    Node* u = val->fast_out(i);
    if (u != this && u->is_LoadBarrier() && u->in(Oop) == val) {
      Node* this_ctrl = in(Control);
      Node* other_ctrl = u->in(Control);
      if (is_dominator(NULL, false, this_ctrl, other_ctrl)) {
        igvn->_worklist.push(u);
      }
    }

    Node* addr = in(LoadBarrierNode::Address);
    for (DUIterator_Fast imax, i = addr->fast_outs(imax); i < imax; i++) {
      Node* u = addr->fast_out(i);
      if (u != this && u->is_LoadBarrier() && u->in(Similar)->is_top()) {
        Node* this_ctrl = in(Control);
        Node* other_ctrl = u->in(Control);
        if (is_dominator(NULL, false, this_ctrl, other_ctrl)) {
          igvn->_worklist.push(u);
        }
      }
    }
  }
}

Node *LoadBarrierNode::Identity(PhaseGVN *phase) {
  LoadBarrierNode* dominating_barrier = has_dominating_barrier(NULL, true, false);
  if (dominating_barrier != NULL) {
    assert(!is_weak(), "Weak barriers cant be eliminated");
    assert(dominating_barrier->in(Oop) == in(Oop), "");
    return dominating_barrier;
  }

  return this;
}

Node *LoadBarrierNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (remove_dead_region(phase, can_reshape)) {
    return this;
  }

  Node *val = in(Oop);
  Node *mem = in(Memory);
  Node *ctrl = in(Control);

  assert(val->Opcode() != Op_LoadN, "");
  assert(val->Opcode() != Op_DecodeN, "");

  if (mem->is_MergeMem()) {
    Node *new_mem = mem->as_MergeMem()->memory_at(Compile::AliasIdxRaw);
    set_req(Memory, new_mem);
    if (mem->outcnt() == 0 && can_reshape) {
      phase->is_IterGVN()->_worklist.push(mem);
    }
    return this;
  }

  LoadBarrierNode *dominating_barrier = NULL;
  if (!is_weak()) {
    dominating_barrier = has_dominating_barrier(NULL, !can_reshape, !phase->C->major_progress());
    if (dominating_barrier != NULL && dominating_barrier->in(Oop) != in(Oop)) {
      assert(in(Address) == dominating_barrier->in(Address), "");
      set_req(Similar, dominating_barrier->proj_out(Oop));
      return this;
    }
  }

  bool eliminate = can_reshape && (dominating_barrier != NULL || !has_true_uses());
  if (eliminate) {
    if (can_reshape) {
      PhaseIterGVN* igvn = phase->is_IterGVN();
      Node* out_ctrl = proj_out_or_null(Control);
      Node* out_res = proj_out_or_null(Oop);

      if (out_ctrl != NULL) {
        igvn->replace_node(out_ctrl, ctrl);
      }

      // That transformation may cause the Similar edge on the load barrier to be invalid
      fix_similar_in_uses(igvn);
      if (out_res != NULL) {
        if (dominating_barrier != NULL) {
          assert(!is_weak(), "Sanity");
          igvn->replace_node(out_res, dominating_barrier->proj_out(Oop));
        } else {
          igvn->replace_node(out_res, val);
        }
      }
    }
    return new ConINode(TypeInt::ZERO);
  }

  // If the Similar edge is no longer a load barrier, clear it
  Node* similar = in(Similar);
  if (!similar->is_top() && !(similar->is_Proj() && similar->in(0)->is_LoadBarrier())) {
    set_req(Similar, phase->C->top());
    return this;
  }

  if (can_reshape && !is_weak()) {
    // If this barrier is linked through the Similar edge by a
    // dominated barrier and both barriers have the same Oop field,
    // the dominated barrier can go away, so push it for reprocessing.
    // We also want to avoid a barrier to depend on another dominating
    // barrier through its Similar edge that itself depend on another
    // barrier through its Similar edge and rather have the first
    // depend on the third.
    PhaseIterGVN* igvn = phase->is_IterGVN();
    Node* out_res = proj_out(Oop);
    for (DUIterator_Fast imax, i = out_res->fast_outs(imax); i < imax; i++) {
      Node* u = out_res->fast_out(i);
      if (u->is_LoadBarrier() && u->in(Similar) == out_res &&
          (u->in(Oop) == val || !u->in(Similar)->is_top())) {
        assert(!u->as_LoadBarrier()->is_weak(), "Sanity");
        igvn->_worklist.push(u);
      }
    }
    push_dominated_barriers(igvn);
  }

  return NULL;
}

uint LoadBarrierNode::match_edge(uint idx) const {
  ShouldNotReachHere();
  return 0;
}

void LoadBarrierNode::fix_similar_in_uses(PhaseIterGVN* igvn) {
  Node* out_res = proj_out_or_null(Oop);
  if (out_res == NULL) {
    return;
  }

  for (DUIterator_Fast imax, i = out_res->fast_outs(imax); i < imax; i++) {
    Node* u = out_res->fast_out(i);
    if (u->is_LoadBarrier() && u->in(Similar) == out_res) {
      igvn->replace_input_of(u, Similar, igvn->C->top());
      --i;
      --imax;
    }
  }
}

bool LoadBarrierNode::has_true_uses() const {
  Node* out_res = proj_out_or_null(Oop);
  if (out_res != NULL) {
    for (DUIterator_Fast imax, i = out_res->fast_outs(imax); i < imax; i++) {
      Node *u = out_res->fast_out(i);
      if (!u->is_LoadBarrier() || u->in(Similar) != out_res) {
        return true;
      }
    }
  }
  return false;
}

static bool barrier_needed(C2Access& access) {
  return ZBarrierSet::barrier_needed(access.decorators(), access.type());
}

Node* ZBarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  Node* p = BarrierSetC2::load_at_resolved(access, val_type);
  if (!barrier_needed(access)) {
    return p;
  }

  bool weak = (access.decorators() & ON_WEAK_OOP_REF) != 0;
  if (p->isa_Load()) {
    load_set_barrier(p->as_Load(), weak);
  }
  return p;
}

Node* ZBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                    Node* new_val, const Type* val_type) const {
  Node* result = BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, val_type);
  LoadStoreNode* lsn = result->as_LoadStore();
  if (barrier_needed(access)) {
    lsn->set_has_barrier();
  }
  return lsn;
}

Node* ZBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                     Node* new_val, const Type* value_type) const {
  Node* result = BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
  LoadStoreNode* lsn = result->as_LoadStore();
  if (barrier_needed(access)) {
    lsn->set_has_barrier();
  }
  return lsn;
}

Node* ZBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* val_type) const {
  Node* result = BarrierSetC2::atomic_xchg_at_resolved(access, new_val, val_type);
  LoadStoreNode* lsn = result->as_LoadStore();
  if (barrier_needed(access)) {
    lsn->set_has_barrier();
  }
  return lsn;
}

// == Macro Expansion ==

// Optimized, low spill, loadbarrier variant using stub specialized on register used
void ZBarrierSetC2::expand_loadbarrier_node(PhaseMacroExpand* phase, LoadBarrierNode* barrier) const {
  PhaseIterGVN &igvn = phase->igvn();
  float unlikely  = PROB_UNLIKELY(0.999);

  Node* in_ctrl = barrier->in(LoadBarrierNode::Control);
  Node* in_mem = barrier->in(LoadBarrierNode::Memory);
  Node* in_val = barrier->in(LoadBarrierNode::Oop);
  Node* in_adr = barrier->in(LoadBarrierNode::Address);

  Node* out_ctrl = barrier->proj_out(LoadBarrierNode::Control);
  Node* out_res = barrier->proj_out(LoadBarrierNode::Oop);

  assert(barrier->in(LoadBarrierNode::Oop) != NULL, "oop to loadbarrier node cannot be null");

  Node* jthread = igvn.transform(new ThreadLocalNode());
  Node* adr = phase->basic_plus_adr(jthread, in_bytes(ZThreadLocalData::address_bad_mask_offset()));
  Node* bad_mask = igvn.transform(LoadNode::make(igvn, in_ctrl, in_mem, adr,
                                                 TypeRawPtr::BOTTOM, TypeX_X, TypeX_X->basic_type(),
                                                 MemNode::unordered));
  Node* cast = igvn.transform(new CastP2XNode(in_ctrl, in_val));
  Node* obj_masked = igvn.transform(new AndXNode(cast, bad_mask));
  Node* cmp = igvn.transform(new CmpXNode(obj_masked, igvn.zerocon(TypeX_X->basic_type())));
  Node *bol = igvn.transform(new BoolNode(cmp, BoolTest::ne))->as_Bool();
  IfNode* iff = igvn.transform(new IfNode(in_ctrl, bol, unlikely, COUNT_UNKNOWN))->as_If();
  Node* then = igvn.transform(new IfTrueNode(iff));
  Node* elsen = igvn.transform(new IfFalseNode(iff));

  Node* new_loadp = igvn.transform(new LoadBarrierSlowRegNode(then, in_adr, in_val,
                                                              (const TypePtr*) in_val->bottom_type(), barrier->is_weak()));

  // Create the final region/phi pair to converge cntl/data paths to downstream code
  Node* result_region = igvn.transform(new RegionNode(3));
  result_region->set_req(1, then);
  result_region->set_req(2, elsen);

  Node* result_phi = igvn.transform(new PhiNode(result_region, TypeInstPtr::BOTTOM));
  result_phi->set_req(1, new_loadp);
  result_phi->set_req(2, barrier->in(LoadBarrierNode::Oop));

  igvn.replace_node(out_ctrl, result_region);
  igvn.replace_node(out_res, result_phi);

  assert(barrier->outcnt() == 0,"LoadBarrier macro node has non-null outputs after expansion!");

  igvn.remove_dead_node(barrier);
  igvn.remove_dead_node(out_ctrl);
  igvn.remove_dead_node(out_res);

  assert(is_gc_barrier_node(result_phi), "sanity");
  assert(step_over_gc_barrier(result_phi) == in_val, "sanity");

  phase->C->print_method(PHASE_BARRIER_EXPANSION, 4, barrier->_idx);
}

bool ZBarrierSetC2::expand_barriers(Compile* C, PhaseIterGVN& igvn) const {
  ZBarrierSetC2State* s = state();
  if (s->load_barrier_count() > 0) {
    PhaseMacroExpand macro(igvn);

    int skipped = 0;
    while (s->load_barrier_count() > skipped) {
      int load_barrier_count = s->load_barrier_count();
      LoadBarrierNode * n = s->load_barrier_node(load_barrier_count-1-skipped);
      if (igvn.type(n) == Type::TOP || (n->in(0) != NULL && n->in(0)->is_top())) {
        // Node is unreachable, so don't try to expand it
        s->remove_load_barrier_node(n);
        continue;
      }
      if (!n->can_be_eliminated()) {
        skipped++;
        continue;
      }
      expand_loadbarrier_node(&macro, n);
      assert(s->load_barrier_count() < load_barrier_count, "must have deleted a node from load barrier list");
      if (C->failing()) {
        return true;
      }
    }
    while (s->load_barrier_count() > 0) {
      int load_barrier_count = s->load_barrier_count();
      LoadBarrierNode* n = s->load_barrier_node(load_barrier_count - 1);
      assert(!(igvn.type(n) == Type::TOP || (n->in(0) != NULL && n->in(0)->is_top())), "should have been processed already");
      assert(!n->can_be_eliminated(), "should have been processed already");
      expand_loadbarrier_node(&macro, n);
      assert(s->load_barrier_count() < load_barrier_count, "must have deleted a node from load barrier list");
      if (C->failing()) {
        return true;
      }
    }
    igvn.set_delay_transform(false);
    igvn.optimize();
    if (C->failing()) {
      return true;
    }
  }

  return false;
}

Node* ZBarrierSetC2::step_over_gc_barrier(Node* c) const {
  Node* node = c;

  // 1. This step follows potential oop projections of a load barrier before expansion
  if (node->is_Proj()) {
    node = node->in(0);
  }

  // 2. This step checks for unexpanded load barriers
  if (node->is_LoadBarrier()) {
    return node->in(LoadBarrierNode::Oop);
  }

  // 3. This step checks for the phi corresponding to an optimized load barrier expansion
  if (node->is_Phi()) {
    PhiNode* phi = node->as_Phi();
    Node* n = phi->in(1);
    if (n != NULL && n->is_LoadBarrierSlowReg()) {
      assert(c == node, "projections from step 1 should only be seen before macro expansion");
      return phi->in(2);
    }
  }

  return c;
}

Node* ZBarrierSetC2::step_over_gc_barrier_ctrl(Node* c) const {
  Node* node = c;

  // 1. This step follows potential ctrl projections of a load barrier before expansion
  if (node->is_Proj()) {
    node = node->in(0);
  }

  // 2. This step checks for unexpanded load barriers
  if (node->is_LoadBarrier()) {
    return node->in(LoadBarrierNode::Control);
  }

  return c;
}

bool ZBarrierSetC2::array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, ArrayCopyPhase phase) const {
  return is_reference_type(type);
}

bool ZBarrierSetC2::final_graph_reshaping(Compile* compile, Node* n, uint opcode) const {
  switch (opcode) {
    case Op_LoadBarrier:
      assert(0, "There should be no load barriers left");
    case Op_ZGetAndSetP:
    case Op_ZCompareAndExchangeP:
    case Op_ZCompareAndSwapP:
    case Op_ZWeakCompareAndSwapP:
#ifdef ASSERT
      if (VerifyOptoOopOffsets) {
        MemNode *mem = n->as_Mem();
        // Check to see if address types have grounded out somehow.
        const TypeInstPtr *tp = mem->in(MemNode::Address)->bottom_type()->isa_instptr();
        ciInstanceKlass *k = tp->klass()->as_instance_klass();
        bool oop_offset_is_sane = k->contains_field_offset(tp->offset());
        assert(!tp || oop_offset_is_sane, "");
      }
#endif
      return true;
    default:
      return false;
  }
}

bool ZBarrierSetC2::matcher_find_shared_visit(Matcher* matcher, Matcher::MStack& mstack, Node* n, uint opcode, bool& mem_op, int& mem_addr_idx) const {
  switch(opcode) {
    case Op_CallLeaf:
      if (n->as_Call()->entry_point() == ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr() ||
          n->as_Call()->entry_point() == ZBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded_addr()) {
        mem_op = true;
        mem_addr_idx = TypeFunc::Parms + 1;
        return true;
      }
      return false;
    default:
      return false;
  }
}

bool ZBarrierSetC2::matcher_find_shared_post_visit(Matcher* matcher, Node* n, uint opcode) const {
  switch(opcode) {
    case Op_ZCompareAndExchangeP:
    case Op_ZCompareAndSwapP:
    case Op_ZWeakCompareAndSwapP: {
      Node *mem = n->in(MemNode::Address);
      Node *keepalive = n->in(5);
      Node *pair1 = new BinaryNode(mem, keepalive);

      Node *newval = n->in(MemNode::ValueIn);
      Node *oldval = n->in(LoadStoreConditionalNode::ExpectedIn);
      Node *pair2 = new BinaryNode(oldval, newval);

      n->set_req(MemNode::Address, pair1);
      n->set_req(MemNode::ValueIn, pair2);
      n->del_req(5);
      n->del_req(LoadStoreConditionalNode::ExpectedIn);
      return true;
    }
    case Op_ZGetAndSetP: {
      Node *keepalive = n->in(4);
      Node *newval = n->in(MemNode::ValueIn);
      Node *pair = new BinaryNode(newval, keepalive);
      n->set_req(MemNode::ValueIn, pair);
      n->del_req(4);
      return true;
    }

    default:
      return false;
  }
}

// == Verification ==

#ifdef ASSERT

static void verify_slippery_safepoints_internal(Node* ctrl) {
  // Given a CFG node, make sure it does not contain both safepoints and loads
  // that have expanded barriers.
  bool found_safepoint = false;
  bool found_load = false;

  for (DUIterator_Fast imax, i = ctrl->fast_outs(imax); i < imax; i++) {
    Node* node = ctrl->fast_out(i);
    if (node->in(0) != ctrl) {
      // Skip outgoing precedence edges from ctrl.
      continue;
    }
    if (node->is_SafePoint()) {
      found_safepoint = true;
    }
    if (node->is_Load() && load_require_barrier(node->as_Load()) &&
        load_has_expanded_barrier(node->as_Load())) {
      found_load = true;
    }
  }
  assert(!found_safepoint || !found_load, "found load and safepoint in same block");
}

static void verify_slippery_safepoints(Compile* C) {
  ResourceArea *area = Thread::current()->resource_area();
  Unique_Node_List visited(area);
  Unique_Node_List checked(area);

  // Recursively walk the graph.
  visited.push(C->root());
  while (visited.size() > 0) {
    Node* node = visited.pop();

    Node* ctrl = node;
    if (!node->is_CFG()) {
      ctrl = node->in(0);
    }

    if (ctrl != NULL && !checked.member(ctrl)) {
      // For each block found in the graph, verify that it does not
      // contain both a safepoint and a load requiring barriers.
      verify_slippery_safepoints_internal(ctrl);

      checked.push(ctrl);
    }

    checked.push(node);

    for (DUIterator_Fast imax, i = node->fast_outs(imax); i < imax; i++) {
      Node* use = node->fast_out(i);
      if (checked.member(use))  continue;
      if (visited.member(use))  continue;
      visited.push(use);
    }
  }
}

void ZBarrierSetC2::verify_gc_barriers(Compile* compile, CompilePhase phase) const {
  switch(phase) {
    case BarrierSetC2::BeforeOptimize:
    case BarrierSetC2::BeforeLateInsertion:
      assert(state()->load_barrier_count() == 0, "No barriers inserted yet");
      break;
    case BarrierSetC2::BeforeMacroExpand:
      // Barrier placement should be set by now.
      verify_gc_barriers(false /*post_parse*/);
      break;
    case BarrierSetC2::BeforeCodeGen:
      // Barriers has been fully expanded.
      assert(state()->load_barrier_count() == 0, "No more macro barriers");
      verify_slippery_safepoints(compile);
      break;
    default:
      assert(0, "Phase without verification");
  }
}

// post_parse implies that there might be load barriers without uses after parsing
// That only applies when adding barriers at parse time.
void ZBarrierSetC2::verify_gc_barriers(bool post_parse) const {
  ZBarrierSetC2State* s = state();
  Compile* C = Compile::current();
  ResourceMark rm;
  VectorSet visited(Thread::current()->resource_area());

  for (int i = 0; i < s->load_barrier_count(); i++) {
    LoadBarrierNode* n = s->load_barrier_node(i);

    // The dominating barrier on the same address if it exists and
    // this barrier must not be applied on the value from the same
    // load otherwise the value is not reloaded before it's used the
    // second time.
    assert(n->in(LoadBarrierNode::Similar)->is_top() ||
           (n->in(LoadBarrierNode::Similar)->in(0)->is_LoadBarrier() &&
            n->in(LoadBarrierNode::Similar)->in(0)->in(LoadBarrierNode::Address) == n->in(LoadBarrierNode::Address) &&
            n->in(LoadBarrierNode::Similar)->in(0)->in(LoadBarrierNode::Oop) != n->in(LoadBarrierNode::Oop)),
           "broken similar edge");

    assert(n->as_LoadBarrier()->has_true_uses(),
           "found unneeded load barrier");

    // Several load barrier nodes chained through their Similar edge
    // break the code that remove the barriers in final graph reshape.
    assert(n->in(LoadBarrierNode::Similar)->is_top() ||
           (n->in(LoadBarrierNode::Similar)->in(0)->is_LoadBarrier() &&
            n->in(LoadBarrierNode::Similar)->in(0)->in(LoadBarrierNode::Similar)->is_top()),
           "chain of Similar load barriers");

    if (!n->in(LoadBarrierNode::Similar)->is_top()) {
      ResourceMark rm;
      Unique_Node_List wq;
      Node* other = n->in(LoadBarrierNode::Similar)->in(0);
      wq.push(n);
      for (uint next = 0; next < wq.size(); ++next) {
        Node *nn = wq.at(next);
        assert(nn->is_CFG(), "");
        assert(!nn->is_SafePoint(), "");

        if (nn == other) {
          continue;
        }

        if (nn->is_Region()) {
          for (uint i = 1; i < nn->req(); i++) {
            Node* m = nn->in(i);
            if (m != NULL) {
              wq.push(m);
            }
          }
        } else {
          Node* m = nn->in(0);
          if (m != NULL) {
            wq.push(m);
          }
        }
      }
    }
  }
}

#endif // end verification code

// If a call is the control, we actually want its control projection
static Node* normalize_ctrl(Node* node) {
 if (node->is_Call()) {
   node = node->as_Call()->proj_out(TypeFunc::Control);
 }
 return node;
}

static Node* get_ctrl_normalized(PhaseIdealLoop *phase, Node* node) {
  return normalize_ctrl(phase->get_ctrl(node));
}

static void call_catch_cleanup_one(PhaseIdealLoop* phase, LoadNode* load, Node* ctrl);

// This code is cloning all uses of a load that is between a call and the catch blocks,
// to each use.

static bool fixup_uses_in_catch(PhaseIdealLoop *phase, Node *start_ctrl, Node *node) {

  if (!phase->has_ctrl(node)) {
    // This node is floating - doesn't need to be cloned.
    assert(node != start_ctrl, "check");
    return false;
  }

  Node* ctrl = get_ctrl_normalized(phase, node);
  if (ctrl != start_ctrl) {
    // We are in a successor block - the node is ok.
    return false; // Unwind
  }

  // Process successor nodes
  int outcnt = node->outcnt();
  for (int i = 0; i < outcnt; i++) {
    Node* n = node->raw_out(0);
    assert(!n->is_LoadBarrier(), "Sanity");
    // Calling recursively, visiting leafs first
    fixup_uses_in_catch(phase, start_ctrl, n);
  }

  // Now all successors are outside
  // - Clone this node to both successors
  assert(!node->is_Store(), "Stores not expected here");

  // In some very rare cases a load that doesn't need a barrier will end up here
  // Treat it as a LoadP and the insertion of phis will be done correctly.
  if (node->is_Load()) {
    call_catch_cleanup_one(phase, node->as_Load(), phase->get_ctrl(node));
  } else {
    for (DUIterator_Fast jmax, i = node->fast_outs(jmax); i < jmax; i++) {
      Node* use = node->fast_out(i);
      Node* clone = node->clone();
      assert(clone->outcnt() == 0, "");

      assert(use->find_edge(node) != -1, "check");
      phase->igvn().rehash_node_delayed(use);
      use->replace_edge(node, clone);

      Node* new_ctrl;
      if (use->is_block_start()) {
        new_ctrl = use;
      } else if (use->is_CFG()) {
        new_ctrl = use->in(0);
        assert (new_ctrl != NULL, "");
      } else {
        new_ctrl = get_ctrl_normalized(phase, use);
      }

      phase->set_ctrl(clone, new_ctrl);

      if (phase->C->directive()->ZTraceLoadBarriersOption) tty->print_cr("  Clone op %i as %i to control %i", node->_idx, clone->_idx, new_ctrl->_idx);
      phase->igvn().register_new_node_with_optimizer(clone);
      --i, --jmax;
    }
    assert(node->outcnt() == 0, "must be empty now");

    // Node node is dead.
    phase->igvn().remove_dead_node(node);
  }
  return true; // unwind - return if a use was processed
}

// Clone a load to a specific catch_proj
static Node* clone_load_to_catchproj(PhaseIdealLoop* phase, Node* load, Node* catch_proj) {
  Node* cloned_load = load->clone();
  cloned_load->set_req(0, catch_proj);      // set explicit control
  phase->set_ctrl(cloned_load, catch_proj); // update
  if (phase->C->directive()->ZTraceLoadBarriersOption) tty->print_cr("  Clone LOAD %i as %i to control %i", load->_idx, cloned_load->_idx, catch_proj->_idx);
  phase->igvn().register_new_node_with_optimizer(cloned_load);
  return cloned_load;
}

static Node* get_dominating_region(PhaseIdealLoop* phase, Node* node, Node* stop) {
  Node* region = node;
  while (!region->isa_Region()) {
    Node *up = phase->idom(region);
    assert(up != region, "Must not loop");
    assert(up != stop,   "Must not find original control");
    region = up;
  }
  return region;
}

// Clone this load to each catch block
static void call_catch_cleanup_one(PhaseIdealLoop* phase, LoadNode* load, Node* ctrl) {
  bool trace = phase->C->directive()->ZTraceLoadBarriersOption;
  phase->igvn().set_delay_transform(true);

  // Verify pre conditions
  assert(ctrl->isa_Proj() && ctrl->in(0)->isa_Call(), "Must be a call proj");
  assert(ctrl->raw_out(0)->isa_Catch(), "Must be a catch");

  if (ctrl->raw_out(0)->isa_Catch()->outcnt() == 1) {
    if (trace) tty->print_cr("Cleaning up catch: Skipping load %i, call with single catch", load->_idx);
    return;
  }

  // Process the loads successor nodes - if any is between
  // the call and the catch blocks, they need to be cloned to.
  // This is done recursively
  for (uint i = 0; i < load->outcnt();) {
    Node *n = load->raw_out(i);
    assert(!n->is_LoadBarrier(), "Sanity");
    if (!fixup_uses_in_catch(phase, ctrl, n)) {
      // if no successor was cloned, progress to next out.
      i++;
    }
  }

  // Now all the loads uses has been cloned down
  // Only thing left is to clone the loads, but they must end up
  // first in the catch blocks.

  // We clone the loads oo the catch blocks only when needed.
  // An array is used to map the catch blocks to each lazily cloned load.
  // In that way no extra unnecessary loads are cloned.

  // Any use dominated by original block must have an phi and a region added

  Node* catch_node = ctrl->raw_out(0);
  int number_of_catch_projs = catch_node->outcnt();
  Node** proj_to_load_mapping = NEW_RESOURCE_ARRAY(Node*, number_of_catch_projs);
  Copy::zero_to_bytes(proj_to_load_mapping, sizeof(Node*) * number_of_catch_projs);

  // The phi_map is used to keep track of where phis have already been inserted
  int phi_map_len = phase->C->unique();
  Node** phi_map = NEW_RESOURCE_ARRAY(Node*, phi_map_len);
  Copy::zero_to_bytes(phi_map, sizeof(Node*) * phi_map_len);

  for (unsigned int i = 0; i  < load->outcnt(); i++) {
    Node* load_use_control = NULL;
    Node* load_use = load->raw_out(i);

    if (phase->has_ctrl(load_use)) {
      load_use_control = get_ctrl_normalized(phase, load_use);
      assert(load_use_control != ctrl, "sanity");
    } else {
      load_use_control = load_use->in(0);
    }
    assert(load_use_control != NULL, "sanity");
    if (trace) tty->print_cr("  Handling use: %i, with control: %i", load_use->_idx, load_use_control->_idx);

    // Some times the loads use is a phi. For them we need to determine from which catch block
    // the use is defined.
    bool load_use_is_phi = false;
    unsigned int load_use_phi_index = 0;
    Node* phi_ctrl = NULL;
    if (load_use->is_Phi()) {
      // Find phi input that matches load
      for (unsigned int u = 1; u < load_use->req(); u++) {
        if (load_use->in(u) == load) {
          load_use_is_phi = true;
          load_use_phi_index = u;
          assert(load_use->in(0)->is_Region(), "Region or broken");
          phi_ctrl = load_use->in(0)->in(u);
          assert(phi_ctrl->is_CFG(), "check");
          assert(phi_ctrl != load,   "check");
          break;
        }
      }
      assert(load_use_is_phi,        "must find");
      assert(load_use_phi_index > 0, "sanity");
    }

    // For each load use, see which catch projs dominates, create load clone lazily and reconnect
    bool found_dominating_catchproj = false;
    for (int c = 0; c < number_of_catch_projs; c++) {
      Node* catchproj = catch_node->raw_out(c);
      assert(catchproj != NULL && catchproj->isa_CatchProj(), "Sanity");

      if (!phase->is_dominator(catchproj, load_use_control)) {
        if (load_use_is_phi && phase->is_dominator(catchproj, phi_ctrl)) {
          // The loads use is local to the catchproj.
          // fall out and replace load with catch-local load clone.
        } else {
          continue;
        }
      }
      assert(!found_dominating_catchproj, "Max one should match");

      // Clone loads to catch projs
      Node* load_clone = proj_to_load_mapping[c];
      if (load_clone == NULL) {
        load_clone = clone_load_to_catchproj(phase, load, catchproj);
        proj_to_load_mapping[c] = load_clone;
      }
      phase->igvn().rehash_node_delayed(load_use);

      if (load_use_is_phi) {
        // phis are special - the load is defined from a specific control flow
        load_use->set_req(load_use_phi_index, load_clone);
      } else {
        // Multipe edges can be replaced at once - on calls for example
        load_use->replace_edge(load, load_clone);
      }
      --i; // more than one edge can have been removed, but the next is in later iterations

      // We could break the for-loop after finding a dominating match.
      // But keep iterating to catch any bad idom early.
      found_dominating_catchproj = true;
    }

    // We found no single catchproj that dominated the use - The use is at a point after
    // where control flow from multiple catch projs have merged. We will have to create
    // phi nodes before the use and tie the output from the cloned loads together. It
    // can be a single phi or a number of chained phis, depending on control flow
    if (!found_dominating_catchproj) {

      // Use phi-control if use is a phi
      if (load_use_is_phi) {
        load_use_control = phi_ctrl;
      }
      assert(phase->is_dominator(ctrl, load_use_control), "Common use but no dominator");

      // Clone a load on all paths
      for (int c = 0; c < number_of_catch_projs; c++) {
        Node* catchproj = catch_node->raw_out(c);
        Node* load_clone = proj_to_load_mapping[c];
        if (load_clone == NULL) {
          load_clone = clone_load_to_catchproj(phase, load, catchproj);
          proj_to_load_mapping[c] = load_clone;
        }
      }

      // Move up dominator tree from use until dom front is reached
      Node* next_region = get_dominating_region(phase, load_use_control, ctrl);
      while (phase->idom(next_region) != catch_node) {
        next_region = phase->idom(next_region);
        if (trace) tty->print_cr("Moving up idom to region ctrl %i", next_region->_idx);
      }
      assert(phase->is_dominator(catch_node, next_region), "Sanity");

      // Create or reuse phi node that collect all cloned loads and feed it to the use.
      Node* test_phi = phi_map[next_region->_idx];
      if ((test_phi != NULL) && test_phi->is_Phi()) {
        // Reuse an already created phi
        if (trace) tty->print_cr("    Using cached Phi %i on load_use %i", test_phi->_idx, load_use->_idx);
        phase->igvn().rehash_node_delayed(load_use);
        load_use->replace_edge(load, test_phi);
        // Now this use is done
      } else {
        // Otherwise we need to create one or more phis
        PhiNode* next_phi = new PhiNode(next_region, load->type());
        phi_map[next_region->_idx] = next_phi; // cache new phi
        phase->igvn().rehash_node_delayed(load_use);
        load_use->replace_edge(load, next_phi);

        int dominators_of_region = 0;
        do {
          // New phi, connect to region and add all loads as in.
          Node* region = next_region;
          assert(region->isa_Region() && region->req() > 2, "Catch dead region nodes");
          PhiNode* new_phi = next_phi;

          if (trace) tty->print_cr("Created Phi %i on load %i with control %i", new_phi->_idx, load->_idx, region->_idx);

          // Need to add all cloned loads to the phi, taking care that the right path is matched
          dominators_of_region = 0; // reset for new region
          for (unsigned int reg_i = 1; reg_i < region->req(); reg_i++) {
            Node* region_pred = region->in(reg_i);
            assert(region_pred->is_CFG(), "check");
            bool pred_has_dominator = false;
            for (int c = 0; c < number_of_catch_projs; c++) {
              Node* catchproj = catch_node->raw_out(c);
              if (phase->is_dominator(catchproj, region_pred)) {
                new_phi->set_req(reg_i, proj_to_load_mapping[c]);
                if (trace) tty->print_cr(" - Phi in(%i) set to load %i", reg_i, proj_to_load_mapping[c]->_idx);
                pred_has_dominator = true;
                dominators_of_region++;
                break;
              }
            }

            // Sometimes we need to chain several phis.
            if (!pred_has_dominator) {
              assert(dominators_of_region <= 1, "More than one region can't require extra phi");
              if (trace) tty->print_cr(" - Region %i pred %i not dominated by catch proj", region->_idx, region_pred->_idx);
              // Continue search on on this region_pred
              // - walk up to next region
              // - create a new phi and connect to first new_phi
              next_region = get_dominating_region(phase, region_pred, ctrl);

              // Lookup if there already is a phi, create a new otherwise
              Node* test_phi = phi_map[next_region->_idx];
              if ((test_phi != NULL) && test_phi->is_Phi()) {
                next_phi = test_phi->isa_Phi();
                dominators_of_region++; // record that a match was found and that we are done
                if (trace) tty->print_cr("    Using cached phi Phi %i on control %i", next_phi->_idx, next_region->_idx);
              } else {
                next_phi = new PhiNode(next_region, load->type());
                phi_map[next_region->_idx] = next_phi;
              }
              new_phi->set_req(reg_i, next_phi);
            }
          }

          new_phi->set_req(0, region);
          phase->igvn().register_new_node_with_optimizer(new_phi);
          phase->set_ctrl(new_phi, region);

          assert(dominators_of_region != 0, "Must have found one this iteration");
        } while (dominators_of_region == 1);
      }
      --i;
    }
  } // end of loop over uses

  assert(load->outcnt() == 0, "All uses should be handled");
  phase->igvn().remove_dead_node(load);
  phase->C->print_method(PHASE_CALL_CATCH_CLEANUP, 4, load->_idx);

  // Now we should be home
  phase->igvn().set_delay_transform(false);
}

// Sort out the loads that are between a call ant its catch blocks
static void process_catch_cleanup_candidate(PhaseIdealLoop* phase, LoadNode* load, bool verify) {
  bool trace = phase->C->directive()->ZTraceLoadBarriersOption;

  Node* ctrl = get_ctrl_normalized(phase, load);
  if (!ctrl->is_Proj() || (ctrl->in(0) == NULL) || !ctrl->in(0)->isa_Call()) {
    return;
  }

  Node* catch_node = ctrl->isa_Proj()->raw_out(0);
  if (catch_node->is_Catch()) {
    if (catch_node->outcnt() > 1) {
      assert(!verify, "All loads should already have been moved");
      call_catch_cleanup_one(phase, load, ctrl);
    } else {
      if (trace) tty->print_cr("Call catch cleanup with only one catch: load %i ", load->_idx);
    }
  }
}

void ZBarrierSetC2::barrier_insertion_phase(Compile* C, PhaseIterGVN& igvn) const {
  PhaseIdealLoop::optimize(igvn, LoopOptsZBarrierInsertion);
  if (C->failing())  return;
}

bool ZBarrierSetC2::optimize_loops(PhaseIdealLoop* phase, LoopOptsMode mode, VectorSet& visited, Node_Stack& nstack, Node_List& worklist) const {

  if (mode == LoopOptsZBarrierInsertion) {
    // First make sure all loads between call and catch are moved to the catch block
    clean_catch_blocks(phase);
    DEBUG_ONLY(clean_catch_blocks(phase, true /* verify */);)

    // Then expand barriers on all loads
    insert_load_barriers(phase);

    // Handle all Unsafe that need barriers.
    insert_barriers_on_unsafe(phase);

    phase->C->clear_major_progress();
    return true;
  } else {
    return false;
  }
}

static bool can_simplify_cas(LoadStoreNode* node) {
  if (node->isa_LoadStoreConditional()) {
    Node *expected_in = node->as_LoadStoreConditional()->in(LoadStoreConditionalNode::ExpectedIn);
    return (expected_in->get_ptr_type() == TypePtr::NULL_PTR);
  } else {
    return false;
  }
}

static void insert_barrier_before_unsafe(PhaseIdealLoop* phase, LoadStoreNode* old_node) {

  Compile *C = phase->C;
  PhaseIterGVN &igvn = phase->igvn();
  LoadStoreNode* zclone = NULL;

  Node *in_ctrl = old_node->in(MemNode::Control);
  Node *in_mem  = old_node->in(MemNode::Memory);
  Node *in_adr  = old_node->in(MemNode::Address);
  Node *in_val  = old_node->in(MemNode::ValueIn);
  const TypePtr *adr_type = old_node->adr_type();
  const TypePtr* load_type = TypeOopPtr::BOTTOM; // The type for the load we are adding

  switch (old_node->Opcode()) {
    case Op_CompareAndExchangeP: {
      zclone = new ZCompareAndExchangePNode(in_ctrl, in_mem, in_adr, in_val, old_node->in(LoadStoreConditionalNode::ExpectedIn),
              adr_type, old_node->get_ptr_type(), ((CompareAndExchangeNode*)old_node)->order());
      load_type = old_node->bottom_type()->is_ptr();
      break;
    }
    case Op_WeakCompareAndSwapP: {
      if (can_simplify_cas(old_node)) {
        break;
      }
      zclone = new ZWeakCompareAndSwapPNode(in_ctrl, in_mem, in_adr, in_val, old_node->in(LoadStoreConditionalNode::ExpectedIn),
              ((CompareAndSwapNode*)old_node)->order());
      adr_type = TypePtr::BOTTOM;
      break;
    }
    case Op_CompareAndSwapP: {
      if (can_simplify_cas(old_node)) {
        break;
      }
      zclone = new ZCompareAndSwapPNode(in_ctrl, in_mem, in_adr, in_val, old_node->in(LoadStoreConditionalNode::ExpectedIn),
              ((CompareAndSwapNode*)old_node)->order());
      adr_type = TypePtr::BOTTOM;
      break;
    }
    case Op_GetAndSetP: {
      zclone = new ZGetAndSetPNode(in_ctrl, in_mem, in_adr, in_val, old_node->adr_type(), old_node->get_ptr_type());
      load_type = old_node->bottom_type()->is_ptr();
      break;
    }
  }
  if (zclone != NULL) {
    igvn.register_new_node_with_optimizer(zclone, old_node);

    // Make load
    LoadPNode *load = new LoadPNode(NULL, in_mem, in_adr, adr_type, load_type, MemNode::unordered,
                                    LoadNode::DependsOnlyOnTest);
    load_set_expanded_barrier(load);
    igvn.register_new_node_with_optimizer(load);
    igvn.replace_node(old_node, zclone);

    Node *barrier = new LoadBarrierNode(C, NULL, in_mem, load, in_adr, false /* weak */);
    Node *barrier_val = new ProjNode(barrier, LoadBarrierNode::Oop);
    Node *barrier_ctrl = new ProjNode(barrier, LoadBarrierNode::Control);

    igvn.register_new_node_with_optimizer(barrier);
    igvn.register_new_node_with_optimizer(barrier_val);
    igvn.register_new_node_with_optimizer(barrier_ctrl);

    // loop over all of in_ctrl usages and move to barrier_ctrl
    for (DUIterator_Last imin, i = in_ctrl->last_outs(imin); i >= imin; --i) {
      Node *use = in_ctrl->last_out(i);
      uint l;
      for (l = 0; use->in(l) != in_ctrl; l++) {}
      igvn.replace_input_of(use, l, barrier_ctrl);
    }

    load->set_req(MemNode::Control, in_ctrl);
    barrier->set_req(LoadBarrierNode::Control, in_ctrl);
    zclone->add_req(barrier_val); // add req as keep alive.

    C->print_method(PHASE_ADD_UNSAFE_BARRIER, 4, zclone->_idx);
  }
}

void ZBarrierSetC2::insert_barriers_on_unsafe(PhaseIdealLoop* phase) const {
  Compile *C = phase->C;
  PhaseIterGVN &igvn = phase->igvn();
  uint new_ids = C->unique();
  VectorSet visited(Thread::current()->resource_area());
  GrowableArray<Node *> nodeStack(Thread::current()->resource_area(), 0, 0, NULL);
  nodeStack.push(C->root());
  visited.test_set(C->root()->_idx);

  // Traverse all nodes, visit all unsafe ops that require a barrier
  while (nodeStack.length() > 0) {
    Node *n = nodeStack.pop();

    bool is_old_node = (n->_idx < new_ids); // don't process nodes that were created during cleanup
    if (is_old_node) {
      if (n->is_LoadStore()) {
        LoadStoreNode* lsn = n->as_LoadStore();
        if (lsn->has_barrier()) {
          BasicType bt = lsn->in(MemNode::Address)->bottom_type()->basic_type();
          assert (is_reference_type(bt), "Sanity test");
          insert_barrier_before_unsafe(phase, lsn);
        }
      }
    }
    for (uint i = 0; i < n->len(); i++) {
      if (n->in(i)) {
        if (!visited.test_set(n->in(i)->_idx)) {
          nodeStack.push(n->in(i));
        }
      }
    }
  }

  igvn.optimize();
  C->print_method(PHASE_ADD_UNSAFE_BARRIER, 2);
}

// The purpose of ZBarrierSetC2::clean_catch_blocks is to prepare the IR for
// splicing in load barrier nodes.
//
// The problem is that we might have instructions between a call and its catch nodes.
// (This is usually handled in PhaseCFG:call_catch_cleanup, which clones mach nodes in
// already scheduled blocks.) We can't have loads that require barriers there,
// because we need to splice in new control flow, and that would violate the IR.
//
// clean_catch_blocks find all Loads that require a barrier and clone them and any
// dependent instructions to each use. The loads must be in the beginning of the catch block
// before any store.
//
// Sometimes the loads use will be at a place dominated by all catch blocks, then we need
// a load in each catch block, and a Phi at the dominated use.

void ZBarrierSetC2::clean_catch_blocks(PhaseIdealLoop* phase, bool verify) const {

  Compile *C = phase->C;
  uint new_ids = C->unique();
  PhaseIterGVN &igvn = phase->igvn();
  VectorSet visited(Thread::current()->resource_area());
  GrowableArray<Node *> nodeStack(Thread::current()->resource_area(), 0, 0, NULL);
  nodeStack.push(C->root());
  visited.test_set(C->root()->_idx);

  // Traverse all nodes, visit all loads that require a barrier
  while(nodeStack.length() > 0) {
    Node *n = nodeStack.pop();

    for (uint i = 0; i < n->len(); i++) {
      if (n->in(i)) {
        if (!visited.test_set(n->in(i)->_idx)) {
          nodeStack.push(n->in(i));
        }
      }
    }

    bool is_old_node = (n->_idx < new_ids); // don't process nodes that were created during cleanup
    if (n->is_Load() && is_old_node) {
      LoadNode* load = n->isa_Load();
      // only care about loads that will have a barrier
      if (load_require_barrier(load)) {
        process_catch_cleanup_candidate(phase, load, verify);
      }
    }
  }

  C->print_method(PHASE_CALL_CATCH_CLEANUP, 2);
}

class DomDepthCompareClosure : public CompareClosure<LoadNode*> {
  PhaseIdealLoop* _phase;

public:
  DomDepthCompareClosure(PhaseIdealLoop* phase) : _phase(phase) { }

  int do_compare(LoadNode* const &n1, LoadNode* const &n2) {
    int d1 = _phase->dom_depth(_phase->get_ctrl(n1));
    int d2 = _phase->dom_depth(_phase->get_ctrl(n2));
    if (d1 == d2) {
      // Compare index if the depth is the same, ensures all entries are unique.
      return n1->_idx - n2->_idx;
    } else {
      return d2 - d1;
    }
  }
};

// Traverse graph and add all loadPs to list, sorted by dom depth
void gather_loadnodes_sorted(PhaseIdealLoop* phase, GrowableArray<LoadNode*>* loadList) {

  VectorSet visited(Thread::current()->resource_area());
  GrowableArray<Node *> nodeStack(Thread::current()->resource_area(), 0, 0, NULL);
  DomDepthCompareClosure ddcc(phase);

  nodeStack.push(phase->C->root());
  while(nodeStack.length() > 0) {
    Node *n = nodeStack.pop();
    if (visited.test(n->_idx)) {
      continue;
    }

    if (n->isa_Load()) {
      LoadNode *load = n->as_Load();
      if (load_require_barrier(load)) {
        assert(phase->get_ctrl(load) != NULL, "sanity");
        assert(phase->dom_depth(phase->get_ctrl(load)) != 0, "sanity");
        loadList->insert_sorted(&ddcc, load);
      }
    }

    visited.set(n->_idx);
    for (uint i = 0; i < n->req(); i++) {
      if (n->in(i)) {
        if (!visited.test(n->in(i)->_idx)) {
          nodeStack.push(n->in(i));
        }
      }
    }
  }
}

// Add LoadBarriers to all LoadPs
void ZBarrierSetC2::insert_load_barriers(PhaseIdealLoop* phase) const {

  bool trace = phase->C->directive()->ZTraceLoadBarriersOption;
  GrowableArray<LoadNode *> loadList(Thread::current()->resource_area(), 0, 0, NULL);
  gather_loadnodes_sorted(phase, &loadList);

  PhaseIterGVN &igvn = phase->igvn();
  int count = 0;

  for (GrowableArrayIterator<LoadNode *> loadIter = loadList.begin(); loadIter != loadList.end(); ++loadIter) {
    LoadNode *load = *loadIter;

    if (load_has_expanded_barrier(load)) {
      continue;
    }

    do {
      // Insert a barrier on a loadP
      // if another load is found that needs to be expanded first, retry on that one
      LoadNode* result = insert_one_loadbarrier(phase, load, phase->get_ctrl(load));
      while (result != NULL) {
        result = insert_one_loadbarrier(phase, result, phase->get_ctrl(result));
      }
    } while (!load_has_expanded_barrier(load));
  }

  phase->C->print_method(PHASE_INSERT_BARRIER, 2);
}

void push_antidependent_stores(PhaseIdealLoop* phase, Node_Stack& nodestack, LoadNode* start_load) {
  // push all stores on the same mem, that can_alias
  // Any load found must be handled first
  PhaseIterGVN &igvn = phase->igvn();
  int load_alias_idx = igvn.C->get_alias_index(start_load->adr_type());

  Node *mem = start_load->in(1);
  for (DUIterator_Fast imax, u = mem->fast_outs(imax); u < imax; u++) {
    Node *mem_use = mem->fast_out(u);

    if (mem_use == start_load) continue;
    if (!mem_use->is_Store()) continue;
    if (!phase->has_ctrl(mem_use)) continue;
    if (phase->get_ctrl(mem_use) != phase->get_ctrl(start_load)) continue;

    // add any aliasing store in this block
    StoreNode *store = mem_use->isa_Store();
    const TypePtr *adr_type = store->adr_type();
    if (igvn.C->can_alias(adr_type, load_alias_idx)) {
      nodestack.push(store, 0);
    }
  }
}

LoadNode* ZBarrierSetC2::insert_one_loadbarrier(PhaseIdealLoop* phase, LoadNode* start_load, Node* ctrl) const {
  bool trace = phase->C->directive()->ZTraceLoadBarriersOption;
  PhaseIterGVN &igvn = phase->igvn();

  // Check for other loadPs at the same loop depth that is reachable by a DFS
  // - if found - return it. It needs to be inserted first
  // - otherwise proceed and insert barrier

  VectorSet visited(Thread::current()->resource_area());
  Node_Stack nodestack(100);

  nodestack.push(start_load, 0);
  push_antidependent_stores(phase, nodestack, start_load);

  while(!nodestack.is_empty()) {
    Node* n = nodestack.node(); // peek
    nodestack.pop();
    if (visited.test(n->_idx)) {
      continue;
    }

    if (n->is_Load() && n != start_load && load_require_barrier(n->as_Load()) && !load_has_expanded_barrier(n->as_Load())) {
      // Found another load that needs a barrier in the same block. Must expand later loads first.
      if (trace) tty->print_cr(" * Found LoadP %i on DFS", n->_idx);
      return n->as_Load(); // return node that should be expanded first
    }

    if (!phase->has_ctrl(n)) continue;
    if (phase->get_ctrl(n) != phase->get_ctrl(start_load)) continue;
    if (n->is_Phi()) continue;

    visited.set(n->_idx);
    // push all children
    for (DUIterator_Fast imax, ii = n->fast_outs(imax); ii < imax; ii++) {
      Node* c = n->fast_out(ii);
      if (c != NULL) {
        nodestack.push(c, 0);
      }
    }
  }

  insert_one_loadbarrier_inner(phase, start_load, ctrl, visited);
  return NULL;
}

void ZBarrierSetC2::insert_one_loadbarrier_inner(PhaseIdealLoop* phase, LoadNode* load, Node* ctrl, VectorSet visited2) const {
  PhaseIterGVN &igvn = phase->igvn();
  Compile* C = igvn.C;
  bool trace = C->directive()->ZTraceLoadBarriersOption;

  // create barrier
  Node* barrier = new LoadBarrierNode(C, NULL, load->in(LoadNode::Memory), NULL, load->in(LoadNode::Address), load_has_weak_barrier(load));
  Node* barrier_val = new ProjNode(barrier, LoadBarrierNode::Oop);
  Node* barrier_ctrl = new ProjNode(barrier, LoadBarrierNode::Control);
  ctrl = normalize_ctrl(ctrl);

  if (trace) tty->print_cr("Insert load %i with barrier: %i and ctrl : %i", load->_idx, barrier->_idx, ctrl->_idx);

  // Splice control
  // - insert barrier control diamond between loads ctrl and ctrl successor on path to block end.
  // - If control successor is a catch, step over to next.
  Node* ctrl_succ = NULL;
  for (DUIterator_Fast imax, j = ctrl->fast_outs(imax); j < imax; j++) {
    Node* tmp = ctrl->fast_out(j);

    // - CFG nodes is the ones we are going to splice (1 only!)
    // - Phi nodes will continue to hang from the region node!
    // - self loops should be skipped
    if (tmp->is_Phi() || tmp == ctrl) {
      continue;
    }

    if (tmp->is_CFG()) {
      assert(ctrl_succ == NULL, "There can be only one");
      ctrl_succ = tmp;
      continue;
    }
  }

  // Now splice control
  assert(ctrl_succ != load, "sanity");
  assert(ctrl_succ != NULL, "Broken IR");
  bool found = false;
  for(uint k = 0; k < ctrl_succ->req(); k++) {
    if (ctrl_succ->in(k) == ctrl) {
      assert(!found, "sanity");
      if (trace) tty->print_cr(" Move CFG ctrl_succ %i to barrier_ctrl", ctrl_succ->_idx);
      igvn.replace_input_of(ctrl_succ, k, barrier_ctrl);
      found = true;
      k--;
    }
  }

  // For all successors of ctrl - move all visited to become successors of barrier_ctrl instead
  for (DUIterator_Fast imax, r = ctrl->fast_outs(imax); r < imax; r++) {
    Node* tmp = ctrl->fast_out(r);
    if (tmp->is_SafePoint() || (visited2.test(tmp->_idx) && (tmp != load))) {
      if (trace) tty->print_cr(" Move ctrl_succ %i to barrier_ctrl", tmp->_idx);
      igvn.replace_input_of(tmp, 0, barrier_ctrl);
      --r; --imax;
    }
  }

  // Move the loads user to the barrier
  for (DUIterator_Fast imax, i = load->fast_outs(imax); i < imax; i++) {
    Node* u = load->fast_out(i);
    if (u->isa_LoadBarrier()) {
      continue;
    }

    // find correct input  - replace with iterator?
    for(uint j = 0; j < u->req(); j++) {
      if (u->in(j) == load) {
        igvn.replace_input_of(u, j, barrier_val);
        --i; --imax; // Adjust the iterator of the *outer* loop
        break; // some nodes (calls) might have several uses from the same node
      }
    }
  }

  // Connect barrier to load and control
  barrier->set_req(LoadBarrierNode::Oop, load);
  barrier->set_req(LoadBarrierNode::Control, ctrl);

  igvn.replace_input_of(load, MemNode::Control, ctrl);
  load->pin();

  igvn.rehash_node_delayed(load);
  igvn.register_new_node_with_optimizer(barrier);
  igvn.register_new_node_with_optimizer(barrier_val);
  igvn.register_new_node_with_optimizer(barrier_ctrl);
  load_set_expanded_barrier(load);

  C->print_method(PHASE_INSERT_BARRIER, 3, load->_idx);
}

// The bad_mask in the ThreadLocalData shouldn't have an anti-dep-check.
// The bad_mask address if of type TypeRawPtr, but that will alias
// InitializeNodes until the type system is expanded.
bool ZBarrierSetC2::needs_anti_dependence_check(const Node* node) const {
  MachNode* mnode = node->as_Mach();
  if (mnode != NULL) {
    intptr_t offset = 0;
    const TypePtr *adr_type2 = NULL;
    const Node* base = mnode->get_base_and_disp(offset, adr_type2);
    if ((base != NULL) &&
        (base->is_Mach() && base->as_Mach()->ideal_Opcode() == Op_ThreadLocal) &&
        (offset == in_bytes(ZThreadLocalData::address_bad_mask_offset()))) {
      return false;
    }
  }
  return true;
}
