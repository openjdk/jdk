#include "precompiled.hpp"
#include "unittest.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"

// These tests will all be skipped (unless Shenandoah becomes the default
// collector). To execute these tests, you must enable Shenandoah, which
// is done with:
//
// % _JAVA_OPTIONS="-XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational" make exploded-test TEST="gtest:Shenandoah*"
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
      tty->print_cr("skipped");  \
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
    _heuristics = _heap->old_heuristics();
    _collection_set = _heap->collection_set();
    ShenandoahHeapLocker locker(_heap->lock());
    ShenandoahResetRegions reset;
    _heap->heap_region_iterate(&reset);
    _heap->set_old_evac_reserve(_heap->old_generation()->soft_max_capacity() / 4);
    _heuristics->abandon_collection_candidates();
    _collection_set->clear();
  }

  size_t make_garbage(size_t region_idx, size_t garbage_bytes) {
    ShenandoahHeapLocker locker(_heap->lock());
    ShenandoahHeapRegion* region = _heap->get_region(region_idx);
    region->make_regular_allocation(OLD_GENERATION);
    region->increase_live_data_alloc_words(1);
    region->set_top(region->bottom() + garbage_bytes / HeapWordSize);
    return region->garbage();
  }

  size_t create_too_much_garbage_for_one_mixed_evacuation() {
    size_t garbage_target = _heap->old_generation()->soft_max_capacity() / 2;
    size_t garbage_total = 0;
    size_t region_idx = 0;
    while (garbage_total < garbage_target && region_idx < _heap->num_regions()) {
      garbage_total += make_garbage_above_threshold(region_idx++);
    }
    return garbage_total;
  }

  void make_pinned(size_t region_idx) {
    ShenandoahHeapLocker locker(_heap->lock());
    ShenandoahHeapRegion* region = _heap->get_region(region_idx);
    region->record_pin();
    region->make_pinned();
  }

  void make_unpinned(size_t region_idx) {
    ShenandoahHeapLocker locker(_heap->lock());
    ShenandoahHeapRegion* region = _heap->get_region(region_idx);
    region->record_unpin();
    region->make_unpinned();
  }

  size_t make_garbage_below_threshold(size_t region_idx) {
    return make_garbage(region_idx, collection_threshold() - 100);
  }

  size_t make_garbage_above_threshold(size_t region_idx) {
    return make_garbage(region_idx, collection_threshold() + 100);
  }

  size_t collection_threshold() const {
    return ShenandoahHeapRegion::region_size_bytes() * ShenandoahOldGarbageThreshold / 100;
  }
};

TEST_VM_F(ShenandoahOldHeuristicTest, select_no_old_regions) {
  SKIP_IF_NOT_SHENANDOAH();

  _heuristics->prepare_for_old_collections();
  EXPECT_EQ(0U, _heuristics->last_old_region_index());
  EXPECT_EQ(0U, _heuristics->last_old_collection_candidate_index());
  EXPECT_EQ(0U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, select_no_old_region_above_threshold) {
  SKIP_IF_NOT_SHENANDOAH();

  // In this case, we have zero regions to add to the collection set,
  // but we will have one region that must still be made parseable.
  make_garbage_below_threshold(10);
  _heuristics->prepare_for_old_collections();
  EXPECT_EQ(1U, _heuristics->last_old_region_index());
  EXPECT_EQ(0U, _heuristics->last_old_collection_candidate_index());
  EXPECT_EQ(0U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, select_one_old_region_above_threshold) {
  SKIP_IF_NOT_SHENANDOAH();

  make_garbage_above_threshold(10);
  _heuristics->prepare_for_old_collections();
  EXPECT_EQ(1U, _heuristics->last_old_region_index());
  EXPECT_EQ(1U, _heuristics->last_old_collection_candidate_index());
  EXPECT_EQ(1U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, prime_one_old_region) {
  SKIP_IF_NOT_SHENANDOAH();

  size_t garbage = make_garbage_above_threshold(10);
  _heuristics->prepare_for_old_collections();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_EQ(garbage, _collection_set->get_old_garbage());
  EXPECT_EQ(0U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, prime_many_old_regions) {
  SKIP_IF_NOT_SHENANDOAH();

  size_t g1 = make_garbage_above_threshold(100);
  size_t g2 = make_garbage_above_threshold(101);
  _heuristics->prepare_for_old_collections();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_EQ(g1 + g2, _collection_set->get_old_garbage());
  EXPECT_EQ(0U, _heuristics->unprocessed_old_collection_candidates());
}

TEST_VM_F(ShenandoahOldHeuristicTest, require_multiple_mixed_evacuations) {
  SKIP_IF_NOT_SHENANDOAH();

  size_t garbage = create_too_much_garbage_for_one_mixed_evacuation();
  _heuristics->prepare_for_old_collections();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_LT(_collection_set->get_old_garbage(), garbage);
  EXPECT_GT(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

TEST_VM_F(ShenandoahOldHeuristicTest, skip_pinned_regions) {
  SKIP_IF_NOT_SHENANDOAH();

  // Create three old regions with enough garbage to be collected.
  size_t g1 = make_garbage_above_threshold(1);
  size_t g2 = make_garbage_above_threshold(2);
  size_t g3 = make_garbage_above_threshold(3);

  // A region can be pinned when we chose collection set candidates.
  make_pinned(2);
  _heuristics->prepare_for_old_collections();

  // We only excluded pinned regions when we actually add regions to the collection set.
  ASSERT_EQ(3UL, _heuristics->unprocessed_old_collection_candidates());

  // Here the region is still pinned, so it cannot be added to the collection set.
  _heuristics->prime_collection_set(_collection_set);

  // The two unpinned regions should be added to the collection set and the pinned
  // region should be retained at the front of the list of candidates as it would be
  // likely to become unpinned by the next mixed collection cycle.
  EXPECT_EQ(_collection_set->get_old_garbage(), g1 + g3);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 1UL);

  // Simulate another mixed collection after making region 2 unpinned. This time,
  // the now unpinned region should be added to the collection set.
  make_unpinned(2);
  _collection_set->clear();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_EQ(_collection_set->get_old_garbage(), g2);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

TEST_VM_F(ShenandoahOldHeuristicTest, pinned_region_is_first) {
  SKIP_IF_NOT_SHENANDOAH();

  // Create three old regions with enough garbage to be collected.
  size_t g1 = make_garbage_above_threshold(1);
  size_t g2 = make_garbage_above_threshold(2);
  size_t g3 = make_garbage_above_threshold(3);

  make_pinned(1);
  _heuristics->prepare_for_old_collections();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_EQ(_collection_set->get_old_garbage(), g2 + g3);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 1UL);

  make_unpinned(1);
  _collection_set->clear();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_EQ(_collection_set->get_old_garbage(), g1);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

TEST_VM_F(ShenandoahOldHeuristicTest, pinned_region_is_last) {
  SKIP_IF_NOT_SHENANDOAH();

  // Create three old regions with enough garbage to be collected.
  size_t g1 = make_garbage_above_threshold(1);
  size_t g2 = make_garbage_above_threshold(2);
  size_t g3 = make_garbage_above_threshold(3);

  make_pinned(3);
  _heuristics->prepare_for_old_collections();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_EQ(_collection_set->get_old_garbage(), g1 + g2);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 1UL);

  make_unpinned(3);
  _collection_set->clear();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_EQ(_collection_set->get_old_garbage(), g3);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

TEST_VM_F(ShenandoahOldHeuristicTest, unpinned_region_is_middle) {
  SKIP_IF_NOT_SHENANDOAH();

  // Create three old regions with enough garbage to be collected.
  size_t g1 = make_garbage_above_threshold(1);
  size_t g2 = make_garbage_above_threshold(2);
  size_t g3 = make_garbage_above_threshold(3);

  make_pinned(1);
  make_pinned(3);
  _heuristics->prepare_for_old_collections();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_EQ(_collection_set->get_old_garbage(), g2);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 2UL);

  make_unpinned(1);
  make_unpinned(3);
  _collection_set->clear();
  _heuristics->prime_collection_set(_collection_set);

  EXPECT_EQ(_collection_set->get_old_garbage(), g1 + g3);
  EXPECT_EQ(_heuristics->unprocessed_old_collection_candidates(), 0UL);
}

#undef SKIP_IF_NOT_SHENANDOAH