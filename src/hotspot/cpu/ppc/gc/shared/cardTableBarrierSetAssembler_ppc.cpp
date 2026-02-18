/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2025 SAP SE. All rights reserved.
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

#include "asm/macroAssembler.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/cardTableBarrierSetAssembler.hpp"
#include "interpreter/interp_masm.hpp"
#include "runtime/jniHandles.hpp"

#define __ masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

void CardTableBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                      Register src, Register dst, Register count, Register preserve1, Register preserve2) {
  if (type == T_OBJECT) {
    gen_write_ref_array_pre_barrier(masm, decorators,
                                    src, dst, count,
                                    preserve1, preserve2);

    bool checkcast = (decorators & ARRAYCOPY_CHECKCAST) != 0;
    if (!checkcast) {
      assert_different_registers(dst, count, R9_ARG7, R10_ARG8);
      // Save some arguments for epilogue, e.g. disjoint_long_copy_core destroys them.
      __ mr(R9_ARG7, dst);
      __ mr(R10_ARG8, count);
    }
  }
}

void CardTableBarrierSetAssembler::arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                      Register dst, Register count, Register preserve) {
  if (type == T_OBJECT) {
    bool checkcast = (decorators & ARRAYCOPY_CHECKCAST) != 0;
    if (!checkcast) {
      gen_write_ref_array_post_barrier(masm, decorators, R9_ARG7, R10_ARG8, preserve);
    } else {
      gen_write_ref_array_post_barrier(masm, decorators, dst, count, preserve);
    }
  }
}

void CardTableBarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                            Register base, RegisterOrConstant ind_or_offs, Register val,
                                            Register tmp1, Register tmp2, Register tmp3,
                                            MacroAssembler::PreservationLevel preservation_level) {
  if (is_reference_type(type)) {
    oop_store_at(masm, decorators, type,
                 base, ind_or_offs, val,
                 tmp1, tmp2, tmp3,
                 preservation_level);
  } else {
    BarrierSetAssembler::store_at(masm, decorators, type,
                                  base, ind_or_offs, val,
                                  tmp1, tmp2, tmp3,
                                  preservation_level);
  }
}

void CardTableBarrierSetAssembler::resolve_jobject(MacroAssembler* masm, Register value,
                                                   Register tmp1, Register tmp2,
                                                   MacroAssembler::PreservationLevel preservation_level) {
  Label done;
  __ cmpdi(CR0, value, 0);
  __ beq(CR0, done);         // Use null as-is.

  __ clrrdi(tmp1, value, JNIHandles::tag_size);
  __ ld(value, 0, tmp1);      // Resolve (untagged) jobject.

  __ verify_oop(value, FILE_AND_LINE);
  __ bind(done);
}

void CardTableBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators, Register addr,
                                                                    Register count, Register preserve) {
  CardTableBarrierSet* ctbs = CardTableBarrierSet::barrier_set();
  assert_different_registers(addr, count, R0);

  Label Lskip_loop, Lstore_loop;

  __ sldi_(count, count, LogBytesPerHeapOop);
  __ beq(CR0, Lskip_loop); // zero length
  __ addi(count, count, -BytesPerHeapOop);
  __ add(count, addr, count);
  // Use two shifts to clear out those low order two bits! (Cannot opt. into 1.)
  __ srdi(addr, addr, CardTable::card_shift());
  __ srdi(count, count, CardTable::card_shift());
  __ subf(count, addr, count);
  __ add_const_optimized(addr, addr, (address)ctbs->card_table_base_const(), R0);
  __ addi(count, count, 1);
  __ li(R0, 0);
  __ mtctr(count);
  // Byte store loop
  __ bind(Lstore_loop);
  __ stb(R0, 0, addr);
  __ addi(addr, addr, 1);
  __ bdnz(Lstore_loop);
  __ bind(Lskip_loop);
}

void CardTableBarrierSetAssembler::card_table_write(MacroAssembler* masm,
                                                    CardTable::CardValue* byte_map_base,
                                                    Register tmp, Register obj) {
  assert_different_registers(obj, tmp, R0);
  __ load_const_optimized(tmp, (address)byte_map_base, R0);
  __ srdi(obj, obj, CardTable::card_shift());
  __ li(R0, CardTable::dirty_card_val());
  __ stbx(R0, tmp, obj);
}

void CardTableBarrierSetAssembler::card_write_barrier_post(MacroAssembler* masm, Register store_addr, Register tmp) {
  CardTableBarrierSet* bs = CardTableBarrierSet::barrier_set();
  card_table_write(masm, bs->card_table_base_const(), tmp, store_addr);
}

void CardTableBarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                Register base, RegisterOrConstant ind_or_offs, Register val,
                                                Register tmp1, Register tmp2, Register tmp3,
                                                MacroAssembler::PreservationLevel preservation_level) {
  bool is_array = (decorators & IS_ARRAY) != 0;
  bool on_anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool precise = is_array || on_anonymous;

  BarrierSetAssembler::store_at(masm, decorators, type,
                                base, ind_or_offs, val,
                                tmp1, tmp2, tmp3,
                                preservation_level);

  // No need for post barrier if storing null
  if (val != noreg) {
    if (precise) {
      if (ind_or_offs.is_constant()) {
        __ add_const_optimized(base, base, ind_or_offs.as_constant(), tmp1);
      } else {
        __ add(base, ind_or_offs.as_register(), base);
      }
    }
    card_write_barrier_post(masm, base, tmp1);
  }
}
