/*
 * Copyright (c) 2018, 2024, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "c1/c1_IR.hpp"
#include "gc/shared/satbMarkQueue.hpp"
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"

#ifdef ASSERT
#define __ gen->lir(__FILE__, __LINE__)->
#else
#define __ gen->lir()->
#endif

void ShenandoahPreBarrierStub::emit_code(LIR_Assembler* ce) {
  ShenandoahBarrierSetAssembler* bs = (ShenandoahBarrierSetAssembler*)BarrierSet::barrier_set()->barrier_set_assembler();
  bs->gen_pre_barrier_stub(ce, this);
}

void ShenandoahLoadReferenceBarrierStub::emit_code(LIR_Assembler* ce) {
  ShenandoahBarrierSetAssembler* bs = (ShenandoahBarrierSetAssembler*)BarrierSet::barrier_set()->barrier_set_assembler();
  bs->gen_load_reference_barrier_stub(ce, this);
}

ShenandoahBarrierSetC1::ShenandoahBarrierSetC1() :
  _pre_barrier_c1_runtime_code_blob(nullptr),
  _load_reference_barrier_strong_rt_code_blob(nullptr),
  _load_reference_barrier_strong_native_rt_code_blob(nullptr),
  _load_reference_barrier_weak_rt_code_blob(nullptr),
  _load_reference_barrier_phantom_rt_code_blob(nullptr) {}

void ShenandoahBarrierSetC1::pre_barrier(LIRGenerator* gen, CodeEmitInfo* info, DecoratorSet decorators, LIR_Opr addr_opr, LIR_Opr pre_val) {
  // First we test whether marking is in progress.

  bool patch = (decorators & C1_NEEDS_PATCHING) != 0;
  bool do_load = pre_val == LIR_OprFact::illegalOpr;

  LIR_Opr thrd = gen->getThreadPointer();
  LIR_Address* gc_state_addr =
          new LIR_Address(thrd,
                          in_bytes(ShenandoahThreadLocalData::gc_state_offset()),
                          T_BYTE);
  // Read the gc_state flag.
  LIR_Opr flag_val = gen->new_register(T_INT);
  __ load(gc_state_addr, flag_val);

  // Create a mask to test if the marking bit is set.
  LIR_Opr mask = LIR_OprFact::intConst(ShenandoahHeap::MARKING);
  LIR_Opr mask_reg = gen->new_register(T_INT);
  __ move(mask, mask_reg);

  if (two_operand_lir_form) {
    __ logical_and(flag_val, mask_reg, flag_val);
  } else {
    LIR_Opr masked_flag = gen->new_register(T_INT);
    __ logical_and(flag_val, mask_reg, masked_flag);
    flag_val = masked_flag;
  }
  __ cmp(lir_cond_notEqual, flag_val, LIR_OprFact::intConst(0));

  LIR_PatchCode pre_val_patch_code = lir_patch_none;

  CodeStub* slow;

  if (do_load) {
    assert(pre_val == LIR_OprFact::illegalOpr, "sanity");
    assert(addr_opr != LIR_OprFact::illegalOpr, "sanity");

    if (patch)
      pre_val_patch_code = lir_patch_normal;

    pre_val = gen->new_register(T_OBJECT);

    if (!addr_opr->is_address()) {
      assert(addr_opr->is_register(), "must be");
      addr_opr = LIR_OprFact::address(new LIR_Address(addr_opr, T_OBJECT));
    }
    slow = new ShenandoahPreBarrierStub(addr_opr, pre_val, pre_val_patch_code, info ? new CodeEmitInfo(info) : nullptr);
  } else {
    assert(addr_opr == LIR_OprFact::illegalOpr, "sanity");
    assert(pre_val->is_register(), "must be");
    assert(pre_val->type() == T_OBJECT, "must be an object");

    slow = new ShenandoahPreBarrierStub(pre_val);
  }

  __ branch(lir_cond_notEqual, slow);
  __ branch_destination(slow->continuation());
}

LIR_Opr ShenandoahBarrierSetC1::load_reference_barrier(LIRGenerator* gen, LIR_Opr obj, LIR_Opr addr, DecoratorSet decorators) {
  if (ShenandoahLoadRefBarrier) {
    return load_reference_barrier_impl(gen, obj, addr, decorators);
  } else {
    return obj;
  }
}

LIR_Opr ShenandoahBarrierSetC1::load_reference_barrier_impl(LIRGenerator* gen, LIR_Opr obj, LIR_Opr addr, DecoratorSet decorators) {
  assert(ShenandoahLoadRefBarrier, "Should be enabled");

  obj = ensure_in_register(gen, obj, T_OBJECT);
  assert(obj->is_register(), "must be a register at this point");
  addr = ensure_in_register(gen, addr, T_ADDRESS);
  assert(addr->is_register(), "must be a register at this point");
  LIR_Opr result = gen->result_register_for(obj->value_type());
  __ move(obj, result);
  LIR_Opr tmp1 = gen->new_register(T_ADDRESS);
  LIR_Opr tmp2 = gen->new_register(T_ADDRESS);

  LIR_Opr thrd = gen->getThreadPointer();
  LIR_Address* active_flag_addr =
    new LIR_Address(thrd,
                    in_bytes(ShenandoahThreadLocalData::gc_state_offset()),
                    T_BYTE);
  // Read and check the gc-state-flag.
  LIR_Opr flag_val = gen->new_register(T_INT);
  __ load(active_flag_addr, flag_val);
  int flags = ShenandoahHeap::HAS_FORWARDED;
  if (!ShenandoahBarrierSet::is_strong_access(decorators)) {
    flags |= ShenandoahHeap::WEAK_ROOTS;
  }
  LIR_Opr mask = LIR_OprFact::intConst(flags);
  LIR_Opr mask_reg = gen->new_register(T_INT);
  __ move(mask, mask_reg);

  if (two_operand_lir_form) {
    __ logical_and(flag_val, mask_reg, flag_val);
  } else {
    LIR_Opr masked_flag = gen->new_register(T_INT);
    __ logical_and(flag_val, mask_reg, masked_flag);
    flag_val = masked_flag;
  }
  __ cmp(lir_cond_notEqual, flag_val, LIR_OprFact::intConst(0));

  CodeStub* slow = new ShenandoahLoadReferenceBarrierStub(obj, addr, result, tmp1, tmp2, decorators);
  __ branch(lir_cond_notEqual, slow);
  __ branch_destination(slow->continuation());

  return result;
}

LIR_Opr ShenandoahBarrierSetC1::ensure_in_register(LIRGenerator* gen, LIR_Opr obj, BasicType type) {
  if (!obj->is_register()) {
    LIR_Opr obj_reg;
    if (obj->is_constant()) {
      obj_reg = gen->new_register(type);
      __ move(obj, obj_reg);
    } else {
      obj_reg = gen->new_pointer_register();
      __ leal(obj, obj_reg);
    }
    obj = obj_reg;
  }
  return obj;
}

void ShenandoahBarrierSetC1::store_at_resolved(LIRAccess& access, LIR_Opr value) {
  if (access.is_oop()) {
    if (ShenandoahSATBBarrier) {
      pre_barrier(access.gen(), access.access_emit_info(), access.decorators(), access.resolved_addr(), LIR_OprFact::illegalOpr /* pre_val */);
    }
  }
  BarrierSetC1::store_at_resolved(access, value);

  if (ShenandoahCardBarrier && access.is_oop()) {
    DecoratorSet decorators = access.decorators();
    bool is_array = (decorators & IS_ARRAY) != 0;
    bool on_anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;

    bool precise = is_array || on_anonymous;
    LIR_Opr post_addr = precise ? access.resolved_addr() : access.base().opr();
    post_barrier(access, post_addr, value);
  }
}

LIR_Opr ShenandoahBarrierSetC1::resolve_address(LIRAccess& access, bool resolve_in_register) {
  // We must resolve in register when patching. This is to avoid
  // having a patch area in the load barrier stub, since the call
  // into the runtime to patch will not have the proper oop map.
  const bool patch_before_barrier = access.is_oop() && (access.decorators() & C1_NEEDS_PATCHING) != 0;
  return BarrierSetC1::resolve_address(access, resolve_in_register || patch_before_barrier);
}

void ShenandoahBarrierSetC1::load_at_resolved(LIRAccess& access, LIR_Opr result) {
  // 1: non-reference load, no additional barrier is needed
  if (!access.is_oop()) {
    BarrierSetC1::load_at_resolved(access, result);
    return;
  }

  LIRGenerator* gen = access.gen();
  DecoratorSet decorators = access.decorators();
  BasicType type = access.type();

  // 2: load a reference from src location and apply LRB if ShenandoahLoadRefBarrier is set
  if (ShenandoahBarrierSet::need_load_reference_barrier(decorators, type)) {
    LIR_Opr tmp = gen->new_register(T_OBJECT);
    BarrierSetC1::load_at_resolved(access, tmp);
    tmp = load_reference_barrier(gen, tmp, access.resolved_addr(), decorators);
    __ move(tmp, result);
  } else {
    BarrierSetC1::load_at_resolved(access, result);
  }

  // 3: apply keep-alive barrier for java.lang.ref.Reference if needed
  if (ShenandoahBarrierSet::need_keep_alive_barrier(decorators, type)) {
    bool is_anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;

    // Register the value in the referent field with the pre-barrier
    LabelObj *Lcont_anonymous;
    if (is_anonymous) {
      Lcont_anonymous = new LabelObj();
      generate_referent_check(access, Lcont_anonymous);
    }
    pre_barrier(gen, access.access_emit_info(), decorators, LIR_OprFact::illegalOpr /* addr_opr */,
                result /* pre_val */);
    if (is_anonymous) {
      __ branch_destination(Lcont_anonymous->label());
    }
  }
}

class C1ShenandoahPreBarrierCodeGenClosure : public StubAssemblerCodeGenClosure {
  virtual OopMapSet* generate_code(StubAssembler* sasm) {
    ShenandoahBarrierSetAssembler* bs = (ShenandoahBarrierSetAssembler*)BarrierSet::barrier_set()->barrier_set_assembler();
    bs->generate_c1_pre_barrier_runtime_stub(sasm);
    return nullptr;
  }
};

class C1ShenandoahLoadReferenceBarrierCodeGenClosure : public StubAssemblerCodeGenClosure {
private:
  const DecoratorSet _decorators;

public:
  C1ShenandoahLoadReferenceBarrierCodeGenClosure(DecoratorSet decorators) : _decorators(decorators) {}

  virtual OopMapSet* generate_code(StubAssembler* sasm) {
    ShenandoahBarrierSetAssembler* bs = (ShenandoahBarrierSetAssembler*)BarrierSet::barrier_set()->barrier_set_assembler();
    bs->generate_c1_load_reference_barrier_runtime_stub(sasm, _decorators);
    return nullptr;
  }
};

bool ShenandoahBarrierSetC1::generate_c1_runtime_stubs(BufferBlob* buffer_blob) {
  C1ShenandoahPreBarrierCodeGenClosure pre_code_gen_cl;
  _pre_barrier_c1_runtime_code_blob = Runtime1::generate_blob(buffer_blob, StubId::NO_STUBID,
                                                              "shenandoah_pre_barrier_slow",
                                                              false, &pre_code_gen_cl);
  if (_pre_barrier_c1_runtime_code_blob == nullptr) {
    return false;
  }
  if (ShenandoahLoadRefBarrier) {
    C1ShenandoahLoadReferenceBarrierCodeGenClosure lrb_strong_code_gen_cl(ON_STRONG_OOP_REF);
    _load_reference_barrier_strong_rt_code_blob = Runtime1::generate_blob(buffer_blob, StubId::NO_STUBID,
                                                                          "shenandoah_load_reference_barrier_strong_slow",
                                                                          false, &lrb_strong_code_gen_cl);
    if (_load_reference_barrier_strong_rt_code_blob == nullptr) {
      return false;
    }

    C1ShenandoahLoadReferenceBarrierCodeGenClosure lrb_strong_native_code_gen_cl(ON_STRONG_OOP_REF | IN_NATIVE);
    _load_reference_barrier_strong_native_rt_code_blob = Runtime1::generate_blob(buffer_blob, StubId::NO_STUBID,
                                                                                 "shenandoah_load_reference_barrier_strong_native_slow",
                                                                                 false, &lrb_strong_native_code_gen_cl);
    if (_load_reference_barrier_strong_native_rt_code_blob == nullptr) {
      return false;
    }

    C1ShenandoahLoadReferenceBarrierCodeGenClosure lrb_weak_code_gen_cl(ON_WEAK_OOP_REF);
    _load_reference_barrier_weak_rt_code_blob = Runtime1::generate_blob(buffer_blob, StubId::NO_STUBID,
                                                                        "shenandoah_load_reference_barrier_weak_slow",
                                                                        false, &lrb_weak_code_gen_cl);
    if (_load_reference_barrier_weak_rt_code_blob == nullptr) {
      return false;
    }

    C1ShenandoahLoadReferenceBarrierCodeGenClosure lrb_phantom_code_gen_cl(ON_PHANTOM_OOP_REF | IN_NATIVE);
    _load_reference_barrier_phantom_rt_code_blob = Runtime1::generate_blob(buffer_blob, StubId::NO_STUBID,
                                                                           "shenandoah_load_reference_barrier_phantom_slow",
                                                                           false, &lrb_phantom_code_gen_cl);
    return (_load_reference_barrier_phantom_rt_code_blob != nullptr);
  }
  return true;
}

void ShenandoahBarrierSetC1::post_barrier(LIRAccess& access, LIR_Opr addr, LIR_Opr new_val) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");

  DecoratorSet decorators = access.decorators();
  LIRGenerator* gen = access.gen();
  bool in_heap = (decorators & IN_HEAP) != 0;
  if (!in_heap) {
    return;
  }

  LIR_Opr thrd = gen->getThreadPointer();
  const int curr_ct_holder_offset = in_bytes(ShenandoahThreadLocalData::card_table_offset());
  LIR_Address* curr_ct_holder_addr = new LIR_Address(thrd, curr_ct_holder_offset, T_ADDRESS);
  LIR_Opr curr_ct_holder_ptr_reg = gen->new_register(T_ADDRESS);
  __ move(curr_ct_holder_addr, curr_ct_holder_ptr_reg);

  if (addr->is_address()) {
    LIR_Address* address = addr->as_address_ptr();
    // ptr cannot be an object because we use this barrier for array card marks
    // and addr can point in the middle of an array.
    LIR_Opr ptr = gen->new_pointer_register();
    if (!address->index()->is_valid() && address->disp() == 0) {
      __ move(address->base(), ptr);
    } else {
      assert(address->disp() != max_jint, "lea doesn't support patched addresses!");
      __ leal(addr, ptr);
    }
    addr = ptr;
  }
  assert(addr->is_register(), "must be a register at this point");

  LIR_Opr tmp = gen->new_pointer_register();
  if (two_operand_lir_form) {
    __ move(addr, tmp);
    __ unsigned_shift_right(tmp, CardTable::card_shift(), tmp);
  } else {
    __ unsigned_shift_right(addr, CardTable::card_shift(), tmp);
  }

  LIR_Address* card_addr = new LIR_Address(curr_ct_holder_ptr_reg, tmp, T_BYTE);
  LIR_Opr dirty = LIR_OprFact::intConst(CardTable::dirty_card_val());
  if (UseCondCardMark) {
    LIR_Opr cur_value = gen->new_register(T_INT);
    __ move(card_addr, cur_value);

    LabelObj* L_already_dirty = new LabelObj();
    __ cmp(lir_cond_equal, cur_value, dirty);
    __ branch(lir_cond_equal, L_already_dirty->label());
    __ move(dirty, card_addr);
    __ branch_destination(L_already_dirty->label());
  } else {
    __ move(dirty, card_addr);
  }
}
