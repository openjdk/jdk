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
#include "gc/shared/collectedHeap.hpp"
#include "runtime/globals.hpp"

#define __ masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

void CardTableBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                    Register addr, Register count, Register tmp) {
  BLOCK_COMMENT("CardTablePostBarrier");
  BarrierSet* bs = BarrierSet::barrier_set();
  CardTableBarrierSet* ctbs = barrier_set_cast<CardTableBarrierSet>(bs);
  CardTable* ct = ctbs->card_table();
  assert(sizeof(*ct->byte_map_base()) == sizeof(jbyte), "adjust this code");

  Label L_cardtable_loop, L_done;

  __ cbz_32(count, L_done); // zero count - nothing to do

  __ add_ptr_scaled_int32(count, addr, count, LogBytesPerHeapOop);
  __ sub(count, count, BytesPerHeapOop);                            // last addr

  __ logical_shift_right(addr, addr, CardTable::card_shift);
  __ logical_shift_right(count, count, CardTable::card_shift);
  __ sub(count, count, addr); // nb of cards

  // warning: Rthread has not been preserved
  __ mov_address(tmp, (address) ct->byte_map_base(), symbolic_Relocation::card_table_reference);
  __ add(addr,tmp, addr);

  Register zero = __ zero_register(tmp);

  __ BIND(L_cardtable_loop);
  __ strb(zero, Address(addr, 1, post_indexed));
  __ subs(count, count, 1);
  __ b(L_cardtable_loop, ge);
  __ BIND(L_done);
}
