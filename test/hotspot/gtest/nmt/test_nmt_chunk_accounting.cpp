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

#include "memory/allocation.hpp"
#include "memory/arena.hpp"
#include "nmt/mallocHeader.inline.hpp"
#include "nmt/mallocSiteTable.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

struct CountersSnapshot {
    size_t mtChunk_malloc_size;
    size_t mtChunk_malloc_count;
    size_t mtTest_arena_size;
    size_t all_mallocs_size;
    size_t all_mallocs_count;
};

static CountersSnapshot take_snapshot() {
  MallocMemorySnapshot* mms = MallocMemorySummary::as_snapshot();
  CountersSnapshot cs;
  cs.mtChunk_malloc_size = mms->by_tag(mtChunk)->malloc_size();
  cs.mtChunk_malloc_count = mms->by_tag(mtChunk)->malloc_count();
  cs.mtTest_arena_size = mms->by_tag(mtTest)->arena_size();
  cs.all_mallocs_size = mms->total();
  cs.all_mallocs_count = mms->total_count();
  return cs;
}

// This test checks the non-standard sized chunks path which bypasses the chunk pool.
TEST_VM(NMTChunkAccounting, non_standard_chunks) {
  if (!MemTracker::enabled()) {
    return;
  }

  static const size_t NUM_ARENAS = 200;
  static const size_t CHUNK_SIZE = ARENA_ALIGN(5000);

  const CountersSnapshot before = take_snapshot();
  const size_t expected_arena_size = before.mtTest_arena_size + NUM_ARENAS * CHUNK_SIZE;

  // Create a bunch of arenas, resulting in allocating new heap chunks.
  Arena* arenas[NUM_ARENAS];
  for (size_t i = 0; i < NUM_ARENAS; i++) {
    arenas[i] = new (mtTest) Arena(mtTest, Arena::Tag::tag_other, CHUNK_SIZE);
  }

  {
    const CountersSnapshot middle = take_snapshot();
    // Concurrent code can malloc and free too, therefore we use a leeway factor of 50%
    // Verify that the memory was attributed to the arena's tag, not mtChunk.
    EXPECT_LE(middle.mtChunk_malloc_size, before.mtChunk_malloc_size + expected_arena_size / 2);
    EXPECT_LE(middle.mtChunk_malloc_count, before.mtChunk_malloc_count + NUM_ARENAS / 2);
    EXPECT_EQ(middle.mtTest_arena_size, expected_arena_size);
    // Total malloc counters should have grown too.
    EXPECT_GE(middle.all_mallocs_size, before.all_mallocs_size + expected_arena_size / 2);
    EXPECT_GE(middle.all_mallocs_count, before.all_mallocs_count + NUM_ARENAS / 2);
  }

  for (size_t i = 0; i < NUM_ARENAS; i++) {
    delete arenas[i];
  }

  const CountersSnapshot after = take_snapshot();
  // Verify mtChunk still hasn't changed and mtTest is back to zero.
  EXPECT_LE(after.mtChunk_malloc_size, before.mtChunk_malloc_size + expected_arena_size / 2);
  EXPECT_LE(after.mtChunk_malloc_count, before.mtChunk_malloc_count + NUM_ARENAS / 2);
  EXPECT_EQ(after.mtTest_arena_size, (size_t)0);
}

// This test uses standard sized chunks which should exercise the code path using the chunk pool.
#ifdef ASSERT
TEST_VM(NMTChunkAccounting, standard_chunks) {
  if (!MemTracker::enabled()) {
    return;
  }
  static const size_t GROW_SIZE = Chunk::size;
  static const size_t NUM_ALLOCS = 512;
  CountersSnapshot after_grow;
  Arena::suspend_chunk_pool_cleaning(true);
  {
    Arena arena(mtTest);
    const CountersSnapshot baseline = take_snapshot();
    for (size_t i = 0; i < NUM_ALLOCS; i++) {
      arena.Amalloc(GROW_SIZE);
    }

    after_grow = take_snapshot();

    const size_t expected_arena_size = baseline.mtTest_arena_size + GROW_SIZE * NUM_ALLOCS;

    // Concurrent code can malloc and free too, therefore we use a leeway factor of 50%
    // mtChunk should not have grown.
    EXPECT_LE(after_grow.mtChunk_malloc_size, baseline.mtChunk_malloc_size + expected_arena_size / 2);
    EXPECT_LE(after_grow.mtChunk_malloc_count, baseline.mtChunk_malloc_count + NUM_ALLOCS / 2);
    // mtTest should grow matching arena size.
    EXPECT_EQ(arena.size_in_bytes(), expected_arena_size);
    EXPECT_EQ(after_grow.mtTest_arena_size, arena.size_in_bytes());
    // Total malloc counters should have grown too, but ensure we account for free chunks pre-existing in the pool.
    EXPECT_GE(after_grow.all_mallocs_size, baseline.all_mallocs_size + expected_arena_size / 2 -  baseline.mtChunk_malloc_size);
    EXPECT_GE(after_grow.all_mallocs_count, baseline.all_mallocs_count + NUM_ALLOCS / 2 - baseline.mtChunk_malloc_count);
  }

  const CountersSnapshot after_destroy = take_snapshot();

  // mtChunk should have grown after chunks are returned to the pool.
  EXPECT_GE(after_destroy.mtChunk_malloc_size, after_grow.mtChunk_malloc_size + GROW_SIZE * NUM_ALLOCS / 2);
  EXPECT_GE(after_destroy.mtChunk_malloc_count, after_grow.mtChunk_malloc_count + NUM_ALLOCS / 2);
  // And mtTest should be completely empty after the arena is destroyed.
  EXPECT_EQ(after_destroy.mtTest_arena_size, (size_t)0);


  Arena arena(mtTest);
  const CountersSnapshot baseline2 = take_snapshot();
  for (size_t i = 0; i < NUM_ALLOCS; i++) {
    arena.Amalloc(GROW_SIZE);
  }
  const CountersSnapshot after_grow2 = take_snapshot();
  const size_t expected_arena_size = baseline2.mtTest_arena_size + GROW_SIZE * NUM_ALLOCS;
  const size_t expected_min_size_shrunk = GROW_SIZE * NUM_ALLOCS / 2;
  const size_t expected_min_count_shrunk = NUM_ALLOCS / 2;

  // mtChunk should have decreased as chunks are pulled from pool.
  EXPECT_LE(after_grow2.mtChunk_malloc_size, baseline2.mtChunk_malloc_size - expected_min_size_shrunk);
  EXPECT_LE(after_grow2.mtChunk_malloc_count, baseline2.mtChunk_malloc_count - expected_min_count_shrunk);
  // mtTest should grow matching arena size.
  EXPECT_EQ(arena.size_in_bytes(), expected_arena_size);
  EXPECT_EQ(after_grow2.mtTest_arena_size, arena.size_in_bytes());
}
#endif

// This test verifies malloc header tags, MST markers, and stacks.
TEST_VM(NMTChunkAccounting, mst) {
  if (!MemTracker::enabled() || MemTracker::tracking_level() != NMT_detail) {
    return;
  }
  NativeCallStack stack_a = CALLER_PC;
  NativeCallStack stack_b = CURRENT_PC;
  void* allocation = os::malloc(100, mtChunk, stack_a);
  MallocHeader* header = (MallocHeader*)allocation - 1;
  uint32_t old_marker = header->mst_marker();
  NativeCallStack old_stack;
  ASSERT_TRUE(MallocSiteTable::access_stack(old_stack, *header));
  EXPECT_TRUE(old_stack.equals(stack_a));

  MemTracker::chunk_assigned_to_arena(allocation, mtTest, stack_b);
  EXPECT_TRUE(header->mem_tag() == mtTest);
  EXPECT_NE(header->mst_marker(), old_marker);
  NativeCallStack new_stack;
  EXPECT_TRUE(MallocSiteTable::access_stack(new_stack, *header));
  EXPECT_TRUE(new_stack.equals(stack_b));

  MemTracker::add_chunk_to_pool(allocation, stack_a);
  ASSERT_TRUE(header->mem_tag() == mtChunk);
  ASSERT_TRUE(MallocSiteTable::access_stack(new_stack, *header));
  EXPECT_TRUE(new_stack.equals(stack_a));
  os::free(allocation);

  // Now test the path where new and old tags are equal.
  // This should only happen when a fresh chunk is created to grow an arena.
  allocation = os::malloc(100, mtTest, stack_b);
  header = (MallocHeader*)allocation - 1;
  old_marker = header->mst_marker();
  ASSERT_TRUE(MallocSiteTable::access_stack(old_stack, *header));
  EXPECT_TRUE(old_stack.equals(stack_b));

  MemTracker::chunk_assigned_to_arena(allocation, mtTest, stack_b);
  EXPECT_TRUE(header->mem_tag() == mtTest);
  EXPECT_EQ(header->mst_marker(), old_marker);
  EXPECT_TRUE(MallocSiteTable::access_stack(new_stack, *header));
  EXPECT_TRUE(new_stack.equals(stack_b));
}
