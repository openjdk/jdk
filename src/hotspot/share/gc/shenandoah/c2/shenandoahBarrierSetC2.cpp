/*
 * Copyright (c) 2018, 2023, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/barrierSet.hpp"
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahForwarding.hpp"
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
    _stubs_start_offset(0) {
}

#define __ kit->

static bool satb_can_remove_pre_barrier(GraphKit* kit, PhaseValues* phase, Node* adr,
                                        BasicType bt, uint adr_idx) {
  intptr_t offset = 0;
  Node* base = AddPNode::Ideal_base_and_offset(adr, phase, offset);
  AllocateNode* alloc = AllocateNode::Ideal_allocation(base);

  if (offset == Type::OffsetBot) {
    return false; // cannot unalias unless there are precise offsets
  }

  if (alloc == nullptr) {
    return false; // No allocation found
  }

  intptr_t size_in_bytes = type2aelembytes(bt);

  Node* mem = __ memory(adr_idx); // start searching here...

  for (int cnt = 0; cnt < 50; cnt++) {

    if (mem->is_Store()) {

      Node* st_adr = mem->in(MemNode::Address);
      intptr_t st_offset = 0;
      Node* st_base = AddPNode::Ideal_base_and_offset(st_adr, phase, st_offset);

      if (st_base == nullptr) {
        break; // inscrutable pointer
      }

      // Break we have found a store with same base and offset as ours so break
      if (st_base == base && st_offset == offset) {
        break;
      }

      if (st_offset != offset && st_offset != Type::OffsetBot) {
        const int MAX_STORE = BytesPerLong;
        if (st_offset >= offset + size_in_bytes ||
            st_offset <= offset - MAX_STORE ||
            st_offset <= offset - mem->as_Store()->memory_size()) {
          // Success:  The offsets are provably independent.
          // (You may ask, why not just test st_offset != offset and be done?
          // The answer is that stores of different sizes can co-exist
          // in the same sequence of RawMem effects.  We sometimes initialize
          // a whole 'tile' of array elements with a single jint or jlong.)
          mem = mem->in(MemNode::Memory);
          continue; // advance through independent store memory
        }
      }

      if (st_base != base
          && MemNode::detect_ptr_independence(base, alloc, st_base,
                                              AllocateNode::Ideal_allocation(st_base),
                                              phase)) {
        // Success:  The bases are provably independent.
        mem = mem->in(MemNode::Memory);
        continue; // advance through independent store memory
      }
    } else if (mem->is_Proj() && mem->in(0)->is_Initialize()) {

      InitializeNode* st_init = mem->in(0)->as_Initialize();
      AllocateNode* st_alloc = st_init->allocation();

      // Make sure that we are looking at the same allocation site.
      // The alloc variable is guaranteed to not be null here from earlier check.
      if (alloc == st_alloc) {
        // Check that the initialization is storing null so that no previous store
        // has been moved up and directly write a reference
        Node* captured_store = st_init->find_captured_store(offset,
                                                            type2aelembytes(T_OBJECT),
                                                            phase);
        if (captured_store == nullptr || captured_store == st_init->zero_memory()) {
          return true;
        }
      }
    }

    // Unless there is an explicit 'continue', we must bail out here,
    // because 'mem' is an inscrutable memory state (e.g., a call).
    break;
  }

  return false;
}

static bool shenandoah_can_remove_post_barrier(GraphKit* kit, PhaseValues* phase, Node* store_ctrl, Node* adr) {
  intptr_t      offset = 0;
  Node*         base   = AddPNode::Ideal_base_and_offset(adr, phase, offset);
  AllocateNode* alloc  = AllocateNode::Ideal_allocation(base);

  if (offset == Type::OffsetBot) {
    return false; // Cannot unalias unless there are precise offsets.
  }
  if (alloc == nullptr) {
    return false; // No allocation found.
  }

  Node* mem = store_ctrl;   // Start search from Store node.
  if (mem->is_Proj() && mem->in(0)->is_Initialize()) {
    InitializeNode* st_init = mem->in(0)->as_Initialize();
    AllocateNode*  st_alloc = st_init->allocation();
    // Make sure we are looking at the same allocation
    if (alloc == st_alloc) {
      return true;
    }
  }

  return false;
}

bool ShenandoahBarrierSetC2::is_shenandoah_clone_call(Node* call) {
  return call->is_CallLeaf() &&
         call->as_CallLeaf()->entry_point() == CAST_FROM_FN_PTR(address, ShenandoahRuntime::clone_barrier);
}

const TypeFunc* ShenandoahBarrierSetC2::clone_barrier_Type() {
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeOopPtr::NOTNULL; // src oop
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}

static uint8_t get_store_barrier(C2Access& access) {
  if (!access.is_parse_access()) {
    // Only support for eliding barriers at parse time for now.
    return ShenandoahBarrierSATB | ShenandoahBarrierCardMark;
  }
  GraphKit* kit = (static_cast<C2ParseAccess&>(access)).kit();
  Node* ctl = kit->control();
  Node* adr = access.addr().node();
  uint adr_idx = kit->C->get_alias_index(access.addr().type());
  assert(adr_idx != Compile::AliasIdxTop, "use other store_to_memory factory");

  bool can_remove_pre_barrier = satb_can_remove_pre_barrier(kit, &kit->gvn(), adr, access.type(), adr_idx);

  // We can skip marks on a freshly-allocated object in Eden. Keep this code in
  // sync with CardTableBarrierSet::on_slowpath_allocation_exit. That routine
  // informs GC to take appropriate compensating steps, upon a slow-path
  // allocation, so as to make this card-mark elision safe.
  // The post-barrier can also be removed if null is written. This case is
  // handled by ShenandoahBarrierSetC2::expand_barriers, which runs at the end of C2's
  // platform-independent optimizations to exploit stronger type information.
  bool can_remove_post_barrier = ReduceInitialCardMarks &&
    ((access.base() == kit->just_allocated_object(ctl)) ||
     shenandoah_can_remove_post_barrier(kit, &kit->gvn(), ctl, adr));

  int barriers = 0;
  if (!can_remove_pre_barrier) {
    barriers |= ShenandoahBarrierSATB;
  }
  if (!can_remove_post_barrier) {
    barriers |= ShenandoahBarrierCardMark;
  }

  return barriers;
}

Node* ShenandoahBarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  DecoratorSet decorators = access.decorators();
  bool anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool tightly_coupled_alloc = (decorators & C2_TIGHTLY_COUPLED_ALLOC) != 0;
  bool need_store_barrier = !(tightly_coupled_alloc && ReduceInitialCardMarks) && (in_heap || anonymous);
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  if (access.is_oop() && need_store_barrier) {
    access.set_barrier_data(get_store_barrier(access));
    if (tightly_coupled_alloc) {
      assert(!ReduceInitialCardMarks,
             "post-barriers are only needed for tightly-coupled initialization stores when ReduceInitialCardMarks is disabled");
      // Pre-barriers are unnecessary for tightly-coupled initialization stores.
      access.set_barrier_data(access.barrier_data() & ~ShenandoahBarrierSATB);
    }
  }
  if (no_keepalive) {
    // No keep-alive means no need for the pre-barrier.
    access.set_barrier_data(access.barrier_data() & ~ShenandoahBarrierSATB);
  }
  return BarrierSetC2::store_at_resolved(access, val);
}

static void set_barrier_data(C2Access& access) {
  if (!access.is_oop()) {
    return;
  }

  if (access.decorators() & C2_TIGHTLY_COUPLED_ALLOC) {
    access.set_barrier_data(ShenandoahBarrierElided);
    return;
  }

  uint8_t barrier_data = 0;

  if (access.decorators() & ON_PHANTOM_OOP_REF) {
    barrier_data |= ShenandoahBarrierPhantom;
  } else if (access.decorators() & ON_WEAK_OOP_REF) {
    barrier_data |= ShenandoahBarrierWeak;
  } else {
    barrier_data |= ShenandoahBarrierStrong;
  }

  if (access.decorators() & IN_NATIVE) {
    barrier_data |= ShenandoahBarrierNative;
  }

  access.set_barrier_data(barrier_data);
}

Node* ShenandoahBarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  // 1: non-reference load, no additional barrier is needed
  if (!access.is_oop()) {
    return BarrierSetC2::load_at_resolved(access, val_type);
  }

  // 2. Set barrier data for LRB.
  set_barrier_data(access);

  // 3. If we are reading the value of the referent field of a Reference object, we
  // need to record the referent in an SATB log buffer using the pre-barrier
  // mechanism.
  DecoratorSet decorators = access.decorators();
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  // If we are reading the value of the referent field of a Reference object, we
  // need to record the referent in an SATB log buffer using the pre-barrier
  // mechanism. Also we need to add a memory barrier to prevent commoning reads
  // from this field across safepoints, since GC can change its value.
  uint8_t barriers = access.barrier_data();
  bool need_read_barrier = ((on_weak || on_phantom) && !no_keepalive);
  if (access.is_oop() && need_read_barrier) {
    barriers |= ShenandoahBarrierSATB;
  }
  access.set_barrier_data(barriers);

  return BarrierSetC2::load_at_resolved(access, val_type);
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                             Node* new_val, const Type* value_type) const {
  if (ShenandoahCASBarrier) {
    set_barrier_data(access);
  }

  if (access.is_oop()) {
    access.set_barrier_data(access.barrier_data() | ShenandoahBarrierSATB | ShenandoahBarrierCardMark);
  }
  return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                              Node* new_val, const Type* value_type) const {
  if (ShenandoahCASBarrier) {
    set_barrier_data(access);
  }
  GraphKit* kit = access.kit();
  if (access.is_oop()) {
    access.set_barrier_data(access.barrier_data() | ShenandoahBarrierSATB | ShenandoahBarrierCardMark);
  }
  return BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* val, const Type* value_type) const {
  if (access.is_oop()) {
    access.set_barrier_data(ShenandoahBarrierStrong | ShenandoahBarrierSATB | ShenandoahBarrierCardMark);
  }
  return BarrierSetC2::atomic_xchg_at_resolved(access, val, value_type);
}


bool ShenandoahBarrierSetC2::is_gc_barrier_node(Node* node) const {
  return is_shenandoah_clone_call(node);
}

static void refine_barrier_by_new_val_type(const Node* n) {
  if (n->Opcode() != Op_StoreP && n->Opcode() != Op_StoreN) {
    return;
  }
  MemNode* store = n->as_Mem();
  const Node* newval = n->in(MemNode::ValueIn);
  assert(newval != nullptr, "");
  const Type* newval_bottom = newval->bottom_type();
  TypePtr::PTR newval_type = newval_bottom->make_ptr()->ptr();
  uint8_t barrier_data = store->barrier_data();
  if (!newval_bottom->isa_oopptr() &&
      !newval_bottom->isa_narrowoop() &&
      newval_type != TypePtr::Null) {
    // newval is neither an OOP nor null, so there is no barrier to refine.
    assert(barrier_data == 0, "non-OOP stores should have no barrier data");
    return;
  }
  if (barrier_data == 0) {
    // No barrier to refine.
    return;
  }
  if (newval_type == TypePtr::Null) {
    // Simply elide post-barrier if writing null.
    barrier_data &= ~ShenandoahBarrierCardMark;
    barrier_data &= ~ShenandoahBarrierCardMarkNotNull;
  } else if ((barrier_data & ShenandoahBarrierCardMark) != 0 &&
             newval_type == TypePtr::NotNull) {
    // If the post-barrier has not been elided yet (e.g. due to newval being
    // freshly allocated), mark it as not-null (simplifies barrier tests and
    // compressed OOPs logic).
    barrier_data |= ShenandoahBarrierCardMarkNotNull;
  }
  store->set_barrier_data(barrier_data);
}

bool ShenandoahBarrierSetC2::expand_barriers(Compile* C, PhaseIterGVN& igvn) const {
  ResourceMark rm;
  VectorSet visited;
  Node_List worklist;
  worklist.push(C->root());
  while (worklist.size() > 0) {
    Node* n = worklist.pop();
    if (visited.test_set(n->_idx)) {
      continue;
    }
    refine_barrier_by_new_val_type(n);
    for (uint j = 0; j < n->req(); j++) {
      Node* in = n->in(j);
      if (in != nullptr) {
        worklist.push(in);
      }
    }
  }
  return false;
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

bool ShenandoahBarrierSetC2::clone_needs_barrier(Node* src, PhaseGVN& gvn) {
  const TypeOopPtr* src_type = gvn.type(src)->is_oopptr();
  if (src_type->isa_instptr() != nullptr) {
    ciInstanceKlass* ik = src_type->is_instptr()->instance_klass();
    if ((src_type->klass_is_exact() || !ik->has_subklass()) && !ik->has_injected_fields()) {
      if (ik->has_object_fields()) {
        return true;
      } else {
        if (!src_type->klass_is_exact()) {
          Compile::current()->dependencies()->assert_leaf_type(ik);
        }
      }
    } else {
      return true;
        }
  } else if (src_type->isa_aryptr()) {
    BasicType src_elem = src_type->isa_aryptr()->elem()->array_element_basic_type();
    if (is_reference_type(src_elem, true)) {
      return true;
    }
  } else {
    return true;
  }
  return false;
}

void ShenandoahBarrierSetC2::clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const {
  Node* ctrl = ac->in(TypeFunc::Control);
  Node* mem = ac->in(TypeFunc::Memory);
  Node* src_base = ac->in(ArrayCopyNode::Src);
  Node* src_offset = ac->in(ArrayCopyNode::SrcPos);
  Node* dest_base = ac->in(ArrayCopyNode::Dest);
  Node* dest_offset = ac->in(ArrayCopyNode::DestPos);
  Node* length = ac->in(ArrayCopyNode::Length);

  Node* src = phase->basic_plus_adr(src_base, src_offset);
  Node* dest = phase->basic_plus_adr(dest_base, dest_offset);

  if (ShenandoahCloneBarrier && clone_needs_barrier(src, phase->igvn())) {
    // Check if heap is has forwarded objects. If it does, we need to call into the special
    // routine that would fix up source references before we can continue.

    enum { _heap_stable = 1, _heap_unstable, PATH_LIMIT };
    Node* region = new RegionNode(PATH_LIMIT);
    Node* mem_phi = new PhiNode(region, Type::MEMORY, TypeRawPtr::BOTTOM);

    Node* thread = phase->transform_later(new ThreadLocalNode());
    Node* offset = phase->igvn().MakeConX(in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
    Node* gc_state_addr = phase->transform_later(new AddPNode(phase->C->top(), thread, offset));

    uint gc_state_idx = Compile::AliasIdxRaw;
    const TypePtr* gc_state_adr_type = nullptr; // debug-mode-only argument
    DEBUG_ONLY(gc_state_adr_type = phase->C->get_adr_type(gc_state_idx));

    Node* gc_state    = phase->transform_later(new LoadBNode(ctrl, mem, gc_state_addr, gc_state_adr_type, TypeInt::BYTE, MemNode::unordered));
    Node* stable_and  = phase->transform_later(new AndINode(gc_state, phase->igvn().intcon(ShenandoahHeap::HAS_FORWARDED)));
    Node* stable_cmp  = phase->transform_later(new CmpINode(stable_and, phase->igvn().zerocon(T_INT)));
    Node* stable_test = phase->transform_later(new BoolNode(stable_cmp, BoolTest::ne));

    IfNode* stable_iff  = phase->transform_later(new IfNode(ctrl, stable_test, PROB_UNLIKELY(0.999), COUNT_UNKNOWN))->as_If();
    Node* stable_ctrl   = phase->transform_later(new IfFalseNode(stable_iff));
    Node* unstable_ctrl = phase->transform_later(new IfTrueNode(stable_iff));

    // Heap is stable, no need to do anything additional
    region->init_req(_heap_stable, stable_ctrl);
    mem_phi->init_req(_heap_stable, mem);

    // Heap is unstable, call into clone barrier stub
    Node* call = phase->make_leaf_call(unstable_ctrl, mem,
                                       ShenandoahBarrierSetC2::clone_barrier_Type(),
                                       CAST_FROM_FN_PTR(address, ShenandoahRuntime::clone_barrier),
                                       "shenandoah_clone",
                                       TypeRawPtr::BOTTOM,
                                       src_base);
    call = phase->transform_later(call);

    ctrl = phase->transform_later(new ProjNode(call, TypeFunc::Control));
    mem = phase->transform_later(new ProjNode(call, TypeFunc::Memory));
    region->init_req(_heap_unstable, ctrl);
    mem_phi->init_req(_heap_unstable, mem);

    // Wire up the actual arraycopy stub now
    ctrl = phase->transform_later(region);
    mem = phase->transform_later(mem_phi);

    const char* name = "arraycopy";
    call = phase->make_leaf_call(ctrl, mem,
                                 OptoRuntime::fast_arraycopy_Type(),
                                 phase->basictype2arraycopy(T_LONG, nullptr, nullptr, true, name, true),
                                 name, TypeRawPtr::BOTTOM,
                                 src, dest, length
                                 LP64_ONLY(COMMA phase->top()));
    call = phase->transform_later(call);

    // Hook up the whole thing into the graph
    phase->igvn().replace_node(ac, call);
  } else {
    BarrierSetC2::clone_at_expansion(phase, ac);
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

void* ShenandoahBarrierSetC2::create_barrier_state(Arena* comp_arena) const {
  return new(comp_arena) ShenandoahBarrierSetC2State(comp_arena);
}

ShenandoahBarrierSetC2State* ShenandoahBarrierSetC2::state() const {
  return reinterpret_cast<ShenandoahBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

#ifdef ASSERT
void ShenandoahBarrierSetC2::verify_gc_barriers(Compile* compile, CompilePhase phase) const {
  // TODO: Re-implement C2 barrier verification.
}
#endif

static ShenandoahBarrierSetC2State* barrier_set_state() {
  return reinterpret_cast<ShenandoahBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

int ShenandoahBarrierSetC2::estimate_stub_size() const {
  Compile* const C = Compile::current();
  BufferBlob* const blob = C->output()->scratch_buffer_blob();
  GrowableArray<ShenandoahBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  int size = 0;

  for (int i = 0; i < stubs->length(); i++) {
    CodeBuffer cb(blob->content_begin(), checked_cast<CodeBuffer::csize_t>((address)C->output()->scratch_locs_memory() - blob->content_begin()));
    MacroAssembler masm(&cb);
    stubs->at(i)->emit_code(masm);
    size += cb.insts_size();
  }

  return size;
}

void ShenandoahBarrierSetC2::emit_stubs(CodeBuffer& cb) const {
  MacroAssembler masm(&cb);
  GrowableArray<ShenandoahBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  barrier_set_state()->set_stubs_start_offset(masm.offset());

  for (int i = 0; i < stubs->length(); i++) {
    // Make sure there is enough space in the code buffer
    if (cb.insts()->maybe_expand_to_ensure_remaining(PhaseOutput::MAX_inst_size) && cb.blob() == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      return;
    }

    stubs->at(i)->emit_code(masm);
  }

  masm.flush();

}

void ShenandoahBarrierStubC2::register_stub() {
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->stubs()->append(this);
  }
}

ShenandoahLoadRefBarrierStubC2* ShenandoahLoadRefBarrierStubC2::create(const MachNode* node, Register obj, Register addr, Register tmp1, Register tmp2, Register tmp3, bool narrow) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahLoadRefBarrierStubC2(node, obj, addr, tmp1, tmp2, tmp3, narrow);
  stub->register_stub();
  return stub;
}

ShenandoahSATBBarrierStubC2* ShenandoahSATBBarrierStubC2::create(const MachNode* node, Register addr, Register preval, Register tmp) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahSATBBarrierStubC2(node, addr, preval, tmp);
  stub->register_stub();
  return stub;
}

ShenandoahCASBarrierSlowStubC2* ShenandoahCASBarrierSlowStubC2::create(const MachNode* node, Register addr, Register expected, Register new_val, Register result, Register tmp, bool cae, bool acquire, bool release, bool weak) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahCASBarrierSlowStubC2(node, addr, Address(), expected, new_val, result, tmp, noreg, cae, acquire, release, weak);
  stub->register_stub();
  return stub;
}

ShenandoahCASBarrierSlowStubC2* ShenandoahCASBarrierSlowStubC2::create(const MachNode* node, Address addr, Register expected, Register new_val, Register result, Register tmp1, Register tmp2, bool cae) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahCASBarrierSlowStubC2(node, noreg, addr, expected, new_val, result, tmp1, tmp2, cae, false, false, false);
  stub->register_stub();
  return stub;
}

ShenandoahCASBarrierMidStubC2* ShenandoahCASBarrierMidStubC2::create(const MachNode* node, ShenandoahCASBarrierSlowStubC2* slow_stub, Register expected, Register result, Register tmp, bool cae) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahCASBarrierMidStubC2(node, slow_stub, expected, result, tmp, cae);
  stub->register_stub();
  return stub;
}

bool ShenandoahBarrierSetC2State::needs_liveness_data(const MachNode* mach) const {
  //assert(mach->barrier_data() != 0, "what else?");
  // return mach->barrier_data() != 0;
  //return (mach->barrier_data() & ShenandoahSATBBarrier) != 0;
  return ShenandoahSATBBarrierStubC2::needs_barrier(mach) || ShenandoahLoadRefBarrierStubC2::needs_barrier(mach);
}

bool ShenandoahBarrierSetC2State::needs_livein_data() const {
  return true;
}
