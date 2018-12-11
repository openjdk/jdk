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

void ShenandoahWriteBarrierStub::emit_code(LIR_Assembler* ce) {
  ShenandoahBarrierSetAssembler* bs = (ShenandoahBarrierSetAssembler*)BarrierSet::barrier_set()->barrier_set_assembler();
  bs->gen_write_barrier_stub(ce, this);
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

LIR_Opr ShenandoahBarrierSetC1::read_barrier(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, bool need_null_check) {
  if (UseShenandoahGC && ShenandoahReadBarrier) {
    return read_barrier_impl(gen, obj, info, need_null_check);
  } else {
    return obj;
  }
}

LIR_Opr ShenandoahBarrierSetC1::read_barrier_impl(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, bool need_null_check) {
  assert(UseShenandoahGC && (ShenandoahReadBarrier || ShenandoahStoreValReadBarrier), "Should be enabled");
  LabelObj* done = new LabelObj();
  LIR_Opr result = gen->new_register(T_OBJECT);
  __ move(obj, result);
  if (need_null_check) {
    __ cmp(lir_cond_equal, result, LIR_OprFact::oopConst(NULL));
    __ branch(lir_cond_equal, T_LONG, done->label());
  }
  LIR_Address* brooks_ptr_address = gen->generate_address(result, ShenandoahBrooksPointer::byte_offset(), T_ADDRESS);
  __ load(brooks_ptr_address, result, info ? new CodeEmitInfo(info) : NULL, lir_patch_none);

  __ branch_destination(done->label());
  return result;
}

LIR_Opr ShenandoahBarrierSetC1::write_barrier(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, bool need_null_check) {
  if (UseShenandoahGC && ShenandoahWriteBarrier) {
    return write_barrier_impl(gen, obj, info, need_null_check);
  } else {
    return obj;
  }
}

LIR_Opr ShenandoahBarrierSetC1::write_barrier_impl(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, bool need_null_check) {
  assert(UseShenandoahGC && (ShenandoahWriteBarrier || ShenandoahStoreValEnqueueBarrier), "Should be enabled");

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

  CodeStub* slow = new ShenandoahWriteBarrierStub(obj, result, info ? new CodeEmitInfo(info) : NULL, need_null_check);
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
  bool need_null_check = (decorators & IS_NOT_NULL) == 0;
  if (ShenandoahStoreValEnqueueBarrier) {
    obj = write_barrier_impl(gen, obj, info, need_null_check);
    pre_barrier(gen, info, decorators, LIR_OprFact::illegalOpr, obj);
  }
  if (ShenandoahStoreValReadBarrier) {
    obj = read_barrier_impl(gen, obj, info, true /*need_null_check*/);
  }
  return obj;
}

LIR_Opr ShenandoahBarrierSetC1::resolve_address(LIRAccess& access, bool resolve_in_register) {
  DecoratorSet decorators = access.decorators();
  bool is_array = (decorators & IS_ARRAY) != 0;
  bool needs_patching = (decorators & C1_NEEDS_PATCHING) != 0;

  bool is_write = (decorators & ACCESS_WRITE) != 0;
  bool needs_null_check = (decorators & IS_NOT_NULL) == 0;

  LIR_Opr base = access.base().item().result();
  LIR_Opr offset = access.offset().opr();
  LIRGenerator* gen = access.gen();

  if (is_write) {
    base = write_barrier(gen, base, access.access_emit_info(), needs_null_check);
  } else {
    base = read_barrier(gen, base, access.access_emit_info(), needs_null_check);
  }

  LIR_Opr addr_opr;
  if (is_array) {
    addr_opr = LIR_OprFact::address(gen->emit_array_address(base, offset, access.type()));
  } else if (needs_patching) {
    // we need to patch the offset in the instruction so don't allow
    // generate_address to try to be smart about emitting the -1.
    // Otherwise the patching code won't know how to find the
    // instruction to patch.
    addr_opr = LIR_OprFact::address(new LIR_Address(base, PATCHED_ADDR, access.type()));
  } else {
    addr_opr = LIR_OprFact::address(gen->generate_address(base, offset, 0, 0, access.type()));
  }

  if (resolve_in_register) {
    LIR_Opr resolved_addr = gen->new_pointer_register();
    __ leal(addr_opr, resolved_addr);
    resolved_addr = LIR_OprFact::address(new LIR_Address(resolved_addr, access.type()));
    return resolved_addr;
  } else {
    return addr_opr;
  }
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
  BarrierSetC1::load_at_resolved(access, result);

  if (ShenandoahKeepAliveBarrier) {
    DecoratorSet decorators = access.decorators();
    bool is_weak = (decorators & ON_WEAK_OOP_REF) != 0;
    bool is_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
    bool is_anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
    LIRGenerator *gen = access.gen();
    if (access.is_oop() && (is_weak || is_phantom || is_anonymous)) {
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

LIR_Opr ShenandoahBarrierSetC1::atomic_add_at_resolved(LIRAccess& access, LIRItem& value) {
  return BarrierSetC1::atomic_add_at_resolved(access, value);
}

LIR_Opr ShenandoahBarrierSetC1::resolve(LIRGenerator* gen, DecoratorSet decorators, LIR_Opr obj) {
  bool is_write = decorators & ACCESS_WRITE;
  if (is_write) {
    return write_barrier(gen, obj, NULL, (decorators & IS_NOT_NULL) == 0);
  } else {
    return read_barrier(gen, obj, NULL, (decorators & IS_NOT_NULL) == 0);
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
