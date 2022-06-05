/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/intrinsicnode.hpp"
#include "unittest.hpp"

TEST_VM(opto, compress_expand_bits) {
  ASSERT_EQ(CompressBitsNode::compress_bits(-4LL, -1LL, 64), -4LL);
  ASSERT_EQ(CompressBitsNode::compress_bits(-4LL, -1LL, 32), (-4LL & 0xFFFFFFFFLL));
  ASSERT_EQ(CompressBitsNode::compress_bits(2147483647LL, -65535LL, 64), 65535LL);
  ASSERT_EQ(CompressBitsNode::compress_bits(2147483647LL, -65535LL, 32), 65535LL);
  ASSERT_EQ(CompressBitsNode::compress_bits(-2147483648LL, -65535LL, 64), 562949953355776LL);
  ASSERT_EQ(CompressBitsNode::compress_bits(-2147483648LL, -65535LL, 32), 65536LL);
  ASSERT_EQ(ExpandBitsNode::expand_bits(-4LL, -1LL, 64), -4LL);
  ASSERT_EQ(ExpandBitsNode::expand_bits(-4LL, -1LL, 32), (-4LL & 0xFFFFFFFFLL));
  ASSERT_EQ(ExpandBitsNode::expand_bits(2147483647LL, -65535LL, 64), 70368744112129LL);
  ASSERT_EQ(ExpandBitsNode::expand_bits(2147483647LL, -65535LL, 32), (-65535LL & 0xFFFFFFFFLL));
  ASSERT_EQ(ExpandBitsNode::expand_bits(-2147483648LL, -65535LL, 64), -70368744177664LL);
  ASSERT_EQ(ExpandBitsNode::expand_bits(-2147483648LL, -65535LL, 32), 0LL);
}

