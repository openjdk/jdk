/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_AARCH64_SWISSTABLE_AARCH64_HPP
#define CPU_AARCH64_SWISSTABLE_AARCH64_HPP

#include "cppstdlib/limits.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/compilerWarnings.hpp"
#include "utilities/swissTable.hpp"

BEGIN_ALLOW_FORBIDDEN_FUNCTIONS
#include "utilities/vmassert_uninstall.hpp"

#include <arm_neon.h>

#include "utilities/vmassert_reinstall.hpp" // don't reorder
END_ALLOW_FORBIDDEN_FUNCTIONS

template <class Entry, class Allocator>
class PDSwissTableImpl {
private:
  using LookupResult = typename SwissTableImpl<Entry, Allocator>::LookupResult;
  using Marker = typename SwissTableImpl<Entry, Allocator>::Marker;

  static constexpr uint64_t _h1_mask = SwissTableImpl<Entry, Allocator>::_h1_mask;
  static constexpr size_t _no_insert_point = SwissTableImpl<Entry, Allocator>::_no_insert_point;

  template <class Token, auto TOKEN_HASH_MATCH>
  static LookupResult lookup_neon(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                  uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);

public:
  static double default_load_factor();
  static size_t metadata_out_of_bounds_size();

  template <class Token, auto TOKEN_HASH_MATCH>
  static LookupResult lookup(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                             uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);
};

template <class Entry, class Allocator>
double PDSwissTableImpl<Entry, Allocator>::default_load_factor() {
  // There is a 99% chance to encounter an empty bucket in the first 16 buckets
  return 0.75;
}

template <class Entry, class Allocator>
size_t PDSwissTableImpl<Entry, Allocator>::metadata_out_of_bounds_size() {
  return 15;
}

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH>
typename SwissTableImpl<Entry, Allocator>::LookupResult
PDSwissTableImpl<Entry, Allocator>::lookup(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                           uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
  return lookup_neon<Token, TOKEN_HASH_MATCH>(metadata, table, table_size_minus_one, hash, token, start_idx, insert_point);
}

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH>
typename SwissTableImpl<Entry, Allocator>::LookupResult
PDSwissTableImpl<Entry, Allocator>::lookup_neon(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                                uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
  constexpr size_t vector_size = sizeof(uint8x16_t);
  size_t idx = start_idx;
  while (true) {
    if (idx > table_size_minus_one) {
      idx = 0;
    }

    uint8x16_t cur_vec = vld1q_u8(&metadata[idx]);
    uint8x16_t empty_marker_vec = vdupq_n_u8(uint8_t(Marker::_empty_marker));
    uint8x16_t empty_mask = vceqq_u8(cur_vec, empty_marker_vec);

    uint8x16_t iota_vec = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    uint8x16_t max_uint8_t_vec = vdupq_n_u8(std::numeric_limits<uint8_t>::max());
    // An element has the value equal to its index in the vector if the corresponding element in
    // empty_mask is true, otherwise, the element is 0xff
    uint8x16_t empty_index_mask = vbslq_u8(empty_mask, iota_vec, max_uint8_t_vec);

    // The offset of the first bucket that is empty, or vector_size if there is none
    size_t empty_off = vminvq_u8(empty_index_mask);

    uint64_t h1 = hash & _h1_mask;
    uint8x16_t h1_vec = vdupq_n_u8(h1);
    // Each true element in this vector means the corresponding bucket has the same h1 value as the
    // one we want to find
    uint8x16_t match_mask = vceqq_u8(cur_vec, h1_vec);

    // An element has the value equal to its index in the vector if the corresponding element in
    // match_mask is true, otherwise, the element is 0xff
    uint8x16_t match_index_mask = vbslq_u8(match_mask, iota_vec, max_uint8_t_vec);
    while (true) {
      // This is the offset of the first bucket that we need to inspect
      size_t match_off = vminvq_u8(match_index_mask);
      // The bucket we want to find cannot appear after an empty bucket, this also takes care of
      // the case where there is no bucket with matching h1 value
      if (match_off >= empty_off) {
        break;
      }

      size_t match_idx = idx + match_off;
      assert(match_idx <= table_size_minus_one, "match_idx %zu out-of-bounds for table size %zu", match_idx, table_size_minus_one + 1);
      const Entry& entry = table[match_idx];
      if (TOKEN_HASH_MATCH(token, hash, entry)) {
        return LookupResult(true, match_idx);
      }

      // Mask off the element we just inspected in match_index_mask by changing it to 0xff
      uint8x16_t match_off_vec = vdupq_n_u8(match_off);
      // All elements in this vector are 0, except the one at match_off which is 0xff
      uint8x16_t match_off_mask = vceqq_u8(match_off_vec, iota_vec);
      match_index_mask = vorrq_u8(match_index_mask, match_off_mask);
    }

    if (insert_point == _no_insert_point) {
      uint8x16_t tombstone_marker_vec = vdupq_n_u8(uint8_t(Marker::_tombstone_marker));
      uint8x16_t tombstone_mask = vceqq_u8(cur_vec, tombstone_marker_vec);

      // An element has the value equal to its index in the vector if the corresponding element in
      // tombstone_mask is true, otherwise, the element is 0xff
      uint8x16_t tombstone_index_mask = vbslq_u8(tombstone_mask, iota_vec, max_uint8_t_vec);

      // The offset of the first bucket that is a tombstone, or vector_size if there is none
      size_t tombstone_off = vminvq_u8(tombstone_index_mask);

      size_t insert_point_off = MIN2(empty_off, tombstone_off);
      if (insert_point_off < vector_size) {
        insert_point = idx + insert_point_off;
        assert(insert_point <= table_size_minus_one, "insert_point %zu out-of-bounds for table size %zu", insert_point, table_size_minus_one + 1);
      }
    }

    // If we find no match in this batch, but there is an empty bucket, it means the entry we want
    // to find is not in the table
    if (empty_off < vector_size) {
      assert(insert_point != _no_insert_point, "must found an insertion point");
      return LookupResult(false, insert_point);
    }

    idx += vector_size;
    assert(idx >= start_idx + vector_size || idx < start_idx, "infinite loop");
  }
}

#endif // CPU_AARCH64_SWISSTABLE_AARCH64_HPP
