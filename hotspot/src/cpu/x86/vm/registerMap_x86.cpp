/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/registerMap.hpp"
#include "vmreg_x86.inline.hpp"

address RegisterMap::pd_location(VMReg reg) const {
  if (reg->is_XMMRegister()) {
    int regBase = reg->value() - ConcreteRegisterImpl::max_fpr;
    if (regBase % 4 == 0) {
      // Reads of the low and high 16 byte parts should be handled by location itself
      // because they have separate callee saved entries.
      // See RegisterSaver::save_live_registers().
      return NULL;
    }
    VMReg baseReg = as_XMMRegister(regBase / XMMRegisterImpl::max_slots_per_register)->as_VMReg();
    intptr_t offset = (reg->value() - baseReg->value()) * VMRegImpl::stack_slot_size; // offset in bytes
    if (offset >= 16) {
      // The high part of YMM registers are saved in a their own area in the frame
      baseReg = baseReg->next()->next()->next()->next();
      offset -= 16;
    }
    address baseLocation = location(baseReg);
    if (baseLocation != NULL) {
      return baseLocation + offset;
    }
  }
  return NULL;
}
