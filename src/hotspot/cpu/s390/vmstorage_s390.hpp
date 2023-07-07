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

#ifndef CPU_S390_VMSTORAGE_S390_INLINE_HPP
#define CPU_S390_VMSTORAGE_S390_INLINE_HPP

#include <cstdint>

#include "asm/register.hpp"

enum class StorageType : int8_t {
  INTEGER = 0,
  FLOAT = 1,
  STACK = 2,
  PLACEHOLDER = 3,
  // special locations used only by native code
  FRAME_DATA = 4,
  INVALID = -1
};

// need to define this before constructing VMStorage (below)
constexpr inline bool VMStorage::is_reg(StorageType type) {
  return type == StorageType::INTEGER || type == StorageType::FLOAT;
}
constexpr inline StorageType VMStorage::stack_type() { return StorageType::STACK; }
constexpr inline StorageType VMStorage::placeholder_type() { return StorageType::PLACEHOLDER; }
constexpr inline StorageType VMStorage::frame_data_type() { return StorageType::FRAME_DATA; }

// Needs to be consistent with S390Architecture.java.
constexpr uint16_t REG32_MASK = 0b0000000000000001;
constexpr uint16_t REG64_MASK = 0b0000000000000011;

inline Register as_Register(VMStorage vms) {
  assert(vms.type() == StorageType::INTEGER, "not the right type");
  return ::as_Register(vms.index());
}

inline FloatRegister as_FloatRegister(VMStorage vms) {
  assert(vms.type() == StorageType::FLOAT, "not the right type");
  return ::as_FloatRegister(vms.index());
}

inline VMStorage as_VMStorage(Register reg, uint16_t segment_mask = REG64_MASK) {
  return VMStorage::reg_storage(StorageType::INTEGER, segment_mask, reg->encoding());
}

inline VMStorage as_VMStorage(FloatRegister reg, uint16_t segment_mask = REG64_MASK) {
  return VMStorage::reg_storage(StorageType::FLOAT, segment_mask, reg->encoding());
}

inline VMStorage as_VMStorage(VMReg reg, BasicType bt) {
  if (reg->is_Register()) {
    uint16_t segment_mask = 0;
    switch (bt) {
      case T_BOOLEAN:
      case T_CHAR   :
      case T_BYTE   :
      case T_SHORT  :
      case T_INT    : segment_mask = REG32_MASK; break;
      default       : segment_mask = REG64_MASK; break;
    }
    return as_VMStorage(reg->as_Register(), segment_mask);
  } else if (reg->is_FloatRegister()) {
    // FP regs always use double format. However, we need the correct format for loads /stores.
    return as_VMStorage(reg->as_FloatRegister(), (bt == T_FLOAT) ? REG32_MASK : REG64_MASK);
  } else if (reg->is_stack()) {
    uint16_t size = 0;
    switch (bt) {
      case T_BOOLEAN:
      case T_CHAR   :
      case T_BYTE   :
      case T_SHORT  :
      case T_INT    :
      case T_FLOAT  : size = 4; break;
      default       : size = 8; break;
    }
    return VMStorage(StorageType::STACK, size,
        checked_cast<uint16_t>(reg->reg2stack() * VMRegImpl::stack_slot_size));
  } else if (!reg->is_valid()) {
    return VMStorage::invalid();
  }

  ShouldNotReachHere();
  return VMStorage::invalid();
}

#endif // CPU_S390_VMSTORAGE_S390_INLINE_HPP
