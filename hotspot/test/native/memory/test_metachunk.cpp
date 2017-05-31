/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "memory/metachunk.hpp"
#include "unittest.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"

class MetachunkTest {
 public:
  static MetaWord* initial_top(Metachunk* metachunk) {
    return metachunk->initial_top();
  }
  static MetaWord* top(Metachunk* metachunk) {
    return metachunk->top();
  }

};

TEST(Metachunk, basic) {
  size_t size = 2 * 1024 * 1024;
  void* memory = malloc(size);
  ASSERT_TRUE(NULL != memory) << "Failed to malloc 2MB";

  Metachunk* metachunk = ::new (memory) Metachunk(size / BytesPerWord, NULL);

  EXPECT_EQ((MetaWord*) metachunk, metachunk->bottom());
  EXPECT_EQ((uintptr_t*) metachunk + metachunk->size(), metachunk->end());

  // Check sizes
  EXPECT_EQ(metachunk->size(), metachunk->word_size());
  EXPECT_EQ(pointer_delta(metachunk->end(), metachunk->bottom(),
                sizeof (MetaWord*)),
            metachunk->word_size());

  // Check usage
  EXPECT_EQ(metachunk->used_word_size(), metachunk->overhead());
  EXPECT_EQ(metachunk->word_size() - metachunk->used_word_size(),
            metachunk->free_word_size());
  EXPECT_EQ(MetachunkTest::top(metachunk), MetachunkTest::initial_top(metachunk));
  EXPECT_TRUE(metachunk->is_empty());

  // Allocate
  size_t alloc_size = 64; // Words
  EXPECT_TRUE(is_size_aligned(alloc_size, Metachunk::object_alignment()));

  MetaWord* mem = metachunk->allocate(alloc_size);

  // Check post alloc
  EXPECT_EQ(MetachunkTest::initial_top(metachunk), mem);
  EXPECT_EQ(MetachunkTest::top(metachunk), mem + alloc_size);
  EXPECT_EQ(metachunk->overhead() + alloc_size, metachunk->used_word_size());
  EXPECT_EQ(metachunk->word_size() - metachunk->used_word_size(),
            metachunk->free_word_size());
  EXPECT_FALSE(metachunk->is_empty());

  // Clear chunk
  metachunk->reset_empty();

  // Check post clear
  EXPECT_EQ(metachunk->used_word_size(), metachunk->overhead());
  EXPECT_EQ(metachunk->word_size() - metachunk->used_word_size(),
            metachunk->free_word_size());
  EXPECT_EQ(MetachunkTest::top(metachunk), MetachunkTest::initial_top(metachunk));
  EXPECT_TRUE(metachunk->is_empty());

  free(memory);
}
