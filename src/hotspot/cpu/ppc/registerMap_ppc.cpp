/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026 SAP SE. All rights reserved.
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

#include "runtime/registerMap.hpp"

address RegisterMap::pd_location(VMReg base_reg, int slot_idx) const {
  if (base_reg->is_VectorRegister()) {
    // Not all physical slots belonging to a VectorRegister have corresponding
    // valid VMReg locations in the RegisterMap.
    // (See RegisterSaver::push_frame_reg_args_and_save_live_registers.)
    // However, the slots are always saved to the stack in a contiguous region
    // of memory so we can calculate the address of the upper slots by
    // offsetting from the base address.
    assert(base_reg->is_concrete(), "must pass base reg");
    address base_location = location(base_reg, nullptr);
    if (base_location != nullptr) {
      intptr_t offset_in_bytes = slot_idx * VMRegImpl::stack_slot_size;
      return base_location + offset_in_bytes;
    } else {
      return nullptr;
    }
  } else {
    return location(base_reg->next(slot_idx), nullptr);
  }
}
