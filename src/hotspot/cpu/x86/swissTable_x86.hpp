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

#ifndef CPU_X86_SWISSTABLE_X86_HPP
#define CPU_X86_SWISSTABLE_X86_HPP

#include "cppstdlib/cstdlib.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/swissTable.hpp"

template <class Entry, class Allocator>
class PDSwissTableImpl {
private:
  using LookupResult = typename SwissTableImpl<Entry, Allocator>::LookupResult;
  using Marker = typename SwissTableImpl<Entry, Allocator>::Marker;
  using InsertPointNecessity = typename SwissTableImpl<Entry, Allocator>::InsertPointNecessity;

  static constexpr uint64_t _h2_mask = SwissTableImpl<Entry, Allocator>::_h2_mask;
  static constexpr size_t _no_insert_point = SwissTableImpl<Entry, Allocator>::_no_insert_point;

  static bool should_use_avx512();
  static bool should_use_avx2();

  template <class Token, auto TOKEN_HASH_MATCH, InsertPointNecessity INSERT_POINT_NECESSITY>
  static LookupResult look_up_avx512(const Marker* metadata, const Entry* table, size_t table_size_minus_one,
                                     uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);

  template <class Token, auto TOKEN_HASH_MATCH, InsertPointNecessity INSERT_POINT_NECESSITY>
  static LookupResult look_up_avx2(const Marker* metadata, const Entry* table, size_t table_size_minus_one,
                                   uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);

  template <class Token, auto TOKEN_HASH_MATCH, InsertPointNecessity INSERT_POINT_NECESSITY>
  static LookupResult look_up_sse2(const Marker* metadata, const Entry* table, size_t table_size_minus_one,
                                   uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);

public:
  static double default_load_factor();
  static size_t metadata_out_of_bounds_size();

  template <class Token, auto TOKEN_HASH_MATCH, InsertPointNecessity INSERT_POINT_NECESSITY>
  static LookupResult look_up(const Marker* metadata, const Entry* table, size_t table_size_minus_one,
                             uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);
};

#ifdef __GNUC__
template <class Entry, class Allocator>
bool PDSwissTableImpl<Entry, Allocator>::should_use_avx512() {
  static bool res = VM_Version::supports_bmi1() && VM_Version::supports_avx512bw();
  return res;
}

template <class Entry, class Allocator>
bool PDSwissTableImpl<Entry, Allocator>::should_use_avx2() {
  static bool res = VM_Version::supports_bmi1() && VM_Version::supports_avx2();
  return res;
}

#else // __GNUC__

// MSVC does not support function-scope target, so we can only use the lowest configuration
template <class Entry, class Allocator>
bool PDSwissTableImpl<Entry, Allocator>::should_use_avx512() {
  return false;
}

template <class Entry, class Allocator>
bool PDSwissTableImpl<Entry, Allocator>::should_use_avx2() {
  return false;
}
#endif // __GNUC__

template <class Entry, class Allocator>
double PDSwissTableImpl<Entry, Allocator>::default_load_factor() {
  if (should_use_avx512()) {
    // If 10% of the buckets are empty, we have a 99.9% chance to encounter one of them in the
    // first 64 buckets
    return 0.9;
  } else if (should_use_avx2()) {
    // There is a 99.4% chance to encounter an empty bucket in the first 32 buckets
    return 0.85;
  } else {
    // There is a 99% chance to encounter an empty bucket in the first 16 buckets
    return 0.75;
  }
}

template <class Entry, class Allocator>
size_t PDSwissTableImpl<Entry, Allocator>::metadata_out_of_bounds_size() {
  if (should_use_avx512()) {
    return 63;
  } else if (should_use_avx2()) {
    return 31;
  } else {
    return 15;
  }
}

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH, typename SwissTableImpl<Entry, Allocator>::InsertPointNecessity INSERT_POINT_NECESSITY>
typename SwissTableImpl<Entry, Allocator>::LookupResult
PDSwissTableImpl<Entry, Allocator>::look_up(const Marker* metadata, const Entry* table, size_t table_size_minus_one,
                                            uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
#ifdef __GNUC__
  if (should_use_avx512()) {
    return look_up_avx512<Token, TOKEN_HASH_MATCH, INSERT_POINT_NECESSITY>(metadata, table, table_size_minus_one, hash, token, start_idx, insert_point);
  } else if (should_use_avx2()) {
    return look_up_avx2<Token, TOKEN_HASH_MATCH, INSERT_POINT_NECESSITY>(metadata, table, table_size_minus_one, hash, token, start_idx, insert_point);
  }
#endif // __GNUC__

  return look_up_sse2<Token, TOKEN_HASH_MATCH, INSERT_POINT_NECESSITY>(metadata, table, table_size_minus_one, hash, token, start_idx, insert_point);
}

#ifdef __GNUC__
// Each function needs to be declared separately, so that the compiler does not use AVX512
// instructions when only AVX2 is available. The repetition is unfortunate, but using macro would
// make it really hard to read and trace.
template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH, typename SwissTableImpl<Entry, Allocator>::InsertPointNecessity INSERT_POINT_NECESSITY>
__attribute__((target("bmi,avx512bw")))
typename SwissTableImpl<Entry, Allocator>::LookupResult
PDSwissTableImpl<Entry, Allocator>::look_up_avx512(const Marker* metadata, const Entry* table, size_t table_size_minus_one,
                                                   uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
  constexpr size_t vector_size = sizeof(__m512i);
  size_t idx = start_idx;
  while (true) {
    if (idx > table_size_minus_one) {
      idx = 0;
    }

    __m512i cur_vec = _mm512_loadu_si512(&metadata[idx]);

    __m512i empty_marker_vec = _mm512_set1_epi8(uint8_t(Marker::_empty_marker));
    uint64_t empty_mask = _cvtmask64_u64(_mm512_cmpeq_epi8_mask(cur_vec, empty_marker_vec));
    // Can only match up to the first empty bucket
    uint64_t match_valid_mask = _blsmsk_u64(empty_mask);

    uint64_t h2 = hash & _h2_mask;
    __m512i h2_vec = _mm512_set1_epi8(h2);
    // Each bit 1 in this mask corresponds to a bucket in which the h2 value matches that of the
    // current token, counting from the lowest bit
    uint64_t match_mask = match_valid_mask & _cvtmask64_u64(_mm512_cmpeq_epi8_mask(cur_vec, h2_vec));
    while (match_mask != 0) {
      size_t match_off = _tzcnt_u64(match_mask);
      size_t match_idx = idx + match_off;
      assert(match_idx <= table_size_minus_one, "match_idx %zu out-of-bounds for table size %zu", match_idx, table_size_minus_one + 1);
      const Entry& entry = table[match_idx];
      if (TOKEN_HASH_MATCH(token, hash, entry)) {
        return LookupResult(true, match_idx);
      }

      // Unset the lowest bit of match_mask, the implication is that we are done with the bucket
      // corresponding to the lowest set bit, so we continue to the second lowest one
      match_mask = _blsr_u64(match_mask);
    }

    // Find an insert point if none has been found, it should be the first bucket that either is
    // empty or is a tombstone
    if (INSERT_POINT_NECESSITY == InsertPointNecessity::_necessary && insert_point == _no_insert_point) {
      __m512i tombstone_marker_vec = _mm512_set1_epi8(uint8_t(Marker::_tombstone_marker));
      uint64_t tombstone_mask = _cvtmask64_u64(_mm512_cmpeq_epi8_mask(cur_vec, tombstone_marker_vec));
      uint64_t insert_point_mask = tombstone_mask | empty_mask;

      if (insert_point_mask != 0) {
        size_t insert_point_off = _tzcnt_u64(insert_point_mask);
        insert_point = idx + insert_point_off;
        assert(insert_point <= table_size_minus_one, "insert_point %zu out-of-bounds for table size %zu", insert_point, table_size_minus_one + 1);
      }
    }

    // If we find no match in this batch, but there is an empty bucket, it means the entry we want
    // to find is not in the table
    if (empty_mask != 0) {
      assert(INSERT_POINT_NECESSITY == InsertPointNecessity::_unnecessary || insert_point != _no_insert_point, "must have found an insertion point");
      return LookupResult(false, insert_point);
    }

    idx += vector_size;
    assert(idx >= start_idx + vector_size || idx < start_idx, "infinite loop");
  }
}

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH, typename SwissTableImpl<Entry, Allocator>::InsertPointNecessity INSERT_POINT_NECESSITY>
__attribute__((target("bmi,avx2")))
typename SwissTableImpl<Entry, Allocator>::LookupResult
PDSwissTableImpl<Entry, Allocator>::look_up_avx2(const Marker* metadata, const Entry* table, size_t table_size_minus_one,
                                                 uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
  constexpr size_t vector_size = sizeof(__m256i);
  size_t idx = start_idx;
  while (true) {
    if (idx > table_size_minus_one) {
      idx = 0;
    }

    __m256i cur_vec = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(&metadata[idx]));

    __m256i empty_marker_vec = _mm256_set1_epi8(uint8_t(Marker::_empty_marker));
    uint32_t empty_mask = _mm256_movemask_epi8(_mm256_cmpeq_epi8(cur_vec, empty_marker_vec));
    uint32_t match_valid_mask = _blsmsk_u32(empty_mask);

    uint64_t h2 = hash & _h2_mask;
    __m256i h2_vec = _mm256_set1_epi8(h2);
    uint32_t match_mask = match_valid_mask & _mm256_movemask_epi8(_mm256_cmpeq_epi8(cur_vec, h2_vec));
    while (match_mask != 0) {
      size_t match_off = _tzcnt_u32(match_mask);
      size_t match_idx = idx + match_off;
      assert(match_idx <= table_size_minus_one, "match_idx %zu out-of-bounds for table size %zu", match_idx, table_size_minus_one + 1);
      const Entry& entry = table[match_idx];
      if (TOKEN_HASH_MATCH(token, hash, entry)) {
        return LookupResult(true, match_idx);
      }

      match_mask = _blsr_u32(match_mask);
    }

    if (INSERT_POINT_NECESSITY == InsertPointNecessity::_necessary && insert_point == _no_insert_point) {
      __m256i tombstone_marker_vec = _mm256_set1_epi8(uint8_t(Marker::_tombstone_marker));
      uint32_t tombstone_mask = _mm256_movemask_epi8(_mm256_cmpeq_epi8(cur_vec, tombstone_marker_vec));
      uint32_t insert_point_mask = tombstone_mask | empty_mask;

      if (insert_point_mask != 0) {
        size_t insert_point_off = _tzcnt_u32(insert_point_mask);
        insert_point = idx + insert_point_off;
        assert(insert_point <= table_size_minus_one, "insert_point %zu out-of-bounds for table size %zu", insert_point, table_size_minus_one + 1);
      }
    }

    if (empty_mask != 0) {
      assert(INSERT_POINT_NECESSITY == InsertPointNecessity::_unnecessary || insert_point != _no_insert_point, "must have found an insertion point");
      return LookupResult(false, insert_point);
    }

    idx += vector_size;
    assert(idx >= start_idx + vector_size || idx < start_idx, "infinite loop");
  }
}
#endif // __GNUC__

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH, typename SwissTableImpl<Entry, Allocator>::InsertPointNecessity INSERT_POINT_NECESSITY>
typename SwissTableImpl<Entry, Allocator>::LookupResult
PDSwissTableImpl<Entry, Allocator>::look_up_sse2(const Marker* metadata, const Entry* table, size_t table_size_minus_one,
                                                 uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
  constexpr size_t vector_size = sizeof(__m128i);
  size_t idx = start_idx;
  while (true) {
    if (idx > table_size_minus_one) {
      idx = 0;
    }

    __m128i cur_vec = _mm_loadu_si128(reinterpret_cast<const __m128i*>(&metadata[idx]));

    __m128i empty_marker_vec = _mm_set1_epi8(uint8_t(Marker::_empty_marker));
    uint32_t empty_mask = _mm_movemask_epi8(_mm_cmpeq_epi8(cur_vec, empty_marker_vec));
    // This is the C++ portable version of blsmsk, see x86 documentation
    uint32_t match_valid_mask = empty_mask ^ (empty_mask - 1);

    uint64_t h2 = hash & _h2_mask;
    __m128i h2_vec = _mm_set1_epi8(h2);
    uint32_t match_mask = match_valid_mask & _mm_movemask_epi8(_mm_cmpeq_epi8(cur_vec, h2_vec));
    while (match_mask != 0) {
      size_t match_off = count_trailing_zeros(match_mask | 0x10000);
      size_t match_idx = idx + match_off;
      assert(match_idx <= table_size_minus_one, "match_idx %zu out-of-bounds for table size %zu", match_idx, table_size_minus_one + 1);
      const Entry& entry = table[match_idx];
      if (TOKEN_HASH_MATCH(token, hash, entry)) {
        return LookupResult(true, match_idx);
      }

      // This is the C++ portable version of blsr, see x86 documentation
      match_mask &= (match_mask - 1);
    }

    if (INSERT_POINT_NECESSITY == InsertPointNecessity::_necessary && insert_point == _no_insert_point) {
      __m128i tombstone_marker_vec = _mm_set1_epi8(uint8_t(Marker::_tombstone_marker));
      uint32_t tombstone_mask = _mm_movemask_epi8(_mm_cmpeq_epi8(cur_vec, tombstone_marker_vec));
      uint32_t insert_point_mask = tombstone_mask | empty_mask;

      if (insert_point_mask != 0) {
        size_t insert_point_off = count_trailing_zeros(insert_point_mask);
        insert_point = idx + insert_point_off;
        assert(insert_point <= table_size_minus_one, "insert_point %zu out-of-bounds for table size %zu", insert_point, table_size_minus_one + 1);
      }
    }

    if (empty_mask != 0) {
      assert(INSERT_POINT_NECESSITY == InsertPointNecessity::_unnecessary || insert_point != _no_insert_point, "must have found an insertion point");
      return LookupResult(false, insert_point);
    }

    idx += vector_size;
    assert(idx >= start_idx + vector_size || idx < start_idx, "infinite loop");
  }
}

#endif // CPU_X86_SWISSTABLE_X86_HPP
