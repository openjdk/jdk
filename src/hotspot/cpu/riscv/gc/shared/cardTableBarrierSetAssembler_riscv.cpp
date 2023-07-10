/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/cardTableBarrierSetAssembler.hpp"
#include "gc/shared/gc_globals.hpp"
#include "interpreter/interp_masm.hpp"

#define __ masm->


void CardTableBarrierSetAssembler::store_check(MacroAssembler* masm, Register obj, Register tmp) {
  assert_different_registers(obj, tmp);
  BarrierSet* bs = BarrierSet::barrier_set();
  assert(bs->kind() == BarrierSet::CardTableBarrierSet, "Wrong barrier set kind");

  __ srli(obj, obj, CardTable::card_shift);

  assert(CardTable::dirty_card_val() == 0, "must be");

  __ load_byte_map_base(tmp);
  __ add(tmp, obj, tmp);

  if (UseCondCardMark) {
    Label L_already_dirty;
    __ lbu(t1,  Address(tmp));
    __ beqz(t1, L_already_dirty);
    __ sb(zr, Address(tmp));
    __ bind(L_already_dirty);
  } else {
    __ sb(zr, Address(tmp));
  }
}

void CardTableBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                    Register start, Register count, Register tmp, RegSet saved_regs) {
  assert_different_registers(start, tmp);
  assert_different_registers(count, tmp);

  Label L_loop, L_done;
  const Register end = count;

  __ beqz(count, L_done); // zero count - nothing to do
  // end = start + count << LogBytesPerHeapOop
  __ shadd(end, count, start, count, LogBytesPerHeapOop);
  __ sub(end, end, BytesPerHeapOop); // last element address to make inclusive

  __ srli(start, start, CardTable::card_shift);
  __ srli(end, end, CardTable::card_shift);
  __ sub(count, end, start); // number of bytes to copy

  __ load_byte_map_base(tmp);
  __ add(start, start, tmp);

  __ bind(L_loop);
  __ add(tmp, start, count);
  __ sb(zr, Address(tmp));
  __ sub(count, count, 1);
  __ bgez(count, L_loop);
  __ bind(L_done);
}

void CardTableBarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                Address dst, Register val, Register tmp1, Register tmp2) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool is_array = (decorators & IS_ARRAY) != 0;
  bool on_anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool precise = is_array || on_anonymous;

  bool needs_post_barrier = val != noreg && in_heap;
  BarrierSetAssembler::store_at(masm, decorators, type, dst, val, noreg, noreg);
  if (needs_post_barrier) {
    // flatten object address if needed
    if (!precise || dst.offset() == 0) {
      store_check(masm, dst.base(), x13);
    } else {
      __ la(x13, dst);
      store_check(masm, x13, t0);
    }
  }
}
