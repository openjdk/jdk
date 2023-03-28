/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"

using idx_t = BitMap::idx_t;
using bm_word_t = BitMap::bm_word_t;

static const idx_t BITMAP_SIZE = 1024;
static const idx_t BITMAP_WORD_SIZE = align_up(BITMAP_SIZE, BitsPerWord) / BitsPerWord;


static void test_iterate_step(const BitMap& map,
                              idx_t index,
                              const idx_t* positions,
                              size_t positions_index,
                              size_t positions_size) {
  ASSERT_LT(positions_index, positions_size);
  ASSERT_EQ(index, positions[positions_index]);
  ASSERT_TRUE(map.at(index));
}

// Test lambda returning void.
static void test_iterate_lambda(const BitMap& map,
                                const idx_t* positions,
                                size_t positions_size) {
  SCOPED_TRACE("iterate with lambda");
  size_t positions_index = 0;
  auto f = [&](idx_t i) {
    test_iterate_step(map, i, positions, positions_index++, positions_size);
  };
  ASSERT_TRUE(map.iterate(f));
  ASSERT_EQ(positions_index, positions_size);
}

// Test closure returning bool.  Also tests lambda returning bool.
static void test_iterate_closure(const BitMap& map,
                                 const idx_t* positions,
                                 size_t positions_size) {
  SCOPED_TRACE("iterate with BitMapClosure");
  struct Closure : public BitMapClosure {
    const BitMap& _map;
    const idx_t* _positions;
    size_t _positions_index;
    size_t _positions_size;

    Closure(const BitMap& map, const idx_t* positions, size_t positions_size)
      : _map(map),
        _positions(positions),
        _positions_index(0),
        _positions_size(positions_size)
    {}

    bool do_bit(idx_t i) override {
      test_iterate_step(_map, i, _positions, _positions_index++, _positions_size);
      return true;
    }
  } closure{map, positions, positions_size};
  ASSERT_TRUE(map.iterate(&closure));
  ASSERT_EQ(closure._positions_index, positions_size);
}

// Test closure returning void.  Also tests lambda returning bool.
static void test_iterate_non_closure(const BitMap& map,
                                     const idx_t* positions,
                                     size_t positions_size) {
  SCOPED_TRACE("iterate with non-BitMapClosure");
  struct Closure {
    const BitMap& _map;
    const idx_t* _positions;
    size_t _positions_index;
    size_t _positions_size;

    Closure(const BitMap& map, const idx_t* positions, size_t positions_size)
      : _map(map),
        _positions(positions),
        _positions_index(0),
        _positions_size(positions_size)
    {}

    void do_bit(idx_t i) {
      test_iterate_step(_map, i, _positions, _positions_index++, _positions_size);
    }
  } closure{map, positions, positions_size};
  ASSERT_TRUE(map.iterate(&closure));
  ASSERT_EQ(closure._positions_index, positions_size);
}

static void fill_iterate_map(BitMap& map,
                             const idx_t* positions,
                             size_t positions_size) {
  map.clear_range(0, map.size());
  for (size_t i = 0; i < positions_size; ++i) {
    map.set_bit(positions[i]);
  }
}

static void test_iterate(BitMap& map,
                         const idx_t* positions,
                         size_t positions_size) {
  fill_iterate_map(map, positions, positions_size);
  test_iterate_lambda(map, positions, positions_size);
  test_iterate_closure(map, positions, positions_size);
  test_iterate_non_closure(map, positions, positions_size);
}

TEST(BitMap, iterate_empty) {
  bm_word_t test_data[BITMAP_WORD_SIZE];
  BitMapView test_map{test_data, BITMAP_SIZE};
  idx_t positions[1] = {};
  test_iterate(test_map, positions, 0);
}

TEST(BitMap, iterate_with_endpoints) {
  bm_word_t test_data[BITMAP_WORD_SIZE];
  BitMapView test_map{test_data, BITMAP_SIZE};
  idx_t positions[] = { 0, 2, 6, 31, 61, 131, 247, 578, BITMAP_SIZE - 1 };
  test_iterate(test_map, positions, ARRAY_SIZE(positions));
}

TEST(BitMap, iterate_without_endpoints) {
  bm_word_t test_data[BITMAP_WORD_SIZE];
  BitMapView test_map{test_data, BITMAP_SIZE};
  idx_t positions[] = { 1, 2, 6, 31, 61, 131, 247, 578, BITMAP_SIZE - 2 };
  test_iterate(test_map, positions, ARRAY_SIZE(positions));
}

TEST(BitMap, iterate_full) {
  bm_word_t test_data[BITMAP_WORD_SIZE];
  BitMapView test_map{test_data, BITMAP_SIZE};
  static idx_t positions[BITMAP_SIZE]; // static to avoid large stack allocation.
  for (idx_t i = 0; i < BITMAP_SIZE; ++i) {
    positions[i] = i;
  }
  test_iterate(test_map, positions, ARRAY_SIZE(positions));
}

TEST(BitMap, iterate_early_termination) {
  bm_word_t test_data[BITMAP_WORD_SIZE];
  BitMapView test_map{test_data, BITMAP_SIZE};
  idx_t positions[] = { 1, 2, 6, 31, 61, 131, 247, 578, BITMAP_SIZE - 2 };
  size_t positions_size = ARRAY_SIZE(positions);
  size_t positions_index = 0;
  fill_iterate_map(test_map, positions, positions_size);
  idx_t stop_at = 131;
  auto f = [&](idx_t i) {
    test_iterate_step(test_map, i, positions, positions_index, positions_size);
    if (positions[positions_index] == stop_at) {
      return false;
    } else {
      positions_index += 1;
      return true;
    }
  };
  ASSERT_FALSE(test_map.iterate(f));
  ASSERT_LT(positions_index, positions_size);
  ASSERT_EQ(positions[positions_index], stop_at);

  struct Closure : public BitMapClosure {
    const BitMap& _map;
    const idx_t* _positions;
    size_t _positions_index;
    size_t _positions_size;
    idx_t _stop_at;

    Closure(const BitMap& map, const idx_t* positions, size_t positions_size, idx_t stop_at)
      : _map(map),
        _positions(positions),
        _positions_index(0),
        _positions_size(positions_size),
        _stop_at(stop_at)
    {}

    bool do_bit(idx_t i) override {
      test_iterate_step(_map, i, _positions, _positions_index, _positions_size);
      if (_positions[_positions_index] == _stop_at) {
        return false;
      } else {
        _positions_index += 1;
        return true;
      }
    }
  } closure{test_map, positions, positions_size, stop_at};
  ASSERT_FALSE(test_map.iterate(&closure));
  ASSERT_LT(closure._positions_index, positions_size);
  ASSERT_EQ(positions[closure._positions_index], stop_at);
}
