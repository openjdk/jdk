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

#define ASSERT_ALIGN(p, n) ASSERT_TRUE(is_aligned(p, n))
#define ASSERT_ALIGN_64(p) ASSERT_ALIGN(p, 8)
#define ASSERT_ALIGN_32(p) ASSERT_ALIGN(p, 4)
#define ASSERT_ALIGN_X(p) ASSERT_ALIGN(p, sizeof(void*))

// #define LOG(s) tty->print_cr s;
#define LOG(s)

// Given a memory range, check that it is filled with the expected byte.
// If not, print the surrounding bytes as hex and return false.
static bool check_range(const void* p, size_t s, int expected) {

  // Omit the test for NULL or 0 ranges
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

// We use this to fill the allocated ranges with a pattern to test for overwriters later.
static void mark_range(void* p, size_t s, int mark) {
  const char m = (char)((mark + 1) % 10); // valid marks are [1...10]
  if (p != NULL && s > 0) {
    ::memset(p, m, s);
  }
}

// Check a range marked with mark_range()
static bool check_marked_range(const void* p, size_t s, int expected_mark) {
  const char m = (char)((expected_mark + 1) % 10); // valid marks are [1...10]
  return check_range(p, s, m);
}

#define ASSERT_RANGE_IS_MARKED(p, size, mark) ASSERT_TRUE(check_marked_range(p, size, mark))
static const int some_random_mark = 4711;

// Helper to check the arena alignment gap pattern.
// In debug, if arena allocation causes gaps due to alignment, a pattern is written into that gap:
//
// |------------------|----|-------
// | first            |'G' | second
// | allocation       |    | allocation
// |------------------|----|-------
//
// This function checks that pattern.
#ifdef ASSERT
static bool check_alignment_gap_pattern(const void* p1, size_t first_allocation_size, const void* p2) {

  // Omit the test if one of them is NULL
  if (p1 == NULL || p2 == NULL) {
    return true;
  }

  // Omit the test if it looks like the pointers are completely unrelated - p2 may live in a new Chunk.
  if ((p2 > p1 && pointer_delta(p2, p1, 1) >= sizeof(Chunk)) ||
      (p1 > p2 && pointer_delta(p1, p2, 1) >= sizeof(Chunk))) {
    return true;
  }

  const char GAP_PATTERN = 'G'; // see arena.hpp
  const char* gap = ((const char*)p1) + first_allocation_size;
  assert(gap <= p2, "sanity");
  const size_t len = ((const char*)p2) - gap;

  if (len > 0) {
    if (check_range(gap, len, GAP_PATTERN) == false) {
      return false;
    }
    // Test the first byte after the gap too: should *not* be "G" since mark_range() does not use that pattern
    if (gap[len] == GAP_PATTERN) {
      return false;
    }
  }

  return true;
}
#define ASSERT_GAP_PATTERN(p1, size1, p2) ASSERT_TRUE(check_alignment_gap_pattern(p1, size1, p2));
#else
#define ASSERT_GAP_PATTERN(p1, size1, p2)
#endif


// Note: any tests making assumptions about the placement of allocations needs to
// be guarded from running with UseMallocOnly.

TEST_VM(Arena, alloc_alignment_32) {
  Arena ar(mtTest);
  void* p1 = ar.Amalloc(1);   // just one byte, leaves _hwm unaligned at offset 1
  void* p2 = ar.Amalloc32(1); // Another byte, 32bit aligned
  ASSERT_NOT_NULL(p2);
  ASSERT_ALIGN_32(p2);
  ASSERT_GAP_PATTERN(p1, 1, p2);
  if (!UseMallocOnly) {
    ASSERT_EQ(pointer_delta(p2, p1, 1), sizeof(uint32_t));
  }
}

TEST_VM(Arena, alloc_alignment_64) {
  Arena ar(mtTest);
  void* p1 = ar.Amalloc(1);   // just one byte, leaves _hwm unaligned at offset 1
  void* p2 = ar.Amalloc64(1); // Another byte, 64bit aligned
  ASSERT_NOT_NULL(p2);
  ASSERT_ALIGN_64(p2);
  ASSERT_GAP_PATTERN(p1, 1, p2);
  if (!UseMallocOnly) {
    ASSERT_EQ(pointer_delta(p2, p1, 1), sizeof(uint64_t));
  }
}

TEST_VM(Arena, alloc_alignment_x) {
  Arena ar(mtTest);
  void* p1 = ar.Amalloc(1);      // just one byte, leaves _hwm unaligned at offset 1
  void* p2 = ar.AmallocWords(1); // Another byte, aligned to void* size
  ASSERT_NOT_NULL(p2);
  ASSERT_ALIGN_X(p2);
  ASSERT_GAP_PATTERN(p1, 1, p2);
  if (!UseMallocOnly) {
    ASSERT_EQ(pointer_delta(p2, p1, 1), sizeof(void*));
  }
}

TEST_VM(Arena, alloc_default_alignment) {
  Arena ar(mtTest);
  void* p1 = ar.Amalloc(1);   // just one byte, leaves _hwm unaligned at offset 1
  void* p2 = ar.Amalloc(1);   // Another byte, 64bit (default) aligned
  ASSERT_NOT_NULL(p2);
  ASSERT_ALIGN_64(p2);        // default alignment is 64bit on all platforms
  ASSERT_GAP_PATTERN(p1, 1, p2);
  if (!UseMallocOnly) {
    ASSERT_EQ(pointer_delta(p2, p1, 1), sizeof(uint64_t));
  }
}

TEST_VM(Arena, alloc_size_0) {
  // Amalloc(0) returns a non-NULL pointer. Note that in contrast to malloc(0), that pointer is *not* unique.
  // There is code in the hotpot relying on RA allocations with size 0 being successful.
  Arena ar(mtTest);
  void* p = ar.Amalloc32(0);
  ASSERT_NOT_NULL(p);
}

// Check Arena.Afree: the free'd allocation (non-top) should be
// zapped (debug only), surrounding blocks should be unaffected.
TEST_VM(Arena, free) {
  Arena ar(mtTest);

  void* p_before = ar.Amalloc(0x10);
  ASSERT_NOT_NULL(p_before);
  mark_range(p_before, 0x10, some_random_mark);

  void* p = ar.Amalloc(0x10);
  ASSERT_NOT_NULL(p);
  mark_range(p, 0x10, some_random_mark + 1);

  void* p_after = ar.Amalloc(0x10);
  ASSERT_NOT_NULL(p_after);
  mark_range(p_after, 0x10, some_random_mark);

  ASSERT_RANGE_IS_MARKED(p_before, 0x10, some_random_mark);
  ASSERT_RANGE_IS_MARKED(p, 0x10, some_random_mark + 1);
  ASSERT_RANGE_IS_MARKED(p_before, 0x10, some_random_mark);

  ar.Afree(p, 0x10);

  ASSERT_RANGE_IS_MARKED(p_before, 0x10, some_random_mark);
#ifdef ASSERT
  ASSERT_TRUE(check_range(p, 0x10, badResourceValue));
#endif
  ASSERT_RANGE_IS_MARKED(p_before, 0x10, some_random_mark);
}

// In-place shrinking.
TEST_VM(Arena, realloc_top_shrink) {
  if (!UseMallocOnly) {
    Arena ar(mtTest);

    void* p1 = ar.Amalloc(0x200);
    ASSERT_NOT_NULL(p1);
    ASSERT_ALIGN_64(p1);
    mark_range(p1, 0x200, some_random_mark);

    void* p2 = ar.Arealloc(p1, 0x200, 0x100);
    ASSERT_EQ(p1, p2);
    ASSERT_RANGE_IS_MARKED(p2, 0x100, some_random_mark); // realloc should preserve old content

    // A subsequent allocation should be placed right after the shrunk first allocation
    void* p3 = ar.Amalloc(1);
    ASSERT_EQ(p3, ((char*)p2) + 0x100);
  }
}

// not-in-place shrinking.
TEST_VM(Arena, realloc_nontop_shrink) {
  Arena ar(mtTest);

  void* p1 = ar.Amalloc(200);
  ASSERT_NOT_NULL(p1);
  ASSERT_ALIGN_64(p1);
  mark_range(p1, 200, some_random_mark);

  void* p_other = ar.Amalloc(20); // new top, p1 not top anymore

  void* p2 = ar.Arealloc(p1, 200, 100);
  if (!UseMallocOnly) {
    ASSERT_EQ(p1, p2); // should still shrink in place
  }
  ASSERT_RANGE_IS_MARKED(p2, 100, some_random_mark); // realloc should preserve old content
}

// in-place growing.
TEST_VM(Arena, realloc_top_grow) {
  Arena ar(mtTest);

  void* p1 = ar.Amalloc(10);
  ASSERT_NOT_NULL(p1);
  ASSERT_ALIGN_64(p1);
  mark_range(p1, 10, some_random_mark);

  void* p2 = ar.Arealloc(p1, 10, 20);
  if (!UseMallocOnly) {
    ASSERT_EQ(p1, p2); // The sizes should be small enough to be able to grow in place
  }
  ASSERT_RANGE_IS_MARKED(p2, 10, some_random_mark); // realloc should preserve old content
}

// not-in-place growing.
TEST_VM(Arena, realloc_nontop_grow) {
  Arena ar(mtTest);

  void* p1 = ar.Amalloc(10);
  ASSERT_NOT_NULL(p1);
  ASSERT_ALIGN_64(p1);
  mark_range(p1, 10, some_random_mark);

  void* p_other = ar.Amalloc(20); // new top, p1 not top anymore

  void* p2 = ar.Arealloc(p1, 10, 20);
  ASSERT_NOT_NULL(p2);
  ASSERT_ALIGN_64(p2);
  ASSERT_RANGE_IS_MARKED(p2, 10, some_random_mark); // realloc should preserve old content
}

// realloc size 0 frees the allocation
TEST_VM(Arena, realloc_size_0) {
  // realloc to 0 is equivalent to free
  Arena ar(mtTest);

  void* p1 = ar.Amalloc(200);
  ASSERT_NOT_NULL(p1);
  ASSERT_ALIGN_64(p1);

  void* p2 = ar.Arealloc(p1, 200, 0); // -> Afree, completely roll back old allocation
  ASSERT_NULL(p2); // ... and should return NULL

  // a subsequent allocation should get the same pointer
  if (!UseMallocOnly) {
    void* p3 = ar.Amalloc(1);
    ASSERT_EQ(p3, p1);
  }
}

// Realloc equal sizes is a noop
TEST_VM(Arena, realloc_same_size) {
  Arena ar(mtTest);
  void* p1 = ar.Amalloc(0x200);
  ASSERT_NOT_NULL(p1);
  ASSERT_ALIGN_64(p1);
  mark_range(p1, 0x200, some_random_mark);

  void* p2 = ar.Arealloc(p1, 0x200, 0x200);

  if (!UseMallocOnly) {
    ASSERT_EQ(p2, p1);
  }
  ASSERT_RANGE_IS_MARKED(p2, 0x200, some_random_mark);
}

// -------- random alloc test -------------
TEST_VM(Arena, random_allocs) {

  // Randomly allocate with random sizes and random alignments;
  // check for overwriters. We do this a large number of times, to give
  // chunk handling a good workout too.

  const int num_allocs = 250 * 1000;
  const int avg_alloc_size = 64;

  void** ptrs = NEW_C_HEAP_ARRAY(void*, num_allocs, mtTest);
  size_t* sizes = NEW_C_HEAP_ARRAY(size_t, num_allocs, mtTest);
  size_t* alignments = NEW_C_HEAP_ARRAY(size_t, num_allocs, mtTest);

  Arena ar(mtTest);

  // Allocate
  for (int i = 0; i < num_allocs; i ++) {
    size_t s = MAX2(1, os::random() % (avg_alloc_size * 2));
    size_t al = ((size_t)1) << (os::random() % (LogBytesPerLong + 1));
    void* p = ar.Amalloc_aligned(s, al);
    ASSERT_NOT_NULL(p);
    ASSERT_CONTAINS(ar, p);
    ASSERT_ALIGN(p, al);
    ptrs[i] = p; sizes[i] = s; alignments[i] = al;
    mark_range(p, s, i); // canary
    LOG(("[%d]: " PTR_FORMAT ", size " SIZE_FORMAT ", aligned " SIZE_FORMAT,
         i, p2i(p), s, al));
  }

  // Check pattern in allocations for overwriters.
  // Check gap patterns in debug.
  for (int i = 0; i < num_allocs; i ++) {
    ASSERT_RANGE_IS_MARKED(ptrs[i], sizes[i], i);
#ifdef ASSERT
    if (i > 0) {
      ASSERT_GAP_PATTERN(ptrs[i - 1], sizes[i - 1], ptrs[i]);
    }
#endif
  }

  // realloc all of them randomly
  for (int i = 0; i < num_allocs; i ++) {
    size_t new_size = MAX2(1, os::random() % (avg_alloc_size * 2));
    void* p2 = ar.Arealloc(ptrs[i], sizes[i], new_size);
    ASSERT_NOT_NULL(p2);
    ASSERT_CONTAINS(ar, p2);
    ASSERT_ALIGN(p2, alignments[i]); // original alignment should have been preserved
    ASSERT_RANGE_IS_MARKED(p2, MIN2(sizes[i], new_size), i); // old content should have been preserved
    ptrs[i] = p2; sizes[i] = new_size;
    mark_range(p2, new_size, i); // canary
    LOG(("[%d]: realloc " PTR_FORMAT ", size " SIZE_FORMAT ", aligned " SIZE_FORMAT,
         i, p2i(p2), new_size, alignments[i]));
  }

  // Check test pattern again
  //  Note that we don't check the gap pattern anymore since if allocations had been shrunk in place
  //  this now gets difficult.
  for (int i = 0; i < num_allocs; i ++) {
    ASSERT_RANGE_IS_MARKED(ptrs[i], sizes[i], i);
  }

  // Randomly free a bunch of allocations.
  for (int i = 0; i < num_allocs; i ++) {
    if (os::random() % 10 == 0) {
      ar.Afree(ptrs[i], sizes[i]);
      // In debug builds the free should have filled the space with badResourceValue
      DEBUG_ONLY(check_range(ptrs[i], sizes[i], badResourceValue));
      ptrs[i] = NULL;
    }
  }

  // Check test pattern again
  for (int i = 0; i < num_allocs; i ++) {
    ASSERT_RANGE_IS_MARKED(ptrs[i], sizes[i], i);
  }

  FREE_C_HEAP_ARRAY(char*, ptrs);
  FREE_C_HEAP_ARRAY(size_t, sizes);
  FREE_C_HEAP_ARRAY(size_t, alignments);

}

