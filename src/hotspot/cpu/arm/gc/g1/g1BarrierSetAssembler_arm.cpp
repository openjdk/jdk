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
#include "gc/g1/g1BarrierSetAssembler.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/heapRegion.hpp"
#include "interpreter/interp_masm.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/thread.hpp"
#include "utilities/macros.hpp"

#define __ masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

void G1BarrierSetAssembler::gen_write_ref_array_pre_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                            Register addr, Register count, int callee_saved_regs) {
  bool dest_uninitialized = (decorators & AS_DEST_NOT_INITIALIZED) != 0;
  if (!dest_uninitialized) {
    assert( addr->encoding() < callee_saved_regs, "addr must be saved");
    assert(count->encoding() < callee_saved_regs, "count must be saved");

    BLOCK_COMMENT("PreBarrier");

#ifdef AARCH64
    callee_saved_regs = align_up(callee_saved_regs, 2);
    for (int i = 0; i < callee_saved_regs; i += 2) {
      __ raw_push(as_Register(i), as_Register(i+1));
    }
#else
    RegisterSet saved_regs = RegisterSet(R0, as_Register(callee_saved_regs-1));
    __ push(saved_regs | R9ifScratched);
#endif // AARCH64

    if (addr != R0) {
      assert_different_registers(count, R0);
      __ mov(R0, addr);
    }
#ifdef AARCH64
    __ zero_extend(R1, count, 32); // G1BarrierSet::write_ref_array_pre_*_entry takes size_t
#else
    if (count != R1) {
      __ mov(R1, count);
    }
#endif // AARCH64

    if (UseCompressedOops) {
      __ call(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_narrow_oop_entry));
    } else {
      __ call(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_pre_oop_entry));
    }

#ifdef AARCH64
    for (int i = callee_saved_regs - 2; i >= 0; i -= 2) {
      __ raw_pop(as_Register(i), as_Register(i+1));
    }
#else
    __ pop(saved_regs | R9ifScratched);
#endif // AARCH64
  }
}

void G1BarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                             Register addr, Register count, Register tmp) {

  BLOCK_COMMENT("G1PostBarrier");
  if (addr != R0) {
    assert_different_registers(count, R0);
    __ mov(R0, addr);
  }
#ifdef AARCH64
  __ zero_extend(R1, count, 32); // G1BarrierSet::write_ref_array_post_entry takes size_t
#else
  if (count != R1) {
    __ mov(R1, count);
  }
#if R9_IS_SCRATCHED
  // Safer to save R9 here since callers may have been written
  // assuming R9 survives. This is suboptimal but is not in
  // general worth optimizing for the few platforms where R9
  // is scratched. Note that the optimization might not be to
  // difficult for this particular call site.
  __ push(R9);
#endif // !R9_IS_SCRATCHED
#endif // !AARCH64
  __ call(CAST_FROM_FN_PTR(address, G1BarrierSet::write_ref_array_post_entry));
#ifndef AARCH64
#if R9_IS_SCRATCHED
  __ pop(R9);
#endif // !R9_IS_SCRATCHED
#endif // !AARCH64
}
