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
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"

#define SKIP_IF_NOT_SHENANDOAH() \
  if (!(UseShenandoahGC && ShenandoahHeap::heap()->mode()->is_generational())) {                 \
    tty->print_cr("skipped (run with -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational)");  \
    return;                                                                                      \
  }


class ShenandoahOldGenerationTest : public ::testing::Test {
protected:
  static const size_t INITIAL_PLAB_SIZE;
  static const size_t INITIAL_PLAB_PROMOTED;

  ShenandoahOldGeneration* old;

  ShenandoahOldGenerationTest()
    : old(nullptr)
  {
  }

  void SetUp() override {
    SKIP_IF_NOT_SHENANDOAH();

    ShenandoahHeap::heap()->lock()->lock(false);

    old = new ShenandoahOldGeneration(8, 1024 * 1024);
    old->set_promoted_reserve(512 * HeapWordSize);
    old->expend_promoted(256 * HeapWordSize);
    old->set_evacuation_reserve(512 * HeapWordSize);

    Thread* thread = Thread::current();
    ShenandoahThreadLocalData::reset_plab_promoted(thread);
    ShenandoahThreadLocalData::disable_plab_promotions(thread);
    ShenandoahThreadLocalData::set_plab_actual_size(thread, INITIAL_PLAB_SIZE);
    ShenandoahThreadLocalData::add_to_plab_promoted(thread, INITIAL_PLAB_PROMOTED);
  }

  void TearDown() override {
    if (UseShenandoahGC) {
      ShenandoahHeap::heap()->lock()->unlock();
      delete old;
    }
  }

  static bool promotions_enabled() {
    return ShenandoahThreadLocalData::allow_plab_promotions(Thread::current());
  }

  static size_t plab_size() {
    return ShenandoahThreadLocalData::get_plab_actual_size(Thread::current());
  }

  static size_t plab_promoted() {
    return ShenandoahThreadLocalData::get_plab_promoted(Thread::current());
  }
};

const size_t ShenandoahOldGenerationTest::INITIAL_PLAB_SIZE = 42;
const size_t ShenandoahOldGenerationTest::INITIAL_PLAB_PROMOTED = 128;

TEST_VM_F(ShenandoahOldGenerationTest, test_can_promote) {
  SKIP_IF_NOT_SHENANDOAH();
  EXPECT_TRUE(old->can_promote(128 * HeapWordSize)) << "Should have room to promote";
  EXPECT_FALSE(old->can_promote(384 * HeapWordSize)) << "Should not have room to promote";
}

TEST_VM_F(ShenandoahOldGenerationTest, test_can_allocate_plab_for_promotion) {
  SKIP_IF_NOT_SHENANDOAH();
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_plab(128, 128);
  EXPECT_TRUE(old->can_allocate(req)) << "Should have room to promote";
}

TEST_VM_F(ShenandoahOldGenerationTest, test_can_allocate_plab_for_evacuation) {
  SKIP_IF_NOT_SHENANDOAH();
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_plab(384, 384);
  EXPECT_FALSE(old->can_promote(req.size() * HeapWordSize)) << "No room for promotions";
  EXPECT_TRUE(old->can_allocate(req)) << "Should have room to evacuate";
}

TEST_VM_F(ShenandoahOldGenerationTest, test_cannot_allocate_plab) {
  SKIP_IF_NOT_SHENANDOAH();
  // Simulate having exhausted the evacuation reserve when request is too big to be promoted
  old->set_evacuation_reserve(0);
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_plab(384, 384);
  EXPECT_FALSE(old->can_allocate(req)) << "No room for promotions or evacuations";
}

TEST_VM_F(ShenandoahOldGenerationTest, test_can_allocate_for_shared_evacuation) {
  SKIP_IF_NOT_SHENANDOAH();
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_shared_gc(768, ShenandoahAffiliation::OLD_GENERATION, false);
  EXPECT_FALSE(old->can_promote(req.size() * HeapWordSize)) << "No room for promotion";
  EXPECT_TRUE(old->can_allocate(req)) << "Should have room to evacuate shared (even though evacuation reserve is smaller than request)";
}

TEST_VM_F(ShenandoahOldGenerationTest, test_cannot_allocate_for_shared_promotion) {
  SKIP_IF_NOT_SHENANDOAH();
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_shared_gc(768, ShenandoahAffiliation::OLD_GENERATION, true);
  EXPECT_FALSE(old->can_promote(req.size() * HeapWordSize)) << "No room for promotion";
  EXPECT_FALSE(old->can_allocate(req)) << "No room to promote, should fall back to evacuation in young gen";
}

TEST_VM_F(ShenandoahOldGenerationTest, test_expend_promoted) {
  SKIP_IF_NOT_SHENANDOAH();
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_plab(128, 128);

  // simulate the allocation
  req.set_actual_size(128);

  size_t actual_size = req.actual_size() * HeapWordSize;
  EXPECT_TRUE(old->can_promote(actual_size)) << "Should have room for promotion";

  size_t expended_before = old->get_promoted_expended();
  old->configure_plab_for_current_thread(req);
  size_t expended_after = old->get_promoted_expended();
  EXPECT_EQ(expended_before + actual_size, expended_after) << "Should expend promotion reserve";
  EXPECT_EQ(plab_promoted(), 0UL) << "Nothing promoted yet";
  EXPECT_EQ(plab_size(), actual_size) << "New plab should be able to hold this much promotion";
  EXPECT_TRUE(promotions_enabled()) << "Plab should be available for promotions";
}

TEST_VM_F(ShenandoahOldGenerationTest, test_actual_size_exceeds_promotion_reserve) {
  SKIP_IF_NOT_SHENANDOAH();
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_plab(128, 128);

  // simulate an allocation that exceeds the promotion reserve after allocation
  req.set_actual_size(384);
  EXPECT_FALSE(old->can_promote(req.actual_size() * HeapWordSize)) << "Should have room for promotion";

  size_t expended_before = old->get_promoted_expended();
  old->configure_plab_for_current_thread(req);
  size_t expended_after = old->get_promoted_expended();

  EXPECT_EQ(expended_before, expended_after) << "Did not promote, should not expend promotion";
  EXPECT_EQ(plab_promoted(), 0UL) << "Cannot promote in new plab";
  EXPECT_EQ(plab_size(), 0UL) << "Should not have space for promotions";
  EXPECT_FALSE(promotions_enabled()) << "New plab can only be used for evacuations";
}

TEST_VM_F(ShenandoahOldGenerationTest, test_shared_expends_promoted_but_does_not_change_plab) {
  SKIP_IF_NOT_SHENANDOAH();
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_shared_gc(128, ShenandoahAffiliation::OLD_GENERATION, true);
  req.set_actual_size(128);
  size_t actual_size = req.actual_size() * HeapWordSize;

  size_t expended_before = old->get_promoted_expended();
  old->configure_plab_for_current_thread(req);
  size_t expended_after = old->get_promoted_expended();

  EXPECT_EQ(expended_before + actual_size, expended_after) << "Shared promotion still expends promotion";
  EXPECT_EQ(plab_promoted(), INITIAL_PLAB_PROMOTED) << "Shared promotion should not count in plab";
  EXPECT_EQ(plab_size(), INITIAL_PLAB_SIZE) << "Shared promotion should not change size of plab";
  EXPECT_FALSE(promotions_enabled());
}

TEST_VM_F(ShenandoahOldGenerationTest, test_shared_evacuation_has_no_side_effects) {
  SKIP_IF_NOT_SHENANDOAH();
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_shared_gc(128, ShenandoahAffiliation::OLD_GENERATION, false);
  req.set_actual_size(128);

  size_t expended_before = old->get_promoted_expended();
  old->configure_plab_for_current_thread(req);
  size_t expended_after = old->get_promoted_expended();

  EXPECT_EQ(expended_before, expended_after) << "Not a promotion, should not expend promotion reserve";
  EXPECT_EQ(plab_promoted(), INITIAL_PLAB_PROMOTED) << "Not a plab, should not have touched plab";
  EXPECT_EQ(plab_size(), INITIAL_PLAB_SIZE) << "Not a plab, should not have touched plab";
  EXPECT_FALSE(promotions_enabled());
}

#undef SKIP_IF_NOT_SHENANDOAH
