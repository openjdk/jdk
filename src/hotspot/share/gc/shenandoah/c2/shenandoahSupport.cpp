/*
 * Copyright (c) 2015, 2018, Red Hat, Inc. All rights reserved.
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

#include "precompiled.hpp"

#include "gc/shenandoah/c2/shenandoahSupport.hpp"
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahBrooksPointer.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/block.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/movenode.hpp"
#include "opto/phaseX.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"

Node* ShenandoahBarrierNode::skip_through_barrier(Node* n) {
  if (n == NULL) {
    return NULL;
  }
  if (n->Opcode() == Op_ShenandoahEnqueueBarrier) {
    n = n->in(1);
  }

  if (n->is_ShenandoahBarrier()) {
    return n->in(ValueIn);
  } else if (n->is_Phi() &&
             n->req() == 3 &&
             n->in(1) != NULL &&
             n->in(1)->is_ShenandoahBarrier() &&
             n->in(2) != NULL &&
             n->in(2)->bottom_type() == TypePtr::NULL_PTR &&
             n->in(0) != NULL &&
             n->in(0)->in(1) != NULL &&
             n->in(0)->in(1)->is_IfProj() &&
             n->in(0)->in(2) != NULL &&
             n->in(0)->in(2)->is_IfProj() &&
             n->in(0)->in(1)->in(0) != NULL &&
             n->in(0)->in(1)->in(0) == n->in(0)->in(2)->in(0) &&
             n->in(1)->in(ValueIn)->Opcode() == Op_CastPP) {
    Node* iff = n->in(0)->in(1)->in(0);
    Node* res = n->in(1)->in(ValueIn)->in(1);
    if (iff->is_If() &&
        iff->in(1) != NULL &&
        iff->in(1)->is_Bool() &&
        iff->in(1)->as_Bool()->_test._test == BoolTest::ne &&
        iff->in(1)->in(1) != NULL &&
        iff->in(1)->in(1)->Opcode() == Op_CmpP &&
        iff->in(1)->in(1)->in(1) != NULL &&
        iff->in(1)->in(1)->in(1) == res &&
        iff->in(1)->in(1)->in(2) != NULL &&
        iff->in(1)->in(1)->in(2)->bottom_type() == TypePtr::NULL_PTR) {
      return res;
    }
  }
  return n;
}

bool ShenandoahBarrierNode::needs_barrier(PhaseGVN* phase, ShenandoahBarrierNode* orig, Node* n, Node* rb_mem, bool allow_fromspace) {
  Unique_Node_List visited;
  return needs_barrier_impl(phase, orig, n, rb_mem, allow_fromspace, visited);
}

bool ShenandoahBarrierNode::needs_barrier_impl(PhaseGVN* phase, ShenandoahBarrierNode* orig, Node* n, Node* rb_mem, bool allow_fromspace, Unique_Node_List &visited) {
  if (visited.member(n)) {
    return false; // Been there.
  }
  visited.push(n);

  if (n->is_Allocate()) {
    return false;
  }

  if (n->is_Call()) {
    return true;
  }

  const Type* type = phase->type(n);
  if (type == Type::TOP) {
    return false;
  }
  if (type->make_ptr()->higher_equal(TypePtr::NULL_PTR)) {
    return false;
  }
  if (type->make_oopptr() && type->make_oopptr()->const_oop() != NULL) {
    return false;
  }

  if (ShenandoahOptimizeStableFinals) {
    const TypeAryPtr* ary = type->isa_aryptr();
    if (ary && ary->is_stable() && allow_fromspace) {
      return false;
    }
  }

  if (n->is_CheckCastPP() || n->is_ConstraintCast() || n->Opcode() == Op_ShenandoahEnqueueBarrier) {
    return needs_barrier_impl(phase, orig, n->in(1), rb_mem, allow_fromspace, visited);
  }
  if (n->is_Parm()) {
    return true;
  }
  if (n->is_Proj()) {
    return needs_barrier_impl(phase, orig, n->in(0), rb_mem, allow_fromspace, visited);
  }

  if (n->Opcode() == Op_ShenandoahWBMemProj) {
    return needs_barrier_impl(phase, orig, n->in(ShenandoahWBMemProjNode::WriteBarrier), rb_mem, allow_fromspace, visited);
  }
  if (n->is_Phi()) {
    bool need_barrier = false;
    for (uint i = 1; i < n->req() && ! need_barrier; i++) {
      Node* input = n->in(i);
      if (input == NULL) {
        need_barrier = true; // Phi not complete yet?
      } else if (needs_barrier_impl(phase, orig, input, rb_mem, allow_fromspace, visited)) {
        need_barrier = true;
      }
    }
    return need_barrier;
  }
  if (n->is_CMove()) {
    return needs_barrier_impl(phase, orig, n->in(CMoveNode::IfFalse), rb_mem, allow_fromspace, visited) ||
           needs_barrier_impl(phase, orig, n->in(CMoveNode::IfTrue ), rb_mem, allow_fromspace, visited);
  }
  if (n->Opcode() == Op_CreateEx) {
    return true;
  }
  if (n->Opcode() == Op_ShenandoahWriteBarrier) {
    return false;
  }
  if (n->Opcode() == Op_ShenandoahReadBarrier) {
    if (rb_mem == n->in(Memory)) {
      return false;
    } else {
      return true;
    }
  }

  if (n->Opcode() == Op_LoadP ||
      n->Opcode() == Op_LoadN ||
      n->Opcode() == Op_GetAndSetP ||
      n->Opcode() == Op_CompareAndExchangeP ||
      n->Opcode() == Op_ShenandoahCompareAndExchangeP ||
      n->Opcode() == Op_GetAndSetN ||
      n->Opcode() == Op_CompareAndExchangeN ||
      n->Opcode() == Op_ShenandoahCompareAndExchangeN) {
    return true;
  }
  if (n->Opcode() == Op_DecodeN ||
      n->Opcode() == Op_EncodeP) {
    return needs_barrier_impl(phase, orig, n->in(1), rb_mem, allow_fromspace, visited);
  }

#ifdef ASSERT
  tty->print("need barrier on?: "); n->dump();
  ShouldNotReachHere();
#endif
  return true;
}

bool ShenandoahReadBarrierNode::dominates_memory_rb_impl(PhaseGVN* phase,
                                                         Node* b1,
                                                         Node* b2,
                                                         Node* current,
                                                         bool linear) {
  ResourceMark rm;
  VectorSet visited(Thread::current()->resource_area());
  Node_Stack phis(0);

  for(int i = 0; i < 10; i++) {
    if (current == NULL) {
      return false;
    } else if (visited.test_set(current->_idx) || current->is_top() || current == b1) {
      current = NULL;
      while (phis.is_nonempty() && current == NULL) {
        uint idx = phis.index();
        Node* phi = phis.node();
        if (idx >= phi->req()) {
          phis.pop();
        } else {
          current = phi->in(idx);
          phis.set_index(idx+1);
        }
      }
      if (current == NULL) {
        return true;
      }
    } else if (current == phase->C->immutable_memory()) {
      return false;
    } else if (current->isa_Phi()) {
      if (!linear) {
        return false;
      }
      phis.push(current, 2);
      current = current->in(1);
    } else if (current->Opcode() == Op_ShenandoahWriteBarrier) {
      const Type* in_type = current->bottom_type();
      const Type* this_type = b2->bottom_type();
      if (is_independent(in_type, this_type)) {
        current = current->in(Memory);
      } else {
        return false;
      }
    } else if (current->Opcode() == Op_ShenandoahWBMemProj) {
      current = current->in(ShenandoahWBMemProjNode::WriteBarrier);
    } else if (current->is_Proj()) {
      current = current->in(0);
    } else if (current->is_Call()) {
      return false; // TODO: Maybe improve by looking at the call's memory effects?
    } else if (current->is_MemBar()) {
      return false; // TODO: Do we need to stop at *any* membar?
    } else if (current->is_MergeMem()) {
      const TypePtr* adr_type = brooks_pointer_type(phase->type(b2));
      uint alias_idx = phase->C->get_alias_index(adr_type);
      current = current->as_MergeMem()->memory_at(alias_idx);
    } else {
#ifdef ASSERT
      current->dump();
#endif
      ShouldNotReachHere();
      return false;
    }
  }
  return false;
}

bool ShenandoahReadBarrierNode::is_independent(Node* mem) {
  if (mem->is_Phi() || mem->is_Proj() || mem->is_MergeMem()) {
    return true;
  } else if (mem->Opcode() == Op_ShenandoahWBMemProj) {
    return true;
  } else if (mem->Opcode() == Op_ShenandoahWriteBarrier) {
    const Type* mem_type = mem->bottom_type();
    const Type* this_type = bottom_type();
    if (is_independent(mem_type, this_type)) {
      return true;
    } else {
      return false;
    }
  } else if (mem->is_Call() || mem->is_MemBar()) {
    return false;
  }
#ifdef ASSERT
  mem->dump();
#endif
  ShouldNotReachHere();
  return true;
}

bool ShenandoahReadBarrierNode::dominates_memory_rb(PhaseGVN* phase, Node* b1, Node* b2, bool linear) {
  return dominates_memory_rb_impl(phase, b1->in(Memory), b2, b2->in(Memory), linear);
}

bool ShenandoahReadBarrierNode::is_independent(const Type* in_type, const Type* this_type) {
  assert(in_type->isa_oopptr(), "expect oop ptr");
  assert(this_type->isa_oopptr(), "expect oop ptr");

  ciKlass* in_kls = in_type->is_oopptr()->klass();
  ciKlass* this_kls = this_type->is_oopptr()->klass();
  if (in_kls != NULL && this_kls != NULL &&
      in_kls->is_loaded() && this_kls->is_loaded() &&
      (!in_kls->is_subclass_of(this_kls)) &&
      (!this_kls->is_subclass_of(in_kls))) {
    return true;
  }
  return false;
}

Node* ShenandoahReadBarrierNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (! can_reshape) {
    return NULL;
  }

  if (in(Memory) == phase->C->immutable_memory()) return NULL;

  // If memory input is a MergeMem, take the appropriate slice out of it.
  Node* mem_in = in(Memory);
  if (mem_in->isa_MergeMem()) {
    const TypePtr* adr_type = brooks_pointer_type(bottom_type());
    uint alias_idx = phase->C->get_alias_index(adr_type);
    mem_in = mem_in->as_MergeMem()->memory_at(alias_idx);
    set_req(Memory, mem_in);
    return this;
  }

  Node* input = in(Memory);
  if (input->Opcode() == Op_ShenandoahWBMemProj) {
    ResourceMark rm;
    VectorSet seen(Thread::current()->resource_area());
    Node* n = in(Memory);
    while (n->Opcode() == Op_ShenandoahWBMemProj &&
           n->in(ShenandoahWBMemProjNode::WriteBarrier) != NULL &&
           n->in(ShenandoahWBMemProjNode::WriteBarrier)->Opcode() == Op_ShenandoahWriteBarrier &&
           n->in(ShenandoahWBMemProjNode::WriteBarrier)->in(Memory) != NULL) {
      if (seen.test_set(n->_idx)) {
        return NULL; // loop
      }
      n = n->in(ShenandoahWBMemProjNode::WriteBarrier)->in(Memory);
    }

    Node* wb = input->in(ShenandoahWBMemProjNode::WriteBarrier);
    const Type* in_type = phase->type(wb);
    // is_top() test not sufficient here: we can come here after CCP
    // in a dead branch of the graph that has not yet been removed.
    if (in_type == Type::TOP) return NULL; // Dead path.
    assert(wb->Opcode() == Op_ShenandoahWriteBarrier, "expect write barrier");
    if (is_independent(in_type, _type)) {
      phase->igvn_rehash_node_delayed(wb);
      set_req(Memory, wb->in(Memory));
      if (can_reshape && input->outcnt() == 0) {
        phase->is_IterGVN()->_worklist.push(input);
      }
      return this;
    }
  }
  return NULL;
}

ShenandoahWriteBarrierNode::ShenandoahWriteBarrierNode(Compile* C, Node* ctrl, Node* mem, Node* obj)
  : ShenandoahBarrierNode(ctrl, mem, obj, false) {
  assert(UseShenandoahGC && ShenandoahWriteBarrier, "should be enabled");
  ShenandoahBarrierSetC2::bsc2()->state()->add_shenandoah_barrier(this);
}

Node* ShenandoahWriteBarrierNode::Identity(PhaseGVN* phase) {
  assert(in(0) != NULL, "should have control");
  PhaseIterGVN* igvn = phase->is_IterGVN();
  Node* mem_in = in(Memory);
  Node* mem_proj = NULL;

  if (igvn != NULL) {
    mem_proj = find_out_with(Op_ShenandoahWBMemProj);
    if (mem_in == mem_proj) {
      return this;
    }
  }

  Node* replacement = Identity_impl(phase);
  if (igvn != NULL) {
    if (replacement != NULL && replacement != this && mem_proj != NULL) {
      igvn->replace_node(mem_proj, mem_in);
    }
  }
  return replacement;
}

Node* ShenandoahWriteBarrierNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  assert(in(0) != NULL, "should have control");
  if (!can_reshape) {
    return NULL;
  }

  Node* mem_in = in(Memory);

  if (mem_in->isa_MergeMem()) {
    const TypePtr* adr_type = brooks_pointer_type(bottom_type());
    uint alias_idx = phase->C->get_alias_index(adr_type);
    mem_in = mem_in->as_MergeMem()->memory_at(alias_idx);
    set_req(Memory, mem_in);
    return this;
  }

  Node* val = in(ValueIn);
  if (val->is_ShenandoahBarrier()) {
    set_req(ValueIn, val->in(ValueIn));
    return this;
  }

  return NULL;
}

bool ShenandoahWriteBarrierNode::expand(Compile* C, PhaseIterGVN& igvn) {
  if (UseShenandoahGC) {
    if (ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barriers_count() > 0 || (!ShenandoahWriteBarrier && ShenandoahStoreValEnqueueBarrier)) {
      bool attempt_more_loopopts = ShenandoahLoopOptsAfterExpansion;
      C->clear_major_progress();
      PhaseIdealLoop ideal_loop(igvn, LoopOptsShenandoahExpand);
      if (C->failing()) return false;
      PhaseIdealLoop::verify(igvn);
      DEBUG_ONLY(ShenandoahBarrierNode::verify_raw_mem(C->root());)
      if (attempt_more_loopopts) {
        C->set_major_progress();
        if (!C->optimize_loops(igvn, LoopOptsShenandoahPostExpand)) {
          return false;
        }
        C->clear_major_progress();
      }
    }
  }
  return true;
}

bool ShenandoahWriteBarrierNode::is_heap_state_test(Node* iff, int mask) {
  if (!UseShenandoahGC) {
    return false;
  }
  assert(iff->is_If(), "bad input");
  if (iff->Opcode() != Op_If) {
    return false;
  }
  Node* bol = iff->in(1);
  if (!bol->is_Bool() || bol->as_Bool()->_test._test != BoolTest::ne) {
    return false;
  }
  Node* cmp = bol->in(1);
  if (cmp->Opcode() != Op_CmpI) {
    return false;
  }
  Node* in1 = cmp->in(1);
  Node* in2 = cmp->in(2);
  if (in2->find_int_con(-1) != 0) {
    return false;
  }
  if (in1->Opcode() != Op_AndI) {
    return false;
  }
  in2 = in1->in(2);
  if (in2->find_int_con(-1) != mask) {
    return false;
  }
  in1 = in1->in(1);

  return is_gc_state_load(in1);
}

bool ShenandoahWriteBarrierNode::is_heap_stable_test(Node* iff) {
  return is_heap_state_test(iff, ShenandoahHeap::HAS_FORWARDED);
}

bool ShenandoahWriteBarrierNode::is_gc_state_load(Node *n) {
  if (!UseShenandoahGC) {
    return false;
  }
  if (n->Opcode() != Op_LoadB && n->Opcode() != Op_LoadUB) {
    return false;
  }
  Node* addp = n->in(MemNode::Address);
  if (!addp->is_AddP()) {
    return false;
  }
  Node* base = addp->in(AddPNode::Address);
  Node* off = addp->in(AddPNode::Offset);
  if (base->Opcode() != Op_ThreadLocal) {
    return false;
  }
  if (off->find_intptr_t_con(-1) != in_bytes(ShenandoahThreadLocalData::gc_state_offset())) {
    return false;
  }
  return true;
}

bool ShenandoahWriteBarrierNode::has_safepoint_between(Node* start, Node* stop, PhaseIdealLoop *phase) {
  assert(phase->is_dominator(stop, start), "bad inputs");
  ResourceMark rm;
  Unique_Node_List wq;
  wq.push(start);
  for (uint next = 0; next < wq.size(); next++) {
    Node *m = wq.at(next);
    if (m == stop) {
      continue;
    }
    if (m->is_SafePoint() && !m->is_CallLeaf()) {
      return true;
    }
    if (m->is_Region()) {
      for (uint i = 1; i < m->req(); i++) {
        wq.push(m->in(i));
      }
    } else {
      wq.push(m->in(0));
    }
  }
  return false;
}

bool ShenandoahWriteBarrierNode::try_common_gc_state_load(Node *n, PhaseIdealLoop *phase) {
  assert(is_gc_state_load(n), "inconsistent");
  Node* addp = n->in(MemNode::Address);
  Node* dominator = NULL;
  for (DUIterator_Fast imax, i = addp->fast_outs(imax); i < imax; i++) {
    Node* u = addp->fast_out(i);
    assert(is_gc_state_load(u), "inconsistent");
    if (u != n && phase->is_dominator(u->in(0), n->in(0))) {
      if (dominator == NULL) {
        dominator = u;
      } else {
        if (phase->dom_depth(u->in(0)) < phase->dom_depth(dominator->in(0))) {
          dominator = u;
        }
      }
    }
  }
  if (dominator == NULL || has_safepoint_between(n->in(0), dominator->in(0), phase)) {
    return false;
  }
  phase->igvn().replace_node(n, dominator);

  return true;
}

bool ShenandoahBarrierNode::dominates_memory_impl(PhaseGVN* phase,
                                                  Node* b1,
                                                  Node* b2,
                                                  Node* current,
                                                  bool linear) {
  ResourceMark rm;
  VectorSet visited(Thread::current()->resource_area());
  Node_Stack phis(0);

  for(int i = 0; i < 10; i++) {
    if (current == NULL) {
      return false;
    } else if (visited.test_set(current->_idx) || current->is_top() || current == b1) {
      current = NULL;
      while (phis.is_nonempty() && current == NULL) {
        uint idx = phis.index();
        Node* phi = phis.node();
        if (idx >= phi->req()) {
          phis.pop();
        } else {
          current = phi->in(idx);
          phis.set_index(idx+1);
        }
      }
      if (current == NULL) {
        return true;
      }
    } else if (current == b2) {
      return false;
    } else if (current == phase->C->immutable_memory()) {
      return false;
    } else if (current->isa_Phi()) {
      if (!linear) {
        return false;
      }
      phis.push(current, 2);
      current = current->in(1);
    } else if (current->Opcode() == Op_ShenandoahWriteBarrier) {
      current = current->in(Memory);
    } else if (current->Opcode() == Op_ShenandoahWBMemProj) {
      current = current->in(ShenandoahWBMemProjNode::WriteBarrier);
    } else if (current->is_Proj()) {
      current = current->in(0);
    } else if (current->is_Call()) {
      current = current->in(TypeFunc::Memory);
    } else if (current->is_MemBar()) {
      current = current->in(TypeFunc::Memory);
    } else if (current->is_MergeMem()) {
      const TypePtr* adr_type = brooks_pointer_type(phase->type(b2));
      uint alias_idx = phase->C->get_alias_index(adr_type);
      current = current->as_MergeMem()->memory_at(alias_idx);
    } else {
#ifdef ASSERT
      current->dump();
#endif
      ShouldNotReachHere();
      return false;
    }
  }
  return false;
}

/**
 * Determines if b1 dominates b2 through memory inputs. It returns true if:
 * - b1 can be reached by following each branch in b2's memory input (through phis, etc)
 * - or we get back to b2 (i.e. through a loop) without seeing b1
 * In all other cases, (in particular, if we reach immutable_memory without having seen b1)
 * we return false.
 */
bool ShenandoahBarrierNode::dominates_memory(PhaseGVN* phase, Node* b1, Node* b2, bool linear) {
  return dominates_memory_impl(phase, b1, b2, b2->in(Memory), linear);
}

Node* ShenandoahBarrierNode::Identity_impl(PhaseGVN* phase) {
  Node* n = in(ValueIn);

  Node* rb_mem = Opcode() == Op_ShenandoahReadBarrier ? in(Memory) : NULL;
  if (! needs_barrier(phase, this, n, rb_mem, _allow_fromspace)) {
    return n;
  }

  // Try to find a write barrier sibling with identical inputs that we can fold into.
  for (DUIterator i = n->outs(); n->has_out(i); i++) {
    Node* sibling = n->out(i);
    if (sibling == this) {
      continue;
    }
    if (sibling->Opcode() != Op_ShenandoahWriteBarrier) {
      continue;
    }

    assert(sibling->in(ValueIn) == in(ValueIn), "sanity");
    assert(sibling->Opcode() == Op_ShenandoahWriteBarrier, "sanity");

    if (dominates_memory(phase, sibling, this, phase->is_IterGVN() == NULL)) {
      return sibling;
    }
  }
  return this;
}

#ifndef PRODUCT
void ShenandoahBarrierNode::dump_spec(outputStream *st) const {
  const TypePtr* adr = adr_type();
  if (adr == NULL) {
    return;
  }
  st->print(" @");
  adr->dump_on(st);
  st->print(" (");
  Compile::current()->alias_type(adr)->adr_type()->dump_on(st);
  st->print(") ");
}
#endif

Node* ShenandoahReadBarrierNode::Identity(PhaseGVN* phase) {
  Node* id = Identity_impl(phase);

  if (id == this && phase->is_IterGVN()) {
    Node* n = in(ValueIn);
    // No success in super call. Try to combine identical read barriers.
    for (DUIterator i = n->outs(); n->has_out(i); i++) {
      Node* sibling = n->out(i);
      if (sibling == this || sibling->Opcode() != Op_ShenandoahReadBarrier) {
        continue;
      }
      assert(sibling->in(ValueIn)  == in(ValueIn), "sanity");
      if (phase->is_IterGVN()->hash_find(sibling) &&
          sibling->bottom_type() == bottom_type() &&
          sibling->in(Control) == in(Control) &&
          dominates_memory_rb(phase, sibling, this, phase->is_IterGVN() == NULL)) {
        return sibling;
      }
    }
  }
  return id;
}

const Type* ShenandoahBarrierNode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type(in(Memory));
  if (t1 == Type::TOP) return Type::TOP;
  const Type *t2 = phase->type(in(ValueIn));
  if( t2 == Type::TOP ) return Type::TOP;

  if (t2 == TypePtr::NULL_PTR) {
    return _type;
  }

  const Type* type = t2->is_oopptr()->cast_to_nonconst();
  return type;
}

uint ShenandoahBarrierNode::hash() const {
  return TypeNode::hash() + _allow_fromspace;
}

uint ShenandoahBarrierNode::cmp(const Node& n) const {
  return _allow_fromspace == ((ShenandoahBarrierNode&) n)._allow_fromspace
    && TypeNode::cmp(n);
}

uint ShenandoahBarrierNode::size_of() const {
  return sizeof(*this);
}

Node* ShenandoahWBMemProjNode::Identity(PhaseGVN* phase) {
  Node* wb = in(WriteBarrier);
  if (wb->is_top()) return phase->C->top(); // Dead path.

  assert(wb->Opcode() == Op_ShenandoahWriteBarrier, "expect write barrier");
  PhaseIterGVN* igvn = phase->is_IterGVN();
  // We can't do the below unless the graph is fully constructed.
  if (igvn == NULL) {
    return this;
  }

  // If the mem projection has no barrier users, it's not needed anymore.
  if (wb->outcnt() == 1) {
    return wb->in(ShenandoahBarrierNode::Memory);
  }

  return this;
}

#ifdef ASSERT
bool ShenandoahBarrierNode::verify_helper(Node* in, Node_Stack& phis, VectorSet& visited, verify_type t, bool trace, Unique_Node_List& barriers_used) {
  assert(phis.size() == 0, "");

  while (true) {
    if (in->bottom_type() == TypePtr::NULL_PTR) {
      if (trace) {tty->print_cr("NULL");}
    } else if (!in->bottom_type()->make_ptr()->make_oopptr()) {
      if (trace) {tty->print_cr("Non oop");}
    } else if (t == ShenandoahLoad && ShenandoahOptimizeStableFinals &&
               in->bottom_type()->make_ptr()->isa_aryptr() &&
               in->bottom_type()->make_ptr()->is_aryptr()->is_stable()) {
      if (trace) {tty->print_cr("Stable array load");}
    } else {
      if (in->is_ConstraintCast()) {
        in = in->in(1);
        continue;
      } else if (in->is_AddP()) {
        assert(!in->in(AddPNode::Address)->is_top(), "no raw memory access");
        in = in->in(AddPNode::Address);
        continue;
      } else if (in->is_Con()) {
        if (trace) {tty->print("Found constant"); in->dump();}
      } else if (in->is_ShenandoahBarrier()) {
        if (t == ShenandoahOopStore) {
          if (in->Opcode() != Op_ShenandoahWriteBarrier) {
            return false;
          }
          uint i = 0;
          for (; i < phis.size(); i++) {
            Node* n = phis.node_at(i);
            if (n->Opcode() == Op_ShenandoahEnqueueBarrier) {
              break;
            }
          }
          if (i == phis.size()) {
            return false;
          }
        } else if (t == ShenandoahStore && in->Opcode() != Op_ShenandoahWriteBarrier) {
          return false;
        }
        barriers_used.push(in);
        if (trace) {tty->print("Found barrier"); in->dump();}
      } else if (in->Opcode() == Op_ShenandoahEnqueueBarrier) {
        if (t != ShenandoahOopStore) {
          in = in->in(1);
          continue;
        }
        if (trace) {tty->print("Found enqueue barrier"); in->dump();}
        phis.push(in, in->req());
        in = in->in(1);
        continue;
      } else if (in->is_Proj() && in->in(0)->is_Allocate()) {
        if (trace) {tty->print("Found alloc"); in->in(0)->dump();}
      } else if (in->is_Phi()) {
        if (!visited.test_set(in->_idx)) {
          if (trace) {tty->print("Pushed phi:"); in->dump();}
          phis.push(in, 2);
          in = in->in(1);
          continue;
        }
        if (trace) {tty->print("Already seen phi:"); in->dump();}
      } else if (in->Opcode() == Op_CMoveP || in->Opcode() == Op_CMoveN) {
        if (!visited.test_set(in->_idx)) {
          if (trace) {tty->print("Pushed cmovep:"); in->dump();}
          phis.push(in, CMoveNode::IfTrue);
          in = in->in(CMoveNode::IfFalse);
          continue;
        }
        if (trace) {tty->print("Already seen cmovep:"); in->dump();}
      } else if (in->Opcode() == Op_EncodeP || in->Opcode() == Op_DecodeN) {
        in = in->in(1);
        continue;
      } else {
        return false;
      }
    }
    bool cont = false;
    while (phis.is_nonempty()) {
      uint idx = phis.index();
      Node* phi = phis.node();
      if (idx >= phi->req()) {
        if (trace) {tty->print("Popped phi:"); phi->dump();}
        phis.pop();
        continue;
      }
      if (trace) {tty->print("Next entry(%d) for phi:", idx); phi->dump();}
      in = phi->in(idx);
      phis.set_index(idx+1);
      cont = true;
      break;
    }
    if (!cont) {
      break;
    }
  }
  return true;
}

void ShenandoahBarrierNode::report_verify_failure(const char *msg, Node *n1, Node *n2) {
  if (n1 != NULL) {
    n1->dump(+10);
  }
  if (n2 != NULL) {
    n2->dump(+10);
  }
  fatal("%s", msg);
}

void ShenandoahBarrierNode::verify(RootNode* root) {
  ResourceMark rm;
  Unique_Node_List wq;
  GrowableArray<Node*> barriers;
  Unique_Node_List barriers_used;
  Node_Stack phis(0);
  VectorSet visited(Thread::current()->resource_area());
  const bool trace = false;
  const bool verify_no_useless_barrier = false;

  wq.push(root);
  for (uint next = 0; next < wq.size(); next++) {
    Node *n = wq.at(next);
    if (n->is_Load()) {
      const bool trace = false;
      if (trace) {tty->print("Verifying"); n->dump();}
      if (n->Opcode() == Op_LoadRange || n->Opcode() == Op_LoadKlass || n->Opcode() == Op_LoadNKlass) {
        if (trace) {tty->print_cr("Load range/klass");}
      } else {
        const TypePtr* adr_type = n->as_Load()->adr_type();

        if (adr_type->isa_oopptr() && adr_type->is_oopptr()->offset() == oopDesc::mark_offset_in_bytes()) {
          if (trace) {tty->print_cr("Mark load");}
        } else if (adr_type->isa_instptr() &&
                   adr_type->is_instptr()->klass()->is_subtype_of(Compile::current()->env()->Reference_klass()) &&
                   adr_type->is_instptr()->offset() == java_lang_ref_Reference::referent_offset) {
          if (trace) {tty->print_cr("Reference.get()");}
        } else {
          bool verify = true;
          if (adr_type->isa_instptr()) {
            const TypeInstPtr* tinst = adr_type->is_instptr();
            ciKlass* k = tinst->klass();
            assert(k->is_instance_klass(), "");
            ciInstanceKlass* ik = (ciInstanceKlass*)k;
            int offset = adr_type->offset();

            if ((ik->debug_final_field_at(offset) && ShenandoahOptimizeInstanceFinals) ||
                (ik->debug_stable_field_at(offset) && ShenandoahOptimizeStableFinals)) {
              if (trace) {tty->print_cr("Final/stable");}
              verify = false;
            } else if (k == ciEnv::current()->Class_klass() &&
                       tinst->const_oop() != NULL &&
                       tinst->offset() >= (ik->size_helper() * wordSize)) {
              ciInstanceKlass* k = tinst->const_oop()->as_instance()->java_lang_Class_klass()->as_instance_klass();
              ciField* field = k->get_field_by_offset(tinst->offset(), true);
              if ((ShenandoahOptimizeStaticFinals && field->is_final()) ||
                  (ShenandoahOptimizeStableFinals && field->is_stable())) {
                verify = false;
              }
            }
          }

          if (verify && !ShenandoahBarrierNode::verify_helper(n->in(MemNode::Address), phis, visited, ShenandoahLoad, trace, barriers_used)) {
            report_verify_failure("Shenandoah verification: Load should have barriers", n);
          }
        }
      }
    } else if (n->is_Store()) {
      const bool trace = false;

      if (trace) {tty->print("Verifying"); n->dump();}
      if (n->in(MemNode::ValueIn)->bottom_type()->make_oopptr()) {
        Node* adr = n->in(MemNode::Address);
        bool verify = true;

        if (adr->is_AddP() && adr->in(AddPNode::Base)->is_top()) {
          adr = adr->in(AddPNode::Address);
          if (adr->is_AddP()) {
            assert(adr->in(AddPNode::Base)->is_top(), "");
            adr = adr->in(AddPNode::Address);
            if (adr->Opcode() == Op_LoadP &&
                adr->in(MemNode::Address)->in(AddPNode::Base)->is_top() &&
                adr->in(MemNode::Address)->in(AddPNode::Address)->Opcode() == Op_ThreadLocal &&
                adr->in(MemNode::Address)->in(AddPNode::Offset)->find_intptr_t_con(-1) == in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset())) {
              if (trace) {tty->print_cr("SATB prebarrier");}
              verify = false;
            }
          }
        }

        if (verify && !ShenandoahBarrierNode::verify_helper(n->in(MemNode::ValueIn), phis, visited, ShenandoahStoreValEnqueueBarrier ? ShenandoahOopStore : ShenandoahValue, trace, barriers_used)) {
          report_verify_failure("Shenandoah verification: Store should have barriers", n);
        }
      }
      if (!ShenandoahBarrierNode::verify_helper(n->in(MemNode::Address), phis, visited, ShenandoahStore, trace, barriers_used)) {
        report_verify_failure("Shenandoah verification: Store (address) should have barriers", n);
      }
    } else if (n->Opcode() == Op_CmpP) {
      const bool trace = false;

      Node* in1 = n->in(1);
      Node* in2 = n->in(2);
      if (in1->bottom_type()->isa_oopptr()) {
        if (trace) {tty->print("Verifying"); n->dump();}

        bool mark_inputs = false;
        if (in1->bottom_type() == TypePtr::NULL_PTR || in2->bottom_type() == TypePtr::NULL_PTR ||
            (in1->is_Con() || in2->is_Con())) {
          if (trace) {tty->print_cr("Comparison against a constant");}
          mark_inputs = true;
        } else if ((in1->is_CheckCastPP() && in1->in(1)->is_Proj() && in1->in(1)->in(0)->is_Allocate()) ||
                   (in2->is_CheckCastPP() && in2->in(1)->is_Proj() && in2->in(1)->in(0)->is_Allocate())) {
          if (trace) {tty->print_cr("Comparison with newly alloc'ed object");}
          mark_inputs = true;
        } else {
          assert(in2->bottom_type()->isa_oopptr(), "");

          if (!ShenandoahBarrierNode::verify_helper(in1, phis, visited, ShenandoahStore, trace, barriers_used) ||
              !ShenandoahBarrierNode::verify_helper(in2, phis, visited, ShenandoahStore, trace, barriers_used)) {
            report_verify_failure("Shenandoah verification: Cmp should have barriers", n);
          }
        }
        if (verify_no_useless_barrier &&
            mark_inputs &&
            (!ShenandoahBarrierNode::verify_helper(in1, phis, visited, ShenandoahValue, trace, barriers_used) ||
             !ShenandoahBarrierNode::verify_helper(in2, phis, visited, ShenandoahValue, trace, barriers_used))) {
          phis.clear();
          visited.Reset();
        }
      }
    } else if (n->is_LoadStore()) {
      if (n->in(MemNode::ValueIn)->bottom_type()->make_ptr() &&
          !ShenandoahBarrierNode::verify_helper(n->in(MemNode::ValueIn), phis, visited, ShenandoahStoreValEnqueueBarrier ? ShenandoahOopStore : ShenandoahValue, trace, barriers_used)) {
        report_verify_failure("Shenandoah verification: LoadStore (value) should have barriers", n);
      }

      if (n->in(MemNode::Address)->bottom_type()->make_oopptr() && !ShenandoahBarrierNode::verify_helper(n->in(MemNode::Address), phis, visited, ShenandoahStore, trace, barriers_used)) {
        report_verify_failure("Shenandoah verification: LoadStore (address) should have barriers", n);
      }
    } else if (n->Opcode() == Op_CallLeafNoFP || n->Opcode() == Op_CallLeaf) {
      CallNode* call = n->as_Call();

      static struct {
        const char* name;
        struct {
          int pos;
          verify_type t;
        } args[6];
      } calls[] = {
        "aescrypt_encryptBlock",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+1, ShenandoahStore },  { TypeFunc::Parms+2, ShenandoahLoad },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "aescrypt_decryptBlock",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+1, ShenandoahStore },  { TypeFunc::Parms+2, ShenandoahLoad },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "multiplyToLen",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+2, ShenandoahLoad },   { TypeFunc::Parms+4, ShenandoahStore },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "squareToLen",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+2, ShenandoahLoad },   { -1,  ShenandoahNone},
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "montgomery_multiply",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+1, ShenandoahLoad },   { TypeFunc::Parms+2, ShenandoahLoad },
          { TypeFunc::Parms+6, ShenandoahStore }, { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "montgomery_square",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+1, ShenandoahLoad },   { TypeFunc::Parms+5, ShenandoahStore },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "mulAdd",
        { { TypeFunc::Parms, ShenandoahStore },  { TypeFunc::Parms+1, ShenandoahLoad },   { -1,  ShenandoahNone},
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "vectorizedMismatch",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+1, ShenandoahLoad },   { -1,  ShenandoahNone},
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "updateBytesCRC32",
        { { TypeFunc::Parms+1, ShenandoahLoad }, { -1,  ShenandoahNone},                  { -1,  ShenandoahNone},
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "updateBytesAdler32",
        { { TypeFunc::Parms+1, ShenandoahLoad }, { -1,  ShenandoahNone},                  { -1,  ShenandoahNone},
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "updateBytesCRC32C",
        { { TypeFunc::Parms+1, ShenandoahLoad }, { TypeFunc::Parms+3, ShenandoahLoad},    { -1,  ShenandoahNone},
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "counterMode_AESCrypt",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+1, ShenandoahStore },  { TypeFunc::Parms+2, ShenandoahLoad },
          { TypeFunc::Parms+3, ShenandoahStore }, { TypeFunc::Parms+5, ShenandoahStore }, { TypeFunc::Parms+6, ShenandoahStore } },
        "cipherBlockChaining_encryptAESCrypt",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+1, ShenandoahStore },  { TypeFunc::Parms+2, ShenandoahLoad },
          { TypeFunc::Parms+3, ShenandoahLoad },  { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "cipherBlockChaining_decryptAESCrypt",
        { { TypeFunc::Parms, ShenandoahLoad },   { TypeFunc::Parms+1, ShenandoahStore },  { TypeFunc::Parms+2, ShenandoahLoad },
          { TypeFunc::Parms+3, ShenandoahLoad },  { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "shenandoah_clone_barrier",
        { { TypeFunc::Parms, ShenandoahLoad },   { -1,  ShenandoahNone},                  { -1,  ShenandoahNone},
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "ghash_processBlocks",
        { { TypeFunc::Parms, ShenandoahStore },  { TypeFunc::Parms+1, ShenandoahLoad },   { TypeFunc::Parms+2, ShenandoahLoad },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "sha1_implCompress",
        { { TypeFunc::Parms, ShenandoahLoad },  { TypeFunc::Parms+1, ShenandoahStore },   { -1, ShenandoahNone },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "sha256_implCompress",
        { { TypeFunc::Parms, ShenandoahLoad },  { TypeFunc::Parms+1, ShenandoahStore },   { -1, ShenandoahNone },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "sha512_implCompress",
        { { TypeFunc::Parms, ShenandoahLoad },  { TypeFunc::Parms+1, ShenandoahStore },   { -1, ShenandoahNone },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "sha1_implCompressMB",
        { { TypeFunc::Parms, ShenandoahLoad },  { TypeFunc::Parms+1, ShenandoahStore },   { -1, ShenandoahNone },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "sha256_implCompressMB",
        { { TypeFunc::Parms, ShenandoahLoad },  { TypeFunc::Parms+1, ShenandoahStore },   { -1, ShenandoahNone },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "sha512_implCompressMB",
        { { TypeFunc::Parms, ShenandoahLoad },  { TypeFunc::Parms+1, ShenandoahStore },   { -1, ShenandoahNone },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
        "encodeBlock",
        { { TypeFunc::Parms, ShenandoahLoad },  { TypeFunc::Parms+3, ShenandoahStore },   { -1, ShenandoahNone },
          { -1,  ShenandoahNone},                 { -1,  ShenandoahNone},                 { -1,  ShenandoahNone} },
      };

      if (call->is_call_to_arraycopystub()) {
        Node* dest = NULL;
        const TypeTuple* args = n->as_Call()->_tf->domain();
        for (uint i = TypeFunc::Parms, j = 0; i < args->cnt(); i++) {
          if (args->field_at(i)->isa_ptr()) {
            j++;
            if (j == 2) {
              dest = n->in(i);
              break;
            }
          }
        }
        if (!ShenandoahBarrierNode::verify_helper(n->in(TypeFunc::Parms), phis, visited, ShenandoahLoad, trace, barriers_used) ||
            !ShenandoahBarrierNode::verify_helper(dest, phis, visited, ShenandoahStore, trace, barriers_used)) {
          report_verify_failure("Shenandoah verification: ArrayCopy should have barriers", n);
        }
      } else if (strlen(call->_name) > 5 &&
                 !strcmp(call->_name + strlen(call->_name) - 5, "_fill")) {
        if (!ShenandoahBarrierNode::verify_helper(n->in(TypeFunc::Parms), phis, visited, ShenandoahStore, trace, barriers_used)) {
          report_verify_failure("Shenandoah verification: _fill should have barriers", n);
        }
      } else if (!strcmp(call->_name, "shenandoah_wb_pre")) {
        // skip
      } else {
        const int calls_len = sizeof(calls) / sizeof(calls[0]);
        int i = 0;
        for (; i < calls_len; i++) {
          if (!strcmp(calls[i].name, call->_name)) {
            break;
          }
        }
        if (i != calls_len) {
          const uint args_len = sizeof(calls[0].args) / sizeof(calls[0].args[0]);
          for (uint j = 0; j < args_len; j++) {
            int pos = calls[i].args[j].pos;
            if (pos == -1) {
              break;
            }
            if (!ShenandoahBarrierNode::verify_helper(call->in(pos), phis, visited, calls[i].args[j].t, trace, barriers_used)) {
              report_verify_failure("Shenandoah verification: intrinsic calls should have barriers", n);
            }
          }
          for (uint j = TypeFunc::Parms; j < call->req(); j++) {
            if (call->in(j)->bottom_type()->make_ptr() &&
                call->in(j)->bottom_type()->make_ptr()->isa_oopptr()) {
              uint k = 0;
              for (; k < args_len && calls[i].args[k].pos != (int)j; k++);
              if (k == args_len) {
                fatal("arg %d for call %s not covered", j, call->_name);
              }
            }
          }
        } else {
          for (uint j = TypeFunc::Parms; j < call->req(); j++) {
            if (call->in(j)->bottom_type()->make_ptr() &&
                call->in(j)->bottom_type()->make_ptr()->isa_oopptr()) {
              fatal("%s not covered", call->_name);
            }
          }
        }
      }
    } else if (n->is_ShenandoahBarrier()) {
      assert(!barriers.contains(n), "");
      assert(n->Opcode() != Op_ShenandoahWriteBarrier || n->find_out_with(Op_ShenandoahWBMemProj) != NULL, "bad shenandoah write barrier");
      assert(n->Opcode() != Op_ShenandoahWriteBarrier || n->outcnt() > 1, "bad shenandoah write barrier");
      barriers.push(n);
    } else if (n->Opcode() == Op_ShenandoahEnqueueBarrier) {
      // skip
    } else if (n->Opcode() == Op_ShenandoahWBMemProj) {
      assert(n->in(0) == NULL && n->in(ShenandoahWBMemProjNode::WriteBarrier)->Opcode() == Op_ShenandoahWriteBarrier, "strange ShenandoahWBMemProj");
    } else if (n->is_AddP()
               || n->is_Phi()
               || n->is_ConstraintCast()
               || n->Opcode() == Op_Return
               || n->Opcode() == Op_CMoveP
               || n->Opcode() == Op_CMoveN
               || n->Opcode() == Op_Rethrow
               || n->is_MemBar()
               || n->Opcode() == Op_Conv2B
               || n->Opcode() == Op_SafePoint
               || n->is_CallJava()
               || n->Opcode() == Op_Unlock
               || n->Opcode() == Op_EncodeP
               || n->Opcode() == Op_DecodeN) {
      // nothing to do
    } else {
      static struct {
        int opcode;
        struct {
          int pos;
          verify_type t;
        } inputs[2];
      } others[] = {
        Op_FastLock,
        { { 1, ShenandoahLoad },                  { -1, ShenandoahNone} },
        Op_Lock,
        { { TypeFunc::Parms, ShenandoahLoad },    { -1, ShenandoahNone} },
        Op_ArrayCopy,
        { { ArrayCopyNode::Src, ShenandoahLoad }, { ArrayCopyNode::Dest, ShenandoahStore } },
        Op_StrCompressedCopy,
        { { 2, ShenandoahLoad },                  { 3, ShenandoahStore } },
        Op_StrInflatedCopy,
        { { 2, ShenandoahLoad },                  { 3, ShenandoahStore } },
        Op_AryEq,
        { { 2, ShenandoahLoad },                  { 3, ShenandoahLoad } },
        Op_StrIndexOf,
        { { 2, ShenandoahLoad },                  { 4, ShenandoahLoad } },
        Op_StrComp,
        { { 2, ShenandoahLoad },                  { 4, ShenandoahLoad } },
        Op_StrEquals,
        { { 2, ShenandoahLoad },                  { 3, ShenandoahLoad } },
        Op_EncodeISOArray,
        { { 2, ShenandoahLoad },                  { 3, ShenandoahStore } },
        Op_HasNegatives,
        { { 2, ShenandoahLoad },                  { -1, ShenandoahNone} },
        Op_CastP2X,
        { { 1, ShenandoahLoad },                  { -1, ShenandoahNone} },
        Op_StrIndexOfChar,
        { { 2, ShenandoahLoad },                  { -1, ShenandoahNone } },
      };

      const int others_len = sizeof(others) / sizeof(others[0]);
      int i = 0;
      for (; i < others_len; i++) {
        if (others[i].opcode == n->Opcode()) {
          break;
        }
      }
      uint stop = n->is_Call() ? n->as_Call()->tf()->domain()->cnt() : n->req();
      if (i != others_len) {
        const uint inputs_len = sizeof(others[0].inputs) / sizeof(others[0].inputs[0]);
        for (uint j = 0; j < inputs_len; j++) {
          int pos = others[i].inputs[j].pos;
          if (pos == -1) {
            break;
          }
          if (!ShenandoahBarrierNode::verify_helper(n->in(pos), phis, visited, others[i].inputs[j].t, trace, barriers_used)) {
            report_verify_failure("Shenandoah verification: intrinsic calls should have barriers", n);
          }
        }
        for (uint j = 1; j < stop; j++) {
          if (n->in(j) != NULL && n->in(j)->bottom_type()->make_ptr() &&
              n->in(j)->bottom_type()->make_ptr()->make_oopptr()) {
            uint k = 0;
            for (; k < inputs_len && others[i].inputs[k].pos != (int)j; k++);
            if (k == inputs_len) {
              fatal("arg %d for node %s not covered", j, n->Name());
            }
          }
        }
      } else {
        for (uint j = 1; j < stop; j++) {
          if (n->in(j) != NULL && n->in(j)->bottom_type()->make_ptr() &&
              n->in(j)->bottom_type()->make_ptr()->make_oopptr()) {
            fatal("%s not covered", n->Name());
          }
        }
      }
    }

    if (n->is_SafePoint()) {
      SafePointNode* sfpt = n->as_SafePoint();
      if (verify_no_useless_barrier && sfpt->jvms() != NULL) {
        for (uint i = sfpt->jvms()->scloff(); i < sfpt->jvms()->endoff(); i++) {
          if (!ShenandoahBarrierNode::verify_helper(sfpt->in(i), phis, visited, ShenandoahLoad, trace, barriers_used)) {
            phis.clear();
            visited.Reset();
          }
        }
      }
    }
    for( uint i = 0; i < n->len(); ++i ) {
      Node *m = n->in(i);
      if (m == NULL) continue;

      // In most cases, inputs should be known to be non null. If it's
      // not the case, it could be a missing cast_not_null() in an
      // intrinsic or support might be needed in AddPNode::Ideal() to
      // avoid a NULL+offset input.
      if (!(n->is_Phi() ||
            (n->is_SafePoint() && (!n->is_CallRuntime() || !strcmp(n->as_Call()->_name, "shenandoah_wb_pre") || !strcmp(n->as_Call()->_name, "unsafe_arraycopy"))) ||
            n->Opcode() == Op_CmpP ||
            n->Opcode() == Op_CmpN ||
            (n->Opcode() == Op_StoreP && i == StoreNode::ValueIn) ||
            (n->Opcode() == Op_StoreN && i == StoreNode::ValueIn) ||
            n->is_ConstraintCast() ||
            n->Opcode() == Op_Return ||
            n->Opcode() == Op_Conv2B ||
            n->is_AddP() ||
            n->Opcode() == Op_CMoveP ||
            n->Opcode() == Op_CMoveN ||
            n->Opcode() == Op_Rethrow ||
            n->is_MemBar() ||
            n->is_Mem() ||
            n->Opcode() == Op_AryEq ||
            n->Opcode() == Op_SCMemProj ||
            n->Opcode() == Op_EncodeP ||
            n->Opcode() == Op_DecodeN ||
            n->Opcode() == Op_ShenandoahWriteBarrier ||
            n->Opcode() == Op_ShenandoahWBMemProj ||
            n->Opcode() == Op_ShenandoahEnqueueBarrier)) {
        if (m->bottom_type()->make_oopptr() && m->bottom_type()->make_oopptr()->meet(TypePtr::NULL_PTR) == m->bottom_type()) {
          report_verify_failure("Shenandoah verification: null input", n, m);
        }
      }

      wq.push(m);
    }
  }

  if (verify_no_useless_barrier) {
    for (int i = 0; i < barriers.length(); i++) {
      Node* n = barriers.at(i);
      if (!barriers_used.member(n)) {
        tty->print("XXX useless barrier"); n->dump(-2);
        ShouldNotReachHere();
      }
    }
  }
}
#endif

bool ShenandoahBarrierNode::is_dominator_same_ctrl(Node*c, Node* d, Node* n, PhaseIdealLoop* phase) {
  // That both nodes have the same control is not sufficient to prove
  // domination, verify that there's no path from d to n
  ResourceMark rm;
  Unique_Node_List wq;
  wq.push(d);
  for (uint next = 0; next < wq.size(); next++) {
    Node *m = wq.at(next);
    if (m == n) {
      return false;
    }
    if (m->is_Phi() && m->in(0)->is_Loop()) {
      assert(phase->ctrl_or_self(m->in(LoopNode::EntryControl)) != c, "following loop entry should lead to new control");
    } else {
      for (uint i = 0; i < m->req(); i++) {
        if (m->in(i) != NULL && phase->ctrl_or_self(m->in(i)) == c) {
          wq.push(m->in(i));
        }
      }
    }
  }
  return true;
}

bool ShenandoahBarrierNode::is_dominator(Node *d_c, Node *n_c, Node* d, Node* n, PhaseIdealLoop* phase) {
  if (d_c != n_c) {
    return phase->is_dominator(d_c, n_c);
  }
  return is_dominator_same_ctrl(d_c, d, n, phase);
}

Node* next_mem(Node* mem, int alias) {
  Node* res = NULL;
  if (mem->is_Proj()) {
    res = mem->in(0);
  } else if (mem->is_SafePoint() || mem->is_MemBar()) {
    res = mem->in(TypeFunc::Memory);
  } else if (mem->is_Phi()) {
    res = mem->in(1);
  } else if (mem->is_ShenandoahBarrier()) {
    res = mem->in(ShenandoahBarrierNode::Memory);
  } else if (mem->is_MergeMem()) {
    res = mem->as_MergeMem()->memory_at(alias);
  } else if (mem->is_Store() || mem->is_LoadStore() || mem->is_ClearArray()) {
    assert(alias = Compile::AliasIdxRaw, "following raw memory can't lead to a barrier");
    res = mem->in(MemNode::Memory);
  } else if (mem->Opcode() == Op_ShenandoahWBMemProj) {
    res = mem->in(ShenandoahWBMemProjNode::WriteBarrier);
  } else {
#ifdef ASSERT
    mem->dump();
#endif
    ShouldNotReachHere();
  }
  return res;
}

Node* ShenandoahBarrierNode::no_branches(Node* c, Node* dom, bool allow_one_proj, PhaseIdealLoop* phase) {
  Node* iffproj = NULL;
  while (c != dom) {
    Node* next = phase->idom(c);
    assert(next->unique_ctrl_out() == c || c->is_Proj() || c->is_Region(), "multiple control flow out but no proj or region?");
    if (c->is_Region()) {
      ResourceMark rm;
      Unique_Node_List wq;
      wq.push(c);
      for (uint i = 0; i < wq.size(); i++) {
        Node *n = wq.at(i);
        if (n == next) {
          continue;
        }
        if (n->is_Region()) {
          for (uint j = 1; j < n->req(); j++) {
            wq.push(n->in(j));
          }
        } else {
          wq.push(n->in(0));
        }
      }
      for (uint i = 0; i < wq.size(); i++) {
        Node *n = wq.at(i);
        assert(n->is_CFG(), "");
        if (n->is_Multi()) {
          for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
            Node* u = n->fast_out(j);
            if (u->is_CFG()) {
              if (!wq.member(u) && !u->as_Proj()->is_uncommon_trap_proj(Deoptimization::Reason_none)) {
                return NodeSentinel;
              }
            }
          }
        }
      }
    } else  if (c->is_Proj()) {
      if (c->is_IfProj()) {
        if (c->as_Proj()->is_uncommon_trap_if_pattern(Deoptimization::Reason_none) != NULL) {
          // continue;
        } else {
          if (!allow_one_proj) {
            return NodeSentinel;
          }
          if (iffproj == NULL) {
            iffproj = c;
          } else {
            return NodeSentinel;
          }
        }
      } else if (c->Opcode() == Op_JumpProj) {
        return NodeSentinel; // unsupported
      } else if (c->Opcode() == Op_CatchProj) {
        return NodeSentinel; // unsupported
      } else if (c->Opcode() == Op_CProj && next->Opcode() == Op_NeverBranch) {
        return NodeSentinel; // unsupported
      } else {
        assert(next->unique_ctrl_out() == c, "unsupported branch pattern");
      }
    }
    c = next;
  }
  return iffproj;
}

bool ShenandoahBarrierNode::build_loop_late_post(PhaseIdealLoop* phase, Node* n) {
  if (n->Opcode() == Op_ShenandoahReadBarrier ||
      n->Opcode() == Op_ShenandoahWriteBarrier ||
      n->Opcode() == Op_ShenandoahWBMemProj) {

    phase->build_loop_late_post_work(n, false);

    if (n->Opcode() == Op_ShenandoahWriteBarrier) {
      // The write barrier and its memory proj must have the same
      // control otherwise some loop opts could put nodes (Phis) between
      // them
      Node* proj = n->find_out_with(Op_ShenandoahWBMemProj);
      if (proj != NULL) {
        phase->set_ctrl_and_loop(proj, phase->get_ctrl(n));
      }
    }
    return true;
  }
  return false;
}

bool ShenandoahBarrierNode::sink_node(PhaseIdealLoop* phase, Node* ctrl, Node* n_ctrl) {
  ctrl = phase->find_non_split_ctrl(ctrl);
  assert(phase->dom_depth(n_ctrl) <= phase->dom_depth(ctrl), "n is later than its clone");
  set_req(0, ctrl);
  phase->register_new_node(this, ctrl);
  return true;
}

#ifdef ASSERT
void ShenandoahWriteBarrierNode::memory_dominates_all_paths_helper(Node* c, Node* rep_ctrl, Unique_Node_List& controls, PhaseIdealLoop* phase) {
  const bool trace = false;
  if (trace) { tty->print("X control is"); c->dump(); }

  uint start = controls.size();
  controls.push(c);
  for (uint i = start; i < controls.size(); i++) {
    Node *n = controls.at(i);

    if (trace) { tty->print("X from"); n->dump(); }

    if (n == rep_ctrl) {
      continue;
    }

    if (n->is_Proj()) {
      Node* n_dom = n->in(0);
      IdealLoopTree* n_dom_loop = phase->get_loop(n_dom);
      if (n->is_IfProj() && n_dom->outcnt() == 2) {
        n_dom_loop = phase->get_loop(n_dom->as_If()->proj_out(n->as_Proj()->_con == 0 ? 1 : 0));
      }
      if (n_dom_loop != phase->ltree_root()) {
        Node* tail = n_dom_loop->tail();
        if (tail->is_Region()) {
          for (uint j = 1; j < tail->req(); j++) {
            if (phase->is_dominator(n_dom, tail->in(j)) && !phase->is_dominator(n, tail->in(j))) {
              assert(phase->is_dominator(rep_ctrl, tail->in(j)), "why are we here?");
              // entering loop from below, mark backedge
              if (trace) { tty->print("X pushing backedge"); tail->in(j)->dump(); }
              controls.push(tail->in(j));
              //assert(n->in(0) == n_dom, "strange flow control");
            }
          }
        } else if (phase->get_loop(n) != n_dom_loop && phase->is_dominator(n_dom, tail)) {
          // entering loop from below, mark backedge
          if (trace) { tty->print("X pushing backedge"); tail->dump(); }
          controls.push(tail);
          //assert(n->in(0) == n_dom, "strange flow control");
        }
      }
    }

    if (n->is_Loop()) {
      Node* c = n->in(LoopNode::EntryControl);
      if (trace) { tty->print("X pushing"); c->dump(); }
      controls.push(c);
    } else if (n->is_Region()) {
      for (uint i = 1; i < n->req(); i++) {
        Node* c = n->in(i);
        if (trace) { tty->print("X pushing"); c->dump(); }
        controls.push(c);
      }
    } else {
      Node* c = n->in(0);
      if (trace) { tty->print("X pushing"); c->dump(); }
      controls.push(c);
    }
  }
}

bool ShenandoahWriteBarrierNode::memory_dominates_all_paths(Node* mem, Node* rep_ctrl, int alias, PhaseIdealLoop* phase) {
  const bool trace = false;
  if (trace) {
    tty->print("XXX mem is"); mem->dump();
    tty->print("XXX rep ctrl is"); rep_ctrl->dump();
    tty->print_cr("XXX alias is %d", alias);
  }
  ResourceMark rm;
  Unique_Node_List wq;
  Unique_Node_List controls;
  wq.push(mem);
  for (uint next = 0; next < wq.size(); next++) {
    Node *nn = wq.at(next);
    if (trace) { tty->print("XX from mem"); nn->dump(); }
    assert(nn->bottom_type() == Type::MEMORY, "memory only");

    if (nn->is_Phi()) {
      Node* r = nn->in(0);
      for (DUIterator_Fast jmax, j = r->fast_outs(jmax); j < jmax; j++) {
        Node* u = r->fast_out(j);
        if (u->is_Phi() && u->bottom_type() == Type::MEMORY && u != nn &&
            (u->adr_type() == TypePtr::BOTTOM || phase->C->get_alias_index(u->adr_type()) == alias)) {
          if (trace) { tty->print("XX Next mem (other phi)"); u->dump(); }
          wq.push(u);
        }
      }
    }

    for (DUIterator_Fast imax, i = nn->fast_outs(imax); i < imax; i++) {
      Node* use = nn->fast_out(i);

      if (trace) { tty->print("XX use %p", use->adr_type()); use->dump(); }
      if (use->is_CFG() && use->in(TypeFunc::Memory) == nn) {
        Node* c = use->in(0);
        if (phase->is_dominator(rep_ctrl, c)) {
          memory_dominates_all_paths_helper(c, rep_ctrl, controls, phase);
        } else if (use->is_CallStaticJava() && use->as_CallStaticJava()->uncommon_trap_request() != 0 && c->is_Region()) {
          Node* region = c;
          if (trace) { tty->print("XX unc region"); region->dump(); }
          for (uint j = 1; j < region->req(); j++) {
            if (phase->is_dominator(rep_ctrl, region->in(j))) {
              if (trace) { tty->print("XX unc follows"); region->in(j)->dump(); }
              memory_dominates_all_paths_helper(region->in(j), rep_ctrl, controls, phase);
            }
          }
        }
        //continue;
      } else if (use->is_Phi()) {
        assert(use->bottom_type() == Type::MEMORY, "bad phi");
        if ((use->adr_type() == TypePtr::BOTTOM) ||
            phase->C->get_alias_index(use->adr_type()) == alias) {
          for (uint j = 1; j < use->req(); j++) {
            if (use->in(j) == nn) {
              Node* c = use->in(0)->in(j);
              if (phase->is_dominator(rep_ctrl, c)) {
                memory_dominates_all_paths_helper(c, rep_ctrl, controls, phase);
              }
            }
          }
        }
        //        continue;
      }

      if (use->is_MergeMem()) {
        if (use->as_MergeMem()->memory_at(alias) == nn) {
          if (trace) { tty->print("XX Next mem"); use->dump(); }
          // follow the memory edges
          wq.push(use);
        }
      } else if (use->is_Phi()) {
        assert(use->bottom_type() == Type::MEMORY, "bad phi");
        if ((use->adr_type() == TypePtr::BOTTOM) ||
            phase->C->get_alias_index(use->adr_type()) == alias) {
          if (trace) { tty->print("XX Next mem"); use->dump(); }
          // follow the memory edges
          wq.push(use);
        }
      } else if (use->bottom_type() == Type::MEMORY &&
                 (use->adr_type() == TypePtr::BOTTOM || phase->C->get_alias_index(use->adr_type()) == alias)) {
        if (trace) { tty->print("XX Next mem"); use->dump(); }
        // follow the memory edges
        wq.push(use);
      } else if ((use->is_SafePoint() || use->is_MemBar()) &&
                 (use->adr_type() == TypePtr::BOTTOM || phase->C->get_alias_index(use->adr_type()) == alias)) {
        for (DUIterator_Fast jmax, j = use->fast_outs(jmax); j < jmax; j++) {
          Node* u = use->fast_out(j);
          if (u->bottom_type() == Type::MEMORY) {
            if (trace) { tty->print("XX Next mem"); u->dump(); }
            // follow the memory edges
            wq.push(u);
          }
        }
      } else if (use->Opcode() == Op_ShenandoahWriteBarrier && phase->C->get_alias_index(use->adr_type()) == alias) {
        Node* m = use->find_out_with(Op_ShenandoahWBMemProj);
        if (m != NULL) {
          if (trace) { tty->print("XX Next mem"); m->dump(); }
          // follow the memory edges
          wq.push(m);
        }
      }
    }
  }

  if (controls.size() == 0) {
    return false;
  }

  for (uint i = 0; i < controls.size(); i++) {
    Node *n = controls.at(i);

    if (trace) { tty->print("X checking"); n->dump(); }

    if (n->unique_ctrl_out() != NULL) {
      continue;
    }

    if (n->Opcode() == Op_NeverBranch) {
      Node* taken = n->as_Multi()->proj_out(0);
      if (!controls.member(taken)) {
        if (trace) { tty->print("X not seen"); taken->dump(); }
        return false;
      }
      continue;
    }

    for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
      Node* u = n->fast_out(j);

      if (u->is_CFG()) {
        if (!controls.member(u)) {
          if (u->is_Proj() && u->as_Proj()->is_uncommon_trap_proj(Deoptimization::Reason_none)) {
            if (trace) { tty->print("X not seen but unc"); u->dump(); }
          } else {
            Node* c = u;
            do {
              c = c->unique_ctrl_out();
            } while (c != NULL && c->is_Region());
            if (c != NULL && c->Opcode() == Op_Halt) {
              if (trace) { tty->print("X not seen but halt"); c->dump(); }
            } else {
              if (trace) { tty->print("X not seen"); u->dump(); }
              return false;
            }
          }
        } else {
          if (trace) { tty->print("X seen"); u->dump(); }
        }
      }
    }
  }
  return true;
}
#endif

Node* ShenandoahBarrierNode::dom_mem(Node* mem, Node*& mem_ctrl, Node* n, Node* rep_ctrl, int alias, PhaseIdealLoop* phase) {
  ResourceMark rm;
  VectorSet wq(Thread::current()->resource_area());
  wq.set(mem->_idx);
  mem_ctrl = phase->get_ctrl(mem);
  while (!is_dominator(mem_ctrl, rep_ctrl, mem, n, phase)) {
    mem = next_mem(mem, alias);
    if (wq.test_set(mem->_idx)) {
      return NULL; // hit an unexpected loop
    }
    mem_ctrl = phase->ctrl_or_self(mem);
  }
  if (mem->is_MergeMem()) {
    mem = mem->as_MergeMem()->memory_at(alias);
    mem_ctrl = phase->ctrl_or_self(mem);
  }
  return mem;
}

Node* ShenandoahBarrierNode::dom_mem(Node* mem, Node* ctrl, int alias, Node*& mem_ctrl, PhaseIdealLoop* phase) {
  ResourceMark rm;
  VectorSet wq(Thread::current()->resource_area());
  wq.set(mem->_idx);
  mem_ctrl = phase->ctrl_or_self(mem);
  while (!phase->is_dominator(mem_ctrl, ctrl) || mem_ctrl == ctrl) {
    mem = next_mem(mem, alias);
    if (wq.test_set(mem->_idx)) {
      return NULL;
    }
    mem_ctrl = phase->ctrl_or_self(mem);
  }
  if (mem->is_MergeMem()) {
    mem = mem->as_MergeMem()->memory_at(alias);
    mem_ctrl = phase->ctrl_or_self(mem);
  }
  return mem;
}

static void disconnect_barrier_mem(Node* wb, PhaseIterGVN& igvn) {
  Node* mem_in = wb->in(ShenandoahBarrierNode::Memory);
  Node* proj = wb->find_out_with(Op_ShenandoahWBMemProj);

  for (DUIterator_Last imin, i = proj->last_outs(imin); i >= imin; ) {
    Node* u = proj->last_out(i);
    igvn.rehash_node_delayed(u);
    int nb = u->replace_edge(proj, mem_in);
    assert(nb > 0, "no replacement?");
    i -= nb;
  }
}

Node* ShenandoahWriteBarrierNode::move_above_predicates(LoopNode* cl, Node* val_ctrl, PhaseIdealLoop* phase) {
  Node* entry = cl->skip_strip_mined(-1)->in(LoopNode::EntryControl);
  Node* above_pred = phase->skip_all_loop_predicates(entry);
  Node* ctrl = entry;
  while (ctrl != above_pred) {
    Node* next = ctrl->in(0);
    if (!phase->is_dominator(val_ctrl, next)) {
      break;
    }
    ctrl = next;
  }
  return ctrl;
}

static MemoryGraphFixer* find_fixer(GrowableArray<MemoryGraphFixer*>& memory_graph_fixers, int alias) {
  for (int i = 0; i < memory_graph_fixers.length(); i++) {
    if (memory_graph_fixers.at(i)->alias() == alias) {
      return memory_graph_fixers.at(i);
    }
  }
  return NULL;
}

static MemoryGraphFixer* create_fixer(GrowableArray<MemoryGraphFixer*>& memory_graph_fixers, int alias, PhaseIdealLoop* phase, bool include_lsm) {
  assert(find_fixer(memory_graph_fixers, alias) == NULL, "none should exist yet");
  MemoryGraphFixer* fixer = new MemoryGraphFixer(alias, include_lsm, phase);
  memory_graph_fixers.push(fixer);
  return fixer;
}

void ShenandoahWriteBarrierNode::try_move_before_loop_helper(LoopNode* cl, Node* val_ctrl, GrowableArray<MemoryGraphFixer*>& memory_graph_fixers, PhaseIdealLoop* phase, bool include_lsm, Unique_Node_List& uses) {
  assert(cl->is_Loop(), "bad control");
  Node* ctrl = move_above_predicates(cl, val_ctrl, phase);
  Node* mem_ctrl = NULL;
  int alias = phase->C->get_alias_index(adr_type());

  MemoryGraphFixer* fixer = find_fixer(memory_graph_fixers, alias);
  if (fixer == NULL) {
    fixer = create_fixer(memory_graph_fixers, alias, phase, include_lsm);
  }

  Node* proj = find_out_with(Op_ShenandoahWBMemProj);

  fixer->remove(proj);
  Node* mem = fixer->find_mem(ctrl, NULL);

  assert(!ShenandoahVerifyOptoBarriers || memory_dominates_all_paths(mem, ctrl, alias, phase), "can't fix the memory graph");

  phase->set_ctrl_and_loop(this, ctrl);
  phase->igvn().replace_input_of(this, Control, ctrl);

  disconnect_barrier_mem(this, phase->igvn());

  phase->igvn().replace_input_of(this, Memory, mem);
  phase->set_ctrl_and_loop(proj, ctrl);

  fixer->fix_mem(ctrl, ctrl, mem, mem, proj, uses);
  assert(proj->outcnt() > 0, "disconnected write barrier");
}

LoopNode* ShenandoahWriteBarrierNode::try_move_before_pre_loop(Node* c, Node* val_ctrl, PhaseIdealLoop* phase) {
  // A write barrier between a pre and main loop can get in the way of
  // vectorization. Move it above the pre loop if possible
  CountedLoopNode* cl = NULL;
  if (c->is_IfFalse() &&
      c->in(0)->is_CountedLoopEnd()) {
    cl = c->in(0)->as_CountedLoopEnd()->loopnode();
  } else if (c->is_IfProj() &&
             c->in(0)->is_If() &&
             c->in(0)->in(0)->is_IfFalse() &&
             c->in(0)->in(0)->in(0)->is_CountedLoopEnd()) {
    cl = c->in(0)->in(0)->in(0)->as_CountedLoopEnd()->loopnode();
  }
  if (cl != NULL &&
      cl->is_pre_loop() &&
      val_ctrl != cl &&
      phase->is_dominator(val_ctrl, cl)) {
    return cl;
  }
  return NULL;
}

void ShenandoahWriteBarrierNode::try_move_before_loop(GrowableArray<MemoryGraphFixer*>& memory_graph_fixers, PhaseIdealLoop* phase, bool include_lsm, Unique_Node_List& uses) {
  Node *n_ctrl = phase->get_ctrl(this);
  IdealLoopTree *n_loop = phase->get_loop(n_ctrl);
  Node* val = in(ValueIn);
  Node* val_ctrl = phase->get_ctrl(val);
  if (n_loop != phase->ltree_root() && !n_loop->_irreducible) {
    IdealLoopTree *val_loop = phase->get_loop(val_ctrl);
    Node* mem = in(Memory);
    IdealLoopTree *mem_loop = phase->get_loop(phase->get_ctrl(mem));
    if (!n_loop->is_member(val_loop) &&
        n_loop->is_member(mem_loop)) {
      Node* n_loop_head = n_loop->_head;

      if (n_loop_head->is_Loop()) {
        LoopNode* loop = n_loop_head->as_Loop();
        if (n_loop_head->is_CountedLoop() && n_loop_head->as_CountedLoop()->is_main_loop()) {
          LoopNode* res = try_move_before_pre_loop(n_loop_head->in(LoopNode::EntryControl), val_ctrl, phase);
          if (res != NULL) {
            loop = res;
          }
        }

        try_move_before_loop_helper(loop, val_ctrl, memory_graph_fixers, phase, include_lsm, uses);
      }
    }
  }
  LoopNode* ctrl = try_move_before_pre_loop(in(0), val_ctrl, phase);
  if (ctrl != NULL) {
    try_move_before_loop_helper(ctrl, val_ctrl, memory_graph_fixers, phase, include_lsm, uses);
  }
}

Node* ShenandoahWriteBarrierNode::would_subsume(ShenandoahBarrierNode* other, PhaseIdealLoop* phase) {
  Node* val = in(ValueIn);
  Node* val_ctrl = phase->get_ctrl(val);
  Node* other_mem = other->in(Memory);
  Node* other_ctrl = phase->get_ctrl(other);
  Node* this_ctrl = phase->get_ctrl(this);
  IdealLoopTree* this_loop = phase->get_loop(this_ctrl);
  IdealLoopTree* other_loop = phase->get_loop(other_ctrl);

  Node* ctrl = phase->dom_lca(other_ctrl, this_ctrl);

  if (ctrl->is_Proj() &&
      ctrl->in(0)->is_Call() &&
      ctrl->unique_ctrl_out() != NULL &&
      ctrl->unique_ctrl_out()->Opcode() == Op_Catch &&
      !phase->is_dominator(val_ctrl, ctrl->in(0)->in(0))) {
    return NULL;
  }

  IdealLoopTree* loop = phase->get_loop(ctrl);

  // We don't want to move a write barrier in a loop
  // If the LCA is in a inner loop, try a control out of loop if possible
  while (!loop->is_member(this_loop) && (other->Opcode() != Op_ShenandoahWriteBarrier || !loop->is_member(other_loop))) {
    ctrl = phase->idom(ctrl);
    if (ctrl->is_MultiBranch()) {
      ctrl = ctrl->in(0);
    }
    if (ctrl != val_ctrl && phase->is_dominator(ctrl, val_ctrl)) {
      return NULL;
    }
    loop = phase->get_loop(ctrl);
  }

  if (ShenandoahDontIncreaseWBFreq) {
    Node* this_iffproj = no_branches(this_ctrl, ctrl, true, phase);
    if (other->Opcode() == Op_ShenandoahWriteBarrier) {
      Node* other_iffproj = no_branches(other_ctrl, ctrl, true, phase);
      if (other_iffproj == NULL || this_iffproj == NULL) {
        return ctrl;
      } else if (other_iffproj != NodeSentinel && this_iffproj != NodeSentinel &&
                 other_iffproj->in(0) == this_iffproj->in(0)) {
        return ctrl;
      }
    } else if (this_iffproj == NULL) {
      return ctrl;
    }
    return NULL;
  }

  return ctrl;
}

void ShenandoahWriteBarrierNode::optimize_before_expansion(PhaseIdealLoop* phase, GrowableArray<MemoryGraphFixer*> memory_graph_fixers, bool include_lsm) {
  bool progress = false;
  Unique_Node_List uses;
  do {
    progress = false;
    for (int i = 0; i < ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barriers_count(); i++) {
      ShenandoahWriteBarrierNode* wb = ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barrier(i);

      wb->try_move_before_loop(memory_graph_fixers, phase, include_lsm, uses);

      Node* val = wb->in(ValueIn);

      for (DUIterator_Fast jmax, j = val->fast_outs(jmax); j < jmax; j++) {
        Node* u = val->fast_out(j);
        if (u != wb && u->is_ShenandoahBarrier()) {
          Node* rep_ctrl = wb->would_subsume(u->as_ShenandoahBarrier(), phase);

          if (rep_ctrl != NULL) {
            Node* other = u;
            Node* val_ctrl = phase->get_ctrl(val);
            if (rep_ctrl->is_Proj() &&
                rep_ctrl->in(0)->is_Call() &&
                rep_ctrl->unique_ctrl_out() != NULL &&
                rep_ctrl->unique_ctrl_out()->Opcode() == Op_Catch) {
              rep_ctrl = rep_ctrl->in(0)->in(0);

              assert(phase->is_dominator(val_ctrl, rep_ctrl), "bad control");
            } else {
              LoopNode* c = ShenandoahWriteBarrierNode::try_move_before_pre_loop(rep_ctrl, val_ctrl, phase);
              if (c != NULL) {
                rep_ctrl = ShenandoahWriteBarrierNode::move_above_predicates(c, val_ctrl, phase);
              } else {
                while (rep_ctrl->is_IfProj()) {
                  CallStaticJavaNode* unc = rep_ctrl->as_Proj()->is_uncommon_trap_if_pattern(Deoptimization::Reason_none);
                  if (unc != NULL) {
                    int req = unc->uncommon_trap_request();
                    Deoptimization::DeoptReason trap_reason = Deoptimization::trap_request_reason(req);
                    if ((trap_reason == Deoptimization::Reason_loop_limit_check ||
                         trap_reason == Deoptimization::Reason_predicate ||
                         trap_reason == Deoptimization::Reason_profile_predicate) &&
                        phase->is_dominator(val_ctrl, rep_ctrl->in(0)->in(0))) {
                      rep_ctrl = rep_ctrl->in(0)->in(0);
                      continue;
                    }
                  }
                  break;
                }
              }
            }

            Node* wb_ctrl = phase->get_ctrl(wb);
            Node* other_ctrl = phase->get_ctrl(other);
            int alias = phase->C->get_alias_index(wb->adr_type());
            MemoryGraphFixer* fixer = find_fixer(memory_graph_fixers, alias);;
            if (!is_dominator(wb_ctrl, other_ctrl, wb, other, phase)) {
              if (fixer == NULL) {
                fixer = create_fixer(memory_graph_fixers, alias, phase, include_lsm);
              }
              Node* mem = fixer->find_mem(rep_ctrl, phase->get_ctrl(other) == rep_ctrl ? other : NULL);

              if (mem->has_out_with(Op_Lock) || mem->has_out_with(Op_Unlock)) {
                continue;
              }

              Node* wb_proj = wb->find_out_with(Op_ShenandoahWBMemProj);
              fixer->remove(wb_proj);
              Node* mem_for_ctrl = fixer->find_mem(rep_ctrl, NULL);

              if (wb->in(Memory) != mem) {
                disconnect_barrier_mem(wb, phase->igvn());
                phase->igvn().replace_input_of(wb, Memory, mem);
              }
              if (rep_ctrl != wb_ctrl) {
                phase->set_ctrl_and_loop(wb, rep_ctrl);
                phase->igvn().replace_input_of(wb, Control, rep_ctrl);
                phase->set_ctrl_and_loop(wb_proj, rep_ctrl);
                progress = true;
              }

              fixer->fix_mem(rep_ctrl, rep_ctrl, mem, mem_for_ctrl, wb_proj, uses);

              assert(!ShenandoahVerifyOptoBarriers || ShenandoahWriteBarrierNode::memory_dominates_all_paths(mem, rep_ctrl, alias, phase), "can't fix the memory graph");
            }

            if (other->Opcode() == Op_ShenandoahWriteBarrier) {
              Node* other_proj = other->find_out_with(Op_ShenandoahWBMemProj);
              if (fixer != NULL) {
                fixer->remove(other_proj);
              }
              phase->igvn().replace_node(other_proj, other->in(Memory));
            }
            phase->igvn().replace_node(other, wb);
            --j; --jmax;
          }
        }
      }
    }
  } while(progress);
}

// Some code duplication with PhaseIdealLoop::split_if_with_blocks_pre()
Node* ShenandoahWriteBarrierNode::try_split_thru_phi(PhaseIdealLoop* phase) {
  Node *ctrl = phase->get_ctrl(this);
  if (ctrl == NULL) {
    return this;
  }
  Node *blk = phase->has_local_phi_input(this);
  if (blk == NULL) {
    return this;
  }

  if (in(0) != blk) {
    return this;
  }

  int policy = blk->req() >> 2;

  if (blk->is_CountedLoop()) {
    IdealLoopTree *lp = phase->get_loop(blk);
    if (lp && lp->_rce_candidate) {
      return this;
    }
  }

  if (phase->C->live_nodes() > 35000) {
    return this;
  }

  uint unique = phase->C->unique();
  Node *phi = phase->split_thru_phi(this, blk, policy);
  if (phi == NULL) {
    return this;
  }

  Node* mem_phi = new PhiNode(blk, Type::MEMORY, phase->C->alias_type(adr_type())->adr_type());
  for (uint i = 1; i < blk->req(); i++) {
    Node* n = phi->in(i);
    if (n->Opcode() == Op_ShenandoahWriteBarrier &&
        n->_idx >= unique) {
      Node* proj = new ShenandoahWBMemProjNode(n);
      phase->register_new_node(proj, phase->get_ctrl(n));
      mem_phi->init_req(i, proj);
    } else {
      Node* mem = in(ShenandoahBarrierNode::Memory);
      if (mem->is_Phi() && mem->in(0) == blk) {
        mem = mem->in(i);
      }
      mem_phi->init_req(i, mem);
    }
  }
  phase->register_new_node(mem_phi, blk);


  Node* proj = find_out_with(Op_ShenandoahWBMemProj);
  phase->igvn().replace_node(proj, mem_phi);
  phase->igvn().replace_node(this, phi);

  return phi;
}

void ShenandoahReadBarrierNode::try_move(PhaseIdealLoop* phase) {
  Node *n_ctrl = phase->get_ctrl(this);
  if (n_ctrl == NULL) {
    return;
  }
  Node* mem = in(MemNode::Memory);
  int alias = phase->C->get_alias_index(adr_type());
  const bool trace = false;

#ifdef ASSERT
  if (trace) { tty->print("Trying to move mem of"); dump(); }
#endif

  Node* new_mem = mem;

  ResourceMark rm;
  VectorSet seen(Thread::current()->resource_area());
  Node_List phis;

  for (;;) {
#ifdef ASSERT
    if (trace) { tty->print("Looking for dominator from"); mem->dump(); }
#endif
    if (mem->is_Proj() && mem->in(0)->is_Start()) {
      if (new_mem != in(MemNode::Memory)) {
#ifdef ASSERT
        if (trace) { tty->print("XXX Setting mem to"); new_mem->dump(); tty->print(" for "); dump(); }
#endif
        phase->igvn().replace_input_of(this, MemNode::Memory, new_mem);
      }
      return;
    }

    Node* candidate = mem;
    do {
      if (!is_independent(mem)) {
        if (trace) { tty->print_cr("Not independent"); }
        if (new_mem != in(MemNode::Memory)) {
#ifdef ASSERT
          if (trace) { tty->print("XXX Setting mem to"); new_mem->dump(); tty->print(" for "); dump(); }
#endif
          phase->igvn().replace_input_of(this, MemNode::Memory, new_mem);
        }
        return;
      }
      if (seen.test_set(mem->_idx)) {
        if (trace) { tty->print_cr("Already seen"); }
        ShouldNotReachHere();
        // Strange graph
        if (new_mem != in(MemNode::Memory)) {
#ifdef ASSERT
          if (trace) { tty->print("XXX Setting mem to"); new_mem->dump(); tty->print(" for "); dump(); }
#endif
          phase->igvn().replace_input_of(this, MemNode::Memory, new_mem);
        }
        return;
      }
      if (mem->is_Phi()) {
        phis.push(mem);
      }
      mem = next_mem(mem, alias);
      if (mem->bottom_type() == Type::MEMORY) {
        candidate = mem;
      }
      assert(is_dominator(phase->ctrl_or_self(mem), n_ctrl, mem, this, phase) == phase->is_dominator(phase->ctrl_or_self(mem), n_ctrl), "strange dominator");
#ifdef ASSERT
      if (trace) { tty->print("Next mem is"); mem->dump(); }
#endif
    } while (mem->bottom_type() != Type::MEMORY || !phase->is_dominator(phase->ctrl_or_self(mem), n_ctrl));

    assert(mem->bottom_type() == Type::MEMORY, "bad mem");

    bool not_dom = false;
    for (uint i = 0; i < phis.size() && !not_dom; i++) {
      Node* nn = phis.at(i);

#ifdef ASSERT
      if (trace) { tty->print("Looking from phi"); nn->dump(); }
#endif
      assert(nn->is_Phi(), "phis only");
      for (uint j = 2; j < nn->req() && !not_dom; j++) {
        Node* m = nn->in(j);
#ifdef ASSERT
        if (trace) { tty->print("Input %d is", j); m->dump(); }
#endif
        while (m != mem && !seen.test_set(m->_idx)) {
          if (is_dominator(phase->ctrl_or_self(m), phase->ctrl_or_self(mem), m, mem, phase)) {
            not_dom = true;
            // Scheduling anomaly
#ifdef ASSERT
            if (trace) { tty->print("Giving up"); m->dump(); }
#endif
            break;
          }
          if (!is_independent(m)) {
            if (trace) { tty->print_cr("Not independent"); }
            if (new_mem != in(MemNode::Memory)) {
#ifdef ASSERT
              if (trace) { tty->print("XXX Setting mem to"); new_mem->dump(); tty->print(" for "); dump(); }
#endif
              phase->igvn().replace_input_of(this, MemNode::Memory, new_mem);
            }
            return;
          }
          if (m->is_Phi()) {
            phis.push(m);
          }
          m = next_mem(m, alias);
#ifdef ASSERT
          if (trace) { tty->print("Next mem is"); m->dump(); }
#endif
        }
      }
    }
    if (!not_dom) {
      new_mem = mem;
      phis.clear();
    } else {
      seen.Clear();
    }
  }
}

CallStaticJavaNode* ShenandoahWriteBarrierNode::pin_and_expand_null_check(PhaseIterGVN& igvn) {
  Node* val = in(ValueIn);

  const Type* val_t = igvn.type(val);

  if (val_t->meet(TypePtr::NULL_PTR) != val_t &&
      val->Opcode() == Op_CastPP &&
      val->in(0) != NULL &&
      val->in(0)->Opcode() == Op_IfTrue &&
      val->in(0)->as_Proj()->is_uncommon_trap_if_pattern(Deoptimization::Reason_none) &&
      val->in(0)->in(0)->is_If() &&
      val->in(0)->in(0)->in(1)->Opcode() == Op_Bool &&
      val->in(0)->in(0)->in(1)->as_Bool()->_test._test == BoolTest::ne &&
      val->in(0)->in(0)->in(1)->in(1)->Opcode() == Op_CmpP &&
      val->in(0)->in(0)->in(1)->in(1)->in(1) == val->in(1) &&
      val->in(0)->in(0)->in(1)->in(1)->in(2)->bottom_type() == TypePtr::NULL_PTR) {
    assert(val->in(0)->in(0)->in(1)->in(1)->in(1) == val->in(1), "");
    CallStaticJavaNode* unc = val->in(0)->as_Proj()->is_uncommon_trap_if_pattern(Deoptimization::Reason_none);
    return unc;
  }
  return NULL;
}

void ShenandoahWriteBarrierNode::pin_and_expand_move_barrier(PhaseIdealLoop* phase, GrowableArray<MemoryGraphFixer*>& memory_graph_fixers, Unique_Node_List& uses) {
  Node* unc = pin_and_expand_null_check(phase->igvn());
  Node* val = in(ValueIn);

  if (unc != NULL) {
    Node* ctrl = phase->get_ctrl(this);
    Node* unc_ctrl = val->in(0);

    // Don't move write barrier in a loop
    IdealLoopTree* loop = phase->get_loop(ctrl);
    IdealLoopTree* unc_loop = phase->get_loop(unc_ctrl);

    if (!unc_loop->is_member(loop)) {
      return;
    }

    Node* branch = no_branches(ctrl, unc_ctrl, false, phase);
    assert(branch == NULL || branch == NodeSentinel, "was not looking for a branch");
    if (branch == NodeSentinel) {
      return;
    }

    RegionNode* r = new RegionNode(3);
    IfNode* iff = unc_ctrl->in(0)->as_If();

    Node* ctrl_use = unc_ctrl->unique_ctrl_out();
    Node* unc_ctrl_clone = unc_ctrl->clone();
    phase->register_control(unc_ctrl_clone, loop, iff);
    Node* c = unc_ctrl_clone;
    Node* new_cast = clone_null_check(c, val, unc_ctrl_clone, phase);
    r->init_req(1, new_cast->in(0)->in(0)->as_If()->proj_out(0));

    phase->igvn().replace_input_of(unc_ctrl, 0, c->in(0));
    phase->set_idom(unc_ctrl, c->in(0), phase->dom_depth(unc_ctrl));
    phase->lazy_replace(c, unc_ctrl);
    c = NULL;;
    phase->igvn().replace_input_of(val, 0, unc_ctrl_clone);
    phase->set_ctrl(val, unc_ctrl_clone);

    IfNode* new_iff = new_cast->in(0)->in(0)->as_If();
    fix_null_check(unc, unc_ctrl_clone, r, uses, phase);
    Node* iff_proj = iff->proj_out(0);
    r->init_req(2, iff_proj);
    phase->register_control(r, phase->ltree_root(), iff);

    Node* new_bol = new_iff->in(1)->clone();
    Node* new_cmp = new_bol->in(1)->clone();
    assert(new_cmp->Opcode() == Op_CmpP, "broken");
    assert(new_cmp->in(1) == val->in(1), "broken");
    new_bol->set_req(1, new_cmp);
    new_cmp->set_req(1, this);
    phase->register_new_node(new_bol, new_iff->in(0));
    phase->register_new_node(new_cmp, new_iff->in(0));
    phase->igvn().replace_input_of(new_iff, 1, new_bol);
    phase->igvn().replace_input_of(new_cast, 1, this);

    for (DUIterator_Fast imax, i = this->fast_outs(imax); i < imax; i++) {
      Node* u = this->fast_out(i);
      if (u == new_cast || u->Opcode() == Op_ShenandoahWBMemProj || u == new_cmp) {
        continue;
      }
      phase->igvn().rehash_node_delayed(u);
      int nb = u->replace_edge(this, new_cast);
      assert(nb > 0, "no update?");
      --i; imax -= nb;
    }

    for (DUIterator_Fast imax, i = val->fast_outs(imax); i < imax; i++) {
      Node* u = val->fast_out(i);
      if (u == this) {
        continue;
      }
      phase->igvn().rehash_node_delayed(u);
      int nb = u->replace_edge(val, new_cast);
      assert(nb > 0, "no update?");
      --i; imax -= nb;
    }

    Node* new_ctrl = unc_ctrl_clone;

    int alias = phase->C->get_alias_index(adr_type());
    MemoryGraphFixer* fixer = find_fixer(memory_graph_fixers, alias);
    if (fixer == NULL) {
      fixer = create_fixer(memory_graph_fixers, alias, phase, true);
    }

    Node* proj = find_out_with(Op_ShenandoahWBMemProj);
    fixer->remove(proj);
    Node* mem = fixer->find_mem(new_ctrl, NULL);

    if (in(Memory) != mem) {
      disconnect_barrier_mem(this, phase->igvn());
      phase->igvn().replace_input_of(this, Memory, mem);
    }

    phase->set_ctrl_and_loop(this, new_ctrl);
    phase->igvn().replace_input_of(this, Control, new_ctrl);
    phase->set_ctrl_and_loop(proj, new_ctrl);

    fixer->fix_mem(new_ctrl, new_ctrl, mem, mem, proj, uses);
  }
}

void ShenandoahWriteBarrierNode::pin_and_expand_helper(PhaseIdealLoop* phase) {
  Node* val = in(ValueIn);
  CallStaticJavaNode* unc = pin_and_expand_null_check(phase->igvn());
  Node* rep = this;
  Node* ctrl = phase->get_ctrl(this);
  if (unc != NULL && val->in(0) == ctrl) {
    Node* unc_ctrl = val->in(0);
    IfNode* other_iff = unc_ctrl->unique_ctrl_out()->as_If();
    ProjNode* other_unc_ctrl = other_iff->proj_out(1);
    Node* cast = NULL;
    for (DUIterator_Fast imax, i = other_unc_ctrl->fast_outs(imax); i < imax && cast == NULL; i++) {
      Node* u = other_unc_ctrl->fast_out(i);
      if (u->Opcode() == Op_CastPP && u->in(1) == this) {
        cast = u;
      }
    }
    assert(other_unc_ctrl->is_uncommon_trap_if_pattern(Deoptimization::Reason_none) == unc, "broken");
    rep = cast;
  }

  // Replace all uses of barrier's input that are dominated by ctrl
  // with the value returned by the barrier: no need to keep both
  // live.
  for (DUIterator_Fast imax, i = val->fast_outs(imax); i < imax; i++) {
    Node* u = val->fast_out(i);
    if (u != this) {
      if (u->is_Phi()) {
        int nb = 0;
        for (uint j = 1; j < u->req(); j++) {
          if (u->in(j) == val) {
            Node* c = u->in(0)->in(j);
            if (phase->is_dominator(ctrl, c)) {
              phase->igvn().replace_input_of(u, j, rep);
              nb++;
            }
          }
        }
        if (nb > 0) {
          imax -= nb;
          --i;
        }
      } else {
        Node* c = phase->ctrl_or_self(u);
        if (is_dominator(ctrl, c, this, u, phase)) {
          phase->igvn().rehash_node_delayed(u);
          int nb = u->replace_edge(val, rep);
          assert(nb > 0, "no update?");
          --i, imax -= nb;
        }
      }
    }
  }
}

Node* ShenandoahWriteBarrierNode::find_bottom_mem(Node* ctrl, PhaseIdealLoop* phase) {
  Node* mem = NULL;
  Node* c = ctrl;
  do {
    if (c->is_Region()) {
      Node* phi_bottom = NULL;
      for (DUIterator_Fast imax, i = c->fast_outs(imax); i < imax && mem == NULL; i++) {
        Node* u = c->fast_out(i);
        if (u->is_Phi() && u->bottom_type() == Type::MEMORY) {
          if (u->adr_type() == TypePtr::BOTTOM) {
            mem = u;
          }
        }
      }
    } else {
      if (c->is_Call() && c->as_Call()->adr_type() != NULL) {
        CallProjections projs;
        c->as_Call()->extract_projections(&projs, true, false);
        if (projs.fallthrough_memproj != NULL) {
          if (projs.fallthrough_memproj->adr_type() == TypePtr::BOTTOM) {
            if (projs.catchall_memproj == NULL) {
              mem = projs.fallthrough_memproj;
            } else {
              if (phase->is_dominator(projs.fallthrough_catchproj, ctrl)) {
                mem = projs.fallthrough_memproj;
              } else {
                assert(phase->is_dominator(projs.catchall_catchproj, ctrl), "one proj must dominate barrier");
                mem = projs.catchall_memproj;
              }
            }
          }
        } else {
          Node* proj = c->as_Call()->proj_out(TypeFunc::Memory);
          if (proj != NULL &&
              proj->adr_type() == TypePtr::BOTTOM) {
            mem = proj;
          }
        }
      } else {
        for (DUIterator_Fast imax, i = c->fast_outs(imax); i < imax; i++) {
          Node* u = c->fast_out(i);
          if (u->is_Proj() &&
              u->bottom_type() == Type::MEMORY &&
              u->adr_type() == TypePtr::BOTTOM) {
              assert(c->is_SafePoint() || c->is_MemBar() || c->is_Start(), "");
              assert(mem == NULL, "only one proj");
              mem = u;
          }
        }
        assert(!c->is_Call() || c->as_Call()->adr_type() != NULL || mem == NULL, "no mem projection expected");
      }
    }
    c = phase->idom(c);
  } while (mem == NULL);
  return mem;
}

void ShenandoahWriteBarrierNode::follow_barrier_uses(Node* n, Node* ctrl, Unique_Node_List& uses, PhaseIdealLoop* phase) {
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* u = n->fast_out(i);
    if (!u->is_CFG() && phase->get_ctrl(u) == ctrl && (!u->is_Phi() || !u->in(0)->is_Loop() || u->in(LoopNode::LoopBackControl) != n)) {
      uses.push(u);
    }
  }
}

static void hide_strip_mined_loop(OuterStripMinedLoopNode* outer, CountedLoopNode* inner, PhaseIdealLoop* phase) {
  OuterStripMinedLoopEndNode* le = inner->outer_loop_end();
  Node* new_outer = new LoopNode(outer->in(LoopNode::EntryControl), outer->in(LoopNode::LoopBackControl));
  phase->register_control(new_outer, phase->get_loop(outer), outer->in(LoopNode::EntryControl));
  Node* new_le = new IfNode(le->in(0), le->in(1), le->_prob, le->_fcnt);
  phase->register_control(new_le, phase->get_loop(le), le->in(0));
  phase->lazy_replace(outer, new_outer);
  phase->lazy_replace(le, new_le);
  inner->clear_strip_mined();
}

void ShenandoahWriteBarrierNode::test_heap_stable(Node*& ctrl, Node* raw_mem, Node*& heap_stable_ctrl,
                                                  PhaseIdealLoop* phase) {
  IdealLoopTree* loop = phase->get_loop(ctrl);
  Node* thread = new ThreadLocalNode();
  phase->register_new_node(thread, ctrl);
  Node* offset = phase->igvn().MakeConX(in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  phase->set_ctrl(offset, phase->C->root());
  Node* gc_state_addr = new AddPNode(phase->C->top(), thread, offset);
  phase->register_new_node(gc_state_addr, ctrl);
  uint gc_state_idx = Compile::AliasIdxRaw;
  const TypePtr* gc_state_adr_type = NULL; // debug-mode-only argument
  debug_only(gc_state_adr_type = phase->C->get_adr_type(gc_state_idx));

  Node* gc_state = new LoadBNode(ctrl, raw_mem, gc_state_addr, gc_state_adr_type, TypeInt::BYTE, MemNode::unordered);
  phase->register_new_node(gc_state, ctrl);
  Node* heap_stable_and = new AndINode(gc_state, phase->igvn().intcon(ShenandoahHeap::HAS_FORWARDED));
  phase->register_new_node(heap_stable_and, ctrl);
  Node* heap_stable_cmp = new CmpINode(heap_stable_and, phase->igvn().zerocon(T_INT));
  phase->register_new_node(heap_stable_cmp, ctrl);
  Node* heap_stable_test = new BoolNode(heap_stable_cmp, BoolTest::ne);
  phase->register_new_node(heap_stable_test, ctrl);
  IfNode* heap_stable_iff = new IfNode(ctrl, heap_stable_test, PROB_UNLIKELY(0.999), COUNT_UNKNOWN);
  phase->register_control(heap_stable_iff, loop, ctrl);

  heap_stable_ctrl = new IfFalseNode(heap_stable_iff);
  phase->register_control(heap_stable_ctrl, loop, heap_stable_iff);
  ctrl = new IfTrueNode(heap_stable_iff);
  phase->register_control(ctrl, loop, heap_stable_iff);

  assert(is_heap_stable_test(heap_stable_iff), "Should match the shape");
}

void ShenandoahWriteBarrierNode::test_null(Node*& ctrl, Node* val, Node*& null_ctrl, PhaseIdealLoop* phase) {
  const Type* val_t = phase->igvn().type(val);
  if (val_t->meet(TypePtr::NULL_PTR) == val_t) {
    IdealLoopTree* loop = phase->get_loop(ctrl);
    Node* null_cmp = new CmpPNode(val, phase->igvn().zerocon(T_OBJECT));
    phase->register_new_node(null_cmp, ctrl);
    Node* null_test = new BoolNode(null_cmp, BoolTest::ne);
    phase->register_new_node(null_test, ctrl);
    IfNode* null_iff = new IfNode(ctrl, null_test, PROB_LIKELY(0.999), COUNT_UNKNOWN);
    phase->register_control(null_iff, loop, ctrl);
    ctrl = new IfTrueNode(null_iff);
    phase->register_control(ctrl, loop, null_iff);
    null_ctrl = new IfFalseNode(null_iff);
    phase->register_control(null_ctrl, loop, null_iff);
  }
}

Node* ShenandoahWriteBarrierNode::clone_null_check(Node*& c, Node* val, Node* unc_ctrl, PhaseIdealLoop* phase) {
  IdealLoopTree *loop = phase->get_loop(c);
  Node* iff = unc_ctrl->in(0);
  assert(iff->is_If(), "broken");
  Node* new_iff = iff->clone();
  new_iff->set_req(0, c);
  phase->register_control(new_iff, loop, c);
  Node* iffalse = new IfFalseNode(new_iff->as_If());
  phase->register_control(iffalse, loop, new_iff);
  Node* iftrue = new IfTrueNode(new_iff->as_If());
  phase->register_control(iftrue, loop, new_iff);
  c = iftrue;
  const Type *t = phase->igvn().type(val);
  assert(val->Opcode() == Op_CastPP, "expect cast to non null here");
  Node* uncasted_val = val->in(1);
  val = new CastPPNode(uncasted_val, t);
  val->init_req(0, c);
  phase->register_new_node(val, c);
  return val;
}

void ShenandoahWriteBarrierNode::fix_null_check(Node* unc, Node* unc_ctrl, Node* new_unc_ctrl,
                                                Unique_Node_List& uses, PhaseIdealLoop* phase) {
  IfNode* iff = unc_ctrl->in(0)->as_If();
  Node* proj = iff->proj_out(0);
  assert(proj != unc_ctrl, "bad projection");
  Node* use = proj->unique_ctrl_out();

  assert(use == unc || use->is_Region(), "what else?");

  uses.clear();
  if (use == unc) {
    phase->set_idom(use, new_unc_ctrl, phase->dom_depth(use));
    for (uint i = 1; i < unc->req(); i++) {
      Node* n = unc->in(i);
      if (phase->has_ctrl(n) && phase->get_ctrl(n) == proj) {
        uses.push(n);
      }
    }
  } else {
    assert(use->is_Region(), "what else?");
    uint idx = 1;
    for (; use->in(idx) != proj; idx++);
    for (DUIterator_Fast imax, i = use->fast_outs(imax); i < imax; i++) {
      Node* u = use->fast_out(i);
      if (u->is_Phi() && phase->get_ctrl(u->in(idx)) == proj) {
        uses.push(u->in(idx));
      }
    }
  }
  for(uint next = 0; next < uses.size(); next++ ) {
    Node *n = uses.at(next);
    assert(phase->get_ctrl(n) == proj, "bad control");
    phase->set_ctrl_and_loop(n, new_unc_ctrl);
    if (n->in(0) == proj) {
      phase->igvn().replace_input_of(n, 0, new_unc_ctrl);
    }
    for (uint i = 0; i < n->req(); i++) {
      Node* m = n->in(i);
      if (m != NULL && phase->has_ctrl(m) && phase->get_ctrl(m) == proj) {
        uses.push(m);
      }
    }
  }

  phase->igvn().rehash_node_delayed(use);
  int nb = use->replace_edge(proj, new_unc_ctrl);
  assert(nb == 1, "only use expected");
}

void ShenandoahWriteBarrierNode::in_cset_fast_test(Node*& ctrl, Node*& not_cset_ctrl, Node* val, Node* raw_mem, PhaseIdealLoop* phase) {
  IdealLoopTree *loop = phase->get_loop(ctrl);
  Node* raw_rbtrue = new CastP2XNode(ctrl, val);
  phase->register_new_node(raw_rbtrue, ctrl);
  Node* cset_offset = new URShiftXNode(raw_rbtrue, phase->igvn().intcon(ShenandoahHeapRegion::region_size_bytes_shift_jint()));
  phase->register_new_node(cset_offset, ctrl);
  Node* in_cset_fast_test_base_addr = phase->igvn().makecon(TypeRawPtr::make(ShenandoahHeap::in_cset_fast_test_addr()));
  phase->set_ctrl(in_cset_fast_test_base_addr, phase->C->root());
  Node* in_cset_fast_test_adr = new AddPNode(phase->C->top(), in_cset_fast_test_base_addr, cset_offset);
  phase->register_new_node(in_cset_fast_test_adr, ctrl);
  uint in_cset_fast_test_idx = Compile::AliasIdxRaw;
  const TypePtr* in_cset_fast_test_adr_type = NULL; // debug-mode-only argument
  debug_only(in_cset_fast_test_adr_type = phase->C->get_adr_type(in_cset_fast_test_idx));
  Node* in_cset_fast_test_load = new LoadBNode(ctrl, raw_mem, in_cset_fast_test_adr, in_cset_fast_test_adr_type, TypeInt::BYTE, MemNode::unordered);
  phase->register_new_node(in_cset_fast_test_load, ctrl);
  Node* in_cset_fast_test_cmp = new CmpINode(in_cset_fast_test_load, phase->igvn().zerocon(T_INT));
  phase->register_new_node(in_cset_fast_test_cmp, ctrl);
  Node* in_cset_fast_test_test = new BoolNode(in_cset_fast_test_cmp, BoolTest::eq);
  phase->register_new_node(in_cset_fast_test_test, ctrl);
  IfNode* in_cset_fast_test_iff = new IfNode(ctrl, in_cset_fast_test_test, PROB_UNLIKELY(0.999), COUNT_UNKNOWN);
  phase->register_control(in_cset_fast_test_iff, loop, ctrl);

  not_cset_ctrl = new IfTrueNode(in_cset_fast_test_iff);
  phase->register_control(not_cset_ctrl, loop, in_cset_fast_test_iff);

  ctrl = new IfFalseNode(in_cset_fast_test_iff);
  phase->register_control(ctrl, loop, in_cset_fast_test_iff);
}

void ShenandoahWriteBarrierNode::call_wb_stub(Node*& ctrl, Node*& val, Node*& result_mem,
                                              Node* raw_mem, Node* wb_mem,
                                              int alias,
                                              PhaseIdealLoop* phase) {
  IdealLoopTree*loop = phase->get_loop(ctrl);
  const TypePtr* obj_type = phase->igvn().type(val)->is_oopptr()->cast_to_nonconst();

  // The slow path stub consumes and produces raw memory in addition
  // to the existing memory edges
  Node* base = find_bottom_mem(ctrl, phase);

  MergeMemNode* mm = MergeMemNode::make(base);
  mm->set_memory_at(alias, wb_mem);
  mm->set_memory_at(Compile::AliasIdxRaw, raw_mem);
  phase->register_new_node(mm, ctrl);

  Node* call = new CallLeafNode(ShenandoahBarrierSetC2::shenandoah_write_barrier_Type(), CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_JRT), "shenandoah_write_barrier", TypeRawPtr::BOTTOM);
  call->init_req(TypeFunc::Control, ctrl);
  call->init_req(TypeFunc::I_O, phase->C->top());
  call->init_req(TypeFunc::Memory, mm);
  call->init_req(TypeFunc::FramePtr, phase->C->top());
  call->init_req(TypeFunc::ReturnAdr, phase->C->top());
  call->init_req(TypeFunc::Parms, val);
  phase->register_control(call, loop, ctrl);
  ctrl = new ProjNode(call, TypeFunc::Control);
  phase->register_control(ctrl, loop, call);
  result_mem = new ProjNode(call, TypeFunc::Memory);
  phase->register_new_node(result_mem, call);
  val = new ProjNode(call, TypeFunc::Parms);
  phase->register_new_node(val, call);
  val = new CheckCastPPNode(ctrl, val, obj_type);
  phase->register_new_node(val, ctrl);
}

void ShenandoahWriteBarrierNode::fix_ctrl(Node* barrier, Node* region, const MemoryGraphFixer& fixer, Unique_Node_List& uses, Unique_Node_List& uses_to_ignore, uint last, PhaseIdealLoop* phase) {
  Node* ctrl = phase->get_ctrl(barrier);
  Node* init_raw_mem = fixer.find_mem(ctrl, barrier);

  // Update the control of all nodes that should be after the
  // barrier control flow
  uses.clear();
  // Every node that is control dependent on the barrier's input
  // control will be after the expanded barrier. The raw memory (if
  // its memory is control dependent on the barrier's input control)
  // must stay above the barrier.
  uses_to_ignore.clear();
  if (phase->has_ctrl(init_raw_mem) && phase->get_ctrl(init_raw_mem) == ctrl && !init_raw_mem->is_Phi()) {
    uses_to_ignore.push(init_raw_mem);
  }
  for (uint next = 0; next < uses_to_ignore.size(); next++) {
    Node *n = uses_to_ignore.at(next);
    for (uint i = 0; i < n->req(); i++) {
      Node* in = n->in(i);
      if (in != NULL && phase->has_ctrl(in) && phase->get_ctrl(in) == ctrl) {
        uses_to_ignore.push(in);
      }
    }
  }
  for (DUIterator_Fast imax, i = ctrl->fast_outs(imax); i < imax; i++) {
    Node* u = ctrl->fast_out(i);
    if (u->_idx < last &&
        u != barrier &&
        !uses_to_ignore.member(u) &&
        (u->in(0) != ctrl || (!u->is_Region() && !u->is_Phi())) &&
        (ctrl->Opcode() != Op_CatchProj || u->Opcode() != Op_CreateEx)) {
      Node* old_c = phase->ctrl_or_self(u);
      Node* c = old_c;
      if (c != ctrl ||
          is_dominator_same_ctrl(old_c, barrier, u, phase) ||
          ShenandoahBarrierSetC2::is_shenandoah_state_load(u)) {
        phase->igvn().rehash_node_delayed(u);
        int nb = u->replace_edge(ctrl, region);
        if (u->is_CFG()) {
          if (phase->idom(u) == ctrl) {
            phase->set_idom(u, region, phase->dom_depth(region));
          }
        } else if (phase->get_ctrl(u) == ctrl) {
          assert(u != init_raw_mem, "should leave input raw mem above the barrier");
          uses.push(u);
        }
        assert(nb == 1, "more than 1 ctrl input?");
        --i, imax -= nb;
      }
    }
  }
}

void ShenandoahWriteBarrierNode::pin_and_expand(PhaseIdealLoop* phase) {
  Node_List enqueue_barriers;
  if (ShenandoahStoreValEnqueueBarrier) {
    Unique_Node_List wq;
    wq.push(phase->C->root());
    for (uint i = 0; i < wq.size(); i++) {
      Node* n = wq.at(i);
      if (n->Opcode() == Op_ShenandoahEnqueueBarrier) {
        enqueue_barriers.push(n);
      }
      for (uint i = 0; i < n->req(); i++) {
        Node* in = n->in(i);
        if (in != NULL) {
          wq.push(in);
        }
      }
    }
  }

  const bool trace = false;

  // Collect raw memory state at CFG points in the entire graph and
  // record it in memory_nodes. Optimize the raw memory graph in the
  // process. Optimizing the memory graph also makes the memory graph
  // simpler.
  GrowableArray<MemoryGraphFixer*> memory_graph_fixers;

  // Let's try to common write barriers again
  optimize_before_expansion(phase, memory_graph_fixers, true);

  Unique_Node_List uses;
  for (int i = 0; i < ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barriers_count(); i++) {
    ShenandoahWriteBarrierNode* wb = ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barrier(i);
    Node* ctrl = phase->get_ctrl(wb);

    Node* val = wb->in(ValueIn);
    if (ctrl->is_Proj() && ctrl->in(0)->is_CallJava()) {
      assert(is_dominator(phase->get_ctrl(val), ctrl->in(0)->in(0), val, ctrl->in(0), phase), "can't move");
      phase->set_ctrl(wb, ctrl->in(0)->in(0));
    } else if (ctrl->is_CallRuntime()) {
      assert(is_dominator(phase->get_ctrl(val), ctrl->in(0), val, ctrl, phase), "can't move");
      phase->set_ctrl(wb, ctrl->in(0));
    }

    assert(wb->Opcode() == Op_ShenandoahWriteBarrier, "only for write barriers");
    // Look for a null check that dominates this barrier and move the
    // barrier right after the null check to enable implicit null
    // checks
    wb->pin_and_expand_move_barrier(phase, memory_graph_fixers, uses);

    wb->pin_and_expand_helper(phase);
  }

  for (uint i = 0; i < enqueue_barriers.size(); i++) {
    Node* barrier = enqueue_barriers.at(i);
    Node* ctrl = phase->get_ctrl(barrier);
    IdealLoopTree* loop = phase->get_loop(ctrl);
    if (loop->_head->is_OuterStripMinedLoop()) {
      // Expanding a barrier here will break loop strip mining
      // verification. Transform the loop so the loop nest doesn't
      // appear as strip mined.
      OuterStripMinedLoopNode* outer = loop->_head->as_OuterStripMinedLoop();
      hide_strip_mined_loop(outer, outer->unique_ctrl_out()->as_CountedLoop(), phase);
    }
  }

  for (int i = ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barriers_count(); i > 0; i--) {
    int cnt = ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barriers_count();
    ShenandoahWriteBarrierNode* wb = ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barrier(i-1);
    Node* ctrl = phase->get_ctrl(wb);
    IdealLoopTree* loop = phase->get_loop(ctrl);
    if (loop->_head->is_OuterStripMinedLoop()) {
      // Expanding a barrier here will break loop strip mining
      // verification. Transform the loop so the loop nest doesn't
      // appear as strip mined.
      OuterStripMinedLoopNode* outer = loop->_head->as_OuterStripMinedLoop();
      hide_strip_mined_loop(outer, outer->unique_ctrl_out()->as_CountedLoop(), phase);
    }
  }

  MemoryGraphFixer fixer(Compile::AliasIdxRaw, true, phase);
  Unique_Node_List uses_to_ignore;
  for (uint i = 0; i < enqueue_barriers.size(); i++) {
    Node* barrier = enqueue_barriers.at(i);
    Node* pre_val = barrier->in(1);

    if (phase->igvn().type(pre_val)->higher_equal(TypePtr::NULL_PTR)) {
      ShouldNotReachHere();
      continue;
    }

    Node* ctrl = phase->get_ctrl(barrier);

    if (ctrl->is_Proj() && ctrl->in(0)->is_CallJava()) {
      assert(is_dominator(phase->get_ctrl(pre_val), ctrl->in(0)->in(0), pre_val, ctrl->in(0), phase), "can't move");
      ctrl = ctrl->in(0)->in(0);
      phase->set_ctrl(barrier, ctrl);
    } else if (ctrl->is_CallRuntime()) {
      assert(is_dominator(phase->get_ctrl(pre_val), ctrl->in(0), pre_val, ctrl, phase), "can't move");
      ctrl = ctrl->in(0);
      phase->set_ctrl(barrier, ctrl);
    }

    Node* init_ctrl = ctrl;
    IdealLoopTree* loop = phase->get_loop(ctrl);
    Node* raw_mem = fixer.find_mem(ctrl, barrier);
    Node* init_raw_mem = raw_mem;
    Node* raw_mem_for_ctrl = fixer.find_mem(ctrl, NULL);
    Node* heap_stable_ctrl = NULL;
    Node* null_ctrl = NULL;
    uint last = phase->C->unique();

    enum { _heap_stable = 1, _heap_unstable, PATH_LIMIT };
    Node* region = new RegionNode(PATH_LIMIT);
    Node* phi = PhiNode::make(region, raw_mem, Type::MEMORY, TypeRawPtr::BOTTOM);

    enum { _fast_path = 1, _slow_path, _null_path, PATH_LIMIT2 };
    Node* region2 = new RegionNode(PATH_LIMIT2);
    Node* phi2 = PhiNode::make(region2, raw_mem, Type::MEMORY, TypeRawPtr::BOTTOM);

    // Stable path.
    test_heap_stable(ctrl, raw_mem, heap_stable_ctrl, phase);
    region->init_req(_heap_stable, heap_stable_ctrl);
    phi->init_req(_heap_stable, raw_mem);

    // Null path
    Node* reg2_ctrl = NULL;
    test_null(ctrl, pre_val, null_ctrl, phase);
    if (null_ctrl != NULL) {
      reg2_ctrl = null_ctrl->in(0);
      region2->init_req(_null_path, null_ctrl);
      phi2->init_req(_null_path, raw_mem);
    } else {
      region2->del_req(_null_path);
      phi2->del_req(_null_path);
    }

    const int index_offset = in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset());
    const int buffer_offset = in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset());
    Node* thread = new ThreadLocalNode();
    phase->register_new_node(thread, ctrl);
    Node* buffer_adr = new AddPNode(phase->C->top(), thread, phase->igvn().MakeConX(buffer_offset));
    phase->register_new_node(buffer_adr, ctrl);
    Node* index_adr = new AddPNode(phase->C->top(), thread, phase->igvn().MakeConX(index_offset));
    phase->register_new_node(index_adr, ctrl);

    BasicType index_bt = TypeX_X->basic_type();
    assert(sizeof(size_t) == type2aelembytes(index_bt), "Loading G1 SATBMarkQueue::_index with wrong size.");
    const TypePtr* adr_type = TypeRawPtr::BOTTOM;
    Node* index = new LoadXNode(ctrl, raw_mem, index_adr, adr_type, TypeX_X, MemNode::unordered);
    phase->register_new_node(index, ctrl);
    Node* index_cmp = new CmpXNode(index, phase->igvn().MakeConX(0));
    phase->register_new_node(index_cmp, ctrl);
    Node* index_test = new BoolNode(index_cmp, BoolTest::ne);
    phase->register_new_node(index_test, ctrl);
    IfNode* queue_full_iff = new IfNode(ctrl, index_test, PROB_LIKELY(0.999), COUNT_UNKNOWN);
    if (reg2_ctrl == NULL) reg2_ctrl = queue_full_iff;
    phase->register_control(queue_full_iff, loop, ctrl);
    Node* not_full = new IfTrueNode(queue_full_iff);
    phase->register_control(not_full, loop, queue_full_iff);
    Node* full = new IfFalseNode(queue_full_iff);
    phase->register_control(full, loop, queue_full_iff);

    ctrl = not_full;

    Node* next_index = new SubXNode(index, phase->igvn().MakeConX(sizeof(intptr_t)));
    phase->register_new_node(next_index, ctrl);

    Node* buffer  = new LoadPNode(ctrl, raw_mem, buffer_adr, adr_type, TypeRawPtr::NOTNULL, MemNode::unordered);
    phase->register_new_node(buffer, ctrl);
    Node *log_addr = new AddPNode(phase->C->top(), buffer, next_index);
    phase->register_new_node(log_addr, ctrl);
    Node* log_store = new StorePNode(ctrl, raw_mem, log_addr, adr_type, pre_val, MemNode::unordered);
    phase->register_new_node(log_store, ctrl);
    // update the index
    Node* index_update = new StoreXNode(ctrl, log_store, index_adr, adr_type, next_index, MemNode::unordered);
    phase->register_new_node(index_update, ctrl);

    // Fast-path case
    region2->init_req(_fast_path, ctrl);
    phi2->init_req(_fast_path, index_update);

    ctrl = full;

    Node* base = find_bottom_mem(ctrl, phase);

    MergeMemNode* mm = MergeMemNode::make(base);
    mm->set_memory_at(Compile::AliasIdxRaw, raw_mem);
    phase->register_new_node(mm, ctrl);

    Node* call = new CallLeafNode(ShenandoahBarrierSetC2::write_ref_field_pre_entry_Type(), CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_ref_field_pre_entry), "shenandoah_wb_pre", TypeRawPtr::BOTTOM);
    call->init_req(TypeFunc::Control, ctrl);
    call->init_req(TypeFunc::I_O, phase->C->top());
    call->init_req(TypeFunc::Memory, mm);
    call->init_req(TypeFunc::FramePtr, phase->C->top());
    call->init_req(TypeFunc::ReturnAdr, phase->C->top());
    call->init_req(TypeFunc::Parms, pre_val);
    call->init_req(TypeFunc::Parms+1, thread);
    phase->register_control(call, loop, ctrl);

    Node* ctrl_proj = new ProjNode(call, TypeFunc::Control);
    phase->register_control(ctrl_proj, loop, call);
    Node* mem_proj = new ProjNode(call, TypeFunc::Memory);
    phase->register_new_node(mem_proj, call);

    // Slow-path case
    region2->init_req(_slow_path, ctrl_proj);
    phi2->init_req(_slow_path, mem_proj);

    phase->register_control(region2, loop, reg2_ctrl);
    phase->register_new_node(phi2, region2);

    region->init_req(_heap_unstable, region2);
    phi->init_req(_heap_unstable, phi2);

    phase->register_control(region, loop, heap_stable_ctrl->in(0));
    phase->register_new_node(phi, region);

    fix_ctrl(barrier, region, fixer, uses, uses_to_ignore, last, phase);
    for(uint next = 0; next < uses.size(); next++ ) {
      Node *n = uses.at(next);
      assert(phase->get_ctrl(n) == init_ctrl, "bad control");
      assert(n != init_raw_mem, "should leave input raw mem above the barrier");
      phase->set_ctrl(n, region);
      follow_barrier_uses(n, init_ctrl, uses, phase);
    }
    fixer.fix_mem(init_ctrl, region, init_raw_mem, raw_mem_for_ctrl, phi, uses);

    phase->igvn().replace_node(barrier, pre_val);
  }

  for (int i = ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barriers_count(); i > 0; i--) {
    int cnt = ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barriers_count();
    ShenandoahWriteBarrierNode* wb = ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barrier(i-1);

    uint last = phase->C->unique();
    Node* ctrl = phase->get_ctrl(wb);
    Node* orig_ctrl = ctrl;

    Node* raw_mem = fixer.find_mem(ctrl, wb);
    Node* init_raw_mem = raw_mem;
    Node* raw_mem_for_ctrl = fixer.find_mem(ctrl, NULL);
    int alias = phase->C->get_alias_index(wb->adr_type());
    Node* wb_mem =  wb->in(Memory);
    Node* init_wb_mem = wb_mem;

    Node* val = wb->in(ValueIn);
    Node* wbproj = wb->find_out_with(Op_ShenandoahWBMemProj);
    IdealLoopTree *loop = phase->get_loop(ctrl);

    assert(val->Opcode() != Op_ShenandoahWriteBarrier, "No chain of write barriers");

    CallStaticJavaNode* unc = wb->pin_and_expand_null_check(phase->igvn());
    Node* unc_ctrl = NULL;
    if (unc != NULL) {
      if (val->in(0) != ctrl) {
        unc = NULL;
      } else {
        unc_ctrl = val->in(0);
      }
    }

    Node* uncasted_val = val;
    if (unc != NULL) {
      uncasted_val = val->in(1);
    }

    Node* heap_stable_ctrl = NULL;
    Node* null_ctrl = NULL;

    assert(val->bottom_type()->make_oopptr(), "need oop");
    assert(val->bottom_type()->make_oopptr()->const_oop() == NULL, "expect non-constant");

    enum { _heap_stable = 1, _heap_unstable, PATH_LIMIT };
    Node* region = new RegionNode(PATH_LIMIT);
    Node* val_phi = new PhiNode(region, uncasted_val->bottom_type()->is_oopptr());
    Node* mem_phi = PhiNode::make(region, wb_mem, Type::MEMORY, phase->C->alias_type(wb->adr_type())->adr_type());
    Node* raw_mem_phi = PhiNode::make(region, raw_mem, Type::MEMORY, TypeRawPtr::BOTTOM);

    enum { _not_cset = 1, _not_equal, _evac_path, _null_path, PATH_LIMIT2 };
    Node* region2 = new RegionNode(PATH_LIMIT2);
    Node* val_phi2 = new PhiNode(region2, uncasted_val->bottom_type()->is_oopptr());
    Node* mem_phi2 = PhiNode::make(region2, wb_mem, Type::MEMORY, phase->C->alias_type(wb->adr_type())->adr_type());
    Node* raw_mem_phi2 = PhiNode::make(region2, raw_mem, Type::MEMORY, TypeRawPtr::BOTTOM);

      // Stable path.
    test_heap_stable(ctrl, raw_mem, heap_stable_ctrl, phase);
    IfNode* heap_stable_iff = heap_stable_ctrl->in(0)->as_If();

    // Heap stable case
    region->init_req(_heap_stable, heap_stable_ctrl);
    val_phi->init_req(_heap_stable, uncasted_val);
    mem_phi->init_req(_heap_stable, wb_mem);
    raw_mem_phi->init_req(_heap_stable, raw_mem);

    Node* reg2_ctrl = NULL;
    // Null case
    test_null(ctrl, val, null_ctrl, phase);
    if (null_ctrl != NULL) {
      reg2_ctrl = null_ctrl->in(0);
      region2->init_req(_null_path, null_ctrl);
      val_phi2->init_req(_null_path, uncasted_val);
      mem_phi2->init_req(_null_path, wb_mem);
      raw_mem_phi2->init_req(_null_path, raw_mem);
    } else {
      region2->del_req(_null_path);
      val_phi2->del_req(_null_path);
      mem_phi2->del_req(_null_path);
      raw_mem_phi2->del_req(_null_path);
    }

    // Test for in-cset.
    // Wires !in_cset(obj) to slot 2 of region and phis
    Node* not_cset_ctrl = NULL;
    in_cset_fast_test(ctrl, not_cset_ctrl, uncasted_val, raw_mem, phase);
    if (not_cset_ctrl != NULL) {
      if (reg2_ctrl == NULL) reg2_ctrl = not_cset_ctrl->in(0);
      region2->init_req(_not_cset, not_cset_ctrl);
      val_phi2->init_req(_not_cset, uncasted_val);
      mem_phi2->init_req(_not_cset, wb_mem);
      raw_mem_phi2->init_req(_not_cset, raw_mem);
    }

    // Resolve object when orig-value is in cset.
    // Make the unconditional resolve for fwdptr, not the read barrier.
    Node* new_val = uncasted_val;
    if (unc_ctrl != NULL) {
      // Clone the null check in this branch to allow implicit null check
      new_val = clone_null_check(ctrl, val, unc_ctrl, phase);
      fix_null_check(unc, unc_ctrl, ctrl->in(0)->as_If()->proj_out(0), uses, phase);

      IfNode* iff = unc_ctrl->in(0)->as_If();
      phase->igvn().replace_input_of(iff, 1, phase->igvn().intcon(1));
    }
    Node* addr = new AddPNode(new_val, uncasted_val, phase->igvn().MakeConX(ShenandoahBrooksPointer::byte_offset()));
    phase->register_new_node(addr, ctrl);
    assert(val->bottom_type()->isa_oopptr(), "what else?");
    const TypePtr* obj_type =  val->bottom_type()->is_oopptr();
    const TypePtr* adr_type = ShenandoahBarrierNode::brooks_pointer_type(obj_type);
    Node* fwd = new LoadPNode(ctrl, wb_mem, addr, adr_type, obj_type, MemNode::unordered);
    phase->register_new_node(fwd, ctrl);

    // Only branch to WB stub if object is not forwarded; otherwise reply with fwd ptr
    Node* cmp = new CmpPNode(fwd, new_val);
    phase->register_new_node(cmp, ctrl);
    Node* bol = new BoolNode(cmp, BoolTest::eq);
    phase->register_new_node(bol, ctrl);

    IfNode* iff = new IfNode(ctrl, bol, PROB_UNLIKELY(0.999), COUNT_UNKNOWN);
    if (reg2_ctrl == NULL) reg2_ctrl = iff;
    phase->register_control(iff, loop, ctrl);
    Node* if_not_eq = new IfFalseNode(iff);
    phase->register_control(if_not_eq, loop, iff);
    Node* if_eq = new IfTrueNode(iff);
    phase->register_control(if_eq, loop, iff);

    // Wire up not-equal-path in slots 3.
    region2->init_req(_not_equal, if_not_eq);
    val_phi2->init_req(_not_equal, fwd);
    mem_phi2->init_req(_not_equal, wb_mem);
    raw_mem_phi2->init_req(_not_equal, raw_mem);

    // Call wb-stub and wire up that path in slots 4
    Node* result_mem = NULL;
    ctrl = if_eq;
    call_wb_stub(ctrl, new_val, result_mem,
                 raw_mem, wb_mem,
                 alias, phase);
    region2->init_req(_evac_path, ctrl);
    val_phi2->init_req(_evac_path, new_val);
    mem_phi2->init_req(_evac_path, result_mem);
    raw_mem_phi2->init_req(_evac_path, result_mem);

    phase->register_control(region2, loop, reg2_ctrl);
    phase->register_new_node(val_phi2, region2);
    phase->register_new_node(mem_phi2, region2);
    phase->register_new_node(raw_mem_phi2, region2);

    region->init_req(_heap_unstable, region2);
    val_phi->init_req(_heap_unstable, val_phi2);
    mem_phi->init_req(_heap_unstable, mem_phi2);
    raw_mem_phi->init_req(_heap_unstable, raw_mem_phi2);

    phase->register_control(region, loop, heap_stable_iff);
    Node* out_val = val_phi;
    phase->register_new_node(val_phi, region);
    phase->register_new_node(mem_phi, region);
    phase->register_new_node(raw_mem_phi, region);

    fix_ctrl(wb, region, fixer, uses, uses_to_ignore, last, phase);

    ctrl = orig_ctrl;

    phase->igvn().replace_input_of(wbproj, ShenandoahWBMemProjNode::WriteBarrier, phase->C->top());
    phase->igvn().replace_node(wbproj, mem_phi);
    if (unc != NULL) {
      for (DUIterator_Fast imax, i = val->fast_outs(imax); i < imax; i++) {
        Node* u = val->fast_out(i);
        Node* c = phase->ctrl_or_self(u);
        if (u != wb && (c != ctrl || is_dominator_same_ctrl(c, wb, u, phase))) {
          phase->igvn().rehash_node_delayed(u);
          int nb = u->replace_edge(val, out_val);
          --i, imax -= nb;
        }
      }
      if (val->outcnt() == 0) {
        phase->igvn()._worklist.push(val);
      }
    }
    phase->igvn().replace_node(wb, out_val);

    follow_barrier_uses(mem_phi, ctrl, uses, phase);
    follow_barrier_uses(out_val, ctrl, uses, phase);

    for(uint next = 0; next < uses.size(); next++ ) {
      Node *n = uses.at(next);
      assert(phase->get_ctrl(n) == ctrl, "bad control");
      assert(n != init_raw_mem, "should leave input raw mem above the barrier");
      phase->set_ctrl(n, region);
      follow_barrier_uses(n, ctrl, uses, phase);
    }

    // The slow path call produces memory: hook the raw memory phi
    // from the expanded write barrier with the rest of the graph
    // which may require adding memory phis at every post dominated
    // region and at enclosing loop heads. Use the memory state
    // collected in memory_nodes to fix the memory graph. Update that
    // memory state as we go.
    fixer.fix_mem(ctrl, region, init_raw_mem, raw_mem_for_ctrl, raw_mem_phi, uses);
    assert(ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barriers_count() == cnt - 1, "not replaced");
  }

  assert(ShenandoahBarrierSetC2::bsc2()->state()->shenandoah_barriers_count() == 0, "all write barrier nodes should have been replaced");
}

void ShenandoahWriteBarrierNode::move_heap_stable_test_out_of_loop(IfNode* iff, PhaseIdealLoop* phase) {
  IdealLoopTree *loop = phase->get_loop(iff);
  Node* loop_head = loop->_head;
  Node* entry_c = loop_head->in(LoopNode::EntryControl);

  Node* bol = iff->in(1);
  Node* cmp = bol->in(1);
  Node* andi = cmp->in(1);
  Node* load = andi->in(1);

  assert(is_gc_state_load(load), "broken");
  if (!phase->is_dominator(load->in(0), entry_c)) {
    Node* mem_ctrl = NULL;
    Node* mem = dom_mem(load->in(MemNode::Memory), loop_head, Compile::AliasIdxRaw, mem_ctrl, phase);
    load = load->clone();
    load->set_req(MemNode::Memory, mem);
    load->set_req(0, entry_c);
    phase->register_new_node(load, entry_c);
    andi = andi->clone();
    andi->set_req(1, load);
    phase->register_new_node(andi, entry_c);
    cmp = cmp->clone();
    cmp->set_req(1, andi);
    phase->register_new_node(cmp, entry_c);
    bol = bol->clone();
    bol->set_req(1, cmp);
    phase->register_new_node(bol, entry_c);

    Node* old_bol =iff->in(1);
    phase->igvn().replace_input_of(iff, 1, bol);
  }
}

bool ShenandoahWriteBarrierNode::identical_backtoback_ifs(Node *n, PhaseIdealLoop* phase) {
  if (!n->is_If() || n->is_CountedLoopEnd()) {
    return false;
  }
  Node* region = n->in(0);

  if (!region->is_Region()) {
    return false;
  }
  Node* dom = phase->idom(region);
  if (!dom->is_If()) {
    return false;
  }

  if (!is_heap_stable_test(n) || !is_heap_stable_test(dom)) {
    return false;
  }

  IfNode* dom_if = dom->as_If();
  Node* proj_true = dom_if->proj_out(1);
  Node* proj_false = dom_if->proj_out(0);

  for (uint i = 1; i < region->req(); i++) {
    if (phase->is_dominator(proj_true, region->in(i))) {
      continue;
    }
    if (phase->is_dominator(proj_false, region->in(i))) {
      continue;
    }
    return false;
  }

  return true;
}

void ShenandoahWriteBarrierNode::merge_back_to_back_tests(Node* n, PhaseIdealLoop* phase) {
  assert(is_heap_stable_test(n), "no other tests");
  if (identical_backtoback_ifs(n, phase)) {
    Node* n_ctrl = n->in(0);
    if (phase->can_split_if(n_ctrl)) {
      IfNode* dom_if = phase->idom(n_ctrl)->as_If();
      if (is_heap_stable_test(n)) {
        Node* gc_state_load = n->in(1)->in(1)->in(1)->in(1);
        assert(is_gc_state_load(gc_state_load), "broken");
        Node* dom_gc_state_load = dom_if->in(1)->in(1)->in(1)->in(1);
        assert(is_gc_state_load(dom_gc_state_load), "broken");
        if (gc_state_load != dom_gc_state_load) {
          phase->igvn().replace_node(gc_state_load, dom_gc_state_load);
        }
      }
      PhiNode* bolphi = PhiNode::make_blank(n_ctrl, n->in(1));
      Node* proj_true = dom_if->proj_out(1);
      Node* proj_false = dom_if->proj_out(0);
      Node* con_true = phase->igvn().makecon(TypeInt::ONE);
      Node* con_false = phase->igvn().makecon(TypeInt::ZERO);

      for (uint i = 1; i < n_ctrl->req(); i++) {
        if (phase->is_dominator(proj_true, n_ctrl->in(i))) {
          bolphi->init_req(i, con_true);
        } else {
          assert(phase->is_dominator(proj_false, n_ctrl->in(i)), "bad if");
          bolphi->init_req(i, con_false);
        }
      }
      phase->register_new_node(bolphi, n_ctrl);
      phase->igvn().replace_input_of(n, 1, bolphi);
      phase->do_split_if(n);
    }
  }
}

IfNode* ShenandoahWriteBarrierNode::find_unswitching_candidate(const IdealLoopTree *loop, PhaseIdealLoop* phase) {
  // Find first invariant test that doesn't exit the loop
  LoopNode *head = loop->_head->as_Loop();
  IfNode* unswitch_iff = NULL;
  Node* n = head->in(LoopNode::LoopBackControl);
  int loop_has_sfpts = -1;
  while (n != head) {
    Node* n_dom = phase->idom(n);
    if (n->is_Region()) {
      if (n_dom->is_If()) {
        IfNode* iff = n_dom->as_If();
        if (iff->in(1)->is_Bool()) {
          BoolNode* bol = iff->in(1)->as_Bool();
          if (bol->in(1)->is_Cmp()) {
            // If condition is invariant and not a loop exit,
            // then found reason to unswitch.
            if (is_heap_stable_test(iff) &&
                (loop_has_sfpts == -1 || loop_has_sfpts == 0)) {
              assert(!loop->is_loop_exit(iff), "both branches should be in the loop");
              if (loop_has_sfpts == -1) {
                for(uint i = 0; i < loop->_body.size(); i++) {
                  Node *m = loop->_body[i];
                  if (m->is_SafePoint() && !m->is_CallLeaf()) {
                    loop_has_sfpts = 1;
                    break;
                  }
                }
                if (loop_has_sfpts == -1) {
                  loop_has_sfpts = 0;
                }
              }
              if (!loop_has_sfpts) {
                unswitch_iff = iff;
              }
            }
          }
        }
      }
    }
    n = n_dom;
  }
  return unswitch_iff;
}


void ShenandoahWriteBarrierNode::optimize_after_expansion(VectorSet &visited, Node_Stack &stack, Node_List &old_new, PhaseIdealLoop* phase) {
  Node_List heap_stable_tests;
  Node_List gc_state_loads;

  stack.push(phase->C->start(), 0);
  do {
    Node* n = stack.node();
    uint i = stack.index();

    if (i < n->outcnt()) {
      Node* u = n->raw_out(i);
      stack.set_index(i+1);
      if (!visited.test_set(u->_idx)) {
        stack.push(u, 0);
      }
    } else {
      stack.pop();
      if (ShenandoahCommonGCStateLoads && is_gc_state_load(n)) {
        gc_state_loads.push(n);
      }
      if (n->is_If() && is_heap_stable_test(n)) {
        heap_stable_tests.push(n);
      }
    }
  } while (stack.size() > 0);

  bool progress;
  do {
    progress = false;
    for (uint i = 0; i < gc_state_loads.size(); i++) {
      Node* n = gc_state_loads.at(i);
      if (n->outcnt() != 0) {
        progress |= try_common_gc_state_load(n, phase);
      }
    }
  } while (progress);

  for (uint i = 0; i < heap_stable_tests.size(); i++) {
    Node* n = heap_stable_tests.at(i);
    assert(is_heap_stable_test(n), "only evacuation test");
    merge_back_to_back_tests(n, phase);
  }

  if (!phase->C->major_progress()) {
    VectorSet seen(Thread::current()->resource_area());
    for (uint i = 0; i < heap_stable_tests.size(); i++) {
      Node* n = heap_stable_tests.at(i);
      IdealLoopTree* loop = phase->get_loop(n);
      if (loop != phase->ltree_root() &&
          loop->_child == NULL &&
          !loop->_irreducible) {
        LoopNode* head = loop->_head->as_Loop();
        if ((!head->is_CountedLoop() || head->as_CountedLoop()->is_main_loop() || head->as_CountedLoop()->is_normal_loop()) &&
            !seen.test_set(head->_idx)) {
          IfNode* iff = find_unswitching_candidate(loop, phase);
          if (iff != NULL) {
            Node* bol = iff->in(1);
            if (head->is_strip_mined()) {
              head->verify_strip_mined(0);
            }
            move_heap_stable_test_out_of_loop(iff, phase);
            if (loop->policy_unswitching(phase)) {
              if (head->is_strip_mined()) {
                OuterStripMinedLoopNode* outer = head->as_CountedLoop()->outer_loop();
                hide_strip_mined_loop(outer, head->as_CountedLoop(), phase);
              }
              phase->do_unswitching(loop, old_new);
            } else {
              // Not proceeding with unswitching. Move load back in
              // the loop.
              phase->igvn().replace_input_of(iff, 1, bol);
            }
          }
        }
      }
    }
  }
}

#ifdef ASSERT
void ShenandoahBarrierNode::verify_raw_mem(RootNode* root) {
  const bool trace = false;
  ResourceMark rm;
  Unique_Node_List nodes;
  Unique_Node_List controls;
  Unique_Node_List memories;

  nodes.push(root);
  for (uint next = 0; next < nodes.size(); next++) {
    Node *n  = nodes.at(next);
    if (ShenandoahBarrierSetC2::is_shenandoah_wb_call(n)) {
      controls.push(n);
      if (trace) { tty->print("XXXXXX verifying"); n->dump(); }
      for (uint next2 = 0; next2 < controls.size(); next2++) {
        Node *m = controls.at(next2);
        for (DUIterator_Fast imax, i = m->fast_outs(imax); i < imax; i++) {
          Node* u = m->fast_out(i);
          if (u->is_CFG() && !u->is_Root() &&
              !(u->Opcode() == Op_CProj && u->in(0)->Opcode() == Op_NeverBranch && u->as_Proj()->_con == 1) &&
              !(u->is_Region() && u->unique_ctrl_out()->Opcode() == Op_Halt)) {
            if (trace) { tty->print("XXXXXX pushing control"); u->dump(); }
            controls.push(u);
          }
        }
      }
      memories.push(n->as_Call()->proj_out(TypeFunc::Memory));
      for (uint next2 = 0; next2 < memories.size(); next2++) {
        Node *m = memories.at(next2);
        assert(m->bottom_type() == Type::MEMORY, "");
        for (DUIterator_Fast imax, i = m->fast_outs(imax); i < imax; i++) {
          Node* u = m->fast_out(i);
          if (u->bottom_type() == Type::MEMORY && (u->is_Mem() || u->is_ClearArray())) {
            if (trace) { tty->print("XXXXXX pushing memory"); u->dump(); }
            memories.push(u);
          } else if (u->is_LoadStore()) {
            if (trace) { tty->print("XXXXXX pushing memory"); u->find_out_with(Op_SCMemProj)->dump(); }
            memories.push(u->find_out_with(Op_SCMemProj));
          } else if (u->is_MergeMem() && u->as_MergeMem()->memory_at(Compile::AliasIdxRaw) == m) {
            if (trace) { tty->print("XXXXXX pushing memory"); u->dump(); }
            memories.push(u);
          } else if (u->is_Phi()) {
            assert(u->bottom_type() == Type::MEMORY, "");
            if (u->adr_type() == TypeRawPtr::BOTTOM || u->adr_type() == TypePtr::BOTTOM) {
              assert(controls.member(u->in(0)), "");
              if (trace) { tty->print("XXXXXX pushing memory"); u->dump(); }
              memories.push(u);
            }
          } else if (u->is_SafePoint() || u->is_MemBar()) {
            for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
              Node* uu = u->fast_out(j);
              if (uu->bottom_type() == Type::MEMORY) {
                if (trace) { tty->print("XXXXXX pushing memory"); uu->dump(); }
                memories.push(uu);
              }
            }
          }
        }
      }
      for (uint next2 = 0; next2 < controls.size(); next2++) {
        Node *m = controls.at(next2);
        if (m->is_Region()) {
          bool all_in = true;
          for (uint i = 1; i < m->req(); i++) {
            if (!controls.member(m->in(i))) {
              all_in = false;
              break;
            }
          }
          if (trace) { tty->print("XXX verifying %s", all_in ? "all in" : ""); m->dump(); }
          bool found_phi = false;
          for (DUIterator_Fast jmax, j = m->fast_outs(jmax); j < jmax && !found_phi; j++) {
            Node* u = m->fast_out(j);
            if (u->is_Phi() && memories.member(u)) {
              found_phi = true;
              for (uint i = 1; i < u->req() && found_phi; i++) {
                Node* k = u->in(i);
                if (memories.member(k) != controls.member(m->in(i))) {
                  found_phi = false;
                }
              }
            }
          }
          assert(found_phi || all_in, "");
        }
      }
      controls.clear();
      memories.clear();
    }
    for( uint i = 0; i < n->len(); ++i ) {
      Node *m = n->in(i);
      if (m != NULL) {
        nodes.push(m);
      }
    }
  }
}
#endif

const Type* ShenandoahEnqueueBarrierNode::bottom_type() const {
  if (in(1) == NULL || in(1)->is_top()) {
    return Type::TOP;
  }
  const Type* t = in(1)->bottom_type();
  if (t == TypePtr::NULL_PTR) {
    return t;
  }
  return t->is_oopptr()->cast_to_nonconst();
}

const Type* ShenandoahEnqueueBarrierNode::Value(PhaseGVN* phase) const {
  if (in(1) == NULL) {
    return Type::TOP;
  }
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) {
    return Type::TOP;
  }
  if (t == TypePtr::NULL_PTR) {
    return t;
  }
  return t->is_oopptr()->cast_to_nonconst();
}

int ShenandoahEnqueueBarrierNode::needed(Node* n) {
  if (n == NULL ||
      n->is_Allocate() ||
      n->bottom_type() == TypePtr::NULL_PTR ||
      (n->bottom_type()->make_oopptr() != NULL && n->bottom_type()->make_oopptr()->const_oop() != NULL)) {
    return NotNeeded;
  }
  if (n->is_Phi() ||
      n->is_CMove()) {
    return MaybeNeeded;
  }
  return Needed;
}

Node* ShenandoahEnqueueBarrierNode::next(Node* n) {
  for (;;) {
    if (n == NULL) {
      return n;
    } else if (n->bottom_type() == TypePtr::NULL_PTR) {
      return n;
    } else if (n->bottom_type()->make_oopptr() != NULL && n->bottom_type()->make_oopptr()->const_oop() != NULL) {
      return n;
    } else if (n->is_ConstraintCast() ||
               n->Opcode() == Op_DecodeN ||
               n->Opcode() == Op_EncodeP) {
      n = n->in(1);
    } else if (n->is_Proj()) {
      n = n->in(0);
    } else {
      return n;
    }
  }
  ShouldNotReachHere();
  return NULL;
}

Node* ShenandoahEnqueueBarrierNode::Identity(PhaseGVN* phase) {
  PhaseIterGVN* igvn = phase->is_IterGVN();

  Node* n = next(in(1));

  int cont = needed(n);

  if (cont == NotNeeded) {
    return in(1);
  } else if (cont == MaybeNeeded) {
    if (igvn == NULL) {
      phase->record_for_igvn(this);
      return this;
    } else {
      ResourceMark rm;
      Unique_Node_List wq;
      uint wq_i = 0;

      for (;;) {
        if (n->is_Phi()) {
          for (uint i = 1; i < n->req(); i++) {
            Node* m = n->in(i);
            if (m != NULL) {
              wq.push(m);
            }
          }
        } else {
          assert(n->is_CMove(), "nothing else here");
          Node* m = n->in(CMoveNode::IfFalse);
          wq.push(m);
          m = n->in(CMoveNode::IfTrue);
          wq.push(m);
        }
        Node* orig_n = NULL;
        do {
          if (wq_i >= wq.size()) {
            return in(1);
          }
          n = wq.at(wq_i);
          wq_i++;
          orig_n = n;
          n = next(n);
          cont = needed(n);
          if (cont == Needed) {
            return this;
          }
        } while (cont != MaybeNeeded || (orig_n != n && wq.member(n)));
      }
    }
  }

  return this;
}

#ifdef ASSERT
static bool has_never_branch(Node* root) {
  for (uint i = 1; i < root->req(); i++) {
    Node* in = root->in(i);
    if (in != NULL && in->Opcode() == Op_Halt && in->in(0)->is_Proj() && in->in(0)->in(0)->Opcode() == Op_NeverBranch) {
      return true;
    }
  }
  return false;
}
#endif

void MemoryGraphFixer::collect_memory_nodes() {
  Node_Stack stack(0);
  VectorSet visited(Thread::current()->resource_area());
  Node_List regions;

  // Walk the raw memory graph and create a mapping from CFG node to
  // memory node. Exclude phis for now.
  stack.push(_phase->C->root(), 1);
  do {
    Node* n = stack.node();
    int opc = n->Opcode();
    uint i = stack.index();
    if (i < n->req()) {
      Node* mem = NULL;
      if (opc == Op_Root) {
        Node* in = n->in(i);
        int in_opc = in->Opcode();
        if (in_opc == Op_Return || in_opc == Op_Rethrow) {
          mem = in->in(TypeFunc::Memory);
        } else if (in_opc == Op_Halt) {
          if (!in->in(0)->is_Region()) {
            Node* proj = in->in(0);
            assert(proj->is_Proj(), "");
            Node* in = proj->in(0);
            assert(in->is_CallStaticJava() || in->Opcode() == Op_NeverBranch || in->Opcode() == Op_Catch || proj->is_IfProj(), "");
            if (in->is_CallStaticJava()) {
              mem = in->in(TypeFunc::Memory);
            } else if (in->Opcode() == Op_Catch) {
              Node* call = in->in(0)->in(0);
              assert(call->is_Call(), "");
              mem = call->in(TypeFunc::Memory);
            }
          }
        } else {
#ifdef ASSERT
          n->dump();
          in->dump();
#endif
          ShouldNotReachHere();
        }
      } else {
        assert(n->is_Phi() && n->bottom_type() == Type::MEMORY, "");
        assert(n->adr_type() == TypePtr::BOTTOM || _phase->C->get_alias_index(n->adr_type()) == _alias, "");
        mem = n->in(i);
      }
      i++;
      stack.set_index(i);
      if (mem == NULL) {
        continue;
      }
      for (;;) {
        if (visited.test_set(mem->_idx) || mem->is_Start()) {
          break;
        }
        if (mem->is_Phi()) {
          stack.push(mem, 2);
          mem = mem->in(1);
        } else if (mem->is_Proj()) {
          stack.push(mem, mem->req());
          mem = mem->in(0);
        } else if (mem->is_SafePoint() || mem->is_MemBar()) {
          mem = mem->in(TypeFunc::Memory);
        } else if (mem->is_MergeMem()) {
          MergeMemNode* mm = mem->as_MergeMem();
          mem = mm->memory_at(_alias);
        } else if (mem->is_Store() || mem->is_LoadStore() || mem->is_ClearArray()) {
          assert(_alias == Compile::AliasIdxRaw, "");
          stack.push(mem, mem->req());
          mem = mem->in(MemNode::Memory);
        } else if (mem->Opcode() == Op_ShenandoahWriteBarrier) {
          assert(_alias != Compile::AliasIdxRaw, "");
          mem = mem->in(ShenandoahBarrierNode::Memory);
        } else if (mem->Opcode() == Op_ShenandoahWBMemProj) {
          stack.push(mem, mem->req());
          mem = mem->in(ShenandoahWBMemProjNode::WriteBarrier);
        } else {
#ifdef ASSERT
          mem->dump();
#endif
          ShouldNotReachHere();
        }
      }
    } else {
      if (n->is_Phi()) {
        // Nothing
      } else if (!n->is_Root()) {
        Node* c = get_ctrl(n);
        _memory_nodes.map(c->_idx, n);
      }
      stack.pop();
    }
  } while(stack.is_nonempty());

  // Iterate over CFG nodes in rpo and propagate memory state to
  // compute memory state at regions, creating new phis if needed.
  Node_List rpo_list;
  visited.Clear();
  _phase->rpo(_phase->C->root(), stack, visited, rpo_list);
  Node* root = rpo_list.pop();
  assert(root == _phase->C->root(), "");

  const bool trace = false;
#ifdef ASSERT
  if (trace) {
    for (int i = rpo_list.size() - 1; i >= 0; i--) {
      Node* c = rpo_list.at(i);
      if (_memory_nodes[c->_idx] != NULL) {
        tty->print("X %d", c->_idx);  _memory_nodes[c->_idx]->dump();
      }
    }
  }
#endif
  uint last = _phase->C->unique();

#ifdef ASSERT
  uint8_t max_depth = 0;
  for (LoopTreeIterator iter(_phase->ltree_root()); !iter.done(); iter.next()) {
    IdealLoopTree* lpt = iter.current();
    max_depth = MAX2(max_depth, lpt->_nest);
  }
#endif

  bool progress = true;
  int iteration = 0;
  Node_List dead_phis;
  while (progress) {
    progress = false;
    iteration++;
    assert(iteration <= 2+max_depth || _phase->C->has_irreducible_loop(), "");
    if (trace) { tty->print_cr("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"); }
    IdealLoopTree* last_updated_ilt = NULL;
    for (int i = rpo_list.size() - 1; i >= 0; i--) {
      Node* c = rpo_list.at(i);

      Node* prev_mem = _memory_nodes[c->_idx];
      if (c->is_Region() && (_include_lsm || !c->is_OuterStripMinedLoop())) {
        Node* prev_region = regions[c->_idx];
        Node* unique = NULL;
        for (uint j = 1; j < c->req() && unique != NodeSentinel; j++) {
          Node* m = _memory_nodes[c->in(j)->_idx];
          assert(m != NULL || (c->is_Loop() && j == LoopNode::LoopBackControl && iteration == 1) || _phase->C->has_irreducible_loop() || has_never_branch(_phase->C->root()), "expect memory state");
          if (m != NULL) {
            if (m == prev_region && ((c->is_Loop() && j == LoopNode::LoopBackControl) || (prev_region->is_Phi() && prev_region->in(0) == c))) {
              assert(c->is_Loop() && j == LoopNode::LoopBackControl || _phase->C->has_irreducible_loop(), "");
              // continue
            } else if (unique == NULL) {
              unique = m;
            } else if (m == unique) {
              // continue
            } else {
              unique = NodeSentinel;
            }
          }
        }
        assert(unique != NULL, "empty phi???");
        if (unique != NodeSentinel) {
          if (prev_region != NULL && prev_region->is_Phi() && prev_region->in(0) == c) {
            dead_phis.push(prev_region);
          }
          regions.map(c->_idx, unique);
        } else {
          Node* phi = NULL;
          if (prev_region != NULL && prev_region->is_Phi() && prev_region->in(0) == c && prev_region->_idx >= last) {
            phi = prev_region;
            for (uint k = 1; k < c->req(); k++) {
              Node* m = _memory_nodes[c->in(k)->_idx];
              assert(m != NULL, "expect memory state");
              phi->set_req(k, m);
            }
          } else {
            for (DUIterator_Fast jmax, j = c->fast_outs(jmax); j < jmax && phi == NULL; j++) {
              Node* u = c->fast_out(j);
              if (u->is_Phi() && u->bottom_type() == Type::MEMORY &&
                  (u->adr_type() == TypePtr::BOTTOM || _phase->C->get_alias_index(u->adr_type()) == _alias)) {
                phi = u;
                for (uint k = 1; k < c->req() && phi != NULL; k++) {
                  Node* m = _memory_nodes[c->in(k)->_idx];
                  assert(m != NULL, "expect memory state");
                  if (u->in(k) != m) {
                    phi = NULL;
                  }
                }
              }
            }
            if (phi == NULL) {
              phi = new PhiNode(c, Type::MEMORY, _phase->C->get_adr_type(_alias));
              for (uint k = 1; k < c->req(); k++) {
                Node* m = _memory_nodes[c->in(k)->_idx];
                assert(m != NULL, "expect memory state");
                phi->init_req(k, m);
              }
            }
          }
          assert(phi != NULL, "");
          regions.map(c->_idx, phi);
        }
        Node* current_region = regions[c->_idx];
        if (current_region != prev_region) {
          progress = true;
          if (prev_region == prev_mem) {
            _memory_nodes.map(c->_idx, current_region);
          }
        }
      } else if (prev_mem == NULL || prev_mem->is_Phi() || ctrl_or_self(prev_mem) != c) {
        Node* m = _memory_nodes[_phase->idom(c)->_idx];
        assert(m != NULL, "expect memory state");
        if (m != prev_mem) {
          _memory_nodes.map(c->_idx, m);
          progress = true;
        }
      }
#ifdef ASSERT
      if (trace) { tty->print("X %d", c->_idx);  _memory_nodes[c->_idx]->dump(); }
#endif
    }
  }

  // Replace existing phi with computed memory state for that region
  // if different (could be a new phi or a dominating memory node if
  // that phi was found to be useless).
  while (dead_phis.size() > 0) {
    Node* n = dead_phis.pop();
    n->replace_by(_phase->C->top());
    n->destruct();
  }
  for (int i = rpo_list.size() - 1; i >= 0; i--) {
    Node* c = rpo_list.at(i);
    if (c->is_Region() && (_include_lsm || !c->is_OuterStripMinedLoop())) {
      Node* n = regions[c->_idx];
      if (n->is_Phi() && n->_idx >= last && n->in(0) == c) {
        _phase->register_new_node(n, c);
      }
    }
  }
  for (int i = rpo_list.size() - 1; i >= 0; i--) {
    Node* c = rpo_list.at(i);
    if (c->is_Region() && (_include_lsm || !c->is_OuterStripMinedLoop())) {
      Node* n = regions[c->_idx];
      for (DUIterator_Fast imax, i = c->fast_outs(imax); i < imax; i++) {
        Node* u = c->fast_out(i);
        if (u->is_Phi() && u->bottom_type() == Type::MEMORY &&
            u != n) {
          if (u->adr_type() == TypePtr::BOTTOM) {
            fix_memory_uses(u, n, n, c);
          } else if (_phase->C->get_alias_index(u->adr_type()) == _alias) {
            _phase->lazy_replace(u, n);
            --i; --imax;
          }
        }
      }
    }
  }
}

Node* MemoryGraphFixer::get_ctrl(Node* n) const {
  Node* c = _phase->get_ctrl(n);
  if (n->is_Proj() && n->in(0) != NULL && n->in(0)->is_Call()) {
    assert(c == n->in(0), "");
    CallNode* call = c->as_Call();
    CallProjections projs;
    call->extract_projections(&projs, true, false);
    if (projs.catchall_memproj != NULL) {
      if (projs.fallthrough_memproj == n) {
        c = projs.fallthrough_catchproj;
      } else {
        assert(projs.catchall_memproj == n, "");
        c = projs.catchall_catchproj;
      }
    }
  }
  return c;
}

Node* MemoryGraphFixer::ctrl_or_self(Node* n) const {
  if (_phase->has_ctrl(n))
    return get_ctrl(n);
  else {
    assert (n->is_CFG(), "must be a CFG node");
    return n;
  }
}

bool MemoryGraphFixer::mem_is_valid(Node* m, Node* c) const {
  return m != NULL && get_ctrl(m) == c;
}

Node* MemoryGraphFixer::find_mem(Node* ctrl, Node* n) const {
  assert(n == NULL || _phase->ctrl_or_self(n) == ctrl, "");
  Node* mem = _memory_nodes[ctrl->_idx];
  Node* c = ctrl;
  while (!mem_is_valid(mem, c) &&
         (!c->is_CatchProj() || mem == NULL || c->in(0)->in(0)->in(0) != get_ctrl(mem))) {
    c = _phase->idom(c);
    mem = _memory_nodes[c->_idx];
  }
  if (n != NULL && mem_is_valid(mem, c)) {
    while (!ShenandoahWriteBarrierNode::is_dominator_same_ctrl(c, mem, n, _phase) && _phase->ctrl_or_self(mem) == ctrl) {
      mem = next_mem(mem, _alias);
    }
    if (mem->is_MergeMem()) {
      mem = mem->as_MergeMem()->memory_at(_alias);
    }
    if (!mem_is_valid(mem, c)) {
      do {
        c = _phase->idom(c);
        mem = _memory_nodes[c->_idx];
      } while (!mem_is_valid(mem, c) &&
               (!c->is_CatchProj() || mem == NULL || c->in(0)->in(0)->in(0) != get_ctrl(mem)));
    }
  }
  assert(mem->bottom_type() == Type::MEMORY, "");
  return mem;
}

bool MemoryGraphFixer::has_mem_phi(Node* region) const {
  for (DUIterator_Fast imax, i = region->fast_outs(imax); i < imax; i++) {
    Node* use = region->fast_out(i);
    if (use->is_Phi() && use->bottom_type() == Type::MEMORY &&
        (_phase->C->get_alias_index(use->adr_type()) == _alias)) {
      return true;
    }
  }
  return false;
}

void MemoryGraphFixer::fix_mem(Node* ctrl, Node* new_ctrl, Node* mem, Node* mem_for_ctrl, Node* new_mem, Unique_Node_List& uses) {
  assert(_phase->ctrl_or_self(new_mem) == new_ctrl, "");
  const bool trace = false;
  DEBUG_ONLY(if (trace) { tty->print("ZZZ control is"); ctrl->dump(); });
  DEBUG_ONLY(if (trace) { tty->print("ZZZ mem is"); mem->dump(); });
  GrowableArray<Node*> phis;
  if (mem_for_ctrl != mem) {
    Node* old = mem_for_ctrl;
    Node* prev = NULL;
    while (old != mem) {
      prev = old;
      if (old->is_Store() || old->is_ClearArray() || old->is_LoadStore()) {
        assert(_alias == Compile::AliasIdxRaw, "");
        old = old->in(MemNode::Memory);
      } else if (old->Opcode() == Op_SCMemProj) {
        assert(_alias == Compile::AliasIdxRaw, "");
        old = old->in(0);
      } else if (old->Opcode() == Op_ShenandoahWBMemProj) {
        assert(_alias != Compile::AliasIdxRaw, "");
        old = old->in(ShenandoahWBMemProjNode::WriteBarrier);
      } else if (old->Opcode() == Op_ShenandoahWriteBarrier) {
        assert(_alias != Compile::AliasIdxRaw, "");
        old = old->in(ShenandoahBarrierNode::Memory);
      } else {
        ShouldNotReachHere();
      }
    }
    assert(prev != NULL, "");
    if (new_ctrl != ctrl) {
      _memory_nodes.map(ctrl->_idx, mem);
      _memory_nodes.map(new_ctrl->_idx, mem_for_ctrl);
    }
    uint input = prev->Opcode() == Op_ShenandoahWriteBarrier ? (uint)ShenandoahBarrierNode::Memory : (uint)MemNode::Memory;
    _phase->igvn().replace_input_of(prev, input, new_mem);
  } else {
    uses.clear();
    _memory_nodes.map(new_ctrl->_idx, new_mem);
    uses.push(new_ctrl);
    for(uint next = 0; next < uses.size(); next++ ) {
      Node *n = uses.at(next);
      assert(n->is_CFG(), "");
      DEBUG_ONLY(if (trace) { tty->print("ZZZ ctrl"); n->dump(); });
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        Node* u = n->fast_out(i);
        if (!u->is_Root() && u->is_CFG() && u != n) {
          Node* m = _memory_nodes[u->_idx];
          if (u->is_Region() && (!u->is_OuterStripMinedLoop() || _include_lsm) &&
              !has_mem_phi(u) &&
              u->unique_ctrl_out()->Opcode() != Op_Halt) {
            DEBUG_ONLY(if (trace) { tty->print("ZZZ region"); u->dump(); });
            DEBUG_ONLY(if (trace && m != NULL) { tty->print("ZZZ mem"); m->dump(); });

            if (!mem_is_valid(m, u) || !m->is_Phi()) {
              bool push = true;
              bool create_phi = true;
              if (_phase->is_dominator(new_ctrl, u)) {
                create_phi = false;
              } else if (!_phase->C->has_irreducible_loop()) {
                IdealLoopTree* loop = _phase->get_loop(ctrl);
                bool do_check = true;
                IdealLoopTree* l = loop;
                create_phi = false;
                while (l != _phase->ltree_root()) {
                  if (_phase->is_dominator(l->_head, u) && _phase->is_dominator(_phase->idom(u), l->_head)) {
                    create_phi = true;
                    do_check = false;
                    break;
                  }
                  l = l->_parent;
                }

                if (do_check) {
                  assert(!create_phi, "");
                  IdealLoopTree* u_loop = _phase->get_loop(u);
                  if (u_loop != _phase->ltree_root() && u_loop->is_member(loop)) {
                    Node* c = ctrl;
                    while (!_phase->is_dominator(c, u_loop->tail())) {
                      c = _phase->idom(c);
                    }
                    if (!_phase->is_dominator(c, u)) {
                      do_check = false;
                    }
                  }
                }

                if (do_check && _phase->is_dominator(_phase->idom(u), new_ctrl)) {
                  create_phi = true;
                }
              }
              if (create_phi) {
                Node* phi = new PhiNode(u, Type::MEMORY, _phase->C->get_adr_type(_alias));
                _phase->register_new_node(phi, u);
                phis.push(phi);
                DEBUG_ONLY(if (trace) { tty->print("ZZZ new phi"); phi->dump(); });
                if (!mem_is_valid(m, u)) {
                  DEBUG_ONLY(if (trace) { tty->print("ZZZ setting mem"); phi->dump(); });
                  _memory_nodes.map(u->_idx, phi);
                } else {
                  DEBUG_ONLY(if (trace) { tty->print("ZZZ NOT setting mem"); m->dump(); });
                  for (;;) {
                    assert(m->is_Mem() || m->is_LoadStore() || m->is_Proj() || m->Opcode() == Op_ShenandoahWriteBarrier || m->Opcode() == Op_ShenandoahWBMemProj, "");
                    Node* next = NULL;
                    if (m->is_Proj()) {
                      next = m->in(0);
                    } else if (m->Opcode() == Op_ShenandoahWBMemProj) {
                      next = m->in(ShenandoahWBMemProjNode::WriteBarrier);
                    } else if (m->is_Mem() || m->is_LoadStore()) {
                      assert(_alias == Compile::AliasIdxRaw, "");
                      next = m->in(MemNode::Memory);
                    } else {
                      assert(_alias != Compile::AliasIdxRaw, "");
                      assert (m->Opcode() == Op_ShenandoahWriteBarrier, "");
                      next = m->in(ShenandoahBarrierNode::Memory);
                    }
                    if (_phase->get_ctrl(next) != u) {
                      break;
                    }
                    if (next->is_MergeMem()) {
                      assert(_phase->get_ctrl(next->as_MergeMem()->memory_at(_alias)) != u, "");
                      break;
                    }
                    if (next->is_Phi()) {
                      assert(next->adr_type() == TypePtr::BOTTOM && next->in(0) == u, "");
                      break;
                    }
                    m = next;
                  }

                  DEBUG_ONLY(if (trace) { tty->print("ZZZ setting to phi"); m->dump(); });
                  assert(m->is_Mem() || m->is_LoadStore() || m->Opcode() == Op_ShenandoahWriteBarrier, "");
                  uint input = (m->is_Mem() || m->is_LoadStore()) ? (uint)MemNode::Memory : (uint)ShenandoahBarrierNode::Memory;
                  _phase->igvn().replace_input_of(m, input, phi);
                  push = false;
                }
              } else {
                DEBUG_ONLY(if (trace) { tty->print("ZZZ skipping region"); u->dump(); });
              }
              if (push) {
                uses.push(u);
              }
            }
          } else if (!mem_is_valid(m, u) &&
                     !(u->Opcode() == Op_CProj && u->in(0)->Opcode() == Op_NeverBranch && u->as_Proj()->_con == 1)) {
            uses.push(u);
          }
        }
      }
    }
    for (int i = 0; i < phis.length(); i++) {
      Node* n = phis.at(i);
      Node* r = n->in(0);
      DEBUG_ONLY(if (trace) { tty->print("ZZZ fixing new phi"); n->dump(); });
      for (uint j = 1; j < n->req(); j++) {
        Node* m = find_mem(r->in(j), NULL);
        _phase->igvn().replace_input_of(n, j, m);
        DEBUG_ONLY(if (trace) { tty->print("ZZZ fixing new phi: %d", j); m->dump(); });
      }
    }
  }
  uint last = _phase->C->unique();
  MergeMemNode* mm = NULL;
  int alias = _alias;
  DEBUG_ONLY(if (trace) { tty->print("ZZZ raw mem is"); mem->dump(); });
  for (DUIterator i = mem->outs(); mem->has_out(i); i++) {
    Node* u = mem->out(i);
    if (u->_idx < last) {
      if (u->is_Mem()) {
        if (_phase->C->get_alias_index(u->adr_type()) == alias) {
          Node* m = find_mem(_phase->get_ctrl(u), u);
          if (m != mem) {
            DEBUG_ONLY(if (trace) { tty->print("ZZZ setting memory of use"); u->dump(); });
            _phase->igvn().replace_input_of(u, MemNode::Memory, m);
            --i;
          }
        }
      } else if (u->is_MergeMem()) {
        MergeMemNode* u_mm = u->as_MergeMem();
        if (u_mm->memory_at(alias) == mem) {
          MergeMemNode* newmm = NULL;
          for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
            Node* uu = u->fast_out(j);
            assert(!uu->is_MergeMem(), "chain of MergeMems?");
            if (uu->is_Phi()) {
              assert(uu->adr_type() == TypePtr::BOTTOM, "");
              Node* region = uu->in(0);
              int nb = 0;
              for (uint k = 1; k < uu->req(); k++) {
                if (uu->in(k) == u) {
                  Node* m = find_mem(region->in(k), NULL);
                  if (m != mem) {
                    DEBUG_ONLY(if (trace) { tty->print("ZZZ setting memory of phi %d", k); uu->dump(); });
                    newmm = clone_merge_mem(u, mem, m, _phase->ctrl_or_self(m), i);
                    if (newmm != u) {
                      _phase->igvn().replace_input_of(uu, k, newmm);
                      nb++;
                      --jmax;
                    }
                  }
                }
              }
              if (nb > 0) {
                --j;
              }
            } else {
              Node* m = find_mem(_phase->ctrl_or_self(uu), uu);
              if (m != mem) {
                DEBUG_ONLY(if (trace) { tty->print("ZZZ setting memory of use"); uu->dump(); });
                newmm = clone_merge_mem(u, mem, m, _phase->ctrl_or_self(m), i);
                if (newmm != u) {
                  _phase->igvn().replace_input_of(uu, uu->find_edge(u), newmm);
                  --j, --jmax;
                }
              }
            }
          }
        }
      } else if (u->is_Phi()) {
        assert(u->bottom_type() == Type::MEMORY, "what else?");
        if (_phase->C->get_alias_index(u->adr_type()) == alias || u->adr_type() == TypePtr::BOTTOM) {
          Node* region = u->in(0);
          bool replaced = false;
          for (uint j = 1; j < u->req(); j++) {
            if (u->in(j) == mem) {
              Node* m = find_mem(region->in(j), NULL);
              Node* nnew = m;
              if (m != mem) {
                if (u->adr_type() == TypePtr::BOTTOM) {
                  mm = allocate_merge_mem(mem, m, _phase->ctrl_or_self(m));
                  nnew = mm;
                }
                DEBUG_ONLY(if (trace) { tty->print("ZZZ setting memory of phi %d", j); u->dump(); });
                _phase->igvn().replace_input_of(u, j, nnew);
                replaced = true;
              }
            }
          }
          if (replaced) {
            --i;
          }
        }
      } else if ((u->adr_type() == TypePtr::BOTTOM && u->Opcode() != Op_StrInflatedCopy) ||
                 u->adr_type() == NULL) {
        assert(u->adr_type() != NULL ||
               u->Opcode() == Op_Rethrow ||
               u->Opcode() == Op_Return ||
               u->Opcode() == Op_SafePoint ||
               (u->is_CallStaticJava() && u->as_CallStaticJava()->uncommon_trap_request() != 0) ||
               (u->is_CallStaticJava() && u->as_CallStaticJava()->_entry_point == OptoRuntime::rethrow_stub()) ||
               u->Opcode() == Op_CallLeaf, "");
        Node* m = find_mem(_phase->ctrl_or_self(u), u);
        if (m != mem) {
          mm = allocate_merge_mem(mem, m, _phase->get_ctrl(m));
          _phase->igvn().replace_input_of(u, u->find_edge(mem), mm);
          --i;
        }
      } else if (_phase->C->get_alias_index(u->adr_type()) == alias) {
        Node* m = find_mem(_phase->ctrl_or_self(u), u);
        if (m != mem) {
          DEBUG_ONLY(if (trace) { tty->print("ZZZ setting memory of use"); u->dump(); });
          _phase->igvn().replace_input_of(u, u->find_edge(mem), m);
          --i;
        }
      } else if (u->adr_type() != TypePtr::BOTTOM &&
                 _memory_nodes[_phase->ctrl_or_self(u)->_idx] == u) {
        Node* m = find_mem(_phase->ctrl_or_self(u), u);
        assert(m != mem, "");
        // u is on the wrong slice...
        assert(u->is_ClearArray(), "");
        DEBUG_ONLY(if (trace) { tty->print("ZZZ setting memory of use"); u->dump(); });
        _phase->igvn().replace_input_of(u, u->find_edge(mem), m);
        --i;
      }
    }
  }
#ifdef ASSERT
  assert(new_mem->outcnt() > 0, "");
  for (int i = 0; i < phis.length(); i++) {
    Node* n = phis.at(i);
    assert(n->outcnt() > 0, "new phi must have uses now");
  }
#endif
}

MergeMemNode* MemoryGraphFixer::allocate_merge_mem(Node* mem, Node* rep_proj, Node* rep_ctrl) const {
  MergeMemNode* mm = MergeMemNode::make(mem);
  mm->set_memory_at(_alias, rep_proj);
  _phase->register_new_node(mm, rep_ctrl);
  return mm;
}

MergeMemNode* MemoryGraphFixer::clone_merge_mem(Node* u, Node* mem, Node* rep_proj, Node* rep_ctrl, DUIterator& i) const {
  MergeMemNode* newmm = NULL;
  MergeMemNode* u_mm = u->as_MergeMem();
  Node* c = _phase->get_ctrl(u);
  if (_phase->is_dominator(c, rep_ctrl)) {
    c = rep_ctrl;
  } else {
    assert(_phase->is_dominator(rep_ctrl, c), "one must dominate the other");
  }
  if (u->outcnt() == 1) {
    if (u->req() > (uint)_alias && u->in(_alias) == mem) {
      _phase->igvn().replace_input_of(u, _alias, rep_proj);
      --i;
    } else {
      _phase->igvn().rehash_node_delayed(u);
      u_mm->set_memory_at(_alias, rep_proj);
    }
    newmm = u_mm;
    _phase->set_ctrl_and_loop(u, c);
  } else {
    // can't simply clone u and then change one of its input because
    // it adds and then removes an edge which messes with the
    // DUIterator
    newmm = MergeMemNode::make(u_mm->base_memory());
    for (uint j = 0; j < u->req(); j++) {
      if (j < newmm->req()) {
        if (j == (uint)_alias) {
          newmm->set_req(j, rep_proj);
        } else if (newmm->in(j) != u->in(j)) {
          newmm->set_req(j, u->in(j));
        }
      } else if (j == (uint)_alias) {
        newmm->add_req(rep_proj);
      } else {
        newmm->add_req(u->in(j));
      }
    }
    if ((uint)_alias >= u->req()) {
      newmm->set_memory_at(_alias, rep_proj);
    }
    _phase->register_new_node(newmm, c);
  }
  return newmm;
}

bool MemoryGraphFixer::should_process_phi(Node* phi) const {
  if (phi->adr_type() == TypePtr::BOTTOM) {
    Node* region = phi->in(0);
    for (DUIterator_Fast jmax, j = region->fast_outs(jmax); j < jmax; j++) {
      Node* uu = region->fast_out(j);
      if (uu->is_Phi() && uu != phi && uu->bottom_type() == Type::MEMORY && _phase->C->get_alias_index(uu->adr_type()) == _alias) {
        return false;
      }
    }
    return true;
  }
  return _phase->C->get_alias_index(phi->adr_type()) == _alias;
}

void MemoryGraphFixer::fix_memory_uses(Node* mem, Node* replacement, Node* rep_proj, Node* rep_ctrl) const {
  uint last = _phase-> C->unique();
  MergeMemNode* mm = NULL;
  assert(mem->bottom_type() == Type::MEMORY, "");
  for (DUIterator i = mem->outs(); mem->has_out(i); i++) {
    Node* u = mem->out(i);
    if (u != replacement && u->_idx < last) {
      if (u->is_ShenandoahBarrier() && _alias != Compile::AliasIdxRaw) {
        if (_phase->C->get_alias_index(u->adr_type()) == _alias && ShenandoahWriteBarrierNode::is_dominator(rep_ctrl, _phase->ctrl_or_self(u), replacement, u, _phase)) {
          _phase->igvn().replace_input_of(u, u->find_edge(mem), rep_proj);
          assert(u->find_edge(mem) == -1, "only one edge");
          --i;
        }
      } else if (u->is_Mem()) {
        if (_phase->C->get_alias_index(u->adr_type()) == _alias && ShenandoahWriteBarrierNode::is_dominator(rep_ctrl, _phase->ctrl_or_self(u), replacement, u, _phase)) {
          assert(_alias == Compile::AliasIdxRaw , "only raw memory can lead to a memory operation");
          _phase->igvn().replace_input_of(u, u->find_edge(mem), rep_proj);
          assert(u->find_edge(mem) == -1, "only one edge");
          --i;
        }
      } else if (u->is_MergeMem()) {
        MergeMemNode* u_mm = u->as_MergeMem();
        if (u_mm->memory_at(_alias) == mem) {
          MergeMemNode* newmm = NULL;
          for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
            Node* uu = u->fast_out(j);
            assert(!uu->is_MergeMem(), "chain of MergeMems?");
            if (uu->is_Phi()) {
              if (should_process_phi(uu)) {
                Node* region = uu->in(0);
                int nb = 0;
                for (uint k = 1; k < uu->req(); k++) {
                  if (uu->in(k) == u && _phase->is_dominator(rep_ctrl, region->in(k))) {
                    if (newmm == NULL) {
                      newmm = clone_merge_mem(u, mem, rep_proj, rep_ctrl, i);
                    }
                    if (newmm != u) {
                      _phase->igvn().replace_input_of(uu, k, newmm);
                      nb++;
                      --jmax;
                    }
                  }
                }
                if (nb > 0) {
                  --j;
                }
              }
            } else {
              if (rep_ctrl != uu && ShenandoahWriteBarrierNode::is_dominator(rep_ctrl, _phase->ctrl_or_self(uu), replacement, uu, _phase)) {
                if (newmm == NULL) {
                  newmm = clone_merge_mem(u, mem, rep_proj, rep_ctrl, i);
                }
                if (newmm != u) {
                  _phase->igvn().replace_input_of(uu, uu->find_edge(u), newmm);
                  --j, --jmax;
                }
              }
            }
          }
        }
      } else if (u->is_Phi()) {
        assert(u->bottom_type() == Type::MEMORY, "what else?");
        Node* region = u->in(0);
        if (should_process_phi(u)) {
          bool replaced = false;
          for (uint j = 1; j < u->req(); j++) {
            if (u->in(j) == mem && _phase->is_dominator(rep_ctrl, region->in(j))) {
              Node* nnew = rep_proj;
              if (u->adr_type() == TypePtr::BOTTOM) {
                if (mm == NULL) {
                  mm = allocate_merge_mem(mem, rep_proj, rep_ctrl);
                }
                nnew = mm;
              }
              _phase->igvn().replace_input_of(u, j, nnew);
              replaced = true;
            }
          }
          if (replaced) {
            --i;
          }

        }
      } else if ((u->adr_type() == TypePtr::BOTTOM && u->Opcode() != Op_StrInflatedCopy) ||
                 u->adr_type() == NULL) {
        assert(u->adr_type() != NULL ||
               u->Opcode() == Op_Rethrow ||
               u->Opcode() == Op_Return ||
               u->Opcode() == Op_SafePoint ||
               (u->is_CallStaticJava() && u->as_CallStaticJava()->uncommon_trap_request() != 0) ||
               (u->is_CallStaticJava() && u->as_CallStaticJava()->_entry_point == OptoRuntime::rethrow_stub()) ||
               u->Opcode() == Op_CallLeaf, "");
        if (ShenandoahWriteBarrierNode::is_dominator(rep_ctrl, _phase->ctrl_or_self(u), replacement, u, _phase)) {
          if (mm == NULL) {
            mm = allocate_merge_mem(mem, rep_proj, rep_ctrl);
          }
          _phase->igvn().replace_input_of(u, u->find_edge(mem), mm);
          --i;
        }
      } else if (_phase->C->get_alias_index(u->adr_type()) == _alias) {
        if (ShenandoahWriteBarrierNode::is_dominator(rep_ctrl, _phase->ctrl_or_self(u), replacement, u, _phase)) {
          _phase->igvn().replace_input_of(u, u->find_edge(mem), rep_proj);
          --i;
        }
      }
    }
  }
}

void MemoryGraphFixer::remove(Node* n) {
  assert(n->Opcode() == Op_ShenandoahWBMemProj, "");
  Node* c = _phase->get_ctrl(n);
  Node* mem = find_mem(c, NULL);
  if (mem == n) {
    _memory_nodes.map(c->_idx, mem->in(ShenandoahWBMemProjNode::WriteBarrier)->in(ShenandoahBarrierNode::Memory));
  }
}
