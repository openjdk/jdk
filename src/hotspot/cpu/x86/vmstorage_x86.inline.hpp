/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VMSTORAGE_X86_INLINE_HPP
#define CPU_X86_VMSTORAGE_X86_INLINE_HPP

#include <cstdint>

#include "asm/register.hpp"
#include "code/vmreg.inline.hpp"
#include "prims/vmstorageBase.inline.hpp"

// keep in sync with jdk/internal/foreign/abi/x64/X86_64Architecture
enum class RegType : int8_t {
  INTEGER = 0,
  VECTOR = 1,
  X87 = 2,
  STACK = 3,
  INVALID = -1
};

// need to define this before constructing VMStorage (below)
constexpr inline RegType VMStorage::stack_type() {
  return RegType::STACK;
}

constexpr uint16_t REG64_MASK = 0b0000000000001111;
constexpr uint16_t XMM_MASK   = 0b0000000000000001;

constexpr VMStorage VMS_RAX = VMStorage::reg_storage(RegType::INTEGER, REG64_MASK, 0);
constexpr VMStorage VMS_RBX = VMStorage::reg_storage(RegType::INTEGER, REG64_MASK, 3);
constexpr VMStorage VMS_XMM0 = VMStorage::reg_storage(RegType::VECTOR, XMM_MASK, 0);

inline Register as_Register(VMStorage vms) {
  assert(vms.type() == RegType::INTEGER, "not the right type");
  return ::as_Register(vms.index());
}

inline XMMRegister as_XMMRegister(VMStorage vms) {
  assert(vms.type() == RegType::VECTOR, "not the right type");
  return ::as_XMMRegister(vms.index());
}

inline VMReg as_VMReg(VMStorage vms) {
  switch (vms.type()) {
    case RegType::INTEGER: return as_Register(vms)->as_VMReg();
    case RegType::VECTOR:  return as_XMMRegister(vms)->as_VMReg();
    case RegType::STACK: {
      assert((vms.index() % VMRegImpl::stack_slot_size) == 0, "can not represent as VMReg");
      return VMRegImpl::stack2reg(vms.index() / VMRegImpl::stack_slot_size);
    }
    default: ShouldNotReachHere(); return VMRegImpl::Bad();
  }
}

inline VMStorage as_VMStorage(Register reg) {
  return VMStorage::reg_storage(RegType::INTEGER, REG64_MASK, reg->encoding());
}

inline VMStorage as_VMStorage(XMMRegister reg) {
  return VMStorage::reg_storage(RegType::VECTOR, XMM_MASK, reg->encoding());
}

inline VMStorage as_VMStorage(VMReg reg) {
  if (reg->is_Register()) {
    return as_VMStorage(reg->as_Register());
  } else if (reg->is_XMMRegister()) {
    return as_VMStorage(reg->as_XMMRegister());
  } else if (reg->is_stack()) {
    return VMStorage::stack_storage(reg);
  } else if (!reg->is_valid()) {
    return VMStorage::invalid();
  }

  ShouldNotReachHere();
  return VMStorage::invalid();
}

#endif // CPU_X86_VMSTORAGE_X86_INLINE_HPP