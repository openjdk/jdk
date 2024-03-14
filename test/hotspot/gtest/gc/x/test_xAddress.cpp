/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xGlobals.hpp"
#include "unittest.hpp"

class XAddressTest : public ::testing::Test {
protected:
  static void is_good_bit(uintptr_t bit_mask) {
    // Setup
    XAddress::initialize();
    XAddress::set_good_mask(bit_mask);

    // Test that a pointer with only the given bit is considered good.
    EXPECT_EQ(XAddress::is_good(XAddressMetadataMarked0),  (bit_mask == XAddressMetadataMarked0));
    EXPECT_EQ(XAddress::is_good(XAddressMetadataMarked1),  (bit_mask == XAddressMetadataMarked1));
    EXPECT_EQ(XAddress::is_good(XAddressMetadataRemapped), (bit_mask == XAddressMetadataRemapped));

    // Test that a pointer with the given bit and some extra bits is considered good.
    EXPECT_EQ(XAddress::is_good(XAddressMetadataMarked0  | 0x8),(bit_mask == XAddressMetadataMarked0));
    EXPECT_EQ(XAddress::is_good(XAddressMetadataMarked1  | 0x8), (bit_mask == XAddressMetadataMarked1));
    EXPECT_EQ(XAddress::is_good(XAddressMetadataRemapped | 0x8), (bit_mask == XAddressMetadataRemapped));

    // Test that null is not considered good.
    EXPECT_FALSE(XAddress::is_good(0));
  }

  static void is_good_or_null_bit(uintptr_t bit_mask) {
    // Setup
    XAddress::initialize();
    XAddress::set_good_mask(bit_mask);

    // Test that a pointer with only the given bit is considered good.
    EXPECT_EQ(XAddress::is_good_or_null(XAddressMetadataMarked0),  (bit_mask == XAddressMetadataMarked0));
    EXPECT_EQ(XAddress::is_good_or_null(XAddressMetadataMarked1),  (bit_mask == XAddressMetadataMarked1));
    EXPECT_EQ(XAddress::is_good_or_null(XAddressMetadataRemapped), (bit_mask == XAddressMetadataRemapped));

    // Test that a pointer with the given bit and some extra bits is considered good.
    EXPECT_EQ(XAddress::is_good_or_null(XAddressMetadataMarked0  | 0x8), (bit_mask == XAddressMetadataMarked0));
    EXPECT_EQ(XAddress::is_good_or_null(XAddressMetadataMarked1  | 0x8), (bit_mask == XAddressMetadataMarked1));
    EXPECT_EQ(XAddress::is_good_or_null(XAddressMetadataRemapped | 0x8), (bit_mask == XAddressMetadataRemapped));

    // Test that null is considered good_or_null.
    EXPECT_TRUE(XAddress::is_good_or_null(0));
  }

  static void finalizable() {
    // Setup
    XAddress::initialize();
    XAddress::flip_to_marked();

    // Test that a normal good pointer is good and weak good, but not finalizable
    const uintptr_t addr1 = XAddress::good(1);
    EXPECT_FALSE(XAddress::is_finalizable(addr1));
    EXPECT_TRUE(XAddress::is_marked(addr1));
    EXPECT_FALSE(XAddress::is_remapped(addr1));
    EXPECT_TRUE(XAddress::is_weak_good(addr1));
    EXPECT_TRUE(XAddress::is_weak_good_or_null(addr1));
    EXPECT_TRUE(XAddress::is_good(addr1));
    EXPECT_TRUE(XAddress::is_good_or_null(addr1));

    // Test that a finalizable good pointer is finalizable and weak good, but not good
    const uintptr_t addr2 = XAddress::finalizable_good(1);
    EXPECT_TRUE(XAddress::is_finalizable(addr2));
    EXPECT_TRUE(XAddress::is_marked(addr2));
    EXPECT_FALSE(XAddress::is_remapped(addr2));
    EXPECT_TRUE(XAddress::is_weak_good(addr2));
    EXPECT_TRUE(XAddress::is_weak_good_or_null(addr2));
    EXPECT_FALSE(XAddress::is_good(addr2));
    EXPECT_FALSE(XAddress::is_good_or_null(addr2));

    // Flip to remapped and test that it's no longer weak good
    XAddress::flip_to_remapped();
    EXPECT_TRUE(XAddress::is_finalizable(addr2));
    EXPECT_TRUE(XAddress::is_marked(addr2));
    EXPECT_FALSE(XAddress::is_remapped(addr2));
    EXPECT_FALSE(XAddress::is_weak_good(addr2));
    EXPECT_FALSE(XAddress::is_weak_good_or_null(addr2));
    EXPECT_FALSE(XAddress::is_good(addr2));
    EXPECT_FALSE(XAddress::is_good_or_null(addr2));
  }
};

TEST_F(XAddressTest, is_good) {
  is_good_bit(XAddressMetadataMarked0);
  is_good_bit(XAddressMetadataMarked1);
  is_good_bit(XAddressMetadataRemapped);
}

TEST_F(XAddressTest, is_good_or_null) {
  is_good_or_null_bit(XAddressMetadataMarked0);
  is_good_or_null_bit(XAddressMetadataMarked1);
  is_good_or_null_bit(XAddressMetadataRemapped);
}

TEST_F(XAddressTest, is_weak_good_or_null) {
#define check_is_weak_good_or_null(value)                                        \
  EXPECT_EQ(XAddress::is_weak_good_or_null(value),                               \
            (XAddress::is_good_or_null(value) || XAddress::is_remapped(value)))  \
    << "is_good_or_null: " << XAddress::is_good_or_null(value)                   \
    << " is_remaped: " << XAddress::is_remapped(value)                           \
    << " is_good_or_null_or_remapped: " << XAddress::is_weak_good_or_null(value)

  check_is_weak_good_or_null((uintptr_t)nullptr);
  check_is_weak_good_or_null(XAddressMetadataMarked0);
  check_is_weak_good_or_null(XAddressMetadataMarked1);
  check_is_weak_good_or_null(XAddressMetadataRemapped);
  check_is_weak_good_or_null((uintptr_t)0x123);
}

TEST_F(XAddressTest, finalizable) {
  finalizable();
}
