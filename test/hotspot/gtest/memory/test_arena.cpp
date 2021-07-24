/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 SAP SE. All rights reserved.
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
#include "memory/arena.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

#define ASSERT_NOT_NULL(p) ASSERT_NE(p, (char*)NULL)
#define ASSERT_NULL(p) ASSERT_EQ(p, (char*)NULL)

#define ASSERT_CONTAINS(ar, p) ASSERT_TRUE(ar.contains(p))

// Note:
// - Amalloc returns 64bit aligned pointer (also on 32-bit)
// - AmallocWords returns word-aligned pointer
#define ASSERT_ALIGN(p, n) ASSERT_TRUE(is_aligned(p, n))
#define ASSERT_ALIGN_64(p) ASSERT_ALIGN(p, 8)

// Check a return address from Amalloc, expected to be non-null
#define ASSERT_AMALLOC(ar, p) \
  ASSERT_NOT_NULL(p); \
  ASSERT_CONTAINS(ar, p); \
  ASSERT_ALIGN_64(p);

// #define LOG(s) tty->print_cr s;
#define LOG(s)

// Given a memory range, check that the whole range is filled with the expected byte.
// If not, print the surrounding bytes as hex and return false.
static bool check_range(const void* p, size_t s, uint8_t expected) {
  if (p == NULL || s == 0) {
    return true;
  }

  const char* first_wrong = NULL;
  char* p2 = (char*)p;
  const char* const end = p2 + s;
  while (p2 < end) {
    if (*p2 != (char)expected) {
      first_wrong = p2;
      break;
    }
    p2 ++;
  }

  if (first_wrong != NULL) {
    tty->print_cr("wrong pattern around " PTR_FORMAT, p2i(first_wrong));
    os::print_hex_dump(tty, (address)(align_down(p2, 0x10) - 0x10),
                            (address)(align_up(end, 0x10) + 0x10), 1);
  }

  return first_wrong == NULL;
}

// Fill range with a byte mark.
static void mark_range_with(void* p, size_t s, uint8_t mark) {
  assert(mark != badResourceValue, "choose a different mark please");
  if (p != NULL && s > 0) {
    ::memset(p, mark, s);
  }
}
#define ASSERT_RANGE_IS_MARKED_WITH(p, size, mark)  ASSERT_TRUE(check_range(p, size, mark))

// Fill range with a fixed byte mark.
static void mark_range(void* p, size_t s) {
  mark_range_with(p, s, '#');
}
#define ASSERT_RANGE_IS_MARKED(p, size)             ASSERT_TRUE(check_range(p, size, '#'))

// Test behavior for Amalloc(0):
TEST_VM(Arena, alloc_size_0) {
  // Amalloc(0) returns a (non-unique) non-NULL pointer.
  Arena ar(mtTest);
  void* p = ar.Amalloc(0);
  // The returned pointer should be not null, aligned, but not (!) contained in the arena
  // since it has size 0 and points at hwm, thus beyond the arena content. Should we ever
  // change that behavior (size 0 -> size 1, like we do in os::malloc) arena.contains will
  // work as expected for 0 sized allocations too. Note that UseMallocOnly behaves differently,
  // but there, arena.contains() is broken anyway for pointers other than the start of a block.
  ASSERT_NOT_NULL(p);
  ASSERT_ALIGN_64(p);
  if (!UseMallocOnly) {
    ASSERT_FALSE(ar.contains(p));
  }

  // Allocate again. The new allocations should have the same position as the 0-sized
  // first one.
  if (!UseMallocOnly) {
    void* p2 = ar.Amalloc(1);
    ASSERT_AMALLOC(ar, p2);
    ASSERT_EQ(p2, p);
  }
}

// Test behavior for Arealloc(p, 0)
TEST_VM(Arena, realloc_size_0) {
  // Arealloc(p, 0) behaves like Afree(p). It should release the memory
  // and, if top position, roll back the hwm.
  Arena ar(mtTest);
  void* p1 = ar.Amalloc(0x10);
  ASSERT_AMALLOC(ar, p1);
  void* p2 = ar.Arealloc(p1, 0x10, 0);
  ASSERT_NULL(p2);

  // a subsequent allocation should get the same pointer
  if (!UseMallocOnly) {
    void* p3 = ar.Amalloc(0x20);
    ASSERT_EQ(p3, p1);
  }
}

// Realloc equal sizes is a noop
TEST_VM(Arena, realloc_same_size) {
  Arena ar(mtTest);
  void* p1 = ar.Amalloc(0x200);
  ASSERT_AMALLOC(ar, p1);
  mark_range(p1, 0x200);

  void* p2 = ar.Arealloc(p1, 0x200, 0x200);

  if (!UseMallocOnly) {
    ASSERT_EQ(p2, p1);
  }
  ASSERT_RANGE_IS_MARKED(p2, 0x200);
}

// Test behavior for Afree(NULL) and Arealloc(NULL, x)
TEST_VM(Arena, free_null) {
  Arena ar(mtTest);
  ar.Afree(NULL, 10); // should just be ignored
}

TEST_VM(Arena, realloc_null) {
  Arena ar(mtTest);
  void* p = ar.Arealloc(NULL, 0, 20); // equivalent to Amalloc(20)
  ASSERT_AMALLOC(ar, p);
}

// Check Arena.Afree in a non-top position.
// The free'd allocation should be zapped (debug only),
// surrounding blocks should be unaffected.
TEST_VM(Arena, free_nontop) {
  Arena ar(mtTest);

  void* p_before = ar.Amalloc(0x10);
  ASSERT_AMALLOC(ar, p_before);
  mark_range(p_before, 0x10);

  void* p = ar.Amalloc(0x10);
  ASSERT_AMALLOC(ar, p);
  mark_range_with(p, 0x10, 'Z');

  void* p_after = ar.Amalloc(0x10);
  ASSERT_AMALLOC(ar, p_after);
  mark_range(p_after, 0x10);

  ASSERT_RANGE_IS_MARKED(p_before, 0x10);
  ASSERT_RANGE_IS_MARKED_WITH(p, 0x10, 'Z');
  ASSERT_RANGE_IS_MARKED(p_after, 0x10);

  ar.Afree(p, 0x10);

  ASSERT_RANGE_IS_MARKED(p_before, 0x10);
  DEBUG_ONLY(ASSERT_RANGE_IS_MARKED_WITH(p, 0x10, badResourceValue);)
  ASSERT_RANGE_IS_MARKED(p_after, 0x10);
}

// Check Arena.Afree in a top position.
// The free'd allocation (non-top) should be zapped (debug only),
// the hwm should have been rolled back.
TEST_VM(Arena, free_top) {
  Arena ar(mtTest);

  void* p = ar.Amalloc(0x10);
  ASSERT_AMALLOC(ar, p);
  mark_range_with(p, 0x10, 'Z');

  ar.Afree(p, 0x10);
  DEBUG_ONLY(ASSERT_RANGE_IS_MARKED_WITH(p, 0x10, badResourceValue);)

  // a subsequent allocation should get the same pointer
  if (!UseMallocOnly) {
    void* p2 = ar.Amalloc(0x20);
    ASSERT_EQ(p2, p);
  }
}

// In-place shrinking.
TEST_VM(Arena, realloc_top_shrink) {
  if (!UseMallocOnly) {
    Arena ar(mtTest);

    void* p1 = ar.Amalloc(0x200);
    ASSERT_AMALLOC(ar, p1);
    mark_range(p1, 0x200);

    void* p2 = ar.Arealloc(p1, 0x200, 0x100);
    ASSERT_EQ(p1, p2);
    ASSERT_RANGE_IS_MARKED(p2, 0x100); // realloc should preserve old content

    // A subsequent allocation should be placed right after the end of the first, shrunk, allocation
    void* p3 = ar.Amalloc(1);
    ASSERT_EQ(p3, ((char*)p1) + 0x100);
  }
}

// not-in-place shrinking.
TEST_VM(Arena, realloc_nontop_shrink) {
  Arena ar(mtTest);

  void* p1 = ar.Amalloc(200);
  ASSERT_AMALLOC(ar, p1);
  mark_range(p1, 200);

  void* p_other = ar.Amalloc(20); // new top, p1 not top anymore

  void* p2 = ar.Arealloc(p1, 200, 100);
  if (!UseMallocOnly) {
    ASSERT_EQ(p1, p2); // should still shrink in place
  }
  ASSERT_RANGE_IS_MARKED(p2, 100); // realloc should preserve old content
}

// in-place growing.
TEST_VM(Arena, realloc_top_grow) {
  Arena ar(mtTest); // initial chunk size large enough to ensure below allocation grows in-place.

  void* p1 = ar.Amalloc(0x10);
  ASSERT_AMALLOC(ar, p1);
  mark_range(p1, 0x10);

  void* p2 = ar.Arealloc(p1, 0x10, 0x20);
  if (!UseMallocOnly) {
    ASSERT_EQ(p1, p2);
  }
  ASSERT_RANGE_IS_MARKED(p2, 0x10); // realloc should preserve old content
}

// not-in-place growing.
TEST_VM(Arena, realloc_nontop_grow) {
  Arena ar(mtTest);

  void* p1 = ar.Amalloc(10);
  ASSERT_AMALLOC(ar, p1);
  mark_range(p1, 10);

  void* p_other = ar.Amalloc(20); // new top, p1 not top anymore

  void* p2 = ar.Arealloc(p1, 10, 20);
  ASSERT_AMALLOC(ar, p2);
  ASSERT_RANGE_IS_MARKED(p2, 10); // realloc should preserve old content
}

// -------- random alloc test -------------

static uint8_t canary(int i) {
  return (uint8_t)('A' + i % 26);
}

TEST_VM(Arena, random_allocs) {

  // Randomly allocate with random sizes and differing alignments;
  //  check alignment and check for overwriters.
  // We do this a large number of times, to give chunk handling a
  //  good workout too.

  const int num_allocs = 250 * 1000;
  const int avg_alloc_size = 64;

  void** ptrs = NEW_C_HEAP_ARRAY(void*, num_allocs, mtTest);
  size_t* sizes = NEW_C_HEAP_ARRAY(size_t, num_allocs, mtTest);
  size_t* alignments = NEW_C_HEAP_ARRAY(size_t, num_allocs, mtTest);

  Arena ar(mtTest);

  // Allocate
  for (int i = 0; i < num_allocs; i ++) {
    size_t size = os::random() % (avg_alloc_size * 2); // Note: 0 is possible and should work
    size_t alignment = 0;
    void* p = NULL;
    if (os::random() % 2) { // randomly switch between Amalloc and AmallocWords
      p = ar.Amalloc(size);
      alignment = BytesPerLong;
    } else {
      // Inconsistency: AmallocWords wants its input size word aligned, whereas Amalloc takes
      //  care of alignment itself. We may want to clean this up, but for now just go with it.
      size = align_up(size, BytesPerWord);
      p = ar.AmallocWords(size);
      alignment = BytesPerWord;
    }
    LOG(("[%d]: " PTR_FORMAT ", size " SIZE_FORMAT ", aligned " SIZE_FORMAT,
         i, p2i(p), size, alignment));
    ASSERT_NOT_NULL(p);
    if (size > 0) {
      ASSERT_ALIGN(p, alignment);
      ASSERT_CONTAINS(ar, p);
    }
    mark_range_with(p, size, canary(i));
    ptrs[i] = p; sizes[i] = size; alignments[i] = alignment;
  }

  // Check pattern in allocations for overwriters.
  for (int i = 0; i < num_allocs; i ++) {
    ASSERT_RANGE_IS_MARKED_WITH(ptrs[i], sizes[i], canary(i));
  }

  // realloc all of them randomly
  for (int i = 0; i < num_allocs; i ++) {
    size_t new_size = os::random() % (avg_alloc_size * 2);  // Note: 0 is possible and should work
    void* p2 = ar.Arealloc(ptrs[i], sizes[i], new_size);
    LOG(("[%d]: realloc " PTR_FORMAT ", size " SIZE_FORMAT ", aligned " SIZE_FORMAT,
         i, p2i(p2), new_size, alignments[i]));
    if (new_size > 0) {
      ASSERT_NOT_NULL(p2);
      ASSERT_TRUE(ar.contains(p2));
      // Arealloc only guarantees the original alignment, nothing bigger (if the block was resized in-place,
      // it keeps the original alignment)
      ASSERT_ALIGN(p2, alignments[i]);
      // old content should have been preserved
      ASSERT_RANGE_IS_MARKED_WITH(p2, MIN2(sizes[i], new_size), canary(i));
      // mark new range
      mark_range_with(p2, new_size, canary(i));
    } else {
      ASSERT_NULL(p2);
    }
    ptrs[i] = p2; sizes[i] = new_size;
  }

  // Check test pattern again
  //  Note that we don't check the gap pattern anymore since if allocations had been shrunk in place
  //  this now gets difficult.
  for (int i = 0; i < num_allocs; i ++) {
    ASSERT_RANGE_IS_MARKED_WITH(ptrs[i], sizes[i], canary(i));
  }

  // Randomly free a bunch of allocations.
  for (int i = 0; i < num_allocs; i ++) {
    if (os::random() % 10 == 0) {
      ar.Afree(ptrs[i], sizes[i]);
      // In debug builds the free should have filled the space with badResourceValue
      DEBUG_ONLY(ASSERT_RANGE_IS_MARKED_WITH(ptrs[i], sizes[i], badResourceValue));
      ptrs[i] = NULL;
    }
  }

  // Check test pattern again
  for (int i = 0; i < num_allocs; i ++) {
    ASSERT_RANGE_IS_MARKED_WITH(ptrs[i], sizes[i], canary(i));
  }

  FREE_C_HEAP_ARRAY(char*, ptrs);
  FREE_C_HEAP_ARRAY(size_t, sizes);
  FREE_C_HEAP_ARRAY(size_t, alignments);

}

TEST(Arena, mixed_alignment_allocation) {
  // Test that mixed alignment allocations work and provide allocations with the correct
  // alignment
  Arena ar(mtTest);
  void* p1 = ar.AmallocWords(BytesPerWord);
  void* p2 = ar.Amalloc(BytesPerLong);
  ASSERT_NOT_NULL(p1);
  ASSERT_TRUE(is_aligned(p1, BytesPerWord));
  ASSERT_NOT_NULL(p2);
  ASSERT_TRUE(is_aligned(p2, BytesPerLong));
}
