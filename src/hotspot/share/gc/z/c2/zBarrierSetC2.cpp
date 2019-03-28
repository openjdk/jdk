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
#include "opto/compile.hpp"
#include "opto/castnode.hpp"
#include "opto/escape.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"
#include "opto/loopnode.hpp"
#include "opto/macro.hpp"
#include "opto/node.hpp"
#include "opto/type.hpp"
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
    if (n != NULL && (n->is_LoadBarrierSlowReg() ||  n->is_LoadBarrierWeakSlowReg())) {
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

void ZBarrierSetC2::find_dominating_barriers(PhaseIterGVN& igvn) {
  // Look for dominating barriers on the same address only once all
  // other loop opts are over. Loop opts may cause a safepoint to be
  // inserted between a barrier and its dominating barrier.
  Compile* C = Compile::current();
  ZBarrierSetC2* bs = (ZBarrierSetC2*)BarrierSet::barrier_set()->barrier_set_c2();
  ZBarrierSetC2State* s = bs->state();
  if (s->load_barrier_count() >= 2) {
    Compile::TracePhase tp("idealLoop", &C->timers[Phase::_t_idealLoop]);
    PhaseIdealLoop::optimize(igvn, LoopOptsLastRound);
    if (C->major_progress()) C->print_method(PHASE_PHASEIDEALLOOP_ITERATIONS, 2);
  }
}

void ZBarrierSetC2::add_users_to_worklist(Unique_Node_List* worklist) const {
  // Permanent temporary workaround
  // Loadbarriers may have non-obvious dead uses keeping them alive during parsing. The use is
  // removed by RemoveUseless (after parsing, before optimize) but the barriers won't be added to
  // the worklist. Unless we add them explicitly they are not guaranteed to end up there.
  ZBarrierSetC2State* s = state();

  for (int i = 0; i < s->load_barrier_count(); i++) {
    LoadBarrierNode* n = s->load_barrier_node(i);
    worklist->push(n);
  }
}

const TypeFunc* ZBarrierSetC2::load_barrier_Type() const {
  const Type** fields;

  // Create input types (domain)
  fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;
  fields[TypeFunc::Parms+1] = TypeOopPtr::BOTTOM;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // Create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::BOTTOM;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

// == LoadBarrierNode ==

LoadBarrierNode::LoadBarrierNode(Compile* C,
                                 Node* c,
                                 Node* mem,
                                 Node* val,
                                 Node* adr,
                                 bool weak,
                                 bool writeback,
                                 bool oop_reload_allowed) :
    MultiNode(Number_of_Inputs),
    _weak(weak),
    _writeback(writeback),
    _oop_reload_allowed(oop_reload_allowed) {
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
  floadbarrier[Memory] = Type::MEMORY;
  floadbarrier[Oop] = val_t;
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

  if (ZVerifyLoadBarriers || can_be_eliminated()) {
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
          return u->as_LoadBarrier();;
        }
        break;
      }
    }
  }

  return NULL;
}

void LoadBarrierNode::push_dominated_barriers(PhaseIterGVN* igvn) const {
  // Change to that barrier may affect a dominated barrier so re-push those
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
  if (!phase->C->directive()->ZOptimizeLoadBarriersOption) {
    return this;
  }

  bool redundant_addr = false;
  LoadBarrierNode* dominating_barrier = has_dominating_barrier(NULL, true, false);
  if (dominating_barrier != NULL) {
    assert(dominating_barrier->in(Oop) == in(Oop), "");
    return dominating_barrier;
  }

  return this;
}

Node *LoadBarrierNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (remove_dead_region(phase, can_reshape)) {
    return this;
  }

  Node* val = in(Oop);
  Node* mem = in(Memory);
  Node* ctrl = in(Control);
  Node* adr = in(Address);
  assert(val->Opcode() != Op_LoadN, "");

  if (mem->is_MergeMem()) {
    Node* new_mem = mem->as_MergeMem()->memory_at(Compile::AliasIdxRaw);
    set_req(Memory, new_mem);
    if (mem->outcnt() == 0 && can_reshape) {
      phase->is_IterGVN()->_worklist.push(mem);
    }

    return this;
  }

  bool optimizeLoadBarriers = phase->C->directive()->ZOptimizeLoadBarriersOption;
  LoadBarrierNode* dominating_barrier = optimizeLoadBarriers ? has_dominating_barrier(NULL, !can_reshape, !phase->C->major_progress()) : NULL;
  if (dominating_barrier != NULL && dominating_barrier->in(Oop) != in(Oop)) {
    assert(in(Address) == dominating_barrier->in(Address), "");
    set_req(Similar, dominating_barrier->proj_out(Oop));
    return this;
  }

  bool eliminate = (optimizeLoadBarriers && !(val->is_Phi() || val->Opcode() == Op_LoadP || val->Opcode() == Op_GetAndSetP || val->is_DecodeN())) ||
                   (can_reshape && (dominating_barrier != NULL || !has_true_uses()));

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

  if (can_reshape) {
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
  if (out_res == NULL) {
    return false;
  }

  for (DUIterator_Fast imax, i = out_res->fast_outs(imax); i < imax; i++) {
    Node* u = out_res->fast_out(i);
    if (!u->is_LoadBarrier() || u->in(Similar) != out_res) {
      return true;
    }
  }

  return false;
}

// == Accesses ==

Node* ZBarrierSetC2::make_cas_loadbarrier(C2AtomicParseAccess& access) const {
  assert(!UseCompressedOops, "Not allowed");
  CompareAndSwapNode* cas = (CompareAndSwapNode*)access.raw_access();
  PhaseGVN& gvn = access.gvn();
  Compile* C = Compile::current();
  GraphKit* kit = access.kit();

  Node* in_ctrl     = cas->in(MemNode::Control);
  Node* in_mem      = cas->in(MemNode::Memory);
  Node* in_adr      = cas->in(MemNode::Address);
  Node* in_val      = cas->in(MemNode::ValueIn);
  Node* in_expected = cas->in(LoadStoreConditionalNode::ExpectedIn);

  float likely                   = PROB_LIKELY(0.999);

  const TypePtr *adr_type        = gvn.type(in_adr)->isa_ptr();
  Compile::AliasType* alias_type = C->alias_type(adr_type);
  int alias_idx                  = C->get_alias_index(adr_type);

  // Outer check - true: continue, false: load and check
  Node* region   = new RegionNode(3);
  Node* phi      = new PhiNode(region, TypeInt::BOOL);
  Node* phi_mem  = new PhiNode(region, Type::MEMORY, adr_type);

  // Inner check - is the healed ref equal to the expected
  Node* region2  = new RegionNode(3);
  Node* phi2     = new PhiNode(region2, TypeInt::BOOL);
  Node* phi_mem2 = new PhiNode(region2, Type::MEMORY, adr_type);

  // CAS node returns 0 or 1
  Node* cmp     = gvn.transform(new CmpINode(cas, kit->intcon(0)));
  Node* bol     = gvn.transform(new BoolNode(cmp, BoolTest::ne))->as_Bool();
  IfNode* iff   = gvn.transform(new IfNode(in_ctrl, bol, likely, COUNT_UNKNOWN))->as_If();
  Node* then    = gvn.transform(new IfTrueNode(iff));
  Node* elsen   = gvn.transform(new IfFalseNode(iff));

  Node* scmemproj1   = gvn.transform(new SCMemProjNode(cas));

  kit->set_memory(scmemproj1, alias_idx);
  phi_mem->init_req(1, scmemproj1);
  phi_mem2->init_req(2, scmemproj1);

  // CAS fail - reload and heal oop
  Node* reload      = kit->make_load(elsen, in_adr, TypeOopPtr::BOTTOM, T_OBJECT, MemNode::unordered);
  Node* barrier     = gvn.transform(new LoadBarrierNode(C, elsen, scmemproj1, reload, in_adr, false, true, false));
  Node* barrierctrl = gvn.transform(new ProjNode(barrier, LoadBarrierNode::Control));
  Node* barrierdata = gvn.transform(new ProjNode(barrier, LoadBarrierNode::Oop));

  // Check load
  Node* tmpX    = gvn.transform(new CastP2XNode(NULL, barrierdata));
  Node* in_expX = gvn.transform(new CastP2XNode(NULL, in_expected));
  Node* cmp2    = gvn.transform(new CmpXNode(tmpX, in_expX));
  Node *bol2    = gvn.transform(new BoolNode(cmp2, BoolTest::ne))->as_Bool();
  IfNode* iff2  = gvn.transform(new IfNode(barrierctrl, bol2, likely, COUNT_UNKNOWN))->as_If();
  Node* then2   = gvn.transform(new IfTrueNode(iff2));
  Node* elsen2  = gvn.transform(new IfFalseNode(iff2));

  // redo CAS
  Node* cas2       = gvn.transform(new CompareAndSwapPNode(elsen2, kit->memory(alias_idx), in_adr, in_val, in_expected, cas->order()));
  Node* scmemproj2 = gvn.transform(new SCMemProjNode(cas2));
  kit->set_control(elsen2);
  kit->set_memory(scmemproj2, alias_idx);

  // Merge inner flow - check if healed oop was equal too expected.
  region2->set_req(1, kit->control());
  region2->set_req(2, then2);
  phi2->set_req(1, cas2);
  phi2->set_req(2, kit->intcon(0));
  phi_mem2->init_req(1, scmemproj2);
  kit->set_memory(phi_mem2, alias_idx);

  // Merge outer flow - then check if first CAS succeeded
  region->set_req(1, then);
  region->set_req(2, region2);
  phi->set_req(1, kit->intcon(1));
  phi->set_req(2, phi2);
  phi_mem->init_req(2, phi_mem2);
  kit->set_memory(phi_mem, alias_idx);

  gvn.transform(region2);
  gvn.transform(phi2);
  gvn.transform(phi_mem2);
  gvn.transform(region);
  gvn.transform(phi);
  gvn.transform(phi_mem);

  kit->set_control(region);
  kit->insert_mem_bar(Op_MemBarCPUOrder);

  return phi;
}

Node* ZBarrierSetC2::make_cmpx_loadbarrier(C2AtomicParseAccess& access) const {
  CompareAndExchangePNode* cmpx = (CompareAndExchangePNode*)access.raw_access();
  GraphKit* kit = access.kit();
  PhaseGVN& gvn = kit->gvn();
  Compile* C = Compile::current();

  Node* in_ctrl     = cmpx->in(MemNode::Control);
  Node* in_mem      = cmpx->in(MemNode::Memory);
  Node* in_adr      = cmpx->in(MemNode::Address);
  Node* in_val      = cmpx->in(MemNode::ValueIn);
  Node* in_expected = cmpx->in(LoadStoreConditionalNode::ExpectedIn);

  float likely                   = PROB_LIKELY(0.999);

  const TypePtr *adr_type        = cmpx->get_ptr_type();
  Compile::AliasType* alias_type = C->alias_type(adr_type);
  int alias_idx                  = C->get_alias_index(adr_type);

  // Outer check - true: continue, false: load and check
  Node* region  = new RegionNode(3);
  Node* phi     = new PhiNode(region, adr_type);

  // Inner check - is the healed ref equal to the expected
  Node* region2 = new RegionNode(3);
  Node* phi2    = new PhiNode(region2, adr_type);

  // Check if cmpx succeeded
  Node* cmp     = gvn.transform(new CmpPNode(cmpx, in_expected));
  Node* bol     = gvn.transform(new BoolNode(cmp, BoolTest::eq))->as_Bool();
  IfNode* iff   = gvn.transform(new IfNode(in_ctrl, bol, likely, COUNT_UNKNOWN))->as_If();
  Node* then    = gvn.transform(new IfTrueNode(iff));
  Node* elsen   = gvn.transform(new IfFalseNode(iff));

  Node* scmemproj1  = gvn.transform(new SCMemProjNode(cmpx));
  kit->set_memory(scmemproj1, alias_idx);

  // CAS fail - reload and heal oop
  Node* reload      = kit->make_load(elsen, in_adr, TypeOopPtr::BOTTOM, T_OBJECT, MemNode::unordered);
  Node* barrier     = gvn.transform(new LoadBarrierNode(C, elsen, scmemproj1, reload, in_adr, false, true, false));
  Node* barrierctrl = gvn.transform(new ProjNode(barrier, LoadBarrierNode::Control));
  Node* barrierdata = gvn.transform(new ProjNode(barrier, LoadBarrierNode::Oop));

  // Check load
  Node* tmpX    = gvn.transform(new CastP2XNode(NULL, barrierdata));
  Node* in_expX = gvn.transform(new CastP2XNode(NULL, in_expected));
  Node* cmp2    = gvn.transform(new CmpXNode(tmpX, in_expX));
  Node *bol2    = gvn.transform(new BoolNode(cmp2, BoolTest::ne))->as_Bool();
  IfNode* iff2  = gvn.transform(new IfNode(barrierctrl, bol2, likely, COUNT_UNKNOWN))->as_If();
  Node* then2   = gvn.transform(new IfTrueNode(iff2));
  Node* elsen2  = gvn.transform(new IfFalseNode(iff2));

  // Redo CAS
  Node* cmpx2      = gvn.transform(new CompareAndExchangePNode(elsen2, kit->memory(alias_idx), in_adr, in_val, in_expected, adr_type, cmpx->get_ptr_type(), cmpx->order()));
  Node* scmemproj2 = gvn.transform(new SCMemProjNode(cmpx2));
  kit->set_control(elsen2);
  kit->set_memory(scmemproj2, alias_idx);

  // Merge inner flow - check if healed oop was equal too expected.
  region2->set_req(1, kit->control());
  region2->set_req(2, then2);
  phi2->set_req(1, cmpx2);
  phi2->set_req(2, barrierdata);

  // Merge outer flow - then check if first cas succeeded
  region->set_req(1, then);
  region->set_req(2, region2);
  phi->set_req(1, cmpx);
  phi->set_req(2, phi2);

  gvn.transform(region2);
  gvn.transform(phi2);
  gvn.transform(region);
  gvn.transform(phi);

  kit->set_control(region);
  kit->set_memory(in_mem, alias_idx);
  kit->insert_mem_bar(Op_MemBarCPUOrder);

  return phi;
}

Node* ZBarrierSetC2::load_barrier(GraphKit* kit, Node* val, Node* adr, bool weak, bool writeback, bool oop_reload_allowed) const {
  PhaseGVN& gvn = kit->gvn();
  Node* barrier = new LoadBarrierNode(Compile::current(), kit->control(), kit->memory(TypeRawPtr::BOTTOM), val, adr, weak, writeback, oop_reload_allowed);
  Node* transformed_barrier = gvn.transform(barrier);

  if (transformed_barrier->is_LoadBarrier()) {
    if (barrier == transformed_barrier) {
      kit->set_control(gvn.transform(new ProjNode(barrier, LoadBarrierNode::Control)));
    }
    Node* result = gvn.transform(new ProjNode(transformed_barrier, LoadBarrierNode::Oop));
    return result;
  } else {
    return val;
  }
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

  assert(access.is_parse_access(), "entry not supported at optimization time");
  C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);
  GraphKit* kit = parse_access.kit();
  PhaseGVN& gvn = kit->gvn();
  Node* adr = access.addr().node();
  Node* heap_base_oop = access.base();
  bool unsafe = (access.decorators() & C2_UNSAFE_ACCESS) != 0;
  if (unsafe) {
    if (!ZVerifyLoadBarriers) {
      p = load_barrier(kit, p, adr);
    } else {
      if (!TypePtr::NULL_PTR->higher_equal(gvn.type(heap_base_oop))) {
        p = load_barrier(kit, p, adr);
      } else {
        IdealKit ideal(kit);
        IdealVariable res(ideal);
#define __ ideal.
        __ declarations_done();
        __ set(res, p);
        __ if_then(heap_base_oop, BoolTest::ne, kit->null(), PROB_UNLIKELY(0.999)); {
          kit->sync_kit(ideal);
          p = load_barrier(kit, p, adr);
          __ set(res, p);
          __ sync_kit(kit);
        } __ end_if();
        kit->final_sync(ideal);
        p = __ value(res);
#undef __
      }
    }
    return p;
  } else {
    return load_barrier(parse_access.kit(), p, access.addr().node(), weak, true, true);
  }
}

Node* ZBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                    Node* new_val, const Type* val_type) const {
  Node* result = BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, val_type);
  if (!barrier_needed(access)) {
    return result;
  }

  access.set_needs_pinning(false);
  return make_cmpx_loadbarrier(access);
}

Node* ZBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                     Node* new_val, const Type* value_type) const {
  Node* result = BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
  if (!barrier_needed(access)) {
    return result;
  }

  Node* load_store = access.raw_access();
  bool weak_cas = (access.decorators() & C2_WEAK_CMPXCHG) != 0;
  bool expected_is_null = (expected_val->get_ptr_type() == TypePtr::NULL_PTR);

  if (!expected_is_null) {
    if (weak_cas) {
      access.set_needs_pinning(false);
      load_store = make_cas_loadbarrier(access);
    } else {
      access.set_needs_pinning(false);
      load_store = make_cas_loadbarrier(access);
    }
  }

  return load_store;
}

Node* ZBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* val_type) const {
  Node* result = BarrierSetC2::atomic_xchg_at_resolved(access, new_val, val_type);
  if (!barrier_needed(access)) {
    return result;
  }

  Node* load_store = access.raw_access();
  Node* adr = access.addr().node();

  assert(access.is_parse_access(), "entry not supported at optimization time");
  C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);
  return load_barrier(parse_access.kit(), load_store, adr, false, false, false);
}

// == Macro Expansion ==

void ZBarrierSetC2::expand_loadbarrier_node(PhaseMacroExpand* phase, LoadBarrierNode* barrier) const {
  Node* in_ctrl = barrier->in(LoadBarrierNode::Control);
  Node* in_mem  = barrier->in(LoadBarrierNode::Memory);
  Node* in_val  = barrier->in(LoadBarrierNode::Oop);
  Node* in_adr  = barrier->in(LoadBarrierNode::Address);

  Node* out_ctrl = barrier->proj_out(LoadBarrierNode::Control);
  Node* out_res  = barrier->proj_out(LoadBarrierNode::Oop);

  PhaseIterGVN &igvn = phase->igvn();

  if (ZVerifyLoadBarriers) {
    igvn.replace_node(out_res, in_val);
    igvn.replace_node(out_ctrl, in_ctrl);
    return;
  }

  if (barrier->can_be_eliminated()) {
    // Clone and pin the load for this barrier below the dominating
    // barrier: the load cannot be allowed to float above the
    // dominating barrier
    Node* load = in_val;

    if (load->is_Load()) {
      Node* new_load = load->clone();
      Node* addp = new_load->in(MemNode::Address);
      assert(addp->is_AddP() || addp->is_Phi() || addp->is_Load(), "bad address");
      Node* cast = new CastPPNode(addp, igvn.type(addp), true);
      Node* ctrl = NULL;
      Node* similar = barrier->in(LoadBarrierNode::Similar);
      if (similar->is_Phi()) {
        // already expanded
        ctrl = similar->in(0);
      } else {
        assert(similar->is_Proj() && similar->in(0)->is_LoadBarrier(), "unexpected graph shape");
        ctrl = similar->in(0)->as_LoadBarrier()->proj_out(LoadBarrierNode::Control);
      }
      assert(ctrl != NULL, "bad control");
      cast->set_req(0, ctrl);
      igvn.transform(cast);
      new_load->set_req(MemNode::Address, cast);
      igvn.transform(new_load);

      igvn.replace_node(out_res, new_load);
      igvn.replace_node(out_ctrl, in_ctrl);
      return;
    }
    // cannot eliminate
  }

  // There are two cases that require the basic loadbarrier
  // 1) When the writeback of a healed oop must be avoided (swap)
  // 2) When we must guarantee that no reload of is done (swap, cas, cmpx)
  if (!barrier->is_writeback()) {
    assert(!barrier->oop_reload_allowed(), "writeback barriers should be marked as requires oop");
  }

  if (!barrier->oop_reload_allowed()) {
    expand_loadbarrier_basic(phase, barrier);
  } else {
    expand_loadbarrier_optimized(phase, barrier);
  }
}

// Basic loadbarrier using conventional argument passing
void ZBarrierSetC2::expand_loadbarrier_basic(PhaseMacroExpand* phase, LoadBarrierNode *barrier) const {
  PhaseIterGVN &igvn = phase->igvn();

  Node* in_ctrl = barrier->in(LoadBarrierNode::Control);
  Node* in_mem  = barrier->in(LoadBarrierNode::Memory);
  Node* in_val  = barrier->in(LoadBarrierNode::Oop);
  Node* in_adr  = barrier->in(LoadBarrierNode::Address);

  Node* out_ctrl = barrier->proj_out(LoadBarrierNode::Control);
  Node* out_res  = barrier->proj_out(LoadBarrierNode::Oop);

  float unlikely  = PROB_UNLIKELY(0.999);
  const Type* in_val_maybe_null_t = igvn.type(in_val);

  Node* jthread = igvn.transform(new ThreadLocalNode());
  Node* adr = phase->basic_plus_adr(jthread, in_bytes(ZThreadLocalData::address_bad_mask_offset()));
  Node* bad_mask = igvn.transform(LoadNode::make(igvn, in_ctrl, in_mem, adr, TypeRawPtr::BOTTOM, TypeX_X, TypeX_X->basic_type(), MemNode::unordered));
  Node* cast = igvn.transform(new CastP2XNode(in_ctrl, in_val));
  Node* obj_masked = igvn.transform(new AndXNode(cast, bad_mask));
  Node* cmp = igvn.transform(new CmpXNode(obj_masked, igvn.zerocon(TypeX_X->basic_type())));
  Node *bol = igvn.transform(new BoolNode(cmp, BoolTest::ne))->as_Bool();
  IfNode* iff = igvn.transform(new IfNode(in_ctrl, bol, unlikely, COUNT_UNKNOWN))->as_If();
  Node* then = igvn.transform(new IfTrueNode(iff));
  Node* elsen = igvn.transform(new IfFalseNode(iff));

  Node* result_region;
  Node* result_val;

  result_region = new RegionNode(3);
  result_val = new PhiNode(result_region, TypeInstPtr::BOTTOM);

  result_region->set_req(1, elsen);
  Node* res = igvn.transform(new CastPPNode(in_val, in_val_maybe_null_t));
  res->init_req(0, elsen);
  result_val->set_req(1, res);

  const TypeFunc *tf = load_barrier_Type();
  Node* call;
  if (barrier->is_weak()) {
    call = new CallLeafNode(tf,
                            ZBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded_addr(),
                            "ZBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded",
                            TypeRawPtr::BOTTOM);
  } else {
    call = new CallLeafNode(tf,
                            ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(),
                            "ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded",
                            TypeRawPtr::BOTTOM);
  }

  call->init_req(TypeFunc::Control, then);
  call->init_req(TypeFunc::I_O    , phase->top());
  call->init_req(TypeFunc::Memory , in_mem);
  call->init_req(TypeFunc::FramePtr, phase->top());
  call->init_req(TypeFunc::ReturnAdr, phase->top());
  call->init_req(TypeFunc::Parms+0, in_val);
  if (barrier->is_writeback()) {
    call->init_req(TypeFunc::Parms+1, in_adr);
  } else {
    // When slow path is called with a null address, the healed oop will not be written back
    call->init_req(TypeFunc::Parms+1, igvn.zerocon(T_OBJECT));
  }
  call = igvn.transform(call);

  Node* ctrl = igvn.transform(new ProjNode(call, TypeFunc::Control));
  res = igvn.transform(new ProjNode(call, TypeFunc::Parms));
  res = igvn.transform(new CheckCastPPNode(ctrl, res, in_val_maybe_null_t));

  result_region->set_req(2, ctrl);
  result_val->set_req(2, res);

  result_region = igvn.transform(result_region);
  result_val = igvn.transform(result_val);

  if (out_ctrl != NULL) { // Added if cond
    igvn.replace_node(out_ctrl, result_region);
  }
  igvn.replace_node(out_res, result_val);
}

// Optimized, low spill, loadbarrier variant using stub specialized on register used
void ZBarrierSetC2::expand_loadbarrier_optimized(PhaseMacroExpand* phase, LoadBarrierNode *barrier) const {
  PhaseIterGVN &igvn = phase->igvn();
#ifdef PRINT_NODE_TRAVERSALS
  Node* preceding_barrier_node = barrier->in(LoadBarrierNode::Oop);
#endif

  Node* in_ctrl = barrier->in(LoadBarrierNode::Control);
  Node* in_mem = barrier->in(LoadBarrierNode::Memory);
  Node* in_val = barrier->in(LoadBarrierNode::Oop);
  Node* in_adr = barrier->in(LoadBarrierNode::Address);

  Node* out_ctrl = barrier->proj_out(LoadBarrierNode::Control);
  Node* out_res = barrier->proj_out(LoadBarrierNode::Oop);

  assert(barrier->in(LoadBarrierNode::Oop) != NULL, "oop to loadbarrier node cannot be null");

#ifdef PRINT_NODE_TRAVERSALS
  tty->print("\n\n\nBefore barrier optimization:\n");
  traverse(barrier, out_ctrl, out_res, -1);

  tty->print("\nBefore barrier optimization:  preceding_barrier_node\n");
  traverse(preceding_barrier_node, out_ctrl, out_res, -1);
#endif

  float unlikely  = PROB_UNLIKELY(0.999);

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

  Node* slow_path_surrogate;
  if (!barrier->is_weak()) {
    slow_path_surrogate = igvn.transform(new LoadBarrierSlowRegNode(then, in_mem, in_adr, in_val->adr_type(),
                                                                    (const TypePtr*) in_val->bottom_type(), MemNode::unordered));
  } else {
    slow_path_surrogate = igvn.transform(new LoadBarrierWeakSlowRegNode(then, in_mem, in_adr, in_val->adr_type(),
                                                                        (const TypePtr*) in_val->bottom_type(), MemNode::unordered));
  }

  Node *new_loadp;
  new_loadp = slow_path_surrogate;
  // Create the final region/phi pair to converge cntl/data paths to downstream code
  Node* result_region = igvn.transform(new RegionNode(3));
  result_region->set_req(1, then);
  result_region->set_req(2, elsen);

  Node* result_phi = igvn.transform(new PhiNode(result_region, TypeInstPtr::BOTTOM));
  result_phi->set_req(1, new_loadp);
  result_phi->set_req(2, barrier->in(LoadBarrierNode::Oop));

  // Finally, connect the original outputs to the barrier region and phi to complete the expansion/substitution
  // igvn.replace_node(out_ctrl, result_region);
  if (out_ctrl != NULL) { // added if cond
    igvn.replace_node(out_ctrl, result_region);
  }
  igvn.replace_node(out_res, result_phi);

  assert(barrier->outcnt() == 0,"LoadBarrier macro node has non-null outputs after expansion!");

#ifdef PRINT_NODE_TRAVERSALS
  tty->print("\nAfter barrier optimization:  old out_ctrl\n");
  traverse(out_ctrl, out_ctrl, out_res, -1);
  tty->print("\nAfter barrier optimization:  old out_res\n");
  traverse(out_res, out_ctrl, out_res, -1);
  tty->print("\nAfter barrier optimization:  old barrier\n");
  traverse(barrier, out_ctrl, out_res, -1);
  tty->print("\nAfter barrier optimization:  preceding_barrier_node\n");
  traverse(preceding_barrier_node, result_region, result_phi, -1);
#endif

  assert(is_gc_barrier_node(result_phi), "sanity");
  assert(step_over_gc_barrier(result_phi) == in_val, "sanity");
}

bool ZBarrierSetC2::expand_barriers(Compile* C, PhaseIterGVN& igvn) const {
  ZBarrierSetC2State* s = state();
  if (s->load_barrier_count() > 0) {
    PhaseMacroExpand macro(igvn);
#ifdef ASSERT
    verify_gc_barriers(false);
#endif
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

// == Loop optimization ==

static bool replace_with_dominating_barrier(PhaseIdealLoop* phase, LoadBarrierNode* lb, bool last_round) {
  PhaseIterGVN &igvn = phase->igvn();
  Compile* C = Compile::current();

  LoadBarrierNode* lb2 = lb->has_dominating_barrier(phase, false, last_round);
  if (lb2 == NULL) {
    return false;
  }

  if (lb->in(LoadBarrierNode::Oop) != lb2->in(LoadBarrierNode::Oop)) {
    assert(lb->in(LoadBarrierNode::Address) == lb2->in(LoadBarrierNode::Address), "Invalid address");
    igvn.replace_input_of(lb, LoadBarrierNode::Similar, lb2->proj_out(LoadBarrierNode::Oop));
    C->set_major_progress();
    return false;
  }

  // That transformation may cause the Similar edge on dominated load barriers to be invalid
  lb->fix_similar_in_uses(&igvn);

  Node* val = lb->proj_out(LoadBarrierNode::Oop);
  assert(lb2->has_true_uses(), "Invalid uses");
  assert(lb2->in(LoadBarrierNode::Oop) == lb->in(LoadBarrierNode::Oop), "Invalid oop");
  phase->lazy_update(lb, lb->in(LoadBarrierNode::Control));
  phase->lazy_replace(lb->proj_out(LoadBarrierNode::Control), lb->in(LoadBarrierNode::Control));
  igvn.replace_node(val, lb2->proj_out(LoadBarrierNode::Oop));

  return true;
}

static Node* find_dominating_memory(PhaseIdealLoop* phase, Node* mem, Node* dom, int i) {
  assert(dom->is_Region() || i == -1, "");

  Node* m = mem;
  while(phase->is_dominator(dom, phase->has_ctrl(m) ? phase->get_ctrl(m) : m->in(0))) {
    if (m->is_Mem()) {
      assert(m->as_Mem()->adr_type() == TypeRawPtr::BOTTOM, "");
      m = m->in(MemNode::Memory);
    } else if (m->is_MergeMem()) {
      m = m->as_MergeMem()->memory_at(Compile::AliasIdxRaw);
    } else if (m->is_Phi()) {
      if (m->in(0) == dom && i != -1) {
        m = m->in(i);
        break;
      } else {
        m = m->in(LoopNode::EntryControl);
      }
    } else if (m->is_Proj()) {
      m = m->in(0);
    } else if (m->is_SafePoint() || m->is_MemBar()) {
      m = m->in(TypeFunc::Memory);
    } else {
#ifdef ASSERT
      m->dump();
#endif
      ShouldNotReachHere();
    }
  }

  return m;
}

static LoadBarrierNode* clone_load_barrier(PhaseIdealLoop* phase, LoadBarrierNode* lb, Node* ctl, Node* mem, Node* oop_in) {
  PhaseIterGVN &igvn = phase->igvn();
  Compile* C = Compile::current();
  Node* the_clone = lb->clone();
  the_clone->set_req(LoadBarrierNode::Control, ctl);
  the_clone->set_req(LoadBarrierNode::Memory, mem);
  if (oop_in != NULL) {
    the_clone->set_req(LoadBarrierNode::Oop, oop_in);
  }

  LoadBarrierNode* new_lb = the_clone->as_LoadBarrier();
  igvn.register_new_node_with_optimizer(new_lb);
  IdealLoopTree *loop = phase->get_loop(new_lb->in(0));
  phase->set_ctrl(new_lb, new_lb->in(0));
  phase->set_loop(new_lb, loop);
  phase->set_idom(new_lb, new_lb->in(0), phase->dom_depth(new_lb->in(0))+1);
  if (!loop->_child) {
    loop->_body.push(new_lb);
  }

  Node* proj_ctl = new ProjNode(new_lb, LoadBarrierNode::Control);
  igvn.register_new_node_with_optimizer(proj_ctl);
  phase->set_ctrl(proj_ctl, proj_ctl->in(0));
  phase->set_loop(proj_ctl, loop);
  phase->set_idom(proj_ctl, new_lb, phase->dom_depth(new_lb)+1);
  if (!loop->_child) {
    loop->_body.push(proj_ctl);
  }

  Node* proj_oop = new ProjNode(new_lb, LoadBarrierNode::Oop);
  phase->register_new_node(proj_oop, new_lb);

  if (!new_lb->in(LoadBarrierNode::Similar)->is_top()) {
    LoadBarrierNode* similar = new_lb->in(LoadBarrierNode::Similar)->in(0)->as_LoadBarrier();
    if (!phase->is_dominator(similar, ctl)) {
      igvn.replace_input_of(new_lb, LoadBarrierNode::Similar, C->top());
    }
  }

  return new_lb;
}

static void replace_barrier(PhaseIdealLoop* phase, LoadBarrierNode* lb, Node* new_val) {
  PhaseIterGVN &igvn = phase->igvn();
  Node* val = lb->proj_out(LoadBarrierNode::Oop);
  igvn.replace_node(val, new_val);
  phase->lazy_update(lb, lb->in(LoadBarrierNode::Control));
  phase->lazy_replace(lb->proj_out(LoadBarrierNode::Control), lb->in(LoadBarrierNode::Control));
}

static bool split_barrier_thru_phi(PhaseIdealLoop* phase, LoadBarrierNode* lb) {
  PhaseIterGVN &igvn = phase->igvn();
  Compile* C = Compile::current();

  if (lb->in(LoadBarrierNode::Oop)->is_Phi()) {
    Node* oop_phi = lb->in(LoadBarrierNode::Oop);

    if ((oop_phi->req() != 3) || (oop_phi->in(2) == oop_phi)) {
      // Ignore phis with only one input
      return false;
    }

    if (phase->is_dominator(phase->get_ctrl(lb->in(LoadBarrierNode::Address)),
                            oop_phi->in(0)) && phase->get_ctrl(lb->in(LoadBarrierNode::Address)) != oop_phi->in(0)) {
      // That transformation may cause the Similar edge on dominated load barriers to be invalid
      lb->fix_similar_in_uses(&igvn);

      RegionNode* region = oop_phi->in(0)->as_Region();

      int backedge = LoopNode::LoopBackControl;
      if (region->is_Loop() && region->in(backedge)->is_Proj() && region->in(backedge)->in(0)->is_If()) {
        Node* c = region->in(backedge)->in(0)->in(0);
        assert(c->unique_ctrl_out() == region->in(backedge)->in(0), "");
        Node* oop = lb->in(LoadBarrierNode::Oop)->in(backedge);
        Node* oop_c = phase->has_ctrl(oop) ? phase->get_ctrl(oop) : oop;
        if (!phase->is_dominator(oop_c, c)) {
          return false;
        }
      }

      // If the node on the backedge above the phi is the node itself - we have a self loop.
      // Don't clone - this will be folded later.
      if (oop_phi->in(LoopNode::LoopBackControl) == lb->proj_out(LoadBarrierNode::Oop)) {
        return false;
      }

      bool is_strip_mined = region->is_CountedLoop() && region->as_CountedLoop()->is_strip_mined();
      Node *phi = oop_phi->clone();

      for (uint i = 1; i < region->req(); i++) {
        Node* ctrl = region->in(i);
        if (ctrl != C->top()) {
          assert(!phase->is_dominator(ctrl, region) || region->is_Loop(), "");

          Node* mem = lb->in(LoadBarrierNode::Memory);
          Node* m = find_dominating_memory(phase, mem, region, i);

          if (region->is_Loop() && i == LoopNode::LoopBackControl && ctrl->is_Proj() && ctrl->in(0)->is_If()) {
            ctrl = ctrl->in(0)->in(0);
          } else if (region->is_Loop() && is_strip_mined) {
            // If this is a strip mined loop, control must move above OuterStripMinedLoop
            assert(i == LoopNode::EntryControl, "check");
            assert(ctrl->is_OuterStripMinedLoop(), "sanity");
            ctrl = ctrl->as_OuterStripMinedLoop()->in(LoopNode::EntryControl);
          }

          LoadBarrierNode* new_lb = clone_load_barrier(phase, lb, ctrl, m, lb->in(LoadBarrierNode::Oop)->in(i));
          Node* out_ctrl = new_lb->proj_out(LoadBarrierNode::Control);

          if (is_strip_mined && (i == LoopNode::EntryControl)) {
            assert(region->in(i)->is_OuterStripMinedLoop(), "");
            igvn.replace_input_of(region->in(i), i, out_ctrl);
            phase->set_idom(region->in(i), out_ctrl, phase->dom_depth(out_ctrl));
          } else if (ctrl == region->in(i)) {
            igvn.replace_input_of(region, i, out_ctrl);
            // Only update the idom if is the loop entry we are updating
            // - A loop backedge doesn't change the idom
            if (region->is_Loop() && i == LoopNode::EntryControl) {
              phase->set_idom(region, out_ctrl, phase->dom_depth(out_ctrl));
            }
          } else {
            Node* iff = region->in(i)->in(0);
            igvn.replace_input_of(iff, 0, out_ctrl);
            phase->set_idom(iff, out_ctrl, phase->dom_depth(out_ctrl)+1);
          }
          phi->set_req(i, new_lb->proj_out(LoadBarrierNode::Oop));
        }
      }
      phase->register_new_node(phi, region);
      replace_barrier(phase, lb, phi);

      if (region->is_Loop()) {
        // Load barrier moved to the back edge of the Loop may now
        // have a safepoint on the path to the barrier on the Similar
        // edge
        igvn.replace_input_of(phi->in(LoopNode::LoopBackControl)->in(0), LoadBarrierNode::Similar, C->top());
        Node* head = region->in(LoopNode::EntryControl);
        phase->set_idom(region, head, phase->dom_depth(head)+1);
        phase->recompute_dom_depth();
        if (head->is_CountedLoop() && head->as_CountedLoop()->is_main_loop()) {
          head->as_CountedLoop()->set_normal_loop();
        }
      }

      return true;
    }
  }

  return false;
}

static bool move_out_of_loop(PhaseIdealLoop* phase, LoadBarrierNode* lb) {
  PhaseIterGVN &igvn = phase->igvn();
  IdealLoopTree *lb_loop = phase->get_loop(lb->in(0));
  if (lb_loop != phase->ltree_root() && !lb_loop->_irreducible) {
    Node* oop_ctrl = phase->get_ctrl(lb->in(LoadBarrierNode::Oop));
    IdealLoopTree *oop_loop = phase->get_loop(oop_ctrl);
    IdealLoopTree* adr_loop = phase->get_loop(phase->get_ctrl(lb->in(LoadBarrierNode::Address)));
    if (!lb_loop->is_member(oop_loop) && !lb_loop->is_member(adr_loop)) {
      // That transformation may cause the Similar edge on dominated load barriers to be invalid
      lb->fix_similar_in_uses(&igvn);

      Node* head = lb_loop->_head;
      assert(head->is_Loop(), "");

      if (phase->is_dominator(head, oop_ctrl)) {
        assert(oop_ctrl->Opcode() == Op_CProj && oop_ctrl->in(0)->Opcode() == Op_NeverBranch, "");
        assert(lb_loop->is_member(phase->get_loop(oop_ctrl->in(0)->in(0))), "");
        return false;
      }

      if (head->is_CountedLoop()) {
        CountedLoopNode* cloop = head->as_CountedLoop();
        if (cloop->is_main_loop()) {
          cloop->set_normal_loop();
        }
        // When we are moving barrier out of a counted loop,
        // make sure we move it all the way out of the strip mined outer loop.
        if (cloop->is_strip_mined()) {
          head = cloop->outer_loop();
        }
      }

      Node* mem = lb->in(LoadBarrierNode::Memory);
      Node* m = find_dominating_memory(phase, mem, head, -1);

      LoadBarrierNode* new_lb = clone_load_barrier(phase, lb, head->in(LoopNode::EntryControl), m, NULL);

      assert(phase->idom(head) == head->in(LoopNode::EntryControl), "");
      Node* proj_ctl = new_lb->proj_out(LoadBarrierNode::Control);
      igvn.replace_input_of(head, LoopNode::EntryControl, proj_ctl);
      phase->set_idom(head, proj_ctl, phase->dom_depth(proj_ctl) + 1);

      replace_barrier(phase, lb, new_lb->proj_out(LoadBarrierNode::Oop));

      phase->recompute_dom_depth();

      return true;
    }
  }

  return false;
}

static bool common_barriers(PhaseIdealLoop* phase, LoadBarrierNode* lb) {
  PhaseIterGVN &igvn = phase->igvn();
  Node* in_val = lb->in(LoadBarrierNode::Oop);
  for (DUIterator_Fast imax, i = in_val->fast_outs(imax); i < imax; i++) {
    Node* u = in_val->fast_out(i);
    if (u != lb && u->is_LoadBarrier() && u->as_LoadBarrier()->has_true_uses()) {
      Node* this_ctrl = lb->in(LoadBarrierNode::Control);
      Node* other_ctrl = u->in(LoadBarrierNode::Control);

      Node* lca = phase->dom_lca(this_ctrl, other_ctrl);
      bool ok = true;

      Node* proj1 = NULL;
      Node* proj2 = NULL;

      while (this_ctrl != lca && ok) {
        if (this_ctrl->in(0) != NULL &&
            this_ctrl->in(0)->is_MultiBranch()) {
          if (this_ctrl->in(0)->in(0) == lca) {
            assert(proj1 == NULL, "");
            assert(this_ctrl->is_Proj(), "");
            proj1 = this_ctrl;
          } else if (!(this_ctrl->in(0)->is_If() && this_ctrl->as_Proj()->is_uncommon_trap_if_pattern(Deoptimization::Reason_none))) {
            ok = false;
          }
        }
        this_ctrl = phase->idom(this_ctrl);
      }
      while (other_ctrl != lca && ok) {
        if (other_ctrl->in(0) != NULL &&
            other_ctrl->in(0)->is_MultiBranch()) {
          if (other_ctrl->in(0)->in(0) == lca) {
            assert(other_ctrl->is_Proj(), "");
            assert(proj2 == NULL, "");
            proj2 = other_ctrl;
          } else if (!(other_ctrl->in(0)->is_If() && other_ctrl->as_Proj()->is_uncommon_trap_if_pattern(Deoptimization::Reason_none))) {
            ok = false;
          }
        }
        other_ctrl = phase->idom(other_ctrl);
      }
      assert(proj1 == NULL || proj2 == NULL || proj1->in(0) == proj2->in(0), "");
      if (ok && proj1 && proj2 && proj1 != proj2 && proj1->in(0)->is_If()) {
        // That transformation may cause the Similar edge on dominated load barriers to be invalid
        lb->fix_similar_in_uses(&igvn);
        u->as_LoadBarrier()->fix_similar_in_uses(&igvn);

        Node* split = lca->unique_ctrl_out();
        assert(split->in(0) == lca, "");

        Node* mem = lb->in(LoadBarrierNode::Memory);
        Node* m = find_dominating_memory(phase, mem, split, -1);
        LoadBarrierNode* new_lb = clone_load_barrier(phase, lb, lca, m, NULL);

        Node* proj_ctl = new_lb->proj_out(LoadBarrierNode::Control);
        igvn.replace_input_of(split, 0, new_lb->proj_out(LoadBarrierNode::Control));
        phase->set_idom(split, proj_ctl, phase->dom_depth(proj_ctl)+1);

        Node* proj_oop = new_lb->proj_out(LoadBarrierNode::Oop);
        replace_barrier(phase, lb, proj_oop);
        replace_barrier(phase, u->as_LoadBarrier(), proj_oop);

        phase->recompute_dom_depth();

        return true;
      }
    }
  }

  return false;
}

void ZBarrierSetC2::loop_optimize_gc_barrier(PhaseIdealLoop* phase, Node* node, bool last_round) {
  if (!Compile::current()->directive()->ZOptimizeLoadBarriersOption) {
    return;
  }

  if (!node->is_LoadBarrier()) {
    return;
  }

  if (!node->as_LoadBarrier()->has_true_uses()) {
    return;
  }

  if (replace_with_dominating_barrier(phase, node->as_LoadBarrier(), last_round)) {
    return;
  }

  if (split_barrier_thru_phi(phase, node->as_LoadBarrier())) {
    return;
  }

  if (move_out_of_loop(phase, node->as_LoadBarrier())) {
    return;
  }

  if (common_barriers(phase, node->as_LoadBarrier())) {
    return;
  }
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
    if (n != NULL && (n->is_LoadBarrierSlowReg() ||  n->is_LoadBarrierWeakSlowReg())) {
      assert(c == node, "projections from step 1 should only be seen before macro expansion");
      return phi->in(2);
    }
  }

  return c;
}

bool ZBarrierSetC2::array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, ArrayCopyPhase phase) const {
  return type == T_OBJECT || type == T_ARRAY;
}

bool ZBarrierSetC2::final_graph_reshaping(Compile* compile, Node* n, uint opcode) const {
  if (opcode != Op_LoadBarrierSlowReg &&
      opcode != Op_LoadBarrierWeakSlowReg) {
    return false;
  }

#ifdef ASSERT
  if (VerifyOptoOopOffsets) {
    MemNode* mem  = n->as_Mem();
    // Check to see if address types have grounded out somehow.
    const TypeInstPtr* tp = mem->in(MemNode::Address)->bottom_type()->isa_instptr();
    ciInstanceKlass* k = tp->klass()->as_instance_klass();
    bool oop_offset_is_sane = k->contains_field_offset(tp->offset());
    assert(!tp || oop_offset_is_sane, "");
  }
#endif

  return true;
}

bool ZBarrierSetC2::matcher_find_shared_visit(Matcher* matcher, Matcher::MStack& mstack, Node* n, uint opcode, bool& mem_op, int& mem_addr_idx) const {
  if (opcode == Op_CallLeaf &&
      (n->as_Call()->entry_point() == ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr() ||
       n->as_Call()->entry_point() == ZBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded_addr())) {
    mem_op = true;
    mem_addr_idx = TypeFunc::Parms + 1;
    return true;
  }

  return false;
}

// == Verification ==

#ifdef ASSERT

static bool look_for_barrier(Node* n, bool post_parse, VectorSet& visited) {
  if (visited.test_set(n->_idx)) {
    return true;
  }

  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* u = n->fast_out(i);
    if (u->is_LoadBarrier()) {
    } else if ((u->is_Phi() || u->is_CMove()) && !post_parse) {
      if (!look_for_barrier(u, post_parse, visited)) {
        return false;
      }
    } else if (u->Opcode() == Op_EncodeP || u->Opcode() == Op_DecodeN) {
      if (!look_for_barrier(u, post_parse, visited)) {
        return false;
      }
    } else if (u->Opcode() != Op_SCMemProj) {
      tty->print("bad use"); u->dump();
      return false;
    }
  }

  return true;
}

void ZBarrierSetC2::verify_gc_barriers(Compile* compile, CompilePhase phase) const {
  if (phase == BarrierSetC2::BeforeCodeGen) return;
  bool post_parse = phase == BarrierSetC2::BeforeOptimize;
  verify_gc_barriers(post_parse);
}

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

    assert(post_parse || n->as_LoadBarrier()->has_true_uses(),
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
      bool ok = true;
      bool dom_found = false;
      for (uint next = 0; next < wq.size(); ++next) {
        Node *n = wq.at(next);
        assert(n->is_CFG(), "");
        assert(!n->is_SafePoint(), "");

        if (n == other) {
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
    }

    if (ZVerifyLoadBarriers) {
      if ((n->is_Load() || n->is_LoadStore()) && n->bottom_type()->make_oopptr() != NULL) {
        visited.Clear();
        bool found = look_for_barrier(n, post_parse, visited);
        if (!found) {
          n->dump(1);
          n->dump(-3);
          stringStream ss;
          C->method()->print_short_name(&ss);
          tty->print_cr("-%s-", ss.as_string());
          assert(found, "");
        }
      }
    }
  }
}

#endif

bool ZBarrierSetC2::escape_add_to_con_graph(ConnectionGraph* conn_graph, PhaseGVN* gvn, Unique_Node_List* delayed_worklist, Node* n, uint opcode) const {
  switch (opcode) {
    case Op_LoadBarrierSlowReg:
    case Op_LoadBarrierWeakSlowReg:
      conn_graph->add_objload_to_connection_graph(n, delayed_worklist);
      return true;

    case Op_Proj:
      if (n->as_Proj()->_con != LoadBarrierNode::Oop || !n->in(0)->is_LoadBarrier()) {
        return false;
      }
      conn_graph->add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(0)->in(LoadBarrierNode::Oop), delayed_worklist);
      return true;
  }

  return false;
}

bool ZBarrierSetC2::escape_add_final_edges(ConnectionGraph* conn_graph, PhaseGVN* gvn, Node* n, uint opcode) const {
  switch (opcode) {
    case Op_LoadBarrierSlowReg:
    case Op_LoadBarrierWeakSlowReg:
      if (gvn->type(n)->make_ptr() == NULL) {
        return false;
      }
      conn_graph->add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(MemNode::Address), NULL);
      return true;

    case Op_Proj:
      if (n->as_Proj()->_con != LoadBarrierNode::Oop || !n->in(0)->is_LoadBarrier()) {
        return false;
      }
      conn_graph->add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(0)->in(LoadBarrierNode::Oop), NULL);
      return true;
  }

  return false;
}
