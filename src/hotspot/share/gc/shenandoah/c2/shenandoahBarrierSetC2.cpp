/*
 * Copyright (c) 2018, 2026, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/escape.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"
#include "opto/macro.hpp"
#include "opto/narrowptrnode.hpp"
#include "opto/output.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"

ShenandoahBarrierSetC2* ShenandoahBarrierSetC2::bsc2() {
  return reinterpret_cast<ShenandoahBarrierSetC2*>(BarrierSet::barrier_set()->barrier_set_c2());
}

ShenandoahBarrierSetC2State::ShenandoahBarrierSetC2State(Arena* comp_arena) :
    BarrierSetC2State(comp_arena),
    _stubs(new (comp_arena) GrowableArray<ShenandoahBarrierStubC2*>(comp_arena, 8,  0, nullptr)),
    _trampoline_stubs_count(0),
    _stubs_start_offset(0),
    _stubs_current_total_size(0) {
}

static void set_barrier_data(C2Access& access, bool load, bool store) {
  if (!access.is_oop()) {
    return;
  }

  DecoratorSet decorators = access.decorators();
  bool tightly_coupled = (decorators & C2_TIGHTLY_COUPLED_ALLOC) != 0;
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;

  if (tightly_coupled) {
    access.set_barrier_data(ShenandoahBitElided);
    return;
  }

  uint8_t barrier_data = 0;

  if (load) {
    if (ShenandoahLoadRefBarrier) {
      if (on_phantom) {
        barrier_data |= ShenandoahBitPhantom;
      } else if (on_weak) {
        barrier_data |= ShenandoahBitWeak;
      } else {
        barrier_data |= ShenandoahBitStrong;
      }
    }
  }

  if (store) {
    if (ShenandoahSATBBarrier) {
      barrier_data |= ShenandoahBitKeepAlive;
    }
    if (ShenandoahCardBarrier && in_heap) {
      barrier_data |= ShenandoahBitCardMark;
    }
  }

  if (!in_heap) {
    barrier_data |= ShenandoahBitNative;
  }

  access.set_barrier_data(barrier_data);
}

Node* ShenandoahBarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  // 1: Non-reference load, no additional barrier is needed
  if (!access.is_oop()) {
    return BarrierSetC2::load_at_resolved(access, val_type);
  }

  // 2. Set barrier data for load
  set_barrier_data(access, /* load = */ true, /* store = */ false);

  // 3. Correction: If we are reading the value of the referent field of
  // a Reference object, we need to record the referent resurrection.
  DecoratorSet decorators = access.decorators();
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  bool needs_keepalive = ((on_weak || on_phantom) && !no_keepalive);
  if (needs_keepalive) {
    uint8_t barriers = access.barrier_data() | (ShenandoahSATBBarrier ? ShenandoahBitKeepAlive : 0);
    access.set_barrier_data(barriers);
  }

  return BarrierSetC2::load_at_resolved(access, val_type);
}

Node* ShenandoahBarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  // 1: Non-reference store, no additional barrier is needed
  if (!access.is_oop()) {
    return BarrierSetC2::store_at_resolved(access, val);
  }

  // 2. Set barrier data for store
  set_barrier_data(access, /* load = */ false, /* store = */ true);

  // 3. Correction: avoid keep-alive barriers that should not do keep-alive.
  DecoratorSet decorators = access.decorators();
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  if (no_keepalive) {
    access.set_barrier_data(access.barrier_data() & ~ShenandoahBitKeepAlive);
  }

  return BarrierSetC2::store_at_resolved(access, val);
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                             Node* new_val, const Type* value_type) const {
  set_barrier_data(access, /* load = */ true, /* store = */ true);
  return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                              Node* new_val, const Type* value_type) const {
  set_barrier_data(access, /* load = */ true, /* store = */ true);
  return BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* val, const Type* value_type) const {
  set_barrier_data(access, /* load = */ true, /* store = */ true);
  return BarrierSetC2::atomic_xchg_at_resolved(access, val, value_type);
}

bool ShenandoahBarrierSetC2::is_Load(int opcode) {
  switch (opcode) {
    case Op_LoadN:
    case Op_LoadP:
      return true;
    default:
      return false;
  }
}

bool ShenandoahBarrierSetC2::is_Store(int opcode) {
  switch (opcode) {
    case Op_StoreN:
    case Op_StoreP:
      return true;
    default:
      return false;
  }
}

bool ShenandoahBarrierSetC2::is_LoadStore(int opcode) {
  switch (opcode) {
    case Op_CompareAndExchangeN:
    case Op_CompareAndExchangeP:
    case Op_WeakCompareAndSwapN:
    case Op_WeakCompareAndSwapP:
    case Op_CompareAndSwapN:
    case Op_CompareAndSwapP:
    case Op_GetAndSetP:
    case Op_GetAndSetN:
      return true;
    default:
      return false;
  }
}

bool ShenandoahBarrierSetC2::can_remove_load_barrier(Node* root) {
  // Check if all outs feed into nodes that do not expose the oops to the rest
  // of the runtime system. In this case, we can elide the LRB barrier. We bail
  // out with false at the first sight of trouble.

  ResourceMark rm;
  VectorSet visited;
  Node_List worklist;
  worklist.push(root);

  while (worklist.size() > 0) {
    Node* n = worklist.pop();
    if (visited.test_set(n->_idx)) {
      continue;
    }

    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* out = n->fast_out(i);
      switch (out->Opcode()) {
        case Op_Phi:
        case Op_EncodeP:
        case Op_DecodeN:
        case Op_CastPP:
        case Op_CheckCastPP:
        case Op_AddP: {
          // Transitive node, check if any other outs are doing anything troublesome.
          worklist.push(out);
          break;
        }

        case Op_LoadRange: {
          // Array length is the same in all copies.
          break;
        }

        case Op_LoadKlass: {
          // Klass is the same in all copies.
          // We would have liked to assert -UCOH, but there are legitimate klass
          // loads from native Klass* instances, which are also safe under +UCOH.
          break;
        }

        case Op_LoadNKlass: {
          // Similar to above, but LoadNKlass is only safe without +UCOH.
          // With +UCOH, it loads from mark word, which clashes with forwarding pointers.
          if (!UseCompactObjectHeaders) {
            break;
          }
          return false;
        }

        case Op_CmpN: {
          if (out->in(1) == n &&
              out->in(2)->Opcode() == Op_ConN &&
              out->in(2)->get_narrowcon() == 0) {
            // Null check, no oop is exposed.
            break;
          }
          if (out->in(2) == n &&
              out->in(1)->Opcode() == Op_ConN &&
              out->in(1)->get_narrowcon() == 0) {
            // Null check, no oop is exposed.
            break;
          }
          return false;
        }

        case Op_CmpP: {
          if (out->in(1) == n &&
              out->in(2)->Opcode() == Op_ConP &&
              out->in(2)->get_ptr() == 0) {
            // Null check, no oop is exposed.
            break;
          }
          if (out->in(2) == n &&
              out->in(1)->Opcode() == Op_ConP &&
              out->in(1)->get_ptr() == 0) {
            // Null check, no oop is exposed.
            break;
          }
          return false;
        }

        case Op_CallStaticJava: {
          if (out->as_CallStaticJava()->is_uncommon_trap()) {
            // Local feeds into uncommon trap. Deopt machinery handles barriers itself.
            break;
          }
          return false;
        }

        default: {
          // Paranoidly distrust any other nodes.
          return false;
        }
      }
    }
  }

  // Nothing troublesome found.
  return true;
}

uint8_t ShenandoahBarrierSetC2::refine_load(Node* n, uint8_t bd) {
  assert(ShenandoahElideIdealBarriers, "Checked by caller");
  assert(bd != 0, "Checked by caller");

  // Do not touch weak loads at all: they are responsible for shielding from
  // Reference.referent resurrection.
  if ((bd & (ShenandoahBitWeak | ShenandoahBitPhantom)) != 0) {
    return bd;
  }

  if (((bd & ShenandoahBitStrong) != 0) && can_remove_load_barrier(n)) {
    bd &= ~ShenandoahBitStrong;
  }

  return bd;
}

uint8_t ShenandoahBarrierSetC2::refine_store(Node* n, uint8_t bd) {
  assert(ShenandoahElideIdealBarriers, "Checked by caller");
  assert(bd != 0, "Checked by caller");
  assert(n->is_Mem() || n->is_LoadStore(), "Sanity");

  const Node* newval = n->in(MemNode::ValueIn);
  assert(newval != nullptr, "Should be present");

  // Type system tells us something about nullity?
  const Type* newval_bottom = newval->bottom_type();
  assert(newval_bottom->isa_oopptr() || newval_bottom->isa_narrowoop() ||
         newval_bottom == TypePtr::NULL_PTR, "Should be an oop store");
  const TypePtr* newval_type = newval_bottom->make_ptr();
  assert(newval_type != nullptr, "Should have been filtered before");
  TypePtr::PTR newval_type_ptr = newval_type->ptr();
  if (newval_type_ptr == TypePtr::Null) {
    bd &= ~ShenandoahBitNotNull;
    // Card table barrier is not needed if we store null.
    bd &= ~ShenandoahBitCardMark;
  } else if (newval_type_ptr == TypePtr::NotNull) {
    // Definitely not null.
    bd |= ShenandoahBitNotNull;
  }

  return bd;
}

void ShenandoahBarrierSetC2::final_refinement(Compile* compile) const {
  ResourceMark rm;
  Unique_Node_List wq;

  RootNode* root = compile->root();
  wq.push(root);

  // Also seed the outs to capture nodes are not reachable from in()-s, e.g. endless loops.
  for (DUIterator_Fast imax, i = root->fast_outs(imax); i < imax; i++) {
    Node* m = root->fast_out(i);
    wq.push(m);
  }

  for (uint next = 0; next < wq.size(); next++) {
    Node* n = wq.at(next);

    assert(!n->is_Mach(), "No Mach nodes here yet");

    int opc = n->Opcode();
    bool is_load = is_Load(opc);
    bool is_store = is_Store(opc);
    bool is_load_store = is_LoadStore(opc);

    uint8_t orig_bd = 0;
    if (is_load_store) {
      orig_bd = n->as_LoadStore()->barrier_data();
    } else if (is_load || is_store) {
      orig_bd = n->as_Mem()->barrier_data();
    }

    uint8_t bd = orig_bd;
    if (ShenandoahElideIdealBarriers && bd != 0) {
      // Note: we cannot apply load optimizations to LoadStores,
      // because their load barriers are needed for fixups.
      if (is_load) {
        bd = refine_load(n, bd);
      }
      if (is_store || is_load_store) {
        bd = refine_store(n, bd);
      }
    }

    // If there are no real barrier flags on the node, strip away additional fluff.
    // Matcher does not care about this, and we would like to avoid invoking "barrier_data() != 0"
    // rules when the only flags are the irrelevant fluff.
    if ((bd != 0) && (bd & ShenandoahBitsReal) == 0) {
      bd = 0;
    }

    if (bd != orig_bd) {
      if (is_load_store) {
        n->as_LoadStore()->set_barrier_data(bd);
      } else {
        n->as_Mem()->set_barrier_data(bd);
      }
    }

    for (uint j = 0; j < n->req(); j++) {
      Node* in = n->in(j);
      if (in != nullptr) {
        wq.push(in);
      }
    }
  }
}

// Support for macro expanded GC barriers
void ShenandoahBarrierSetC2::eliminate_gc_barrier_data(Node* node) const {
  if (node->is_LoadStore()) {
    LoadStoreNode* loadstore = node->as_LoadStore();
    loadstore->set_barrier_data(0);
  } else if (node->is_Mem()) {
    MemNode* mem = node->as_Mem();
    mem->set_barrier_data(0);
  }
}

void ShenandoahBarrierSetC2::eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const {
  eliminate_gc_barrier_data(node);
}

void ShenandoahBarrierSetC2::elide_dominated_barrier(MachNode* node, MachNode* dominator) const {
  uint8_t orig_bd = node->barrier_data();
  if (orig_bd == 0) {
    // Nothing to do.
    return;
  }

  uint8_t bd = orig_bd;
  int node_opcode = node->ideal_Opcode();

  if (dominator == nullptr) {
    // Must be allocation node.
    if (is_Load(node_opcode) || is_LoadStore(node_opcode)) {
      // Loads from recent allocations do not need LRBs.
      bd &= ~ShenandoahBitStrong;
    }
    if (is_Store(node_opcode) || is_LoadStore(node_opcode)) {
      // Stores to recent allocations do not need KA or CM.
      bd &= ~ShenandoahBitKeepAlive;
      bd &= ~ShenandoahBitCardMark;
    }
  } else {
    // LoadStores do not get these optimizations, since their LRBs
    // are required for fixups.
    if (is_Load(node_opcode) || is_Store(node_opcode)) {
      int dom_opcode = dominator->ideal_Opcode();
      uint8_t dom_bd = dominator->barrier_data();

      if (is_Load(dom_opcode) || is_LoadStore(dom_opcode)) {
        // If dominating load is set up to perform LRB fixups, no further LRB is needed.
        if ((dom_bd & ShenandoahBitStrong) != 0) {
          bd &= ~ShenandoahBitStrong;
        }
      }
      if (is_Store(dom_opcode)) {
        // Dominating store has stored the good ref, no LRB is needed.
        bd &= ~ShenandoahBitStrong;
      }
    }
  }

  if (orig_bd != bd) {
    // We are already in final output.
    // Strip the extra barrier data if no real bits are left.
    if ((bd & ShenandoahBitsReal) != 0) {
      node->set_barrier_data(bd);
    } else {
      node->set_barrier_data(0);
    }
  }
}

void ShenandoahBarrierSetC2::analyze_dominating_barriers() const {
  if (!ShenandoahElideMachBarriers) {
    return;
  }

  ResourceMark rm;
  Node_List accesses, dominators;

  PhaseCFG* const cfg = Compile::current()->cfg();
  for (uint i = 0; i < cfg->number_of_blocks(); ++i) {
    const Block* const block = cfg->get_block(i);
    for (uint j = 0; j < block->number_of_nodes(); ++j) {
      Node* const node = block->get_node(j);

      // Everything that happens in allocations does not need barriers.
      // Record them for dominance analysis.
      if (node->is_Phi() && is_allocation(node)) {
        dominators.push(node);
        continue;
      }

      if (!node->is_Mach()) {
        continue;
      }

      MachNode* const mach = node->as_Mach();
      int opcode = mach->ideal_Opcode();
      if (is_Load(opcode) || is_Store(opcode) || is_LoadStore(opcode)) {
        if ((mach->barrier_data() & ShenandoahBitsReal) != 0) {
          accesses.push(mach);
          dominators.push(mach);
        }
      }
    }
  }

  elide_dominated_barriers(accesses, dominators);
}

uint ShenandoahBarrierSetC2::estimated_barrier_size(const Node* node) const {
  // Barrier impact on fast-path is driven by GC state checks emitted very late.
  // These checks are tight load-test-branch sequences, with no impact on C2 graph
  // size. Limiting unrolling in presence of GC barriers might turn some loops
  // tighter than with default unrolling, which may benefit performance due to denser
  // code. Testing shows it is still counter-productive.
  // Therefore, we report zero barrier size to let C2 do its normal thing.
  return 0;
}

bool ShenandoahBarrierSetC2::array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, bool is_clone_instance, ArrayCopyPhase phase) const {
  bool is_oop = is_reference_type(type);
  if (!is_oop) {
    return false;
  }
  if (ShenandoahSATBBarrier && tightly_coupled_alloc) {
    if (phase == Optimization) {
      return false;
    }
    return !is_clone;
  }
  return true;
}

bool ShenandoahBarrierSetC2::clone_needs_barrier(const TypeOopPtr* src_type, bool& is_oop_array) {
  if (!ShenandoahCloneBarrier) {
    return false;
  }

  if (src_type->isa_instptr() != nullptr) {
    // Instance: need barrier only if there is a possibility of having an oop anywhere in it.
    ciInstanceKlass* ik = src_type->is_instptr()->instance_klass();
    if ((src_type->klass_is_exact() || !ik->has_subklass()) &&
        !ik->has_injected_fields() && !ik->has_object_fields()) {
      if (!src_type->klass_is_exact()) {
        // Class is *currently* the leaf in the hierarchy.
        // Record the dependency so that we deopt if this does not hold in future.
        Compile::current()->dependencies()->assert_leaf_type(ik);
      }
      return false;
    }
  } else if (src_type->isa_aryptr() != nullptr) {
    // Array: need barrier only if array is oop-bearing.
    BasicType src_elem = src_type->isa_aryptr()->elem()->array_element_basic_type();
    if (is_reference_type(src_elem, true)) {
      is_oop_array = true;
    } else {
      return false;
    }
  }

  // Assume the worst.
  return true;
}

void ShenandoahBarrierSetC2::clone(GraphKit* kit, Node* src_base, Node* dst_base, Node* size, bool is_array) const {
  const TypeOopPtr* src_type = kit->gvn().type(src_base)->is_oopptr();

  bool is_oop_array = false;
  if (!clone_needs_barrier(src_type, is_oop_array)) {
    // No barrier is needed? Just do what common BarrierSetC2 wants with it.
    BarrierSetC2::clone(kit, src_base, dst_base, size, is_array);
    return;
  }

  if (ShenandoahCloneRuntime || !is_array || !is_oop_array) {
    // Looks like an instance? Prepare the instance clone. This would either
    // be exploded into individual accesses or be left as runtime call.
    // Common BarrierSetC2 prepares everything for both cases.
    BarrierSetC2::clone(kit, src_base, dst_base, size, is_array);
    return;
  }

  // We are cloning the oop array. Prepare to call the normal arraycopy stub
  // after the expansion. Normal stub takes the number of actual type-sized
  // elements to copy after the base, compute the count here.
  Node* offset = kit->MakeConX(arrayOopDesc::base_offset_in_bytes(UseCompressedOops ? T_NARROWOOP : T_OBJECT));
  size = kit->gvn().transform(new SubXNode(size, offset));
  size = kit->gvn().transform(new URShiftXNode(size, kit->intcon(LogBytesPerHeapOop)));
  ArrayCopyNode* ac = ArrayCopyNode::make(kit, false, src_base, offset, dst_base, offset, size, true, false);
  ac->set_clone_array();
  Node* n = kit->gvn().transform(ac);
  if (n == ac) {
    ac->set_adr_type(TypeRawPtr::BOTTOM);
    kit->set_predefined_output_for_runtime_call(ac, ac->in(TypeFunc::Memory), TypeRawPtr::BOTTOM);
  } else {
    kit->set_all_memory(n);
  }
}

void ShenandoahBarrierSetC2::clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const {
  Node* const ctrl        = ac->in(TypeFunc::Control);
  Node* const mem         = ac->in(TypeFunc::Memory);
  Node* const src         = ac->in(ArrayCopyNode::Src);
  Node* const src_offset  = ac->in(ArrayCopyNode::SrcPos);
  Node* const dest        = ac->in(ArrayCopyNode::Dest);
  Node* const dest_offset = ac->in(ArrayCopyNode::DestPos);
  Node* length            = ac->in(ArrayCopyNode::Length);

  const TypeOopPtr* src_type = phase->igvn().type(src)->is_oopptr();

  bool is_oop_array = false;
  if (!clone_needs_barrier(src_type, is_oop_array)) {
    // No barrier is needed? Expand to normal HeapWord-sized arraycopy.
    BarrierSetC2::clone_at_expansion(phase, ac);
    return;
  }

  if (ShenandoahCloneRuntime || !ac->is_clone_array() || !is_oop_array) {
    // Still looks like an instance? Likely a large instance or reflective
    // clone with unknown length. Go to runtime and handle it there.
    clone_in_runtime(phase, ac, ShenandoahRuntime::clone_addr(), "ShenandoahRuntime::clone");
    return;
  }

  // We are cloning the oop array. Call into normal oop array copy stubs.
  // Those stubs would call BarrierSetAssembler to handle GC barriers.

  // This is the full clone, so offsets should equal each other and be at array base.
  assert(src_offset == dest_offset, "should be equal");
  const jlong offset = src_offset->get_long();
  const TypeAryPtr* const ary_ptr = src->get_ptr_type()->isa_aryptr();
  BasicType bt = ary_ptr->elem()->array_element_basic_type();
  assert(offset == arrayOopDesc::base_offset_in_bytes(bt), "should match");

  const char*   copyfunc_name = "arraycopy";
  const address copyfunc_addr = phase->basictype2arraycopy(T_OBJECT, nullptr, nullptr, true, copyfunc_name, true);

  Node* const call = phase->make_leaf_call(ctrl, mem,
      OptoRuntime::fast_arraycopy_Type(),
      copyfunc_addr, copyfunc_name,
      TypeRawPtr::BOTTOM,
      phase->basic_plus_adr(src, src_offset),
      phase->basic_plus_adr(dest, dest_offset),
      length,
      phase->top()
  );
  phase->transform_later(call);

  phase->igvn().replace_node(ac, call);
}

void* ShenandoahBarrierSetC2::create_barrier_state(Arena* comp_arena) const {
  return new(comp_arena) ShenandoahBarrierSetC2State(comp_arena);
}

ShenandoahBarrierSetC2State* ShenandoahBarrierSetC2::state() const {
  return reinterpret_cast<ShenandoahBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

void ShenandoahBarrierSetC2::print_barrier_data(outputStream* os, uint8_t data) {
  os->print(" Node barriers: ");
  if ((data & ShenandoahBitStrong) != 0) {
    data &= ~ShenandoahBitStrong;
    os->print("strong ");
  }

  if ((data & ShenandoahBitWeak) != 0) {
    data &= ~ShenandoahBitWeak;
    os->print("weak ");
  }

  if ((data & ShenandoahBitPhantom) != 0) {
    data &= ~ShenandoahBitPhantom;
    os->print("phantom ");
  }

  if ((data & ShenandoahBitKeepAlive) != 0) {
    data &= ~ShenandoahBitKeepAlive;
    os->print("keepalive ");
  }

  if ((data & ShenandoahBitCardMark) != 0) {
    data &= ~ShenandoahBitCardMark;
    os->print("cardmark ");
  }

  if ((data & ShenandoahBitNative) != 0) {
    data &= ~ShenandoahBitNative;
    os->print("native ");
  }

  if ((data & ShenandoahBitNotNull) != 0) {
    data &= ~ShenandoahBitNotNull;
    os->print("not-null ");
  }

  if ((data & ShenandoahBitElided) != 0) {
    data &= ~ShenandoahBitElided;
    os->print("elided ");
  }

  os->cr();

  if (data > 0) {
    fatal("Unknown bit!");
  }

  os->print_cr(" GC configuration: %sLRB %sSATB %sClone %sCard",
    (ShenandoahLoadRefBarrier ? "+" : "-"),
    (ShenandoahSATBBarrier    ? "+" : "-"),
    (ShenandoahCloneBarrier   ? "+" : "-"),
    (ShenandoahCardBarrier    ? "+" : "-")
  );
}


#ifdef ASSERT
void ShenandoahBarrierSetC2::verify_gc_barrier_assert(bool cond, const char* msg, uint8_t bd, Node* n) {
  if (!cond) {
    stringStream ss;
    ss.print_cr("%s", msg);
    ss.print_cr("-----------------");
    print_barrier_data(&ss, bd);
    ss.print_cr("-----------------");
    n->dump_bfs(1, nullptr, "", &ss);
    report_vm_error(__FILE__, __LINE__, ss.as_string());
  }
}

void ShenandoahBarrierSetC2::verify_gc_barriers(Compile* compile, CompilePhase phase) const {
  if (!ShenandoahVerifyOptoBarriers) {
    return;
  }

  // Verify depending on the barriers actually enabled, allowing verification in passive mode.
  // Normally, we have _some_ bits set on all accesses. Optimizations may drop some bits,
  // but only the last optimization step eliminates all remaining metadata flags. Only then
  // the access data can be completely blank.
  bool final_phase = (phase == BeforeCodeGen);
  bool expect_load_barriers       = !final_phase && ShenandoahLoadRefBarrier;
  bool expect_store_barriers      = !final_phase && (ShenandoahSATBBarrier || ShenandoahCardBarrier);
  bool expect_load_store_barriers = expect_load_barriers || expect_store_barriers;
  bool expect_some_real           = final_phase;

  Unique_Node_List wq;

  RootNode* root = compile->root();
  wq.push(root);

  // Also seed the outs to capture nodes are not reachable from in()-s, e.g. endless loops.
  for (DUIterator_Fast imax, i = root->fast_outs(imax); i < imax; i++) {
    Node* m = root->fast_out(i);
    wq.push(m);
  }

  for (uint next = 0; next < wq.size(); next++) {
    Node *n = wq.at(next);
    assert(!n->is_Mach(), "No Mach nodes here yet");

    int opc = n->Opcode();

    uint8_t bd = 0;
    const TypePtr* adr_type = nullptr;
    if (is_Load(opc)) {
      bd = n->as_Load()->barrier_data();
      adr_type = n->as_Load()->adr_type();
    } else if (is_Store(opc)) {
      bd = n->as_Store()->barrier_data();
      adr_type = n->as_Store()->adr_type();
    } else if (is_LoadStore(opc)) {
      bd = n->as_LoadStore()->barrier_data();
      adr_type = n->as_LoadStore()->adr_type();
    } else if (n->is_Mem()) {
      bd = MemNode::barrier_data(n);
      verify_gc_barrier_assert(bd == 0, "Other mem nodes should have no barrier data", bd, n);
    }

    bool is_weak   = (bd & (ShenandoahBitWeak | ShenandoahBitPhantom)) != 0;
    bool is_native = (bd & ShenandoahBitNative) != 0;

    bool is_referent = adr_type != nullptr &&
                       adr_type->isa_instptr() &&
                       adr_type->is_instptr()->instance_klass()->is_subtype_of(Compile::current()->env()->Reference_klass()) &&
                       adr_type->is_instptr()->offset() == java_lang_ref_Reference::referent_offset();

    bool is_oop_addr = (adr_type != nullptr) && (adr_type->isa_oopptr() || adr_type->isa_narrowoop());
    bool is_raw_addr = (adr_type != nullptr) && (adr_type->isa_rawptr() || adr_type->isa_klassptr());

    verify_gc_barrier_assert(!expect_some_real || (bd == 0) || (bd & ShenandoahBitsReal) != 0, "Without real barriers, metadata should be stripped at this point", bd, n);

    if (is_oop_addr) {
      if (is_Load(opc)) {
        verify_gc_barrier_assert(!expect_load_barriers || (bd != 0), "Oop load should have barrier data", bd, n);
        verify_gc_barrier_assert(!is_weak || is_referent, "Weak load only for Reference.referent", bd, n);
      } else if (is_Store(opc)) {
        // Reference.referent stores can be without barriers.
        verify_gc_barrier_assert(!expect_store_barriers || is_referent || (bd != 0), "Oop store should have barrier data", bd, n);
      } else if (is_LoadStore(opc)) {
        verify_gc_barrier_assert(!expect_load_store_barriers || (bd != 0), "Oop load-store should have barrier data", bd, n);
      }
    } else if (is_raw_addr) {
      if (is_native) {
        if (is_Load(opc)) {
          verify_gc_barrier_assert(!expect_load_barriers || (bd != 0), "Native oop load should have barrier data", bd, n);
        }
        if (is_Store(opc)) {
          verify_gc_barrier_assert(!expect_store_barriers || (bd != 0), "Native oop store should have barrier data", bd, n);
        }
        if (is_LoadStore(opc)) {
          verify_gc_barrier_assert(!expect_load_store_barriers || (bd != 0), "Native oop load-store should have barrier data", bd, n);
        }
      } else {
        // Some Load/Stores are used for T_ADDRESS and/or raw stores, which are supposed not to have barriers.
        // Some other Load/Stores are emitted for real oops, but on raw addresses via Unsafe.
        // The distinction on this level is lost, so we cannot really verify this.
      }
    } else {
      if (is_Load(opc) || is_Store(opc) || is_LoadStore(opc)) {
        verify_gc_barrier_assert(false, "Unclassified access type", bd, n);
      }
    }

    for (uint j = 0; j < n->req(); j++) {
      Node* in = n->in(j);
      if (in != nullptr) {
        wq.push(in);
      }
    }
  }
}
#endif

static ShenandoahBarrierSetC2State* barrier_set_state() {
  return reinterpret_cast<ShenandoahBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

int ShenandoahBarrierSetC2::estimate_stub_size() const {
  GrowableArray<ShenandoahBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  assert(stubs->is_empty(), "Lifecycle: no stubs were yet created");
  return 0;
}

void ShenandoahBarrierSetC2::emit_stubs(CodeBuffer& cb) const {
  MacroAssembler masm(&cb);

  PhaseOutput* const output = Compile::current()->output();
  assert(masm.offset() <= output->buffer_sizing_data()->_code,
         "Stubs are assumed to be emitted directly after code and code_size is a hard limit on where it can start");
  barrier_set_state()->set_stubs_start_offset(masm.offset());

  // Stub generation counts all stubs as skipped for the sake of inlining policy.
  // This is critical for performance, check it.
#ifdef ASSERT
  int offset_before = masm.offset();
  int skipped_before = cb.total_skipped_instructions_size();
#endif

  GrowableArray<ShenandoahBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  for (int i = 0; i < stubs->length(); i++) {
    // Make sure there is enough space in the code buffer
    if (cb.insts()->maybe_expand_to_ensure_remaining(PhaseOutput::MAX_inst_size) && cb.blob() == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      return;
    }
    stubs->at(i)->emit_code(masm);
  }

#ifdef ASSERT
  int offset_after = masm.offset();
  int skipped_after = cb.total_skipped_instructions_size();
  assert(offset_after - offset_before == skipped_after - skipped_before,
         "All stubs are counted as skipped. masm: %d - %d = %d, cb: %d - %d = %d",
        offset_after, offset_before, offset_after - offset_before,
        skipped_after, skipped_before, skipped_after - skipped_before);
#endif

  masm.flush();
}

void ShenandoahBarrierStubC2::register_stub(ShenandoahBarrierStubC2* stub) {
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->stubs()->append(stub);
  }
}

ShenandoahBarrierStubC2* ShenandoahBarrierStubC2::create(const MachNode* node, Register obj, Address addr, Register tmp1, Register tmp2, bool narrow, bool do_load) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahBarrierStubC2(node, obj, addr, tmp1, tmp2, narrow, do_load);
  register_stub(stub);
  return stub;
}

void ShenandoahBarrierStubC2::load_post(MacroAssembler* masm, const MachNode* node, Register obj, Address addr, Register tmp1, Register tmp2, bool narrow) {
  // Load post-barrier:
  //  a. Satisfies the need for LRB for normal loads
  //  b. Passes a weak load through LRB-weak
  //  c. Keep-alives a weak load
  if (needs_slow_barrier(node)) {
    ShenandoahBarrierStubC2* const stub = create(node, obj, addr, tmp1, tmp2, narrow, /* do_load = */ false);
    char check = 0;
    check |= needs_keep_alive_barrier(node)    ? ShenandoahHeap::MARKING : 0;
    check |= needs_load_ref_barrier(node)      ? ShenandoahHeap::HAS_FORWARDED : 0;
    check |= needs_load_ref_barrier_weak(node) ? ShenandoahHeap::WEAK_ROOTS : 0;
    stub->enter_if_gc_state(*masm, check, tmp1);
  }
}

void ShenandoahBarrierStubC2::store_pre(MacroAssembler* masm, const MachNode* node, Address addr, Register tmp1, Register tmp2, Register tmp3, bool narrow) {
  // Store pre-barrier: SATB, keep-alive the current memory value.
  if (needs_slow_barrier(node)) {
    assert(!needs_load_ref_barrier(node), "Should not be required for stores");
    ShenandoahBarrierStubC2* const stub = create(node, tmp1, addr, tmp2, tmp3, narrow, /* do_load = */ true);
    stub->enter_if_gc_state(*masm, ShenandoahHeap::MARKING, tmp1);
  }
}

void ShenandoahBarrierStubC2::load_store_pre(MacroAssembler* masm, const MachNode* node, Address addr, Register tmp1, Register tmp2, Register tmp3, bool narrow) {
  // Load/Store pre-barrier:
  //  a. Avoids false positives from CAS encountering to-space memory values.
  //  b. Satisfies the need for LRB for the CAE result.
  //  c. Records old value for the sake of SATB.
  //
  // (a) and (b) are covered because load barrier does memory location fixup.
  // (c) is covered by KA on the current memory value.
  if (needs_slow_barrier(node)) {
    ShenandoahBarrierStubC2* const stub = create(node, tmp1, addr, tmp2, tmp3, narrow, /* do_load = */ true);
    char check = 0;
    check |= needs_keep_alive_barrier(node) ? ShenandoahHeap::MARKING : 0;
    check |= needs_load_ref_barrier(node)   ? ShenandoahHeap::HAS_FORWARDED : 0;
    assert(!needs_load_ref_barrier_weak(node), "Not supported for Load/Stores");
    stub->enter_if_gc_state(*masm, check, tmp1);
  }
}

void ShenandoahBarrierStubC2::store_post(MacroAssembler* masm, const MachNode* node, Address addr, Register tmp1, Register tmp2) {
  if (needs_card_barrier(node)) {
    cardtable(*masm, addr, tmp1, tmp2);
  }
}

void ShenandoahBarrierStubC2::load_store_post(MacroAssembler* masm, const MachNode* node, Address addr, Register tmp1, Register tmp2) {
  store_post(masm, node, addr, tmp1, tmp2);
}

bool ShenandoahBarrierStubC2::is_live_register(Register reg) {
  return preserve_set().member(OptoReg::as_OptoReg(reg->as_VMReg()));
}

Register ShenandoahBarrierStubC2::select_temp_register(bool& selected_live, Register skip_reg1, Register skip_reg2) {
  Register tmp = noreg;
  Register fallback_live = noreg;

  // Try to select non-live first:
  for (int i = 0; i < available_gp_registers(); i++) {
    Register r = as_Register(i);
    if (r != _obj && r != _addr.base() && r != _addr.index() &&
        r != skip_reg1 && r != skip_reg2 && !is_special_register(r)) {
      if (!is_live_register(r)) {
        tmp = r;
        break;
      } else if (fallback_live == noreg) {
        fallback_live = r;
      }
    }
  }

  // If we could not find a non-live register, select the live fallback:
  if (tmp == noreg) {
    tmp = fallback_live;
    selected_live = true;
  } else {
    selected_live = false;
  }

  assert(tmp != noreg, "successfully selected");
  assert_different_registers(tmp, skip_reg1);
  assert_different_registers(tmp, skip_reg2);
  assert_different_registers(tmp, _obj);
  assert_different_registers(tmp, _addr.base());
  assert_different_registers(tmp, _addr.index());
  return tmp;
}

address ShenandoahBarrierStubC2::keepalive_runtime_entry_addr() {
  if (_narrow) {
    return CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre_narrow);
  } else {
    return CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre);
  }
}

address ShenandoahBarrierStubC2::lrb_runtime_entry_addr() {
  bool is_strong  = (_node->barrier_data() & ShenandoahBitStrong)  != 0;
  bool is_weak    = (_node->barrier_data() & ShenandoahBitWeak)    != 0;
  bool is_phantom = (_node->barrier_data() & ShenandoahBitPhantom) != 0;

  if (_narrow) {
    if (is_strong) {
      return CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow_narrow);
    } else if (is_weak) {
      return CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow_narrow);
    } else if (is_phantom) {
      return CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom_narrow_narrow);
    }
  } else {
    if (is_strong) {
      return CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
    } else if (is_weak) {
      return CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak);
    } else if (is_phantom) {
      return CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom);
    }
  }

  ShouldNotReachHere();
  return nullptr;
}

bool ShenandoahBarrierSetC2State::needs_liveness_data(const MachNode* mach) const {
  // Nodes that require slow-path stubs need liveness data.
  return ShenandoahBarrierStubC2::needs_slow_barrier(mach);
}

bool ShenandoahBarrierSetC2State::needs_livein_data() const {
  return true;
}
