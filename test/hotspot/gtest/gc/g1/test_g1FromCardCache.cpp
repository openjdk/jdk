/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1FromCardCache.inline.hpp"
#include "unittest.hpp"

TEST(G1FromCardCache, hit_and_miss) {
  const uintptr_t from_card = 64;
  const uint card_set_group_a = 3;
  const uint card_set_group_b = 13;
  const uint card_set_group_high = 1024;

  G1FromCardCache cache;

  EXPECT_FALSE(cache.contains_or_add(from_card, card_set_group_a));
  EXPECT_TRUE(cache.contains_or_add(from_card, card_set_group_a));

  // Retain multiple card set groups for the same from_card.
  EXPECT_FALSE(cache.contains_or_add(from_card, card_set_group_b));
  EXPECT_TRUE(cache.contains_or_add(from_card, card_set_group_a));
  EXPECT_TRUE(cache.contains_or_add(from_card, card_set_group_b));

  // A group id is not an array index.
  EXPECT_FALSE(cache.contains_or_add(from_card, card_set_group_high));
  EXPECT_TRUE(cache.contains_or_add(from_card, card_set_group_high));
}

TEST(G1FromCardCache, from_card_transition) {
  const uintptr_t from_card_a = 2;
  const uintptr_t from_card_b = 3;
  const uint card_set_group_id = 17;

  G1FromCardCache cache;

  EXPECT_FALSE(cache.contains_or_add(from_card_a, card_set_group_id));
  EXPECT_TRUE(cache.contains_or_add(from_card_a, card_set_group_id));

  // Discard previous from_card data.
  EXPECT_FALSE(cache.contains_or_add(from_card_b, card_set_group_id));
  EXPECT_TRUE(cache.contains_or_add(from_card_b, card_set_group_id));

  // Verify that it was discarded before.
  EXPECT_FALSE(cache.contains_or_add(from_card_a, card_set_group_id));
}

TEST(G1FromCardCache, cache_reset) {
  const uintptr_t from_card = 17;
  const uint card_set_group_id = 17;

  G1FromCardCache cache;

  EXPECT_FALSE(cache.contains_or_add(from_card, card_set_group_id));
  EXPECT_TRUE(cache.contains_or_add(from_card, card_set_group_id));

  cache.reset();

  EXPECT_FALSE(cache.contains_or_add(from_card, card_set_group_id));
  EXPECT_TRUE(cache.contains_or_add(from_card, card_set_group_id));
}
