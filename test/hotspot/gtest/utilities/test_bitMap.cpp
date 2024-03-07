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

class BitMapTruncateTest {

 public:
  const static BitMap::idx_t BITMAP_SIZE = 128;

  template <class ResizableBitMapClass>
  static void fillBitMap(ResizableBitMapClass& map,  BitMap::idx_t size) {
    BitMap::idx_t set_bits[] = {0, 31, 63, 64, 95, 127};
    for (BitMap::idx_t bit : set_bits) {
        if (bit < size) {
            map.set_bit(bit);
        }
    }
  }

  template <class ResizableBitMapClass>
  static void testTruncateOneWord() {
    ResourceMark rm;

    ResizableBitMapClass map(64);
    map.set_bit(0);
    map.set_bit(1);
    map.set_bit(2);
    map.set_bit(3);

    ResizableBitMapClass result(2);
    result.set_bit(0);
    result.set_bit(1);

    map.truncate(1, 3);

    EXPECT_TRUE(map.is_same(result));
  }

  template <class ResizableBitMapClass>
  static void testTruncateSame() {
    // Resulting map should be the same as the original
    ResourceMark rm;
    ResizableBitMapClass expected(BITMAP_SIZE);
    fillBitMap(expected, BITMAP_SIZE);

    ResizableBitMapClass map(BITMAP_SIZE);
    fillBitMap(map, BITMAP_SIZE);
    map.truncate(0, BITMAP_SIZE);

    EXPECT_TRUE(map.is_same(expected));
  }

  template <class ResizableBitMapClass>
  static void testTruncateStart() {
    // Resulting map should start at the beginning of the original
    ResourceMark rm;
    ResizableBitMapClass expected(64);
    fillBitMap(expected, 64);

    ResizableBitMapClass map(BITMAP_SIZE);
    fillBitMap(map, BITMAP_SIZE);
    map.truncate(0, 64);

    EXPECT_TRUE(map.is_same(expected));
  }

  template <class ResizableBitMapClass>
  static void testTruncateEnd() {
    // Resulting map should end at the end of the original
    ResourceMark rm;
    ResizableBitMapClass expected(64);
    expected.set_bit(0);
    expected.set_bit(31);
    expected.set_bit(63);

    ResizableBitMapClass map(BITMAP_SIZE);
    fillBitMap(map, BITMAP_SIZE);
    map.truncate(64, 128);

    EXPECT_TRUE(map.is_same(expected));
  }

  template <class ResizableBitMapClass>
  static void testTruncateMiddle() {
    // Resulting map should end at the end of the original
    ResourceMark rm;
    ResizableBitMapClass expected(64);
    expected.set_bit(31);
    expected.set_bit(32);
    expected.set_bit(63);

    ResizableBitMapClass map(BITMAP_SIZE);
    fillBitMap(map, BITMAP_SIZE);
    map.truncate(32, 96);

    EXPECT_TRUE(map.is_same(expected));
  }

  template <class ResizableBitMapClass>
  static void testTruncateStartUnaligned() {
    // Resulting map should start at the beginning of the original
    ResourceMark rm;
    ResizableBitMapClass expected(96);
    fillBitMap(expected, 96);

    ResizableBitMapClass map(BITMAP_SIZE);
    fillBitMap(map, BITMAP_SIZE);
    map.truncate(0, 96);

    EXPECT_TRUE(map.is_same(expected));
  }

  template <class ResizableBitMapClass>
  static void testTruncateEndUnaligned() {
    // Resulting map should end at the end of the original
    ResourceMark rm;
    ResizableBitMapClass expected(97);
    expected.set_bit(0);
    expected.set_bit(32);
    expected.set_bit(33);
    expected.set_bit(64);
    expected.set_bit(96);

    ResizableBitMapClass map(BITMAP_SIZE);
    fillBitMap(map, BITMAP_SIZE);
    map.truncate(31, 128);

    EXPECT_TRUE(map.is_same(expected));
  }

  template <class ResizableBitMapClass>
  static void testRandom() {
    for (int i = 0; i < 100; i++) {
      ResourceMark rm;

      const size_t max_size = 1024;
      const size_t size = os::random() % max_size + 1;
      const size_t truncate_size = os::random() % size + 1;
      const size_t truncate_start = size == truncate_size ? 0 : os::random() % (size - truncate_size);

      ResizableBitMapClass map(size);
      ResizableBitMapClass result(truncate_size);

      for (BitMap::idx_t idx = 0; idx < truncate_start; idx++) {
        if (os::random() % 2 == 0) {
          map.set_bit(idx);
        }
      }

      for (BitMap::idx_t idx = 0; idx < truncate_size; idx++) {
        if (os::random() % 2 == 0) {
          map.set_bit(truncate_start + idx);
          result.set_bit(idx);
        }
      }

      map.truncate(truncate_start, truncate_start + truncate_size);

      EXPECT_TRUE(map.is_same(result));
    }
  }
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

#ifdef ASSERT

TEST_VM(BitMap, print_on) {
  constexpr BitMap::idx_t sizes[] = {0, BitMapTest::BITMAP_SIZE >> 3, BitMapTest::BITMAP_SIZE};

  for (auto size : sizes) {
    BitMapTest::testPrintOn<ResourceBitMap>(size);
    BitMapTest::testPrintOn<TestArenaBitMap>(size);
  }
}

#endif

TEST_VM(BitMap, truncate_same) {
  BitMapTruncateTest::testTruncateSame<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateSame<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateSame<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, truncate_start) {
  BitMapTruncateTest::testTruncateStart<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateStart<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateStart<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, truncate_end) {
  BitMapTruncateTest::testTruncateEnd<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateEnd<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateEnd<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, truncate_middle) {
  BitMapTruncateTest::testTruncateMiddle<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateMiddle<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateMiddle<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, truncate_start_unaligned) {
  BitMapTruncateTest::testTruncateStartUnaligned<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateStartUnaligned<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateStartUnaligned<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, truncate_end_unaligned) {
  BitMapTruncateTest::testTruncateEndUnaligned<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateEndUnaligned<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateEndUnaligned<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, truncate_one_word) {
  BitMapTruncateTest::testTruncateOneWord<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateOneWord<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateOneWord<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMap, truncate_random) {
  BitMapTruncateTest::testRandom<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testRandom<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testRandom<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}
