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

PackedTableBase::PackedTableBase(uint32_t max_pivot, uint32_t max_payload) {
  unsigned int pivot_bits = max_pivot == 0 ? 0 : 32 - count_leading_zeros(max_pivot);
  unsigned int payload_bits = max_payload == 0 ? 0 : 32 - count_leading_zeros(max_payload);
  _element_bytes = align_up(pivot_bits + payload_bits, 8) / 8;
  _pivot_mask = (1 << pivot_bits) - 1;
  _payload_shift = pivot_bits;
  _payload_mask = (1 << payload_bits) - 1;
  guarantee(_element_bytes > 0, "wouldn't work");
}

void PackedTableBuilder::fill(Array<u1> *array, Supplier &supplier) const {
  uint32_t pivot, payload;
  u1 *data = array->data();
  size_t length = static_cast<size_t>(array->length());
  size_t offset = 0;
  for (; offset + sizeof(uint64_t) <= length && supplier.next(&pivot, &payload); offset += _element_bytes) {
    assert((pivot & ~_pivot_mask) == 0, "pivot out of bounds");
    assert((payload & ~_payload_mask) == 0, "payload out of bounds");
    *reinterpret_cast<uint64_t *>(data + offset) = static_cast<uint64_t>(pivot) | (static_cast<uint64_t>(payload) << _payload_shift);
  }
  // last bytes
  for (; offset < length && supplier.next(&pivot, &payload); offset += _element_bytes) {
    uint64_t value = static_cast<uint64_t>(pivot) | (static_cast<uint64_t>(payload) << _payload_shift);
    for (unsigned int i = 0; i < _element_bytes; ++i) {
      data[offset + i] = static_cast<u1>(0xFF & (value >> (8 * i)));
    }
  }
  assert(offset == length, "Did not fill whole array");
  assert(!supplier.next(&pivot, &payload), "Supplier has more elements");
}

uint64_t PackedTableLookup::read_value(const u1* data, size_t length, size_t offset) const {
  if (offset + sizeof(uint64_t) <= length) {
    return *reinterpret_cast<const uint64_t *>(data + offset);
  }
  // slow path for accessing end of array
  uint64_t value = 0;
  for (size_t i = 0; i < sizeof(uint64_t) && offset + i < length; ++i) {
    value = value | (data[offset + i] << (i * 8));
  }
  return value;
}

bool PackedTableLookup::search(Comparator& comparator, const Array<u1>* search_table, uint32_t* found_pivot, uint32_t* found_payload) const {
  unsigned int low = 0, high = search_table->length() / _element_bytes;
  assert(low < high, "must be");
  const u1 *data = search_table->data();
  while (low < high) {
    unsigned int mid = low + (high - low) / 2;
    assert(mid >= low && mid < high, "integer overflow?");
    uint64_t value = read_value(data, static_cast<size_t>(search_table->length()), _element_bytes * mid);
    uint32_t pivot = value & _pivot_mask;
    int cmp = comparator.compare_to(pivot);
    if (cmp == 0) {
      *found_pivot = pivot;
      *found_payload = (value >> _payload_shift) & _payload_mask;
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
void PackedTableLookup::validate_order(Comparator &comparator, const Array<u1> *search_table) const {
  const u1* data = search_table->data();
  size_t length = static_cast<size_t>(search_table->length());
  for (size_t offset = 0; offset < length; offset += _element_bytes) {
    uint64_t value = read_value(data, length, offset);
    uint32_t pivot = value & _pivot_mask;

    if (offset != 0) {
      assert(comparator.compare_to(pivot) < 0, "not sorted");
    }
    comparator.reset(pivot);
  }
}
#endif
