/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "runtime/registerMap.hpp"
#include "vmreg_aarch64.inline.hpp"

address RegisterMap::pd_location(VMReg reg, int slot) const {
  if (reg->is_concrete() && slot == 0) {
    return NULL; // Should have been handled by base location()
  } else if (reg->is_FloatRegister()) {
    // Not all physical slots of an SVE register have corresponding
    // VMRegs. However they are always saved to the stack in a
    // contiguous region of memory so we can calculate the address of
    // the upper slots by offsetting from the base address.
    int base_reg_enc = (reg->value() - ConcreteRegisterImpl::max_gpr) /
                       FloatRegisterImpl::max_slots_per_register;
    VMReg base_reg = as_FloatRegister(base_reg_enc)->as_VMReg();
    assert(base_reg->is_concrete(), "should be concrete");
    address base_addr = location(base_reg);
    slot += reg->value() - base_reg->value();
    if (base_addr != NULL) {
      return base_addr + (slot * VMRegImpl::stack_slot_size);
    }
  }
  return NULL;
}
