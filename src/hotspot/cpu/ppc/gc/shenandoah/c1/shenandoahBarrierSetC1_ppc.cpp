/*
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2012, 2021 SAP SE. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"

#define __ masm->masm()->

void LIR_OpShenandoahCompareAndSwap::emit_code(LIR_Assembler *masm) {
  __ block_comment("LIR_OpShenandoahCompareAndSwap (shenandaohgc) {");

  Register addr = _addr->as_register_lo();
  Register new_val = _new_value->as_register();
  Register cmp_val = _cmp_value->as_register();
  Register tmp1 = _tmp1->as_register();
  Register tmp2 = _tmp2->as_register();
  Register result = result_opr()->as_register();

  if (ShenandoahIUBarrier) {
    ShenandoahBarrierSet::assembler()->iu_barrier(masm->masm(), new_val, tmp1, tmp2,
                                                  MacroAssembler::PRESERVATION_FRAME_LR_GP_FP_REGS);
  }

  if (UseCompressedOops) {
    __ encode_heap_oop(cmp_val, cmp_val);
    __ encode_heap_oop(new_val, new_val);
  }

  // Due to the memory barriers emitted in ShenandoahBarrierSetC1::atomic_cmpxchg_at_resolved,
  // there is no need to specify stronger memory semantics.
  ShenandoahBarrierSet::assembler()->cmpxchg_oop(masm->masm(), addr, cmp_val, new_val, tmp1, tmp2,
                                                 false, result);

  if (UseCompressedOops) {
    __ decode_heap_oop(cmp_val);
    __ decode_heap_oop(new_val);
  }

  __ block_comment("} LIR_OpShenandoahCompareAndSwap (shenandaohgc)");
}

#undef __

#ifdef ASSERT
#define __ gen->lir(__FILE__, __LINE__)->
#else
#define __ gen->lir()->
#endif

LIR_Opr ShenandoahBarrierSetC1::atomic_cmpxchg_at_resolved(LIRAccess &access, LIRItem &cmp_value, LIRItem &new_value) {
  BasicType bt = access.type();

  if (access.is_oop()) {
    LIRGenerator* gen = access.gen();

    if (ShenandoahCASBarrier) {
      if (support_IRIW_for_not_multiple_copy_atomic_cpu) {
        __ membar();
      } else {
        __ membar_release();
      }
    }

    if (ShenandoahSATBBarrier) {
      pre_barrier(gen, access.access_emit_info(), access.decorators(), access.resolved_addr(),
                  LIR_OprFact::illegalOpr);
    }

    if (ShenandoahCASBarrier) {
      cmp_value.load_item();
      new_value.load_item();

      LIR_Opr t1 = gen->new_register(T_OBJECT);
      LIR_Opr t2 = gen->new_register(T_OBJECT);
      LIR_Opr addr = access.resolved_addr()->as_address_ptr()->base();
      LIR_Opr result = gen->new_register(T_INT);

      __ append(new LIR_OpShenandoahCompareAndSwap(addr, cmp_value.result(), new_value.result(), t1, t2, result));

      if (support_IRIW_for_not_multiple_copy_atomic_cpu) {
        __ membar_acquire();
      } else {
        __ membar();
      }

      return result;
    }
  }

  return BarrierSetC1::atomic_cmpxchg_at_resolved(access, cmp_value, new_value);
}

LIR_Opr ShenandoahBarrierSetC1::atomic_xchg_at_resolved(LIRAccess &access, LIRItem &value) {
  LIRGenerator* gen = access.gen();
  BasicType type = access.type();

  LIR_Opr result = gen->new_register(type);
  value.load_item();
  LIR_Opr value_opr = value.result();

  if (support_IRIW_for_not_multiple_copy_atomic_cpu) {
    __ membar();
  } else {
    __ membar_release();
  }

  if (access.is_oop()) {
    value_opr = iu_barrier(access.gen(), value_opr, access.access_emit_info(), access.decorators());
  }

  assert(type == T_INT || is_reference_type(type) LP64_ONLY( || type == T_LONG ), "unexpected type");
  LIR_Opr tmp_xchg = gen->new_register(T_INT);
  __ xchg(access.resolved_addr(), value_opr, result, tmp_xchg);

  if (access.is_oop()) {
    result = load_reference_barrier_impl(access.gen(), result, LIR_OprFact::addressConst(0),
                                         access.decorators());

    LIR_Opr tmp_barrier = gen->new_register(type);
    __ move(result, tmp_barrier);
    result = tmp_barrier;

    if (ShenandoahSATBBarrier) {
      pre_barrier(access.gen(), access.access_emit_info(), access.decorators(), LIR_OprFact::illegalOpr, result);
    }
  }

  if (support_IRIW_for_not_multiple_copy_atomic_cpu) {
    __ membar_acquire();
  } else {
    __ membar();
  }

  return result;
}
