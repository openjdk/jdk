/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "unittest.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"
#include <cstdarg>

// These tests will all be skipped (unless Shenandoah becomes the default
// collector). To execute these tests, you must enable Shenandoah, which
// is done with:
//
// % make exploded-test TEST="gtest:ShenandoahOld*" CONF=release TEST_OPTS="JAVA_OPTIONS=-XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:ShenandoahGCMode=generational"
//
// Please note that these 'unit' tests are really integration tests and rely
// on the JVM being initialized. These tests manipulate the state of the
// collector in ways that are not compatible with a normal collection run.
// If these tests take longer than the minimum time between gc intervals -
// or, more likely, if you have them paused in a debugger longer than this
// interval - you can expect trouble. These tests will also not run in a build
// with asserts enabled because they use APIs that expect to run on a safepoint.
#ifdef ASSERT
#define SKIP_IF_NOT_SHENANDOAH()           \
  tty->print_cr("skipped (debug build)" ); \
  return;
#else
#define SKIP_IF_NOT_SHENANDOAH() \
    if (!UseShenandoahGC) {      \
      std::cout << "skipped\n";  \
      return;                    \
    }
#endif

class ShenandoahResetRegions : public ShenandoahHeapRegionClosure {
 public:
  virtual void heap_region_do(ShenandoahHeapRegion* region) override {
    if (!region->is_empty()) {
      region->make_trash();
      region->make_empty();
    }
    region->set_affiliation(FREE);
    region->clear_live_data();
    region->set_top(region->bottom());
  }
};

class ShenandoahOldHeuristicTest : public ::testing::Test {
 protected:
  ShenandoahHeap* _heap;
  ShenandoahOldHeuristics* _heuristics;
  ShenandoahCollectionSet* _collection_set;

  ShenandoahOldHeuristicTest()
    : _heap(nullptr),
      _heuristics(nullptr),
      _collection_set(nullptr) {
    SKIP_IF_NOT_SHENANDOAH();
    _heap = ShenandoahHeap::heap();
    _heuristics = _heap->old_generation()->heuristics();
    _collection_set = _heap->collection_set();
    _heap->lock()->lock(false);
    ShenandoahResetRegions reset;
    _heap->heap_region_iterate(&reset);
    // _heap->old_generation()->set_capacity(ShenandoahHeapRegion::region_size_bytes() * 10)
    _heap->free_set()->resize_old_collector_capacity(10);
    _heap->old_generation()->set_evacuation_reserve(ShenandoahHeapRegion::region_size_bytes() * 4);
    _heuristics->abandon_collection_candidates();
    _collection_set->clear();
  }

  ~ShenandoahOldHeuristicTest() override {
    SKIP_IF_NOT_SHENANDOAH();
    _heap->lock()->unlock();
  }

  ShenandoahOldGeneration::State old_generation_state() {
    return _heap->old_generation()->state();
  }

  size_t make_garbage(size_t region_idx, size_t garbage_bytes) {
    ShenandoahHeapRegion* region = _heap->get_region(region_idx);
    region->set_affiliation(OLD_GENERATION);
    region->make_regular_allocation(OLD_GENERATION);
    size_t live_bytes = ShenandoahHeapRegion::region_size_bytes() - garbage_bytes;
    region->increase_live_data_alloc_words(live_bytes / HeapWordSize);
    region->set_top(region->end());
    return region->garbage();
  }

  size_t create_too_much_garbage_for_one_mixed_evacuation() {
    size_t garbage_target = _heap->old_generation()->max_capacity() / 2;
    size_t garbage_total = 0;
    size_t region_idx = 0;
    while (garbage_total < garbage_target && region_idx < _heap->num_regions()) {
      garbage_total += make_garbage_above_collection_threshold(region_idx++);
    }
    return garbage_total;
  }

  void make_pinned(size_t region_idx) {
    ShenandoahHeapRegion* region = _heap->get_region(region_idx);
    region->record_pin();
    region->make_pinned();
  }

  void make_unpinned(size_t region_idx) {
    ShenandoahHeapRegion* region = _heap->get_region(region_idx);
    region->record_unpin();
    region->make_unpinned();
  }

  size_t make_garbage_below_collection_threshold(size_t region_idx) {
    return make_garbage(region_idx, collection_threshold() - 100);
  }

  size_t make_garbage_above_collection_threshold(size_t region_idx) {
    return make_garbage(region_idx, collection_threshold() + 100);
  }

  size_t collection_threshold() const {
    return ShenandoahHeapRegion::region_size_bytes() * ShenandoahOldGarbageThreshold / 100;
  }

  bool collection_set_is(size_t r1) { return _collection_set_is(1, r1); }
  bool collection_set_is(size_t r1, size_t r2) { return _collection_set_is(2, r1, r2); }
  bool collection_set_is(size_t r1, size_t r2, size_t r3) { return _collection_set_is(3, r1, r2, r3); }

  bool _collection_set_is(size_t count, ...) {
    va_list args;
    va_start(args, count);
    EXPECT_EQ(count, _collection_set->count());
    bool result = true;
    for (size_t i = 0; i < count; ++i) {
      size_t index = va_arg(args, size_t);
      if (!_collection_set->is_in(index)) {
        result = false;
        break;
      }
    }
    va_end(args);
    return result;
  }
};

TEST_VM_F(ShenandoahOldHeuristicTest, select_no_old_regions) {
  SKIP_IF_NOT_SHENANDOAH();

  _heuristics->prepare_for_old_collections();
  EXPECT_EQ(0U, _heuristics->coalesce_and_fill_candidates_count());
  EXPECT_EQ(0U, _heuristics->last_old_collection_candidate_index());
  EXPECT_EQ(0U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, select_no_old_region_above_threshold) {
  SKIP_IF_NOT_SHENANDOAH();

  // In this case, we have zero regions to add to the collection set,
  // but we will have one region that must still be made parseable.
  make_garbage_below_collection_threshold(10);
  _heuristics->prepare_for_old_collections();
  EXPECT_EQ(1U, _heuristics->coalesce_and_fill_candidates_count());
  EXPECT_EQ(0U, _heuristics->last_old_collection_candidate_index());
  EXPECT_EQ(0U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, select_one_old_region_above_threshold) {
  SKIP_IF_NOT_SHENANDOAH();

  make_garbage_above_collection_threshold(10);
  _heuristics->prepare_for_old_collections();
  EXPECT_EQ(1U, _heuristics->coalesce_and_fill_candidates_count());
  EXPECT_EQ(1U, _heuristics->last_old_collection_candidate_index());
  EXPECT_EQ(1U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, prime_one_old_region) {
  SKIP_IF_NOT_SHENANDOAH();

  size_t garbage = make_garbage_above_collection_threshold(10);
  _heuristics->prepare_for_old_collections();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_TRUE(collection_set_is(10UL));
  EXPECT_EQ(garbage, _collection_set->get_old_garbage());
  EXPECT_EQ(0U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, prime_many_old_regions) {
  SKIP_IF_NOT_SHENANDOAH();

  size_t g1 = make_garbage_above_collection_threshold(100);
  size_t g2 = make_garbage_above_collection_threshold(101);
  _heuristics->prepare_for_old_collections();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_TRUE(collection_set_is(100UL, 101UL));
  EXPECT_EQ(g1 + g2, _collection_set->get_old_garbage());
  EXPECT_EQ(0U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, require_multiple_mixed_evacuations) {
  SKIP_IF_NOT_SHENANDOAH();

  size_t garbage = create_too_much_garbage_for_one_mixed_evacuation();
  _heuristics->prepare_for_old_collections();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_LT(_collection_set->get_old_garbage(), garbage);
  EXPECT_GT(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

TEST_VM_F(ShenandoahOldHeuristicTest, skip_pinned_regions) {
  SKIP_IF_NOT_SHENANDOAH();

  // Create three old regions with enough garbage to be collected.
  size_t g1 = make_garbage_above_collection_threshold(0);
  size_t g2 = make_garbage_above_collection_threshold(1);
  size_t g3 = make_garbage_above_collection_threshold(2);

  // A region can be pinned when we chose collection set candidates.
  make_pinned(1);
  _heuristics->prepare_for_old_collections();

  // We only exclude pinned regions when we actually add regions to the collection set.
  ASSERT_EQ(3UL, _heuristics->unprocessed_old_collection_candidates());

  // Here the region is still pinned, so it cannot be added to the collection set.
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  // The two unpinned regions should be added to the collection set and the pinned
  // region should be retained at the front of the list of candidates as it would be
  // likely to become unpinned by the next mixed collection cycle.
  EXPECT_TRUE(collection_set_is(0UL, 2UL));
  EXPECT_EQ(_collection_set->get_old_garbage(), g1 + g3);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 1UL);

  // Simulate another mixed collection after making region 1 unpinned. This time,
  // the now unpinned region should be added to the collection set.
  make_unpinned(1);
  _collection_set->clear();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_EQ(_collection_set->get_old_garbage(), g2);
  EXPECT_TRUE(collection_set_is(1UL));
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

TEST_VM_F(ShenandoahOldHeuristicTest, pinned_region_is_first) {
  SKIP_IF_NOT_SHENANDOAH();

  // Create three old regions with enough garbage to be collected.
  size_t g1 = make_garbage_above_collection_threshold(0);
  size_t g2 = make_garbage_above_collection_threshold(1);
  size_t g3 = make_garbage_above_collection_threshold(2);

  make_pinned(0);
  _heuristics->prepare_for_old_collections();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_TRUE(collection_set_is(1UL, 2UL));
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 1UL);

  make_unpinned(0);
  _collection_set->clear();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_TRUE(collection_set_is(0UL));
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

TEST_VM_F(ShenandoahOldHeuristicTest, pinned_region_is_last) {
  SKIP_IF_NOT_SHENANDOAH();

  // Create three old regions with enough garbage to be collected.
  size_t g1 = make_garbage_above_collection_threshold(0);
  size_t g2 = make_garbage_above_collection_threshold(1);
  size_t g3 = make_garbage_above_collection_threshold(2);

  make_pinned(2);
  _heuristics->prepare_for_old_collections();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_TRUE(collection_set_is(0UL, 1UL));
  EXPECT_EQ(_collection_set->get_old_garbage(), g1 + g2);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 1UL);

  make_unpinned(2);
  _collection_set->clear();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_TRUE(collection_set_is(2UL));
  EXPECT_EQ(_collection_set->get_old_garbage(), g3);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

TEST_VM_F(ShenandoahOldHeuristicTest, unpinned_region_is_middle) {
  SKIP_IF_NOT_SHENANDOAH();

  // Create three old regions with enough garbage to be collected.
  size_t g1 = make_garbage_above_collection_threshold(0);
  size_t g2 = make_garbage_above_collection_threshold(1);
  size_t g3 = make_garbage_above_collection_threshold(2);

  make_pinned(0);
  make_pinned(2);
  _heuristics->prepare_for_old_collections();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_TRUE(collection_set_is(1UL));
  EXPECT_EQ(_collection_set->get_old_garbage(), g2);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 2UL);

  make_unpinned(0);
  make_unpinned(2);
  _collection_set->clear();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  EXPECT_TRUE(collection_set_is(0UL, 2UL));
  EXPECT_EQ(_collection_set->get_old_garbage(), g1 + g3);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

TEST_VM_F(ShenandoahOldHeuristicTest, all_candidates_are_pinned) {
  SKIP_IF_NOT_SHENANDOAH();

  size_t g1 = make_garbage_above_collection_threshold(0);
  size_t g2 = make_garbage_above_collection_threshold(1);
  size_t g3 = make_garbage_above_collection_threshold(2);

  make_pinned(0);
  make_pinned(1);
  make_pinned(2);
  _heuristics->prepare_for_old_collections();
  if (_heuristics->prime_collection_set(_collection_set)) {
    _heuristics->finalize_mixed_evacs();
  }

  // In the case when all candidates are pinned, we want to abandon
  // this set of mixed collection candidates so that another old collection
  // can run. This is meant to defend against "bad" JNI code that permanently
  // leaves an old region in the pinned state.
  EXPECT_EQ(_collection_set->count(), 0UL);
  EXPECT_EQ(old_generation_state(), ShenandoahOldGeneration::FILLING);
}
#undef SKIP_IF_NOT_SHENANDOAH
