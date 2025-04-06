/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zGlobals.hpp"
#include "gc/z/zMemory.inline.hpp"
#include "unittest.hpp"

class ZAddressOffsetMaxSetter {
private:
  const size_t _old_max;
  const size_t _old_mask;

public:
  ZAddressOffsetMaxSetter()
    : _old_max(ZAddressOffsetMax),
      _old_mask(ZAddressOffsetMask) {
    ZAddressOffsetMax = size_t(16) * G * 1024;
    ZAddressOffsetMask = ZAddressOffsetMax - 1;
  }
  ~ZAddressOffsetMaxSetter() {
    ZAddressOffsetMax = _old_max;
    ZAddressOffsetMask = _old_mask;
  }
};

TEST(ZMemory, accessors) {
  ZAddressOffsetMaxSetter setter;

  {
    ZMemory mem(zoffset(0), ZGranuleSize);

    EXPECT_EQ(mem.start(), zoffset(0));
    EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize));
    EXPECT_EQ(mem.size(), ZGranuleSize);
  }


  {
    ZMemory mem(zoffset(ZGranuleSize), ZGranuleSize);

    EXPECT_EQ(mem.start(), zoffset(ZGranuleSize));
    EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize + ZGranuleSize));
    EXPECT_EQ(mem.size(), ZGranuleSize);
  }

  {
    // Max area - check end boundary
    ZMemory mem(zoffset(0), ZAddressOffsetMax);

    EXPECT_EQ(mem.start(), zoffset(0));
    EXPECT_EQ(mem.end(), zoffset_end(ZAddressOffsetMax));
    EXPECT_EQ(mem.size(), ZAddressOffsetMax);
  }
}

TEST(ZMemory, resize) {
  ZAddressOffsetMaxSetter setter;

  ZMemory mem(zoffset(ZGranuleSize * 2), ZGranuleSize * 2) ;

  mem.shrink_from_front(ZGranuleSize);
  EXPECT_EQ(mem.start(),   zoffset(ZGranuleSize * 3));
  EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize * 4));
  EXPECT_EQ(mem.size(),            ZGranuleSize * 1);
  mem.grow_from_front(ZGranuleSize);

  mem.shrink_from_back(ZGranuleSize);
  EXPECT_EQ(mem.start(),   zoffset(ZGranuleSize * 2));
  EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize * 3));
  EXPECT_EQ(mem.size(),            ZGranuleSize * 1);
  mem.grow_from_back(ZGranuleSize);

  mem.grow_from_front(ZGranuleSize);
  EXPECT_EQ(mem.start(),   zoffset(ZGranuleSize * 1));
  EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize * 4));
  EXPECT_EQ(mem.size(),            ZGranuleSize * 3);
  mem.shrink_from_front(ZGranuleSize);

  mem.grow_from_back(ZGranuleSize);
  EXPECT_EQ(mem.start(),   zoffset(ZGranuleSize * 2));
  EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize * 5));
  EXPECT_EQ(mem.size(),            ZGranuleSize * 3);
  mem.shrink_from_back(ZGranuleSize);
}
