/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/bitMap.inline.hpp"
#include "unittest.hpp"

class BitMapTest {

  template <class ResizableBitMapClass>
  static void fillBitMap(ResizableBitMapClass& map) {
    map.set_bit(1);
    map.set_bit(3);
    map.set_bit(17);
    map.set_bit(512);
  }

  template <class ResizableBitMapClass>
  static void testResize(BitMap::idx_t start_size) {
    ResourceMark rm;

    ResizableBitMapClass map(start_size);
    map.resize(BITMAP_SIZE);
    fillBitMap(map);

    ResizableBitMapClass map2(BITMAP_SIZE);
    fillBitMap(map2);
    EXPECT_TRUE(map.is_same(map2)) << "With start_size " << start_size;
  }

 public:
  const static BitMap::idx_t BITMAP_SIZE = 1024;


  template <class ResizableBitMapClass>
  static void testResizeGrow() {
    testResize<ResizableBitMapClass>(0);
    testResize<ResizableBitMapClass>(BITMAP_SIZE >> 3);
  }

  template <class ResizableBitMapClass>
  static void testResizeSame() {
    testResize<ResizableBitMapClass>(BITMAP_SIZE);
  }

  template <class ResizableBitMapClass>
  static void testResizeShrink() {
    testResize<ResizableBitMapClass>(BITMAP_SIZE * 2);
  }

  template <class InitializableBitMapClass>
  static void testInitialize() {
    ResourceMark rm;

    InitializableBitMapClass map;
    map.initialize(BITMAP_SIZE);
    fillBitMap(map);

    InitializableBitMapClass map2(BITMAP_SIZE);
    fillBitMap(map2);
    EXPECT_TRUE(map.is_same(map2));
  }


  static void testReinitialize(BitMap::idx_t init_size) {
    ResourceMark rm;

    ResourceBitMap map(init_size);
    map.reinitialize(BITMAP_SIZE);
    fillBitMap(map);

    ResourceBitMap map2(BITMAP_SIZE);
    fillBitMap(map2);
    EXPECT_TRUE(map.is_same(map2)) << "With init_size " << init_size;
  }

};

TEST_VM(BitMap, resize_grow) {
  BitMapTest::testResizeGrow<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTest::testResizeGrow<CHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
}

TEST_VM(BitMap, resize_shrink) {
  BitMapTest::testResizeShrink<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTest::testResizeShrink<CHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
}

TEST_VM(BitMap, resize_same) {
  BitMapTest::testResizeSame<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTest::testResizeSame<CHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
}

// Verify that when growing with clear, all added bits get cleared,
// even those corresponding to a partial word after the old size.
TEST_VM(BitMap, resize_grow_clear) {
  ResourceMark rm;
  const size_t word_size = sizeof(BitMap::bm_word_t) * BitsPerByte;
  const size_t size = 4 * word_size;
  ResourceBitMap bm(size, true /* clear */);
  bm.set_bit(size - 1);
  EXPECT_EQ(bm.count_one_bits(), size_t(1));
  // Discard the only set bit.  But it might still be "set" in the
  // partial word beyond the new size.
  bm.resize(size - word_size/2);
  EXPECT_EQ(bm.count_one_bits(), size_t(0));
  // Grow to include the previously set bit.  Verify that it ended up cleared.
  bm.resize(2 * size);
  EXPECT_EQ(bm.count_one_bits(), size_t(0));
}

TEST_VM(BitMap, initialize) {
  BitMapTest::testInitialize<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTest::testInitialize<CHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
}

TEST_VM(BitMap, reinitialize) {
  BitMapTest::testReinitialize(0);
  BitMapTest::testReinitialize(BitMapTest::BITMAP_SIZE >> 3);
  BitMapTest::testReinitialize(BitMapTest::BITMAP_SIZE);
}

struct BitMapTestSetter {
  ResourceBitMap* _bm;
  size_t          _bit;
  bool            _already_set;

  BitMapTestSetter(ResourceBitMap* bm, size_t bit) : _bm(bm), _bit(bit), _already_set(_bm->at(_bit)) {
    if (!_already_set) {
      _bm->set_bit(_bit);
    }
  }
  ~BitMapTestSetter() {
    if (!_already_set) {
      _bm->clear_bit(_bit);
    }
  }
};

TEST_VM(BitMap, get_prev_one_offset) {
  ResourceMark rm;
  const size_t word_size = sizeof(BitMap::bm_word_t) * BitsPerByte;
  const size_t size = 4 * word_size;
  ResourceBitMap bm(size, true /* clear */);

#define ASSERT_MSG "l_index: " << l_index << " r_index: " << r_index << " l_bit: " << l_bit << " r_bit: " << r_bit

  // Using "size" takes too long time. Change this if you want more extensive testing
  size_t test_size = word_size * 2;

  for (size_t l_index = 0; l_index < test_size - 1; l_index++) {
    for (size_t r_index = l_index; r_index < test_size - 1; r_index++) {
      for (size_t l_bit = 0; l_bit < test_size - 1; l_bit++) {
        BitMapTestSetter l_bit_setter(&bm, l_bit);
        for (size_t r_bit = l_bit; r_bit < test_size - 1; r_bit++) {
          BitMapTestSetter r_bit_setter(&bm, r_bit);
          if (l_index <= r_bit && r_bit <= r_index) {
            // r_bit is within range; expect to find it
            ASSERT_EQ(bm.get_prev_one_offset(l_index, r_index), r_bit) << ASSERT_MSG;
            ASSERT_EQ(bm.get_prev_one_offset(r_index), r_bit) << ASSERT_MSG;
          } else if (l_index <= l_bit && l_bit <= r_index) {
            // r_bit is out-of-range while l_bit is within range; expect to find it
            ASSERT_EQ(bm.get_prev_one_offset(l_index, r_index), l_bit) << ASSERT_MSG;
            ASSERT_EQ(bm.get_prev_one_offset(r_index), l_bit) << ASSERT_MSG;
          } else {
            // No bit in range; expect to find nothing
            ASSERT_EQ(bm.get_prev_one_offset(l_index, r_index), size_t(-1)) << ASSERT_MSG;
          }
        }
      }
    }
  }

  bm.at_put_range(0, test_size, true);
  for (size_t l_index = 0; l_index < test_size - 1; l_index++) {
    for (size_t r_index = l_index; r_index < word_size - 1; r_index++) {
      ASSERT_EQ(bm.get_prev_one_offset(l_index, r_index), r_index);
    }
  }
}

class BitMapTestClosure : public BitMapClosure {
private:
  size_t* _sequence;
  size_t  _length;
  size_t  _index;

public:
  BitMapTestClosure(size_t* sequence, size_t length) : _sequence(sequence), _length(length), _index(length) {}

  virtual bool do_bit(BitMap::idx_t bit) {
    if (_index == 0) {
      // Went too far
      return false;
    }
    if (_sequence[--_index] != bit) {
      return false;
    }

    return true;
  }

  bool all_visited() {
    return _index == 0;
  }
};

class BitMapTestIterateReverse {
public:
  static void test_with_lambda(size_t size, size_t* seq, size_t seq_length) {
    ResourceMark rm;
    ResourceBitMap bm(size, true /* clear */);

    // Fill in the bits
    for (size_t i = 0; i < seq_length; i++) {
      bm.set_bit(seq[i]);
    }

    // Iterate over the bits
    size_t index = seq_length;

    auto lambda = [&](BitMap::idx_t bit) -> bool {
      if (index == 0) {
        // Went too far
        return false;
      }
      if (seq[--index] != bit) {
        return false;
      }

      return true;
    };

    bool val = bm.iterate_reverse_f(lambda, 0, bm.size() - 1);

    ASSERT_TRUE(val) << "Failed";

    ASSERT_TRUE(index == 0) << "Not all visited";
  }

  static void test(size_t size, size_t* seq, size_t seq_length) {
    test_with_lambda(size, seq, seq_length);

    ResourceMark rm;
    ResourceBitMap bm(size, true /* clear */);

    // Fill in the bits
    for (size_t i = 0; i < seq_length; i++) {
      bm.set_bit(seq[i]);
    }

    // Iterate over the bits
    BitMapTestClosure cl(seq, seq_length);
    bool val = bm.iterate_reverse(&cl, 0, bm.size()-1);
    ASSERT_TRUE(val) << "Failed";

    ASSERT_TRUE(cl.all_visited()) << "Not all visited";
  }
};

TEST_VM(BitMap, iterate_reverse) {
  const size_t word_size = sizeof(BitMap::bm_word_t) * BitsPerByte;
  const size_t size = 4 * word_size;

  // With no bits set
  {
    size_t seq[] = {};
    BitMapTestIterateReverse::test(size, seq, 0);
  }

  // With end-points set
  {
    size_t seq[] = {0, 2, 6, 31, 61, 131, size - 1};
    BitMapTestIterateReverse::test(size, seq, ARRAY_SIZE(seq));
  }

  // Without end-points set
  {
    size_t seq[] = {1, 2, 6, 31, 61, 131, size - 2};
    BitMapTestIterateReverse::test(size, seq, ARRAY_SIZE(seq));
  }

  // With all bits set
  {
    size_t* seq = (size_t*)os::malloc(size * sizeof(size_t), mtTest);
    for (size_t i = 0; i < size; i++) {
      seq[i] = i;
    }
    BitMapTestIterateReverse::test(size, seq, size);
    os::free(seq);
  }
}
