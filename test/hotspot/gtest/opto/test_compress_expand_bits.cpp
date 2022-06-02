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
  ASSERT_EQ(CompressBitsNode::compress_bits(-4L, -1L, 64), -4L);
  ASSERT_EQ(CompressBitsNode::compress_bits(-4L, -1L, 32), (-4L & 0xFFFFFFFFL));
  ASSERT_EQ(CompressBitsNode::compress_bits(2147483647L, -65535L, 64), 65535L);
  ASSERT_EQ(CompressBitsNode::compress_bits(2147483647L, -65535L, 32), 65535L);
  ASSERT_EQ(CompressBitsNode::compress_bits(-2147483648L, -65535L, 64), 562949953355776L);
  ASSERT_EQ(CompressBitsNode::compress_bits(-2147483648L, -65535L, 32), 65536L);
  ASSERT_EQ(ExpandBitsNode::expand_bits(-4L, -1L, 64), -4L);
  ASSERT_EQ(ExpandBitsNode::expand_bits(-4L, -1L, 32), (-4L & 0xFFFFFFFFL));
  ASSERT_EQ(ExpandBitsNode::expand_bits(2147483647L, -65535L, 64), 70368744112129L);
  ASSERT_EQ(ExpandBitsNode::expand_bits(2147483647L, -65535L, 32), (-65535L & 0xFFFFFFFFL));
  ASSERT_EQ(ExpandBitsNode::expand_bits(-2147483648L, -65535L, 64), -70368744177664L);
  ASSERT_EQ(ExpandBitsNode::expand_bits(-2147483648L, -65535L, 32), 0L);
}

