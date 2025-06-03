/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "utilities/align.hpp"
#include "utilities/count_leading_zeros.hpp"
#include "utilities/packedTable.hpp"

PackedTableBase::PackedTableBase(uint32_t max_key, uint32_t max_value) {
  unsigned int key_bits = max_key == 0 ? 0 : 32 - count_leading_zeros(max_key);
  unsigned int value_bits = max_value == 0 ? 0 : 32 - count_leading_zeros(max_value);
  _element_bytes = align_up(key_bits + value_bits, 8) / 8;
  // shifting left by 32 is undefined behaviour, and in practice returns 1
  _key_mask = key_bits >= 32 ? -1 : (1U << key_bits) - 1;
  _value_shift = key_bits;
  _value_mask = value_bits >= 32 ? -1 : (1U << value_bits) - 1;
  guarantee(_element_bytes > 0, "wouldn't work");
  assert(_element_bytes <= sizeof(uint64_t), "shouldn't happen");
}

// Note: we require the supplier to provide the elements in the final order as we can't easily sort
// within this method - qsort() accepts only pure function as comparator.
void PackedTableBuilder::fill(u1* data, size_t length, Supplier &supplier) const {
  uint32_t key, value;
  size_t offset = 0;
  for (; offset + sizeof(uint64_t) <= length && supplier.next(&key, &value); offset += _element_bytes) {
    assert((key & ~_key_mask) == 0, "key out of bounds");
    assert((value & ~_value_mask) == 0, "value out of bounds: %x vs. %x (%x)", value, _value_mask, ~_value_mask);
    *reinterpret_cast<uint64_t*>(data + offset) = static_cast<uint64_t>(key) | (static_cast<uint64_t>(value) << _value_shift);
  }
  // last bytes
  for (; offset < length && supplier.next(&key, &value); offset += _element_bytes) {
    uint64_t element = static_cast<uint64_t>(key) | (static_cast<uint64_t>(value) << _value_shift);
    for (unsigned int i = 0; i < _element_bytes; ++i) {
      data[offset + i] = static_cast<u1>(0xFF & (element >> (8 * i)));
    }
  }

  assert(offset == length, "Did not fill whole array");
  assert(!supplier.next(&key, &value), "Supplier has more elements");
}

uint64_t PackedTableLookup::read_element(const u1* data, size_t length, size_t offset) const {
  if (offset + sizeof(uint64_t) <= length) {
    return *reinterpret_cast<const uint64_t*>(data + offset);
  }
  // slow path for accessing end of array
  uint64_t element = 0;
  for (size_t i = 0; i < _element_bytes && offset + i < length; ++i) {
    element = element | (static_cast<uint64_t>(data[offset + i]) << (i * 8));
  }
  assert((element & ~((uint64_t) _key_mask | ((uint64_t) _value_mask << _value_shift))) == 0, "read too much");
  return element;
}

bool PackedTableLookup::search(Comparator& comparator, const u1* data, size_t length, uint32_t* found_key, uint32_t* found_value) const {
  unsigned int low = 0, high = checked_cast<unsigned int>(length / _element_bytes);
  assert(low < high, "must be");
  while (low < high) {
    unsigned int mid = low + (high - low) / 2;
    assert(mid >= low && mid < high, "integer overflow?");
    uint64_t element = read_element(data, length, _element_bytes * mid);
    uint32_t key = element & _key_mask;
    int cmp = comparator.compare_to(key);
    if (cmp == 0) {
      *found_key = key;
      *found_value = (element >> _value_shift) & _value_mask;
      return true;
    } else if (cmp < 0) {
      high = mid;
    } else {
      low = mid + 1;
    }
  }
  return false;
}

#ifdef ASSERT
void PackedTableLookup::validate_order(Comparator &comparator, const u1* table, size_t length) const {
  for (size_t offset = 0; offset < length; offset += _element_bytes) {
    uint64_t element = read_element(table, length, offset);
    uint32_t key = element & _key_mask;

    if (offset != 0) {
      assert(comparator.compare_to(key) < 0, "not sorted");
    }
    comparator.reset(key);
  }
}
#endif
