/*
 * Copyright (c) 2026, Microsoft Corporation. All rights reserved.
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

#include "asm/macroAssembler.hpp"

// __chkstk is an internal CRT function that probes the stack page by page
// to ensure that the guard page moves to the desired stack location. On
// entry, r15 contains the number of 16-byte slots to allocate.
// It clobbers r16 and r17 but does not modify sp or any other registers.
extern "C" void __chkstk();

void MacroAssembler::pd_extend_stack_guard_page_for_method_max_stack(Register const_method, Register temp1, Register temp2) {
  assert_different_registers(const_method, temp1, temp2);
  assert_different_registers(r15, temp1, temp2);
  assert_different_registers(r16, temp1, temp2);
  assert_different_registers(r17, temp1, temp2);

  stp(r15, lr, Address(pre(sp, -2 * wordSize)));
  ldrh(temp1, Address(const_method, ConstMethod::max_stack_offset()));
  add(temp1, temp1, MAX2(3, Method::extra_stack_entries()));

  // load the number of 16-byte slots required into r15
  add(temp1, temp1, 1);
  lsr(r15, temp1, 1);

  mov(temp1, r16);
  mov(temp2, r17);

  mov(lr, ExternalAddress(CAST_FROM_FN_PTR(address, __chkstk)));
  blr(lr);

  mov(r16, temp1);
  mov(r17, temp2);
  ldp(r15, lr, Address(post(sp, 2 * wordSize)));
}
