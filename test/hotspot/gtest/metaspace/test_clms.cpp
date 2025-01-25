/*
 * Copyright (c) 2024 Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/classLoaderMetaspace.hpp"
#include "memory/metaspace/freeBlocks.hpp"
#include "memory/metaspace/metablock.inline.hpp"
#include "memory/metaspace/metaspaceArena.hpp"
#include "memory/metaspace/metaspaceSettings.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "memory/metaspace.hpp"
#include "oops/klass.hpp"
#include "runtime/mutex.hpp"
#include "utilities/debug.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef _LP64

#define LOG_PLEASE
#include "metaspaceGtestCommon.hpp"
#include "metaspaceGtestContexts.hpp"
#include "metaspaceGtestRangeHelpers.hpp"
#include "metaspaceGtestSparseArray.hpp"

#define HANDLE_FAILURE \
  if (testing::Test::HasFailure()) { \
    return; \
  }

namespace metaspace {

class ClmsTester {

  Mutex _lock;
  MetaspaceContext* _class_context;
  MetaspaceContext* _nonclass_context;
  ClassLoaderMetaspace* _clms;
  const size_t _klass_arena_alignment_words;
  unsigned _num_allocations;

  struct Deltas {
    int num_chunks_delta;
    ssize_t used_words_delta;
    int num_freeblocks_delta;
    ssize_t freeblocks_words_delta;
  };

  Deltas calc_deltas(const ArenaStats& before, const ArenaStats& after) {
    Deltas d;
    d.num_chunks_delta = after.totals()._num - before.totals()._num;
    d.used_words_delta = after.totals()._used_words - before.totals()._used_words;
    d.num_freeblocks_delta = (int)after._free_blocks_num - (int)before._free_blocks_num;
    d.freeblocks_words_delta = after._free_blocks_word_size - before._free_blocks_word_size;
    return d;
  }

public:

  ClmsTester(size_t klass_alignment_words, Metaspace::MetaspaceType space_type,
             MetaspaceContext* class_context, MetaspaceContext* nonclass_context)
  : _lock(Monitor::nosafepoint, "CLMSTest_lock"),
    _class_context(class_context), _nonclass_context(nonclass_context),
    _clms(nullptr), _klass_arena_alignment_words(klass_alignment_words), _num_allocations(0) {
    _clms = new ClassLoaderMetaspace(&_lock, space_type, nonclass_context, class_context, klass_alignment_words);
  }

  ~ClmsTester() {
    delete _clms;
    EXPECT_EQ(_class_context->used_words(), (size_t)0);
    EXPECT_EQ(_nonclass_context->used_words(), (size_t)0);
  }

  MetaBlock allocate_and_check(size_t word_size, bool is_class) {

    // take stats before allocation
    ClmsStats stats_before;
    _clms->add_to_statistics(&stats_before);

    // allocate
    MetaWord* p = _clms->allocate(word_size, is_class ? Metaspace::ClassType : Metaspace::NonClassType);
    _num_allocations ++;

    // take stats after allocation
    ClmsStats stats_after;
    _clms->add_to_statistics(&stats_after);

    // for less verbose testing:
    const ArenaStats& ca_before = stats_before._arena_stats_class;
    const ArenaStats& ca_after = stats_after._arena_stats_class;
    const ArenaStats& nca_before = stats_before._arena_stats_nonclass;
    const ArenaStats& nca_after = stats_after._arena_stats_nonclass;

    // deltas
    const Deltas d_ca = calc_deltas(ca_before, ca_after);
    const Deltas d_nca = calc_deltas(nca_before, nca_after);

#define EXPECT_FREEBLOCKS_UNCHANGED(arena_prefix) \
    EXPECT_EQ(d_##arena_prefix.num_freeblocks_delta, 0);  \
    EXPECT_EQ(d_##arena_prefix.freeblocks_words_delta, (ssize_t)0);

#define EXPECT_ARENA_UNCHANGED(arena_prefix) \
    EXPECT_EQ(d_##arena_prefix.num_chunks_delta, 0);  \
    EXPECT_EQ(d_##arena_prefix.used_words_delta, (ssize_t)0);

    if (p != nullptr) {

      MetaBlock bl(p, word_size);

      if (is_class) {

        EXPECT_TRUE(bl.is_aligned_base(_klass_arena_alignment_words));

        if (_num_allocations == 1) {
          // first allocation: nonclass arena unchanged, class arena grows by 1 chunk and wordsize,
          // class arena freeblocks unchanged
          EXPECT_ARENA_UNCHANGED(nca);
          EXPECT_FREEBLOCKS_UNCHANGED(nca);
          EXPECT_EQ(d_ca.num_chunks_delta, 1);
          EXPECT_EQ((size_t)d_ca.used_words_delta, word_size);
          EXPECT_FREEBLOCKS_UNCHANGED(ca);
          return bl;
        }

        // Had this been taken from class arena freeblocks?
        if (d_ca.num_freeblocks_delta == -1) {
          // the class arena freeblocks should have gone down, and the non-class arena freeblocks may have gone
          // up in case the block was larger than required
          const size_t wordsize_block_taken = (size_t)(-d_ca.freeblocks_words_delta);
          EXPECT_GE(wordsize_block_taken, word_size); // the block we took must be at least allocation size
          const size_t expected_freeblock_remainder = wordsize_block_taken - word_size;
          if (expected_freeblock_remainder > 0) {
            // the remainder, if it existed, should have been added to nonclass freeblocks
            EXPECT_EQ(d_nca.num_freeblocks_delta, 1);
            EXPECT_EQ((size_t)d_nca.freeblocks_words_delta, expected_freeblock_remainder);
          }
          // finally, nothing should have happened in the arenas proper.
          EXPECT_ARENA_UNCHANGED(ca);
          EXPECT_ARENA_UNCHANGED(nca);
          return bl;
        }

        // block was taken from class arena proper

        // We expect allocation waste due to alignment, should have been added to the freeblocks
        // of nonclass arena. Allocation waste can be 0. If no chunk turnover happened, it must be
        // smaller than klass alignment, otherwise it can get as large as a commit granule.
        const size_t max_expected_allocation_waste =
            d_ca.num_chunks_delta == 0 ? (_klass_arena_alignment_words - 1) : Settings::commit_granule_words();
        EXPECT_GE(d_ca.num_chunks_delta, 0);
        EXPECT_LE(d_ca.num_chunks_delta, 1);
        EXPECT_GE((size_t)d_ca.used_words_delta, word_size);
        EXPECT_LE((size_t)d_ca.used_words_delta, word_size + max_expected_allocation_waste);
        EXPECT_FREEBLOCKS_UNCHANGED(ca);
        EXPECT_ARENA_UNCHANGED(nca);
        if (max_expected_allocation_waste > 0) {
          EXPECT_GE(d_nca.num_freeblocks_delta, 0);
          EXPECT_LE(d_nca.num_freeblocks_delta, 1);
          EXPECT_GE(d_nca.freeblocks_words_delta, 0);
          EXPECT_LE((size_t)d_nca.freeblocks_words_delta, max_expected_allocation_waste);
        } else {
          EXPECT_FREEBLOCKS_UNCHANGED(nca);
        }
        return bl;
        // end: is_class
      } else {
        // Nonclass arena allocation.
        // Allocation waste can happen:
        // - if we allocate from nonclass freeblocks, the block remainder
        // - if we allocate from arena proper, by chunk turnover

        if (d_nca.freeblocks_words_delta < 0) {
          // We allocated a block from the nonclass arena freeblocks.
          const size_t wordsize_block_taken = (size_t)(-d_nca.freeblocks_words_delta);
          EXPECT_EQ(wordsize_block_taken, word_size);
          // The number of blocks may or may not have decreased (depending on whether there
          // was a wastage block)
          EXPECT_GE(d_nca.num_chunks_delta, -1);
          EXPECT_LE(d_nca.num_chunks_delta, 0);
          EXPECT_ARENA_UNCHANGED(nca);
          EXPECT_ARENA_UNCHANGED(ca);
          EXPECT_FREEBLOCKS_UNCHANGED(ca);
          return bl;
        }

        // We don't expect alignment waste. Only wastage happens at chunk turnover.
        const size_t max_expected_allocation_waste =
            d_nca.num_chunks_delta == 0 ? 0 : Settings::commit_granule_words();
        EXPECT_ARENA_UNCHANGED(ca);
        EXPECT_FREEBLOCKS_UNCHANGED(ca);
        EXPECT_GE(d_nca.num_chunks_delta, 0);
        EXPECT_LE(d_nca.num_chunks_delta, 1);
        EXPECT_GE((size_t)d_nca.used_words_delta, word_size);
        EXPECT_LE((size_t)d_nca.used_words_delta, word_size + max_expected_allocation_waste);
        if (max_expected_allocation_waste == 0) {
          EXPECT_FREEBLOCKS_UNCHANGED(nca);
        }
      }
      return bl;

    } // end: allocation successful

    // allocation failed.
    EXPECT_ARENA_UNCHANGED(ca);
    EXPECT_FREEBLOCKS_UNCHANGED(ca);
    EXPECT_ARENA_UNCHANGED(nca);
    EXPECT_FREEBLOCKS_UNCHANGED(nca);

    return MetaBlock();
  }

  MetaBlock allocate_expect_success(size_t word_size, bool is_class) {
    MetaBlock bl = allocate_and_check(word_size, is_class);
    EXPECT_TRUE(bl.is_nonempty());
    return bl;
  }

  MetaBlock allocate_expect_failure(size_t word_size, bool is_class) {
    MetaBlock bl = allocate_and_check(word_size, is_class);
    EXPECT_TRUE(bl.is_empty());
    return bl;
  }

  void deallocate_and_check(MetaBlock bl) {

    // take stats before deallocation
    ClmsStats stats_before;
    _clms->add_to_statistics(&stats_before);

    // allocate
    _clms->deallocate(bl.base(), bl.word_size());

    // take stats after deallocation
    ClmsStats stats_after;
    _clms->add_to_statistics(&stats_after);

    // for less verbose testing:
    const ArenaStats& ca_before = stats_before._arena_stats_class;
    const ArenaStats& ca_after = stats_after._arena_stats_class;
    const ArenaStats& nca_before = stats_before._arena_stats_nonclass;
    const ArenaStats& nca_after = stats_after._arena_stats_nonclass;

    // deltas
    // deltas
    const Deltas d_ca = calc_deltas(ca_before, ca_after);
    const Deltas d_nca = calc_deltas(nca_before, nca_after);

    EXPECT_ARENA_UNCHANGED(ca);
    EXPECT_ARENA_UNCHANGED(nca);
    // Depending on whether the returned block was suitable for Klass,
    // it may have gone to either the non-class freelist or the class freelist
    if (d_ca.num_freeblocks_delta == 1) {
      EXPECT_EQ(d_ca.num_freeblocks_delta, 1);
      EXPECT_EQ((size_t)d_ca.freeblocks_words_delta, bl.word_size());
      EXPECT_FREEBLOCKS_UNCHANGED(nca);
    } else {
      EXPECT_EQ(d_nca.num_freeblocks_delta, 1);
      EXPECT_EQ((size_t)d_nca.freeblocks_words_delta, bl.word_size());
      EXPECT_FREEBLOCKS_UNCHANGED(ca);
    }
  }
};

static constexpr size_t klass_size = sizeof(Klass) / BytesPerWord;

static void basic_test(size_t klass_arena_alignment) {
  MetaspaceGtestContext class_context, nonclass_context;
  {
    ClmsTester tester(klass_arena_alignment, Metaspace::StandardMetaspaceType, class_context.context(), nonclass_context.context());

    MetaBlock bl1 = tester.allocate_expect_success(klass_size, true);
    HANDLE_FAILURE;

    MetaBlock bl2 = tester.allocate_expect_success(klass_size, true);
    HANDLE_FAILURE;

    tester.deallocate_and_check(bl1);
    HANDLE_FAILURE;

    MetaBlock bl3 = tester.allocate_expect_success(klass_size, true);
    HANDLE_FAILURE;

    MetaBlock bl4 = tester.allocate_expect_success(Metaspace::min_allocation_word_size, false);
    HANDLE_FAILURE;

    MetaBlock bl5 = tester.allocate_expect_success(K, false);
    HANDLE_FAILURE;

    tester.deallocate_and_check(bl5);
    HANDLE_FAILURE;

    MetaBlock bl6 = tester.allocate_expect_success(K, false);
    HANDLE_FAILURE;

    EXPECT_EQ(bl5, bl6); // should have gotten the same block back from freelist
  }
  EXPECT_EQ(class_context.used_words(), (size_t)0);
  EXPECT_EQ(nonclass_context.used_words(), (size_t)0);
  // we should have used exactly one commit granule (64K), not more, for each context
  EXPECT_EQ(class_context.committed_words(), Settings::commit_granule_words());
  EXPECT_EQ(nonclass_context.committed_words(), Settings::commit_granule_words());
}

#define TEST_BASIC_N(n)               \
TEST_VM(metaspace, CLMS_basics_##n) { \
  basic_test(n);            \
}

TEST_BASIC_N(1)
TEST_BASIC_N(4)
TEST_BASIC_N(16)
TEST_BASIC_N(32)
TEST_BASIC_N(128)

static void test_random(size_t klass_arena_alignment) {
  MetaspaceGtestContext class_context, nonclass_context;
  constexpr int max_allocations = 1024;
  const SizeRange nonclass_alloc_range(Metaspace::min_allocation_alignment_words, 1024);
  const SizeRange class_alloc_range(klass_size, 1024);
  const IntRange one_out_of_ten(0, 10);
  for (int runs = 9; runs >= 0; runs--) {
    {
      ClmsTester tester(64, Metaspace::StandardMetaspaceType, class_context.context(), nonclass_context.context());
      struct LifeBlock {
        MetaBlock bl;
        bool is_class;
      };
      LifeBlock life_allocations[max_allocations];
      for (int i = 0; i < max_allocations; i++) {
        life_allocations[i].bl.reset();
      }

      unsigned num_class_allocs = 0, num_nonclass_allocs = 0, num_class_deallocs = 0, num_nonclass_deallocs = 0;
      for (int i = 0; i < 5000; i ++) {
        const int slot = IntRange(0, max_allocations).random_value();
        if (life_allocations[slot].bl.is_empty()) {
          const bool is_class = one_out_of_ten.random_value() == 0;
          const size_t word_size =
              is_class ? class_alloc_range.random_value() : nonclass_alloc_range.random_value();
          MetaBlock bl = tester.allocate_expect_success(word_size, is_class);
          HANDLE_FAILURE;
          life_allocations[slot].bl = bl;
          life_allocations[slot].is_class = is_class;
          if (is_class) {
            num_class_allocs ++;
          } else {
            num_nonclass_allocs ++;
          }
        } else {
          tester.deallocate_and_check(life_allocations[slot].bl);
          HANDLE_FAILURE;
          life_allocations[slot].bl.reset();
          if (life_allocations[slot].is_class) {
            num_class_deallocs ++;
          } else {
            num_nonclass_deallocs ++;
          }
        }
      }
      LOG("num class allocs: %u, num nonclass allocs: %u, num class deallocs: %u, num nonclass deallocs: %u",
          num_class_allocs, num_nonclass_allocs, num_class_deallocs, num_nonclass_deallocs);
    }
    EXPECT_EQ(class_context.used_words(), (size_t)0);
    EXPECT_EQ(nonclass_context.used_words(), (size_t)0);
    constexpr float fragmentation_factor = 3.0f;
    const size_t max_expected_nonclass_committed = max_allocations * nonclass_alloc_range.highest() * fragmentation_factor;
    const size_t max_expected_class_committed = max_allocations * class_alloc_range.highest() * fragmentation_factor;
    // we should have used exactly one commit granule (64K), not more, for each context
    EXPECT_LT(class_context.committed_words(), max_expected_class_committed);
    EXPECT_LT(nonclass_context.committed_words(), max_expected_nonclass_committed);
  }
}

#define TEST_RANDOM_N(n)               \
TEST_VM(metaspace, CLMS_random_##n) {  \
  test_random(n);                      \
}

TEST_RANDOM_N(1)
TEST_RANDOM_N(4)
TEST_RANDOM_N(16)
TEST_RANDOM_N(32)
TEST_RANDOM_N(128)

} // namespace metaspace

#endif // _LP64
