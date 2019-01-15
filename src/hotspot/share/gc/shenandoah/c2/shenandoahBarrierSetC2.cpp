/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#include "gc/shenandoah/c2/shenandoahSupport.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/escape.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"
#include "opto/macro.hpp"
#include "opto/movenode.hpp"
#include "opto/narrowptrnode.hpp"
#include "opto/rootnode.hpp"

ShenandoahBarrierSetC2* ShenandoahBarrierSetC2::bsc2() {
  return reinterpret_cast<ShenandoahBarrierSetC2*>(BarrierSet::barrier_set()->barrier_set_c2());
}

ShenandoahBarrierSetC2State::ShenandoahBarrierSetC2State(Arena* comp_arena)
  : _shenandoah_barriers(new (comp_arena) GrowableArray<ShenandoahWriteBarrierNode*>(comp_arena, 8,  0, NULL)) {
}

int ShenandoahBarrierSetC2State::shenandoah_barriers_count() const {
  return _shenandoah_barriers->length();
}

ShenandoahWriteBarrierNode* ShenandoahBarrierSetC2State::shenandoah_barrier(int idx) const {
  return _shenandoah_barriers->at(idx);
}

void ShenandoahBarrierSetC2State::add_shenandoah_barrier(ShenandoahWriteBarrierNode * n) {
  assert(!_shenandoah_barriers->contains(n), "duplicate entry in barrier list");
  _shenandoah_barriers->append(n);
}

void ShenandoahBarrierSetC2State::remove_shenandoah_barrier(ShenandoahWriteBarrierNode * n) {
  if (_shenandoah_barriers->contains(n)) {
    _shenandoah_barriers->remove(n);
  }
}

#define __ kit->

Node* ShenandoahBarrierSetC2::shenandoah_read_barrier(GraphKit* kit, Node* obj) const {
  if (ShenandoahReadBarrier) {
    obj = shenandoah_read_barrier_impl(kit, obj, false, true, true);
  }
  return obj;
}

Node* ShenandoahBarrierSetC2::shenandoah_storeval_barrier(GraphKit* kit, Node* obj) const {
  if (ShenandoahStoreValEnqueueBarrier) {
    obj = shenandoah_write_barrier(kit, obj);
    obj = shenandoah_enqueue_barrier(kit, obj);
  }
  if (ShenandoahStoreValReadBarrier) {
    obj = shenandoah_read_barrier_impl(kit, obj, true, false, false);
  }
  return obj;
}

Node* ShenandoahBarrierSetC2::shenandoah_read_barrier_impl(GraphKit* kit, Node* obj, bool use_ctrl, bool use_mem, bool allow_fromspace) const {
  const Type* obj_type = obj->bottom_type();
  if (obj_type->higher_equal(TypePtr::NULL_PTR)) {
    return obj;
  }
  const TypePtr* adr_type = ShenandoahBarrierNode::brooks_pointer_type(obj_type);
  Node* mem = use_mem ? __ memory(adr_type) : __ immutable_memory();

  if (! ShenandoahBarrierNode::needs_barrier(&__ gvn(), NULL, obj, mem, allow_fromspace)) {
    // We know it is null, no barrier needed.
    return obj;
  }

  if (obj_type->meet(TypePtr::NULL_PTR) == obj_type->remove_speculative()) {

    // We don't know if it's null or not. Need null-check.
    enum { _not_null_path = 1, _null_path, PATH_LIMIT };
    RegionNode* region = new RegionNode(PATH_LIMIT);
    Node*       phi    = new PhiNode(region, obj_type);
    Node* null_ctrl = __ top();
    Node* not_null_obj = __ null_check_oop(obj, &null_ctrl);

    region->init_req(_null_path, null_ctrl);
    phi   ->init_req(_null_path, __ zerocon(T_OBJECT));

    Node* ctrl = use_ctrl ? __ control() : NULL;
    ShenandoahReadBarrierNode* rb = new ShenandoahReadBarrierNode(ctrl, mem, not_null_obj, allow_fromspace);
    Node* n = __ gvn().transform(rb);

    region->init_req(_not_null_path, __ control());
    phi   ->init_req(_not_null_path, n);

    __ set_control(__ gvn().transform(region));
    __ record_for_igvn(region);
    return __ gvn().transform(phi);

  } else {
    // We know it is not null. Simple barrier is sufficient.
    Node* ctrl = use_ctrl ? __ control() : NULL;
    ShenandoahReadBarrierNode* rb = new ShenandoahReadBarrierNode(ctrl, mem, obj, allow_fromspace);
    Node* n = __ gvn().transform(rb);
    __ record_for_igvn(n);
    return n;
  }
}

Node* ShenandoahBarrierSetC2::shenandoah_write_barrier_helper(GraphKit* kit, Node* obj, const TypePtr* adr_type) const {
  ShenandoahWriteBarrierNode* wb = new ShenandoahWriteBarrierNode(kit->C, kit->control(), kit->memory(adr_type), obj);
  Node* n = __ gvn().transform(wb);
  if (n == wb) { // New barrier needs memory projection.
    Node* proj = __ gvn().transform(new ShenandoahWBMemProjNode(n));
    __ set_memory(proj, adr_type);
  }
  return n;
}

Node* ShenandoahBarrierSetC2::shenandoah_write_barrier(GraphKit* kit, Node* obj) const {
  if (ShenandoahWriteBarrier) {
    obj = shenandoah_write_barrier_impl(kit, obj);
  }
  return obj;
}

Node* ShenandoahBarrierSetC2::shenandoah_write_barrier_impl(GraphKit* kit, Node* obj) const {
  if (! ShenandoahBarrierNode::needs_barrier(&__ gvn(), NULL, obj, NULL, true)) {
    return obj;
  }
  const Type* obj_type = obj->bottom_type();
  const TypePtr* adr_type = ShenandoahBarrierNode::brooks_pointer_type(obj_type);
  Node* n = shenandoah_write_barrier_helper(kit, obj, adr_type);
  __ record_for_igvn(n);
  return n;
}

bool ShenandoahBarrierSetC2::satb_can_remove_pre_barrier(GraphKit* kit, PhaseTransform* phase, Node* adr,
                                                         BasicType bt, uint adr_idx) const {
  intptr_t offset = 0;
  Node* base = AddPNode::Ideal_base_and_offset(adr, phase, offset);
  AllocateNode* alloc = AllocateNode::Ideal_allocation(base, phase);

  if (offset == Type::OffsetBot) {
    return false; // cannot unalias unless there are precise offsets
  }

  if (alloc == NULL) {
    return false; // No allocation found
  }

  intptr_t size_in_bytes = type2aelembytes(bt);

  Node* mem = __ memory(adr_idx); // start searching here...

  for (int cnt = 0; cnt < 50; cnt++) {

    if (mem->is_Store()) {

      Node* st_adr = mem->in(MemNode::Address);
      intptr_t st_offset = 0;
      Node* st_base = AddPNode::Ideal_base_and_offset(st_adr, phase, st_offset);

      if (st_base == NULL) {
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
                                              AllocateNode::Ideal_allocation(st_base, phase),
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
        // Check that the initialization is storing NULL so that no previous store
        // has been moved up and directly write a reference
        Node* captured_store = st_init->find_captured_store(offset,
                                                            type2aelembytes(T_OBJECT),
                                                            phase);
        if (captured_store == NULL || captured_store == st_init->zero_memory()) {
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

#undef __
#define __ ideal.

void ShenandoahBarrierSetC2::satb_write_barrier_pre(GraphKit* kit,
                                                    bool do_load,
                                                    Node* obj,
                                                    Node* adr,
                                                    uint alias_idx,
                                                    Node* val,
                                                    const TypeOopPtr* val_type,
                                                    Node* pre_val,
                                                    BasicType bt) const {
  // Some sanity checks
  // Note: val is unused in this routine.

  if (do_load) {
    // We need to generate the load of the previous value
    assert(obj != NULL, "must have a base");
    assert(adr != NULL, "where are loading from?");
    assert(pre_val == NULL, "loaded already?");
    assert(val_type != NULL, "need a type");

    if (ReduceInitialCardMarks
        && satb_can_remove_pre_barrier(kit, &kit->gvn(), adr, bt, alias_idx)) {
      return;
    }

  } else {
    // In this case both val_type and alias_idx are unused.
    assert(pre_val != NULL, "must be loaded already");
    // Nothing to be done if pre_val is null.
    if (pre_val->bottom_type() == TypePtr::NULL_PTR) return;
    assert(pre_val->bottom_type()->basic_type() == T_OBJECT, "or we shouldn't be here");
  }
  assert(bt == T_OBJECT, "or we shouldn't be here");

  IdealKit ideal(kit, true);

  Node* tls = __ thread(); // ThreadLocalStorage

  Node* no_base = __ top();
  Node* zero  = __ ConI(0);
  Node* zeroX = __ ConX(0);

  float likely  = PROB_LIKELY(0.999);
  float unlikely  = PROB_UNLIKELY(0.999);

  // Offsets into the thread
  const int index_offset   = in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset());
  const int buffer_offset  = in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset());

  // Now the actual pointers into the thread
  Node* buffer_adr  = __ AddP(no_base, tls, __ ConX(buffer_offset));
  Node* index_adr   = __ AddP(no_base, tls, __ ConX(index_offset));

  // Now some of the values
  Node* marking;
  Node* gc_state = __ AddP(no_base, tls, __ ConX(in_bytes(ShenandoahThreadLocalData::gc_state_offset())));
  Node* ld = __ load(__ ctrl(), gc_state, TypeInt::BYTE, T_BYTE, Compile::AliasIdxRaw);
  marking = __ AndI(ld, __ ConI(ShenandoahHeap::MARKING));
  assert(ShenandoahWriteBarrierNode::is_gc_state_load(ld), "Should match the shape");

  // if (!marking)
  __ if_then(marking, BoolTest::ne, zero, unlikely); {
    BasicType index_bt = TypeX_X->basic_type();
    assert(sizeof(size_t) == type2aelembytes(index_bt), "Loading G1 SATBMarkQueue::_index with wrong size.");
    Node* index   = __ load(__ ctrl(), index_adr, TypeX_X, index_bt, Compile::AliasIdxRaw);

    if (do_load) {
      // load original value
      // alias_idx correct??
      pre_val = __ load(__ ctrl(), adr, val_type, bt, alias_idx);
    }

    // if (pre_val != NULL)
    __ if_then(pre_val, BoolTest::ne, kit->null()); {
      Node* buffer  = __ load(__ ctrl(), buffer_adr, TypeRawPtr::NOTNULL, T_ADDRESS, Compile::AliasIdxRaw);

      // is the queue for this thread full?
      __ if_then(index, BoolTest::ne, zeroX, likely); {

        // decrement the index
        Node* next_index = kit->gvn().transform(new SubXNode(index, __ ConX(sizeof(intptr_t))));

        // Now get the buffer location we will log the previous value into and store it
        Node *log_addr = __ AddP(no_base, buffer, next_index);
        __ store(__ ctrl(), log_addr, pre_val, T_OBJECT, Compile::AliasIdxRaw, MemNode::unordered);
        // update the index
        __ store(__ ctrl(), index_adr, next_index, index_bt, Compile::AliasIdxRaw, MemNode::unordered);

      } __ else_(); {

        // logging buffer is full, call the runtime
        const TypeFunc *tf = ShenandoahBarrierSetC2::write_ref_field_pre_entry_Type();
        __ make_leaf_call(tf, CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_ref_field_pre_entry), "shenandoah_wb_pre", pre_val, tls);
      } __ end_if();  // (!index)
    } __ end_if();  // (pre_val != NULL)
  } __ end_if();  // (!marking)

  // Final sync IdealKit and GraphKit.
  kit->final_sync(ideal);

  if (ShenandoahSATBBarrier && adr != NULL) {
    Node* c = kit->control();
    Node* call = c->in(1)->in(1)->in(1)->in(0);
    assert(is_shenandoah_wb_pre_call(call), "shenandoah_wb_pre call expected");
    call->add_req(adr);
  }
}

bool ShenandoahBarrierSetC2::is_shenandoah_wb_pre_call(Node* call) {
  return call->is_CallLeaf() &&
         call->as_CallLeaf()->entry_point() == CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_ref_field_pre_entry);
}

bool ShenandoahBarrierSetC2::is_shenandoah_wb_call(Node* call) {
  return call->is_CallLeaf() &&
         call->as_CallLeaf()->entry_point() == CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_JRT);
}

bool ShenandoahBarrierSetC2::is_shenandoah_marking_if(PhaseTransform *phase, Node* n) {
  if (n->Opcode() != Op_If) {
    return false;
  }

  Node* bol = n->in(1);
  assert(bol->is_Bool(), "");
  Node* cmpx = bol->in(1);
  if (bol->as_Bool()->_test._test == BoolTest::ne &&
      cmpx->is_Cmp() && cmpx->in(2) == phase->intcon(0) &&
      is_shenandoah_state_load(cmpx->in(1)->in(1)) &&
      cmpx->in(1)->in(2)->is_Con() &&
      cmpx->in(1)->in(2) == phase->intcon(ShenandoahHeap::MARKING)) {
    return true;
  }

  return false;
}

bool ShenandoahBarrierSetC2::is_shenandoah_state_load(Node* n) {
  if (!n->is_Load()) return false;
  const int state_offset = in_bytes(ShenandoahThreadLocalData::gc_state_offset());
  return n->in(2)->is_AddP() && n->in(2)->in(2)->Opcode() == Op_ThreadLocal
         && n->in(2)->in(3)->is_Con()
         && n->in(2)->in(3)->bottom_type()->is_intptr_t()->get_con() == state_offset;
}

void ShenandoahBarrierSetC2::shenandoah_write_barrier_pre(GraphKit* kit,
                                                          bool do_load,
                                                          Node* obj,
                                                          Node* adr,
                                                          uint alias_idx,
                                                          Node* val,
                                                          const TypeOopPtr* val_type,
                                                          Node* pre_val,
                                                          BasicType bt) const {
  if (ShenandoahSATBBarrier) {
    IdealKit ideal(kit);
    kit->sync_kit(ideal);

    satb_write_barrier_pre(kit, do_load, obj, adr, alias_idx, val, val_type, pre_val, bt);

    ideal.sync_kit(kit);
    kit->final_sync(ideal);
  }
}

Node* ShenandoahBarrierSetC2::shenandoah_enqueue_barrier(GraphKit* kit, Node* pre_val) const {
  return kit->gvn().transform(new ShenandoahEnqueueBarrierNode(pre_val));
}

// Helper that guards and inserts a pre-barrier.
void ShenandoahBarrierSetC2::insert_pre_barrier(GraphKit* kit, Node* base_oop, Node* offset,
                                                Node* pre_val, bool need_mem_bar) const {
  // We could be accessing the referent field of a reference object. If so, when G1
  // is enabled, we need to log the value in the referent field in an SATB buffer.
  // This routine performs some compile time filters and generates suitable
  // runtime filters that guard the pre-barrier code.
  // Also add memory barrier for non volatile load from the referent field
  // to prevent commoning of loads across safepoint.

  // Some compile time checks.

  // If offset is a constant, is it java_lang_ref_Reference::_reference_offset?
  const TypeX* otype = offset->find_intptr_t_type();
  if (otype != NULL && otype->is_con() &&
      otype->get_con() != java_lang_ref_Reference::referent_offset) {
    // Constant offset but not the reference_offset so just return
    return;
  }

  // We only need to generate the runtime guards for instances.
  const TypeOopPtr* btype = base_oop->bottom_type()->isa_oopptr();
  if (btype != NULL) {
    if (btype->isa_aryptr()) {
      // Array type so nothing to do
      return;
    }

    const TypeInstPtr* itype = btype->isa_instptr();
    if (itype != NULL) {
      // Can the klass of base_oop be statically determined to be
      // _not_ a sub-class of Reference and _not_ Object?
      ciKlass* klass = itype->klass();
      if ( klass->is_loaded() &&
          !klass->is_subtype_of(kit->env()->Reference_klass()) &&
          !kit->env()->Object_klass()->is_subtype_of(klass)) {
        return;
      }
    }
  }

  // The compile time filters did not reject base_oop/offset so
  // we need to generate the following runtime filters
  //
  // if (offset == java_lang_ref_Reference::_reference_offset) {
  //   if (instance_of(base, java.lang.ref.Reference)) {
  //     pre_barrier(_, pre_val, ...);
  //   }
  // }

  float likely   = PROB_LIKELY(  0.999);
  float unlikely = PROB_UNLIKELY(0.999);

  IdealKit ideal(kit);

  Node* referent_off = __ ConX(java_lang_ref_Reference::referent_offset);

  __ if_then(offset, BoolTest::eq, referent_off, unlikely); {
      // Update graphKit memory and control from IdealKit.
      kit->sync_kit(ideal);

      Node* ref_klass_con = kit->makecon(TypeKlassPtr::make(kit->env()->Reference_klass()));
      Node* is_instof = kit->gen_instanceof(base_oop, ref_klass_con);

      // Update IdealKit memory and control from graphKit.
      __ sync_kit(kit);

      Node* one = __ ConI(1);
      // is_instof == 0 if base_oop == NULL
      __ if_then(is_instof, BoolTest::eq, one, unlikely); {

        // Update graphKit from IdeakKit.
        kit->sync_kit(ideal);

        // Use the pre-barrier to record the value in the referent field
        satb_write_barrier_pre(kit, false /* do_load */,
                               NULL /* obj */, NULL /* adr */, max_juint /* alias_idx */, NULL /* val */, NULL /* val_type */,
                               pre_val /* pre_val */,
                               T_OBJECT);
        if (need_mem_bar) {
          // Add memory barrier to prevent commoning reads from this field
          // across safepoint since GC can change its value.
          kit->insert_mem_bar(Op_MemBarCPUOrder);
        }
        // Update IdealKit from graphKit.
        __ sync_kit(kit);

      } __ end_if(); // _ref_type != ref_none
  } __ end_if(); // offset == referent_offset

  // Final sync IdealKit and GraphKit.
  kit->final_sync(ideal);
}

#undef __

const TypeFunc* ShenandoahBarrierSetC2::write_ref_field_pre_entry_Type() {
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // original field value
  fields[TypeFunc::Parms+1] = TypeRawPtr::NOTNULL; // thread
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc* ShenandoahBarrierSetC2::shenandoah_clone_barrier_Type() {
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // original field value
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc* ShenandoahBarrierSetC2::shenandoah_write_barrier_Type() {
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // original field value
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

void ShenandoahBarrierSetC2::resolve_address(C2Access& access) const {
  const TypePtr* adr_type = access.addr().type();

  if ((access.decorators() & IN_NATIVE) == 0 && (adr_type->isa_instptr() || adr_type->isa_aryptr())) {
    int off = adr_type->is_ptr()->offset();
    int base_off = adr_type->isa_instptr() ? instanceOopDesc::base_offset_in_bytes() :
      arrayOopDesc::base_offset_in_bytes(adr_type->is_aryptr()->elem()->array_element_basic_type());
    assert(off != Type::OffsetTop, "unexpected offset");
    if (off == Type::OffsetBot || off >= base_off) {
      DecoratorSet decorators = access.decorators();
      bool is_write = (decorators & C2_WRITE_ACCESS) != 0;
      GraphKit* kit = NULL;
      if (access.is_parse_access()) {
        C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);
        kit = parse_access.kit();
      }
      Node* adr = access.addr().node();
      assert(adr->is_AddP(), "unexpected address shape");
      Node* base = adr->in(AddPNode::Base);

      if (is_write) {
        if (kit != NULL) {
          base = shenandoah_write_barrier(kit, base);
        } else {
          assert(access.is_opt_access(), "either parse or opt access");
          assert((access.decorators() & C2_ARRAY_COPY) != 0, "can be skipped for clone");
        }
      } else {
        if (adr_type->isa_instptr()) {
          Compile* C = access.gvn().C;
          ciField* field = C->alias_type(adr_type)->field();

          // Insert read barrier for Shenandoah.
          if (field != NULL &&
              ((ShenandoahOptimizeStaticFinals   && field->is_static()  && field->is_final()) ||
               (ShenandoahOptimizeInstanceFinals && !field->is_static() && field->is_final()) ||
               (ShenandoahOptimizeStableFinals   && field->is_stable()))) {
            // Skip the barrier for special fields
          } else {
            if (kit != NULL) {
              base = shenandoah_read_barrier(kit, base);
            } else {
              assert(access.is_opt_access(), "either parse or opt access");
              assert((access.decorators() & C2_ARRAY_COPY) != 0, "can be skipped for arraycopy");
            }
          }
        } else {
          if (kit != NULL) {
            base = shenandoah_read_barrier(kit, base);
          } else {
            assert(access.is_opt_access(), "either parse or opt access");
            assert((access.decorators() & C2_ARRAY_COPY) != 0, "can be skipped for arraycopy");
          }
        }
      }
      if (base != adr->in(AddPNode::Base)) {
        assert(kit != NULL, "no barrier should have been added");

        Node* address = adr->in(AddPNode::Address);

        if (address->is_AddP()) {
          assert(address->in(AddPNode::Base) == adr->in(AddPNode::Base), "unexpected address shape");
          assert(!address->in(AddPNode::Address)->is_AddP(), "unexpected address shape");
          assert(address->in(AddPNode::Address) == adr->in(AddPNode::Base), "unexpected address shape");
          address = address->clone();
          address->set_req(AddPNode::Base, base);
          address->set_req(AddPNode::Address, base);
          address = kit->gvn().transform(address);
        } else {
          assert(address == adr->in(AddPNode::Base), "unexpected address shape");
          address = base;
        }
        adr = adr->clone();
        adr->set_req(AddPNode::Base, base);
        adr->set_req(AddPNode::Address, address);
        adr = kit->gvn().transform(adr);
        access.addr().set_node(adr);
      }
    }
  }
}

Node* ShenandoahBarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  DecoratorSet decorators = access.decorators();

  const TypePtr* adr_type = access.addr().type();
  Node* adr = access.addr().node();

  bool anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool on_heap = (decorators & IN_HEAP) != 0;

  if (!access.is_oop() || (!on_heap && !anonymous)) {
    return BarrierSetC2::store_at_resolved(access, val);
  }

  if (access.is_parse_access()) {
    C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);
    GraphKit* kit = parse_access.kit();

    uint adr_idx = kit->C->get_alias_index(adr_type);
    assert(adr_idx != Compile::AliasIdxTop, "use other store_to_memory factory" );
    Node* value = val.node();
    value = shenandoah_storeval_barrier(kit, value);
    val.set_node(value);
    shenandoah_write_barrier_pre(kit, true /* do_load */, /*kit->control(),*/ access.base(), adr, adr_idx, val.node(),
                                 static_cast<const TypeOopPtr*>(val.type()), NULL /* pre_val */, access.type());
  } else {
    assert(access.is_opt_access(), "only for optimization passes");
    assert(((decorators & C2_TIGHLY_COUPLED_ALLOC) != 0 || !ShenandoahSATBBarrier) && (decorators & C2_ARRAY_COPY) != 0, "unexpected caller of this code");
    C2OptAccess& opt_access = static_cast<C2OptAccess&>(access);
    PhaseGVN& gvn =  opt_access.gvn();
    MergeMemNode* mm = opt_access.mem();

    if (ShenandoahStoreValReadBarrier) {
      RegionNode* region = new RegionNode(3);
      const Type* v_t = gvn.type(val.node());
      Node* phi = new PhiNode(region, v_t->isa_oopptr() ? v_t->is_oopptr()->cast_to_nonconst() : v_t);
      Node* cmp = gvn.transform(new CmpPNode(val.node(), gvn.zerocon(T_OBJECT)));
      Node* bol = gvn.transform(new BoolNode(cmp, BoolTest::ne));
      IfNode* iff = new IfNode(opt_access.ctl(), bol, PROB_LIKELY_MAG(3), COUNT_UNKNOWN);

      gvn.transform(iff);
      if (gvn.is_IterGVN()) {
        gvn.is_IterGVN()->_worklist.push(iff);
      } else {
        gvn.record_for_igvn(iff);
      }

      Node* null_true = gvn.transform(new IfFalseNode(iff));
      Node* null_false = gvn.transform(new IfTrueNode(iff));
      region->init_req(1, null_true);
      region->init_req(2, null_false);
      phi->init_req(1, gvn.zerocon(T_OBJECT));
      Node* cast = new CastPPNode(val.node(), gvn.type(val.node())->join_speculative(TypePtr::NOTNULL));
      cast->set_req(0, null_false);
      cast = gvn.transform(cast);
      Node* rb = gvn.transform(new ShenandoahReadBarrierNode(null_false, gvn.C->immutable_memory(), cast, false));
      phi->init_req(2, rb);
      opt_access.set_ctl(gvn.transform(region));
      val.set_node(gvn.transform(phi));
    }
    if (ShenandoahStoreValEnqueueBarrier) {
      const TypePtr* adr_type = ShenandoahBarrierNode::brooks_pointer_type(gvn.type(val.node()));
      int alias = gvn.C->get_alias_index(adr_type);
      Node* wb = new ShenandoahWriteBarrierNode(gvn.C, opt_access.ctl(), mm->memory_at(alias), val.node());
      Node* wb_transformed = gvn.transform(wb);
      Node* enqueue = gvn.transform(new ShenandoahEnqueueBarrierNode(wb_transformed));
      if (wb_transformed == wb) {
        Node* proj = gvn.transform(new ShenandoahWBMemProjNode(wb));
        mm->set_memory_at(alias, proj);
      }
      val.set_node(enqueue);
    }
  }
  return BarrierSetC2::store_at_resolved(access, val);
}

Node* ShenandoahBarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  DecoratorSet decorators = access.decorators();

  Node* adr = access.addr().node();
  Node* obj = access.base();

  bool mismatched = (decorators & C2_MISMATCHED) != 0;
  bool unknown = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool on_heap = (decorators & IN_HEAP) != 0;
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool is_unordered = (decorators & MO_UNORDERED) != 0;
  bool need_cpu_mem_bar = !is_unordered || mismatched || !on_heap;

  Node* top = Compile::current()->top();

  Node* offset = adr->is_AddP() ? adr->in(AddPNode::Offset) : top;
  Node* load = BarrierSetC2::load_at_resolved(access, val_type);

  // If we are reading the value of the referent field of a Reference
  // object (either by using Unsafe directly or through reflection)
  // then, if SATB is enabled, we need to record the referent in an
  // SATB log buffer using the pre-barrier mechanism.
  // Also we need to add memory barrier to prevent commoning reads
  // from this field across safepoint since GC can change its value.
  bool need_read_barrier = ShenandoahKeepAliveBarrier &&
    (on_heap && (on_weak || (unknown && offset != top && obj != top)));

  if (!access.is_oop() || !need_read_barrier) {
    return load;
  }

  assert(access.is_parse_access(), "entry not supported at optimization time");
  C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);
  GraphKit* kit = parse_access.kit();

  if (on_weak) {
    // Use the pre-barrier to record the value in the referent field
    satb_write_barrier_pre(kit, false /* do_load */,
                           NULL /* obj */, NULL /* adr */, max_juint /* alias_idx */, NULL /* val */, NULL /* val_type */,
                           load /* pre_val */, T_OBJECT);
    // Add memory barrier to prevent commoning reads from this field
    // across safepoint since GC can change its value.
    kit->insert_mem_bar(Op_MemBarCPUOrder);
  } else if (unknown) {
    // We do not require a mem bar inside pre_barrier if need_mem_bar
    // is set: the barriers would be emitted by us.
    insert_pre_barrier(kit, obj, offset, load, !need_cpu_mem_bar);
  }

  return load;
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                   Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  if (access.is_oop()) {
    new_val = shenandoah_storeval_barrier(kit, new_val);
    shenandoah_write_barrier_pre(kit, false /* do_load */,
                                 NULL, NULL, max_juint, NULL, NULL,
                                 expected_val /* pre_val */, T_OBJECT);

    MemNode::MemOrd mo = access.mem_node_mo();
    Node* mem = access.memory();
    Node* adr = access.addr().node();
    const TypePtr* adr_type = access.addr().type();
    Node* load_store = NULL;

#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      Node *newval_enc = kit->gvn().transform(new EncodePNode(new_val, new_val->bottom_type()->make_narrowoop()));
      Node *oldval_enc = kit->gvn().transform(new EncodePNode(expected_val, expected_val->bottom_type()->make_narrowoop()));
      load_store = kit->gvn().transform(new ShenandoahCompareAndExchangeNNode(kit->control(), mem, adr, newval_enc, oldval_enc, adr_type, value_type->make_narrowoop(), mo));
    } else
#endif
    {
      load_store = kit->gvn().transform(new ShenandoahCompareAndExchangePNode(kit->control(), mem, adr, new_val, expected_val, adr_type, value_type->is_oopptr(), mo));
    }

    access.set_raw_access(load_store);
    pin_atomic_op(access);

#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      return kit->gvn().transform(new DecodeNNode(load_store, load_store->get_ptr_type()));
    }
#endif
    return load_store;
  }
  return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                              Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  if (access.is_oop()) {
    new_val = shenandoah_storeval_barrier(kit, new_val);
    shenandoah_write_barrier_pre(kit, false /* do_load */,
                                 NULL, NULL, max_juint, NULL, NULL,
                                 expected_val /* pre_val */, T_OBJECT);
    DecoratorSet decorators = access.decorators();
    MemNode::MemOrd mo = access.mem_node_mo();
    Node* mem = access.memory();
    bool is_weak_cas = (decorators & C2_WEAK_CMPXCHG) != 0;
    Node* load_store = NULL;
    Node* adr = access.addr().node();
#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      Node *newval_enc = kit->gvn().transform(new EncodePNode(new_val, new_val->bottom_type()->make_narrowoop()));
      Node *oldval_enc = kit->gvn().transform(new EncodePNode(expected_val, expected_val->bottom_type()->make_narrowoop()));
      if (is_weak_cas) {
        load_store = kit->gvn().transform(new ShenandoahWeakCompareAndSwapNNode(kit->control(), mem, adr, newval_enc, oldval_enc, mo));
      } else {
        load_store = kit->gvn().transform(new ShenandoahCompareAndSwapNNode(kit->control(), mem, adr, newval_enc, oldval_enc, mo));
      }
    } else
#endif
    {
      if (is_weak_cas) {
        load_store = kit->gvn().transform(new ShenandoahWeakCompareAndSwapPNode(kit->control(), mem, adr, new_val, expected_val, mo));
      } else {
        load_store = kit->gvn().transform(new ShenandoahCompareAndSwapPNode(kit->control(), mem, adr, new_val, expected_val, mo));
      }
    }
    access.set_raw_access(load_store);
    pin_atomic_op(access);
    return load_store;
  }
  return BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* val, const Type* value_type) const {
  GraphKit* kit = access.kit();
  if (access.is_oop()) {
    val = shenandoah_storeval_barrier(kit, val);
  }
  Node* result = BarrierSetC2::atomic_xchg_at_resolved(access, val, value_type);
  if (access.is_oop()) {
    shenandoah_write_barrier_pre(kit, false /* do_load */,
                                 NULL, NULL, max_juint, NULL, NULL,
                                 result /* pre_val */, T_OBJECT);
  }
  return result;
}

void ShenandoahBarrierSetC2::clone(GraphKit* kit, Node* src, Node* dst, Node* size, bool is_array) const {
  assert(!src->is_AddP(), "unexpected input");
  src = shenandoah_read_barrier(kit, src);
  BarrierSetC2::clone(kit, src, dst, size, is_array);
}

Node* ShenandoahBarrierSetC2::resolve(GraphKit* kit, Node* n, DecoratorSet decorators) const {
  bool is_write = decorators & ACCESS_WRITE;
  if (is_write) {
    return shenandoah_write_barrier(kit, n);
  } else {
  return shenandoah_read_barrier(kit, n);
  }
}

Node* ShenandoahBarrierSetC2::obj_allocate(PhaseMacroExpand* macro, Node* ctrl, Node* mem, Node* toobig_false, Node* size_in_bytes,
                                           Node*& i_o, Node*& needgc_ctrl,
                                           Node*& fast_oop_ctrl, Node*& fast_oop_rawmem,
                                           intx prefetch_lines) const {
  PhaseIterGVN& igvn = macro->igvn();

  // Allocate several words more for the Shenandoah brooks pointer.
  size_in_bytes = new AddXNode(size_in_bytes, igvn.MakeConX(ShenandoahBrooksPointer::byte_size()));
  macro->transform_later(size_in_bytes);

  Node* fast_oop = BarrierSetC2::obj_allocate(macro, ctrl, mem, toobig_false, size_in_bytes,
                                              i_o, needgc_ctrl, fast_oop_ctrl, fast_oop_rawmem,
                                              prefetch_lines);

  // Bump up object for Shenandoah brooks pointer.
  fast_oop = new AddPNode(macro->top(), fast_oop, igvn.MakeConX(ShenandoahBrooksPointer::byte_size()));
  macro->transform_later(fast_oop);

  // Initialize Shenandoah brooks pointer to point to the object itself.
  fast_oop_rawmem = macro->make_store(fast_oop_ctrl, fast_oop_rawmem, fast_oop, ShenandoahBrooksPointer::byte_offset(), fast_oop, T_OBJECT);

  return fast_oop;
}

// Support for GC barriers emitted during parsing
bool ShenandoahBarrierSetC2::is_gc_barrier_node(Node* node) const {
  if (node->Opcode() != Op_CallLeaf && node->Opcode() != Op_CallLeafNoFP) {
    return false;
  }
  CallLeafNode *call = node->as_CallLeaf();
  if (call->_name == NULL) {
    return false;
  }

  return strcmp(call->_name, "shenandoah_clone_barrier") == 0 ||
         strcmp(call->_name, "shenandoah_cas_obj") == 0 ||
         strcmp(call->_name, "shenandoah_wb_pre") == 0;
}

Node* ShenandoahBarrierSetC2::step_over_gc_barrier(Node* c) const {
  return ShenandoahBarrierNode::skip_through_barrier(c);
}

bool ShenandoahBarrierSetC2::expand_barriers(Compile* C, PhaseIterGVN& igvn) const {
  return !ShenandoahWriteBarrierNode::expand(C, igvn);
}

bool ShenandoahBarrierSetC2::optimize_loops(PhaseIdealLoop* phase, LoopOptsMode mode, VectorSet& visited, Node_Stack& nstack, Node_List& worklist) const {
  if (mode == LoopOptsShenandoahExpand) {
    assert(UseShenandoahGC, "only for shenandoah");
    ShenandoahWriteBarrierNode::pin_and_expand(phase);
    return true;
  } else if (mode == LoopOptsShenandoahPostExpand) {
    assert(UseShenandoahGC, "only for shenandoah");
    visited.Clear();
    ShenandoahWriteBarrierNode::optimize_after_expansion(visited, nstack, worklist, phase);
    return true;
  }
  GrowableArray<MemoryGraphFixer*> memory_graph_fixers;
  ShenandoahWriteBarrierNode::optimize_before_expansion(phase, memory_graph_fixers, false);
  return false;
}

bool ShenandoahBarrierSetC2::array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, ArrayCopyPhase phase) const {
  bool is_oop = type == T_OBJECT || type == T_ARRAY;
  if (!is_oop) {
    return false;
  }

  if (tightly_coupled_alloc) {
    if (phase == Optimization) {
      return false;
    }
    return !is_clone;
  }
  if (phase == Optimization) {
    return !ShenandoahStoreValEnqueueBarrier;
  }
  return true;
}

bool ShenandoahBarrierSetC2::clone_needs_postbarrier(ArrayCopyNode *ac, PhaseIterGVN& igvn) {
  Node* src = ac->in(ArrayCopyNode::Src);
  const TypeOopPtr* src_type = igvn.type(src)->is_oopptr();
  if (src_type->isa_instptr() != NULL) {
    ciInstanceKlass* ik = src_type->klass()->as_instance_klass();
    if ((src_type->klass_is_exact() || (!ik->is_interface() && !ik->has_subklass())) && !ik->has_injected_fields()) {
      if (ik->has_object_fields()) {
        return true;
      } else {
        if (!src_type->klass_is_exact()) {
          igvn.C->dependencies()->assert_leaf_type(ik);
        }
      }
    } else {
      return true;
    }
  } else if (src_type->isa_aryptr()) {
    BasicType src_elem  = src_type->klass()->as_array_klass()->element_type()->basic_type();
    if (src_elem == T_OBJECT || src_elem == T_ARRAY) {
      return true;
    }
  } else {
    return true;
  }
  return false;
}

void ShenandoahBarrierSetC2::clone_barrier_at_expansion(ArrayCopyNode* ac, Node* call, PhaseIterGVN& igvn) const {
  assert(ac->is_clonebasic(), "no other kind of arraycopy here");

  if (!clone_needs_postbarrier(ac, igvn)) {
    BarrierSetC2::clone_barrier_at_expansion(ac, call, igvn);
    return;
  }

  const TypePtr* raw_adr_type = TypeRawPtr::BOTTOM;
  Node* c = new ProjNode(call,TypeFunc::Control);
  c = igvn.transform(c);
  Node* m = new ProjNode(call, TypeFunc::Memory);
  m = igvn.transform(m);

  Node* dest = ac->in(ArrayCopyNode::Dest);
  assert(dest->is_AddP(), "bad input");
  Node* barrier_call = new CallLeafNode(ShenandoahBarrierSetC2::shenandoah_clone_barrier_Type(),
                                        CAST_FROM_FN_PTR(address, ShenandoahRuntime::shenandoah_clone_barrier),
                                        "shenandoah_clone_barrier", raw_adr_type);
  barrier_call->init_req(TypeFunc::Control, c);
  barrier_call->init_req(TypeFunc::I_O    , igvn.C->top());
  barrier_call->init_req(TypeFunc::Memory , m);
  barrier_call->init_req(TypeFunc::ReturnAdr, igvn.C->top());
  barrier_call->init_req(TypeFunc::FramePtr, igvn.C->top());
  barrier_call->init_req(TypeFunc::Parms+0, dest->in(AddPNode::Base));

  barrier_call = igvn.transform(barrier_call);
  c = new ProjNode(barrier_call,TypeFunc::Control);
  c = igvn.transform(c);
  m = new ProjNode(barrier_call, TypeFunc::Memory);
  m = igvn.transform(m);

  Node* out_c = ac->proj_out(TypeFunc::Control);
  Node* out_m = ac->proj_out(TypeFunc::Memory);
  igvn.replace_node(out_c, c);
  igvn.replace_node(out_m, m);
}


// Support for macro expanded GC barriers
void ShenandoahBarrierSetC2::register_potential_barrier_node(Node* node) const {
  if (node->Opcode() == Op_ShenandoahWriteBarrier) {
    state()->add_shenandoah_barrier((ShenandoahWriteBarrierNode*) node);
  }
}

void ShenandoahBarrierSetC2::unregister_potential_barrier_node(Node* node) const {
  if (node->Opcode() == Op_ShenandoahWriteBarrier) {
    state()->remove_shenandoah_barrier((ShenandoahWriteBarrierNode*) node);
  }
}

void ShenandoahBarrierSetC2::eliminate_gc_barrier(PhaseMacroExpand* macro, Node* n) const {
  if (is_shenandoah_wb_pre_call(n)) {
    shenandoah_eliminate_wb_pre(n, &macro->igvn());
  }
}

void ShenandoahBarrierSetC2::shenandoah_eliminate_wb_pre(Node* call, PhaseIterGVN* igvn) const {
  assert(UseShenandoahGC && is_shenandoah_wb_pre_call(call), "");
  Node* c = call->as_Call()->proj_out(TypeFunc::Control);
  c = c->unique_ctrl_out();
  assert(c->is_Region() && c->req() == 3, "where's the pre barrier control flow?");
  c = c->unique_ctrl_out();
  assert(c->is_Region() && c->req() == 3, "where's the pre barrier control flow?");
  Node* iff = c->in(1)->is_IfProj() ? c->in(1)->in(0) : c->in(2)->in(0);
  assert(iff->is_If(), "expect test");
  if (!is_shenandoah_marking_if(igvn, iff)) {
    c = c->unique_ctrl_out();
    assert(c->is_Region() && c->req() == 3, "where's the pre barrier control flow?");
    iff = c->in(1)->is_IfProj() ? c->in(1)->in(0) : c->in(2)->in(0);
    assert(is_shenandoah_marking_if(igvn, iff), "expect marking test");
  }
  Node* cmpx = iff->in(1)->in(1);
  igvn->replace_node(cmpx, igvn->makecon(TypeInt::CC_EQ));
  igvn->rehash_node_delayed(call);
  call->del_req(call->req()-1);
}

void ShenandoahBarrierSetC2::enqueue_useful_gc_barrier(PhaseIterGVN* igvn, Node* node) const {
  if (node->Opcode() == Op_AddP && ShenandoahBarrierSetC2::has_only_shenandoah_wb_pre_uses(node)) {
    igvn->add_users_to_worklist(node);
  }
}

void ShenandoahBarrierSetC2::eliminate_useless_gc_barriers(Unique_Node_List &useful, Compile* C) const {
  for (uint i = 0; i < useful.size(); i++) {
    Node* n = useful.at(i);
    if (n->Opcode() == Op_AddP && ShenandoahBarrierSetC2::has_only_shenandoah_wb_pre_uses(n)) {
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        C->record_for_igvn(n->fast_out(i));
      }
    }
  }
  for (int i = state()->shenandoah_barriers_count()-1; i >= 0; i--) {
    ShenandoahWriteBarrierNode* n = state()->shenandoah_barrier(i);
    if (!useful.member(n)) {
      state()->remove_shenandoah_barrier(n);
    }
  }

}

bool ShenandoahBarrierSetC2::has_special_unique_user(const Node* node) const {
  assert(node->outcnt() == 1, "match only for unique out");
  Node* n = node->unique_out();
  return node->Opcode() == Op_ShenandoahWriteBarrier && n->Opcode() == Op_ShenandoahWBMemProj;
}

void ShenandoahBarrierSetC2::add_users_to_worklist(Unique_Node_List* worklist) const {}

void* ShenandoahBarrierSetC2::create_barrier_state(Arena* comp_arena) const {
  return new(comp_arena) ShenandoahBarrierSetC2State(comp_arena);
}

ShenandoahBarrierSetC2State* ShenandoahBarrierSetC2::state() const {
  return reinterpret_cast<ShenandoahBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

// If the BarrierSetC2 state has kept macro nodes in its compilation unit state to be
// expanded later, then now is the time to do so.
bool ShenandoahBarrierSetC2::expand_macro_nodes(PhaseMacroExpand* macro) const { return false; }

#ifdef ASSERT
void ShenandoahBarrierSetC2::verify_gc_barriers(Compile* compile, CompilePhase phase) const {
  if (ShenandoahVerifyOptoBarriers && phase == BarrierSetC2::BeforeExpand) {
    ShenandoahBarrierNode::verify(Compile::current()->root());
  } else if (phase == BarrierSetC2::BeforeCodeGen) {
    // Verify G1 pre-barriers
    const int marking_offset = in_bytes(ShenandoahThreadLocalData::satb_mark_queue_active_offset());

    ResourceArea *area = Thread::current()->resource_area();
    Unique_Node_List visited(area);
    Node_List worklist(area);
    // We're going to walk control flow backwards starting from the Root
    worklist.push(compile->root());
    while (worklist.size() > 0) {
      Node *x = worklist.pop();
      if (x == NULL || x == compile->top()) continue;
      if (visited.member(x)) {
        continue;
      } else {
        visited.push(x);
      }

      if (x->is_Region()) {
        for (uint i = 1; i < x->req(); i++) {
          worklist.push(x->in(i));
        }
      } else {
        worklist.push(x->in(0));
        // We are looking for the pattern:
        //                            /->ThreadLocal
        // If->Bool->CmpI->LoadB->AddP->ConL(marking_offset)
        //              \->ConI(0)
        // We want to verify that the If and the LoadB have the same control
        // See GraphKit::g1_write_barrier_pre()
        if (x->is_If()) {
          IfNode *iff = x->as_If();
          if (iff->in(1)->is_Bool() && iff->in(1)->in(1)->is_Cmp()) {
            CmpNode *cmp = iff->in(1)->in(1)->as_Cmp();
            if (cmp->Opcode() == Op_CmpI && cmp->in(2)->is_Con() && cmp->in(2)->bottom_type()->is_int()->get_con() == 0
                && cmp->in(1)->is_Load()) {
              LoadNode *load = cmp->in(1)->as_Load();
              if (load->Opcode() == Op_LoadB && load->in(2)->is_AddP() && load->in(2)->in(2)->Opcode() == Op_ThreadLocal
                  && load->in(2)->in(3)->is_Con()
                  && load->in(2)->in(3)->bottom_type()->is_intptr_t()->get_con() == marking_offset) {

                Node *if_ctrl = iff->in(0);
                Node *load_ctrl = load->in(0);

                if (if_ctrl != load_ctrl) {
                  // Skip possible CProj->NeverBranch in infinite loops
                  if ((if_ctrl->is_Proj() && if_ctrl->Opcode() == Op_CProj)
                      && (if_ctrl->in(0)->is_MultiBranch() && if_ctrl->in(0)->Opcode() == Op_NeverBranch)) {
                    if_ctrl = if_ctrl->in(0)->in(0);
                  }
                }
                assert(load_ctrl != NULL && if_ctrl == load_ctrl, "controls must match");
              }
            }
          }
        }
      }
    }
  }
}
#endif

Node* ShenandoahBarrierSetC2::ideal_node(PhaseGVN* phase, Node* n, bool can_reshape) const {
  if (is_shenandoah_wb_pre_call(n)) {
    uint cnt = ShenandoahBarrierSetC2::write_ref_field_pre_entry_Type()->domain()->cnt();
    if (n->req() > cnt) {
      Node* addp = n->in(cnt);
      if (has_only_shenandoah_wb_pre_uses(addp)) {
        n->del_req(cnt);
        if (can_reshape) {
          phase->is_IterGVN()->_worklist.push(addp);
        }
        return n;
      }
    }
  }
  if (n->Opcode() == Op_CmpP) {
    Node* in1 = n->in(1);
    Node* in2 = n->in(2);
    if (in1->bottom_type() == TypePtr::NULL_PTR) {
      in2 = step_over_gc_barrier(in2);
    }
    if (in2->bottom_type() == TypePtr::NULL_PTR) {
      in1 = step_over_gc_barrier(in1);
    }
    PhaseIterGVN* igvn = phase->is_IterGVN();
    if (in1 != n->in(1)) {
      if (igvn != NULL) {
        n->set_req_X(1, in1, igvn);
      } else {
        n->set_req(1, in1);
      }
      assert(in2 == n->in(2), "only one change");
      return n;
    }
    if (in2 != n->in(2)) {
      if (igvn != NULL) {
        n->set_req_X(2, in2, igvn);
      } else {
        n->set_req(2, in2);
      }
      return n;
    }
  } else if (can_reshape &&
             n->Opcode() == Op_If &&
             ShenandoahWriteBarrierNode::is_heap_stable_test(n) &&
             n->in(0) != NULL) {
    Node* dom = n->in(0);
    Node* prev_dom = n;
    int op = n->Opcode();
    int dist = 16;
    // Search up the dominator tree for another heap stable test
    while (dom->Opcode() != op    ||  // Not same opcode?
           !ShenandoahWriteBarrierNode::is_heap_stable_test(dom) ||  // Not same input 1?
           prev_dom->in(0) != dom) {  // One path of test does not dominate?
      if (dist < 0) return NULL;

      dist--;
      prev_dom = dom;
      dom = IfNode::up_one_dom(dom);
      if (!dom) return NULL;
    }

    // Check that we did not follow a loop back to ourselves
    if (n == dom) {
      return NULL;
    }

    return n->as_If()->dominated_by(prev_dom, phase->is_IterGVN());
  }

  return NULL;
}

Node* ShenandoahBarrierSetC2::identity_node(PhaseGVN* phase, Node* n) const {
  if (n->is_Load()) {
    Node *mem = n->in(MemNode::Memory);
    Node *value = n->as_Load()->can_see_stored_value(mem, phase);
    if (value) {
      PhaseIterGVN *igvn = phase->is_IterGVN();
      if (igvn != NULL &&
          value->is_Phi() &&
          value->req() > 2 &&
          value->in(1) != NULL &&
          value->in(1)->is_ShenandoahBarrier()) {
        if (igvn->_worklist.member(value) ||
            igvn->_worklist.member(value->in(0)) ||
            (value->in(0)->in(1) != NULL &&
             value->in(0)->in(1)->is_IfProj() &&
             (igvn->_worklist.member(value->in(0)->in(1)) ||
              (value->in(0)->in(1)->in(0) != NULL &&
               igvn->_worklist.member(value->in(0)->in(1)->in(0)))))) {
          igvn->_worklist.push(n);
          return n;
        }
      }
      // (This works even when value is a Con, but LoadNode::Value
      // usually runs first, producing the singleton type of the Con.)
      Node *value_no_barrier = step_over_gc_barrier(value->Opcode() == Op_EncodeP ? value->in(1) : value);
      if (value->Opcode() == Op_EncodeP) {
        if (value_no_barrier != value->in(1)) {
          Node *encode = value->clone();
          encode->set_req(1, value_no_barrier);
          encode = phase->transform(encode);
          return encode;
        }
      } else {
        return value_no_barrier;
      }
    }
  }
  return n;
}

bool ShenandoahBarrierSetC2::has_only_shenandoah_wb_pre_uses(Node* n) {
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* u = n->fast_out(i);
    if (!is_shenandoah_wb_pre_call(u)) {
      return false;
    }
  }
  return n->outcnt() > 0;
}

bool ShenandoahBarrierSetC2::flatten_gc_alias_type(const TypePtr*& adr_type) const {
  int offset = adr_type->offset();
  if (offset == ShenandoahBrooksPointer::byte_offset()) {
    if (adr_type->isa_aryptr()) {
      adr_type = TypeAryPtr::make(adr_type->ptr(), adr_type->isa_aryptr()->ary(), adr_type->isa_aryptr()->klass(), false, offset);
    } else if (adr_type->isa_instptr()) {
      adr_type = TypeInstPtr::make(adr_type->ptr(), ciEnv::current()->Object_klass(), false, NULL, offset);
    }
    return true;
  } else {
    return false;
  }
}

bool ShenandoahBarrierSetC2::final_graph_reshaping(Compile* compile, Node* n, uint opcode) const {
  switch (opcode) {
    case Op_CallLeaf:
    case Op_CallLeafNoFP: {
      assert (n->is_Call(), "");
      CallNode *call = n->as_Call();
      if (ShenandoahBarrierSetC2::is_shenandoah_wb_pre_call(call)) {
        uint cnt = ShenandoahBarrierSetC2::write_ref_field_pre_entry_Type()->domain()->cnt();
        if (call->req() > cnt) {
          assert(call->req() == cnt + 1, "only one extra input");
          Node *addp = call->in(cnt);
          assert(!ShenandoahBarrierSetC2::has_only_shenandoah_wb_pre_uses(addp), "useless address computation?");
          call->del_req(cnt);
        }
      }
      return false;
    }
    case Op_ShenandoahCompareAndSwapP:
    case Op_ShenandoahCompareAndSwapN:
    case Op_ShenandoahWeakCompareAndSwapN:
    case Op_ShenandoahWeakCompareAndSwapP:
    case Op_ShenandoahCompareAndExchangeP:
    case Op_ShenandoahCompareAndExchangeN:
#ifdef ASSERT
      if( VerifyOptoOopOffsets ) {
        MemNode* mem  = n->as_Mem();
        // Check to see if address types have grounded out somehow.
        const TypeInstPtr *tp = mem->in(MemNode::Address)->bottom_type()->isa_instptr();
        ciInstanceKlass *k = tp->klass()->as_instance_klass();
        bool oop_offset_is_sane = k->contains_field_offset(tp->offset());
        assert( !tp || oop_offset_is_sane, "" );
      }
#endif
      return true;
    case Op_ShenandoahReadBarrier:
      return true;
    case Op_ShenandoahWriteBarrier:
      assert(false, "should have been expanded already");
      return true;
    default:
      return false;
  }
}

#ifdef ASSERT
bool ShenandoahBarrierSetC2::verify_gc_alias_type(const TypePtr* adr_type, int offset) const {
  if (offset == ShenandoahBrooksPointer::byte_offset() &&
      (adr_type->base() == Type::AryPtr || adr_type->base() == Type::OopPtr)) {
    return true;
  } else {
    return false;
  }
}
#endif

bool ShenandoahBarrierSetC2::escape_add_to_con_graph(ConnectionGraph* conn_graph, PhaseGVN* gvn, Unique_Node_List* delayed_worklist, Node* n, uint opcode) const {
  switch (opcode) {
    case Op_ShenandoahCompareAndExchangeP:
    case Op_ShenandoahCompareAndExchangeN:
      conn_graph->add_objload_to_connection_graph(n, delayed_worklist);
      // fallthrough
    case Op_ShenandoahWeakCompareAndSwapP:
    case Op_ShenandoahWeakCompareAndSwapN:
    case Op_ShenandoahCompareAndSwapP:
    case Op_ShenandoahCompareAndSwapN:
      conn_graph->add_to_congraph_unsafe_access(n, opcode, delayed_worklist);
      return true;
    case Op_StoreP: {
      Node* adr = n->in(MemNode::Address);
      const Type* adr_type = gvn->type(adr);
      // Pointer stores in G1 barriers looks like unsafe access.
      // Ignore such stores to be able scalar replace non-escaping
      // allocations.
      if (adr_type->isa_rawptr() && adr->is_AddP()) {
        Node* base = conn_graph->get_addp_base(adr);
        if (base->Opcode() == Op_LoadP &&
          base->in(MemNode::Address)->is_AddP()) {
          adr = base->in(MemNode::Address);
          Node* tls = conn_graph->get_addp_base(adr);
          if (tls->Opcode() == Op_ThreadLocal) {
             int offs = (int) gvn->find_intptr_t_con(adr->in(AddPNode::Offset), Type::OffsetBot);
             const int buf_offset = in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset());
             if (offs == buf_offset) {
               return true; // Pre barrier previous oop value store.
             }
          }
        }
      }
      return false;
    }
    case Op_ShenandoahReadBarrier:
    case Op_ShenandoahWriteBarrier:
      // Barriers 'pass through' its arguments. I.e. what goes in, comes out.
      // It doesn't escape.
      conn_graph->add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(ShenandoahBarrierNode::ValueIn), delayed_worklist);
      break;
    case Op_ShenandoahEnqueueBarrier:
      conn_graph->add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(1), delayed_worklist);
      break;
    default:
      // Nothing
      break;
  }
  return false;
}

bool ShenandoahBarrierSetC2::escape_add_final_edges(ConnectionGraph* conn_graph, PhaseGVN* gvn, Node* n, uint opcode) const {
  switch (opcode) {
    case Op_ShenandoahCompareAndExchangeP:
    case Op_ShenandoahCompareAndExchangeN: {
      Node *adr = n->in(MemNode::Address);
      conn_graph->add_local_var_and_edge(n, PointsToNode::NoEscape, adr, NULL);
      // fallthrough
    }
    case Op_ShenandoahCompareAndSwapP:
    case Op_ShenandoahCompareAndSwapN:
    case Op_ShenandoahWeakCompareAndSwapP:
    case Op_ShenandoahWeakCompareAndSwapN:
      return conn_graph->add_final_edges_unsafe_access(n, opcode);
    case Op_ShenandoahReadBarrier:
    case Op_ShenandoahWriteBarrier:
      // Barriers 'pass through' its arguments. I.e. what goes in, comes out.
      // It doesn't escape.
      conn_graph->add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(ShenandoahBarrierNode::ValueIn), NULL);
      return true;
    case Op_ShenandoahEnqueueBarrier:
      conn_graph->add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(1), NULL);
      return true;
    default:
      // Nothing
      break;
  }
  return false;
}

bool ShenandoahBarrierSetC2::escape_has_out_with_unsafe_object(Node* n) const {
  return n->has_out_with(Op_ShenandoahCompareAndExchangeP) || n->has_out_with(Op_ShenandoahCompareAndExchangeN) ||
         n->has_out_with(Op_ShenandoahCompareAndSwapP, Op_ShenandoahCompareAndSwapN, Op_ShenandoahWeakCompareAndSwapP, Op_ShenandoahWeakCompareAndSwapN);

}

bool ShenandoahBarrierSetC2::escape_is_barrier_node(Node* n) const {
  return n->is_ShenandoahBarrier();
}

bool ShenandoahBarrierSetC2::matcher_find_shared_visit(Matcher* matcher, Matcher::MStack& mstack, Node* n, uint opcode, bool& mem_op, int& mem_addr_idx) const {
  switch (opcode) {
    case Op_ShenandoahReadBarrier:
      if (n->in(ShenandoahBarrierNode::ValueIn)->is_DecodeNarrowPtr()) {
        matcher->set_shared(n->in(ShenandoahBarrierNode::ValueIn)->in(1));
      }
      matcher->set_shared(n);
      return true;
    default:
      break;
  }
  return false;
}

bool ShenandoahBarrierSetC2::matcher_find_shared_post_visit(Matcher* matcher, Node* n, uint opcode) const {
  switch (opcode) {
    case Op_ShenandoahCompareAndExchangeP:
    case Op_ShenandoahCompareAndExchangeN:
    case Op_ShenandoahWeakCompareAndSwapP:
    case Op_ShenandoahWeakCompareAndSwapN:
    case Op_ShenandoahCompareAndSwapP:
    case Op_ShenandoahCompareAndSwapN: {   // Convert trinary to binary-tree
      Node* newval = n->in(MemNode::ValueIn);
      Node* oldval = n->in(LoadStoreConditionalNode::ExpectedIn);
      Node* pair = new BinaryNode(oldval, newval);
      n->set_req(MemNode::ValueIn,pair);
      n->del_req(LoadStoreConditionalNode::ExpectedIn);
      return true;
    }
    default:
      break;
  }
  return false;
}

bool ShenandoahBarrierSetC2::matcher_is_store_load_barrier(Node* x, uint xop) const {
  return xop == Op_ShenandoahCompareAndExchangeP ||
         xop == Op_ShenandoahCompareAndExchangeN ||
         xop == Op_ShenandoahWeakCompareAndSwapP ||
         xop == Op_ShenandoahWeakCompareAndSwapN ||
         xop == Op_ShenandoahCompareAndSwapN ||
         xop == Op_ShenandoahCompareAndSwapP;
}

void ShenandoahBarrierSetC2::igvn_add_users_to_worklist(PhaseIterGVN* igvn, Node* use) const {
  if (use->is_ShenandoahBarrier()) {
    for (DUIterator_Fast i2max, i2 = use->fast_outs(i2max); i2 < i2max; i2++) {
      Node* u = use->fast_out(i2);
      Node* cmp = use->find_out_with(Op_CmpP);
      if (u->Opcode() == Op_CmpP) {
        igvn->_worklist.push(cmp);
      }
    }
  }
}

void ShenandoahBarrierSetC2::ccp_analyze(PhaseCCP* ccp, Unique_Node_List& worklist, Node* use) const {
  if (use->is_ShenandoahBarrier()) {
    for (DUIterator_Fast i2max, i2 = use->fast_outs(i2max); i2 < i2max; i2++) {
      Node* p = use->fast_out(i2);
      if (p->Opcode() == Op_AddP) {
        for (DUIterator_Fast i3max, i3 = p->fast_outs(i3max); i3 < i3max; i3++) {
          Node* q = p->fast_out(i3);
          if (q->is_Load()) {
            if(q->bottom_type() != ccp->type(q)) {
              worklist.push(q);
            }
          }
        }
      }
    }
  }
}

Node* ShenandoahBarrierSetC2::split_if_pre(PhaseIdealLoop* phase, Node* n) const {
  if (n->Opcode() == Op_ShenandoahReadBarrier) {
    ((ShenandoahReadBarrierNode*)n)->try_move(phase);
  } else if (n->Opcode() == Op_ShenandoahWriteBarrier) {
    return ((ShenandoahWriteBarrierNode*)n)->try_split_thru_phi(phase);
  }

  return NULL;
}

bool ShenandoahBarrierSetC2::build_loop_late_post(PhaseIdealLoop* phase, Node* n) const {
  return ShenandoahBarrierNode::build_loop_late_post(phase, n);
}

bool ShenandoahBarrierSetC2::sink_node(PhaseIdealLoop* phase, Node* n, Node* x, Node* x_ctrl, Node* n_ctrl) const {
  if (n->is_ShenandoahBarrier()) {
    return x->as_ShenandoahBarrier()->sink_node(phase, x_ctrl, n_ctrl);
  }
  if (n->is_MergeMem()) {
    // PhaseIdealLoop::split_if_with_blocks_post() would:
    // _igvn._worklist.yank(x);
    // which sometimes causes chains of MergeMem which some of
    // shenandoah specific code doesn't support
    phase->register_new_node(x, x_ctrl);
    return true;
  }
  return false;
}
