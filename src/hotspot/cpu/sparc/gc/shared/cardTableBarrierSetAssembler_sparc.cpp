
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
#include "interpreter/interp_masm.hpp"

#define __ masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

void CardTableBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                    Register addr, Register count, Register tmp) {
  CardTableBarrierSet* ctbs = barrier_set_cast<CardTableBarrierSet>(Universe::heap()->barrier_set());
  CardTable* ct = ctbs->card_table();
  assert(sizeof(*ct->byte_map_base()) == sizeof(jbyte), "adjust this code");
  assert_different_registers(addr, count, tmp);

  Label L_loop, L_done;

  __ cmp_and_br_short(count, 0, Assembler::equal, Assembler::pt, L_done); // zero count - nothing to do

  __ sll_ptr(count, LogBytesPerHeapOop, count);
  __ sub(count, BytesPerHeapOop, count);
  __ add(count, addr, count);
  // Use two shifts to clear out those low order two bits! (Cannot opt. into 1.)
  __ srl_ptr(addr, CardTable::card_shift, addr);
  __ srl_ptr(count, CardTable::card_shift, count);
  __ sub(count, addr, count);
  AddressLiteral rs(ct->byte_map_base());
  __ set(rs, tmp);
  __ BIND(L_loop);
  __ stb(G0, tmp, addr);
  __ subcc(count, 1, count);
  __ brx(Assembler::greaterEqual, false, Assembler::pt, L_loop);
  __ delayed()->add(addr, 1, addr);

  __ BIND(L_done);
}
