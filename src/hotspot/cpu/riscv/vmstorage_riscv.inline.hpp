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

#ifndef CPU_RISCV_VMSTORAGE_RISCV_INLINE_HPP
#define CPU_RISCV_VMSTORAGE_RISCV_INLINE_HPP

#include <cstdint>

#include "asm/register.hpp"
#include "prims/vmstorageBase.inline.hpp"

enum class StorageType : int8_t {
  STACK = 0,
  PLACEHOLDER = 1,
// special locations used only by native code
  FRAME_DATA = PLACEHOLDER + 1,
  INVALID = -1
};

// need to define this before constructing VMStorage (below)
constexpr inline bool VMStorage::is_reg(StorageType type) {
   return false;
}
constexpr inline StorageType VMStorage::stack_type() { return StorageType::STACK; }
constexpr inline StorageType VMStorage::placeholder_type() { return StorageType::PLACEHOLDER; }
constexpr inline StorageType VMStorage::frame_data_type() { return StorageType::FRAME_DATA; }

inline VMStorage as_VMStorage(VMReg reg) {
  ShouldNotReachHere();
  return VMStorage::invalid();
}

#endif // CPU_RISCV_VMSTORAGE_RISCV_INLINE_HPP