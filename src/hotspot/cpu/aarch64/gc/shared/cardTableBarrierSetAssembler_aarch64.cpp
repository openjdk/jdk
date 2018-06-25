/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interp_masm.hpp"

#define __ masm->


void CardTableBarrierSetAssembler::store_check(MacroAssembler* masm, Register obj, Address dst) {

  BarrierSet* bs = BarrierSet::barrier_set();
  assert(bs->kind() == BarrierSet::CardTableBarrierSet, "Wrong barrier set kind");

  CardTableBarrierSet* ctbs = barrier_set_cast<CardTableBarrierSet>(bs);
  CardTable* ct = ctbs->card_table();
  assert(sizeof(*ct->byte_map_base()) == sizeof(jbyte), "adjust this code");

  __ lsr(obj, obj, CardTable::card_shift);

  assert(CardTable::dirty_card_val() == 0, "must be");

  __ load_byte_map_base(rscratch1);

  if (UseCondCardMark) {
    Label L_already_dirty;
    __ membar(Assembler::StoreLoad);
    __ ldrb(rscratch2,  Address(obj, rscratch1));
    __ cbz(rscratch2, L_already_dirty);
    __ strb(zr, Address(obj, rscratch1));
    __ bind(L_already_dirty);
  } else {
    if (ct->scanned_concurrently()) {
      __ membar(Assembler::StoreStore);
    }
    __ strb(zr, Address(obj, rscratch1));
  }
}

void CardTableBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                    Register start, Register end, Register scratch, RegSet saved_regs) {
  BarrierSet* bs = BarrierSet::barrier_set();
  CardTableBarrierSet* ctbs = barrier_set_cast<CardTableBarrierSet>(bs);
  CardTable* ct = ctbs->card_table();
  assert(sizeof(*ct->byte_map_base()) == sizeof(jbyte), "adjust this code");

  Label L_loop;

  __ lsr(start, start, CardTable::card_shift);
  __ lsr(end, end, CardTable::card_shift);
  __ sub(end, end, start); // number of bytes to copy

  const Register count = end; // 'end' register contains bytes count now
  __ load_byte_map_base(scratch);
  __ add(start, start, scratch);
  if (ct->scanned_concurrently()) {
    __ membar(__ StoreStore);
  }
  __ bind(L_loop);
  __ strb(zr, Address(start, count));
  __ subs(count, count, 1);
  __ br(Assembler::GE, L_loop);
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
    if (!precise || (dst.index() == noreg && dst.offset() == 0)) {
      store_check(masm, dst.base(), dst);
    } else {
      __ lea(r3, dst);
      store_check(masm, r3, dst);
    }
  }
}
