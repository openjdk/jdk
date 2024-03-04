/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
  static void testTruncate(BitMap::idx_t start_bit, BitMap::idx_t end_bit, ResizableBitMapClass& result) {
    ResourceMark rm;

    ResizableBitMapClass map(BITMAP_SIZE);
    fillBitMap(map, BITMAP_SIZE);
    map.truncate(start_bit, end_bit);

    EXPECT_TRUE(map.is_same(result));
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

    map.truncate(1, 3, true);

    EXPECT_TRUE(map.is_same(result));
  }

  template <class ResizableBitMapClass>
  static void testTruncateSame() {
    // Resulting map should be the same as the original
    ResourceMark rm;
    ResizableBitMapClass map(BITMAP_SIZE);
    fillBitMap(map, BITMAP_SIZE);
    testTruncate<ResizableBitMapClass>(0, BITMAP_SIZE, map);
  }

  template <class ResizableBitMapClass>
  static void testTruncateStart() {
    // Resulting map should start at the beginning of the original
    ResourceMark rm;
    ResizableBitMapClass map(64);
    fillBitMap(map, 64);
    testTruncate<ResizableBitMapClass>(0, 64, map);
  }

  template <class ResizableBitMapClass>
  static void testTruncateEnd() {
    // Resulting map should end at the end of the original
    ResourceMark rm;
    ResizableBitMapClass map(64);
    map.set_bit(0);
    map.set_bit(31);
    map.set_bit(63);
    testTruncate<ResizableBitMapClass>(64, 128, map);
  }

  template <class ResizableBitMapClass>
  static void testTruncateMiddle() {
    // Resulting map should end at the end of the original
    ResourceMark rm;
    ResizableBitMapClass map(64);
    map.set_bit(31);
    map.set_bit(32);
    map.set_bit(63);
    testTruncate<ResizableBitMapClass>(32, 96, map);
  }

  template <class ResizableBitMapClass>
  static void testTruncateStartUnaligned() {
    // Resulting map should start at the beginning of the original
    ResourceMark rm;
    ResizableBitMapClass map(96);
    fillBitMap(map, 96);
    testTruncate<ResizableBitMapClass>(0, 96, map);
  }

  template <class ResizableBitMapClass>
  static void testTruncateEndUnaligned() {
    // Resulting map should end at the end of the original
    ResourceMark rm;
    ResizableBitMapClass map(97);
    map.set_bit(0);
    map.set_bit(32);
    map.set_bit(33);
    map.set_bit(64);
    map.set_bit(96);
    testTruncate<ResizableBitMapClass>(31, 128, map);
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

TEST_VM(BitMapTruncate, truncate_same) {
  BitMapTruncateTest::testTruncateSame<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateSame<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateSame<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMapTruncate, truncate_start) {
  BitMapTruncateTest::testTruncateStart<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateStart<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateStart<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMapTruncate, truncate_end) {
  BitMapTruncateTest::testTruncateEnd<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateEnd<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateEnd<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMapTruncate, truncate_middle) {
  BitMapTruncateTest::testTruncateMiddle<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateMiddle<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateMiddle<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMapTruncate, truncate_start_unaligned) {
  BitMapTruncateTest::testTruncateStartUnaligned<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateStartUnaligned<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateStartUnaligned<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMapTruncate, truncate_end_unaligned) {
  BitMapTruncateTest::testTruncateEndUnaligned<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateEndUnaligned<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateEndUnaligned<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}

TEST_VM(BitMapTruncate, truncate_one_word) {
  BitMapTruncateTest::testTruncateOneWord<ResourceBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ResourceBitMap";
  BitMapTruncateTest::testTruncateOneWord<TestCHeapBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type CHeapBitMap";
  BitMapTruncateTest::testTruncateOneWord<TestArenaBitMap>();
  EXPECT_FALSE(HasFailure()) << "Failed on type ArenaBitMap";
}
