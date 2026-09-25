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
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

// A Swiss table is an open-addressed hash table implementation that uses a small side table for
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
  template <class P1, class P2>
  friend class PDSwissTableImpl;

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
  enum class Marker : uint8_t {
    _empty_marker   = uint8_t(-1),
    _tombstone_marker = uint8_t(-2),
    _oob_marker     = uint8_t(-3),
  };

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
                             uint64_t hash, const Token& token);

  template <class Token, auto TOKEN_HASH_MATCH>
  static LookupResult lookup_fallback(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                      uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
    uint64_t h1 = hash & _h1_mask;
    for (size_t idx = start_idx;; idx++) {
      if (idx > table_size_minus_one) {
        idx = 0;
      }

      uint8_t cur_metadata = metadata[idx];
      if (cur_metadata == uint8_t(Marker::_empty_marker)) {
        return LookupResult(false, insert_point == _no_insert_point ? idx : insert_point);
      }

      if (insert_point == _no_insert_point && cur_metadata == uint8_t(Marker::_tombstone_marker)) {
        insert_point = idx;
      }

      if (cur_metadata == h1 && TOKEN_HASH_MATCH(token, hash, table[idx])) {
        return LookupResult(true, idx);
      }
    }
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

    // It is less verbose to deal with allocation failure when you only do one, so we allocate both
    // of the tables in one call
    void* allocated = _alloc.allocate(allocated_size);
    if (allocated == nullptr) {
      return false;
    }

    uint8_t* new_metadata = static_cast<uint8_t*>(allocated);
    Entry* new_table = reinterpret_cast<Entry*>(new_metadata + table_offset);
    size_t new_table_size_minus_one = new_table_size - 1;
    ::memset(new_metadata, uint8_t(Marker::_empty_marker), metadata_size_in_bytes);
    ::memset(new_metadata + metadata_size_in_bytes, uint8_t(Marker::_oob_marker), metadata_out_of_bounds_size());

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
      assert(new_metadata[insert_point] == uint8_t(Marker::_empty_marker), "must be empty but %u", new_metadata[insert_point]);
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

public:
  SwissTableImpl(Allocator alloc)
    : _alloc(alloc), _metadata(nullptr), _table(nullptr), _table_size_minus_one(std::numeric_limits<size_t>::max()),
      _size_include_removed(0), _load_factor(default_load_factor()), _size(0) {
    assert(_load_factor >= 0.1 && _load_factor < 1, "invalid load factor %lf", _load_factor);
  }

  ~SwissTableImpl() {
    _alloc.deallocate(_metadata);
  }

  size_t size() const {
    return _size;
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
      if (old_metadata == uint8_t(Marker::_empty_marker)) {
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
    _metadata[idx] = uint8_t(Marker::_tombstone_marker);
    _size--;
    return EraseResult(EraseResult::ERASED);
  }
};

#if __has_include(CPU_HEADER(swissTable))

#include CPU_HEADER(swissTable)

#else // __has_include(CPU_HEADER(swissTable))

template <class Entry, class Allocator>
class PDSwissTableImpl {
public:
  static double default_load_factor() {
    // Not much guarantee when the entries are processed one by one
    return 0.75;
  }

  static size_t metadata_out_of_bounds_size() {
    return 0;
  }

  template <class Token, auto TOKEN_HASH_MATCH>
  static typename SwissTableImpl<Entry, Allocator>::LookupResult
  lookup(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
         uint64_t hash, const Token& token, size_t start_idx, size_t insert_point) {
    return SwissTableImpl<Entry, Allocator>::template lookup_fallback<Token, TOKEN_HASH_MATCH>(metadata, table, table_size_minus_one,
                                                                                               hash, token, start_idx, insert_point);
  }
};

#endif // __has_include(CPU_HEADER(swissTable))

template <class Entry, class Allocator>
double SwissTableImpl<Entry, Allocator>::default_load_factor() {
  return PDSwissTableImpl<Entry, Allocator>::default_load_factor();
}

template <class Entry, class Allocator>
size_t SwissTableImpl<Entry, Allocator>::metadata_out_of_bounds_size() {
  return PDSwissTableImpl<Entry, Allocator>::metadata_out_of_bounds_size();
}

template <class Entry, class Allocator>
template <class Token, auto TOKEN_HASH_MATCH>
typename SwissTableImpl<Entry, Allocator>::LookupResult
SwissTableImpl<Entry, Allocator>::lookup(const uint8_t* metadata, const Entry* table, size_t table_size_minus_one,
                                         uint64_t hash, const Token& token) {
  assert(table != nullptr, "must have been initialized");
  uint64_t h2 = hash >> _h2_shift;
  size_t start_idx = h2 & table_size_minus_one;

  uint8_t first_metadata = metadata[start_idx];
  if (first_metadata == uint8_t(Marker::_empty_marker)) {
    return LookupResult(false, start_idx);
  }

  if (uint64_t h1 = hash & _h1_mask; first_metadata == h1) {
    const Entry& entry = table[start_idx];
    if (TOKEN_HASH_MATCH(token, hash, entry)) {
      return LookupResult(true, start_idx);
    }
  }

  size_t insert_point = _no_insert_point;
  if (first_metadata == uint8_t(Marker::_tombstone_marker)) {
    insert_point = start_idx;
  }
  return PDSwissTableImpl<Entry, Allocator>::template lookup<Token, TOKEN_HASH_MATCH>(metadata, table, table_size_minus_one,
                                                                                      hash, token, start_idx + 1, insert_point);
}

#endif // SHARE_UTILITIES_SWISSTABLE_HPP
