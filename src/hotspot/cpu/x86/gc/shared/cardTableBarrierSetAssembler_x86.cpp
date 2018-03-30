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

#define __ masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

#define TIMES_OOP (UseCompressedOops ? Address::times_4 : Address::times_8)

void CardTableBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                    Register addr, Register count, Register tmp) {
  BarrierSet *bs = Universe::heap()->barrier_set();
  CardTableBarrierSet* ctbs = barrier_set_cast<CardTableBarrierSet>(bs);
  CardTable* ct = ctbs->card_table();
  assert(sizeof(*ct->byte_map_base()) == sizeof(jbyte), "adjust this code");
  intptr_t disp = (intptr_t) ct->byte_map_base();

  Label L_loop, L_done;
  const Register end = count;
  assert_different_registers(addr, end);

  __ testl(count, count);
  __ jcc(Assembler::zero, L_done); // zero count - nothing to do


#ifdef _LP64
  __ leaq(end, Address(addr, count, TIMES_OOP, 0));  // end == addr+count*oop_size
  __ subptr(end, BytesPerHeapOop); // end - 1 to make inclusive
  __ shrptr(addr, CardTable::card_shift);
  __ shrptr(end, CardTable::card_shift);
  __ subptr(end, addr); // end --> cards count

  __ mov64(tmp, disp);
  __ addptr(addr, tmp);
__ BIND(L_loop);
  __ movb(Address(addr, count, Address::times_1), 0);
  __ decrement(count);
  __ jcc(Assembler::greaterEqual, L_loop);
#else
  __ lea(end,  Address(addr, count, Address::times_ptr, -wordSize));
  __ shrptr(addr, CardTable::card_shift);
  __ shrptr(end,   CardTable::card_shift);
  __ subptr(end, addr); // end --> count
__ BIND(L_loop);
  Address cardtable(addr, count, Address::times_1, disp);
  __ movb(cardtable, 0);
  __ decrement(count);
  __ jcc(Assembler::greaterEqual, L_loop);
#endif

__ BIND(L_done);
}
