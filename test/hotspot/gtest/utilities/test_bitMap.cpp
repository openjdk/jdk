/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logStream.hpp"
#include "memory/arena.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/bitMap.inline.hpp"
#include "unittest.hpp"

typedef BitMap::idx_t idx_t;
typedef BitMap::bm_word_t bm_word_t;

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

  template <class ReinitializableBitMapClass>
  static void testReinitialize(BitMap::idx_t init_size) {
    ResourceMark rm;

    ReinitializableBitMapClass  map(init_size);
    map.reinitialize(BITMAP_SIZE);
    fillBitMap(map);

    ReinitializableBitMapClass map2(BITMAP_SIZE);
    fillBitMap(map2);
    EXPECT_TRUE(map.is_same(map2)) << "With init_size " << init_size;
  }

#ifdef ASSERT
  template <class PrintableBitMapClass>
  static void testPrintOn(BitMap::idx_t size) {
    ResourceMark rm;

    PrintableBitMapClass map(size);
    if (size > 0) {
      map.set_bit(size / 2);
    }

    LogStreamHandle(Info, test) stream;
    map.print_on(&stream);
  }

#endif
};

// TestArenaBitMap is the shorthand combination of Arena and ArenaBitMap.
// Multiple inheritance guarantees to construct Arena first.
class TestArenaBitMap : private Arena, public ArenaBitMap {
 public:
  TestArenaBitMap() : TestArenaBitMap(0) {}
  TestArenaBitMap(idx_t size_in_bits, bool clear = true) : Arena(mtTest),
                                                           ArenaBitMap(static_cast<Arena*>(this), size_in_bits, clear) {}
};

class TestCHeapBitMap : public CHeapBitMap {
public:
  TestCHeapBitMap(size_t size = 0) : CHeapBitMap(size, mtTest) {}

};

TEST_VM(BitMap, resize_grow) {
  BitMapTest::testResizeGrow<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTest::testResizeGrow<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTest::testResizeGrow<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, resize_shrink) {
  BitMapTest::testResizeShrink<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTest::testResizeShrink<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTest::testResizeShrink<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, resize_same) {
  BitMapTest::testResizeSame<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTest::testResizeSame<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTest::testResizeSame<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
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
  BitMapTest::testInitialize<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTest::testInitialize<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, reinitialize) {
  constexpr BitMap::idx_t sizes[] = {0, BitMapTest::BITMAP_SIZE >> 3, BitMapTest::BITMAP_SIZE};

  for (auto size : sizes) {
    BitMapTest::testReinitialize<ResourceBitMap>(size);
    BitMapTest::testReinitialize<TestArenaBitMap>(size);
  }
}

class BitMapTestIterate {
public:
  static void test_with_lambda(idx_t size, idx_t* seq, size_t seq_length) {
    ResourceMark rm;
    ResourceBitMap bm(size, true /* clear */);

    // Fill in the bits
    for (size_t i = 0; i < seq_length; i++) {
      bm.set_bit(seq[i]);
    }

    // Iterate over the bits
    idx_t index = 0;

    auto lambda = [&](BitMap::idx_t bit) -> bool {
      if (index == seq_length) {
        // Went too far
        return false;
      }
      if (seq[index++] != bit) {
        return false;
      }

      return true;
    };

    bool val = bm.iterate(lambda, 0, bm.size());

    ASSERT_TRUE(val) << "Failed";

    ASSERT_TRUE(index == seq_length) << "Not all visited. index: " << index << " size: " << size << " seq_length: " << seq_length;
  }

  static void test_with_closure(idx_t size, idx_t* seq, size_t seq_length) {
    ResourceMark rm;
    ResourceBitMap bm(size, true /* clear */);

    // Fill in the bits
    for (size_t i = 0; i < seq_length; i++) {
      bm.set_bit(seq[i]);
    }

    // Iterate over the bits
    class Closure : public BitMapClosure {
    private:
      idx_t* const _sequence;
      size_t const _length;
      idx_t        _index;

    public:
      Closure(size_t* sequence, size_t length) : _sequence(sequence), _length(length), _index(0) {}

      virtual bool do_bit(BitMap::idx_t bit) {
        if (_index == _length) {
          // Went too far
          return false;
        }
        if (_sequence[_index++] != bit) {
          return false;
        }

        return true;
      }

      bool all_visited() {
        return _index == _length;
      }
    } cl(seq, seq_length);

    bool val = bm.iterate(&cl, 0, bm.size());

    ASSERT_TRUE(val) << "Failed";

    ASSERT_TRUE(cl.all_visited()) << "Not all visited";
  }

  static void test(idx_t size, idx_t* seq, size_t seq_length) {
    test_with_lambda(size, seq, seq_length);
    test_with_closure(size, seq, seq_length);
  }
};

TEST_VM(BitMap, iterate) {
  const idx_t size = 256;

  // With no bits set
  {
    BitMapTestIterate::test(size, NULL, 0);
  }

  // With end-points set
  {
    idx_t seq[] = {0, 2, 6, 31, 61, 131, size - 1};
    BitMapTestIterate::test(size, seq, ARRAY_SIZE(seq));
  }

  // Without end-points set
  {
    idx_t seq[] = {1, 2, 6, 31, 61, 131, size - 2};
    BitMapTestIterate::test(size, seq, ARRAY_SIZE(seq));
  }

  // With all bits set
  {
    idx_t* seq = (idx_t*)os::malloc(size * sizeof(size_t), mtTest);
    for (size_t i = 0; i < size; i++) {
      seq[i] = idx_t(i);
    }
    BitMapTestIterate::test(size, seq, size);
    os::free(seq);
  }
}

class BitMapTestIterateReverse {
public:
  static void test_with_lambda(idx_t size, idx_t* seq, size_t seq_length) {
    ResourceMark rm;
    ResourceBitMap bm(size, true /* clear */);

    // Fill in the bits
    for (size_t i = 0; i < seq_length; i++) {
      bm.set_bit(seq[i]);
    }

    // Iterate over the bits
    idx_t index = seq_length;

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

    bool val = bm.iterate_reverse(lambda, 0, bm.size());

    ASSERT_TRUE(val) << "Failed";

    ASSERT_TRUE(index == 0) << "Not all visited. index: " << index << " size: " << size << " seq_length: " << seq_length;
  }

  static void test_with_closure(idx_t size, idx_t* seq, size_t seq_length) {
    ResourceMark rm;
    ResourceBitMap bm(size, true /* clear */);

    // Fill in the bits
    for (size_t i = 0; i < seq_length; i++) {
      bm.set_bit(seq[i]);
    }

    // Iterate over the bits
    class Closure : public BitMapClosure {
    private:
      idx_t* const _sequence;
      size_t const _length;
      idx_t        _index;

    public:
      Closure(size_t* sequence, size_t length) : _sequence(sequence), _length(length), _index(length) {}

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
    } cl(seq, seq_length);

    bool val = bm.iterate_reverse(&cl, 0, bm.size());

    ASSERT_TRUE(val) << "Failed";

    ASSERT_TRUE(cl.all_visited()) << "Not all visited";
  }

  static void test(idx_t size, idx_t* seq, size_t seq_length) {
    test_with_lambda(size, seq, seq_length);
    test_with_closure(size, seq, seq_length);
  }
};

TEST_VM(BitMap, iterate_reverse) {
  const idx_t size = 256;

  // With no bits set
  {
    BitMapTestIterateReverse::test(size, NULL, 0);
  }

  // With end-points set
  {
    idx_t seq[] = {0, 2, 6, 31, 61, 131, size - 1};
    BitMapTestIterateReverse::test(size, seq, ARRAY_SIZE(seq));
  }

  // Without end-points set
  {
    idx_t seq[] = {1, 2, 6, 31, 61, 131, size - 2};
    BitMapTestIterateReverse::test(size, seq, ARRAY_SIZE(seq));
  }

  // With all bits set
  {
    idx_t* seq = (idx_t*)os::malloc(size * sizeof(size_t), mtTest);
    for (size_t i = 0; i < size; i++) {
      seq[i] = idx_t(i);
    }
    BitMapTestIterateReverse::test(size, seq, size);
    os::free(seq);
  }
}

#ifdef ASSERT

TEST_VM(BitMap, print_on) {
  constexpr BitMap::idx_t sizes[] = {0, BitMapTest::BITMAP_SIZE >> 3, BitMapTest::BITMAP_SIZE};

  for (auto size : sizes) {
    BitMapTest::testPrintOn<ResourceBitMap>(size);
    BitMapTest::testPrintOn<TestArenaBitMap>(size);
  }
}

#endif
