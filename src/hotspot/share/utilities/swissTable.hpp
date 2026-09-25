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

#ifndef SHARE_UTILITIES_SWISSTABLE_HPP
#define SHARE_UTILITIES_SWISSTABLE_HPP

#include "cppstdlib/cstdlib.hpp"
#include "cppstdlib/limits.hpp"
#include "cppstdlib/type_traits.hpp"
#include "utilities/align.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

#ifdef AMD64
#include "vm_version_x86.hpp"
#endif // AMD64

// A Swiss table is an open-addressing hash table implementation that uses a small side table for
// fast lookup. A 64-bit hash value is divided into 2 parts, H1 consists of the last 7 bits, and H2
// is the remaining 57 bits. Each element in the side table will then contain either the 7-bit H1
// value of the key corresponding to that bucket, or a marker value which signifies that the bucket
// is empty, or the entry there has been removed. The marker values are negative, while a H1 value
// is always positive, so there will be no ambiguity. Another marker value denotes out-of-bound
// buckets which are allocated so the algorithm can freely read the metadata table even at the very
// end.
//
// When performing a lookup, we use H2 of the key to compute an index in the tables. Starting from
// that index, we traverse the side table to find an element that has the stored H1 value match H1
// of the looked up key, then compare the key stored in the main table. The strength of Swiss table
// comes from the fact that each element in the side table is very small, which allows us to
// operate on multiple of them at a time using simd instructions.
//
// This is the base implementation. It is implemented focusing on generality instead of usability.
// Users looking for a hash table should use one of the derived implementations of this class, or
// build one themselves if it is necessary.
template <class Entry, class Allocator>
class SwissTableImpl {
private:
  class LookupResult {
  private:
    bool _exist;
    size_t _idx;

  public:
    LookupResult(bool exist, size_t idx) : _exist(exist), _idx(idx) {}
    bool exist() const { return _exist; }
    size_t idx() const { return _idx; }
  };

public:
  class FindResult {
  public:
    enum Result {
      FOUND,
      NOT_EXIST,
    };

  private:
    Result _result;
    const Entry* _entry;

  public:
    FindResult(Result result, const Entry* entry) : _result(result), _entry(entry) {}
    Result result() const { return _result; }
    const Entry* entry() { return _entry; }
  };

  class EmplaceResult {
  public:
    enum Result {
      EXIST,
      NOT_EXIST,
      FAIL_TO_ALLOCATE,
    };

  private:
    Result _result;

  public:
    EmplaceResult(Result result) : _result(result) {}
    Result result() const { return _result; }
  };

  class EraseResult {
  public:
    enum Result {
      ERASED,
      NOT_EXIST,
    };

  private:
    Result _result;

  public:
    EraseResult(Result result) : _result(result) {}
    Result result() const { return _result; }
  };

private:
  static constexpr uint8_t _empty_marker = -1;
  static constexpr uint8_t _removed_marker = -2;
  static constexpr uint8_t _oob_marker = -3;
  static constexpr int _h2_shift = 7;
  static constexpr uint64_t _h1_mask = right_n_bits(_h2_shift);
  static constexpr size_t _no_insert_point = -1;
  static constexpr size_t _min_table_size = 8;

  Allocator _alloc;
  uint8_t* _metadata;
  Entry* _table;
  size_t _table_size_minus_one;
  size_t _size_include_removed;
  double _load_factor;
  size_t _size;

  // Implementation limitations
  static_assert(std::is_trivially_destructible_v<Entry>);

  static double default_load_factor();
  static size_t metadata_out_of_bounds_size();

  static size_t max_table_size() {
    static_assert(is_power_of_2(_min_table_size));
    constexpr size_t max_allocatable = std::numeric_limits<size_t>::max() >> 1;
    assert(max_allocatable > metadata_out_of_bounds_size(), "impossible values %zu - %zu", max_allocatable, metadata_out_of_bounds_size());
    size_t max_allocatable_table_size = (max_allocatable - metadata_out_of_bounds_size() - alignof(Entry)) / (sizeof(Entry) + sizeof(uint8_t));
    assert(max_allocatable_table_size >= _min_table_size, "impossible values %zu - %zu", max_allocatable_table_size, _min_table_size);
    size_t max_table_size = round_down_power_of_2(max_allocatable_table_size);
    return max_table_size;
  }

  // The most basic block of the data structure. Token and TOKEN_HASH_MATCH are parameterized so
  // that derived implementations can support looking up a key using a token without the need to
  // construct an actual key.
  template <class Token, auto TOKEN_HASH_MATCH>
  static LookupResult lookup(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                             uint64_t hash, const Token& token) {
    assert(table != nullptr, "must have been initialized");
    uint64_t h2 = hash >> _h2_shift;
    size_t start_idx = h2 & table_size_minus_one;

    uint8_t first_metadata = metadata[start_idx];
    if (first_metadata == _empty_marker) {
      return LookupResult(false, start_idx);
    }

    if (uint64_t h1 = hash & _h1_mask; first_metadata == h1) {
      const Entry& entry = table[start_idx];
      if (TOKEN_HASH_MATCH(token, hash, entry)) {
        return LookupResult(true, start_idx);
      }
    }

    size_t insert_point = _no_insert_point;
    if (first_metadata == _removed_marker) {
      insert_point = start_idx;
    }
    return pd_lookup<Token, TOKEN_HASH_MATCH>(metadata, table, table_size_minus_one, hash, token, start_idx + 1, insert_point);
  }

  bool should_grow() {
    size_t size_to_grow = size_t(double(_table_size_minus_one + 1) * _load_factor);
    return _size_include_removed >= size_to_grow;
  }

  static bool cannot_match(uint64_t, int, const Entry&) {
    return false;
  }

  bool grow() {
    size_t table_size = _table_size_minus_one + 1;
    if (table_size == max_table_size()) {
      return false;
    }

    size_t new_table_size = MAX2(table_size * 2, _min_table_size);
    assert(is_power_of_2(new_table_size), "invalid table size %zu", new_table_size);
    size_t metadata_size_in_bytes = sizeof(uint8_t) * new_table_size;
    size_t table_offset = align_up(metadata_size_in_bytes + metadata_out_of_bounds_size(), alignof(Entry));
    size_t allocated_size = table_offset + sizeof(Entry) * new_table_size;
    void* allocated = _alloc.allocate(allocated_size);
    if (allocated == nullptr) {
      return false;
    }

    uint8_t* new_metadata = static_cast<uint8_t*>(allocated);
    Entry* new_table = reinterpret_cast<Entry*>(new_metadata + table_offset);
    size_t new_table_size_minus_one = new_table_size - 1;
    ::memset(new_metadata, _empty_marker, metadata_size_in_bytes);
    ::memset(new_metadata + metadata_size_in_bytes, _oob_marker, metadata_out_of_bounds_size());
    
    for (size_t idx = 0; idx < table_size; idx++) {
      uint8_t cur_metadata = _metadata[idx];
      if (int8_t(cur_metadata) < 0) {
        continue;
      }

      const auto& entry = _table[idx];
      uint64_t hash = entry.hash();
      int dummy_token = 0;
      auto lookup_res = lookup<int, cannot_match>(new_metadata, new_table, new_table_size_minus_one, hash, dummy_token);

      assert(!lookup_res.exist(), "must not exist");
      size_t insert_point = lookup_res.idx();
      assert(insert_point < new_table_size, "unexpected insert point %zu out-of-bound %zu", insert_point, new_table_size);
      assert(new_metadata[insert_point] == _empty_marker, "must be empty but %u", new_metadata[insert_point]);
      uint64_t h1 = hash & _h1_mask;
      new_metadata[insert_point] = h1;
      ::new(&new_table[insert_point]) Entry(entry);
    }

    _alloc.deallocate(_metadata);
    _metadata = new_metadata;
    _table = new_table;
    _table_size_minus_one = new_table_size_minus_one;
    _size_include_removed = _size;
    return true;
  }

  template <class Token, auto TOKEN_HASH_MATCH>
  static LookupResult pd_lookup(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);

  template <class Token, auto TOKEN_HASH_MATCH>
  static LookupResult pd_lookup_fallback(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                          uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
    uint64_t h1 = hash & _h1_mask;
    for (size_t idx = start_idx;; idx++) {
      if (idx > table_size_minus_one) {
        idx = 0;
      }

      uint8_t cur_metadata = metadata[idx];
      if (cur_metadata == _empty_marker) {
        return LookupResult(false, insert_point == _no_insert_point ? idx : insert_point);
      }

      if (insert_point == _no_insert_point && cur_metadata == _removed_marker) {
        insert_point = idx;
      }

      if (cur_metadata == h1 && TOKEN_HASH_MATCH(token, hash, table[idx])) {
        return LookupResult(true, idx);
      }
    }
  }

#ifdef AMD64
  template <class Token, auto TOKEN_HASH_MATCH>
  static LookupResult pd_lookup_avx512(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                       uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);

  template <class Token, auto TOKEN_HASH_MATCH>
  static LookupResult pd_lookup_avx2(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                     uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);

  template <class Token, auto TOKEN_HASH_MATCH>
  static LookupResult pd_lookup_sse2(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                     uint64_t hash, const Token& token, size_t start_idx, size_t insert_point);
#endif // AMD64

public:
  SwissTableImpl(Allocator alloc)
    : _alloc(alloc), _metadata(nullptr), _table(nullptr), _table_size_minus_one(std::numeric_limits<size_t>::max()),
      _size_include_removed(0), _load_factor(default_load_factor()), _size(0) {
    assert(_load_factor >= 0.1 && _load_factor < 1, "invalid load factor %lf", _load_factor);
  }

  ~SwissTableImpl() {
    _alloc.deallocate(_metadata);
  }

  jlong size() const {
    return checked_cast<jlong>(_size);
  }

  template <class Token, auto TOKEN_HASH_MATCH>
  FindResult find(uint64_t hash, const Token& token) const {
    if (_table == nullptr) {
      return FindResult(FindResult::NOT_EXIST, nullptr);
    }

    auto lookup_res = lookup<Token, TOKEN_HASH_MATCH>(_metadata, _table, _table_size_minus_one, hash, token);
    if (lookup_res.exist()) {
      return FindResult(FindResult::FOUND, &_table[lookup_res.idx()]);
    } else {
      return FindResult(FindResult::NOT_EXIST, nullptr);
    }
  }

  template <class Token, auto TOKEN_HASH_MATCH, class EmplaceEntry>
  EmplaceResult emplace(uint64_t hash, const Token& token, EmplaceEntry emplace_entry) {
    bool must_not_insert_new = should_grow() && !grow();
    auto lookup_res = lookup<Token, TOKEN_HASH_MATCH>(_metadata, _table, _table_size_minus_one, hash, token);
    size_t insert_point = lookup_res.idx();
    uint8_t old_metadata = _metadata[insert_point];
    assert(lookup_res.exist() == (int8_t(old_metadata) >= 0), "inconsistent");

    if (int8_t(old_metadata) < 0) {
      if (old_metadata == _empty_marker) {
        if (must_not_insert_new) {
          return EmplaceResult(EmplaceResult::FAIL_TO_ALLOCATE);
        }
        _size_include_removed++;
      }
      _size++;
      assert(_size <= _size_include_removed, "inconsistent");
      uint64_t h1 = hash & _h1_mask;
      _metadata[insert_point] = h1;
    }

    Entry* entry = &_table[insert_point];
    emplace_entry(lookup_res.exist(), entry);
    return EmplaceResult(lookup_res.exist() ? EmplaceResult::EXIST : EmplaceResult::NOT_EXIST);
  }

  template <class Token, auto TOKEN_HASH_MATCH, class ExtractEntry>
  EraseResult erase(uint64_t hash, const Token& token, ExtractEntry extract_entry) {
    if (_table == nullptr) {
      return EraseResult(EraseResult::NOT_EXIST);
    }

    auto lookup_res = lookup<Token, TOKEN_HASH_MATCH>(_metadata, _table, _table_size_minus_one, hash, token);
    if (!lookup_res.exist()) {
      return EraseResult(EraseResult::NOT_EXIST);
    }

    size_t idx = lookup_res.idx();
    extract_entry(&_table[idx]);
    assert(int8_t(_metadata[idx]) >= 0, "inconsistent");
    _metadata[idx] = _removed_marker;
    _size--;
    return EraseResult(EraseResult::ERASED);
  }
};

#ifdef AMD64

#ifdef __GNUC__
static bool should_run_avx512() {
  static bool res = VM_Version::supports_bmi1() && VM_Version::supports_avx512bw();
  return res;
}

static bool should_run_avx2() {
  static bool res = VM_Version::supports_bmi1() && VM_Version::supports_avx2();
  return res;
}

#else // __GNUC__

// MSVC does not support function-scope target, so we can only use the lowest configuration
static bool should_run_avx512() {
  return false;
}

static bool should_run_avx2() {
  return false;
}
#endif // __GNUC__

template <class Entry, class Allocator>
double SwissTableImpl<Entry, Allocator>::default_load_factor() {
  if (should_run_avx512()) {
    // If 10% of the buckets are empty, we have a 99.9% chance to encounter one of them in the
    // first 64 buckets
    return 0.9;
  } else if (should_run_avx2()) {
    // There is a 99.4% chance to encounter an empty bucket in the first 32 buckets
    return 0.85;
  } else {
    // There is a 99% chance to encounter an empty bucket in the first 16 buckets
    return 0.75;
  }
}

template <class Entry, class Allocator>
size_t SwissTableImpl<Entry, Allocator>::metadata_out_of_bounds_size() {
  if (should_run_avx512()) {
    return 63;
  } else if (should_run_avx2()) {
    return 31;
  } else {
    return 15;
  }
}

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH>
typename SwissTableImpl<Entry, Allocator>::LookupResult
SwissTableImpl<Entry, Allocator>::pd_lookup(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                            uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
#ifdef __GNUC__
  if (should_run_avx512()) {
    return pd_lookup_avx512<Token, TOKEN_HASH_MATCH>(metadata, table, table_size_minus_one, hash, token, start_idx, insert_point);
  } else if (should_run_avx2()) {
    return pd_lookup_avx2<Token, TOKEN_HASH_MATCH>(metadata, table, table_size_minus_one, hash, token, start_idx, insert_point);
  }
#endif // __GNUC__

  return pd_lookup_sse2<Token, TOKEN_HASH_MATCH>(metadata, table, table_size_minus_one, hash, token, start_idx, insert_point);
}

#ifdef __GNUC__
// Each function needs to be declared separately, so that the compiler does not use AVX512
// instructions when only AVX2 is available. The repetition is unfortunate, but using macro would
// make it really hard to read and trace.
template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH>
__attribute__((target("bmi,avx512bw")))
typename SwissTableImpl<Entry, Allocator>::LookupResult
SwissTableImpl<Entry, Allocator>::pd_lookup_avx512(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                                   uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
  constexpr size_t vector_size = sizeof(__m512i);
  uint64_t h1 = hash & _h1_mask;
  __m512i h1_vec = _mm512_set1_epi8(h1);
  __m512i empty_marker_vec = _mm512_set1_epi8(_empty_marker);
  __m512i removed_marker_vec = _mm512_set1_epi8(_removed_marker);

  size_t idx = start_idx;
  while (true) {
    if (idx > table_size_minus_one) {
      idx = 0;
    }

    __m512i cur_vec = _mm512_loadu_epi8(&metadata[idx]);
    uint64_t empty_mask = _cvtmask64_u64(_mm512_cmpeq_epi8_mask(cur_vec, empty_marker_vec));
    size_t empty_off = _tzcnt_u64(empty_mask);

    uint64_t match_mask = _cvtmask64_u64(_mm512_cmpeq_epi8_mask(cur_vec, h1_vec));
    while (match_mask != 0) {
      size_t match_off = _tzcnt_u64(match_mask);
      if (match_off > empty_off) {
        break;
      }

      size_t match_idx = idx + match_off;
      assert(match_idx <= table_size_minus_one, "match_idx %zu out-of-bounds for table size %zu", match_idx, table_size_minus_one + 1);
      const Entry& entry = table[match_idx];
      if (TOKEN_HASH_MATCH(token, hash, entry)) {
        return LookupResult(true, match_idx);
      }

      match_mask &= (match_mask - 1);
    }

    if (insert_point == _no_insert_point) {
      uint64_t removed_mask = _cvtmask64_u64(_mm512_cmpeq_epi8_mask(cur_vec, removed_marker_vec));
      size_t removed_off = _tzcnt_u64(removed_mask);
      size_t insert_point_off = MIN2(empty_off, removed_off);
      if (insert_point_off < vector_size) {
        insert_point = idx + insert_point_off;
        assert(insert_point <= table_size_minus_one, "insert_point %zu out-of-bounds for table size %zu", insert_point, table_size_minus_one + 1);
      }
    }

    if (empty_off < vector_size) {
      assert(insert_point != _no_insert_point, "must found an insertion point");
      return LookupResult(false, insert_point);
    }

    idx += vector_size;
    assert(idx >= start_idx + vector_size || idx < start_idx, "infinite loop");
  }
}

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH>
__attribute__((target("bmi,avx2")))
typename SwissTableImpl<Entry, Allocator>::LookupResult
SwissTableImpl<Entry, Allocator>::pd_lookup_avx2(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                                 uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
  constexpr size_t vector_size = sizeof(__m256i);
  uint64_t h1 = hash & _h1_mask;
  __m256i h1_vec = _mm256_set1_epi8(h1);
  __m256i empty_marker_vec = _mm256_set1_epi8(_empty_marker);
  __m256i removed_marker_vec = _mm256_set1_epi8(_removed_marker);

  size_t idx = start_idx;
  while (true) {
    if (idx > table_size_minus_one) {
      idx = 0;
    }

    __m256i cur_vec = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(&metadata[idx]));
    uint32_t empty_mask = _mm256_movemask_epi8(_mm256_cmpeq_epi8(cur_vec, empty_marker_vec));
    size_t empty_off = _tzcnt_u32(empty_mask);

    uint32_t match_mask = _mm256_movemask_epi8(_mm256_cmpeq_epi8(cur_vec, h1_vec));
    while (match_mask != 0) {
      size_t match_off = _tzcnt_u32(match_mask);
      if (match_off > empty_off) {
        break;
      }

      size_t match_idx = idx + match_off;
      assert(match_idx <= table_size_minus_one, "match_idx %zu out-of-bounds for table size %zu", match_idx, table_size_minus_one + 1);
      const Entry& entry = table[match_idx];
      if (TOKEN_HASH_MATCH(token, hash, entry)) {
        return LookupResult(true, match_idx);
      }

      match_mask &= (match_mask - 1);
    }

    if (insert_point == _no_insert_point) {
      uint32_t removed_mask = _mm256_movemask_epi8(_mm256_cmpeq_epi8(cur_vec, removed_marker_vec));
      size_t removed_off = _tzcnt_u32(removed_mask);
      size_t insert_point_off = MIN2(empty_off, removed_off);
      if (insert_point_off < vector_size) {
        insert_point = idx + insert_point_off;
        assert(insert_point <= table_size_minus_one, "insert_point %zu out-of-bounds for table size %zu", insert_point, table_size_minus_one + 1);
      }
    }

    if (empty_off < vector_size) {
      assert(insert_point != _no_insert_point, "must found an insertion point");
      return LookupResult(false, insert_point);
    }

    idx += vector_size;
    assert(idx >= start_idx + vector_size || idx < start_idx, "infinite loop");
  }
}
#endif // __GNUC__

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH>
typename SwissTableImpl<Entry, Allocator>::LookupResult
SwissTableImpl<Entry, Allocator>::pd_lookup_sse2(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                                 uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
  constexpr size_t vector_size = sizeof(__m128i);
  uint64_t h1 = hash & _h1_mask;
  __m128i h1_vec = _mm_set1_epi8(h1);
  __m128i empty_marker_vec = _mm_set1_epi8(_empty_marker);
  __m128i removed_marker_vec = _mm_set1_epi8(_removed_marker);

  size_t idx = start_idx;
  while (true) {
    if (idx > table_size_minus_one) {
      idx = 0;
    }

    __m128i cur_vec = _mm_loadu_si128(reinterpret_cast<const __m128i*>(&metadata[idx]));
    uint32_t empty_mask = _mm_movemask_epi8(_mm_cmpeq_epi8(cur_vec, empty_marker_vec));
    size_t empty_off = count_trailing_zeros(empty_mask | 0x10000);

    uint32_t match_mask = _mm_movemask_epi8(_mm_cmpeq_epi8(cur_vec, h1_vec));
    while (match_mask != 0) {
      size_t match_off = count_trailing_zeros(match_mask | 0x10000);
      if (match_off > empty_off) {
        break;
      }

      size_t match_idx = idx + match_off;
      assert(match_idx <= table_size_minus_one, "match_idx %zu out-of-bounds for table size %zu", match_idx, table_size_minus_one + 1);
      const Entry& entry = table[match_idx];
      if (TOKEN_HASH_MATCH(token, hash, entry)) {
        return LookupResult(true, match_idx);
      }

      match_mask &= (match_mask - 1);
    }

    if (insert_point == _no_insert_point) {
      uint32_t removed_mask = _mm_movemask_epi8(_mm_cmpeq_epi8(cur_vec, removed_marker_vec));
      size_t removed_off = count_trailing_zeros(removed_mask | 0x10000);
      size_t insert_point_off = MIN2(empty_off, removed_off);
      if (insert_point_off < vector_size) {
        insert_point = idx + insert_point_off;
        assert(insert_point <= table_size_minus_one, "insert_point %zu out-of-bounds for table size %zu", insert_point, table_size_minus_one + 1);
      }
    }

    if (empty_off < vector_size) {
      assert(insert_point != _no_insert_point, "must found an insertion point");
      return LookupResult(false, insert_point);
    }

    idx += vector_size;
    assert(idx >= start_idx + vector_size || idx < start_idx, "infinite loop");
  }
}

#else // AMD64

template <class Entry, class Allocator>
double SwissTableImpl<Entry, Allocator>::default_load_factor() {
  // Not much guarantee when the entries are processed one by one
  return 0.75;
}

template <class Entry, class Allocator>
size_t SwissTableImpl<Entry, Allocator>::metadata_out_of_bounds_size() {
  return 0;
}

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH>
typename SwissTableImpl<Entry, Allocator>::LookupResult
SwissTableImpl<Entry, Allocator>::pd_lookup(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                            uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
  return pd_lookup_fallback<Token, TOKEN_HASH_MATCH>(metadata, table, table_size_minus_one, hash, token, start_idx, insert_point);
}

#endif // AMD64

#endif // SHARE_UTILITIES_SWISSTABLE_HPP
