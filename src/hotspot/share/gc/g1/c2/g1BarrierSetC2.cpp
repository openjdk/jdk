/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaClasses.hpp"
#include "code/vmreg.inline.hpp"
#include "gc/g1/c2/g1BarrierSetC2.hpp"
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/g1BarrierSetRuntime.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/block.hpp"
#include "opto/compile.hpp"
#include "opto/escape.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"
#include "opto/machnode.hpp"
#include "opto/macro.hpp"
#include "opto/memnode.hpp"
#include "opto/node.hpp"
#include "opto/output.hpp"
#include "opto/regalloc.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/type.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

/*
 * Determine if the G1 pre-barrier can be removed. The pre-barrier is
 * required by SATB to make sure all objects live at the start of the
 * marking are kept alive, all reference updates need to any previous
 * reference stored before writing.
 *
 * If the previous value is null there is no need to save the old value.
 * References that are null are filtered during runtime by the barrier
 * code to avoid unnecessary queuing.
 *
 * However in the case of newly allocated objects it might be possible to
 * prove that the reference about to be overwritten is null during compile
 * time and avoid adding the barrier code completely.
 *
 * The compiler needs to determine that the object in which a field is about
 * to be written is newly allocated, and that no prior store to the same field
 * has happened since the allocation.
 */
bool G1BarrierSetC2::g1_can_remove_pre_barrier(GraphKit* kit,
                                               PhaseValues* phase,
                                               Node* adr,
                                               BasicType bt,
                                               uint adr_idx) const {
  intptr_t offset = 0;
  Node* base = AddPNode::Ideal_base_and_offset(adr, phase, offset);
  AllocateNode* alloc = AllocateNode::Ideal_allocation(base);

  if (offset == Type::OffsetBot) {
    return false; // Cannot unalias unless there are precise offsets.
  }
  if (alloc == nullptr) {
    return false; // No allocation found.
  }

  intptr_t size_in_bytes = type2aelembytes(bt);
  Node* mem = kit->memory(adr_idx); // Start searching here.

  for (int cnt = 0; cnt < 50; cnt++) {
    if (mem->is_Store()) {
      Node* st_adr = mem->in(MemNode::Address);
      intptr_t st_offset = 0;
      Node* st_base = AddPNode::Ideal_base_and_offset(st_adr, phase, st_offset);

      if (st_base == nullptr) {
        break; // Inscrutable pointer.
      }
      if (st_base == base && st_offset == offset) {
        // We have found a store with same base and offset as ours.
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
          continue; // Advance through independent store memory.
        }
      }
      if (st_base != base
          && MemNode::detect_ptr_independence(base, alloc, st_base,
                                              AllocateNode::Ideal_allocation(st_base),
                                              phase)) {
        // Success: the bases are provably independent.
        mem = mem->in(MemNode::Memory);
        continue; // Advance through independent store memory.
      }
    } else if (mem->is_Proj() && mem->in(0)->is_Initialize()) {
      InitializeNode* st_init = mem->in(0)->as_Initialize();
      AllocateNode* st_alloc = st_init->allocation();

      // Make sure that we are looking at the same allocation site.
      // The alloc variable is guaranteed to not be null here from earlier check.
      if (alloc == st_alloc) {
        // Check that the initialization is storing null so that no previous store
        // has been moved up and directly write a reference.
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

/*
 * G1, similar to any GC with a Young Generation, requires a way to keep track
 * of references from Old Generation to Young Generation to make sure all live
 * objects are found. G1 also requires to keep track of object references
 * between different regions to enable evacuation of old regions, which is done
 * as part of mixed collections. References are tracked in remembered sets,
 * which are continuously updated as references are written to with the help of
 * the post-barrier.
 *
 * To reduce the number of updates to the remembered set, the post-barrier
 * filters out updates to fields in objects located in the Young Generation, the
 * same region as the reference, when null is being written, or if the card is
 * already marked as dirty by an earlier write.
 *
 * Under certain circumstances it is possible to avoid generating the
 * post-barrier completely, if it is possible during compile time to prove the
 * object is newly allocated and that no safepoint exists between the allocation
 * and the store. This can be seen as a compile-time version of the
 * above-mentioned Young Generation filter.
 *
 * In the case of a slow allocation, the allocation code must handle the barrier
 * as part of the allocation if the allocated object is not located in the
 * nursery; this would happen for humongous objects.
 */
bool G1BarrierSetC2::g1_can_remove_post_barrier(GraphKit* kit,
                                                PhaseValues* phase, Node* store_ctrl,
                                                Node* adr) const {
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

Node* G1BarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  DecoratorSet decorators = access.decorators();
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  // If we are reading the value of the referent field of a Reference object, we
  // need to record the referent in an SATB log buffer using the pre-barrier
  // mechanism. Also we need to add a memory barrier to prevent commoning reads
  // from this field across safepoints, since GC can change its value.
  bool need_read_barrier = ((on_weak || on_phantom) && !no_keepalive);
  if (access.is_oop() && need_read_barrier) {
    access.set_barrier_data(G1C2BarrierPre);
  }
  return CardTableBarrierSetC2::load_at_resolved(access, val_type);
}

void G1BarrierSetC2::eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const {
  eliminate_gc_barrier_data(node);
}

void G1BarrierSetC2::eliminate_gc_barrier_data(Node* node) const {
  if (node->is_LoadStore()) {
    LoadStoreNode* loadstore = node->as_LoadStore();
    loadstore->set_barrier_data(0);
  } else if (node->is_Mem()) {
    MemNode* mem = node->as_Mem();
    mem->set_barrier_data(0);
  }
}

static void refine_barrier_by_new_val_type(const Node* n) {
  if (n->Opcode() != Op_StoreP &&
      n->Opcode() != Op_StoreN) {
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
    barrier_data &= ~G1C2BarrierPost;
    barrier_data &= ~G1C2BarrierPostNotNull;
  } else if (((barrier_data & G1C2BarrierPost) != 0) &&
             newval_type == TypePtr::NotNull) {
    // If the post-barrier has not been elided yet (e.g. due to newval being
    // freshly allocated), mark it as not-null (simplifies barrier tests and
    // compressed OOPs logic).
    barrier_data |= G1C2BarrierPostNotNull;
  }
  store->set_barrier_data(barrier_data);
  return;
}

// Refine (not really expand) G1 barriers by looking at the new value type
// (whether it is necessarily null or necessarily non-null).
bool G1BarrierSetC2::expand_barriers(Compile* C, PhaseIterGVN& igvn) const {
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

uint G1BarrierSetC2::estimated_barrier_size(const Node* node) const {
  uint8_t barrier_data = MemNode::barrier_data(node);
  uint nodes = 0;
  if ((barrier_data & G1C2BarrierPre) != 0) {
    // Only consider the fast path for the barrier that is
    // actually inlined into the main code stream.
    // The slow path is laid out separately and does not
    // directly affect performance.
    // It has a cost of 6 (AddP, LoadB, Cmp, Bool, If, IfProj).
    nodes += 6;
  }
  if ((barrier_data & G1C2BarrierPost) != 0) {
    nodes += 60;
  }
  return nodes;
}

bool G1BarrierSetC2::can_initialize_object(const StoreNode* store) const {
  assert(store->Opcode() == Op_StoreP || store->Opcode() == Op_StoreN, "OOP store expected");
  // It is OK to move the store across the object initialization boundary only
  // if it does not have any barrier, or if it has barriers that can be safely
  // elided (because of the compensation steps taken on the allocation slow path
  // when ReduceInitialCardMarks is enabled).
  return (MemNode::barrier_data(store) == 0) || use_ReduceInitialCardMarks();
}

void G1BarrierSetC2::clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const {
  if (ac->is_clone_inst() && !use_ReduceInitialCardMarks()) {
    clone_in_runtime(phase, ac, G1BarrierSetRuntime::clone_addr(), "G1BarrierSetRuntime::clone");
    return;
  }
  BarrierSetC2::clone_at_expansion(phase, ac);
}

Node* G1BarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  DecoratorSet decorators = access.decorators();
  bool anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool tightly_coupled_alloc = (decorators & C2_TIGHTLY_COUPLED_ALLOC) != 0;
  bool need_store_barrier = !(tightly_coupled_alloc && use_ReduceInitialCardMarks()) && (in_heap || anonymous);
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  if (access.is_oop() && need_store_barrier) {
    access.set_barrier_data(get_store_barrier(access));
    if (tightly_coupled_alloc) {
      assert(!use_ReduceInitialCardMarks(),
             "post-barriers are only needed for tightly-coupled initialization stores when ReduceInitialCardMarks is disabled");
      // Pre-barriers are unnecessary for tightly-coupled initialization stores.
      access.set_barrier_data(access.barrier_data() & ~G1C2BarrierPre);
    }
  }
  if (no_keepalive) {
    // No keep-alive means no need for the pre-barrier.
    access.set_barrier_data(access.barrier_data() & ~G1C2BarrierPre);
  }
  return BarrierSetC2::store_at_resolved(access, val);
}

Node* G1BarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                     Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  if (!access.is_oop()) {
    return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
  }
  access.set_barrier_data(G1C2BarrierPre | G1C2BarrierPost);
  return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
}

Node* G1BarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                      Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  if (!access.is_oop()) {
    return BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
  }
  access.set_barrier_data(G1C2BarrierPre | G1C2BarrierPost);
  return BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
}

Node* G1BarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  if (!access.is_oop()) {
    return BarrierSetC2::atomic_xchg_at_resolved(access, new_val, value_type);
  }
  access.set_barrier_data(G1C2BarrierPre | G1C2BarrierPost);
  return BarrierSetC2::atomic_xchg_at_resolved(access, new_val, value_type);
}

class G1BarrierSetC2State : public BarrierSetC2State {
private:
  GrowableArray<G1BarrierStubC2*>* _stubs;

public:
  G1BarrierSetC2State(Arena* arena)
    : BarrierSetC2State(arena),
      _stubs(new (arena) GrowableArray<G1BarrierStubC2*>(arena, 8,  0, nullptr)) {}

  GrowableArray<G1BarrierStubC2*>* stubs() {
    return _stubs;
  }

  bool needs_liveness_data(const MachNode* mach) const {
    return G1PreBarrierStubC2::needs_barrier(mach) ||
           G1PostBarrierStubC2::needs_barrier(mach);
  }

  bool needs_livein_data() const {
    return false;
  }
};

static G1BarrierSetC2State* barrier_set_state() {
  return reinterpret_cast<G1BarrierSetC2State*>(Compile::current()->barrier_set_state());
}

G1BarrierStubC2::G1BarrierStubC2(const MachNode* node) : BarrierStubC2(node) {}

G1PreBarrierStubC2::G1PreBarrierStubC2(const MachNode* node) : G1BarrierStubC2(node) {}

bool G1PreBarrierStubC2::needs_barrier(const MachNode* node) {
  return (node->barrier_data() & G1C2BarrierPre) != 0;
}

G1PreBarrierStubC2* G1PreBarrierStubC2::create(const MachNode* node) {
  G1PreBarrierStubC2* const stub = new (Compile::current()->comp_arena()) G1PreBarrierStubC2(node);
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->stubs()->append(stub);
  }
  return stub;
}

void G1PreBarrierStubC2::initialize_registers(Register obj, Register pre_val, Register thread, Register tmp1, Register tmp2) {
  _obj = obj;
  _pre_val = pre_val;
  _thread = thread;
  _tmp1 = tmp1;
  _tmp2 = tmp2;
}

Register G1PreBarrierStubC2::obj() const {
  return _obj;
}

Register G1PreBarrierStubC2::pre_val() const {
  return _pre_val;
}

Register G1PreBarrierStubC2::thread() const {
  return _thread;
}

Register G1PreBarrierStubC2::tmp1() const {
  return _tmp1;
}

Register G1PreBarrierStubC2::tmp2() const {
  return _tmp2;
}

void G1PreBarrierStubC2::emit_code(MacroAssembler& masm) {
  G1BarrierSetAssembler* bs = static_cast<G1BarrierSetAssembler*>(BarrierSet::barrier_set()->barrier_set_assembler());
  bs->generate_c2_pre_barrier_stub(&masm, this);
}

G1PostBarrierStubC2::G1PostBarrierStubC2(const MachNode* node) : G1BarrierStubC2(node) {}

bool G1PostBarrierStubC2::needs_barrier(const MachNode* node) {
  return (node->barrier_data() & G1C2BarrierPost) != 0;
}

G1PostBarrierStubC2* G1PostBarrierStubC2::create(const MachNode* node) {
  G1PostBarrierStubC2* const stub = new (Compile::current()->comp_arena()) G1PostBarrierStubC2(node);
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->stubs()->append(stub);
  }
  return stub;
}

void G1PostBarrierStubC2::initialize_registers(Register thread, Register tmp1, Register tmp2, Register tmp3) {
  _thread = thread;
  _tmp1 = tmp1;
  _tmp2 = tmp2;
  _tmp3 = tmp3;
}

Register G1PostBarrierStubC2::thread() const {
  return _thread;
}

Register G1PostBarrierStubC2::tmp1() const {
  return _tmp1;
}

Register G1PostBarrierStubC2::tmp2() const {
  return _tmp2;
}

Register G1PostBarrierStubC2::tmp3() const {
  return _tmp3;
}

void G1PostBarrierStubC2::emit_code(MacroAssembler& masm) {
  G1BarrierSetAssembler* bs = static_cast<G1BarrierSetAssembler*>(BarrierSet::barrier_set()->barrier_set_assembler());
  bs->generate_c2_post_barrier_stub(&masm, this);
}

void* G1BarrierSetC2::create_barrier_state(Arena* comp_arena) const {
  return new (comp_arena) G1BarrierSetC2State(comp_arena);
}

int G1BarrierSetC2::get_store_barrier(C2Access& access) const {
  if (!access.is_parse_access()) {
    // Only support for eliding barriers at parse time for now.
    return G1C2BarrierPre | G1C2BarrierPost;
  }
  GraphKit* kit = (static_cast<C2ParseAccess&>(access)).kit();
  Node* ctl = kit->control();
  Node* adr = access.addr().node();
  uint adr_idx = kit->C->get_alias_index(access.addr().type());
  assert(adr_idx != Compile::AliasIdxTop, "use other store_to_memory factory");

  bool can_remove_pre_barrier = g1_can_remove_pre_barrier(kit, &kit->gvn(), adr, access.type(), adr_idx);

  // We can skip marks on a freshly-allocated object in Eden. Keep this code in
  // sync with CardTableBarrierSet::on_slowpath_allocation_exit. That routine
  // informs GC to take appropriate compensating steps, upon a slow-path
  // allocation, so as to make this card-mark elision safe.
  // The post-barrier can also be removed if null is written. This case is
  // handled by G1BarrierSetC2::expand_barriers, which runs at the end of C2's
  // platform-independent optimizations to exploit stronger type information.
  bool can_remove_post_barrier = use_ReduceInitialCardMarks() &&
    ((access.base() == kit->just_allocated_object(ctl)) ||
     g1_can_remove_post_barrier(kit, &kit->gvn(), ctl, adr));

  int barriers = 0;
  if (!can_remove_pre_barrier) {
    barriers |= G1C2BarrierPre;
  }
  if (!can_remove_post_barrier) {
    barriers |= G1C2BarrierPost;
  }

  return barriers;
}

void G1BarrierSetC2::elide_dominated_barrier(MachNode* mach) const {
  uint8_t barrier_data = mach->barrier_data();
  barrier_data &= ~G1C2BarrierPre;
  if (CardTableBarrierSetC2::use_ReduceInitialCardMarks()) {
    barrier_data &= ~G1C2BarrierPost;
    barrier_data &= ~G1C2BarrierPostNotNull;
  }
  mach->set_barrier_data(barrier_data);
}

void G1BarrierSetC2::analyze_dominating_barriers() const {
  ResourceMark rm;
  PhaseCFG* const cfg = Compile::current()->cfg();

  // Find allocations and memory accesses (stores and atomic operations), and
  // track them in lists.
  Node_List accesses;
  Node_List allocations;
  for (uint i = 0; i < cfg->number_of_blocks(); ++i) {
    const Block* const block = cfg->get_block(i);
    for (uint j = 0; j < block->number_of_nodes(); ++j) {
      Node* const node = block->get_node(j);
      if (node->is_Phi()) {
        if (BarrierSetC2::is_allocation(node)) {
          allocations.push(node);
        }
        continue;
      } else if (!node->is_Mach()) {
        continue;
      }

      MachNode* const mach = node->as_Mach();
      switch (mach->ideal_Opcode()) {
      case Op_StoreP:
      case Op_StoreN:
      case Op_CompareAndExchangeP:
      case Op_CompareAndSwapP:
      case Op_GetAndSetP:
      case Op_CompareAndExchangeN:
      case Op_CompareAndSwapN:
      case Op_GetAndSetN:
        if (mach->barrier_data() != 0) {
          accesses.push(mach);
        }
        break;
      default:
        break;
      }
    }
  }

  // Find dominating allocations for each memory access (store or atomic
  // operation) and elide barriers if there is no safepoint poll in between.
  elide_dominated_barriers(accesses, allocations);
}

void G1BarrierSetC2::late_barrier_analysis() const {
  compute_liveness_at_stubs();
  analyze_dominating_barriers();
}

void G1BarrierSetC2::emit_stubs(CodeBuffer& cb) const {
  MacroAssembler masm(&cb);
  GrowableArray<G1BarrierStubC2*>* const stubs = barrier_set_state()->stubs();
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

#ifndef PRODUCT
void G1BarrierSetC2::dump_barrier_data(const MachNode* mach, outputStream* st) const {
  if ((mach->barrier_data() & G1C2BarrierPre) != 0) {
    st->print("pre ");
  }
  if ((mach->barrier_data() & G1C2BarrierPost) != 0) {
    st->print("post ");
  }
  if ((mach->barrier_data() & G1C2BarrierPostNotNull) != 0) {
    st->print("notnull ");
  }
}
#endif // !PRODUCT
