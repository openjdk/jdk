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
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "runtime/thread.hpp"
#include "interpreter/interp_masm.hpp"

#define __ masm->

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count, RegSet saved_regs) {
  bool dest_uninitialized = (decorators & AS_DEST_NOT_INITIALIZED) != 0;
  if (!dest_uninitialized) {
    __ push(saved_regs, sp);
    if (count == c_rarg0) {
      if (addr == c_rarg1) {
        // exactly backwards!!
        __ mov(rscratch1, c_rarg0);
        __ mov(c_rarg0, c_rarg1);
        __ mov(c_rarg1, rscratch1);
      } else {
        __ mov(c_rarg1, count);
        __ mov(c_rarg0, addr);
      }
    } else {
      __ mov(c_rarg0, addr);
      __ mov(c_rarg1, count);
    }
    if (UseCompressedOops) {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_narrow_oop_entry), 2);
    } else {
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_oop_entry), 2);
    }
    __ pop(saved_regs, sp);
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register start, Register end, Register scratch, RegSet saved_regs) {
  __ push(saved_regs, sp);
  // must compute element count unless barrier set interface is changed (other platforms supply count)
  assert_different_registers(start, end, scratch);
  __ lea(scratch, Address(end, BytesPerHeapOop));
  __ sub(scratch, scratch, start);               // subtract start to get #bytes
  __ lsr(scratch, scratch, LogBytesPerHeapOop);  // convert to element count
  __ mov(c_rarg0, start);
  __ mov(c_rarg1, scratch);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_post_entry), 2);
  __ pop(saved_regs, sp);
}
