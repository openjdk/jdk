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
#include "gc/shared/modRefBarrierSetAssembler.hpp"

#define __ masm->

void ModRefBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                   Register src, Register dst, Register count) {
  if (type == T_OBJECT) {
    bool checkcast = (decorators & ARRAYCOPY_CHECKCAST) != 0;
    if (!checkcast) {
      // save arguments for barrier generation
      __ mov(dst, G1);
      __ mov(count, G5);
      gen_write_ref_array_pre_barrier(masm, decorators, G1, G5);
    } else {
      gen_write_ref_array_pre_barrier(masm, decorators, dst, count);
    }
  }
}

void ModRefBarrierSetAssembler::arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                   Register src, Register dst, Register count) {
  if (type == T_OBJECT) {
    bool checkcast = (decorators & ARRAYCOPY_CHECKCAST) != 0;
    if (!checkcast) {
      // O0 is used as temp register
      gen_write_ref_array_post_barrier(masm, decorators, G1, G5, O0);
    } else {
      gen_write_ref_array_post_barrier(masm, decorators, dst, count, O3);
    }
  }
}

void ModRefBarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                         Register val, Address dst, Register tmp) {
  if (type == T_OBJECT || type == T_ARRAY) {
    oop_store_at(masm, decorators, type, val, dst, tmp);
  } else {
    BarrierSetAssembler::store_at(masm, decorators, type, val, dst, tmp);
  }
}
