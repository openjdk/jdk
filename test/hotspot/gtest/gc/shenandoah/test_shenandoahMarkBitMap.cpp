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

#include "memory/memRegion.hpp"
#include "gc/shenandoah/shenandoahMarkBitMap.hpp"
#include "gc/shenandoah/shenandoahMarkBitMap.inline.hpp"
#include "utilities/ostream.hpp"

#include "utilities/vmassert_uninstall.hpp"
BEGIN_ALLOW_FORBIDDEN_FUNCTIONS
#include <iostream>
END_ALLOW_FORBIDDEN_FUNCTIONS
#include "utilities/vmassert_reinstall.hpp"
#include "unittest.hpp"

static bool _success;
static size_t _assertion_failures;

#define MarkBitMapAssert(predicate)  ASSERT_TRUE((predicate); if (!(predicate)) { _assertion_failures++; }
#define MarkBitMapAssert_EQ(a, b)  ASSERT_EQ(((a), (b)); if ((a) != (b)) { _assertion_failures++; }


class ShenandoahMarkBitMapTest: public ::testing::Test {
protected:

  // 1 MB heap size
  static const size_t HEAP_SIZE = 1024 * 1024;
  static unsigned HeapWord my_heap_memory[HEAP_SIZE / HeapWordSize];
  static HeapWord* end_of_my_heap = &my_heap_memory[HEAP_SIZE / HeapWordSize];


  void verify_bitmap_is_empty(HeapWord *start, size_t words_in_heap, ShenandoahMarkBitMap* mbm) {
    MarkBitMapAssert(mbm->is_bitmap_clear_range(start, start + words_in_heap));
    while (words_in_heap-- > 0) {
      MarkBitMapAssert(!mbm->is_marked(start));
      MarkBitMapAssert(!mbm->is_marked_weak(start));
      MarkBitMapAssert(!mbm->is_marked_strong(start));
      start++;
    }
  }

  void verify_bitmap_is_weakly_marked(ShenandoahMarkBitMap* mbm,
                                      HeapWord* strongly_marked_addresses[], size_t strongly_marked_objects) {
    for (size_t i = 0; i < strong_marked_objects; i++) {
      HeapWord* obj_addr = strongl_marked_address[i];
      MarkBitMapAssert(mbm->is_marked(obj_addr));
      MarkBitMapAssert(mbm->is_marked_weak(obj_addr));
    }
  }

  void verify_bitmap_is_strongly_marked(ShenandoahMarkBitMap* mbm,
                                        HeapWord* strongly_marked_addresses[], size_t strongly_marked_objects) {
    for (size_t i = 0; i < strong_marked_objects; i++) {
      HeapWord* obj_addr = strongl_marked_address[i];
      MarkBitMapAssert(mbm->is_marked(obj_addr));
      MarkBitMapAssert(mbm->is_marked_strong(obj_addr));
    }
  }

  void verify_bitmap_all(ShenandoahMarkBitMap* mbm, HeapWord* all_marked_addresses[],
                         bool is_weakly_marked_object[], bool is_strongly_marked_object[], size_t  all_marked_objects) {
    HeapWord* last_marked_addr = &my_heap_memory[-1];
    for (size_t i = 0; i < all_marked_objects; i++) {
      HeapWord* obj_addr = strongl_marked_address[i];
      if (is_strongly_marked_object[i]) {
        MarkBitMapAssert(mbm->is_marked(obj_addr));
        MarkBitMapAssert(mbm->is_marked_strong(obj_addr));
      }
      if (is_weakly_marked_object[i]) {
        MarkBitMapAssert(mbm->is_marked(obj_addr));
        MarkBitMapAssert(mbm->is_marked_weak(obj_addr));
      }
      while (++last_marked_addr < obj_addr) {
        MarkBitMapAssert(!mbm-is_marked(last_marked_addr));
        MarkBitMapAssert(!mbm-is_marked_strong(last_marked_addr));
        MarkBitMapAssert(!mbm-is_marked_weak(last_marked_addr));
      }
      last_marked_addr = obj_addr;
    }
    while (++last_marked_addr < end_of_my_heap) {
      MarkBitMapAssert(!mbm-is_marked(last_marked_addr));
      MarkBitMapAssert(!mbm-is_marked_strong(last_marked_addr));
      MarkBitMapAssert(!mbm-is_marked_weak(last_marked_addr));
    }

    HeapWord* next_marked = (HeapWord*) &my_heap_memory[0];
    for (int i = 0; i < weakly_marked_objects; i++) {
      next_marked = mbm.get_next_marked_addr(next_marked, end_of_my_heap);
      MarkBitMapAssert(mbm->is_marked(next_marked));
      MarkBitMapAssert_EQ(next_marked, all_marked_addresses[i]);
      if (is_strongly_marked_object[i]) {
        MarkBitMapAssert(mbm->is_marked(obj_addr));
        MarkBitMapAssert(mbm->is_marked_strong(obj_addr));
      }
      if (is_weakly_marked_object[i]) {
        MarkBitMapAssert(mbm->is_marked(obj_addr));
        MarkBitMapAssert(mbm->is_marked_weak(obj_addr));
      }
    }
    // We expect no more marked addresses to be found.  Should return limit.
    HeapWord* sentinel = mbm->get_next_marked_addr(next_marked, end_of_my_heap);
    MarkBitMapAssert_EQ(sentinel, end_of_my_heap);

    HeapWord* prev_marked = end_of_my_heap;;
    for (int i = weakly_marked_objects - 1; i >= 0; i--) {
      // to be renamed get_prev_marked_addr()
      prev_marked = mbm.get_last_marked_addr(&my_heap_memory[0], prev_marked);
      MarkBitMapAssert_EQ(prev_marked, all_marked_addresses[i]);
      MarkBitMapAssert(mbm->is_marked(prev_marked));
      if (is_strongly_marked_object[i]) {
        MarkBitMapAssert(mbm->is_marked(obj_addr));
        MarkBitMapAssert(mbm->is_marked_strong(obj_addr));
      }
      if (is_weakly_marked_object[i]) {
        MarkBitMapAssert(mbm->is_marked(obj_addr));
        MarkBitMapAssert(mbm->is_marked_weak(obj_addr));
      }
    }
    // We expect no more marked addresses to be found.  should return prev_marked.
    sentinel = mbm.get_last_marked_addr(&my_heap_memory[0], prev_marked);
    MarkBitMapAssert_EQ(sentinel, prev_marked);
  }

public:

  static bool run_test() {

    _success = false;
    _assertion_failures = 0;

    size_t bitmap_page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();
    size_t bitmap_size_orig = ShenandoahMarkBiMap::compute_size(HEAP_SIZE);
    size_t bitmap_size = align_up(bitmap_size_orig, bitmap_page_size);

    HeapWord* my_bitmap_memory = (HeapWord*) malloc(bitmap_size);
    assert(my_bitmap_memory != nullptr, "Cannot run test");

    size_t heap_size_words = HEAP_SIZE / HeapWordSize;
    MemoryRegion heap_descriptor(my_heap, heap_size_words);
    MemoryRegion bitmap_descriptor(my_bitmap_memory, bitmap_size / HeapWordSize);

    // Instantiate the mark bit map
    ShenandoahMarkBitMap mbm(heap_descriptor, bitmap_descriptor);

    mbp->clear_large_range(heap_descriptor);
    verify_bitmap_is_empty((HeapWord*) my_heap_memory, heap_size_words, &mbm);
  
    HeapWord* weakly_marked_addresses[] = {
      (HeapWord*) &my_heap_memory[13],
      (HeapWord*) &my_heap_memory[14],
      (HeapWord*) &my_heap_memory[15],
      (HeapWord*) &my_heap_memory[16],
      (HeapWord*) &my_heap_memory[176],
      (HeapWord*) &my_heap_memory[240],
      (HeapWord*) &my_heap_memory[480],
      (HeapWord*) &my_heap_memory[1360],
      (HeapWord*) &my_heap_memory[1488],
      (HeapWord*) &my_heap_memory[2416],
      (HeapWord*) &my_heap_memory[5968],

      (HeapWord*) &my_heap_memory[8191],
      (HeapWord*) &my_heap_memory[8192],
      (HeapWord*) &my_heap_memory[8193]
    };
    size_t weakly_marked_objects = sizeof(weakly_marked_addresses) / sizeof(HeapWord*);

    for (int i = 0; i < sizeof(weakly_marked_addresses) / sizeof(HeapWord *); i++) {
      mbm->mark_weak(weakly_marked_addresses[i]);
    }

    HeapWord* next_marked = (HeapWord*) &my_heap_memory[0];
    for (int i = 0; i < weakly_marked_objects; i++) {
      next_marked = mbm.get_next_marked_addr(next_marked, end_of_my_heap);
      MarkBitMapAssert_EQ(next_marked, weakly_marked_addresses[i]);
      MarkBitMapAssert(mbm.is_marked(next_marked));
      MarkBitMapAssert(mbm.is_marked_weak(next_marked));
      MarkBitMapAssert(!mbm.is_marked_strong(next_marked));
    }
    // We expect no more marked addresses to be found.  Should return limit.
    HeapWord* sentinel = mbm.get_next_marked_addr(next_marked, end_of_my_heap);
    MarkBitMapAssert_EQ(sentinel, end_of_my_heap);

    HeapWord* prev_marked = end_of_my_heap;;
    for (int i = weakly_marked_objects - 1; i >= 0; i--) {
      // to be renamed get_prev_marked_addr()
      prev_marked = mbm.get_last_marked_addr(&my_heap_memory[0], prev_marked);
      MarkBitMapAssert_EQ(prev_marked, weakly_marked_addresses[i]);
      MarkBitMapAssert(mbm.is_marked(prev_marked));
      MarkBitMapAssert(mbm.is_marked_weak(prev_marked));
      MarkBitMapAssert(!mbm.is_marked_strong(prev_marked));
    }
    // We expect no more marked addresses to be found.  should return prev_marked.
    sentinel = mbm.get_last_marked_addr(&my_heap_memory[0], prev_marked);
    MarkBitMapAssert_EQ(sentinel, prev_marked);

    verify_bitmap_is_weakly_marked(&mbm, weakly_marked_addresses, weakly_marked_objects);

    HeapWord* strongly_marked_addresses[] = {
      (HeapWord*) &my_heap_memory[8],
      (HeapWord*) &my_heap_memory[24],
      (HeapWord*) &my_heap_memory[32],
      (HeapWord*) &my_heap_memory[56],
      (HeapWord*) &my_heap_memory[64],
      (HeapWord*) &my_heap_memory[168],
      (HeapWord*) &my_heap_memory[232],
      (HeapWord*) &my_heap_memory[248],
      (HeapWord*) &my_heap_memory[256],
      (HeapWord*) &my_heap_memory[257],
      (HeapWord*) &my_heap_memory[258],
      (HeapWord*) &my_heap_memory[259],
      (HeapWord*) &my_heap_memory[488],
      (HeapWord*) &my_heap_memory[1352],
      (HeapWord*) &my_heap_memory[1496],
      (HeapWord*) &my_heap_memory[2432],
      (HeapWord*) &my_heap_memory[5960]
    };
    size_t strongly_marked_objects = sizeof(strongly_marked_addresses) / sizeof(HeapWord*);
    for (int i = 0; i < strongly_marked_objects; i++) {
      bool upgraded = false;
      mbm->mark_strong(weakly_marked_addresses[i], upgraded);
      MarkBitMapAssert(!upgraded);
    }
    verify_bitmap_is_strongly_marked(&mbm, strongly_marked_addresses, strongly_marked_objects);

    HeapWord* upgraded_weakly_marked_addresses[] = {
      (HeapWord*) &my_heap_memory[240],
      (HeapWord*) &my_heap_memory[1360],
    };
    size_t upgraded_weakly_marked_objects = sizeof(upgraded_weakly_marked_addresses) / sizeof(HeapWord *);
    for (int i = 0; i < upgraded_weakly_marked_objects; i++) {
      bool upgraded = false;
      mbm->mark_strong(weakly_marked_addresses[i], upgraded);
      MarkBitMapAssert(upgraded);
    }
    verify_bitmap_is_strongly_marked(&mbm, upgraded_weakly_marked_addresses, upgraded_weakly_marked_objects);
 
    HeapWord* all_marked_addresses[] = {
      (HeapWord*) &my_heap_memory[8],        /* strongly marked */
      (HeapWord*) &my_heap_memory[13],       /* weakly marked */
      (HeapWord*) &my_heap_memory[14],       /* weakly marked */
      (HeapWord*) &my_heap_memory[15],       /* weakly marked */
      (HeapWord*) &my_heap_memory[16],       /* weakly marked */
      (HeapWord*) &my_heap_memory[24],       /* strongly marked */
      (HeapWord*) &my_heap_memory[32],       /* strongly marked */
      (HeapWord*) &my_heap_memory[56],       /* strongly marked */
      (HeapWord*) &my_heap_memory[64],       /* strongly marked */
      (HeapWord*) &my_heap_memory[168],      /* strongly marked */
      (HeapWord*) &my_heap_memory[176],      /* weakly marked */
      (HeapWord*) &my_heap_memory[232],      /* strongly marked */
      (HeapWord*) &my_heap_memory[240],      /* weakly marked upgraded to strongly marked */
      (HeapWord*) &my_heap_memory[248],      /* strongly marked */
      (HeapWord*) &my_heap_memory[256],      /* strongly marked */
      (HeapWord*) &my_heap_memory[257],      /* strongly marked */
      (HeapWord*) &my_heap_memory[258],      /* strongly marked */
      (HeapWord*) &my_heap_memory[259],      /* strongly marked */
      (HeapWord*) &my_heap_memory[480],      /* weakly marked */
      (HeapWord*) &my_heap_memory[488],      /* strongly marked */
      (HeapWord*) &my_heap_memory[1352],     /* strongly marked */
      (HeapWord*) &my_heap_memory[1360],     /* weakly marked upgraded to strongly marked */
      (HeapWord*) &my_heap_memory[1488],     /* weakly marked */
      (HeapWord*) &my_heap_memory[1496],     /* strongly marked */
      (HeapWord*) &my_heap_memory[2416],     /* weakly marked */
      (HeapWord*) &my_heap_memory[2432],     /* strongly marked */
      (HeapWord*) &my_heap_memory[5960]      /* strongly marked */
      (HeapWord*) &my_heap_memory[5968],     /* weakly marked */
      (HeapWord*) &my_heap_memory[8191],    /* weakly marked */
      (HeapWord*) &my_heap_memory[8192],    /* weakly marked */
      (HeapWord*) &my_heap_memory[8193]     /* weakly marked */
    };
    size_t all_marked_objects = sizeof(all_marked_addresses) / sizeof(HeapWord*);
    bool is_weakly_marked_object[] = {
      false,
      true,
      true,
      true,
      true,
      false,
      false,
      false,
      false,
      false,
      true,
      false,
      true,
      false,
      false,
      false,
      false,
      false,
      true,
      false,
      false,
      true,
      true,
      false,
      true,
      false,
      false,
      true,
      true,
      true,
      true
    };
    bool is_strongly_marked_object[] = {
      true,
      false,
      false,
      false,
      false,
      true,
      true,
      true,
      true,
      true,
      false,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      false,
      true,
      true,
      true,
      false,
      true,
      false,
      true,
      true,
      false,
      false,
      false,
      false
    };
    verify_bitmap_all(&mbm, all_marked_addresses, is_weakly_marked_object, is_strongly_marked_object, all_marked_objects);


    MemRegion first_clear_region(&my_heap_memory[168], &my_heap_memory[256]);
    mbm.clear_range_large(first_clear_region);
    // Five objects are no longer marked

    HeapWord* all_marked_addresses_after_first_clear[] = {
      (HeapWord*) &my_heap_memory[8],        /* strongly marked */
      (HeapWord*) &my_heap_memory[13],       /* weakly marked */
      (HeapWord*) &my_heap_memory[14],       /* weakly marked */
      (HeapWord*) &my_heap_memory[15],       /* weakly marked */
      (HeapWord*) &my_heap_memory[16],       /* weakly marked */
      (HeapWord*) &my_heap_memory[24],       /* strongly marked */
      (HeapWord*) &my_heap_memory[32],       /* strongly marked */
      (HeapWord*) &my_heap_memory[56],       /* strongly marked */
      (HeapWord*) &my_heap_memory[64],       /* strongly marked */
      (HeapWord*) &my_heap_memory[256],      /* strongly marked */
      (HeapWord*) &my_heap_memory[257],      /* strongly marked */
      (HeapWord*) &my_heap_memory[258],      /* strongly marked */
      (HeapWord*) &my_heap_memory[259],      /* strongly marked */
      (HeapWord*) &my_heap_memory[480],      /* weakly marked */
      (HeapWord*) &my_heap_memory[488],      /* strongly marked */
      (HeapWord*) &my_heap_memory[1352],     /* strongly marked */
      (HeapWord*) &my_heap_memory[1360],     /* weakly marked upgraded to strongly marked */
      (HeapWord*) &my_heap_memory[1488],     /* weakly marked */
      (HeapWord*) &my_heap_memory[1496],     /* strongly marked */
      (HeapWord*) &my_heap_memory[2416],     /* weakly marked */
      (HeapWord*) &my_heap_memory[2432],     /* strongly marked */
      (HeapWord*) &my_heap_memory[5960]      /* strongly marked */
      (HeapWord*) &my_heap_memory[5968],     /* weakly marked */
      (HeapWord*) &my_heap_memory[8191],    /* weakly marked */
      (HeapWord*) &my_heap_memory[8192],    /* weakly marked */
      (HeapWord*) &my_heap_memory[8193]     /* weakly marked */
    };
    size_t all_marked_objects_after_first_clear = sizeof(all_marked_addresses_after_first_clear) / sizeof(HeapWord*);
    bool is_weakly_marked_object_after_first_clear[] = {
      false,
      true,
      true,
      true,
      true,
      false,
      false,
      false,
      false,
      false,
      false,
      false,
      false,
      true,
      false,
      false,
      true,
      true,
      false,
      true,
      false,
      false,
      true,
      true,
      true,
      true
    };
    bool is_strongly_marked_object_after_first_clear[] = {
      true,
      false,
      false,
      false,
      false,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      false,
      true,
      true,
      true,
      false,
      true,
      false,
      true,
      true,
      false,
      false,
      false,
      false
    };
    verify_bitmap_all(&mbm, all_marked_addresses_after_first_clear,
                      is_weakly_marked_object_after_first_clear, is_strongly_marked_object_after_first_cler,
                      all_marked_objects_after_first_clear);


    MemRegion second_clear_region(&my_heap_memory[2360], &my_heap_memory[2416]);
    mbm.clear_range_large(second_clear_region);
    // Five objects are no longer marked

    HeapWord* all_marked_addresses_after_2nd_clear[] = {
      (HeapWord*) &my_heap_memory[8],        /* strongly marked */
      (HeapWord*) &my_heap_memory[13],       /* weakly marked */
      (HeapWord*) &my_heap_memory[14],       /* weakly marked */
      (HeapWord*) &my_heap_memory[15],       /* weakly marked */
      (HeapWord*) &my_heap_memory[16],       /* weakly marked */
      (HeapWord*) &my_heap_memory[24],       /* strongly marked */
      (HeapWord*) &my_heap_memory[32],       /* strongly marked */
      (HeapWord*) &my_heap_memory[56],       /* strongly marked */
      (HeapWord*) &my_heap_memory[64],       /* strongly marked */
      (HeapWord*) &my_heap_memory[256],      /* strongly marked */
      (HeapWord*) &my_heap_memory[257],      /* strongly marked */
      (HeapWord*) &my_heap_memory[258],      /* strongly marked */
      (HeapWord*) &my_heap_memory[259],      /* strongly marked */
      (HeapWord*) &my_heap_memory[480],      /* weakly marked */
      (HeapWord*) &my_heap_memory[488],      /* strongly marked */
      (HeapWord*) &my_heap_memory[1352],     /* strongly marked */
      (HeapWord*) &my_heap_memory[2416],     /* weakly marked */
      (HeapWord*) &my_heap_memory[2432],     /* strongly marked */
      (HeapWord*) &my_heap_memory[5960]      /* strongly marked */
      (HeapWord*) &my_heap_memory[5968],     /* weakly marked */
      (HeapWord*) &my_heap_memory[8191],    /* weakly marked */
      (HeapWord*) &my_heap_memory[8192],    /* weakly marked */
      (HeapWord*) &my_heap_memory[8193]     /* weakly marked */
    };
    size_t all_marked_objects_after_2nd_clear = sizeof(all_marked_addresses_after_2nd_clear) / sizeof(HeapWord*);
    bool is_weakly_marked_object_after_2nd_clear[] = {
      false,
      true;
      true;
      true;
      true;
      false,
      false,
      false,
      false,
      false,
      false,
      false,
      false,
      true,
      false,
      false,
      true,
      false,
      false,
      true,
      true,
      true,
      true
    };
    bool is_strongly_marked_object_after_2nd_clear[] = {
      true,
      false,
      false,
      false,
      false,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      true,
      false,
      true,
      true,
      false,
      true,
      true,
      false,
      false,
      false,
      false
    };
    verify_bitmap_all(&mbm, all_marked_addresses_after_2nd_clear,
                      is_weakly_marked_object_after_2nd_clear, is_strongly_marked_object_after_2nd_cler,
                      all_marked_objects_after_2nd_clear);

    _success = true;
    return true;
  }

};

TEST(BasicShenandoahMarkBitMapTest, minimum_test) {

  bool result = ShenandoahMarkBitMapTest::run_test();
  ASSERT_EQ(result, true);
  ASSERT_EQ(_success, true);
  ASSERT_EQ(_assertion_failures, (size_t) 0);
}
