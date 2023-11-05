/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_PRIMS_VMSTORAGE_HPP
#define SHARE_PRIMS_VMSTORAGE_HPP

#include <cstdint>

#include "code/vmreg.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

enum class StorageType : int8_t; // defined in arch specific headers

class VMStorage {
public:
  constexpr static StorageType INVALID_TYPE = static_cast<StorageType>(-1);
private:
  StorageType _type;
  // 1 byte of padding
  uint16_t _segment_mask_or_size;
  uint32_t _index_or_offset; // stack offset in bytes for stack storage

  friend bool operator==(const VMStorage& a, const VMStorage& b);

  constexpr inline static bool is_reg(StorageType type);
  constexpr inline static StorageType stack_type();
  constexpr inline static StorageType placeholder_type();
  constexpr inline static StorageType frame_data_type();
public:
  constexpr VMStorage() : _type(INVALID_TYPE), _segment_mask_or_size(0), _index_or_offset(0) {};
  constexpr VMStorage(StorageType type, uint16_t segment_mask_or_size, uint32_t index_or_offset)
    : _type(type), _segment_mask_or_size(segment_mask_or_size), _index_or_offset(index_or_offset) {};

  constexpr static VMStorage reg_storage(StorageType type, uint16_t segment_mask, uint32_t index) {
    assert(is_reg(type), "must be reg");
    return VMStorage(type, segment_mask, index);
  }

  constexpr static VMStorage stack_storage(uint16_t size, uint32_t offset) {
    return VMStorage(stack_type(), size, offset);
  }

  static VMStorage stack_storage(VMReg reg) {
    return stack_storage(BytesPerWord, checked_cast<uint16_t>(reg->reg2stack() * VMRegImpl::stack_slot_size));
  }

  constexpr static VMStorage invalid() {
    VMStorage result;
    result._type = INVALID_TYPE;
    return result;
  }

  StorageType type() const { return _type; }

  // type specific accessors to make calling code more readable
  uint16_t segment_mask()    const { assert(is_reg(), "must be reg");                  return _segment_mask_or_size; }
  uint16_t stack_size()      const { assert(is_stack() || is_frame_data(), "must be"); return _segment_mask_or_size; }
  uint32_t index()           const { assert(is_reg() || is_placeholder(), "must be");  return _index_or_offset; }
  uint32_t offset()          const { assert(is_stack() || is_frame_data(), "must be"); return _index_or_offset; }
  uint32_t index_or_offset() const { assert(is_valid(), "must be valid");              return _index_or_offset; }

  bool is_valid()       const { return _type != INVALID_TYPE; }
  bool is_reg()         const { return is_reg(_type); }
  bool is_stack()       const { return _type == stack_type(); }
  bool is_placeholder() const { return _type == placeholder_type(); }
  bool is_frame_data()  const { return _type == frame_data_type(); }

  void print_on(outputStream* os) const;
};

inline bool operator==(const VMStorage& a, const VMStorage& b) {
  return a._type == b._type
    && a._index_or_offset == b._index_or_offset
    && a._segment_mask_or_size == b._segment_mask_or_size;
}

#include CPU_HEADER(vmstorage)

#endif // SHARE_PRIMS_VMSTORAGE_HPP
