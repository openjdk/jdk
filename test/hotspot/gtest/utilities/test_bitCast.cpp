/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Google and/or its affiliates. All rights reserved.
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
#include "memory/allStatic.hpp"
#include "utilities/bitCast.hpp"
#include "unittest.hpp"

TEST(BitCast, round_trip_int) {
  int  sfive = 5;
  int  mfive = -5;
  uint ufive = 5u;

  using SI = signed int;
  using UI = unsigned int;

  EXPECT_EQ(sfive, bit_cast<int>(bit_cast<SI>(sfive)));
  EXPECT_EQ(sfive, bit_cast<int>(bit_cast<UI>(sfive)));

  EXPECT_EQ(mfive, bit_cast<int>(bit_cast<SI>(mfive)));
  EXPECT_EQ(mfive, bit_cast<int>(bit_cast<UI>(mfive)));

  EXPECT_EQ(ufive, bit_cast<uint>(bit_cast<SI>(ufive)));
  EXPECT_EQ(ufive, bit_cast<uint>(bit_cast<UI>(ufive)));
}

TEST(BitCast, round_trip_int_constexpr) {
  constexpr int  sfive = 5;
  constexpr int  mfive = -5;
  constexpr uint ufive = 5u;

  using SI = signed int;
  using UI = unsigned int;

  {
    constexpr SI i = bit_cast<SI>(sfive);
    constexpr int r = bit_cast<int>(i);
    EXPECT_EQ(sfive, r);
  }

  {
    constexpr UI i = bit_cast<UI>(sfive);
    constexpr int r = bit_cast<int>(i);
    EXPECT_EQ(sfive, r);
  }

  {
    constexpr SI i = bit_cast<SI>(mfive);
    constexpr int r = bit_cast<int>(i);
    EXPECT_EQ(mfive, r);
  }

  {
    constexpr UI i = bit_cast<UI>(mfive);
    constexpr int r = bit_cast<int>(i);
    EXPECT_EQ(mfive, r);
  }

  {
    constexpr SI i = bit_cast<SI>(ufive);
    constexpr uint r = bit_cast<uint>(i);
    EXPECT_EQ(ufive, r);
  }

  {
    constexpr UI i = bit_cast<UI>(ufive);
    constexpr uint r = bit_cast<uint>(i);
    EXPECT_EQ(ufive, r);
  }
}

TEST(BitCast, round_trip_float) {
  float  ffive = 5.0f;
  double dfive = 5.0;

  using SF = int32_t;
  using UF = uint32_t;

  using SD = int64_t;
  using UD = uint64_t;

  EXPECT_EQ(ffive, bit_cast<float>(bit_cast<SF>(ffive)));
  EXPECT_EQ(ffive, bit_cast<float>(bit_cast<UF>(ffive)));

  EXPECT_EQ(dfive, bit_cast<double>(bit_cast<SD>(dfive)));
  EXPECT_EQ(dfive, bit_cast<double>(bit_cast<UD>(dfive)));
}

TEST(BitCast, round_trip_ptr) {
  int five = 5;
  int* pfive = &five;
  const int* cpfive = &five;

  using SIP = intptr_t;
  using UIP = uintptr_t;

  EXPECT_EQ(pfive, bit_cast<int*>(bit_cast<SIP>(pfive)));
  EXPECT_EQ(pfive, bit_cast<int*>(bit_cast<UIP>(pfive)));

  EXPECT_EQ(cpfive, bit_cast<const int*>(bit_cast<SIP>(cpfive)));
  EXPECT_EQ(cpfive, bit_cast<const int*>(bit_cast<UIP>(cpfive)));
}

TEST(BitCast, round_trip_const_ptr) {
  int five = 5;
  int* pfive = &five;
  const int* cpfive = &five;

  EXPECT_EQ(pfive, bit_cast<int*>(cpfive));
  EXPECT_EQ(cpfive, bit_cast<const int*>(pfive));
}

TEST(BitCast, round_trip_volatile_ptr) {
  int five = 5;
  int* pfive = &five;
  volatile int* vpfive = &five;

  EXPECT_EQ(pfive, bit_cast<int*>(vpfive));
  EXPECT_EQ(vpfive, bit_cast<volatile int*>(pfive));
}

class BitCastTest : public AllStatic {
 public:
  struct TrivialStruct1 {
    int member;

    const TrivialStruct1* operator&() const { return nullptr; }

    TrivialStruct1* operator&() { return nullptr; }
  };

  struct TrivialStruct2 {
    int member;

    const TrivialStruct2* operator&() const { return nullptr; }

    TrivialStruct2* operator&() { return nullptr; }
  };
};

TEST(BitCast, round_trip_trivial_struct) {
  BitCastTest::TrivialStruct1 s1 = {5};
  BitCastTest::TrivialStruct2 s2 = bit_cast<BitCastTest::TrivialStruct2>(s1);
  EXPECT_EQ(s1.member, s2.member);
}
