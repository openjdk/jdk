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

void MacroAssembler::pd_extend_stack_guard_page_for_method_max_stack(Register const_method, Register temp1, Register temp2, Register temp3) {
  const int page_size = (int)os::vm_page_size();
  const int page_size_mask = -page_size;

  ldrh(temp1, Address(const_method, ConstMethod::max_stack_offset()));
  add(temp1, temp1, MAX2(3, Method::extra_stack_entries()));

  // load the number of 16-byte slots required into temp1
  add(temp1, temp1, 1);
  lsr(temp1, temp1, 1);

  // compute number of bytes required and load the target SP into temp2
  subs(temp2, sp, temp1, ext::uxtw, 4);
  csel(temp2, zr, temp2, Assembler::LO);

  // round both down to the nearest page
  mov(temp3, page_size_mask);
  mov(temp1, sp);
  andr(temp1, temp1, temp3);
  andr(temp2, temp2, temp3);

  Label stack_check_done;
  cmp(temp1, temp2);
  br(Assembler::EQ, stack_check_done);

  Label stack_check;
  bind(stack_check);
  sub(temp1, temp1, page_size);
  ldr(zr, Address(temp1));
  cmp(temp1, temp2);
  br(Assembler::NE, stack_check);
  bind(stack_check_done);
}
