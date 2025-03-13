/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023 Red Hat, Inc. All rights reserved.
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

#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/commitLimiter.hpp"
#include "memory/metaspace/counters.hpp"
#include "memory/metaspace/internalStats.hpp"
#include "memory/metaspace/freeBlocks.hpp"
#include "memory/metaspace/metablock.inline.hpp"
#include "memory/metaspace/metaspaceArena.hpp"
#include "memory/metaspace/metaspaceArenaGrowthPolicy.hpp"
#include "memory/metaspace/metachunkList.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceSettings.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "memory/metaspace.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// #define LOG_PLEASE
#include "metaspaceGtestCommon.hpp"
#include "metaspaceGtestContexts.hpp"
#include "metaspaceGtestRangeHelpers.hpp"

#define HANDLE_FAILURE \
  if (testing::Test::HasFailure()) { \
    return; \
  }

namespace metaspace {

class MetaspaceArenaTestFriend {
  const MetaspaceArena* const _arena;
public:
  MetaspaceArenaTestFriend(const MetaspaceArena* arena) : _arena(arena) {}
  const MetachunkList& chunks() const { return _arena->_chunks; }
  const FreeBlocks* fbl() const { return _arena->_fbl; }
};

class MetaspaceArenaTestHelper {

  MetaspaceGtestContext& _context;
  const ArenaGrowthPolicy* const _growth_policy;

  MetaspaceArena* _arena;

public:

  // Create a helper; growth policy is directly specified
  MetaspaceArenaTestHelper(MetaspaceGtestContext& helper, const ArenaGrowthPolicy* growth_policy,
                           size_t allocation_alignment_words = Metaspace::min_allocation_alignment_words) :
    _context(helper), _growth_policy(growth_policy), _arena(nullptr)
  {
    _arena = new MetaspaceArena(_context.context(), _growth_policy, allocation_alignment_words, "gtest-MetaspaceArena");
    DEBUG_ONLY(_arena->verify());
    _context.inc_num_arenas_created();
  }


  // Create a helper; growth policy for arena is determined by the given spacetype|class tupel
  MetaspaceArenaTestHelper(MetaspaceGtestContext& helper,
                           Metaspace::MetaspaceType space_type, bool is_class,
                           size_t allocation_alignment_words = Metaspace::min_allocation_alignment_words) :
    MetaspaceArenaTestHelper(helper, ArenaGrowthPolicy::policy_for_space_type(space_type, is_class), allocation_alignment_words)
  {}

  ~MetaspaceArenaTestHelper() {
    delete_arena_with_tests();
  }

  MetaspaceArena* arena() const { return _arena; }

  // Note: all test functions return void due to gtests limitation that we cannot use ASSERT
  // in non-void returning tests.

  void delete_arena_with_tests() {
    if (_arena != nullptr) {
      size_t used_words_before = _context.used_words();
      size_t committed_words_before = _context.committed_words();
      DEBUG_ONLY(_arena->verify());
      delete _arena;
      _arena = nullptr;
      size_t used_words_after = _context.used_words();
      size_t committed_words_after = _context.committed_words();
      assert(_context.num_arenas_created() >= 1, "Sanity");
      if (_context.num_arenas_created() == 1) {
        ASSERT_0(used_words_after);
      } else {
        ASSERT_LE(used_words_after, used_words_before);
      }
      ASSERT_LE(committed_words_after, committed_words_before);
    }
  }

  void usage_numbers_with_test(size_t* p_used, size_t* p_committed, size_t* p_capacity) const {
    size_t arena_used = 0, arena_committed = 0, arena_reserved = 0;
    _arena->usage_numbers(&arena_used, &arena_committed, &arena_reserved);
    EXPECT_GE(arena_committed, arena_used);
    EXPECT_GE(arena_reserved, arena_committed);

    size_t context_used = _context.used_words();
    size_t context_committed = _context.committed_words();
    size_t context_reserved = _context.reserved_words();
    EXPECT_GE(context_committed, context_used);
    EXPECT_GE(context_reserved, context_committed);

    // If only one arena uses the context, usage numbers must match.
    if (_context.num_arenas_created() == 1) {
      EXPECT_EQ(context_used, arena_used);
    } else {
      assert(_context.num_arenas_created() > 1, "Sanity");
      EXPECT_GE(context_used, arena_used);
    }

    // commit, reserve numbers don't have to match since free chunks may exist
    EXPECT_GE(context_committed, arena_committed);
    EXPECT_GE(context_reserved, arena_reserved);

    if (p_used) {
      *p_used = arena_used;
    }
    if (p_committed) {
      *p_committed = arena_committed;
    }
    if (p_capacity) {
      *p_capacity = arena_reserved;
    }
  }

  // Allocate; caller expects success; return pointer in *p_return_value
  void allocate_from_arena_with_tests_expect_success(MetaWord** p_return_value, size_t word_size) {
    allocate_from_arena_with_tests(p_return_value, word_size);
    ASSERT_NOT_NULL(*p_return_value);
  }

  // Allocate; caller expects success but is not interested in return value
  void allocate_from_arena_with_tests_expect_success(size_t word_size) {
    MetaWord* dummy = nullptr;
    allocate_from_arena_with_tests_expect_success(&dummy, word_size);
  }

  // Allocate; caller expects failure
  void allocate_from_arena_with_tests_expect_failure(size_t word_size) {
    MetaWord* dummy = nullptr;
    allocate_from_arena_with_tests(&dummy, word_size);
    ASSERT_NULL(dummy);
  }

  void allocate_from_arena_with_tests(MetaWord** p_return_value, size_t word_size) {
    MetaBlock result, wastage;
    allocate_from_arena_with_tests(word_size, result, wastage);
    if (wastage.is_nonempty()) {
      _arena->deallocate(wastage);
      wastage.reset();
    }
    (*p_return_value) = result.base();
  }

  // Allocate; it may or may not work; return value in *p_return_value
  void allocate_from_arena_with_tests(size_t word_size, MetaBlock& result, MetaBlock& wastage) {

    // Note: usage_numbers walks all chunks in use and counts.
    size_t used = 0, committed = 0, capacity = 0;
    usage_numbers_with_test(&used, &committed, &capacity);

    size_t possible_expansion = _context.commit_limiter().possible_expansion_words();

    result = _arena->allocate(word_size, wastage);

    SOMETIMES(DEBUG_ONLY(_arena->verify();))

    size_t used2 = 0, committed2 = 0, capacity2 = 0;
    usage_numbers_with_test(&used2, &committed2, &capacity2);

    if (result.is_empty()) {
      // Allocation failed.
      ASSERT_LT(possible_expansion, word_size);
      ASSERT_EQ(used, used2);
      ASSERT_EQ(committed, committed2);
      ASSERT_EQ(capacity, capacity2);
    } else {
      // Allocation succeeded. Should be correctly aligned.
      ASSERT_TRUE(result.is_aligned_base(_arena->allocation_alignment_words()));

      // used: may go up or may not (since our request may have been satisfied from the freeblocklist
      //   whose content already counts as used).
      // committed: may go up, may not
      // capacity: ditto
      ASSERT_GE(used2, used);
      ASSERT_GE(committed2, committed);
      ASSERT_GE(capacity2, capacity);
    }
  }

  // Allocate; it may or may not work; but caller does not care for the result value
  void allocate_from_arena_with_tests(size_t word_size) {
    MetaWord* dummy = nullptr;
    allocate_from_arena_with_tests(&dummy, word_size);
  }

  void deallocate_with_tests(MetaWord* p, size_t word_size) {
    size_t used = 0, committed = 0, capacity = 0;
    usage_numbers_with_test(&used, &committed, &capacity);

    _arena->deallocate(MetaBlock(p, word_size));

    SOMETIMES(DEBUG_ONLY(_arena->verify();))

    size_t used2 = 0, committed2 = 0, capacity2 = 0;
    usage_numbers_with_test(&used2, &committed2, &capacity2);

    // Nothing should have changed. Deallocated blocks are added to the free block list
    // which still counts as used.
    ASSERT_EQ(used2, used);
    ASSERT_EQ(committed2, committed);
    ASSERT_EQ(capacity2, capacity);
  }

  ArenaStats get_arena_statistics() const {
    ArenaStats stats;
    _arena->add_to_statistics(&stats);
    return stats;
  }

  MetaspaceArenaTestFriend internal_access() const {
    return MetaspaceArenaTestFriend (_arena);
  }

  // Convenience method to return number of chunks in arena (including current chunk)
  int get_number_of_chunks() const {
    return internal_access().chunks().count();
  }

};

static void test_basics(size_t commit_limit, bool is_micro) {
  MetaspaceGtestContext context(commit_limit);
  const Metaspace::MetaspaceType type = is_micro ? Metaspace::ClassMirrorHolderMetaspaceType : Metaspace::StandardMetaspaceType;
  MetaspaceArenaTestHelper helper(context, type, false);

  helper.allocate_from_arena_with_tests(1);
  helper.allocate_from_arena_with_tests(128);
  helper.allocate_from_arena_with_tests(128 * K);
  helper.allocate_from_arena_with_tests(1);
  helper.allocate_from_arena_with_tests(128);
  helper.allocate_from_arena_with_tests(128 * K);
}

TEST_VM(metaspace, MetaspaceArena_basics_micro_nolimit) {
  test_basics(max_uintx, true);
}

TEST_VM(metaspace, MetaspaceArena_basics_micro_limit) {
  test_basics(256 * K, true);
}

TEST_VM(metaspace, MetaspaceArena_basics_standard_nolimit) {
  test_basics(max_uintx, false);
}

TEST_VM(metaspace, MetaspaceArena_basics_standard_limit) {
  test_basics(256 * K, false);
}

// Test chunk enlargement:
//  A single MetaspaceArena, left undisturbed with place to grow. Slowly fill arena up.
//  We should see at least some occurrences of chunk-in-place enlargement.
static void test_chunk_enlargment_simple(Metaspace::MetaspaceType spacetype, bool is_class) {

  MetaspaceGtestContext context;
  MetaspaceArenaTestHelper helper(context, (Metaspace::MetaspaceType)spacetype, is_class);

  uint64_t n1 = metaspace::InternalStats::num_chunks_enlarged();

  size_t allocated = 0;
  while (allocated <= MAX_CHUNK_WORD_SIZE &&
         metaspace::InternalStats::num_chunks_enlarged() == n1) {
    size_t s = IntRange(32, 128).random_value();
    helper.allocate_from_arena_with_tests_expect_success(s);
    allocated += metaspace::get_raw_word_size_for_requested_word_size(s);
  }

  EXPECT_GT(metaspace::InternalStats::num_chunks_enlarged(), n1);

}

// Do this test for some of the standard types; don't do it for the boot loader type
//  since that one starts out with max chunk size so we would not see any enlargement.

TEST_VM(metaspace, MetaspaceArena_test_enlarge_in_place_standard_c) {
  test_chunk_enlargment_simple(Metaspace::StandardMetaspaceType, true);
}

TEST_VM(metaspace, MetaspaceArena_test_enlarge_in_place_standard_nc) {
  test_chunk_enlargment_simple(Metaspace::StandardMetaspaceType, false);
}

TEST_VM(metaspace, MetaspaceArena_test_enlarge_in_place_micro_c) {
  test_chunk_enlargment_simple(Metaspace::ClassMirrorHolderMetaspaceType, true);
}

TEST_VM(metaspace, MetaspaceArena_test_enlarge_in_place_micro_nc) {
  test_chunk_enlargment_simple(Metaspace::ClassMirrorHolderMetaspaceType, false);
}

// Test chunk enlargement:
// A single MetaspaceArena, left undisturbed with place to grow. Slowly fill arena up.
//  We should see occurrences of chunk-in-place enlargement.
//  Here, we give it an ideal policy which should enable the initial chunk to grow unmolested
//  until finish.
TEST_VM(metaspace, MetaspaceArena_test_enlarge_in_place_2) {
  // Note: internally, chunk in-place enlargement is disallowed if growing the chunk
  //  would cause the arena to claim more memory than its growth policy allows. This
  //  is done to prevent the arena to grow too fast.
  //
  // In order to test in-place growth here without that restriction I give it an
  //  artificial growth policy which starts out with a tiny chunk size, then balloons
  //  right up to max chunk size. This will cause the initial chunk to be tiny, and
  //  then the arena is able to grow it without violating growth policy.
  chunklevel_t growth[] = { HIGHEST_CHUNK_LEVEL, ROOT_CHUNK_LEVEL };
  ArenaGrowthPolicy growth_policy(growth, 2);

  MetaspaceGtestContext context;
  MetaspaceArenaTestHelper helper(context, &growth_policy);

  uint64_t n1 = metaspace::InternalStats::num_chunks_enlarged();

  size_t allocated = 0;
  while (allocated <= MAX_CHUNK_WORD_SIZE) {
    size_t s = IntRange(32, 128).random_value();
    helper.allocate_from_arena_with_tests_expect_success(s);
    allocated += metaspace::get_raw_word_size_for_requested_word_size(s);
    if (allocated <= MAX_CHUNK_WORD_SIZE) {
      // Chunk should have been enlarged in place
      ASSERT_EQ(1, helper.get_number_of_chunks());
    } else {
      // Next chunk should have started
      ASSERT_EQ(2, helper.get_number_of_chunks());
    }
  }

  int times_chunk_were_enlarged = metaspace::InternalStats::num_chunks_enlarged() - n1;
  LOG("chunk was enlarged %d times.", times_chunk_were_enlarged);

  ASSERT_GT0(times_chunk_were_enlarged);

}

// Regression test: Given a single MetaspaceArena, left undisturbed with place to grow,
//  test that in place enlargement correctly fails if growing the chunk would bring us
//  beyond the max. size of a chunk.
TEST_VM(metaspace, MetaspaceArena_test_failing_to_enlarge_in_place_max_chunk_size) {
  MetaspaceGtestContext context;

  for (size_t first_allocation_size = 1; first_allocation_size <= MAX_CHUNK_WORD_SIZE / 2; first_allocation_size *= 2) {

    MetaspaceArenaTestHelper helper(context, Metaspace::StandardMetaspaceType, false);

    // we allocate first a small amount, then the full amount possible.
    // The sum of first and second allocation should bring us above root chunk size.
    // This should work, we should not see any problems, but no chunk enlargement should
    // happen.
    int n1 = metaspace::InternalStats::num_chunks_enlarged();

    helper.allocate_from_arena_with_tests_expect_success(first_allocation_size);
    EXPECT_EQ(helper.get_number_of_chunks(), 1);

    helper.allocate_from_arena_with_tests_expect_success(MAX_CHUNK_WORD_SIZE - first_allocation_size + 1);
    EXPECT_EQ(helper.get_number_of_chunks(), 2);

    int times_chunk_were_enlarged = metaspace::InternalStats::num_chunks_enlarged() - n1;
    LOG("chunk was enlarged %d times.", times_chunk_were_enlarged);

    EXPECT_0(times_chunk_were_enlarged);

  }
}

// Regression test: Given a single MetaspaceArena, left undisturbed with place to grow,
//  test that in place enlargement correctly fails if growing the chunk would cause more
//  than doubling its size
TEST_VM(metaspace, MetaspaceArena_test_failing_to_enlarge_in_place_doubling_chunk_size) {
  MetaspaceGtestContext context;
  MetaspaceArenaTestHelper helper(context, Metaspace::StandardMetaspaceType, false);

  int n1 = metaspace::InternalStats::num_chunks_enlarged();

  helper.allocate_from_arena_with_tests_expect_success(1000);
  EXPECT_EQ(helper.get_number_of_chunks(), 1);

  helper.allocate_from_arena_with_tests_expect_success(4000);
  EXPECT_EQ(helper.get_number_of_chunks(), 2);

  int times_chunk_were_enlarged = metaspace::InternalStats::num_chunks_enlarged() - n1;
  LOG("chunk was enlarged %d times.", times_chunk_were_enlarged);

  EXPECT_0(times_chunk_were_enlarged);

}

// Test the MetaspaceArenas' free block list:
// Allocate, deallocate, then allocate the same block again. The second allocate should
// reuse the deallocated block.
TEST_VM(metaspace, MetaspaceArena_deallocate) {
  for (size_t s = 2; s <= MAX_CHUNK_WORD_SIZE; s *= 2) {
    MetaspaceGtestContext context;
    MetaspaceArenaTestHelper helper(context, Metaspace::StandardMetaspaceType, false);

    MetaWord* p1 = nullptr;
    helper.allocate_from_arena_with_tests_expect_success(&p1, s);
    ASSERT_FALSE(HasFailure());

    size_t used1 = 0, capacity1 = 0;
    helper.usage_numbers_with_test(&used1, nullptr, &capacity1);
    ASSERT_FALSE(HasFailure());
    ASSERT_EQ(used1, s);

    helper.deallocate_with_tests(p1, s);

    size_t used2 = 0, capacity2 = 0;
    helper.usage_numbers_with_test(&used2, nullptr, &capacity2);
    ASSERT_FALSE(HasFailure());
    ASSERT_EQ(used1, used2);
    ASSERT_EQ(capacity2, capacity2);

    MetaWord* p2 = nullptr;
    helper.allocate_from_arena_with_tests_expect_success(&p2, s);
    ASSERT_FALSE(HasFailure());

    size_t used3 = 0, capacity3 = 0;
    helper.usage_numbers_with_test(&used3, nullptr, &capacity3);
    ASSERT_FALSE(HasFailure());
    ASSERT_EQ(used3, used2);
    ASSERT_EQ(capacity3, capacity2);

    // Actually, we should get the very same allocation back
    ASSERT_EQ(p1, p2);
  }
}

static void test_recover_from_commit_limit_hit() {

  // Test:
  // - Multiple MetaspaceArena allocate (operating under the same commit limiter).
  // - One, while attempting to commit parts of its current chunk on demand,
  //   triggers the limit and cannot commit its chunk further.
  // - We release the other MetaspaceArena - its content is put back to the
  //   freelists.
  // - We re-attempt allocation from the first manager. It should now succeed.
  //
  // This means if the first MetaspaceArena may have to let go of its current chunk and
  // retire it and take a fresh chunk from the freelist.

  const size_t commit_limit = Settings::commit_granule_words() * 10;
  MetaspaceGtestContext context(commit_limit);

  // The first MetaspaceArena mimicks a micro loader. This will fill the free
  //  chunk list with very small chunks. We allocate from them in an interleaved
  //  way to cause fragmentation.
  MetaspaceArenaTestHelper helper1(context, Metaspace::ClassMirrorHolderMetaspaceType, false);
  MetaspaceArenaTestHelper helper2(context, Metaspace::ClassMirrorHolderMetaspaceType, false);

  // This MetaspaceArena should hit the limit. We use BootMetaspaceType here since
  // it gets a large initial chunk which is committed
  // on demand and we are likely to hit a commit limit while trying to expand it.
  MetaspaceArenaTestHelper helper3(context, Metaspace::BootMetaspaceType, false);

  // Allocate space until we have below two but above one granule left
  size_t allocated_from_1_and_2 = 0;
  while (context.commit_limiter().possible_expansion_words() >= Settings::commit_granule_words() * 2 &&
      allocated_from_1_and_2 < commit_limit) {
    helper1.allocate_from_arena_with_tests_expect_success(1);
    helper2.allocate_from_arena_with_tests_expect_success(1);
    allocated_from_1_and_2 += 2;
    HANDLE_FAILURE
  }

  // Now, allocating from helper3, creep up on the limit
  size_t allocated_from_3 = 0;
  MetaWord* p = nullptr;
  while ( (helper3.allocate_from_arena_with_tests(&p, 1), p != nullptr) &&
         ++allocated_from_3 < Settings::commit_granule_words() * 2);

  EXPECT_LE(allocated_from_3, Settings::commit_granule_words() * 2);

  // We expect the freelist to be empty of committed space...
  EXPECT_0(context.cm().calc_committed_word_size());

  //msthelper.cm().print_on(tty);

  // Release the first MetaspaceArena.
  helper1.delete_arena_with_tests();

  //msthelper.cm().print_on(tty);

  // Should have populated the freelist with committed space
  // We expect the freelist to be empty of committed space...
  EXPECT_GT(context.cm().calc_committed_word_size(), (size_t)0);

  // Repeat allocation from helper3, should now work.
  helper3.allocate_from_arena_with_tests_expect_success(1);

}

TEST_VM(metaspace, MetaspaceArena_recover_from_limit_hit) {
  test_recover_from_commit_limit_hit();
}

static void test_controlled_growth(Metaspace::MetaspaceType type, bool is_class,
                                   size_t expected_starting_capacity,
                                   bool test_in_place_enlargement)
{

  // From a MetaspaceArena in a clean room allocate tiny amounts;
  // watch it grow. Used/committed/capacity should not grow in
  // large jumps. Also, different types of MetaspaceArena should
  // have different initial capacities.

  MetaspaceGtestContext context;
  MetaspaceArenaTestHelper smhelper(context, type, is_class);

  const Metaspace::MetaspaceType other_type =
         (type == Metaspace::StandardMetaspaceType) ? Metaspace::ClassMirrorHolderMetaspaceType : Metaspace::StandardMetaspaceType;
  MetaspaceArenaTestHelper smhelper_harrasser(context, other_type, true);

  size_t used = 0, committed = 0, capacity = 0;
  const size_t alloc_words = 16;

  smhelper.arena()->usage_numbers(&used, &committed, &capacity);
  ASSERT_0(used);
  ASSERT_0(committed);
  ASSERT_0(capacity);

  ///// First allocation //

  smhelper.allocate_from_arena_with_tests_expect_success(alloc_words);

  smhelper.arena()->usage_numbers(&used, &committed, &capacity);

  ASSERT_EQ(used, alloc_words);
  ASSERT_GE(committed, used);
  ASSERT_GE(capacity, committed);

  ASSERT_EQ(capacity, expected_starting_capacity);

  // What happens when we allocate, commit wise:
  // Arena allocates from current chunk, committing needed memory from the chunk on demand.
  // The chunk asks the underlying vsnode to commit the area it is located in. Since the
  // chunk may be smaller than one commit granule, this may result in surrounding memory
  // also getting committed.
  // In reality we will commit in granule granularity, but arena can only know what its first
  // chunk did commit. So what it thinks was committed depends on the size of its first chunk,
  // which depends on ArenaGrowthPolicy.
  {
    const chunklevel_t expected_level_for_first_chunk =
        ArenaGrowthPolicy::policy_for_space_type(type, is_class)->get_level_at_step(0);
    const size_t what_arena_should_think_was_committed =
        MIN2(Settings::commit_granule_words(), word_size_for_level(expected_level_for_first_chunk));
    const size_t what_should_really_be_committed = Settings::commit_granule_words();

    ASSERT_EQ(committed, what_arena_should_think_was_committed);
    ASSERT_EQ(context.committed_words(), what_should_really_be_committed);
  }

  ///// subsequent allocations //

  DEBUG_ONLY(const uintx num_chunk_enlarged = metaspace::InternalStats::num_chunks_enlarged();)

  size_t words_allocated = 0;
  int num_allocated = 0;
  const size_t safety = MAX_CHUNK_WORD_SIZE * 1.2;
  size_t highest_capacity_jump = capacity;
  int num_capacity_jumps = 0;

  while (words_allocated < safety && num_capacity_jumps < 15) {

    // if we want to test growth with in-place chunk enlargement, leave MetaspaceArena
    // undisturbed; it will have all the place to grow. Otherwise allocate from a little
    // side arena to increase fragmentation.
    // (Note that this does not completely prevent in-place chunk enlargement but makes it
    //  rather improbable)
    if (!test_in_place_enlargement) {
      smhelper_harrasser.allocate_from_arena_with_tests_expect_success(alloc_words * 2);
    }

    smhelper.allocate_from_arena_with_tests_expect_success(alloc_words);
    HANDLE_FAILURE
    words_allocated += metaspace::get_raw_word_size_for_requested_word_size(alloc_words);
    num_allocated++;

    size_t used2 = 0, committed2 = 0, capacity2 = 0;

    smhelper.arena()->usage_numbers(&used2, &committed2, &capacity2);
    HANDLE_FAILURE

    // used should not grow larger than what we allocated, plus possible overhead.
    ASSERT_GE(used2, used);
    ASSERT_LE(used2, used + alloc_words * 2);
    ASSERT_LE(used2, words_allocated + 100);
    used = used2;

    // A jump in committed words should not be larger than commit granule size.
    // It can be smaller, since the current chunk of the MetaspaceArena may be
    // smaller than a commit granule.
    // (Note: unless root chunks are born fully committed)
    ASSERT_GE(committed2, used2);
    ASSERT_GE(committed2, committed);
    const size_t committed_jump = committed2 - committed;
    if (committed_jump > 0) {
      ASSERT_LE(committed_jump, Settings::commit_granule_words());
    }
    committed = committed2;

    // Capacity jumps: Test that arenas capacity does not grow too fast.
    ASSERT_GE(capacity2, committed2);
    ASSERT_GE(capacity2, capacity);
    const size_t capacity_jump = capacity2 - capacity;
    if (capacity_jump > 0) {
      LOG(">%zu->%zu(+%zu)", capacity, capacity2, capacity_jump)
      if (capacity_jump > highest_capacity_jump) {
        /* Disabled for now since this is rather shaky. The way it is tested makes it too dependent
         * on allocation history. Need to rethink this.
        ASSERT_LE(capacity_jump, highest_capacity_jump * 2);
        ASSERT_GE(capacity_jump, MIN_CHUNK_WORD_SIZE);
        ASSERT_LE(capacity_jump, MAX_CHUNK_WORD_SIZE);
        */
        highest_capacity_jump = capacity_jump;
      }
      num_capacity_jumps++;
    }

    capacity = capacity2;

  }

  // No FBL should exist, we did not deallocate
  ASSERT_EQ(smhelper.internal_access().fbl(), (FreeBlocks*)nullptr);
  ASSERT_EQ(smhelper_harrasser.internal_access().fbl(), (FreeBlocks*)nullptr);

  // After all this work, we should see an increase in number of chunk-in-place-enlargements
  //  (this especially is vulnerable to regression: the decisions of when to do in-place-enlargements are somewhat
  //   complicated, see MetaspaceArena::attempt_enlarge_current_chunk())
#ifdef ASSERT
  if (test_in_place_enlargement) {
    const uintx num_chunk_enlarged_2 = metaspace::InternalStats::num_chunks_enlarged();
    ASSERT_GT(num_chunk_enlarged_2, num_chunk_enlarged);
  }
#endif
}

// these numbers have to be in sync with arena policy numbers (see memory/metaspace/arenaGrowthPolicy.cpp)
TEST_VM(metaspace, MetaspaceArena_growth_anon_c_inplace) {
  test_controlled_growth(Metaspace::ClassMirrorHolderMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_anon_c_not_inplace) {
  test_controlled_growth(Metaspace::ClassMirrorHolderMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1K), false);
}

TEST_VM(metaspace, MetaspaceArena_growth_standard_c_inplace) {
  test_controlled_growth(Metaspace::StandardMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_2K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_standard_c_not_inplace) {
  test_controlled_growth(Metaspace::StandardMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_2K), false);
}

/* Disabled growth tests for BootMetaspaceType: there, the growth steps are too rare,
 * and too large, to make any reliable guess as toward chunks get enlarged in place.
TEST_VM(metaspace, MetaspaceArena_growth_boot_c_inplace) {
  test_controlled_growth(Metaspace::BootMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1M), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_boot_c_not_inplace) {
  test_controlled_growth(Metaspace::BootMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1M), false);
}
*/

TEST_VM(metaspace, MetaspaceArena_growth_anon_nc_inplace) {
  test_controlled_growth(Metaspace::ClassMirrorHolderMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_1K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_anon_nc_not_inplace) {
  test_controlled_growth(Metaspace::ClassMirrorHolderMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_1K), false);
}

TEST_VM(metaspace, MetaspaceArena_growth_standard_nc_inplace) {
  test_controlled_growth(Metaspace::StandardMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_4K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_standard_nc_not_inplace) {
  test_controlled_growth(Metaspace::StandardMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_4K), false);
}

/* Disabled growth tests for BootMetaspaceType: there, the growth steps are too rare,
 * and too large, to make any reliable guess as toward chunks get enlarged in place.
TEST_VM(metaspace, MetaspaceArena_growth_boot_nc_inplace) {
  test_controlled_growth(Metaspace::BootMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_4M), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_boot_nc_not_inplace) {
  test_controlled_growth(Metaspace::BootMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_4M), false);
}
*/

// Test that repeated allocation-deallocation cycles with the same block size
//  do not increase metaspace usage after the initial allocation (the deallocated
//  block should be reused by the next allocation).
static void test_repeatedly_allocate_and_deallocate(bool is_topmost) {
  // Test various sizes, including (important) the max. possible block size = 1 root chunk
  for (size_t blocksize = Metaspace::max_allocation_word_size();
       blocksize >= Metaspace::min_allocation_word_size; blocksize /= 2) {
    size_t used1 = 0, used2 = 0, committed1 = 0, committed2 = 0;
    MetaWord* p = nullptr, *p2 = nullptr;

    MetaspaceGtestContext context;
    MetaspaceArenaTestHelper helper(context, Metaspace::StandardMetaspaceType, false);

    // First allocation
    helper.allocate_from_arena_with_tests_expect_success(&p, blocksize);
    if (!is_topmost) {
      // another one on top, size does not matter.
      helper.allocate_from_arena_with_tests_expect_success(0x10);
      HANDLE_FAILURE
    }

    // Measure
    helper.usage_numbers_with_test(&used1, &committed1, nullptr);

    // Dealloc, alloc several times with the same size.
    for (int i = 0; i < 5; i ++) {
      helper.deallocate_with_tests(p, blocksize);
      helper.allocate_from_arena_with_tests_expect_success(&p2, blocksize);
      HANDLE_FAILURE
      // We should get the same pointer back.
      EXPECT_EQ(p2, p);
    }

    // Measure again
    helper.usage_numbers_with_test(&used2, &committed2, nullptr);
    EXPECT_EQ(used2, used1);
    EXPECT_EQ(committed1, committed2);
  }
}

TEST_VM(metaspace, MetaspaceArena_test_repeatedly_allocate_and_deallocate_top_allocation) {
  test_repeatedly_allocate_and_deallocate(true);
}

TEST_VM(metaspace, MetaspaceArena_test_repeatedly_allocate_and_deallocate_nontop_allocation) {
  test_repeatedly_allocate_and_deallocate(false);
}

static void test_random_aligned_allocation(size_t arena_alignment_words, SizeRange range) {
  // We let the arena use 4K chunks, unless the alloc size is larger.
  chunklevel_t level = CHUNK_LEVEL_4K;
  const ArenaGrowthPolicy policy (&level, 1);
  const size_t chunk_word_size = word_size_for_level(level);

  size_t expected_used = 0;

  MetaspaceGtestContext context;
  MetaspaceArenaTestHelper helper(context, &policy, arena_alignment_words);

  size_t last_alloc_size = 0;
  unsigned num_allocations = 0;

  const size_t max_used = MIN2(MAX2(chunk_word_size * 10, (range.highest() * 100)),
                               LP64_ONLY(64) NOT_LP64(16) * M); // word size!
  while (expected_used < max_used) {

    const int chunks_before = helper.get_number_of_chunks();

    MetaBlock result, wastage;
    size_t alloc_words = range.random_value();
    NOT_LP64(alloc_words = align_up(alloc_words, Metaspace::min_allocation_alignment_words));
    helper.allocate_from_arena_with_tests(alloc_words, result, wastage);

    ASSERT_TRUE(result.is_nonempty());
    ASSERT_TRUE(result.is_aligned_base(arena_alignment_words));
    ASSERT_EQ(result.word_size(), alloc_words);

    expected_used += alloc_words + wastage.word_size();
    const int chunks_now = helper.get_number_of_chunks();
    ASSERT_GE(chunks_now, chunks_before);
    ASSERT_LE(chunks_now, chunks_before + 1);

    // Estimate wastage:
    // Guessing at wastage is somewhat simple since we don't expect to ever use the fbl (we
    // don't deallocate). Therefore, wastage can only be caused by alignment gap or by
    // salvaging an old chunk before a new chunk is added.
    const bool expect_alignment_gap = !is_aligned(last_alloc_size, arena_alignment_words);
    const bool new_chunk_added = chunks_now > chunks_before;

    if (num_allocations == 0) {
      // expect no wastage if its the first allocation in the arena
      ASSERT_TRUE(wastage.is_empty());
    } else {
      if (expect_alignment_gap) {
        // expect wastage if the alignment requires it
        ASSERT_TRUE(wastage.is_nonempty());
      }
    }

    if (wastage.is_nonempty()) {
      // If we have wastage, we expect it to be either too small or unaligned. That would not be true
      // for wastage from the fbl, which could have any size; however, in this test we don't deallocate,
      // so we don't expect wastage from the fbl.
      if (wastage.is_aligned_base(arena_alignment_words)) {
        ASSERT_LT(wastage.word_size(), alloc_words);
      }
      if (new_chunk_added) {
        // chunk turnover: no more wastage than size of a commit granule, since we salvage the
        // committed remainder of the old chunk.
        ASSERT_LT(wastage.word_size(), Settings::commit_granule_words());
      } else {
        // No chunk turnover: no more wastage than what alignment requires.
        ASSERT_LT(wastage.word_size(), arena_alignment_words);
      }
    }

    // Check stats too
    size_t used, committed, reserved;
    helper.usage_numbers_with_test(&used, &committed, &reserved);
    ASSERT_EQ(used, expected_used);

    // No FBL should exist, we did not deallocate
    ASSERT_EQ(helper.internal_access().fbl(), (FreeBlocks*)nullptr);

    HANDLE_FAILURE

    last_alloc_size = alloc_words;
    num_allocations ++;
  }
  LOG("allocs: %u", num_allocations);
}

#define TEST_ARENA_WITH_ALIGNMENT_SMALL_RANGE(al)                              \
TEST_VM(metaspace, MetaspaceArena_test_random_small_aligned_allocation_##al) { \
  static const SizeRange range(Metaspace::min_allocation_word_size, 128);      \
  test_random_aligned_allocation(al, range);                                   \
}

#ifdef _LP64
TEST_ARENA_WITH_ALIGNMENT_SMALL_RANGE(1);
#endif
TEST_ARENA_WITH_ALIGNMENT_SMALL_RANGE(2);
TEST_ARENA_WITH_ALIGNMENT_SMALL_RANGE(8);
TEST_ARENA_WITH_ALIGNMENT_SMALL_RANGE(32);
TEST_ARENA_WITH_ALIGNMENT_SMALL_RANGE(128);
TEST_ARENA_WITH_ALIGNMENT_SMALL_RANGE(MIN_CHUNK_WORD_SIZE);

#define TEST_ARENA_WITH_ALIGNMENT_LARGE_RANGE(al)                              \
TEST_VM(metaspace, MetaspaceArena_test_random_large_aligned_allocation_##al) { \
  static const SizeRange range(Metaspace::max_allocation_word_size() / 2,      \
                                   Metaspace::max_allocation_word_size());     \
  test_random_aligned_allocation(al, range);                                   \
}

#ifdef _LP64
TEST_ARENA_WITH_ALIGNMENT_LARGE_RANGE(1);
#endif
TEST_ARENA_WITH_ALIGNMENT_LARGE_RANGE(2);
TEST_ARENA_WITH_ALIGNMENT_LARGE_RANGE(8);
TEST_ARENA_WITH_ALIGNMENT_LARGE_RANGE(32);
TEST_ARENA_WITH_ALIGNMENT_LARGE_RANGE(128);
TEST_ARENA_WITH_ALIGNMENT_LARGE_RANGE(MIN_CHUNK_WORD_SIZE);

} // namespace metaspace
