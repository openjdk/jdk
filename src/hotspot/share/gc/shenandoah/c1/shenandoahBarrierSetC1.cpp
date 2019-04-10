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
#include "c1/c1_IR.hpp"
#include "gc/shared/satbMarkQueue.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahBrooksPointer.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"

#ifndef PATCHED_ADDR
#define PATCHED_ADDR  (max_jint)
#endif

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

void ShenandoahBarrierSetC1::pre_barrier(LIRGenerator* gen, CodeEmitInfo* info, DecoratorSet decorators, LIR_Opr addr_opr, LIR_Opr pre_val) {
  // First we test whether marking is in progress.
  BasicType flag_type;
  bool patch = (decorators & C1_NEEDS_PATCHING) != 0;
  bool do_load = pre_val == LIR_OprFact::illegalOpr;
  if (in_bytes(SATBMarkQueue::byte_width_of_active()) == 4) {
    flag_type = T_INT;
  } else {
    guarantee(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1,
              "Assumption");
    // Use unsigned type T_BOOLEAN here rather than signed T_BYTE since some platforms, eg. ARM,
    // need to use unsigned instructions to use the large offset to load the satb_mark_queue.
    flag_type = T_BOOLEAN;
  }
  LIR_Opr thrd = gen->getThreadPointer();
  LIR_Address* mark_active_flag_addr =
    new LIR_Address(thrd,
                    in_bytes(ShenandoahThreadLocalData::satb_mark_queue_active_offset()),
                    flag_type);
  // Read the marking-in-progress flag.
  LIR_Opr flag_val = gen->new_register(T_INT);
  __ load(mark_active_flag_addr, flag_val);
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
    slow = new ShenandoahPreBarrierStub(addr_opr, pre_val, pre_val_patch_code, info ? new CodeEmitInfo(info) : NULL);
  } else {
    assert(addr_opr == LIR_OprFact::illegalOpr, "sanity");
    assert(pre_val->is_register(), "must be");
    assert(pre_val->type() == T_OBJECT, "must be an object");

    slow = new ShenandoahPreBarrierStub(pre_val);
  }

  __ branch(lir_cond_notEqual, T_INT, slow);
  __ branch_destination(slow->continuation());
}

LIR_Opr ShenandoahBarrierSetC1::load_reference_barrier(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, bool need_null_check) {
  if (ShenandoahLoadRefBarrier) {
    return load_reference_barrier_impl(gen, obj, info, need_null_check);
  } else {
    return obj;
  }
}

LIR_Opr ShenandoahBarrierSetC1::load_reference_barrier_impl(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, bool need_null_check) {
  assert(ShenandoahLoadRefBarrier, "Should be enabled");

  obj = ensure_in_register(gen, obj);
  assert(obj->is_register(), "must be a register at this point");
  LIR_Opr result = gen->new_register(T_OBJECT);
  __ move(obj, result);

  LIR_Opr thrd = gen->getThreadPointer();
  LIR_Address* active_flag_addr =
    new LIR_Address(thrd,
                    in_bytes(ShenandoahThreadLocalData::gc_state_offset()),
                    T_BYTE);
  // Read and check the gc-state-flag.
  LIR_Opr flag_val = gen->new_register(T_INT);
  __ load(active_flag_addr, flag_val);
  LIR_Opr mask = LIR_OprFact::intConst(ShenandoahHeap::HAS_FORWARDED |
                                       ShenandoahHeap::EVACUATION |
                                       ShenandoahHeap::TRAVERSAL);
  LIR_Opr mask_reg = gen->new_register(T_INT);
  __ move(mask, mask_reg);

  if (TwoOperandLIRForm) {
    __ logical_and(flag_val, mask_reg, flag_val);
  } else {
    LIR_Opr masked_flag = gen->new_register(T_INT);
    __ logical_and(flag_val, mask_reg, masked_flag);
    flag_val = masked_flag;
  }
  __ cmp(lir_cond_notEqual, flag_val, LIR_OprFact::intConst(0));

  CodeStub* slow = new ShenandoahLoadReferenceBarrierStub(obj, result, info ? new CodeEmitInfo(info) : NULL, need_null_check);
  __ branch(lir_cond_notEqual, T_INT, slow);
  __ branch_destination(slow->continuation());

  return result;
}

LIR_Opr ShenandoahBarrierSetC1::ensure_in_register(LIRGenerator* gen, LIR_Opr obj) {
  if (!obj->is_register()) {
    LIR_Opr obj_reg = gen->new_register(T_OBJECT);
    if (obj->is_constant()) {
      __ move(obj, obj_reg);
    } else {
      __ leal(obj, obj_reg);
    }
    obj = obj_reg;
  }
  return obj;
}

LIR_Opr ShenandoahBarrierSetC1::storeval_barrier(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, DecoratorSet decorators) {
  if (ShenandoahStoreValEnqueueBarrier) {
    obj = ensure_in_register(gen, obj);
    pre_barrier(gen, info, decorators, LIR_OprFact::illegalOpr, obj);
  }
  return obj;
}

void ShenandoahBarrierSetC1::store_at_resolved(LIRAccess& access, LIR_Opr value) {
  if (access.is_oop()) {
    if (ShenandoahSATBBarrier) {
      pre_barrier(access.gen(), access.access_emit_info(), access.decorators(), access.resolved_addr(), LIR_OprFact::illegalOpr /* pre_val */);
    }
    value = storeval_barrier(access.gen(), value, access.access_emit_info(), access.decorators());
  }
  BarrierSetC1::store_at_resolved(access, value);
}

void ShenandoahBarrierSetC1::load_at_resolved(LIRAccess& access, LIR_Opr result) {
  if (!access.is_oop()) {
    BarrierSetC1::load_at_resolved(access, result);
    return;
  }

  LIRGenerator *gen = access.gen();

  if (ShenandoahLoadRefBarrier) {
    LIR_Opr tmp = gen->new_register(T_OBJECT);
    BarrierSetC1::load_at_resolved(access, tmp);
    tmp = load_reference_barrier(access.gen(), tmp, access.access_emit_info(), true);
    __ move(tmp, result);
  } else {
    BarrierSetC1::load_at_resolved(access, result);
  }

  if (ShenandoahKeepAliveBarrier) {
    DecoratorSet decorators = access.decorators();
    bool is_weak = (decorators & ON_WEAK_OOP_REF) != 0;
    bool is_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
    bool is_anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
    if (is_weak || is_phantom || is_anonymous) {
      // Register the value in the referent field with the pre-barrier
      LabelObj *Lcont_anonymous;
      if (is_anonymous) {
        Lcont_anonymous = new LabelObj();
        generate_referent_check(access, Lcont_anonymous);
      }
      pre_barrier(access.gen(), access.access_emit_info(), access.decorators(), LIR_OprFact::illegalOpr /* addr_opr */,
                  result /* pre_val */);
      if (is_anonymous) {
        __ branch_destination(Lcont_anonymous->label());
      }
    }
  }
}

class C1ShenandoahPreBarrierCodeGenClosure : public StubAssemblerCodeGenClosure {
  virtual OopMapSet* generate_code(StubAssembler* sasm) {
    ShenandoahBarrierSetAssembler* bs = (ShenandoahBarrierSetAssembler*)BarrierSet::barrier_set()->barrier_set_assembler();
    bs->generate_c1_pre_barrier_runtime_stub(sasm);
    return NULL;
  }
};

void ShenandoahBarrierSetC1::generate_c1_runtime_stubs(BufferBlob* buffer_blob) {
  C1ShenandoahPreBarrierCodeGenClosure pre_code_gen_cl;
  _pre_barrier_c1_runtime_code_blob = Runtime1::generate_blob(buffer_blob, -1,
                                                              "shenandoah_pre_barrier_slow",
                                                              false, &pre_code_gen_cl);
}
